#!/usr/bin/env python3
"""一章の支度の段 (docs/design/34 の 4.2) の絵: 記念品「鯨追いの印」(skyabyss:whale_chaser_mark) の 32x32 とモデル。

  python tools/gen/ch1/gen_ch1_trail_assets.py            選んだ案 (A) を textures/item と models/item に書く
  python tools/gen/ch1/gen_ch1_trail_assets.py --roughs   ラフ 3 案を tools/sources/whale_chaser_mark/ に書く (比べる用。並べた絵 sheet.png も)

案 (どれも暗い縁取り 1 px・平らな塗りに明るい面 1 段・琥珀の差し色。既存の割符・鼓動守・苔の護符と同じ描き方):
  A 骨の牙   三日月の骨の欠片に茶の紐の輪と結び目、琥珀の玉ひとつ (細く、ほかの品と並べると小さく見える)
  B 骨の円盤 丸い骨の板に灰青の鯨の影を彫り、紐で吊る (選んだ案: 苔の護符・鼓動守と同じ「吊り紐 + 塊」の形で、並べて一番なじむ)
  C 鉄の輪   鉄の小さな輪と骨の札を紐で束ねる (鉄・刃・盾の三つ)
"""
import os
import sys

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__)))))
RES = os.path.join(ROOT, 'src', 'main', 'resources')
SRC = os.path.join(ROOT, 'tools', 'sources', 'whale_chaser_mark')

OUT = (28, 24, 30, 255)          # 縁取り
BONE = (226, 218, 196, 255)
BONE_HI = (246, 241, 226, 255)
BONE_LO = (176, 166, 142, 255)
CORD = (122, 84, 52, 255)
CORD_HI = (158, 114, 72, 255)
AMBER = (224, 150, 52, 255)
AMBER_HI = (255, 206, 112, 255)
SLATE = (84, 100, 118, 255)
IRON = (150, 156, 164, 255)
IRON_HI = (206, 212, 218, 255)
IRON_LO = (98, 104, 112, 255)


def blank():
    return Image.new('RGBA', (32, 32), (0, 0, 0, 0))


def outline(img):
    """塗った画素のまわりの透明な画素に縁取りを付ける (4 近傍)"""
    px = img.load()
    solid = {(x, y) for x in range(32) for y in range(32) if px[x, y][3] > 0 and px[x, y] != OUT}
    for (x, y) in solid:
        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            q = (x + dx, y + dy)
            if 0 <= q[0] < 32 and 0 <= q[1] < 32 and px[q][3] == 0:
                px[q] = OUT
    return img


def cord_loop(px, cx, top, bottom, half):
    """吊り紐の輪 (上が丸い、細い 1 px の紐)"""
    for y in range(top, bottom + 1):
        t = (y - top) / max(1, bottom - top)
        w = int(round(half * (0.35 + 0.65 * min(1.0, t * 2.2))))
        for x in (cx - w, cx + w):
            px[x, y] = CORD if (y % 3) else CORD_HI
    for x in range(cx - int(half * 0.35), cx + int(half * 0.35) + 1):
        px[x, top] = CORD_HI


def knot(px, cx, y):
    for dx, dy, c in ((0, 0, CORD_HI), (-1, 0, CORD), (1, 0, CORD), (0, 1, CORD), (-1, 1, CORD_HI), (1, 1, CORD), (0, -1, CORD)):
        px[cx + dx, y + dy] = c


def option_a():
    img = blank()
    px = img.load()
    cord_loop(px, 16, 3, 11, 6)
    knot(px, 16, 12)
    # 琥珀の玉
    for dx, dy in ((0, 0), (-1, 0), (0, -1), (-1, -1)):
        px[16 + dx, 15 + dy] = AMBER
    px[15, 14] = AMBER_HI
    # 三日月の骨の牙 (y 16..28、右へ反る)
    for y in range(16, 29):
        t = (y - 16) / 12
        left = int(round(12 + 3.5 * t * t))
        right = int(round(20 - 1.0 * t + 2.5 * t * t))
        width = right - left
        if width < 1:
            continue
        for x in range(left, right + 1):
            px[x, y] = BONE
        px[left, y] = BONE_LO
        if width > 2:
            px[left + 1, y] = BONE_HI if y < 25 else BONE
        px[right, y] = BONE_LO
    # 先は細く
    px[19, 28] = BONE_LO
    px[19, 29] = BONE_LO
    # 刻み (鯨の波の三本線)
    for x, y in ((15, 19), (16, 19), (17, 19), (15, 22), (16, 22), (17, 22), (16, 25), (17, 25)):
        px[x, y] = BONE_LO
    return outline(img)


def option_b():
    img = blank()
    px = img.load()
    cord_loop(px, 16, 1, 8, 5)
    knot(px, 16, 9)
    cx, cy, r = 16, 20, 10.2
    for y in range(32):
        for x in range(32):
            d = ((x - cx) ** 2 + (y - cy) ** 2) ** 0.5
            if d <= r:
                px[x, y] = BONE_HI if (x - cx) + (y - cy) < -9 else BONE if d < r - 1.2 else BONE_LO
    # 彫った鯨の影 (灰青。頭が左、尾が右上へ跳ねる)と琥珀の目
    for y in range(32):
        for x in range(32):
            ex, ey = (x - 15) / 6.2, (y - 21) / 2.6
            if ex * ex + ey * ey <= 1.0:
                px[x, y] = SLATE
    for x, y in ((21, 20), (22, 19), (23, 18), (23, 17), (24, 17), (22, 18), (24, 19), (25, 18)):
        px[x, y] = SLATE
    for x in range(11, 19):
        px[x, 23] = (64, 78, 94, 255)
    px[11, 20] = AMBER
    # 縁の刻み
    for x, y in ((9, 15), (8, 18), (8, 22), (9, 25), (23, 25), (24, 22)):
        px[x, y] = BONE_LO
    return outline(img)


def option_c():
    img = blank()
    px = img.load()
    cord_loop(px, 15, 2, 9, 5)
    knot(px, 15, 10)
    # 鉄の輪 (左下)
    cx, cy = 11, 21
    for y in range(32):
        for x in range(32):
            d = ((x - cx) ** 2 + (y - cy) ** 2) ** 0.5
            if 3.2 <= d <= 5.4:
                px[x, y] = IRON_HI if (x - cx) + (y - cy) < -3 else IRON if d < 4.8 else IRON_LO
    # 骨の札 (右下、少し傾ける)
    for y in range(15, 28):
        for x in range(17, 24):
            if (x - 17) + (27 - y) * 0.15 > 0.3:
                px[x, y] = BONE if x > 17 else BONE_HI
    for y in range(15, 28):
        px[23, y] = BONE_LO
    for x, y in ((19, 19), (20, 19), (21, 19), (19, 22), (20, 22), (19, 25), (20, 25), (21, 25)):
        px[x, y] = BONE_LO
    px[20, 16] = AMBER
    # 紐から二つへ
    for y in range(11, 16):
        px[14 - (y - 11) // 2, y] = CORD
        px[16 + (y - 11) // 2, y] = CORD
    return outline(img)


OPTIONS = {'a': option_a, 'b': option_b, 'c': option_c}
CHOSEN = 'b'


def main():
    if '--roughs' in sys.argv:
        os.makedirs(SRC, exist_ok=True)
        sheet = Image.new('RGBA', (3 * 140 + 10, 150), (58, 58, 62, 255))
        for i, (k, fn) in enumerate(sorted(OPTIONS.items())):
            img = fn()
            img.save(os.path.join(SRC, 'rough_%s.png' % k))
            sheet.paste(img.resize((128, 128), Image.NEAREST), (10 + i * 140, 10), img.resize((128, 128), Image.NEAREST))
        sheet.save(os.path.join(SRC, 'sheet.png'))
        print('wrote 3 roughs to', SRC)
        return
    tex = os.path.join(RES, 'assets/skyabyss/textures/item/whale_chaser_mark.png')
    OPTIONS[CHOSEN]().save(tex)
    with open(os.path.join(RES, 'assets/skyabyss/models/item/whale_chaser_mark.json'), 'w', encoding='utf-8', newline='\n') as f:
        f.write('{"parent":"minecraft:item/generated","textures":{"layer0":"skyabyss:item/whale_chaser_mark"}}\n')
    print('wrote whale_chaser_mark (option %s)' % CHOSEN.upper())


if __name__ == '__main__':
    main()
