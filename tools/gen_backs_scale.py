#!/usr/bin/env python3
"""巨神の背の「今」と「拡大案」の上から見た比較図と、床面積の数字を作る (設計書 26)。

    python tools/gen_backs_scale.py            # docs/review/backs_scale/ に png と metrics.json
    python tools/gen_backs_scale.py --scale 2  # 倍率を変えて試す (index.html の図は 1.6)

「今」の部屋は *Layout.java の箱から写したもの (2026-10-20、岩鯨 12・潮殻 8・雷翼 6・余燼 6)。
「拡大案」は、今の部屋を倍率 S で位置ごと拡大した形 + 新しい部屋 (設計書 21 の初回の拡張と 26 の追加) の概念図。
配置は実装担当が決める。この図は大きさの感覚をつかむためのもの。
"""
import argparse
import json
import math
import os
import sys

from PIL import Image, ImageDraw, ImageFont

OUT = os.path.join(os.path.dirname(__file__), "..", "docs", "review", "backs_scale")
FONT = "C:/Windows/Fonts/YuGothM.ttc"

# 部屋: (名前, 種類, 形, 高さ)   形 = ("r", x1, x2, z1, z2) 両端を含む / ("c", cx, cz, 半径)
# 種類: hub 広間 / trial 試練の部屋 / boss ボス戦 / heart 心臓 / corr 通路 / rest 前室・休み場 / lower 下の層
R = lambda x1, x2, z1, z2: ("r", x1, x2, z1, z2)
C = lambda cx, cz, r: ("c", cx, cz, r)

TITANS = {}

# ---- 岩鯨 (TitanBackLayout 版 12。体は x -138..131, z -172..261) ----
TITANS["whale"] = dict(
    label="岩鯨",
    bounds=(-140, 135, -175, 262),
    body=[(0, -165), (-22, -150), (-34, -125), (-43, -90), (-43, 150), (-30, 205), (-62, 236), (-58, 257), (-8, 246),
          (0, 252), (8, 246), (58, 257), (62, 236), (30, 205), (43, 150), (43, -90), (34, -125), (22, -150)],
    extra_body=[
        [(-43, -62), (-100, -34), (-98, -20), (-43, -28)],  # 左の胸びれ
        [(43, -62), (100, -34), (98, -20), (43, -28)],      # 右の胸びれ
    ],
    k=1.45,
    layout="side",
    rooms=[
        ("鼓動の間", "hub", R(-9, 9, -32, -12), 12),
        ("宝物庫", "rest", R(-6, 6, -44, -34), 7),
        ("入口の通路", "corr", R(-1, 1, -11, 4), 4),
        ("西の前室", "rest", R(-22, -16, -26, -18), 6),
        ("肋骨の谷", "trial", R(-36, -24, -34, -12), 11),
        ("竪穴", "trial", R(-31, -22, -56, -44), 11),
        ("崩れ橋", "trial", R(-19, -11, -52, -30), 11),
        ("東の前室", "rest", R(16, 22, -26, -18), 6),
        ("第一の広間", "boss", R(24, 36, -34, -5), 8),
        ("控え", "rest", R(26, 32, -42, -36), 5),
        ("番兵長の広間", "boss", R(11, 19, -60, -30), 8),
        ("回廊", "corr", R(-1, 1, -63, -46), 4),
        ("心臓の前室", "rest", R(-3, 3, -57, -51), 4),
        ("心臓の間", "heart", C(0, -85, 19), 12),
        ("闇の水路 (下)", "lower", R(-22, 22, -62, -2), 4),
        ("階段", "corr", R(-34, 2, 104, 106), 4),
        ("祠", "rest", R(-5, 5, 29, 49), 6),
        ("広場", "rest", C(0, 100, 9), 6),
    ],
    heart_name="心臓の間",
    # 拡大案で追加する部屋 (拡大後の座標)。opt = 任意の翼、man = 必須の長い道 (21 の初回の拡張)
    new=[
        ("数え歌の鐘楼", "man", R(-66, -42, 8, 42), 20),
        ("骨の書庫", "opt", R(-66, -46, 52, 80), 10),
        ("第二の広間", "man", R(34, 64, 8, 50), 12),
        ("番兵の詰所", "man", R(36, 64, 58, 78), 8),
        ("左の胸びれの道", "opt", R(-156, -86, -88, -52), 8),
        ("右の胸びれの道", "opt", R(86, 156, -88, -52), 8),
        ("尾の展望", "opt", R(-26, 26, 300, 352), 10),
        ("歌の祠", "man", R(10, 24, -110, -92), 6),
    ],
    # 拡大案の倍率 (拡大の標準 S と違うもの)
    scale_override={"心臓の間": 1.3, "鼓動の間": 1.9, "入口の通路": None, "回廊": None, "階段": None},
    corridor_width=5,
    time_now="25〜35 分 (21 の見込み。東の翼を短くした後)",
    walk_now="到着→鼓動の間 118 歩 (約 40 秒)",
    standing_now=26009,
)

# ---- 潮殻 (TideBackLayout 版 8。体は x -141..140, z -153..115) ----
def _superellipse(cx, cz, rx, rz, n, steps=72):
    pts = []
    for i in range(steps):
        t = 2 * math.pi * i / steps
        c, s = math.cos(t), math.sin(t)
        x = rx * math.copysign(abs(c) ** (2 / n), c)
        z = rz * math.copysign(abs(s) ** (2 / n), s)
        pts.append((cx + x, cz + z))
    return pts


TITANS["tide"] = dict(
    label="潮殻",
    bounds=(-145, 145, -155, 118),
    body=_superellipse(0, -3, 71, 85, 2.6),
    extra_body=[
        [(-6, -86), (-12, -125), (-16, -148), (16, -148), (12, -125), (6, -86)],      # 首と頭
        [(-66, -30), (-122, -52), (-136, -40), (-122, -8), (-68, 5)],                  # 左の前ひれ
        [(66, -30), (122, -52), (136, -40), (122, -8), (68, 5)],                       # 右の前ひれ
        [(-60, 50), (-92, 70), (-100, 96), (-66, 84)],                                 # 左の後ろひれ (到着)
        [(60, 50), (92, 70), (100, 96), (66, 84)],
    ],
    k=2.0,
    layout="side",
    rooms=[
        ("潮門の浜", "rest", R(-32, -12, 42, 62), 8),
        ("到着の段", "corr", R(-67, -56, 49, 59), 8),
        ("潮の広間", "hub", C(0, 0, 12), 13),
        ("珊瑚の庭", "trial", R(-62, -19, -42, 40), 30),
        ("潮見の間", "trial", R(24, 56, -30, 10), 18),
        ("干潟の前室", "rest", R(-4, 4, -30, -18), 3),
        ("群れの干潟", "trial", R(-24, 24, -82, -50), 12),
        ("殻の底", "heart", C(0, -30, 16), 12),
        ("縁のくぐり道", "corr", R(-12, -10, 22, 42), 4),
        ("予約の間 1", "sealed", R(-32, 30, -38, 34), 9),
        ("予約の間 2", "sealed", R(-22, 22, -28, 22), 9),
        ("予約の間 3", "sealed", R(-10, 10, -16, 12), 8),
    ],
    heart_name="殻の底",
    new=[
        ("息の間 (大空洞)", "man", C(0, -5, 15), 70),
        ("黒真珠の淵", "man", R(-88, -48, 66, 92), 14),
        ("四段目の溜め", "man", R(90, 108, -52, -26), 12),
        ("第二の巣域 (西)", "man", R(-76, -42, -112, -70), 12),
        ("第二の巣域 (東)", "man", R(42, 76, -112, -70), 12),
        ("首の道", "opt", R(-8, 8, -236, -142), 8),
        ("潮の祠", "man", R(8, 22, -50, -34), 6),
    ],
    scale_override={"殻の底": 1.3, "潮の広間": 1.9, "縁のくぐり道": None, "到着の段": None, "予約の間 1": 1.0, "予約の間 2": 1.0, "予約の間 3": 1.0},
    corridor_width=5,
    time_now="25〜35 分 (21 の見込み)",
    walk_now="到着→潮の広間 134 歩 (約 40 秒)",
    standing_now=35694,
)

# ---- 雷翼 (StormBackLayout 版 6。体は x -232..232, z -136..140。北が頭) ----
TITANS["storm"] = dict(
    label="雷翼",
    bounds=(-236, 236, -140, 145),
    body=[(0, -100), (-8, -92), (-8, -50), (-14, -26), (-18, 10), (-14, 46), (-34, 112), (0, 118), (34, 112), (14, 46),
          (18, 10), (14, -26), (8, -50), (8, -92)],
    extra_body=[
        [(-14, -30), (-90, -66), (-232, -120), (-236, -96), (-200, -30), (-120, 6), (-16, 14)],   # 左の翼
        [(14, -30), (90, -66), (232, -120), (236, -96), (200, -30), (120, 6), (16, 14)],           # 右の翼
    ],
    k=1.5,
    layout="stack",
    rooms=[
        ("尾の間", "rest", R(-10, 10, 20, 32), 6),
        ("風の広間", "hub", R(-14, 14, -18, 10), 19),
        ("風切りの骨 (西)", "trial", R(-115, -31, -24, -10), 14),
        ("雷の骨 (東)", "trial", R(31, 115, -24, -10), 10),
        ("左手首の瘤", "trial", C(-128, -17, 12), 15),
        ("雷獣の巣", "boss", C(128, -17, 13), 15),
        ("首の回廊", "corr", R(-2, 2, -43, -19), 4),
        ("首の前室", "rest", R(-4, 4, -56, -44), 5),
        ("眼の間", "trial", R(-8, 8, -86, -70), 10),
        ("嘴の小部屋", "rest", R(-3, 3, -95, -89), 4),
        ("左の肩の門", "corr", R(-22, -15, -7, -5), 5),
        ("右の肩の門", "corr", R(15, 22, -7, -5), 5),
        ("竜骨の前室", "rest", R(-3, 3, -36, -31), 4),
        ("竜骨の間", "heart", C(0, -14, 15), 12),
    ],
    heart_name="竜骨の間",
    new=[
        ("第二の気嚢", "man", R(-178, -120, -12, 8), 16),
        ("四本目の柱の間", "man", R(120, 178, -12, 8), 14),
        ("第二の鏡の間", "man", R(16, 50, -118, -90), 10),
        ("風の祠", "man", R(8, 24, -62, -48), 6),
        ("風見の塔", "opt", R(-46, -26, 6, 28), 60),
        ("避雷の塔", "opt", R(26, 46, 6, 28), 60),
        ("尾羽の外廊", "opt", R(-34, 34, 126, 180), 8),
        ("翼の外の道 (西)", "opt", R(-190, -92, -84, -64), 6),
        ("翼の外の道 (東)", "opt", R(92, 190, -84, -64), 6),
    ],
    scale_override={"竜骨の間": 1.3, "風の広間": 1.9, "左の肩の門": None, "右の肩の門": None, "首の回廊": None,
                    "風切りの骨 (西)": "bone", "雷の骨 (東)": "bone"},
    corridor_width=6,
    time_now="30〜40 分 (21 の見込み)",
    walk_now="到着→風の広間 96 歩 (約 30 秒)",
    standing_now=33649,
)

# ---- 余燼 (EmberBackLayout 版 6。体の箱 x -100..100, z -180..226。北が頭) ----
TITANS["ember"] = dict(
    label="余燼",
    bounds=(-122, 122, -182, 230),
    body=_superellipse(0, 20, 52, 150, 2.6) ,
    extra_body=[
        [(-14, -170), (-30, -150), (-34, -120), (-26, -92), (26, -92), (34, -120), (30, -150), (14, -170)],  # 頭と顎
        [(-30, 150), (-48, 180), (-80, 214), (-96, 226), (-100, 214), (-64, 180), (-20, 140)],               # 尾 (-X へ曲がる)
        [(-52, -50), (-92, -66), (-104, -40), (-62, -22)], [(52, -50), (92, -66), (104, -40), (62, -22)],     # 前脚
        [(-52, 96), (-92, 82), (-106, 110), (-60, 124)], [(52, 96), (92, 82), (106, 110), (60, 124)],          # 後ろ脚
    ],
    k=1.5,
    layout="side",
    rooms=[
        ("口の中 (到着)", "rest", R(-7, 7, -178, -134), 5),
        ("喉の坂", "corr", R(-3, 2, -90, -50), 17),
        ("炉心の広間", "hub", C(0, -34, 14), 18),
        ("灰の道 (西)", "trial", R(-37, -13, -8, 56), 22),
        ("鍛えの間 (東)", "trial", R(13, 39, -8, 28), 16),
        ("鍛え手の間", "boss", C(26, 45, 13), 15),
        ("火守りの谷 (南)", "trial", R(-10, 10, -6, 62), 31),
        ("前室", "rest", R(-4, 4, -29, -23), 6),
        ("熾の底", "heart", C(0, -4, 16), 18),
    ],
    heart_name="熾の底",
    new=[
        ("第二の湖", "man", R(-62, -22, 98, 130), 22),
        ("火の鍵の二つ目", "man", R(22, 62, 46, 62), 14),
        ("火床 II", "man", R(-18, 18, 104, 130), 20),
        ("肺の関 (喉の坂)", "man", R(-14, 14, -150, -118), 12),
        ("左の肺 (息の洞)", "opt", R(-60, -22, -232, -100), 14),
        ("右の肺 (熱の洞)", "opt", R(22, 60, -232, -100), 14),
        ("尾の炉", "opt", R(-152, -104, 316, 362), 12),
        ("炉の祠", "man", R(8, 24, -44, -32), 6),
    ],
    scale_override={"熾の底": 1.3, "炉心の広間": 1.9, "喉の坂": None},
    corridor_width=6,
    time_now="35〜45 分 (21 の見込み)",
    walk_now="到着→炉心の広間 (喉の坂 約 40 歩) 約 30 秒",
    standing_now=None,
)

COL = {
    "hub": (91, 141, 239), "trial": (58, 166, 117), "boss": (217, 83, 79), "heart": (176, 42, 55),
    "corr": (141, 153, 166), "rest": (224, 184, 76), "lower": (122, 91, 209),
    "opt": (240, 140, 46), "sealed": (190, 192, 196), "man": (242, 194, 48), "man_low": (170, 130, 220),
}
BODY_FILL = (214, 220, 226)
BODY_LINE = (107, 119, 130)
BG = (244, 245, 247)
INK = (27, 33, 41)
MUTED = (91, 102, 114)


def font(size):
    try:
        return ImageFont.truetype(FONT, size, index=0)
    except Exception:
        return ImageFont.load_default()


def area(shape):
    if shape[0] == "r":
        return (shape[2] - shape[1] + 1) * (shape[4] - shape[3] + 1)
    return math.pi * shape[3] ** 2


def dims(shape):
    if shape[0] == "r":
        return shape[2] - shape[1] + 1, shape[4] - shape[3] + 1
    return 2 * shape[3], 2 * shape[3]


def scaled(name, kind, shape, h, t, s):
    """今の部屋を拡大案の部屋へ。位置は s 倍、大きさは部屋ごとの倍率 m (標準は s)。"""
    ov = t["scale_override"].get(name, "std")
    m = s if ov == "std" else ov
    if ov == "bone":  # 骨の回廊: 長さ s 倍、幅は 24 まで広げる (今 15)
        x1, x2, z1, z2 = shape[1:]
        cx, cz = (x1 + x2) / 2 * s, (z1 + z2) / 2 * s
        hl = (x2 - x1 + 1) / 2 * s
        hw = 12
        return (name, kind, R(round(cx - hl), round(cx + hl), round(cz - hw), round(cz + hw)), round(max(h * 1.2, 16)))
    if m is None:  # 通路: 長さ s 倍、幅は corridor_width
        if shape[0] == "r":
            x1, x2, z1, z2 = shape[1:]
            cx, cz = (x1 + x2) / 2 * s, (z1 + z2) / 2 * s
            w, l = x2 - x1 + 1, z2 - z1 + 1
            if w >= l:  # 横長
                hl, hw = w / 2 * s, t["corridor_width"] / 2
                return (name, kind, R(round(cx - hl), round(cx + hl), round(cz - hw), round(cz + hw)), max(6, round(h * 1.5)))
            hl, hw = l / 2 * s, t["corridor_width"] / 2
            return (name, kind, R(round(cx - hw), round(cx + hw), round(cz - hl), round(cz + hl)), max(6, round(h * 1.5)))
        m = s
    if shape[0] == "c":
        return (name, kind, C(round(shape[1] * s), round(shape[2] * s), round(shape[3] * m)), round(h * 1.35))
    x1, x2, z1, z2 = shape[1:]
    cx, cz = (x1 + x2) / 2 * s, (z1 + z2) / 2 * s
    hx, hz = (x2 - x1 + 1) / 2 * m, (z2 - z1 + 1) / 2 * m
    return (name, kind, R(round(cx - hx), round(cx + hx), round(cz - hz), round(cz + hz)), round(h * 1.35))


def proposed(t, s):
    out = [scaled(n, "opt" if k == "sealed" else k, sh, h, t, s) for (n, k, sh, h) in t["rooms"]]
    return out, list(t["new"])


def draw_shape(d, shape, fill, line, X, Y, k, dashed=False):
    if shape[0] == "r":
        x1, x2, z1, z2 = shape[1:]
        box = [X(x1), Y(z1), X(x2 + 1), Y(z2 + 1)]
        d.rectangle(box, fill=fill, outline=line, width=1)
    else:
        cx, cz, r = shape[1:]
        d.ellipse([X(cx - r), Y(cz - r), X(cx + r), Y(cz + r)], fill=fill, outline=line, width=1)


def panel(t, rooms, new, s_body, title, k, bounds, size_px, sub):
    """1 枚の上から見た図。s_body は体の倍率 (今は 1)。"""
    x0, x1, z0, z1 = bounds
    W, H = size_px
    img = Image.new("RGB", (W, H), BG)
    d = ImageDraw.Draw(img)
    X = lambda x: (x - x0) * k
    Y = lambda z: (z - z0) * k + 28
    f10, f12, f16 = font(11), font(12), font(17)
    d.text((8, 5), title, fill=INK, font=f16)
    d.text((W - 8, 8), sub, fill=MUTED, font=f12, anchor="ra")
    polys = [t["body"]] + t["extra_body"]
    for p in polys:
        pts = [(X(x * s_body), Y(z * s_body)) for (x, z) in p]
        d.polygon(pts, fill=BODY_FILL, outline=BODY_LINE)
    # 格子 (50 ブロック)
    gx = math.ceil(x0 / 50) * 50
    while gx <= x1:
        d.line([X(gx), 28, X(gx), H], fill=(226, 229, 233), width=1)
        gx += 50
    gz = math.ceil(z0 / 50) * 50
    while gz <= z1:
        d.line([0, Y(gz), W, Y(gz)], fill=(226, 229, 233), width=1)
        gz += 50
    for p in polys:  # 体を格子の上に描き直す
        pts = [(X(x * s_body), Y(z * s_body)) for (x, z) in p]
        d.polygon(pts, fill=BODY_FILL, outline=BODY_LINE)
    order = {"lower": 0, "man_low": 0, "sealed": 0}
    allr = [(n, kd, sh, h, False) for (n, kd, sh, h) in rooms] + [(n, kd, sh, h, True) for (n, kd, sh, h) in new]
    allr.sort(key=lambda r: order.get(r[1], 1))
    for (n, kd, sh, h, isnew) in allr:
        col = COL.get(kd, (120, 120, 120))
        fill = tuple(int(c * 0.78 + 255 * 0.22) for c in col)
        draw_shape(d, sh, fill, col if not isnew else tuple(int(c * 0.6) for c in col), X, Y, k)
    for (n, kd, sh, h, isnew) in allr:
        w, l = dims(sh)
        if min(w, l) * k < 26 and max(w, l) * k < 60:
            continue
        cx = X((sh[1] + sh[2] + 1) / 2) if sh[0] == "r" else X(sh[1])
        cz = Y((sh[3] + sh[4] + 1) / 2) if sh[0] == "r" else Y(sh[2])
        txt = n
        tw = d.textlength(txt, font=f10)
        if tw > max(w, l) * k + 4 and max(w, l) * k < 90:
            continue
        d.text((cx, cz), txt, fill=INK, font=f10, anchor="mm")
    # 縮尺 (50 ブロック)
    bx = 10
    by = H - 16
    d.line([bx, by, bx + 50 * k, by], fill=INK, width=3)
    d.text((bx + 50 * k + 6, by), "50 ブロック", fill=INK, font=f12, anchor="lm")
    d.rectangle([0, 0, W - 1, H - 1], outline=(201, 208, 214))
    return img


def poly_area(p):
    a = 0.0
    for i in range(len(p)):
        x1, z1 = p[i]
        x2, z2 = p[(i + 1) % len(p)]
        a += x1 * z2 - x2 * z1
    return abs(a) / 2


def summarize(t, s):
    cur = t["rooms"]
    prop, new = proposed(t, s)
    def tot(lst, exclude=("lower", "sealed", "opt")):
        return sum(area(sh) for (n, kd, sh, h) in lst if kd not in exclude)
    def tot_new(lst, kinds):
        return sum(area(sh) for (n, kd, sh, h) in lst if kd in kinds)
    hn = t["heart_name"]
    heart_cur = next(area(sh) for (n, kd, sh, h) in cur if n == hn)
    heart_prop = next(area(sh) for (n, kd, sh, h) in prop if n == hn)
    def room_rows(lst):
        return [dict(name=n, kind=kd, w=dims(sh)[0], l=dims(sh)[1], area=round(area(sh)), h=h) for (n, kd, sh, h) in lst]
    big_cur = max((area(sh), n) for (n, kd, sh, h) in cur if kd in ("trial", "boss"))
    big_prop = max((area(sh), n) for (n, kd, sh, h) in prop if kd in ("trial", "boss"))
    return dict(
        label=t["label"], scale=s,
        cur=room_rows(cur), prop=room_rows(prop), new=room_rows(new),
        cur_total=round(tot(cur)), prop_total=round(tot(prop)),
        new_man=round(tot_new(new, ("man", "man_low"))), new_opt=round(tot_new(new, ("opt",)) + tot_new(prop, ("opt",))),
        heart_cur=round(heart_cur), heart_prop=round(heart_prop),
        heart_per_player_cur=round(heart_cur / 6), heart_per_player_prop=round(heart_prop / 6),
        big_cur=(round(big_cur[0]), big_cur[1]), big_prop=(round(big_prop[0]), big_prop[1]),
        body_area=round(sum(poly_area(p) for p in [t["body"]] + t["extra_body"])),
        time_now=t["time_now"], walk_now=t["walk_now"], standing_now=t["standing_now"],
    )


def build_compare(key, t, s):
    k = t["k"]
    x0, x1, z0, z1 = t["bounds"]
    bounds_p = (x0 * s, x1 * s, z0 * s, z1 * s)
    wc, hc = (x1 - x0) * k, (z1 - z0) * k + 34
    wp, hp = (x1 * s - x0 * s) * k, (z1 * s - z0 * s) * k + 34
    prop, new = proposed(t, s)
    a = panel(t, t["rooms"], [], 1.0, f"{t['label']}の背: いま", k, t["bounds"], (int(wc), int(hc)), f"体 {x1 - x0}×{z1 - z0} ブロック")
    b = panel(t, prop, new, s, f"{t['label']}の背: 拡大案 (×{s:g})", k, bounds_p, (int(wp), int(hp)),
              f"体 {round((x1 - x0) * s)}×{round((z1 - z0) * s)} ブロック")
    if t["layout"] == "side":
        W = int(wc + wp + 36)
        H = int(max(hc, hp) + 70)
        img = Image.new("RGB", (W, H), BG)
        img.paste(a, (12, 12))
        img.paste(b, (int(wc) + 24, 12))
        ly = H - 52
    else:
        W = int(max(wc, wp) + 24)
        H = int(hc + hp + 24 + 70)
        img = Image.new("RGB", (W, H), BG)
        img.paste(a, (12, 12))
        img.paste(b, (12, int(hc) + 24))
        ly = H - 52
    d = ImageDraw.Draw(img)
    f12 = font(12)
    names = [("hub", "広間"), ("trial", "試練の部屋"), ("boss", "ボス・広間戦"), ("heart", "心臓"), ("corr", "通路"),
             ("rest", "前室・休み場"), ("lower", "下の層"), ("man", "新しい部屋 (必須の道)"), ("opt", "新しい部屋 (任意の翼)")]
    x = 14
    for kd, nm in names:
        d.rectangle([x, ly + 6, x + 12, ly + 18], fill=COL[kd], outline=(80, 80, 80))
        d.text((x + 17, ly + 12), nm, fill=INK, font=f12, anchor="lm")
        x += 24 + d.textlength(nm, font=f12) + 10
    d.text((14, ly + 32), "拡大案は概念図 (部屋の位置と形は実装担当が決める)。2 枚とも同じ縮尺。格子は 50 ブロック。", fill=MUTED, font=f12, anchor="lm")
    path = os.path.join(OUT, f"compare_{key}.png")
    img.save(path)
    return path


def build_overview(s):
    cell = 520
    img = Image.new("RGB", (cell * 2 + 36, cell * 2 + 80), BG)
    d = ImageDraw.Draw(img)
    f12, f16 = font(12), font(17)
    k = 0.52
    keys = ["whale", "tide", "storm", "ember"]
    for i, key in enumerate(keys):
        t = TITANS[key]
        ox, oy = 12 + (i % 2) * (cell + 12), 12 + (i // 2) * (cell + 12)
        d.rectangle([ox, oy, ox + cell, oy + cell], fill=BG, outline=(201, 208, 214))
        d.text((ox + 8, oy + 5), t["label"], fill=INK, font=f16)
        x0, x1, z0, z1 = t["bounds"]
        cx, cz = (x0 + x1) / 2 * s, (z0 + z1) / 2 * s
        X = lambda x: ox + cell / 2 + (x - cx) * k
        Y = lambda z: oy + cell / 2 + 10 + (z - cz) * k
        polys = [t["body"]] + t["extra_body"]
        for p in polys:
            d.polygon([(X(x * s), Y(z * s)) for (x, z) in p], fill=(246, 214, 168), outline=(214, 132, 40))
        for p in polys:
            d.polygon([(X(x), Y(z)) for (x, z) in p], fill=(170, 180, 190), outline=(80, 90, 100))
        d.text((ox + cell - 8, oy + 8), f"体の長さ {round((z1 - z0))} → {round((z1 - z0) * s)}", fill=MUTED, font=f12, anchor="ra")
        d.line([ox + 10, oy + cell - 14, ox + 10 + 100 * k, oy + cell - 14], fill=INK, width=3)
        d.text((ox + 16 + 100 * k, oy + cell - 14), "100 ブロック", fill=INK, font=f12, anchor="lm")
    y = cell * 2 + 34
    d.rectangle([14, y + 4, 26, y + 16], fill=(170, 180, 190), outline=(80, 90, 100))
    d.text((32, y + 10), "いま", fill=INK, font=f12, anchor="lm")
    d.rectangle([80, y + 4, 92, y + 16], fill=(246, 214, 168), outline=(214, 132, 40))
    d.text((98, y + 10), f"拡大案 (×{s:g})。四つとも同じ縮尺、中心を合わせて重ねた", fill=INK, font=f12, anchor="lm")
    img.save(os.path.join(OUT, "overview_all.png"))


def build_party_rooms(s):
    """部屋に 6 人が入ったときの感覚。人 = 1 ブロックの点、足踏みの円 (半径 3.5) を 6 人ぶん並べる。"""
    t = TITANS["whale"]
    cases = [("第一の広間 (戦い)", (13, 30), (round(13 * s), round(30 * s))),
             ("番兵長の広間 (ボス)", (9, 31), (round(9 * s), round(31 * s))),
             ("雷翼 眼の間 (鏡の謎)", (17, 17), (round(17 * s), round(17 * s))),
             ("潮殻 群れの干潟", (49, 33), (round(49 * s), round(33 * s)))]
    k = 7
    f12, f16 = font(12), font(16)
    pad = 14
    # 2 行 4 列の絵: いま / 拡大案 を縦に並べる
    colw = [max(max(a[0], b[0]) * k, 190) + pad * 2 for (_, a, b) in cases]
    img = Image.new("RGB", (int(sum(colw)) + 12, int(max(max(a[1], b[1]) for (_, a, b) in cases) * k * 2 + 130)), BG)
    d = ImageDraw.Draw(img)
    x = 6
    import random
    rnd = random.Random(7)
    for ci, (nm, a, b) in enumerate(cases):
        y = 10
        d.text((x + pad, y), nm, fill=INK, font=f16)
        y += 26
        for lab, (w, l) in (("いま", a), ("拡大案", b)):
            d.text((x + pad, y), f"{lab}  {w}×{l} = {w * l} マス", fill=MUTED, font=f12)
            y += 18
            d.rectangle([x + pad, y, x + pad + w * k, y + l * k], fill=(222, 230, 224), outline=(58, 130, 98), width=2)
            for gx in range(0, w + 1, 5):
                d.line([x + pad + gx * k, y, x + pad + gx * k, y + l * k], fill=(200, 210, 204))
            for gz in range(0, l + 1, 5):
                d.line([x + pad, y + gz * k, x + pad + w * k, y + gz * k], fill=(200, 210, 204))
            # 6 人 (青) と、足踏みの円 (半径 3.5) 3 つ (赤) と、敵の点 (灰)
            for i in range(3):
                cx, cz = rnd.uniform(5, w - 5), rnd.uniform(5, l - 5)
                r = 3.5
                d.ellipse([x + pad + (cx - r) * k, y + (cz - r) * k, x + pad + (cx + r) * k, y + (cz + r) * k],
                          fill=(244, 200, 196), outline=(217, 83, 79))
            for i in range(6):
                cx, cz = rnd.uniform(1, w - 1), rnd.uniform(1, l - 1)
                d.ellipse([x + pad + (cx - 0.5) * k, y + (cz - 0.5) * k, x + pad + (cx + 0.5) * k, y + (cz + 0.5) * k], fill=(40, 90, 200))
            y += l * k + 14
        x += colw[ci]
    d.text((10, img.height - 22), "青 = 6 人 (1 ブロック)。赤い円 = 足踏みの予告 (半径 3.5) を 3 つ。格子は 5 ブロック。", fill=MUTED, font=f12)
    img.save(os.path.join(OUT, "party_rooms.png"))


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--scale", type=float, default=1.6)
    a = ap.parse_args()
    os.makedirs(OUT, exist_ok=True)
    s = a.scale
    metrics = {}
    for key, t in TITANS.items():
        metrics[key] = summarize(t, s)
        print("wrote", build_compare(key, t, s))
    build_overview(s)
    build_party_rooms(s)
    with open(os.path.join(OUT, "metrics.json"), "w", encoding="utf-8") as f:
        json.dump(metrics, f, ensure_ascii=False, indent=1)
    for key, m in metrics.items():
        print(f"   body footprint {m['body_area']} -> {round(m['body_area'] * s * s)}; rooms share now {m['cur_total'] / m['body_area']:.1%}")
        print(f"== {m['label']}: rooms {len(m['cur'])} cur_total {m['cur_total']} -> prop_total {m['prop_total']} "
              f"(+new man {m['new_man']} opt {m['new_opt']}), heart {m['heart_cur']} -> {m['heart_prop']}, "
              f"biggest trial/boss {m['big_cur']} -> {m['big_prop']}")
        for r in m["cur"]:
            p = next(x for x in m["prop"] if x["name"] == r["name"])
            print(f"   {r['name']:<12} {r['kind']:<6} {r['w']:>3}x{r['l']:<3} {r['area']:>5} h{r['h']:<3} -> {p['w']:>3}x{p['l']:<3} {p['area']:>5} h{p['h']}")
        for r in m["new"]:
            print(f"   [new] {r['name']:<14} {r['kind']:<8} {r['w']:>3}x{r['l']:<3} {r['area']:>5} h{r['h']}")


if __name__ == "__main__":
    sys.exit(main())
