"""置き換えの第 2 段 (矢・薬・食べ物) の 32x32 アイコンの 3 案 (2026-10-27 の声「ブロック以外はすべて mod で」)。
光は左上から。輪郭は暗い 1 画素、光の側の縁を明るく、影の側の縁を暗く、面の中に 1 段の陰。乱数は使わない。

薬 7 品は一品一案 (色違いにしない): 器の形を 9 種そろえ、品 i の案 k は器 (i + 3k) mod 9。同じ案の列で同じ器は出ない。
中身の色と印 (心臓・葉・炎・月・泡・草) は品ごと。

実行: python tools/rough/icons_phase2.py
出力: docs/review/roughs_consumables/<品>_<案>.png (32x32) と compare_<組>.png (8 倍の比べ)
"""
from __future__ import annotations

from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "docs/review/roughs_consumables"
N = 32
OUTLINE = (18, 16, 22)


# ------------------------------------------------------------------ 描く道具
class Icon:
    def __init__(self):
        self.rgb = np.zeros((N, N, 3), np.int32)
        self.a = np.zeros((N, N), bool)
        self.layers = []   # (mask, base, light, dark)

    def mask(self, draw):
        im = Image.new("1", (N, N), 0)
        draw(ImageDraw.Draw(im))
        return np.array(im, bool)

    def put(self, m, base, light=None, dark=None, shade=True):
        base = np.array(base)
        light = np.array(light) if light is not None else np.clip(base * 1.28 + 14, 0, 255)
        dark = np.array(dark) if dark is not None else base * 0.66
        col = np.broadcast_to(base, (N, N, 3)).copy()
        if shade:
            up = m & ~np.roll(m, 1, 0)
            left = m & ~np.roll(m, 1, 1)
            down = m & ~np.roll(m, -1, 0)
            right = m & ~np.roll(m, -1, 1)
            yy, xx = np.mgrid[0:N, 0:N]
            ys, xs = np.nonzero(m)
            if len(ys):
                cy, cx = ys.mean(), xs.mean()
                inner_dark = m & ((yy - cy) + (xx - cx) > (ys.max() - ys.min() + xs.max() - xs.min()) * 0.22)
                col[inner_dark] = (base * 0.84).astype(int)
            col[up | left] = light
            col[down | right] = dark
        self.rgb[m] = col[m]
        self.a |= m
        return self

    def px(self, pts, c):
        for x, y in pts:
            if 0 <= x < N and 0 <= y < N:
                self.rgb[y, x] = c
                self.a[y, x] = True
        return self

    def done(self):
        img = np.zeros((N, N, 4), np.uint8)
        img[..., :3] = np.clip(self.rgb, 0, 255)
        img[..., 3] = np.where(self.a, 255, 0)
        a = self.a
        pad = np.pad(a, 1)
        ring = (pad[:-2, 1:-1] | pad[2:, 1:-1] | pad[1:-1, :-2] | pad[1:-1, 2:]) & ~a
        img[ring] = OUTLINE + (255,)
        return Image.fromarray(img, "RGBA")


def E(box):
    return lambda d: d.ellipse(box, fill=1)


def P(pts):
    return lambda d: d.polygon(pts, fill=1)


def R(box):
    return lambda d: d.rectangle(box, fill=1)


def L(pts, w=1):
    return lambda d: d.line(pts, fill=1, width=w)


GLASS = (178, 196, 206)
CORK = (150, 108, 64)
CLAY = (164, 98, 62)
WOOD = (120, 84, 52)
PAPER = (222, 210, 182)
CORD = (184, 160, 110)
BAMBOO = (132, 156, 74)
SHELL = (222, 196, 196)
HORN = (196, 170, 120)


# ------------------------------------------------------------------ 薬の器 9 種 (中身の色 liquid、印 mark(ic) を受ける)
def round_flask(ic, liq, mark):
    ic.put(ic.mask(E((6, 11, 26, 30))), GLASS)
    ic.put(ic.mask(E((8, 15, 24, 28))), liq)
    ic.put(ic.mask(R((13, 5, 19, 12))), GLASS)
    ic.put(ic.mask(R((12, 2, 20, 6))), CORK)
    ic.px([(10, 16), (11, 15), (10, 17)], (240, 248, 252))
    mark(ic, 16, 22)


def gourd(ic, liq, mark):
    ic.put(ic.mask(E((6, 14, 26, 31))), liq, dark=np.array(liq) * 0.55)
    ic.put(ic.mask(E((10, 5, 22, 17))), liq, dark=np.array(liq) * 0.55)
    ic.put(ic.mask(R((14, 1, 18, 6))), WOOD)
    ic.put(ic.mask(L([(10, 14), (22, 14)], 2)), CORD, shade=False)
    mark(ic, 16, 23)


def bamboo(ic, liq, mark):
    ic.put(ic.mask(R((10, 4, 22, 30))), BAMBOO)
    for y in (11, 21):
        ic.put(ic.mask(R((9, y, 23, y + 1))), (98, 118, 52), shade=False)
    ic.put(ic.mask(R((11, 2, 21, 5))), CORK)
    ic.put(ic.mask(R((12, 13, 20, 19))), PAPER)
    mark(ic, 16, 16)
    ic.put(ic.mask(L([(10, 6), (5, 12), (6, 18)], 1)), CORD, shade=False)
    ic.px([(12, 24), (12, 25)], liq)


def shell_vial(ic, liq, mark):
    ic.put(ic.mask(P([(16, 3), (28, 14), (24, 28), (8, 28), (4, 14)])), SHELL)
    for x in (10, 16, 22):
        ic.put(ic.mask(L([(16, 5), (x, 26)], 1)), (196, 166, 168), shade=False)
    ic.put(ic.mask(E((10, 14, 22, 26))), liq)
    mark(ic, 16, 20)


def square_bottle(ic, liq, mark):
    ic.put(ic.mask(R((7, 10, 25, 30))), GLASS)
    ic.put(ic.mask(R((9, 14, 23, 28))), liq)
    ic.put(ic.mask(R((13, 5, 19, 10))), GLASS)
    ic.put(ic.mask(R((12, 2, 20, 5))), (56, 52, 60))
    ic.px([(9, 15), (9, 16), (9, 17)], (240, 248, 252))
    mark(ic, 16, 21)


def clay_jar(ic, liq, mark):
    ic.put(ic.mask(E((5, 9, 27, 31))), CLAY)
    ic.put(ic.mask(R((10, 6, 22, 11))), CLAY)
    ic.put(ic.mask(E((10, 4, 22, 9))), liq)
    ic.put(ic.mask(R((8, 18, 24, 19))), (110, 62, 40), shade=False)
    ic.put(ic.mask(R((11, 15, 21, 25))), PAPER)
    mark(ic, 16, 20)


def packet(ic, liq, mark):
    ic.put(ic.mask(P([(4, 10), (16, 4), (28, 10), (28, 28), (4, 28)])), PAPER)
    ic.put(ic.mask(P([(4, 10), (16, 17), (28, 10), (16, 4)])), (236, 226, 204))
    ic.put(ic.mask(L([(4, 18), (28, 18)], 2)), liq, shade=False)
    ic.put(ic.mask(L([(15, 2), (17, 30)], 1)), CORD, shade=False)
    mark(ic, 16, 24)


def horn_vial(ic, liq, mark):
    ic.put(ic.mask(P([(5, 26), (12, 30), (28, 8), (24, 4)])), HORN)
    ic.put(ic.mask(P([(9, 25), (13, 27), (24, 11), (21, 9)])), liq)
    ic.put(ic.mask(E((22, 1, 30, 9))), CORK)
    ic.put(ic.mask(R((3, 24, 9, 30))), (110, 90, 60))
    mark(ic, 15, 19)


def tube(ic, liq, mark):
    ic.put(ic.mask(R((12, 3, 20, 29))), GLASS)
    ic.put(ic.mask(R((13, 10, 19, 28))), liq)
    ic.put(ic.mask(R((11, 1, 21, 4))), CORK)
    ic.put(ic.mask(R((8, 12, 24, 14))), (96, 70, 44))
    ic.px([(13, 11), (13, 12), (13, 13), (13, 14)], (240, 248, 252))
    mark(ic, 16, 21)


def float_tube(ic, liq, mark):
    """水息の薬 (選定 B の器を、再生の薬の細い硝子管と分ける): 網で包んだ硝子管と、口に縛った硝子の浮き玉"""
    ic.put(ic.mask(R((11, 9, 21, 30))), GLASS)
    ic.put(ic.mask(R((12, 14, 20, 29))), liq)
    for y in range(11, 30, 3):
        ic.put(ic.mask(L([(11, y), (21, y + 2)], 1)), (96, 104, 104), shade=False)
    ic.put(ic.mask(E((9, 0, 23, 12))), (150, 200, 210), light=(230, 250, 255))
    ic.px([(12, 3), (13, 3), (12, 4)], (250, 255, 255))
    ic.put(ic.mask(L([(10, 9), (22, 9)], 1)), CORD, shade=False)
    mark(ic, 16, 21)


def clam(ic, liq, mark):
    """毒消し (選定 C の器を、火除けの薬の貝の小瓶と分ける): 紐で縛った二枚貝の薬入れ。合わせ目から白い膏薬"""
    ic.put(ic.mask(E((3, 8, 29, 30))), (214, 200, 176))
    for x in (8, 12, 16, 20, 24):
        ic.put(ic.mask(L([(16, 28), (x, 10)], 1)), (180, 164, 140), shade=False)
    ic.put(ic.mask(L([(3, 19), (29, 19)], 2)), liq, shade=False)
    ic.put(ic.mask(L([(16, 6), (16, 31)], 1)), CORD, shade=False)
    ic.put(ic.mask(E((11, 11, 21, 18))), (236, 228, 208))
    mark(ic, 16, 14)


VESSELS = [round_flask, gourd, bamboo, shell_vial, square_bottle, clay_jar, packet, horn_vial, tube]
VESSEL_JP = ["丸い硝子瓶", "瓢箪", "竹筒", "貝の小瓶", "角瓶", "土の壺", "薬包", "角の小瓶", "細い硝子管"]


# ------------------------------------------------------------------ 薬の印
def m_heart(ic, x, y, c=(250, 236, 236)):
    ic.px([(x - 2, y - 1), (x - 1, y - 2), (x, y - 1), (x + 1, y - 2), (x + 2, y - 1), (x - 1, y), (x, y), (x + 1, y), (x - 2, y), (x + 2, y), (x - 1, y + 1),
           (x, y + 1), (x + 1, y + 1), (x, y + 2)], c)


def m_heart2(ic, x, y):
    m_heart(ic, x - 3, y, (255, 230, 120))
    m_heart(ic, x + 3, y, (255, 230, 120))


def m_leaf(ic, x, y):
    ic.px([(x - 2, y + 2), (x - 1, y + 1), (x, y), (x + 1, y - 1), (x + 2, y - 2), (x, y - 1), (x - 1, y), (x + 1, y), (x, y + 1), (x + 1, y - 2), (x + 2, y - 1)], (200, 240, 150))


def m_flame(ic, x, y):
    ic.px([(x, y - 3), (x, y - 2), (x - 1, y - 1), (x, y - 1), (x + 1, y - 1), (x - 1, y), (x, y), (x + 1, y), (x - 2, y + 1), (x - 1, y + 1), (x, y + 1),
           (x + 1, y + 1), (x + 2, y + 1), (x - 1, y + 2), (x, y + 2), (x + 1, y + 2)], (255, 214, 120))
    ic.px([(x, y), (x, y + 1)], (255, 250, 220))


def m_moon(ic, x, y):
    ic.px([(x - 1, y - 3), (x - 2, y - 2), (x - 3, y - 1), (x - 3, y), (x - 3, y + 1), (x - 2, y + 2), (x - 1, y + 3), (x, y + 3), (x - 2, y - 1),
           (x - 2, y), (x - 2, y + 1)], (236, 236, 255))
    ic.px([(x + 2, y - 2)], (255, 255, 255))


def m_bubbles(ic, x, y):
    for bx, by in ((x - 2, y + 1), (x + 1, y - 1), (x + 2, y + 2)):
        ic.px([(bx, by - 1), (bx - 1, by), (bx + 1, by), (bx, by + 1)], (230, 250, 255))


def m_herb(ic, x, y):
    ic.px([(x, y - 3), (x, y - 2), (x, y - 1), (x, y), (x, y + 1), (x, y + 2), (x - 1, y - 2), (x - 2, y - 2), (x + 1, y - 1), (x + 2, y - 1),
           (x - 1, y + 1), (x - 2, y + 1)], (120, 190, 90))


POTIONS = [
    ("healing_draught", "癒しの薬", (196, 36, 48), m_heart),
    ("strong_healing_draught", "濃い癒しの薬", (140, 18, 40), m_heart2),
    ("regen_draught", "再生の薬", (212, 92, 160), m_leaf),
    ("fire_ward_draught", "火除けの薬", (228, 120, 36), m_flame),
    ("night_eye_draught", "夜目の薬", (40, 52, 128), m_moon),
    ("water_breath_draught", "水息の薬", (44, 128, 196), m_bubbles),
    ("antidote", "毒消し", (236, 232, 214), m_herb),
]


# 選定 (2026-10-27) で同じ器が二つ重なったので、後の品の器だけ形を変える (色違いにしない)
OVERRIDE = {("water_breath_draught", 1): (float_tube, "浮き玉を縛った網の硝子管"), ("antidote", 2): (clam, "紐で縛った二枚貝")}


def potion(i, k):
    ic = Icon()
    iid, _, liq, mark = POTIONS[i]
    if (iid, k) in OVERRIDE:
        fn, jp = OVERRIDE[(iid, k)]
        fn(ic, liq, mark)
        return ic.done(), jp
    VESSELS[(i + 3 * k) % 9](ic, liq, mark)
    return ic.done(), VESSEL_JP[(i + 3 * k) % 9]


# ------------------------------------------------------------------ 食べ物
def bread(k):
    ic = Icon()
    crust, crumb = (176, 112, 54), (226, 190, 130)
    if k == 0:   # 丸い田舎パン (十字の切れ目)
        ic.put(ic.mask(E((4, 9, 28, 29))), crust)
        ic.put(ic.mask(L([(10, 19), (22, 19)], 1)), crumb, shade=False)
        ic.put(ic.mask(L([(16, 13), (16, 25)], 1)), crumb, shade=False)
        d = "丸い田舎パン (十字の切れ目)"
    elif k == 1:   # 細長いパン (斜めの切れ目 3 本)
        ic.put(ic.mask(P([(3, 22), (8, 12), (24, 6), (29, 10), (24, 21), (8, 27)])), crust)
        for t in (0, 1, 2):
            ic.put(ic.mask(L([(10 + 5 * t, 15 - t), (12 + 5 * t, 20 - t)], 1)), crumb, shade=False)
        d = "細長いパン (斜めの切れ目)"
    else:   # 平たいパンを紐で重ねる
        for j, y in enumerate((18, 13, 8)):
            ic.put(ic.mask(E((4 + j, y, 28 - j, y + 9))), (186 - j * 6, 126 - j * 4, 64))
        ic.put(ic.mask(L([(16, 6), (16, 28)], 1)), CORD, shade=False)
        d = "平たいパンを紐で重ねる"
    return ic.done(), d


def fish(k):
    ic = Icon()
    body, belly = (150, 112, 80), (206, 172, 124)
    if k == 0:   # 縄に吊るした干し魚 2 尾
        ic.put(ic.mask(L([(4, 3), (28, 3)], 1)), CORD, shade=False)
        for x in (10, 21):
            ic.put(ic.mask(E((x - 4, 5, x + 4, 24))), body)
            ic.put(ic.mask(P([(x - 4, 30), (x + 4, 30), (x, 23)])), body)
            ic.px([(x - 1, 9), (x + 1, 9)], (30, 24, 20))
            ic.put(ic.mask(L([(x, 11), (x, 21)], 1)), belly, shade=False)
        d = "縄に吊るした干し魚 2 尾"
    elif k == 1:   # 串に刺した開きの魚
        ic.put(ic.mask(L([(3, 29), (29, 3)], 1)), WOOD, shade=False)
        ic.put(ic.mask(P([(7, 18), (14, 8), (24, 9), (25, 16), (17, 25), (8, 25)])), body)
        ic.put(ic.mask(P([(10, 19), (15, 12), (21, 12), (21, 17), (16, 22)])), belly)
        for t in range(4):
            ic.px([(12 + 3 * t, 15 + t % 2)], (120, 82, 56))
        d = "串に刺した開きの魚"
    else:   # 葉に包んだ一尾
        ic.put(ic.mask(P([(2, 22), (12, 8), (30, 10), (22, 26)])), (90, 130, 60))
        ic.put(ic.mask(E((7, 12, 25, 22))), body)
        ic.put(ic.mask(P([(25, 14), (30, 10), (30, 23), (25, 20)])), body)
        ic.px([(10, 15)], (30, 24, 20))
        ic.put(ic.mask(L([(9, 19), (22, 19)], 1)), belly, shade=False)
        ic.put(ic.mask(L([(16, 8), (14, 26)], 1)), CORD, shade=False)
        d = "葉に包んだ一尾"
    return ic.done(), d


def honey(k):
    ic = Icon()
    gold = (226, 160, 40)
    if k == 0:   # 蓋の布を縛った土の小壺と木の匙
        ic.put(ic.mask(E((5, 10, 27, 30))), CLAY)
        ic.put(ic.mask(E((8, 6, 24, 13))), PAPER)
        ic.put(ic.mask(L([(8, 12), (24, 12)], 1)), CORD, shade=False)
        ic.put(ic.mask(L([(22, 2), (26, 16)], 2)), WOOD, shade=False)
        ic.put(ic.mask(P([(9, 18), (14, 16), (13, 22)])), gold)
        d = "布の蓋を縛った土の小壺と木の匙"
    elif k == 1:   # 蜂の巣の欠片の入った硝子瓶
        ic.put(ic.mask(R((7, 9, 25, 30))), GLASS)
        ic.put(ic.mask(R((9, 12, 23, 28))), gold)
        for cx, cy in ((12, 16), (17, 16), (14, 21), (19, 21), (12, 26)):
            ic.px([(cx, cy), (cx + 1, cy), (cx, cy + 1)], (180, 112, 20))
        ic.put(ic.mask(R((8, 4, 24, 9))), WOOD)
        d = "蜂の巣の欠片の入った硝子瓶"
    else:   # 瓢箪の蜜入れと蜂
        ic.put(ic.mask(E((7, 13, 25, 30))), gold, dark=(160, 100, 20))
        ic.put(ic.mask(E((11, 6, 21, 16))), gold, dark=(160, 100, 20))
        ic.put(ic.mask(R((14, 2, 18, 7))), WOOD)
        ic.put(ic.mask(E((22, 3, 28, 8))), (60, 48, 30))
        ic.px([(24, 5), (25, 5)], (240, 200, 80))
        ic.px([(23, 2), (26, 2)], (220, 230, 240))
        d = "瓢箪の蜜入れと蜂"
    return ic.done(), d


def meat(k):
    ic = Icon()
    m, fat = (150, 64, 46), (230, 206, 180)
    if k == 0:   # 骨付きの燻し肉
        ic.put(ic.mask(E((4, 8, 22, 26))), m)
        ic.put(ic.mask(L([(18, 18), (27, 27)], 3)), (236, 226, 204))
        ic.put(ic.mask(E((24, 24, 30, 30))), (236, 226, 204))
        ic.put(ic.mask(L([(7, 14), (17, 12)], 1)), fat, shade=False)
        d = "骨付きの燻し肉"
    elif k == 1:   # 葉の上の厚切りの塊
        ic.put(ic.mask(P([(2, 24), (8, 18), (26, 16), (30, 22), (22, 28)])), (90, 130, 60))
        ic.put(ic.mask(P([(6, 20), (10, 8), (24, 6), (26, 18), (14, 24)])), m)
        ic.put(ic.mask(L([(10, 12), (24, 10)], 1)), fat, shade=False)
        ic.put(ic.mask(L([(9, 16), (25, 14)], 1)), fat, shade=False)
        d = "葉の上の厚切りの塊"
    else:   # 縄で吊った燻し肉の塊
        ic.put(ic.mask(L([(16, 1), (16, 8)], 1)), CORD, shade=False)
        ic.put(ic.mask(lambda d: d.rounded_rectangle((7, 8, 25, 30), radius=5, fill=1)), (120, 52, 40))
        ic.put(ic.mask(L([(8, 14), (24, 14)], 1)), CORD, shade=False)
        ic.put(ic.mask(L([(7, 20), (25, 20)], 1)), CORD, shade=False)
        d = "縄で吊った燻し肉の塊"
    return ic.done(), d


def arrow(k, glow=False):
    ic = Icon()
    shaft = (156, 120, 80)
    tipc = (226, 238, 140) if glow else (150, 156, 168)
    fl = (240, 236, 180) if glow else (220, 214, 200)
    if k == 0:   # 一本の矢 (羽 3 枚)
        ic.put(ic.mask(L([(5, 27), (25, 7)], 1)), shaft, shade=False)
        ic.put(ic.mask(P([(23, 5), (29, 3), (27, 9)])), tipc)
        ic.put(ic.mask(P([(3, 23), (8, 22), (6, 27)])), fl)
        ic.put(ic.mask(P([(7, 29), (8, 24), (3, 26)])), fl)
        d = "一本の矢 (三枚羽)"
    elif k == 1:   # 束ねた三本
        for o in (-3, 0, 3):
            ic.put(ic.mask(L([(5 + o, 27), (25 + o, 7)], 1)), shaft, shade=False)
            ic.put(ic.mask(P([(23 + o, 5), (28 + o, 4), (26 + o, 9)])), tipc)
            ic.put(ic.mask(P([(3 + o, 25), (7 + o, 24), (5 + o, 28)])), fl)
        ic.put(ic.mask(L([(12, 16), (18, 22)], 2)), CORD, shade=False)
        d = "麻縄で束ねた三本"
    else:   # 骨の鏃と赤い羽
        ic.put(ic.mask(L([(5, 27), (24, 8)], 1)), shaft, shade=False)
        ic.put(ic.mask(P([(21, 7), (28, 2), (26, 11)])), (234, 226, 200) if not glow else tipc)
        ic.put(ic.mask(P([(3, 22), (9, 21), (6, 28)])), (196, 60, 48) if not glow else fl)
        ic.put(ic.mask(P([(8, 29), (9, 23), (3, 26)])), (196, 60, 48) if not glow else fl)
        d = "骨の鏃と赤い羽" if not glow else "骨の鏃と光る羽"
    if glow:
        ic.px([(26, 2), (30, 5), (24, 1), (29, 1)], (255, 255, 200))
    return ic.done(), d


def glow_arrow(k):
    """光の矢: 旅の矢とは形を変える (色違いにしない)"""
    ic = Icon()
    shaft = (172, 150, 110)
    lit = (236, 248, 170)
    if k == 0:   # 光る石の玉を鏃にした矢。玉のまわりに光の粒
        ic.put(ic.mask(L([(4, 28), (21, 11)], 1)), shaft, shade=False)
        ic.put(ic.mask(E((19, 4, 28, 13))), lit, light=(255, 255, 230), dark=(170, 190, 90))
        ic.px([(22, 7), (23, 7)], (255, 255, 255))
        ic.put(ic.mask(R((12, 18, 15, 21))), PAPER)
        ic.put(ic.mask(P([(2, 25), (6, 25), (4, 30)])), (226, 226, 236))
        ic.px([(30, 3), (29, 14), (17, 3), (30, 9)], (255, 255, 200))
        d = "光る石の玉を鏃にした矢"
    elif k == 1:   # 二股の鏃と、光を織った吹き流しの羽
        ic.put(ic.mask(L([(5, 27), (23, 9)], 1)), shaft, shade=False)
        ic.put(ic.mask(P([(21, 7), (30, 2), (25, 9)])), (196, 204, 216))
        ic.put(ic.mask(P([(23, 9), (30, 6), (25, 11)])), (196, 204, 216))
        for t in range(3):
            ic.put(ic.mask(L([(6 - t, 25 + t), (1 + t, 31)], 1)), lit, shade=False)
        ic.put(ic.mask(L([(7, 24), (2, 22)], 1)), lit, shade=False)
        d = "二股の鏃と光の吹き流し"
    else:   # 長い硝子の鏃と、鏃のまわりの光の輪
        ic.put(ic.mask(L([(4, 28), (20, 12)], 1)), shaft, shade=False)
        ic.put(ic.mask(E((17, 2, 31, 16))), (120, 140, 70), shade=False)
        ic.put(ic.mask(E((19, 4, 29, 14))), (40, 44, 30), shade=False)
        ic.put(ic.mask(P([(19, 13), (29, 3), (22, 15)])), (210, 240, 250))
        ic.put(ic.mask(P([(3, 23), (8, 24), (5, 29)])), (150, 170, 120))
        d = "長い硝子の鏃と光の輪"
    return ic.done(), d


FOODS = [("town_bread", "麦のパン", bread), ("dried_fish", "干し魚", fish), ("honey_jar", "蜂蜜の小壺", honey), ("smoked_meat", "燻し肉", meat),
         ("travel_arrow", "旅の矢", lambda k: arrow(k)), ("glow_arrow", "光の矢", glow_arrow)]


def font(sz):
    for p in ("C:/Windows/Fonts/YuGothB.ttc", "C:/Windows/Fonts/meiryob.ttc"):
        try:
            return ImageFont.truetype(p, sz)
        except Exception:
            pass
    return ImageFont.load_default()


def sheet(rows, name):
    """rows = [(品の名, [(案の絵, 一言) x 3])]"""
    cw, ch = 300, 330
    W = Image.new("RGB", (cw * 3 + 180, ch * len(rows)), (34, 30, 42))
    dr = ImageDraw.Draw(W)
    for r, (jp, opts) in enumerate(rows):
        dr.text((10, r * ch + 140), jp, font=font(22), fill=(255, 232, 160))
        for k, (img, desc) in enumerate(opts):
            x0, y0 = 180 + k * cw, r * ch + 20
            big = img.resize((256, 256), Image.NEAREST)
            W.paste(big, (x0, y0), big)
            W.paste(img, (x0 + 262, y0 + 220), img)
            dr.text((x0, y0 + 262), f"{'ABC'[k]}  {desc}", font=font(15), fill=(226, 220, 244))
    W.save(OUT / name)


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    rows = []
    for i, (iid, jp, _, _) in enumerate(POTIONS):
        opts = []
        for k in range(3):
            img, d = potion(i, k)
            img.save(OUT / f"{iid}_{'abc'[k]}.png")
            opts.append((img, d))
        rows.append((jp, opts))
    sheet(rows, "compare_potions.png")
    rows = []
    for iid, jp, fn in FOODS:
        opts = []
        for k in range(3):
            img, d = fn(k)
            img.save(OUT / f"{iid}_{'abc'[k]}.png")
            opts.append((img, d))
        rows.append((jp, opts))
    sheet(rows, "compare_food_arrows.png")
    print("ok", OUT)


if __name__ == "__main__":
    main()
