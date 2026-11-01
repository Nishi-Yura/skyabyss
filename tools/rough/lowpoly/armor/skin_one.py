"""スキン (docs/design/54 の 6.1。物語と関係のない見た目だけの一式) の一着一案。最初の組 = 侍・海賊の船長・白銀の騎士・宇宙飛行士。
作りは旅装 (travel_one) と同じ: 部位 helmet / chestplate / leggings / boots、骨は armor*、塗りは tpaint (材質 → 色と質感の表をこのファイルで足す)。
色違いにしない: 一着ずつ形が違う。顔を出すもの (侍・海賊) は頭の前を開け、騎士の兜と宇宙の兜は顔を覆う。
実行: python skin_one.py [名前 ...]  -> docs/review/skins/<名前>.png (前・横・後ろ・3/4) と docs/review/skins/skins_1.png (並べたもの)
"""
from __future__ import annotations

import math
import sys

import numpy as np
from PIL import Image, ImageDraw

import aview
import build_one
import lpkit
import tpaint
from akit import *  # noqa: F401,F403
from akit import V, bx, crv, spk, rr, shell, feather, hide_front, rot_x, rot_y, rot_z, ARM, LEG, BOOT, cull_inside, build_atlas, tri_count, ROOT
from travel_one import sleeve

H_, B_ = "armorHead", "armorBody"
OUT = ROOT / "docs/review/skins"
A_ = np.array

# ---------------------------------------------------------------- 色 (tpaint の表へ足す)
tpaint.C.update(
    LACQUER=A_([(14, 14, 20), (24, 24, 32), (38, 38, 50), (56, 58, 72), (80, 84, 100)]),
    RED_SILK=A_([(110, 18, 22), (150, 28, 30), (188, 44, 40), (220, 70, 58), (240, 110, 90)]),
    GOLD=A_([(110, 76, 22), (156, 112, 34), (200, 152, 52), (232, 190, 84), (250, 224, 140)]),
    INDIGO=A_([(18, 22, 44), (28, 34, 64), (40, 50, 88), (56, 68, 112), (76, 90, 136)]),
    CRIMSON=A_([(70, 12, 18), (104, 20, 28), (138, 30, 38), (170, 46, 50), (200, 72, 70)]),
    NAVY=A_([(14, 20, 40), (22, 30, 58), (32, 44, 80), (46, 60, 104)]),
    LINEN=A_([(176, 170, 156), (206, 200, 186), (228, 224, 212), (244, 242, 234)]),
    CREAM=A_([(150, 132, 100), (180, 162, 126), (206, 190, 152), (226, 214, 180)]),
    BLACK_LEATHER=A_([(14, 12, 12), (24, 20, 20), (36, 30, 30), (52, 44, 42), (72, 62, 58)]),
    SILVER=A_([(70, 76, 88), (102, 110, 124), (138, 146, 160), (176, 184, 196), (214, 220, 230)]),
    MAIL=A_([(46, 50, 56), (70, 74, 80), (96, 100, 106), (124, 128, 132)]),
    ROYAL=A_([(16, 30, 84), (24, 44, 116), (36, 62, 150), (54, 86, 180), (80, 114, 204)]),
    PLUME_RED=A_([(120, 20, 24), (164, 30, 34), (204, 48, 46), (232, 80, 72)]),
    SUIT_WHITE=A_([(170, 174, 182), (202, 206, 212), (226, 228, 232), (242, 244, 246), (252, 252, 252)]),
    SUIT_GREY=A_([(70, 74, 82), (96, 100, 108), (124, 128, 136), (152, 156, 162)]),
    VISOR=A_([(120, 74, 16), (176, 116, 30), (222, 166, 58), (246, 212, 120), (255, 240, 196)]),
    HOSE=A_([(110, 114, 120), (140, 144, 150), (170, 174, 178)]),
    FLAG=A_([(30, 60, 140), (44, 84, 178), (200, 40, 40), (236, 236, 236)]),
)
tpaint.SETS.update({
    "samurai": dict(main=("LACQUER", 0.48, "metal"), cloth=("RED_SILK", 0.50, "rope"), cord=("RED_SILK", 0.55, "rope"), metal=("GOLD", 0.60, "metal"),
                    edge=("GOLD", 0.66, "metal"), sec=("INDIGO", 0.48, "wool"), fur=("STRAW", 0.58, "straw"), acc=("LACQUER", 0.40, "leather"),
                    glow="G_LAVA"),
    "pirate": dict(main=("CRIMSON", 0.50, "wool"), edge=("NAVY", 0.50, "wool"), sec=("LINEN", 0.62, "wool"), metal=("GOLD", 0.62, "metal"),
                   cloth=("BLACK_LEATHER", 0.50, "leather"), acc=("CREAM", 0.55, "wool"), cord=("LEATHER", 0.50, "leather"), fur=("LINEN", 0.70, "feather"),
                   glow="G_PINK"),
    "knight": dict(main=("SILVER", 0.56, "metal"), sec=("MAIL", 0.52, "rope"), cloth=("ROYAL", 0.50, "wool"), acc=("GOLD", 0.60, "metal"),
                   edge=("SILVER", 0.70, "metal"), metal=("GOLD", 0.62, "metal"), cord=("LEATHER", 0.50, "leather"), fur=("PLUME_RED", 0.55, "feather"),
                   glow="G_CYAN"),
    "astronaut": dict(main=("SUIT_WHITE", 0.58, "wool"), acc=("SUIT_GREY", 0.52, "metal"), glass=("VISOR", 0.58, "glass"), cord=("HOSE", 0.55, "rope"),
                      edge=("FLAG", 0.50, "leather"), metal=("SUIT_GREY", 0.62, "metal"), sec=("SUIT_GREY", 0.45, "leather"), glow="G_CYAN"),
})


def empty():
    return {"helmet": [], "chestplate": [], "leggings": [], "boots": []}


def band(bone, mat, cx, y, w, h, d, cz=0.0, skip=("u", "b")):
    return bx(bone, mat, (cx, y, cz), (w, h, d), skip=skip)


# ================================================================== 侍 (紅糸威の大鎧)
def samurai():
    p = empty()
    h = p["helmet"]
    # 兜の鉢: 黒漆の丸い鉢 (y 30〜34)、前に眉庇
    h += shell(H_, "main", [rr(30.0, 5.0, -5.0, 5.0, cut=1.8), rr(32.4, 4.8, -4.8, 4.8, cut=2.0), rr(34.2, 3.4, -3.4, 3.4, cut=1.6),
                            rr(35.0, 1.4, -1.4, 1.4, cut=0.6)], caps=(False, "main"))
    h += bx(H_, "main", (0, 30.2, -5.4), (9.6, 0.6, 1.8), rot=rot_x(-12))
    h += bx(H_, "edge", (0, 30.2, -6.3), (9.8, 0.4, 0.3), rot=rot_x(-12))
    # 錣: 鉢の下から首の後ろと横へ三段に広がる (前は顔を出す)。段の間に紅の糸
    for k, (y, hx, zb) in enumerate(((29.4, 5.4, 5.6), (27.6, 6.0, 6.3), (25.8, 6.6, 7.0))):
        tier = shell(H_, "main", [rr(y + 1.0, hx - 0.4, -2.6, zb - 0.6, cut=1.6), rr(y - 0.6, hx, -2.2, zb, cut=1.8)], caps=(False, False))
        h += [f for f in tier if f.normal[2] > -0.35]
        lace = shell(H_, "cloth", [rr(y - 0.6, hx + 0.05, -2.2, zb + 0.05, cut=1.8), rr(y - 0.9, hx + 0.05, -2.2, zb + 0.05, cut=1.8)], caps=(False, False))
        h += [f for f in lace if f.normal[2] > -0.35]
    # 吹返し (顔の両わきへ反る板) と鍬形 (金の角 二本) と前立の丸
    for s in (-1, 1):
        h += bx(H_, "main", (s * 5.3, 29.2, -3.8), (0.8, 2.6, 2.4), rot=rot_y(s * 30))
        h += bx(H_, "edge", (s * 5.6, 29.2, -4.4), (0.4, 2.8, 0.5), rot=rot_y(s * 30))
        h += feather(H_, "metal", (s * 0.9, 31.0, -5.6), (s * 4.2, 37.6, -6.2), 0.8, th=0.3, big=False, n=(0, 0, 1))
    h += spk(H_, "cloth", (0, 31.4, -5.8), (0, 0, -1), 0.5, 1.0, sides=8, rings=1)
    c = p["chestplate"]
    # 胴: 黒漆の札を六段、段の間に紅の糸。前に縦の糸 (威し)
    for k in range(6):
        y = 13.2 + k * 1.9
        c += band(B_, "main", 0, y, 9.8, 1.5, 5.8)
        c += band(B_, "cloth", 0, y + 0.92, 9.6, 0.34, 5.6)
    for x in (-3.3, -1.1, 1.1, 3.3):
        c += bx(B_, "cloth", (x, 18.0, -2.95), (0.35, 11.0, 0.2))
    # 胸の上の板 (栴檀板と鳩尾板) と、肩の上の紐
    c += bx(B_, "main", (-2.6, 22.6, -3.3), (2.6, 3.2, 0.6))
    c += bx(B_, "main", (2.6, 22.8, -3.3), (2.2, 2.6, 0.6))
    for s in (-1, 1):
        c += bx(B_, "edge", (s * 2.6, 24.2, -3.3), (2.4, 0.4, 0.7))
        c += crv(B_, "cord", [(s * 2.0, 24.4, -3.0), (s * 3.0, 24.9, 0.0), (s * 2.0, 24.4, 3.0)], 0.3, 0.3, sides=4)
    # 背の総角 (紅の大きな結び)
    c += spk(B_, "cloth", (0, 20.0, 3.2), (0, 0, 1), 0.8, 1.2, sides=6, rings=1)
    for s in (-1, 1):
        c += crv(B_, "cloth", [(0, 20.0, 3.6), (s * 1.0, 18.0, 3.9), (s * 0.7, 15.6, 3.9)], 0.35, 0.25, sides=4)
    # 腕: 籠手 (藍の袖に黒の小札) と大袖 (腕の外に下がる大きな板、五段)
    for s in (-1, 1):
        ab = ARM[s]
        c += sleeve(ab, s, "sec")
        for k in range(3):
            c += bx(ab, "main", (s * 6.0, 15.0 + k * 1.6, -2.55), (3.2, 1.0, 0.4))
        for k in range(5):
            y = 24.0 - k * 1.8
            c += bx(ab, "main", (s * 8.7, y, 0), (0.7, 1.5, 6.0 + k * 0.2), rot=rot_z(s * 8))
            c += bx(ab, "cloth", (s * 8.85, y - 0.9, 0), (0.6, 0.3, 5.9 + k * 0.2), rot=rot_z(s * 8))
        c += bx(ab, "edge", (s * 8.6, 25.0, 0), (0.9, 0.5, 6.2), rot=rot_z(s * 8))
    lg = p["leggings"]
    # 袴 (藍) と草摺 (脚の前と後ろと外に、黒漆の札三段と紅の糸)
    for s in (-1, 1):
        L = LEG[s]
        cx = s * 2.15
        lg += bx(L, "sec", (cx, 7.4, 0), (4.8, 9.6, 5.0), taper=(1.02, 1.0), skip=("u",))
        for k in range(3):
            y = 11.4 - k * 1.7
            for z, w, dz in ((-3.0, 4.8, 0.5), (3.0, 4.8, 0.5)):
                lg += bx(L, "main", (cx, y, z), (w + k * 0.2, 1.4, dz))
                lg += bx(L, "cloth", (cx, y - 0.85, z), (w + k * 0.2 - 0.1, 0.3, dz + 0.05))
            lg += bx(L, "main", (s * 4.7, y, 0), (0.5, 1.4, 5.6 + k * 0.2))
    bt = p["boots"]
    # 臑当 (黒漆に金の縁) と草鞋
    for s in (-1, 1):
        cx = s * 2.15
        bt += bx(BOOT[s], "main", (cx, 3.4, -0.4), (4.9, 4.4, 4.6), taper=(0.96, 0.96))
        bt += bx(BOOT[s], "edge", (cx, 5.7, -0.4), (5.0, 0.4, 4.7))
        bt += bx(BOOT[s], "acc", (cx, 0.9, 0), (4.8, 1.8, 5.0))
        bt += bx(BOOT[s], "fur", (cx, 0.2, -0.2), (5.2, 0.4, 5.8), skip=("u",))
        bt += crv(BOOT[s], "cord", [(cx - 2.4, 1.2, -2.6), (cx, 0.8, -2.9), (cx + 2.4, 1.2, -2.6)], 0.25, 0.25, sides=3)
    return p


# ================================================================== 海賊の船長
def pirate():
    p = empty()
    h = p["helmet"]
    # 三角帽: 丸い山と、三方で反り上がる鍔 (角が前と左右後ろ)。鍔の縁に金、前に白い骨の印、横に白い羽根
    h += shell(H_, "cloth", [rr(29.4, 4.8, -4.8, 4.8, cut=1.8), rr(32.6, 4.4, -4.4, 4.4, cut=2.0), rr(34.4, 3.4, -3.4, 3.4, cut=1.6),
                             rr(35.0, 1.8, -1.8, 1.8, cut=0.8)], caps=(False, "cloth"))
    for k in range(3):
        a = k * 120 + 60
        R = rot_y(a)
        mid = R @ V(0, 0, 5.4)
        h += bx(H_, "cloth", tuple(V(0, 31.0, 0) + mid), (8.6, 3.2, 0.5), rot=R @ rot_x(-22))
        top = R @ V(0, 0, 5.9)
        h += bx(H_, "metal", tuple(V(0, 32.5, 0) + top), (8.8, 0.35, 0.6), rot=R @ rot_x(-22))
    h += bx(H_, "cloth", (0, 29.6, 0), (10.6, 0.5, 10.6), rot=rot_y(45), taper=(0.96, 0.96))
    h += bx(H_, "sec", (0, 31.2, -5.6), (1.4, 1.4, 0.4))
    h += bx(H_, "dark", (0, 31.3, -5.85), (0.9, 0.4, 0.1))
    h += feather(H_, "fur", (4.4, 31.6, 1.0), (6.4, 36.0, 4.4), 1.0, th=0.3, n=(1, 0, 0))
    c = p["chestplate"]
    # 長い上着 (深紅): 胴、前は開いて白いシャツと胸飾り。紺の折り返しの襟と金のボタン
    c += bx(B_, "sec", (0, 18.0, 0), (8.6, 12.0, 4.6), skip=("u", "b"))
    c += bx(B_, "main", (0, 18.0, 0.6), (9.8, 12.2, 4.6), taper=(1.0, 1.0), skip=("-z",))
    for s in (-1, 1):
        c += bx(B_, "main", (s * 3.6, 18.0, -2.4), (2.6, 12.2, 1.0), skip=("+z",))
        c += bx(B_, "edge", (s * 2.5, 21.2, -3.0), (1.4, 5.6, 0.4), rot=rot_z(s * -14))
        for k in range(4):
            c += bx(B_, "metal", (s * 3.3, 19.6 - k * 1.8, -3.0), (0.6, 0.6, 0.3))
    for k in range(4):
        c += bx(B_, "sec", (0, 22.6 - k * 0.9, -2.7 - k * 0.1), (2.2 + k * 0.4, 0.8, 0.8), taper=(0.8, 0.8))
    # 帯 (黒革) と大きな金の留め金、斜めの剣帯
    c += band(B_, "cloth", 0, 14.4, 10.2, 1.4, 5.2)
    c += bx(B_, "metal", (0, 14.4, -2.85), (2.0, 1.6, 0.4))
    c += bx(B_, "dark", (0, 14.4, -3.05), (1.0, 0.7, 0.1))
    c += crv(B_, "cord", [(3.8, 24.0, -2.6), (0.0, 19.0, -2.9), (-3.9, 14.6, -2.6)], 0.5, 0.5, sides=4)
    # 袖 (深紅) と大きな折り返しの袖口 (紺に金のボタン)、肩の金の房
    for s in (-1, 1):
        ab = ARM[s]
        c += sleeve(ab, s, "main")
        c += bx(ab, "edge", (s * 6.0, 14.4, 0), (5.2, 2.6, 5.2), taper=(1.06, 1.06))
        c += bx(ab, "metal", (s * 6.0, 14.6, -2.75), (0.6, 0.6, 0.3))
        c += bx(ab, "metal", (s * 6.0, 24.2, 0), (5.2, 0.6, 5.0))
        for k in range(5):
            c += spk(ab, "metal", (s * (4.6 + k * 0.8), 24.0, -1.8 + (k % 2) * 3.6), (0, -1, 0), 1.6, 0.25, sides=3, rings=1)
    lg = p["leggings"]
    # 半ズボン (生成り) と、上着の裾 (後ろと両わきへ、ひざの上まで)
    for s in (-1, 1):
        L = LEG[s]
        cx = s * 2.15
        lg += bx(L, "acc", (cx, 8.6, 0), (4.6, 6.6, 4.8), skip=("u",))
        lg += bx(L, "main", (cx, 9.0, 2.7), (4.9, 6.0, 0.6))
        lg += bx(L, "main", (s * 4.45, 9.2, 0.4), (0.6, 5.6, 5.0))
        lg += bx(L, "edge", (cx, 6.1, 2.8), (5.0, 0.5, 0.7))
    bt = p["boots"]
    # 長靴 (黒革。上で折り返して広がる)
    for s in (-1, 1):
        cx = s * 2.15
        bt += bx(BOOT[s], "cloth", (cx, 3.2, 0), (4.8, 6.4, 5.0), skip=("u",))
        bt += bx(BOOT[s], "cloth", (cx, 6.6, 0), (5.4, 1.8, 5.6), taper=(1.08, 1.08))
        bt += bx(BOOT[s], "dark", (cx, 0.25, -0.3), (5.0, 0.5, 5.8), skip=("u",))
        bt += bx(BOOT[s], "metal", (cx, 2.0, -2.6), (1.2, 0.8, 0.3))
    return p


# ================================================================== 白銀の騎士
def knight():
    p = empty()
    h = p["helmet"]
    # 大兜 (桶の形、顔を覆う): 上は少し丸い。目の細い隙間と息の穴、前に金の十字の補強、上に赤い羽根飾り
    h += shell(H_, "main", [rr(23.8, 4.9, -4.9, 4.9, cut=1.4), rr(30.0, 5.0, -5.1, 5.0, cut=1.5), rr(33.0, 4.7, -4.8, 4.7, cut=1.8),
                            rr(34.0, 3.4, -3.4, 3.4, cut=1.5)], caps=(False, "main"))
    h += bx(H_, "dark", (0, 29.0, -5.15), (8.2, 0.7, 0.3))
    h += bx(H_, "acc", (0, 26.6, -5.2), (0.9, 4.4, 0.4))
    h += bx(H_, "acc", (0, 29.9, -5.2), (8.6, 0.6, 0.4))
    for i in range(3):
        for s in (-1, 1):
            h += bx(H_, "dark", (s * (1.6 + i * 0.9), 26.0 - i * 0.6, -5.12), (0.35, 0.35, 0.2))
    h += bx(H_, "edge", (0, 24.2, 0), (10.2, 0.6, 10.2), skip=("u",))
    for k in range(6):
        z = -3.0 + k * 1.2
        h += feather(H_, "fur", (0, 34.0, z), (0, 37.4 - abs(k - 2.5) * 0.4, z + 1.6), 0.9, th=0.4, n=(1, 0, 0))
    c = p["chestplate"]
    # 胸当て (銀。前に稜) と上に青い陣羽織 (金の十字)、背も
    c += shell(B_, "main", [rr(12.4, 4.9, -3.0, 2.9, cut=1.2, keel=0.6), rr(18.0, 5.0, -3.3, 2.9, cut=1.2, keel=0.9), rr(24.0, 4.9, -3.0, 2.9, cut=1.2, keel=0.5)],
               caps=(False, False))
    c += bx(B_, "cloth", (0, 16.4, -3.55), (7.0, 9.4, 0.4))
    c += bx(B_, "cloth", (0, 16.4, 3.25), (7.0, 9.4, 0.4))
    for z, d in ((-3.8, 1), (3.5, -1)):
        c += bx(B_, "acc", (0, 17.0, z), (1.2, 7.0, 0.2))
        c += bx(B_, "acc", (0, 18.6, z), (5.0, 1.2, 0.2))
    c += band(B_, "cord", 0, 12.8, 10.0, 1.0, 6.2)
    c += bx(B_, "metal", (0, 12.8, -3.15), (1.4, 1.2, 0.3))
    # 肩当て (丸い銀の板を三段、外へ張る) と鎖の袖、篭手
    for s in (-1, 1):
        ab = ARM[s]
        c += sleeve(ab, s, "sec")
        for k in range(3):
            y = 24.6 - k * 1.7
            c += shell(ab, "main", [rr(y, 2.9 + k * 0.15, -3.0 - k * 0.1, 3.0 + k * 0.1, cut=1.3, cx=s * 6.4), rr(y - 1.6, 3.2 + k * 0.15, -3.2 - k * 0.1, 3.2 + k * 0.1, cut=1.3, cx=s * 6.6)],
                       caps=("main", False))
        c += bx(ab, "edge", (s * 6.6, 25.6, 0), (4.6, 0.6, 5.2))
        c += bx(ab, "main", (s * 6.0, 14.2, 0), (5.0, 2.8, 5.0), taper=(1.05, 1.05))
    lg = p["leggings"]
    # 陣羽織の裾 (前と後ろ) と、腿の板・膝の丸い当て
    for s in (-1, 1):
        L = LEG[s]
        cx = s * 2.15
        lg += bx(L, "sec", (cx, 8.6, 0), (4.6, 6.6, 4.8), skip=("u",))
        lg += bx(L, "cloth", (cx, 9.4, -2.8), (4.6, 5.2, 0.4))
        lg += bx(L, "cloth", (cx, 9.4, 2.8), (4.6, 5.2, 0.4))
        lg += bx(L, "acc", (cx, 6.9, -3.0), (4.7, 0.4, 0.5))
        lg += bx(L, "main", (cx, 8.0, -2.55), (4.2, 3.4, 0.5))
        lg += spk(L, "main", (cx, 6.0, -2.6), (0, 0, -1), 1.0, 1.4, sides=6, rings=1)
    bt = p["boots"]
    # 鉄の靴 (重ねた板とつま先の尖り)
    for s in (-1, 1):
        cx = s * 2.15
        for k in range(3):
            bt += bx(BOOT[s], "main", (cx, 5.0 - k * 1.5, -0.2 - k * 0.15), (4.9 + k * 0.05, 1.5, 5.0 + k * 0.2))
        bt += bx(BOOT[s], "main", (cx, 0.8, -3.2), (3.8, 1.4, 2.2), taper=(0.6, 0.6))
        bt += bx(BOOT[s], "dark", (cx, 0.2, -0.6), (4.9, 0.4, 6.0), skip=("u",))
    return p


# ================================================================== 宇宙飛行士
def astronaut():
    p = empty()
    h = p["helmet"]
    # 丸い大きな兜 (白) に金の面覆い、首の灰の輪、横の灯り
    h += shell(H_, "main", [rr(23.6, 4.6, -4.6, 4.6, cut=1.6), rr(26.0, 5.6, -5.6, 5.6, cut=2.2), rr(30.0, 5.9, -5.9, 5.9, cut=2.4),
                            rr(33.2, 5.0, -5.0, 5.0, cut=2.2), rr(35.0, 3.0, -3.0, 3.0, cut=1.3)], caps=(False, "main"))
    h += bx(H_, "glass", (0, 28.8, -5.75), (8.4, 6.4, 1.2), taper=(0.86, 0.9))
    h += bx(H_, "acc", (0, 32.3, -5.7), (8.0, 0.6, 1.3))
    h += bx(H_, "acc", (0, 25.3, -5.6), (7.6, 0.6, 1.3))
    h += shell(H_, "acc", [rr(23.0, 5.2, -5.2, 5.2, cut=1.8), rr(24.4, 5.2, -5.2, 5.2, cut=1.8)], caps=(False, False))
    for s in (-1, 1):
        h += bx(H_, "acc", (s * 5.9, 29.6, -1.0), (0.8, 1.6, 2.2))
        h += bx(H_, "gem", (s * 6.3, 29.6, -1.6), (0.3, 0.8, 0.8))
    c = p["chestplate"]
    # 膨らんだ服 (白)、胸の操作箱 (灰に光る釦三つ)、背の生命維持の背負い箱と管、左腕に旗の札
    c += bx(B_, "main", (0, 18.0, 0), (10.0, 12.4, 5.8), taper=(0.96, 0.96), skip=("u",))
    c += bx(B_, "acc", (0, 18.6, -3.3), (5.2, 3.6, 1.0), taper=(0.92, 0.92))
    for i, x in enumerate((-1.4, 0.0, 1.4)):
        c += bx(B_, "gem", (x, 19.0, -3.85), (0.7, 0.7, 0.2))
    c += bx(B_, "metal", (0, 17.2, -3.85), (3.6, 0.5, 0.2))
    c += bx(B_, "main", (0, 18.6, 4.4), (8.0, 10.0, 3.2), taper=(0.95, 0.95))
    c += bx(B_, "acc", (0, 23.4, 4.4), (7.0, 0.8, 2.8))
    c += bx(B_, "acc", (0, 13.8, 4.4), (7.0, 0.8, 2.8))
    for s_ in (-1, 1):
        c += bx(B_, "acc", (s_ * 3.9, 18.6, 4.4), (0.6, 9.0, 3.0))
    c += bx(B_, "gem", (2.2, 21.6, 6.05), (0.8, 0.8, 0.2))
    c += bx(B_, "acc", (-1.2, 18.0, 6.05), (3.0, 3.6, 0.3))
    for s in (-1, 1):
        c += crv(B_, "cord", [(s * 3.0, 22.0, 4.0), (s * 4.8, 21.0, 0.0), (s * 2.6, 19.6, -3.4)], 0.45, 0.45, sides=5)
    c += band(B_, "acc", 0, 12.8, 10.4, 1.0, 6.2)
    for s in (-1, 1):
        ab = ARM[s]
        c += sleeve(ab, s, "main", w=5.2, d=5.3)
        c += bx(ab, "acc", (s * 6.0, 17.8, 0), (5.5, 0.8, 5.6))
        c += bx(ab, "acc", (s * 6.0, 13.6, 0), (5.4, 1.6, 5.5))
    c += bx(ARM[1], "edge", (8.75, 21.4, 0), (0.2, 1.8, 2.8))
    lg = p["leggings"]
    for s in (-1, 1):
        L = LEG[s]
        cx = s * 2.15
        lg += bx(L, "main", (cx, 8.4, 0), (5.2, 7.2, 5.4), taper=(0.98, 0.98), skip=("u",))
        lg += bx(L, "acc", (cx, 6.6, -2.8), (3.8, 1.8, 0.6))
        lg += bx(L, "acc", (s * 4.7, 9.6, 0), (0.4, 1.6, 2.6))
    bt = p["boots"]
    for s in (-1, 1):
        cx = s * 2.15
        bt += bx(BOOT[s], "acc", (cx, 2.6, -0.2), (5.4, 4.4, 5.6), taper=(0.95, 0.95))
        bt += bx(BOOT[s], "main", (cx, 5.0, -0.1), (5.6, 0.8, 5.8))
        bt += bx(BOOT[s], "dark", (cx, 0.4, -0.4), (5.8, 0.8, 6.6), skip=("u",))
    return p


SKINS = [("samurai", "侍（紅糸威の大鎧）", samurai), ("pirate", "海賊の船長", pirate), ("knight", "白銀の騎士", knight), ("astronaut", "宇宙飛行士", astronaut)]


def built(key, fn):
    out = {}
    for k, v in fn().items():
        faces = cull_inside([f for f in v if f is not None])
        if not faces:
            continue
        lpkit.TEX = 2.0
        size, P, N, M, L = build_atlas(faces, 32, 0)
        atlas, glow = tpaint.paint(faces, size, P, N, M, L, key)
        out[k] = dict(faces=faces, size=size, atlas=atlas, glow=glow, tris=tri_count(faces))
    return out


def main():
    aview.SS = 2
    OUT.mkdir(parents=True, exist_ok=True)
    names = sys.argv[1:] or [k for k, _, _ in SKINS]
    rows = []
    for key, label, fn in SKINS:
        if key not in names:
            continue
        res = built(key, fn)
        im = build_one.strip(res, "whale", label)
        im.save(OUT / f"{key}.png")
        rows.append(im)
        print(key, aview.set_tris(res), flush=True)
    S = Image.new("RGB", (rows[0].width, sum(r.height for r in rows)))
    y = 0
    for r in rows:
        S.paste(r, (0, y))
        y += r.height
    S.save(OUT / "skins_1.png")
    print("wrote", OUT / "skins_1.png")


if __name__ == "__main__":
    main()
