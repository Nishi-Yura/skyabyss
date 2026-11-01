# -*- coding: utf-8 -*-
"""骨の笛 (bone_flute) の密度上げ。採用案 A 笛の頭蓋（取り込み 340 → 目標 733〜1,026）。

足したもの（骨と色は元のまま。head / jaw / tail）:
- 眉の骨の張り（calc の楕円体。左右）と、頬の骨（bone。左右）
- 歯（calc の錐。顎の縁に六本。jaw の骨に載せる）
- 笛の管の輪（calc の細い輪。二段）
- 尾の節の輪（bone の細い輪。二段）
"""
import math
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from dens import add  # noqa: E402


def extra(o, F, eyes):
    ck = o
    for s in (-1, 1):
        F = add(F, "brow%d" % s, ck.ellipsoid("head", "calc", (s * 1.0, 8.4, -2.4), (0.7, 0.35, 0.5), segs=6, rings=2))
        F = add(F, "cheek%d" % s, ck.ellipsoid("head", "bone", (s * 1.9, 6.4, -1.8), (0.6, 0.6, 0.6), segs=5, rings=2))
    for j, x in enumerate((-1.2, -0.7, -0.2, 0.3, 0.8, 1.3)):
        F = add(F, "tooth%d" % j, ck.spike("jaw", "calc", (x, 5.3, -2.3 + 0.1 * (j % 2)), (x * 0.1, -0.4, -0.6), 0.9, 0.16, sides=3, rings=1))
    for j, z in enumerate((-3.2, -4.6)):
        F = add(F, "pipering%d" % j, ck.tube("jaw", "calc", [(math.cos(a) * 0.62, 3.6 + math.sin(a) * 0.62, z) for a in [i / 8 * 2 * math.pi for i in range(9)]],
                                             ra=0.14, sides=3, hint=(0, 0, 1)))
    for j, z in enumerate((4.2, 5.0)):
        F = add(F, "tailring%d" % j, ck.tube("tail", "bone", [(math.cos(a) * 0.6, 2.4 + math.sin(a) * 0.6, z) for a in [i / 8 * 2 * math.pi for i in range(9)]],
                                             ra=0.14, sides=3, hint=(0, 1, 0)))
    # 頭蓋の稜（上を前後に走る骨の筋）と、鼻の骨の下の張り
    F = add(F, "crest", ck.tube("head", "calc", [(0.0, 9.4, -1.8), (0.0, 9.7, 0.0), (0.0, 9.2, 1.8)], ra=[0.3, 0.4, 0.22], sides=4, hint=(1, 0, 0)))
    for j, z in enumerate((-1.0, 0.0, 1.0)):
        F = add(F, "crestt%d" % j, ck.spike("head", "bone", (0.0, 9.6, z * 0.8 - 0.2), (0.0, 1.0, 0.0), 0.8, 0.16, sides=3, rings=1))
    F = add(F, "nasalbulge", ck.ellipsoid("head", "calc", (0.0, 5.8, -2.8), (0.45, 0.4, 0.4), segs=5, rings=2))
    return F


if __name__ == "__main__":
    from dens import run_mob
    run_mob("bone_flute", "abyss/bone_flute.py", extra, letter="a")
