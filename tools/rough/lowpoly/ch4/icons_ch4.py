"""四章の小物 32x32 のドット絵 3 案ずつ (煤鉄・灰麻・灰の護り糸・煤の砥石・灰の冷やし水) とシート。
余燼の色の言葉 (灰・炭・黒石・熾の橙) で、既存のアイテム絵の作り (暗い輪郭 1 画素、左上の光、限られた色) に合わせる。描く道具は二章の icons_ch2.py のもの。

実行: python -I tools/rough/lowpoly/ch4/icons_ch4.py
出力: docs/review/ch4_roughs/items/icons/<品>_{a,b,c}_32.png、docs/review/ch4_roughs/items/icons_ch4.png
入れ方: tools/gen/ch4/gen_ch4_ground_mobs.py (選んだ案を assets へ、選ばなかった案を tools/sources/<品>/ へ)
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
OUT = ROOT / "docs/review/ch4_roughs/items"
ICON_DIR = OUT / "icons"

ASH = [(64, 60, 62), (100, 94, 92), (138, 132, 126), (176, 170, 160), (210, 204, 192)]
SOOT = [(18, 16, 20), (36, 32, 36), (58, 52, 54), (84, 76, 74), (112, 104, 98)]
IRON = [(30, 30, 38), (54, 54, 64), (82, 82, 94), (116, 116, 128), (154, 154, 166)]
EMBER = [(150, 46, 16), (210, 84, 24), (255, 146, 40), (255, 210, 96)]
HEMP = [(70, 58, 40), (108, 92, 64), (146, 128, 92), (184, 168, 126), (214, 202, 162)]
WATER = [(40, 70, 96), (64, 108, 140), (100, 150, 180), (150, 196, 216), (210, 236, 244)]
GLASS = [(96, 120, 132), (150, 176, 188), (200, 222, 230), (236, 248, 250)]
CORK = [(80, 56, 36), (120, 88, 58), (160, 124, 86)]


def ingot(im, ramp, x0=5, y0=12, w=22, h=9, top=4):
    """斜めから見た鉄の延べ棒 (上の面・前の面・右の面)"""
    poly(im, [(x0 + top, y0), (x0 + w, y0), (x0 + w - top, y0 + top), (x0, y0 + top)], ramp[3])
    poly(im, [(x0, y0 + top), (x0 + w - top, y0 + top), (x0 + w - top, y0 + h), (x0, y0 + h)], ramp[1])
    poly(im, [(x0 + w - top, y0 + top), (x0 + w, y0), (x0 + w, y0 + h - top), (x0 + w - top, y0 + h)], ramp[0])


# ------------------------------------------------------------------ 煤鉄
def soot_iron_a():
    """煤けた鉄の延べ棒。上の面に煤のまだら、角に熾の赤が一点"""
    im = canvas()
    ingot(im, IRON, 4, 11, 24, 10, 5)
    for (x, y) in ((10, 12), (14, 13), (19, 12), (12, 14), (22, 13), (17, 14)):
        px(im, x, y, SOOT[1])
    px(im, 25, 13, EMBER[2])
    return finish(im, IRON[4])


def soot_iron_b():
    """煤のこびりついた鉄の塊 (いびつな岩のような塊に、鉄の光る面が少し)"""
    im = canvas()
    poly(im, [(6, 18), (9, 9), (17, 6), (25, 10), (27, 19), (21, 26), (11, 26)], SOOT[2])
    poly(im, [(9, 12), (16, 8), (22, 11), (18, 16), (11, 17)], IRON[3])
    poly(im, [(11, 13), (16, 10), (19, 12), (15, 14)], IRON[4])
    poly(im, [(18, 18), (25, 15), (24, 21), (19, 24)], SOOT[1])
    for (x, y) in ((8, 20), (13, 22), (22, 22), (24, 13)):
        px(im, x, y, SOOT[0])
    return finish(im, SOOT[4])


def soot_iron_c():
    """延べ棒を二本重ね、煤で黒い帯が一本ずつ走る"""
    im = canvas()
    ingot(im, IRON, 3, 16, 20, 8, 4)
    ingot(im, IRON, 9, 8, 20, 8, 4)
    rect(im, 12, 12, 26, 13, SOOT[1])
    rect(im, 6, 20, 18, 21, SOOT[1])
    return finish(im, IRON[4])


# ------------------------------------------------------------------ 灰麻
def ash_hemp_a():
    """灰色の麻の束 (細い茎の束を真ん中で縛る)"""
    im = canvas()
    for k, x in enumerate(range(9, 24, 2)):
        line(im, [(x, 4), (x + (k - 3) * 0.6, 28)], HEMP[2 + (k % 2)])
    rect(im, 8, 15, 24, 17, ASH[1])
    px(im, 16, 16, ASH[3])
    for x in range(9, 24, 3):
        px(im, x, 5, ASH[4])
    return finish(im, HEMP[4])


def ash_hemp_b():
    """灰をかぶった麻の繊維の玉 (ふわっとした丸に灰の点)"""
    im = canvas()
    sphere(im, 16, 17, 10.5, HEMP[:4], spec=False)
    d = ImageDraw.Draw(im)
    for k in range(4):
        d.arc((7 + k, 8 + k, 25 - k, 26 - k), start=30 * k, end=30 * k + 200, fill=c(HEMP[4]), width=1)
    for (x, y) in ((11, 12), (19, 10), (22, 18), (14, 22), (18, 16)):
        px(im, x, y, ASH[3])
    return finish(im)


def ash_hemp_c():
    """灰麻の葉の付いた一本の茎 (煤けた緑灰の葉 3 枚)"""
    im = canvas()
    line(im, [(10, 29), (20, 3)], HEMP[1], 2)
    for (x, y, s) in ((13, 20, -1), (16, 13, 1), (18, 7, -1)):
        poly(im, [(x, y), (x + s * 7, y - 3), (x + s * 9, y + 1), (x + s * 3, y + 2)], ASH[2])
        line(im, [(x, y), (x + s * 8, y - 1)], ASH[3])
    return finish(im, ASH[4])


# ------------------------------------------------------------------ 灰の護り糸
def thread_a():
    """灰色の糸のかせに、熾の玉が一つ結んである"""
    im = canvas()
    d = ImageDraw.Draw(im)
    for k, col in enumerate((ASH[1], ASH[3], ASH[2], ASH[4])):
        d.ellipse((5 + k, 7 + k, 26 - k, 25 - k), outline=c(col), width=2)
    line(im, [(16, 25), (20, 30)], ASH[2])
    sphere(im, 21, 27, 3.2, EMBER, spec=False)
    px(im, 20, 26, (255, 240, 200))
    return finish(im)


def thread_b():
    """一つの輪に結んだ護り結び。結び目に黒石の玉、熾の芯が光る"""
    im = canvas()
    d = ImageDraw.Draw(im)
    d.ellipse((6, 3, 26, 21), outline=c(ASH[3]), width=2)
    d.arc((8, 5, 24, 19), start=200, end=320, fill=c(ASH[4]), width=1)
    ellipse(im, (12, 17, 20, 25), SOOT[2])
    ellipse(im, (13, 18, 18, 23), EMBER[1])
    ellipse(im, (14, 19, 17, 22), EMBER[3])
    for x0, dx in ((14, -1), (18, 1)):
        for k in range(6):
            px(im, x0 + dx * (k // 2), 25 + k, ASH[3] if k % 2 else ASH[2])
    return finish(im)


def thread_c():
    """煤鉄の糸巻きに灰色の糸を巻き、糸の端が燃えさしのように赤い"""
    im = canvas()
    rect(im, 9, 6, 23, 8, IRON[2])
    rect(im, 9, 24, 23, 26, IRON[1])
    for y in range(9, 24):
        rect(im, 11, y, 21, y, ASH[2 + (y % 2)])
    line(im, [(21, 15), (26, 19), (27, 24)], ASH[3])
    px(im, 27, 25, EMBER[2])
    px(im, 27, 26, EMBER[3])
    return finish(im, IRON[4])


# ------------------------------------------------------------------ 煤の砥石
def whet_a():
    """黒い平たい砥石。上の面に煤の筋、角に熾"""
    im = canvas()
    im, u, v, body = I2.whet_body(im, SOOT, ASH[2])
    px(im, 24, 8, EMBER[2])
    px(im, 7, 24, EMBER[1])
    return finish(im, SOOT[4])


def whet_b():
    """煤鉄の帯を巻いた灰色の砥石"""
    im = canvas()
    im, u, v, body = I2.whet_body(im, ASH, ASH[4])
    a = np.array(im)
    a[body & (np.abs(u) < 2.2)] = c(IRON[2])
    a[body & (np.abs(u) < 0.8)] = c(IRON[4])
    return finish(Image.fromarray(a), ASH[4])


def whet_c():
    """煤けた砥石の割れ目から熾がのぞく"""
    im = canvas()
    im, u, v, body = I2.whet_body(im, SOOT, SOOT[3])
    a = np.array(im)
    a[body & (np.abs(u - 2 * np.sin(v)) < 0.7)] = c(EMBER[2])
    return finish(Image.fromarray(a), SOOT[4])


# ------------------------------------------------------------------ 灰の冷やし水
def flask(im, ramp_liquid, cork=True, shape="round"):
    if shape == "round":
        sphere(im, 16, 20, 9, GLASS, spec=False)
        sphere(im, 16, 21, 7.4, ramp_liquid, spec=False)
        rect(im, 13, 6, 19, 11, GLASS[2])
    else:
        rect(im, 9, 10, 23, 28, GLASS[2])
        rect(im, 10, 15, 22, 27, ramp_liquid[2])
        rect(im, 10, 15, 22, 16, ramp_liquid[4])
        rect(im, 13, 5, 19, 9, GLASS[1])
    if cork:
        rect(im, 13, 3, 19, 6, CORK[1])
        rect(im, 13, 3, 19, 3, CORK[2])


def cooler_a():
    """丸い瓶の青い水に、灰の粒が沈む。栓は灰麻で縛る"""
    im = canvas()
    flask(im, WATER)
    for (x, y) in ((13, 24), (17, 25), (20, 23), (15, 22)):
        px(im, x, y, ASH[3])
    rect(im, 12, 7, 20, 7, HEMP[2])
    px(im, 12, 16, (255, 255, 255))
    return finish(im)


def cooler_b():
    """四角い瓶に灰色がかった水。瓶の肩に灰の帯"""
    im = canvas()
    flask(im, [ASH[0], ASH[1], (120, 150, 166), (170, 196, 208), (220, 236, 240)], shape="square")
    rect(im, 9, 12, 23, 13, ASH[2])
    px(im, 11, 18, (255, 255, 255))
    return finish(im)


def cooler_c():
    """灰の器 (素焼きの小壺) に冷たい水。口から白い冷気"""
    im = canvas()
    poly(im, [(8, 14), (24, 14), (26, 22), (21, 28), (11, 28), (6, 22)], ASH[2])
    poly(im, [(8, 14), (24, 14), (23, 17), (9, 17)], ASH[3])
    ellipse(im, (10, 11, 22, 16), WATER[2])
    ellipse(im, (12, 12, 20, 15), WATER[3])
    for (x, y) in ((12, 8), (15, 6), (19, 7), (17, 4)):
        px(im, x, y, (230, 240, 246))
    return finish(im, ASH[4])


# ------------------------------------------------------------------ 灰焼きのパン
CRUST = [(86, 54, 30), (122, 80, 44), (160, 110, 62), (196, 146, 88), (226, 186, 126)]


def loaf(im, x0, y0, x1, y1):
    ellipse(im, (x0, y0, x1, y1), CRUST[1])
    ellipse(im, (x0 + 1, y0 + 1, x1 - 2, y1 - 3), CRUST[2])
    ellipse(im, (x0 + 3, y0 + 2, x1 - 6, y1 - 7), CRUST[3])


def bread_a():
    """灰の上で焼いた丸い田舎パン。上に灰がうっすら、焼き目の割れ"""
    im = canvas()
    loaf(im, 4, 9, 28, 27)
    for (x, y) in ((10, 12), (14, 11), (19, 12), (23, 15), (12, 16), (17, 15)):
        px(im, x, y, ASH[3])
    line(im, [(9, 18), (16, 14), (23, 18)], CRUST[0])
    return finish(im, CRUST[4])


def bread_b():
    """細長いパン (斜めの太い棒の両端を丸く) に、灰麻の紐を二本巻いた旅のパン"""
    im = canvas()
    for k in range(9):
        t = k / 8
        cx, cy = 8 + 16 * t, 24 - 16 * t
        ellipse(im, (cx - 5, cy - 5, cx + 5, cy + 5), CRUST[1])
    for k in range(9):
        t = k / 8
        cx, cy = 7 + 16 * t, 23 - 16 * t
        ellipse(im, (cx - 3, cy - 4, cx + 3, cy + 2), CRUST[2])
    for k in range(3):
        cx, cy = 10 + 6 * k, 19 - 6 * k
        line(im, [(cx - 2, cy - 3), (cx + 2, cy + 1)], CRUST[0])
    for t in (0.3, 0.7):
        cx, cy = 8 + 16 * t, 24 - 16 * t
        line(im, [(cx - 4, cy - 4), (cx + 4, cy + 4)], HEMP[3], 2)
    for (x, y) in ((12, 15), (18, 11), (20, 14)):
        px(im, x, y, ASH[3])
    return finish(im, CRUST[4])


def bread_c():
    """平たい灰焼きの円盤に、炭の焦げの格子と熾の点"""
    im = canvas()
    ellipse(im, (3, 10, 29, 25), CRUST[1])
    ellipse(im, (4, 11, 28, 22), CRUST[2])
    for x in (9, 14, 19, 24):
        line(im, [(x, 12), (x - 3, 21)], SOOT[2])
    px(im, 16, 15, EMBER[2])
    return finish(im, CRUST[4])


# ------------------------------------------------------------------ 煤の刃の効き目 (18x18)
def soot_whetted_icon():
    im = canvas(18)
    im, u, v, body = I2.whet_body(canvas(32), SOOT, SOOT[3])
    im = im.resize((18, 18), Image.NEAREST)
    a = np.array(im)
    for (x, y) in ((8, 7), (9, 8), (10, 9)):
        a[y, x] = c(EMBER[2])
    return finish(Image.fromarray(a), SOOT[4])


ITEMS = {
    "soot_iron": [soot_iron_a, soot_iron_b, soot_iron_c],
    "ash_hemp": [ash_hemp_a, ash_hemp_b, ash_hemp_c],
    "ash_ward_thread": [thread_a, thread_b, thread_c],
    "soot_whetstone": [whet_a, whet_b, whet_c],
    "ash_cooler": [cooler_a, cooler_b, cooler_c],
    "ash_baked_bread": [bread_a, bread_b, bread_c],
}
NAMES_JP = {"soot_iron": "煤鉄", "ash_hemp": "灰麻", "ash_ward_thread": "灰の護り糸", "soot_whetstone": "煤の砥石", "ash_cooler": "灰の冷やし水", "ash_baked_bread": "灰焼きのパン"}
NOTES_JP = {
    "soot_iron": ["煤けた延べ棒", "煤の塊", "二本重ね"],
    "ash_hemp": ["縛った茎の束", "繊維の玉", "葉の付いた茎"],
    "ash_ward_thread": ["かせに熾の玉", "護り結び", "煤鉄の糸巻き"],
    "soot_whetstone": ["黒い砥石", "煤鉄の帯", "割れ目に熾"],
    "ash_cooler": ["丸い瓶と灰の粒", "四角い瓶", "素焼きの小壺"],
    "ash_baked_bread": ["灰をかぶった丸パン", "灰麻の紐の細長いパン", "焦げ格子の円盤"],
}


def sheet(icons):
    cell = 150
    W = 200 + 3 * (cell * 2 + 16)
    H = 70 + len(ITEMS) * (cell + 30)
    S = Image.new("RGB", (W, H), (24, 28, 36))
    d = ImageDraw.Draw(S)
    d.text((10, 8), "四章の小物 3 案ずつ (32x32。左 = 暗い枠の 4x と 1x、右 = 明るい背景の 4x)", font=font(18), fill=(255, 255, 255))
    d.text((10, 36), "余燼の色: 灰・炭・黒石・熾の橙", font=font(13), fill=(200, 205, 215))
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
    S.save(OUT / "icons_ch4.png")


def main():
    ICON_DIR.mkdir(parents=True, exist_ok=True)
    icons = {}
    for name, fns in ITEMS.items():
        for o, fn in zip("abc", fns):
            im = fn()
            assert im.size == (32, 32), name
            im.save(ICON_DIR / f"{name}_{o}_32.png")
            icons[(name, o)] = im
    soot_whetted_icon().save(ICON_DIR / "soot_whetted_18.png")
    sheet(icons)
    print("icons", len(icons))


if __name__ == "__main__":
    main()
