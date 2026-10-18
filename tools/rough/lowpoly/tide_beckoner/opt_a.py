"""A 案: 瓦の大蟹 — 重ね瓦の段々の甲羅、赤茶の分厚い大鋏 (鋸歯の刃)、石板の背びれ、藤壺。広く低く重い。"""
from __future__ import annotations

from kit import *  # noqa

NAME = "A 瓦の大蟹"
HINT = 256

SLATE = [(24, 30, 44), (46, 56, 76), (76, 90, 114), (112, 128, 152), (150, 166, 188)]
SHELL = [(46, 20, 20), (88, 36, 28), (132, 60, 38), (172, 90, 54), (204, 122, 74), (226, 156, 104)]
BARN = [(96, 90, 82), (150, 142, 128), (196, 188, 170), (228, 222, 202)]
PAL = dict(
    mats={
        "tile": dict(ramp=SLATE, base=0.50, lit=0.34, noise=0.07, crack=0.22, min=0, streak=0.06,
                     stain=dict(ramp=[(26, 44, 36), (44, 70, 46), (70, 100, 58), (104, 134, 78)], thr=0.95, up=0.34, conc=0.34, low=0.16)),
        "tileu": dict(ramp=SLATE, base=0.16, lit=0.2, noise=0.05, crack=0, min=0),
        "tilecap": dict(ramp=SLATE, base=0.70, lit=0.30, noise=0.06, crack=0.30, min=1),
        "shell": dict(ramp=SHELL, base=0.55, lit=0.30, noise=0.09, crack=0.18, min=1, streak=0.12, ao=0.12),
        "shellb": dict(ramp=SHELL, base=0.34, lit=0.30, noise=0.07, crack=0.1, min=0, ao=0.1),
        "tooth": dict(ramp=BARN, base=0.62, lit=0.30, noise=0.05, crack=0, min=0),
        "barn": dict(ramp=BARN, base=0.55, lit=0.32, noise=0.12, crack=0.1, min=0),
        "dark": dict(ramp=[(8, 10, 16), (18, 20, 28), (32, 34, 44)], base=0.4, lit=0.1, noise=0.1, crack=0, min=0),
        "glass": dict(ramp=[(40, 140, 170), (95, 232, 255), (210, 250, 255)], base=0.55, lit=0.5, noise=0.04, crack=0, min=0),
        "eye": dict(ramp=[(8, 10, 16), (18, 20, 28), (32, 34, 44)], base=0.5, lit=0.2, noise=0.05, crack=0, min=0),
    },
    glow_mats=["glass"],
)


def build():
    F = []
    # ================================================================ 甲羅 (瓦の段)
    rows = [  # (y0, rx0, zf0, zb0, lip, y1, rx1, zf1, zb1)
        (11.0, 34.0, -32.0, 27.0, 3.0, 20.0, 29.0, -27.0, 23.0),
        (18.0, 32.0, -30.0, 25.5, 2.6, 26.0, 25.0, -23.5, 20.0),
        (24.6, 27.5, -25.5, 22.0, 2.4, 32.0, 20.5, -19.0, 16.0),
        (30.6, 22.5, -20.5, 18.0, 2.2, 37.0, 15.0, -13.5, 12.0),
        (35.8, 17.5, -15.0, 14.0, 2.0, 42.5, 8.0, -7.5, 7.0),
    ]
    prev = None
    for i, (y0, r0, f0, b0, lip, y1, r1, f1, b1) in enumerate(rows):
        bot = tring(y0, r0, f0, b0, n=12, zig=0.14)
        lipr = tring(y0 + lip, r0 * 0.985, f0 * 0.985, b0 * 0.985, n=12, zig=0.14)
        topr = tring(y1, r1, f1, b1, n=12, zig=0.06)
        if prev is not None:
            F += rloft("body", "tileu", [prev, bot], caps=(False, False))
        F += rloft("body", "tilecap", [bot, lipr], caps=(False, False))
        F += rloft("body", "tile", [lipr, topr], caps=(False, False))
        prev = topr
    F += poly("body", "tilecap", prev, np.array([0, 20.0, 0]))
    F += poly("body", "dark", tring(11.0, 34.0, -32.0, 27.0, n=12), np.array([0, 30.0, 0]))
    # 甲羅の縁の角 (左右 2 本ずつ)
    for s in (1, -1):
        F += sp("body", "tilecap", (s * 31.0, 19.0, -14.0), (s * 1.0, 0.45, -0.5), 13.0, 4.2, sides=4, rings=2, bend=(s * 3, 5, -4))
        F += sp("body", "tilecap", (s * 30.0, 18.0, 10.0), (s * 1.0, 0.4, 0.6), 10.0, 3.6, sides=4, rings=2, bend=(s * 2, 4, 4))

    # 背の石板 (墓石のように後ろへ傾く)
    for i, (z, h, w) in enumerate(((-6, 12.0, 11.0), (2, 14.5, 12.5), (10, 11.0, 10.0))):
        F += fbox("body", "tilecap", (0, 40.0 + h / 2 - 1.5, z), (w, h, 3.0), R=rot_x(16), taper=(0.55, 0.7), cut=0.35)
    for s in (1, -1):
        for z, h, w, a in ((-9, 7.0, 6.0, 12), (5, 8.5, 6.5, 14)):
            F += fbox("body", "tilecap", (s * 14.0, 33.0 + h / 2, z), (w, h, 2.4), R=rot_x(16) @ rot_z(-s * 18), taper=(0.55, 0.7), cut=0.3)

    # 藤壺 (甲羅の上の小さな円錐)
    def barn(x, y, z, r=1.7, ln=3.2):
        n = nrm(np.array([x, 0.0, z]) * np.array([1.0, 0, 1.0]) + np.array([0, 38.0, 0]) * 0.0 + np.array([0, 0.9 * math.hypot(x, z) * 0.5 + 6, 0]))
        return sp("body", "barn", (x, y, z), n, ln, r, sides=5, rings=1)
    for (x, y, z) in ((-23, 25.2, -17), (-26, 21.4, -8), (24, 22.5, 9), (-13, 31.5, 9),
                      (12, 33.5, -15), (28.5, 17.3, 13), (17, 27.0, -20), (-18, 28, 12)):
        F += barn(x, y, z)

    # ================================================================ 眼柄の目
    eyes = []
    for s in (1, -1):
        F += tube("eyes", "shell", [(s * 9.0, 32.0, -19.0), (s * 9.6, 39.0, -21.0), (s * 10.0, 43.5, -22.5)], [3.0, 2.4, 2.1],
                  sides=4, hint=(0, 0, 1), phase=45, caps=(False, False))
        F += ellipsoid("eyes", "eye", (s * 10.0, 46.2, -23.2), (5.0, 4.4, 4.2), segs=6, rings=1)
        eyes.append(dict(c=(s * 10.0, 46.2, -23.2), r=4.6, dir=(s * 0.12, 0, -1), a=3.8, b=2.1, tilt=0.2))

    # ================================================================ 脚 6 本
    for s, side in ((1, "r"), (-1, "l")):
        for i, (z0, dz) in enumerate(((-14.0, -11.0), (0.0, -2.0), (14.0, 9.0)), start=1):
            bone = f"leg_{side}{i}"
            path = [(s * 24.0, 12.5, z0), (s * 41.0, 22.5, z0 + dz * 0.3), (s * 50.0, 17.0, z0 + dz * 0.6), (s * 55.0, 8.0, z0 + dz * 0.85)]
            F += tube(bone, "shell", path[:3], [4.6, 4.0, 3.8], [4.0, 3.5, 3.3], sides=4, hint=(0, 0, 1), phase=45, caps=(False, False))
            F += tube(bone, "shellb", [path[2], path[3], (s * 57.0, 0.4, z0 + dz)], [3.8, 2.8, 0.0], [3.3, 2.4, 0.0], sides=4,
                      hint=(0, 0, 1), phase=45, caps=(False, False))
            F += fbox(bone, "tilecap", (s * 42.0, 25.0, z0 + dz * 0.3), (6.0, 3.0, 5.0), R=rot_z(-s * 24), taper=(0.7, 0.8))

    # ================================================================ 大鋏 (右 = +x)
    B, PB = "claw_big", "claw_big_pincer"
    F += tube(B, "shell", [(24.0, 27.0, -4.0), (35.0, 26.0, -18.0), (40.5, 24.0, -33.0)], [9.0, 10.5, 9.5], [8.5, 10.0, 9.0], sides=6,
              hint=(0, 1, 0), phase=0, caps=(False, False))
    for k, (z, a) in enumerate(((-8, 0), (-15, 4), (-22, 8))):       # 肩の瓦
        F += fbox(B, "tile", (31.0 + 2.2 * k, 34.5 - 0.5 * k, z), (17.0 - 2 * k, 2.2, 9.0), R=rot_x(-12) @ rot_z(-14), taper=(0.8, 0.9))
    F += sp(B, "shell", (45.0, 29.0, -24.0), (1, 0.7, -0.2), 11.0, 3.8, sides=4, rings=2, bend=(2, 4, -3))
    secs = [((40.5, 23.0, -33.0), 16.0, 18.0), ((42.5, 22.0, -45.0), 27.0, 32.0), ((44.0, 22.0, -57.0), 29.0, 36.0),
            ((43.5, 20.5, -67.0), 23.0, 28.0), ((43.0, 19.5, -74.0), 15.0, 17.0)]
    F += rings_along(B, "shell", secs, (1, 0, 0), (0, 1, 0), caps=(False, True), cut=0.45)
    # 外側の石の盾板と、背の石板、背の角
    for k, (yy, hh, zz, ww) in enumerate(((32.5, 12.0, -54.0, 21.0), (23.0, 12.5, -56.0, 24.0), (13.5, 11.0, -57.0, 19.0))):
        F += fbox(B, "tilecap" if k != 1 else "tile", (57.8 + 0.4 * k, yy, zz), (2.4, hh, ww), R=rot_z(11) @ rot_x(-3), taper=(0.9, 0.85), cut=0.35)
        F += sp(B, "barn", (59.4 + 0.4 * k, yy - 1, zz + (4 if k == 1 else -3)), (1, 0.25, 0), 3.0, 1.5, sides=5, rings=1)
    for k in range(3):
        F += fbox(B, "tilecap", (44.0, 42.0 + 0.4 * k, -44.0 - 9.0 * k), (12 - 1.4 * k, 8.0 + k, 2.6), R=rot_x(14), taper=(0.6, 0.7), cut=0.3)
    for k, z in enumerate((-40, -52, -64)):
        F += sp(B, "shell", (53.0, 38.0 - 1.5 * k, z), (1, 0.8, 0), 6.5, 2.8, sides=4, rings=1, bend=(1, 2, 0))
    for (x, y, z) in ((57.5, 36.0, -66.0), (31.0, 30.0, -52.0), (31.0, 18.0, -58.0), (44.0, 41.0, -70.0)):
        F += sp(B, "barn", (x, y, z), (1, 0.2, 0) if x > 50 else ((-1, 0.2, 0) if y < 39 else (0, 1, 0)), 3.4, 1.8, sides=5, rings=1)
    # 固定指 (下)
    low = [(43.0, 15.5, -70.0), (43.0, 13.0, -81.0), (43.0, 14.0, -93.0), (43.0, 19.5, -103.0)]
    F += tube(B, "shellb", low[:3], [7.0, 6.0, 4.6], [5.4, 4.6, 3.4], sides=4, hint=(1, 0, 0), phase=45, caps=(False, False))
    F += tube(B, "shell", [low[2], low[3]], [4.6, 3.6], [3.4, 2.6], sides=4, hint=(1, 0, 0), phase=45, caps=(False, False))
    F += tube(B, "glass", [low[3], (43.0, 25.0, -107.0)], [3.6, 0.0], [2.6, 0.0], sides=4, hint=(1, 0, 0), phase=45, caps=(False, False))
    for z, y in ((-74, 19.4), (-80, 17.6), (-86, 17.6), (-92, 19.2)):
        F += sp(B, "tooth", (43.0, y, z), (0, 1, -0.2), 4.6, 2.2, sides=3)
    # 可動指 (上)
    up = [(43.0, 31.5, -60.0), (43.0, 32.5, -73.0), (43.0, 29.0, -88.0)]
    F += tube(PB, "shell", up, [7.5, 6.4, 5.0], [6.8, 5.4, 3.8], sides=4, hint=(1, 0, 0), phase=45, caps=(True, False))
    F += tube(PB, "shell", [up[2], (43.0, 23.5, -99.0)], [5.0, 3.8], [3.8, 2.8], sides=4, hint=(1, 0, 0), phase=45, caps=(False, False))
    F += tube(PB, "glass", [(43.0, 23.5, -99.0), (43.0, 18.0, -103.0)], [3.8, 0.0], [2.8, 0.0], sides=4, hint=(1, 0, 0), phase=45,
              caps=(False, False))
    for z, y in ((-68, 26.8), (-76, 25.8), (-84, 24.2), (-91, 22.0)):
        F += sp(PB, "tooth", (43.0, y, z), (0, -1, -0.2), 4.2, 2.1, sides=3)

    # ================================================================ 小鋏 (左 = -x)
    C = "claw_small"
    F += tube(C, "shell", [(-24.0, 22.0, -7.0), (-31.0, 21.0, -15.0), (-35.0, 16.0, -24.0)], [4.8, 4.4, 4.0], [4.4, 4.0, 3.6], sides=6,
              hint=(0, 1, 0), caps=(False, False))
    F += fbox(C, "tile", (-30.0, 27.0, -10.0), (10.0, 1.8, 7.0), R=rot_x(-10) @ rot_z(12), taper=(0.8, 0.9))
    F += rings_along(C, "shell", [((-35.5, 14.0, -26.0), 8.0, 10.0), ((-37.0, 13.0, -33.0), 13.0, 15.0), ((-37.0, 12.5, -39.0), 11.0, 12.0)],
                     (1, 0, 0), (0, 1, 0), caps=(False, True), cut=0.4)
    F += tube(C, "shellb", [(-37.0, 16.5, -38.0), (-37.0, 17.0, -46.0), (-37.0, 14.0, -52.0)], [3.2, 2.8, 0], [3.0, 2.4, 0], sides=4,
              hint=(1, 0, 0), phase=45, caps=(False, False))
    F += tube(C, "shellb", [(-37.0, 8.5, -38.0), (-37.0, 8.5, -46.0), (-37.0, 11.0, -52.0)], [3.2, 2.8, 0], [3.0, 2.4, 0], sides=4,
              hint=(1, 0, 0), phase=45, caps=(False, False))
    for z in (-42, -47):
        F += sp(C, "tooth", (-37.0, 11.0, z), (0, 1, 0), 2.4, 1.2, sides=3)
    for (x, y, z) in ((-44.0, 18.0, -30.0), (-43.0, 10.0, -33.0)):
        F += sp(C, "barn", (x, y, z), (-1, 0.1, 0), 2.6, 1.4, sides=5, rings=1)

    return F, eyes, None
