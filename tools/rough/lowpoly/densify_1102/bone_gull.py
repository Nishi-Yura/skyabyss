# -*- coding: utf-8 -*-
"""骨の鴎 (bone_gull) の密度上げ。採用案 A 骨の翼（取り込み 450 → 目標 728〜1,019）。

足したもの（骨と色は元のまま。body / head / jaw / wing_* / tail）:
- 翼の中の骨の筋（rim。左右に二本ずつ。前縁の筋と平行）
- 翼の後ろ縁の小さな骨の爪（bone。左右に四本ずつ。翼の面の後ろから）
- 胸の骨の板の肋（bone。胸の下に二本の弧）
- 首の骨の輪（bone の細い輪。頭と胴の付け根に一つ）
- 尾の付け根の骨の板（rim。左右に一枚ずつ）
"""
import math
import sys
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).resolve().parent))
from dens import add  # noqa: E402


def extra(o, F, eyes):
    ck = o
    for s in (-1, 1):
        nm = "wing_l" if s < 0 else "wing_r"
        for j, (z, dz) in enumerate(((-1.4, 0.0), (1.0, 0.2))):
            F = add(F, "strut2_%s%d" % (nm, j), ck.tube(nm, "rim", [(1.6 * s + 2.0 * s, 3.5, -0.2 + z), (1.6 * s + 6.0 * s, 3.2 + dz, -0.2 + z * 0.8),
                                                                  (1.6 * s + 8.0 * s, 2.7, -0.4 + z * 0.6)], ra=[0.22, 0.2, 0.14], sides=4, hint=(0, 0, 1)))
        for j, t in enumerate((0.25, 0.42, 0.6, 0.78)):
            base_ = np.array([1.6 * s + 9.0 * s * t, 3.0 - 0.4 - 0.5 * t, -0.5 + 2.2 * t * t])
            F = add(F, "trail_%s%d" % (nm, j), ck.spike(nm, "bone", tuple(float(v) for v in base_), (0.0, 0.3, 1.0), 1.6, 0.3, sides=3, rings=1))
    for j, x in enumerate((-1.0, 1.0)):
        F = add(F, "breastrib%d" % j, ck.tube("body", "bone", [(x * 1.3, 0.8, -1.4), (x * 1.6, 0.4, 0.2), (x * 1.1, 0.4, 1.8)], ra=0.22, sides=4, hint=(0, 0, 1)))
    F = add(F, "neckring", ck.tube("head", "bone", [(math.cos(a) * 1.3, 3.2 + math.sin(a) * 1.2, -2.8) for a in [i / 8 * 2 * math.pi for i in range(9)]],
                                   ra=0.22, sides=3, hint=(0, 1, 0)))
    for s in (-1, 1):
        F = add(F, "tailplate%d" % s, ck.ellipsoid("tail", "rim", (s * 1.2, 2.1, 2.2), (0.7, 0.5, 0.9), segs=5, rings=2))
    return F


if __name__ == "__main__":
    from dens import run_mob
    run_mob("bone_gull", "ch1/bone_gull.py", extra, letter="a")
