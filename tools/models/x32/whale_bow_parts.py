# -*- coding: utf-8 -*-
"""鯨の弓（x32・レア）と引きの 3 段。前の形: 骨の弓腕（反って張る）に棘、握りは苔の巻きに骨の輪、暗い石の台に空色の結晶。
弦は -x。握りの真ん中は (12, 15)。弓腕は +x へ張り出し、先は弦の側へ戻る（shrine_bow と同じ考え）。
シリーズ（whale）の色: 骨の弓腕、暗い石の先と台、苔の握り、空色の結晶。金は使わない。
レア（60 の 1.6）: 骨格と素材だけ。見せ所は握りの結晶（表と裏）一つ。
"""
NAME = "whale_bow"
SERIES = "whale"


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
    out = [piece(f"arm_{n}{i}", "bone", body[i], body[i + 1], 3.5, 3.5, "grain") for i in range(len(body) - 1)]
    out += [piece(f"tip_{n}{i}", "slate", tip[i], tip[i + 1], 3.2 - i * 0.5, 2.6, "worn") for i in range(len(tip) - 1)]
    # 棘: 弓腕の外（+x）へ 2 本ずつ、骨の先を尖らせる
    out.append(piece(f"thorn_{n}0", "bone", (14.5, 24), (17, 26.5), 1.5, 1.5, "plain"))
    out.append(piece(f"thorn_{n}1", "bone", (14.5, 30), (17, 32.5), 1.5, 1.5, "plain"))
    out.append(piece(f"nock_{n}", "bone", (-1, 43.8), (-3.5, 44.6), 2.5, 2.5, "plain"))
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
    "whale_bow": BODY + string(0),
    "whale_bow_pull0": BODY + string(2.4),
    "whale_bow_pull1": BODY + string(6.8),
    "whale_bow_pull2": BODY + string(11.6),
}
