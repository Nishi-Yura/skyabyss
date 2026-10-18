"""C案「磯巾着の鬣」: 潮の民の干からびた体に、イソギンチャクのような触手の珊瑚が鬣になって垂れる。
頭の周り・肩・背のこぶから、細い触手が放射状に広がって下へしなだれる (A の上へ尖る枝、B の板と円盤と対照的な、
丸く垂れる有機的な輪郭)。胴は昆布を巻いた干物。左手は触手の指、銛は反った長い角と根元の触手の襟。"""
from common import *  # noqa

HIDE = [(34, 26, 26), (66, 50, 44), (104, 82, 66), (142, 116, 90), (178, 150, 118), (210, 184, 148)]
BONE = [(40, 38, 40), (84, 78, 74), (124, 116, 106), (166, 156, 138), (204, 192, 168)]
PEACH = [(96, 40, 40), (156, 68, 56), (208, 108, 76), (240, 156, 108), (255, 204, 160)]
ORNG = [(90, 36, 22), (144, 62, 32), (200, 98, 44), (238, 142, 70), (255, 188, 116)]
PINK = [(76, 26, 54), (126, 44, 78), (180, 72, 104), (224, 112, 128), (250, 162, 154)]
VIOL = [(44, 26, 70), (78, 46, 112), (118, 74, 152), (162, 112, 188), (206, 164, 216)]
KELP = [(18, 40, 38), (30, 70, 62), (48, 104, 88), (78, 142, 112), (116, 176, 140)]
MOSS = [(36, 46, 30), (60, 80, 44), (92, 114, 60), (130, 150, 80)]
GLASS = [(60, 150, 176), (110, 210, 226), (180, 244, 252)]
PAL = {"mats": {
    "hide": dict(ramp=HIDE, base=0.64, noise=0.12, crack=0.14, stain=dict(ramp=MOSS, thr=0.95, up=0.3, low=0.1)),
    "bone": dict(ramp=BONE, base=0.62, crack=0.1, min=0),
    "dark": dict(ramp=[(6, 6, 10), (16, 12, 16), (34, 26, 28)], base=0.5, crack=0.0, min=0),
    "peach": dict(ramp=PEACH, base=0.62, lit=0.22, crack=0.0, min=0, noise=0.1, dots=0.1),
    "orng": dict(ramp=ORNG, base=0.62, lit=0.22, crack=0.0, min=0, noise=0.1, dots=0.1),
    "pink": dict(ramp=PINK, base=0.62, lit=0.22, crack=0.0, min=0, noise=0.1, dots=0.1),
    "viol": dict(ramp=VIOL, base=0.6, lit=0.22, crack=0.0, min=0, noise=0.1, dots=0.1),
    "kelp": dict(ramp=KELP, base=0.55, lit=0.2, crack=0.0, min=0, stripe=3, axis="v", noise=0.12),
    "glass": dict(ramp=GLASS, base=0.6, lit=0.2, crack=0.0, min=0),
}, "noses": [(0.0, 27.4, -6.9)], "face_mat": "hide", "glow_mats": ["glass"]}
HINT = 128
HEAD_CENTER, HEAD_SCALE = (0, 30.0, -2.5), 17
CYC = ("peach", "pink", "orng", "viol")


def tent(bone, mat, base, d, length, r, droop=3.0, side=(0, 0, 0), sides=3, rings=2):
    """触手: 根元から d の向きへ出て、先が下 (droop) へしなだれる。"""
    d = nrm(d)
    return spike(bone, mat, base, d, length, r, sides=sides, rings=rings, bend=np.array([0, -droop, 0.0]) + np.asarray(side, float))


def build():
    F = []
    # ------------------------------------------------------------ 頭 (干からびた頭蓋。うつむく)
    R = [ring(30.4, 2.5, -2.9, 3.0, -2.0), ring(28.9, 2.9, -4.0, 2.9, -2.4), ring(27.3, 2.5, -4.6, 2.5, -2.4), ring(25.8, 1.4, -4.2, 1.7, -2.0)]
    for s, k in ((1, 1), (-1, 7)):
        R[1][k] += V(-s * 0.7, -0.1, 1.4)
        R[0][k] += V(s * 0.1, 0.3, -0.6)
    R[2][0] += V(0, 0.2, -0.4)
    eyes = [dict(c=R[1][1] + V(0, 0, -0.2), rx=0.95, ry=0.6, pr=(0.55, 0.32), tilt=0.15),
            dict(c=R[1][7] + V(0, 0, -0.2), rx=0.95, ry=0.6, pr=(0.55, 0.32), tilt=0.15)]
    F += loft("head", "hide", R, caps=("hide", "hide"))
    for z in (-5.0, -3.6):
        for s in (-1, 1):
            F += spike("head", "bone", (s * 0.9, 26.1, z - 2.0), (0, -1, 0.05), 1.3, 0.38, sides=3, rings=1)
    F += tube("head", "bone", [(-2.0, 25.6, -1.0), (-1.4, 24.6, -5.0), (0, 24.5, -7.0), (1.4, 24.6, -5.0), (2.0, 25.6, -1.0)],
              ra=[0.55, 0.62, 0.5, 0.62, 0.55], rb=[0.5, 0.5, 0.45, 0.5, 0.5], sides=4, hint=(0, 1, 0),
              axis_fn=lambda p: np.array([0, p[1] + 0.3, p[2] + 2.0]), hide_cos=0.7)
    F += tube("head", "hide", [(0, 23.2, -0.8), (0, 25.4, -1.2)], ra=[1.3, 1.1], rb=[1.2, 1.0], sides=4, hint=(1, 0, 0))
    # 触手の鬣: 頭の上と後ろ・横から放射状に出て、先が下へしなだれる
    hc = V(0.0, 29.0, -2.3)
    k = 0
    for el, az_list, ln, r, dr in ((78, (0, 120, 240), 7.0, 1.35, 3.0), (40, (0, 60, 120, 240, 300), 9.0, 1.3, 6.5), (8, (65, 118, 242, 295), 9.6, 1.2, 9.5)):
        for az in az_list:
            a, e = math.radians(az), math.radians(el)
            d = V(math.sin(a) * math.cos(e), math.sin(e), -math.cos(a) * math.cos(e) * -1.0 if False else math.cos(a) * math.cos(e))
            d = V(math.sin(a) * math.cos(e), math.sin(e), math.cos(a) * math.cos(e))     # az 0 は後ろ (+Z)
            base = hc + V(d[0] * 2.4, d[1] * 2.6, d[2] * 2.4)
            F += tent("head", CYC[k % 4], base, d, ln * (0.9 + 0.2 * ((k * 7) % 3) / 2), r, droop=dr + 1.0 * (k % 2), side=(d[0] * 1.0, 0, d[2] * 1.0))
            k += 1
    # ------------------------------------------------------------ 胴 (昆布を巻いた干物。猫背)
    T = [ring(14.4, 2.6, -2.0, 2.3, 0.0), ring(17.6, 3.2, -2.8, 2.7, -0.4), ring(21.2, 3.9, -3.4, 2.9, -1.2), ring(24.2, 3.1, -2.6, 2.5, -1.8)]
    F += loft("body", "hide", T, caps=("hide", "hide"))
    # 昆布の巻き (胴を巻く 2 本の帯)
    for (y0, y1, rx, zf, zb, zc) in ((19.4, 20.6, 4.15, -3.6, 3.0, -0.9),):
        F += loft("body", "kelp", [ring(y0, rx, zf, zb, zc), ring(y1, rx, zf, zb, zc)])
    F += ellipsoid("body", "hide", (1.0, 22.4, 1.2), (3.0, 2.6, 2.4), segs=5, rings=1)      # 背のこぶ
    for x, m, ex in ((-1.8, "pink", -1.0), (0.4, "peach", 0.0), (2.4, "viol", 1.0)):
        F += tent("body", m, V(x, 23.6, 2.8), (ex * 0.4, 0.6, 1.0), 7.4, 1.0, droop=7.0)
    # 脇腹の折れた肋
    for s in (-1, 1):
        for y, ln in ((22.4, 3.4), (12.0, 0.0)):
            if ln:
                F += spike("body", "bone", (s * 3.7, y, -2.0), (s * 0.9, 0.2, -0.5), ln, 0.5, sides=3, rings=1, bend=(0, -0.8, -0.6))
    # 腰の昆布の前垂れ
    for x, yb, w in ((-2.2, 8.6, 1.0), (0, 7.4, 1.2), (2.2, 8.8, 1.0)):
        rows = [[V(x - w, 14.2, -2.3), V(x + w, 14.2, -2.3)], [V(x - w * 0.9, 11.4, -2.9), V(x + w * 0.9, 11.4, -2.9)],
                [V(x, yb, -3.3), V(x, yb, -3.3)]]
        F += grid("body", "kelp", rows, (x, 11, 0))
    # ------------------------------------------------------------ 腕
    sh = V(4.0, 24.0, -1.4)
    elbow = V(3.75, 16.0, -0.5)
    F += tube("arm_r", "hide", [sh + V(0.3, 0.8, 0), sh + V(0.2, -0.5, 0), elbow + V(0, 0.4, 0)],
              ra=[0.9, 1.4, 0.8], rb=[0.9, 1.3, 0.8], sides=4, hint=(1, 0, 0), caps=(True, False))
    for m, (dx, dz, ln) in (("orng", (0.6, 0.0, 6.0)), ("pink", (0.9, 0.6, 5.0))):
        F += tent("arm_r", m, sh + V(0.8, 0.6, dz), (dx, 0.7, dz * 0.4), ln, 0.9, droop=6.0, side=(1.0, 0, 0))
    hand = V(3.75, 11.5, -2.5)
    F += tube("forearm_r", "hide", [elbow, (3.75, 13.6, -1.6), hand], ra=[0.8, 0.8, 0.7], rb=[0.8, 0.8, 0.7], sides=4, hint=(1, 0, 0))
    F += tube("forearm_r", "kelp", [(3.75, 15.0, -0.9), (3.75, 13.6, -1.6)], ra=[1.1, 1.1], rb=[1.1, 1.1], sides=4, hint=(1, 0, 0), phase=0, caps=(True, True))
    F += ellipsoid("forearm_r", "hide", hand, (1.0, 1.0, 1.0), segs=4, rings=1)
    for i in range(3):
        dx = (i - 1) * 0.7
        F += spike("forearm_r", "hide", hand + V(dx * 0.5, -0.4, -0.3), (dx * 0.2, -0.3, -1.0), 2.2, 0.45, sides=3, rings=1)
    shl = V(-4.0, 24.0, -1.4)
    elbl = V(-3.75, 16.0, 0.0)
    F += tube("arm_l", "hide", [shl + V(-0.3, 0.9, 0), shl + V(-0.2, -0.5, 0), elbl + V(0, 0.4, 0)],
              ra=[1.1, 1.8, 1.3], rb=[1.1, 1.7, 1.3], sides=5, hint=(1, 0, 0), caps=(True, False))
    for m, (dz, ln) in (("peach", (0.0, 6.4)),):
        F += tent("arm_l", m, shl + V(-0.8, 0.6, dz), (-0.7, 0.7, dz * 0.4), ln, 1.0, droop=6.4, side=(-1.0, 0, 0))
    F += tube("forearm_l", "hide", [elbl, (-3.8, 12.4, -0.3), (-3.8, 9.2, -0.7)], ra=[1.3, 1.6, 1.2], rb=[1.3, 1.6, 1.2], sides=5, hint=(1, 0, 0))
    palm = V(-3.8, 8.6, -0.7)
    F += ellipsoid("forearm_l", "hide", palm, (1.5, 1.1, 1.4), segs=4, rings=1)
    for dx, dz, m in ((-1.2, -0.8, "peach"), (-0.3, -1.2, "orng"), (0.8, -1.0, "pink"), (1.4, 0.2, "viol")):
        F += tent("forearm_l", m, palm + V(dx * 0.7, -0.6, dz * 0.5), (dx * 0.4, -1.0, dz * 0.8), 4.6, 0.75, droop=0.0, side=(dx * 0.5, -0.4, dz * 1.2))
    # ------------------------------------------------------------ 脚 (細く曲がる)
    for nm, s in (("l", -1), ("r", 1)):
        b = f"leg_{nm}"
        x = s * 2.0
        F += tube(b, "hide", [(x, 13.3, 0.0), (x, 7.8, -2.2)], ra=[1.2, 1.0], rb=[1.2, 1.0], sides=5, hint=(1, 0, 0))
        F += tube(b, "hide", [(x, 7.8, -2.2), (x, 1.8, 0.3)], ra=[1.0, 0.85], rb=[1.0, 0.85], sides=5, hint=(1, 0, 0))
        F += tube(b, "kelp", [(x, 8.6, -1.9), (x, 7.2, -2.3)], ra=[1.3, 1.3], rb=[1.3, 1.3], sides=4, hint=(1, 0, 0), phase=0, caps=(True, True))
        F += tube(b, "hide", [(x, 1.8, 0.4), (x, 0.8, -1.6), (x, 0.3, -4.0)], ra=[0.9, 0.9, 0.12], rb=[0.8, 0.6, 0.1], sides=4, hint=(1, 0, 0))
    # ------------------------------------------------------------ 銛: 反った長い角と根元の触手の襟
    Sp = []
    Sp += tube("spear", "hide", [(0, -9.5, 0), (0, 0.0, 0), (0, 8.0, 0)], ra=[0.6, 0.75, 0.8], rb=[0.6, 0.75, 0.8], sides=5, hint=(1, 0, 0), caps=(True, False))
    horn = [V(0, 7.0, 0), V(0.4, 13.0, 0), V(0.2, 19.0, 0), V(-0.8, 27.0, 0)]
    Sp += tube("spear", "peach", horn, ra=[1.3, 1.25, 0.8, 0.0], rb=[1.3, 1.25, 0.8, 0.0], sides=5, hint=(1, 0, 0), phase=22.0)
    for y, rr, dx in ((10.2, 1.7, 0.2), (14.6, 1.5, 0.3), (18.6, 1.15, 0.2)):
        Sp += tube("spear", "orng", [(dx, y, 0), (dx, y + 1.1, 0)], ra=[rr, rr], rb=[rr, rr], sides=5, hint=(1, 0, 0), phase=22.0, caps=(True, True))
    # 根元の襟: 触手が放射状に広がり、先が下へ垂れる
    for i in range(7):
        a = math.radians(360 * i / 7 + 10)
        d = V(math.sin(a), 0.55, math.cos(a))
        Sp += tent("spear", CYC[i % 4], V(math.sin(a) * 1.0, 6.4, math.cos(a) * 1.0), d, 5.2, 0.7, droop=3.4, side=(d[0] * 0.8, 0, d[2] * 0.8))
    for y in (-3.0, 0.8):
        Sp += tube("spear", "kelp", [(0, y, 0), (0, y + 1.5, 0)], ra=[1.0, 1.0], rb=[1.0, 1.0], sides=4, hint=(1, 0, 0), phase=0, caps=(True, True))
    Sp += spike("spear", "peach", (0, -9.5, 0), (0, -1, 0), 2.0, 0.6, sides=4, rings=1)
    Sp += spike("spear", "glass", (-0.7, 25.6, 0.0), (-0.12, 1, 0), 2.6, 0.45, sides=4, rings=1)
    return F, Sp, eyes


def statue_coral():
    F = []
    for s in (-1, 1):
        F += tent("statue_coral", "orng", V(s * 4.8, 25.0, 0.0), (s * 0.8, 1, 0.1), 6.0, 0.9, droop=4.0, side=(s * 1.0, 0, 0))
        F += tent("statue_coral", "peach", V(s * 2.6, 1.0, -3.2), (s * 0.5, 1, 0), 4.4, 0.9, droop=2.0, side=(s * 0.6, 0, 0))
    F += tent("statue_coral", "pink", V(0.0, 20.0, 3.0), (0, 0.8, 1), 7.0, 1.0, droop=5.0)
    return F
