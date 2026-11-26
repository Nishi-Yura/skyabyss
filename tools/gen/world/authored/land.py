# -*- coding: utf-8 -*-
"""土地全体を前もって組む (2026-11-25。鯨見の町の試作 whale_town.py を土地全体へ)。

元: 今の世界の区画ごとの書き出し run/shots/land_<土地>/dumps/<土地>_<tx>_<tz>.sbd (gen_land_dump.py の台本)
先: data/skyabyss/continent/authored/<土地>/<tx>_<tz>.bin と index.json (ContinentAuthored が読む)

    python -I tools/gen/world/authored/land.py whale [tx_tz ...]   # 区画を絞ると、その区画だけ書き直す (確かめ用)

二段で組む:
  1. 土地全体の 2 次元: 元の地面の高さ・人の作った物・水・道・建った場所を集め、高さ (H)、守る所 (keep)、
     岩山・池・道の岩の門・木の位置と種 (それぞれ自分の乱数の種) を決める
  2. 区画ごとの 3 次元: 区画の列を積み直し、区画にかかる形 (岩山・木・門・池の板の道) を描く。形は種が同じなので、
     隣の区画で描いても同じ形になり、継ぎ目が出ない
決まり (memory skyabyss-authored-terrain): 岩山は盛った裾野からなだらか (滝の岩だけ切り立つ)、木は 4 種 + 灯り葉の樹の林、雪のまだらなし。
"""
from __future__ import annotations

import json
import os
import math
import random
import sys
import time
from pathlib import Path

import numpy as np
from scipy import ndimage

sys.path.insert(0, str(Path(__file__).resolve().parent))
import alib  # noqa: E402
import shapes as S  # noqa: E402
import shapes_big as B  # noqa: E402
import lairs as LAIRS  # noqa: E402

LANDS = {"whale": {"region": 2, "town": (-120, -760), "half": 71, "lairs": [(230, -1000)],
                   "ponds": [(25, -640)], "n_big": 14, "n_small": 22, "n_ponds": 6, "n_arches": 5}}
# 起伏だけの土地 (style relief): 書き出した世界の地面・木・草をそのまま、列ごとに上下させて起伏をつける。
# 木と草は今の Java の木 (ContinentTrees の土地ごとの木) のまま。道と建った場所は動かさない。
# 岩山・池・岩の門は岩鯨と同じ形で、素材だけ土地の石に読み替える (subst)。
FAR = (10 ** 7, 10 ** 7)
RELIEF = {"town": FAR, "half": 0, "lairs": [], "ponds": [], "style": "relief"}
LANDS["tide"] = dict(RELIEF, region=3, n_big=8, n_small=14, n_ponds=4, n_arches=3, subst={
    "stone": "sandstone", "andesite": "smooth_sandstone", "tuff": "cut_sandstone", "cobblestone": "sandstone",
    "mossy_cobblestone": "smooth_sandstone", "calcite": "smooth_sandstone", "gravel": "sand", "moss_block": "coarse_dirt",
    "moss_carpet": "short_grass", "fern": "short_grass", "podzol": "coarse_dirt", "spruce_log": "stripped_acacia_log",
    "spruce_fence": "acacia_fence", "spruce_planks": "acacia_planks", "spruce_slab": "acacia_slab",
    "spruce_leaves": "acacia_leaves", "cave_vines": "air", "cave_vines_plant": "air", "mud": "sand", "clay": "sand"})
MARGIN = 8
T = alib.TILE

# 鐘の野・外れ: 緑の野。岩は岩鯨と同じ灰の石、木の部品は楢
_OAK = {"spruce_log": "oak_log", "spruce_leaves": "oak_leaves", "spruce_fence": "oak_fence", "spruce_planks": "oak_planks",
        "spruce_slab": "oak_slab", "cave_vines": "air", "cave_vines_plant": "air", "podzol": "coarse_dirt"}
LANDS["meadow"] = dict(RELIEF, region=0, n_big=8, n_small=16, n_ponds=5, n_arches=3, subst=dict(_OAK))
LANDS["outskirts"] = dict(RELIEF, region=1, n_big=1, n_small=4, n_ponds=1, n_arches=1, subst=dict(_OAK))
# 嵐の高地: 灰と白の石 (方解石の縞)、苔はなし
LANDS["storm"] = dict(RELIEF, region=4, n_big=12, n_small=18, n_ponds=3, n_arches=3, subst={
    "grass_block": "snow_block", "short_grass": "air", "fern": "air", "tall_grass": "air",
    "mossy_cobblestone": "cobblestone", "moss_block": "gravel", "moss_carpet": "air", "tuff": "calcite",
    "cave_vines": "air", "cave_vines_plant": "air", "podzol": "coarse_dirt", "mud": "gravel"})
# 灰の荒野: 黒い石と玄武岩、草はなし
LANDS["ember"] = dict(RELIEF, region=5, n_big=12, n_small=20, n_ponds=0, n_arches=3, subst={
    # 灰の荒野は素焼きの縞の荒れ地: 岩山は素焼きの縞のメサ、崩れ石は黒い石と玄武岩
    "stone": "terracotta", "andesite": "brown_terracotta", "tuff": "light_gray_terracotta", "cobblestone": "blackstone",
    "mossy_cobblestone": "basalt", "calcite": "white_terracotta", "diorite": "white_terracotta", "granite": "red_terracotta",
    "grass_block": "red_sand", "moss_block": "coarse_dirt", "podzol": "coarse_dirt", "dirt": "terracotta",
    "moss_carpet": "air", "fern": "dead_bush", "short_grass": "air", "tall_grass": "air", "spore_blossom": "air",
    "spruce_log": "dark_oak_log", "spruce_leaves": "air", "spruce_fence": "dark_oak_fence",
    "spruce_planks": "dark_oak_planks", "spruce_slab": "dark_oak_slab", "cave_vines": "air", "cave_vines_plant": "air",
    "clay": "red_sand", "mud": "red_sand", "lily_pad": "air", "gravel": "red_sand"})


def smooth(t):
    t = np.clip(t, 0.0, 1.0)
    return t * t * (3 - 2 * t)


def rnd_for(*key):
    """形ごとの乱数 (何度走らせても同じ。区画を分けて描いても同じ形)"""
    import zlib
    return random.Random(zlib.crc32(repr(key).encode("utf-8")))


class LandVol:
    """区画 1 枚の立体 (alib.Vol と同じ書き方)。範囲の外への書き込みは捨てる"""

    def __init__(self, dump):
        self.v = alib.Vol(dump)

    def __getattr__(self, k):
        return getattr(self.v, k)


def main():
    land = sys.argv[1] if len(sys.argv) > 1 else "whale"
    only = set(sys.argv[2:])
    cfg = LANDS[land]
    relief = cfg.get("style") == "relief"
    t0 = time.time()
    dump_dir = alib.ROOT / "run" / "shots" / f"land_{land}" / "dumps"
    files = sorted(dump_dir.glob(f"{land}_*.sbd"))
    tiles = []
    for f in files:
        _, tx, tz = f.stem.rsplit("_", 2)
        tiles.append((int(tx), int(tz)))
    TX0, TX1 = min(t[0] for t in tiles), max(t[0] for t in tiles)
    TZ0, TZ1 = min(t[1] for t in tiles), max(t[1] for t in tiles)
    X0, Z0 = TX0 * T, TZ0 * T
    W, D = (TX1 - TX0 + 1) * T, (TZ1 - TZ0 + 1) * T
    print(f"{land}: {len(tiles)} tiles, box {X0},{Z0} {W}x{D}")

    # ------------------------------------------------------------ 1. 集める (区画ごとに読んで 2 次元だけ残す)
    g = np.full((D, W), -1, dtype=np.int32)
    made2d = np.zeros((D, W), dtype=bool)  # 張り出しの下の地面から 5 までに人の作った物がある列
    pathy = np.full((D, W), -1, dtype=np.int32)  # 上から 18 までのいちばん上の土の道 (道に乗った岩の下の道)
    gfloor = np.full((D, W), -1, dtype=np.int32)  # 張り出しの下の地面 (上の岩が 12 以下の厚さで、下に 4 以上の空き)。道の高さに使う
    NAMES = {}  # ブロックの名前 → 番号 (岩山の素材をまわりの地面から取る)
    top2d = np.full((D, W), -1, dtype=np.int32)
    sub2d = np.full((D, W), -1, dtype=np.int32)
    rock2d = [np.full((D, W), -1, dtype=np.int32) for _ in range(3)]
    built2d = np.zeros((D, W), dtype=bool)
    water2d = np.zeros((D, W), dtype=bool)
    road2d = np.zeros((D, W), dtype=bool)
    have = np.zeros((D, W), dtype=bool)
    sites, shrines, seen = [], {}, set()
    OY = None
    for (tx, tz), f in zip(tiles, files):
        d = alib.sbd.load(f)
        v = alib.Vol(d)
        OY = v.oy
        st = v.states
        nat = v.natural_flags() | (tree_flags(st) if relief else False)
        plant_words = ("grass", "fern", "flower", "dandelion", "poppy", "bluet", "daisy", "cornflower", "allium", "orchid", "valley",
                       "berry", "bush", "petals", "snow", "vine", "lichen", "mushroom", "_log", "leaves", "seagrass", "kelp", "roots")
        is_air = np.array([s.endswith(":air") for s in st])
        is_water = np.array([s.startswith("minecraft:water") for s in st])
        is_veg = np.array([any(w in s for w in plant_words) and not s.startswith(("minecraft:grass_block", "minecraft:rooted_dirt", "minecraft:snow_block",
                                                                                   "minecraft:mangrove_roots")) for s in st])
        if relief:
            is_veg |= tree_flags(st)
        gnd = (nat & ~is_air & ~is_water & ~is_veg)[v.ids]
        rev = gnd[::-1]
        gg = np.where(rev.any(axis=0), v.sy - 1 - rev.argmax(axis=0), -1)
        yy = np.arange(v.sy)[:, None, None]
        bb = ((~nat)[v.ids] & (yy >= (gg - 3)[None])).any(axis=0)
        ww = is_water[v.ids].any(axis=0)
        top = np.array(st)[v.ids[np.clip(gg, 0, v.sy - 1), np.arange(T)[:, None], np.arange(T)[None, :]]]
        rr = top == "minecraft:dirt_path"
        sz = slice((tz - TZ0) * T, (tz - TZ0 + 1) * T)
        sx = slice((tx - TX0) * T, (tx - TX0 + 1) * T)
        g[sz, sx] = gg
        ya = np.where((~gnd & (yy < gg[None])).any(axis=0), v.sy - 1 - (~gnd & (yy < gg[None]))[::-1].argmax(axis=0), -1)
        yb = np.where((gnd & (yy < ya[None])).any(axis=0), v.sy - 1 - (gnd & (yy < ya[None]))[::-1].argmax(axis=0), -1)
        roof = (ya >= 0) & (yb >= 0) & (gg - ya <= 12) & (ya - yb >= 4)
        gfloor[sz, sx] = np.where(roof, yb, gg)
        isp = (np.array([s_ == "minecraft:dirt_path" for s_ in st])[v.ids]) & (yy >= (gg - 18)[None]) & (yy <= gg[None])
        py_ = np.where(isp.any(axis=0), v.sy - 1 - isp[::-1].argmax(axis=0), -1)
        # 道の上に乗っているのが岩と空気 (と草) だけのときだけ (人の作った物の下の道は触らない)
        rockish = ~np.array([any(w in s_.split("[")[0] for w in MADE_WORDS) for s_ in st])
        pile_bad = (~rockish[v.ids] & (yy > py_[None]) & (yy <= gg[None])).any(axis=0)
        pathy[sz, sx] = np.where(pile_bad, -1, py_)
        gf_ = gfloor[sz, sx]
        made2d[sz, sx] = (~rockish[v.ids] & (yy >= (gf_ - 5)[None]) & (yy <= gf_[None])).any(axis=0)
        if relief:
            glob_of = np.array([NAMES.setdefault(q.split("[")[0], len(NAMES)) for q in st], dtype=np.int32)
            zi, xi = np.arange(T)[:, None], np.arange(T)[None, :]
            top2d[sz, sx] = glob_of[v.ids[np.clip(gg, 0, v.sy - 1), zi, xi]]
            sub2d[sz, sx] = glob_of[v.ids[np.clip(gg - 1, 0, v.sy - 1), zi, xi]]
            for k_, dy in enumerate((3, 5, 8)):
                rock2d[k_][sz, sx] = glob_of[v.ids[np.clip(gg - dy, 0, v.sy - 1), zi, xi]]
        built2d[sz, sx] = bb
        water2d[sz, sx] = ww
        road2d[sz, sx] = rr
        have[sz, sx] = True
        hs = d.header.get("sites", {})
        for e in hs.get("entries", []):
            k = (e["kind"], e["index"])
            if k not in seen:
                seen.add(k)
                sites.append(e)
        shrines.update(hs.get("shrines", {}))
    road2d = ndimage.binary_closing(road2d, iterations=2)
    # 道の線 (layout.json) も道に。土の道でない所 (砂利・橋のたもと) を見落とさない
    lay = json.loads((alib.ROOT / "src/main/resources/data/skyabyss/continent/layout.json").read_text(encoding="utf-8"))
    for line in lay["roads"]:
        for (ax, az), (bx, bz) in zip(line, line[1:]):
            n = max(1, int(math.ceil(math.hypot(bx - ax, bz - az))))
            for k in range(n + 1):
                px = int(math.floor(ax + (bx - ax) * k / n)) - X0
                pz = int(math.floor(az + (bz - az) * k / n)) - Z0
                if 1 <= px < W - 1 and 1 <= pz < D - 1:
                    road2d[pz - 1:pz + 2, px - 1:px + 2] = True
    print(f"gathered in {time.time() - t0:.0f}s; sites {len(sites)}, shrines {list(shrines)}")

    xs, zs = np.meshgrid(np.arange(W) + X0, np.arange(D) + Z0)
    # 土地の列 (地域の図)。地域の外と書き出していない列は守る (生成器のまま)
    size, scale, rox, roz, region = _region()
    ri = np.clip((xs - rox) // scale, 0, size - 1)
    rj = np.clip((zs - roz) // scale, 0, size - 1)
    mine = (region[rj, ri] == cfg["region"]) & have
    # チャンクに一つでも土地の列があれば、そのチャンクは全部組む (区画のチャンクは組む・組まないのどちらか)
    ch = mine.reshape(D // 16, 16, W // 16, 16).any(axis=(1, 3))
    authored = np.repeat(np.repeat(ch, 16, axis=0), 16, axis=1) & have

    # ------------------------------------------------------------ 2. 守る所と高さ (whale_town.py と同じ考え)
    cx, cz = cfg["town"]
    half = cfg["half"]
    ddx = np.maximum(np.abs(xs - cx) - half, 0)
    ddz = np.maximum(np.abs(zs - cz) - half, 0)
    dT = np.hypot(ddx, ddz)
    in_town = (np.abs(xs - cx) <= half) & (np.abs(zs - cz) <= half)
    tc = g[in_town & ~built2d]
    P = int(np.median(tc)) if tc.size else 0
    built_out = built2d & ~in_town
    dist_built = ndimage.distance_transform_edt(~built_out)
    dist_water = ndimage.distance_transform_edt(~water2d)
    road_out = road2d & ~in_town
    dist_road = ndimage.distance_transform_edt(~road_out)
    # 建った場所: その場所の人の作った物の塊 + 6 (塊が見つからなければ半径 22、章の小要素は 12)
    lab, _ = ndimage.label(ndimage.binary_dilation(built_out, iterations=4))
    site_keep = np.zeros((D, W), dtype=bool)
    for e in sites:
        if e["kind"] == "town" and e["index"] == 0:
            continue
        ex, ez = e["pos"][0] - X0, e["pos"][2] - Z0
        if not (0 <= ex < W and 0 <= ez < D):
            continue
        win = lab[max(0, ez - 12):ez + 13, max(0, ex - 12):ex + 13]
        ids_ = np.unique(win[win > 0])
        if len(ids_):
            site_keep |= ndimage.binary_dilation(np.isin(lab, ids_), iterations=6)
        else:
            r = 12 if e["kind"].startswith("ch:") else 22
            site_keep |= (xs - e["pos"][0]) ** 2 + (zs - e["pos"][2]) ** 2 < r * r
    for (lx, lz) in cfg["lairs"]:
        site_keep |= (xs - lx) ** 2 + (zs - lz) ** 2 < 90 ** 2
    # 巨神の体の下となじませの幅・祠の広場 (lairs.json)。書き出した世界の地面がもう体の下を下げ、広場をならしている (自己テスト titan lairs)
    # 生成器の「平らに残す所」(ContinentMap の flats: 都 175・始まり 30・船着き場と渡し場 24・町 100) も。岩鯨の町は町の四角 (in_town) で扱う
    zone = LAIRS.zone(X0, Z0, W, D)
    flats = [(lay["capital"], 175), (lay["start"], 30), (lay["dock"], 24)] + [(p, 24) for f_ in lay["ferry_docks"] for p in f_]
    # 章の跡地のまわり (要素は跡地から 150 以内に置く)。起伏で平らな所が減ると、骨の隠し場や碑が斜面に置かれて沈んだ (自己テスト build audit)
    flats += [(p, 160) for p in lay["sites"].values()]
    if relief:
        flats += [(p, 100) for p in lay["towns"].values()]
    for (fx_, fz_), r_ in flats:
        zone |= (xs - fx_) ** 2 + (zs - fz_) ** 2 <= r_ * r_
    zone &= ~in_town
    no_road_fix = site_keep | LAIRS.zone(X0, Z0, W, D)  # 道のならしをしない所 (平らに残す所の道はならす)
    site_keep |= zone
    dist_site = ndimage.distance_transform_edt(~site_keep)
    keep = ~mine | in_town | (dist_built <= 3) | (dist_water <= 3) | site_keep
    if relief:
        # 小さな守る島 (野の飾り 1 つ: 骨・小さな崩れ跡など) は地面といっしょに動かす (取り残されると穴になる)
        isl = keep & mine & ~site_keep & (dist_water > 3)
        lab_k, nk = ndimage.label(isl)
        if nk:
            area = ndimage.sum(np.ones_like(lab_k), lab_k, np.arange(1, nk + 1))
            small = np.zeros(nk + 1, dtype=bool)
            small[1:] = area < 150
            keep &= ~small[lab_k]
        keep |= dist_road <= 2.5  # 道は生成器のならしのまま (上下させない)
    # 建物のある所: 岩の下の木を消す手・張り出しの下の隙間を埋める手を使わない (都の梁の黒樫の丸太が木として消え、吊り灯の下が埋まった)
    protect = site_keep | (dist_built <= 3) | in_town
    dist_nl = ndimage.distance_transform_edt(mine)  # 土地の外 (海・ほかの地域) からの距離
    edge = dist_nl
    print(f"town ground {P + OY}; land cols {int(mine.sum())}")

    w_edge = smooth((edge - MARGIN) / 48)
    w_town = smooth((dT - 6) / 34)
    w_built = smooth((dist_built - 3) / 16) * smooth(dist_site / 14)
    w_water = smooth((dist_water - 3) / 12)
    w_road = smooth((dist_road - 6) / 16)
    ridge = S.ridged(xs, zs, 150, 11) ** 1.7
    hills = 22 * ridge + 7 * S.fbm(xs, zs, 70, 12) - 6
    terr = hills if relief else np.where(ridge > 0.55, np.floor(hills / 3) * 3, hills)
    delta = terr * w_edge * w_town * w_built * w_water * w_road
    bump = smooth(dT / 3) * (1 - smooth((dT - 10) / 26))
    ramp = smooth(dT / 24) * (1 - smooth((dT - 26) / 14))
    roadness = 1 - smooth((dist_road - 4) / 10)
    low = -5.0 * (bump * (1 - roadness) + ramp * roadness) * w_edge
    Hf = g + ndimage.gaussian_filter(delta + low, 2.0)
    Hroad = ndimage.gaussian_filter(Hf, 7)
    core = np.clip(1 - (dist_road - 3.5) / 8, 0, 1)
    Hf = Hf * (1 - core) + Hroad * core
    # 守る所 (体の下・祠の広場・平らに残す所) の縁は 16 かけて書き出しの地面へなじませる (町のそばを下げる分 low が縁で崖になった)
    wz = smooth(ndimage.distance_transform_edt(~zone) / 16)
    Hf = g + (Hf - g) * wz
    Hroad = g + (Hroad - g) * wz
    H = np.floor(Hf + 0.5).astype(np.int32)
    road_base = np.floor(Hroad).astype(np.int32)
    road_half = (Hroad - road_base) >= 0.5
    H = np.where(keep, g, H)
    H = np.maximum(H, 2)
    if relief:
        H = np.minimum(H, np.maximum(g, 250 - OY - 6))  # 起伏は切らない (立体の上の端だけ)
    else:
        H = np.minimum(H, np.maximum(g, 250 - OY - 70))  # 上げるのは 180 まで。元から高い山は削らない
    road_base = np.where(keep, g, road_base)
    road_half = road_half & ~keep  # 守る列の道に半ブロックは敷かない (岩の門の下を空ける高さもこれで決まる)
    dr = dist_road
    del w_edge, w_town, w_water, w_road, hills, terr, delta, bump, ramp, roadness, low, Hf, Hroad, core

    # 小さなくぼみを埋める: 3 離れた 4 方向のどれよりも 2 以上低い列は、その低いほう - 1 まで上げる (自己テストの穴)
    for _ in range(3):
        P3 = np.pad(H, 3, mode="edge")
        ring = np.minimum.reduce([P3[3:-3, 6:], P3[3:-3, :-6], P3[6:, 3:-3], P3[:-6, 3:-3]])
        H = np.where(~keep & (H <= ring - 2), ring - 1, H)
    # ------------------------------------------------------------ 3. 池 (土地に数か所。町の南東の池を含む)
    feats = []  # (種類, x, z, 半径, 引数)
    pond_wl = np.full((D, W), -10**6, dtype=np.int32)
    pond_bot = np.full((D, W), -10**6, dtype=np.int32)
    extra_zone = np.zeros((D, W), dtype=bool)
    ok = ~keep & (dist_road > 22) & (dT > 55) & (edge > 50) & (dist_site > 24) & (dist_built > 24)
    dist_keep_pond = ndimage.distance_transform_edt(~keep)
    targets = list(cfg["ponds"])
    cand = np.argwhere(ok)
    hrng = random.Random(77)
    tries = 0
    while len(targets) < cfg["n_ponds"] and len(cand) and tries < 3000:
        tries += 1
        k = hrng.randrange(len(cand))
        tz_, tx_ = cand[k]
        p = (tx_ + X0, tz_ + Z0)
        if all((p[0] - a) ** 2 + (p[1] - b) ** 2 > 260 ** 2 for a, b in targets):
            targets.append(p)
    for pi, (px, pz) in enumerate(targets):
        k = int(np.argmin((cand[:, 0] + Z0 - pz) ** 2 + (cand[:, 1] + X0 - px) ** 2))
        pz0, px0 = cand[k]
        win = (slice(max(0, pz0 - 40), pz0 + 41), slice(max(0, px0 - 40), px0 + 41))
        wx, wz = xs[win], zs[win]
        ang = np.arctan2(wz - (pz0 + Z0), wx - (px0 + X0))
        rr = np.hypot(wz - (pz0 + Z0), wx - (px0 + X0))
        rr_ = rnd_for("pond", pi)
        shape_r = 13 + 3.5 * np.sin(ang * 2 + rr_.uniform(0, 6)) + 2.0 * np.sin(ang * 5 + rr_.uniform(0, 6))
        pond = (rr < shape_r) & ok[win]
        rim = (rr < shape_r + 3) & ~pond & ~keep[win]
        if not pond.any():
            continue
        wl = int(np.min(H[win][rim])) - 1 if rim.any() else int(H[pz0, px0]) - 1
        depth = np.clip((shape_r - rr) / 3.0, 1, 3).astype(int)
        pw, pb, hw = pond_wl[win], pond_bot[win], H[win]
        pw[pond] = wl
        pb[pond] = wl - depth[pond]
        hw[pond] = wl - depth[pond]
        lower = rim & (rr < shape_r + 1.5) & (hw > wl)
        hw[lower] = wl
        extra_zone[win] |= rr < shape_r + 6
        top_r = int(np.max(shape_r[pond]))
        cl_x, cl_z = px0 + X0, pz0 + Z0 - top_r - 11
        wi, wk = cl_z - Z0, cl_x - X0
        if 0 <= wi < D and 0 <= wk < W and dist_road[wi, wk] > 24 and dist_keep_pond[wi, wk] > 16:  # 滝の岩は道と守る所から離す
            feats.append(("waterfall", cl_x, cl_z, 40, {"wl": wl + OY, "pond": pi}))
        feats.append(("boardwalk", px0 + X0, pz0 + Z0 + 2, 34, {"wl": wl + OY, "pond": pi}))
        cz_ = slice(max(0, cl_z - Z0 - 26), cl_z - Z0 + 27)
        cx_ = slice(max(0, cl_x - X0 - 30), cl_x - X0 + 31)
        extra_zone[cz_, cx_] = True
    print(f"ponds {len(targets)}")

    # ------------------------------------------------------------ 4. 道をまたぐ岩の門 (町と建った場所から離れた道の上)
    flat_arch = np.ones((D, W), dtype=bool)
    if relief:  # 急な道に門を架けると壁になる
        flat_arch = (ndimage.maximum_filter(H, size=41) - ndimage.minimum_filter(H, size=41)) < 10
    arch_cand = np.argwhere(flat_arch & road_out & (~keep | (relief & (dist_road <= 2.5))) & (dT > 70) & (edge > 30) & (dist_site > 35) & (dist_built > 25))
    arches = []
    for k in np.argsort(S._hash2(arch_cand[:, 1], arch_cand[:, 0], 5)):
        iz, ix = arch_cand[k]
        if any((iz - a) ** 2 + (ix - b) ** 2 < 300 ** 2 for a, b in arches):
            continue
        arches.append((iz, ix))
        near = np.argwhere(road_out[max(0, iz - 8):iz + 9, max(0, ix - 8):ix + 9])
        w_, vec = np.linalg.eigh(np.cov(near.T.astype(float)))
        dz_, dx_ = vec[:, -1]
        nx_, nz_ = -dz_, dx_
        x, z = ix + X0, iz + Z0
        ax, az = int(round(x + nx_ * 20)), int(round(z + nz_ * 20))
        bx, bz = int(round(x - nx_ * 20)), int(round(z - nz_ * 20))
        if not (0 <= az - Z0 < D and 0 <= bz - Z0 < D and 0 <= ax - X0 < W and 0 <= bx - X0 < W):
            continue
        ay, by = int(H[az - Z0, ax - X0]) + OY, int(H[bz - Z0, bx - X0]) + OY
        if relief and abs(ay - by) > 6:
            continue
        if int(road_base[iz, ix]) + OY + 22 + 16 > 245:  # 門のてっぺんが立体の上 (Y 250) で切れない高さだけ
            continue
        lim = 14 if relief else 8  # 起伏の土地は脚の岩が広がるので離す (灰の崖の道で脚が道に乗った)
        if dist_road[az - Z0, ax - X0] < lim or dist_road[bz - Z0, bx - X0] < lim:  # 門の脚が曲がった道の上に立たない
            continue
        if relief:  # 門の下の道が 5 より上り下りしない (崖をまたぐ道では門の腹が道に近づく)
            hh_ = H[max(0, iz - 28):iz + 29, max(0, ix - 28):ix + 29][road_out[max(0, iz - 28):iz + 29, max(0, ix - 28):ix + 29]]
            if hh_.size and int(hh_.max()) - int(hh_.min()) > 5:
                continue
        ry = int(road_base[iz, ix]) + OY
        feats.append(("arch", x, z, 34, {"a": (ax, ay, az), "b": (bx, by, bz), "rise": (ry + 22) - (ay + by) / 2, "n": len(arches)}))
        extra_zone |= (xs - x) ** 2 + (zs - z) ** 2 < 30 * 30
        if len(arches) >= cfg["n_arches"]:
            break
    clear_from = np.full((D, W), 10**6, dtype=np.int32)  # この高さより上 (15 まで) を空ける (門の下の道)
    for f in feats:
        if f[0] != "arch":
            continue
        m_ = ((xs - f[1]) ** 2 + (zs - f[2]) ** 2 < 28 * 28) & (dr <= 3.5)
        base = np.where(road_out, road_base + 1 + road_half.astype(int), H + 1)
        clear_from[m_] = base[m_]
    print(f"arches {sum(1 for f in feats if f[0] == 'arch')}")

    # ------------------------------------------------------------ 5. 岩山 (大・小。町から見える目印)
    dist_keep = ndimage.distance_transform_edt(~(keep | extra_zone))
    free = ~keep & ~extra_zone & (dist_road > 14) & (dT > 45) & (edge > 30)
    if relief:  # 山の中では岩山の裾野が谷を埋めて台地になるので、平らに近い所 (半径 30 の高低差 16 未満) だけ
        rng = ndimage.maximum_filter(H, size=61) - ndimage.minimum_filter(H, size=61)
        free &= rng < 16
        free &= H + OY < 175  # 岩山の頭が立体の上に出ない高さだけ
    # 自己テスト continent の穴を見る 5 か所 (ふつうの地面の見本) には岩山を置かない (岩鯨でも、脇の小さな岩が穴に数えられた)
    for (wx, wz) in ((-324, 444), (1184, 224), (-512, 812), (300, 1100), (-100, -600)):
        free &= (np.abs(xs - wx) > 150) | (np.abs(zs - wz) > 150)
    spires = []
    for big, n_want, need in ((True, cfg["n_big"], 30), (False, cfg["n_small"], 16)):
        cand = np.argwhere(free & (dist_keep > need * (2 if relief else 1)) & (dist_road > need * 1.5))
        order_c = np.argsort(-(ridge[cand[:, 0], cand[:, 1]] + 0.3 * S._hash2(cand[:, 1], cand[:, 0], 13 if big else 14)))
        got = 0
        for k in order_c:
            iz, ix = cand[k]
            if any((iz - a) ** 2 + (ix - b) ** 2 < (95 if big or bb else 60) ** 2 for a, b, bb in spires):
                continue
            spires.append((iz, ix, big))
            got += 1
            if got >= n_want:
                break
    rock_zone = np.zeros((D, W), dtype=bool)
    for si, (iz, ix, big) in enumerate(spires):
        x, z = ix + X0, iz + Z0
        rr_ = rnd_for("massif", si)
        if big:
            args = {"radius": rr_.uniform(26, 32), "height": rr_.uniform(50, 62), "peaks": 3}
        else:
            args = {"radius": rr_.uniform(11, 15), "height": rr_.uniform(22, 30), "peaks": 2}
        args["base"] = int(H[iz, ix]) + OY - 3
        feats.append(("massif", x, z, int(args["radius"] * 2.5) + 4, args))
        r = 40 if big else 20
        win = (slice(max(0, iz - r), iz + r + 1), slice(max(0, ix - r), ix + r + 1))
        rock_zone[win] |= (xs[win] - x) ** 2 + (zs[win] - z) ** 2 < r * r
    print(f"massifs {len(spires)}")
    # 岩山の裾野を盛ってよい重み (道と守る所のそばは 0)
    hold_w = smooth((ndimage.distance_transform_edt(~(keep | road2d)) - 2) / 12)

    # ------------------------------------------------------------ 6. 木 (4 種 + 灯り葉の樹の林)
    nmin = ndimage.minimum_filter(H, footprint=np.array([[0, 1, 0], [1, 1, 1], [0, 1, 0]]), mode="nearest")
    expo = H - nmin
    forest = S.fbm(xs, zs, 90, 31)
    grove = S.fbm(xs, zs, 55, 33)
    birchy = S.fbm(xs, zs, 50, 32)
    occupied = np.zeros((D, W), dtype=bool)
    plantable = ~keep & ~rock_zone & ~extra_zone & (dist_road > 4) & (dT > 7) & (expo < 2) & (edge > MARGIN + 6) & (dist_built > 9)
    if relief:
        plantable[:] = False  # 木は書き出しの木 (Java) のまま
    pts = np.argwhere(plantable)
    order = np.argsort(S._hash2(pts[:, 1] + X0, pts[:, 0] + Z0, 77))
    n_trees = 0
    for k in order:
        iz, ix = pts[k]
        f = forest[iz, ix]
        dens = 0.3 + (f - 0.55) * 1.5 if f > 0.55 else (0.05 if f > 0.45 else 0.006)
        if S._hash2(int(ix), int(iz), 5) > dens:
            continue
        r = 5 if f > 0.6 else (6 if f > 0.45 else 8)
        y0_, y1_, x0_, x1_ = max(0, iz - r), iz + r + 1, max(0, ix - r), ix + r + 1
        if occupied[y0_:y1_, x0_:x1_].any():
            continue
        x, z = int(ix + X0), int(iz + Z0)
        y = int(H[iz, ix]) + 1 + OY
        roll = S._hash2(x, z, 91)
        if grove[iz, ix] > 0.66 and f > 0.45 and dT[iz, ix] > 25 and dist_built[iz, ix] > 14 and edge[iz, ix] > 20 and roll < 0.55:
            kind, rad = "teal", 16
            occupied[max(0, iz - 11):iz + 12, max(0, ix - 11):ix + 12] = True
        elif f > 0.58 and roll < 0.38 and dT[iz, ix] > 30 and dist_built[iz, ix] > 14 and edge[iz, ix] > 20:
            kind, rad = "fir_big", 12
            occupied[max(0, iz - 9):iz + 10, max(0, ix - 9):ix + 10] = True
        elif birchy[iz, ix] > 0.62 and f < 0.6:
            kind, rad = "pale", 8
            occupied[y0_:y1_, x0_:x1_] = True
        elif f < 0.45 and roll < 0.5:
            kind, rad = "moss_low", 11
            occupied[max(0, iz - 7):iz + 8, max(0, ix - 7):ix + 8] = True
        else:
            kind, rad = "fir", 8
            occupied[y0_:y1_, x0_:x1_] = True
        feats.append(("tree", x, z, rad, {"kind": kind, "y": y, "seed": n_trees}))
        n_trees += 1
    # 岩と茂み
    for k in order[::37]:
        iz, ix = pts[k]
        if occupied[iz, ix] or S._hash2(int(ix), int(iz), 6) > 0.35:
            continue
        x, z = int(ix + X0), int(iz + Z0)
        feats.append(("bush" if forest[iz, ix] > 0.5 else "boulder", x, z, 5, {"y": int(H[iz, ix]) + 1 + OY}))
        occupied[max(0, iz - 3):iz + 4, max(0, ix - 3):ix + 4] = True
    print(f"trees {n_trees}; features {len(feats)}; 2D done in {time.time() - t0:.0f}s")

    # 道の段をならす: 道の列どうしで 2 以上の段があれば、低いほうを上げる (乗り物が越えられる 1 段ずつに)
    R = np.where(keep, g, np.where(road_out, road_base, H)) if not relief else np.where(keep, g, H)
    if relief:
        # 土の道が岩に埋まって消えた列は、まわり 2 の道の高さを使う (岩が 1〜4 段乗った所だけ)
        for _ in range(8):  # 埋もれた道が長いときは、道に沿って少しずつ広げる
            nb = ndimage.maximum_filter(np.where(road2d, pathy, -1), size=5)
            fill = road2d & (pathy < 0) & ~made2d & (nb >= 0) & (gfloor - nb >= 1) & (gfloor - nb <= 4)
            if not fill.any():
                break
            pathy = np.where(fill, nb, pathy)
        gfloor = np.where(road2d & (pathy >= 0), pathy, gfloor)
    R = np.where(road2d & keep, gfloor, R)  # 岩の張り出しの下を通る道は、岩の上でなく道の高さで (灰の崖の道で岩が道に落ちた)
    roadc = road2d & mine & ~in_town & ~no_road_fix & ((dist_built > 1) | relief) & (g + OY >= 62) & ~(pond_wl > -10**5)
    Rf = R.copy()
    for _ in range(80):
        M = np.where(roadc, Rf, -10**6)
        up = ndimage.maximum_filter(M, size=3) - 1
        nxt = np.where(roadc, np.minimum(np.maximum(Rf, up), R + 3), Rf)  # 上げるのは 3 まで (崖をまたぐ道は上げと削りの両方で坂にする)
        if (nxt == Rf).all():
            break
        Rf = nxt
    for _ in range(80):
        M = np.where(roadc, Rf, 10**6)
        dn = ndimage.minimum_filter(M, size=3) + 1
        nxt = np.where(roadc, np.maximum(np.minimum(Rf, dn), R - 5), Rf)  # 削るのは 5 まで (深い溝にしない)
        if (nxt == Rf).all():
            break
        Rf = nxt
    lift = np.where(roadc, Rf - R, 0).astype(np.int32)
    print(f"road steps lifted: {int((lift > 0).sum())} cols, cut: {int((lift < 0).sum())} cols")
    if os.environ.get("LAND_DEBUG_PT"):
        px, pz = (int(t) for t in os.environ["LAND_DEBUG_PT"].split(","))
        w = (slice(pz - Z0 - 3, pz - Z0 + 4), slice(px - X0 - 3, px - X0 + 4))
        for nm, arr in (("H", H + OY), ("g", g + OY), ("Rf", Rf + OY), ("roadc", roadc.astype(int)), ("keep", keep.astype(int)), ("road_out", road_out.astype(int)), ("site_keep", site_keep.astype(int)), ("dist_built", dist_built.astype(int)), ("road2d", road2d.astype(int)), ("pathy", pathy + OY), ("gfloor", gfloor + OY), ("made2d", made2d.astype(int))):
            print(nm)
            print(arr[w])
    if relief:
        NAME = {i: n for n, i in NAMES.items()}
        ROCKW = ("stone", "andesite", "diorite", "granite", "tuff", "calcite", "deepslate", "basalt", "blackstone", "terracotta",
                 "sandstone")
        GROUNDW = ("grass_block", "snow_block", "sand", "coarse_dirt", "podzol", "dirt", "gravel", "moss_block", "mud", "clay",
                   "terracotta", "stone", "andesite", "calcite", "basalt", "blackstone", "sandstone", "tuff", "packed_ice")
        NOTW = ("ore", "infested", "cobbled", "polished", "brick", "path", "slab", "stairs", "wall", "smooth_stone")

        def pal(arrs, win, words, need=60):
            vals = np.concatenate([a_[win].ravel() for a_ in arrs])
            vals = vals[vals >= 0]
            u, c = np.unique(vals, return_counts=True)
            got = [(NAME[int(i)], int(k)) for i, k in zip(u, c)
                   if any(w in NAME[int(i)] for w in words) and not any(w in NAME[int(i)] for w in NOTW)]
            got.sort(key=lambda t: -t[1])
            tot = sum(k for _, k in got)
            if tot < need:
                return None
            return [n for n, k in got[:4] if k / tot >= 0.06]
        for f in feats:
            if f[0] not in ("massif", "waterfall", "arch"):
                continue
            ix_, iz_ = f[1] - X0, f[2] - Z0
            win = (slice(max(0, iz_ - 60), iz_ + 61), slice(max(0, ix_ - 60), ix_ + 61))
            f[4]["pal"] = {"rock": pal(rock2d, win, ROCKW), "top": pal([top2d], win, GROUNDW), "sub": pal([sub2d], win, GROUNDW)}
        print("palettes:", [(f[0], f[4]["pal"]) for f in feats if "pal" in f[4]][:3])
    # 撮る場所を選ぶための形の一覧 (gen_land_shots.py が読む)
    (dump_dir.parent / "feats.json").write_text(json.dumps({"feats": [[f[0], int(f[1]), int(f[2]), int(H[int(f[2]) - Z0, int(f[1]) - X0]) + OY if 0 <= f[2] - Z0 < D and 0 <= f[1] - X0 < W else 120] for f in feats if f[0] != "tree"],
                                                       "sites": [[e["kind"], e["pos"][0], e["pos"][2], e["pos"][1]] for e in sites]}), encoding="utf-8")
    # ------------------------------------------------------------ 7. 区画ごとに組む
    strip_cols = keep & ~in_town & (dist_built > 1) & mine
    lamp_spots = np.argwhere((np.abs(dr - 3.0) < 0.5) & ~keep & ~in_town & (dT > 4))
    taken = []
    paved = dT < 36
    lamps = np.zeros((D, W), dtype=bool)
    for (iz, ix) in lamp_spots[np.argsort(S._hash2(lamp_spots[:, 1], lamp_spots[:, 0], 9))]:
        gap = 11 if paved[iz, ix] else 24
        if any((iz - a) ** 2 + (ix - b) ** 2 < gap * gap for a, b in taken[-400:]):
            continue
        taken.append((iz, ix))
        lamps[iz, ix] = True
    patch = S.fbm(xs, zs, 24, 21)
    patch2 = S.fbm(xs, zs, 40, 22)
    flower_patch = S.fbm(xs, zs, 18, 41)
    which = S.fbm(xs, zs, 33, 42)
    written = []
    for (tx, tz), fpath in zip(tiles, files):
        if only and f"{tx}_{tz}" not in only:
            written.append((tx, tz))
            continue
        sz = slice((tz - TZ0) * T, (tz - TZ0 + 1) * T)
        sx = slice((tx - TX0) * T, (tx - TX0 + 1) * T)
        if not authored[sz, sx].any():
            continue
        v = alib.Vol(alib.sbd.load(fpath))
        (build_tile_relief if relief else build_tile)(v, locals(), sz, sx)
        alib.write_tile(land, tx, tz, v.ids, v.states, v.oy, authored[sz, sx])
        written.append((tx, tz))
        print(f"tile {tx},{tz} done ({time.time() - t0:.0f}s)")
    alib.write_land_index(land, written, {"entries": sites, "shrines": shrines})
    total = sum(p.stat().st_size for p in (alib.OUT_DIR / land).glob("*.bin"))
    print(f"wrote {len(written)} tiles, {total // 1024} KB, in {time.time() - t0:.0f}s")


def _region():
    import gzip
    import struct
    b = gzip.decompress((alib.ROOT / "src/main/resources/data/skyabyss/continent/region.bin").read_bytes())
    size, scale, ox, oz = struct.unpack(">4i", b[4:20])
    return size, scale, ox, oz, np.frombuffer(b[20:], dtype=np.uint8).reshape(size, size)


def build_tile(v, L, sz, sx):
    """区画 1 枚を組む。L は main の局所変数 (土地全体の 2 次元の図)"""
    ids = v.ids
    SY = v.sy
    OY = v.oy
    X0, Z0 = L["X0"], L["Z0"]
    sl = lambda a: a[sz, sx]  # noqa: E731
    keep, H, expo, nmin = sl(L["keep"]), sl(L["H"]), sl(L["expo"]), sl(L["nmin"])
    patch, patch2 = sl(L["patch"]), sl(L["patch2"])
    air = v.id("minecraft:air")
    grass = v.id("minecraft:grass_block[snowy=false]")
    dirt = v.id("minecraft:dirt")
    stone = v.id("minecraft:stone")
    podzol = v.id("minecraft:podzol[snowy=false]")
    moss = v.id("minecraft:moss_block")
    coarse = v.id("minecraft:coarse_dirt")
    # 古い木を消す (残す列の、幹を切られた木)
    veg_ids = np.nonzero(np.array([("_log" in s or "leaves" in s) and s.startswith(("minecraft:spruce", "minecraft:oak",
                                                                                      "minecraft:birch", "minecraft:dark_oak"))
                                   for s in v.states]))[0]
    ids[np.isin(ids, veg_ids) & sl(L["strip_cols"])[None]] = air
    # 雪をどけて草に (土地の列だけ)
    snow_ids = [v.id(s) for s in ("minecraft:snow[layers=1]",)] + [i for i, s in enumerate(v.states) if s.startswith("minecraft:snow")]
    mine = sl(L["mine"])
    ids[np.isin(ids, snow_ids) & mine[None] & (np.arange(SY)[:, None, None] < 165 - OY)] = air
    for i, s in enumerate(list(v.states)):
        if s.startswith("minecraft:grass_block[snowy=true") or s.startswith("minecraft:podzol[snowy=true"):
            ids[(ids == i) & mine[None]] = v.id(s.replace("snowy=true", "snowy=false"))
    # 地面を積み直す
    for (iz, ix) in np.argwhere(~keep):
        h = int(H[iz, ix])
        col = ids[:, iz, ix]
        col[h + 1:] = air
        col[:max(0, h - 4)] = stone
        x, z = ix + sx.start + X0, iz + sz.start + Z0
        e = int(expo[iz, ix])
        col[max(0, h - 4):h] = dirt
        if e >= 2:
            for y in range(max(0, int(nmin[iz, ix]) - 1), h):
                col[y] = v.id(S.strata(x, y + OY, z, 3))
        r = S._hash2(x, z, 61)
        if e >= 4 and r < 0.55:
            col[h] = v.id(S.strata(x, h + OY, z, 3))
        elif e >= 2 and r < 0.15:
            col[h] = coarse if patch[iz, ix] > 0.5 else moss
        elif patch[iz, ix] > 0.82:
            col[h] = podzol
        elif patch2[iz, ix] > 0.78:
            col[h] = moss
        else:
            col[h] = grass
    # 池
    pw, pb = sl(L["pond_wl"]), sl(L["pond_bot"])
    water = v.id("minecraft:water")
    for (iz, ix) in np.argwhere(pw > -10**5):
        x, z = ix + sx.start + X0, iz + sz.start + Z0
        bot, wl = int(pb[iz, ix]), int(pw[iz, ix])
        ids[bot + 1:, iz, ix] = air
        ids[bot, iz, ix] = v.id(["minecraft:clay", "minecraft:gravel", "minecraft:mud", "minecraft:sand"][int(S._hash2(x, z, 81) * 4)])
        ids[bot + 1:wl + 1, iz, ix] = water
        if S._hash2(x, z, 82) < 0.06:
            ids[wl + 1, iz, ix] = v.id("minecraft:lily_pad")
    # 町の石垣
    _town_wall(v, L, sz, sx)
    # 道
    dr, road_out, road_base, road_half = sl(L["dr"]), sl(L["road_out"]), sl(L["road_base"]), sl(L["road_half"])
    paved, in_town = sl(L["paved"]), sl(L["in_town"])
    pave = ["minecraft:cobblestone", "minecraft:mossy_cobblestone", "minecraft:stone_bricks", "minecraft:andesite",
            "minecraft:cobblestone", "minecraft:mossy_stone_bricks", "minecraft:gravel"]
    pave_slab = {"minecraft:cobblestone": "cobblestone_slab", "minecraft:mossy_cobblestone": "mossy_cobblestone_slab",
                 "minecraft:stone_bricks": "stone_brick_slab", "minecraft:andesite": "andesite_slab",
                 "minecraft:mossy_stone_bricks": "mossy_stone_brick_slab", "minecraft:gravel": "cobblestone_slab"}
    for (iz, ix) in np.argwhere((dr > 0) & (dr <= 2) & ~keep & ~in_town):
        h = int(H[iz, ix])
        x, z = ix + sx.start + X0, iz + sz.start + Z0
        r = S._hash2(x, z, 41)
        if paved[iz, ix] and dr[iz, ix] <= 1.0:
            ids[h, iz, ix] = v.id("minecraft:stone_bricks" if r < 0.6 else "minecraft:mossy_stone_bricks")
            continue
        ids[h, iz, ix] = v.id("minecraft:grass_block[snowy=false]" if r < 0.7 else ("minecraft:gravel" if r < 0.82 else
                              ("minecraft:coarse_dirt" if r < 0.92 else "minecraft:moss_block")))
    for (iz, ix) in np.argwhere(road_out & ~keep):
        h = int(road_base[iz, ix])
        x, z = ix + sx.start + X0, iz + sz.start + Z0
        r = S._hash2(x, z, 42)
        ids[h + 1:, iz, ix] = air
        ids[max(0, h - 3):h, iz, ix] = dirt
        if paved[iz, ix]:
            b = pave[int(r * len(pave))]
            ids[h, iz, ix] = v.id(b)
            if road_half[iz, ix]:
                ids[h + 1, iz, ix] = v.id(f"minecraft:{pave_slab[b]}[type=bottom,waterlogged=false]")
        else:
            ids[h, iz, ix] = v.id("minecraft:dirt_path" if r > 0.08 else "minecraft:coarse_dirt")
    _lift_columns(v, sl(L["lift"]), sl(L["R"]))
    for (iz, ix) in np.argwhere(sl(L["lamps"])):
        h = int(H[iz, ix])
        if ids[h + 1, iz, ix] != air:
            continue
        for k in range(1, 4):
            ids[h + k, iz, ix] = v.id("minecraft:spruce_fence[east=false,north=false,south=false,waterlogged=false,west=false]")
        ids[h + 4, iz, ix] = v.id("minecraft:lantern[hanging=false,waterlogged=false]")
        ids[h, iz, ix] = v.id("minecraft:mossy_cobblestone")
    # 形 (区画にかかるものだけ。種は形ごと)
    x0, z0 = sx.start + X0, sz.start + Z0
    for f in L["feats"]:
        kind, fx, fz, rad, a = f
        if fx + rad < x0 or fx - rad >= x0 + alib.TILE or fz + rad < z0 or fz - rad >= z0 + alib.TILE:
            continue
        rnd = rnd_for(kind, fx, fz)
        if kind == "massif":
            B.massif(v, fx, a["base"], fz, rnd, radius=a["radius"], height=a["height"], peaks=a["peaks"], hold=_hold(L))
        elif kind == "waterfall":
            _waterfall(v, fx, fz, a["wl"], rnd)
        elif kind == "boardwalk":
            _boardwalk(v, L, fx, fz, a["wl"])
        elif kind == "arch":
            B.big_arch(v, *a["a"], *a["b"], rise=a["rise"], rnd=rnd, thick=7.0)
        elif kind == "tree":
            t = a["kind"]
            if t == "teal":
                B.teal_lantern_tree(v, fx, a["y"], fz, rnd)
            elif t == "fir_big":
                B.weeping_fir(v, fx, a["y"], fz, rnd, big=True)
            elif t == "fir":
                B.weeping_fir(v, fx, a["y"], fz, rnd)
            elif t == "pale":
                B.pale_slender(v, fx, a["y"], fz, rnd)
            else:
                B.moss_low_tree(v, fx, a["y"], fz, rnd)
        elif kind == "bush":
            S.bush(v, fx, a["y"], fz, rnd, "spruce")
        elif kind == "boulder":
            S.boulder(v, fx, a["y"], fz, rnd)
    # 門の下の道を空ける
    cf = sl(L["clear_from"])
    lf = sl(L["lift"])
    cf = np.where((lf != 0) & (cf < 10**5), np.maximum(cf, sl(L["R"]) + lf + 1), cf)  # 道を上げ下げした列は、その上から空ける
    for (iz, ix) in np.argwhere(cf < 10**5):
        ids[int(cf[iz, ix]):int(cf[iz, ix]) + 15, iz, ix] = air
    # 下草と花
    fp, wh, forest = sl(L["flower_patch"]), sl(L["which"]), sl(L["forest"])
    for (iz, ix) in np.argwhere(~keep):
        h = int(H[iz, ix])
        if h + 2 >= SY or ids[h + 1, iz, ix] != air or ids[h, iz, ix] not in (grass, podzol, moss):
            continue
        x, z = ix + x0, iz + z0
        r = S._hash2(x, z, 51)
        f = forest[iz, ix]
        if fp[iz, ix] > 0.64 and f < 0.55 and r < 0.45:
            ids[h + 1, iz, ix] = v.id(S.FLOWERS[int(wh[iz, ix] * 7) % 7])
        elif f > 0.55 and r < 0.16:
            ids[h + 1, iz, ix] = v.id("minecraft:fern")
        elif f > 0.55 and r < 0.24 and ids[h, iz, ix] == moss:
            ids[h + 1, iz, ix] = v.id("minecraft:moss_carpet")
        elif r < 0.30:
            ids[h + 1, iz, ix] = v.id("minecraft:short_grass")
        elif r < 0.34:
            ids[h + 1, iz, ix] = v.id("minecraft:tall_grass[half=lower]")
            ids[h + 2, iz, ix] = v.id("minecraft:tall_grass[half=upper]")
    # 横に空気がある土の面は石の縞に (区画の中だけ)
    soil = np.isin(ids, [v.id(s) for s in ("minecraft:dirt", "minecraft:grass_block[snowy=false]", "minecraft:coarse_dirt",
                                            "minecraft:podzol[snowy=false]", "minecraft:rooted_dirt")])
    airv = ids == air
    side = np.zeros_like(airv)
    side[:, 1:, :] |= airv[:, :-1, :]
    side[:, :-1, :] |= airv[:, 1:, :]
    side[:, :, 1:] |= airv[:, :, :-1]
    side[:, :, :-1] |= airv[:, :, 1:]
    above_solid = np.zeros_like(airv)
    above_solid[:-1] = ~airv[1:]
    near_built = sl(L["dist_built"]) <= 0.5
    face = soil & side & above_solid & (~sl(L["in_town"]) & ~near_built & mine)[None]
    for (iy, iz, ix) in np.argwhere(face):
        ids[iy, iz, ix] = v.id(S.strata(ix + x0, iy + OY, iz + z0, 3))


# ------------------------------------------------------------ 起伏だけの土地 (style relief)
_TREE = None


# 道に乗った岩を除くとき、これが混じる列は人の作った物として触らない
MADE_WORDS = ("brick", "plank", "_log", "_wood", "slab", "stairs", "_wall", "fence", "lantern", "torch", "sign", "door", "glass", "wool",
              "chain", "bars", "chest", "barrel", "campfire", "bed", "polished_basalt", "smooth_stone", "chiseled", "cut_", "carpet",
              "banner", "candle", "bell", "anvil", "table", "pot", "rail", "ladder", "scaffold", "hay", "terracotta_glazed", "concrete")


def tree_names():
    """木の部品 (Java の土地ごとの木が使うブロック。地面のブロックは除く)"""
    global _TREE
    if _TREE is None:
        import re
        d = alib.ROOT / "src" / "main" / "java" / "jp" / "revati" / "skyabyss" / "world" / "continent"
        names = set()
        for f in d.glob("*Trees.java"):
            names |= {m.lower() for m in re.findall(r"Blocks\.([A-Z_]+)", f.read_text(encoding="utf-8"))}
        names -= {"coarse_dirt", "cobblestone", "dirt_path", "grass_block", "mossy_cobblestone", "snow"}
        _TREE = {"minecraft:" + n for n in names}
    return _TREE


def tree_flags(states):
    t = tree_names()
    return np.array([s.split("[")[0] in t or s.split("[")[0].endswith(("_leaves", "_log", "_wood")) for s in states], dtype=bool)


# 岩山の素材の読み替え: 岩鯨の形が使う名前 → まわりの地面の素材 (順番で対応させ、縞はそのまま残す)
_ROCK_ORDER = ("stone", "andesite", "tuff", "calcite", "cobblestone", "mossy_cobblestone", "diorite", "granite", "gravel")
_TOP_ORDER = ("grass_block", "coarse_dirt", "moss_block", "podzol")


def _local_set(v, pal, fallback):
    rock, top, sub = pal.get("rock"), pal.get("top"), pal.get("sub")
    raw = getattr(v, "raw_set", fallback)
    cache = {}

    def f(x, y, z, s):
        if not isinstance(s, str):
            return fallback(x, y, z, s)
        r = cache.get(s)
        if r is None:
            base = s.split("[")[0].replace("minecraft:", "")
            r = False
            if rock and base in _ROCK_ORDER:
                r = rock[_ROCK_ORDER.index(base) % len(rock)]
            elif top and base in _TOP_ORDER:
                r = top[_TOP_ORDER.index(base) % len(top)]
            elif sub and base == "dirt":
                r = sub[0]
            cache[s] = r
        if r:
            return raw(x, y, z, r)
        return fallback(x, y, z, s)
    return f


def _subst(v, table):
    """v.set に渡る素材を土地の素材へ読み替える。返り値: 文字列を読み替える関数"""
    cache = {}

    def conv(s):
        if not isinstance(s, str):
            return s
        r = cache.get(s)
        if r is None:
            head = s.split("[")[0]
            base = head.replace("minecraft:", "")
            to = table.get(base)
            if to is None:
                r = s
            elif to == "air":
                r = "minecraft:air"
            else:
                same = base.rsplit("_", 1)[-1] == to.rsplit("_", 1)[-1]
                r = "minecraft:" + to + (s[len(head):] if same else "")
            cache[s] = r
        return r

    if table:
        orig = v.set
        v.raw_set = orig
        v.set = lambda x, y, z, s: orig(x, y, z, conv(s))
    return conv


def build_tile_relief(v, L, sz, sx):
    """区画 1 枚: 列を上下させて起伏をつけ、池・岩の門・岩山を土地の素材で描く。木と草は列といっしょに動く"""
    ids = v.ids
    SY = v.sy
    X0, Z0 = L["X0"], L["Z0"]
    sl = lambda a: a[sz, sx]  # noqa: E731
    keep, H, g = sl(L["keep"]), sl(L["H"]), sl(L["g"])
    conv = _subst(v, L["cfg"].get("subst", {}))
    air = v.id("minecraft:air")
    # 0. 道に乗った岩 (元の世界の散らし岩) を除く: 道のすぐ上に積もった岩と、頭がつかえる低い張り出し (4 段より下) だけ。
    #    高い所の岩の門・張り出しは残す (道の幅だけ切り欠かない)
    py, rd = sl(L["pathy"]), sl(L["road2d"])
    path_id = v.id("minecraft:dirt_path")
    for (iz, ix) in np.argwhere(rd & sl(L["mine"]) & (py >= 0) & (py < g)):
        y = int(py[iz, ix])
        col = ids[:, iz, ix]
        k = y + 1
        while k <= g[iz, ix] and col[k] != air:
            k += 1
        a = k
        while a <= g[iz, ix] and col[a] == air:
            a += 1
        top = int(g[iz, ix]) if a <= g[iz, ix] and a - k < 4 else k - 1
        ids[y + 1:top + 1, iz, ix] = air
        ids[y, iz, ix] = path_id
    # 1. 列を上下させる (元の地面の 6 下から上を、まるごと d だけずらす)
    Dl = np.where(keep | (g < 0), 0, H - g)
    Y = np.arange(SY)[:, None]
    for d in np.unique(Dl):
        if d == 0:
            continue
        cz, cx = np.nonzero(Dl == d)
        old = ids[:, cz, cx]
        base = np.maximum(g[cz, cx] - 6, 0)[None]
        src = Y - int(d)
        ok = Y >= base + int(d)
        new = np.where(ok, old[np.clip(src, 0, SY - 1), np.arange(len(cz))[None]], old)
        new = np.where(ok & (src >= SY), air, new)
        if d > 0:  # 上げた分の下は元の地下の土で埋める (古い空気を地下に残さない)
            fill = (Y >= base) & (Y < base + int(d))
            new = np.where(fill, old[np.clip(base, 0, SY - 1), np.arange(len(cz))[None]], new)
        ids[:, cz, cx] = new
    _lift_columns(v, sl(L["lift"]), sl(L["R"]))
    # 2. 池
    pw, pb = sl(L["pond_wl"]), sl(L["pond_bot"])
    water = v.id("minecraft:water")
    for (iz, ix) in np.argwhere(pw > -10**5):
        x, z = ix + sx.start + X0, iz + sz.start + Z0
        bot, wl = int(pb[iz, ix]), int(pw[iz, ix])
        ids[bot + 1:, iz, ix] = air
        ids[bot, iz, ix] = v.id(conv(["minecraft:clay", "minecraft:gravel", "minecraft:mud", "minecraft:sand"][int(S._hash2(x, z, 81) * 4)]))
        ids[bot + 1:wl + 1, iz, ix] = water
    # 3. 形
    before = ids.copy()
    x0, z0 = sx.start + X0, sz.start + Z0
    for f in L["feats"]:
        kind, fx, fz, rad, a = f
        if fx + rad < x0 or fx - rad >= x0 + alib.TILE or fz + rad < z0 or fz - rad >= z0 + alib.TILE:
            continue
        rnd = rnd_for(kind, fx, fz)
        keep_set = v.set
        if "pal" in a:
            v.set = _local_set(v, a["pal"], keep_set)
        if kind == "massif":
            B.massif(v, fx, a["base"], fz, rnd, radius=a["radius"], height=a["height"], peaks=a["peaks"], hold=_hold(L))
        elif kind == "waterfall":
            _waterfall(v, fx, fz, a["wl"], rnd)
        elif kind == "boardwalk":
            _boardwalk(v, L, fx, fz, a["wl"])
        elif kind == "arch":
            B.big_arch(v, *a["a"], *a["b"], rise=a["rise"], rnd=rnd, thick=7.0)
        v.set = keep_set
    cf = sl(L["clear_from"])
    lf = sl(L["lift"])
    cf = np.where((lf != 0) & (cf < 10**5), np.maximum(cf, sl(L["R"]) + lf + 1), cf)  # 道を上げ下げした列は、その上から空ける
    for (iz, ix) in np.argwhere(cf < 10**5):
        ids[int(cf[iz, ix]):int(cf[iz, ix]) + 15, iz, ix] = air
    # 4. 形や池にかかった木は、木ごと消す (大きな林のつながりは、かかった所のまわり 7 だけ)
    tf = tree_flags(v.states)
    prot = sl(L["protect"])
    tree0 = tf[before] & ~prot[None]
    drawn = before != ids
    rockw = ("stone", "basalt", "blackstone", "tuff", "terracotta", "deepslate", "andesite", "diorite", "granite", "calcite", "sandstone")
    rock_ = np.array([any(w in s_.split("[")[0] for w in rockw) and not s_.split("[")[0].endswith(("_slab", "_stairs", "_wall", "button"))
                      for s_ in v.states])[ids] & ~tf[ids]
    under = np.zeros_like(drawn)  # 岩や形のすぐ下 3 段の木 (木の頭と岩のあいだに 1〜2 段の隙間を残さない。自己テスト continent overhangs)
    for k in (1, 2, 3):
        under[:-k] |= rock_[k:]
    woody = np.array([s_.split("[")[0].endswith(("_log", "_leaves", "_wood")) for s_ in v.states])[before]  # 花びらなどの地面の草は岩の下でも消さない
    touched = tree0 & (drawn | (under & woody) | (pw > -10**5)[None])
    if touched.any():
        lab, _ = ndimage.label(tree0, structure=np.ones((3, 3, 3)))
        hit = np.unique(lab[touched])
        hit = hit[hit > 0]
        sizes = ndimage.sum(np.ones_like(lab, dtype=np.int32), lab, hit) if len(hit) else []
        small = [h for h, s in zip(hit, sizes) if s <= 3000]
        big = [h for h, s in zip(hit, sizes) if s > 3000]
        now_tree = tf[ids]
        if small:
            ids[np.isin(lab, small) & now_tree] = air
        if big:
            near = ndimage.distance_transform_edt(~touched.any(axis=0)) <= 7
            ids[np.isin(lab, big) & now_tree & near[None]] = air
    # 4b. 岩の張り出しの下に 1〜2 段の空きがあれば埋める (頭がつかえる隙間を作らない。自己テスト continent overhangs)
    # 形を描いた列と、人の作った物のない土地の列。形を描く前に人の作った物がある列 (都の吊り灯・戸の前の階段) は埋めない
    made_p = np.array([any(w in s_.split("[")[0] for w in MADE_WORDS) for s_ in v.states]) & ~tree_flags(v.states)
    feat_cols = ((before != ids).any(axis=0) | (sl(L["mine"]) & ~sl(L["built2d"]))) & ~made_p[before].any(axis=0)
    if feat_cols.any():
        tf2 = tree_flags(v.states)
        soft = np.array([s.endswith(":air") or s.startswith("minecraft:water") for s in v.states])
        for _ in range(2):
            solid = ~soft[ids] & ~tf2[ids]
            hole = ids == v.id("minecraft:air")  # 水は埋めない (池の板の道の下)
            g1 = np.zeros_like(solid)
            g1[1:-1] = hole[1:-1] & solid[2:] & solid[:-2]
            g2 = np.zeros_like(solid)
            g2[1:-2] = hole[1:-2] & hole[2:-1] & solid[3:] & solid[:-3]
            g1 &= feat_cols[None]
            g2 &= feat_cols[None]
            if not (g1.any() or g2.any()):
                break
            above1 = np.zeros_like(ids)
            above1[:-1] = ids[1:]
            above2 = np.zeros_like(ids)
            above2[:-2] = ids[2:]
            g2b = np.zeros_like(g2)
            g2b[1:] = g2[:-1]
            ids[g1] = above1[g1]
            ids[g2] = above2[g2]
            ids[g2b] = above1[g2b]
    # 5. 横に空気がある土の面は土地の石の縞に (動かした列だけ)
    soil = np.isin(ids, [v.id(s) for s in ("minecraft:dirt", "minecraft:grass_block[snowy=false]", "minecraft:coarse_dirt",
                                            "minecraft:podzol[snowy=false]", "minecraft:rooted_dirt")])
    airv = ids == air
    side = np.zeros_like(airv)
    side[:, 1:, :] |= airv[:, :-1, :]
    side[:, :-1, :] |= airv[:, 1:, :]
    side[:, :, 1:] |= airv[:, :, :-1]
    side[:, :, :-1] |= airv[:, :, 1:]
    above_solid = np.zeros_like(airv)
    above_solid[:-1] = ~airv[1:]
    cliff = sl(L["expo"]) >= 2  # 2 段以上の崖だけ (1 段の段差の横は土のまま)
    face = soil & side & above_solid & ((cliff & (Dl != 0)) | (before != ids).any(axis=0))[None]
    for (iy, iz, ix) in np.argwhere(face):
        ids[iy, iz, ix] = v.id(conv(S.strata(ix + x0, iy + v.oy, iz + z0, 3)))


def _hold(L):
    hw, X0, Z0 = L["hold_w"], L["X0"], L["Z0"]
    D, W = hw.shape

    def f(x, z):
        i, j = x - X0, z - Z0
        return float(hw[j, i]) if 0 <= i < W and 0 <= j < D else 0.0
    return f


def _lift_columns(v, lift, R):
    """列の地面 (高さ R) から上を lift だけ上げる (下は 3 つ下の土で埋める)"""
    ids = v.ids
    SY = v.sy
    Y = np.arange(SY)[:, None]
    for d in np.unique(lift):
        if d == 0:
            continue
        cz, cx = np.nonzero(lift == d)
        old = ids[:, cz, cx]
        base = np.maximum(R[cz, cx] - 3, 0)[None]
        src = Y - int(d)
        ok = Y >= base + int(d)
        new = np.where(ok, old[np.clip(src, 0, SY - 1), np.arange(len(cz))[None]], old)
        ids[:, cz, cx] = np.where(ok & (src >= SY), 0 if v.states[0].endswith(":air") else v.id("minecraft:air"), new)


def _town_wall(v, L, sz, sx):
    """町の四角のすぐ外の石垣 (窪みが 2 以上の所。道の口と角は開ける)・胸壁・控え壁"""
    ids = v.ids
    X0, Z0 = L["X0"], L["Z0"]
    cx, cz = L["cx"], L["cz"]
    half, P = L["half"], L["P"]
    sl = lambda a: a[sz, sx]  # noqa: E731
    dT, in_town, keep, H, dist_road = sl(L["dT"]), sl(L["in_town"]), sl(L["keep"]), sl(L["H"]), sl(L["dist_road"])
    edge = sl(L["edge"])
    wall_cols = np.argwhere((dT > 0) & (dT <= 2.0) & ~in_town & ~keep & (edge >= MARGIN) & (P - H >= 2) & (dist_road > 5))
    if not len(wall_cols):
        return
    bricks = ["minecraft:stone_bricks", "minecraft:mossy_stone_bricks", "minecraft:cracked_stone_bricks", "minecraft:andesite",
              "minecraft:stone_bricks", "minecraft:mossy_stone_bricks"]
    x0, z0 = sx.start + X0, sz.start + Z0
    wall_set = {(int(a), int(b)) for a, b in wall_cols}
    for (iz, ix) in wall_cols:
        x, z = ix + x0, iz + z0
        col = ids[:, iz, ix]
        for y in range(int(H[iz, ix]) + 1, P + 1):
            col[y] = v.id(bricks[int(S._hash2(x, z, y) * len(bricks))])
        along = x if abs(z - cz) > half else z
        if along % 9 == 0:
            col[P + 1] = v.id("minecraft:chiseled_stone_bricks")
            col[P + 2] = v.id("minecraft:lantern[hanging=false,waterlogged=false]")
            ox_ = int(np.sign(x - cx)) if abs(x - cx) > half else 0
            oz_ = int(np.sign(z - cz)) if abs(z - cz) > half else 0
            for k in (1, 2):
                jx, jz = ix + ox_ * k, iz + oz_ * k
                if 0 <= jx < alib.TILE and 0 <= jz < alib.TILE and not keep[jz, jx]:
                    topb = P - 1 - (k - 1) * 2
                    for y in range(int(H[jz, jx]) + 1, topb + 1):
                        ids[y, jz, jx] = v.id("minecraft:mossy_stone_bricks" if S._hash2(x + k, z, y) < 0.4 else "minecraft:stone_bricks")
                    ids[topb + 1, jz, jx] = v.id("minecraft:stone_brick_slab[type=bottom,waterlogged=false]")
        else:
            arm = {d: ("low" if (iz + dz, ix + dx) in wall_set else "none")
                   for d, dx, dz in (("east", 1, 0), ("west", -1, 0), ("south", 0, 1), ("north", 0, -1))}
            col[P + 1] = v.id(f"minecraft:stone_brick_wall[east={arm['east']},north={arm['north']},south={arm['south']},"
                              f"up=true,waterlogged=false,west={arm['west']}]")


def _waterfall(v, cx, cz, W, rnd):
    """池の北の岸の切り立った岩と滝"""
    tops = B.massif(v, cx, W - 3, cz, rnd, radius=13.0, height=34.0, peaks=2, steep=True)
    rock_z = [pz for (px, pz) in tops if px == cx]
    fz_front = max(rock_z) + 1 if rock_z else cz + 10
    ftop = max(tops.get((cx, fz_front - k), W) for k in range(1, 5))
    fz_src = max((fz_front - k for k in range(1, 5)), key=lambda zz: tops.get((cx, zz), W))
    v.set(cx, ftop, fz_src, "minecraft:water")
    for zz in range(fz_src + 1, fz_front + 1):
        v.set(cx, ftop, zz, "minecraft:water[level=1]")
    for y in range(W + 1, ftop + 1):
        v.set(cx, y, fz_front, "minecraft:water[level=8]")
        if y < ftop - 2:
            v.set(cx + 1, y, fz_front, "minecraft:water[level=8]")


def _boardwalk(v, L, cx, walk_z, W):
    pw = L["pond_wl"]
    X0, Z0 = L["X0"], L["Z0"]
    xs_row = [x for x in range(cx - 30, cx + 31) if 0 <= walk_z - Z0 < pw.shape[0] and 0 <= x - X0 < pw.shape[1]
              and pw[walk_z - Z0, x - X0] > -10**5]
    if not xs_row:
        return
    for x in range(min(xs_row) - 2, max(xs_row) + 3):
        for dz in (0, 1):
            wet = 0 <= walk_z + dz - Z0 < pw.shape[0] and pw[walk_z + dz - Z0, x - X0] > -10**5
            v.set(x, W + 1, walk_z + dz, "minecraft:spruce_slab[type=top,waterlogged=false]" if wet else "minecraft:spruce_planks")
        if (x - min(xs_row)) % 5 == 0:
            for dz in (-1, 2):
                v.set(x, W + 1, walk_z + dz, "minecraft:spruce_log[axis=y]")
                v.set(x, W + 2, walk_z + dz, "minecraft:spruce_fence[east=false,north=false,south=false,waterlogged=false,west=false]")
                if (x - min(xs_row)) % 10 == 0:
                    v.set(x, W + 3, walk_z + dz, "minecraft:lantern[hanging=false,waterlogged=false]")


if __name__ == "__main__":
    main()
