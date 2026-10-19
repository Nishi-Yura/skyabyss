# -*- coding: utf-8 -*-
"""ボスバーの左に出す 16x16 のドット絵 (中ボス 4 体: 鯨骨の隊長・古い見張り・真珠の番人・潮呼び) を書く。
使い方: python tools/gen_boss_icons.py
出力: src/main/resources/assets/skyabyss/textures/gui/boss/{captain,elder_sentry,pearl_keeper,tide_beckoner}_icon.png
(四つの心臓・雷獣・灰の鍛冶は、すでに <名前>_icon*.png がある。暗い縁取りは心臓の印と同じ)
"""
from pathlib import Path

from PIL import Image, ImageDraw

OUT = Path(__file__).resolve().parents[1] / "src/main/resources/assets/skyabyss/textures/gui/boss"
OUTLINE = (18, 14, 12, 255)


def finish(img):
    px = img.load()
    w, h = img.size
    solid = {(x, y) for x in range(w) for y in range(h) if px[x, y][3] > 0}
    for x in range(w):
        for y in range(h):
            if (x, y) in solid:
                continue
            if any((x + dx, y + dy) in solid for dx in (-1, 0, 1) for dy in (-1, 0, 1)):
                px[x, y] = OUTLINE
    return img


def new():
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    return img, ImageDraw.Draw(img)


def captain():
    img, d = new()
    d.polygon([(7, 1), (9, 0), (11, 2), (10, 4), (7, 4)], fill=(72, 132, 204, 255))  # 青い羽根飾り
    d.ellipse((3, 3, 12, 12), fill=(232, 226, 206, 255))  # 骨の兜
    d.rectangle((3, 9, 12, 14), fill=(214, 206, 184, 255))
    d.rectangle((5, 14, 10, 14), fill=(190, 182, 160, 255))
    d.rectangle((4, 7, 11, 8), fill=(24, 22, 28, 255))  # 面頬の隙間
    d.point((5, 7), fill=(120, 214, 232, 255))
    d.point((10, 7), fill=(120, 214, 232, 255))
    d.line((7, 9, 7, 13), fill=(160, 152, 130, 255))
    d.line((8, 9, 8, 13), fill=(160, 152, 130, 255))
    return finish(img)


def elder_sentry():
    img, d = new()
    d.polygon([(5, 14), (4, 4), (6, 1), (9, 1), (11, 4), (10, 14)], fill=(142, 150, 164, 255))  # 石の柱
    d.polygon([(5, 14), (4, 4), (6, 1), (7, 1), (6, 14)], fill=(176, 184, 196, 255))
    d.rectangle((9, 5, 10, 14), fill=(104, 110, 124, 255))
    d.ellipse((5, 5, 10, 9), fill=(28, 30, 38, 255))  # 目の窪み
    d.rectangle((6, 6, 9, 8), fill=(255, 196, 64, 255))  # 光る目
    d.point((7, 7), fill=(255, 244, 190, 255))
    d.line((5, 11, 10, 11), fill=(98, 104, 118, 255))
    return finish(img)


def pearl_keeper():
    img, d = new()
    d.pieslice((1, 2, 14, 15), 180, 360, fill=(236, 190, 196, 255))  # 貝の上の殻
    d.pieslice((1, 0, 14, 12), 0, 180, fill=(214, 156, 168, 255))  # 貝の下の殻
    d.line((3, 7, 12, 7), fill=(150, 98, 114, 255))
    for x in (4, 6, 9, 11):
        d.line((x, 3, x, 6), fill=(196, 138, 152, 255))
    d.ellipse((5, 6, 10, 11), fill=(250, 248, 240, 255))  # 真珠
    d.point((6, 7), fill=(255, 255, 255, 255))
    d.point((9, 10), fill=(196, 206, 224, 255))
    return finish(img)


def tide_beckoner():
    img, d = new()
    d.polygon([(1, 13), (1, 8), (3, 4), (7, 2), (11, 3), (13, 6), (11, 7), (9, 6), (7, 7), (8, 9), (6, 11), (4, 13)], fill=(78, 196, 188, 255))  # 巻く波
    d.polygon([(1, 13), (1, 10), (4, 12), (8, 12), (12, 11), (14, 13)], fill=(40, 132, 148, 255))
    d.line((4, 5, 7, 3), fill=(190, 246, 238, 255))
    d.line((9, 3, 11, 4), fill=(190, 246, 238, 255))
    d.rectangle((6, 4, 7, 5), fill=(24, 40, 56, 255))  # 波の目
    for x, y in ((12, 9), (14, 6), (13, 11)):
        d.point((x, y), fill=(222, 252, 246, 255))
    return finish(img)


OUT.mkdir(parents=True, exist_ok=True)
captain().save(OUT / "captain_icon.png")
elder_sentry().save(OUT / "elder_sentry_icon.png")
pearl_keeper().save(OUT / "pearl_keeper_icon.png")
tide_beckoner().save(OUT / "tide_beckoner_icon.png")
print(OUT)
