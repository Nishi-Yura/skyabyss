# -*- coding: utf-8 -*-
"""外れの道 (2026-11-06 の声「道は奥まで完全につながっているように」「ダンジョンへも道つなげて」。案の頁「大陸の外れ」の m_roads = a)。
いまの大陸の高さの図 (height.bin) の上で、傾きと水を避ける道筋を探し (A*)、環の道と、外れの集落・奥のダンジョン・今のダンジョンへの枝道の
制御点を tools/lib/frontier_roads.py に書く。continent_layout の c1 が読み込んで道に足す (gen_continent が曲線にして layout.json へ)。

    python tools/gen/world/plan_frontier_roads.py            道を探して tools/lib/frontier_roads.py を書く (と確かめの絵)

置き場 (SPOTS) は 2026-11-06 に、どの場所からも遠い・平ら・岸から離れた所を土地ごとに選んだもの (集落 8・奥のダンジョン 4)。
道の端は、今の道の上の一番近い点 (つなぎ) か、行き先の手前 (部品の範囲 + 少し) で止める。
"""
import gzip
import heapq
import json
import math
import os
import struct
import sys

import numpy as np
from scipy import ndimage

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..', '..'))
D = os.path.join(ROOT, 'src', 'main', 'resources', 'data', 'skyabyss', 'continent')
OUT = os.path.join(ROOT, 'tools', 'lib', 'frontier_roads.py')
SEA = 62

# 外れの置き場: (種類, 土地, 鍵, x, z)。集落 = 外れの集落 (宿・店・住人)。奥 = 奥のダンジョン (形はラフから選ぶ)
SPOTS = [
    ('hamlet', 'whale', 'w_ne', 724, -1112), ('hamlet', 'whale', 'w_nw', -528, -1204),
    ('hamlet', 'tide', 't_e', 1300, -652), ('hamlet', 'tide', 't_se', 1300, 848),
    ('hamlet', 'ember', 'e_se', 920, 1020), ('hamlet', 'ember', 'e_n', 584, 576),
    ('hamlet', 'storm', 's_sw', -1412, 920), ('hamlet', 'storm', 's_nw', -936, -1060),
    ('deep', 'whale', 'fort', 1280, -1176), ('deep', 'tide', 'temple', 1720, 564),
    ('deep', 'storm', 'ice', -1584, -1288), ('deep', 'ember', 'lava', 1148, 1124),
]

# 道: 通る点の並び。'@x,z' = 今の道の上の、その点に一番近い所 (つなぎ)。'#鍵' = 置き場。'!種類' = 今の見どころ (pois.json) の手前
# 環の道は四つの弧 (北東・南東・南西・北西)。枝は行き先で止める
LINKS = [
    # 北東の弧: 岩鯨の跡地の道 → 北東の集落 → 東の集落 → 潮殻の跡地の道
    ('ring_ne', ['@150,-960', '#w_ne', '#t_e', '@1420,-180']),
    # 南東の弧: 汐守の南の脇道 → 南東の集落 → 余燼の南東の集落 → 余燼の跡地の道
    ('ring_se', ['@1432,304', '#t_se', '#e_se', '@380,1250']),
    # 南西の弧: 灰炉の西の脇道 → 南西の集落 → 風待の南の脇道
    ('ring_sw', ['@-428,1120', '#s_sw', '@-1144,132']),
    # 北西の弧: 雷翼の跡地の道 → 北西の集落 (嵐) → 北西の集落 (岩鯨) → 鯨見の西の脇道
    ('ring_nw', ['@-1280,-420', '#s_nw', '#w_nw', '@-256,-700']),
    # 内海の南の岸の道: 都の東の船着き場の道 → 内海の南の集落 → 余燼の南東の集落
    ('shore_s', ['@240,121', '#e_n', '#e_se']),
    # 奥のダンジョンへの枝
    ('deep_fort', ['@1000,-1150', '#fort']),
    ('deep_temple', ['@1300,848', '#temple']),
    ('deep_lava', ['@920,1020', '#lava']),
    ('deep_ice', ['@-936,-1060', '#ice']),
    # 今の地下のダンジョンへの枝
    ('dun_bone', ['@60,-798', '!dungeon_bone_pit']),
    ('dun_tide', ['@1426,-112', '!dungeon_tide_cave']),
    ('dun_storm', ['@-1244,-282', '!dungeon_storm_hollow']),
    ('dun_molten', ['@240,1220', '!dungeon_molten_mine']),
]

# 水を渡る枝は、岸で切って渡し舟にする
FERRY_LINKS = {'deep_ice'}

# 行き先の手前で止める距離 (部品の半分 + 道の半幅 + 少し)
STOP = {'hamlet': 30, 'deep': 24, 'poi': 18}


def rb(name):
    raw = gzip.decompress(open(os.path.join(D, name), 'rb').read())
    size, scale, x0, z0 = struct.unpack('>iiii', raw[4:20])
    return np.frombuffer(raw[20:], dtype=np.uint8).reshape(size, size).astype(float), size, scale, x0, z0


def main():
    h, size, scale, x0, z0 = rb('height.bin')
    lay = json.load(open(os.path.join(D, 'layout.json'), encoding='utf-8'))
    pois = json.load(open(os.path.join(D, 'pois.json'), encoding='utf-8'))
    lairs = json.load(open(os.path.join(D, 'lairs.json'), encoding='utf-8'))
    # 2 升 (8 ブロック) の粗い図で探す
    k = 2
    H = h.reshape(size // k, k, size // k, k).mean(axis=(1, 3))
    n = H.shape[0]
    cs = scale * k
    land = H > SEA
    gz, gx = np.gradient(H)
    slope = np.hypot(gx, gz) / cs
    sea_dist = ndimage.distance_transform_edt(land) * cs
    # 歩きにくさ: 傾き (二乗)、水 (川は渡れるが高い、海は避ける)、岸の際 (崖を避ける)、町・都・巨神の居場所・見どころの中
    cost = 1.0 + 30.0 * slope ** 2
    water = ~land
    big_water = ndimage.binary_opening(water, iterations=2)
    cost = np.where(water, 40.0, cost)
    cost = np.where(big_water, 400.0, cost)
    cost = cost + np.where(land & (sea_dist < 24), 3.0, 0.0)
    ii, jj = np.indices((n, n))

    def cell(x, z):
        return int((z - z0) / cs), int((x - x0) / cs)

    def xz(i, j):
        return int(x0 + j * cs + cs // 2), int(z0 + i * cs + cs // 2)

    block = np.zeros((n, n), dtype=bool)

    def keep_out(x, z, r):
        ci, cj = cell(x, z)
        block[np.hypot(ii - ci, jj - cj) * cs < r] = True

    keep_out(*lay['capital'], 130)
    for v in lay['towns'].values():
        keep_out(*v, 70)
    for key, v in lairs.items():
        if isinstance(v, dict) and 'x' in v:
            keep_out(v['x'], v['z'], 90)
    for p in pois:
        if p['kind'] not in ('gone', 'signpost'):
            keep_out(p['x'], p['z'], 22)
    cost = np.where(block, cost + 60.0, cost)

    # つなぎ先は今の道だけ (layout.json に外れの道がもう入っていれば、その分を除く。外れの道どうしは探した順につなぐ)
    base = lay['roads']
    try:
        sys.path.insert(0, os.path.join(ROOT, 'tools', 'lib'))
        import frontier_roads as _fr
        import continent_layout as _cl
        n_base = len(_cl.LAYOUTS['c1']['roads']) - len(_fr.ROADS)
        if len(base) > n_base:
            base = base[:n_base]
    except Exception:
        pass
    roads = [np.array(r, dtype=float) for r in base]

    def on_road(x, z):
        best = None
        for r in roads:
            d = np.hypot(r[:, 0] - x, r[:, 1] - z)
            m = int(np.argmin(d))
            if best is None or d[m] < best[0]:
                best = (d[m], (int(r[m, 0]), int(r[m, 1])))
        return best[1]

    spots = {s[2]: s for s in SPOTS}
    poi_at = {p['kind']: (p['x'], p['z']) for p in pois}

    def astar(a, b):
        si, sj = cell(*a)
        ti, tj = cell(*b)
        INF = 1e18
        g = np.full((n, n), INF)
        g[si, sj] = 0
        came = {}
        pq = [(0.0, si, sj)]
        nb = [(-1, -1), (-1, 0), (-1, 1), (0, -1), (0, 1), (1, -1), (1, 0), (1, 1)]
        while pq:
            f, i, j = heapq.heappop(pq)
            if (i, j) == (ti, tj):
                break
            gi = g[i, j]
            for di, dj in nb:
                a2, b2 = i + di, j + dj
                if not (0 <= a2 < n and 0 <= b2 < n):
                    continue
                step = math.hypot(di, dj) * (cost[i, j] + cost[a2, b2]) * 0.5 + abs(H[a2, b2] - H[i, j]) * 0.6
                ng = gi + step
                if ng < g[a2, b2]:
                    g[a2, b2] = ng
                    came[(a2, b2)] = (i, j)
                    heapq.heappush(pq, (ng + math.hypot(a2 - ti, b2 - tj), a2, b2))
        path = [(ti, tj)]
        while path[-1] != (si, sj):
            path.append(came[path[-1]])
        path.reverse()
        return [xz(i, j) for i, j in path]

    def rdp(pts, eps):
        if len(pts) < 3:
            return pts
        a, b = np.array(pts[0], float), np.array(pts[-1], float)
        ab = b - a
        L = np.linalg.norm(ab) + 1e-9
        dmax, idx = 0, 0
        for k2 in range(1, len(pts) - 1):
            p = np.array(pts[k2], float)
            d = abs(np.cross(ab, p - a)) / L
            if d > dmax:
                dmax, idx = d, k2
        if dmax > eps:
            return rdp(pts[:idx + 1], eps)[:-1] + rdp(pts[idx:], eps)
        return [pts[0], pts[-1]]

    def resolve(tok, toward):
        if tok.startswith('@'):
            x, z = map(int, tok[1:].split(','))
            return on_road(x, z), None
        if tok.startswith('#'):
            s = spots[tok[1:]]
            return (s[3], s[4]), STOP['hamlet' if s[0] == 'hamlet' else 'deep']
        x, z = poi_at[tok[1:]]
        return (x, z), STOP['poi']

    out = []
    ferries = []
    for name, toks in LINKS:
        pts = []
        for idx in range(len(toks) - 1):
            a, _ = resolve(toks[idx], None)
            b, stop = resolve(toks[idx + 1], None)
            seg = astar(a, b)
            seg[0] = a
            seg[-1] = b
            pts = pts + (seg if not pts else seg[1:])
        # 終わりが行き先 (つなぎでない) なら、手前で止める
        last = toks[-1]
        if not last.startswith('@'):
            end, stop = resolve(last, None)
            while len(pts) > 2 and math.hypot(pts[-2][0] - end[0], pts[-2][1] - end[1]) < stop:
                pts.pop()
            ax, az = pts[-2]
            d = math.hypot(ax - end[0], az - end[1])
            t = (d - stop) / d if d > stop else 0
            pts[-1] = (round(ax + (end[0] - ax) * t), round(az + (end[1] - az) * t))
        if name in FERRY_LINKS:
            def wet_at(p):
                return h[min(size - 1, max(0, int((p[1] - z0) / scale))), min(size - 1, max(0, int((p[0] - x0) / scale)))] <= SEA
            a_i = next(i2 for i2, p in enumerate(pts) if wet_at(p))
            b_i = max(i2 for i2, p in enumerate(pts) if wet_at(p)) + 1
            shore_a, shore_b = pts[max(0, a_i - 3)], pts[min(len(pts) - 1, b_i + 3)]
            ferries.append([[int(shore_a[0]), int(shore_a[1])], [int(shore_b[0]), int(shore_b[1])]])
            island = pts[min(len(pts) - 1, b_i + 3):]
            pts = pts[:max(1, a_i - 3) + 1]
            if len(island) >= 2:
                isl = rdp([tuple(p) for p in island], 10)
                out.append((name + '_isle', [[int(p[0]), int(p[1])] for p in isl]))
            print(f'{name}: ferry {ferries[-1]}')
        ctrl = rdp([tuple(p) for p in pts], 10)
        # 曲線にする間 (24) より細かい制御点は間引く (端は残す)
        thin = [ctrl[0]]
        for p in ctrl[1:-1]:
            if math.hypot(p[0] - thin[-1][0], p[1] - thin[-1][1]) >= 40:
                thin.append(p)
        thin.append(ctrl[-1])
        length = sum(math.hypot(b[0] - a[0], b[1] - a[1]) for a, b in zip(pts, pts[1:]))
        wet = sum(1 for p in pts if h[min(size - 1, max(0, int((p[1] - z0) / scale))), min(size - 1, max(0, int((p[0] - x0) / scale)))] <= SEA)
        print(f'{name:12s} {len(thin):3d} points, {int(length):5d} blocks, {wet} wet samples')
        out.append((name, [[int(p[0]), int(p[1])] for p in thin]))
        roads.append(np.array(pts, dtype=float))

    with open(OUT, 'w', encoding='utf-8', newline='\n') as f:
        f.write('# -*- coding: utf-8 -*-\n')
        f.write('# tools/gen/world/plan_frontier_roads.py が書く (手で直さない)。外れの道の制御点と外れの置き場\n')
        f.write('SPOTS = %r\n' % (SPOTS,))
        f.write('ROADS = [\n')
        for name, pts in out:
            f.write('    # %s\n    %r,\n' % (name, pts))
        f.write(']\n')
        f.write('# 渡し舟 (本土の岸 → 島の岸)\n')
        f.write('FERRIES = %r\n' % (ferries,))
    print('wrote', OUT)
    if len(sys.argv) > 1:
        preview(h, size, scale, x0, z0, lay, out, sys.argv[1])


def preview(h, size, scale, x0, z0, lay, out, path):
    import matplotlib
    matplotlib.use('Agg')
    import matplotlib.pyplot as plt
    img = np.where((h > SEA)[..., None], np.dstack([0.55 + h / 600] * 3) * np.array([0.9, 1.0, 0.8]), np.array([0.2, 0.33, 0.48]))
    fig, ax = plt.subplots(figsize=(10, 10), dpi=100)
    ax.imshow(np.clip(img, 0, 1), extent=[x0, x0 + size * scale, z0 + size * scale, z0])
    for r in lay['roads']:
        a = np.array(r)
        ax.plot(a[:, 0], a[:, 1], color='#f2e6c8', lw=2)
    for name, pts in out:
        a = np.array(pts)
        ax.plot(a[:, 0], a[:, 1], color='#d0402a', lw=2.4)
    for kind, region, key, x, z in SPOTS:
        ax.scatter([x], [z], marker='H' if kind == 'hamlet' else 'X', s=140, c='#ffb000' if kind == 'hamlet' else '#00ff99', edgecolors='black', zorder=4)
        ax.annotate(key, (x, z), textcoords='offset points', xytext=(6, 6), fontsize=8, color='white')
    ax.set_title('frontier roads (red) on the current roads (cream); H = hamlet, X = deep dungeon')
    plt.savefig(path, bbox_inches='tight')
    print('preview', path)


if __name__ == '__main__':
    main()
