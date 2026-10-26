"""章の変わり目の語り (一〜四章の題字の前に 5 枚ずつ)。pxkit / parts で描く。

  python tools/story/pixel/chapters.py [章の ID ...]

出力は docs/review/story_pixel/chapter_<章の ID>_<絵>.png (240x135) と _x4.png。
"""
import math
import sys
from pathlib import Path

import numpy as np

import pxkit as K
import parts as P
from opening import moon, puffs
from opening_more import DEPTH

OUT = K.ROOT / "docs/review/story_pixel"
S = (12, 12, 16)


# ---------------------------------------------------------------- 一章 岩鯨「空を泳ぐもの」
WARM = ["#0c0806", "#22160e", "#3e2814", "#6a4420", "#a06a30", "#d8a050", "#f4d690", "#fff4d0", "#1a1820", "#3a3440", "#7a5a3a"]


def w1():
    # 都の鐘楼の中、蝋燭の灯で語り部が古い歌を口ずさむ。頭の上に大鐘の影
    c = K.Canvas()
    c.vgrad([(0, (16, 10, 8)), (1, (34, 22, 14))])
    c.glow(120, 92, 110, (120, 70, 24), 1.0)
    # 壁の石積みのかすかな目地
    for y in range(8, 120, 9):
        for x in range((y // 9) % 2 * 9, c.w, 18):
            c.px[y, x:x + 16] = c.px[y, x:x + 16] * 0.86
    # 大鐘 (上から吊られた影。下の縁に蝋燭の灯が映る)
    c.poly([(98, 0), (100, 8), (96, 26), (88, 42), (152, 42), (144, 26), (140, 8), (142, 0)], (34, 24, 16))
    c.poly([(84, 42), (156, 42), (158, 46), (82, 46)], (52, 36, 22))
    P.line(c, 84, 46, 158, 46, (200, 140, 70))
    P.line(c, 90, 41, 150, 41, (120, 80, 40))
    c.poly([(116, 46), (124, 46), (123, 54), (117, 54)], (40, 28, 18))
    # 床と蝋燭
    c.poly([(0, 118), (240, 118), (240, 135), (0, 135)], (28, 18, 12))
    c.poly([(150, 100), (155, 100), (155, 118), (150, 118)], (220, 200, 160))
    c.disc(152.5, 96, 2.4, (255, 230, 150))
    c.glow(152, 95, 46, (120, 80, 20), 1.0)
    # 語り部 (頭巾の年寄り。蝋燭の方を向いて背を丸めて座る。灯の側の縁が明るい)
    ox, oy = 104, 76
    c.poly([(ox - 22, 118), (ox - 16, 96), (ox - 6, 84), (ox + 8, 82), (ox + 20, 90), (ox + 26, 104), (ox + 30, 118)], S)
    c.disc(ox + 6, 80, 11, S)
    c.poly([(ox + 10, 70), (ox + 18, 76), (ox + 17, 88), (ox + 10, 86)], (6, 6, 8))
    for y in range(72, 118):
        xs = [x for x in range(ox - 24, ox + 34) if (c.px[y, x] == np.array(S, float)).all()]
        if xs:
            xr = max(xs)
            c.px[y, xr] = (150, 96, 44)
    # 膝の上の開いた本 (灯を受けて白い)
    c.poly([(ox + 18, 104), (ox + 28, 99), (ox + 38, 104), (ox + 28, 108)], (226, 204, 150))
    # 歌の音 (浮かぶ小さな点)
    rng = np.random.default_rng(3)
    for i in range(7):
        x, y = ox + 20 + i * 6 + rng.uniform(-2, 2), 70 - i * 6 + math.sin(i) * 4
        c.disc(x, y, 1.0, (250, 220, 150), alpha=0.8 - i * 0.08)
    return c.quantize(WARM)


DAY = ["#1c2a3c", "#2e4a6a", "#4a72a0", "#7aa2cc", "#b4d0e8", "#eef4f8", "#2e3a28", "#46583a", "#6a7c50", "#94a46a", "#3a3a40", "#6a6a74"]


def w2():
    # 北の丘陵の昼の空を、岩の鯨が泳ぐ
    c = K.Canvas()
    c.vgrad([(0, (60, 100, 160)), (0.7, (150, 190, 226)), (1, (220, 234, 244))])
    c.vgrad([(0, (110, 154, 200)), (1, (200, 220, 236))], 36, 100)
    puffs(c, 92, 40, 7, (226, 234, 244), (252, 252, 255), 160, 5, 11)
    img, _ = K.render_mesh("stone_whale_far", (120, 40), yaw=96, pitch=6, light=(-0.3, 0.9, -0.4), ambient=0.5, tint=(190, 196, 214))
    c.sprite(img, 70, 40)
    c.ridge(100, 10, 0.02, 3, (110, 128, 96), rough=0.5)
    c.ridge(112, 9, 0.03, 4, (78, 96, 64), rough=0.5)
    c.ridge(124, 6, 0.05, 5, (52, 64, 44), rough=0.5)
    return c.quantize(DAY)


SUNSET = ["#100a14", "#24142a", "#40203a", "#6a2e40", "#a04a44", "#d8784a", "#f4b066", "#ffe0a0", "#0e0c12"]


def w3():
    # 鯨見の町の見張り台 (丘の上) に立つ鯨追い。遠眼鏡を空へ。遠くに小さな鯨
    c = K.Canvas()
    c.vgrad([(0, (36, 20, 44)), (0.5, (150, 70, 70)), (0.8, (240, 160, 96)), (1, (255, 214, 150))])
    c.glow(190, 110, 70, (100, 50, 10), 0.8)
    img, _ = K.render_mesh("stone_whale_far", (42, 14), yaw=96, pitch=6, silhouette=(60, 30, 50))
    c.sprite(img, 156, 30)
    c.ridge(116, 14, 0.02, 9, (24, 14, 22), rough=0.5)
    # 見張り台 (木の櫓)
    tx, ty = 60, 60
    for x0 in (tx, tx + 22):
        P.line(c, x0, ty + 8, x0 - 4 + (x0 - tx) // 3, 118, S)
        P.line(c, x0 + 1, ty + 8, x0 - 3 + (x0 - tx) // 3, 118, S)
    P.line(c, tx, ty + 30, tx + 22, ty + 46, S)
    P.line(c, tx + 22, ty + 30, tx, ty + 46, S)
    c.poly([(tx - 4, ty + 6), (tx + 27, ty + 6), (tx + 27, ty + 9), (tx - 4, ty + 9)], S)
    c.poly([(tx - 6, ty - 14), (tx + 11, ty - 22), (tx + 29, ty - 14), (tx + 29, ty - 12), (tx - 6, ty - 12)], S)
    P.line(c, tx - 3, ty - 12, tx - 3, ty + 6, S)
    P.line(c, tx + 26, ty - 12, tx + 26, ty + 6, S)
    # 鯨追い (立って遠眼鏡を構える)
    P.stamp(c, P.STAND, tx + 6, ty - 13, S)
    P.line(c, tx + 13, ty - 11, tx + 22, ty - 16, S)
    P.line(c, tx + 13, ty - 10, tx + 22, ty - 15, S)
    return c.quantize(SUNSET)


SNOWY = ["#0e1018", "#1e2230", "#323a4c", "#55607a", "#8a96ae", "#c4ccdc", "#eef2f8", "#4a3a2a", "#7a6040", "#b89a64"]


def w4():
    # 雪の残る峠道。石標に立てかけた古い荷と、風にめくれる頁。同じ支度で歩いた誰かの跡
    c = K.Canvas()
    c.vgrad([(0, (60, 70, 96)), (1, (170, 180, 200))], 0, 70)
    c.ridge(60, 16, 0.02, 21, (100, 110, 132), rough=0.55)
    c.ridge(84, 10, 0.03, 22, (150, 160, 182), rough=0.5)
    c.vgrad([(0, (210, 216, 228)), (1, (170, 178, 196))], 90, 135)
    c.poly([(0, 135), (0, 96), (80, 92), (160, 96), (240, 92), (240, 135)], (214, 220, 232))
    # 道 (奥へ細く) と足あと
    c.poly([(104, 92), (122, 92), (176, 135), (60, 135)], (186, 192, 206))
    for i in range(9):
        t = i / 9
        x = 112 + (118 - 112) * t + (i % 2) * 6 - 3
        y = 94 + t * 38
        c.disc(x + (t * 10 if i % 2 else -t * 10), y, 1 + t * 1.4, (140, 148, 168))
    # 石標と荷
    c.poly([(148, 120), (150, 86), (156, 82), (162, 86), (164, 120)], (90, 92, 104))
    P.line(c, 152, 92, 160, 92, (60, 62, 74))
    P.line(c, 152, 98, 158, 98, (60, 62, 74))
    c.poly([(134, 122), (136, 102), (148, 100), (152, 104), (150, 122)], (96, 72, 46))
    c.poly([(136, 104), (148, 102), (149, 106), (137, 108)], (130, 100, 64))
    P.line(c, 140, 100, 138, 92, (70, 52, 34))
    P.line(c, 146, 100, 148, 92, (70, 52, 34))
    # 風にめくれる頁 (三枚が舞う)
    for k, (x, y, a) in enumerate(((126, 104, 20), (112, 90, -15), (94, 74, 30))):
        ca, sa = math.cos(math.radians(a)), math.sin(math.radians(a))
        pts = [(x + dx * ca - dy * sa, y + dx * sa + dy * ca) for dx, dy in ((-4, -3), (4, -3), (4, 3), (-4, 3))]
        c.poly(pts, (236, 228, 204))
        P.line(c, x - 2, y, x + 2, y, (120, 100, 80))
    return c.quantize(SNOWY)


HEART = ["#060406", "#140c0a", "#2a1a12", "#4a2e1a", "#7a5030", "#b08050", "#e0b070", "#fff0b0", "#c86a20", "#ffb040"]


def w5():
    # 鯨の背の奥の間。骨の柱が並ぶ闇の奥で、琥珀の鼓動が鳴る
    c = K.Canvas()
    c.vgrad([(0, (8, 6, 6)), (1, (20, 14, 10))])
    c.glow(120, 70, 110, (110, 60, 16), 1.0)
    for k, x in enumerate((20, 50, 190, 220)):
        h = 0.85 if k in (1, 2) else 1.0
        w0 = 7 if k in (1, 2) else 10
        c.poly([(x - w0, 135), (x - w0 + 3, 20 * h), (x, 0), (x + w0 - 3, 20 * h), (x + w0, 135)], (40 + k * 4, 30, 22))
    # 肋の弧
    for k in range(5):
        y = 14 + k * 6
        for t in np.linspace(0, 1, 160):
            x = 20 + 200 * t
            yy = y + math.sin(t * math.pi) * -10 + k * 2
            if 0 <= int(yy) < c.h:
                c.px[int(yy), int(x)] = (70, 50, 34)
    # 心臓 (台の上で脈打つ琥珀の核)
    c.poly([(96, 118), (102, 96), (138, 96), (144, 118)], (50, 36, 26))
    c.glow(120, 76, 50, (200, 110, 30), 1.0)
    c.disc(120, 76, 12, (210, 120, 40))
    c.disc(117, 73, 6, (255, 200, 110))
    for r in (18, 24, 31):
        d = np.hypot(c.xx - 120, c.yy - 76)
        c.blend(((d > r - 0.6) & (d < r + 0.6)) * (0.9 - r * 0.02), (255, 180, 80))
    return c.quantize(HEART)


# ---------------------------------------------------------------- 二章 潮殻「引く海」
NIGHTSEA = ["#05060c", "#0c1020", "#161e38", "#24305a", "#3a4c82", "#6a80b4", "#a8bcdc", "#e4ecf6", "#f0d890", "#2a2620"]


def t1():
    # 心臓が止まり、岩鯨は雲の海へ沈むように眠る。背の灯は消えている
    c = K.Canvas()
    c.vgrad([(0, (6, 8, 18)), (0.7, (24, 32, 64)), (1, (40, 54, 96))])
    c.stars(140, 41, ymax=90)
    moon(c, 196, 28, 12)
    img, _ = K.render_mesh("stone_whale_far", (170, 60), yaw=96, pitch=-4, roll=-6, light=(0.4, 0.8, -0.3), ambient=0.3, tint=(80, 90, 130))
    c.sprite(img, 34, 62)
    puffs(c, 100, 40, 51, (30, 40, 76), (90, 108, 156), 196, 6, 13)
    puffs(c, 116, 40, 52, (42, 56, 100), (130, 148, 196), 196, 6, 12)
    # 眠りの息 (小さな泡のような光が上へ)
    rng = np.random.default_rng(8)
    for i in range(10):
        x, y = rng.uniform(60, 180), rng.uniform(40, 90)
        c.disc(x, y, 0.9, (170, 190, 230), alpha=0.6)
    return c.quantize(NIGHTSEA)


SHARD = ["#06080e", "#121a26", "#22344a", "#3a5a78", "#6a9cc0", "#b0e0ff", "#f0fbff", "#3a2a24", "#6a4a38", "#9a7258"]


def key_frame(c, cx, cy, lit, r=34):
    """天の鍵の枠: 丸い石の輪に、十字に四つの菱形の窪み。lit = 光っている窪みの数 (上・右・下・左の順)"""
    c.disc(cx, cy, r, (40, 52, 70))
    c.disc(cx, cy, r - 5, (24, 32, 46))
    d = np.hypot(c.xx - cx, c.yy - cy)
    c.blend(((d > r - 1.2) & (d < r)) * 0.8, (110, 140, 170))
    c.disc(cx, cy, 5, (40, 52, 70))
    for k, (dx, dy) in enumerate(((0, -1), (1, 0), (0, 1), (-1, 0))):
        x, y = cx + dx * (r - 15), cy + dy * (r - 15)
        if k < lit:
            P.fragment(c, x, y, 2.0)
        else:
            c.poly([(x, y - 8), (x + 4.4, y), (x, y + 8), (x - 4.4, y)], (10, 14, 22))
            P.fragment(c, x, y, 2.0, color=(70, 96, 124), lit=False)


def t2():
    # 天の鍵の枠。四つの窪みのうち、一つだけに欠片が光る
    c = K.Canvas()
    c.vgrad([(0, (6, 8, 14)), (1, (18, 26, 40))])
    c.stars(40, 3, ymax=135, color=(120, 150, 190))
    key_frame(c, 120, 67, 1)
    return c.quantize(SHARD)


HARBOR = ["#1a2030", "#2c3a52", "#46607c", "#6c8ca6", "#a2bccc", "#dae6ea", "#f6f0dc", "#f4c870", "#2e3a2e", "#56684a", "#8a7a5a", "#c4b08a"]


def t3():
    # 朝の東の海。島のような甲羅の亀が沖を行く。左に汐守の港の灯台
    c = K.Canvas()
    c.vgrad([(0, (110, 140, 170)), (0.6, (210, 220, 220)), (1, (246, 236, 214))], 0, 74)
    c.vgrad([(0, (120, 150, 170)), (1, (60, 90, 120))], 74, 135)
    for y in range(76, 135, 3):
        for x in range((y * 7) % 11, c.w, 13):
            c.px[y, x:x + 4] = (170, 196, 210)
    img, _ = K.render_mesh("tide_shell_far", (130, 64), yaw=110, pitch=8, light=(0.3, 0.8, -0.5), ambient=0.45, tint=(176, 186, 196))
    c.sprite(img, 92, 34)
    P.ripples(c, 158, 94, 3, (200, 220, 230), spread=26)
    # 灯台と岬
    c.poly([(0, 135), (0, 82), (20, 78), (44, 86), (56, 135)], (40, 48, 44))
    c.poly([(20, 80), (22, 44), (30, 44), (32, 80)], (226, 222, 210))
    c.poly([(19, 44), (26, 36), (33, 44)], (120, 60, 50))
    c.glow(26, 46, 20, (120, 90, 30), 0.8)
    c.disc(26, 47, 2, (255, 230, 140))
    return c.quantize(HARBOR)


FALL = ["#080406", "#1a0a10", "#341420", "#5a2030", "#8a3438", "#c45a44", "#f0a070", "#ffe0b0", "#120c10"]


def t4():
    # 遠い昔、空が初めて落ちかけた夜。近すぎる月の下、人々は縄を伝って巨神の背へ逃れた
    c = K.Canvas()
    c.vgrad([(0, (24, 8, 14)), (0.6, (90, 30, 40)), (1, (150, 70, 60))])
    moon(c, 150, 30, 46, base=(250, 210, 170), shade=(200, 140, 120))
    img, _ = K.render_mesh("stone_whale_far", (200, 70), yaw=96, pitch=4, silhouette=(18, 10, 14))
    c.sprite(img, 20, 62)
    # 縄 (月の光を受けて明るい) と、縄を登る人々の影
    for k, x in enumerate((64, 100, 136, 168)):
        top = 74 + (k % 2) * 4
        P.line(c, x, top, x - 10, 135, (150, 70, 60))
        for j in range(2):
            y = 100 + j * 14 + (k % 2) * 4
            xx = x - 10 * (y - top) / (135 - top)
            c.disc(xx - 1, y - 9, 2.2, (14, 8, 10))
            c.poly([(xx - 4, y), (xx - 3, y - 7), (xx + 2, y - 7), (xx + 2, y)], (14, 8, 10))
            P.line(c, xx + 1, y - 7, xx + 3, y - 11, (14, 8, 10))
    c.poly([(0, 135), (0, 124), (60, 120), (120, 126), (240, 122), (240, 135)], (14, 8, 10))
    return c.quantize(FALL)


SALT = ["#0a0c10", "#1a1e26", "#2e3440", "#4a5260", "#727c8a", "#a8b0bc", "#dce2e8", "#f6f8fa", "#c8b890"]


def t5():
    # 沈んだ塩の蔵の奥の碑。上から差す細い光が、甲羅の上の人々の浮き彫りを照らす
    c = K.Canvas()
    c.vgrad([(0, (14, 16, 22)), (1, (30, 34, 44))])
    c.blend(((np.abs(c.xx - (120 + (c.yy - 0) * 0.15)) < 18 - c.yy * 0.05)) * 0.35, (220, 228, 236))
    P.tablet(c, 70, 24, 100, 100, (110, 116, 128), None)
    # 浮き彫り: 甲羅の弧と、その上の小さな家と人
    for t in np.linspace(0, 1, 120):
        x = 84 + 72 * t
        y = 92 - math.sin(t * math.pi) * 24
        c.px[int(y), int(x)] = (60, 64, 74)
        c.px[int(y) + 1, int(x)] = (170, 176, 186)
    for k, x in enumerate((100, 112, 126, 140)):
        y = 92 - math.sin((x - 84) / 72 * math.pi) * 24
        c.poly([(x - 3, y - 1), (x - 3, y - 6), (x, y - 9), (x + 3, y - 6), (x + 3, y - 1)], (64, 68, 78))
    for x in (96, 108, 120, 132, 146):
        c.disc(x, 104, 1.4, (64, 68, 78))
        P.line(c, x, 106, x, 110, (64, 68, 78))
    # 彫りの字の列 (読めない)
    for li in range(2):
        for x in range(82, 160, 5):
            if (x + li * 3) % 4:
                c.px[40 + li * 6, x:x + 3] = (70, 74, 86)
    # 塩の結晶
    rng = np.random.default_rng(4)
    for _ in range(40):
        x, y = rng.integers(0, c.w), rng.integers(116, c.h)
        c.poly([(x, y), (x + 2, y - 4), (x + 4, y)], (220, 226, 232))
    return c.quantize(SALT)


# ---------------------------------------------------------------- 三章 雷翼「風に逆らう嵐」
DUSKSEA = ["#0c0c18", "#1c1c34", "#30305a", "#4c4c80", "#7a6a9a", "#b48aa0", "#e6b0a0", "#fae0c4", "#18324a", "#2c5070", "#5a84a4"]


def s1():
    # 潮殻もまた、夕暮れの海へ沈むように眠る。大きな波紋と泡
    c = K.Canvas()
    c.vgrad([(0, (28, 28, 60)), (0.6, (170, 120, 150)), (1, (250, 210, 180))], 0, 70)
    c.vgrad([(0, (60, 90, 120)), (1, (16, 30, 50))], 70, 135)
    img, _ = K.render_mesh("tide_shell_far", (110, 54), yaw=110, pitch=8, light=(0.3, 0.8, -0.5), ambient=0.35, tint=(110, 110, 150))
    # 水面より上だけを描く (沈んでいる)
    cut = img.copy()
    cut[34:, :, 3] = 0
    c.sprite(cut, 64, 44)
    P.ripples(c, 120, 80, 4, (200, 190, 210), spread=30)
    rng = np.random.default_rng(6)
    for _ in range(18):
        x, y = rng.uniform(80, 160), rng.uniform(82, 120)
        c.disc(x, y, rng.uniform(0.6, 1.4), (190, 200, 220), alpha=0.7)
    return c.quantize(DUSKSEA)


STORM = ["#06060c", "#10121e", "#1c2032", "#2c3248", "#444c66", "#6a7490", "#a4aec6", "#e8eeff", "#8a9cff"]


def s2():
    # 西の峰。稲妻に照らされた雷雲を背に、雷をまとう鳥の影が舞う
    c = K.Canvas()
    c.vgrad([(0, (30, 34, 56)), (0.5, (90, 100, 136)), (1, (40, 46, 66))])
    c.glow(150, 50, 90, (60, 66, 110), 0.9)
    puffs(c, 20, 30, 61, (52, 58, 84), (130, 140, 180), 150, 8, 16)
    c.vgrad([(0, (110, 120, 156)), (0.6, (74, 82, 112)), (1, (36, 40, 58))], 28, 135)
    img, _ = K.render_mesh("thunder_wing_far", (170, 78), yaw=20, pitch=-40, silhouette=(12, 14, 24))
    c.sprite(img, 36, 30)
    P.bolt(c, 30, 0, 52, 110, 3, width=2)
    P.bolt(c, 206, 0, 188, 120, 9)
    c.ridge(118, 22, 0.03, 63, (16, 18, 28), rough=0.6)
    return c.quantize(STORM)


LAMP = ["#0e0a06", "#22180e", "#4a3420", "#7a5a38", "#b08c5c", "#d8bc8c", "#f2e2be", "#fff6e0", "#3a1a12"]


def s3():
    # 風の民の書。灯りの下の頁に、四本ずつ束ねた刻みが何度も並ぶ (何度も来た誰か)
    c = K.Canvas()
    c.vgrad([(0, (14, 10, 6)), (1, (34, 24, 14))])
    c.glow(200, 20, 120, (110, 80, 30), 1.0)
    c.poly([(40, 14), (206, 10), (210, 126), (34, 130)], (226, 206, 160))
    c.blend(((c.xx < 60) & (c.xx > 30)) * 0.25, (90, 60, 30))
    ink = (60, 34, 24)
    for row in range(5):
        y = 24 + row * 20
        for b in range(5 if row < 4 else 3):
            x = 54 + b * 30
            for k in range(4):
                P.line(c, x + k * 4, y, x + k * 4 + 1, y + 12, ink)
            P.line(c, x - 2, y + 10, x + 15, y + 2, ink)
    # 端の小さな人の印
    P.stamp(c, P.STAND, 190, 98, ink)
    return c.quantize(LAMP)


HILL = ["#04050a", "#0c0e1a", "#161a2c", "#232a44", "#36405e", "#56638a", "#8a98be", "#d6deee", "#1a2416", "#2c3a22"]


def s4():
    # 夜の丘。遠い丘の上に、自分に似た人影が立ってこちらを見ている。手前に旅人の背
    c = K.Canvas()
    c.vgrad([(0, (6, 8, 18)), (0.55, (30, 38, 64)), (0.72, (96, 108, 140)), (1, (60, 70, 100))])
    c.stars(150, 71, ymax=70)
    moon(c, 60, 30, 13)
    ys = c.ridge(84, 10, 0.02, 72, (14, 16, 28), rough=0.5)
    img, _ = K.render_mesh("watcher", (14, 26), yaw=200, pitch=4, silhouette=(10, 12, 20))
    hx = 168
    c.sprite(img, hx - 7, ys[hx] - 24)
    c.ridge(112, 8, 0.03, 73, (18, 24, 30), rough=0.5)
    c.ridge(116, 4, 0.05, 74, (40, 46, 66), rough=0.5)
    P.stamp(c, P.STAND, 40, 98, (8, 10, 14))
    rng = np.random.default_rng(2)
    for _ in range(60):
        x, y = rng.integers(0, c.w), rng.integers(118, c.h)
        for k in range(rng.integers(3, 7)):
            if y - k >= 0:
                c.px[y - k, x] = (10, 12, 14)
    return c.quantize(HILL)


MIRROR = ["#060608", "#121218", "#22222c", "#36364a", "#525470", "#7a7e9e", "#aab0cc", "#dce0f0", "#3a2e22", "#6a5434"]


def s5():
    # 竜骨の間の入口の鏡。鏡の中の影は、少し遅れて振り向く
    c = K.Canvas()
    c.vgrad([(0, (8, 8, 12)), (1, (20, 20, 28))])
    c.poly([(74, 128), (74, 40), (86, 18), (120, 8), (154, 18), (166, 40), (166, 128)], (80, 64, 44))
    c.poly([(82, 124), (82, 42), (92, 24), (120, 15), (148, 24), (158, 42), (158, 124)], (60, 64, 84))
    c.glow(120, 70, 50, (40, 44, 64), 0.8)
    # 鏡の中の自分 (こちら向き) と、遅れて動くもう一つの影 (薄く、少しずれて)
    P.stamp(c, P.STAND, 116, 88, (130, 136, 170))
    P.stamp(c, P.STAND, 112, 88, (14, 14, 20))
    c.poly([(0, 128), (240, 128), (240, 135), (0, 135)], (14, 14, 18))
    # 手前の旅人の背 (大きく、下の端)
    c.disc(60, 108, 10, (6, 6, 8))
    c.poly([(40, 135), (44, 116), (60, 112), (76, 116), (80, 135)], (6, 6, 8))
    return c.quantize(MIRROR)


# ---------------------------------------------------------------- 四章 余燼「足もとから来るもの」
PEAK = ["#06080e", "#121624", "#20283c", "#34405a", "#56648a", "#8a98b8", "#c4cede", "#f2f6fa", "#2a2a3a"]


def e1():
    # 晴れた夜の雪の峰。羽をたたんだ大鳥が、雪をかぶった岩の塊のように眠る (羽の先が少しだけ覗く)
    c = K.Canvas()
    c.vgrad([(0, (8, 10, 22)), (1, (60, 70, 100))])
    c.stars(160, 81, ymax=80)
    c.poly([(0, 135), (0, 96), (60, 70), (110, 44), (128, 40), (150, 52), (200, 84), (240, 96), (240, 135)], (52, 60, 84))
    c.poly([(96, 54), (110, 44), (128, 40), (150, 52), (162, 62), (140, 60), (126, 52), (112, 60)], (226, 232, 242))
    moon(c, 128, 22, 24)
    # 眠る鳥 (頂にとまり、羽をたたんで背を向ける影。肩に雪)
    B = (14, 16, 28)
    bx, by = 128, 4
    c.poly([(bx - 16, by + 30), (bx - 18, by + 16), (bx - 10, by + 6), (bx + 10, by + 6), (bx + 18, by + 16), (bx + 16, by + 30), (bx, by + 36)], B)
    c.disc(bx, by + 4, 6, B)
    c.poly([(bx - 2, by - 2), (bx + 1, by - 9), (bx + 3, by - 1)], B)
    c.poly([(bx + 2, by - 1), (bx + 7, by - 7), (bx + 6, by)], B)
    for k in range(5):
        y = by + 14 + k * 4
        for sx in (-1, 1):
            c.poly([(bx + sx * (17 - k), y), (bx + sx * (21 - k), y + 7), (bx + sx * (13 - k), y + 5)], B)
    c.poly([(bx - 10, by + 32), (bx - 6, by + 44), (bx - 2, by + 32)], B)
    c.poly([(bx + 2, by + 32), (bx + 6, by + 44), (bx + 10, by + 32)], B)
    for sx in (-1, 1):
        for t in np.linspace(0, 1, 30):
            x = bx + sx * (4 + 12 * t)
            y = by + 8 + 7 * t * t
            c.px[int(y), int(x)] = (230, 236, 246)
            c.px[int(y) - 1, int(x)] = (200, 210, 226)
    # 降る雪
    rng = np.random.default_rng(11)
    for _ in range(120):
        x, y = rng.integers(0, c.w), rng.integers(0, c.h)
        c.px[y, x] = (230, 236, 246)
    return c.quantize(PEAK)


def e2():
    # 三つの欠片が光る天の鍵の枠。真ん中から、光の筋が足もとを指す
    c = K.Canvas()
    c.vgrad([(0, (6, 8, 14)), (1, (18, 22, 32))])
    c.stars(40, 13, ymax=135, color=(120, 150, 190))
    c.blend(((np.abs(c.xx - 120) < 2 + (c.yy - 67) * 0.05) & (c.yy > 67)) * 0.8, (170, 220, 255))
    c.glow(120, 132, 40, (40, 70, 110), 0.8)
    key_frame(c, 120, 60, 3, r=32)
    return c.quantize(SHARD)


EMBERS = ["#080404", "#180a08", "#2e120c", "#4e1c10", "#7a2a14", "#b4401a", "#ec7a2a", "#ffc060", "#fff0b0", "#3a2a2a"]


def e3():
    # 南の荒野の夜。溶けた岩の割れ目から、火の巨神の背が浮かび上がる。火の粉が昇る
    c = K.Canvas()
    c.vgrad([(0, (12, 6, 8)), (0.7, (60, 20, 16)), (1, (120, 40, 20))])
    c.ridge(70, 14, 0.03, 91, (40, 16, 14), rough=0.55)
    c.ridge(92, 10, 0.04, 92, (26, 10, 10), rough=0.55)
    # 溶岩の割れ目
    c.poly([(30, 135), (60, 112), (120, 104), (190, 110), (230, 135)], (180, 60, 20))
    c.glow(120, 112, 90, (140, 50, 10), 1.0)
    img, _ = K.render_mesh("ember_titan_far", (150, 70), yaw=60, pitch=12, light=(0.3, -0.6, -0.5), ambient=0.35, tint=(170, 110, 90))
    cut = img.copy()
    cut[52:, :, 3] = 0
    c.sprite(cut, 46, 46)
    c.poly([(0, 135), (0, 118), (40, 112), (70, 124), (170, 126), (210, 114), (240, 118), (240, 135)], (20, 8, 8))
    rng = np.random.default_rng(12)
    for _ in range(60):
        x, y = rng.uniform(40, 200), rng.uniform(20, 110)
        c.disc(x, y, rng.uniform(0.5, 1.2), (255, 170, 80), alpha=rng.uniform(0.4, 1.0))
    return c.quantize(EMBERS)


FORGE = ["#0a0606", "#1c1210", "#32221c", "#4e3a2e", "#706050", "#9a8a78", "#c8bca8", "#f0e6d0", "#ff9a40", "#5a2a18"]


def e4():
    # 灰炉の村の炉の跡。崩れた煙突と冷えた金床。金床の上に一枚の頁。灰が降る
    c = K.Canvas()
    c.vgrad([(0, (40, 30, 30)), (1, (90, 70, 60))])
    c.poly([(30, 135), (34, 30), (44, 22), (58, 26), (62, 60), (70, 135)], (34, 24, 20))
    c.poly([(150, 135), (160, 70), (210, 64), (230, 80), (236, 135)], (44, 32, 26))
    c.poly([(0, 116), (240, 112), (240, 135), (0, 135)], (30, 22, 18))
    # 金床
    c.poly([(98, 112), (102, 98), (90, 92), (150, 92), (138, 98), (142, 112)], (24, 20, 20))
    c.poly([(84, 88), (156, 88), (150, 94), (90, 94)], (36, 30, 30))
    # 頁 (かすかに光る)
    c.glow(120, 86, 24, (90, 60, 20), 0.8)
    c.poly([(108, 88), (114, 80), (132, 80), (128, 88)], (236, 224, 196))
    for y in (82, 84, 86):
        P.line(c, 114, y, 127, y, (120, 96, 70))
    rng = np.random.default_rng(14)
    for _ in range(140):
        x, y = rng.integers(0, c.w), rng.integers(0, c.h)
        c.px[y, x] = (190, 180, 170) if rng.random() < 0.7 else (120, 110, 100)
    return c.quantize(FORGE)


def e5():
    # 大地の断面。四つの小さな光が、地の底の心臓へ降りていく (借りた鼓動を下へ返す)
    c = K.Canvas()
    c.vgrad([(0, (28, 34, 56)), (1, (90, 106, 138))], 0, 16)
    for y0, col in [(16, (74, 90, 48)), (20, (86, 66, 46)), (40, (66, 50, 38)), (66, (50, 38, 32)), (92, (36, 26, 24)), (112, (24, 16, 16))]:
        c.ridge(y0 + 3, 3, 0.06, y0, col, rough=0.6)
    hx, hy = 120, 116
    c.disc(hx, hy, 22, (10, 6, 8), soft=3)
    c.glow(hx, hy, 40, (90, 20, 10), 0.8)
    c.disc(hx, hy, 6, (140, 36, 26))
    for k, x0 in enumerate((40, 90, 150, 200)):
        for t in np.linspace(0, 1, 120):
            x = x0 + (hx - x0) * t
            y = 22 + (hy - 22) * t
            a = 0.5 * t
            xi, yi = int(x), int(y)
            c.px[yi, xi] = c.px[yi, xi] * (1 - a) + np.array((255, 190, 110)) * a
        tx, ty = x0 + (hx - x0) * 0.62, 22 + (hy - 22) * 0.62
        c.glow(tx, ty, 8, (140, 90, 30), 1.0)
        c.disc(tx, ty, 1.6, (255, 220, 150))
    return c.quantize(DEPTH)


CHAPTERS = {
    "titan_whale": [("p1", w1, "still", None, "都の語り部は、巨神の古い歌を覚えていた。", "The capital's storyteller remembered the titans' old songs."),
                    ("p2", w2, "left", None, "北の丘の空を、岩の鯨が泳いでいるという。", "They say a whale of stone swims the sky above the northern hills."),
                    ("p3", w3, "still", None, "鯨見の町には、鯨を追いつづける者がいる。", "In Kujirami, someone still chases the whale."),
                    ("p4", w4, "up", None, "峠の道には、誰かが同じ支度で歩いた跡があった。", "On the pass, someone had walked before you, packed just the same."),
                    ("p5", w5, "still", "minecraft:entity.warden.heartbeat", "鯨の背の奥で、借りものの鼓動が鳴っている。", "Deep in the whale's back, a borrowed heartbeat sounds.")],
    "titan_tide": [("p1", t1, "down", None, "心臓が止まると、岩鯨は深い眠りに落ちた。死んではいない。", "When its heart stopped, the stone whale fell into a deep sleep. It is not dead."),
                   ("p2", t2, "still", "minecraft:block.amethyst_block.chime", "その奥から、天の鍵の欠片がひとつ。欠片は、あと三つ。", "From deep within, one fragment of the Sky Key. Three more remain."),
                   ("p3", t3, "right", None, "東の海を、島のような甲羅の亀が行く。", "Across the eastern sea goes a turtle whose shell is an island."),
                   ("p4", t4, "still", None, "遠い昔、空が落ちかけた夜、人は巨神の背へ逃れたという。", "Long ago, on the night the sky nearly fell, people fled onto the titans' backs."),
                   ("p5", t5, "up", None, "民はなぜ、背の上で生きることを選んだのか。", "Why did they choose to live upon those backs?")],
    "titan_storm": [("p1", s1, "down", None, "潮殻もまた、海の底へ眠りについた。", "The tide shell, too, sank into sleep beneath the sea."),
                    ("p2", s2, "left", "minecraft:entity.lightning_bolt.thunder", "西の峰では、雷をまとう鳥が舞う。", "Over the western peaks wheels a bird clad in thunder."),
                    ("p3", s3, "still", None, "風の民の書には、同じ者が何度も来たと記されていた。", "The wind folk's book recorded the same visitor, again and again."),
                    ("p4", s4, "still", None, "夜の丘から、誰かがこちらを見ている。", "From a hill in the night, someone is watching."),
                    ("p5", s5, "still", None, "鏡の中の影は、少し遅れて振り向いた。", "The shadow in the mirror turned a moment too late.")],
    "titan_ember": [("p1", e1, "up", None, "雷翼も、羽をたたんで眠った。", "The thunder wing, too, folded its wings and slept."),
                    ("p2", e2, "down", "minecraft:block.amethyst_block.chime", "三つの欠片は、かすかに下を指している。", "The three fragments point faintly downward."),
                    ("p3", e3, "right", None, "南の荒野では、地の中を火の巨神が泳ぐ。", "In the southern wastes, a titan of fire swims beneath the ground."),
                    ("p4", e4, "still", None, "炉の跡の頁「四つ返したら、下へ。今度こそ。」", "A page at the old forge: \"Return all four, then go down. This time, for sure.\""),
                    ("p5", e5, "down", "minecraft:entity.warden.heartbeat", "心臓を止めるのは、殺すことではないのかもしれない。", "Perhaps stilling a heart is not the same as killing.")],
}


def main():
    want = sys.argv[1:] or list(CHAPTERS)
    OUT.mkdir(parents=True, exist_ok=True)
    for ch in want:
        for pid, fn, *_ in CHAPTERS[ch]:
            img = fn()
            img.save(OUT / f"chapter_{ch}_{pid}.png")
            img.resize((img.width * 4, img.height * 4), 0).save(OUT / f"chapter_{ch}_{pid}_x4.png")
            print("wrote", ch, pid)


if __name__ == "__main__":
    main()
