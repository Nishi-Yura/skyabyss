"""A 案: 螺旋の宿 — 真珠層の白い巻貝 (青緑の螺旋の帯、息の穴の列) を背負う宿借り。
殻の肩に真珠層の花びらの台があり、大きな真珠が据わる。はさみは右が大きく珊瑚色、左は小さい。縞の脚。"""
from __future__ import annotations

from crab import *  # noqa

NAME = "A 螺旋の宿"
HINT = 256

NACRE = [(96, 92, 112), (150, 146, 166), (196, 192, 208), (226, 222, 234), (246, 242, 250)]
NACREIN = [(150, 104, 128), (204, 150, 168), (236, 196, 206), (252, 228, 232)]
TEAL = [(16, 54, 62), (28, 88, 96), (48, 126, 128), (86, 168, 160), (140, 206, 192)]
CORAL = [(70, 18, 22), (128, 36, 34), (182, 64, 48), (220, 102, 70), (242, 146, 106)]
WHITE = [(120, 112, 104), (176, 168, 156), (220, 214, 200), (244, 240, 228)]
PEARL = [(176, 196, 214), (214, 232, 244), (240, 250, 255), (255, 255, 255)]
PAL = dict(
    mats={
        "shell": dict(ramp=NACRE, base=0.60, lit=0.30, noise=0.08, crack=0.12, min=0, streak=0.06,
                      stain=dict(ramp=[(150, 104, 128), (204, 150, 168), (236, 196, 206)], thr=0.97, up=0.25, conc=0.3, low=0.0)),
        "band": dict(ramp=TEAL, base=0.55, lit=0.30, noise=0.08, crack=0.08, min=0),
        "lip": dict(ramp=NACREIN, base=0.62, lit=0.30, noise=0.06, crack=0, min=0),
        "dark": dict(ramp=[(8, 12, 18), (16, 24, 32), (28, 40, 50)], base=0.4, lit=0.1, noise=0.1, crack=0, min=0),
        "vent": dict(ramp=[(40, 150, 160), (100, 226, 230), (200, 252, 250)], base=0.5, lit=0.4, noise=0.05, crack=0, min=0),
        "pearl": dict(ramp=PEARL, base=0.62, lit=0.45, noise=0.04, crack=0, min=0),
        "crab": dict(ramp=CORAL, base=0.55, lit=0.32, noise=0.08, crack=0.12, min=1, ao=0.1),
        "crab2": dict(ramp=CORAL, base=0.36, lit=0.30, noise=0.06, crack=0.05, min=0),
        "claw": dict(ramp=CORAL, base=0.62, lit=0.30, noise=0.08, crack=0.15, min=1, streak=0.1),
        "tip": dict(ramp=WHITE, base=0.62, lit=0.30, noise=0.05, crack=0, min=0),
        "barn": dict(ramp=WHITE, base=0.55, lit=0.32, noise=0.12, crack=0.1, min=0),
        "coral": dict(ramp=[(120, 40, 70), (190, 80, 110), (236, 130, 150), (252, 184, 190)], base=0.6, lit=0.3, noise=0.08, crack=0, min=0),
        "eye": dict(ramp=[(8, 10, 16), (18, 20, 28), (32, 34, 44)], base=0.5, lit=0.2, noise=0.05, crack=0, min=0),
    },
    glow_mats=["pearl", "vent"],
)


def build():
    F = []
    # ================================================================ 殻 (螺旋)
    R = rot_x(52)
    base = V(-7.0, 16.0, 8.0)
    sh, path, ra = helix_shell(
        "shell", "shell", base, R, turns=2.7,
        r_tube=lambda t: 11.5 * (1 - t) ** 1.25 + 0.6,
        r_spiral=lambda t: 7.8 * (1 - t) ** 1.0 + 0.3,
        height=lambda t: 36.0 * (1 - (1 - t) ** 1.6),
        n=30, sides=8, start=0.0, cap_mat="dark", aim=(0, 0, -1), anchor=(0.0, 18.0, -1.0),
        mats=["shell", "shell", "band", "shell", "shell", "shell", "band", "shell"])
    F += sh
    # 殻口の唇 (真珠層の桃色の縁)
    a0, a1 = path[0], path[1]
    lipd = nrm(a0 - a1)
    F += tube("shell", "lip", [a0 - lipd * 1.0, a0 + lipd * 1.6], [ra[0] * 1.12, ra[0] * 1.02], sides=8,
              hint=R @ V(0, 1, 0), phase=0.0, caps=(False, False))
    cen = np.mean(path, 0)
    upv = V(0, 1, 0)

    def outward(i, lift=0.6):
        p = path[i]
        tang = nrm(path[min(i + 1, len(path) - 1)] - path[max(i - 1, 0)])
        o = p - cen
        o = nrm(o - tang * np.dot(o, tang))
        return nrm(o + upv * lift - tang * np.dot(upv * lift, tang)), tang
    # 息の穴の列 (鮑のように、体の巻きの背に沿って)
    for i in range(3, 13, 2):
        n_, tang = outward(i, 1.2)
        pos = path[i] + n_ * ra[i] * 0.92
        F += tube("shell", "band", [pos - n_ * 0.6, pos + n_ * 1.4], [2.0, 1.5], sides=5, hint=tang, caps=(False, False))
        F += tube("shell", "vent", [pos + n_ * 1.4, pos + n_ * 1.5], [1.1, 1.1], sides=5, hint=tang, caps=(False, True))
    # 藤壺
    for k in (6, 10, 16, 20):
        d, _ = outward(k, 0.2)
        F += sp("shell", "barn", path[k] + d * ra[k] * 0.9, d, 2.8, 1.6, sides=5, rings=1)
    F += coral("shell", "coral", path[-6], (0.2, 1, 0.3), 7.0, 1.4, [(0.5, 40, 0.6, 50), (0.7, 220, 0.5, 45)], sides=4, rings=2)

    # ================================================================ 真珠の台 (殻の肩) と真珠
    pc = pv("pearl")
    cup = pc + V(0, -5.0, 0.5)
    F += tube("shell", "lip", [cup + V(0, -9.0, 5.0), cup + V(0, -3.0, 1.0), cup], [4.0, 4.6, 6.0], sides=8, hint=(0, 0, 1),
              phase=22.5, caps=(True, False))
    for k in range(6):   # 真珠層の花びら
        a = math.radians(30 + 60 * k)
        d = V(math.sin(a), 0.0, -math.cos(a))
        F += fbox("shell", "lip" if k % 2 else "shell", cup + d * 5.2 + V(0, 2.2, 0), (4.4, 6.5, 1.2),
                  R=np.column_stack([np.cross(V(0, 1, 0), d), V(0, 1, 0), d]) @ rot_x(-28), taper=(0.45, 0.8))
    F += pearl_ball(6.0)

    # ================================================================ 宿借りの体
    hf, eyes = head_part("crab", "crab2", eye_h=10.0)
    F += hf
    # ---- 大きいはさみ (右 = +x)
    B, PB = "claw_r", "claw_r_pincer"
    s0 = pv(B)
    F += tube(B, "claw", [s0, s0 + V(4.5, 0.5, -6.0), s0 + V(7.5, 1.5, -12.0)], [3.6, 4.0, 4.4], [3.2, 3.6, 4.0], sides=6,
              hint=(0, 1, 0), phase=0)
    secs = [(V(19.0, 17.0, -24.0), 8.0, 9.0), (V(20.0, 17.0, -28.0), 12.0, 15.0), (V(20.5, 16.5, -34.0), 12.5, 15.5),
            (V(20.5, 15.5, -38.5), 9.0, 11.0)]
    F += rings_along(B, "claw", secs, (1, 0, 0), (0, 1, 0), caps=(True, True), cut=0.45)
    for (x, y, z) in ((26.5, 20, -29), (26.5, 14, -32), (25.5, 22.5, -35), (14.0, 18, -31)):   # 粒々
        F += sp(B, "tip", (x, y, z), (1 if x > 20 else -1, 0.3, 0), 2.2, 1.3, sides=4, rings=1)
    low = [V(20.5, 11.5, -38.5), V(20.5, 10.5, -43.5), V(20.5, 12.5, -48.5)]
    F += tube(B, "claw", low, [4.0, 3.0, 0.0], [3.0, 2.3, 0.0], sides=4, hint=(1, 0, 0), phase=45)
    F += tube(B, "tip", [low[1] + V(0, 0, 0.5), low[2]], [2.4, 0.0], [1.8, 0.0], sides=4, hint=(1, 0, 0), phase=45)
    pp = pv(PB)
    up = [pp, pp + V(0.0, 0.5, -6.0), pp + V(0.0, -3.0, -11.5), pp + V(0.0, -7.5, -13.5)]
    F += tube(PB, "claw", up[:3], [3.6, 3.4, 2.4], [3.0, 2.8, 2.0], sides=4, hint=(1, 0, 0), phase=45, caps=(True, False))
    F += tube(PB, "tip", [up[2], up[3]], [2.4, 0.0], [2.0, 0.0], sides=4, hint=(1, 0, 0), phase=45)
    # ---- 小さいはさみ (左)
    C = "claw_l"
    c0 = pv(C)
    F += tube(C, "claw", [c0, c0 + V(-3.5, -1.0, -5.0), c0 + V(-5.0, -2.0, -9.0)], [2.4, 2.4, 2.6], sides=5,
              hint=(0, 1, 0))
    F += rings_along(C, "claw", [(c0 + V(-5.0, -2.0, -9.5), 4.5, 5.0), (c0 + V(-5.5, -2.0, -13.0), 6.0, 7.0),
                                 (c0 + V(-5.5, -2.0, -16.0), 5.0, 5.5)], (1, 0, 0), (0, 1, 0), caps=(True, True), cut=0.4)
    for dy in (1.6, -1.6):
        F += tube(C, "tip", [c0 + V(-5.5, -2.0 + dy, -16.0), c0 + V(-5.5, -2.0 + dy * 0.6, -20.5)], [1.4, 0.0], [1.2, 0.0],
                  sides=4, hint=(1, 0, 0), phase=45)
    # ---- 脚 (赤と白の縞)
    for s, side in ((1, "r"), (-1, "l")):
        for i, foot in enumerate(((22.0, 0.0, -20.0), (26.0, 0.0, -5.0), (23.0, 0.0, 10.0)), start=1):
            F += leg(f"leg_{side}{i}", V(s * foot[0], foot[1], foot[2]), knee_up=7.0, r0=2.3, mats=("crab", "tip"))
    return F, eyes, None
