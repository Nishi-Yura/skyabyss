#!/usr/bin/env python3
"""試練のプール・碑・領域の仕掛けの lang を assets/skyabyss_trial/lang/{ja_jp,en_us}.json に書く (何度流しても同じ。上書き)。
キーの元は tools/trial_lang/*.py (それぞれ JA と EN の辞書を持つ)。リポジトリ直下で python tools/gen/story/gen_trial_lang.py"""
import glob
import importlib.util
import json
import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', '..', 'lib'))
from langmerge import merged  # noqa: E402

ja, en = {}, {}
not_here = set()
for f in sorted(glob.glob(os.path.join(os.path.dirname(__file__), '..', '..', 'trial_lang', '*.py'))):
    spec = importlib.util.spec_from_file_location('m', f)
    m = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(m)
    ja.update(m.JA)
    en.update(m.EN)
    not_here.update(getattr(m, 'NOT_HERE', ()))
assert set(ja) == set(en), sorted(set(ja) ^ set(en))
for name, d in (('ja_jp', ja), ('en_us', en)):
    p = 'src/main/resources/assets/skyabyss_trial/lang/%s.json' % name
    os.makedirs(os.path.dirname(p), exist_ok=True)
    # 持たないキー (手で足した文) は残して書く (tools/lib/langmerge.py)
    d = merged(p, {k: v for k, v in d.items() if k not in not_here})
    json.dump(d, open(p, 'w', encoding='utf-8'), ensure_ascii=False, indent=2)
print(len(ja), 'keys')
