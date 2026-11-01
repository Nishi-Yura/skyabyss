# -*- coding: utf-8 -*-
"""骨拾い (bone_picker) の密度上げ。採用案 A 骨の山（取り込み 472 → 目標 733〜1,026）。

足したもの（骨と色は元のまま。body / head / leg_*）:
- 体の背の肋の弧（calc。三本。体の表面に沿う）
- 骨の束の残りの二本（calc と bone。背に載せ、根元は体に沈める）
- 頭の角（bone の錐。左右に一本ずつ）と、顎の下の骨（calc の楕円体）
- 脚の先の爪（calc の錐。六本）
- 体の上の骨の欠片（bone の小さな錐。四本）
"""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from dens import add  # noqa: E402


def extra(o, F, eyes):
    ck = o
    for j, z in enumerate((-1.8, -0.2, 1.4)):
        F = add(F, "archrib%d" % j, ck.tube("body", "calc", [(-2.2, 2.8, z), (0.0, 4.7, z), (2.2, 2.8, z)], ra=0.26, sides=4, hint=(0, 0, 1)))
    F = add(F, "bonebundle4", ck.tube("body", "calc", [(-0.2, 3.1, -1.9), (-0.8, 4.8, -1.5), (-1.6, 5.8, -1.0)], ra=[0.36, 0.32, 0.2], sides=6, hint=(0, 1, 0)))
    F = add(F, "bonebundle5", ck.tube("body", "bone", [(1.4, 3.2, -0.9), (1.9, 4.4, -0.4), (2.4, 5.2, 0.2)], ra=[0.34, 0.3, 0.18], sides=6, hint=(0, 1, 0)))
    for s in (-1, 1):
        F = add(F, "hornp%d" % s, ck.spike("head", "bone", (s * 0.9, 3.2, -2.6), (s * 0.4, 0.8, -0.4), 1.5, 0.26, sides=4, rings=2))
    F = add(F, "chin", ck.ellipsoid("head", "calc", (0.0, 1.0, -3.6), (0.9, 0.5, 0.9), segs=6, rings=2))
    for nm, s, i in o.LEGS:
        zz = o.LEG_Z[i]
        for j, dx in enumerate((-0.2, 0.2)):
            F = add(F, "claw_%s%d_%d" % (nm, i, j), ck.spike("body", "calc", (s * 2.9 + dx, 0.0, zz + 0.9), (s * 0.3, -0.5, 0.3), 0.7, 0.14, sides=3, rings=1))
    for j, (x, y, z) in enumerate(((-1.0, 5.6, 0.0), (0.4, 5.9, 0.9), (1.2, 4.8, -1.8), (-0.6, 4.9, 1.8))):
        F = add(F, "flake%d" % j, ck.spike("body", "bone", (x, y - 0.3, z), (x * 0.1, 1.0, z * 0.1), 0.9, 0.2, sides=3, rings=1))
    return F


if __name__ == "__main__":
    from dens import run_mob
    run_mob("bone_picker", "abyss/bone_picker.py", extra, letter="a")
