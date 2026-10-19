#!/usr/bin/env python3
"""低ポリのメッシュ JSON を水密にする (開いた辺を塞ぐ・向きをそろえる・付け根に襟を付ける)。

生成スクリプト (tools/rough/lowpoly/**) は「見える面だけ」「筒の端は親に埋まる前提で蓋なし」で面を書き出すので、
どの骨も縁が開いている。そのまま描くと関節が動いたとき・外れた面の所で中が見える。
このモジュールは書き出し済みのメッシュ JSON に対して次を行う。生成物の見た目 (外から見える面・UV・絵) は変えない。

  1. 開いた縁の輪を塞ぐ (平らな輪は耳切り、反った輪は重心の扇)。蓋の三角形は縁の隣の面の絵を一点で拾った平らな色
  2. 面の向きの食い違い (隣と裏表が逆) を直し、閉じた塊の体積が負なら全体をひっくり返す
  3. 付け根の襟 (collar): 子の骨の「親に最も近い開いた輪」(= 親へ埋まるはずの根元) を、親の体積の内側へ押し込んだ筒で延長し、
     アニメで曲がっても根元が親から抜けない・隙間が開かないようにする (--collar)。

使い方:
  python tools/mesh_close.py --all                # lowpoly/**.json を全部直す (直したものだけ書き換える)
  python tools/mesh_close.py <名前の一部>...      # 名前を絞る
  python tools/mesh_close.py --all --no-collar    # 襟は付けない
生成スクリプトの installer (install_lowpoly*.py ほか) は書き出しのあとに close_mesh_file() を呼ぶ。書き出し直すと開いた状態に戻るため。
"""
from __future__ import annotations

import collections
import glob
import json
import math
import os
import sys

import numpy as np
from scipy.spatial import cKDTree

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import mesh_audit as ma  # noqa: E402

MARK = "skyabyss_closed"


# ----------------------------------------------------------------- 輪の抽出

def boundary_loops(V, T):
    """溶接して開いた辺をたどった輪。戻り値: (輪のリスト [(頂点番号 (元の最初の番号) のリスト, 辺ごとの三角形番号のリスト)], welded ids)"""
    c = ma.weld_ids(V)
    first = {}
    for i, cid in enumerate(c):
        first.setdefault(cid, i)
    directed = {}
    cnt = collections.Counter()
    for ti, (a, b, d) in enumerate(c[T]):
        for p, q in ((a, b), (b, d), (d, a)):
            if p == q:
                continue
            directed[(p, q)] = ti
            cnt[(min(p, q), max(p, q))] += 1
    nxt = collections.defaultdict(list)
    for (p, q), ti in directed.items():
        if cnt[(min(p, q), max(p, q))] == 1:
            nxt[p].append((q, ti))
    used = set()
    loops = []
    for s in list(nxt):
        for (q0, t0) in list(nxt[s]):
            if (s, q0) in used:
                continue
            ids = [s]
            tis = [t0]
            used.add((s, q0))
            cur = q0
            while cur != s and len(ids) < 100000:
                ids.append(cur)
                cand = [(z, tz) for (z, tz) in nxt[cur] if (cur, z) not in used]
                if not cand:
                    break
                # 複数あるときは最初の辺に近い向きへ (図 8 の交点では素直に最初を取る)
                z, tz = cand[0]
                used.add((cur, z))
                tis.append(tz)
                cur = z
            if cur == s and len(ids) >= 3:
                loops.append(([first[i] for i in ids], tis))
    return loops, c


# ----------------------------------------------------------------- 輪の三角形分割

def newell(P):
    n = np.zeros(3)
    for i in range(len(P)):
        a, b = P[i], P[(i + 1) % len(P)]
        n += np.array([(a[1] - b[1]) * (a[2] + b[2]), (a[2] - b[2]) * (a[0] + b[0]), (a[0] - b[0]) * (a[1] + b[1])])
    return n


def ear_clip(P):
    """3D の平らな多角形 P (順に並んだ頂点) を三角形分割。戻り値は P の添字の三つ組 (向きは P の並びと同じ)。失敗したら None。"""
    n = len(P)
    if n == 3:
        return [(0, 1, 2)]
    nm = newell(P)
    ln = np.linalg.norm(nm)
    if ln < 1e-9:
        return None
    nm /= ln
    h = np.array([0, 1, 0.0]) if abs(nm[1]) < 0.9 else np.array([1, 0, 0.0])
    u = np.cross(h, nm)
    u /= np.linalg.norm(u)
    w = np.cross(nm, u)
    Q = np.array([[np.dot(p, u), np.dot(p, w)] for p in P])  # nm が手前向き → Q は反時計回り
    idx = list(range(n))
    tris = []
    guard = 0

    def cross(o, a, b):
        return (a[0] - o[0]) * (b[1] - o[1]) - (a[1] - o[1]) * (b[0] - o[0])

    while len(idx) > 3 and guard < 10 * n * n:
        guard += 1
        m = len(idx)
        found = False
        for k in range(m):
            i0, i1, i2 = idx[(k - 1) % m], idx[k], idx[(k + 1) % m]
            if cross(Q[i0], Q[i1], Q[i2]) <= 1e-9:
                continue
            bad = False
            for j in idx:
                if j in (i0, i1, i2):
                    continue
                if cross(Q[i0], Q[i1], Q[j]) >= -1e-9 and cross(Q[i1], Q[i2], Q[j]) >= -1e-9 and cross(Q[i2], Q[i0], Q[j]) >= -1e-9:
                    bad = True
                    break
            if bad:
                continue
            tris.append((i0, i1, i2))
            idx.pop(k)
            found = True
            break
        if not found:
            return None
    tris.append((idx[0], idx[1], idx[2]))
    return tris


def triangulate_loop(P, centroid):
    """輪 P の蓋。戻り値 (追加頂点 [(x,y,z)], 三角形 (P の添字 or n = 追加頂点))。三角形の辺の向きは P の並びに沿う。
    蓋は重心の扇で、頂点は輪の面から内側へ少し引っ込める (平らな蓋は、貼りついた隣の面と同じ平面に重なって z が競り、ちらつくため)。
    平らな輪は蓋の法線 (輪の向きから決まる外向き) の逆へ、反った輪は骨の中心の側へ。"""
    n = len(P)
    ctr = P.mean(0)
    nm = newell(P)
    ln = np.linalg.norm(nm)
    ext = float(np.linalg.norm(P - ctr, axis=1).max()) if n else 0.0
    if ln > 1e-9:
        nm = nm / ln
        dev = float(np.abs((P - ctr) @ nm).max())
        if dev <= 0.2 * max(ext, 1e-9):
            apex = ctr - nm * max(0.2, 0.2 * ext)
            return [tuple(apex)], [(k, (k + 1) % n, n) for k in range(n)]
    apex = ctr + (centroid - ctr) * 0.2
    return [tuple(apex)], [(k, (k + 1) % n, n) for k in range(n)]


# ----------------------------------------------------------------- 向きの修正

def fix_orientation(V, T):
    """隣と裏表が逆の面を直し、閉じた塊で体積が負のものを全体ひっくり返す。戻り値 (T, 直した三角形数)。"""
    c = ma.weld_ids(V)
    tri = [list(t) for t in T]
    n = len(tri)
    edge_tris = collections.defaultdict(list)
    for ti, t in enumerate(T):
        a, b, d = c[t]
        for p, q in ((a, b), (b, d), (d, a)):
            if p != q:
                edge_tris[(min(p, q), max(p, q))].append(ti)
    flipped = [False] * n
    seen = [False] * n
    fixed = 0
    comps = []
    for s in range(n):
        if seen[s]:
            continue
        seen[s] = True
        stack = [s]
        comp = [s]
        while stack:
            ti = stack.pop()
            a, b, d = c[T[ti]]
            if flipped[ti]:
                a, d = d, a
            for p, q in ((a, b), (b, d), (d, a)):
                if p == q:
                    continue
                nb = edge_tris[(min(p, q), max(p, q))]
                if len(nb) != 2:
                    continue
                tj = nb[0] if nb[1] == ti else nb[1]
                if seen[tj]:
                    continue
                x, y, z = c[T[tj]]
                # tj が (q→p) の向きに辺を持つなら向きは合っている
                has_qp = any((u, v) == (q, p) for u, v in ((x, y), (y, z), (z, x)))
                flipped[tj] = not has_qp
                if flipped[tj]:
                    fixed += 1
                seen[tj] = True
                stack.append(tj)
                comp.append(tj)
        comps.append(comp)
    out = np.array(T)
    for ti in range(n):
        if flipped[ti]:
            out[ti] = out[ti][[0, 2, 1]]
    # 閉じた塊の体積が負なら全部ひっくり返す。開いた塊でも体積の符号が負で、閉じたあとの判定に任せるのでここでは閉じた塊だけ
    open_vertices = set()
    for (p, q), lst in edge_tris.items():
        if len(lst) == 1:
            open_vertices.add(p)
            open_vertices.add(q)
    for comp in comps:
        cs = set()
        for ti in comp:
            cs.update(c[out[ti]])
        if cs & open_vertices:
            continue
        vol = 0.0
        for ti in comp:
            a, b, d = V[out[ti]]
            vol += np.dot(a, np.cross(b, d)) / 6.0
        if vol < -1e-6:
            for ti in comp:
                out[ti] = out[ti][[0, 2, 1]]
            fixed += len(comp)
    return out, fixed


# ----------------------------------------------------------------- 蓋

def _plane(P):
    """輪の Newell 法線 (単位)・重心・面積。"""
    nm = newell(P)
    ln = float(np.linalg.norm(nm))
    return (nm / ln if ln > 1e-12 else nm), P.mean(0), ln / 2.0


def _in_poly(pt2, poly2):
    x, y = pt2
    inside = False
    n = len(poly2)
    for i in range(n):
        x1, y1 = poly2[i]
        x2, y2 = poly2[(i + 1) % n]
        if (y1 > y) != (y2 > y) and x < (x2 - x1) * (y - y1) / (y2 - y1 + 1e-18) + x1:
            inside = not inside
    return inside


def nested_pairs(Ps):
    """帯でつなぐ輪の組 [(外, 内)]。厚みのある壁の縁 (兜・筒・殻の口・窓の穴) は、外の輪と内の輪の二つになる。
    辿った向きで法線が逆向きの平行な輪 (= 蓋にすると互いに背を向ける) で、内の輪の重心が外の輪の中に入るものを候補にし、
    平面のずれと大きさの差が小さい組から順に、一つの輪を一度だけ使って決める。"""
    info = [_plane(P) for P in Ps]
    cands = []
    for b, (nb, cb, ab) in enumerate(info):
        for a, (na, ca, aa) in enumerate(info):
            if a == b or aa < ab * 0.98 or aa < 1e-6 or (aa <= ab * 1.02 and a > b):
                continue
            if float(np.dot(na, nb)) > -0.85:
                continue
            ext = float(np.linalg.norm(Ps[a] - ca, axis=1).max())
            off = abs(float(np.dot(cb - ca, na)))
            if off > 0.3 * max(ext, 1e-9):
                continue
            h = np.array([0, 1, 0.0]) if abs(na[1]) < 0.9 else np.array([1, 0, 0.0])
            u = np.cross(h, na)
            u /= np.linalg.norm(u)
            w = np.cross(na, u)
            poly = [((p - ca) @ u, (p - ca) @ w) for p in Ps[a]]
            if not _in_poly(((cb - ca) @ u, (cb - ca) @ w), poly):
                continue
            cands.append((off + 0.3 * abs(math.sqrt(aa) - math.sqrt(ab)), a, b))
    cands.sort()
    used = set()
    out = []
    for _, a, b in cands:
        if a in used or b in used:
            continue
        used.add(a)
        used.add(b)
        out.append((a, b))
    return out


def annulus(PA, PB):
    """外の輪 PA (既存の辺の向きの並び) と内の輪 PB の間の帯。戻り値は三角形 [(区分, 外の番号, 内の番号...)]、頂点はそれぞれ ('A', i) / ('B', j)。
    蓋は既存の辺と逆向きに使うので、外は逆順 (RA)、内は元の順 (LB) の同じ回転の向きにして、ジッパーで進む。"""
    RA = PA[::-1]
    n, m = len(RA), len(PB)
    j0 = int(np.argmin(np.linalg.norm(PB - RA[0], axis=1)))
    B = np.roll(PB, -j0, axis=0)
    ia = ib = 0
    tris = []
    while ia < n or ib < m:
        a0, a1 = RA[ia % n], RA[(ia + 1) % n]
        b0, b1 = B[ib % m], B[(ib + 1) % m]
        ca = np.linalg.norm(a1 - b0) if ia < n else 1e18
        cb = np.linalg.norm(a0 - b1) if ib < m else 1e18
        if ia < n and (ib >= m or ca <= cb):
            tris.append((("A", ia % n), ("A", (ia + 1) % n), ("B", ib % m)))
            ia += 1
        else:
            tris.append((("A", ia % n), ("B", (ib + 1) % m), ("B", ib % m)))
            ib += 1
    return RA, B, tris


def close_bone(V, UV, T, centroid):
    """開いた縁を蓋で塞ぐ。入れ子の輪 (厚みのある壁の縁) は、内と外の間の帯でつなぐ。戻り値 (V, UV, T, 蓋の数)。"""
    loops, c = boundary_loops(V, T)
    if not loops:
        return V, UV, T, 0
    V = [list(p) for p in V]
    UV = [list(p) for p in UV]
    T = [list(t) for t in T]
    Va = np.array(V)
    Ps = [Va[ids] for ids, tis in loops]
    done = set()
    for a, b in nested_pairs(Ps):
        RA, B, tris = annulus(Ps[a], Ps[b])
        ta = loops[a][1][::-1]
        tb = loops[b][1]
        # 一周ぶんが三角形を作る向きが外れる (法線が外れた輪と逆) 場合は、帯にせず蓋に任せる
        n_ann = newell(RA)
        if float(np.dot(n_ann, newell(B))) <= 0:
            continue
        for tri in tris:
            kind, idx = tri[0]
            nbt = T[ta[min(idx, len(ta) - 1)]] if kind == "A" else T[tb[min(idx, len(tb) - 1)]]
            suv = [sum(UV[i][0] for i in nbt) / 3.0, sum(UV[i][1] for i in nbt) / 3.0]
            vi = []
            for kk, ii in tri:
                pos = RA[ii] if kk == "A" else B[ii]
                V.append([float(pos[0]), float(pos[1]), float(pos[2])])
                UV.append(suv)
                vi.append(len(V) - 1)
            T.append(vi)
        done.add(a)
        done.add(b)
    for li, (ids, tis) in enumerate(loops):
        if li in done:
            continue
        P = Va[ids]
        # 蓋は輪を逆向きにたどる向き (辺は既存の面と逆向きになる)
        Prev = P[::-1]
        ids_rev = ids[::-1]
        tis_rev = tis[::-1]
        extra, tris = triangulate_loop(Prev, np.asarray(centroid, float))
        # 蓋の頂点は輪の頂点の複製 (それぞれの三角形が自分の UV を持つ)
        for tri in tris:
            # 絵: 縁の隣の面の重心の一点 (どの頂点も同じ UV にして平らな色にする)
            k = tri[0] if tri[0] < len(ids_rev) else 0
            nb = T[tis_rev[min(k, len(tis_rev) - 1)]]
            suv = [sum(UV[i][0] for i in nb) / 3.0, sum(UV[i][1] for i in nb) / 3.0]
            vi = []
            for x in tri:
                pos = Prev[x] if x < len(Prev) else np.asarray(extra[0])
                V.append([float(pos[0]), float(pos[1]), float(pos[2])])
                UV.append(suv)
                vi.append(len(V) - 1)
            T.append(vi)
    return np.array(V), np.array(UV), np.array(T, int), len(loops)


# ----------------------------------------------------------------- 関節の玉

def icosphere(center, r, level=0):
    t = (1 + 5 ** 0.5) / 2
    V = [(-1, t, 0), (1, t, 0), (-1, -t, 0), (1, -t, 0), (0, -1, t), (0, 1, t), (0, -1, -t), (0, 1, -t), (t, 0, -1), (t, 0, 1), (-t, 0, -1), (-t, 0, 1)]
    F = [(0, 11, 5), (0, 5, 1), (0, 1, 7), (0, 7, 10), (0, 10, 11), (1, 5, 9), (5, 11, 4), (11, 10, 2), (10, 7, 6), (7, 1, 8),
         (3, 9, 4), (3, 4, 2), (3, 2, 6), (3, 6, 8), (3, 8, 9), (4, 9, 5), (2, 4, 11), (6, 2, 10), (8, 6, 7), (9, 8, 1)]
    V = [np.array(v, float) / np.linalg.norm(v) for v in V]
    for _ in range(level):
        cache = {}
        newF = []

        def mid(a, b):
            k = (min(a, b), max(a, b))
            if k not in cache:
                m = (V[a] + V[b]) / 2
                V.append(m / np.linalg.norm(m))
                cache[k] = len(V) - 1
            return cache[k]
        for a, b, c in F:
            ab, bc, ca = mid(a, b), mid(b, c), mid(c, a)
            newF += [(a, ab, ca), (b, bc, ab), (c, ca, bc), (ab, bc, ca)]
        F = newF
    return np.array(V) * r + np.asarray(center, float), F


def inside_any(P, refs):
    """点 P が、塞いだ参照メッシュ refs = [(V, T)] のどれかの体積の中か。"""
    m = np.zeros(len(P), bool)
    for V, T in refs:
        m |= ma.inside_mask(P, np.asarray(V, float), np.asarray(T, int))
    return m


def ref_samples(refs):
    return np.vstack([ma.surface_samples(Bone_like(V, T)) for V, T in refs])


def joint_ball(bones, name, refs, cchild, ref_uvs):
    """子の付け根が親 (と兄弟) に十分埋まっていない (付け根に近い 80 点の 25% 未満) とき、子と親の隙間・接面・浅い重なりにまたがる小さな玉を子の骨へ足す。
    玉の中心は、子と親の最近点どうしの線分のうち pivot にいちばん近い点 (回しても隙間に残る)、半径は両側の面に届く最小限
    (+0.4)。隙間が 3 単位より大きい・玉が 2.5 を超えるときは足さない。色は玉の各三角形にいちばん近い子・親の面の絵の一点。
    足したら True。"""
    V, UV, T = bones[name]
    if len(T) == 0 or not refs:
        return False
    pivot = np.array(bones["_pivot_" + name], float)
    cV, cUV, cT = cchild
    cV, cT = np.asarray(cV, float), np.asarray(cT, int)
    # すでに親に埋まっていれば足さない
    csamp = ma.surface_samples(Bone_like(cV, cT))
    psamp = ref_samples(refs)
    d, ip = cKDTree(psamp).query(csamp)
    zone = csamp[np.argsort(d)[:80]]
    if inside_any(zone, refs).mean() >= 0.25:
        return False  # 十分に埋まっている
    k = int(np.argmin(d))
    cp, pp = csamp[k], psamp[ip[k]]
    gap = float(d[k])
    if gap > 3.0:
        return False
    seg = pp - cp
    t = float(np.clip(np.dot(pivot - cp, seg) / max(np.dot(seg, seg), 1e-9), 0, 1))
    center = cp + seg * t
    r = max(np.linalg.norm(center - cp), np.linalg.norm(center - pp)) + 0.4
    # pivot が線分の近くなら pivot を中心にする (回しても玉の位置が動かず、どの角度でも隙間に残る)
    if np.linalg.norm(pivot - center) <= 1.0:
        r2 = max(np.linalg.norm(pivot - cp), np.linalg.norm(pivot - pp)) + 0.4
        if r2 <= 2.5:
            center, r = pivot, r2
    r = max(r, 0.9)
    if r > 2.5:
        return False
    SV, SF = icosphere(center, r, 0)
    cent = [(cV[cT[:, 0]] + cV[cT[:, 1]] + cV[cT[:, 2]]) / 3]
    uvs = [(np.asarray(cUV)[cT[:, 0]] + np.asarray(cUV)[cT[:, 1]] + np.asarray(cUV)[cT[:, 2]]) / 3]
    for (rv, rt), ruv in ref_uvs:
        rv, rt, ruv = np.asarray(rv, float), np.asarray(rt, int), np.asarray(ruv, float)
        if len(rt):
            cent.append((rv[rt[:, 0]] + rv[rt[:, 1]] + rv[rt[:, 2]]) / 3)
            uvs.append((ruv[rt[:, 0]] + ruv[rt[:, 1]] + ruv[rt[:, 2]]) / 3)
    cent = np.vstack(cent)
    uvs = np.vstack(uvs)
    tree = cKDTree(cent)
    V2 = [list(p) for p in V]
    UV2 = [list(p) for p in UV]
    T2 = [list(t_) for t_ in T]
    for a, b, d_ in SF:
        c3 = (SV[a] + SV[b] + SV[d_]) / 3
        suv = [float(x) for x in uvs[int(tree.query(c3)[1])]]
        vi = []
        for kk in (a, b, d_):
            V2.append([float(x) for x in SV[kk]])
            UV2.append(suv)
            vi.append(len(V2) - 1)
        T2.append(vi)
    bones[name] = [np.array(V2), np.array(UV2), np.array(T2, int)]
    return True


class Bone_like:
    def __init__(self, V, T):
        self.V = np.asarray(V, float)
        self.T = np.asarray(T, int)


# ----------------------------------------------------------------- 付け根の襟

def collar(bones, name, refs):
    """子 name の根元 (親に最も近い開いた輪) を親の体積の内側へ押し込んで筒で延長する。bones は {名前: [V, UV, T]} (蓋の前)。
    戻り値は追加した輪の数。"""
    V, UV, T = bones[name]
    if len(T) == 0:
        return 0
    loops, c = boundary_loops(V, T)
    if not loops:
        return 0
    pivot = np.array(bones.get("_pivot_" + name, [0, 0, 0]), float)
    Va = np.array(V)
    # 根元の輪: 輪の重心が pivot に最も近いもの
    best = None
    for ids, tis in loops:
        P = Va[ids]
        d = np.linalg.norm(P.mean(0) - pivot)
        if best is None or d < best[0]:
            best = (d, ids, tis)
    if best is None:
        return 0
    _, ids, tis = best
    P = Va[ids]
    ctr = P.mean(0)
    if not refs:
        return 0
    inside_pivot = bool(inside_any(pivot[None, :], refs)[0])
    if inside_pivot:
        target = pivot
    else:
        # pivot が親の外: 親の表面でいちばん近い点から、親の中心の側へ少し入った点
        psamp = ref_samples(refs)
        ps = psamp[int(np.argmin(np.linalg.norm(psamp - pivot, axis=1)))]
        inward = psamp.mean(0) - ps
        ln_ = float(np.linalg.norm(inward))
        target = ps + inward / max(ln_, 1e-9) * min(2.0, 0.5 * ln_)
    away = ctr - Va.mean(0)
    best_q = None
    for f in (1.0, 0.7, 0.5, 0.3):
        for taper in (0.5, 0.7):
            tg = ctr + (target - ctr) * f
            Q = tg + (P - ctr) * taper
            direction = tg - ctr
            if np.linalg.norm(direction) < 0.3 or np.dot(direction, away) <= 0:
                continue
            if inside_any(np.vstack([Q, tg[None, :]]), refs).mean() >= 0.9:
                best_q = Q
                break
        if best_q is not None:
            break
    if best_q is None:
        return 0
    # 筒: 輪の辺ごとに四角 (輪の向きに沿って新しい輪へ)。輪の辺は既存の面と逆向きに使う → 側面は輪の辺を逆向きにたどる
    n = len(ids)
    V2 = [list(p) for p in V]
    UV2 = [list(p) for p in UV]
    T2 = [list(t) for t in T]
    Q = best_q
    for k in range(n):
        k2 = (k + 1) % n
        a, b = P[k], P[k2]
        a2, b2 = Q[k], Q[k2]
        nb = T[tis[k]]
        suv = [sum(UV[i][0] for i in nb) / 3.0, sum(UV[i][1] for i in nb) / 3.0]
        # 既存の面は辺 a→b を持つ。側面は b→a を持つ: (b, a, a2), (b, a2, b2)
        for tri in ((b, a, a2), (b, a2, b2)):
            vi = []
            for pos in tri:
                V2.append([float(pos[0]), float(pos[1]), float(pos[2])])
                UV2.append(suv)
                vi.append(len(V2) - 1)
            T2.append(vi)
    bones[name] = [np.array(V2), np.array(UV2), np.array(T2, int)]
    return 1


# ----------------------------------------------------------------- ファイル

def drop_degenerate(V, T):
    """面積がほぼ 0 の三角形 (一直線に並んだ三点・つぶれた三角形) を捨てる。描かれないので見た目は変わらず、辺の数え間違いを避ける。"""
    if len(T) == 0:
        return T
    c = ma.weld_ids(V)
    area = np.linalg.norm(np.cross(V[T[:, 1]] - V[T[:, 0]], V[T[:, 2]] - V[T[:, 0]]), axis=1) / 2
    ct = c[T]
    bad = (ct[:, 0] == ct[:, 1]) | (ct[:, 1] == ct[:, 2]) | (ct[:, 2] == ct[:, 0]) | (area < ma.DEGEN_AREA)
    return T[~bad]


def close_mesh(data, with_collar=True):
    """メッシュ JSON (dict) を水密にして返す。戻り値 (dict, 統計)。"""
    bones = {n: [np.array(b.get("vertices", []), float).reshape(-1, 3), np.array(b.get("uvs", []), float).reshape(-1, 2),
                 np.array(b.get("triangles", []), int).reshape(-1, 3)] for n, b in data["bones"].items()}
    for n, b in data["bones"].items():
        bones["_pivot_" + n] = b.get("pivot", [0, 0, 0])
    stats = dict(caps=0, flipped=0, collars=0, balls=0)
    names = [n for n in data["bones"]]
    for n in names:
        V, UV, T = bones[n]
        bones[n] = [V, UV, drop_degenerate(V, T)]
    # 段 A: 親の体積の判定に使う、塞いだ写し (collar は親が閉じていないと内外を判定できない)
    closed = {}
    for n in names:
        V, UV, T = bones[n]
        if len(T) == 0:
            closed[n] = bones[n]
            continue
        T1, _ = fix_orientation(V, T)
        V1, UV1, T1, _ = close_bone(V, UV, T1, V.mean(0))
        T1, _ = fix_orientation(V1, T1)
        closed[n] = [V1, UV1, T1]
    # 段 B: 付け根の襟 (子の元のメッシュの根元を、閉じた親の中へ延ばす)
    if with_collar and len(names) > 1:
        for n in names:
            pn = data["bones"][n].get("parent")
            hops = 0
            while pn in closed and len(closed[pn][2]) == 0 and hops < 20:
                pn = data["bones"][pn].get("parent")  # 親が形を持たない骨 (root など) なら、形のある祖先まで遡る
                hops += 1
            if pn in (None, "None", "") or pn not in closed or len(closed[pn][2]) == 0 or len(bones[n][2]) == 0:
                continue
            sib = [m for m in names if m != n and data["bones"][m].get("parent") == data["bones"][n].get("parent") and len(closed[m][2]) and m != pn]
            refs_n = [pn] + sib
            refs = [(closed[m][0], closed[m][2]) for m in refs_n]
            ref_uvs = [((closed[m][0], closed[m][2]), closed[m][1]) for m in refs_n]
            view = dict(bones)
            if collar(view, n, refs):
                stats["collars"] += 1
            if joint_ball(view, n, refs, closed[n], ref_uvs):
                stats["balls"] += 1
            bones[n] = view[n]
    # 段 C: 仕上げ
    capstart = {}
    for n in names:
        V, UV, T = bones[n]
        if len(T) == 0:
            continue
        cent = V.mean(0)
        T, fx = fix_orientation(V, T)
        nv0 = len(V)
        V, UV, T, caps = close_bone(V, UV, T, cent)
        T, fx2 = fix_orientation(V, T)
        capstart[n] = nv0 if caps else None
        stats["caps"] += caps
        stats["flipped"] += fx + fx2
        bones[n] = [V, UV, T]
    for n in names:
        V, UV, T = bones[n]
        b = data["bones"][n]
        if len(T) == 0:
            continue
        b["vertices"] = [[round(float(x), 4) for x in p] for p in V]
        b["uvs"] = [[round(float(x), 5) for x in p] for p in UV]
        b["triangles"] = [[int(i) for i in t] for t in T]
        b["triangle_count"] = len(T)
        if capstart.get(n) is not None:
            b["cap_vertex_start"] = int(capstart[n])
    data["triangle_count"] = sum(len(bones[n][2]) for n in names)
    data[MARK] = True
    return data, stats


def close_mesh_file(path, with_collar=True, force=False):
    with open(path, encoding="utf-8") as f:
        data = json.load(f)
    if "bones" not in data or (data.get(MARK) and not force):
        return None
    data, stats = close_mesh(data, with_collar)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(data, f, separators=(",", ":"))
    return stats


def main(argv):
    flt = [a for a in argv if not a.startswith("--")]
    if not flt and "--all" not in argv:
        print(__doc__)
        return 1
    force = "--force" in argv
    for p in ma.mesh_files():
        r = ma.rel(p)
        if flt and not any(f in r for f in flt):
            continue
        st = close_mesh_file(p, with_collar="--no-collar" not in argv, force=force)
        if st:
            print("%-50s caps=%d flipped=%d collars=%d balls=%d" % (r, st["caps"], st["flipped"], st["collars"], st["balls"]))
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
