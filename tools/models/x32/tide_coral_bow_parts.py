# -*- coding: utf-8 -*-
"""潮の珊瑚弓（x32・伝説）と引きの 3 段。前の形: 珊瑚の枝の長い弓腕と棘（白灰の枝・桃）、握りは海の石と貝。
伝説（60 の 1.6）: 骨格を一回り大きく（先まで ±38〜44）。珊瑚の枝の分かれ（弓腕の外へ枝を 2 本ずつ、中ほどに一段の枝）と、
貝の扇（握りの上下に貝の板）を足す。光る所は 2 か所（握りの真珠・上の先の真珠）。
弦は -x。握りの真ん中は (12, 15)。
"""
NAME = "tide_coral_bow"
SERIES = "tide"


def arm(sign):
    """上の弓腕（sign=1）か下の弓腕（sign=-1）。y は握りの高さ 15 を中心に折り返す"""
    def p(x, y):
        return (x, 15 + sign * (y - 15))

    def piece(name, mat, a, b, w, d, kind="band", over=0.5):
        a, b = p(*a), p(*b)
        return seg(name, mat, a, b, w, d, kind, over) if sign > 0 else seg(name, mat, b, a, w, d, kind, over)

    n = "u" if sign > 0 else "d"
    body = [(12, 15), (13.5, 23), (15, 31), (14.5, 38.5)]
    tip = [(14.5, 38.5), (11.5, 44), (6.5, 47.5), (0, 49), (-3, 47.5)]
    out = [piece(f"arm_{n}{i}", "coral", body[i], body[i + 1], 4, 4, "grain") for i in range(len(body) - 1)]
    # 枝: 弓腕の外（+x）へ 2 本ずつ、中ほどで二股に分かれた枝（珊瑚の枝の分かれ）
    out.append(piece(f"thorn_{n}0", "coral", (15.5, 23), (18.5, 26), 1.8, 1.8, "plain"))
    out.append(piece(f"thorn_{n}1", "coral", (16.5, 31), (19.5, 34), 1.8, 1.8, "plain"))
    out.append(piece(f"fork_{n}a", "coral", (16.8, 33.5), (19.5, 39), 1.5, 1.5, "plain"))
    out.append(piece(f"fork_{n}b", "coral", (16.8, 33.5), (19.0, 30.5), 1.5, 1.5, "plain"))
    out.append(piece(f"ring_{n}", "shell", (14.4, 34), (14.0, 37), 5.5, 5.5, "plain", 0))
    out += [piece(f"tip_{n}{i}", "kelp", tip[i], tip[i + 1], 3.4 - i * 0.4, 2.8, "worn") for i in range(len(tip) - 1)]
    out.append(piece(f"nock_{n}", "shell", (-1, 46.8), (-3.5, 47.6), 3.0, 3.0, "plain"))
    return out


GRIP = [
    ("grip", "kelp", [9, 9, -3.5], [15, 21, 3.5], "band"),
    ("shell_top", "shell", [7.5, 20.5, -5], [16.5, 24, 5], "plain"),
    ("shell_bot", "shell", [7.5, 6, -5], [16.5, 9.5, 5], "plain"),
    ("ring_top", "shell", [8.5, 20, -4], [15.5, 22, 4], "plain"),
    ("ring_bot", "shell", [8.5, 8, -4], [15.5, 10, 4], "plain"),
    ("socket_f", "seastone", [10.5, 12.5, -4.5], [13.5, 17.5, -3.5], "plain"),
    ("pearl_f_glow", "pearl", [11, 13, -5.5], [13, 17, -4.5], "gem"),
    ("socket_b", "seastone", [10.5, 12.5, 3.5], [13.5, 17.5, 4.5], "plain"),
    ("pearl_b_glow", "pearl", [11, 13, 4.5], [13, 17, 5.5], "gem"),
]

# 上の先の枝に埋めた真珠（見せ所の 2 か所目）
TIP_GLOW = [("tip_u_glow", "pearl", [7, 44.5, -1.4], [10, 47.5, 1.4], "gem")]

BODY = arm(1) + arm(-1) + GRIP + TIP_GLOW


def string(pull):
    """弦。pull = 引いた量（-x へ）。0 はまっすぐ"""
    top, bot = (-3, 47.5), (-3, -17.5)
    if pull == 0:
        return [("string", "string", [-3.5, -17.5, -0.5], [-2.5, 47.5, 0.5], "grad")]
    mid = (-3 - pull, 15)
    return [seg("string_u", "string", mid, top, 1, 1, "grad", 0.2),
            seg("string_d", "string", bot, mid, 1, 1, "grad", 0.2)]


VARIANTS = {
    "tide_coral_bow": BODY + string(0),
    "tide_coral_bow_pull0": BODY + string(2.4),
    "tide_coral_bow_pull1": BODY + string(6.8),
    "tide_coral_bow_pull2": BODY + string(11.6),
}
