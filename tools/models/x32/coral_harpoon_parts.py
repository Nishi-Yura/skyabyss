# -*- coding: utf-8 -*-
"""珊瑚の銛（x32・伝説、槍）。前の形: 淡い桃の珊瑚の穂先（先が細い）、後ろへ返る 2 本の返し（もり）、
深い青緑の柄、縄の巻き、珊瑚の芽。潮のシリーズ（基準は tide_hammer）。
伝説（60 の 1.6）: 穂先を太く、返しを大きく、金の輪を 1 つ足す。光るのは穂先の根元の真珠一つ。原点は握りの真ん中、穂先は +y。
"""
NAME = "coral_harpoon"
SERIES = "tide"


def prong(name, mat, cx, levels, tip, t, kind="grad"):
    """穂先（縦に伸びる刃）。段ごとに六角断面、先は一点"""
    V = []
    for y, w in levels:
        r = w - 1.0
        V += [(cx - w, y, 0), (cx - r, y, -t), (cx + r, y, -t), (cx + w, y, 0), (cx + r, y, t), (cx - r, y, t)]
    V.append((cx, tip, 0))
    F = [[0, 1, 2, 3], [3, 4, 5, 0]]
    for L in range(len(levels) - 1):
        a, b = L * 6, (L + 1) * 6
        F += [[a + k, a + (k + 1) % 6, b + (k + 1) % 6, b + k] for k in range(6)]
    top = (len(levels) - 1) * 6
    F += [[top + k, top + (k + 1) % 6, len(V) - 1] for k in range(6)]
    return ("mesh", name, mat, V, F, kind)


PARTS = [
    # 石突きと柄
    ("butt", "seastone", [-2.5, -20, -2.5], [2.5, -16, 2.5], "worn"),
    ("shaft", "seastone", [-2, -16, -2], [2, 24, 2], "grain"),
    ("grip", "kelp", [-3, -9, -3], [3, 6, 3], "band"),
    ("ring_low", "tidering", [-3.5, -9.5, -3.5], [3.5, -8.5, 3.5], "plain"),
    ("ring_high", "tidering", [-3.5, 6, -3.5], [3.5, 7, 3.5], "plain"),
    # 縄の巻き（柄の上）と金の輪（伝説）
    ("rope", "string", [-2.6, 12, -2.6], [2.6, 16, 2.6], "band"),
    ("gold_ring", "gold", [-3, 21, -3], [3, 22.5, 3], "plain"),
    ("collar", "shell", [-4, 24, -4], [4, 26, 4], "plain"),
    # 穂先（太い珊瑚の刃、先が細く尖る）
    prong("head", "coral", 0, [(26, 2.4), (38, 3.0), (50, 2.2)], 66, 1.2),
    # 返し（後ろの下へ返る 2 本の枝）
    seg("barb_l1", "coral", (-2.5, 36), (-9, 29), 2.4, 2.4, "grad"),
    seg("barb_l2", "coral", (-9, 29), (-11, 32), 1.5, 1.5, "plain"),
    seg("barb_r1", "coral", (2.5, 36), (9, 29), 2.4, 2.4, "grad"),
    seg("barb_r2", "coral", (9, 29), (11, 32), 1.5, 1.5, "plain"),
    # 見せ所: 穂先の根元の真珠（前の面）
    ("pearl_glow", "pearl", [-1.2, 30, -2.4], [1.2, 33, -1.1], "gem"),
]
