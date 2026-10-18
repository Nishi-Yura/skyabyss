"""鯨骨の大剣 3 案。白い鯨骨 + 青灰の石 + 苔、光は水色。rarity: common / rare / legend。"""
from __future__ import annotations

from wkit import *  # noqa: F401,F403
from wkit import block, blade, prism, curve, extrude, feather, mirror_x, remat, at, V, rot_x, rot_y, rot_z
from paint import line_mask, edge_mask, spot_mask

TITAN = "whale"
GRIP = (0.0, 1.8, 0.0)       # 手の位置 (模型で握る点)


def hilt(r, guard_w=6.0, pommel=True):
    f = []
    # 柄: 巻き (苔の縄) と石の 3 段
    f += prism("grip", (0, -1.4, 0), (0, 0.2, 0), 1.15, 1.0, sides=6, caps=(False, False))
    f += prism("wrap", (0, 0.2, 0), (0, 1.9, 0), 1.2, 1.05, sides=6, caps=(False, False))
    f += prism("grip", (0, 1.9, 0), (0, 3.4, 0), 1.15, 1.0, sides=6, caps=(False, False))
    if pommel:
        f += block("guard", (0, -2.3, 0), (3.2, 1.7, 2.8), taper=(0.7, 0.7), skip=("u",))
        f += block("trim", (0, -1.35, 0), (2.6, 0.5, 2.2))
    return f


# ---------------------------------------------------------------- A 肋の曲刃
def a(r="common"):
    f = hilt(r)
    st = [(5.0, -2.5, 2.5, 1.0, 1.0, 0.0), (8.5, -2.8, 2.6, 1.1, 1.1, 0.1), (12.5, -2.9, 2.5, 1.0, 1.0, 0.6),
          (16.5, -2.5, 2.1, 0.8, 0.8, 1.4), (19.4, -1.5, 1.2, 0.5, 0.5, 2.6), (21.0, 0, 0, 0, 0, 3.7)]
    f += blade("main", st)
    # 背のこぶ (肋の関節)
    for y, cx in ((9.0, 0.2), (13.0, 0.7), (16.6, 1.5)):
        f += block("acc", (-2.9 + cx, y, 0), (1.3, 1.4, 1.7), taper=(0.5, 0.7))
    # 鍔: 反り上がる 2 本の肋
    for s in (1, -1):
        f += curve("guard", [(s * 1.2, 4.0, 0), (s * 4.2, 3.6, 0), (s * 6.4, 4.4, 0), (s * 7.2, 6.0, 0)],
                   [1.5, 1.3, 1.0, 0.45], [1.6, 1.4, 1.1, 0.5], sides=4)
    f += block("guard", (0, 4.0, 0), (3.6, 2.0, 3.2), taper=(0.9, 0.9))
    f += block("gem", (0, 5.5, 0), (1.5, 1.2, 1.8), taper=(0.8, 0.8))
    if r in ("rare", "legend"):
        # 刃の付け根の副刃 (背側に小さな骨の刃)
        f += blade("main", [(5.8, -4.6, -2.4, 0.7, 0.7, 0), (8.6, -4.8, -2.7, 0.7, 0.7, 0.0), (11.0, -3.6, -2.7, 0.4, 0.4, 0.2),
                            (12.6, 0, 0, 0, 0, -2.9)])
        f += block("trim", (0, 6.5, 0), (3.6, 1.0, 2.4), taper=(0.9, 0.9))
        for s in (1, -1):
            f += block("trim", (s * 7.0, 5.6, 0), (1.3, 1.3, 1.6), taper=(0.7, 0.7))
    if r == "legend":
        # 背びれの冠: 刃の背に沿って並ぶ 3 枚の骨の板と、鍔の石の翼
        for k, (y, h) in enumerate(((9.0, 4.6), (14.2, 4.4))):
            f += at(extrude("edge", [(0, 0), (1.8, h * 0.25), (0.6, h), (-1.2, h * 0.5)], 0.9, c=(0, 0, 0)), None, (-3.4 + 0.15 * k, y, 0))
        for s in (1, -1):
            f += block("spine", (s * 5.2, 6.3, 0), (1.6, 3.6, 1.6), rot=rot_z(-s * 12), taper=(0.5, 0.6))
    return f


def inlay_a(env, kind):
    cx = lambda y: 0.0 + 0.0 * y
    # 刃の中心線 (反りに沿う)
    def cxf(y):
        return np.interp(y, [5, 8.5, 12.5, 16.5, 19.4], [0, 0.1, 0.6, 1.4, 2.6])
    if kind == "rare":
        return line_mask(env, ["main"], cxf, 5.5, 8.2, 0.55)
    if kind == "legend":
        return line_mask(env, ["main"], cxf, 8.8, 11.5, 0.55)
    if kind == "p5":
        return line_mask(env, ["main"], cxf, 5.5, 18, 0.5, dash=(2.4, 1.3))
    if kind == "p10":
        return line_mask(env, ["main"], cxf, 5.5, 19, 0.6)
    return env["lit"] < -9


EDGE_A = ("edge",)


# ---------------------------------------------------------------- B 牙の大剣
def b(r="common"):
    f = hilt(r)
    st = [(5.0, -2.6, 2.6, 1.2, 1.2), (9.0, -3.0, 3.0, 1.5, 1.5), (14.0, -2.6, 2.6, 1.3, 1.3), (18.0, -1.4, 1.4, 0.8, 0.8),
          (20.4, 0, 0, 0, 0)]
    f += blade("main", st)
    # 牙のうねり: 刃の面に斜めの稜 (石の帯 3 本)
    for y in (8.0, 11.5, 15.0):
        f += block("spine", (0, y, 0), (6.4 - 0.18 * (y - 8), 0.7, 3.6 - 0.15 * (y - 8)), rot=rot_z(0), taper=(0.97, 0.97))
    # 鍔: 鯨の尾びれ (中央が凹んだ 2 枚の扇)
    for s in (1, -1):
        pts = [(0.0, 0.0), (3.2, -0.8), (7.2, 1.0), (8.0, 3.6), (4.6, 2.6), (1.4, 1.8)]
        pts = [(s * x, y) for x, y in pts]
        if s < 0:
            pts = pts[::-1]
        f += extrude("guard", pts, 1.5, c=(0, 3.6, 0), mats=["edge", "guard", "guard", "edge", "guard", "guard"])
    f += block("guard", (0, 4.1, 0), (2.8, 2.0, 3.0), taper=(0.9, 0.9))
    f += block("gem", (0, 5.4, 0), (1.5, 1.1, 1.7), taper=(0.8, 0.8))
    # 背びれの小さな棘 (刃の根元の両脇)
    for s in (1, -1):
        f += spike(BONE, "edge", (s * 2.0, 5.3, 0), (s * 0.5, 1, 0), 3.2, 0.8, sides=4, rings=2, hint=(0, 0, 1))
    if r in ("rare", "legend"):
        f += block("trim", (0, 6.3, 0), (4.2, 1.0, 2.8), taper=(0.9, 0.9))
        f += block("trim", (0, -3.3, 0), (3.6, 0.9, 3.0))
        for s in (1, -1):
            f += at(extrude("edge", [(0, 0), (1.4, 1.6), (0.2, 4.2), (-0.8, 1.4)], 0.8), None, (s * 3.0, 6.2, 0))
    if r == "legend":
        # 刃先の二つ目の牙 (刃の左右に寄り添う短い牙) と柄頭の尾びれ
        for s in (1, -1):
            f += spike(BONE, "main", (s * 2.6, 9.6, 0), (s * 0.35, 1, 0), 6.0, 1.0, sides=4, rings=3, hint=(0, 0, 1))
        f += extrude("spine", [(0, 0), (2.4, -1.4), (3.0, -3.4), (0, -2.4), (-3.0, -3.4), (-2.4, -1.4)], 0.9, c=(0, -3.0, 0))
    return f


def inlay_b(env, kind):
    if kind == "rare":
        return line_mask(env, ["main"], lambda y: 0 * y, 5.6, 7.4, 0.5)
    if kind == "legend":
        return line_mask(env, ["main"], lambda y: 0 * y, 16.0, 18.2, 0.5)
    if kind == "p5":
        return line_mask(env, ["main", "spine"], lambda y: 0 * y, 5.6, 18.5, 0.5, dash=(3.5, 1.7))
    if kind == "p10":
        return line_mask(env, ["main", "spine"], lambda y: 0 * y, 5.6, 19.6, 0.6)
    return env["lit"] < -9


# ---------------------------------------------------------------- C 背骨の大剣
def c(r="common"):
    f = hilt(r)
    # 刃: 厚い骨の板 (先は斜めに断つ)
    st = [(5.0, -1.6, 3.4, 1.0, 1.0), (10.0, -1.7, 3.5, 1.1, 1.1), (15.0, -1.5, 3.3, 1.0, 1.0), (18.6, -1.0, 2.2, 0.6, 0.6),
          (20.4, 1.0, 1.0, 0.0, 0.0)]
    f += blade("main", st)
    # 背: 椎骨の柱 (石の六角柱 4 つ。それぞれに棘)
    for k, y in enumerate((6.4, 10.0, 13.6, 17.0)):
        wd = 1.5 - 0.1 * k
        f += prism("spine", (-2.4, y - 1.4, 0), (-2.4, y + 1.4, 0), wd, wd * 1.15, sides=6, caps=(k == 0, k == 3))
        f += spike(BONE, "edge", (-3.4, y, 0), (-1, 0.15, 0), 2.8, 0.7, sides=4, rings=2, hint=(0, 0, 1))
    f += block("gem", (-2.4, 15.2, -1.4), (0.9, 0.9, 0.7))
    # 鍔: 六角の円盤 (椎骨)
    f += prism("guard", (0, 3.2, 0), (0, 4.8, 0), 4.2, 3.0, sides=6, caps=(True, True), phase=30)
    f += block("gem", (0, 5.5, 0), (1.5, 1.0, 1.7), taper=(0.8, 0.8))
    if r in ("rare", "legend"):
        f += prism("trim", (0, 4.8, 0), (0, 5.6, 0), 3.8, 2.6, sides=6, caps=(False, True), phase=30)
        f += block("trim", (0, -3.3, 0), (3.6, 0.9, 3.0))
        f += spike(BONE, "edge", (2.6, 4.0, 0), (1, 0.5, 0), 3.4, 0.9, sides=4, rings=2, hint=(0, 0, 1))
    if r == "legend":
        f += spike(BONE, "edge", (-4.8, 4.0, 0), (-1, 0.5, 0), 3.4, 0.9, sides=4, rings=2, hint=(0, 0, 1))
        f += prism("spine", (-2.4, 18.4, 0), (-2.4, 19.8, 0), 1.0, 1.1, sides=6)
        f += spike(BONE, "edge", (-2.4, 19.8, 0), (0, 1, 0), 2.2, 0.9, sides=4, rings=2, hint=(0, 0, 1))
    return f


def inlay_c(env, kind):
    c3 = lambda y: 0.9 + 0 * y
    if kind == "rare":
        return line_mask(env, ["main"], c3, 5.6, 8.0, 0.5)
    if kind == "legend":
        return line_mask(env, ["main"], c3, 10.0, 13.0, 0.5)
    if kind == "p5":
        return line_mask(env, ["main"], c3, 5.6, 18, 0.5, dash=(3.6, 1.8))
    if kind == "p10":
        return line_mask(env, ["main"], c3, 5.6, 19.2, 0.6)
    return env["lit"] < -9


OPTS = dict(a=(a, inlay_a, EDGE_A), b=(b, inlay_b, ("edge",)), c=(c, inlay_c, ("edge",)))
