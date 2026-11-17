# -*- coding: utf-8 -*-
"""鯨の短剣（x32・レア）。前の形: 骨の刃（少し反る）に空色の光る溝、暗い石の鍔に小さな棘、苔の巻きの握りに骨の輪、暗い石の柄頭。
鯨のシリーズ（基準は whale_hammer）。レア: 見せ所は空色の溝の一つ。原点は握りの真ん中、刃は +x。
"""
NAME = "whale_dagger"
SERIES = "whale"


def blade():
    # 刃の輪郭（高さ y, 背の x, 刃の x）。刃 (+x) がふくらみ、先は背の側へ反る
    rows = [(7, -2.2, 2.6), (16, -2.2, 3.0), (24, -2.6, 2.2), (30, -3.4, 0.4)]
    t = 0.7
    V = []
    for y, xb, xe in rows:
        mid = (xb + xe) / 2
        V += [(xb, y, 0), (mid, y, -t), (xe, y, 0), (mid, y, t)]
    V.append((-4.4, 35, 0))
    F = [[0, 1, 2, 3]]
    for L in range(len(rows) - 1):
        a, b = L * 4, (L + 1) * 4
        F += [[a + k, a + (k + 1) % 4, b + (k + 1) % 4, b + k] for k in range(4)]
    top = (len(rows) - 1) * 4
    F += [[top + k, top + (k + 1) % 4, 16] for k in range(4)]
    return ("mesh", "blade", "bone", V, F, "blade")


PARTS = [
    ("pommel_a", "slate", [-2.5, -12, -2], [2.5, -9, 2], "worn"),
    ("pommel_b", "slate", [-2, -12.5, -2.5], [2, -8.5, 2.5], "worn"),
    ("grip", "moss", [-2, -9, -2], [2, 4, 2], "band"),
    ("ring_1", "bone", [-2.5, -6, -2.5], [2.5, -5, 2.5], "plain"),
    ("ring_2", "bone", [-2.5, -1, -2.5], [2.5, 0, 2.5], "plain"),
    # 鍔: 暗い石の輪（角を落とした 2 箱）と左右の小さな棘
    ("guard_a", "slate", [-4, 4, -2.5], [4, 7, 2.5], "worn"),
    ("guard_b", "slate", [-3.5, 3.5, -3], [3.5, 7.5, 3], "worn"),
    seg("spike_l", "slate", (-3.5, 6), (-6, 8), 1.5, 1.5, "plain"),
    seg("spike_r", "slate", (3.5, 6), (6, 8), 1.5, 1.5, "plain"),
    blade(),
    # 空色の光る溝（刃の表と裏に細く）
    ("groove_f_glow", "sky", [-0.6, 10, -0.9], [0.6, 22, -0.6], "grad"),
    ("groove_b_glow", "sky", [-0.6, 10, 0.6], [0.6, 22, 0.9], "grad"),
]
