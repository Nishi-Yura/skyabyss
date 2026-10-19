"""B 案: 灰板の火蜥蜴の仔 — 親 (採用案 C「皮が岩になる火蜥蜴」) をいちばんそのまま縮めた、低く長い火蜥蜴の仔。
脚は横へ張り出し、尾は横へ巻く。背の両脇に黒い殻の板が並び、そのあいだに灰色の平たい石の板 (鞍) が載る。
平たい頭の額に燠の結晶の冠 (歌のかけら)、胸にも光る核。"""
from __future__ import annotations

from ye import *  # noqa

NAME = "B 灰板の火蜥蜴"
TITLE = "灰板の火蜥蜴"
HINT = 512
PAL = base_pal()


def leg(bone):
    p = pv(bone)
    s = 1 if p[0] > 0 else -1
    front = p[2] < 0
    F = []
    elbow = V(s * 40, 22, p[2] + (-4 if front else 4))
    foot = V(s * 44, 2.0, p[2] + (-8 if front else 2))
    F += ellipsoid(bone, "skin", p + V(s * 6, 1, 0), (10, 8, 11), segs=8, rings=2)
    F += rod(bone, "skin", [p + V(s * 2, 0, 0), elbow], [7.0, 6.0], sides=6, hint=(0, 0, 1))
    F += ellipsoid(bone, "rock", elbow, (6.5, 6.5, 6.5), segs=6, rings=2)
    F += rod(bone, "rock", [elbow, foot + V(0, 2, 0)], [6.0, 5.0], sides=6, hint=(0, 0, 1))
    F += ellipsoid(bone, "skin", foot + V(0, 0.8, 0), (7.5, 2.8, 7.5), segs=8, rings=2)
    for k in range(4):   # 指 (扇に開く)
        a = math.radians(-60 + 40 * k) * s
        d = V(math.sin(a), -0.15, -math.cos(a)) if front else V(math.sin(a), -0.15, -math.cos(a) * 0.6)
        F += cone(bone, "claw", foot + V(0, 1.2, 0) + d * 4.5, d, 5.0, 1.6, sides=4)
    F += fbox(bone, "plate", elbow + V(s * 1, 5.5, 0), (8, 3, 8), R=rot_z(s * -20), taper=(0.6, 0.6), cut=0.3)
    return F


def build():
    F = []
    # ================================================================ 胴 (低く長い。灰茶の皮)
    F += body_loft("body", "skin", [(-66, 30, 18, 8, 8), (-58, 29, 30, 10, 10), (-30, 28, 36, 11, 9), (0, 28, 37, 11, 9),
                                    (30, 28, 36, 11, 9), (58, 28, 30, 10, 9), (66, 27, 22, 8, 8)], n=12, squ=0.25)
    F += body_loft("body", "belly", [(-54, 21, 26, 3, 3), (0, 20, 30, 3, 3), (54, 21, 26, 3, 3)], n=8, squ=0.2)
    # 背の両脇の黒い殻の板 (外へ傾く。鞍の外)
    for s in (1, -1):
        for i, z in enumerate(range(-50, 51, 20)):
            F += fbox("body", "plate", (s * 33, 36 - (i % 2), z), (12, 4, 17), R=rot_z(s * -26), taper=(0.6, 0.85), cut=0.3)
        F += fbox("body", "rock", (s * 38, 30, 0), (8, 6, 90), R=rot_z(s * -40), taper=(0.6, 0.9), cut=0.3)        # 脇の岩の帯
    # ================================================================ 灰色の石の板 (鞍。上面 = SEAT_Y)
    base_b = rrect(V(0, 30, 0), (1, 0, 0), (0, 0, 1), 44, 104, 0.1)
    base_t = rrect(V(0, 37, 0), (1, 0, 0), (0, 0, 1), 62, 114, 0.1)
    F += loft("saddle", "plate", [base_b, base_t], caps=("plate", "plate"))
    top_b = rrect(V(0, 36.5, 0), (1, 0, 0), (0, 0, 1), 57, 109, 0.12)
    top_t = rrect(V(0, SEAT_Y, 0), (1, 0, 0), (0, 0, 1), 56, 108, 0.12)
    F += loft("saddle", "slab", [top_b, top_t], caps=("slab", "slab"))
    F += fbox("saddle", "slab", (0, 42.5, -57), (40, 6, 5), taper=(0.85, 0.6), cut=0.3)      # 前の縁石
    F += fbox("saddle", "slab", (0, 42.0, 57), (40, 5, 5), taper=(0.85, 0.6), cut=0.3)       # 後ろの縁石
    for s in (1, -1):    # 板の脇の小さな石の柱 (鞍の外。手がかり)
        for z in (-40, -16, 16, 40):
            F += fbox("saddle", "slab", (s * 30.5, 41, z), (4, 6, 5), taper=(0.7, 0.7), cut=0.3)
    # ================================================================ 胸の核 (歌のかけら)
    F += core_mote(V(0, 28, -69), r=5.0, ring_mat="plate")
    # ================================================================ 首・平たい頭・顎
    F += body_loft("neck", "skin", [(-56, 32, 20, 9, 10), (-66, 36, 16, 8, 8), (-76, 40, 15, 7, 7)], n=8, squ=0.2)
    for z, y in ((-62, 43), (-72, 46)):
        F += fbox("neck", "plate", (0, y, z), (10, 3, 9), R=rot_x(-15), taper=(0.6, 0.7), cut=0.3)
    F += body_loft("head", "skin", [(-72, 44, 17, 7, 6), (-82, 45, 20, 8, 6), (-93, 44, 18, 6, 5), (-100, 42, 12, 4, 4)],
                   n=10, squ=0.25)
    F += fbox("head", "plate", (0, 51.5, -84), (22, 3, 18), taper=(0.8, 0.8), cut=0.35)       # 頭の殻
    for s in (1, -1):
        F += ellipsoid("head", "rock", V(s * 13, 50, -84), (4.6, 4.4, 4.6), segs=6, rings=2)   # 目の台
        F += ellipsoid("head", "eye", V(s * 14.2, 51.2, -85.5), (2.8, 2.6, 2.8), segs=6, rings=2)
        F += cone("head", "plate", V(s * 16, 47, -74), (s * 0.8, 0.3, 1.0), 9, 3.0, sides=4, rings=2)               # 頬の角
    # 額の燠の結晶の冠 (歌のかけら)
    F += cone("head", "crystal", V(0, 52.5, -88), (0, 1, 0.15), 8, 2.6, sides=4, rings=2)
    for s in (1, -1):
        F += cone("head", "crystal", V(s * 4, 52.5, -86), (s * 0.35, 1, 0.3), 5.5, 1.9, sides=4, rings=1)
    F += body_loft("jaw", "mouth", [(-80, 38, 18, 3, 3), (-94, 38.5, 15, 2.5, 2.5), (-100, 39, 10, 2, 2)], n=8, squ=0.25)
    # ================================================================ 尾 (平たく、先は横へ巻く)
    F += rod("tail", "skin", [V(0, 27, 56), V(0, 26, 68), V(0, 24, 80)], [8, 6.5, 5], rb=[20, 14, 9.5], sides=8)
    for z in (64, 74):
        F += fbox("tail", "plate", (0, 32.5 - (z - 64) * 0.15, z), (8, 3, 8), taper=(0.5, 0.6), cut=0.3)
    F += rod("tail2", "skin", [V(0, 24, 78), V(6, 22, 84), V(15, 20, 86.5), V(25, 19, 85.5)], [5, 4, 3, 1.6],
             rb=[9.5, 7, 4.5, 2], sides=8)
    F += cone("tail2", "crystal", V(25, 19, 85.5), (1, 0.1, -0.15), 5, 1.8, sides=4)
    # ================================================================ 脚 (横へ張り出す)
    for b in G.LEGS:
        F += leg(b)
    return F, None
