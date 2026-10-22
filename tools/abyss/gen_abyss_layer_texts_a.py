# -*- coding: utf-8 -*-
"""骨の層と塩の層の遊びの文言 (docs/design/36 の 8 章・A9)。

python -I tools/abyss/gen_abyss_layer_texts_a.py
書くもの:
  src/main/resources/assets/skyabyss_abyss_la/lang/ja_jp.json・en_us.json
文言は story/ch5/layers/LayerNotes と BoneBridge・SaltTide が行動バーに出す。短く、深淵の静かな言葉で。
"""
from __future__ import annotations

import json
import os

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', '..')
LANG = os.path.join(ROOT, 'src', 'main', 'resources', 'assets', 'skyabyss_abyss_la', 'lang')

JA = {
    'message.skyabyss.abyss_layers.bone_pack': '橋の両端から、骨拾いが這い寄ってくる',
    'message.skyabyss.abyss_layers.den_seal': '戸が閉じた。肋の番を倒すまで開かない',
    'message.skyabyss.abyss_layers.den_open': '戸が開いた。肋の番は静かになった',
    'message.skyabyss.abyss_layers.tide_high': '潮が満ちてきた。水の底から手が伸びる',
    'message.skyabyss.abyss_layers.tide_low': '潮が引いた。下の道が現れた',
}

EN = {
    'message.skyabyss.abyss_layers.bone_pack': 'Bone pickers crawl in from both ends of the bridge',
    'message.skyabyss.abyss_layers.den_seal': 'The door shut. It will not open until the rib guard falls',
    'message.skyabyss.abyss_layers.den_open': 'The door opened. The rib guard is still now',
    'message.skyabyss.abyss_layers.tide_high': 'The tide is rising. Hands reach up from the water',
    'message.skyabyss.abyss_layers.tide_low': 'The tide has gone out. The lower path is showing',
}


def write(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w', encoding='utf-8', newline='\n') as f:
        json.dump(obj, f, ensure_ascii=False, indent=2, sort_keys=True)
        f.write('\n')


def main():
    write(os.path.join(LANG, 'ja_jp.json'), JA)
    write(os.path.join(LANG, 'en_us.json'), EN)
    print('wrote', os.path.relpath(LANG, ROOT), len(JA), 'keys')


if __name__ == '__main__':
    main()
