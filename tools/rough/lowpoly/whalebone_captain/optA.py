"""A 案: 石鎧の将 — 青灰の石の板金を着た騎士。重ね板の肩・縦に長い背びれの冠・胸の肋の弧。"""
from __future__ import annotations

from ck import *

NAME = "石鎧の将"
PAL = dict(
    STONE=ramp((28, 34, 48), (54, 63, 80), (86, 98, 118), (122, 136, 156), (162, 176, 194)),
    BONE=ramp((52, 44, 42), (100, 88, 72), (150, 135, 108), (190, 175, 142), (222, 210, 178), (242, 235, 210)),
    MOSS=ramp((22, 40, 30), (40, 66, 40), (68, 100, 52), (108, 140, 74)),
    TRIM=ramp((30, 22, 22), (58, 42, 36), (88, 66, 52), (122, 94, 70)),
    WOOD=ramp((52, 42, 30), (94, 78, 54), (128, 108, 74), (160, 138, 96), (190, 166, 118)),
    stone_base=0.56, rim_p=0.8, rivet_p=0.7, moss_thr=0.93, crack=0.2, seed=1,
)


def build():
    F = []

    # ================================================================ 胴 (胸の中央に竜骨の稜)
    T = [ring(17.0, 8.8, -6.6, 5.0, 0.0), ring(20.0, 9.8, -7.8, 5.6, 0.0), ring(24.0, 11.2, -9.4, 6.2, 0.0),
         ring(28.0, 11.6, -9.8, 6.4, 0.0), ring(31.4, 10.8, -8.2, 5.8, 0.0), ring(34.0, 6.0, -4.6, 4.0, 0.0)]
    F += rloft("body", "stone", T, caps=(False, "stone"))
    # 胸の肋の弧 (竜骨から左右へ降りる。板にぴったり沿う)
    for s in (1, -1):
        for y in (29.6, 26.2, 22.8):
            pts = []
            for a in (14, 52, 92):
                p = torso_pt(T, y - 2.2 * a / 92.0, a if s > 0 else 360 - a)
                pts.append(p + V(0, 0, -0.15 if a < 60 else 0) + V(s * 0.15 * (a > 60), 0, 0))
            F += tube("body", "bone", pts, ra=[0.95, 0.95, 0.75], rb=[0.55, 0.6, 0.5], sides=3, hint=(0, 1, 0),
                      axis_fn=lambda p: np.array([0, p[1], -0.64]), hide_cos=0.5, ridge_out=True)
    F += tube("body", "bone", [(0, 31.6, -9.0), (0, 18.4, -8.4)], ra=[1.1, 0.8], rb=[1.0, 0.7], sides=4, hint=(1, 0, 0),
              phase=0, axis_fn=lambda p: np.array([0, p[1], -2.0]), hide_cos=0.6)
    F += ellipsoid("body", "gem", (0, 26.6, -11.0), (1.9, 2.8, 1.5), segs=4, rings=1)
    for dy in (1, -1):
        F += spike("body", "bone", (0, 26.6 + dy * 2.4, -10.6), (0, dy, -0.2), 3.4, 1.0, sides=3, rings=1)
    # 腰帯と留め金、下げ板
    F += rloft("body", "trim", [ring(16.6, 9.5, -7.2, 5.6, 0.0), ring(18.6, 9.2, -7.0, 5.4, 0.0)], caps=(False, False))
    F += fbox("body", "bone", (0, 17.6, -7.6), (4.6, 3.2, 1.6), taper=(0.8, 0.8))
    F += spike("body", "tooth", (0, 16.0, -8.2), (0, -1, -0.3), 3.2, 1.1, sides=4, rings=1)
    for x, w, a in ((0.0, 6.2, 0), (5.2, 4.6, -14), (-5.2, 4.6, 14)):
        F += fbox("body", "stone", (x, 14.6, -7.4 - 0.4 * (abs(x) < 1)), (w, 5.0, 1.2), R=rot_z(a) @ rot_x(-14), taper=(0.6, 1.0))
    for s in (1, -1):
        F += fbox("body", "stone", (s * 9.6, 14.8, -0.5), (1.6, 5.6, 8.0), R=rot_z(-s * 12), taper=(1.0, 0.75))
    F += rloft("body", "stone", [ring(32.8, 8.4, -6.4, 5.8, 0.0), ring(35.0, 6.0, -5.0, 4.6, 0.0)], caps=(False, False))

    # 背: 背びれの列 (spine0..7)
    lens = [3.4, 4.6, 6.0, 7.6, 8.6, 7.8, 6.0, 4.4]
    for i in range(8):
        y = 18.0 + 2.0 * i
        L = lens[i]
        pts = [(5.0, y - 1.2), (5.6 + L, y + 1.6 + 0.12 * L), (5.2, y + 1.9)]
        F += fin(f"spine{i}", "bone", pts, 0.0, 1.6, tip_thin=0.5)
        F += fbox(f"spine{i}", "bone", (0, y + 0.3, 5.7), (3.0, 2.2, 2.4), taper=(0.8, 0.8))

    # ================================================================ 頭 (兜: 覗き窓の庇、背びれの冠、牙の角)
    def hring(y, rx, zf, zb, dz=0.0):
        r = ring(y, rx, zf, zb, 0.0)
        for k in (0, 1, 7):
            r[k] = r[k] + V(0, 0, dz)
        return r
    H = [hring(34.6, 5.6, -4.8, 4.8), hring(36.6, 8.0, -8.4, 6.4), hring(38.6, 8.4, -7.6, 6.8, 0.7),
         hring(40.8, 8.4, -7.6, 6.9, 0.7), hring(41.8, 9.0, -9.2, 7.2), hring(44.0, 7.8, -6.8, 6.6),
         hring(46.2, 3.4, -2.0, 3.6)]
    F += rloft("head", "stone", H, caps=(False, "stone"), mats=["stone", "stone", "visor", "stone", "stone", "stone"])
    crest = [(-4.0, 44.4), (-3.0, 47.4), (0.0, 50.2), (3.6, 52.0), (9.0, 52.8), (6.4, 49.2), (6.2, 44.4)]
    F += fin("head", "bone", crest, 0.0, 2.0, tip_thin=0.4)
    for s in (1, -1):
        F += spike("head", "bone", (s * 7.6, 41.4, -0.2), (s * 0.85, 0.5, 0.2), 8.4, 1.8, sides=4, rings=3,
                   bend=(s * 0.8, 2.6, 3.0))
        F += spike("head", "tooth", (s * 6.2, 37.2, -6.8), (s * 0.05, -0.6, -0.8), 3.6, 1.1, sides=3, rings=1)
    F += spike("head", "tooth", (0, 36.4, -6.6), (0, -0.7, -0.7), 3.0, 1.3, sides=4, rings=1)
    F += fbox("head", "bone", (0, 42.6, -8.6), (7.8, 1.2, 2.4), R=rot_x(-22), taper=(0.6, 1.0))

    # ================================================================ 腕
    for s, nm in ((1, "L"), (-1, "R")):
        a, fa, ha = f"arm{nm}", f"forearm{nm}", f"hand{nm}"
        x = s * 14.5
        F += fbox(a, "stone", (x + s * 3.0, 27.2, 0.2), (13.0, 1.8, 11.8), R=rot_z(-s * 20), taper=(0.78, 0.82), cut=0.35)
        F += fbox(a, "stone", (x + s * 4.2, 25.0, 0.2), (11.4, 1.7, 10.6), R=rot_z(-s * 30), taper=(0.85, 0.85))
        F += fbox(a, "stone", (x + s * 5.0, 23.0, 0.2), (9.0, 1.6, 9.2), R=rot_z(-s * 40), taper=(0.85, 0.85))
        F += ellipsoid(a, "stone", (x - s * 0.2, 27.2, 0.0), (4.6, 2.8, 4.8), segs=6, rings=1)
        F += spike(a, "bone", (x + s * 4.0, 28.8, -0.6), (s * 0.55, 1.0, -0.15), 6.0 if s > 0 else 8.4, 1.6, sides=4,
                   rings=2, bend=(s * 2.2, 1.5, 0.0))
        F += lim(a, "bone", [(x, 22.6, 0.0), (x, 19.8, 0.1), (x, 17.8, 0.2)], ra=[2.4, 1.9, 2.2], rb=[2.4, 1.9, 2.2], sides=5,
                 phase=20, caps=(False, False))
        F += ellipsoid(fa, "stone", (x, 17.5, 0.8), (3.2, 2.2, 3.0), segs=5, rings=1)
        F += spike(fa, "bone", (x + s * 2.6, 17.6, 1.2), (s * 1.0, 0.1, 0.6), 4.4, 1.4, sides=4, rings=2, bend=(0, 0.6, 1.2))
        F += fbox(fa, "stone", (x, 13.6, -0.1), (6.6, 6.6, 6.0), taper=(0.78, 0.8), cut=0.35)
        F += fbox(fa, "bone", (x, 13.4, -3.4), (2.2, 5.0, 1.0), R=rot_x(-8), taper=(0.7, 1.0))
        F += fbox(ha, "stone", (x, 8.2, -0.5), (6.0, 4.4, 5.8), taper=(0.8, 0.8), cut=0.45)
        F += fbox(ha, "stone", (x, 6.0, -0.5), (4.8, 1.2, 5.0), taper=(0.9, 0.8))
        for k in range(3):
            F += spike(ha, "tooth", (x + s * 2.6, 9.4, -1.8 + 1.8 * k), (s * 1.0, 0.45, 0), 2.0, 0.8, sides=3, rings=1)
        F += fbox(ha, "stone", (x - s * 2.4, 8.6, -2.6), (1.6, 2.4, 2.4), R=rot_y(s * 20), taper=(0.8, 0.8))

    # ================================================================ 脚
    for s, nm in ((1, "L"), (-1, "R")):
        leg = f"leg{nm}"
        x = s * 4.5
        F += fbox(leg, "stone", (x, 9.0, 0.0), (8.2, 6.2, 7.0), taper=(0.84, 0.88), cut=0.3)
        F += ellipsoid(leg, "stone", (x, 6.0, -3.0), (2.9, 2.0, 1.8), segs=5, rings=1, rot=rot_x(-12))
        F += spike(leg, "bone", (x, 6.2, -4.4), (0, -0.1, -1.0), 3.6, 1.3, sides=4, rings=2)
        F += fbox(leg, "stone", (x, 3.5, 0.0), (6.4, 5.4, 6.2), taper=(1.1, 1.05), cut=0.3)
        F += fbox(leg, "stone", (x, 3.6, -3.1), (4.4, 4.4, 1.4), R=rot_x(-8), taper=(0.7, 1.0))
        F += rings_along(leg, "stone", [(V(x, 1.4, 3.2), 6.2, 2.8), (V(x, 1.5, -1.0), 7.4, 3.0), (V(x, 0.7, -8.4), 3.0, 1.2)],
                         (1, 0, 0), (0, 1, 0), caps=(True, True))

    # ================================================================ 鉾 (受け口 socket_main の原点)
    PK = []
    PK += lim("pike", "wood", [(0, 0, 12.6), (0, 0, -12.4)], ra=[0.95, 1.0], rb=[0.95, 1.0], sides=6, phase=30)
    for z in (-10.4, -5.0, 2.0):
        PK += lim("pike", "trim", [(0, 0, z), (0, 0, z - 1.3)], ra=[1.45, 1.45], rb=[1.45, 1.45], sides=6, phase=30)
    PK += spike("pike", "bone", (0, 0, 12.4), (0, 0, 1), 3.8, 1.3, sides=4, rings=1)
    PK += fbox("pike", "bone", (0, 0, -12.8), (3.0, 3.0, 1.2), taper=(0.8, 0.8))
    leaf = [(-13.0, 0.4), (-14.4, 3.2), (-19.6, 3.0), (-27.3, 0.2), (-21.6, -1.4), (-15.0, -1.8)]
    PK += fin("pike", "bone", leaf, 0.0, 2.4, tip_thin=0.7)
    fluke = [(-12.8, -1.2), (-12.2, -6.0), (-13.8, -10.6), (-16.2, -7.6), (-19.0, -5.2), (-21.8, -3.0), (-16.6, -1.2)]
    PK += fin("pike", "bone", fluke, 0.0, 2.2, tip_thin=0.6)
    PK += ellipsoid("pike", "gem", (0, -0.2, -12.0), (1.5, 1.6, 1.3), segs=4, rings=1)
    return F, PK, dict(eyes=[dict(x=3.6, y=39.8, a=2.8, b=0.6, tilt=0.38)], extra_paint=extra_paint)


def extra_paint(c, col, glow):
    P, N = c["P"], c["N"]
    # 兜の下半分の呼吸孔: 縦の細い溝
    m = (c["bones"] == "head") & (c["mats"] == "stone") & (N[..., 2] < -0.6) & (P[..., 1] > 35.5) & (P[..., 1] < 38.0) \
        & (np.floor(P[..., 0] * 2.0).astype(int) % 2 == 0) & (np.abs(P[..., 0]) < 5.2) & c["owned"]
    col = np.where(m[..., None], DARK[1], col)
    return col, glow
