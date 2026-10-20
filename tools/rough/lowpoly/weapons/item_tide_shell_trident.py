"""潮殻の三叉槍 (作り直し)。祭具の優雅さ (真珠層の杯の首・曲線の脇の穂・薔薇色の帯・真珠) + 使い込み (藤壺・海藻の巻き・欠け)。
中央の穂を長く、左右の穂は短く太く外へ反って三叉に読ませる。握り y 3 (変えない)。y -3..23。
階級: 通常 = 三叉 + 帯 1 本 / レア = 穂の返し + 貝の扇 + 真珠 + 帯 2 本 / 伝説 = 外へ巻く珊瑚の枝の冠 + 後ろの枝 + 真珠 3 つ。"""
from __future__ import annotations

from td_kit import *  # noqa: F401,F403
from td_kit import sash, pearl, barnacles, kelp_wrap, scallop
from paint import line_mask, spot_mask

TITAN = "tide"
GRIP = (0.0, 3.0, 0.0)
NAME = "潮殻の三叉槍"
ID = "tide_shell_trident"
EDGE = ("main",)


def _cx0(y):
    return 0.0 * y


def prong(s, x0, x1, y0, y1, w, mat="main", tf=0.9):
    """反った脇の穂 (葉の形): 根元 x0 から先 x1 へ弧を描いて上へ。"""
    return blade(mat, [(y0, -w, w, tf, tf, s * x0), (y0 + (y1 - y0) * 0.38, -w * 1.1, w * 1.1, tf, tf, s * (x0 + (x1 - x0) * 1.0)),
                       (y1, 0, 0, 0, 0, s * (x1 - 0.5))])


def faces(r="common"):
    t = {"common": 0, "rare": 1, "legend": 2}[r]
    f = []
    # 柄: 濡れた石、海藻の巻き、石突きの巻貝
    f += prism("guard", (0, -2.2, 0), (0, 4.8, 0), 1.05, 1.05, sides=6, caps=(False, False))
    f += prism("guard", (0, 4.8, 0), (0, 11.2, 0), 1.05, 1.05, sides=6, caps=(False, False))
    f += spike(BONE, "main", (0, -2.2, 0), (0, -1, 0), 2.6, 1.3, sides=6, rings=1, hint=(1, 0, 0))
    f += kelp_wrap(0.6, 4.2, 1.2, bands=2)
    f += kelp_wrap(6.4, 10.4, 1.2, bands=2 if t < 2 else 1)
    f += barnacles((0.5, 5.8, -1.0), 3, 0.5, 0.4, 0.8)
    # 杯の首: 真珠層の貝殻の台 (上へ開く)
    f += prism("main", (0, 11.0, 0), (0, 13.4, 0), 1.5, 2.8, sides=6, caps=(False, True), phase=0)
    f += pearl((0, 12.6, -2.5), 0.95)
    # 中央の穂: 長く、細身で反りのある葉
    f += blade("spine", [(13.2, -1.7, 1.7, 1.0, 1.0), (17.4, -2.1, 2.1, 1.0, 1.0), (23.0, 0, 0, 0, 0)],
               colmats=["edge", "spine", "edge", "edge", "spine", "edge"])
    # 脇の穂: 短く太い葉、外へ開いて上へ反る (三叉)
    for s in (1, -1):
        f += prong(s, 2.4, 5.8, 13.0, 20.4, 1.25)
    # 帯: 首から垂れる薔薇色の布 (幅広、柄に沿ってゆるく波打つ)
    f += sash([(2.2, 11.4, -1.2), (3.6, 8.4, -1.8), (2.4, 4.4, -1.2)], 1.7, th=0.2, taper=0.45)
    if t >= 1:
        f += sash([(-2.2, 11.4, 1.0), (-3.6, 8.0, 1.6), (-2.6, 3.0, 1.0)], 1.5, th=0.2, taper=0.45)
        # 穂の返し (中央の穂の根元の左右)
        for s in (1, -1):
            f += spike(BONE, "edge", (s * 1.6, 15.8, 0), (s * 0.9, 0.5, 0), 3.2, 0.8, sides=4, rings=1, hint=(0, 0, 1))
        # 首の後ろへ開く貝の扇
        f += scallop((0, 12.6), 3, 4.4, 40, 1.6, th=0.7, mat="edge", z=1.8)
        f += block("trim", (0, 2.2, 0), (3.0, 1.0, 3.0))
        f += block("trim", (0, 11.0, 0), (3.4, 0.6, 3.4))
    if t == 2:
        # 外側の小さな 2 本の穂 (五叉の冠) と、首の後ろの珊瑚の枝
        for s in (1, -1):
            f += prong(s, 4.6, 8.4, 12.4, 17.6, 1.1, mat="acc", tf=0.8)
            f += pearl((s * 3.7, 12.6, -1.6), 0.7)
        f += spike(BONE, "acc", (0, 12.8, 2.0), (0, 0.8, 0.8), 6.4, 0.9, sides=4, rings=1, hint=(1, 0, 0))
        f += sash([(0.8, 11.0, 1.8), (0.2, 6.6, 2.6), (1.0, 1.0, 1.8)], 1.7, th=0.22, taper=0.45)
    return f


def inlay(env, kind):
    if kind == "rare":
        return line_mask(env, ["main"], _cx0, 14.2, 16.8, 0.45)
    if kind == "legend":
        return line_mask(env, ["main"], _cx0, 14.2, 20.4, 0.45) | spot_mask(env, ["gem"], (0, 12.8, -2.5), 1.4)
    if kind == "p5":
        return line_mask(env, ["main"], _cx0, 14.0, 21.2, 0.45, dash=(2.2, 1.2))
    if kind == "p10":
        return line_mask(env, ["main"], _cx0, 13.6, 22.0, 0.55) | spot_mask(env, ["acc"], (5.5, 17.0, 0), 1.6) | spot_mask(env, ["acc"], (-5.5, 17.0, 0), 1.6)
    return env["lit"] < -9
