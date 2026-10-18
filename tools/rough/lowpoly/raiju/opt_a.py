"""A 案: 嵐の狼 — 脚の長い走る狼。背に後ろへ倒れた水晶の鬣、胸に白い毛、折れ線の尾の先に水晶の刃。"""
from __future__ import annotations

from kit import *  # noqa

NAME = "A 嵐の狼"
HINT = 256

SLATE = [(16, 20, 34), (30, 38, 58), (50, 62, 90), (78, 94, 126), (112, 130, 164), (150, 170, 200)]
FROST = [(60, 76, 104), (104, 122, 154), (150, 170, 200), (196, 212, 234), (236, 244, 255)]
DARKC = [(8, 10, 18), (16, 20, 32), (28, 34, 50)]
COPPER = [(58, 28, 20), (110, 54, 28), (166, 92, 42), (214, 134, 62), (248, 190, 108)]
CRYS = [(30, 96, 140), (84, 190, 236), (150, 226, 252), (214, 246, 255)]
PAL = dict(
    mats={
        "fur": dict(ramp=SLATE, base=0.56, lit=0.30, noise=0.05, facevar=0.015, crack=0, min=0, streak=0.14, ao=0.14),
        "furd": dict(ramp=SLATE, base=0.36, lit=0.26, noise=0.08, crack=0, min=0, streak=0.1, ao=0.1),
        "frost": dict(ramp=FROST, base=0.58, lit=0.30, noise=0.08, crack=0, min=0, streak=0.12),
        "dark": dict(ramp=DARKC, base=0.5, lit=0.3, noise=0.12, crack=0, min=0),
        "copper": dict(ramp=COPPER, base=0.62, lit=0.32, noise=0.08, crack=0, min=0),
        "crystal": dict(ramp=CRYS, base=0.42, lit=0.40, noise=0.05, crack=0, min=0, facevar=0.14),
        "eye": dict(ramp=DARKC, base=0.5, lit=0.2, noise=0.05, crack=0, min=0),
    },
    glow_mats=["crystal"],
)


def TOPY(z):
    return float(np.interp(z, [-31, -24, -15, -6, 4, 14, 24, 30], [47, 45, 44, 39.5, 34, 37, 36, 33]))


def build():
    F = []
    sgn = 1 if pv("leg_fl")[0] > 0 else -1

    # ================================================================ 胴 (body: 腰〜腹) と胸 (chest: 肩〜首)
    body_rings = [
        sring(30, 33.0, 23.0, 5.0, 2.4, 2.4),
        sring(24, 36.0, 20.0, 8.4, 3.6, 4.2),
        sring(14, 37.0, 18.0, 10.0, 4.2, 5.2),
        sring(4, 34.0, 22.5, 8.0, 3.4, 3.8),
        sring(-6, 39.5, 14.0, 10.6, 4.2, 5.2),
    ]
    chest_rings = [
        sring(-6, 39.5, 14.0, 10.6, 4.2, 5.2),
        sring(-15, 44.0, 10.0, 12.6, 5.4, 5.0),
        sring(-24, 45.0, 17.0, 10.4, 4.8, 4.2),
        sring(-31, 47.0, 26.0, 8.0, 3.8, 3.6),
    ]
    F += torso("body", "fur", body_rings, caps=(True, False))
    F += torso("chest", "fur", chest_rings, caps=(False, True))
    # 胸と喉の白い毛 (下へ垂れる鋭い房)
    for z, x, y, ln in ((-13, 0, 12.5, 8.0), (-18, 4.5, 13.0, 7.0), (-18, -4.5, 13.0, 7.0), (-23, 2.0, 19.5, 6.0),
                        (-23, -2.0, 19.5, 6.0), (-28, 0, 27.5, 5.5), (-9, 3.5, 14.0, 6.0), (-9, -3.5, 14.0, 6.0),
                        (-4, 4.5, 14.5, 5.0), (-4, -4.5, 14.5, 5.0)):
        F += tuft("chest", "frost", (x, y, z), (x * 0.1, -1, -0.35), ln, 2.3, sides=3)
    # 肩の毛の逆立ち
    for z, x in ((-21, 5.0), (-21, -5.0), (-13, 7.0), (-13, -7.0)):
        F += tuft("chest", "fur", (x, 39.5, z), (x * 0.1, 0.6, 1), 6.5, 2.4, sides=3)

    # ================================================================ 頭
    hd = [
        sring(-27, 48.0, 31.0, 7.6, 4.4, 5.0),
        sring(-34, 50.0, 30.5, 8.8, 4.8, 5.4),
        sring(-41, 45.0, 31.5, 5.8, 2.8, 3.4),
        sring(-47, 41.5, 33.0, 3.6, 1.8, 2.2),
        sring(-52, 39.5, 34.5, 2.4, 1.3, 1.6),
    ]
    F += torso("head", "fur", hd, caps=(True, False))
    F += poly("head", "dark", hd[-1], np.array([0, 37, -40.0]))
    F += fbox("head", "dark", (0, 38.8, -52.2), (3.6, 2.8, 2.4), taper=(0.8, 0.8), cut=0.3)   # 鼻
    for s in (1, -1):
        F += tuft("head", "frost", (s * 8.0, 35.0, -31.0), (s * 0.7, -0.5, 0.6), 8.0, 2.6, sides=3)
        F += tuft("head", "frost", (s * 7.6, 38.5, -35.0), (s * 0.9, -0.3, 0.3), 6.5, 2.2, sides=3)
        # 大きな耳 (外は毛、内は銅)
        F += tube("head", "fur", [(s * 4.6, 48.0, -28.5), (s * 5.8, 56.5, -28.0), (s * 7.0, 64.0, -26.0)], [4.2, 3.0, 0.0], [2.0, 1.5, 0.0],
                  sides=4, hint=(1, 0, 0), phase=45, caps=(False, False))
        F += tube("head", "copper", [(s * 4.4, 49.0, -30.5), (s * 5.5, 56.0, -30.2), (s * 6.7, 62.0, -28.4)], [1.9, 1.3, 0.0], [0.6, 0.5, 0.0],
                  sides=3, hint=(1, 0, 0), phase=90, caps=(False, False))
        F += fbox("head", "furd", (s * 5.4, 48.4, -38.6), (3.8, 1.8, 7.0), R=rot_z(-s * 14) @ rot_y(s * 8), taper=(0.7, 0.8), cut=0.2)   # 眉の稜
    eyes = []
    for s in (1, -1):
        c = (s * 5.6, 44.4, -39.0)
        F += ellipsoid("head", "eye", c, (2.1, 2.1, 2.1), segs=6, rings=1)
        eyes.append(dict(c=c, r=2.2, dir=(s * 0.55, 0.05, -1), a=1.9, b=1.1, tilt=0.45))

    jw = [
        sring(-32, 35.5, 29.5, 4.8, 3.2, 2.8),
        sring(-40, 35.0, 29.5, 4.2, 2.8, 2.2),
        sring(-48, 35.0, 31.8, 2.4, 1.7, 1.3),
        sring(-52, 35.0, 33.2, 1.6, 1.1, 0.9),
    ]
    F += torso("jaw", "furd", jw, caps=(True, True))
    for s in (1, -1):
        F += spike("head", "frost", (s * 2.5, 34.6, -48.5), (0, -1, -0.15), 4.6, 1.0, sides=3, rings=1)
        F += spike("jaw", "frost", (s * 1.9, 34.4, -47.0), (0, 1, -0.2), 3.4, 0.9, sides=3, rings=1)
        F += spike("head", "frost", (s * 3.6, 35.0, -42.5), (0, -1, 0), 3.0, 1.0, sides=3, rings=1)

    # ================================================================ 鬣 (背の水晶: 後ろへ倒れた列)
    for z, h, r in ((-27, 13.0, 2.7), (-21, 16.0, 3.3), (-14, 19.0, 3.6), (-7, 16.0, 3.3), (-1, 13.0, 3.0), (5, 11.0, 2.6),
                    (11, 9.0, 2.2), (17, 7.0, 1.9), (23, 5.5, 1.6)):
        top = TOPY(z)
        F += crystal("mane", "crystal", (0, top - 1.0, z), (0, 1, 0.78), h, r, sides=5, mid=0.33, bend=(0, -1.2, 5.0))
    for z, h, r in ((-18, 9.0, 2.2), (-10, 10.0, 2.4), (-3, 8.0, 2.0), (4, 6.5, 1.8)):
        for s in (1, -1):
            F += crystal("mane", "crystal", (s * 4.8, TOPY(z) - 2.0, z), (s * 0.55, 1, 0.7), h, r,
                         sides=4, mid=0.35, bend=(s * 1.5, -0.5, 3.0))

    # ================================================================ 脚 (太さを胴に合わせる)
    def foreleg(side):
        s = pv("leg_f" + side)[0] * 1.22
        b = "leg_f" + side
        F.extend(limb(b, "fur", [(s, 29.0, -15.0), (s, 21.0, -14.8), (s, 14.0, -14.5)], [6.0, 5.0, 3.6], [5.2, 4.4, 3.2], sides=5, hint=(1, 0, 0), phase=36))
        F.extend(fbox(b, "furd", (s, 21.5, -19.0), (4.4, 8.0, 4.4), R=rot_x(20), taper=(0.6, 0.6), cut=0.25))   # 肘の毛
        lo = b + "_lo"
        F.extend(limb(lo, "furd", [(s, 14.5, -14.5), (s, 8.0, -15.8), (s, 2.5, -17.0)], [3.8, 3.0, 3.2], [3.4, 2.8, 2.8], sides=5, hint=(1, 0, 0), phase=36))
        F.extend(tuft(lo, "frost", (s, 13.0, -12.0), (0, -1, 0.2), 7.0, 2.2, sides=3))
        F.extend(paw(lo, "dark", "copper", (s, 0.0, -20.0), 6.0, 9.0, 3.4, dirz=-1, cl=3.2))

    def hindleg(side):
        s = pv("leg_b" + side)[0] * 1.3
        b = "leg_b" + side
        F.extend(ellipsoid(b, "fur", (s * 1.05, 23.5, 12.5), (6.0, 7.6, 6.6), segs=6, rings=2))
        F.extend(limb(b, "fur", [(s, 24.0, 12.5), (s, 17.0, 10.5), (s, 12.0, 8.5)], [5.6, 4.6, 3.4], [5.0, 4.0, 3.0], sides=5, hint=(1, 0, 0), phase=36))
        lo = b + "_lo"
        F.extend(limb(lo, "furd", [(s, 12.0, 8.5), (s, 9.0, 13.0), (s, 7.5, 17.0)], [3.4, 2.9, 2.5], [3.1, 2.6, 2.2], sides=5, hint=(1, 0, 0), phase=36))
        F.extend(limb(lo, "furd", [(s, 7.5, 17.0), (s, 4.0, 15.5), (s, 2.4, 14.6)], [2.5, 2.3, 2.4], [2.2, 2.0, 2.1], sides=5, hint=(1, 0, 0), phase=36))
        F.extend(tuft(lo, "fur", (s, 9.5, 18.5), (0, 0.15, 1), 4.6, 2.0, sides=3))
        F.extend(paw(lo, "dark", "copper", (s, 0.0, 12.0), 5.6, 9.0, 3.4, dirz=-1, cl=3.2))

    for side in ("l", "r"):
        foreleg(side)
        hindleg(side)

    # ================================================================ 尾 (稲妻の折れ線: 平たい帯が鋭く折れ、先は水晶の刃)
    F += tube("tail", "fur", [(0, 28.0, 27.0), (4.0, 36.5, 37.5), (2.0, 24.0, 46.0)], [3.4, 2.8, 2.2], [6.0, 4.2, 2.8], sides=4,
              hint=(1, 0, 0), phase=45, caps=(False, False))
    F += tube("tail_2", "furd", [(2.0, 24.0, 46.0), (-3.5, 36.0, 54.5), (0, 25.0, 62.0)], [2.4, 2.2, 1.9], [4.6, 3.8, 2.4], sides=4,
              hint=(1, 0, 0), phase=45, caps=(False, False))
    F += tube("tail_3", "copper", [(0, 25.0, 62.0), (2.0, 29.0, 66.0)], [1.9, 1.9], [2.8, 2.4], sides=4, hint=(1, 0, 0), phase=45, caps=(False, False))
    F += crystal("tail_3", "crystal", (2.0, 29.0, 66.0), (0.15, 0.55, 1.0), 14.0, 3.4, sides=4, mid=0.3, bend=(3, 5, 0), r0k=0.8)
    F += crystal("tail", "crystal", (4.0, 38.0, 37.5), (1.0, 0.8, 0.2), 7.0, 1.7, sides=4, mid=0.4)
    F += crystal("tail_2", "crystal", (-3.5, 37.5, 54.5), (-1.0, 0.8, 0.2), 7.5, 1.8, sides=4, mid=0.4)
    F += crystal("tail", "crystal", (1.0, 25.0, 42.0), (0.4, -1.0, 0.3), 6.0, 1.5, sides=4, mid=0.4)

    return F, eyes, extra


def extra(c, col, glow):
    return fur_extra(c, col, glow, vein=None, belly=("fur", FROST),
                     bolts=dict(lines=[[(-20, 42), (-14, 36), (-19, 30), (-11, 24), (-15, 18)], [(19, 36), (12, 30), (17, 26), (9, 20)],
                                       [(-3, 36), (-8, 30), (-2, 26)]],
                               color=(214, 238, 255), w=0.8, mats=["fur", "furd"], glow=True))
