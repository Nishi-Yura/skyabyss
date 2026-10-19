"""宿借りの柔らかい体・脚・はさみ (3 案で形の言葉を変えて使う部品)。"""
from __future__ import annotations

from pk import *  # noqa


def head_part(mat="crab", mat2="crab2", eye_h=11.0, stalk_mat="crab2", wide=1.0, antenna=14.0, ant_mat="crab2"):
    """頭胸 (殻口から出ている前の体)、目柄、触角、口の脚。戻り値: (faces, eyes)。"""
    F = []
    c = pv("head")
    F += ellipsoid("head", mat, c + V(0, 1.0, -2.0), (9.0 * wide, 5.5, 8.0), segs=8, rings=3)
    # 額の殻の板
    F += fbox("head", mat2, c + V(0, 5.8, -4.5), (11.0 * wide, 2.2, 8.0), R=rot_x(-14), taper=(0.75, 0.8), cut=0.35)
    eyes = []
    for s in (1, -1):
        a = c + V(s * 3.0, 4.5, -8.5)
        b = c + V(s * 4.2, 4.5 + eye_h * 0.6, -11.5)
        e = c + V(s * 5.0, 4.5 + eye_h, -13.0)
        F += tube("head", stalk_mat, [a, b, e], [1.5, 1.3, 1.2], sides=4, hint=(0, 0, 1), phase=45)
        F += ellipsoid("head", "eye", e + V(0, 1.6, -0.4), (2.4, 2.6, 2.4), segs=6, rings=2)
        eyes.append(dict(c=tuple(e + V(0, 1.6, -0.4)), r=2.6, dir=(s * 0.2, 0.1, -1), a=1.9, b=1.5, tilt=0.1, bone="head"))
        # 触角 (長い、上と外へ反る)
        F += sp("head", ant_mat, c + V(s * 1.6, 3.0, -10.5), (s * 0.35, 0.55, -1.0), antenna, 0.8, sides=3, rings=2,
                bend=(s * 3.0, 4.0, 2.0))
        # 口の脚 (短い 2 本)
        F += sp("head", mat2, c + V(s * 2.2, -1.5, -9.5), (s * 0.1, -0.6, -1.0), 4.0, 1.2, sides=3, rings=1)
    return F, eyes


def leg(bone, foot, knee_up=6.0, r0=2.4, mats=("crab", "band"), sides=4, joint_mat=None):
    """脚: pivot → 膝 (上) → 足先。節ごとに色を変える (縞の脚)。"""
    p = pv(bone)
    foot = np.asarray(foot, float)
    knee = p + (foot - p) * 0.45 + V(0, knee_up, 0)
    ank = p + (foot - p) * 0.8 + V(0, knee_up * 0.35, 0)
    F = tube(bone, mats[0], [p, knee], [r0, r0 * 0.9], sides=sides, hint=(0, 0, 1), phase=45)
    F += tube(bone, mats[1], [knee, ank], [r0 * 0.9, r0 * 0.75], sides=sides, hint=(0, 0, 1), phase=45)
    F += tube(bone, mats[0], [ank, foot], [r0 * 0.75, 0.0], sides=sides, hint=(0, 0, 1), phase=45)
    if joint_mat:
        F += ellipsoid(bone, joint_mat, knee, (r0 * 1.05, r0 * 1.05, r0 * 1.05), segs=5, rings=1)
    return F
