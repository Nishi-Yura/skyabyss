"""開幕の語りの残りの絵 (1・3・4・6・7・9)。opening.py の SCENES に足す"""
import math

import numpy as np

import pxkit as K
from opening import NIGHT, moon, puffs


def p1():
    # 星空と大きな月。何も動かない静かな一枚
    c = K.Canvas()
    c.vgrad([(0, (5, 6, 14)), (0.7, (16, 20, 44)), (1, (30, 40, 76))])
    c.stars(220, 21, ymax=110, big=0.15)
    moon(c, 120, 52, 26)
    puffs(c, 118, 30, 41, (22, 28, 58), (70, 86, 130), 120, 6, 12)
    puffs(c, 128, 34, 42, (32, 42, 80), (110, 128, 176), 120, 5, 10)
    return c.quantize(NIGHT)


DEPTH = ["#08060a", "#1a1214", "#2c1e1c", "#3e2c24", "#56402e", "#6e5a3e", "#4a5a30", "#7a8a44", "#601818", "#9a2a1e", "#e0502a", "#ffb070", "#1c2238", "#5a6a8a"]


def p3():
    # 大地の断面。上に小さな地表、下へ地層、底の洞で赤い心臓が脈打ち、光の筋が割れ目を昇る
    c = K.Canvas()
    c.vgrad([(0, (28, 34, 56)), (1, (90, 106, 138))], 0, 16)
    layers = [(16, (74, 90, 48)), (20, (86, 66, 46)), (40, (66, 50, 38)), (66, (50, 38, 32)), (92, (36, 26, 24)), (112, (24, 16, 16))]
    for y0, col in layers:
        c.ridge(y0 + 3, 3, 0.06, y0, col, rough=0.6)
    # 地表の小さな木と草
    for x in (40, 46, 170):
        c.poly([(x, 17), (x + 2, 9), (x + 4, 17)], (40, 60, 34))
    # 洞と心臓
    hx, hy = 120, 112
    c.disc(hx, hy, 26, (10, 6, 8), soft=3)
    c.glow(hx, hy, 60, (110, 20, 10), 1.0)
    c.disc(hx, hy, 8, (160, 40, 28))
    c.disc(hx - 2, hy - 2, 4, (240, 110, 60))
    for r in (13, 18):
        d = np.hypot(c.xx - hx, c.yy - hy)
        c.blend(((d > r - 0.7) & (d < r + 0.7)) * 0.6, (220, 80, 40))
    # 割れ目を昇る光の筋
    rng = np.random.default_rng(5)
    for k in range(5):
        x, y = hx + rng.uniform(-6, 6), hy - 8
        for _ in range(70):
            x += rng.uniform(-1.4, 1.4) + (k - 2) * 0.35
            y -= 1.1
            if y < 20:
                break
            xi, yi = int(x), int(y)
            if 0 <= xi < c.w:
                a = max(0.0, 1 - (hy - y) / 100)
                c.px[yi, xi] = c.px[yi, xi] * (1 - a) + np.array((230, 90, 40)) * a
    return c.quantize(DEPTH)


SPLIT = ["#06070e", "#121628", "#20284a", "#33406e", "#4c5e92", "#7c8eb8", "#c4cee0", "#601818", "#b03020", "#ff8a4a", "#ffe0a0", "#1a1a22", "#3a3a48", "#262030"]


def p4():
    # 心臓から四つの光が昇り、四体の巨神の影になる (ゲームの形を暗い影絵に)
    c = K.Canvas()
    c.vgrad([(0, (6, 8, 18)), (0.6, (20, 26, 52)), (1, (12, 10, 18))])
    c.stars(90, 31, ymax=70)
    hx, hy = 120, 124
    c.glow(hx, hy, 44, (120, 24, 12), 1.0)
    c.disc(hx, hy, 6, (176, 48, 32))
    c.disc(hx - 1, hy - 1, 3, (255, 150, 80))
    spots = [(44, 42, "stone_whale_far", 96, 8, (78, 56)), (90, 30, "tide_shell_far", 40, 18, (62, 42)),
             (150, 30, "thunder_wing_far", 20, 55, (72, 44)), (206, 44, "ember_titan_far", 60, 12, (64, 42))]
    for x, y, name, yaw, pitch, size in spots:
        # 光の筋 (心臓から弧を描いて昇る)
        for t in np.linspace(0, 1, 140):
            px_ = hx + (x - hx) * t
            py_ = hy + (y + 10 - hy) * t - math.sin(t * math.pi) * 14
            xi, yi = int(px_), int(py_)
            a = 0.85 * (1 - t * 0.5)
            if 0 <= xi < c.w and 0 <= yi < c.h:
                c.px[yi, xi] = c.px[yi, xi] * (1 - a) + np.array((255, 170, 100)) * a
        img, _ = K.render_mesh(name, size, yaw=yaw, pitch=pitch, light=(0.2, 0.9, -0.4), ambient=0.4, tint=(130, 125, 160))
        c.glow(x, y, 26, (40, 30, 30), 0.6)
        c.sprite(img, x - size[0] / 2, y - size[1] / 2)
        # 体の奥の小さな鼓動
        c.disc(x, y + 2, 1.4, (255, 140, 80))
    return c.quantize(SPLIT)


TOWN = ["#06070e", "#10142a", "#1c2444", "#2c3a66", "#46588c", "#7a8cb6", "#b0bed8", "#e8eef6", "#0c0c12", "#24242e", "#f0c060", "#ffeab0", "#8a6a2a"]


def bell_town(c, people, lit, bell_swing):
    # 鐘楼 (中央) と家並みの影、窓の灯、空を見上げる人々
    S = (12, 12, 18)
    for x0, w, h in ((0, 30, 22), (26, 26, 30), (50, 22, 18), (160, 28, 26), (186, 24, 20), (208, 34, 28)):
        c.poly([(x0, 135), (x0, 135 - h), (x0 + w / 2, 135 - h - 10), (x0 + w, 135 - h), (x0 + w, 135)], S)
        if lit:
            for wx in range(x0 + 5, x0 + w - 4, 9):
                c.px[135 - h + 6:135 - h + 9, wx:wx + 2] = (240, 192, 96)
    tx = 112
    c.poly([(tx - 12, 135), (tx - 10, 52), (tx + 10, 52), (tx + 12, 135)], S)
    c.poly([(tx - 14, 52), (tx + 14, 52), (tx + 14, 48), (tx - 14, 48)], S)
    c.poly([(tx - 13, 30), (tx - 13, 48), (tx - 8, 48), (tx - 8, 34), (tx + 8, 34), (tx + 8, 48), (tx + 13, 48), (tx + 13, 30)], S)
    c.poly([(tx - 16, 30), (tx, 8), (tx + 16, 30)], S)
    # 鐘 (振れ)
    ang = math.radians(bell_swing)
    bx, by = tx + math.sin(ang) * 3, 38
    c.poly([(bx - 5, by + 8), (bx - 4, by + 1), (bx, by - 2), (bx + 4, by + 1), (bx + 5, by + 8)], (150, 112, 40))
    if bell_swing:
        for r in (9, 14, 19):
            d = np.hypot(c.xx - bx, c.yy - by - 3)
            ring = (d > r - 0.6) & (d < r + 0.6) & (np.abs(c.yy - by - 3) < r * 0.8)
            c.blend(ring * (0.7 - r * 0.025), (250, 230, 170))
    if lit:
        c.px[60:64, tx - 2:tx + 2] = (240, 192, 96)
        c.px[80:84, tx - 2:tx + 2] = (240, 192, 96)
    for i, px_ in enumerate(people):
        # 空を見上げる人 (外套の裾が広がり、頭は少し上を向く。一人おきに空を指さす)
        base = 135
        c.poly([(px_ - 3, base), (px_ - 2, base - 8), (px_ + 2, base - 8), (px_ + 3, base)], S)
        c.disc(px_, base - 10, 2.2, S)
        c.px[base - 13, px_ + 1] = S
        if i % 2 == 0:
            for k in range(5):
                c.px[base - 8 - k, px_ + 2 + k // 2] = S


def p6():
    c = K.Canvas()
    c.vgrad([(0, (10, 12, 28)), (0.7, (40, 52, 92)), (1, (70, 86, 130))])
    c.stars(110, 61, ymax=80)
    moon(c, 190, 30, 10)
    bell_town(c, people=(66, 76, 88, 138, 150, 160), lit=True, bell_swing=18)
    return c.quantize(TOWN)


def p7():
    c = K.Canvas()
    c.vgrad([(0, (6, 8, 18)), (0.7, (26, 32, 60)), (1, (44, 54, 90))])
    c.stars(80, 61, ymax=80)
    moon(c, 186, 34, 24)
    bell_town(c, people=(), lit=False, bell_swing=0)
    return c.quantize(TOWN)


PAGE = ["#120c08", "#2a1c12", "#4a3424", "#7a5a3c", "#a88458", "#cdb488", "#e6d6b0", "#f4ead0", "#1a1820", "#3a3448", "#ffd890"]


def p9():
    # 開いた帳面の寄り。左から灯りが差し、頁に手書きの行 (読めない筆の跡)。最後の一行だけ濃く新しい
    c = K.Canvas()
    c.vgrad([(0, (18, 12, 8)), (1, (40, 28, 18))])
    c.glow(10, 30, 120, (90, 60, 20), 0.9)
    c.poly([(24, 20), (118, 14), (120, 126), (28, 128)], (226, 210, 172))
    c.poly([(122, 14), (216, 20), (212, 128), (122, 126)], (236, 222, 186))
    c.poly([(117, 14), (123, 14), (122, 127), (119, 127)], (150, 124, 88))
    c.blend(((c.xx > 100) & (c.xx < 140) & (c.yy > 14) & (c.yy < 128)) * np.clip(1 - np.abs(c.xx - 120) / 20, 0, 1) * 0.35, (120, 90, 60))
    rng = np.random.default_rng(13)
    ink = (52, 40, 34)
    for page, (x0, x1) in enumerate(((34, 110), (130, 204))):
        lines = 9 if page == 0 else 5
        for li in range(lines):
            y = 26 + li * 10 + (0 if page == 0 else 0)
            x = x0 + rng.integers(0, 4)
            end = x1 - rng.integers(0, 24)
            while x < end:
                w = rng.integers(3, 8)
                for k in range(w):
                    yy = y + int(round(math.sin((x + k) * 1.7) * 1.2))
                    c.px[yy, x + k] = ink if rng.random() < 0.85 else (110, 90, 70)
                x += w + rng.integers(1, 3)
    # 右の頁の最後の一行は濃く、太い (新しい字)
    y = 86
    x = 134
    while x < 200:
        w = int(rng.integers(4, 9))
        for k in range(w):
            for t in (0, 1):
                yy = y + int(round(math.sin((x + k) * 1.3) * 1.5)) + t
                c.px[yy, x + k] = (20, 14, 14)
        x += w + 2
    c.disc(196, 96, 2.2, (20, 14, 14))
    return c.quantize(PAGE)


SCENES = {"p1": p1, "p3": p3, "p4": p4, "p6": p6, "p7": p7, "p9": p9}
