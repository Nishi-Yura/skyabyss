"""封の箱 10 種 (docs/design/52 の 2 章)。一品一案: 巨神の箱 4 (岩鯨・潮殻・雷翼・余燼)、階級の箱 4 (アンコモン・レア・エピック・ヒロイック)、淵の金の箱、
塔の箱 (あいだの塔の報酬。docs/design/54 の 2.6)。
大きさは手に持てる小箱 (幅 ±4、高さ 0〜7 単位、奥行き ±3。1 単位 = 1/16 ブロック)。底が y = 0、正面 = -Z (封は正面)。
縁 (trim) は階級の色 (paint_wild.TRIM)。光るのは封 (gem) だけ。

  python -I tools/rough/lowpoly/weapons/wild/build_wild.py o_boxes [id ...]
  python -I tools/install/install_boxes.py
"""
from __future__ import annotations

import math

from kit_wild import *  # noqa: F401,F403
from kit_wild import W

D = 3.0     # 奥行きの半分
WX = 4.0    # 幅の半分


def chest(main="main", lid="spine", h=4.2, lid_h=1.8, rim=True, dome=(0.94, 0.8)):
    """箱の体とふた。rim: 体の上下とふたの縁に階級の色の帯"""
    f = block(main, (0, h / 2, 0), (2 * WX, h, 2 * D))
    f += block(lid, (0, h + lid_h / 2, 0), (2 * WX + 0.3, lid_h, 2 * D + 0.3), taper=dome)
    if rim:
        f += block("trim", (0, 0.25, 0), (2 * WX + 0.35, 0.5, 2 * D + 0.35))
        f += block("trim", (0, h - 0.05, 0), (2 * WX + 0.4, 0.42, 2 * D + 0.4))
    return f


def corner_caps(mat="trim", h=4.2, s=0.9):
    """四つの角の金具 (縦の帯)"""
    f = []
    for x in (-WX, WX):
        for z in (-D, D):
            f += block(mat, (x, h / 2, z), (s, h + 0.1, s))
    return f


def seal_disc(c, r, mat="gem", th=0.3, n=8):
    """正面の丸い封 (z = 手前)"""
    x, y, z = c
    return prism(mat, (x, y, z + th / 2), (x, y, z - th / 2), r, r, sides=n)


# ====================================================================== 巨神の箱
def box_stone_whale(stage=0):
    """岩鯨の箱: 象牙色の櫃を鯨骨の肋が二本抱く。ふたに苔、封は水色の石の留め"""
    f = chest("main", "main", h=4.2, lid_h=1.9, rim=False, dome=(0.9, 0.72))
    for x in (-2.7, 2.7):
        pts = [(x, 0.2, -3.25), (x, 3.0, -3.45), (x, 5.4, -2.7), (x, 6.35, -1.0), (x, 6.45, 0.0), (x, 6.35, 1.0), (x, 5.4, 2.7),
               (x, 3.0, 3.45), (x, 0.2, 3.25)]
        f += curve("spine", pts, 0.5, 0.42, sides=6)
        for z in (-3.2, 3.2):
            f += ell("spine", (x, 0.25, z), (0.6, 0.35, 0.55), segs=6, rings=2)
    for p, r in (((1.0, 6.2, 0.9), (1.3, 0.4, 1.0)), ((-1.2, 6.1, -0.7), (1.0, 0.35, 0.9)), ((0.1, 6.3, 0.2), (0.9, 0.4, 0.8))):
        f += lump("acc", p, r, seed=abs(int(p[0] * 7 + 30)), segs=7, rings=2)
    f += block("guard", (0, 3.9, -3.1), (1.7, 2.2, 0.5), taper=(0.8, 1.0))
    f += gem((0, 3.9, -3.45), 0.55, 0.55, 0.28, segs=6, rings=2)
    return f


def box_tide_shell(stage=0):
    """潮殻の箱: 二枚貝の殻がふたになった黒い流木の櫃。殻の筋が扇に広がり、縁に藤壺、封は真珠"""
    f = block("main", (0, 2.1, 0), (2 * WX, 4.2, 2 * D))
    f += block("trim", (0, 0.25, 0), (2 * WX + 0.35, 0.5, 2 * D + 0.35))
    f += block("spine", (0, 4.6, 0.2), (2 * WX + 0.4, 0.9, 2 * D + 0.4), taper=(0.96, 0.92))
    for k in range(-3, 4):
        x0, x1 = k * 0.55, k * 1.25
        f += curve("spine", [(x0, 4.9, 3.1), (x0 * 1.6, 5.75, 1.4), (x1, 5.55, -1.4), (x1, 4.75, -3.35)], 0.42, 0.3, sides=4)
    f += block("spine", (0, 5.0, 3.0), (2.0, 1.2, 0.8))
    for p in ((-3.3, 0.8, -3.1), (-2.4, 0.6, -3.15), (3.2, 1.1, -3.1), (3.5, 0.6, 2.9), (-3.6, 1.6, 2.7), (2.6, 0.7, 3.1)):
        f += prism("acc", (p[0], p[1], p[2] * 0.92), p, 0.42, 0.24, sides=5)
    f += ell("cloth", (0, 3.7, -3.35), (0.72, 0.72, 0.6), segs=7, rings=3)
    f += torus("trim", (0, 3.7, -3.2), 0.85, 0.14, 0.14, n=10, k=3, ax="z")
    return f


def box_thunder_wing(stage=0):
    """雷翼の箱: 銅の帯を巻いた太鼓の形の櫃。両脇に小さな翼、封は稲妻に曲げた銅線と光る留め"""
    r = 2.7
    f = prism("main", (-3.4, 3.2, 0), (3.4, 3.2, 0), r, r, sides=10, hint=(0, 1, 0))
    for x in (-3.55, 3.55):
        f += prism("cloth", (x - 0.15, 3.2, 0), (x + 0.15, 3.2, 0), r - 0.15, r - 0.15, sides=10, hint=(0, 1, 0))
    for x in (-2.6, 0.0, 2.6):
        f += prism("acc", (x - 0.3, 3.2, 0), (x + 0.3, 3.2, 0), r + 0.2, r + 0.2, sides=10, hint=(0, 1, 0))
    for x in (-2.4, 2.4):
        f += block("dark", (x, 0.35, 0), (1.2, 0.7, 3.6))
    for s in (-1, 1):
        f += feather("guard", (s * 3.7, 4.6, 0.6), (s * 6.0, 7.2, 1.4), 1.3, th=0.4)
        f += feather("guard", (s * 3.7, 4.0, 0.9), (s * 6.4, 5.4, 1.8), 1.1, th=0.4)
    zig = [(-1.6, 4.6, -2.95), (0.4, 3.9, -3.0), (-0.6, 3.2, -3.0), (1.4, 2.4, -2.95)]
    f += curve("acc", zig, 0.22, 0.22, sides=4)
    f += gem((0, 3.3, -3.0), 0.42, 0.42, 0.25, segs=6, rings=2)
    return f


def box_ember(stage=0):
    """余燼の箱: 黒曜の板と冷えた溶岩の櫃。継ぎ目に熾の光が走り、封は燠石"""
    f = chest("main", "spine", h=4.2, lid_h=1.7, rim=False, dome=(0.92, 0.82))
    for p, r in (((-3.0, 1.0, -2.6), (1.3, 1.0, 0.8)), ((3.1, 0.8, 2.4), (1.2, 0.9, 0.9)), ((2.8, 1.2, -2.7), (1.0, 1.1, 0.7)),
                 ((-2.9, 0.9, 2.6), (1.1, 0.9, 0.8))):
        f += lump("acc", p, r, seed=abs(int(p[2] * 5 + 30)), segs=7, rings=2, rough=0.25)
    for z in (-3.05, 3.05):
        f += gbar([(-3.4, 2.4, z), (-1.0, 3.1, z), (0.6, 2.2, z), (3.3, 2.9, z)], r=0.16, mat="gem")
    f += gbar([(-3.9, 4.25, -3.1), (3.9, 4.25, -3.1)], r=0.14, mat="gem")
    f += block("acc", (0, 3.6, -3.1), (1.9, 2.0, 0.5), taper=(0.75, 1.0))
    f += ell("gem", (0, 3.6, -3.4), (0.7, 0.7, 0.4), segs=6, rings=2)
    return f


# ====================================================================== 階級の箱
def box_uncommon(stage=0):
    """アンコモンの箱: 樫の板を打った素朴な箱。縄を十字にかけ、結び目に緑の紐の封"""
    f = chest("main", "main", h=4.0, lid_h=1.5, dome=(0.97, 0.94))
    for y in (1.35, 2.7):
        for z in (-3.02, 3.02):
            f += block("dark", (0, y, z), (2 * WX - 0.4, 0.12, 0.05))
    f += curve("cord", [(0, 0.1, -3.2), (0, 5.65, -3.1), (0, 5.75, 0), (0, 5.65, 3.1), (0, 0.1, 3.2)], 0.24, 0.24, sides=4)
    f += curve("cord", [(-4.15, 0.1, 0), (-4.1, 5.65, 0), (0, 5.75, 0), (4.1, 5.65, 0), (4.15, 0.1, 0)], 0.24, 0.24, sides=4)
    f += ell("cloth", (0, 4.4, -3.35), (0.6, 0.5, 0.35), segs=6, rings=2)
    for s in (-1, 1):
        f += curve("cloth", [(0, 4.3, -3.4), (s * 0.5, 3.4, -3.45), (s * 0.7, 2.5, -3.4)], 0.18, 0.12, sides=3)
    return f


def box_rare(stage=0):
    """レアの箱: 青く塗った箱に鉄の帯を三本打ち、角に鋲。正面に赤い蝋の封と鉄の錠"""
    f = chest("main", "main", h=4.2, lid_h=1.8, dome=(0.95, 0.8))
    for x in (-2.6, 0.0, 2.6):
        f += block("acc", (x, 2.1, 0), (0.5, 4.3, 2 * D + 0.25))
        f += block("acc", (x, 5.1, 0), (0.5, 1.85, 2 * D + 0.55), taper=(1.0, 0.8))
    f += corner_caps("trim", h=4.2, s=0.7)
    for x in (-3.6, 3.6):
        for y in (1.0, 3.2):
            f += ell("acc", (x, y, -3.15), (0.24, 0.24, 0.18), segs=5, rings=1)
    f += block("acc", (-1.3, 3.5, -3.2), (1.1, 1.3, 0.45))
    f += seal_disc((1.3, 3.4, -3.15), 0.62, mat="cloth", th=0.32)
    f += curve("cloth", [(1.3, 2.9, -3.35), (1.5, 2.2, -3.35), (1.2, 1.6, -3.3)], 0.14, 0.1, sides=3)
    return f


def box_epic(stage=0):
    """エピックの箱: 紫の漆の箱に銀の金具。ふたの縁は反り、角に銀の葉の飾り、正面に紫の印の封"""
    f = chest("main", "main", h=4.2, lid_h=2.0, dome=(0.86, 0.66))
    f += block("acc", (0, 4.35, 0), (2 * WX + 0.7, 0.3, 2 * D + 0.7))
    for x in (-WX, WX):
        for z in (-D, D):
            f += extrude("acc", [(-0.8, 0), (0, -0.4), (0.8, 0), (0.55, 1.3), (0, 1.9), (-0.55, 1.3)], 0.25, c=(x * 0.78, 1.0, z * 1.03))
    for y in (1.0, 3.3):
        f += gbar([(-3.2, y, -3.08), (-1.4, y, -3.08)], r=0.12, mat="acc")
        f += gbar([(1.4, y, -3.08), (3.2, y, -3.08)], r=0.12, mat="acc")
    f += block("acc", (0, 3.0, -3.1), (2.0, 2.2, 0.4), taper=(0.8, 1.0))
    f += gem((0, 3.0, -3.35), 0.55, 0.62, 0.22, segs=4, rings=1)
    return f


def box_heroic(stage=0):
    """ヒロイックの箱: 朱の漆に金の縁。角に小さな炎の意匠が立ち、封は金の鎖を正面で十字に掛ける"""
    f = chest("main", "main", h=4.4, lid_h=2.0, dome=(0.9, 0.7))
    f += corner_caps("acc", h=4.4, s=0.8)
    for x in (-WX, WX):
        for z in (-D, D):
            f += spike(BONE, "acc", (x * 0.98, 4.5, z * 0.98), (x * 0.08, 1.0, z * 0.06), 1.6, 0.35, sides=4, rings=1)
            f += spike(BONE, "gem", (x * 0.98, 4.6, z * 0.98), (x * 0.02, 1.0, z * 0.02), 0.9, 0.18, sides=4, rings=1)
    for i in range(7):
        t = i / 6
        a = (-3.4 + 6.8 * t, 1.0 + 3.2 * t)
        b = (3.4 - 6.8 * t, 1.0 + 3.2 * t)
        f += torus("acc", (a[0], a[1], -3.25), 0.32, 0.09, 0.09, n=6, k=3, ax="z")
        f += torus("acc", (b[0], b[1], -3.25), 0.32, 0.09, 0.09, n=6, k=3, ax="z")
    f += block("acc", (0, 2.6, -3.15), (1.6, 1.6, 0.5))
    f += gem((0, 2.6, -3.45), 0.5, 0.5, 0.24, segs=6, rings=2)
    return f


def box_artifact(stage=0):
    """淵の金の箱: 金の箱のまわりに淵の欠片が浮く。ふたに淵の紋、封は金の輪と深い紫の石"""
    f = chest("main", "main", h=4.2, lid_h=1.9, rim=False, dome=(0.9, 0.74))
    f += block("acc", (0, 0.25, 0), (2 * WX + 0.35, 0.5, 2 * D + 0.35))
    f += block("acc", (0, 4.15, 0), (2 * WX + 0.4, 0.42, 2 * D + 0.4))
    f += torus("acc", (0, 6.15, 0), 1.3, 0.16, 0.16, n=10, k=3, ax="y")
    f += gbar([(-1.9, 6.2, 0), (1.9, 6.2, 0)], r=0.12, mat="gem")
    f += gbar([(0, 6.2, -1.6), (0, 6.2, 1.6)], r=0.12, mat="gem")
    for p, s in (((-4.9, 6.0, -1.2), 0.75), ((4.8, 7.1, 0.8), 0.9), ((-2.6, 8.4, 1.3), 0.6), ((3.0, 8.8, -1.6), 0.55), ((0.4, 9.4, 0.4), 0.7)):
        f += gem(p, s * 0.55, s * 1.2, s * 0.55, segs=4, rings=1)
    f += torus("acc", (0, 3.0, -3.2), 0.95, 0.17, 0.17, n=10, k=3, ax="z")
    f += gem((0, 3.0, -3.35), 0.55, 0.55, 0.3, segs=6, rings=2)
    return f


def box_tower(stage=0):
    """塔の箱: 箱が縦に割れ、左は白い石 (天)、右は黒い石 (淵)。合わせ目と角は金、黒い側に紫に光る割れ目。ふたの上に天秤の印 (金の輪と竿と二つの皿)、
    正面の封は金の輪の中の紫の石"""
    f = []
    for s, mat in ((-1, "main"), (1, "spine")):
        f += block(mat, (s * WX / 2, 2.1, 0), (WX, 4.2, 2 * D))
        f += block(mat, (s * WX / 2, 5.05, 0), (WX + 0.15, 1.9, 2 * D + 0.3), taper=(0.92, 0.8))
    f += block("acc", (0, 0.25, 0), (2 * WX + 0.35, 0.5, 2 * D + 0.35))
    f += block("acc", (0, 4.15, 0), (2 * WX + 0.4, 0.42, 2 * D + 0.4))
    f += block("acc", (0, 2.9, 0), (0.5, 5.8, 2 * D + 0.45))
    for x in (-WX, WX):
        for z in (-D, D):
            f += block("acc", (x, 2.1, z), (0.7, 4.2, 0.7))
    # 黒い側の割れ目 (光る)
    f += gbar([(1.2, 0.8, -D - 0.05), (2.0, 1.9, -D - 0.05), (1.6, 2.9, -D - 0.05), (2.6, 3.8, -D - 0.05)], r=0.11, mat="gem")
    f += gbar([(WX + 0.05, 1.0, -1.4), (WX + 0.05, 2.2, -0.4), (WX + 0.05, 3.4, -1.0)], r=0.11, mat="gem")
    # ふたの上の天秤の印
    f += prism("acc", (0, 6.0, 0), (0, 7.6, 0), 0.18, 0.18, sides=4, caps=(True, True))
    f += prism("acc", (-2.6, 7.6, 0), (2.6, 7.6, 0), 0.16, 0.16, sides=4, caps=(True, True), hint=(0, 1, 0))
    for s, mat in ((-1, "acc"), (1, "spine")):
        f += prism("acc", (s * 2.4, 7.5, 0), (s * 2.4, 6.7, 0), 0.05, 0.05, sides=3, caps=(False, False))
        f += ell(mat, (s * 2.4, 6.55, 0), (0.75, 0.2, 0.75), segs=8, rings=2)
    f += torus("acc", (0, 7.6, -0.05), 0.75, 0.14, 0.18, n=10, k=3, ax="z")
    # 正面の封
    f += torus("acc", (0, 2.6, -3.2), 0.95, 0.17, 0.17, n=10, k=3, ax="z")
    f += gem((0, 2.6, -3.35), 0.55, 0.55, 0.3, segs=6, rings=2)
    return f


WEAPONS = dict([
    W(box_stone_whale, "岩鯨の封の箱", "rare", "trinket", "frost", dict(main="driftwood", spine="tusk", acc="moss", guard="stone")),
    W(box_tide_shell, "潮殻の封の箱", "rare", "trinket", "sea", dict(main="darkwood", spine="shell", acc="rockshell", cloth="nacre")),
    W(box_thunder_wing, "雷翼の封の箱", "rare", "trinket", "spark", dict(main="bluecloth", cloth="paper", acc="copper", guard="whitefur", dark="iron")),
    W(box_ember, "余燼の封の箱", "rare", "trinket", "ember", dict(main="basalt", spine="blacksteel", acc="ash")),
    W(box_uncommon, "アンコモンの封の箱", "uncommon", "trinket", "moss", dict(main="oak", cord="rope", cloth="greencloth")),
    W(box_rare, "レアの封の箱", "rare", "trinket", "lamp", dict(main="bluecloth", acc="iron", cloth="redcloth")),
    W(box_epic, "エピックの封の箱", "epic", "trinket", "void", dict(main="purplecloth", acc="silver")),
    W(box_heroic, "ヒロイックの封の箱", "heroic", "trinket", "ember", dict(main="redcloth", acc="gold")),
    W(box_artifact, "淵の金の箱", "heroic", "trinket", "void", dict(main="gold", acc="gold")),
    W(box_tower, "塔の箱", "heroic", "trinket", "void", dict(main="cloudwhite", spine="basalt", acc="gold")),
])
for _k in WEAPONS:
    WEAPONS[_k]["scale"] = 15.0
    WEAPONS[_k]["cen"] = (0.0, 3.6, 0.0)
