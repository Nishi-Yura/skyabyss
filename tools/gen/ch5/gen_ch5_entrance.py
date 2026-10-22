# -*- coding: utf-8 -*-
"""五章の入口「足もとの鼓動」(docs/design/34 の 4.7)。深淵の中は 00 の 245 で決めるので、ここは底の境まで。

python -I tools/gen/ch5/gen_ch5_entrance.py
書くもの:
  data/skyabyss/quest/main_abyss.json            メイン: 噂 → 支度 (黒い鉄の器) → 封じた戸 → 大坑道 → 報告
  data/skyabyss/quest/ch5/black_iron.json        支度の段: ネザライトのインゴット 1 → 鍛冶師に見せる (鍛冶型の写し 1)
  data/skyabyss/dialogue/ch5/storyteller_abyss.json   語り部の一言 (噂・報告)
  data/skyabyss/dialogue/ch5/smith_abyss.json        鍛冶師の一言 (黒い鉄の頁・見せる)
  assets/skyabyss_ch5/lang/ja_jp.json・en_us.json
"""
from __future__ import annotations

import importlib.util
import json
import os

_spec = importlib.util.spec_from_file_location('gen_ch5_layers', os.path.join(os.path.dirname(os.path.abspath(__file__)), 'gen_ch5_layers.py'))
_layers = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(_layers)
SECTION_STAGES = _layers.SECTION_STAGES  # 深淵の中の四つの区切り (骨・塩・雷・灰の 野営 → 印 → 主)。36 の 8 章

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', '..', '..')
RES = os.path.join(ROOT, 'src', 'main', 'resources')
DATA = os.path.join(RES, 'data', 'skyabyss')
LANG = os.path.join(RES, 'assets', 'skyabyss_ch5', 'lang')
ST = 'skyabyss:storyteller'
SMITH = 'skyabyss:attendant_smith'
CH = 'chapter2'  # 五章の内部の ID (story/Chapters の ABYSS)


def write(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w', encoding='utf-8', newline='\n') as f:
        json.dump(obj, f, ensure_ascii=False, indent=2)
        f.write('\n')


def q_active(quest, stage=None):
    c = {'type': 'quest', 'quest': quest, 'is': 'active'}
    if stage:
        c['stage'] = stage
    return c


MAIN = {
    'category': 'main', 'chapter': CH, 'order': 60, 'giver': ST,
    'start_when': {'type': 'quest', 'quest': 'main_key', 'is': 'done'},
    'stages': [
        {'id': 'rumor', 'xp': 60, 'objectives': [{'id': 'talk', 'type': 'talk', 'target': ST, 'topic': 'ch5_rumor'}]},
        {'id': 'prepare', 'objectives': [{'id': 'ready', 'type': 'condition', 'if': {'type': 'quest', 'quest': 'ch5/black_iron', 'is': 'done'}}]},
        {'id': 'door', 'xp': 80, 'objectives': [{'id': 'open', 'type': 'signal', 'targets': ['ch5_abyss_door'], 'count': 1}]},
        {'id': 'shaft', 'xp': 120, 'objectives': [{'id': 'reach', 'type': 'signal', 'targets': ['ch5_threshold'], 'count': 1}]},
        # 深淵の中 (36 の 8 章)。門をくぐる → 四つの層 (野営で生き残りと話す → 印を三つ → 層の主) → 心臓の間 → 番人 → 心臓に触れる
        {'id': 'gate', 'xp': 100, 'objectives': [{'id': 'enter', 'type': 'signal', 'targets': ['ch5_abyss_enter'], 'count': 1}]},
    ] + SECTION_STAGES + [
        {'id': 'descend', 'xp': 300, 'objectives': [{'id': 'reach', 'type': 'signal', 'targets': ['abyss_heart_room'], 'count': 1}]},
        {'id': 'warden', 'xp': 500, 'objectives': [{'id': 'defeat', 'type': 'signal', 'targets': ['abyss_heart'], 'count': 1}]},
        {'id': 'heart', 'objectives': [{'id': 'touch', 'type': 'signal', 'targets': ['abyss_heart_touch'], 'count': 1}]},
        {'id': 'report', 'objectives': [{'id': 'talk', 'type': 'talk', 'target': ST, 'topic': 'ch5_report'}]},
    ],
    'reward': {'xp': 800, 'affinity': {ST: 8}},
}

IRON = {
    'category': 'main', 'chapter': CH, 'order': 61, 'giver': SMITH,
    'start_when': {'type': 'quest', 'quest': 'main_abyss', 'is': 'active', 'stage': 'prepare'},  # 噂を聞いて支度の段に入ってから (00 の見直し)
    'stages': [
        {'id': 'iron', 'xp': 150, 'objectives': [{'id': 'ingot', 'type': 'collect', 'target': 'minecraft:netherite_ingot', 'count': 1, 'party': False}]},
        {'id': 'show', 'objectives': [{'id': 'talk', 'type': 'talk', 'target': SMITH, 'topic': 'ch5_iron_show'}]},
    ],
    'reward': {'xp': 200, 'items': [{'item': 'minecraft:netherite_upgrade_smithing_template', 'count': 1}], 'affinity': {SMITH: 3}},
}

ST_DIALOGUE = {
    'npc': ST, 'extends': True,
    # 噂も報告も、話しかけたときに先に出る入口 (メインの段。自己テストで頼みごとの流れを見るときは main_abyss を外す)
    'entries': [
        {'node': 'ch5_rumor', 'if': q_active('main_abyss', 'rumor')},
        {'node': 'ch5_report', 'if': q_active('main_abyss', 'report')},
    ],
    'nodes': {
        'ch5_rumor': {'face': 'grave', 'next': 'ch5_rumor2'},
        'ch5_rumor2': {'face': 'neutral', 'next': 'ch5_rumor3'},
        'ch5_rumor3': {'face': 'grave', 'options': [{'id': 'ok', 'goto': 'end', 'kind': 'talk'}]},
        'ch5_report': {'face': 'grave', 'next': 'ch5_report2'},
        'ch5_report2': {'face': 'neutral', 'options': [{'id': 'ok', 'goto': 'end', 'kind': 'talk'}]},
    },
}

SMITH_DIALOGUE = {
    'npc': SMITH, 'extends': True,
    'add_options': {
        'menu': {
            'before': 'about',
            'options': [
                {'id': 'ch5_iron_tell', 'goto': 'ch5_iron_tell', 'kind': 'quest', 'if': q_active('ch5/black_iron', 'iron')},
                {'id': 'ch5_iron_show', 'goto': 'ch5_iron_show', 'kind': 'quest', 'if': q_active('ch5/black_iron', 'show')},
            ],
        },
    },
    'nodes': {
        'ch5_iron_tell': {'face': 'neutral', 'next': 'ch5_iron_tell2'},
        'ch5_iron_tell2': {'face': 'neutral', 'options': [{'id': 'ok', 'goto': 'end', 'kind': 'talk'}]},
        'ch5_iron_show': {'face': 'smile', 'next': 'ch5_iron_show2'},
        'ch5_iron_show2': {'face': 'neutral', 'options': [{'id': 'ok', 'goto': 'end', 'kind': 'talk'}]},
    },
}

# (キー, 日本語, 英語)
TEXTS = [
    # メイン
    ('quest.skyabyss.main_abyss.title', '足もとの鼓動', 'The Beat Underfoot'),
    ('quest.skyabyss.main_abyss.summary', '天の鍵が、足もとへ脈を打つ。都の下に、何かがある。', 'The sky key pulses toward the ground. Something lies beneath the capital.'),
    ('quest.skyabyss.main_abyss.done', '「錨は、まだ緩いまま。……むっつめの歌は、空の歌だよ」', '"The anchor is still loose. ...The sixth verse is a song of the sky."'),
    ('quest.skyabyss.main_abyss.rumor.journal', '天の鍵が、ときどき下を向いて脈を打つ。語り部なら、何か知っているかもしれない。', 'Now and then the sky key turns downward and pulses. The storyteller may know something.'),
    ('quest.skyabyss.main_abyss.rumor.talk', '脈を打つ鍵のことを、語り部に聞こう', 'Ask the storyteller about the pulsing key'),
    ('quest.skyabyss.main_abyss.rumor.lead', '語り部「その鍵、下を指してるね。……都の下から、音がするんだよ」', 'Storyteller: "That key points down. ...There is a sound from under the capital."'),
    ('quest.skyabyss.main_abyss.prepare.journal', '語り部が言っていた。「前に来た人は、鍵を黒い鉄の器に収めていった」。鍛冶場で、黒い鉄のことを聞こう。', 'The storyteller said: "The one who came before set the key in a cradle of black iron." Ask at the smithy about the black iron.'),
    ('quest.skyabyss.main_abyss.prepare.ready', '黒い鉄の器を、鍛冶場で整えよう', 'Ready the black iron cradle at the smithy'),
    ('quest.skyabyss.main_abyss.door.journal', '鍵は器に収まった。酒場「深い杯」の地下の奥、封じた戸の前で、鍵が一番強く脈を打つ。', 'The key rests in its cradle. Deep in the Deep Cup tavern\'s cellar, before the sealed door, it pulses strongest.'),
    ('quest.skyabyss.main_abyss.door.open', '天の鍵を持って、酒場の地下の封じた戸に触れよう', "Open the cellar's sealed door with the key"),
    ('quest.skyabyss.main_abyss.door.lead', '封じた戸の向こうから、鍵と同じ拍子の音がする。', 'From behind the sealed door comes a beat in time with the key.'),
    ('quest.skyabyss.main_abyss.shaft.journal', '戸の向こうは、都の真下へ続く大坑道だった。灯りは踊り場にしかない。底まで降りよう。', 'Beyond the door, a great shaft runs straight down beneath the capital. Light only at the landings. Go down to the bottom.'),
    ('quest.skyabyss.main_abyss.shaft.reach', '大坑道を、底の境まで降りよう', 'Descend the shaft to the threshold'),
    ('quest.skyabyss.main_abyss.report.journal', '深淵の心臓は、世界をここにつなぎとめる錨だった。緩んだ錨のこと、近づく月のことを、語り部に伝えよう。', 'The heart of the abyss is the anchor that holds the world in place. Tell the storyteller about the loosened anchor and the nearing moon.'),
    ('quest.skyabyss.main_abyss.report.talk', '深淵の底で知ったことを、語り部に話そう', 'Tell the storyteller'),
    ('quest.skyabyss.main_abyss.gate.journal', '底の境の門の向こうで、鼓動が鳴った。天の鍵がそれに応えて脈を打ち、門の格子が溶けた。門をくぐって、深淵へ降りよう。', 'Beyond the gate at the threshold, a heartbeat sounded. The sky key pulsed in answer and the bars melted away. Pass through the gate into the abyss.'),
    ('quest.skyabyss.main_abyss.gate.enter', '開いた門をくぐって、深淵へ入ろう', 'Pass through the open gate'),
    ('quest.skyabyss.main_abyss.descend.journal', '四つの層を越えた。灰の層の主の間の鎖の先に、心臓の控えの間がある。番人は、層の主たちが教えた灯り・重さ・鼓動・闇を全部使ってくる。', 'Four layers behind you. Past the chain in the hall of the ash lord lies the antechamber of the heart. The warden will use everything the layer lords taught: light, weight, the beat and the dark.'),
    ('quest.skyabyss.main_abyss.descend.reach', '心臓の間へ入る', 'Enter the heart chamber'),
    ('quest.skyabyss.main_abyss.warden.journal', '心臓の間は真っ暗だった。闇の中に、姿の見えない番人がいる。\n輪に並んだ灯りに火を移せば、その光の中でだけ番人の姿が見える。闇の中の番人には、攻撃がほとんど効かない。', 'The heart room is pitch dark. Somewhere in it is a warden you cannot see.\nPass the flame to the ring of lanterns: only in their light does the warden take shape. In the dark, your blows barely reach it.'),
    ('quest.skyabyss.main_abyss.warden.defeat', '灯りを点けて、心臓の番人を倒そう', 'Defeat the heart warden'),
    ('quest.skyabyss.main_abyss.heart.journal', '番人は闇に溶けた。真ん中の台の上で、何かが脈打っている。', 'The warden melted into the dark. On the altar in the middle, something pulses.'),
    ('quest.skyabyss.main_abyss.heart.touch', '台の上の心臓に触れよう', 'Touch the heart on the altar'),
    # 支度
    ('quest.skyabyss.ch5.black_iron.title', '黒い鉄の器', 'A Cradle of Black Iron'),
    ('quest.skyabyss.ch5.black_iron.summary', '鍵を収める器のために、ネザーの底で黒い鉄を手に入れる。', 'Get black iron from the depths of the Nether for the key\'s cradle.'),
    ('quest.skyabyss.ch5.black_iron.done', '「器ができた。……この型も持っていけ。前のあんたも、同じ型を写していった」', '"The cradle\'s done. ...Take this template too. The you from before copied the same one."'),
    ('quest.skyabyss.ch5.black_iron.iron.journal', '鍛冶師が言っていた。「火の国の底に、黒い鉄がある。古代の残骸を掘って、金と合わせればインゴットになる」\nネザーの低い所（溶岩の海の高さより下）で、古代の残骸を探そう。ベッドや TNT で掘ると早い。残骸 4 と金のインゴット 4 で、ネザライトのインゴット 1。', 'The smith said: "Black iron lies at the bottom of the land of fire. Dig ancient debris and add gold to make an ingot."\nLook for ancient debris low in the Nether (below the lava sea). Beds or TNT dig it faster. 4 debris and 4 gold ingots make 1 netherite ingot.'),
    ('quest.skyabyss.main_abyss.prepare.lead', '鍛冶師「黒い鉄か。……火の国の底だな」', 'Smith: "Black iron, huh. ...The bottom of the land of fire."'),
    ('quest.skyabyss.ch5.black_iron.iron.ingot', 'ネザーで、ネザライトのインゴットを作ろう', 'Make a netherite ingot from the Nether'),
    ('quest.skyabyss.ch5.black_iron.show.journal', 'ネザライトのインゴットができた。鍛冶師に見せよう。', 'The netherite ingot is ready. Show it to the smith.'),
    ('quest.skyabyss.ch5.black_iron.show.talk', 'ネザライトのインゴットを、鍛冶師に見せよう', 'Show the netherite ingot to the smith'),
    # 語り部
    ('dialogue.skyabyss.storyteller.ch5_rumor', 'その鍵、ときどき下を向くだろう。……あたしも気づいてた。\n夜、都が静かになるとね、足の下から音がするんだよ。鐘じゃない。もっと、ゆっくりした音。', 'That key turns downward now and then, doesn\'t it. ...I noticed too.\nAt night, when the capital is quiet, there\'s a sound from under our feet. Not the bell. Something slower.'),
    ('dialogue.skyabyss.storyteller.ch5_rumor2', '酒場の地下の奥に、開かない戸があるだろう。祖母の祖母の頃から、あそこは封じてあった。\n前に来た人は……鍵を黒い鉄の器に収めて、あの戸の前に立ったそうだよ。', 'There\'s a door in the back of the tavern cellar that won\'t open. It has been sealed since my grandmother\'s grandmother\'s day.\nThe one who came before... set the key in a cradle of black iron and stood before that door, they say.'),
    ('dialogue.skyabyss.storyteller.ch5_rumor3', '黒い鉄のことは、鍛冶場で聞いてごらん。あの人は口が重いけど、火の国の話になると長いから。', 'Ask at the smithy about the black iron. That one doesn\'t talk much, but goes on and on about the land of fire.'),
    ('dialogue.skyabyss.storyteller.ch5_rumor3.ok', 'わかった', 'Got it'),
    ('dialogue.skyabyss.storyteller.ch5_report', '心臓が、錨……。そうか。だから、都は落ちなかったんだね。\n数え歌の、いつつめ。「いきづく　つちのそこ」。息をしてたのは、底の心臓だったんだ。', 'The heart is an anchor... I see. That\'s why the capital never fell.\nThe fifth verse. "Breathing, the bottom of the earth." What was breathing was the heart down there.'),
    ('dialogue.skyabyss.storyteller.ch5_report2', '四つ返しても、まだ足りない。……月が近い、か。むっつめの歌は、空の歌だよ。あたしも最後まで聞いたことがない。\n門は開いたままだ。前のあんたたちの跡は、まだ底に眠ってる。調べたくなったら、また潜っておいで。', 'Four returned, and still not enough. ...The moon is near, you say. The sixth verse is a song of the sky. Even I have never heard it to the end.\nThe gate stays open. The traces of the yous before are still asleep at the bottom. Dive again whenever you want to look.'),
    ('dialogue.skyabyss.storyteller.ch5_report2.ok', 'また潜る', 'I\'ll go down again'),
    # 鍛冶師
    ('dialogue.skyabyss.attendant_smith.menu.ch5_iron_tell', '黒い鉄の器のこと', 'About the black iron cradle'),
    ('dialogue.skyabyss.attendant_smith.ch5_iron_tell', '鍵を収める器？　……黒い鉄でなきゃ持たん。火の国の底、溶岩の海の下に、古代の残骸が埋まってる。\n掘り出して炉で溶かし、金と合わせればインゴットになる。一つあれば器は打てる。', 'A cradle for the key? ...Only black iron will hold. At the bottom of the land of fire, under the lava sea, ancient debris lies buried.\nDig it out, smelt it, add gold, and you have an ingot. One is enough for the cradle.'),
    ('dialogue.skyabyss.attendant_smith.ch5_iron_tell2', '残骸は硬い。ダイヤのつるはしで掘れ。……ベッドや TNT で掘ると早いが、溶岩に気をつけろ。', 'The debris is hard. Use a diamond pickaxe. ...Blasting is faster, but mind the lava.'),
    ('dialogue.skyabyss.attendant_smith.ch5_iron_tell2.ok', 'わかった', 'Got it'),
    ('dialogue.skyabyss.attendant_smith.menu.ch5_iron_show', 'ネザライトのインゴットを見せる', 'Show the netherite ingot'),
    ('dialogue.skyabyss.attendant_smith.ch5_iron_show', '……本物だ。よく持って帰った。\n器は打っておいた。鍵をここに。……ほら、ぴったりだ。前にも、同じ形の器を打った気がする。', '...The real thing. Good work bringing it back.\nI\'ve forged the cradle. Put the key here. ...There, a perfect fit. I feel like I\'ve forged one this shape before.'),
    ('dialogue.skyabyss.attendant_smith.ch5_iron_show2', 'それと、この型を持っていけ。ダイヤの品を黒い鉄へ打ち直す型の写しだ。……前のあんたも、同じ型を写していった。', 'And take this template. A copy of the one that reforges diamond gear into black iron. ...The you from before copied the same one.'),
    ('dialogue.skyabyss.attendant_smith.ch5_iron_show2.ok', 'ありがとう', 'Thank you'),
    # 戸と坑道
    ('message.skyabyss.ch5.door_locked', '戸は動かない。……鍵の脈が、まだ弱い。', 'The door does not move. ...The key\'s pulse is still faint.'),
    ('message.skyabyss.ch5.door_open', '鍵の脈に合わせて、封じた戸が低く鳴り、開いた。', 'In time with the key\'s pulse, the sealed door hums low and opens.'),
    ('message.skyabyss.ch5.landing_workshop', '灰の民の作業場。……ここで鼓動を運んでいたのか。', 'The ash folk\'s workshop. ...So this is where they carried the beat.'),
    ('message.skyabyss.ch5.landing_camp', '野営の跡。焚き火はまだ温かい気がする。帳面の字が、自分の字に似ている。', 'A camp\'s remains. The fire almost feels warm. The writing in the notebook looks like yours.'),
    ('message.skyabyss.ch5.threshold', '底の境。格子の門の向こうで、遠い鼓動が一つ鳴った。', 'The threshold. Beyond the barred gate, one distant beat sounds.'),
    ('message.skyabyss.ch5.gate_locked', '門の格子は動かない。……天の鍵があれば。', 'The bars of the gate do not move. ...If only you had the sky key.'),
    ('message.skyabyss.ch5.gate_open', '鍵の脈に応えて、門の格子が音もなく溶けた。向こうは、底の見えない闇。', 'Answering the key\'s pulse, the bars melt away without a sound. Beyond lies a dark with no bottom.'),
    ('message.skyabyss.abyss.rest', '灯りの休み場。……ここで倒れても、灯りがまた呼び戻してくれる。', 'A lantern rest. ...Fall below, and the light will call you back here.'),
    ('message.skyabyss.abyss.woke_at_rest', '灯りの火のそばで目を覚ました。', "You wake beside the lantern's flame."),
    # 深淵の寄り道 (story/ch5/AbyssFinds・AbyssShortcuts・AbyssHorror。36 の 8 章)
    ('message.skyabyss.abyss.carve.bone_1', '刻み「ここまでは、皆で来た。灯りは三つ。」', 'A carving: "We all made it this far. Three lights."'),
    ('message.skyabyss.abyss.carve.bone_2', '刻み「骨の橋を渡った。下を見るな、と誰かが言った。言ったのは私だった気がする。」', 'A carving: "We crossed the bone bridge. Someone said not to look down. I think it was me."'),
    ('message.skyabyss.abyss.carve.bone_3', '刻み「鯨の鼓動が、まだ壁の中で鳴っている。返したはずなのに。」', 'A carving: "The whale\'s beat still sounds inside the walls. We returned it, didn\'t we."'),
    ('message.skyabyss.abyss.carve.bone_4', '刻み「一つ目の灯りが消えた。名前は書かない。書けば、また会う気がする。」', 'A carving: "The first light went out. I won\'t write the name. If I do, I feel we\'ll meet again."'),
    ('message.skyabyss.abyss.grave.bone_1', '前の私の墓。名札は削れて読めない。骨の欠片を握ったまま眠っている。', 'A grave of one before. The name tag is worn away. They sleep with a bone shard in hand.'),
    ('message.skyabyss.abyss.grave.bone_2', '前の私の墓。名札に「七度目」とだけある。そばに、空の灯り。', 'A grave of one before. The tag only says "the seventh time". Beside it, an empty lamp.'),
    ('message.skyabyss.abyss.grave.bone_3', '前の私の墓。帳面の切れ端が石に挟まっている。「上で待っている人に、ごめんと」', 'A grave of one before. A scrap of notebook is wedged under the stone. "Tell the one waiting above I\'m sorry."'),
    ('lore.skyabyss.ch5.carvings_bone.title', '前の私の刻み（骨 の層）', 'Carvings of Those Before (Bone Layer)'),
    ('lore.skyabyss.ch5.carvings_bone.body', '深淵の骨の層の壁に、自分と同じ字の刻みが四つあった。上から順に。\n\n「ここまでは、皆で来た。灯りは三つ。」\n\n「骨の橋を渡った。下を見るな、と誰かが言った。言ったのは私だった気がする。」\n\n「鯨の鼓動が、まだ壁の中で鳴っている。返したはずなのに。」\n\n「一つ目の灯りが消えた。名前は書かない。書けば、また会う気がする。」', 'Four carvings in your own hand on the walls of the Bone layer, top to bottom.\n\n"We all made it this far. Three lights."\n\n"We crossed the bone bridge. Someone said not to look down. I think it was me."\n\n"The whale\'s beat still sounds inside the walls. We returned it, didn\'t we."\n\n"The first light went out. I won\'t write the name. If I do, I feel we\'ll meet again."'),
    ('lore.skyabyss.ch5.graves_bone.title', '前の私の墓（骨 の層）', 'Graves of Those Before (Bone Layer)'),
    ('lore.skyabyss.ch5.graves_bone.body', '深淵の骨の層で、前の私の墓を三つ見つけた。\n\n名札は削れて読めない。骨の欠片を握ったまま眠っている。\n\n名札に「七度目」とだけある。そばに、空の灯り。\n\n帳面の切れ端が石に挟まっている。「上で待っている人に、ごめんと」', 'Three graves of those before, found in the Bone layer.\n\nThe name tag is worn away. They sleep with a bone shard in hand.\n\nThe tag only says "the seventh time". Beside it, an empty lamp.\n\nA scrap of notebook is wedged under the stone. "Tell the one waiting above I\'m sorry."'),
    ('message.skyabyss.abyss.carve.salt_1', '刻み「潮が引くのを待つ。待つのは得意だ。何度も待った。」', 'A carving: "Waiting for the tide to ebb. I\'m good at waiting. I\'ve done it many times."'),
    ('message.skyabyss.abyss.carve.salt_2', '刻み「体が軽い。軽くなると、上に戻れる気がしてしまう。」', 'A carving: "My body feels light. Light enough that I start to think I could climb back up."'),
    ('message.skyabyss.abyss.carve.salt_3', '刻み「塩が、去年の私の字を覆っていた。同じことを書いていた。」', 'A carving: "Salt had covered last year\'s writing. Mine. It said the same thing."'),
    ('message.skyabyss.abyss.carve.salt_4', '刻み「二つ目の灯りが、上へ戻った。戻れる人がいるなら、それでいい。」', 'A carving: "The second light went back up. If anyone can return, that is enough."'),
    ('message.skyabyss.abyss.grave.salt_1', '前の私の墓。塩に覆われて、形だけが残っている。顔の向きは、上。', 'A grave of one before. Covered in salt, only the shape remains. The face is turned upward.'),
    ('message.skyabyss.abyss.grave.salt_2', '前の私の墓。名札に、自分と同じ名前。日付だけが違う。', 'A grave of one before. The tag bears your own name. Only the date is different.'),
    ('message.skyabyss.abyss.grave.salt_3', '前の私の墓。貝殻が三つ並べてある。誰かが、あとから供えたらしい。', 'A grave of one before. Three shells are laid in a row. Someone left them here later.'),
    ('lore.skyabyss.ch5.carvings_salt.title', '前の私の刻み（塩 の層）', 'Carvings of Those Before (Salt Layer)'),
    ('lore.skyabyss.ch5.carvings_salt.body', '深淵の塩の層の壁に、自分と同じ字の刻みが四つあった。上から順に。\n\n「潮が引くのを待つ。待つのは得意だ。何度も待った。」\n\n「体が軽い。軽くなると、上に戻れる気がしてしまう。」\n\n「塩が、去年の私の字を覆っていた。同じことを書いていた。」\n\n「二つ目の灯りが、上へ戻った。戻れる人がいるなら、それでいい。」', 'Four carvings in your own hand on the walls of the Salt layer, top to bottom.\n\n"Waiting for the tide to ebb. I\'m good at waiting. I\'ve done it many times."\n\n"My body feels light. Light enough that I start to think I could climb back up."\n\n"Salt had covered last year\'s writing. Mine. It said the same thing."\n\n"The second light went back up. If anyone can return, that is enough."'),
    ('lore.skyabyss.ch5.graves_salt.title', '前の私の墓（塩 の層）', 'Graves of Those Before (Salt Layer)'),
    ('lore.skyabyss.ch5.graves_salt.body', '深淵の塩の層で、前の私の墓を三つ見つけた。\n\n塩に覆われて、形だけが残っている。顔の向きは、上。\n\n名札に、自分と同じ名前。日付だけが違う。\n\n貝殻が三つ並べてある。誰かが、あとから供えたらしい。', 'Three graves of those before, found in the Salt layer.\n\nCovered in salt, only the shape remains. The face is turned upward.\n\nThe tag bears your own name. Only the date is different.\n\nThree shells are laid in a row. Someone left them here later.'),
    ('message.skyabyss.abyss.carve.storm_1', '刻み「雷の拍を数える。一、二、三。心臓と同じ速さだ。」', 'A carving: "Counting the thunder. One, two, three. The same pace as a heart."'),
    ('message.skyabyss.abyss.carve.storm_2', '刻み「拍に合わせれば渡れる。合わせられなくなったら、ここが墓だ。」', 'A carving: "Keep to the beat and you can cross. Lose it, and this is your grave."'),
    ('message.skyabyss.abyss.carve.storm_3', '刻み「翼の鼓動は優しかった。ここまで来て、ようやく分かった。」', 'A carving: "The wing\'s beat was gentle. I only understood it here."'),
    ('message.skyabyss.abyss.carve.storm_4', '刻み「私ひとりになった。灯りは、まだ一つある。」', 'A carving: "Only I am left. There is still one light."'),
    ('message.skyabyss.abyss.grave.storm_1', '前の私の墓。焦げた外套。手は、拍を数えるように指を折っている。', 'A grave of one before. A scorched cloak. The fingers are bent, as if counting beats.'),
    ('message.skyabyss.abyss.grave.storm_2', '前の私の墓。名札は雷で溶けている。銅の輪だけが残った。', 'A grave of one before. The tag has melted in the lightning. Only a copper ring remains.'),
    ('message.skyabyss.abyss.grave.storm_3', '前の私の墓。「ここで待つ。次の私が来たら、起こして」', 'A grave of one before. "I\'ll wait here. Wake me when the next me arrives."'),
    ('lore.skyabyss.ch5.carvings_storm.title', '前の私の刻み（雷 の層）', 'Carvings of Those Before (Storm Layer)'),
    ('lore.skyabyss.ch5.carvings_storm.body', '深淵の雷の層の壁に、自分と同じ字の刻みが四つあった。上から順に。\n\n「雷の拍を数える。一、二、三。心臓と同じ速さだ。」\n\n「拍に合わせれば渡れる。合わせられなくなったら、ここが墓だ。」\n\n「翼の鼓動は優しかった。ここまで来て、ようやく分かった。」\n\n「私ひとりになった。灯りは、まだ一つある。」', 'Four carvings in your own hand on the walls of the Storm layer, top to bottom.\n\n"Counting the thunder. One, two, three. The same pace as a heart."\n\n"Keep to the beat and you can cross. Lose it, and this is your grave."\n\n"The wing\'s beat was gentle. I only understood it here."\n\n"Only I am left. There is still one light."'),
    ('lore.skyabyss.ch5.graves_storm.title', '前の私の墓（雷 の層）', 'Graves of Those Before (Storm Layer)'),
    ('lore.skyabyss.ch5.graves_storm.body', '深淵の雷の層で、前の私の墓を三つ見つけた。\n\n焦げた外套。手は、拍を数えるように指を折っている。\n\n名札は雷で溶けている。銅の輪だけが残った。\n\n「ここで待つ。次の私が来たら、起こして」', 'Three graves of those before, found in the Storm layer.\n\nA scorched cloak. The fingers are bent, as if counting beats.\n\nThe tag has melted in the lightning. Only a copper ring remains.\n\n"I\'ll wait here. Wake me when the next me arrives."'),
    ('message.skyabyss.abyss.carve.ash_1', '刻み「暗い。灯りを点けると、見られている気がする。点けないと、もっと。」', 'A carving: "It\'s dark. When I light the lamp, I feel watched. When I don\'t, more so."'),
    ('message.skyabyss.abyss.carve.ash_2', '刻み「足音がついてくる。止まると止まる。私の歩き方だ。」', 'A carving: "Footsteps follow. They stop when I stop. They walk the way I do."'),
    ('message.skyabyss.abyss.carve.ash_3', '刻み「底に番人がいる。声を聞いた。あれは、私の声だった。」', 'A carving: "There is a warden at the bottom. I heard its voice. It was my voice."'),
    ('message.skyabyss.abyss.carve.ash_4', '刻み「次に来るあなたへ。灯りを、全部点けて。私は三つしか点けられなかった。」', 'A carving: "To you who come next. Light all of them. I could only light three."'),
    ('message.skyabyss.abyss.grave.ash_1', '前の私の墓。灰の中に、指の跡。最後まで灯りを探していた。', 'A grave of one before. Finger marks in the ash. They were searching for a light to the end.'),
    ('message.skyabyss.abyss.grave.ash_2', '前の私の墓。名札は真っ白。名前を書く前に、ここに来たらしい。', 'A grave of one before. The tag is blank. They came here before writing a name.'),
    ('message.skyabyss.abyss.grave.ash_3', '前の私の墓。墓の石の裏に、心臓の形が彫ってある。打っているように見える。', 'A grave of one before. A heart is carved on the back of the stone. It almost seems to beat.'),
    ('lore.skyabyss.ch5.carvings_ash.title', '前の私の刻み（灰 の層）', 'Carvings of Those Before (Ash Layer)'),
    ('lore.skyabyss.ch5.carvings_ash.body', '深淵の灰の層の壁に、自分と同じ字の刻みが四つあった。上から順に。\n\n「暗い。灯りを点けると、見られている気がする。点けないと、もっと。」\n\n「足音がついてくる。止まると止まる。私の歩き方だ。」\n\n「底に番人がいる。声を聞いた。あれは、私の声だった。」\n\n「次に来るあなたへ。灯りを、全部点けて。私は三つしか点けられなかった。」', 'Four carvings in your own hand on the walls of the Ash layer, top to bottom.\n\n"It\'s dark. When I light the lamp, I feel watched. When I don\'t, more so."\n\n"Footsteps follow. They stop when I stop. They walk the way I do."\n\n"There is a warden at the bottom. I heard its voice. It was my voice."\n\n"To you who come next. Light all of them. I could only light three."'),
    ('lore.skyabyss.ch5.graves_ash.title', '前の私の墓（灰 の層）', 'Graves of Those Before (Ash Layer)'),
    ('lore.skyabyss.ch5.graves_ash.body', '深淵の灰の層で、前の私の墓を三つ見つけた。\n\n灰の中に、指の跡。最後まで灯りを探していた。\n\n名札は真っ白。名前を書く前に、ここに来たらしい。\n\n墓の石の裏に、心臓の形が彫ってある。打っているように見える。', 'Three graves of those before, found in the Ash layer.\n\nFinger marks in the ash. They were searching for a light to the end.\n\nThe tag is blank. They came here before writing a name.\n\nA heart is carved on the back of the stone. It almost seems to beat.'),
    ('message.skyabyss.abyss.shortcut_closed', '巻き上げ機の鎖は上で絡まっている。……ここから回せば、ほどけそうだ。', 'The winch chain is tangled above. ...Turning it from here might free it.'),
    ('message.skyabyss.abyss.shortcut_open', '鎖がほどけ、上の休み場へ一本につながった。', 'The chain comes free, one line up to the rest above.'),
    ('message.skyabyss.abyss.shortcut_up', '鎖を巻き上げ、上の休み場へ。', 'You wind the chain up to the rest above.'),
    ('message.skyabyss.abyss.shortcut_down', '鎖を伝って、下の休み場へ。', 'You ride the chain down to the rest below.'),
    ('message.skyabyss.abyss.shortcut_top_closed', '鎖の掛け金。下で巻き上げ機を回せば、ここまで鎖が来るはずだ。', 'A chain anchor. If the winch below is turned, the chain should reach here.'),
    ('message.skyabyss.abyss.exit', '鎖をつかむと、上へ引かれた。', 'You grip the chain and are drawn upward.'),
    ('message.skyabyss.abyss.heart_1', '台の上の心臓に触れた。……冷たい。けれど、確かに打っている。', 'You touch the heart on the altar. ...Cold. Yet it is surely beating.'),
    ('message.skyabyss.abyss.heart_2', '鼓動が、足もとの世界ぜんぶへ根を張っているのが分かる。これは心臓で、錨だ。世界をここにつなぎとめる錨。', 'You can feel the beat rooting into the whole world underfoot. It is a heart, and an anchor. The anchor that holds the world in place.'),
    ('message.skyabyss.abyss.heart_3', '四つの巨神に鼓動を貸したせいで、錨は緩んでいた。返した今も、まだ足りない。……空の上で、月が少し近い。', 'Lending its beat to the four titans loosened the anchor. Even with them returned, it is not enough. ...Up in the sky, the moon is a little closer.'),
    # 深淵の圧 (story/ch5/AbyssPressure・client/AbyssHud。36 の A4)
    ('hud.skyabyss.abyss.depth', '深さ %s', 'Depth %s'),
    ('hud.skyabyss.abyss.weight', '体が重い %s%%', 'Heavy %s%%'),
    ('message.skyabyss.abyss.voice_full', '闇の中から、名前を呼ばれた気がする。……灯りを。', 'Something in the dark seemed to call your name. ...Light, now.'),
    # 心臓の番人 (story/ch5/warden。36 の 6)
    ('entity.skyabyss.heart_warden', '心臓の番人', 'Warden of the Heart'),
    ('message.skyabyss.warden.lighting', '灯りに火を移している……そばを離れないで。', 'Passing the flame to the lantern... stay close.'),
    ('message.skyabyss.warden.wake', '闇の奥で、何かが目を開けた。灯りを点けて、姿を照らそう。', 'Something opened its eyes in the dark. Light the lanterns and catch it in the glow.'),
    ('message.skyabyss.warden.shades', '番人の影が分かれた。鼓動の音がするのは、本物だけ。', 'The warden\'s shadow splits. Only the real one has a heartbeat.'),
    ('message.skyabyss.warden.wave', '心臓の台が三度鳴った。灯りが、全部消える。', 'The heart\'s altar tolls three times. Every light goes out.'),
    ('message.skyabyss.warden.fall', '番人が闇に溶けていく。台の上で、何かが脈打っている。', 'The warden melts into the dark. Something pulses on the altar.'),
]


def main():
    write(os.path.join(DATA, 'quest', 'main_abyss.json'), MAIN)
    write(os.path.join(DATA, 'quest', 'ch5', 'black_iron.json'), IRON)
    write(os.path.join(DATA, 'dialogue', 'ch5', 'storyteller_abyss.json'), ST_DIALOGUE)
    write(os.path.join(DATA, 'dialogue', 'ch5', 'smith_abyss.json'), SMITH_DIALOGUE)
    ja = {k: j for k, j, _ in TEXTS}
    en = {k: e for k, _, e in TEXTS}
    write(os.path.join(LANG, 'ja_jp.json'), ja)
    write(os.path.join(LANG, 'en_us.json'), en)
    print('wrote main_abyss, ch5/black_iron, 2 dialogues, %d texts' % len(TEXTS))


if __name__ == '__main__':
    main()
