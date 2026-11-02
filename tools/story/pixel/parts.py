"""語りの絵の部品: 人の影絵 (画素を置いた形)・欠片・手・稲妻・碑・鏡など。章の語り (chapters.py) で使う"""
import math

import numpy as np

STAND = """
...XXX....
..XXXXX...
..XXXXX...
...XXX....
..XXXXXX..
.XXXXXXXX.
.XXXXXXXX.
XXXXXXXXXX
XXXXXXXXXX
XX.XXXXX.X
X..XXXXX..
...XXXXX..
...XXXXXX.
...XXXXXX.
...XX..XX.
...XX..XX.
...XX..XX.
...XX..XX.
..XXX..XXX
"""

SIT = """
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
"""

# 頭巾をかぶって背を丸めて座る年寄り (語り部)。左を向く
ELDER = """
.......XXXXX.........
.....XXXXXXXX........
....XXXXXXXXXX.......
...XXXXXXXXXXX.......
...XXX.XXXXXXXX......
...XX..XXXXXXXXX.....
....X..XXXXXXXXXX....
.......XXXXXXXXXXX...
......XXXXXXXXXXXXX..
.....XXXXXXXXXXXXXX..
....XXXXXXXXXXXXXXXX.
...XXXXXXXXXXXXXXXXX.
..XXXXXXXXXXXXXXXXXXX
..XXXXXXXXXXXXXXXXXXX
.XXXXXXXXXXXXXXXXXXXX
XXXXXXXXXXXXXXXXXXXXX
"""


def stamp(c, art, x, y, color, flip=False):
    rows = art.strip().splitlines()
    for r, line in enumerate(rows):
        if flip:
            line = line[::-1]
        for q, ch in enumerate(line):
            if ch == "X":
                xi, yi = int(x) + q, int(y) + r
                if 0 <= xi < c.w and 0 <= yi < c.h:
                    c.px[yi, xi] = color
    return len(rows[0]), len(rows)


def fragment(c, x, y, s, color=(150, 220, 255), glow=(40, 80, 120), lit=True):
    """天の鍵の欠片: 縦長の菱形。lit でなければ輪郭だけ (まだ無い欠片)"""
    pts = [(x, y - 4 * s), (x + 2.2 * s, y), (x, y + 4 * s), (x - 2.2 * s, y)]
    if lit:
        c.glow(x, y, 9 * s, glow, 1.0)
        c.poly(pts, color)
        c.poly([(x, y - 4 * s), (x + 2.2 * s, y), (x, y)], tuple(min(255, v + 50) for v in color))
    else:
        for i in range(4):
            x0, y0 = pts[i]
            x1, y1 = pts[(i + 1) % 4]
            for t in np.linspace(0, 1, 20):
                xi, yi = int(x0 + (x1 - x0) * t), int(y0 + (y1 - y0) * t)
                if 0 <= xi < c.w and 0 <= yi < c.h and int(t * 20) % 2 == 0:
                    c.px[yi, xi] = color


def palm(c, x, y, s, color):
    """下から差し出した開いた手のひら (上向き)"""
    c.poly([(x - 16 * s, 135), (x - 14 * s, y + 4 * s), (x - 10 * s, y), (x + 10 * s, y), (x + 15 * s, y + 4 * s), (x + 16 * s, 135)], color)
    for k, (dx, h) in enumerate(((-11, 9), (-5, 13), (1, 14), (7, 12))):
        c.poly([(x + (dx - 2) * s, y + 1), (x + (dx - 2) * s, y - h * s), (x + (dx + 2) * s, y - h * s), (x + (dx + 2) * s, y + 1)], color)
        c.disc(x + dx * s, y - h * s, 2 * s, color)
    c.poly([(x + 12 * s, y + 6 * s), (x + 22 * s, y - 2 * s), (x + 24 * s, y + 1 * s), (x + 15 * s, y + 10 * s)], color)


def bolt(c, x0, y0, x1, y1, seed, color=(240, 240, 255), glow=(70, 70, 140), width=1):
    rng = np.random.default_rng(seed)
    pts = [(x0, y0)]
    n = 9
    for i in range(1, n):
        t = i / n
        pts.append((x0 + (x1 - x0) * t + rng.uniform(-7, 7), y0 + (y1 - y0) * t))
    pts.append((x1, y1))
    for (ax, ay), (bx, by) in zip(pts, pts[1:]):
        c.glow((ax + bx) / 2, (ay + by) / 2, 10, glow, 0.5)
    for (ax, ay), (bx, by) in zip(pts, pts[1:]):
        for t in np.linspace(0, 1, 30):
            xi, yi = int(ax + (bx - ax) * t), int(ay + (by - ay) * t)
            for w in range(width):
                if 0 <= xi + w < c.w and 0 <= yi < c.h:
                    c.px[yi, xi + w] = color


def line(c, x0, y0, x1, y1, color, dashed=False):
    n = int(max(abs(x1 - x0), abs(y1 - y0)) * 2) + 1
    for i in range(n):
        t = i / max(1, n - 1)
        if dashed and (i // 2) % 2:
            continue
        xi, yi = int(round(x0 + (x1 - x0) * t)), int(round(y0 + (y1 - y0) * t))
        if 0 <= xi < c.w and 0 <= yi < c.h:
            c.px[yi, xi] = color


def tablet(c, x, y, w, h, stone, carve):
    """角の欠けた石の碑 (面に彫り)"""
    c.poly([(x, y + h), (x, y + 4), (x + 3, y), (x + w - 5, y), (x + w, y + 6), (x + w, y + h)], stone)
    return (x, y, w, h)


def ripples(c, cx, cy, n, color, spread=10):
    for k in range(1, n + 1):
        r = k * spread
        d = np.hypot((c.xx - cx) / 2.6, c.yy - cy)
        c.blend(((d > r / 2.6 - 0.5) & (d < r / 2.6 + 0.5)) * (0.8 - k * 0.15), color)
