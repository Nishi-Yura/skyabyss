# -*- coding: utf-8 -*-
"""NPC の頭の上の印の絵 (16x24 のドット絵 5 つと、やわらかい光 1 つ) を書く。
使い方: python tools/gen_npc_marker_icons.py
出力: src/main/resources/assets/skyabyss/textures/gui/npc_marker/{bang_gold,bang_teal,question_gold,question_teal,chat,glow}.png
形: 「!」新しい話・頼み、「?」報告、吹き出し = 新しい雑談。金 = メイン、青緑 = サブ。暗い縁取りと、左上の明るい縁・右下の影つき
"""
from pathlib import Path

from PIL import Image

OUT = Path(__file__).resolve().parents[1] / "src/main/resources/assets/skyabyss/textures/gui/npc_marker"
W, H = 16, 24
OUTLINE = (26, 18, 8, 255)

BANG = [
    ".####.", "######", "######", "######", "######", ".####.", ".####.", "..##..", "..##..",
    "......", ".####.", "######", ".####.",
]
QUESTION = [
    "..######..", ".########.", "###....###", "###....###", ".......###", "......###.", ".....###..", "....###...",
    "...###....", "...###....", "..........", "...###....", "...###....", "...###....",
]
CHAT = [
    "..##########..", ".############.", "##############", "##############", "##############", "##############", "##############",
    ".############.", "..####..#####.", "....###.......", "....##........", "....#.........",
]
# 吹き出しの中の三つの点 (行, 列)
CHAT_DOTS = [(3, 3), (3, 4), (3, 6), (3, 7), (3, 9), (3, 10), (4, 3), (4, 4), (4, 6), (4, 7), (4, 9), (4, 10)]

GOLD = {"base": (242, 193, 78), "light": (255, 233, 160), "dark": (176, 118, 36)}
TEAL = {"base": (92, 207, 199), "light": (176, 245, 236), "dark": (36, 120, 124)}
PALE = {"base": (236, 230, 214), "light": (255, 252, 240), "dark": (150, 142, 128)}


def mask_of(rows):
    return {(x, y) for y, r in enumerate(rows) for x, c in enumerate(r) if c == "#"}


def icon(rows, pal, holes=()):
    shape = mask_of(rows)
    sw, sh = max(x for x, _ in shape) + 1, max(y for _, y in shape) + 1
    ox, oy = (W - sw) // 2, (H - sh) // 2
    shape = {(x + ox, y + oy) for x, y in shape}
    img = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    px = img.load()
    for x in range(W):
        for y in range(H):
            if (x, y) in shape:
                continue
            if any((x + dx, y + dy) in shape for dx in (-1, 0, 1) for dy in (-1, 0, 1)):
                px[x, y] = OUTLINE
    for x, y in shape:
        light = (x - 1, y) not in shape or (x, y - 1) not in shape
        dark = (x + 1, y) not in shape or (x, y + 1) not in shape
        c = pal["light"] if light and not dark else pal["dark"] if dark and not light else pal["base"]
        px[x, y] = c + (255,)
    for hx, hy in holes:
        px[hx + ox, hy + oy] = OUTLINE
    return img


def glow():
    size = 64
    img = Image.new("RGBA", (size, size), (255, 255, 255, 0))
    px = img.load()
    c = (size - 1) / 2
    for x in range(size):
        for y in range(size):
            d = ((x - c) ** 2 + (y - c) ** 2) ** 0.5 / c
            a = max(0.0, 1.0 - d)
            px[x, y] = (255, 255, 255, int(255 * a * a))
    return img


OUT.mkdir(parents=True, exist_ok=True)
icon(BANG, GOLD).save(OUT / "bang_gold.png")
icon(BANG, TEAL).save(OUT / "bang_teal.png")
icon(QUESTION, GOLD).save(OUT / "question_gold.png")
icon(QUESTION, TEAL).save(OUT / "question_teal.png")
icon(CHAT, PALE, CHAT_DOTS).save(OUT / "chat.png")
glow().save(OUT / "glow.png")
print(OUT)
