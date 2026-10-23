# -*- coding: utf-8 -*-
"""深淵 v3 の確かめの絵を docs/review/abyss_v3/ に縮めて写し、見出しつきの一覧 (index.html) と比べ表を作る。

python -I tools/abyss/abyss_v3_page.py [撮った絵のフォルダ]
  既定のフォルダは run/shots/abyss_v3/shots/abyss_v3 (台本 tools/shots/abyss_v3.txt)
"""
import glob
import html
import os
import sys

from PIL import Image

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', '..')
OUT = os.path.join(ROOT, 'docs', 'review', 'abyss_v3')
CAPTIONS = {
    'c01_sealed_door': '酒場の地下の奥の封じた戸',
    'c02_corridor': '戸の裏の幅 3 の通路と碑「大坑道」',
    'c03_shaft_top_down': '大縦穴を上から (半径 11。吊った鎖と灯り)',
    'c04_spiral_mid': '幅 3 の螺旋の段と壁の帯・縦の筋',
    'c05_landing_workshop': '踊り場 1: 灰の民の作業場',
    'c06_landing_camp': '踊り場 2: 前の私の野営',
    'c07_bottom_to_hall': '底の輪の象嵌から門の大広間へ',
    'c08_gate_hall': '門の大広間 (柱の列・龕・黒い旗)',
    'c09_gate_close': '深淵の門 (格子 幅 5 × 高さ 6、泣く黒曜石の楣と目の浮き彫り)',
    'c10_shaft_look_up': '底から見上げる',
    'a01_return_shrine': '骨の野営の帰りの祠 (白い石英・空色の窓)',
    'a02_return_shrine_close': '帰りの祠の白い磁石石',
    'a03_bar_gate_closed': '層の北の格子の門 (印が足りない)',
    'a04_bar_gate_raised': '印が三つで格子が上がった',
    'a05_guide_strip': '目的の帯の道案内 (矢印と距離)',
    'a06_lord_bone_intro': '肋の主の名乗り (咆哮のボスバー)',
    'a07_lord_bone_move': '肋の主の技の赤い予告',
    'a08_lord_bone_move2': '肋の主の技の赤い予告 (二つ目)',
    'a09_lord_salt_move': '白殻の主の技',
    'a10_lord_storm_move': '鼓動まねの主の技',
    'a11_lord_ash_move': '灯り喰いの主の技',
}


def main():
    src = sys.argv[1] if len(sys.argv) > 1 else os.path.join(ROOT, 'run', 'shots', 'abyss_v3', 'shots', 'abyss_v3')
    files = sorted(glob.glob(os.path.join(src, '*.png')))
    os.makedirs(OUT, exist_ok=True)
    items = []
    for f in files:
        stem = os.path.splitext(os.path.basename(f))[0]
        im = Image.open(f).convert('RGB')
        im.thumbnail((960, 540))
        name = stem + '.jpg'
        im.save(os.path.join(OUT, name), quality=82)
        items.append((name, CAPTIONS.get(stem, stem)))
    body = '\n'.join('<figure><img src="%s" loading="lazy"><figcaption>%s</figcaption></figure>' % (html.escape(n), html.escape(c)) for n, c in items)
    page = '''<!doctype html><html lang="ja"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>深淵 v3 の確かめ</title><style>
:root{--bg:#111318;--fg:#e8e6e0;--muted:#9a9890}
body{background:var(--bg);color:var(--fg);font-family:system-ui,sans-serif;margin:0 auto;padding:16px;max-width:1100px}
h1{font-size:20px}p{color:var(--muted)}
.grid{display:grid;grid-template-columns:repeat(auto-fill,minmax(300px,1fr));gap:12px}
figure{margin:0}img{width:100%;border-radius:4px;display:block}figcaption{font-size:13px;margin-top:4px}
</style></head><body><h1>深淵 v3 (2026-10-23 の試遊の直し)</h1>
<p>大坑道と門の大広間・帰りの祠・層の格子の門・道案内の帯・層の主。台本 tools/shots/abyss_v3.txt</p>
<div class="grid">
''' + body + '''
</div></body></html>
'''
    with open(os.path.join(OUT, 'index.html'), 'w', encoding='utf-8', newline='\n') as fp:
        fp.write(page)
    print('wrote', len(items), 'shots to', os.path.relpath(OUT, ROOT))


if __name__ == '__main__':
    main()
