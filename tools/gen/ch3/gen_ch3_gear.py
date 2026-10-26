#!/usr/bin/env python3
"""三章の備えの品 (docs/design/21 の 1.2・5.1) のレシピ・文・職人の会話を書き出す。絵は tools/install/install_ch3_gear_assets.py。

  python -I tools/gen/ch3/gen_ch3_gear.py

書くもの:
  data/skyabyss/recipe/peak_crossing_{helmet,chestplate,leggings,boots}.json, thunder_whetstone.json, wind_dumpling.json, lightning_oil.json,
    wind_streamer.json, copper_wire.json   作業台のレシピ (旅の覚書 (三章) か職人に教わるまで作れない = guide/RecipeLock。ID は guide/Ch3Gear.RECIPES)
  data/skyabyss/dialogue/ch3/artisan_gear.json   旅の職人の足し書き (三章の覚書を渡す・作り方を教える・雷翼の鼓動守を編む / 編み直す)
  assets/skyabyss_ch3gear/lang/{ja_jp,en_us}.json   名前・説明・知らせ・備えの手段の名前・旅の覚書 (三章) の行・職人の台詞 (このファイルで丸ごと書く)

旅装の素材の合計 (4 部位): 焦げ羽 12・帯電綿 6・銅線 6・潮殻の殻片 4 (21 の 5.1 の備え 1 と同じ数)。
"""
import json
import os

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__)))))
RES = os.path.join(ROOT, 'src', 'main', 'resources')

ja = {}
en = {}


def put(key, j, e):
    ja[key] = j
    en[key] = e


def write_json(path, data, indent=2):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w', encoding='utf-8', newline='\n') as f:
        json.dump(data, f, ensure_ascii=False, indent=indent)
        f.write('\n')


F = {'item': 'skyabyss:scorched_feather'}
C = {'item': 'skyabyss:static_cotton'}
W = {'item': 'skyabyss:copper_wire'}
T = {'item': 'skyabyss:tide_shell_shard'}


def shaped(name, pattern, key, result, count=1, category='equipment'):
    write_json(os.path.join(RES, 'data/skyabyss/recipe/%s.json' % name), {
        'type': 'minecraft:crafting_shaped', 'category': category, 'pattern': pattern, 'key': key,
        'result': {'id': 'skyabyss:' + result, 'count': count}})


def shapeless(name, ingredients, result, count=1, category='misc'):
    write_json(os.path.join(RES, 'data/skyabyss/recipe/%s.json' % name), {
        'type': 'minecraft:crafting_shapeless', 'category': category, 'ingredients': ingredients,
        'result': {'id': 'skyabyss:' + result, 'count': count}})


RECIPE_TOTAL = {'skyabyss:scorched_feather': 12, 'skyabyss:static_cotton': 6, 'skyabyss:copper_wire': 6, 'skyabyss:tide_shell_shard': 4}


def recipes():
    # 焦げ羽 F・帯電綿 C・銅線 W・潮殻の殻片 T。4 部位の合計: F12 C6 W6 T4
    shaped('peak_crossing_helmet', ['FCF', 'CTC'], {'F': F, 'C': C, 'T': T}, 'peak_crossing_helmet')                     # F2 C3 T1
    shaped('peak_crossing_chestplate', ['FFF', 'WTW', 'FCF'], {'F': F, 'C': C, 'W': W, 'T': T}, 'peak_crossing_chestplate')  # F5 W2 C1 T1
    shaped('peak_crossing_leggings', ['FTF', 'W W', 'C C'], {'F': F, 'C': C, 'W': W, 'T': T}, 'peak_crossing_leggings')     # F2 T1 W2 C2
    shaped('peak_crossing_boots', ['FTF', 'WFW'], {'F': F, 'W': W, 'T': T}, 'peak_crossing_boots')                       # F3 T1 W2
    # 雷の砥石: 銅線 2 + 帯電綿 1 + 火打ち石 1 -> 2 つ
    shapeless('thunder_whetstone', [W, W, C, {'item': 'minecraft:flint'}], 'thunder_whetstone', 2)
    # 風干しの団子: 小麦 3 + 砂糖 1 + 棒 1 (串) -> 3 つ
    shapeless('wind_dumpling', [{'item': 'minecraft:wheat'}] * 3 + [{'item': 'minecraft:sugar'}, {'item': 'minecraft:stick'}], 'wind_dumpling', 3)
    # 避雷の油: ガラス瓶 2 + 帯電綿 1 + 銅線 1 -> 2 つ
    shapeless('lightning_oil', [{'item': 'minecraft:glass_bottle'}] * 2 + [C, W], 'lightning_oil', 2)
    # 風読みの小旗: 焦げ羽 2・帯電綿 1 (吹き流し)・銅線 2 (竿)
    shaped('wind_streamer', ['FCF', ' W ', ' W '], {'F': F, 'C': C, 'W': W}, 'wind_streamer', 1, 'misc')
    # 銅線: 銅のインゴット 1 -> 3 (地上の敵の落とし物でも集まる)
    shapeless('copper_wire', [{'item': 'minecraft:copper_ingot'}], 'copper_wire', 3)


# ---------------------------------------------------------------- 職人の会話 (三章)
def flag(name):
    return {'type': 'flag', 'flag': name}


def not_(c):
    return {'type': 'not', 'of': c}


def dialogue():
    ward_ok = {'type': 'ward_ok', 'titan': 'thunder_wing'}
    refill = {'type': 'item', 'item': 'skyabyss:static_cotton', 'count': 8, 'optional': True}
    write_json(os.path.join(RES, 'data/skyabyss/dialogue/ch3/artisan_gear.json'), {
        'npc': 'skyabyss:artisan', 'extends': True,
        'entries': [
            {'node': 'ch3t_ward_weave', 'if': [{'type': 'item', 'item': 'skyabyss:thunder_ward_thread', 'count': 1, 'optional': True},
                                               not_(flag('ch3t_woven')), ward_ok]},
            {'node': 'ch3a_first', 'if': [{'type': 'stage', 'min': 2, 'max': 2}, not_(flag('ch3a_met'))]},
        ],
        'nodes': {
            'ch3a_first': {'face': 'neutral', 'do': [flag('ch3a_met'), flag('met'), {'type': 'give_notes', 'chapter': 3}, {'type': 'affinity', 'amount': 1}],
                           'next': 'a_menu'},
            'ch3p_teach': {'face': 'neutral', 'do': [{'type': 'unlock_recipes', 'chapter': 3}, flag('ch3p_taught')], 'next': 'a_menu'},
            'ch3t_ward_weave': {'face': 'smile', 'do': [{'type': 'take', 'item': 'skyabyss:thunder_ward_thread', 'count': 1},
                                                        {'type': 'give', 'item': 'skyabyss:heartbeat_ward_thunder_wing', 'count': 1},
                                                        flag('ch3t_woven'), {'type': 'affinity', 'amount': 2}], 'next': 'a_menu'},
            'ch3a_ward': {'face': 'smile', 'do': [{'type': 'take', 'item': 'skyabyss:static_cotton', 'count': 8}, {'type': 'remake_ward', 'titan': 'thunder_wing'},
                                                  {'type': 'affinity', 'amount': 1}], 'next': 'a_menu'},
        },
        'add_options': {'a_menu': {'before': 'bye', 'options': [
            {'id': 'teach3', 'kind': 'quest', 'if': [{'type': 'stage', 'min': 2, 'max': 2}, flag('ch3a_met')], 'goto': 'ch3p_teach'},
            {'id': 'ward3', 'kind': 'give', 'if': [flag('ch3t_woven'), ward_ok, refill], 'goto': 'ch3a_ward'},
        ]}},
    })
    d = 'dialogue.skyabyss.artisan.'
    put(d + 'ch3a_first', 'また会ったな、旅の人。……峰の風は、針金をよく鳴らす。耳を澄ますといい。\nこれは峰の覚書だ。焦げた羽と、帯電した綿の扱いを書いておいた。',
        'We meet again, traveller. ...The peak wind makes wire sing. Listen to it.\nThese are notes for the peaks. I wrote down how to handle scorched feathers and charged cotton.')
    put(d + 'ch3p_teach', '素材のことか。……焦げ羽は軽い。帯電綿は縫い目に詰めると、雷が肌まで来ない。銅線は縁に巻く。\n砥石も、団子も、油も、覚書に写しておいた。鍛冶場と薬師へ持っていけ。小旗は風の向きを見るためのものだ。',
        'The materials? ...Scorched feathers are light. Pack charged cotton into the seams and the lightning stays off your skin. Copper wire goes round the hems.\nThe whetstone, the dumplings and the oil are in the notes. Take them to the smithy and the apothecary. The streamer is for reading the wind.')
    put(d + 'ch3t_ward_weave', '雷の護り糸か。……指がしびれる。生きている糸だ。\n鼓動守に編もう。翼の中で鼓動が速くなったら、これを握れ。',
        'Thunder ward thread. ...It numbs the fingers. A living thread.\nI will weave it into a heartbeat ward. When the beat quickens inside the wing, hold it.')
    put(d + 'ch3a_ward', '護りがほどけたか。帯電綿があるな。峰に浮く毛玉が落とすやつだ。……編み直しておいた。',
        'The ward came undone? You brought the charged cotton. ...There, rewoven. One a day, no more.')
    put(d + 'a_menu.teach3', '峰越えの旅装を教えてほしい', 'Teach me the Peak Crossing gear')
    put(d + 'a_menu.ward3', '雷翼の鼓動守を編み直してほしい（帯電綿 8 つ）', 'Reweave the Thunder Wing ward (8 charged cotton)')


# ---------------------------------------------------------------- 文
def texts():
    put('item.skyabyss.scorched_feather', '焦げ羽', 'Scorched Feather')
    put('item.skyabyss.scorched_feather.desc', '雷に先を焼かれた羽。軽く、旅装と小旗に', 'A feather singed at the tip by lightning. Light; for travel gear and streamers.')
    put('item.skyabyss.static_cotton', '帯電綿', 'Static Cotton')
    put('item.skyabyss.static_cotton.desc', '触るとぱちりと鳴る綿。縫い目に詰めると雷を逃がす', 'Cotton that snaps when touched. Packed into seams, it sheds lightning.')
    put('item.skyabyss.copper_wire', '銅線', 'Copper Wire')
    put('item.skyabyss.copper_wire.desc', '細く伸ばした銅。旅装の縁、砥石、油に', 'Thin drawn copper. For hems, whetstones and oil.')
    put('item.skyabyss.thunder_ward_thread', '雷の護り糸', 'Thunder Ward Thread')
    put('item.skyabyss.thunder_ward_thread.desc', '指がしびれる白い糸。鼓動守 (雷翼) の材料', 'A white thread that numbs the fingers. Material for the Thunder Wing heartbeat ward.')
    put('item.skyabyss.peak_crossing_helmet', '峰越えの頭巾', 'Peak Crossing Hood')
    put('item.skyabyss.peak_crossing_chestplate', '峰越えの上衣', 'Peak Crossing Coat')
    put('item.skyabyss.peak_crossing_leggings', '峰越えの裾', 'Peak Crossing Leggings')
    put('item.skyabyss.peak_crossing_boots', '峰越えの足袋', 'Peak Crossing Boots')
    put('item.skyabyss.peak_crossing_armor.desc', '焦げ羽を重ね、縫い目に帯電綿を詰めた旅の装い。雷が肌まで来にくい（一式で感電の傷 −25%）',
        'Layered scorched feathers with charged cotton in the seams. Lightning rarely reaches the skin (full set: shock damage -25%).')
    put('gear.skyabyss.origin.peak_crossing', '峰越えの旅装', 'Peak Crossing gear')
    put('item.skyabyss.peak_crossing_helmet.story', '「風を聞く耳は、塞がない。」', '"Never cover the ears that hear the wind."')
    put('item.skyabyss.peak_crossing_chestplate.story', '「焦げた羽ほど、よく飛んだ。」', '"The more singed the feather, the farther it flew."')
    put('item.skyabyss.peak_crossing_leggings.story', '「銅の縁が鳴ったら、伏せろ。」', '"When the copper hem hums, get down."')
    put('item.skyabyss.peak_crossing_boots.story', '「峰の石は、踏む前に一度叩く。」', '"Tap a peak stone once before you trust it."')
    put('item.skyabyss.thunder_whetstone', '雷の砥石', 'Thunder Whetstone')
    put('item.skyabyss.thunder_whetstone.desc', '使うと 20 分のあいだ刃に雷が宿る（攻撃 +2.2）。当てるたびに小さな火花が散る',
        'Charges your blade for twenty minutes (+2.2 attack). Every hit throws a small spark.')
    put('effect.skyabyss.thunder_whetted', '雷の刃', 'Thunder Edge')
    put('message.skyabyss.thunder_whetstone.used', '刃が細く鳴った。二十分のあいだ、雷が刃に残る', 'The edge hums thinly. For twenty minutes the lightning stays in it.')
    put('item.skyabyss.wind_dumpling', '風干しの団子', 'Wind-Dried Dumpling')
    put('item.skyabyss.wind_dumpling.desc', '峰の風で干した団子。体が軽くなり、落ちるのがゆっくりになる（吸収 II 60 秒・落下速度低下 20 秒）',
        'Dumplings dried in the peak wind. You feel lighter and fall slowly (Absorption II 60 s, Slow Falling 20 s).')
    put('item.skyabyss.lightning_oil', '避雷の油', 'Lightning Oil')
    put('item.skyabyss.lightning_oil.desc', '銅を溶かした油。飲むと 90 秒、感電と雷の傷が半分になる。雷は高い銅へ逃げる、と覚える',
        'Oil with copper dissolved in it. Drink it and shocks and lightning hurt half as much for ninety seconds. Lightning runs to high copper.')
    put('effect.skyabyss.grounded', '避雷', 'Grounded')
    put('message.skyabyss.lightning_oil.used', '舌に銅の味が残る。九十秒のあいだ、雷は体を素通りしやすい', 'A taste of copper lingers. For ninety seconds lightning slips past you more easily.')
    put('item.skyabyss.wind_streamer', '風読みの小旗', 'Wind Streamer')
    put('item.skyabyss.wind_streamer.desc', '立てると 30 秒、風下へ細い光の筋が伸び、次の嵐までの目安が分かる（30 秒に一度。なくならない）',
        'Plant it to see a thin line of light run downwind for thirty seconds, and how long until the next storm (once every 30 seconds; never used up).')
    put('message.skyabyss.wind_streamer.read', '風は%sへ流れる。次の嵐まで、およそ %s 分', 'The wind runs %s. The next storm is about %s minutes away.')
    put('message.skyabyss.wind_streamer.now', '風は%sへ流れる。いま、嵐の中だ', 'The wind runs %s. You are in the storm now.')
    put('message.skyabyss.wind_streamer.still', '風は%sへ流れる。嵐の気配は読めない', 'The wind runs %s. No storm can be read.')
    for k, (j, e) in {'n': ('北', 'north'), 'ne': ('北東', 'northeast'), 'e': ('東', 'east'), 'se': ('南東', 'southeast'), 's': ('南', 'south'),
                      'sw': ('南西', 'southwest'), 'w': ('西', 'west'), 'nw': ('北西', 'northwest')}.items():
        put('message.skyabyss.wind_streamer.dir.' + k, j, e)
    put('message.skyabyss.readiness.remedy.name.peak_crossing', '峰越えの旅装', 'the Peak Crossing gear')
    put('message.skyabyss.readiness.remedy.name.thunder_whetstone', '雷の砥石', 'a thunder whetstone')
    put('message.skyabyss.readiness.remedy.name.wind_dumpling', '風干しの団子', 'wind-dried dumplings')
    put('message.skyabyss.readiness.remedy.name.lightning_oil', '避雷の油', 'lightning oil')
    lines = [
        ('帯電毛玉は、毛が逆立ってから放電する。固まって立つと、雷が隣の人へ渡る。毛が立ったら、散る。',
         'A static puff discharges after its fluff stands on end. Stand close together and the spark jumps to your neighbour. When the fluff rises, spread out.'),
        ('峰の雛は、飛び上がってから地面に光る線を引く。線の外へ一歩。外した雛は岩に当たって、しばらく動けない。',
         'A peak fledgling rises, then draws a glowing line on the ground. Step off the line. A fledgling that misses hits the rock and reels.'),
        ('雷拾いは逃げるだけで、噛まない。驚かせると荷を落とす。追えば、焼けた荷の原に出る。',
         'Lightning gleaners only flee. Startle one and it drops its pack. Follow it and you reach the field of burnt cargo.'),
        ('焼けた羽守は硬い。頂の柱の雷を浴びせると、しばらく刃が通る。足もとに輪が出たら、外へ。',
         'The scorched warden is hard to cut. Let the pillar\'s lightning strike it and your blade bites for a while. When a ring appears at its feet, get out.'),
        ('雷は高い銅へ落ちる。柱のそばに立たない。避雷の油を飲めば、感電は半分で済む。',
         'Lightning falls on high copper. Do not stand by the pillar. Drink lightning oil and shocks hurt half as much.'),
        ('風読みの小旗を立てれば、風の向きと、次の嵐までの目安が分かる。背の嵐の拍も、同じように読む。',
         'Plant a wind streamer to learn the wind and how long until the next storm. Read the storm beat on the wing the same way.'),
        ('焦げ羽と帯電綿と銅線は、旅装にも、雷の砥石にも、油にも小旗にもなる。鍛冶場と薬師が誂えてくれる。',
         'Scorched feathers, charged cotton and copper wire make travel gear, thunder whetstones, oil and streamers. The smithy and the apothecary make them.'),
    ]
    for i, (j, e) in enumerate(lines, 1):
        put('wayfarer_notes.ch3.line.%d' % i, j, e)


def main():
    recipes()
    dialogue()
    texts()
    for code, table in (('ja_jp', ja), ('en_us', en)):
        write_json(os.path.join(RES, 'assets/skyabyss_ch3gear/lang/%s.json' % code), dict(sorted(table.items())))
    print('wrote ch3 gear: 9 recipes, dialogue, %d texts' % len(ja))


if __name__ == '__main__':
    main()
