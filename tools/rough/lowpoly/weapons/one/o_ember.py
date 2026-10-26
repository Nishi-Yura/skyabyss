"""余燼 (四章) の一品一案。章の職の武器 5 本 (エピック) と、余燼の伝説 5 本。
塗りは余燼 (../paint.py、一段明るく): main = 玄武岩 (光る割れ目)、edge/spine = 鉄、guard/acc = 焼けた岩、grip = 柄の木、wrap = 灰の布、chain = 鎖、soot = 煤、gem = 熾火の橙。
"""
from __future__ import annotations

import math

from kit_one import *  # noqa: F401,F403
from kit_one import legend_finish, at, ell, lump, strand, ringy, gbar, gem, arc, bend, mirror_faces_y, string_and_arrow, inlay_line, CY


def wood_haft(y0, y1, r=0.95, wrap=(-1.6, 3.0), bands=2):
    f = prism("grip", (0, y0, 0), (0, y1, 0), r, r * 0.95, sides=6, caps=(True, True))
    f += prism("wrap", (0, wrap[0], 0), (0, wrap[1], 0), r + 0.16, r + 0.16, sides=6, caps=(False, False))
    for i in range(bands):
        y = wrap[1] + 1.2 + i * (y1 - wrap[1] - 2.0) / max(1, bands - 1) if bands > 1 else wrap[1] + 1.2
        f += ringy("spine", y, r + 0.22, 0.6, sides=6)
        for k in range(3):
            a = math.radians(120 * k + 30)
            f += ell("edge", ((r + 0.22) * math.cos(a), y, (r + 0.22) * math.sin(a)), (0.2, 0.2, 0.2), segs=4, rings=1)
    return f


def column(c, h, r, mat="main", top=True):
    """玄武岩の柱 (六角の柱、上は少し斜めに欠ける)"""
    x, y, z = c
    f = prism(mat, (x, y, z), (x, y + h, z), r, r * 0.96, sides=6, caps=(False, False), phase=30)
    f += block(mat, (x, y + h + 0.2, z), (r * 1.7, 0.5, r * 1.5), rot=rot_z(9), taper=(0.8, 0.85))
    return f


def flame(c, h, r, mat="gem", lean=0.0):
    """炎の舌 (細る光の棘)"""
    return spike(BONE, mat, c, (lean, 1, 0), h, r, sides=4, rings=2, bend=(lean * 0.6, 0, 0), hint=(0, 0, 1))


# ================================================================== 章の武器 (エピック)
def ember_staff(stage=0):
    """熾火の杖: 柄の先に柱状の玄武岩が五本、高さを違えて束になって立ち、柱のあいだの割れ目の奥で熾火の核が燃える。鉄の帯で束ね、炎の舌がのぞく"""
    f = wood_haft(-4.0, 14.2, bands=3)
    f += spike(BONE, "spine", (0, -4.0, 0), (0, -1, 0), 1.6, 0.9, sides=6, rings=1, hint=(0, 0, 1))
    f += gem((0, 17.4, -0.6), 1.5, 1.9, 1.3, segs=6, rings=2)
    for (x, z, h, r, lean) in ((0.0, 1.4, 7.6, 1.0, 0), (2.0, 0.5, 5.6, 0.95, 14), (-2.0, 0.5, 6.4, 0.9, -14), (1.3, -1.3, 4.0, 0.8, 10), (-1.4, -1.2, 4.8, 0.8, -12)):
        f += at(column((0, 0, 0), h, r), rot_z(-lean), (x, 14.4, z))
    f += prism("spine", (0, 15.0, 0), (0, 15.8, 0), 3.1, 3.1, sides=8, caps=(False, False))
    for k in range(4):
        a = math.radians(45 + 90 * k)
        f += ell("edge", (3.1 * math.cos(a), 15.4, 3.1 * math.sin(a)), (0.28, 0.28, 0.28), segs=4, rings=1)
    f += prism("chain", (0, 14.0, 0), (0, 14.6, 0), 1.4, 1.4, sides=8, caps=(True, True))
    f += flame((0.15, 18.2, -1.0), 2.2, 0.45, lean=0.1)
    f += flame((-0.2, 18.8, -0.9), 3.0, 0.4, lean=-0.15)
    f += strand("wrap", (1.6, 14.4, -0.9), 2.6, 0.4, r=0.3)
    f += chain((-1.8, 14.4, -0.6), (-2.2, 11.4, -0.6), n=4)
    f += lump("acc", (-2.2, 10.9, -0.6), (0.6, 0.6, 0.6), seed=71, segs=5, rings=2)
    return f


def ember_censer(stage=0):
    """灰の香炉: 鍛冶場の鉄の鉤の先に、透かし彫りの鉄の香炉が鎖で下がる。孔から熾火が光り、上へ灰の煙が立つ。鉤の根に灰の布の房"""
    f = wood_haft(-4.0, 12.8, bands=2)
    f += spike(BONE, "spine", (0, -4.0, 0), (0, -1, 0), 1.6, 0.9, sides=6, rings=1, hint=(0, 0, 1))
    # 鉄の鉤 (四角い鍛え鉄の曲がり)
    f += curve("spine", [(0, 12.4, 0), (0, 16.4, 0), (0.6, 19.4, 0), (2.6, 21.0, 0), (4.6, 20.6, 0), (5.2, 19.2, 0)],
               [0.7, 0.65, 0.6, 0.55, 0.5, 0.42], [0.7, 0.65, 0.6, 0.55, 0.5, 0.42], sides=4, caps=(False, True), phase=45)
    f += spike(BONE, "edge", (0.4, 20.0, 0), (-0.5, 1, 0), 1.6, 0.35, sides=4, rings=1, hint=(0, 0, 1))
    # 鎖と香炉 (球の籠: 上下の鉄の椀と、間の透かし)
    f += chain((5.2, 18.8, 0), (5.2, 15.8, 0), n=4)
    cx, cy = 5.2, 13.4
    f += ell("edge", (cx, cy + 0.9, 0), (2.0, 1.1, 2.0), segs=8, rings=2, deform=lambda p: p if p[1] > -0.05 else p * [1, 0.2, 1])
    f += ell("edge", (cx, cy - 0.9, 0), (2.0, 1.1, 2.0), segs=8, rings=2, deform=lambda p: p if p[1] < 0.05 else p * [1, 0.2, 1])
    f += gem((cx, cy, 0), 1.7, 0.9, 1.7, segs=8, rings=1)
    for k in range(8):
        a = math.radians(22.5 + 45 * k)
        f += prism("spine", (cx + 1.95 * math.cos(a), cy - 0.9, 1.95 * math.sin(a)), (cx + 1.95 * math.cos(a), cy + 0.9, 1.95 * math.sin(a)), 0.18, 0.18,
                   sides=4, caps=(False, False))
    f += prism("spine", (cx, cy + 1.9, 0), (cx, cy + 2.5, 0), 0.6, 0.3, sides=6, caps=(True, True))
    f += spike(BONE, "spine", (cx, cy - 1.9, 0), (0, -1, 0), 1.4, 0.55, sides=6, rings=1, hint=(0, 0, 1))
    # 灰の煙 (上へ揺れて立つ塊)
    for i, (dx, dy, r) in enumerate(((0.3, 2.6, 0.7), (-0.4, 3.6, 0.6), (0.4, 4.5, 0.5))):
        f += ell("soot", (cx + dx, cy + dy, -0.2), (r, r * 0.8, r), segs=5, rings=1)
    # 布の房
    for x, z in ((0.8, -0.8), (-0.8, 0.6)):
        f += rag((x, 12.6, z), 3.2, 0.9, sign=1 if x > 0 else -1, mat="wrap", th=0.25)
    return f


def ember_greatsword(stage=0):
    """熾火の大剣: 幅広い玄武岩の板の刃。縁は柱状の割れで段になって欠け、面の割れ目が熾火に光る。芯に鉄の背、鍔は鋲を打った炉の鉄、柄頭は鉄の角"""
    f = wood_haft(-3.8, 3.0, r=0.95, wrap=(-3.0, 2.4), bands=0)
    f += prism("spine", (0, -4.4, 0), (0, -3.4, 0), 1.35, 1.2, sides=6, caps=(True, True))
    f += spike(BONE, "spine", (0, -4.4, 0), (0, -1, 0), 1.2, 0.9, sides=6, rings=1, hint=(0, 0, 1))
    # 鍔: 炉の鉄の厚い帯と鋲、両端は下へ曲がる
    f += block("spine", (0, 3.6, 0), (7.6, 1.5, 2.6), taper=(0.95, 0.95))
    for s_ in (1, -1):
        f += block("spine", (s_ * 4.1, 2.9, 0), (1.1, 2.0, 2.2), rot=rot_z(s_ * 20))
        for x in (1.2, 2.4, 3.4):
            f += ell("edge", (s_ * x, 3.6, -1.35), (0.3, 0.3, 0.22), segs=4, rings=1)
    f += gem((0, 3.6, -1.35), 0.6, 0.6, 0.25, segs=5, rings=1)
    # 刃: 幅広の板 (先は斜めに断ち切った柱の頭)
    f += blade("main", [(4.2, -2.7, 2.7, 0.85, 0.85), (9.0, -2.9, 2.9, 0.8, 0.8), (14.0, -2.7, 2.7, 0.75, 0.75), (18.0, -2.3, 2.3, 0.65, 0.65),
                        (20.6, -1.0, 1.6, 0.5, 0.5), (21.8, 0, 0, 0, 0, 0.8)], colmats=["main"] * 6)
    # 縁の段の欠け (柱の面で割れた角が左右に交互に張り出す)
    for y, s_, w in ((6.0, 1, 1.4), (8.6, -1, 1.5), (11.4, 1, 1.3), (13.8, -1, 1.4), (16.4, 1, 1.1), (18.6, -1, 1.0)):
        f += block("main", (s_ * 2.75, y, 0), (w, 1.9, 1.3), rot=rot_z(s_ * 10), taper=(0.6, 0.85))
    # 鉄の背 (芯) と、割れ目の光
    f += block("spine", (0, 9.0, 0), (0.9, 9.0, 1.9), taper=(0.7, 0.9))
    f += gbar([(-1.6, 6.4, -0.82), (-0.6, 8.2, -0.82), (-1.2, 10.4, -0.8)], r=0.2)
    f += gbar([(1.4, 12.0, -0.78), (0.6, 14.4, -0.75), (1.2, 16.6, -0.7)], r=0.2)
    f += lump("guard", (1.9, 4.9, -0.7), (0.8, 0.6, 0.5), seed=73, segs=5, rings=1)
    return f


def ember_bow(stage=0):
    """灰の弓: 腕は鍛えた鉄の板を三枚重ねた板ばね。鋲で留め、鋲の頭が熾火に光る。握りは小さな金床の形、鎖が一本下がる"""
    f = []
    f += block("spine", (5.4, CY, 0), (2.4, 2.4, 2.6), taper=(1.2, 1.0))
    f += block("spine", (6.0, CY + 1.4, 0), (1.4, 0.7, 2.4))
    f += prism("grip", (5.0, CY - 3.0, 0), (5.0, CY + 3.0, 0), 0.95, 0.95, sides=6, caps=(False, False), hint=(0, 0, 1))
    f += prism("wrap", (5.0, CY - 2.6, 0), (5.0, CY - 1.3, 0), 1.12, 1.12, sides=6, caps=(True, True), hint=(0, 0, 1))
    up = bend(stage, [(5.0, CY + 2.4, 0), (4.4, CY + 5.0, 0), (2.8, CY + 7.6, 0), (0.6, CY + 9.6, 0), (-1.8, CY + 10.6, 0)])
    half = []
    for k, (n, w) in enumerate(((5, 0.5), (4, 0.42), (3, 0.36))):
        path = [(x + 0.55 * k, y - 0.25 * k, 0) for x, y, z in up[:n]]
        half += curve("edge" if k == 0 else "spine", path, [w] * n, [1.2 - 0.15 * k] * n, sides=4, caps=(True, True), hint=(0, 0, 1))
    for i in (1, 2):
        x, y, _ = up[i]
        half += prism("spine", (x + 0.4, y - 0.3, -1.25), (x + 0.4, y - 0.3, 1.25), 0.32, 0.32, sides=6, caps=(True, True), hint=(1, 0, 0))
        half += gem((x + 0.4, y - 0.3, -1.3), 0.3, 0.3, 0.15, segs=5, rings=1)
    tx, ty, _ = up[-1]
    half += block("spine", (tx, ty, 0), (0.9, 0.9, 1.4), rot=rot_z(30))
    f += half + mirror_faces_y(half)
    lo = [(x, 2 * CY - y, z) for x, y, z in up]
    f += chain((6.4, CY - 1.0, -0.6), (6.6, CY - 4.4, -0.6), n=4)
    f += gem((6.7, CY + 0.3, -1.32), 0.4, 0.4, 0.15, segs=5, rings=1)
    return f + string_and_arrow(stage, "rare", up, lo)


def ember_dagger(stage=0):
    """熾の短剣: 炉の火箸を打ち直した短剣。片方の顎が刃に伸び、もう片方は受けの鉤。要の鋲が熾火に光り、二本の柄を鉄線で縛る"""
    f = []
    # 二本の柄 (少し開いて、鉄線で縛る)
    for s in (1, -1):
        f += curve("spine", [(s * 0.35, -3.4, 0), (s * 0.55, -0.6, 0), (s * 0.3, 2.8, 0)], [0.4, 0.42, 0.45], [0.55, 0.55, 0.6], sides=4, caps=(True, False))
        f += ell("spine", (s * 0.35, -3.6, 0), (0.55, 0.45, 0.65), segs=5, rings=1)
    for y in (-2.2, -1.0, 0.2, 1.4):
        f += prism("chain", (0, y - 0.22, 0), (0, y + 0.22, 0), 1.0, 0.9, sides=6, caps=(True, True))
    # 要 (鋲)
    f += prism("spine", (0, 3.6, -0.9), (0, 3.6, 0.9), 1.25, 1.25, sides=8, caps=(True, True), hint=(1, 0, 0))
    f += prism("gem", (0, 3.6, -1.0), (0, 3.6, 1.0), 0.55, 0.55, sides=6, caps=(True, True), hint=(1, 0, 0))
    # 刃 (顎が伸びた細い刃、鉄)
    f += blade("main", [(4.2, -0.9, 0.7, 0.42, 0.42), (8.0, -0.8, 0.8, 0.38, 0.38), (11.4, -0.5, 0.5, 0.3, 0.3), (13.2, 0, 0, 0, 0)],
               colmats=["edge", "edge", "edge", "edge", "edge", "edge"])
    f += gbar([(0.0, 5.0, -0.42), (0.1, 8.2, -0.38), (0.0, 10.6, -0.3)], r=0.17)
    # 受けの鉤 (もう一方の顎、+x へ反って戻る)
    f += curve("spine", [(0.9, 4.0, 0), (2.2, 5.0, 0), (2.6, 6.6, 0), (2.0, 7.6, 0)], [0.45, 0.4, 0.32, 0.12], [0.5, 0.45, 0.36, 0.12], sides=4, caps=(False, True))
    f += block("edge", (-1.0, 3.9, 0), (1.0, 0.8, 1.0), rot=rot_z(-20))
    f += lump("acc", (2.0, 7.4, 0), (0.42, 0.4, 0.4), seed=75, segs=5, rings=1)
    return f


# ================================================================== 伝説
def abyss_ember_sword(stage=0):
    """溶岩流の大剣: 刃は冷えかけた溶岩の流れ。左右にうねる幅広の黒い殻に、蛇行する赤い流れの筋が何本も走り、縁から滴が垂れる。鍔は固まった溶岩の溜まり、金の輪"""
    f = wood_haft(-3.8, 3.0, r=0.95, wrap=(-3.0, 2.4), bands=0)
    for y in (-3.2, 2.6):
        f += ringy("trim", y, 1.2, 0.45)
    f += gem((0, -4.6, 0), 0.6, 0.7, 0.6, segs=6, rings=1, mat="void")
    f += torus("trim", (0, -4.6, 0), 0.8, 0.16, 0.2, n=8, k=3, ax="y")
    # 鍔: 溶岩の溜まり (平たい塊) と下への垂れ
    f += lump("main", (0, 3.8, 0), (3.8, 1.3, 1.8), seed=81, segs=9, rings=2, rough=0.2)
    f += torus("trim", (0, 3.4, 0), 2.6, 0.15, 0.2, n=10, k=3, ax="y")
    for x, L in ((-2.8, 1.6), (-1.1, 2.4), (1.4, 1.5), (2.9, 2.1)):
        f += spike(BONE, "main", (x, 3.2, -0.3), (0, -1, 0), L, 0.5, sides=4, rings=1, hint=(0, 0, 1))
        f += gem((x, 3.2 - L - 0.1, -0.3), 0.24, 0.32, 0.24, segs=4, rings=1)
    # 刃: うねる幅広の殻 (中心が左右に揺れ、幅も波打つ)
    wav = lambda y: 1.0 * math.sin((y - 4.0) * 0.42)
    ys = [4.4, 6.4, 8.4, 10.4, 12.4, 14.4, 16.4, 18.2]
    st = [(y, -(2.6 + 0.5 * math.sin(y * 1.1)) * (1 - (y - 4.4) / 21), (2.6 + 0.5 * math.cos(y * 0.9)) * (1 - (y - 4.4) / 21), 0.8, 0.8, wav(y)) for y in ys]
    f += blade("main", st + [(20.8, 0, 0, 0, 0, wav(20.8))], colmats=["main"] * 6)
    # 流れの筋 (蛇行する光、前と後ろ)
    for z, off_ in ((-0.8, -0.7), (-0.8, 0.8), (0.8, 0.0)):
        pts = [(wav(y) + off_ * (1 - (y - 4.4) / 21) + 0.35 * math.sin(y * 1.3 + off_), y, z * (1 - (y - 4.4) / 30)) for y in (5.0, 7.4, 9.8, 12.2, 14.6, 17.0)]
        f += gbar(pts, r=0.24)
    # 殻の板 (継ぎ目に浮く黒い板、少し)
    for y, x in ((7.0, -0.9), (11.4, 0.8), (15.2, -0.5)):
        f += block("main", (wav(y) + x, y, -0.75), (1.6, 1.4, 0.4), rot=rot_z(15 * x), taper=(0.8, 0.9))
    # 縁の滴
    for x, y in ((2.6, 8.4), (-2.4, 11.2), (1.9, 14.6), (-1.6, 17.0)):
        f += spike(BONE, "main", (x * (1 - (y - 4.4) / 21) + wav(y), y, 0), (0.25 * math.copysign(1, x), -1, 0), 1.5, 0.38, sides=4, rings=1, hint=(0, 0, 1))
        f += gem((x * (1 - (y - 4.4) / 21) + wav(y) + 0.35 * math.copysign(1, x), y - 1.6, 0), 0.2, 0.28, 0.2, segs=4, rings=1)
    return f


def abyss_ember_staff(stage=0):
    """火山弾の杖: 鉄の三叉の先に、噴き上げられた紡錘の火山弾が浮いたまま。殻は割れて赤く、下へ火の尾を引き、まわりを小さな火の粉がめぐる。柄は金の帯"""
    f = wood_haft(-4.0, 13.6, bands=2)
    f += spike(BONE, "spine", (0, -4.0, 0), (0, -1, 0), 1.8, 0.95, sides=6, rings=1, hint=(0, 0, 1))
    for y in (-1.8, 3.4, 13.0):
        f += ringy("trim", y, 1.25, 0.45)
    f += gem((0, -2.6, -1.1), 0.4, 0.4, 0.25, segs=5, rings=1, mat="void")
    # 三叉 (金の縁の鉄、外へ開いて上へ)
    for k in range(3):
        a = math.radians(90 + 120 * k)
        ca, sa = math.cos(a), math.sin(a)
        f += curve("spine", [(0.6 * ca, 13.4, 0.6 * sa), (2.2 * ca, 15.2, 2.2 * sa), (2.4 * ca, 17.0, 2.4 * sa)], [0.42, 0.38, 0.3], [0.42, 0.38, 0.3],
                   sides=4, caps=(False, True))
        f += spike(BONE, "trim", (2.4 * ca, 17.0, 2.4 * sa), (0.2 * ca, 1, 0.2 * sa), 1.2, 0.3, sides=4, rings=1, hint=(0, 0, 1))
    # 火山弾 (紡錘形にねじれた岩、光る割れ目、両端が尖る)
    f += lump("main", (0, 20.0, 0), (1.9, 2.6, 1.8), seed=83, segs=8, rings=3, rough=0.12)
    f += spike(BONE, "main", (0, 22.2, 0), (0.2, 1, 0), 1.8, 0.8, sides=6, rings=1, hint=(0, 0, 1))
    f += gem((0.5, 20.4, -1.5), 0.8, 1.2, 0.4, segs=6, rings=1)
    f += gem((-0.8, 19.4, 1.4), 0.6, 0.9, 0.35, segs=6, rings=1)
    # 火の尾 (下へ)
    for x, L in ((0, 3.0), (0.7, 2.2), (-0.7, 2.0)):
        f += spike(BONE, "gem", (x, 18.2, 0), (x * 0.1, -1, 0), L, 0.5, sides=4, rings=2, hint=(0, 0, 1))
    # 火の粉 (めぐる小さな光)
    for k in range(5):
        a = math.radians(72 * k + 10)
        f += gem((3.2 * math.cos(a), 20.0 + 1.4 * math.sin(a * 2), 3.2 * math.sin(a)), 0.28, 0.28, 0.28, segs=4, rings=1)
    f += torus("trim", (0, 20.0, 0), 3.2, 0.08, 0.1, n=12, k=3, ax="y")
    f += strand("wrap", (1.1, 13.0, -0.8), 2.4, 0.4, r=0.3)
    return f


def abyss_ember_bow(stage=0):
    """黒曜の弓: 腕は打ち欠いた黒曜石の刃の連なり。外の縁は貝殻のような欠け跡で鋭く、芯に熾火が透ける。握りは金を巻いた鉄、淵の核"""
    f = []
    f += prism("grip", (5.0, CY - 2.8, 0), (5.0, CY + 2.8, 0), 1.0, 1.0, sides=8, caps=(False, False), hint=(0, 0, 1))
    f += prism("trim", (5.0, CY - 1.4, 0), (5.0, CY + 1.4, 0), 1.2, 1.2, sides=8, caps=(True, True), hint=(0, 0, 1))
    f += ell("void", (6.2, CY, 0), (0.75, 1.3, 0.85), segs=6, rings=2)
    f += gem((6.75, CY, 0), 0.4, 0.6, 0.5, segs=5, rings=1)
    up = bend(stage, [(5.0, CY + 2.4, 0), (4.5, CY + 5.0, 0), (3.0, CY + 7.6, 0), (0.8, CY + 9.6, 0), (-1.6, CY + 10.7, 0)])
    half = []
    # 芯 (光る細い線) と、黒曜の刃の板 (外へ尖る三角の連なり)
    half += curve("gem", up, [0.3] * 5, [0.3] * 5, sides=4, caps=(False, True), hint=(0, 0, 1))
    for i in range(4):
        (x0, y0, _), (x1, y1, _) = up[i], up[i + 1]
        mx, my = (x0 + x1) / 2, (y0 + y1) / 2
        dx, dy = x1 - x0, y1 - y0
        L = math.hypot(dx, dy)
        nx, ny = dy / L, -dx / L
        w = 1.6 * (1 - 0.18 * i)
        half += extrude("main", [(x0 - nx * 0.5, y0 - ny * 0.5), (x0 + nx * w * 0.6, y0 + ny * w * 0.6), (mx + nx * w, my + ny * w),
                                 (x1 + nx * w * 0.5, y1 + ny * w * 0.5), (x1 - nx * 0.5, y1 - ny * 0.5)], 0.75 - 0.08 * i, c=(0, 0, 0))
        half += spike(BONE, "main", (mx + nx * w * 0.9, my + ny * w * 0.9, 0), (nx, ny, 0), 0.9, 0.25, sides=3, rings=1, hint=(0, 0, 1))
    tx, ty, _ = up[-1]
    half += spike(BONE, "main", (tx, ty, 0), (-0.6, 0.8, 0), 1.6, 0.35, sides=4, rings=1, hint=(0, 0, 1))
    half += prism("trim", (up[1][0], up[1][1] - 0.3, 0), (up[1][0], up[1][1] + 0.3, 0), 0.85, 0.85, sides=6, caps=(True, True), hint=(0, 0, 1))
    f += half + mirror_faces_y(half)
    lo = [(x, 2 * CY - y, z) for x, y, z in up]
    f += chain((4.4, CY - 1.4, -0.8), (4.2, CY - 4.6, -0.8), n=4, mat="trim")
    return f + string_and_arrow(stage, "legend", up, lo)


def abyss_ember_dagger(stage=0):
    """熔け滴の短剣: 滴った熔けた鉄が、波打つ刃のまま固まった短剣。先はまだ赤い雫、鍔は跳ねた飛沫の冠。柄は鉄線巻きに金"""
    f = []
    f += prism("grip", (0, -3.0, 0), (0, 2.4, 0), 0.82, 0.8, sides=8, caps=(True, False))
    for y in (-2.2, -1.2, -0.2, 0.8, 1.8):
        f += prism("chain", (0, y - 0.2, 0), (0, y + 0.2, 0), 0.95, 0.95, sides=8, caps=(True, True))
    f += ringy("trim", -2.9, 0.98, 0.4)
    f += gem((0, -3.8, 0), 0.6, 0.7, 0.6, segs=6, rings=1, mat="void")
    # 飛沫の冠 (外へ跳ねる棘の輪、先に光の粒)
    f += ell("edge", (0, 3.0, 0), (1.6, 0.6, 1.4), segs=8, rings=1)
    for k in range(8):
        a = math.radians(45 * k + 22.5)
        d = (math.cos(a), 0.7, math.sin(a) * 0.8)
        f += spike(BONE, "edge", (1.3 * math.cos(a), 3.1, 1.1 * math.sin(a)), d, 1.5 if k % 2 else 1.0, 0.28, sides=4, rings=1, hint=(0, 1, 0))
    f += torus("trim", (0, 2.7, 0), 1.2, 0.14, 0.18, n=8, k=3, ax="y")
    # 波打つ刃 (クリスの形)
    wv = lambda y: 0.55 * math.sin((y - 3.4) * 1.05)
    st = [(y, -0.95 * (1 - (y - 3.4) / 12), 0.95 * (1 - (y - 3.4) / 12), 0.34, 0.34, wv(y)) for y in (3.4, 5.0, 6.6, 8.2, 9.8, 11.2)]
    f += blade("main", st + [(12.6, 0, 0, 0, 0, wv(12.6))], colmats=["edge", "spine", "edge", "edge", "spine", "edge"])
    f += gbar([(wv(y), y, -0.33) for y in (4.2, 6.0, 7.8, 9.6)], r=0.15)
    f += gem((wv(12.9), 12.9, 0), 0.32, 0.5, 0.32, segs=5, rings=1)
    return f


def abyss_ember_bell_staff(stage=0):
    """坩堝の鈴杖: 鉄の柄の先の二股に、熔けた鉄の坩堝が軸で吊られて傾く。口から赤い熔鉄が光り、縁から一滴が落ちかける。股には鎖と灰の布"""
    f = wood_haft(-4.0, 13.4, bands=2)
    f += spike(BONE, "spine", (0, -4.0, 0), (0, -1, 0), 1.8, 0.95, sides=6, rings=1, hint=(0, 0, 1))
    for y in (-1.8, 3.4, 13.0):
        f += ringy("trim", y, 1.25, 0.45)
    f += gem((0, -2.6, -1.1), 0.4, 0.4, 0.25, segs=5, rings=1, mat="void")
    # 二股 (鍛え鉄)
    for s in (1, -1):
        f += curve("spine", [(s * 0.4, 13.2, 0), (s * 2.4, 14.8, 0), (s * 3.0, 17.4, 0), (s * 2.9, 19.6, 0)], [0.5, 0.45, 0.42, 0.4], [0.5, 0.45, 0.42, 0.4],
                   sides=4, caps=(False, True))
        f += prism("trim", (s * 2.9, 18.6, -0.5), (s * 2.9, 18.6, 0.5), 0.6, 0.6, sides=6, caps=(True, True), hint=(1, 0, 0))
    # 坩堝 (軸で吊られ、+x へ傾く)
    tilt = rot_z(-18) @ rot_x(-30)
    c = V(0, 17.6, 0)
    rings = [[tuple(tilt @ V(rr * math.cos(math.radians(22.5 * k)), y, rr * math.sin(math.radians(22.5 * k))) + c) for k in range(16)]
             for y, rr in ((-2.2, 1.0), (-1.6, 1.9), (0.0, 2.3), (1.4, 2.4), (1.8, 2.55))]
    f += loft(BONE, "acc", rings, caps=("acc", "gem"))
    f += prism("spine", tuple(tilt @ V(-2.4, 0.2, 0) + c), tuple(tilt @ V(2.4, 0.2, 0) + c), 0.35, 0.35, sides=6, caps=(True, True), hint=(0, 1, 0))
    f += prism("trim", tuple(tilt @ V(0, 1.7, 0) + c), tuple(tilt @ V(0, 1.95, 0) + c), 2.62, 2.62, sides=16, caps=(False, False))
    # 注ぎ口と落ちかける一滴
    lip = tilt @ V(2.5, 1.8, 0) + c
    f += block("acc", tuple(lip), (1.0, 0.5, 1.0), rot=tilt)
    f += gem((lip[0] + 0.5, lip[1] - 1.2, 0), 0.35, 0.6, 0.35, segs=5, rings=1)
    f += spike(BONE, "gem", (lip[0] + 0.5, lip[1] - 0.2, 0), (0, -1, 0), 0.7, 0.25, sides=4, rings=1, hint=(0, 0, 1))
    # 股の鎖と布
    f += chain((-2.6, 15.0, -0.4), (-2.9, 12.0, -0.4), n=4)
    f += rag((1.0, 13.0, -0.8), 3.0, 0.9, sign=1, mat="wrap", th=0.25)
    return f


# ================================================================== 一覧
def _w(fn, jp, tier, pose, grip, cen, scale, inlay, stages=1):
    if tier == "legend":
        kind = {"thunder": "bow"}.get(pose) or ("hammer" if pose == "ember" else "dagger" if scale >= 15 else "sword" if scale == 10.0 else "staff")
        base, fn = fn, (lambda stage=0, base=fn, kind=kind: base(stage) + legend_finish(kind, seed=len(jp)))
        fn.__doc__ = base.__doc__
    e = dict(jp=jp, tier=tier, glow="ember", pose=pose, grip=grip, cen=cen, scale=scale,
             opts={"a": ((lambda stage=0: fn(stage)), inlay, ("edge",), (fn.__doc__ or "").split(":", 1)[-1].strip())})
    if stages > 1:
        e["stages"] = stages
    return e


ST = dict(pose="whale", grip=(0.0, 1.5, 0.0), cen=(0.0, 9.0, 0.0), scale=9.5)
BW = dict(pose="thunder", grip=(5.0, 8.0, 0.0), cen=(2.0, 8.0, 0.0), scale=12.0, stages=4)
DG = dict(pose="whale", grip=(0.0, 0.6, 0.0), cen=(0.0, 4.8, 0.0), scale=16.0)
SW = dict(pose="whale", grip=(0.0, 1.0, 0.0), cen=(0.0, 9.0, 0.0), scale=10.0)
off = inlay_line(["main"], 99.0, 99.5)
WEAPONS = {
    "ember_staff": _w(ember_staff, "熾火の杖", "rare", inlay=off, **ST),
    "ember_censer": _w(ember_censer, "灰の香炉", "rare", inlay=off, **ST),
    "ember_greatsword": _w(ember_greatsword, "熾火の大剣", "rare", inlay=off, **SW),
    "ember_bow": _w(ember_bow, "灰の弓", "rare", inlay=off, **BW),
    "ember_dagger": _w(ember_dagger, "熾の短剣", "rare", inlay=off, **DG),
    "abyss_ember_sword": _w(abyss_ember_sword, "溶岩流の大剣", "legend", inlay=off, **SW),
    "abyss_ember_staff": _w(abyss_ember_staff, "火山弾の杖", "legend", inlay=off, **ST),
    "abyss_ember_bow": _w(abyss_ember_bow, "黒曜の弓", "legend", inlay=off, **BW),
    "abyss_ember_dagger": _w(abyss_ember_dagger, "熔け滴の短剣", "legend", inlay=off, **DG),
    "abyss_ember_bell_staff": _w(abyss_ember_bell_staff, "坩堝の鈴杖", "legend", inlay=off, **ST),
}
