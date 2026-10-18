"""C案「枝珊瑚の長身」: 体じゅうが生きた枝珊瑚になった、背の高い細い影。胴は深い青紫の芯で、
そこから桃・橙・紅の枝があらゆる向きへ伸びて輪郭がぎざぎざに広がる。頭は筋の入った二枚貝の殻で、
殻の隙間の暗い窪みに海硝子の目 (顔も顎もない)。銛は枝分かれした珊瑚の小枝。"""
from parts import *  # noqa

CORE = [(14, 12, 36), (28, 22, 64), (48, 36, 96), (76, 58, 130), (112, 90, 164)]
PINK = [(86, 26, 56), (140, 44, 84), (200, 76, 112), (240, 120, 134), (255, 172, 164)]
ORNG = [(96, 36, 26), (152, 62, 34), (208, 98, 46), (244, 142, 72), (255, 192, 120)]
MAGN = [(64, 14, 60), (110, 24, 94), (160, 44, 132), (206, 84, 170), (240, 140, 206)]
SHELL = [(70, 44, 72), (120, 80, 112), (176, 130, 150), (226, 186, 190), (250, 230, 222)]
PAL = {"mats": {
    "core": dict(ramp=CORE, base=0.6, noise=0.14, crack=0.0, facevar=0.05, lit=0.18),
    "pink": dict(ramp=PINK, base=0.62, lit=0.22, crack=0.0, min=0, noise=0.1, facevar=0.08, dots=0.08),
    "orng": dict(ramp=ORNG, base=0.62, lit=0.22, crack=0.0, min=0, noise=0.1, facevar=0.08, dots=0.08),
    "magn": dict(ramp=MAGN, base=0.6, lit=0.2, crack=0.0, min=0, noise=0.1, facevar=0.08),
    "shell": dict(ramp=SHELL, base=0.62, lit=0.22, crack=0.0, min=0, noise=0.08, stripe=3, axis="u"),
    "dark": dict(ramp=DARKR, base=0.5, crack=0.0, min=0),
    "kelp": dict(ramp=KELP, base=0.55, lit=0.2, crack=0.0, min=0, stripe=3, axis="v", noise=0.12),
    "wood": dict(ramp=WOOD, base=0.6, lit=0.15, crack=0.0, min=0, noise=0.2, stripe=4, axis="v"),
    "glass": dict(ramp=GLASS, base=0.6, lit=0.2, crack=0.0, min=0),
}, "face_mat": "dark", "glow_mats": ["glass"]}
HINT = 128
HEAD_CENTER, HEAD_SCALE = (0, 31.5, -2.5), 15.5


def shell_ring(y, rx, zf, zb, zc, n=10, depth=0.16):
    """筋の入った殻の輪: 頂点の半径が交互に違う。"""
    pts = ring(y, rx, zf, zb, zc, n=n)
    c = V(0, y, zc)
    out = []
    for k, p in enumerate(pts):
        out.append(c + (p - c) * (1.0 if k % 2 == 0 else 1.0 - depth))
    return out


def branchy(bone, mat, base, d, ln, r, mats=("pink", "orng", "magn"), n=2, bend=(0, 0, 0), sides=3):
    """枝珊瑚の小さな木: 幹と左右 n 組の枝。"""
    br = []
    for i in range(n):
        t = 0.35 + 0.3 * i
        br += [(t, (-1.0, 0.25, 0.1 * i), ln * (0.5 - 0.1 * i), r * 0.55, mats[(i + 1) % 3]), (t, (1.0, 0.25, -0.1 * i), ln * (0.5 - 0.1 * i), r * 0.55, mats[(i + 2) % 3])]
    return stag(bone, mat, base, d, ln, r, br, bend=bend, sides=sides, rings=2)


def build():
    F = []
    # ---- 頭: 筋入りの二枚貝の殻。細い首の上
    F += tube("head", "core", [(0, 22.6, -0.4), (0, 26.0, -0.8), (0, 28.0, -1.0)], ra=[1.0, 0.9, 1.0], rb=[1.0, 0.9, 1.0], sides=4, hint=(1, 0, 0))
    R = [shell_ring(28.4, 2.6, -3.0, 3.0, -1.2), shell_ring(30.2, 4.4, -4.3, 3.8, -1.2), shell_ring(32.2, 4.9, -4.4, 4.0, -1.2),
         shell_ring(34.2, 3.8, -3.4, 3.2, -1.2), shell_ring(35.8, 1.4, -1.6, 1.8, -1.2)]
    F += loft("head", "shell", R, caps=("shell", "shell"))
    # 殻の隙間: 前に暗い窪みの面。目は窪みの中
    slit = [V(-3.0, 31.8, -4.62), V(3.0, 31.8, -4.62), V(2.6, 29.9, -4.4), V(-2.6, 29.9, -4.4)]
    F += poly("head", "dark", slit, (0, 31, 0))
    eyes = [dict(c=V(1.6, 30.9, -4.5), rx=0.9, ry=0.55, pr=(0.52, 0.32), tilt=0.1), dict(c=V(-1.6, 30.9, -4.5), rx=0.9, ry=0.55, pr=(0.52, 0.32), tilt=0.1)]
    # 殻から広がる枝角の冠
    F += stag("head", "pink", (0.0, 35.4, -1.2), (0, 1, -0.1), 8.5, 1.1, [(0.3, (-1.2, 0.2, 0), 4.0, 0.6, "orng"), (0.3, (1.2, 0.2, 0), 4.0, 0.6, "magn"),
                                                                   (0.6, (-1.0, 0.2, 0.2), 3.0, 0.5, "magn"), (0.6, (1.0, 0.2, -0.2), 3.0, 0.5, "orng")], bend=(0, 0, 1.0), rings=2)
    F += stag("head", "orng", (2.4, 34.0, -1.0), (1, 0.7, 0.1), 5.6, 0.9, [(0.5, (0.2, 1, 0), 3.0, 0.45, "pink")], bend=(0.8, 0.4, 0.3), sides=3, rings=2)
    F += stag("head", "magn", (-2.4, 34.0, -1.0), (-1, 0.7, 0.1), 5.6, 0.9, [(0.5, (-0.2, 1, 0), 3.0, 0.45, "orng")], bend=(-0.8, 0.4, 0.3), sides=3, rings=2)
    # ---- 胴: 細い青紫の芯。胸と背から枝が四方へ
    T = [ring(14.0, 2.3, -1.9, 2.3, 0.0), ring(17.4, 3.0, -2.6, 2.8, -0.2), ring(21.0, 3.6, -3.0, 3.0, -0.6), ring(24.2, 2.9, -2.4, 2.6, -1.0)]
    F += loft("body", "core", T, caps=("core", "core"))
    F += ellipsoid("body", "orng", (0.0, 19.0, -3.0), (2.4, 2.2, 1.4), segs=6, rings=1)          # 脳珊瑚のこぶ (胸)
    for (x, y, z, d, ln, r, m) in ((-2.4, 23.0, -2.0, (-1, 0.8, -0.5), 6.0, 1.1, "pink"), (2.6, 21.6, -2.4, (1, 0.7, -0.6), 5.6, 1.0, "magn"),
                                   (-3.0, 17.4, -2.0, (-1, 0.5, -0.7), 4.6, 0.9, "orng"), (1.6, 16.6, -2.4, (0.6, 0.2, -1), 4.0, 0.85, "pink")):
        F += branchy("body", m, (x, y, z), d, ln, r, n=1)
    for (x, y, z, d, ln, r, m) in ((-1.4, 23.4, 2.4, (-0.5, 1, 0.7), 9.0, 1.2, "pink"), (1.8, 22.0, 2.6, (0.6, 1, 0.8), 8.0, 1.1, "orng"),
                                   (0.2, 18.0, 2.8, (0, 0.6, 1), 6.0, 1.0, "magn")):
        F += branchy("body", m, (x, y, z), d, ln, r, n=2, bend=(0, 0, 1.2))
    # 腰のぼろの昆布
    for x, yb, w in ((-2.2, 8.0, 1.0), (0, 6.8, 1.2), (2.2, 8.2, 1.0)):
        F += cloth("body", "kelp", [[(x - w, 14.2, -2.0), (x + w, 14.2, -2.0)], [(x - w * 0.9, 11.2, -2.6), (x + w * 0.9, 11.2, -2.6)], [(x, yb, -3.0), (x, yb, -3.0)]], (x, 11, 0))
    # ---- 腕: 節のある細い枝
    for nm, s in (("l", -1), ("r", 1)):
        a, fa = f"arm_{nm}", f"forearm_{nm}"
        sh = V(s * 4.0, 24.0, -0.2)
        elbow = V(s * 3.75, 16.0, -0.5 if nm == "r" else 0.0)
        F += tube(a, "pink" if nm == "r" else "orng", [sh + V(s * 0.2, 0.8, 0), sh + V(s * 0.1, -0.5, 0), (s * 3.9, 20.0, -0.2), elbow + V(0, 0.5, 0)],
                  ra=[1.1, 1.6, 0.95, 1.05], rb=[1.1, 1.5, 0.95, 1.05], sides=4, hint=(1, 0, 0), caps=(True, False))
        F += branchy(a, "magn" if nm == "r" else "pink", sh + V(s * 0.6, 1.0, 0.2), (s * 0.6, 1, 0.1), 7.0, 1.0, n=2, bend=(s * 1.0, 0, 0.3))
        F += stag(a, "orng", (s * 4.4, 20.4, 0.2), (s, 0.1, 0.5), 3.6, 0.7, [(0.5, (0, 1, 0), 2.0, 0.4, "pink")], sides=3, rings=1)
        if nm == "r":
            hand = V(3.75, 11.5, -2.5)
            F += tube(fa, "pink", [elbow, (3.75, 13.6, -1.6), hand], ra=[1.0, 0.85, 0.9], rb=[1.0, 0.85, 0.9], sides=4, hint=(1, 0, 0))
            F += ellipsoid(fa, "orng", hand, (1.1, 1.1, 1.1), segs=4, rings=1)
            for i in range(3):
                dx = (i - 1) * 0.8
                F += spike(fa, "magn", hand + V(dx * 0.5, -0.3, -0.5), (dx * 0.2, -0.3, -1.0), 2.4, 0.5, sides=3, rings=1)
        else:
            hand = V(-3.75, 5.4, -0.4)
            F += tube(fa, "orng", [elbow, (-3.8, 11.0, -0.2), hand], ra=[1.0, 0.8, 0.85], rb=[1.0, 0.8, 0.85], sides=4, hint=(1, 0, 0))
            F += ellipsoid(fa, "pink", hand + V(0, 0.3, 0), (1.1, 1.0, 1.0), segs=4, rings=1)
            for i in range(4):               # 枝の指
                dx = (i - 1.5) * 0.8
                F += stag(fa, ("magn", "orng")[i % 2], hand + V(dx * 0.5, -0.4, -0.2), (dx * 0.35, -1.0, -0.4), 3.6, 0.55,
                          [(0.55, (dx * 0.6, 0.2, -0.5), 1.8, 0.3, "pink")], sides=3, rings=1)
    # ---- 脚: 細い珊瑚の幹
    for nm, s in (("l", -1), ("r", 1)):
        b = f"leg_{nm}"
        x = s * 2.0
        F += tube(b, "pink" if s > 0 else "orng", [(x, 13.3, 0.0), (x, 8.0, -0.6), (x, 2.8, 0.0)], ra=[1.35, 1.0, 1.2], rb=[1.35, 1.0, 1.2], sides=4, hint=(1, 0, 0))
        F += stag(b, "magn", (x + s * 0.9, 9.0, -0.6), (s, 0.5, -0.4), 4.0, 0.7, [(0.5, (0, 1, 0), 2.0, 0.4, "orng")], sides=3, rings=1)
        F += tube(b, "core", [(x, 2.8, 0.0), (x, 0.9, -1.0), (x, 0.3, -3.8)], ra=[1.1, 1.0, 0.12], rb=[0.9, 0.5, 0.1], sides=4, hint=(1, 0, 0))
    # ---- 銛: 枝分かれした珊瑚の小枝
    B = []
    B += tube("spear", "wood", [(0, -9.5, 0), (0, -2.0, 0), (0, 6.0, 0), (0, 10.0, 0)], ra=[0.5, 0.65, 0.6, 0.55], rb=[0.5, 0.65, 0.6, 0.55],
              sides=4, hint=(1, 0, 0), phase=45, caps=(True, False))
    for y in (-3.0, 0.8):
        B += tube("spear", "kelp", [(0, y, 0), (0, y + 1.6, 0)], ra=[0.95, 0.95], rb=[0.95, 0.95], sides=4, hint=(1, 0, 0), phase=0, caps=(True, True))
    B += spike("spear", "magn", (0, -9.5, 0), (0, -1, 0), 2.2, 0.55, sides=4, rings=1)
    B += stag("spear", "pink", (0, 9.5, 0), (0, 1, 0), 14.0, 0.95,
              [(0.2, (1.4, 0, 0.3), 5.0, 0.5, "orng"), (0.2, (-1.4, 0, -0.3), 5.0, 0.5, "magn"), (0.4, (1.2, 0, -0.4), 4.6, 0.45, "magn"), (0.4, (-1.2, 0, 0.4), 4.6, 0.45, "orng"),
               (0.62, (1.0, 0, 0.3), 3.4, 0.4, "orng"), (0.62, (-1.0, 0, -0.3), 3.4, 0.4, "magn")], bend=(0.6, 0, 0.3), sides=4, rings=3)
    B += spike("spear", "glass", (0.6, 23.4, 0.05), (0.05, 1, 0), 3.4, 0.5, sides=4, rings=1)
    for sx, y in ((-1, 14.8), (1, 18.2)):
        B += spike("spear", "glass", (sx * 3.5 + 0.2, y + 3.2, 0.0), (sx * 0.3, 1, 0), 2.2, 0.38, sides=3, rings=1)
    B += tube("spear", "shell", [(0, 8.9, 0), (0, 10.1, 0)], ra=[1.15, 1.15], rb=[1.15, 1.15], sides=5, hint=(1, 0, 0), phase=0, caps=(True, True))
    return F, B, eyes


def statue_coral():
    F = []
    for s in (-1, 1):
        F += branchy("statue_coral", "pink", (s * 4.8, 25.0, 0.2), (s * 0.5, 1, 0.3), 5.0, 0.9, n=1, bend=(s * 1.0, 0, 0))
    F += branchy("statue_coral", "magn", (0.0, 20.0, 3.5), (0, 0.7, 1), 6.0, 1.0, n=1, bend=(0, 0, 1.6))
    return F
