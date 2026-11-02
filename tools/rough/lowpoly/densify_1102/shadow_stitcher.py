# -*- coding: utf-8 -*-
"""影の綴じ手 (shadow_stitcher) の密度上げ。採用案 C 裂けた帯の影 (取り込み 1032 三角形 → 目標 1,330〜1,860)。

足したもの（骨・色は元のまま。骨は body / head / arm_l / arm_r）:
- 胴の襟 2 本（影の藍の襞で、帯の根元を巻く）
- 帯の先の垂れた切れ端（各帯の先に 2 本ずつ、影の色）
- 頭の輪の上の棘 5 本（ほどけた糸の端）
- 肘のこぶと手首の袖口（腕の骨ごと）
- 縫い目の星の糸を帯の途中に 1 本ずつ（光る材質。光る画素は増えるが面は少ない）
"""
import math
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from dens import kit, add  # noqa: E402


def extra(o, F, eyes):
    ck = kit()
    # 胴の襟（帯の根元を締める薄い輪）
    for j, y in enumerate((20.6, 29.6)):
        rings = [ck.ring(y, 3.5, -2.2, 2.2, n=10), ck.ring(y + 0.7, 3.6, -2.3, 2.3, n=10)]
        F = add(F, "collar%d" % j, ck.loft("body", "shade2", rings, caps=(False, False)))
    # 帯の先の垂れた切れ端（各帯の先に 2 本）
    for i in range(7):
        a = (i - 3) * 0.28
        x0 = math.sin(a) * 2.2
        bx = x0 * 2.2
        for s in (-1, 1):
            F = add(F, "tip%d%d" % (i, s), ck.spike("body", "shade2" if i % 2 == 0 else "shadow",
                                                   (bx + s * 0.55, 1.6, -0.4 + 0.15 * s), (s * 0.3, -1, 0.0), 2.6, 0.32, sides=4, rings=2))
    # 頭の輪の上の棘（ほどけた糸）
    for i in range(5):
        a = math.pi * 0.5 + (i - 2) * 0.55
        F = add(F, "crown%d" % i, ck.spike("head", "shadow", (math.cos(a) * 2.5, 35.6 + math.sin(a) * 2.5, 0.0),
                                           (math.cos(a), math.sin(a), 0.0), 1.6, 0.28, sides=4, rings=2))
    # 肘のこぶと手首の袖口
    for s, b in ((-1, "arm_l"), (1, "arm_r")):
        x = s * 4.2
        F = add(F, "elbow%d" % s, ck.ellipsoid(b, "shadow", (x + s * 1.0, 22.0, 0.4), (0.9, 0.9, 0.9), segs=6, rings=3))
        F = add(F, "cuff%d" % s, ck.loft(b, "shade2", [ck.ring(13.6, 0.95, -0.7, 0.7, n=8), ck.ring(14.2, 1.0, -0.75, 0.75, n=8)],
                                         caps=(False, False)))
    # 縫い目の星の糸を帯の途中に
    for i in (1, 3, 5):
        y = 14.0 - i * 0.6
        F = add(F, "bthr%d" % i, ck.tube("body", "thread", [(-0.2 + 0.4 * (i % 2), y, -1.9), (0.2 + 0.4 * (i % 2), y + 1.5, -1.9)],
                                         ra=0.16, sides=4, hint=(0, 0, 1)))
    return F


if __name__ == "__main__":
    from dens import run_mob
    run_mob("shadow_stitcher", "moon/shadow_stitcher.py", extra)
