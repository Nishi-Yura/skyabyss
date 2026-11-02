# -*- coding: utf-8 -*-
"""砂潜り (sand_burrower) の密度上げ。採用案 C 星見魚（取り込み 256 → 目標 591〜827）。

足したもの（骨と色は元のまま。body / head / jaw / tail / fin_l / fin_r）:
- 背の棘（coral。背の上に五本。尾の方へ向く）
- 腹の鱗（belly。腹の下に十枚の薄い楕円体）
- 目のまぶた（skin の薄い楕円体。左右の目の上）
- 脇の鰭の棘（teal。各鰭に三本。鰭の外縁へ）
- 口の縁の牙（tooth の小さな錐。上あごの前に六本）
- 尾の鰭の筋（teal。二本）
"""
import math
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from dens import add  # noqa: E402


def extra(o, F, eyes):
    ck = o
    for j, z in enumerate((-1.5, 0.0, 1.5, 3.0, 4.6)):
        F = add(F, "dorsal%d" % j, ck.spike("body", "coral", (0.0, 4.9 - 0.1 * j, z), (0.0, 0.7, 0.8), 1.4, 0.4, sides=3, rings=1))
    for j, (x, z) in enumerate(((-1.2, -2.6), (1.2, -2.6), (-1.6, 0.0), (1.6, 0.0), (-1.2, 2.6), (1.2, 2.6), (-0.6, 4.6), (0.6, 4.6), (-1.6, -4.2), (1.6, -4.2))):
        F = add(F, "belly%d" % j, ck.ellipsoid("body", "belly", (x, 0.8, z), (0.6, 0.35, 0.8), segs=5, rings=2))
    for s in (-1, 1):
        F = add(F, "lid%d" % s, ck.ellipsoid("head", "skin", (s * 1.8, 4.9, -8.4), (0.95, 0.45, 0.5), segs=6, rings=2))
    for s, b in ((-1, "fin_l"), (1, "fin_r")):
        for j, (dy, dz) in enumerate(((0.6, 0.0), (-0.4, 0.8), (-0.9, -0.8))):
            F = add(F, "finray%d%d" % (s, j), ck.spike(b, "teal", (s * 4.2, 2.4 + dy * 0.5, -2.0 + dz), (s, -0.2 + dy * 0.3, 0.2 + dz * 0.2), 1.4, 0.26, sides=3, rings=1))
    for j, dx in enumerate((-0.6, -0.2, 0.2, 0.6)):
        F = add(F, "tooth%d" % j, ck.spike("jaw", "tooth", (dx * 1.6, 2.2, -10.6), (dx * 0.2, 0.2, -1.0), 0.8, 0.16, sides=3, rings=1))
    for j, dx in enumerate((-0.4, 0.4)):
        F = add(F, "tailray%d" % j, ck.tube("tail", "teal", [(dx * 0.6, 2.0, 10.4), (dx * 1.2, 1.9, 13.4), (dx * 1.6, 1.8, 16.0)], ra=[0.14, 0.12, 0.05], sides=3, hint=(0, 1, 0)))
    return F


if __name__ == "__main__":
    from dens import run_mob
    run_mob("sand_burrower", "ch2/sand_burrower.py", extra, letter="c")
