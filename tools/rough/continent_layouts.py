# 大陸の見取り図の案 (docs/design/40 の 3 章「作る順」の 1)。三つの案を、描いた地図のような絵にする。
#   python tools/rough/continent_layouts.py  ->  docs/review/continent_roughs/layout_{a,b,c}.png と並べた sheet.png
# 座標はブロック (x が東、z が南。都のあたりが 0)。大陸は 4000 x 4000 の中に収める。
# 選んだ案の LAYOUTS の値は、そのまま地形の生成器 (tools/gen/world/) の入力に使う
import math
import os
import sys

import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont

sys.path.insert(0, os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), 'lib'))
from continent_layout import BIOMES, FERRY, HALF, INK, LAYOUTS, N, PAPER, PX, ROAD, SEA, SEA_DEEP, fbm, terrain, to_px  # noqa: E402

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
OUT = os.path.join(ROOT, 'docs', 'review', 'continent_roughs')
FONT_TITLE = 'C:/Windows/Fonts/yumin.ttf'
FONT_BODY = 'C:/Windows/Fonts/YuGothB.ttc'

# 土地の中のムラの色
SUB_COLORS = {
    'birch': (196, 204, 150), 'marsh': (120, 150, 120), 'dunes': (236, 222, 170), 'pine': (100, 128, 96),
    'basalt': (96, 88, 92), 'ashwood': (130, 110, 96), 'flowers': (200, 186, 120),
}


def render_map(key, lay, seed=7):
    ex = {}
    land, height, region = terrain(lay, seed, out=ex)
    img = np.zeros((N, N, 3))
    # 陰影 (北西から)
    gz, gx = np.gradient(height)
    shade = np.clip(1.0 - (gx + gz) * 18, 0.6, 1.3)
    hn = np.clip(height / 1.2, 0, 1)
    for i, (kind, _) in enumerate(lay['regions']):
        lo, hi = BIOMES[kind]
        m = region == i
        for c in range(3):
            img[..., c] = np.where(m, (lo[c] * (1 - hn) + hi[c] * hn) * shade, img[..., c])
    for kind, m in ex.get('subs', []):
        col = np.array(SUB_COLORS[kind], dtype=float)
        img = np.where(m[..., None], img * 0.35 + col * 0.65 * shade[..., None], img)
    # 海: 岸からの遠さで深く
    from scipy import ndimage
    dist = ndimage.distance_transform_edt(~land)
    deep = np.clip(dist / 30, 0, 1)[..., None]
    sea = np.array(SEA) * (1 - deep) + np.array(SEA_DEEP) * deep
    img = np.where(land[..., None], img, sea)
    # 紙の地へ寄せる (描いた地図の色)
    paper = np.array(PAPER)
    img = img * 0.82 + paper * 0.18
    grain = fbm(seed + 9, octaves=3, base=60)[..., None] * 20
    img = np.clip(img + grain, 0, 255).astype(np.uint8)
    base = Image.fromarray(img).resize((PX, PX), Image.BICUBIC)
    # 岸の線と土地の境
    landmask = Image.fromarray((land * 255).astype(np.uint8)).resize((PX, PX), Image.BICUBIC).point(lambda v: 255 if v > 127 else 0)
    coast = landmask.filter(ImageFilter.FIND_EDGES).filter(ImageFilter.MaxFilter(3))
    regimg = Image.fromarray((region * 40).astype(np.uint8)).resize((PX, PX), Image.NEAREST)
    border = regimg.filter(ImageFilter.FIND_EDGES).point(lambda v: 255 if v > 0 else 0)
    border = Image.composite(border, Image.new('L', (PX, PX), 0), landmask)
    d = ImageDraw.Draw(base)
    base.paste(Image.new('RGB', (PX, PX), INK), mask=coast.point(lambda v: 170 if v else 0))
    dots = Image.new('L', (PX, PX), 0)
    bp = np.asarray(border)
    yy, xx = np.nonzero(bp)
    dd = ImageDraw.Draw(dots)
    for x, y in zip(xx[::9], yy[::9]):
        dd.point((x, y), 160)
    base.paste(Image.new('RGB', (PX, PX), INK), mask=dots.filter(ImageFilter.MaxFilter(3)))
    # 波の線 (岸の外に二本)
    for k, a in ((10, 90), (22, 50)):
        ring = landmask.filter(ImageFilter.MaxFilter(k * 2 + 1)).filter(ImageFilter.FIND_EDGES)
        base.paste(Image.new('RGB', (PX, PX), (90, 120, 130)), mask=ring.point(lambda v, a=a: a if v else 0))
    # 峰の記号
    rng = np.random.default_rng(seed)
    hp = Image.fromarray((np.clip(height, 0, 2) * 120).astype(np.uint8)).resize((PX, PX), Image.BICUBIC)
    harr = np.asarray(hp)
    larr = np.asarray(landmask)
    for _ in range(2600):
        x, y = int(rng.integers(20, PX - 20)), int(rng.integers(20, PX - 20))
        if larr[y, x] and harr[y, x] > 105:
            s = 5 + (harr[y, x] - 105) / 14
            d.line([(x - s, y + s * 0.6), (x, y - s * 0.7), (x + s, y + s * 0.6)], fill=INK, width=2)
            d.line([(x, y - s * 0.7), (x + s * 0.35, y + s * 0.6)], fill=(110, 92, 74), width=1)
    # 林の記号 (野・谷・丘陵・浜の低い所)
    for _ in range(1700):
        x, y = int(rng.integers(20, PX - 20)), int(rng.integers(20, PX - 20))
        gi, gj = int(y / PX * N), int(x / PX * N)
        kind = lay['regions'][region[gi, gj]][0]
        if larr[y, x] and harr[y, x] < 70 and kind in ('meadow', 'start', 'whale', 'tide') and rng.random() < 0.35:
            d.ellipse([x - 3, y - 4, x + 3, y + 2], fill=(92, 118, 74), outline=(60, 76, 50))
            d.line([(x, y + 2), (x, y + 5)], fill=(60, 76, 50))
    return base, d


def draw_layer(base, d, lay):
    f_town = ImageFont.truetype(FONT_BODY, 20)
    f_cap = ImageFont.truetype(FONT_TITLE, 30)
    f_name = ImageFont.truetype(FONT_TITLE, 28)
    f_lv = ImageFont.truetype(FONT_BODY, 18)
    f_site = ImageFont.truetype(FONT_BODY, 15)

    def text(xy, s, font, fill=INK, halo=PAPER, anchor='mm'):
        d.text(xy, s, font=font, fill=fill, anchor=anchor, stroke_width=3, stroke_fill=halo)

    for road in lay['roads']:
        pts = [to_px(*p) for p in road]
        d.line(pts, fill=(240, 228, 200), width=7, joint='curve')
        d.line(pts, fill=ROAD, width=3, joint='curve')
    for ferry in lay['ferries']:
        pts = [to_px(*p) for p in ferry]
        for (x0, y0), (x1, y1) in zip(pts, pts[1:]):
            n = int(math.hypot(x1 - x0, y1 - y0) / 12)
            for i in range(0, n, 2):
                a, b = i / n, min(1, (i + 1) / n)
                d.line([(x0 + (x1 - x0) * a, y0 + (y1 - y0) * a), (x0 + (x1 - x0) * b, y0 + (y1 - y0) * b)], fill=FERRY, width=3)
        x, y = pts[len(pts) // 2]
        text((x, y - 18), '渡し船', f_site, fill=FERRY)
    for name, p in lay['sites']:
        x, y = to_px(*p)
        d.polygon([(x, y - 9), (x + 8, y), (x, y + 9), (x - 8, y)], fill=(150, 40, 36), outline=INK)
        text((x, y + 20), name, f_site, fill=(120, 30, 28))
    for name, p in lay['towns']:
        x, y = to_px(*p)
        d.rectangle([x - 7, y - 7, x + 7, y + 7], fill=(250, 240, 214), outline=INK, width=2)
        d.rectangle([x - 3, y - 3, x + 3, y + 3], fill=INK)
        text((x, y - 20), name, f_town)
    x, y = to_px(*lay['capital'])
    d.ellipse([x - 16, y - 16, x + 16, y + 16], fill=(250, 240, 214), outline=INK, width=3)
    d.ellipse([x - 8, y - 8, x + 8, y + 8], fill=(178, 140, 52), outline=INK, width=2)
    text((x, y - 34), '鐘の都', f_cap)
    text((x, y + 30), '（地下に深淵の入口）', f_site, fill=(70, 50, 90))
    x, y = to_px(*lay['start'])
    d.ellipse([x - 7, y - 7, x + 7, y + 7], fill=(230, 120, 40), outline=INK, width=2)
    text((x + 10, y + 20), 'はじまり（目覚めの野営地）', f_site, anchor='lm')
    for name, p in lay['names']:
        text(to_px(*p), name, f_name, fill=(70, 56, 44))
    for lv, p in lay['levels']:
        x, y = to_px(*p)
        w = d.textlength(lv, font=f_lv)
        d.rounded_rectangle([x - w / 2 - 8, y - 14, x + w / 2 + 8, y + 14], 6, fill=(250, 240, 214), outline=(120, 90, 60), width=2)
        d.text((x, y), lv, font=f_lv, fill=(120, 60, 30), anchor='mm')
    # のちの土地 (空けておく海)
    for label, p in lay.get('future', []):
        x, y = to_px(*p)
        d.ellipse([x - 40, y - 22, x + 40, y + 22], outline=(90, 110, 130), width=2)
        text((x, y), label, f_site, fill=(60, 80, 100))
    # 方位と縮尺
    cx, cy = PX - 80, 90
    d.line([(cx, cy - 46), (cx, cy + 46)], fill=INK, width=2)
    d.line([(cx - 46, cy), (cx + 46, cy)], fill=INK, width=2)
    d.polygon([(cx, cy - 56), (cx - 9, cy - 30), (cx + 9, cy - 30)], fill=INK)
    text((cx, cy - 72), '北', f_town)
    x0, y0 = 60, PX - 50
    x1 = to_px(-HALF + 1000, 0)[0] - to_px(-HALF, 0)[0] + x0
    d.line([(x0, y0), (x1, y0)], fill=INK, width=3)
    for t in (x0, (x0 + x1) / 2, x1):
        d.line([(t, y0 - 6), (t, y0 + 6)], fill=INK, width=2)
    text((x0, y0 + 20), '0', f_site)
    text((x1, y0 + 20), '1000 ブロック', f_site)


def frame(base):
    out = Image.new('RGB', (PX + 40, PX + 40), (110, 84, 60))
    out.paste(Image.new('RGB', (PX + 20, PX + 20), PAPER), (10, 10))
    out.paste(base, (20, 20))
    return out


def card(key, lay, mapimg):
    W = mapimg.width
    f_t = ImageFont.truetype(FONT_TITLE, 46)
    f_b = ImageFont.truetype(FONT_BODY, 24)
    f_s = ImageFont.truetype(FONT_BODY, 22)
    lines = 3 + len(lay['pros']) + len(lay['cons'])
    H = mapimg.height + 140 + lines * 36
    out = Image.new('RGB', (W, H), PAPER)
    d = ImageDraw.Draw(out)
    d.text((24, 22), lay['title'], font=f_t, fill=INK)
    d.text((26, 86), lay['lead'], font=f_b, fill=(90, 70, 52))
    out.paste(mapimg, (0, 130))
    y = mapimg.height + 150
    d.text((26, y), '良い所', font=f_b, fill=(50, 100, 60))
    y += 36
    for s in lay['pros']:
        d.text((40, y), '・' + s, font=f_s, fill=INK)
        y += 34
    d.text((26, y + 6), '気になる所', font=f_b, fill=(140, 60, 40))
    y += 42
    for s in lay['cons']:
        d.text((40, y), '・' + s, font=f_s, fill=INK)
        y += 34
    return out


def main():
    os.makedirs(OUT, exist_ok=True)
    keys = sys.argv[1:] or ['a', 'b', 'c']
    sheet_name = 'sheet.png' if not sys.argv[1:] else 'sheet_' + '_'.join(keys) + '.png'
    cards = []
    for key in keys:
        lay = LAYOUTS[key]
        base, d = render_map(key, lay)
        draw_layer(base, d, lay)
        c = card(key, lay, frame(base))
        c.save(os.path.join(OUT, f'layout_{key}.png'))
        cards.append(c)
        print('wrote', f'layout_{key}.png')
    scale = 0.6
    small = [c.resize((int(c.width * scale), int(c.height * scale)), Image.LANCZOS) for c in cards]
    sheet = Image.new('RGB', (sum(s.width for s in small) + 40 * (len(small) + 1), max(s.height for s in small) + 80), (90, 70, 52))
    x = 40
    for s in small:
        sheet.paste(s, (x, 40))
        x += s.width + 40
    sheet.save(os.path.join(OUT, sheet_name))
    print('wrote', sheet_name)


if __name__ == '__main__':
    main()
