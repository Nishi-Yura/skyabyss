# -*- coding: utf-8 -*-
"""淵の羽影 (rift_wing) の密度上げ。一案 A（取り込み 820 → 目標 1,330〜1,860）。

足したもの（骨と色は元のまま。wing_l / wing_r / body / head / tail）:
- 翼の後ろの縁の羽の棘（銀。縁の裂けの所に四本ずつ）
- 翼の前の骨の関節の玉（銀。三か所）
- 胴の背の肋の筋（銀の細い弧。三本）
- 鉤爪の根元の玉と、尾の両脇の薄い棘（影の色）
- 仮面の縁の小さな刻み（銀の玉）
"""
import math
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from dens import kit, add  # noqa: E402


def extra(o, F, eyes):
    ck = kit()
    CY = o.CY
    # 翼の後ろの縁の羽の棘と前の骨の関節
    for sx, b in ((-1, "wing_l"), (1, "wing_r")):
        for t in (0.22, 0.42, 0.6, 0.78):
            x = sx * (3.4 + t * 24.0)
            fz = -1.2 - math.sin(t * math.pi) * 2.6 - t ** 3 * 3.0
            fy = CY + 1.0 + math.sin(t * math.pi * 0.9) * 3.4 - t * 1.2
            chord = 12.0 * (1 - t) ** 0.7 + 0.8
            notch = 2.6 * abs(math.sin(t * math.pi * 3)) * (1 - t)
            bz = fz + chord - notch
            F = add(F, "feather%s%d" % (b, int(t * 100)), ck.spike(b, "silver", (x, fy - 0.5, bz - 0.2), (sx * 0.1, 0.1, 1.0), 1.6, 0.3, sides=4, rings=2))
        for t in (0.12, 0.86):
            x = sx * (3.4 + t * 24.0)
            fz = -1.2 - math.sin(t * math.pi) * 2.6 - t ** 3 * 3.0
            fy = CY + 1.0 + math.sin(t * math.pi * 0.9) * 3.4 - t * 1.2
            chord = 12.0 * (1 - t) ** 0.7 + 0.8
            notch = 2.6 * abs(math.sin(t * math.pi * 3)) * (1 - t)
            bz = fz + chord - notch
            F = add(F, "fin%s%d" % (b, int(t * 100)), ck.tube(b, "silver", [(x, fy + 0.15, fz), (x, fy - 0.2, bz - 0.4)], ra=[0.28, 0.1], sides=4, hint=(0, 1, 0)))
        for t in (0.25, 0.5, 0.75):
            x = sx * (3.4 + t * 24.0)
            fz = -1.2 - math.sin(t * math.pi) * 2.6 - t ** 3 * 3.0
            fy = CY + 1.0 + math.sin(t * math.pi * 0.9) * 3.4 - t * 1.2
            F = add(F, "knuck%s%d" % (b, int(t * 100)), ck.ellipsoid(b, "silver", (x, fy + 0.2, fz), (0.55, 0.55, 0.55), segs=6, rings=2))
    # 胴の背の肋の筋（銀）
    for j, z in enumerate((-2.6, 0.4, 3.2)):
        F = add(F, "rib%d" % j, ck.tube("body", "silver", [(-2.6, CY + 2.2, z), (0.0, CY + 3.1, z + 0.2), (2.6, CY + 2.2, z)], ra=0.26, sides=4, hint=(0, 0, 1)))
    # 鉤爪の根元の玉と尾の両脇の薄い棘
    for sx in (-1, 1):
        F = add(F, "clawknot%d" % sx, ck.ellipsoid("body", "silver", (sx * 1.6, CY - 2.5, -1.0), (0.6, 0.6, 0.6), segs=6, rings=2))
        for j, dx in enumerate((-0.25, 0.25)):
            F = add(F, "clawtip%d%d" % (sx, j), ck.spike("body", "silver", (sx * 1.7 + dx, CY - 5.3, -3.3), (0, -0.5, -0.8), 0.7, 0.16, sides=4, rings=1))
        F = add(F, "tailthorn%d" % sx, ck.spike("tail", "shadow", (sx * 1.0, CY - 0.6, 9.0), (sx, 0.2, 0.3), 1.6, 0.4, sides=4, rings=2))
    # 仮面の縁の刻み
    for j, a in enumerate((-0.9, -0.3, 0.3, 0.9)):
        F = add(F, "notchm%d" % j, ck.ellipsoid("head", "silver", (math.sin(a) * 3.9, CY + 0.5 + math.cos(a) * 3.9, -8.0), (0.32, 0.32, 0.32), segs=5, rings=2))
    return F


if __name__ == "__main__":
    from dens import run_mob
    run_mob("rift_wing", "moon/rift_wing.py", extra, letter="a")
