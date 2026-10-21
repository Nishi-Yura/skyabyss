#!/usr/bin/env python3
"""旅の職人 (docs/design/21 の 1.4、P4) の会話・名前・好感度の小さな話を書き出す。

  python tools/gen_artisan_dialogue.py

書くもの:
  data/skyabyss/dialogue/artisan.json         全章の共通 (入口は a_menu だけ。好感度で開く「手の話」3 つ)。章の入口は章ごとの足し書きが前に足す
  data/skyabyss/dialogue/ch1/artisan.json     一章の足し書き ("extends": true。初対面・支度の段「折れた剣」の会話・護り糸から鼓動守を編む・乗る前の一言・覚書の渡し直し・作り方・鼓動守の編み直し)
  assets/skyabyss_npc/lang/{ja_jp,en_us}.json     名前・殴られたときの一言・共通の会話 (既存のキーの後ろに足す)
  assets/skyabyss_ch1/lang/{ja_jp,en_us}.json     一章の会話 (キー順に混ぜる)
二〜四章 (P11・P15・P19) は dialogue/ch<n>/artisan.json を同じ形で足す ("extends": true、入口に初対面・頼みの会話、add_options に覚書と作り方)。

職人の決まり (21 の 1.4): 役名だけで固有の名前はない。同じ顔・同じ口調で、前のことを覚えていない (イサナの「どこかで会ったか？」と同じ方向)。
道具を直し、旅装を編む。語るのは巨神の話ではなく「手の話」(道具の来歴)。数字は言わない。心臓・深淵・巨神の眠りの理由は書かない。
"""
import json
import os

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, 'src', 'main', 'resources')
NL = chr(92) + 'n'  # 文字列リテラルでなく、データの中の改行 (JSON が \n に直す) は下の t() で本物の改行にする

NPC = {'ja': {}, 'en': {}}
CH1 = {'ja': {}, 'en': {}}
DROP = []


def t(text):
    return text.replace('@@', '\n')


def put(table, key, j, e):
    table['ja'][key] = t(j)
    table['en'][key] = t(e)


def write_json(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w', encoding='utf-8', newline='\n') as f:
        json.dump(data, f, ensure_ascii=False, indent=2)
        f.write('\n')


def flag(name):
    return {'type': 'flag', 'flag': name}


def not_(c):
    return {'type': 'not', 'of': c}


def quest(q, is_='active', stage=None):
    c = {'type': 'quest', 'quest': q, 'is': is_}
    if stage:
        c['stage'] = stage
    return c


B = 'dialogue.skyabyss.artisan.'


# ---------------------------------------------------------------- 全章の共通
def base():
    write_json(os.path.join(RES, 'data/skyabyss/dialogue/artisan.json'), {
        'npc': 'skyabyss:artisan',
        'entries': [{'node': 'a_menu'}],
        'nodes': {
            'a_menu': {'face': 'neutral', 'options': [
                {'id': 'hammer', 'if': [{'type': 'affinity', 'min': 10}, not_(flag('a_hammer_told'))], 'goto': 'a_hammer'},
                {'id': 'whet', 'if': [{'type': 'affinity', 'min': 30}, not_(flag('a_whet_told'))], 'goto': 'a_whet'},
                {'id': 'hands', 'if': [{'type': 'affinity', 'min': 60}, not_(flag('a_hands_told'))], 'goto': 'a_hands'},
                {'id': 'bye', 'kind': 'leave', 'goto': 'end'},
            ]},
            'a_hammer': {'face': 'neutral', 'do': [flag('a_hammer_told'), {'type': 'affinity', 'amount': 1}], 'next': 'a_menu'},
            'a_whet': {'face': 'smile', 'do': [flag('a_whet_told'), {'type': 'affinity', 'amount': 1}], 'next': 'a_menu'},
            'a_hands': {'face': 'grave', 'do': [flag('a_hands_told'), {'type': 'affinity', 'amount': 1}], 'next': 'a_menu'},
        },
    })
    put(NPC, 'entity.skyabyss.artisan', '旅の職人', 'Traveling Artisan')
    put(NPC, 'message.skyabyss.artisan.struck', '「……手を止めさせるな。針が滑る」', '"...Do not make me stop. The needle slips."')
    put(NPC, 'message.skyabyss.heartbeat_ward.remade', '鼓動守を編み直してもらった。', 'You had your heartbeat ward woven again.')
    put(NPC, B + 'a_menu', '用か。手は止めないがね。', 'Something you need? My hands will keep moving.')
    put(NPC, B + 'a_menu.hammer', 'その槌のこと、聞いてもいい？', 'May I ask about that hammer?')
    put(NPC, B + 'a_menu.whet', '腰の砥石のことは？', 'What about the whetstone at your hip?')
    put(NPC, B + 'a_menu.hands', '手は、何を覚えている？', 'What do your hands remember?')
    put(NPC, B + 'a_menu.bye', 'また来る。', 'I will be back.')
    put(NPC, B + 'a_hammer',
        'この槌か。柄は三度替えた。頭は、一度も。……いや、一度だったか。どうも、そこだけ覚えがない。@@槌は、振った数を覚えていない。覚えているのは、手のほうだ。',
        'This hammer? The haft has been changed three times. The head, never. ...Or once? I have no memory of that part.@@A hammer does not remember how often it fell. The hand does.')
    put(NPC, B + 'a_whet',
        '砥石は、削られるほうが先に減る。刃を研ぐたびに、石は丸くなる。それでいい。石は、そのためにある。@@……減っていくものに、名前を付ける奴がいる。俺は付けない。付けると、手が惜しむ。',
        'A whetstone wears down before the blade does. Every time you hone, it rounds off. That is fine. That is what it is for.@@...Some people name the things that wear away. I do not. Name it, and the hand starts to spare it.')
    put(NPC, B + 'a_hands',
        '手は、覚えている。針の入れ方、槌の落とし方、結び目の向き。……誰に教わったのかは、手に聞いても答えない。@@それでも、道具を持つと、持ち主の側へ立っている気がする。変な話だ。聞き流してくれ。',
        'The hands remember. How the needle goes in, how the hammer falls, which way a knot lies. ...Who taught me, the hands will not say.@@Still, when I hold a tool, I feel I am standing on the owner\'s side. Odd. Forget I said it.')


# ---------------------------------------------------------------- 一章
def ch1():
    ROD = 'minecraft:iron_ingot'
    SCRAP = 'skyabyss:rock_shell_scrap'
    THREAD = 'skyabyss:rock_shell_thread'
    write_json(os.path.join(RES, 'data/skyabyss/dialogue/ch1/artisan.json'), {
        'npc': 'skyabyss:artisan', 'extends': True,
        'entries': [
            {'node': 'ch1t_blade_show', 'if': quest('ch1/trail_blade', 'active', 'show')},
            {'node': 'ch1t_blade_tell', 'if': [quest('ch1/trail_blade', 'active', 'cave'), not_(flag('ch1t_blade_told')), flag('met')]},
            {'node': 'ch1t_ward_weave', 'if': [{'type': 'item', 'item': THREAD, 'count': 1}, not_(flag('ch1t_woven')),
                                               {'type': 'ward_ok', 'titan': 'stone_whale'}]},
            {'node': 'ch1a_ready', 'if': [{'type': 'prep', 'chapter': 1, 'min': 3}, not_(flag('ch1a_ready_told'))]},
            {'node': 'ch1a_some', 'if': [{'type': 'prep', 'chapter': 1, 'min': 1}, not_({'type': 'prep', 'chapter': 1, 'min': 3}),
                                         {'type': 'day', 'min': 3}, not_(flag('ch1a_some_told'))]},
            {'node': 'ch1a_deja', 'if': [{'type': 'loop', 'min': 1}, {'type': 'remembers', 'flag': 'met'}, not_(flag('met'))]},
            {'node': 'ch1a_first', 'if': not_(flag('met'))},
        ],
        'nodes': {
            'ch1a_first': {'face': 'neutral', 'do': [{'type': 'give_notes', 'chapter': 1}, {'type': 'affinity', 'amount': 1}], 'next': 'a_menu'},
            'ch1a_deja': {'face': 'grave', 'do': [{'type': 'give_notes', 'chapter': 1}], 'next': 'a_menu'},
            'ch1t_blade_show': {'face': 'grave', 'do': [{'type': 'unlock_recipes', 'chapter': 1}, flag('ch1p_taught'), {'type': 'affinity', 'amount': 2}],
                                'next': 'a_menu'},
            'ch1t_blade_tell': {'face': 'neutral', 'do': [flag('ch1t_blade_told')], 'next': 'a_menu'},
            'ch1t_ward_weave': {'face': 'smile', 'do': [{'type': 'take', 'item': THREAD, 'count': 1}, {'type': 'give', 'item': 'skyabyss:heartbeat_ward_stone_whale', 'count': 1},
                                                        flag('ch1t_woven'), {'type': 'affinity', 'amount': 2}], 'next': 'a_menu'},
            'ch1a_ready': {'face': 'smile', 'do': [flag('ch1a_ready_told'), {'type': 'affinity', 'amount': 1}], 'next': 'a_menu'},
            'ch1a_some': {'face': 'neutral', 'do': [flag('ch1a_some_told')], 'next': 'a_menu'},
            'ch1p_teach': {'face': 'neutral', 'do': [{'type': 'unlock_recipes', 'chapter': 1}, flag('ch1p_taught')], 'next': 'a_menu'},
            'ch1a_notes': {'face': 'neutral', 'do': [{'type': 'give_notes', 'chapter': 1}], 'next': 'a_menu'},
            'ch1a_ward': {'face': 'smile', 'do': [{'type': 'take', 'item': SCRAP, 'count': 8}, {'type': 'remake_ward', 'titan': 'stone_whale'},
                                                  {'type': 'affinity', 'amount': 1}], 'next': 'a_menu'},
            'ch1a_ward_ask': {'face': 'neutral', 'next': 'a_menu'},
        },
    })
    # a_menu への足し (bye の手前)
    # 支度の段に吸収した備えの頼みの会話の文
    DROP.extend([B + 'ch1p_gear_teach', B + 'ch1p_blade_report', B + 'ch1p_pulse_weave'])
    path = os.path.join(RES, 'data/skyabyss/dialogue/ch1/artisan.json')
    d = json.load(open(path, encoding='utf-8'))
    d['add_options'] = {'a_menu': {'before': 'bye', 'options': [
        {'id': 'teach', 'kind': 'quest', 'if': quest('ch1/trail_blade', 'started'), 'goto': 'ch1p_teach'},
        {'id': 'notes', 'kind': 'quest', 'if': quest('ch1/trail_blade', 'started'), 'goto': 'ch1a_notes'},
        {'id': 'ward', 'kind': 'give', 'if': [flag('ch1t_woven'), {'type': 'ward_ok', 'titan': 'stone_whale'},
                                              {'type': 'item', 'item': SCRAP, 'count': 8}], 'goto': 'ch1a_ward'},
        {'id': 'ward_ask', 'kind': 'quest', 'if': [flag('ch1t_woven'), {'type': 'ward_ok', 'titan': 'stone_whale'},
                                                   not_({'type': 'item', 'item': SCRAP, 'count': 8})], 'goto': 'ch1a_ward_ask'},
    ]}}
    write_json(path, d)

    put(CH1, B + 'ch1a_first',
        '……荷を見に来たのか。道具は覚えている。持ち主は、そうでもない。@@旅の職人、とでも呼んでくれ。名前は、荷のどこにも書いていなかった。@@旅装なら編める。刃も研ぐ。……ただ、頼まれたことは、そう長くは覚えていられない。急ぐなら、覚書に写しておけ。',
        '...Come to look at the pack? The tools remember. The owner, less so.@@Call me the traveling artisan. There was no name written anywhere in the pack.@@I can stitch road gear. I can hone a blade. ...But I do not keep what I am asked for very long. If it matters, copy it into your notes.')
    put(CH1, B + 'ch1a_deja',
        'ああ、荷のそばにいた人か。……いや、違う。前にも会った気がするだけだ。@@道具は覚えている。持ち主は、そうでもない。覚書を渡しておく。写しておけ。',
        'Ah, you were by the pack. ...No. I only feel I have met you before.@@The tools remember. The owner, less so. Take these notes; copy what matters.')
    put(CH1, B + 'ch1t_blade_show',
        '折れた剣を見たのか。台座の。……柄の巻き方を、覚えているか。@@あれは、俺が教えた巻き方だ。……いや、教えた気がするだけだ。手が、そう言っている。@@鉄の剣なら、作業台で作れる。研ぐのは岩の砥石だ。岩殻片と骨片と火打ち石。作り方は、覚書に写しておいた。研がずに振るな。',
        'You saw the broken sword. On the plinth. ...Do you remember how the grip was wound?@@That is the way I teach it. ...No. I only feel I taught it. My hands say so.@@An iron sword you can make at a workbench. Hone it on a rock whetstone: shell scraps, a bone chip and flint. I copied the way into your notes. Do not swing it unhoned.')
    put(CH1, B + 'ch1t_blade_tell',
        '折れた剣を見た。割れ殻の洞の奥だ。……いつ見たのかは、覚えていない。@@台座の上に、横たえてあった。誰かが、また取りに来ると思ったんだろう。見てくるといい。',
        'I saw a broken sword. Deep in the Cleft Shell. ...When, I do not remember.@@It lay on a plinth. Someone meant to come back for it, I suppose. Go and look.')
    put(CH1, B + 'ch1t_ward_weave',
        '殻の護り糸か。いい糸だ。鼓動に合わせて、ゆるむようにできている。@@編んでおこう。倒れかけた一度だけ、これが肩で受けてくれる。使えば、ほどけて消える。ほどけたら、また持っておいで。手間は、岩殻片でいい。',
        'Shell-ward thread. A good one. It is made to slacken in time with a beat.@@I will weave it. Once, when you would fall, it will take the blow on your shoulder. Used, it unravels and is gone. When it does, bring it back. Shell scraps will do for my trouble.')
    put(CH1, B + 'ch1a_ready',
        'いい荷の持ち方だ。……身も、刃も、糧も、道の用意ができている。@@鎖が降りたら、迷うな。迷うと、荷が重くなる。',
        'You carry your pack well. ...Body, blade, provisions: you are ready for the road.@@When the chain comes down, do not hesitate. Hesitation makes a pack heavy.')
    put(CH1, B + 'ch1a_some',
        '支度は、まだ半分といったところか。急がなくていい。鯨は、待ってはくれないが、急かしもしない。@@足りないものは、たいてい手の届くところにある。道の脇に、割れ殻の洞に、骨の山に。',
        'Half-ready, by the look. No need to hurry. The whale will not wait, but it will not hurry you either.@@What you lack is usually within reach: beside the road, in the Cleft Shell, under the bones.')
    put(CH1, B + 'ch1p_teach',
        '作り方か。……岩殻片は、骨片と合わせて砥石にもなる。固焼きも、軟膏もだ。覚書に書いてある。@@手が思い出すまで、作業台で何度でも広げろ。',
        'The way of it? ...Shell scraps, with bone chips, make whetstones too. Hardtack and salve as well. It is in your notes.@@Lay it out on a workbench as often as it takes for your hands to remember.')
    put(CH1, B + 'ch1a_notes',
        'なくしたのか。……荷のどこかに、もう一冊挟んであったはずだ。ほら。@@道具は、なくなる。覚書も、なくなる。写しは、いくつあってもいい。',
        'Lost them? ...There should be another copy tucked in the pack somewhere. Here.@@Tools get lost. Notes get lost. There is no harm in more copies.')
    put(CH1, B + 'ch1a_ward',
        '護りが、ほどけたか。……岩殻片が要る。持ってきたな。@@編み直しておいた。一日にひとつだけだ。糸の手が、そう言っている。',
        'The ward came unwoven. ...I need shell scraps. You brought them.@@It is woven again. One a day, no more. The thread-hand says so.')
    put(CH1, B + 'ch1a_ward_ask',
        '護りを編み直すなら、岩殻片が要る。手間の分だ。道の脇か、洞か、骨の山に落ちている。@@それと、一日にひとつまで。急ぐなら、先に糸を数えておけ。',
        'To weave a ward again I need shell scraps, for the trouble. They lie beside the road, in the cave, under the bones.@@And one a day at most. If you are in a hurry, count your thread first.')
    put(CH1, B + 'a_menu.teach', '作り方を、もう一度教えてほしい。', 'Teach me how to make things again.')
    put(CH1, B + 'a_menu.notes', '旅の覚書を、なくした。', 'I lost my wayfarer\'s notes.')
    put(CH1, B + 'a_menu.ward', '鼓動守を、編み直してほしい。（岩殻片 8 つ）', 'Weave my heartbeat ward again. (eight shell scraps)')
    put(CH1, B + 'a_menu.ward_ask', '鼓動守を、編み直してほしい。', 'Weave my heartbeat ward again.')


def merge(path, table, sort):
    with open(path, encoding='utf-8') as f:
        merged = json.load(f)
    merged.update(table)
    for k in DROP:
        merged.pop(k, None)
    out = dict(sorted(merged.items())) if sort else merged
    write_json(path, out)


def main():
    base()
    ch1()
    for code, name in (('ja', 'ja_jp'), ('en', 'en_us')):
        merge(os.path.join(RES, 'assets/skyabyss_npc/lang/%s.json' % name), NPC[code], False)
        merge(os.path.join(RES, 'assets/skyabyss_ch1/lang/%s.json' % name), CH1[code], True)
    print('wrote artisan dialogue: %d common + %d ch1 texts' % (len(NPC['ja']), len(CH1['ja'])))


if __name__ == '__main__':
    main()
