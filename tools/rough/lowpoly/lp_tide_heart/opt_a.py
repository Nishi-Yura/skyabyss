"""A 真珠の巻貝: 今の姿の延長。なめらかな八角断面の渦、放射状の溝の丸い真珠の心臓、桃・橙の珊瑚の枝。"""
from __future__ import annotations

from parts import *  # noqa

SHELL = np.array([(84, 76, 116), (128, 118, 156), (172, 162, 196), (212, 206, 228), (240, 238, 250)])
RIB = np.array([(96, 84, 118), (138, 126, 158), (182, 170, 198), (220, 210, 228), (246, 240, 248)])
PEARL = np.array([(86, 70, 104), (130, 108, 142), (176, 150, 176), (214, 192, 208), (240, 226, 236)])
VEIN = np.array([(52, 24, 72), (84, 40, 108), (120, 64, 142), (160, 100, 172)])
CORAL = np.array([(70, 14, 22), (116, 26, 32), (160, 44, 42), (198, 76, 62), (226, 116, 92)])
FR = [np.array([(120, 50, 30), (178, 84, 38), (226, 132, 58), (250, 186, 100)]),
      np.array([(120, 30, 70), (178, 56, 104), (224, 100, 140), (246, 160, 180)]),
      np.array([(130, 90, 30), (190, 144, 52), (232, 190, 84), (250, 226, 140)])]
STUD = np.array([(60, 20, 70), (104, 36, 112), (148, 64, 150), (190, 108, 186)])
SPOT = np.array([(70, 22, 80), (112, 40, 116), (150, 70, 150)])


def spiral_params(phase):
    th0, th1 = SPIRAL[phase]
    span = th1 - th0
    if phase == 1:
        rc = lambda th: 10.2 + 3.3 * ((th - th0) / span)
        hw = lambda th: 0.9 + 2.7 * ((th - th0) / span) ** 0.8
        zt = lambda th: 1.6 + 1.4 * ((th - th0) / span)
        zc = lambda th: 0.9 - 1.8 * ((th - th0) / span)
    else:
        rc = lambda th: 11.6 + 1.6 * ((th - th0) / span)
        hw = lambda th: (1.6 + 2.0 * ((th - th0) / span)) * (1.0 if phase == 2 else 0.8)
        zt = lambda th: (1.7 + 1.2 * ((th - th0) / span)) * (1.0 if phase == 2 else 0.7)
        zc = lambda th: 0.5 - 1.0 * ((th - th0) / span)
    return th0, th1, rc, hw, zt, zc


def build(phase, pres):
    F = []
    # ---- 殻の渦
    th0, th1, rc, hw, zt, zc = spiral_params(phase)
    n = {1: 30, 2: 20, 3: 12}[phase]
    if phase == 1:
        F += band("spiral", "shell", th0, th1, n, rc, hw, zt, zc, sides=8, phase=22.5, caps=(False, False))
        # 肋 (気室の仕切り): 帯の前の面を横切る細い稜
        for k in range(14):
            th = th0 + 14 + k * ((th1 - th0 - 24) / 13)
            u = (th - th0) / (th1 - th0)
            pts = []
            for t in (-0.85, 0.0, 0.85):
                r = rc(th) + t * hw(th)
                a = th + 5.0 * (1 - t)
                pts.append(band_pt(a, r, zc(th) - zt(th) * (1 - 0.18 * abs(t) ** 2) - 0.25))
            F += tube("spiral", "rib", pts, 0.55, 0.45, sides=3, hint=(0, 0, 1), phase=90)
        # 口の縁 (渦の末端が開いて広がる)
        a0 = th1 - 6
        lipn = 4
        F += band("aperture_lip", "lip", a0, a0 + 38, lipn,
                  lambda th: rc(th1) + 0.3 + 1.2 * smooth(a0, a0 + 38, th),
                  lambda th: hw(th1) + 1.5 * smooth(a0, a0 + 38, th) ** 0.8 + 0.2,
                  lambda th: zt(th1) * (1 - 0.45 * smooth(a0, a0 + 38, th)),
                  lambda th: zc(th1) - 0.2 * smooth(a0, a0 + 38, th), sides=8, phase=22.5, caps=(False, True))
    else:
        def jit(i, p=phase):
            return (rj(i, 0.55, 3), rj(i, 0.35, 4))
        # 末端は欠けて細る。厚みを u で絞る
        F += band("spiral", "shell", th0, th1, n, rc,
                  lambda th: hw(th) * tap((th - th0) / (th1 - th0)),
                  lambda th: zt(th) * tap((th - th0) / (th1 - th0), 0.5, 0.35), zc, sides=8, phase=22.5, jit=jit,
                  caps=(True, True))
        for k in range(5 if phase == 2 else 3):
            th = th0 + 18 + k * ((th1 - th0 - 36) / (4 if phase == 2 else 2))
            pts = []
            for t in (-0.8, 0.0, 0.8):
                r = rc(th) + t * hw(th) * 0.9
                pts.append(band_pt(th + 5.0 * (1 - t), r, zc(th) - zt(th) * 0.9 - 0.25))
            F += tube("spiral", "rib", pts, 0.5, 0.4, sides=3, hint=(0, 0, 1), phase=90)
    # ---- 心臓 (放射状の溝)
    dent = {1: 0.9, 2: 1.2, 3: 1.5}[phase]
    F += blob("heart", "pearl", [18, 36, 54, 72, 90, 108, 126, 144, 162], segs=14, dent=dent)
    # ---- 血管 (隆起)
    for k in range(6):
        ph = 25 + 60 * k + rj(k, 8, 5)
        pts = []
        for j, th in enumerate((18, 38, 58, 78, 98, 118, 132)):
            pts.append(heart_pt(th, ph + 10 * math.sin(j * 1.1 + k) + (j * 3 if k % 2 else -j * 3), lift=0.32))
        F += tube("heart_vein", "vein", pts, 0.40, 0.40, sides=3, hint=(0, 0, 1), phase=90)
        if k % 2 == 0:
            pts = [heart_pt(th, ph + 8 + (j * 7), lift=0.3) for j, th in enumerate((62, 80, 100, 116))]
            F += tube("heart_vein", "vein", pts, 0.32, 0.32, sides=3, hint=(0, 0, 1), phase=90)
    # ---- 器 (赤い珊瑚)
    F += vessel_coral("coral", 5, 1.0, extra=(phase == 3))
    # ---- 核と環
    rg = {1: 2.4, 2: 3.0, 3: 2.5}[phase]
    path = [V(CORE[0] + rg * math.cos(rad(a)), CORE[1] + rg * math.sin(rad(a)), CORE[2] - 1.0) for a in range(0, 361, 45)]
    F += tube("core_ring", "collar", path, 0.75, 0.75, sides=4, hint=(0, 0, 1), phase=45)
    if phase == 2:
        for a in (45, 135, 225, 315):
            b = V(CORE[0] + rg * math.cos(rad(a)), CORE[1] + rg * math.sin(rad(a)), CORE[2] - 1.0)
            F += spike("core_ring", "collar", b, V(math.cos(rad(a)) * 0.7, math.sin(rad(a)) * 0.7, -0.5), 2.2, 0.7, sides=4, rings=1)
    cr = {1: 1.55, 2: 2.1, 3: 1.75}[phase]
    F += ellipsoid("core_eye", "core", CORE + V(0, 0, -0.4), (cr, cr, cr), segs=6, rings=2)
    # ---- 段階ごと
    if phase == 1:
        fns = (rc, hw, zt, zc)
        for c, sz in barnacle_sites():
            th = math.degrees(math.atan2(c[1] - S[1], c[0] - S[0]))
            p, d, _ = on_shell(fns, th0 + 10, th1 - 14, th, 1.0, 0.0)
            F += spike("barnacle", "stud", p - d * 0.6, d + V(0, 0, -0.25), 2.2, 1.2, sides=5, rings=1, hint=(0, 0, 1))
        for th, L in ((255, 4.6), (318, 6.0), (48, 4.6)):
            p, d, _ = on_shell(fns, th0 + 10, th1 - 14, th, 1.0, 0.2)
            F += branching("frond", "frond", p - d * 0.5, d + V(0, 0, -0.1), L, 0.7,
                           [(0.45, V(-d[1] * 0.9, d[0] * 0.9, 0.2), 0.55), (0.62, V(d[1] * 0.9, -d[0] * 0.9, -0.2), 0.45)], sides=4)
    if phase == 2:
        F += chamber_strip(2)
        F += shard_chips(2)
    if phase == 3:
        F += chamber_strip(3, r_out=13.0, r_in=10.4, n=8)
        F += moon()
        F += drops(3)
        F += orbit_pearls(3)
    return F


CTX = dict(
    shell=SHELL, rib=RIB, lip=SHELL, pearl=PEARL, vein=VEIN, coral=CORAL, frond=FR, stud=STUD, shard=RIB,
    collar=RIB, orbit=np.array([(120, 110, 150), (170, 160, 196), (214, 208, 232), (244, 242, 252)]),
    shell_base=0.58, pearl_base=0.58, shell_tint=(22, -10, 16), pearl_tint=(16, -6, 12),
    crack_w=[0.0, 0.016, 0.022], crack_f=0.30,
    h_crack=[0.55, 0.75, 1.0], h_crack_prob=[0.34, 0.6, 0.85],
    vein_glow=[0.0, 0.30, 0.55],
    groove_glow=[0.18, 0.40, 0.70],
    spot=dict(f=0.5, p=0.07, pal=SPOT),
    painted_vein=dict(w=0.012, pal=np.array([(64, 40, 96), (96, 62, 128), (130, 92, 160)]), glow=[0, 0.12, 0.3]),
    core_r=2.2,
)
OPT = dict(id="a", build=build, ctx=CTX, gcol=(150, 232, 255))
