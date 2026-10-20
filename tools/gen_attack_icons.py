# -*- coding: utf-8 -*-
"""ボスの技の名前の左に出す 16px のドット絵の印 (四つの心臓・中ボス・雷獣・灰の鍛冶の、全ての技) を書く。
使い方: python tools/gen_attack_icons.py [--sheet 出力.png]
入力: src/main/resources/assets/*/lang/ja_jp.json の gui.skyabyss.attack.<ボス>.<技> (技の一覧はここから読む。印の無い技があれば止まる)
出力: src/main/resources/assets/skyabyss/textures/gui/attack/<ボス>_<技>.png (32x32。16px の絵を 2 倍に引き伸ばしたもの。雷翼の心臓の warn_*.png と同じ大きさ・同じ円形の枠)
絵の作り: 円形の枠 (ボスごとの色) + 暗い紺の地 + 技の形 (クリーム色 + ボスごとの差し色)。技の形は下の GLYPHS の名前で、技ごとの割り当ては ASSIGN。
"""
import json
import math
import sys
from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
LANG_DIR = ROOT / "src/main/resources/assets"
OUT = ROOT / "src/main/resources/assets/skyabyss/textures/gui/attack"

BG = (20, 24, 36, 255)
CREAM = (255, 240, 170, 255)
WHITE = (236, 240, 246, 255)
# ボスごとの (枠の色, 差し色)
BOSS = {
    "whale": ((176, 190, 170, 255), (150, 168, 140, 255)),
    "tide": ((92, 143, 208, 255), (110, 200, 240, 255)),
    "storm": ((196, 170, 255, 255), (255, 226, 90, 255)),
    "ember": ((240, 130, 60, 255), (255, 90, 40, 255)),
    "captain": ((226, 220, 200, 255), (120, 200, 232, 255)),
    "elder_sentinel": ((168, 176, 192, 255), (255, 196, 64, 255)),
    "elder_sentry": ((168, 176, 192, 255), (255, 196, 64, 255)),
    "pearl_keeper": ((226, 150, 176, 255), (250, 214, 226, 255)),
    "tide_beckoner": ((63, 180, 174, 255), (134, 228, 218, 255)),
    "raiju": ((236, 200, 70, 255), (130, 190, 255, 255)),
    "ash_smith": ((190, 96, 56, 255), (255, 150, 60, 255)),
}


class Cv:
    def __init__(self, rim, acc):
        self.img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
        self.d = ImageDraw.Draw(self.img)
        self.rim = rim
        self.acc = acc
        # 円形の枠: 半径 7.5 の円盤を縁の色で塗り、半径 6 を地の色で塗る
        for y in range(16):
            for x in range(16):
                r = math.hypot(x - 7.5, y - 7.5)
                if r <= 7.7:
                    self.img.putpixel((x, y), rim if r > 6.3 else BG)

    def p(self, x, y, c=None):
        if 0 <= x < 16 and 0 <= y < 16 and math.hypot(x - 7.5, y - 7.5) <= 6.3:
            self.img.putpixel((x, y), c or CREAM)

    def line(self, a, b, c=None):
        n = int(max(abs(b[0] - a[0]), abs(b[1] - a[1]))) or 1
        for i in range(n + 1):
            self.p(round(a[0] + (b[0] - a[0]) * i / n), round(a[1] + (b[1] - a[1]) * i / n), c)

    def rect(self, x0, y0, x1, y1, c=None):
        for y in range(y0, y1 + 1):
            for x in range(x0, x1 + 1):
                self.p(x, y, c)

    def arc(self, cx, cy, r, a0, a1, c=None):
        steps = max(8, int(abs(a1 - a0) * r))
        for i in range(steps + 1):
            a = math.radians(a0 + (a1 - a0) * i / steps)
            self.p(round(cx + math.cos(a) * r), round(cy + math.sin(a) * r), c)

    def disc(self, cx, cy, r, c=None):
        for y in range(16):
            for x in range(16):
                if math.hypot(x - cx, y - cy) <= r:
                    self.p(x, y, c)

    def poly(self, pts, c=None):
        for y in range(16):
            for x in range(16):
                if inside(pts, x, y):
                    self.p(x, y, c)


def inside(pts, x, y):
    ok = False
    j = len(pts) - 1
    for i in range(len(pts)):
        xi, yi = pts[i]
        xj, yj = pts[j]
        if (yi > y) != (yj > y) and x < (xj - xi) * (y - yi) / (yj - yi + 1e-9) + xi:
            ok = not ok
        j = i
    return ok


# ---- 技の形 (cv に描く。acc = 差し色) ----
def g_wave(c):  # 広がる波の輪
    c.arc(8, 11, 3, 190, 350)
    c.arc(8, 11, 5, 200, 340, c.acc)
    c.arc(8, 11, 7, 215, 325)
    c.p(8, 10, WHITE)


def g_ring(c):  # 輪 (切れ目つき)
    c.arc(8, 8, 4.5, 20, 160)
    c.arc(8, 8, 4.5, 200, 340)
    c.p(8, 8, c.acc)


def g_spike(c):  # 棘
    for x, h in ((4, 5), (8, 8), (11, 5)):
        c.poly([(x - 1.5, 12.5), (x, 12.5 - h), (x + 1.5, 12.5)], CREAM if x == 8 else c.acc)


def g_spike_ring(c):  # 輪に並ぶ棘
    for i in range(6):
        a = math.pi * 2 * i / 6 + 0.3
        c.p(round(8 + math.cos(a) * 4.2), round(8 + math.sin(a) * 4.2))
        c.p(round(8 + math.cos(a) * 3.2), round(8 + math.sin(a) * 3.2), c.acc)
    c.p(8, 8, WHITE)


def g_beam(c):  # 光線
    c.line((4, 12), (12, 4))
    c.line((5, 12), (12, 5), c.acc)
    c.disc(4.5, 11.5, 1.6, WHITE)


def g_claw(c):  # 三つの爪痕
    for dx in (-3, 0, 3):
        c.line((6 + dx, 3), (4 + dx + 4, 12), CREAM if dx == 0 else c.acc)


def g_roar(c):  # 咆哮 (音の弧)
    c.disc(5, 8, 1.5, WHITE)
    c.arc(5, 8, 3.5, -50, 50)
    c.arc(5, 8, 5.5, -50, 50, c.acc)
    c.arc(5, 8, 7.5, -45, 45)


def g_breath(c):  # 息 (広がる扇)
    c.poly([(3, 8), (12, 3), (12, 13)], c.acc)
    c.poly([(3, 8), (10, 5.5), (10, 10.5)], CREAM)
    c.p(3, 8, WHITE)


def g_slam(c):  # 打ち下ろし
    c.rect(5, 3, 10, 5)
    c.rect(7, 6, 8, 9, c.acc)
    c.line((3, 12), (12, 12), CREAM)
    c.line((4, 10), (2, 8), c.acc)
    c.line((11, 10), (13, 8), c.acc)


def g_rush(c):  # 突進 (矢と速さの線)
    c.line((3, 8), (12, 8))
    c.line((9, 5), (12, 8))
    c.line((9, 11), (12, 8))
    c.line((2, 5), (6, 5), c.acc)
    c.line((2, 11), (6, 11), c.acc)


def g_leap(c):  # 跳躍
    c.arc(8, 13, 6, 200, 340, c.acc)
    c.line((8, 12), (8, 7))
    c.line((6, 10), (8, 12))
    c.line((10, 10), (8, 12))


def g_rain(c):  # 雨
    for x, y in ((4, 3), (8, 5), (12, 3), (6, 9)):
        c.line((x, y), (x, y + 3), c.acc)
        c.p(x, y + 4)
    c.p(11, 9, c.acc)
    c.p(11, 10)


def g_chain(c):  # 鎖
    c.arc(5.5, 8, 2.5, 0, 360)
    c.arc(10.5, 8, 2.5, 0, 360, c.acc)


def g_shell(c):  # 殻
    c.arc(8, 11, 5, 180, 360)
    c.line((3, 11), (13, 11))
    c.line((8, 6), (8, 11), c.acc)
    c.line((5, 7), (6, 11), c.acc)
    c.line((11, 7), (10, 11), c.acc)


def g_shell_drop(c):  # 殻の落下
    c.arc(8, 8, 4, 180, 360)
    c.line((4, 8), (12, 8))
    c.line((8, 10), (8, 13), c.acc)
    c.line((6, 12), (8, 14), c.acc)
    c.line((10, 12), (8, 14), c.acc)


def g_reclose(c):  # 殻の再閉 (向き合う矢)
    c.line((2, 8), (6, 8))
    c.line((4, 6), (6, 8))
    c.line((4, 10), (6, 8))
    c.line((14, 8), (10, 8), c.acc)
    c.line((12, 6), (10, 8), c.acc)
    c.line((12, 10), (10, 8), c.acc)


def g_pearl(c):  # 真珠
    c.disc(8, 8, 3.5, WHITE)
    c.disc(8, 8, 2.4, c.acc)
    c.p(7, 6, WHITE)
    c.p(6, 7, WHITE)


def g_pearl_rain(c):  # 真珠の雨
    for x, y in ((4, 3), (9, 4), (6, 8), (11, 9)):
        c.disc(x, y, 1.2, WHITE)
        c.p(x, y + 2, c.acc)
        c.p(x, y + 3, c.acc)


def g_pearl_volley(c):  # 真珠の斉射
    for y in (4, 8, 12):
        c.line((3, y), (9, y), c.acc)
        c.disc(11, y, 1.2, WHITE)


def g_orb(c):  # 玉
    c.disc(8, 8, 4.5, c.acc)
    c.disc(8, 8, 3, CREAM)
    c.p(7, 7, WHITE)


def g_floor(c):  # 崩れる床
    c.rect(3, 4, 12, 4)
    c.rect(3, 12, 12, 12)
    c.line((3, 4), (3, 12))
    c.line((12, 4), (12, 12))
    c.line((7, 4), (9, 8), c.acc)
    c.line((9, 8), (7, 12), c.acc)
    c.line((9, 8), (12, 9), c.acc)


def g_wall(c):  # 壁
    for y in (4, 8, 12):
        c.line((3, y), (12, y))
    c.line((7, 4), (7, 8), c.acc)
    c.line((10, 8), (10, 12), c.acc)
    c.line((5, 8), (5, 12), c.acc)


def g_lead(c):  # 先回り (狙いの印と矢)
    c.arc(7, 8, 4, 0, 360)
    c.p(7, 8, c.acc)
    c.line((7, 2), (7, 4), c.acc)
    c.line((7, 12), (7, 14), c.acc)
    c.line((11, 8), (14, 8), c.acc)
    c.line((12, 6), (14, 8), c.acc)
    c.line((12, 10), (14, 8), c.acc)


def g_chase(c):  # 追い薙ぎ (曲がる矢)
    c.arc(8, 8, 5, 40, 250, c.acc)
    c.line((3, 6), (5, 9))
    c.line((3, 6), (7, 6))
    c.p(8, 8, WHITE)


def g_up(c):  # 上げ潮・昇り
    c.line((8, 3), (8, 10))
    c.line((5, 6), (8, 3))
    c.line((11, 6), (8, 3))
    c.arc(5, 12, 2, 180, 360, c.acc)
    c.arc(9, 12, 2, 180, 360, c.acc)


def g_down(c):  # 引き潮・押し潮
    c.line((8, 3), (8, 10))
    c.line((5, 7), (8, 10))
    c.line((11, 7), (8, 10))
    c.arc(5, 12, 2, 180, 360, c.acc)
    c.arc(9, 12, 2, 180, 360, c.acc)


def g_whirl(c):  # 渦
    c.arc(8, 8, 2, 0, 270)
    c.arc(8, 8, 4, 90, 360, c.acc)
    c.arc(8, 8, 6, 180, 450)
    c.p(8, 8, WHITE)


def g_bubble(c):  # 泡
    c.arc(8, 8, 4.5, 0, 360, c.acc)
    c.p(6, 6, WHITE)
    c.p(7, 5, WHITE)
    c.arc(8, 8, 1.5, 0, 360)


def g_bars(c):  # 檻の格子
    for x in (4, 7, 10, 13):
        c.line((x, 3), (x, 13), CREAM if x != 7 else c.acc)
    c.line((3, 4), (13, 4), c.acc)
    c.line((3, 12), (13, 12), c.acc)


def g_cross(c):  # 斜めの格子
    c.line((3, 3), (13, 13))
    c.line((13, 3), (3, 13), c.acc)
    c.line((3, 8), (13, 8), c.acc)
    c.line((8, 3), (8, 13), c.acc)


def g_pillar(c):  # 雷の柱
    c.poly([(9, 2), (5, 8), (8, 8), (6, 14), (11, 6), (8, 6)], c.acc)
    c.line((8, 3), (6, 8))
    c.p(7, 9, WHITE)


def g_bolt(c):  # 稲妻
    c.poly([(9, 2), (5, 8), (8, 8), (6, 14), (11, 6), (8, 6)], c.acc)


def g_rail(c):  # 雷の走り
    c.line((2, 8), (13, 8), c.acc)
    c.line((4, 5), (6, 8))
    c.line((6, 8), (8, 5))
    c.line((8, 5), (10, 8))
    c.line((10, 8), (12, 5))


def g_horn(c):  # 骨笛・角笛
    c.poly([(3, 7), (3, 9), (8, 12), (12, 12), (12, 4), (8, 4)], CREAM)
    c.rect(3, 7, 4, 9, c.acc)
    c.arc(12, 8, 3, -60, 60, c.acc)


def g_flute(c):  # 骨の笛
    c.line((3, 12), (12, 4), CREAM)
    c.line((4, 12), (13, 4), CREAM)
    for t in (0.35, 0.55, 0.75):
        c.p(round(3 + 9 * t), round(12 - 8 * t), c.acc)


def g_stomp(c):  # 踏みしめ
    c.rect(5, 3, 10, 7)
    c.rect(4, 8, 11, 9, c.acc)
    c.line((2, 12), (13, 12))
    c.line((3, 10), (1, 12), c.acc)
    c.line((12, 10), (14, 12), c.acc)


def g_swing(c):  # 振りかぶり / 薙ぎ払い
    c.arc(8, 11, 7, 200, 340)
    c.arc(8, 11, 5, 210, 330, c.acc)
    c.line((11, 6), (13, 4), WHITE)


def g_thrust(c):  # 突き
    c.line((2, 8), (12, 8))
    c.poly([(10, 5), (14, 8), (10, 11)], c.acc)
    c.line((2, 6), (5, 6), c.acc)
    c.line((2, 10), (5, 10), c.acc)


def g_crack(c):  # 亀裂の爆発
    c.line((3, 8), (6, 5))
    c.line((6, 5), (8, 9))
    c.line((8, 9), (11, 4))
    c.line((11, 4), (13, 8))
    c.line((8, 9), (7, 13), c.acc)
    c.line((8, 9), (12, 12), c.acc)
    c.p(8, 9, WHITE)


def g_erupt(c):  # 噴出 (扇状の骨)
    for dx, h in ((-4, 4), (-2, 6), (0, 8), (2, 6), (4, 4)):
        c.line((8 + dx // 2, 13), (8 + dx, 13 - h), CREAM if dx == 0 else c.acc)


def g_volley(c):  # 投射 (矢が並ぶ)
    for y in (4, 8, 12):
        c.line((3, y), (10, y), c.acc)
        c.line((8, y - 1), (11, y))
        c.line((8, y + 1), (11, y))


def g_sparks(c):  # 火花
    for x, y in ((4, 4), (11, 3), (8, 8), (4, 11), (12, 11)):
        c.p(x, y, WHITE)
        c.p(x + 1, y, c.acc)
        c.p(x - 1, y, c.acc)
        c.p(x, y + 1, c.acc)
        c.p(x, y - 1, c.acc)


def g_quench(c):  # 焼き入れ (炎に雫)
    c.poly([(8, 5), (4, 12), (12, 12)], c.acc)
    c.poly([(8, 8), (6, 12), (10, 12)], CREAM)
    c.line((12, 2), (12, 4), WHITE)
    c.p(12, 5, WHITE)


def g_rage(c):  # 怒り
    c.poly([(8, 2), (5, 7), (7, 7), (4, 13), (12, 6), (9, 6), (11, 2)], c.acc)
    c.line((8, 4), (7, 8))
    c.p(7, 10, WHITE)


def g_forge(c):  # 炉打ち (金床)
    c.rect(3, 5, 12, 7)
    c.rect(6, 8, 9, 10)
    c.rect(4, 11, 11, 12, c.acc)
    c.line((12, 5), (14, 4))
    c.p(13, 3, WHITE)


def g_embers(c):  # 残り火
    for x, y in ((5, 11), (9, 9), (11, 5), (7, 6), (4, 7)):
        c.p(x, y, c.acc)
        c.p(x, y - 1, CREAM)


def g_fading(c):  # 消えかけの鼓動
    c.poly([(8, 12), (3, 7), (4, 4), (8, 6), (12, 4), (13, 7)], c.acc)
    c.line((5, 8), (11, 8), BG)
    c.p(8, 7, WHITE)


def g_pulse(c):  # 熱の脈
    c.poly([(8, 12), (3, 7), (4, 4), (8, 6), (12, 4), (13, 7)], CREAM)
    c.arc(8, 8, 6, 0, 360, c.acc)


def g_gale(c):  # 嵐 (風の渦線)
    c.line((3, 5), (11, 5), c.acc)
    c.arc(11, 7, 2, -90, 90, c.acc)
    c.line((3, 8), (9, 8))
    c.line((3, 11), (11, 11), c.acc)
    c.arc(11, 9.5, 1.5, 270, 450)


def g_pellet(c):  # 礫
    for x, y in ((4, 8), (7, 6), (7, 10), (10, 4), (10, 8), (10, 12)):
        c.disc(x, y, 1.1, CREAM if x != 7 else c.acc)


def g_veil(c):  # 帳
    c.line((3, 3), (13, 3))
    for x in (4, 7, 10, 13):
        c.line((x - 1, 4), (x - 1, 12), c.acc if x % 2 else CREAM)
    c.p(8, 13, WHITE)


def g_burrow(c):  # 潜り
    c.arc(8, 11, 5, 180, 360, c.acc)
    c.line((8, 3), (8, 8))
    c.line((6, 6), (8, 8))
    c.line((10, 6), (8, 8))
    c.line((3, 11), (13, 11))


def g_beckon(c):  # 招き (内へ向かう矢)
    c.line((2, 8), (6, 8))
    c.line((4, 6), (6, 8))
    c.line((4, 10), (6, 8))
    c.line((14, 8), (10, 8), c.acc)
    c.line((12, 6), (10, 8), c.acc)
    c.line((12, 10), (10, 8), c.acc)
    c.p(8, 8, WHITE)


def g_moon(c):  # 月潮
    c.arc(7, 7, 4, 40, 320)
    c.arc(9, 6, 3, 60, 300, c.acc)
    c.arc(8, 13, 4, 200, 340, c.acc)


def g_spring(c):  # 大潮 (大波)
    c.arc(5, 13, 6, 270, 360)
    c.arc(5, 13, 4, 270, 360, c.acc)
    c.arc(11, 13, 4, 180, 270)
    c.line((3, 13), (13, 13), c.acc)


def g_descend(c):  # 降下
    c.line((8, 2), (8, 9))
    c.line((5, 6), (8, 9))
    c.line((11, 6), (8, 9))
    c.line((3, 12), (13, 12), c.acc)
    c.line((5, 14), (11, 14), c.acc)


def g_return(c):  # 跳び戻り
    c.arc(8, 12, 5, 200, 340, c.acc)
    c.line((3, 9), (3, 12))
    c.line((3, 9), (6, 9))
    c.line((3, 9), (6, 11))


def g_pounce(c):  # 飛びかかり
    c.arc(5, 12, 7, 250, 345, c.acc)
    c.line((12, 5), (12, 9))
    c.line((9, 5), (12, 5))
    c.p(12, 5, WHITE)
    c.line((11, 10), (13, 10))


def g_arrow_up(c):
    g_up(c)


GLYPHS = {
    "wave": g_wave, "ring": g_ring, "spike": g_spike, "spike_ring": g_spike_ring, "beam": g_beam, "claw": g_claw, "roar": g_roar,
    "breath": g_breath, "slam": g_slam, "rush": g_rush, "leap": g_leap, "rain": g_rain, "chain": g_chain, "shell": g_shell,
    "shell_drop": g_shell_drop, "reclose": g_reclose, "pearl": g_pearl, "pearl_rain": g_pearl_rain, "pearl_volley": g_pearl_volley,
    "orb": g_orb, "floor": g_floor, "wall": g_wall, "lead": g_lead, "chase": g_chase, "up": g_up, "down": g_down, "whirl": g_whirl,
    "bubble": g_bubble, "bars": g_bars, "cross": g_cross, "pillar": g_pillar, "bolt": g_bolt, "rail": g_rail, "horn": g_horn,
    "flute": g_flute, "stomp": g_stomp, "swing": g_swing, "thrust": g_thrust, "crack": g_crack, "erupt": g_erupt, "volley": g_volley,
    "sparks": g_sparks, "quench": g_quench, "rage": g_rage, "forge": g_forge, "embers": g_embers, "fading": g_fading, "pulse": g_pulse,
    "gale": g_gale, "pellet": g_pellet, "veil": g_veil, "burrow": g_burrow, "beckon": g_beckon, "moon": g_moon, "spring": g_spring,
    "descend": g_descend, "return": g_return, "pounce": g_pounce,
}

# (ボス, 技) -> 形。技の一覧 (lang) と食い違えば止まる
ASSIGN = {
    ("whale", "abyss_spines"): "spike_ring", ("tide", "abyss_undertow"): "whirl", ("storm", "abyss_pillars"): "pillar",
    ("ember", "abyss_rain"): "rain",
    ("whale", "thrust"): "thrust", ("whale", "sweep"): "swing", ("whale", "leap"): "leap", ("whale", "crack"): "crack",
    ("whale", "reclose"): "reclose", ("whale", "volley"): "volley", ("whale", "beam"): "beam", ("whale", "chain"): "chain",
    ("whale", "shell_drop"): "shell_drop", ("whale", "lead"): "lead", ("whale", "cutoff"): "wall", ("whale", "chase"): "chase",
    ("whale", "sea_wave"): "wave", ("whale", "sea_spike"): "spike_ring",
    ("tide", "breath"): "breath", ("tide", "shell_strike"): "slam", ("tide", "whirl"): "whirl", ("tide", "flood"): "spike",
    ("tide", "bubble"): "bubble", ("tide", "pearl"): "pearl", ("tide", "ebb"): "down", ("tide", "moon_wave"): "moon",
    ("tide", "pearl_rain"): "pearl_rain", ("tide", "spring_tide"): "spring", ("tide", "lead"): "lead", ("tide", "cutoff"): "wall",
    ("tide", "chase"): "chase", ("tide", "flood_rise"): "up", ("tide", "flood_ebb"): "down",
    ("storm", "lead"): "lead", ("storm", "cutoff"): "wall", ("storm", "chase"): "chase", ("storm", "cage_bars"): "bars",
    ("storm", "cage_cross"): "cross",
    ("ember", "breath"): "breath", ("ember", "pulse"): "pulse", ("ember", "orb"): "orb", ("ember", "floor"): "floor",
    ("ember", "gale"): "gale", ("ember", "pellet"): "pellet", ("ember", "embers"): "embers", ("ember", "fading"): "fading",
    ("ember", "veil"): "veil", ("ember", "forge"): "forge", ("ember", "lead"): "lead", ("ember", "cutoff"): "wall",
    ("ember", "chase"): "chase", ("ember", "core_rings"): "ring", ("ember", "core_fans"): "breath",
    ("captain", "sweep"): "swing", ("captain", "leap"): "leap", ("captain", "erupt"): "erupt", ("captain", "roar"): "roar",
    ("elder_sentinel", "rush"): "rush", ("elder_sentinel", "stomp"): "stomp", ("elder_sentinel", "flute"): "flute",
    ("elder_sentry", "swing"): "swing", ("elder_sentry", "rush"): "rush", ("elder_sentry", "horn"): "horn",
    ("elder_sentry", "stomp"): "stomp",
    ("pearl_keeper", "updraft"): "up", ("pearl_keeper", "downdraft"): "down", ("pearl_keeper", "charge"): "rush",
    ("pearl_keeper", "volley"): "pearl_volley", ("pearl_keeper", "shell"): "shell",
    ("tide_beckoner", "slam"): "slam", ("tide_beckoner", "beckon"): "beckon", ("tide_beckoner", "breath"): "breath",
    ("tide_beckoner", "burrow"): "burrow", ("tide_beckoner", "roar"): "roar",
    ("raiju", "rail"): "rail", ("raiju", "descend"): "descend", ("raiju", "pounce"): "pounce", ("raiju", "claw"): "claw",
    ("raiju", "roar"): "roar", ("raiju", "return"): "return",
    ("ash_smith", "slam"): "slam", ("ash_smith", "breath"): "breath", ("ash_smith", "quench"): "quench",
    ("ash_smith", "sparks"): "sparks", ("ash_smith", "rage"): "rage",
}


def attacks():
    out = []
    for f in sorted(LANG_DIR.glob("*/lang/ja_jp.json")):
        for k in json.load(open(f, encoding="utf-8")):
            if k.startswith("gui.skyabyss.attack."):
                boss, atk = k[len("gui.skyabyss.attack."):].split(".", 1)
                if (boss, atk) not in out:
                    out.append((boss, atk))
    return out


def make(boss, atk):
    rim, acc = BOSS[boss]
    cv = Cv(rim, acc)
    GLYPHS[ASSIGN[(boss, atk)]](cv)
    return cv.img.resize((32, 32), Image.NEAREST)


def main():
    keys = attacks()
    missing = [k for k in keys if k not in ASSIGN]
    if missing:
        sys.exit("印の割り当てがない技: %s" % missing)
    OUT.mkdir(parents=True, exist_ok=True)
    for boss, atk in keys:
        make(boss, atk).save(OUT / ("%s_%s.png" % (boss, atk)))
    print("%d 個の印を書いた -> %s" % (len(keys), OUT))
    if "--sheet" in sys.argv:
        path = sys.argv[sys.argv.index("--sheet") + 1]
        cols = 14
        rows = (len(keys) + cols - 1) // cols
        sheet = Image.new("RGBA", (cols * 70, rows * 70), (44, 48, 60, 255))
        for i, (boss, atk) in enumerate(keys):
            im = make(boss, atk).resize((64, 64), Image.NEAREST)
            sheet.paste(im, ((i % cols) * 70 + 3, (i // cols) * 70 + 3), im)
        sheet.save(path)


if __name__ == "__main__":
    main()
