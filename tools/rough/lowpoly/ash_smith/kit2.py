"""鍛え手用の部品 (kit.py の上に載せる)。"""
from __future__ import annotations

from kit import *  # noqa


def ngon(c, r, n=6, u=(1, 0, 0), v=(0, 0, 1), rot=0.0, su=1.0, sv=1.0):
    """中心 c、軸 u, v の平面にある n 角形の輪 (半径 r、su・sv で楕円に)。"""
    c, u, v = (np.asarray(a, float) for a in (c, u, v))
    u, v = nrm(u), nrm(v)
    out = []
    for k in range(n):
        a = math.radians(rot + 360.0 * k / n)
        out.append(c + math.cos(a) * r * su * u + math.sin(a) * r * sv * v)
    return out


def hexcol(bone, mat, c, r, h, n=6, taper=(1.0, 1.0), rot=30.0, R=None, su=1.0, sv=1.0, caps=(False, True), top_mat=None):
    """縦の n 角柱 (玄武岩の柱)。c = 底の中心。R で全体を底の中心まわりに傾ける。taper = (上の幅の割合, 上の奥行の割合)。"""
    c = np.asarray(c, float)
    R = np.eye(3) if R is None else R
    ax = np.array([1.0, 0, 0])
    az = np.array([0, 0, 1.0])
    r0 = ngon(c, r, n, ax, az, rot, su, sv)
    r1 = ngon(c + np.array([0, h, 0]), r, n, ax, az, rot, su * taper[0], sv * taper[1])
    rings = [[c + R @ (p - c) for p in rg] for rg in (r0, r1)]
    cp = tuple((top_mat or mat) if x is True else x for x in caps)
    return rloft(bone, mat, rings, caps=cp)


def hpipe(bone, mat, p0, p1, r0, r1=None, n=6, rot=30.0, caps=(False, True), cap_mat=None, sq=1.0):
    """p0 → p1 の n 角の管 (柱・柄・煙突)。"""
    p0, p1 = np.asarray(p0, float), np.asarray(p1, float)
    r1 = r0 if r1 is None else r1
    d = nrm(p1 - p0)
    h = np.array([0, 1.0, 0]) if abs(d[1]) < 0.95 else np.array([1.0, 0, 0])
    u = nrm(np.cross(d, h))
    v = np.cross(d, u)
    rings = [ngon(p0, r0, n, u, v, rot, 1.0, sq), ngon(p1, r1, n, u, v, rot, 1.0, sq)]
    cp = tuple((cap_mat or mat) if x is True else x for x in caps)
    return rloft(bone, mat, rings, caps=cp)


def hband(bone, mat, c, r, h, n=6, rot=30.0, grow=1.0, su=1.0, sv=1.0):
    """水平の輪帯 (鉄の箍)。c = 下の中心。"""
    return hexcol(bone, mat, c, r, h, n=n, rot=rot, su=su, sv=sv, taper=(grow, grow), caps=(False, False))


def contour_cracks(c, col, glow, mats, ramp, thr=0.04, scale=0.11, seed=3, glow_on=True, gate=None, rim=None,
                   ny_min=-0.5, gmin=0.0):
    """3D ノイズの等高線で熱割れ (光る亀裂) を描く。中心が最も熱い。ramp: 冷 → 熱の色。
    gate(P) -> 0..1.5 で亀裂の太さ (熱さ) を場所ごとに変える。rim: 亀裂の縁の暗い焦げの色。"""
    P, bay, owned, ny, mt = c["P"], c["bay"], c["owned"], c["ny"], c["mats"]
    n1 = vnoise(P * scale + seed, 3)
    n2 = vnoise(P * scale * 2.3 + seed * 2, 5)
    r = np.abs(0.68 * n1 + 0.32 * n2 - 0.5)
    t = thr
    if gate is not None:
        t = thr * np.clip(gate(P), 0.0, 1.5)
    sel0 = np.isin(mt, mats) & owned & (ny > ny_min)
    ramp = np.array(ramp, float)
    core = sel0 & (r < t)
    if rim is not None:
        edge = sel0 & (r < t * 1.7) & ~core
        col = np.where(edge[..., None], np.array(rim, float), col)
    k = np.clip(np.floor((1 - r / np.maximum(t, 1e-6)) * len(ramp) * 0.999 + 0.5 * bay), 0, len(ramp) - 1).astype(int)
    col = np.where(core[..., None], ramp[k], col)
    if glow_on:
        glow = glow | (core & (k >= gmin * len(ramp)))
    return col, glow


def paint_slit(c, col, glow, bone, mat, y0, y1, xr, zmax, ramp, nzmin=0.5):
    """正面 (法線が -Z 向き) の面に、y0〜y1・|x| < xr の光る細長い窓 (兜の覗き穴・炉の口) を描く。"""
    P, N, bay, owned = c["P"], c["N"], c["bay"], c["owned"]
    sel = owned & (c["bones"] == bone) & (c["mats"] == mat) & (N[..., 2] < -nzmin) & (P[..., 1] >= y0) & \
        (P[..., 1] <= y1) & (np.abs(P[..., 0]) < xr) & (P[..., 2] < zmax)
    ramp = np.array(ramp, float)
    t = (P[..., 1] - y0) / max(y1 - y0, 1e-6)
    mid = 1 - np.abs(t - 0.5) * 2
    k = np.clip(np.floor(mid * len(ramp) + 0.4 * bay), 0, len(ramp) - 1).astype(int)
    col = np.where(sel[..., None], ramp[k], col)
    return col, glow | sel


def scale_about(faces, s, c=(0, 0, 0)):
    """面の一覧を、点 c を中心に s 倍する (法線はそのまま)。"""
    c = np.asarray(c, float)
    for f in faces:
        f.verts = c + (f.verts - c) * s
        f.ref = c + (f.ref - c) * s
        if f.loc is not None and f.loc.shape == f.verts.shape:
            f.loc = c + (f.loc - c) * s
    return faces


def tooth_ring(y, rx, rz, cx=0.0, cz=0.0, n=14, depth=4.0, k=1.0, phase=0.0, front_gap=None):
    """歯の並んだ輪 (外套・裾の縁): 偶数の頂点は y、奇数の頂点は y - depth (尖り)。楕円 (rx, rz)。"""
    pts = []
    for i in range(n):
        a = math.radians(phase + 360.0 * i / n)
        r = 1.0 if i % 2 == 0 else k
        pts.append(np.array([cx + rx * r * math.sin(a), y - (depth if i % 2 else 0.0), cz - rz * r * math.cos(a)]))
    return pts


def smooth_ring(y, rx, rz, cx=0.0, cz=0.0, n=14, phase=0.0):
    return tooth_ring(y, rx, rz, cx, cz, n, 0.0, 1.0, phase)


def arc_pts(y, rx, rz, a0, a1, n, cx=0.0, cz=0.0, depth=0.0, k=1.0):
    """正面 (-Z) を 0 度、+x 側を 90 度とした角度の範囲 a0〜a1 の弧の点列。depth > 0 なら奇数番の点が下がる (歯)。"""
    pts = []
    for i in range(n):
        a = math.radians(a0 + (a1 - a0) * i / (n - 1))
        r = 1.0 if i % 2 == 0 else k
        pts.append(np.array([cx + rx * r * math.sin(a), y - (depth if i % 2 else 0.0), cz - rz * r * math.cos(a)]))
    return pts


def paint_eyes(c, col, glow, bone, mat, eyes, ramp, nzmax=-0.5):
    """正面向きの面に光る目 (横長の楕円) を描く。eyes = [(x, y, a, b, tilt)]。x の符号に合わせて傾きが対称になる。"""
    P, N, bay, owned = c["P"], c["N"], c["bay"], c["owned"]
    ramp = np.array(ramp, float)
    base = owned & (c["bones"] == bone) & (c["mats"] == mat) & (N[..., 2] < nzmax)
    for (ex, ey, a, b, tilt) in eyes:
        dx = (P[..., 0] - ex)
        sgn = 1 if ex >= 0 else -1
        dy = P[..., 1] - ey - tilt * dx * sgn
        r = np.sqrt((dx / a) ** 2 + (dy / b) ** 2)
        sel = base & (r + 0.05 * bay < 1.0)
        k = np.clip(np.floor((1 - r) * len(ramp) * 1.3 + 0.3 * bay), 0, len(ramp) - 1).astype(int)
        col = np.where(sel[..., None], ramp[k], col)
        glow = glow | sel
    return col, glow


def tilt_x(deg):
    return rot_x(deg)


def sides():
    return ((1, "r"), (-1, "l"))
