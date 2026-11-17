# -*- coding: utf-8 -*-
"""残り火の短剣（x32・レア）。前の形: 鉤の付いた黒い刃（先の背に鉤）、灰色の鉄の柄、鍔の下の橙の光。
火のシリーズ（色は series/ember.py）。レア: 骨格と素材だけ。鉤の形が見せ所。
炉の窓（熾火）を鍔の表に一つ（光る）。原点は握りの真ん中、刃と鉤は +x。
"""
NAME = "ember_dagger"
SERIES = "ember"


def blade():
    # 黒い溶岩石の刃（高さ y, 背の x, 刃の x）。先は背の側へ反る
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
    return ("mesh", "blade", "obsidian", V, F, "blade")


PARTS = [
    ("pommel", "obsidian", [-2.5, -12, -2.5], [2.5, -9, 2.5], "worn"),
    ("grip", "ashiron", [-2, -9, -2], [2, 4, 2], "band"),
    ("ring_1", "soot", [-2.5, -6, -2.5], [2.5, -5, 2.5], "plain"),
    ("ring_2", "soot", [-2.5, -1, -2.5], [2.5, 0, 2.5], "plain"),
    # 鍔: 焦げた鉄の 2 箱
    ("guard_a", "soot", [-5, 4, -2.5], [5, 6.5, 2.5], "log"),
    ("guard_b", "soot", [-4, 3.5, -3], [4, 7, 3], "log"),
    blade(),
    # 鉤（背の側の先に、後ろへ反る一本）
    seg("hook_1", "ashiron", (-2.5, 24), (-5, 29), 1.8, 1.8, "plain"),
    seg("hook_tip", "ashiron", (-5, 29), (-6.5, 31), 1.2, 1.2, "plain"),
    # 見せ所: 炉の窓（鍔の表の熾火、光る一点）
    ("window_glow", "ember", [-1.5, 4.5, -3.6], [1.5, 6.5, -2.6], "gem"),
]
