"""C案「鐘の守り手」: 低く幅広い鐘の輪郭。顔のない丸い銅の面 (中は空) と放射状の避雷針の冠、緑青の浮いた青銅の鎧、
地面まで届く重ね羽の裾、肩の丸い羽のうろこ、体を抱くように曲がった大きな風切りのマント (翼で身を包む梟のような構え)。
鉤槍は重い羊飼いの杖状で、先に大きな環の鉤。緑青の青銅 + 風化した灰緑の羽。"""
from common import *  # noqa

BRZ = [(28, 20, 14), (66, 46, 24), (108, 76, 38), (152, 108, 54), (194, 146, 80), (232, 190, 122)]
VERD = [(30, 62, 58), (52, 100, 88), (84, 140, 118), (126, 176, 148)]
GRN = [(22, 30, 36), (48, 62, 70), (84, 102, 112), (126, 146, 156), (170, 188, 196), (214, 226, 230)]
GRNF = [(26, 34, 42), (56, 72, 82), (96, 116, 128), (140, 160, 170), (186, 202, 210), (230, 238, 242)]
DARKW = [(22, 18, 18), (44, 36, 34), (68, 58, 54), (98, 86, 78)]
PAL = {"mats": {
    "steel": dict(ramp=GRN, kind="plate", base=0.6, per=8, noise=0.04),
    "feather": dict(ramp=GRNF, kind="feather", base=0.56, fvar=1, noise=0.03),
    "dkfeather": dict(ramp=GRN, kind="feather", base=0.68, fvar=1, noise=0.03, bw=0.4),
    "copper": dict(ramp=BRZ, base=0.72, noise=0.1, stain=dict(ramp=VERD, thr=0.86, up=0.32, conc=0.3)),
    "wood": dict(ramp=DARKW, base=0.7, stripe=7),
    "glow": dict(ramp=[(150, 214, 255), (210, 240, 255), (250, 252, 255)], base=0.8, glow=True),
}}
HEAD_CENTER, HEAD_SCALE = (0, 31.0, -2.0), 17.0


def build():
    F = []
    # ------------------------------------------------------------ 頭: 丸い銅の面 (顔の部分は空)
    R = [ring(26.4, 2.4, -2.6, 2.6), ring(28.2, 3.8, -4.0, 3.8), ring(30.0, 4.5, -4.8, 4.6), ring(32.0, 4.5, -4.8, 4.6),
         ring(34.0, 3.4, -3.8, 3.8), ring(35.4, 1.8, -2.0, 2.2)]
    F += loft2("head", R, lambda i, j: "void" if (i in (2,) and j in (7, 0)) else "copper", caps=("copper", "copper"))
    eyes = [dict(c=(1.7, 31.0, -4.4), rx=0.7, ry=0.5, zr=1.8), dict(c=(-1.7, 31.0, -4.4), rx=0.7, ry=0.5, zr=1.8)]
    # 放射状の避雷針の冠 (頭の後ろの面に扇)
    for k, a in enumerate((-72, -48, -24, 0, 24, 48, 72)):
        d = V(math.sin(math.radians(a)), math.cos(math.radians(a)), 0.25)
        b0 = V(0.0, 33.0, 3.4) + V(math.sin(math.radians(a)) * 3.0, math.cos(math.radians(a)) * 2.4, 0)
        ln = 6.4 if k % 2 == 0 else 5.0
        F += tube("head", "copper", [b0, b0 + nrm(d) * ln], ra=[0.8, 0.5], rb=[0.8, 0.5], sides=3, hint=(0, 0, 1))
        F += tube("head", "glow", [b0 + nrm(d) * ln, b0 + nrm(d) * (ln + 1.5)], ra=[0.5, 0.0], rb=[0.5, 0.0], sides=3, hint=(0, 0, 1))
    # ------------------------------------------------------------ 胴: 鐘
    T = [ring(12.4, 5.6, -4.2, 4.2), ring(16.0, 6.0, -4.6, 4.4), ring(20.4, 5.8, -4.8, 4.0), ring(24.0, 4.8, -3.6, 3.2),
         ring(25.4, 2.8, -2.4, 2.0)]
    F += loft2("body", T, lambda i, j: "copper" if i >= 1 else "steel", caps=("steel", "copper"))
    for y, step, w, h, fl in ((22.6, 1.0, 3.8, 4.6, 1.0), (18.6, 1.0, 4.0, 5.0, 1.2)):
        for k in np.arange(-2, 2.01, step):
            p, n = surf(T, y, k % 8)
            F += shingle("body", "feather", p, (0, -1, 0), n, w, h, fl)
    for y, w, h, fl, mat in ((15.6, 4.6, 7.2, 2.6, "dkfeather"), (11.6, 4.6, 7.0, 3.0, "feather")):
        for k in np.arange(0, 8, 0.8):
            if y < 13:
                p, n = surf(T, 12.6, k % 8)
                p = p + V(0, y - 12.6, 0)
            else:
                p, n = surf(T, y, k % 8)
            F += shingle("body", mat, p, (0, -1, 0), n, w, h, fl)
    # ------------------------------------------------------------ 肩の丸い羽のうろこ
    for s, a in ((-1, "arm_l"), (1, "arm_r")):
        F += ellipsoid(a, "copper", (s * 6.0, 23.8, 0.0), (3.4, 2.2, 3.4), segs=6, rings=1)
        for k in range(3):
            F += feather(a, "feather" if k != 1 else "dkfeather", (s * 3.6, 25.0 - 1.0 * k, -2.4 + 2.4 * k), (s * (10.0 - 0.3 * k), 19.2 - 0.9 * k, -2.4 + 2.4 * k),
                         4.6, ref=(s * 6, 30, -2.4 + 2.4 * k), fold=0.7, n_mid=0.5)
        F += tube(a, "steel", [(s * 6.0, 22.8, 0), (s * 6.6, 20.0, -0.6), (s * 6.6, 17.8, -1.0)], ra=[1.9, 1.7, 1.5], rb=[1.9, 1.7, 1.5], sides=5, hint=(1, 0, 0))
    for s, f, wrist, elbow in ((-1, "forearm_l", V(-6.6, 12.4, -3.0), V(-6.6, 17.5, -0.5)),
                               (1, "forearm_r", V(6.6, 12.4, -4.3), V(6.6, 17.5, -1.5))):
        F += tube(f, "copper", [elbow, (elbow + wrist) / 2, wrist], ra=[1.8, 1.9, 1.5], rb=[1.8, 1.9, 1.5], sides=5, hint=(1, 0, 0))
        F += ellipsoid(f, "steel", wrist + V(0, -0.8, -0.4), (1.5, 1.4, 1.6), segs=5, rings=1)
        if s == -1:
            for dx in (-1.0, 0.0, 1.0):
                F += spike(f, "copper", wrist + V(dx, -1.4, -0.6), (dx * 0.4, -0.5, -1.0), 2.6, 0.55, sides=3, rings=1)
    # ------------------------------------------------------------ 体を抱くマント (外へ広がって前へ巻く)
    for s, c in ((-1, "cloak_l"), (1, "cloak_r")):
        for i in range(4):
            F += feather(c, "feather" if i % 2 == 0 else "dkfeather", (s * (1.5 + 1.0 * i), 24.0 - 0.6 * i, 3.4 - 0.3 * i),
                         (s * (6.8 + 1.0 * i), 4.0 + 2.8 * i, 2.0 - 2.4 * i), 5.2, ref=(s * 3, 14, 10), fold=0.7, bow=0.9, n_mid=0.5)
        for i in range(2):
            F += feather(c, "dkfeather", (s * (1.6 + 2.4 * i), 24.2, 3.2), (s * (3.4 + 2.8 * i), 12.0 + 1.0 * i, 4.4), 4.0,
                         ref=(s * 4, 14, 10), fold=0.5)
    # ------------------------------------------------------------ 脚: 短く太い
    for s, b in ((-1, "leg_l"), (1, "leg_r")):
        x = s * 2.4
        F += tube(b, "steel", [(x, 13.4, 0.0), (x, 8.0, -0.6), (x, 3.8, 0.0)], ra=[2.1, 1.9, 1.6], rb=[2.1, 1.9, 1.6], sides=5, hint=(1, 0, 0))
        F += tube(b, "copper", [(x, 3.8, 0.0), (x, 1.6, -0.2)], ra=[1.7, 1.5], rb=[1.7, 1.5], sides=5, hint=(1, 0, 0))
        for dx in (-1.0, 0.0, 1.0):
            F += spike(b, "copper", (x + dx * 0.5, 1.2, -0.4), (dx * 0.3, -0.15, -1.0), 3.8, 0.7, sides=3, rings=1)
    # ------------------------------------------------------------ 鉤槍: 重い羊飼いの杖
    G = []
    G += tube("glaive", "wood", [(0, -10, 0), (0, 4, 0), (0, 24, 0)], ra=[0.95, 1.0, 0.85], rb=[0.95, 1.0, 0.85], sides=5, hint=(1, 0, 0))
    G += spike("glaive", "copper", (0, -9.6, 0), (0, -1, 0), 2.8, 0.95, sides=3, rings=1)
    for y in (-2.0, 8.0, 17.0):
        G += tube("glaive", "copper", [(0, y, 0), (0, y + 1.4, 0)], ra=[1.35, 1.35], rb=[1.35, 1.35], sides=5, hint=(1, 0, 0))
    # 大きな環の鉤
    pts = [(math.sin(math.radians(a)) * 5.2 - 5.2 - 0.0, 29.5 + math.cos(math.radians(a)) * 5.2 - 0.0, 0.0) for a in range(-30, 241, 30)]
    pts = [(-5.2 + 5.2 * math.cos(math.radians(a)), 29.0 + 5.2 * math.sin(math.radians(a)), 0.0) for a in range(-90, 221, 38)]
    G += tube("glaive", "copper", [(0, 24, 0)] + pts, ra=[1.0] * len(pts) + [0.5], rb=[0.9] * len(pts) + [0.45],
              sides=4, hint=(0, 0, 1))
    G += tube("glaive", "copper", [(0, 24, 0), (0, 30, 0), (0, 37.0, 0)], ra=[1.0, 0.9, 0.0], rb=[0.9, 0.8, 0.0], sides=4, hint=(1, 0, 0))
    G += tube("glaive", "glow", [(pts[-1][0], pts[-1][1], 0), (pts[-1][0] + 0.8, pts[-1][1] - 1.8, 0)], ra=[0.5, 0.0], rb=[0.45, 0.0], sides=4, hint=(0, 0, 1))
    for k, dz in enumerate((-1.0, 1.0)):
        G += feather("glaive", "feather" if k else "dkfeather", (0.9, 20.0, dz), (3.8, 8.0 - 2 * k, dz * 2.4), 2.8, ref=(5, 16, dz * 3), fold=0.3)
    return F, G, eyes
