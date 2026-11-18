# 設計（*_bold.py の build）の位置・回転だけを、取り込み済みの模型（筆を入れた絵つき）へ写す。大きさ（UV の画素数）が同じことを確かめる
import json, math, sys
from pathlib import Path
build_dir, src, out = Path(sys.argv[1]), Path(sys.argv[2]), Path(sys.argv[3])
i = src.stem
new = {c['name']: c for c in json.load(open(build_dir / f'{i}.json', encoding='utf-8'))}
d = json.loads(src.read_text(encoding='utf-8'))
ce = lambda a, b: [max(1, math.ceil(b[k] - a[k] - 1e-6)) for k in range(3)]
moved = 0
for e in d['elements']:
    c = new.get(e['name'])
    if c is None:
        sys.exit(f'{i}: no design cube for {e["name"]}')
    if ce(e['from'], e['to']) != ce(c['from'], c['to']):
        sys.exit(f'{i}: size differs for {e["name"]}: {ce(e["from"], e["to"])} vs {ce(c["from"], c["to"])}')
    if [round(v, 3) for v in e['from'] + e['to']] != [round(v, 3) for v in c['from'] + c['to']]:
        moved += 1
    e['from'], e['to'], e['origin'] = c['from'], c['to'], c['origin']
    e['rotation'] = c['rotation']
names = [e['name'] for e in d['elements']]
assert len(names) == len(set(names)) or i == 'ember_war_axe'
out.write_text(json.dumps(d, ensure_ascii=False), encoding='utf-8')
print(i, 'cubes', len(names), 'moved', moved)
