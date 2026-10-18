"""雷乞い用の追加の形の部品。"""
from __future__ import annotations

from parts import *  # noqa


def tring(y, rx, zf, zb, n=12, phase=15.0, zc=0.0, cx=0.0, zig=0.0, dy=None):
    """楕円の輪 (前 = -Z)。zig > 0 なら奇数番の頂点の半径を (1 - zig) 倍にした鋸歯 (襞)。
    dy: 頂点ごとの高さの足し (長さ n の列、または関数 k -> dy)。"""
    pts = []
    for k in range(n):
        a = math.radians(phase + 360.0 * k / n)
        sn, c = math.sin(a), math.cos(a)
        z = zf * c if c >= 0 else zb * -c
        x = rx * sn
        if k % 2 == 1 and zig:
            x *= 1 - zig
            z *= 1 - zig
        d = 0.0
        if dy is not None:
            d = dy(k) if callable(dy) else dy[k]
        pts.append(np.array([cx + x, y + d, zc + z]))
    return pts


def circ(y, r, zc=0.0, cx=0.0, n=8, phase=22.5, rz=None):
    """水平な円 (n 角形)。前 = -Z。"""
    return tring(y, r, -(rz or r), (rz or r), n=n, phase=phase, zc=zc, cx=cx)


def disc(bone, mat, y0, r0, y1, r1, zc=0.0, cx=0.0, n=8, phase=22.5, caps=(True, True), rz=1.0):
    return rloft(bone, mat, [circ(y0, r0, zc, cx, n, phase, r0 * rz), circ(y1, r1, zc, cx, n, phase, r1 * rz)], caps)


def sp(bone, mat, base, d, length, r, sides=3, rings=1, bend=(0, 0, 0)):
    return spike(bone, mat, base, d, length, r, sides=sides, rings=rings, bend=bend)


def lerp(a, b, t):
    return np.asarray(a, float) * (1 - t) + np.asarray(b, float) * t


def bez(p0, p1, p2, n=4):
    """二次ベジェの点列 (n 区間)。"""
    p0, p1, p2 = (np.asarray(p, float) for p in (p0, p1, p2))
    return [(1 - t) ** 2 * p0 + 2 * (1 - t) * t * p1 + t * t * p2 for t in (i / n for i in range(n + 1))]


def jit(i, a=1.0, salt=0):
    q = math.sin(i * 12.9898 + salt * 78.233) * 43758.5453
    return (q - math.floor(q) - 0.5) * 2 * a


def rotp(p, ang, c=(0.0, 0.0)):
    """点 p を、(c[0], c[1]) の (x, z) 軸まわりに y 軸回転。"""
    a = math.radians(ang)
    x, z = p[0] - c[0], p[2] - c[1]
    return np.array([c[0] + x * math.cos(a) + z * math.sin(a), p[1], c[1] - x * math.sin(a) + z * math.cos(a)])


def bolt(bone, mat, pts, r=0.32, sides=3, hint=(0, 0, 1)):
    """稲妻 (折れ線の細い三角柱)。"""
    return tube(bone, mat, pts, [r] * len(pts), sides=sides, hint=hint, phase=90, caps=(False, False))


def leaf(bone, mat, p, u, v, L, w, thick, mid=0.4):
    """平たい葉・羽: 根元 p から u の向きへ長さ L、幅 w (v の向き)。厚み thick (u × v の向き)。凸の菱形。"""
    p, u, v = (np.asarray(a, float) for a in (p, u, v))
    u, v = nrm(u), nrm(v)
    pts = [p, p + u * L * mid + v * w / 2, p + u * L, p + u * L * mid - v * w / 2]
    return extrude(bone, mat, pts, nrm(np.cross(u, v)) * thick)


def tri_plate(bone, mat, pts, thick):
    pts = [np.asarray(q, float) for q in pts]
    n = nrm(np.cross(pts[1] - pts[0], pts[2] - pts[0]))
    return extrude(bone, mat, pts, n * thick)


def hoop(bone, mat, c, r, n, plane_u, plane_v, thick, sides=3, phase=90):
    """輪 (n 角形の細い筒)。plane_u / plane_v の張る平面。"""
    c, u, v = (np.asarray(a, float) for a in (c, plane_u, plane_v))
    u, v = nrm(u), nrm(v)
    pts = [c + r * (math.cos(2 * math.pi * k / n) * u + math.sin(2 * math.pi * k / n) * v) for k in range(n + 1)]
    return tube(bone, mat, pts, [thick] * (n + 1), sides=sides, hint=nrm(np.cross(u, v)), phase=phase, caps=(False, False))


def hexsec(cx, cy, cz, w, h, n=6):
    """z に垂直な六角 (左右が頂点の扁平な六角形) の輪。"""
    pts = []
    for k in range(n):
        a = math.radians(360.0 * k / n)
        pts.append(np.array([cx + w / 2 * math.cos(a), cy + h / 2 * math.sin(a) / 0.866, cz]))
    return pts


def sleeve_hex(bone, mat, s, secs, caps=(False, False)):
    """腕の袖: secs = [(y, z, w, h)] を x = s*4.9 に並べた六角の筒。"""
    return rloft(bone, mat, [hexsec(s * 4.9, y, z, w, h) for (y, z, w, h) in secs], caps)


SKIN = dict(ramp=[(34, 32, 44), (64, 60, 78), (98, 94, 114), (136, 132, 152)], base=0.5, lit=0.3, noise=0.05, crack=0, min=0,
            stripe=3, axis="v")


def fist(bone, s, c=(4.8, 10.5, -6.0), size=(1.9, 2.0, 1.9)):
    return fbox(bone, "skin", (s * c[0], c[1], c[2]), size, R=rot_x(14) @ rot_z(-s * 8), taper=(0.7, 0.75))


def plate_yz(bone, mat, pts_zy, x, thick):
    """YZ 平面の凸多角形 (z, y) を x の幅 thick で押し出した板。"""
    pts = [np.array([x, y, z]) for z, y in pts_zy]
    return extrude(bone, mat, pts, np.array([thick, 0, 0]))
