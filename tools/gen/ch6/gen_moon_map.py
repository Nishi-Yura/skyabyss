# -*- coding: utf-8 -*-
"""月の旅の地図 (M) に敷く、描いた地図の絵 (docs/design/46 の 3.6。2026-10-28 の試遊「月行ったらマップ変わる様にしてほしい」)。

  書くもの: src/main/resources/assets/skyabyss/textures/gui/map/moon.png (512 x 512 で、月の x -256..256、z -128..384)
  読むもの: data/skyabyss/moon/valley.json (新月の谷の柱。上から見た高さと色)
  絵: 灰青の紙に、月の砂の明るい地、着く所のクレーターの輪、谷の深さの陰と等高線、谷の門と見どころの印。
  地図の外は透かして、地図の盤の地になじませる (大陸の continent.png と同じ作り)。

  java 側: client/map/TravelMapScreen の drawMoon (MOON_X0 / MOON_Z0 / MOON_SIZE と合わせる)
"""
import json
import math
import os

import numpy as np
from PIL import Image, ImageDraw, ImageFilter

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..', '..'))
VALLEY = os.path.join(ROOT, 'src', 'main', 'resources', 'data', 'skyabyss', 'moon', 'valley.json')
OUT = os.path.join(ROOT, 'src', 'main', 'resources', 'assets', 'skyabyss', 'textures', 'gui', 'map', 'moon.png')

X0, Z0, SIZE = -256, -128, 512
SURFACE = 11
PAPER = np.array([214, 216, 222], dtype=float)
DUST = np.array([196, 198, 204], dtype=float)
DEEP = np.array([92, 98, 120], dtype=float)
INK = (52, 56, 76)


def tops():
    """谷の柱ごとの床の高さ。runs は [y, z, x0, (色, 横の数)...] (build_moon_valley.py)。
    地面 (SURFACE) より下に空気がある柱は谷: いちばん下の空気の下の床。ない柱は上面"""
    d = json.load(open(VALLEY, encoding='utf-8'))
    pal = d['palette']
    low_air = np.full((SIZE, SIZE), 10 ** 6)
    top = np.full((SIZE, SIZE), -10 ** 6)
    for r in d['runs']:
        y, z, x = r[0], r[1], r[2]
        j = z - Z0
        for k in range(3, len(r), 2):
            b, n = r[k], r[k + 1]
            for xx in range(x, x + n):
                i = xx - X0
                if not (0 <= i < SIZE and 0 <= j < SIZE):
                    continue
                if pal[b] == 'minecraft:air':
                    if y <= SURFACE:
                        low_air[j, i] = min(low_air[j, i], y)
                else:
                    top[j, i] = max(top[j, i], y)
            x += n
    h = np.full((SIZE, SIZE), float(SURFACE))
    h = np.where(top > -10 ** 6, top, h)
    h = np.where(low_air < 10 ** 6, low_air - 1, h)
    return h.astype(float), d['marks'], d['rect']


def craters(h):
    """着く所のまわりのクレーター (MoonWorld.landing と同じ広がり。位置は絵のための固定の乱数)"""
    rng = np.random.default_rng(0x4D4F)
    out = [(0, 0, 9)]
    for k in range(26):
        a = rng.random() * math.pi * 2
        dist = 16 + rng.random() * 56
        out.append((math.cos(a) * dist, math.sin(a) * dist, 3 + rng.integers(0, 9 if k < 4 else 5)))
    for k in range(40):
        # 遠くの小さなクレーター (谷にかからない所)
        x, z = rng.uniform(-240, 240), rng.uniform(-110, 370)
        if -60 < x < 60 and 10 < z < 190:
            continue
        out.append((x, z, rng.integers(3, 12)))
    yy, xx = np.mgrid[0:SIZE, 0:SIZE]
    for cx, cz, rad in out:
        dd = np.hypot(xx + X0 - cx, yy + Z0 - cz) / rad
        dig = np.where(dd < 1, max(2, rad / 2) * (1 - dd * dd), 0)
        rim = np.where((dd >= 1) & (dd < 1.35), (1.35 - dd) / 0.35 * min(3, max(1, rad / 3)), 0)
        h -= dig
        h += rim
    return out


def main():
    h, marks, rect = tops()
    rings = craters(h)
    # 色: 高い所は砂、低い所 (谷) ほど深い青
    t = np.clip((SURFACE + 2 - h) / 30, 0, 1)[..., None]
    col = DUST * (1 - t) + DEEP * t
    gz, gx = np.gradient(h)
    shade = np.clip(1.0 - (gx + gz) * 0.06, 0.6, 1.3)[..., None]
    col = col * shade
    col = col * 0.82 + PAPER * 0.18
    rng = np.random.default_rng(7)
    col += rng.normal(0, 4, (SIZE, SIZE, 1))
    img = Image.fromarray(np.clip(col, 0, 255).astype(np.uint8))
    # 谷の等高線 (8 ごと)
    band = np.floor((h - SURFACE) / 8).astype(int)
    edges = (np.abs(np.diff(band, axis=0, prepend=band[:1])) + np.abs(np.diff(band, axis=1, prepend=band[:, :1]))) > 0
    img.paste(Image.new('RGB', img.size, (70, 74, 96)), mask=Image.fromarray((edges * 120).astype(np.uint8)))
    d = ImageDraw.Draw(img)

    def px(x, z):
        return (x - X0, z - Z0)

    # クレーターの縁の線
    for cx, cz, rad in rings:
        a, b = px(cx, cz)
        d.ellipse([a - rad, b - rad, a + rad, b + rad], outline=(120, 124, 140))
    # 着く所 → 谷の門の踏み跡 (点線)
    gate = marks.get('valley_gate', [0, 0, 90])
    pts = [px(0, 3), px(0, 20), px(-6, 48), px(gate[0], gate[2])]
    for (x0, y0), (x1, y1) in zip(pts, pts[1:]):
        n = max(1, int(math.hypot(x1 - x0, y1 - y0) / 4))
        for i in range(0, n, 2):
            a0, a1 = i / n, min(1, (i + 1) / n)
            d.line([(x0 + (x1 - x0) * a0, y0 + (y1 - y0) * a0), (x0 + (x1 - x0) * a1, y0 + (y1 - y0) * a1)], fill=INK, width=2)
    # 印: 着く所 (二重の輪)、野営の跡、谷の門、主の間
    a, b = px(0, 0)
    d.ellipse([a - 5, b - 5, a + 5, b + 5], outline=INK, width=2)
    d.ellipse([a - 2, b - 2, a + 2, b + 2], fill=INK)
    for key, r in (('camp', 3), ('valley_gate', 3), ('arena_center', 6)):
        if key in marks:
            a, b = px(marks[key][0], marks[key][2])
            d.ellipse([a - r, b - r, a + r, b + r], outline=(250, 236, 180), width=2)
    # 外は透かす (着く所と谷から離れるほど)
    yy, xx = np.mgrid[0:SIZE, 0:SIZE]
    wx, wz = xx + X0, yy + Z0
    near = np.minimum(np.hypot(wx, wz) - 120, np.hypot(np.maximum(0, np.abs(wx) - 60), np.maximum(0, np.maximum(rect[1] - wz, wz - rect[3]))) - 60)
    alpha = np.clip(1 - near / 90, 0, 1)
    a = Image.fromarray((alpha * 255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(3))
    img = img.convert('RGBA')
    img.putalpha(a)
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    img.save(OUT)
    print('wrote', OUT)


if __name__ == '__main__':
    main()
