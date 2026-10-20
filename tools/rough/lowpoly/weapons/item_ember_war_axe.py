"""余燼の戦斧 (作り直し)。大きな玄武岩の欠けた頭 (A の量塊) + 鉄の刃と帯・鎖・灰 (C の使い込み) + 背の火とかげの背びれ。
握り y 3 (変えない)、柄 x 0、刃は -x。階級: 通常 = 欠けた頭 + 鉄の背 + 帯 / レア = 背びれ 3 + 上の角 + 鎖 / 伝説 = 刃の下のひげの大板 + 背びれ 5 + 2 本の鎖 + 太い割れ目。"""
from __future__ import annotations

from em_kit import *  # noqa: F401,F403
from em_kit import chain, ridge, band
from d_ember import haft, head_blade
from paint import line_mask, spot_mask

TITAN = "ember"
GRIP = (0.0, 3.0, 0.0)
NAME = "余燼の戦斧"
ID = "ember_war_axe"
EDGE = ("edge",)


def faces(r="common"):
    t = {"common": 0, "rare": 1, "legend": 2}[r]
    f = haft(r)
    cy = 14.0
    body = [(0.5, -3.3, 3.3, 1.5, 1.5), (3.0, -4.4, 4.2, 1.5, 1.5), (5.4, -5.2, 5.0, 1.1, 1.1)]
    f += head_blade("main", body, cy, colmats=["main"] * 6)
    f += block("gem", (-5.9, cy, 0), (0.7, 9.6, 1.4), taper=(1, 0.8))
    edge = [(6.3, -5.4, 5.2, 0.9, 0.9), (8.8, -6.4, 5.6, 0.1, 0.1)]
    f += head_blade("edge", edge, cy, colmats=["edge"] * 6)
    # 欠け: 刃の下半分が一段引っ込む (割れた跡)
    f += block("soot", (-8.0, cy - 4.4, 0), (1.6, 2.2, 1.0), taper=(0.6, 0.8))
    # 背: 鉄の塊と火とかげの尾のような後ろ向きの棘
    f += block("spine", (1.6, cy, 0), (2.8, 3.6, 2.6), taper=(0.9, 0.8))
    f += spike(BONE, "spine", (2.8, cy, 0), (1, 0.15, 0), 3.4, 1.1, sides=4, rings=2, hint=(0, 0, 1))
    # 柄の頭の鉄の帯 (頭を締める) と鎖
    f += band((0, cy - 3.6, 0), 1.35, 0.8)
    f += band((0, cy + 3.0, 0), 1.25, 0.7)
    f += chain((0.0, -2.9, 0.0), (1.2, -7.0, 0.0), 3, sag=0.4)
    if t >= 1:
        f += spike(BONE, "edge", (-1.0, cy + 4.6, 0), (-0.2, 1, 0), 3.8, 1.0, sides=4, rings=2, hint=(0, 0, 1))
        f += block("trim", (0, cy - 3.0, 0), (2.8, 0.6, 3.0))
        f += block("trim", (-3.0, cy + 5.4, 0), (3.6, 0.9, 2.6))
        f += ridge((-1.0, cy + 5.2, 0.0), (0.4, 1, 0), 3, 3.0, (1.6, 0.0, 0.0), 0.9)
    if t == 2:
        # 刃の下の長いひげの大板と、頭の背の二つ目の鎖
        f += at(extrude("main", [(0, 0), (-3.4, -1.2), (-6.4, -4.6), (-2.0, -5.6), (0.4, -3.0)], 1.4), None, (0, cy - 3.0, 0))
        f += block("gem", (-3.6, cy - 5.2, 0), (0.7, 1.6, 1.0), taper=(0.8, 0.8))
        f += chain((2.0, cy - 1.0, 0.0), (5.4, cy - 5.0, 0.0), 3, sag=0.6)
    return f


def inlay(env, kind):
    if kind == "rare":
        return spot_mask(env, ["spine"], (2.8, 14.0, 0), 1.2)
    if kind == "legend":
        return spot_mask(env, ["edge"], (-6.0, 14.0, 0), 1.4)
    if kind == "p5":
        return line_mask(env, ["edge"], lambda y: 0 * y, -99, 99, 9) & (env["Y"] > 12.0) & (env["Y"] < 16.0) & (env["X"] < -6.2) & (np.abs(env["N"][..., 2]) > 0.4)
    if kind == "p10":
        return (np.isin(env["mats"], ["edge"]) & env["convex"])
    return env["lit"] < -9


import wd2  # noqa: E402
wd2.install(ID, __name__)   # D2 (整え): 面取り + 輪 + 宝石 + 光る線
