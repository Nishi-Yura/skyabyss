#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""満月の野の仕掛けのブロック (満ち引きの石) と月の砂時計の絵・ブロックの状態・模型・品の模型を書く (docs/design/46 の 4.5)。

  PYTHONIOENCODING=utf-8 python -I tools/gen/ch6/gen_field_assets.py

絵は 32 x 32。新月の谷のブロック (黒曜の星図) と同じ様式で描く。
  moon_light_stone(_blink|_off)  光の石: 白い石英の結晶の面 (斜めの割れ面と青白い芯)。瞬きは全体が白く飛ぶ。透けると縁の輪郭だけ
  moon_shade_stone(_blink|_off)  影の石: 黒い硝子に藍の斑と星の粒。瞬きは藍が明るむ。透けると縁の輪郭だけ
  moon_hourglass                 月の砂時計 (品): 銀の上下の板と柱、上は空の光る硝子、下に月の砂
"""
import json
import math
import os
import random

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__)))))
A = os.path.join(ROOT, "src", "main", "resources", "assets", "skyabyss")
TEX = os.path.join(A, "textures", "block")
ITEM = os.path.join(A, "textures", "item")
N = 32


def w(path, data):
    path = os.path.join(A, path)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(data, f, ensure_ascii=False, indent=2)
        f.write("\n")


def clamp(c):
    return tuple(max(0, min(255, int(v))) for v in c)


def light_stone(mode):
    """白い石英の結晶の面。斜めの割れ面ごとに明るさを変え、真ん中に青白い芯"""
    r = random.Random(31)
    im = Image.new("RGBA", (N, N))
    px = im.load()
    # 割れ面: 斜めの線で 5 つの面に分ける
    cuts = [(0.55, 4), (-0.8, 22), (1.6, -18), (-0.3, 12)]
    for y in range(N):
        for x in range(N):
            face = sum(1 << i for i, (k, b) in enumerate(cuts) if y > k * x + b)
            base = 196 + (face * 37 % 48) - 20
            cx, cy = x - 15.5, y - 15.5
            core = max(0.0, 1.0 - math.hypot(cx, cy) / 12.0)
            c = (base - 6 + core * 30, base + core * 34, base + 14 + core * 30)
            c = tuple(v + r.randint(-5, 5) for v in c)
            px[x, y] = clamp(c) + (255,)
    # 割れ線 (明るい稜) と縁の暗い帯
    for k, b in cuts:
        for x in range(N):
            y = int(round(k * x + b))
            if 0 <= y < N:
                px[x, y] = (246, 250, 255, 255)
    for i in range(N):
        for (x, y) in ((i, 0), (i, N - 1), (0, i), (N - 1, i)):
            px[x, y] = (150, 170, 204, 255)
    # 星の粒
    for _ in range(6):
        x, y = r.randrange(3, N - 3), r.randrange(3, N - 3)
        px[x, y] = (255, 255, 255, 255)
    if mode == "blink":
        for y in range(N):
            for x in range(N):
                c = px[x, y]
                px[x, y] = clamp((c[0] * 0.5 + 128, c[1] * 0.5 + 128, c[2] * 0.5 + 128)) + (255,)
    if mode == "off":
        return outline((220, 232, 255), 90)
    return im


def shade_stone(mode):
    """黒い硝子に藍の斑と星の粒。上の縁に細い銀の線"""
    r = random.Random(41)
    im = Image.new("RGBA", (N, N))
    px = im.load()
    for y in range(N):
        for x in range(N):
            k = r.randint(-4, 4)
            blot = (x // 3 * 5 + y // 3 * 3) % 7 == 0
            c = (12 + k, 12 + k, 30 + k + (14 if blot else 0))
            px[x, y] = clamp(c) + (236,)
    # 斜めの照り (硝子の映り込み)
    for i in range(N):
        for d in (0, 1):
            x, y = i, N - 1 - i - 9 + d
            if 0 <= y < N:
                px[x, y] = (44, 46, 82, 236)
    for i in range(N):
        for (x, y) in ((i, 0), (i, N - 1), (0, i), (N - 1, i)):
            px[x, y] = (70, 78, 120, 255)
    for _ in range(9):
        x, y = r.randrange(2, N - 2), r.randrange(2, N - 2)
        px[x, y] = (170, 190, 240, 255)
    if mode == "blink":
        for y in range(N):
            for x in range(N):
                c = px[x, y]
                px[x, y] = clamp((c[0] + 40, c[1] + 34, c[2] + 70)) + (c[3],)
    if mode == "off":
        return outline((90, 96, 160), 110)
    return im


def outline(col, alpha):
    """透けた石: 縁の輪郭と角だけを薄く (中はほぼ透明)"""
    im = Image.new("RGBA", (N, N), (0, 0, 0, 0))
    px = im.load()
    for i in range(N):
        for (x, y) in ((i, 0), (i, N - 1), (0, i), (N - 1, i)):
            a = alpha if (i // 3) % 2 == 0 else alpha // 2
            px[x, y] = col + (a,)
    for x, y in ((1, 1), (N - 2, 1), (1, N - 2), (N - 2, N - 2)):
        px[x, y] = col + (alpha,)
    for y in range(N):
        for x in range(N):
            if px[x, y][3] == 0:
                px[x, y] = col + (14,)
    return im


def hourglass():
    """月の砂時計 (品の絵): 銀の上下の板・柱、上は空の光る硝子、下に月の砂"""
    im = Image.new("RGBA", (N, N), (0, 0, 0, 0))
    px = im.load()
    SIL = [(60, 66, 80), (110, 118, 134), (168, 176, 190), (214, 220, 230), (244, 248, 252)]
    GL = (150, 190, 240)
    SAND = [(112, 118, 130), (152, 158, 168), (192, 196, 204), (226, 228, 232)]

    def put(x, y, c, a=255):
        if 0 <= x < N and 0 <= y < N:
            px[x, y] = tuple(c) + (a,)

    # 硝子の玉 (上と下、くびれは真ん中)
    for y in range(6, 26):
        t = abs(y - 15.5) / 10.0
        half = int(round(1.5 + 6.0 * math.sin(min(1.0, t) * math.pi / 2) ** 1.2))
        for x in range(16 - half, 16 + half):
            edge = x in (16 - half, 16 + half - 1)
            if y > 16:
                # 下の玉の砂 (上ほど少なく、山の形)
                fill = 26 - y < 7 - abs(x - 15.5) * 0.45
                if fill or y >= 22:
                    sh = SAND[3] if x < 14 else SAND[2] if x < 18 else SAND[1]
                    put(x, y, SAND[0] if edge else sh)
                    continue
            put(x, y, (200, 224, 255) if edge else GL, 255 if edge else 190)
    # 落ちる砂の筋
    for y in range(15, 22):
        put(15, y, SAND[3])
    # 上と下の板
    for y, ys in ((3, (3, 4, 5)), (26, (26, 27, 28))):
        for yy in ys:
            for x in range(7, 25):
                c = SIL[4] if yy == ys[0] else SIL[2] if yy == ys[1] else SIL[1]
                if x in (7, 24):
                    c = SIL[1]
                put(x, yy, c)
    # 柱 (左右)
    for y in range(6, 26):
        put(8, y, SIL[3])
        put(9, y, SIL[1])
        put(23, y, SIL[2])
        put(22, y, SIL[0])
    # 上の三日月の飾りと星
    for x, y in ((14, 1), (15, 1), (16, 1), (13, 2), (17, 2)):
        put(x, y, SIL[3])
    put(15, 2, (250, 252, 255))
    return im


def main():
    os.makedirs(TEX, exist_ok=True)
    os.makedirs(ITEM, exist_ok=True)
    for mode in ("on", "blink", "off"):
        suf = "" if mode == "on" else "_" + mode
        light_stone(mode).save(os.path.join(TEX, "moon_light_stone%s.png" % suf))
        shade_stone(mode).save(os.path.join(TEX, "moon_shade_stone%s.png" % suf))
    hourglass().save(os.path.join(ITEM, "moon_hourglass.png"))

    variants = {}
    for shade, name in ((False, "light"), (True, "shade")):
        for state, suf in ((0, "_off"), (1, ""), (2, "_blink")):
            variants["shade=%s,state=%d" % (str(shade).lower(), state)] = {"model": "skyabyss:block/tide_stone_%s%s" % (name, suf)}
            w("models/block/tide_stone_%s%s.json" % (name, suf), {"parent": "minecraft:block/cube_all", "render_type": "minecraft:translucent",
                                                                 "textures": {"all": "skyabyss:block/moon_%s_stone%s" % (name, suf)}})
    w("blockstates/tide_stone.json", {"variants": variants})
    w("models/item/moon_hourglass.json", {"parent": "minecraft:item/generated", "textures": {"layer0": "skyabyss:item/moon_hourglass"}})
    print("done")


if __name__ == "__main__":
    main()
