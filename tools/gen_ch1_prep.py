#!/usr/bin/env python3
"""一章の備えの導きと新しい場所 (docs/design/21 の 3.1〜3.2、P6) のデータ・文・仮の絵を書き出す。

  python tools/gen_ch1_prep.py

書くもの:
  data/skyabyss/quest/ch1/prep_gear|prep_blade|prep_pulse.json   備えの頼み 3 本 (07 の 6.8 の型)
  data/skyabyss/dialogue/ch1/storyteller_prep.json               語り部の入口 3 つ (職人 P4 が入るまでの代用。TODO は story/ch1/Ch1Prep)
  data/skyabyss/dialogue/ch1/whale_chaser_prep.json              イサナの備えの一言 3 つ
  data/skyabyss/lore/entry/ch1/road_cairns|song_chamber|cleft_cave.json
  assets/skyabyss_ch1/lang/{ja_jp,en_us}.json  文 (既存のキーに混ぜる。キー順)
  assets/skyabyss/textures/item/{rock_shell_scrap,bone_chip,hare_quill,rock_shell_thread}.png と models/item/*.json (仮の絵。右上にマゼンタの印)
語りの決まり (21 の 0.2・8): 心臓・深淵・巨神の眠りの理由を書かない。暮らしの小さな変化とほのめかしだけ。数字は言わない。
"""
import json
import os
import struct
import zlib

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, 'src', 'main', 'resources')

# 職人 (P4) が入ったら、ここを 'skyabyss:artisan' にして、会話を dialogue/ch1/artisan.json へ移す
GIVER = 'skyabyss:storyteller'
ITEM_ROCK_SHELL = 'skyabyss:rock_shell_scrap'
ITEM_THREAD = 'skyabyss:rock_shell_thread'

ja = {}
en = {}


def put(key, j, e):
    ja[key] = j
    en[key] = e


def write_json(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w', encoding='utf-8', newline='\n') as f:
        json.dump(data, f, ensure_ascii=False, indent=2)
        f.write('\n')


START = {
    'type': 'all',
    'of': [
        {'type': 'stage', 'max': 0},
        {'type': 'lore', 'entry': 'chapter1/whale_chaser'},
        {'type': 'day', 'min': 1},
    ],
}


# ---------------------------------------------------------------- クエスト
def quests():
    write_json(os.path.join(RES, 'data/skyabyss/quest/ch1/prep_gear.json'), {
        'category': 'sub', 'order': 42, 'giver': GIVER, 'chapter': 'titan_whale', 'start_when': START,
        'stages': [
            {'id': 'gather', 'mode': 'all', 'objectives': [
                {'id': 'iron', 'type': 'collect', 'target': 'minecraft:iron_ingot', 'count': 8, 'party': False},
                {'id': 'scrap', 'type': 'collect', 'target': ITEM_ROCK_SHELL, 'count': 12, 'party': False}]},
            # TODO(P5): 旅装を作ったら合図 ch1_travel_gear_made が立つ (どちらか。会話だけでも済む)
            {'id': 'weave', 'mode': 'any', 'objectives': [
                {'id': 'talk', 'type': 'talk', 'target': GIVER, 'topic': 'ch1p_gear_weave'},
                {'id': 'made', 'type': 'signal', 'target': 'ch1_travel_gear_made', 'party': False}]},
        ],
        'reward': {'xp': 80, 'items': []},
    })
    write_json(os.path.join(RES, 'data/skyabyss/quest/ch1/prep_blade.json'), {
        'category': 'sub', 'order': 43, 'giver': GIVER, 'chapter': 'titan_whale', 'start_when': START,
        'stages': [
            # TODO(P5・P7): 砥石を使って洞の主 (老いた歩哨) を倒す条件をここへ足す。今は洞を抜ける (敵を倒しきる) だけ
            {'id': 'cave', 'xp': 60, 'objectives': [
                {'id': 'clear', 'type': 'signal', 'target': 'site_clear:ch1_cleft_shell_cave', 'party': False}]},
            {'id': 'report', 'objectives': [
                {'id': 'talk', 'type': 'talk', 'target': GIVER, 'topic': 'ch1p_blade_report'}]},
        ],
        'reward': {'xp': 100, 'items': [], 'do': [{'type': 'lore', 'entry': 'ch1/cleft_cave'}]},
    })
    write_json(os.path.join(RES, 'data/skyabyss/quest/ch1/prep_pulse.json'), {
        'category': 'sub', 'order': 44, 'giver': GIVER, 'chapter': 'titan_whale', 'start_when': START,
        'stages': [
            {'id': 'thread', 'objectives': [
                {'id': 'thread', 'type': 'collect', 'target': ITEM_THREAD, 'count': 1, 'party': False}]},
            {'id': 'weave', 'objectives': [
                {'id': 'talk', 'type': 'talk', 'target': GIVER, 'topic': 'ch1p_pulse_weave'}]},
        ],
        'reward': {'xp': 60, 'items': [{'item': 'skyabyss:heartbeat_ward_stone_whale', 'count': 1}]},
    })

    # ---- 文
    put('quest.skyabyss.ch1.prep_gear.title', '身ごしらえ', 'Packing for the Road')
    put('quest.skyabyss.ch1.prep_gear.summary', '鉄と岩殻片をそろえて、旅の支度を編んでもらう。', 'Gather iron and rock-shell scraps, and have the road gear stitched.')
    put('quest.skyabyss.ch1.prep_gear.gather.journal',
        '道の脇の荷車のそばに、誰かの荷が置いてあった。「道具は覚えている。持ち主は、そうでもない」\n鉄と、灰青の殻の欠片があれば、この先の道に耐える支度が編めるらしい。',
        'Someone had left a pack by the cart beside the road. "The tools remember. The owner, less so."\nWith iron and shards of grey-blue shell, a set fit for the road ahead can be stitched.')
    put('quest.skyabyss.ch1.prep_gear.gather.iron', '鉄を集める', 'Gather iron ingots')
    put('quest.skyabyss.ch1.prep_gear.gather.scrap', '岩殻片を集める', 'Gather rock-shell scraps')
    put('quest.skyabyss.ch1.prep_gear.weave.journal', '材料はそろった。語り部に見せに行こう。', 'The materials are ready. Show them to the storyteller.')
    put('quest.skyabyss.ch1.prep_gear.weave.talk', '語り部に支度を頼む', 'Ask the storyteller to stitch the gear')
    put('quest.skyabyss.ch1.prep_gear.weave.made', '旅装を作る', 'Make the road gear')
    put('quest.skyabyss.ch1.prep_gear.done', '「縫い目は、荷を持ったぶんだけ締まる。……歩いておいで」', '"A seam only tightens under a load. ...Go and walk in it."')

    put('quest.skyabyss.ch1.prep_blade.title', '刃を研ぐ', 'Edge to the Stone')
    put('quest.skyabyss.ch1.prep_blade.summary', '割れ殻の洞を抜けて、刃の使い方を確かめる。', 'Go through the Cleft Shell cave and test how you wield a blade.')
    put('quest.skyabyss.ch1.prep_blade.cave.journal',
        '丘の斜面に、岩の割れ目がある。湿った風が出ているそうだ。\n中の敵を一人で倒せないなら、この先で刃は役に立たない。盾の前は避け、背を取る。',
        'There is a crack in a hillside, and a damp wind comes out of it.\nIf the things inside are too much for you, a blade will not help you further on. Avoid the front of a shield; get behind.')
    put('quest.skyabyss.ch1.prep_blade.cave.clear', '割れ殻の洞を抜ける', 'Clear the Cleft Shell cave')
    put('quest.skyabyss.ch1.prep_blade.report.journal', '洞を抜けた。語り部に話そう。', 'You came through the cave. Tell the storyteller.')
    put('quest.skyabyss.ch1.prep_blade.report.talk', '語り部に話す', 'Talk to the storyteller')
    put('quest.skyabyss.ch1.prep_blade.done', '「刃は、研いだぶんだけ軽くなる。……腕のほうも」', '"A blade lightens as you hone it. So does the arm."')

    put('quest.skyabyss.ch1.prep_pulse.title', '鼓動を聞く', 'Listening for the Beat')
    put('quest.skyabyss.ch1.prep_pulse.summary', '洞から護り糸を持ち帰り、護りを編んでもらう。', 'Bring a ward thread back from the cave and have a charm woven.')
    put('quest.skyabyss.ch1.prep_pulse.thread.journal',
        '割れ殻の洞の奥に、殻の護り糸が眠っているという。糸は、鼓動に合わせてゆるむ。\n洞を抜けた人の手に、一本だけ渡るそうだ。',
        'Deep in the Cleft Shell cave lies a thread of shell ward. It loosens in time with a beat.\nOne length passes to whoever comes through.')
    put('quest.skyabyss.ch1.prep_pulse.thread.thread', '殻の護り糸を持ち帰る', 'Bring back a thread of shell ward')
    put('quest.skyabyss.ch1.prep_pulse.weave.journal', '糸を手に入れた。語り部に、護りを編んでもらおう。', 'You have the thread. Ask the storyteller to weave it into a charm.')
    put('quest.skyabyss.ch1.prep_pulse.weave.talk', '護りを編んでもらう', 'Have the charm woven')
    put('quest.skyabyss.ch1.prep_pulse.done', '「鼓動に合わせて編んである。ほどけたら、また持っておいで」', '"It is woven to a beat. When it unravels, bring it back."')


# ---------------------------------------------------------------- 会話
def dialogue():
    write_json(os.path.join(RES, 'data/skyabyss/dialogue/ch1/storyteller_prep.json'), {
        'npc': 'skyabyss:storyteller', 'extends': True,
        'entries': [
            {'node': 'ch1p_gear_weave', 'if': [
                {'type': 'quest', 'quest': 'ch1/prep_gear', 'is': 'active', 'stage': 'weave'},
                {'type': 'item', 'item': 'minecraft:iron_ingot', 'count': 8},
                {'type': 'item', 'item': ITEM_ROCK_SHELL, 'count': 12}]},
            {'node': 'ch1p_blade_report', 'if': {'type': 'quest', 'quest': 'ch1/prep_blade', 'is': 'active', 'stage': 'report'}},
            {'node': 'ch1p_pulse_weave', 'if': [
                {'type': 'quest', 'quest': 'ch1/prep_pulse', 'is': 'active', 'stage': 'weave'},
                {'type': 'item', 'item': ITEM_THREAD, 'count': 1}]},
        ],
        'nodes': {
            'ch1p_gear_weave': {'face': 'smile', 'do': [{'type': 'affinity', 'amount': 2}], 'next': 'menu'},
            'ch1p_blade_report': {'face': 'neutral', 'do': [{'type': 'affinity', 'amount': 2}], 'next': 'menu'},
            'ch1p_pulse_weave': {'face': 'smile', 'do': [{'type': 'take', 'item': ITEM_THREAD, 'count': 1}, {'type': 'affinity', 'amount': 2}], 'next': 'menu'},
        },
    })
    put('dialogue.skyabyss.storyteller.ch1p_gear_weave',
        '岩殻と鉄か。……道の脇に置いてあった荷の手は、覚えている。針の入れ方も。\n旅装は、材料を持って作業台に向かえば、手が思い出す。持ち主のほうは、まだ思い出せないがね。',
        'Shell and iron. ...My hands remember whose pack that was by the road. The way the needle goes in, too.\nTake the materials to a workbench and your hands will recall the rest. The owner, I still cannot recall.')
    put('dialogue.skyabyss.storyteller.ch1p_blade_report',
        '割れ殻の洞を抜けたのかい。……盾は、前から押すものじゃない。背へ回るものだ。\n手ぶらで通れたなら、もう少しは行ける。もう少しだけ、だが。',
        'You went through the Cleft Shell. ...A shield is not pushed from the front. You go round to its back.\nIf you passed with empty hands, you can go a little further. Only a little.')
    put('dialogue.skyabyss.storyteller.ch1p_pulse_weave',
        '殻の護り糸か。……いい糸だ。鼓動に合わせて、ゆるむようにできている。\n編んでおこう。倒れかけた一度だけ、これが肩で受けてくれる。使えば、ほどけて消える。',
        'Shell-ward thread. ...A good one. It is made to slacken in time with a beat.\nI will weave it. Once, when you would fall, it will take the blow on your shoulder. Used, it unravels and is gone.')

    # イサナ (whale_chaser): 乗る前の備えの一言。数字は言わず、言い回しだけ
    write_json(os.path.join(RES, 'data/skyabyss/dialogue/ch1/whale_chaser_prep.json'), {
        'npc': 'skyabyss:whale_chaser', 'extends': True,
        'entries': [
            {'node': 'ch1p_c_all', 'if': [
                {'type': 'prep', 'chapter': 1, 'min': 3},
                {'type': 'not', 'of': {'type': 'flag', 'flag': 'ch1p_all_told'}}]},
            {'node': 'ch1p_c_some', 'if': [
                {'type': 'prep', 'chapter': 1, 'min': 1},
                {'type': 'not', 'of': {'type': 'prep', 'chapter': 1, 'min': 3}},
                {'type': 'day', 'min': 3},
                {'type': 'not', 'of': {'type': 'flag', 'flag': 'ch1p_some_told'}}]},
            {'node': 'ch1p_c_none', 'if': [
                {'type': 'stage', 'max': 0},
                {'type': 'day', 'min': 3},
                {'type': 'not', 'of': {'type': 'prep', 'chapter': 1, 'min': 1}},
                {'type': 'not', 'of': {'type': 'flag', 'flag': 'ch1p_none_told'}}]},
        ],
        'nodes': {
            'ch1p_c_all': {'face': 'smile', 'do': [{'type': 'flag', 'flag': 'ch1p_all_told'}, {'type': 'affinity', 'amount': 1}], 'next': 'c_menu'},
            'ch1p_c_some': {'face': 'neutral', 'do': [{'type': 'flag', 'flag': 'ch1p_some_told'}], 'next': 'c_menu'},
            'ch1p_c_none': {'face': 'grave', 'do': [{'type': 'flag', 'flag': 'ch1p_none_told'}], 'next': 'c_menu'},
        },
    })
    put('dialogue.skyabyss.whale_chaser.ch1p_c_all',
        '荷が重そうだな。……いい重さだ。\n鎖が降りたら、迷うな。迷って落ちた奴は、何人も見た。あんたは、そうならない顔をしている。',
        'Your pack looks heavy. ...A good weight.\nWhen the chain comes down, do not hesitate. I have seen plenty fall by hesitating. You do not have that look.')
    put('dialogue.skyabyss.whale_chaser.ch1p_c_some',
        '支度は半分ってところか。悪くない。\n鯨は、待ってはくれないが、急かしもしない。足りないものが、まだ道の脇にあるはずだ。',
        'Half-ready, by the look. Not bad.\nThe whale will not wait, but it will not hurry you either. What you lack is probably still lying beside the road.')
    put('dialogue.skyabyss.whale_chaser.ch1p_c_none',
        '手ぶらで空を見上げてるのか。……鎖が降りたとき、困らないといいが。\n道の脇の荷車のあたりを、見てきたか。誰かの手が、まだ残っている。',
        'Standing there empty-handed, looking at the sky. ...I hope you will not regret it when the chain comes.\nHave you looked around the cart by the road? Someone\'s hands are still in it.')


# ---------------------------------------------------------------- 伝承
def lore():
    entries = [
        ('road_cairns', 16, '空の下へ向かう石',
         'The Stones Leading Under the Sky',
         '石の積みは、一つとして同じ高さではなかった。ただ、どれも同じほうを指している。道の終わりには、屋根の低い祠があって、中の蝋燭は燃え尽きていた。\n\n七つ目の石の裏に、一行だけ、爪で彫ってあった。「ここまでは、歩いてこられた」\n\nそこから先は、歩かなかったのだろうか。',
         'No two cairns were the same height, yet every one pointed the same way. At the end of the road stood a low-roofed shrine, its candle burnt out.\n\nBehind the seventh stone, one line was scratched with a nail: "Up to here, we could walk."\n\nDid they not walk any farther?'),
        ('song_chamber', 17, '鐘の下の部屋',
         'The Room Beneath the Bell',
         '鐘の足もとの石が、一つだけ、音もなく沈んだ。梯子の下に、小さな部屋があった。壁に、高さの違う五本の柱が並んでいる。いちばん低い柱から、いちばん高い柱へ。\n\n数え歌の「いつつ」は、数のことではなかったのかもしれない。高さの、順番のことだった。',
         'One stone at the foot of the bell sank without a sound. Beneath a ladder lay a small room. Five pillars of differing height stood along the wall, from the lowest to the tallest.\n\nThe "five" in the counting song may not have been a number. It may have been an order of heights.'),
        ('cleft_cave', 18, '割れ殻の洞',
         'The Cleft Shell',
         '洞の奥の壁に、琥珀の目を象った割れ目が、いくつも並んでいた。誰かが、ここで刃を試したのだ。削られた痕は新しく、刃の主は、まだ戻ってきていない。\n\n奥の台座には、割符が置かれていた跡がある。持ち去ったのは、誰だろう。',
         'Along the back wall of the cave ran a line of cracks carved in the shape of amber eyes. Someone had tested a blade here. The scars were fresh, and whoever held that blade had not come back.\n\nOn the plinth, a tally had once sat. Who took it?'),
    ]
    for name, order, tj, te, bj, be in entries:
        write_json(os.path.join(RES, 'data/skyabyss/lore/entry/ch1/%s.json' % name), {
            'chapter': 'titan_whale', 'order': order, 'unlock': [{'type': 'manual'}], 'related': []})
        put('lore.skyabyss.ch1.%s.title' % name, tj, te)
        put('lore.skyabyss.ch1.%s.body' % name, bj, be)


# ---------------------------------------------------------------- 場所の文・覚書
def texts():
    for n, (j, e) in enumerate([
        ('低い石の積み。傍らに、石で作った矢印が空を指している。', 'A low cairn, with an arrow of stones pointing at the sky.'),
        ('積み石が、さっきより少し高い。矢印は、同じほうを向いている。', 'This cairn is a little taller than the last. The arrow points the same way.'),
        ('苔むした積み石。矢印の先は、雲の下だ。', 'A mossy cairn. The arrow ends under the clouds.'),
        ('積み石の根もとに、爪の痕が一本。', 'A single nail scratch at the root of the cairn.'),
        ('石は、空の下へ向かっている。', 'The stones lead under the sky.'),
        ('積み石は、ここだけ崩れかけている。誰かが、何度も触ったように。', 'This cairn alone is on the verge of collapse, as if many hands had touched it.'),
        ('道の終わりの祠。蝋燭は、燃え尽きている。', 'A shrine at the end of the road. The candle has burnt out.'),
    ], 1):
        put('message.skyabyss.ch1.arrive.ch1_road_cairn_%d' % n, j, e)
    put('message.skyabyss.ch1.arrive.ch1_bone_cache', '骨の山。誰かが集めて、運ぼうとしていた。', 'A heap of bones, gathered by something that meant to carry them off.')
    put('message.skyabyss.ch1.cache.found', '骨の山の下から、使える物を拾った。', 'You pick something usable from under the bones.')
    put('message.skyabyss.ch1.arrive.ch1_cart_track', '轍の先に、荷が置いてある。持ち主は、いない。', 'Where the ruts end, a pack has been left. No owner in sight.')
    put('message.skyabyss.ch1.arrive.ch1_cleft_shell_cave', '岩の割れ目から、湿った風が出ている。', 'A damp wind comes out of the crack in the rock.')
    put('message.skyabyss.ch1.arrive.song_chamber', '鐘が、一節ぶん低く鳴った。', 'The bell rang one verse lower.')
    put('message.skyabyss.ch1.stone.chamber', '鐘の足もとの石が、音もなく沈んだ。', 'A stone at the foot of the bell sank without a sound.')
    put('message.skyabyss.ch1.plate.n1', '石が一度、鳴った。', 'The stone rang once.')
    put('message.skyabyss.ch1.plate.n2', '石が二度、鳴った。', 'The stone rang twice.')
    put('message.skyabyss.ch1.plate.n3', '石が三度、鳴った。奥で、鉄が軋んだ。', 'The stone rang three times. Iron groaned in the deep.')
    put('message.skyabyss.ch1.plate.wrong', '石は鳴りかけて、黙った。', 'The stone began to ring, then fell silent.')
    put('message.skyabyss.ch1.plate.done', '石は、もう鳴らない。', 'The stone no longer rings.')
    put('message.skyabyss.ch1.plate.open', '奥の鉄格子が、ゆっくり上がった。', 'The iron grate in the deep slowly rose.')
    put('message.skyabyss.ch1.plate.hint', 'ろうそくの数だけ、石は鳴る。', 'Each stone rings as many times as it has candles.')

    # 素材
    put('item.skyabyss.rock_shell_scrap', '岩殻片', 'Rock-Shell Scrap')
    put('item.skyabyss.rock_shell_scrap.desc', '灰青の殻の欠片。旅装の材料', 'A shard of grey-blue shell. Material for road gear')
    put('item.skyabyss.bone_chip', '骨片', 'Bone Chip')
    put('item.skyabyss.bone_chip.desc', '白い骨の欠片。砥石と固焼きに', 'A chip of pale bone. For whetstones and hardtack')
    put('item.skyabyss.hare_quill', '棘針', 'Hare Quill')
    put('item.skyabyss.hare_quill.desc', '骨の棘の針。旅用の矢の材料', 'A needle of bone spine. Material for travel arrows')
    put('item.skyabyss.rock_shell_thread', '岩殻の護り糸', 'Rock-Shell Ward Thread')
    put('item.skyabyss.rock_shell_thread.desc', '殻の裏の糸。鼓動守 (岩鯨) の材料', 'A thread from behind the shell. Material for the Stone Whale heartbeat ward')

    # 旅の覚書 (一章): 背中の敵の予習の一行ずつ。数字は出さない
    put('wayfarer_notes.ch1.line.1', '骨の番兵の盾は、前からの傷をほとんど通さない。回り込んで背を取る。', 'A bone sentinel\'s shield turns almost every blow from the front. Circle round to its back.')
    put('wayfarer_notes.ch1.line.2', '骨の射手は、撃つ前に弦を引く。引いている間は動かない。柱の陰から出ず、撃ったあとに詰める。', 'A bone archer draws before it shoots, and does not move while drawing. Stay behind a pillar, close in after it fires.')
    put('wayfarer_notes.ch1.line.3', '苔這いは低く、足もとへ寄ってくる。囲まれる前に、背を壁につけない。', 'Moss crawlers stay low and come at your feet. Do not put your back to a wall before you are surrounded.')
    put('wayfarer_notes.ch1.line.4', '予告の線や輪が出たら、跳ぶか、走るか、物の陰に入れば避けられる。立ち止まらない。', 'When a line or ring is marked on the floor, jump, run or take cover. Do not stand still.')
    put('wayfarer_notes.ch1.line.5', '歌石は、積み石の低い順に叩く。洞の鳴り石も、ろうそくの数の少ない順に。', 'Strike the song stones from the lowest cairn up. In the cave, strike the ringing stones from the fewest candles up.')
    put('wayfarer_notes.ch1.line.6', '心臓の前には、身と刃と糧がそろっているか確かめる。足りなくても乗れる。ただ、重くなる。', 'Before the heart, check your body, blade and provisions. You can board without them. It will only be heavier.')


# ---------------------------------------------------------------- 仮の絵 (素材 4 つ)
def png(path, px, w=32, h=32):
    raw = b''
    for y in range(h):
        raw += b'\x00' + b''.join(bytes(px[y][x]) for x in range(w))

    def chunk(tag, data):
        c = struct.pack('>I', len(data)) + tag + data
        return c + struct.pack('>I', zlib.crc32(tag + data) & 0xFFFFFFFF)

    data = b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>IIBBBBB', w, h, 8, 6, 0, 0, 0)) + chunk(b'IDAT', zlib.compress(raw, 9)) + chunk(b'IEND', b'')
    with open(path, 'wb') as f:
        f.write(data)


def blank():
    return [[(0, 0, 0, 0) for _ in range(32)] for _ in range(32)]


def mark(px):
    for y in range(7):
        for x in range(32 - 7 + y, 32):
            px[y][x] = (255, 0, 255, 255)


def shard(px, base, dark, light, eye=None):
    pts = [(8, 20), (13, 8), (22, 6), (26, 15), (23, 25), (14, 27)]
    cx, cy = 17, 17
    for y in range(32):
        for x in range(32):
            d = max(abs(x - cx), abs(y - cy))
            if abs(x - cx) + abs(y - cy) <= 11 and d <= 9:
                px[y][x] = base
                if abs(x - cx) + abs(y - cy) >= 10:
                    px[y][x] = dark
                if x < cx - 2 and y < cy - 2:
                    px[y][x] = light
    if eye:
        for dy in (-1, 0, 1):
            for dx in (-1, 0, 1):
                px[cy + dy][cx + dx] = eye
    return px


def icons():
    out = os.path.join(RES, 'assets/skyabyss/textures/item')
    mdl = os.path.join(RES, 'assets/skyabyss/models/item')
    os.makedirs(out, exist_ok=True)
    os.makedirs(mdl, exist_ok=True)
    # 岩殻片: 灰青の殻 + 琥珀の目
    px = shard(blank(), (92, 104, 118, 255), (58, 66, 78, 255), (132, 146, 160, 255), (216, 150, 52, 255))
    mark(px)
    png(os.path.join(out, 'rock_shell_scrap.png'), px)
    # 骨片: 白い骨の欠片 (斜めの骨。両端に節)
    px = blank()
    for i in range(18):
        x, y = 8 + i, 24 - i
        for dx, dy, col in ((0, 0, (232, 226, 206, 255)), (1, 0, (190, 182, 160, 255)), (0, -1, (244, 240, 224, 255))):
            px[y + dy][x + dx] = col
    for (kx, ky) in ((8, 24), (25, 7)):
        for yy in range(-2, 3):
            for xx in range(-2, 3):
                if abs(xx) + abs(yy) <= 3:
                    px[ky + yy][kx + xx] = (232, 226, 206, 255) if xx + yy < 1 else (190, 182, 160, 255)
    mark(px)
    png(os.path.join(out, 'bone_chip.png'), px)
    # 棘針: 細い骨の針
    px = blank()
    for i in range(20):
        x = 8 + i
        y = 24 - i
        px[y][x] = (222, 212, 186, 255)
        px[y][x + 1] = (176, 166, 140, 255)
        px[y - 1][x] = (240, 234, 214, 255)
    mark(px)
    png(os.path.join(out, 'hare_quill.png'), px)
    # 岩殻の護り糸: 灰青の糸の輪
    px = blank()
    cx, cy = 16, 17
    for y in range(32):
        for x in range(32):
            d = ((x - cx) ** 2 + (y - cy) ** 2) ** 0.5
            if 8 <= d <= 10:
                px[y][x] = (110, 128, 146, 255) if (x + y) % 3 else (70, 84, 100, 255)
            elif 4 <= d <= 5.2 and x > cx:
                px[y][x] = (216, 150, 52, 255)
    mark(px)
    png(os.path.join(out, 'rock_shell_thread.png'), px)
    for name in ('rock_shell_scrap', 'bone_chip', 'hare_quill', 'rock_shell_thread'):
        with open(os.path.join(mdl, name + '.json'), 'w', encoding='utf-8', newline='\n') as f:
            json.dump({'parent': 'minecraft:item/generated', 'textures': {'layer0': 'skyabyss:item/' + name}}, f, separators=(',', ':'))
            f.write('\n')


def main():
    quests()
    dialogue()
    lore()
    texts()
    icons()
    # 文は skyabyss_ch1 の lang に混ぜる (伝承・クエスト・会話の自己テストが読む場所。ほかのキーは触らず、キー順に並べ直す。何度流しても同じ)
    for code, table in (('ja_jp', ja), ('en_us', en)):
        path = os.path.join(RES, 'assets/skyabyss_ch1/lang/%s.json' % code)
        with open(path, encoding='utf-8') as f:
            merged = json.load(f)
        merged.update({k: v for k, v in table.items() if v != ''})
        write_json(path, dict(sorted(merged.items())))
    print('wrote ch1 prep data: %d texts' % len(ja))


if __name__ == '__main__':
    main()
