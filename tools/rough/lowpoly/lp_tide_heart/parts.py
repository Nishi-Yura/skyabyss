"""3 案で共通に使う、位置が決まっている部品 (今の geo の cube の位置から取る)。"""
from __future__ import annotations

from common import *  # noqa

# 殻の渦 (段階ごとの角度の範囲。geo の cube の範囲に合わせた)。度。+x = 0、反時計回り。
SPIRAL = {1: (20.0, 350.0), 2: (25.0, 255.0), 3: (70.0, 200.0)}
CHAMBER = {2: (262.0, 385.0), 3: (150.0, 250.0)}


def tap(u, e=0.35, lo=0.28):
    return max(lo, min(1.0, 4 * u, 4 * (1 - u)) ** e)


def rj(i, amp=0.8, seed=1):
    return (hash1(i, seed) - 0.5) * 2 * amp


def chamber_strip(phase, r_out=13.6, r_in=10.0, n=10, z0=-1.1, z1=-0.35, mat="chamber"):
    a0, a1 = CHAMBER[phase]
    outer, inner = [], []
    for i in range(n + 1):
        th = a0 + (a1 - a0) * i / n
        t = i / n
        k = max(0.25, math.sin(math.pi * t) ** 0.6)
        mid = (r_out + r_in) / 2
        hw = (r_out - r_in) / 2 * k
        outer.append((S[0] + (mid + hw) * math.cos(rad(th)), S[1] + (mid + hw) * math.sin(rad(th))))
        inner.append((S[0] + (mid - hw) * math.cos(rad(th)), S[1] + (mid - hw) * math.sin(rad(th))))
    return strip("chamber", mat, outer, inner, z0, z1, (S[0], S[1]))


def drops(phase, scale=0.8, mat="drop"):
    F = []
    for c, sz in cubes(phase, "water_drop"):
        F += ellipsoid("water_drop", mat, c, (scale * 0.8, scale * 1.15, scale * 0.8), segs=4, rings=1)
    return F


def orbit_pearls(phase, mat="orbit", segs=6):
    F = []
    for c, sz in cubes(phase, "orbit"):
        F += ellipsoid("orbit", mat, c, (sz[0] * 0.55, sz[1] * 0.6, sz[2] * 0.55), segs=segs, rings=1,
                       rot=rot_y(hash1(c[0] * 3 + c[2], 2) * 60))
    return F


def shard_chips(phase, mat="shard", k=1.0):
    F = []
    for c, sz in cubes(phase, "shard"):
        r = np.maximum(sz * 0.62 * k, 0.45)
        F += ellipsoid("shard", mat, c, tuple(r), segs=5, rings=1, rot=rot_y(hash1(c[0] * 5 + c[1], 3) * 70))
    return F


def barnacle_sites():
    return cubes(1, "barnacle")


def outward_xy(c):
    d = V(c[0] - S[0], c[1] - S[1], 0)
    return nrm(d)


def frond_sites():
    """(根元, 向き, 長さ) 3 か所。今の frond の cube の位置から。"""
    return [
        (V(-4.2, -1.0, 0.3), V(-0.12, -1.0, 0.08), 4.4),
        (V(14.8, 4.6, 0.0), V(0.55, -0.85, 0.0), 6.0),
        (V(17.6, 20.5, -1.0), V(0.35, -1.0, -0.1), 4.6),
    ]


def moon(mat="moon"):
    return crescent("moon_crescent", mat, (-5.5, 23.2), 5.8, 115, 245, 3.0, 3.0, 5.0, n=7)


def vessel_coral(mat="coral", sides=5, spread=1.0, extra=False):
    base = V(0.0, 19.0, -0.6)
    F = branching("vessel", mat, base, V(0.05, 1.0, -0.12), 6.0, 1.45,
                  [(0.42, V(-1.0 * spread, 0.35, -0.15), 0.62), (0.45, V(1.0 * spread, 0.3, 0.1), 0.6),
                   (0.62, V(0.1, 0.2, -1.0 * spread), 0.5)] + ([(0.7, V(0.2, 0.2, 1.0), 0.4)] if extra else []),
                  sides=sides)
    return F


def on_shell(fns, th0, th1, th, side=1.0, inset=0.0):
    """渦の帯 (rc, hw, zt, zc) の外 (side=+1) か内 (-1) の面の上の点と、外向きの向き (xy の放射)。"""
    rc, hw, zt, zc = fns
    th = min(max(th % 360.0, th0 + 4), th1 - 4) if th0 <= th % 360.0 <= th1 or True else th
    r = rc(th) + side * (hw(th) - inset)
    p = band_pt(th, r, zc(th))
    d = V(math.cos(rad(th)), math.sin(rad(th)), 0.0) * side
    return p, d, th
