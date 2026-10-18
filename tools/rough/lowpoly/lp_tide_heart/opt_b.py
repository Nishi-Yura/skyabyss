"""B 珊瑚の殻: 殻が板に割れ、珊瑚が生えてのしかかる。暖かい色 (橙・桃・紫) が多く、シルエットが荒い。"""
from __future__ import annotations

from parts import *  # noqa
from opt_a import spiral_params

SHELL = np.array([(92, 80, 72), (138, 122, 108), (182, 166, 144), (220, 208, 184), (244, 238, 220)])
PEARL = np.array([(104, 80, 76), (150, 120, 112), (196, 168, 152), (230, 206, 186), (248, 236, 218)])
CORAL = np.array([(86, 16, 18), (136, 30, 26), (184, 52, 36), (222, 86, 52), (246, 132, 84)])
VEIN = np.array([(110, 36, 52), (160, 56, 74), (206, 90, 100), (240, 138, 134)])
FR = [np.array([(130, 54, 26), (190, 88, 34), (234, 134, 50), (252, 188, 96)]),
      np.array([(112, 26, 66), (170, 48, 102), (222, 92, 138), (248, 156, 182)]),
      np.array([(74, 40, 110), (110, 62, 154), (150, 98, 190), (196, 150, 220)]),
      np.array([(26, 84, 90), (44, 130, 132), (86, 180, 170), (156, 224, 208)])]
POLYP = np.array([(120, 40, 24), (178, 70, 30), (230, 118, 44), (252, 176, 88)])
OVER = [
    (np.array([(120, 36, 40), (176, 56, 62), (224, 92, 96), (246, 148, 140)]), 0.60, 0.20, 21),
    (np.array([(110, 70, 30), (170, 112, 40), (222, 160, 60), (248, 208, 110)]), 0.68, 0.26, 41),
    (np.array([(26, 78, 82), (44, 120, 120), (84, 170, 160), (140, 216, 200)]), 0.70, 0.30, 61),
]


def build(phase, pres):
    F = []
    th0, th1, rc, hw, zt, zc = spiral_params(phase)
    fns = (rc, hw, zt, zc)
    # ---- 殻 = 割れた板
    npl = {1: 11, 2: 7, 3: 4}[phase]
    span = th1 - th0
    step = span / npl
    gap = {1: 2.2, 2: 4.0, 3: 5.0}[phase]
    for i in range(npl):
        a0 = th0 + i * step + gap / 2
        a1 = th0 + (i + 1) * step - gap / 2
        jr = rj(i, 0.7 if phase == 1 else 1.3, 11)
        jz = rj(i, 0.5 if phase == 1 else 1.0, 12)
        sh = 1.0 + rj(i, 0.12, 13)
        st = 1.0 + rj(i, 0.15, 14)
        n = 3
        path, ra, rb = [], [], []
        for j in range(n + 1):
            th = a0 + (a1 - a0) * j / n
            e = (0.74, 1.0, 1.0, 0.74)[j]
            r = rc(th) + jr
            path.append(band_pt(th, r, zc(th) + jz))
            ra.append(zt(th) * st * e * 1.05)
            rb.append(hw(th) * sh * (0.55 + 0.45 * e) * (0.9 if phase > 1 else 1.0))
        F += tube("spiral", "plate", path, ra, rb, sides=6, hint=(0, 0, 1), phase=0, caps=(True, True))
    if phase == 1:
        # 口の縁: 巻貝の冠の棘
        a0 = th1 - 8
        for j in range(6):
            th = a0 + j * 6.5
            p, d, _ = on_shell((lambda t: rc(th1) + 1.0, lambda t: hw(th1) + 1.2, zt, zc), th0, 1e9, th, 1.0, 0.0)
            F += spike("aperture_lip", "lip", p - d * 0.4, d + V(0.0, 0, -0.5), 3.6 + rj(j, 1.0, 5), 1.15, sides=4, rings=2,
                       bend=V(0, 0, -1.4), hint=(0, 0, 1))
            q = band_pt(th + 2, rc(th1) + 0.5, zc(th1) + 0.0)
        # 口の縁の板
        F += band("aperture_lip", "lip", a0, a0 + 34, 3, lambda th: rc(th1) + 0.4 + 1.2 * smooth(a0, a0 + 34, th),
                  lambda th: hw(th1) + 1.2 * smooth(a0, a0 + 34, th) + 0.2, lambda th: zt(th1) * 0.8, lambda th: zc(th1) - 0.3,
                  sides=6, phase=0, caps=(False, True))
    # ---- 心臓
    F += blob("heart", "pearl", [20, 42, 64, 86, 108, 130, 152], segs=12, dent=0.0)
    # 隆起した血管 (太く、枝分かれ) + 小さな珊瑚のいぼ
    for k in range(5):
        ph = 20 + 72 * k + rj(k, 10, 5)
        pts = [heart_pt(th, ph + 8 * math.sin(j * 1.3 + k), lift=0.45) for j, th in enumerate((16, 40, 64, 88, 112, 134))]
        F += tube("heart_vein", "vein", pts, 0.65, 0.65, sides=3, hint=(0, 0, 1), phase=90)
        pts = [heart_pt(th, ph + 22 + 5 * j, lift=0.4) for j, th in enumerate((70, 90, 108, 122))]
        F += tube("heart_vein", "vein", pts, 0.45, 0.45, sides=3, hint=(0, 0, 1), phase=90)
        for th, off in ((52, -9), (100, 16)):
            c = heart_pt(th, ph + off, lift=0.0)
            dd = nrm(c - C)
            F += spike("heart_vein", "stud", c - dd * 0.4, dd, 2.1, 0.9, sides=4, rings=1)
    # ---- 器 (枝の多い珊瑚)
    F += vessel_coral("coral", 5, 1.25, extra=True)
    # ---- 核と環 (イソギンチャクの触手の環)
    rg = {1: 2.5, 2: 3.1, 3: 2.6}[phase]
    path = [V(CORE[0] + rg * math.cos(rad(a)), CORE[1] + rg * math.sin(rad(a)), CORE[2] - 1.0) for a in range(0, 361, 60)]
    F += tube("core_ring", "collar", path, 0.8, 0.8, sides=4, hint=(0, 0, 1), phase=45)
    for a in range(0, 360, 60 if phase != 2 else 45):
        b = V(CORE[0] + rg * math.cos(rad(a)), CORE[1] + rg * math.sin(rad(a)), CORE[2] - 1.0)
        F += spike("core_ring", "collar", b, V(math.cos(rad(a)) * 0.5, math.sin(rad(a)) * 0.5, -0.9), 2.0 + (1.0 if phase == 2 else 0), 0.7,
                   sides=4, rings=1)
    cr = {1: 1.55, 2: 2.1, 3: 1.75}[phase]
    F += ellipsoid("core_eye", "core", CORE + V(0, 0, -0.4), (cr, cr, cr), segs=6, rings=2)
    if phase == 1:
        for c, sz in barnacle_sites():
            th = math.degrees(math.atan2(c[1] - S[1], c[0] - S[0]))
            p, d, _ = on_shell(fns, th0 + 10, th1 - 14, th, 1.0, 0.3)
            for q in range(3):
                dd = nrm(d + V(rj(q, 0.5, 2) * 0.8, rj(q, 0.5, 3) * 0.8, -0.3 + rj(q, 0.3, 4)))
                F += spike("barnacle", "stud", p - d * 0.5 + V(rj(q, 0.7, 6), rj(q, 0.7, 7), 0), dd, 2.0 + q * 0.7, 0.85, sides=4, rings=1)
        for th, L in ((255, 7.0), (318, 8.5), (48, 7.0)):
            p, d, _ = on_shell(fns, th0 + 10, th1 - 14, th, 1.0, 0.3)
            t = V(-d[1], d[0], 0)
            F += branching("frond", "frond", p - d * 0.6, d + V(0, 0, -0.15), L, 1.0,
                           [(0.34, t * 0.9 + V(0, 0, 0.25), 0.62), (0.4, -t * 0.9, 0.58), (0.58, t * 0.7 + V(0, 0, -0.5), 0.45),
                            (0.62, -t * 0.7, 0.42)], sides=4)
    if phase == 2:
        F += chamber_strip(2, r_out=13.8, r_in=9.8, n=12)
        F += shard_chips(2, "shard", 1.25)
    if phase == 3:
        F += chamber_strip(3, r_out=13.2, r_in=10.0, n=10)
        F += moon()
        F += drops(3, 0.85)
        F += orbit_pearls(3, segs=5)
    return F


CTX = dict(
    shell=SHELL, lip=SHELL, pearl=PEARL, vein=VEIN, coral=CORAL, frond=FR, stud=POLYP, shard=FR[0], collar=np.array(
        [(110, 40, 48), (160, 70, 70), (210, 110, 100), (246, 160, 140)]),
    orbit=np.array([(140, 110, 100), (190, 160, 140), (230, 206, 184), (250, 244, 232)]),
    shell_base=0.55, pearl_base=0.58, overgrow=OVER,
    crack_w=[0.0, 0.018, 0.024], crack_f=0.34,
    h_crack=[0.5, 0.8, 1.1], h_crack_prob=[0.3, 0.55, 0.8],
    vein_glow=[0.0, 0.0, 0.0],
    groove_glow=[0.10, 0.35, 0.6],
    spot=dict(f=0.5, p=0.06, pal=np.array([(120, 40, 70), (176, 64, 100), (222, 110, 140)])),
    core_r=2.2,
)
OPT = dict(id="b", build=build, ctx=CTX, gcol=(150, 232, 255))
