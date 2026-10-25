# -*- coding: utf-8 -*-
# はじまりの島の道の文と、船着き場の渡し守の会話 (world/continent/IslandTrail。2026-10-26)
#   python tools/gen/story/gen_island_trail.py
# 渡し守は汐守の渡し守と同じ絵 (同じ人かどうかは語らない)。島の道 (足跡・峠の野盗・休み場・祠) を済ませた人だけ乗せる (会話の印 island_ready)
import json

DIALOGUE = {
    'npc': 'skyabyss:guest_island_ferryman',
    'entries': [
        {'node': 'ready', 'if': {'type': 'flag', 'flag': 'island_ready'}},
        {'node': 'early'},
    ],
    'nodes': {
        'early': {'face': 'grave', 'options': [
            {'id': 'why', 'goto': 'early2', 'kind': 'talk'},
            {'id': 'bye', 'goto': 'end', 'kind': 'leave'}]},
        'early2': {'face': 'neutral', 'options': [{'id': 'ok', 'goto': 'end', 'kind': 'talk'}]},
        'ready': {'face': 'neutral', 'options': [
            {'id': 'cross', 'goto': 'end', 'kind': 'talk', 'do': [{'type': 'town', 'act': 'island_cross'}]},
            {'id': 'later', 'goto': 'end', 'kind': 'leave'}]},
    },
}

TOWN = {
    'entity.skyabyss.guest_island_ferryman': ('渡し守', 'Ferryman'),
    'dialogue.skyabyss.guest_island_ferryman.early': (
        '都へ渡りたいのかい。……まだだ。島の道を歩いてからにしな。\n峠の野盗、窪地の足跡、休み場の火、それから宿り木の祠。祠に祈ってから来な。',
        "Want to cross to the capital? ...Not yet. Walk the island road first.\nThe bandits at the pass, the footprints in the hollow, the fire at the rest stop, then the mistletoe shrine. Come back once you've prayed there."),
    'dialogue.skyabyss.guest_island_ferryman.early.why': ('どうして？', 'Why?'),
    'dialogue.skyabyss.guest_island_ferryman.early.bye': ('わかった', 'All right'),
    'dialogue.skyabyss.guest_island_ferryman.early2': (
        '目が覚めたばかりの者は、たいてい自分の名前も忘れてる。島の道を歩くうちに、少しずつ戻る。\n……戻らないものも、あるがね。',
        "Those who've only just woken usually can't even remember their own name. It comes back, bit by bit, as they walk the island road.\n...Some things don't come back."),
    'dialogue.skyabyss.guest_island_ferryman.early2.ok': ('歩いてくる', "I'll walk it"),
    'dialogue.skyabyss.guest_island_ferryman.ready': (
        '祠の匂いがするな。……島の道は歩いたらしい。\n乗りな。鐘の都まで漕いでやる。',
        "You smell of the shrine. ...So you've walked the island road.\nGet in. I'll row you to the Bell Capital."),
    'dialogue.skyabyss.guest_island_ferryman.ready.cross': ('鐘の都へ渡る', 'Cross to the Bell Capital'),
    'dialogue.skyabyss.guest_island_ferryman.ready.later': ('まだ島にいる', "I'll stay a while"),
}

OPENING = {
    'objective.skyabyss.island_prints': ('道の先の窪地を見に行こう', 'Look at the hollow up the road'),
    'objective.skyabyss.island_bandits': ('峠の野盗を退けよう', 'Drive off the bandits at the pass'),
    'objective.skyabyss.island_rest': ('旅の休み場の焚き火で休もう', 'Rest by the fire at the rest stop'),
    'objective.skyabyss.island_shrine': ('宿り木の祠に祈ろう', 'Pray at the mistletoe shrine'),
    'objective.skyabyss.take_ferry': ('船着き場の渡し守と話そう', 'Talk to the ferryman at the dock'),
}

MAIN = {
    # 島の道の分かれ目の札 (IslandTrail.buildSigns)
    'sign.skyabyss.island.prints': ('足跡の窪地', 'Footprint Hollow'),
    'sign.skyabyss.island.bandits': ('野盗の野営', 'Bandit Camp'),
    'sign.skyabyss.island.fire': ('旅の休み場', 'Rest Stop'),
    'sign.skyabyss.island.shrine': ('宿り木の祠', 'Mistletoe Shrine'),
    'message.skyabyss.island.bandit_count': ('野盗を退けた %s / %s', 'Bandits driven off %s / %s'),
    'message.skyabyss.island.bandits_done': ('峠の野盗を退けた。柵が開いた', 'The bandits at the pass are gone. The barricade is open'),
    'message.skyabyss.island.gate_open': ('峠の柵が開いた', 'The barricade at the pass is open'),
    'message.skyabyss.island.rested': ('焚き火で休んだ。樽の糧は、いつでも食べてよい', "You rested by the fire. The food in the barrel is yours whenever you need it"),
    'message.skyabyss.island.prayed': ('宿り木の祠に祈った。倒れたら、ここで目覚める', "You prayed at the mistletoe shrine. If you fall, you'll wake here"),
    'message.skyabyss.island.skill_hint': ('技を一つ覚えよう（K で技の画面）', 'Learn a skill (K opens the skill screen)'),
}

# 渡りの場面 (ferry_crossing) と、都を見上げる場面 (capital_arrival)。場面の文は skyabyss_cinematic (CinematicSelfTest が見る)
CINEMATIC = {
    'cinematic.skyabyss.speaker.ferryman': ('渡し守', 'Ferryman'),
    'cinematic.skyabyss.ferry.f1': ('目が覚めたら島にいた、か。……そういう客は、ときどき乗せる。', "Woke up on the island, did you. ...I get passengers like that, now and then."),
    'cinematic.skyabyss.ferry.hero1': ('前にも、この海を渡った気がする。', "I feel like I've crossed this sea before."),
    'cinematic.skyabyss.ferry.f2': ('この海の向こうが鐘の都だ。鐘が鳴るうちは、都は落ちない。……そう言われてる。', "Past this sea is the Bell Capital. While the bells ring, it won't fall. ...So they say."),
    'cinematic.skyabyss.ferry.heaven1': ('鐘の方へ。あなたを覚えている人が、そこにいる。', 'Toward the bells. Someone there remembers you.'),
    'cinematic.skyabyss.ferry.f3': ('着いたら、振り返らずに門をくぐりな。……漕いでいるのが誰か、は聞かないでくれ。', "When we land, go through the gate without looking back. ...And don't ask who's rowing."),
    'cinematic.skyabyss.arrival.hero1': ('ここが、鐘の都……。', 'So this is the Bell Capital...'),
    'cinematic.skyabyss.arrival.heaven1': ('大鐘楼の足もとに、語り部がいる。', 'The storyteller waits at the foot of the great bell tower.'),
}


def merge(path, kv, lang_index):
    s = open(path, encoding='utf8').read()
    d = json.loads(s)
    for k, v in kv.items():
        if k in d:
            old = f'  "{k}": {json.dumps(d[k], ensure_ascii=False)}'
            assert old in s, (path, k)
            s = s.replace(old, f'  "{k}": {json.dumps(v[lang_index], ensure_ascii=False)}', 1)
    ins = ''.join(f'  "{k}": {json.dumps(v[lang_index], ensure_ascii=False)},\n' for k, v in kv.items() if k not in d)
    s = s.replace('{\n', '{\n' + ins, 1)
    json.loads(s)
    open(path, 'w', encoding='utf8', newline='\n').write(s)


with open('src/main/resources/data/skyabyss/dialogue/guest_island_ferryman.json', 'w', encoding='utf8', newline='\n') as f:
    json.dump(DIALOGUE, f, ensure_ascii=False, indent=2)
    f.write('\n')
for lang, i in (('ja_jp', 0), ('en_us', 1)):
    merge(f'src/main/resources/assets/skyabyss_town/lang/{lang}.json', TOWN, i)
    merge(f'src/main/resources/assets/skyabyss_opening/lang/{lang}.json', OPENING, i)
    merge(f'src/main/resources/assets/skyabyss/lang/{lang}.json', MAIN, i)
    merge(f'src/main/resources/assets/skyabyss_cinematic/lang/{lang}.json', CINEMATIC, i)
print('ok')
