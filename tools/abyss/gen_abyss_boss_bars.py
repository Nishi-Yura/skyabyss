# -*- coding: utf-8 -*-
"""深淵の主たちの段のあるボスバーの絵 (枠 182x16 と顔の印 16x16、段ごとに 3 枚)。PhasedBossBar と同じ寸法 (溝は x 3〜177、y 3〜8)。

python -I tools/abyss/gen_abyss_boss_bars.py
出力: src/main/resources/assets/skyabyss/textures/gui/boss/<型>_frame_p1..3.png・<型>_icon_p1..3.png
      型 = abyss_lord_bone・abyss_lord_salt・abyss_lord_storm・abyss_lord_ash・abyss_elite・abyss_king・abyss_warden
描くのは story/ch5/client/AbyssBossBarClient (帯の色はゲームの側で塗る。ここは枠と印だけ)
段: 一段目は層の色の落ち着いた縁、二段目は縁が明るく刻みが増え、三段目は縁に赤い亀裂と両端の目 (怒り)
"""
from pathlib import Path

import numpy as np
from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / 'src/main/resources/assets/skyabyss/textures/gui/boss'
W, H = 182, 16
DARK = (10, 12, 18)
GROOVE = (18, 20, 28)
RED = (220, 50, 50)

# 型 → (外の縁, 縁, 内の線, 飾りの色, 印の形)
KINDS = {
    'abyss_lord_bone': ((40, 44, 56), (200, 196, 180), (120, 190, 230), (236, 232, 216), 'rib'),
    'abyss_lord_salt': ((30, 52, 58), (210, 230, 228), (90, 200, 190), (250, 252, 250), 'shell'),
    'abyss_lord_storm': ((54, 44, 30), (210, 160, 100), (250, 220, 90), (255, 248, 210), 'bolt'),
    'abyss_lord_ash': ((48, 26, 20), (180, 90, 50), (250, 140, 60), (255, 210, 150), 'flame'),
    'abyss_elite': ((40, 36, 52), (160, 150, 190), (200, 170, 230), (236, 228, 250), 'fang'),
    'abyss_king': ((44, 30, 54), (170, 130, 200), (220, 120, 190), (250, 230, 250), 'crown'),
    'abyss_warden': ((26, 30, 40), (110, 140, 160), (150, 110, 210), (220, 230, 240), 'eye'),
}
MARKS = [round(3 + (178 - 3) * f) for f in (0.33, 0.66)]


def lerp(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))


def frame(kind, ph):
    outer, rim, inner, deco, _ = KINDS[kind]
    rim = lerp(rim, (255, 255, 255), 0.15 * (ph - 1))
    a = np.zeros((H, W, 4), np.uint8)

    def px(x, y, c):
        if 0 <= x < W and 0 <= y < H:
            a[y, x] = (*c, 255)

    for y in range(12):
        for x in range(W):
            edge = min(y, 11 - y, x, W - 1 - x)
            px(x, y, outer if edge == 0 else rim if edge == 1 else DARK if edge == 2 else GROOVE)
    for corner in ((0, 0), (W - 1, 0), (0, 11), (W - 1, 11)):
        a[corner[1], corner[0]] = (0, 0, 0, 0)
    for x in range(3, W - 3):
        px(x, 2, inner)
    # 上の縁の刻み (段が進むほど細かく)
    step = {1: 16, 2: 10, 3: 7}[ph]
    for x in range(6, W - 6, step):
        px(x, 0, deco)
        px(x + 1, 0, deco)
    # 三段目: 赤い亀裂と両端の目
    if ph == 3:
        for x0 in range(20, W - 20, 37):
            for k, (dx, dy) in enumerate([(0, 0), (1, 1), (2, 1), (3, 2), (4, 1)]):
                px(x0 + dx, 10 - dy, RED)
        for x0 in (1, W - 6):
            for dx, dy in [(1, 4), (2, 3), (3, 3), (4, 4), (1, 6), (2, 7), (3, 7), (4, 6)]:
                px(x0 + dx, dy, deco)
            px(x0 + 2, 5, RED)
            px(x0 + 3, 5, RED)
    # 段の境の印 (下の 12〜15 行)
    for mx in MARKS:
        for dx, y in [(0, 12), (-1, 13), (0, 13), (1, 13), (0, 14)]:
            px(mx + dx, y, inner if ph < 3 else RED)
        px(mx, 9, inner)
    return Image.fromarray(a, 'RGBA')


def icon(kind, ph):
    outer, rim, inner, deco, shape = KINDS[kind]
    a = np.zeros((16, 16, 4), np.uint8)

    def px(x, y, c):
        if 0 <= x < 16 and 0 <= y < 16:
            a[y, x] = (*c, 255)

    # 丸い台 (暗い石)、縁は段で明るく
    ring = lerp(rim, (255, 255, 255), 0.2 * (ph - 1))
    for y in range(16):
        for x in range(16):
            d = ((x - 7.5) ** 2 + (y - 7.5) ** 2) ** 0.5
            if d <= 7.6:
                px(x, y, ring if d > 6.4 else DARK)
    c = deco if ph < 3 else lerp(deco, RED, 0.35)
    if shape == 'rib':
        for y in (5, 8, 11):
            for x in range(4, 12):
                if abs(x - 7.5) > 1:
                    px(x, y - (1 if abs(x - 7.5) > 3 else 0), c)
        for y in range(4, 13):
            px(7, y, c)
            px(8, y, c)
    elif shape == 'shell':
        for y in range(4, 12):
            for x in range(4, 12):
                if ((x - 7.5) ** 2 / 14 + (y - 9) ** 2 / 20) <= 1 and y <= 11:
                    px(x, y, c if (x % 2 == 0 or y == 11) else inner)
    elif shape == 'bolt':
        for (x, y) in [(9, 3), (8, 4), (8, 5), (7, 6), (6, 7), (7, 7), (8, 7), (9, 7), (8, 8), (7, 9), (7, 10), (6, 11), (6, 12)]:
            px(x, y, c)
            px(x + 1, y, inner)
    elif shape == 'flame':
        for y in range(4, 13):
            w = max(0, int((y - 3) * 0.6)) if y < 10 else 12 - y + 2
            for x in range(8 - w, 8 + w):
                px(x, y, c if abs(x - 7.5) < w - 1 else inner)
    elif shape == 'fang':
        for y in range(4, 12):
            for x in (5, 10):
                if y < 12 - abs(y - 4) // 3:
                    px(x, y, c)
                    px(x + (1 if x == 5 else -1), y - 1, c)
    elif shape == 'crown':
        for x in range(4, 12):
            px(x, 10, c)
            px(x, 11, c)
        for x in (4, 7, 8, 11):
            for y in range(5, 10):
                if y >= 5 + (0 if x in (7, 8) else 1):
                    px(x, y, c)
        px(7, 4, RED)
        px(8, 4, RED)
    else:  # eye
        for x in range(3, 13):
            dy = int(2.5 - abs(x - 7.5) * 0.45)
            px(x, 8 - dy, c)
            px(x, 8 + dy, c)
        for (x, y) in [(7, 7), (8, 7), (7, 8), (8, 8)]:
            px(x, y, RED if ph >= 2 else inner)
    return Image.fromarray(a, 'RGBA')


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    n = 0
    for kind in KINDS:
        for ph in (1, 2, 3):
            frame(kind, ph).save(OUT / ('%s_frame_p%d.png' % (kind, ph)))
            icon(kind, ph).save(OUT / ('%s_icon_p%d.png' % (kind, ph)))
            n += 2
    print('wrote', n, 'images to', OUT.relative_to(ROOT))


if __name__ == '__main__':
    main()
