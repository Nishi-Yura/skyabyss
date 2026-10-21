#!/usr/bin/env python3
"""一章の支度の段 (docs/design/34 の 4.2・4.2.1・5 章、C1) のデータと文を書き出す。

  python tools/gen_ch1_trails.py

書くもの:
  data/skyabyss/quest/ch1/trail_iron|trail_blade|trail_shield.json   支度の段 3 本 (メインの段。順不同。07 の 6.8)
  data/skyabyss/dialogue/ch1/storyteller_trail.json                  語り部: 足音の晩の盾の話 (trail_shield の最初の段)・支度のあとの一言
  data/skyabyss/dialogue/ch1/whale_chaser_trail.json                 イサナ: 頁を見たあと・鉄を着たあとの一言
  data/skyabyss/lore/entry/ch1/trail_iron|trail_blade|trail_shield|cold_door.json   前の私の頁 3 枚と冷えの戸の碑文
  assets/skyabyss_ch1/lang/{ja_jp,en_us}.json   文 (既存のキーに混ぜる。キー順)
旧い備えの頼み (ch1/prep_gear・prep_blade・prep_pulse) はこの 3 本に吸収した。古いファイルが残っていれば消す。
イサナの「鯨の話」の一言 (鉄を着ていた旅人) は tools/gen_npc_content.py、職人の会話は tools/gen_artisan_dialogue.py、骨の坑の部品は tools/gen_ch1_sites.py。

語りの決まり (34 の 2・7、21 の 0.2): 前の私の正体は言わない (「誰かの跡」。字に見覚えがある、くらいまで)。目標はバニラの品と行為、理由は物語。
数字は頁の中の「鉄を二十四」だけ (前の私の几帳面さ)。心臓・深淵・巨神の眠りの理由は書かない。静かなホラーは、暮らしの小さなずれで。
"""
import json
import os

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, 'src', 'main', 'resources')

ISANA = 'skyabyss:whale_chaser'
ARTISAN = 'skyabyss:artisan'
STORYTELLER = 'skyabyss:storyteller'
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


def flag(name, npc=None):
    c = {'type': 'flag', 'flag': name}
    if npc:
        c['npc'] = npc
    return c


def not_(c):
    return {'type': 'not', 'of': c}


def quest(q, is_='active', stage=None):
    c = {'type': 'quest', 'quest': q, 'is': is_}
    if stage:
        c['stage'] = stage
    return c


# 三本とも、一章 (段階 0) の人が、イサナの鯨の話 (c_whale1 で印 c_whale_heard) を聞いたら出る。話を聞かずに岩鯨を見た人にも出る
START = {
    'type': 'all',
    'of': [
        {'type': 'stage', 'max': 0},
        {'type': 'any', 'of': [flag('c_whale_heard', ISANA), {'type': 'lore', 'entry': 'chapter1/stone_whale'}]},
    ],
}
IRON_ARMOR = ['minecraft:iron_helmet', 'minecraft:iron_chestplate', 'minecraft:iron_leggings', 'minecraft:iron_boots']


# ---------------------------------------------------------------- クエスト
def quests():
    q = os.path.join(RES, 'data/skyabyss/quest/ch1')
    for old in ('prep_gear', 'prep_blade', 'prep_pulse'):
        p = os.path.join(q, old + '.json')
        if os.path.exists(p):
            os.remove(p)
    write_json(os.path.join(q, 'trail_iron.json'), {
        'category': 'main', 'order': 11, 'giver': ISANA, 'chapter': 'titan_whale', 'start_when': START,
        'stages': [
            # 骨の坑の奥の荷で頁を見つける (BonePit.openPack が伝承 ch1/trail_iron を記す)
            {'id': 'pit', 'xp': 30, 'objectives': [{'id': 'page', 'type': 'lore', 'targets': ['ch1/trail_iron']}]},
            # 鉄の防具 4 部位を着る (着ている種類の数。帯は「鉄の防具を作って着よう（2/4）」)
            {'id': 'armor', 'objectives': [{'id': 'wear', 'type': 'wear', 'targets': IRON_ARMOR}]},
        ],
        'reward': {'xp': 70, 'items': [], 'affinity': {ISANA: 3}},
    })
    write_json(os.path.join(q, 'trail_blade.json'), {
        'category': 'main', 'order': 12, 'giver': ARTISAN, 'chapter': 'titan_whale', 'start_when': START,
        'stages': [
            # 割れ殻の洞を抜けると台座に折れた剣 (Ch1Trails が伝承 ch1/trail_blade を記す。先に抜けた人もさかのぼって済む)
            {'id': 'cave', 'xp': 30, 'objectives': [{'id': 'sword', 'type': 'lore', 'targets': ['ch1/trail_blade']}]},
            # 職人に話す (作り方を教わる = 結果 unlock_recipes)
            {'id': 'show', 'objectives': [{'id': 'talk', 'type': 'talk', 'target': ARTISAN, 'topic': 'ch1t_blade_show'}]},
            # 鉄の剣を持ち、岩の砥石で研ぐ (どちらが先でもよい)
            {'id': 'forge', 'mode': 'all', 'objectives': [
                {'id': 'sword', 'type': 'collect', 'target': 'minecraft:iron_sword', 'count': 1, 'party': False},
                {'id': 'whet', 'type': 'signal', 'target': 'ch1_whetstone_used', 'party': False}]},
        ],
        'reward': {'xp': 70, 'items': [], 'affinity': {ARTISAN: 3}},
    })
    write_json(os.path.join(q, 'trail_shield.json'), {
        'category': 'main', 'order': 13, 'giver': STORYTELLER, 'chapter': 'titan_whale', 'start_when': START,
        'stages': [
            # 語り部の足音の晩の話 (会話の結果で伝承 ch1/trail_shield)
            {'id': 'tale', 'objectives': [{'id': 'talk', 'type': 'talk', 'target': STORYTELLER, 'topic': 'ch1t_shield_tale'}]},
            # 盾と道の固焼き 8 (固焼きの作り方は旅の覚書。鼓動守は任意 = 職人に護り糸を編んでもらう)
            {'id': 'pack', 'mode': 'all', 'objectives': [
                {'id': 'shield', 'type': 'collect', 'target': 'minecraft:shield', 'count': 1, 'party': False},
                {'id': 'hardtack', 'type': 'collect', 'target': 'skyabyss:road_hardtack', 'count': 8, 'party': False}]},
        ],
        'reward': {'xp': 50, 'items': [], 'affinity': {STORYTELLER: 3}},
    })

    # ---- 文
    b = 'quest.skyabyss.ch1.trail_iron.'
    put(b + 'title', '鉄の跡', 'The Iron Trail')
    put(b + 'summary', '前にここへ来た旅人の道を辿り、骨の坑へ。鉄を着る。', 'Follow the road of the traveller who came before, down into the Bone Pit. Wear iron.')
    put(b + 'pit.journal',
        'イサナが言っていた。「鎖は素手では上れない。前にここへ来た旅人は、鉄を着ていた」\n鯨道の石標を辿った先に、骨の坑という古い坑道があるらしい。その旅人の道は、そこへ続いている。',
        'Isana said: "You cannot climb the chain bare-handed. The traveller who came here before wore iron."\nThe Whale Road cairns lead to an old mine called the Bone Pit. That traveller\'s road went there.')
    put(b + 'pit.page', '骨の坑の奥で、前の旅人の荷を探す', 'Find the old pack in the Bone Pit')
    put(b + 'armor.journal',
        '荷の頁には、こうあった。「鉄を二十四。胸当てと脚当てを先に。背の風は骨まで冷える」\n鉄の鉱石は、かまどで溶かせば鉄になる。坑の壁にも、筋が走っていた。兜と胸当てと脚当てと靴を作って、身につけよう。',
        'The page in the pack read: "Twenty-four iron. Breastplate and leggings first. The wind on the back cuts to the bone."\nIron ore smelts into iron in a furnace; the pit walls were streaked with it. Make a helmet, chestplate, leggings and boots, and wear them.')
    put(b + 'armor.wear', '鉄の防具を作って着よう', 'Make and wear iron armor')
    put(b + 'done', '「鉄の匂いがするな。……あの旅人も、そうやって鎖の下に立っていたんだろう」', '"You smell of iron. ...That traveller must have stood under the chain like that."')

    b = 'quest.skyabyss.ch1.trail_blade.'
    put(b + 'title', '折れた剣', 'The Broken Sword')
    put(b + 'summary', '割れ殻の洞の奥の折れた剣を見て、鉄の剣を作り、砥石で研ぐ。', 'See the broken sword deep in the Cleft Shell, then make an iron sword and hone it.')
    put(b + 'cave.journal',
        '旅の職人が言っていた。「折れた剣を見た。割れ殻の洞の奥だ。……いつ見たのかは、覚えていない」\n丘の斜面の岩の割れ目から、湿った風が出ている。奥の主は、盾の前を嫌う相手ではない。背を取れ。',
        'The traveling artisan said: "I saw a broken sword. Deep in the Cleft Shell. ...When, I do not remember."\nA damp wind comes from a crack in the hillside. Whatever waits at the bottom does not mind a shield in its face. Get behind it.')
    put(b + 'cave.sword', '割れ殻の洞の奥で、折れた剣を探す', 'Find the broken sword in the Cleft Shell')
    put(b + 'show.journal', '台座の剣は、持ち上げると崩れた。柄の布の巻き方だけが、目に残っている。旅の職人に話そう。',
        'The sword on the plinth crumbled when lifted. Only the way its grip was wrapped stays in your eye. Tell the traveling artisan.')
    put(b + 'show.talk', '折れた剣のことを、旅の職人に話す', 'Tell the artisan about the broken sword')
    put(b + 'forge.journal', '「研がずに振った。だから折れた」と台座にあった。鉄の剣を作業台で作り、岩の砥石で研いでから振ろう。砥石の作り方は旅の覚書に。',
        'The plinth said: "Swung it unhoned. So it broke." Make an iron sword at a workbench and hone it on a rock whetstone before you swing it. The whetstone is in your wayfarer\'s notes.')
    put(b + 'forge.sword', '鉄の剣を作る', 'Make an iron sword')
    put(b + 'forge.whet', '岩の砥石で刃を研ぐ', 'Hone your blade on a rock whetstone')
    put(b + 'done', '「刃は、研いだぶんだけ軽くなる。……今度は、折るなよ」', '"A blade lightens as you hone it. ...Do not break this one."')

    b = 'quest.skyabyss.ch1.trail_shield.'
    put(b + 'title', '盾と糧', 'Shield and Provisions')
    put(b + 'summary', '足音の晩の話を聞き、盾と道の固焼きを用意する。', 'Hear the tale of the night of footsteps, then ready a shield and road hardtack.')
    put(b + 'tale.journal', '語り部が、昔の旅人の話をしたがっている。「足音の晩のことだよ。聞いておいき」',
        'The storyteller wants to tell you about a traveller long ago. "It is about the night of footsteps. Listen."')
    put(b + 'tale.talk', '語り部に、足音の晩の話を聞く', 'Hear the tale of the night of footsteps')
    put(b + 'pack.journal',
        '足音の晩、前の旅人は盾を抱え、固焼きの包みを背負って戻ってきたという。盾の表には、大きな爪の痕が三本。\n盾は作業台で作れる。道の固焼きは旅の覚書に作り方がある。鼓動守は、旅の職人に護り糸を持っていけば編んでくれる (なくてもいい)。',
        'On the night of footsteps, the earlier traveller came back clutching a shield, a bundle of hardtack on their back. Three great claw marks crossed the shield.\nA shield can be made at a workbench; road hardtack is in your wayfarer\'s notes. If you bring ward thread to the artisan, they will weave a heartbeat ward too (not required).')
    put(b + 'pack.shield', '盾を作る', 'Make a shield')
    put(b + 'pack.hardtack', '道の固焼きを持つ', 'Carry road hardtack')
    put(b + 'done', '「盾は、前を守るためだけのものじゃないよ。……帰り道のためにも、持っておいき」', '"A shield is not only for what is in front of you. ...Carry it for the way home, too."')


# ---------------------------------------------------------------- 会話
def dialogue():
    write_json(os.path.join(RES, 'data/skyabyss/dialogue/ch1/storyteller_trail.json'), {
        'npc': STORYTELLER, 'extends': True,
        'entries': [
            {'node': 'ch1t_shield_tale', 'if': quest('ch1/trail_shield', 'active', 'tale')},
            {'node': 'ch1t_s_ready', 'if': [{'type': 'prep', 'chapter': 1, 'min': 3}, not_(flag('ch1t_s_ready_told'))]},
        ],
        'nodes': {
            'ch1t_shield_tale': {'face': 'grave', 'do': [{'type': 'lore', 'entry': 'ch1/trail_shield'}], 'next': 'ch1t_shield_tale2'},
            'ch1t_shield_tale2': {'face': 'neutral', 'next': 'menu'},
            'ch1t_s_ready': {'face': 'smile', 'do': [flag('ch1t_s_ready_told'), {'type': 'affinity', 'amount': 1}], 'next': 'menu'},
        },
    })
    d = 'dialogue.skyabyss.storyteller.'
    put(d + 'ch1t_shield_tale',
        '足音の晩の話をしようか。……あたしが、まだ小さかったころだよ。\n夜じゅう、丘の向こうで地面が鳴っていた。朝になって、ひとりの旅人が丘から戻ってきた。盾を胸に抱えて、固焼きの包みを背負ってね。盾の表には、大きな爪の痕が三本あった。',
        'Let me tell you about the night of footsteps. ...I was still small then.\nAll night the ground boomed beyond the hill. In the morning a traveller came back over it, a shield clutched to their chest, a bundle of hardtack on their back. Three great claw marks crossed the shield.')
    put(d + 'ch1t_shield_tale2',
        'その人は、少し休むと、また空を見に行った。それきりさ。\n……あんたの歩き方を見ていると、あの朝を思い出すよ。盾を持っておいき。糧もね。帰ってくるつもりなら。',
        'They rested a little, then went to look at the sky again. That was the last of them.\n...Watching the way you walk reminds me of that morning. Take a shield. Provisions too. If you mean to come back.')
    put(d + 'ch1t_s_ready',
        '身なりが変わったね。鉄と、刃と、盾と。……ほんとうに、あの朝の人みたいだ。\nこの字、どこかで見た気がするよ。あんたが拾ってきた頁の字さ。……さて、どこだったかね。',
        'You look different. Iron, a blade, a shield. ...Truly like the one from that morning.\nI feel I have seen this hand before. The writing on the pages you brought back. ...Now where was it.')

    write_json(os.path.join(RES, 'data/skyabyss/dialogue/ch1/whale_chaser_trail.json'), {
        'npc': ISANA, 'extends': True,
        'entries': [
            {'node': 'ch1t_c_iron', 'if': [quest('ch1/trail_iron', 'done'), not_(flag('ch1t_c_iron_told'))]},
            {'node': 'ch1t_c_page', 'if': [quest('ch1/trail_iron', 'active', 'armor'), not_(flag('ch1t_c_page_told'))]},
        ],
        'nodes': {
            'ch1t_c_page': {'face': 'neutral', 'do': [flag('ch1t_c_page_told')], 'next': 'c_menu'},
            'ch1t_c_iron': {'face': 'smile', 'do': [flag('ch1t_c_iron_told'), {'type': 'affinity', 'amount': 1}], 'next': 'c_menu'},
        },
    })
    d = 'dialogue.skyabyss.whale_chaser.'
    put(d + 'ch1t_c_page',
        '坑の奥で頁を見つけたのか。……鉄を二十四、か。几帳面な奴だ。\n鉱石は、かまどで溶かせば鉄になる。燃やすものは、木炭でも石炭でもいい。……鎖は待ってはくれないが、急かしもしない。',
        'You found a page down the pit? ...Twenty-four iron. A tidy sort.\nOre smelts into iron in a furnace. Charcoal or coal, either burns. ...The chain will not wait, but it will not hurry you either.')
    put(d + 'ch1t_c_iron',
        '鉄の匂いがする。……似合ってるよ。\nあの旅人も、そうやって鎖の下に立っていた気がする。……いや、話に聞いただけだ。見たはずがない。',
        'You smell of iron. ...It suits you.\nI feel that traveller stood under the chain just like that. ...No. I only heard the story. I cannot have seen it.')


# ---------------------------------------------------------------- 伝承 (前の私の頁 3 枚・冷えの戸の碑文)
def lore():
    entries = [
        ('trail_iron', 19, None, '坑の頁', 'The Page in the Pit',
         '坑の奥の荷の底に、油紙で包んだ頁が一枚あった。字は急いでいて、ところどころ滲んでいる。\n\n「鉄を二十四。胸当てと脚当てを先に。背の風は骨まで冷える。鎖は、素手では上れない」\n\n余白に、もう一行。「ここまでは、前と同じ。次は、迷わないこと」\n\n誰の字だろう。見覚えがある気がするのは、きっと気のせいだ。',
         'At the bottom of a pack deep in the pit lay a page wrapped in oilcloth. The hand was hurried and ran in places.\n\n"Twenty-four iron. Breastplate and leggings first. The wind on the back cuts to the bone. You cannot climb the chain bare-handed."\n\nIn the margin, one more line: "Same as before, up to here. Next time, do not hesitate."\n\nWhose hand is this? That it looks familiar must be nothing.'),
        ('trail_blade', 20, None, '折れた剣', 'The Broken Sword',
         '割れ殻の洞の奥の台座に、鉄の剣が一本、横たえてあった。刃は根もとから折れ、柄には布が固く巻いてある。巻き方の癖が、自分の手の癖に似ていた。\n\n台座の縁に、爪で一行。「研がずに振った。だから折れた」\n\n持ち上げると、剣は砂のように崩れた。何年、ここで待っていたのだろう。',
         'On the plinth deep in the Cleft Shell lay an iron sword, snapped at the root, its grip bound tight with cloth. The way it was wound was like the way your own hands wind things.\n\nOn the plinth\'s edge, one line scratched with a nail: "Swung it unhoned. So it broke."\n\nLifted, the sword fell apart like sand. How many years had it waited there?'),
        ('trail_shield', 21, None, '足音の晩の盾', 'The Shield on the Night of Footsteps',
         '語り部が小さかったころ、夜じゅう丘の向こうで地面が鳴った晩があった。朝、ひとりの旅人が丘から戻ってきた。盾を胸に抱え、固焼きの包みを背負って。盾の表には、大きな爪の痕が三本。\n\n旅人は少し休むと、また空を見に行き、それきりだった。\n\n語り部は言った。「あんたの歩き方を見ていると、あの朝を思い出すよ」',
         'When the storyteller was small, there was a night when the ground boomed beyond the hill until dawn. In the morning a traveller came back over it, a shield clutched to their chest, hardtack on their back. Three great claw marks crossed the shield.\n\nThey rested a little, went to look at the sky again, and were never seen after.\n\nThe storyteller said: "Watching the way you walk reminds me of that morning."'),
        ('cold_door', 22, 'sign.skyabyss.ch1_cold_door', '冷えの戸の碑文', 'The Inscription Behind the Cold Door',
         '骨の坑の奥、鉄を着た者にだけ開く石の戸の向こうに、氷の小部屋があった。碑には、古い字でこう刻まれていた。\n\n「背の風は冷たい。骨まで届く。鉄を着て上れ。鉄は冷えを覚える」\n\n碑の足もとの霜に、新しい足跡が一つだけあった。こちらへ来る向きに。',
         'Deep in the Bone Pit, past a stone door that opens only to one who wears iron, lay a small room of ice. On its tablet, in an old hand:\n\n"The wind on the back is cold. It reaches the bone. Climb wearing iron. Iron remembers the cold."\n\nIn the frost at its foot was a single fresh footprint, facing toward the door.'),
    ]
    for name, order, sign, tj, te, bj, be in entries:
        data = {'chapter': 'titan_whale', 'order': order, 'unlock': [{'type': 'manual'}], 'related': []}
        if sign:
            data['inscription_sign'] = sign
            data['unlock'] = [{'type': 'sign', 'target': sign}]
        write_json(os.path.join(RES, 'data/skyabyss/lore/entry/ch1/%s.json' % name), data)
        put('lore.skyabyss.ch1.%s.title' % name, tj, te)
        put('lore.skyabyss.ch1.%s.body' % name, bj, be)


# ---------------------------------------------------------------- 場所の文・碑・品
def texts():
    put('message.skyabyss.ch1.arrive.ch1_bone_pit', '地面から、巨きな肋骨が二本、門のように突き出している。下から、冷たい風。',
        'Two enormous ribs jut from the ground like a gate. A cold wind rises from below.')
    put('message.skyabyss.ch1.pit.trail', '石標の矢印は、ここで途切れていた。', 'The cairns\' arrows end here.')
    put('message.skyabyss.ch1.pit.pack', '荷の底に、油紙で包んだ頁が一枚。鉄と松明も、きちんと揃えてある。',
        'At the bottom of the pack, a page wrapped in oilcloth. Iron and torches, neatly set out.')
    put('message.skyabyss.ch1.pit.pack_hint', '古い荷がある。触れてみよう', 'An old pack lies here. Try touching it')
    put('message.skyabyss.ch1.pit.pack_empty', '荷は、もう空だ。頁の写しだけが、まだ挟まっている。', 'The pack is empty now. Only a copy of the page is still tucked inside.')
    put('message.skyabyss.ch1.pit.cold_shut', '石の戸は、氷のように冷たい。触れた指が、張りついて離れない。',
        'The stone door is cold as ice. Your fingers stick to it.')
    put('message.skyabyss.ch1.pit.cold_open', '鉄が冷えを受け止めた。石の戸が、軋みながら退いていく。',
        'The iron takes the cold. The stone door grinds back.')
    put('message.skyabyss.ch1.cave.sword', '奥の台座に、折れた鉄の剣が横たえてある。', 'On the plinth at the back lies a broken iron sword.')
    put('message.skyabyss.ch1.trail.mark', '鯨追いの印を手に入れた', 'You received the Whale Chaser\'s Mark')
    # 碑 (4 行。空の行は飛ばす)
    for key, lines_ja, lines_en in (
            ('sign.skyabyss.ch1_bone_pit', ['骨の坑', '鉄は ここに眠る', '下る者は 灯りを', '戻らぬ者は 数えない'],
             ['The Bone Pit', 'Iron sleeps here', 'Who goes down, bring light', 'Who stays down, we do not count']),
            ('sign.skyabyss.ch1_cold_door', ['背の風は 冷たい', '骨まで 届く', '鉄を着て 上れ', '鉄は 冷えを覚える'],
             ['The wind on the back is cold', 'It reaches the bone', 'Climb wearing iron', 'Iron remembers the cold'])):
        for i in range(4):
            put('%s.%d' % (key, i + 1), lines_ja[i], lines_en[i])
    put('item.skyabyss.whale_chaser_mark', '鯨追いの印', 'Whale Chaser\'s Mark')
    put('item.skyabyss.whale_chaser_mark.lore', '鉄と刃と盾をそろえた旅人の印。骨の欠片に、イサナの結び方で紐が通してある。',
        'The mark of a traveller with iron, blade and shield. A bone chip on a cord, tied the way Isana ties things.')


def main():
    quests()
    dialogue()
    lore()
    texts()
    for code, table in (('ja_jp', ja), ('en_us', en)):
        path = os.path.join(RES, 'assets/skyabyss_ch1/lang/%s.json' % code)
        with open(path, encoding='utf-8') as f:
            merged = json.load(f)
        merged.update(table)
        write_json(path, dict(sorted(merged.items())))
    assert set(ja) == set(en)
    print('wrote ch1 trails: %d texts' % len(ja))


if __name__ == '__main__':
    main()
