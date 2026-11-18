# -*- coding: utf-8 -*-
"""火輝の弓（x32・伝説・火のシリーズ）と引きの 3 段。前の形: 黒い溶岩石の弓腕の割れ目から熾火が漏れる（橙の筋）、握りは灰の鉄。
伝説（60 の 1.6）: 骨格を一回り大きく（先まで ±38〜44）。弓腕の割れ目を熾火の筋として 1 本（光る細い筋は作らず、
割れ目の段差に熾火の石を置く）、握りの炉の窓、先の鉄の輪を足す。光る所は 2 か所（握りの炉・上の先の熾火）。
弦は -x。握りの真ん中は (12, 15)。
"""
NAME = "ember_flare_bow"
SERIES = "ember"


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
    out = [piece(f"arm_{n}{i}", "obsidian", body[i], body[i + 1], 4.4, 4.4, "worn") for i in range(len(body) - 1)]
    # 割れ目の熾火（弓腕の表の段差に一本、熾火の石の角）と、弓腕の縁の石の角
    out.append(piece(f"seam_{n}0", "ember", (13.3, 22), (14.6, 26.5), 1.4, 1.4, "plain"))
    out.append(piece(f"seam_{n}1", "ember", (14.2, 31), (15.4, 35), 1.4, 1.4, "plain"))
    out.append(piece(f"chip_{n}0", "ashiron", (16.0, 26), (17.8, 28.5), 1.6, 1.6, "plain"))
    out.append(piece(f"chip_{n}1", "ashiron", (16.4, 34), (18.0, 36.5), 1.6, 1.6, "plain"))
    out += [piece(f"tip_{n}{i}", "obsidian", tip[i], tip[i + 1], 3.6 - i * 0.4, 3, "worn") for i in range(len(tip) - 1)]
    out.append(piece(f"ring_{n}", "ashiron", (14.2, 36), (13.8, 39), 5.6, 5.6, "plain", 0))
    out.append(piece(f"nock_{n}", "ashiron", (-1, 46.8), (-3.5, 47.6), 3.2, 3.2, "plain"))
    return out


GRIP = [
    ("grip", "ashiron", [9, 9, -3.5], [15, 21, 3.5], "band"),
    ("ring_top", "ashiron", [8.5, 20, -4], [15.5, 22, 4], "plain"),
    ("ring_bot", "ashiron", [8.5, 8, -4], [15.5, 10, 4], "plain"),
    ("stove_f", "soot", [10.5, 12.5, -4.5], [13.5, 17.5, -3.5], "plain"),
    ("ember_f_glow", "ember", [11, 13, -5.5], [13, 17, -4.5], "gem"),
    ("stove_b", "soot", [10.5, 12.5, 3.5], [13.5, 17.5, 4.5], "plain"),
]

# 上の先の熾火（見せ所の 2 か所目）
TIP_GLOW = [("tip_u_glow", "ember", [7, 45.5, -1.4], [10, 48.5, 1.4], "gem")]

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
    "ember_flare_bow": BODY + string(0),
    "ember_flare_bow_pull0": BODY + string(2.4),
    "ember_flare_bow_pull1": BODY + string(6.8),
    "ember_flare_bow_pull2": BODY + string(11.6),
}
