"""野の装備 帯 IV (Lv 31〜40。深淵の上の層 (骨・塩)) の武器 24 本 (docs/design/51 の 4.4)。一品一案。
レア = 探掘隊の道具 (柄長剣・つるはし鎚・骨笛・塩鴉・縄切り・弔いの手鈴)、エピック = 塩と骨の層の品 (塩晶・臼・塩の涙・骨弦・骨針・塩の柱) と
深淵の者を写した品 (縄打ち・発破・羅針・灯籠・逆さ歩きの爪・縄結び)、ヒロイック = 深淵だけの者 (逆さ歩き・灯喰い・鼓動もどき・縄の人・灯消し・子守)。
深淵の品は少し不気味に: 錨鉄の黒、古い真鍮、灯の琥珀、塩の白、淵の紫。
"""
from __future__ import annotations

import math

from kit_wild import *  # noqa: F401,F403
from kit_wild import W, haft, bow_grip, bell, lash, flag, leaf_blade, claw_hand, _slab


def _crystals(f, base, n, size, seed, mat="main", up=(0, 1, 0), spread=0.9):
    """結晶の群れ (先の尖った六角の柱)"""
    import random
    rnd = random.Random(seed)
    for i in range(n):
        d = V(up[0] + rnd.uniform(-spread, spread), up[1] + rnd.uniform(0.2, 0.8), up[2] + rnd.uniform(-spread, spread))
        d = d / (sum(x * x for x in d) ** 0.5)
        L = size * rnd.uniform(0.6, 1.2)
        b = V(*base) + V(rnd.uniform(-0.5, 0.5), 0, rnd.uniform(-0.5, 0.5)) * size * 0.4
        f += prism(mat, tuple(b), tuple(b + d * L * 0.7), size * 0.28, size * 0.28, sides=6, caps=(True, False))
        f += spike(BONE, mat, tuple(b + d * L * 0.7), tuple(d), L * 0.45, size * 0.28, sides=6, rings=1)
    return f


def wild_delver_sword(stage=0):
    """探掘隊の柄長剣: 探掘隊の長い柄の剣。両手で持てる長い柄に縄の輪、鍔に灯を掛ける鉤が二つ、刃は真っすぐで根に隊の刻印"""
    f = haft(-6.6, 2.8, r=0.8, mat="grip", wrap=(-6.0, 2.2))
    f += torus("cord", (0, -7.6, 0), 0.9, 0.22, 0.22, n=10, k=4, ax="z")
    f += curve("guard", [(-3.0, 3.6, 0), (0, 3.0, 0), (3.0, 3.6, 0)], [0.5, 0.6, 0.5], [0.55, 0.65, 0.55], sides=6, caps=(True, True), hint=(0, 0, 1))
    for s in (-1, 1):
        f += curve("guard", [(s * 3.0, 3.6, 0), (s * 3.6, 3.2, 0), (s * 3.5, 2.2, 0), (s * 3.0, 2.0, 0)], [0.3, 0.28, 0.24, 0.1], [0.3, 0.28, 0.24, 0.1], sides=4)
    f += prism("acc", (0, 3.0, -0.8), (0, 3.0, 0.8), 1.0, 1.0, sides=8, caps=(True, True), hint=(1, 0, 0))
    st = [(3.6, -1.15, 1.15, 0.45, 0.45), (10.0, -1.15, 1.15, 0.42, 0.42), (17.0, -1.05, 1.05, 0.4, 0.4), (21.0, -0.8, 0.8, 0.34, 0.34),
          (23.6, 0, 0, 0, 0)]
    f += blade("main", st)
    f += block("dark", (0, 6.0, -0.44), (0.9, 1.4, 0.06))
    return f


def wild_saltcrystal_greatsword(stage=0):
    """塩晶の大剣: 塩の結晶が群れて育った大剣。白く透ける刃の縁、刃の根と鍔に結晶の房が噴き出し、柄は塩に固まった縄"""
    f = haft(-5.6, 3.0, r=0.9, mat="grip", wrap=(-4.8, 2.4), wmat="cord")
    f += lump("main", (0, -6.0, 0), (1.1, 0.9, 1.1), seed=41)
    f += block("guard", (0, 3.6, 0), (4.6, 1.4, 2.0), taper=(0.8, 0.8))
    st = [(4.2, -2.0, 2.0, 0.65, 0.65), (10.0, -2.2, 2.2, 0.6, 0.6), (17.0, -2.0, 2.0, 0.56, 0.56), (22.0, -1.5, 1.5, 0.48, 0.48),
          (25.8, -0.7, 0.7, 0.3, 0.3), (27.8, 0, 0, 0, 0)]
    f += blade("main", st, colmats=["edge", "main", "edge", "edge", "main", "edge"])
    for s in (-1, 1):
        _crystals(f, (s * 2.4, 4.6, 0), 4, 2.4, 5 + s, mat="edge", up=(s * 1.2, 0.6, 0), spread=0.5)
    _crystals(f, (-2.2, 12.0, 0), 3, 1.5, 8, mat="edge", up=(-1, 0.5, 0), spread=0.4)
    _crystals(f, (2.0, 17.0, 0), 3, 1.3, 9, mat="edge", up=(1, 0.5, 0), spread=0.4)
    return f


def wild_rope_halberd(stage=0):
    """縄打ちの鉤槍: 鉤の付いた黒い斧槍。斧の刃の背に大きな鉤、穂は細い錐。柄に縄がきつく巻かれ、鉤の先から縄の輪が垂れる"""
    f = haft(-6.0, 17.0, r=0.72, mat="grip", wrap=(-2.6, 2.4), wmat="cord")
    for y in (6.0, 9.0, 12.0):
        f += lash(y, 0.92, 2, mat="cord")
    f += spike(BONE, "spine", (0, -5.9, 0), (0, -1, 0), 1.4, 0.7, sides=6, rings=1)
    f += prism("spine", (0, 16.6, 0), (0, 19.4, 0), 0.9, 0.75, sides=8, caps=(True, True))
    f += spike(BONE, "main", (0, 19.2, 0), (0, 1, 0), 6.0, 0.7, sides=6, rings=2)
    f += extrude("main", [(0.6, 16.4), (3.2, 15.4), (4.8, 16.6), (5.0, 18.6), (4.2, 20.4), (2.6, 20.0), (0.6, 19.4)], 0.8)
    f += curve("edge", [(3.4, 15.6, 0), (4.8, 16.8, 0), (5.0, 18.6, 0), (4.3, 20.2, 0)], 0.2, 0.45, sides=4, hint=(0, 0, 1))
    hook = [(-0.6, 18.0, 0), (-2.6, 18.4, 0), (-3.8, 17.4, 0), (-3.6, 15.8, 0), (-2.8, 15.4, 0)]
    f += curve("main", hook, [0.55, 0.5, 0.42, 0.3, 0.08], [0.45, 0.4, 0.34, 0.24, 0.06], sides=5, caps=(True, True))
    f += curve("cord", [(-3.6, 16.0, 0), (-3.9, 13.6, 0.2), (-3.0, 12.2, 0.4), (-2.4, 13.8, 0.2), (-2.9, 15.6, 0)], 0.14, 0.14, sides=3)
    return f


def wild_inverted_greatsword(stage=0):
    """逆さ歩きの天剣: 天井から逆さに吊られた剣の形。柄の上に鍔の代わりの逆さの手の骨が広がり、そこから刃が上へ伸びて先がふくらむ (逆さの涙)。刃の根は細く、刃に逆さの指の溝、柄頭から縄が垂れる"""
    f = haft(-5.6, 2.8, r=0.85, mat="grip", wrap=(-4.8, 2.2))
    f += curve("cord", [(0, -5.6, 0), (0.4, -7.4, 0), (-0.2, -9.0, 0)], 0.14, 0.14, sides=3)
    f += ell("spine", (0, -5.8, 0), (0.95, 0.6, 0.95), segs=6, rings=1)
    # 逆さの手の骨 (指が下へ曲がって柄を掴む)
    f += ell("acc", (0, 3.6, 0), (1.8, 1.0, 1.3), segs=8, rings=2)
    for k in range(5):
        a = math.radians(-60 + 30 * k)
        x, z = 1.6 * math.sin(a), -1.1 * math.cos(a)
        f += curve("acc", [(x, 3.6, z), (x * 1.5, 3.0, z * 1.4), (x * 1.3, 1.6, z * 1.3), (x * 0.8, 0.8, z * 0.9)], [0.3, 0.27, 0.22, 0.08],
                   [0.3, 0.27, 0.22, 0.08], sides=5)
    # 刃: 根は細く、先へふくらむ逆さの涙
    st = [(4.4, -0.7, 0.7, 0.4, 0.4), (8.0, -0.9, 0.9, 0.42, 0.42), (13.0, -1.6, 1.6, 0.48, 0.48), (19.0, -2.6, 2.6, 0.55, 0.55),
          (23.0, -2.7, 2.7, 0.52, 0.52), (25.8, -1.8, 1.8, 0.4, 0.4), (27.4, 0, 0, 0, 0)]
    f += blade("main", st)
    for x in (-1.2, 0.0, 1.2):
        f += gbar([(x * 0.4, 9.0, -0.5), (x, 16.0, -0.55), (x * 1.6, 22.0, -0.55)], r=0.16)
    return f


def wild_pick_hammer(stage=0):
    """探掘隊のつるはし鎚: 片側がつるはし、片側が鎚の探掘の道具。鉄の頭に隊の刻印、柄に灯の鉤と縄"""
    f = haft(-4.4, 12.0, r=0.88, mat="grip", wrap=(-3.6, 0.8), wmat="cord")
    f += block("main", (0, 13.2, 0), (2.8, 2.6, 2.4))
    f += prism("main", (1.4, 13.2, 0), (3.6, 13.2, 0), 1.2, 1.5, sides=8, caps=(True, True), hint=(0, 1, 0))
    f += prism("edge", (3.6, 13.2, 0), (4.0, 13.2, 0), 1.55, 1.55, sides=8, caps=(True, True), hint=(0, 1, 0))
    pick = [(-1.2, 13.4, 0), (-3.4, 13.2, 0), (-5.6, 12.4, 0), (-7.0, 11.0, 0)]
    f += curve("main", pick, [0.95, 0.8, 0.55, 0.06], [0.8, 0.65, 0.45, 0.05], sides=5, caps=(True, True))
    f += curve("spine", [(0.95, 9.0, 0), (1.8, 9.4, 0), (1.9, 8.4, 0)], 0.16, 0.16, sides=4)
    f += block("dark", (0, 13.2, -1.22), (1.2, 1.0, 0.06))
    return f


def wild_saltmill_maul(stage=0):
    """岩塩の臼鎚: 岩塩の石臼を頭にした大鎚。二枚の臼が重なり、目の溝が放射に刻まれる。真ん中の心棒が突き出て、臼の縁から塩がこぼれる"""
    f = haft(-4.8, 12.4, r=1.0, mat="grip", wrap=(-4.0, 0.8))
    for x in (-1.0, 1.0):
        f += prism("main", (x * 2.6, 14.6, 0), (x * 0.1, 14.6, 0), 3.2, 3.2, sides=12, caps=(True, True), hint=(0, 1, 0))
    for s in (-1, 1):
        for k in range(8):
            a = math.radians(45 * k + 22.5)
            f += prism("dark", (s * 2.62, 14.6 + 0.6 * math.cos(a), 0.6 * math.sin(a)), (s * 2.62, 14.6 + 2.9 * math.cos(a), 2.9 * math.sin(a)),
                       0.1, 0.1, sides=3, caps=(False, False))
        f += prism("spine", (s * 2.6, 14.6, 0), (s * 3.6, 14.6, 0), 0.6, 0.5, sides=8, caps=(True, True), hint=(0, 1, 0))
    _crystals(f, (0.4, 17.6, 0), 4, 0.8, 12, mat="main", spread=1.0)
    return f


def wild_blasting_axe(stage=0):
    """発破の斧: 黒い鉄の斧の刃の裏に、赤い発破の筒を三本縛った斧。導火線が柄に沿って螺旋に巻き、柄頭に火打ちの金具"""
    f = haft(-4.4, 12.4, r=0.85, mat="grip", wrap=(-3.6, 0.8))
    f += prism("spine", (0, 11.6, 0), (0, 15.2, 0), 1.1, 1.05, sides=8, caps=(True, True))
    f += extrude("main", [(0.8, 10.4), (3.8, 9.4), (5.8, 10.6), (6.4, 13.4), (5.8, 16.2), (3.8, 17.2), (0.8, 15.8)], 1.1)
    f += curve("edge", [(4.0, 9.6, 0), (5.8, 10.8, 0), (6.4, 13.4, 0), (5.8, 16.0, 0), (4.0, 17.0, 0)], 0.2, 0.55, sides=4, hint=(0, 0, 1))
    for dz in (-0.9, 0.0, 0.9):
        f += prism("cloth", (-1.4, 11.6, dz), (-1.4, 15.6, dz), 0.48, 0.48, sides=8, caps=(True, True))
    for y in (12.4, 14.8):
        f += prism("cord", (-1.4, y, -1.5), (-1.4, y, 1.5), 0.12, 0.12, sides=3) + curve("cord", [(-2.0, y, -1.4), (-2.0, y, 1.4)], 0.12, 0.12, sides=3)
    pts = [(0.9 * math.cos(t * 0.8 + 1), 15.6 - t * 0.9, 0.9 * math.sin(t * 0.8 + 1)) for t in range(16)]
    f += curve("cord", [(-1.4, 15.8, 0)] + pts, 0.1, 0.1, sides=3)
    f += block("acc", (0, -4.6, 0), (1.2, 0.8, 1.2))
    return f


def wild_lampeater_hammer(stage=0):
    """灯喰いの顎鎚: 頭が灯を呑んだ大きな顎の鎚。黒い顎の上下に牙が並び、閉じた歯の隙から腹の中の灯が琥珀に光る。打つ面は顎の額の骨、柄は錨鉄で喉の管が巻く"""
    f = haft(-4.6, 12.2, r=0.95, mat="grip", wrap=(-3.8, 0.8))
    f += spike(BONE, "trim", (0, -4.4, 0), (0, -1, 0), 1.6, 0.9, sides=8, rings=1)
    # 顎 (横に長い頭。口は +x)
    C = (0.6, 14.4, 0)
    f += ell("main", C, (3.6, 2.4, 2.1), segs=10, rings=4)
    f += ell("gem", (C[0] + 1.6, C[1], 0), (1.6, 1.0, 1.5), segs=8, rings=2)
    for row, sgn in ((C[1] + 0.9, -1), (C[1] - 0.9, 1)):
        for i in range(5):
            x = C[0] + 0.4 + i * 0.8
            f += spike(BONE, "spine", (x, row, -1.3 + 0.0 * i), (0, sgn, -0.25), 0.9, 0.24, sides=4, rings=1)
            f += spike(BONE, "spine", (x, row, 1.3), (0, sgn, 0.25), 0.9, 0.24, sides=4, rings=1)
    # 額の骨の打つ面 (-x)
    f += prism("acc", (-2.6, 14.4, 0), (-3.8, 14.4, 0), 1.8, 1.9, sides=8, caps=(True, True), hint=(0, 1, 0))
    for s in (-1, 1):
        f += ell("dark", (C[0] - 0.6, C[1] + 1.4, s * 1.4), (0.4, 0.3, 0.25), segs=6, rings=1)
        f += ell("gem", (C[0] - 0.6, C[1] + 1.45, s * 1.45), (0.18, 0.14, 0.12), segs=4, rings=1)
    pts = [(1.05 * math.cos(t * 0.9), 2.0 + t * 0.6, 1.05 * math.sin(t * 0.9)) for t in range(16)]
    f += curve("cloth", pts, 0.2, 0.2, sides=4)
    return f


def wild_bonepipe_staff(stage=0):
    """骨笛の杖: 長さの違う骨の管を束ねたパイプの杖。管の口が上を向いて斜めに切られ、束を鉄の帯が締める。柄頭は椎骨"""
    f = haft(-4.0, 12.6, r=0.75, mat="grip", wrap=(-2.4, 2.0))
    f += vertebra("main", (0, -4.4, 0), 1.0, 0.6, procs=4)
    pipes = [(0.0, 0.0, 9.0), (1.1, 0.4, 7.4), (-1.1, 0.4, 7.8), (0.6, -1.0, 6.2), (-0.6, -1.0, 6.6), (0.0, 1.2, 5.6)]
    for x, z, h in pipes:
        f += bone("main", [(x, 12.4, z), (x, 12.4 + h, z)], 0.5, knob=False)
        f += prism("dark", (x, 12.4 + h, z), (x, 12.5 + h, z), 0.35, 0.35, sides=6, caps=(True, True))
        f += ell("main", (x, 12.4 + h * 0.5, z), (0.62, 0.35, 0.62), segs=6, rings=1)
    for y in (13.4, 16.4):
        f += prism("spine", (0, y, 0), (0, y + 0.6, 0), 1.95, 1.95, sides=8, caps=(False, False))
    f += ell("gem", (0, 21.6, 0), (0.3, 0.3, 0.3), segs=5, rings=1)
    return f


def wild_salttear_staff(stage=0):
    """塩の涙の杖: 流木の籠に吊った、大きな塩の雫の結晶。雫の中に閉じた泡が光り、籠の木は白く塩をふく"""
    f = haft(-4.0, 13.6, r=0.78, mat="grip", wrap=(-2.4, 2.0))
    for k in range(4):
        a = math.radians(45 + 90 * k)
        f += curve("grip", [(0.4 * math.cos(a), 13.4, 0.4 * math.sin(a)), (2.4 * math.cos(a), 15.6, 2.4 * math.sin(a)),
                            (2.2 * math.cos(a), 19.0, 2.2 * math.sin(a)), (0.3 * math.cos(a), 21.8, 0.3 * math.sin(a))], [0.3, 0.26, 0.24, 0.2],
                   [0.3, 0.26, 0.24, 0.2], sides=5, caps=(True, True))
    f += ell("grip", (0, 22.0, 0), (0.6, 0.5, 0.6), segs=6, rings=1)
    # 雫 (上が尖る)
    f += ell("main", (0, 17.2, 0), (1.7, 1.9, 1.7), segs=10, rings=4)
    f += spike(BONE, "main", (0, 18.6, 0), (0, 1, 0), 2.4, 1.2, sides=8, rings=2)
    for p in ((0.4, 17.0, -1.2), (-0.5, 16.4, -1.3), (0.1, 17.8, -1.4)):
        f += ell("gem", p, (0.3, 0.3, 0.2), segs=5, rings=1)
    return f


def wild_compass_staff(stage=0):
    """探掘隊の羅針杖: 大きな羅針の盤を頭にした杖。真鍮の縁の円盤に方位の刻み、真ん中に深みを指して下を向く光る針、盤の上に灯の鉤"""
    f = haft(-4.0, 13.4, r=0.75, mat="grip", wrap=(-2.4, 2.0))
    f += prism("acc", (0, 13.2, 0), (0, 14.4, 0), 0.8, 1.0, sides=8, caps=(True, True))
    C = (0, 17.0, 0)
    f += prism("cloth", (0, 17.0, -0.3), (0, 17.0, 0.3), 2.6, 2.6, sides=16, caps=(True, True), hint=(1, 0, 0))
    f += torus("acc", C, 2.7, 0.3, 0.45, n=16, k=4, ax="z")
    for k in range(8):
        a = math.radians(45 * k)
        f += block("dark", (2.1 * math.cos(a), 17.0 + 2.1 * math.sin(a), -0.32), (0.18, 0.5 if k % 2 == 0 else 0.3, 0.05), rot=rot_z(45 * k - 90))
    f += extrude("gem", [(0, -2.0), (0.4, 0), (0, 0.9), (-0.4, 0)], 0.2, c=(0, 17.0, -0.45))
    f += ell("acc", (0, 17.0, -0.55), (0.3, 0.3, 0.15), segs=6, rings=1)
    f += curve("spine", [(0, 19.7, 0), (0, 20.8, 0), (1.0, 21.4, 0), (1.4, 20.6, 0)], 0.16, 0.16, sides=4)
    return f


def wild_mimic_heart_staff(stage=0):
    """鼓動もどきの心杖: 肋の骨の籠に、脈打つ偽の心臓を抱いた杖。赤黒い肉の心臓から管が柄へ這い降り、肋の先は爪のように内へ曲がる"""
    f = haft(-4.0, 13.0, r=0.8, mat="grip", wrap=(-2.6, 2.4))
    f += spike(BONE, "trim", (0, -3.8, 0), (0, -1, 0), 1.6, 0.8, sides=8, rings=1)
    C = (0, 17.2, 0)
    f += ell("cloth", C, (1.9, 2.3, 1.8), segs=10, rings=4)
    f += ell("cloth", (C[0] + 0.7, C[1] + 1.6, 0.2), (0.9, 0.8, 0.8), segs=6, rings=2)
    for p in ((0.6, 16.8, -1.6), (-0.8, 17.6, -1.5), (0.1, 15.6, -1.4)):
        f += ell("gem", p, (0.35, 0.35, 0.2), segs=5, rings=1)
    for k in range(6):
        a = math.radians(60 * k + 30)
        pts = [(0.6 * math.cos(a), 14.0, 0.6 * math.sin(a)), (2.6 * math.cos(a), 15.4, 2.6 * math.sin(a)), (2.8 * math.cos(a), 18.0, 2.8 * math.sin(a)),
               (1.6 * math.cos(a), 20.4, 1.6 * math.sin(a)), (0.6 * math.cos(a), 20.6, 0.6 * math.sin(a))]
        f += curve("main", pts, [0.32, 0.3, 0.27, 0.2, 0.06], [0.32, 0.3, 0.27, 0.2, 0.06], sides=5, caps=(True, True))
    f += prism("main", (0, 13.0, 0), (0, 14.2, 0), 0.9, 1.2, sides=8, caps=(True, True))
    for k in range(2):
        a = math.radians(140 + 120 * k)
        pts = [(1.0 * math.cos(a), 15.4, 1.0 * math.sin(a))] + [(0.95 * math.cos(a + t * 0.8), 13.0 - t * 1.2, 0.95 * math.sin(a + t * 0.8)) for t in range(7)]
        f += curve("cloth", pts, 0.22, 0.22, sides=4)
    return f


def wild_saltcrow_bow(stage=0):
    """塩鴉の弓: 塩の吹いた白い木に、鴉の黒い羽根を重ねた弓。腕の先は鴉の頭骨、握りの外に塩の結晶"""
    f = bow_grip("grip", "wrap")
    _crystals(f, (6.0, CY, 0), 3, 0.9, 14, mat="spine", up=(1, 0, 0), spread=0.5)
    up = bend(stage, [(5.0, CY + 2.4, 0), (4.5, CY + 5.2, 0), (3.2, CY + 7.9, 0), (1.2, CY + 9.9, 0), (-1.2, CY + 10.8, 0)])
    half = []
    half += curve("main", up, [0.9, 0.85, 0.74, 0.58, 0.4], [1.0, 0.95, 0.82, 0.64, 0.42], sides=5, caps=(False, True), hint=(0, 0, 1))
    for i in range(1, 4):
        (x0, y0, _), (x1, y1, _) = up[i], up[i + 1]
        dx, dy = x1 - x0, y1 - y0
        ln = math.hypot(dx, dy) or 1
        ux, uy = dx / ln, dy / ln
        half += plume((x0 + uy * 0.4, y0 - ux * 0.4, 0.1), (x0 + uy * 1.8 + ux * 2.6, y0 - ux * 1.8 + uy * 2.6, 0.1), 0.6, th=0.2, big=True,
                      mat="cloth", tipmat="cloth", scorch=0)
    tx, ty, _ = up[-1]
    half += ell("spine", (tx, ty, 0), (0.7, 0.6, 0.6), segs=6, rings=2)
    half += spike(BONE, "dark", (tx - 0.5, ty + 0.1, 0), (-1, 0.2, 0), 1.4, 0.3, sides=4, rings=1)
    f += half + mirror_faces_y(half)
    lo = [(x, 2 * CY - y, z) for x, y, z in up]
    return f + string_and_arrow(stage, "rare", up, lo)


def wild_bonestring_bow(stage=0):
    """骨弦の弓: 肋の骨を腕に、椎骨を握りにした弓。腕は二本の肋が並んで節で結ばれ、先は顎の骨の弭"""
    f = []
    for dy in (-2.0, -0.7, 0.7, 2.0):
        f += vertebra("main", (5.0, CY + dy, 0), 1.05, 0.9, procs=2, axis="y", ph=0.0)
    up = bend(stage, [(5.0, CY + 2.4, 0), (4.6, CY + 5.4, 0), (3.3, CY + 8.2, 0), (1.2, CY + 10.3, 0), (-1.2, CY + 11.2, 0)])
    half = []
    for dz in (-0.45, 0.45):
        half += curve("main", [(x, y, dz) for x, y, z in up], [0.55, 0.52, 0.46, 0.38, 0.26], [0.5, 0.48, 0.42, 0.34, 0.24], sides=5, caps=(False, True))
    for i in (1, 2, 3):
        half += ell("spine", up[i], (0.7, 0.5, 0.95), segs=6, rings=1)
    tx, ty, _ = up[-1]
    half += curve("main", [(tx, ty, 0), (tx + 0.4, ty + 1.0, 0), (tx + 1.4, ty + 1.2, 0)], [0.4, 0.3, 0.08], [0.5, 0.4, 0.1], sides=5)
    f += half + mirror_faces_y(half)
    lo = [(x, 2 * CY - y, z) for x, y, z in up]
    return f + string_and_arrow(stage, "rare", up, lo)


def wild_lantern_bow(stage=0):
    """灯籠の弓: 古い真鍮の金具の黒い弓。両の先に小さな灯籠が下がり、握りの外に灯の窓 (琥珀の硝子の小窓)"""
    f = bow_grip("grip", "wrap")
    f += block("acc", (6.3, CY, 0), (1.4, 2.4, 1.4))
    f += block("gem", (6.6, CY, 0), (1.0, 1.6, 1.1))
    up = bend(stage, [(5.0, CY + 2.4, 0), (4.6, CY + 5.4, 0), (3.3, CY + 8.2, 0), (1.3, CY + 10.2, 0), (-1.0, CY + 11.0, 0)])
    half = []
    half += curve("main", up, [0.9, 0.85, 0.74, 0.58, 0.4], [1.0, 0.95, 0.82, 0.64, 0.42], sides=6, caps=(False, True), hint=(0, 0, 1))
    for i in (1, 3):
        x, y, _ = up[i]
        half += ell("acc", (x, y, 0), (1.05, 0.6, 1.05), segs=6, rings=1)
    tx, ty, _ = up[-1]
    half += prism("cord", (tx, ty, 0), (tx, ty - 1.4, 0), 0.06, 0.06, sides=3, caps=(False, False))
    half += prism("acc", (tx, ty - 1.4, 0), (tx, ty - 1.8, 0), 0.3, 0.55, sides=6, caps=(True, True))
    half += prism("gem", (tx, ty - 1.8, 0), (tx, ty - 3.0, 0), 0.5, 0.5, sides=6, caps=(False, False))
    half += prism("acc", (tx, ty - 3.0, 0), (tx, ty - 3.3, 0), 0.55, 0.3, sides=6, caps=(True, True))
    f += half + mirror_faces_y(half)
    lo = [(x, 2 * CY - y, z) for x, y, z in up]
    return f + string_and_arrow(stage, "rare", up, lo)


def wild_hanged_longbow(stage=0):
    """逆さ吊りの長弓: 弦が逆さ (腕の外側) に張られたように見える長い黒い弓。腕から縄が何本も垂れ、先に絞首の結び目。握りは縄で幾重にも巻かれ、真ん中に淵の紫の目"""
    f = bow_grip("cord", "cord", r=1.1)
    f += ell("gem", (6.3, CY, 0), (0.6, 0.6, 0.4), segs=8, rings=2)
    f += torus("trim", (6.3, CY, 0), 0.8, 0.14, 0.3, n=10, k=4, ax="z")
    up = bend(stage, [(5.0, CY + 2.4, 0), (5.2, CY + 6.2, 0), (4.4, CY + 9.8, 0), (2.4, CY + 12.6, 0), (-0.6, CY + 14.0, 0)])
    half = []
    half += curve("main", up, [1.0, 0.92, 0.8, 0.62, 0.4], [1.05, 0.96, 0.84, 0.64, 0.42], sides=6, caps=(False, True), hint=(0, 0, 1))
    half += curve("cord", [(x + 0.9, y, 0) for x, y, z in up[1:]], 0.13, 0.13, sides=3)
    for i in (1, 2, 3):
        x, y, _ = up[i]
        L = 2.4 + 0.8 * i
        half += prism("cord", (x + 0.9, y, 0), (x + 1.3, y - L, 0), 0.12, 0.12, sides=3, caps=(False, False))
        half += torus("cord", (x + 1.3, y - L - 0.5, 0), 0.5, 0.12, 0.12, n=8, k=3, ax="z")
    tx, ty, _ = up[-1]
    half += ell("spine", (tx, ty, 0), (0.55, 0.55, 0.55), segs=6, rings=1)
    f += half + mirror_faces_y(half)
    lo = [(x, 2 * CY - y, z) for x, y, z in up]
    return f + string_and_arrow(stage, "legend", up, lo)


def wild_ropecut_knife(stage=0):
    """縄切りの鉤小刀: 縄を切る、内へ曲がった鉤の刃の小刀。刃の内の縁が刃、柄に縄の巻き、柄頭に吊り輪"""
    f = haft(-3.0, 2.4, r=0.7, mat="grip", wrap=(-2.6, 1.9), wmat="cord")
    f += torus("spine", (0, -3.8, 0), 0.7, 0.18, 0.18, n=8, k=4, ax="z")
    f += block("guard", (0, 2.7, 0), (2.4, 0.6, 1.2))
    st = []
    for y, w, cx in ((3.0, 0.9, 0.0), (6.0, 1.0, 0.4), (9.0, 0.95, 1.2), (11.0, 0.8, 2.4), (12.2, 0.5, 3.6), (12.4, 0.0, 4.4)):
        t = 0.36 if w else 0
        st.append((y, -w, w * 0.5, t, t, cx))
    f += blade("main", st, colmats=["edge", "main", "spine", "spine", "main", "edge"])
    return f


def wild_bone_needle_dagger(stage=0):
    """骨針の暗器: 長い骨の針の刺し刀。針に血の溝が三筋、鍔は小さな椎骨、柄は骨に黒い革、柄頭は指の骨"""
    f = haft(-3.2, 2.6, r=0.66, mat="grip", wrap=(-2.6, 2.0))
    f += bone("main", [(0, -3.2, 0), (0, -4.6, 0)], 0.45)
    f += vertebra("main", (0, 3.0, 0), 1.1, 0.7, procs=4)
    f += prism("main", (0, 3.4, 0), (0, 13.0, 0), 0.62, 0.3, sides=6, caps=(True, False))
    f += spike(BONE, "main", (0, 13.0, 0), (0, 1, 0), 2.4, 0.3, sides=6, rings=1)
    for k in range(3):
        a = math.radians(120 * k)
        f += prism("dark", (0.5 * math.cos(a), 4.6, 0.5 * math.sin(a)), (0.28 * math.cos(a), 12.0, 0.28 * math.sin(a)), 0.07, 0.07, sides=3, caps=(False, False))
    return f


def wild_ceiling_claw(stage=0):
    """逆さ歩きの鉤爪: 逆さ歩きの長い鉤の指を写した爪。関節が逆に曲がる三本の長い指が、甲から上へ伸びて先が手前へ鉤になる。甲は灰白の皮と錨鉄"""
    f = claw_hand()
    f += ell("spine", (0, -1.3, 0), (0.95, 0.6, 0.95), segs=6, rings=1)
    f += block("guard", (0, 5.0, 0), (4.6, 2.0, 2.2), taper=(0.85, 0.85))
    for x in (-1.6, 0.0, 1.6):
        pts = [(x, 5.8, 0), (x, 8.6, 0.8), (x * 1.1, 11.6, -0.4), (x * 1.1, 13.6, 0.6), (x * 1.0, 13.2, 1.8)]
        f += curve("main", pts, [0.5, 0.42, 0.36, 0.26, 0.06], [0.5, 0.42, 0.36, 0.26, 0.06], sides=5, caps=(False, True))
        for p in pts[1:3]:
            f += ell("main", p, (0.55, 0.5, 0.55), segs=6, rings=1)
    return f


def wild_snuffer_dagger(stage=0):
    """灯消しの短剣: 鍔が蝋燭消しの円錐 (帽子の形) の短剣。黒い刃の先に消えた灯の煙が一筋の光として残り、円錐の中は暗い。柄は黒い革に蝋の滴"""
    f = haft(-3.2, 2.4, r=0.7, mat="grip", wrap=(-2.6, 1.9))
    f += spike(BONE, "acc", (0, -3.1, 0), (0, -1, 0), 1.1, 0.7, sides=8, rings=1)
    # 円錐 (口は下)
    rings_ = [[V(rr * math.cos(math.radians(30 * j)), y, rr * math.sin(math.radians(30 * j))) for j in range(12)]
              for y, rr in ((2.6, 2.0), (3.0, 1.8), (4.2, 1.0), (5.0, 0.4))]
    f += loft(BONE, "acc", rings_, caps=("dark", "acc"))
    st = [(4.8, -0.8, 0.8, 0.36, 0.36), (8.0, -0.9, 0.9, 0.34, 0.34), (11.6, -0.6, 0.6, 0.28, 0.28), (14.4, 0, 0, 0, 0)]
    f += blade("main", st)
    f += gbar([(0, 9.4, -0.36), (0.3, 12.0, -0.3), (-0.2, 13.8, -0.2)], r=0.12)
    for y in (-1.0, 0.8):
        f += ell("cloth", (0.75, y, -0.1), (0.2, 0.35, 0.2), segs=4, rings=1)
    return f


def wild_mourning_bell_staff(stage=0):
    """弔いの手鈴杖: 探掘隊が失った人を弔う手鈴の杖。黒い杖の頭の横木に、大きな手鈴が一つ下がり、鈴から名を刻んだ札が三枚垂れる。頭に黒い布"""
    f = haft(-4.0, 16.0, r=0.75, mat="grip", wrap=(-2.4, 2.0))
    f += prism("grip", (-0.2, 15.4, 0), (3.2, 15.4, 0), 0.4, 0.38, sides=6, caps=(True, True), hint=(0, 1, 0))
    f += flag((-0.6, 16.4), 1.4, 3.6, z=-0.8, mat="cloth", tears=2, wave=0.3, th=0.12)
    f += bell((3.0, 14.8, 0), r=1.2, h=2.2, mat="acc")
    for dx, ln in ((-0.6, 2.2), (0.0, 3.0), (0.6, 2.6)):
        f += prism("cord", (3.0 + dx, 12.6, 0), (3.0 + dx, 12.6 - ln, 0), 0.06, 0.06, sides=3, caps=(False, False))
        f += block("soot", (3.0 + dx, 12.2 - ln, 0), (0.5, 0.9, 0.1))
    return f


def wild_saltpillar_totem(stage=0):
    """塩の柱の祭具: 塩の柱に人の顔が三つ浮かぶ祭具。白い六角の柱の三つの面に、目を閉じ口を結んだ顔。頂は崩れかけた塩の冠"""
    f = haft(-4.0, 12.6, r=0.8, mat="grip", wrap=(-2.4, 2.0))
    f += prism("main", (0, 12.4, 0), (0, 19.6, 0), 1.8, 1.6, sides=6, caps=(True, True))
    for k in range(3):
        a = math.radians(90 + 120 * k)
        d = (math.cos(a), math.sin(a))
        c = (d[0] * 1.6, 16.6, -d[1] * 1.6)
        f += ell("main", c, (0.8, 1.1, 0.5), segs=6, rings=2)
        # 閉じた目と結んだ口 (顔の前に細い暗い筋)
        out = V(d[0], 0, -d[1])
        side = V(d[1], 0, d[0])
        for s in (-1, 1):
            p0 = V(*c) + out * 0.5 + side * (0.32 * s) + V(0, 0.35, 0)
            f += gbar([tuple(p0 - side * 0.15), tuple(p0 + side * 0.15)], r=0.07, mat="dark")
        m0 = V(*c) + out * 0.5 + V(0, -0.4, 0)
        f += gbar([tuple(m0 - side * 0.22), tuple(m0 + side * 0.22)], r=0.07, mat="dark")
    _crystals(f, (0, 19.6, 0), 5, 1.2, 21, mat="main", spread=0.7)
    return f


def wild_knotted_bell_staff(stage=0):
    """縄結びの鈴杖: 結び目の並んだ縄を杖に巻き上げ、頭で大きな結び目の輪を作る。結び目ごとに小さな鈴、輪の真ん中に淵の紫の灯"""
    f = haft(-4.0, 14.0, r=0.72, mat="grip", wrap=(-2.4, 2.0))
    pts = [(0.85 * math.cos(t * 0.7), 2.0 + t * 0.6, 0.85 * math.sin(t * 0.7)) for t in range(20)]
    f += curve("cord", pts, 0.24, 0.24, sides=4)
    for t in (3, 8, 13, 18):
        x, y, z = pts[t]
        f += ell("cord", (x, y, z), (0.45, 0.45, 0.45), segs=6, rings=1)
        f += bell((x * 1.4, y - 0.4, z * 1.4), r=0.35, h=0.6, mat="acc")
    loop = [(2.2 * math.sin(math.radians(a)), 17.0 + 2.6 * math.cos(math.radians(a)), 0.3 * math.sin(math.radians(2 * a))) for a in range(-160, 181, 30)]
    f += curve("cord", loop, 0.3, 0.3, sides=4)
    f += ell("cord", (0, 14.4, 0), (0.8, 0.7, 0.8), segs=6, rings=2)
    f += ell("gem", (0, 17.0, 0), (0.8, 0.8, 0.8), segs=8, rings=2)
    return f


def wild_lullaby_bell_staff(stage=0):
    """深淵の子守鐘: 肋の籠の中に小さな礼拝堂の鐘を吊った杖。籠の上に揺り籠の弧が渡され、鐘の中に淵の紫の灯。肋に古い布のリボン"""
    f = haft(-4.0, 13.2, r=0.8, mat="grip", wrap=(-2.6, 2.4))
    f += spike(BONE, "trim", (0, -3.8, 0), (0, -1, 0), 1.6, 0.8, sides=8, rings=1)
    for k in range(6):
        a = math.radians(60 * k)
        pts = [(0.6 * math.cos(a), 13.0, 0.6 * math.sin(a)), (2.6 * math.cos(a), 15.0, 2.6 * math.sin(a)), (2.6 * math.cos(a), 18.4, 2.6 * math.sin(a)),
               (0.6 * math.cos(a), 20.4, 0.6 * math.sin(a))]
        f += curve("main", pts, [0.3, 0.28, 0.26, 0.22], [0.3, 0.28, 0.26, 0.22], sides=5, caps=(True, True))
    f += ell("main", (0, 20.6, 0), (0.8, 0.5, 0.8), segs=6, rings=1)
    # 揺り籠の弧
    arc_ = [(3.6 * math.sin(math.radians(a)), 21.6 - 1.4 * math.cos(math.radians(a)) * 0 - 1.0 * (1 - math.cos(math.radians(a))), 0) for a in range(-70, 71, 20)]
    f += curve("acc", arc_, 0.3, 0.3, sides=5)
    f += prism("cord", (0, 20.4, 0), (0, 19.0, 0), 0.08, 0.08, sides=3, caps=(False, False))
    f += bell((0, 19.0, 0), r=1.2, h=2.6, mat="acc", mouth="gem")
    f += sash([(2.6, 18.0, 0.3), (3.4, 16.6, 0.4), (3.6, 14.6, 0.5)], 0.6, mat="cloth")
    return f


WEAPONS = dict([
    W(wild_delver_sword, "探掘隊の柄長剣", "rare", "sword", "lamp",
      dict(main="steel", edge="steel", guard="brass", acc="brass", grip="leather", wrap="rope", cord="rope", dark="dark")),
    W(wild_saltcrystal_greatsword, "塩晶の大剣", "epic", "greatsword", "salt",
      dict(main="salt", edge="glass", guard="blacksteel", grip="rope", cord="salt")),
    W(wild_rope_halberd, "縄打ちの鉤槍", "epic", "glaive", "void",
      dict(main="blacksteel", edge="steel", spine="iron", grip="darkwood", cord="rope")),
    W(wild_inverted_greatsword, "逆さ歩きの天剣", "heroic", "greatsword", "void",
      dict(main="meteor", edge="silver", acc="bone", spine="bone", grip="blackleather", wrap="leather", cord="rope"),
      inlay=inlay_line(["trim"], 99.0, 99.5)),
    W(wild_pick_hammer, "探掘隊のつるはし鎚", "rare", "hammer", "lamp",
      dict(main="iron", edge="steel", spine="brass", grip="oak", cord="rope", wrap="rope")),
    W(wild_saltmill_maul, "岩塩の臼鎚", "epic", "maul", "salt",
      dict(main="salt", spine="blacksteel", grip="darkwood", wrap="rope")),
    W(wild_blasting_axe, "発破の斧", "epic", "axe", "ember",
      dict(main="blacksteel", edge="steel", spine="iron", cloth="redcloth", cord="rope", acc="brass", grip="darkwood", wrap="leather")),
    W(wild_lampeater_hammer, "灯喰いの顎鎚", "heroic", "hammer", "lamp",
      dict(main="blacksteel", spine="bone", acc="bone", cloth="flesh", grip="blacksteel", wrap="blackleather"),
      inlay=inlay_line(["trim"], 99.0, 99.5)),
    W(wild_bonepipe_staff, "骨笛の杖", "rare", "staff", "void",
      dict(main="bone", spine="iron", grip="darkwood", wrap="leather")),
    W(wild_salttear_staff, "塩の涙の杖", "epic", "staff", "salt",
      dict(main="salt", grip="driftwood", wrap="rope")),
    W(wild_compass_staff, "探掘隊の羅針杖", "epic", "staff", "lamp",
      dict(acc="brass", cloth="paper", spine="iron", grip="darkwood", wrap="leather")),
    W(wild_mimic_heart_staff, "鼓動もどきの心杖", "heroic", "staff", "heart",
      dict(main="bone", cloth="flesh", grip="blacksteel", wrap="blackleather"),
      inlay=inlay_line(["trim"], 99.0, 99.5)),
    W(wild_saltcrow_bow, "塩鴉の弓", "rare", "bow", "salt",
      dict(main="driftwood", spine="salt", cloth="blackleather", grip="leather", wrap="leather")),
    W(wild_bonestring_bow, "骨弦の弓", "epic", "bow", "void",
      dict(main="bone", spine="leather")),
    W(wild_lantern_bow, "灯籠の弓", "epic", "bow", "lamp",
      dict(main="darkwood", acc="brass", cord="rope", grip="leather", wrap="leather")),
    W(wild_hanged_longbow, "逆さ吊りの長弓", "heroic", "longbow", "void",
      dict(main="blacksteel", cord="rope", spine="iron")),
    W(wild_ropecut_knife, "縄切りの鉤小刀", "rare", "dagger", "lamp",
      dict(main="steel", edge="steel", spine="iron", guard="brass", grip="oak", cord="rope")),
    W(wild_bone_needle_dagger, "骨針の暗器", "epic", "dagger", "heart",
      dict(main="bone", grip="bone", wrap="blackleather")),
    W(wild_ceiling_claw, "逆さ歩きの鉤爪", "epic", "claw", "void",
      dict(main="bone", guard="blacksteel", spine="iron", grip="blackleather", wrap="leather")),
    W(wild_snuffer_dagger, "灯消しの短剣", "heroic", "dagger", "void",
      dict(main="blacksteel", edge="iron", acc="brass", cloth="paper", grip="blackleather", wrap="leather"),
      inlay=inlay_line(["trim"], 99.0, 99.5)),
    W(wild_mourning_bell_staff, "弔いの手鈴杖", "rare", "bell", "lamp",
      dict(grip="darkwood", wrap="leather", cloth="blackleather", acc="bronze", cord="rope", soot="paper")),
    W(wild_saltpillar_totem, "塩の柱の祭具", "epic", "totem", "salt",
      dict(main="salt", grip="driftwood", wrap="rope")),
    W(wild_knotted_bell_staff, "縄結びの鈴杖", "epic", "bell", "void",
      dict(grip="darkwood", wrap="leather", cord="rope", acc="brass")),
    W(wild_lullaby_bell_staff, "深淵の子守鐘", "heroic", "bell", "void",
      dict(main="bone", acc="bronze", cord="rope", cloth="purplecloth", grip="darkwood", wrap="leather"),
      inlay=inlay_line(["trim"], 99.0, 99.5)),
])
