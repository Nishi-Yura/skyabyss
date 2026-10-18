"""B 避雷針の骨籠: 銅 (緑青の吹いた避雷針) の言葉。心臓は銅の輪の籠に入り、翼は銅の軸に櫛の歯のような羽枝が並ぶ骨。
静電気の玉が先に灯り、稲妻は銅の棒を伝う。暗い鋼の色に、緑青と電光。"""
from __future__ import annotations

from parts import *  # noqa
from opt_a import arm_p2

STONE = np.array([(30, 36, 44), (52, 60, 72), (80, 90, 104), (112, 124, 138), (152, 164, 178)])
VANE = np.array([(40, 52, 62), (66, 84, 98), (98, 120, 134), (136, 160, 172), (184, 204, 212)])
COPPER = np.array([(70, 32, 20), (122, 62, 32), (176, 100, 48), (222, 146, 76), (248, 196, 124)])
VERD = np.array([(24, 70, 66), (40, 110, 102), (70, 156, 140), (130, 206, 186)])
SINEW = np.array([(110, 104, 92), (168, 160, 140), (214, 206, 184), (244, 238, 220)])
BONE = np.array([(52, 58, 66), (88, 96, 104), (130, 138, 144), (176, 182, 184), (220, 222, 216)])


def hoops(phase, n=3, gap=0.0, r_add=1.0):
    c = HC[phase]
    rx, ry, rz = [a + r_add for a in HR[phase]]
    F = []
    for k in range(n):
        al = rad(180.0 / n * k + 10)
        pts = []
        steps = 14
        sweep = 360.0 - gap
        for i in range(steps + 1):
            th = rad(sweep * i / steps + (90 + gap / 2 if gap else 0))
            lx, ly = math.cos(th) * 1.0, math.sin(th)
            x = rx * lx * math.cos(al)
            z = rx * lx * math.sin(al) * (rz / rx) * 1.0
            pts.append(c + V(x, ry * ly, z))
        if not gap:
            pts[-1] = pts[0]
        F += tube("heart_vein", "copper", pts, 0.5, 0.5, sides=3, hint=(0, 0, 1), phase=90)
        if gap:
            F += node("heart_vein", "spark", pts[0], 0.8, 10 * k)
            F += node("heart_vein", "spark", pts[-1], 0.8, 10 * k + 30)
    return F


def comb(bone, s, r, p0, p1, zf, nb, L0, L1, W=0.85, shaft_r=0.75, n=2, seed=0, sweep=0.85, out=0.6, phase=1):
    c = HC[1]
    pts = []
    for i in range(6):
        ph = p0 + (p1 - p0) * i / 5
        pts.append(V(s * r * math.sin(rad(ph)), c[1] + r * math.cos(rad(ph)), zf(ph)))
    P = curve(pts, 8)
    F = rod(bone, "copper", P, shaft_r, sides=4, hint=(0, 0, 1), taper=True)
    for j in range(nb):
        u = 0.06 + 0.9 * j / max(nb - 1, 1)
        idx = min(int(u * 8), 7)
        a, b = P[idx], P[idx + 1]
        root = a + (b - a) * (u * 8 - idx)
        tan = nrm(P[min(idx + 2, 8)] - P[max(idx - 1, 0)])
        rad_ = nrm(V(root[0], root[1] - c[1], 0))
        d = nrm(rad_ * out + tan * sweep)
        L = L0 + (L1 - L0) * math.sin(math.pi * min(1, u * 1.05)) ** 0.6 + rj(j, 0.5, seed)
        mid = root + d * L * 0.5 + V(0, 0, rj(j, 0.5, seed + 1) * 0.5)
        tp = root + d * L + tan * 0.6
        F += blade(bone, "vane", root, [mid, tp], W, V(0, 0, -1), n=n, notch=0.0, seed=seed + j, thick=0.35, peak=0.3)
    return F


def wings_p1(s):
    F = []
    b = side_bone(s)
    zf1 = lambda ph: 0.2 - 4.0 * smooth(110, 176, ph)
    zf2 = lambda ph: 1.8 - 3.0 * smooth(120, 178, ph)
    F += comb(b, s, 11.0, 20, 168, zf1, 11, 3.6, 6.4, seed=2 + (6 if s > 0 else 0))
    F += comb(b, s, 8.9, 34, 176, zf2, 6, 2.4, 4.2, W=0.7, shaft_r=0.55, seed=20 + (6 if s > 0 else 0), out=0.2, sweep=0.95)
    return F


def wings_p2(s):
    F = []
    b = side_bone(s)
    arm = curve(arm_p2(s), 8)
    F += rod(b, "copper", arm, 1.15, sides=5, hint=(0, 0, 1), taper=True)
    nz = V(0, 0, -1)
    for j in range(10):
        i = 1 + int(j * 0.75)
        u = 0.1 + 0.85 * j / 9
        k = min(int(u * 8), 7)
        root = arm[k] + (arm[k + 1] - arm[k]) * (u * 8 - k)
        L = max(4.0, min(root[1] - 20.0, 11.0))
        dx = s * (0.5 - 0.35 * u)
        F += blade(b, "vane", root, [root + V(dx * 1.2, -L * 0.5, 0.1), root + V(dx * 2.6 + s * 0.6, -L, 0)], 0.85, nz, n=2, seed=j + (9 if s > 0 else 0), thick=0.35, peak=0.3)
    for j in range(6):
        u = 0.1 + 0.6 * j / 5
        k = min(int(u * 8), 7)
        root = arm[k] + (arm[k + 1] - arm[k]) * (u * 8 - k)
        L = 3.6 + 1.2 * (j % 2)
        F += blade(b, "vane", root, [root + V(s * 0.6, L * 0.5, 0), root + V(s * 1.5, L, 0)], 0.75, nz, n=2, seed=30 + j + (9 if s > 0 else 0), thick=0.3, peak=0.3)
    return F


def crest_p2(s):
    F = []
    b = crest_bone(s)
    pts = curve([V(s * 2.0, 28.5, 3.6), V(s * 4.0, 33.5, 3.6), V(s * 6.5, 38.5, 3.6), V(s * 8.8, 44.0, 3.6)], 6)
    F += rod(b, "copper", pts, 0.8, sides=4, hint=(0, 0, 1), taper=True)
    for j in range(5):
        k = 1 + j
        root = pts[k]
        L = 3.8 - 0.35 * j
        F += blade(b, "vane", root, [root + V(-s * 1.4, L * 0.5, 0), root + V(-s * 2.8, L * 0.95, 0)], 0.7, V(0, 0, -1), n=2, seed=50 + j + (9 if s > 0 else 0), thick=0.3, peak=0.3)
    F += node(b, "spark", pts[-1] + V(0, 0.4, 0), 0.8)
    return F


def wings_p3(s):
    F = []
    b = side_bone(s)
    pts = curve([V(s * 5, 25, 2), V(s * 9, 26.3, 1.6), V(s * 13, 25.5, 1.4), V(s * 16.5, 23, 1.2)], 5)
    F += rod(b, "bone", pts, 0.9, sides=5, hint=(0, 0, 1), taper=True)
    for j, i in enumerate((1, 2, 3, 4)):
        root = pts[i]
        F += blade(b, "vane", root, [root + V(s * 0.8, -1.6, 0.1), root + V(s * 1.2, -3.2 - 0.3 * j, 0)], 0.6, V(0, 0, -1), n=2, seed=80 + j + (9 if s > 0 else 0), thick=0.3, peak=0.3)
    return F


def fingers_p3(s):
    F = []
    wr = V(s * 16, 24.5, 1.5)
    tips = [V(s * 22, 27.5, 1.2), V(s * 22.5, 22, 1.3), V(s * 21.5, 17, 1.3), V(s * 19.5, 12, 1.3)]
    for j, tp in enumerate(tips):
        mid = wr + (tp - wr) * 0.5 + V(s * 0.6, 0.3, 0)
        F += rod("finger", "copper", curve([wr, mid, tp], 4), 0.5, sides=4, hint=(0, 0, 1), taper=True)
    return F


def web_p3():
    F = []
    for bi, (y, hw) in enumerate(((28.0, 21.5), (18.0, 20.0), (12.0, 15.5))):
        F += rod("web", "copper", [V(-hw, y, 2.7), V(-hw / 3, y, 2.7), V(hw / 3, y, 2.7), V(hw, y, 2.7)], 0.45, sides=4, hint=(0, 0, 1), phase=45)
        n = 10
        pts = []
        for i in range(n + 1):
            x = -hw + 2 * hw * i / n
            amp = 1.6 * (1 if i % 2 else -1) * (0.3 if i in (0, n) else 1.0)
            pts.append(V(x, y + amp, 2.7 + 1.8 * math.sin(i * 2.1 + bi)))
        F += rod("web", "arc", pts, 0.38, sides=3, hint=(0, 0, 1), phase=90)
    return F


def joints_p3():
    F = []
    for s in (-1, 1):
        for p in (V(s * 16, 24.5, 1.5), V(s * 22, 27.5, 1.2), V(s * 22.5, 22, 1.3), V(s * 21.5, 17, 1.3), V(s * 19.5, 12, 1.3),
                  V(s * 21.5, 28.0, 2.7), V(s * 15.5, 12.0, 2.7)):
            F += node("joint", "spark", p, 0.9, 11)
    return F


def shed_p3():
    F = []
    for i, (c, cnt) in enumerate(clump_sites(3)):
        for j in range(5):
            a = rad(72 * j + 31 * i)
            d = nrm(V(math.cos(a), 0.6 * math.sin(a * 3), math.sin(a)))
            r0 = c + d * 0.6
            F += blade("shed", "vane", r0, [r0 + d * 2.2, r0 + d * 4.4], 0.8, V(0, 1, 0.3), n=2, seed=90 + i * 5 + j, thick=0.3, peak=0.3)
    return F


def build(phase, pres):
    F = []
    F += heart_ball("heart", "stone", phase, seam={1: 0.0, 2: 0.03, 3: 0.08}[phase], jit=0.03)
    F += hoops(phase, n={1: 3, 2: 2, 3: 3}[phase], gap={1: 0.0, 2: 0.0, 3: 70.0}[phase], r_add=0.9)
    F += aortas(phase, "copper", "b", tip_spark=1)
    F += core_sphere(phase)
    c = CORE[phase]
    for k in range(4):
        a = rad(90 * k + 45)
        F += spike("core", "copper", c + V(math.cos(a) * 1.8, math.sin(a) * 1.8, -0.2), V(math.cos(a) * 0.9, math.sin(a) * 0.9, -0.5),
                   2.3 + 0.3 * phase, 0.45, sides=3, rings=1)
    for s in (-1, 1):
        if phase == 1:
            F += wings_p1(s)
        elif phase == 2:
            F += wings_p2(s)
            F += crest_p2(s)
        else:
            F += wings_p3(s)
            F += fingers_p3(s)
    if phase == 3:
        F += web_p3()
        F += joints_p3()
        F += shed_p3()
    return F


CTX = dict(
    stone=STONE, vane=VANE, copper=COPPER, sinew=SINEW, bone=BONE, verd=(0.55,), verd_pal=VERD,
    stone_base=0.52, vane_base=0.5, barb_n=6.0, barb_skew=1.0, shaft_w=0.06, tip_light=0.22,
    scar_w=[0.0, 0.012, 0.022], scar_f=0.5,
    vane_scar=[0.0, 0.0, 0.0], tip_glow=[0.05, 0.14, 0.3],
    sinew_glow=[0.0, 0.0, 0.0], bone_scar=[0.0, 0.0, 0.05], groove_glow=[0.0, 0.06, 0.14],
)
OPT = dict(id="b", build=build, ctx=CTX)
