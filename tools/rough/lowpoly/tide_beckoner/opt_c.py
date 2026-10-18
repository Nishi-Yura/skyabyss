"""C 案: 潮見の塔 — 長い脚で高く構えた蟹。背に潮位を測る石の塔 (目盛りと灯)、細長い大鋏は招き猫ならぬ『招く鉤』。"""
from __future__ import annotations

from kit import *  # noqa

NAME = "C 潮見の塔"
HINT = 256

BONE = [(52, 58, 66), (96, 106, 114), (142, 152, 156), (186, 194, 192), (226, 230, 220), (244, 246, 238)]
TEAL = [(14, 30, 44), (26, 58, 78), (44, 92, 112), (74, 132, 148), (118, 176, 184)]
STONE = [(22, 32, 38), (40, 58, 64), (66, 90, 94), (100, 126, 128), (142, 164, 160)]
BRASS = [(54, 38, 18), (98, 72, 30), (150, 114, 50), (200, 164, 86), (236, 208, 136)]
PAL = dict(
    mats={
        "bone": dict(ramp=BONE, base=0.56, lit=0.34, noise=0.08, crack=0.14, min=1, streak=0.1, ao=0.1),
        "boneb": dict(ramp=BONE, base=0.38, lit=0.30, noise=0.07, crack=0.06, min=0, ao=0.1),
        "teal": dict(ramp=TEAL, base=0.50, lit=0.36, noise=0.09, crack=0.14, min=0, streak=0.1,
                     stain=dict(ramp=[(150, 114, 50), (200, 164, 86), (236, 208, 136)], thr=1.05, up=0.2, conc=0.3, low=0.0)),
        "tealb": dict(ramp=TEAL, base=0.32, lit=0.30, noise=0.07, crack=0.06, min=0, ao=0.1),
        "stone": dict(ramp=STONE, base=0.50, lit=0.36, noise=0.10, crack=0.2, min=0, streak=0.1,
                      stain=dict(ramp=[(26, 48, 44), (44, 74, 56), (74, 108, 72)], thr=0.96, up=0.3, conc=0.34, low=0.18)),
        "stoneu": dict(ramp=STONE, base=0.20, lit=0.2, noise=0.05, crack=0, min=0),
        "brass": dict(ramp=BRASS, base=0.52, lit=0.40, noise=0.06, crack=0.0, min=0, stripe=5, axis="u"),
        "tooth": dict(ramp=BONE, base=0.72, lit=0.30, noise=0.05, crack=0, min=2),
        "dark": dict(ramp=[(8, 10, 16), (18, 20, 28), (32, 34, 44)], base=0.4, lit=0.1, noise=0.1, crack=0, min=0),
        "glass": dict(ramp=[(40, 140, 170), (95, 232, 255), (210, 250, 255)], base=0.55, lit=0.5, noise=0.04, crack=0, min=0),
        "eye": dict(ramp=[(8, 10, 16), (18, 20, 28), (32, 34, 44)], base=0.5, lit=0.2, noise=0.05, crack=0, min=0),
    },
    glow_mats=["glass"],
)


def build():
    F = []
    # ================================================================ 甲羅 (細めの丸屋根)
    prof = [(18.0, 24.0, -26.0, 22.0), (24.0, 26.5, -28.0, 23.0), (31.0, 22.0, -23.0, 19.0), (37.0, 14.5, -15.0, 13.0), (39.5, 12.0, -12.0, 11.0)]
    R = [tring(y, r, f, b, n=10, phase=18.0, zig=0.0) for y, r, f, b in prof]
    for i in range(len(R) - 1):
        F += rloft("body", "bone" if i % 2 == 0 else "boneb", [R[i], R[i + 1]], caps=(False, False))
    F += poly("body", "bone", R[-1], np.array([0, 20.0, 0]))
    F += poly("body", "dark", R[0], np.array([0, 30.0, 0]))
    # 真鍮の帯 (甲羅の縁と腰)
    F += rloft("body", "brass", [tring(24.0, 27.2, -28.7, 23.7, n=10, phase=18.0), tring(26.4, 26.0, -27.5, 22.6, n=10, phase=18.0)], caps=(False, False))
    # 前の庇 (兜の庇のような板) と脇の棘
    F += fbox("body", "tealb", (0, 31.5, -25.0), (22.0, 3.0, 9.0), R=rot_x(-26), taper=(0.7, 1.0), cut=0.3)
    for s in (1, -1):
        F += sp("body", "bone", (s * 24.0, 27.0, -10.0), (s * 1, 0.5, -0.4), 16.0, 3.2, sides=4, rings=2, bend=(s * 2, 5, -4))
        F += sp("body", "bone", (s * 22.0, 25.0, 12.0), (s * 1, 0.4, 0.7), 12.0, 3.0, sides=4, rings=2, bend=(s * 2, 4, 4))

    # ================================================================ 潮見の塔 (背)
    def oct_ring(y, r, rot=22.5):
        return [np.array([1.18 * r * math.sin(math.radians(rot + 45 * k)), y, 1.18 * r * math.cos(math.radians(rot + 45 * k))]) for k in range(8)]
    T = [oct_ring(38.5, 12.0), oct_ring(40.0, 12.6), oct_ring(54.0, 9.2)]
    F += rloft("body", "stone", T[0:2], caps=(False, False), mats=["tealb"])
    F += rloft("body", "stone", T[1:3], caps=(False, False))
    F += rloft("body", "brass", [oct_ring(54.0, 9.2), oct_ring(55.4, 9.6)], caps=(False, False))
    F += rloft("body", "brass", [oct_ring(55.4, 9.6), oct_ring(55.4, 13.8, 22.5)], caps=(False, False))      # 見張り台
    F += rloft("body", "stoneu", [oct_ring(55.4, 13.8), oct_ring(53.6, 13.0)], caps=(False, False))
    F += rloft("body", "brass", [oct_ring(55.4, 13.8), oct_ring(56.0, 13.8)], caps=(False, False))
    F += rloft("body", "stone", [oct_ring(56.0, 8.2), oct_ring(63.0, 7.2)], caps=(False, False), mats=["glass"])      # 灯の部屋 (光る)
    F += rloft("body", "brass", [oct_ring(63.0, 8.8), oct_ring(64.4, 8.4)], caps=(False, False))
    F += sp("body", "teal", (0, 64.4, 0), (0, 1, 0), 13.0, 9.9, sides=8, rings=2)
    F += sp("body", "brass", (0, 76.0, 0), (0, 1, 0), 6.0, 1.2, sides=4, rings=1)
    # 見張り台の手すり代わりの小さな歯
    for k in range(8):
        a = math.radians(22.5 + 45 * k + 22.5)
        F += sp("body", "tooth", (16.0 * math.sin(a), 56.0, 16.0 * math.cos(a)), (math.sin(a) * 0.3, 1, math.cos(a) * 0.3), 3.6, 1.1, sides=3, rings=1)
    # 控え壁 (塔と甲羅をつなぐ石の翼)
    for ang in (0, 90, 180, 270):
        a = math.radians(ang)
        d = np.array([math.sin(a), 0, math.cos(a)])
        c = d * 17.0 + np.array([0, 40.0, 0])
        R_ = rot_y(ang) @ rot_x(22)
        F += fbox("body", "stone", c, (3.0, 12.0, 5.0), R=R_, taper=(1.0, 0.4), cut=0.0)

    # ================================================================ 眼柄の目 (細く長く)
    eyes = []
    for s in (1, -1):
        F += tube("eyes", "bone", [(s * 8.5, 33.0, -18.0), (s * 9.5, 42.0, -22.0), (s * 10.0, 49.0, -24.0)], [2.0, 1.6, 1.4],
                  sides=4, hint=(0, 0, 1), phase=45, caps=(False, False))
        F += ellipsoid("eyes", "eye", (s * 10.0, 51.4, -24.6), (4.4, 4.0, 3.8), segs=6, rings=1)
        eyes.append(dict(c=(s * 10.0, 51.4, -24.6), r=4.2, dir=(s * 0.1, 0, -1), a=3.4, b=2.0, tilt=0.15))

    # ================================================================ 脚 (高い膝のクモのような脚)
    for s, side in ((1, "r"), (-1, "l")):
        for i, (z0, dz) in enumerate(((-14.0, -14.0), (0.0, -3.0), (14.0, 12.0)), start=1):
            bone = f"leg_{side}{i}"
            hip = (s * 18.0, 24.0, z0 * 0.8)
            path = [hip, (s * 27.0, 30.0, z0 * 0.9 + dz * 0.15), (s * 42.0, 46.0, z0 + dz * 0.35),
                    (s * 54.0, 24.0, z0 + dz * 0.7), (s * 59.0, 8.0, z0 + dz * 0.9)]
            F += tube(bone, "bone", path[:3], [4.2, 3.7, 3.4], [3.7, 3.2, 3.0], sides=4, hint=(0, 0, 1), phase=45, caps=(False, False))
            F += tube(bone, "boneb", path[2:], [3.4, 3.0, 2.2], [3.0, 2.6, 1.9], sides=4, hint=(0, 0, 1), phase=45, caps=(False, False))
            F += tube(bone, "tealb", [path[4], (s * 60.5, 0.4, z0 + dz)], [1.9, 0.0], [1.6, 0.0], sides=4, hint=(0, 0, 1), phase=45, caps=(False, False))
            F += fbox(bone, "brass", path[2], (5.6, 5.6, 5.6), R=rot_z(-s * 30), taper=(0.8, 0.8), cut=0.3)

    # ================================================================ 大鋏 (右 = +x): 長い腕と細長い鉤の指
    B, PB = "claw_big", "claw_big_pincer"
    F += tube(B, "teal", [(22.0, 28.0, -4.0), (36.0, 32.0, -7.0), (46.0, 30.0, -20.0), (47.0, 27.0, -34.0)], [7.0, 7.4, 6.8, 6.2], [6.4, 7.0, 6.4, 5.8],
              sides=6, hint=(0, 1, 0), phase=0, caps=(False, False))
    F += fbox(B, "brass", (46.0, 30.0, -20.0), (14.0, 14.0, 4.0), R=rot_y(40), taper=(0.9, 0.9), cut=0.35)
    for k in (-26, -31):
        F += fbox(B, "brass", (47.0, 28.5, k), (12.0, 12.0, 2.0), taper=(0.9, 0.9), cut=0.4)
    secs = [((47.0, 27.0, -34.0), 14.0, 16.0), ((46.0, 25.0, -44.0), 19.0, 26.0), ((45.0, 23.5, -56.0), 20.0, 28.0),
            ((44.0, 22.0, -66.0), 15.0, 20.0), ((43.0, 21.5, -73.0), 10.0, 13.0)]
    F += rings_along(B, "teal", secs, (1, 0, 0), (0, 1, 0), caps=(False, True), cut=0.42)
    # 背の骨の板 (列)
    for k in range(4):
        F += fbox(B, "bone", (45.5, 40.0 - 0.5 * k, -42.0 - 8.0 * k), (7.0 - 0.8 * k, 7.0 - k, 2.4), R=rot_x(16), taper=(0.5, 0.7), cut=0.3)
    # 固定指 (下): 長く上へ反る
    low = [(43.0, 17.0, -70.0), (43.0, 13.0, -84.0), (43.0, 15.0, -98.0), (43.0, 22.0, -110.0)]
    F += tube(B, "bone", low[:3], [5.0, 4.4, 3.6], [4.4, 3.6, 3.0], sides=4, hint=(1, 0, 0), phase=45, caps=(False, False))
    F += tube(B, "bone", [low[2], low[3]], [3.6, 2.8], [3.0, 2.2], sides=4, hint=(1, 0, 0), phase=45, caps=(False, False))
    F += tube(B, "glass", [low[3], (43.0, 30.0, -114.5)], [2.8, 0.0], [2.2, 0.0], sides=4, hint=(1, 0, 0), phase=45, caps=(False, False))
    for z, y in ((-77, 17.0), (-85, 15.8), (-93, 16.6), (-101, 19.0)):
        F += sp(B, "tooth", (43.0, y, z), (0, 1, -0.3), 5.0, 1.8, sides=3)
    F += fbox(B, "brass", (43.0, 14.0, -84.0), (11.0, 8.0, 2.4), taper=(0.9, 0.9), cut=0.3)
    # 可動指 (上): 長く下へ曲がる
    up = [(43.0, 30.0, -60.0), (43.0, 31.5, -76.0), (43.0, 28.0, -94.0), (43.0, 19.0, -110.0)]
    F += tube(PB, "bone", up[:3], [5.4, 4.6, 3.8], [4.8, 3.8, 3.0], sides=4, hint=(1, 0, 0), phase=45, caps=(True, False))
    F += tube(PB, "bone", [up[2], up[3]], [3.8, 2.8], [3.0, 2.2], sides=4, hint=(1, 0, 0), phase=45, caps=(False, False))
    F += tube(PB, "glass", [up[3], (43.0, 11.0, -114.5)], [2.8, 0.0], [2.2, 0.0], sides=4, hint=(1, 0, 0), phase=45, caps=(False, False))
    for z, y in ((-70, 25.8), (-80, 25.0), (-90, 22.6), (-99, 18.0)):
        F += sp(PB, "tooth", (43.0, y, z), (0, -1, -0.3), 4.8, 1.7, sides=3)
    F += fbox(PB, "brass", (43.0, 31.0, -72.0), (10.0, 6.0, 2.4), taper=(0.9, 0.9), cut=0.3)

    # ================================================================ 小鋏 (左)
    C = "claw_small"
    F += tube(C, "teal", [(-21.0, 27.0, -6.0), (-31.0, 24.0, -12.0), (-35.0, 17.0, -22.0)], [4.2, 4.0, 3.6], [3.8, 3.6, 3.2], sides=6,
              hint=(0, 1, 0), caps=(False, False))
    F += rings_along(C, "teal", [((-35.5, 15.0, -24.0), 7.0, 9.0), ((-37.0, 13.5, -31.0), 10.0, 13.0), ((-37.0, 12.5, -38.0), 7.0, 9.0)],
                     (1, 0, 0), (0, 1, 0), caps=(False, True), cut=0.4)
    F += tube(C, "bone", [(-37.0, 16.0, -36.0), (-37.0, 16.5, -46.0), (-37.0, 12.0, -56.0)], [2.8, 2.4, 0], [2.4, 2.0, 0], sides=4,
              hint=(1, 0, 0), phase=45, caps=(False, False))
    F += tube(C, "bone", [(-37.0, 9.0, -36.0), (-37.0, 9.0, -46.0), (-37.0, 12.0, -56.0)], [2.8, 2.4, 0], [2.4, 2.0, 0], sides=4,
              hint=(1, 0, 0), phase=45, caps=(False, False))
    return F, eyes, extra


def extra(c, col, glow):
    P, N, mats, owned, bones = c["P"], c["N"], c["mats"], c["owned"], c["bones"]
    # 塔の潮位の目盛り (南=-Z 向きの面の 2 本の帯に、2.5 単位ごとの横線)
    tower = (bones == "body") & (mats == "stone") & owned & (P[..., 1] > 40) & (P[..., 1] < 54.5)
    for cx_ in (-4.0, 4.0):
        face = tower & (N[..., 2] < -0.5) & (np.abs(P[..., 0] - cx_) < 1.7)
        yi = np.floor((P[..., 1] - 40.0) * 2.0 / 2.5).astype(int)
        big = (yi % 2 == 0)
        tick = face & ((P[..., 1] * 2.0) % 2.5 < 0.7)
        long_ = tick & big
        red = (P[..., 1] > 49.5)
        col = np.where((tick & ~big & (np.abs(P[..., 0] - cx_) < 1.0))[..., None], np.array(BRASS[3], float), col)
        col = np.where(long_[..., None], np.where(red[..., None], np.array([210, 70, 60], float), np.array(BRASS[4], float)), col)
    return col, glow
