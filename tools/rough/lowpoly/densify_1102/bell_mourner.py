# -*- coding: utf-8 -*-
"""鐘の亡者 (bell_mourner) の密度上げ。一品一案（取り込み 1,002 → 目標 1,326〜1,856）。

足したもの（骨と色は元のまま。body / head / arm_* / leg_* / bell）:
- 外套の裾の縁飾り（腰の上の帯の下に、もう一本の輪）と、外套の前の襞（縦の筋四本）
- 頭巾の縁の輪（頭巾の付け根）と、頬の骨の張り（漆喰の色）
- 腕の肘の瘤と、手の指の爪（空ろの色。各三本）
- 鐘の縁の鋲（青銅。前の下縁に四個）と、鐘の胴の緑青の帯（もう一本）
"""
import math
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from dens import kit, add  # noqa: E402


def extra(o, F, eyes):
    ck = kit()
    # 外套の縁飾り（二段目の帯）
    F = add(F, "hemtrim", ck.tube("body", "robe", [(math.cos(a) * 3.5, 20.0, math.sin(a) * 3.0 - 0.6) for a in [i / 10 * 2 * math.pi for i in range(11)]],
                                  ra=0.3, sides=4, hint=(0, 1, 0)))
    # 外套の前の襞
    for j, x in enumerate((-1.8, -0.9, 0.9, 1.8)):
        F = add(F, "fold%d" % j, ck.tube("body", "plaster", [(x, 20.0, -2.8), (x * 1.2, 12.0, -3.0), (x * 1.4, 4.0, -2.9)], ra=0.22, sides=4, hint=(0, 0, 1)))
    # 頭巾の縁の輪と頬の張り
    F = add(F, "hoodrim", ck.tube("head", "plaster", [(math.cos(a) * 2.9, 25.2, math.sin(a) * 2.6 - 2.0) for a in [i / 10 * 2 * math.pi for i in range(11)]],
                                  ra=0.28, sides=4, hint=(0, 1, 0)))
    for s in (-1, 1):
        F = add(F, "cheek%d" % s, ck.ellipsoid("head", "plaster", (s * 1.5, 27.2, -3.6), (0.5, 0.55, 0.55), segs=5, rings=2))
    # 腕の肘の瘤と指の爪
    for s, b in ((-1, "arm_l"), (1, "arm_r")):
        F = add(F, "elbow%d" % s, ck.ellipsoid(b, "plaster", (s * 3.6, 19.5, -1.8), (0.55, 0.55, 0.55), segs=6, rings=2))
        for j, dx in enumerate((-0.35, 0.0, 0.35)):
            F = add(F, "nail%d%d" % (s, j), ck.spike(b, "hollow", (s * 2.5 + dx, 15.6, -4.6), (dx * 0.3, -0.5, -0.8), 0.7, 0.16, sides=4, rings=1))
    # 鐘の縁の鋲と緑青の帯
    for j, a in enumerate((3.9, 4.4, 4.9, 5.4)):   # 鐘の前の下縁だけ（外套の中に入る後ろ側は避ける）
        F = add(F, "stud%d" % j, ck.ellipsoid("bell", "bronze", (math.cos(a) * 2.7, 15.9, -4.2 + math.sin(a) * 2.9), (0.36, 0.34, 0.36), segs=5, rings=2))
    F = add(F, "verd2", ck.tube("bell", "verdi", [(math.cos(a) * 2.5, 18.4, -4.2 + math.sin(a) * 2.4) for a in [i / 12 * 2 * math.pi for i in range(13)]],
                                ra=0.26, sides=4, hint=(0, 1, 0)))
    return F


if __name__ == "__main__":
    from dens import run_mob
    run_mob("bell_mourner", "cloud/bell_mourner.py", extra, letter="a")
