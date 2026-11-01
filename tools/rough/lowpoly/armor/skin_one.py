"""スキン (docs/design/54 の 6.1。物語と関係のない見た目だけの一式) の一着一案。最初の組 = 侍・海賊の船長・白銀の騎士・宇宙飛行士。二組目 = 忍び・魔女・料理人・吸血鬼の貴族。
作りは旅装 (travel_one) と同じ: 部位 helmet / chestplate / leggings / boots、骨は armor*、塗りは tpaint (材質 → 色と質感の表をこのファイルで足す)。
色違いにしない: 一着ずつ形が違う。顔を出すもの (侍・海賊) は頭の前を開け、騎士の兜と宇宙の兜は顔を覆う。
実行: python skin_one.py [組|名前 ...]     -> docs/review/skins/<名前>.png (前・横・後ろ・3/4) と docs/review/skins/skins_<組>.png (並べたもの。名前を並べたときは skins_pick.png)
      python skin_one.py install [名前 ...]  -> docs/review/skins/raw/<名前>/ (tools/install/install_skins.py の入力) と衣装棚の絵
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
    NINJA=A_([(14, 16, 26), (22, 26, 40), (32, 38, 56), (46, 54, 74), (62, 72, 94)]),
    NINJA_L=A_([(40, 46, 64), (56, 64, 86), (74, 84, 108), (96, 106, 130)]),
    STEEL=A_([(56, 60, 68), (84, 90, 100), (118, 124, 134), (156, 162, 170), (196, 200, 206)]),
    WRAP=A_([(150, 150, 146), (182, 182, 176), (210, 210, 204), (232, 232, 226)]),
    WITCH=A_([(36, 16, 52), (54, 26, 76), (74, 38, 102), (98, 54, 130), (126, 76, 158)]),
    NIGHT=A_([(10, 10, 14), (18, 18, 24), (28, 28, 36), (40, 40, 50), (56, 56, 68)]),
    CHEF=A_([(186, 188, 190), (212, 214, 216), (232, 234, 234), (246, 246, 246), (255, 255, 255)]),
    CHECK=A_([(26, 26, 30), (60, 60, 64), (170, 170, 172), (220, 220, 222)]),
    APRON=A_([(26, 40, 66), (36, 54, 88), (50, 72, 112), (68, 94, 138)]),
    BLOOD=A_([(70, 6, 14), (104, 12, 22), (138, 20, 30), (170, 34, 42), (200, 56, 60)]),
    VELVET=A_([(12, 8, 14), (20, 14, 24), (32, 24, 36), (46, 36, 52), (64, 52, 70)]),
    G_GREEN=A_([(60, 170, 70), (100, 220, 110), (170, 250, 160), (230, 255, 220)]),
    G_RED=A_([(170, 20, 30), (220, 40, 50), (255, 100, 100), (255, 190, 190)]),
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
    "ninja": dict(main=("NINJA", 0.50, "wool"), sec=("NINJA_L", 0.50, "wool"), cloth=("RED_SILK", 0.50, "wool"), metal=("STEEL", 0.60, "metal"),
                  cord=("WRAP", 0.55, "rope"), acc=("LACQUER", 0.45, "leather"), edge=("STEEL", 0.70, "metal"), glow="G_CYAN"),
    "witch": dict(main=("WITCH", 0.50, "wool"), sec=("NIGHT", 0.50, "wool"), metal=("GOLD", 0.62, "metal"), cloth=("NIGHT", 0.45, "leather"),
                  cord=("ROPE", 0.52, "rope"), edge=("WITCH", 0.65, "wool"), acc=("LEATHER", 0.50, "leather"), glow="G_GREEN"),
    "chef": dict(main=("CHEF", 0.62, "wool"), sec=("CHECK", 0.50, "straw"), cloth=("APRON", 0.50, "wool"), edge=("RED_SILK", 0.55, "wool"),
                 cord=("APRON", 0.60, "rope"), acc=("STEEL", 0.62, "metal"), metal=("NIGHT", 0.50, "metal"), fur=("BLACK_LEATHER", 0.50, "leather"), glow="G_CYAN"),
    "vampire": dict(main=("VELVET", 0.50, "wool"), sec=("BLOOD", 0.50, "wool"), edge=("LINEN", 0.66, "wool"), metal=("SILVER", 0.62, "metal"),
                    cloth=("BLACK_LEATHER", 0.52, "leather"), acc=("BLOOD", 0.40, "leather"), cord=("VELVET", 0.60, "rope"), glow="G_RED"),
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


# ================================================================== 忍び
def ninja():
    p = empty()
    h = p["helmet"]
    # 頭巾: 頭を包む藍の布。目のまわり (y 26.6〜29.4 の前) だけ開け、下は口元を覆う。額に鉄の鉢金、首の後ろに結び目
    rings = [rr(24.0, 4.6, -4.6, 4.6, cut=1.4), rr(28.0, 4.7, -4.7, 4.7, cut=1.6), rr(32.2, 4.6, -4.6, 4.6, cut=1.8), rr(33.2, 3.2, -3.2, 3.2, cut=1.2)]
    h += hide_front(shell(H_, "main", rings, caps=(False, "main")), 29.4, zcos=0.55)
    h += bx(H_, "main", (0, 25.4, -4.75), (9.0, 2.8, 0.5))
    h += bx(H_, "metal", (0, 30.2, -4.85), (7.6, 1.4, 0.4))
    h += bx(H_, "edge", (0, 30.2, -5.1), (1.4, 0.9, 0.2))
    h += spk(H_, "main", (0, 29.0, 4.9), (0, 0, 1), 0.8, 0.9, sides=6, rings=1)
    for s_ in (-1, 1):
        h += crv(H_, "main", [(s_ * 0.5, 29.0, 5.4), (s_ * 1.4, 27.4, 6.6), (s_ * 1.0, 25.6, 7.2)], 0.45, 0.3, sides=4)
    c = p["chestplate"]
    # 胴着 (藍) と打ち合わせの襟 (明るい藍)、紅の帯と結び、背に斜めの刀、腰に手裏剣、前腕の手甲
    c += bx(B_, "main", (0, 18.0, 0), (9.2, 12.0, 5.0), skip=("u", "b"))
    for s_ in (-1, 1):
        c += bx(B_, "sec", (s_ * 1.4, 21.0, -2.75), (1.4, 6.0, 0.4), rot=rot_z(s_ * -28))
    c += band(B_, "cloth", 0, 13.8, 9.8, 1.6, 5.6)
    c += bx(B_, "cloth", (2.6, 13.8, -3.0), (1.6, 1.4, 0.6))
    c += crv(B_, "cloth", [(2.6, 13.4, -3.2), (3.2, 11.2, -3.4), (3.0, 9.8, -3.2)], 0.4, 0.3, sides=4)
    c += crv(B_, "acc", [(-4.4, 23.6, 3.4), (4.6, 11.6, 3.4)], 0.55, 0.55, sides=4)
    c += crv(B_, "metal", [(-4.4, 23.6, 3.4), (-5.8, 25.6, 3.4)], 0.4, 0.4, sides=4)
    c += crv(B_, "cord", [(-3.8, 24.0, 2.8), (4.2, 23.6, -2.8)], 0.22, 0.22, sides=3)
    for i in range(4):
        a = math.radians(i * 90 + 45)
        c += spk(B_, "metal", (-3.0, 14.0, -3.3), (math.cos(a), math.sin(a), 0), 1.0, 0.25, sides=3, rings=1)
    # 紅の襟巻きの長い尾 (首の後ろから背へ流れる)
    c += crv(B_, "cloth", [(0.8, 24.2, 3.0), (1.8, 21.0, 4.4), (3.0, 17.0, 5.4), (4.4, 13.6, 6.2)], 0.7, 0.45, sides=4, hint=(0, 0, 1))
    c += crv(B_, "cloth", [(-0.4, 24.2, 3.0), (0.2, 20.4, 4.6), (1.2, 16.0, 5.8)], 0.6, 0.4, sides=4, hint=(0, 0, 1))
    for s_ in (-1, 1):
        ab = ARM[s_]
        c += sleeve(ab, s_, "main", w=4.7, d=4.7)
        c += bx(ab, "metal", (s_ * 6.0, 15.0, 0), (5.0, 3.0, 5.0), taper=(1.04, 1.04))
        c += bx(ab, "cord", (s_ * 6.0, 17.0, 0), (4.9, 0.5, 4.9))
    lg = p["leggings"]
    # 細い袴
    for s_ in (-1, 1):
        L = LEG[s_]
        cx = s_ * 2.15
        lg += bx(L, "main", (cx, 8.6, 0), (4.7, 6.6, 4.9), taper=(1.03, 1.0), skip=("u",))
        lg += bx(L, "sec", (cx, 6.0, 0), (4.8, 1.0, 5.0))
    bt = p["boots"]
    # 脚絆 (白い布を巻く) と足袋 (つま先が割れる)
    for s_ in (-1, 1):
        cx = s_ * 2.15
        bt += bx(BOOT[s_], "cord", (cx, 3.6, 0), (4.8, 3.4, 4.9))
        for k in range(3):
            bt += bx(BOOT[s_], "sec", (cx, 2.4 + k * 1.1, 0), (4.9, 0.3, 5.0), rot=rot_z(8 if k % 2 else -8))
        bt += bx(BOOT[s_], "main", (cx, 1.0, -0.2), (4.8, 2.0, 5.2))
        bt += bx(BOOT[s_], "dark", (cx, 1.0, -2.85), (0.3, 1.8, 0.4))
        bt += bx(BOOT[s_], "dark", (cx, 0.15, -0.2), (5.0, 0.3, 5.6), skip=("u",))
    return p


# ================================================================== 魔女
def witch():
    p = empty()
    h = p["helmet"]
    # 大きなとんがり帽子: 広い鍔、背の高い円錐が先で後ろへ折れる。金の留め金の帯と、先に小さな金の星
    h += shell(H_, "main", [rr(29.6, 8.0, -8.0, 8.0, cut=3.2), rr(30.2, 8.0, -8.0, 8.0, cut=3.2)], caps=("main", "main"))
    h += shell(H_, "main", [rr(30.1, 4.8, -4.8, 4.8, cut=1.8), rr(33.6, 3.6, -3.6, 3.6, cut=1.4), rr(36.4, 2.4, -2.2, 2.6, cut=1.0),
                            rr(38.6, 1.4, -0.8, 2.2, cut=0.6)], caps=(False, False))
    h += crv(H_, "main", [(0, 38.6, 0.7), (0, 40.0, 2.4), (0, 39.6, 4.6), (0, 38.2, 5.6)], 1.1, 0.25, sides=5)
    h += shell(H_, "sec", [rr(30.3, 4.9, -4.9, 4.9, cut=1.8), rr(31.5, 4.6, -4.6, 4.6, cut=1.7)], caps=(False, False))
    h += bx(H_, "metal", (0, 30.9, -4.95), (1.8, 1.4, 0.4))
    h += bx(H_, "sec", (0, 30.9, -5.15), (0.8, 0.6, 0.1))
    h += spk(H_, "metal", (0, 38.0, 5.8), (0, -1, 0.2), 1.2, 0.5, sides=5, rings=1)
    c = p["chestplate"]
    # 紫の長衣と黒い高い襟の外套、帯に光る薬瓶三本、首に金の星の飾り
    c += bx(B_, "main", (0, 18.0, 0), (9.4, 12.0, 5.2), skip=("u", "b"))
    c += bx(B_, "sec", (0, 23.0, 0.8), (10.4, 2.6, 6.0), taper=(0.9, 0.95))
    c += bx(B_, "sec", (0, 25.6, 2.6), (8.8, 3.0, 0.8), rot=rot_x(-14))
    for s_ in (-1, 1):
        c += bx(B_, "sec", (s_ * 4.4, 25.2, 0.6), (0.8, 2.6, 4.0), rot=rot_z(s_ * 16))
    c += bx(B_, "sec", (0, 16.8, 3.1), (9.8, 13.4, 0.8), taper=(1.0, 1.08))
    c += band(B_, "acc", 0, 14.0, 9.8, 1.2, 5.6)
    c += bx(B_, "metal", (0, 14.0, -2.95), (1.4, 1.4, 0.4))
    for x in (-3.4, -2.0, 2.6):
        c += bx(B_, "gem", (x, 12.6, -3.0), (1.0, 1.6, 1.0))
        c += bx(B_, "acc", (x, 13.6, -3.0), (0.6, 0.5, 0.6))
    c += crv(B_, "metal", [(-2.0, 24.2, -2.8), (0, 22.6, -3.1), (2.0, 24.2, -2.8)], 0.15, 0.15, sides=3)
    c += spk(B_, "metal", (0, 22.2, -3.3), (0, -1, -0.2), 1.0, 0.45, sides=5, rings=1)
    for s_ in (-1, 1):
        ab = ARM[s_]
        c += sleeve(ab, s_, "main", w=4.8, d=4.9)
        # 広がる袖口 (手首で大きく開く)
        c += bx(ab, "main", (s_ * 6.0, 13.0, 0), (6.2, 2.6, 6.4), taper=(0.8, 0.8))
        c += bx(ab, "edge", (s_ * 6.0, 11.7, 0), (6.4, 0.4, 6.6))
    lg = p["leggings"]
    # 長い裾 (前と後ろと横。くるぶしまで)
    for s_ in (-1, 1):
        L = LEG[s_]
        cx = s_ * 2.15
        lg += bx(L, "main", (cx, 7.6, 0), (4.9, 9.0, 5.2), taper=(1.0, 1.08), skip=("u",))
        lg += bx(L, "sec", (cx, 7.0, 3.0), (5.0, 9.6, 0.6))
        lg += bx(L, "edge", (cx, 3.2, 0), (5.4, 0.5, 5.8))
    bt = p["boots"]
    # とがって反り返るつま先の靴
    for s_ in (-1, 1):
        cx = s_ * 2.15
        bt += bx(BOOT[s_], "cloth", (cx, 1.6, 0), (4.8, 3.2, 5.0))
        bt += crv(BOOT[s_], "cloth", [(cx, 0.8, -2.4), (cx, 0.8, -4.2), (cx, 1.8, -5.0)], 0.9, 0.2, sides=4)
        bt += bx(BOOT[s_], "metal", (cx, 2.0, -2.6), (1.0, 0.8, 0.3))
    return p


# ================================================================== 料理人
def chef():
    p = empty()
    h = p["helmet"]
    # 背の高い白いコック帽 (上がふくらみ、縦のひだ) と帯
    h += shell(H_, "main", [rr(29.8, 4.7, -4.7, 4.7, cut=1.6), rr(32.0, 4.6, -4.6, 4.6, cut=1.6)], caps=(False, False))
    h += shell(H_, "main", [rr(32.0, 4.4, -4.4, 4.4, cut=1.6), rr(34.0, 5.4, -5.4, 5.4, cut=2.2), rr(36.6, 5.8, -5.8, 5.8, cut=2.6),
                            rr(38.4, 5.0, -5.0, 5.0, cut=2.2), rr(39.2, 3.0, -3.0, 3.0, cut=1.4)], caps=(False, "main"))
    for i in range(6):
        a = math.radians(i * 60)
        h += bx(H_, "main", (math.cos(a) * 5.4, 36.0, math.sin(a) * 5.4), (0.6, 4.0, 0.6), rot=rot_y(-i * 60))
    c = p["chestplate"]
    # 白い両前の上着 (黒いボタン二列)、赤い首巻き、紺の前掛け (腰で紐を結び、ポケットにお玉)
    c += bx(B_, "main", (0, 18.0, 0), (9.6, 12.0, 5.4), skip=("u", "b"))
    c += bx(B_, "main", (1.0, 18.6, -2.85), (6.2, 10.4, 0.4))
    for k in range(4):
        for x in (-1.2, 3.0):
            c += bx(B_, "metal", (x, 22.0 - k * 2.0, -3.15), (0.6, 0.6, 0.3))
    c += bx(B_, "edge", (0, 23.6, -0.4), (9.4, 1.0, 5.4))
    c += spk(B_, "edge", (-1.0, 23.2, -3.0), (0.2, -1, -0.1), 1.6, 0.6, sides=4, rings=1)
    c += bx(B_, "cloth", (0, 14.6, -2.95), (8.4, 4.4, 0.4))
    c += band(B_, "cord", 0, 15.4, 9.8, 0.5, 5.6)
    c += crv(B_, "cord", [(0, 15.4, 3.0), (1.0, 14.2, 3.4), (0.6, 12.8, 3.4)], 0.25, 0.2, sides=3)
    c += crv(B_, "cord", [(0, 15.4, 3.0), (-1.0, 14.2, 3.4), (-0.6, 12.8, 3.4)], 0.25, 0.2, sides=3)
    c += bx(B_, "cloth", (2.4, 13.6, -3.3), (2.6, 2.0, 0.4))
    c += crv(B_, "acc", [(2.8, 13.8, -3.6), (3.0, 17.0, -3.7)], 0.2, 0.2, sides=3)
    c += spk(B_, "acc", (3.0, 17.4, -3.7), (0, 0, -1), 0.6, 0.7, sides=6, rings=1)
    for s_ in (-1, 1):
        ab = ARM[s_]
        c += sleeve(ab, s_, "main", w=4.9, d=4.9)
        c += bx(ab, "main", (s_ * 6.0, 16.6, 0), (5.3, 1.6, 5.3))
    lg = p["leggings"]
    # 千鳥格子のズボンと、前掛けの裾 (ひざ上まで)
    for s_ in (-1, 1):
        L = LEG[s_]
        cx = s_ * 2.15
        lg += bx(L, "sec", (cx, 8.6, 0), (4.6, 6.6, 4.8), skip=("u",))
        lg += bx(L, "cloth", (cx, 9.6, -2.75), (4.6, 4.8, 0.4))
    bt = p["boots"]
    # 黒い木靴 (丸いつま先)
    for s_ in (-1, 1):
        cx = s_ * 2.15
        bt += bx(BOOT[s_], "sec", (cx, 3.4, 0), (4.6, 2.8, 4.8), skip=("u",))
        bt += bx(BOOT[s_], "fur", (cx, 1.0, -0.4), (5.0, 2.0, 5.8), taper=(0.92, 0.92))
        bt += bx(BOOT[s_], "fur", (cx, 0.9, -3.0), (4.4, 1.6, 1.4), taper=(0.7, 0.7))
    return p


# ================================================================== 吸血鬼の貴族
def vampire():
    import cosmetic_one as CO
    p = empty()
    h = p["helmet"]
    # 細い黒の山高帽 (血の色の帯)
    h += shell(H_, "main", [rr(31.4, 6.4, -6.4, 6.4, cut=2.6), rr(31.9, 6.4, -6.4, 6.4, cut=2.6)], caps=("main", "main"))
    h += shell(H_, "main", [rr(31.8, 3.8, -3.8, 3.8, cut=1.4), rr(37.0, 4.1, -4.1, 4.1, cut=1.5)], caps=(False, "main"))
    h += shell(H_, "sec", [rr(32.0, 3.9, -3.9, 3.9, cut=1.4), rr(33.0, 3.95, -3.95, 3.95, cut=1.45)], caps=(False, False))
    c = p["chestplate"]
    # 黒の燕尾の上着、血の色の胴着と銀のボタン、白い襟飾りに紅玉の留め
    c += bx(B_, "sec", (0, 18.0, 0), (8.8, 12.0, 4.8), skip=("u", "b"))
    c += bx(B_, "main", (0, 18.0, 0.6), (9.8, 12.2, 4.6), skip=("-z",))
    for s_ in (-1, 1):
        c += bx(B_, "main", (s_ * 3.7, 18.0, -2.45), (2.4, 12.2, 1.0), skip=("+z",))
        c += bx(B_, "main", (s_ * 2.6, 21.6, -3.0), (1.2, 5.4, 0.4), rot=rot_z(s_ * -16))
    for k in range(3):
        c += bx(B_, "metal", (0, 19.6 - k * 1.8, -2.65), (0.6, 0.6, 0.3))
    for k in range(3):
        c += bx(B_, "edge", (0, 23.4 - k * 0.8, -2.6 - k * 0.15), (2.0 - k * 0.3, 0.8, 0.8))
    c += spk(B_, "gem", (0, 22.6, -3.4), (0, 0, -1), 0.6, 0.55, sides=6, rings=1)
    # 外套: 肩から足首まで (外は黒・内は血の色)、背の高い襟が頭の後ろで扇に開く
    rows = [(24.4, 2.9, 5.0, 0.4, None), (18.0, 3.4, 5.6, 0.9, None), (10.0, 4.0, 6.2, 1.3, None), (3.0, 4.6, 6.6, 1.6, [1.2, 0, -0.6, 0, 0.6, 0, -0.6, 0, 1.2])]
    c += CO.cloak("main", rows, th=0.45)
    c += CO.cloak("sec", [(y, z - 0.25, hw - 0.2, b, dy) for (y, z, hw, b, dy) in rows], th=0.2)
    c += shell(B_, "main", [CO.slab_ring(24.6, 2.6, 5.4, 1.0, 0.4, 9), CO.slab_ring(29.0, 3.6, 6.6, 2.2, 0.4, 9), CO.slab_ring(31.2, 4.4, 7.0, 2.6, 0.4, 9)],
               caps=(True, True))
    c += shell(B_, "sec", [CO.slab_ring(24.6, 2.35, 5.2, 1.0, 0.2, 9), CO.slab_ring(29.0, 3.35, 6.4, 2.2, 0.2, 9), CO.slab_ring(31.0, 4.15, 6.8, 2.6, 0.2, 9)],
               caps=(True, True))
    for s_ in (-1, 1):
        ab = ARM[s_]
        c += sleeve(ab, s_, "main")
        c += bx(ab, "edge", (s_ * 6.0, 13.2, 0), (5.0, 1.0, 5.0))
    lg = p["leggings"]
    # 黒のズボンと燕尾の尾 (後ろでひざの下まで)
    for s_ in (-1, 1):
        L = LEG[s_]
        cx = s_ * 2.15
        lg += bx(L, "main", (cx, 8.6, 0), (4.6, 6.6, 4.8), skip=("u",))
        lg += bx(L, "main", (cx, 8.0, 2.7), (4.4, 8.0, 0.5), taper=(1.0, 0.8))
    bt = p["boots"]
    # 磨いた黒の長靴 (銀のかかと)
    for s_ in (-1, 1):
        cx = s_ * 2.15
        bt += bx(BOOT[s_], "cloth", (cx, 3.0, 0), (4.8, 6.0, 5.0), skip=("u",))
        bt += bx(BOOT[s_], "cloth", (cx, 0.8, -2.8), (4.2, 1.6, 1.4), taper=(0.75, 0.75))
        bt += bx(BOOT[s_], "metal", (cx, 0.4, 2.3), (4.0, 0.8, 0.8))
    return p


SKINS = [("samurai", "侍（紅糸威の大鎧）", samurai), ("pirate", "海賊の船長", pirate), ("knight", "白銀の騎士", knight), ("astronaut", "宇宙飛行士", astronaut),
         ("ninja", "忍び", ninja), ("witch", "魔女", witch), ("chef", "料理人", chef), ("vampire", "吸血鬼の貴族", vampire)]
GROUPS = {"1": ["samurai", "pirate", "knight", "astronaut"], "2": ["ninja", "witch", "chef", "vampire"]}


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


def install(names):
    """メッシュとアトラス (raw) と、衣装棚の絵 (前の 3/4 の全身、128x192。背景は透明)"""
    aview.SS = 2
    gui = ROOT / "src/main/resources/assets/skyabyss/textures/gui/skins"
    gui.mkdir(parents=True, exist_ok=True)
    for key, label, fn in SKINS:
        if names and key not in names:
            continue
        res = built(key, fn)
        aview.save_set(res, OUT / "raw" / key, "skin_" + key)
        im = aview.render(res, "whale", 200, 8, (256, 384), 9.6, center=(0, 17.0, 0), mann=True, bg=(0, 0, 0))
        a = np.array(im.convert("RGBA"))
        a[..., 3] = np.where((a[..., :3] == 0).all(-1), 0, 255)
        Image.fromarray(a, "RGBA").resize((128, 192), Image.LANCZOS).save(gui / f"{key}.png")
        print(key, "raw + wardrobe picture", flush=True)


def main():
    if sys.argv[1:2] == ["install"]:
        install(set(sys.argv[2:]))
        return
    aview.SS = 2
    OUT.mkdir(parents=True, exist_ok=True)
    args = sys.argv[1:]
    group = args[0] if len(args) == 1 and args[0] in GROUPS else None
    names = GROUPS[group] if group else args or [k for k, _, _ in SKINS]
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
    out = OUT / (f"skins_{group}.png" if group else "skins_pick.png")
    S.save(out)
    print("wrote", out)


if __name__ == "__main__":
    main()
