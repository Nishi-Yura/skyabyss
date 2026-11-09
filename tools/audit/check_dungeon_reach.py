# -*- coding: utf-8 -*-
"""ダンジョンの層 (data/skyabyss/dungeon/*.json) を、入口 (entry) から空き・水・格子をたどって、印 (部屋・敵・扉・next) に届くかを見る。
2026-11-09 の声「沈んだ神殿の 2 層、奥がブロックで閉じられて行けない」: 洞と坑道の縁のゆらぎで、あいだに岩が残っていた。
印が床のブロックの上に置かれているものは「届かない」に出るが、すぐ隣まで届いていれば歩ける (next が出たら本当に塞がっている)

    python -X utf8 tools/audit/check_dungeon_reach.py src/main/resources/data/skyabyss/dungeon/*.json
"""
import json, sys
from collections import deque
SOLIDS = ('stairs',)  # stairs walkable-ish; treat as passable for feet? treat as solid floor too
def load(p):
    d = json.load(open(p, encoding='utf-8'))
    pal = d['palette']; cells = {}
    for r in d['runs']:
        y, z, x = r[0], r[1], r[2]
        seq = r[3:]
        for i in range(0, len(seq), 2):
            idx, n = seq[i], seq[i+1]
            if idx >= 0:
                for k in range(n):
                    cells[(x+k, y, z)] = pal[idx]
            x += n
    return d, cells
def passable(s):
    if s is None: return False   # unwritten = whatever was there (stone in the dungeon world)
    b = s.split('[')[0]
    return b in ('minecraft:air','minecraft:water','minecraft:light','minecraft:brown_mushroom','minecraft:iron_bars','minecraft:glow_lichen','minecraft:sea_pickle','minecraft:dead_brain_coral_fan','minecraft:dead_tube_coral','minecraft:pointed_dripstone','minecraft:soul_lantern') or 'coral_fan' in b
def run(p):
    d, cells = load(p)
    m = d['marks']
    start = tuple(m['entry'])
    seen = {start}; q = deque([start])
    while q:
        c = q.popleft()
        for dx,dy,dz in ((1,0,0),(-1,0,0),(0,1,0),(0,-1,0),(0,0,1),(0,0,-1)):
            n = (c[0]+dx,c[1]+dy,c[2]+dz)
            if n not in seen and passable(cells.get(n)):
                seen.add(n); q.append(n)
    bad = [k for k,v in m.items() if not k.startswith('view') and tuple(v) not in seen and not k.startswith('chest') and k not in ('leave','cp')]
    print(p.split('/')[-1], 'cells', len(seen), 'unreached marks:', bad)
    return seen, cells, m
if __name__ == '__main__':
    for p in sys.argv[1:]:
        run(p)
