"""C 案: 珊瑚の兜 — 低く広い円い殻 (青緑の石灰と真珠層の縁) に珊瑚の林が生え、頂の開いた二枚貝に真珠が据わる。
下から白い宿借りが覗き、平たい盾のような両のはさみで顔を守る。横に広く重い輪郭。"""
from __future__ import annotations

from crab import *  # noqa

NAME = "C 珊瑚の兜"
HINT = 256

STONE = [(16, 40, 50), (26, 62, 74), (42, 90, 100), (70, 124, 128), (112, 164, 160)]
NACRE = [(110, 104, 124), (164, 158, 178), (210, 204, 220), (238, 234, 244)]
NACREIN = [(150, 104, 128), (204, 150, 168), (236, 196, 206), (252, 228, 232)]
CREAM = [(96, 80, 64), (156, 138, 114), (208, 192, 164), (238, 228, 204), (252, 246, 232)]
ORANGE = [(110, 44, 20), (176, 82, 36), (226, 128, 60), (248, 176, 104)]
PINK = [(120, 36, 70), (186, 70, 108), (232, 118, 146), (252, 176, 188)]
GOLD = [(120, 70, 20), (186, 120, 40), (232, 176, 72), (252, 220, 140)]
PURPLE = [(56, 30, 80), (96, 56, 130), (146, 96, 180), (196, 150, 220)]
PEARL = [(176, 196, 214), (214, 232, 244), (240, 250, 255), (255, 255, 255)]
PAL = dict(
    mats={
        "shell": dict(ramp=STONE, base=0.55, lit=0.32, noise=0.1, crack=0.2, min=0, streak=0.08,
                      stain=dict(ramp=[(150, 146, 166), (196, 192, 208), (226, 222, 234)], thr=0.93, up=0.2, conc=0.2, low=0.1)),
        "rim": dict(ramp=NACRE, base=0.6, lit=0.3, noise=0.06, crack=0.06, min=0),
        "lip": dict(ramp=NACREIN, base=0.62, lit=0.30, noise=0.06, crack=0, min=0),
        "dark": dict(ramp=[(8, 12, 18), (16, 24, 32), (28, 40, 50)], base=0.4, lit=0.1, noise=0.1, crack=0, min=0),
        "pearl": dict(ramp=PEARL, base=0.62, lit=0.45, noise=0.04, crack=0, min=0),
        "crab": dict(ramp=CREAM, base=0.58, lit=0.30, noise=0.08, crack=0.12, min=1, ao=0.1),
        "crab2": dict(ramp=ORANGE, base=0.5, lit=0.30, noise=0.06, crack=0.05, min=0),
        "claw": dict(ramp=CREAM, base=0.62, lit=0.30, noise=0.08, crack=0.2, min=1, streak=0.1,
                     stain=dict(ramp=ORANGE[1:], thr=0.9, up=0.0, conc=0.2, low=0.0)),
        "tip": dict(ramp=ORANGE, base=0.5, lit=0.30, noise=0.05, crack=0, min=0),
        "coral1": dict(ramp=PINK, base=0.6, lit=0.3, noise=0.08, crack=0, min=0),
        "coral2": dict(ramp=GOLD, base=0.6, lit=0.3, noise=0.08, crack=0, min=0),
        "coral3": dict(ramp=PURPLE, base=0.6, lit=0.3, noise=0.08, crack=0, min=0),
        "barn": dict(ramp=NACRE, base=0.55, lit=0.32, noise=0.12, crack=0.1, min=0),
        "eye": dict(ramp=[(8, 10, 16), (18, 20, 28), (32, 34, 44)], base=0.5, lit=0.2, noise=0.05, crack=0, min=0),
    },
    glow_mats=["pearl"],
)


def dome_ring(y, rx, zf, zb, zc, n=12, wob=0.0, seed=0):
    pts = []
    for k in range(n):
        a = math.radians(360.0 * k / n + 15)
        s, c = math.sin(a), math.cos(a)
        w = 1 + wob * math.sin(k * 2.3 + seed)
        pts.append(V(rx * s * w, y, zc + (-zf * c if c >= 0 else zb * -c) * w))
    return pts


def build():
    F = []
    # ================================================================ 円い兜の殻 (低く広い)
    zc = 4.0
    rings = [  # (y, rx, 前, 後ろ)
        (13.0, 25.0, 21.0, 22.0),
        (16.5, 26.5, 22.5, 23.5),
        (24.0, 25.0, 21.0, 22.5),
        (31.0, 20.0, 16.5, 18.5),
        (36.0, 12.0, 10.0, 12.0),
        (38.2, 4.0, 3.5, 4.5),
    ]
    R = [dome_ring(y, rx, zf, zb, zc, wob=0.05 if i in (2, 3) else 0.0, seed=i) for i, (y, rx, zf, zb) in enumerate(rings)]
    F += rloft("shell", "rim", [R[0], R[1]], caps=(False, False))
    F += rloft("shell", "shell", R[1:], caps=(False, True))
    # 殻の下の口 (暗い中) と、正面の切り欠き (体の出る所)
    F += poly("shell", "dark", R[0], V(0, 30.0, zc))
    # 縁の真珠層の棘 (周りに 8 本、低く外へ)
    for k in range(10):
        a = math.radians(36 * k + 18)
        d = V(math.sin(a), 0.0, -math.cos(a))
        if abs(math.degrees(a) % 360) < 40 or abs(math.degrees(a) % 360) > 320:
            continue
        p = V(d[0] * 26.0, 16.0, zc + d[2] * (22.5 if d[2] < 0 else 23.5))
        F += sp("shell", "rim", p, nrm(d + V(0, -0.15, 0)), 5.5, 2.2, sides=4, rings=1)
    # ================================================================ 珊瑚の林
    def surf(x, z, lift=0.0):
        r2 = math.hypot(x / 25.0, (z - zc) / 21.5)
        return V(x, float(np.interp(r2, [0, 0.15, 0.46, 0.77, 0.96, 1.02], [38.6, 38.2, 36.0, 31.0, 24.0, 16.5])) + lift, z)
    # 低い螺旋の巻き (月日貝のような平たい巻貝の印)
    spiral = []
    for i in range(15):
        t = i / 14
        th = math.radians(200 + 560 * t)
        rr = 0.12 + 0.8 * t
        spiral.append(surf(25.0 * rr * math.sin(th), zc - 21.5 * rr * math.cos(th), 0.4))
    F += tube("shell", "rim", spiral, [1.0] + [1.7] * 13 + [0.8], sides=4, hint=(0, 1, 0), phase=45)
    corals = [  # (位置 (x, z), 材, 長さ, 傾き)
        ((-15, -4), "coral1", 21.0, (-0.6, 1, -0.2)), ((15, 10), "coral1", 19.0, (0.6, 1, 0.3)),
        ((-9, 14), "coral2", 15.0, (-0.3, 1, 0.6)), ((14, -6), "coral3", 16.0, (0.6, 1, -0.3)),
        ((-19, 8), "coral3", 13.0, (-0.9, 1, 0.2)), ((4, 17), "coral2", 13.0, (0.1, 1, 0.7)),
    ]
    for i, ((x, z), m, ln, d) in enumerate(corals):
        F += coral("shell", m, surf(x, z, -1.0), d, ln, 2.6,
                   [(0.4, 30 + 70 * i, 0.55, 45), (0.6, 200 + 50 * i, 0.5, 40), (0.8, 110 + 30 * i, 0.4, 35)],
                   sides=4, rings=2, bsides=4)
    # 脳珊瑚の塊
    for (x, z, r) in ((-6, -12, 4.0), (19, -2, 3.4), (-20, -4, 3.0)):
        r2 = math.hypot(x / 26.0, (z - zc) / 22.0)
        y = 38.0 - 24.0 * r2 ** 1.6
        F += ellipsoid("shell", "coral2", (x, y + 0.6, z), (r, r * 0.7, r), segs=6, rings=2)
    for (x, z) in ((-12, -14), (10, -14), (22, 4), (-22, 12), (8, 22)):
        r2 = math.hypot(x / 26.0, (z - zc) / 22.0)
        y = 38.0 - 24.0 * r2 ** 1.6
        F += sp("shell", "barn", (x, y, z), nrm(V(x, 30, z - zc)), 2.6, 1.6, sides=5, rings=1)
    # ================================================================ 頂の二枚貝と真珠
    pc = pv("pearl")
    F += tube("shell", "rim", [V(0, 36.0, -1.0), pc + V(0, -6.0, 0)], [5.0, 4.0], sides=8, hint=(0, 0, 1), phase=22.5)
    for s in (1, -1):   # 開いた二枚の殻 (扇の帆立のような畝)
        hinge = pc + V(0, -6.0, 3.0)
        for k in range(5):
            a = math.radians(-50 + 25 * k)
            tip = hinge + V(s * 9.5 * math.cos(a) * 0.9, 7.5 + 2.5 * math.cos(a), -9.0 * math.sin(a) - 1.0)
            F += tube("shell", "lip" if k % 2 else "rim", [hinge, (hinge + tip) / 2 + V(s * 1.5, -1.0, 0), tip],
                      [1.2, 2.2, 0.6], [0.8, 1.2, 0.5], sides=4, hint=(0, 1, 0), phase=45)
    F += pearl_ball(6.4)
    # ================================================================ 宿借りの体 (白い)
    hf, eyes = head_part("crab", "crab2", eye_h=8.0, stalk_mat="crab2", wide=1.2, antenna=12.0, ant_mat="crab2")
    F += hf
    # ---- 大きいはさみ (平たい盾。顔の前に折って構える)
    B, PB = "claw_r", "claw_r_pincer"
    s0 = pv(B)
    F += tube(B, "claw", [s0, s0 + V(6.0, 1.0, -7.0), V(19.0, 17.0, -24.0)], [3.6, 3.8, 4.0], [3.0, 3.2, 3.4], sides=6,
              hint=(0, 1, 0), phase=0)
    # 盾の掌: 内へ傾いた、幅広で薄い板
    Rp = rot_y(-18)
    F += rings_along(B, "claw", [(V(19.0, 17.0, -24.0), 8.0, 14.0), (V(18.0, 17.5, -30.0), 7.0, 19.0), (V(17.0, 17.0, -37.0), 6.0, 17.0),
                                 (V(16.5, 16.0, -41.0), 4.5, 12.0)],
                     Rp @ V(1, 0, 0), V(0, 1, 0), caps=(True, True), cut=0.35)
    for (y, z) in ((24.0, -30.0), (24.0, -36.0), (10.0, -32.0)):    # 縁の棘
        F += sp(B, "tip", (18.0, y, z), (0.2, 1 if y > 17 else -1, -0.2), 3.0, 1.3, sides=4, rings=1)
    for (y, z) in ((19.0, -29.0), (15.0, -35.0), (20.5, -38.0)):
        F += sp(B, "tip", (22.0 - (z + 24) * 0.25, y, z), (1, 0.1, 0.1), 1.8, 1.2, sides=4, rings=1)
    low = [V(16.5, 11.0, -41.0), V(15.5, 10.5, -46.0), V(14.5, 12.0, -49.0)]
    F += tube(B, "claw", low, [3.0, 2.4, 0.0], [2.2, 1.8, 0.0], sides=4, hint=(1, 0, 0), phase=45)
    pp = pv(PB)
    F += tube(PB, "claw", [pp, pp + V(-3.0, 0.0, -7.0), pp + V(-5.0, -4.0, -12.0)], [3.0, 2.6, 1.8], [2.4, 2.0, 1.4], sides=4,
              hint=(1, 0, 0), phase=45, caps=(True, False))
    F += tube(PB, "tip", [pp + V(-5.0, -4.0, -12.0), pp + V(-6.0, -8.0, -13.5)], [1.8, 0.0], [1.4, 0.0], sides=4,
              hint=(1, 0, 0), phase=45)
    # ---- 小さいはさみ (平たい盾の小さい版)
    C = "claw_l"
    c0 = pv(C)
    F += tube(C, "claw", [c0, c0 + V(-4.0, 0.5, -6.0), c0 + V(-6.0, 1.5, -10.0)], [2.8, 2.8, 3.0], sides=5, hint=(0, 1, 0))
    F += rings_along(C, "claw", [(c0 + V(-6.0, 1.5, -10.0), 5.0, 9.0), (c0 + V(-5.5, 1.5, -15.0), 4.5, 11.0),
                                 (c0 + V(-5.0, 1.0, -19.0), 3.5, 8.0)], rot_y(18) @ V(1, 0, 0), V(0, 1, 0), caps=(True, True), cut=0.35)
    for dy in (2.0, -2.0):
        F += tube(C, "tip", [c0 + V(-5.0, 1.0 + dy, -19.0), c0 + V(-4.5, 1.0 + dy * 0.5, -23.0)], [1.4, 0.0], [1.1, 0.0],
                  sides=4, hint=(1, 0, 0), phase=45)
    # ---- 脚 (短く太く、横へ張る)
    for s, side in ((1, "r"), (-1, "l")):
        for i, foot in enumerate(((27.0, 0.0, -16.0), (30.0, 0.0, -3.0), (28.0, 0.0, 11.0)), start=1):
            F += leg(f"leg_{side}{i}", V(s * foot[0], foot[1], foot[2]), knee_up=5.0, r0=2.8, mats=("crab", "crab2"))
    return F, eyes, None
