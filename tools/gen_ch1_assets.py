#!/usr/bin/env python3
"""歌石 (skyabyss:song_stone) のブロックの絵とモデルを書き出す (P4a。docs/design/17 の 1.5)。

  python tools/gen_ch1_assets.py

テクスチャは 32x32 (美術の基準: ブロックは 32x32。docs/design/01 の 8)。苔むした古い石に、刻まれた輪。
叩くと LIT (輪がほのかに青緑に光る)。書くもの:
  src/main/resources/assets/skyabyss/textures/block/song_stone_{side,top}{,_lit}.png
  src/main/resources/assets/skyabyss/models/block/song_stone{,_lit}.json
  src/main/resources/assets/skyabyss/blockstates/song_stone.json
"""
import json
import math
import os
import random

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, 'src', 'main', 'resources', 'assets', 'skyabyss')
S = 32

STONE = [(112, 116, 120), (102, 106, 111), (92, 96, 101), (124, 128, 130)]
DARK = (62, 66, 72)
MOSS = [(70, 98, 60), (86, 116, 68), (58, 84, 52)]
GLOW = (126, 236, 220)
HALO = (72, 156, 156)


def base(seed, moss_bottom):
    rnd = random.Random(seed)
    img = Image.new('RGB', (S, S))
    px = img.load()
    # 2x2 のかたまりで石の濃淡
    for by in range(0, S, 2):
        for bx in range(0, S, 2):
            c = rnd.choice(STONE)
            for dy in range(2):
                for dx in range(2):
                    px[bx + dx, by + dy] = c
    # 縦のひび
    for _ in range(3):
        x = rnd.randrange(3, S - 3)
        for y in range(rnd.randrange(0, 10), rnd.randrange(14, S)):
            if rnd.random() < 0.85:
                px[x, y] = DARK
            x = max(1, min(S - 2, x + rnd.choice((-1, 0, 0, 1))))
    # 苔: 下ほど濃い
    if moss_bottom:
        for y in range(S):
            for x in range(S):
                p = (y - 14) / 18.0
                if p > 0 and rnd.random() < p * 0.85:
                    px[x, y] = rnd.choice(MOSS)
    return img


def ring(img, cx, cy, r, lit, glyph=True):
    px = img.load()
    for y in range(S):
        for x in range(S):
            d = math.hypot(x + 0.5 - cx, y + 0.5 - cy)
            if abs(d - r) < 1.1:
                px[x, y] = GLOW if lit else DARK
            elif lit and abs(d - r) < 2.4:
                px[x, y] = HALO
    if glyph:
        # 輪の中の縦線と、その上の点 (数え歌の「ひとつ」)
        for y in range(int(cy) - 4, int(cy) + 5):
            px[int(cx), y] = GLOW if lit else DARK
        px[int(cx), int(cy) - 6] = GLOW if lit else DARK


def save(name, img):
    os.makedirs(os.path.join(RES, 'textures', 'block'), exist_ok=True)
    img.save(os.path.join(RES, 'textures', 'block', name + '.png'))


def write(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w', encoding='utf-8', newline='\n') as f:
        json.dump(data, f, ensure_ascii=False, indent=2)
        f.write('\n')


def main():
    for lit in (False, True):
        suffix = '_lit' if lit else ''
        side = base(4242, True)
        ring(side, 16, 13, 8.5, lit)
        save('song_stone_side' + suffix, side)
        top = base(4243, False)
        ring(top, 16, 16, 9, lit, glyph=False)
        ring(top, 16, 16, 4.5, lit, glyph=False)
        save('song_stone_top' + suffix, top)
        write(os.path.join(RES, 'models', 'block', 'song_stone%s.json' % suffix), {
            'parent': 'minecraft:block/cube_column',
            'textures': {'end': 'skyabyss:block/song_stone_top' + suffix, 'side': 'skyabyss:block/song_stone_side' + suffix},
        })
    write(os.path.join(RES, 'blockstates', 'song_stone.json'), {
        'variants': {
            'lit=false': {'model': 'skyabyss:block/song_stone'},
            'lit=true': {'model': 'skyabyss:block/song_stone_lit'},
        }
    })
    print('wrote song_stone textures (32x32), models, blockstate')


if __name__ == '__main__':
    main()
