# -*- coding: utf-8 -*-
"""押し石 (push_stone) の密度上げ。採用案 A 目のある輪石（取り込み 676 → 目標 1,330〜1,860）。

足したもの（骨と色は元のまま。ring / core / body / plate_*）:
- 輪の外縁の岩の歯（凝灰岩。三角に外へ 14 本のうち 7 本）
- 輪の内縁の銀の鋲（内側へ 6 個）
- 輪の表面に張り付いた小石（玄武岩と月の砂。輪の瘤の間に 4 個）
- 芯の銀の籠の帯（芯を囲む細い輪。二本。目の前は開ける）
- 輪の上下に走る割れ目の光（星の光の小さな筋。二本）
"""
import math
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from dens import kit, add  # noqa: E402


def extra(o, F, eyes):
    ck = kit()
    # 輪の外縁の岩の歯
    for i in range(0, 14, 2):
        a = i / 14 * 2 * math.pi + math.pi / 14
        r = 7.0 + 1.2
        F = add(F, "tooth%d" % i, ck.spike("ring", "tuff", (math.cos(a) * 8.7, 14 + math.sin(a) * 8.7, 0.0),
                                           (math.cos(a), math.sin(a), 0.0), 1.6, 0.5, sides=4, rings=2))
    # 歯の間の小さな欠けの歯
    knobs = [i / 5 * 2 * math.pi + 0.3 for i in range(5)]
    for i in range(1, 14, 2):
        a = i / 14 * 2 * math.pi + math.pi / 14
        if min(abs((a - k + math.pi) % (2 * math.pi) - math.pi) for k in knobs) < 0.3:
            continue   # 瘤の上には置かない（埋まるため）
        F = add(F, "smalltooth%d" % i, ck.spike("ring", "basalt", (math.cos(a) * 8.6, 14 + math.sin(a) * 8.6, 0.0),
                                                (math.cos(a), math.sin(a), 0.0), 0.9, 0.36, sides=4, rings=1))
    # 輪の内縁の銀の鋲
    for i in range(6):
        a = i / 6 * 2 * math.pi + 0.5
        F = add(F, "rivet%d" % i, ck.ellipsoid("ring", "silver", (math.cos(a) * 5.9, 14 + math.sin(a) * 5.9, -1.4), (0.42, 0.42, 0.42), segs=5, rings=2))
    # 輪の表面の小石
    for i, a in enumerate((0.9, 2.1, 3.0, 3.9, 5.2, 0.2)):
        m = "basalt" if i % 2 else "dust"
        F = add(F, "pebble%d" % i, ck.ellipsoid("ring", m, (math.cos(a) * 7.0, 14 + math.sin(a) * 7.0, 1.6), (0.8, 0.7, 0.7), segs=6, rings=2))
    # 輪の表側の縁の帯（前に張り出す薄い輪）
    for i in range(14):
        a0, a1 = i / 14 * 2 * math.pi, (i + 1) / 14 * 2 * math.pi
        if i % 2:
            continue
        F = add(F, "lip%d" % i, ck.tube("ring", "silver" if i % 4 == 0 else "tuff",
                                        [(math.cos(a0) * 7.0, 14 + math.sin(a0) * 7.0, -2.1), (math.cos(a1) * 7.0, 14 + math.sin(a1) * 7.0, -2.1)],
                                        ra=0.5, sides=4, hint=(0, 0, 1)))
    # 芯の銀の籠の帯（前を開ける）
    for j, t in enumerate((0.45, -0.45)):
        pts = [(math.cos(q / 8 * 2 * math.pi) * 3.2, 14 + t * 3.2 + math.sin(q / 8 * 2 * math.pi) * 0.4, math.sin(q / 8 * 2 * math.pi) * 0.5 + 0.6)
               for q in range(9)]
        F = add(F, "corerib%d" % j, ck.tube("core", "silver", pts, ra=0.22, sides=4, hint=(0, 0, 1)))
    # 割れ目の光（輪の上下）
    for j, a in enumerate((math.pi / 2, -math.pi / 2)):
        F = add(F, "crack%d" % j, ck.tube("ring", "star", [(math.cos(a) * 6.0, 14 + math.sin(a) * 6.0, -2.0), (math.cos(a) * 6.9, 14 + math.sin(a) * 6.9, -2.1)],
                                          ra=0.22, sides=4, hint=(0, 0, 1)))
    return F


if __name__ == "__main__":
    from dens import run_mob
    run_mob("push_stone", "moon/push_stone.py", extra, letter="a")
