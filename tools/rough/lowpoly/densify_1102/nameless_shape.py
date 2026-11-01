# -*- coding: utf-8 -*-
"""名のない形 (nameless_shape) の密度上げ。採用案 A 折れた殻（取り込み 308 → 目標 959〜1,343）。

足したもの（骨と色は元のまま。core / ring / shard*）:
- 欠片の二段目（石板と骨の色。輪の骨に六つ。一段目の間に、向きを変えて）
- 核のまわりの細い輪（slate。核の赤道に一本）
- 核の表面の骨の小さな欠け（bone の錐。十八個）
- 目のふちの輪（bone の楕円の輪。二つ）
"""
import math
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from dens import add  # noqa: E402


def extra(o, F, eyes):
    ck = o
    import numpy as np
    # 欠片の二段目
    dirs2 = [(0.8, 0.3, -0.5), (-0.9, -0.2, 0.4), (0.3, 0.8, 0.6), (-0.4, 0.6, -0.9), (0.9, -0.6, 0.3), (-0.2, -0.8, -0.6)]
    for i, (x, y, z) in enumerate(dirs2):
        m = "bone" if i % 2 == 0 else "slate"
        c = (x * 3.6, 12.0 + y * 3.8, z * 3.6)
        F = add(F, "shard2_%d" % i, o.cone("ring", m, c, (x, y, z), 3.4, 1.1, sides=5, rings=3))
    # 核の赤道の輪
    F = add(F, "equator", ck.tube("core", "slate", [(math.cos(a) * 3.4, 12.0, math.sin(a) * 3.4) for a in [i / 12 * 2 * math.pi for i in range(13)]],
                                  ra=0.28, sides=4, hint=(0, 1, 0)))
    # 核の表面の骨の欠け（球の表面に立てる）
    rng = np.random.RandomState(7)
    for i in range(18):
        v = rng.normal(size=3)
        v = v / np.linalg.norm(v)
        base = (float(v[0] * 3.2), float(12.0 + v[1] * 4.2), float(v[2] * 3.2))
        F = add(F, "chip%d" % i, ck.cone("core", "bone", base, tuple(float(q) for q in v), 0.9, 0.3, sides=4, rings=1))
    # 目のふちの輪
    for j, dz in enumerate((-0.2, 0.2)):
        F = add(F, "eyering%d" % j, ck.tube("core", "bone", [(math.cos(a) * 0.9, 12.0 + math.sin(a) * 0.9, -2.9 + dz) for a in [i / 8 * 2 * math.pi for i in range(9)]],
                                            ra=0.14, sides=3, hint=(0, 0, 1)))
    # 子午線の輪（縦の輪）と外側の欠片（三段目、六つ）
    F = add(F, "meridian", ck.tube("core", "slate", [(0.0, 12.0 + math.sin(a) * 3.8, math.cos(a) * 3.8) for a in [i / 12 * 2 * math.pi for i in range(13)]],
                                   ra=0.26, sides=4, hint=(1, 0, 0)))
    for i in range(6):
        a = i / 6 * 2 * math.pi + 0.5
        c = (math.cos(a) * 4.8, 12.0 + (0.6 if i % 2 else -0.6), math.sin(a) * 4.8)
        F = add(F, "shard3_%d" % i, o.cone("ring", "slate" if i % 2 else "bone", c, (math.cos(a), 0.0, math.sin(a)), 2.4, 0.7, sides=4, rings=2))
    return F


if __name__ == "__main__":
    from dens import run_mob
    run_mob("nameless_shape", "abyss/nameless_shape.py", extra, letter="a")
