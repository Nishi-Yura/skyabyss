"""第三章の小物 32x32 のドット絵 3 案ずつ (素材 4・砥石・団子・油・小旗) と、効き目の絵 (18x18) 2 枚とシート。
雷翼の地上の色の言葉 (峰の灰・焦げ・銅・白青の羽・雷の青白) で、二章の icons_ch2.py と同じ描き方 (暗い輪郭 1 画素、左上の光、限られた色)。

実行: python -I tools/rough/lowpoly/ch3/icons_ch3.py
出力: docs/review/ch3_roughs/items/icons/<品>_{a,b,c}_32.png、thunder_whetted_18.png・grounded_18.png
      docs/review/ch3_roughs/items/icons_ch3.png (4x と 1x を暗い枠・明るい背景で)
入れ方: tools/install/install_ch3_gear_assets.py (選んだ案を assets へ、選ばなかった案を tools/sources/<品>/ へ)
"""
from __future__ import annotations

import importlib.util
import math
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

HERE = Path(__file__).resolve().parent
_spec = importlib.util.spec_from_file_location("icons_ch2", HERE.parent / "ch2" / "icons_ch2.py")
I2 = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(I2)
canvas, poly, rect, px, line, ellipse, sphere, finish, font, c = I2.canvas, I2.poly, I2.rect, I2.px, I2.line, I2.ellipse, I2.sphere, I2.finish, I2.font, I2.c

ROOT = HERE.parents[3]
OUT = ROOT / "docs/review/ch3_roughs/items"
ICON_DIR = OUT / "icons"

# ------------------------------------------------------------------ 色
ROCK = [(48, 54, 64), (76, 84, 96), (108, 118, 130), (146, 156, 166), (186, 194, 202)]
CHAR = [(22, 20, 24), (40, 36, 38), (62, 56, 56), (90, 82, 78), (120, 110, 102)]
COPPER = [(84, 42, 20), (136, 72, 32), (184, 104, 46), (226, 146, 72), (252, 196, 120)]
VERD = [(44, 82, 74), (66, 118, 100), (98, 150, 126), (140, 186, 160)]
FEATHER = [(62, 76, 100), (108, 124, 150), (158, 174, 198), (204, 216, 234), (240, 246, 252)]
DOWN = [(150, 150, 164), (190, 190, 202), (222, 222, 232), (240, 240, 248), (255, 255, 255)]
BOLT = [(90, 150, 220), (150, 214, 255), (210, 240, 255), (250, 252, 255)]
DOUGH = [(150, 120, 80), (190, 160, 112), (220, 196, 148), (240, 224, 186)]
HERB = [(40, 70, 40), (62, 98, 54), (92, 130, 70)]
GLASS = [(70, 96, 120), (120, 156, 180), (180, 210, 226), (230, 244, 250)]
OIL = [(84, 60, 20), (140, 104, 30), (196, 156, 52), (236, 206, 110)]
CLOTH = [(120, 40, 36), (168, 64, 52), (206, 96, 72), (236, 140, 110)]
WOOD = [(52, 40, 32), (82, 64, 50), (116, 92, 70), (150, 124, 96)]
STONE = [(40, 46, 60), (64, 72, 92), (92, 102, 124), (130, 142, 162)]


def feather_shape(im, x0, y0, x1, y1, w, ramp, vane_dark=None):
    """斜めの羽 (軸 + 両側の羽弁)"""
    d = ImageDraw.Draw(im)
    n = 14
    for i in range(n + 1):
        t = i / n
        x = x0 + (x1 - x0) * t
        y = y0 + (y1 - y0) * t
        ww = w * math.sin(math.pi * min(1, t * 1.15)) if t < 0.95 else 0.5
        nx, ny = -(y1 - y0), (x1 - x0)
        L = math.hypot(nx, ny)
        nx, ny = nx / L, ny / L
        d.line([(x - nx * ww, y - ny * ww), (x, y)], fill=c(ramp[2] if i % 3 else ramp[1]), width=2)
        d.line([(x, y), (x + nx * ww, y + ny * ww)], fill=c(ramp[3] if i % 3 else ramp[2]), width=2)
    line(im, [(x0, y0), (x1, y1)], ramp[4] if vane_dark is None else vane_dark)


# ------------------------------------------------------------------ 焦げ羽
def feather_a():
    """白青の羽の先が黒く焦げ、焦げ目に青白い筋"""
    im = canvas()
    feather_shape(im, 6, 28, 25, 4, 5.0, FEATHER)
    for (x, y) in ((20, 6), (21, 8), (23, 7), (19, 9), (22, 5), (24, 9)):
        px(im, x, y, CHAR[1])
    for (x, y) in ((18, 10), (20, 11)):
        px(im, x, y, BOLT[2])
    return finish(im)


def feather_b():
    """黒く焼けた羽一枚。軸に沿って青い焦げ筋"""
    im = canvas()
    feather_shape(im, 5, 27, 26, 5, 5.5, CHAR, vane_dark=BOLT[1])
    px(im, 22, 8, BOLT[3])
    return finish(im)


def feather_c():
    """二枚の羽 (灰と焦げ) を銅の糸で束ねる"""
    im = canvas()
    feather_shape(im, 8, 28, 20, 3, 4.0, CHAR)
    feather_shape(im, 12, 29, 27, 6, 4.0, FEATHER)
    rect(im, 11, 22, 16, 23, COPPER[3])
    return finish(im)


# ------------------------------------------------------------------ 帯電綿
def cotton_a():
    """白い綿の玉から細い毛がはね、先に火花"""
    im = canvas()
    for (x, y, r) in ((11, 18, 7), (20, 17, 7), (16, 12, 6), (16, 21, 6)):
        sphere(im, x, y, r, DOWN, spec=False)
    for a in range(0, 360, 45):
        x = 16 + math.cos(math.radians(a)) * 12
        y = 16 + math.sin(math.radians(a)) * 11
        line(im, [(16 + math.cos(math.radians(a)) * 9, 16 + math.sin(math.radians(a)) * 8), (x, y)], DOWN[2])
        if a % 90 == 0:
            px(im, x, y, BOLT[2])
    return finish(im)


def cotton_b():
    """ふわりとした綿の房を三つ寄せ、間に青白い小さな稲妻"""
    im = canvas()
    for (x, y, r) in ((9, 20, 6), (22, 20, 6), (16, 11, 6.5)):
        sphere(im, x, y, r, DOWN, spec=True)
    line(im, [(14, 18), (17, 21), (15, 23), (18, 27)], BOLT[2])
    return finish(im)


def cotton_c():
    """綿を巻いた小さな銅の糸巻き (帯電綿を集めた束)"""
    im = canvas()
    rect(im, 13, 5, 18, 27, COPPER[2])
    for (x, y, r) in ((15.5, 12, 7), (15.5, 20, 7)):
        sphere(im, x, y, r, DOWN, spec=False)
    rect(im, 9, 4, 22, 6, COPPER[1])
    rect(im, 9, 26, 22, 28, COPPER[1])
    px(im, 21, 13, BOLT[2])
    px(im, 10, 19, BOLT[2])
    return finish(im)


# ------------------------------------------------------------------ 銅線
def wire_a():
    """丸く巻いた銅線のかせ"""
    im = canvas()
    d = ImageDraw.Draw(im)
    for k, col in enumerate((COPPER[1], COPPER[3], COPPER[2], COPPER[4])):
        d.ellipse((4 + k, 6 + k, 27 - k, 26 - k), outline=c(col), width=2)
    line(im, [(25, 18), (30, 24)], COPPER[3])
    return finish(im)


def wire_b():
    """木の芯に巻いた銅線 (端が一本はねる)"""
    im = canvas()
    rect(im, 8, 6, 24, 9, WOOD[2])
    rect(im, 8, 23, 24, 26, WOOD[1])
    rect(im, 10, 9, 22, 23, COPPER[2])
    for y in range(10, 23, 2):
        line(im, [(10, y), (22, y)], COPPER[4] if y % 4 == 0 else COPPER[3])
    line(im, [(22, 12), (28, 8), (29, 3)], COPPER[3])
    return finish(im)


def wire_c():
    """焼けた荷から拾ったよじれた銅線の束 (緑青の斑)"""
    im = canvas()
    for k, (a, b) in enumerate((((4, 22), (27, 9)), ((5, 25), (28, 13)), ((7, 27), (26, 18)))):
        pts = []
        for i in range(9):
            t = i / 8
            pts.append((a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t + (1.5 if i % 2 else -1.5)))
        line(im, pts, COPPER[2 + (k % 2)], 2)
    for (x, y) in ((10, 20), (18, 17), (22, 13)):
        px(im, x, y, VERD[2])
    return finish(im)


# ------------------------------------------------------------------ 雷の護り糸
def ward_a():
    """銅の糸のかせに、雷の青白の玉が一つ"""
    im = canvas()
    d = ImageDraw.Draw(im)
    for k, col in enumerate((COPPER[1], COPPER[3], DOWN[1], COPPER[4])):
        d.ellipse((5 + k, 6 + k, 26 - k, 24 - k), outline=c(col), width=2)
    sphere(im, 21, 26, 3.4, BOLT, spec=True)
    line(im, [(16, 24), (19, 26)], COPPER[3])
    return finish(im)


def ward_b():
    """護り結び (白い糸) の結び目に青白い光、房が稲妻の形に垂れる"""
    im = canvas()
    d = ImageDraw.Draw(im)
    d.ellipse((6, 3, 26, 21), outline=c(DOWN[2]), width=2)
    ellipse(im, (12, 16, 20, 24), COPPER[2])
    ellipse(im, (14, 18, 18, 22), BOLT[2])
    line(im, [(14, 24), (12, 27), (15, 28), (13, 31)], BOLT[1])
    line(im, [(18, 24), (20, 27), (17, 28), (19, 31)], DOWN[2])
    return finish(im)


def ward_c():
    """羽の軸に巻いた細い糸 (焦げ羽と銅の糸)"""
    im = canvas()
    feather_shape(im, 7, 28, 24, 4, 4.0, FEATHER)
    for y in range(12, 22, 2):
        x = 7 + (24 - 7) * (28 - y) / 24
        line(im, [(x - 3, y), (x + 3, y - 1)], COPPER[3])
    px(im, 18, 11, BOLT[3])
    return finish(im)


# ------------------------------------------------------------------ 雷の砥石
def whet_a():
    """灰青の石に銅の筋、先で火花"""
    im = canvas()
    poly(im, [(4, 22), (22, 6), (28, 11), (10, 27)], STONE[2])
    poly(im, [(22, 6), (28, 11), (27, 13), (21, 8)], STONE[3])
    poly(im, [(10, 27), (28, 11), (28, 13), (11, 29)], STONE[0])
    line(im, [(8, 23), (24, 9)], COPPER[3])
    for (x, y) in ((27, 5), (29, 8), (25, 3)):
        px(im, x, y, BOLT[2])
    return finish(im)


def whet_b():
    """雷に焼かれて黒い石。稲妻の形のひびが青白く光る"""
    im = canvas()
    poly(im, [(4, 22), (22, 6), (28, 11), (10, 27)], CHAR[2])
    poly(im, [(22, 6), (28, 11), (27, 13), (21, 8)], CHAR[3])
    poly(im, [(10, 27), (28, 11), (28, 13), (11, 29)], CHAR[0])
    line(im, [(8, 22), (13, 19), (12, 17), (18, 14), (17, 12), (23, 9)], BOLT[2])
    return finish(im)


def whet_c():
    """銅を巻いた石 (握りの銅線と、黒い刃の面)"""
    im = canvas()
    poly(im, [(4, 22), (22, 6), (28, 11), (10, 27)], STONE[1])
    poly(im, [(22, 6), (28, 11), (27, 13), (21, 8)], STONE[3])
    for k in range(4):
        x = 7 + k * 2
        line(im, [(x, 22 - k * 2 + 3), (x + 4, 26 - k * 2 + 1)], COPPER[3 if k % 2 else 2], 2)
    px(im, 24, 8, BOLT[3])
    return finish(im)


# ------------------------------------------------------------------ 風干しの団子
def dumpling_a():
    """串に団子が三つ (薄い焼き目)"""
    im = canvas()
    line(im, [(4, 29), (28, 3)], WOOD[3], 2)
    for (x, y) in ((10, 22), (16, 16), (22, 10)):
        sphere(im, x, y, 4.6, DOUGH, spec=True)
        px(im, x + 1, y + 2, DOUGH[0])
    return finish(im)


def dumpling_b():
    """笹の葉に包んだ団子 (風に干す紐つき)"""
    im = canvas()
    poly(im, [(4, 20), (16, 8), (28, 20), (16, 28)], HERB[1])
    poly(im, [(16, 8), (28, 20), (16, 28)], HERB[0])
    sphere(im, 16, 18, 5.5, DOUGH, spec=True)
    line(im, [(16, 8), (16, 2)], CLOTH[2])
    line(im, [(13, 3), (19, 3)], CLOTH[2])
    return finish(im)


def dumpling_c():
    """白い羽の飾りを挿した干し団子 (落ちるのがゆっくりになる)"""
    im = canvas()
    for (x, y) in ((11, 21), (21, 21), (16, 14)):
        sphere(im, x, y, 5.5, DOUGH, spec=True)
    feather_shape(im, 16, 12, 24, 1, 2.5, FEATHER)
    return finish(im)


# ------------------------------------------------------------------ 避雷の油
def oil_a():
    """銅の口金の硝子の小瓶に、琥珀の油"""
    im = canvas()
    rect(im, 13, 3, 18, 7, COPPER[2])
    poly(im, [(11, 8), (20, 8), (25, 16), (25, 27), (6, 27), (6, 16)], GLASS[1])
    poly(im, [(7, 17), (24, 17), (24, 26), (7, 26)], OIL[2])
    rect(im, 8, 18, 9, 24, OIL[3])
    px(im, 9, 11, GLASS[3])
    line(im, [(16, 20), (14, 22), (17, 22), (15, 25)], BOLT[2])
    return finish(im)


def oil_b():
    """革の栓の黒い壺 (銅の帯に避雷針の印)"""
    im = canvas()
    ellipse(im, (6, 9, 26, 29), CHAR[2])
    ellipse(im, (8, 11, 20, 23), CHAR[3])
    rect(im, 12, 4, 19, 9, WOOD[2])
    rect(im, 6, 17, 26, 19, COPPER[3])
    line(im, [(16, 13), (16, 24)], COPPER[4])
    px(im, 16, 12, BOLT[2])
    return finish(im)


def oil_c():
    """油を含ませた布の包みと、垂れる琥珀の一滴"""
    im = canvas()
    poly(im, [(5, 14), (16, 6), (27, 14), (24, 26), (8, 26)], CLOTH[1])
    poly(im, [(16, 6), (27, 14), (24, 26), (16, 20)], CLOTH[0])
    line(im, [(8, 15), (24, 15)], COPPER[3])
    ellipse(im, (14, 26, 18, 31), OIL[2])
    px(im, 15, 27, OIL[3])
    return finish(im)


# ------------------------------------------------------------------ 風読みの小旗
def streamer_a():
    """銅の細い柄に、白い吹き流し (風下へなびく)"""
    im = canvas()
    line(im, [(7, 30), (7, 3)], COPPER[3], 2)
    poly(im, [(8, 4), (26, 7), (22, 10), (28, 13), (8, 12)], DOWN[2])
    poly(im, [(8, 9), (28, 13), (8, 12)], DOWN[1])
    px(im, 7, 2, BOLT[3])
    return finish(im)


def streamer_b():
    """緋の三角の小旗に銅線の縁、先に青白い光の糸"""
    im = canvas()
    line(im, [(6, 30), (6, 3)], WOOD[3], 2)
    poly(im, [(7, 4), (27, 10), (7, 16)], CLOTH[2])
    poly(im, [(7, 10), (27, 10), (7, 16)], CLOTH[1])
    line(im, [(7, 4), (27, 10), (7, 16)], COPPER[3])
    line(im, [(27, 10), (30, 11)], BOLT[2])
    return finish(im)


def streamer_c():
    """風見の矢羽を付けた短い竿 (羽と銅の矢)"""
    im = canvas()
    line(im, [(15, 30), (15, 9)], WOOD[3], 2)
    line(im, [(4, 9), (27, 9)], COPPER[3], 2)
    poly(im, [(27, 6), (31, 9), (27, 12)], COPPER[4])
    feather_shape(im, 3, 12, 9, 5, 2.5, FEATHER)
    px(im, 15, 8, BOLT[3])
    return finish(im)


# ------------------------------------------------------------------ 効き目 (18x18)
def eff_whetted():
    im = canvas(18)
    poly(im, [(2, 12), (12, 2), (16, 6), (6, 16)], STONE[2])
    line(im, [(4, 12), (7, 9), (6, 8), (10, 5), (9, 4), (13, 2)], BOLT[2])
    return finish(im)


def eff_grounded():
    im = canvas(18)
    rect(im, 8, 2, 9, 14, COPPER[3])
    poly(im, [(5, 14), (12, 14), (14, 16), (3, 16)], COPPER[2])
    line(im, [(3, 3), (6, 6), (4, 7), (7, 10)], BOLT[2])
    px(im, 8, 1, BOLT[3])
    return finish(im)


ITEMS = {
    "scorched_feather": (feather_a, feather_b, feather_c),
    "static_cotton": (cotton_a, cotton_b, cotton_c),
    "copper_wire": (wire_a, wire_b, wire_c),
    "thunder_ward_thread": (ward_a, ward_b, ward_c),
    "thunder_whetstone": (whet_a, whet_b, whet_c),
    "wind_dumpling": (dumpling_a, dumpling_b, dumpling_c),
    "lightning_oil": (oil_a, oil_b, oil_c),
    "wind_streamer": (streamer_a, streamer_b, streamer_c),
}
NAMES_JP = {"scorched_feather": "焦げ羽", "static_cotton": "帯電綿", "copper_wire": "銅線", "thunder_ward_thread": "雷の護り糸",
            "thunder_whetstone": "雷の砥石", "wind_dumpling": "風干しの団子", "lightning_oil": "避雷の油", "wind_streamer": "風読みの小旗"}
NOTES_JP = {
    "scorched_feather": ("先の焦げた白青の羽", "青い筋の黒い羽", "二枚を銅で束ねる"),
    "static_cotton": ("火花の毛の玉", "三つの房と稲妻", "銅の糸巻き"),
    "copper_wire": ("丸いかせ", "木の芯に巻く", "よじれた拾い物"),
    "thunder_ward_thread": ("銅のかせと光の玉", "稲妻の房の護り結び", "羽の軸に巻く"),
    "thunder_whetstone": ("灰石に銅の筋", "黒い石に光るひび", "銅を巻いた握り"),
    "wind_dumpling": ("串の三つ", "笹の包み", "羽を挿した団子"),
    "lightning_oil": ("硝子の小瓶", "黒い壺と銅の帯", "油の布の包み"),
    "wind_streamer": ("白い吹き流し", "緋の三角旗", "風見の矢羽"),
}


def sheet(icons):
    cell = 150
    W = 200 + 3 * (cell * 2 + 16)
    H = 70 + len(ITEMS) * (cell + 30)
    S = Image.new("RGB", (W, H), (24, 28, 36))
    d = ImageDraw.Draw(S)
    d.text((10, 8), "三章の小物 3 案ずつ (32x32。左 = 暗い枠の 4x と 1x、右 = 明るい背景の 4x)", font=font(18), fill=(255, 255, 255))
    d.text((10, 36), "雷翼の地上の色: 峰の灰・焦げ・銅・白青の羽 / 光る所は雷の青白", font=font(13), fill=(200, 205, 215))
    y = 70
    for name in ITEMS:
        d.text((10, y + 50), NAMES_JP[name], font=font(16), fill=(255, 232, 160))
        d.text((10, y + 74), name, font=font(11), fill=(170, 176, 188))
        for k, o in enumerate("abc"):
            ic = icons[(name, o)]
            x = 200 + k * (cell * 2 + 16)
            slot = Image.new("RGBA", (cell, cell), (46, 52, 62, 255))
            slot.alpha_composite(ic.resize((128, 128), Image.NEAREST), (4, 4))
            slot.alpha_composite(ic, (cell - 36, cell - 36))
            S.paste(slot.convert("RGB"), (x, y))
            light = Image.new("RGBA", (cell, cell), (198, 198, 198, 255))
            light.alpha_composite(ic.resize((128, 128), Image.NEAREST), (11, 11))
            S.paste(light.convert("RGB"), (x + cell + 4, y))
            d.text((x, y + cell + 4), "%s  %s" % (o.upper(), NOTES_JP[name][k]), font=font(13), fill=(220, 224, 232))
        y += cell + 30
    p = OUT / "icons_ch3.png"
    S.save(p)
    print("sheet", p, S.size)


def main():
    ICON_DIR.mkdir(parents=True, exist_ok=True)
    icons = {}
    for name, fns in ITEMS.items():
        for o, fn in zip("abc", fns):
            im = fn()
            assert im.size == (32, 32), name
            im.save(ICON_DIR / f"{name}_{o}_32.png")
            icons[(name, o)] = im
    eff_whetted().save(ICON_DIR / "thunder_whetted_18.png")
    eff_grounded().save(ICON_DIR / "grounded_18.png")
    sheet(icons)


if __name__ == "__main__":
    main()
