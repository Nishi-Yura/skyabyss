"""余燼の戦斧 3 案。玄武岩の黒い板・光る割れ目・鍛冶の鉄、光は橙。
座標: 柄が x = 0 で y -3..13、刃の向きは -x (今の item モデルと同じ)。"""
from __future__ import annotations

from wkit import *  # noqa: F401,F403
from wkit import block, blade, prism, curve, extrude, feather, mirror_x, remat, at, V, rot_x, rot_y, rot_z
from paint import line_mask, edge_mask, spot_mask

TITAN = "ember"
GRIP = (0.0, 3.0, 0.0)


def haft(r, top=13.0):
    f = []
    f += prism("grip", (0, -2.4, 0), (0, 0.2, 0), 1.0, 1.0, sides=6, caps=(False, False))
    f += prism("wrap", (0, 0.2, 0), (0, 4.8, 0), 1.15, 1.15, sides=6, caps=(False, False))
    f += prism("grip", (0, 4.8, 0), (0, top, 0), 1.0, 1.0, sides=6, caps=(False, True))
    f += block("trim", (0, 5.2, 0), (2.5, 0.6, 2.5))
    f += block("trim", (0, -0.1, 0), (2.4, 0.6, 2.4))
    f += block("spine", (0, -2.7, 0), (2.4, 1.2, 2.4), taper=(0.7, 0.7), skip=("u",))
    return f


def head_blade(mat, st, cy, colmats=None):
    """軸が -x を向く刃 (blade は軸 +y なので z 軸まわりに 90 度)。"""
    fs = blade(mat, st, colmats=colmats)
    return at(fs, rot_z(90), (0, cy, 0))


# ---------------------------------------------------------------- A 割れ板の斧
def a(r="common"):
    f = haft(r)
    cy = 14.0
    body = [(0.5, -3.3, 3.3, 1.5, 1.5), (3.0, -4.4, 4.2, 1.5, 1.5), (5.4, -5.2, 5.0, 1.1, 1.1)]
    f += head_blade("main", body, cy, colmats=["main"] * 6)
    f += block("gem", (-5.9, cy, 0), (0.7, 9.6, 1.4), taper=(1, 0.8))
    edge = [(6.3, -5.4, 5.2, 0.9, 0.9), (8.8, -6.4, 5.6, 0.1, 0.1)]
    f += head_blade("edge", edge, cy, colmats=["edge"] * 6)
    # 背 (ポール): 鉄の塊と後ろ向きの棘
    f += block("spine", (1.6, cy, 0), (2.8, 3.6, 2.6), taper=(0.9, 0.8))
    f += spike(BONE, "spine", (2.8, cy, 0), (1, 0.15, 0), 3.4, 1.1, sides=4, rings=2, hint=(0, 0, 1))
    if r in ("rare", "legend"):
        f += spike(BONE, "edge", (-1.0, cy + 4.6, 0), (-0.2, 1, 0), 3.8, 1.0, sides=4, rings=2, hint=(0, 0, 1))
        f += block("trim", (0, cy - 3.0, 0), (2.8, 0.6, 3.0))
        f += block("trim", (-3.0, cy + 5.4, 0), (3.6, 0.9, 2.6))
        f += block("trim", (-3.0, cy - 5.4, 0), (3.6, 0.9, 2.6))
    if r == "legend":
        # 刃の下の長いひげと、板の割れの上のもう一枚の大板
        f += at(extrude("main", [(0, 0), (-3.4, -1.2), (-6.4, -4.6), (-2.0, -5.6), (0.4, -3.0)], 1.4), None, (0, cy - 3.0, 0))
        f += block("gem", (-3.6, cy - 5.2, 0), (0.7, 1.6, 1.0), taper=(0.8, 0.8))
        f += spike(BONE, "edge", (-1.0, cy - 4.0, 0), (-0.4, -1, 0), 3.2, 0.9, sides=4, rings=2, hint=(0, 0, 1))
    return f


def inlay_a(env, kind):
    if kind == "rare":
        return spot_mask(env, ["spine"], (2.8, 14.0, 0), 1.2)
    if kind == "legend":
        return spot_mask(env, ["edge"], (-6.0, 14.0, 0), 1.4)
    if kind == "p5":
        return line_mask(env, ["edge"], lambda y: 0 * y, -99, 99, 9) & (env["Y"] > 12.0) & (env["Y"] < 16.0) & (env["X"] < -6.2) & (np.abs(env["N"][..., 2]) > 0.4)
    if kind == "p10":
        return (np.isin(env["mats"], ["edge"]) & env["convex"])
    return env["lit"] < -9


# ---------------------------------------------------------------- B 二枚刃の鍛造斧 (大きな三日月 + 小さな返し刃)
def b(r="common"):
    f = haft(r)
    cy = 13.0
    # 中央の鉄の核
    f += block("spine", (0, cy, 0), (3.4, 6.0, 3.0), taper=(0.9, 0.9))
    f += block("gem", (0, cy, 0), (1.6, 3.2, 3.2), taper=(0.9, 0.9))
    # 大きな刃 (-x、上下に反った三日月)
    big = [(1.4, -3.0, 3.0, 1.3, 1.3), (3.6, -4.6, 3.8, 1.3, 1.3, 0, 0), (6.0, -6.8, 5.6, 0.9, 0.9), (8.6, -8.4, 7.4, 0.15, 0.15)]
    f += head_blade("main", big, cy, colmats=["main", "main", "main", "main", "main", "main"])
    # 小さな刃 (+x)
    small = [(1.4, -2.6, 2.6, 1.1, 1.1), (3.4, -3.4, 3.2, 1.0, 1.0), (5.2, -4.2, 3.8, 0.12, 0.12)]
    f += at(head_blade("main", small, cy, colmats=["edge", "main", "edge", "edge", "main", "edge"]), rot_z(180), (0, 2 * cy, 0))
    f += spike(BONE, "spine", (0, cy + 3.0, 0), (0, 1, 0), 3.6, 1.1, sides=4, rings=2, hint=(1, 0, 0))
    if r in ("rare", "legend"):
        f += block("trim", (0, cy - 3.4, 0), (3.0, 0.9, 3.4))
        f += block("trim", (-5.0, cy + 6.4, 0), (3.2, 0.9, 2.4))
        f += block("trim", (-5.0, cy - 6.4, 0), (3.2, 0.9, 2.4))
        f += block("trim", (0, cy + 3.3, 0), (3.0, 0.6, 3.2))
    if r == "legend":
        f += spike(BONE, "edge", (-5.6, cy + 3.6, 0), (-0.6, 1, 0), 4.0, 1.0, sides=4, rings=2, hint=(0, 0, 1))
        f += spike(BONE, "edge", (-5.6, cy - 3.6, 0), (-0.6, -1, 0), 4.0, 1.0, sides=4, rings=2, hint=(0, 0, 1))
        f += block("gem", (-7.0, cy, 0), (0.7, 3.0, 1.0), taper=(0.8, 0.8))
    return f


def inlay_b(env, kind):
    if kind == "rare":
        return spot_mask(env, ["main"], (-4.6, 13.0, 1.5), 1.6) | spot_mask(env, ["main"], (-4.6, 13.0, -1.5), 1.6)
    if kind == "legend":
        return spot_mask(env, ["main"], (-6.6, 13.0, -1.0), 1.8) | spot_mask(env, ["main"], (-6.6, 13.0, 1.0), 1.8)
    if kind == "p5":
        return (np.isin(env["mats"], ["edge"]) & env["convex"]) & (env["X"] < -2) & (env["Y"] % 2.0 < 1.0)
    if kind == "p10":
        return (np.isin(env["mats"], ["edge"]) & env["convex"]) & (env["X"] < -1)
    return env["lit"] < -9


# ---------------------------------------------------------------- C 岩塊の戦斧 (積んだ玄武岩と鋸の刃)
def c(r="common"):
    f = haft(r)
    cy = 14.0
    f += block("main", (-2.8, cy - 0.4, 0), (5.2, 6.6, 3.4), rot=rot_z(6), taper=(0.9, 0.9))
    f += block("main", (-4.6, cy + 4.6, 0), (4.8, 4.6, 3.2), rot=rot_z(-12), taper=(0.75, 0.8))
    f += block("main", (-4.8, cy - 4.6, 0), (4.6, 4.2, 3.0), rot=rot_z(14), taper=(0.8, 0.8))
    f += block("gem", (-0.4, cy + 2.0, 0), (1.3, 1.2, 3.0))
    f += block("gem", (-0.6, cy - 2.4, 0), (1.3, 1.2, 3.0))
    # 鋸の刃 (鉄の楔 5 つ、-x へ)
    for k, dy in enumerate((-5.6, -2.8, 0.0, 2.8, 5.6)):
        f += at(extrude("edge", [(0, 0), (-2.0 - 0.3 * (k % 2), 0.6), (0, 2.2)], 1.0), None, (-6.8 + 0.4 * abs(dy) * 0.4, cy + dy - 1.0, 0))
    # 鉄の帯 (岩を柄にくくる)
    f += block("trim", (0, cy + 3.6, 0), (3.0, 0.7, 3.8))
    f += block("trim", (0, cy - 3.6, 0), (3.0, 0.7, 3.8))
    f += block("spine", (2.0, cy, 0), (2.2, 3.0, 2.4), taper=(0.8, 0.8))
    if r in ("rare", "legend"):
        f += spike(BONE, "edge", (1.0, cy + 3.0, 0), (0.3, 1, 0), 3.0, 0.9, sides=4, rings=2, hint=(0, 0, 1))
        f += block("trim", (-1.0, cy + 3.7, 0), (3.2, 0.9, 3.8))
        f += block("trim", (-1.0, cy - 3.7, 0), (3.2, 0.9, 3.8))
    if r == "legend":
        f += block("main", (-8.6, cy + 1.0, 0), (3.2, 3.6, 2.6), rot=rot_z(-20), taper=(0.7, 0.7))
        f += spike(BONE, "edge", (1.0, cy - 3.0, 0), (0.3, -1, 0), 3.0, 0.9, sides=4, rings=2, hint=(0, 0, 1))
    return f


def inlay_c(env, kind):
    if kind == "rare":
        return spot_mask(env, ["main"], (-1.8, 16.0, -1.7), 1.6)
    if kind == "legend":
        return spot_mask(env, ["main"], (-4.6, 18.6, -1.6), 1.6) | spot_mask(env, ["main"], (-4.8, 9.4, -1.5), 1.6)
    if kind == "p5":
        return (np.isin(env["mats"], ["edge"]) & env["convex"]) & (env["Y"] % 2.0 < 1.0)
    if kind == "p10":
        return (np.isin(env["mats"], ["edge"]) & env["convex"])
    return env["lit"] < -9


OPTS = dict(a=(a, inlay_a, ("edge",)), b=(b, inlay_b, ("edge",)), c=(c, inlay_c, ("edge",)))
