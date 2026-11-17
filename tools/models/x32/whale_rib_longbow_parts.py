# -*- coding: utf-8 -*-
"""鯨の肋骨の長弓（x32・伝説）と引きの 3 段。前の形: 黒い石の長い弓腕と青い輪、握りは苔と暗い石。
伝説（60 の 1.6）: 骨格を一回り大きく。弓腕は肋骨の骨を二本ずつ添えて鯨の肋骨の組みに見せる（弓腕の表と裏）。
上の端の空色の結晶（見せ所の 2 か所目: 握りと先）。金は使わない。
弦は -x。握りの真ん中は (12, 15)。上下の端は握りから ±32 前後（28〜34 の範囲）。
"""
NAME = "whale_rib_longbow"
SERIES = "whale"


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
    out = [piece(f"arm_{n}{i}", "bone", body[i], body[i + 1], 4, 4, "grain") for i in range(len(body) - 1)]
    # 肋骨: 弓腕の表（弦の側）と裏（背）に一本ずつ、弓腕に沿って添える
    for dx, tag in ((-2.4, "f"), (2.6, "b")):
        pts = [(x + dx, y) for x, y in body[1:]]
        out += [piece(f"rib_{tag}{n}{i}", "bone", pts[i], pts[i + 1], 1.6, 2.6, "plain") for i in range(len(pts) - 1)]
    # 石の縛り（弓腕と肋骨を一緒に巻く暗い石の帯）
    out.append(piece(f"lash_{n}", "slate", (14.6, 27), (14.9, 28.5), 7.5, 5, "plain", 0))
    out += [piece(f"tip_{n}{i}", "slate", tip[i], tip[i + 1], 3.4 - i * 0.4, 2.6, "worn") for i in range(len(tip) - 1)]
    out.append(piece(f"nock_{n}", "bone", (-1, 46.8), (-3.5, 47.6), 2.5, 2.5, "plain"))
    return out


GRIP = [
    ("grip", "moss", [9, 9, -3.5], [15, 21, 3.5], "band"),
    ("ring_top", "bone", [8.5, 20, -4], [15.5, 22, 4], "plain"),
    ("ring_bot", "bone", [8.5, 8, -4], [15.5, 10, 4], "plain"),
    ("socket_f", "slate", [10.5, 12.5, -4.5], [13.5, 17.5, -3.5], "plain"),
    ("crystal_f_glow", "sky", [11, 13, -5.5], [13, 17, -4.5], "gem"),
    ("socket_b", "slate", [10.5, 12.5, 3.5], [13.5, 17.5, 4.5], "plain"),
    ("crystal_b_glow", "sky", [11, 13, 4.5], [13, 17, 5.5], "gem"),
]

# 上の弓腕の先に、暗い石の台で埋めた空色の結晶（見せ所の 2 か所目）
TIP_GLOW = [("tip_u_glow", "sky", [7, 44.5, -1.4], [10, 47.5, 1.4], "gem")]

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
    "whale_rib_longbow": BODY + string(0),
    "whale_rib_longbow_pull0": BODY + string(2.4),
    "whale_rib_longbow_pull1": BODY + string(6.8),
    "whale_rib_longbow_pull2": BODY + string(11.6),
}
