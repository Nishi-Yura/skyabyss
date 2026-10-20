#!/usr/bin/env python3
"""岩鯨の試遊の声 2 回目 (版 22) の言葉を足す。リポジトリ直下で python tools/add_whale_fix2_lang.py 。何度流しても同じ。
 - 試練済みの封印 (trial_seal)・越えた試練の知らせ・抜け道の言い換え
 - 試練の板のまわり (案内の碑・目的・初めての説明) は別の節で足す"""
import json, os

A = 'src/main/resources/assets'

def load(p):
    return json.load(open(p, encoding='utf-8'))

def save(p, d):
    with open(p, 'w', encoding='utf-8', newline='\n') as f:
        json.dump(d, f, ensure_ascii=False, indent=2)
        f.write('\n')

ADD = {
    'skyabyss_trial': {
        'ja_jp': {
            'block.skyabyss.trial_seal': '試練済みの封印',
            'message.skyabyss.trial_hub.sealed_block': '封印が刻まれている。この試練は、もう越えた',
        },
        'en_us': {
            'block.skyabyss.trial_seal': 'Cleared Trial Seal',
            'message.skyabyss.trial_hub.sealed_block': 'A seal is carved here. You have already cleared this trial',
        },
    },
}
# 既存の言葉の言い換え: 抜け道はなくなり、石が鳴ると少しして鼓動の間へ戻る
SET = {
    'skyabyss': {
        'ja_jp': {'message.skyabyss.return_opened': '試練を越えた。まもなく鼓動の間へ戻される'},
        'en_us': {'message.skyabyss.return_opened': 'Trial cleared. You will be returned to the Heartbeat Hall shortly'},
    },
    'skyabyss_storm': {
        'ja_jp': {'message.skyabyss.storm.return_opened': '試練を越えた。まもなく広間へ戻される'},
        'en_us': {'message.skyabyss.storm.return_opened': 'Trial cleared. You will be returned to the hall shortly'},
    },
    'skyabyss_tide': {
        'ja_jp': {'message.skyabyss.tide.return_opened': '試練を越えた。まもなく潮の広間へ戻される'},
        'en_us': {'message.skyabyss.tide.return_opened': 'Trial cleared. You will be returned to the tide hall shortly'},
    },
}

for table in (ADD, SET):
    for ns, langs in table.items():
        for lang, kv in langs.items():
            p = '%s/%s/lang/%s.json' % (A, ns, lang)
            d = load(p)
            for k, v in kv.items():
                if table is SET and k not in d:
                    raise SystemExit('missing key %s in %s' % (k, p))
                d[k] = v
            save(p, d)
print('ok')
