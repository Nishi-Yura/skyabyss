# -*- coding: utf-8 -*-
"""弱り (倒れたあと 2 分。combat/DeathWeakness) の効き目の絵 18x18。
ひびの入った、色の抜けた心臓。ほかの効き目の絵 (避雷・出血・研いだ刃) と同じく、暗い縁取りと左上からの光。

出力: src/main/resources/assets/skyabyss/textures/mob_effect/weakened.png
何度流しても同じ。
"""
import os

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__)))))
OUT = os.path.join(ROOT, 'src', 'main', 'resources', 'assets', 'skyabyss', 'textures', 'mob_effect', 'weakened.png')

N = 18
OUTLINE = (36, 29, 43, 255)
LIGHT = (176, 160, 186, 255)
MID = (128, 112, 141, 255)
SHADE = (88, 75, 101, 255)
CRACK = (44, 34, 52, 255)


def heart(x, y):
    # ハートの式 (x^2 + y^2 - 1)^3 - x^2 y^3 <= 0 を、上下左右に 1〜2 升の余白を残して 18 の升目に合わせる
    u = (x - 8.5) / 6.4
    v = -(y - 9.2) / 6.0
    return (u * u + v * v - 1) ** 3 - u * u * v ** 3 <= 0


def main():
    mask = [[heart(x, y) for x in range(N)] for y in range(N)]
    img = Image.new('RGBA', (N, N), (0, 0, 0, 0))
    px = img.load()
    for y in range(N):
        for x in range(N):
            if not mask[y][x]:
                continue
            edge = any(not (0 <= x + dx < N and 0 <= y + dy < N and mask[y + dy][x + dx]) for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))
            if edge:
                px[x, y] = OUTLINE
                continue
            # 左上ほど明るく、右下ほど暗い
            t = (x - 3) * 0.55 + (y - 3) * 0.7
            px[x, y] = LIGHT if t < 3 else MID if t < 9.5 else SHADE
    # ひび: 上のくぼみから先の尖りへ、左右に振れながら
    crack = [(9, 4), (9, 5), (8, 6), (8, 7), (9, 8), (10, 9), (9, 10), (9, 11), (8, 12), (9, 13), (9, 14)]
    for x, y in crack:
        if mask[y][x]:
            px[x, y] = CRACK
    # 割れ目の右の縁に細い光 (欠けて見えるように)
    for x, y in ((10, 6), (9, 7), (11, 10), (10, 11)):
        if mask[y][x] and px[x, y] != OUTLINE:
            px[x, y] = LIGHT
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    img.save(OUT)
    print('weakened icon ->', OUT)


if __name__ == '__main__':
    main()
