# -*- coding: utf-8 -*-
"""作風のラフ 3 案の撮影を、案ごとに 1 枚のシートへまとめる。

python -I tools/capital/style_sheet.py → docs/review/town_roughs/style_{a,b,c}.png
入力: run/shots/capital_style/shots/capital_style/style_<案>_<向き>.png
"""
import os

from PIL import Image, ImageDraw, ImageFont

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', '..')
SRC = os.path.join(ROOT, 'run', 'shots', 'capital_style', 'shots', 'capital_style')
OUT = os.path.join(ROOT, 'docs', 'review', 'town_roughs')
VIEWS = ['aerial', 'street', 'tower', 'hall', 'roofs']
NAMES = {'a': 'A 白の鐘都', 'b': 'B 灰石と緋の屋根', 'c': 'C 砂岩と瓦の段丘'}


def font(size):
    for f in ('C:/Windows/Fonts/meiryo.ttc', 'C:/Windows/Fonts/msgothic.ttc', 'C:/Windows/Fonts/YuGothM.ttc'):
        if os.path.exists(f):
            return ImageFont.truetype(f, size)
    return ImageFont.load_default()


def main():
    for k, name in NAMES.items():
        ims = [Image.open(os.path.join(SRC, 'style_%s_%s.png' % (k, v))).convert('RGB') for v in VIEWS]
        big = ims[0].resize((1280, 720))
        small = [im.resize((640, 360)) for im in ims[1:]]
        sheet = Image.new('RGB', (1280 + 640, 720 + 60), (24, 26, 30))
        sheet.paste(big, (0, 60))
        for i, im in enumerate(small):
            sheet.paste(im, (1280 if i < 2 else 0, 60 + (i % 2) * 360) if i < 2 else (1280, 60 + (i - 2) * 360))
        d = ImageDraw.Draw(sheet)
        d.text((16, 12), name, fill=(240, 236, 220), font=font(30))
        # 右の列が足りないので 2 段に 4 枚を並べ直す
        sheet2 = Image.new('RGB', (1280 + 640, 720 + 60 + 360), (24, 26, 30))
        sheet2.paste(big, (0, 60))
        for i, im in enumerate(small):
            if i < 2:
                sheet2.paste(im, (1280, 60 + i * 360))
            else:
                sheet2.paste(im, ((i - 2) * 640, 780))
        d = ImageDraw.Draw(sheet2)
        d.text((16, 12), name, fill=(240, 236, 220), font=font(30))
        os.makedirs(OUT, exist_ok=True)
        sheet2.save(os.path.join(OUT, 'style_%s.png' % k))
        print('wrote style_%s.png' % k)


if __name__ == '__main__':
    main()
