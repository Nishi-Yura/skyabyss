# -*- coding: utf-8 -*-
"""言語ファイルの文言を番号つきで見せる (推敲の作業用。docs/design/57_text_style.md)。

  python -X utf8 tools/dev/text/lang_dump.py <ja_jp.json> <はじめの番号> <数>

行は「番号<TAB>キー<TAB>文言」。文言の改行は \n と書く。カナを含まない文言 (記号・数字だけ) は飛ばさずに出す
"""
import json
import sys

path, start, count = sys.argv[1], int(sys.argv[2]), int(sys.argv[3])
d = json.load(open(path, encoding="utf8"))
items = list(d.items())
print(f"# {path}: {len(items)} entries, showing {start}..{min(len(items), start + count) - 1}")
for i in range(start, min(len(items), start + count)):
    k, v = items[i]
    print(f"{i}\t{k}\t{v.replace(chr(10), chr(92) + 'n')}")
