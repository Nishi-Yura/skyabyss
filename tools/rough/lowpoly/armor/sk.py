"""巨神の防具シリーズ用の部品 (面取りした板・なめらかな輪・厚みのある布)。
B 祭具 (布が輪郭を作り、板は要所だけ) を土台に、角の丸い断面・面取り・薄いが閉じた布で滑らかにする。
座標は akit と同じ (骨の絶対座標、正面 -Z、右腕・右脚は x<0)。"""
from __future__ import annotations

import math

import numpy as np

from akit import *  # noqa: F401,F403
from akit import V, tag, spk, feather, crv, nrm, rot_x, rot_y, rot_z, at, bx, ext
from lpkit import _mk, loft

ARMS = {-1: "armorRightArm", 1: "armorLeftArm"}


def mk(pts, bone, mat, ref):
    f = _mk(pts, bone, mat, ref)
    return [f] if f else []


def sring(y, hx, zf, zb, n=12, p=2.7, cx=0.0, dy=None):
    """角の丸い輪 (超楕円)。頂点 0 が正面 (-Z) の中央、n/2 が背中の中央。"""
    e = 2.0 / p
    zc, hz = (zf + zb) / 2, (zb - zf) / 2
    out = []
    for k in range(n):
        a = 2 * math.pi * k / n
        s, c = math.sin(a), math.cos(a)
        x = hx * math.copysign(abs(s) ** e, s)
        z = zc - hz * math.copysign(abs(c) ** e, c)
        out.append(V(cx + x, y + (dy[k] if dy is not None else 0.0), z))
    return out


def dloft(bone, mat, rings, caps=(False, False), mats=None):
    return tag(loft(bone, mat, rings, caps=caps, mats=mats), bone)


def bplate(bone, mat, bev, pts, c=(0, 0, 0), R=None, h=1.0, ins=0.8, ch=0.45, bow=0.0, back=None, dbl=False):
    """面取りした板。pts は局所の xy (凸)。手前 (-z 局所) の縁が bev の材質で斜めに落ちる (dbl = 両面)。
    bow > 0 で左右の端が後ろ (+z 局所) へ反る (体に沿う)。"""
    P = np.array(pts, float)
    cen = P.mean(0)
    rad = float(np.mean(np.linalg.norm(P - cen, axis=1)))
    s = max(0.45, 1 - ins / rad)
    xm = max(1e-6, float(np.abs(P[:, 0] - cen[0]).max()))

    def r3(Q, z):
        return [V(x, y, z + bow * ((x - cen[0]) / xm) ** 2) for x, y in Q]

    Ps = cen + (P - cen) * s
    if dbl:
        rings = [r3(Ps, -h / 2), r3(P, -h / 2 + ch), r3(P, h / 2 - ch), r3(Ps, h / 2)]
        f = loft(bone, mat, rings, caps=(mat, mat), mats=[bev, mat, bev])
    else:
        rings = [r3(Ps, -h / 2), r3(P, -h / 2 + ch), r3(P, h / 2)]
        f = loft(bone, mat, rings, caps=(mat, back or mat), mats=[bev, mat])
    tag(f, bone)
    return at(f, R, c)


def ngon(w, h, k=8, taper=0.0, bottom=0.0):
    """凸の丸み多角形 (局所 xy)。taper > 0 で下が細い。bottom > 0 で下端が尖る。"""
    pts = []
    for i in range(k):
        a = 2 * math.pi * (i + 0.5) / k
        x, y = math.sin(a) * w / 2, math.cos(a) * h / 2
        if y < 0:
            x *= (1 - taper)
        pts.append((x, y))
    if bottom > 0:
        pts.append((0.0, -h / 2 - bottom))
        pts.sort(key=lambda p: math.atan2(p[0], p[1]))
    return pts


def slab(bone, mats, G, ov, t, hem_open=False):
    """曲面の格子 G[row][col] を厚み t の閉じた布にする。ov = 外へ向かう向き。
    上の縁は体の中なので作らない。mats は行ごとの材質 (len = rows-1)。"""
    G = [[V(*p) for p in row] for row in G]
    ov = nrm(V(*ov))
    Gi = [[p - ov * t for p in row] for row in G]
    R, C = len(G), len(G[0])
    if isinstance(mats, str):
        mats = [mats] * (R - 1)
    cen = np.mean([p for row in G for p in row], axis=0) - ov * t / 2
    out = []
    for r in range(R - 1):
        for c in range(C - 1):
            q = [G[r][c], G[r][c + 1], G[r + 1][c + 1], G[r + 1][c]]
            qi = [Gi[r][c], Gi[r][c + 1], Gi[r + 1][c + 1], Gi[r + 1][c]]
            ctr = sum(q) / 4
            out += mk(q, bone, mats[r], ctr - ov * 6)
            out += mk(qi, bone, "under", ctr + ov * 6)
    for r in range(R - 1):
        for c in (0, C - 1):
            q = [G[r][c], G[r + 1][c], Gi[r + 1][c], Gi[r][c]]
            out += mk(q, bone, mats[r], cen)
    for c in range(C - 1):
        q = [G[R - 1][c], G[R - 1][c + 1], Gi[R - 1][c + 1], Gi[R - 1][c]]
        out += mk(q, bone, mats[-1], cen)
    return out


def drape(bone, mats, xc, ytop, ybot, z0, hw0, hw1, rows=4, cols=7, t=0.55, lean=0.0, hem=None, fold=0.35,
          out=1.0, flare_z=0.9, ov=(0, 0, 1)):
    """垂れ布: 上から下へ広がり、後ろ (out=+1) か前 (out=-1) へ反る。hem = 列ごとの下端のずれ。"""
    G = []
    for r in range(rows):
        v = r / (rows - 1)
        row = []
        for c in range(cols):
            u = 2.0 * c / (cols - 1) - 1.0
            hw = hw0 + (hw1 - hw0) * v ** 1.15
            y = ytop + (ybot - ytop) * v
            if r == rows - 1 and hem is not None:
                y += hem[c % len(hem)]
            x = xc + lean * v + u * hw
            z = z0 + out * (flare_z * v * v + fold * math.sin(u * 2.4) * v)
            row.append((x, y, z))
        G.append(row)
    return slab(bone, mats, G, (0, 0, out) if ov == (0, 0, 1) else ov, t)


def lume(bone, c, size, rot=None):
    """光る細い板 (2 テクセル以上の幅)。"""
    return bx(bone, "lume", c, size, rot=rot, skip=("b",))


def gem(bone, c, size=(1.3, 1.0, 0.55), rot=None):
    return bx(bone, "gem", c, size, rot=rot, taper=(0.75, 0.75), skip=("b",))


def band(bone, mat, c, size, **k):
    return bx(bone, mat, c, size, skip=("b", "u"), **k)


def ring_tube(bone, mat, c, R, r, n=12, sides=4, plane="xy", z=None, span=360, start=0):
    """円環 (背の環・腕輪)。plane xy = 正面を向く輪。"""
    pts = []
    for i in range(n + (0 if span >= 360 else 1)):
        th = math.radians(start + span * i / n)
        if plane == "xy":
            pts.append((c[0] + R * math.sin(th), c[1] + R * math.cos(th), c[2]))
        else:
            pts.append((c[0] + R * math.sin(th), c[1], c[2] + R * math.cos(th)))
    if span >= 360:
        pts.append(pts[0])
    return crv(bone, mat, pts, [r] * len(pts), sides=sides, hint=(0, 0, 1) if plane == "xy" else (0, 1, 0))


M_TOP = np.array([[1.0, 0, 0], [0, 0, 1.0], [0, -1.0, 0]])      # 局所 -z が世界の +y を向く (肩の板)
M_SIDE = lambda s: np.array([[0, 0, -1.0 * s], [0, 1.0, 0], [1.0 * s, 0, 0]])  # 局所 -z が世界の s*x を向く (外向き)
