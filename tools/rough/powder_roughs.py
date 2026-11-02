"""巨神の粉の絵の案 (docs/design/41)。前の欠片 (角のある石) を粉に描き直す 3 案。32x32、暗い縁取りと 3 段の面。
  python tools/rough/powder_roughs.py -> docs/review/powder_roughs/sheet.png (案ごとに 5 属性、8 倍)
案 A = 口を縛った小さな袋に属性の印、口から粉がこぼれる / B = 小さな山 (粉の盛り) に光る粒 / C = 栓をした小瓶に粉、ラベルの印
"""
import os
import sys

from PIL import Image, ImageDraw, ImageFont

sys.path.insert(0, os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), 'gen', 'items'))
from gen_gear_freedom_assets import ELEMENTS, OUTLINE, facet, shade, symbol  # noqa: E402

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
OUT = os.path.join(ROOT, 'docs', 'review', 'powder_roughs')


def pouch(kind, base):
    img = Image.new('RGBA', (32, 32), (0, 0, 0, 0))
    cloth = (132, 104, 74)
    d = ImageDraw.Draw(img)
    # 袋の体
    d.ellipse([5, 10, 27, 30], fill=OUTLINE)
    d.ellipse([6, 11, 26, 29], fill=shade(cloth, 1.0))
    d.ellipse([7, 12, 17, 22], fill=shade(cloth, 1.2))
    d.pieslice([6, 11, 26, 29], 20, 120, fill=shade(cloth, 0.7))
    # 首と口の結び
    d.polygon([(11, 6), (21, 6), (19, 12), (13, 12)], fill=OUTLINE)
    d.polygon([(12, 7), (20, 7), (18, 11), (14, 11)], fill=shade(cloth, 0.95))
    d.line([(12, 11), (20, 11)], fill=shade(base, 0.6))
    # 口からこぼれる粉
    for x, y in ((14, 5), (16, 4), (18, 5), (17, 3), (15, 3)):
        d.point((x, y), fill=shade(base, 1.3))
    # 印 (袋の腹)
    sym = Image.new('RGBA', (32, 32), (0, 0, 0, 0))
    symbol(sym, kind, shade(base, 1.1))
    img.alpha_composite(sym, (0, 4))
    return img


def heap(kind, base):
    img = Image.new('RGBA', (32, 32), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    d.polygon([(3, 27), (9, 17), (14, 11), (18, 10), (23, 15), (29, 27)], fill=OUTLINE)
    d.polygon([(5, 26), (10, 18), (14, 12), (18, 11), (22, 16), (27, 26)], fill=shade(base, 1.0))
    d.polygon([(10, 18), (14, 12), (16, 12), (12, 22)], fill=shade(base, 1.35))
    d.polygon([(18, 11), (22, 16), (27, 26), (19, 26)], fill=shade(base, 0.7))
    # 光る粒
    for x, y in ((13, 16), (17, 20), (21, 23), (9, 23), (15, 24), (19, 15)):
        d.point((x, y), fill=shade(base, 1.7))
    d.line([(4, 28), (28, 28)], fill=shade(base, 0.45))
    return img


def vial(kind, base):
    img = Image.new('RGBA', (32, 32), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    glass = (190, 210, 220)
    d.rectangle([10, 8, 22, 29], fill=OUTLINE)
    d.rectangle([11, 9, 21, 28], fill=shade(glass, 0.9))
    d.rectangle([11, 15, 21, 28], fill=shade(base, 1.0))
    d.rectangle([12, 16, 14, 27], fill=shade(base, 1.3))
    d.rectangle([19, 16, 20, 27], fill=shade(base, 0.7))
    # 栓
    d.rectangle([12, 3, 20, 8], fill=OUTLINE)
    d.rectangle([13, 4, 19, 7], fill=(150, 110, 70, 255))
    # 光の筋
    d.line([(12, 10), (12, 14)], fill=(255, 255, 255, 200))
    sym = Image.new('RGBA', (32, 32), (0, 0, 0, 0))
    symbol(sym, kind, shade(base, 0.35))
    img.alpha_composite(sym, (0, 5))
    return img


def main():
    os.makedirs(OUT, exist_ok=True)
    rows = [('A 袋', pouch), ('B 粉の山', heap), ('C 小瓶', vial)]
    scale = 8
    cell = 32 * scale + 24
    sheet = Image.new('RGB', (160 + cell * len(ELEMENTS), 60 + cell * len(rows)), (40, 36, 44))
    d = ImageDraw.Draw(sheet)
    font = ImageFont.truetype('C:/Windows/Fonts/YuGothB.ttc', 28)
    for c, kind in enumerate(ELEMENTS):
        d.text((160 + c * cell + cell // 2, 30), kind, font=font, fill=(230, 220, 200), anchor='mm')
    for r, (label, fn) in enumerate(rows):
        d.text((20, 60 + r * cell + cell // 2), label, font=font, fill=(230, 220, 200), anchor='lm')
        for c, (kind, base) in enumerate(ELEMENTS.items()):
            img = fn(kind, base)
            img.save(os.path.join(OUT, f'{label[0]}_{kind}.png'))
            big = img.resize((32 * scale, 32 * scale), Image.NEAREST)
            sheet.paste(big, (160 + c * cell + 12, 60 + r * cell + 12), big)
    sheet.save(os.path.join(OUT, 'sheet.png'))
    print('wrote', os.path.join(OUT, 'sheet.png'))


if __name__ == '__main__':
    main()
