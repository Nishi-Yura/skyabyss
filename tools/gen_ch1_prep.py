#!/usr/bin/env python3
"""一章の備えの導きと新しい場所 (docs/design/21 の 3.1〜3.2、P6) のデータ・文・仮の絵を書き出す。

  python tools/gen_ch1_prep.py

書くもの:
  (旧い備えの頼み 3 本 ch1/prep_* は、支度の段 ch1/trail_* に吸収した = tools/gen_ch1_trails.py。ここでは書かない)
  data/skyabyss/dialogue/ch1/storyteller_prep.json               語り部の一言 1 つ (荷車の職人を指す。頼みの会話は職人 = tools/gen_artisan_dialogue.py)
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

# 依頼主は旅の職人 (P4。会話は tools/gen_artisan_dialogue.py が dialogue/artisan.json・dialogue/ch1/artisan.json に書く)
GIVER = 'skyabyss:artisan'
IRON_COUNT = 7
ITEM_LEATHER = 'minecraft:leather'
ITEM_BONE_CHIP = 'skyabyss:bone_chip'
ITEM_ROCK_SHELL = 'skyabyss:rock_shell_scrap'
ITEM_THREAD = 'skyabyss:rock_shell_thread'

ja = {}
en = {}

# 職人 (P4) に移して、使わなくなった文
DROPPED = ['dialogue.skyabyss.storyteller.ch1p_gear_weave', 'dialogue.skyabyss.storyteller.ch1p_blade_report', 'dialogue.skyabyss.storyteller.ch1p_pulse_weave']


def put(key, j, e):
    ja[key] = j
    en[key] = e


def write_json(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w', encoding='utf-8', newline='\n') as f:
        json.dump(data, f, ensure_ascii=False, indent=2)
        f.write('\n')


# ---------------------------------------------------------------- 会話
def dialogue():
    # 語り部: 職人の頼みの入口は職人へ移した (tools/gen_artisan_dialogue.py)。ここは「荷車の傍らの道に職人がいる」の一言だけ
    write_json(os.path.join(RES, 'data/skyabyss/dialogue/ch1/storyteller_prep.json'), {
        'npc': 'skyabyss:storyteller', 'extends': True,
        'entries': [
            {'node': 'ch1p_artisan_pointer', 'if': [
                {'type': 'quest', 'quest': 'ch1/trail_blade', 'is': 'active'},
                {'type': 'not', 'of': {'type': 'flag', 'flag': 'ch1p_pointer_told'}}]},
        ],
        'nodes': {
            'ch1p_artisan_pointer': {'face': 'smile', 'do': [{'type': 'flag', 'flag': 'ch1p_pointer_told'}], 'next': 'menu'},
        },
    })
    put('dialogue.skyabyss.storyteller.ch1p_artisan_pointer',
        '道の脇の荷車のそばで、荷を広げている人を見たかい。旅の職人だよ。……あの手つきは、あたしも覚えている。針の入れ方も。\n折れた剣の話なら、あの人に聞くといい。刃を研ぐのも、旅装を編むのも。持ち主のほうは、まだ思い出せないけどね。',
        'Have you seen the one with a pack spread out by the cart track? A traveling artisan. ...My hands remember whose pack that was. The way the needle goes in, too.\nAbout the broken sword, ask them. Honing a blade, stitching road gear, too. The owner, I still cannot recall.')

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
        '支度は半分ってところか。悪くない。\n焦るな。鯨は、まだ遠い。足りないものが、まだ道の脇にあるはずだ。',
        'Half-ready, then. Not bad.\nNo rush. The whale is still far off. What you lack should still be by the road.')
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
    put('message.skyabyss.ch1.arrive.ch1_cart_track', '轍の先に、荷が広げてある。', 'Where the ruts end, a pack has been spread out.')
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
    put('wayfarer_notes.ch1.line.3', '苔這いは低く、足もとへ寄ってくる。壁際に追いつめられないよう、広い所で戦う。', 'Moss crawlers stay low and come at your feet. Do not put your back to a wall before you are surrounded.')
    put('wayfarer_notes.ch1.line.4', '予告の線や輪が出たら、跳ぶか、走るか、物の陰に入れば避けられる。立ち止まらない。', 'When a line or ring is marked on the floor, jump, run or take cover. Do not stand still.')
    put('wayfarer_notes.ch1.line.5', '歌石は、積み石の低い順に叩く。洞の鳴り石も、ろうそくの数の少ない順に。', 'Strike the song stones from the lowest cairn up. In the cave, strike the ringing stones from the fewest candles up.')
    put('wayfarer_notes.ch1.line.6', '背の奥へ進む前に、身と刃と糧がそろっているか確かめる。足りなくても進める。ただ、重くなる。', 'Before going deep into the back, check your body, blade and provisions. You can go on without them. It will only be heavier.')
    put('wayfarer_notes.ch1.line.7', '岩殻片と骨片は、旅装にも、岩の砥石にも、道の固焼きにもなる。この覚書を開くと、作り方が作業台に出る。', 'Shell scraps and bone chips make road gear, rock whetstones and road hardtack. Open these notes and the recipes appear at the workbench.')


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
        for k in DROPPED:
            merged.pop(k, None)
        # 支度の段に吸収した備えの頼みの文
        for k in [k for k in merged if k.startswith('quest.skyabyss.ch1.prep_')]:
            merged.pop(k)
        write_json(path, dict(sorted(merged.items())))
    print('wrote ch1 prep data: %d texts' % len(ja))


if __name__ == '__main__':
    main()
