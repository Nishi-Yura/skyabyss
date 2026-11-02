# -*- coding: utf-8 -*-
"""終章の神器の装飾 2 つの平らな絵 (docs/design/50 の 4.5。32x32)。神器の武器と防具の絵は低ポリの形から (o_divine.py・build_divine_armor.py)。

  PYTHONIOENCODING=utf-8 python -X utf8 tools/gen/finale/gen_divine_gear_icons.py            見せる絵だけ (docs/review/divine_gear/)
  PYTHONIOENCODING=utf-8 python -X utf8 tools/gen/finale/gen_divine_gear_icons.py --install  assets へ入れる (承認のあと)

  divine_plume_charm    巡礼の羽飾り: 白い大きな風切羽一枚に金の軸、根元を空の藍の紐で結び、朝の光の珠
  divine_promise_chime  約束の小鈴: 金の小さな鈴に空の藍の紐、鈴のまわりに四つの小さな灯 (前の自分たちの灯)
"""
import json
import math
import sys
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[3]
A = ROOT / "src/main/resources/assets/skyabyss"
REVIEW = ROOT / "docs/review/divine_gear"
OUTLINE = (40, 32, 26, 255)
GOLD = ((255, 232, 160, 255), (226, 186, 104, 255), (170, 128, 60, 255))
INDIGO = (38, 54, 96, 255)


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


def disc(px, cx, cy, r, col):
    for y in range(32):
        for x in range(32):
            if math.hypot(x + 0.5 - cx, y + 0.5 - cy) <= r:
                put(px, x, y, col)


def plume_charm():
    im = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    px = im.load()
    # 斜めの羽 (根元 = 左下、先 = 右上)
    bx, by, tx, ty = 9.0, 25.0, 25.0, 4.0
    L = math.hypot(tx - bx, ty - by)
    ux, uy = (tx - bx) / L, (ty - by) / L
    nx, ny = -uy, ux
    for y in range(32):
        for x in range(32):
            dx, dy = x + 0.5 - bx, y + 0.5 - by
            t = dx * ux + dy * uy
            d = dx * nx + dy * ny
            if 0 <= t <= L:
                w = 5.2 * math.sin(math.pi * min(1, t / L) ** 0.8) * (1.0 if d > 0 else 0.7)
                if abs(d) <= w:
                    barb = int(t * 0.9 + d * 0.6) % 3 == 0
                    col = (250, 250, 246, 255) if d > 0 else (220, 222, 232, 255)
                    if barb:
                        col = (206, 208, 220, 255)
                    put(px, x, y, col)
    for i in range(int(L) + 1):
        put(px, round(bx + ux * i), round(by + uy * i), GOLD[1] if i % 4 else GOLD[0])
    # 結び紐と珠
    for k in range(-2, 3):
        put(px, round(bx + nx * k), round(by + ny * k), INDIGO)
    disc(px, 7.0, 27.0, 2.4, (255, 226, 160, 255))
    disc(px, 6.4, 26.4, 0.9, (255, 250, 232, 255))
    outline(im)
    return im


def promise_chime():
    im = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    px = im.load()
    # 紐
    for y in range(3, 11):
        put(px, 16, y, INDIGO)
        put(px, 15 + (y % 2), y, INDIGO)
    # 鈴 (上が細く下が広い)
    for y in range(10, 24):
        t = (y - 10) / 13.0
        r = 3.0 + 5.2 * t ** 0.8
        for x in range(32):
            d = x + 0.5 - 16
            if abs(d) <= r:
                put(px, x, y, GOLD[0] if d < -r * 0.3 else GOLD[1] if d < r * 0.4 else GOLD[2])
    for x in range(8, 25):
        put(px, x, 23, GOLD[2])
        put(px, x, 24, (130, 96, 44, 255))
    disc(px, 16, 26.0, 1.6, (255, 236, 180, 255))
    # 四つの灯
    for cx, cy in ((4.5, 14.5), (8.5, 7.5), (23.5, 7.5), (27.5, 14.5)):
        disc(px, cx, cy, 1.6, (255, 214, 140, 255))
        put(px, int(cx) - 1, int(cy) - 1, (255, 248, 226, 255))
    outline(im)
    return im


def main():
    install = "--install" in sys.argv
    REVIEW.mkdir(parents=True, exist_ok=True)
    for name, fn in (("divine_plume_charm", plume_charm), ("divine_promise_chime", promise_chime)):
        im = fn()
        im.save(REVIEW / f"{name}.png")
        im.resize((256, 256), Image.NEAREST).save(REVIEW / f"{name}_x8.png")
        if install:
            im.save(A / "textures/item" / f"{name}.png")
            (A / "models/item" / f"{name}.json").write_text(json.dumps({"parent": "minecraft:item/generated", "textures": {"layer0": "skyabyss:item/" + name}},
                                                                      indent=2), encoding="utf-8")
    print("ok")


if __name__ == "__main__":
    main()
