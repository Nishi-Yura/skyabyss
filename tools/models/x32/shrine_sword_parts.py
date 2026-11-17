# -*- coding: utf-8 -*-
"""祠の剣（x32）。決まった 1/16 の形（広刃、鍔の両翼が上へ反り角が立つ、鍔の中に空色の石、柄頭に菱の輪）を 2 倍の細かさで。
始めの武器は木が基調（60 の 1.6）: 鍔・翼・角は濃い木、留め具は青銅。刃だけは前の形の鋼（2026-11-18、木の芯に鋼の縁を
付けた刃は「キモイ。前のでよかった」）。
金は刃の付け根の口金と柄頭の輪だけ。
"""
NAME = "shrine_sword"


def blade():
    levels = [(10, 3.2), (34, 4.8), (43, 4.0)]
    t = 0.64
    V = []
    for y, w in levels:
        r = w - 1.1
        V += [(-w, y, 0), (-r, y, -t), (r, y, -t), (w, y, 0), (r, y, t), (-r, y, t)]
    V.append((0, 54, 0))
    F = [[0, 1, 2, 3], [3, 4, 5, 0]]                       # 下のふた
    for L in range(len(levels) - 1):
        a, b = L * 6, (L + 1) * 6
        F += [[a + k, a + (k + 1) % 6, b + (k + 1) % 6, b + k] for k in range(6)]
    top = (len(levels) - 1) * 6
    F += [[top + k, top + (k + 1) % 6, 18] for k in range(6)]  # 切っ先
    return ("mesh", "blade", "steel", V, F, "blade")


PARTS = [
    # 柄頭の輪と柄
    ("pommel_top", "gold", [-3, -12, -1], [3, -11, 1], "plain"),
    ("pommel_bottom", "gold", [-3, -17, -1], [3, -16, 1], "plain"),
    ("pommel_left", "gold", [-3, -16, -1], [-2, -12, 1], "plain"),
    ("pommel_right", "gold", [2, -16, -1], [3, -12, 1], "plain"),
    ("pommel_cap", "bronze", [-3, -11, -3], [3, -8, 3], "plain"),
    ("grip", "cord", [-2, -8, -2], [2, 5, 2], "band"),
    # 鍔
    ("guard_a", "darkwood", [-5, 5, -3], [5, 11, 3], "log"),
    ("guard_b", "darkwood", [-4, 4, -3.5], [4, 12, 3.5], "log"),
    seg("wing_l", "darkwood", (-4, 8), (-11, 13), 3, 4, "log"),
    seg("wing_r", "darkwood", (4, 8), (11, 13), 3, 4, "log"),
    ("collar_l", "bronze", [-13, 11, -2.5], [-9, 15, 2.5], "plain"),
    ("collar_r", "bronze", [9, 11, -2.5], [13, 15, 2.5], "plain"),
    seg("horn_l", "darkwood", (-11, 15), (-13, 21), 2.5, 3, "log"),
    seg("horn_r", "darkwood", (11, 15), (13, 21), 2.5, 3, "log"),
    ("stone_front_glow", "sky", [-1.5, 6.5, -4.5], [1.5, 9.5, -3.5], "gem"),
    ("stone_back_glow", "sky", [-1.5, 6.5, 3.5], [1.5, 9.5, 4.5], "gem"),
    # 刃（広刃）: 決まった 1/16 の葉の形の鋼の刃をそのまま 2 倍（下 ±3.2、y 34 で一番広い ±4.8、y 43 で ±4、y 54 で尖る）。
    # 断面は六角（真ん中が平らで、両の刃へ斜めに薄くなる）
    ("blade_collar", "gold", [-4, 12, -1.5], [4, 14, 1.5], "plain"),
    blade(),
]
