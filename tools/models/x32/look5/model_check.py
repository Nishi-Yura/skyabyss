# -*- coding: utf-8 -*-
"""入っている模型 (tools/models/x32/<id>.bbmodel) そのものの形の確かめ。joint_check.py は設計ファイルを組み直して見るので、
Blockbench で手を入れたあと・大きさを変えたあとの模型はこちらで見る。

1. 離れ: ほかのどの部品にも触れていない部品 (浮かせる意図の名前 float_ などと弦は除く)
2. 面の重なり: 同じ向きの箱どうしで、同じ平面に載って重なっている面 (ちらつく)
3. 突き抜け: 柄 (shaft・haft・rod・stalk・pillar・grip) の先が、頭の部品を抜けて上へ少しだけ飛び出している

  python -I tools/models/x32/look5/model_check.py [id ...]     (省くと manifest の全部)
箱は回転つきの箱として、メッシュは面ごとの小さな箱として比べる (粗い見立て。出たものは絵で確かめる)"""
import json
import math
import sys
from pathlib import Path

import numpy as np

ROOT = Path(__file__).resolve().parents[4]
X32 = ROOT / 'tools/models/x32'
FLOAT_OK = ('spark', 'mote', 'note', 'drop_', 'float_', 'drift_', 'spray_', 'smoke_', 'ember_a', 'ember_b', 'sat_', 'wave_', 'string', 'shard', 'orb', 'star', 'halo', 'rune', 'chip')
SHAFT = ('shaft', 'haft', 'rod', 'stalk', 'pillar', 'grip')
EPS = 0.08


def rot(r):
    rx, ry, rz = (math.radians(v) for v in r)
    cx, sx, cy, sy, cz, sz = math.cos(rx), math.sin(rx), math.cos(ry), math.sin(ry), math.cos(rz), math.sin(rz)
    Rx = np.array([[1, 0, 0], [0, cx, -sx], [0, sx, cx]]); Ry = np.array([[cy, 0, sy], [0, 1, 0], [-sy, 0, cy]]); Rz = np.array([[cz, -sz, 0], [sz, cz, 0], [0, 0, 1]])
    return Rz @ Ry @ Rx   # Blockbench (three.js の ZYX)


class Box:
    """回転つきの箱: 中心 c、軸 A (列)、半分の大きさ h"""
    def __init__(self, lo, hi, origin=(0, 0, 0), r=(0, 0, 0), name=''):
        lo, hi, o = np.array(lo, float), np.array(hi, float), np.array(origin, float)
        self.A = rot(r); self.h = (hi - lo) / 2; self.c = o + self.A @ ((lo + hi) / 2 - o); self.name = name
        self.plain = not any(r)
        self.lo, self.hi = lo, hi

    def contains(self, p, grow=0.0):
        q = self.A.T @ (np.array(p, float) - self.c)
        return bool((np.abs(q) <= self.h + grow).all())


def sat_gap(a, b):
    """二つの箱の離れ (負なら重なっている。分離軸の中でいちばん大きい隙間)"""
    axes = [a.A[:, i] for i in range(3)] + [b.A[:, i] for i in range(3)]
    for i in range(3):
        for j in range(3):
            v = np.cross(a.A[:, i], b.A[:, j])
            n = np.linalg.norm(v)
            if n > 1e-6:
                axes.append(v / n)
    d = b.c - a.c; best = -1e9
    for ax in axes:
        ra = sum(a.h[i] * abs(a.A[:, i] @ ax) for i in range(3)); rb = sum(b.h[i] * abs(b.A[:, i] @ ax) for i in range(3))
        best = max(best, abs(d @ ax) - ra - rb)
    return best


def parts(d):
    out = []
    for e in d['elements']:
        n = e['name']
        if e.get('type') == 'mesh':
            o = np.array(e['origin'], float); R = rot(e.get('rotation') or (0, 0, 0))
            V = {k: o + R @ np.array(v, float) for k, v in e['vertices'].items()}
            bs = []
            for f in e['faces'].values():
                P = np.array([V[k] for k in f['vertices']])
                bs.append(Box(P.min(0) - 0.02, P.max(0) + 0.02, name=n))
            out.append((n, bs, True))
        else:
            out.append((n, [Box(e['from'], e['to'], e['origin'], e.get('rotation') or (0, 0, 0), n)], False))
    return out


def check(i):
    d = json.loads((X32 / f'{i}.bbmodel').read_text(encoding='utf-8'))
    P = parts(d); notes = []
    N = len(P)
    # 1. 離れ
    touch = [False] * N
    gaps = [1e9] * N
    for a in range(N):
        for b in range(a + 1, N):
            g = min(sat_gap(x, y) for x in P[a][1] for y in P[b][1])
            gaps[a] = min(gaps[a], g); gaps[b] = min(gaps[b], g)
            if g <= EPS:
                touch[a] = touch[b] = True
    for k in range(N):
        n = P[k][0]
        if not touch[k] and not any(w in n for w in FLOAT_OK) and N > 1:
            notes.append(f'離れ: {n} (いちばん近い部品まで {gaps[k]:.2f})')
    # 2. 面の重なり (回っていない箱どうし)
    plain = [(n, bs[0]) for n, bs, mesh in P if not mesh and bs[0].plain]
    for a in range(len(plain)):
        for b in range(a + 1, len(plain)):
            (na, A), (nb, B) = plain[a], plain[b]
            for ax in range(3):
                o1, o2 = [k for k in range(3) if k != ax]
                ov = min(A.hi[o1], B.hi[o1]) - max(A.lo[o1], B.lo[o1]), min(A.hi[o2], B.hi[o2]) - max(A.lo[o2], B.lo[o2])
                if ov[0] <= 0.3 or ov[1] <= 0.3:
                    continue
                for side in ('lo', 'hi'):
                    if abs(getattr(A, side)[ax] - getattr(B, side)[ax]) < 0.012:
                        notes.append(f'面の重なり: {na} と {nb} の {"xyz"[ax]}{"-" if side == "lo" else "+"} 面 ({ov[0]:.1f} x {ov[1]:.1f})')
    # 3. 柄の先の突き抜け
    for n, bs, mesh in P:
        if mesh or not n.startswith(SHAFT) or not bs[0].plain:
            continue
        S = bs[0]; cx, cz = (S.lo[0] + S.hi[0]) / 2, (S.lo[2] + S.hi[2]) / 2; top = S.hi[1]
        above = any(b.contains((cx, top + 0.3, cz)) for m, bb, _ in P if m != n for b in bb)
        if above:
            continue
        for m, bb, _ in P:
            if m == n or m.startswith(SHAFT):
                continue
            for b in bb:
                for dlt in (0.4, 0.8, 1.2, 1.6, 2.0, 2.5, 3.0):
                    if b.contains((cx, top - dlt, cz)) and not b.contains((cx, top - 0.05, cz)):
                        notes.append(f'突き抜け: {n} の先が {m} の上へ {dlt:.1f} ほど出ている')
                        break
                else:
                    continue
                break
    return sorted(set(notes))


if __name__ == '__main__':
    sys.stdout.reconfigure(encoding='utf-8')
    man = json.loads((X32 / 'manifest.json').read_text(encoding='utf-8'))
    ids = sys.argv[1:] or sorted(man)
    bad = 0
    for i in ids:
        ns = check(i)
        if ns:
            bad += 1
            print(f'{i}: {len(ns)} 件')
            for s in ns[:8]:
                print('   ', s)
            if len(ns) > 8:
                print(f'    ... ほか {len(ns) - 8} 件')
    print(f'{len(ids)} 品を調べ、{bad} 品に気づきあり')
