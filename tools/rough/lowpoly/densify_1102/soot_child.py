# -*- coding: utf-8 -*-
"""煤の子 (soot_child) の密度上げ。採用案 A 煤の団子（取り込み 742 → 目標 959〜1,343）。

足したもの（骨と色は元のまま。body / head / jaw / arm_* / leg_* / wisp）:
- 腹の下の煤の塊（char。下のふちに四つ）と、熾の筋（ember。腹の継ぎ目に二本）
- 目のふちの煤の輪（char の小さな楕円体。二つ）
- 肘の瘤（char。左右に一つずつ）と、足の爪（char の細い錐。各二本）
- 煙の尾の途中の煤のかたまり（soot。二つ）
"""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from dens import add  # noqa: E402


def extra(o, F, eyes):
    ck = o
    for j, (x, y, z) in enumerate(((-1.4, 0.4, 1.9), (0.2, 0.2, 2.2), (1.5, 0.5, 1.8), (-0.4, 0.6, -2.1))):
        F = add(F, "under%d" % j, ck.ellipsoid("body", "char", (x, y, z), (0.55, 0.42, 0.55), segs=6, rings=2))
    for j, x in enumerate((-1.1, 1.1)):
        F = add(F, "seam%d" % j, ck.tube("body", "ember", [(x * 0.9, 1.6, 2.35), (x * 0.6, 2.4, 2.15), (x * 0.3, 3.0, 1.6)], ra=0.16, sides=4, hint=(0, 0, 1)))
    for s in (-1, 1):
        F = add(F, "eyerim%d" % s, ck.ellipsoid("head", "char", (s * 0.8, 3.25, -1.95), (0.55, 0.5, 0.25), segs=7, rings=2))
    for s, b in ((1, "arm_l"), (-1, "arm_r")):
        F = add(F, "elbow%d" % s, ck.ellipsoid(b, "char", (s * 2.8, 2.25, -0.3), (0.38, 0.36, 0.38), segs=5, rings=2))
    for s, b in ((1, "leg_l"), (-1, "leg_r")):
        for j, dx in enumerate((-0.25, 0.25)):
            F = add(F, "legclaw%d%d" % (s, j), ck.cone(b, "char", (s * 1.2 + dx, 0.2, -1.4), (dx * 0.3, -0.2, -1.0), 0.6, 0.12, sides=3, rings=1))
    for j, (x, y, z) in enumerate(((0.1, 5.0, 2.2), (-0.1, 5.5, 3.0))):
        F = add(F, "wispsoot%d" % j, ck.ellipsoid("wisp", "soot", (x, y, z), (0.45, 0.42, 0.42), segs=5, rings=2))
    return F


if __name__ == "__main__":
    from dens import run_mob
    run_mob("soot_child", "abyss/soot_child.py", extra, letter="a")
