# -*- coding: utf-8 -*-
"""殻背の兎 (shell_hare) の密度上げ。採用案 B 欠けた三日月の兎（取り込み 932 → 目標 1,330〜1,860）。

足したもの（骨と色は元のまま。ear_l/r / body / head / shell / arm_* / leg_*）:
- 耳の根元の瘤（毛の色。立った耳の付け根を締める）
- 三日月の背の板に、上に尖った欠けの棘 5 本（玄武岩）と縁の銀の刃の先
- 前足と後ろ足の指の爪（銀。各 3 本）
- 鼻の両側のひげ（銀の細い筋。4 本）
- 胸の横の毛束（毛の色の棘。左右 4 本ずつ）
- 尾の根元の毛の塊
"""
import math
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from dens import kit, add  # noqa: E402


def extra(o, F, eyes):
    ck = kit()
    # 耳の根元の瘤
    for s, b in ((-1, "ear_l"), (1, "ear_r")):
        F = add(F, "earknot%d" % s, ck.ellipsoid("head", "fur", (s * 0.8, 7.0, -3.4), (0.6, 0.55, 0.6), segs=6, rings=2))
    # 三日月の背の欠けの棘（上に尖る）
    for i, (y, z) in enumerate(((5.0, -2.4), (7.2, -1.3), (8.8, 0.4), (9.4, 2.0), (8.5, 3.6), (6.6, 4.9))):
        F = add(F, "notch%d" % i, ck.spike("shell", "tuff" if i % 2 else "basalt", (0.0, y + 1.0, z), (0.0, 1.0, 0.35), 1.2, 0.42, sides=4, rings=2))
    # 眉の毛の塊（目の上）
    for s in (-1, 1):
        F = add(F, "brow%d" % s, ck.ellipsoid("head", "fur", (s * 1.0, 7.1, -5.1), (0.45, 0.32, 0.5), segs=6, rings=2))
    # 前足と後ろ足の爪（銀）
    for s, b in ((-1, "arm_l"), (1, "arm_r")):
        for j in (-1, 0, 1):
            F = add(F, "toe%d%d" % (s, j), ck.spike(b, "silver", (s * 1.5 + j * 0.28, 0.3, -4.2), (j * 0.2, -0.3, -1), 0.7, 0.16, sides=4, rings=1))
    for s, b in ((-1, "leg_l"), (1, "leg_r")):
        for j in (-1, 0, 1):
            F = add(F, "htoe%d%d" % (s, j), ck.spike(b, "silver", (s * 2.1 + j * 0.3, 0.45, 3.5), (j * 0.25, -0.3, 1), 0.8, 0.17, sides=4, rings=1))
    # 鼻の両側のひげ
    for s in (-1, 1):
        for j, y in enumerate((5.8, 5.4)):
            F = add(F, "whisk%d%d" % (s, j), ck.tube("head", "silver", [(s * 0.3, y, -6.1), (s * 1.8, y - 0.1 + 0.25 * j, -6.5)], ra=[0.12, 0.08], sides=4, hint=(0, 1, 0)))
    # 胸の横の毛束
    for s in (-1, 1):
        for j, (y, z) in enumerate(((3.8, -2.9), (4.2, -1.5), (4.4, 0.0), (4.1, 1.5))):
            F = add(F, "tuft%d%d" % (s, j), ck.spike("body", "fur", (s * 1.9, y, z), (s * 1.0, 0.5, 0.2), 0.9, 0.3, sides=4, rings=1))
    # 尾の根元の毛の塊
    F = add(F, "tailfur", ck.ellipsoid("body", "fur", (0, 4.6, 4.0), (1.1, 1.0, 1.0), segs=6, rings=2))
    return F


if __name__ == "__main__":
    from dens import run_mob
    run_mob("shell_hare", "moon/shell_hare.py", extra)
