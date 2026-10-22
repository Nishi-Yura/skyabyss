"""控え帳 (大事な品と記録の画面) のテクスチャを作る。

使い方 (リポジトリ直下):
    python tools/gen/story/gen_record_screen.py              3 案 (a/b/c) を docs/review/record_screen_roughs/ に書き、比較画像を作る
    python tools/gen/story/gen_record_screen.py --install a  a 案をゲームの場所 (textures/gui/record/) へ写す

3 案 (どれも 32px 密度。GUI の 1 単位 = 2 画素):
  a = 帳簿: 青灰の石の表紙 (伝承の書と同じ系統) に、罫線の入った羊皮紙の見開き。左に一覧、右に詳しい頁
  b = 鯨骨の枠: ステータス画面と同じ骨の枠に、一枚の羊皮紙。真ん中を青緑の細い線で分ける
  c = 石板と札: ステータス画面と同じ暗い石板に、一覧は石の上、詳しい頁だけ羊皮紙の札を重ねる

見開きは 304x192 単位 = 608x384 画素 (伝承の書と同じ大きさ)。
部品の位置は client/record/RecordScreen の Sprite と合わせる (変えたら両方直す)。
乱数は seed 付きの numpy 生成器だけを使う。
"""
from __future__ import annotations

import shutil
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFont

sys.path.insert(0, str(Path(__file__).resolve().parent))
import gen_lore_book as lb  # noqa: E402  (羊皮紙・むら・菱形の道具を共有する)

ROOT = Path(__file__).resolve().parent.parent.parent.parent
OUT = ROOT / "docs/review/record_screen_roughs"
TEX = ROOT / "src/main/resources/assets/skyabyss/textures/gui/record"

BW, BH = 608, 384
WW, WH = 512, 256

# widgets.png の部品 (u, v, w, h) — RecordScreen と同じ
TAB_OFF = (0, 0, 128, 36)
TAB_ON = (128, 0, 128, 48)
MARK_SAFE = (256, 0, 16, 16)
MARK_ATTENTION = (272, 0, 16, 16)
MARK_LOST = (288, 0, 16, 16)
MARK_LOCKED = (304, 0, 16, 16)
MARK_CHANGED = (320, 0, 16, 16)
ARROW_UP = (336, 0, 16, 16)
ARROW_DOWN = (352, 0, 16, 16)
ROW_SELECTED = (0, 56, 256, 24)
BAR_FRAME = (256, 56, 200, 10)
BAR_FILL = (256, 70, 196, 4)
BUTTON = [(0, 88, 160, 28), (0, 120, 160, 28), (0, 152, 160, 28)]
RIBBON = (0, 184, 256, 22)

TEAL = lb.TEAL
TEAL_GLOW = lb.TEAL_GLOW
INK = lb.INK
hx = lb.hx

STYLES = {
    "a": dict(
        name="帳簿（石の表紙と罫の羊皮紙）",
        cover=[hx("1c2229"), hx("2b343e"), hx("3e4a56"), hx("56646f")],
        cover_line=hx("0e1114"),
        stitch=TEAL_GLOW,
        paper=[hx("c9bc9c"), hx("d9ccab"), hx("e6dbbf"), hx("f0e8d3")],
        paper_edge=hx("9c8f72"),
        ornament=TEAL,
        ribbon=hx("1f6e69"),
        tab_off=hx("bdb398"), tab_on=hx("e6dbbf"), tab_line=hx("3e4a56"),
    ),
    "b": dict(
        name="鯨骨の枠と一枚の羊皮紙",
        cover=[hx("6e6654"), hx("a0967c"), hx("c4baa0"), hx("e2d9c2")],
        cover_line=hx("0c0e12"),
        stitch=TEAL_GLOW,
        paper=[hx("cdb894"), hx("dccaa4"), hx("e9dbbb"), hx("f3e9d0")],
        paper_edge=hx("a88e64"),
        ornament=TEAL,
        ribbon=hx("1f6e69"),
        tab_off=hx("b8ab88"), tab_on=hx("e9dbbb"), tab_line=hx("6e6654"),
    ),
    "c": dict(
        name="暗い石板と羊皮紙の札",
        cover=[hx("14171e"), hx("1c2029"), hx("262b36"), hx("323846")],
        cover_line=hx("0c0e12"),
        stitch=TEAL_GLOW,
        paper=[hx("cdb894"), hx("dccaa4"), hx("e9dbbb"), hx("f3e9d0")],
        paper_edge=hx("a88e64"),
        ornament=TEAL,
        ribbon=hx("1f6e69"),
        tab_off=hx("2b313d"), tab_on=hx("3a4250"), tab_line=hx("0c0e12"),
    ),
}


def col(v, a=255):
    return tuple(int(x) for x in v) + (a,)


# ---------------------------------------------------------------- 見開き
def ledger_a(st):
    """伝承の書の b (碑文の石板) を土台に、頁へ帳簿の罫を引く"""
    img = lb.book("b", st)
    d = ImageDraw.Draw(img)
    # 罫線 (左の頁: 一覧の行に合わせた薄い横線。GUI y=32 から 11 刻み → 64 px から 22 px 刻み)
    for y in range(86, 362, 22):
        d.line((36, y, 288, y), fill=col(INK, 26), width=1)
    # 左の頁の右寄りに、値の欄を分ける縦の細い線 (帳簿の金額の欄)
    d.line((226, 64, 226, 356), fill=col(TEAL, 46), width=1)
    # 右の頁: 本文の罫は薄く
    for y in range(86, 340, 20):
        d.line((322, y, 572, y), fill=col(INK, 16), width=1)
    # 右の頁の下: ボタンの置き場の枠 (GUI y=170)
    d.rectangle((326, 334, 570, 362), outline=col(INK, 40), width=1)
    return img


def ledger_b(st):
    rng = np.random.default_rng(4321)
    img = Image.new("RGBA", (BW, BH), (0, 0, 0, 0))
    # 骨の枠 (ステータス画面の配色)
    n = lb.fbm(BW, BH, rng, cells=(24, 12, 6))
    t = 0.55 + (n - 0.5) * 0.6
    frame = lb.ramp(st["cover"], np.clip(t, 0, 1))
    img = lb.to_img(frame, lb.rounded_mask(BW, BH, 6) * 255)
    d = ImageDraw.Draw(img)
    d.rounded_rectangle((0, 0, BW - 1, BH - 1), 6, outline=col(st["cover_line"]), width=2)
    # 骨の節 (枠に一定の間隔で刻み)
    for x in range(20, BW - 20, 32):
        d.line((x, 3, x, 9), fill=col(st["cover"][0]), width=1)
        d.line((x, BH - 10, x, BH - 4), fill=col(st["cover"][0]), width=1)
    for y in range(20, BH - 20, 32):
        d.line((3, y, 9, y), fill=col(st["cover"][0]), width=1)
        d.line((BW - 10, y, BW - 4, y), fill=col(st["cover"][0]), width=1)
    # 一枚の羊皮紙
    p = lb.paper(BW - 24, BH - 24, st, rng)
    img.paste(lb.to_img(p), (12, 12))
    d.rectangle((12, 12, BW - 13, BH - 13), outline=col(st["cover_line"]), width=1)
    # 真ん中を青緑の線で分ける
    d.line((303, 30, 303, BH - 30), fill=col(TEAL, 160), width=2)
    lb.stamp_diamond(img, 304, 30, 4, TEAL, glow=TEAL_GLOW)
    lb.stamp_diamond(img, 304, BH - 30, 4, TEAL, glow=TEAL_GLOW)
    # 四隅の青緑の結晶 (ステータス画面と同じ)
    for cx, cy in ((7, 7), (BW - 8, 7), (7, BH - 8), (BW - 8, BH - 8)):
        lb.stamp_diamond(img, cx, cy, 5, TEAL, glow=TEAL_GLOW)
    for x1, x2 in ((36, 288), (320, 572)):
        lb.hline(img, x1, x2, 54, INK, alpha=110, width=1)
        lb.stamp_diamond(img, (x1 + x2) // 2, 55, 3, TEAL)
    return img


def ledger_c(st):
    rng = np.random.default_rng(777)
    n = lb.fbm(BW, BH, rng, cells=(32, 16, 8))
    t = 0.45 + (n - 0.5) * 0.5
    slate = lb.ramp(st["cover"], np.clip(t, 0, 1))
    img = lb.to_img(slate, lb.rounded_mask(BW, BH, 4) * 255)
    d = ImageDraw.Draw(img)
    d.rounded_rectangle((0, 0, BW - 1, BH - 1), 4, outline=col(hx("c4baa0")), width=3)
    d.rounded_rectangle((3, 3, BW - 4, BH - 4), 3, outline=col(hx("6e6654")), width=1)
    # 左: 石のくぼみに一覧
    d.rectangle((22, 60, 290, 362), fill=col(hx("0f1117")), outline=col(hx("0a0b0f")))
    d.line((23, 361, 289, 361), fill=col(hx("343a48")))
    for x1, x2 in ((36, 288),):
        lb.hline(img, x1, x2, 54, TEAL_GLOW, alpha=120, width=1)
        lb.stamp_diamond(img, (x1 + x2) // 2, 55, 3, TEAL_GLOW)
    # 右: 羊皮紙の札
    p = lb.paper(276, 352, st, rng)
    card = lb.to_img(p, lb.rounded_mask(276, 352, 6) * 255)
    img.paste(card, (310, 16), card)
    d.rounded_rectangle((310, 16, 585, 367), 6, outline=col(hx("6e6654")), width=2)
    lb.hline(img, 326, 570, 54, INK, alpha=110, width=1)
    lb.stamp_diamond(img, 448, 55, 3, TEAL)
    for cx, cy in ((7, 7), (BW - 8, 7), (7, BH - 8), (BW - 8, BH - 8)):
        lb.stamp_diamond(img, cx, cy, 5, TEAL, glow=TEAL_GLOW)
    return img


LEDGERS = {"a": ledger_a, "b": ledger_b, "c": ledger_c}


# ---------------------------------------------------------------- 部品
def mark(color, shape="diamond"):
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    if shape == "diamond":
        lb.stamp_diamond(img, 7, 8, 5, color * 0.7)
        lb.stamp_diamond(img, 7, 8, 3, color)
        d.point([(6, 6), (7, 5)], fill=col(np.minimum(color * 1.6, 255)))
    elif shape == "hollow":
        d.polygon([(7, 3), (12, 8), (7, 13), (2, 8)], outline=col(color, 220))
    elif shape == "cross":
        # なくした: 割れた菱形
        lb.stamp_diamond(img, 7, 8, 5, color * 0.75)
        d.line((4, 11, 10, 5), fill=(240, 220, 200, 255), width=1)
    elif shape == "star":
        # 変わった: 金のきらめき
        g = hx("c8902a")
        for r, a in ((6, 60), (4, 120)):
            d.ellipse((8 - r, 8 - r, 8 + r - 1, 8 + r - 1), fill=col(hx("f2c14e"), a))
        d.line((8, 2, 8, 13), fill=col(g), width=1)
        d.line((2, 8, 13, 8), fill=col(g), width=1)
        d.point([(8, 8), (7, 7), (9, 9), (7, 9), (9, 7)], fill=(255, 245, 210, 255))
    return img


def button(st, state, rng):
    w, h = BUTTON[0][2], BUTTON[0][3]
    base = {0: hx("d9ccab"), 1: hx("efe3c4"), 2: hx("b9ad90")}[state]
    n = lb.fbm(w, h, rng, cells=(12, 6, 3))
    rgb = base * (0.95 + (n[..., None] - 0.5) * 0.12)
    img = lb.to_img(rgb, lb.rounded_mask(w, h, 5) * 255)
    d = ImageDraw.Draw(img)
    edge = TEAL if state != 2 else hx("8e7b5e")
    d.rounded_rectangle((0, 0, w - 1, h - 1), 5, outline=col(edge), width=2)
    if state == 1:
        d.rounded_rectangle((3, 3, w - 4, h - 4), 4, outline=col(TEAL_GLOW, 140), width=1)
    d.line((6, h - 4, w - 7, h - 4), fill=col(INK, 50 if state != 2 else 25))
    return img


def ribbon(st, rng):
    """管理者が見ているときの印: えんじの細い帯"""
    w, h = RIBBON[2], RIBBON[3]
    n = lb.fbm(w, h, rng, cells=(16, 8, 4))
    rgb = hx("7a2e3e") * (0.9 + (n[..., None] - 0.5) * 0.2)
    ys, xs = np.mgrid[0:h, 0:w]
    a = np.ones((h, w)) * 255
    # 右端を燕尾に切る
    notch = (xs > w - 14) & (np.abs(ys - h / 2) < (xs - (w - 14)) * 0.8)
    a[notch] = 0
    img = lb.to_img(rgb, a)
    d = ImageDraw.Draw(img)
    d.line((0, 1, w - 16, 1), fill=col(hx("b8586a")), width=1)
    d.line((0, h - 2, w - 16, h - 2), fill=col(hx("4a1822")), width=1)
    return img


def bar_frame():
    w, h = BAR_FRAME[2], BAR_FRAME[3]
    img = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    d.rectangle((0, 0, w - 1, h - 1), fill=col(hx("b9ad90")), outline=col(INK, 200))
    d.rectangle((2, 2, w - 3, h - 3), fill=col(hx("8e7b5e"), 120))
    return img


def bar_fill():
    w, h = BAR_FILL[2], BAR_FILL[3]
    img = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    d.rectangle((0, 0, w - 1, h - 1), fill=col(TEAL))
    d.line((0, 0, w - 1, 0), fill=col(TEAL_GLOW))
    return img


def widgets(style, st):
    rng = np.random.default_rng(99)
    img = Image.new("RGBA", (WW, WH), (0, 0, 0, 0))

    def put(part, rect):
        img.paste(part, (rect[0], rect[1]), part)

    put(lb.tab(st, TAB_OFF[2], TAB_OFF[3], False, rng), TAB_OFF)
    put(lb.tab(st, TAB_ON[2], TAB_ON[3], True, rng), TAB_ON)
    put(mark(TEAL), MARK_SAFE)
    put(mark(hx("b07a12")), MARK_ATTENTION)
    put(mark(hx("8a2a3c"), "cross"), MARK_LOST)
    put(mark(st["paper_edge"] * 0.8, "hollow"), MARK_LOCKED)
    put(mark(None, "star"), MARK_CHANGED)
    put(lb.arrow(st, True), ARROW_UP)
    put(lb.arrow(st, False), ARROW_DOWN)
    put(lb.row_selected(st, rng), ROW_SELECTED)
    put(bar_frame(), BAR_FRAME)
    put(bar_fill(), BAR_FILL)
    for i, rect in enumerate(BUTTON):
        put(button(st, i, rng), rect)
    put(ribbon(st, rng), RIBBON)
    return img


# ---------------------------------------------------------------- 見本 (ラフの比較用。ゲームでは字はゲームが描く)
FONT_PATH = "C:/Windows/Fonts/BIZ-UDGothicR.ttc"


def mock(style, ledger, wid):
    """GUI の 2 倍で、字と行を置いた見本"""
    pad = 40
    sheet = Image.new("RGBA", (BW + pad * 2, BH + pad * 2 + 40), (26, 28, 30, 255))
    font = ImageFont.truetype(FONT_PATH, 16)
    small = ImageFont.truetype(FONT_PATH, 14)
    ox, oy = pad, pad + 36
    dark = style == "c"
    ink = (59, 42, 28, 255)
    left_ink = (230, 226, 214, 255) if dark else ink
    title = (24, 79, 85, 255)
    left_title = (159, 232, 255, 255) if dark else title
    # つまみ
    names = ["大事な品", "巨神", "成長", "縁と頁"]
    for i, n in enumerate(names):
        on = i == 0
        r = TAB_ON if on else TAB_OFF
        part = wid.crop((r[0], r[1], r[0] + r[2], r[1] + r[3]))
        x = ox + 28 + i * 136
        y = oy - (40 if on else 30)
        sheet.paste(part, (x, y), part)
        tc = (24, 79, 85, 255) if not dark else ((242, 193, 78, 255) if on else (169, 174, 188, 255))
        ImageDraw.Draw(sheet).text((x + 64, y + 18), n, font=font, fill=tc, anchor="mm")
    sheet.paste(ledger, (ox, oy), ledger)
    d = ImageDraw.Draw(sheet)
    d.text((ox + 162, oy + 34), "大事な品", font=font, fill=left_title, anchor="mm")
    d.text((ox + 162, oy + 4), "", font=small)
    rows = [
        ("h", "持ち歩く品", ""),
        ("i", "天の鍵の欠片", "手もとに 1", 0),
        ("i", "岩鯨の心核", "地面に落ちている", 1),
        ("i", "岩鯨の呼び笛", "手もとに 1", 0),
        ("i", "伝承の書", "見当たらない", 2),
        ("h", "預かっているもの", ""),
        ("i", "預かっている報酬", "3 品・経験値 40", 1),
        ("v", "背中の預かり", "なし"),
        ("h", "権利", ""),
        ("v", "初回の選択報酬", "選べる"),
    ]
    y = oy + 64
    colors = [(29, 110, 105, 255), (176, 122, 18, 255), (138, 42, 60, 255)]
    item_colors = [(127, 214, 208), (196, 186, 160), (170, 160, 200), (120, 90, 60)]
    for k, row in enumerate(rows):
        if row[0] == "h":
            d.text((ox + 40, y + 4), row[1], font=small, fill=left_title)
            y += 26
        elif row[0] == "i":
            if k == 1:
                sel = wid.crop(ROW_SELECTED[:2] + (ROW_SELECTED[0] + ROW_SELECTED[2], ROW_SELECTED[1] + ROW_SELECTED[3]))
                sel = sel.resize((252, 36))
                sheet.paste(sel, (ox + 36, y - 1), sel)
            d.rectangle((ox + 40, y + 2, ox + 70, y + 32), fill=item_colors[k % 4] + (255,), outline=(20, 20, 20, 255))
            d.text((ox + 78, y + 2), row[1], font=font, fill=left_ink)
            d.text((ox + 78, y + 19), row[2], font=small, fill=colors[row[3]])
            m = [MARK_SAFE, MARK_ATTENTION, MARK_LOST][row[3]]
            part = wid.crop((m[0], m[1], m[0] + 16, m[1] + 16))
            sheet.paste(part, (ox + 266, y + 10), part)
            y += 36
        else:
            d.text((ox + 40, y + 2), row[1], font=font, fill=left_ink)
            d.text((ox + 282, y + 2), row[2], font=font, fill=(176, 122, 18, 255), anchor="ra")
            y += 22
    # 右の頁
    rx = ox + 324
    d.rectangle((rx, oy + 20, rx + 30, oy + 50), fill=(127, 214, 208, 255), outline=(20, 20, 20, 255))
    d.text((rx + 40, oy + 26), "天の鍵の欠片", font=font, fill=title)
    d.text((rx, oy + 66), "手もとに 1", font=font, fill=colors[0])
    body = ["在りか", "・持ち物 ×1", "受け取った記録：あり", "守り：投げ捨てられない。地面に", "落ちても消えず、奈落に落ちると", "手もとへ戻る", "なくしたら：岩鯨の心臓をもう一度", "倒すと、どこにもなければ戻る"]
    yy = oy + 94
    for line in body:
        d.text((rx, yy), line, font=small, fill=ink if not line.startswith("在りか") else title)
        yy += 22
    b = wid.crop((BUTTON[0][0], BUTTON[0][1], BUTTON[0][0] + 160, BUTTON[0][1] + 28))
    sheet.paste(b, (rx + 46, oy + 334), b)
    d.text((rx + 126, oy + 348), "受け取る", font=small, fill=title, anchor="mm")
    d.text((10, 8), f"{style}: {STYLES[style]['name']}", font=font, fill=(230, 230, 230, 255))
    return sheet


def build(style):
    st = STYLES[style]
    out = OUT / style
    out.mkdir(parents=True, exist_ok=True)
    ledger = LEDGERS[style](st)
    wid = widgets(style, st)
    ledger.save(out / "ledger.png")
    wid.save(out / "widgets.png")
    m = mock(style, ledger, wid)
    m.save(out / "mock.png")
    return m


def compare(images):
    w = sum(i.width for i in images)
    h = max(i.height for i in images)
    sheet = Image.new("RGBA", (w, h), (16, 16, 18, 255))
    x = 0
    for im in images:
        sheet.paste(im, (x, 0))
        x += im.width
    sheet.save(OUT / "compare.png")


def install(style):
    src = OUT / style
    TEX.mkdir(parents=True, exist_ok=True)
    shutil.copy(src / "ledger.png", TEX / "ledger.png")
    shutil.copy(src / "widgets.png", TEX / "widgets.png")
    print("installed", style)


if __name__ == "__main__":
    if len(sys.argv) >= 3 and sys.argv[1] == "--install":
        build(sys.argv[2])
        install(sys.argv[2])
    else:
        compare([build(s) for s in STYLES])
        print("wrote", OUT)
