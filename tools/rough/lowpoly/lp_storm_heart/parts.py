"""3 案で共通の、位置が決まっている部品。今の geo の cube の位置・範囲に合わせる。"""
from __future__ import annotations

from common import *  # noqa

SHOULDER = {1: V(5.0, 24.0, 2.0), 2: V(5.0, 25.0, 1.0), 3: V(5.0, 24.0, 2.0)}   # 右の肩 (左は x 反転)


def mx(p, s):
    """左 (s=-1) へ鏡写し。"""
    q = np.array(p, float)
    q[0] *= s
    return q


def side_bone(s):
    return "wing_l" if s < 0 else "wing_r"


def crest_bone(s):
    return "crest_l" if s < 0 else "crest_r"


def aortas(phase, mat="copper", style="a", tip_spark=0):
    """大動脈 3 本。心臓の上へ立つ。"""
    c = HC[phase]
    top = c[1] + 5.4
    F = []
    specs = [((0.0, top - 1.5, -0.3), [(0.4, 3.0, -0.3), (-0.5, 5.2, 0.2), (0.2, 7.2, 0.0)], 1.35),
             ((-2.2, top - 1.0, 0.3), [(-3.4, 2.4, 0.3), (-4.4, 4.4, 0.5)], 0.95),
             ((2.3, top - 1.0, 0.3), [(3.5, 2.4, 0.3), (4.8, 4.2, -0.2)], 0.95)]
    if style == "b":   # 避雷針: まっすぐ、先に玉
        specs = [((0.0, top - 1.5, -0.3), [(0.0, 3.5, -0.3), (0.0, 7.5, -0.3)], 1.1),
                 ((-2.4, top - 1.0, 0.3), [(-3.0, 3.0, 0.3), (-3.6, 6.0, 0.3)], 0.8),
                 ((2.4, top - 1.0, 0.3), [(3.0, 3.0, 0.3), (3.6, 6.0, 0.3)], 0.8)]
    if style == "c":   # 稲妻の形: 折れ曲がる
        specs = [((0.0, top - 1.5, -0.3), [(1.6, 2.6, -0.3), (-1.2, 4.4, 0.0), (1.4, 6.4, 0.2), (0.0, 8.2, 0.0)], 1.25),
                 ((-2.2, top - 1.0, 0.3), [(-3.8, 2.0, 0.3), (-2.8, 3.6, 0.4), (-5.0, 5.4, 0.3)], 0.9),
                 ((2.3, top - 1.0, 0.3), [(3.8, 2.0, 0.3), (2.6, 3.8, 0.3), (4.6, 5.6, 0.0)], 0.9)]
    for i, (rt, offs, r) in enumerate(specs):
        base = V(*rt) + V(c[0], 0, c[2])
        pts = curve([base] + [base + V(*o) for o in offs], 2 * len(offs))
        F += rod("vessel", mat, pts, r, sides=5, hint=(0, 0, 1), taper=True)
        if tip_spark:
            F += node("vessel", "spark", pts[-1] + V(0, 0.2, 0), 0.8 + 0.2 * tip_spark, 20 * i)
    return F


def veins(phase, mat="sinew", n=4, r=0.4, lift=0.5, seed=1):
    F = []
    for k in range(n):
        ph = 25 + 360.0 / n * k + rj(k, 14, seed)
        pts = [hsurf(phase, th, ph + 14 * math.sin(j * 1.2 + k + seed), lift=lift) for j, th in enumerate((16, 38, 62, 88, 112, 134))]
        F += tube("heart_vein", mat, pts, r, r, sides=3, hint=(0, 0, 1), phase=90)
    return F


def clump_sites(phase):
    return clusters(phase, "shed", 5.0)
