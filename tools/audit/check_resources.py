#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""資源の参照の検査 (ゲームを起動しない。数秒)。
    python tools/audit/check_resources.py

見るもの (src/main/resources):
  - JSON・mcmeta が全部読めるか
  - モデルのテクスチャ参照 (skyabyss:) と親モデルが実在するか、blockstate が指すモデルが実在するか
  - sounds.json: 自前の ogg が実在するか、バニラのファイル名が資産の索引 (~/.gradle/caches/neoformruntime/assets/indexes) に実在するか
    (minecraft:block/stone/step1 のようなファイル名はバニラに無く、起動ログに「does not exist」が出て鳴らない。出来事 (type: event) で指すのが確実)
  - 看板の文 (sign.skyabyss.*.1〜4) の 1 行の幅が SignFit.MAX_LINE_WIDTH (90 px) 以内か (ja_jp・en_us。幅の表は SignFit と同じ)
  - lang の ja_jp と en_us の鍵がそろっているか
指摘が 1 つでもあれば終了コード 1。
"""
import json
import os
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
RES = os.path.join(ROOT, 'src', 'main', 'resources')
ASSETS = os.path.join(RES, 'assets')
MOD = os.path.join(ASSETS, 'skyabyss')
MAX_LINE_WIDTH = 90
# SignFit.ASCII_WIDTHS と同じ ('!' 〜 '~')
ASCII_WIDTHS = "2466662444626266666666666225656766666666466666666666666666464663666665662653666666646666664247"


def sign_width(text):
    w = 0
    for ch in text:
        cp = ord(ch)
        if cp == 32:
            w += 4
        elif 32 < cp < 127:
            w += int(ASCII_WIDTHS[cp - 33])
        else:
            w += 9
    return w


def rel(p):
    return os.path.relpath(p, ROOT).replace(os.sep, '/')


def main():
    fails = []
    # 1. JSON
    n = 0
    for dp, _, fs in os.walk(RES):
        for f in fs:
            if f.endswith(('.json', '.mcmeta')):
                n += 1
                p = os.path.join(dp, f)
                try:
                    json.load(open(p, encoding='utf-8'))
                except Exception as e:  # noqa
                    fails.append('json: %s: %s' % (rel(p), str(e)[:80]))
    print('json files: %d' % n)
    # 2. models / blockstates
    refs = 0
    for dp, _, fs in os.walk(os.path.join(MOD, 'models')):
        for f in fs:
            if not f.endswith('.json'):
                continue
            p = os.path.join(dp, f)
            try:
                j = json.load(open(p, encoding='utf-8'))
            except Exception:
                continue
            for v in (j.get('textures') or {}).values():
                if not isinstance(v, str) or v.startswith('#'):
                    continue
                ns, _, path = v.partition(':')
                if not path:
                    ns, path = 'minecraft', ns
                if ns != 'skyabyss':
                    continue
                refs += 1
                if not os.path.exists(os.path.join(MOD, 'textures', path + '.png')):
                    fails.append('model texture missing: %s -> %s' % (rel(p), v))
            par = j.get('parent')
            if isinstance(par, str) and par.startswith('skyabyss:'):
                if not os.path.exists(os.path.join(MOD, 'models', par.split(':', 1)[1] + '.json')):
                    fails.append('model parent missing: %s -> %s' % (rel(p), par))
    print('model texture refs: %d' % refs)
    bsd = os.path.join(MOD, 'blockstates')
    for f in os.listdir(bsd):
        j = json.load(open(os.path.join(bsd, f), encoding='utf-8'))
        models = []

        def walk(o):
            if isinstance(o, dict):
                if isinstance(o.get('model'), str):
                    models.append(o['model'])
                for v in o.values():
                    walk(v)
            elif isinstance(o, list):
                for v in o:
                    walk(v)
        walk(j)
        for m in models:
            if m.startswith('skyabyss:') and not os.path.exists(os.path.join(MOD, 'models', m.split(':', 1)[1] + '.json')):
                fails.append('blockstate model missing: blockstates/%s -> %s' % (f, m))
    # 3. sounds.json (バニラのファイル名は、neoform の資産の索引 (assets/indexes/*.json) があればそれで実在を見る)
    vanilla = None
    idx_dir = os.path.join(os.path.expanduser('~'), '.gradle', 'caches', 'neoformruntime', 'assets', 'indexes')
    if os.path.isdir(idx_dir):
        vanilla = set()
        for f in os.listdir(idx_dir):
            if f.endswith('.json'):
                vanilla.update(json.load(open(os.path.join(idx_dir, f), encoding='utf-8')).get('objects', {}).keys())
    sj = json.load(open(os.path.join(MOD, 'sounds.json'), encoding='utf-8'))
    for ev, d in sj.items():
        for s in d.get('sounds', []):
            name = s['name'] if isinstance(s, dict) else s
            typ = s.get('type', 'sound') if isinstance(s, dict) else 'sound'
            if typ == 'event':
                continue
            ns, _, path = name.partition(':')
            if not path:
                ns, path = 'minecraft', ns
            if ns == 'skyabyss':
                if not os.path.exists(os.path.join(MOD, 'sounds', path + '.ogg')):
                    fails.append('sounds.json: %s -> %s (ogg missing)' % (ev, name))
            elif vanilla is not None and ('minecraft/sounds/%s.ogg' % path) not in vanilla:
                fails.append('sounds.json: %s -> %s is not a vanilla sound file; use the event (type: event, e.g. minecraft:block.stone.step)' % (ev, name))
    print('sound events: %d (vanilla index: %s)' % (len(sj), 'yes' if vanilla is not None else 'not found, vanilla file names not checked'))
    # 4. lang parity + sign widths
    signs = 0
    for ns in sorted(os.listdir(ASSETS)):
        ld = os.path.join(ASSETS, ns, 'lang')
        if not os.path.isdir(ld):
            continue
        langs = {}
        for lc in ('ja_jp', 'en_us'):
            p = os.path.join(ld, lc + '.json')
            if os.path.exists(p):
                langs[lc] = json.load(open(p, encoding='utf-8'))
        if len(langs) == 2:
            ja, en = langs['ja_jp'], langs['en_us']
            for k in ja:
                if k not in en:
                    fails.append('lang: %s has %s in ja_jp but not en_us' % (ns, k))
            for k in en:
                if k not in ja:
                    fails.append('lang: %s has %s in en_us but not ja_jp' % (ns, k))
        for lc, j in langs.items():
            for k, v in j.items():
                if k.startswith('sign.') and k.rsplit('.', 1)[1] in ('1', '2', '3', '4'):
                    signs += 1
                    w = sign_width(v)
                    if w > MAX_LINE_WIDTH:
                        fails.append('sign too wide: %s (%s) %d px > %d: %s' % (k, lc, w, MAX_LINE_WIDTH, v))
    print('sign lines: %d' % signs)
    for f in fails:
        print('FAIL ' + f)
    print('check_resources: %d problem(s)' % len(fails))
    return 1 if fails else 0


if __name__ == '__main__':
    sys.exit(main())
