#!/usr/bin/env python3
"""試練済みの封印 (trial_seal) のブロックの絵 (32x32)・モデル・状態・アイテムモデルを書く (docs/design/29 の 4.6、版 22)。
越えた試練の入口に、そのグループにだけ立つ封印。暗い玄武岩の板に、金の輪と、閉じた目 (まぶたの線と一つの点) の紋。
リポジトリ直下で python tools/gen_trial_seal.py 。何度流しても同じ。"""
import json, os, random
from PIL import Image

A = 'src/main/resources/assets/skyabyss'
os.makedirs(A + '/textures/block', exist_ok=True)
os.makedirs(A + '/models/block', exist_ok=True)
os.makedirs(A + '/models/item', exist_ok=True)
os.makedirs(A + '/blockstates', exist_ok=True)

N = 32
rnd = random.Random(4242)
im = Image.new('RGBA', (N, N))

def put(x, y, c):
    if 0 <= x < N and 0 <= y < N:
        im.putpixel((x, y), c + (255,) if len(c) == 3 else c)

# 地: 暗い玄武岩 (青みの灰)。ひび割れ風の濃淡
for y in range(N):
    for x in range(N):
        v = 40 + rnd.randint(-5, 5)
        put(x, y, (v, v + 2, v + 12))
# 縁: 外 1px 濃く、内 1px 明るく (石板の面取り)
for i in range(N):
    for (x, y) in ((i, 0), (i, N - 1), (0, i), (N - 1, i)):
        put(x, y, (18, 19, 28))
    for (x, y) in ((i, 1), (i, N - 2), (1, i), (N - 2, i)):
        if 1 < i < N - 2:
            put(x, y, (66, 70, 92))
# 隅の鋲 (金)
GOLD = (214, 184, 98)
GOLD_D = (150, 122, 58)
GOLD_L = (255, 226, 140)
for cx, cy in ((4, 4), (N - 5, 4), (4, N - 5), (N - 5, N - 5)):
    put(cx, cy, GOLD_L); put(cx + 1, cy, GOLD); put(cx, cy + 1, GOLD); put(cx + 1, cy + 1, GOLD_D)

# 金の二重の輪 (中心 15.5, 15.5)
cx = cy = 15.5
for y in range(N):
    for x in range(N):
        d = ((x - cx) ** 2 + (y - cy) ** 2) ** 0.5
        if 11.0 <= d < 12.6:
            put(x, y, GOLD if (x + y) % 5 else GOLD_L)
        elif 12.6 <= d < 13.4:
            put(x, y, GOLD_D)
        elif 8.6 <= d < 9.6:
            put(x, y, GOLD_D)
# 輪の四方の刻み (十二時・三時・六時・九時)
for k in range(-1, 3):
    for (x, y) in ((15 + (k > 0), 3 + 0), (15 + (k > 0), 28), (3, 15 + (k > 0)), (28, 15 + (k > 0))):
        pass
for t in (3, 4):
    for dx in (0, 1):
        put(15 + dx, t, GOLD_L); put(15 + dx, N - 1 - t, GOLD_L); put(t, 15 + dx, GOLD_L); put(N - 1 - t, 15 + dx, GOLD_L)

# 中の紋: 閉じた目。上まぶた (弧) ・下まぶた (弧) ・まつげの短い線・真ん中の点
for x in range(8, 24):
    u = (x - 15.5) / 8.0
    top = int(round(15.5 - 3.6 * (1 - u * u)))
    bot = int(round(15.5 + 3.6 * (1 - u * u)))
    put(x, top, GOLD)
    put(x, bot, GOLD_D)
# 閉じたまぶたの線 (横一文字)
for x in range(10, 22):
    put(x, 15, GOLD_L)
    put(x, 16, GOLD)
# 点と涙の筋
put(15, 19, GOLD_L); put(16, 19, GOLD_L)
for y in (21, 22):
    put(15, y, GOLD_D); put(16, y, GOLD_D)
for x in (11, 13, 18, 20):
    put(x, 12, GOLD_D)
im.save(A + '/textures/block/trial_seal.png')

json.dump({"parent": "minecraft:block/cube_all", "textures": {"all": "skyabyss:block/trial_seal"}},
          open(A + '/models/block/trial_seal.json', 'w'), indent=2)
json.dump({"variants": {"": {"model": "skyabyss:block/trial_seal"}}}, open(A + '/blockstates/trial_seal.json', 'w'), indent=2)
json.dump({"parent": "skyabyss:block/trial_seal"}, open(A + '/models/item/trial_seal.json', 'w'), indent=2)
print('wrote trial_seal')
