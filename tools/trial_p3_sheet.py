# -*- coding: utf-8 -*-
"""試練のプール P3 (岩鯨 W08・潮殻 T03・T04・T08・T11・T12。docs/design/29 の 3.7) の見取り図を 1 枚の表にまとめる。
ゲームは起動せず、セルの座標 (TideDeck・WhaleDeck・各セルの定数) を写した平面図を描く。落ち着いた色・日本語の見出し。

使い方: python -I tools/trial_p3_sheet.py [出力ファイル]
出力: docs/review/previews/trial_p3_whale_tide_sheet.png (git add -f)
"""
import math
import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[1]
OUT = Path(sys.argv[1]) if len(sys.argv) > 1 else ROOT / "docs/review/previews/trial_p3_whale_tide_sheet.png"

BG = (29, 33, 36)
PANEL = (38, 43, 46)
FG = (201, 196, 184)
SUB = (140, 146, 148)
ACC = (120, 160, 150)
WALL = (70, 76, 80)
WATER = (52, 84, 96)
FLOOR = (88, 92, 90)
GOOD = (112, 150, 104)
BAD = (150, 92, 88)
WARN = (176, 160, 96)
PINK = (176, 116, 140)
BONE = (214, 208, 190)

PW, PH, COLS, PAD, CAP = 420, 300, 3, 8, 58


def font(size):
    for p in ("C:/Windows/Fonts/meiryo.ttc", "C:/Windows/Fonts/YuGothM.ttc", "C:/Windows/Fonts/msgothic.ttc"):
        if Path(p).exists():
            return ImageFont.truetype(p, size)
    return ImageFont.load_default()


class Map:
    """x1..x2, z1..z2 (ブロック) を、パネルの中に収めて描く"""

    def __init__(self, d, ox, oy, x1, x2, z1, z2, flip=False):
        self.d = d
        w, h = PW - 24, PH - CAP - 20
        self.s = min(w / (x2 - x1 + 1), h / (z2 - z1 + 1))
        self.ox = ox + 12 + (w - self.s * (x2 - x1 + 1)) / 2
        self.oy = oy + 8 + (h - self.s * (z2 - z1 + 1)) / 2
        self.x1, self.z1 = x1, z1

    def rect(self, x1, x2, z1, z2, fill, outline=None):
        s = self.s
        self.d.rectangle([self.ox + (x1 - self.x1) * s, self.oy + (z1 - self.z1) * s,
                          self.ox + (x2 + 1 - self.x1) * s - 1, self.oy + (z2 + 1 - self.z1) * s - 1], fill=fill, outline=outline)

    def circle(self, cx, cz, r, fill=None, outline=None, width=1):
        s = self.s
        x = self.ox + (cx + 0.5 - self.x1) * s
        y = self.oy + (cz + 0.5 - self.z1) * s
        self.d.ellipse([x - r * s, y - r * s, x + r * s, y + r * s], fill=fill, outline=outline, width=width)

    def line(self, pts, fill, width=2):
        s = self.s
        self.d.line([(self.ox + (x + 0.5 - self.x1) * s, self.oy + (z + 0.5 - self.z1) * s) for x, z in pts], fill=fill, width=width)

    def text(self, x, z, t, f, fill=FG):
        self.d.text((self.ox + (x - self.x1) * self.s, self.oy + (z - self.z1) * self.s), t, font=f, fill=fill)


def shadow_start(l, p):
    return 2 * p - l + 4


def w08(d, ox, oy, f):
    m = Map(d, ox, oy, -49, -31, 92, 117)
    m.rect(-48, -32, 98, 115, FLOOR)
    # 幕 (北の壁): 目標の紋 / 合っているか / いまの影 (灯 2・柱 1 の例)
    lamps, pillars = [2, 3], [2]
    dark = [all(any(shadow_start(l, p) <= c <= shadow_start(l, p) + 2 for p in pillars) for l in lamps) for c in range(15)]
    for c in range(15):
        m.rect(-47 + c, -47 + c, 94, 94, (20, 20, 22) if dark[c] else (110, 150, 150))
        m.rect(-47 + c, -47 + c, 96, 96, GOOD)
    # 灯の列 (z 110)・柱の列 (z 104)
    for k in range(5):
        m.rect(-41 + k, -41 + k, 110, 110, WARN if k in lamps else WALL)
    for p in pillars:
        m.rect(-42 + p, -41 + p, 103, 104, BONE)
    # 光の筋 (灯 → 柱の縁 → 幕)
    for l in lamps:
        lx = -41 + l + 0.5
        for e in (-42 + pillars[0], -40 + pillars[0]):
            sx = lx + (e - lx) * 2
            m.line([(lx - 0.5, 110), (e - 0.5, 104), (sx - 0.5, 98)], WARN, 1)
    # 台 (灯 右・柱 左)
    for i in range(3):
        for k in range(5):
            m.rect(-37 + k, -37 + k, 98 + 0, 98 + 0, FLOOR) if False else None
    m.text(-47, 112, "柱", f, SUB)
    m.text(-37, 112, "灯", f, SUB)
    m.rect(-31, -30, 104, 108, GOOD)  # 出口の戸 (東の壁)
    m.rect(-42, -38, 116, 117, BAD)  # 入口の戸 (S1 の北の壁)


def t03(d, ox, oy, f):
    m = Map(d, ox, oy, 0, 16, 75, 110)
    m.rect(0, 16, 75, 78, FLOOR)
    m.rect(0, 16, 79, 107, WATER)
    m.rect(5, 11, 91, 94, FLOOR)
    m.rect(0, 16, 108, 110, FLOOR)
    centers = [[3, 12], [7, 14], [4, 10], [5, 11], [8, 3], [12, 6]]
    zs = [80, 84, 88, 96, 101, 106]
    for xs, z in zip(centers, zs):
        for x in xs:
            m.rect(x - 1, x + 1, z - 1, z + 1, PINK)
    for z in (98, 103):
        m.rect(0, 16, z, z + 1, (150, 96, 96))
    m.text(1, 98.2, "触手の線", f, BG)
    m.text(6, 92, "休み島", f, BG)


def t04(d, ox, oy, f):
    m = Map(d, ox, oy, 19, 33, 75, 115)
    m.rect(20, 32, 75, 78, FLOOR)
    m.rect(22, 30, 79, 111, WATER)
    m.rect(20, 32, 112, 114, FLOOR)
    for k, z0 in enumerate((81, 92, 103)):
        fwd = (0, 0, 1)[k]
        m.rect(22, 25, z0, z0 + 8, GOOD if fwd == 0 else BAD)
        m.rect(27, 30, z0, z0 + 8, BAD if fwd == 0 else GOOD)
        m.rect(26, 26, z0, z0 + 8, WALL)
        for w in (2, 6):
            m.rect(26, 26, z0 + w, z0 + w, WATER)
    for (x, z) in ((31, 77), (32, 91), (32, 102)):
        m.rect(x, x, z, z, WARN)


def t08(d, ox, oy, f):
    m = Map(d, ox, oy, 55, 73, 74, 93)
    m.rect(56, 72, 75, 92, FLOOR)
    for s in range(14):
        x = 71 - 2 * (s // 7)
        z = 78 + 2 * (s % 7)
        m.rect(x, x, z, z, (150, 110, 120) if s % 3 == 0 else (110, 130, 150) if s % 3 == 1 else (170, 150, 100))
    for b in range(4):
        z = 78 + 4 * b
        m.rect(58, 58, z, z, WALL if b == 3 else ACC)
        m.rect(56, 56, z, z, (30, 30, 30) if b == 3 else BONE)
    m.rect(60, 60, 76, 76, WARN)
    m.text(62, 90, "割れ鉢=黒い札", f, SUB)


def t11(d, ox, oy, f):
    m = Map(d, ox, oy, 35, 53, 74, 107)
    m.rect(36, 52, 75, 106, FLOOR)
    route = [(44, 79), (44, 86), (39, 86), (39, 94), (49, 94), (49, 99), (44, 99), (44, 105)]
    m.line(route, ACC, 3)
    length = sum(abs(a[0] - b[0]) + abs(a[1] - b[1]) for a, b in zip(route, route[1:]))
    for k in (1, 2):
        s = length * k / 3
        left = s
        for a, b in zip(route, route[1:]):
            seg = abs(a[0] - b[0]) + abs(a[1] - b[1])
            if left <= seg:
                t = left / seg
                x, z = a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t
                break
            left -= seg
        m.circle(x, z, 1.5, outline=BAD, width=2)
        m.rect(37, 37, int(z), int(z), BAD)
        m.rect(51, 51, int(z), int(z), BAD)
    m.rect(44 - 1, 44 + 1, 78, 79, GOOD)
    m.text(37, 106, "祠", f, SUB)


def t12(d, ox, oy, f):
    m = Map(d, ox, oy, 74, 96, 74, 103)
    m.circle(85, 85, 10.4, fill=FLOOR)
    m.circle(85, 85, 8.5, fill=(98, 104, 100))
    m.circle(85, 85, 6.8, outline=PINK, width=2)
    m.circle(85, 85, 6.0, outline=WARN, width=1)
    m.rect(85, 85, 83, 83, BONE)
    m.rect(83, 87, 73, 74, BAD)
    m.rect(83, 87, 96, 97, GOOD)
    m.text(75, 100, "幕 (一人・二人) / 全面 (三人から)", f, SUB)


PANELS = [
    (w08, "岩鯨 W08 影絵の間", "灯と柱の影を幕の紋に重ねる。灯が増えると重なりになる"),
    (t03, "潮殻 T03 くらげの回廊", "沈む傘を渡り、触手の線の拍をかいくぐる"),
    (t04, "潮殻 T04 渦潮の滑り台", "緑の水路は前へ・赤は後ろへ。拍で入れ替わる"),
    (t08, "潮殻 T08 珊瑚の標本棚", "札は色→丈→色と丈。合わなければ割れ鉢"),
    (t11, "潮殻 T11 大貝の護送", "貝の前で呼んで引く。潮の刻限と襲撃"),
    (t12, "潮殻 T12 真珠守りの試し", "守り手の技・殻の群れ・真珠。少人数は幕"),
]


def main():
    rows = math.ceil(len(PANELS) / COLS)
    head = 56
    img = Image.new("RGB", (COLS * (PW + PAD) + PAD, head + rows * (PH + PAD) + PAD), BG)
    d = ImageDraw.Draw(img)
    d.text((PAD + 6, 10), "試練のプール P3: 岩鯨・潮殻が十二ずつに (見取り図)", font=font(22), fill=FG)
    d.text((PAD + 6, 36), "岩鯨 W08 と、潮殻の下の階 (T03・T04・T11・T08・T12)。ゲームを起動せず、セルの座標から描いた平面図", font=font(12), fill=SUB)
    f12, f13, f15 = font(11), font(13), font(15)
    for i, (fn, title, sub) in enumerate(PANELS):
        ox = PAD + (i % COLS) * (PW + PAD)
        oy = head + (i // COLS) * (PH + PAD)
        d.rectangle([ox, oy, ox + PW - 1, oy + PH - 1], fill=PANEL)
        fn(d, ox, oy, f12)
        d.text((ox + 10, oy + PH - CAP + 4), title, font=f15, fill=FG)
        d.text((ox + 10, oy + PH - CAP + 26), sub, font=f13, fill=SUB)
    OUT.parent.mkdir(parents=True, exist_ok=True)
    img.save(OUT)
    print("wrote", OUT)


if __name__ == "__main__":
    main()
