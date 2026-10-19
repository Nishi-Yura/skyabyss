"""A 案: 産毛の雛 — 灰青の産毛にふくらんだ丸い雛。生えかけの羽に、金に光る雷の羽軸 (稲妻の羽根) が数本だけ混じる。
背には白い産毛の座布団の鞍、その縁を立てた羽根の輪 (羽根の柵) がぐるりと囲む。頭の上に銅の小さな冠と、光る歌のかけら。"""
from __future__ import annotations

from bird import *  # noqa

NAME = "A 産毛の雛"
HINT = 512
PAL = palette()

BODY = [(-68, 26, 24, 16), (-58, 22, 50, 26), (-38, 19, 66, 30), (-10, 18, 70, 32), (20, 18, 68, 32), (46, 20, 58, 28),
        (62, 23, 40, 20), (74, 27, 16, 8)]


def half_w(z, y):
    """体の半幅 (横腹の房の位置用。おおよそ)。"""
    zs = [s[0] for s in BODY]
    w = np.interp(z, zs, [s[2] for s in BODY]) / 2
    yc = np.interp(z, zs, [s[1] for s in BODY])
    return w - 0.4 * max(0.0, abs(y - yc) - 6)


def build():
    F = []
    # ================================================================ 体 (ふくらんだ産毛) と房
    F += body_loft(BODY, "down", cut=0.5)
    F += ellipsoid("body", "belly", V(0, 13, -32), (26, 10, 28), segs=8, rings=3)          # 白い胸
    F += flank_feathers(half_w, (-34, -16, 2, 20, 38), (24,), ("cov", "back"), 13.0, 10.0, 3.0)
    F += tufts("body", "down", [(V(s * (half_w(z, 12) - 1.0), 12, z), V(s, -0.4, 0.4)) for z in (-30, -6, 18, 42) for s in (1, -1)],
               3.6, 3.8, sides=4)
    F += tufts("body", "down", [(V(x, 5, z), V(x * 0.03, -1, 0.2)) for x in (-12, 0, 12) for z in (-6, 20, 44)], 3.4, 3.6, sides=4)
    F += tufts("body", "belly", [(V(x, 18, -62), V(x * 0.04, -0.3, -1)) for x in (-12, -4, 4, 12)], 5.0, 3.4, sides=4)

    # ================================================================ 鞍: 産毛の座布団 + 羽根の柵
    out = rrect(V(0, SEAT_Y, 0), V(1, 0, 0), V(0, 0, 1), 62, 116, 0.25)
    F += saddle_plate("saddle", "pad", out, thick=4.0, rim_mat="down2")
    for s in (1, -1):                 # 左右の産毛の枕 (鞍の板を産毛に埋める)
        F += ctube("saddle", "down", [V(s * 34.0, 32.5, -54), V(s * 35.5, 33.0, -30), V(s * 35.5, 33.0, 30), V(s * 34.0, 32.5, 54)],
                   [3.0, 5.0, 5.0, 3.0], sides=6, hint=(0, 1, 0), phase=0)
    k = 0
    for z in range(-48, 49, 12):     # 左右の柵
        for s in (1, -1):
            m = "quill" if (k % 4 == 1) else ("cov" if k % 2 else "back")
            F += feather("saddle", m, V(s * 33.0, 35.5, z), V(s * 39.0, 48.0, z + 2.0), 5.0, t=1.3, up=(s * -1, 0.2, 0),
                         bow=0.0, wtip=1.4)
            k += 1
    for x in (-21, -7, 7, 21):        # 前と後ろの柵
        for s in (-1, 1):
            m = "quill" if (x == 7 and s < 0) or (x == -7 and s > 0) else "cov"
            F += feather("saddle", m, V(x, 34.5, s * 57.0), V(x * 1.08, 45.0, s * 63.0), 5.0, t=1.3, up=(0, 0.2, -s),
                         bow=0.0, wtip=1.4)

    # ================================================================ 首・頭 (丸い雛の頭)
    F += neck_tube([V(0, 24, -62), V(0, 34, -70), V(0, 42, -76)], [13, 12, 11], "down")
    hc = V(0, 49, -78)
    F += ellipsoid("head", "down", hc, (17, 16, 17), segs=10, rings=4)
    F += ellipsoid("head", "belly", hc + V(0, -6, -8), (10, 7, 8), segs=8, rings=2)          # 白い喉
    F += cspike("head", "beak", hc + V(0, -1.5, -15), (0, -0.25, -1), 10.0, 4.5, sides=4, rings=2, rb=3.2, bend=(0, -1.5, 0))
    F += cspike("head", "beak_d", hc + V(0, -5, -14), (0, -0.15, -1), 7.0, 3.4, sides=4, rings=1, rb=1.8)
    ef, eyes = eye_pair(hc, r=4.6, dx=11.2, dy=3.0, dz=-10.5)
    F += ef
    F += tufts("head", "down2", [(hc + V(s * 13, -3, -2), V(s, -0.2, 0.3)) for s in (1, -1)] +
               [(hc + V(s * 6, 12, 4), V(s * 0.4, 1, 0.6)) for s in (1, -1)], 6.0, 2.8)
    for i, a in enumerate((-22, 0, 22)):   # 銅の冠の芽 (成体の避雷針の冠の小さい版)
        F += cspike("head", "copper", hc + V(a * 0.25, 13.5, 5 + abs(a) * 0.1), rot_y(a) @ V(0, 0.8, 0.6), 8.0 - abs(a) * 0.08,
                    1.8, sides=4, rings=2, bend=(0, -1, 2))
    # 歌のかけら: 頭の上に光る珠 (冠の芽の前)
    F += ellipsoid("mote", "mote", pv("mote") + V(0, 6.0, -3.0), (4.0, 4.0, 4.0), segs=8, rings=3)

    # ================================================================ 翼 (生えかけ。金の雷の羽軸が混じる)
    def wing(s):
        W, T = ("wing_r", "wing_r_tip") if s > 0 else ("wing_l", "wing_l_tip")
        G_ = wing_plate(W, "cov", [(22, 30, -10, 30, 6), (40, 31, -10, 34, 5.5), (62, 32, -8, 34, 5), (86, 33, -6, 30, 4.5)], s)
        G_ += wing_plate(T, "cov", [(80, 33, -6, 28, 4.5), (98, 33.5, -4, 24, 4), (112, 34, -2, 16, 3.5)], s)
        G_ += trailing(W, list(range(30, 84, 9)), lambda x: -10 + 0.05 * (x - 30) + 12, 14.0, 9.0, ["sec", "cov"], 31.5, s)
        G_ += tufts(W, "down2", [(V(s * x, 33.5, -24 + 0.05 * x), V(s * 0.2, 0.3, -1)) for x in (34, 50, 66)], 5.0, 2.4)
        roots = [(100, -10), (104, -6), (106, -2), (106, 3), (102, 7), (96, 10)]
        tips = [(130, -22), (136, -10), (138, 2), (134, 14), (124, 24), (110, 30)]
        G_ += primaries(T, roots, tips, 8.0, ["prim", "quill", "prim", "prim", "quill", "prim"], 33.5, s)
        return G_
    F += mirror(wing)

    # ================================================================ 尾 (短い扇 + 産毛) と脚
    F += ellipsoid("tail", "down", V(0, 29, 66), (11, 6, 9), segs=8, rings=2)
    F += fan("tail", V(0, 29, 64), 5, 70, 22, 7.5, ["tailf", "prim", "quill", "prim", "tailf"], y_tip=-1.5, bow=1.0)
    F += tucked_legs(6.0, 3.0)
    return F, eyes, chain(vein_paint(("cov",), rows=((-14.0, 1.0),)), seat_paint())
