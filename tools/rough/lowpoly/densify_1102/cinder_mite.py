# -*- coding: utf-8 -*-
"""熾の粒虫 (cinder_mite) の密度上げ。採用案 A（取り込み 278 → 目標 583〜816）。

足したもの（骨と色は元のまま。body / head / tail / leg_*）:
- 背の炭の板（basalt の薄い楕円体。背の上に三枚。ひびの両脇）
- 背の熾のひびの縁（char の細い筒。左右に二本ずつ）
- 脚の関節の瘤（leg。六本の脚の付け根に一つずつ）
- 脚の先の爪（leg の錐。各脚に二本）
- 触角のひげ（leg の細い筒。頭の両脇に二本）
"""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from dens import add  # noqa: E402


def extra(o, F, eyes):
    ck = o
    for j, x in enumerate((-1.2, 0.0, 1.2, -0.6, 0.6)):
        F = add(F, "plate%d" % j, ck.ellipsoid("body", "basalt", (x * 0.8, 3.1, -0.9 + 0.6 * j), (0.6, 0.25, 0.7), segs=6, rings=2))
    for s in (-1, 1):
        F = add(F, "craclip%d" % s, ck.tube("body", "char", [(s * 1.2, 3.0, -1.2), (s * 1.4, 3.1, 0.0), (s * 1.1, 2.9, 1.5)], ra=0.16, sides=3, hint=(0, 0, 1)))
        F = add(F, "craclip2%d" % s, ck.tube("body", "char", [(s * 0.6, 3.2, -0.6), (s * 0.9, 3.3, 0.6)], ra=0.14, sides=3, hint=(0, 0, 1)))
    for nm, s, i in o.LEGS:
        F = add(F, "knee_%s%d" % (nm, i), ck.ellipsoid("body", "leg", (s * 2.5, 1.2, o.LEG_Z[i]), (0.3, 0.3, 0.3), segs=4, rings=1))
        for j, dz in enumerate((-0.2, 0.2)):
            F = add(F, "legclaw_%s%d_%d" % (nm, i, j), ck.spike("body", "leg", (s * 3.0, 0.2, o.LEG_Z[i] + 0.3 + dz), (s * 0.3, -0.7, dz), 0.6, 0.12, sides=3, rings=1))
    for s in (-1, 1):
        F = add(F, "feeler%d" % s, ck.tube("head", "leg", [(s * 0.4, 1.8, -3.2), (s * 0.9, 1.5, -4.2), (s * 1.0, 1.2, -5.2)], ra=[0.08, 0.06, 0.03], sides=3, hint=(0, 1, 0)))
    return F


if __name__ == "__main__":
    from dens import run_mob
    run_mob("cinder_mite", "ch4/cinder_mite.py", extra, letter="a")
