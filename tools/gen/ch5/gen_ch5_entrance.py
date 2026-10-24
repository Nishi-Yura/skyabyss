# -*- coding: utf-8 -*-
"""五章の入口「足もとの鼓動」(docs/design/34 の 4.7)。深淵の中は 00 の 245 で決めるので、ここは底の境まで。

python -I tools/gen/ch5/gen_ch5_entrance.py
書くもの:
  data/skyabyss/quest/main_abyss.json            メイン: 噂 → 支度 (黒い鉄の器) → 封じた戸 → 大坑道 → 報告
  data/skyabyss/quest/ch5/black_iron.json        支度の段: ブレイズロッド 3・黒曜石 2 → 鍛冶師に見せる (鍛冶型の写し 1)
  data/skyabyss/dialogue/ch5/storyteller_abyss.json   語り部の一言 (噂・報告)
  data/skyabyss/dialogue/ch5/smith_abyss.json        鍛冶師の一言 (黒い鉄の頁・見せる)
  data/skyabyss/quest/ch5/{shaft_landings,party_rations,gate_sweep}.json   大坑道と骨の層のサブ (預かり所・宿・組合が頼む)
  data/skyabyss/dialogue/ch5/{store_landings,inn_rations,guild_sweep,survivor_bone_rations}.json   その頼みと報告の一言
  data/skyabyss/quest/{daily_abyss_rounds,weekly_abyss_haul}.json   深淵の日替わり・週替わり (言伝ての板)
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
        {'id': 'iron', 'xp': 150, 'objectives': [{'id': 'rod', 'type': 'collect', 'target': 'minecraft:blaze_rod', 'count': 3, 'party': False},
                                                 {'id': 'obsidian', 'type': 'collect', 'target': 'minecraft:obsidian', 'count': 2, 'party': False}]},
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
    ('quest.skyabyss.main_abyss.descend.reach', '心臓の間へ入ろう', 'Enter the heart chamber'),
    ('quest.skyabyss.main_abyss.warden.journal', '心臓の間は真っ暗だった。闇の中に、姿の見えない番人がいる。\n輪に並んだ灯りに火を移せば、その光の中でだけ番人の姿が見える。闇の中の番人には、攻撃がほとんど効かない。', 'The heart room is pitch dark. Somewhere in it is a warden you cannot see.\nPass the flame to the ring of lanterns: only in their light does the warden take shape. In the dark, your blows barely reach it.'),
    ('quest.skyabyss.main_abyss.warden.defeat', '灯りを点けて、心臓の番人を倒そう', 'Defeat the heart warden'),
    ('quest.skyabyss.main_abyss.heart.journal', '番人は闇に溶けた。真ん中の台の上で、何かが脈打っている。', 'The warden melted into the dark. On the altar in the middle, something pulses.'),
    ('quest.skyabyss.main_abyss.heart.touch', '台の上の心臓に触れよう', 'Touch the heart on the altar'),
    # 支度
    ('quest.skyabyss.ch5.black_iron.title', '黒い鉄の器', 'A Cradle of Black Iron'),
    ('quest.skyabyss.ch5.black_iron.summary', '鍵を収める器のために、ネザーで黒い鉄を打つ火と石を集める。', 'Gather fire and stone from the Nether to forge black iron for the key\'s cradle.'),
    ('quest.skyabyss.ch5.black_iron.done', '「器ができた。……この型も持っていけ。前のあんたも、同じ型を写していった」', '"The cradle\'s done. ...Take this template too. The you from before copied the same one."'),
    ('quest.skyabyss.ch5.black_iron.iron.journal', '鍛冶師が言っていた。「黒い鉄は、火の国の火でしか打てん。ブレイズの棒で炉を焚いて、黒曜石を溶かし込む」\nネザーの要塞で、ブレイズを倒してブレイズロッドを 3 本集めよう。黒曜石 2 つは、溶岩に水をかければ作れる（ダイヤのつるはしで掘る）。', 'The smith said: "Black iron can only be forged with the fire of the land of fire. Fire the forge with blaze rods and melt obsidian into it."\nFind a Nether fortress and defeat blazes for 3 blaze rods. Pour water on lava to make 2 obsidian (mine it with a diamond pickaxe).'),
    ('quest.skyabyss.main_abyss.prepare.lead', '鍛冶師「黒い鉄か。……火の国の火がいるな」', 'Smith: "Black iron, huh. ...That needs the fire of the land of fire."'),
    ('quest.skyabyss.ch5.black_iron.iron.rod', 'ネザーの要塞で、ブレイズロッドを集めよう', 'Collect blaze rods in the Nether'),
    ('quest.skyabyss.ch5.black_iron.iron.obsidian', '黒曜石を集めよう', 'Collect obsidian'),
    ('quest.skyabyss.ch5.black_iron.show.journal', 'ブレイズロッドと黒曜石がそろった。鍛冶師に見せよう。', 'The blaze rods and obsidian are ready. Show them to the smith.'),
    ('quest.skyabyss.ch5.black_iron.show.talk', 'ブレイズロッドと黒曜石を、鍛冶師に見せよう', 'Show the rods and obsidian to the smith'),
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
    ('dialogue.skyabyss.attendant_smith.ch5_iron_tell', '鍵を収める器？　……黒い鉄でなきゃ持たん。黒い鉄は、火の国の火でしか打てん。\nブレイズの棒が 3 本。あれで炉を焚いて、黒曜石を 2 つ溶かし込む。それで器は打てる。', 'A cradle for the key? ...Only black iron will hold. And black iron can only be forged with the fire of the land of fire.\nThree blaze rods to fire the forge, two obsidian melted into it. That\'s enough for the cradle.'),
    ('dialogue.skyabyss.attendant_smith.ch5_iron_tell2', 'ブレイズは火の国の砦にいる。火の玉を撃ってくるから、雪玉か盾を持っていけ。\n黒曜石は、溶岩に水をかければできる。ダイヤのつるはしで掘れ。', 'Blazes live in the fortresses of the land of fire. They throw fireballs, so bring snowballs or a shield.\nObsidian forms where water meets lava. Mine it with a diamond pickaxe.'),
    ('dialogue.skyabyss.attendant_smith.ch5_iron_tell2.ok', 'わかった', 'Got it'),
    ('dialogue.skyabyss.attendant_smith.menu.ch5_iron_show', 'ブレイズロッドと黒曜石を見せる', 'Show the blaze rods and obsidian'),
    ('dialogue.skyabyss.attendant_smith.ch5_iron_show', '……いい火だ。よく持って帰った。\n器は打っておいた。鍵をここに。……ほら、ぴったりだ。前にも、同じ形の器を打った気がする。', '...Good fire. Well done bringing it back.\nI\'ve forged the cradle. Put the key here. ...There, a perfect fit. I feel like I\'ve forged one this shape before.'),
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
    ('message.skyabyss.abyss.voice_dark', '暗がりにいると、淵の声が満ちてくる。満ちると目の前が暗くなる。灯りのそばで静まる。', 'Stay in the dark and the abyss voice rises; when it fills, your sight goes dark. It quiets near light.'),
    # 心臓の番人 (story/ch5/warden。36 の 6)
    ('entity.skyabyss.heart_warden', '心臓の番人', 'Warden of the Heart'),
    ('message.skyabyss.warden.lighting', '灯りに火を移している……そばを離れないで。', 'Passing the flame to the lantern... stay close.'),
    ('message.skyabyss.warden.wake', '闇の奥で、何かが目を開けた。灯りを点けて、姿を照らそう。', 'Something opened its eyes in the dark. Light the lanterns and catch it in the glow.'),
    ('message.skyabyss.warden.shades', '番人の影が分かれた。鼓動の音がするのは、本物だけ。', 'The warden\'s shadow splits. Only the real one has a heartbeat.'),
    ('message.skyabyss.warden.wave', '心臓の台が三度鳴った。灯りが、全部消える。', 'The heart\'s altar tolls three times. Every light goes out.'),
    ('message.skyabyss.warden.fall', '番人が闇に溶けていく。台の上で、何かが脈打っている。', 'The warden melts into the dark. Something pulses on the altar.'),
]


# ---------------------------------------------------------------- 大坑道と酒場の地下の頼み (2026-10-23 の点検: 四章の終わりから五章の帯への経験値の橋)
# 探掘隊 (隊長トウヤの七人。36 の 8 章の野営の生き残り) が都から降りたときの名残り。都の三人が、隊の補給と坑道の片付けを頼む。
# どれもサブ (メインの段は一つずつ)。戸を開けたあと (段 door を越えたら) 都の三人のメニューに出る。章の経験値の枠 (ChapterXpBudget) は四章までなので数えない
STORE = 'skyabyss:attendant_storage_keeper'
INN = 'skyabyss:attendant_innkeeper'
GUILD = 'skyabyss:attendant_guild_clerk'
MIO = 'skyabyss:attendant_survivor_bone'
BONE_FOES = ['skyabyss:bone_picker', 'skyabyss:rib_guard', 'skyabyss:bone_flute']
# 深淵の 18 種 (abyss/mobs の A 組 9・B 組 9。どの層の湧きの表 AbyssMobSpawner.TABLE にも出る)
ABYSS_FOES = BONE_FOES + ['skyabyss:salt_husk', 'skyabyss:tide_hands', 'skyabyss:white_shell_keeper',
                          'skyabyss:charged_moth', 'skyabyss:lightning_idol', 'skyabyss:scorched_bird',
                          'skyabyss:ash_trail', 'skyabyss:soot_child', 'skyabyss:ember_keeper', 'skyabyss:past_shadow',
                          'skyabyss:nameless_shape', 'skyabyss:lamp_eater', 'skyabyss:ceiling_walker', 'skyabyss:heartbeat_mimic',
                          'skyabyss:rope_hanger']


def not_(c):
    return {'type': 'not', 'of': c}


def past(*stages):
    # main_abyss を始めていて、並べた段のどれにもいない (= その先にいるか、終えた)
    return {'type': 'all', 'of': [{'type': 'quest', 'quest': 'main_abyss', 'is': 'started'},
                                  not_({'type': 'any', 'of': [q_active('main_abyss', s) for s in stages]})]}


AFTER_DOOR = past('rumor', 'prepare', 'door')
AFTER_GATE = past('rumor', 'prepare', 'door', 'shaft', 'gate')

SIDE = {
    'ch5/shaft_landings': {
        'category': 'sub', 'chapter': CH, 'order': 62, 'giver': STORE, 'min_stage': 4,
        'stages': [
            {'id': 'workshop', 'xp': 100, 'objectives': [{'id': 'reach', 'type': 'signal', 'targets': ['ch5_landing_1'], 'count': 1, 'party': False}]},
            {'id': 'camp', 'xp': 100, 'objectives': [{'id': 'reach', 'type': 'signal', 'targets': ['ch5_landing_2'], 'count': 1, 'party': False}]},
            {'id': 'tell', 'objectives': [{'id': 'talk', 'type': 'talk', 'target': STORE, 'topic': 'ch5s_back'}]},
        ],
        'reward': {'xp': 400, 'items': [{'item': 'minecraft:lantern', 'count': 3}], 'affinity': {STORE: 3}},
    },
    'ch5/party_rations': {
        'category': 'sub', 'chapter': CH, 'order': 63, 'giver': INN, 'min_stage': 4,
        'stages': [
            {'id': 'pack', 'objectives': [{'id': 'bread', 'type': 'collect', 'target': 'minecraft:bread', 'count': 12, 'party': False}]},
            {'id': 'deliver', 'xp': 150, 'objectives': [{'id': 'talk', 'type': 'talk', 'target': MIO, 'topic': 'ch5i_rations'}]},
            {'id': 'tell', 'objectives': [{'id': 'talk', 'type': 'talk', 'target': INN, 'topic': 'ch5i_back'}]},
        ],
        'reward': {'xp': 450, 'items': [{'item': 'minecraft:golden_carrot', 'count': 6}], 'affinity': {INN: 3}},
    },
    'ch5/gate_sweep': {
        'category': 'sub', 'chapter': CH, 'order': 64, 'giver': GUILD, 'min_stage': 4,
        'stages': [
            {'id': 'sweep', 'xp': 200, 'objectives': [{'id': 'kill', 'type': 'kill', 'targets': BONE_FOES, 'count': 15}]},
            {'id': 'tell', 'objectives': [{'id': 'talk', 'type': 'talk', 'target': GUILD, 'topic': 'ch5g_back'}]},
        ],
        'reward': {'xp': 500, 'items': [{'item': 'skyabyss:abyss_anchor_iron', 'count': 3}], 'affinity': {GUILD: 3}},
    },
}


def offer_dialogue(npc, quest, offer_if):
    # 都の人のメニューに足す: 頼み (まだ受けていない) → 受ける / やめる。報告 (段 tell) は入口と同じに先に出す
    return {
        'npc': npc, 'extends': True,
        'entries': [{'node': quest_node(quest, 'back'), 'if': q_active(quest, 'tell')}],
        'add_options': {'menu': {'before': 'about', 'options': [
            {'id': quest_node(quest, 'offer'), 'goto': quest_node(quest, 'offer'), 'kind': 'quest',
             'if': {'type': 'all', 'of': [offer_if, {'type': 'quest', 'quest': quest, 'is': 'none'}]}},
        ]}},
        'nodes': {
            quest_node(quest, 'offer'): {'face': 'neutral', 'next': quest_node(quest, 'offer') + '2'},
            quest_node(quest, 'offer') + '2': {'face': 'neutral', 'options': [
                {'id': 'go', 'goto': 'end', 'kind': 'quest', 'do': [{'type': 'quest_start', 'quest': quest}]},
                {'id': 'no', 'goto': 'menu', 'kind': 'talk'}]},
            quest_node(quest, 'back'): {'face': 'smile', 'options': [{'id': 'ok', 'goto': 'end', 'kind': 'talk'}]},
        },
    }


TOPIC = {'ch5/shaft_landings': 'ch5s', 'ch5/party_rations': 'ch5i', 'ch5/gate_sweep': 'ch5g'}


def quest_node(quest, kind):
    return '%s_%s' % (TOPIC[quest], kind)


SIDE_DIALOGUES = {
    'ch5/store_landings.json': offer_dialogue(STORE, 'ch5/shaft_landings', AFTER_DOOR),
    'ch5/inn_rations.json': offer_dialogue(INN, 'ch5/party_rations', AFTER_DOOR),
    'ch5/guild_sweep.json': offer_dialogue(GUILD, 'ch5/gate_sweep', AFTER_DOOR),
    # 灯り番のミオ (骨の野営) が、宿の主のパンを受け取る
    'ch5/survivor_bone_rations.json': {
        'npc': MIO, 'extends': True,
        'add_options': {'menu': {'before': 'bye', 'options': [
            {'id': 'ch5i_rations', 'goto': 'ch5i_rations', 'kind': 'quest',
             'if': {'type': 'all', 'of': [q_active('ch5/party_rations', 'deliver'), {'type': 'item', 'item': 'minecraft:bread', 'count': 12}]}},
        ]}},
        'nodes': {
            'ch5i_rations': {'face': 'smile', 'do': [{'type': 'take', 'item': 'minecraft:bread', 'count': 12}], 'next': 'ch5i_rations2'},
            'ch5i_rations2': {'face': 'neutral', 'options': [{'id': 'ok', 'goto': 'menu', 'kind': 'talk'}]},
        },
    },
}

# ---------------------------------------------------------------- 深淵の日替わり・週替わり (言伝ての板。10 の 8.6 節の形)
# 敵は深淵の湧きの表 (AbyssMobSpawner.TABLE) の 18 種。週替わりの品は上の二層の敵 (A 組) が落とす素材 (36 の 4.2 の落とし)
REPEATS = {
    'daily_abyss_rounds': {
        'category': 'sub', 'order': 69, 'repeat': 'daily', 'board': True, 'min_stage': 4, 'unlock': AFTER_GATE,
        'stages': [{'id': 'rounds', 'objectives': [{'id': 'kill', 'type': 'kill', 'targets': ABYSS_FOES, 'count': 12}]}],
        'reward': {'xp': 160, 'items': [{'item': 'skyabyss:abyss_anchor_iron', 'count': 1}, {'item': 'minecraft:bread', 'count': 4}]},
    },
    'weekly_abyss_haul': {
        'category': 'sub', 'order': 86, 'repeat': 'weekly', 'board': True, 'min_stage': 4, 'unlock': AFTER_GATE,
        'stages': [{'id': 'haul', 'objectives': [
            {'id': 'chips', 'type': 'turn_in', 'target': 'skyabyss:abyss_bone_chip', 'count': 8},
            {'id': 'salt', 'type': 'turn_in', 'target': 'skyabyss:abyss_salt_crystal', 'count': 6}]}],
        'reward': {'xp': 450, 'items': [{'item': 'skyabyss:abyss_anchor_iron', 'count': 4}, {'item': 'skyabyss:resonance_crystal', 'count': 1}]},
    },
}

TEXTS += [
    # 踊り場の荷 (預かり所)
    ('quest.skyabyss.ch5.shaft_landings.title', '踊り場の荷', 'Crates on the Landings'),
    ('quest.skyabyss.ch5.shaft_landings.summary', '探掘隊が大坑道の踊り場に置いていった荷が、まだあるか確かめる。', 'Check whether the crates the excavation party left on the shaft landings are still there.'),
    ('quest.skyabyss.ch5.shaft_landings.done', '「帳面の荷は、そろっていました。……取りに戻る人が、いなかっただけで」', '"Every crate in the ledger was there. ...There was just no one left to come back for them."'),
    ('quest.skyabyss.ch5.shaft_landings.workshop.journal', '預かり所の帳面に、探掘隊の荷が二つ残っている。「灰の民の作業場」と「野営」。どちらも大坑道の踊り場だという。\n上の踊り場から見てこよう。', 'The storage ledger still lists two of the excavation party\'s crates: "the ash folk\'s workshop" and "the camp". Both are landings in the great shaft.\nStart with the upper landing.'),
    ('quest.skyabyss.ch5.shaft_landings.workshop.reach', '大坑道の上の踊り場（作業場）へ行こう', 'Reach the upper landing (the workshop)'),
    ('quest.skyabyss.ch5.shaft_landings.camp.journal', '作業場の隅に、帳面と同じ印の木箱があった。中は綱と楔。\nもう一つは、下の踊り場の野営に置いたらしい。', 'In a corner of the workshop stood a crate with the ledger\'s mark. Rope and wedges inside.\nThe other was left at the camp on the lower landing.'),
    ('quest.skyabyss.ch5.shaft_landings.camp.reach', '大坑道の下の踊り場（野営）へ行こう', 'Reach the lower landing (the camp)'),
    ('quest.skyabyss.ch5.shaft_landings.tell.journal', '野営の木箱には、乾いたパンと油が残っていた。封は切られていない。\n預かり所に知らせよう。', 'The camp crate still held dry bread and oil, the seal unbroken.\nLet the storage keeper know.'),
    ('quest.skyabyss.ch5.shaft_landings.tell.talk', '踊り場の荷のことを、預かり所に知らせよう', 'Tell the storage keeper about the crates'),
    ('dialogue.skyabyss.attendant_storage_keeper.menu.ch5s_offer', '探掘隊の荷のこと', 'About the excavation party\'s crates'),
    ('dialogue.skyabyss.attendant_storage_keeper.ch5s_offer', '酒場の下の戸が開いたそうですね。……帳面に、ずっと消せない行があるんです。\n十年ほど前、探掘隊が大坑道へ降りるときに預けた荷です。二つの踊り場に置く、と書いてある。', 'I hear the door under the tavern is open. ...There are lines in my ledger I have never been able to strike out.\nCrates the excavation party left when they went down the great shaft, ten years or so ago. Two landings, it says.'),
    ('dialogue.skyabyss.attendant_storage_keeper.ch5s_offer2', '取りに来た人はいません。まだあるのか、それとも誰かが持って降りたのか。\n降りるついでに、見てきてもらえませんか。', 'No one ever came back for them. Are they still there, or did someone carry them further down?\nIf you\'re going down anyway, would you take a look?'),
    ('dialogue.skyabyss.attendant_storage_keeper.ch5s_offer2.go', '見てくる', 'I\'ll look'),
    ('dialogue.skyabyss.attendant_storage_keeper.ch5s_offer2.no', '今はいい', 'Not now'),
    ('dialogue.skyabyss.attendant_storage_keeper.ch5s_back', '封が切られていない……そうですか。\n帳面の行は、このまま残しておきます。消すのは、誰かが帰ってきてからにします。これは灯りの足しに。', 'The seals unbroken... I see.\nI\'ll leave those lines in the ledger. I\'ll strike them when someone comes home. Take these for light.'),
    ('dialogue.skyabyss.attendant_storage_keeper.ch5s_back.ok', '受け取る', 'Thanks'),
    # 隊の糧 (宿)
    ('quest.skyabyss.ch5.party_rations.title', '隊の糧', 'Rations for the Party'),
    ('quest.skyabyss.ch5.party_rations.summary', '宿の主が探掘隊に持たせていたパンを、深淵の骨の野営へ届ける。', 'Carry bread from the innkeeper to the bone camp in the abyss, as the inn once did for the excavation party.'),
    ('quest.skyabyss.ch5.party_rations.done', '「生きてたか。……次の分も、焼いておくよ」', '"She\'s alive. ...I\'ll bake the next batch, then."'),
    ('quest.skyabyss.ch5.party_rations.pack.journal', '宿の主が言っていた。探掘隊が降りるとき、宿は毎回パンを持たせた。最後の便は届けられないままだった、と。\nパンを 12 個そろえよう。パンは小麦 3 つから作業台で作れる。', 'The innkeeper said the inn sent bread with the excavation party every time they went down. The last batch was never delivered.\nGather 12 bread. Three wheat make a loaf at a crafting table.'),
    ('quest.skyabyss.ch5.party_rations.pack.bread', '届けるパンを 12 個そろえよう', 'Gather 12 bread to deliver'),
    ('quest.skyabyss.ch5.party_rations.deliver.journal', 'パンがそろった。深淵の骨の層の野営に、隊の生き残りがいるはずだ。届けよう。', 'The bread is ready. A survivor of the party should be at the camp in the bone layer of the abyss. Take it to her.'),
    ('quest.skyabyss.ch5.party_rations.deliver.talk', '骨の野営の灯り番に、パンを届けよう', 'Bring the bread to the lamp keeper at the bone camp'),
    ('quest.skyabyss.ch5.party_rations.tell.journal', '灯り番のミオは、パンを半分だけ食べて、残りを隊長の分だと言って包んだ。\n宿の主に伝えよう。', 'Mio the lamp keeper ate half the bread and wrapped the rest, saying it was the captain\'s share.\nTell the innkeeper.'),
    ('quest.skyabyss.ch5.party_rations.tell.talk', '届けたことを、宿の主に伝えよう', 'Tell the innkeeper it was delivered'),
    ('dialogue.skyabyss.attendant_innkeeper.menu.ch5i_offer', '探掘隊のこと', 'About the excavation party'),
    ('dialogue.skyabyss.attendant_innkeeper.ch5i_offer', '下へ行くのか。……あの隊も、ここで寝て、朝に降りていった。\n降りるたびに、うちのパンを持たせた。最後の便だけ、渡せなかった。', 'Going down, are you. ...That party slept here too, and went down in the morning.\nEvery time, I sent our bread with them. Only the last batch never went.'),
    ('dialogue.skyabyss.attendant_innkeeper.ch5i_offer2', 'パンを 12、持っていってくれないか。もし、まだ誰か下にいるなら。\n……いなかったら、あんたが食べればいい。', 'Take twelve loaves down, would you. In case anyone is still below.\n...If no one is, eat them yourself.'),
    ('dialogue.skyabyss.attendant_innkeeper.ch5i_offer2.go', '届ける', 'I\'ll take them'),
    ('dialogue.skyabyss.attendant_innkeeper.ch5i_offer2.no', '今はいい', 'Not now'),
    ('dialogue.skyabyss.attendant_innkeeper.ch5i_back', '灯り番の娘が……そうか。隊長の分を取っておいた、か。\n次の分も焼いておく。降りるときは、声をかけてくれ。', 'The lamp keeper girl... I see. Saved the captain\'s share, did she.\nI\'ll bake the next batch. Let me know when you go down.'),
    ('dialogue.skyabyss.attendant_innkeeper.ch5i_back.ok', '頼む', 'Please do'),
    ('dialogue.skyabyss.attendant_survivor_bone.menu.ch5i_rations', '宿の主のパンを渡す', 'Give her the innkeeper\'s bread'),
    ('dialogue.skyabyss.attendant_survivor_bone.ch5i_rations', '鐘の宿のパン……。この匂い、覚えてる。降りる朝に、いつも焼きたてだった', 'Bread from the Bell Inn... I remember this smell. It was always fresh the morning we went down.'),
    ('dialogue.skyabyss.attendant_survivor_bone.ch5i_rations2', '半分だけ、もらう。残りは隊長の分。……持っていけたら、いいんだけど', 'I\'ll take half. The rest is the captain\'s share. ...If only I could take it to him.'),
    ('dialogue.skyabyss.attendant_survivor_bone.ch5i_rations2.ok', 'わかった', 'I see'),
    # 門の下の骨拾い (組合)
    ('quest.skyabyss.ch5.gate_sweep.title', '門の下の骨拾い', 'Below the Gate'),
    ('quest.skyabyss.ch5.gate_sweep.summary', '深淵の骨の層で、骨拾い・肋の番・骨笛を十五体減らす。仲間の分も数える。', 'Thin out fifteen bone pickers, rib guards or bone flutes in the bone layer of the abyss. Party members count too.'),
    ('quest.skyabyss.ch5.gate_sweep.done', '「これで、門の下の音も少しは静かになるでしょう」', '"That should quiet the noise below the gate a little."'),
    ('quest.skyabyss.ch5.gate_sweep.sweep.journal', '組合の受付が言っていた。門が開いてから、夜になると大坑道の底で骨の鳴る音がする。探掘隊も、最初は骨の層で道を拓いたという。\n門の下の骨の層で、骨拾い・肋の番・骨笛を減らそう。肋の番は盾の正面を避けて、横から。', 'The Whale Chasers\' clerk said that since the gate opened, bones rattle at the bottom of the shaft at night. The excavation party also cleared a path through the bone layer first.\nThin out the bone pickers, rib guards and bone flutes in the bone layer below the gate. Hit rib guards from the side, not into the shield.'),
    ('quest.skyabyss.ch5.gate_sweep.sweep.kill', '骨の層で、骨拾い・肋の番・骨笛を倒そう', 'Defeat bone pickers, rib guards and bone flutes'),
    ('quest.skyabyss.ch5.gate_sweep.tell.journal', '骨の層の音が、少し遠のいた。組合に知らせよう。', 'The rattle in the bone layer has faded a little. Report to the Whale Chasers\' Guild.'),
    ('quest.skyabyss.ch5.gate_sweep.tell.talk', '骨の層のことを、組合の受付に知らせよう', "Report to the Whale Chasers\' clerk"),
    ('dialogue.skyabyss.attendant_guild_clerk.menu.ch5g_offer', '大坑道の底の音のこと', 'About the noise at the bottom of the shaft'),
    ('dialogue.skyabyss.attendant_guild_clerk.ch5g_offer', '門が開いてから、夜になると、酒場の床の下で骨の鳴る音がすると苦情が来ています。\n探掘隊の記録では、門の下の層は骨ばかりで、最初にそこで道を拓いたそうです。', 'Since the gate opened, we\'ve had complaints of bones rattling under the tavern floor at night.\nThe excavation party\'s records say the layer below the gate is all bone, and they cleared their path there first.'),
    ('dialogue.skyabyss.attendant_guild_clerk.ch5g_offer2', '組合として、正式に頼みます。骨の層で、十五ほど数を減らしてきてください。お礼は、隊の残した錨鉄で。', 'Officially, from the Whale Chasers\' Guild: please thin them out in the bone layer, fifteen or so. We\'ll pay in the anchor iron the party left behind.'),
    ('dialogue.skyabyss.attendant_guild_clerk.ch5g_offer2.go', '引き受ける', 'I\'ll take it'),
    ('dialogue.skyabyss.attendant_guild_clerk.ch5g_offer2.no', '今はいい', 'Not now'),
    ('dialogue.skyabyss.attendant_guild_clerk.ch5g_back', '確かに。苦情の紙は、これで片付けられます。\nこちらがお礼の錨鉄です。……探掘隊の名前で、組合の帳面に記しておきますね。', 'Confirmed. I can file away the complaints now.\nHere is the anchor iron. ...I\'ll record it in the guild ledger under the excavation party\'s name.'),
    ('dialogue.skyabyss.attendant_guild_clerk.ch5g_back.ok', '受け取る', 'Thanks'),
    # 深淵の日替わり・週替わり
    ('quest.skyabyss.daily_abyss_rounds.title', '縦穴の見回り', 'Rounds in the Abyss'),
    ('quest.skyabyss.daily_abyss_rounds.summary', '深淵の層で、深淵の敵を十二体倒す。仲間の分も数える。', 'Defeat twelve foes in the layers of the abyss. Party members count too.'),
    ('quest.skyabyss.daily_abyss_rounds.done', '今夜は、酒場の床の下も静かだろう。', 'The tavern floor should be quiet tonight.'),
    ('quest.skyabyss.daily_abyss_rounds.rounds.journal', '板の紙（組合の判）：「深淵の層を見回り、這い上がってくるものを十二ほど減らしてほしい。どの層でもよい」', 'A note on the board, with the Whale Chasers\' seal: "Patrol the layers of the abyss and thin out twelve or so of what crawls upward. Any layer will do."'),
    ('quest.skyabyss.daily_abyss_rounds.rounds.kill', '深淵の層で、敵を倒そう', 'Defeat foes in the abyss'),
    ('quest.skyabyss.daily_abyss_rounds.unlock_hint', '深淵の門をくぐると、受けられる', 'Available once you have passed the abyss gate'),
    ('quest.skyabyss.weekly_abyss_haul.title', '深淵の荷', 'A Haul from the Abyss'),
    ('quest.skyabyss.weekly_abyss_haul.summary', '骨の層と塩の層の敵が落とす骨のかけら 8 と塩の結晶 6 を、板に納める。', 'Deliver 8 bone chips and 6 salt crystals, dropped by foes of the bone and salt layers, to the board.'),
    ('quest.skyabyss.weekly_abyss_haul.done', '荷は、組合の蔵に収まった。', 'The haul is in the Whale Chasers\' storehouse.'),
    ('quest.skyabyss.weekly_abyss_haul.haul.journal', '板の紙（預かり所の字）：「探掘隊がしていたように、深淵の品を上へ運んでほしい。骨のかけら八つ、塩の結晶六つ。七日に一度で十分です」', 'A note on the board, in the storage keeper\'s hand: "Carry abyss goods up, as the excavation party used to. Eight bone chips, six salt crystals. Once every seven days is enough."'),
    ('quest.skyabyss.weekly_abyss_haul.haul.chips', '骨のかけらを納めよう', 'Deliver bone chips'),
    ('quest.skyabyss.weekly_abyss_haul.haul.salt', '塩の結晶を納めよう', 'Deliver salt crystals'),
    ('quest.skyabyss.weekly_abyss_haul.unlock_hint', '深淵の門をくぐると、受けられる', 'Available once you have passed the abyss gate'),
    # 深淵の初めての経験値 (story/ch5/layers の印・層の主)
    ('growth.skyabyss.reason.abyss_mark', '深淵の印', 'Abyss mark'),
    ('growth.skyabyss.reason.abyss_lord', '層の主を倒した', 'Layer lord defeated'),
]


def main():
    write(os.path.join(DATA, 'quest', 'main_abyss.json'), MAIN)
    write(os.path.join(DATA, 'quest', 'ch5', 'black_iron.json'), IRON)
    write(os.path.join(DATA, 'dialogue', 'ch5', 'storyteller_abyss.json'), ST_DIALOGUE)
    write(os.path.join(DATA, 'dialogue', 'ch5', 'smith_abyss.json'), SMITH_DIALOGUE)
    for qid, q in SIDE.items():
        write(os.path.join(DATA, 'quest', qid + '.json'), q)
    for name, d in SIDE_DIALOGUES.items():
        write(os.path.join(DATA, 'dialogue', name), d)
    for qid, q in REPEATS.items():
        write(os.path.join(DATA, 'quest', qid + '.json'), q)
    ja = {k: j for k, j, _ in TEXTS}
    en = {k: e for k, _, e in TEXTS}
    write(os.path.join(LANG, 'ja_jp.json'), ja)
    write(os.path.join(LANG, 'en_us.json'), en)
    print('wrote main_abyss, ch5/black_iron, %d side quests, %d repeats, %d dialogues, %d texts'
          % (len(SIDE), len(REPEATS), 2 + len(SIDE_DIALOGUES), len(TEXTS)))


if __name__ == '__main__':
    main()
