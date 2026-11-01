# -*- coding: utf-8 -*-
"""岩背負いの蟹 (cliff_creeper) の密度上げ。採用案 B（取り込み 780 → 目標 1,330〜1,860）。

足したもの（骨と色は元のまま。body / head / arm_* / leg_* / rock）:
- 殻の上の瘤（玄武岩。三列）と、殻の横の縁の鋲（凝灰岩）
- 殻の背に弓なりの筋（凝灰岩の細い帯。二本）
- 鋏の関節の瘤と、爪の節の棘（銀）
- 脚の関節（腿と脛の境の瘤。六本）
- 背の岩に、欠けた小さな岩を二つ（凝灰岩・月の砂）
- 目の柄の根元の瘤
"""
import math
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from dens import kit, add  # noqa: E402


def extra(o, F, eyes):
    ck = kit()
    # 殻の上の瘤（三列）
    for j, (x, z) in enumerate(((-1.8, -1.0), (0.0, -1.2), (1.8, -1.0), (-2.2, 1.2), (-0.9, 1.4), (0.9, 1.4), (2.2, 1.2))):
        top = 4.0 + 2.4 * math.sqrt(max(0.0, 1 - (x / 4.2) ** 2 - (z / 3.6) ** 2))   # 殻の表面の高さ（楕円体）
        F = add(F, "bump%d" % j, ck.ellipsoid("body", "tuff", (x, top - 0.1, z), (0.6, 0.45, 0.6), segs=6, rings=2))
    # 縁の鋲
    for s in (-1, 1):
        for j, z in enumerate((-2.4, -0.4, 1.6)):
            F = add(F, "stud%d%d" % (s, j), ck.ellipsoid("body", "tuff", (s * 3.6, 3.4, z - 0.6), (0.38, 0.36, 0.38), segs=5, rings=2))
    # 殻の背の弓なりの筋（凝灰岩）
    for j, x in enumerate((-1.6, 1.6)):
        F = add(F, "ridgeb%d" % j, ck.tube("body", "tuff", [(x, 5.0, -3.0), (x * 0.7, 5.6, -0.6), (x * 0.7, 5.6, 2.2), (x, 5.0, 3.6)], ra=0.3, sides=4, hint=(0, 1, 0)))
    # 鋏の関節の瘤と爪の節の棘
    for s, b in ((-1, "arm_l"), (1, "arm_r")):
        F = add(F, "wristk%d" % s, ck.ellipsoid(b, "basalt", (s * 4.6, 4.4, -5.6), (0.8, 0.7, 0.8), segs=6, rings=2))
        F = add(F, "clawsp%d" % s, ck.spike(b, "silver", (s * 4.8, 4.8, -7.0), (s * 0.3, 0.6, -0.7), 1.0, 0.3, sides=4, rings=1))
    # 脚の関節の瘤（腿と脛の境、六本）
    for s in (-1, 1):
        for j, z in enumerate((2.6, 0.6, -1.2)):
            b = "leg_l" if s < 0 else "leg_r"
            if j == 0:
                F = add(F, "kneeb%d%d" % (s, j), ck.ellipsoid(b, "basalt", (s * 6.0, 4.6, z * 1.2), (0.5, 0.45, 0.5), segs=6, rings=2))
            else:
                F = add(F, "kneeb%d%d" % (s, j), ck.ellipsoid("body", "basalt", (s * 6.0, 4.6, z * 1.2), (0.5, 0.45, 0.5), segs=6, rings=2))
    # 背の岩に欠けた小さな岩
    F = add(F, "chip1", ck.ellipsoid("rock", "tuff", (-1.8, 8.4, 0.2), (0.9, 0.7, 0.8), segs=6, rings=3))
    F = add(F, "chip2", ck.ellipsoid("rock", "dust", (1.9, 7.6, -0.9), (0.7, 0.5, 0.7), segs=5, rings=2))
    # 目の柄の根元の瘤
    for s in (-1, 1):
        F = add(F, "stalkk%d" % s, ck.ellipsoid("head", "tuff", (s * 1.0, 4.5, -4.0), (0.35, 0.35, 0.35), segs=5, rings=2))
    return F


if __name__ == "__main__":
    from dens import run_mob
    run_mob("cliff_creeper", "moon/cliff_creeper.py", extra, letter="b")
