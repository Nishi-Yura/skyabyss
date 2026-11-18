# -*- coding: utf-8 -*-
"""潮の短剣（x32・レア）。前の形: 細い銀の刃（淡い桃の筋）、緑の柄、珊瑚色の小さな鍔。
潮のシリーズ（基準は tide_hammer）。レア: 骨格と素材だけ。見せ所は刃の表と裏の淡い真珠の溝（光る）。
原点は握りの真ん中、刃は +x。
"""
NAME = "tide_dagger"
SERIES = "tide"


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
    return ("mesh", "blade", "shell", V, F, "blade")


PARTS = [
    ("pommel", "seastone", [-2.5, -12, -2.5], [2.5, -9, 2.5], "worn"),
    ("grip", "kelp", [-2, -9, -2], [2, 4, 2], "band"),
    ("ring_1", "tidering", [-2.5, -6, -2.5], [2.5, -5, 2.5], "plain"),
    ("ring_2", "tidering", [-2.5, -1, -2.5], [2.5, 0, 2.5], "plain"),
    # 鍔: 珊瑚の枝の鍔（角を落とした 2 箱）
    ("guard_a", "coral", [-5, 4, -2.5], [5, 6.5, 2.5], "log"),
    ("guard_b", "coral", [-4, 3.5, -3], [4, 7, 3], "log"),
    blade(),
    # 真珠の溝（刃の表と裏に細く。見せ所）
    ("groove_f_glow", "pearl", [-0.6, 10, -0.9], [0.6, 22, -0.6], "grad"),
    ("groove_b_glow", "pearl", [-0.6, 10, 0.6], [0.6, 22, 0.9], "grad"),
]
