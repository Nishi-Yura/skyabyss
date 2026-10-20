#!/usr/bin/env python3
"""岩鯨の試遊の声 2 回目 (版 25) の言葉を足す。リポジトリ直下で python tools/add_whale_fix2_lang.py 。何度流しても同じ。
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
ADD['skyabyss'] = {
    'ja_jp': {
        'sign.skyabyss.deck_board.1': '試練の板', 'sign.skyabyss.deck_board.2': '引いた三つと', 'sign.skyabyss.deck_board.3': '今週の試練が', 'sign.skyabyss.deck_board.4': '読める',
        'sign.skyabyss.deck_pad.1': '光る床に立てば', 'sign.skyabyss.deck_pad.2': '試練の階の', 'sign.skyabyss.deck_pad.3': '広間へ飛べる', 'sign.skyabyss.deck_pad.4': '',
        'sign.skyabyss.deck_stair.1': 'この階段は', 'sign.skyabyss.deck_stair.2': '試練の階へ', 'sign.skyabyss.deck_stair.3': '続いている', 'sign.skyabyss.deck_stair.4': '(光る床が近道)',
        'sign.skyabyss.deck_hall.1': '試練の階', 'sign.skyabyss.deck_hall.2': '引いた三つと', 'sign.skyabyss.deck_hall.3': '今週の試練の', 'sign.skyabyss.deck_hall.4': '戸だけが開く',
        'objective.skyabyss.trial_board': '試練の板を読んで、挑む試練を確かめよう',
        'objective.skyabyss.trial_deck': '階段を上り、試練の階へ向かおう',
        'objective.skyabyss.trial_door': '光る目印の戸から、引いた試練へ入ろう',
    },
    'en_us': {
        'sign.skyabyss.deck_board.1': 'Trial Board', 'sign.skyabyss.deck_board.2': 'Your 3 trials', 'sign.skyabyss.deck_board.3': 'and the weekly', 'sign.skyabyss.deck_board.4': 'trial are shown',
        'sign.skyabyss.deck_pad.1': 'Step on the glow', 'sign.skyabyss.deck_pad.2': 'to jump to the', 'sign.skyabyss.deck_pad.3': 'Trial Hall', 'sign.skyabyss.deck_pad.4': '',
        'sign.skyabyss.deck_stair.1': 'Stairs up to', 'sign.skyabyss.deck_stair.2': 'the Trial Hall', 'sign.skyabyss.deck_stair.3': '(the glow pad', 'sign.skyabyss.deck_stair.4': 'is faster)',
        'sign.skyabyss.deck_hall.1': 'Trial Hall', 'sign.skyabyss.deck_hall.2': 'Only your three', 'sign.skyabyss.deck_hall.3': 'and the weekly', 'sign.skyabyss.deck_hall.4': 'doors open',
        'objective.skyabyss.trial_board': 'Read the Trial Board to see your trials',
        'objective.skyabyss.trial_deck': 'Go up to the Trial Hall',
        'objective.skyabyss.trial_door': 'Enter your trial through the marked door',
    },
}
ADD['skyabyss_trial']['ja_jp'].update({
    'message.skyabyss.trial_intro.board': '石の板がある。「試練の板」だ。右クリックで、いま挑む三つの試練と「今週の試練」を読める',
    'message.skyabyss.trial_intro.deck': 'ここは試練の階。板で引いた三つの試練の戸と、「今週の試練」の戸だけが開いている。ほかの戸は閉じている',
    'message.skyabyss.trial_intro.weekly': '「今週の試練」は、引いた三つとは別に、週ごとに替わる一つの試練。いつでも挑め、済ませると素材が倍になる',
    'message.skyabyss.trial_intro.weekly_cell': 'ここは「今週の試練」。引いていなくても挑める。終えると、その週は素材が倍になる',
})
ADD['skyabyss_trial']['en_us'].update({
    'message.skyabyss.trial_intro.board': 'A stone slab: the Trial Board. Right-click it to read your three trials and the weekly trial',
    'message.skyabyss.trial_intro.deck': 'The Trial Hall. Only the doors of your three drawn trials and the weekly trial are open; the rest stay shut',
    'message.skyabyss.trial_intro.weekly': 'The weekly trial is a fourth trial that changes each week. Take it any time; clearing it doubles your materials',
    'message.skyabyss.trial_intro.weekly_cell': 'This is the weekly trial. You can take it without drawing it. Clearing it doubles your materials this week',
})
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
