# -*- coding: utf-8 -*-
"""天井の這い人 (ceiling_walker) の密度上げ。採用案 A 逆さの人（取り込み 412 → 目標 959〜1,343）。

足したもの（骨と色は元のまま。body / head / arm_* / leg_*）:
- 両手の指（slate の錐。各手に三本、垂れる向き）
- 足の先の爪（天井に吸い付く向き。各足に三本）
- 背の瘤（slate_b。背に五つ）と、肋の輪（slate の細い輪。二段）
- 肩の瘤（void。左右）と、首の輪（胴と頭の境）
"""
import math
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from dens import add  # noqa: E402


def extra(o, F, eyes):
    ck = o
    for s, b in ((1, "arm_l"), (-1, "arm_r")):
        for j, dx in enumerate((-0.45, 0.0, 0.45)):
            F = add(F, "finger%d%d" % (s, j), ck.cone(b, "slate", (s * 4.2 + dx, 8.4, -1.0), (dx * 0.4, -1.0, -0.2), 1.3, 0.22, sides=3, rings=1))
    for s, b in ((1, "leg_l"), (-1, "leg_r")):
        for j, dz in enumerate((-0.5, 0.0, 0.5)):
            F = add(F, "toe%d%d" % (s, j), ck.cone(b, "slate_b", (s * 2.2 + dz * 0.2, o.CEIL - 1.2, 0.2 * 1.2 + dz), (0.0, 1.0, dz * 0.3), 0.9, 0.18, sides=3, rings=1))
    for j, y in enumerate((8.0, 10.6, 13.2, 15.8, 18.4)):
        F = add(F, "bump%d" % j, ck.ellipsoid("body", "slate_b", (0.0, y, 1.5), (0.5, 0.5, 0.5), segs=6, rings=2))
    for j, y in enumerate((10.0, 16.0)):
        F = add(F, "rib%d" % j, ck.tube("body", "slate", [(math.cos(a) * 2.9, y, math.sin(a) * 1.7) for a in [i / 10 * 2 * math.pi for i in range(11)]],
                                        ra=0.24, sides=4, hint=(0, 1, 0)))
    for s in (-1, 1):
        F = add(F, "shoulder%d" % s, ck.ellipsoid("body", "void", (s * 3.4, 20.2, 0.0), (1.2, 1.1, 1.2), segs=6, rings=2))
    F = add(F, "neckring", ck.tube("head", "slate_b", [(math.cos(a) * 1.5, 5.9, math.sin(a) * 1.5) for a in [i / 8 * 2 * math.pi for i in range(9)]],
                                   ra=0.28, sides=4, hint=(0, 1, 0)))
    for j, y in enumerate((7.0, 9.5, 12.0, 14.5, 17.0, 19.5)):
        F = add(F, "spineb%d" % j, ck.cone("body", "slate", (0.0, y, -1.5), (0.0, 0.2, -1.0), 0.9, 0.25, sides=4, rings=1))
    for s, b in ((1, "arm_l"), (-1, "arm_r")):
        F = add(F, "elbowc%d" % s, ck.ellipsoid(b, "slate_b", (s * 4.9, 15.0, -0.6), (0.6, 0.6, 0.6), segs=5, rings=2))
    F = add(F, "ribc", ck.tube("body", "slate_b", [(math.cos(a) * 2.5, 13.0, math.sin(a) * 2.5 * 0.6) for a in [i / 10 * 2 * math.pi for i in range(11)]],
                               ra=0.22, sides=4, hint=(0, 1, 0)))
    return F


if __name__ == "__main__":
    from dens import run_mob
    run_mob("ceiling_walker", "abyss/ceiling_walker.py", extra, letter="a")
