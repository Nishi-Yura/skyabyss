#!/usr/bin/env python3
"""二章の備えの品 (docs/design/21 の 1.2・4.1、P5 の二章の分) のレシピと文を書き出す。絵は tools/install/install_ch2_gear_assets.py。

  python tools/gen/ch2/gen_ch2_gear.py

書くもの:
  data/skyabyss/recipe/tide_wader_{helmet,chestplate,leggings,boots}.json, salt_whetstone.json, tide_pickle.json, pearl_powder.json,
    tide_hourglass.json   作業台のレシピ (旅の覚書 (二章) か職人に教わるまで作れない = guide/RecipeLock。ID は guide/Ch2Gear.RECIPES)
  assets/skyabyss_ch2gear/lang/{ja_jp,en_us}.json   名前・説明・知らせ・備えの手段の名前・旅の覚書 (二章) の行・旅装の物語の一行 (このファイルで丸ごと書く)

旅装の素材の合計 (4 部位): 小真珠 10・塩結晶 12・岩鯨の鱗片 4・革 3。備えの頼み「身ごしらえ」(ch2/prep_gear) の集める数はこれと同じにする。
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


P = {'item': 'skyabyss:small_pearl'}
S = {'item': 'skyabyss:salt_crystal'}
W = {'item': 'skyabyss:stone_whale_shard'}
L = {'item': 'minecraft:leather'}
C = {'item': 'skyabyss:shell_chip'}
G = {'item': 'minecraft:glass'}


def shaped(name, pattern, key, result, count=1, category='equipment'):
    write_json(os.path.join(RES, 'data/skyabyss/recipe/%s.json' % name), {
        'type': 'minecraft:crafting_shaped', 'category': category, 'pattern': pattern, 'key': key,
        'result': {'id': 'skyabyss:' + result, 'count': count}})


def shapeless(name, ingredients, result, count=1, category='misc'):
    write_json(os.path.join(RES, 'data/skyabyss/recipe/%s.json' % name), {
        'type': 'minecraft:crafting_shapeless', 'category': category, 'ingredients': ingredients,
        'result': {'id': 'skyabyss:' + result, 'count': count}})


def recipes():
    # 小真珠 P・塩結晶 S・岩鯨の鱗片 W・革 L。4 部位の合計: P10 S12 W4 L3
    shaped('tide_wader_helmet', ['PSP', 'SWS'], {'P': P, 'S': S, 'W': W}, 'tide_wader_helmet')            # P2 S3 W1
    shaped('tide_wader_chestplate', ['SSS', 'PWP', 'SLS'], {'P': P, 'S': S, 'W': W, 'L': L}, 'tide_wader_chestplate')  # S5 P2 W1 L1
    shaped('tide_wader_leggings', ['PWP', 'S S', 'LPL'], {'P': P, 'S': S, 'W': W, 'L': L}, 'tide_wader_leggings')     # P3 W1 S2 L2
    shaped('tide_wader_boots', ['SPS', 'PWP'], {'P': P, 'S': S, 'W': W}, 'tide_wader_boots')               # S2 P3 W1
    # 塩の砥石: 塩結晶 2 + 貝の欠け 1 + 火打ち石 1 -> 2 つ
    shapeless('salt_whetstone', [S, S, C, {'item': 'minecraft:flint'}], 'salt_whetstone', 2)
    # 潮の塩漬け: 乾燥した昆布 3 + 塩結晶 1 -> 3 つ
    shapeless('tide_pickle', [{'item': 'minecraft:dried_kelp'}] * 3 + [S], 'tide_pickle', 3)
    # 真珠の粉薬: 小真珠 1 + 塩結晶 1 + 砂糖 1 -> 2 つ
    shapeless('pearl_powder', [P, S, {'item': 'minecraft:sugar'}], 'pearl_powder', 2)
    # 潮の刻みの砂時計: ガラス 2・塩結晶 4・小真珠 1 (上下のガラスの間を塩が落ちる)
    shaped('tide_hourglass', ['SGS', ' P ', 'SGS'], {'S': S, 'G': G, 'P': P}, 'tide_hourglass', 1, 'misc')


# ---------------------------------------------------------------- 文
def texts():
    # 素材
    put('item.skyabyss.small_pearl', '小真珠', 'Small Pearl')
    put('item.skyabyss.small_pearl.desc', '潮だまりの貝が抱いていた小さな真珠。旅装と粉薬に', 'A little pearl from a tidepool clam. For travel gear and powder.')
    put('item.skyabyss.salt_crystal', '塩結晶', 'Salt Crystal')
    put('item.skyabyss.salt_crystal.desc', '潮が置いていった白い結晶。旅装・砥石・塩漬けに', 'White crystal the tide left behind. For gear, whetstones and pickles.')
    put('item.skyabyss.pearl_ward_thread', '真珠の護り糸', 'Pearl Ward Thread')
    put('item.skyabyss.pearl_ward_thread.desc', '真珠色に光る細い糸。鼓動守 (潮殻) の材料', 'A fine thread with a nacre sheen. Material for the Tide Shell heartbeat ward.')
    put('item.skyabyss.shell_chip', '貝の欠け', 'Shell Chip')
    put('item.skyabyss.shell_chip.desc', '珊瑚色の貝の欠片。砥石と飾りに', 'A coral-coloured shell fragment. For whetstones and trinkets.')
    # 旅装
    put('item.skyabyss.tide_wader_helmet', '潮渡りの頭巾', 'Tide Wader Hood')
    put('item.skyabyss.tide_wader_chestplate', '潮渡りの上衣', 'Tide Wader Coat')
    put('item.skyabyss.tide_wader_leggings', '潮渡りの裾', 'Tide Wader Leggings')
    put('item.skyabyss.tide_wader_boots', '潮渡りの足袋', 'Tide Wader Boots')
    put('item.skyabyss.tide_wader_armor.desc', '塩を吸った布に真珠を縫いつけた旅の装い。水の中でも足が重くならない',
        'Salt-stiff cloth sewn with pearls. Your legs stay light in the water.')
    put('gear.skyabyss.origin.tide_wader', '潮渡りの旅装', 'Tide Wader gear')
    put('item.skyabyss.tide_wader_helmet.story', '「頭巾の塩は、誰の涙でもない。」', '"The salt on the hood is no one\'s tears."')
    put('item.skyabyss.tide_wader_chestplate.story', '「真珠の数だけ、潮を待った。」', '"One pearl for every tide it waited out."')
    put('item.skyabyss.tide_wader_leggings.story', '「裾は濡れても、足は覚えている。」', '"The hem gets wet. The legs remember."')
    put('item.skyabyss.tide_wader_boots.story', '「足袋の底に、引き潮の砂が残る。」', '"Ebb-tide sand stays in the soles."')
    # 砥石・糧・薬・道具
    put('item.skyabyss.salt_whetstone', '塩の砥石', 'Salt Whetstone')
    put('item.skyabyss.salt_whetstone.desc', '使うと 20 分のあいだ刃が塩で研がれる（攻撃 +1.8）。水の中でも切れ、水の中の一撃は塩が散ってもう少し深く入る',
        'Hones your blade with salt for twenty minutes (+1.8 attack). It cuts in water too: wet hits scatter salt and bite a little deeper.')
    put('effect.skyabyss.salt_whetted', '塩の刃', 'Salt Edge')
    put('message.skyabyss.salt_whetstone.used', '刃に塩を噛ませた。二十分のあいだ、水の中でも鈍らない', 'You worked salt into the edge. For twenty minutes it will not dull, even in water.')
    put('item.skyabyss.tide_pickle', '潮の塩漬け', 'Tide Pickle')
    put('item.skyabyss.tide_pickle.desc', '昆布を塩で漬けた浜の糧。60 秒、水の中で息が続く', 'Kelp pickled in salt. Lets you breathe underwater for sixty seconds.')
    put('item.skyabyss.pearl_powder', '真珠の粉薬', 'Pearl Powder')
    put('item.skyabyss.pearl_powder.desc', '真珠を挽いた粉。飲み下すとすぐに傷がふさがり (体力 6)、浮く体と重い足が戻る',
        'Ground pearl. Swallow it to close wounds at once (6 HP) and shake off levitation and slowness.')
    put('item.skyabyss.tide_hourglass', '潮の刻みの砂時計', 'Tide Hourglass')
    put('item.skyabyss.tide_hourglass.desc', '使うと、次の干潮と満潮までの秒数が分かる（世界の潮。30 秒に一度）',
        'Use it to learn how many seconds remain until the next low and high tide (the world\'s tide; once every 30 seconds).')
    put('message.skyabyss.tide_hourglass.low', '砂が速く落ちる。いまは干潮の刻。満ち潮まで あと %s 秒',
        'The sand runs fast. It is low tide now. High tide in %s seconds.')
    put('message.skyabyss.tide_hourglass.high', '次の干潮まで あと %s 秒。その満ち潮まで あと %s 秒',
        'Low tide in %s seconds. High tide again in %s seconds.')
    put('message.skyabyss.tide_hourglass.still', '砂が止まっている。潮が動いていない', 'The sand does not move. The tide is still.')
    # 備えの手段の名前 (潮殻の備えのときだけ)
    put('message.skyabyss.readiness.remedy.name.tide_wader', '潮渡りの旅装', 'the Tide Wader gear')
    put('message.skyabyss.readiness.remedy.name.salt_whetstone', '塩の砥石', 'a salt whetstone')
    put('message.skyabyss.readiness.remedy.name.tide_pickle', '潮の塩漬け', 'tide pickles')
    put('message.skyabyss.readiness.remedy.name.pearl_powder', '真珠の粉薬', 'pearl powder')
    # 旅の覚書 (二章)。敵の見分け方 (背中の敵の予習) と備えの一行
    lines = [
        ('潮だまり貝は、閉じた貝にしか見えない。近づくと殻がゆっくり開く。開ききる前に離れれば、また閉じる。',
         'A tidepool clam looks like any closed shell. It opens slowly as you near. Step away before it gapes and it shuts again.'),
        ('干潮の砂が小さく盛り上がったら、砂潜りが真下にいる。足を止めず、盛り上がりから一歩ずれる。',
         'When ebb-tide sand bulges, a sand burrower waits below. Keep moving and step off the bulge.'),
        ('宿借りは逃げるだけで、噛まない。殻を叩くと、拾い物を吐く。行列を追えば、浜の奥に何かある。',
         'Drift hermits only flee; they never bite. Tap the shell and they cough up finds. Follow the line and it leads somewhere.'),
        ('潮の番人は、銛を引いてから突く。床に青い輪が広がったら、輪の外へ。そこは水に沈む。',
         'The shore warden draws back its harpoon before it thrusts. When a blue ring spreads on the floor, leave it. That ground will flood.'),
        ('沈んだ塩の蔵は、干潮なら歩いて、満ち潮なら息を止めて潜って入れる。潮見の貝と砂時計で、刻みを読んでから向かう。',
         'The Sunken Salt Vault can be walked into at low tide, or entered by diving with a held breath at high tide. Read the tide with the tide-watching shell and the hourglass before you go.'),
        ('塩の砥石の刃は、水の中でも鈍らない。潮の背では、水に浸かった相手ほど深く切れる。',
         'A salt-honed edge does not dull in water. On the tide\'s back, it cuts deeper the wetter the foe.'),
        ('小真珠と塩結晶は、旅装にも、塩の砥石にも、塩漬けにも粉薬にもなる。鍛冶場と薬師が誂えてくれる。',
         'Small pearls and salt crystals make travel gear, salt whetstones, pickles and powder. The smithy and the apothecary make them.'),
    ]
    for i, (j, e) in enumerate(lines, 1):
        put('wayfarer_notes.ch2.line.%d' % i, j, e)


def main():
    recipes()
    texts()
    for code, table in (('ja_jp', ja), ('en_us', en)):
        write_json(os.path.join(RES, 'assets/skyabyss_ch2gear/lang/%s.json' % code), dict(sorted(table.items())))
    print('wrote ch2 gear: 8 recipes, %d texts' % len(ja))


if __name__ == '__main__':
    main()
