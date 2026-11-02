#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""下弦の淵の仕掛けのブロック (下弦の鏡・残像の板) の絵・ブロックの状態・模型を書く (docs/design/46 の 4.6)。

  PYTHONIOENCODING=utf-8 python -I tools/gen/ch6/gen_rift_assets.py

絵は 32 x 32。新月の谷のブロック (黒曜の星図) と同じ様式で描く。
  echo_mirror_face(_recording|_waiting)  鏡の面: 丸い銀の枠に青白い鏡、左が欠けた下弦の影が映る。覚えているあいだは鏡が明るく波打ち、
                                         待つあいだは鏡の中に人の形の影が立つ
  echo_mirror_side                       枠の厚み (銀の帯)
  echo_mirror_base                       台: 磨いた黒石に銀の星図の線
  echo_plate(_pressed)                   残像の板: 丸い銀の板に下弦の月と星、踏まれると青白く光る
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
SIL = [(60, 66, 80), (110, 118, 134), (168, 176, 190), (214, 220, 230), (244, 248, 252)]
BLACK = [(14, 14, 22), (24, 24, 36), (36, 36, 52)]


def w(path, data):
    path = os.path.join(A, path)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(data, f, ensure_ascii=False, indent=2)
        f.write("\n")


def clamp(c):
    return tuple(max(0, min(255, int(v))) for v in c)


def mirror_face(mode):
    """丸い銀の枠に鏡。鏡の左は下弦の影で欠ける"""
    r = random.Random(51)
    im = Image.new("RGBA", (N, N), (0, 0, 0, 0))
    px = im.load()
    c0 = 15.5
    for y in range(N):
        for x in range(N):
            d = math.hypot(x - c0, y - c0)
            if d > 15.6:
                continue
            if d > 13.2:
                # 枠: 上が明るい銀
                k = 4 if y < 8 else 3 if y < 16 else 2 if y < 24 else 1
                if d > 15.0:
                    k = 0
                px[x, y] = SIL[k] + (255,)
                continue
            # 鏡: 上ほど明るい青白、右上に照り
            t = (y / N)
            base = (150 - t * 40, 176 - t * 36, 214 - t * 24)
            if mode == "recording":
                wave = math.sin((x + y * 0.6) * 0.7) * 18
                base = (base[0] + 40 + wave, base[1] + 40 + wave, base[2] + 30 + wave)
            if mode == "waiting":
                base = (base[0] + 20, base[1] + 26, base[2] + 30)
            # 下弦の影 (左が欠ける): 左寄りの円の外を暗く
            shade = math.hypot(x - (c0 + 7), y - c0) > 12.5
            if shade:
                base = (base[0] * 0.35, base[1] * 0.38, base[2] * 0.5)
            c = tuple(v + r.randint(-4, 4) for v in base)
            px[x, y] = clamp(c) + (255,)
    # 照り (右上の斜めの白い筋)
    for i in range(6):
        for dd in (0, 1):
            x, y = 21 + i - dd, 6 + i
            if px[x, y][3] and math.hypot(x - c0, y - c0) < 13:
                px[x, y] = (240, 248, 255, 255)
    if mode == "waiting":
        # 鏡の中に立つ人の形の影 (頭の丸と肩から下)
        for y in range(9, 27):
            for x in range(N):
                hx = x - 17
                head = math.hypot(hx, y - 11) < 2.6
                body = 14 <= y <= 25 and abs(hx) <= (3.6 if y < 20 else 2.4)
                if (head or body) and math.hypot(x - c0, y - c0) < 13:
                    px[x, y] = (90, 120, 190, 255)
    # 枠の上の三日月の飾り
    for x, y in ((14, 0), (15, 0), (16, 0), (17, 0)):
        px[x, y] = SIL[4] + (255,)
    return im


def mirror_side():
    im = Image.new("RGBA", (N, N))
    px = im.load()
    for y in range(N):
        for x in range(N):
            k = 3 if (x // 4) % 2 == 0 else 2
            if y in (0, N - 1):
                k = 1
            px[x, y] = SIL[k] + (255,)
    return im


def base():
    """磨いた黒石に銀の星図の線 (谷の台と同じ様式)"""
    r = random.Random(61)
    im = Image.new("RGBA", (N, N))
    px = im.load()
    for y in range(N):
        for x in range(N):
            k = r.randint(0, 2)
            px[x, y] = BLACK[k] + (255,)
    stars = [(6, 7), (14, 4), (24, 9), (27, 20), (17, 25), (7, 22), (15, 15)]
    for (ax, ay), (bx, by) in zip(stars, stars[1:]):
        n = max(abs(bx - ax), abs(by - ay))
        for i in range(n + 1):
            x = round(ax + (bx - ax) * i / n)
            y = round(ay + (by - ay) * i / n)
            px[x, y] = SIL[2] + (255,)
    for x, y in stars:
        px[x, y] = (210, 232, 255, 255)
    for i in range(N):
        for (x, y) in ((i, 0), (i, N - 1), (0, i), (N - 1, i)):
            px[x, y] = SIL[1] + (255,)
    return im


def plate(pressed):
    """丸い銀の板に下弦の月と星。踏まれると青白く光る"""
    im = Image.new("RGBA", (N, N), (0, 0, 0, 0))
    px = im.load()
    c0 = 15.5
    for y in range(N):
        for x in range(N):
            d = math.hypot(x - c0, y - c0)
            if d > 15.5:
                continue
            if d > 13.5:
                px[x, y] = SIL[3 if pressed else 2] + (255,)
                continue
            inner = (40, 44, 64) if not pressed else (120, 160, 220)
            px[x, y] = inner + (255,)
            # 下弦の月 (右が明るい三日月)
            if math.hypot(x - c0, y - c0) < 7.5 and math.hypot(x - (c0 - 4), y - c0) > 7.0:
                px[x, y] = (230, 238, 255, 255) if pressed else SIL[3] + (255,)
    for x, y in ((6, 8), (24, 7), (8, 24), (25, 23), (16, 5)):
        px[x, y] = (255, 255, 255, 255) if pressed else (170, 190, 230, 255)
    return im


def main():
    os.makedirs(TEX, exist_ok=True)
    for mode in ("idle", "recording", "waiting"):
        suf = "" if mode == "idle" else "_" + mode
        mirror_face(mode).save(os.path.join(TEX, "echo_mirror_face%s.png" % suf))
    mirror_side().save(os.path.join(TEX, "echo_mirror_side.png"))
    base().save(os.path.join(TEX, "echo_mirror_base.png"))
    plate(False).save(os.path.join(TEX, "echo_plate.png"))
    plate(True).save(os.path.join(TEX, "echo_plate_pressed.png"))

    variants = {}
    for mode in ("idle", "recording", "waiting"):
        suf = "" if mode == "idle" else "_" + mode
        name = "echo_mirror%s" % suf
        variants["mode=%s" % mode] = {"model": "skyabyss:block/%s" % name}
        w("models/block/%s.json" % name, {
            "render_type": "minecraft:cutout",
            "textures": {"face": "skyabyss:block/echo_mirror_face%s" % suf, "side": "skyabyss:block/echo_mirror_side",
                         "base": "skyabyss:block/echo_mirror_base", "particle": "skyabyss:block/echo_mirror_base"},
            "elements": [
                {"from": [3, 0, 3], "to": [13, 3, 13],
                 "faces": {d: {"texture": "#base", "uv": [3, 3, 13, 13] if d in ("up", "down") else [3, 13, 13, 16]}
                           for d in ("north", "south", "east", "west", "up", "down")}},
                {"from": [0, 3, 7], "to": [16, 19, 9],
                 "faces": {"north": {"texture": "#face", "uv": [0, 0, 16, 16]}, "south": {"texture": "#face", "uv": [16, 0, 0, 16]},
                           "east": {"texture": "#side", "uv": [7, 0, 9, 16]}, "west": {"texture": "#side", "uv": [7, 0, 9, 16]},
                           "up": {"texture": "#side", "uv": [0, 7, 16, 9]}, "down": {"texture": "#side", "uv": [0, 7, 16, 9]}}},
            ]})
    w("blockstates/echo_mirror.json", {"variants": variants})
    pv = {}
    for pressed in (False, True):
        name = "echo_plate" + ("_pressed" if pressed else "")
        pv["pressed=%s" % str(pressed).lower()] = {"model": "skyabyss:block/%s" % name}
        w("models/block/%s.json" % name, {
            "render_type": "minecraft:cutout",
            "textures": {"top": "skyabyss:block/%s" % name, "particle": "skyabyss:block/%s" % name},
            "elements": [{"from": [0, 0, 0], "to": [16, 0.5 if pressed else 1, 16],
                          # 縁の四面も描く (薄い板の側が抜けて見えていた。2026-10-29 の監査)
                          "faces": {"up": {"texture": "#top", "uv": [0, 0, 16, 16]}, "down": {"texture": "#top", "uv": [0, 0, 16, 16]},
                                    **{d: {"texture": "#top", "uv": [0, 0, 16, 1]} for d in ("north", "south", "east", "west")}}}]})
    w("blockstates/echo_plate.json", {"variants": pv})
    print("done")


if __name__ == "__main__":
    main()
