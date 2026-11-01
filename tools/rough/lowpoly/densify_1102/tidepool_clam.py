# -*- coding: utf-8 -*-
"""潮だまりの貝 (tidepool_clam) の密度上げ。採用案 A 縁飾りの帆立（取り込み 376 → 目標 591〜827）。

足したもの（骨と色は元のまま。body / lid / pearl）:
- 上の殻の放射の筋の間に、薄い細い筋（rib。十本。殻の縁から中ほどへ）
- 殻の縁の小さな縁の歯（shell。前の縁に沿って八つ。外へ向く）
- 下の殻の縁の付着した藤壺（salt。三つ）
- 殻の内側の真珠層の帯（nacre の細い輪。一本）
"""
import math
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from dens import add  # noqa: E402


def extra(o, F, eyes):
    ck = o
    # 上の殻の放射の筋（元の筋のあいだ）
    for i in range(10):
        a = math.radians(-66 + 132 * (i + 0.5) / 10)
        s, c = math.sin(a), math.cos(a)
        p0 = (s * 7.4 * 0.2, 7.2, -c * 7.6 * 0.15)
        p1 = (s * 7.4 * 0.62, 5.6, -c * 7.6 * 0.66)
        p2 = (s * 7.4 * 0.95, 4.5, -c * 7.6 * 0.95)
        F = add(F, "thinrib%d" % i, ck.tube("lid", "rib", [p0, p1, p2], ra=[0.14, 0.2, 0.16], sides=3, hint=(0, 1, 0)))
    # 殻の縁の歯（前の縁に沿う）
    for i in range(8):
        a = math.radians(-60 + 120 * i / 7)
        s, c = math.sin(a), math.cos(a)
        F = add(F, "edgetooth%d" % i, ck.spike("body", "shell", (s * 7.3, 3.5, -c * 7.5), (s * 0.4, 0.2, -c * 0.6), 0.9, 0.22, sides=3, rings=1))
    # 下の殻の縁の藤壺
    for j, (x, z) in enumerate(((-4.2, -4.0), (4.0, -4.2), (0.0, -6.2))):
        F = add(F, "barnacle%d" % j, ck.ellipsoid("body", "salt", (x, 2.6, z), (0.6, 0.45, 0.6), segs=5, rings=2))
    # 内側の真珠層の帯
    pts = [(math.cos(a) * 5.4, 3.3, -0.6 + math.sin(a) * 4.8 * 0.2) for a in [i / 10 * 2 * math.pi for i in range(11)]]
    F = add(F, "nacreband", ck.tube("body", "nacre", pts, ra=0.2, sides=3, hint=(0, 1, 0)))
    return F


if __name__ == "__main__":
    from dens import run_mob
    run_mob("tidepool_clam", "ch2/tidepool_clam.py", extra, letter="a")
