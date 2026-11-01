# -*- coding: utf-8 -*-
"""岩の跳び虫 (moon_leaper) の密度上げ。採用案 A（取り込み 700 → 目標 1,330〜1,860）。

足したもの（骨と色は元のまま。body / head / leg_* / arm_*）:
- 胴の節の板の上の棘（銀。各節の背に一本）
- 後ろ脚の膝の瘤（玄武岩）と、足の先の爪（銀。各三本）
- 鎌の前腕の付け根の瘤と、鎌の刃の小さな棘
- 頭の鉢の棘（凝灰岩。三本）と、触角の根元の瘤
- 腹の側面の縁の筋（二本ずつ）
"""
import math
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from dens import kit, add  # noqa: E402


def extra(o, F, eyes):
    ck = kit()
    # 節の板の背の棘
    for i, z in enumerate((-3.0, -0.6, 1.8, 4.0)):
        F = add(F, "platespike%d" % i, ck.spike("body", "silver", (0, 11.0 - 0.1 * i, z), (0, 1, 0), 1.6, 0.36, sides=4, rings=2))
    # 後ろ脚の膝の瘤と足の爪
    for s in (-1, 1):
        x = s * 3.0
        F = add(F, "knee%d" % s, ck.ellipsoid("body", "basalt", (x + s * 1.6, 13.0, 5.5), (0.8, 0.7, 0.8), segs=6, rings=2))
        for j, dx in enumerate((-0.35, 0.0, 0.35)):
            F = add(F, "claw%d%d" % (s, j), ck.spike("body", "silver", (x + s * 2.4 + dx, 0.5, 7.2), (0, -0.4, 1), 0.9, 0.18, sides=4, rings=1))
    # 鎌の前腕の付け根の瘤と刃の棘
    for s in (-1, 1):
        b = "arm_l" if s < 0 else "arm_r"
        F = add(F, "armknot%d" % s, ck.ellipsoid(b, "basalt", (s * 2.8, 6.8, -4.0), (0.7, 0.6, 0.7), segs=6, rings=2))
        F = add(F, "bladespike%d" % s, ck.spike(b, "silver", (s * 3.2, 4.2, -6.2), (0, -0.2, -1), 1.0, 0.22, sides=4, rings=1))
    # 頭の鉢の棘と触角の根元の瘤
    for j, (x, z) in enumerate(((-0.9, -5.6), (0.0, -6.2), (0.9, -5.6))):
        F = add(F, "crest%d" % j, ck.spike("head", "tuff", (x, 11.2, z), (x * 0.3, 1, -0.2), 1.3, 0.34, sides=4, rings=2))
    for s in (-1, 1):
        F = add(F, "antk%d" % s, ck.ellipsoid("head", "basalt", (s * 0.8, 11.2, -6.6), (0.4, 0.35, 0.4), segs=5, rings=2))
    # 胴の締め輪（胸と腹の境、腹の中ほど）
    for j, (z, rx, ry) in enumerate(((-1.5, 2.9, 2.5), (2.0, 2.6, 2.2), (4.6, 2.5, 2.0))):
        pts = [(math.cos(a) * rx, 8.4 + math.sin(a) * ry, z) for a in [i / 8 * 2 * math.pi for i in range(9)]]
        F = add(F, "belt%d" % j, ck.tube("body", "tuff" if j != 1 else "silver", pts, ra=0.3, sides=4, hint=(0, 1, 0)))
    # 腹の背の棘（銀）
    for j, z in enumerate((2.0, 4.4)):
        F = add(F, "absp%d" % j, ck.spike("body", "silver", (0, 10.8, z), (0, 1, 0), 1.2, 0.3, sides=4, rings=1))
    # 足首の瘤
    for s in (-1, 1):
        F = add(F, "ankle%d" % s, ck.ellipsoid("body", "basalt", (s * 3.0 + s * 2.2, 1.6, 2.0), (0.6, 0.5, 0.6), segs=6, rings=2))
    # 腹の側面の縁の筋
    for s in (-1, 1):
        for j, (y, z) in enumerate(((7.8, 1.2), (6.8, 3.0))):
            F = add(F, "edge%d%d" % (s, j), ck.tube("body", "tuff", [(s * 2.2, y, z - 1.0), (s * 2.8, y - 0.6, z + 0.5)], ra=0.22, sides=4, hint=(0, 1, 0)))
    return F


if __name__ == "__main__":
    from dens import run_mob
    run_mob("moon_leaper", "moon/moon_leaper.py", extra, letter="a")
