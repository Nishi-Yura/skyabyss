# -*- coding: utf-8 -*-
"""落ちた羽根 (fallen_plume) の密度上げ。一品一案（取り込み 486 → 目標 1,326〜1,856）。

足したもの（骨と色は元のまま。body / quills）:
- 羽の縁の刻み（縁の外へ細い棘を、片側ごとに 12 本ずつ）
- 羽の裏側の面（片側ごとに二枚。表より少し後ろへずらす）
- 軸の金の輪（一定の間隔で四つ）
- 羽の下の小さな副羽（左右に一枚ずつ。低い所に）
- 羽根の矢のまわりに落ちた小羽（六枚）
- 副羽の二段目（左右に一枚ずつ）
- 軸の先の細い羽毛（左右と先の金の毛先）
"""
import math
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from dens import kit, add  # noqa: E402


def extra(o, F, eyes):
    ck = kit()
    # 羽の縁の刻み
    def width(y):
        return 3.6 if y < 21 else (5.0 if y < 33 else max(0.4, 3.4 - (y - 33.4) * 0.35))
    for s in (-1, 1):
        for j, y in enumerate((12.5, 14.0, 16.5, 19.0, 22.0, 24.5, 26.5, 29.0, 31.5, 34.0, 36.5, 39.0)):
            F = add(F, "barb%d%d" % (s, j), ck.spike("body", "gold" if j % 2 else "vane2", (s * (width(y) * 0.5 + 0.2), y, -0.3 + 0.1 * (j % 3)),
                                                    (s, 0.25, 0.1), 1.0, 0.17, sides=4, rings=1))
        # 裏側の羽面（薄い板。表より後ろへずらす）
        F = add(F, "back%d_a" % s, ck.tube("body", "vane2", [(s * 1.6, 13.5, 0.7), (s * 3.2, 17.0, 0.9), (s * 3.6, 21.0, 0.8)], ra=[0.25, 0.3, 0.2], rb=[1.6, 1.8, 1.4], sides=4, hint=(0, 0, 1)))
        F = add(F, "back%d_b" % s, ck.tube("body", "vane2", [(s * 2.2, 25.0, 0.9), (s * 3.6, 29.5, 0.8), (s * 3.6, 34.0, 0.6)], ra=[0.25, 0.3, 0.2], rb=[1.5, 1.7, 1.0], sides=4, hint=(0, 0, 1)))
    # 軸の金の輪
    for j, y in enumerate((14.0, 20.5, 27.0, 34.0)):
        r = 0.7 - 0.1 * j
        F = add(F, "quillring%d" % j, ck.tube("body", "gold", [(math.cos(a) * r, y, math.sin(a) * r) for a in [i / 6 * 2 * math.pi for i in range(7)]],
                                              ra=0.14, sides=4, hint=(0, 1, 0)))
    # 羽の下の副羽（二段）
    for s in (-1, 1):
        F = add(F, "coverts%d" % s, ck.tube("body", "vane2", [(s * 0.6, 12.0, 0.2), (s * 2.6, 13.4, 0.5), (s * 3.4, 15.4, 0.4)],
                                            ra=[0.2, 0.2, 0.05], rb=[1.2, 1.0, 0.3], sides=4, hint=(0, 1, 0)))
        F = add(F, "coverts2_%d" % s, ck.tube("body", "vane", [(s * 0.5, 17.0, 0.0), (s * 2.2, 18.2, 0.2), (s * 2.8, 19.8, 0.1)],
                                              ra=[0.2, 0.2, 0.05], rb=[1.0, 0.9, 0.3], sides=4, hint=(0, 1, 0)))
    # 羽根の矢の根元の小羽
    for i in range(8):
        a = i * math.pi * 2 / 8 + 0.6
        cx, cz = math.cos(a) * 3.6, math.sin(a) * 3.6
        F = add(F, "fallen%d" % i, ck.tube("body", "vane", [(cx, 11.8, cz), (cx * 1.1, 12.6, cz * 1.1), (cx * 1.2, 13.6, cz * 1.2)],
                                           ra=0.15, rb=[0.8, 0.5, 0.1], sides=4, hint=(0, 1, 0)))
    # 先の細い羽毛（軸の先）
    for s in (-1, 1):
        F = add(F, "wisp%d" % s, ck.tube("body", "vane2", [(s * 0.8, 37.0, 0.6), (s * 1.8, 41.0, 0.8), (s * 2.4, 45.0, 0.6)], ra=[0.2, 0.14, 0.04], rb=[0.9, 0.6, 0.1], sides=4, hint=(0, 0, 1)))
    F = add(F, "tiptuft", ck.spike("body", "gold", (0.0, 42.2, 0.8), (0.0, 1.0, 0.1), 2.0, 0.25, sides=4, rings=2))
    return F


if __name__ == "__main__":
    from dens import run_mob
    run_mob("fallen_plume", "cloud/fallen_plume.py", extra, letter="a")
