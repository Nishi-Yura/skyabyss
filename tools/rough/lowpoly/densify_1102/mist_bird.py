# -*- coding: utf-8 -*-
"""霞鳥 (mist_bird) の密度上げ。一品一案（取り込み 892 → 目標 1,326〜1,856）。

足したもの（骨と色は元のまま。body / head / wing_* / tail）:
- 胸の羽毛の盛り（白の楕円体を二つ。胸の下と横）と、首の襟の羽（白い輪）
- 頭の冠の羽（金。三本）と、足の指（金。各三本）
- 翼の先の風切りの羽（三本ずつ。翼の後ろの端から外へ）
- 尾の外側の長い霞の羽（左右に二本ずつ。先が細い）
"""
import math
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from dens import kit, add  # noqa: E402


def extra(o, F, eyes):
    ck = kit()
    # 胸の羽毛の盛り
    F = add(F, "breast", ck.ellipsoid("body", "mist", (0, 7.6, -2.2), (1.6, 1.0, 1.6), segs=7, rings=3))
    for s in (-1, 1):
        F = add(F, "flank%d" % s, ck.ellipsoid("body", "white", (s * 1.5, 8.2, 0.4), (1.1, 1.1, 1.8), segs=6, rings=2))
    # 首の襟の羽（白い輪）
    F = add(F, "ruff", ck.tube("head", "white", [(math.cos(a) * 1.5, 8.9, -3.8 + math.sin(a) * 1.4) for a in [i / 9 * 2 * math.pi for i in range(10)]],
                               ra=0.4, sides=4, hint=(0, 1, 0)))
    # 頭の冠の羽（金）
    for j, dx in enumerate((-0.6, 0.0, 0.6)):
        F = add(F, "crest%d" % j, ck.spike("head", "gold", (dx, 10.9, -5.0), (dx * 0.4, 1.0, 0.5), 1.1, 0.22, sides=4, rings=1))
    # 足の指（金）
    for s in (-1, 1):
        for j, dx in enumerate((-0.25, 0.0, 0.25)):
            F = add(F, "toe%d%d" % (s, j), ck.spike("body", "gold", (s * 0.9 + dx * 0.5, 6.0, 1.7), (dx, -0.3, 1.0), 0.6, 0.12, sides=4, rings=1))
    # 翼の先の風切りの羽
    for side in (-1, 1):
        b = "wing_l" if side < 0 else "wing_r"
        for j, (dz, ln) in enumerate(((2.2, 2.6), (3.2, 2.2), (4.2, 1.8))):
            F = add(F, "prim%d%d" % (side, j), ck.tube(b, "mist" if j % 2 else "white",
                                                       [(side * 14.6, 9.4, 2.2 + dz * 0.5), (side * (15.5 + 0.5 * j), 9.2, 2.6 + dz), (side * (15.9 + 0.4 * j), 9.0, 2.8 + dz + ln * 0.5)],
                                                       ra=[0.2, 0.14, 0.05], rb=[0.7, 0.5, 0.15], sides=4, hint=(0, 1, 0)))
    # 尾の外側の長い羽
    for i, dx in enumerate((-1.6, 1.6, -2.4, 2.4)):
        F = add(F, "tailx%d" % i, ck.tube("tail", "mist", [(dx * 0.5, 8.6, 3.0), (dx * 1.5, 8.2, 6.0), (dx * 2.5, 7.8, 9.6)],
                                          ra=[0.18, 0.12, 0.05], rb=[0.7, 0.5, 0.15], sides=4, hint=(0, 1, 0)))
    return F


if __name__ == "__main__":
    from dens import run_mob
    run_mob("mist_bird", "cloud/mist_bird.py", extra, letter="a")
