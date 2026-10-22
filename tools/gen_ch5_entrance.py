# -*- coding: utf-8 -*-
"""五章の入口「足もとの鼓動」(docs/design/34 の 4.7)。深淵の中は 00 の 245 で決めるので、ここは底の境まで。

python -I tools/gen_ch5_entrance.py
書くもの:
  data/skyabyss/quest/main_abyss.json            メイン: 噂 → 支度 (黒い鉄の器) → 封じた戸 → 大坑道 → 報告
  data/skyabyss/quest/ch5/black_iron.json        支度の段: ネザライトのインゴット 1 → 鍛冶師に見せる (鍛冶型の写し 1)
  data/skyabyss/dialogue/ch5/storyteller_abyss.json   語り部の一言 (噂・報告)
  data/skyabyss/dialogue/ch5/smith_abyss.json        鍛冶師の一言 (黒い鉄の頁・見せる)
  assets/skyabyss_ch5/lang/ja_jp.json・en_us.json
"""
from __future__ import annotations

import json
import os

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..')
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
        {'id': 'door', 'xp': 80, 'objectives': [{'id': 'open', 'type': 'signal', 'targets': ['ch5_abyss_door'], 'count': 1, 'party': False}]},
        {'id': 'shaft', 'xp': 120, 'objectives': [{'id': 'reach', 'type': 'signal', 'targets': ['ch5_threshold'], 'count': 1, 'party': False}]},
        {'id': 'report', 'objectives': [{'id': 'talk', 'type': 'talk', 'target': ST, 'topic': 'ch5_report'}]},
    ],
    'reward': {'xp': 300, 'affinity': {ST: 5}},
}

IRON = {
    'category': 'main', 'chapter': CH, 'order': 61, 'giver': SMITH,
    'start_when': {'type': 'quest', 'quest': 'main_abyss', 'is': 'started'},
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
    ('quest.skyabyss.main_abyss.done', '「鼓動は、まだ遠い。……でも、あんたの足音は、もう向こうに届いてる」', '"The beat is still far off. ...But your footsteps have already reached the other side."'),
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
    ('quest.skyabyss.main_abyss.report.journal', '底の境の門は、まだ開かなかった。鼓動が遠い。語り部に伝えよう。', 'The gate at the threshold did not open yet. The beat is far. Tell the storyteller.'),
    ('quest.skyabyss.main_abyss.report.talk', '底の境で見たことを、語り部に話そう', 'Tell the storyteller about the gate'),
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
    ('dialogue.skyabyss.storyteller.ch5_report', '門が開かなかった、か。……鼓動が遠い、ね。\n四つ返しても、まだ足りないのかもしれない。それとも、向こうが、まだあんたを待ってないのか。', 'The gate didn\'t open... The beat is far, you say.\nMaybe four returned is still not enough. Or maybe the other side isn\'t waiting for you yet.'),
    ('dialogue.skyabyss.storyteller.ch5_report2', '数え歌の、いつつめ。「いきづく　つちのそこ」。……息をしてるんだよ、底は。\nそのうち、向こうから呼ぶさ。その時は、あたしにも聞かせておくれ。', 'The fifth verse. "Breathing, the bottom of the earth." ...It\'s breathing, down there.\nIt will call for you, sooner or later. When it does, let me hear it too.'),
    ('dialogue.skyabyss.storyteller.ch5_report2.ok', 'そうする', 'I will'),
    # 鍛冶師
    ('dialogue.skyabyss.attendant_smith.menu.ch5_iron_tell', '黒い鉄の器のこと', 'About the black iron cradle'),
    ('dialogue.skyabyss.attendant_smith.ch5_iron_tell', '鍵を収める器？　……黒い鉄でなきゃ持たん。火の国の底、溶岩の海の下に、古代の残骸が埋まってる。\n掘り出して炉で溶かし、金と合わせればインゴットになる。一つあれば器は打てる。', 'A cradle for the key? ...Only black iron will hold. At the bottom of the land of fire, under the lava sea, ancient debris lies buried.\nDig it out, smelt it, add gold, and you have an ingot. One is enough for the cradle.'),
    ('dialogue.skyabyss.attendant_smith.ch5_iron_tell2', '残骸は硬い。ダイヤのつるはしで掘れ。……爆ぜるもので掘ると早いが、溶岩に気をつけろ。', 'The debris is hard. Use a diamond pickaxe. ...Blasting is faster, but mind the lava.'),
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
    ('message.skyabyss.ch5.threshold', '底の境。門は閉じている。向こうで、遠い鼓動が一つ鳴った。', 'The threshold. The gate is closed. Beyond it, one distant beat sounds.'),
    # 深淵の圧 (story/ch5/AbyssPressure・client/AbyssHud。36 の A4)
    ('hud.skyabyss.abyss.depth', '深さ %s', 'Depth %s'),
    ('hud.skyabyss.abyss.weight', '体が重い %s%%', 'Heavy %s%%'),
    ('message.skyabyss.abyss.voice_full', '闇の中から、名前を呼ばれた気がする。……灯りを。', 'Something in the dark seemed to call your name. ...Light, now.'),
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
