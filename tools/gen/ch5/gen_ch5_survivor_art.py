# -*- coding: utf-8 -*-
"""深淵の生き残り (探掘隊) の絵 (仮)。都の係の人の絵から写し、色を抜いて灰と煤をかぶせる (docs/design/36 の 8 章)。

python -I tools/gen/ch5/gen_ch5_survivor_art.py
  textures/lowpoly/attendant_<a|b|c>_<役>.png  (体の絵)
  textures/gui/npc/face/attendant_<役>_<grave|neutral|smile>.png  (会話の顔)
もとの役: 灯り番ミオ ← 宿屋、測量士ハヤセ ← 地図屋、薬師シズ ← 薬屋、隊長トウヤ ← 砦の古参、荷運びロク ← 道具屋
本番の絵は、ラフ三案から選んで描き直す (今は形の確かめ用)
"""
from __future__ import annotations

import os

from PIL import Image, ImageEnhance

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', '..', '..')
TEX = os.path.join(ROOT, 'src', 'main', 'resources', 'assets', 'skyabyss', 'textures')
FROM = {'survivor_bone': 'innkeeper', 'survivor_salt': 'mapmaker', 'survivor_storm': 'apothecary', 'survivor_ash': 'keep_veteran',
        'abyss_porter': 'tool_merchant'}
TINT = {'survivor_bone': (226, 214, 190), 'survivor_salt': (220, 226, 230), 'survivor_storm': (190, 214, 206), 'survivor_ash': (150, 140, 136),
        'abyss_porter': (206, 196, 176)}


def weather(img, tint, amount):
    img = img.convert('RGBA')
    a = img.getchannel('A')
    rgb = ImageEnhance.Color(img.convert('RGB')).enhance(0.45)
    rgb = ImageEnhance.Brightness(rgb).enhance(0.82)
    over = Image.new('RGB', rgb.size, tint)
    rgb = Image.blend(rgb, over, amount)
    out = rgb.convert('RGBA')
    out.putalpha(a)
    return out


def main():
    n = 0
    for role, src in FROM.items():
        for look in ('a', 'b', 'c'):
            p = os.path.join(TEX, 'lowpoly', 'attendant_%s_%s.png' % (look, src))
            if os.path.exists(p):
                weather(Image.open(p), TINT[role], 0.18).save(os.path.join(TEX, 'lowpoly', 'attendant_%s_%s.png' % (look, role)))
                n += 1
        for face in ('grave', 'neutral', 'smile'):
            p = os.path.join(TEX, 'gui', 'npc', 'face', 'attendant_%s_%s.png' % (src, face))
            if os.path.exists(p):
                weather(Image.open(p), TINT[role], 0.12).save(os.path.join(TEX, 'gui', 'npc', 'face', 'attendant_%s_%s.png' % (role, face)))
                n += 1
    print('wrote', n, 'textures')


if __name__ == '__main__':
    main()
