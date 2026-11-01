# -*- coding: utf-8 -*-
"""逆火 (backfire) の密度上げ。一品一案（取り込み 760 → 目標 1,326〜1,856）。

足したもの（骨と色は元のまま。body / core / tail）:
- 鉄の籠の格子の交差の留め金（鉄の鋲。籠の三段の輪の上に各六個）
- 籠の下の脚（鉄の短い足。四本）と、籠の上の縁の輪（鉄）
- 芯の残り火の粒（ember。芯のまわりに六つ）
- 炎の冠の根元の火の舌（ember。三本）
- 尾の炎の外側の筋（ember の細い帯。二本）
"""
import math
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from dens import kit, add  # noqa: E402


def extra(o, F, eyes):
    ck = kit()
    # 籠の留め金（三段の輪の上の鋲）
    for y, r in ((6.6, 2.6), (10.0, 3.2), (13.6, 2.4)):
        for j in range(6):
            a = j / 6 * 2 * math.pi + 0.5
            F = add(F, "rivet%d_%d" % (int(y * 10), j), ck.ellipsoid("body", "iron", (math.cos(a) * (r + 0.2), y, math.sin(a) * (r + 0.2)),
                                                                (0.3, 0.3, 0.3), segs=5, rings=2))
    # 籠の下の脚と上の縁の輪
    for j in range(4):
        a = j / 4 * 2 * math.pi + math.pi / 4
        F = add(F, "foot%d" % j, ck.tube("body", "iron", [(math.cos(a) * 2.4, 6.6, math.sin(a) * 2.4), (math.cos(a) * 2.7, 4.6, math.sin(a) * 2.7),
                                                        (math.cos(a) * 3.0, 3.6, math.sin(a) * 3.0)], ra=[0.32, 0.28, 0.2], sides=4, hint=(0, 1, 0)))
    F = add(F, "topring", ck.tube("body", "iron", [(math.cos(a) * 2.2, 14.6, math.sin(a) * 2.2) for a in [i / 10 * 2 * math.pi for i in range(11)]],
                                  ra=0.28, sides=4, hint=(0, 1, 0)))
    # 芯の残り火の粒
    for j in range(6):
        a = j / 6 * 2 * math.pi
        y = 10.0 + (1.2 if j % 2 else -1.2)
        F = add(F, "ember%d" % j, ck.ellipsoid("core", "ember", (math.cos(a) * 2.3, y, math.sin(a) * 2.3), (0.4, 0.4, 0.4), segs=5, rings=2))
    # 炎の冠の根元の火の舌
    for j in range(3):
        a = j / 3 * 2 * math.pi + 0.9
        F = add(F, "tongue%d" % j, ck.spike("body", "ember", (math.cos(a) * 1.9, 13.8, math.sin(a) * 1.9), (math.cos(a) * 0.5, 1, math.sin(a) * 0.5), 1.4, 0.4, sides=4, rings=2))
    # 尾の炎の外側の筋
    for s in (-1, 1):
        F = add(F, "tailstreak%d" % s, ck.tube("tail", "ember", [(s * 1.6, 6.2, 0.0), (s * 2.2, 3.6, 1.0), (s * 2.6, 1.2, 2.6)], ra=[0.3, 0.2, 0.05], sides=3, hint=(0, 0, 1)))
    return F


if __name__ == "__main__":
    from dens import run_mob
    run_mob("backfire", "cloud/backfire.py", extra, letter="a")
