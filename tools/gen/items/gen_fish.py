# -*- coding: utf-8 -*-
"""釣りの魚 (docs/design/54 の 6.2.4) の絵。32×32、一匹ずつ形が違う (色違いにしない)。
五つの土地に 6 種ずつ (30 種) と、どの土地でもごくまれな「巨神の夢を見た魚」。体は左向きの横姿 (鰈は上から、竜の落とし子は立ち姿)、
左上から光、外の縁は濃い一色の線。
    python tools/gen/items/gen_fish.py          -> docs/review/fish_roughs/fish_all.png (8 倍の一覧) と docs/review/fish_roughs/icons/<id>.png
    python tools/gen/items/gen_fish.py install  -> assets/skyabyss/textures/item/fish_<id>.png
"""
import math
import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[3]
OUT = ROOT / "docs/review/fish_roughs"
ITEM = ROOT / "src/main/resources/assets/skyabyss/textures/item"
OUTLINE = (28, 26, 34, 255)
FONT = ROOT / "tools/sources/fonts/DotGothic16-Regular.ttf"


def new():
    im = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    return im, im.load()


def put(px, x, y, c):
    x, y = int(x), int(y)
    if 0 <= x < 32 and 0 <= y < 32:
        px[x, y] = c if len(c) == 4 else tuple(c) + (255,)


def has(px, x, y):
    return 0 <= x < 32 and 0 <= y < 32 and px[x, y][3] > 0


def outline(im, col=OUTLINE):
    px = im.load()
    src = im.copy().load()
    for y in range(32):
        for x in range(32):
            if src[x, y][3] == 0 and any(0 <= x + dx < 32 and 0 <= y + dy < 32 and src[x + dx, y + dy][3] > 200
                                         for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                px[x, y] = col


def mix(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))


def ramp_of(dark, light, n=4):
    return [mix(dark, light, i / (n - 1)) for i in range(n)]


def body(px, cx, cy, rx, ry, ramp, belly, hump=0.0, head=0.8, taper=1.25, belly_at=0.72):
    """紡錘形の体 (左が頭)。hump = 背の盛り上がり、head = 頭の丸さ (小さいほど丸い)、taper = 尾への細り"""
    for y in range(32):
        for x in range(32):
            u = (x + 0.5 - cx) / rx
            if abs(u) >= 1:
                continue
            h = math.sqrt(1 - u * u) ** (head if u < 0 else taper)
            top = cy - ry * h * (1 + hump * max(0.0, 1 - abs(u + 0.25) * 1.6))
            bot = cy + ry * h * 0.9
            if not (top <= y + 0.5 <= bot):
                continue
            t = (y + 0.5 - top) / max(0.01, bot - top)
            light = t - 0.12 * (1 - abs(u))
            col = belly if t > belly_at else ramp[min(len(ramp) - 1, max(0, int(light * (len(ramp) + 0.4))))]
            put(px, x, y, col)


def tail(px, x0, cy, w, h, col, dark, kind="fork"):
    for y in range(32):
        for x in range(32):
            dx = x + 0.5 - x0
            if not (0 <= dx <= w):
                continue
            dy = abs(y + 0.5 - cy)
            if kind == "round":
                half = h * math.sqrt(max(0.0, 1 - (1 - dx / w) ** 2)) * (0.4 + 0.6 * dx / w)
                ok = dy <= half and (dx / w) ** 2 + (dy / h) ** 2 <= 1.05
            elif kind == "straight":
                half = h * (0.3 + 0.7 * dx / w)
                ok = dy <= half
            else:
                half = h * (0.25 + 0.75 * dx / w)
                notch = h * 0.55 * (dx / w) ** 2
                ok = dy <= half and not (dx > w * 0.55 and dy < notch * 0.6)
            if ok:
                put(px, x, y, dark if dy > half - 1.2 or dx > w - 1.2 else col)


def poly(px, pts, col):
    xs = [p[0] for p in pts]
    ys = [p[1] for p in pts]
    for y in range(int(min(ys)), int(max(ys)) + 1):
        for x in range(int(min(xs)), int(max(xs)) + 1):
            c = False
            for i in range(len(pts)):
                (x0, y0), (x1, y1) = pts[i], pts[i - 1]
                if (y0 > y + 0.5) != (y1 > y + 0.5) and x + 0.5 < (x1 - x0) * (y + 0.5 - y0) / (y1 - y0) + x0:
                    c = not c
            if c:
                put(px, x, y, col)


def eye(px, x, y, ring=(236, 232, 214), pupil=(18, 16, 22)):
    put(px, x, y, ring)
    put(px, x + 1, y, ring)
    put(px, x, y + 1, pupil)
    put(px, x + 1, y + 1, ring)


def gill(px, x, y0, y1, col):
    for y in range(y0, y1 + 1):
        put(px, x + (1 if y in (y0, y1) else 0), y, col)


def dots(px, pts, col, only_body=True):
    for x, y in pts:
        if not only_body or has(px, x, y):
            put(px, x, y, col)


def line(px, pts, col, only_body=True):
    for (x0, y0), (x1, y1) in zip(pts, pts[1:]):
        n = max(abs(x1 - x0), abs(y1 - y0), 1)
        for t in range(n + 1):
            x = round(x0 + (x1 - x0) * t / n)
            y = round(y0 + (y1 - y0) * t / n)
            if not only_body or has(px, x, y):
                put(px, x, y, col)


def spines(px, x0, x1, ybase, hmax, col, tip, step=2):
    for i, x in enumerate(range(x0, x1 + 1, step)):
        h = hmax * (1 - abs((x - x0) / max(1, x1 - x0) - 0.4) * 1.2)
        for y in range(int(ybase - h), ybase + 1):
            put(px, x, y, tip if y <= ybase - h + 1 else col)


def finish(im):
    outline(im)
    return im


# ====================================================================== 都のまわり (川と池)
def crucian():
    """小鮒: いちばんよく釣れる。くすんだ金茶、鱗の粗い格子"""
    im, px = new()
    tail(px, 23, 16, 7, 7, (140, 118, 62), (96, 80, 42))
    poly(px, [(12, 10), (19, 8), (21, 11), (13, 12)], (112, 94, 50))
    body(px, 14.5, 16, 10.5, 6.2, ramp_of((92, 78, 40), (186, 166, 100)), (214, 202, 160), hump=0.12)
    poly(px, [(12, 21), (16, 21), (14, 25)], (150, 128, 70))
    dots(px, [(x, y) for y in range(13, 21) for x in range(9, 22) if (x + y) % 4 == 0 and px[x, y][:3] != (214, 202, 160)], (106, 90, 46))
    gill(px, 8, 13, 18, (90, 74, 38))
    eye(px, 6, 14)
    return finish(im)


def sweetfish():
    """銀鮎: 細身の川の魚。青みの銀の体に、えらの後ろの黄色い点。小さな脂びれ"""
    im, px = new()
    tail(px, 25, 16, 5, 5, (150, 170, 150), (96, 116, 100))
    poly(px, [(13, 12), (18, 10), (19, 13)], (110, 130, 110))
    poly(px, [(21, 13), (23, 12), (23, 14)], (110, 130, 110))
    body(px, 15.0, 16, 11.5, 4.0, ramp_of((76, 100, 92), (190, 206, 196)), (232, 236, 224), hump=0.05, head=0.9)
    dots(px, [(10, 15), (11, 15), (10, 16)], (240, 210, 70))
    line(px, [(9, 17), (24, 17)], (170, 186, 176))
    gill(px, 7, 14, 17, (70, 90, 84))
    eye(px, 5, 14)
    return finish(im)


def bell_carp():
    """鐘鳴り鯉: 都の堀の鯉。金色の大きな鱗が鐘の形に並び、口に短いひげ"""
    im, px = new()
    tail(px, 23, 16, 7, 8, (210, 150, 50), (150, 96, 30))
    poly(px, [(10, 9), (20, 7), (22, 10), (11, 11)], (190, 130, 40))
    body(px, 14.0, 16.5, 10.5, 7.2, ramp_of((150, 96, 30), (250, 210, 110)), (252, 236, 190), hump=0.2)
    poly(px, [(11, 22), (15, 22), (13, 26)], (220, 160, 60))
    for y in range(12, 21, 3):
        for x in range(9 + (y % 2), 22, 3):
            if has(px, x, y):
                put(px, x, y, (170, 110, 34))
                put(px, x + 1, y, (170, 110, 34))
                put(px, x, y + 1, (250, 220, 130))
    line(px, [(3, 19), (2, 21)], (150, 96, 30), only_body=False)
    gill(px, 7, 13, 19, (150, 96, 30))
    eye(px, 5, 14)
    return finish(im)


def mud_catfish():
    """泥鯰: 平たい頭と長いひげ四本の、泥の色の鯰。尾は丸い"""
    im, px = new()
    tail(px, 24, 17, 6, 5, (80, 72, 58), (52, 46, 38), kind="round")
    poly(px, [(14, 13), (17, 11), (19, 13)], (70, 62, 50))
    body(px, 15.0, 17, 11.5, 5.0, ramp_of((48, 44, 36), (126, 114, 92)), (170, 160, 130), hump=-0.05, head=0.45)
    for (x0, y0), (x1, y1) in (((4, 16), (0, 20)), ((4, 17), (1, 23)), ((5, 15), (1, 12)), ((6, 18), (4, 24))):
        line(px, [(x0, y0), (x1, y1)], (60, 54, 44), only_body=False)
    dots(px, [(x, y) for y in range(13, 19) for x in range(9, 23) if (x * 7 + y * 3) % 9 == 0], (70, 64, 52))
    eye(px, 6, 15, ring=(200, 190, 150))
    return finish(im)


def striped_dace():
    """縞鮠: 小さな川の魚。体の真ん中を走る黒い縞と、赤い腹びれ"""
    im, px = new()
    tail(px, 22, 16, 6, 5, (150, 150, 140), (96, 96, 90))
    poly(px, [(12, 12), (16, 10), (17, 13)], (120, 120, 110))
    body(px, 13.5, 16, 9.0, 4.6, ramp_of((96, 104, 100), (210, 214, 204)), (236, 234, 226), hump=0.08)
    line(px, [(7, 16), (22, 16)], (36, 36, 40))
    line(px, [(8, 15), (20, 15)], (130, 134, 128))
    poly(px, [(11, 19), (14, 19), (12, 23)], (210, 70, 56))
    poly(px, [(16, 19), (18, 19), (17, 22)], (210, 70, 56))
    eye(px, 6, 14)
    return finish(im)


def mill_trout():
    """水車鱒: 水車のある川の鱒。桃色の横の帯と、背の黒い点"""
    im, px = new()
    tail(px, 24, 16, 6, 6, (120, 130, 100), (80, 90, 66), kind="straight")
    poly(px, [(12, 11), (18, 9), (19, 12)], (100, 110, 84))
    body(px, 14.5, 16, 11.0, 5.4, ramp_of((70, 90, 70), (180, 190, 150)), (236, 228, 210), hump=0.1)
    line(px, [(7, 16), (23, 16)], (220, 120, 130))
    line(px, [(8, 17), (22, 17)], (200, 104, 116))
    dots(px, [(x, y) for y in range(11, 16) for x in range(8, 24) if (x * 5 + y * 3) % 7 == 0], (30, 30, 30))
    gill(px, 8, 13, 18, (70, 80, 60))
    eye(px, 5, 14)
    return finish(im)


# ====================================================================== 鯨見 (岩の岸と冷たい海)
def moss_sculpin():
    """苔鰍: 大きな頭と棘の鰍。苔のまだらの緑、扇のような胸びれ"""
    im, px = new()
    tail(px, 22, 18, 6, 5, (90, 110, 60), (60, 76, 40), kind="round")
    spines(px, 10, 20, 12, 4, (80, 100, 56), (40, 52, 30))
    body(px, 13.5, 18, 10.0, 6.0, ramp_of((56, 72, 40), (150, 170, 100)), (200, 200, 150), hump=0.0, head=0.4, taper=1.6)
    poly(px, [(10, 18), (14, 21), (13, 26), (8, 24)], (120, 140, 80))
    dots(px, [(x, y) for y in range(13, 22) for x in range(5, 21) if (x * 3 + y * 5) % 6 == 0], (40, 56, 30))
    for x, y in ((6, 12), (4, 13)):
        put(px, x, y, (60, 76, 40))
    eye(px, 6, 14, ring=(230, 200, 90))
    return finish(im)


def songstone_flounder():
    """歌石鰈: 上から見た平たい鰈。砂色の体を縁のひれがぐるりと囲み、目は二つとも上。歌石のような白い輪の模様"""
    im, px = new()
    for y in range(32):
        for x in range(32):
            u = (x + 0.5 - 15) / 12.5
            v = (y + 0.5 - 16) / 9.5
            r = u * u + v * v
            if r <= 1.0:
                if r > 0.74:
                    col = (120, 96, 66) if (x + y) % 2 else (150, 122, 86)
                else:
                    col = mix((130, 108, 76), (210, 186, 140), max(0.0, min(1.0, 0.6 - v * 0.5 - u * 0.2)))
                put(px, x, y, col)
    poly(px, [(27, 16), (31, 12), (31, 20)], (140, 114, 80))
    dots(px, [(x, y) for y in range(10, 23) for x in range(6, 25) if (x * 7 + y * 5) % 11 == 0], (110, 88, 60))
    for cx, cy, r in ((12, 18, 2), (17, 13, 1.5), (20, 19, 2.2), (9, 13, 1.2)):
        for y in range(32):
            for x in range(32):
                d = math.hypot(x + 0.5 - cx, y + 0.5 - cy)
                if r - 0.8 <= d <= r + 0.2:
                    put(px, x, y, (240, 232, 206))
    eye(px, 5, 12)
    eye(px, 7, 15)
    return finish(im)


def bone_cod():
    """骨鱈: 太い鱈。鯨骨のような白い筋が体を斜めに走り、あごに一本のひげ"""
    im, px = new()
    tail(px, 24, 16, 6, 6, (110, 110, 100), (70, 70, 66), kind="straight")
    poly(px, [(10, 11), (14, 9), (15, 12)], (90, 90, 84))
    poly(px, [(16, 11), (19, 9), (20, 12)], (90, 90, 84))
    body(px, 14.5, 16, 11.0, 6.4, ramp_of((60, 62, 60), (170, 168, 156)), (226, 222, 206), hump=0.1, head=0.6)
    for x0 in (9, 13, 17, 21):
        line(px, [(x0, 12), (x0 - 2, 19)], (236, 232, 214))
    line(px, [(3, 19), (3, 22)], (90, 90, 84), only_body=False)
    eye(px, 5, 14)
    return finish(im)


def barnacle_bream():
    """藤壺鯛: 背の高い鯛の、背に藤壺がいくつも付いている。灰青の体"""
    im, px = new()
    tail(px, 23, 16, 7, 8, (110, 130, 150), (70, 86, 100))
    spines(px, 10, 20, 9, 3, (100, 118, 136), (60, 72, 86))
    body(px, 14.5, 16, 10.5, 7.2, ramp_of((70, 86, 100), (180, 196, 210)), (226, 232, 236), hump=0.25)
    for x, y in ((11, 11), (14, 10), (17, 11), (19, 13), (13, 13)):
        put(px, x, y, (220, 214, 196))
        put(px, x + 1, y, (190, 182, 166))
        put(px, x, y - 1, (240, 236, 220))
    poly(px, [(11, 22), (15, 22), (13, 26)], (120, 140, 160))
    gill(px, 8, 13, 19, (70, 86, 100))
    eye(px, 5, 14)
    return finish(im)


def ash_herring():
    """灰鰊: 群れで来る細い鰊。銀灰の体、背の青黒い帯"""
    im, px = new()
    tail(px, 23, 16, 6, 6, (120, 130, 140), (80, 90, 100))
    poly(px, [(13, 12), (16, 10), (17, 13)], (90, 100, 110))
    body(px, 14.0, 16, 10.5, 4.4, ramp_of((60, 70, 86), (200, 206, 214)), (236, 238, 240), hump=0.05)
    line(px, [(6, 13), (22, 13)], (40, 50, 70))
    line(px, [(7, 14), (21, 14)], (70, 84, 104))
    dots(px, [(x, 17) for x in range(8, 22, 3)], (250, 250, 250))
    eye(px, 5, 14)
    return finish(im)


def whalebone_eel():
    """鯨髭鰻: 長い鰻。体に鯨のひげのような淡い縞が並ぶ。体はうねる (まれ)"""
    im, px = new()
    for x in range(2, 31):
        cy = 16 + 4 * math.sin((x - 2) / 28 * math.pi * 2)
        th = 2.6 * (1 - max(0, (x - 22) / 9) * 0.7)
        for y in range(32):
            d = y + 0.5 - cy
            if abs(d) <= th:
                t = (d + th) / (2 * th)
                col = (190, 180, 150) if t > 0.75 else mix((50, 46, 40), (130, 120, 96), 1 - t)
                if x % 3 == 0 and t < 0.75:
                    col = (200, 190, 160)
                put(px, x, y, col)
    for x in range(4, 30, 2):
        cy = 16 + 4 * math.sin((x - 2) / 28 * math.pi * 2)
        put(px, x, int(cy - 3.6), (80, 72, 60))
    eye(px, 3, int(16 + 4 * math.sin(1 / 28 * math.pi * 2)) - 1)
    return finish(im)


# ====================================================================== 汐守 (貝と珊瑚の海)
def pearl_bream():
    """真珠鯛: 薄紅の体に、背から腹へ真珠の粒の列。背びれの棘、高い背"""
    im, px = new()
    tail(px, 23, 16, 7, 8, (206, 104, 116), (150, 64, 78))
    for i, x in enumerate(range(10, 21, 2)):
        h = 4 - abs(i - 2) * 0.6
        for y in range(int(9 - h), 10):
            put(px, x, y, (226, 136, 140) if y > 9 - h + 1 else (150, 64, 78))
        put(px, x + 1, 9, (200, 100, 110))
    body(px, 14.5, 16, 10.5, 7.0, ramp_of((176, 82, 96), (244, 186, 180)), (250, 222, 210), hump=0.22)
    poly(px, [(11, 22), (15, 22), (12, 26)], (220, 120, 128))
    for row, (y0, n) in enumerate(((12, 5), (15, 6), (18, 5))):
        for i in range(n):
            x = 10 + i * 2 + row % 2
            y = y0 + (i % 2)
            if has(px, x, y):
                put(px, x, y, (250, 246, 240))
                put(px, x + 1, y + 1, (210, 196, 200))
    gill(px, 8, 12, 18, (150, 64, 78))
    eye(px, 6, 14)
    return finish(im)


def tideshell_puffer():
    """潮殻河豚: ふくらんだ丸い河豚。小さな棘と、貝殻のような渦の模様"""
    im, px = new()
    tail(px, 24, 16, 5, 4, (200, 170, 110), (150, 120, 70), kind="round")
    for y in range(32):
        for x in range(32):
            d = math.hypot(x + 0.5 - 15, y + 0.5 - 16)
            if d <= 9.5:
                t = (y + 0.5 - 6.5) / 19
                put(px, x, y, (248, 236, 210) if t > 0.7 else mix((150, 120, 70), (236, 210, 150), 1 - t * 1.2))
    for a in range(0, 360, 30):
        r = math.radians(a)
        put(px, 15 + math.cos(r) * 10.4, 16 + math.sin(r) * 10.4, (120, 96, 56))
    for k in range(30):
        a = k * 0.5
        r = 1 + k * 0.22
        put(px, 16 + math.cos(a) * r, 15 + math.sin(a) * r, (180, 140, 80))
    poly(px, [(11, 20), (14, 21), (12, 24)], (220, 190, 130))
    eye(px, 8, 13)
    put(px, 5, 17, (120, 70, 60))
    return finish(im)


def kelp_seahorse():
    """藻の竜 (竜の落とし子): 立ち姿。海藻のようなひれが体じゅうに揺れる (まれ)"""
    im, px = new()
    green = ramp_of((40, 90, 50), (150, 200, 110))
    # 頭 (左向き、筒の口)
    poly(px, [(10, 5), (16, 4), (19, 7), (17, 10), (12, 9)], green[2])
    poly(px, [(4, 7), (10, 6), (10, 8), (4, 8)], green[1])
    # 体 (首から腹へ弧、尾は巻く)
    for y in range(9, 26):
        cx = 15 + 3 * math.sin((y - 9) / 16 * math.pi)
        w = 3.4 - abs(y - 16) * 0.12
        for x in range(32):
            if abs(x + 0.5 - cx) <= w:
                put(px, x, y, green[min(3, int((x + 0.5 - cx + w) / (2 * w) * 3.9))])
    for k in range(18):
        a = k * 0.42
        r = 4 - k * 0.18
        put(px, 14 + math.cos(a) * r, 27 + math.sin(a) * r * 0.8, green[1])
    for x0, y0, x1, y1 in ((19, 8, 24, 5), (20, 13, 26, 12), (20, 18, 25, 20), (11, 14, 6, 13), (11, 19, 6, 21)):
        line(px, [(x0, y0), (x1, y1)], (110, 170, 80), only_body=False)
        put(px, x1, y1, (190, 230, 140))
    for y in range(11, 24, 2):
        put(px, 15 + 3 * math.sin((y - 9) / 16 * math.pi), y, green[0])
    eye(px, 13, 6)
    return finish(im)


def coral_parrotfish():
    """珊瑚鸚哥: 背の高い鮮やかな魚。青緑の体に桃色の縁取り、くちばしのような口"""
    im, px = new()
    tail(px, 23, 16, 6, 8, (230, 120, 170), (170, 70, 120), kind="straight")
    body(px, 14.5, 16, 10.5, 7.2, ramp_of((30, 120, 120), (120, 220, 200)), (180, 240, 220), hump=0.2)
    line(px, [(8, 10), (22, 12)], (236, 130, 180))
    line(px, [(8, 22), (21, 20)], (236, 130, 180))
    for y in range(12, 21, 2):
        for x in range(9 + y % 3, 22, 3):
            if has(px, x, y):
                put(px, x, y, (60, 160, 150))
    poly(px, [(3, 14), (6, 13), (6, 17), (3, 16)], (240, 230, 200))
    line(px, [(3, 15), (5, 15)], (170, 150, 120), only_body=False)
    eye(px, 7, 13)
    return finish(im)


def moon_mackerel():
    """月鰆: 細長い鰆。背に月の波のような青い波線、腹は月の銀"""
    im, px = new()
    tail(px, 25, 16, 5, 6, (60, 100, 140), (36, 66, 96))
    for x in (20, 22, 24):
        put(px, x, 12, (60, 100, 140))
        put(px, x, 20, (60, 100, 140))
    body(px, 14.5, 16, 12.0, 4.6, ramp_of((36, 66, 110), (170, 200, 230)), (232, 236, 244), hump=0.06)
    for k in range(3):
        pts = [(x, 12 + k + round(math.sin(x * 0.9 + k) * 0.8)) for x in range(6, 24)]
        line(px, pts, (30, 50, 90))
    gill(px, 7, 13, 18, (36, 66, 110))
    eye(px, 4, 14)
    return finish(im)


def swirl_shark():
    """渦鮫の子: 尖った鼻先と高い背びれの小さな鮫。腹に渦の白い模様 (まれ)"""
    im, px = new()
    poly(px, [(24, 16), (31, 9), (29, 16), (31, 22)], (80, 96, 120))
    poly(px, [(12, 11), (16, 4), (18, 11)], (70, 86, 110))
    body(px, 14.0, 16, 12.5, 5.2, ramp_of((60, 74, 100), (150, 168, 190)), (230, 234, 240), hump=0.05, head=1.6, taper=1.0)
    poly(px, [(10, 19), (15, 21), (12, 25)], (90, 106, 130))
    for k in range(14):
        a = k * 0.55
        r = 0.6 + k * 0.18
        put(px, 15 + math.cos(a) * r * 1.6, 19 + math.sin(a) * r * 0.5, (180, 190, 210))
    for x in (8, 9, 10):
        put(px, x, 15, (40, 50, 70))
    eye(px, 4, 14, ring=(200, 210, 220))
    return finish(im)


# ====================================================================== 風待 (高い山と雷)
def thunder_char():
    """雷紋岩魚: 山の岩魚。背に稲妻の折れ線の模様が淡く光る"""
    im, px = new()
    tail(px, 24, 16, 6, 6, (90, 100, 120), (56, 64, 80), kind="straight")
    poly(px, [(12, 11), (18, 9), (19, 12)], (80, 90, 110))
    body(px, 14.5, 16, 11.0, 5.6, ramp_of((50, 60, 80), (150, 160, 180)), (236, 220, 180), hump=0.1)
    line(px, [(7, 13), (10, 16), (13, 12), (16, 16), (19, 12), (22, 15)], (250, 230, 120))
    dots(px, [(x, 18) for x in range(9, 22, 3)], (240, 160, 90))
    gill(px, 8, 13, 18, (50, 60, 80))
    eye(px, 5, 14)
    return finish(im)


def cloud_trout():
    """雲鱒: 雲の上の湖の鱒。白と淡い灰の体に、雲のような柔らかいまだら"""
    im, px = new()
    tail(px, 24, 16, 6, 7, (200, 206, 216), (140, 150, 166))
    poly(px, [(12, 11), (18, 8), (19, 12)], (180, 186, 200))
    body(px, 14.5, 16, 11.0, 6.0, ramp_of((150, 160, 176), (246, 248, 252)), (252, 252, 255), hump=0.1)
    for cx, cy in ((11, 13), (16, 14), (20, 13), (13, 17)):
        for dx, dy in ((0, 0), (1, 0), (-1, 0), (0, -1), (1, -1)):
            if has(px, cx + dx, cy + dy):
                put(px, cx + dx, cy + dy, (214, 220, 232))
    gill(px, 8, 13, 18, (150, 160, 176))
    eye(px, 5, 14, ring=(220, 230, 250), pupil=(60, 80, 120))
    return finish(im)


def pass_yamame():
    """峠山女: 峠の谷の山女。体の横に並ぶ楕円の斑 (パーマーク) と小さな黒点"""
    im, px = new()
    tail(px, 23, 16, 6, 6, (130, 140, 110), (86, 96, 70))
    poly(px, [(12, 11), (17, 9), (18, 12)], (110, 120, 90))
    body(px, 14.0, 16, 10.5, 5.4, ramp_of((80, 96, 76), (200, 206, 180)), (240, 236, 220), hump=0.1)
    for cx in (9, 12, 15, 18, 21):
        for dy in (-1, 0, 1):
            for dx in (0, 1):
                if has(px, cx + dx, 16 + dy):
                    put(px, cx + dx, 16 + dy, (90, 110, 120))
    dots(px, [(10, 12), (14, 11), (18, 12), (12, 13), (20, 13)], (30, 30, 30))
    eye(px, 5, 14)
    return finish(im)


def lightning_eel():
    """稲妻鰻: 黄色く光る鰻。体に沿って稲妻の筋が走る (まれ)"""
    im, px = new()
    for x in range(2, 31):
        cy = 16 + 3.5 * math.sin((x - 2) / 28 * math.pi * 2.5)
        th = 2.4 * (1 - max(0, (x - 22) / 9) * 0.6)
        for y in range(32):
            d = y + 0.5 - cy
            if abs(d) <= th:
                t = (d + th) / (2 * th)
                put(px, x, y, mix((60, 50, 20), (240, 210, 70), 1 - t * 0.8))
    pts = []
    for i, x in enumerate(range(3, 30, 2)):
        cy = 16 + 3.5 * math.sin((x - 2) / 28 * math.pi * 2.5)
        pts.append((x, round(cy + (-1 if i % 2 else 1))))
    line(px, pts, (255, 252, 210))
    eye(px, 3, int(16 + 3.5 * math.sin(1 / 28 * math.pi * 2.5)) - 1, ring=(255, 250, 200))
    return finish(im)


def featherfin_flyingfish():
    """羽鰭飛魚: 大きな羽のような胸びれで風に乗る飛び魚。青い背と、羽の縞"""
    im, px = new()
    tail(px, 24, 17, 6, 6, (70, 110, 170), (40, 70, 120))
    poly(px, [(10, 15), (16, 3), (22, 5), (15, 15)], (180, 210, 240))
    for k in range(4):
        line(px, [(12 + k * 2, 14), (16 + k * 2, 5)], (110, 150, 200), only_body=False)
    body(px, 14.0, 17, 10.5, 4.4, ramp_of((40, 70, 120), (170, 200, 230)), (236, 240, 248), hump=0.05)
    poly(px, [(12, 20), (17, 21), (15, 26)], (150, 180, 220))
    eye(px, 5, 15)
    return finish(im)


def glacier_icefish():
    """氷白魚: 氷河の湖の、透きとおった細い魚。中の骨が透けて見える"""
    im, px = new()
    tail(px, 24, 16, 5, 5, (200, 226, 240, 200), (140, 176, 200))
    for y in range(32):
        for x in range(32):
            u = (x + 0.5 - 14.5) / 11.0
            if abs(u) >= 1:
                continue
            h = math.sqrt(1 - u * u) ** (0.8 if u < 0 else 1.25)
            if abs(y + 0.5 - 16) <= 4.4 * h:
                put(px, x, y, (210, 232, 246, 210))
    line(px, [(6, 16), (23, 16)], (150, 180, 206))
    for x in range(8, 23, 2):
        line(px, [(x, 13), (x + 1, 16), (x, 19)], (170, 198, 220))
    eye(px, 5, 14, ring=(240, 250, 255), pupil=(40, 60, 90))
    return finish(im)


# ====================================================================== 灰炉 (火の山と湯の泉)
def ember_snapper():
    """熾火鯛: 背の高い赤い鯛。体のひびから熾火が光る"""
    im, px = new()
    tail(px, 23, 16, 7, 8, (170, 60, 40), (110, 34, 24))
    spines(px, 10, 20, 9, 3, (150, 50, 36), (90, 28, 20))
    body(px, 14.5, 16, 10.5, 7.2, ramp_of((100, 30, 22), (210, 90, 60)), (230, 160, 120), hump=0.22)
    line(px, [(9, 12), (12, 15), (11, 19)], (255, 190, 80))
    line(px, [(15, 11), (16, 14), (19, 16), (18, 20)], (255, 170, 60))
    line(px, [(20, 13), (22, 15)], (255, 200, 100))
    gill(px, 8, 13, 19, (90, 28, 20))
    eye(px, 5, 14, ring=(255, 220, 140))
    return finish(im)


def ash_catfish():
    """灰鯰: 灰の川の鯰。灰をかぶった白灰の体に、黒い煤の帯と長いひげ"""
    im, px = new()
    tail(px, 24, 17, 6, 5, (150, 146, 140), (100, 96, 92), kind="round")
    body(px, 15.0, 17, 11.5, 5.2, ramp_of((96, 92, 88), (210, 206, 200)), (226, 222, 216), hump=-0.05, head=0.45)
    for x0 in (10, 15, 20):
        line(px, [(x0, 13), (x0 + 1, 20)], (50, 46, 44))
    for (x0, y0), (x1, y1) in (((4, 17), (0, 22)), ((5, 16), (1, 13)), ((5, 18), (3, 25))):
        line(px, [(x0, y0), (x1, y1)], (80, 76, 72), only_body=False)
    eye(px, 6, 15, ring=(220, 200, 160))
    return finish(im)


def hotspring_goldfish():
    """湯泉金魚: 湯の泉の丸い金魚。ひらひらの長い尾 (薄い朱)"""
    im, px = new()
    for y in range(32):
        for x in range(32):
            dx = x + 0.5 - 19
            dy = y + 0.5 - 16
            if 0 <= dx <= 12 and abs(dy) <= 3 + dx * 0.75 and not (dx > 6 and abs(dy) < (dx - 6) * 0.5):
                put(px, x, y, (250, 170, 120, 200) if abs(dy) > 1 + dx * 0.6 else (250, 140, 90, 220))
    for y in range(32):
        for x in range(32):
            d = math.hypot((x + 0.5 - 13) / 7.5, (y + 0.5 - 16) / 7.0)
            if d <= 1:
                t = (y + 0.5 - 9) / 14
                put(px, x, y, (255, 230, 190) if t > 0.72 else mix((210, 70, 30), (255, 150, 80), 1 - t))
    poly(px, [(11, 9), (16, 6), (18, 10)], (230, 100, 50))
    poly(px, [(11, 21), (14, 22), (12, 26)], (250, 140, 90))
    eye(px, 8, 13)
    return finish(im)


def obsidian_spinefish():
    """黒曜棘魚: 黒曜石のような黒い体に紫の照り。背に鋭い棘が並ぶ"""
    im, px = new()
    tail(px, 23, 16, 7, 7, (50, 40, 60), (24, 20, 30))
    for i, x in enumerate(range(9, 22, 2)):
        h = 6 - abs(i - 3) * 1.0
        line(px, [(x, 11), (x + 1, int(11 - h))], (70, 56, 90), only_body=False)
        put(px, x + 1, int(11 - h), (190, 150, 230))
    body(px, 14.5, 16, 10.5, 6.0, ramp_of((20, 18, 26), (90, 70, 120)), (60, 50, 80), hump=0.12)
    line(px, [(9, 13), (20, 12)], (180, 140, 230))
    dots(px, [(12, 16), (16, 15), (19, 17)], (150, 120, 200))
    eye(px, 5, 14, ring=(230, 120, 120))
    return finish(im)


def firetail():
    """炎尾: 尾が炎の形にゆらめく魚。尾の先ほど黄色く光る"""
    im, px = new()
    for y in range(32):
        for x in range(32):
            dx = x + 0.5 - 21
            dy = y + 0.5 - 16
            if 0 <= dx <= 10:
                wave = math.sin(dx * 0.9 + (2 if dy < 0 else 0)) * 1.5
                if abs(dy) <= 2 + dx * 0.7 + wave and not (dx > 5 and abs(dy) < (dx - 5) * 0.4):
                    put(px, x, y, mix((220, 60, 20), (255, 230, 90), dx / 10))
    poly(px, [(11, 11), (16, 9), (18, 12)], (180, 60, 30))
    body(px, 13.0, 16, 9.5, 5.4, ramp_of((110, 40, 30), (220, 110, 70)), (240, 190, 150), hump=0.1)
    gill(px, 7, 13, 18, (110, 40, 30))
    eye(px, 5, 14, ring=(255, 220, 150))
    return finish(im)


def flame_dragonfish():
    """焔の竜魚: 長い体に大きな鱗とあごのひげ二本。鱗の縁が火の色に光る (まれ)"""
    im, px = new()
    tail(px, 25, 16, 6, 6, (180, 70, 30), (120, 40, 20), kind="round")
    poly(px, [(12, 11), (24, 10), (24, 13), (13, 12)], (160, 60, 30))
    poly(px, [(14, 21), (24, 20), (24, 22), (14, 22)], (160, 60, 30))
    body(px, 14.5, 16, 12.0, 5.0, ramp_of((90, 30, 20), (200, 90, 50)), (230, 170, 110), hump=0.0, head=0.7, taper=0.9)
    for y in range(12, 21, 3):
        for x in range(6 + (y % 2) * 2, 25, 4):
            if has(px, x, y):
                put(px, x, y, (255, 190, 80))
                put(px, x + 1, y, (255, 150, 60))
    line(px, [(3, 18), (1, 23)], (255, 170, 60), only_body=False)
    line(px, [(4, 18), (4, 24)], (255, 170, 60), only_body=False)
    eye(px, 5, 14, ring=(255, 230, 160))
    return finish(im)


# ====================================================================== どの土地でもごくまれ
def dream_fish():
    """巨神の夢を見た魚: 深い藍の体に、巨神の背の稜線のような淡い金の筋と星の粒。尾は光の帯にほどける"""
    im, px = new()
    for x in range(23, 31):
        for y in range(8, 25):
            dy = abs(y + 0.5 - 16)
            w = 2 + (x - 23) * 0.95
            if dy <= w:
                put(px, x, y, (200, 225, 255, 200 - (x - 23) * 20) if dy > w - 1.2 else (150, 190, 250, 170 - (x - 23) * 18))
    tail(px, 22, 16, 5, 6, (70, 96, 170), (40, 52, 110))
    poly(px, [(11, 10), (20, 7), (21, 11), (13, 12)], (60, 78, 146))
    body(px, 14.0, 16, 10.0, 6.4, ramp_of((28, 34, 78), (90, 112, 180)), (110, 130, 190), hump=0.15)
    poly(px, [(11, 21), (15, 21), (13, 25)], (70, 96, 170))
    line(px, [(8, 13), (10, 12), (12, 13), (14, 11), (16, 12), (18, 11), (20, 13)], (240, 208, 120))
    dots(px, [(11, 16), (15, 18), (18, 15), (13, 19), (20, 17), (9, 18)], (240, 244, 255))
    gill(px, 7, 13, 18, (28, 34, 78))
    eye(px, 5, 14, ring=(255, 236, 170), pupil=(28, 20, 60))
    return finish(im)


# ====================================================================== 道具
def fishing_rod():
    """釣り竿: 左下の握り (コルク) から右上へしなる竹の竿、真ん中に真鍮のリール、先から垂れる糸と赤白の浮き"""
    im, px = new()
    pts = [(4, 28), (8, 24), (13, 18), (18, 12), (23, 7), (27, 4), (29, 3)]
    for (x0, y0), (x1, y1) in zip(pts, pts[1:]):
        n = max(abs(x1 - x0), abs(y1 - y0))
        for t in range(n + 1):
            x = round(x0 + (x1 - x0) * t / n)
            y = round(y0 + (y1 - y0) * t / n)
            put(px, x, y, (190, 160, 90))
            put(px, x + 1, y, (130, 104, 56))
    for k in range(0, 18, 4):
        put(px, 9 + k, 23 - k, (90, 70, 40))
    for x, y in ((3, 29), (4, 28), (5, 27), (6, 26)):
        put(px, x, y, (200, 150, 100))
        put(px, x + 1, y, (160, 116, 72))
    for dx, dy in ((0, 0), (1, 0), (0, 1), (1, 1), (-1, 0), (0, -1)):
        put(px, 9 + dx, 25 + dy, (210, 170, 80))
    put(px, 10, 26, (130, 100, 40))
    line(px, [(29, 3), (29, 14)], (220, 220, 230), only_body=False)
    for y, c in ((15, (230, 60, 50)), (16, (230, 60, 50)), (17, (245, 245, 240)), (18, (245, 245, 240))):
        put(px, 28, y, c)
        put(px, 29, y, c)
        put(px, 30, y, c)
    return finish(im)


TOOLS = [("fishing_rod", "釣り竿", "道具屋", "C", fishing_rod)]

# (id, 名前, 土地, まれさ C/U/R/L, 絵)
FISH = [
    ("crucian", "小鮒", "都のまわり", "C", crucian), ("sweetfish", "銀鮎", "都のまわり", "C", sweetfish),
    ("bell_carp", "鐘鳴り鯉", "都のまわり", "U", bell_carp), ("mud_catfish", "泥鯰", "都のまわり", "C", mud_catfish),
    ("striped_dace", "縞鮠", "都のまわり", "C", striped_dace), ("mill_trout", "水車鱒", "都のまわり", "U", mill_trout),
    ("moss_sculpin", "苔鰍", "鯨見", "C", moss_sculpin), ("songstone_flounder", "歌石鰈", "鯨見", "U", songstone_flounder),
    ("bone_cod", "骨鱈", "鯨見", "C", bone_cod), ("barnacle_bream", "藤壺鯛", "鯨見", "U", barnacle_bream),
    ("ash_herring", "灰鰊", "鯨見", "C", ash_herring), ("whalebone_eel", "鯨髭鰻", "鯨見", "R", whalebone_eel),
    ("pearl_bream", "真珠鯛", "汐守", "U", pearl_bream), ("tideshell_puffer", "潮殻河豚", "汐守", "U", tideshell_puffer),
    ("kelp_seahorse", "藻の竜", "汐守", "R", kelp_seahorse), ("coral_parrotfish", "珊瑚鸚哥", "汐守", "C", coral_parrotfish),
    ("moon_mackerel", "月鰆", "汐守", "C", moon_mackerel), ("swirl_shark", "渦鮫の子", "汐守", "R", swirl_shark),
    ("thunder_char", "雷紋岩魚", "風待", "U", thunder_char), ("cloud_trout", "雲鱒", "風待", "C", cloud_trout),
    ("pass_yamame", "峠山女", "風待", "C", pass_yamame), ("lightning_eel", "稲妻鰻", "風待", "R", lightning_eel),
    ("featherfin_flyingfish", "羽鰭飛魚", "風待", "U", featherfin_flyingfish), ("glacier_icefish", "氷白魚", "風待", "C", glacier_icefish),
    ("ember_snapper", "熾火鯛", "灰炉", "U", ember_snapper), ("ash_catfish", "灰鯰", "灰炉", "C", ash_catfish),
    ("hotspring_goldfish", "湯泉金魚", "灰炉", "C", hotspring_goldfish), ("obsidian_spinefish", "黒曜棘魚", "灰炉", "U", obsidian_spinefish),
    ("firetail", "炎尾", "灰炉", "C", firetail), ("flame_dragonfish", "焔の竜魚", "灰炉", "R", flame_dragonfish),
    ("dream_fish", "巨神の夢を見た魚", "どこでも", "L", dream_fish),
]
RARITY = {"C": ("ふつう", (200, 200, 200)), "U": ("ときどき", (120, 220, 120)), "R": ("まれ", (110, 170, 255)), "L": ("ごくまれ", (242, 193, 78))}


def main():
    install = sys.argv[1:2] == ["install"]
    (OUT / "icons").mkdir(parents=True, exist_ok=True)
    ims = [(fid, name, land, rar, fn()) for fid, name, land, rar, fn in FISH]
    tools = [(fid, name, land, rar, fn()) for fid, name, land, rar, fn in TOOLS]
    if install:
        for fid, _, _, _, im in ims:
            im.save(ITEM / f"fish_{fid}.png")
        for fid, _, _, _, im in tools:
            im.save(ITEM / f"{fid}.png")
        print("installed", len(ims), "fish icons and", len(tools), "tools")
        return
    ims = ims + tools
    k = 4
    cols = 8
    cw, ch = 32 * k + 24, 32 * k + 60
    rows = math.ceil(len(ims) / cols)
    sheet = Image.new("RGB", (cols * cw + 24, rows * ch + 50), (24, 26, 34))
    d = ImageDraw.Draw(sheet)
    font = ImageFont.truetype(str(FONT), 15)
    small = ImageFont.truetype(str(FONT), 12)
    d.text((16, 14), f"釣りの魚 {len(FISH)} 種と釣り竿 (32×32 を 4 倍。五つの土地に 6 種ずつと、ごくまれな一匹)", font=font, fill=(242, 193, 78))
    for i, (fid, name, land, rar, im) in enumerate(ims):
        im.save(OUT / "icons" / f"{fid}.png")
        x = 12 + (i % cols) * cw + 12
        y = 44 + (i // cols) * ch
        d.rectangle([x - 2, y - 2, x + 32 * k + 1, y + 32 * k + 1], fill=(34, 38, 48), outline=(70, 76, 90))
        big = im.resize((32 * k, 32 * k), Image.NEAREST)
        sheet.paste(big, (x, y), big)
        d.text((x, y + 32 * k + 6), name, font=font, fill=(236, 232, 214))
        label, col = RARITY[rar]
        d.text((x, y + 32 * k + 26), land if fid == "fishing_rod" else f"{land}・{label}", font=small, fill=(242, 193, 78) if fid == "fishing_rod" else col)
    sheet.save(OUT / "fish_all.png")
    print("wrote", OUT / "fish_all.png")


if __name__ == "__main__":
    main()
