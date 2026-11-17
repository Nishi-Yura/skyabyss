# -*- coding: utf-8 -*-
"""鯨骨の大剣（x32・レア）。前の形: 広い骨の刃、背（-x）に欠けた骨の歯が並ぶ、刃に空色の光る筋、暗い石の鍔に棘、
苔の巻きの握りに骨の輪、暗い石の柄頭。鯨のシリーズ（基準は whale_hammer）。レア: 見せ所は空色の筋の一つ。原点は握りの真ん中。
"""
NAME = "whalebone_greatsword"
SERIES = "whale"


def blade():
    # 広い骨の刃（下 ±5、y 30 で一番広い ±7、y 52 で ±5、y 66 で尖る）。断面は六角
    levels = [(10, 5.0), (30, 7.0), (52, 5.2)]
    t = 1.2
    V = []
    for y, w in levels:
        r = w - 1.8
        V += [(-w, y, 0), (-r, y, -t), (r, y, -t), (w, y, 0), (r, y, t), (-r, y, t)]
    V.append((0.5, 68, 0))
    F = [[0, 1, 2, 3], [3, 4, 5, 0]]
    for L in range(len(levels) - 1):
        a, b = L * 6, (L + 1) * 6
        F += [[a + k, a + (k + 1) % 6, b + (k + 1) % 6, b + k] for k in range(6)]
    top = (len(levels) - 1) * 6
    F += [[top + k, top + (k + 1) % 6, 18] for k in range(6)]
    return ("mesh", "blade", "bone", V, F, "blade")


PARTS = [
    ("pommel_a", "slate", [-3.5, -16, -3], [3.5, -12, 3], "worn"),
    ("pommel_b", "slate", [-3, -16.5, -3.5], [3, -11.5, 3.5], "worn"),
    ("grip", "moss", [-2.5, -12, -2.5], [2.5, 7, 2.5], "band"),
    ("ring_1", "bone", [-3, -9, -3], [3, -8, 3], "plain"),
    ("ring_2", "bone", [-3, -3, -3], [3, -2, 3], "plain"),
    ("ring_3", "bone", [-3, 3, -3], [3, 4, 3], "plain"),
    # 鍔: 暗い石の輪と、左右へ伸びる棘 2 本ずつ
    ("guard_a", "slate", [-6, 7, -3.5], [6, 11, 3.5], "worn"),
    ("guard_b", "slate", [-5, 6.5, -4], [5, 11.5, 4], "worn"),
    seg("spike_l1", "slate", (-5.5, 9), (-10, 11), 2, 2, "plain"),
    seg("spike_r1", "slate", (5.5, 9), (10, 11), 2, 2, "plain"),
    seg("spike_l2", "slate", (-5, 8), (-8, 6), 1.5, 1.5, "plain"),
    seg("spike_r2", "slate", (5, 8), (8, 6), 1.5, 1.5, "plain"),
    blade(),
    # 背（-x）の欠けた骨の歯（刃から張り出す段）
    ("tooth_1", "bone", [-8.5, 20, -1], [-6, 23, 1], "plain"),
    ("tooth_2", "bone", [-9, 28, -1], [-6.5, 31, 1], "plain"),
    ("tooth_3", "bone", [-8, 36, -1], [-5.5, 39, 1], "plain"),
    ("tooth_4", "bone", [-7, 44, -1], [-4.5, 47, 1], "plain"),
    # 空色の光る筋（刃の表と裏、真ん中より少し背の側）
    ("vein_f_glow", "sky", [-1.5, 16, -1.4], [0, 40, -1.1], "grad"),
    ("vein_b_glow", "sky", [-1.5, 16, 1.1], [0, 40, 1.4], "grad"),
]
