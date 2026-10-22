#!/usr/bin/env python3
"""潮殻の背の「上り」の作り直し案 (docs/design/24) の見取り図を、ドット絵の格子で描く。
出力: docs/review/tide_climb/{base,a,b,c}_{top,side}.png  (1 ブロック = 5 px。ブロックの絵なので最近傍で拡大)
座標は TideBackLayout の値 (T1〜T4 の台地、頂の塔、月の窓の縦穴、予約の間 1〜3、階段)。案ごとの足しは概念図。
使い方: python tools/gen/world/gen_tide_climb_review.py   (リポジトリ直下から)
"""
import math
import os
from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__)))))
OUT = os.path.join(ROOT, "docs", "review", "tide_climb")
S = 5  # 1 ブロックの px

# 描く範囲
X0, X1 = -62, 62
Z0, Z1 = -68, 64
Y0, Y1 = 60, 186

COL = {
    "bg": (0x10, 0x16, 0x1c),
    "sea": (0x1b, 0x3a, 0x4b),
    "shell": (0x3d, 0x6b, 0x66),
    "shell_dark": (0x2c, 0x4e, 0x4b),
    "t1": (0x6f, 0x8f, 0x86),
    "t2": (0x8a, 0xa8, 0x9d),
    "t3": (0xa6, 0xc0, 0xb3),
    "t4": (0xc4, 0xd8, 0xcb),
    "tower": (0xe3, 0xd8, 0xb8),
    "room": (0x24, 0x34, 0x3d),
    "room_edge": (0x5f, 0x7b, 0x86),
    "shaft": (0x07, 0x0d, 0x12),
    "water": (0x3f, 0xb6, 0xc8),
    "water_dark": (0x2a, 0x84, 0x94),
    "coral_pink": (0xe8, 0x79, 0x8a),
    "coral_orange": (0xf0, 0xa0, 0x5a),
    "coral_purple": (0xa5, 0x6b, 0xd0),
    "coral_yellow": (0xf0, 0xd0, 0x60),
    "glass": (0xbf, 0xea, 0xf0),
    "beam": (0xff, 0xf2, 0xa0),
    "plate": (0xd8, 0xcb, 0xb0),
    "arrow": (0xff, 0xff, 0xff),
    "boss": (0xd2, 0x4a, 0x3c),
    "door": (0xe0, 0x5a, 0x3c),
    "stair": (0xf0, 0xe8, 0xb0),
    "gold": (0xf0, 0xd0, 0x60),
    "mirror": (0xf4, 0xf4, 0xf4),
    "spiral": (0xb0, 0xd8, 0xe8),
}

# 台地 (x1, x2, z1, z2, 高さ)
T = {1: (-58, 58, -64, 60, 101), 2: (-45, 44, -60, 52, 114), 3: (-36, 28, -44, 36, 126), 4: (-20, 18, -28, 22, 137)}
TOWER = (-7, 6, -10, 3)  # x1, x2, z1, z2。中心 (0, -3)、幅 14。137〜181
CX, CZ = 0, -3
U = {1: (-32, 30, -38, 34, 101, 9), 2: (-22, 22, -28, 22, 114, 9), 3: (-10, 10, -16, 12, 126, 8)}


class Canvas:
    def __init__(self, w, h):
        self.img = Image.new("RGB", (w * S, h * S), COL["bg"])
        self.px = self.img.load()
        self.w, self.h = w, h

    def set(self, gx, gy, color):
        if 0 <= gx < self.w and 0 <= gy < self.h:
            for dx in range(S):
                for dy in range(S):
                    self.px[gx * S + dx, gy * S + dy] = color

    def save(self, name):
        os.makedirs(OUT, exist_ok=True)
        # 格子の目印 (10 ブロックごとに薄い点) は入れず、そのまま保存する
        self.img.save(os.path.join(OUT, name))


class Top(Canvas):
    def __init__(self):
        super().__init__(X1 - X0 + 1, Z1 - Z0 + 1)

    def b(self, x, z, color):
        self.set(x - X0, z - Z0, color)

    def rect(self, x1, x2, z1, z2, color):
        for x in range(x1, x2 + 1):
            for z in range(z1, z2 + 1):
                self.b(x, z, color)

    def frame(self, x1, x2, z1, z2, color):
        for x in range(x1, x2 + 1):
            self.b(x, z1, color)
            self.b(x, z2, color)
        for z in range(z1, z2 + 1):
            self.b(x1, z, color)
            self.b(x2, z, color)

    def disc(self, cx, cz, r, color):
        for x in range(cx - r, cx + r + 1):
            for z in range(cz - r, cz + r + 1):
                if (x - cx) ** 2 + (z - cz) ** 2 <= r * r + 0.5:
                    self.b(x, z, color)

    def ring(self, cx, cz, r, color, w=0.9):
        for x in range(cx - r - 1, cx + r + 2):
            for z in range(cz - r - 1, cz + r + 2):
                d = math.hypot(x - cx, z - cz)
                if abs(d - r) <= w:
                    self.b(x, z, color)

    def line(self, x1, z1, x2, z2, color):
        n = max(abs(x2 - x1), abs(z2 - z1), 1)
        for i in range(n + 1):
            self.b(round(x1 + (x2 - x1) * i / n), round(z1 + (z2 - z1) * i / n), color)


class Side(Canvas):
    def __init__(self):
        super().__init__(X1 - X0 + 1, Y1 - Y0 + 1)

    def b(self, x, y, color):
        self.set(x - X0, Y1 - y, color)

    def rect(self, x1, x2, y1, y2, color):
        for x in range(x1, x2 + 1):
            for y in range(y1, y2 + 1):
                self.b(x, y, color)

    def line(self, x1, y1, x2, y2, color):
        n = max(abs(x2 - x1), abs(y2 - y1), 1)
        for i in range(n + 1):
            self.b(round(x1 + (x2 - x1) * i / n), round(y1 + (y2 - y1) * i / n), color)

    def disc_x(self, cx, cy, r, color):
        for x in range(cx - r, cx + r + 1):
            for y in range(cy - r, cy + r + 1):
                if (x - cx) ** 2 + (y - cy) ** 2 <= r * r + 0.5:
                    self.b(x, y, color)


# ------------------------------------------------------------------ 今の姿
def base_top(c: Top, rooms=True, gardens=False):
    c.rect(X0, X1, Z0, Z1, COL["sea"])
    # 甲羅の輪郭 (台地 T1 の外の斜面)
    c.rect(-66 if -66 > X0 else X0, 66, -72 if -72 > Z0 else Z0, 66 if 66 < Z1 else Z1, COL["sea"])
    for x in range(X0, X1 + 1):
        for z in range(Z0, Z1 + 1):
            n = (abs(x / 71.4) ** 2.6 + abs((z + 2.6) / 85.9) ** 2.6) ** (1 / 2.6)
            if n <= 1.0:
                c.b(x, z, COL["shell"])
    for k, col in ((1, "t1"), (2, "t2"), (3, "t3"), (4, "t4")):
        x1, x2, z1, z2, _ = T[k]
        c.rect(x1, x2, z1, z2, COL[col])
        c.frame(x1, x2, z1, z2, COL["shell_dark"])
    c.rect(TOWER[0], TOWER[1], TOWER[2], TOWER[3], COL["tower"])
    if rooms:
        for k in (1, 2, 3):
            x1, x2, z1, z2, _, _ = U[k]
            c.frame(x1, x2, z1, z2, COL["room_edge"])
        # 入口の戸と廊下
        c.rect(-48, -33, -1, 1, COL["room"])
        c.rect(-49, -49, -1, 1, COL["door"])
        # 階段 (1: x -20〜-18, z 8〜19 / 2: z 8〜10, x -19〜-9)
        c.rect(-20, -18, 8, 19, COL["stair"])
        c.rect(-19, -9, 8, 10, COL["stair"])
    # 月の窓の縦穴
    c.disc(CX, CZ, 3, COL["shaft"])
    # 下の浜から甲羅の上へ上る段 (5x5、x -46〜-42、z 42〜46)
    c.rect(-46, -42, 42, 46, COL["spiral"])


def base_side(c: Side, rooms=True):
    c.rect(X0, X1, Y0, Y1, COL["bg"])
    c.rect(X0, X1, Y0, 63, COL["sea"])
    # 甲羅の断面 (z = -3)。T1 の縁 ±58 までと、斜面 ±71
    for x in range(-71, 72):
        top = 101 if abs(x) <= 58 else int(101 - (abs(x) - 58) * 2.2)
        top = max(top, 64)
        for y in range(64, top + 1):
            c.b(x, y, COL["shell"] if y < top - 2 else COL["shell_dark"])
    # 腹 (広間のあたり)
    for k, col in ((1, "t1"), (2, "t2"), (3, "t3"), (4, "t4")):
        x1, x2, _, _, y = T[k]
        c.rect(x1, x2, y - 1, y, COL[col])
        # 台地の壁
    for k in (2, 3, 4):
        x1, x2, _, _, y = T[k]
        c.rect(x1, x2, T[k - 1][4] + 1, y, COL["shell"])
    c.rect(-7, 6, 138, 181, COL["tower"])
    # 広間 (y 70〜84, x -12〜12)
    c.rect(-12, 12, 70, 84, COL["room"])
    if rooms:
        for k in (1, 2, 3):
            x1, x2, _, _, fl, h = U[k]
            c.rect(x1, x2, fl + 1, fl + h, COL["room"])
        # 戸の廊下
        c.rect(-48, -33, 102, 105, COL["room"])
    # 縦穴 (月の窓)。広間の天井 84 から窓のガラス 173 まで
    c.rect(-3, 3, 84, 172, COL["shaft"])
    c.rect(-3, 3, 173, 173, COL["glass"])
    c.rect(-2, 2, 174, 181, COL["glass"])
    if rooms:
        # 柱 (縦穴の壁、半径 4.5)
        for k in (1, 2, 3):
            x1, x2, _, _, fl, h = U[k]
            c.rect(-4, -4, fl + 1, fl + h, COL["room_edge"])
            c.rect(4, 4, fl + 1, fl + h, COL["room_edge"])


# ------------------------------------------------------------------ 案 A 潮の塔 (昇る水の三層)
def a_top():
    c = Top()
    base_top(c)
    # 第一層 取水の間: 水に浮かぶ板と三つの水門 (西・東・南の壁)
    x1, x2, z1, z2, _, _ = U[1]
    for gx in range(x1 + 3, x2 - 2, 6):
        for gz in range(z1 + 3, z2 - 2, 6):
            if abs(gx) <= 6 and abs(gz - CZ) <= 6:
                continue
            c.rect(gx, gx + 2, gz, gz + 2, COL["plate"])
    for (sx, sz) in ((x1, 0), (x2, 0), (0, z2)):
        c.rect(sx - 1, sx + 1, sz - 2, sz + 2, COL["water"]) if sz == 0 else c.rect(sx - 2, sx + 2, sz - 1, sz + 1, COL["water"])
    # 第二層 月暦の間: 三つの輪 (月の目盛り) と三つの出口
    for r, col in ((9, "gold"), (13, "water"), (17, "gold")):
        c.ring(CX, CZ, r, COL[col])
    for ang in (200, 270, 340):
        c.b(round(CX + 19 * math.cos(math.radians(ang))), round(CZ + 19 * math.sin(math.radians(ang))), COL["door"])
    # 第三層 昇りの間 (閘室): 縦穴の根元を囲む小部屋
    c.ring(CX, CZ, 7, COL["water_dark"], 1.2)
    # 塔の頂の展望 (中心に矢印)
    c.disc(CX, CZ, 3, COL["glass"])
    # 昇る向き
    c.line(-48, 0, -34, 0, COL["arrow"])
    c.line(-19, 10, -19, 20, COL["arrow"])
    c.save("a_top.png")


def a_side():
    c = Side()
    base_side(c)
    # 水位: 第一層は半分、第二層は 3 分の 2、第三層はほぼ満水 (縦穴へ)
    for k, frac in ((1, 0.55), (2, 0.7), (3, 1.0)):
        x1, x2, _, _, fl, h = U[k]
        top = fl + int(h * frac)
        c.rect(x1 + 1, x2 - 1, fl + 1, top, COL["water"])
        # 浮き板
        for gx in range(x1 + 4, x2 - 3, 8):
            c.rect(gx, gx + 2, top, top, COL["plate"])
    # 縦穴を昇る水 (青)
    c.rect(-3, 3, 84, 172, COL["water_dark"])
    # 上端の水際と、塔の頂の展望
    c.rect(-3, 3, 150, 172, COL["water"])
    c.rect(-8, 7, 181, 183, COL["glass"])
    # 出口 (段) の印
    c.rect(-20, -18, 102, 114, COL["stair"])
    c.rect(-19, -9, 115, 126, COL["stair"])
    c.save("a_side.png")


# ------------------------------------------------------------------ 案 B 冠の園 (月の庭と光の聖堂)
def b_top():
    c = Top()
    base_top(c)
    # T2・T3・T4 の庭: 珊瑚の房
    import random
    rnd = random.Random(7)
    cols = ["coral_pink", "coral_orange", "coral_purple", "coral_yellow"]
    for k in (2, 3, 4):
        x1, x2, z1, z2, _ = T[k]
        px1, px2, pz1, pz2, _ = T[k - 1]
        for _ in range(60 if k == 2 else 40 if k == 3 else 22):
            x = rnd.randint(x1 + 1, x2 - 1)
            z = rnd.randint(z1 + 1, z2 - 1)
            if TOWER[0] - 2 <= x <= TOWER[1] + 2 and TOWER[2] - 2 <= z <= TOWER[3] + 2:
                continue
            c.rect(x, x + 1, z, z + 1, COL[rnd.choice(cols)])
    # 塔のまわりの聖堂 (弧の柱)
    for ang in range(0, 360, 30):
        c.b(round(CX + 12 * math.cos(math.radians(ang))), round(CZ + 12 * math.sin(math.radians(ang))), COL["tower"])
    # 光の道 (月の光を真珠で曲げる): 台座 (白) と光 (黄)
    pts = [(-30, 40), (-30, 8), (20, 8), (20, -22), (-14, -22)]
    for (ax, az), (bx, bz) in zip(pts, pts[1:]):
        c.line(ax, az, bx, bz, COL["beam"])
    for (x, z) in pts[1:-1]:
        c.rect(x - 1, x + 1, z - 1, z + 1, COL["mirror"])
    # 珊瑚の心 (光を受けて咲く三つ)
    for (x, z) in ((-30, 40), (20, -22), (-14, -22)):
        c.rect(x - 1, x + 1, z - 1, z + 1, COL["coral_pink"])
    # 上る道 (斜路): 入口の戸 → T1 → 各台地の縁
    c.line(-48, 0, -34, 0, COL["arrow"])
    c.line(-45, 30, -36, 30, COL["arrow"])
    c.line(-30, 36, -22, 36, COL["arrow"])
    # 近道の滑り台 (塔の頂 → 縦穴 → 広間)。縦穴を水色で
    c.disc(CX, CZ, 3, COL["water"])
    c.save("b_top.png")


def b_side():
    c = Side()
    base_side(c)
    import random
    rnd = random.Random(3)
    cols = ["coral_pink", "coral_orange", "coral_purple", "coral_yellow"]
    # 台地の上の珊瑚
    for k in (2, 3, 4):
        x1, x2, _, _, y = T[k]
        for x in range(x1 + 2, x2 - 1, 3):
            h = rnd.randint(2, 6)
            c.rect(x, x + 1, y + 1, y + h, COL[rnd.choice(cols)])
    # 聖堂の丸天井 (塔のまわりに弧)
    for dx in range(-24, 25):
        y = 137 + int(math.sqrt(max(0, 24 * 24 - dx * dx)) * 1.15)
        c.b(dx, y, COL["glass"])
        c.b(dx, y + 1, COL["glass"])
    # 塔の中の螺旋の昇り
    for i in range(0, 44):
        c.b(-5 + (i % 10) if (i // 10) % 2 == 0 else 4 - (i % 10), 138 + i, COL["stair"])
    c.rect(-8, 7, 181, 183, COL["glass"])
    # 近道 (縦穴を下る水の柱)
    c.rect(-3, 3, 84, 172, COL["water_dark"])
    c.save("b_side.png")


# ------------------------------------------------------------------ 案 C 息の間 (亀の肺)
def c_top():
    c = Top()
    base_top(c)
    # 気室: 縦穴を半径 9 まで広げる (U3 を呑み込む)。U1・U2 は回廊
    c.disc(CX, CZ, 10, COL["water_dark"])
    c.disc(CX, CZ, 3, COL["shaft"])
    # 螺旋に並ぶ浮かぶ殻の板 (半径 4〜9 の円周上、角度をずらして上へ)
    for i in range(14):
        ang = i * 38
        r = 6 + (i % 3) * 1.5
        x = round(CX + r * math.cos(math.radians(ang)))
        z = round(CZ + r * math.sin(math.radians(ang)))
        c.rect(x - 1, x, z - 1, z, COL["plate"])
    # 流れの矢印 (反時計まわりの螺旋)
    for i in range(0, 360, 20):
        c.b(round(CX + 12 * math.cos(math.radians(i))), round(CZ + 12 * math.sin(math.radians(i))), COL["arrow"])
    # 頂の番 (中心)
    c.disc(CX, CZ, 2, COL["boss"])
    # 回廊 (U1・U2) と入口
    c.line(-48, 0, -34, 0, COL["arrow"])
    c.line(-19, 10, -19, 20, COL["arrow"])
    c.save("c_top.png")


def c_side():
    c = Side()
    base_side(c)
    # 気室 (半径 9、y 101〜181)。壁の柱を除き、水色の空洞
    c.rect(-9, 9, 102, 172, COL["water_dark"])
    c.rect(-3, 3, 84, 101, COL["shaft"])
    # 広間の縦穴の水の柱 (下から押し上げる)
    c.rect(-3, 3, 84, 101, COL["water"])
    # 浮かぶ殻の板 (らせんに高さをずらす)
    for i in range(14):
        ang = i * 38
        x = round(7 * math.cos(math.radians(ang)))
        y = 106 + i * 5
        c.rect(x - 2, x + 1, y, y, COL["plate"])
    # 回廊の層 (U1・U2)
    for k in (1, 2):
        x1, x2, _, _, fl, h = U[k]
        c.rect(x1, -10, fl + 1, fl + h, COL["room"])
        c.rect(10, x2, fl + 1, fl + h, COL["room"])
    # 頂 (塔のガラスの床 173)。番
    c.rect(-8, 7, 173, 173, COL["glass"])
    c.disc_x(0, 176, 3, COL["boss"])
    c.rect(-8, 7, 181, 183, COL["glass"])
    # 流れ (らせんの矢印を、側面は上向きの白点)
    for y in range(90, 170, 6):
        c.b(-6 if (y // 6) % 2 else 6, y, COL["arrow"])
    c.save("c_side.png")


def base_images():
    c = Top()
    base_top(c)
    c.save("base_top.png")
    s = Side()
    base_side(s)
    s.save("base_side.png")


if __name__ == "__main__":
    base_images()
    a_top()
    a_side()
    b_top()
    b_side()
    c_top()
    c_side()
    print("wrote", OUT)
