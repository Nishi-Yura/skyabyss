# -*- coding: utf-8 -*-
"""家の型の見本 (massing.py)。型 × 屋根を一列に並べた設計図 town_rough_houses.json。

python -I tools/capital/house_sheet.py [whale]   → src/main/resources/data/skyabyss/opening/town_rough_houses.json
並び (西から東、戸は南): 平入りの町家 / 妻入りの町家 / 張り出しの家 / L 字の家 / 塔の家 / 小屋 / 段の妻の商家 と、屋根の形 6 つ (急・緩・反り・寄棟・半切妻・段の妻)
"""
from __future__ import annotations

import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import vox  # noqa: E402
import town as T  # noqa: E402
import massing as M  # noqa: E402
from town_plans import PlanTown  # noqa: E402
from region_town import _check_blocks  # noqa: E402

ROW1 = [  # (型, 間口, 奥行き, 階, 屋根)
    ('row', 9, 8, 2, 'steep'), ('gable', 7, 10, 3, 'steep'), ('jetty', 8, 9, 3, 'bell'), ('ell', 11, 8, 2, 'kick'),
    ('tower', 7, 7, 4, 'steep'), ('cottage', 10, 7, 1, 'half_hip'), ('merchant', 9, 10, 3, 'stepped'),
]
ROW2 = [('row', 9, 8, 2, k) for k in ('steep', 'low', 'bell', 'hip', 'half_hip', 'stepped')]


def main():
    region = sys.argv[1] if len(sys.argv) > 1 else 'whale'
    t = PlanTown(region, 'a')
    t.streets = []
    st = t.st
    for (row, z0) in ((ROW1, -14), (ROW2, 10)):
        x = -60
        for i, (kind, w, d, floors, rk) in enumerate(row):
            rect = (x, z0, x + w - 1, z0 + d - 1)
            mat = [st.roof, st.roof_alts[0], st.roof_alts[-1]][i % 3] if row is ROW1 else st.roof
            M.build_house(t, kind, rect, 'south', floors, rk, mat=mat, extras=('dormer', 'chimney') if i % 2 else ())
            x += w + 8
    for x in range(-64, 64):
        for z in range(-26, 26):
            if t.v.get(x, -1, z) is None:
                t.v.set(x, -1, z, 'grass_block[snowy=false]')
    t.drop_floaters()
    t.fix_stairs()
    t.v.connect()
    data = t.v.runs({'rect': [-64, -26, 64, 26], 'level': True, 'ring': 4, 'fillWater': False, 'height': 40})
    data['site'] = 'town_rough_houses'
    _check_blocks(data, region)
    path = os.path.join(T.DATA, 'town_rough_houses.json')
    vox.save(path, data)
    print('wrote', path, os.path.getsize(path) // 1024, 'KB')


if __name__ == '__main__':
    main()
