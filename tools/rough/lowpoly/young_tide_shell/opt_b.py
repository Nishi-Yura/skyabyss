"""B 案: 巻き殻の稚貝 — 生まれたての鸚鵡貝。後ろに大きな巻き殻 (虎縞) が立ち、その殻口が前を向いて 6 人の背もたれになる。
殻口から前へ平らに開いた体の部屋の床が甲板 (真珠層の縁)。前は頭巾と触手の冠、横に大きな目。頭巾の先に歌のかけらが灯る。"""
from __future__ import annotations

from ys import *  # noqa
from pal import *  # noqa

NAME = "B 巻き殻の稚貝"
HINT = 512

CREAM = [(120, 104, 86), (178, 160, 134), (216, 202, 176), (238, 230, 210), (250, 246, 236)]
HOOD = [(70, 40, 40), (118, 70, 62), (160, 104, 88), (196, 146, 122)]
FRILL = [(26, 78, 86), (44, 116, 120), (80, 160, 156), (136, 204, 192)]
PAL = dict(
    mats={
        **COMMON,
        "nshell": m(CREAM, 0.58, 0.3, 0.07, 0.04, 1),
        "coil": m(CREAM, 0.6, 0.3, 0.07, 0.03, 1),
        "inside": m([(20, 30, 40), (40, 56, 70), (70, 90, 104)], 0.45, 0.2, 0.06),
        "belly": m(BELLY, 0.6, 0.3, 0.08, ao=0.2),
        "deck": m(NACRE, 0.56, 0.25, 0.07, 0.0, 1, stripe=12, axis="v"),
        "lip": m(NACRE_IN, 0.62, 0.3, 0.06),
        "hood": m(HOOD, 0.58, 0.3, 0.08, 0.06, 1, stain=dict(ramp=[(220, 200, 180), (240, 228, 214)], thr=0.93, up=0.2, conc=0.0, low=0.0)),
        "tent": m(CORAL_P, 0.55, 0.3, 0.08, 0.0, 1, stripe=4, axis="v"),
        "frill": m(FRILL, 0.55, 0.3, 0.06, 0.0, 1, stripe=5, axis="u"),
    },
    glow_mats=["song"],
)

COIL_C = V(0, 55.0, 69.5)
R0, GROW, TURNS = 26.0, 3.0, 2.3


def coil_path(n=46):
    path, ra, rb = [], [], []
    for i in range(n + 1):
        th = 2 * math.pi * TURNS * i / n
        R = R0 * GROW ** (-th / (2 * math.pi))
        path.append(COIL_C + R * V(0, -math.cos(th), math.sin(th)))
        rb.append(0.5 * R)
        ra.append(0.85 * R)
    ra[-1] = rb[-1] = 0.0
    return path, ra, rb


def stripes(c, col, glow):
    P, mats, owned, bay = c["P"], c["mats"], c["owned"], c["bay"]
    st = np.array(STRIPE, float)
    # 体: 波打つ縦縞 (水より上)
    on = (mats == "nshell") & owned & (P[..., 1] > 7) & (((P[..., 2] + 5 * np.sin(P[..., 1] * 0.3)) / 15.0) % 1.0 < 0.3)
    k = np.clip((1 + 0.8 * bay + (P[..., 1] > 15)).astype(int), 0, 2)
    col = np.where(on[..., None], st[k], col)
    # 巻き殻: 中心から放射する虎縞 (殻口の近くは無地)
    dy, dz = P[..., 1] - COIL_C[1], P[..., 2] - COIL_C[2]
    ang = np.arctan2(dz, -dy)
    r = np.hypot(dy, dz)
    band = ((ang * 7 / np.pi + r * 0.05) % 1.0) < 0.38
    near_ap = (P[..., 2] < COIL_C[2] + 6) & (P[..., 1] < COIL_C[1] - 8)
    on = (mats == "coil") & owned & band & ~near_ap & (np.abs(P[..., 0]) < 0.8 * r + 2)
    col = np.where(on[..., None], st[np.clip((1 + bay * 0.8).astype(int), 0, 2)], col)
    return col, glow


def build():
    F = []
    # ================================================================ 体の部屋 (舟形の殻。水に浮く)
    F += sloft("body", "belly", [(0.0, 24.0, 48.0), (5.0, 34.0, 58.0), (12.0, 40.0, 64.0), (18.0, 42.0, 66.0), (21.0, 41.0, 65.0)],
               n=20, p=2.3, mats=["belly", "nshell", "nshell", "nshell"], cz=6.0)
    # ================================================================ 巻き殻 (後ろに立つ。殻口は前を向いた背もたれ)
    path, ra, rb = coil_path()
    F += tube("body", "coil", path, ra, rb, sides=10, hint=(1, 0, 0), phase=0.0, caps=(True, False))
    # 殻口の唇 (真珠層の桃色) と奥の暗がり
    t0 = nrm(path[1] - path[0])
    F += tube("body", "lip", [path[0] - t0 * 1.5, path[0] + t0 * 2.0], [ra[0] * 1.1, ra[0] * 1.08], [rb[0] * 1.14, rb[0] * 1.12],
              sides=10, hint=(1, 0, 0), phase=0.0, caps=(True, True))
    F += tube("body", "inside", [path[0] - t0 * 1.8, path[0] - t0 * 1.2], [ra[0] * 0.92] * 2, [rb[0] * 0.9] * 2,
              sides=10, hint=(1, 0, 0), phase=0.0, caps=(True, True))
    # 巻きの節の畝 (背の外側)
    for i in (6, 12, 18, 24):
        ti = nrm(path[i + 1] - path[i - 1])
        F += tube("body", "nshell", [path[i] - ti * 1.0, path[i] + ti * 1.0], [ra[i] * 1.06] * 2, [rb[i] * 1.08] * 2,
                  sides=10, hint=(1, 0, 0), phase=0.0, caps=(True, True))
    F += coral("body", "coral", path[14] + V(8, 3, 0), (0.5, 1, 0.3), 9.0, 1.6, [(0.5, 40, 0.6, 50)])
    F += cone("body", "barn", path[9] + V(-10, 0, 2), (-0.8, 0.4, 0.3), 3.0, 2.2, sides=5)
    F += cone("body", "barn", V(40, 15, 30), (1, 0.4, 0), 3.0, 2.2, sides=5)
    F += cone("body", "barn", V(-41, 14, -20), (-1, 0.4, 0), 3.0, 2.2, sides=5)

    # ================================================================ 鞍 (開いた体の部屋の床 = 甲板。縁は真珠層の唇)
    F += sloft("saddle", "deck", [(SEAT - 4.0, 35.5, 61.0), (SEAT, 35.5, 61.0)], n=24, p=3.5)
    rim = sring(37.5, 63.5, SEAT + 1.4, n=24, p=3.5)
    F += rod("saddle", "lip", rim + [rim[0]], 2.4, sides=6, hint=(0, 1, 0))
    # 前の頭巾へつながる低い舳先の殻
    F += fbox("saddle", "lip", V(0, SEAT + 1.8, -64.0), (30.0, 4.0, 5.0), R=rot_x(14), taper=(0.6, 0.7), cut=0.4)

    # ================================================================ 頭: 頭巾・目・触手の冠
    F += ellipsoid("head", "hood", V(0, 22.0, -72.0), (24.0, 7.0, 14.0), segs=10, rings=3, rot=rot_x(-10))
    F += ellipsoid("head", "hood", V(0, 14.0, -70.0), (20.0, 9.0, 11.0), segs=10, rings=3)
    eyes = []
    for s in (1, -1):
        c = V(s * 21.0, 16.0, -66.0)
        F += ellipsoid("head", "eye", c, (5.5, 5.5, 5.5), segs=8, rings=3)
        eyes.append(dict(c=tuple(c), r=5.5, dir=(s * 1.0, 0.1, -0.3), a=3.4, b=3.4, tilt=0.0, bone="head"))
    for k in range(11):
        a = math.radians(-75 + 15 * k)
        base = V(15 * math.sin(a), 11.0, -72.0 - 3.0 * math.cos(a))
        d = nrm(V(math.sin(a) * 0.7, -0.45 + 0.15 * math.cos(2 * a), -1.0))
        ln = 10.0 + 4.0 * math.cos(a)
        F += cone("head", "tent", base, d, ln, 3.0, sides=4, rings=2, bend=(0, -2.5, 0))
    # 歌のかけら: 頭巾の先の真珠層の杯に灯る
    F += fbox("head", "lip", V(0, 27.5, -82.0), (7.0, 3.0, 7.0), taper=(1.35, 1.35), cut=0.4)
    F += pearl("song", "song", V(0, 31.5, -82.0), 3.8, segs=8, rings=3)

    # ================================================================ ひれ (横の薄い襞)・尾 (舵の襞)
    for s, sd in ((1, "r"), (-1, "l")):
        F += paddle(f"fin_f{sd}", "frill", V(s * 36, 12.0, -36.0), V(s * 70, 9.0, -26.0), [28, 28, 20, 6], [3.0, 2.6, 2.0, 1.0],
                    lead=(0, 0, -1), sweep=8.0)
        F += paddle(f"fin_b{sd}", "frill", V(s * 36, 10.0, 42.0), V(s * 56, 8.0, 56.0), [18, 17, 11, 4], [2.6, 2.2, 1.8, 1.0],
                    lead=(s * 0.3, 0, -1))
    F += paddle("tail", "frill", V(0, 6.0, 70.0), V(0, 2.0, 88.0), [14, 13, 9, 3], [3.0, 2.4, 1.8, 1.0], up=(1, 0, 0),
                lead=(0, 1, 0))
    return F, eyes, stripes
