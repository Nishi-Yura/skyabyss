"""岩鯨 (一章) の一品一案。章の職の武器 4 本 (エピック) と、岩鯨の伝説 5 本。
塗りは岩鯨 (../paint.py): main/edge = 鯨骨、spine/guard/grip = 石、wrap/acc = 苔、gem = 水色の光。伝説は縁が金 (trim)、淵の欠片 (void)。
豪華さ: 章の 4 本は巨神の武器 (鯨骨の大剣・船長の矛) と同じ段 (~400 面)。伝説はそれより一段上 (~800 面、金の縁と淵の欠片)。
"""
from __future__ import annotations

import math

from kit_one import *  # noqa: F401,F403
from kit_one import legend_finish, ell, lump, bone, vertebra, strand, ringy, gbar, gem, arc, bend, mirror_faces_y, string_and_arrow, inlay_line, CY


# ================================================================== 章の武器 (エピック)
def whale_staff(stage=0):
    """鯨骨の星杖: 一本の長い肋骨の杖。先で三本の肋が指のように反り上がり、大きな水色の石を抱く。根には椎骨の襟と苔の房"""
    f = []
    # 柄: わずかに S に反る一本の肋骨。下は苔の縄の握りと石の石突き
    f += bone("main", [(0, -3.6, 0), (0.25, 2.0, 0), (-0.2, 8.0, 0), (0.15, 13.6, 0)], [0.95, 0.9, 0.85, 0.8], knob=False)
    f += ell("edge", (0, 13.8, 0), (1.25, 0.9, 1.2))
    f += prism("wrap", (0.1, -1.2, 0), (0.1, 3.4, 0), 1.12, 1.1, sides=6, caps=(False, False))
    for y in (-0.6, 0.9, 2.4):
        f += ringy("cord", y, 1.2, 0.35, cx=0.1)
    f += lump("guard", (0, -4.2, 0), (1.3, 1.2, 1.3), seed=3)
    f += spike(BONE, "guard", (0, -4.8, 0), (0, -1, 0), 1.6, 0.8, sides=5, rings=1, hint=(0, 0, 1))
    # 椎骨の襟
    f += vertebra("spine", (0, 14.6, 0), 1.7, 1.0, procs=5, plen=1.3)
    f += ringy("trim", 15.3, 1.3, 0.5)
    # 三本の肋 (120 度ごと、外へ開いてから内へ戻る指)
    for k in range(3):
        a = math.radians(90 + 120 * k)
        ca, sa = math.cos(a), math.sin(a)
        pts = [(1.0 * ca, 15.2, 1.0 * sa), (2.7 * ca, 17.2, 2.7 * sa), (2.9 * ca, 19.8, 2.9 * sa), (1.6 * ca, 22.0, 1.6 * sa), (0.6 * ca, 22.6, 0.6 * sa)]
        f += curve("edge", pts, [0.55, 0.5, 0.45, 0.35, 0.15], [0.7, 0.62, 0.55, 0.4, 0.15], sides=4, caps=(False, True), hint=(0, 1, 0))
        f += ell("main", (2.7 * ca, 17.2, 2.7 * sa), (0.75, 0.65, 0.75), segs=5, rings=1)
    # 抱かれた星の石 (縦長の八面の結晶) と、下の受けの石
    f += gem((0, 19.6, 0), 1.75, 2.5, 1.75, segs=6, rings=2)
    f += lump("guard", (0, 16.6, 0), (1.3, 0.8, 1.3), seed=5, segs=6, rings=2)
    # 苔の房と、縄に吊るした骨の護符
    for x, z in ((1.2, -0.9), (-1.3, 0.5)):
        f += strand("acc", (x, 14.2, z), 2.6, 0.5, r=0.32)
    f += charm(-1.6, 14.0, 2.2, mat="edge", z=-0.4)
    f += barnacles((0.85, 6.0, -0.6), n=3, spread=0.5, r=0.3, h=0.55, dirv=(0.5, 0, -1), mat="guard")
    return f


def whale_bell_staff(stage=0):
    """鯨歌いの鈴杖: 柄は積んだ椎骨の柱。先は海から立ち上がる鯨の尾鰭で、右の鰭先から苔むした石の鐘、左から骨の錘が下がる"""
    f = []
    f += prism("grip", (0, -4.0, 0), (0, 12.8, 0), 0.62, 0.62, sides=6, caps=(True, True))
    f += prism("wrap", (0, -1.4, 0), (0, 3.0, 0), 1.05, 1.05, sides=6, caps=(False, False))
    f += lump("guard", (0, -4.4, 0), (1.15, 1.0, 1.15), seed=7)
    # 椎骨の柱 (上ほど小さく、突起が交互に回る)
    for i, y in enumerate((4.2, 6.1, 7.9, 9.6, 11.2)):
        f += vertebra("main", (0, y, 0), 1.35 - 0.07 * i, 1.1, procs=3, plen=0.9, ph=30 + 60 * (i % 2))
    # 尾鰭: 柄の先の尾の付け根 (細る) と、左右へ反り上がる二枚の鰭 (正面を向く板)
    f += curve("main", [(0, 12.4, 0), (0, 14.4, 0), (0, 16.0, 0)], [1.0, 0.75, 0.55], [0.9, 0.6, 0.45], sides=6, caps=(False, False))
    for s in (1, -1):
        f += extrude("main", [(0, 15.4), (s * 2.6, 16.6), (s * 5.2, 19.2), (s * 4.0, 18.0), (s * 1.8, 17.4), (0, 17.6)][::s], 0.8, c=(0, 0, 0))
        f += extrude("edge", [(s * 4.0, 18.0), (s * 5.2, 19.2), (s * 5.6, 20.4), (s * 4.6, 19.0)][::s], 0.6, c=(0, 0, 0))
    f += ell("edge", (0, 17.0, 0), (0.9, 0.9, 0.7))
    f += barnacles((-1.6, 16.6, -0.4), n=3, spread=0.45, r=0.28, h=0.5, mat="guard")
    # 右: 石の鐘 (苔むして藤壺つき)
    f += prism("cord", (4.4, 18.6, 0), (4.4, 15.4, 0), 0.22, 0.22, sides=4, caps=(False, False))
    rings = [[V(4.4 + rr * math.cos(math.radians(22.5 + 45 * k)), y, rr * math.sin(math.radians(22.5 + 45 * k))) for k in range(8)]
             for y, rr in ((11.2, 2.1), (11.7, 1.95), (13.6, 1.5), (15.2, 0.95), (15.6, 0.5))]
    f += loft(BONE, "guard", rings, caps=("dark", "guard"))
    f += prism("acc", (4.4, 11.0, 0), (4.4, 11.6, 0), 2.25, 2.15, sides=8, caps=(True, True))
    f += gem((4.4, 10.8, 0), 0.6, 0.6, 0.6, segs=5, rings=1)
    for k in range(3):
        a = math.radians(40 + 120 * k)
        f += strand("acc", (4.4 + 2.0 * math.cos(a), 11.2, 2.0 * math.sin(a)), 1.4 + 0.5 * k, 0.3, r=0.25)
    # 左: 骨の錘
    f += prism("cord", (-4.4, 18.6, 0), (-4.4, 16.0, 0), 0.2, 0.2, sides=4, caps=(False, False))
    f += ell("edge", (-4.4, 15.2, 0), (0.7, 1.1, 0.7), segs=5, rings=2)
    f += ringy("trim", 3.2, 1.2, 0.5)
    return f


def whale_hammer(stage=0):
    """岩鯨の石鎚: 背の大石を二本の肋が万力のように挟み、苔の縄で締めた鎚。打つ面は平ら、反対は鯨の歯の突き"""
    f = []
    f += bone("main", [(0, -3.4, 0), (0, 4.0, 0), (0, 10.4, 0)], [0.9, 0.85, 0.8], knob=False)
    f += prism("wrap", (0, -2.6, 0), (0, 2.4, 0), 1.1, 1.1, sides=6, caps=(False, False))
    for y in (-2.0, -0.4, 1.2):
        f += ringy("cord", y, 1.18, 0.35)
    f += lump("guard", (0, -3.9, 0), (1.2, 1.0, 1.2), seed=11)
    # 大石の頭 (x へ長い)、打つ面 (+x) は平らな板
    f += lump("guard", (0.6, 12.2, 0), (3.4, 2.4, 2.1), seed=13, segs=8, rings=3, rough=0.14)
    f += block("spine", (3.9, 12.2, 0), (0.9, 3.6, 3.4), taper=(1.0, 1.0))
    f += block("trim", (3.4, 12.2, 0), (0.4, 4.0, 3.8))
    # 反対の鯨の歯
    f += spike(BONE, "edge", (-2.4, 12.4, 0), (-1, 0.15, 0), 3.4, 1.2, sides=5, rings=2, bend=(0, 0.5, 0), hint=(0, 1, 0))
    # 二本の肋の万力 (前後から石を抱く)
    for z in (-1.9, 1.9):
        f += curve("main", [(0, 9.6, z * 0.4), (-0.6, 11.0, z), (-0.2, 13.6, z * 1.05), (1.2, 14.8, z * 0.9), (2.4, 14.6, z * 0.6)],
                   [0.55, 0.5, 0.5, 0.45, 0.3], [0.6, 0.55, 0.55, 0.5, 0.3], sides=4, caps=(False, True), hint=(0, 0, 1))
    # 苔の縄 (石に巻く二本)
    for x in (-0.6, 1.6):
        f += prism("acc", (x, 12.2, -2.3), (x, 12.2, 2.3), 0.4, 0.4, sides=4, caps=(True, True), hint=(0, 1, 0))
        f += prism("acc", (x, 9.8, 0), (x, 14.6, 0), 0.38, 0.38, sides=4, caps=(True, True))
    f += barnacles((1.2, 13.8, -1.6), n=4, spread=0.7, r=0.32, h=0.6, mat="spine")
    f += strand("acc", (-0.6, 10.0, -1.6), 2.0, 0.3, r=0.25)
    f += gem((0.6, 12.3, -2.05), 0.55, 0.55, 0.3, segs=5, rings=1)
    return f


def whale_bow(stage=0):
    """鯨肋の弓: 握りは椎骨。腕は太い肋に細い肋を沿わせて苔の縄で縛った重ね腕。先は骨の鉤、握りの脇に藤壺"""
    f = []
    f += vertebra("guard", (5.0, CY, 0), 1.35, 2.2, procs=3, plen=1.0, axis="y", ph=0)
    f += prism("grip", (5.0, CY - 2.8, 0), (5.0, CY + 2.8, 0), 1.0, 1.0, sides=6, caps=(False, False), hint=(0, 0, 1))
    f += prism("wrap", (5.0, CY - 1.2, 0), (5.0, CY + 1.2, 0), 1.2, 1.2, sides=6, caps=(True, True), hint=(0, 0, 1))
    up = bend(stage, [(5.0, CY + 2.4, 0), (4.3, CY + 5.0, 0), (2.8, CY + 7.6, 0), (0.6, CY + 9.6, 0), (-1.8, CY + 10.6, 0)])
    half = []
    half += curve("main", up, [1.15, 1.05, 0.9, 0.7, 0.45], [1.25, 1.1, 0.95, 0.75, 0.5], sides=5, caps=(False, True), hint=(0, 0, 1))
    thin = [(x + 0.9, y - 0.3, 0.0) for x, y, z in up[:4]]
    half += curve("edge", thin, [0.45, 0.42, 0.38, 0.2], [0.5, 0.45, 0.4, 0.2], sides=4, caps=(True, True), hint=(0, 0, 1))
    for i in (1, 2):
        x, y, _ = up[i]
        half += prism("acc", (x + 0.4, y - 0.4, 0), (x + 0.4, y + 0.4, 0), 1.45, 1.4, sides=6, caps=(True, True), hint=(0, 0, 1))
        half += ell("main", (x - 0.6, y + 0.2, 0), (0.75, 0.75, 0.8), segs=5, rings=1)
    tx, ty, _ = up[-1]
    half += spike(BONE, "edge", (tx, ty, 0), (-0.4, -1, 0), 1.6, 0.45, sides=4, rings=1, hint=(0, 0, 1))
    half += barnacles((5.9, CY + 3.0, -0.4), n=3, spread=0.4, r=0.26, h=0.45, dirv=(1, 0, -0.4), mat="guard")
    # 肋の籠: 腕の背から外 (+x) へ短く反る小肋が三本 (胸郭の輪郭)
    for i, L in ((1, 2.6), (2, 2.2), (3, 1.6)):
        x, y, _ = up[i]
        half += spike(BONE, "main", (x + 0.6, y, 0), (1.0, 0.55, 0), L, 0.42, sides=4, rings=2, bend=(0, 0.6, 0), hint=(0, 0, 1))
    f += half + mirror_faces_y(half)
    lo = [(x, 2 * CY - y, z) for x, y, z in up]
    f += gem((6.3, CY, 0), 0.55, 0.9, 0.7, segs=5, rings=1)
    f += strand("acc", (4.2, CY - 1.0, -0.9), 2.4, 0.3, r=0.25)
    return f + string_and_arrow(stage, "rare", up, lo)


# ================================================================== 伝説
def abyss_whale_hammer(stage=0):
    """背骨打ちの鎚: 岩鯨の椎骨ひとつが頭。椎体の両面が打つ面で、金の輪と淵の核。上へ神経棘が帆のように立ち、左右に横突起。柄は金の帯の肋"""
    f = []
    f += bone("main", [(0, -4.2, 0), (0, 3.0, 0), (0, 10.8, 0)], [1.0, 0.92, 0.85], knob=False)
    f += prism("wrap", (0, -3.2, 0), (0, 2.2, 0), 1.18, 1.18, sides=8, caps=(False, False))
    for y in (-3.4, -0.5, 2.4, 6.4):
        f += ringy("trim", y, 1.3 if y < 3 else 1.05, 0.45)
    f += vertebra("guard", (0, -5.0, 0), 1.3, 1.0, procs=4, plen=0.9)
    f += gem((0, -6.1, 0), 0.6, 0.7, 0.6, segs=5, rings=1)
    # 椎体 (x 向きの太い円筒)。両端に金の輪と淵の核
    f += prism("main", (-3.6, 12.6, 0), (3.6, 12.6, 0), 2.6, 2.6, sides=10, caps=(True, True), hint=(0, 1, 0))
    for s in (1, -1):
        f += prism("trim", (s * 3.3, 12.6, 0), (s * 3.9, 12.6, 0), 2.85, 2.85, sides=10, caps=(True, True), hint=(0, 1, 0))
        f += prism("void", (s * 3.9, 12.6, 0), (s * 4.15, 12.6, 0), 1.7, 1.7, sides=10, caps=(True, True), hint=(0, 1, 0))
        f += prism("gem", (s * 4.1, 12.6, 0), (s * 4.3, 12.6, 0), 0.7, 0.7, sides=6, caps=(True, True), hint=(0, 1, 0))
        # 横突起 (前後へ反る翼)
    for z in (-1, 1):
        f += spike(BONE, "edge", (0, 12.6, z * 2.2), (0, -0.25, z), 3.2, 1.0, sides=4, rings=2, bend=(0, -0.6, 0), hint=(1, 0, 0))
    # 神経弓 (椎体の上の環) と神経棘の帆
    f += torus("edge", (0, 15.2, 0), 1.3, 0.45, 0.5, n=8, k=4, ax="z")
    f += prism("void", (0, 15.2, -0.3), (0, 15.2, 0.3), 0.9, 0.9, sides=8, caps=(True, True), hint=(1, 0, 0))
    f += extrude("main", [(-1.6, 15.6), (1.6, 15.6), (2.2, 18.4), (0.8, 21.6), (-0.4, 20.2), (-1.2, 17.6)], 0.9, c=(0, 0, 0))
    f += gbar([(0.2, 16.4, -0.5), (0.5, 18.4, -0.5), (0.6, 20.2, -0.5)])
    f += extrude("trim", [(0.8, 21.6), (1.3, 22.4), (0.2, 21.2)], 0.6, c=(0, 0, 0))
    # 下の関節突起 (柄を抱く)
    for x in (-1.3, 1.3):
        f += spike(BONE, "edge", (x, 10.6, 0), (x * 0.3, -1, 0), 2.2, 0.6, sides=4, rings=1, hint=(0, 0, 1))
    f += barnacles((-1.6, 13.8, -2.3), n=4, spread=0.7, r=0.3, h=0.55, mat="guard")
    f += barnacles((1.8, 11.0, 2.3), n=3, spread=0.6, r=0.3, h=0.55, dirv=(0, 0, 1), mat="guard")
    f += strand("acc", (2.0, 10.6, -1.8), 2.6, 0.4, r=0.3)
    f += strand("acc", (-2.4, 10.8, 1.5), 2.0, 0.3, r=0.26)
    return f


def abyss_whale_staff(stage=0):
    """鯨眼の杖: 沈んだ鯨の大きな目を、上下の石の瞼が抱く。瞼の縁から骨のまつ毛、目の下に涙の雫の玉。柄は顎の骨に金の細工"""
    f = []
    f += bone("main", [(0, -4.0, 0), (0.3, 3.0, 0), (0.0, 9.0, 0), (-0.3, 14.0, 0)], [0.95, 0.9, 0.85, 0.95], knob=False)
    f += prism("wrap", (0.15, -1.6, 0), (0.15, 3.2, 0), 1.12, 1.12, sides=8, caps=(False, False))
    for y in (-1.8, 3.4, 7.0, 10.6, 13.2):
        f += ringy("trim", y, 1.15, 0.4, cx=0.1)
    f += gbar([(0.1, 5.0, -0.85), (0.0, 7.0, -0.85), (-0.1, 9.0, -0.85)], r=0.22)
    f += lump("guard", (0, -4.4, 0), (1.25, 1.05, 1.25), seed=17)
    f += gem((0, -5.5, 0), 0.6, 0.7, 0.6, segs=5, rings=1, mat="void")
    # 眼窩の受け (石の環) と目
    f += torus("guard", (0, 18.0, 0), 3.1, 0.9, 1.1, n=10, k=4, ax="z")
    f += ell("void", (0, 18.0, 0.3), (2.5, 2.5, 1.5), segs=8, rings=3)
    f += ell("gem", (0, 18.0, -1.0), (1.65, 1.65, 0.7), segs=8, rings=2)
    f += ell("dark", (0, 18.0, -1.55), (0.55, 1.15, 0.3), segs=6, rings=1)
    # 上下の瞼 (反った石の殻)
    for s in (1, -1):
        rings = []
        for i, a in enumerate((-60, -30, 0, 30, 60)):
            rr = 3.0
            ca = math.radians(90 * s + a * s)
            x, y = rr * math.cos(ca), 18.0 + rr * math.sin(ca)
            rings.append([V(x, y, -1.6), V(x * 1.15, y + s * 0.6, -0.2), V(x * 1.1, y + s * 0.5, 1.0), V(x * 0.92, y - s * 0.2, 0.2)])
        f += loft(BONE, "spine", rings, caps=("spine", "spine"))
        # まつ毛の骨
        for a in (-50, -20, 10, 40):
            ca = math.radians(90 * s + a * s)
            p = (3.5 * math.cos(ca), 18.0 + 3.5 * math.sin(ca), -0.8)
            f += spike(BONE, "edge", p, (math.cos(ca), math.sin(ca), -0.25), 1.8, 0.32, sides=4, rings=1, hint=(0, 0, 1))
    f += extrude("trim", [(-0.5, 21.6), (0.5, 21.6), (0.0, 23.0)], 0.5, c=(0, 0, -0.6))
    # 涙の雫 (金の鎖に下がる水色の玉)
    for x, d in ((-1.4, 2.0), (1.4, 2.8)):
        f += prism("trim", (x, 15.2, -0.8), (x, 15.2 - d, -0.8), 0.15, 0.15, sides=4, caps=(False, False))
        f += gem((x, 14.6 - d, -0.8), 0.5, 0.8, 0.5, segs=5, rings=1)
    # 首: 顎の関節と苔
    f += ell("edge", (-0.3, 14.6, 0), (1.5, 1.0, 1.4), segs=6, rings=2)
    f += strand("acc", (1.2, 14.4, 0.8), 2.4, 0.4, r=0.3)
    f += barnacles((-2.6, 16.4, -0.9), n=3, spread=0.4, r=0.28, h=0.5, mat="guard")
    return f


def abyss_whale_bow(stage=0):
    """鯨ひげの弓: 腕は鯨のひげ板を何枚も重ねた櫛。弦の側にひげの房が並び、金の帯で束ねる。握りは金を巻いた骨と淵の核"""
    f = []
    f += prism("grip", (5.0, CY - 2.8, 0), (5.0, CY + 2.8, 0), 1.05, 1.05, sides=8, caps=(False, False), hint=(0, 0, 1))
    f += prism("trim", (5.0, CY - 1.6, 0), (5.0, CY + 1.6, 0), 1.25, 1.25, sides=8, caps=(True, True), hint=(0, 0, 1))
    f += ell("void", (6.1, CY, 0), (0.8, 1.4, 0.9), segs=6, rings=2)
    f += gem((6.7, CY, 0), 0.45, 0.7, 0.55, segs=5, rings=1)
    up = bend(stage, [(5.0, CY + 2.4, 0), (4.6, CY + 5.0, 0), (3.4, CY + 7.6, 0), (1.4, CY + 9.6, 0), (-1.0, CY + 10.8, 0)])
    half = []
    # 背の骨 (細い)
    half += curve("edge", up, [0.55, 0.5, 0.45, 0.38, 0.25], [0.7, 0.65, 0.55, 0.45, 0.3], sides=4, caps=(False, True), hint=(0, 0, 1))
    # ひげ板: 背から弦の側へ下がる薄い板 (広い面は ±Z)。先ほど短い
    for i in range(9):
        u = 0.08 + 0.1 * i
        k = min(int(u * 4), 3)
        t = u * 4 - k
        p = [up[k][j] + (up[k + 1][j] - up[k][j]) * t for j in range(3)]
        L = 2.6 * (1 - 0.6 * u)
        tang = V(up[k + 1][0] - up[k][0], up[k + 1][1] - up[k][1], 0)
        nrm_in = V(-tang[1], tang[0], 0) / max(1e-6, float(np.linalg.norm(tang)))
        if nrm_in[0] > 0:
            nrm_in = -nrm_in
        tip = V(*p) + nrm_in * L + V(0, -0.3, 0)
        half += feather("main", tuple(p), tuple(tip), 0.55, th=0.32, big=False)
    # 金の帯と房
    for i in (1, 3):
        x, y, _ = up[i]
        half += prism("trim", (x, y - 0.35, 0), (x, y + 0.35, 0), 0.95, 0.95, sides=6, caps=(True, True), hint=(0, 0, 1))
    tx, ty, _ = up[-1]
    half += ell("edge", (tx, ty, 0), (0.55, 0.55, 0.55), segs=5, rings=1)
    half += extrude("trim", [(0, 0), (0.9, 0.5), (0.3, 1.2)], 0.4, c=(tx, ty, 0))
    f += half + mirror_faces_y(half)
    lo = [(x, 2 * CY - y, z) for x, y, z in up]
    f += strand("acc", (5.6, CY - 2.0, -0.9), 2.6, 0.3, r=0.25)
    f += barnacles((5.9, CY + 2.2, 0.6), n=3, spread=0.35, r=0.24, h=0.4, dirv=(1, 0, 0.5), mat="spine")
    return f + string_and_arrow(stage, "legend", up, lo)


def abyss_whale_fang(stage=0):
    """古銛の牙: 岩鯨の背に刺さったままだった銛先。返しの大きな平たい刃、刃に藤壺。根は鯨の骨の塊に食い込み、柄は縄を巻いた骨、金の輪"""
    f = []
    f += bone("main", [(0, -3.0, 0), (0, 0.0, 0), (0, 3.0, 0)], [0.8, 0.75, 0.8], knob=False)
    f += prism("cord", (0, -2.4, 0), (0, 2.4, 0), 0.98, 0.98, sides=6, caps=(False, False))
    for y in (-1.6, -0.4, 0.8, 2.0):
        f += ringy("cord", y, 1.08, 0.35)
    f += ringy("trim", -2.7, 1.1, 0.4)
    f += ringy("trim", 2.8, 1.1, 0.4)
    f += ell("guard", (0, -3.6, 0), (1.0, 0.9, 1.0), segs=6, rings=2)
    f += gem((0, -4.4, 0), 0.45, 0.5, 0.45, segs=5, rings=1, mat="void")
    # 根: 鯨骨の塊 (銛が食い込んだ肉の骨)
    f += lump("edge", (0, 4.2, 0), (1.8, 1.4, 1.5), seed=23, segs=7, rings=2)
    f += prism("spine", (0, 3.6, 0), (0, 6.2, 0), 0.7, 0.55, sides=6, caps=(False, False))
    # 銛先: 平たい矢じり + 大きな返し (片側)
    f += blade("main", [(5.6, -1.6, 1.6, 0.45, 0.45), (8.4, -1.8, 1.8, 0.4, 0.4), (11.2, -0.9, 1.0, 0.3, 0.3), (13.0, 0, 0, 0, 0)])
    # 大きな返し (左は長く反った鉤、右は短い)
    f += extrude("edge", [(-1.0, 6.0), (-1.2, 8.6), (-2.6, 6.6), (-3.8, 3.6), (-2.4, 4.6)], 0.55, c=(0, 0, 0))
    f += extrude("edge", [(1.0, 6.0), (2.4, 4.8), (3.0, 3.8), (1.6, 7.6)][::-1], 0.55, c=(0, 0, 0))
    f += extrude("trim", [(-3.8, 3.6), (-3.2, 3.9), (-3.5, 3.1)], 0.6, c=(0, 0, 0))
    f += barnacles((0.7, 8.8, -0.42), n=3, spread=0.4, r=0.24, h=0.35, mat="guard")
    # 刺さっていた名残の、ほどけた縄と金の環
    f += torus("trim", (0, 4.9, 0), 1.25, 0.25, 0.3, n=8, k=4, ax="y")
    f += curve("cord", [(1.2, 4.6, -0.6), (2.2, 3.0, -0.8), (2.0, 1.0, -0.9), (2.6, -0.6, -0.8)], [0.24, 0.22, 0.2, 0.12], [0.24, 0.22, 0.2, 0.12], sides=4, caps=(True, True))
    f += strand("acc", (-1.4, 4.0, 0.6), 1.8, 0.3, r=0.24)
    return f


def abyss_whale_bell_staff(stage=0):
    """耳骨の鈴杖: 柄は鯨の下顎の骨 (上ほど幅広く、歯が並ぶ)。顎の関節から金の鎖で、巻いた貝のような耳の骨が鈴として下がり、水色の音の輪をまとう"""
    f = []
    f += prism("grip", (0, -4.0, 0), (0, 3.0, 0), 0.95, 0.95, sides=8, caps=(True, False))
    f += prism("wrap", (0, -1.6, 0), (0, 2.8, 0), 1.1, 1.1, sides=8, caps=(False, False))
    for y in (-2.0, 3.2):
        f += ringy("trim", y, 1.2, 0.45)
    f += vertebra("guard", (0, -4.6, 0), 1.1, 0.8, procs=4, plen=0.8)
    # 下顎: 平たく、上へ広がりながら +x へ反る骨 (正面を向く板の筒)
    jaw = [(0, 2.8, 0), (0.2, 7.0, 0), (0.9, 11.0, 0), (2.0, 14.8, 0), (3.4, 17.6, 0)]
    f += curve("main", jaw, [0.95, 1.1, 1.3, 1.45, 1.4], [0.85, 0.85, 0.8, 0.75, 0.7], sides=6, caps=(False, True), hint=(0, 0, 1))
    # 歯 (顎の -x 側に並ぶ円錐)
    for i, (x, y, _) in enumerate(jaw[1:4]):
        for dy in (0.0, 1.6):
            f += spike(BONE, "edge", (x - 1.0, y + dy, 0), (-1, 0.35, 0), 1.3 - 0.1 * i, 0.35, sides=4, rings=1, hint=(0, 0, 1))
    # 関節の丸 (金の帽子)
    f += ell("edge", (3.6, 18.4, 0), (1.6, 1.3, 1.2), segs=7, rings=2)
    f += prism("trim", (3.6, 18.4, -1.3), (3.6, 18.4, 1.3), 0.8, 0.8, sides=8, caps=(True, True), hint=(1, 0, 0))
    f += extrude("trim", [(3.0, 19.4), (4.2, 19.4), (3.6, 21.0)], 0.5, c=(0, 0, 0))
    # 金の鎖と、耳の骨 (巻いた殻) の鈴
    f += chain((4.6, 17.8, 0), (5.8, 13.6, 0), n=4, mat="trim")
    f += ell("edge", (6.0, 12.0, 0), (1.7, 1.9, 1.5), segs=8, rings=3,
             deform=lambda p: p * (1.0 + 0.25 * math.sin(math.atan2(p[1], p[0]) * 2)))
    f += torus("main", (6.0, 12.0, 0), 1.3, 0.35, 0.4, n=8, k=4, ax="z")
    f += ell("void", (6.0, 11.6, -1.2), (0.9, 0.7, 0.4), segs=6, rings=1)
    f += gem((6.0, 9.9, 0), 0.45, 0.6, 0.45, segs=5, rings=1)
    # 音の輪 (光る細い輪 二つ)
    f += torus("gem", (6.0, 12.0, 0), 2.6, 0.12, 0.12, n=10, k=3, ax="y")
    f += torus("gem", (6.0, 12.0, 0), 3.2, 0.1, 0.1, n=10, k=3, ax="z")
    f += strand("acc", (1.4, 12.0, 0.8), 2.4, 0.4, r=0.3)
    f += barnacles((1.6, 9.2, -0.8), n=3, spread=0.4, r=0.26, h=0.45, mat="guard")
    return f


def whale_dagger(stage=0):
    """鯨歯の短剣: 岩鯨の大きな歯をそのまま刃にした短剣。根元は顎の骨の欠片が鍔になって小さな歯が下を向き、歯の内側の溝が水色に光る。柄は石に苔の縄、柄頭は椎骨"""
    f = []
    f += prism("grip", (0, -3.4, 0), (0, 3.2, 0), 0.85, 0.8, sides=6, caps=(True, True))
    f += prism("wrap", (0, -2.6, 0), (0, 2.4, 0), 0.98, 0.98, sides=6, caps=(False, False))
    for y in (-1.8, -0.4, 1.0, 2.2):
        f += ringy("cord", y, 1.06, 0.3)
    f += vertebra("guard", (0, -3.9, 0), 1.0, 0.7, procs=4, plen=0.6)
    # 鍔: 顎の骨の欠片 (横に長い塊) と、下を向く小さな歯
    f += lump("spine", (0, 3.7, 0), (2.5, 1.0, 1.3), seed=41, segs=7, rings=2)
    for s in (1, -1):
        for x in (1.0, 2.0):
            f += spike(BONE, "edge", (s * x, 3.2, 0), (s * 0.25, -1, 0), 1.0 - 0.2 * (x - 1.0), 0.32, sides=4, rings=1, hint=(0, 0, 1))
    f += barnacles((-1.6, 4.1, -0.9), n=3, spread=0.35, r=0.22, h=0.35, mat="guard")
    # 刃: 歯 (+x へゆるく反る円錐)。根は太く、先は鋭い
    tooth = [(0, 4.0, 0), (0.25, 7.0, 0), (0.7, 10.0, 0), (1.3, 12.4, 0), (1.9, 13.8, 0)]
    f += curve("main", tooth, [1.3, 1.2, 0.95, 0.6, 0.12], [0.9, 0.85, 0.7, 0.45, 0.1], sides=6, caps=(False, True), hint=(0, 0, 1))
    # 内側の溝の光と、歯の根の筋
    f += gbar([(-0.55, 5.0, -0.78), (-0.35, 8.0, -0.74), (0.2, 11.0, -0.55)], r=0.17)
    f += ringy("edge", 4.6, 1.32, 0.35)
    f += strand("acc", (1.2, 3.4, 0.7), 1.8, 0.3, r=0.24)
    return f


# ================================================================== 一覧
def _w(fn, jp, tier, pose, grip, cen, scale, inlay, stages=1):
    if tier == "legend":
        kind = {"thunder": "bow"}.get(pose) or ("hammer" if pose == "ember" else "dagger" if scale >= 15 else "sword" if scale == 10.0 else "staff")
        base, fn = fn, (lambda stage=0, base=fn, kind=kind: base(stage) + legend_finish(kind, seed=len(jp)))
        fn.__doc__ = base.__doc__
    e = dict(jp=jp, tier=tier, glow="whale", pose=pose, grip=grip, cen=cen, scale=scale,
             opts={"a": ((lambda stage=0: fn(stage)), inlay, ("edge",), (fn.__doc__ or "").split(":", 1)[-1].strip())})
    if stages > 1:
        e["stages"] = stages
    return e


ST = dict(pose="whale", grip=(0.0, 1.5, 0.0), cen=(0.0, 9.0, 0.0), scale=9.5)
HM = dict(pose="ember", grip=(0.0, 0.0, 0.0), cen=(0.0, 7.5, 0.0), scale=10.5)
BW = dict(pose="thunder", grip=(5.0, 8.0, 0.0), cen=(2.0, 8.0, 0.0), scale=12.0, stages=4)
DG = dict(pose="whale", grip=(0.0, 0.6, 0.0), cen=(0.0, 4.8, 0.0), scale=16.0)
ln_staff = inlay_line(["main"], 4.0, 13.0)
WEAPONS = {
    "whale_dagger": _w(whale_dagger, "鯨歯の短剣", "rare", inlay=inlay_line(["main"], 5.0, 12.0), **DG),
    "whale_staff": _w(whale_staff, "鯨骨の星杖", "rare", inlay=inlay_line(["edge"], 15.5, 22.0), **ST),
    "whale_bell_staff": _w(whale_bell_staff, "鯨歌いの鈴杖", "rare", inlay=inlay_line(["main"], 15.0, 20.0), **ST),
    "whale_hammer": _w(whale_hammer, "岩鯨の石鎚", "rare", inlay=inlay_line(["guard"], 10.5, 14.0), **HM),
    "whale_bow": _w(whale_bow, "鯨肋の弓", "rare", inlay=inlay_line(["main"], 9.0, 18.0, lambda y: 0.0 * y), **BW),
    "abyss_whale_hammer": _w(abyss_whale_hammer, "背骨打ちの鎚", "legend", inlay=inlay_line(["main"], 10.5, 21.0), **HM),
    "abyss_whale_staff": _w(abyss_whale_staff, "鯨眼の杖", "legend", inlay=inlay_line(["spine", "guard"], 15.5, 21.0), **ST),
    "abyss_whale_bow": _w(abyss_whale_bow, "鯨ひげの弓", "legend", inlay=inlay_line(["edge"], 9.0, 18.0), **BW),
    "abyss_whale_fang": _w(abyss_whale_fang, "古銛の牙", "legend", inlay=inlay_line(["main"], 6.0, 12.0), **DG),
    "abyss_whale_bell_staff": _w(abyss_whale_bell_staff, "耳骨の鈴杖", "legend", inlay=inlay_line(["main"], 12.0, 18.0), **ST),
}
