# 用語表 (docs/design/57) の機械的な置き換えを、すべての ja_jp.json にかける。
# 使い方: python -X utf8 tools/dev/text/lang_terms.py [--write]
# 書式の記号・改行は変えない。看板 (sign.*) は 1 行 9 字を超えるなら当てない
import glob
import json
import re
import sys

RULES = [
    (r'スキルの再使用までの時間', 'クールダウン'),
    (r'スキルの再使用時間', 'クールダウン'),
    (r'スキルの待ち時間', 'クールダウン'),
    (r'スキルの待ち', 'クールダウン'),
    (r'技の再使用までの時間', 'クールダウン'),
    (r'技の待ち', 'クールダウン'),
    (r'の再使用までの時間', 'のクールダウン'),
    (r'再使用までの時間', 'クールダウン'),
    (r'パーティ(?!ー)', 'パーティー'),
    (r'気嚢|気のう', '空気袋'),
    (r'頁', 'ページ'),
    (r'封の箱', '封印の箱'),
]


def fix(key, v):
    n = v
    for a, b in RULES:
        n = re.sub(a, b, n)
    if key.startswith('sign.') and any(len(l) > 9 for l in n.split('\n')):
        return v
    return n


def main():
    write = '--write' in sys.argv
    total = 0
    for f in sorted(glob.glob('src/main/resources/assets/*/lang/ja_jp.json')):
        d = json.load(open(f, encoding='utf8'))
        changed = 0
        for k, v in d.items():
            n = fix(k, v)
            if n != v:
                changed += 1
                if not write and changed <= 3:
                    print('  ', k, ':', v.replace('\n', '/'), '=>', n.replace('\n', '/'))
                d[k] = n
        if changed:
            print(f, changed)
            total += changed
            if write:
                with open(f, 'w', encoding='utf8', newline=chr(10)) as o:
                    json.dump(d, o, ensure_ascii=False, indent=2)
                    o.write(chr(10))
    print('total', total, '(written)' if write else '(dry run)')


main()
