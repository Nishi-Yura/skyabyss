# -*- coding: utf-8 -*-
# 土地の町の人の頼み (town/RegionFolk、00 の 278 = A の続き)。近くの見どころ (ContinentPois の poi:<kind> の合図) へ行き、品を持って戻る
import json

# 段: ('go', poi の種類) / ('get', 品, 数) / ('kill', 敵, 数) / ('back',) = 話して渡す (品があれば渡す)
FOLK = {
    'tide_ferryman': dict(
        quest='town/tide_ferry', chapter='titan_tide', min=1, order=300,
        stages=[('go', 'beached_wreck'), ('back',)],
        give=[], reward=dict(xp=60, coins=15, items=[('minecraft:cooked_cod', 3)]),
        text=dict(
            title=('流れ着いた舟', 'The Drifted Boat'),
            summary=('渡し守に頼まれて、浜に打ち上がった難破船を見てくる。', 'For the ferryman, go and look at the wreck washed up on the beach.'),
            done=('難破船は空だった。渡し守は、あれを「帰り舟」と呼んだ。', 'The wreck was empty. The ferryman called it a "return boat".'),
            go_journal=('港から東へ、浜の先の難破船。渡し守の印があるのに、漕いだ覚えのない船。中に誰かいたか見てこよう。',
                        "East of the harbour, past the beach: a wreck with the ferryman's mark on it, though he never rowed it. See if anyone was aboard."),
            go_obj=('浜の難破船を見てこよう（港から東）', 'Look at the beached wreck (east of the harbour)'),
            back_journal=('誰もいなかった。渡し守に伝えよう。', 'No one was there. Tell the ferryman.'),
            back_obj=('渡し守に伝えよう', 'Tell the ferryman'),
            menu=('困っていることは？', 'Is something troubling you?'),
            ask=('東の浜に、難破船が打ち上がっとる。……妙なんだ。わしが漕いだ覚えのない船なのに、舳先にわしの渡しの印がある。\n見てきてくれんか。中に誰かいたかどうかだけでいい。',
                 "A wreck has washed up on the east beach. ...Strange thing. I never rowed that boat, but my ferry mark is on its bow.\nWould you go and look? Just tell me whether anyone was aboard."),
            ask_ok=('見てくる', "I'll go and look"),
            wait=('難破船は港から東、浜の先だ。……誰かいたかどうか、それだけでいい。', "The wreck's east of the harbour, past the beach. ...Just whether anyone was aboard."),
            back=('見てきたかい。', 'Did you see it?'),
            back_give=('誰もいなかった、と伝える', 'Tell him no one was there'),
            later=('また来る', "I'll come back"),
            done_line=('誰もいない、か。……そうか。なら、あれは帰り舟だったんだな。誰かを島へ送って、空で戻ってきた。\n礼だ。焼いた魚、持っておいき。',
                       "No one, eh. ...I see. Then it was a return boat. It carried someone to the island and came back empty.\nHere, for your trouble. Some grilled fish."),
            done_ok=('ありがとう', 'Thank you'),
        )),
    'tide_lightkeeper': dict(
        quest='town/tide_light', chapter='titan_tide', min=1, order=301,
        stages=[('get', 'minecraft:candle', 2), ('go', 'lighthouse'), ('back',)],
        give=[('minecraft:candle', 2)], reward=dict(xp=60, coins=15, items=[('minecraft:lantern', 1)]),
        text=dict(
            title=('岬の古い灯台', 'The Old Lighthouse on the Cape'),
            summary=('灯台守に頼まれて、岬の古い灯台を見てきて、ろうそくを二本届ける。', 'For the lightkeeper, look at the old lighthouse on the cape and bring two candles.'),
            done=('岬の古い灯台の壁にも、同じ刻みがあった。灯台守は、ろうそくを窓に立てた。', 'The old lighthouse wall had the same carving. The lightkeeper set the candles in the window.'),
            get_journal=('ろうそくを二本。道具屋で買える。', 'Two candles. The tool shop sells them.'),
            get_obj=('ろうそくを二本手に入れよう（道具屋）', 'Get two candles (tool shop)'),
            go_journal=('港から南西の岬に、古い灯台がある。壁に「潮は読める。待て」の刻みがあるか見てこよう。',
                        'An old lighthouse stands on the cape southwest of the harbour. See if the carving "The tide can be read. Wait." is on its wall.'),
            go_obj=('岬の古い灯台を見てこよう（港から南西）', 'Look at the old lighthouse (southwest of the harbour)'),
            back_journal=('刻みはあった。灯台守にろうそくを渡そう。', 'The carving was there. Give the lightkeeper the candles.'),
            back_obj=('灯台守にろうそくを渡そう', 'Give the lightkeeper the candles'),
            menu=('困っていることは？', 'Is something troubling you?'),
            ask=('岬の古い灯台にも、うちと同じ刻みがあるそうだ。「潮は読める。待て」。\n見てきてくれんか。それと、ろうそくを二本。道具屋にある。……古い灯台の分も、窓に立ててやりたくてな。',
                 'They say the old lighthouse on the cape has the same carving as ours. "The tide can be read. Wait."\nWould you go and see? And bring two candles from the tool shop. ...I want to light one for the old one too.'),
            ask_ok=('わかった', 'All right'),
            wait=('ろうそくは道具屋。古い灯台は港から南西の岬だ。', "Candles at the tool shop. The old lighthouse is on the cape southwest of the harbour."),
            back=('どうだった。', 'Well?'),
            back_give=('刻みはあった、と伝えてろうそくを渡す', 'Tell him the carving was there and hand over the candles'),
            later=('また来る', "I'll come back"),
            done_line=('あったか。……同じ手だな。わしの前の灯台守か、その前か。\nろうそくは一本ずつ窓に立てる。こっちと、あっちの分だ。これを持っていけ。暗い所を歩くなら要る。',
                       'It was there. ...Same hand. The keeper before me, or the one before that.\nOne candle in each window: one for here, one for there. Take this. You will want it in the dark.'),
            done_ok=('ありがとう', 'Thank you'),
        )),
    'storm_elder': dict(
        quest='town/storm_rods', chapter='titan_storm', min=2, order=302,
        stages=[('go', 'rod_tower'), ('get', 'minecraft:copper_ingot', 1), ('back',)],
        give=[('minecraft:copper_ingot', 1)], reward=dict(xp=80, coins=20, items=[('skyabyss:socket_shard_wind', 1)]),
        text=dict(
            title=('避雷の塔の銅', 'Copper for the Rod Tower'),
            summary=('里の長に頼まれて、避雷の塔の跡を見てきて、銅を一つ届ける。', 'For the elder, look at the old lightning-rod tower and bring a copper ingot.'),
            done=('避雷の塔の銅は、雷を地へ逃がすための継ぎ目が欠けていた。長は、銅を風の民の祠に供えた。', "The rod tower's copper was missing a joint that sends lightning into the ground. The elder offered the copper at the wind folk's shrine."),
            go_journal=('里から南東に、避雷の塔の跡がある。銅の柱がどこまで残っているか見てこよう。', 'Southeast of the village stands an old lightning-rod tower. See how much of its copper is left.'),
            go_obj=('避雷の塔の跡を見てこよう（里から南東）', 'Look at the rod tower ruin (southeast of the village)'),
            get_journal=('継ぎ目が欠けていた。銅を一つ。里の店で買える。', 'A joint is missing. One copper ingot, from the village shop.'),
            get_obj=('銅を一つ手に入れよう（里の店）', 'Get a copper ingot (village shop)'),
            back_journal=('里の長に銅を渡そう。', 'Give the elder the copper.'),
            back_obj=('里の長に銅を渡そう', 'Give the elder the copper'),
            menu=('困っていることは？', 'Is something troubling you?'),
            ask=('南東の避雷の塔の跡を、見てきてくれんか。祖先が雷を地へ逃がした柱だ。\nこのごろ、夕立の刻に塔の上で光が跳ねる。銅がどこか欠けておるのかもしれん。',
                 'Would you look at the old rod tower to the southeast? Our forebears sent the lightning into the ground with it.\nLately, at squall-time, light jumps on top of it. Perhaps some copper is missing.'),
            ask_ok=('見てくる', "I'll go and look"),
            wait=('塔は里から南東。銅は里の店にある。', 'The tower is southeast of the village. Copper is in the village shop.'),
            back=('塔はどうだった。', 'How was the tower?'),
            back_give=('継ぎ目が欠けていた、と伝えて銅を渡す', 'Tell him a joint was missing and hand over the copper'),
            later=('また来る', "I'll come back"),
            done_line=('継ぎ目か。……雷が地へ降りられずに、塔の上で迷っておったのだな。\n銅は風の民の祠に供えておく。継ぐのは若い者に頼もう。これは礼だ。風の欠片。武具の穴に嵌まる。',
                       "A joint. ...So the lightning couldn't reach the ground and wandered on top of the tower.\nI'll offer the copper at the wind folk's shrine and have the young ones mend it. For you: a wind shard. It fits a socket in your gear."),
            done_ok=('ありがとう', 'Thank you'),
        )),
    'storm_hutkeeper': dict(
        quest='town/storm_hut', chapter='titan_storm', min=2, order=303,
        stages=[('get', 'minecraft:bread', 3), ('go', 'wind_gate'), ('back',)],
        give=[('minecraft:bread', 3)], reward=dict(xp=80, coins=20, items=[('skyabyss:road_hardtack', 2)]),
        text=dict(
            title=('風の門の足跡', 'Footprints at the Wind Gate'),
            summary=('山小屋の番に頼まれて、風の門の跡を見てきて、パンを三つ届ける。', 'For the hut keeper, look at the wind gate ruin and bring three loaves of bread.'),
            done=('風の門の跡には、峰へ上る足跡だけがあった。山小屋の番は、パンを棚に置いた。', 'At the wind gate there were only footprints going up toward the peaks. The hut keeper put the bread on the shelf.'),
            get_journal=('パンを三つ。道具屋で買える。小屋に泊まる人の分。', "Three loaves of bread from the tool shop, for whoever stays at the hut."),
            get_obj=('パンを三つ手に入れよう（道具屋）', 'Get three loaves of bread (tool shop)'),
            go_journal=('里から南の、風の門の跡。夜に峰へ上った旅人がいる。戻った足跡があるか見てこよう。', 'The wind gate ruin, south of the village. A traveller went up to the peaks at night. See if any footprints come back.'),
            go_obj=('風の門の跡を見てこよう（里から南）', 'Look at the wind gate ruin (south of the village)'),
            back_journal=('上る足跡だけだった。山小屋の番にパンを渡そう。', 'Only footprints going up. Give the hut keeper the bread.'),
            back_obj=('山小屋の番にパンを渡そう', 'Give the hut keeper the bread'),
            menu=('困っていることは？', 'Is something troubling you?'),
            ask=('ゆうべ、旅の人が峰へ上った。雷の鳴る前に降りてこいと言ったんだが。\n南の風の門の跡を見てきてくれんか。戻った足跡があるかどうか。それと、パンを三つ。小屋の棚が空でな。',
                 "Last night a traveller went up to the peaks. I told them to come down before the thunder.\nWould you check the wind gate ruin to the south, whether any footprints come back? And bring three loaves. The hut's shelf is bare."),
            ask_ok=('わかった', 'All right'),
            wait=('パンは道具屋。風の門の跡は里から南だ。', 'Bread at the tool shop. The wind gate ruin is south of the village.'),
            back=('どうだった。', 'Well?'),
            back_give=('上る足跡だけだった、と伝えてパンを渡す', 'Tell him the footprints only went up, and hand over the bread'),
            later=('また来る', "I'll come back"),
            done_line=('上る足跡だけ、か。……そうか。\nパンは棚に置いておく。降りてくる人がいたら、腹が減っとるだろうからな。\nこれを持っていけ。峰の上じゃ、パンより固いのがいい。',
                       "Only going up, eh. ...I see.\nI'll put the bread on the shelf. If anyone does come down, they'll be hungry.\nTake these. Up on the peaks, something harder than bread keeps better."),
            done_ok=('ありがとう', 'Thank you'),
        )),
    'ember_survivor': dict(
        quest='town/ember_forge', chapter='titan_ember', min=3, order=304,
        stages=[('go', 'forge_ruin'), ('back',)],
        give=[], reward=dict(xp=100, coins=25, items=[('skyabyss:socket_shard_ash', 1)]),
        text=dict(
            title=('炉の跡の火', 'The Fire at the Old Forge'),
            summary=('灰の民の生き残りに頼まれて、西の炉の跡を見てくる。', 'For the last of the ash folk, look at the old forge to the west.'),
            done=('西の炉の跡の灰は、まだ温かかった。灰の民の生き残りは、それを「預かった火」と呼んだ。', 'The ash at the old forge to the west was still warm. The last of the ash folk called it "the fire we keep".'),
            go_journal=('村から西の炉の跡。灰がまだ温かいか、手を当てて見てこよう。', 'The old forge west of the village. Put a hand to the ash and see whether it is still warm.'),
            go_obj=('炉の跡を見てこよう（村から西）', 'Look at the old forge (west of the village)'),
            back_journal=('灰はまだ温かかった。生き残りに伝えよう。', 'The ash was still warm. Tell the survivor.'),
            back_obj=('灰の民の生き残りに伝えよう', 'Tell the last of the ash folk'),
            menu=('困っていることは？', 'Is something troubling you?'),
            ask=('西の炉の跡を、見てきてくれないか。灰の民が最後に火を預けた所だ。\n灰がまだ温かいかどうか。……わたしは、もう近づけない。',
                 'Would you look at the old forge to the west? It is where the ash folk last kept their fire.\nWhether the ash is still warm. ...I cannot go near it any more.'),
            ask_ok=('見てくる', "I'll go and look"),
            wait=('炉の跡は村から西だ。灰に手を当ててくれ。', 'The old forge is west of the village. Put a hand to the ash.'),
            back=('……どうだった。', '...Well?'),
            back_give=('まだ温かかった、と伝える', 'Tell them it was still warm'),
            later=('また来る', "I'll come back"),
            done_line=('まだ温かい。……そうか。返していない火が、まだあそこにある。\nこれを持っていってくれ。灰の欠片だ。預かった火の、ほんのひとかけ。',
                       "Still warm. ...So the fire we never gave back is still there.\nTake this, an ash shard. A tiny piece of the fire we keep."),
            done_ok=('ありがとう', 'Thank you'),
        )),
    'ember_watch': dict(
        quest='town/ember_watch', chapter='titan_ember', min=3, order=305,
        stages=[('kill', 'minecraft:pillager', 3), ('go', 'burnt_fort'), ('back',)],
        give=[], reward=dict(xp=100, coins=30, items=[('skyabyss:road_hardtack', 2)]),
        text=dict(
            title=('焼けた砦の見回り', 'Rounds at the Burnt Fort'),
            summary=('焼けた砦の見張りに頼まれて、野盗を三人退け、南西の焼けた砦を見回る。', 'For the burnt fort watch, drive off three bandits and walk the burnt fort to the southwest.'),
            done=('野盗を退け、焼けた砦を見回った。砦の見張り台からは、余燼の円の真ん中が見えた。', 'You drove off the bandits and walked the burnt fort. From its watchtower you could see the middle of the ember circle.'),
            kill_journal=('村の北東の野営に、野盗がいる。三人退けよう。', 'Bandits camp northeast of the village. Drive off three of them.'),
            kill_obj=('野盗を退けよう', 'Drive off the bandits'),
            go_journal=('村から南西の焼けた砦。見張り台から南の空を見てこよう。', 'The burnt fort southwest of the village. Look at the southern sky from its watchtower.'),
            go_obj=('焼けた砦を見回ろう（村から南西）', 'Walk the burnt fort (southwest of the village)'),
            back_journal=('見張りに伝えよう。', 'Report to the watch.'),
            back_obj=('砦の見張りに伝えよう', 'Report to the burnt fort watch'),
            menu=('困っていることは？', 'Is something troubling you?'),
            ask=('見張りを一人で回すのは骨が折れる。手を貸してくれ。\n北東の野営の野盗を三人退けて、それから南西の焼けた砦を見回ってくれ。見張り台から、南の空がどう見えるかも。',
                 "Keeping watch alone wears a man down. Lend me a hand.\nDrive off three of the bandits camped to the northeast, then walk the burnt fort to the southwest. Tell me how the southern sky looks from its watchtower."),
            ask_ok=('引き受ける', "I'll do it"),
            wait=('野営は村の北東、焼けた砦は南西だ。', 'The camp is northeast of the village, the burnt fort southwest.'),
            back=('どうだった。', 'Well?'),
            back_give=('南の空が赤かった、と伝える', 'Tell him the southern sky was red'),
            later=('また来る', "I'll come back"),
            done_line=('赤かったか。……あそこから見ると、息をしているのがわかるだろう。\n助かった。これは見張りの非常食だ。持っていけ。',
                       "Red, was it. ...From up there you can see it breathing, can't you.\nThanks. These are the watch's emergency rations. Take them."),
            done_ok=('ありがとう', 'Thank you'),
        )),
}


def stage_id(st):
    return st[0]


def quest_json(fid, f):
    stages = []
    for st in f['stages']:
        if st[0] == 'go':
            stages.append({'id': 'go', 'objectives': [{'id': 'go', 'type': 'signal', 'target': 'poi:' + st[1], 'party': False}]})
        elif st[0] == 'get':
            stages.append({'id': 'get', 'objectives': [{'id': 'get', 'type': 'collect', 'target': st[1], 'count': st[2]}]})
        elif st[0] == 'kill':
            stages.append({'id': 'kill', 'objectives': [{'id': 'kill', 'type': 'kill', 'target': st[1], 'count': st[2]}]})
        else:
            stages.append({'id': 'back', 'objectives': [{'id': 'back', 'type': 'signal', 'target': 'town_hand:guest_' + fid, 'party': False}]})
    r = f['reward']
    items = [{'item': 'skyabyss:bell_coin', 'count': r['coins']}] + [{'item': i, 'count': n} for i, n in r['items']]
    return {
        'category': 'sub',
        'order': f['order'],
        'giver': 'skyabyss:guest_' + fid,
        'chapter': f['chapter'],
        'min_stage': f['min'],
        'stages': stages,
        'reward': {'xp': r['xp'], 'items': items},
    }


def dialogue_json(fid, f, base):
    q = f['quest']
    ask = {
        'id': 'ask', 'goto': 'q_ask', 'kind': 'quest',
        'if': {'type': 'all', 'of': [{'type': 'stage', 'min': f['min']}, {'type': 'quest', 'quest': q, 'is': 'none'}]},
        'do': [{'type': 'quest_start', 'quest': q}],
    }
    leave = {'id': 'bye', 'goto': 'end', 'kind': 'leave'}
    nodes = {}
    for k, face in (('idle', 'neutral'), ('hint', 'grave'), ('after', 'smile')):
        nodes[k] = {'face': face, 'options': [dict(ask), dict(leave)]}
    nodes['q_ask'] = {'face': 'grave', 'options': [{'id': 'ok', 'goto': 'end', 'kind': 'talk'}]}
    nodes['q_wait'] = {'face': 'neutral'}
    do = [{'type': 'take', 'item': i, 'count': n} for i, n in f['give']] + [{'type': 'signal', 'signal': 'town_hand:guest_' + fid}]
    give = {'id': 'give', 'goto': 'q_done', 'kind': 'give' if f['give'] else 'quest', 'do': do}
    if f['give']:
        conds = [{'type': 'item', 'item': i, 'count': n} for i, n in f['give']]
        give['if'] = conds[0] if len(conds) == 1 else {'type': 'all', 'of': conds}
    nodes['q_back'] = {'face': 'neutral', 'options': [give, {'id': 'later', 'goto': 'end', 'kind': 'leave'}]}
    nodes['q_done'] = {'face': 'smile', 'options': [{'id': 'ok', 'goto': 'end', 'kind': 'talk'}]}
    return {
        'npc': 'skyabyss:guest_' + fid,
        'entries': [
            {'node': 'q_back', 'if': {'type': 'quest', 'quest': q, 'is': 'active', 'stage': 'back'}},
            {'node': 'q_wait', 'if': {'type': 'quest', 'quest': q, 'is': 'active'}},
        ] + base['entries'],
        'nodes': nodes,
    }


def lang(fid, f, i):
    t = f['text']
    qb = 'quest.skyabyss.' + f['quest'].replace('/', '.')
    db = 'dialogue.skyabyss.guest_' + fid
    kv = {
        qb + '.title': t['title'][i],
        qb + '.summary': t['summary'][i],
        qb + '.done': t['done'][i],
        db + '.q_ask': t['ask'][i],
        db + '.q_ask.ok': t['ask_ok'][i],
        db + '.q_wait': t['wait'][i],
        db + '.q_back': t['back'][i],
        db + '.q_back.give': t['back_give'][i],
        db + '.q_back.later': t['later'][i],
        db + '.q_done': t['done_line'][i],
        db + '.q_done.ok': t['done_ok'][i],
    }
    for k in ('idle', 'hint', 'after'):
        kv[f'{db}.{k}.ask'] = t['menu'][i]
        kv[f'{db}.{k}.bye'] = ('じゃあ、また' if i == 0 else 'See you')
    for st in f['stages']:
        s = st[0]
        kv[f'{qb}.{s}.journal'] = t[s + '_journal'][i]
        kv[f'{qb}.{s}.{s}'] = t[s + '_obj'][i]
    return kv


for fid, f in FOLK.items():
    with open(f'src/main/resources/data/skyabyss/quest/{f["quest"]}.json', 'w', encoding='utf8', newline='\n') as fh:
        json.dump(quest_json(fid, f), fh, ensure_ascii=False, indent=2)
        fh.write('\n')
    dp = f'src/main/resources/data/skyabyss/dialogue/guest_{fid}.json'
    base = json.load(open(dp, encoding='utf8'))
    base['entries'] = [e for e in base['entries'] if not e['node'].startswith('q_')]
    with open(dp, 'w', encoding='utf8', newline='\n') as fh:
        json.dump(dialogue_json(fid, f, base), fh, ensure_ascii=False, indent=2)
        fh.write('\n')
for lg, i in (('ja_jp', 0), ('en_us', 1)):
    p = f'src/main/resources/assets/skyabyss_town/lang/{lg}.json'
    s = open(p, encoding='utf8').read()
    d = json.loads(s)
    kv = {}
    for fid, f in FOLK.items():
        kv.update(lang(fid, f, i))
    for k, v in kv.items():
        if k in d:
            old = f'  "{k}": {json.dumps(d[k], ensure_ascii=False)}'
            assert old in s, k
            s = s.replace(old, f'  "{k}": {json.dumps(v, ensure_ascii=False)}', 1)
    ins = ''.join(f'  "{k}": {json.dumps(v, ensure_ascii=False)},\n' for k, v in kv.items() if k not in d)
    s = s.replace('{\n', '{\n' + ins, 1)
    json.loads(s)
    open(p, 'w', encoding='utf8', newline='\n').write(s)
print('ok')
