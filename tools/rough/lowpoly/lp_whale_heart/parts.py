"""3 案で共有する部品: 根 (tendrils)、台座、鎖と骨の枠、爪、棘の輪、心臓の血管。"""
from __future__ import annotations

from common import *  # noqa


def rot_pt(r, ang_deg, y):
    t = math.radians(ang_deg)
    return V(r * math.sin(t), y, -r * math.cos(t))


def tendrils(n=6, seed=1, tip=True, thick=1.15):
    """床を這う赤い根。先に骨色の小さな尖り。"""
    F = []
    for i in range(n):
        a0 = 360.0 / n * i + 18 + jit(seed, i, amp=10)
        path, ra = [], []
        for s, (r, y, da, rad) in enumerate([(5.6, 1.1, 0, thick), (7.6, 1.3, 6, thick * 0.9), (9.6, 0.9, -5, thick * 0.65),
                                              (11.2, 0.8, 4, thick * 0.35)]):
            path.append(rot_pt(r, a0 + da, y))
            ra.append(rad)
        F += tube("tendrils", "tend", path, ra, [x * 0.7 for x in ra], sides=3, hint=(0, 1, 0), phase=90,
                  axis_fn=lambda p: V(0, p[1], 0))
        if tip:
            e = path[-1]
            d = nrm(path[-1] - path[-2])
            F += tube("tendrils", "tip", [e, e + d * 1.8], [thick * 0.35, 0.0], sides=3, hint=(0, 1, 0), phase=90,
                      axis_fn=lambda p: V(0, p[1], 0))
    return F


def base_ring(bone="base", rings=None, mat="base"):
    rings = rings or [(0.0, 5.4), (0.9, 6.6), (2.3, 6.9), (3.0, 7.5)]
    R = [ring(y, r, -r, r) for y, r in rings]
    return loft(bone, mat, R, caps=(mat, False))


def bone_frame():
    """骨の枠: 鯨の肋のような弧の横木 (両端は下へ曲がって尖る) と、心臓の上へ下りる柱。"""
    F = []
    beam = [V(-13.2, 23.6, 0), V(-11.6, 25.4, 0), V(-6.5, 26.9, 0), V(0, 27.4, 0), V(6.5, 26.9, 0), V(11.6, 25.4, 0),
            V(13.2, 23.6, 0)]
    F += tube("chains", "bone", beam, [0.0, 1.1, 1.5, 1.7, 1.5, 1.1, 0.0], [0.0, 0.9, 1.2, 1.4, 1.2, 0.9, 0.0], sides=5,
              hint=(0, 0, 1), phase=0, axis_fn=lambda p: V(0, 25, 0))
    stem = [V(0, 27.0, 0), V(0, 24.4, 0), V(0, 21.8, 0)]
    F += tube("chains", "bone", stem, [2.0, 1.5, 0.8], [1.8, 1.3, 0.7], sides=6, hint=(1, 0, 0), phase=0,
              axis_fn=lambda p: V(0, p[1], 0))
    return F


def side_chains(n=5):
    F = []
    for s in (-1, 1):
        F += chain_links("chains", "link", V(s * 12.5, 24.0, 0), V(s * 12.5, 15.8, 0), n, size=(1.7, 2.6), alt=0 if s < 0 else 1)
    return F


def disc(bone, mat, c, rings, n=12, zdir=-1.0, phase=0.0):
    """正面向きの円盤 (虹彩)。rings = [(半径, z のずれ), ...] 外から内へ。最後の輪は塞ぐ。"""
    c = np.asarray(c, float)
    R = []
    for r, dz in rings:
        R.append([c + V(r * math.cos(math.radians(phase + 360.0 * i / n)), r * math.sin(math.radians(phase + 360.0 * i / n)),
                        zdir * dz) for i in range(n)])
    F = []
    back = V(0, 0, -zdir * 6)
    for k in range(len(R) - 1):
        for i in range(n):
            q = [R[k][i], R[k][(i + 1) % n], R[k + 1][(i + 1) % n], R[k + 1][i]]
            F += poly(bone, mat, q, np.mean(q, 0) + back)
    F += poly(bone, mat, R[-1], np.mean(R[-1], 0) + back)
    return F


def claw(bone, mat, variant=0):
    """爪: 局所の -Z 方向へ伸びて先が上へ曲がる、割れた石の鉤。"""
    F = []
    path = [V(0, 8.2, -9.8), V(0, 9.2, -12.8), V(0, 11.6, -15.2), V(0, 15.4, -15.4)]
    F += tube(bone, mat, path, [3.4, 2.9, 2.0, 0.0], [2.2, 1.9, 1.3, 0.0], sides=4, hint=(1, 0, 0), phase=45,
              axis_fn=lambda p: V(0, p[1] - 3, -6))
    for s in (-1, 1):
        p2 = [V(s * 2.2, 8.4, -10.2), V(s * 3.0, 9.6, -13.2), V(s * 2.6, 12.8, -14.6)]
        F += tube(bone, mat, p2, [1.6, 1.3, 0.0], [1.2, 1.0, 0.0], sides=4, hint=(1, 0, 0), phase=45,
                  axis_fn=lambda p: V(0, p[1] - 3, -6))
    return F


def veins(bone, center, radii, deform, specs, lift=0.55, rad=0.55):
    """specs: [[(lat,phi),..], ..] 表面に沿った血管 (3 角の細い筒)。"""
    F = []
    for sp in specs:
        path = vein_path(center, radii, sp, deform, lift)
        n = len(path)
        ra = [rad * (1 - 0.65 * i / (n - 1)) for i in range(n)]
        ra[-1] = max(0.12, ra[-1] * 0.3)
        F += tube(bone, "vein", path, ra, sides=3, hint=(0, 1, 0), phase=90,
                  axis_fn=lambda p, c=np.asarray(center, float): c)
    return F
