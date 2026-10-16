"""余燼のゲア (戦斧・鍛え手の大鎚・灰被りの火掻き) と、護符・灯・角杯・心核・欠片・粉・殻のラフ 3案ずつを作る。

使い方:
    python tools/rough/ember_gear.py

出力 (docs/review/ember_gear_roughs/):
    compare_<item>.png    案ごとの比較 (3D の品だけ。アイコン・3D の正面と斜め)
    compare_icons.png     アイコンだけの品 (8 品) の比較
    <item>/<a|b|c>/icon.png      32x32 のアイコン (武器は斜め、刃先が右上)
    <item>/<a|b|c>/upright.png   3D の元になる立てた向きの 32x32 (確認用)
    <item>/<a|b|c>/voxels.npz    3D の彫刻ボクセル (3D の品だけ)
    <item>/<a|b|c>/front.png, hand.png   3D の確認画像

作り方は tools/rough/tide_gear.py と同じ (層で描く → 32px に縮めて影と輪郭 → 3D は 1 画素 = 3 ボクセルで
丸い断面と盛り上がり)。鍛え手の大鎚と灰被りの火掻きは、中ボスと雑魚の既存のモデル
(tools/rough/ember_smith.py の hammer、tools/rough/ember_mobs_thrall.py の weapon_poker) と同じ形・色にする。
色は ember_mobs_common.EMBER_RAMP と ember_smith.py の RAMP の値。
"""
from __future__ import annotations

import math
import sys
from pathlib import Path

import numpy as np
from PIL import Image

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
import tide_gear as tg  # noqa: E402
from tide_gear import (  # noqa: E402
    Pic, arc, compose, line, poly, rect, ell, taper, voxelize_hi, preview_3d, ROT_DEG,
)
from voxel import icon_big, sheet  # noqa: E402

ROOT = HERE.parents[1]
OUT_DIR = ROOT / "docs/review/ember_gear_roughs"

# 余燼の色 (EMBER_RAMP / RAMP の値を RGB にした。3 段目の明るさを主に使う)
EMBER_PAL = {
    "basalt": (52, 48, 58), "basalt_lite": (76, 70, 84), "basalt_deep": (30, 27, 34),
    "ash": (130, 124, 118), "ash_lite": (171, 165, 157), "ash_cloth": (110, 104, 98),
    "cloth_deep": (72, 68, 64),
    "ember_red": (200, 58, 20), "ember_orange": (240, 122, 30), "deepred": (122, 36, 12),
    "molten": (255, 200, 80), "molten_hot": (255, 244, 200),
    "iron": (92, 94, 99), "iron_lite": (160, 162, 168),
    "bronze": (150, 104, 56), "leather": (92, 58, 38),
    "wood": (90, 58, 36), "haft": (122, 82, 48),
    "bone": (214, 200, 176), "rust": (126, 62, 30),
    "slag": (88, 66, 50), "slag_dk": (46, 34, 26),
    "crust": (112, 106, 100),
}
tg.PAL.update(EMBER_PAL)  # tide_gear の compose / voxelize_hi が名前で色を引けるように


# ---------------------------------------------------------------- 武器 (立てた向きで描く。刃先は上)

def axe(opt):
    """余燼の戦斧 (レア): 玄武岩の刃 (上の右)、左の小さな刺し、焼けた木の柄。刃の縁に火の線。"""
    p = Pic()
    haft = {"a": "wood", "b": "bone", "c": "haft"}[opt]
    d = p.layer(haft); rect(d, 15, 9, 17.5, 31)
    d = p.layer("leather", bump=1); rect(d, 14.5, 21, 18, 24)            # 握りの革
    d = p.layer("basalt_lite", bump=1)
    poly(d, [(16.5, 0.2), (23.5, -0.2), (28.5, 3.6), (28.5, 11.8), (22, 14), (16.5, 11)])   # 刃 (大きめ)
    poly(d, [(15.5, 3), (9.5, 4.2), (10.5, 9.8), (15.5, 9)])               # 左の刺し
    d = p.layer("ash_lite"); line(d, [(23.6, 0.8), (27.2, 3.8), (27.4, 11)], 0.6)  # 刃先の明るい縁
    if opt == "a":   # 刃先に沿う、途切れない熾火のひび (ぎざぎざ)
        seam = [(24.8, 2.6), (23.4, 4.2), (25.2, 5.6), (23.6, 7.4), (25.6, 9.0), (23.8, 10.6), (22.2, 12.2)]
        d = p.layer("ember_orange", glow=True); line(d, seam, 0.7)
        d = p.layer("molten", glow=True); line(d, seam[2:5], 0.4)
    elif opt == "b":
        d = p.layer("molten", glow=True); line(d, [(26.4, 4.5), (25.8, 9.8)], 0.7)
    else:
        d = p.layer("iron", bump=1); rect(d, 14.5, 9, 18, 11.2)            # 鉄の箍
        d = p.layer("ember_orange", glow=True)
        line(d, [(24.6, 1.8), (22.8, 3.6), (25.0, 5.2), (23.2, 7.0), (25.4, 8.6)], 0.6)
    return p


def smith_hammer(opt):
    """鍛え手の大鎚 (伝説・両手): 中ボスの大鎚と同じ。横の鉄の頭 (打つ面は焼けた橙)、長い柄、革の握り。
    柄は頭の幅の約 1.7 倍の長さ (中ボスの柄と同じ比)。"""
    p = Pic()
    d = p.layer("haft"); rect(d, 15, 9, 17.5, 31)
    d = p.layer("leather", bump=1); rect(d, 14.5, 21, 18, 23.5); rect(d, 14.5, 25.5, 18, 27)
    d = p.layer("iron", bump=1); rect(d, 6.5, 2.5, 25, 9.5)               # 頭
    if opt == "a":
        d = p.layer("molten", glow=True); rect(d, 6.5, 3, 8.8, 9)         # 打つ面 (前)
        d = p.layer("rust"); rect(d, 12, 2.2, 13.2, 9.8); rect(d, 19.5, 2.2, 20.7, 9.8)
        d = p.layer("bronze", bump=1); rect(d, 14.5, 8.5, 18, 10.2)       # 柄の箍
    elif opt == "b":
        d = p.layer("slag", bump=1); rect(d, 6.5, 2.5, 25, 9.5)
        d = p.layer("ember_orange", glow=True); line(d, [(9.5, 4), (13.5, 6), (20, 4)], 0.4)
        line(d, [(15, 8.6), (23, 7.5)], 0.4)
        d = p.layer("molten", glow=True); rect(d, 22.6, 3, 25, 9)         # 打つ面 (後)
        d = p.layer("bronze", bump=1); rect(d, 14.5, 8.5, 18, 10)
    else:
        d = p.layer("ember_orange", glow=True); rect(d, 22.2, 3, 25, 9)  # 打つ面 (熱)
        d = p.layer("iron_lite"); line(d, [(8, 3.6), (20, 3.6)], 0.5)
        d = p.layer("bronze", bump=1); rect(d, 14.5, 8.5, 18, 10.2); rect(d, 19, 2.5, 20, 9.5)
    return p


def thrall_poker(opt):
    """灰被りの火掻き (伝説): 細い鉄の棒、先は焼けて光る鉤。中ほどに青銅の輪、下に吊るす輪と革の握り。
    中の雑魚の火掻き棒 (weapon_poker) と同じ並び。"""
    p = Pic()
    d = p.layer("iron_lite"); rect(d, 15, 7, 17, 28)
    d = p.layer("bronze", bump=1); rect(d, 14.2, 11, 17.8, 12.2); rect(d, 14.2, 17, 17.8, 18.2)
    d = p.layer("leather", bump=1); rect(d, 14.5, 22.5, 17.5, 25.5)
    d = p.layer("iron"); line(d, arc(16, 29.2, 2.2, 0, 360, 24), 0.7)     # 吊るす輪
    if opt == "a":
        d = p.layer("ember_orange", glow=True); rect(d, 15, 2, 17, 7)
        line(d, [(16, 2.8), (20, 2.2), (21.2, 4.8)], 0.8)
    elif opt == "b":
        d = p.layer("ember_red", glow=True); rect(d, 15, 1.5, 17, 7.5)
        line(d, [(16, 1.8), (19.2, 1.2), (20.6, 3.4), (19.5, 5.2)], 0.6)
        d = p.layer("molten", glow=True); ell(d, 16, 1.6, 0.9)
    else:
        d = p.layer("bronze", bump=1); rect(d, 15, 6, 17, 7)
        d = p.layer("molten", glow=True); rect(d, 15, 2.5, 17, 6)
        line(d, [(16, 2.6), (19.5, 2.4)], 0.7)
    return p


# ---------------------------------------------------------------- アイコンだけ

def mantle(opt):
    """熾火の外套 (装飾): 灰色の外套、深いフード、裾に熾火の帯。"""
    p = Pic()
    cloth = {"a": "ash_cloth", "b": "basalt", "c": "ash"}[opt]
    # 外套の身: 首元 (襟) から裾へ広がり、裾は不揃い。右へ少し流れる
    body = [(11, 5.5), (21, 5.5), (24.5, 9), (26.5, 20), (27.5, 26.5), (22, 25), (18, 27.8),
            (13, 25), (7.5, 27.2), (4.5, 20), (7.5, 9)]
    d = p.layer(cloth, bump=1); poly(d, body)
    d = p.layer("cloth_deep")   # 襟から裾へ広がる折り目
    line(d, [(14, 9.5), (11, 18), (9.5, 24.5)], 0.6); line(d, [(16, 10), (16.5, 18), (17.5, 25.5)], 0.6)
    line(d, [(19, 9.5), (22.5, 18), (24, 24.5)], 0.6)
    d = p.layer("cloth_deep", bump=1); poly(d, [(9.5, 5.5), (22.5, 5.5), (21.5, 9.5), (10.5, 9.5)])  # 襟の陰
    d = p.layer("iron", bump=1); ell(d, 16, 7.6, 1.6)                            # 留め金 (首元)
    if opt == "c":
        d = p.layer("molten", glow=True); ell(d, 16, 7.6, 0.8)                    # 留め金の火
    # 裾の熾火: 不揃いの帯 (裾の線に沿う)
    hem = [(7, 26.6), (13, 24.6), (18, 27.2), (22, 24.6), (27, 26.2)]
    d = p.layer("ember_orange", glow=True); line(d, hem, 1.0)
    if opt == "b":
        d = p.layer("molten", glow=True)
        for x, y in [(10, 25.6), (16, 26.2), (23, 25.2)]:
            ell(d, x, y, 0.6)
    return p


def kindling_charm(opt):
    """熾りの護符 (護符): 鉄の紐の先に、生きた炭を抱えた小さな玄武岩の円盤。"""
    p = Pic()
    d = p.layer("iron"); line(d, [(11, 2.5), (16, 7.5), (21, 2.5)], 0.8)
    if opt == "a":
        d = p.layer("basalt", bump=1); ell(d, 16, 18, 9.5)
        d = p.layer("ash"); ell(d, 16, 18, 7)
        d = p.layer("basalt_deep"); ell(d, 16, 18, 5.2)
        d = p.layer("ember_orange", glow=True); ell(d, 16, 18, 2.8)
        d = p.layer("molten", glow=True); ell(d, 15.2, 17.2, 1.1)
    elif opt == "b":
        d = p.layer("iron", bump=1); poly(d, [(7, 9), (25, 9), (25, 27), (7, 27)])
        d = p.layer("basalt_deep"); poly(d, [(9.2, 11.2), (22.8, 11.2), (22.8, 24.8), (9.2, 24.8)])
        d = p.layer("molten", glow=True); ell(d, 16, 18, 3.6)
    else:
        d = p.layer("bronze", bump=1); poly(d, [(16, 7), (24, 18), (16, 29), (8, 18)])
        d = p.layer("basalt"); poly(d, [(16, 10), (21, 18), (16, 26), (11, 18)])
        d = p.layer("ember_orange", glow=True); ell(d, 16, 18, 2.4)
    return p


def lantern(opt):
    """余燼の灯 (伝説): 下げ輪のついた小さな灯。中に最後の火が一つ残る。"""
    p = Pic()
    d = p.layer("iron"); line(d, arc(16, 4.5, 3.6, 180, 360, 12), 0.8)  # 吊り手
    if opt == "a":   # 籠の灯
        d = p.layer("iron"); rect(d, 10, 8.5, 22, 25.5)
        d = p.layer("molten", glow=True); ell(d, 16, 17, 3.2, 4.4)
        d = p.layer("ember_red", glow=True); ell(d, 16, 19.5, 1.6, 1.4)
        d = p.layer("iron_lite"); line(d, [(12.5, 9.5), (12.5, 24.5)], 0.45); line(d, [(19.5, 9.5), (19.5, 24.5)], 0.45)
        d = p.layer("iron", bump=1); poly(d, [(9, 9), (23, 9), (16, 4)])
        rect(d, 9.5, 25, 22.5, 27.5)
    elif opt == "b":  # 丸い灯 (真鍮)
        d = p.layer("bronze", bump=1); ell(d, 16, 17, 8.5)
        d = p.layer("molten", glow=True); ell(d, 16, 17, 5.6)
        d = p.layer("ember_orange", glow=True); ell(d, 16, 18.5, 2.4, 2.6)
        d = p.layer("bronze", bump=1); rect(d, 12, 24.5, 20, 27)
    else:            # 玄武岩の灯
        d = p.layer("basalt", bump=1); poly(d, [(11, 8), (21, 8), (23, 25), (9, 25)])
        d = p.layer("ember_red", glow=True); poly(d, [(14, 11), (18, 11), (19.5, 22), (12.5, 22)])
        d = p.layer("molten", glow=True); ell(d, 16, 16.5, 1.8, 2.4)
        d = p.layer("iron"); rect(d, 9.5, 25, 22.5, 27)
    return p


def horn(opt):
    """熾火の角杯 (食べ物): 曲がった角の杯。口に熾火の酒。"""
    p = Pic()
    # 暗い角の身 (口は上の左、先は下の右へ曲がる)。口に金属の縁、熾火の酒、帯
    horn = {"a": "slag", "b": "basalt", "c": "leather"}[opt]
    rim = {"a": "iron", "b": "bronze", "c": "bronze"}[opt]
    d = p.layer(horn, bump=1); poly(d, taper([(8, 10), (11, 17), (17, 23), (24.5, 27.5)], 10.5, 2.4))
    d = p.layer("haft" if opt != "b" else "basalt_lite")   # 角の筋 (光の当たる側)
    line(d, [(10.5, 14), (16, 19.5)], 0.5); line(d, [(13.5, 20.5), (19.5, 24.5)], 0.5)
    d = p.layer("leather", bump=1); line(d, [(12.5, 16.2), (18.5, 20.8)], 0.8)   # 革の帯
    d = p.layer(rim, bump=1); line(d, [(5.2, 9.2), (11.8, 6.6)], 1.0)            # 金属の縁 (口)
    d = p.layer("ember_orange", glow=True); ell(d, 8.2, 8.2, 3.8, 1.8)           # 熾火の酒 (口の面)
    d = p.layer("molten", glow=True); ell(d, 8.0, 8.0, 2.2, 0.9)
    d = p.layer("molten", glow=True); ell(d, 10.2, 3.2, 0.6); ell(d, 12.4, 1.6, 0.5)   # 立つ火の粉
    return p


def core(opt):
    """余燼の心核: 石の枠 (tide_shell_core と同じ八角の枠) に熾火の結晶。"""
    p = Pic()
    frame = {"a": "basalt", "b": "iron", "c": "ash"}[opt]
    d = p.layer(frame)
    poly(d, [(11, 3.5), (21, 3.5), (27.5, 10), (27.5, 22), (21, 28.5), (11, 28.5), (4.5, 22), (4.5, 10)])
    d = p.layer("basalt_deep" if opt == "a" else ("slag_dk" if opt == "b" else "cloth_deep"))
    poly(d, [(11, 5.5), (21, 5.5), (25.5, 10), (25.5, 22), (21, 26.5), (11, 26.5), (6.5, 22), (6.5, 10)])
    d = p.layer(frame)
    for yy in (9, 15, 21):
        line(d, [(9, yy), (23, yy + 1)], 0.7)
    if opt == "a":
        d = p.layer("ember_red"); poly(d, [(16, 9), (21, 16), (16, 23), (11, 16)])
        d = p.layer("molten", glow=True); ell(d, 16, 16, 2.4)
    elif opt == "b":
        d = p.layer("ember_orange"); poly(d, [(16, 9), (21, 16), (16, 23), (11, 16)])
        d = p.layer("molten", glow=True); ell(d, 15, 14.5, 1.6)
    else:
        d = p.layer("molten", glow=True); poly(d, arc(16, 16, 7, 0, 300, 24))
        d = p.layer("ember_red"); ell(d, 16, 16, 2.5)
        d = p.layer("molten", glow=True); ell(d, 16, 16, 1.0)
    return p


def shard(opt):
    """余燼の欠片 (素材): 玄武岩の結晶の欠片。中に熾火の筋。"""
    p = Pic()
    d = p.layer("basalt_lite" if opt == "c" else "basalt", bump=1)
    poly(d, [(16, 2), (23, 12), (19, 28), (12, 27), (9, 12)])
    d = p.layer("ash"); line(d, [(12.5, 12), (16, 6)], 0.5)
    if opt == "a":
        d = p.layer("ember_orange", glow=True); line(d, [(16, 6), (16, 24)], 0.9)
    elif opt == "b":
        d = p.layer("ash_lite"); poly(d, [(16, 2), (23, 12), (16, 14), (9, 12)])
        d = p.layer("molten", glow=True); line(d, [(12, 14), (16, 22), (19.5, 15)], 0.6)
    else:
        d = p.layer("deepred"); poly(d, [(16, 7), (20, 14), (16, 22), (12, 14)])
        d = p.layer("molten", glow=True); ell(d, 16, 14, 1.5)
    return p


def spark(opt):
    """火の粉の粉 (素材): 灰の上に散る光る粉。"""
    p = Pic()
    if opt == "a":   # 粉の山: 暗い縁、橙の本体、黄の芯、浮いた火の粉
        d = p.layer("deepred", bump=1); ell(d, 16, 22.5, 9.5, 4.5)
        d = p.layer("ember_orange", glow=True); ell(d, 16, 21.5, 7.5, 3.4)
        d = p.layer("molten", glow=True); ell(d, 16, 20.6, 4.2, 2.0)
        d = p.layer("molten", glow=True)
        for x, y in [(8, 14), (23.5, 12.5), (27, 18), (5.5, 19.5)]:
            ell(d, x, y, 1.3)   # 1 画素だと輪郭線で暗くなるので 2 画素以上にする
    elif opt == "b":   # 青銅の皿に入った粉
        d = p.layer("bronze", bump=1); poly(d, arc(16, 20, 10, 0, 180, 18) + [(6, 20)])
        d = p.layer("ember_red", glow=True)
        for x, y in [(11, 18), (16, 16.5), (21, 18), (14, 20.5), (19, 21)]:
            ell(d, x, y, 1.1)
        d = p.layer("molten", glow=True); ell(d, 16, 16.5, 0.6)
    else:              # 散る火の粉 (大きく広がる)
        d = p.layer("molten", glow=True)
        for x, y in [(6, 14), (11, 10), (20, 8), (25, 12), (16, 14), (9, 21), (23, 19), (28, 17)]:
            ell(d, x, y, 1.0)
        d = p.layer("ember_orange", glow=True)
        for x, y in [(13, 6), (18, 4), (27, 22), (5, 25)]:
            ell(d, x, y, 0.7)
    return p


def husk(opt):
    """燠の殻 (素材): 丸まった玄武岩の殻の板 (cinder_roller の殻)。継ぎ目から熾火が覗く。"""
    p = Pic()
    col = {"a": ("basalt", "basalt_deep"), "b": ("slag", "slag_dk"), "c": ("crust", "basalt")}[opt]
    d = p.layer(col[0], bump=1); poly(d, arc(16, 22, 11.5, 180, 360, 22))  # 殻の半球
    d = p.layer(col[1]); poly(d, arc(16, 22, 8.5, 180, 360, 20))           # 内側 (丸まった陰)
    d = p.layer(col[0], bump=1)
    for x in (9.5, 13, 16.5, 20, 23.5):                                    # 殻の節
        line(d, [(x, 12.5), (x + 0.4, 21)], 0.6)
    d = p.layer("ember_orange", glow=True); line(d, arc(16, 22, 7.2, 200, 340, 16), 0.8)  # 熾火の継ぎ目
    if opt == "b":
        d = p.layer("molten", glow=True); ell(d, 11.5, 17, 0.9); ell(d, 21, 15, 0.8)
    if opt == "c":
        d = p.layer("rust"); line(d, [(8, 19), (24, 19)], 0.5)
    return p


# ---------------------------------------------------------------- 項目

# kind: weapon (立てて描き、-45 度に傾けて 3D) / icon (アイコンだけ)
# target: 立てたときの長さ (単位)。thick: 断面の中心の厚み (片側、3D ボクセル)
ITEMS = {
    "ember_war_axe": {"kind": "weapon", "draw": axe, "target": 22.0, "thick": 3, "name": "余燼の戦斧"},
    "smith_hammer": {"kind": "weapon", "draw": smith_hammer, "target": 30.0, "thick": 4, "name": "鍛え手の大鎚"},
    "thrall_poker": {"kind": "weapon", "draw": thrall_poker, "target": 22.0, "thick": 2, "name": "灰被りの火掻き"},
    "ember_mantle": {"kind": "icon", "draw": mantle, "name": "熾火の外套"},
    "kindling_charm": {"kind": "icon", "draw": kindling_charm, "name": "熾りの護符"},
    "ember_lantern": {"kind": "icon", "draw": lantern, "name": "余燼の灯"},
    "ember_horn": {"kind": "icon", "draw": horn, "name": "熾火の角杯"},
    "ember_core": {"kind": "icon", "draw": core, "name": "余燼の心核"},
    "ember_shard": {"kind": "icon", "draw": shard, "name": "余燼の欠片"},
    "spark_dust": {"kind": "icon", "draw": spark, "name": "火の粉の粉"},
    "cinder_husk": {"kind": "icon", "draw": husk, "name": "燠の殻"},
}
OPTS = ("a", "b", "c")


def build_option(item: str, opt: str):
    cfg = ITEMS[item]
    pic = cfg["draw"](opt)
    if cfg["kind"] == "weapon":
        icon, _, _ = compose(pic, ROT_DEG)
    else:
        icon, _, _ = compose(pic, 0.0)
    upright, _, _ = compose(pic, 0.0)
    grid = gl = None
    if cfg["kind"] == "weapon":
        grid, gl = voxelize_hi(pic, cfg["thick"], 0.0)
    return {"icon": icon, "upright": upright, "grid": grid, "glow": gl}


def write_option(item, opt, res, outdir: Path):
    outdir.mkdir(parents=True, exist_ok=True)
    cfg = ITEMS[item]
    Image.fromarray(res["icon"], "RGBA").save(outdir / "icon.png")
    Image.fromarray(res["upright"], "RGBA").save(outdir / "upright.png")
    if res["grid"] is not None:
        np.savez_compressed(outdir / "voxels.npz", grid=res["grid"], glow=res["glow"],
                            target=np.float64(cfg["target"]), tilt=np.float64(ROT_DEG))


def main():
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    results = {}
    for item, cfg in ITEMS.items():
        cols = []
        for opt in OPTS:
            res = build_option(item, opt)
            results[(item, opt)] = res
            write_option(item, opt, res, OUT_DIR / item / opt)
            ims = [icon_big(res["icon"], 8)]
            if res["grid"] is not None:
                front, hand = preview_3d(res["grid"], res["glow"], ROT_DEG)
                front.save(OUT_DIR / item / opt / "front.png")
                hand.save(OUT_DIR / item / opt / "hand.png")
                ims += [front, hand]
                print(item, opt, "voxels", int(res["grid"][..., 3].astype(bool).sum()))
            cols.append((f"{opt.upper()} 案", ims))
        if cfg["kind"] == "weapon":
            sheet(f"{cfg['name']} ({item}) ラフ 3 案", cols,
                  "上: 32px アイコン (刃先は右上) / 中: 3D 正面 / 下: 3D 斜め。1 画素 = 3 ボクセル、丸い断面と盛り上がり"
                  ).save(OUT_DIR / f"compare_{item}.png")
    rows = []
    for item, cfg in ITEMS.items():
        if cfg["kind"] != "icon":
            continue
        rows.append((cfg["name"], [icon_big(results[(item, opt)]["icon"], 6) for opt in OPTS]))
    cols = [(name, ims) for name, ims in rows]
    sheet("アイコンだけの品 3 案 (余燼)", cols, "32x32 / 6 倍表示。上から A / B / C。光る部分は明るい色").save(
        OUT_DIR / "compare_icons.png")


if __name__ == "__main__":
    main()
