"""鍛え手の大鎚 (作り直し)。一回り小さい玄武岩の頭 (質量) + 鉄の打面と帯 + 光は頭の割れ目の細い線だけ (水玉をやめる) + 柄頭の鎖。
握り y 3 (変えない)、y -7..22、x ±8。階級: 通常 = 頭 + 帯 + 鎖 / レア = 片側のピーン (尖った背) + 鉄の棘 + 割れ目の線 / 伝説 = 背びれの列 + 2 本目の鎖と鉤 + 割れた背板。"""
from __future__ import annotations

from em_kit import *  # noqa: F401,F403
from em_kit import chain, ridge, band
from paint import line_mask, spot_mask

TITAN = "ember"
GRIP = (0.0, 3.0, 0.0)
NAME = "鍛え手の大鎚"
ID = "smith_hammer"
EDGE = ("edge",)
CY_V = 8.5
SC_V = 8.0


def faces(r="common"):
    t = {"common": 0, "rare": 1, "legend": 2}[r]
    f = []
    f += prism("grip", (0, -6.0, 0), (0, 14.4, 0), 1.15, 1.15, sides=6, caps=(False, True), hint=(0, 0, 1))
    f += prism("wrap", (0, -1.0, 0), (0, 5.0, 0), 1.3, 1.3, sides=6, caps=(False, False), hint=(0, 0, 1))
    f += block("spine", (0, -6.4, 0), (3.0, 1.2, 2.6), taper=(0.7, 0.7), skip=("u",))
    f += block("trim", (0, 5.4, 0), (3.2, 0.7, 2.8))
    f += block("trim", (0, -1.4, 0), (3.0, 0.7, 2.6))
    # 頭: 小さめの玄武岩 (左は低く、右は高い一段の段差)
    f += block("main", (-3.6, 17.0, 0), (7.2, 5.6, 3.4), rot=rot_z(4), taper=(0.96, 0.95))
    f += block("main", (3.8, 18.2, 0), (7.4, 6.2, 3.6), rot=rot_z(-4), taper=(0.96, 0.95))
    # 打面の鉄 (両端) と頭を締める帯
    f += block("edge", (-7.6, 17.2, 0), (1.8, 5.0, 3.2), rot=rot_z(3))
    f += block("edge", (7.8, 18.4, 0), (1.8, 5.4, 3.4), rot=rot_z(-3))
    f += block("spine", (0, 14.4, 0), (3.4, 1.6, 2.8), taper=(0.9, 0.9))
    f += band((-3.6, 17.0, 0), 1.9, 0.8, axis="x", mat="spine", sides=4)
    # 割れ目: 頭の中央の細い縦の線 (光る) — 水玉ではなく線
    f += block("gem", (0.0, 17.6, 0), (0.5, 5.0, 3.5), taper=(0.9, 0.9))
    # 柄頭の鎖
    f += chain((0.0, -6.8, 0.0), (1.4, -11.0, 0.0), 3, sag=0.4)
    if t >= 1:
        # ピーン: 頭の背 (+z 向きではなく上) の鉄の尖り
        f += spike(BONE, "edge", (4.0, 21.2, 0), (0.15, 1, 0), 3.4, 1.0, sides=4, rings=1, hint=(0, 0, 1))
        f += block("trim", (-7.6, 20.0, 0), (2.2, 0.8, 3.2))
        f += block("trim", (7.8, 21.4, 0), (2.2, 0.8, 3.4))
        f += block("trim", (-7.6, 14.6, 0), (2.2, 0.8, 3.2))
        f += block("trim", (7.8, 15.4, 0), (2.2, 0.8, 3.4))
    if t == 2:
        # 背びれの列 (頭の上に 3 本) と割れた背板、柄の中ほどの 2 本目の鎖と鉤
        f += ridge((-4.6, 19.6, 0.0), (0.0, 1, 0), 3, 3.4, (3.6, 0.6, 0.0), 1.0)
        f += at(extrude("main", [(0, 0), (3.0, 0.2), (3.6, 2.0), (0.4, 1.8)], 1.8), None, (-7.4, 19.4, 0))
        f += chain((0.0, 6.0, -1.4), (-3.2, 2.4, -1.4), 3, sag=0.5)
    return f


def inlay(env, kind):
    if kind == "rare":
        return spot_mask(env, ["spine"], (0, 14.4, 0), 1.2)
    if kind == "legend":
        return spot_mask(env, ["main"], (-4.0, 17.0, -1.7), 1.2) | spot_mask(env, ["main"], (4.6, 19.4, -1.7), 1.2)
    if kind == "p5":
        return (np.isin(env["mats"], ["edge"]) & env["convex"]) & (env["Y"] > 14) & (env["Y"] % 2.0 < 1.1)
    if kind == "p10":
        return (np.isin(env["mats"], ["edge"]) & env["convex"]) & (env["Y"] > 14)
    return env["lit"] < -9
