# -*- coding: utf-8 -*-
"""灰の小刀（x32・伝説）。前の形: 黒い刃（先だけ熾火の橙）、暗い柄。
火のシリーズ（色は series/ember.py）。伝説: 鍔に灰の翼（一つ足す）。見せ所は鍔の炉の窓と柄頭の熾火（光る二点）。
原点は握りの真ん中、刃は +x。
"""
NAME = "ember_ash_knife"
SERIES = "ember"


def blade():
    # 黒い刃（少し広い。先は背の側へ反る）
    rows = [(7, -2.6, 3.0), (16, -2.6, 3.4), (24, -3.0, 2.6), (30, -3.6, 0.8)]
    t = 0.8
    V = []
    for y, xb, xe in rows:
        mid = (xb + xe) / 2
        V += [(xb, y, 0), (mid, y, -t), (xe, y, 0), (mid, y, t)]
    V.append((-4.0, 35, 0))
    F = [[0, 1, 2, 3]]
    for L in range(len(rows) - 1):
        a, b = L * 4, (L + 1) * 4
        F += [[a + k, a + (k + 1) % 4, b + (k + 1) % 4, b + k] for k in range(4)]
    top = (len(rows) - 1) * 4
    F += [[top + k, top + (k + 1) % 4, 16] for k in range(4)]
    return ("mesh", "blade", "obsidian", V, F, "blade")


PARTS = [
    ("pommel", "obsidian", [-2.5, -12, -2.5], [2.5, -9, 2.5], "worn"),
    ("pommel_glow", "ember", [-1, -12.5, -1], [1, -11.5, 1], "gem"),
    ("grip", "soot", [-2, -9, -2], [2, 4, 2], "band"),
    ("ring_1", "ashiron", [-2.5, -6, -2.5], [2.5, -5, 2.5], "plain"),
    ("ring_2", "ashiron", [-2.5, -1, -2.5], [2.5, 0, 2.5], "plain"),
    # 鍔: 灰の板と左右の灰の翼（後ろへ反る）
    ("guard", "ashiron", [-6, 4, -2.5], [6, 6, 2.5], "plain"),
    seg("wing_l", "ashiron", (-4, 5), (-7, 8), 2, 2, "plain"),
    seg("wing_r", "ashiron", (4, 5), (7, 8), 2, 2, "plain"),
    blade(),
    # 見せ所: 鍔の炉の窓（熾火、光る一点）
    ("core_glow", "ember", [-1.5, 4, -3.6], [1.5, 6, -2.6], "gem"),
]
