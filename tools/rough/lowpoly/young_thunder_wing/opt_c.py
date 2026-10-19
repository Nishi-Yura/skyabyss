"""C 案: 雷の輪の雛 — ずんぐり丸い雛。翼は短く小さく、飛ぶ力の大半は腹のまわりに浮かぶ雷の輪 (青白く光る輪と稲妻の歯、銅の節)。
背は産毛の巣の鞍 (平らな座面を、ふくらんだ産毛の縁が丸く囲む)。額に光る稲妻の巻き毛 (歌のかけら)。"""
from __future__ import annotations

from bird import *  # noqa

NAME = "C 雷の輪の雛"
HINT = 512
PAL = palette()

BODY = [(-60, 26, 40, 20), (-50, 22, 72, 32), (-30, 20, 88, 38), (0, 20, 92, 40), (28, 20, 88, 38), (46, 22, 74, 32),
        (60, 25, 48, 22), (68, 28, 20, 10)]


def half_w(z, y):
    zs = [s[0] for s in BODY]
    w = np.interp(z, zs, [s[2] for s in BODY]) / 2
    yc = np.interp(z, zs, [s[1] for s in BODY])
    return w - 0.5 * max(0.0, abs(y - yc) - 6)


def build():
    F = []
    # ================================================================ 体 (丸い産毛の玉) と白い腹
    F += body_round(BODY, "down", top=34.0, n=12)
    F += ellipsoid("body", "belly", V(0, 12, -8), (30, 7, 40), segs=10, rings=3)
    F += tufts("body", "down", [(V(s * (half_w(z, y) - 1.5), y, z), V(s, -0.3, 0.5)) for z in (-36, -14, 8, 30) for y in (12, 22)
                                for s in (1, -1)], 6.0, 4.0, sides=4)
    F += ellipsoid("body", "belly", V(0, 19, -48), (32, 15, 20), segs=10, rings=3)     # 丸い胸
    F += ellipsoid("body", "down", V(0, 22, 50), (34, 12, 18), segs=10, rings=3)       # 丸い尻
    F += tufts("body", "down2", [(V(x, 18, -66), V(x * 0.05, -0.2, -1)) for x in (-14, -5, 5, 14)], 5.0, 3.6, sides=4)

    # ================================================================ 鞍: 産毛の巣 (平らな座面 + 丸い産毛の縁)
    out = rrect(V(0, SEAT_Y, 0), V(1, 0, 0), V(0, 0, 1), 62, 116, 0.25)
    F += saddle_plate("saddle", "pad", out, thick=4.0, rim_mat="down2")
    # 縁: ふくらんだ産毛の玉をぐるりと並べる (巣の縁)。内側は鞍の範囲 (|x| <= 25.6, |z| <= 51.2) の外
    for i in range(12):
        a = 2 * math.pi * (i + 0.5) / 12
        sx, cz = math.sin(a), -math.cos(a)
        se = lambda t: math.copysign(abs(t) ** 0.5, t)
        c = V(40.0 * se(sx), SEAT_Y - 1.5 + (1.0 if i % 2 else 0.0), 67.0 * se(cz))
        o = nrm(V(c[0], 0, c[2]))
        tang = np.cross(V(0, 1, 0), o)
        R = np.column_stack([tang, V(0, 1, 0), o])
        F += ellipsoid("saddle", "down" if i % 2 else "down2", c, (12.0, 7.0, 8.0), segs=8, rings=3, rot=R)
        F += cspike("saddle", "down2" if i % 2 else "down", c + o * 5.0 + V(0, 2.0, 0), o + V(0, 0.7, 0), 5.0, 3.0, sides=4, rings=1)

    # ================================================================ 首・頭 (大きな丸い頭、額の稲妻の巻き毛)
    F += neck_tube([V(0, 22, -64), V(0, 32, -70), V(0, 42, -76)], [13, 13, 12], "down")
    hc = V(0, 52, -80)
    F += ellipsoid("head", "down", hc, (22, 20, 20), segs=10, rings=4)
    F += ellipsoid("head", "belly", hc + V(0, -8, -9), (14, 9, 10), segs=8, rings=2)
    F += cspike("head", "beak", hc + V(0, -2.0, -18.5), (0, -0.2, -1), 7.0, 4.2, sides=4, rings=1, rb=3.0)
    F += cspike("head", "beak_d", hc + V(0, -5.5, -17.5), (0, -0.2, -1), 5.0, 3.0, sides=4, rings=1, rb=1.5)
    ef, eyes = eye_pair(hc, r=5.6, dx=13.5, dy=3.5, dz=-12.0, out=0.5)
    F += ef
    F += tufts("head", "down2", [(hc + V(s * 19, -4, -1), V(s, -0.3, 0.2)) for s in (1, -1)] +
               [(hc + V(s * 8, 17, 6), V(s * 0.3, 1, 0.5)) for s in (1, -1)], 6.0, 3.4, sides=4)
    # 歌のかけら: 額から立つ光る稲妻の巻き毛 (ジグザグの 3 節) と珠
    m0 = pv("mote") + V(0, 10.0, -8.0)
    zig = [m0, m0 + V(2.5, 4.0, -1.0), m0 + V(-2.5, 7.5, -2.0), m0 + V(1.5, 11.0, -1.0)]
    F += ctube("mote", "spark", zig, [2.2, 1.8, 1.4, 0.0], sides=4, hint=(0, 0, 1))
    F += ellipsoid("mote", "mote", m0 + V(0, 0.5, 0), (3.2, 3.2, 3.2), segs=8, rings=3)

    # ================================================================ 短い翼 (全部 wing_l/wing_r に。先の骨は空)
    def wing(s):
        W = "wing_r" if s > 0 else "wing_l"
        G_ = wing_plate(W, "cov", [(26, 30, -6, 30, 7), (46, 31, -4, 30, 6), (60, 32, -2, 24, 5)], s)
        G_ += trailing(W, [34, 42, 50], lambda x: 6.0, 10.0, 9.0, ["sec", "cov"], 31.5, s, 2.0)
        roots = [(56, -8), (59, -3), (59, 3), (56, 8)]
        tips = [(80, -16), (84, -4), (82, 8), (76, 18)]
        G_ += primaries(W, roots, tips, 8.0, ["prim", "quill", "prim", "prim"], 32.5, s, 1.4, 1.0)
        G_ += tufts(W, "down2", [(V(s * x, 33.0, -20.0), V(s * 0.2, 0.4, -1)) for x in (34, 48)], 5.0, 3.0, sides=4)
        return G_
    F += mirror(wing)

    # ================================================================ 雷の輪 (腹のまわりに浮かぶ。光る輪 + 銅の節 + 稲妻の歯)
    hcn = pv("halo")
    ring_, rc = torus("halo", "halo", hcn, 50.0, 72.0, 2.4, n=24, m=4, mats=["halo", "halo", "halo", "halo_d"])
    F += ring_
    for i, c in enumerate(rc):
        o = nrm(c - V(0, c[1], 0))
        if i % 3 == 0:
            F += ellipsoid("halo", "halo_d", c, (3.6, 3.6, 3.6), segs=6, rings=2)
        else:
            d = o + V(0, 0.9 if i % 2 else -0.9, 0)
            F += cspike("halo", "spark", c + o * 1.5, d, 7.0, 1.8, sides=3, rings=2, bend=(0, -2.0 if i % 2 else 2.0, 0))

    # ================================================================ 尾 (ちょこんと短い) と太い足
    F += ellipsoid("tail", "down", V(0, 28, 62), (12, 7, 9), segs=8, rings=2)
    F += fan("tail", V(0, 29, 62), 3, 50, 14, 8.0, ["tailf", "quill", "tailf"], y_tip=2.0, bow=1.0, wtip=3.0)
    F += tucked_legs(2.0, 4.0, chunky=True)
    return F, eyes, chain(vein_paint(("cov",), rows=((-8.0, 1.0),), period=6.0, amp=2.0), seat_paint())
