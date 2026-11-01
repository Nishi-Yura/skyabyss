"""2026-11-02 の全体監査で「階級が上がるほど豪華」から外れていた三品のラフ 3 案 (docs/review/roughs_1102)。
一品一案の決まりどおり、選んだ一つだけを o_boxes.py / o_wild_acc.py の本体に写して入れる (ここは選ぶまでの置き場)。

  エピックの封の箱   今 192 三角形 (アンコモン 220・レア 262 より少ない)。目標 380〜450、レア (鉄の帯) より部品が一段多い
  淵の金の箱         今 248 (ヒロイック 664 より少ない)。目標 700〜900、ヒロイックの上
  雲雀の羽根飾り     今 90 三角形・32px (全品でいちばん少ない)。ヒロイックの装飾として 250 以上

  python -I tools/rough/lowpoly/weapons/wild/build_wild.py o_roughs_1102 [--quick]
"""
from __future__ import annotations

import math

from kit_wild import *  # noqa: F401,F403
from kit_wild import W
from o_boxes import chest, corner_caps, seal_disc, D, WX
from o_wild_acc import plume  # noqa: F401


# ====================================================================== エピックの封の箱 (紫の漆・銀)
def box_epic_a(stage=0):
    """案 A 銀の葉冠: 紫の漆の箱に銀の金具。角の銀の葉を大きくし、ふたの上に銀の三日月の冠、正面に紫の印の封と銀の鎖の垂れ"""
    f = chest("main", "main", h=4.2, lid_h=2.0, dome=(0.86, 0.66))
    f += block("acc", (0, 4.35, 0), (2 * WX + 0.7, 0.3, 2 * D + 0.7))
    f += corner_caps("acc", h=4.2, s=0.7)
    for x in (-WX, WX):
        for z in (-D, D):
            f += extrude("acc", [(-1.0, 0), (0, -0.5), (1.0, 0), (0.7, 1.6), (0, 2.4), (-0.7, 1.6)], 0.3, c=(x * 0.8, 1.0, z * 1.05))
    f += torus("acc", (0, 6.5, 0), 1.6, 0.18, 0.18, n=12, k=3, ax="y")
    for k in range(5):
        a = math.radians(-60 + 30 * k)
        f += gem((1.6 * math.sin(a), 6.55, -1.6 * math.cos(a)), 0.22, 0.3, 0.22, segs=4, rings=1)
    for y in (1.0, 3.3):
        f += gbar([(-3.2, y, -3.08), (-1.4, y, -3.08)], r=0.12, mat="acc")
        f += gbar([(1.4, y, -3.08), (3.2, y, -3.08)], r=0.12, mat="acc")
    f += block("acc", (0, 3.0, -3.1), (2.0, 2.2, 0.4), taper=(0.8, 1.0))
    f += gem((0, 3.0, -3.35), 0.55, 0.62, 0.22, segs=4, rings=1)
    for s in (-1, 1):
        f += curve("acc", [(s * 0.9, 2.0, -3.3), (s * 1.3, 1.2, -3.4), (s * 1.1, 0.5, -3.3)], 0.1, 0.08, sides=4)
        f += gem((s * 1.1, 0.4, -3.4), 0.16, 0.22, 0.12, segs=4, rings=1)
    return f


def box_epic_b(stage=0):
    """案 B 紫紋の箱: ふたの上に銀の紋 (四つの紫の石を銀の線でつなぐ)。縦の銀の帯三本と鋲、正面の封は紫の石を銀の環で囲む"""
    f = chest("main", "main", h=4.2, lid_h=2.0, dome=(0.88, 0.7))
    for x in (-2.4, 0.0, 2.4):
        f += block("acc", (x, 2.1, 0), (0.45, 4.3, 2 * D + 0.25))
        f += block("acc", (x, 5.1, 0), (0.45, 1.9, 2 * D + 0.6), taper=(1.0, 0.75))
        for z in (-3.2, 3.2):
            for y in (0.8, 3.4):
                f += ell("acc", (x, y, z), (0.22, 0.22, 0.16), segs=5, rings=1)
    pts = [(-1.4, 6.35, 0), (0, 6.35, -1.2), (1.4, 6.35, 0), (0, 6.35, 1.2)]
    for i in range(4):
        f += gbar([pts[i], pts[(i + 1) % 4]], r=0.1, mat="acc")
        f += gem(pts[i], 0.22, 0.26, 0.22, segs=4, rings=1)
    f += gem((0, 6.55, 0), 0.32, 0.36, 0.32, segs=5, rings=2)
    f += torus("acc", (0, 3.0, -3.2), 0.85, 0.14, 0.14, n=10, k=3, ax="z")
    f += gem((0, 3.0, -3.35), 0.5, 0.56, 0.22, segs=4, rings=1)
    return f


def box_epic_c(stage=0):
    """案 C 二重のふた: ふたが二段に積まれ、段の縁を銀の唐草 (曲線) がめぐる。角は銀の留め、正面の封は紫の石と、左右に銀の小鈴"""
    f = chest("main", "main", h=4.2, lid_h=1.4, dome=(0.9, 0.78))
    f += block("main", (0, 6.1, 0), (2 * WX - 1.4, 1.1, 2 * D - 1.0), taper=(0.82, 0.7))
    f += block("acc", (0, 5.55, 0), (2 * WX - 1.2, 0.22, 2 * D - 0.8))
    f += corner_caps("acc", h=4.2, s=0.7)
    for z in (-D - 0.25, D + 0.25):
        f += curve("acc", [(-3.4, 5.0, z), (-2.0, 5.7, z), (-0.8, 5.0, z), (0.8, 5.7, z), (2.0, 5.0, z), (3.4, 5.7, z)], 0.12, 0.1, sides=4)
    for x in (-WX - 0.25, WX + 0.25):
        f += curve("acc", [(x, 5.0, -2.5), (x, 5.7, -1.2), (x, 5.0, 0.0), (x, 5.7, 1.2), (x, 5.0, 2.5)], 0.12, 0.1, sides=4)
    f += gem((0, 6.9, 0), 0.4, 0.5, 0.4, segs=5, rings=2)
    f += block("acc", (0, 3.0, -3.1), (1.8, 2.0, 0.4), taper=(0.85, 1.0))
    f += gem((0, 3.0, -3.35), 0.5, 0.58, 0.22, segs=4, rings=1)
    for s in (-1, 1):
        f += curve("acc", [(s * 2.6, 4.1, -3.2), (s * 2.9, 3.3, -3.35)], 0.08, 0.08, sides=4)
        f += ell("acc", (s * 2.9, 2.9, -3.35), (0.3, 0.36, 0.3), segs=6, rings=2)
    return f


# ====================================================================== 淵の金の箱 (金・淵の欠片)
def box_artifact_a(stage=0):
    """案 A 欠片の冠: 金の箱の角に金の柱、ふたの上の金の輪から九つの淵の欠片が浮く。正面は金の輪と深い紫の石、左右に小さな石"""
    f = chest("main", "main", h=4.2, lid_h=1.9, rim=False, dome=(0.9, 0.74))
    f += block("acc", (0, 0.25, 0), (2 * WX + 0.35, 0.5, 2 * D + 0.35))
    f += block("acc", (0, 4.15, 0), (2 * WX + 0.4, 0.42, 2 * D + 0.4))
    f += corner_caps("acc", h=4.2, s=0.9)
    for x in (-WX, WX):
        for z in (-D, D):
            f += gem((x, 4.7, z), 0.3, 0.45, 0.3, segs=4, rings=1)
    f += torus("acc", (0, 6.15, 0), 1.5, 0.16, 0.16, n=12, k=3, ax="y")
    f += torus("acc", (0, 6.9, 0), 0.9, 0.12, 0.12, n=10, k=3, ax="y")
    for k in range(9):
        a = math.radians(40 * k)
        r = 2.6 + 0.9 * (k % 3)
        y = 6.3 + 0.9 * ((k * 2) % 3) + 0.4 * (k % 2)
        s = 0.5 + 0.2 * (k % 3)
        f += gem((r * math.cos(a), y, r * math.sin(a) * 0.7), s * 0.55, s * 1.25, s * 0.55, segs=4, rings=1)
    f += torus("acc", (0, 3.0, -3.2), 0.95, 0.17, 0.17, n=10, k=3, ax="z")
    f += gem((0, 3.0, -3.35), 0.55, 0.55, 0.3, segs=6, rings=2)
    for s in (-1, 1):
        f += gem((s * 2.4, 2.2, -3.3), 0.2, 0.3, 0.14, segs=4, rings=1)
    return f


def box_artifact_b(stage=0):
    """案 B 金の格子: ふたと側面に金の格子をかけ、格子の交わりに淵の石。ふたの上に大きな金の輪と六つの浮く欠片、正面は金の錠と紫の石"""
    f = chest("main", "main", h=4.2, lid_h=1.9, rim=False, dome=(0.9, 0.74))
    f += block("acc", (0, 0.25, 0), (2 * WX + 0.35, 0.5, 2 * D + 0.35))
    f += block("acc", (0, 4.15, 0), (2 * WX + 0.4, 0.42, 2 * D + 0.4))
    for x in (-2.0, 0.0, 2.0):
        f += gbar([(x, 4.4, -3.1), (x, 5.9, -1.4), (x, 6.1, 0), (x, 5.9, 1.4), (x, 4.4, 3.1)], r=0.1, mat="acc")
    for z in (-1.6, 0.0, 1.6):
        f += gbar([(-4.0, 4.5, z), (-2.0, 5.8, z), (0, 6.1, z), (2.0, 5.8, z), (4.0, 4.5, z)], r=0.1, mat="acc")
    for x in (-2.0, 0.0, 2.0):
        for z in (-1.6, 0.0, 1.6):
            f += gem((x, 6.1 - 0.3 * (abs(z) / 1.6) - 0.2 * (abs(x) / 2.0), z), 0.16, 0.22, 0.16, segs=4, rings=1)
    for y in (1.4, 2.9):
        f += gbar([(-3.9, y, -3.08), (3.9, y, -3.08)], r=0.09, mat="acc")
    f += torus("acc", (0, 6.6, 0), 1.9, 0.14, 0.14, n=12, k=3, ax="y")
    for k in range(6):
        a = math.radians(60 * k + 30)
        f += gem((3.0 * math.cos(a), 7.2 + 0.5 * (k % 2), 2.2 * math.sin(a)), 0.4, 0.85, 0.4, segs=4, rings=1)
    f += block("acc", (0, 2.6, -3.2), (1.5, 1.7, 0.45))
    f += gem((0, 2.7, -3.45), 0.45, 0.5, 0.26, segs=6, rings=2)
    return f


def box_artifact_c(stage=0):
    """案 C 淵の暈: 箱のまわりを二本の金の輪 (水平と斜め) がめぐり、七つの欠片が輪に沿って浮く。角は金の棘、ふたの石の群れ、正面は紫の石"""
    f = chest("main", "main", h=4.2, lid_h=1.9, rim=False, dome=(0.9, 0.74))
    f += block("acc", (0, 0.25, 0), (2 * WX + 0.35, 0.5, 2 * D + 0.35))
    f += block("acc", (0, 4.15, 0), (2 * WX + 0.4, 0.42, 2 * D + 0.4))
    for x in (-WX, WX):
        for z in (-D, D):
            f += spike(BONE, "acc", (x * 0.98, 4.4, z * 0.98), (x * 0.1, 1.0, z * 0.08), 1.4, 0.3, sides=4, rings=1)
    f += torus("acc", (0, 3.4, 0), 5.3, 0.16, 0.16, n=18, k=3, ax="y")
    f += at(torus("acc", (0, 0, 0), 5.6, 0.14, 0.14, n=18, k=3, ax="y"), rot_x(28), (0, 4.0, 0))
    for k in range(7):
        a = math.radians(51.4 * k)
        f += gem((5.3 * math.cos(a), 3.4 + 1.6 * math.sin(a * 2), 5.3 * math.sin(a) * 0.75), 0.35, 0.8, 0.35, segs=4, rings=1)
    for p, s in (((0.0, 6.4, 0.0), 0.75), ((-1.1, 6.2, 0.6), 0.5), ((1.0, 6.25, -0.5), 0.55), ((0.3, 6.1, 1.1), 0.45)):
        f += gem(p, s * 0.5, s * 1.0, s * 0.5, segs=4, rings=1)
    f += torus("acc", (0, 3.0, -3.2), 0.95, 0.17, 0.17, n=10, k=3, ax="z")
    f += gem((0, 3.0, -3.35), 0.55, 0.55, 0.3, segs=6, rings=2)
    return f


# ====================================================================== 雲雀の羽根飾り (ヒロイックの装飾)
def wild_skylark_plume_a(stage=0):
    """案 A 五枚の扇: 雲雀の白い羽根を五枚扇に束ね、金の留め具の輪に空の光の石。羽根の先は空の光、留め具から金の小鎖"""
    f = prism("trim", (0, 0, 0), (0, 1.4, 0), 0.6, 0.55, sides=8, caps=(True, True))
    f += torus("trim", (0, 0.7, -0.55), 0.5, 0.1, 0.1, n=10, k=3, ax="z")
    f += gem((0, 0.7, -0.6), 0.28, 0.28, 0.16, segs=6, rings=2)
    for k, (dx, L, lean) in enumerate(((-1.6, 5.2, -1.0), (-0.8, 6.4, -0.5), (0.0, 7.6, 0.0), (0.8, 6.4, 0.5), (1.6, 5.2, 1.0))):
        f += plume((dx * 0.25, 1.2, 0.1 * (k % 3)), (dx * 1.6, 1.2 + L, 0.6 + 0.15 * k), 0.8, th=0.2, big=True, mat="cloth", tipmat="gem", scorch=1)
    for s in (-1, 1):
        f += curve("trim", [(s * 0.3, 0.1, -0.3), (s * 0.5, -0.9, -0.5), (s * 0.4, -1.7, -0.3)], 0.07, 0.06, sides=4)
        f += gem((s * 0.4, -1.95, -0.3), 0.14, 0.2, 0.12, segs=4, rings=1)
    return f


def wild_skylark_plume_b(stage=0):
    """案 B 渦の留め具: 三枚の大きな羽根と二枚の小さな羽根。留め具は金の渦 (らせんの線) に三つの空の光の石、下に二つの雫"""
    f = prism("trim", (0, 0, 0), (0, 1.2, 0), 0.5, 0.45, sides=8, caps=(True, True))
    pts = [(0.9 * math.cos(math.radians(a)) * (1 - a / 900), 0.6 + 0.9 * math.sin(math.radians(a)) * (1 - a / 900), -0.55) for a in range(0, 540, 45)]
    f += curve("trim", pts, 0.09, 0.07, sides=4)
    for k, a in enumerate((20, 150, 280)):
        f += gem((0.7 * math.cos(math.radians(a)), 0.6 + 0.7 * math.sin(math.radians(a)), -0.6), 0.16, 0.16, 0.1, segs=4, rings=1)
    for k, (dx, L) in enumerate(((-0.9, 6.2), (0.0, 7.8), (0.9, 6.6))):
        f += plume((dx * 0.3, 1.0, 0.1 * k), (dx * 2.4, 1.0 + L, 0.6 + 0.2 * k), 0.9, th=0.2, big=True, mat="cloth", tipmat="gem", scorch=1)
    for k, (dx, L) in enumerate(((-1.6, 3.6), (1.6, 3.9))):
        f += plume((dx * 0.3, 0.9, -0.2), (dx * 1.8, 0.9 + L, 0.2), 0.55, th=0.15, big=False, mat="cloth", tipmat="gem", scorch=1)
    for s in (-1, 1):
        f += gbar([(s * 0.35, 0.0, -0.4), (s * 0.5, -1.2, -0.5)], r=0.06, mat="trim")
        f += gem((s * 0.5, -1.45, -0.5), 0.16, 0.26, 0.14, segs=4, rings=1)
    return f


def wild_skylark_plume_c(stage=0):
    """案 C 巻く羽根: 羽根の先が外へ巻く四枚。留め具は金の二重の輪 (ブローチ) に三つの石、輪の下に金の房"""
    f = prism("trim", (0, 0, 0), (0, 1.3, 0), 0.55, 0.5, sides=8, caps=(True, True))
    f += torus("trim", (0, 0.65, -0.55), 0.7, 0.1, 0.1, n=12, k=3, ax="z")
    f += torus("trim", (0, 0.65, -0.6), 0.42, 0.08, 0.08, n=10, k=3, ax="z")
    for a in (90, 210, 330):
        f += gem((0.56 * math.cos(math.radians(a)), 0.65 + 0.56 * math.sin(math.radians(a)), -0.66), 0.15, 0.15, 0.1, segs=4, rings=1)
    for k, (dx, L, curl) in enumerate(((-1.2, 5.6, -1.6), (-0.4, 7.2, -0.7), (0.4, 7.4, 0.7), (1.2, 5.8, 1.6))):
        f += plume((dx * 0.25, 1.1, 0.1 * k), (dx * 1.4 + curl, 1.1 + L, 0.5 + 0.15 * k), 0.85, th=0.2, big=True, mat="cloth", tipmat="gem", scorch=1)
        f += gem((dx * 1.4 + curl * 1.15, 1.3 + L, 0.6 + 0.15 * k), 0.14, 0.14, 0.1, segs=4, rings=1)
    for k in range(5):
        x = -0.6 + 0.3 * k
        f += curve("trim", [(x * 0.6, 0.0, -0.4), (x, -1.3 - 0.2 * (k % 2), -0.45)], 0.06, 0.05, sides=4)
    return f


WEAPONS = dict([
    W(box_epic_a, "エピックの封の箱 A", "epic", "trinket", "void", dict(main="purplecloth", acc="silver")),
    W(box_epic_b, "エピックの封の箱 B", "epic", "trinket", "void", dict(main="purplecloth", acc="silver")),
    W(box_epic_c, "エピックの封の箱 C", "epic", "trinket", "void", dict(main="purplecloth", acc="silver")),
    W(box_artifact_a, "淵の金の箱 A", "heroic", "trinket", "void", dict(main="gold", acc="gold")),
    W(box_artifact_b, "淵の金の箱 B", "heroic", "trinket", "void", dict(main="gold", acc="gold")),
    W(box_artifact_c, "淵の金の箱 C", "heroic", "trinket", "void", dict(main="gold", acc="gold")),
    W(wild_skylark_plume_a, "雲雀の羽根飾り A", "heroic", "trinket", "dawn", dict(cloth="cloudwhite")),
    W(wild_skylark_plume_b, "雲雀の羽根飾り B", "heroic", "trinket", "dawn", dict(cloth="cloudwhite")),
    W(wild_skylark_plume_c, "雲雀の羽根飾り C", "heroic", "trinket", "dawn", dict(cloth="cloudwhite")),
])
for _k in WEAPONS:
    if _k.startswith("box_"):
        WEAPONS[_k]["scale"] = 15.0
        WEAPONS[_k]["cen"] = (0.0, 3.6, 0.0)
