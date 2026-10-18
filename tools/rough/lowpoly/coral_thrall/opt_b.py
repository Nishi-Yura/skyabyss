"""B案「扇珊瑚の背負い」: 石になった潮の民の体に、板を重ねた卓珊瑚の肩当て・脳珊瑚の頭巾・背に大きな扇珊瑚。
どっしり幅広の輪郭で、尖りでなく面の重なりと背の円盤で見せる。銛は扇珊瑚の平たい葉の穂先。"""
from common import *  # noqa

STONE = [(28, 32, 42), (58, 66, 78), (96, 106, 116), (134, 146, 150), (174, 184, 182), (210, 218, 212)]
VERD = [(22, 50, 48), (38, 90, 82), (62, 130, 110), (100, 170, 140), (150, 206, 170)]
FAN = [(56, 18, 56), (108, 32, 86), (162, 56, 112), (212, 100, 142), (244, 156, 176)]
TABL = [(80, 56, 20), (136, 100, 34), (194, 148, 54), (232, 188, 88), (252, 226, 140)]
BRAIN = [(96, 66, 62), (146, 108, 94), (192, 152, 126), (226, 192, 160), (248, 222, 192)]
MOSS = [(30, 50, 44), (52, 84, 66), (84, 120, 88), (120, 156, 108)]
GLASS = [(60, 150, 176), (110, 210, 226), (180, 244, 252)]
WOOD = [(26, 24, 30), (50, 46, 54), (78, 72, 78), (108, 100, 100)]
PAL = {"mats": {
    "stone": dict(ramp=STONE, base=0.62, noise=0.09, crack=0.2, stain=dict(ramp=MOSS, thr=0.95, up=0.3, low=0.1)),
    "dark": dict(ramp=[(6, 8, 14), (14, 16, 26), (28, 30, 42)], base=0.5, crack=0.0, min=0),
    "verd": dict(ramp=VERD, base=0.6, lit=0.2, crack=0.0, min=0, noise=0.12, stripe=4, axis="v"),
    "fan": dict(ramp=FAN, base=0.6, lit=0.22, crack=0.0, min=0, noise=0.08, stripe=3, axis="u"),
    "tabl": dict(ramp=TABL, base=0.62, lit=0.2, crack=0.0, min=0, noise=0.1, stripe=5, axis="v"),
    "brain": dict(ramp=BRAIN, base=0.62, lit=0.2, crack=0.0, min=0, noise=0.08, stripe=3, axis="v"),
    "wood": dict(ramp=WOOD, base=0.6, lit=0.15, crack=0.0, min=0, noise=0.2, stripe=4, axis="v"),
    "glass": dict(ramp=GLASS, base=0.6, lit=0.2, crack=0.0, min=0),
}, "noses": [], "face_mat": "stone", "glow_mats": ["glass"]}
HINT = 128


def plate(bone, mat, c, rx, rz, th=0.5, tilt=(0, 0, 0), segs=6, bulge=1.0):
    """卓珊瑚の板: 平たい多角形の円盤。上の面と、すぼまる側面だけ (下は塞がない)。"""
    R = rot_z(tilt[2]) @ rot_x(tilt[0]) @ rot_y(tilt[1])
    c = np.asarray(c, float)
    ph = lambda k: math.radians(360 * k / segs + 15)
    top = [R @ V(rx * math.sin(ph(k)), th * 0.5, rz * math.cos(ph(k))) + c for k in range(segs)]
    bot = [R @ V(rx * 0.8 * math.sin(ph(k)), -th * 0.5 - 0.3, rz * 0.8 * math.cos(ph(k))) + c for k in range(segs)]
    return loft(bone, mat, [top, bot], caps=(mat, False))


def build():
    F = []
    # ------------------------------------------------------------ 頭 (石の顔 + 脳珊瑚の頭巾)
    R = [ring(30.2, 2.7, -3.4, 2.9, 0.0), ring(28.8, 2.8, -4.3, 2.8, -0.1), ring(27.2, 2.4, -4.8, 2.4, -0.2), ring(25.8, 1.4, -4.2, 1.6, -0.2)]
    for s, k in ((1, 1), (-1, 7)):
        R[1][k] += V(-s * 0.7, -0.1, 1.4)
        R[0][k] += V(s * 0.1, 0.3, -0.6)
    R[2][0] += V(0, 0.2, -0.3)
    eyes = [dict(c=R[1][1] + V(0, 0, -0.2), rx=0.95, ry=0.62, pr=(0.55, 0.33), tilt=0.15),
            dict(c=R[1][7] + V(0, 0, -0.2), rx=0.95, ry=0.62, pr=(0.55, 0.33), tilt=0.15)]
    F += loft("head", "stone", R, caps=("stone", "stone"))
    # 下顎 (角ばった石)
    F += tube("head", "stone", [(-2.0, 25.9, 1.4), (-1.5, 25.0, -2.4), (0, 24.8, -4.4), (1.5, 25.0, -2.4), (2.0, 25.9, 1.4)],
              ra=[0.7, 0.8, 0.7, 0.8, 0.7], rb=[0.6, 0.6, 0.55, 0.6, 0.6], sides=4, hint=(0, 1, 0),
              axis_fn=lambda p: np.array([0, p[1] + 0.3, p[2] + 2.0]), hide_cos=0.7)
    # 脳珊瑚の頭巾: 眉の上からかぶる丸い殻
    H = [ring(27.0, 3.5, -4.4, 3.9, 0.0), ring(31.8, 4.1, -4.4, 4.2, 0.1), ring(33.6, 2.9, -3.0, 3.2, 0.2),
         ring(34.6, 1.1, -1.2, 1.5, 0.3)]
    for k, dy in ((0, 3.2), (1, 3.0), (7, 3.0), (2, 1.0), (6, 1.0)):
        H[0][k] += V(0, dy, 0)
    F += loft("head", "brain", H, caps=(False, "brain"))
    # 頭巾の前の縁: 眉の上へせり出す庇
    brim = [[V(-3.5, 30.3, -4.0), V(0, 30.0, -5.3), V(3.5, 30.3, -4.0)], [V(-3.9, 31.3, -3.0), V(0, 30.9, -4.8), V(3.9, 31.3, -3.0)]]
    F += grid("head", "brain", [brim[1], brim[0]], (0, 33, 1))
    # 頭の後ろの首の石
    F += tube("head", "stone", [(0, 23.3, 0.8), (0, 25.2, 0.9)], ra=[1.3, 1.1], rb=[1.2, 1.0], sides=4, hint=(1, 0, 0))
    # ------------------------------------------------------------ 胴 (石の胴 + 卓珊瑚の重ね板)
    T = [ring(14.4, 3.1, -2.4, 2.5, 0.0), ring(20.0, 4.2, -3.4, 3.1, 0.0), ring(24.6, 3.4, -2.8, 2.6, 0.0)]
    F += loft("body", "stone", T, caps=("stone", "stone"))
    # 胸の前の重ね板 (上から下へ小さく)
    for i, (y, w) in enumerate(((22.8, 3.9), (20.6, 3.4), (18.4, 2.9))):
        F += plate("body", "tabl" if i != 1 else "fan", (0, y, -3.0), w, 1.9, th=0.5, tilt=(-14, 0, 0), segs=6)
    # 腰の帯 (緑青の銅)
    # 腰の前垂れ
    for x, w in ((-2.2, 1.1), (0, 1.3), (2.2, 1.1)):
        rows = [[V(x - w, 14.6, -2.7), V(x + w, 14.6, -2.7)], [V(x - w, 11.0, -3.2), V(x + w, 11.0, -3.2)], [V(x, 9.4, -3.4), V(x, 9.4, -3.4)]]
        F += grid("body", "verd", rows, (x, 12, 0))
    # 背の扇珊瑚: 輪郭が欠けた大きな円盤 (体の後ろ・縦)
    cx, cy, cz = 0.0, 21.5, 4.2
    rad = [9.0, 12.0, 8.6, 12.4, 9.0, 12.6, 8.8, 12.2, 8.6, 11.0, 9.2]
    outer, inner = [], []
    n = len(rad)
    for k, rr in enumerate(rad):
        a = math.radians(-125 + 250.0 * k / (n - 1))
        outer.append(V(cx + rr * math.sin(a), cy + rr * math.cos(a) - 2.0, cz + 1.2 * math.cos(a * 2)))
        inner.append(V(cx + 3.2 * math.sin(a), cy + 3.2 * math.cos(a) - 1.2, cz))
    F += grid("body", "fan", [inner, outer], (0, 20, -4))
    for k in range(n - 1):
        F += poly("body", "fan", [inner[k], inner[k + 1], V(cx, cy - 2.2, cz - 0.3)], (0, 20, -4))
    # 扇の付け根の太い茎
    F += tube("body", "fan", [(0, 17.0, 2.4), (0, 19.2, 3.4), (0, 21.0, 4.2)], ra=[1.6, 1.3, 1.0], rb=[1.2, 1.0, 0.8], sides=4, hint=(1, 0, 0))
    # ------------------------------------------------------------ 腕
    for nm, s in (("l", -1), ("r", 1)):
        a, fa = f"arm_{nm}", f"forearm_{nm}"
        sh = V(s * 4.0, 24.0, 0.0)
        elbow = V(s * 3.75, 16.0, -0.5 if nm == "r" else 0.0)
        F += tube(a, "stone", [sh + V(s * 0.3, 1.0, 0), sh + V(s * 0.2, -0.6, 0), elbow + V(0, 0.4, 0)],
                  ra=[1.1, 1.7, 1.0], rb=[1.1, 1.6, 1.0], sides=4, hint=(1, 0, 0), caps=(True, False))
        # 肩当て: 卓珊瑚の板 2 枚
        F += plate(a, "tabl", sh + V(s * 1.4, 1.1, 0.0), 3.3, 2.7, th=0.5, tilt=(0, 0, -s * 22), segs=6)
        F += plate(a, "fan" if nm == "l" else "brain", sh + V(s * 2.4, -0.3, 0.0), 2.4, 2.1, th=0.45, tilt=(0, 0, -s * 30), segs=6)
        if nm == "r":
            hand = V(3.75, 11.5, -2.5)
            F += tube(fa, "stone", [elbow, (3.75, 13.6, -1.6), hand], ra=[1.0, 1.1, 0.85], rb=[1.0, 1.1, 0.85], sides=4, hint=(1, 0, 0))
            F += plate(fa, "verd", (3.75, 13.4, -1.5), 1.7, 1.7, th=0.8, tilt=(-35, 0, 0), segs=5)
            F += ellipsoid(fa, "stone", hand, (1.2, 1.2, 1.2), segs=4, rings=1)
            for i in range(3):
                dx = (i - 1) * 0.75
                F += spike(fa, "stone", hand + V(dx * 0.5, -0.4, -0.3), (dx * 0.2, -0.3, -1.0), 2.2, 0.5, sides=3, rings=1)
        else:
            hand = V(-3.75, 6.4, -0.4)
            F += tube(fa, "stone", [elbow, (-3.8, 11.5, -0.1), hand], ra=[1.0, 1.0, 0.8], rb=[1.0, 1.0, 0.8], sides=4, hint=(1, 0, 0))
            F += plate(fa, "verd", (-3.8, 12.6, -0.1), 1.8, 1.8, th=0.9, tilt=(0, 0, 8), segs=5)
            F += ellipsoid(fa, "stone", hand + V(0, 0.2, 0), (1.15, 1.1, 1.1), segs=4, rings=1)
            for i in range(3):
                dx = (i - 1) * 0.7
                F += spike(fa, "stone", hand + V(dx * 0.5, -0.2, -0.2), (dx * 0.18, -1.0, -0.4), 2.6, 0.5, sides=3, rings=1)
    # ------------------------------------------------------------ 脚 (短く太い)
    for nm, s in (("l", -1), ("r", 1)):
        b = f"leg_{nm}"
        x = s * 2.1
        F += tube(b, "stone", [(x, 13.4, 0.0), (x, 9.8, -0.4), (x, 6.6, -0.9)], ra=[1.5, 1.4, 1.2], rb=[1.5, 1.4, 1.2], sides=4, hint=(1, 0, 0))
        F += tube(b, "stone", [(x, 6.6, -0.9), (x, 3.8, -0.3), (x, 1.4, 0.1)], ra=[1.2, 1.0, 1.2], rb=[1.2, 1.0, 1.2], sides=4, hint=(1, 0, 0))
        F += plate(b, "tabl" if nm == "r" else "fan", (x, 7.0, -1.6), 1.7, 1.4, th=0.5, tilt=(-50, 0, 0), segs=5)
        F += tube(b, "stone", [(x, 1.6, 0.4), (x, 0.8, -1.4), (x, 0.3, -3.8)], ra=[1.2, 1.2, 0.3], rb=[1.0, 0.7, 0.1], sides=4, hint=(1, 0, 0))
    # ------------------------------------------------------------ 銛: 扇珊瑚の平たい葉の穂先
    Sp = []
    Sp += tube("spear", "wood", [(0, -9.5, 0), (0, -2.0, 0), (0, 6.0, 0), (0, 11.0, 0)], ra=[0.55, 0.7, 0.65, 0.6], rb=[0.55, 0.7, 0.65, 0.6],
               sides=4, hint=(1, 0, 0), phase=45, caps=(True, False))
    for y in (-3.4, 0.6):
        Sp += tube("spear", "verd", [(0, y, 0), (0, y + 1.6, 0)], ra=[1.0, 1.0], rb=[1.0, 1.0], sides=4, hint=(1, 0, 0), phase=0, caps=(True, True))
    Sp += spike("spear", "tabl", (0, -9.5, 0), (0, -1, 0), 2.0, 0.6, sides=4, rings=1)
    # 葉: 縁がぎざぎざの平たい刃 (中央の稜が盛り上がる)
    ys = [10.0, 12.5, 15.5, 18.5, 21.0, 23.0]
    hw = [1.2, 3.4, 4.2, 3.3, 1.8, 0.0]
    notch = [0.0, -0.5, 0.4, -0.5, 0.0, 0.0]
    rows_f, rows_b = [], []
    for y, w, nt in zip(ys, hw, notch):
        w2 = max(0.0, w + nt)
        rows_f.append([V(-w2, y, 0.0), V(0, y + 0.3, -0.7), V(w2, y, 0.0)])
    Sp += grid("spear", "fan", rows_f, (0, 15, 5))
    Sp += grid("spear", "fan", [[V(p[0], p[1], 0.0 if k != 1 else 0.7) for k, p in enumerate(r)] for r in rows_f], (0, 15, -5))
    # 葉の縁の小さな突起 (海硝子)
    for sx in (-1, 1):
        Sp += spike("spear", "glass", (sx * 4.0, 15.7, 0.0), (sx, 0.5, 0), 2.2, 0.45, sides=3, rings=1)
        Sp += spike("spear", "glass", (sx * 3.2, 18.6, 0.0), (sx, 0.9, 0), 1.8, 0.4, sides=3, rings=1)
    Sp += spike("spear", "glass", (0, 22.9, 0.0), (0, 1, 0), 2.6, 0.5, sides=4, rings=1)
    Sp += tube("spear", "verd", [(0, 9.4, 0), (0, 10.6, 0)], ra=[1.2, 1.2], rb=[1.2, 1.2], sides=5, hint=(1, 0, 0), phase=0, caps=(True, True))
    return F, Sp, eyes


def statue_coral():
    F = []
    for s in (-1, 1):
        F += plate("statue_coral", "fan", (s * 6.2, 26.0, 0.3), 2.2, 1.8, th=0.5, tilt=(0, 0, -s * 55), segs=5)
        F += plate("statue_coral", "tabl", (s * 3.0, 1.6, -3.0), 1.7, 1.5, th=0.5, segs=5)
    F += plate("statue_coral", "brain", (0.0, 25.8, 1.8), 2.0, 1.6, th=0.6, tilt=(-20, 0, 0), segs=5)
    return F
HEAD_CENTER, HEAD_SCALE = (0, 30.0, -1.5), 18
