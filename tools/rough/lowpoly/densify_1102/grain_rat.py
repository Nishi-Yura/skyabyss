# -*- coding: utf-8 -*-
"""穀ねずみ (grain_rat) の密度上げ。ラフの唯一の案（取り込み 606 → 目標 989〜1,385）。

足したもの（骨と色は元のまま。body / head / ear_* / fore_* / hind_* / tail）:
- 背の毛束（fur。背の上に七つ。背骨に沿う）と、脇腹の毛のふくらみ（fur。左右に二つずつ）
- 前足と後ろ足の指（pink の細い錐。各足に三本）と、後ろ足の関節の毛（fur）
- 耳の内側（dark の薄い楕円体。左右）と、鼻の横のひげ（belly。各側二本ずつ足す）
- 尾の節の輪（pink の細い輪。三本）
- 麦の穂の粒（wheat。穂の先に三つ）
"""
import math
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from dens import add  # noqa: E402


def extra(o, F, eyes):
    ck = o
    for j, z in enumerate((-3.6, -2.4, -1.2, 0.0, 1.2, 2.4, 3.6)):
        F = add(F, "backfur%d" % j, ck.ellipsoid("body", "fur", (0.0, 7.2, z), (0.7, 0.8, 0.6), segs=5, rings=2))
    for s in (-1, 1):
        for j, (y, z) in enumerate(((4.4, -1.2), (4.2, 1.4))):
            F = add(F, "flankfur%d%d" % (s, j), ck.ellipsoid("body", "fur", (s * 3.1, y, z), (0.6, 0.7, 0.7), segs=5, rings=2))
    for s in (-1, 1):
        for j, dx in enumerate((-0.35, 0.0, 0.35)):
            F = add(F, "forec%d%d" % (s, j), ck.spike("fore_l" if s > 0 else "fore_r", "pink", (s * 1.9 + dx, 0.5, -4.3), (dx * 0.3, -0.5, -0.6), 0.8, 0.16, sides=3, rings=1))
            F = add(F, "hindc%d%d" % (s, j), ck.spike("hind_l" if s > 0 else "hind_r", "pink", (s * 2.5 + dx, 0.2, 1.5), (dx * 0.3, -0.5, 0.6), 0.8, 0.16, sides=3, rings=1))
        F = add(F, "hindknee%d" % s, ck.ellipsoid("hind_l" if s > 0 else "hind_r", "fur", (s * 2.3, 3.0, 0.9), (0.5, 0.6, 0.5), segs=5, rings=2))
    for s in (-1, 1):
        F = add(F, "earin%d" % s, ck.ellipsoid("ear_l" if s > 0 else "ear_r", "dark", (s * 1.6, 7.6, -5.1), (0.7, 0.8, 0.2), segs=5, rings=2))
        for k2 in range(2):
            F = add(F, "muzzle%d%d" % (s, k2), ck.tube("head", "belly", [(s * 0.8, 4.4 - 0.4 * k2, -8.0), (s * 2.2, 4.2 - 0.6 * k2, -8.4)], ra=[0.06, 0.04], sides=3, hint=(0, 1, 0)))
    for j, (x, y, z) in enumerate(((0.0, 4.9, 9.0), (0.4, 4.6, 11.0))):
        pts = [(x + 0.6 * math.cos(a), y + 0.6 * math.sin(a), z) for a in [i / 6 * 2 * math.pi for i in range(7)]]
        F = add(F, "tailring%d" % j, ck.tube("tail", "pink", pts, ra=0.1, sides=3, hint=(0, 0, 1)))
    for j, (x, y, z) in enumerate(((3.4, 4.4, -7.6), (3.8, 4.8, -7.8), (4.1, 4.2, -7.5))):
        F = add(F, "grain%d" % j, ck.ellipsoid("head", "wheat", (x, y, z), (0.35, 0.35, 0.35), segs=4, rings=1))
    return F


if __name__ == "__main__":
    from dens import run_mob
    run_mob("grain_rat", "foes/grain_rat.py", extra, letter="a")
