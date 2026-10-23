"""灰被りの火かき棒 (作り直し)。太い鉄の軸と、先の大きな鉤 (突いて引っかける) + 玄武岩の熱よけ板 (割れ目の火) + 握りの鎖の輪と灰。
握り y 2 (変えない)、y -3..19。階級: 通常 = 軸 + 熱よけ + 右の鉤 / レア = 左の返し + 帯 + 鎖 / 伝説 = 板の角 2 枚 + 長い返し + 先の熾火の塊 + 背びれ。"""
from __future__ import annotations

from em_kit import *  # noqa: F401,F403
from em_kit import chain, ridge, band
from paint import line_mask, spot_mask

TITAN = "ember"
GRIP = (0.0, 2.0, 0.0)
NAME = "灰被りの火かき棒"
ID = "thrall_poker"
EDGE = ("edge",)


def faces(r="common"):
    t = {"common": 0, "rare": 1, "legend": 2}[r]
    f = []
    f += prism("grip", (0, -2.4, 0), (0, 4.6, 0), 0.95, 0.95, sides=6, caps=(False, True), hint=(0, 0, 1))
    f += prism("wrap", (0, -0.4, 0), (0, 2.8, 0), 1.1, 1.1, sides=6, caps=(False, False), hint=(0, 0, 1))
    f += block("spine", (0, -2.7, 0), (2.4, 1.0, 2.2), taper=(0.7, 0.7), skip=("u",))
    f += block("trim", (0, 4.4, 0), (2.4, 0.6, 2.2))
    # 太い鉄の軸
    f += prism("edge", (0, 4.6, 0), (0, 15.4, 0), 0.95, 0.85, sides=6, caps=(False, False), hint=(0, 0, 1))
    # 玄武岩の熱よけ板: 割れ目が光る (大きめの面)
    f += block("main", (0, 10.2, 0), (5.0, 2.2, 3.0), rot=rot_z(-4), taper=(0.9, 0.9))
    f += block("gem", (0.2, 10.2, 0), (0.8, 2.3, 3.2), taper=(0.9, 0.9))
    # 穂先: 太い鉄の棘 + 右へ大きく巻く鉤 (先は太く、突く)
    f += spike(BONE, "edge", (0, 15.2, 0), (0, 1, 0), 4.0, 1.0, sides=4, rings=2, hint=(0, 0, 1))
    f += curve("edge", [(0, 14.0, 0), (0.4, 16.2, 0), (2.0, 18.0, 0), (3.6, 17.2, 0), (3.8, 15.2, 0)],
               [0.95, 0.9, 0.8, 0.7, 0.5], [0.9, 0.85, 0.75, 0.65, 0.5], sides=4, caps=(False, True), hint=(0, 0, 1))  # 先は細らせず熾火の塊を抱える (2026-10-23: 塊が浮いて見えた)
    f += chain((0.0, -2.9, 0.0), (1.0, -6.4, 0.0), 3, sag=0.4)
    if t >= 1:
        f += band((0, 12.6, 0), 1.45, 0.7)
        f += band((0, 7.6, 0), 1.35, 0.7)
        f += block("trim", (0, 8.2, 0), (3.0, 0.6, 2.6))
        # 左の返し
        f += curve("edge", [(0, 7.0, 0), (-1.8, 7.8, 0), (-3.0, 9.4, 0), (-3.1, 11.0, 0)],
                   [0.8, 0.65, 0.5, 0.15], [0.8, 0.65, 0.5, 0.2], sides=4, caps=(False, False), hint=(0, 0, 1))
    if t == 2:
        f += at(extrude("main", [(0, 0), (2.6, 0.2), (2.0, 2.6), (0.2, 2.0)], 1.6), None, (-2.4, 10.8, 0))
        f += at(extrude("main", [(0, 0), (-2.6, 0.2), (-2.0, 2.6), (-0.2, 2.0)], 1.6), None, (2.4, 10.6, 0))
        f += block("gem", (3.8, 15.0, 0), (1.3, 1.3, 1.5), taper=(0.8, 0.8))
        f += ridge((0.0, 5.6, -1.2), (0, 0.3, -1), 3, 2.2, (0.0, 2.0, 0.0), 0.7, mat="edge")
    return f


def inlay(env, kind):
    if kind == "rare":
        return spot_mask(env, ["edge"], (0, 13.6, 0), 1.0)
    if kind == "legend":
        return spot_mask(env, ["main"], (-1.4, 10.2, -1.5), 1.2) | spot_mask(env, ["main"], (1.4, 10.2, -1.5), 1.2)
    if kind == "p5":
        return (np.isin(env["mats"], ["edge"]) & env["convex"]) & (env["Y"] > 5) & (env["Y"] < 15.0) & (env["Y"] % 4.0 < 1.2)
    if kind == "p10":
        return (np.isin(env["mats"], ["edge"]) & env["convex"]) & (env["Y"] > 5) & (env["Y"] < 15.0) & (env["Y"] % 3.0 < 1.8)
    return env["lit"] < -9


import wd2  # noqa: E402
wd2.install(ID, __name__)   # D2 (整え): 面取り + 輪 + 宝石 + 光る線
