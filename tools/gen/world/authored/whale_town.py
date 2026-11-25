# -*- coding: utf-8 -*-
"""鯨見の町のまわりを前もって組む (試作。2026-11-25)。

元: 今の世界の書き出し run/shots/whale_authored/dumps/whale_src.sbd (tools/shots/whale_authored_dump.txt)
先: data/skyabyss/continent/authored/whale_town.sbd (ContinentAuthored が読む)

    python -I tools/gen/world/authored/whale_town.py [元の .sbd]

組むもの:
- 町は石垣の台の上 (町の四角はそのまま)。外は 5 ほど下がった草の窪みで、街道は石を敷いた坂で門へ上がる
- その外の丘: 尾根と、崖の縞の岩、岩の塔 (遠くからの目印)。雪は高い所だけ
- 草木は描き直す: 唐檜の大木の林・唐檜・樺の群れ・横に広い楢・茂み・岩、下草と花の群れ
- 道: 真ん中は踏み固めた道、縁は荒れた土・砂利・苔のまだら。脇に灯りの柱と縁石
- 範囲の縁 8 と、見どころ・水のまわりは元のまま (外の地形と継ぎ目なくつながる)
"""
from __future__ import annotations

import math
import random
import sys
from pathlib import Path

import numpy as np
from scipy import ndimage

sys.path.insert(0, str(Path(__file__).resolve().parent))
import alib  # noqa: E402
import shapes as S  # noqa: E402
import shapes_big as B  # noqa: E402

SRC = alib.ROOT / "run" / "shots" / "whale_authored" / "dumps" / "whale_src.sbd"
TOWN = (-120, -760)
TOWN_HALF = 71
MARGIN = 8
SEED = 1125


def smooth(t):
    t = np.clip(t, 0.0, 1.0)
    return t * t * (3 - 2 * t)


def main():
    src = Path(sys.argv[1]) if len(sys.argv) > 1 else SRC
    v = alib.Vol(alib.sbd.load(src))
    SY, SZ, SX = v.ids.shape
    rnd = random.Random(SEED)
    st = v.states

    # ------------------------------------------------------------ 列を調べる
    def flags(pred):
        return np.array([bool(pred(s)) for s in st], dtype=bool)

    nat = v.natural_flags()
    plant_words = ("grass", "fern", "flower", "dandelion", "poppy", "bluet", "daisy", "cornflower", "allium", "orchid", "valley",
                   "berry", "bush", "petals", "snow", "vine", "lichen", "mushroom", "_log", "leaves", "seagrass", "kelp", "roots")
    is_air = flags(lambda s: s.endswith(":air") or s.endswith("cave_air"))
    is_water = flags(lambda s: s.startswith("minecraft:water"))
    is_veg = flags(lambda s: any(w in s for w in plant_words) and not s.startswith("minecraft:grass_block")
                   and not s.startswith("minecraft:rooted_dirt") and not s.startswith("minecraft:mangrove_roots"))
    is_ground = nat & ~is_air & ~is_water & ~is_veg
    is_built = ~nat

    gnd = is_ground[v.ids]
    # 列の地面の高さ (局所の y)。無ければ -1
    rev = gnd[::-1]
    has = rev.any(axis=0)
    g = np.where(has, SY - 1 - rev.argmax(axis=0), -1).astype(np.int32)
    # 人の作った物: 地面の 3 下より上にある自然でないブロック (地下の鉱石や晶洞は数えない)
    yy = np.arange(SY)[:, None, None]
    built2d = (is_built[v.ids] & (yy >= (g - 3)[None, :, :])).any(axis=0)
    water2d = is_water[v.ids].any(axis=0)
    top_state = np.array(st)[v.ids[np.clip(g, 0, SY - 1), np.arange(SZ)[:, None], np.arange(SX)[None, :]]]
    road2d = (top_state == "minecraft:dirt_path")
    road2d = ndimage.binary_closing(road2d, iterations=2)

    xs, zs = np.meshgrid(np.arange(SX) + v.ox, np.arange(SZ) + v.oz)
    cx, cz = TOWN
    # 町の四角からの距離 (外 = 正)
    ddx = np.maximum(np.abs(xs - cx) - TOWN_HALF, 0)
    ddz = np.maximum(np.abs(zs - cz) - TOWN_HALF, 0)
    dT = np.hypot(ddx, ddz)
    in_town = (np.abs(xs - cx) <= TOWN_HALF) & (np.abs(zs - cz) <= TOWN_HALF)
    P = int(np.median(g[in_town & ~built2d]))
    print(f"area {SX}x{SY}x{SZ} at {v.ox},{v.oy},{v.oz}; town ground {P + v.oy}; road cols {int(road2d.sum())}")

    edge = np.minimum.reduce([xs - v.ox, v.ox + SX - 1 - xs, zs - v.oz, v.oz + SZ - 1 - zs]).astype(float)
    built_out = built2d & ~in_town
    dist_built = ndimage.distance_transform_edt(~built_out) if built_out.any() else np.full(g.shape, 999.0)
    dist_water = ndimage.distance_transform_edt(~water2d) if water2d.any() else np.full(g.shape, 999.0)
    road_out = road2d & ~in_town
    dist_road = ndimage.distance_transform_edt(~road_out) if road_out.any() else np.full(g.shape, 999.0)

    # 書き出した世界に建っていた場所 (町は別) は、整地の輪まで元の地面のまま (あとで同じ高さに仕上げ・建て直しができるように)
    site_keep = np.zeros(g.shape, dtype=bool)
    for e in v.header.get("sites", {}).get("entries", []):
        if e["kind"] == "town":
            continue
        r = 72 if e["kind"] == "ribs" else (12 if e["kind"].startswith("ch:") else 22)
        site_keep |= (xs - e["pos"][0]) ** 2 + (zs - e["pos"][2]) ** 2 < r * r
    keep = (edge < MARGIN) | in_town | (dist_built <= 3) | (dist_water <= 3) | site_keep

    # ------------------------------------------------------------ 高さ
    w_edge = smooth((edge - MARGIN) / 48)
    w_town = smooth((dT - 6) / 34)
    dist_site = ndimage.distance_transform_edt(~site_keep) if site_keep.any() else np.full(g.shape, 999.0)
    w_built = smooth((dist_built - 3) / 16) * smooth(dist_site / 14)
    w_water = smooth((dist_water - 3) / 12)
    w_road = smooth((dist_road - 6) / 16)
    ridge = S.ridged(xs, zs, 150, 11) ** 1.7
    hills = 22 * ridge + 7 * S.fbm(xs, zs, 70, 12) - 6
    # 岩の段: 尾根の高い所は 3 ずつの段になって崖の面が出る
    terr = np.where(ridge > 0.55, np.floor(hills / 3) * 3, hills)
    delta = terr * w_edge * w_town * w_built * w_water * w_road
    # 町のまわりの窪み (石垣の下)。道のそばは門へ向かう坂
    bump = smooth(dT / 3) * (1 - smooth((dT - 10) / 26))
    ramp = smooth(dT / 24) * (1 - smooth((dT - 26) / 14))
    roadness = 1 - smooth((dist_road - 4) / 10)
    low = -5.0 * (bump * (1 - roadness) + ramp * roadness) * w_edge
    Hf = g + ndimage.gaussian_filter(delta + low, 2.0)
    # 道の床: 太くぼかした高さ。道と縁 3 はこの高さそのもの、外へなだらかに戻す
    Hroad = ndimage.gaussian_filter(Hf, 7)
    core = np.clip(1 - (dist_road - 3.5) / 8, 0, 1)
    Hf = Hf * (1 - core) + Hroad * core
    H = np.floor(Hf + 0.5).astype(np.int32)
    road_base = np.floor(Hroad).astype(np.int32)
    road_half = (Hroad - road_base) >= 0.5
    H = np.where(keep, g, H)
    H = np.clip(H, 2, SY - 60)
    road_base = np.where(keep, g, road_base)

    # 露わな面: 隣のいちばん低い所との差。2 以上の横の面は石の縞 (土の壁を見せない)
    nmin = ndimage.minimum_filter(H, footprint=np.array([[0, 1, 0], [1, 1, 1], [0, 1, 0]]), mode="nearest")
    expo = H - nmin
    snow_line = P + 22

    # ------------------------------------------------------------ 地面を積み直す
    air = v.id("minecraft:air")
    ids = v.ids
    grass = v.id("minecraft:grass_block[snowy=false]")
    grass_snow = v.id("minecraft:grass_block[snowy=true]")
    dirt = v.id("minecraft:dirt")
    stone = v.id("minecraft:stone")
    podzol = v.id("minecraft:podzol[snowy=false]")
    moss = v.id("minecraft:moss_block")
    coarse = v.id("minecraft:coarse_dirt")
    snow_block = v.id("minecraft:snow_block")
    patch = S.fbm(xs, zs, 24, 21)
    patch2 = S.fbm(xs, zs, 40, 22)
    work = np.argwhere(~keep)
    for (iz, ix) in work:
        h = int(H[iz, ix])
        col = ids[:, iz, ix]
        col[h + 1:] = air
        col[:max(0, h - 4)] = stone
        x, z = ix + v.ox, iz + v.oz
        e = int(expo[iz, ix])
        for y in range(max(0, h - 4), h):
            col[y] = dirt
        # 横に見える所は石の縞 (いちばん上の 1 だけ土と草を残す)
        if e >= 2:
            for y in range(max(0, int(nmin[iz, ix]) - 1), h):
                col[y] = v.id(S.strata(x, y + v.oy, z, 3))
        r = S._hash2(x, z, 61)
        if h > snow_line + int(4 * patch[iz, ix]):
            col[h] = snow_block if e >= 2 else grass_snow
            if e < 2 and h + 1 < SY:
                col[h + 1] = v.id("minecraft:snow[layers=1]")
        elif e >= 4 and r < 0.55:
            col[h] = v.id(S.strata(x, h + v.oy, z, 3))
        elif e >= 2 and r < 0.15:
            col[h] = coarse if patch[iz, ix] > 0.5 else moss
        elif patch[iz, ix] > 0.82:
            col[h] = podzol
        elif patch2[iz, ix] > 0.78:
            col[h] = moss
        else:
            col[h] = grass

    # 1) 残しの列 (縁・見どころ・水のまわり) の元の木: 幹を切られて浮いた葉と、半分だけの木を消す (見どころのすぐそばと町は残す)
    veg_ids = np.nonzero(np.array([("_log" in s or "leaves" in s) and s.startswith(("minecraft:spruce", "minecraft:oak", "minecraft:birch",
                                                                                   "minecraft:dark_oak"))
                                   for s in v.states]))[0]
    strip = keep & ~in_town & (dist_built > 1)
    is_veg_vol = np.isin(ids, veg_ids)
    sel = is_veg_vol & strip[None, :, :]
    ids[sel] = air

    # ------------------------------------------------------------ 石垣 (町の四角のすぐ外。窪みが 2 以上の所)
    bricks = ["minecraft:stone_bricks", "minecraft:mossy_stone_bricks", "minecraft:cracked_stone_bricks", "minecraft:andesite",
              "minecraft:stone_bricks", "minecraft:mossy_stone_bricks"]
    wall_cols = np.argwhere((dT > 0) & (dT <= 2.0) & ~in_town & ~keep & (edge >= MARGIN) & (P - H >= 2) & (dist_road > 5))
    for (iz, ix) in wall_cols:
        x, z = ix + v.ox, iz + v.oz
        col = ids[:, iz, ix]
        for y in range(int(H[iz, ix]) + 1, P + 1):
            col[y] = v.id(bricks[int(S._hash2(x, z, y) * len(bricks))])
        # 胸壁 (道の口と角は開ける)
        along = x if abs(z - cz) > TOWN_HALF else z
        if along % 9 == 0:
            col[P + 1] = v.id("minecraft:chiseled_stone_bricks")
            col[P + 2] = v.id("minecraft:lantern[hanging=false,waterlogged=false]")
        else:
            col[P + 1] = v.id("minecraft:stone_brick_wall[east=none,north=none,south=none,up=true,waterlogged=false,west=none]")
    # 胸壁の腕: となりが胸壁 (か柱) の向きだけつなぐ
    wall_set = {(int(iz), int(ix)) for iz, ix in wall_cols}
    for (iz, ix) in wall_cols:
        cur = v.states[int(ids[P + 1, iz, ix])]
        if not cur.startswith("minecraft:stone_brick_wall"):
            continue
        arm = {d: ("low" if (iz + dz, ix + dx) in wall_set else "none")
               for d, dx, dz in (("east", 1, 0), ("west", -1, 0), ("south", 0, 1), ("north", 0, -1))}
        ids[P + 1, iz, ix] = v.id(f"minecraft:stone_brick_wall[east={arm['east']},north={arm['north']},south={arm['south']},"
                                  f"up=true,waterlogged=false,west={arm['west']}]")
    # 控え壁 (9 ごとに一つ外へ。下は太く)
    for (iz, ix) in wall_cols:
        x, z = ix + v.ox, iz + v.oz
        along = x if abs(z - cz) > TOWN_HALF else z
        if along % 9 != 0:
            continue
        ox_ = int(np.sign(x - cx)) if abs(x - cx) > TOWN_HALF else 0
        oz_ = int(np.sign(z - cz)) if abs(z - cz) > TOWN_HALF else 0
        for k in (1, 2):
            jx, jz = ix + ox_ * k, iz + oz_ * k
            if 0 <= jx < SX and 0 <= jz < SZ and not keep[jz, jx]:
                topb = P - 1 - (k - 1) * 2
                for y in range(int(H[jz, jx]) + 1, topb + 1):
                    ids[y, jz, jx] = v.id("minecraft:mossy_stone_bricks" if S._hash2(x + k, z, y) < 0.4 else "minecraft:stone_bricks")
                ids[topb + 1, jz, jx] = v.id("minecraft:stone_brick_slab[type=bottom,waterlogged=false]")

    # ------------------------------------------------------------ 道 (町の外)
    road_cols = np.argwhere(road_out & ~keep)
    dr = ndimage.distance_transform_edt(~road_out)
    near_road = np.argwhere((dr > 0) & (dr <= 2) & ~keep & ~in_town)
    paved = dT < 36
    pave = ["minecraft:cobblestone", "minecraft:mossy_cobblestone", "minecraft:stone_bricks", "minecraft:andesite",
            "minecraft:cobblestone", "minecraft:mossy_stone_bricks", "minecraft:gravel"]
    pave_slab = {"minecraft:cobblestone": "cobblestone_slab", "minecraft:mossy_cobblestone": "mossy_cobblestone_slab",
                 "minecraft:stone_bricks": "stone_brick_slab", "minecraft:andesite": "andesite_slab",
                 "minecraft:mossy_stone_bricks": "mossy_stone_brick_slab", "minecraft:gravel": "cobblestone_slab"}
    for (iz, ix) in near_road:
        h = int(H[iz, ix])
        x, z = ix + v.ox, iz + v.oz
        r = S._hash2(x, z, 41)
        if paved[iz, ix] and dr[iz, ix] <= 1.0:
            ids[h, iz, ix] = v.id("minecraft:stone_bricks" if r < 0.6 else "minecraft:mossy_stone_bricks")
            continue
        # 道の縁は草がほとんど、ところどころ砂利と荒れた土 (土色のまだらを減らす)
        ids[h, iz, ix] = v.id("minecraft:grass_block[snowy=false]" if r < 0.7 else ("minecraft:gravel" if r < 0.82 else
                              ("minecraft:coarse_dirt" if r < 0.92 else "minecraft:moss_block")))
    for (iz, ix) in road_cols:
        h = int(road_base[iz, ix])
        x, z = ix + v.ox, iz + v.oz
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
    # 灯りの柱 (道の縁の外 2、22 ごと。左右を替える)
    lamp_spots = np.argwhere((np.abs(dr - 3.0) < 0.5) & ~keep & ~in_town & (dT > 4))
    taken = []
    for (iz, ix) in lamp_spots[np.argsort(S._hash2(lamp_spots[:, 1], lamp_spots[:, 0], 9))]:
        gap = 11 if paved[iz, ix] else 24
        if any((iz - a) ** 2 + (ix - b) ** 2 < gap * gap for a, b in taken):
            continue
        taken.append((iz, ix))
        h = int(H[iz, ix])
        if ids[h + 1, iz, ix] != air:
            continue
        for k in range(1, 4):
            ids[h + k, iz, ix] = v.id("minecraft:spruce_fence[east=false,north=false,south=false,waterlogged=false,west=false]")
        ids[h + 4, iz, ix] = v.id("minecraft:lantern[hanging=false,waterlogged=false]")
        ids[h, iz, ix] = v.id("minecraft:mossy_cobblestone")

    # ------------------------------------------------------------ 池と滝と板の道 (南東の草地)
    extra_zone = np.zeros(g.shape, dtype=bool)
    ok = ~keep & (dist_road > 22) & (dT > 55) & (edge > 50) & (dist_site > 24) & (dist_built > 24)
    cand = np.argwhere(ok)
    target = (-640 - v.oz, 25 - v.ox)
    k = int(np.argmin((cand[:, 0] - target[0]) ** 2 + (cand[:, 1] - target[1]) ** 2))
    pz0, px0 = cand[k]
    ang = np.arctan2(zs - (pz0 + v.oz), xs - (px0 + v.ox))
    rr = np.hypot(zs - (pz0 + v.oz), xs - (px0 + v.ox))
    shape_r = 13 + 3.5 * np.sin(ang * 2 + 0.7) + 2.0 * np.sin(ang * 5 + 1.9)
    pond = (rr < shape_r) & ok
    rim = (rr < shape_r + 3) & ~pond & ~keep
    wl = int(np.min(H[rim])) - 1 if rim.any() else int(H[pz0, px0]) - 1
    water = v.id("minecraft:water")
    depth = np.clip((shape_r - rr) / 3.0, 1, 3).astype(int)
    for (iz, ix) in np.argwhere(pond):
        x, z = ix + v.ox, iz + v.oz
        bot = wl - int(depth[iz, ix])
        ids[bot + 1:, iz, ix] = air
        ids[bot, iz, ix] = v.id(["minecraft:clay", "minecraft:gravel", "minecraft:mud", "minecraft:sand"][int(S._hash2(x, z, 81) * 4)])
        ids[bot + 1:wl + 1, iz, ix] = water
        H[iz, ix] = bot
        if S._hash2(x, z, 82) < 0.06 and depth[iz, ix] >= 1:
            ids[wl + 1, iz, ix] = v.id("minecraft:lily_pad")
    # 岸: 水の高さまで下げ、泥と苔と砂利。葦 (さとうきび) と背の高い草
    for (iz, ix) in np.argwhere(rim):
        x, z = ix + v.ox, iz + v.oz
        h = int(H[iz, ix])
        if h > wl + 2:
            ids[wl + 1:h + 1, iz, ix] = air if rr[iz, ix] < shape_r[iz, ix] + 1.5 else ids[wl + 1:h + 1, iz, ix]
            if rr[iz, ix] < shape_r[iz, ix] + 1.5:
                H[iz, ix] = wl
                h = wl
        top = ["minecraft:mud", "minecraft:moss_block", "minecraft:grass_block[snowy=false]", "minecraft:gravel"][int(S._hash2(x, z, 83) * 4)]
        ids[h, iz, ix] = v.id(top)
        r = S._hash2(x, z, 84)
        if h <= wl + 1 and r < 0.25 and top != "minecraft:gravel":
            if top == "minecraft:mud" or top == "minecraft:grass_block[snowy=false]":
                for q in range(1, 2 + int(r * 8) % 2 + 1):
                    ids[h + q, iz, ix] = v.id("minecraft:sugar_cane[age=0]")
        elif r < 0.45:
            ids[h + 1, iz, ix] = v.id("minecraft:tall_grass[half=lower]")
            ids[h + 2, iz, ix] = v.id("minecraft:tall_grass[half=upper]")
    extra_zone |= rr < shape_r + 6
    # 北の岸に、張り出した岩と滝
    cliff_z = pz0 + v.oz - int(np.max(shape_r[pond]) if pond.any() else 14) - 3
    cliff_x = px0 + v.ox
    W = wl + v.oy  # 水面の世界の y
    cliff_z -= 8
    cl_tops = B.massif(v, cliff_x, W - 3, cliff_z, rnd, radius=13.0, height=34.0, peaks=2, steep=True)
    # 滝: 岩の背の真ん中の上から池へ (水の柱。上は湧き水)
    fx = cliff_x
    rock_z = [pz for (px, pz) in cl_tops if px == fx]
    fz_front = max(rock_z) + 1 if rock_z else cliff_z + 10
    # 面から少し入った所の高い列を湧き口に
    ftop = max(cl_tops.get((fx, fz_front - k), W) for k in range(1, 5))
    fz_src = max((fz_front - k for k in range(1, 5)), key=lambda zz: cl_tops.get((fx, zz), W))
    v.set(fx, ftop, fz_src, "minecraft:water")
    for zz in range(fz_src + 1, fz_front + 1):
        v.set(fx, ftop, zz, "minecraft:water[level=1]")
    for y in range(W + 1, ftop + 1):
        v.set(fx, y, fz_front, "minecraft:water[level=8]")
        if y < ftop - 2:
            v.set(fx + 1, y, fz_front, "minecraft:water[level=8]")
    extra_zone |= (np.abs(xs - cliff_x) < 30) & (np.abs(zs - cliff_z) < 26)
    # 板の道: 西の岸から東の岸へ、水の上 1
    walk_z = pz0 + v.oz + 2
    xs_row = [x for x in range(cliff_x - 30, cliff_x + 31) if 0 <= x - v.ox < SX and pond[walk_z - v.oz, x - v.ox]]
    if xs_row:
        for x in range(min(xs_row) - 2, max(xs_row) + 3):
            for dz in (0, 1):
                v.set(x, W + 1, walk_z + dz, "minecraft:spruce_slab[type=top,waterlogged=false]" if pond[walk_z + dz - v.oz, x - v.ox]
                      else "minecraft:spruce_planks")
            if (x - min(xs_row)) % 5 == 0:
                for dz in (-1, 2):
                    v.set(x, W + 1, walk_z + dz, "minecraft:spruce_log[axis=y]")
                    v.set(x, W + 2, walk_z + dz, "minecraft:spruce_fence[east=false,north=false,south=false,waterlogged=false,west=false]")
                    if (x - min(xs_row)) % 10 == 0:
                        v.set(x, W + 3, walk_z + dz, "minecraft:lantern[hanging=false,waterlogged=false]")
    print(f"pond at {px0 + v.ox},{pz0 + v.oz} water y {wl + v.oy}, cliff {cliff_x},{cliff_z}")

    # ------------------------------------------------------------ 道をまたぐ岩の門 (北東の街道、町から 70〜95)
    arch_cand = np.argwhere(road_out & ~keep & (dT > 70) & (dT < 95) & (edge > 40))
    if len(arch_cand):
        iz, ix = arch_cand[len(arch_cand) // 2]
        x, z = ix + v.ox, iz + v.oz
        near = np.argwhere(road_out[max(0, iz - 8):iz + 9, max(0, ix - 8):ix + 9])
        cov = np.cov(near.T.astype(float))
        w_, vec = np.linalg.eigh(cov)
        dz_, dx_ = vec[:, -1]  # 道の向き
        nx_, nz_ = -dz_, dx_   # 道に直角
        span = 20
        ax, az = int(round(x + nx_ * span)), int(round(z + nz_ * span))
        bx, bz = int(round(x - nx_ * span)), int(round(z - nz_ * span))
        ay = int(H[az - v.oz, ax - v.ox]) + v.oy
        by = int(H[bz - v.oz, bx - v.ox]) + v.oy
        ry = int(road_base[iz, ix]) + v.oy
        B.big_arch(v, ax, ay, az, bx, by, bz, rise=(ry + 22) - (ay + by) / 2, rnd=rnd, thick=7.0)
        extra_zone |= (xs - x) ** 2 + (zs - z) ** 2 < 30 * 30
        # 道の上 (門の下) は空ける
        for (jz, jx) in np.argwhere(((xs - x) ** 2 + (zs - z) ** 2 < 28 * 28) & (dr <= 3.5)):
            hb = int(road_base[jz, jx]) if road_out[jz, jx] else int(H[jz, jx])
            ids[hb + 1 + (1 if road_out[jz, jx] and road_half[jz, jx] else 0):hb + 15, jz, jx] = air
        print(f"arch over the road at {x},{z} (feet {ax},{az} / {bx},{bz})")

    # ------------------------------------------------------------ 岩山 (町から見える目印。大 3・小 4)
    dist_keep = ndimage.distance_transform_edt(~(keep | extra_zone))
    free = ~keep & ~extra_zone & (dist_road > 14) & (dT > 45) & (edge > 30)
    spires = []
    for big, n_want, need in ((True, 3, 30), (False, 4, 16)):
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
    for (iz, ix, big) in spires:
        x, z = ix + v.ox, iz + v.oz
        base = int(H[iz, ix]) + v.oy - 3
        if big:
            B.massif(v, x, base, z, rnd, radius=rnd.uniform(26, 32), height=rnd.uniform(50, 62), peaks=3)
        else:
            B.massif(v, x, base, z, rnd, radius=rnd.uniform(11, 15), height=rnd.uniform(22, 30), peaks=2)
        for q in range(rnd.randint(4, 7)):
            a2 = rnd.uniform(0, math.tau)
            d2 = rnd.uniform(30, 40) if big else rnd.uniform(15, 20)
            qx, qz = int(x + math.cos(a2) * d2), int(z + math.sin(a2) * d2)
            kx, kz = qx - v.ox, qz - v.oz
            if 0 <= kx < SX and 0 <= kz < SZ and not keep[kz, kx]:
                S.boulder(v, qx, int(H[kz, kx]) + 1 + v.oy, qz, rnd)
    print(f"spires {len(spires)}: " + " ".join(f"{'B' if bb else 's'}{ix + v.ox},{iz + v.oz}" for iz, ix, bb in spires))
    rock_zone = np.zeros(g.shape, dtype=bool)
    for (iz, ix, big) in spires:
        r = 40 if big else 20
        rock_zone |= (xs - (ix + v.ox)) ** 2 + (zs - (iz + v.oz)) ** 2 < r * r

    # ------------------------------------------------------------ mod の木のラフ (A 鯨骨・B 苔衣・C 灯り葉) を南の草地に並べる
    show = []
    okt = ~keep & ~extra_zone & ~rock_zone & (dist_road > 12) & (expo < 2) & (dT > 30) & (edge > 30)
    cand = np.argwhere(okt)
    for kname, (tx, tz) in zip("abc", ((-80, -650), (-45, -655), (-10, -650))):
        k = int(np.argmin((cand[:, 1] + v.ox - tx) ** 2 + (cand[:, 0] + v.oz - tz) ** 2))
        iz, ix = cand[k]
        x, z = ix + v.ox, iz + v.oz
        # まわりの低い物を空ける
        for (jz, jx) in np.argwhere((xs - x) ** 2 + (zs - z) ** 2 < 14 * 14):
            hh = int(H[jz, jx])
            ids[hh + 1:hh + 40, jz, jx] = air
        B.TREES[kname](v, x, int(H[iz, ix]) + 1 + v.oy, z, rnd)
        extra_zone |= (xs - x) ** 2 + (zs - z) ** 2 < 18 * 18
        show.append((kname, x, int(H[iz, ix]) + v.oy, z))
    print("tree roughs " + " ".join(f"{k}:{x},{y},{z}" for k, x, y, z in show))

    # ------------------------------------------------------------ 草木
    forest = S.fbm(xs, zs, 90, 31)
    grove = S.fbm(xs, zs, 55, 33)  # 灯り葉の樹の林 (C)
    birchy = S.fbm(xs, zs, 50, 32)
    occupied = np.zeros(g.shape, dtype=bool)
    plantable = ~keep & ~rock_zone & ~extra_zone & (dist_road > 4) & (dT > 7) & (expo < 2) & (edge > MARGIN + 6) & (dist_built > 9)
    pts = np.argwhere(plantable)
    order = np.argsort(S._hash2(pts[:, 1] + v.ox, pts[:, 0] + v.oz, 77))
    n_trees = 0
    for k in order:
        iz, ix = pts[k]
        f = forest[iz, ix]
        dens = 0.0
        if f > 0.55:
            dens = 0.3 + (f - 0.55) * 1.5
        elif f > 0.45:
            dens = 0.05
        else:
            dens = 0.006
        if S._hash2(ix, iz, 5) > dens:
            continue
        r = 5 if f > 0.6 else (6 if f > 0.45 else 8)
        y0, y1, x0, x1 = max(0, iz - r), iz + r + 1, max(0, ix - r), ix + r + 1
        if occupied[y0:y1, x0:x1].any():
            continue
        x, z = ix + v.ox, iz + v.oz
        y = int(H[iz, ix]) + 1 + v.oy
        roll = rnd.random()
        if grove[iz, ix] > 0.66 and f > 0.45 and dT[iz, ix] > 25 and dist_built[iz, ix] > 14 and edge[iz, ix] > 20 and roll < 0.55:
            B.teal_lantern_tree(v, x, y, z, rnd)
            occupied[max(0, iz - 11):iz + 12, max(0, ix - 11):ix + 12] = True
        elif f > 0.58 and roll < 0.38 and dT[iz, ix] > 30 and dist_built[iz, ix] > 14 and edge[iz, ix] > 20:
            B.weeping_fir(v, x, y, z, rnd, big=True)
            occupied[max(0, iz - 9):iz + 10, max(0, ix - 9):ix + 10] = True
        elif birchy[iz, ix] > 0.62 and f < 0.6:
            B.pale_slender(v, x, y, z, rnd)
            occupied[y0:y1, x0:x1] = True
        elif f < 0.45 and roll < 0.5:
            B.moss_low_tree(v, x, y, z, rnd)
            occupied[max(0, iz - 7):iz + 8, max(0, ix - 7):ix + 8] = True
        else:
            B.weeping_fir(v, x, y, z, rnd)
            occupied[y0:y1, x0:x1] = True
        n_trees += 1
    print(f"trees {n_trees}")
    # 岩と茂み
    for k in order[::37]:
        iz, ix = pts[k]
        if occupied[iz, ix] or S._hash2(ix, iz, 6) > 0.35:
            continue
        x, z = ix + v.ox, iz + v.oz
        y = int(H[iz, ix]) + 1 + v.oy
        if forest[iz, ix] > 0.5:
            S.bush(v, x, y, z, rnd, "spruce")
        else:
            S.boulder(v, x, y, z, rnd)
        occupied[max(0, iz - 3):iz + 4, max(0, ix - 3):ix + 4] = True

    # 下草と花の群れ (草の上の空いた所だけ)
    flower_patch = S.fbm(xs, zs, 18, 41)
    which = S.fbm(xs, zs, 33, 42)
    for (iz, ix) in work:
        h = int(H[iz, ix])
        if h + 2 >= SY or ids[h + 1, iz, ix] != air or ids[h, iz, ix] not in (grass, podzol, moss):
            continue
        x, z = ix + v.ox, iz + v.oz
        r = S._hash2(x, z, 51)
        f = forest[iz, ix]
        if flower_patch[iz, ix] > 0.64 and f < 0.55 and r < 0.45:
            ids[h + 1, iz, ix] = v.id(S.FLOWERS[int(which[iz, ix] * 7) % 7])
        elif f > 0.55 and r < 0.16:
            ids[h + 1, iz, ix] = v.id("minecraft:fern")
        elif f > 0.55 and r < 0.24 and ids[h, iz, ix] == moss:
            ids[h + 1, iz, ix] = v.id("minecraft:moss_carpet")
        elif r < 0.30:
            ids[h + 1, iz, ix] = v.id("minecraft:short_grass")
        elif r < 0.34:
            ids[h + 1, iz, ix] = v.id("minecraft:tall_grass[half=lower]")
            ids[h + 2, iz, ix] = v.id("minecraft:tall_grass[half=upper]")

    # ------------------------------------------------------------ 仕上げ
    # 2) 横に空気がある土・草 (上が空気でないもの) は石の縞に (切り通しの土の壁を見せない)。町の中と見どころのそばは触らない
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
    face = soil & side & above_solid & (~in_town & (dist_built > 0.5))[None, :, :]
    for (iy, iz, ix) in np.argwhere(face):
        ids[iy, iz, ix] = v.id(S.strata(ix + v.ox, iy + v.oy, iz + v.oz, 3))
    print(f"soil faces {int(face.sum())}")
    for (iz, ix, big) in spires:
        print(f"spire {'big' if big else 'small'} {ix + v.ox},{int(H[iz, ix]) + v.oy},{iz + v.oz}")

    # 縁にまたがる場所 (町は別): 立体からは建物を抜き、地面だけ残す。ゲームが同じ場所・高さで一度だけ建てる (SiteJob.authoredDone)
    nat_flags = v.natural_flags()
    for e in v.header.get("sites", {}).get("entries", []):
        if e["kind"] == "town":
            continue
        r = 72 if e["kind"] == "ribs" else (12 if e["kind"].startswith("ch:") else 22)
        ex, ez = e["pos"][0], e["pos"][2]
        if v.ox + r <= ex <= v.ox + SX - 1 - r and v.oz + r <= ez <= v.oz + SZ - 1 - r:
            continue
        circ = (xs - ex) ** 2 + (zs - ez) ** 2 < r * r
        vol = circ[None, :, :] & ~np.isin(ids, np.nonzero(nat_flags[:len(nat_flags)])[0])
        ids[vol] = air
        print(f"edge site {e['kind']}#{e['index']} at {ex},{ez}: removed {int(vol.sum())} built blocks")

    tiles = alib.vol_to_tiles(v, "whale")
    alib.write_land_index("whale", tiles, v.header.get("sites", {"entries": []}))
    out = alib.OUT_DIR / "whale"
    # 撮影用: 道の口と町の台の高さ
    print(f"wrote {out} ({len(tiles)} tiles, {sum(f.stat().st_size for f in out.glob('*.bin')) // 1024} KB); town top y {P + v.oy}")
    for name, (wx, wz) in {"east": (cx + 95, cz), "west": (cx - 95, cz), "north": (cx, cz - 95)}.items():
        jx, jz = wx - v.ox, wz - v.oz
        print(f"ground {name} {wx},{int(H[jz, jx]) + v.oy},{wz}")


if __name__ == "__main__":
    main()
