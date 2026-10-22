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
  python tools/lib/mesh_close.py --all                # lowpoly/**.json を全部直す (直したものだけ書き換える)
  python tools/lib/mesh_close.py <名前の一部>...      # 名前を絞る
  python tools/lib/mesh_close.py --all --no-collar    # 襟は付けない
生成スクリプトの installer (install_lowpoly*.py ほか) は書き出しのあとに close_mesh_file() を呼ぶ。書き出し直すと開いた状態に戻るため。
"""
from __future__ import annotations
# tools 内の他グループのモジュールを import できるようにする（フォルダ移動後も兄弟モジュールを解決）
import os as _os
import sys as _sys
_sys.path[:0] = [_os.path.join(_os.path.dirname(_os.path.abspath(__file__)), p) for p in ('../audit')]

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
            apex = ctr - nm * min(max(0.12, 0.08 * ext), 0.4)
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

def inside_any(P, refs):
    """点 P が、塞いだ参照メッシュ refs = [(V, T)] のどれかの体積の中か。"""
    m = np.zeros(len(P), bool)
    for V, T in refs:
        m |= ma.inside_mask(P, np.asarray(V, float), np.asarray(T, int))
    return m


def ref_samples(refs):
    return np.vstack([ma.surface_samples(Bone_like(V, T)) for V, T in refs])


def root_buried(bones, name, refs):
    """子の付け根に近い 80 点のうち、親 (と兄弟) の体積の中にある割合。"""
    V, UV, T = bones[name]
    if len(T) == 0:
        return 1.0
    cs = ma.surface_samples(Bone_like(np.asarray(V, float), np.asarray(T, int)))
    d = cKDTree(ref_samples(refs)).query(cs)[0]
    return float(inside_any(cs[np.argsort(d)[:80]], refs).mean())


class Bone_like:
    def __init__(self, V, T):
        self.V = np.asarray(V, float)
        self.T = np.asarray(T, int)


# ----------------------------------------------------------------- 付け根の襟

def collar(bones, name, refs, loop=None):
    """子 name の根元 (親に最も近い開いた輪。loop で輪を直接渡してもよい) を、輪の断面そのままに細めた筒 (袖) で親の体積の内側へ延ばす。
    アニメで曲がっても根元が親から抜けず、隙間が開かない。袖の側面の絵は輪の隣の面の絵の一点 (隣と同じ色で続く)。
    bones は {名前: [V, UV, T]} (蓋の前)。戻り値は追加した輪の数。"""
    V, UV, T = bones[name]
    if len(T) == 0 or not refs:
        return 0
    loops, c = boundary_loops(V, T)
    if not loops:
        return 0
    pivot = np.array(bones.get("_pivot_" + name, [0, 0, 0]), float)
    Va = np.array(V)
    if loop is None:
        best = None
        for ids, tis in loops:
            d = np.linalg.norm(Va[ids].mean(0) - pivot)
            if best is None or d < best[0]:
                best = (d, ids, tis)
        _, ids, tis = best
    else:
        ids, tis = loop
    P = Va[ids]
    ctr = P.mean(0)
    psamp = ref_samples(refs)
    # 袖の行き先: 輪の中心から親の表面へ最も近い点を通り、その先の親の中へ入った点 (pivot が親の中ならそこ)
    cand = []
    if bool(inside_any(pivot[None, :], refs)[0]):
        cand.append(pivot)
    k = int(np.argmin(np.linalg.norm(psamp - ctr, axis=1)))
    ps = psamp[k]
    dv = ps - ctr
    ln_ = float(np.linalg.norm(dv))
    pc = psamp.mean(0)
    ext_c = float((Va.max(0) - Va.min(0)).max())
    for depth in (max(1.6, 0.06 * ext_c), max(1.2, 0.04 * ext_c), max(0.8, 0.025 * ext_c), 0.5):
        dirn = dv / ln_ if ln_ > 1e-6 else (pc - ctr) / max(float(np.linalg.norm(pc - ctr)), 1e-9)
        cand.append(ps + dirn * depth)
        cand.append(ps + (pc - ps) / max(float(np.linalg.norm(pc - ps)), 1e-9) * depth)
    best_q = None
    for target in cand:
        for f in (1.0, 0.8, 0.6):
            for taper in (0.85, 0.7, 0.55):
                tg = ctr + (target - ctr) * f
                Q = tg + (P - ctr) * taper
                if np.linalg.norm(tg - ctr) < 0.2:
                    continue
                if inside_any(np.vstack([Q, tg[None, :]]), refs).mean() >= 0.9:
                    best_q = Q
                    break
            if best_q is not None:
                break
        if best_q is not None:
            break
    if best_q is None:
        return 0
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
        for tri in ((b, a, a2), (b, a2, b2)):
            vi = []
            for pos in tri:
                V2.append([float(pos[0]), float(pos[1]), float(pos[2])])
                UV2.append(suv)
                vi.append(len(V2) - 1)
            T2.append(vi)
    bones[name] = [np.array(V2), np.array(UV2), np.array(T2, int)]
    return 1


def extrude_root(bones, name, refs):
    """閉じた子 (根元に開いた輪がない) の「親に向いた端の面」を取り除き、その縁から袖を親の中へ延ばす (人が面を押し出すのと同じ)。
    端の面 = 親にいちばん近い面と同じ向きでつながる面。取り除くのは全体の 35% まで。戻り値は True / False。"""
    V, UV, T = bones[name]
    if len(T) < 8 or not refs:
        return False
    V = np.asarray(V, float)
    T = np.asarray(T, int)
    psamp = ref_samples(refs)
    tree = cKDTree(psamp)
    A, B, C = V[T[:, 0]], V[T[:, 1]], V[T[:, 2]]
    cr = np.cross(B - A, C - A)
    ar = np.linalg.norm(cr, axis=1)
    ok = ar > 1e-9
    Nf = np.zeros_like(cr)
    Nf[ok] = cr[ok] / ar[ok][:, None]
    cen = (A + B + C) / 3
    d, ip = tree.query(cen)
    toward = np.einsum("ij,ij->i", Nf, psamp[ip] - cen)
    facing = ok & ((toward > 0.2 * np.maximum(d, 1e-6)) | (d < 0.05))
    if not facing.any():
        return False
    dd = np.where(facing, d, np.inf)
    seed = int(np.argmin(dd))
    ext = float((V.max(0) - V.min(0)).max())
    cl = ma.weld_ids(V)
    ct = cl[T]
    edge = collections.defaultdict(list)
    for ti in range(len(T)):
        a, b, e = ct[ti]
        for p, q in ((a, b), (b, e), (e, a)):
            edge[(min(p, q), max(p, q))].append(ti)
    region = {seed}
    stack = [seed]
    while stack:
        ti = stack.pop()
        a, b, e = ct[ti]
        for p, q in ((a, b), (b, e), (e, a)):
            for tj in edge[(min(p, q), max(p, q))]:
                if tj in region or not ok[tj]:
                    continue
                if float(Nf[tj] @ Nf[seed]) < 0.8 or d[tj] > d[seed] + 0.25 * ext:
                    continue
                region.add(tj)
                stack.append(tj)
    if len(region) > 0.35 * len(T):
        return False
    keep = np.array([i for i in range(len(T)) if i not in region])
    V2, UV2 = np.array(bones[name][0], float), np.array(bones[name][1], float)
    bones[name] = [V2, UV2, T[keep]]
    loops, _ = boundary_loops(V2, T[keep])
    rc = cen[list(region)].mean(0)
    best = None
    for ids, tis in loops:
        dist = np.linalg.norm(V2[ids].mean(0) - rc)
        if best is None or dist < best[0]:
            best = (dist, ids, tis)
    if best is None:
        bones[name] = [V2, UV2, T]
        return False
    if collar(bones, name, refs, loop=(best[1], best[2])):
        return True
    bones[name] = [V2, UV2, T]
    return False


# ----------------------------------------------------------------- 付け根を沈める

def components(V, T):
    """三角形ごとの連結成分の番号 (溶接した頂点でつながるもの)。"""
    c = ma.weld_ids(V)
    par = {}

    def find(x):
        while par.setdefault(x, x) != x:
            par[x] = par[par[x]]
            x = par[x]
        return x
    for a, b, d in c[T]:
        for p, q in ((a, b), (b, d)):
            ra, rb = find(p), find(q)
            if ra != rb:
                par[ra] = rb
    return np.array([find(c[t[0]]) for t in T])


def sink_root(bd, refs, amount, name):
    """子の骨 bd (data["bones"][name]) の付け根まわりの頂点を、親の中へ向けて滑らかに沈める。頂点数・面は変えない (新しい面を足さない)。
    つながった塊ごとに (背びれの棘のように一つの骨に塊がいくつもあるとき、それぞれの根を沈める)、付け根 = 親の表面にいちばん近い 80 点の中心、
    動かす量 (隙間 + amount) は親の表面までの距離に応じて 1 → 0 (帯の幅は塊の大きさの 14% と動かす量の 2 倍の大きいほう。
    帯を動かす量より十分広く取るので、薄い部品が裏返らない)。人が「付け根の点を選んで親の中へ動かす」のと同じで、継ぎ目に何も足さない。
    amount は塊の大きさに対する倍率ではなく、骨全体の大きさ ext に対する量。戻り値は動かしたか。"""
    V = np.array(bd["vertices"], float)
    T = np.array(bd["triangles"], int)
    if len(V) == 0 or not refs:
        return False
    psamp = np.vstack([ma.surface_samples(r) for r in refs])
    tree = cKDTree(psamp)
    lab = components(V, T)
    V2 = V.copy()
    moved = False
    for L in np.unique(lab):
        tt = T[lab == L]
        vi = np.unique(tt)
        Vc = V[vi]
        ext = float((Vc.max(0) - Vc.min(0)).max())
        cs = ma.surface_samples(Bone_like(V, tt))
        dd = tree.query(cs)[0]
        gap = float(dd.min())
        if gap > 0.4 * max(ext, 1e-6):
            continue
        root = cs[np.argsort(dd)[:80]]
        c = root.mean(0)
        k = int(np.argmin(np.linalg.norm(psamp - c, axis=1)))
        ps = psamp[k]
        near = psamp[np.linalg.norm(psamp - ps, axis=1) < max(1.0, 0.25 * ext)]
        u = ps - c
        c_in = any(bool(ma.inside_mask(c[None, :], r.V, r.T)[0]) for r in refs)
        if c_in:
            u = c - ps  # 根がもう親の中なら、表面から離れる向き (さらに奥へ)
        elif float(np.linalg.norm(u)) < 1e-6 or gap < 0.05 * ext:
            u = near.mean(0) - ps
        nu = float(np.linalg.norm(u))
        if nu < 1e-9:
            continue
        u = u / nu
        push = gap + amount * (ext / max(float((V.max(0) - V.min(0)).max()), 1e-6))
        band = max(0.14 * ext, 2.0 * push, 0.6)
        d_v = tree.query(Vc)[0]
        w = np.clip(1.0 - d_v / band, 0.0, 1.0)
        w = w * w * (3 - 2 * w)
        V2[vi] = Vc + u[None, :] * push * w[:, None]
        moved = True
    if not moved:
        return False
    bd["vertices"] = [[round(float(x), 4) for x in p] for p in V2]
    return True


def joint_bad(data, mesh_name):
    bones = {n: ma.Bone(n, b) for n, b in data["bones"].items()}
    if len(bones) < 2:
        return {}, bones
    j = ma.joint_check(bones, mesh_name)
    return {n: v for n, v in j.items() if v["status"] in ("detach", "shallow")}, bones


def _joint_score(j):
    """骨の付け根の判定の良さ (大きいほど良い)。ok > shallow > detach、同じなら埋まりの割合と最悪の回転での割合。"""
    rank = {"ok": 2, "shallow": 1, "detach": 0}[j["status"]]
    worst = j["worst"][0] if j.get("worst") else 1.0
    return (rank, min(worst, j["buried"]), j["buried"])


# 手で決めた付け根の沈め (sink_root で直らない薄い翼・小さな飾り用)。(メッシュ名, 骨) -> (向き, 量, 帯の幅)
# 帯の幅は付け根の外向き (|x|) の距離で、その内側ほど強く動かす。None なら骨ごと平行移動 (形は変えない)。
JOINT_TOUCHUPS = {
    ("static_moth", "wing_l"): ((0, -1, 0), 1.5, 4.0),
    ("static_moth", "wing_r"): ((0, -1, 0), 1.5, 4.0),
    ("moss_crawler", "lure_l"): ((0, -1, 0), 0.7, None),
    ("moss_crawler", "lure_r"): ((0, -1, 0), 0.7, None),
    # 顎の後ろの上の縁を頭蓋の底へ立ち上げる (あごの枝。蝶番が頭蓋の中に入る)。(lift, 後ろ z0, 前 z1, 下の y)
    ("waterway_maw", "jaw"): ("ramus", 6.0, -14.0, -26.0, 2.0),
}


def touchup_joint(data, mesh_name, n):
    spec = JOINT_TOUCHUPS.get((mesh_name, n))
    b = data["bones"].get(n)
    if not spec or not b or not b.get("vertices"):
        return False
    V = np.array(b["vertices"], float)
    if spec[0] == "ramus":
        _, lift, z0, z1, yr = spec
        w = np.clip((V[:, 2] - z1) / (z0 - z1), 0.0, 1.0)
        w = w * w * (3 - 2 * w)
        wy = np.clip((V[:, 1] - yr) / (6.8 - yr), 0.0, 1.0)
        V[:, 1] += lift * w * wy
        b["vertices"] = [[round(float(x), 4) for x in p] for p in V]
        return True
    u, push, reach = spec
    if reach is None:
        w = np.ones(len(V))
    else:
        w = np.clip(1.0 - np.abs(V[:, 0]) / reach, 0.0, 1.0)
        w = w * w * (3 - 2 * w)
    V = V + np.array(u, float)[None, :] * push * w[:, None]
    b["vertices"] = [[round(float(x), 4) for x in p] for p in V]
    return True


def fix_joints(data, mesh_name):
    """付け根の検査 (mesh_audit.joint_check) で detach / shallow の骨を、sink_root で沈めて直す。
    骨ごとに、元の頂点から沈める量を (部品の大きさの 3% から 60% まで) 少しずつ増やして試し、最初に ok になった量を採る。
    どれでも ok にならなければ最も判定の良かった量を採る (悪くなるなら沈めない)。裏返る量は採らない。
    離れすぎた部品 (隙間が大きさの 40% 超) は触らない。親から先に直す。戻り値は直せなかった骨の名前の一覧。"""
    steps = (0.03, 0.05, 0.07, 0.1, 0.15, 0.2, 0.3, 0.4, 0.6)
    bad, bones = joint_bad(data, mesh_name)
    for n in sorted(bad, key=lambda x: _depth(data, x)):
        b = data["bones"][n]
        if not b.get("vertices"):
            continue
        bad_now, bones = joint_bad(data, mesh_name)
        if n not in bad_now:
            continue  # 親を直したら直った
        pn = bad_now[n]["parent"]
        refs = [bones[pn]] + [m for k, m in bones.items() if k not in (n, pn) and m.parent == bones[n].parent and len(m.T)]
        ext = float((bones[n].V.max(0) - bones[n].V.min(0)).max())
        psamp = np.vstack([ma.surface_samples(r) for r in refs])
        if float(cKDTree(psamp).query(ma.surface_samples(bones[n]))[0].min()) > 0.4 * ext:
            continue
        orig = b["vertices"]
        best = (_joint_score(bad_now[n]), orig)
        for st in steps:
            b["vertices"] = orig
            if not sink_root(b, refs, st * ext, n):
                break
            if ma.analyze(np.array(b["vertices"], float), np.array(b["triangles"], int))["inv"]:
                continue  # 裏返った (薄い部品が突き抜けた)
            nb, bn = joint_bad(data, mesh_name)
            j = ma.joint_check(bn, mesh_name).get(n) if bn else None
            if j is None:
                continue
            sc = _joint_score(j)
            if sc > best[0]:
                best = (sc, b["vertices"])
            if j["status"] == "ok":
                break
        b["vertices"] = best[1]
    bad, _ = joint_bad(data, mesh_name)
    for n in sorted(bad):
        touchup_joint(data, mesh_name, n)
    bad, _ = joint_bad(data, mesh_name)
    return sorted(bad)


def _anc_in(data, n, bad):
    p = data["bones"][n].get("parent")
    k = 0
    while p in data["bones"] and k < 50:
        yield p in bad
        p = data["bones"][p].get("parent")
        k += 1


def _depth(data, n):
    d = 0
    while n in data["bones"] and data["bones"][n].get("parent") in data["bones"] and d < 50:
        n = data["bones"][n]["parent"]
        d += 1
    return d


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


def drop_duplicates(V, T):
    """同じ三点・同じ向きで重なった三角形の二枚目以降を捨てる (二重に書かれた面は、開いた縁を「二枚の面がある辺」に見せて蓋を止めてしまう)。"""
    if len(T) == 0:
        return T
    c = ma.weld_ids(V)
    seen = set()
    keep = []
    for i, (a, b, d) in enumerate(c[T]):
        k = min(((a, b, d), (b, d, a), (d, a, b)))
        if k in seen:
            continue
        seen.add(k)
        keep.append(i)
    return T[keep] if len(keep) < len(T) else T


def drop_back_to_back(V, T):
    """同じ三点で向きが逆の二枚 (両面に書いた面) のうち、部品の重心から見て内向きのほうを捨てる。
    薄い板ではなく殻の壁を二重に書いたもの用 (開いた縁を覆って蓋が付かなくなる)。呼ぶのは手で選んだメッシュだけ。"""
    if len(T) == 0:
        return T
    c = ma.weld_ids(V)
    lab = components(V, T)
    cen = {L: V[np.unique(T[lab == L])].mean(0) for L in np.unique(lab)}
    byset = collections.defaultdict(list)
    for i, t in enumerate(c[T]):
        byset[tuple(sorted(t))].append(i)
    drop = set()
    for ids in byset.values():
        if len(ids) < 2:
            continue
        ori = {}
        for i in ids:
            a, b, d = c[T[i]]
            ori.setdefault(min(((a, b, d), (b, d, a), (d, a, b))), []).append(i)
        if len(ori) < 2:
            continue
        for i in ids:
            P = V[T[i]]
            n = np.cross(P[1] - P[0], P[2] - P[0])
            if float(np.dot(n, P.mean(0) - cen[lab[i]])) < 0:
                drop.add(i)
    return T[[i for i in range(len(T)) if i not in drop]]


def repair_file(path, pairs=False):
    """閉じ済みのメッシュの二重の面を捨て、そのせいで塞がれなかった縁をもう一度蓋で塞ぐ。直したら True。何度流しても同じ。"""
    with open(path, encoding="utf-8") as f:
        data = json.load(f)
    changed = False
    for n, b in data.get("bones", {}).items():
        if not b.get("triangles"):
            continue
        V = np.array(b["vertices"], float)
        UV = np.array(b["uvs"], float)
        T = np.array(b["triangles"], int)
        T2 = drop_duplicates(V, T)
        if pairs:
            T2 = drop_back_to_back(V, T2)
        if len(T2) == len(T):
            continue
        T2, _ = fix_orientation(V, T2)
        nv0 = len(V)
        V2, UV2, T2, caps = close_bone(V, UV, T2, V.mean(0))
        T2, _ = fix_orientation(V2, T2)
        b["vertices"] = [[round(float(x), 4) for x in p] for p in V2]
        b["uvs"] = [[round(float(x), 5) for x in p] for p in UV2]
        b["triangles"] = [[int(i) for i in t] for t in T2]
        b["triangle_count"] = len(T2)
        if caps and b.get("cap_vertex_start") is None:
            b["cap_vertex_start"] = nv0
        changed = True
    if changed:
        data["triangle_count"] = sum(len(b.get("triangles", [])) for b in data["bones"].values())
        with open(path, "w", encoding="utf-8") as f:
            json.dump(data, f, separators=(",", ":"))
    return changed


def close_mesh(data, with_collar=True):
    """メッシュ JSON (dict) を水密にして返す。戻り値 (dict, 統計)。"""
    bones = {n: [np.array(b.get("vertices", []), float).reshape(-1, 3), np.array(b.get("uvs", []), float).reshape(-1, 2),
                 np.array(b.get("triangles", []), int).reshape(-1, 3)] for n, b in data["bones"].items()}
    for n, b in data["bones"].items():
        bones["_pivot_" + n] = b.get("pivot", [0, 0, 0])
    stats = dict(caps=0, flipped=0, collars=0)
    names = [n for n in data["bones"]]
    for n in names:
        V, UV, T = bones[n]
        bones[n] = [V, UV, drop_duplicates(V, drop_degenerate(V, T))]
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
            view = dict(bones)
            if collar(view, n, refs):
                stats["collars"] += 1
            elif root_buried(view, n, refs) < 0.25 and extrude_root(view, n, refs):
                stats["collars"] += 1
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
    if with_collar:
        stats["unfixed"] = fix_joints(data, os.path.splitext(os.path.basename(path))[0])
    with open(path, "w", encoding="utf-8") as f:
        json.dump(data, f, separators=(",", ":"))
    return stats


def main(argv):
    flt = [a for a in argv if not a.startswith("--")]
    if not flt and "--all" not in argv:
        print(__doc__)
        return 1
    force = "--force" in argv
    if "--repair" in argv:
        for p in ma.mesh_files():
            if flt and not any(f in ma.rel(p) for f in flt):
                continue
            if repair_file(p, pairs="--pairs" in argv):
                print("repaired", ma.rel(p))
        return 0
    jonly = "--joints-only" in argv  # 閉じ済みのメッシュの付け根だけをもう一度直す (fix_joints)
    for p in ma.mesh_files():
        r = ma.rel(p)
        if flt and not any(f in r for f in flt):
            continue
        if jonly:
            with open(p, encoding="utf-8") as f:
                d = json.load(f)
            if "bones" in d and len(d["bones"]) > 1:
                un = fix_joints(d, os.path.splitext(os.path.basename(p))[0])
                with open(p, "w", encoding="utf-8") as f:
                    json.dump(d, f, separators=(",", ":"))
                print("%-50s unfixed=%s" % (r, ",".join(un) or "-"))
            continue
        st = close_mesh_file(p, with_collar="--no-collar" not in argv, force=force)
        if st:
            print("%-50s caps=%d flipped=%d collars=%d unfixed=%s" % (r, st["caps"], st["flipped"], st["collars"], ",".join(st.get("unfixed", [])) or "-"))
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
