# -*- coding: utf-8 -*-
"""灰の打ち手 (ash_striker) の密度上げ。一品一案（取り込み 754 → 目標 1,326〜1,856）。

足したもの（骨と色は元のまま。body / head / arm_* / leg_* / hammer）:
- 胸と腹の皮の皺（灰の肌の筋。縦に三本）
- 肩紐の留め金（鉄。左右に二つずつ）と、前掛けの裾の縁（革の帯）
- 腕の腕当て（革の輪。前腕に二本ずつ）
- 握りこぶしの関節の鉄の鋲（各四個）
- 長靴の上の革の縁（二本）と、靴の爪先の鉄
- 頭巾の縁の飾り（革の帯の輪）
- 大槌の頭の鉄の鋲（四個）
"""
import math
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from dens import kit, add  # noqa: E402


def extra(o, F, eyes):
    ck = kit()
    # 胸と腹の皮の皺
    for j, x in enumerate((-1.4, 0.0, 1.4)):
        F = add(F, "wrinkle%d" % j, ck.tube("body", "skin", [(x, 19.0, -3.5), (x * 0.8, 15.0, -3.4), (x * 0.6, 13.6, -3.3)], ra=0.2, sides=4, hint=(0, 0, 1)))
    # 肩紐の留め金と前掛けの裾の縁
    for s in (-1, 1):
        for j, y in enumerate((23.0, 19.6)):
            F = add(F, "buckle%d%d" % (s, j), ck.ellipsoid("body", "iron", (s * 2.5, y, -3.4), (0.4, 0.38, 0.4), segs=5, rings=2))
    F = add(F, "apronhem", ck.loft("body", "leather", [ck.ring(5.0, 3.2, -3.4, -1.4, n=8), ck.ring(5.4, 3.2, -3.5, -1.3, n=8)], caps=(False, False)))
    # 前腕の腕当て（革の輪）
    for s, b in ((-1, "arm_l"), (1, "arm_r")):
        for j, (y, dz) in enumerate(((19.0, 0.0), (17.0, 0.0))):
            F = add(F, "bracer%d%d" % (s, j), ck.tube(b, "leather", [(s * 5.4, y, -1.4 + dz), (s * 5.3, y - 0.6, -2.4 + dz)], ra=[1.6, 1.6], sides=6, hint=(1, 0, 0)))
    # 握りこぶしの関節の鉄の鋲
    for s, b in ((-1, "arm_l"), (1, "arm_r")):
        for j, dx in enumerate((-0.7, -0.25, 0.25, 0.7)):
            F = add(F, "knuck%d%d" % (s, j), ck.ellipsoid(b, "iron", (s * 5.2 + dx, 16.9, -4.6), (0.22, 0.22, 0.22), segs=4, rings=2))
    # 長靴の上の革の縁と爪先の鉄
    for s, b in ((-1, "leg_l"), (1, "leg_r")):
        F = add(F, "bootcuff%d" % s, ck.tube(b, "leather", [(s * 1.8, 3.4, 0.1), (s * 2.0, 3.4, -0.9)], ra=[1.35, 1.35], sides=6, hint=(1, 0, 0)))
        F = add(F, "toecap%d" % s, ck.ellipsoid(b, "iron", (s * 2.0, 0.6, -2.4), (0.8, 0.45, 0.6), segs=5, rings=2))
    # 頭巾の縁の飾り（革の帯）
    F = add(F, "hoodband", ck.tube("head", "leather", [(math.cos(a) * 2.5, 25.9, math.sin(a) * 2.3 - 2.2) for a in [i / 10 * 2 * math.pi for i in range(11)]],
                                   ra=0.3, sides=4, hint=(0, 1, 0)))
    # 大槌の頭の鉄の鋲
    for j, x in enumerate((3.0, 4.6, 6.2, 7.6)):
        F = add(F, "hammerstud%d" % j, ck.ellipsoid("hammer", "iron", (x, 29.8, -7.6), (0.3, 0.3, 0.3), segs=5, rings=2))
    return F


if __name__ == "__main__":
    from dens import run_mob
    run_mob("ash_striker", "cloud/ash_striker.py", extra, letter="a")
