# -*- coding: utf-8 -*-
"""影の歩き手 (shade_strider) の密度上げ。採用案 A（取り込み 1,014 → 目標 1,330〜1,860）。

足したもの（骨と色は元のまま。body / head / cloth / leg_* / shin_*）:
- 胴の硝子の甲の縁に、銀の鋲の列（前と横。十数個）
- 影の布の裾に、短い裂け（影の色の小さな棘。六本ずつ）
- 腿の硝子の環（腿の途中に一本ずつ。銀の薄い輪）
- 足先の爪（銀。四本ずつ）
- 仮面の縁の銀の鋲（左右に三つずつ）
- 硝子の甲の背の筋（一本）
"""
import math
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from dens import kit, add  # noqa: E402


def extra(o, F, eyes):
    ck = kit()
    BY = o.BY
    # 硝子の甲の縁の銀の鋲
    for i in range(12):
        a = i / 12 * 2 * math.pi
        x, z = math.sin(a) * 5.8, math.cos(a) * 5.9
        y = BY + 2.4 + 3.0 * math.sqrt(max(0.0, 1 - (x / 5.9) ** 2 - (z / 6.1) ** 2)) - 0.2
        F = add(F, "shellrivet%d" % i, ck.ellipsoid("body", "silver", (x, y - 0.3, z), (0.45, 0.4, 0.45), segs=5, rings=2))
    # 影の布の裾の短い裂け
    for i in range(6):
        a = math.radians(i * 60 + 60)
        x, z = math.sin(a) * 5.4, math.cos(a) * 5.4
        F = add(F, "hem%d" % i, ck.spike("cloth", "shadow", (x, BY - 13.4, z), (math.sin(a) * 0.3, -1, math.cos(a) * 0.3), 2.0, 0.5, sides=4, rings=2))
    # 腿の硝子の環
    for key, (sx, sz) in o.LEGS.items():
        h, kn = o.hip(sx, sz), o.knee(sx, sz)
        mid = o.lerp(h, kn, 0.78)
        F = add(F, "thring" + key, ck.tube("leg_" + key, "silver", [o.lerp(h, kn, 0.74), o.lerp(h, kn, 0.80)], ra=2.0, sides=6, hint=(0, 1, 0), caps=(True, True)))
        ft = o.foot(sx, sz)
        for j, dx in enumerate((-0.5, 0.0, 0.5, 0.0)):
            dz = (0.5 if j == 3 else 0.0)
            F = add(F, "toe%s%d" % (key, j), ck.spike("shin_" + key, "silver", (ft[0] + dx, ft[1] + 0.2, ft[2] + dz), (dx * 0.3, -0.6, -0.6 if j != 3 else 0.2), 1.0, 0.26, sides=4, rings=1))
    # 仮面の縁の銀の鋲
    for j, a in enumerate((-0.9, -0.45, 0.0, 0.45, 0.9)):
        F = add(F, "maskstud%d" % j, ck.ellipsoid("head", "silver", (math.sin(a) * 5.4, BY - 0.8 - math.cos(a) * 4.4 + 3.0, -7.0 - 1.2 * math.cos(a) - 2.5),
                                                  (0.42, 0.42, 0.42), segs=5, rings=2))
    # 硝子の甲の背の筋
    F = add(F, "shellspine", ck.tube("body", "glass", [(0, BY + 5.2, -4.0), (0, BY + 5.4, 0.8), (0, BY + 4.6, 4.8)], ra=0.35, sides=4, hint=(1, 0, 0)))
    return F


if __name__ == "__main__":
    from dens import run_mob
    run_mob("shade_strider", "moon/shade_strider.py", extra)
