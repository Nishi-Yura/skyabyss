# -*- coding: utf-8 -*-
"""設計の build が出した <id>.json から、Blockbench の place_cube に渡す引数を箱ごとに 1 行で出す（数は小数 2 桁に丸める）。

  cd <作業フォルダ> && python -I tools/models/x32/look5/dump.py <id>
"""
import json
import sys
from PIL import Image

n = sys.argv[1]
print(Image.open(n + '_base.png').size)
r2 = (lambda v: [round(x, 2) for x in v])
for c in json.load(open(n + '.json', encoding='utf-8')):
    e = {'name': c['name'], 'from': r2(c['from']), 'to': r2(c['to']), 'origin': r2(c['origin'])}
    if any(c['rotation']):
        e['rotation'] = r2(c['rotation'])
    print(json.dumps([e], separators=(',', ':')), '|', json.dumps(c['faces'], separators=(',', ':')))
