#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""六章「月」の装備の平らな絵と、月の暦のブロック (docs/design/03 の 12.2・46 の 4.8)。

  PYTHONIOENCODING=utf-8 python -I tools/gen/ch6/gen_moon_gear_assets.py

絵は 32 x 32。
  moon_fragment / resonance_shard_moon / moon_heart   巨神の欠片・共鳴の欠片・心核の絵 (余燼) の色を月の青白へ回したもの
                                                     (欠片と心核は巨神ごとに色だけ違う決まり。深淵と同じ)
  moon_full_ring    銀の環。頂に満月の円盤 (月の石) を抱き、両脇に小さな三日月の留め
  moon_tear         涙の形の硝子の小瓶に青白く光る月の雫、銀の三日月の栓
  block/moon_calendar_top・_side   月の暦: 磨いた黒石の円盤に銀の縁、四方に四つの位相 (新月・上弦・満月・下弦)、真ん中に星
  block/moon_memory_stone(_top)    記憶の石: 月の石の小さな碑、面に銀の三日月の刻みと、青白く光る筋 (46 の 4.9)
"""
import colorsys
import json
import math
import os

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__)))))
A = os.path.join(ROOT, "src", "main", "resources", "assets", "skyabyss")
ITEM = os.path.join(A, "textures", "item")
BLOCK = os.path.join(A, "textures", "block")
N = 32
SIL = [(46, 52, 68), (84, 92, 110), (130, 138, 156), (176, 184, 200), (216, 222, 232), (246, 248, 252)]
STONE = [(92, 98, 116), (130, 136, 152), (168, 174, 188), (204, 208, 220), (230, 232, 240)]
GLOW = [(110, 150, 214), (160, 200, 250), (206, 230, 255), (244, 250, 255)]
BLACK = [(12, 12, 20), (22, 22, 34), (34, 34, 50), (48, 48, 66)]
OUT = (14, 14, 24, 255)


def w(path, data):
    path = os.path.join(A, path)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(data, f, ensure_ascii=False, indent=2)
        f.write("\n")


def flat_model(name):
    w("models/item/%s.json" % name, {"parent": "minecraft:item/generated", "textures": {"layer0": "skyabyss:item/" + name}})


def outline(im):
    """形のまわりに 1 画素の濃い縁"""
    px = im.load()
    add = []
    for y in range(N):
        for x in range(N):
            if px[x, y][3] > 0:
                continue
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                xx, yy = x + dx, y + dy
                if 0 <= xx < N and 0 <= yy < N and px[xx, yy][3] > 0 and px[xx, yy] != OUT:
                    add.append((x, y))
                    break
    for x, y in add:
        px[x, y] = OUT
    return im


def recolor(src, dst, hue=0.60, sat=0.55, lift=0.06):
    im = Image.open(os.path.join(ITEM, src + ".png")).convert("RGBA")
    px = im.load()
    for y in range(im.size[1]):
        for x in range(im.size[0]):
            r, g, b, a = px[x, y]
            if a == 0:
                continue
            h, l, s = colorsys.rgb_to_hls(r / 255, g / 255, b / 255)
            if s < 0.08:
                continue
            nr, ng, nb = colorsys.hls_to_rgb(hue, min(1.0, l + lift), s * sat)
            px[x, y] = (int(nr * 255), int(ng * 255), int(nb * 255), a)
    im.save(os.path.join(ITEM, dst + ".png"))
    flat_model(dst)


def ring():
    im = Image.new("RGBA", (N, N), (0, 0, 0, 0))
    px = im.load()
    cx, cy = 15.5, 19.0
    # 環 (斜めから見た銀の帯: 楕円の輪)。上の縁は明るく、下は暗い
    for y in range(N):
        for x in range(N):
            dx, dy = (x - cx) / 11.0, (y - cy) / 7.5
            d = math.hypot(dx, dy)
            if 0.70 <= d <= 1.0:
                t = (y - (cy - 7.5)) / 15.0
                k = 4 if t < 0.25 else 3 if t < 0.5 else 2 if t < 0.8 else 1
                if d > 0.93 or d < 0.76:
                    k -= 1
                if x < cx - 6 and t > 0.4:
                    k = max(0, k - 1)
                px[x, y] = SIL[max(0, min(5, k))] + (255,)
    # 頂の座 (銀の爪) と満月の円盤
    mx, my, R = 15.5, 9.0, 6.2
    for y in range(N):
        for x in range(N):
            d = math.hypot(x - mx, y - my)
            if R - 1.2 < d <= R and y < my + 5:
                px[x, y] = SIL[3 if y < my else 2] + (255,)
            elif d <= R - 1.2:
                # 月の石: 左上が明るい。小さなクレーター
                l = -(x - mx) * 0.08 - (y - my) * 0.1
                k = 3 if l > 0.25 else 2 if l > -0.15 else 1
                if (x, y) in ((13, 7), (14, 7), (17, 10), (18, 11), (13, 11)):
                    k -= 1
                px[x, y] = STONE[max(0, k)] + (255,)
    for (x, y) in ((12, 6), (13, 5), (12, 5)):
        px[x, y] = STONE[4] + (255,)
    # 両脇の三日月の留め
    for s in (1, -1):
        bx = int(mx + s * 7.5)
        for (dx, dy, k) in ((0, 0, 4), (0, 1, 3), (s * 1, 2, 3), (s * 1, -1, 4), (0, 2, 2)):
            px[bx + dx, int(my) + 3 + dy] = SIL[k] + (255,)
    # 星の照り
    for (x, y, k) in ((24, 4, 3), (25, 3, 2), (23, 3, 2), (24, 2, 2), (24, 5, 1), (7, 13, 2)):
        px[x, y] = GLOW[k] + (255,)
    outline(im)
    # 星の照りの縁は消す (浮いた光)
    for (x, y) in ((24, 1), (26, 3), (22, 3), (24, 6), (25, 2), (23, 2), (25, 4), (23, 4), (26, 2)):
        if px[x, y] == OUT:
            px[x, y] = (0, 0, 0, 0)
    im.save(os.path.join(ITEM, "moon_full_ring.png"))
    flat_model("moon_full_ring")


def tear():
    im = Image.new("RGBA", (N, N), (0, 0, 0, 0))
    px = im.load()
    cx, cy, R = 15.5, 20.5, 8.0
    for y in range(N):
        for x in range(N):
            # 涙の形: 下は円、上は尖る
            if y >= cy:
                inside = math.hypot(x - cx, y - cy) <= R
                edge = math.hypot(x - cx, y - cy) > R - 1.3
            else:
                half = R * max(0.0, 1.0 - (cy - y) / 14.0) ** 0.8
                inside = abs(x - cx) <= half and y >= cy - 14
                edge = abs(x - cx) > half - 1.3
            if not inside:
                continue
            if edge:
                px[x, y] = (150, 170, 206, 255) if x < cx else (98, 112, 150, 255)
                continue
            # 中身: 下ほど満ちた光。上 1/3 は空の硝子
            if y < cy - 5:
                px[x, y] = (64, 74, 108, 255)
            else:
                t = (y - (cy - 5)) / 13.0
                l = 0.5 + 0.5 * ((cx - x) / R) - 0.2 * t
                k = 3 if l > 0.85 else 2 if l > 0.5 else 1 if l > 0.15 else 0
                px[x, y] = GLOW[k] + (255,)
    # 液の面のさざ波と照り
    for x in range(10, 22):
        if px[x, int(cy - 5)][3] > 0 and px[x, int(cy - 5)] not in ((150, 170, 206, 255), (98, 112, 150, 255)):
            px[x, int(cy - 5)] = GLOW[3] + (255,)
    for (x, y) in ((11, 18), (11, 19), (12, 17), (11, 20)):
        px[x, y] = (250, 252, 255, 255)
    # 栓: 銀の三日月
    for (x, y, k) in ((15, 4, 4), (16, 4, 3), (14, 5, 4), (17, 5, 2), (14, 6, 3), (17, 6, 2), (15, 7, 3), (16, 7, 2), (15, 6, 1), (16, 6, 1)):
        px[x, y] = SIL[k] + (255,)
    for (x, y, k) in ((13, 3, 3), (12, 4, 2), (13, 2, 2)):
        px[x, y] = SIL[k] + (255,)
    outline(im)
    im.save(os.path.join(ITEM, "moon_tear.png"))
    flat_model("moon_tear")


def calendar_top():
    im = Image.new("RGBA", (N, N), BLACK[1] + (255,))
    px = im.load()
    c = 15.5
    for y in range(N):
        for x in range(N):
            d = math.hypot(x - c, y - c)
            if d > 15.6:
                px[x, y] = BLACK[0] + (255,)
            elif d > 14.0:
                px[x, y] = SIL[4 if y < 12 else 3 if y < 20 else 2] + (255,)
            elif d > 13.2:
                px[x, y] = BLACK[0] + (255,)
            elif abs(d - 9.5) < 0.5 and (int(math.degrees(math.atan2(y - c, x - c))) // 9) % 2 == 0:
                px[x, y] = SIL[1] + (255,)
            else:
                px[x, y] = BLACK[2 if (x * 7 + y * 3) % 11 == 0 else 1] + (255,)

    def phase(mx, my, kind):
        for y in range(-3, 4):
            for x in range(-3, 4):
                if x * x + y * y > 10:
                    continue
                lit = {"new": False, "full": True, "first": x >= 0, "last": x <= 0}[kind]
                px[int(mx + x), int(my + y)] = (GLOW[2] if lit else BLACK[3]) + (255,)
        if kind == "new":
            for a in range(0, 360, 30):
                xx, yy = int(round(mx + 3.2 * math.cos(math.radians(a)))), int(round(my + 3.2 * math.sin(math.radians(a))))
                px[xx, yy] = SIL[2] + (255,)
    phase(15.5, 5.0, "full")
    phase(26.0, 15.5, "last")
    phase(15.5, 26.0, "new")
    phase(5.0, 15.5, "first")
    # 真ん中の星
    for (x, y, k) in ((15, 15, 3), (16, 15, 3), (15, 16, 3), (16, 16, 3), (15, 13, 2), (16, 13, 2), (15, 14, 2), (16, 14, 2),
                      (15, 17, 2), (16, 17, 2), (15, 18, 2), (16, 18, 2), (13, 15, 2), (14, 15, 2), (17, 15, 2), (18, 15, 2),
                      (13, 16, 2), (14, 16, 2), (17, 16, 2), (18, 16, 2)):
        px[x, y] = GLOW[k] + (255,)
    return im


def calendar_side():
    im = Image.new("RGBA", (N, N), BLACK[1] + (255,))
    px = im.load()
    for y in range(N):
        for x in range(N):
            k = 2 if (x // 8 + y // 8) % 2 == 0 else 1
            if x % 8 == 0 or y % 8 == 0:
                k = 0
            px[x, y] = BLACK[k] + (255,)
    for x in range(N):
        px[x, 26] = SIL[3] + (255,)
        px[x, 27] = SIL[2] + (255,)
        px[x, 0] = SIL[2] + (255,)
    for x in range(2, N, 6):
        px[x, 13] = GLOW[1] + (255,)
    return im


def memory_stone():
    """碑の面 (4 面とも): 上が丸い月の石の板に、銀の三日月と光る縦の筋"""
    im = Image.new("RGBA", (N, N), (0, 0, 0, 0))
    px = im.load()
    for y in range(N):
        for x in range(N):
            l = 0.55 + 0.25 * (1 - y / N) + (0.08 if (x * 5 + y * 3) % 13 == 0 else 0) - (0.12 if (x * 7 + y * 11) % 17 == 0 else 0)
            k = 4 if l > 0.78 else 3 if l > 0.62 else 2 if l > 0.48 else 1
            if x in (0, 31) or y == 31:
                k = 0
            px[x, y] = STONE[k] + (255,)
    # 三日月の刻み (銀) と光る筋
    for a in range(200, 341, 4):
        for r in (6.0, 6.8):
            xx = int(round(15.5 + r * math.cos(math.radians(a))))
            yy = int(round(11.0 + r * math.sin(math.radians(a)) * -1))
            if 0 <= xx < N and 0 <= yy < N:
                px[xx, yy] = SIL[4] + (255,)
    for y in range(16, 29):
        px[15, y] = GLOW[2 if y % 3 else 3] + (255,)
        px[16, y] = GLOW[1] + (255,)
    return im


def stone_top():
    im = Image.new("RGBA", (N, N), STONE[3] + (255,))
    px = im.load()
    for y in range(N):
        for x in range(N):
            if (x * 3 + y * 5) % 11 == 0:
                px[x, y] = STONE[2] + (255,)
    return im


def main():
    recolor("ember_shard", "moon_fragment")
    recolor("resonance_shard_ember", "resonance_shard_moon")
    recolor("ember_core", "moon_heart", sat=0.7)
    ring()
    tear()
    os.makedirs(BLOCK, exist_ok=True)
    calendar_top().save(os.path.join(BLOCK, "moon_calendar_top.png"))
    calendar_side().save(os.path.join(BLOCK, "moon_calendar_side.png"))
    w("models/block/moon_calendar.json", {
        "render_type": "minecraft:cutout",
        "textures": {"top": "skyabyss:block/moon_calendar_top", "side": "skyabyss:block/moon_calendar_side", "particle": "skyabyss:block/moon_calendar_side"},
        "elements": [
            {"from": [3, 0, 3], "to": [13, 10, 13],
             "faces": {d: {"texture": "#side", "uv": [3, 6, 13, 16]} for d in ("north", "south", "east", "west")}
             | {"down": {"texture": "#side", "uv": [3, 3, 13, 13]}}},
            {"from": [0, 10, 0], "to": [16, 13, 16],
             "faces": {d: {"texture": "#side", "uv": [0, 13, 16, 16]} for d in ("north", "south", "east", "west")}
             | {"up": {"texture": "#top", "uv": [0, 0, 16, 16]}, "down": {"texture": "#side", "uv": [0, 0, 16, 16]}}},
        ]})
    w("blockstates/moon_calendar.json", {"variants": {"": {"model": "skyabyss:block/moon_calendar"}}})
    memory_stone().save(os.path.join(BLOCK, "moon_memory_stone.png"))
    stone_top().save(os.path.join(BLOCK, "moon_memory_stone_top.png"))
    w("models/block/moon_memory_stone.json", {
        "textures": {"face": "skyabyss:block/moon_memory_stone", "top": "skyabyss:block/moon_memory_stone_top", "particle": "skyabyss:block/moon_memory_stone_top"},
        "elements": [
            {"from": [4, 0, 5], "to": [12, 14, 11],
             "faces": {"north": {"texture": "#face", "uv": [4, 0, 12, 14]}, "south": {"texture": "#face", "uv": [4, 0, 12, 14]},
                       "east": {"texture": "#face", "uv": [5, 0, 11, 14]}, "west": {"texture": "#face", "uv": [5, 0, 11, 14]},
                       "up": {"texture": "#top", "uv": [4, 5, 12, 11]}, "down": {"texture": "#top", "uv": [4, 5, 12, 11]}}},
            {"from": [3, 0, 4], "to": [13, 2, 12],
             "faces": {d: {"texture": "#top", "uv": [3, 4, 13, 12] if d in ("up", "down") else [3, 14, 13, 16]}
                       for d in ("north", "south", "east", "west", "up", "down")}},
        ]})
    w("blockstates/moon_memory_stone.json", {"variants": {"section=%d" % i: {"model": "skyabyss:block/moon_memory_stone"} for i in range(4)}})
    print("done")


if __name__ == "__main__":
    main()
