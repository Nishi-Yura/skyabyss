"""潮招き用の追加の形の部品。"""
from __future__ import annotations

from parts import *  # noqa


def tring(y, rx, zf, zb, n=12, phase=15.0, zc=0.0, cx=0.0, zig=0.0, dy=None):
    """楕円の輪 (前 = -Z)。zig > 0 なら奇数番の頂点の半径を (1 - zig) 倍にした鋸歯 (瓦の縁)。
    dy: 頂点ごとの高さの足し (長さ n の列)。"""
    pts = []
    for k in range(n):
        a = math.radians(phase + 360.0 * k / n)
        sn, c = math.sin(a), math.cos(a)
        z = zf * c if c >= 0 else zb * -c
        x = rx * sn
        if k % 2 == 1 and zig:
            x *= 1 - zig
            z *= 1 - zig
        pts.append(np.array([cx + x, y + (dy[k] if dy is not None else 0.0), zc + z]))
    return pts


def scale_ring(r, k, c=None):
    r = [np.asarray(p, float) for p in r]
    c = np.mean(r, 0) if c is None else np.asarray(c, float)
    return [c + (p - c) * k for p in r]


def blade(bone, mat, path, w, t, sides=4, hint=(1, 0, 0), phase=45.0, caps=(True, True)):
    """path に沿う平たい刃・指。w = hint 方向の半径、t = もう一方の半径 (点ごとの配列)。"""
    return tube(bone, mat, path, w, t, sides=sides, hint=hint, phase=phase, caps=caps)


def sp(bone, mat, base, d, length, r, sides=3, rings=1, bend=(0, 0, 0)):
    return spike(bone, mat, base, d, length, r, sides=sides, rings=rings, bend=bend)


def lerp(a, b, t):
    return np.asarray(a, float) * (1 - t) + np.asarray(b, float) * t


def bez(p0, p1, p2, n=4):
    """二次ベジェの点列 (n 区間)。"""
    p0, p1, p2 = (np.asarray(p, float) for p in (p0, p1, p2))
    return [(1 - t) ** 2 * p0 + 2 * (1 - t) * t * p1 + t * t * p2 for t in (i / n for i in range(n + 1))]


def pv(bone):
    """作図空間 (前 = -Z) での骨の pivot。"""
    return flipxz(BONES[bone][1])


def coral(bone, mat, base, d, length, r, branches, sides=4, rings=2, bend=(0, 0, 0), bmat=None, bsides=3, brings=1):
    """枝分かれする珊瑚。branches = [(幹の位置 0..1, 向き(度), 長さの割合, 開き(度), 曲がり(任意))]。"""
    d = nrm(np.asarray(d, float))
    base = np.asarray(base, float)
    bend = np.asarray(bend, float)
    out = spike(bone, mat, base, d, length, r, sides=sides, rings=rings, bend=bend)
    ref = np.array([0, 1.0, 0]) if abs(d[1]) < 0.9 else np.array([1.0, 0, 0])
    u = nrm(np.cross(d, ref))
    v = np.cross(d, u)
    for br in branches:
        t, ang, fr, tilt = br[:4]
        bb = np.asarray(br[4], float) if len(br) > 4 else np.zeros(3)
        p = base + d * length * t + bend * t * t
        a = math.radians(ang)
        tl = math.radians(tilt)
        dd = nrm(d * math.cos(tl) + (u * math.cos(a) + v * math.sin(a)) * math.sin(tl))
        out += spike(bone, bmat or mat, p, dd, length * fr, r * (1 - t) * 0.8 + 0.6, sides=bsides, rings=brings, bend=bb)
    return out


def jit(i, a=1.0, salt=0):
    """決まった乱数 (-a..a)。"""
    q = math.sin(i * 12.9898 + salt * 78.233) * 43758.5453
    return (q - math.floor(q) - 0.5) * 2 * a
