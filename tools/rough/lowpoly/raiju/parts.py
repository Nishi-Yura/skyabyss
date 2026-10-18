"""形の部品 (番兵長の ck.py から写した薄い道具)。lpkit のプリミティブの上に載せる。"""
from __future__ import annotations

import math

import numpy as np
from common import *  # noqa


# ====================================================================== 形の部品

def rrect(c, u, v, w, h, cut=0.0):
    """中心 c、軸 u, v の矩形の輪 (4 点)。cut > 0 なら角を落とした 8 点 (cut は短辺の半分に対する割合)。"""
    c, u, v = (np.asarray(a, float) for a in (c, u, v))
    u, v = nrm(u), nrm(v)
    hw, hh = w / 2.0, h / 2.0
    if cut <= 0:
        return [c - hw * u - hh * v, c + hw * u - hh * v, c + hw * u + hh * v, c - hw * u + hh * v]
    k = cut * min(hw, hh)
    pts = [(-hw + k, -hh), (hw - k, -hh), (hw, -hh + k), (hw, hh - k), (hw - k, hh), (-hw + k, hh), (-hw, hh - k),
           (-hw, -hh + k)]
    return [c + a * u + b * v for a, b in pts]


def rloft(bone, mat, rings, caps=(True, True), mats=None):
    return loft(bone, mat, rings, caps=tuple(mat if x is True else x for x in caps), mats=mats)


def fbox(bone, mat, c, size, R=None, taper=(1.0, 1.0), shift=(0.0, 0.0), cut=0.0, caps=(True, True), mats=None):
    """傾けられる箱 (上へ向けて taper で細る錐台)。size = (x, y, z)。R は中心まわりの回転。"""
    sx, sy, sz = size
    R = np.eye(3) if R is None else R
    ex, ey, ez = (R @ np.array(a) for a in ((1, 0, 0), (0, 1, 0), (0, 0, 1)))
    c = np.asarray(c, float)
    r0 = rrect(c - ey * sy / 2, ex, ez, sx, sz, cut)
    r1 = rrect(c + ey * sy / 2 + ex * shift[0] + ez * shift[1], ex, ez, sx * taper[0], sz * taper[1], cut)
    return rloft(bone, mat, [r0, r1], caps, mats)


def cbox(bone, mat, c, size, R=None, taper=0.78, cut=0.45, caps=(True, True)):
    """面取りした箱: 下 (taper) - 中 (全幅) - 上 (taper) の 3 つの八角形の輪。拳・兜・肩の玉など。"""
    sx, sy, sz = size
    R = np.eye(3) if R is None else R
    ex, ey, ez = (R @ np.array(a) for a in ((1, 0, 0), (0, 1, 0), (0, 0, 1)))
    c = np.asarray(c, float)
    rings = [rrect(c - ey * sy / 2, ex, ez, sx * taper, sz * taper, cut),
             rrect(c - ey * sy * 0.18, ex, ez, sx, sz, cut),
             rrect(c + ey * sy * 0.18, ex, ez, sx, sz, cut),
             rrect(c + ey * sy / 2, ex, ez, sx * taper, sz * taper, cut)]
    return rloft(bone, mat, rings, caps)


def rings_along(bone, mat, secs, axis_u, axis_v, caps=(True, True), cut=0.0, mats=None):
    """断面の列: secs = [(center, w, h), ...]。断面は u, v の平面。"""
    return rloft(bone, mat, [rrect(c, axis_u, axis_v, w, h, cut) for c, w, h in secs], caps, mats)


def fin(bone, mat, pts_yz, x, thick, R=None, center=None, tip_thin=0.45):
    """YZ 平面の多角形 (z, y) を x の厚み thick で押し出した板 (先へ行くほど薄くなる)。
    R は center まわりの回転 (省略時は x を中心に回さない)。"""
    pts = [np.array(p, float) for p in pts_yz]
    ymin = min(p[1] for p in pts)
    ymax = max(p[1] for p in pts)

    def mk(side):
        out = []
        for z, y in pts:
            k = (y - ymin) / max(ymax - ymin, 1e-6)
            tt = thick / 2 * (1 - (1 - tip_thin) * k)
            out.append(np.array([x + side * tt, y, z]))
        return out
    a, b = mk(-1), mk(1)
    rings = [a, b]
    fs = rloft(bone, mat, rings, (True, True))
    if R is not None:
        cen = np.array(center if center is not None else (x, 0, 0), float)
        transform(fs, R, cen - R @ cen)
    return fs


def ringp(y, rx, zf, zb, zc=0.0, n=8, phase=22.5, dz=0.0, dzk=()):
    """正面が頂点でなく面になる n 角形の輪 (ring() と同じ向き。phase 度ずらす)。dzk の頂点だけ z を dz ずらす。"""
    pts = []
    for k in range(n):
        a = math.radians(phase + 360.0 * k / n)
        sn, c = math.sin(a), math.cos(a)
        z = zc + (zf * c if c >= 0 else zb * -c)
        if k in dzk:
            z += dz
        pts.append(np.array([rx * sn, y, z]))
    return pts


def extrude(bone, mat, pts, d, taper=1.0, caps=(True, True)):
    """平面の多角形 pts (3D の点列) を d だけ押し出した板。taper で押し出した側が縮む。"""
    pts = [np.asarray(p, float) for p in pts]
    c = np.mean(pts, 0)
    d = np.asarray(d, float)
    r0 = [p - d / 2 for p in pts]
    r1 = [c + (p - c) * taper + d / 2 for p in pts]
    return rloft(bone, mat, [r0, r1], caps)


def mirror(fn, **kw):
    """左右 (s = +1: 左 ... x が正) の両側を作る。"""
    out = []
    for s in (1, -1):
        out += fn(s, **kw)
    return out


def lim(bone, mat, path, ra, rb=None, sides=6, hint=(1, 0, 0), phase=0.0, caps=(True, True)):
    return tube(bone, mat, path, ra, rb, sides=sides, hint=hint, phase=phase, caps=caps)


def torso_pt(rings, y, ang):
    """リング列 (ring() が作る点の列) 上の、高さ y・角度 ang (度。0 が正面、90 が +x) の点。"""
    ys = [r[0][1] for r in rings]
    j = int(np.clip(np.searchsorted(ys, y) - 1, 0, len(rings) - 2))
    t = (y - ys[j]) / max(ys[j + 1] - ys[j], 1e-9)
    n = len(rings[0])
    f = ang / (360.0 / n)
    k0 = int(math.floor(f)) % n
    k1 = (k0 + 1) % n
    u = f - math.floor(f)
    a = rings[j][k0] * (1 - u) + rings[j][k1] * u
    b = rings[j + 1][k0] * (1 - u) + rings[j + 1][k1] * u
    return a * (1 - t) + b * t


