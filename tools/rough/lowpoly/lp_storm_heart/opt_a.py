"""A 風切りの殻: 嵐の傷 (採用済みの C) の素直な低ポリ化。丸い石の心臓を、軸 (銅) と枝 (羽枝の縞) を持つ
大きな羽根の板が抱く。先は切れ込み、傷から稲妻が漏れる。段階 2 で翼が開き、段階 3 で羽が抜けて骨と網。"""
from __future__ import annotations

from parts import *  # noqa

STONE = np.array([(40, 48, 66), (66, 78, 100), (100, 114, 138), (138, 152, 174), (182, 194, 212)])
VANE = np.array([(52, 62, 84), (84, 98, 124), (120, 136, 162), (162, 176, 200), (208, 218, 232)])
COPPER = np.array([(84, 40, 24), (134, 70, 36), (184, 108, 52), (226, 152, 82), (250, 200, 130)])
SINEW = np.array([(96, 108, 130), (150, 162, 184), (200, 212, 228), (238, 244, 252)])
BONE = np.array([(70, 74, 88), (110, 114, 128), (158, 162, 174), (204, 206, 212), (236, 236, 238)])


def arc_blade(bone, mat, s, r, p0, p1, zf, W, notch, seed, n=6, peak=0.38, thick=0.45):
    c = HC[1]
    pts = []
    for i in range(5):
        ph = p0 + (p1 - p0) * i / 4
        pts.append(V(s * r * math.sin(rad(ph)), c[1] + r * math.cos(rad(ph)), zf(ph)))
    hint = lambda p: V(p[0], p[1] - c[1], 0)
    P = curve(pts, n)
    wr, wl = [], []
    for i in range(n + 1):
        u = i / n
        prof = math.sin(math.pi * min(1.0, u / peak / 2.0)) ** 0.7 if u < peak else 1 - ((u - peak) / (1 - peak)) ** 1.4
        a = W * (0.45 + 0.55 * prof) if 0 < i < n else (W * 0.5 if i == 0 else 0.0)
        ra = la = a
        if notch and 0.5 < u < 1 and i < n:
            cut = notch * (0.45 + 0.55 * hash1(i + seed, 9))
            if hash1(i * 5 + seed, 4) < 0.5:
                ra *= 1 - cut
                la *= 1 - cut * 0.35
            else:
                la *= 1 - cut
                ra *= 1 - cut * 0.35
        wr.append(ra)
        wl.append(la)
    return feather(bone, mat, P, wr, wl, hint, thick=thick)


def wings_p1(s):
    F = []
    b = side_bone(s)
    prim = [(9.6, 24, 172, 4.6), (10.9, 20, 176, 4.2), (12.2, 16, 166, 3.8)]
    for k, (r, p0, p1, W) in enumerate(prim):
        zf = lambda ph, k=k: 0.4 + s * 0.5 * k - 5.0 * smooth(110, 176, ph) * (1 - 0.15 * k)
        F += arc_blade(b, "vane", s, r, p0, p1, zf, W, 0.55, 3 + k + (7 if s > 0 else 0), n=7)
    cov = [(13.2, 9, 66, 3.4, 1.4), (12.7, 16, 96, 3.2, -1.3), (13.4, 20, 122, 3.0, 1.0), (12.4, 30, 140, 2.8, -1.0)]
    for k, (r, p0, p1, W, z0) in enumerate(cov):
        zf = lambda ph, z0=z0: z0 - 2.5 * smooth(100, 150, ph)
        F += arc_blade(b, "vane", s, r, p0, p1, zf, W, 0.35, 21 + k + (5 if s > 0 else 0), n=5)
    # 腱: 肩から心臓の脇へ
    for j, zz in enumerate((-2.0, 2.4)):
        pts = curve([V(s * 5, 24.2, 2.0 + zz * 0.3), V(s * 7.6, 20.5, zz), V(s * 8.0, 15.5, zz * 1.1), V(s * 5.6, 10.5, zz * 0.6)], 5)
        F += rod(b, "sinew", pts, 0.42, sides=3, hint=(0, 0, 1), phase=90)
    return F


def arm_p2(s):
    ctrl = [V(5, 26.5, 1), V(9, 30, 0.8), V(14, 31.5, 0.5), V(19, 30, 0.3), V(23, 26, 0.2), V(24, 21, 0)]
    return [mx(p, s) for p in ctrl]


def wings_p2(s, style="a"):
    F = []
    b = side_bone(s)
    arm = curve(arm_p2(s), 8)
    F += rod(b, "copper", arm, 1.05, sides=5, hint=(0, 0, 1), taper=True)
    nz = V(0, 0, -1)
    # 風切り (下へ垂れる)
    for j, i in enumerate((3, 4, 5, 6, 7)):
        root = arm[i]
        L = max(5.0, root[1] - 20.0)
        dx = s * (0.35 if j < 4 else 0.15)
        F += blade(b, "vane", root, [root + V(dx * 2, -L * 0.33, 0.2), root + V(dx * 5, -L * 0.7, 0.1), root + V(dx * 6.5, -L, 0)],
                   1.9 - 0.1 * j, nz, n=5, notch=0.55, seed=j + (9 if s > 0 else 0))
    # 上へ立つ雨覆い
    for j, i in enumerate((2, 3, 5, 6)):
        root = arm[i]
        L = 4.2 + 1.3 * (j % 2) + (0.8 if j == 1 else 0)
        F += blade(b, "vane", root, [root + V(s * 0.5, L * 0.5, 0), root + V(s * 1.6, L, 0.1)], 1.35, nz, n=4, notch=0.4, seed=40 + j + (4 if s > 0 else 0))
    # 内側の次列風切り
    for j, i in enumerate((1, 2, 3)):
        root = arm[i]
        L = 5.0 + j * 1.0
        F += blade(b, "vane", root, [root + V(-s * 0.4, -L * 0.5, 0.3), root + V(-s * 0.9, -L, 0.1)], 2.0, nz, n=4, notch=0.45, seed=60 + j + (4 if s > 0 else 0))
    return F


def crest_p2(s):
    F = []
    b = crest_bone(s)
    nz = V(0, 0, -1)
    root0 = V(s * 2.0, 28.5, 3.6)
    tips = [V(s * 9.0, 44.0, 3.6), V(s * 6.2, 40.0, 3.4), V(s * 3.4, 36.0, 3.2)]
    for j, tp in enumerate(tips):
        mid1 = root0 + (tp - root0) * 0.35 + V(s * 1.6, 0, 0)
        mid2 = root0 + (tp - root0) * 0.7 + V(s * 1.0, 0, 0)
        F += blade(b, "vane", root0 + V(0, 0, 0.3 * j), [mid1, mid2, tp], 1.5 - 0.15 * j, nz, n=5, notch=0.6, seed=70 + j + (4 if s > 0 else 0), peak=0.3)
    return F


def wings_p3(s):
    F = []
    b = side_bone(s)
    pts = curve([V(s * 5, 25, 2), V(s * 9, 26.3, 1.6), V(s * 13, 25.5, 1.4), V(s * 16.5, 23, 1.2)], 5)
    F += rod(b, "bone", pts, 0.9, sides=5, hint=(0, 0, 1), taper=True)
    for j, i in enumerate((1, 2, 3, 4)):
        root = pts[i]
        F += blade(b, "vane", root, [root + V(s * 1.0, -2.2, 0.2), root + V(s * 1.6, -4.6 + 0.4 * j, 0.1)], 0.95, V(0, 0, -1), n=3, notch=0.5, seed=80 + j + (4 if s > 0 else 0))
    return F


def fingers_p3(s):
    F = []
    wr = V(s * 16, 24.5, 1.5)
    tips = [V(s * 22, 27.5, 1.2), V(s * 22.5, 22, 1.3), V(s * 21.5, 17, 1.3), V(s * 19.5, 12, 1.3)]
    for j, tp in enumerate(tips):
        mid = wr + (tp - wr) * 0.5 + V(s * 0.8, 0.4, 0)
        F += rod("finger", "bone", curve([wr, mid, tp], 4), 0.55, sides=4, hint=(0, 0, 1), taper=True)
    return F


def web_p3(joints=True):
    F = []
    for bi, (y, hw) in enumerate(((28.0, 21.5), (18.0, 20.0), (12.0, 15.5))):
        n = 14
        pts = []
        for i in range(n + 1):
            x = -hw + 2 * hw * i / n
            amp = 1.3 * (1 if i % 2 else -1) * (0.4 if i in (0, n) else 1.0)
            pts.append(V(x, y + amp, 2.7 + 2.6 * math.sin(i * 1.7 + bi) * (0.3 if i in (0, n) else 1.0)))
        F += rod("web", "arc", pts, 0.5, sides=4, hint=(0, 0, 1), phase=45)
    return F


def joints_p3(rot=0.0):
    F = []
    spots = []
    for s in (-1, 1):
        spots += [V(s * 16, 24.5, 1.5), V(s * 22, 27.5, 1.2), V(s * 22.5, 22, 1.3), V(s * 21.5, 17, 1.3), V(s * 19.5, 12, 1.3),
                  V(s * 21.5, 28.0, 2.7), V(s * 15.5, 12.0, 2.7)]
    for i, p in enumerate(spots):
        F += node("joint", "copper", p, 1.0, 17 * i)
    return F


def shed_p3(nf=3, W=1.8):
    F = []
    for i, (c, cnt) in enumerate(clump_sites(3)):
        for j in range(nf):
            a = rad(120 * j + 37 * i)
            d = V(math.cos(a), 0.5 * math.sin(a * 2), math.sin(a))
            r0 = c + d * 0.5 - d * 2.4
            F += blade("shed", "vane", r0, [r0 + d * 2.4 + V(0, 0.6, 0), r0 + d * 4.8 + V(0, -0.2, 0)], W, V(0, 1, 0.3), n=4, notch=0.5, seed=90 + i * 3 + j)
    return F


def build(phase, pres):
    F = []
    F += heart_ball("heart", "stone", phase, seam={1: 0.0, 2: 0.04, 3: 0.11}[phase])
    F += veins(phase, "sinew", n={1: 4, 2: 3, 3: 4}[phase])
    F += aortas(phase, "copper", "a", tip_spark=phase - 1)
    F += core_sphere(phase)
    # 核の電極: 前に短い銅の針
    c = CORE[phase]
    for k in range(6):
        a = rad(60 * k + 15)
        F += spike("core", "copper", c + V(math.cos(a) * 1.7, math.sin(a) * 1.7, -0.3), V(math.cos(a) * 0.7, math.sin(a) * 0.7, -0.9),
                   1.8 + 0.3 * phase, 0.5, sides=3, rings=1)
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
    stone=STONE, vane=VANE, copper=COPPER, sinew=SINEW, bone=BONE,
    stone_base=0.56, vane_base=0.52, barb_n=9.0, barb_skew=2.4, shaft_w=0.14, tip_light=0.2,
    scar_w=[0.007, 0.012, 0.018], scar_f=0.30,
    vane_scar=[0.0, 0.009, 0.015], tip_glow=[0.0, 0.10, 0.20],
    sinew_glow=[0.15, 0.35, 0.55], bone_scar=[0.0, 0.0, 0.05], groove_glow=[0.0, 0.10, 0.25],
)
OPT = dict(id="a", build=build, ctx=CTX)
