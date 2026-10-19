"""B 案: 畝の塔 — 細く高い塔のような巻貝 (青緑の殻に白い畝) を立てて背負う、脚の長い宿借り。
真珠は殻の正面の畝の籠 (桃色の真珠層の窓) に埋まる。体は深い青、関節と目柄は珊瑚色。縦に高い輪郭。"""
from __future__ import annotations

from crab import *  # noqa

NAME = "B 畝の塔"
HINT = 256

TEAL = [(14, 44, 54), (22, 72, 82), (36, 106, 112), (66, 146, 144), (118, 188, 176)]
RIB = [(132, 140, 146), (186, 192, 194), (226, 230, 228), (248, 250, 246)]
NACREIN = [(150, 104, 128), (204, 150, 168), (236, 196, 206), (252, 228, 232)]
BLUE = [(18, 20, 52), (34, 40, 92), (56, 70, 136), (88, 108, 176), (136, 158, 210)]
CORAL = [(90, 26, 26), (156, 52, 40), (210, 94, 62), (240, 140, 96)]
WHITE = [(120, 120, 124), (176, 178, 182), (220, 222, 224), (246, 246, 246)]
PEARL = [(176, 196, 214), (214, 232, 244), (240, 250, 255), (255, 255, 255)]
PAL = dict(
    mats={
        "shell": dict(ramp=TEAL, base=0.56, lit=0.32, noise=0.09, crack=0.14, min=0, streak=0.08,
                      stain=dict(ramp=[(40, 70, 52), (66, 104, 66), (100, 140, 84)], thr=0.96, up=0.3, conc=0.3, low=0.1)),
        "rib": dict(ramp=RIB, base=0.60, lit=0.30, noise=0.06, crack=0.08, min=0),
        "lip": dict(ramp=NACREIN, base=0.62, lit=0.30, noise=0.06, crack=0, min=0),
        "dark": dict(ramp=[(8, 12, 18), (16, 24, 32), (28, 40, 50)], base=0.4, lit=0.1, noise=0.1, crack=0, min=0),
        "pearl": dict(ramp=PEARL, base=0.62, lit=0.45, noise=0.04, crack=0, min=0),
        "crab": dict(ramp=BLUE, base=0.55, lit=0.32, noise=0.08, crack=0.1, min=1, ao=0.1),
        "crab2": dict(ramp=CORAL, base=0.5, lit=0.30, noise=0.06, crack=0.05, min=0),
        "claw": dict(ramp=BLUE, base=0.6, lit=0.30, noise=0.08, crack=0.15, min=1, streak=0.1),
        "tip": dict(ramp=WHITE, base=0.62, lit=0.30, noise=0.05, crack=0, min=0),
        "joint": dict(ramp=CORAL, base=0.55, lit=0.30, noise=0.06, crack=0, min=0),
        "barn": dict(ramp=WHITE, base=0.55, lit=0.32, noise=0.12, crack=0.1, min=0),
        "eye": dict(ramp=[(8, 10, 16), (18, 20, 28), (32, 34, 44)], base=0.5, lit=0.2, noise=0.05, crack=0, min=0),
    },
    glow_mats=["pearl"],
)


def build():
    F = []
    # ================================================================ 殻 (高い塔の巻貝)
    R = rot_x(16)
    sh, path, ra = helix_shell(
        "shell", "shell", V(0, 0, 0), R, turns=4.6,
        r_tube=lambda t: 9.6 * (1 - t) ** 0.9 + 0.5,
        r_spiral=lambda t: 5.2 * (1 - t) ** 0.9 + 0.3,
        height=lambda t: 40.0 * (1 - (1 - t) ** 1.25),
        n=38, sides=7, start=0.0, cap_mat="dark", aim=(0, -0.2, -1), anchor=(0.0, 17.0, -1.0),
        rib_every=3, rib_mat="rib", rib_k=1.1)
    F += sh
    a0, a1 = path[0], path[1]
    lipd = nrm(a0 - a1)
    F += tube("shell", "lip", [a0 - lipd * 1.0, a0 + lipd * 1.8], [ra[0] * 1.15, ra[0] * 1.04], sides=7,
              hint=(0, 1, 0), phase=0.0, caps=(False, False))
    # 殻口の下の棘 (前へ張り出す庇)
    F += sp("shell", "rib", a0 + V(0, -ra[0] * 0.9, -1.0), (0, -0.4, -1), 6.0, 2.4, sides=4, rings=1)
    cen = np.mean(path, 0)
    # 塔の棘 (畝ごとに外へ)
    for k in range(5, 33, 5):
        p = path[k]
        tang = nrm(path[k + 1] - path[k - 1])
        o = p - (cen + (R @ V(0, 1, 0)) * np.dot(p - cen, R @ V(0, 1, 0)))
        o = nrm(o - tang * np.dot(o, tang))
        F += sp("shell", "rib", p + o * ra[k] * 0.9, nrm(o + V(0, 0.5, 0)), 4.0 + 0.6 * (33 - k) / 5, 1.6, sides=4, rings=1)
    # ================================================================ 真珠の籠 (正面の窓)
    pc = pv("pearl")
    # 真珠のうしろの殻の点へ桃色の窓の座をつなぐ
    j = min(range(len(path)), key=lambda i: np.linalg.norm(path[i] - (pc + V(0, 0, 8.0))))
    seat = path[j]
    F += tube("shell", "lip", [seat, pc + V(0, 0, 3.5)], [6.5, 7.8], sides=8, hint=(0, 1, 0), phase=22.5, caps=(False, True))
    for k in range(5):   # 畝の籠 (真珠を抱く白い肋)
        a = math.radians(-90 + 45 * k)
        d = V(math.cos(a), math.sin(a), 0)
        b0 = pc + d * 7.6 + V(0, 0, 3.0)
        F += tube("shell", "rib", bez(b0, pc + d * 8.6 + V(0, 0, -3.5), pc + d * 3.2 + V(0, 0, -7.5), 3), [1.6, 1.4, 1.1, 0.0],
                  sides=4, hint=(0, 0, 1), phase=45)
    F += pearl_ball(6.2)
    # ================================================================ 宿借りの体
    hf, eyes = head_part("crab", "crab2", eye_h=13.0, stalk_mat="crab2", wide=0.9, antenna=18.0, ant_mat="joint")
    F += hf
    # ---- 大きいはさみ (細長い。先が鋸歯)
    B, PB = "claw_r", "claw_r_pincer"
    s0 = pv(B)
    elbow = s0 + V(9.0, 9.0, -9.0)
    F += tube(B, "claw", [s0, elbow], [2.8, 2.6], sides=5, hint=(0, 1, 0))
    F += ellipsoid(B, "joint", elbow, (3.0, 3.0, 3.0), segs=5, rings=1)
    F += tube(B, "claw", [elbow, V(21.0, 21.0, -26.0)], [2.6, 3.4], sides=5, hint=(0, 1, 0))
    F += rings_along(B, "claw", [(V(21.0, 21.0, -26.0), 6.5, 8.0), (V(21.0, 21.0, -31.0), 8.5, 11.0), (V(21.0, 20.0, -36.5), 7.5, 9.5)],
                     (1, 0, 0), (0, 1, 0), caps=(True, True), cut=0.45)
    low = [V(21.0, 17.0, -37.0), V(21.0, 15.5, -45.0), V(21.0, 16.5, -53.0)]
    F += tube(B, "claw", low, [2.4, 2.0, 0.0], [2.0, 1.6, 0.0], sides=4, hint=(1, 0, 0), phase=45)
    for z in (-41, -45, -49):
        F += sp(B, "tip", (21.0, 17.6 + (z + 41) * -0.05, z), (0, 1, -0.2), 2.4, 1.0, sides=3)
    pp = pv(PB)
    F += tube(PB, "claw", [pp, pp + V(0, 1.0, -9.0), pp + V(0, -2.0, -16.0)], [2.4, 2.0, 1.4], [2.0, 1.7, 1.2], sides=4,
              hint=(1, 0, 0), phase=45, caps=(True, False))
    F += tube(PB, "tip", [pp + V(0, -2.0, -16.0), pp + V(0, -5.0, -18.5)], [1.4, 0.0], [1.2, 0.0], sides=4, hint=(1, 0, 0), phase=45)
    # ---- 小さいはさみ
    C = "claw_l"
    c0 = pv(C)
    el = c0 + V(-6.0, 5.0, -6.0)
    F += tube(C, "claw", [c0, el], [2.0, 1.9], sides=5, hint=(0, 1, 0))
    F += ellipsoid(C, "joint", el, (2.2, 2.2, 2.2), segs=5, rings=1)
    F += tube(C, "claw", [el, el + V(-1.0, 0.5, -8.0)], [1.9, 2.4], sides=5, hint=(0, 1, 0))
    F += rings_along(C, "claw", [(el + V(-1.0, 0.5, -8.0), 4.0, 4.8), (el + V(-1.0, 0.5, -12.0), 4.5, 5.6)],
                     (1, 0, 0), (0, 1, 0), caps=(True, True), cut=0.4)
    for dy in (1.4, -1.4):
        F += tube(C, "tip", [el + V(-1.0, 0.5 + dy, -12.0), el + V(-1.0, 0.5 + dy * 0.4, -18.0)], [1.2, 0.0], [1.0, 0.0],
                  sides=4, hint=(1, 0, 0), phase=45)
    # ---- 脚 (長く高い膝。蜘蛛蟹のよう)
    for s, side in ((1, "r"), (-1, "l")):
        for i, foot in enumerate(((24.0, 0.0, -22.0), (28.0, 0.0, -6.0), (25.0, 0.0, 12.0)), start=1):
            F += leg(f"leg_{side}{i}", V(s * foot[0], foot[1], foot[2]), knee_up=14.0, r0=1.9, mats=("crab", "crab"),
                     sides=4, joint_mat="joint")
    return F, eyes, None
