# -*- coding: utf-8 -*-
"""帯電の蛾 (charged_moth) の密度上げ。採用案 A 細い羽虫（取り込み 442 → 目標 733〜1,026）。

足したもの（骨と色は元のまま。body / head / wing_l / wing_r / abd）:
- 羽の筋（oxcu の細い筋。羽ごとに二本ずつ）と、羽の縁の棘（tuff。各羽に三本）
- 胸の節の輪（tuff の細い輪。二段）と、腹の節の輪（tuff。三段）
- 脚（tuff の細い筒。左右に三本ずつ。胴の下から出す）
- 触角の先の小さな光（light。二つ）
"""
import math
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from dens import add  # noqa: E402


def extra(o, F, eyes):
    ck = o
    for s in (-1, 1):
        wb = "wing_l" if s < 0 else "wing_r"
        F = add(F, "vein%da" % s, ck.tube(wb, "oxcu", [(s * 0.7, 2.9, -0.5), (s * 2.0, 2.95, -0.4), (s * 3.2, 3.0, -0.25)],
                                         ra=[0.08, 0.06, 0.04], sides=3, hint=(0, 1, 0)))
        F = add(F, "vein%db" % s, ck.tube(wb, "oxcu", [(s * 0.7, 2.9, 0.4), (s * 1.8, 2.85, 1.2), (s * 2.5, 2.8, 1.5)],
                                         ra=[0.08, 0.06, 0.04], sides=3, hint=(0, 1, 0)))
        for j, (x, z) in enumerate(((2.0, -0.9), (2.9, -0.2), (2.2, 1.0))):
            F = add(F, "wspike%d%d" % (s, j), ck.spike(wb, "tuff", (s * x, 3.0, z), (s * 0.3, 0.2, 0.1), 0.6, 0.12, sides=3, rings=1))
    for j, z in enumerate((-1.2, -0.3)):
        F = add(F, "thoraxring%d" % j, ck.tube("body", "tuff", [(math.cos(a) * 1.05, 2.2, z + math.sin(a) * 0.15) for a in [i / 8 * 2 * math.pi for i in range(9)]],
                                               ra=0.16, sides=3, hint=(0, 1, 0)))
    for j, z in enumerate((1.2, 2.0, 2.8)):
        F = add(F, "abdring%d" % j, ck.tube("abd", "tuff", [(math.cos(a) * 0.65, math.sin(a) * 0.6, z * 0.0 + 0.5 * j - 0.5) for a in [i / 8 * 2 * math.pi for i in range(9)]],
                                            ra=0.1, sides=3, hint=(0, 1, 0)))
    for s in (-1, 1):
        for j, dz in enumerate((-0.8, -0.1, 0.6)):
            F = add(F, "leg%d%d" % (s, j), ck.tube("body", "tuff", [(s * 0.9, 1.6, dz - 0.3), (s * 1.8, 0.4, dz)], ra=[0.14, 0.08], sides=3, hint=(0, 0, 1)))
        F = add(F, "antlight%d" % s, ck.ellipsoid("head", "light", (s * 1.0, 1.7, -1.0), (0.12, 0.12, 0.12), segs=4, rings=1))
    return F


if __name__ == "__main__":
    from dens import run_mob
    run_mob("charged_moth", "abyss/charged_moth.py", extra, letter="a")
