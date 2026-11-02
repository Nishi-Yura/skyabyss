# -*- coding: utf-8 -*-
"""過去の影 (past_shadow) の密度上げ。採用案 C 濡れた影（取り込み 442 → 目標 959〜1,343）。

足したもの（骨と色は元のまま。body / head / arm_* / leg_*）:
- 肩の瘤（void。左右の腕の付け根）と、腰の帯（slate の細い輪）
- 両手の指（void の錐。各手に三本）と、足の先の裏（void の楕円体）
- 頭の上の髪の筋（void の細い錐。六本、後ろへ流す）
- 胸の肋の筋（slate の細い弧。三本）
"""
import math
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from dens import add  # noqa: E402


def extra(o, F, eyes):
    ck = o
    for s in (-1, 1):
        F = add(F, "shoulder%d" % s, ck.ellipsoid("body", "void", (s * 3.2, 20.6, 0.0), (1.3, 1.2, 1.3), segs=6, rings=2))
    F = add(F, "waist", ck.tube("body", "slate", [(math.cos(a) * 2.8, 16.8, math.sin(a) * 1.3) for a in [i / 10 * 2 * math.pi for i in range(11)]],
                                ra=0.3, sides=4, hint=(0, 1, 0)))
    for j, y in enumerate((19.5, 15.0)):
        F = add(F, "rib%d" % j, ck.tube("body", "slate", [(-2.2, y, -1.6), (0.0, y + 0.4, -1.9), (2.2, y, -1.6)], ra=0.22, sides=4, hint=(0, 0, 1)))
    for s, b in ((1, "arm_l"), (-1, "arm_r")):
        ex = s * 5.4 * 1.35
        for j, dx in enumerate((-0.4, 0.0, 0.4)):
            F = add(F, "finger%d%d" % (s, j), ck.cone(b, "void", (ex + dx, 11.0, -0.6), (dx * 0.4, -1.0, -0.3), 1.2, 0.22, sides=3, rings=1))
    for s, b in ((1, "leg_l"), (-1, "leg_r")):
        F = add(F, "sole%d" % s, ck.ellipsoid(b, "void", (s * 1.8, 0.4, -0.7), (0.9, 0.45, 1.0), segs=6, rings=2))
    for j, dx in enumerate((-1.2, -0.7, -0.2, 0.3, 0.8, 1.2)):
        F = add(F, "hair%d" % j, ck.cone("head", "void", (dx, 26.4, 0.2), (dx * 0.3, 0.6, 1.0), 2.2, 0.17, sides=3, rings=1))
    # 裾の破れ（下向きの錐、六本）と、手首・膝の輪
    for j in range(12):
        a = j / 12 * 2 * math.pi + 0.3
        F = add(F, "hemtear%d" % j, ck.cone("body", "void", (math.cos(a) * 2.2, 12.6, math.sin(a) * 1.2), (math.cos(a) * 0.3, -1.0, math.sin(a) * 0.2), 1.6, 0.26, sides=3, rings=1))
    for s, b in ((1, "arm_l"), (-1, "arm_r")):
        F = add(F, "cuff%d" % s, ck.tube(b, "slate", [(s * 5.2 * 1.35, 12.2, -0.6), (s * 5.4 * 1.35, 11.4, -0.6)], ra=[0.55, 0.5], sides=5, hint=(1, 0, 0)))
    for s, b in ((1, "leg_l"), (-1, "leg_r")):
        F = add(F, "legring%d" % s, ck.tube(b, "slate", [(s * 1.7, 9.0, -0.2), (s * 1.8, 9.2, 0.2)], ra=[1.5, 1.5], sides=6, hint=(0, 1, 0)))
    F = add(F, "neckband", ck.tube("head", "slate", [(math.cos(a) * 1.3, 21.9, math.sin(a) * 1.3) for a in [i / 8 * 2 * math.pi for i in range(9)]],
                                   ra=0.25, sides=4, hint=(0, 1, 0)))
    for j, dx in enumerate((-0.9, 0.9, -1.6, 1.6, -2.1, 2.1, -0.3, 0.3)):
        F = add(F, "hairb%d" % j, ck.cone("head", "void", (dx * 0.5, 26.2, 0.6), (dx * 0.3, 0.5, 1.0), 1.8, 0.14, sides=3, rings=1))
    return F


if __name__ == "__main__":
    from dens import run_mob
    run_mob("past_shadow", "abyss/past_shadow.py", extra, letter="c")
