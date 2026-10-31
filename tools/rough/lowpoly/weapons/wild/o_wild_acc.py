"""野の装備の装飾の枠の品 26 品 (docs/design/51 の 6 章)。一品一案。装飾 (体に付ける) と護符 (H キーで使う)。
大きさは今の装飾品と同じ (±4〜6 単位、1 単位 = 1/16 ブロック)。直立 (y 上)、広い面は ±Z、正面 -Z。
体に付ける品は、spec.py の mount の留め点 (首飾り・根付は紐の輪 y ≈ 5、背当ては真ん中 y ≈ 3、腕輪は原点を巻く輪、耳飾りは原点から下へ) に合わせる。
"""
from __future__ import annotations

import math

from kit_wild import *  # noqa: F401,F403
from kit_wild import W, bell, _slab


def loop(y=5.4, R=1.1, mat="cord"):
    return torus(mat, (0, y, 0), R, 0.24, 0.24, n=8, k=3, ax="z")


def cordline(y0, y1, x=0.0, mat="cord"):
    return prism(mat, (x, y0, 0), (x, y1, 0), 0.16, 0.16, sides=4, caps=(True, True))


# ====================================================================== 帯 I
def wild_guard_badge(stage=0):
    """衛士の鐘章: 都の衛士の胸の章。青銅の盾形の板に鐘の浮き彫り、上に鐘楼の三つの尖り、紐で胸に下げる"""
    f = loop(5.2)
    f += cordline(3.4, 4.6)
    f += extrude("acc", [(-2.4, 3.2), (2.4, 3.2), (2.6, 0.4), (1.4, -2.4), (0, -3.4), (-1.4, -2.4), (-2.6, 0.4)], 0.6)
    f += extrude("trim", [(-2.0, 2.8), (2.0, 2.8), (2.2, 0.4), (1.2, -2.0), (0, -2.9), (-1.2, -2.0), (-2.2, 0.4)], 0.2, c=(0, 0, -0.38))
    f += bell((0, 1.4, -0.7), r=0.95, h=1.9, mat="acc", mouth="dark")
    for x in (-1.6, 0.0, 1.6):
        f += spike(BONE, "acc", (x, 3.1, 0), (0, 1, 0), 1.2 if x == 0 else 0.8, 0.3, sides=4, rings=1)
    f += gem((0, -1.8, -0.5), 0.3, 0.3, 0.15, segs=5, rings=1)
    return f


def wild_mossball_netsuke(stage=0):
    """苔玉の根付: 光る苔をまとった丸い石を、骨の小さな留め具で帯から下げる根付。苔の房が垂れる"""
    f = loop(5.4)
    f += bone("spine", [(0, 4.4, 0), (0, 2.8, 0)], 0.35)
    f += ell("main", (0, 0.4, 0), (2.4, 2.4, 2.2), segs=8, rings=3)
    for i, (dx, dy, dz) in enumerate(((1.4, 0.8, -1.2), (-1.2, 1.2, -1.0), (0.4, -1.4, -1.4), (-1.4, -0.6, 1.0), (1.0, 1.6, 1.0))):
        f += lump("acc", (dx, 0.4 + dy, dz), (0.9, 0.7, 0.8), seed=i + 30)
    f += gem((0.2, 0.2, -2.1), 0.45, 0.45, 0.25, segs=6, rings=1)
    for x in (-1.0, 0.8):
        f += strand("acc", (x, -1.6, -0.6), 2.0, sway=0.3, r=0.25, n=3)
    return f


def wild_herb_vial(stage=0):
    """草薬の小瓶: 草原の薬草を漬けた丸い硝子の小瓶。コルクの栓に麻紐、首に乾いた薬草の小束を結ぶ"""
    f = ell("gem", (0, -0.6, 0), (2.3, 2.5, 2.3), segs=10, rings=4)
    f += prism("main", (0, 1.6, 0), (0, 3.0, 0), 0.9, 0.8, sides=8, caps=(False, False))
    f += prism("spine", (0, 2.8, 0), (0, 4.0, 0), 1.0, 1.1, sides=8, caps=(True, True))
    f += ringy("cord", 2.2, 1.0, 0.4)
    for k in range(3):
        a = math.radians(-30 + 30 * k)
        f += feather("acc", (0.9, 2.2, 0.2 * k), (0.9 + 2.2 * math.cos(a), 2.2 + 2.2 * math.sin(a) + 0.6, 0.2 * k), 0.45, th=0.18, big=False)
    f += curve("cord", [(-0.9, 2.2, 0), (-1.6, 1.0, -0.4), (-1.4, -0.4, -0.6)], 0.14, 0.14, sides=3)
    return f


# ====================================================================== 帯 II
def wild_float_netsuke(stage=0):
    """浮き玉の根付: 漁師の小さな硝子の浮き玉を縄の網で包んだ根付。網の結び目と短い縄の房"""
    f = loop(5.4)
    f += cordline(2.8, 4.4)
    f += ell("gem", (0, 0.4, 0), (2.3, 2.3, 2.3), segs=10, rings=4)
    for k in range(4):
        a = math.radians(45 + 90 * k)
        f += curve("cord", [(0.4 * math.cos(a), 2.8, 0.4 * math.sin(a)), (2.4 * math.cos(a), 0.4, 2.4 * math.sin(a)), (0.4 * math.cos(a), -2.0, 0.4 * math.sin(a))],
                   0.13, 0.13, sides=3)
    f += torus("cord", (0, 0.4, 0), 2.35, 0.13, 0.13, n=12, k=3, ax="y")
    f += strand("cord", (0, -2.0, 0), 1.8, sway=0.2, r=0.3, n=3)
    return f


def wild_wolf_tooth_necklace(stage=0):
    """雷狼の牙の首飾り: 雷狼の大きな牙を真ん中に、小さな牙を左右に並べた首飾り。牙の根を銅の線で巻き、真ん中の牙に雷の筋"""
    f = []
    f += curve("cord", [(-3.6, 5.0, 0), (-2.6, 1.8, 0), (0, 1.0, 0), (2.6, 1.8, 0), (3.6, 5.0, 0)], 0.16, 0.16, sides=4)
    for x, y, L, r in ((0, 1.0, 4.0, 0.7), (-1.5, 1.3, 2.6, 0.5), (1.5, 1.3, 2.6, 0.5), (-2.8, 2.3, 1.8, 0.4), (2.8, 2.3, 1.8, 0.4)):
        f += curve("main", [(x, y, 0), (x * 1.02, y - L * 0.5, -0.2), (x * 1.05 - 0.2, y - L, -0.5)], [r, r * 0.75, 0.05], [r * 0.7, r * 0.5, 0.05], sides=5)
        f += ringy("acc", y - 0.1, r + 0.08, 0.3, cx=x)
    f += gbar([(0, 0.4, -0.55), (0.15, -1.2, -0.62), (-0.1, -2.2, -0.6)], r=0.11)
    return f


def wild_crab_backplate(stage=0):
    """大蟹の甲羅の背当て: 潮招きの大蟹の甲羅を背に当てる板。赤い殻の縁に棘、真ん中に藤壺の群れ、革の帯で留める"""
    f = []
    rings_ = [[V(x * math.cos(math.radians(30 * j)), 3.0 + y * math.sin(math.radians(30 * j)), z) for j in range(12)]
              for x, y, z in ((4.6, 3.6, 0.6), (4.2, 3.3, -0.4), (3.0, 2.4, -1.1), (1.2, 1.0, -1.5))]
    f += loft(BONE, "main", rings_, caps=("main", "main"))
    for k in range(8):
        a = math.radians(-160 + 40 * k)
        f += spike(BONE, "spine", (4.5 * math.cos(a), 3.0 + 3.5 * math.sin(a), 0.4), (math.cos(a), math.sin(a), 0), 1.0, 0.32, sides=4, rings=1)
    f += barnacles((0, 3.6, -1.4), n=4, spread=1.0, dirv=(0, 0, -1), mat="acc")
    for y in (5.4, 0.8):
        f += prism("cord", (-4.8, y, 0.4), (4.8, y, 0.4), 0.3, 0.3, sides=4, caps=(True, True), hint=(0, 1, 0))
    f += gem((0, 2.0, -1.5), 0.4, 0.4, 0.2, segs=5, rings=1)
    return f


def wild_fog_handbell(stage=0):
    """霧鐘の小鈴: 灯台の霧鐘を手に持てるほどに写した青銅の小鈴。握りは白と赤の縞、鈴の口から霧が光る"""
    f = prism("cloth", (0, 1.6, 0), (0, 3.0, 0), 0.6, 0.6, sides=8, caps=(False, False))
    f += prism("main", (0, 3.0, 0), (0, 4.4, 0), 0.6, 0.6, sides=8, caps=(False, False))
    f += prism("cloth", (0, 4.4, 0), (0, 5.4, 0), 0.6, 0.6, sides=8, caps=(False, False))
    f += ell("main", (0, 5.7, 0), (0.8, 0.6, 0.8), segs=8, rings=2)
    f += bell((0, 1.8, 0), r=2.4, h=4.2, mat="acc", mouth="gem")
    f += torus("trim", (0, -2.0, 0), 2.45, 0.14, 0.18, n=12, k=4, ax="y")
    return f


# ====================================================================== 帯 III
def wild_weave_bracelet(stage=0):
    """織り紐の腕輪: 風待の織り紐を三色に編んだ足首の輪。編み目の結び目に小さな風切りの羽根が二枚"""
    f = []
    for k, (mat, ph) in enumerate((("cloth", 0.0), ("soot", 2.1), ("acc", 4.2))):
        pts = [(5.2 * math.cos(math.radians(a)), 0.5 * math.sin(math.radians(a * 3) + ph), 5.2 * math.sin(math.radians(a))) for a in range(0, 361, 30)]
        f += curve(mat, pts, 0.3, 0.3, sides=4)
    f += ell("cloth", (0, 0, -5.3), (0.7, 0.7, 0.5), segs=6, rings=1)
    for s in (-1, 1):
        f += feather("acc", (s * 0.4, -0.2, -5.4), (s * 1.6, -3.2, -5.6), 0.55, th=0.16, big=True)
    f += gem((0, 0.2, -5.8), 0.25, 0.25, 0.15, segs=5, rings=1)
    return f


def wild_emberbug_amber(stage=0):
    """熾り蟲の琥珀: 熾り蟲を一匹閉じ込めた涙形の琥珀。黒鉄の爪が三本で抱え、上に焦げた紐の輪"""
    f = loop(5.4)
    f += cordline(3.6, 4.6)
    f += ell("gem", (0, 0.6, 0), (2.0, 2.6, 1.6), segs=10, rings=4)
    f += spike(BONE, "gem", (0, 2.6, 0), (0, 1, 0), 1.2, 1.0, sides=8, rings=1)
    f += ell("dark", (0, 0.4, -0.4), (0.5, 0.9, 0.4), segs=6, rings=2)
    for k in range(3):
        a = math.radians(90 + 120 * k)
        f += curve("spine", [(0, 3.4, 0), (2.0 * math.cos(a), 1.8, 1.6 * math.sin(a)), (1.8 * math.cos(a), -0.6, 1.4 * math.sin(a))], [0.25, 0.22, 0.08],
                   [0.25, 0.22, 0.08], sides=4)
    return f


def wild_ramhorn_bugle(stage=0):
    """雷角の角笛: 雷角の大羊の渦巻く角をくり抜いた角笛。口金は銅、吹き口に革、腰に下げる革紐と房"""
    f = loop(5.4)
    f += curve("cord", [(0, 5.0, 0), (1.6, 3.8, 0), (2.8, 3.0, 0)], 0.16, 0.16, sides=4)
    pts = []
    for i in range(9):
        a = math.radians(-40 + 45 * i)
        R = 3.2 - 0.25 * i
        pts.append((R * math.cos(a), 0.6 + R * math.sin(a) * 0.9, -0.3 * i))
    rs = [1.3 - 0.13 * i for i in range(9)]
    f += curve("main", pts, rs, rs, sides=7, caps=(True, True))
    f += prism("acc", pts[0], (pts[0][0] + 0.6, pts[0][1] - 0.5, 0), 1.45, 1.6, sides=8, caps=(True, True))
    f += prism("dark", (pts[0][0] + 0.55, pts[0][1] - 0.45, 0), (pts[0][0] + 0.62, pts[0][1] - 0.52, 0), 1.2, 1.2, sides=8, caps=(True, True))
    for i in (2, 4, 6):
        x, y, z = pts[i]
        f += ell("spine", (x, y, z), (rs[i] + 0.12, 0.3, rs[i] + 0.12), segs=8, rings=1)
    f += gbar([(pts[3][0], pts[3][1], pts[3][2] - 0.9), (pts[5][0], pts[5][1], pts[5][2] - 0.8)], r=0.14)
    return f


def wild_ash_talisman(stage=0):
    """灰の民の祈り札: 焼いた木の札に灰の民の祈りの紋を焼き付け、熾の粒を一つ嵌めた札。札の下に灰色の房"""
    f = loop(5.6, R=0.9)
    f += cordline(4.4, 5.0)
    f += _slab("main", [V(-2.0, 4.4, -0.3), V(2.0, 4.4, -0.3), V(2.0, -2.8, -0.3), V(-2.0, -2.8, -0.3)], 0.6)
    f += gbar([(-1.0, 2.6, -0.4), (0.0, 3.4, -0.42), (1.0, 2.6, -0.4)], r=0.14, mat="dark")
    f += gbar([(0.0, 3.0, -0.42), (0.0, -0.4, -0.42)], r=0.14, mat="dark")
    f += gbar([(-1.2, 0.6, -0.4), (1.2, 0.6, -0.4)], r=0.14, mat="dark")
    f += gem((0, -1.4, -0.45), 0.5, 0.5, 0.2, segs=6, rings=1)
    for x in (-0.8, 0.0, 0.8):
        f += strand("cloth", (x, -2.8, 0), 2.2, sway=0.2, r=0.2, n=3)
    return f


def wild_signal_fire(stage=0):
    """峰の狼煙筒: 嵐の高地の見張りが使う銅の狼煙の筒。筒の口から燃える火、胴に雷角の紋、底に握りの革"""
    f = prism("main", (0, -3.0, 0), (0, 3.4, 0), 1.6, 1.8, sides=10, caps=(True, False))
    f += prism("main", (0, 3.4, 0), (0, 3.6, 0), 2.1, 2.1, sides=10, caps=(True, True))
    f += prism("cloth", (0, -3.0, 0), (0, -0.6, 0), 1.75, 1.75, sides=10, caps=(False, False))
    for y in (0.6, 2.4):
        f += ringy("spine", y, 1.8, 0.35, sides=10)
    f += curve("trim", [(-0.8, 1.0, -1.75), (0, 1.8, -1.8), (0.8, 1.0, -1.75)], 0.14, 0.14, sides=3)
    for k, h in enumerate((3.2, 2.4, 2.8)):
        a = math.radians(120 * k)
        f += spike(BONE, "gem", (0.5 * math.cos(a), 3.6, 0.5 * math.sin(a)), (0.2 * math.cos(a), 1, 0.2 * math.sin(a)), h, 0.8, sides=6, rings=2)
    return f


# ====================================================================== 帯 IV
def wild_plumb_line_ring(stage=0):
    """測り縄の輪: 探掘隊が深さを測る縄を巻いた輪。真鍮の目盛りの札と、下に鉛の小さな錘"""
    f = loop(6.0, R=0.9)
    f += torus("cord", (0, 3.0, 0), 2.6, 0.7, 0.7, n=12, k=6, ax="z")
    for a in (60, 200, 320):
        f += ell("cord", (2.6 * math.cos(math.radians(a)), 3.0 + 2.6 * math.sin(math.radians(a)), 0), (0.9, 0.9, 0.9), segs=6, rings=1)
    f += block("acc", (2.9, 2.0, -0.7), (1.0, 1.4, 0.2))
    f += cordline(0.4, -1.8)
    f += prism("spine", (0, -1.8, 0), (0, -2.4, 0), 0.4, 0.8, sides=6, caps=(True, True))
    f += spike(BONE, "spine", (0, -2.4, 0), (0, -1, 0), 1.4, 0.8, sides=6, rings=1)
    f += gem((0, 3.0, -0.75), 0.3, 0.3, 0.15, segs=5, rings=1)
    return f


def wild_salttear_earring(stage=0):
    """塩の涙の耳飾り: 小さな塩の雫の結晶を細い銀の鎖で下げる耳飾り。雫の中に光る泡"""
    f = torus("acc", (0, -0.6, 0), 0.6, 0.12, 0.12, n=8, k=3, ax="z")
    f += prism("acc", (0, -1.2, 0), (0, -3.0, 0), 0.1, 0.1, sides=3, caps=(False, False))
    f += ell("main", (0, -4.6, 0), (1.1, 1.4, 1.1), segs=8, rings=3)
    f += spike(BONE, "main", (0, -3.6, 0), (0, 1, 0), 1.0, 0.7, sides=6, rings=1)
    f += gem((0.2, -4.8, -0.8), 0.3, 0.3, 0.2, segs=5, rings=1)
    return f


def wild_bonepipe_necklace(stage=0):
    """骨笛の首飾り: 長さの違う小さな骨の管を五本並べた首飾り。管の口に淵の紫の灯がかすかに灯る"""
    f = curve("cord", [(-3.4, 5.0, 0), (-2.4, 2.2, 0), (0, 1.6, 0), (2.4, 2.2, 0), (3.4, 5.0, 0)], 0.16, 0.16, sides=4)
    for i, (x, L) in enumerate(((-2.0, 2.0), (-1.0, 3.0), (0.0, 3.8), (1.0, 3.0), (2.0, 2.0))):
        y0 = 1.6 + abs(x) * 0.3
        f += bone("main", [(x, y0, 0), (x, y0 - L, 0)], 0.38, knob=False)
        f += prism("gem", (x, y0 - L, 0), (x, y0 - L - 0.12, 0), 0.25, 0.25, sides=6, caps=(True, True))
    f += prism("spine", (-2.4, 1.7, 0), (2.4, 1.7, 0), 0.2, 0.2, sides=4, caps=(True, True), hint=(0, 1, 0))
    return f


def wild_hangman_knot(stage=0):
    """縄の人の結び目: 縄の人の首の縄の、ほどけない結び目。黒い縄の輪と幾重もの巻き、結び目の中に淵の紫の目"""
    f = loop(5.4)
    f += torus("cord", (0, 1.6, 0), 2.0, 0.45, 0.45, n=10, k=5, ax="z")
    for i in range(5):
        f += ringy("cord", -0.8 - 0.55 * i, 0.75, 0.4)
    f += prism("cord", (0, -0.4, 0), (0, -3.8, 0), 0.55, 0.55, sides=6, caps=(True, True))
    f += curve("cord", [(0, -3.8, 0), (0.6, -5.0, 0.2), (0.2, -6.0, 0.4)], 0.3, 0.3, sides=4)
    f += ell("gem", (0, -1.8, -0.75), (0.3, 0.3, 0.15), segs=5, rings=1)
    f += cordline(3.6, 4.4)
    return f


def wild_lamp_charm(stage=0):
    """灯籠の小灯: 手のひらほどの古い真鍮の灯籠。四本の柱に琥珀の硝子、頂に吊り環、底に鋲"""
    f = prism("acc", (0, -3.0, 0), (0, -2.4, 0), 2.0, 1.8, sides=8, caps=(True, True))
    f += prism("gem", (0, -2.4, 0), (0, 2.0, 0), 1.6, 1.6, sides=8, caps=(False, False))
    for k in range(4):
        a = math.radians(45 + 90 * k)
        f += prism("acc", (1.7 * math.cos(a), -2.4, 1.7 * math.sin(a)), (1.7 * math.cos(a), 2.0, 1.7 * math.sin(a)), 0.22, 0.22, sides=4, caps=(False, False))
    f += prism("acc", (0, 2.0, 0), (0, 3.4, 0), 2.0, 0.6, sides=8, caps=(True, True))
    f += torus("acc", (0, 4.2, 0), 0.8, 0.18, 0.18, n=8, k=3, ax="z")
    return f


def wild_mimic_heart_charm(stage=0):
    """脈打つ偽の心臓: 鼓動もどきから剥がした小さな偽の心臓。赤黒い肉の塊に管が二本、骨の留め針で刺し留め、芯が脈を打って光る"""
    f = ell("cloth", (0, 0, 0), (2.4, 2.8, 2.2), segs=10, rings=4)
    f += ell("cloth", (0.9, 2.2, 0.2), (1.1, 1.0, 1.0), segs=6, rings=2)
    for p in ((0.6, -0.4, -2.0), (-0.9, 0.8, -1.8), (0.2, 1.2, -2.0)):
        f += gem(p, 0.35, 0.35, 0.2, segs=5, rings=1)
    for s in (-1, 1):
        f += curve("cloth", [(s * 0.8, 2.6, 0), (s * 1.4, 4.0, 0.3), (s * 2.4, 4.4, 0.6)], 0.4, 0.4, sides=5)
    f += spike(BONE, "main", (-3.0, -1.6, 0), (1, 0.5, 0), 6.0, 0.25, sides=4, rings=1)
    return f


# ====================================================================== 帯 V
def wild_fulgurite_necklace(stage=0):
    """雷管石の首飾り: 雷が砂を焼いた枝分かれの硝子 (雷管石) を、銅の線で吊った首飾り。枝の中を光が走る"""
    f = curve("acc", [(-3.4, 5.0, 0), (-2.2, 2.6, 0), (0, 2.0, 0), (2.2, 2.6, 0), (3.4, 5.0, 0)], 0.15, 0.15, sides=4)
    trunk = [(0, 2.0, 0), (0.3, 0.4, -0.1), (-0.2, -1.4, 0), (0.1, -3.2, 0)]
    f += curve("main", trunk, [0.5, 0.45, 0.38, 0.1], [0.5, 0.45, 0.38, 0.1], sides=5)
    for p0, p1 in (((0.3, 0.4, -0.1), (1.6, -0.8, 0)), ((-0.2, -1.4, 0), (-1.4, -2.4, 0)), ((0.2, 1.2, 0), (-1.2, 0.6, 0))):
        f += curve("main", [p0, p1], [0.3, 0.06], [0.3, 0.06], sides=4)
    f += gbar([(0.2, 1.6, -0.45), (0.3, 0.2, -0.4), (-0.1, -1.6, -0.35)], r=0.1)
    f += ringy("acc", 1.8, 0.55, 0.3)
    return f


def wild_ash_hourglass_charm(stage=0):
    """灰の砂時計の飾り: 腰に下げる小さな砂時計。焦げた骨の枠に灰が落ち、落ちる筋が光る"""
    f = loop(5.6, R=0.9)
    f += cordline(4.0, 4.8)
    for y in (-2.8, 3.6):
        f += prism("acc", (0, y, 0), (0, y + 0.4, 0), 1.6, 1.6, sides=8, caps=(True, True))
    for k in range(3):
        a = math.radians(120 * k)
        f += prism("acc", (1.3 * math.cos(a), -2.6, 1.3 * math.sin(a)), (1.3 * math.cos(a), 3.8, 1.3 * math.sin(a)), 0.18, 0.18, sides=4, caps=(False, False))
    for y in (-1.1, 2.0):
        f += ell("main", (0, y, 0), (1.05, 1.3, 1.05), segs=8, rings=3)
    f += ell("cloth", (0, -1.6, 0), (0.85, 0.5, 0.85), segs=8, rings=1)
    f += prism("gem", (0, -1.0, 0), (0, 1.6, 0), 0.07, 0.07, sides=3, caps=(False, False))
    return f


def wild_deep_eye_charm(stage=0):
    """深みの眼の護り: 瞼を半ば開いた眼を、黒い指が四本で抱く護り。瞳は淵の紫"""
    f = loop(5.6, R=0.9)
    f += cordline(3.6, 4.8)
    f += ell("main", (0, 1.0, 0), (1.8, 1.8, 1.6), segs=10, rings=4)
    f += ell("gem", (0, 1.0, -1.4), (0.8, 0.8, 0.3), segs=8, rings=2)
    f += ell("dark", (0, 1.0, -1.65), (0.3, 0.55, 0.12), segs=6, rings=1)
    for sy in (1, -1):
        f += curve("cloth", [(-1.7, 1.0 + sy * 0.2, -0.6), (0, 1.0 + sy * 1.0, -1.6), (1.7, 1.0 + sy * 0.2, -0.6)], 0.3, 0.3, sides=5)
    for k in range(4):
        a = math.radians(45 + 90 * k)
        f += curve("spine", [(0, -1.2, 0), (2.0 * math.cos(a), 0.4, 1.6 * math.sin(a) * 0.6 + 0.4), (1.6 * math.cos(a), 2.6, 1.2 * math.sin(a) * 0.6 + 0.4)],
                   [0.28, 0.24, 0.06], [0.28, 0.24, 0.06], sides=4)
    return f


def wild_meteor_ring(stage=0):
    """隕鉄の環: 月に落ちた星の鉄を叩いて輪にした腰の飾り。網目の浮いた黒銀の環に、焼けた星の石を一つ嵌め、星の光が漏れる"""
    f = loop(5.6, R=0.9)
    f += cordline(3.8, 4.8)
    f += torus("main", (0, 1.0, 0), 2.6, 0.6, 0.7, n=14, k=6, ax="z")
    f += lump("acc", (0, -1.7, -0.2), (1.1, 0.9, 0.9), seed=48)
    f += gem((0, -1.7, -1.0), 0.45, 0.45, 0.2, segs=6, rings=1)
    for a in (40, 140, 220, 320):
        d = (math.cos(math.radians(a)), math.sin(math.radians(a)))
        f += gem((2.6 * d[0], 1.0 + 2.6 * d[1], -0.65), 0.2, 0.2, 0.12, segs=4, rings=1)
    return f


def wild_skylark_plume(stage=0):
    """雲雀の羽根飾り: 雲雀の白い羽根を三枚束ね、金の留め具で耳の後ろに挿す飾り。羽根の先は空の光"""
    f = prism("trim", (0, 0, 0), (0, 1.2, 0), 0.45, 0.4, sides=6, caps=(True, True))
    f += gem((0, 0.6, -0.45), 0.25, 0.25, 0.15, segs=5, rings=1)
    for k, (dx, L) in enumerate(((-0.8, 6.0), (0.0, 7.6), (0.8, 6.4))):
        f += plume((dx * 0.3, 1.0, 0.1 * k), (dx * 2.4, 1.0 + L, 0.6 + 0.2 * k), 0.85, th=0.2, big=True, mat="cloth", tipmat="gem", scorch=1)
    return f


def wild_starchart_compass(stage=0):
    """星図の羅針盤: 星図を刻んだ銀の羅針盤。蓋が半ば開き、盤に星座の点と光る針、縁に方位の刻み"""
    f = prism("main", (0, 0, -0.5), (0, 0, 0.5), 3.0, 3.0, sides=16, caps=(True, True), hint=(1, 0, 0))
    f += torus("acc", (0, 0, 0), 3.05, 0.2, 0.55, n=16, k=4, ax="z")
    for k in range(8):
        a = math.radians(45 * k)
        f += block("dark", (2.5 * math.cos(a), 2.5 * math.sin(a), -0.55), (0.15, 0.5 if k % 2 == 0 else 0.3, 0.05), rot=rot_z(45 * k - 90))
    for p in ((-1.0, 1.2), (0.4, 1.6), (1.4, 0.4), (-0.6, -1.0)):
        f += gem((p[0], p[1], -0.55), 0.18, 0.18, 0.08, segs=4, rings=1)
    f += extrude("gem", [(0, -2.0), (0.3, 0), (0, 2.0), (-0.3, 0)], 0.15, c=(0, 0, -0.65), rot=rot_z(30))
    f += at(prism("acc", (0, 0, -0.15), (0, 0, 0.15), 3.0, 3.0, sides=16, caps=(True, True), hint=(1, 0, 0)), rot_x(-60), (0, 2.8, 1.4))
    f += torus("acc", (0, 3.4, 0), 0.6, 0.15, 0.15, n=8, k=3, ax="z")
    return f


def wild_moon_hourglass(stage=0):
    """月の砂時計: 月の石の枠に銀の砂が止まったまま宙に浮く砂時計。枠の上下に三日月、真ん中の首に光の環"""
    f = []
    for y, s in ((-3.2, 1), (3.2, -1)):
        f += prism("main", (0, y - 0.3, 0), (0, y + 0.3, 0), 2.0, 2.0, sides=10, caps=(True, True))
    for k in range(4):
        a = math.radians(45 + 90 * k)
        f += prism("acc", (1.6 * math.cos(a), -2.9, 1.6 * math.sin(a)), (1.6 * math.cos(a), 2.9, 1.6 * math.sin(a)), 0.2, 0.2, sides=4, caps=(False, False))
    for y in (-1.5, 1.5):
        f += ell("cloth", (0, y, 0), (1.3, 1.5, 1.3), segs=8, rings=3)
    for p in ((0.3, 1.0, 0.2), (-0.4, 0.6, -0.2), (0.1, 0.2, 0.3), (0.0, -1.4, 0.0)):
        f += gem(p, 0.18, 0.18, 0.18, segs=4, rings=1)
    f += torus("trim", (0, 0, 0), 0.65, 0.1, 0.12, n=10, k=3, ax="y")
    for s in (1, -1):
        pts = [(1.2 * math.cos(math.radians(a)), s * 3.6 + s * 0.6 * math.sin(math.radians(a)), 0) for a in range(200, 341, 35)]
        f += curve("acc", pts, 0.22, 0.22, sides=4)
    return f


def wild_cloudhymn_slip(stage=0):
    """雲上の祝詞の札: 雲の白の細長い札に、金で祝詞を書き、上に金の鈴、下に紙の垂 (しで) を二筋"""
    f = _slab("main", [V(-1.4, 4.0, -0.2), V(1.4, 4.0, -0.2), V(1.4, -3.4, -0.2), V(-1.4, -3.4, -0.2)], 0.4)
    for y in (2.6, 1.2, -0.2, -1.6):
        f += gbar([(-0.7, y, -0.25), (0.7, y, -0.25)], r=0.1, mat="acc")
    f += gem((0, 3.3, -0.3), 0.3, 0.3, 0.15, segs=5, rings=1)
    f += bell((0, 5.6, 0), r=0.7, h=1.3, mat="acc", mouth="gem")
    for s in (-1, 1):
        zz = [(s * 0.9, -3.4, 0), (s * 1.5, -4.4, 0), (s * 1.0, -5.4, 0), (s * 1.6, -6.4, 0)]
        for (x0, y0, _), (x1, y1, _) in zip(zz, zz[1:]):
            f += _slab("cloth", [V(x0 - 0.25, y0, -0.05), V(x0 + 0.25, y0, -0.05), V(x1 + 0.25, y1, -0.05), V(x1 - 0.25, y1, -0.05)], 0.08)
    return f


WEAPONS = dict([
    W(wild_guard_badge, "衛士の鐘章", "uncommon", "trinket", "lamp", dict(acc="bronze", cord="leather", dark="dark")),
    W(wild_mossball_netsuke, "苔玉の根付", "rare", "trinket", "moss", dict(main="stone", acc="moss", spine="bone", cord="rope")),
    W(wild_herb_vial, "草薬の小瓶", "uncommon", "charm", "moss", dict(main="glass", spine="oak", cord="rope", acc="greencloth")),
    W(wild_float_netsuke, "浮き玉の根付", "uncommon", "trinket", "sea", dict(cord="rope")),
    W(wild_wolf_tooth_necklace, "雷狼の牙の首飾り", "rare", "trinket", "spark", dict(main="tusk", acc="copper", cord="leather")),
    W(wild_crab_backplate, "大蟹の甲羅の背当て", "epic", "trinket", "sea", dict(main="crabshell", spine="crabshell", acc="shell", cord="leather")),
    W(wild_fog_handbell, "霧鐘の小鈴", "rare", "charm", "lamp", dict(main="whitecloth", cloth="redcloth", acc="bronze")),
    W(wild_weave_bracelet, "織り紐の腕輪", "rare", "trinket", "frost", dict(cloth="whitecloth", soot="bluecloth", acc="bluecloth")),
    W(wild_emberbug_amber, "熾り蟲の琥珀", "epic", "trinket", "ember", dict(spine="blacksteel", cord="blackleather")),
    W(wild_ramhorn_bugle, "雷角の角笛", "heroic", "trinket", "spark", dict(main="tusk", acc="copper", spine="copper", cord="leather")),
    W(wild_ash_talisman, "灰の民の祈り札", "epic", "charm", "ember", dict(main="ashwood", cloth="ash", cord="blackleather")),
    W(wild_signal_fire, "峰の狼煙筒", "heroic", "charm", "ember", dict(main="copper", spine="iron", cloth="leather")),
    W(wild_plumb_line_ring, "測り縄の輪", "rare", "trinket", "lamp", dict(cord="rope", acc="brass", spine="iron")),
    W(wild_salttear_earring, "塩の涙の耳飾り", "epic", "trinket", "salt", dict(main="salt", acc="silver")),
    W(wild_bonepipe_necklace, "骨笛の首飾り", "epic", "trinket", "void", dict(main="bone", spine="iron", cord="blackleather")),
    W(wild_hangman_knot, "縄の人の結び目", "heroic", "trinket", "void", dict(cord="rope")),
    W(wild_lamp_charm, "灯籠の小灯", "rare", "charm", "lamp", dict(acc="brass")),
    W(wild_mimic_heart_charm, "脈打つ偽の心臓", "heroic", "charm", "heart", dict(cloth="flesh", main="bone")),
    W(wild_fulgurite_necklace, "雷管石の首飾り", "rare", "trinket", "spark", dict(main="fulgurite", acc="copper")),
    W(wild_ash_hourglass_charm, "灰の砂時計の飾り", "epic", "trinket", "ember", dict(main="glass", acc="bone", cloth="ash", cord="blackleather")),
    W(wild_deep_eye_charm, "深みの眼の護り", "epic", "trinket", "void", dict(main="bone", cloth="flesh", spine="blacksteel", cord="blackleather")),
    W(wild_meteor_ring, "隕鉄の環", "heroic", "trinket", "star", dict(main="meteor", acc="basalt", cord="bluecloth")),
    W(wild_skylark_plume, "雲雀の羽根飾り", "heroic", "trinket", "dawn", dict(cloth="cloudwhite")),
    W(wild_starchart_compass, "星図の羅針盤", "epic", "charm", "star", dict(main="bluecloth", acc="silver")),
    W(wild_moon_hourglass, "月の砂時計", "heroic", "charm", "star", dict(main="stone", acc="silver", cloth="glass")),
    W(wild_cloudhymn_slip, "雲上の祝詞の札", "heroic", "charm", "dawn", dict(main="cloudwhite", acc="gold", cloth="paper")),
])
# 大きい品は絵の枠を引く
WEAPONS["wild_weave_bracelet"]["scale"] = 12.0
WEAPONS["wild_crab_backplate"]["scale"] = 15.0
WEAPONS["wild_wolf_tooth_necklace"]["scale"] = 18.0
