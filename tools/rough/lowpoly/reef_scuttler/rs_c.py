"""C案「蜘蛛蟹」: 酸化した銅の緑青色の細い矢尻形の甲羅を、長い細脚 4 本ずつで高く持ち上げた走り屋。
背に尖った背びれのような棘の列、鼻先の角、前へ長く伸びた珊瑚色の刃の鋏 (内側に歯)。目は角の両脇の前向きの柄。"""
from scut import *  # noqa

VERD = [(14, 32, 34), (28, 62, 58), (48, 98, 86), (80, 138, 110), (122, 178, 140), (174, 214, 172)]
UNDER = [(18, 22, 28), (36, 46, 52), (62, 76, 78), (94, 110, 104)]
CORAL = [(62, 24, 38), (120, 46, 54), (180, 78, 66), (224, 118, 86), (248, 168, 124)]
CREAM = [(78, 66, 56), (140, 124, 100), (196, 182, 148), (232, 222, 190), (250, 244, 220)]
PAL = {"mats": {
    "verd": dict(ramp=VERD, base=0.64, lit=0.16, crack=0.0, streak=0.10),
    "under": dict(ramp=UNDER, base=0.66, lit=0.14, crack=0.0, min=0),
    "leg": dict(ramp=VERD[:5], base=0.60, lit=0.14, crack=0.0, min=0),
    "coral": dict(ramp=CORAL, base=0.66, lit=0.16, crack=0.0, streak=0.06),
    "cream": dict(ramp=CREAM, base=0.72, lit=0.12, crack=0.0, min=0),
    "dark": dict(ramp=[(8, 10, 14), (18, 24, 28), (32, 44, 46)], base=0.5, crack=0.0, min=0),
}}
HINT = 128
VIEW = dict(k=0.84, cen=(0, 5.6, -2.0), sc=15.0, claw_cen=(0, 5.2, -12.0), claw_sc=30.0)


def pentring(c, w, h, z, n=5, rot=90.0):
    return [V(c[0] + w * math.cos(math.radians(rot + 360.0 * k / n)), c[1] + h * math.sin(math.radians(rot + 360.0 * k / n)), z)
            for k in range(n)]


def blade_claw(s):
    b = f"claw_{side(s)}"
    F = []
    p0 = V(s * 2.8, 5.6, -4.2)
    px = s * 4.6
    F += tube(b, "coral", [p0, (s * 3.8, 5.3, -5.8), (px, 5.0, -7.4)], ra=[0.95, 0.85, 0.9], rb=[0.85, 0.75, 0.8], sides=4,
              hint=(0, 1, 0))
    R = [pentring((px, 5.0), 0.75, 1.0, -7.4), pentring((px, 5.0), 1.15, 1.5, -8.8), pentring((px, 5.0), 0.8, 1.05, -9.8)]
    F += loft(b, "coral", R, caps=("coral", "coral"))
    # 長い刃の指: 上は下へ反り、下は上へ反って先が交わる
    up = [(px, 5.8, -9.5), (px - s * 0.2, 6.3, -11.8), (px - s * 0.8, 5.9, -14.0), (px - s * 1.5, 4.8, -15.4)]
    lo = [(px, 4.2, -9.5), (px - s * 0.2, 3.7, -11.8), (px - s * 0.8, 4.0, -14.0), (px - s * 1.5, 4.9, -15.3)]
    F += tube(b, "coral", up, ra=[0.85, 0.7, 0.5, 0.0], rb=[0.6, 0.5, 0.35, 0.0], sides=4, hint=(1, 0, 0))
    F += tube(b, "coral", lo, ra=[0.85, 0.7, 0.5, 0.0], rb=[0.6, 0.5, 0.35, 0.0], sides=4, hint=(1, 0, 0))
    for z, yu, yl in ((-10.7, 6.0, 3.9), (-12.0, 6.0, 3.85), (-13.2, 5.6, 4.1)):
        F += spike(b, "cream", (px - s * 0.2, yu - 0.6, z), (0, -1, -0.3), 0.95, 0.3, sides=3, rings=1)
        F += spike(b, "cream", (px - s * 0.2, yl + 0.5, z), (0, 1, -0.3), 0.95, 0.3, sides=3, rings=1)
    F += spike(b, "cream", (s * 4.0, 6.0, -5.4), (0.3 * s, 1, -0.3), 1.6, 0.55, sides=3, rings=1)
    return F


def build():
    F = []
    B = "body"
    # ---- 矢尻形の甲羅 (持ち上げて高い位置)
    R = [carapace_ring(6.0, 2.4, 4.6, 2.6, corner=0.6, fc=0.5), carapace_ring(7.1, 3.5, 4.4, 3.4, corner=0.72, fc=0.42),
         carapace_ring(8.3, 2.9, 3.0, 2.8, corner=0.62, fc=0.5), carapace_ring(9.0, 1.4, 1.4, 1.8, corner=0.5, fc=0.6)]
    R[1][0] += V(0, 0, -1.0)
    R[1][2] += V(0.5, 0, 0)
    R[1][6] += V(-0.5, 0, 0)
    F += loft(B, "verd", R, caps=("under", "verd"))
    # 鼻先の角
    F += spike(B, "cream", (0, 7.0, -5.3), (0, -0.05, -1), 3.4, 0.8, sides=4, rings=2, bend=(0, -0.5, 0))
    # 背びれの棘列
    for i, (z, h, r) in enumerate(((-0.8, 3.0, 0.9), (0.6, 2.7, 0.85), (1.9, 2.3, 0.75), (3.0, 1.8, 0.6))):
        F += spike(B, "cream", (0, 8.5 + (0.3 if i == 0 else 0.1 * (3 - i)) - 0.15 * i, z), (0, 1, 0.55 + 0.1 * i), h, r, sides=3, rings=1)
    # 側面の棘 (後ろへ流れる)
    for s in (1, -1):
        F += spike(B, "cream", (s * 3.7, 7.1, -0.6), (s, 0.15, 0.5), 2.2, 0.75, sides=3, rings=1)
        F += spike(B, "cream", (s * 3.0, 7.1, -2.6), (s * 0.8, 0.3, -0.1), 1.8, 0.6, sides=3, rings=1)
        F += spike(B, "cream", (s * 2.7, 7.0, 2.4), (s * 0.5, 0.2, 1), 1.9, 0.6, sides=3, rings=1)
    # ---- 目: 角の両脇の前向きの柄
    for s in (1, -1):
        F += tube(B, "verd", [(s * 1.5, 7.3, -3.7), (s * 1.7, 8.2, -4.7), (s * 1.8, 8.8, -5.5)], ra=[0.8, 0.6, 0.5],
                  rb=[0.8, 0.6, 0.5], sides=4, hint=(1, 0, 0))
        F += ellipsoid(B, "lamp", V(s * 1.8, 9.0, -5.8), (0.75, 0.75, 0.75), segs=6, rings=1)
    # 口まわりの小棘
    for s in (1, -1):
        F += spike(B, "cream", (s * 0.8, 6.1, -4.1), (s * 0.15, -0.6, -1), 1.4, 0.4, sides=3, rings=1)
    # ---- 長い脚 (4 本ずつ)
    for s in (1, -1):
        b = f"leg_{side(s)}"
        for zc in (2.6, 1.0, -0.7, -2.4):
            k = (zc - 0.1) / 2.5
            pts = [(s * 2.8, 6.6, zc), (s * 6.4, 10.6, zc * 1.1 + 0.2 * k), (s * 9.6, 5.4, zc * 2.0 + 0.4 * k),
                   (s * 10.8, 0.0, zc * 2.6 + 0.9 * k)]
            F += leg_chain(b, "leg", pts, [0.95, 0.8, 0.6, 0.0], sides=3, hint=(0, 0, 1))
    F += blade_claw(1)
    F += blade_claw(-1)
    return F
