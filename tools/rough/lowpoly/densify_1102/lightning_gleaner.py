# -*- coding: utf-8 -*-
"""雷拾い (lightning_gleaner) の密度上げ。採用案 C 避雷の鶴（取り込み 404 → 目標 545〜763）。

足したもの（骨と色は元のまま。body / neck / head / wing_* / leg_* / tail）:
- 首の銅の輪（wire。首の付け根と中ほどに二本ずつ）
- 胸の下の白い羽毛の盛り（white。胸に一つ、首の根元に一つ）
- 翼の下端の羽の棘（white。左右に三本ずつ）
- 尾の羽の先の棘（white。三本）
- 冠の先の小さな光（glow。二つ目の冠）
"""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from dens import add  # noqa: E402


def extra(o, F, eyes):
    ck = o
    for j, (y, z) in enumerate(((17.0, -3.2), (19.6, -3.8))):
        F = add(F, "neckwire%d" % j, ck.tube("neck", "wire", [(-1.0, y, z), (0.0, y + 0.25, z - 0.2), (1.0, y, z)], ra=0.22, sides=3, hint=(0, 1, 0)))
    F = add(F, "breast", ck.ellipsoid("body", "white", (0.0, 13.6, -1.8), (1.2, 0.9, 1.1), segs=6, rings=2))
    F = add(F, "neckfluff", ck.ellipsoid("neck", "white", (0.0, 16.6, -2.6), (1.1, 0.9, 1.0), segs=5, rings=2))
    for s, b in ((-1, "wing_l"), (1, "wing_r")):
        for j, z in enumerate((-1.2, 0.0, 1.2)):
            F = add(F, "wfeather%d%d" % (s, j), ck.spike(b, "white", (s * 2.7, 13.6, 0.8 + z), (s * 0.2, -1.0, 0.1), 1.2, 0.2, sides=3, rings=1))
    for j, dx in enumerate((-0.5, 0.0, 0.5)):
        F = add(F, "tailtip%d" % j, ck.spike("tail", "white", (dx, 14.8, 5.6), (dx * 0.4, 0.3, 1.0), 1.2, 0.2, sides=3, rings=1))
    F = add(F, "crownlight", ck.ellipsoid("head", "glow", (0.0, 27.0, -4.3), (0.3, 0.3, 0.3), segs=4, rings=1))
    return F


if __name__ == "__main__":
    from dens import run_mob
    run_mob("lightning_gleaner", "ch3/lightning_gleaner.py", extra, letter="c")
