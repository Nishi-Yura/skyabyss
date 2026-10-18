"""B 案: 珊瑚の山 — 岩のこぶの甲羅に、枝分かれする珊瑚が冠のように生える。大鋏は鹿の角のように枝分かれする指。"""
from __future__ import annotations

from kit import *  # noqa

NAME = "B 珊瑚の山"
HINT = 256

ROCK = [(18, 26, 38), (34, 50, 64), (56, 78, 92), (86, 110, 122), (124, 148, 154)]
CRAB = [(30, 12, 24), (58, 22, 40), (92, 38, 58), (130, 62, 76), (168, 92, 98), (200, 124, 120)]
CORAL = [(168, 58, 72), (216, 96, 88), (242, 140, 112), (252, 190, 150), (255, 230, 196)]
CORALB = [(190, 84, 84), (232, 128, 104), (250, 176, 134), (255, 214, 170), (255, 242, 214)]
PAL = dict(
    mats={
        "rock": dict(ramp=ROCK, base=0.46, lit=0.36, noise=0.12, crack=0.22, min=0, streak=0.08,
                     stain=dict(ramp=[(150, 70, 80), (206, 112, 108), (240, 168, 140), (252, 214, 184)], thr=0.99, up=0.30, conc=0.40, low=0.0)),
        "rock2": dict(ramp=ROCK, base=0.62, lit=0.32, noise=0.12, crack=0.2, min=1),
        "crab": dict(ramp=CRAB, base=0.52, lit=0.32, noise=0.10, crack=0.16, min=1, streak=0.12, ao=0.1),
        "crabb": dict(ramp=CRAB, base=0.34, lit=0.30, noise=0.07, crack=0.08, min=0, ao=0.1),
        "coral": dict(ramp=CORAL, base=0.50, lit=0.40, noise=0.10, crack=0.0, min=0, stripe=7, axis="v"),
        "coralb": dict(ramp=CORALB, base=0.66, lit=0.36, noise=0.08, crack=0.0, min=1),
        "brain": dict(ramp=CORAL, base=0.58, lit=0.40, noise=0.05, crack=0.0, min=0),
        "tooth": dict(ramp=CORALB, base=0.74, lit=0.30, noise=0.05, crack=0, min=1),
        "dark": dict(ramp=[(8, 10, 16), (18, 20, 28), (32, 34, 44)], base=0.4, lit=0.1, noise=0.1, crack=0, min=0),
        "glass": dict(ramp=[(40, 140, 170), (95, 232, 255), (210, 250, 255)], base=0.55, lit=0.5, noise=0.04, crack=0, min=0),
        "eye": dict(ramp=[(8, 10, 16), (18, 20, 28), (32, 34, 44)], base=0.5, lit=0.2, noise=0.05, crack=0, min=0),
    },
    glow_mats=["glass"],
)


def jring(y, rx, zf, zb, seed, j=2.4, dyj=1.0, n=10, phase=18.0, zc=0.0):
    r = tring(y, rx, zf, zb, n=n, phase=phase, zc=zc)
    out = []
    for k, p in enumerate(r):
        c = np.array([0.0, y, zc])
        d = p - c
        d = d * (1 + jit(k + seed * 7, 0.07)) if True else d
        out.append(c + d + np.array([jit(k, j * 0.4, seed), jit(k + 31, dyj, seed), jit(k + 17, j * 0.4, seed)]))
    return out


def build():
    F = []
    # ================================================================ 甲羅 (岩のこぶ)
    R = [jring(11.0, 33.0, -31.0, 26.0, 1), jring(19.0, 31.5, -29.0, 25.0, 2), jring(26.0, 26.0, -24.0, 21.5, 3, dyj=1.6),
         jring(32.5, 19.0, -17.0, 16.0, 4, dyj=1.8), jring(37.5, 10.5, -9.0, 9.5, 5, dyj=1.2)]
    for i in range(len(R) - 1):
        F += rloft("body", "rock" if i % 2 == 0 else "rock2", [R[i], R[i + 1]], caps=(False, False))
    F += poly("body", "rock2", R[-1], np.array([0, 20.0, 0]))
    F += poly("body", "dark", tring(11.0, 33.0, -31.0, 26.0, n=10, phase=18.0), np.array([0, 30.0, 0]))
    # 肩の岩塊
    for s in (1, -1):
        F += cbox("body", "rock2", (s * 25.0, 24.0, -8.0), (13.0, 11.0, 15.0), R=rot_y(s * 20) @ rot_z(-s * 12), taper=0.7, cut=0.4)
        F += cbox("body", "rock", (s * 20.0, 20.0, 14.0), (11.0, 9.0, 12.0), R=rot_y(-s * 15) @ rot_z(s * 8), taper=0.7, cut=0.4)
    # 珊瑚の冠
    def tree(base, d, ln, r, br, bend=(0, 0, 0), **kw):
        return coral("body", "coral", base, d, ln, r, br, bend=bend, bmat="coralb", **kw)
    BR5 = [(0.34, 0, 0.5, 52), (0.34, 180, 0.46, 56), (0.58, 90, 0.42, 48), (0.58, 270, 0.42, 50), (0.8, 40, 0.28, 40)]
    BR3 = [(0.4, 20, 0.5, 52), (0.4, 200, 0.44, 54), (0.7, 110, 0.34, 46)]
    F += tree((0, 36.0, 6.0), (0.05, 1, 0.22), 34.0, 5.6, BR5, bend=(0, -2, 7), sides=5, rings=3, brings=2)
    F += tree((-12, 32.0, -1.0), (-0.55, 1, 0.05), 22.0, 3.6, BR3, bend=(-3, 0, 3))
    F += tree((13, 33.0, 2.0), (0.55, 1, 0.1), 24.0, 3.8, BR3, bend=(3, 0, 3))
    F += tree((-5, 34.0, -12.0), (-0.2, 1, -0.5), 17.0, 3.0, BR3[:2], bend=(-1, 0, -4))
    F += tree((6, 31.0, -15.0), (0.3, 1, -0.6), 15.0, 2.8, BR3[:2], bend=(1, 0, -4))
    F += tree((0, 27.0, 18.0), (0, 1, 0.9), 15.0, 3.0, BR3[:2], bend=(0, 2, 4))
    for s in (1, -1):                   # 縁の角 (鹿の角)
        F += tree((s * 28.0, 22.0, -18.0), (s * 0.9, 1, -0.3), 17.0, 3.4, BR3[:2], bend=(s * 4, 3, -3))
        F += tree((s * 29.0, 20.0, 8.0), (s * 1.0, 0.8, 0.5), 15.0, 3.2, BR3[:2], bend=(s * 4, 2, 4))
    # 管サンゴの束
    for (x, z, ys) in ((-17.0, -14.0, 28.4), (-21.0, -9.0, 27.0), (-14.0, -9.0, 30.2)):
        ln = 8.0 + 2 * jit(int(x * 3), 1)
        F += tube("body", "coral", [(x, ys - 3, z), (x + 0.5, ys + ln, z)], [2.5, 2.2], [2.5, 2.2], sides=5, phase=18, caps=(False, False))
        F += poly("body", "dark", [np.array([x + 1.7 * math.cos(math.radians(18 + 72 * k)), ys + ln - 0.3, z + 1.7 * math.sin(math.radians(18 + 72 * k))]) for k in range(5)],
                  np.array([x, ys - 4, z]))
    # 脳サンゴ
    F += ellipsoid("body", "brain", (12.0, 33.0, -10.0), (9.5, 6.0, 9.0), segs=8, rings=1)
    # ウチワサンゴ (薄い扇)
    fan = [(0, 0), (-9, 6), (-12, 14), (-8, 21), (-2, 25), (4, 24), (10, 19), (12, 11), (8, 4)]
    fan = [(z + 10.0, y + 26.0) for z, y in fan]
    F += fin("body", "coral", fan, -9.0, 1.8, R=rot_y(25) @ rot_z(-12), center=np.array([-9.0, 26.0, 10.0]), tip_thin=0.5)

    # ================================================================ 眼柄の目
    eyes = []
    for s in (1, -1):
        F += tube("eyes", "coral", [(s * 9.0, 30.0, -19.0), (s * 10.0, 38.0, -21.5), (s * 10.6, 43.0, -23.0)], [2.8, 2.2, 1.9],
                  sides=4, hint=(0, 0, 1), phase=45, caps=(False, False))
        F += ellipsoid("eyes", "eye", (s * 10.6, 45.6, -23.8), (4.8, 4.4, 4.2), segs=6, rings=1)
        F += sp("eyes", "coralb", (s * 12.5, 48.5, -22.5), (s * 0.5, 1, 0), 6.0, 1.8, sides=3, rings=1)
        eyes.append(dict(c=(s * 10.6, 45.6, -23.8), r=4.5, dir=(s * 0.12, 0, -1), a=3.6, b=2.1, tilt=0.15))

    # ================================================================ 脚 6 本 (細く、膝が高い)
    for s, side in ((1, "r"), (-1, "l")):
        for i, (z0, dz) in enumerate(((-14.0, -12.0), (0.0, -2.0), (14.0, 10.0)), start=1):
            bone = f"leg_{side}{i}"
            path = [(s * 24.0, 12.5, z0), (s * 42.0, 27.0, z0 + dz * 0.3), (s * 51.0, 17.0, z0 + dz * 0.65), (s * 56.0, 7.0, z0 + dz * 0.9)]
            F += tube(bone, "crab", path[:3], [3.8, 3.2, 3.0], [3.4, 2.8, 2.6], sides=4, hint=(0, 0, 1), phase=45, caps=(False, False))
            F += tube(bone, "crabb", [path[2], path[3], (s * 58.0, 0.4, z0 + dz)], [3.0, 2.2, 0.0], [2.6, 1.9, 0.0], sides=4,
                      hint=(0, 0, 1), phase=45, caps=(False, False))
            F += coral(bone, "coral", (s * 42.0, 29.0, z0 + dz * 0.3), (s * 0.3, 1, 0), 8.0, 2.0, [(0.5, 0, 0.5, 55)], bmat="coralb", sides=3,
                       rings=1, brings=1)

    # ================================================================ 大鋏 (右 = +x): 角のような枝分かれする指
    B, PB = "claw_big", "claw_big_pincer"
    F += tube(B, "crab", [(24.0, 27.0, -4.0), (35.0, 26.0, -18.0), (40.5, 24.0, -33.0)], [8.5, 10.0, 9.0], [8.0, 9.6, 8.6], sides=6,
              hint=(0, 1, 0), phase=0, caps=(False, False))
    for (x, z, ys) in ((30.0, -12.0, 34.0), (36.0, -22.0, 34.5)):
        ln = 7.0
        F += tube(B, "coral", [(x, ys - 2, z), (x + 0.3, ys + ln, z)], [2.3, 2.0], [2.3, 2.0], sides=5, phase=18, caps=(False, False))
        F += poly(B, "dark", [np.array([x + 1.5 * math.cos(math.radians(18 + 72 * k)), ys + ln - 0.3, z + 1.5 * math.sin(math.radians(18 + 72 * k))]) for k in range(5)],
                  np.array([x, ys - 4, z]))
    secs = [((40.5, 23.0, -33.0), 16.0, 18.0), ((42.5, 22.0, -45.0), 26.0, 31.0), ((44.0, 22.0, -57.0), 28.0, 35.0),
            ((43.5, 20.5, -67.0), 22.0, 27.0), ((43.0, 19.5, -74.0), 15.0, 17.0)]
    rr = []
    for k, (c, w, h) in enumerate(secs):
        pts = rrect(c, (1, 0, 0), (0, 1, 0), w, h, 0.45)
        rr.append([p + np.array([jit(k * 9 + i, 1.1, 5), jit(k * 9 + i + 40, 1.1, 5), jit(k * 9 + i + 80, 0.8, 5)]) for i, p in enumerate(pts)])
    F += rloft(B, "crab", rr, caps=(False, True))
    F += coral(B, "coral", (44.0, 38.0, -46.0), (0.05, 1, -0.25), 20.0, 3.6, [(0.35, 0, 0.5, 52), (0.35, 180, 0.46, 56), (0.62, 90, 0.4, 48)],
               bmat="coralb", bend=(0, 1, -4))
    F += coral(B, "coral", (52.0, 36.0, -58.0), (0.6, 1, 0.0), 14.0, 3.0, [(0.45, 20, 0.5, 52), (0.45, 200, 0.44, 54)], bmat="coralb")
    F += coral(B, "coral", (35.0, 36.0, -62.0), (-0.5, 1, -0.2), 13.0, 2.8, [(0.5, 20, 0.5, 52)], bmat="coralb")
    F += coral(B, "coral", (56.0, 30.0, -52.0), (1, 0.6, 0.0), 13.0, 3.0, [(0.45, 20, 0.5, 52), (0.45, 200, 0.46, 54)], bmat="coralb", bend=(2, 3, 0))
    F += coral(B, "coral", (57.0, 18.0, -62.0), (1, 0.3, -0.2), 11.0, 2.8, [(0.5, 0, 0.5, 55)], bmat="coralb", bend=(1, 2, -1))
    # 固定指 (下): 上へ反る主枝と脇枝
    low = [(43.0, 15.5, -70.0), (43.0, 12.5, -83.0), (43.0, 15.0, -96.0), (43.0, 24.0, -105.0)]
    F += tube(B, "crabb", low[:3], [7.0, 5.8, 4.4], [5.4, 4.4, 3.4], sides=4, hint=(1, 0, 0), phase=45, caps=(False, False))
    F += tube(B, "crab", [low[2], low[3]], [4.4, 3.0], [3.4, 2.2], sides=4, hint=(1, 0, 0), phase=45, caps=(False, False))
    F += tube(B, "glass", [low[3], (43.0, 31.0, -108.0)], [3.0, 0.0], [2.2, 0.0], sides=4, hint=(1, 0, 0), phase=45, caps=(False, False))
    for z, y, dz in ((-76, 19.0, -0.5), (-86, 16.5, -0.6), (-93, 18.0, -0.6)):
        F += sp(B, "coralb", (43.0, y, z), (0, 1, dz), 10.0, 2.8, sides=4, rings=2, bend=(0, 2, -3))
    for z in (-79, -89):
        F += sp(B, "coral", (43.0 + 4.0, 12.0, z), (1, 0.7, -0.2), 7.0, 2.2, sides=3, rings=1)
        F += sp(B, "coral", (43.0 - 4.0, 12.0, z), (-1, 0.7, -0.2), 7.0, 2.2, sides=3, rings=1)
    # 可動指 (上): 下へ曲がる主枝と、背の高い枝
    up = [(43.0, 31.5, -60.0), (43.0, 33.0, -75.0), (43.0, 29.0, -91.0), (43.0, 20.0, -103.0)]
    F += tube(PB, "crab", up[:3], [7.5, 6.2, 4.8], [6.8, 5.4, 3.8], sides=4, hint=(1, 0, 0), phase=45, caps=(True, False))
    F += tube(PB, "crab", [up[2], up[3]], [4.8, 3.2], [3.8, 2.4], sides=4, hint=(1, 0, 0), phase=45, caps=(False, False))
    F += tube(PB, "glass", [up[3], (43.0, 13.0, -107.0)], [3.2, 0.0], [2.4, 0.0], sides=4, hint=(1, 0, 0), phase=45, caps=(False, False))
    for z, y in ((-68, 26.8), (-78, 26.0), (-88, 23.5)):
        F += sp(PB, "coralb", (43.0, y, z), (0, -1, -0.3), 8.0, 2.6, sides=4, rings=2, bend=(0, -1, -3))
    F += coral(PB, "coral", (43.0, 38.0, -70.0), (0, 1, -0.3), 14.0, 3.0, [(0.4, 20, 0.5, 52), (0.4, 200, 0.5, 52), (0.7, 100, 0.35, 44)], bmat="coralb")

    # ================================================================ 小鋏 (左)
    C = "claw_small"
    F += tube(C, "crab", [(-24.0, 22.0, -7.0), (-31.0, 21.0, -15.0), (-35.0, 16.0, -24.0)], [4.8, 4.4, 4.0], [4.4, 4.0, 3.6], sides=6,
              hint=(0, 1, 0), caps=(False, False))
    F += rings_along(C, "crab", [((-35.5, 14.0, -26.0), 8.0, 10.0), ((-37.0, 13.0, -33.0), 13.0, 15.0), ((-37.0, 12.5, -39.0), 11.0, 12.0)],
                     (1, 0, 0), (0, 1, 0), caps=(False, True), cut=0.4)
    F += tube(C, "crabb", [(-37.0, 16.5, -38.0), (-37.0, 17.0, -46.0), (-37.0, 12.0, -54.0)], [3.2, 2.8, 0], [3.0, 2.4, 0], sides=4,
              hint=(1, 0, 0), phase=45, caps=(False, False))
    F += tube(C, "crabb", [(-37.0, 8.5, -38.0), (-37.0, 8.5, -46.0), (-37.0, 12.0, -54.0)], [3.2, 2.8, 0], [3.0, 2.4, 0], sides=4,
              hint=(1, 0, 0), phase=45, caps=(False, False))
    F += coral(C, "coral", (-37.0, 19.0, -30.0), (-0.3, 1, 0), 11.0, 2.4, [(0.5, 0, 0.5, 52)], bmat="coralb", sides=4)
    return F, eyes, extra


def extra(c, col, glow):
    P, N, mats, owned = c["P"], c["N"], c["mats"], c["owned"]
    bay = c["bay"]
    # 脳サンゴの迷路の溝
    m = (mats == "brain") & owned
    n1 = vnoise(P * 0.42 + 4.0, 7)
    line = m & (np.abs(n1 - 0.5) < 0.055)
    col = np.where(line[..., None], np.array(CORAL[0], float), col)
    # 珊瑚の先は明るく (上へ向けて淡く)
    t = np.clip((P[..., 1] - 30) / 30.0, 0, 1)
    pale = ((mats == "coral") & owned & (t + 0.4 * (vnoise(P * 0.5, 3) - 0.5) > 0.62 + 0.1 * bay))
    col = np.where(pale[..., None], np.array(CORALB[3], float), col)
    return col, glow
