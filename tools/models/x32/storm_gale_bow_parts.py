# -*- coding: utf-8 -*-
"""嵐の風弓（x32・伝説）と引きの 3 段。前の形: 銅の稲妻のジグザグの弓腕（銅と鉄紺の柄）、羽根の飾り（灰白・先が黒い）。
伝説（60 の 1.6）: 骨格を一回り大きく。銅の弓腕は稲妻の折れを大きく（先まで ±38〜48）、折れの角ごとに羽根の板を足す。
光る所は 2 か所（握りの雷の結晶・上の先の雷の点）。
弦は -x。握りの真ん中は (12, 15)。
"""
NAME = "storm_gale_bow"
SERIES = "storm"


def arm(sign):
    """上の弓腕（sign=1）か下の弓腕（sign=-1）。y は握りの高さ 15 を中心に折り返す"""
    def p(x, y):
        return (x, 15 + sign * (y - 15))

    def piece(name, mat, a, b, w, d, kind="band", over=0.5):
        a, b = p(*a), p(*b)
        return seg(name, mat, a, b, w, d, kind, over) if sign > 0 else seg(name, mat, b, a, w, d, kind, over)

    n = "u" if sign > 0 else "d"
    # 稲妻の折れ: 弓腕の角を外と内へ交互に振る
    body = [(12, 15), (14, 21), (11.5, 27), (15, 33), (13, 38.5)]
    tip = [(13, 38.5), (10, 43.5), (5, 47), (0, 48.5), (-3, 47.5)]
    out = [piece(f"arm_{n}{i}", "copper", body[i], body[i + 1], 3.6, 3.6, "grain") for i in range(len(body) - 1)]
    # 羽根: 折れの角ごとに、弓腕の外（+x）へ灰白の板を一枚
    out.append(piece(f"fin_{n}0", "feather", body[1], (18, 19.5), 1.8, 3.2, "plain"))
    out.append(piece(f"fin_{n}1", "feather", body[3], (19, 30.5), 1.8, 3.2, "plain"))
    out += [piece(f"tip_{n}{i}", "verdigris", tip[i], tip[i + 1], 3.2 - i * 0.4, 2.6, "worn") for i in range(len(tip) - 1)]
    out.append(piece(f"ring_{n}", "stormring", (14.2, 36), (13.8, 39), 5.0, 5.0, "plain", 0))
    out.append(piece(f"nock_{n}", "stormring", (-1, 46.8), (-3.5, 47.6), 3.0, 3.0, "plain"))
    return out


GRIP = [
    ("grip", "string", [9, 9, -3.5], [15, 21, 3.5], "band"),
    ("ring_top", "copper", [8.5, 20, -4], [15.5, 22, 4], "plain"),
    ("ring_bot", "copper", [8.5, 8, -4], [15.5, 10, 4], "plain"),
    ("socket_f", "ironnavy", [10.5, 12.5, -4.5], [13.5, 17.5, -3.5], "plain"),
    ("bolt_f_glow", "bolt", [11, 13, -5.5], [13, 17, -4.5], "gem"),
    ("socket_b", "ironnavy", [10.5, 12.5, 3.5], [13.5, 17.5, 4.5], "plain"),
    ("bolt_b_glow", "bolt", [11, 13, 4.5], [13, 17, 5.5], "gem"),
]

# 上の先の雷の点（見せ所の 2 か所目）
TIP_GLOW = [("tip_u_glow", "bolt", [7, 45.5, -1.4], [10, 48.5, 1.4], "gem")]

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
    "storm_gale_bow": BODY + string(0),
    "storm_gale_bow_pull0": BODY + string(2.4),
    "storm_gale_bow_pull1": BODY + string(6.8),
    "storm_gale_bow_pull2": BODY + string(11.6),
}
