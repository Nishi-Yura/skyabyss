# -*- coding: utf-8 -*-
"""貝の三叉（x32・レア、槍・鉾）。前の形: 淡い貝の三叉の穂先（中が高く両脇が低い）、珊瑚の枝の飾り、
深い青緑の柄（海の石）、昆布の巻きの握り、淡い青の輪、貝の台の真珠。潮のシリーズ（基準は tide_hammer）。
レア（60 の 1.6）: 骨格と素材だけ。見せ所は貝の台の真珠の一つ。原点は握りの真ん中、穂先は +y。
"""
NAME = "tide_shell_trident"
SERIES = "tide"


def prong(name, mat, cx, levels, tip, t, kind="grad"):
    """穂先（縦に伸びる細い刃）。段ごとに六角断面、先は一点。cx は横の位置"""
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
    # 石突きと柄（海の石・昆布の巻き・淡い青の輪）
    ("butt", "seastone", [-2.5, -20, -2.5], [2.5, -16, 2.5], "worn"),
    ("shaft", "seastone", [-2, -16, -2], [2, 30, 2], "grain"),
    ("grip", "kelp", [-3, -9, -3], [3, 7, 3], "band"),
    ("ring_low", "tidering", [-3.5, -9.5, -3.5], [3.5, -8.5, 3.5], "plain"),
    ("ring_high", "tidering", [-3.5, 6, -3.5], [3.5, 7, 3.5], "plain"),
    ("collar", "tidering", [-4, 28, -4], [4, 30, 4], "plain"),
    # 貝の台（穂先の根元の横棒）
    ("shell_base", "shell", [-7, 30, -3.5], [7, 36, 3.5], "plain"),
    # 三叉の穂先: 中が高く、両脇が低い
    prong("tine_c", "shell", 0, [(36, 2.6), (48, 3.2), (58, 2.2)], 68, 0.9),
    prong("tine_l", "shell", -6.5, [(36, 2.0), (46, 2.2), (54, 1.6)], 60, 0.7),
    prong("tine_r", "shell", 6.5, [(36, 2.0), (46, 2.2), (54, 1.6)], 60, 0.7),
    # 珊瑚の枝（根元の両脇へ広がる）
    seg("horn_l1", "coral", (-7, 33), (-11, 29), 2.5, 2.5, "grad"),
    seg("horn_l2", "coral", (-11, 29), (-13.5, 32), 1.5, 1.5, "plain"),
    seg("horn_r1", "coral", (7, 33), (11, 29), 2.5, 2.5, "grad"),
    seg("horn_r2", "coral", (11, 29), (13.5, 32), 1.5, 1.5, "plain"),
    # 見せ所: 貝の台に置いた真珠（前の面）
    ("pearl_glow", "pearl", [-1.5, 31.5, -4.5], [1.5, 34.5, -3.5], "gem"),
]
