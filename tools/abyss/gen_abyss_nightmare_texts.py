# -*- coding: utf-8 -*-
"""番人の悪夢の文 (docs/design/36 の 9)。段の名前・決まり・二択・報酬・番人の言葉を書く。

python -I tools/abyss/gen_abyss_nightmare_texts.py
書くもの:
  assets/skyabyss_abyss_nm/lang/ja_jp.json・en_us.json (丸ごと書く。何度流しても同じ)
段の数字 (体力・傷・灯り・技) は story/ch5/warden/NightmareTier.java の表が正。ここの決まりの文は、その表と合わせて読むこと。
"""
from __future__ import annotations

import json
import os

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', '..')
LANG = os.path.join(ROOT, 'src', 'main', 'resources', 'assets', 'skyabyss_abyss_nm', 'lang')
P = 'message.skyabyss.abyss_nm.'

# 段 n の (名前, 決まり)。決まりは前の段の決まりに足す (表と同じ順)
TIERS = [
    ('短い灯', '灯りの持ちが四分の一短くなる（30 秒・22.5 秒・15 秒）。体力 +10%。',
     'The Short Light', 'Every lantern lasts a quarter less (30 s, 22.5 s, 15 s). Health +10%.'),
    ('二度の爪', '爪のあと、同じ扇がもう一度来る（一秒後・赤い予告つき）。体力 +20%。',
     'Two Claws', 'After a claw, the same fan comes again one second later (red warning). Health +20%.'),
    ('写しの牙', '影の写しが、本物の爪と踏み込みを使う。傷 +10%・体力 +30%。',
     'Fangs of the Shades', 'The shades use the real claw and lunge. Damage +10%, health +30%.'),
    ('六つの灯', '灯りは六つだけ。七つ目と八つ目は点かない。体力 +40%。',
     'Six Lanterns', 'Only six lanterns. The seventh and eighth do not light. Health +40%.'),
    ('二度の闇', '闇の波が二度来る。二度目は体力 12% のとき。傷 +20%・体力 +50%。',
     'Twice Dark', 'The dark wave comes twice; the second at 12% health. Damage +20%, health +50%.'),
    ('戻る闇', '闇の中では、六秒攻撃を受けないと番人が毎秒 0.5% 戻る。体力 +60%。',
     'The Returning Dark', 'In the dark, if nothing hits it for six seconds, the warden recovers 0.5% a second. Health +60%.'),
    ('追う踏み込み', '踏み込みのあと、少し間を置いて二度目の踏み込み（赤い予告つき）。傷 +30%・体力 +70%。',
     'The Chasing Lunge', 'After a lunge, a second lunge follows after a short pause (red warning). Damage +30%, health +70%.'),
    ('速い吹き消し', '吹き消しの予告が三秒から二秒に縮まる。体力 +80%。',
     'Swift Snuffing', 'The snuffing warning shrinks from three seconds to two. Health +80%.'),
    ('深い闇', '闇の中の傷は 15%（輪郭の術の 60% は変わらない）。傷 +40%・体力 +90%。',
     'Deeper Dark', 'In the dark, hits deal 15% (the outline art still gives 60%). Damage +40%, health +90%.'),
    ('心臓の脈', '十二秒ごとに、心臓の台のまわりに赤い輪（半径五・予告二秒）。踏めば当たる。傷 +50%・体力 +100%。',
     'Pulse of the Heart', 'Every twelve seconds a red ring (radius 5, two-second warning) closes around the altar. Standing in it hurts. Damage +50%, health +100%.'),
]


def build(locale):
    ja = locale == 'ja'
    d = {}
    # 番人の悪夢の入口 (台の二択)
    if ja:
        d[P + 'not_yet'] = 'まだ、この心臓の言葉を聞いていない。'
        d[P + 'offer'] = '心臓が、まだ打っている。番人の悪夢へ入れる（次は段 %1$s：%2$s）。'
        d[P + 'choose'] = '[悪夢へ入る（段 %1$s）]'
        d[P + 'busy'] = '今は番人が目覚めている。終わるまで待とう。'
        d[P + 'far'] = '台のそばに寄らないと、悪夢は開かない。'
        d[P + 'locked'] = '番人を倒すまで、悪夢は開かない。'
        d[P + 'wake'] = '心臓の奥で、悪夢が目を開けた。段 %1$s：%2$s。%3$s'
        d[P + 'wave_again'] = '闇の波が、もう一度来る。'
        d[P + 'lantern_dark'] = 'この段では、この灯りは点かない。'
        d[P + 'cleared_first'] = '悪夢の段 %1$s を、初めて越えた。経験値 %2$s、深淵術の点 %3$s。'
        d[P + 'cleared_again'] = '悪夢の段 %1$s を、もう一度越えた。経験値 %2$s。'
        d[P + 'special_5'] = '段 5 を越えた。闇の声が、初めて名前で呼んだ。'
        d[P + 'special_10'] = '段 10 を越えた。この組の名が、心臓の台に刻まれた。'
    else:
        d[P + 'not_yet'] = 'You have not heard what this heart says yet.'
        d[P + 'offer'] = 'The heart is still beating. The warden\'s nightmare is open (next: tier %1$s, %2$s).'
        d[P + 'choose'] = '[Enter the nightmare (tier %1$s)]'
        d[P + 'busy'] = 'The warden is awake now. Wait until it is over.'
        d[P + 'far'] = 'The nightmare only opens from beside the altar.'
        d[P + 'locked'] = 'The nightmare stays shut until the warden falls.'
        d[P + 'wake'] = 'Deep in the heart, the nightmare opens its eyes. Tier %1$s: %2$s. %3$s'
        d[P + 'wave_again'] = 'The dark wave comes once more.'
        d[P + 'lantern_dark'] = 'This lantern does not light in this tier.'
        d[P + 'cleared_first'] = 'First time past nightmare tier %1$s. %2$s xp, %3$s arts points.'
        d[P + 'cleared_again'] = 'Past nightmare tier %1$s again. %2$s xp.'
        d[P + 'special_5'] = 'Tier 5 is past. The dark voice called a name for the first time.'
        d[P + 'special_10'] = 'Tier 10 is past. This party\'s name is carved onto the heart\'s altar.'
    # 段の名前と決まり
    for i, (jn, jr, en, er) in enumerate(TIERS, start=1):
        d[P + 'tier.%d.name' % i] = jn if ja else en
        d[P + 'tier.%d.rule' % i] = jr if ja else er
    return d


def write(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w', encoding='utf-8', newline='\n') as f:
        json.dump(obj, f, ensure_ascii=False, indent=2)
        f.write('\n')


def main():
    write(os.path.join(LANG, 'ja_jp.json'), build('ja'))
    write(os.path.join(LANG, 'en_us.json'), build('en'))


if __name__ == '__main__':
    main()
