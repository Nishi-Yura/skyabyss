# -*- coding: utf-8 -*-
"""切り絵の作り替え (docs/design/56 の 2.1) で、画面ごとに残った夜空の数を数える。

python -I tools/dev/paper_audit.py [--all]   → 多い順に「ファイル 数 (内訳)」。--all なら 0 の画面も
数えるもの: Button.builder・直書きの金や空色や藍 (0xE9C46A・0x8FD0FF・0x9FB8FF・0xE0B040 ほか)・fillGradient・Golden.outline(・
Golden の夜空の色の定数 (Golden.GOLD・PALE・TEAL・MANA・SOFT・DIM・LINE など)。
HUD・知らせ (画面でないもの) と Golden.java は数えない。組み直した画面 (SkillScreen・JobChoiceScreen) は、生成りへ写す表 (SkillScreen.ink) が夜空の値で引くので数えない。
"""
import os
import re
import sys

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', '..', 'src', 'main', 'java', 'jp', 'revati', 'skyabyss')
PATTERNS = {
    'builder': re.compile(r'Button\.builder'),
    'hex': re.compile(r'0x(?:FF|[0-9A-Fa-f]{2})?(?:E9C46A|8FD0FF|9FB8FF|E0B040|B8923A|F2D78A|F2C14E|0A0E1E|0C1022)\b', re.I),
    'grad': re.compile(r'fillGradient'),
    'outline': re.compile(r'Golden\.outline\('),
    'const': re.compile(r'Golden\.(?:GOLD|PALE|TEAL|MANA|SOFT|DIM|LINE|LINE_DIM|RECESS_EDGE|RECESS_FILL|GLASS_TOP|GLASS_BOTTOM)\b'),
}
SKIP = {'Golden.java', 'Paper.java', 'SkillScreen.java', 'JobChoiceScreen.java'}


def is_screen(path, text):
    return ('extends Screen' in text or 'extends AbstractContainerScreen' in text or 'Golden.' in text or 'Paper.' in text) and 'client' in path.replace('\\', '/')


def main():
    rows = []
    for dp, _, fs in os.walk(ROOT):
        for f in fs:
            if not f.endswith('.java') or f in SKIP:
                continue
            p = os.path.join(dp, f)
            t = open(p, encoding='utf-8', errors='replace').read()
            if not is_screen(p, t):
                continue
            # 「意味の色」と書いた行 (土地・稀さなど、色が種類を表す) は数えない
            t = '\n'.join(l for l in t.split('\n') if '意味の色' not in l)
            c = {k: len(r.findall(t)) for k, r in PATTERNS.items()}
            n = sum(c.values())
            rows.append((n, f, c))
    rows.sort(key=lambda r: (-r[0], r[1]))
    total = 0
    for n, f, c in rows:
        total += n
        if n or '--all' in sys.argv:
            print('%4d %s  %s' % (n, f, ' '.join('%s=%d' % (k, v) for k, v in c.items() if v)))
    print('total', total)


if __name__ == '__main__':
    main()
