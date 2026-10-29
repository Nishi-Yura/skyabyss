"""六章の終わりの語り「月が止まる」(月の核を止めたあと。docs/design/46 の 4.7)。pxkit / parts で描く。

  python tools/story/pixel/moon_end.py

出力は docs/review/story_pixel/moon_core_end_<絵>.png (240x135) と _x4.png。ゲームへは install_reels.py (REELS に moon_core_end)。
"""
import math
import sys
from pathlib import Path

import numpy as np

import pxkit as K
import parts as P
from opening import moon

OUT = K.ROOT / "docs/review/story_pixel"
NIGHT = ["#04050c", "#0c1024", "#1a2244", "#2c3a6a", "#4a5c94", "#7c90c4", "#b4c4e8", "#e8f0ff", "#ffffff", "#24242e", "#4a4a58", "#8a8a98",
         "#c8a060", "#f4dca0"]
RED = ["#04050c", "#120a14", "#2a1022", "#5a1a2a", "#9a2a30", "#d8504a", "#2c3a6a", "#7c90c4", "#e8f0ff", "#ffffff", "#24242e", "#4a4a58"]


def m1():
    # 核の間: 割れた珠から光があふれ、三つの輪がほどけて落ちていく
    c = K.Canvas()
    c.vgrad([(0, (4, 5, 14)), (1, (14, 18, 38))])
    c.stars(60, 11, color=(120, 140, 200))
    cx, cy = 120, 60
    c.glow(cx, cy, 90, (60, 80, 140), 1.0)
    img, _ = K.render_mesh("moon_core", (110, 110), yaw=200, pitch=8)
    c.sprite(img, cx - 55, cy - 55)
    # 割れ目 (珠を斜めに走る白い稲妻)
    P.bolt(c, cx - 14, cy - 18, cx + 10, cy + 14, 5, color=(255, 255, 255), glow=(120, 150, 220), width=1)
    P.bolt(c, cx + 4, cy - 20, cx - 8, cy + 6, 9, color=(240, 248, 255), glow=(100, 130, 200), width=1)
    # 光の筋 (放射)
    for k in range(14):
        a = k / 14 * math.tau + 0.2
        for t in np.linspace(26, 120, 60):
            x, y = cx + math.cos(a) * t, cy + math.sin(a) * t
            if 0 <= int(x) < c.w and 0 <= int(y) < c.h:
                al = 0.35 * (1 - t / 120)
                c.px[int(y), int(x)] = c.px[int(y), int(x)] * (1 - al) + np.array((220, 232, 255)) * al
    # 落ちていく輪のかけら
    rng = np.random.default_rng(4)
    for _ in range(9):
        x, y = rng.uniform(40, 200), rng.uniform(90, 128)
        P.line(c, x - 6, y, x + 6, y + rng.uniform(-3, 3), (170, 180, 200))
    # 床の円
    c.poly([(30, 124), (210, 124), (240, 135), (0, 135)], (24, 22, 34))
    P.line(c, 50, 126, 190, 126, (200, 196, 210))
    return c.quantize(NIGHT)


def m2():
    # 四つの台の四人が、ほほえんで光の粒になって昇っていく
    c = K.Canvas()
    c.vgrad([(0, (6, 8, 20)), (1, (24, 30, 60))])
    c.stars(80, 21, color=(140, 160, 220))
    c.poly([(0, 112), (240, 112), (240, 135), (0, 135)], (22, 20, 30))
    xs = [42, 94, 146, 198]
    rng = np.random.default_rng(8)
    for i, x in enumerate(xs):
        base = 112
        # 台
        c.poly([(x - 10, base), (x + 10, base), (x + 8, base - 3), (x - 8, base - 3)], (60, 56, 74))
        # 人の影 (旅装の外套と頭。上ほど光の粒に溶けて薄い)
        fade = 0.15 + i * 0.1
        body = (30, 34, 56)
        c.poly([(x - 6, base - 3), (x + 6, base - 3), (x + 4, base - 16), (x + 3, base - 22), (x - 3, base - 22), (x - 4, base - 16)], body, alpha=1 - fade)
        c.disc(x, base - 26, 3.4, body, alpha=1 - fade)
        # 縁の光 (青白い輪郭)
        for (x0, y0, x1, y1) in ((x - 6, base - 3, x - 3, base - 22), (x + 6, base - 3, x + 3, base - 22)):
            P.line(c, x0, y0, x1, y1, (150, 170, 220))
        # それぞれの持ち物の光 (角灯・綱・砂時計・鏡)
        tint = [(255, 220, 150), (200, 200, 210), (190, 220, 255), (220, 236, 255)][i]
        c.glow(x + 7, base - 13, 6, tuple(v // 4 for v in tint), 1.0)
        c.disc(x + 7, base - 13, 1.4, tint)
        # 昇る光の粒 (頭の上から)
        for _ in range(30):
            px, py = x + rng.normal(0, 4), base - 30 - rng.uniform(0, 80)
            c.disc(px, py, rng.uniform(0.5, 1.2), (230, 240, 255), alpha=max(0.15, 1 - (base - 30 - py) / 90))
    return c.quantize(NIGHT)


def m3():
    # 月の面の着く所。黒い空に地上の大陸が浮かぶ。止まった月は静かで、砂だけが舞う
    c = K.Canvas()
    c.vgrad([(0, (2, 3, 10)), (1, (8, 10, 22))], 0, 92)
    c.stars(220, 31, ymax=90)
    # 地上 (大陸の浮かぶ青い球)
    ex, ey, er = 168, 38, 26
    c.glow(ex, ey, er * 2.2, (30, 50, 90), 0.9)
    c.disc(ex, ey, er, (40, 80, 140))
    rng = np.random.default_rng(5)
    for _ in range(14):
        a, rr = rng.uniform(0, math.tau), rng.uniform(0, er * 0.8)
        c.disc(ex + math.cos(a) * rr, ey + math.sin(a) * rr, rng.uniform(2, 6), (70, 120, 70), alpha=0.8)
    for _ in range(6):
        a, rr = rng.uniform(0, math.tau), rng.uniform(0, er * 0.85)
        c.disc(ex + math.cos(a) * rr, ey + math.sin(a) * rr, rng.uniform(2, 5), (230, 236, 244), alpha=0.7)
    d = np.hypot(c.xx - (ex - er * 0.4), c.yy - (ey - er * 0.2))
    inside = np.hypot(c.xx - ex, c.yy - ey) <= er
    c.blend(((d > er * 0.95) & inside) * 0.55, (4, 6, 14))
    # 月の地面と着く所の円い台、四つの灯り
    c.ridge(96, 3, 0.04, 7, (150, 156, 170), rough=0.4)
    c.poly([(0, 104), (240, 104), (240, 135), (0, 135)], (176, 180, 192))
    c.poly([(70, 112), (170, 112), (182, 120), (58, 120)], (220, 220, 228))
    for x in (66, 98, 142, 174):
        c.poly([(x - 1, 112), (x + 1, 112), (x + 1, 104), (x - 1, 104)], (60, 60, 70))
        c.glow(x, 103, 5, (60, 140, 160), 1.0)
        c.disc(x, 103, 1.2, (140, 230, 240))
    # 舞う砂
    for _ in range(40):
        c.disc(rng.uniform(0, 240), rng.uniform(96, 130), 0.6, (230, 232, 240), alpha=0.5)
    return c.quantize(NIGHT)


def m4():
    # 月の上を、巨きな翼の影がよぎる (全身は見えない)。空が赤く滲む
    c = K.Canvas()
    c.vgrad([(0, (10, 4, 10)), (0.7, (40, 12, 22)), (1, (70, 22, 30))])
    c.stars(90, 41, color=(200, 160, 170), ymax=80)
    moon(c, 120, 70, 44, base=(214, 216, 226), shade=(140, 140, 160))
    # 翼の影 (左上から右へ、羽の切れ込み)
    pts = [(-10, 18), (40, 30), (90, 46), (130, 60), (170, 56), (210, 40), (250, 22), (250, 0), (-10, 0)]
    c.poly(pts, (8, 4, 10))
    for k in range(9):
        x = 10 + k * 26
        c.poly([(x, 20 + k * 3), (x + 14, 34 + (k % 3) * 6), (x + 22, 22 + k * 2)], (8, 4, 10))
    c.glow(120, 70, 70, (90, 30, 40), 0.6)
    return c.quantize(RED)


def m5():
    # 地上の夜。都の鐘楼の上に、近づいた大きな月。小さな人影が鐘を鳴らす
    c = K.Canvas()
    c.vgrad([(0, (8, 10, 24)), (0.8, (30, 36, 70)), (1, (50, 58, 96))])
    c.stars(140, 51, ymax=70)
    moon(c, 150, 40, 36)
    # 町の屋根の影
    c.ridge(104, 6, 0.08, 3, (16, 18, 30), rough=0.9)
    c.poly([(0, 110), (240, 110), (240, 135), (0, 135)], (14, 16, 26))
    # 鐘楼
    bx = 74
    c.poly([(bx - 10, 135), (bx + 10, 135), (bx + 8, 50), (bx - 8, 50)], (22, 24, 40))
    c.poly([(bx - 13, 50), (bx + 13, 50), (bx, 32)], (26, 28, 46))
    c.poly([(bx - 6, 58), (bx + 6, 58), (bx + 6, 72), (bx - 6, 72)], (8, 8, 14))
    c.poly([(bx - 4, 60), (bx + 4, 60), (bx + 5, 69), (bx - 5, 69)], (200, 160, 80))
    c.glow(bx, 64, 16, (110, 80, 30), 1.0)
    # 鐘の間の小さな人影 (綱を引く)
    c.poly([(bx - 2, 72), (bx + 1, 72), (bx + 1, 66), (bx - 2, 66)], (10, 10, 16))
    c.disc(bx - 0.5, 64.5, 1.4, (10, 10, 16))
    # 鐘の音 (左右へ短い弧の線)
    for k in range(3):
        r = 12 + k * 6
        for t in np.linspace(-0.6, 0.6, 14):
            for side in (-1, 1):
                x, y = bx + side * math.cos(t) * r, 62 + math.sin(t) * r * 0.6
                if 0 <= int(x) < c.w and 0 <= int(y) < c.h:
                    c.px[int(y), int(x)] = c.px[int(y), int(x)] * 0.4 + np.array((240, 220, 160)) * 0.6
    # 窓の灯り
    rng = np.random.default_rng(6)
    for _ in range(18):
        x, y = rng.uniform(0, 240), rng.uniform(106, 128)
        c.disc(x, y, 0.8, (240, 200, 120), alpha=0.9)
    return c.quantize(NIGHT)


PANELS = [("p1", m1, "still", "minecraft:block.glass.break", "核の目が閉じ、時を縛っていた三つの輪がほどけた。", "The core's eye closed, and the three rings that bound time came undone."),
          ("p2", m2, "up", "minecraft:block.amethyst_block.resonate", "四人の私は、ほほえんで光に還っていった。", "The four of me smiled, and returned to the light."),
          ("p3", m3, "still", None, "巻き戻りは止まった。月はもう、落ちる前へは戻らない。", "The rewinding has stopped. The moon will never turn back to before the fall."),
          ("p4", m4, "left", "minecraft:entity.phantom.flap", "……それでも、月は落ちるのをやめない。空の上を、巨きな翼の影がよぎった。", "...And still, the moon does not stop falling. Above it, the shadow of vast wings passed by."),
          ("p5", m5, "down", "minecraft:block.bell.use", "地上で、鐘が鳴る。雲の上から、巡礼者が降りてくる。", "On the ground, the bell rings. From above the clouds, the Pilgrim descends.")]


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    for pid, fn, *_ in PANELS:
        img = fn()
        img.save(OUT / f"moon_core_end_{pid}.png")
        img.resize((img.width * 4, img.height * 4), 0).save(OUT / f"moon_core_end_{pid}_x4.png")
        print("wrote", pid)


if __name__ == "__main__":
    main()
