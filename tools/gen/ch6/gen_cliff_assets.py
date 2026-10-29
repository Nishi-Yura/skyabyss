#!/usr/bin/env python3
"""上弦の崖の仕掛けのブロック (崩れる棚・天秤の皿・月の錘・錘の山) の絵・ブロックの状態・模型・品の模型を書く (docs/design/46 の 4.4)。

  PYTHONIOENCODING=utf-8 python -I tools/gen/ch6/gen_cliff_assets.py

絵は 32 x 32。新月の谷のブロック (黒曜の星図: 磨いた暗い石・上の縁の明るい帯・青白い銀の線と星) と同じ様式で描く。
  moon_ledge / moon_ledge_top   崩れる棚: 明るい凝灰岩の板に、暗いひびと小さな星
  moon_plate_top / moon_plate_side  天秤の皿: 暗い磨いた石に銀の弧と星、横は銀の帯
  moon_weight / moon_weight_top     月の錘: 銀の帯と三日月の印の石、上は銀の輪の取っ手
"""
import json
import math
import os
import random

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__)))))
A = os.path.join(ROOT, "src", "main", "resources", "assets", "skyabyss")
TEX = os.path.join(A, "textures", "block")
N = 32

SILVER = (186, 206, 232)
STAR = (226, 238, 255)
STAR_DIM = (150, 172, 210)


def w(path, data):
    path = os.path.join(A, path)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(data, f, ensure_ascii=False, indent=2)
        f.write("\n")


def stone(base, var, seed, top_band=None):
    """細かな斑のある石。top_band = 上の縁の明るい帯の色 (2 画素)"""
    r = random.Random(seed)
    im = Image.new("RGBA", (N, N))
    px = im.load()
    for y in range(N):
        for x in range(N):
            k = r.randint(-var, var)
            # 2 画素の塊で斑を作る (細かすぎない)
            if (x // 2 + y // 2 * 7 + seed) % 5 == 0:
                k -= var // 2
            px[x, y] = tuple(max(0, min(255, c + k)) for c in base) + (255,)
    if top_band:
        for y in range(2):
            for x in range(N):
                px[x, y] = top_band + (255,)
        for x in range(N):
            px[x, N - 1] = tuple(max(0, c - 18) for c in base) + (255,)
    return im


def star(px, x, y, big=False, col=STAR):
    if not (0 <= x < N and 0 <= y < N):
        return
    px[x, y] = col + (255,)
    if big:
        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            if 0 <= x + dx < N and 0 <= y + dy < N:
                px[x + dx, y + dy] = STAR_DIM + (255,)


def line(px, a, b, col):
    (x0, y0), (x1, y1) = a, b
    n = max(abs(x1 - x0), abs(y1 - y0), 1)
    for i in range(n + 1):
        x = round(x0 + (x1 - x0) * i / n)
        y = round(y0 + (y1 - y0) * i / n)
        if 0 <= x < N and 0 <= y < N:
            px[x, y] = col + (255,)


def crack(px, x, y, steps, r, col=(70, 70, 74), depth=0):
    """細いひび: 2〜4 画素の折れ線をつなぎ、ときどき枝を出す。線の下の縁は少し明るい (割れ目の縁)"""
    ang = r.uniform(0, math.pi * 2)
    for _ in range(steps):
        ang += r.uniform(-0.7, 0.7)
        seg = r.randint(2, 4)
        nx, ny = x + round(math.cos(ang) * seg), y + round(math.sin(ang) * seg)
        line(px, (x, y), (nx, ny), col)
        if 0 <= nx < N and 0 <= ny + 1 < N:
            px[nx, ny + 1] = (150, 150, 152, 255)
        x, y = nx, ny
        if depth < 1 and r.random() < 0.3:
            crack(px, x, y, max(1, steps // 2), r, col, depth + 1)


# ---------------------------------------------------------------- 崩れる棚 (明るい凝灰岩、ひび、小さな星)
def ledge(top):
    im = stone((122, 122, 124), 9, 41 if top else 42, None if top else (150, 150, 152))
    px = im.load()
    r = random.Random(43 if top else 44)
    for _ in range(4 if top else 3):
        crack(px, r.randrange(4, N - 4), r.randrange(4 if top else 5, N - 6), 4, r)
    for _ in range(3):
        star(px, r.randrange(3, N - 3), r.randrange(4, N - 3), col=STAR_DIM)
    return im


# ---------------------------------------------------------------- 天秤の皿 (暗い磨いた石、銀の弧と星 / 横は銀の帯)
def plate_top():
    im = stone((46, 46, 54), 6, 51)
    px = im.load()
    # 隅を中心にした銀の弧 (大きな皿の縁の一部に見える)
    for y in range(N):
        for x in range(N):
            d = math.hypot(x + 0.5, y + 0.5)
            if abs(d - 27) < 0.6 or abs(d - 15) < 0.5:
                px[x, y] = SILVER + (255,)
    r = random.Random(52)
    pts = [(r.randrange(4, 28), r.randrange(4, 28)) for _ in range(3)]
    for a, b in zip(pts, pts[1:]):
        line(px, a, b, (96, 110, 140))
    for p in pts:
        star(px, p[0], p[1], big=True)
    return im


def plate_side():
    im = stone((44, 44, 52), 6, 53)
    px = im.load()
    for y in range(0, 4):
        for x in range(N):
            px[x, y] = (SILVER if y in (1, 2) else (120, 132, 150)) + (255,)
    for x in range(2, N, 8):
        px[x, 2] = STAR + (255,)
    return im


# ---------------------------------------------------------------- 月の錘 (銀の帯と三日月の印 / 上は銀の輪)
def weight_side():
    im = stone((70, 70, 78), 7, 61, (96, 96, 104))
    px = im.load()
    for y in (7, 8, 23, 24):
        for x in range(N):
            px[x, y] = SILVER + (255,)
    # 三日月 (真ん中)
    for y in range(N):
        for x in range(N):
            a = math.hypot(x - 15.5, y - 15.5)
            b = math.hypot(x - 18.0, y - 14.0)
            if a <= 5.0 and b > 4.2:
                px[x, y] = STAR + (255,)
    return im


def weight_top():
    im = stone((76, 76, 84), 7, 62)
    px = im.load()
    for y in range(N):
        for x in range(N):
            d = math.hypot(x - 15.5, y - 15.5)
            if abs(d - 9.5) < 0.8:
                px[x, y] = SILVER + (255,)
            elif d < 3:
                px[x, y] = (110, 120, 140, 255)
    return im


def main():
    os.makedirs(TEX, exist_ok=True)
    for name, im in (("moon_ledge", ledge(False)), ("moon_ledge_top", ledge(True)), ("moon_plate_top", plate_top()), ("moon_plate_side", plate_side()),
                     ("moon_weight", weight_side()), ("moon_weight_top", weight_top())):
        im.save(os.path.join(TEX, name + ".png"))

    # 崩れる棚: 落ちたときは何も描かない (粒の絵だけ)
    w("blockstates/crumbling_ledge.json", {"variants": {
        "fallen=false,shaking=false": {"model": "skyabyss:block/crumbling_ledge"},
        "fallen=false,shaking=true": {"model": "skyabyss:block/crumbling_ledge"},
        "fallen=true,shaking=false": {"model": "skyabyss:block/crumbling_ledge_fallen"},
        "fallen=true,shaking=true": {"model": "skyabyss:block/crumbling_ledge_fallen"}}})
    w("models/block/crumbling_ledge.json", {"parent": "minecraft:block/cube_column",
                                            "textures": {"end": "skyabyss:block/moon_ledge_top", "side": "skyabyss:block/moon_ledge"}})
    w("models/block/crumbling_ledge_fallen.json", {"textures": {"particle": "skyabyss:block/moon_ledge"}, "elements": []})
    # 天秤の皿
    w("blockstates/balance_plate.json", {"variants": {"": {"model": "skyabyss:block/balance_plate"}}})
    w("models/block/balance_plate.json", {"parent": "minecraft:block/cube_bottom_top",
                                          "textures": {"top": "skyabyss:block/moon_plate_top", "side": "skyabyss:block/moon_plate_side",
                                                       "bottom": "skyabyss:block/moon_plate_side"}})

    def box(f, t, side="#side", top="#top"):
        return {"from": f, "to": t, "faces": {d: {"texture": top if d in ("up", "down") else side} for d in ("north", "south", "east", "west", "up", "down")}}

    # 月の錘: 錘の胴と、上の輪の取っ手
    w("blockstates/moon_weight.json", {"variants": {"": {"model": "skyabyss:block/moon_weight"}}})
    w("models/block/moon_weight.json", {"parent": "minecraft:block/block",
                                        "textures": {"side": "skyabyss:block/moon_weight", "top": "skyabyss:block/moon_weight_top",
                                                     "particle": "skyabyss:block/moon_weight"},
                                        "elements": [box([3, 0, 3], [13, 9, 13]), box([4, 9, 4], [12, 10, 12]), box([7, 10, 6], [9, 12, 10])]})
    # 錘の山: 三つの錘を寄せて積む
    w("blockstates/weight_pile.json", {"variants": {"": {"model": "skyabyss:block/weight_pile"}}})
    w("models/block/weight_pile.json", {"parent": "minecraft:block/block",
                                        "textures": {"side": "skyabyss:block/moon_weight", "top": "skyabyss:block/moon_weight_top",
                                                     "particle": "skyabyss:block/moon_weight"},
                                        "elements": [box([1, 0, 1], [8, 6, 8]), box([8, 0, 7], [15, 6, 14]), box([4, 6, 4], [11, 12, 11])]})
    w("models/item/moon_weight.json", {"parent": "skyabyss:block/moon_weight"})
    print("done")


if __name__ == "__main__":
    main()
