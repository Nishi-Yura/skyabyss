"""B 案: 羽鬣の獅子 — 胸と肩の厚い獅子。頭のまわりに放射する羽根の大きな襟、背に羽根の稜線、尾の先は羽根の扇。"""
from __future__ import annotations

from kit import *  # noqa

NAME = "B 羽鬣の獅子"
HINT = 256

STORM = [(14, 18, 32), (26, 32, 52), (42, 52, 80), (64, 78, 108), (92, 108, 140), (126, 144, 176)]
FROST = [(58, 70, 98), (96, 112, 144), (140, 158, 190), (190, 206, 230), (232, 240, 252)]
DARKC = [(8, 10, 18), (16, 20, 32), (28, 34, 50)]
COPPER = [(58, 28, 20), (110, 54, 28), (166, 92, 42), (214, 134, 62), (248, 190, 108)]
FTH = [(22, 28, 50), (40, 52, 84), (66, 84, 124), (98, 120, 162), (136, 158, 198), (176, 196, 226)]
TIP = [(214, 232, 252), (250, 255, 255)]
PAL = dict(
    mats={
        "fur": dict(ramp=STORM, base=0.54, lit=0.30, noise=0.05, facevar=0.015, crack=0, min=0, streak=0.16, ao=0.14),
        "furd": dict(ramp=STORM, base=0.34, lit=0.26, noise=0.05, crack=0, min=0, streak=0.1, ao=0.1),
        "frost": dict(ramp=FROST, base=0.58, lit=0.30, noise=0.06, crack=0, min=0, streak=0.12),
        "dark": dict(ramp=DARKC, base=0.5, lit=0.3, noise=0.12, crack=0, min=0),
        "copper": dict(ramp=COPPER, base=0.62, lit=0.32, noise=0.08, crack=0, min=0),
        "fth": dict(ramp=FTH, base=0.5, lit=0.2, noise=0, crack=0, min=0, facevar=0),
        "eye": dict(ramp=DARKC, base=0.5, lit=0.2, noise=0.05, crack=0, min=0),
    },
    glow_mats=[],
)


def fan(bone, base, n, spread_deg, length, width, up=(0, 1, 0), axis=(0, 0, 1), mat="fth", jitter=0.12, hint=(0, 1, 0), droop=0.0):
    """根元から扇状に広がる羽根。axis = 中心の向き、spread_deg = 全体の開き (up 軸まわり)。"""
    out = []
    axis = nrm(np.asarray(axis, float))
    up = nrm(np.asarray(up, float))
    side = nrm(np.cross(axis, up))
    for i in range(n):
        t = (i / (n - 1) - 0.5) if n > 1 else 0.0
        a = math.radians(spread_deg * t)
        d = axis * math.cos(a) + side * math.sin(a)
        ln = length * (1 - 0.22 * abs(t) * 2) * (1 + jit(i, jitter))
        out += feather6(bone, mat, base, np.asarray(base, float) + d * ln, width, hint=hint, bend=(0, -droop, 0))
    return out


def build():
    F = []
    sgn = 1 if pv("leg_fl")[0] > 0 else -1

    # ================================================================ 胴: 胸の厚い獅子
    body_rings = [
        sring(30, 33.0, 23.0, 5.2, 2.4, 2.4),
        sring(24, 36.0, 19.0, 9.0, 3.8, 4.4),
        sring(14, 37.0, 17.0, 10.6, 4.4, 5.4),
        sring(4, 36.0, 18.5, 9.8, 4.0, 4.8),
        sring(-6, 40.0, 13.0, 12.0, 5.0, 5.8),
    ]
    chest_rings = [
        sring(-6, 40.0, 13.0, 12.0, 5.0, 5.8),
        sring(-15, 46.0, 9.5, 14.0, 6.2, 5.6),
        sring(-24, 47.0, 14.0, 11.5, 5.2, 4.6),
        sring(-31, 47.0, 25.0, 8.5, 4.0, 3.6),
    ]
    F += torso("body", "fur", body_rings, caps=(True, False))
    F += torso("chest", "fur", chest_rings, caps=(False, True))
    # 胸の羽毛 (腹側に下向きの羽根)
    for z, x, ln in ((-12, 0, 11), (-17, 4.5, 10), (-17, -4.5, 10), (-22, 2.0, 9), (-22, -2.0, 9), (-8, 3.5, 9), (-8, -3.5, 9)):
        F += feather("chest", "fth", (x, 12.5 if z < -10 else 14, z), (x * 1.2, 12.5 - ln if z < -10 else 14 - ln, z - 3), 4.2, hint=(0, 0, -1), bend=(0, 0, 2.5))

    # ================================================================ 頭と襟
    hd = [
        sring(-27, 48.5, 30.0, 8.6, 5.0, 5.4),
        sring(-34, 50.5, 29.0, 9.8, 5.4, 6.0),
        sring(-41, 44.5, 30.0, 7.4, 3.8, 4.8),
        sring(-46, 40.5, 31.5, 5.2, 2.6, 3.4),
        sring(-49.5, 38.5, 32.5, 3.8, 1.8, 2.4),
    ]
    F += torso("head", "fur", hd, caps=(True, False))
    F += poly("head", "dark", hd[-1], np.array([0, 36, -40.0]))
    F += fbox("head", "dark", (0, 38.6, -50.2), (4.4, 3.0, 2.4), taper=(0.8, 0.8), cut=0.3)
    for s in (1, -1):
        F += fbox("head", "furd", (s * 5.8, 48.8, -38.6), (4.4, 1.8, 7.0), R=rot_z(-s * 12) @ rot_y(s * 8), taper=(0.7, 0.8), cut=0.2)
        # 丸い耳 + 房の羽根
        F += ellipsoid("head", "fur", (s * 7.8, 50.0, -28.5), (2.6, 3.2, 2.0), segs=6, rings=1)
        F += feather("head", "fth", (s * 7.8, 52.0, -28.5), (s * 10.0, 62.0, -25.0), 3.4, hint=(s, 0, -0.3), bend=(s * 1, 0, 2))
        F += feather("head", "fth", (s * 6.8, 52.0, -28.5), (s * 6.0, 59.0, -24.0), 2.8, hint=(s, 0, -0.3), bend=(0, 0, 2))
    eyes = []
    for s in (1, -1):
        c = (s * 5.8, 44.6, -39.2)
        F += ellipsoid("head", "eye", c, (2.2, 2.2, 2.2), segs=6, rings=1)
        eyes.append(dict(c=c, r=2.3, dir=(s * 0.55, 0.05, -1), a=2.0, b=1.2, tilt=0.4))
    # 襟: 頭を中心に放射する 3 重の羽根 (後ろへ流れ、下は短い)
    cen = np.array([0.0, 42.0, -29.0])
    for ring_i, (n, r0, ln, wd, back) in enumerate(((16, 9.5, 19.0, 7.6, 7.0), (14, 8.2, 13.5, 6.4, 6.0), (11, 7.4, 9.0, 5.2, 4.0))):
        for k in range(n):
            ang = 360.0 * (k + 0.5 * (ring_i % 2)) / n
            th = math.radians(ang)
            # ang 0 = 真上、90 = +x。下 (180 付近) は短く
            sc = 1.0 - 0.45 * max(0.0, math.cos(math.radians(ang - 180))) ** 1.5
            d = np.array([math.sin(th), math.cos(th), 0.0])
            base = cen + d * r0
            tip = base + d * ln * sc + np.array([0, 0, back * sc])
            F += feather6("head", "fth", base, tip, wd * (0.8 + 0.2 * sc), hint=(0, 0, -1), bend=(0, -2.0 * d[1] * 0, 3.5))
    # 牙
    jw = [
        sring(-32, 35.0, 28.5, 5.4, 3.4, 3.0),
        sring(-40, 34.5, 28.5, 4.8, 3.0, 2.4),
        sring(-47, 34.5, 30.5, 3.2, 2.0, 1.6),
        sring(-50.5, 34.5, 31.8, 2.2, 1.4, 1.2),
    ]
    F += torso("jaw", "furd", jw, caps=(True, True))
    for s in (1, -1):
        F += spike("head", "frost", (s * 3.0, 34.2, -46.5), (0, -1, -0.15), 5.0, 1.2, sides=3, rings=1)
        F += spike("jaw", "frost", (s * 2.4, 33.8, -46.0), (0, 1, -0.2), 3.6, 1.0, sides=3, rings=1)

    # ================================================================ 鬣 (背の稜線: 後ろへ倒れた羽根の刃)
    for i, z in enumerate((-26, -20, -14, -8, -2, 4, 10, 16, 22)):
        h = 24.0 - 0.7 * abs(z + 14)
        top = (46.0 if z < -8 else 37.0) - max(0.0, (z - 6) * 0.12)
        F += feather6("mane", "fth", (0, top - 2.0, z), (0, top - 2.0 + h * 0.8, z + h * 0.75), 7.0, hint=(1, 0, 0), bend=(0, -2.5, 3))
    for i, z in enumerate((-17, -9, -1)):
        for s in (1, -1):
            F += feather("mane", "fth", (s * 5.2, (44.0 if z < -8 else 36.5), z), (s * 9.0, (44.0 if z < -8 else 36.5) + 8.0, z + 7.0), 4.0,
                         hint=(s, 0.4, 0), bend=(s * 1.0, -1.5, 2))

    # ================================================================ 脚 (前脚は太く、羽毛の袴)
    def foreleg(side):
        s = pv("leg_f" + side)[0] * 1.3
        b = "leg_f" + side
        F.extend(limb(b, "fur", [(s, 31.0, -15.0), (s, 22.0, -14.8), (s, 14.0, -14.5)], [7.0, 5.8, 4.2], [6.2, 5.0, 3.8], sides=5, hint=(1, 0, 0), phase=36))
        for k, dz in enumerate((0, 2.5, 5)):   # 肘の羽根 (後ろ下へ)
            F.extend(feather(b, "fth", (s + jit(k, 1), 24.0, -11.5 + dz), (s + jit(k, 1.5, 3), 8.5 + 1.5 * k, -3.0 + dz * 1.4), 4.2, hint=(sgn * np.sign(s) or 1, 0, 0), bend=(0, 0, 1.5)))
        lo = b + "_lo"
        F.extend(limb(lo, "furd", [(s, 14.5, -14.5), (s, 8.0, -15.8), (s, 2.5, -17.0)], [4.4, 3.6, 3.8], [4.0, 3.2, 3.4], sides=5, hint=(1, 0, 0), phase=36))
        F.extend(paw(lo, "dark", "copper", (s, 0.0, -20.0), 7.0, 10.0, 3.6, dirz=-1, cl=3.4))

    def hindleg(side):
        s = pv("leg_b" + side)[0] * 1.35
        b = "leg_b" + side
        F.extend(ellipsoid(b, "fur", (s * 1.05, 23.5, 12.5), (6.4, 8.0, 7.0), segs=6, rings=2))
        F.extend(limb(b, "fur", [(s, 24.0, 12.5), (s, 17.0, 10.5), (s, 12.0, 8.5)], [5.8, 4.8, 3.6], [5.2, 4.2, 3.2], sides=5, hint=(1, 0, 0), phase=36))
        for k in range(3):
            F.extend(feather(b, "fth", (s * 1.5, 25.0 - 2.5 * k, 16.5), (s * 1.8, 11.0 - 2.0 * k, 21.0 + 2.0 * k), 4.6, hint=(1 if s > 0 else -1, 0, 0), bend=(0, 0, 2)))
        lo = b + "_lo"
        F.extend(limb(lo, "furd", [(s, 12.0, 8.5), (s, 9.0, 13.0), (s, 7.5, 17.0)], [3.6, 3.0, 2.6], [3.2, 2.7, 2.3], sides=5, hint=(1, 0, 0), phase=36))
        F.extend(limb(lo, "furd", [(s, 7.5, 17.0), (s, 4.0, 15.5), (s, 2.4, 14.6)], [2.6, 2.4, 2.5], [2.3, 2.1, 2.2], sides=5, hint=(1, 0, 0), phase=36))
        F.extend(paw(lo, "dark", "copper", (s, 0.0, 12.0), 6.0, 9.5, 3.6, dirz=-1, cl=3.4))

    for side in ("l", "r"):
        foreleg(side)
        hindleg(side)

    # ================================================================ 尾 (太い獅子の尾 + 羽根の扇)
    F += tube("tail", "fur", [(0, 28.0, 27.0), (0.5, 28.5, 37.0), (2.0, 24.0, 46.0)], [3.6, 3.2, 2.8], [3.4, 3.0, 2.6], sides=5,
              hint=(1, 0, 0), phase=36, caps=(False, False))
    F += tube("tail_2", "furd", [(2.0, 24.0, 46.0), (0.5, 24.5, 54.0), (0, 25.0, 62.0)], [2.8, 2.6, 2.4], [2.6, 2.4, 2.2], sides=5,
              hint=(1, 0, 0), phase=36, caps=(False, False))
    for z, s in ((34, 1), (34, -1), (42, 1), (42, -1)):
        F += feather("tail", "fth", (s * 2.0, 28.0, z), (s * 8.0, 33.0, z + 12), 4.2, hint=(0, 1, 0), bend=(s * 2, -3, 3))
    for z, s in ((52, 1), (52, -1), (58, 1), (58, -1)):
        F += feather("tail_2", "fth", (s * 1.5, 25.0, z), (s * 7.0, 28.0, z + 11), 4.0, hint=(0, 1, 0), bend=(s * 2, -2, 3))
    F += fan("tail_3", (0, 25.0, 62.0), 7, 130, 22.0, 10.0, up=(0, 1, 0), axis=(0, 0.25, 1), hint=(0, 1, 0), droop=3)
    F += fan("tail_3", (0, 25.0, 62.0), 5, 80, 18.0, 8.0, up=(1, 0, 0), axis=(0, 0.25, 1), hint=(1, 0, 0))

    return F, eyes, extra


def extra(c, col, glow):
    col, glow = fur_extra(c, col, glow, vein=None, belly=("fur", FROST),
                          bolts=dict(lines=[[(-20, 44), (-14, 37), (-19, 31), (-11, 24)], [(19, 36), (12, 30), (17, 25)]],
                                     color=(200, 224, 250), w=0.7, mats=["fur", "furd"], glow=False))
    return feather_extra(c, col, glow, ["fth"], FTH, TIP, COPPER[3], tip_from=0.78, tip_glow=True)
