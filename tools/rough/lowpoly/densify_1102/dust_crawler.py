# -*- coding: utf-8 -*-
"""砂這い (dust_crawler) の密度上げ。採用案を取り込み時の数え方で読む（870 → 目標 1,330〜1,860）。

足したもの（骨と色は元のまま。body / head / jaw / tail / leg_*）:
- 甲羅の上の砂の粒の瘤（tuff。甲羅の背に二列）
- 甲の縁の砂のとげ（縁に沿って外向きに小さな棘）
- 脚の関節の瘤（dust。六本の脚の途中）
- 尾の根元の段と、尾の先の砂の帯
- 体の下の砂の盛り（床の高さ。潜っているときの盛り上がりの下地）
"""
import math
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from dens import kit, add  # noqa: E402


def extra(o, F, eyes):
    ck = kit()
    # 甲羅の背の瘤（二列）
    for j, (x, y, z) in enumerate(((-0.9, 5.55, -1.6), (0.9, 5.55, -1.6), (-0.9, 5.55, 0.9), (0.9, 5.55, 0.9), (0.0, 5.75, -0.3), (0.0, 5.75, 1.7))):
        F = add(F, "scute%d" % j, ck.ellipsoid("body", "tuff", (x, y, z), (0.7, 0.45, 0.75), segs=6, rings=2))
    # 甲の縁の砂のとげ（外向き）
    for i in range(10):
        a = i / 10 * 2 * math.pi
        if math.sin(a) < -0.8:
            continue
        x, z = math.cos(a) * 3.9, math.sin(a) * 4.3 * 0.9 + 0.2
        F = add(F, "grit%d" % i, ck.spike("body", "dust", (x, 3.3, z), (math.cos(a) * 0.9, 0.5, math.sin(a) * 0.9), 1.1, 0.26, sides=4, rings=2))
    # 脚の関節の瘤
    for nm, s, i, z in o.LEGS:
        F = add(F, "knee_%s%d" % (nm, i), ck.ellipsoid("body", "basalt", (s * (4.4 + 0.2 * (i - 2)), 2.6, z + 0.1), (0.55, 0.5, 0.55), segs=6, rings=2))
    # 尾の根元の段と先の砂の帯
    F = add(F, "tailring", ck.tube("tail", "tuff", [(0, 2.9, 5.0), (0, 2.6, 5.4)], ra=1.25, sides=6, hint=(1, 0, 0)))
    F = add(F, "tailtip", ck.loft("tail", "dust", [ck.ring(0.0, 1.0, -0.5, 0.5, n=8), ck.ring(0.5, 1.2, -0.6, 0.6, n=8)], caps=(False, False)))
    # 体の下の砂の盛り
    for i, (x, z) in enumerate(((-3.6, -2.0), (3.4, -1.4), (-3.1, 2.8), (3.6, 3.0))):
        F = add(F, "mound%d" % i, ck.ellipsoid("body", "dust", (x, 0.3, z), (1.2, 0.75, 1.2), segs=6, rings=2))
    return F


if __name__ == "__main__":
    from dens import run_mob
    run_mob("dust_crawler", "moon/dust_crawler.py", extra)
