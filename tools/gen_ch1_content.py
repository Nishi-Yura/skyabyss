#!/usr/bin/env python3
"""序章 (日 1) と一章 岩鯨 (P4a) の中身を書き出す: 伝承 12 項目、クエスト 3 本、会話の足し書き 2 つ、言語ファイル (assets/skyabyss_ch1)。

  python tools/gen_ch1_content.py

書くもの:
  src/main/resources/data/skyabyss/lore/entry/ch1/*.json
  src/main/resources/data/skyabyss/quest/ch1/*.json
  src/main/resources/data/skyabyss/dialogue/ch1/*.json
  src/main/resources/assets/skyabyss_ch1/lang/{ja_jp,en_us}.json
仕様は docs/design/17 の 1.5・6.8、docs/review/chapter_per_titan_plan.md の 1.3〜1.4。
語りの決まり (17 の 6 章): ほのめかし、言いすぎない。巨神・見る者・ループ・天の鍵の意味は書かない。
"""
import json
import os

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, 'src', 'main', 'resources')

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


# ---------------------------------------------------------------- 伝承 (12)
# (id, 章, 順, 条件, 題 ja, 題 en, 本文 ja, 本文 en, related)
PAGE_TRACE = 'ch1_trace'
PAGE_DIARY = 'ch1_diary'
trace = {'type': 'page', 'target': PAGE_TRACE}
diary = {'type': 'page', 'target': PAGE_DIARY}
manual = {'type': 'manual'}

LORE = [
    ('trace_ring', 'prologue', 31, trace,
     '石の輪', 'The Ring of Stones',
     '石の輪の中に、炭が残っていた。先のほうだけが白く、根もとは黒い。誰かが火を消さずに、立ち去ったように。\n\n'
     '隣に、折れた頁が一枚、石で押さえてあった。「三日目からは、数えるのをやめた」とだけ読めた。\n\n何の日数だったのだろう。',
     'Charcoal remained inside the ring of stones, white at the tips and black at the root, as if someone had walked away without putting out the fire.\n\n'
     'A torn page was weighted beside it with a stone. All I could read was: "From the third day on, I stopped counting."\n\nWhat had been counted?',
     ['prologue/camp']),
    ('trace_cart', 'prologue', 32, trace,
     '倒れた荷車', 'The Fallen Cart',
     '車輪は片方だけ外れて、遠くの草の中に落ちていた。荷台の板には、爪で引いたような傷が四本。獣のものにしては、間隔が広すぎる。\n\n'
     '床板の隙間に、頁が一枚挟まっていた。「重いものを積んで逃げるな。軽いものほど、遠くへ運べる」\n\n何から、逃げていたのだろう。',
     'One wheel had come off and lay far away in the grass. Four scratches ran across the bed of the cart, as if raked by claws, spaced too wide for any beast.\n\n'
     'A page was wedged between the floorboards: "Do not flee with heavy loads. The lighter the thing, the farther it travels."\n\nWhat were they fleeing?',
     []),
    ('trace_altar', 'prologue', 33, trace,
     '小さな祭壇', 'The Small Altar',
     '石を三つ重ねただけの祭壇だった。供えられていたのは、乾いたパンの欠片と、古い鈴。鈴の舌は抜かれている。\n\n'
     '台の裏に、刻まれた文字があった。「鳴らすな。呼ぶことになる」\n\n何を、呼ぶのだろう。',
     'It was only three stones piled one upon another. On it lay a crust of dry bread and an old bell, its clapper pulled out.\n\n'
     'Letters had been cut into the back of the stand: "Do not ring it. You would be calling."\n\nCalling what?',
     []),
    ('trace_well', 'prologue', 34, trace,
     '底の青い井戸', 'The Well Too Blue',
     '井戸の水面は、空を映していなかった。雲があるのに、底のほうまで澄んだ青が見える。\n\n'
     '覗き込んでいると、縄に結ばれた頁が一枚、するすると降りてきた。「水は、上から来るものではない。下から来る」\n\n誰が、その縄を結んだのだろう。',
     'The water of the well did not reflect the sky. Though clouds were passing, a clear blue showed all the way to the bottom.\n\n'
     'As I peered in, a page tied to a rope slid down toward me: "Water does not come from above. It comes from below."\n\nWho had tied the rope?',
     []),
    ('trace_pack', 'prologue', 35, trace,
     '置き去りの荷', 'The Abandoned Pack',
     '背負い袋が、口を開けたまま置かれていた。中身は食料と、折りたたんだ地図。地図には道だけが描かれていて、地名がひとつもない。\n\n'
     '袋の底に、頁が一枚敷いてあった。「行き先に名前をつけなかった。つけると、向こうがこちらを覚えるから」\n\n誰が、覚えられるのを恐れたのだろう。',
     'A pack lay with its mouth open. Inside were provisions and a folded map. The map showed only roads; not one place had a name.\n\n'
     'A page lay flat at the bottom of the pack: "I gave my destination no name. If I did, it would remember me."\n\nWho feared being remembered?',
     []),
    ('cairn_rounds', 'prologue', 36, manual,
     '積み石の見回り', 'Rounds of the Cairns',
     '積み石は、どれも崩れていなかった。風も雨もあったはずなのに。\n\n'
     'ただ、石の積み方が少しずつ違う。右回りに重ねたもの、左回りに重ねたもの。同じ手で積んだものではない。\n\n誰が、道しるべをつなぎ直しているのだろう。',
     'Not one of the cairns had fallen, though there must have been wind and rain.\n\n'
     'But each was stacked a little differently, some wound to the right, some to the left. They were not the work of a single hand.\n\nWho keeps mending the waymarks?',
     ['prologue/camp']),
    ('lookout', 'titan_whale', 2, manual,
     '見張りの丘', 'The Lookout Hill',
     '丘の上に、石の台と真鍮の筒が据えられていた。筒を覗くと、夕暮れの雲の切れ間に、雲より暗い縁が見えた。\n\n'
     '縁は動いていた。ゆっくりと、一定の速さで。縁だけが見えているのに、縁だけで丘ひとつぶんはあった。\n\nあれを、何に喩えればよかったのだろう。',
     'On the hilltop stood a stone plinth and a brass tube. Through it, in a break in the dusk clouds, I saw an edge darker than the clouds.\n\n'
     'The edge was moving, slowly, at an even pace. Only the edge was visible, and the edge alone was as large as a hill.\n\nWhat could I have compared it to?',
     []),
    ('small_wrongs', 'titan_whale', 3, manual,
     '小さな違い', 'Small Wrongs',
     '毎日、ひとつだけ、何かが違った。鳴き声が途切れる。鐘が逆の方から聞こえる。足跡が一歩多い。\n\n'
     '数えようとして、やめた。数えるほど、増えていく気がして。\n\n違っているのは、世界のほうだろうか。それとも。',
     'Each day, one thing was different. The calls of birds broke off. A bell sounded from the wrong direction. A line of footprints had one step too many.\n\n'
     'I tried to count them, and stopped. It felt as though the more I counted, the more there would be.\n\nIs it the world that is wrong? Or is it me?',
     ['prologue/camp']),
    ('song_stones', 'titan_whale', 4, manual,
     '五つの歌石', 'Five Song Stones',
     '五つの石は、叩くと、それぞれ違う高さで鳴った。傍らの積み石の低い順に叩いていくと、遠くで鐘が一度、遅れて応えた。\n\n'
     '鐘の宿りの鐘だ。誰も鳴らしていないのに。\n\n数え歌の続きを、石が知っていたのだろうか。',
     'Each of the five stones rang at a different pitch when struck. When I struck them in the order of the small cairns beside them, from the lowest, a bell answered once, late, from far away.\n\n'
     'The bell at the lodge, though no one was ringing it.\n\nDid the stones know the rest of the counting song?',
     ['prologue/counting_song']),
    ('traveler_diary', 'titan_whale', 5, diary,
     '荷車の持ち主の日記', "The Cart Owner's Diary",
     '荷車の持ち主の日記は、途中から文字が細くなっていた。\n\n'
     '「東へ。鐘の鳴るほうへ。見上げるな、と歌う声が風に混じる。足跡を数えながら歩く。数が合わない。私の足跡は、私より一歩多い」\n\n'
     '筆跡を見て、手が止まった。縦の線を長く引く癖も、はねの角度も、どこかで見た覚えがある。\n\nこの日記を書いたのは、誰だったのだろう。',
     "The cart owner's handwriting grew thinner as the diary went on.\n\n"
     '"East, toward the bell. A voice singing "don\'t look up" mixes with the wind. I walk counting my footprints. The count is wrong. There is always one more than my own."\n\n'
     'I stopped at the handwriting. The long downstrokes, the angle of the flicks. I had seen this hand somewhere.\n\nWho wrote this diary?',
     ['prologue/the_book']),
    ('sentry', 'titan_whale', 6, manual,
     '夜を歩く骨', 'Bones That Walk at Night',
     '夜の丘のふもとを、鎧をまとった骨が歩いていた。同じ道を、巡るように。朝には、いなくなっていた。\n\n'
     '倒れた骨の胸の奥に、頁が一枚、折りたたまれていた。「持ち場を離れるな」とだけ書かれている。\n\n誰が、何を見張らせたのだろう。',
     'At night an armored skeleton walked the foot of the hill, circling the same path. By morning it was gone.\n\n'
     'Folded deep in the chest of the fallen bones was a single page. It said only: "Do not leave your post."\n\nWho set it to watch, and over what?',
     ['prologue/bone_hill']),
    ('whale_hush', 'titan_whale', 15, manual,
     '歌のやんだ日', 'The Day the Song Stopped',
     '空が静かになった。動物も、風も、あの低い歌も、同時に黙った。\n\n'
     '黙ったのは、私たちが怖がったからではない。大きなものが、自分より大きなものの下で、息を止めたのだ。\n\n影が去ると、歌は少しだけ遅れて戻ってきた。何が、あれを黙らせたのだろう。',
     'The sky fell quiet. The animals, the wind, and that low song all went silent at once.\n\n'
     'It was not that we were afraid. A great thing had held its breath beneath something greater.\n\nWhen the shadow passed, the song came back a little late. What could silence that?',
     ['chapter1/stone_whale', 'prologue/shadow']),
]

for (name, chapter, order, trig, tj, te, bj, be, related) in LORE:
    entry = {'chapter': chapter, 'order': order, 'unlock': [trig]}
    if related:
        entry['related'] = related
    write_json(os.path.join(RES, 'data', 'skyabyss', 'lore', 'entry', 'ch1', name + '.json'), entry)
    put('lore.skyabyss.ch1.%s.title' % name, tj, te)
    put('lore.skyabyss.ch1.%s.body' % name, bj, be)

put('item.skyabyss.lore_page.desc.ch1_trace', '人が去った跡に残されていた頁。誰の字かは、分からない。',
    'A page left behind where people once were. I cannot tell whose hand it is.')
put('item.skyabyss.lore_page.desc.ch1_diary', '荷車の持ち主が書いた日記の頁。', 'A page from the diary of the cart owner.')

# ---------------------------------------------------------------- クエスト (3)
QUEST_DIR = os.path.join(RES, 'data', 'skyabyss', 'quest', 'ch1')
write_json(os.path.join(QUEST_DIR, 'day1_cairns.json'), {
    'category': 'sub', 'order': 58, 'repeat': 'weekly', 'board': True, 'chapter': 'prologue',
    'stages': [{'id': 'round', 'objectives': [{'id': 'round', 'type': 'signal', 'target': 'ch1_cairn_round', 'count': 2, 'party': False}]}],
    'reward': {'xp': 20, 'items': [{'item': 'minecraft:bread', 'count': 2}], 'do': [{'type': 'lore', 'entry': 'ch1/cairn_rounds'}]},
})
write_json(os.path.join(QUEST_DIR, 'day1_firewood.json'), {
    'category': 'sub', 'order': 59, 'repeat': 'weekly', 'board': True, 'chapter': 'prologue',
    'stages': [{'id': 'wood', 'objectives': [{'id': 'wood', 'type': 'turn_in', 'target': '#minecraft:logs', 'count': 8}]}],
    'reward': {'xp': 20, 'items': [{'item': 'minecraft:torch', 'count': 4}]},
})
write_json(os.path.join(QUEST_DIR, 'lost_traveller.json'), {
    'category': 'sub', 'order': 41, 'giver': 'skyabyss:storyteller', 'chapter': 'titan_whale',
    'stages': [
        {'id': 'follow', 'xp': 15, 'objectives': [{'id': 'diary', 'type': 'lore', 'targets': ['ch1/traveler_diary']}]},
        {'id': 'tell', 'objectives': [{'id': 'talk', 'type': 'talk', 'target': 'skyabyss:storyteller', 'topic': 'ch1_diary_tell'}]},
    ],
    'reward': {'xp': 25, 'items': [], 'affinity': {'skyabyss:storyteller': 3}},
})

put('quest.skyabyss.ch1.day1_cairns.title', '積み石の見回り', 'Rounds of the Cairns')
put('quest.skyabyss.ch1.day1_cairns.summary', '道や旅の跡の積み石を、二か所見て回る。', 'Look over two of the cairns along the road and at the traces of travelers.')
put('quest.skyabyss.ch1.day1_cairns.round.journal',
    '語り部の頼み：「道に積んである石が崩れていないか、見てきておくれ。二つも見れば足りるよ」\n積み石は、道の脇や、旅の跡のそばにある。',
    'The storyteller asked: "Go and see whether the stones piled along the road have fallen. Two will be enough."\nThe cairns stand beside the road and near the traces of travelers.')
put('quest.skyabyss.ch1.day1_cairns.round.round', '積み石を見て回る', 'Look over the cairns')
put('quest.skyabyss.ch1.day1_cairns.done', '「崩れていなかったかい。……それなら、いいんだよ」', '"Not a one had fallen? ...Then that is all right."')

put('quest.skyabyss.ch1.day1_firewood.title', '宿りの薪', 'Firewood for the Lodge')
put('quest.skyabyss.ch1.day1_firewood.summary', '原木を八本、言伝ての板に納める。', 'Hand eight logs in at the message board.')
put('quest.skyabyss.ch1.day1_firewood.wood.journal',
    '語り部の頼み：「夜に備えて、薪を束ねておくれ。原木が八本あれば足りる」\n原木を持って板を開けば、受け取ってもらえる。',
    'The storyteller asked: "Bundle some firewood against the night. Eight logs will do."\nOpen the board with logs in hand and they will be taken.')
put('quest.skyabyss.ch1.day1_firewood.wood.wood', '原木を言伝ての板に納める', 'Hand logs in at the message board')
put('quest.skyabyss.ch1.day1_firewood.done', '「ありがとうね。これで夜の火は、つなげられる」', '"Thank you. Now the fire can last through the night."')

put('quest.skyabyss.ch1.lost_traveller.title', '荷車の持ち主', 'The Cart Owner')
put('quest.skyabyss.ch1.lost_traveller.summary', '倒れた荷車の持ち主を探し、日記を見つける。', 'Look for the owner of the fallen cart and find the diary.')
put('quest.skyabyss.ch1.lost_traveller.follow.journal',
    '倒れた荷車の下から、足跡のくぼみが一歩ずつ続いている。どこかへ向かって、まっすぐに。\nくぼみをたどって、持ち主を探そう。',
    'Hollows of footprints lead away from beneath the fallen cart, one step at a time, straight toward somewhere.\nFollow the hollows and look for the owner.')
put('quest.skyabyss.ch1.lost_traveller.follow.diary', '荷車の持ち主の日記を見つける', "Find the cart owner's diary")
put('quest.skyabyss.ch1.lost_traveller.tell.journal', '日記を読んだ。語り部に、見せに行こう。', 'I read the diary. I should show it to the storyteller.')
put('quest.skyabyss.ch1.lost_traveller.tell.talk', '語り部に日記のことを話す', 'Tell the storyteller about the diary')
put('quest.skyabyss.ch1.lost_traveller.done', '持ち主は、ここへ着かなかった。それでも、字は残っていた。', 'The owner never arrived here. But the handwriting remained.')

# ---------------------------------------------------------------- 会話 (足し書き)
DIALOGUE_DIR = os.path.join(RES, 'data', 'skyabyss', 'dialogue', 'ch1')


def cond_not_flag(flag):
    return {'type': 'not', 'of': {'type': 'flag', 'flag': flag}}


# 語り部
ST = 'dialogue.skyabyss.storyteller.'
write_json(os.path.join(DIALOGUE_DIR, 'storyteller_ch1.json'), {
    'npc': 'skyabyss:storyteller',
    'extends': True,
    # メニューの選択肢は増やさない (会話の枠の高さが上限に近いため)。お使いの頼みは、初めて話す日の「今日あたり、空を見ていなさい」の返事の選択肢に足す
    'entries': [
        {'node': 'ch1_diary_tell', 'if': {'type': 'quest', 'quest': 'ch1/lost_traveller', 'is': 'active', 'stage': 'tell'}},
    ],
    'add_options': {
        'shadow0_b': {
            'options': [
                {'id': 'ch1_help', 'goto': 'ch1_errand_offer', 'kind': 'quest'},
            ],
        },
    },
    'nodes': {
        'ch1_errand_offer': {
            'face': 'smile',
            'options': [
                {'id': 'yes', 'goto': 'ch1_errand_ok', 'kind': 'quest',
                 'do': [{'type': 'quest_start', 'quest': 'ch1/day1_cairns'}, {'type': 'quest_start', 'quest': 'ch1/day1_firewood'}]},
                {'id': 'later', 'goto': 'menu'},
            ],
        },
        'ch1_errand_ok': {'face': 'neutral', 'next': 'menu'},
        'ch1_diary_tell': {'face': 'grave', 'options': [{'id': 'ok', 'goto': 'end', 'do': [{'type': 'affinity', 'amount': 1}]}]},
    },
})
put(ST + 'shadow0_b.ch1_help', '何か、手伝おうか？', 'Is there something I can do?')
put(ST + 'ch1_errand_offer',
    '手伝ってくれるのかい。助かるよ。\n道の積み石を二つ、見てきておくれ。崩れていないかどうか。\nそれと、夜に備えて薪をね。原木が八本あれば足りる。',
    "You'll lend a hand? That's a help.\nGo and look at two of the stones piled along the road, to see whether they've fallen.\nAnd some firewood against the night. Eight logs will do.")
put(ST + 'ch1_errand_offer.yes', '引き受ける。', "I'll do it.")
put(ST + 'ch1_errand_offer.later', 'また今度。', 'Another time.')
put(ST + 'ch1_errand_ok',
    'ありがとうね。薪は、宿りの外の板に出しておくれ。\n……それから、積み石の数は、数えすぎないようにね。',
    "Thank you. Put the firewood out at the board by the lodge.\n...And try not to count the stones too carefully.")
put(ST + 'ch1_diary_tell',
    '……その日記、見せておくれ。\n荷車の主は、ここへ着かなかったんだね。字が、……いや、何でもないよ。よく似た字を見たことがあるだけさ。\n探してくれて、ありがとう。',
    "...Let me see that diary.\nSo the cart's owner never made it here. The handwriting is... no, never mind. I've simply seen a hand like it before.\nThank you for looking.")
put(ST + 'ch1_diary_tell.ok', '……ああ。', '...Yes.')

# イサナ
WC = 'dialogue.skyabyss.whale_chaser.'
write_json(os.path.join(DIALOGUE_DIR, 'whale_chaser_ch1.json'), {
    'npc': 'skyabyss:whale_chaser',
    'extends': True,
    # メニューの選択肢は増やさない (枠の高さ)。会うたびに先に一度ずつ、イサナのほうから語る
    'entries': [
        {'node': 'ch1_c_why1', 'if': [{'type': 'lore', 'entry': 'chapter1/whale_chaser'}, {'type': 'day', 'min': 2}, cond_not_flag('ch1_why_told')]},
        {'node': 'ch1_c_diary', 'if': [{'type': 'lore', 'entry': 'ch1/traveler_diary'}, cond_not_flag('ch1_diary_told')]},
        {'node': 'ch1_c_hush', 'if': [{'type': 'lore', 'entry': 'ch1/whale_hush'}, {'type': 'day', 'min': 3}, cond_not_flag('ch1_hush_told')]},
        {'node': 'ch1_c_after', 'if': [{'type': 'stage', 'min': 1}, {'type': 'day', 'min': 3}, cond_not_flag('ch1_after_told')]},
    ],
    'nodes': {
        'ch1_c_why1': {'face': 'neutral', 'next': 'ch1_c_why2'},
        'ch1_c_why2': {'face': 'grave', 'options': [{'id': 'then', 'goto': 'ch1_c_why3'}]},
        'ch1_c_why3': {'face': 'grave', 'do': [{'type': 'flag', 'flag': 'ch1_why_told'}, {'type': 'affinity', 'amount': 2}], 'next': 'c_menu'},
        'ch1_c_diary': {'face': 'neutral', 'do': [{'type': 'flag', 'flag': 'ch1_diary_told'}], 'next': 'c_menu'},
        'ch1_c_hush': {'face': 'grave', 'next': 'ch1_c_hush2'},
        'ch1_c_hush2': {'face': 'grave', 'do': [{'type': 'flag', 'flag': 'ch1_hush_told'}, {'type': 'affinity', 'amount': 2}], 'next': 'c_menu'},
        'ch1_c_after': {'face': 'smile', 'next': 'ch1_c_after2'},
        'ch1_c_after2': {'face': 'grave', 'do': [{'type': 'flag', 'flag': 'ch1_after_told'}, {'type': 'affinity', 'amount': 3}], 'next': 'c_menu'},
    },
})
put(WC + 'ch1_c_why1', '追う理由、か。……話すほどのことじゃない。\nただ、俺は海の人間だった。それだけだ。',
    "My reason, is it. ...Nothing worth telling.\nI was a man of the sea, once. That's all.")
put(WC + 'ch1_c_why2', '船があった。空じゃない、海の船だ。\nある晩、甲板で、あの歌を聞いた。船長は何も言わず、舵を切った。歌のほうへ。',
    "There was a ship. Not a ship of the sky, a ship of the sea.\nOne night on deck I heard that song. The captain said nothing and turned the wheel toward it.")
put(WC + 'ch1_c_why2.then', 'それで、どうなった？', 'And then?')
put(WC + 'ch1_c_why3',
    '気づいたら、浜に打ち上げられていた。俺ひとりだ。\n船長の日誌は、船と一緒に、あの背へ流れ着いたと聞いた。\n……あんたがあの背に立ったら、探してみてくれ。',
    "When I came to, I'd been washed up on a beach. Only me.\nThey say the captain's log drifted onto that back along with the ship.\n...If you ever stand on it, look for it for me.")
put(WC + 'ch1_c_diary',
    '荷車の持ち主の日記か。見せてみろ。\n……読めない。いや、読めるが、読みたくない。字の癖が、見覚えのあるやつに似ている。\n誰とは、言わないでおくよ。',
    "The cart owner's diary. Let me see.\n...I can't read it. No, I can, but I don't want to. The hand looks like one I know.\nI'll not say whose.")
put(WC + 'ch1_c_hush', '歌がやんだ、か。……長いこと追ってきたが、鯨が黙ったことは一度もない。\nあの影の下では、鯨でさえ息を止めるんだな。',
    "The song stopped, did it. ...I've chased it a long time and never once heard it go silent.\nBeneath that shadow, even the whale holds its breath.")
put(WC + 'ch1_c_hush2',
    'だとしたら、俺が追ってきたものは、いちばん上じゃなかったってことになる。\n……鎖が降りたら、迷わずつかめ。俺のぶんまで、とは言わない。',
    "Then what I've been chasing wasn't the highest thing after all.\n...When the chain comes down, grab it without hesitating. I won't ask you to do it for me.")
put(WC + 'ch1_c_after', '止めたのか、あの心臓を。……歌が、一節だけ欠けて鳴ったろう。俺にも聞こえた。\n鯨は空へ戻った。あんたは、地の底から預かったものを、確かに受け取ったんだ。',
    "You stopped that heart. ...The song rang with one verse missing, didn't it. I heard it too.\nThe whale has gone back to the sky. You truly took in what was kept for you from the deep earth.")
put(WC + 'ch1_c_after2', '海の匂いがしないか。夜風に、塩が混じっている。\n……次は、海だろうな。俺は先に、浜へ行っておく。',
    "Don't you smell the sea? There's salt in the night wind.\n...The sea is next, I expect. I'll go ahead to the shore.")

# ---------------------------------------------------------------- 小さな知らせ (message.skyabyss.ch1.*)
M = 'message.skyabyss.ch1.'
# 跡に着いたとき (人ごとに一度)
put(M + 'arrive.ch1_trace_camp', '石の輪の中に、炭が残っている。先のほうだけが白い。', 'Charcoal remains in the ring of stones, white only at the tips.')
put(M + 'arrive.ch1_trace_cart', '倒れた荷車。板に、爪で引いたような傷が四本。', 'A fallen cart. Four scratches rake the boards, as if by claws.')
put(M + 'arrive.ch1_trace_altar', '石を三つ重ねた祭壇。供え物は、乾いたパンと、舌のない鈴。', 'A small altar of three stacked stones. An offering of dry bread and a bell with no clapper.')
put(M + 'arrive.ch1_trace_well', '井戸の底が、空より青い。', 'The bottom of the well is bluer than the sky.')
put(M + 'arrive.ch1_trace_pack', '口の開いた背負い袋。中の地図には、地名がひとつもない。', 'A pack with its mouth open. The map inside has not one place name.')
put(M + 'arrive.ch1_lost_cart', '荷車の下から、足跡のくぼみが一歩ずつ続いている。', 'Hollows of footprints lead away from beneath the cart, one step at a time.')
put(M + 'arrive.ch1_diary_spot', '石の腰掛けと、冷えた焚き火。誰かがここで、書いていた。', 'A stone seat and a cold fire. Someone sat here and wrote.')
put(M + 'arrive.ch1_lookout', '高台に、石の台と真鍮の筒が据えてある。', 'On the high ground stand a stone plinth and a brass tube.')
for n in range(1, 6):
    put(M + 'arrive.ch1_song_stone_%d' % n, '苔むした石と、傍らの積み石。叩けば、鳴りそうだ。', 'A mossy stone with a small cairn beside it. It looks as if it would ring if struck.')
# 小さな異変
put(M + 'oddity.quiet', '……鳴き声が、ひとつも聞こえない。鳥も、虫も。', '...Not a single call can be heard. No birds, no insects.')
put(M + 'oddity.bell', '鐘が鳴った。宿りとは、逆の方角から。', 'A bell sounded, from the opposite direction to the lodge.')
put(M + 'oddity.step', '足跡が、一歩ぶん、多い。', 'There is one footprint too many.')
put(M + 'oddity.cold', '息が白い。日の高い昼なのに、ほんの一呼吸だけ。', 'My breath shows white. At midday, for the space of a single breath.')
# 見張りの丘
put(M + 'lookout.edge', '筒を覗く。夕暮れの雲の合間に、雲より暗い縁が見えた。\n縁は%sの方角で、ゆっくりと動いている。',
    'I look through the tube. Between the dusk clouds, an edge darker than the clouds.\nIt moves slowly, toward the %s.')
put(M + 'lookout.size', '比べるものが、ひとつもない。それなのに、あまりに大きい。', 'There is nothing to compare it to. And still, it is far too large.')
put(M + 'lookout.wait', '雲が流れていくだけだ。夕方に、もう一度覗いてみよう。', 'Only clouds drift by. I should look again at dusk.')
put(M + 'lookout.seen', '縁はもう見えない。雲の向こうへ行ってしまった。', 'The edge is gone, beyond the clouds.')
# 歌石
put(M + 'stone.n1', 'ひとつ。石が、低く鳴った。', 'One. The stone rang low.')
put(M + 'stone.n2', 'ふたつ。石が、少し高く鳴った。', 'Two. The stone rang a little higher.')
put(M + 'stone.n3', 'みっつ。石が、澄んで鳴った。', 'Three. The stone rang clear.')
put(M + 'stone.n4', 'よっつ。石が、細く鳴った。', 'Four. The stone rang thin.')
put(M + 'stone.n5', 'いつつ――。', 'Five--.')
put(M + 'stone.wrong', '石は、鳴りかけて、黙った。', 'The stone began to ring, and fell silent.')
put(M + 'stone.answer', '遠くで、鐘が一度、応えた。', 'Far away, a bell answered once.')
put(M + 'stone.done', '石は、もう鳴らない。……鐘が、まだ耳に残っている。', 'The stone no longer rings. ...The bell still lingers in my ears.')
# 歩哨
put(M + 'sentry.page', '倒れた骨の胸の奥で、紙の擦れる音がした。', 'From deep inside the fallen bones came the sound of paper rubbing.')
# 岩鯨の「歌をやめる」出来事
put(M + 'hush.start', '歌が、ぴたりとやんだ。風も、鳥も、息を止めている。', 'The song stopped dead. The wind and the birds hold their breath.')
put(M + 'hush.mid', '雲の上を、何かが通っている。岩鯨が、それを知っている。', 'Something is passing above the clouds. The whale knows it.')
put(M + 'hush.end', '影が去った。鎖の鳴る音がして、岩鯨が、また動きだした。', 'The shadow has gone. A chain clinks, and the whale begins to move again.')
# 予兆 3 拍
put(M + 'beat.dust', '晴れているのに、乾いた石の粉が、空からうっすらと降ってくる。古いにおいがした。',
    'Though the sky is clear, a fine dry stone dust sifts down from above. It smells very old.')
put(M + 'beat.shadow', '雲ひとつない昼の空の下で、地面の上だけを、大きな影がゆっくり渡っていった。',
    'Under a cloudless noon sky, a vast shadow crossed the ground, slowly, and only the ground.')
put(M + 'beat.chain', '遠く高い所で、鎖が鳴った。しばらくして、足もとに、石の環がひとつ落ちてきた。手のひらより大きく、冷たい。',
    'Far above, a chain rang. After a while a ring of stone dropped at my feet, larger than my palm, and cold.')
put('item.skyabyss.ch1.whale_link', '岩の環', 'Stone Link')
put('item.skyabyss.ch1.whale_link.lore', '空から落ちてきた、鎖の一節。まだ、ほのかに震えている。', 'A link of chain fallen from the sky. It still trembles faintly.')
# 章の余韻 (一章 終)
put('chapter.skyabyss.aftermath.titan_whale', 'この鼓動は、地の底から預かったもの。', 'This heartbeat was kept in trust, from the depths of the earth.')
put(M + 'aftermath.storyteller', '……歌が、一節だけ欠けて鳴ったね。預かったものは、いつか返さなきゃならない。誰にかは、聞かないでおくれ。',
    '...The song rang with a verse missing, didn\'t it. What is kept in trust must one day be returned. Don\'t ask me to whom.')
# 宿りの炉端
put(M + 'nook.bed', '宿りに、ベッドが置かれている。', 'A bed has been set out at the lodge.')

# ブロック
put('block.skyabyss.song_stone', '歌石', 'Song Stone')

# ---------------------------------------------------------------- 言語ファイル
for code, table in (('ja_jp', ja), ('en_us', en)):
    write_json(os.path.join(RES, 'assets', 'skyabyss_ch1', 'lang', code + '.json'), dict(sorted(table.items())))

print('wrote %d lang keys, %d lore entries' % (len(ja), len(LORE)))
