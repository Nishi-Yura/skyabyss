# -*- coding: utf-8 -*-
"""都の作風のラフ 3 案 (同じ一角を三つの作風で)。

python -I tools/capital/style_roughs.py   → data/skyabyss/opening/capital_style_{a,b,c}.json
撮影: ./gradlew runShots -Pscript=tools/shots/capital_style.txt (平らな世界へ skyabyss dev ch1 place で置く)
一角: 北に町家の並び、東の角に鐘の塔、南に大きな館 (張り出しの破風と隅の小塔)、南東に店の並び、真ん中に広場。
"""
from __future__ import annotations

import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import vox  # noqa: E402
from vox import (Vox, Body, gable_roof, tower, turret, arcade, fountain, rosette, pave, flowerbed,  # noqa: E402
                 double_lamp, lamp_post, tree, bench, planter, bush)

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', '..')
DATA = os.path.join(ROOT, 'src', 'main', 'resources', 'data', 'skyabyss', 'opening')


def district(st) -> Vox:
    v = Vox(seed=7)
    pave(v, st, -3, -3, 64, 47)
    # ---- 北の町家の並び (戸は南の広場へ)
    rows = [  # x0, x1, floors, roof axis, extra
        (1, 9, 3, 'z', 'chimney'),
        (11, 19, 2, 'x', 'dormer'),
        (21, 29, 3, 'z', 'turret'),
        (31, 37, 2, 'z', 'chimney'),
        (39, 46, 2, 'x', 'dormer'),
    ]
    for i, (x0, x1, floors, axis, extra) in enumerate(rows):
        z0, z1 = 2, 13
        b = Body(v, st, x0, z0, x1, z1, 0, floors=floors, fh=5, bay=4, door_side='south',
                 ground='shop' if i in (1, 4) else 'window').build()
        gable_roof(v, st, x0, z0, x1, z1, b.top + 1, axis=axis, steep=True,
                   dormers=1 if extra == 'dormer' else 0,
                   chimney=(x0 + 2, z0 + 3) if extra == 'chimney' else None)
        if extra == 'turret':
            turret(v, st, x1, z1, b.top - 4, 7, half=1, rise=3)
        planter(v, st, x0 + 1, 0, z1 + 2)
        planter(v, st, x1 - 1, 0, z1 + 2)
    # ---- 東の角の鐘の塔
    tower(v, st, 53, 8, half=4, y0=0, h=24, belfry=True, spire_rise=3, door_side='south', clock=True)
    # ---- 南の館 (2 階、各階 6)。真ん中に張り出し、両隅に小塔
    hall = Body(v, st, 2, 30, 30, 42, 0, floors=2, fh=6, bay=4, door_side='north', door_at=14).build()
    gable_roof(v, st, 2, 30, 30, 42, hall.top + 1, axis='x', steep=True, dormers=2)
    fr = Body(v, st, 11, 25, 21, 31, 0, floors=3, fh=5, bay=4, door_side='north', interior=False,
              sides=('north', 'east', 'west')).build()
    gable_roof(v, st, 11, 25, 21, 31, fr.top + 1, axis='z', steep=True)
    for (tx, tz) in ((2, 30), (30, 30)):
        turret(v, st, tx, tz, 0, hall.top + 4, half=1, rise=3)
    # ---- 南東の店の並び (前にアーケード)
    shops = Body(v, st, 35, 34, 47, 42, 0, floors=2, fh=5, bay=4, door_side='north', ground='shop').build()
    gable_roof(v, st, 35, 34, 47, 42, shops.top + 1, axis='x', steep=True, chimney=(45, 37))
    arcade(v, st, 34, 32, 48, y0=0, h=4, step=3)
    # ---- 広場 (中心 (32, 21))
    rosette(v, st, 32, 21, 6)
    fountain(v, st, 32, 21, r=3)
    for (x, z) in ((24, 17), (40, 17), (24, 26), (40, 26)):
        double_lamp(v, st, x, 0, z, h=4, axis='x')
    bench(v, st, 28, 0, 28, 'south', 3)
    bench(v, st, 34, 0, 28, 'south', 3)
    # ---- 東の庭
    flowerbed(v, st, 50, 17, 60, 27)
    tree(v, 54, 0, 21, st.trunk, st.leaves, h=6, r=2)
    tree(v, 58, 0, 25, st.trunk2, st.leaves2, h=5, r=2)
    flowerbed(v, st, 51, 31, 60, 43, density=0.45, shrubs=0.2)
    tree(v, 55, 0, 37, st.trunk2, st.leaves2, h=6, r=2)
    # ---- 西の小さな花壇
    for (x, z) in ((6, 19),):
        flowerbed(v, st, x - 4, z - 3, x + 4, z + 3, density=0.6)
        tree(v, x, 0, z, st.trunk, st.leaves, h=5, r=2)
    for x in range(2, 62, 8):
        lamp_post(v, st, x, 0, 46, h=4)
    v.connect()
    return v


def main():
    for key, st in vox.STYLES.items():
        v = district(st)
        data = v.runs({'rect': [-4, -4, 65, 48]})
        path = os.path.join(DATA, 'capital_style_%s.json' % key)
        vox.save(path, data)
        print(key, len(v.b), 'cells', len(data['palette']), 'states', os.path.getsize(path) // 1024, 'KB')


if __name__ == '__main__':
    main()
