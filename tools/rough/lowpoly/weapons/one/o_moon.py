"""六章「月」の職の武器 12 本 (6 職それぞれにレア一本とヒロイック一本) の一品一案 (docs/design/03 の 12.2)。月の決まり (秤・満ち引き・残像・欠け・星) を形にする。
塗りは月の塗り (paint_moon.py): main = 月の石 (クレーター)、edge/spine/acc/guard = 銀、grip/wrap = 藍、gem = 月の光 (moon) / 星 (star) / 残像 (shade)、trim = 階級の縁。
豪華さ: 五章の武器と同じ段 (~650 面)、太刀は一段上。
"""
from __future__ import annotations

import math

from kit_one import *  # noqa: F401,F403
from kit_one import ell, strand, ringy, gbar, gem, arc, bend, mirror_faces_y, string_and_arrow, inlay_line, CY
from st_kit import torus


def indigo_pole(y0, y1, r=0.9):
    """藍の柄: 握りの巻き、銀の輪三つ、尻の銀の突き"""
    f = prism("grip", (0, y0, 0), (0, y1, 0), r, r * 0.95, sides=8, caps=(True, True))
    f += prism("wrap", (0, -1.6, 0), (0, 3.0, 0), r + 0.16, r + 0.16, sides=8, caps=(False, False))
    for y in (-1.8, 3.2):
        f += ringy("trim", y, r + 0.3, 0.45)
    for y in (6.4, 10.0):
        f += ringy("spine", y, r + 0.2, 0.55, sides=8)
    f += spike(BONE, "spine", (0, y0, 0), (0, -1, 0), 1.6, r, sides=8, rings=1, hint=(0, 0, 1))
    return f


def crescent_x(mat, c, R, a0, a1, t, n=9, z=0.0):
    """xy 平面の三日月 (弧の真ん中が太く、両端が細い)"""
    pts = arc(c[0], c[1], R, a0, a1, n, z=z + (c[2] if len(c) > 2 else 0.0))
    rr = [t * (0.35 + 0.65 * math.sin(math.pi * i / (n - 1))) for i in range(n)]
    return curve(mat, pts, rr, [x * 0.85 for x in rr], sides=6, caps=(True, True), hint=(0, 0, 1))


def weight(x0, length, R, sgn, mat="main"):
    """分銅 (くびれた円盤の重り)。x0 から sgn の向きへ length。軸は x"""
    prof = ((0.0, 0.82), (0.22, 1.0), (0.5, 0.74), (0.78, 1.0), (1.0, 0.82))
    rings = [[V(x0 + sgn * length * u, 12.4 + R * k * math.cos(math.radians(30 * j)), R * k * math.sin(math.radians(30 * j))) for j in range(12)]
             for u, k in prof]
    if sgn < 0:
        rings = rings[::-1]
    return loft(BONE, mat, rings, caps=("trim", "trim"))


def moon_scale_hammer(stage=0):
    """月秤の鎚: 頭は天秤の竿。打つ側 (+x) に大きな月の石の分銅、背に小さな分銅。真ん中の支点の上に銀の三日月が載り、その腕に星が抱かれる。竿から秤の針が上へ立つ"""
    f = indigo_pole(-3.6, 10.8, r=0.95)
    # 柄尻の小さな分銅
    rings = [[V(1.0 * k * math.cos(math.radians(30 * j)), y, 1.0 * k * math.sin(math.radians(30 * j))) for j in range(12)]
             for y, k in ((-3.6, 0.8), (-4.0, 1.0), (-4.4, 0.75), (-4.8, 1.0), (-5.2, 0.6))]
    f += loft(BONE, "acc", rings[::-1], caps=("trim", "acc"))
    # 支点の台 (柄の先) と竿
    f += block("spine", (0, 11.2, 0), (2.2, 1.6, 2.2), taper=(0.8, 0.8))
    f += prism("acc", (-3.0, 12.4, 0), (3.0, 12.4, 0), 0.85, 0.85, sides=8, caps=(True, True), hint=(0, 1, 0))
    f += block("main", (0, 12.4, 0), (2.6, 2.6, 2.4))
    f += gem((0, 12.4, -1.2), 0.55, 0.55, 0.22, segs=6, rings=1)
    # 分銅 (打つ側は大きく、背は小さく。重いほうへ傾く天秤)
    f += weight(2.6, 3.2, 2.4, 1)
    f += prism("trim", (5.7, 12.4, 0), (6.0, 12.4, 0), 1.7, 1.7, sides=12, caps=(True, True), hint=(0, 1, 0))
    f += weight(-2.6, 2.2, 1.6, -1)
    f += spike(BONE, "edge", (-4.7, 12.4, 0), (-1, 0, 0), 1.0, 0.7, sides=6, rings=1, hint=(0, 1, 0))
    for x in (2.7, -2.7):
        f += prism("spine", (x - 0.25, 12.4, 0), (x + 0.25, 12.4, 0), 1.15, 1.15, sides=8, caps=(True, True), hint=(0, 1, 0))
    # 支点の上の三日月と抱かれた星、秤の針
    f += prism("spine", (0, 13.6, 0), (0, 14.4, 0), 0.5, 0.45, sides=6, caps=(True, True))
    f += crescent_x("edge", (0, 16.2, 0), 2.1, 200, 340, 0.75)
    f += spike(BONE, "edge", (0, 14.5, 0), (0, 1, 0), 3.8, 0.28, sides=4, rings=1, hint=(0, 0, 1))
    f += gem((0, 16.6, 0), 0.75, 0.75, 0.75, segs=6, rings=2)
    for a in (0, 90, 180, 270):
        d = V(math.cos(math.radians(a)), math.sin(math.radians(a)), 0)
        f += spike(BONE, "gem", tuple(V(0, 16.6, 0) + d * 0.6), tuple(d), 0.8, 0.22, sides=4, rings=1, hint=(0, 0, 1))
    return f


def moon_tide_staff(stage=0):
    """満ち引きの杖: 柄の先に銀の三日月の揺り籠。籠に抱かれた月の石の珠は半分が満ちの光、半分が引きの影。上に小さな逆さの三日月が蓋をして星を戴く。籠の下から潮の布が二筋"""
    f = indigo_pole(-4.0, 13.6)
    f += prism("spine", (0, 13.4, 0), (0, 14.6, 0), 1.0, 1.4, sides=8, caps=(True, True))
    f += ringy("trim", 14.6, 1.5, 0.4)
    # 揺り籠 (下の三日月) と珠
    f += crescent_x("edge", (0, 18.0, 0), 3.4, 195, 345, 1.0, n=11)
    f += prism("spine", (0, 14.4, 0), (0, 15.0, 0), 0.7, 0.7, sides=6, caps=(True, True))
    f += ell("main", (0, 18.0, 0), (2.3, 2.3, 2.3), segs=10, rings=5)
    f += ell("gem", (0.6, 18.0, 0), (2.0, 2.34, 2.34), segs=10, rings=5)
    f += torus("trim", (0, 18.0, 0), 2.32, 0.12, 0.2, n=12, k=3, ax="x")
    # 蓋の逆さの三日月と星
    f += crescent_x("acc", (0, 18.4, 0), 3.0, 25, 155, 0.7, n=9)
    for s in (1, -1):
        f += ell("trim", (s * 3.15, 17.6, 0), (0.45, 0.55, 0.45), segs=6, rings=1)
    f += spike(BONE, "acc", (0, 21.2, 0), (0, 1, 0), 1.4, 0.4, sides=4, rings=1, hint=(0, 0, 1))
    f += gem((0, 22.9, 0), 0.5, 0.65, 0.5, segs=5, rings=1)
    # 満ち引きの四つの位相 (籠の下の小さな珠: 新月・三日月・半月・満月)
    for k, (x, mat) in enumerate(((-1.8, "dark"), (-0.6, "acc"), (0.6, "main"), (1.8, "gem"))):
        a = math.radians(230 + 80 * k / 3)
        f += ell(mat, (3.4 * math.cos(a) * 0.98, 18.0 + 3.4 * math.sin(a) * 0.98, -0.85), (0.38, 0.38, 0.3), segs=6, rings=1)
    # 潮の布
    for s in (1, -1):
        f += strand("cloth", (s * 1.2, 15.0, 0.3), 4.2, sway=s * 0.5, r=0.3, n=4)
    return f


def moon_waning_bow(stage=0):
    """下弦の弓: 藍の腕に銀の背を張った三日月の弓。握りの外に満月の円盤。腕のそばを、半ば透けた残像の腕がもう一本、少し遅れて沿う。先は小さな三日月の鉤"""
    f = []
    f += prism("grip", (5.0, CY - 2.8, 0), (5.0, CY + 2.8, 0), 1.0, 1.0, sides=8, caps=(False, False), hint=(0, 0, 1))
    f += prism("wrap", (5.0, CY - 1.6, 0), (5.0, CY + 1.6, 0), 1.12, 1.12, sides=8, caps=(False, False), hint=(0, 0, 1))
    # 満月の円盤 (z 向き)
    f += prism("main", (6.4, CY, -0.5), (6.4, CY, 0.5), 1.7, 1.7, sides=12, caps=(True, True), hint=(1, 0, 0))
    f += torus("trim", (6.4, CY, 0), 1.75, 0.18, 0.55, n=12, k=4, ax="z")
    f += gem((6.4, CY, -0.6), 0.5, 0.5, 0.2, segs=6, rings=1)
    up = bend(stage, [(5.0, CY + 2.4, 0), (4.6, CY + 5.2, 0), (3.2, CY + 7.9, 0), (1.0, CY + 9.9, 0), (-1.6, CY + 10.8, 0)])
    half = []
    half += curve("grip", up, [0.95, 0.88, 0.75, 0.58, 0.38], [1.1, 1.0, 0.85, 0.66, 0.42], sides=5, caps=(False, True), hint=(0, 0, 1))
    half += curve("acc", [(x + 0.7, y, 0) for x, y, z in up[:4]], [0.28, 0.26, 0.22, 0.14], [0.7, 0.65, 0.55, 0.3], sides=4, caps=(True, True), hint=(0, 0, 1))
    # 残像の腕 (両端は腕に沈み、真ん中で外へ離れる)
    ghost = [(x + 1.5 * math.sin(math.pi * i / (len(up) - 1)) + 0.3, y + 0.4 * math.sin(math.pi * i / (len(up) - 1)), 0.0) for i, (x, y, z) in enumerate(up)]
    half += curve("gem", ghost, [0.2, 0.26, 0.26, 0.22, 0.16], [0.2, 0.26, 0.26, 0.22, 0.16], sides=4, caps=(True, True), hint=(0, 0, 1))
    tx, ty, _ = up[-1]
    half += crescent_x("trim", (tx - 0.2, ty + 0.9, 0), 0.9, 120, 300, 0.4, n=7)
    half += ell("spine", (tx, ty, 0), (0.55, 0.55, 0.55), segs=6, rings=1)
    f += half + mirror_faces_y(half)
    lo = [(x, 2 * CY - y, z) for x, y, z in up]
    return f + string_and_arrow(stage, "rare", up, lo)


def moon_crescent_dagger(stage=0):
    """欠け月の短剣: 刃そのものが細い三日月 (内の縁が刃)。鍔は新月の黒い円盤に銀の縁と小さな星、柄頭は銀の三日月の環"""
    f = []
    f += prism("grip", (0, -3.2, 0), (0, 3.0, 0), 0.8, 0.76, sides=8, caps=(True, True))
    f += prism("wrap", (0, -2.6, 0), (0, 2.4, 0), 0.92, 0.92, sides=8, caps=(False, False))
    f += ringy("trim", -2.8, 1.05, 0.4)
    # 柄頭の三日月の環
    f += prism("spine", (0, -3.2, 0), (0, -3.7, 0), 0.6, 0.5, sides=6, caps=(True, True))
    f += crescent_x("acc", (0, -4.9, 0), 1.2, 40, 320, 0.45, n=11)
    # 鍔 (新月の円盤)
    f += prism("dark", (0, 3.0, 0), (0, 3.7, 0), 1.9, 1.9, sides=12, caps=(True, True))
    f += torus("trim", (0, 3.35, 0), 1.95, 0.2, 0.38, n=12, k=4, ax="y")
    f += gem((0, 3.35, -1.95), 0.32, 0.32, 0.2, segs=5, rings=1)
    f += prism("spine", (0, 3.7, 0), (0, 4.5, 0), 0.95, 0.8, sides=6, caps=(True, True))
    # 刃: 三日月 (根から外へ膨らみ、先が内へ返る)
    st = []
    for y, w, cx in ((4.4, 1.1, 0.0), (6.4, 1.35, -0.7), (8.6, 1.45, -1.3), (10.8, 1.3, -1.35), (12.6, 0.95, -0.8), (14.0, 0.5, 0.1), (14.9, 0.0, 1.0)):
        t = 0.36 * min(1.0, w / 0.9) if w > 0 else 0.0
        st.append((y, -w, w * 0.55, t, t, cx))
    f += blade("spine", st, colmats=["spine", "spine", "edge", "edge", "spine", "spine"])
    f += gbar([(-0.9, 6.6, -0.33), (-1.4, 8.8, -0.33), (-1.4, 10.8, -0.33), (-0.9, 12.4, -0.3)], r=0.16)
    f += gbar([(-0.9, 6.6, 0.33), (-1.4, 8.8, 0.33), (-1.4, 10.8, 0.33), (-0.9, 12.4, 0.3)], r=0.16)
    return f


def moon_starlight_bell_staff(stage=0):
    """星灯りの鈴杖: 柄の先に銀の大きな輪 (錫杖の形)。輪の真ん中に四方へ光る星が細い銀の輻で留められ、輪の両脇に星の口の鈴が下がる。輪の頂に小さな三日月"""
    f = indigo_pole(-4.0, 14.2)
    f += prism("spine", (0, 14.0, 0), (0, 15.2, 0), 0.9, 0.6, sides=8, caps=(True, True))
    C = V(0, 18.8, 0)
    f += torus("acc", tuple(C), 3.6, 0.42, 0.42, n=16, k=6, ax="z")
    # 星と輻
    f += gem(tuple(C), 0.8, 0.8, 0.6, segs=6, rings=2)
    for a in (90, 180, 270, 0):
        d = V(math.cos(math.radians(a)), math.sin(math.radians(a)), 0)
        f += spike(BONE, "gem", tuple(C + d * 0.6), tuple(d), 1.7 if a in (90, 270) else 1.3, 0.38, sides=4, rings=1, hint=(0, 0, 1))
    for a in (45, 135, 225, 315):
        d = V(math.cos(math.radians(a)), math.sin(math.radians(a)), 0)
        f += prism("spine", tuple(C + d * 0.6), tuple(C + d * 3.4), 0.12, 0.12, sides=4, caps=(True, True))
        f += ell("trim", tuple(C + d * 1.9), (0.22, 0.22, 0.22), segs=4, rings=1)
    # 頂の三日月
    f += crescent_x("trim", (0, 23.2, 0), 1.0, 200, 340, 0.4, n=7)
    f += prism("spine", (0, 22.25, 0), (0, 22.45, 0), 0.32, 0.3, sides=6)
    # 両脇の鈴 (星の口)
    for s in (1, -1):
        x = s * 4.0
        f += prism("cord", (x, 18.8, 0), (x, 16.6, 0), 0.1, 0.1, sides=3, caps=(False, False))
        rings = [[V(x + rr * math.cos(math.radians(30 * k)), y, rr * math.sin(math.radians(30 * k))) for k in range(12)]
                 for y, rr in ((14.4, 0.95), (14.6, 0.9), (15.6, 0.7), (16.4, 0.5), (16.8, 0.2))]
        f += loft(BONE, "spine", rings, caps=("gem", "spine"))
        f += torus("trim", (x, 14.55, 0), 0.95, 0.1, 0.12, n=12, k=3, ax="y")
    return f


def _sori(y):
    return -0.009 * (y - 5.0) ** 2


def moon_waning_blade(stage=0):
    """残月の太刀: 反りのある銀の長い太刀。鎬に沿って残像の光が一筋走り、刃の上に半ば透けた二の刃が少し遅れて重なる。鍔は藍の丸鍔に銀の三日月、柄は藍の菱巻き、柄頭から三日月の根付"""
    f = []
    # 柄 (菱巻き) と柄頭
    f += prism("grip", (0, -4.0, 0), (0, 3.3, 0), 0.82, 0.86, sides=8, caps=(True, True))
    f += prism("wrap", (0, -3.5, 0), (0, 2.8, 0), 0.98, 0.98, sides=8, caps=(False, False))
    f += prism("spine", (0, -4.0, 0), (0, -4.6, 0), 0.95, 0.7, sides=8, caps=(True, True))
    f += ringy("trim", -3.8, 1.04, 0.35)
    f += ell("trim", (0.0, -1.0, -0.98), (0.25, 0.5, 0.15), segs=4, rings=1)
    # 根付 (紐と三日月と星の珠)
    f += curve("cord", [(0, -4.6, 0), (0.4, -5.6, 0.2), (0.6, -6.6, 0.2)], [0.12] * 3, [0.12] * 3, sides=4, caps=(True, True))
    f += crescent_x("acc", (0.6, -7.5, 0.2), 0.8, 30, 330, 0.3, n=9)
    f += gem((0.6, -7.5, 0.2), 0.3, 0.3, 0.3, segs=5, rings=1)
    # 鍔 (藍の丸鍔、上面に銀の三日月) と鎺
    f += prism("dark", (0, 3.3, 0), (0, 3.9, 0), 2.3, 2.3, sides=14, caps=(True, True))
    f += torus("trim", (0, 3.6, 0), 2.32, 0.16, 0.32, n=14, k=4, ax="y")
    cres = [(1.5 * math.cos(math.radians(a)), 3.98, 1.5 * math.sin(math.radians(a))) for a in range(100, 261, 20)]
    f += curve("edge", cres, [0.12, 0.25, 0.35, 0.4, 0.4, 0.4, 0.35, 0.25, 0.12], [0.12] * 9, sides=4, caps=(True, True), hint=(0, 1, 0))
    f += block("acc", (0, 4.4, 0), (1.5, 1.0, 0.95))
    # 刃: 反り (刃は +x、峰は -x)
    st = []
    for y in (4.9, 8.0, 12.0, 16.0, 20.0, 22.6, 24.2):
        w = 1.0 if y < 20 else 0.75 if y < 22 else 0.5 if y < 24 else 0.0
        t = 0.42 if y < 22 else 0.3 if y < 24 else 0.0
        st.append((y, -w * 0.85, w * 1.05, t, t, _sori(y)))
    st[-1] = (25.2, 0.0, 0.0, 0.0, 0.0, _sori(25.2) + 0.6)
    f += blade("spine", st, colmats=["spine", "spine", "edge", "edge", "spine", "spine"])
    # 鎬の残像の光 (表と裏)
    ys = [6.0, 9.0, 12.0, 15.0, 18.0, 21.0]
    for z in (-0.4, 0.4):
        f += gbar([(_sori(y) - 0.35, y, z) for y in ys], r=0.15)
    # 二の刃 (半ば透けた残像、刃の外に薄く沿う。根と先で刃に沈む)
    gh = [(_sori(y) + 1.0 + 0.9 * math.sin(math.pi * (y - 7.0) / 15.0), y, 0.0) for y in (7.0, 10.0, 13.0, 16.0, 19.0, 22.0)]
    f += curve("gem", gh, [0.12, 0.2, 0.24, 0.24, 0.2, 0.12], [0.12, 0.2, 0.24, 0.24, 0.2, 0.12], sides=4, caps=(True, True), hint=(0, 0, 1))
    return f


# ================================================================== 2026-10-31: 6 職それぞれにレアとヒロイックを一本ずつ
def moon_light_sword(stage=0):
    """月光の長剣: まっすぐな銀の長剣。鍔は角を上へ向けた銀の三日月で、真ん中に満月の円盤 (月の石) を抱く。樋に月の光が一筋、柄頭は小さな月の珠"""
    f = []
    f += prism("grip", (0, -4.0, 0), (0, 3.2, 0), 0.85, 0.88, sides=8, caps=(True, True))
    f += prism("wrap", (0, -3.4, 0), (0, 2.6, 0), 1.0, 1.0, sides=8, caps=(False, False))
    f += ringy("trim", -3.7, 1.08, 0.4)
    f += ell("main", (0, -4.8, 0), (1.05, 1.0, 1.05), segs=8, rings=3)
    f += gem((0, -4.8, -0.98), 0.35, 0.35, 0.18, segs=5, rings=1)
    # 鍔: 三日月 (角が上) と満月の円盤
    f += crescent_x("acc", (0, 6.4, 0), 4.2, 205, 335, 1.15, n=11)
    f += block("spine", (0, 3.6, 0), (2.4, 1.0, 1.6), taper=(0.9, 0.9))
    f += prism("main", (0, 4.4, -0.55), (0, 4.4, 0.55), 1.55, 1.55, sides=12, caps=(True, True), hint=(1, 0, 0))
    f += torus("trim", (0, 4.4, 0), 1.6, 0.16, 0.6, n=12, k=4, ax="z")
    f += gem((0, 4.4, -0.62), 0.45, 0.45, 0.16, segs=6, rings=1)
    # 刃
    f += blade("spine", [(5.6, -1.45, 1.45, 0.55, 0.55), (12.0, -1.35, 1.35, 0.52, 0.52), (18.0, -1.1, 1.1, 0.48, 0.48),
                         (21.0, -0.7, 0.7, 0.36, 0.36), (22.8, 0, 0, 0, 0)], colmats=["edge", "spine", "edge", "edge", "spine", "edge"])
    for z in (-0.5, 0.5):
        f += gbar([(0, 6.4, z), (0, 12.0, z), (0, 17.5, z)], r=0.16)
    return f


def moon_full_hammer(stage=0):
    """望月の大鎚: 頭はまるい満月の石 (クレーター) を、金縁の銀の帯二本が十字に締める。両の打つ面は銀の円盤に月の光、頂に三日月の冠と星。首から星の房"""
    f = indigo_pole(-4.0, 10.2, r=1.0)
    f += ell("acc", (0, -4.6, 0), (1.2, 0.9, 1.2), segs=8, rings=2)
    f += prism("spine", (0, 9.8, 0), (0, 11.2, 0), 1.1, 1.6, sides=8, caps=(True, True))
    C = (0, 13.6, 0)
    f += ell("main", C, (3.2, 3.0, 3.0), segs=12, rings=6)
    f += torus("acc", C, 3.15, 0.3, 0.55, n=16, k=4, ax="x")
    f += torus("acc", C, 3.1, 0.3, 0.55, n=16, k=4, ax="z")
    f += torus("trim", C, 3.25, 0.12, 0.62, n=16, k=3, ax="x")
    for s in (1, -1):
        f += prism("spine", (s * 2.6, 13.6, 0), (s * 3.9, 13.6, 0), 2.0, 2.0, sides=12, caps=(True, True), hint=(0, 1, 0))
        f += prism("trim", (s * 3.9, 13.6, 0), (s * 4.2, 13.6, 0), 1.7, 1.7, sides=12, caps=(True, True), hint=(0, 1, 0))
        f += gem((s * 4.25, 13.6, 0), 0.25, 0.9, 0.9, segs=8, rings=2)
    # 三日月の冠と星
    f += crescent_x("trim", (0, 18.0, 0), 1.5, 205, 335, 0.55, n=9)
    f += prism("trim", (0, 16.4, 0), (0, 16.9, 0), 0.4, 0.35, sides=6, caps=(True, True))
    f += gem((0, 17.6, 0), 0.55, 0.55, 0.55, segs=6, rings=2)
    for a in (0, 90, 180, 270):
        d = V(math.cos(math.radians(a)), math.sin(math.radians(a)), 0)
        f += spike(BONE, "gem", tuple(V(0, 17.6, 0) + d * 0.45), tuple(d), 0.7, 0.2, sides=4, rings=1, hint=(0, 0, 1))
    # 星の房
    for s in (1, -1):
        f += strand("cord", (s * 0.9, 10.6, -0.6), 3.0, sway=s * 0.4, r=0.22, n=3)
        f += gem((s * 0.9 + s * 0.2, 7.5, -0.6), 0.3, 0.3, 0.3, segs=4, rings=1)
    return f


def moon_new_staff(stage=0):
    """朔の杖: 柄の先に、光を呑んだ新月の黒い珠。まわりを細い光の冠 (日食の環) がめぐり、四本の銀の三日月の爪が珠を抱く。柄に影の布が巻く"""
    f = indigo_pole(-4.0, 13.6)
    f += prism("spine", (0, 13.4, 0), (0, 14.8, 0), 0.9, 1.4, sides=8, caps=(True, True))
    C = (0, 18.2, 0)
    f += ell("dark", C, (2.4, 2.4, 2.4), segs=12, rings=6)
    f += torus("gem", C, 2.75, 0.22, 0.22, n=18, k=4, ax="z")
    f += torus("trim", C, 2.75, 0.1, 0.32, n=18, k=3, ax="y")
    for k in range(4):
        a = 45 + 90 * k
        P = lambda r, d: (r * math.cos(math.radians(a + d)), 18.2 + r * math.sin(math.radians(a + d)), 0.0)
        f += curve("acc", [P(2.2, 0), P(3.3, 0), P(3.2, 22), P(2.8, 34)], [0.38, 0.34, 0.25, 0.1], [0.38, 0.34, 0.25, 0.1], sides=5, caps=(True, True), hint=(0, 0, 1))
    f += spike(BONE, "acc", (0, 20.4, 0), (0, 1, 0), 2.4, 0.4, sides=4, rings=1, hint=(0, 0, 1))
    # 影の布
    f += curve("cloth", [(0.95, 12.6, 0), (0.0, 11.8, -1.0), (-0.95, 11.0, 0), (0.0, 10.2, 1.0), (0.95, 9.4, 0)],
               [0.2] * 5, [0.5] * 5, sides=4, caps=(True, True), hint=(0, 1, 0))
    f += strand("cloth", (0.95, 9.4, 0), 3.2, sway=0.5, r=0.32, n=4)
    return f


def moon_starfall_bow(stage=0):
    """星落ちの弓: 銀の腕に月の石を張った大きな弓。両の先は四方へ光る星、腕に沿って星の尾の粒が流れる。握りの外に金縁の星の座"""
    f = []
    f += prism("grip", (5.0, CY - 2.8, 0), (5.0, CY + 2.8, 0), 1.0, 1.0, sides=8, caps=(False, False), hint=(0, 0, 1))
    f += prism("wrap", (5.0, CY - 1.6, 0), (5.0, CY + 1.6, 0), 1.12, 1.12, sides=8, caps=(False, False), hint=(0, 0, 1))
    f += prism("spine", (6.3, CY, -0.5), (6.3, CY, 0.5), 1.3, 1.3, sides=8, caps=(True, True), hint=(1, 0, 0), phase=22.5)
    f += torus("trim", (6.3, CY, 0), 1.35, 0.16, 0.55, n=8, k=4, ax="z")
    f += gem((6.3, CY, -0.6), 0.55, 0.55, 0.2, segs=6, rings=1)
    up = bend(stage, [(5.0, CY + 2.4, 0), (4.4, CY + 5.4, 0), (2.8, CY + 8.2, 0), (0.4, CY + 10.2, 0), (-2.2, CY + 11.0, 0)])
    half = []
    half += curve("acc", up, [0.95, 0.88, 0.75, 0.58, 0.4], [1.1, 1.0, 0.85, 0.66, 0.45], sides=5, caps=(False, True), hint=(0, 0, 1))
    half += curve("main", [(x + 0.75, y, 0) for x, y, z in up[:4]], [0.3, 0.28, 0.24, 0.15], [0.8, 0.72, 0.6, 0.3], sides=4, caps=(True, True), hint=(0, 0, 1))
    for i in range(1, 4):
        (x0, y0, _), (x1, y1, _) = up[i], up[i + 1]
        for t, rr in ((0.3, 0.45), (0.75, 0.32)):
            half += ell("gem", (x0 + (x1 - x0) * t + 1.15, y0 + (y1 - y0) * t, 0), (rr, rr, rr), segs=5, rings=2)
    tx, ty, _ = up[-1]
    half += ell("trim", (tx, ty, 0), (0.5, 0.5, 0.5), segs=6, rings=1)
    for a in (0, 90, 180, 270):
        d = V(math.cos(math.radians(a + 20)), math.sin(math.radians(a + 20)), 0)
        half += spike(BONE, "gem", tuple(V(tx, ty, 0) + d * 0.4), tuple(d), 1.1, 0.28, sides=4, rings=1, hint=(0, 0, 1))
    f += half + mirror_faces_y(half)
    lo = [(x, 2 * CY - y, z) for x, y, z in up]
    return f + string_and_arrow(stage, "legend", up, lo)


def moon_umbra_dagger(stage=0):
    """朔影の短剣: 藍の黒の両刃 (木の葉の形)、縁を影の紫の光が走る。鍔は影の印の三つの黒い珠 (芯が光る)、柄頭は黒い三日月"""
    f = []
    f += prism("grip", (0, -3.2, 0), (0, 3.0, 0), 0.8, 0.76, sides=8, caps=(True, True))
    f += prism("wrap", (0, -2.6, 0), (0, 2.4, 0), 0.92, 0.92, sides=8, caps=(False, False))
    f += ringy("trim", -2.8, 1.05, 0.4)
    f += prism("spine", (0, -3.2, 0), (0, -3.7, 0), 0.6, 0.5, sides=6, caps=(True, True))
    f += crescent_x("dark", (0, -4.9, 0), 1.2, 40, 320, 0.5, n=11)
    # 鍔: 三つの印の珠を銀の腕で
    f += block("acc", (0, 3.4, 0), (4.6, 0.7, 1.0), taper=(0.8, 0.9))
    for x in (-2.6, 0.0, 2.6):
        f += ell("dark", (x, 3.5 if x else 3.9, 0), (0.75, 0.75, 0.75), segs=8, rings=3)
        f += gem((x, 3.5 if x else 3.9, -0.62), 0.3, 0.3, 0.18, segs=5, rings=1)
    # 刃: 木の葉 (藍の黒、縁は影の光)
    f += blade("grip", [(4.2, -0.9, 0.9, 0.35, 0.35), (6.4, -1.55, 1.55, 0.42, 0.42), (9.4, -1.6, 1.6, 0.42, 0.42),
                        (12.4, -1.0, 1.0, 0.34, 0.34), (14.6, 0, 0, 0, 0)], colmats=["gem", "grip", "gem", "gem", "grip", "gem"])
    f += gbar([(0, 5.0, -0.36), (0, 12.0, -0.3)], r=0.13, mat="acc")
    f += gbar([(0, 5.0, 0.36), (0, 12.0, 0.3)], r=0.13, mat="acc")
    return f


def moon_halo_bell_staff(stage=0):
    """月暈の鈴杖: 柄の先に満月の珠、そのまわりを大きな月暈 (光の輪) が水平にめぐり、輪から銀の鈴が六つ下がる。頂に三日月"""
    f = indigo_pole(-4.0, 14.0)
    f += prism("spine", (0, 13.8, 0), (0, 15.2, 0), 0.9, 1.3, sides=8, caps=(True, True))
    C = (0, 17.4, 0)
    f += ell("main", C, (2.0, 2.0, 2.0), segs=10, rings=5)
    f += torus("gem", C, 4.0, 0.26, 0.26, n=18, k=4, ax="y")
    f += torus("trim", (0, 17.0, 0), 4.0, 0.3, 0.12, n=18, k=4, ax="y")
    for k in range(4):
        a = math.radians(45 + 90 * k)
        f += prism("acc", (1.7 * math.cos(a), 17.2, 1.7 * math.sin(a)), (3.9 * math.cos(a), 17.1, 3.9 * math.sin(a)), 0.16, 0.16, sides=4, caps=(True, True))
    for k in range(6):
        a = math.radians(60 * k)
        x, z = 4.0 * math.cos(a), 4.0 * math.sin(a)
        f += prism("cord", (x, 16.9, z), (x, 15.8, z), 0.09, 0.09, sides=3, caps=(False, False))
        rings = [[V(x + rr * math.cos(math.radians(30 * j)), y, z + rr * math.sin(math.radians(30 * j))) for j in range(12)]
                 for y, rr in ((14.2, 0.62), (14.35, 0.58), (15.1, 0.45), (15.7, 0.3), (15.95, 0.12))]
        f += loft(BONE, "spine", rings, caps=("gem", "spine"))
    f += crescent_x("trim", (0, 21.0, 0), 1.1, 205, 335, 0.45, n=9)
    f += prism("trim", (0, 19.3, 0), (0, 20.1, 0), 0.3, 0.25, sides=6, caps=(True, True))
    return f


def _w(fn, jp, tier, glow, pose, grip, cen, scale, inlay, stages=1):
    e = dict(jp=jp, tier=tier, glow=glow, pose=pose, grip=grip, cen=cen, scale=scale,
             opts={"a": ((lambda stage=0: fn(stage)), inlay, ("edge",), (fn.__doc__ or "").split(":", 1)[-1].strip())})
    if stages > 1:
        e["stages"] = stages
    return e


ST = dict(pose="whale", grip=(0.0, 1.5, 0.0), cen=(0.0, 9.0, 0.0), scale=9.5)
HM = dict(pose="ember", grip=(0.0, 0.0, 0.0), cen=(0.0, 7.5, 0.0), scale=10.5)
BW = dict(pose="thunder", grip=(5.0, 8.0, 0.0), cen=(2.0, 8.0, 0.0), scale=12.0, stages=4)
DG = dict(pose="whale", grip=(0.0, 0.6, 0.0), cen=(0.0, 4.8, 0.0), scale=16.0)
SW = dict(pose="whale", grip=(0.0, 1.0, 0.0), cen=(0.0, 10.0, 0.0), scale=9.0)
WEAPONS = {
    "moon_scale_hammer": _w(moon_scale_hammer, "月秤の鎚", "rare", "moon", inlay=inlay_line(["main"], 10.5, 14.5, lambda y: 4.2 + 0.0 * y), **HM),
    "moon_tide_staff": _w(moon_tide_staff, "満ち引きの杖", "rare", "moon", inlay=inlay_line(["grip"], 5.0, 13.0), **ST),
    "moon_waning_bow": _w(moon_waning_bow, "下弦の弓", "rare", "shade", inlay=inlay_line(["main"], 99.0, 99.5), **BW),
    "moon_crescent_dagger": _w(moon_crescent_dagger, "欠け月の短剣", "rare", "moon", inlay=inlay_line(["dark"], 2.5, 4.0), **DG),
    "moon_starlight_bell_staff": _w(moon_starlight_bell_staff, "星灯りの鈴杖", "rare", "star", inlay=inlay_line(["grip"], 5.0, 13.0), **ST),
    "moon_light_sword": _w(moon_light_sword, "月光の長剣", "rare", "moon", inlay=inlay_line(["spine"], 6.0, 20.0), **SW),
    "moon_full_hammer": _w(moon_full_hammer, "望月の大鎚", "legend", "moon", inlay=inlay_line(["main"], 99.0, 99.5), **HM),
    "moon_new_staff": _w(moon_new_staff, "朔の杖", "legend", "shade", inlay=inlay_line(["main"], 99.0, 99.5), **ST),
    "moon_starfall_bow": _w(moon_starfall_bow, "星落ちの弓", "legend", "star", inlay=inlay_line(["acc"], 99.0, 99.5), **BW),
    "moon_umbra_dagger": _w(moon_umbra_dagger, "朔影の短剣", "legend", "shade", inlay=inlay_line(["main"], 99.0, 99.5), **DG),
    "moon_halo_bell_staff": _w(moon_halo_bell_staff, "月暈の鈴杖", "legend", "moon", inlay=inlay_line(["main"], 99.0, 99.5), **ST),
    "moon_waning_blade": _w(moon_waning_blade, "残月の太刀", "legend", "shade", inlay=inlay_line(["spine"], 6.0, 21.0, lambda y: _sori(y) + 0.5), **SW),
}
