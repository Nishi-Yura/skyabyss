# -*- coding: utf-8 -*-
"""火の弓（x32・レア・火のシリーズ）と引きの 3 段。前の形: 黒い溶岩石の弓腕（ごつごつした黒灰）、握りは灰の鉄と熾火の割れ目。
レア（60 の 1.6）: 骨格と素材だけ。見せ所は握りの炉の窓一つ（表と裏）。割れ目の熾火は弓腕に入れない（光は 1 か所）。
弦は -x。握りの真ん中は (12, 15)。
"""
NAME = "ember_bow"
SERIES = "ember"


def arm(sign):
    """上の弓腕（sign=1）か下の弓腕（sign=-1）。y は握りの高さ 15 を中心に折り返す"""
    def p(x, y):
        return (x, 15 + sign * (y - 15))

    def piece(name, mat, a, b, w, d, kind="band", over=0.5):
        a, b = p(*a), p(*b)
        return seg(name, mat, a, b, w, d, kind, over) if sign > 0 else seg(name, mat, b, a, w, d, kind, over)

    n = "u" if sign > 0 else "d"
    body = [(12, 15), (13.5, 24), (14, 30), (13, 34)]
    tip = [(13, 34), (10, 39), (5, 42.5), (0, 44), (-3, 44.5)]
    out = [piece(f"arm_{n}{i}", "obsidian", body[i], body[i + 1], 4.2, 4.2, "worn") for i in range(len(body) - 1)]
    # 石の割れ目の縁（弓腕の表と裏に、暗い石の角を一つずつ）
    out.append(piece(f"chip_{n}0", "ashiron", (14.6, 26), (16.5, 28), 1.6, 1.6, "plain"))
    out.append(piece(f"chip_{n}1", "ashiron", (14.2, 32), (16.0, 34.5), 1.6, 1.6, "plain"))
    out += [piece(f"tip_{n}{i}", "obsidian", tip[i], tip[i + 1], 3.4 - i * 0.5, 3, "worn") for i in range(len(tip) - 1)]
    out.append(piece(f"ring_{n}", "ashiron", (13.6, 32), (13.2, 35), 5.2, 5.2, "plain", 0))
    out.append(piece(f"nock_{n}", "ashiron", (-1, 43.6), (-3.5, 44.6), 3.2, 3.2, "plain"))
    return out


GRIP = [
    ("grip", "ashiron", [9, 9, -3], [15, 21, 3], "band"),
    ("ring_top", "ashiron", [8.5, 20, -4], [15.5, 22, 4], "plain"),
    ("ring_bot", "ashiron", [8.5, 8, -4], [15.5, 10, 4], "plain"),
    ("stove_f", "soot", [10.5, 12.5, -4.5], [13.5, 17.5, -3.5], "plain"),
    ("ember_f_glow", "ember", [11, 13, -5.5], [13, 17, -4.5], "gem"),
    ("stove_b", "soot", [10.5, 12.5, 3.5], [13.5, 17.5, 4.5], "plain"),
]

BODY = arm(1) + arm(-1) + GRIP


def string(pull):
    """弦。pull = 引いた量（-x へ）。0 はまっすぐ"""
    top, bot = (-3, 44.5), (-3, -14.5)
    if pull == 0:
        return [("string", "string", [-3.5, -14.5, -0.5], [-2.5, 44.5, 0.5], "grad")]
    mid = (-3 - pull, 15)
    return [seg("string_u", "string", mid, top, 1, 1, "grad", 0.2),
            seg("string_d", "string", bot, mid, 1, 1, "grad", 0.2)]


VARIANTS = {
    "ember_bow": BODY + string(0),
    "ember_bow_pull0": BODY + string(2.4),
    "ember_bow_pull1": BODY + string(6.8),
    "ember_bow_pull2": BODY + string(11.6),
}
