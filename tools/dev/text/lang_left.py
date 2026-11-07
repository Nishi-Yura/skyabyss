# 用語表 (docs/design/57) の古い言葉が、仕組みの文言 (会話・碑文・伝承・看板を除く) にどれだけ残っているか数える
# 使い方: python -X utf8 tools/dev/text/lang_left.py [語] [件数]
import glob
import json
import re
import sys

TERMS = ['技', '組', '職', '傷', '目安', '節', '型', '欄', '装飾', '予告', '崩し', '順路', '写し', '帯']
STORY = re.compile(r'^(dialogue|lore|sign|story)\.|\.(story|lore|body|line\d*|say|speech)$')


def main():
    only = sys.argv[1] if len(sys.argv) > 1 else None
    limit = int(sys.argv[2]) if len(sys.argv) > 2 else 12
    for t in ([only] if only else TERMS):
        hits = []
        for f in sorted(glob.glob('src/main/resources/assets/*/lang/ja_jp.json')):
            d = json.load(open(f, encoding='utf8'))
            for k, v in d.items():
                if t in v and not STORY.search(k):
                    hits.append((k, v))
        print(f'== {t}: {len(hits)}')
        for k, v in hits[:limit if only else 3]:
            print('  ', k, ':', v.replace('\n', '/')[:110])


main()
