# -*- coding: utf-8 -*-
"""つながりの確かめ（見せる前に必ず流す）: 設計ファイルの品を組み、
1. 弦以外の部品が一つの塊につながっているか（離れた部品を名前で出す。浮かせる意図の部品はそうと分かる名前にする）
2. 弦の両端が弓の部品の中に入っているか、弦がまっすぐ張れているか（引きの段ごと）
3. 弦の飾りが同じ段の弦に触れているか
を調べる。  python -I joint_check.py <id> [id ...]   （作業フォルダで。出力ファイルは消す）
部品は z 軸まわりにだけ回る（look5 の節）。重なりは xy の回した四角の分離軸と z の区間で見る（0.05 までの隙間は接しているとみなす）"""
import importlib
import json
import math
import os
import sys
import tempfile
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
MODS = [importlib.import_module(f.stem) for f in sorted(HERE.glob('*_bold.py'))]
EPS = 0.05
FLOAT_OK = ('spark', 'mote', 'note', 'drop_', 'float_', 'drift_', 'spray_', 'smoke_', 'ember_a', 'ember_b', 'sat_', 'wave_')   # 浮かせる意図の部品（名前で分かるようにする）


def corners(c):
    (x0, y0, z0), (x1, y1, z1) = c['from'], c['to']
    a = math.radians(c['rotation'][2])
    ox, oy = c['origin'][0], c['origin'][1]
    pts = []
    for x, y in ((x0, y0), (x1, y0), (x1, y1), (x0, y1)):
        dx, dy = x - ox, y - oy
        pts.append((ox + dx * math.cos(a) - dy * math.sin(a), oy + dx * math.sin(a) + dy * math.cos(a)))
    return pts, (min(z0, z1), max(z0, z1))


def overlap(a, b):
    pa, za = a
    pb, zb = b
    if za[1] < zb[0] - EPS or zb[1] < za[0] - EPS:
        return False
    for poly in (pa, pb):
        for i in range(4):
            ex, ey = poly[(i + 1) % 4][0] - poly[i][0], poly[(i + 1) % 4][1] - poly[i][1]
            nx, ny = -ey, ex
            L = math.hypot(nx, ny) or 1
            nx, ny = nx / L, ny / L
            sa = [p[0] * nx + p[1] * ny for p in pa]
            sb = [p[0] * nx + p[1] * ny for p in pb]
            if max(sa) < min(sb) - EPS or max(sb) < min(sa) - EPS:
                return False
    return True


def inside(pt, shape, pad=0.3):
    poly, (z0, z1) = shape
    if not (z0 - pad <= 0 <= z1 + pad):
        return False
    x, y = pt
    for i in range(4):
        (ax, ay), (bx, by) = poly[i], poly[(i + 1) % 4]
        ex, ey = bx - ax, by - ay
        L = math.hypot(ex, ey) or 1
        if (ex * (y - ay) - ey * (x - ax)) / L < -pad:   # 外向き（反時計回りの左が内）
            return False
    return True


def ends(c):
    """弦の部品の両端（長い向きの両端の真ん中）"""
    poly, _ = corners(c)
    (x0, y0, _), (x1, y1, _) = c['from'], c['to']
    if abs(x1 - x0) >= abs(y1 - y0):
        return ((poly[0][0] + poly[3][0]) / 2, (poly[0][1] + poly[3][1]) / 2), ((poly[1][0] + poly[2][0]) / 2, (poly[1][1] + poly[2][1]) / 2)
    return ((poly[0][0] + poly[1][0]) / 2, (poly[0][1] + poly[1][1]) / 2), ((poly[2][0] + poly[3][0]) / 2, (poly[2][1] + poly[3][1]) / 2)


def check(i):
    fn = next((getattr(md, i) for md in MODS if hasattr(md, i)), None)
    if fn is None:
        return None
    with tempfile.TemporaryDirectory() as tmp:
        fn().build(tmp)
        cubes = json.load(open(os.path.join(tmp, f'{i}.json'), encoding='utf-8'))
    # 板の絵の poly は ccw に（回していないので from→to の順で ccw）
    body = [c for c in cubes if not c['name'].startswith('string_')]
    strs = [c for c in cubes if c['name'].startswith('string_')]
    sh = {c['name']: corners(c) for c in cubes}
    probs = []
    # 1. 塊
    n = len(body)
    adj = {k: set() for k in range(n)}
    for a in range(n):
        for b in range(a + 1, n):
            if overlap(sh[body[a]['name']], sh[body[b]['name']]):
                adj[a].add(b)
                adj[b].add(a)
    seen, comps = set(), []
    for s in range(n):
        if s in seen:
            continue
        st, comp = [s], []
        seen.add(s)
        while st:
            u = st.pop()
            comp.append(u)
            for v in adj[u]:
                if v not in seen:
                    seen.add(v)
                    st.append(v)
        comps.append(comp)
    # 本体は「浮かせない部品を含む塊」のうち一番大きいもの（浮く破片どうしが力の糸でつながって大きな塊になっても本体と取り違えない）
    def has_body(comp):
        return any(not any(f in body[k]['name'] for f in FLOAT_OK) for k in comp)
    comps.sort(key=lambda c: (has_body(c), len(c)), reverse=True)
    for comp in comps[1:]:
        names = [body[k]['name'] for k in comp]
        if all(any(f in nm for f in FLOAT_OK) for nm in names):
            continue
        probs.append('離れた部品: ' + ', '.join(names))
    # 2. 弦の端
    keys = sorted({c['name'].split('_')[1] for c in strs})
    def is_string(c):
        b = c['name'].split('_')
        return len(b) == 2 or (len(b) == 3 and b[2] in ('u', 'd'))
    for key in keys:
        parts = [c for c in strs if c['name'].split('_')[1] == key and is_string(c)]
        for c in parts:
            for e in ends(c):
                if abs(e[1] - 15) < 2:       # 引いた弦の真ん中（矢をつがえる所）
                    continue
                if not any(inside(e, sh[b['name']]) for b in body):
                    probs.append(f'弦の端が弓に付いていない: {c["name"]} ({e[0]:.2f}, {e[1]:.2f})')
        if key == '0' and parts:
            c = parts[0]
            if abs(c['rotation'][2]) > 0.01:
                probs.append('引いていない弦が傾いている')
    # 3. 弦の飾り・二本目の弦: 同じ段の弦の部品どうしのつながりの塊ごとに、本の弦か弓の体に触れていること
    for key in keys:
        grp = [c for c in strs if c['name'].split('_')[1] == key]
        m2 = len(grp)
        adj2 = {k: set() for k in range(m2)}
        for a in range(m2):
            for b in range(a + 1, m2):
                if overlap(sh[grp[a]['name']], sh[grp[b]['name']]):
                    adj2[a].add(b)
                    adj2[b].add(a)
        seen2 = set()
        for s0 in range(m2):
            if s0 in seen2:
                continue
            st, comp = [s0], []
            seen2.add(s0)
            while st:
                u = st.pop()
                comp.append(u)
                for v in adj2[u]:
                    if v not in seen2:
                        seen2.add(v)
                        st.append(v)
            if any(is_string(grp[k]) for k in comp):
                continue
            if any(overlap(sh[grp[k]['name']], sh[b['name']]) for k in comp for b in body):
                continue
            names = [grp[k]['name'] for k in comp]
            if all(any(f in nm for f in FLOAT_OK) for nm in names):
                continue
            probs.append('弦の飾りが弦から離れている: ' + ', '.join(names))
    return probs


if __name__ == '__main__':
    sys.stdout.reconfigure(encoding='utf-8')
    bad = 0
    for i in sys.argv[1:]:
        p = check(i)
        if p is None:
            print(f'{i}: 設計ファイルにない（調べていない）')
            continue
        print(f'{i}: ' + ('OK' if not p else f'{len(p)} 件'))
        for x in p:
            print('   ', x)
        bad += bool(p)
    sys.exit(1 if bad else 0)
