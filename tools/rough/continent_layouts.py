# 大陸の見取り図の案 (docs/design/40 の 3 章「作る順」の 1)。三つの案を、描いた地図のような絵にする。
#   python tools/rough/continent_layouts.py  ->  docs/review/continent_roughs/layout_{a,b,c}.png と並べた sheet.png
# 座標はブロック (x が東、z が南。都のあたりが 0)。大陸は 4000 x 4000 の中に収める。
# 選んだ案の LAYOUTS の値は、そのまま地形の生成器 (tools/gen/world/) の入力に使う
import math
import os

import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
OUT = os.path.join(ROOT, 'docs', 'review', 'continent_roughs')
HALF = 2000
N = 500          # 地形の升目 (1 升 = 8 ブロック)
PX = 1100        # 絵の一辺
FONT_TITLE = 'C:/Windows/Fonts/yumin.ttf'
FONT_BODY = 'C:/Windows/Fonts/YuGothB.ttc'

# 土地の色 (低い所, 高い所)
BIOMES = {
    'meadow': ((150, 170, 104), (118, 140, 84)),     # 都のまわりの野
    'start': ((166, 178, 112), (130, 146, 90)),      # 外れの谷
    'whale': ((132, 150, 106), (150, 146, 128)),     # 岩鯨の丘陵 (草と岩)
    'tide': ((214, 200, 150), (176, 170, 120)),      # 潮殻の浜 (砂と松)
    'storm': ((128, 140, 142), (190, 196, 204)),     # 雷翼の高地 (灰青の峰)
    'ember': ((150, 112, 92), (92, 70, 66)),         # 余燼の荒野 (灰と赤土)
}
SEA = (176, 196, 196)
SEA_DEEP = (150, 176, 182)
PAPER = (232, 220, 190)
INK = (66, 52, 40)
ROAD = (126, 82, 46)
FERRY = (60, 96, 120)

LAYOUTS = {
    'a': {
        'title': '案 A　十字の大陸',
        'lead': '都がまんなか。四つの土地へ、まっすぐな四本の道。どこへも近い',
        'land': [((0, 0), 1500, 1.0), ((-200, -300), 900, 0.4), ((300, 400), 800, 0.4)],
        'sea': [],
        'ridges': [((-1300, -500), (-900, 300), 0.9), ((-1100, -800), (-700, -400), 0.5)],
        'regions': [
            ('meadow', (0, 0)), ('start', (-460, 380)), ('whale', (0, -950)), ('tide', (1000, 0)),
            ('storm', (-1000, -50)), ('ember', (0, 1000)),
        ],
        'capital': (0, 0),
        'start': (-520, 430),
        'towns': [('鯨見', (40, -820)), ('汐守', (1080, 60)), ('風待', (-860, -60)), ('灰炉', (60, 880))],
        'sites': [('岩鯨の跡地', (-120, -1150)), ('潮殻の跡地', (1300, -120)), ('雷翼の跡地', (-1200, -300)), ('余燼の跡地', (180, 1200))],
        'roads': [[(0, 0), (40, -820), (-120, -1150)], [(0, 0), (1080, 60), (1300, -120)], [(0, 0), (-860, -60), (-1200, -300)],
                  [(0, 0), (60, 880), (180, 1200)], [(-520, 430), (-200, 160), (0, 0)]],
        'ferries': [],
        'levels': [('Lv 1–4', (220, 260)), ('Lv 4–12', (-300, -700)), ('Lv 12–18', (700, -350)), ('Lv 18–24', (-700, 350)),
                   ('Lv 24–30', (420, 700))],
        'names': [('岩鯨の丘陵', (260, -1050)), ('潮の浜', (1150, 320)), ('嵐の高地', (-1200, 120)), ('灰の荒野', (-260, 1150)),
                  ('外れの谷', (-560, 560))],
        'pros': ['道に迷わない。地図がいちばん読みやすい', '作るのがいちばん軽い (四つの土地を並べるだけ)'],
        'cons': ['どの土地も近く、旅の手ごたえが薄い', '章が進んでも「遠くへ来た」感じが出にくい'],
    },
    'b': {
        'title': '案 B　渦の旅路',
        'lead': '道が都から渦を巻いて外へ。章が進むほど遠く、高い所へ。峰が近道をふさぐ',
        'land': [((0, 0), 1700, 1.0), ((-600, 700), 900, 0.5), ((700, -700), 800, 0.4)],
        'sea': [],
        'ridges': [((-300, -350), (-700, 500), 1.0), ((-700, 500), (200, 650), 0.9), ((-1400, -700), (-1000, 200), 0.8),
                   ((-500, 1500), (400, 1300), 0.6)],
        'regions': [
            ('meadow', (150, 0)), ('start', (520, 380)), ('whale', (300, -800)), ('tide', (1200, 100)),
            ('storm', (-1150, -400)), ('ember', (-300, 1200)),
        ],
        'capital': (150, 0),
        'start': (600, 420),
        'towns': [('鯨見', (280, -700)), ('汐守', (1250, 160)), ('風待', (-1050, -520)), ('灰炉', (-200, 1080))],
        'sites': [('岩鯨の跡地', (500, -950)), ('潮殻の跡地', (1450, 450)), ('雷翼の跡地', (-1350, -150)), ('余燼の跡地', (-650, 1350))],
        'roads': [[(600, 420), (350, 200), (150, 0)], [(150, 0), (280, -700), (500, -950)], [(280, -700), (900, -500), (1250, 160), (1450, 450)],
                  [(500, -950), (-200, -1250), (-1050, -520), (-1350, -150)], [(-1050, -520), (-1350, 300), (-1100, 900), (-200, 1080), (-650, 1350)]],
        'ferries': [],
        'levels': [('Lv 1–4', (450, 120)), ('Lv 4–12', (-50, -800)), ('Lv 12–18', (950, -150)), ('Lv 18–24', (-750, -950)),
                   ('Lv 24–30', (-600, 850))],
        'names': [('岩鯨の丘陵', (550, -1200)), ('潮の浜', (1350, -200)), ('嵐の高地', (-1350, -800)), ('灰の荒野', (100, 1350)),
                  ('外れの谷', (760, 600))],
        'pros': ['章が進むほど遠く・高くなり、旅をしている感じが強い', '峰の向こうが見えていて「いつか行く所」が分かる (Wynncraft の作り)'],
        'cons': ['道が長い。行き来は呼び笛や巨神の子の飛びに頼る', '峰で道をふさぐ所が多く、作る量がいちばん多い'],
    },
    'c': {
        'title': '案 C　内海の大陸',
        'lead': '大陸の中に内海。都は西の岸。潮殻の浜へは船で渡る (二章 = 船)。峰と火山が西と南をふさぐ',
        'land': [((150, 0), 1650, 1.0), ((-900, 300), 800, 0.4), ((400, -900), 700, 0.3)],
        'sea': [((380, 30), 620, 1.25), ((900, 150), 380, 0.8)],
        'ridges': [((-950, -800), (-900, 400), 1.0), ((-1300, -300), (-1000, -900), 0.6), ((-250, 1000), (600, 1150), 0.7),
                   ((150, 1250), (250, 1300), 1.2)],
        'regions': [
            ('meadow', (-450, 0)), ('start', (-700, 520)), ('whale', (250, -1050)), ('tide', (1250, 150)),
            ('storm', (-1300, -150)), ('ember', (250, 1350)),
        ],
        'capital': (-450, 0),
        'start': (-760, 560),
        'towns': [('鯨見', (150, -980)), ('汐守', (1160, 120)), ('風待', (-1180, -100)), ('灰炉', (60, 1080))],
        'sites': [('岩鯨の跡地', (500, -1250)), ('潮殻の跡地', (1450, -250)), ('雷翼の跡地', (-1450, -450)), ('余燼の跡地', (380, 1450))],
        'roads': [[(-760, 560), (-560, 260), (-450, 0)], [(-450, 0), (-250, -650), (150, -980), (500, -1250)],
                  [(-450, 0), (-900, -80), (-1180, -100), (-1450, -450)], [(-450, 0), (-420, 700), (60, 1080), (380, 1450)],
                  [(1160, 120), (1450, -250)]],
        'ferries': [[(-180, 60), (380, 140), (1000, 140)]],
        'levels': [('Lv 1–4', (-260, 300)), ('Lv 4–12', (-150, -1100)), ('Lv 12–18', (1350, 500)), ('Lv 18–24', (-1450, 250)),
                   ('Lv 24–30', (700, 1300))],
        'names': [('岩鯨の丘陵', (300, -1400)), ('潮の浜', (1500, 250)), ('嵐の高地', (-1450, -800)), ('灰の荒野', (-200, 1400)),
                  ('外れの谷', (-900, 700)), ('内海', (380, -120))],
        'pros': ['内海が真ん中の目印になり、どこにいても都の向きが分かる', '二章の「船で渡る」が地形のまま話になる。眺めがいちばん良い',
                 '都の地下の深淵の入口が、内海の底の渦ともつながる (五章へ)'],
        'cons': ['船の行き来 (渡し船) を作る必要がある', '内海のぶん、歩ける土地は B より少ない'],
    },
}


def to_px(x, z, size=PX, margin=0):
    s = (size - 2 * margin) / (2 * HALF)
    return margin + (x + HALF) * s, margin + (z + HALF) * s


def fbm(seed, n=N, octaves=5, base=6):
    rng = np.random.default_rng(seed)
    out = np.zeros((n, n))
    amp = 1.0
    total = 0.0
    for o in range(octaves):
        g = base * (2 ** o)
        grid = rng.random((g + 1, g + 1))
        img = Image.fromarray((grid * 255).astype(np.uint8)).resize((n, n), Image.BICUBIC)
        out += np.asarray(img, dtype=float) / 255.0 * amp
        total += amp
        amp *= 0.5
    return out / total - 0.5


def seg_dist(px, pz, a, b):
    ax, az = a
    bx, bz = b
    dx, dz = bx - ax, bz - az
    t = np.clip(((px - ax) * dx + (pz - az) * dz) / (dx * dx + dz * dz), 0, 1)
    return np.hypot(px - (ax + t * dx), pz - (az + t * dz))


def terrain(lay, seed):
    xs = np.linspace(-HALF, HALF, N)
    X, Z = np.meshgrid(xs, xs)
    warp = fbm(seed + 1, octaves=4, base=4) * 500
    warp2 = fbm(seed + 2, octaves=4, base=4) * 500
    WX, WZ = X + warp, Z + warp2
    field = np.zeros_like(X)
    for (cx, cz), r, w in lay['land']:
        field += w * np.clip(1 - np.hypot(WX - cx, WZ - cz) / r, 0, None) ** 0.6
    for (cx, cz), r, w in lay['sea']:
        field -= w * np.clip(1 - np.hypot(WX - cx, WZ - cz) / r, 0, None) ** 0.7
    field += fbm(seed + 3, octaves=5, base=5) * 0.35
    edge = np.clip((np.maximum(abs(X), abs(Z)) - 1600) / 400, 0, 1)
    field -= edge * 1.2
    land = field > 0.18
    # 高さ: 岸からの近さ + 峰 + 揺らぎ
    height = np.clip(field - 0.18, 0, None) * 0.9 + fbm(seed + 4, octaves=6, base=8) * 0.12
    for a, b, w in lay['ridges']:
        d = seg_dist(WX * 0.3 + X * 0.7, WZ * 0.3 + Z * 0.7, a, b)
        height += w * np.clip(1 - d / 260, 0, None) ** 1.5 * (0.8 + fbm(seed + 5, octaves=5, base=10) * 1.4)
    # 土地: ゆがめた距離で一番近い種
    best = np.full(X.shape, 1e9)
    region = np.zeros(X.shape, dtype=int)
    for i, (_, (cx, cz)) in enumerate(lay['regions']):
        scale = 0.55 if lay['regions'][i][0] == 'start' else 1.0
        d = np.hypot(WX * 0.6 + X * 0.4 - cx, WZ * 0.6 + Z * 0.4 - cz) / (scale if scale else 1)
        m = d < best
        best[m] = d[m]
        region[m] = i
    for i, (kind, _) in enumerate(lay['regions']):
        if kind == 'storm':
            height = np.where(region == i, height + 0.25, height)
        if kind == 'whale':
            height = np.where(region == i, height + 0.1, height)
    return land, height, region


def render_map(key, lay, seed=7):
    land, height, region = terrain(lay, seed)
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
    cards = []
    for key, lay in LAYOUTS.items():
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
    sheet.save(os.path.join(OUT, 'sheet.png'))
    print('wrote sheet.png')


if __name__ == '__main__':
    main()
