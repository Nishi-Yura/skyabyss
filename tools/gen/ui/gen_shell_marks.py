#!/usr/bin/env python3
"""合わせ貝 (ShellMatchDomain) の四つの印のビットマップフォントを書く。

遠くからでも形で見分けられるよう、文字の ▲◆★● ではなく 32x32 の太い塗りの形に、
2 画素の黒い縁と、右下の面の陰 (立体に見える) を付ける。白で描き、文字の色 (印の色) が掛かる。

  巻貝 ▲ -> U+E100 (底が平らな三角。上が尖る)
  二枚貝 ◆ -> U+E101 (縦長のひし形。上下が尖る)
  ヒトデ ★ -> U+E102 (五つの腕。腕は太い)
  真珠 ● -> U+E103 (丸。中に光の点)

出力:
  src/main/resources/assets/skyabyss/textures/font/shell_marks.png (128x32)
  src/main/resources/assets/skyabyss/font/shell_marks.json

使い方: python tools/gen/ui/gen_shell_marks.py
"""
import json
import math
import os

from PIL import Image, ImageDraw

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "..", ".."))
ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets", "skyabyss")
S = 32
CHARS = ["", "", "", ""]


def star(cx, cy, r0, r1, n=5, rot=-90):
    pts = []
    for i in range(n * 2):
        r = r0 if i % 2 == 0 else r1
        a = math.radians(rot + i * 180 / n)
        pts.append((cx + r * math.cos(a), cy + r * math.sin(a)))
    return pts


def shape(kind, grow):
    """kind の形の多角形 (grow で外へ広げる: 縁を描くため)。"""
    c = (S - 1) / 2
    if kind == 0:
        return [(c, 1.5 - grow * 1.6), (S - 2 + grow, S - 4 + grow), (1 - grow, S - 4 + grow)]
    if kind == 1:
        return [(c, 0.5 - grow), (S - 5 + grow, c), (c, S - 1.5 + grow), (4 - grow, c)]
    if kind == 2:
        return star(c, c + 1.2, 15.5 + grow, 7.2 + grow * 0.8)
    return None


def draw(kind):
    img = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    c = (S - 1) / 2
    edge = (16, 16, 20, 255)
    white = (255, 255, 255, 255)
    shade = (196, 196, 204, 255)
    if kind == 3:
        d.ellipse((1, 1, S - 2, S - 2), fill=edge)
        d.ellipse((3, 3, S - 4, S - 4), fill=shade)
        d.ellipse((3, 3, S - 7, S - 7), fill=white)
        # 光の点
        d.ellipse((S - 15, S - 15, S - 9, S - 9), fill=(150, 150, 160, 255))
        return img
    d.polygon(shape(kind, 2.2), fill=edge)
    inner = shape(kind, 0)
    d.polygon(inner, fill=shade)
    # 陰: 左上へ少しずらした白で、右下の縁に陰の帯が残る
    lit = [(x - 1.6, y - 1.6) for x, y in inner]
    mask = Image.new("L", (S, S), 0)
    ImageDraw.Draw(mask).polygon(inner, fill=255)
    top = Image.new("L", (S, S), 0)
    ImageDraw.Draw(top).polygon(lit, fill=255)
    both = Image.new("L", (S, S), 0)
    both.paste(top, (0, 0), mask)
    img.paste(white, (0, 0), both)
    if kind == 1:
        # 二枚貝の合わせ目 (横の線)
        d.line([(7, c), (S - 8, c)], fill=edge, width=2)
    return img


def main():
    sheet = Image.new("RGBA", (S * 4, S), (0, 0, 0, 0))
    for k in range(4):
        sheet.paste(draw(k), (k * S, 0))
    out_png = os.path.join(ASSETS, "textures", "font", "shell_marks.png")
    os.makedirs(os.path.dirname(out_png), exist_ok=True)
    sheet.save(out_png)
    font = {"providers": [{"type": "bitmap", "file": "skyabyss:font/shell_marks.png", "height": 12, "ascent": 10, "chars": ["".join(CHARS)]}]}
    out_json = os.path.join(ASSETS, "font", "shell_marks.json")
    os.makedirs(os.path.dirname(out_json), exist_ok=True)
    with open(out_json, "w", encoding="utf-8") as f:
        json.dump(font, f, ensure_ascii=True, indent=2)
        f.write("\n")
    print("wrote", out_png, out_json)


if __name__ == "__main__":
    main()
