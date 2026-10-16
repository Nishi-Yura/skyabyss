"""伝承の書のテクスチャ (GUI の見開き・しおりなどの部品・知らせの札・アイテム 2 種) を作る。

使い方 (リポジトリ直下):
    python tools/gen_lore_book.py            3 案 (a/b/c) を docs/review/lore_book_roughs/ に書き、比較画像を作る
    python tools/gen_lore_book.py --install b  b 案をゲームの場所 (textures/gui/lore/, textures/item/) へ写す

3 案:
  a = 羊皮紙と革: こげ茶の革の表紙に縫い目、温かい羊皮紙、青緑のしおり紐
  b = 碑文の石板: 青灰の石の表紙に青緑の光る碑文の縁取り、少し冷たい羊皮紙
  c = 鯨骨の装丁: 象牙色の骨の表紙に真鍮の留め具、羊皮紙、青緑のしおり紐

密度は 32px (GUI の 1 単位 = 2 画素)。見開きは 304x192 単位 = 608x384 画素。
部品の位置は LoreBookScreen の Sprite と合わせる (変えたら両方直す)。
乱数は seed 付きの numpy 生成器だけを使う。
"""
from __future__ import annotations

import shutil
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "docs/review/lore_book_roughs"
TEX = ROOT / "src/main/resources/assets/skyabyss/textures"

BW, BH = 608, 384
WW, WH = 512, 256

# widgets.png の部品 (u, v, w, h) — LoreBookScreen と同じ
TAB_OFF = (0, 0, 104, 36)
TAB_ON = (104, 0, 104, 48)
MARK_KNOWN = (208, 0, 16, 16)
MARK_LOCKED = (224, 0, 16, 16)
MARK_NEW = (240, 0, 16, 16)
ARROW_UP = (256, 0, 16, 16)
ARROW_DOWN = (272, 0, 16, 16)
ROW_SELECTED = (0, 56, 256, 24)
INK_BAR = (0, 88, 256, 12)
TOAST = (0, 104, 320, 64)

TEAL = np.array([29, 110, 105], float)
TEAL_GLOW = np.array([127, 214, 208], float)
INK = np.array([59, 42, 28], float)


def hx(h):
    h = h.lstrip("#")
    return np.array([int(h[i:i + 2], 16) for i in (0, 2, 4)], float)


STYLES = {
    "a": dict(
        name="羊皮紙と革",
        cover=[hx("2a1a12"), hx("3f281a"), hx("5a3a24"), hx("74503a")],
        cover_line=hx("14100c"),
        stitch=hx("b89a6a"),
        paper=[hx("cdb894"), hx("dccaa4"), hx("e9dbbb"), hx("f3e9d0")],
        paper_edge=hx("a88e64"),
        ornament=TEAL,
        ribbon=hx("1f6e69"),
        tab_off=hx("b49a72"), tab_on=hx("eadbb9"), tab_line=hx("6a4c30"),
    ),
    "b": dict(
        name="碑文の石板",
        cover=[hx("1c2229"), hx("2b343e"), hx("3e4a56"), hx("56646f")],
        cover_line=hx("0e1114"),
        stitch=TEAL_GLOW,
        paper=[hx("c9bc9c"), hx("d9ccab"), hx("e6dbbf"), hx("f0e8d3")],
        paper_edge=hx("9c8f72"),
        ornament=TEAL,
        ribbon=hx("1f6e69"),
        tab_off=hx("bdb398"), tab_on=hx("e6dbbf"), tab_line=hx("3e4a56"),
    ),
    "c": dict(
        name="鯨骨の装丁",
        cover=[hx("8f8466"), hx("b3a888"), hx("d2c8a8"), hx("ebe3c9")],
        cover_line=hx("4a4232"),
        stitch=hx("c8963c"),
        paper=[hx("cdb894"), hx("dccaa4"), hx("e9dbbb"), hx("f3e9d0")],
        paper_edge=hx("a88e64"),
        ornament=TEAL,
        ribbon=hx("1f6e69"),
        tab_off=hx("b8ab88"), tab_on=hx("eadbb9"), tab_line=hx("6f6450"),
    ),
}


# ---------------------------------------------------------------- 道具
def ramp(cols, t):
    """t (0..1) を色の段に当てる (段の間は 1 段だけ中間色を許す)"""
    n = len(cols)
    s = np.clip(t, 0, 0.999) * (n - 1)
    i = np.floor(s).astype(int)
    f = s - i
    # 段のあいだは 3 値に量子化 (0, 0.5, 1) してドット絵らしさを残す
    f = np.round(f * 2) / 2
    a = np.array(cols)[i]
    b = np.array(cols)[np.minimum(i + 1, n - 1)]
    return a * (1 - f[..., None]) + b * f[..., None]


def value_noise(w, h, cell, rng):
    gw, gh = w // cell + 2, h // cell + 2
    g = rng.random((gh, gw))
    ys, xs = np.mgrid[0:h, 0:w]
    fx, fy = xs / cell, ys / cell
    x0, y0 = fx.astype(int), fy.astype(int)
    tx, ty = fx - x0, fy - y0
    tx = tx * tx * (3 - 2 * tx)
    ty = ty * ty * (3 - 2 * ty)
    a = g[y0, x0] * (1 - tx) + g[y0, x0 + 1] * tx
    b = g[y0 + 1, x0] * (1 - tx) + g[y0 + 1, x0 + 1] * tx
    return a * (1 - ty) + b * ty


def fbm(w, h, rng, cells=(48, 24, 12, 6)):
    total = np.zeros((h, w))
    amp, norm = 1.0, 0.0
    for c in cells:
        total += value_noise(w, h, c, rng) * amp
        norm += amp
        amp *= 0.55
    return total / norm


def to_img(rgb, alpha=None):
    rgb = np.clip(rgb, 0, 255).astype(np.uint8)
    if alpha is None:
        alpha = np.full(rgb.shape[:2], 255, np.uint8)
    else:
        alpha = np.clip(alpha, 0, 255).astype(np.uint8)
    return Image.fromarray(np.dstack([rgb, alpha]), "RGBA")


def rounded_mask(w, h, r):
    m = Image.new("L", (w, h), 0)
    ImageDraw.Draw(m).rounded_rectangle((0, 0, w - 1, h - 1), r, fill=255)
    return np.array(m) > 0


def paper(w, h, st, rng, gutter=None, outer=None):
    """羊皮紙。繊維のむら、端の焼け、綴じ目の影"""
    n = fbm(w, h, rng)
    fib = value_noise(w, h, 3, rng)
    t = 0.55 + (n - 0.5) * 0.7 + (fib - 0.5) * 0.12
    ys, xs = np.mgrid[0:h, 0:w]
    # 端の焼け (外側ほど暗く)
    dx = np.minimum(xs, w - 1 - xs)
    dy = np.minimum(ys, h - 1 - ys)
    d = np.minimum(dx, dy)
    t -= np.clip(1 - d / 22.0, 0, 1) ** 2 * 0.45
    if gutter is not None:
        g = np.abs(xs - gutter)
        t -= np.clip(1 - g / 18.0, 0, 1) ** 1.6 * 0.5
    rgb = ramp(st["paper"], np.clip(t, 0, 1))
    # しみ (ところどころ)
    spots = value_noise(w, h, 20, rng)
    rgb = np.where((spots > 0.88)[..., None], rgb * 0.965, rgb)
    return rgb


def stamp_diamond(img: Image.Image, cx, cy, r, color, fill=True, glow=None):
    d = ImageDraw.Draw(img)
    pts = [(cx, cy - r), (cx + r, cy), (cx, cy + r), (cx - r, cy)]
    if glow is not None:
        for k in range(3, 0, -1):
            a = int(40 / k)
            gp = [(cx, cy - r - k), (cx + r + k, cy), (cx, cy + r + k), (cx - r - k, cy)]
            d.polygon(gp, fill=tuple(int(v) for v in glow) + (a,))
    if fill:
        d.polygon(pts, fill=tuple(int(v) for v in color) + (255,))
    else:
        d.polygon(pts, outline=tuple(int(v) for v in color) + (255,))


def hline(img, x1, x2, y, color, alpha=255, width=2):
    d = ImageDraw.Draw(img)
    d.rectangle((x1, y, x2, y + width - 1), fill=tuple(int(v) for v in color) + (alpha,))


# ---------------------------------------------------------------- 見開き
def book(style, st):
    rng = np.random.default_rng(1234)
    # 表紙
    n = fbm(BW, BH, rng, cells=(32, 16, 8, 4))
    grain = value_noise(BW, BH, 2, rng)
    t = 0.5 + (n - 0.5) * 0.8 + (grain - 0.5) * 0.15
    ys, xs = np.mgrid[0:BH, 0:BW]
    # 左上から光
    edge = np.minimum(np.minimum(xs, BW - 1 - xs), np.minimum(ys, BH - 1 - ys))
    t += np.where(edge < 4, 0.25 * ((xs + ys) < (BW + BH) / 2) - 0.2 * ((xs + ys) >= (BW + BH) / 2), 0)
    cover = ramp(st["cover"], np.clip(t, 0, 1))
    if style == "b":
        # 石の割れ目
        cr = value_noise(BW, BH, 9, rng)
        cover = np.where((np.abs(cr - 0.5) < 0.012)[..., None], st["cover"][0], cover)
    if style == "c":
        # 骨の繊維 (横向きの細い筋)
        fib = value_noise(BW, BH // 2, 6, rng).repeat(2, axis=0)[:BH]
        stripes = (np.sin(ys * 0.9 + fib * 9) > 0.92)
        cover = np.where(stripes[..., None], cover * 0.9, cover)
    mask = rounded_mask(BW, BH, 12)
    img = to_img(cover, mask * 255)
    d = ImageDraw.Draw(img)
    d.rounded_rectangle((0, 0, BW - 1, BH - 1), 12, outline=tuple(int(v) for v in st["cover_line"]) + (255,), width=2)

    # 頁の重なり (下と外側に紙の端が数枚)
    for k in (3, 2, 1):
        col = st["paper_edge"] * (0.82 + 0.06 * k)
        off = k * 2
        d.rectangle((14 - off, 10, 302, 372 + off), fill=tuple(int(v) for v in col) + (255,))
        d.rectangle((306, 10, 594 + off, 372 + off), fill=tuple(int(v) for v in col) + (255,))
        d.line((14 - off, 372 + off, 302, 372 + off), fill=tuple(int(v) for v in col * 0.8) + (255,), width=1)
        d.line((306, 372 + off, 594 + off, 372 + off), fill=tuple(int(v) for v in col * 0.8) + (255,), width=1)
    # 頁
    lp = paper(289, 363, st, rng, gutter=288)
    rp = paper(289, 363, st, rng, gutter=0)
    img.paste(to_img(lp), (14, 10))
    img.paste(to_img(rp), (306, 10))
    # 綴じ目
    d.rectangle((302, 8, 305, 375), fill=tuple(int(v) for v in st["cover_line"]) + (255,))
    d.line((303, 10, 303, 372), fill=tuple(int(v) for v in st["cover"][1]) + (255,), width=1)
    # 頁の外枠 (細い墨線)
    for x1, x2 in ((14, 302), (306, 594)):
        d.rectangle((x1, 10, x2, 372), outline=tuple(int(v) for v in st["paper_edge"] * 0.75) + (255,), width=1)

    # 表紙の飾り
    if style == "a":
        # 縫い目 (表紙の縁に沿って)
        for x in range(10, BW - 10, 8):
            for y in (4, BH - 6):
                d.rectangle((x, y, x + 3, y + 1), fill=tuple(int(v) for v in st["stitch"]) + (255,))
        for y in range(10, BH - 10, 8):
            for x in (4, BW - 6):
                d.rectangle((x, y, x + 1, y + 3), fill=tuple(int(v) for v in st["stitch"]) + (255,))
    elif style == "b":
        # 光る碑文の縁取り: 表紙の縁に短い刻線と菱形を交互に
        g = st["stitch"]
        for x in range(16, BW - 16, 12):
            for y in (3, BH - 6):
                if (x // 12) % 3 == 0:
                    stamp_diamond(img, x + 2, y + 1, 2, g, glow=g)
                else:
                    d.rectangle((x, y, x + 5, y + 1), fill=tuple(int(v) for v in g * 0.85) + (220,))
        for y in range(16, BH - 16, 12):
            for x in (3, BW - 6):
                if (y // 12) % 3 == 0:
                    stamp_diamond(img, x + 1, y + 2, 2, g, glow=g)
                else:
                    d.rectangle((x, y, x + 1, y + 5), fill=tuple(int(v) for v in g * 0.85) + (220,))
    elif style == "c":
        # 真鍮の留め具 (四隅) と骨の角当て
        brass = st["stitch"]
        for cx, cy in ((2, 2), (BW - 26, 2), (2, BH - 26), (BW - 26, BH - 26)):
            d.rounded_rectangle((cx, cy, cx + 23, cy + 23), 6, fill=tuple(int(v) for v in brass * 0.75) + (255,))
            d.rounded_rectangle((cx + 2, cy + 2, cx + 21, cy + 21), 5, fill=tuple(int(v) for v in brass) + (255,))
            d.rectangle((cx + 6, cy + 6, cx + 9, cy + 9), fill=(255, 236, 170, 255))

    # 見出しの下の罫 (GUI y=27 → 54 px)、真ん中に青緑の菱形
    for x1, x2 in ((36, 288), (320, 572)):
        hline(img, x1, x2, 54, INK, alpha=110, width=1)
        hline(img, x1 + 8, x2 - 8, 57, INK, alpha=60, width=1)
        stamp_diamond(img, (x1 + x2) // 2, 55, 3, st["ornament"], glow=TEAL_GLOW if style == "b" else None)
    # 頁の四隅の小さな飾り (碑文の青緑)
    for (x, y) in ((24, 20), (292, 20), (24, 362), (292, 362), (316, 20), (584, 20), (316, 362), (584, 362)):
        a = 150 if style != "b" else 200
        d.line((x - 4, y, x + 4, y), fill=tuple(int(v) for v in st["ornament"]) + (a,), width=1)
        d.line((x, y - 4, x, y + 4), fill=tuple(int(v) for v in st["ornament"]) + (a,), width=1)
    # しおり紐 (a と c): 綴じ目の下から垂れる
    if style in ("a", "c"):
        rb = st["ribbon"]
        for y in range(360, BH):
            w = 3
            d.rectangle((300 + (y - 360) // 6, y, 300 + (y - 360) // 6 + w, y), fill=tuple(int(v) for v in rb) + (255,))
        d.point((301, 362), fill=(140, 220, 210, 255))
    return img


# ---------------------------------------------------------------- 部品
def tab(st, w, h, on, rng):
    col = st["tab_on"] if on else st["tab_off"]
    n = fbm(w, h, rng, cells=(16, 8, 4))
    rgb = col * (0.92 + (n[..., None] - 0.5) * 0.18)
    ys, xs = np.mgrid[0:h, 0:w]
    rgb = rgb * (1 - 0.18 * (ys / h)[..., None]) if not on else rgb
    m = Image.new("L", (w, h), 0)
    ImageDraw.Draw(m).rounded_rectangle((0, 0, w - 1, h + 8), 7, fill=255)
    img = to_img(rgb, np.array(m))
    d = ImageDraw.Draw(img)
    line = tuple(int(v) for v in st["tab_line"]) + (255,)
    d.rounded_rectangle((0, 0, w - 1, h + 8), 7, outline=line, width=2)
    if on:
        # 頁へつながる下端は線を消す
        d.rectangle((2, h - 6, w - 3, h - 1), fill=tuple(int(v) for v in st["tab_on"] * 0.97) + (255,))
        d.line((6, 3, w - 7, 3), fill=tuple(int(v) for v in TEAL) + (200,), width=2)
    return img


def marker_known(st):
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    stamp_diamond(img, 7, 8, 5, TEAL * 0.8)
    stamp_diamond(img, 7, 8, 3, TEAL)
    ImageDraw.Draw(img).point([(6, 6), (7, 5)], fill=(170, 235, 228, 255))
    return img


def marker_locked(st):
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    c = tuple(int(v) for v in st["paper_edge"] * 0.8) + (220,)
    pts = [(7, 3), (12, 8), (7, 13), (2, 8)]
    d.polygon(pts, outline=c)
    return img


def marker_new(st):
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    for r, a in ((6, 50), (5, 90), (4, 140)):
        d.ellipse((8 - r, 8 - r, 8 + r - 1, 8 + r - 1), fill=tuple(int(v) for v in TEAL_GLOW) + (a,))
    d.ellipse((5, 5, 10, 10), fill=tuple(int(v) for v in TEAL) + (255,))
    d.point((6, 6), fill=(220, 255, 250, 255))
    return img


def arrow(st, up):
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    c = tuple(int(v) for v in INK) + (200,)
    pts = [(8, 4), (13, 11), (3, 11)] if up else [(8, 12), (13, 5), (3, 5)]
    d.polygon(pts, fill=c)
    return img


def row_selected(st, rng):
    w, h = ROW_SELECTED[2], ROW_SELECTED[3]
    ys, xs = np.mgrid[0:h, 0:w]
    fade = np.clip(np.minimum(xs, w - 1 - xs) / 28.0, 0, 1) * np.clip(np.minimum(ys, h - 1 - ys) / 5.0, 0, 1)
    n = value_noise(w, h, 6, rng)
    a = fade * (150 + (n - 0.5) * 16)
    rgb = np.broadcast_to(np.array([70, 170, 160], float), (h, w, 3))
    return to_img(rgb, a)


def ink_bar(st, rng):
    """伏せ字の墨の帯: にじんだ横長のしみ"""
    w, h = INK_BAR[2], INK_BAR[3]
    ys, xs = np.mgrid[0:h, 0:w]
    n = fbm(w, h, rng, cells=(16, 6, 3))
    cy = h / 2 + (value_noise(w, 1, 24, rng)[0] - 0.5) * 2
    dist = np.abs(ys - cy) / (h * 0.45)
    a = np.clip(1.15 - dist - (n - 0.5) * 0.9, 0, 1)
    a = np.round(a * 3) / 3
    rgb = np.broadcast_to(INK * 1.1, (h, w, 3))
    return to_img(rgb, a * 190)


def toast(st, rng):
    w, h = TOAST[2], TOAST[3]
    p = paper(w, h, st, rng)
    img = to_img(p, rounded_mask(w, h, 6) * 255)
    d = ImageDraw.Draw(img)
    d.rounded_rectangle((0, 0, w - 1, h - 1), 6, outline=tuple(int(v) for v in st["cover"][1]) + (255,), width=3)
    # 左に表紙の色の帯 (書の背)
    d.rounded_rectangle((3, 3, 52, h - 4), 4, fill=tuple(int(v) for v in st["cover"][2]) + (255,))
    d.rounded_rectangle((3, 3, 52, h - 4), 4, outline=tuple(int(v) for v in st["cover"][0]) + (255,), width=1)
    d.line((56, h - 12, w - 12, h - 12), fill=tuple(int(v) for v in INK) + (60,), width=1)
    stamp_diamond(img, w - 14, 12, 3, TEAL)
    return img


def widgets(style, st):
    rng = np.random.default_rng(99)
    img = Image.new("RGBA", (WW, WH), (0, 0, 0, 0))

    def put(part, rect):
        img.paste(part, (rect[0], rect[1]), part)

    put(tab(st, TAB_OFF[2], TAB_OFF[3], False, rng), TAB_OFF)
    put(tab(st, TAB_ON[2], TAB_ON[3], True, rng), TAB_ON)
    put(marker_known(st), MARK_KNOWN)
    put(marker_locked(st), MARK_LOCKED)
    put(marker_new(st), MARK_NEW)
    put(arrow(st, True), ARROW_UP)
    put(arrow(st, False), ARROW_DOWN)
    put(row_selected(st, rng), ROW_SELECTED)
    put(ink_bar(st, rng), INK_BAR)
    put(toast(st, rng), TOAST)
    return img


# ---------------------------------------------------------------- アイテム (32x32)
def _poly_mask(points, size=32):
    m = Image.new("L", (size, size), 0)
    ImageDraw.Draw(m).polygon(points, fill=255)
    return np.array(m) > 0


def _outline(mask):
    """mask の外側に接する 1 画素の輪郭"""
    pad = np.pad(mask, 1)
    near = np.zeros_like(mask)
    for dy in (-1, 0, 1):
        for dx in (-1, 0, 1):
            near |= pad[1 + dy:1 + dy + mask.shape[0], 1 + dx:1 + dx + mask.shape[1]]
    return near & ~mask


def _shade(mask, cols, rng, light=(-0.6, -0.8), noise=0.18):
    """左上から光を当てた面の色。輪郭からの距離で縁を立て、細かいむらを足す"""
    ys, xs = np.mgrid[0:32, 0:32]
    pts = np.argwhere(mask)
    if len(pts) == 0:
        return np.zeros((32, 32, 3))
    cy, cx = pts.mean(axis=0)
    span = max(np.ptp(pts[:, 0]), np.ptp(pts[:, 1]), 1)
    t = 0.55 + (light[0] * (xs - cx) + light[1] * (ys - cy)) / span * 0.55 * -1
    t += (rng.random((32, 32)) - 0.5) * noise
    # 縁: 光の側 (上・左) は明るく、影の側は暗く
    up = np.roll(mask, 1, axis=0)
    left = np.roll(mask, 1, axis=1)
    down = np.roll(mask, -1, axis=0)
    right = np.roll(mask, -1, axis=1)
    t = np.where(mask & (~up | ~left), t + 0.25, t)
    t = np.where(mask & (~down | ~right), t - 0.25, t)
    return ramp(cols, np.clip(t, 0, 1))


def item_book(style, st):
    rng = np.random.default_rng(7)
    rgb = np.zeros((32, 32, 3))
    alpha = np.zeros((32, 32))
    # 閉じた本を少し傾けて置く: 小口 (頁の束) → 表紙 → 背 の順に重ねる
    pages = _poly_mask([(9, 26), (25, 23), (27, 26), (11, 29)]) | _poly_mask([(23, 4), (26, 24), (28, 25), (25, 5)])
    cover = _poly_mask([(6, 5), (23, 3), (26, 24), (9, 27)])
    spine = _poly_mask([(5, 5), (8, 5), (11, 27), (8, 28)])
    paper_cols = [st["paper"][0] * 0.85, st["paper"][1], st["paper"][2], st["paper"][3]]
    pr = _shade(pages, paper_cols, rng, noise=0.05)
    # 小口の頁の筋
    ys, xs = np.mgrid[0:32, 0:32]
    pr = np.where(((xs + ys) % 2 == 0)[..., None], pr * 0.93, pr)
    rgb = np.where(pages[..., None], pr, rgb)
    alpha = np.where(pages, 255, alpha)
    cr = _shade(cover, st["cover"], rng)
    rgb = np.where(cover[..., None], cr, rgb)
    alpha = np.where(cover, 255, alpha)
    sr = _shade(spine, [c * 0.8 for c in st["cover"]], rng, noise=0.1)
    rgb = np.where(spine[..., None], sr, rgb)
    alpha = np.where(spine, 255, alpha)
    body = pages | cover | spine
    edge = _outline(body)
    rgb = np.where(edge[..., None], st["cover_line"] * 0.7, rgb)
    alpha = np.where(edge, 255, alpha)
    img = to_img(rgb, alpha)
    d = ImageDraw.Draw(img)
    teal = tuple(int(v) for v in TEAL) + (255,)
    glow = tuple(int(v) for v in TEAL_GLOW) + (255,)
    deep = tuple(int(v) for v in TEAL * 0.6) + (255,)
    # 表紙の碑文: 菱形の印と、上下の刻線
    cx, cy = 16, 15
    d.polygon([(cx, cy - 5), (cx + 5, cy), (cx, cy + 5), (cx - 5, cy)], fill=deep)
    d.polygon([(cx, cy - 4), (cx + 4, cy), (cx, cy + 4), (cx - 4, cy)], fill=teal)
    d.polygon([(cx, cy - 2), (cx + 2, cy), (cx, cy + 2), (cx - 2, cy)], fill=glow)
    d.point((cx - 1, cy - 1), fill=(225, 255, 250, 255))
    for (x1, y1, x2, y2) in ((11, 8, 21, 7), (13, 22, 23, 21)):
        d.line([(x1, y1 + 1), (x2, y2 + 1)], fill=tuple(int(v) for v in st["cover"][0]) + (255,))
        d.line([(x1, y1), (x2, y2)], fill=teal)
    if style == "a":
        d.line([(19, 27), (20, 31)], fill=teal)
    if style == "c":
        brass = tuple(int(v) for v in st["stitch"]) + (255,)
        d.rectangle((24, 13, 26, 16), fill=brass)
        d.point((24, 13), fill=(255, 236, 170, 255))
    return img


def item_page(style, st):
    rng = np.random.default_rng(11)
    sheet = _poly_mask([(6, 4), (25, 3), (27, 10), (25, 13), (28, 18), (24, 22), (26, 28), (16, 27), (12, 29), (9, 27), (5, 17)])
    curl = _poly_mask([(6, 4), (11, 4), (6, 9)])
    paper_cols = [st["paper"][0] * 0.9, st["paper"][1], st["paper"][2], st["paper"][3]]
    rgb = _shade(sheet, paper_cols, rng, noise=0.12)
    # 角の折れ (裏の色)
    rgb = np.where(curl[..., None], st["paper"][0] * 0.82, rgb)
    alpha = np.where(sheet, 255, 0)
    edge = _outline(sheet)
    rgb = np.where(edge[..., None], st["paper_edge"] * 0.5, rgb)
    alpha = np.where(edge, 255, alpha)
    img = to_img(rgb, alpha)
    d = ImageDraw.Draw(img)
    ink = tuple(int(v) for v in INK) + (255,)
    ink2 = tuple(int(v) for v in (INK * 0.6 + st["paper"][1] * 0.4)) + (255,)
    teal = tuple(int(v) for v in TEAL) + (255,)
    glow = tuple(int(v) for v in TEAL_GLOW) + (255,)
    # 碑文の写し (青緑) と、墨の文
    d.line([(10, 8), (21, 8)], fill=teal)
    d.point((15, 8), fill=glow)
    for y, x2 in ((12, 22), (15, 20), (18, 23), (21, 18), (24, 14)):
        d.line([(9, y), (x2, y)], fill=ink)
        d.point((x2 - 4, y), fill=ink2)
        d.point((x2 - 9, y), fill=ink2)
    return img


# ---------------------------------------------------------------- 比較画像
def preview(style, st, b, w):
    """GUI の大きさ (半分) に縮めず、そのままの画素で書と部品を並べた見本。字の位置に仮の帯を置く"""
    canvas = Image.new("RGBA", (BW + 40, BH + 80), (24, 24, 28, 255))
    # しおり (後ろ)
    for i in range(5):
        if i == 1:
            continue
        part = w.crop((TAB_OFF[0], TAB_OFF[1], TAB_OFF[0] + TAB_OFF[2], TAB_OFF[1] + TAB_OFF[3]))
        canvas.paste(part, (20 + 28 + i * 112, 50 - 30), part)
    canvas.paste(b, (20, 50), b)
    part = w.crop((TAB_ON[0], TAB_ON[1], TAB_ON[0] + TAB_ON[2], TAB_ON[1] + TAB_ON[3]))
    canvas.paste(part, (20 + 28 + 112, 50 - 36), part)
    # 一覧の行と本文の帯 (字の代わり)
    d = ImageDraw.Draw(canvas)
    for r in range(12):
        y = 50 + 66 + r * 22
        mk = MARK_KNOWN if r < 7 else MARK_LOCKED
        m = w.crop((mk[0], mk[1], mk[0] + 16, mk[1] + 16))
        if r == 2:
            sel = w.crop((ROW_SELECTED[0], ROW_SELECTED[1], ROW_SELECTED[0] + ROW_SELECTED[2], ROW_SELECTED[1] + ROW_SELECTED[3]))
            canvas.paste(sel, (20 + 32, y - 2), sel)
        canvas.paste(m, (20 + 36, y + 2), m)
        col = (59, 42, 28, 255) if r < 7 else (142, 123, 94, 255)
        d.rectangle((20 + 58, y + 6, 20 + 58 + 90 + (r * 37) % 80, y + 11), fill=col)
        if r == 4:
            nm = w.crop((MARK_NEW[0], MARK_NEW[1], MARK_NEW[0] + 16, MARK_NEW[1] + 16))
            canvas.paste(nm, (20 + 270, y + 2), nm)
    d.rectangle((20 + 320, 50 + 32, 20 + 470, 50 + 40), fill=(24, 79, 85, 255))
    for r in range(4):
        y = 50 + 70 + r * 20
        d.rectangle((20 + 322, y - 2, 20 + 323, y + 16), fill=(29, 110, 105, 128))
        d.rectangle((20 + 332, y + 4, 20 + 500 - r * 30, y + 10), fill=(29, 110, 105, 255))
    for r in range(8):
        y = 50 + 160 + r * 20
        d.rectangle((20 + 320, y + 4, 20 + 560 - (r * 53) % 120, y + 10), fill=(59, 42, 28, 255))
    ib = w.crop((INK_BAR[0], INK_BAR[1], INK_BAR[0] + INK_BAR[2], INK_BAR[1] + INK_BAR[3]))
    canvas.paste(ib, (20 + 320, 50 + 330), ib)
    return canvas


def build(style):
    st = STYLES[style]
    out = OUT / style
    out.mkdir(parents=True, exist_ok=True)
    b = book(style, st)
    w = widgets(style, st)
    ib = item_book(style, st)
    ip = item_page(style, st)
    b.save(out / "book.png")
    w.save(out / "widgets.png")
    ib.save(out / "lore_book.png")
    ip.save(out / "lore_page.png")
    pv = preview(style, st, b, w)
    # 右下にアイテムと知らせの札
    t = w.crop((TOAST[0], TOAST[1], TOAST[0] + TOAST[2], TOAST[1] + TOAST[3]))
    big = Image.new("RGBA", (pv.width, pv.height + 90), (24, 24, 28, 255))
    big.paste(pv, (0, 0))
    big.paste(t, (20, pv.height + 10), t)
    big.paste(ib.resize((32, 32), Image.NEAREST), (20 + 16, pv.height + 26), ib.resize((32, 32), Image.NEAREST))
    big.paste(ib.resize((64, 64), Image.NEAREST), (380, pv.height + 10), ib.resize((64, 64), Image.NEAREST))
    big.paste(ip.resize((64, 64), Image.NEAREST), (460, pv.height + 10), ip.resize((64, 64), Image.NEAREST))
    big.save(out / "preview.png")
    return big


def compare(images):
    w = max(i.width for i in images)
    h = sum(i.height for i in images) + 30 * len(images)
    sheet = Image.new("RGBA", (w, h), (16, 16, 18, 255))
    y = 0
    d = ImageDraw.Draw(sheet)
    for style, im in zip(STYLES, images):
        d.text((10, y + 8), f"{style}: {STYLES[style]['name']}", fill=(230, 230, 230, 255))
        sheet.paste(im, (0, y + 30))
        y += im.height + 30
    sheet.save(OUT / "compare.png")


def install(style):
    src = OUT / style
    gui = TEX / "gui/lore"
    gui.mkdir(parents=True, exist_ok=True)
    shutil.copy(src / "book.png", gui / "book.png")
    shutil.copy(src / "widgets.png", gui / "widgets.png")
    shutil.copy(src / "lore_book.png", TEX / "item/lore_book.png")
    shutil.copy(src / "lore_page.png", TEX / "item/lore_page.png")
    print("installed", style)


if __name__ == "__main__":
    if len(sys.argv) >= 3 and sys.argv[1] == "--install":
        build(sys.argv[2])
        install(sys.argv[2])
    else:
        compare([build(s) for s in STYLES])
        print("wrote", OUT)
