# -*- coding: utf-8 -*-
"""静電の毛玉 (static_puff) の密度上げ。採用案 B 静電の羊毛（取り込み 402 → 目標 545〜763）。

足したもの（骨と色は元のまま。body / tuft_*）:
- 毛の房の表面の小さな毛束（wool。房ごとに二つ）
- 羊毛の塊の下の縁（down の小さな楕円体。底の周りに四つ）
- 目のふちの暗い輪（dark。二つ）
- 火花の棘（spark。房の先に三本）
"""
import sys
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).resolve().parent))
from dens import add  # noqa: E402


def extra(o, F, eyes):
    ck = o
    C0 = np.asarray(o.C0, float)
    # 房の表面の毛束（方向は房の骨ごとに一つ）
    dirs = [(0.6, 0.5, -0.6), (-0.6, 0.5, -0.6), (0.7, 0.4, 0.5), (-0.7, 0.4, 0.5), (0.0, 0.9, 0.0), (0.2, 0.8, -0.5)]
    for i, d in enumerate(dirs):
        d = np.asarray(d, float)
        d = d / np.linalg.norm(d)
        b = "body"
        c = C0 + d * 3.3
        F = add(F, "tuftlump%d" % i, ck.ellipsoid(b, "wool" if i % 2 else "down", tuple(float(v) for v in c), (1.0, 0.9, 1.0), segs=5, rings=2))
    # 底の縁
    for j, (x, z) in enumerate(((-2.8, -1.8), (2.8, -1.8), (-2.6, 2.2), (2.6, 2.2))):
        F = add(F, "rim%d" % j, ck.ellipsoid("body", "down", (x, C0[1] - 3.2, z), (0.9, 0.55, 0.9), segs=5, rings=2))
    # 目のふちの暗い輪
    for s in (-1, 1):
        F = add(F, "eyering%d" % s, ck.ellipsoid("body", "dark", (s * 1.3, 8.6, -3.78), (0.75, 0.82, 0.22), segs=6, rings=1))
    # 火花の棘（房の先）
    for j, d in enumerate(((0.9, 0.2, 0.2), (-0.9, 0.2, 0.2), (0.0, 0.2, -0.95))):
        d = np.asarray(d, float)
        base = C0 + d * 4.9
        F = add(F, "spark%d" % j, ck.spike("body", "spark", tuple(float(v) for v in base), tuple(float(v) for v in d), 1.1, 0.22, sides=3, rings=1))
    return F


if __name__ == "__main__":
    from dens import run_mob
    run_mob("static_puff", "ch3/static_puff.py", extra, letter="b")
