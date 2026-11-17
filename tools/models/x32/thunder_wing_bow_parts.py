# -*- coding: utf-8 -*-
"""雷翼の弓（x32・レア・嵐のシリーズ）と引きの 3 段。前の形: 銅の稲妻のジグザグの弓腕、羽根の束（灰白・先が黒い）が
弓腕の両側に流れる。レア（60 の 1.6）: 骨格と素材だけ。羽根は弓腕の先に一束（表と裏）、飾りは一点まで（握りの雷の点）。
弦は -x。握りの真ん中は (12, 15)。
"""
NAME = "thunder_wing_bow"
SERIES = "storm"


def arm(sign):
    """上の弓腕（sign=1）か下の弓腕（sign=-1）。y は握りの高さ 15 を中心に折り返す"""
    def p(x, y):
        return (x, 15 + sign * (y - 15))

    def piece(name, mat, a, b, w, d, kind="band", over=0.5):
        a, b = p(*a), p(*b)
        return seg(name, mat, a, b, w, d, kind, over) if sign > 0 else seg(name, mat, b, a, w, d, kind, over)

    n = "u" if sign > 0 else "d"
    body = [(12, 15), (14, 22), (11.5, 28), (13, 34)]
    tip = [(13, 34), (10, 39), (5, 42.5), (0, 44), (-3, 44.5)]
    out = [piece(f"arm_{n}{i}", "copper", body[i], body[i + 1], 3.6, 3.6, "grain") for i in range(len(body) - 1)]
    # 羽根の束: 弓腕の先の外側へ、灰白の羽根を三枚（先は黒い）
    out.append(piece(f"wing_{n}0", "feather", body[3], (17.5, 40.5), 2.0, 2.4, "grain"))
    out.append(piece(f"wing_{n}1", "feather", body[3], (19, 36), 2.0, 2.4, "grain"))
    out.append(piece(f"wing_{n}2", "feather", body[3], (16.5, 45), 2.0, 2.4, "grain"))
    out += [piece(f"tip_{n}{i}", "verdigris", tip[i], tip[i + 1], 3.0 - i * 0.4, 2.6, "worn") for i in range(len(tip) - 1)]
    out.append(piece(f"ring_{n}", "stormring", (13.4, 32), (13.0, 35), 5.0, 5.0, "plain", 0))
    out.append(piece(f"nock_{n}", "stormring", (-1, 43.6), (-3.5, 44.6), 3.0, 3.0, "plain"))
    return out


GRIP = [
    ("grip", "string", [9, 9, -3.5], [15, 21, 3.5], "band"),
    ("ring_top", "copper", [8.5, 20, -4], [15.5, 22, 4], "plain"),
    ("ring_bot", "copper", [8.5, 8, -4], [15.5, 10, 4], "plain"),
    ("socket_f", "ironnavy", [10.5, 12.5, -4.5], [13.5, 17.5, -3.5], "plain"),
    ("bolt_f_glow", "bolt", [11, 13, -5.5], [13, 17, -4.5], "gem"),
    ("socket_b", "ironnavy", [10.5, 12.5, 3.5], [13.5, 17.5, 4.5], "plain"),
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
    "thunder_wing_bow": BODY + string(0),
    "thunder_wing_bow_pull0": BODY + string(2.4),
    "thunder_wing_bow_pull1": BODY + string(6.8),
    "thunder_wing_bow_pull2": BODY + string(11.6),
}
