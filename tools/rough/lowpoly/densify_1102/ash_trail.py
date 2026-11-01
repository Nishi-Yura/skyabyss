# -*- coding: utf-8 -*-
"""灰の跡 (ash_trail) の密度上げ。採用案 C 手だけの這い（取り込み 380 → 目標 959〜1,343）。

足したもの（骨と色は元のまま。body / head / arm_* / prints）:
- 背の棘（basalt の錐。背の上に七本）と、体の横の灰のかたまり（ash。左右に三つずつ）
- 尾（basalt の細い筒。先が熾）
- 手の爪（char の錐。各手に二本ずつ足す）
- 頭の上の棘（char。三本）と、床の追加の足跡（dust。六つ）
"""
import math
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from dens import add  # noqa: E402


def extra(o, F, eyes):
    ck = o
    for j, z in enumerate((-2.0, -1.2, -0.4, 0.4, 1.2, 2.0, 2.8)):
        F = add(F, "spine%d" % j, ck.cone("body", "basalt", (0.0, 4.6, z), (0.0, 1.0, 0.25), 1.1, 0.26, sides=4, rings=1))
    for s in (-1, 1):
        for j, (y, z) in enumerate(((1.4, -1.6), (2.2, 0.0), (1.2, 1.8))):
            F = add(F, "clump%d%d" % (s, j), ck.ellipsoid("body", "ash", (s * 2.6, y, z), (0.62, 0.55, 0.62), segs=5, rings=2))
    F = add(F, "tail", ck.tube("body", "basalt", [(0.0, 2.6, 3.2), (0.0, 2.0, 4.8), (0.0, 1.5, 6.4)], ra=[0.6, 0.4, 0.1], sides=5, hint=(1, 0, 0)))
    F = add(F, "tailtip", ck.ellipsoid("body", "ember", (0.0, 1.5, 6.5), (0.22, 0.22, 0.22), segs=4, rings=1))
    for s, b in ((1, "arm_l"), (-1, "arm_r")):
        for j, dz in enumerate((-0.5, 0.5)):
            F = add(F, "claw%d%d" % (s, j), ck.cone(b, "char", (s * 6.2, 0.2, -6.0 + dz), (s * 0.5, 0.0, -0.4), 0.8, 0.16, sides=3, rings=1))
    for j, dx in enumerate((-0.5, 0.0, 0.5)):
        F = add(F, "crown%d" % j, ck.cone("head", "char", (dx * 0.6, 4.1, -3.6), (dx * 0.4, 1.0, -0.2), 0.9, 0.2, sides=3, rings=1))
    for j, (x, z) in enumerate(((-2.8, -5.8), (2.6, -5.0), (-2.2, 4.8), (2.4, 5.6), (-0.6, 3.6), (0.8, -7.6))):
        F = add(F, "dust%d" % j, ck.ellipsoid("prints", "dust", (x, 0.1, z), (0.6, 0.12, 0.8), segs=5, rings=1))
    # 肘の瘤と、手の先の爪（二本ずつ）、床の灰の粒
    for s, b in ((1, "arm_l"), (-1, "arm_r")):
        F = add(F, "elbowk%d" % s, ck.ellipsoid(b, "basalt", (s * 4.0, 1.2, -2.8), (0.5, 0.45, 0.5), segs=5, rings=2))
    for s, b in ((1, "arm_l"), (-1, "arm_r")):
        for j, dz in enumerate((-0.2, 0.2)):
            F = add(F, "armclaw%d%d" % (s, j), ck.cone(b, "char", (s * 6.6, 0.15, -6.2 + dz * 2.2), (s * 0.5, -0.1, -0.5), 0.6, 0.13, sides=3, rings=1))
    for j, (x, z) in enumerate(((-4.6, -1.8), (4.6, -1.4), (-4.4, 2.6), (4.4, 2.4), (-3.2, -6.4), (3.6, -6.8), (-4.8, 6.0), (4.8, 6.4), (-2.2, -8.4), (2.4, -9.0), (-1.6, 9.0), (1.8, 9.6), (5.4, 8.6), (-5.6, -9.8))):
        F = add(F, "grain%d" % j, ck.ellipsoid("prints", "ash", (x, 0.12, z), (0.45, 0.25, 0.45), segs=5, rings=2))
    F = add(F, "tailridge", ck.tube("body", "char", [(0.0, 2.6, 3.4), (0.0, 1.9, 4.6), (0.0, 1.4, 6.0)], ra=0.18, sides=4, hint=(1, 0, 0)))
    return F


if __name__ == "__main__":
    from dens import run_mob
    run_mob("ash_trail", "abyss/ash_trail.py", extra, letter="c")
