# -*- coding: utf-8 -*-
"""攻撃と技の粒の絵 (docs/design/59 の 2 章): 7 属性 x 3 種 x 4 枚、ドット 8x8、2 色 (地色と光の色)。

使い方: python -I tools/gen/ui/gen_fx_particles.py [--style a|b|c] [--sheet]
  --style  絵の案 (a = 欠けた石の素朴なドット / b = 鋭い幾何 / c = 丸く柔らか)。既定 a
  --sheet  docs/review/fx_particles/<style>.png に見比べの台紙 (8 倍) も書く
書くもの: src/main/resources/assets/skyabyss/textures/particle/fx/<属性>_<種類>_<n>.png と
          src/main/resources/assets/skyabyss/particles/fx_<属性>_<種類>.json
"""
import json
import zlib
import math
import random
import sys
from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[3]
TEX = ROOT / "src/main/resources/assets/skyabyss/textures/particle/fx"
JSON = ROOT / "src/main/resources/assets/skyabyss/particles"
SHEET = ROOT / "docs/review/fx_particles"

# 属性ごとの 2 色 (地色, 光の色)
COLORS = {
    "stone": ((0x5A, 0x68, 0x60), (0xD2, 0xF4, 0xDE)),
    "water": ((0x1C, 0x6A, 0x8A), (0xBE, 0xF0, 0xFF)),
    "storm": ((0x4A, 0x3C, 0x9C), (0xE6, 0xDE, 0xFF)),
    "ember": ((0x8C, 0x2C, 0x0E), (0xFF, 0xB4, 0x5E)),
    "abyss": ((0x2C, 0x20, 0x3C), (0xE6, 0xDC, 0xC2)),
    "moon": ((0x3C, 0x4C, 0x7C), (0xF4, 0xF8, 0xFF)),
    "paper": ((0x1A, 0x14, 0x16), (0xF2, 0xE6, 0xD0)),
}
KINDS = ["shard", "glow", "smoke"]
N = 8
B, L, H = 1, 2, 3  # 地色・光の色・半透明の地色 (c の縁だけ)


def blank():
    return [[0] * N for _ in range(N)]


# ---------------------------------------------------------------- a: 欠けた石の素朴なドット
def a_shard(r):
    g = blank()
    w, h = r.choice([(4, 3), (3, 4), (5, 3), (4, 4)])
    x0, y0 = (N - w) // 2, (N - h) // 2
    for y in range(h):
        for x in range(w):
            g[y0 + y][x0 + x] = B
    # 角を欠く
    for _ in range(r.randint(1, 3)):
        cx, cy = r.choice([(x0, y0), (x0 + w - 1, y0), (x0, y0 + h - 1), (x0 + w - 1, y0 + h - 1)])
        g[cy][cx] = 0
    # 光の縁 (上と左)
    for x in range(x0, x0 + w):
        if g[y0][x]:
            g[y0][x] = L
            break
    for y in range(y0, y0 + h):
        if g[y][x0]:
            g[y][x0] = L
            break
    return g


def a_glow(r, i):
    g = blank()
    c = 3 + (i % 2)
    arm = [1, 2, 3, 2][i]
    for d in range(-arm, arm + 1):
        g[c][c + d] = B if abs(d) == arm else L
        g[c + d][c] = B if abs(d) == arm else L
    g[c][c] = L
    return g


def a_smoke(r, i):
    g = blank()
    rad = [2.6, 3.0, 3.4, 2.8][i]
    cx = cy = 3.5
    for y in range(N):
        for x in range(N):
            d = math.hypot(x - cx, y - cy)
            if d <= rad:
                g[y][x] = B
            if d <= rad - 1.4 and x + y < 7:
                g[y][x] = L
    for _ in range(i + 1):
        x, y = r.randrange(N), r.randrange(N)
        g[y][x] = 0
    return g


# ---------------------------------------------------------------- b: 鋭い幾何
def b_shard(r, i):
    g = blank()
    pts = [[(1, 6), (6, 6), (3, 1)], [(1, 1), (6, 3), (2, 6)], [(3, 0), (6, 4), (3, 7), (1, 4)], [(0, 5), (7, 2), (4, 6)]][i]
    img = Image.new("L", (N, N), 0)
    ImageDraw.Draw(img).polygon(pts, fill=1)
    for y in range(N):
        for x in range(N):
            if img.getpixel((x, y)):
                g[y][x] = B
    # 光の縁: 一つ目の辺
    img2 = Image.new("L", (N, N), 0)
    ImageDraw.Draw(img2).line([pts[0], pts[1]], fill=1)
    for y in range(N):
        for x in range(N):
            if img2.getpixel((x, y)) and g[y][x]:
                g[y][x] = L
    return g


def b_glow(r, i):
    g = blank()
    rad = [1, 2, 3, 2][i]
    c = 3 if i != 3 else 4
    for y in range(N):
        for x in range(N):
            d = abs(x - c) + abs(y - c)
            if d < rad:
                g[y][x] = L
            elif d == rad:
                g[y][x] = B
    return g


def b_smoke(r, i):
    g = blank()
    rad = [2, 3, 3, 2][i]
    c = 3 + (i % 2)
    for y in range(N):
        for x in range(N):
            d = abs(x - c) + abs(y - c)
            if d == rad:
                g[y][x] = B
    if 0 <= c - rad + 1 < N:
        g[c][c - rad + 1] = L
    g[c - rad + 1][c] = L
    return g


# ---------------------------------------------------------------- c: 丸く柔らか
def c_shard(r, i):
    g = blank()
    w, h = [(4, 3), (3, 3), (4, 4), (5, 3)][i]
    x0, y0 = (N - w) // 2, (N - h) // 2
    for y in range(h):
        for x in range(w):
            corner = (x in (0, w - 1)) and (y in (0, h - 1))
            if not corner:
                g[y0 + y][x0 + x] = B
    g[y0 + 1 if h > 2 else y0][x0 + 1] = L
    return g


def c_glow(r, i):
    g = blank()
    rad = [1.5, 2.2, 2.8, 2.0][i]
    cx = cy = 3.5
    for y in range(N):
        for x in range(N):
            d = math.hypot(x - cx, y - cy)
            if d <= rad * 0.55:
                g[y][x] = L
            elif d <= rad:
                g[y][x] = B
            elif d <= rad + 0.8:
                g[y][x] = H
    return g


def c_smoke(r, i):
    g = blank()
    blobs = [[(3, 4, 2.4), (5, 3, 1.6)], [(4, 4, 2.8)], [(3, 3, 2.0), (5, 5, 2.0)], [(4, 3, 2.2), (2, 5, 1.4)]][i]
    for y in range(N):
        for x in range(N):
            for (bx, by, br) in blobs:
                d = math.hypot(x - bx, y - by)
                if d <= br - 0.9 and x + y < 8:
                    g[y][x] = L
                    break
                if d <= br:
                    g[y][x] = max(g[y][x], B) if g[y][x] != L else L
                elif d <= br + 0.7 and g[y][x] == 0:
                    g[y][x] = H
    return g


def make(style, kind, i, seed):
    r = random.Random(seed)
    if style == "a":
        return {"shard": lambda: a_shard(r), "glow": lambda: a_glow(r, i), "smoke": lambda: a_smoke(r, i)}[kind]()
    if style == "b":
        return {"shard": lambda: b_shard(r, i), "glow": lambda: b_glow(r, i), "smoke": lambda: b_smoke(r, i)}[kind]()
    return {"shard": lambda: c_shard(r, i), "glow": lambda: c_glow(r, i), "smoke": lambda: c_smoke(r, i)}[kind]()


def paint(g, base, light):
    img = Image.new("RGBA", (N, N), (0, 0, 0, 0))
    for y in range(N):
        for x in range(N):
            v = g[y][x]
            if v == B:
                img.putpixel((x, y), base + (255,))
            elif v == L:
                img.putpixel((x, y), light + (255,))
            elif v == H:
                img.putpixel((x, y), base + (120,))
    return img


def main():
    style = "a"
    if "--style" in sys.argv:
        style = sys.argv[sys.argv.index("--style") + 1].lower()
    TEX.mkdir(parents=True, exist_ok=True)
    JSON.mkdir(parents=True, exist_ok=True)
    sheet_cells = []
    for el, (base, light) in COLORS.items():
        row = []
        for kind in KINDS:
            frames = []
            for i in range(4):
                g = make(style, kind, i, zlib.crc32(f"{el}/{kind}/{i}".encode()))
                img = paint(g, base, light)
                img.save(TEX / f"{el}_{kind}_{i}.png")
                frames.append(f"skyabyss:fx/{el}_{kind}_{i}")
                row.append(img)
            (JSON / f"fx_{el}_{kind}.json").write_text(json.dumps({"textures": frames}, indent=2) + "\n", encoding="utf-8")
        sheet_cells.append(row)
    if "--sheet" in sys.argv:
        SHEET.mkdir(parents=True, exist_ok=True)
        z = 8
        pad = 4
        w = (N * z + pad) * 12 + pad
        h = (N * z + pad) * len(sheet_cells) + pad
        sh = Image.new("RGBA", (w, h), (24, 24, 30, 255))
        for ri, row in enumerate(sheet_cells):
            for ci, img in enumerate(row):
                big = img.resize((N * z, N * z), Image.NEAREST)
                x = pad + ci * (N * z + pad) + (ci // 4) * 0
                sh.alpha_composite(big, (x, pad + ri * (N * z + pad)))
        sh.save(SHEET / f"{style}.png")
        print("sheet", SHEET / f"{style}.png")
    print("wrote style", style)


if __name__ == "__main__":
    main()
