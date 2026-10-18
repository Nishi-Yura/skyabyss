"""C 深海の殻: 暗い藍の殻に鋭い稜と棘 (アンモナイト)、花弁が閉じた心臓。縁と割れ目が青く光る。静かに不気味。"""
from __future__ import annotations

from parts import *  # noqa
from opt_a import spiral_params

SHELL = np.array([(10, 16, 36), (20, 30, 60), (34, 50, 88), (54, 78, 120), (88, 118, 156)])
PETAL = np.array([(14, 24, 46), (26, 42, 76), (44, 68, 106), (72, 104, 138), (118, 150, 178)])
PEARL = np.array([(10, 14, 30), (18, 26, 52), (28, 42, 74), (42, 62, 98)])
VEIN = np.array([(36, 20, 70), (60, 34, 108), (90, 56, 146), (128, 90, 178)])
CORAL = np.array([(40, 8, 36), (74, 14, 58), (114, 26, 80), (160, 44, 100), (206, 80, 126)])
SPINE = np.array([(40, 56, 84), (88, 112, 142), (146, 174, 196), (210, 232, 240)])
FR = [np.array([(14, 50, 76), (24, 88, 118), (44, 136, 160), (96, 196, 206)]),
      np.array([(40, 24, 90), (66, 44, 132), (100, 78, 176), (150, 130, 210)])]


def build(phase, pres):
    F = []
    th0, th1, rc, hw, zt, zc = spiral_params(phase)
    fns = (rc, hw, zt, zc)
    n = {1: 32, 2: 22, 3: 14}[phase]
    taper = (lambda th: 1.0) if phase == 1 else (lambda th: tap((th - th0) / (th1 - th0), 0.4, 0.3))
    jit = None if phase == 1 else (lambda i: (rj(i, 0.5, 3), rj(i, 0.3, 4)))
    # ---- 殻: 菱形断面の稜つき帯 (前に稜が立つ)
    F += band("spiral", "shell", th0, th1, n, rc, lambda th: hw(th) * 1.05 * taper(th), lambda th: zt(th) * 1.25 * taper(th), zc,
              sides=4, phase=0, jit=jit, caps=(phase > 1, phase > 1))
    # ---- 殻の棘 (外周に並ぶ、前へ反る)
    k0 = {1: 19, 2: 14, 3: 8}[phase]
    for k in range(k0):
        th = th0 + 8 + k * ((th1 - th0 - 16) / max(k0 - 1, 1))
        u = (th - th0) / (th1 - th0)
        L = (2.4 + 2.6 * u + rj(k, 0.9, 7)) * (1.0 if phase == 1 else 0.85)
        p, d, _ = on_shell(fns, th0, th1, th, 1.0, 0.5)
        bone = "spiral"
        F += spike(bone, "spine", p, d + V(0, 0, -0.35), L, 1.0 + 0.5 * u, sides=4, rings=2, bend=V(0, 0, -1.2), hint=(0, 0, 1))
    if phase == 1:
        # 口の縁: 大きく開き、縁に長い棘
        a0 = th1 - 8
        F += band("aperture_lip", "lip", a0, a0 + 34, 4, lambda th: rc(th1) + 0.4 + 1.8 * smooth(a0, a0 + 34, th),
                  lambda th: hw(th1) + 2.0 * smooth(a0, a0 + 34, th) + 0.2, lambda th: zt(th1) * (1.0 - 0.5 * smooth(a0, a0 + 34, th)),
                  lambda th: zc(th1) - 0.3, sides=4, phase=0, caps=(False, True))
        for j in range(4):
            th = a0 + 6 + j * 8
            r = rc(th1) + 1.8 + hw(th1) + 2.0
            p = band_pt(th, rc(th1) + 1.2 * smooth(a0, a0 + 34, th) + hw(th1) + 1.5 * smooth(a0, a0 + 34, th), zc(th1) - 0.4)
            d = V(math.cos(rad(th)), math.sin(rad(th)), -0.45)
            F += spike("aperture_lip", "spine", p, d, 4.2 + rj(j, 0.8, 5), 0.9, sides=4, rings=2, bend=V(0, 0, -1.5), hint=(0, 0, 1))
    # ---- 心臓: 閉じた花弁 (7 枚) と、下の暗い玉
    npet = 7
    openv = {1: 0.0, 2: 1.9, 3: 4.2}[phase]
    wph = {1: 21.0, 2: 19.0, 3: 15.0}[phase]
    F += blob("heart", "inner", [28, 56, 84, 112, 140], segs=8, scale=0.86 if phase == 1 else 0.82, ph0=22.5)
    ths = [22, 48, 74, 100, 124, 144]
    for k in range(npet):
        phk = 90 + 360.0 * k / npet
        # 外面と内面の格子 (中の列が盛り上がる)
        def grid_of(extra):
            rows = []
            for ti, th in enumerate(ths):
                w = wph * (1.0 - 0.55 * (ti / (len(ths) - 1)) ** 1.6)
                f = ti / (len(ths) - 1)
                row = []
                for ci, off in enumerate((-w, 0.0, w)):
                    bulge = 1.0 if ci == 1 else 0.15
                    lift = 0.9 * bulge + extra + openv * f ** 1.8 + 0.0
                    row.append(heart_pt(th, phk + off, lift=lift))
                rows.append(row)
            return rows
        O = grid_of(0.0)
        I = grid_of(-0.8)
        for i in range(len(ths) - 1):
            for j in range(2):
                F += poly("heart", "petal", [O[i][j], O[i][j + 1], O[i + 1][j + 1], O[i + 1][j]], C)
        for i in range(len(ths) - 1):
            F += poly("heart", "petal", [O[i][0], O[i + 1][0], I[i + 1][0], I[i][0]], C + (O[i][0] - C) * 2 + V(*((O[i][0] - O[i][1])[:3])) * 3)
            F += poly("heart", "petal", [O[i][2], O[i + 1][2], I[i + 1][2], I[i][2]], C + (O[i][2] - C) * 2 + V(*((O[i][2] - O[i][1])[:3])) * 3)
        F += poly("heart", "petal", [O[-1][0], O[-1][1], O[-1][2], I[-1][2], I[-1][1], I[-1][0]], C)
    # ---- 花弁の間を走る光る糸 (血管)
    for k in range(npet):
        phk = 90 + 360.0 * (k + 0.5) / npet
        pts = [heart_pt(th, phk + 3 * math.sin(j + k), lift=0.15 + openv * (th / 144) ** 1.8 * 0.3) for j, th in enumerate((20, 46, 72, 98, 122))]
        F += tube("heart_vein", "vein", pts, 0.32, 0.32, sides=3, hint=(0, 0, 1), phase=90)
    # ---- 器 (暗い赤紫の珊瑚)
    F += vessel_coral("coral", 5, 1.0, extra=(phase == 3))
    # ---- 核と環 (鋭い爪の環)
    rg = {1: 2.4, 2: 3.0, 3: 2.5}[phase]
    path = [V(CORE[0] + rg * math.cos(rad(a)), CORE[1] + rg * math.sin(rad(a)), CORE[2] - 1.0) for a in range(0, 361, 45)]
    F += tube("core_ring", "collar", path, 0.6, 0.6, sides=4, hint=(0, 0, 1), phase=45)
    for a in range(22, 360, 45):
        b = V(CORE[0] + rg * math.cos(rad(a)), CORE[1] + rg * math.sin(rad(a)), CORE[2] - 1.0)
        F += spike("core_ring", "spine", b, V(math.cos(rad(a)) * 0.9, math.sin(rad(a)) * 0.9, -0.35), 2.4, 0.55, sides=3, rings=1)
    cr = {1: 1.55, 2: 2.1, 3: 1.75}[phase]
    F += ellipsoid("core_eye", "core", CORE + V(0, 0, -0.4), (cr, cr, cr), segs=6, rings=2)
    if phase == 1:
        for c, sz in barnacle_sites():
            th = math.degrees(math.atan2(c[1] - S[1], c[0] - S[0]))
            p, d, _ = on_shell(fns, th0 + 10, th1 - 14, th, 1.0, 0.3)
            F += spike("barnacle", "spine", p - d * 0.4, d + V(0, 0, -0.5), 4.2, 0.55, sides=3, rings=2, bend=V(0, 0, -1.2), hint=(0, 0, 1))
        for si, (th, L) in enumerate(((255, 7.5), (318, 8.5), (48, 7.0))):
            p, d, _ = on_shell(fns, th0 + 10, th1 - 14, th, 1.0, 0.3)
            t = V(-d[1], d[0], 0)
            for q in range(2):
                dd = d + t * (0.4 if q else -0.4) + V(0, 0, -0.15)
                F += spike("frond", "frond", p - d * 0.4, dd, L - q * 1.5, 0.6, sides=4, rings=3, bend=t * (2.2 if q else -2.2) + V(0, 0, -1.2),
                           hint=(0, 0, 1))
    if phase == 2:
        F += chamber_strip(2)
        F += shard_chips(2, "shard", 1.15)
    if phase == 3:
        F += chamber_strip(3, r_out=13.0, r_in=10.4, n=8)
        F += moon()
        F += drops(3)
        F += orbit_pearls(3)
    return F


CTX = dict(
    shell=SHELL, lip=SHELL, petal=PETAL, pearl=PEARL, inner=np.array([(8, 12, 28), (16, 26, 52), (26, 44, 80)]),
    vein=VEIN, coral=CORAL, frond=FR, stud=SPINE, spine=SPINE, shard=SPINE, collar=SPINE,
    orbit=np.array([(60, 90, 130), (110, 150, 182), (170, 206, 224), (226, 244, 250)]),
    shell_base=0.50, pearl_base=0.5,
    crack_w=[0.0, 0.016, 0.022], crack_f=0.28,
    h_crack=[0.0, 0.0, 0.0], h_crack_prob=[0, 0, 0],
    vein_glow=[0.18, 0.75, 1.0],
    groove_glow=[0.22, 0.6, 0.9],
    inner_glow=[0.20, 0.55, 0.85],
    tip_glow=0.9,
    edge_glow=[0.08, 0.22, 0.38],
    core_r=2.2,
)
OPT = dict(id="c", build=build, ctx=CTX, gcol=(130, 225, 255))
