# -*- coding: utf-8 -*-
"""巨神のアスペクト (docs/design/58) の表を書く。手で直さず、ここを直して流す。

    python -I tools/gen/aspects/gen_aspects.py

書くもの:
  src/main/java/jp/revati/skyabyss/rpg/aspect/AspectTable.java   300 品の表 (職 6 × 50)
  src/main/resources/assets/skyabyss_aspect/lang/ja_jp.json      名前・効き目の文・画面の文
  src/main/resources/assets/skyabyss_aspect/lang/en_us.json

決めごと (2026-11-09 の頁の選定): 稀さは三段 (レア・エピック・レジェンド。2026-11-09 の声で「ミシック」を「レジェンド」に)。一つの職に 50:
  どの祠でも出るレア 5・エピック 5、祠ごとのレア 4・エピック 4 (2026-11-09 の声「レアとエピック、祠につき増やしていい」「もう少し増やしていい」)、
  祠ごとのレジェンド 2。一つの祠で出るのは 20。id のミシックの印 (_m_) は保存のために残す。
アスペクトは職の使う技を一つずつ変える。効き目は Lv 1 → Lv 5 で lo → hi へ伸びる。
"""
from __future__ import annotations

import json
import os
from collections import OrderedDict

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', '..', '..')
JAVA = os.path.join(ROOT, 'src', 'main', 'java', 'jp', 'revati', 'skyabyss', 'rpg', 'aspect', 'AspectTable.java')
LANG = os.path.join(ROOT, 'src', 'main', 'resources', 'assets', 'skyabyss_aspect', 'lang')

# 職ごとの使う技 (Skill の id、日本語の名、英語の名)。並びは表の割り当ての順
JOBS = OrderedDict([
    ('warrior', [('TITAN_CLEAVE', '巨神斬り', 'Titan Cleave'), ('ROCK_CALL', '岩呼び', 'Rock Call'), ('WHALE_SWEEP', '鯨尾薙ぎ', 'Whale Sweep'),
                 ('SHIELD_RUSH', '盾の踏み込み', 'Shield Rush'), ('SHIELD_BASH', '盾殴り', 'Shield Bash'), ('BONE_SWING', '鯨骨の振り', 'Whalebone Swing'),
                 ('SPIN_CUT', '回し斬り', 'Spinning Cut')]),
    ('smith', [('TEMPER', '焼き入れ', 'Tempering'), ('EMBER_HAMMER', '熾火の槌', 'Ember Hammer'), ('ASH_SCATTER', '灰散らし', 'Ash Scatter'),
               ('FORGE_STOMP', '炉の踏み', 'Forge Stomp'), ('SPARK_BURST', '火花散らし', 'Spark Burst')]),
    ('mage', [('THUNDER_BOLT', '雷の矢', 'Thunder Bolt'), ('EMBER_ORB', '熾火の玉', 'Ember Orb'), ('FROST_TIDE', '凍て潮', 'Frost Tide'),
              ('BLINK', '転移', 'Blink'), ('STARDUST', '星屑', 'Stardust')]),
    ('archer', [('CLOUD_PIERCE', '雲穿ち', 'Cloud Pierce'), ('BONE_TRAP', '骨の罠', 'Bone Trap'), ('SKY_RAIN', '天の驟雨', 'Sky Rain'),
                ('ARCHER_LEAP', '射手の跳び退き', 'Archer Leap'), ('SNARE_SHOT', '足止めの矢', 'Snare Shot')]),
    ('assassin', [('LUNGE', '踏み込み', 'Lunge'), ('BACKSTEP', '跳び退き', 'Backstep'), ('WIND_REND', '風裂き', 'Wind Rend'),
                  ('MIST_VEIL', '霧隠れ', 'Mist Veil'), ('TWIN_FANG', '二連牙', 'Twin Fangs')]),
    ('shaman', [('WARD_SONG', '守りの唄', 'Ward Song'), ('EBB_TIDE', '引き潮', 'Ebb Tide'), ('CYCLE_SONG', '巡りの唄', 'Cycle Song'),
                ('CURSE_MARK', '呪いの印', 'Curse Mark'), ('LAMP_CHARM', '灯の守り札', 'Lamp Charm')]),
])
SHRINES = [('stone_whale', '鯨', 'Whale'), ('tide_shell', '潮', 'Tide'), ('thunder_wing', '雷', 'Storm'), ('ember', '熾', 'Ember')]

# 効き目 (Lv 1 → Lv 5)。数は割合 (0.10 = 10%)、ECHO はもう一度の力、RESET は待ちが戻る確率
RARE = [('POWER', 0.08, 0.20, 'の研ぎ', ' Edge'), ('COOLDOWN', 0.06, 0.15, 'の早業', ' Haste'),
        ('MANA', 0.10, 0.25, 'の倹約', ' Thrift'), ('HASTE', 0.10, 0.22, 'の追い風', ' Tailwind'),
        ('SURGE', 0.12, 0.30, 'の勢い', ' Drive')]
EPIC = [
    ([('SURGE', 0.25, 0.60), ('POWER', 0.04, 0.10)], 'の余勢', ' Momentum'),
    ([('GUARD', 0.10, 0.25)], 'の構え', ' Stance'),
    ([('POWER', 0.10, 0.25), ('COOLDOWN', 0.03, 0.08)], 'の冴え', ' Clarity'),
    ([('GUARD', 0.06, 0.15), ('HASTE', 0.08, 0.18)], 'の身のこなし', ' Footwork'),
    ([('MANA', 0.06, 0.15), ('COOLDOWN', 0.03, 0.08)], 'の流れ', ' Flow'),
]
# 祠ごとのレア・エピック (祠の色の効き目。名前は「祠の字の<名>・技」)
SHRINE_RARE = {
    'stone_whale': [([('GUARD', 0.06, 0.15)], 'の岩肌・', ' Rockhide: '),
                    ([('POWER', 0.05, 0.13), ('GUARD', 0.02, 0.05)], 'の骨鳴り・', ' Bonering: '),
                    ([('MANA', 0.08, 0.20)], 'の長い息・', ' Long Breath: '),
                    ([('HASTE', 0.08, 0.18)], 'の地響き・', ' Rumble: ')],
    'tide_shell': [([('MANA', 0.10, 0.24)], 'の満ち・', ' Flood: '),
                   ([('HASTE', 0.10, 0.22)], 'の引き・', ' Ebb: '),
                   ([('COOLDOWN', 0.05, 0.13)], 'の泡・', ' Foam: '),
                   ([('GUARD', 0.05, 0.12)], 'の殻・', ' Shell: ')],
    'thunder_wing': [([('COOLDOWN', 0.06, 0.15)], 'の稲光・', ' Flash: '),
                     ([('HASTE', 0.12, 0.25)], 'の風切り・', ' Windcut: '),
                     ([('POWER', 0.07, 0.18)], 'の雷鳴・', ' Thunderclap: '),
                     ([('SURGE', 0.15, 0.38)], 'の放電・', ' Discharge: ')],
    'ember': [([('POWER', 0.08, 0.19)], 'の火種・', ' Kindling: '),
              ([('SURGE', 0.15, 0.40)], 'の火の粉・', ' Sparks: '),
              ([('GUARD', 0.05, 0.13)], 'の灰被り・', ' Ashcloak: '),
              ([('COOLDOWN', 0.05, 0.12)], 'の炉風・', ' Bellows: ')],
}
SHRINE_EPIC = {
    'stone_whale': [([('GUARD', 0.08, 0.20), ('POWER', 0.04, 0.10)], 'の背の重み・', ' Weight of the Back: '),
                    ([('SURGE', 0.20, 0.50), ('GUARD', 0.04, 0.10)], 'の岩の鼓動・', ' Stone Pulse: '),
                    ([('COOLDOWN', 0.04, 0.10), ('MANA', 0.06, 0.15)], 'の古い歩み・', ' Old Stride: '),
                    ([('POWER', 0.06, 0.15), ('GUARD', 0.05, 0.12)], 'の大地の盾・', ' Earthshield: ')],
    'tide_shell': [([('COOLDOWN', 0.05, 0.12), ('MANA', 0.06, 0.15)], 'の渦・', ' Whirl: '),
                   ([('POWER', 0.08, 0.20), ('MANA', 0.04, 0.10)], 'の大潮・', ' Spring Tide: '),
                   ([('HASTE', 0.08, 0.18), ('SURGE', 0.15, 0.35)], 'の潮騒・', ' Surf: '),
                   ([('GUARD', 0.06, 0.14), ('MANA', 0.05, 0.12)], 'の深み・', ' Depths: ')],
    'thunder_wing': [([('SURGE', 0.20, 0.50), ('HASTE', 0.06, 0.15)], 'の迅雷・', ' Swift Bolt: '),
                     ([('COOLDOWN', 0.05, 0.12), ('POWER', 0.05, 0.12)], 'の嵐の目・', ' Eye of the Storm: '),
                     ([('POWER', 0.10, 0.24)], 'の落雷・', ' Strike: '),
                     ([('COOLDOWN', 0.05, 0.12), ('HASTE', 0.06, 0.14)], 'の疾風・', ' Gale: ')],
    'ember': [([('POWER', 0.08, 0.20), ('SURGE', 0.10, 0.25)], 'の燎原・', ' Wildfire: '),
              ([('GUARD', 0.08, 0.18), ('POWER', 0.03, 0.08)], 'の炉の守り・', ' Forge Ward: '),
              ([('SURGE', 0.15, 0.35), ('COOLDOWN', 0.03, 0.08)], 'の余熱・', ' Afterheat: '),
              ([('POWER', 0.06, 0.15), ('HASTE', 0.05, 0.12)], 'の陽炎・', ' Heat Haze: ')],
}

MYTH = [
    ([('ECHO', 0.35, 0.75), ('POWER', 0.04, 0.10)], 'の残響・', ' Echo of '),
    ([('RESET', 0.15, 0.35), ('SURGE', 0.15, 0.35)], 'の巡り・', ' Cycle of '),
]


def build():
    rows = []
    for job, skills in JOBS.items():
        n = len(skills)
        for i, (verb, lo, hi, ja, en) in enumerate(RARE):
            sk = skills[i % n]
            rows.append(dict(id='%s_r%d' % (job, i + 1), job=job, rarity=0, shrine='', skill=sk, fx=[(verb, lo, hi)],
                             ja=sk[1] + ja, en=sk[2] + en))
        for i, (fx, ja, en) in enumerate(EPIC):
            sk = skills[(i + 2) % n]
            rows.append(dict(id='%s_e%d' % (job, i + 1), job=job, rarity=1, shrine='', skill=sk, fx=fx, ja=sk[1] + ja, en=sk[2] + en))
        for s, (shrine, sja, sen) in enumerate(SHRINES):
            for rarity, table, tag in ((0, SHRINE_RARE, 'r'), (1, SHRINE_EPIC, 'e')):
                for k, (fx, ja, en) in enumerate(table[shrine]):
                    sk = skills[(s * 4 + k + rarity * 2) % n]
                    rows.append(dict(id='%s_%s_%s%d' % (job, tag, shrine, k + 1), job=job, rarity=rarity, shrine=shrine, skill=sk, fx=fx,
                                     ja=sja + ja + sk[1], en=sen + en + sk[2]))
            for k, (fx, ja, en) in enumerate(MYTH):
                sk = skills[(s * 2 + k) % n]
                rows.append(dict(id='%s_m_%s%d' % (job, shrine, k + 1), job=job, rarity=2, shrine=shrine, skill=sk, fx=fx,
                                 ja=sja + ja + sk[1], en=sen + en + sk[2]))
    return rows


def java(rows):
    out = ['package jp.revati.skyabyss.rpg.aspect;', '',
           'import java.util.List;', '',
           'import jp.revati.skyabyss.rpg.skill.Job;', 'import jp.revati.skyabyss.rpg.skill.Skill;', '',
           '// tools/gen/aspects/gen_aspects.py が書く。手で直さない (docs/design/58)',
           'public final class AspectTable {', '    private AspectTable() {', '    }', '',
           '    public static final List<Aspect> ALL = List.of(']
    lines = []
    for r in rows:
        fx = ', '.join('new Aspect.Fx(Aspect.Verb.%s, %sf, %sf)' % (v, lo, hi) for v, lo, hi in r['fx'])
        lines.append('            new Aspect("%s", Job.%s, %d, "%s", Skill.%s, List.of(%s))' % (r['id'], r['job'].upper(), r['rarity'], r['shrine'], r['skill'][0], fx))
    out.append(',\n'.join(lines) + ');')
    out += ['}', '']
    os.makedirs(os.path.dirname(JAVA), exist_ok=True)
    with open(JAVA, 'w', encoding='utf-8', newline='\n') as f:
        f.write('\n'.join(out))


UI_JA = {
    'aspect.skyabyss.rarity.0': 'レア', 'aspect.skyabyss.rarity.1': 'エピック', 'aspect.skyabyss.rarity.2': 'レジェンド',
    'aspect.skyabyss.fx.POWER': '技の力 +%s%%', 'aspect.skyabyss.fx.COOLDOWN': 'クールダウン −%s%%', 'aspect.skyabyss.fx.MANA': '技のマナ −%s%%',
    'aspect.skyabyss.fx.HASTE': '使ったあと 3 秒、移動 +%s%%', 'aspect.skyabyss.fx.SURGE': '使ったあとの次の一撃 +%s%%',
    'aspect.skyabyss.fx.GUARD': '使ったあと 3 秒、受ける傷 −%s%%', 'aspect.skyabyss.fx.ECHO': '0.6 秒後にもう一度 (力 %s%%)',
    'aspect.skyabyss.fx.RESET': '%s%% で待ちがすぐ戻る',
    'aspect.skyabyss.level': 'Lv %s', 'aspect.skyabyss.level_max': 'Lv 5（最高）',
    'aspect.skyabyss.copies': '次の Lv まで %s / %s', 'aspect.skyabyss.skill': '変える技: %s', 'aspect.skyabyss.shrine': '出る所: %sの祠だけ',
    'aspect.skyabyss.shared': '出る所: どの祠でも', 'aspect.skyabyss.unowned': 'まだ持っていない',
    'gui.skyabyss.aspect.title': 'アスペクト', 'gui.skyabyss.aspect.slots': '付けている（%s / %s）',
    'gui.skyabyss.aspect.slot_locked': 'Lv %s で開く', 'gui.skyabyss.aspect.slot_locked_rebirth': '転生 3 回で開く',
    'gui.skyabyss.aspect.empty': '空き', 'gui.skyabyss.aspect.click_equip': 'クリックで付ける', 'gui.skyabyss.aspect.click_remove': 'クリックで外す',
    'gui.skyabyss.aspect.none_for_job': 'まだアスペクトを持っていない。巨神の祠のルートを越えると出る',
    'gui.skyabyss.aspect.reveal_title': '報いの祭壇', 'gui.skyabyss.aspect.reveal_new': '新しい', 'gui.skyabyss.aspect.reveal_up': 'Lv %s → %s',
    'gui.skyabyss.aspect.reveal_hint': 'クリックで次へ・Esc で閉じる',
    'gui.skyabyss.trial.tab.rewards': '報い',
    'gui.skyabyss.aspect.pool_head': 'この祠のアスペクト（%s）', 'gui.skyabyss.aspect.pool_rates': 'ルートを越えるたびに 1 つ（崩れなしでもう 1 つ）。レア 60%%・エピック 30%%・レジェンド 10%%。レジェンドが 8 回出なければ次は必ずレジェンド',
    'gui.skyabyss.aspect.pool_pity': 'レジェンドまであと %s 回',
    'message.skyabyss.aspect.equip_locked': 'その枠はまだ開いていない', 'message.skyabyss.aspect.equip_job': 'いまの職のアスペクトではない',
    'message.skyabyss.aspect.equip_full': '枠がいっぱい。外してから付ける',
    'gui.skyabyss.aspect.altar': 'アスペクトの祭壇', 'gui.skyabyss.aspect.empty_hint': '棚の品を押すとここに付く',
    'gui.skyabyss.aspect.pity': 'レジェンドまであと %s 回', 'gui.skyabyss.aspect.collected': '集めた %s / %s',
    'gui.skyabyss.aspect.group_shared': 'どの祠でも', 'gui.skyabyss.aspect.group_shrine': '%sの祠',
    'gui.skyabyss.aspect.unknown': '？？？', 'gui.skyabyss.aspect.worn': '付けている',
    'gui.skyabyss.aspect.no_job': '職を選ぶと、その職のアスペクトが並ぶ',
    'aspect.skyabyss.applies_changed': '%sの形にも効く',
}
UI_EN = {
    'aspect.skyabyss.rarity.0': 'Rare', 'aspect.skyabyss.rarity.1': 'Epic', 'aspect.skyabyss.rarity.2': 'Legendary',
    'aspect.skyabyss.fx.POWER': 'Skill power +%s%%', 'aspect.skyabyss.fx.COOLDOWN': 'Skill cooldown −%s%%', 'aspect.skyabyss.fx.MANA': 'Skill mana −%s%%',
    'aspect.skyabyss.fx.HASTE': 'For 3 s after use, move +%s%%', 'aspect.skyabyss.fx.SURGE': 'Next hit after use +%s%%',
    'aspect.skyabyss.fx.GUARD': 'For 3 s after use, damage taken −%s%%', 'aspect.skyabyss.fx.ECHO': 'Fires again after 0.6 s (%s%% power)',
    'aspect.skyabyss.fx.RESET': '%s%% chance the cooldown resets',
    'aspect.skyabyss.level': 'Lv %s', 'aspect.skyabyss.level_max': 'Lv 5 (max)',
    'aspect.skyabyss.copies': 'To next Lv %s / %s', 'aspect.skyabyss.skill': 'Changes: %s', 'aspect.skyabyss.shrine': 'Drops only at the %s shrine',
    'aspect.skyabyss.shared': 'Drops at every shrine', 'aspect.skyabyss.unowned': 'Not owned yet',
    'gui.skyabyss.aspect.title': 'Aspects', 'gui.skyabyss.aspect.slots': 'Equipped (%s / %s)',
    'gui.skyabyss.aspect.slot_locked': 'Opens at Lv %s', 'gui.skyabyss.aspect.slot_locked_rebirth': 'Opens at 3 rebirths',
    'gui.skyabyss.aspect.empty': 'Empty', 'gui.skyabyss.aspect.click_equip': 'Click to equip', 'gui.skyabyss.aspect.click_remove': 'Click to remove',
    'gui.skyabyss.aspect.none_for_job': 'No aspects yet. Clear a titan shrine route to earn them',
    'gui.skyabyss.aspect.reveal_title': 'Altar of Rewards', 'gui.skyabyss.aspect.reveal_new': 'New', 'gui.skyabyss.aspect.reveal_up': 'Lv %s → %s',
    'gui.skyabyss.aspect.reveal_hint': 'Click for next · Esc to close',
    'gui.skyabyss.trial.tab.rewards': 'Rewards',
    'gui.skyabyss.aspect.pool_head': 'Aspects of this shrine (%s)', 'gui.skyabyss.aspect.pool_rates': 'One per cleared route (one more with no wipe). Rare 60%% · Epic 30%% · Legendary 10%%. After 8 runs without a legendary, the next is a legendary',
    'gui.skyabyss.aspect.pool_pity': '%s more runs to a sure legendary',
    'message.skyabyss.aspect.equip_locked': 'That slot is not open yet', 'message.skyabyss.aspect.equip_job': 'That aspect is not for your current job',
    'message.skyabyss.aspect.equip_full': 'All slots are full. Remove one first',
    'gui.skyabyss.aspect.altar': 'Aspect Altar', 'gui.skyabyss.aspect.empty_hint': 'Click an aspect on the shelf to place it here',
    'gui.skyabyss.aspect.pity': '%s runs to a sure legendary', 'gui.skyabyss.aspect.collected': 'Found %s / %s',
    'gui.skyabyss.aspect.group_shared': 'Any shrine', 'gui.skyabyss.aspect.group_shrine': '%s shrine',
    'gui.skyabyss.aspect.unknown': '???', 'gui.skyabyss.aspect.worn': 'Equipped',
    'gui.skyabyss.aspect.no_job': 'Choose a job to see its aspects',
    'aspect.skyabyss.applies_changed': 'Also works with %s',
}


def lang(rows):
    os.makedirs(LANG, exist_ok=True)
    for code, ui, key in (('ja_jp', UI_JA, 'ja'), ('en_us', UI_EN, 'en')):
        d = OrderedDict()
        for r in rows:
            d['aspect.skyabyss.' + r['id']] = r[key]
        d.update(ui)
        with open(os.path.join(LANG, code + '.json'), 'w', encoding='utf-8', newline='\n') as f:
            f.write(json.dumps(d, ensure_ascii=False, indent=2) + '\n')


def main():
    rows = build()
    assert len(rows) == 300, len(rows)
    assert len({r['id'] for r in rows}) == 300
    assert len({r['ja'] for r in rows}) == 300, 'duplicate ja names'
    java(rows)
    lang(rows)
    print('aspects:', len(rows))


if __name__ == '__main__':
    main()
