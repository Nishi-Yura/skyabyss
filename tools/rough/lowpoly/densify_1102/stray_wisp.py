# -*- coding: utf-8 -*-
"""迷い火 (stray_wisp) の密度上げ。ラフの唯一の案（取り込み 324 → 目標 989〜1,385）。

足したもの（骨と色は元のまま。core / cage / tail / mote_a / mote_b）:
- 灯籠の肋を八本増やす（iron。傘の縁から下の輪へ。元の三本のあいだ）
- 傘の縁の鉄の爪（iron の錐。六本）と、肋の交点の留め金（iron の小さな楕円体。六個）
- 核のまわりの光の輪（glow_blue の細い輪。二段）
- 霧の尾を四筋増やす（mist。元の尾のあいだ）
- めぐる光の粒を十二に増やし、核から外へ光の棘を八本出す（glow_blue）
"""
import math
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from dens import add  # noqa: E402


def extra(o, F, eyes):
    ck = o
    # 灯籠の肋（元の三本のあいだ）
    for i, a in enumerate((30, 150, 270, 0, 60, 120, 240, 300)):
        ca, sa = math.cos(math.radians(a)), math.sin(math.radians(a))
        F = add(F, "xrib%d" % i, ck.tube("cage", "iron", [(2.7 * ca, 21.2, 2.7 * sa), (3.1 * ca, 18.0, 3.1 * sa), (2.5 * ca, 14.8, 2.5 * sa)],
                                         ra=[0.22, 0.22, 0.16], sides=4, hint=(0, 1, 0)))
    # 傘の縁の鉄の爪
    for j in range(6):
        a = math.radians(j * 60 + 30)
        F = add(F, "claw%d" % j, ck.spike("cage", "iron", (2.8 * math.cos(a), 21.2, 2.8 * math.sin(a)), (0.4 * math.cos(a), 0.7, 0.4 * math.sin(a)), 1.2, 0.2, sides=4, rings=1))
    # 肋の交点の留め金
    for j in range(6):
        a = math.radians(j * 60)
        F = add(F, "clasp%d" % j, ck.ellipsoid("cage", "iron", (2.9 * math.cos(a), 17.6, 2.9 * math.sin(a)), (0.3, 0.3, 0.3), segs=4, rings=1))
    # 核のまわりの光の輪
    for j, y in enumerate((17.4, 18.6)):
        pts = [(math.cos(a) * 3.2, y, math.sin(a) * 3.2) for a in [i / 10 * 2 * math.pi for i in range(11)]]
        F = add(F, "halo%d" % j, ck.tube("core", "glow_blue", pts, ra=0.14, sides=3, hint=(0, 1, 0)))
    # 霧の尾の増し（元の尾のあいだ）
    for i, (dx, dz, L) in enumerate(((-0.5, 0.0, 6.2), (0.5, 0.2, 6.0), (1.8, 0.2, 3.8), (-1.8, 0.4, 3.4), (0.9, -0.6, 5.2), (-0.9, -0.8, 4.8), (2.6, -0.4, 2.4), (-2.6, 0.2, 2.2))):
        F = add(F, "mist%d" % i, ck.tube("tail", "mist", [(dx, 15.6, dz), (dx * 1.3, 12.6, dz * 1.2), (dx * 0.7, 15.6 - L, dz * 1.5)], ra=[0.7, 0.4, 0.08], sides=4, hint=(1, 0, 0)))
    # めぐる光の粒（核から一定の半径）
    for j in range(12):
        a = j / 12 * 2 * math.pi + 0.4
        y = 18.0 + (1.4 if j % 2 else -1.2)
        F = add(F, "mote%d" % j, ck.ellipsoid("core", "glow_blue", (math.cos(a) * 4.4, y, math.sin(a) * 4.4), (0.34, 0.34, 0.34), segs=4, rings=1))
    # 核から外へ出る光の棘（八本）
    for j in range(8):
        a = j / 8 * 2 * math.pi + 0.2
        d = (math.cos(a) * 0.8, 0.3 if j % 2 else -0.2, math.sin(a) * 0.8)
        F = add(F, "spark%d" % j, ck.spike("core", "glow_blue", (math.cos(a) * 2.2, 18.0, math.sin(a) * 2.2), d, 1.0, 0.18, sides=3, rings=1))
    return F


if __name__ == "__main__":
    from dens import run_mob
    run_mob("stray_wisp", "foes/stray_wisp.py", extra, letter="a")
