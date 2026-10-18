"""B案「海綿と昆布の外套」: 体が黄橙色の海綿 (穴だらけ) になった、丸く垂れる輪郭の溺れ人。
深い青紫の昆布を外套のように肩から背へ垂らし、肩と背に管海綿の筒が生える。顔はイソギンチャクの円盤:
触手の襟に囲まれ、中央の暗い窪みに海硝子の目。頭の上には細い枝珊瑚が少し。銛は巻貝の尖塔。"""
from parts import *  # noqa

SPONGE = [(80, 44, 26), (140, 84, 38), (198, 134, 54), (234, 176, 84), (252, 214, 134)]
CREAM = [(110, 76, 84), (170, 128, 126), (222, 184, 166), (250, 226, 204)]
ANEM = [(80, 20, 70), (136, 36, 104), (190, 64, 134), (232, 108, 164), (252, 168, 196)]
TEAL = [(10, 52, 66), (20, 92, 106), (40, 134, 140), (78, 176, 168), (140, 214, 196)]
PINK = [(70, 28, 52), (120, 44, 76), (176, 70, 100), (222, 110, 124), (250, 160, 150)]
VIOL = [(36, 24, 70), (66, 44, 110), (104, 72, 150), (148, 108, 188), (196, 160, 218)]
PAL = {"mats": {
    "sponge": dict(ramp=SPONGE, base=0.62, noise=0.14, crack=0.0, facevar=0.05, dots=0.16, lit=0.18,
                   stain=dict(ramp=[(50, 28, 30), (84, 44, 40)], thr=0.9, up=0.2, low=0.1)),
    "cream": dict(ramp=CREAM, base=0.62, lit=0.22, crack=0.0, min=0, noise=0.08, stripe=3, axis="u"),
    "anem": dict(ramp=ANEM, base=0.62, lit=0.2, crack=0.0, min=0, noise=0.1, facevar=0.08, dots=0.06),
    "teal": dict(ramp=TEAL, base=0.6, lit=0.2, crack=0.0, min=0, noise=0.1, facevar=0.06),
    "pink": dict(ramp=PINK, base=0.62, lit=0.2, crack=0.0, min=0, noise=0.1, facevar=0.07),
    "viol": dict(ramp=VIOL, base=0.6, lit=0.2, crack=0.0, min=0, noise=0.1, facevar=0.07),
    "dark": dict(ramp=DARKR, base=0.5, crack=0.0, min=0),
    "kelp": dict(ramp=KELP, base=0.55, lit=0.2, crack=0.0, min=0, stripe=3, axis="v", noise=0.12),
    "wood": dict(ramp=WOOD, base=0.6, lit=0.15, crack=0.0, min=0, noise=0.2, stripe=4, axis="v"),
    "glass": dict(ramp=GLASS, base=0.6, lit=0.2, crack=0.0, min=0),
}, "face_mat": "dark", "glow_mats": ["glass"]}
HINT = 128
HEAD_CENTER, HEAD_SCALE = (0, 29.5, -2.5), 14.5


def build():
    F = []
    # ---- 頭: 丸い海綿の頭。顔は暗い窪みの円盤
    R = [ring(31.0, 2.6, -2.8, 3.0, -0.8), ring(29.4, 3.5, -4.0, 3.4, -1.0), ring(27.2, 3.4, -4.4, 3.2, -1.2), ring(25.4, 2.4, -3.4, 2.4, -1.0)]
    R[1][0] += V(0, 0, 0.5)
    eyes = [dict(c=V(1.4, 28.9, -4.4), rx=0.95, ry=0.6, pr=(0.55, 0.34), tilt=0.1),
            dict(c=V(-1.4, 28.9, -4.4), rx=0.95, ry=0.6, pr=(0.55, 0.34), tilt=0.1)]
    F += loft("head", "sponge", R, caps=("sponge", "sponge"))
    # 顔の円盤: 暗い窪み (八角の板) とその縁を囲む触手の襟
    disc = [V(1.9 * math.cos(math.radians(a)), 28.6 + 1.7 * math.sin(math.radians(a)), -4.55 + 0.0) for a in range(0, 360, 45)]
    F += poly("head", "dark", disc, (0, 28.6, 0))
    for i in range(10):
        a = math.radians(i * 36 + 18)
        ux, uy = math.cos(a), math.sin(a)
        base = V(ux * 2.6, 28.6 + uy * 2.2, -4.2)
        F += spike("head", ("anem", "pink")[i % 2], base, V(ux * 0.8, uy * 0.8, -0.7), 2.6, 0.62, sides=3, rings=1, bend=(ux * 0.6, uy * 0.4 - 0.5, 0))
    F += tube("head", "sponge", [(0, 23.0, -0.8), (0, 25.4, -0.8)], ra=[1.6, 1.4], rb=[1.5, 1.3], sides=5, hint=(1, 0, 0))
    # 頭の上: 細い枝珊瑚の冠 (少なめ)
    F += stag("head", "anem", (0.4, 31.0, -0.8), (0.1, 1, -0.1), 7.5, 1.0, [(0.35, (-1.1, 0.2, 0), 3.0, 0.5, "viol"), (0.35, (1.1, 0.2, 0), 3.0, 0.5, "pink"),
                                                                       (0.65, (-0.9, 0.2, 0.2), 2.4, 0.42), (0.65, (0.9, 0.2, -0.2), 2.4, 0.42, "viol")], bend=(0, 0, 1.0), rings=2)
    F += stag("head", "viol", (-2.2, 30.4, 0.4), (-0.8, 1, 0.3), 5.6, 0.9, [(0.5, (-0.8, 0.3, 0.2), 2.4, 0.4, "anem")], bend=(-1.0, 0, 0.8), sides=3, rings=2)
    # ---- 胴: 丸い海綿の腹。猫背
    T = [ring(13.8, 3.0, -2.6, 3.0, 0.0), ring(17.0, 4.2, -3.6, 3.8, -0.2), ring(20.8, 4.8, -3.8, 4.0, -0.8), ring(24.2, 4.0, -3.2, 3.4, -1.2)]
    F += loft("body", "sponge", T, caps=("sponge", "sponge"))
    # 昆布の外套: 肩の襟と背に垂れる三枚
    C = [ring(25.0, 5.0, -3.4, 4.2, -0.8, n=6), ring(23.4, 5.9, -3.9, 4.8, -0.8, n=6)]
    F += loft("body", "kelp", C, caps=("kelp", False))
    for x, w, yb, z in ((-3.0, 1.6, 7.0, 5.0), (0.0, 1.8, 5.0, 5.6), (3.0, 1.6, 7.6, 5.0)):
        F += cloth("body", "kelp", [[(x - w, 23.4, 4.6), (x + w, 23.4, 4.6)], [(x - w * 1.1, 17.0, z - 0.4), (x + w * 1.1, 17.0, z - 0.4)],
                                    [(x - w * 0.9, 11.0, z), (x + w * 0.9, 11.0, z)], [(x, yb, z + 0.3), (x, yb, z + 0.3)]], (x, 14, 0))
    # 前の昆布の帯 (斜め)
    F += cloth("body", "kelp", [[(3.8, 23.4, -4.0), (2.6, 23.6, -4.4)], [(1.0, 18.4, -4.5), (-0.2, 18.6, -4.9)], [(-2.4, 14.4, -3.6), (-3.6, 14.6, -4.0)]], (0, 19, 0))
    # 管海綿の筒 (背と肩)
    F += pipe("body", "teal", (2.2, 23.0, 3.2), (0.3, 1, 0.5), 7.0, 1.2)
    F += pipe("body", "cream", (-1.4, 23.4, 3.4), (-0.3, 1, 0.5), 5.2, 1.0)
    F += pipe("body", "teal", (-3.4, 22.0, 3.2), (-0.8, 1, 0.3), 4.0, 0.9)
    for x, yb, w in ((-2.4, 9.4, 1.1), (0, 8.4, 1.3), (2.4, 9.6, 1.1)):
        F += cloth("body", "kelp", [[(x - w, 14.0, -2.4), (x + w, 14.0, -2.4)], [(x - w * 0.9, 11.6, -3.0), (x + w * 0.9, 11.6, -3.0)], [(x, yb, -3.3), (x, yb, -3.3)]], (x, 11, 0))
    # ---- 腕: 穴だらけの海綿の腕
    for nm, s in (("l", -1), ("r", 1)):
        a, fa = f"arm_{nm}", f"forearm_{nm}"
        sh = V(s * 4.4, 24.0, -0.6)
        elbow = V(s * 3.75, 16.0, -0.5 if nm == "r" else 0.0)
        F += tube(a, "sponge", [sh + V(s * 0.3, 0.9, 0), sh + V(s * 0.2, -0.5, 0), (s * 4.0, 20.0, -0.3), elbow + V(0, 0.5, 0)],
                  ra=[1.5, 2.1, 1.7, 1.4], rb=[1.5, 2.0, 1.6, 1.4], sides=5, hint=(1, 0, 0), caps=(True, False))
        F += pipe(a, "cream" if nm == "r" else "teal", sh + V(s * 0.8, 1.0, 0.2), (s * 0.9, 1, 0.1), 3.4, 0.85)
        if nm == "r":
            hand = V(3.75, 11.5, -2.5)
            F += tube(fa, "sponge", [elbow, (3.75, 13.6, -1.6), hand], ra=[1.3, 1.2, 1.2], rb=[1.3, 1.2, 1.2], sides=5, hint=(1, 0, 0))
            F += tube(fa, "kelp", [(3.75, 14.6, -1.2), (3.75, 13.4, -1.7)], ra=[1.6, 1.6], rb=[1.6, 1.6], sides=5, hint=(1, 0, 0), phase=0, caps=(True, True))
            F += ellipsoid(fa, "sponge", hand, (1.4, 1.3, 1.3), segs=5, rings=1)
            for i in range(3):
                dx = (i - 1) * 0.8
                F += spike(fa, "anem", hand + V(dx * 0.5, -0.3, -0.5), (dx * 0.2, -0.3, -1.0), 2.2, 0.55, sides=3, rings=1)
        else:
            hand = V(-3.75, 5.4, -0.4)
            F += tube(fa, "sponge", [elbow, (-3.8, 11.0, -0.2), hand], ra=[1.3, 1.2, 1.2], rb=[1.3, 1.2, 1.2], sides=5, hint=(1, 0, 0))
            F += ellipsoid(fa, "sponge", hand + V(0, 0.2, 0), (1.5, 1.3, 1.4), segs=5, rings=1)
            for i in range(4):               # イソギンチャクの触手の指
                dx = (i - 1.5) * 0.75
                F += spike(fa, ("anem", "pink")[i % 2], hand + V(dx * 0.5, -0.4, -0.2), (dx * 0.3, -1.0, -0.3), 3.2, 0.5, sides=3, rings=1, bend=(0, 0, -0.9))
            F += cloth(fa, "kelp", [[(-5.0, 13.0, -0.8), (-5.0, 13.0, 0.8)], [(-5.6, 9.0, -1.0), (-5.6, 9.0, 1.0)], [(-5.4, 5.6, 0), (-5.4, 5.6, 0)]], (-3.8, 10, 0))
    # ---- 脚
    for nm, s in (("l", -1), ("r", 1)):
        b = f"leg_{nm}"
        x = s * 2.3
        F += tube(b, "sponge", [(x, 13.3, 0.0), (x, 8.5, -0.5), (x, 3.0, 0.0)], ra=[1.8, 1.6, 1.7], rb=[1.8, 1.6, 1.7], sides=5, hint=(1, 0, 0))
        F += tube(b, "kelp", [(x, 9.0, -0.4), (x, 7.6, -0.4)], ra=[2.0, 2.0], rb=[2.0, 2.0], sides=5, hint=(1, 0, 0), phase=0, caps=(True, True))
        F += ellipsoid(b, "sponge", (x, 1.2, -1.2), (2.0, 1.2, 2.6), segs=5, rings=1)
    # ---- 銛: 巻貝の尖塔 (別メッシュ)
    B = []
    B += tube("spear", "wood", [(0, -9.5, 0), (0, -2.0, 0), (0, 6.0, 0), (0, 10.0, 0)], ra=[0.5, 0.65, 0.6, 0.55], rb=[0.5, 0.65, 0.6, 0.55],
              sides=4, hint=(1, 0, 0), phase=45, caps=(True, False))
    for y in (-3.0, 0.8):
        B += tube("spear", "kelp", [(0, y, 0), (0, y + 1.6, 0)], ra=[0.95, 0.95], rb=[0.95, 0.95], sides=4, hint=(1, 0, 0), phase=0, caps=(True, True))
    B += spike("spear", "anem", (0, -9.5, 0), (0, -1, 0), 2.2, 0.6, sides=4, rings=1)
    # 巻貝の尖塔: 段ごとに細くなる太い輪を重ねる (少しずつ傾く)
    y = 8.0
    for i, (r, h) in enumerate(((1.7, 3.4), (1.45, 3.2), (1.2, 3.0), (0.95, 2.8), (0.65, 2.4))):
        B += tube("spear", "cream" if i % 2 == 0 else "anem", [(0.15 * i, y, 0), (0.15 * i + 0.1, y + h, 0)], ra=[r, r * 0.82], rb=[r, r * 0.82], sides=5, hint=(1, 0, 0),
                  phase=10 * i, caps=(i == 0, False))
        y += h * 0.92
    B += spike("spear", "glass", (0.7, y + 0.2, 0.0), (0.1, 1, 0), 4.2, 0.55, sides=4, rings=1)
    for i in range(5):                      # 根元の触手の襟
        a = math.radians(72 * i)
        d = V(math.sin(a), 0.6, math.cos(a))
        B += spike("spear", ("anem", "pink")[i % 2], V(math.sin(a) * 0.9, 7.0, math.cos(a) * 0.9), d, 3.8, 0.6, sides=3, rings=1, bend=(0, -2.0, 0))
    return F, B, eyes


def statue_coral():
    F = []
    for s in (-1, 1):
        F += pipe("statue_coral", "teal", (s * 5.2, 24.0, 0.2), (s * 0.7, 1, 0.2), 4.4, 0.9)
    F += stag("statue_coral", "anem", (0.0, 20.0, 4.0), (0, 0.7, 1), 6.0, 1.0, [(0.45, (1, 0.2, 0), 2.8, 0.45, "pink"), (0.45, (-1, 0.2, 0), 2.8, 0.45, "pink")], bend=(0, 0, 1.6))
    return F
