# -*- coding: utf-8 -*-
"""峰の雛 (peak_fledgling) の密度上げ。採用案 B 鷲の子（取り込み 228 → 目標 545〜763）。

足したもの（骨と色は元のまま。body / head / wing_* / tail / leg_*）:
- 胸の羽の盛り（feather。胸の下に一つ、横に二つ）
- 翼の先の風切り（feather の棘。左右に四本ずつ）
- 尾羽の棘（feather。三本）
- 脚の膝の瘤（feather。左右に一つずつ）と、目のふちの白（feathl の小さな輪）
- 嘴の根元の羽（feathl の小さな楕円体。二つ）
"""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from dens import add  # noqa: E402


def extra(o, F, eyes):
    ck = o
    F = add(F, "breastpad", ck.ellipsoid("body", "feather", (0.0, 4.8, -1.4), (1.5, 1.0, 1.1), segs=6, rings=2))
    for s in (-1, 1):
        F = add(F, "flank%d" % s, ck.ellipsoid("body", "feathl", (s * 1.9, 5.6, -0.2), (0.8, 0.9, 1.4), segs=5, rings=2))
    for s, b in ((-1, "wing_l"), (1, "wing_r")):
        for j, z in enumerate((-0.4, 0.6, 1.6, 2.6, 3.4, 4.0)):
            F = add(F, "prim%d%d" % (s, j), ck.spike(b, "feather", (s * (5.8 + 0.2 * j), 6.6, z), (s * 0.3, 0.3, 0.9), 1.5, 0.22, sides=3, rings=1))
    for j, dx in enumerate((-1.5, -1.0, -0.5, 0.0, 0.5, 1.0, 1.5)):
        F = add(F, "tailfeat%d" % j, ck.spike("tail", "feather", (dx * 0.8, 5.6, 5.4), (dx * 0.5, 0.2, 1.0), 1.4, 0.26, sides=3, rings=1))
    for s, b in ((-1, "leg_l"), (1, "leg_r")):
        F = add(F, "kneebump%d" % s, ck.ellipsoid(b, "feather", (s * 1.2, 3.8, 0.2), (0.45, 0.4, 0.45), segs=5, rings=2))
    for s in (-1, 1):
        F = add(F, "eyeline%d" % s, ck.tube("head", "feathl", [(s * 1.0, 9.9, -3.7), (s * 1.9, 9.9, -3.6)], ra=0.12, sides=3, hint=(0, 1, 0)))
    for s in (-1, 1):
        F = add(F, "beakbase%d" % s, ck.ellipsoid("head", "feathl", (s * 0.9, 8.6, -3.2), (0.5, 0.45, 0.6), segs=5, rings=2))
    F = add(F, "neckfluff", ck.ellipsoid("head", "down", (0.0, 7.2, -2.0), (1.4, 1.0, 1.2), segs=5, rings=2))
    return F


if __name__ == "__main__":
    from dens import run_mob
    run_mob("peak_fledgling", "ch3/peak_fledgling.py", extra, letter="b")
