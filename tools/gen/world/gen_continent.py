# 大陸の地形の図を作る (docs/design/40 の 3 章「作る順」の 2)。
#   python tools/gen/world/gen_continent.py
#   -> src/main/resources/data/skyabyss/continent/height.bin (地面の高さ Y)・biome.bin (生物群系の番号)・region.bin (土地の番号。敵の強さ)。どれも gzip
#      layout.json (都・はじまり・町・跡地・道・渡し船・土地の名前の座標。world/continent/ContinentLayout が読む)
#      assets/skyabyss/textures/gui/map/continent.png (旅の地図 (M) に敷く、描いた地図の絵。1024 x 1024 で 4000 x 4000)
#      docs/review/continent_roughs/terrain_<案>.png (確かめ用の陰影図)
# 見取り図の値は tools/lib/continent_layout.py (CHOSEN の案)。1 升 = 4 ブロック、4000 x 4000 を 1000 x 1000 升で。
# ゲーム側は world/continent/ContinentChunkGenerator が読み、升の間をなめらかにつなぎ、細かな揺らぎを足す。
# 土地の番号は ContinentBiomeSource の biomes の並び (data/skyabyss/worldgen/world_preset/continent.json) と同じ
import gzip
import json
import math
import os
import struct
import sys

import numpy as np
from PIL import Image
from scipy import ndimage

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(os.path.dirname(os.path.dirname(HERE)))
sys.path.insert(0, os.path.join(ROOT, 'tools', 'lib'))
from continent_layout import CHOSEN, HALF, LAYOUTS, fbm, terrain  # noqa: E402

OUT = os.path.join(ROOT, 'src', 'main', 'resources', 'data', 'skyabyss', 'continent')
REVIEW = os.path.join(ROOT, 'docs', 'review', 'continent_roughs')
SIZE = 1000
SCALE = 4
SEA_LEVEL = 63   # 水面は 62 まで

# 土地の番号 (ContinentBiomeSource の並び)
BIOME_NAMES = ['deep_ocean', 'ocean', 'lukewarm_ocean', 'beach', 'plains', 'forest', 'meadow', 'windswept_hills', 'taiga',
               'savanna', 'windswept_gravelly_hills', 'stony_peaks', 'snowy_slopes', 'badlands', 'eroded_badlands', 'river',
               'birch_forest', 'swamp', 'desert', 'old_growth_pine_taiga', 'wooded_badlands', 'flower_forest']
B = {n: i for i, n in enumerate(BIOME_NAMES)}

# 土地 (見取り図の regions の種類) の番号。region.bin の値。world/continent/ContinentMap.region と同じ並び
REGION_IDS = ['meadow', 'start', 'whale', 'tide', 'storm', 'ember']

# 土地ごとの (なだらかな高さ, 起伏)。ブロック
SHAPE = {
    'meadow': (10, 15),
    'start': (10, 9),
    'whale': (28, 30),
    'tide': (5, 9),
    'storm': (55, 45),
    'ember': (18, 16),
}
# 平らにする所: (半径, なじませる外の半径, 高さ)。都は町を建てるので広く
FLAT_CAPITAL = (130, 230, 72)
FLAT_TOWN = (48, 110, None)
FLAT_START = (30, 80, None)
FLAT_SITE = (40, 90, None)

# 確かめ用の色
PREVIEW = {
    'deep_ocean': (40, 70, 110), 'ocean': (60, 100, 140), 'lukewarm_ocean': (60, 130, 150), 'beach': (220, 210, 160),
    'plains': (130, 170, 90), 'forest': (80, 130, 70), 'meadow': (150, 190, 110), 'windswept_hills': (130, 140, 120),
    'taiga': (90, 120, 90), 'savanna': (180, 175, 100), 'windswept_gravelly_hills': (140, 140, 140), 'stony_peaks': (170, 170, 170),
    'snowy_slopes': (240, 240, 245), 'badlands': (190, 110, 70), 'eroded_badlands': (90, 60, 60), 'river': (70, 120, 170),
    'birch_forest': (170, 200, 120), 'swamp': (90, 120, 90), 'desert': (230, 215, 160), 'old_growth_pine_taiga': (70, 100, 70),
    'wooded_badlands': (170, 120, 80), 'flower_forest': (190, 200, 110),
}


# 川の種 (見取り図の案ごと)。近くの、ふもとの高さ (RIVER_SOURCE_Y) の所から下り始める
RIVER_SEEDS = {
    'c': [(200, -1000), (-200, -950), (-150, -450), (-950, 450), (1150, -450), (600, 700)],
    'c1': [(200, -1000), (-200, -950), (-150, -450), (-950, 450), (1150, -450), (600, 700)],
}
RIVER_SOURCE_Y = (84, 112)
RIVER_KEEP_OUT = 150  # 都・町・はじまりのまわり (ブロック) は削らない


def n_of(a):
    return a.shape[0]


# 川すじを蛇行させる: 道のりに沿って、横へ波の形にずらす (波長と振れ幅は決まった乱数)。両端は動かさない。陸から出る点はずらさない
def meander(path, land, seed):
    if len(path) < 8:
        return path
    rng = np.random.default_rng(seed)
    pts = np.array(path, dtype=float)
    sm = ndimage.uniform_filter1d(pts, size=9, axis=0, mode='nearest')
    tan = np.gradient(sm, axis=0)
    tan /= np.maximum(1e-6, np.hypot(tan[:, 0], tan[:, 1]))[:, None]
    nrm = np.stack([-tan[:, 1], tan[:, 0]], axis=1)
    s_ = np.arange(len(pts))
    lam = rng.uniform(35, 60)
    amp = rng.uniform(5, 9)
    env = np.minimum(1, np.minimum(s_, len(pts) - 1 - s_) / 15.0)
    off = amp * env * (np.sin(2 * np.pi * s_ / lam + rng.uniform(0, 6.28)) + 0.4 * np.sin(2 * np.pi * s_ / (lam * 0.37)))
    moved = sm + nrm * off[:, None]
    n = land.shape[0]
    out = []
    prev = None
    for (a, b), (oa, ob) in zip(moved, pts):
        i, j = int(round(a)), int(round(b))
        if not (0 <= i < n and 0 <= j < n and land[i, j]):
            i, j = int(oa), int(ob)
        # 隙間を埋める (一升ずつ)
        if prev is not None:
            pi, pj = prev
            steps = max(abs(i - pi), abs(j - pj))
            for t in range(1, steps):
                out.append((pi + round((i - pi) * t / steps), pj + round((j - pj) * t / steps)))
        out.append((i, j))
        prev = (i, j)
    return out


def carve_rivers(y, land, X, Z, lay):
    river = np.zeros(y.shape, dtype=bool)
    seeds = RIVER_SEEDS.get(CHOSEN, [])
    if not seeds:
        return y, river
    sm = ndimage.gaussian_filter(y, 3)
    # 下る向きは「水までの近さ」が主 (行き止まりが無い)。高さと揺らぎで少し蛇行させる
    to_water = ndimage.distance_transform_edt(land)
    wiggle = fbm(77, octaves=3, base=14, n=n_of(y))
    flow = to_water + (sm - SEA_LEVEL) * 0.08 + wiggle * 14
    keep = np.zeros(y.shape, dtype=bool)
    for c in [lay['capital'], lay['start']] + [p for _, p in lay['towns']]:
        keep |= np.hypot(X - c[0], Z - c[1]) < RIVER_KEEP_OUT
    ys = y.copy()
    n = y.shape[0]
    for sx, sz in seeds:
        ci, cj = int((sz + HALF) / SCALE), int((sx + HALF) / SCALE)
        # ふもとの高さの、いちばん近い所を探す
        best = None
        for r in range(0, 60):
            for di in range(-r, r + 1):
                for dj in (-r, r) if abs(di) != r else range(-r, r + 1):
                    i, j = ci + di, cj + dj
                    if 0 <= i < n and 0 <= j < n and land[i, j] and RIVER_SOURCE_Y[0] <= sm[i, j] <= RIVER_SOURCE_Y[1]:
                        best = (i, j)
                        break
                if best:
                    break
            if best:
                break
        if best is None:
            continue
        path = [best]
        seen = {best}
        i, j = best
        for _ in range(2000):
            if not land[i, j] or ys[i, j] < SEA_LEVEL - 1:
                break
            nb = [(flow[i + a, j + b], to_water[i + a, j + b], i + a, j + b) for a in (-1, 0, 1) for b in (-1, 0, 1)
                  if (a or b) and 0 <= i + a < n and 0 <= j + b < n and (i + a, j + b) not in seen]
            if not nb:
                break
            down = [q for q in nb if q[0] < flow[i, j]]
            # 行き止まり (揺らぎのくぼみ) では、水へ近づく向きへ
            _, _, i, j = min(down) if down else min(nb, key=lambda q: q[1])
            seen.add((i, j))
            path.append((i, j))
        path = meander(path, land, seed=len(path) + ci * 7 + cj)
        for k, (i, j) in enumerate(path):
            r = min(3.0, 1.2 + k / 200.0)
            rr = int(r) + 30
            i0, i1, j0, j1 = max(0, i - rr), min(n, i + rr + 1), max(0, j - rr), min(n, j + rr + 1)
            ii, jj = np.mgrid[i0:i1, j0:j1]
            d = np.hypot(ii - i, jj - j)
            sub = y[i0:i1, j0:j1]
            kp = keep[i0:i1, j0:j1]
            bed = np.where(d <= r, SEA_LEVEL - 2 - (r - d), np.inf)
            bank = np.where(d > r, SEA_LEVEL + (d - r) * 2.2, np.inf)
            new = np.minimum(sub, np.minimum(bed, bank))
            y[i0:i1, j0:j1] = np.where(kp, sub, new)
            river[i0:i1, j0:j1] |= (d <= r) & ~kp
    return y, river


def flatten(y, X, Z, center, spec, land):
    r0, r1, target = spec
    cx, cz = center
    d = np.hypot(X - cx, Z - cz)
    if target is None:
        target = float(np.median(y[(d < r0) & land])) if np.any((d < r0) & land) else 70.0
        target = max(target, 66.0)
    t = np.clip((d - r0) / (r1 - r0), 0, 1)
    t = t * t * (3 - 2 * t)
    return np.where(land | (d < r0), y * t + target * (1 - t), y)



# ---------------------------------------------------------------- はじまりの島の谷 (見取り図の island_plan。2026-10-26)
# 道は尾根に挟まれた谷を通る (谷底の半幅 VALLEY_HALF)。道の脇の開けた所 (pockets) と首 (necks) は床の高さ。そのほかの島の陸は、
# 開けた所から WALL_RUN で尾根 (谷底 + RIDGE_LOW..RIDGE_HIGH) まで上がる (崖)。海ぎわも尾根のまま海へ落ちる (歩ける浜は野営地と船着き場だけ)。
# 北の尾根は高い山並み、南はやや低い丘。尾根には林 (木が道の外を塞ぐ)
# 2026-10-27 の声「道からそらしすぎなくていい」: 谷を広く、壁を低くなだらかに (道の外の丘も歩ける。道は谷底の目印のまま)
VALLEY_HALF = 11.0
WALL_RUN = 18.0
RIDGE_LOW = 6.0
RIDGE_HIGH = 14.0


def _polyline_dist(X, Z, pts):
    """各点から折れ線までの距離と、いちばん近い所の道のり"""
    best = np.full(X.shape, 1e9)
    along = np.zeros(X.shape)
    s0 = 0.0
    for (ax, az), (bx, bz) in zip(pts[:-1], pts[1:]):
        dx, dz = bx - ax, bz - az
        L2 = dx * dx + dz * dz
        L = math.sqrt(L2)
        u = np.clip(((X - ax) * dx + (Z - az) * dz) / max(L2, 1e-9), 0, 1)
        d = np.hypot(X - (ax + u * dx), Z - (az + u * dz))
        m = d < best
        best = np.where(m, d, best)
        along = np.where(m, s0 + u * L, along)
        s0 += L
    return best, along, s0


def _seg_dist(X, Z, a, b):
    ax, az = a
    bx, bz = b
    dx, dz = bx - ax, bz - az
    L2 = dx * dx + dz * dz
    u = np.clip(((X - ax) * dx + (Z - az) * dz) / max(L2, 1e-9), 0, 1)
    return np.hypot(X - (ax + u * dx), Z - (az + u * dz))


def shape_island(y, X, Z, lay, land, region, kinds, seed=7):
    plan = lay.get('island_plan')
    if not plan:
        return y, None
    # 島の陸 (見取り図の island の楕円の中。土地の番号の境で段ができないように)
    (icx, icz), ir, _ = lay['island']
    isl = land & (np.hypot(X - icx, Z - icz) < ir * 1.6)
    if not isl.any():
        return y, None
    road = curve(lay['roads'][0])
    dR, along, total = _polyline_dist(X, Z, road)
    prof = plan['profile']
    ps = np.array([a for a, _ in prof], dtype=float) * (total / prof[-1][0])
    floor_road = np.interp(along, ps, [h for _, h in prof])
    # 開けた所までの距離 (内側は負) と、その床の高さ
    open_d = dR - VALLEY_HALF
    floor = floor_road.copy()
    for name, (cx, cz), r, fl in plan['pockets']:
        rx, rz = (r, r) if not isinstance(r, tuple) else r
        q = np.hypot((X - cx) / rx, (Z - cz) / rz)
        d = (q - 1) * min(rx, rz)
        if fl is None:
            # 道の高さ (中心にいちばん近い道の点)
            k = np.argmin(np.hypot(X - cx, Z - cz) + np.where(isl, 0, 1e9))
            fh = floor_road.flat[k]
        elif isinstance(fl, str):
            k = np.argmin(np.hypot(X - cx, Z - cz) + np.where(isl, 0, 1e9))
            fh = floor_road.flat[k] + float(fl)
        else:
            fh = float(fl)
        w = np.clip(-d / 6.0, 0, 1)
        floor = np.where(d < open_d, floor * (1 - w) + fh * w, floor)
        open_d = np.minimum(open_d, d)
    for a, b, half in plan['necks']:
        open_d = np.minimum(open_d, _seg_dist(X, Z, a, b) - half)
    # 尾根: 北ほど高い (島の中心より北)。揺らぎで峰と鞍部
    cz_isl = float(Z[isl].mean())
    north = np.clip((cz_isl - Z) / 140.0, -1, 1) * 0.5 + 0.5
    n1 = fbm(seed + 71, octaves=4, base=60, n=SIZE)
    n2 = fbm(seed + 72, octaves=3, base=140, n=SIZE)
    ridge = RIDGE_LOW + (RIDGE_HIGH - RIDGE_LOW) * np.clip(north * 0.75 + n1 * 0.9 + 0.2, 0, 1)
    t = np.clip(open_d / WALL_RUN, 0, 1)
    t = t * t * (3 - 2 * t)
    rock = 1.0 + n2 * 0.7
    new = floor + t * ridge * rock + np.clip(open_d - WALL_RUN, 0, 40) * 0.15 + n1 * 3 * t
    # 床の小さな起伏 (開けた所の中)
    new = new + (1 - t) * n2 * 1.5
    y = np.where(isl, new, y)
    ridge_mask = isl & (open_d > WALL_RUN * 0.6)
    return y, ridge_mask

def build(key=CHOSEN, seed=7):
    lay = LAYOUTS[key]
    ex = {}
    land, _, region = terrain(lay, seed, n=SIZE, out=ex)
    X, Z, field, ridge, dists = ex['X'], ex['Z'], ex['field'], ex['ridge'], ex['dists']
    kinds = [k for k, _ in lay['regions']]
    # 土地どうしをなじませる重み
    dmin = np.minimum.reduce(dists)
    weights = [np.exp(-(d - dmin) / 120.0) for d in dists]
    wsum = sum(weights)
    base = sum(w * SHAPE[k][0] for w, k in zip(weights, kinds)) / wsum
    rough = sum(w * SHAPE[k][1] for w, k in zip(weights, kinds)) / wsum
    c = np.clip((field - 0.18) / 0.3, 0, 1)
    hills = fbm(seed + 11, octaves=6, base=12, n=SIZE) + 0.5
    fine = fbm(seed + 12, octaves=4, base=40, n=SIZE)
    mid = fbm(seed + 13, octaves=3, base=48, n=SIZE)
    # 尾根の線が立つ揺らぎ (峰と丘陵)。1 - |n| を二乗すると、細い尾根と谷が交互に並ぶ
    ridged = (1 - np.abs(fbm(seed + 14, octaves=5, base=18, n=SIZE) * 2.2)) ** 2
    wk = {k: sum(w for w, kk in zip(weights, kinds) if kk == k) / wsum for k in set(kinds)}
    mountain = wk.get('storm', 0) * 1.0 + wk.get('whale', 0) * 0.35
    y = (65 + c ** 0.6 * base + hills * rough * c ** 0.5 + ridge * 95 + fine * 3 * c
         + mid * rough * 0.6 * c + ridged * mountain * 38 * c + ridge * ridged * 30)
    # 岩の小山 (野・丘陵・浜に、ところどころ。2026-10-25「平原ばかりで何もない」)。とがった岩肌は段差で石になる
    knoll = np.clip((fbm(seed + 60, octaves=3, base=70, n=SIZE) - 0.16) * 7, 0, 1) ** 2
    lowland = wk.get('meadow', 0) + wk.get('whale', 0) * 0.7 + wk.get('tide', 0) * 0.5
    y = y + knoll * 14 * lowland * c
    # 灰の荒野: 段の付いた台地 (メサ)。ところどころに高い台
    ember = wk.get('ember', 0)
    mesa = np.clip(fbm(seed + 15, octaves=4, base=22, n=SIZE) * 3 - 0.2, 0, 1) * 26
    ye = y + mesa * ember * c
    step = 7.0
    k = (ye - 65) / step
    frac = k - np.floor(k)
    terr = 65 + (np.floor(k) + np.clip((frac - 0.72) / 0.28, 0, 1) ** 2) * step
    y = np.where(ember > 0.5, ye * (1 - ember) + terr * ember, ye)
    # 火山の火口 (灰の荒野のいちばん高い所のくぼみ)
    em = np.array([kk == 'ember' for kk in kinds])[region] & land
    if em.any():
        yy = np.where(em, y, -1)
        pi, pj = np.unravel_index(np.argmax(yy), yy.shape)
        d = np.hypot(X - X[pi, pj], Z - Z[pi, pj])
        y = y - np.clip(1 - d / 60.0, 0, 1) ** 1.5 * 34 * em
    # 海の深さ: 岸から離れるほど深く
    px_from_land = ndimage.distance_transform_edt(~land)
    inner = ~land
    labels, _ = ndimage.label(inner)
    border = set(np.unique(np.concatenate([labels[0], labels[-1], labels[:, 0], labels[:, -1]]))) - {0}
    outer = np.isin(labels, list(border))
    inland = inner & ~outer
    # 小さな水 (湖) は淡水: 川と同じ土地
    small = np.zeros(y.shape, dtype=bool)
    lab_in, n_in = ndimage.label(inland)
    if n_in:
        sizes = ndimage.sum(inland, lab_in, range(1, n_in + 1))
        for idx, sz in enumerate(sizes, start=1):
            if sz < 3000:
                small |= lab_in == idx
    depth = np.clip(px_from_land * SCALE / 5.0, 1, 30)
    depth = np.where(inland, np.minimum(depth, 20), depth)
    y = np.where(land, np.maximum(y, SEA_LEVEL + 1), SEA_LEVEL - 1 - depth)
    # 川 (山のふもとから海へ。決まった種の点から坂を下る)
    y, river = carve_rivers(y, land, X, Z, lay)
    # 平らにする所
    y = flatten(y, X, Z, lay['capital'], FLAT_CAPITAL, land)
    for _, p in lay['towns']:
        y = flatten(y, X, Z, p, FLAT_TOWN, land)
    for _, p in lay['sites']:
        y = flatten(y, X, Z, p, FLAT_SITE, land)
    if 'island_plan' not in lay:
        y = flatten(y, X, Z, lay['start'], FLAT_START, land)
    y, island_ridge = shape_island(y, X, Z, lay, land, region, kinds, seed)
    height = np.clip(np.round(y), 1, 250).astype(np.uint8)

    # 土地の番号
    biome = np.zeros((SIZE, SIZE), dtype=np.uint8)
    patch = fbm(seed + 21, octaves=4, base=16, n=SIZE)
    near_water = ndimage.distance_transform_edt(land) * SCALE <= 12
    for i, k in enumerate(kinds):
        m = (region == i) & land
        if k == 'meadow':
            biome[m] = np.where(patch[m] > 0.08, B['forest'], B['plains'])
        elif k == 'start':
            biome[m] = B['meadow']
        elif k == 'whale':
            biome[m] = np.where(height[m] > 100, B['windswept_hills'], np.where(patch[m] > 0.05, B['taiga'], B['plains']))
        elif k == 'tide':
            biome[m] = np.where(height[m] <= 70, B['beach'], B['savanna'])
        elif k == 'storm':
            biome[m] = np.where(height[m] > 175, B['snowy_slopes'],
                                np.where(height[m] > 135, B['stony_peaks'],
                                         np.where(patch[m] > 0, B['windswept_gravelly_hills'], B['windswept_hills'])))
        elif k == 'ember':
            biome[m] = np.where(height[m] > 120, B['eroded_badlands'], B['badlands'])
    if island_ridge is not None:
        # 島の尾根は林 (道の外を木が塞ぐ)。高い所は白樺まじり
        biome[island_ridge] = np.where(patch[island_ridge] > 0.0, B['forest'], B['birch_forest'])
    beach = land & near_water & (height <= SEA_LEVEL + 3)
    keep = np.isin(biome, [B['badlands'], B['eroded_badlands'], B['stony_peaks'], B['snowy_slopes']])
    biome[beach & ~keep] = B['beach']
    # 土地の中のムラ (見取り図の subs)。岸と水の上は除く
    SUB_BIOMES = {'birch': 'birch_forest', 'marsh': 'swamp', 'dunes': 'desert', 'pine': 'old_growth_pine_taiga',
                  'basalt': 'eroded_badlands', 'ashwood': 'wooded_badlands', 'flowers': 'flower_forest'}
    for kind, m in ex.get('subs', []):
        mm = m & land & ~beach & (height >= SEA_LEVEL) & (height < 170)
        biome[mm] = B[SUB_BIOMES[kind]]
    biome[river & land & (height < SEA_LEVEL)] = B['river']
    sea = ~land
    biome[sea] = np.where(inland[sea], B['lukewarm_ocean'], np.where(depth[sea] > 18, B['deep_ocean'], B['ocean']))
    biome[small] = B['river']
    # 土地の番号 (敵の強さ): REGION_IDS の並び。海も近い土地の番号
    reg = np.zeros((SIZE, SIZE), dtype=np.uint8)
    for i, k in enumerate(kinds):
        reg[region == i] = REGION_IDS.index(k)
    return lay, height, biome, reg


def write_bin(path, arr):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    head = b'SKCN' + struct.pack('>iiii', SIZE, SCALE, -HALF, -HALF)
    raw = head + arr.astype(np.uint8).tobytes()
    with open(path, 'wb') as f:
        # 時刻を入れない (何度作っても同じ中身)
        f.write(gzip.compress(raw, compresslevel=9, mtime=0))


def preview(key, height, biome):
    col = np.zeros((SIZE, SIZE, 3))
    for name, rgb in PREVIEW.items():
        col[biome == B[name]] = rgb
    h = height.astype(float)
    gz, gx = np.gradient(h)
    shade = np.clip(1.0 - (gx + gz) * 0.08, 0.55, 1.35)[..., None]
    img = np.clip(col * shade, 0, 255).astype(np.uint8)
    os.makedirs(REVIEW, exist_ok=True)
    Image.fromarray(img).save(os.path.join(REVIEW, f'terrain_{key}.png'))


# 道を曲線にする (角を丸める Catmull-Rom、約 24 ブロックごとの点)。決まった揺らぎで少し蛇行させる (端は動かさない)
def curve(points, step=24.0, seed=31):
    pts = [np.array(p, dtype=float) for p in points]
    ext = [pts[0] * 2 - pts[1]] + pts + [pts[-1] * 2 - pts[-2]]
    out = [pts[0]]
    rng = np.random.default_rng(seed + int(abs(points[0][0]) + abs(points[-1][1])))
    for i in range(1, len(ext) - 2):
        p0, p1, p2, p3 = ext[i - 1], ext[i], ext[i + 1], ext[i + 2]
        n = max(2, int(np.linalg.norm(p2 - p1) / step))
        for k in range(1, n + 1):
            t = k / n
            q = 0.5 * ((2 * p1) + (-p0 + p2) * t + (2 * p0 - 5 * p1 + 4 * p2 - p3) * t * t + (-p0 + 3 * p1 - 3 * p2 + p3) * t ** 3)
            out.append(q)
    res = [out[0]]
    for i in range(1, len(out) - 1):
        d = out[i + 1] - out[i - 1]
        nrm = np.array([-d[1], d[0]]) / (np.linalg.norm(d) + 1e-9)
        res.append(out[i] + nrm * rng.uniform(-6, 6))
    res.append(out[-1])
    return [[int(round(q[0])), int(round(q[1]))] for q in res]


# 土地の名前 (地図に出す。lang の map.skyabyss.continent.<id>)
REGION_NAMES = {'岩鯨の丘陵': 'whale', '潮の浜': 'tide', '嵐の高地': 'storm', '灰の荒野': 'ember', '外れの谷': 'outskirts', 'はじまりの島': 'outskirts', '内海': 'inland_sea'}
LEVEL_REGIONS = ['capital', 'whale', 'tide', 'storm', 'ember']
MAP_PX = 1024
MAP_COLORS = {
    'deep_ocean': (150, 172, 172), 'ocean': (168, 188, 184), 'lukewarm_ocean': (160, 192, 186), 'beach': (226, 212, 166),
    'plains': (176, 186, 124), 'forest': (150, 168, 108), 'meadow': (184, 194, 130), 'windswept_hills': (164, 166, 132),
    'taiga': (140, 154, 108), 'savanna': (204, 194, 138), 'windswept_gravelly_hills': (170, 168, 150), 'stony_peaks': (186, 182, 168),
    'snowy_slopes': (230, 228, 220), 'badlands': (200, 140, 100), 'eroded_badlands': (120, 92, 84), 'river': (150, 184, 186),
    'birch_forest': (190, 200, 140), 'swamp': (120, 140, 110), 'desert': (232, 216, 168), 'old_growth_pine_taiga': (120, 140, 100),
    'wooded_badlands': (190, 140, 100), 'flower_forest': (196, 196, 130),
}
PAPER = np.array((232, 220, 190), dtype=float)
INK = (66, 52, 40)


def paint_map(lay, height, biome, path):
    from PIL import ImageDraw, ImageFilter
    col = np.zeros((SIZE, SIZE, 3))
    for name, rgb in MAP_COLORS.items():
        col[biome == B[name]] = rgb
    h = height.astype(float)
    gz, gx = np.gradient(ndimage.gaussian_filter(h, 1.2))
    shade = np.clip(1.0 - (gx + gz) * 0.05, 0.62, 1.25)[..., None]
    land = h >= SEA_LEVEL
    col = np.where(land[..., None], col * shade, col)
    col = col * 0.8 + PAPER * 0.2
    grain = fbm(91, octaves=3, base=80, n=SIZE)[..., None] * 18
    img = Image.fromarray(np.clip(col + grain, 0, 255).astype(np.uint8)).resize((MAP_PX, MAP_PX), Image.BICUBIC)
    # 等高線 (40 ごと、陸だけ、薄く)
    band = (np.floor((h - SEA_LEVEL) / 40)).astype(int)
    edges = (np.abs(np.diff(band, axis=0, prepend=band[:1])) + np.abs(np.diff(band, axis=1, prepend=band[:, :1]))) > 0
    cont = Image.fromarray(((edges & land) * 255).astype(np.uint8)).resize((MAP_PX, MAP_PX), Image.NEAREST)
    img.paste(Image.new('RGB', img.size, (110, 92, 70)), mask=cont.point(lambda v: 70 if v else 0))
    # 岸の線と波
    lm = Image.fromarray((land * 255).astype(np.uint8)).resize((MAP_PX, MAP_PX), Image.BICUBIC).point(lambda v: 255 if v > 127 else 0)
    for k, a in ((6, 80), (13, 45)):
        ring = lm.filter(ImageFilter.MaxFilter(k * 2 + 1)).filter(ImageFilter.FIND_EDGES)
        img.paste(Image.new('RGB', img.size, (92, 120, 128)), mask=ring.point(lambda v, a=a: a if v else 0))
    img.paste(Image.new('RGB', img.size, INK), mask=lm.filter(ImageFilter.FIND_EDGES).point(lambda v: 200 if v else 0))
    d = ImageDraw.Draw(img)
    rng = np.random.default_rng(5)
    s = MAP_PX / SIZE

    def px(p):
        return ((p[0] + HALF) / (2 * HALF) * MAP_PX, (p[1] + HALF) / (2 * HALF) * MAP_PX)

    # 峰の記号と林の点
    for _ in range(5000):
        i, j = int(rng.integers(4, SIZE - 4)), int(rng.integers(4, SIZE - 4))
        x, y = i * s, j * s
        b = BIOME_NAMES[biome[j, i]]
        if h[j, i] > 150 and rng.random() < 0.5:
            r = 3 + (h[j, i] - 150) / 30
            d.line([(x - r, y + r * 0.6), (x, y - r * 0.7), (x + r, y + r * 0.6)], fill=INK, width=1)
        elif b in ('forest', 'taiga') and rng.random() < 0.6:
            d.ellipse([x - 2, y - 2, x + 2, y + 2], fill=(84, 108, 70))
        elif b == 'eroded_badlands' and rng.random() < 0.3:
            d.point((x, y), fill=(60, 40, 36))
    # 道と渡し船
    for road in lay['roads']:
        pts = [px(q) for q in curve(road)]
        # 2026-10-27 の声「マップもっと見やすく」: 道を太く濃く (縁の明るい線 + 濃い茶の芯)
        d.line(pts, fill=(246, 236, 210), width=7, joint='curve')
        d.line(pts, fill=(98, 60, 32), width=4, joint='curve')
    for ferry in lay['ferries']:
        pts = [px(q) for q in ferry]
        for (x0, y0), (x1, y1) in zip(pts, pts[1:]):
            n = max(1, int(np.hypot(x1 - x0, y1 - y0) / 6))
            for i in range(0, n, 2):
                a0, a1 = i / n, min(1, (i + 1) / n)
                d.line([(x0 + (x1 - x0) * a0, y0 + (y1 - y0) * a0), (x0 + (x1 - x0) * a1, y0 + (y1 - y0) * a1)], fill=(60, 96, 120), width=2)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    # 外の海は、岸から離れるほど透かす (地図の盤の地へなじませる。四角い縁を見せない)
    far = ndimage.distance_transform_edt(~land) * SCALE
    alpha = np.clip(1 - (far - 160) / 280, 0, 1)
    a = Image.fromarray((alpha * 255).astype(np.uint8)).resize((MAP_PX, MAP_PX), Image.BICUBIC)
    img = img.convert('RGBA')
    img.putalpha(a)
    img.save(path, optimize=True)


# 渡し船の両端の船着き場: 渡し船の線の端から、もう一方の端へ向かって歩き、水に出る手前 (岸から 6 ブロック陸の側) の点
def ferry_docks(line, height):
    def land(x, z):
        i, j = int((x + HALF) / SCALE), int((z + HALF) / SCALE)
        return 0 <= i < SIZE and 0 <= j < SIZE and height[j, i] >= SEA_LEVEL

    def dock(a, b):
        ax, az = a
        bx, bz = b
        L = math.hypot(bx - ax, bz - az)
        last = None
        for k in range(0, int(L), 2):
            x, z = ax + (bx - ax) * k / L, az + (bz - az) * k / L
            if land(x, z):
                last = (x, z)
            elif last is not None:
                break
        if last is None:
            last = (ax, az)
        ux, uz = (bx - ax) / L, (bz - az) / L
        return [int(round(last[0] - ux * 6)), int(round(last[1] - uz * 6))]
    return [dock(line[0], line[1]), dock(line[-1], line[-2])]


def trim_road_ends(out):
    """見どころへの脇道は、建物の手前 (部品の範囲 + 3) で止める (2026-10-28 の試遊「道の上には何も乗らないように」。道の幅 8 で建物に食い込んでいた)"""
    pp = os.path.join(OUT, 'pois.json')
    if not os.path.exists(pp):
        return
    pois = json.load(open(pp, encoding='utf-8'))
    for r in out['roads']:
        for end in (0, -1):
            x, z = r[end]
            for q in pois:
                if q['kind'] == 'signpost' or math.hypot(q['x'] - x, q['z'] - z) >= 2:
                    continue
                bpp = os.path.join(OUT, '..', 'opening', 'poi_%s.json' % q['kind'])
                if not os.path.exists(bpp):
                    continue
                half = max(abs(v) for v in json.load(open(bpp, encoding='utf-8'))['meta']['rect'])
                keep = half + 3
                pts = r if end == -1 else r[::-1]
                while len(pts) > 2 and math.hypot(pts[-2][0] - x, pts[-2][1] - z) < keep:
                    pts.pop()
                ax, az = pts[-2]
                d = math.hypot(ax - x, az - z)
                t = (d - keep) / d if d > keep else 0
                pts[-1] = [round(ax + (x - ax) * t), round(az + (z - az) * t)]
                if end == 0:
                    r[:] = pts[::-1]


# 生成のあとで手で動かした点 (layout.json を直に直した分。生成し直しても戻らないように、ここで同じ値にする)。
# (道の番号, 端 (0 か -1)) -> [x, z]
HAND_ROAD_ENDS = {
    # 汐守の東の脇道の端: 0.9.5 の点検で建物の手前へ 5 ブロック寄せた (trim_road_ends の計算より少し先)
    (13, -1): [1483, 80],
}


def hand_fix_roads(out):
    for (i, end), p in HAND_ROAD_ENDS.items():
        if i < len(out['roads']):
            out['roads'][i][end] = list(p)


def write_layout(lay, height=None):
    def pt(p):
        return [int(p[0]), int(p[1])]
    kinds = {'岩鯨の跡地': 'whale', '潮殻の跡地': 'tide', '雷翼の跡地': 'storm', '余燼の跡地': 'ember'}
    towns = {'鯨見': 'whale', '汐守': 'tide', '風待': 'storm', '灰炉': 'ember'}
    out = {
        'layout': CHOSEN,
        'capital': pt(lay['capital']),
        'start': pt(lay['start']),
        'dock': pt(lay.get('dock', lay['start'])),
        'towns': {towns[n]: pt(p) for n, p in lay['towns']},
        'sites': {kinds[n]: pt(p) for n, p in lay['sites']},
        'roads': [curve(r) for r in lay['roads']],
        'ferries': [[pt(p) for p in r] for r in lay['ferries']],
        'names': {REGION_NAMES[n]: pt(p) for n, p in lay['names']},
        'levels': {k: [lv.replace('Lv ', '').replace('–', '-'), pt(p)] for k, (lv, p) in zip(LEVEL_REGIONS, lay['levels'])},
    }
    if height is not None:
        out['ferry_docks'] = [ferry_docks(f, height) for f in lay['ferries']]
    plan = lay.get('island_plan')
    if plan:
        pk = {n: pt(c) for n, c, _, _ in plan['pockets']}
        out['island'] = {'gate': pt(plan['gate']), 'prints': [pt(p) for p in plan['prints']], 'fire': pk['fire'], 'shrine': pk['shrine'],
                         'bandits': pk['bandits'], 'prints_view': pt(plan['necks'][0][0])}
    trim_road_ends(out)
    hand_fix_roads(out)
    with open(os.path.join(OUT, 'layout.json'), 'w', encoding='utf8', newline='\n') as f:
        json.dump(out, f, ensure_ascii=False, separators=(',', ':'))
        f.write('\n')


def main():
    lay, height, biome, reg = build()
    write_bin(os.path.join(OUT, 'height.bin'), height)
    write_bin(os.path.join(OUT, 'biome.bin'), biome)
    write_bin(os.path.join(OUT, 'region.bin'), reg)
    preview(CHOSEN, height, biome)
    write_layout(lay, height)
    paint_map(lay, height, biome, os.path.join(ROOT, 'src', 'main', 'resources', 'assets', 'skyabyss', 'textures', 'gui', 'map', 'continent.png'))
    land = height >= SEA_LEVEL
    print(f'layout {CHOSEN}: land {land.mean() * 100:.0f}%, max Y {height.max()}, capital Y {height[SIZE // 2, int((lay["capital"][0] + HALF) / SCALE)]}')


if __name__ == '__main__':
    main()
