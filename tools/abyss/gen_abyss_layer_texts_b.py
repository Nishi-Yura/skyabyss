# -*- coding: utf-8 -*-
"""雷の層と灰の層の遊びの文言 (docs/design/36 の 8 章。A9)。

python -I tools/abyss/gen_abyss_layer_texts_b.py
書くもの:
  assets/skyabyss_abyss_lb/lang/ja_jp.json・en_us.json
文言の数は少なく、ゲームの中の一行（知らせ）だけ。語り口は tools/gen/ch5/gen_ch5_entrance.py に合わせる。
"""
from __future__ import annotations

import json
import os

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', '..')
LANG = os.path.join(ROOT, 'src', 'main', 'resources', 'assets', 'skyabyss_abyss_lb', 'lang')

JA = {
    'message.skyabyss.abyss.storm.idol_down': '避雷の像が砕け、橋の一区間に雷が落ちなくなった。',
    'message.skyabyss.abyss.ash.enter': '灰の中で、壁の向こうの足音がついてくる。灯りの下でだけ、道の跡が見える。',
    'message.skyabyss.abyss.ash.keeper_down': '燠の番人が崩れ、灰の底に近道の鎖が残った。',
}

EN = {
    'message.skyabyss.abyss.storm.idol_down': 'The lightning idol shattered. No more strikes fall on that section of the bridge.',
    'message.skyabyss.abyss.ash.enter': 'Footsteps follow behind the ash walls. The trail shows only in light.',
    'message.skyabyss.abyss.ash.keeper_down': 'The ember keeper collapsed. A chain shortcut is left in the ash.',
}


def write(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w', encoding='utf-8', newline='\n') as f:
        json.dump(obj, f, ensure_ascii=False, indent=2, sort_keys=True)
        f.write('\n')


def main():
    write(os.path.join(LANG, 'ja_jp.json'), JA)
    write(os.path.join(LANG, 'en_us.json'), EN)
    print('wrote', os.path.relpath(LANG, ROOT))


if __name__ == '__main__':
    main()
