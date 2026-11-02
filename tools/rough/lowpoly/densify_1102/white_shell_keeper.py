# -*- coding: utf-8 -*-
"""白い殻の番人 (white_shell_keeper) の密度上げ。採用案 A 巻貝（取り込み 336 → 目標 733〜1,026）。

足したもの（骨と色は元のまま。shell / body / claw_*）:
- 殻の表面の帯（saltc の細い輪。三本。殻の円錐に沿って）
- 殻の頂の棘（sea の錐。三本）
- 身の下の裾（saltc の楕円体。四つ）
- 鋏の付け根の瘤（salt）と、鋏の節の輪（saltc の細い輪）
"""
import math
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from dens import add  # noqa: E402


def extra(o, F, eyes):
    ck = o

    def circ(y, rad, cz=0.0, n=14):
        return [(rad * math.cos(2 * math.pi * k / n), y, cz + rad * math.sin(2 * math.pi * k / n)) for k in range(n)]

    for j, (y, rad) in enumerate(((3.4, 4.5), (5.4, 3.9), (7.4, 2.7))):
        F = add(F, "band%d" % j, ck.tube("shell", "saltc", circ(y, rad + 0.05, 0.3 + 0.1 * j, 12), ra=0.22, sides=4, hint=(0, 1, 0)))
    for j, (x, y, z) in enumerate(((0.0, 8.6, 0.8), (-0.8, 8.0, 1.5), (0.9, 7.8, 1.6))):
        F = add(F, "crown%d" % j, ck.spike("shell", "sea", (x, y, z), (x * 0.1, 1.0, 0.1), 1.2, 0.22, sides=4, rings=1))
    for j, (x, z) in enumerate(((-1.8, 0.4), (1.8, 0.4), (0.0, -1.8), (0.0, 2.2))):
        F = add(F, "skirt%d" % j, ck.ellipsoid("body", "saltc", (x, 0.4, z), (0.7, 0.4, 0.7), segs=5, rings=2))
    for s, nm in ((-1, "l"), (1, "r")):
        F = add(F, "clawbase" + nm, ck.ellipsoid("claw_" + nm, "salt", (s * 2.8, 1.4, -2.2), (0.7, 0.6, 0.7), segs=5, rings=2))
        F = add(F, "clawring" + nm, ck.tube("claw_" + nm, "saltc", [(s * 3.6, 0.8, -4.2), (s * 3.9, 0.6, -4.6)], ra=0.25, sides=4, hint=(0, 1, 0)))
    return F


if __name__ == "__main__":
    from dens import run_mob
    run_mob("white_shell_keeper", "abyss/white_shell_keeper.py", extra, letter="a")
