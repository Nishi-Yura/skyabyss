# -*- coding: utf-8 -*-
"""quick_check の二つの確認 (2026-11-10 の見直し):
  1. 古い言葉: 用語表 (tools/dev/text/lang_terms.py の RULES と下の RETIRED) に当たる文が、どの置き場の ja_jp.json にも残っていない。
     言葉は 75 ほどの置き場 (assets/skyabyss_*/lang) に分かれているので、一つの置き場だけ見ると漏れる。
  2. 重なり検査の台本 (tools/shots/overlap_audit.txt) の `open <名前>` が、撮影の道具 (client/dev/ShotRunner) の知っている名前である。
     消した画面の名前が台本に残ると、撮影がそこで止まって後ろの画面を見なくなる。
python -I tools/audit/check_ui_terms.py"""
import glob
import io
import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
# 言い換えた言葉 (言い換え先は docs/design/57 と各日の決定)
RETIRED = ['旅嚢', 'アクセサリー', 'アクセ(?!ント|ス)']


def rules():
    src = io.open(ROOT / 'tools/dev/text/lang_terms.py', encoding='utf-8').read()
    body = src[src.index('RULES = ['):src.index(']', src.index('RULES = [')) + 1]
    out = [a for a, _ in re.findall(r"\(r'([^']+)', '([^']*)'\)", body)]
    return out + RETIRED


def old_terms():
    pats = [re.compile(p) for p in rules()]
    bad = []
    for f in sorted(glob.glob(str(ROOT / 'src/main/resources/assets/*/lang/ja_jp.json'))):
        d = json.load(io.open(f, encoding='utf-8'))
        for k, v in d.items():
            if k.startswith('sign.'):
                continue
            for p in pats:
                if p.search(v):
                    bad.append('%s: %s: %s  (%s)' % (Path(f).parent.parent.name, k, v.replace('\n', '/')[:60], p.pattern))
                    break
    return bad


def overlap_opens():
    runner = io.open(ROOT / 'src/main/java/jp/revati/skyabyss/client/dev/ShotRunner.java', encoding='utf-8').read()
    known = set()
    for m in re.finditer(r'case ((?:"[\w]+"(?:, )?)+) ->', runner):
        known.update(re.findall(r'"([\w]+)"', m.group(1)))
    prefixes = set(re.findall(r'name\.startsWith\("([\w]+:)"\)', runner))
    bad = []
    script = ROOT / 'tools/shots/overlap_audit.txt'
    for n, line in enumerate(io.open(script, encoding='utf-8').read().splitlines(), 1):
        m = re.match(r'open (\S+)', line)
        if not m:
            continue
        name = m.group(1)
        if '.' in name or name.lower() in known or any(name.startswith(p) for p in prefixes) or re.match(r'pouchwheel_\d+$', name):
            continue
        bad.append('overlap_audit.txt:%d: open %s (ShotRunner の知らない名前)' % (n, name))
    return bad


def main():
    bad = old_terms() + overlap_opens()
    for b in bad[:40]:
        print(b)
    print('check_ui_terms: %d problems' % len(bad))
    sys.exit(1 if bad else 0)


main()
