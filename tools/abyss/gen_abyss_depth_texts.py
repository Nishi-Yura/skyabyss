# -*- coding: utf-8 -*-
"""心臓の下の果てない深み (docs/design/36 の 9) の文字。階の型の名・王の名・罠・知らせ・目的・節目・順位。

python -I tools/abyss/gen_abyss_depth_texts.py
書くもの:
  assets/skyabyss_abyss_dp/lang/ja_jp.json・en_us.json （src/main/resources の下）
鍵の名前は src/main/java/jp/revati/skyabyss/story/ch5/depths の DepthPlan.Kind、DepthBoss.KINGS、
DepthFloor.TrapKind と、DepthRun・DepthTraps・DepthBoss・DepthRecords の message.skyabyss.depths.* に合わせる。
"""
from __future__ import annotations

import json
import os

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', '..')
LANG = os.path.join(ROOT, 'src', 'main', 'resources', 'assets', 'skyabyss_abyss_dp', 'lang')

# 階の型 (Kind): 名前
KINDS = {
    'inverted_forest': ('逆さの森', 'Inverted Forest'),
    'floating_graves': ('浮かぶ墓場', 'Floating Graves'),
    'pulse_cave': ('心臓の脈の洞', 'Pulse Cave'),
    'mirror_dark': ('鏡の闇', 'Mirror Dark'),
    'chain_net': ('鎖の網', 'Chain Net'),
    'lamp_hall': ('灯りを喰う広間', 'Lamp-Eating Hall'),
    'dry_well': ('静かな井戸', 'Quiet Well'),
    'hollow_stars': ('星のない空洞', 'Starless Hollow'),
}

# 王の名 (DepthBoss.KINGS の id)
KINGS = {
    'bone': ('歪んだ骨の王', 'The Warped Bone King'),
    'salt': ('歪んだ塩の王', 'The Warped Salt King'),
    'storm': ('歪んだ雷の王', 'The Warped Storm King'),
    'ember': ('歪んだ灰の王', 'The Warped Ember King'),
    'shadow': ('歪んだ影の王', 'The Warped Shadow King'),
}

MESSAGES = {
    'climb': ('鎖を登るには、しゃがんで右クリック。', 'To climb the chain, crouch and right-click.'),
    'descend': ('深み %1$s 階 ─ %2$s', 'Depth %1$s ─ %2$s'),
    'fell': ('倒れた。深み %s 階の入口で目を覚ます。', 'You fell. You wake at the entrance of depth %s.'),
    'returned': ('心臓の間へ戻った。この潜りは、ここで終わる。', 'You are back in the heart chamber. This dive ends here.'),
    'not_yet': ('下の階を整えている。少し待つ。', 'The floor below is still taking shape. Wait a moment.'),
    'rest': ('灯りに手をかざすと、体の重さが抜けた。', 'Holding your hand to the flame, the weight leaves your body.'),
    'rest_wait': ('灯りの温もりは、さっき分けてもらった。もうしばらく待つ。', 'The flame has just given what it could. Wait a while longer.'),
    'house': ('敵の巣だ。部屋じゅうに湧き出した。', 'A monster den. They pour out around the room.'),
    'merchant': ('底の行商', 'Merchant of the Depths'),
    'trap.spike': ('足もとから棘が突き上げた。', 'Spikes thrust up from the floor.'),
    'trap.poison': ('毒の霧が噴き出した。', 'A poison mist bursts out.'),
    'trap.summon': ('呼び笛が鳴った。敵が集まってくる。', 'A whistle sounds. Foes gather.'),
    'trap.dud': ('呼び笛が鳴った。だが、何も来なかった。', 'A whistle sounds, but nothing comes.'),
    'trap.blind': ('視界が闇に呑まれた。', 'Darkness swallows your sight.'),
    'trap.heavy': ('足が石のように重い。', 'Your legs turn to stone.'),
    'king.appear': ('%s が目を覚ました。', '%s wakes.'),
    'king.phase1': ('%s が体をよじる。動きが変わった。', '%s twists. Its moves change.'),
    'king.phase2': ('%s の体が崩れていく。もう形を保てない。', '%s is coming apart. It can no longer hold its shape.'),
    'king.down': ('%s が崩れ落ちた。', '%s collapses.'),
    'obj.find_stairs': ('下り口を探して、次の階へ下りる', 'Find the way down and descend'),
    'obj.king_floor': ('広間の王を倒して、下り口を開ける', 'Defeat the king in the hall to open the way down'),
    'obj.king': ('%s を倒す', 'Defeat %s'),
    'obj.stairs_open': ('広間の奥の下り口から、次の階へ', 'Take the opened way down at the back of the hall'),
    'checkpoint': ('深み %s 階が、再開の階になった。', 'Depth %s is now your restart floor.'),
    'milestone': ('心臓の下 %s の節目を越えた。経験の段が上がる。', 'You passed the %s mark below the heart. Your level rises.'),
    'milestone_group': ('組が心臓の下 %s の節目を越えた。', 'Your party passed the %s mark below the heart.'),
    'rank_head': ('── 深みの順位 ──', '── Depth Ranking ──'),
    'rank_group': ('組ごと', 'By party'),
    'rank_player': ('一人ごと', 'By player'),
    'rank_line': ('%1$s位  %2$s  深さ %3$s', '#%1$s  %2$s  depth %3$s'),
    'rank_none': ('まだ記録がない。', 'No records yet.'),
}


def build(i):
    out = {}
    for k, v in KINDS.items():
        out['message.skyabyss.depths.kind.' + k] = v[i]
    for k, v in KINGS.items():
        out['message.skyabyss.depths.king.' + k] = v[i]
    for k, v in MESSAGES.items():
        out['message.skyabyss.depths.' + k] = v[i]
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
