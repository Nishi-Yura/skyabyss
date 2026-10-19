#!/usr/bin/env python3
"""試練の板 (trial_board) と、鼓動の間の碑 (trial_tablet。lit なし/あり) のブロックの絵・モデル・状態を書く (docs/design/29 の 2.7・4.6)。
リポジトリ直下で python tools/gen_trial_blocks.py 。何度流しても同じ。"""
import json, os, random
from PIL import Image

A = 'src/main/resources/assets/skyabyss'
os.makedirs(A + '/textures/block', exist_ok=True)

def stone(seed):
    rnd = random.Random(seed)
    im = Image.new('RGBA', (16, 16))
    for y in range(16):
        for x in range(16):
            v = 88 + rnd.randint(-8, 8)
            if x in (0, 15) or y in (0, 15):
                v -= 22
            elif x in (1, 14) or y in (1, 14):
                v += 8
            im.putpixel((x, y), (v, v + 2, v + 6, 255))
    return im

def glyph(im, color, rows):
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch == '#':
                im.putpixel((3 + x, 3 + y), color)

BOARD = ["#########", "#.......#", "#.#####.#", "#.......#", "#.#####.#", "#.......#", "#.#####.#", "#.......#", "#########"]
RUNE = ["....#....", "...###...", "..#.#.#..", ".#..#..#.", "#########", ".#..#..#.", "..#.#.#..", "...###...", "....#...."]

im = stone(1); glyph(im, (214, 184, 98, 255), BOARD); im.save(A + '/textures/block/trial_board.png')
im = stone(2); glyph(im, (60, 62, 70, 255), RUNE); im.save(A + '/textures/block/trial_tablet.png')
im = stone(3); glyph(im, (255, 226, 130, 255), RUNE)
for y in range(2, 14):
    for x in range(2, 14):
        r, g, b, a = im.getpixel((x, y))
        if (r, g, b) != (255, 226, 130):
            im.putpixel((x, y), (min(255, r + 38), min(255, g + 30), b, 255))
im.save(A + '/textures/block/trial_tablet_lit.png')

def model(name, tex):
    m = {"textures": {"front": "skyabyss:block/" + tex, "particle": "skyabyss:block/" + tex},
         "elements": [{"from": [1, 0, 6], "to": [15, 15, 10],
                       "faces": {"north": {"uv": [0, 0, 16, 16], "texture": "#front"}, "south": {"uv": [0, 0, 16, 16], "texture": "#front"},
                                 "east": {"uv": [0, 0, 4, 16], "texture": "#front"}, "west": {"uv": [0, 0, 4, 16], "texture": "#front"},
                                 "up": {"uv": [0, 0, 16, 4], "texture": "#front"}, "down": {"uv": [0, 0, 16, 4], "texture": "#front"}}}]}
    os.makedirs(A + '/models/block', exist_ok=True)
    json.dump(m, open(A + '/models/block/%s.json' % name, 'w'), indent=2)

model('trial_board', 'trial_board')
model('trial_tablet', 'trial_tablet')
model('trial_tablet_lit', 'trial_tablet_lit')
rot = {'north': 0, 'east': 90, 'south': 180, 'west': 270}
json.dump({"variants": {"facing=%s" % f: {"model": "skyabyss:block/trial_board", "y": y} for f, y in rot.items()}},
          open(A + '/blockstates/trial_board.json', 'w'), indent=2)
json.dump({"variants": {"facing=%s,lit=%s" % (f, l): {"model": "skyabyss:block/trial_tablet" + ("_lit" if l == "true" else ""), "y": y}
                        for f, y in rot.items() for l in ("false", "true")}}, open(A + '/blockstates/trial_tablet.json', 'w'), indent=2)
os.makedirs(A + '/models/item', exist_ok=True)
for n in ('trial_board', 'trial_tablet'):
    json.dump({"parent": "skyabyss:block/" + n}, open(A + '/models/item/%s.json' % n, 'w'), indent=2)
print('ok')
