# -*- coding: utf-8 -*-
# 職の新しい技 10 本 (docs/design/43 の 4 段目) の文。python tools/gen/story/gen_job_skills_lang.py
# effect の %n$s の並びは SkillRules.describeArgs とそろえる。無い鍵だけ足す (直したいときは lang の行を消してから流す)
import json

KV = {
    # ---- 星読み (魔法使い)
    'skill.skyabyss.thunder_bolt': ('雷の矢', 'Thunder Bolt'),
    'skill.skyabyss.thunder_bolt.flavor': ('星は、一番近い者から順に見つける。', 'The stars find the nearest one first.'),
    'skill.skyabyss.thunder_bolt.effect': ('見ている向きへ %1$s ブロックの稲妻を放ち、最初の敵に魔法の傷 %2$s。3 段では、近く（%4$s ブロック以内）の別の敵へ %3$s で跳ねる。',
                                           'Fires lightning up to %1$s blocks ahead; the first foe takes %2$s spell damage. At rank 3 it jumps to another foe within %4$s blocks for %3$s.'),
    'skill.skyabyss.star_reading': ('星読みの心得', 'Star Reading'),
    'skill.skyabyss.star_reading.flavor': ('空を読む者は、息の継ぎ方も知っている。', 'Those who read the sky know how to catch their breath.'),
    'skill.skyabyss.star_reading.effect': ('杖を持っている間、マナの戻りが %1$s%% 速くなる。', 'While holding a staff, mana regenerates %1$s%% faster.'),
    'skill.skyabyss.ember_orb': ('熾火の玉', 'Ember Orb'),
    'skill.skyabyss.ember_orb.flavor': ('炉の火を、手のひらで運ぶ。', 'Forge fire, carried in the palm.'),
    'skill.skyabyss.ember_orb.effect': ('ゆっくり飛ぶ火の玉を放つ。敵か壁に当たると %1$s ブロックに弾け、魔法の傷 %2$s と %3$s 秒の炎。',
                                        'Throws a slow fireball. On hitting a foe or wall it bursts in %1$s blocks: %2$s spell damage and %3$s s of fire.'),
    'skill.skyabyss.frost_tide': ('凍て潮', 'Frost Tide'),
    'skill.skyabyss.frost_tide.flavor': ('冬の海は、足首から凍らせる。', 'The winter sea freezes you from the ankles up.'),
    'skill.skyabyss.frost_tide.effect': ('自分のまわり %1$s ブロックに冷たい潮。敵に魔法の傷 %2$s と、%4$s 秒の鈍足 %3$s%%（大きな相手は半分）。',
                                         'A cold tide around you (%1$s blocks): %2$s spell damage and %3$s%% slow for %4$s s (half on large foes).'),
    'skill.skyabyss.blink': ('転移', 'Blink'),
    'skill.skyabyss.blink.flavor': ('星と星のあいだは、思うより近い。', 'The gap between stars is shorter than you think.'),
    'skill.skyabyss.blink.effect': ('見ている向きへ %1$s ブロックまで跳ぶ（壁の手前で止まる）。落ちた高さは消える。待ち %2$s 秒。',
                                    'Leaps up to %1$s blocks the way you look (stops before walls). Clears fall height. Cooldown %2$s s.'),
    'skill.skyabyss.star_oath': ('星の誓い', 'Star Oath'),
    'skill.skyabyss.star_oath.flavor': ('星に誓った者は、星の分だけ多く払う。', 'Swear by the stars, and pay for the stars.'),
    'skill.skyabyss.star_oath.effect': ('星読みの技の傷 +%1$s%%。そのかわり、星読みの技のマナ +%2$s%%。', 'Star skills deal +%1$s%% damage but cost +%2$s%% mana.'),
    # ---- 職の 4 つ目
    'skill.skyabyss.forge_stomp': ('炉の踏み', 'Forge Stomp'),
    'skill.skyabyss.forge_stomp.flavor': ('鍛え人の足は、炉の床を覚えている。', "A smith's feet remember the forge floor."),
    'skill.skyabyss.forge_stomp.effect': ('足もとを踏み鳴らし、まわり %1$s ブロックに火の波。敵に %2$s の傷と %3$s 秒の炎。小さな敵は浮く。',
                                          'Stomps the ground: a wave of fire within %1$s blocks deals %2$s damage and %3$s s of fire. Small foes are lifted.'),
    'skill.skyabyss.mist_veil': ('霧隠れ', 'Mist Veil'),
    'skill.skyabyss.mist_veil.flavor': ('風読みは、霧の向きも読む。', 'Wind readers read the mist too.'),
    'skill.skyabyss.mist_veil.effect': ('%1$s 秒、姿を消して %2$s%% 速くなる。狙っていた敵は見失う。消えている間の最初の一撃は %3$s。',
                                        'Vanish for %1$s s and move %2$s%% faster. Foes targeting you lose track. Your first hit while hidden deals %3$s.'),
    'skill.skyabyss.curse_mark': ('呪いの印', 'Curse Mark'),
    'skill.skyabyss.curse_mark.flavor': ('潮歌いの唄には、裏の節がある。', 'Every tide song has a hidden verse.'),
    'skill.skyabyss.curse_mark.effect': ('見ている敵（%1$s ブロック以内）に %3$s 秒の印。印の敵は誰の攻撃でも +%2$s%% 傷つき、攻撃が弱る。',
                                         'Marks the foe you look at (within %1$s blocks) for %3$s s. It takes +%2$s%% damage from everyone and hits weaker.'),
    'skill.skyabyss.lamp_charm': ('灯の守り札', 'Lamp Charm'),
    'skill.skyabyss.lamp_charm.flavor': ('灯のそばでは、波もおとなしい。', 'By the lamp, even the waves are calm.'),
    'skill.skyabyss.lamp_charm.effect': ('足もとに灯を置く（%1$s ブロック、%2$s 秒）。そばの自分と仲間の護りの体力を、%4$s 秒ごとに %3$s 点まで満たす。',
                                         'Places a lamp at your feet (%1$s blocks, %2$s s). Every %4$s s it tops up ward HP to %3$s for you and allies nearby.'),
    # ---- 戦士・弓使いの 4 つ目 (踏み込み・跳び退きと同じ動き)
    'skill.skyabyss.shield_rush': ('盾の踏み込み', 'Shield Rush'),
    'skill.skyabyss.shield_rush.flavor': ('岩は、転がり出すと止まらない。', 'Once a rock starts rolling, it does not stop.'),
    'skill.skyabyss.shield_rush.effect': ('見ている先 %1$s ブロック以内の敵の手前まで一気に詰め、攻撃力の %2$s%% で打つ。地に足が着いているときだけ。足場のない所へは出ない。',
                                          'Close in on a foe you look at within %1$s blocks and strike for %2$s%% of your attack damage. Only on solid ground; never steps off a ledge.'),
    'skill.skyabyss.archer_leap': ('射手の跳び退き', "Archer's Leap"),
    'skill.skyabyss.archer_leap.flavor': ('射手の間合いは、いつも一歩遠い。', "An archer's distance is always one step farther."),
    'skill.skyabyss.archer_leap.effect': ('後ろへ軽く跳ぶ。地に足が着いていて、%1$s ブロック以内に敵がいるときだけ。', 'Hop backwards. Only on solid ground with a foe within %1$s blocks.'),
    # ---- 断り
    'message.skyabyss.skill.blink_blocked': ('跳べる場所がない', 'No room to blink'),
}
for lang, i in (('ja_jp', 0), ('en_us', 1)):
    p = f'src/main/resources/assets/skyabyss/lang/{lang}.json'
    s = open(p, encoding='utf8').read()
    d = json.loads(s)
    ins = ''.join(f'  "{k}": {json.dumps(v[i], ensure_ascii=False)},\n' for k, v in KV.items() if k not in d)
    s = s.replace('{\n', '{\n' + ins, 1)
    json.loads(s)
    open(p, 'w', encoding='utf8', newline='\n').write(s)
print('ok')
