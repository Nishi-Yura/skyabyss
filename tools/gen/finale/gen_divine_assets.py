# -*- coding: utf-8 -*-
"""終章の神器の素材の平らな絵 (docs/design/50 の 4.5。32x32)。神器そのものの絵とメッシュは o_divine.py → install_job_weapons.py。

  PYTHONIOENCODING=utf-8 python -X utf8 tools/gen/finale/gen_divine_assets.py

  cloud_heart        雲上の心核: 白い雲の珠の真ん中に朝の光の芯、まわりに金の輪 (七つ目の心核)
  heart_core_shard   心核の欠片: 光の芯を抱いた、角の立った金白の小さな結晶の欠片
"""
import json
import math
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[3]
A = ROOT / "src/main/resources/assets/skyabyss"
OUTLINE = (40, 32, 26, 255)


def put(px, x, y, c):
    if 0 <= x < 32 and 0 <= y < 32:
        px[x, y] = c


def outline(im):
    px = im.load()
    src = im.copy().load()
    for y in range(32):
        for x in range(32):
            if src[x, y][3] == 0 and any(0 <= x + dx < 32 and 0 <= y + dy < 32 and src[x + dx, y + dy][3] > 0
                                         for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                px[x, y] = OUTLINE


def shade(base, k):
    return tuple(max(0, min(255, int(v * k))) for v in base) + (255,)


def cloud_heart():
    im = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    px = im.load()
    cx, cy = 15.5, 16.5
    # 金の輪 (後ろ)
    for a in range(0, 360, 4):
        r = math.radians(a)
        put(px, round(cx + math.cos(r) * 12.5), round(cy + math.sin(r) * 5.5 - 1), (214, 170, 84, 255))
    # 雲の珠 (三つの塊が重なる)
    for bx, by, br in ((cx, cy, 8.6), (cx - 4.5, cy + 1.5, 6.0), (cx + 4.8, cy + 1.2, 6.2), (cx, cy - 3.8, 6.0)):
        for y in range(32):
            for x in range(32):
                d = math.hypot(x - bx, y - by)
                if d <= br:
                    light = 1.0 - 0.18 * ((x - bx) + (y - by)) / br - 0.1 * (d / br)
                    put(px, x, y, shade((236, 236, 244), max(0.7, min(1.08, light))))
    # 朝の光の芯
    for y in range(32):
        for x in range(32):
            d = math.hypot(x - cx, y - (cy + 0.5))
            if d <= 3.6:
                put(px, x, y, (255, 214, 140, 255) if d > 2.0 else (255, 246, 214, 255))
    # 金の輪 (前)
    for a in range(20, 160, 4):
        r = math.radians(a)
        put(px, round(cx + math.cos(r) * 12.5), round(cy + math.sin(r) * 5.5 - 1), (246, 214, 130, 255))
    put(px, 13, 11, (255, 255, 255, 255))
    put(px, 12, 12, (255, 255, 255, 255))
    outline(im)
    return im


def shard():
    im = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    px = im.load()
    pts = [(16, 4), (23, 12), (21, 25), (14, 28), (9, 19), (11, 10)]

    def inside(x, y):
        c = False
        for i in range(len(pts)):
            (x0, y0), (x1, y1) = pts[i], pts[i - 1]
            if (y0 > y) != (y1 > y) and x < (x1 - x0) * (y - y0) / (y1 - y0) + x0:
                c = not c
        return c

    for y in range(32):
        for x in range(32):
            if inside(x + 0.5, y + 0.5):
                facet = (x - 16) * 0.6 - (y - 16) * 0.4
                col = (248, 236, 206) if facet < -2 else (230, 206, 150) if facet < 2 else (196, 160, 96)
                put(px, x, y, col + (255,))
    for y in range(32):
        for x in range(32):
            if math.hypot(x - 15.5, y - 16.5) < 2.8 and px[x, y][3] > 0:
                put(px, x, y, (255, 228, 160, 255))
    for x, y in ((14, 8), (13, 10), (13, 11)):
        put(px, x, y, (255, 255, 255, 255))
    outline(im)
    return im


def main():
    for name, fn in (("cloud_heart", cloud_heart), ("heart_core_shard", shard)):
        fn().save(A / "textures/item" / f"{name}.png")
        (A / "models/item" / f"{name}.json").write_text(json.dumps({"parent": "minecraft:item/generated", "textures": {"layer0": "skyabyss:item/" + name}},
                                                                  indent=2), encoding="utf-8")
    print("ok")


if __name__ == "__main__":
    main()
