# -*- coding: utf-8 -*-
"""終章の入口の語り「巨神の昇り」(都の大鐘楼の門に初めて触れたとき。docs/design/50 の 4.1)。pxkit / parts で描く。

  python -X utf8 tools/story/pixel/finale_ascend.py

出力は docs/review/story_pixel/finale_ascend_<絵>.png (240x135) と _x4.png。ゲームへは install_reels.py (REELS に finale_ascend)。
"""
import math

import numpy as np

import pxkit as K
import parts as P
from opening import moon

OUT = K.ROOT / "docs/review/story_pixel"
DAWN = ["#0a0c1c", "#1c2444", "#34406c", "#5a6a9a", "#8c9cc4", "#c4cce4", "#f4f0e8", "#ffffff", "#f8d890", "#e8a85c", "#b8703c", "#6a3c2c",
        "#2a2a34", "#4a4c5c", "#8a8c9c", "#c8b080"]


def titan(c, name, x, y, w, h, yaw, pitch=8, tint=None):
    img, _ = K.render_mesh(name, (w, h), yaw=yaw, pitch=pitch, tint=None if tint is None else tuple(int(v * 255) for v in tint))
    c.sprite(img, int(x - w / 2), int(y - h / 2))


def a1():
    # 眠っていた四体が目を覚ます: 夜明け前の地平に、四つの巨きな影がそれぞれ顔を上げる
    c = K.Canvas()
    c.vgrad([(0, (12, 16, 40)), (0.7, (60, 70, 120)), (1, (200, 150, 110))])
    c.stars(70, 61, ymax=60)
    c.ridge(108, 5, 0.05, 4, (30, 32, 50), rough=0.7)
    c.poly([(0, 114), (240, 114), (240, 135), (0, 135)], (22, 24, 38))
    for name, x, y, w, h, yaw in (("stone_whale_far", 52, 58, 110, 52, 120), ("tide_shell", 96, 100, 86, 44, 200),
                                  ("thunder_wing_far", 170, 48, 110, 56, 220), ("ember_titan_far", 206, 98, 80, 44, 250)):
        titan(c, name, x, y, w, h, yaw, tint=(0.55, 0.6, 0.8))
        c.glow(x, y - h * 0.15, 10, (120, 140, 200), 0.6)
    return c.quantize(DAWN)


def a2():
    # 都の上に集まる: 鐘楼のまわりの空に、四体がゆっくり寄ってくる
    c = K.Canvas()
    c.vgrad([(0, (40, 52, 100)), (0.8, (200, 170, 140)), (1, (240, 200, 150))])
    moon(c, 196, 30, 16, base=(230, 228, 236), shade=(170, 170, 190))
    titan(c, "stone_whale_mid", 70, 36, 110, 48, 100, pitch=4, tint=(0.75, 0.75, 0.9))
    titan(c, "thunder_wing_mid", 168, 64, 90, 46, 240, tint=(0.8, 0.8, 0.95))
    c.ridge(110, 5, 0.09, 6, (40, 40, 60), rough=0.8)
    bx = 120
    c.poly([(bx - 7, 135), (bx + 7, 135), (bx + 6, 84), (bx - 6, 84)], (44, 44, 66))
    c.poly([(bx - 9, 84), (bx + 9, 84), (bx, 70)], (50, 48, 72))
    c.poly([(0, 116), (240, 116), (240, 135), (0, 135)], (34, 34, 52))
    titan(c, "tide_shell", 40, 112, 50, 26, 160, tint=(0.6, 0.62, 0.78))
    titan(c, "ember_titan_far", 206, 112, 46, 26, 200, tint=(0.6, 0.6, 0.75))
    return c.quantize(DAWN)


def a3():
    # 岩鯨が持ち上げる: 鯨の背の上に立つ小さな人影、下に遠ざかる都と雲の下側
    c = K.Canvas()
    c.vgrad([(0, (120, 140, 190)), (1, (220, 220, 232))])
    titan(c, "stone_whale", 120, 86, 220, 90, 80, pitch=14)
    # 背の上の人影
    c.poly([(118, 52), (121, 52), (121, 45), (118, 45)], (20, 20, 30))
    c.disc(119.5, 43.5, 1.5, (20, 20, 30))
    # 上の雲の底 (近づく)
    rng = np.random.default_rng(3)
    for _ in range(26):
        x, y = rng.uniform(-10, 250), rng.uniform(-6, 14)
        c.disc(x, y, rng.uniform(8, 16), (244, 244, 250), alpha=0.9)
    return c.quantize(DAWN)


def a4():
    # 雷翼が雲を抜ける: 翼の下から雲の層を突き抜け、光があふれる
    c = K.Canvas()
    c.vgrad([(0, (150, 180, 230)), (0.6, (240, 230, 210)), (1, (255, 250, 240))])
    c.glow(120, 40, 90, (255, 230, 170), 0.9)
    titan(c, "thunder_wing", 120, 60, 200, 100, 180, pitch=-18)
    rng = np.random.default_rng(4)
    for _ in range(40):
        x, y = rng.uniform(-10, 250), rng.uniform(104, 140)
        c.disc(x, y, rng.uniform(8, 18), (250, 250, 252), alpha=0.95)
    for k in range(10):
        a = math.pi * (0.1 + 0.08 * k)
        P.line(c, 120, 40, 120 + math.cos(a) * 140, 40 - math.sin(a) * 140 * 0.4, (255, 240, 200))
    return c.quantize(DAWN)


def a5():
    # 雲海の夜明け: 果てない雲の海、金の朝日、浮かぶ島と崩れた門。西に止まった月
    c = K.Canvas()
    c.vgrad([(0, (110, 150, 210)), (0.55, (240, 220, 190)), (0.62, (250, 230, 190)), (1, (240, 240, 245))])
    c.glow(40, 70, 60, (255, 210, 140), 1.0)
    c.disc(40, 70, 12, (255, 236, 170))
    moon(c, 206, 28, 12, base=(236, 234, 240), shade=(190, 190, 205))
    rng = np.random.default_rng(5)
    for row in range(5):
        y0 = 78 + row * 12
        for _ in range(30):
            c.disc(rng.uniform(-10, 250), y0 + rng.uniform(-3, 3), rng.uniform(6, 14) + row * 2, (248, 246, 244), alpha=0.85)
    # 島: 上は緑と白い広場、下は逆さの岩
    ix, iy = 150, 52
    c.poly([(ix - 34, iy), (ix + 34, iy), (ix + 20, iy + 14), (ix + 6, iy + 30), (ix - 4, iy + 34), (ix - 16, iy + 18), (ix - 28, iy + 8)], (150, 140, 150))
    c.poly([(ix - 34, iy), (ix + 34, iy), (ix + 30, iy + 3), (ix - 30, iy + 3)], (110, 150, 90))
    c.poly([(ix - 12, iy - 1), (ix + 12, iy - 1), (ix + 12, iy + 1), (ix - 12, iy + 1)], (240, 236, 228))
    # 崩れた門 (白い柱二本、片方は折れている)
    c.poly([(ix - 6, iy - 1), (ix - 4, iy - 1), (ix - 4, iy - 16), (ix - 6, iy - 16)], (244, 240, 232))
    c.poly([(ix + 4, iy - 1), (ix + 6, iy - 1), (ix + 6, iy - 10), (ix + 4, iy - 10)], (244, 240, 232))
    c.poly([(ix - 7, iy - 16), (ix + 2, iy - 16), (ix + 2, iy - 18), (ix - 7, iy - 18)], (236, 210, 140))
    return c.quantize(DAWN)


PANELS = [("p1", a1, "still", "minecraft:entity.warden.heartbeat", "眠っていた巨神たちが、同じ夜明けに目を開けた。", "The sleeping titans opened their eyes at the same dawn."),
          ("p2", a2, "left", "minecraft:block.bell.resonate", "四つの影が、鐘楼の上に集まってくる。", "Four shadows gathered above the bell tower."),
          ("p3", a3, "up", "minecraft:entity.elder_guardian.ambient", "岩鯨の背に乗ると、都が足の下に遠ざかった。", "On the stone whale's back, the city fell away beneath you."),
          ("p4", a4, "up", "minecraft:entity.phantom.flap", "雷翼が雲を突き抜ける。光が、あふれた。", "The thunder wing broke through the clouds. Light poured over everything."),
          ("p5", a5, "still", "minecraft:block.amethyst_block.resonate", "雲の海の上に、崩れた門の島が浮かんでいた。", "Above the sea of clouds floated an island with a broken gate.")]


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    for pid, fn, *_ in PANELS:
        img = fn()
        img.save(OUT / f"finale_ascend_{pid}.png")
        img.resize((img.width * 4, img.height * 4), 0).save(OUT / f"finale_ascend_{pid}_x4.png")
        print("wrote", pid)


if __name__ == "__main__":
    main()
