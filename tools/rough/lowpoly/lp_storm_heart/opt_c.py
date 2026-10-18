"""C 帯電の羽冠: 暗い藍の面取りの心臓から、帯電した針羽が四方へ逆立つ (先が光る)。翼は稲妻の形に折れた
幅広の板 (嵐の布)。縁は深く裂け、段階ごとに針の灯りが増える。段階 3 は稲妻の骨と太い光の網。"""
from __future__ import annotations

from parts import *  # noqa
from opt_a import arm_p2

STONE = np.array([(24, 24, 48), (44, 44, 84), (70, 68, 120), (104, 102, 160), (150, 148, 205)])
VANE = np.array([(36, 38, 70), (60, 66, 110), (92, 100, 154), (138, 148, 200), (200, 208, 238)])
COPPER = np.array([(90, 56, 20), (150, 100, 30), (206, 150, 50), (240, 196, 92), (255, 236, 160)])
SINEW = np.array([(120, 124, 170), (170, 176, 214), (214, 220, 244), (244, 246, 255)])
BONE = np.array([(44, 46, 78), (78, 82, 122), (120, 126, 168), (170, 176, 212), (222, 226, 244)])
QUILL = np.array([(40, 36, 78), (70, 66, 124), (112, 108, 176), (168, 164, 224), (226, 224, 250)])


def quills(phase, n, L0, L1, skip=38.0, seed=5):
    F = []
    c = HC[phase]
    k = 0
    gold = (0.618 * 360.0)
    for i in range(n):
        th = 180.0 * math.acos(1 - 2 * (i + 0.5) / n) / math.pi     # 0 = 核の極 (前)
        ph = (i * gold) % 360.0
        if th < skip:
            continue
        p = hsurf(phase, th, ph, lift=-0.6)
        d = nrm(p - c + V(0, 0.15, 0))
        L = L0 + (L1 - L0) * hash1(i, seed) + (1.2 if th > 120 else 0)
        F += spike("heart_vein", "quill", p, d + V(0, 0, 0), L, 0.9, sides=3, rings=2, bend=d * 0.0 + V(rj(i, 0.6, 3), rj(i, 0.6, 4), 0), hint=(0, 0, 1))
        k += 1
    return F


def zig(a, b, nseg, amp, up, seed, taper=False):
    """a→b の折れ線 (稲妻)。up は振れる向き。"""
    pts = []
    for i in range(nseg + 1):
        u = i / nseg
        p = a + (b - a) * u
        if 0 < i < nseg:
            p = p + up * amp * (1 if i % 2 else -1) * (0.7 + 0.6 * hash1(i + seed, 6))
        pts.append(p)
    return pts


def bolt_plate(bone, mat, pts, W, hint, notch=0.6, seed=0, thick=0.45, peak=0.3):
    n = len(pts) - 1
    wr, wl = [], []
    for i in range(n + 1):
        u = i / n
        prof = math.sin(math.pi * min(1.0, u / peak / 2.0)) ** 0.7 if u < peak else 1 - ((u - peak) / (1 - peak)) ** 1.3
        a = W * (0.45 + 0.55 * prof) if i < n else 0.0
        if i == 0:
            a = W * 0.5
        ra = la = a
        if notch and 0.35 < u < 1 and i < n:
            cut = notch * (0.5 + 0.5 * hash1(i + seed, 9))
            if i % 2:
                ra *= 1 - cut
            else:
                la *= 1 - cut
        wr.append(ra)
        wl.append(la)
    return feather(bone, mat, pts, wr, wl, hint, thick=thick)


def wings_p1(s):
    F = []
    b = side_bone(s)
    c = HC[1]
    hint = lambda p: V(p[0], p[1] - c[1], 0)
    for k, (r, p0, p1, W) in enumerate([(9.8, 22, 172, 4.6), (11.2, 16, 168, 4.0), (12.6, 14, 150, 3.4)]):
        pts = []
        n = 7
        for i in range(n + 1):
            ph = p0 + (p1 - p0) * i / n
            rr = r + (1.5 if i % 2 else -0.8) * (1 if 0 < i < n else 0) * (0.6 + 0.4 * k)
            z = 0.3 + s * 0.4 * k - 5.0 * smooth(110, 176, ph) * (1 - 0.2 * k) + (1.0 if i % 2 else -1.0) * 0.5 * (1 if 0 < i < n else 0)
            pts.append(V(s * rr * math.sin(rad(ph)), c[1] + rr * math.cos(rad(ph)), z))
        F += bolt_plate(b, "vane", pts, W, hint, notch=0.65, seed=3 + k + (8 if s > 0 else 0), peak=0.28)
    for k, (r, p0, p1) in enumerate([(13.6, 10, 100), (12.6, 40, 150)]):
        pts = []
        n = 5
        for i in range(n + 1):
            ph = p0 + (p1 - p0) * i / n
            rr = r + (0.9 if i % 2 else -0.5)
            pts.append(V(s * rr * math.sin(rad(ph)), c[1] + rr * math.cos(rad(ph)), (1.6 if k == 0 else -1.4) - 2.0 * smooth(100, 150, ph)))
        F += bolt_plate(b, "vane", pts, 2.8, hint, notch=0.5, seed=30 + k + (8 if s > 0 else 0), peak=0.3)
    return F


def wings_p2(s):
    F = []
    b = side_bone(s)
    sh = V(s * 5, 26.5, 1.0)
    nz = V(0, 0, -1)
    tips = [V(s * 22.5, 34.0, 0.2), V(s * 24.0, 27.0, 0.0), V(s * 23.5, 20.5, 0.0), V(s * 17.0, 19.5, 0.0), V(s * 11.0, 21.0, 0.2)]
    mids = [V(s * 12.0, 31.0, 0.6), V(s * 14.0, 28.5, 0.4), V(s * 13.0, 25.0, 0.4), V(s * 10.5, 24.0, 0.6), V(s * 8.0, 24.0, 0.8)]
    Ws = [2.2, 2.9, 3.0, 2.8, 2.2]
    for j, (tp, md, W) in enumerate(zip(tips, mids, Ws)):
        pts = zig(sh, tp, 7, 1.7 if j != 1 else 1.3, nrm(np.cross(tp - sh, nz)), 100 + j + (6 if s > 0 else 0))
        pts[1] = pts[1] * 0.5 + md * 0.5 if False else pts[1]
        F += bolt_plate(b, "vane", pts, W, nz, notch=0.65, seed=j + (9 if s > 0 else 0), peak=0.3)
    return F


def crest_p2(s):
    F = []
    b = crest_bone(s)
    r0 = V(s * 2.0, 28.5, 3.6)
    for j, tp in enumerate((V(s * 9.0, 44.0, 3.6), V(s * 4.8, 39.0, 3.3))):
        pts = zig(r0, tp, 6, 1.5, V(1, 0, 0), 120 + j + (6 if s > 0 else 0))
        F += bolt_plate(b, "vane", pts, 1.5, V(0, 0, -1), notch=0.5, seed=60 + j + (9 if s > 0 else 0), peak=0.3)
    return F


def wings_p3(s):
    F = []
    b = side_bone(s)
    pts = zig(V(s * 5, 25, 2), V(s * 16.5, 23, 1.2), 5, 1.1, V(0, 1, 0), 130 + (6 if s > 0 else 0))
    F += rod(b, "bone", pts, 0.8, sides=4, hint=(0, 0, 1), taper=True)
    for j, i in enumerate((1, 2, 3, 4)):
        root = pts[i]
        F += blade(b, "vane", root, [root + V(s * 1.0, -2.0, 0.2), root + V(s * 1.8, -4.0 - 0.4 * j, 0.1)], 1.1, V(0, 0, -1), n=3, notch=0.6, seed=140 + j + (9 if s > 0 else 0))
    return F


def fingers_p3(s):
    F = []
    wr = V(s * 16, 24.5, 1.5)
    tips = [V(s * 22, 27.5, 1.2), V(s * 22.5, 22, 1.3), V(s * 21.5, 17, 1.3), V(s * 19.5, 12, 1.3)]
    for j, tp in enumerate(tips):
        pts = zig(wr, tp, 4, 0.9, V(0, 0, 1) if j % 2 else V(1, 0, 0), 150 + j)
        F += rod("finger", "bone", pts, 0.5, sides=3, hint=(0, 0, 1), taper=True)
    return F


def web_p3():
    F = []
    for bi, (y, hw) in enumerate(((28.0, 21.5), (18.0, 20.0), (12.0, 15.5))):
        n = 12
        pts = []
        for i in range(n + 1):
            x = -hw + 2 * hw * i / n
            amp = 2.0 * (1 if i % 2 else -1) * (0.3 if i in (0, n) else 1.0)
            pts.append(V(x, y + amp, 2.7 + 2.2 * math.sin(i * 1.9 + bi)))
        F += rod("web", "arc", pts, 0.8, sides=4, hint=(0, 0, 1), phase=45)
    # 帯と帯をつなぐ稲妻
    for s in (-1, 1):
        pts = zig(V(s * 18.0, 28.0, 2.7), V(s * 16.0, 12.0, 2.7), 6, 1.6, V(1, 0, 0), 160 + (6 if s > 0 else 0))
        F += rod("web", "arc", pts, 0.5, sides=3, hint=(0, 0, 1), phase=90)
    return F


def joints_p3():
    F = []
    for s in (-1, 1):
        for p in (V(s * 16, 24.5, 1.5), V(s * 22, 27.5, 1.2), V(s * 22.5, 22, 1.3), V(s * 21.5, 17, 1.3), V(s * 19.5, 12, 1.3),
                  V(s * 21.5, 28.0, 2.7), V(s * 15.5, 12.0, 2.7)):
            F += node("joint", "spark", p, 1.05, 11)
    return F


def shed_p3():
    F = []
    for i, (c, cnt) in enumerate(clump_sites(3)):
        for j in range(2):
            a = rad(180 * j + 55 * i)
            d = V(math.cos(a), 0.4, math.sin(a))
            r0 = c - d * 2.2
            pts = zig(r0, r0 + d * 5.0, 3, 0.8, V(0, 1, 0), 170 + i * 3 + j)
            F += bolt_plate("shed", "vane", pts, 1.8, V(0, 1, 0.3), notch=0.55, seed=180 + i * 3 + j, peak=0.35)
    return F


def build(phase, pres):
    F = []
    F += heart_ball("heart", "stone", phase, segs=10, rings=4, seam={1: 0.0, 2: 0.04, 3: 0.10}[phase], jit=0.11, notch=1.4)
    F += quills(phase, {1: 34, 2: 42, 3: 50}[phase], {1: 2.6, 2: 3.4, 3: 4.2}[phase], {1: 4.4, 2: 5.6, 3: 7.0}[phase])
    F += aortas(phase, "copper", "c", tip_spark=phase - 1)
    F += core_sphere(phase, segs=6, rings=2)
    c = CORE[phase]
    for k in range(5):
        a = rad(72 * k + 20)
        F += spike("core", "spark", c + V(math.cos(a) * 1.9, math.sin(a) * 1.9, -0.2), V(math.cos(a) * 1.0, math.sin(a) * 1.0, -0.3),
                   1.6 + 0.5 * phase, 0.4, sides=3, rings=1)
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
    stone=STONE, vane=VANE, copper=COPPER, sinew=SINEW, bone=BONE, quill=QUILL,
    stone_base=0.52, vane_base=0.5, barb_n=5.0, barb_skew=3.5, shaft_w=0.0, tip_light=0.25,
    scar_w=[0.008, 0.016, 0.026], scar_f=0.5,
    vane_scar=[0.0, 0.012, 0.022], tip_glow=[0.0, 0.12, 0.25],
    quill_tip=[0.0, 0.25, 0.55],
    sinew_glow=[0.0, 0.0, 0.0], bone_scar=[0.0, 0.0, 0.06], groove_glow=[0.0, 0.12, 0.3],
)
OPT = dict(id="c", build=build, ctx=CTX)
