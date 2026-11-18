# -*- coding: utf-8 -*-
"""設計の build が出した <id>.json から、Blockbench の place_cube に渡す引数を箱ごとに 1 行で出す。

  cd <作業フォルダ> && python -I tools/models/x32/look5/dump.py <id>
"""
import json
import sys
from PIL import Image

n = sys.argv[1]
print(Image.open(n + '_base.png').size)
for c in json.load(open(n + '.json', encoding='utf-8')):
    e = {'name': c['name'], 'from': c['from'], 'to': c['to'], 'origin': c['origin']}
    if any(c['rotation']):
        e['rotation'] = c['rotation']
    print(json.dumps([e], separators=(',', ':')), '|', json.dumps(c['faces'], separators=(',', ':')))
