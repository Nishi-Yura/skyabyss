# -*- coding: utf-8 -*-
"""灯喰い (lamp_eater) の密度上げ。採用案 A 黒い口の球（取り込み 496 → 目標 959〜1,343）。

足したもの（骨と色は元のまま。body / jaw / neck / head）:
- 球の表面の棘（void の錐。十二本。口の反対側と上へ）
- 口の内側の奥歯（bone の錐。二列。前の歯の間に）
- 球の赤道の肋（slate の細い輪。二本）
- 口の縁の唇の盛り（void の楕円体。上下に二つ）
"""
import math
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from dens import add  # noqa: E402


def extra(o, F, eyes):
    ck = o
    # 球の表面の棘（口の反対側へ）
    for i in range(12):
        a = 2 * math.pi * i / 12 + 0.26
        if math.cos(a) * 0 + math.sin(a) > 0.2 and math.cos(a) < -0.3:
            pass
        d = (math.cos(a), math.sin(a), 0.2)
        n = math.sqrt(sum(v * v for v in d))
        d = tuple(v / n for v in d)
        base = (d[0] * 4.2, 8.0 + d[1] * 4.2, d[2] * 4.2 + 0.0)
        if base[2] > 1.4:       # 前の口の縁は避ける
            continue
        F = add(F, "spine%d" % i, ck.cone("body", "void", base, d, 1.5, 0.42, sides=4, rings=2))
    # 口の奥の歯（二列目）
    for i in range(8):
        a = 2 * math.pi * i / 8 + math.pi / 8
        x, y = 1.6 * math.cos(a), 8.0 + 1.6 * math.sin(a)
        F = add(F, "innertooth%d" % i, ck.cone("jaw", "bone", (x, y, -3.9), (x * 0.2, (y - 8.0) * 0.2, -1.0), 0.9, 0.22, sides=6, rings=1))
    # 赤道の肋
    for j, z in enumerate((-1.2, 1.4)):
        pts = [(4.5 * math.cos(a), 8.0 + 4.5 * math.sin(a) * 0.05, z + 0.3 * math.sin(a)) for a in [i / 10 * 2 * math.pi for i in range(11)]]
        F = add(F, "rib%d" % j, ck.tube("body", "slate", pts, ra=0.22, sides=4, hint=(0, 1, 0)))
    # 唇の盛り
    for s in (-1, 1):
        F = add(F, "lipbump%d" % s, ck.ellipsoid("jaw", "void", (s * 1.8, 5.2 + (s < 0) * 0.0, -2.8), (0.9, 0.6, 0.9), segs=6, rings=2))
    return F


if __name__ == "__main__":
    from dens import run_mob
    run_mob("lamp_eater", "abyss/lamp_eater.py", extra, letter="a")
