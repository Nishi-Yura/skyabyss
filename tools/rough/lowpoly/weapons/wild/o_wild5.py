"""野の装備 帯 V (Lv 41〜50。深淵の下の層 (雷・灰) と深み・月・雲海) の武器 30 本 (docs/design/51 の 4.5)。一品一案。
レア = 雷の層 (雷柱・溶け鉄・雷玉・稲光・火花・雨乞い)、エピック = 灰の層と深み (双頭・鎖剣・歯車・鉄球・砂時計・眼・遠眼鏡・呼び声・鎌爪・釣り針・香炉・人形)、
ヒロイック Lv 48 = 月 (隕鉄・星の炉・星図・灯台・鏡・星鈴)、Lv 50 = 雲海 (雲断ち・天割り・虹の端・雲雀・雲隠れ・祝詞)。帯の中でいちばん豪華 (~600〜900 面)。
"""
from __future__ import annotations

import math

from kit_wild import *  # noqa: F401,F403
from kit_wild import W, haft, bow_grip, bell, lash, flag, leaf_blade, claw_hand, _slab


def _zig(pts, r=0.16, mat="gem"):
    return curve(mat, pts, r, r, sides=3, caps=(True, True))


def wild_fulgurite_sword(stage=0):
    """雷柱の剣: 雷が砂を焼いた硝子の管 (雷管石) を刃にした剣。ざらついた砂色の刃に枝分かれの光の筋が走り、鍔は銅の碍子、柄は黒い革"""
    f = haft(-4.0, 2.8, r=0.78, mat="grip", wrap=(-3.4, 2.2))
    f += ell("acc", (0, -4.3, 0), (0.8, 0.7, 0.8), segs=6, rings=1)
    for x in (-1.6, 1.6):
        f += prism("acc", (x * 0.4, 3.2, 0), (x * 1.6, 3.2, 0), 0.6, 0.45, sides=8, caps=(True, True), hint=(0, 1, 0))
        for k in range(3):
            f += prism("acc", (x * (0.8 + 0.4 * k), 3.2, 0), (x * (0.95 + 0.4 * k), 3.2, 0), 0.85, 0.85, sides=8, caps=(True, True), hint=(0, 1, 0))
    st = [(3.6, -1.15, 1.15, 0.6, 0.6), (8.0, -1.25, 1.2, 0.62, 0.62), (12.0, -1.05, 1.3, 0.58, 0.58), (16.0, -1.2, 1.0, 0.55, 0.55),
          (20.0, -0.8, 0.9, 0.42, 0.42), (23.2, 0, 0, 0, 0)]
    f += blade("main", st, colmats=["edge", "main", "edge", "edge", "main", "edge"])
    for z in (-0.62, 0.62):
        f += _zig([(0, 4.6, z), (0.4, 7.0, z), (-0.3, 9.6, z), (0.3, 12.6, z), (-0.2, 15.6, z), (0.2, 18.6, z * 0.9)], r=0.14)
        f += _zig([(-0.3, 9.6, z), (-0.8, 11.0, z)], r=0.1)
        f += _zig([(0.3, 12.6, z), (0.8, 14.0, z)], r=0.1)
    return f


def wild_twin_spear(stage=0):
    """双頭の槍: 両端に同じ黒鉄の穂を持つ槍。真ん中の握りに鉄の輪が二つ、穂の根に灰の層の煤の布が巻き、どちらの穂にも細い血の溝"""
    f = haft(-9.0, 15.0, r=0.72, mat="grip", wrap=(-1.6, 3.4))
    for y in (-2.0, 3.8):
        f += ringy("trim", y, 0.95, 0.45)
    for sgn, y0 in ((1, 15.0), (-1, -9.0)):
        f += prism("spine", (0, y0, 0), (0, y0 + sgn * 1.6, 0), 0.9, 0.7, sides=8, caps=(True, True))
        f += prism("cloth", (0, y0 - sgn * 1.4, 0), (0, y0 + sgn * 0.2, 0), 0.92, 0.92, sides=8, caps=(False, False))
        st = [(0.0, -0.8, 0.8, 0.45, 0.45), (1.4, -2.2, 2.2, 0.5, 0.5), (2.6, -2.0, 2.0, 0.48, 0.48), (4.4, -1.2, 1.2, 0.4, 0.4), (7.6, 0, 0, 0, 0)]
        head = blade("main", st)
        head += gbar([(0, 1.0, -0.5), (0, 4.6, -0.4)], r=0.14, mat="spine") + gbar([(0, 1.0, 0.5), (0, 4.6, 0.4)], r=0.14, mat="spine")
        for s in (-1, 1):
            head += spike(BONE, "main", (s * 1.6, 1.6, 0), (s, -0.8, 0), 1.6, 0.35, sides=4, rings=1)
        head = at(head, rot_z(180) if sgn < 0 else None, (0, y0 + sgn * 1.4, 0))
        f += head
    return f


def wild_chain_greatsword(stage=0):
    """淵の鎖剣: 刃が六つの節に分かれ、芯の鎖でつながる蛇腹の大剣。節のあいだに鎖の環が覗き、節ごとに少しずつずれて揺れる。鍔は錨の爪、柄頭は錨の環"""
    f = haft(-5.6, 3.0, r=0.88, mat="grip", wrap=(-4.8, 2.4))
    f += torus("spine", (0, -6.6, 0), 1.0, 0.3, 0.3, n=10, k=4, ax="z")
    for s in (-1, 1):
        f += curve("spine", [(0, 3.4, 0), (s * 2.2, 3.4, 0), (s * 3.4, 4.6, 0), (s * 3.2, 5.6, 0)], [0.55, 0.5, 0.4, 0.08], [0.45, 0.42, 0.34, 0.08], sides=5)
    f += block("spine", (0, 3.6, 0), (2.6, 1.4, 1.8))
    for i in range(6):
        y0 = 4.6 + i * 3.6
        w = 1.9 - 0.18 * i
        dx = 0.25 * math.sin(i * 1.3)
        top = 0 if i == 5 else w * 0.85
        st = [(y0, -w * 0.9 + dx, w * 0.9 + dx, 0.5, 0.5), (y0 + 1.6, -w + dx, w + dx, 0.5, 0.5), (y0 + 2.9, -top + dx, top + dx, 0.45 if top else 0, 0.45 if top else 0)]
        if i == 5:
            st[-1] = (y0 + 4.2, dx, dx, 0, 0)
        f += blade("main", st)
        if i < 5:
            f += torus("chain", (dx, y0 + 3.25, 0), 0.4, 0.12, 0.12, n=6, k=3, ax="z")
    f += prism("chain", (0, 4.0, 0), (0, 23.0, 0), 0.18, 0.18, sides=4, caps=(True, True))
    return f


def wild_meteor_greatsword(stage=0):
    """隕鉄の大剣: 月に落ちた星の鉄を叩いた大剣。黒銀の刃に隕鉄の網目が浮き、鍔は焼けた星の石を二つの爪で抱く。刃の根から先へ星の尾の光が一筋、柄頭に小さな星"""
    f = haft(-5.8, 3.0, r=0.92, mat="grip", wrap=(-5.0, 2.4))
    f += spike(BONE, "trim", (0, -5.8, 0), (0, -1, 0), 1.4, 0.7, sides=6, rings=1)
    for a in (0, 90, 180, 270):
        d = (math.cos(math.radians(a)), math.sin(math.radians(a)))
        f += spike(BONE, "gem", (0, -7.2, 0), (d[0], d[1], 0), 0.9, 0.22, sides=4, rings=1)
    f += lump("acc", (0, 4.0, 0), (2.4, 1.6, 1.8), seed=51)
    for s in (-1, 1):
        f += curve("trim", [(s * 0.8, 2.8, 0), (s * 2.6, 3.4, 0), (s * 3.0, 5.0, 0), (s * 2.2, 5.6, 0)], [0.4, 0.36, 0.3, 0.08], [0.4, 0.36, 0.3, 0.08], sides=5)
    f += ell("gem", (0, 4.2, -1.6), (0.6, 0.6, 0.3), segs=6, rings=2)
    st = [(5.0, -2.2, 2.2, 0.62, 0.62), (11.0, -2.3, 2.3, 0.6, 0.6), (17.0, -2.15, 2.15, 0.56, 0.56), (22.0, -1.7, 1.7, 0.48, 0.48),
          (25.8, -0.9, 0.9, 0.34, 0.34), (28.2, 0, 0, 0, 0)]
    f += blade("main", st)
    for z in (-0.62, 0.62):
        f += gbar([(0.0, 6.0, z), (0.1, 14.0, z * 0.97), (0.0, 22.0, z * 0.82)], r=0.18)
    return f


def wild_cloudcleaver_greatsword(stage=0):
    """雲断ちの大剣: 雲の白の大きな大剣。刃に雲の渦の透かしが三つ抜け、鍔は左右に広げた雲の翼、柄は空の藍に金の輪。刃の背に風の筋が光る"""
    f = haft(-6.0, 3.2, r=0.95, mat="grip", wrap=(-5.2, 2.6))
    for y in (-5.6, 2.8):
        f += ringy("trim", y, 1.1, 0.45)
    f += ell("acc", (0, -6.6, 0), (1.1, 0.9, 1.1), segs=8, rings=2)
    f += block("acc", (0, 3.9, 0), (3.2, 1.4, 2.0), taper=(0.85, 0.85))
    for s in (-1, 1):
        for k in range(4):
            f += feather("acc", (s * 1.2, 3.8 + 0.2 * k, 0.1), (s * (5.4 - 0.7 * k), 5.6 - 0.6 * k, 0.1), 0.85 - 0.1 * k, th=0.3, big=True)
    st = [(4.8, -2.4, 2.4, 0.6, 0.6), (11.0, -2.6, 2.6, 0.58, 0.58), (18.0, -2.4, 2.4, 0.54, 0.54), (23.0, -1.9, 1.9, 0.46, 0.46),
          (26.6, -1.0, 1.0, 0.34, 0.34), (29.0, 0, 0, 0, 0)]
    f += blade("main", st)
    for y in (9.0, 14.6, 20.0):
        f += prism("dark", (0.3, y, -0.62), (0.3, y, 0.62), 0.8, 0.8, sides=8, caps=(True, True), hint=(1, 0, 0))
        f += curve("trim", [(0.3 + 0.95 * math.cos(math.radians(a)), y + 0.95 * math.sin(math.radians(a)), -0.6) for a in range(0, 300, 40)], 0.12, 0.12, sides=3)
    for z in (-0.62, 0.62):
        f += gbar([(-1.6, 6.0, z), (-1.7, 14.0, z), (-1.3, 22.0, z * 0.9)], r=0.16)
    return f


def wild_molten_axe(stage=0):
    """溶け鉄の斧: 半ば熔けて垂れた鉄の頭の斧。刃の下の縁から熔けた鉄が滴となって垂れ、垂れの先が赤く光る。柄は焦げた鉄木"""
    f = haft(-4.4, 12.6, r=0.85, mat="grip", wrap=(-3.6, 0.8))
    f += prism("spine", (0, 11.6, 0), (0, 15.0, 0), 1.1, 1.05, sides=8, caps=(True, True))
    f += extrude("main", [(0.8, 10.6), (3.6, 9.8), (5.6, 10.8), (6.2, 13.2), (5.6, 15.8), (3.6, 16.8), (0.8, 15.6)], 1.3)
    for x, ln in ((2.4, 2.4), (3.8, 3.4), (5.0, 1.8)):
        f += strand("main", (x, 10.2, 0), ln, sway=0.2, r=0.5, n=3)
        f += ell("gem", (x + 0.1, 10.2 - ln, 0), (0.42, 0.55, 0.42), segs=6, rings=2)
    f += curve("gem", [(5.6, 10.9, 0), (6.2, 13.2, 0), (5.6, 15.6, 0)], 0.18, 0.5, sides=4, hint=(0, 0, 1))
    return f


def wild_cog_hammer(stage=0):
    """歯車の大鎚: 頭が大きな歯車の鎚。打つ面の周りに歯が並び、軸に小さな歯車が二つ噛み合う。探掘隊の機械の真鍮と鉄"""
    f = haft(-4.8, 12.6, r=1.0, mat="grip", wrap=(-4.0, 0.8))
    f += prism("main", (-2.0, 14.4, 0), (2.0, 14.4, 0), 2.8, 2.8, sides=12, caps=(True, True), hint=(0, 1, 0))
    for k in range(12):
        a = math.radians(30 * k)
        f += block("main", (0, 14.4 + 3.1 * math.cos(a), 3.1 * math.sin(a)), (3.6, 0.8, 0.9), rot=rot_x(-30 * k))
    for s in (-1, 1):
        f += prism("acc", (s * 2.0, 14.4, 0), (s * 2.9, 14.4, 0), 1.5, 1.5, sides=8, caps=(True, True), hint=(0, 1, 0))
        f += prism("spine", (s * 2.9, 14.4, 0), (s * 3.2, 14.4, 0), 0.5, 0.5, sides=6, caps=(True, True), hint=(0, 1, 0))
    f += prism("acc", (0, 11.6, -1.4), (0, 11.6, -0.9), 1.2, 1.2, sides=8, caps=(True, True), hint=(1, 0, 0))
    for k in range(8):
        a = math.radians(45 * k)
        f += block("acc", (1.35 * math.cos(a), 11.6 + 1.35 * math.sin(a), -1.15), (0.4, 0.4, 0.5))
    return f


def wild_abyss_mace(stage=0):
    """深み呑みの鉄球: 鎖でつないだ黒鉄の大きな球。球の正面に閉じかけた一つ目の瞼があり、隙から淵の紫が覗く。球に錨の棘が六本、柄は錨鉄"""
    f = haft(-4.4, 11.6, r=0.92, mat="grip", wrap=(-3.6, 0.8))
    f += ell("spine", (0, -4.6, 0), (1.1, 0.7, 1.1), segs=8, rings=1)
    f += chain((0, 11.4, 0), (0.4, 13.0, 0), n=2)
    C = (0.5, 15.6, 0)
    f += ell("main", C, (2.6, 2.6, 2.6), segs=10, rings=4)
    f += ell("gem", (C[0], C[1], -2.25), (1.0, 0.45, 0.5), segs=8, rings=2)
    for sy in (1, -1):
        f += curve("acc", [(C[0] - 1.3, C[1], -2.2), (C[0], C[1] + sy * 0.6, -2.55), (C[0] + 1.3, C[1], -2.2)], 0.22, 0.22, sides=4)
    for d in ((1, 0, 0), (-1, 0, 0), (0, 1, 0), (0.7, -0.7, 0.4), (-0.6, 0.4, 0.7), (0, -0.3, 1)):
        n = math.sqrt(sum(x * x for x in d))
        d = tuple(x / n for x in d)
        f += spike(BONE, "edge", (C[0] + d[0] * 2.3, C[1] + d[1] * 2.3, C[2] + d[2] * 2.3), d, 1.6, 0.5, sides=5, rings=1)
    return f


def wild_starforge_hammer(stage=0):
    """星の炉の大鎚: 頭が小さな炉の大鎚。黒鉄の炉の正面の口の中に星が燃え、後ろへ煙突が反る。炉の上に金床の角、柄は月の石に金の輪、打つ面は焼けた鉄"""
    f = haft(-4.8, 12.6, r=1.0, mat="grip", wrap=(-4.0, 0.8))
    for y in (-3.8, 10.8):
        f += ringy("trim", y, 1.15, 0.5)
    f += spike(BONE, "trim", (0, -4.6, 0), (0, -1, 0), 1.6, 0.9, sides=8, rings=1)
    f += block("main", (0, 14.6, 0), (6.0, 4.0, 3.6))
    f += prism("dark", (0, 14.4, -1.8), (0, 14.4, -1.9), 1.3, 1.3, sides=10, caps=(True, True), hint=(1, 0, 0))
    f += ell("gem", (0, 14.4, -1.6), (0.8, 0.8, 0.5), segs=8, rings=2)
    for a in (0, 90, 180, 270):
        d = (math.cos(math.radians(a + 45)), math.sin(math.radians(a + 45)))
        f += spike(BONE, "gem", (0, 14.4, -2.0), (d[0], d[1], -0.3), 1.2, 0.2, sides=4, rings=1)
    for s in (-1, 1):
        f += prism("edge", (s * 3.0, 14.6, 0), (s * 3.8, 14.6, 0), 1.9, 2.0, sides=8, caps=(True, True), hint=(0, 1, 0))
    f += curve("acc", [(0.8, 16.6, 0.8), (1.2, 18.4, 1.4), (0.6, 20.0, 2.2)], [0.6, 0.55, 0.5], [0.6, 0.55, 0.5], sides=6)
    f += block("acc", (-1.4, 17.0, 0), (2.4, 0.8, 2.0), taper=(0.6, 0.8))
    return f


def wild_skysplit_axe(stage=0):
    """天割りの両刃斧: 雲を割る楔の形の大きな両刃斧。左右の白い刃は鳥の翼のように反り、刃の内に空の光の筋。柄頭と頂は金の楔、柄は空の藍"""
    f = haft(-5.0, 15.0, r=0.95, mat="grip", wrap=(-4.2, 0.8))
    for y in (-3.6, 11.2):
        f += ringy("trim", y, 1.1, 0.5)
    f += spike(BONE, "trim", (0, -4.8, 0), (0, -1, 0), 1.8, 0.9, sides=6, rings=1)
    f += spike(BONE, "trim", (0, 14.8, 0), (0, 1, 0), 3.4, 1.0, sides=6, rings=2)
    for s in (-1, 1):
        pts = [(s * 0.8, 10.6), (s * 3.6, 8.6), (s * 6.4, 8.8), (s * 7.6, 11.8), (s * 7.2, 15.2), (s * 5.4, 17.6), (s * 2.8, 17.0), (s * 0.8, 15.4)]
        if s < 0:
            pts = pts[::-1]
        f += extrude("main", pts, 1.0)
        f += curve("edge", [(s * 6.4, 8.9, 0), (s * 7.6, 11.8, 0), (s * 7.2, 15.2, 0), (s * 5.4, 17.5, 0)], 0.2, 0.55, sides=4, hint=(0, 0, 1))
        for z in (-0.52, 0.52):
            f += gbar([(s * 2.0, 12.4, z), (s * 4.6, 12.0, z), (s * 6.2, 13.0, z)], r=0.16)
    return f


def wild_ballspark_staff(stage=0):
    """雷玉の杖: 雷の珠のまわりを、角度の違う三本の銅の輪がめぐる杖。輪のあいだに火花が飛び、柄の頭は碍子の段"""
    f = haft(-4.0, 13.2, r=0.75, mat="grip", wrap=(-2.4, 2.0))
    for i in range(3):
        f += prism("cloth", (0, 13.0 + i * 0.6, 0), (0, 13.4 + i * 0.6, 0), 1.0 - 0.1 * i, 1.0 - 0.1 * i, sides=8, caps=(True, True))
    C = (0, 17.6, 0)
    f += ell("gem", C, (1.5, 1.5, 1.5), segs=10, rings=4)
    for R in (rot_x(25), rot_z(60) @ rot_x(25), rot_z(-60) @ rot_x(25)):
        f += at(torus("acc", (0, 0, 0), 2.5, 0.15, 0.2, n=14, k=4, ax="y"), R, C)
    for a in (30, 150, 270):
        d = V(math.cos(math.radians(a)), 0.3, math.sin(math.radians(a)))
        f += _zig([tuple(V(*C) + d * 1.5), tuple(V(*C) + d * 2.0 + V(0, 0.4, 0)), tuple(V(*C) + d * 2.5)], r=0.1)
    return f


def wild_hourglass_staff(stage=0):
    """灰の砂時計の杖: 灰が落ちる砂時計を頭にした杖。焦げた骨の三本柱の枠に硝子の二つの球、上から下へ灰が細く落ち、下の球に灰が溜まる"""
    f = haft(-4.0, 13.2, r=0.75, mat="grip", wrap=(-2.4, 2.0))
    for y in (13.4, 21.0):
        f += prism("acc", (0, y, 0), (0, y + 0.6, 0), 1.9, 1.9, sides=8, caps=(True, True))
    for k in range(3):
        a = math.radians(120 * k)
        f += bone("acc", [(1.6 * math.cos(a), 13.9, 1.6 * math.sin(a)), (1.6 * math.cos(a), 21.1, 1.6 * math.sin(a))], 0.22)
    for y, top in ((15.8, False), (19.0, True)):
        f += ell("main", (0, y, 0), (1.3, 1.5, 1.3), segs=8, rings=3)
    f += prism("main", (0, 17.0, 0), (0, 17.8, 0), 0.35, 0.35, sides=6, caps=(False, False))
    f += ell("cloth", (0, 15.2, 0), (1.05, 0.7, 1.05), segs=8, rings=2)
    f += ell("cloth", (0, 19.4, 0), (0.9, 0.6, 0.9), segs=8, rings=2)
    f += prism("gem", (0, 15.8, 0), (0, 18.6, 0), 0.08, 0.08, sides=3, caps=(False, False))
    f += spike(BONE, "acc", (0, 21.6, 0), (0, 1, 0), 1.4, 0.4, sides=6, rings=1)
    return f


def wild_deep_eye_staff(stage=0):
    """深みの眼杖: 瞼を半ば開いた大きな眼を、根のような黒い指が五本で抱く杖。眼の瞳は淵の紫、白目は灰白、瞼は皺の皮"""
    f = haft(-4.0, 13.4, r=0.78, mat="grip", wrap=(-2.4, 2.0))
    C = (0, 17.4, 0)
    for k in range(5):
        a = math.radians(72 * k + 18)
        pts = [(0.4 * math.cos(a), 13.2, 0.4 * math.sin(a)), (2.0 * math.cos(a), 14.6, 2.0 * math.sin(a)), (2.4 * math.cos(a), 17.0, 2.4 * math.sin(a)),
               (1.6 * math.cos(a), 19.2, 1.6 * math.sin(a))]
        f += curve("spine", pts, [0.4, 0.34, 0.28, 0.06], [0.4, 0.34, 0.28, 0.06], sides=5)
    f += ell("main", C, (1.9, 1.9, 1.9), segs=10, rings=4)
    f += ell("gem", (0, 17.4, -1.7), (0.8, 0.8, 0.3), segs=8, rings=2)
    f += ell("dark", (0, 17.4, -1.95), (0.35, 0.5, 0.12), segs=6, rings=1)
    for sy, ang in ((1, 1.0), (-1, -1.0)):
        f += curve("cloth", [(-1.8, 17.4 + sy * 0.2, -0.8), (-0.8, 17.4 + sy * 1.0, -1.7), (0.8, 17.4 + sy * 1.0, -1.7), (1.8, 17.4 + sy * 0.2, -0.8)],
                   0.32, 0.32, sides=5)
    return f


def wild_starchart_staff(stage=0):
    """星図の杖: 星図の輪を重ねた天球儀を頭にした杖。月の石の柄に金の輪、天球儀の輪には星の粒が嵌まり、真ん中に小さな月の珠。頂に四方へ光る星"""
    f = haft(-4.0, 13.4, r=0.8, mat="grip", wrap=(-2.6, 2.4))
    for y in (-2.8, 2.6, 11.0):
        f += ringy("trim", y, 0.95, 0.4)
    f += spike(BONE, "trim", (0, -3.8, 0), (0, -1, 0), 1.8, 0.8, sides=8, rings=1)
    f += prism("trim", (0, 13.2, 0), (0, 14.6, 0), 0.9, 1.4, sides=8, caps=(True, True))
    C = (0, 18.0, 0)
    for R in (rot_x(90), rot_z(35) @ rot_x(90), rot_z(-35) @ rot_x(90), rot_x(15)):
        f += at(torus("acc", (0, 0, 0), 2.7, 0.15, 0.22, n=16, k=4, ax="y"), R, C)
    f += ell("main", C, (1.0, 1.0, 1.0), segs=8, rings=3)
    import random
    rnd = random.Random(48)
    for i in range(9):
        a, b = rnd.uniform(0, 2 * math.pi), rnd.uniform(-1.0, 1.0)
        p = (2.7 * math.cos(a) * math.cos(b), 18.0 + 2.7 * math.sin(b), 2.7 * math.sin(a) * math.cos(b))
        f += ell("gem", p, (0.28, 0.28, 0.28), segs=5, rings=1)
    for a in (0, 90, 180, 270):
        d = (math.cos(math.radians(a)), math.sin(math.radians(a)))
        f += spike(BONE, "gem", (0, 21.4, 0), (d[0] * 0.6, d[1] * 0.6 + 0.4, 0), 1.2, 0.26, sides=4, rings=1)
    return f


def wild_prism_staff(stage=0):
    """虹の端の杖: 雲の上に掛かる虹の端の弧を、七つの硝子の柱で作った杖。柄の頭の雲の塊から、七色の柱が弧を描いて立ち上がる。柄は雲の白に金の輪"""
    f = haft(-4.0, 13.2, r=0.8, mat="grip", wrap=(-2.6, 2.4))
    for y in (-2.8, 2.6, 11.4):
        f += ringy("trim", y, 0.95, 0.4)
    f += spike(BONE, "trim", (0, -3.8, 0), (0, -1, 0), 1.8, 0.8, sides=8, rings=1)
    for p, r in (((0, 14.2, 0), (2.0, 1.2, 1.7)), ((1.4, 14.6, 0.4), (1.2, 0.9, 1.1)), ((-1.3, 14.5, -0.3), (1.1, 0.9, 1.0))):
        f += ell("acc", p, r, segs=8, rings=2)
    for i in range(7):
        a = math.radians(150 - 20 * i)
        x0, y0 = 1.2 * math.cos(a), 14.8 + 0.6 * math.sin(a)
        x1, y1 = 3.4 * math.cos(a), 15.6 + 4.6 * math.sin(a)
        f += prism("gem", (x0, y0, 0.2 * (i - 3) * 0.1), (x1, y1, 0), 0.36, 0.28, sides=6, caps=(True, True))
    return f


def wild_flash_bow(stage=0):
    """稲光の弓: 雷管石の白い腕の弓。腕の内に枝分かれの光、先は碍子の鉤、握りは銅の巻き線"""
    f = bow_grip("acc", "acc")
    up = bend(stage, [(5.0, CY + 2.4, 0), (4.6, CY + 5.3, 0), (3.2, CY + 8.0, 0), (1.2, CY + 10.0, 0), (-1.2, CY + 10.8, 0)])
    half = []
    half += curve("main", up, [0.95, 0.88, 0.76, 0.6, 0.42], [1.0, 0.92, 0.8, 0.62, 0.44], sides=6, caps=(False, True), hint=(0, 0, 1))
    for z in (-0.92, 0.92):
        half += _zig([(x + 0.1 * ((i % 2) * 2 - 1), y, z * (1 - 0.15 * i)) for i, (x, y, _) in enumerate(up[:4])], r=0.12)
    tx, ty, _ = up[-1]
    half += prism("cloth", (tx, ty, 0), (tx - 0.8, ty + 0.6, 0), 0.5, 0.5, sides=8, caps=(True, True))
    f += half + mirror_faces_y(half)
    lo = [(x, 2 * CY - y, z) for x, y, z in up]
    return f + string_and_arrow(stage, "rare", up, lo)


def wild_scope_longbow(stage=0):
    """遠眼鏡の長弓: 探掘隊の長い弓。握りの上に真鍮の遠眼鏡が載り、腕は黒い木に真鍮の帯。先に照準の小さな環"""
    f = bow_grip("grip", "wrap", r=1.05)
    f += prism("acc", (6.4, CY + 1.0, 0), (6.4, CY + 5.4, 0), 0.55, 0.7, sides=8, caps=(True, True))
    f += prism("gem", (6.4, CY + 5.4, 0), (6.4, CY + 5.6, 0), 0.6, 0.6, sides=8, caps=(True, True))
    f += prism("acc", (5.6, CY + 2.4, 0), (6.4, CY + 2.4, 0), 0.25, 0.25, sides=4, caps=(True, True), hint=(0, 1, 0))
    up = bend(stage, [(5.0, CY + 2.4, 0), (4.7, CY + 6.2, 0), (3.6, CY + 9.6, 0), (1.6, CY + 12.4, 0), (-1.0, CY + 13.8, 0)])
    half = []
    half += curve("main", up, [0.95, 0.88, 0.76, 0.58, 0.38], [1.05, 0.96, 0.82, 0.62, 0.4], sides=6, caps=(False, True), hint=(0, 0, 1))
    for i in (1, 2, 3):
        half += ell("acc", up[i], (1.0, 0.45, 1.05), segs=6, rings=1)
    tx, ty, _ = up[-1]
    half += torus("acc", (tx, ty + 0.6, 0), 0.5, 0.12, 0.12, n=8, k=3, ax="z")
    f += half + mirror_faces_y(half)
    lo = [(x, 2 * CY - y, z) for x, y, z in up]
    return f + string_and_arrow(stage, "rare", up, lo)


def wild_calling_bow(stage=0):
    """呼び声の弓: 腕に耳のような巻き貝が並ぶ灰白の弓。握りの外に口の形の貝、腕の先から細い声の糸 (光る) が流れる"""
    f = bow_grip("grip", "wrap")
    f += ell("acc", (6.2, CY, 0), (0.9, 1.2, 0.7), segs=8, rings=2)
    f += ell("dark", (6.6, CY, 0), (0.3, 0.8, 0.4), segs=6, rings=1)
    up = bend(stage, [(5.0, CY + 2.4, 0), (4.6, CY + 5.3, 0), (3.2, CY + 8.0, 0), (1.2, CY + 10.0, 0), (-1.2, CY + 10.8, 0)])
    half = []
    half += curve("main", up, [0.9, 0.84, 0.72, 0.56, 0.38], [1.0, 0.92, 0.8, 0.62, 0.42], sides=6, caps=(False, True), hint=(0, 0, 1))
    for i in (1, 2, 3):
        x, y, _ = up[i]
        sp = [(x + 0.7 + 0.5 * math.cos(t) * (1 - t / 7), y + 0.5 * math.sin(t) * (1 - t / 7), 0.3) for t in [0.0, 1.2, 2.4, 3.6, 4.8]]
        half += curve("acc", sp, [0.45, 0.4, 0.32, 0.22, 0.08], [0.45, 0.4, 0.32, 0.22, 0.08], sides=5)
    tx, ty, _ = up[-1]
    half += curve("gem", [(tx, ty, 0), (tx - 1.0, ty + 0.8, 0.2), (tx - 1.8, ty + 0.6, 0.5), (tx - 2.6, ty + 1.2, 0.7)], 0.08, 0.08, sides=3)
    f += half + mirror_faces_y(half)
    lo = [(x, 2 * CY - y, z) for x, y, z in up]
    return f + string_and_arrow(stage, "rare", up, lo)


def wild_lighthouse_longbow(stage=0):
    """灯台の大弓: 握りに灯台の灯室 (硝子とレンズ) を組んだ大きな弓。腕は白い塔の石に赤い帯、灯室の中に月の光、腕の先は塔の屋根の尖り"""
    f = bow_grip("grip", "wrap", r=1.1)
    f += prism("acc", (6.4, CY - 1.6, 0), (6.4, CY + 1.6, 0), 1.3, 1.3, sides=8, caps=(True, True))
    f += prism("gem", (6.4, CY - 1.2, 0), (6.4, CY + 1.2, 0), 1.35, 1.35, sides=8, caps=(False, False))
    for k in range(8):
        a = math.radians(45 * k + 22.5)
        f += prism("trim", (6.4 + 1.38 * math.cos(a), CY - 1.2, 1.38 * math.sin(a)), (6.4 + 1.38 * math.cos(a), CY + 1.2, 1.38 * math.sin(a)), 0.1, 0.1,
                   sides=3, caps=(False, False))
    f += prism("trim", (8.0, CY, 0), (8.2, CY, 0), 0.9, 0.9, sides=10, caps=(True, True), hint=(0, 1, 0))
    up = bend(stage, [(5.0, CY + 2.4, 0), (5.0, CY + 6.2, 0), (4.0, CY + 9.8, 0), (2.0, CY + 12.6, 0), (-0.8, CY + 14.0, 0)])
    half = []
    half += curve("main", up, [1.1, 1.0, 0.86, 0.66, 0.42], [1.15, 1.05, 0.9, 0.7, 0.44], sides=8, caps=(False, True), hint=(0, 0, 1))
    for i in (1, 3):
        half += ell("cloth", up[i], (1.15, 0.55, 1.2), segs=8, rings=1)
    tx, ty, _ = up[-1]
    half += spike(BONE, "trim", (tx, ty, 0), (-0.6, 1, 0), 1.6, 0.6, sides=8, rings=1)
    f += half + mirror_faces_y(half)
    lo = [(x, 2 * CY - y, z) for x, y, z in up]
    return f + string_and_arrow(stage, "legend", up, lo)


def wild_skylark_bow(stage=0):
    """雲雀の弓: 雲雀が広げた翼の白い弓。腕は風切羽を重ねた翼そのもの、先は尾羽の扇、握りに雲の珠と金の輪"""
    f = bow_grip("grip", "wrap")
    f += ell("gem", (6.3, CY, 0), (0.75, 0.75, 0.55), segs=8, rings=2)
    f += torus("trim", (6.3, CY, 0), 0.9, 0.14, 0.4, n=10, k=4, ax="z")
    up = bend(stage, [(5.0, CY + 2.4, 0), (5.2, CY + 5.0, 0), (4.4, CY + 7.6, 0), (2.4, CY + 9.8, 0), (-0.4, CY + 11.0, 0)])
    half = []
    half += curve("main", up, [0.85, 0.8, 0.7, 0.54, 0.34], [0.95, 0.9, 0.78, 0.6, 0.38], sides=5, caps=(False, True), hint=(0, 0, 1))
    for i in range(0, 4):
        (x0, y0, _), (x1, y1, _) = up[i], up[i + 1]
        dx, dy = x1 - x0, y1 - y0
        ln = math.hypot(dx, dy) or 1
        ux, uy = dx / ln, dy / ln
        nx, ny = uy, -ux
        for t in (0.0, 0.5):
            px, py = x0 + dx * t, y0 + dy * t
            half += plume((px + nx * 0.3, py + ny * 0.3, 0.1), (px + nx * 2.4 + ux * 2.2, py + ny * 2.4 + uy * 2.2, 0.1), 0.65, th=0.2, big=True,
                          mat="cloth", tipmat="acc", scorch=1)
    tx, ty, _ = up[-1]
    for k in range(3):
        half += plume((tx, ty, 0.1), (tx - 2.2 + 0.6 * k, ty + 1.8 - 0.5 * k, 0.1), 0.45, th=0.2, big=False, mat="cloth", tipmat="acc", scorch=1)
    f += half + mirror_faces_y(half)
    lo = [(x, 2 * CY - y, z) for x, y, z in up]
    return f + string_and_arrow(stage, "legend", up, lo)


def wild_spark_dagger(stage=0):
    """火花の双刃: 銅と鉄の二枚の刃を合わせた短剣。合わせ目に火花の光の筋、鍔は碍子、柄頭に銅の玉"""
    f = haft(-3.0, 2.6, r=0.68, mat="grip", wrap=(-2.6, 2.1))
    f += ell("acc", (0, -3.3, 0), (0.7, 0.7, 0.7), segs=6, rings=1)
    f += prism("cloth", (-1.6, 3.0, 0), (1.6, 3.0, 0), 0.45, 0.45, sides=8, caps=(True, True), hint=(0, 1, 0))
    for x in (-1.2, 1.2):
        f += prism("cloth", (x - 0.15, 3.0, 0), (x + 0.15, 3.0, 0), 0.75, 0.75, sides=8, caps=(True, True), hint=(0, 1, 0))
    f += blade("acc", [(3.4, -0.9, 0.0, 0.34, 0.34), (7.0, -1.0, 0.0, 0.34, 0.34), (10.6, -0.7, 0.0, 0.28, 0.28), (13.4, 0, 0, 0, 0)],
               colmats=["edge", "acc", "acc", "acc", "acc", "edge"])
    f += blade("main", [(3.4, 0.0, 0.9, 0.34, 0.34), (7.0, 0.0, 1.0, 0.34, 0.34), (10.6, 0.0, 0.7, 0.28, 0.28), (13.4, 0, 0, 0, 0)],
               colmats=["main", "main", "edge", "edge", "main", "main"])
    for z in (-0.36, 0.36):
        f += _zig([(0, 4.0, z), (0.12, 7.0, z), (-0.1, 10.0, z), (0, 12.0, z * 0.7)], r=0.1)
    return f


def wild_ash_sickle_claw(stage=0):
    """灰被りの鎌爪: 小さな鎌を三枚並べた爪。煤けた鉄の鎌の刃が内へ曲がり、刃に灰が被る。甲は焦げた革に鋲"""
    f = claw_hand()
    f += ell("spine", (0, -1.3, 0), (0.95, 0.6, 0.95), segs=6, rings=1)
    f += block("guard", (0, 5.0, 0), (4.8, 2.0, 2.2), taper=(0.9, 0.85))
    for x in (-1.8, 0, 1.8):
        f += ell("spine", (x, 5.0, -1.1), (0.25, 0.25, 0.12), segs=4, rings=1)
    # 三枚の鎌 (前へ伸びて、先が手前 (-Z) へ内に曲がる。刃は薄い板)
    for x in (-1.6, 0.0, 1.6):
        pts = [(x, 6.0, 0.0), (x, 8.8, -0.3), (x, 11.2, -1.3), (x, 12.8, -2.8), (x, 13.2, -4.2)]
        f += curve("main", pts, [0.22, 0.2, 0.18, 0.14, 0.04], [0.9, 0.95, 0.85, 0.6, 0.06], sides=4, caps=(False, True), hint=(1, 0, 0))
        f += curve("edge", [(x, 9.0, 0.6), (x, 11.4, -0.5), (x, 12.9, -2.1)], [0.1, 0.1, 0.06], [0.3, 0.3, 0.2], sides=4, hint=(1, 0, 0))
    return f


def wild_fishhook_knife(stage=0):
    """深みの釣り針: 大きな釣り針の形の刃。返しの付いた鉤が下へ戻り、柄頭から糸と鉛の錘が垂れる。刃は錆びた錨鉄"""
    f = haft(-3.0, 2.4, r=0.7, mat="grip", wrap=(-2.6, 1.9), wmat="cord")
    f += prism("spine", (0, 2.4, 0), (0, 3.0, 0), 0.85, 0.6, sides=8, caps=(True, True))
    hook = [(0, 2.8, 0), (0, 8.0, 0), (0.6, 11.2, 0), (2.4, 12.6, 0), (4.0, 11.8, 0), (4.4, 9.4, 0), (3.6, 7.6, 0)]
    f += curve("main", hook, [0.6, 0.55, 0.5, 0.45, 0.4, 0.32, 0.1], [0.45, 0.42, 0.38, 0.34, 0.3, 0.24, 0.08], sides=6, caps=(True, True))
    f += spike(BONE, "edge", (3.7, 8.2, 0), (-0.6, 1, 0), 1.6, 0.3, sides=4, rings=1)
    f += prism("cord", (0, -3.0, 0), (0.6, -5.4, 0), 0.06, 0.06, sides=3, caps=(False, False))
    f += ell("spine", (0.6, -5.8, 0), (0.45, 0.6, 0.45), segs=6, rings=2)
    return f


def wild_mirror_dagger(stage=0):
    """鏡写しの短剣: 磨いた鏡の刃の短剣。刃の両の面が鏡 (月の光を映す)、鍔は向き合う二つの銀の手、柄頭は逆さの小さな鏡。刃の縁に金の筋"""
    f = haft(-3.4, 2.4, r=0.72, mat="grip", wrap=(-2.8, 1.8))
    f += prism("acc", (0, -3.4, 0), (0, -3.9, 0), 0.6, 0.6, sides=8, caps=(True, True))
    f += prism("gem", (0, -4.9, -0.2), (0, -4.9, 0.2), 1.1, 1.1, sides=10, caps=(True, True), hint=(1, 0, 0))
    f += torus("trim", (0, -4.9, 0), 1.15, 0.14, 0.26, n=10, k=4, ax="z")
    for s in (-1, 1):
        f += ell("acc", (s * 1.2, 2.9, 0), (0.9, 0.5, 0.6), segs=6, rings=1)
        for k in range(3):
            f += curve("acc", [(s * 1.6, 2.9, -0.3 + 0.3 * k), (s * 2.3, 3.4, -0.3 + 0.3 * k), (s * 2.0, 4.0, -0.3 + 0.3 * k)], 0.14, 0.14, sides=4)
    st = [(3.4, -1.2, 1.2, 0.3, 0.3), (7.0, -1.35, 1.35, 0.3, 0.3), (10.6, -1.0, 1.0, 0.26, 0.26), (13.4, -0.4, 0.4, 0.15, 0.15), (14.6, 0, 0, 0, 0)]
    f += blade("trim", st, colmats=["trim", "gem", "trim", "trim", "gem", "trim"])
    return f


def wild_cloudveil_claw(stage=0):
    """雲隠れの爪: 雲の糸を撚った細い四本の爪。甲は白い雲の綿の籠手に金の輪、指の輪から雲の帯が流れ、爪の先は空の光"""
    f = claw_hand()
    f += ringy("trim", 3.6, 1.0, 0.4)
    f += ell("acc", (0, 5.0, 0), (2.6, 1.5, 1.5), segs=8, rings=2)
    for p in ((1.4, 5.6, 0.8), (-1.4, 5.4, 0.9), (0.2, 6.0, 1.1)):
        f += ell("acc", p, (1.0, 0.8, 0.8), segs=6, rings=2)
    for x in (-1.8, -0.6, 0.6, 1.8):
        pts = [(x, 6.0, -0.2), (x * 1.05, 9.0, -0.6), (x * 1.1, 12.0, -1.4), (x * 1.1, 14.0, -2.6)]
        f += curve("main", pts, [0.32, 0.28, 0.2, 0.05], [0.32, 0.28, 0.2, 0.05], sides=5)
        f += ell("gem", pts[-1], (0.18, 0.18, 0.18), segs=4, rings=1)
        f += torus("trim", (x, 6.4, -0.3), 0.36, 0.08, 0.1, n=6, k=3, ax="y")
    f += sash([(-2.2, 5.4, 0.6), (-3.6, 4.0, 1.0), (-4.4, 2.0, 1.4)], 0.8, mat="cloth")
    return f


def wild_raincall_bell_staff(stage=0):
    """雨乞いの鈴杖: 雨の雫の形の鈴を連ねた杖。頭に小さな雲の傘、傘の縁から雫の鈴が六つ下がる。柄は黒い竹"""
    f = haft(-4.0, 15.4, r=0.72, mat="grip", wrap=(-2.4, 2.0))
    for y in (3.0, 7.0, 11.0):
        f += ringy("spine", y, 0.8, 0.3)
    f += prism("acc", (0, 15.2, 0), (0, 16.4, 0), 2.8, 0.6, sides=10, caps=(True, True))
    for p in ((0, 16.6, 0), (1.0, 16.3, 0.3), (-0.9, 16.4, -0.3)):
        f += ell("cloth", p, (1.1, 0.7, 1.0), segs=8, rings=2)
    for k in range(6):
        a = math.radians(60 * k)
        x, z = 2.5 * math.cos(a), 2.5 * math.sin(a)
        f += prism("cord", (x, 15.2, z), (x, 13.6, z), 0.05, 0.05, sides=3, caps=(False, False))
        f += ell("gem", (x, 13.2, z), (0.38, 0.5, 0.38), segs=6, rings=2)
        f += spike(BONE, "gem", (x, 13.5, z), (0, 1, 0), 0.6, 0.3, sides=6, rings=1)
    return f


def wild_calming_censer(stage=0):
    """鎮めの香炉: 銀の透かしの香炉を鎖で下げた杖。花の形の煙の穴から薄い煙、香炉の中に淡い灯、杖の頭は鉤の形"""
    f = haft(-4.0, 14.0, r=0.75, mat="grip", wrap=(-2.4, 2.0))
    hook = [(0, 13.8, 0), (0.4, 16.0, 0), (1.8, 17.4, 0), (3.4, 17.0, 0), (3.8, 15.8, 0)]
    f += curve("spine", hook, [0.6, 0.55, 0.48, 0.4, 0.3], [0.6, 0.55, 0.48, 0.4, 0.3], sides=6)
    f += chain((3.8, 15.6, 0), (3.8, 13.0, 0), n=3)
    C = (3.8, 11.4, 0)
    f += ell("main", C, (1.6, 1.4, 1.6), segs=10, rings=3)
    f += torus("trim", (3.8, 11.4, 0), 1.65, 0.12, 0.15, n=12, k=4, ax="y")
    for k in range(6):
        a = math.radians(60 * k)
        f += ell("gem", (3.8 + 1.4 * math.cos(a), 11.8, 1.4 * math.sin(a)), (0.2, 0.25, 0.2), segs=5, rings=1)
    f += prism("main", (3.8, 12.6, 0), (3.8, 13.2, 0), 0.9, 0.5, sides=8, caps=(True, True))
    f += prism("main", (3.8, 10.0, 0), (3.8, 9.4, 0), 0.7, 1.0, sides=8, caps=(True, True))
    for k, (dx, dy) in enumerate(((0.4, 13.8), (-0.3, 14.8), (0.5, 15.6))):
        f += ell("cloth", (3.8 + dx, dy, 0.2 * k), (0.5 - 0.1 * k, 0.4, 0.5 - 0.1 * k), segs=6, rings=1)
    return f


def wild_effigy_totem(stage=0):
    """身代わりの人形柱: 藁の人形を三体縛り付けた柱。人形の胸に赤い糸が縫い込まれ、顔は布で覆われる。柱の頂に釘が一本"""
    f = haft(-4.0, 13.0, r=0.82, mat="grip", wrap=(-2.4, 2.0))
    f += prism("grip", (0, 12.8, 0), (0, 20.0, 0), 0.9, 0.8, sides=8, caps=(True, True))
    f += spike(BONE, "spine", (0, 19.8, 0), (0, 1, 0), 2.4, 0.3, sides=4, rings=1)
    for k, y in enumerate((13.6, 16.0, 18.2)):
        a = math.radians(120 * k - 90)
        dx, dz = 1.1 * math.cos(a), 1.1 * math.sin(a)
        f += ell("cloth", (dx, y + 0.4, dz), (1.0, 1.3, 0.85), segs=6, rings=2)
        f += ell("soot", (dx * 1.05, y + 2.0, dz * 1.05), (0.7, 0.75, 0.7), segs=6, rings=2)
        f += prism("cloth", (dx - 1.4, y + 1.0, dz), (dx + 1.4, y + 1.0, dz), 0.28, 0.28, sides=4, caps=(True, True), hint=(0, 1, 0))
        for lx in (-0.4, 0.4):
            f += prism("cloth", (dx + lx, y - 0.6, dz), (dx + lx * 1.4, y - 1.6, dz), 0.26, 0.2, sides=4, caps=(True, True))
        f += ell("gem", (dx * 1.75, y + 0.6, dz * 1.75), (0.24, 0.24, 0.14), segs=4, rings=1)
        f += ringy("cord", y + 0.6, 0.95, 0.25)
    return f


def wild_starbell_totem(stage=0):
    """星鈴の木: 銀の枝に星の鈴が実った小さな木の祭具。月の石の幹が三つに枝分かれし、枝先ごとに四方へ光る星の形の鈴、根元に金の環"""
    f = haft(-4.0, 13.4, r=0.85, mat="grip", wrap=(-2.6, 2.4))
    for y in (-2.8, 2.6):
        f += ringy("trim", y, 1.0, 0.4)
    f += spike(BONE, "trim", (0, -3.8, 0), (0, -1, 0), 1.8, 0.85, sides=8, rings=1)
    f += torus("trim", (0, 13.4, 0), 1.0, 0.25, 0.3, n=10, k=4, ax="y")
    f += prism("main", (0, 13.2, 0), (0, 16.0, 0), 0.8, 0.6, sides=8, caps=(True, True))
    tips = []
    for k in range(5):
        a = math.radians(72 * k)
        p1 = (1.6 * math.cos(a), 18.0, 1.6 * math.sin(a))
        p2 = (2.6 * math.cos(a + 0.3), 20.4 - 0.6 * (k % 2), 2.6 * math.sin(a + 0.3))
        f += curve("acc", [(0, 15.8, 0), p1, p2], [0.4, 0.28, 0.15], [0.4, 0.28, 0.15], sides=5)
        tips.append(p2)
    f += prism("acc", (0, 15.8, 0), (0, 21.6, 0), 0.4, 0.15, sides=6, caps=(True, True))
    tips.append((0, 21.6, 0))
    for p in tips:
        f += bell((p[0], p[1] - 0.1, p[2]), r=0.5, h=0.9, mat="gem", mouth="trim")
        for a in (0, 90, 180, 270):
            d = (math.cos(math.radians(a)), 0, math.sin(math.radians(a)))
            f += spike(BONE, "gem", (p[0], p[1] - 0.5, p[2]), d, 0.6, 0.15, sides=3, rings=1)
    return f


def wild_cloudhymn_bell_staff(stage=0):
    """雲上の祝詞の鈴杖: 長い白い杖の頭に、雲の形に重ねた鈴の房と紙の垂 (しで) の帯。杖は雲の白に金の輪、頂に暁の光の珠"""
    f = haft(-4.4, 17.0, r=0.8, mat="grip", wrap=(-2.6, 2.4))
    for y in (-3.2, 3.0, 9.0, 15.0):
        f += ringy("trim", y, 0.98, 0.4)
    f += spike(BONE, "trim", (0, -4.2, 0), (0, -1, 0), 1.8, 0.8, sides=8, rings=1)
    f += ell("gem", (0, 18.4, 0), (1.0, 1.0, 1.0), segs=8, rings=3)
    f += torus("trim", (0, 18.4, 0), 1.25, 0.15, 0.2, n=12, k=4, ax="y")
    for p, r in (((1.6, 16.2, 0), 0.8), ((-1.5, 16.4, 0.2), 0.75), ((0.4, 15.4, 1.3), 0.7), ((-0.4, 15.6, -1.3), 0.7), ((2.4, 15.0, -0.6), 0.6),
                 ((-2.4, 15.2, 0.6), 0.6)):
        f += bell(p, r=r * 0.8, h=r * 1.4, mat="acc", mouth="trim")
    for s in (-1, 1):
        zz = [(s * 0.9, 17.0, 0), (s * 1.6, 15.8, 0), (s * 1.0, 14.6, 0), (s * 1.7, 13.4, 0), (s * 1.1, 12.2, 0)]
        for (x0, y0, _), (x1, y1, _) in zip(zz, zz[1:]):
            f += _slab("cloth", [V(x0 - 0.3, y0, -0.05), V(x0 + 0.3, y0, -0.05), V(x1 + 0.3, y1, -0.05), V(x1 - 0.3, y1, -0.05)], 0.1)
    return f


WEAPONS = dict([
    W(wild_fulgurite_sword, "雷柱の剣", "rare", "sword", "spark",
      dict(main="fulgurite", edge="fulgurite", acc="copper", grip="blackleather", wrap="leather")),
    W(wild_twin_spear, "双頭の槍", "epic", "spear", "ember",
      dict(main="blacksteel", edge="steel", spine="iron", cloth="redcloth", grip="ashwood", wrap="blackleather")),
    W(wild_chain_greatsword, "淵の鎖剣", "epic", "greatsword", "void",
      dict(main="blacksteel", edge="steel", spine="iron", chain="iron", grip="blackleather", wrap="leather")),
    W(wild_meteor_greatsword, "隕鉄の大剣", "heroic", "greatsword", "star",
      dict(main="meteor", edge="silver", acc="basalt", grip="bluecloth", wrap="blackleather")),
    W(wild_cloudcleaver_greatsword, "雲断ちの大剣", "heroic", "greatsword", "dawn",
      dict(main="cloudwhite", edge="silver", acc="cloudwhite", grip="bluecloth", wrap="bluecloth", dark="dark")),
    W(wild_molten_axe, "溶け鉄の斧", "rare", "axe", "ember",
      dict(main="molten", spine="iron", grip="ashwood", wrap="blackleather")),
    W(wild_cog_hammer, "歯車の大鎚", "epic", "maul", "lamp",
      dict(main="iron", acc="brass", spine="steel", grip="darkwood", wrap="leather")),
    W(wild_abyss_mace, "深み呑みの鉄球", "epic", "mace", "void",
      dict(main="blacksteel", acc="flesh", edge="iron", spine="iron", chain="iron", grip="blacksteel", wrap="blackleather")),
    W(wild_starforge_hammer, "星の炉の大鎚", "heroic", "hammer", "star",
      dict(main="blacksteel", edge="molten", acc="iron", grip="stone", wrap="bluecloth")),
    W(wild_skysplit_axe, "天割りの両刃斧", "heroic", "axe", "dawn",
      dict(main="cloudwhite", edge="silver", grip="bluecloth", wrap="bluecloth")),
    W(wild_ballspark_staff, "雷玉の杖", "rare", "staff", "spark",
      dict(acc="copper", cloth="whitecloth", grip="darkwood", wrap="leather")),
    W(wild_hourglass_staff, "灰の砂時計の杖", "epic", "staff", "ember",
      dict(main="glass", acc="bone", cloth="ash", grip="ashwood", wrap="blackleather")),
    W(wild_deep_eye_staff, "深みの眼杖", "epic", "staff", "void",
      dict(main="bone", spine="blacksteel", cloth="flesh", grip="blackleather", wrap="leather")),
    W(wild_starchart_staff, "星図の杖", "heroic", "staff", "star",
      dict(main="stone", acc="silver", grip="stone", wrap="bluecloth")),
    W(wild_prism_staff, "虹の端の杖", "heroic", "staff", "rainbow",
      dict(acc="cloudwhite", grip="cloudwhite", wrap="bluecloth")),
    W(wild_flash_bow, "稲光の弓", "rare", "bow", "spark",
      dict(main="fulgurite", acc="copper", cloth="whitecloth")),
    W(wild_scope_longbow, "遠眼鏡の長弓", "epic", "longbow", "lamp",
      dict(main="darkwood", acc="brass", grip="leather", wrap="leather")),
    W(wild_calling_bow, "呼び声の弓", "epic", "bow", "void",
      dict(main="bone", acc="shell", grip="blackleather", wrap="leather")),
    W(wild_lighthouse_longbow, "灯台の大弓", "heroic", "longbow", "star",
      dict(main="whitecloth", cloth="redcloth", acc="silver", grip="bluecloth", wrap="blackleather")),
    W(wild_skylark_bow, "雲雀の弓", "heroic", "bow", "dawn",
      dict(main="cloudwhite", cloth="cloudwhite", acc="gold", grip="bluecloth", wrap="bluecloth")),
    W(wild_spark_dagger, "火花の双刃", "rare", "dagger", "spark",
      dict(main="steel", edge="steel", acc="copper", cloth="whitecloth", grip="blackleather", wrap="leather")),
    W(wild_ash_sickle_claw, "灰被りの鎌爪", "epic", "claw", "ember",
      dict(main="blacksteel", edge="ash", guard="blackleather", spine="iron", grip="blackleather", wrap="leather")),
    W(wild_fishhook_knife, "深みの釣り針", "epic", "dagger", "void",
      dict(main="rust", edge="iron", spine="iron", cord="rope", grip="darkwood")),
    W(wild_mirror_dagger, "鏡写しの短剣", "heroic", "dagger", "star",
      dict(acc="silver", grip="bluecloth", wrap="blackleather")),
    W(wild_cloudveil_claw, "雲隠れの爪", "heroic", "claw", "dawn",
      dict(main="silver", acc="cloudwhite", cloth="cloudwhite", grip="bluecloth", wrap="bluecloth")),
    W(wild_raincall_bell_staff, "雨乞いの鈴杖", "rare", "bell", "sea",
      dict(acc="silver", cloth="cloudwhite", spine="iron", grip="ashwood", wrap="leather", cord="rope")),
    W(wild_calming_censer, "鎮めの香炉", "epic", "censer", "lamp",
      dict(main="silver", spine="iron", chain="iron", cloth="cloudwhite", grip="darkwood", wrap="leather")),
    W(wild_effigy_totem, "身代わりの人形柱", "epic", "totem", "heart",
      dict(cloth="straw", soot="sailcloth", spine="iron", cord="redcloth", grip="darkwood", wrap="leather")),
    W(wild_starbell_totem, "星鈴の木", "heroic", "totem", "star",
      dict(main="stone", acc="silver", grip="stone", wrap="bluecloth")),
    W(wild_cloudhymn_bell_staff, "雲上の祝詞の鈴杖", "heroic", "bell", "dawn",
      dict(acc="gold", cloth="paper", grip="cloudwhite", wrap="bluecloth")),
])
# 双頭の槍は上下に長いので、絵の真ん中を握りへ寄せる
WEAPONS["wild_twin_spear"]["cen"] = (0.0, 3.0, 0.0)
WEAPONS["wild_twin_spear"]["scale"] = 6.6
