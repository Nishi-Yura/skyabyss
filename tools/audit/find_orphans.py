#!/usr/bin/env python3
# -*- coding: utf-8 -*-
# 使われていない資源の候補を挙げる (消す前の下調べ。2026-11-05)。消すのは人が確かめてから。
#   音       : sounds/*.ogg のうち、どの sounds.json からも指されていないもの (確か)
#   メッシュ : lowpoly/*.json のうち、名前 (と名前の頭の部分) が Java・JSON のどこにも出てこないもの (候補)
#   絵       : textures/**.png のうち、名前 (拡張子なし) がどこにも出てこないもの (候補)
# 名前の出どころ: src/main/java の全文、src/main/resources の JSON の全文、tools/ の生成スクリプトは数えない (遊びで読まれないため)
#   python -I tools/audit/find_orphans.py [--json out.json]
import json
import os
import re
import sys

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..'))
RES = os.path.join(ROOT, 'src', 'main', 'resources')
ASSETS = os.path.join(RES, 'assets')
JAVA = os.path.join(ROOT, 'src', 'main', 'java')


def read_all(base, exts):
    out = []
    for dp, _, fs in os.walk(base):
        for f in fs:
            if f.endswith(exts):
                try:
                    out.append(open(os.path.join(dp, f), encoding='utf-8').read())
                except Exception:
                    pass
    return out


def main():
    java = '\n'.join(read_all(JAVA, ('.java',)))
    jsons = '\n'.join(read_all(RES, ('.json', '.mcmeta')))
    corpus = java + '\n' + jsons
    words = set(re.findall(r'[A-Za-z0-9_./-]+', corpus))
    tokens = set()
    for w in words:
        for part in re.split(r'[./:]', w):
            if part:
                tokens.add(part)

    def seen(name):
        return name in tokens or name in words

    def prefix_seen(name):
        # 名前の後ろの _xxx を一つずつ外して、残りが出てくるか (stem + "_s1" のような組み立て)
        parts = name.split('_')
        for k in range(len(parts) - 1, 0, -1):
            if seen('_'.join(parts[:k])):
                return '_'.join(parts[:k])
        return None

    report = {'sounds': [], 'meshes': [], 'mesh_prefix_only': [], 'textures': [], 'texture_prefix_only': []}
    # 音
    for ns in sorted(os.listdir(ASSETS)):
        sd = os.path.join(ASSETS, ns, 'sounds')
        sj = os.path.join(ASSETS, ns, 'sounds.json')
        if not os.path.isdir(sd):
            continue
        refs = set()
        for other in os.listdir(ASSETS):
            p = os.path.join(ASSETS, other, 'sounds.json')
            if os.path.exists(p):
                for ev, d in json.load(open(p, encoding='utf-8')).items():
                    for s in d.get('sounds', []):
                        name = s['name'] if isinstance(s, dict) else s
                        if isinstance(s, dict) and s.get('type') == 'event':
                            continue
                        a, _, b = name.partition(':')
                        refs.add((a, b) if b else ('minecraft', a))
        for dp, _, fs in os.walk(sd):
            for f in fs:
                if f.endswith('.ogg'):
                    rel = os.path.relpath(os.path.join(dp, f), sd).replace(os.sep, '/')[:-4]
                    if (ns, rel) not in refs:
                        report['sounds'].append((ns + ':' + rel, os.path.getsize(os.path.join(dp, f))))
    # メッシュ (lowpoly の直下と下の階層)
    lp = os.path.join(ASSETS, 'skyabyss', 'lowpoly')
    for dp, _, fs in os.walk(lp):
        for f in fs:
            if not f.endswith('.json'):
                continue
            rel = os.path.relpath(os.path.join(dp, f), lp).replace(os.sep, '/')[:-5]
            size = os.path.getsize(os.path.join(dp, f))
            parts = rel.split('/')
            # items/<id>/<tier> や armor/<set>/<piece> は、id か set が出てくれば使われる
            key = parts[1] if len(parts) >= 3 else parts[-1]
            if seen(key) or seen(rel):
                continue
            pre = prefix_seen(key)
            (report['mesh_prefix_only'] if pre else report['meshes']).append((rel, size, pre))
    # 絵
    for ns in sorted(os.listdir(ASSETS)):
        td = os.path.join(ASSETS, ns, 'textures')
        if not os.path.isdir(td):
            continue
        for dp, _, fs in os.walk(td):
            for f in fs:
                if not f.endswith('.png'):
                    continue
                rel = os.path.relpath(os.path.join(dp, f), td).replace(os.sep, '/')[:-4]
                base = rel.split('/')[-1]
                size = os.path.getsize(os.path.join(dp, f))
                if seen(base) or seen(rel):
                    continue
                pre = prefix_seen(base)
                (report['texture_prefix_only'] if pre else report['textures']).append((ns + ':' + rel, size, pre))
    for k, v in report.items():
        total = sum(x[1] for x in v)
        print('%-20s %4d files  %7.2f MB' % (k, len(v), total / 1e6))
    if '--json' in sys.argv:
        out = sys.argv[sys.argv.index('--json') + 1]
        json.dump(report, open(out, 'w', encoding='utf-8'), ensure_ascii=False, indent=1)
        print('written', out)


if __name__ == '__main__':
    main()
