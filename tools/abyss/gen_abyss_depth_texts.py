# -*- coding: utf-8 -*-
"""心臓の下の果てない深み (docs/design/36 の 9) の文字。区画の型・仕掛け・修飾・珍しい部屋の名と一言、節目・順位・潜りの知らせ。

python -I tools/abyss/gen_abyss_depth_texts.py
書くもの:
  assets/skyabyss_abyss_dp/lang/ja_jp.json・en_us.json （src/main/resources の下）
鍵の名前は src/main/java/jp/revati/skyabyss/story/ch5/depths の DepthPlan (Kind・Gimmick・Modifier・Rare) と、
DepthRun・DepthRecords の message.skyabyss.depths.* に合わせる。区画の型の名は小文字にして kind.<名前> にする。
"""
from __future__ import annotations

import json
import os

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', '..')
LANG = os.path.join(ROOT, 'src', 'main', 'resources', 'assets', 'skyabyss_abyss_dp', 'lang')

# 区画の型 (Kind): 名前・一言
KINDS = {
    'inverted_forest': ('逆さの森', 'Inverted Forest',
                        '天井から幹が垂れ、その根の下を道が縫う森。', 'A forest hanging from the ceiling; the path winds beneath the roots.'),
    'floating_graves': ('浮かぶ墓場', 'Floating Graves',
                        '足場ごとに墓標が立つ、宙に浮いた墓地。', 'A graveyard in the air, each ledge with its own stone.'),
    'pulse_cave': ('心臓の脈の洞', 'Pulse Cave',
                   '壁の脈が一定の拍で赤く息をする洞。', 'Veins on the walls redden in a steady beat, like a breathing cave.'),
    'mirror_dark': ('鏡の闇', 'Mirror Dark',
                    '黒い鏡が帯になって壁を巡る、闇の中の広間。', 'Black mirrors run around the walls like bands in the dark.'),
    'chain_net': ('鎖の網', 'Chain Net',
                  '鎖が網のように張られ、その目を跳んで下りる。', 'Chains are strung like a net; you drop down through its gaps.'),
    'lamp_hall': ('灯りを喰う広間', 'Lamp-Eating Hall',
                  '灯りは多いのに、黒い口がその灯りを喰っていく広間。', 'Many lamps, yet black mouths in the walls eat every light.'),
    'dry_well': ('静かな井戸', 'Quiet Well',
                 '中心に泥煉瓦の井戸の筒がそびえる、音のない井戸。', 'A mud-brick well shaft rises at the center of a silent well.'),
    'hollow_stars': ('星のない空洞', 'Starless Hollow',
                     '天井に星の代わりの光が疎らに散る空洞。', 'Faint lights scattered on the ceiling stand in for stars.'),
}

# 仕掛け (Gimmick): 名前・一言
GIMMICKS = {
    'lights_out': ('灯りが消える', 'Lights Go Out',
                   '壁の灯りが四分の一ずつ、二十秒ごとに消えて回る。', 'The wall lamps go out a quarter at a time, every twenty seconds.'),
    'heavy_descent': ('下りの重さ', 'Heavy Descent',
                      '落ちる速さが増す。上るのは変わらない。', 'Falling gets faster. Climbing stays the same.'),
    'beat_ledges': ('拍で消える岩棚', 'Beat Ledges',
                    '脇の岩棚が一秒半ごとに消えたり出たりする。', 'The side ledges blink out and back on every second and a half.'),
    'crumble_ledges': ('崩れる岩棚', 'Crumbling Ledges',
                       '脇の岩棚に二秒立つと崩れ、十秒で戻る。', 'Stand on a side ledge for two seconds and it crumbles; it returns after ten.'),
    'copy_foes': ('写しの敵', 'Copied Foes',
                  '湧く敵が、前の私の影や名のない形に寄る。', 'Foes that spawn lean toward past shadows and nameless shapes.'),
    'fog_hide': ('霧が敵を隠す', 'Hidden in Fog',
                 '灯りから離れた敵の姿が消える。', 'Foes far from any light vanish from sight.'),
    'dark_front': ('下から広がる闇', 'Rising Dark',
                   '底から闇が十二秒に一ブロック上がってくる。', 'The dark rises from the bottom, one block every twelve seconds.'),
    'chase_fall': ('上からの追手', 'Falling Stone',
                   '人の真上へ石が落ちる。輪の予告のあと。', 'Stones fall straight onto a person, after a warning ring.'),
}

# 修飾 (Modifier): 名前・一言
MODIFIERS = {
    'more_foes': ('敵が多い', 'More Foes', '湧きの上限が一・五倍。', 'The spawn cap is one and a half times higher.'),
    'fierce': ('傷が深い', 'Fierce', '敵の強さの段が一つ上がる。', 'Foes gain one step of strength.'),
    'rich_drops': ('落とし物が多い', 'Rich Drops', '深淵の素材が一つ増える機会がある。', 'Foes sometimes drop an extra abyss material.'),
    'sparse_light': ('灯りが少ない', 'Sparse Light', '壁の灯りが半分になる。', 'Half as many wall lamps.'),
}

# 珍しい部屋 (Rare): 名前・一言
RARES = {
    'treasure_hall': ('宝の間', 'Treasure Hall', '脇の橋の先に箱が二つ。遺跡の中身。', 'Two chests at the end of a side bridge, full of old loot.'),
    'past_grave': ('前の私の大きな墓', 'Large Past Grave', '石の台と、前の私の遺品の樽。', 'A stone bier and a barrel of a past self\'s belongings.'),
    'warden_fragment': ('番人の欠片', 'Warden Fragment', '裂けた心臓の柱と、箱。番人の一部の名残。', 'A split heart pillar and a chest: a remnant of the warden.'),
}

# 文字 (節目・順位・潜りの知らせ)。%s は数字や名前
MESSAGES = {
    'milestone': ('心臓の下 %s の節目を越えた。経験の段が上がる。', 'Passed the depth %s milestone below the heart. Your levels rise.'),
    'milestone_group': ('組が心臓の下 %s の節目を越えた。', 'Your group passed the depth %s milestone below the heart.'),
    'rank_head': ('── 深みの順位 ──', '── Depth ranking ──'),
    'rank_group': ('組ごと', 'By group'),
    'rank_player': ('一人ごと', 'By player'),
    'rank_line': ('%1$s位  %2$s  深さ %3$s', '#%1$s  %2$s  depth %3$s'),
    'rank_none': ('まだ記録がない。', 'No record yet.'),
    'fell': ('深みの底を抜けた。覚えた灯りで目を覚ます。', 'You fell through the floor of the depths. You wake at the last lantern.'),
    'not_yet': ('その下の深みは、まだできていない。少し待つ。', 'The depth below is not formed yet. Wait a moment.'),
    'descend': ('深み %s へ落ちた。闇が濃い。', 'You fell into depth %s. The dark is thick here.'),
    'lit': ('灯りに火が点いた (深み %s)。', 'The lantern is lit (depth %s).'),
    'rest': ('ここで休める。倒れたら、ここから目を覚ます。', 'You can rest here. If you fall, you wake here.'),
    'climb': ('鎖を登るには、しゃがんで右クリック。', 'To climb the chain, crouch and right-click.'),
    'returned': ('心臓の間へ戻った。この潜りの記録は、ここで止まる。', 'Back in the heart room. This dive\'s record stops here.'),
}


def build(lang_index):
    out = {}
    for k, v in KINDS.items():
        out['message.skyabyss.depths.kind.' + k] = v[lang_index]
        out['message.skyabyss.depths.kind_note.' + k] = v[2 + lang_index]
    for k, v in GIMMICKS.items():
        out['message.skyabyss.depths.gimmick.' + k] = v[lang_index]
        out['message.skyabyss.depths.gimmick_note.' + k] = v[2 + lang_index]
    for k, v in MODIFIERS.items():
        out['message.skyabyss.depths.modifier.' + k] = v[lang_index]
        out['message.skyabyss.depths.modifier_note.' + k] = v[2 + lang_index]
    for k, v in RARES.items():
        out['message.skyabyss.depths.rare.' + k] = v[lang_index]
        out['message.skyabyss.depths.rare_note.' + k] = v[2 + lang_index]
    for k, v in MESSAGES.items():
        out['message.skyabyss.depths.' + k] = v[lang_index]
    return out


def write(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w', encoding='utf-8', newline='\n') as f:
        json.dump(obj, f, ensure_ascii=False, indent=2, sort_keys=True)
        f.write('\n')


def main():
    write(os.path.join(LANG, 'ja_jp.json'), build(0))
    write(os.path.join(LANG, 'en_us.json'), build(1))
    print('wrote', LANG)


if __name__ == '__main__':
    main()
