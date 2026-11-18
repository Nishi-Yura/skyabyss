# -*- coding: utf-8 -*-
"""祠の弓（x32）と引きの 3 段。決まった 1/16 の形（弓腕は +x 側へ張り出して曲がり、先は弦の側へ戻る。握りに紐、握りの外に空色の石、
弓腕と先の継ぎ目に輪）を 2 倍の細かさで。始めの武器は木が基調（60 の 1.6）: 弓腕は流木、先は濃い木、留め具は青銅。
弦は y = 44.5 と -14.5 の先から、握りの高さ y = 15 で引かれる。引きの段ごとに弦の真ん中が -x へ下がる（1/16 の時と同じ量の 2 倍）。
"""


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
    out = [piece(f"arm_{n}{i}", "wood", body[i], body[i + 1], 4, 4) for i in range(len(body) - 1)]
    out += [piece(f"tip_{n}{i}", "darkwood", tip[i], tip[i + 1], 3.5 - i * 0.5, 3, "log") for i in range(len(tip) - 1)]
    out.append(piece(f"ring_{n}", "bronze", (13.6, 32), (13.2, 35), 5.5, 5, "plain", 0))
    out.append(piece(f"nock_{n}", "bronze", (-1, 43.6), (-3.5, 44.6), 3.5, 3.5, "plain"))
    return out


BODY = arm(1) + arm(-1) + [
    ("grip", "cord", [9, 9, -3], [15, 21, 3], "band"),
    ("stone_front_glow", "sky", [10.5, 13.5, -4], [13.5, 16.5, -3], "gem"),
    ("stone_back_glow", "sky", [10.5, 13.5, 3], [13.5, 16.5, 4], "gem"),
]


def string(pull):
    """弦。pull = 引いた量（-x へ）。0 はまっすぐ"""
    top, bot = (-3, 44.5), (-3, -14.5)
    if pull == 0:
        return [("string", "string", [-3.5, -14.5, -0.5], [-2.5, 44.5, 0.5], "grad")]
    mid = (-3 - pull, 15)
    return [seg("string_u", "string", mid, top, 1, 1, "grad", 0.2),
            seg("string_d", "string", bot, mid, 1, 1, "grad", 0.2)]


VARIANTS = {
    "shrine_bow": BODY + string(0),
    "shrine_bow_pull0": BODY + string(2.4),
    "shrine_bow_pull1": BODY + string(6.8),
    "shrine_bow_pull2": BODY + string(11.6),
}
