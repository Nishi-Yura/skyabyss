"""C 案: 銅角の雷獣 — 肩の高い鬣犬の体。銅の鎧の帯、稲妻の形に折れる水晶の大角、背の避雷針 (がいし) 、尾の先の帯電した球。"""
from __future__ import annotations

from kit import *  # noqa

NAME = "C 銅角の雷獣"
HINT = 256

GUN = [(12, 16, 28), (24, 30, 46), (38, 46, 68), (56, 68, 94), (80, 94, 124), (110, 126, 156)]
FROST = [(52, 64, 90), (90, 106, 136), (132, 150, 182), (180, 196, 222), (226, 236, 250)]
DARKC = [(8, 10, 18), (16, 20, 32), (28, 34, 50)]
COPPER = [(40, 18, 14), (84, 40, 22), (132, 68, 32), (176, 100, 44), (214, 136, 64), (244, 178, 96)]
COPD = [(40, 20, 16), (78, 38, 22), (120, 62, 30), (164, 94, 44)]
CER = [(88, 100, 124), (140, 156, 184), (196, 208, 228), (238, 244, 255)]
CRYS = [(30, 96, 140), (84, 190, 236), (150, 226, 252), (214, 246, 255)]
PAL = dict(
    mats={
        "fur": dict(ramp=GUN, base=0.54, lit=0.30, noise=0.05, facevar=0.015, crack=0, min=0, streak=0.14, ao=0.14),
        "furd": dict(ramp=GUN, base=0.34, lit=0.26, noise=0.05, crack=0, min=0, streak=0.1, ao=0.1),
        "frost": dict(ramp=FROST, base=0.58, lit=0.30, noise=0.06, crack=0, min=0, streak=0.12),
        "dark": dict(ramp=DARKC, base=0.5, lit=0.3, noise=0.12, crack=0, min=0),
        "copper": dict(ramp=COPPER, base=0.46, lit=0.32, noise=0.07, crack=0.22, min=1, streak=0.1),
        "copd": dict(ramp=COPD, base=0.5, lit=0.3, noise=0.07, crack=0, min=0),
        "cer": dict(ramp=CER, base=0.58, lit=0.34, noise=0.05, crack=0, min=0),
        "crystal": dict(ramp=CRYS, base=0.42, lit=0.40, noise=0.05, crack=0, min=0, facevar=0.14),
        "eye": dict(ramp=DARKC, base=0.5, lit=0.2, noise=0.05, crack=0, min=0),
    },
    glow_mats=["crystal"],
)


def antler(s, F):
    """稲妻の形に折れる大角 (頭の骨)。主枝は 5 節の水晶、折れ目から枝が出る。"""
    pts = [(4.5, 49.0, -29.0), (9.0, 54.0, -25.0), (5.0, 59.0, -26.5), (11.0, 65.0, -22.0), (7.0, 70.0, -24.0), (13.0, 76.0, -20.0)]
    P = [np.array([s * x, y, z + (y - 49.0) * 0.55]) for x, y, z in pts]
    rs = [3.0, 2.6, 2.3, 2.0, 1.7]
    for i in range(5):
        d = P[i + 1] - P[i]
        F += crystal("head", "crystal", P[i] - nrm(d) * 1.0, d, np.linalg.norm(d) + 1.6, rs[i], sides=3, mid=0.6, midk=1.0, r0k=0.9)
    br = [(1, (0.9, 0.6, -0.3), 10.0, 2.0), (2, (-0.6, 0.8, 0.2), 9.0, 1.8), (3, (0.9, 0.5, 0.2), 9.0, 1.6), (4, (-0.5, 0.8, 0.1), 7.5, 1.4)]
    for i, d, ln, r in br:
        dd = np.array([s * d[0], d[1], d[2]])
        F += crystal("head", "crystal", P[i], dd, ln, r, sides=3, mid=0.45)


def tower(F, z, top, h):
    """背の避雷針: 銅の棒に、がいしの板を 3 枚、先に水晶。"""
    F += crystal("mane", "copper", (0, top - 1.0, z), (0, 1, 0.25), h, 1.5, sides=3, mid=0.9, midk=1.0, r0k=1.0)
    for k, (t, w) in enumerate(((0.30, 8.0), (0.52, 6.0))):
        y = top - 1.0 + h * t
        F += fbox("mane", "cer", (0, y, z + h * t * 0.25), (w, 0.9, w * 0.8), R=rot_x(-14), taper=(0.8, 0.8), cut=0.0)
    F += crystal("mane", "crystal", (0, top - 1.0 + h * 0.8, z + h * 0.2), (0, 1, 0.25), 7.5, 1.8, sides=3, mid=0.4)


def build():
    F = []
    sgn = 1 if pv("leg_fl")[0] > 0 else -1

    # ================================================================ 胴: 肩が高く尻が低い
    st_body = [(30, 30.0, 22.0, 5.0, 2.4, 2.4), (24, 32.0, 18.5, 8.2, 3.6, 4.2), (14, 34.0, 18.0, 9.4, 4.0, 5.0),
               (4, 38.0, 17.0, 10.4, 4.4, 5.2), (-6, 43.0, 14.0, 12.0, 5.2, 5.8)]
    st_chest = [(-6, 43.0, 14.0, 12.0, 5.2, 5.8), (-15, 49.0, 11.0, 13.6, 6.0, 5.6), (-24, 49.0, 17.0, 11.0, 5.0, 4.4),
                (-31, 47.0, 26.0, 8.0, 3.8, 3.4)]
    pb, pc = Prof(st_body), Prof(st_chest)
    F += torso("body", "furd", [pb.ring(z) for z, *_ in sorted(st_body, key=lambda s: -s[0])], caps=(True, False))
    F += torso("chest", "furd", [pc.ring(z) for z, *_ in sorted(st_chest, key=lambda s: -s[0])], caps=(False, True))
    # 銅の鎧の帯 (後ろへ重なる)
    for z0, z1 in ((26, 19), (19, 12), (12, 5), (5, -6)):
        F += band("body", "copper", pb, z0, z1, 0.6, 2.2, lip=0.8, lip_mat="copd")
    for z0, z1 in ((-6 + 0.0, -13), (-13, -20), (-20, -27)):
        F += band("chest", "copper", pc, z0, z1, 0.6, 2.6, lip=0.8, lip_mat="copd")
    # 肩の大きな銅の板 (肩甲)
    for s in (1, -1):
        F += fbox("chest", "copper", (s * 10.5, 47.0, -15.0), (9.0, 2.4, 14.0), R=rot_z(-s * 30), taper=(0.7, 0.8), cut=0.0)
    # 胸の白い毛
    for z, x, y, ln in ((-13, 0, 11.0, 7.0), (-18, 4.5, 11.5, 6.0), (-18, -4.5, 11.5, 6.0), (-23, 0, 17.5, 5.0)):
        F += tuft("chest", "frost", (x, y, z), (x * 0.1, -1, -0.35), ln, 2.3, sides=3)

    # ================================================================ 頭: 幅広で低い (銅の頭の板 + 水晶の角と牙)
    hd = [
        sring(-27, 49.0, 31.0, 8.2, 4.6, 5.0),
        sring(-34, 50.5, 30.0, 9.4, 5.0, 5.6),
        sring(-41, 45.0, 30.5, 7.0, 3.4, 4.2),
        sring(-46, 41.0, 32.0, 4.8, 2.4, 3.0),
        sring(-50, 39.0, 33.0, 3.4, 1.7, 2.2),
    ]
    F += torso("head", "furd", hd, caps=(True, False))
    F += poly("head", "dark", hd[-1], np.array([0, 36, -40.0]))
    F += fbox("head", "dark", (0, 38.6, -50.8), (4.2, 3.0, 2.4), taper=(0.8, 0.8), cut=0.0)
    # 頭の板 (額から鼻筋へ)
    ph = Prof([(-27, 49.0, 31.0, 8.2, 4.6, 5.0), (-34, 50.5, 30.0, 9.4, 5.0, 5.6), (-41, 45.0, 30.5, 7.0, 3.4, 4.2), (-46, 41.0, 32.0, 4.8, 2.4, 3.0)])
    F += band("head", "copper", ph, -29, -36, 0.5, 1.6, cols=(7, 0, 1, 2), lip=0.6, lip_mat="copd")
    F += band("head", "copper", ph, -36, -45, 0.3, 1.2, cols=(0, 1), lip=0.6, lip_mat="copd")
    for s in (1, -1):
        F += fbox("head", "copd", (s * 6.4, 49.0, -38.4), (4.6, 2.0, 7.4), R=rot_z(-s * 12) @ rot_y(s * 8), taper=(0.7, 0.8), cut=0.0)
        F += ellipsoid("head", "furd", (s * 8.6, 49.0, -27.0), (2.6, 3.0, 1.8), segs=6, rings=1)   # 小さな丸い耳
        antler(s, F)
    eyes = []
    for s in (1, -1):
        c = (s * 5.9, 44.6, -39.2)
        F += ellipsoid("head", "eye", c, (2.2, 2.2, 2.2), segs=6, rings=1)
        eyes.append(dict(c=c, r=2.3, dir=(s * 0.55, 0.05, -1), a=2.0, b=1.1, tilt=0.5))
    jw = [
        sring(-32, 35.0, 28.5, 5.4, 3.4, 3.0),
        sring(-40, 34.5, 28.5, 4.8, 3.0, 2.4),
        sring(-47, 34.5, 30.5, 3.0, 2.0, 1.6),
        sring(-50, 34.5, 31.8, 2.2, 1.4, 1.2),
    ]
    F += torso("jaw", "fur", jw, caps=(True, True))
    for s in (1, -1):
        F += crystal("jaw", "crystal", (s * 3.4, 33.0, -44.0), (s * 0.15, 1, -0.6), 13.0, 2.0, sides=4, mid=0.5, bend=(0, 0, -5.0))   # 反った水晶の牙
        F += spike("head", "frost", (s * 2.6, 34.2, -47.0), (0, -1, -0.15), 4.4, 1.1, sides=3, rings=1)

    # ================================================================ 背の避雷針 (がいし)
    for z, top, h in ((-22, 52.5, 14.0), (-10, 51.0, 16.0), (2, 44.0, 14.0), (14, 38.0, 12.0)):
        tower(F, z, top, h)

    # ================================================================ 脚
    def foreleg(side):
        s = pv("leg_f" + side)[0] * 1.3
        b = "leg_f" + side
        F.extend(limb(b, "furd", [(s, 33.0, -15.0), (s, 23.0, -14.8), (s, 14.0, -14.5)], [7.0, 5.8, 4.2], [6.2, 5.0, 3.8], sides=5, hint=(1, 0, 0), phase=36))
        lo = b + "_lo"
        F.extend(limb(lo, "fur", [(s, 14.5, -14.5), (s, 8.0, -15.8), (s, 2.5, -17.0)], [4.4, 3.6, 3.8], [4.0, 3.2, 3.4], sides=5, hint=(1, 0, 0), phase=36))
        # 銅の巻き線 (輪)
        for y, z in ((11.0, -15.0), (6.0, -16.0)):
            r0 = [np.array([s + math.cos(a) * 5.0, y, z + math.sin(a) * 5.0]) for a in [k * math.pi / 2.5 for k in range(5)]]
            r1 = [p + np.array([0, 2.0, 0]) for p in r0]
            F.extend(rloft(lo, "copper", [r0, r1], (False, False)))
        F.extend(paw(lo, "dark", "copper", (s, 0.0, -20.0), 7.0, 10.0, 3.6, dirz=-1, cl=3.4, cut=0.0))

    def hindleg(side):
        s = pv("leg_b" + side)[0] * 1.35
        b = "leg_b" + side
        F.extend(ellipsoid(b, "furd", (s * 1.05, 22.5, 12.5), (6.2, 7.0, 6.6), segs=6, rings=2))
        F.extend(limb(b, "furd", [(s, 23.0, 12.5), (s, 16.5, 10.5), (s, 12.0, 8.5)], [5.6, 4.6, 3.4], [5.0, 4.0, 3.0], sides=5, hint=(1, 0, 0), phase=36))
        F.extend(fbox(b, "copper", (s * 1.25, 25.5, 12.5), (3.0, 11.0, 11.0), R=rot_z(-sgn * np.sign(s) * 14), taper=(0.6, 0.8), cut=0.0))   # 腿の銅板
        lo = b + "_lo"
        F.extend(limb(lo, "fur", [(s, 12.0, 8.5), (s, 9.0, 13.0), (s, 7.5, 17.0)], [3.6, 3.0, 2.6], [3.2, 2.7, 2.3], sides=5, hint=(1, 0, 0), phase=36))
        F.extend(limb(lo, "fur", [(s, 7.5, 17.0), (s, 4.0, 15.5), (s, 2.4, 14.6)], [2.6, 2.4, 2.5], [2.3, 2.1, 2.2], sides=5, hint=(1, 0, 0), phase=36))
        for y, z in ((6.0, 15.8),):
            r0 = [np.array([s + math.cos(a) * 3.6, y, z + math.sin(a) * 3.6]) for a in [k * math.pi / 2.5 for k in range(5)]]
            r1 = [p + np.array([0, 2.4, 0]) for p in r0]
            F.extend(rloft(lo, "copper", [r0, r1], (False, False)))
        F.extend(paw(lo, "dark", "copper", (s, 0.0, 12.0), 6.0, 9.5, 3.6, dirz=-1, cl=3.4, cut=0.0))

    for side in ("l", "r"):
        foreleg(side)
        hindleg(side)

    # ================================================================ 尾: 銅の輪を巻いた棒 + 帯電した球
    F += tube("tail", "furd", [(0, 27.0, 27.0), (0.5, 26.0, 37.0), (2.0, 24.0, 46.0)], [3.6, 3.1, 2.7], [3.4, 2.9, 2.5], sides=5,
              hint=(1, 0, 0), phase=36, caps=(False, False))
    F += tube("tail_2", "furd", [(2.0, 24.0, 46.0), (0.5, 24.5, 54.0), (0, 25.0, 62.0)], [2.7, 2.5, 2.3], [2.5, 2.3, 2.1], sides=5,
              hint=(1, 0, 0), phase=36, caps=(False, False))
    for bone, zs in (("tail", (33, 42)), ("tail_2", (50, 58))):
        for z in zs:
            t = (z - 27) / 35.0
            c = np.array([0.7 * t, 27.0 - 3.0 * min(t * 2, 1.0) + (0 if t < 0.5 else 0.5), z])
            r = 4.4 - 1.4 * t
            r0 = [c + np.array([math.cos(a) * r, math.sin(a) * r, 0]) for a in [k * math.pi / 2.5 for k in range(5)]]
            r1 = [p + np.array([0, 0, 2.6]) for p in r0]
            F += rloft(bone, "copper", [r0, r1], (False, False))
    # 先の帯電した球と棘
    cb = np.array([0.0, 25.5, 70.0])
    F += tube("tail_3", "copper", [(0, 25.0, 62.0), tuple(cb + np.array([0, 0.3, -5.0]))], [2.2, 2.2], [2.0, 2.0], sides=5, hint=(1, 0, 0), phase=36, caps=(False, False))
    F += ellipsoid("tail_3", "crystal", cb, (6.0, 6.0, 6.0), segs=6, rings=2)
    for k in range(6):
        a = math.radians(k * 60.0 + 20)
        d = np.array([math.sin(a), math.cos(a), 0.45])
        F += crystal("tail_3", "crystal", cb + nrm(d) * 4.8, d, 8.0, 1.9, sides=3, mid=0.4)
    F += crystal("tail_3", "crystal", cb + np.array([0, 0, 5.0]), (0, 0.1, 1), 12.0, 2.4, sides=4, mid=0.4)

    return F, eyes, extra


def extra(c, col, glow):
    return fur_extra(c, col, glow, vein=None, belly=("fur", FROST),
                     bolts=dict(lines=[[(-22, 46), (-16, 40), (-21, 34), (-13, 28)], [(20, 33), (14, 28), (19, 24)]],
                                color=(200, 228, 255), w=0.7, mats=["furd", "fur"], glow=True))
