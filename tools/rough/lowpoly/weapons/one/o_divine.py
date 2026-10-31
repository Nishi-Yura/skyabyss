"""終章「雲海」の神器 12 本 (6 職に二つずつ。docs/design/50 の 4.5) の一品一案。一本目は島と座で覚えた決まり、二本目 (2026-11-01) は雲の島の敵と主:
羽の番人の大剣 = 羽の番人 (盾の羽と面頬)・灰打ちの大鎚 = 炉の島の金床と熱の継ぎ目・雲の瞼の杖 = 巡礼者の目・霞鳥の弓 = 霞鳥の翼・
灯蛾の短剣 = 灯蛾の二枚の翅・鐘守の鈴杖 = 最後の鐘守の小さな鐘楼。以下は一本目の説明。島と座で覚えた決まりを一つずつ形にする:
天穹の剣 = 巡礼者の手 (翼の鍔と天の光の樋)・七つ鐘の大鎚 = 鐘・暁の灯杖 = 灯の目・巡礼の羽弓 = 落ち羽・巻き戻りの短剣 = 割れた歯車・約束の鈴杖 = 四人の灯。
塗りは雲海の塗り (paint_divine.py): main = 雲の白の石 (金の筋)、edge/spine/acc/guard = 金、grip/wrap = 空の藍、gem = 朝の光 (dawn) / 空 (sky) / 残り火 (ember)、trim = 神器の金。
豪華さ: 月のヒロイックより一段上 (~800 面)。
"""
from __future__ import annotations

import math

from kit_one import *  # noqa: F401,F403
from kit_one import ell, strand, ringy, gbar, gem, arc, bend, mirror_faces_y, string_and_arrow, inlay_line, CY
from st_kit import torus


def sky_pole(y0, y1, r=0.9):
    """空の藍の柄: 握りの巻き、金の輪、尻の金の突き"""
    f = prism("grip", (0, y0, 0), (0, y1, 0), r, r * 0.95, sides=8, caps=(True, True))
    f += prism("wrap", (0, -1.6, 0), (0, 3.0, 0), r + 0.16, r + 0.16, sides=8, caps=(False, False))
    for y in (-1.8, 3.2):
        f += ringy("trim", y, r + 0.3, 0.45)
    for y in (6.6, 10.2):
        f += ringy("spine", y, r + 0.22, 0.55, sides=8)
    f += spike(BONE, "spine", (0, y0, 0), (0, -1, 0), 1.8, r, sides=8, rings=1, hint=(0, 0, 1))
    return f


def wing_guard(y, span, rise, n=4):
    """翼の鍔: 左右へ畳んだ翼 (金の骨と白い羽)"""
    f = []
    for s in (-1, 1):
        bone_pts = [(s * 0.8, y, 0), (s * span * 0.45, y + rise * 0.3, 0), (s * span, y + rise, 0)]
        f += curve("acc", bone_pts, [0.5, 0.42, 0.22], [0.5, 0.42, 0.22], sides=5, caps=(True, True), hint=(0, 0, 1))
        for i in range(n):
            t = (i + 1) / (n + 0.5)
            bx = s * span * t
            by = y + rise * t * t
            ln = 3.4 - 1.6 * t
            f += feather("cloth", (bx, by, 0.1 * i), (bx + s * ln * 0.55, by - ln * 0.85, 0.1 * i), 0.7 - 0.25 * t, th=0.3)
    return f


def divine_sky_sword(stage=0):
    """天穹の剣: まっすぐな白い大剣。刃は雲の白の石に金の縁、樋に朝の光が一筋走る。鍔は左右に畳んだ巡礼者の翼と、真ん中の金の輪に朝の光の珠。柄は空の藍、柄頭は金の輪"""
    f = []
    f += prism("grip", (0, -4.6, 0), (0, 3.2, 0), 0.9, 0.92, sides=8, caps=(True, True))
    f += prism("wrap", (0, -3.8, 0), (0, 2.6, 0), 1.04, 1.04, sides=8, caps=(False, False))
    f += ringy("trim", -4.1, 1.1, 0.4)
    f += torus("trim", (0, -5.8, 0), 1.0, 0.24, 0.3, n=12, k=4, ax="z")
    f += gem((0, -5.8, 0), 0.42, 0.42, 0.3, segs=6, rings=2)
    # 鍔: 翼と輪
    f += block("spine", (0, 3.8, 0), (2.8, 1.1, 1.7), taper=(0.9, 0.9))
    f += wing_guard(4.2, 5.6, 2.6)
    f += torus("trim", (0, 5.2, 0), 1.7, 0.2, 0.55, n=14, k=4, ax="z")
    f += gem((0, 5.2, -0.4), 0.7, 0.7, 0.3, segs=8, rings=2)
    # 刃 (長く、根元が広い)
    f += blade("main", [(5.8, -1.7, 1.7, 0.6, 0.6), (11.0, -1.62, 1.62, 0.58, 0.58), (17.0, -1.45, 1.45, 0.52, 0.52), (22.0, -1.1, 1.1, 0.44, 0.44),
                        (25.0, -0.6, 0.6, 0.3, 0.3), (26.6, 0, 0, 0, 0)], colmats=["edge", "main", "edge", "edge", "main", "edge"])
    for z in (-0.58, 0.58):
        f += gbar([(0, 7.0, z), (0, 14.0, z * 0.98), (0, 21.5, z * 0.9)], r=0.17)
    # 刃の根元の小さな金の光の冠
    for s in (-1, 1):
        f += spike(BONE, "acc", (s * 1.4, 6.4, 0), (s * 0.5, 1, 0), 1.6, 0.35, sides=4, rings=1, hint=(0, 0, 1))
    return f


def divine_bell_hammer(stage=0):
    """七つ鐘の大鎚: 頭は横に寝かせた金の大鐘。打つ面は鐘の口 (中に光る撞木)、背は鐘の冠の輪。胴の帯から小さな鐘が七つ下がる。柄は空の藍"""
    f = sky_pole(-4.2, 10.4, r=1.0)
    f += ell("acc", (0, -4.8, 0), (1.2, 0.9, 1.2), segs=8, rings=2)
    f += prism("spine", (0, 10.0, 0), (0, 11.6, 0), 1.1, 1.6, sides=8, caps=(True, True))
    # 大鐘 (軸は x。+x が口)
    prof = ((-3.2, 1.5), (-2.4, 2.4), (-0.8, 2.8), (1.2, 3.1), (2.8, 3.8), (3.4, 4.1))
    rings = [[V(x, 14.0 + r * math.cos(math.radians(30 * j)), r * math.sin(math.radians(30 * j))) for j in range(12)] for x, r in prof]
    f += loft(BONE, "acc", rings, caps=("acc", None))
    # 口の縁 (白い石の唇) と中
    f += torus("main", (3.4, 14.0, 0), 4.0, 0.35, 0.35, n=16, k=4, ax="x")
    f += prism("dark", (3.0, 14.0, 0), (3.3, 14.0, 0), 3.7, 3.7, sides=12, caps=(True, True), hint=(0, 1, 0))
    f += prism("cord", (-1.0, 14.0, 0), (2.4, 14.0, 0), 0.25, 0.25, sides=4, caps=(True, True), hint=(0, 1, 0))
    f += ell("gem", (2.8, 14.0, 0), (0.7, 0.9, 0.9), segs=8, rings=3)
    # 胴の帯と冠
    f += torus("trim", (0.2, 14.0, 0), 3.0, 0.3, 0.3, n=16, k=4, ax="x")
    f += torus("trim", (-3.3, 14.0, 0), 1.4, 0.4, 0.4, n=12, k=4, ax="z")
    # 帯から下がる七つの小さな鐘
    for k in range(7):
        a = math.radians(-150 + 50 * k)
        y, z = 14.0 + 3.1 * math.cos(a), 3.1 * math.sin(a)
        if y > 15.5:
            continue
        f += prism("cord", (0.2, y, z), (0.2, y - 1.0, z), 0.08, 0.08, sides=3, caps=(False, False))
        brings = [[V(0.2 + rr * math.cos(math.radians(30 * j)), yy, z + rr * math.sin(math.radians(30 * j))) for j in range(12)]
                  for yy, rr in ((y - 2.4, 0.55), (y - 2.25, 0.52), (y - 1.6, 0.4), (y - 1.1, 0.26), (y - 1.0, 0.1))]
        f += loft(BONE, "spine", brings, caps=("gem", "spine"))
    return f


def divine_dawn_staff(stage=0):
    """暁の灯杖: 柄の先に金の灯の籠 (縦の格子四本と上下の輪)。籠の中に朝の光の珠が浮き、前に瞳のような空の輪。二枚の金の鎧戸が半ば開き、頂に光の冠"""
    f = sky_pole(-4.0, 13.6)
    f += prism("spine", (0, 13.4, 0), (0, 14.8, 0), 0.9, 1.5, sides=8, caps=(True, True))
    C = (0, 18.0, 0)
    f += torus("acc", (0, 15.0, 0), 2.4, 0.3, 0.3, n=16, k=4, ax="y")
    f += torus("acc", (0, 21.0, 0), 2.0, 0.3, 0.3, n=16, k=4, ax="y")
    for k in range(4):
        a = math.radians(45 + 90 * k)
        f += curve("acc", [(2.4 * math.cos(a), 15.0, 2.4 * math.sin(a)), (2.9 * math.cos(a), 18.0, 2.9 * math.sin(a)), (2.0 * math.cos(a), 21.0, 2.0 * math.sin(a))],
                   [0.22, 0.24, 0.22], [0.22, 0.24, 0.22], sides=4, caps=(True, True), hint=(0, 1, 0))
    f += ell("gem", C, (1.7, 1.7, 1.7), segs=10, rings=5)
    f += torus("trim", (0, 18.0, -1.5), 0.8, 0.14, 0.14, n=12, k=3, ax="z")
    f += ell("dark", (0, 18.0, -1.62), (0.45, 0.55, 0.12), segs=8, rings=2)
    # 鎧戸 (左右に半ば開く金の板)
    for s in (-1, 1):
        f += block("guard", (s * 2.2, 18.0, -1.2), (0.25, 4.6, 2.0), rot=(0, s * 35, 0))
        f += gbar([(s * 2.35, 16.2, -1.4), (s * 2.35, 19.8, -1.4)], r=0.1, mat="trim")
    # 頂の光の冠
    f += spike(BONE, "trim", (0, 21.2, 0), (0, 1, 0), 2.4, 0.5, sides=6, rings=1, hint=(0, 0, 1))
    for a in (30, 150):
        d = V(math.cos(math.radians(a)), math.sin(math.radians(a)), 0)
        f += spike(BONE, "trim", tuple(V(0, 21.4, 0) + d * 0.6), tuple(d), 1.3, 0.25, sides=4, rings=1, hint=(0, 0, 1))
    return f


def divine_plume_bow(stage=0):
    """巡礼の羽弓: 腕は二枚の大きな白い風切羽 (金の軸)。握りは空の藍、外に金の輪と朝の光の珠。羽の先は光る"""
    f = []
    f += prism("grip", (5.0, CY - 2.8, 0), (5.0, CY + 2.8, 0), 1.0, 1.0, sides=8, caps=(False, False), hint=(0, 0, 1))
    f += prism("wrap", (5.0, CY - 1.6, 0), (5.0, CY + 1.6, 0), 1.12, 1.12, sides=8, caps=(False, False), hint=(0, 0, 1))
    f += torus("trim", (6.4, CY, 0), 1.45, 0.18, 0.55, n=10, k=4, ax="z")
    f += gem((6.4, CY, -0.5), 0.6, 0.6, 0.3, segs=6, rings=2)
    up = bend(stage, [(5.0, CY + 2.4, 0), (4.6, CY + 5.6, 0), (3.2, CY + 8.6, 0), (0.8, CY + 10.8, 0), (-2.0, CY + 11.8, 0)])
    half = []
    # 金の軸 (弓の腕そのもの)
    half += curve("acc", up, [0.6, 0.55, 0.48, 0.38, 0.24], [0.7, 0.62, 0.52, 0.4, 0.26], sides=5, caps=(False, True), hint=(0, 0, 1))
    # 羽の面: 軸の外側へ、先へ流れる羽を重ねる (腕の向きに沿って寝かせる)
    for i in range(len(up) - 1):
        (x0, y0, _), (x1, y1, _) = up[i], up[i + 1]
        dx, dy = x1 - x0, y1 - y0
        ln = math.hypot(dx, dy) or 1
        dx, dy = dx / ln, dy / ln
        nx, ny = dy, -dx
        for t in (0.0, 0.5):
            bx, by = x0 + (x1 - x0) * t, y0 + (y1 - y0) * t
            L = 3.4 - 0.4 * i
            tip = (bx + dx * L * 0.85 + nx * 1.5, by + dy * L * 0.85 + ny * 1.5, 0.08 * i + 0.04 * t)
            half += feather("cloth", (bx + nx * 0.3, by + ny * 0.3, 0.08 * i + 0.04 * t), tip, 0.75 - 0.08 * i, th=0.22)
    tx, ty, _ = up[-1]
    half += feather("cloth", (tx + 0.6, ty - 0.6, 0), (tx - 1.6, ty + 1.4, 0), 0.7, th=0.25, big=False)
    half += ell("gem", (tx - 1.4, ty + 1.2, 0), (0.35, 0.35, 0.35), segs=5, rings=2)
    f += half + mirror_faces_y(half)
    lo = [(x, 2 * CY - y, z) for x, y, z in up]
    return f + string_and_arrow(stage, "legend", up, lo)


def divine_rewind_dagger(stage=0):
    """巻き戻りの短剣: 刃は黒い硝子 (縁に残り火の光)。鍔は一つ歯の欠けた金の歯車、柄頭は逆に向いた時計の針"""
    f = []
    f += prism("grip", (0, -3.2, 0), (0, 3.0, 0), 0.8, 0.76, sides=8, caps=(True, True))
    f += prism("wrap", (0, -2.6, 0), (0, 2.4, 0), 0.92, 0.92, sides=8, caps=(False, False))
    f += ringy("trim", -2.8, 1.05, 0.4)
    f += prism("spine", (0, -3.2, 0), (0, -3.8, 0), 0.6, 0.5, sides=6, caps=(True, True))
    # 柄頭: 下向きの時計の針
    f += curve("acc", [(0, -3.8, 0), (0, -5.4, 0), (0.4, -6.6, 0)], [0.32, 0.22, 0.06], [0.2, 0.15, 0.05], sides=4, caps=(True, True), hint=(0, 0, 1))
    f += ell("gem", (0, -4.2, 0), (0.35, 0.35, 0.35), segs=5, rings=2)
    # 鍔: 歯車 (一つ欠け)
    f += torus("acc", (0, 3.6, 0), 2.0, 0.42, 0.5, n=16, k=4, ax="z")
    for k in range(10):
        a = math.radians(18 + 36 * k)
        if k == 3:
            continue
        d = V(math.cos(a), math.sin(a), 0)
        f += block("acc", tuple(V(0, 3.6, 0) + d * 2.6), (0.7, 0.7, 0.8), rot=(0, 0, math.degrees(a)))
    for k in range(4):
        a = math.radians(45 + 90 * k)
        f += prism("spine", (0, 3.6, 0), (1.7 * math.cos(a), 3.6 + 1.7 * math.sin(a), 0), 0.16, 0.16, sides=4, caps=(True, True))
    f += ell("gem", (0, 3.6, -0.3), (0.55, 0.55, 0.3), segs=6, rings=2)
    # 刃: 黒い硝子、縁に残り火
    f += blade("dark", [(4.6, -1.0, 1.0, 0.36, 0.36), (7.0, -1.55, 1.55, 0.42, 0.42), (10.0, -1.45, 1.45, 0.4, 0.4),
                        (13.0, -0.9, 0.9, 0.32, 0.32), (15.2, 0, 0, 0, 0)], colmats=["gem", "dark", "gem", "gem", "dark", "gem"])
    f += gbar([(0, 5.2, -0.38), (0, 12.6, -0.3)], r=0.12, mat="trim")
    return f


def divine_promise_bell_staff(stage=0):
    """約束の鈴杖: 柄の先に白い石の三日月が上を向き、その腕に縦の金の輪が架かる。輪の四方に小さな灯 (前の自分たちの四つの灯)、下に金の鈴が四つ。頂に星"""
    f = sky_pole(-4.0, 14.0)
    f += prism("spine", (0, 13.8, 0), (0, 15.2, 0), 0.9, 1.3, sides=8, caps=(True, True))
    # 白い石の三日月 (角が上)
    pts = arc(0, 18.6, 3.2, 200, 340, 11)
    rr = [0.3 + 0.75 * math.sin(math.pi * i / 10) for i in range(11)]
    f += curve("main", pts, rr, [x * 0.8 for x in rr], sides=6, caps=(True, True), hint=(0, 0, 1))
    # 縦の金の輪
    C = (0, 18.8, 0)
    f += torus("trim", C, 3.0, 0.24, 0.24, n=20, k=4, ax="z")
    for k in range(4):
        a = math.radians(90 * k)
        p = (3.0 * math.cos(a), 18.8 + 3.0 * math.sin(a), 0)
        f += ell("gem", p, (0.55, 0.55, 0.55), segs=6, rings=3)
        f += torus("acc", p, 0.6, 0.12, 0.12, n=8, k=3, ax="z")
    # 四つの鈴 (三日月の下から)
    for k, x in enumerate((-2.2, -0.75, 0.75, 2.2)):
        y = 16.2 - abs(x) * 0.15
        f += prism("cord", (x, y + 0.4, 0), (x, y - 0.6, 0), 0.08, 0.08, sides=3, caps=(False, False))
        brings = [[V(x + r * math.cos(math.radians(30 * j)), yy, r * math.sin(math.radians(30 * j))) for j in range(12)]
                  for yy, r in ((y - 2.0, 0.5), (y - 1.85, 0.48), (y - 1.2, 0.36), (y - 0.75, 0.22), (y - 0.6, 0.08))]
        f += loft(BONE, "spine", brings, caps=("gem", "spine"))
    # 頂の星
    f += ell("gem", (0, 22.6, 0), (0.6, 0.6, 0.6), segs=6, rings=3)
    for a in (0, 90, 180, 270):
        d = V(math.cos(math.radians(a)), math.sin(math.radians(a)), 0)
        f += spike(BONE, "trim", tuple(V(0, 22.6, 0) + d * 0.5), tuple(d), 1.0, 0.24, sides=4, rings=1, hint=(0, 0, 1))
    return f


# ======================================================================== 二本目 (2026-11-01)
def divine_warden_blade(stage=0):
    """羽の番人の大剣: 幅の広いまっすぐな大剣。刃の面に白い羽の板が鱗のように重なり (金の軸)、鍔は番人の面頬の形 (金の覗きの帯に朝の光)。柄頭は畳んだ羽"""
    f = []
    f += prism("grip", (0, -4.6, 0), (0, 3.2, 0), 0.9, 0.92, sides=8, caps=(True, True))
    f += prism("wrap", (0, -3.8, 0), (0, 2.6, 0), 1.04, 1.04, sides=8, caps=(False, False))
    f += ringy("trim", -4.1, 1.1, 0.4)
    for s in (-1, 1):
        f += feather("cloth", (0, -4.6, 0.05 * s), (s * 1.6, -7.4, 0.05 * s), 0.6, th=0.3)
    f += ell("acc", (0, -4.8, 0), (0.8, 0.6, 0.8), segs=6, rings=2)
    # 鍔: 面頬 (横に広い金の板に覗きの帯)
    f += block("spine", (0, 4.0, 0), (6.4, 2.2, 2.0), taper=(0.8, 0.9))
    f += block("dark", (0, 4.2, -1.02), (4.2, 0.45, 0.2))
    f += gbar([(-1.8, 4.2, -1.15), (1.8, 4.2, -1.15)], r=0.14)
    for s in (-1, 1):
        f += spike(BONE, "acc", (s * 3.1, 4.4, 0), (s, 0.5, 0), 1.8, 0.45, sides=4, rings=1, hint=(0, 0, 1))
        # 番人の畳んだ羽 (鍔から外へ、下へ流れる)
        for i in range(4):
            bx = s * (2.6 + 0.5 * i)
            f += feather("cloth" if i % 2 == 0 else "main", (bx, 3.6 - 0.2 * i, 0.06 * i), (s * (5.4 + 0.4 * i), 1.6 - 1.0 * i, 0.06 * i), 0.62 - 0.06 * i, th=0.22)
        f += curve("acc", [(s * 2.4, 3.8, 0), (s * 4.2, 3.0, 0), (s * 5.6, 1.0, 0)], [0.24, 0.18, 0.08], [0.24, 0.18, 0.08], sides=4, caps=(True, True),
                   hint=(0, 0, 1))
    # 刃: 幅広。面に羽の鱗
    f += blade("main", [(5.2, -2.4, 2.4, 0.62, 0.62), (11.0, -2.3, 2.3, 0.6, 0.6), (17.0, -2.1, 2.1, 0.55, 0.55), (21.5, -1.6, 1.6, 0.46, 0.46),
                        (24.5, -0.8, 0.8, 0.32, 0.32), (26.2, 0, 0, 0, 0)])
    for k in range(5):
        y = 7.0 + k * 3.4
        w = 1.9 - k * 0.22
        for z in (-0.66, 0.66):
            f += feather("cloth", (0, y, z), (0, y + 3.0, z), w, th=0.12, big=True)
        f += gbar([(0, y + 0.2, -0.7), (0, y + 2.6, -0.7)], r=0.09, mat="acc")
    return f


def divine_ash_hammer(stage=0):
    """灰打ちの大鎚: 頭は炉の島の金床の形 (白い石の角ばった台、片側は角のように尖る)。胴に朝の光の熱の継ぎ目が走り、金の箍が二本。柄は空の藍、尻に小さなふいごの輪"""
    f = sky_pole(-4.2, 10.4, r=1.0)
    f += torus("trim", (0, -4.6, 0), 0.9, 0.2, 0.3, n=10, k=4, ax="y")
    f += prism("spine", (0, 10.0, 0), (0, 11.6, 0), 1.1, 1.6, sides=8, caps=(True, True))
    cy = 14.0
    # 金床の台 (+x が打つ面、-x が角)
    f += block("main", (0.6, cy, 0), (5.6, 4.2, 4.0), taper=(1.0, 1.0))
    f += block("main", (3.9, cy, 0), (1.2, 4.8, 4.6))
    f += spike(BONE, "main", (-2.2, cy + 0.6, 0), (-1, 0.15, 0), 3.6, 1.6, sides=6, rings=2, hint=(0, 0, 1))
    f += block("edge", (4.6, cy, 0), (0.3, 4.4, 4.2))
    # 熱の継ぎ目 (光る) と金の箍
    f += gbar([(-1.6, cy - 1.0, -2.05), (0.0, cy + 0.4, -2.05), (1.2, cy - 0.6, -2.05), (2.8, cy + 0.8, -2.05)], r=0.16)
    f += gbar([(-1.6, cy - 1.0, 2.05), (0.0, cy + 0.4, 2.05), (1.2, cy - 0.6, 2.05), (2.8, cy + 0.8, 2.05)], r=0.16)
    for x in (-0.6, 2.2):
        f += block("trim", (x, cy, 0), (0.5, 4.5, 4.3))
    f += ell("gem", (4.75, cy, 0), (0.2, 1.0, 1.0), segs=8, rings=3)
    return f


def divine_eyelid_staff(stage=0):
    """雲の瞼の杖: 柄の先に、雲の白の二枚の瞼が上下から半ば閉じた大きな一つ目 (朝の光の瞳、金の虹彩)。目のまわりを金の細い輪が囲み、上に小さな雲の房"""
    f = sky_pole(-4.0, 13.6)
    f += prism("spine", (0, 13.4, 0), (0, 14.8, 0), 0.9, 1.4, sides=8, caps=(True, True))
    C = (0, 18.2, 0)
    f += ell("main", C, (2.4, 2.4, 2.0), segs=10, rings=5)
    f += ell("gem", (0, 18.2, -1.7), (1.0, 1.0, 0.5), segs=8, rings=3)
    f += torus("acc", (0, 18.2, -1.55), 1.3, 0.16, 0.16, n=14, k=3, ax="z")
    f += ell("dark", (0, 18.2, -2.05), (0.4, 0.55, 0.15), segs=6, rings=2)
    # 瞼 (上下から半ば閉じる)
    for s in (-1, 1):
        f += ell("cloth", (0, 18.2 + s * 1.6, -0.6), (2.7, 1.25, 2.1), segs=10, rings=4)
        f += torus("trim", (0, 18.2 + s * 1.05, -1.25), 2.0, 0.12, 0.12, n=12, k=3, ax="y")
    f += torus("trim", C, 3.1, 0.18, 0.18, n=20, k=3, ax="z")
    # 雲の房
    for p, r in (((0, 21.8, 0), 0.9), ((-0.8, 21.4, 0.2), 0.6), ((0.8, 21.5, -0.2), 0.65)):
        f += ell("cloth", p, (r, r * 0.8, r), segs=6, rings=3)
    return f


def divine_mist_bow(stage=0):
    """霞鳥の弓: 腕は霞鳥の翼 (灰白の羽を重ね、先は金)。握りの外に金の鈴が一つ、腕の付け根に霞の筋 (空の光)"""
    f = []
    f += prism("grip", (5.0, CY - 2.8, 0), (5.0, CY + 2.8, 0), 1.0, 1.0, sides=8, caps=(False, False), hint=(0, 0, 1))
    f += prism("wrap", (5.0, CY - 1.6, 0), (5.0, CY + 1.6, 0), 1.12, 1.12, sides=8, caps=(False, False), hint=(0, 0, 1))
    brings = [[V(6.6 + rr * math.cos(math.radians(30 * j)), yy, rr * math.sin(math.radians(30 * j))) for j in range(12)]
              for yy, rr in ((CY - 1.0, 0.8), (CY - 0.85, 0.78), (CY - 0.1, 0.6), (CY + 0.4, 0.36), (CY + 0.5, 0.1))]
    f += loft(BONE, "acc", brings, caps=("gem", "acc"))
    f += prism("cord", (5.6, CY + 0.4, 0), (6.6, CY + 0.5, 0), 0.08, 0.08, sides=3, caps=(False, False))
    up = bend(stage, [(5.0, CY + 2.4, 0), (4.4, CY + 5.6, 0), (2.8, CY + 8.4, 0), (0.6, CY + 10.4, 0), (-2.2, CY + 11.4, 0)])
    half = []
    half += curve("cloth", up, [0.55, 0.5, 0.42, 0.34, 0.2], [0.65, 0.58, 0.48, 0.36, 0.22], sides=5, caps=(False, True), hint=(0, 0, 1))
    for i in range(len(up) - 1):
        (x0, y0, _), (x1, y1, _) = up[i], up[i + 1]
        dx, dy = x1 - x0, y1 - y0
        ln = math.hypot(dx, dy) or 1
        dx, dy = dx / ln, dy / ln
        nx, ny = dy, -dx
        for t, mat in ((0.0, "cloth"), (0.5, "main")):
            bx, by = x0 + (x1 - x0) * t, y0 + (y1 - y0) * t
            L = 3.8 - 0.45 * i
            tip = (bx + dx * L * 0.7 + nx * 1.8, by + dy * L * 0.7 + ny * 1.8, 0.08 * i + 0.04 * t)
            half += feather(mat, (bx + nx * 0.2, by + ny * 0.2, 0.08 * i + 0.04 * t), tip, 0.8 - 0.09 * i, th=0.22)
    tx, ty, _ = up[-1]
    half += feather("acc", (tx + 0.6, ty - 0.4, 0), (tx - 1.4, ty + 1.2, 0), 0.6, th=0.25, big=False)
    half += gbar([(4.8, CY + 3.0, -0.5), (3.6, CY + 7.0, -0.45), (1.6, CY + 9.6, -0.4)], r=0.1)
    f += half + mirror_faces_y(half)
    lo = [(x, 2 * CY - y, z) for x, y, z in up]
    return f + string_and_arrow(stage, "legend", up, lo)


def divine_moth_dagger(stage=0):
    """灯蛾の短剣: 刃は灯蛾の二枚の翅 (白い石の薄い二枚刃が少し開き、翅の目の紋が光る)。鍔は金の触角が左右へ、柄頭に小さな灯"""
    f = []
    f += prism("grip", (0, -3.2, 0), (0, 3.0, 0), 0.8, 0.76, sides=8, caps=(True, True))
    f += prism("wrap", (0, -2.6, 0), (0, 2.4, 0), 0.92, 0.92, sides=8, caps=(False, False))
    f += ringy("trim", -2.8, 1.05, 0.4)
    f += prism("acc", (0, -3.2, 0), (0, -4.0, 0), 0.6, 0.4, sides=6, caps=(True, True))
    f += ell("gem", (0, -4.6, 0), (0.55, 0.65, 0.55), segs=6, rings=3)
    # 鍔: 胴と触角
    f += ell("spine", (0, 3.8, 0), (1.2, 1.0, 1.0), segs=8, rings=3)
    for s in (-1, 1):
        f += curve("acc", [(s * 0.6, 4.4, 0), (s * 2.0, 5.4, 0), (s * 3.2, 5.4, 0), (s * 3.8, 4.8, 0)], [0.18, 0.15, 0.12, 0.08], [0.18, 0.15, 0.12, 0.08],
                   sides=4, caps=(True, True), hint=(0, 0, 1))
        f += ell("gem", (s * 3.9, 4.7, 0), (0.25, 0.25, 0.25), segs=5, rings=2)
    # 刃: 少し開いた二枚の翅
    for s in (-1, 1):
        f += blade("main", [(4.4, -0.7, 0.7, 0.25, 0.25, s * 0.3), (7.0, -1.4, 1.1, 0.3, 0.3, s * 0.9), (10.0, -1.5, 1.2, 0.28, 0.28, s * 1.3),
                            (12.6, -1.0, 0.8, 0.22, 0.22, s * 1.5), (14.4, 0, 0, 0, 0, s * 1.5)])
        f += gem((s * 1.0, 8.6, -0.32), 0.5, 0.5, 0.14, segs=6, rings=2)
        f += torus("trim", (s * 1.0, 8.6, -0.3), 0.6, 0.08, 0.08, n=10, k=3, ax="z")
    return f


def divine_keeper_bell_staff(stage=0):
    """鐘守の鈴杖: 柄の先に、最後の鐘守の小さな鐘楼 (四本の白い石の柱、金の屋根、真ん中に吊った金の鐘と光る撞木)。屋根の頂に小さな鐘守の旗"""
    f = sky_pole(-4.0, 14.0)
    f += prism("spine", (0, 13.8, 0), (0, 15.0, 0), 0.9, 1.3, sides=8, caps=(True, True))
    f += block("main", (0, 15.3, 0), (4.4, 0.6, 4.4))
    for x in (-1.8, 1.8):
        for z in (-1.8, 1.8):
            f += prism("main", (x, 15.5, z), (x, 20.0, z), 0.35, 0.35, sides=6, caps=(True, True))
    f += block("trim", (0, 20.2, 0), (4.8, 0.5, 4.8))
    f += spike(BONE, "acc", (0, 20.4, 0), (0, 1, 0), 3.0, 3.0, sides=4, rings=1, hint=(0, 0, 1))
    # 鐘と撞木
    f += prism("cord", (0, 20.0, 0), (0, 19.2, 0), 0.08, 0.08, sides=3, caps=(False, False))
    brings = [[V(rr * math.cos(math.radians(30 * j)), yy, rr * math.sin(math.radians(30 * j))) for j in range(12)]
              for yy, rr in ((16.4, 1.35), (16.6, 1.3), (17.6, 1.0), (18.6, 0.7), (19.2, 0.3))]
    f += loft(BONE, "acc", brings, caps=("gem", "acc"))
    f += ell("gem", (0, 16.2, 0), (0.35, 0.35, 0.35), segs=5, rings=2)
    # 旗
    f += prism("trim", (0, 23.3, 0), (0, 25.0, 0), 0.08, 0.08, sides=3, caps=(True, True))
    f += block("cloth", (0.7, 24.5, 0), (1.3, 0.8, 0.08))
    return f


def _w(fn, jp, tier, glow, pose, grip, cen, scale, inlay, stages=1):
    e = dict(jp=jp, tier=tier, glow=glow, pose=pose, grip=grip, cen=cen, scale=scale,
             opts={"a": ((lambda stage=0: fn(stage)), inlay, ("edge",), (fn.__doc__ or "").split(":", 1)[-1].strip())})
    if stages > 1:
        e["stages"] = stages
    return e


ST = dict(pose="whale", grip=(0.0, 1.5, 0.0), cen=(0.0, 9.5, 0.0), scale=9.0)
HM = dict(pose="ember", grip=(0.0, 0.0, 0.0), cen=(0.0, 7.5, 0.0), scale=10.0)
BW = dict(pose="thunder", grip=(5.0, 8.0, 0.0), cen=(2.0, 8.0, 0.0), scale=11.5, stages=4)
DG = dict(pose="whale", grip=(0.0, 0.6, 0.0), cen=(0.0, 4.8, 0.0), scale=15.0)
SW = dict(pose="whale", grip=(0.0, 1.0, 0.0), cen=(0.0, 11.0, 0.0), scale=8.2)
WEAPONS = {
    "divine_sky_sword": _w(divine_sky_sword, "天穹の剣", "legend", "dawn", inlay=inlay_line(["main"], 99.0, 99.5), **SW),
    "divine_bell_hammer": _w(divine_bell_hammer, "七つ鐘の大鎚", "legend", "dawn", inlay=inlay_line(["acc"], 99.0, 99.5), **HM),
    "divine_dawn_staff": _w(divine_dawn_staff, "暁の灯杖", "legend", "dawn", inlay=inlay_line(["grip"], 99.0, 99.5), **ST),
    "divine_plume_bow": _w(divine_plume_bow, "巡礼の羽弓", "legend", "dawn", inlay=inlay_line(["acc"], 99.0, 99.5), **BW),
    "divine_rewind_dagger": _w(divine_rewind_dagger, "巻き戻りの短剣", "legend", "ember", inlay=inlay_line(["dark"], 99.0, 99.5), **DG),
    "divine_promise_bell_staff": _w(divine_promise_bell_staff, "約束の鈴杖", "legend", "sky", inlay=inlay_line(["grip"], 99.0, 99.5), **ST),
    "divine_warden_blade": _w(divine_warden_blade, "羽の番人の大剣", "legend", "dawn", inlay=inlay_line(["main"], 99.0, 99.5), **SW),
    "divine_ash_hammer": _w(divine_ash_hammer, "灰打ちの大鎚", "legend", "ember", inlay=inlay_line(["main"], 99.0, 99.5), **HM),
    "divine_eyelid_staff": _w(divine_eyelid_staff, "雲の瞼の杖", "legend", "dawn", inlay=inlay_line(["grip"], 99.0, 99.5), **ST),
    "divine_mist_bow": _w(divine_mist_bow, "霞鳥の弓", "legend", "sky", inlay=inlay_line(["acc"], 99.0, 99.5), **BW),
    "divine_moth_dagger": _w(divine_moth_dagger, "灯蛾の短剣", "legend", "dawn", inlay=inlay_line(["main"], 99.0, 99.5), **DG),
    "divine_keeper_bell_staff": _w(divine_keeper_bell_staff, "鐘守の鈴杖", "legend", "dawn", inlay=inlay_line(["grip"], 99.0, 99.5), **ST),
}
