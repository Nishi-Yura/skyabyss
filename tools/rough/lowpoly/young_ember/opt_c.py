"""C 案: 燠結晶の岩亀 — 丸い岩の塊のような亀の仔。甲羅は黒い殻の板と玄武岩の帯で、上は平らに削れた台地 (石の板の鞍)。
台地のふちを燠の結晶がぐるりと囲む (親の背の火口のふちの小さな写し)。柱のような脚でのしのし歩き、
首を前へ伸ばす。甲羅の前のふちに光る核 (歌のかけら) が埋まる。"""
from __future__ import annotations

from ye import *  # noqa

NAME = "C 燠結晶の岩亀"
TITLE = "燠結晶の岩亀"
HINT = 512
PAL = base_pal()


def leg(bone):
    p = pv(bone)
    s = 1 if p[0] > 0 else -1
    front = p[2] < 0
    F = []
    foot = V(s * 37, 0, p[2] + (-4 if front else 4))
    F += rod(bone, "skin", [p + V(s * 4, 0, 0), V(s * 36, 12, foot[2]), foot + V(0, 4, 0)], [9.0, 9.0, 9.5], sides=7,
             hint=(0, 0, 1))
    F += rod(bone, "rock", [foot + V(0, 4.5, 0), foot + V(0, 0.0, 0)], [10.5, 10.5], sides=7, hint=(0, 0, 1))   # 足 (柱)
    F += claws(bone, foot + V(0, 0.2, -7.5 if front else -7.0), spread=5.0, length=4.0, r=2.0)
    for k in range(3):   # 腕の鱗の板
        F += fbox(bone, "plate", V(s * (31 + k * 1.5), 15 - k * 3.5, foot[2] - 8.5), (7, 3.5, 2.5), R=rot_x(-10),
                  taper=(0.7, 0.7), cut=0.3)
    return F


def crystal(bone, base, d, length, r):
    """燠の結晶: 根元を塞いだ四角い柱 + とがり (閉じる)。"""
    d = nrm(np.asarray(d, float))
    path = [base, base + d * length * 0.55, base + d * length * 0.62, base + d * length]
    return tube(bone, "crystal", path, [r, r * 0.95, r * 0.85, 0.0], sides=4, hint=(0, 1, 0), phase=45, caps=(True, False))


def build():
    F = []
    # ================================================================ 甲羅 (丸い岩の塊。上は平らな台地)
    rings = [hring(9, 22, 35, 16, squ=0.2), hring(9, 42, 68, 16, jit=0.05, seed=1, squ=0.2), hring(19, 49, 76, 16, jit=0.08, seed=2, squ=0.25),
             hring(30, 46, 72, 16, jit=0.06, seed=3, squ=0.3), hring(36, 40, 66, 16, jit=0.03, seed=4, squ=0.45),
             hring(38.5, 37, 63, 16, squ=0.9), hring(38.5, 18, 32, 16, squ=0.9)]
    F += loft("body", "rock", rings, caps=("belly", "slab"), mats=["belly", "plate", "rock", "plate", "rock", "slab"])
    # 甲羅の縁の板 (下の縁に並ぶ黒い殻)
    for k in range(16):
        a = 2 * math.pi * (k + 0.5) / 16
        if abs(math.cos(a)) > 0.92:     # 首・尾の出口は空ける
            continue
        p = V(math.sin(a) * 46, 14, -math.cos(a) * 72)
        out = nrm(V(math.sin(a) / 47, 0, -math.cos(a) / 73))
        R = np.column_stack([np.cross(V(0, 1, 0), out), V(0, 1, 0), out])
        F += fbox("body", "plate", p, (14, 9, 4), R=R @ rot_x(18), taper=(0.8, 0.8), cut=0.3)
    # ================================================================ 台地の石の板 (鞍。上面 = SEAT_Y)
    sb = rrect(V(0, 36.5, 0), (1, 0, 0), (0, 0, 1), 66, 118, 0.25)
    st = rrect(V(0, SEAT_Y, 0), (1, 0, 0), (0, 0, 1), 68, 120, 0.25)
    F += loft("saddle", "slab", [sb, st], caps=("slab", "slab"))
    # 台地のふちを囲む燠の結晶 (鞍の外)
    rng = np.random.default_rng(7)
    for k in range(18):
        a = 2 * math.pi * (k + 0.25) / 18
        sx, sz = math.sin(a), -math.cos(a)
        p = V(sx * 39.5, 35, sz * 65.5)
        out = nrm(V(sx / 40, 0, sz / 66))
        ln = 9 + 5 * rng.random()
        F += crystal("saddle", p, out * 0.8 + V(0, 1.0, 0), ln, 2.6 + 0.8 * rng.random())
        if k % 3 == 0:
            F += crystal("saddle", p + out * 2 + V(0, -1.5, 0), out * 1.4 + V(0, 0.6, 0), ln * 0.6, 1.8)
    # ================================================================ 甲羅の前のふちの核 (歌のかけら)
    F += core_mote(V(0, 31, -72), r=5.5, ring_mat="plate")
    # ================================================================ 首・頭 (前へ伸びる)・顎
    F += rod("neck", "skin", [V(0, 24, -58), V(0, 33, -72), V(0, 42, -80)], [9.5, 8.5, 8.0], sides=8)
    for z, y in ((-68, 38), (-76, 46)):
        F += fbox("neck", "plate", (0, y, z), (9, 2.5, 7), R=rot_x(-40), taper=(0.7, 0.7), cut=0.3)
    F += body_loft("head", "rock", [(-75, 47, 12, 9, 8), (-85, 48, 13.5, 9, 8), (-95, 46, 10, 7, 6), (-101, 43, 5, 3.5, 3.5)],
                   n=10, squ=0.25)
    F += fbox("head", "plate", (0, 56, -85), (16, 3, 15), taper=(0.75, 0.75), cut=0.35)
    for s in (1, -1):
        F += ellipsoid("head", "eye", V(s * 11.2, 50, -89), (3.0, 2.8, 3.0), segs=6, rings=2)
    F += body_loft("jaw", "skin", [(-80, 39.5, 10.5, 2.5, 2.5), (-93, 39.5, 8, 2, 2), (-100, 40, 4, 1.5, 1.5)], n=8)
    # ================================================================ 尾 (短い)
    F += cone("tail", "skin", V(0, 22, 64), (0, -0.25, 1), 20, 6.5, sides=6, rings=2)
    F += crystal("tail2", V(0, 18, 80), (0, 0.4, 1), 6, 2.0)
    # ================================================================ 脚 (柱のよう)
    for b in G.LEGS:
        F += leg(b)
    return F, None
