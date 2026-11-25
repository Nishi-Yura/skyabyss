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

LANDS = {"whale": {"region": 2, "town": (-120, -760), "half": 71, "lairs": [(230, -1000)],
                   "ponds": [(25, -640)], "n_big": 14, "n_small": 22, "n_ponds": 6, "n_arches": 5}}
MARGIN = 8
T = alib.TILE


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
        nat = v.natural_flags()
        plant_words = ("grass", "fern", "flower", "dandelion", "poppy", "bluet", "daisy", "cornflower", "allium", "orchid", "valley",
                       "berry", "bush", "petals", "snow", "vine", "lichen", "mushroom", "_log", "leaves", "seagrass", "kelp", "roots")
        is_air = np.array([s.endswith(":air") for s in st])
        is_water = np.array([s.startswith("minecraft:water") for s in st])
        is_veg = np.array([any(w in s for w in plant_words) and not s.startswith(("minecraft:grass_block", "minecraft:rooted_dirt",
                                                                                   "minecraft:mangrove_roots")) for s in st])
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
    dist_site = ndimage.distance_transform_edt(~site_keep)
    keep = ~mine | in_town | (dist_built <= 3) | (dist_water <= 3) | site_keep
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
    terr = np.where(ridge > 0.55, np.floor(hills / 3) * 3, hills)
    delta = terr * w_edge * w_town * w_built * w_water * w_road
    bump = smooth(dT / 3) * (1 - smooth((dT - 10) / 26))
    ramp = smooth(dT / 24) * (1 - smooth((dT - 26) / 14))
    roadness = 1 - smooth((dist_road - 4) / 10)
    low = -5.0 * (bump * (1 - roadness) + ramp * roadness) * w_edge
    Hf = g + ndimage.gaussian_filter(delta + low, 2.0)
    Hroad = ndimage.gaussian_filter(Hf, 7)
    core = np.clip(1 - (dist_road - 3.5) / 8, 0, 1)
    Hf = Hf * (1 - core) + Hroad * core
    H = np.floor(Hf + 0.5).astype(np.int32)
    road_base = np.floor(Hroad).astype(np.int32)
    road_half = (Hroad - road_base) >= 0.5
    H = np.where(keep, g, H)
    H = np.clip(H, 2, 250 - OY - 70)
    road_base = np.where(keep, g, road_base)
    dr = dist_road
    del w_edge, w_town, w_water, w_road, hills, terr, delta, bump, ramp, roadness, low, Hf, Hroad, core

    # ------------------------------------------------------------ 3. 池 (土地に数か所。町の南東の池を含む)
    feats = []  # (種類, x, z, 半径, 引数)
    pond_wl = np.full((D, W), -10**6, dtype=np.int32)
    pond_bot = np.full((D, W), -10**6, dtype=np.int32)
    extra_zone = np.zeros((D, W), dtype=bool)
    ok = ~keep & (dist_road > 22) & (dT > 55) & (edge > 50) & (dist_site > 24) & (dist_built > 24)
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
        feats.append(("waterfall", cl_x, cl_z, 40, {"wl": wl + OY, "pond": pi}))
        feats.append(("boardwalk", px0 + X0, pz0 + Z0 + 2, 34, {"wl": wl + OY, "pond": pi}))
        cz_ = slice(max(0, cl_z - Z0 - 26), cl_z - Z0 + 27)
        cx_ = slice(max(0, cl_x - X0 - 30), cl_x - X0 + 31)
        extra_zone[cz_, cx_] = True
    print(f"ponds {len(targets)}")

    # ------------------------------------------------------------ 4. 道をまたぐ岩の門 (町と建った場所から離れた道の上)
    arch_cand = np.argwhere(road_out & ~keep & (dT > 70) & (edge > 30) & (dist_site > 35) & (dist_built > 25))
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
    spires = []
    for big, n_want, need in ((True, cfg["n_big"], 30), (False, cfg["n_small"], 16)):
        cand = np.argwhere(free & (dist_keep > need) & (dist_road > need * 0.8))
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

    # ------------------------------------------------------------ 6. 木 (4 種 + 灯り葉の樹の林)
    nmin = ndimage.minimum_filter(H, footprint=np.array([[0, 1, 0], [1, 1, 1], [0, 1, 0]]), mode="nearest")
    expo = H - nmin
    forest = S.fbm(xs, zs, 90, 31)
    grove = S.fbm(xs, zs, 55, 33)
    birchy = S.fbm(xs, zs, 50, 32)
    occupied = np.zeros((D, W), dtype=bool)
    plantable = ~keep & ~rock_zone & ~extra_zone & (dist_road > 4) & (dT > 7) & (expo < 2) & (edge > MARGIN + 6) & (dist_built > 9)
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
        build_tile(v, locals(), sz, sx)
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
            B.massif(v, fx, a["base"], fz, rnd, radius=a["radius"], height=a["height"], peaks=a["peaks"])
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
