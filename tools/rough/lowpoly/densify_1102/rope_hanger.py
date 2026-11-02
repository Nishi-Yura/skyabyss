# -*- coding: utf-8 -*-
"""縄の吊り人 (rope_hanger) の密度上げ。採用案 B 縄の束の人（取り込み 652 → 目標 959〜1,343）。

足したもの（骨と色は元のまま。figure / head / arm_* / leg_* / rope / wrap）:
- 胴の縄の輪（rope。胴に三本。胴の外へ少し出す）
- 首の輪の結び目（rope。首の付け根に一つ。縄の付け根へつながる）
- 手の指（slate の細い錐。各手に三本）と、足の先の靴（void の楕円体）
- 頭の上の縄の結び目（rope の小さな球）
"""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from dens import add  # noqa: E402


def extra(o, F, eyes):
    ck = o
    import math
    for j, y in enumerate((7.0, 12.0, 17.0)):
        pts = [(3.0 * math.cos(a), y, 2.0 * math.sin(a)) for a in [i / 10 * 2 * math.pi for i in range(11)]]
        F = add(F, "bandrope%d" % j, ck.tube("figure", "rope", pts, ra=0.3, sides=4, hint=(0, 1, 0)))
    F = add(F, "neckknot", ck.ellipsoid("figure", "rope", (0.0, 22.0, 0.0), (1.1, 0.9, 1.1), segs=6, rings=2))
    F = add(F, "headknot", ck.ellipsoid("rope", "rope", (0.0, 27.0, 0.0), (0.7, 0.7, 0.7), segs=5, rings=2))
    for s, b in ((1, "arm_l"), (-1, "arm_r")):
        for j, dx in enumerate((-0.35, 0.0, 0.35)):
            F = add(F, "finger%d%d" % (s, j), ck.cone(b, "slate", (s * 3.8 + dx, 9.2, -0.8), (dx * 0.3, -1.0, -0.2), 1.0, 0.2, sides=3, rings=1))
    for s, b in ((1, "leg_l"), (-1, "leg_r")):
        F = add(F, "shoe%d" % s, ck.ellipsoid(b, "void", (s * 1.8, 0.6, -0.8), (0.9, 0.6, 1.0), segs=6, rings=2))
    return F


if __name__ == "__main__":
    from dens import run_mob
    run_mob("rope_hanger", "abyss/rope_hanger.py", extra, letter="b")
