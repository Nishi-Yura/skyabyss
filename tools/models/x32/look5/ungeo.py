# geo.json で読み直した模型の書き出し: 群をほどき、箱の名前を元の模型から戻す。
# 照合は面の UV（south / up / east のどれか一致）→ 形（from/to）の順。名前を手で付けた箱（g_ で始まらない）はそのまま。
import json, sys
from pathlib import Path

p, orig = Path(sys.argv[1]), Path(sys.argv[2])
d = json.loads(p.read_text(encoding='utf-8'))
src = json.loads(orig.read_text(encoding='utf-8'))['elements']


def key(e, f):
    fc = e['faces'].get(f)
    return (f, tuple(round(v, 3) for v in fc['uv'])) if fc else None


by = {}
for e in src:
    for f in ('south', 'up', 'east'):
        by.setdefault(key(e, f), e['name'])
    by.setdefault(('geo', tuple(map(lambda v: round(v, 3), e['from'] + e['to']))), e['name'])
left = []
for e in d['elements']:
    if not e['name'].startswith('g_'):
        continue
    n = None
    for f in ('south', 'up', 'east'):
        n = n or by.get(key(e, f))
    n = n or by.get(('geo', tuple(map(lambda v: round(v, 3), e['from'] + e['to']))))
    if n:
        e['name'] = n
    else:
        left.append(e['uuid'])
d['groups'] = []
d['outliner'] = [e['uuid'] for e in d['elements']]
p.write_text(json.dumps(d, ensure_ascii=False), encoding='utf-8')
names = [e['name'] for e in d['elements']]
print('ok', len(names), 'unnamed:', len(left), 'dupes:', sorted({n for n in names if names.count(n) > 1}))
