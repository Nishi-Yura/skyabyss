# -*- coding: utf-8 -*-
"""伝承の書・記録の帳の切り絵の絵柄 (docs/design/56 の 2.1。2026-11-24 にユーザーが「切り絵に寄せる」と決めた)。

gen_lore_book.py と gen_record_screen.py の絵柄 "p" がここを使う。色は client/ui/Paper と同じ 4 つと差し色
(黒い紙・生成り・桃色・灰の菱)。紙ははさみで切ったまっすぐな辺 (手で決めた点の並び)。
光の輪・角の丸い札・ぼかし・乱数のまだら・左右対称の飾りは使わない。絵は 2 倍の細かさ (画面には半分で描く)。
"""
from PIL import Image, ImageDraw
# 2026-11-24: 色は板の絵柄 A 鐘の銅 (client/ui/Plate の石と銅) に合わせた。名前は切り絵のときのまま (BLACK = 石、PINK = 銅)

BLACK = (24, 28, 40)
BLACK_LO = (15, 18, 26)
PINK = (196, 138, 68)
PINK_HI = (242, 196, 124)
PINK_LO = (131, 80, 42)
GREY = (168, 162, 154)
GREY_LO = (90, 84, 96)
CREAM = (236, 228, 214)
CREAM_HI = (255, 246, 234)
CREAM_LO = (201, 191, 174)


def rgba(c, a=255):
    return tuple(int(v) for v in c) + (a,)


def poly(img, pts, c, dx=0, dy=0, a=255):
    ImageDraw.Draw(img).polygon([(x + dx, y + dy) for x, y in pts], fill=rgba(c, a))


def layer(img, pts, body, under, ux, uy):
    """紙を一枚: 下に影の色の紙 (ずらして)、上に本体"""
    poly(img, pts, under, ux, uy)
    poly(img, pts, body)


def diamond(img, cx, cy, r, c, hi=None):
    d = ImageDraw.Draw(img)
    d.polygon([(cx, cy - r), (cx + r, cy), (cx, cy + r), (cx - r, cy)], fill=rgba(c))
    if hi is not None:
        d.polygon([(cx, cy - r), (cx + r, cy), (cx, cy)], fill=rgba(hi))


def pin(img, cx, cy, r, c=PINK, hi=PINK_HI):
    """黒い縁の菱 (切り絵の留め)"""
    diamond(img, cx, cy, r + 2, BLACK_LO)
    diamond(img, cx, cy, r, c, hi)


def rule(img, x1, x2, y):
    """見出しの下の区切り: 濃い桃色の細い線と、左寄り (0.31) の桃色の菱 (Golden.headLine と同じ)"""
    ImageDraw.Draw(img).rectangle((x1, y, x2, y + 1), fill=rgba(PINK_LO))
    pin(img, x1 + round((x2 - x1) * 0.31), y + 1, 4)


def slip(w, h, body, stripe, shadow=True, lift=0):
    """釦・つまみの紙 (Paper.slip と同じ形: 右へ傾けた短冊・左の端に斜めの筋・右下に黒い紙の硬い影)"""
    img = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    bw = w - 3 if shadow else w
    bh = h - 4 if shadow else h
    sl = max(4, round(bh * 0.23))
    t = 2 + lift if shadow else 0
    pts = [(sl, t), (bw, t), (bw - sl, t + bh - 2), (0, t + bh - 2)] if shadow else [(sl, 0), (bw, 0), (bw - sl, bh), (0, bh)]
    if shadow:
        poly(img, pts, BLACK_LO, 2, 3 - lift)
    poly(img, pts, body)
    if stripe is not None:
        top = pts[0][1]
        bot = pts[3][1]
        poly(img, [(sl + 6, top), (sl + 10, top), (10, bot), (6, bot)], stripe)
    return img


def tab(w, h, on):
    """つまみ (選んだものは桃色で下の頁へつながる。ほかは一段沈んだ生成り)。本のつまみは重ねて並ぶので、釦の斜めの筋は入れない (隣の字にかかる)"""
    img = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    sl = 8
    if on:
        pts = [(sl, 0), (w - 2, 0), (w - 2 - sl * h // 36, h), (0, h)]
        poly(img, pts, BLACK_LO, 2, 2)
        poly(img, pts, PINK)
    else:
        pts = [(sl, 4), (w - 3, 4), (w - 3 - sl, h - 1), (0, h - 1)]
        poly(img, pts, BLACK_LO, 2, 0)
        poly(img, pts, CREAM_LO)
    return img


def arrow(up):
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    poly(img, [(8, 4), (13, 11), (3, 11)] if up else [(8, 12), (13, 5), (3, 5)], BLACK)
    return img


def mark(kind):
    """菱の印。known/safe = 濃い桃色、new/attention = 明るい桃色、locked = 灰の縁だけ、lost = 黒に生成りの切れ目、changed = 桃色に生成りの小さな菱"""
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    if kind == "locked":
        ImageDraw.Draw(img).polygon([(7, 2), (13, 8), (7, 14), (1, 8)], outline=rgba(GREY_LO))
    elif kind == "lost":
        diamond(img, 7, 8, 6, BLACK_LO)
        diamond(img, 7, 8, 5, BLACK)
        ImageDraw.Draw(img).line((4, 11, 10, 5), fill=rgba(CREAM), width=1)
    elif kind == "changed":
        pin(img, 7, 8, 4, PINK, PINK_HI)
        diamond(img, 7, 8, 1, CREAM_HI)
    elif kind in ("new", "attention"):
        pin(img, 7, 8, 4, PINK_HI, CREAM_HI)
    else:
        pin(img, 7, 8, 4, PINK_LO, PINK)
    return img


def row_selected(w, h):
    """選んだ行: 淡い桃色の地 (段なし) と左の桃色の印"""
    img = Image.new("RGBA", (w, h), rgba(PINK_HI, 90))
    ImageDraw.Draw(img).rectangle((0, 0, 3, h - 1), fill=rgba(PINK))
    return img


def ink_bar(w, h):
    """伏せ字: 黒い紙の細い帯 (両端は斜めに切る。左右で向きを変える)"""
    img = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    poly(img, [(6, 1), (w - 1, 0), (w - 4, h - 1), (0, h - 2)], BLACK)
    return img


def toast(w, h):
    """知らせの札: 生成りの紙に、左の黒い紙の背 (斜めに切る) と右上の桃色の菱"""
    img = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    body = [(2, 2), (w - 4, 0), (w - 1, h - 5), (0, h - 3)]
    layer(img, body, CREAM, BLACK_LO, 2, 3)
    poly(img, [(4, 3), (50, 2), (42, h - 5), (2, h - 4)], BLACK)
    ImageDraw.Draw(img).rectangle((58, h - 13, w - 14, h - 12), fill=rgba(PINK_LO))
    pin(img, w - 16, 12, 4)
    return img


def sheet_black(w, h):
    """見開きの土台: 手で切った黒い紙 (右下に濃い桃色の影)"""
    img = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    pts = [(3, 5), (w * 0.43, 1), (w - 8, 4), (w - 6, h * 0.52), (w - 9, h - 8), (w * 0.57, h - 6), (4, h - 9), (1, h * 0.46)]
    layer(img, pts, BLACK, PINK_LO, 5, 6)
    return img


def page(img, x1, y1, x2, y2, under_dx, under_dy, skew):
    """生成りの頁を一枚。下に一段暗い紙と濃い桃色の紙を少しずつずらして重ねる。skew で上の辺を傾け、右の辺も少し切り違える"""
    pts = [(x1 + 2, y1 + max(0, -skew)), (x2, y1 + max(0, skew)), (x2 + 2, y2 - 3), (x1, y2)]
    poly(img, pts, PINK_LO, under_dx + (2 if under_dx > 0 else -2), under_dy + 3)
    layer(img, pts, CREAM, CREAM_LO, under_dx, under_dy)
