"""開幕の語り (序章の前) の絵。tools/story/pixel/pxkit.py で描く。

  python tools/story/pixel/opening.py [出力先]

一枚ずつ: 240x135 に描き、色の表 (一枚 8〜12 色) に網目で落とす。出力は <出力先>/opening_<番号>.png (既定は docs/review/story_pixel/)。
"""
import math
import sys
from pathlib import Path

import numpy as np

import pxkit as K

OUT = Path(sys.argv[1]) if len(sys.argv) > 1 else K.ROOT / "docs/review/story_pixel"

# 語り (一枚に一、二行)。ゲームでは絵の下に一字ずつ出す
STORY = [
    ("p1", "むかし、空と地のあいだに、ひとつの約束があった。"),
    ("p2", "空には、月を引いて飛ぶ、古いものがいた。"),
    ("p3", "地の底には、世界をつなぎとめる心臓が脈打っていた。"),
    ("p4", "心臓は鼓動を四つに分け、巨神たちに預けた。"),
    ("p5", "巨神は歌い、月を遠ざけた。人はその背に町を建てた。"),
    ("p6", "地上では、空見の一族が鐘を鳴らし、空を見張った。"),
    ("p7", "……いま、鐘を鳴らす者はいない。月は、また近づいている。"),
    ("p8", "名もない島で、ひとりの旅人が目を覚ます。"),
    ("p9", "「忘れないうちに書け。次は、もっと早く気づけるように。」"),
]

NIGHT = ["#07080f", "#0e1124", "#171d3a", "#232d55", "#34467a", "#4d6699", "#7590ba", "#a8bddb", "#dfe6f2", "#f0dca0"]


def moon(c, cx, cy, r, base=(222, 228, 240), shade=(150, 160, 190)):
    c.glow(cx, cy, r * 3.2, (40, 50, 80), 0.9)
    c.disc(cx, cy, r, base)
    # 欠け (右下の影) と海 (斑)
    d = np.hypot(c.xx - (cx + r * 0.35), c.yy - (cy + r * 0.25))
    inside = np.hypot(c.xx - cx, c.yy - cy) <= r
    c.blend(((d < r * 1.05) & inside) * 0.35, shade)
    rng = np.random.default_rng(3)
    for _ in range(9):
        a, rr = rng.uniform(0, math.tau), rng.uniform(0, r * 0.75)
        x, y = cx + math.cos(a) * rr, cy + math.sin(a) * rr
        c.disc(x, y, rng.uniform(1.2, r * 0.22), shade, alpha=0.45)


def cloud_sea(c, top, layers, colors, rims, seed):
    """雲の海: 奥から手前へ、明るい縁の付いた稜線を重ねる"""
    for i in range(layers):
        y0 = top + i * (c.h - top) / layers
        ys = c.ridge(y0 + 6, 7 - i, 0.035 + i * 0.01, seed + i, colors[i], rough=0.55)
        # 縁の光
        for x in range(c.w):
            y = int(math.ceil(ys[x]))
            if 0 <= y < c.h:
                c.px[y, x] = c.px[y, x] * 0.3 + np.array(rims[i], float) * 0.7


def wing(c, ox, oy, s, color):
    """大きな翼の影: 弧を描く前縁と骨の太い腕、その下に分かれて垂れる長い風切り羽"""
    arm = [(0, 10), (40, -6), (90, -14), (140, -10), (180, 4), (176, 12), (130, 4), (80, 6), (30, 18), (0, 22)]
    c.poly([(ox + x * s, oy + y * s) for x, y in arm], color)
    for k in range(9):
        t = k / 8
        bx, by = 20 + t * 150, 14 - math.sin(t * math.pi) * 10
        length = 40 + 26 * math.sin(t * math.pi * 0.9 + 0.3)
        ang = math.radians(70 - t * 38)
        tipx, tipy = bx + math.cos(ang) * length, by + math.sin(ang) * length
        nx, ny = -math.sin(ang) * 6, math.cos(ang) * 6
        feather = [(bx - nx, by - ny), (bx + nx, by + ny), (tipx + nx * 0.25, tipy + ny * 0.25), (tipx, tipy + 2), (tipx - nx * 0.4, tipy - ny * 0.4)]
        c.poly([(ox + x * s, oy + y * s) for x, y in feather], color)


def puffs(c, y0, n, seed, base, rim, light_x, rmin=6, rmax=14, spread=None):
    """もこもこの雲: 円を重ねた一つの塊にして、塊の上の縁 (光の側ほど太く) を明るく"""
    rng = np.random.default_rng(seed)
    mask = np.zeros((c.h, c.w), bool)
    for _ in range(n):
        x = rng.uniform(-10, c.w + 10)
        r = rng.uniform(rmin, rmax)
        y = y0 + rng.uniform(-3, 5) + (spread or 0) * rng.uniform(-1, 1)
        mask |= np.hypot(c.xx - x, c.yy - y) <= r
    mask |= c.yy >= y0 + 6
    up = np.zeros_like(mask)
    up[1:] = mask[:-1]
    edge = mask & ~up
    up2 = np.zeros_like(mask)
    up2[2:] = mask[:-2]
    near = np.abs(c.xx - light_x) < 70
    edge2 = mask & ~up2 & near
    c.px[mask] = np.array(base, float)
    c.px[edge2] = np.array(base, float) * 0.4 + np.array(rim, float) * 0.6
    c.px[edge] = np.array(rim, float)


def p2():
    c = K.Canvas()
    c.vgrad([(0, (6, 7, 16)), (0.6, (18, 22, 48)), (1, (40, 54, 98))])
    c.stars(160, 7, ymax=90)
    moon(c, 150, 48, 34)
    # 月を引く鎖 (翼の付け根から月の縁へ、たわんだ二本)
    for k, (x1, y1) in enumerate(((176, 70), (128, 76))):
        for t in np.linspace(0, 1, 160):
            x = 66 + (x1 - 66) * t
            y = 22 + (y1 - 22) * t + math.sin(t * math.pi) * (8 + k * 6)
            xi, yi = int(x), int(y)
            if 0 <= xi < c.w and 0 <= yi < c.h and int(t * 160) % 4 != 0:
                c.px[yi, xi] = (12, 12, 24)
    # 翼の影 (左上から月の前を横切る)
    wing(c, -40, -4, 0.95, (8, 8, 18))
    # 雲の海 (奥ほど暗く、月の側の縁が光る)
    puffs(c, 104, 26, 11, (30, 38, 74), (90, 110, 160), 150, 8, 16)
    puffs(c, 114, 30, 12, (40, 52, 94), (120, 140, 190), 150, 7, 14)
    puffs(c, 126, 34, 13, (54, 70, 118), (160, 178, 220), 150, 6, 12)
    # 翼の影が雲に落ちる
    c.blend(((((c.xx - 60) / 80.0) ** 2 + ((c.yy - 112) / 10.0) ** 2) < 1) * 0.5, (14, 16, 36))
    return c.quantize(NIGHT)


DUSK = ["#120c1e", "#24163a", "#3a2350", "#5a3460", "#86485e", "#b8665a", "#e09a62", "#f4cc84", "#2a2f4c", "#4a5478", "#7d86a6", "#fff0b8"]


def p5():
    c = K.Canvas()
    c.vgrad([(0, (30, 20, 56)), (0.45, (96, 52, 92)), (0.75, (200, 110, 90)), (1, (240, 186, 120))])
    c.stars(30, 5, ymax=40, color=(250, 240, 220))
    c.glow(205, 112, 70, (90, 50, 20), 0.6)
    moon(c, 40, 24, 9, base=(250, 236, 210), shade=(200, 170, 160))
    # 遠い雲
    cloud_sea(c, 96, 2, [(120, 70, 100), (150, 90, 104)], [(220, 140, 120), (240, 170, 130)], 21)
    # 岩鯨 (夕陽の側を明るく、手前の暗い色に寄せる)
    img, _ = K.render_mesh("stone_whale_far", (190, 74), yaw=96, pitch=8, light=(0.7, 0.5, -0.4), ambient=0.35, tint=(170, 150, 175))
    c.sprite(img, 30, 38)
    # 背の町の灯 (背の稜線の上に小さな灯をいくつか)
    a = img[..., 3] > 0
    tops = [(x, int(np.argmax(a[:, x]))) for x in range(img.shape[1]) if a[:, x].any()]
    rng = np.random.default_rng(4)
    for x, y in tops[30:150:9]:
        if rng.random() < 0.8:
            c.disc(30 + x, 38 + y - 1, 0.8, (255, 236, 160))
            c.glow(30 + x, 38 + y - 1, 3.5, (120, 80, 20), 0.6)
    # 手前の雲 (鯨の腹を隠す)
    cloud_sea(c, 108, 2, [(90, 60, 96), (70, 48, 86)], [(200, 130, 120), (180, 110, 120)], 33)
    return c.quantize(DUSK)


DAWN = ["#141a26", "#22303e", "#34485a", "#4e6a78", "#7a96a0", "#b4c6c8", "#e6e4d6", "#f4d6a4", "#2c2a22", "#4a4330", "#6e6444", "#9a8c5e", "#3a2a22", "#7a3a2a"]


FIG = r'''
..............XXXX..................
.............XXXXXX.................
............XXXXXXXX................
............XXXXXXXXX...............
............XXXXXXXXX...............
.............XXXXXXX................
............XXXXXXXX................
..........XXXXXXXXXXX...............
.........XXXXXXXXXXXXX..............
.........XXXXXXXXXXXXXX.............
........XXXXXXXXXXXXXXXX............
........XXXXXXXXXX.XXXXXXX..........
........XXXXXXXXXX...XXXXXX.........
........XXXXXXXXXX.....XXXXX..XXXX..
.......XXXXXXXXXXX......XXXXXXXXXXX.
.......XXXXXXXXXXX.......XXXXXXXXXXX
.......XXXXXXXXXXX......XXXXXXXX.XXX
.......XXXXXXXXXXX.....XXXXXX....XXX
......XXXXXXXXXXXXXXXXXXXXX......XXX
......XXXXXXXXXXXXXXXXXXX........XXX
.....XXXXXXXXXXXXXXXXXX..........XXX
.....XXXXXXXXXXXXXXXXX...........XXX
....XXXXXXXXXXXXXXXXXX...........XXX
...XXXXXXXXXXXXXXXXXXX..........XXXX
.XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX
'''


def p8():
    c = K.Canvas()
    # 夜明けの空 (上は冷たい青、水平線の上は暖かい)
    c.vgrad([(0, (40, 58, 88)), (0.5, (120, 140, 160)), (0.85, (236, 206, 160)), (1, (248, 226, 180))], 0, 82)
    c.glow(176, 82, 46, (90, 60, 20), 1.0)
    c.disc(176, 82, 7, (255, 240, 200))
    # 海 (陽の道が光る)
    c.vgrad([(0, (190, 186, 176)), (1, (110, 124, 136))], 82, 108)
    rng = np.random.default_rng(2)
    for y in range(84, 108, 2):
        for _ in range(10):
            x = int(176 + rng.normal(0, 4 + (y - 82) * 1.2))
            if 0 <= x < c.w - 3:
                c.px[y, x:x + rng.integers(2, 5)] = (250, 230, 190)
    # 島の丘 (手前は暗い影絵)
    c.poly([(0, 100), (40, 96), (66, 106), (110, 108), (150, 104), (200, 107), (240, 106), (240, 135), (0, 135)], (30, 30, 30))
    c.poly([(0, 112), (60, 108), (120, 113), (240, 118), (240, 135), (0, 135)], (20, 20, 22))
    # 起き上がって海を見る旅人 (影絵。膝を立てて座り、腕を膝に。画素を一つずつ置いた形)
    S = (14, 14, 18)
    ox, oy = 70, 83
    for r, line in enumerate(FIG.strip().splitlines()):
        for q, ch in enumerate(line):
            if ch == "X":
                c.px[oy + r, ox + q] = S
    oy = 108
    # 冷えた焚き火 (石の輪の影と、細く曲がって昇る煙)
    fx, fy = 130, 111
    for k in range(7):
        a = k / 7 * math.pi
        c.disc(fx + math.cos(a) * 6, fy - math.sin(a) * 1.5, 1.8, S)
    c.poly([(fx - 5, fy - 2), (fx + 4, fy - 5), (fx + 5, fy - 4), (fx - 4, fy - 1)], S)
    for i in range(60):
        t = i / 60
        x = fx + math.sin(t * 7.0) * 3.5 * t + t * 6
        y = fy - 6 - t * 50
        c.blend(((c.xx - x) ** 2 + (c.yy - y) ** 2 < 0.6 + t * 1.6) * (0.55 * (1 - t) ** 1.2), (230, 226, 220))
    # 投げ出された帳面 (開いた白い頁がひとつだけ明るい)
    c.poly([(ox + 36, oy + 1), (ox + 42, oy - 1), (ox + 48, oy + 1), (ox + 42, oy + 3)], (232, 222, 196))
    # 草の穂の影
    for _ in range(70):
        x = int(rng.integers(0, c.w))
        base = 112 + int(6 * x / c.w) + int(rng.integers(0, 20))
        h = int(rng.integers(3, 9))
        lean = rng.choice([-1, 0, 1])
        for k in range(h):
            xx = x + (lean if k > h // 2 else 0)
            if 0 <= xx < c.w and 0 <= base - k < c.h:
                c.px[base - k, xx] = (10, 10, 12)
    return c.quantize(DAWN2)


DAWN2 = ["#0a0a0e", "#1e1e22", "#2c3a54", "#4a6282", "#7c90a8", "#a8b4be", "#d2c6b0", "#ecd0a0", "#f8e4b8", "#fff6dc", "#5a6a7a"]


SCENES = {"p2": p2, "p5": p5, "p8": p8}


def main():
    import opening_more
    OUT.mkdir(parents=True, exist_ok=True)
    scenes = dict(SCENES)
    scenes.update(opening_more.SCENES)
    for key, fn in sorted(scenes.items()):
        img = fn()
        img.save(OUT / f"opening_{key}.png")
        img.resize((img.width * 4, img.height * 4), 0).save(OUT / f"opening_{key}_x4.png")
        print("wrote", key)


if __name__ == "__main__":
    main()
