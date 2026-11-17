# -*- coding: utf-8 -*-
"""雷獣の爪（x32・伝説）。前の形: 暗い鉄紺の三本の爪が扇に開き、根元は銅と布の握り。
嵐のシリーズ（色は series/storm.py）。伝説: 根元に銅の翼の帯（一つ足し）。見せ所は根元の雷の光（一点）。
原点は握りの真ん中、爪は +x へ開く。
"""
NAME = "raiju_claw"
SERIES = "storm"

PARTS = [
    ("pommel", "ironnavy", [-2.5, -11, -2.5], [2.5, -8, 2.5], "worn"),
    ("grip", "string", [-2, -8, -2], [2, 3, 2], "band"),
    ("ring_low", "copper", [-2.5, -5, -2.5], [2.5, -4, 2.5], "plain"),
    ("ring_high", "copper", [-2.5, 0, -2.5], [2.5, 1, 2.5], "plain"),
    # 根元の銅の翼の帯（伝説の一つ足し）
    ("band", "copper", [-5, 3, -3], [5, 5, 3], "log"),
    # 中央の爪
    seg("claw_c_1", "ironnavy", (0, 5), (0, 11), 3, 3, "grad"),
    seg("claw_c_2", "ironnavy", (0, 11), (0, 17), 2, 2, "grad"),
    seg("claw_c_tip", "ironnavy", (0, 17), (0.5, 19.5), 1.2, 1.2, "plain"),
    # 左右の爪（外へ開き、先は前へ）
    seg("claw_l_1", "ironnavy", (-2, 5), (-4, 10), 2.5, 2.5, "grad"),
    seg("claw_l_2", "ironnavy", (-4, 10), (-6.5, 14), 2, 2, "grad"),
    seg("claw_l_tip", "ironnavy", (-6.5, 14), (-8, 16), 1.2, 1.2, "plain"),
    seg("claw_r_1", "ironnavy", (2, 5), (4, 10), 2.5, 2.5, "grad"),
    seg("claw_r_2", "ironnavy", (4, 10), (6.5, 14), 2, 2, "grad"),
    seg("claw_r_tip", "ironnavy", (6.5, 14), (8, 16), 1.2, 1.2, "plain"),
    # 見せ所: 根元の雷の光（一点）
    ("bolt_glow", "bolt", [-1.2, 4.5, -3.6], [1.2, 6.5, -2.6], "gem"),
]
