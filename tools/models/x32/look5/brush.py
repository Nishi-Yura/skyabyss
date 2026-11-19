# -*- coding: utf-8 -*-
"""筆の 3 回の座標を設計から選ぶ（Blockbench の paint_with_brush にそのまま渡す）。作業フォルダで:  python -I brush.py <設計モジュール> <id>
1 回目 = 主の素材の大きな部品 3 つの上の縁に明るい線、2 回目 = 差し色の部品 2 つの上の縁に明るい縁、3 回目 = 主の大きな部品 2 つの下の縁に影。
出力は 1 行ずつ「色 座標の JSON」。"""
import importlib
import json
import sys
from collections import Counter
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import lib  # noqa: E402

mod, item = sys.argv[1], sys.argv[2]
m = getattr(importlib.import_module(mod), item)()
uv = {c['name']: {f['face']: f['uv'] for f in c['faces']} for c in json.load(open(f'{item}.json', encoding='utf-8'))}
parts = [p for p in m.parts if p['mat'] not in ('string',) and not p['name'].endswith('_glow') and p['name'] in uv]


def area(p):
    u0, v0, u1, v1 = uv[p['name']]['south']
    return abs(u1 - u0) * abs(v1 - v0)


parts = [p for p in parts if p['mat'] != 'decal']
tot = Counter()
for p in parts:
    tot[p['mat']] += area(p)
main = tot.most_common(1)[0][0]
acc = m.accent if m.accent != main and tot[m.accent] else next((k for k, _ in tot.most_common() if k != main), None)


def rows(names, which):
    pts = []
    for n in names:
        for f in ('south', 'north'):
            u0, v0, u1, v1 = uv[n][f]
            y = min(v0, v1) if which == 'top' else max(v0, v1) - 1
            pts += [(x, int(y)) for x in range(int(min(u0, u1)), int(max(u0, u1)))]
    pts = list(dict.fromkeys(pts))[:90]
    return json.dumps([{'x': x + .5, 'y': y + .5} for x, y in pts], separators=(',', ':'))


big = [p['name'] for p in sorted((p for p in parts if p['mat'] == main), key=area, reverse=True)]
ramp = lib.RAMPS[main]
print(ramp[-2], rows(big[:3], 'top'))
if acc:
    ac = [p['name'] for p in sorted((p for p in parts if p['mat'] == acc), key=area, reverse=True)][:2]
    print(lib.RAMPS[acc][-1], rows(ac, 'top'))
else:
    print(ramp[-1], rows(big[3:5], 'top'))
print(ramp[1], rows(big[:2], 'bottom'))
