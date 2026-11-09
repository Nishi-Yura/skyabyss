# -*- coding: utf-8 -*-
"""入れたメッシュの「凹んだ多角形の扇」を耳切りに分け直す (裏面の透けの直し。issue #16・#17)。

    python -I tools/audit/retri_concave.py               # assets/skyabyss/lowpoly/**.json を全部
    python -I tools/audit/retri_concave.py bell_hermit   # 名前の一部で絞る
    ... --dry                                            # 書き換えずに数だけ

わけ: 生成器の面 (lpkit.Face など) は多角形を一つ目の頂点からの扇で三角形にしていた。曲がった帯の切り口・裂けた裾の
蓋のような凹んだ多角形だと、扇の三角形が多角形の外へはみ出し、裏返った面が外から見える (mesh_audit の see)。
水密の蓋 (mesh_close の重心の扇) も同じ。生成器の側は直した (lpkit.Face.tris と mesh_close.triangulate_loop が耳切り)。
ここは、もう入っているメッシュを作り直さずに直す道具:
  面ごとに頂点を持つ (隣の面と頂点を共有しない) ので、頂点でつながる三角形の塊 = 元の一つの多角形。
  塊がすべて一つの頂点を共有する鎖 (v0, a1, a2) (v0, a2, a3) ... なら扇。輪 [v0, a1, ..., ak] (開いた鎖) か、
  閉じた鎖なら頂点 v0 を除いた輪 (重心の扇の蓋)。輪が凹んでいれば耳切りで分け直す (頂点と UV はそのまま)。
"""
from __future__ import annotations

import collections
import glob
import json
import os
import sys

import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import mesh_audit as M  # noqa: E402

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', '..')
LOWPOLY = os.path.join(ROOT, 'src', 'main', 'resources', 'assets', 'skyabyss', 'lowpoly')


def newell(P):
    n = np.zeros(3)
    for i in range(len(P)):
        a, b = P[i], P[(i + 1) % len(P)]
        n += np.array([(a[1] - b[1]) * (a[2] + b[2]), (a[2] - b[2]) * (a[0] + b[0]), (a[0] - b[0]) * (a[1] + b[1])])
    return n


def plane2d(P, nm):
    h = np.array([0, 1, 0.0]) if abs(nm[1]) < 0.9 else np.array([1, 0, 0.0])
    u = np.cross(h, nm)
    u /= np.linalg.norm(u)
    w = np.cross(nm, u)
    return [(float(p @ u), float(p @ w)) for p in P]


def cr(o, a, b):
    return (a[0] - o[0]) * (b[1] - o[1]) - (a[1] - o[1]) * (b[0] - o[0])


def ear_clip(Q):
    idx = list(range(len(Q)))
    tris = []
    guard = 0
    while len(idx) > 3 and guard < 10 * len(Q) ** 2:
        guard += 1
        m = len(idx)
        for k in range(m):
            i0, i1, i2 = idx[(k - 1) % m], idx[k], idx[(k + 1) % m]
            if cr(Q[i0], Q[i1], Q[i2]) <= 1e-9:
                continue
            if any(cr(Q[i0], Q[i1], Q[j]) >= -1e-9 and cr(Q[i1], Q[i2], Q[j]) >= -1e-9 and cr(Q[i2], Q[i0], Q[j]) >= -1e-9
                   for j in idx if j not in (i0, i1, i2)):
                continue
            tris.append((i0, i1, i2))
            idx.pop(k)
            break
        else:
            return None
    tris.append(tuple(idx))
    return tris


def fan_loop(tris):
    """三角形の塊が一つの頂点の扇なら (輪の頂点の並び, 閉じた扇か) を返す"""
    common = set(tris[0])
    for t in tris[1:]:
        common &= set(t)
    if len(common) != 1:
        return None
    v0 = common.pop()
    nxt = {}
    for t in tris:
        i = t.index(v0)
        a, b = t[(i + 1) % 3], t[(i + 2) % 3]
        if a in nxt:
            return None
        nxt[a] = b
    starts = set(nxt) - set(nxt.values())
    if len(starts) == 0:  # 閉じた鎖 (重心の扇): 輪は v0 を除く
        a = next(iter(nxt))
        loop = [a]
        while nxt[loop[-1]] != a:
            loop.append(nxt[loop[-1]])
            if len(loop) > len(nxt):
                return None
        return (loop, True, v0) if len(loop) == len(nxt) else None
    if len(starts) != 1:
        return None
    a = starts.pop()
    loop = [v0, a]
    while loop[-1] in nxt:
        loop.append(nxt[loop[-1]])
        if len(loop) > len(nxt) + 2:
            return None
    return (loop, False, v0) if len(loop) == len(nxt) + 2 else None


def rim(V, tris):
    """三角形の塊の外周の有向辺 (一度だけ使われる辺。位置を 0.001 で丸めて比べる)"""
    key = lambda i: tuple(np.round(V[i], 3))
    cnt = collections.Counter()
    for t in tris:
        for a, b in ((t[0], t[1]), (t[1], t[2]), (t[2], t[0])):
            ka, kb = key(a), key(b)
            if ka == kb:
                continue
            cnt[(ka, kb)] += 1
    out = set()
    for (a, b), k in cnt.items():
        if cnt.get((b, a), 0) == 0:
            out.add((a, b))
    return out


def fix_bone(V, T):
    """戻り値 (新しい三角形の並び, 分け直した多角形の数)"""
    if len(T) == 0:
        return T, 0
    parent = {}

    def find(x):
        while parent.setdefault(x, x) != x:
            parent[x] = parent[parent[x]]
            x = parent[x]
        return x

    for t in T:
        for v in t[1:]:
            ra, rb = find(t[0]), find(v)
            if ra != rb:
                parent[ra] = rb
    groups = collections.defaultdict(list)
    for ti, t in enumerate(T):
        groups[find(t[0])].append(ti)
    out = []
    fixed = 0
    for g in groups.values():
        tris = [tuple(T[i]) for i in g]
        if len(tris) >= 2:
            r = fan_loop(tris)
            if r:
                loop, closed, v0 = r
                P = V[loop]
                nm = newell(P)
                ln = np.linalg.norm(nm)
                ext = float(np.linalg.norm(P - P.mean(0), axis=1).max())
                if ln > 1e-9 and ext > 1e-6:
                    nm = nm / ln
                    dev = float(np.abs((P - P.mean(0)) @ nm).max())
                    Q = plane2d(P, nm)
                    k = len(Q)
                    concave = any(cr(Q[i - 1], Q[i], Q[(i + 1) % k]) < -1e-6 for i in range(k))
                    if concave and dev <= 0.25 * ext:
                        et = ear_clip(Q)
                        if et:
                            new = [[loop[a], loop[b], loop[c]] for a, b, c in et]
                            # 外周の辺 (位置で溶接) が元と同じときだけ替える (穴を開けない)
                            if rim(V, new) == rim(V, tris):
                                out += new
                                fixed += 1
                                continue
        out += [list(t) for t in tris]
    return out, fixed


def fix_file(path, dry=False):
    with open(path, encoding='utf-8') as f:
        data = json.load(f)
    if 'bones' not in data:
        return 0
    total = 0
    for n, b in data['bones'].items():
        T = b.get('triangles') or []
        if not T:
            continue
        V = np.array(b['vertices'], float).reshape(-1, 3)
        T2, k = fix_bone(V, T)
        if k:
            # 監査の溶接で見て、開いた辺・向きの食い違い・つぶれが増えるなら、この骨は替えない
            a0 = M.analyze(V, np.array(T, int))
            a1 = M.analyze(V, np.array(T2, int))
            if any(a1[x] > a0[x] for x in ("open", "wind", "inv", "degen", "nonman")):
                continue
            total += k
            b['triangles'] = T2
            b['triangle_count'] = len(T2)
    if total and not dry:
        data['triangle_count'] = sum(len(b.get('triangles') or []) for b in data['bones'].values())
        with open(path, 'w', encoding='utf-8') as f:
            json.dump(data, f, separators=(',', ':'))
    return total


def main(argv):
    dry = '--dry' in argv
    flt = [a for a in argv if not a.startswith('--')]
    files = sorted(glob.glob(os.path.join(LOWPOLY, '**', '*.json'), recursive=True))
    if flt:
        files = [p for p in files if any(f in os.path.relpath(p, LOWPOLY).replace('\\', '/') for f in flt)]
    n_files = n_polys = 0
    for p in files:
        k = fix_file(p, dry)
        if k:
            n_files += 1
            n_polys += k
            print(f"{os.path.relpath(p, LOWPOLY)}: {k}")
    print(f"retri_concave: {n_polys} polygons in {n_files} meshes{' (dry)' if dry else ''}")


if __name__ == '__main__':
    main(sys.argv[1:])
