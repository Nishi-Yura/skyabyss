"""置き換えの第 3 段 (素材と道具類) の 32x32 アイコンの 3 案 (2026-10-27 の声「ブロック以外はすべて mod で」)。
描き方は第 2 段 (icons_phase2.Icon) と同じ。品ごとに案の形を変える (色違いにしない)。

実行: python tools/rough/icons_phase3.py
出力: docs/review/roughs_materials/<品>_<案>.png と compare_<n>.png
"""
from __future__ import annotations

import sys
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).resolve().parent))
from icons_phase2 import Icon, E, P, R, L, sheet, CORD, WOOD, PAPER, GLASS, CLAY  # noqa: E402
import icons_phase2  # noqa: E402

OUT = Path(__file__).resolve().parents[2] / "docs/review/roughs_materials"
icons_phase2.OUT = OUT
IRON = (124, 128, 138)
COPPER = (196, 112, 64)
COAL = (52, 50, 56)
GOLD = (232, 186, 64)
SKY = (150, 210, 236)
HIDE = (150, 104, 70)
EMBER = (232, 96, 40)


def nugget(ic, base, pts, light=None):
    ic.put(ic.mask(P(pts)), base, light=light)


# ------------------------------------------------------------------ 素材
def iron_lump(k):
    ic = Icon()
    if k == 0:   # 打ち延ばした棒の鉄 2 本を重ねる
        ic.put(ic.mask(P([(3, 20), (22, 10), (28, 14), (9, 25)])), IRON)
        ic.put(ic.mask(P([(5, 26), (24, 16), (29, 20), (11, 30)])), (104, 108, 118))
        ic.px([(12, 15), (16, 13)], (180, 186, 196))
        d = "打ち延ばした鉄の棒 2 本"
    elif k == 1:   # でこぼこの鉄の塊 (錆の点)
        nugget(ic, IRON, [(6, 14), (12, 6), (22, 6), (28, 14), (26, 26), (14, 29), (5, 23)])
        ic.px([(11, 20), (19, 23), (22, 12), (15, 16)], (150, 86, 52))
        d = "でこぼこの鉄の塊 (錆の点)"
    else:   # 縄で括った鉄の板
        ic.put(ic.mask(R((5, 8, 27, 26))), IRON)
        ic.put(ic.mask(R((7, 10, 25, 14))), (150, 156, 166), shade=False)
        ic.put(ic.mask(L([(16, 5), (16, 29)], 2)), CORD, shade=False)
        d = "縄で括った鉄の板"
    return ic.done(), d


def copper_lump(k):
    ic = Icon()
    if k == 0:   # 丸い銅の餅 (緑青)
        ic.put(ic.mask(E((4, 8, 28, 26))), COPPER)
        ic.px([(9, 18), (10, 18), (20, 21), (22, 13)], (80, 170, 140))
        d = "丸い銅の餅 (緑青の点)"
    elif k == 1:   # 巻いた銅の線
        for r in (12, 9, 6):
            ic.put(ic.mask(lambda d, r=r: d.ellipse((16 - r, 17 - r, 16 + r, 17 + r), outline=1, width=2)), COPPER, shade=False)
        ic.put(ic.mask(L([(28, 17), (30, 8)], 2)), COPPER, shade=False)
        d = "巻いた銅の線"
    else:   # 角の欠けた銅の塊
        nugget(ic, COPPER, [(5, 18), (10, 7), (24, 5), (28, 16), (20, 28), (8, 27)])
        ic.put(ic.mask(P([(10, 7), (24, 5), (18, 13)])), (230, 150, 96))
        d = "角の欠けた銅の塊"
    return ic.done(), d


def charcoal(k):
    ic = Icon()
    if k == 0:   # 割れた炭の塊 (木目)
        nugget(ic, COAL, [(5, 12), (14, 5), (27, 9), (27, 22), (16, 28), (5, 24)])
        for y in (12, 17, 22):
            ic.put(ic.mask(L([(8, y), (24, y - 2)], 1)), (30, 28, 34), shade=False)
        d = "割れた炭の塊 (木目)"
    elif k == 1:   # 紐で括った炭の棒の束
        for x in (9, 14, 19, 24):
            ic.put(ic.mask(R((x - 2, 4, x + 2, 28))), COAL)
        ic.put(ic.mask(L([(5, 16), (27, 16)], 2)), CORD, shade=False)
        d = "紐で括った炭の棒の束"
    else:   # 熾の残る炭 (赤い芯)
        nugget(ic, COAL, [(4, 18), (12, 6), (24, 8), (29, 18), (20, 29), (8, 27)])
        ic.px([(13, 17), (14, 17), (15, 18), (19, 14), (18, 22), (19, 22)], (232, 96, 40))
        d = "熾の残る炭 (赤い芯)"
    return ic.done(), d


def sky_stone(k):
    ic = Icon()
    if k == 0:   # 面を削った六角の空石
        ic.put(ic.mask(P([(16, 2), (27, 9), (27, 23), (16, 30), (5, 23), (5, 9)])), SKY, light=(236, 250, 255))
        ic.put(ic.mask(P([(16, 2), (27, 9), (16, 15), (5, 9)])), (196, 236, 250), shade=False)
        d = "面を削った六角の空石"
    elif k == 1:   # 岩に埋まった空石の柱 3 本
        ic.put(ic.mask(E((3, 20, 29, 31))), (96, 96, 106))
        for x, h in ((10, 9), (16, 4), (22, 11)):
            ic.put(ic.mask(P([(x - 3, 24), (x - 3, h + 3), (x, h), (x + 3, h + 3), (x + 3, 24)])), SKY)
        d = "岩に埋まった空石の柱"
    else:   # 雷の筋の入った空石の丸
        ic.put(ic.mask(E((5, 5, 27, 27))), SKY)
        ic.put(ic.mask(L([(10, 9), (16, 15), (13, 18), (22, 24)], 1)), (255, 250, 200), shade=False)
        d = "雷の筋の入った空石の丸"
    return ic.done(), d


def gold_grain(k):
    ic = Icon()
    if k == 0:   # 小さな粒 3 つ
        for cx, cy, r in ((11, 19, 6), (20, 13, 5), (21, 23, 4)):
            ic.put(ic.mask(E((cx - r, cy - r, cx + r, cy + r))), GOLD)
        d = "金の粒 3 つ"
    elif k == 1:   # 布袋からこぼれる金の粒
        ic.put(ic.mask(P([(6, 28), (5, 14), (11, 8), (19, 9), (22, 16), (20, 28)])), (170, 140, 100))
        ic.put(ic.mask(L([(8, 11), (18, 11)], 1)), CORD, shade=False)
        for cx, cy in ((24, 23), (27, 27), (22, 28)):
            ic.put(ic.mask(E((cx - 2, cy - 2, cx + 2, cy + 2))), GOLD)
        d = "布袋からこぼれる金の粒"
    else:   # 平たい金の小判
        ic.put(ic.mask(E((6, 4, 26, 29))), GOLD)
        ic.put(ic.mask(R((15, 9, 17, 24))), (196, 148, 40), shade=False)
        d = "平たい金の小判"
    return ic.done(), d


def glint_shard(k):
    ic = Icon()
    c1, c2 = (120, 90, 220), (90, 160, 230)
    if k == 0:   # 色の違う結晶の欠片 2 つ
        ic.put(ic.mask(P([(6, 28), (9, 10), (15, 5), (16, 26)])), c1)
        ic.put(ic.mask(P([(16, 28), (19, 12), (26, 8), (27, 27)])), c2)
        d = "色の違う結晶の欠片 2 つ"
    elif k == 1:   # 光る筋の入った石の欠片
        nugget(ic, (110, 108, 120), [(4, 18), (11, 6), (25, 7), (29, 19), (18, 29), (7, 26)])
        ic.put(ic.mask(L([(8, 22), (14, 15), (22, 13), (26, 9)], 2)), c2, shade=False)
        ic.px([(14, 15), (22, 13)], (230, 250, 255))
        d = "光る筋の入った石の欠片"
    else:   # 星形にきらめく小さな結晶
        ic.put(ic.mask(P([(16, 2), (19, 13), (30, 16), (19, 19), (16, 30), (13, 19), (2, 16), (13, 13)])), c1, light=(220, 200, 255))
        d = "星形にきらめく小さな結晶"
    return ic.done(), d


def flint_stone(k):
    ic = Icon()
    fl = (70, 72, 82)
    if k == 0:   # 打ち欠いた火打ち石 (刃の縁)
        ic.put(ic.mask(P([(5, 24), (9, 8), (22, 4), (28, 14), (18, 28)])), fl)
        ic.put(ic.mask(L([(9, 8), (22, 4), (28, 14)], 1)), (170, 176, 190), shade=False)
        d = "打ち欠いた火打ち石"
    elif k == 1:   # 火打ち石と火打ち金
        ic.put(ic.mask(P([(4, 26), (8, 12), (18, 10), (20, 24)])), fl)
        ic.put(ic.mask(lambda d: d.arc((14, 4, 30, 20), 180, 360, fill=1, width=3)), IRON, shade=False)
        ic.px([(21, 8), (25, 6), (19, 12)], (255, 220, 120))
        d = "火打ち石と火打ち金"
    else:   # 割った断面の白い火打ち石
        ic.put(ic.mask(E((4, 6, 28, 28))), (226, 222, 210))
        ic.put(ic.mask(E((8, 10, 24, 24))), fl)
        d = "割った断面 (白い皮と黒い芯)"
    return ic.done(), d


def clay_lump(k):
    ic = Icon()
    cl = (150, 156, 172)
    if k == 0:   # 丸めた粘土の玉 (指の跡)
        ic.put(ic.mask(E((5, 6, 27, 28))), cl)
        ic.px([(13, 13), (14, 13), (18, 17), (19, 17), (12, 21)], (120, 126, 142))
        d = "丸めた粘土の玉 (指の跡)"
    elif k == 1:   # 葉に包んだ粘土
        ic.put(ic.mask(P([(3, 20), (12, 6), (28, 10), (24, 28)])), (90, 130, 60))
        ic.put(ic.mask(E((9, 10, 23, 24))), cl)
        d = "葉に包んだ粘土"
    else:   # 四角く切った粘土の塊
        ic.put(ic.mask(R((6, 9, 26, 27))), cl)
        ic.put(ic.mask(L([(6, 9), (26, 27)], 1)), (120, 126, 142), shade=False)
        d = "四角く切った粘土の塊 (糸の切り跡)"
    return ic.done(), d


def wheat_sheaf(k):
    ic = Icon()
    straw, grain = (214, 180, 96), (236, 206, 120)
    if k == 0:   # 縄で縛った麦の束
        for x in range(9, 24, 3):
            ic.put(ic.mask(L([(16, 30), (x, 8)], 1)), straw, shade=False)
            ic.put(ic.mask(E((x - 2, 3, x + 2, 10))), grain)
        ic.put(ic.mask(L([(11, 20), (21, 20)], 2)), CORD, shade=False)
        d = "縄で縛った麦の束"
    elif k == 1:   # 穂を垂れた二本
        for x0, x1 in ((10, 6), (20, 25)):
            ic.put(ic.mask(L([(16, 30), (x0, 12), (x1, 6)], 1)), straw, shade=False)
            ic.put(ic.mask(E((x1 - 3, 2, x1 + 3, 14))), grain)
        d = "穂を垂れた二本"
    else:   # 籠に盛った麦の穂
        ic.put(ic.mask(P([(4, 16), (28, 16), (24, 29), (8, 29)])), WOOD)
        for x in range(8, 26, 4):
            ic.put(ic.mask(E((x - 2, 6, x + 2, 17))), grain)
        d = "籠に盛った麦の穂"
    return ic.done(), d


def thread_spool(k):
    ic = Icon()
    th = (226, 220, 200)
    if k == 0:   # 木の糸巻き
        ic.put(ic.mask(R((8, 4, 24, 8))), WOOD)
        ic.put(ic.mask(R((8, 24, 24, 28))), WOOD)
        ic.put(ic.mask(R((10, 8, 22, 24))), th)
        for y in range(10, 24, 3):
            ic.put(ic.mask(L([(10, y), (22, y + 1)], 1)), (196, 190, 170), shade=False)
        d = "木の糸巻き"
    elif k == 1:   # 糸玉と針
        ic.put(ic.mask(E((5, 9, 25, 29))), th)
        for a in range(3):
            ic.put(ic.mask(lambda d, a=a: d.arc((7 + a * 2, 11, 23 - a * 2, 27), 200, 340, fill=1, width=1)), (196, 190, 170), shade=False)
        ic.put(ic.mask(L([(18, 6), (30, 2)], 1)), (180, 186, 196), shade=False)
        d = "糸玉と針"
    else:   # 綛 (かせ) にした糸の輪
        ic.put(ic.mask(lambda d: d.ellipse((4, 8, 28, 26), outline=1, width=4)), th, shade=False)
        ic.put(ic.mask(L([(4, 17), (10, 17)], 3)), CORD, shade=False)
        d = "綛にした糸の輪"
    return ic.done(), d


def hide(k):
    ic = Icon()
    if k == 0:   # 広げた獣の革
        ic.put(ic.mask(P([(16, 3), (24, 6), (29, 3), (27, 13), (29, 28), (16, 25), (3, 28), (5, 13), (3, 3), (8, 6)])), HIDE)
        d = "広げた獣の革"
    elif k == 1:   # 巻いて紐で縛った革
        ic.put(ic.mask(P([(4, 12), (24, 6), (28, 18), (8, 26)])), HIDE)
        ic.put(ic.mask(E((22, 5, 30, 19))), (120, 82, 54))
        for t in (0.35, 0.65):
            ic.put(ic.mask(L([(int(4 + 20 * t), int(12 - 6 * t)), (int(8 + 20 * t), int(26 - 8 * t))], 1)), CORD, shade=False)
        d = "巻いて紐で縛った革"
    else:   # 畳んだ革の重ね
        for j, y in enumerate((20, 14, 8)):
            ic.put(ic.mask(R((5 + j, y, 27 - j, y + 6))), (150 - j * 12, 104 - j * 8, 70))
        d = "畳んだ革の重ね"
    return ic.done(), d


def ember_rod(k):
    ic = Icon()
    if k == 0:   # 熾火の芯の黒い棒
        ic.put(ic.mask(L([(6, 27), (26, 5)], 4)), (60, 40, 36), shade=False)
        ic.put(ic.mask(L([(8, 25), (24, 7)], 1)), EMBER, shade=False)
        d = "熾火の芯の黒い棒"
    elif k == 1:   # 先の燃える棒
        ic.put(ic.mask(L([(6, 27), (22, 9)], 3)), WOOD, shade=False)
        ic.put(ic.mask(P([(20, 12), (24, 2), (30, 9), (27, 15)])), EMBER, light=(255, 210, 120))
        d = "先の燃える棒"
    else:   # ねじれた溶岩の棒
        for t in range(6):
            ic.put(ic.mask(E((6 + t * 3, 22 - t * 3, 12 + t * 3, 28 - t * 3))), (160 + t * 12, 60 + t * 6, 30))
        d = "ねじれた溶岩の棒"
    return ic.done(), d


def ember_drop(k):
    ic = Icon()
    if k == 0:   # 冷めない火の雫
        ic.put(ic.mask(P([(16, 3), (24, 16)])), EMBER)
        ic.put(ic.mask(E((8, 12, 24, 29))), EMBER, light=(255, 200, 110))
        ic.put(ic.mask(P([(16, 2), (23, 15), (9, 15)])), EMBER, light=(255, 200, 110))
        ic.px([(13, 18), (14, 17)], (255, 246, 200))
        d = "冷めない火の雫"
    elif k == 1:   # 殻の割れ目から光る熾
        nugget(ic, (70, 50, 46), [(4, 17), (11, 6), (24, 6), (28, 18), (19, 29), (7, 26)])
        ic.put(ic.mask(L([(9, 20), (15, 13), (18, 18), (24, 11)], 2)), EMBER, shade=False)
        d = "殻の割れ目から光る熾"
    else:   # 硝子の小瓶に封じた火
        ic.put(ic.mask(E((8, 9, 24, 29))), GLASS)
        ic.put(ic.mask(E((11, 14, 21, 26))), EMBER)
        ic.put(ic.mask(R((13, 3, 19, 10))), (60, 50, 56))
        d = "硝子の小瓶に封じた火"
    return ic.done(), d


# ------------------------------------------------------------------ 道具類
def wayfinder(k):
    ic = Icon()
    if k == 0:   # 真鍮の丸い方位盤 (四つの星の針)
        ic.put(ic.mask(E((3, 3, 29, 29))), (196, 160, 80))
        ic.put(ic.mask(E((6, 6, 26, 26))), (234, 226, 200))
        ic.put(ic.mask(P([(16, 7), (18, 16), (16, 25), (14, 16)])), (180, 40, 40), shade=False)
        d = "真鍮の丸い方位盤"
    elif k == 1:   # 鯨骨の板に刻んだ方位と吊った針
        ic.put(ic.mask(R((4, 6, 28, 28))), (226, 214, 184))
        ic.put(ic.mask(L([(16, 8), (16, 26)], 1)), (120, 100, 70), shade=False)
        ic.put(ic.mask(L([(6, 17), (26, 17)], 1)), (120, 100, 70), shade=False)
        ic.put(ic.mask(L([(16, 1), (16, 6)], 1)), CORD, shade=False)
        ic.put(ic.mask(P([(16, 9), (19, 17), (16, 15), (13, 17)])), (60, 60, 70), shade=False)
        d = "鯨骨の板の方位と吊った針"
    else:   # 水に浮かべた針の皿
        ic.put(ic.mask(E((3, 9, 29, 27))), CLAY)
        ic.put(ic.mask(E((6, 11, 26, 24))), (90, 150, 190))
        ic.put(ic.mask(L([(10, 18), (22, 16)], 1)), (230, 230, 236), shade=False)
        d = "水に浮かべた針の皿"
    return ic.done(), d


def blank_chart(k):
    ic = Icon()
    if k == 0:   # 巻いた海図 (紐)
        ic.put(ic.mask(P([(4, 22), (22, 4), (28, 10), (10, 28)])), PAPER)
        ic.put(ic.mask(E((22, 3, 29, 10))), (206, 192, 160))
        ic.put(ic.mask(L([(12, 13), (18, 19)], 2)), (160, 40, 40), shade=False)
        d = "巻いた海図 (赤い紐)"
    elif k == 1:   # 広げた白紙の海図 (四隅に石)
        ic.put(ic.mask(R((4, 6, 28, 26))), PAPER)
        for x, y in ((5, 7), (26, 7), (5, 24), (26, 24)):
            ic.put(ic.mask(E((x - 2, y - 2, x + 2, y + 2))), (110, 110, 120))
        ic.put(ic.mask(L([(8, 16), (24, 16)], 1)), (196, 186, 160), shade=False)
        d = "広げた白紙の海図 (四隅に石)"
    else:   # 畳んだ海図と炭筆
        ic.put(ic.mask(P([(4, 10), (16, 6), (28, 10), (28, 26), (16, 22), (4, 26)])), PAPER)
        ic.put(ic.mask(L([(16, 6), (16, 22)], 1)), (196, 186, 160), shade=False)
        ic.put(ic.mask(L([(18, 30), (30, 18)], 2)), COAL, shade=False)
        d = "畳んだ海図と炭筆"
    return ic.done(), d


def skiff(k):
    ic = Icon()
    if k == 0:   # 横から見た板張りの小舟と櫂
        ic.put(ic.mask(P([(2, 16), (30, 16), (25, 25), (7, 25)])), WOOD)
        ic.put(ic.mask(L([(4, 19), (28, 19)], 1)), (90, 60, 38), shade=False)
        ic.put(ic.mask(L([(18, 4), (12, 22)], 2)), (176, 140, 96), shade=False)
        d = "板張りの小舟と櫂"
    elif k == 1:   # 舳先の反った丸木舟
        ic.put(ic.mask(P([(2, 12), (6, 18), (26, 18), (30, 12), (24, 24), (8, 24)])), (140, 96, 60))
        ic.put(ic.mask(E((8, 15, 24, 20))), (90, 60, 38))
        d = "舳先の反った丸木舟"
    else:   # 帆の付いた小舟
        ic.put(ic.mask(P([(3, 22), (29, 22), (24, 29), (8, 29)])), WOOD)
        ic.put(ic.mask(L([(16, 3), (16, 22)], 1)), (90, 60, 38), shade=False)
        ic.put(ic.mask(P([(17, 4), (27, 19), (17, 19)])), (226, 216, 192))
        d = "帆の付いた小舟"
    return ic.done(), d


def water_skin(k, full):
    ic = Icon()
    skin = HIDE if full else (176, 140, 104)
    if k == 0:   # 革の水袋 (栓と紐)
        shape = [(8, 10), (24, 10), (27, 22), (16, 30), (5, 22)] if full else [(9, 12), (23, 12), (25, 22), (16, 27), (7, 22)]
        ic.put(ic.mask(P(shape)), skin)
        ic.put(ic.mask(R((13, 4, 19, 10))), (90, 60, 38))
        ic.put(ic.mask(L([(8, 11), (4, 4), (12, 4)], 1)), CORD, shade=False)
        d = "革の水袋"
    elif k == 1:   # 瓢箪の水筒
        ic.put(ic.mask(E((7, 13, 25, 30) if full else (8, 15, 24, 30))), (200, 170, 100) if full else (220, 196, 140))
        ic.put(ic.mask(E((11, 5, 21, 16))), (200, 170, 100) if full else (220, 196, 140))
        ic.put(ic.mask(R((14, 1, 18, 6))), WOOD)
        d = "瓢箪の水筒"
    else:   # 竹の水筒
        ic.put(ic.mask(R((10, 3, 22, 30))), (132, 156, 74))
        ic.put(ic.mask(R((9, 14, 23, 15))), (98, 118, 52), shade=False)
        ic.put(ic.mask(L([(10, 6), (4, 14)], 1)), CORD, shade=False)
        d = "竹の水筒"
    if full:
        ic.px([(26, 4), (27, 5), (26, 6)], (120, 190, 240))
    return ic.done(), d + (" (水入り)" if full else " (空)")


def bookmark(k):
    ic = Icon()
    indigo = (48, 62, 120)
    if k == 0:   # 藍染めの布の栞と房
        ic.put(ic.mask(R((11, 2, 21, 24))), indigo)
        ic.put(ic.mask(P([(11, 24), (21, 24), (16, 28)])), indigo)
        ic.put(ic.mask(L([(16, 28), (16, 31)], 1)), CORD, shade=False)
        d = "藍染めの布の栞と房"
    elif k == 1:   # 藍の紐を結んだ木の栞
        ic.put(ic.mask(R((10, 4, 22, 28))), (176, 140, 96))
        ic.put(ic.mask(L([(16, 2), (12, 12), (20, 20)], 2)), indigo, shade=False)
        d = "藍の紐を結んだ木の栞"
    else:   # 藍の押し花の栞
        ic.put(ic.mask(R((9, 3, 23, 29))), PAPER)
        ic.put(ic.mask(E((12, 9, 20, 17))), indigo)
        ic.put(ic.mask(L([(16, 17), (16, 25)], 1)), (90, 130, 60), shade=False)
        d = "藍の押し花の栞"
    return ic.done(), d


ITEMS = [("iron_lump", "鉄の塊", iron_lump), ("copper_lump", "銅の塊", copper_lump), ("charcoal_lump", "炭", charcoal),
         ("sky_stone", "空石", sky_stone), ("gold_grain", "金の粒", gold_grain), ("glint_shard", "光る石の欠片", glint_shard),
         ("flint_stone", "火打ち石", flint_stone), ("clay_lump", "粘土の塊", clay_lump), ("wheat_sheaf", "麦の束", wheat_sheaf),
         ("thread_spool", "糸巻き", thread_spool), ("hide", "獣の革", hide), ("ember_rod", "熾火の棒", ember_rod), ("ember_drop", "熾の雫", ember_drop),
         ("wayfinder", "方位盤", wayfinder), ("blank_chart", "白紙の海図", blank_chart), ("skiff", "小舟", skiff),
         ("water_skin", "水袋 (空)", lambda k: water_skin(k, False)), ("water_skin_full", "水袋 (水入り)", lambda k: water_skin(k, True)),
         ("indigo_bookmark", "藍の栞", bookmark)]


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    rows = []
    for iid, jp, fn in ITEMS:
        opts = []
        for k in range(3):
            img, d = fn(k)
            img.save(OUT / f"{iid}_{'abc'[k]}.png")
            opts.append((img, d))
        rows.append((jp, opts))
    for n, i in enumerate(range(0, len(rows), 7)):
        sheet(rows[i:i + 7], f"compare_{n}.png")
    print("ok", len(rows))


if __name__ == "__main__":
    main()
