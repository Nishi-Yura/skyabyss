# -*- coding: utf-8 -*-
"""設計図の palette のブロック名と状態の名前が、バニラ (1.21.1) にあるかを検める。

python -I tools/capital/check_states.py <設計図.json>...
バニラの blockstates は Minecraft のクライアント jar (gradle のキャッシュ) から読む。skyabyss: のブロックは確かめない。
"""
import json
import os
import re
import sys
import zipfile

JARS = [os.path.expanduser('~/.gradle/caches/neoformruntime/artifacts/minecraft_1.21.1_client.jar')]
# blockstates の json に出てこない (モデルを変えない) 状態
SILENT = {'waterlogged', 'powered', 'distance', 'persistent', 'snowy', 'signal_fire', 'lit', 'open', 'level', 'up',
          'north', 'south', 'east', 'west', 'hanging', 'half', 'shape', 'facing', 'type', 'axis', 'hinge', 'occupied', 'part', 'has_book', 'attachment', 'in_wall', 'moisture', 'age', 'face', 'stage', 'power', 'candles', 'cracked', 'slot_0_occupied', 'slot_1_occupied', 'slot_2_occupied', 'slot_3_occupied', 'slot_4_occupied', 'slot_5_occupied', 'bites', 'rotation', 'has_bottle_0', 'has_bottle_1', 'has_bottle_2'}


def load():
    known = {}
    for j in JARS:
        if not os.path.exists(j):
            continue
        with zipfile.ZipFile(j) as z:
            for n in z.namelist():
                m = re.match(r'assets/minecraft/blockstates/([a-z0-9_]+)\.json$', n)
                if not m:
                    continue
                d = json.loads(z.read(n))
                props = set()
                if 'variants' in d:
                    for k in d['variants']:
                        for kv in k.split(','):
                            if '=' in kv:
                                props.add(kv.split('=')[0])
                for part in d.get('multipart', []):
                    w = part.get('when', {})
                    for c in (w.get('OR') or w.get('AND') or [w]):
                        props.update(c.keys())
                known[m.group(1)] = props
    return known


def load_mod():
    """mod のブロック (skyabyss:) の状態の名前を assets の blockstates から。mod のものは厳しく見る (使える状態は json に出るものだけ)"""
    root = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', '..', 'src', 'main', 'resources', 'assets', 'skyabyss', 'blockstates')
    known = {}
    for fn in os.listdir(root):
        if not fn.endswith('.json'):
            continue
        d = json.load(open(os.path.join(root, fn), encoding='utf-8'))
        props = set()
        for k in d.get('variants', {}):
            for kv in k.split(','):
                if '=' in kv:
                    props.add(kv.split('=')[0])
        for part in d.get('multipart', []):
            w = part.get('when', {})
            for c in (w.get('OR') or w.get('AND') or [w]):
                props.update(c.keys())
        known[fn[:-5]] = props
    return known


def main(paths):
    known = load()
    mod = load_mod()
    if not known:
        print('no vanilla jar found; skipped')
        return 0
    bad = 0
    for p in paths:
        data = json.load(open(p, encoding='utf-8'))
        for s in data['palette']:
            name = s.split('[')[0]
            if name.startswith('skyabyss:'):
                n = name[len('skyabyss:'):]
                if n not in mod:
                    print('%s: unknown mod block %s' % (os.path.basename(p), s))
                    bad += 1
                elif '[' in s:
                    for kv in s[s.index('[') + 1:-1].split(','):
                        k = kv.split('=')[0]
                        if k not in mod[n] and k not in ('waterlogged',):
                            print('%s: mod block %s has no state %s (has %s)' % (os.path.basename(p), n, k, sorted(mod[n])))
                            bad += 1
                continue
            if not name.startswith('minecraft:'):
                continue
            n = name[len('minecraft:'):]
            if n not in known:
                print('%s: unknown block %s' % (os.path.basename(p), s))
                bad += 1
                continue
            if '[' in s:
                for kv in s[s.index('[') + 1:-1].split(','):
                    k = kv.split('=')[0]
                    if k not in known[n] and k not in SILENT:
                        print('%s: %s has no state %s' % (os.path.basename(p), n, k))
                        bad += 1
    print('checked', len(paths), 'blueprints,', bad, 'problems')
    return 1 if bad else 0


if __name__ == '__main__':
    sys.exit(main(sys.argv[1:]))
