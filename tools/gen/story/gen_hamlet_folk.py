# -*- coding: utf-8 -*-
# 外れの集落 (world/continent/FrontierHamlets) の住人と頼み。集落ごとに二人 (印 folk_a・folk_b)、一人に頼みが一つ。
# 頼みは集落のまわりの名のある場所 (poi:@<名>) へ行かせ、その土地の敵を払わせる。褒美は鐘銭と経験とレアの封の箱 (土地の帯)。
# 頼みを済ませたあとの台詞 (hint) は、もっと奥 (霜の小島・東の岬・沈んだ社・溶岩の筒) をほのめかす。
# あわせて霜の小島の渡し守 (isle_ferryman。ContinentFerries の二つめの渡し) の名と会話も書く。
#
#   python -X utf8 -I tools/gen/story/gen_hamlet_folk.py
#
# 書くもの: data/skyabyss/quest/town/hamlet_<人>.json・data/skyabyss/dialogue/guest_<人>.json・assets/skyabyss_town/lang の名・会話・頼みの言葉
import json
import os

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__)))))
RES = os.path.join(ROOT, 'src', 'main', 'resources')

# 土地ごと: 章・受けられる段・巨神・経験・鐘銭・箱
LAND = {
    'whale': dict(chapter='titan_whale', min=0, titan='stone_whale', xp=150, coins=40, box='skyabyss:box_rare_1'),
    'tide': dict(chapter='titan_tide', min=1, titan='tide_shell', xp=220, coins=50, box='skyabyss:box_rare_2'),
    'storm': dict(chapter='titan_storm', min=2, titan='thunder_wing', xp=300, coins=60, box='skyabyss:box_rare_3'),
    'ember': dict(chapter='titan_ember', min=3, titan='ember', xp=380, coins=70, box='skyabyss:box_rare_3'),
}


def kill(targets, count, obj, journal):
    return dict(kind='kill', targets=targets, count=count, obj=obj, journal=journal)


def go(places, journal):
    # places: [(名, (ja, en))]
    return dict(kind='go', places=places, journal=journal)


# 人: id → 中身。hamlet = 集落の key、mark = folk_a / folk_b
FOLK = {}


def person(pid, hamlet, mark, land, order, name, stages, t):
    FOLK[pid] = dict(hamlet=hamlet, mark=mark, land=land, order=order, name=name, stages=stages, t=t)


# ================================================================ 骨垣の砦村 (岩鯨の北東)
person('bonefort_beacon', 'w_ne', 'folk_a', 'whale', 320, ('狼煙番のトウジ', 'Toji the Beacon Keeper'), [
    kill(['skyabyss:bone_gull'], 4, ('骨鳥を払おう（砦のまわりの丘）', 'Drive off bone gulls (hills around the fort)'),
         ('狼煙台の骨組みに、骨鳥がたかっている。骨の髄をつつくらしい。砦のまわりの丘で四羽払おう。',
          'Bone gulls flock to the beacon frame and peck at the marrow. Drive off four in the hills around the fort.')),
    go([('north_ribs', ('北の肋を見てこよう（砦から西北西）', 'Look at the North Ribs (west-northwest of the fort)'))],
       ('北の肋は、砦から西北西にある。空から落ちた骨だという。砦の骨垣と同じ骨か、見てこよう。',
        "West-northwest of the fort: the North Ribs, bones said to have fallen from the sky. See whether they match the fort's bone wall.")),
], dict(
    title=('北の肋の骨', 'The Bones of the North Ribs'),
    summary=('狼煙番のトウジに頼まれて、狼煙台をつつく骨鳥を払い、北の肋の骨を見てくる。',
             'For Toji the beacon keeper, drive off the bone gulls pecking at the beacon, then look at the bones of the North Ribs.'),
    done=('北の肋の碑には「岩鯨の骨ではない。もっと古い」とあった。トウジは、砦の骨垣を見上げて黙った。',
          'The marker at the North Ribs read "Not the stone whale\'s bones. Older." Toji looked up at the bone wall of the fort and said nothing.'),
    back_journal=('碑には「岩鯨の骨ではない」とあった。トウジに伝えよう。', 'The marker said "Not the stone whale\'s bones." Tell Toji.'),
    back_obj=('狼煙番のトウジに伝えよう', 'Tell Toji the beacon keeper'),
    ask=('骨鳥のやつら、狼煙台の骨組みをつついて髄をすすりよる。四羽ばかり払ってくれ。\n'
         'それと、西北西の北の肋を見てきてほしい。この砦の垣は、あそこの骨を運んで組んだと聞いとる。……どうも、岩鯨の骨とは鳴りが違うんでな。',
         'The bone gulls peck at the beacon frame and suck out the marrow. Drive off four or so.\n'
         "And go and look at the North Ribs to the west-northwest. They say this fort's wall was built from bones hauled from there. ...They don't ring like the stone whale's."),
    ask_ok=('引き受ける', "I'll do it"),
    wait=('骨鳥は砦のまわりの丘だ。北の肋は西北西。碑があるはずだ。', 'The gulls are in the hills around the fort. The North Ribs are west-northwest. There should be a marker.'),
    back=('見てきたか。何と書いてあった。', 'Did you see it? What did it say?'),
    back_give=('「岩鯨の骨ではない。もっと古い」と伝える', 'Tell him "Not the stone whale\'s bones. Older."'),
    done_line=('もっと古い、か。……巨神より前に、空から落ちたものがおったんだな。\nわしらは、それで垣を組み、その中で寝とる。……礼だ。持っていけ。',
               'Older, eh. ...So something fell from the sky before the titans did.\nAnd we built our wall from it and sleep inside. ...Here. Take this for your trouble.'),
    idle=('狼煙台は都の方へ向けて組んである。晴れた晩は三つ焚いて「無事」を送る。\n……東の岬の先からも、ときどき灯が見える。誰が焚いとるのかは知らん。',
          'The beacon faces the capital. On clear nights I light three fires to send "all is well".\n...Sometimes a light shows past the eastern cape too. Who lights it, I couldn\'t say.'),
    hint=('東の岬の先は、狼煙が届かん。……あっちの灯は、こっちが焚くと消える。わしが若いころから、ずっとだ。',
          "The beacon doesn't reach past the eastern cape. ...And the light out there goes dark whenever we light ours. Has done since I was young."),
    after=('岩鯨が眠ってから、骨垣が鳴らなくなった。風が抜けても、しんとしとる。\n……古い骨も、一緒に眠ったのかもしれん。',
           'Since the stone whale went to sleep, the bone wall has stopped ringing. Even when the wind goes through, it is silent.\n...Maybe the old bones went to sleep with it.'),
))

person('bonefort_scribe', 'w_ne', 'folk_b', 'whale', 321, ('写字生のセツ', 'Setsu the Copyist'), [
    go([('scriptorium', ('写本の僧院を見てこよう（砦から西北西）', 'Visit the Scriptorium (west-northwest of the fort)')),
        ('hushed_abbey', ('祈り止めの僧院を見てこよう（砦から西南西）', 'Visit the Hushed Abbey (west-southwest of the fort)'))],
       ('砦から西北西にある写本の僧院と、西南西にある祈り止めの僧院。セツが写しかけた巨神の名の帳面が、どちらかに残っているかもしれない。',
        'The Scriptorium west-northwest of the fort, and the Hushed Abbey west-southwest. The book of titan names Setsu was copying may still be in one of them.')),
], dict(
    title=('白いページ', 'The Blank Page'),
    summary=('写字生のセツに頼まれて、写本の僧院と祈り止めの僧院を見てくる。', 'For Setsu the copyist, visit the Scriptorium and the Hushed Abbey.'),
    done=('写本の僧院の帳面の最後のページは、白いままだった。祈り止めの僧院には「鐘を鳴らすな」とあった。セツは、何も書かずに筆を置いた。',
          'The last page of the book at the Scriptorium was still blank. The Hushed Abbey said "Do not ring the bell." Setsu set down her brush without writing a word.'),
    back_journal=('帳面の最後のページは白いままだった。セツに伝えよう。', 'The last page was still blank. Tell Setsu.'),
    back_obj=('写字生のセツに伝えよう', 'Tell Setsu the copyist'),
    ask=('わたし、写本の僧院で巨神の名を写していたの。でも最後の一つが、どうしても書けなかった。書こうとすると、筆が止まるの。\n'
         '僧院には、もう戻れない。写本の僧院は西北西、祈り止めの僧院は西南西にある。帳面がどうなったか、見てきてくれる？',
         'I used to copy the names of the titans at the Scriptorium. But I could never write the last one. Every time I tried, my brush stopped.\n'
         "I can't go back there now. Would you look at the Scriptorium to the west-northwest and the Hushed Abbey to the west-southwest, and see what became of the book?"),
    ask_ok=('見てくる', "I'll go and look"),
    wait=('写本の僧院は砦から西北西、祈り止めの僧院は西南西。……鐘があっても、鳴らさないでね。',
          "The Scriptorium is west-northwest of the fort, the Hushed Abbey west-southwest. ...If there's a bell, don't ring it."),
    back=('どうだった？　最後のページは……。', 'Well? The last page...?'),
    back_give=('白いままだった、と伝える', 'Tell her it was still blank'),
    done_line=('白いまま。……そう。誰にも書けなかったのね。わたしだけじゃなかった。\nこれ、僧院から持ち出した箱。開けられなくて、ずっと持っていたの。あなたが持っていて。',
               "Still blank. ...I see. No one could write it. It wasn't only me.\nThis is a box I took from the abbey. I never could open it. You keep it."),
    idle=('巨神の名は、写すたびに一字ずつ変わるの。だから僧院では、毎朝写し直していた。\n……変わらなくなったら、その巨神は眠ったってこと。',
          "The titans' names change by one letter every time you copy them. So at the abbey we copied them fresh every morning.\n...When a name stops changing, that titan has gone to sleep."),
    hint=('最後の名前はね、書こうとするとページが冷たくなるの。霜みたいに。\n……北の、もっと寒いところの名前なんだと思う。',
          "The last name... when you try to write it, the page turns cold. Like frost.\n...I think it is the name of something in the north, somewhere colder."),
    after=('岩鯨の名前が、今朝は変わらなかった。……眠ったのね。\n書き写すのが、少しだけ寂しい。',
           "This morning the stone whale's name didn't change. ...It's asleep.\nCopying it feels a little lonely now."),
))

# ================================================================ 鐘見の里 (岩鯨の北西)
person('bellwatch_crone', 'w_nw', 'folk_a', 'whale', 322, ('鐘聞きのオトメ', 'Otome the Bell-Listener'), [
    go([('nine_stones', ('九つ石を見てこよう（里から南南東）', 'Visit the Nine Stones (south-southeast of the hamlet)')),
        ('backwarden_graves', ('背守りの墓を見てこよう（里から東）', 'Visit the Backwarden Graves (east of the hamlet)'))],
       ('九つ石は里から南南東、背守りの墓は里から東にある。オトメの夫は岩鯨の背に登ったきり戻らなかった。墓に名があるか、石が何を言うか、見てこよう。',
        "The Nine Stones south-southeast of the hamlet and the Backwarden Graves to the east. Otome's husband climbed the stone whale's back and never came down. See whether his name is on a grave, and what the stones say.")),
], dict(
    title=('九つ石と背守りの墓', 'The Nine Stones and the Backwarden Graves'),
    summary=('鐘聞きのオトメに頼まれて、九つ石と背守りの墓を見てくる。', 'For Otome the bell-listener, visit the Nine Stones and the Backwarden Graves.'),
    done=('九つ石には「巨神は四つ。残る五つは、まだ眠っている」とあった。背守りの墓に、オトメの夫の名はなかった。オトメは黙って、鐘の音に耳を澄ませた。',
          'The Nine Stones read "Titans: four. The other five still sleep." Her husband\'s name was not at the Backwarden Graves. Otome fell silent and listened for the bell.'),
    back_journal=('石は「残る五つは、まだ眠っている」と言い、墓に夫の名はなかった。オトメに伝えよう。',
                  'The stones said "the other five still sleep", and his name was on no grave. Tell Otome.'),
    back_obj=('鐘聞きのオトメに伝えよう', 'Tell Otome the bell-listener'),
    ask=('うちの人はね、岩鯨の背に登ったきり、降りてこなかった。東の背守りの墓に、名があるかどうか見てきておくれ。\n'
         'それから南南東の九つ石。あれは石の数と巨神の数が合わないの。……何て書いてあるか、教えておくれ。',
         'My husband climbed the stone whale\'s back and never came down. Go and see if his name is at the Backwarden Graves to the east.\n'
         "And the Nine Stones to the south-southeast. The number of stones doesn't match the number of titans. ...Tell me what's written there."),
    ask_ok=('見てくる', "I'll go and look"),
    wait=('墓は里から東、九つ石は南南東だよ。……鐘が聞こえたら、立ち止まって数えておくれ。',
          'The graves are east of the hamlet, the stones south-southeast. ...If you hear the bell, stop and count.'),
    back=('どうだったね。', 'Well, dear?'),
    back_give=('名はなく、石は「残る五つは、まだ眠っている」と言っていた、と伝える', 'Tell her his name was not there, and the stones said "the other five still sleep"'),
    done_line=('名はなかった。……なら、まだ降りてくる途中なのかもしれないね。\n残る五つ、か。わたしが生きてるうちに、全部は起きないでおくれよ。……ほら、これ。うちの人の箱だよ。',
               "Not there. ...Then maybe he's still on his way down.\nThe other five, is it. Let them not all wake while I'm alive. ...Here. This was his box."),
    idle=('この里はね、都の大鐘がいちばん遠くまで届く所なんだよ。南東から風が吹く日だけ、ほんの小さく聞こえる。\n聞こえた日は、みんなで数えるの。何回鳴ったか。',
          "This hamlet is the farthest the capital's great bell can reach. Only on days the wind blows from the southeast, you hear it, ever so faint.\nOn those days we all count how many times it rings."),
    hint=('九つ石の残りの五つはね、海の向こうと、空の上と、地の下にあるって言う人もいる。\n……北西の海で、霜の降りる島を見たって漁師がいたよ。あれも、石の一つかね。',
          'Some say the other five of the Nine Stones are across the sea, up in the sky, and under the ground.\n...A fisherman once said he saw an island in the north-west sea where frost comes down. Maybe that is one of the stones.'),
    after=('岩鯨が眠った晩、鐘が九つ鳴ったよ。……いつもは八つなのに。\n誰が九つめを打ったんだろうね。',
           "The night the stone whale went to sleep, the bell rang nine times. ...It's always eight.\nWho do you suppose struck the ninth?"),
))

person('bellwatch_miner', 'w_nw', 'folk_b', 'whale', 323, ('青石掘りのイワオ', 'Iwao the Bluestone Miner'), [
    kill(['skyabyss:rock_shell_beetle'], 4, ('岩殻虫を払おう（里のまわり）', 'Drive off rock shell beetles (around the hamlet)'),
         ('里のまわりに、岩殻虫が出る。坑道の口に殻を積んで塞ぐらしい。四匹払おう。',
          "Rock shell beetles roam around the hamlet, piling their shells over the mine's mouth. Drive off four.")),
    go([('bluestone_mine', ('青石の坑道を見てこよう（里から東南東）', 'Look at the Bluestone Mine (east-southeast of the hamlet)'))],
       ('青石の坑道は、里から東南東にある。青い石は岩鯨の涙だという。壁がまだ濡れているか、見てこよう。',
        "The Bluestone Mine east-southeast of the hamlet. They say the blue stone is the stone whale's tears. See whether the walls are still wet.")),
], dict(
    title=('青石の坑道', 'The Bluestone Mine'),
    summary=('青石掘りのイワオに頼まれて、岩殻虫を払い、青石の坑道を見てくる。', 'For Iwao the bluestone miner, drive off the rock shell beetles and look at the Bluestone Mine.'),
    done=('青石の坑道の壁は、まだ濡れていた。イワオは「涙が乾かんうちは掘らん」と言った。',
          'The walls of the Bluestone Mine were still wet. Iwao said, "I won\'t dig while the tears are wet."'),
    back_journal=('壁はまだ濡れていた。イワオに伝えよう。', 'The walls were still wet. Tell Iwao.'),
    back_obj=('青石掘りのイワオに伝えよう', 'Tell Iwao the bluestone miner'),
    ask=('岩殻虫が坑道の口に殻を積んで、入れんようになった。四匹ばかり払ってくれ。\n'
         'それから、東南東の坑道の壁が濡れとるか見てきてほしい。青石はな、岩鯨の涙だ。濡れとるうちに掘ると、ろくなことがない。',
         "The rock shell beetles have piled their shells over the mine's mouth. Can't get in. Drive off four or so.\n"
         "Then go and see if the walls of the mine to the east-southeast are wet. Bluestone is the stone whale's tears. Dig while they're wet and nothing good comes of it."),
    ask_ok=('引き受ける', "I'll do it"),
    wait=('岩殻虫は里のまわりだ。坑道は東南東。壁に手を当ててみろ。', 'The beetles are around the hamlet. The mine is east-southeast. Put a hand to the wall.'),
    back=('どうだった。濡れとったか。', 'Well? Was it wet?'),
    back_give=('まだ濡れていた、と伝える', 'Tell him it was still wet'),
    done_line=('まだ濡れとるか。……なら、今年も掘らん。岩鯨が泣きやむまで待つ。\n掘れんかわりに、これをやる。持っていけ。',
               "Still wet. ...Then no digging this year either. I'll wait till the whale stops crying.\nSince I can't dig, take this instead."),
    idle=('青石はな、磨くと中で何かが泳いどるように見える。都の連中は宝石だと言って高く買うが……。\nわしは、あれは閉じ込められとるんだと思う。',
          "Polish bluestone and something seems to swim inside. The capital folk pay well and call it a jewel...\nI think it's trapped."),
    hint=('坑道の一番奥はな、北西へ向けて掘ってある。爺さまの代に、そっちから冷たい風が吹いてきて、掘るのをやめた。\n……海の下を通って、どこかへ抜けとるんだろう。',
          "The deepest shaft runs north-west. In my grandfather's day a cold wind came up from it and they stopped digging.\n...It must run under the sea to somewhere."),
    after=('坑道の壁が、乾き始めた。……岩鯨が泣きやんだ。\n来年は掘れる。……そう思うと、なんだか惜しい気もする。',
           "The mine walls are starting to dry. ...The whale's stopped crying.\nNext year I can dig. ...Somehow that feels like a loss."),
))

# ================================================================ 灯守の里 (潮殻の東)
person('lightvillage_apprentice', 't_e', 'folk_a', 'tide', 324, ('灯守見習いのミナト', 'Minato the Lightkeeper\'s Apprentice'), [
    kill(['skyabyss:sand_burrower'], 4, ('砂潜りを払おう（里のまわりの草原）', 'Drive off sand burrowers (grassland around the hamlet)'),
         ('里と岬のあいだの草原に、砂潜りが出る。油を運ぶ道で足を取られる。四匹払おう。',
          'Sand burrowers lurk in the grassland between the hamlet and the cape, tripping up the oil carriers. Drive off four.')),
    go([('northcape_light', ('北岬の灯を見てこよう（里から南東）', 'Visit the Northcape Light (southeast of the hamlet)'))],
       ('北岬の灯は里から南東にある。前の灯守は毎晩ひとつずつ夜を数えていたという。今いくつか見てこよう。',
        'The Northcape Light, southeast of the hamlet. The old keeper counted one night at a time. See what the count is now.')),
], dict(
    title=('九千の夜', 'Nine Thousand Nights'),
    summary=('灯守見習いのミナトに頼まれて、砂潜りを払い、北岬の灯を見てくる。', "For Minato the lightkeeper's apprentice, drive off the sand burrowers and visit the Northcape Light."),
    done=('北岬の灯の壁には、灯守が数えた夜の数が刻まれていた。九千。ミナトは、その次の数を自分で数えると決めた。',
          "On the wall of the Northcape Light was the count of nights the keeper had kept: nine thousand. Minato decided to count the next one himself."),
    back_journal=('数は九千だった。ミナトに伝えよう。', 'The count was nine thousand. Tell Minato.'),
    back_obj=('灯守見習いのミナトに伝えよう', 'Tell Minato the apprentice'),
    ask=('北岬の灯の灯守さん、毎晩ひとつずつ夜を数えてたんだって。ぼく、そのあとを継ぐことになったんだけど……今いくつなのか、誰も知らないんだ。\n'
         '南東の北岬まで見てきてくれない？　途中の草原の砂潜りも、四匹くらい払ってくれると助かる。油を運ぶとき、足を取られるんだ。',
         "The keeper at the Northcape Light counted one night at a time, every night. I'm to take over after them... but nobody knows what the count is now.\n"
         "Would you go to the cape to the southeast and look? And if you could drive off four or so sand burrowers in the grassland on the way, that'd help. They trip us up when we carry the oil."),
    ask_ok=('引き受ける', "I'll do it"),
    wait=('砂潜りは草原の砂の下。北岬の灯は南東だよ。壁に刻んであると思う。', 'The burrowers are under the sand in the grassland. The Northcape Light is southeast. I think the count is carved on the wall.'),
    back=('いくつだった？', 'What was the count?'),
    back_give=('九千だった、と伝える', 'Tell him it was nine thousand'),
    done_line=('九千……。灯守さん、ぼくが生まれる前からずっと数えてたんだ。\nじゃあ今夜から、ぼくが九千と一だね。……ありがとう。これ、灯の油代にと渡された箱。ぼくより、旅の人が持っていた方がいい。',
               "Nine thousand... They'd been counting since before I was born.\nSo tonight I'm nine thousand and one. ...Thanks. This box was given to me for lamp oil. It's better off with a traveller than with me."),
    idle=('この里はね、灯守の家ばかりなんだ。北岬、東の岬、南の岬。みんな交代で灯をともす。\n消えた灯があったら、その晩は誰も寝ないで、海を見てる。',
          "This hamlet is all lightkeepers' houses. North cape, east cape, south cape — everyone takes turns lighting them.\nIf a light ever goes out, nobody sleeps that night. We all watch the sea."),
    hint=('北の岬の、そのまた先にも、昔は灯があったんだって。古い砦の上に。\n……今は、誰もともしてない。なのに、ときどき光るんだ。',
          'They say there used to be a light even further north than the north cape. On top of an old fort.\n...No one lights it now. But sometimes it shines anyway.'),
    after=('潮殻が眠ってから、海がずっと凪いでる。灯を探す船も、来なくなった。\n……それでも、ともすよ。九千と、いくつめだっけ。',
           "Since the tide shell went to sleep, the sea's been calm. No ships come looking for the light any more.\n...I still light it, though. Nine thousand and... what was it now."),
))

person('lightvillage_comber', 't_e', 'folk_b', 'tide', 325, ('浜拾いのアオ', 'Ao the Beachcomber'), [
    go([('northern_strand', ('北の座礁船を見てこよう（里から東）', 'Visit the Northern Strand wreck (east of the hamlet)')),
        ('tidewatch_ruin', ('潮見の塔を見てこよう（里から南）', 'Visit the Tidewatch Ruin (south of the hamlet)'))],
       ('北の座礁船は里から東、潮見の塔は南にある。アオの拾う大きな貝殻が、どこから来るのか。どちらにも、昔の人の書き残しがあるという。',
        'The Northern Strand wreck east of the hamlet, and the Tidewatch Ruin to the south. Where do the huge shells Ao finds come from? Both places hold words left by people long ago.')),
], dict(
    title=('島だと思った', 'We Thought It Was an Island'),
    summary=('浜拾いのアオに頼まれて、北の座礁船と潮見の塔を見てくる。', 'For Ao the beachcomber, visit the Northern Strand wreck and the Tidewatch Ruin.'),
    done=('北の座礁船には「潮殻の背に乗り上げた。島だと思った」とあり、潮見の塔には「海の底に背が見えた」とあった。アオは、拾い集めた貝殻を海へ返した。',
          'The wreck read "Ran aground on the tide shell\'s back. We thought it was an island," and the tower, "The sea bottom showed a back." Ao returned her shells to the sea.'),
    back_journal=('どちらも「背」のことを書いていた。アオに伝えよう。', 'Both wrote about a "back". Tell Ao.'),
    back_obj=('浜拾いのアオに伝えよう', 'Tell Ao the beachcomber'),
    ask=('浜で拾う貝殻がね、このごろ大きいの。手のひらより大きい。こんな貝、海にいないよ。\n'
         '東の座礁船と、南の潮見の塔。どっちも昔の人が何か書き残してるって。見てきてくれる？　……この貝がどこから来たのか、知りたいの。',
         "The shells I find on the beach are getting bigger lately. Bigger than my hand. There's no shellfish that size in the sea.\n"
         'The wreck to the east and the Tidewatch Ruin to the south — people long ago left writing at both. Would you go and look? ...I want to know where these shells come from.'),
    ask_ok=('見てくる', "I'll go and look"),
    wait=('座礁船は里から東、潮見の塔は南。……貝が落ちてたら、拾わないでね。', "The wreck is east of the hamlet, the tower south. ...If you see shells lying about, don't pick them up."),
    back=('何て書いてあった？', 'What did they say?'),
    back_give=('どちらも潮殻の背のことだった、と伝える', "Tell her both were about the tide shell's back"),
    done_line=('背……。じゃあこの貝殻、潮殻の背から剥がれたんだ。\n返してくる。……拾っちゃいけないものだった気がする。これは浜で拾った箱。こっちは返さなくていいと思う。',
               "A back... So these shells flaked off the tide shell.\nI'll give them back. ...I feel like they weren't mine to take. This is a box I found on the beach. I don't think this one needs returning."),
    idle=('浜拾いはね、朝いちばんが勝負。潮が引いたときに、夜の海が置いてったものを拾うの。\n……ときどき、拾う前に動くものもあるけど。',
          "Beachcombing is all about first thing in the morning. When the tide's out, you pick up what the night sea left behind.\n...Sometimes things move before you pick them up, though."),
    hint=('潮見の塔の「背が見えた」朝って、いつのことだろう。……おばあちゃんは、北の砦の沖だって言ってた。\nあの辺の海は、今でも引き潮のときだけ、変な形の岩が出るんだよ。',
          'When was that morning the tower saw a back, I wonder. ...Grandma said it was off the old fort to the north.\nEven now, at low tide, oddly shaped rocks show out there.'),
    after=('潮殻が眠ってから、大きな貝殻が浜に来なくなった。\n……さみしいような、ほっとしたような。',
           'Since the tide shell went to sleep, the big shells stopped washing up.\n...I feel lonely, and relieved.'),
))

# ================================================================ 潮垣の砦村 (潮殻の南東)
person('tidefort_watch', 't_se', 'folk_a', 'tide', 326, ('潮垣の見張りカジ', 'Kaji of the Tidewall Watch'), [
    kill(['skyabyss:reef_scuttler'], 5, ('磯走りを払おう（砦のまわりの浜や沼）', 'Drive off reef scuttlers (shores around the fort)'),
         ('砦のまわりの浜や沼に、磯走りが群れている。夜に垣の下を掘るらしい。五匹払おう。',
          "Reef scuttlers swarm the beaches and marshes around the fort, digging under the wall at night. Drive off five.")),
    go([('sunk_tower', ('沈み塔の段を数えてこよう（砦から北北西）', "Count the Sunken Tower's steps (north-northwest)"))],
       ('砦から北北西の沈み塔。年に一段ずつ砂に沈むという。碑の数と、本当に残っている段の数を見比べよう。',
        'The Sunken Tower, north-northwest of the fort. It sinks one step into the sand each year. Compare the count on its marker with the steps actually left.')),
], dict(
    title=('あと四段', 'Four Steps Left'),
    summary=('潮垣の見張りカジに頼まれて、磯走りを払い、沈み塔に何段残っているか見てくる。', 'For Kaji of the Tidewall watch, drive off the reef scuttlers and see how many steps of the Sunken Tower remain.'),
    done=('沈み塔の碑には「あと四段」とあったが、砂の上に出ている段は三つだった。カジは、砦の垣を一段高く組み直すと言った。',
          'The Sunken Tower\'s marker said "four steps left", but only three showed above the sand. Kaji said he would build the fort\'s wall one course higher.'),
    back_journal=('碑は「あと四段」とあったが、見えるのは三段だった。カジに伝えよう。', 'The marker said four, but only three showed. Tell Kaji.'),
    back_obj=('潮垣の見張りカジに伝えよう', 'Tell Kaji of the watch'),
    ask=('磯走りどもが、夜な夜な垣の下を掘りやがる。五匹ほど払ってくれ。\n'
         'それと、北北西の沈み塔だ。あれは年に一段ずつ砂に沈む。碑には「あと四段」とあるはずだ。……本当に四段残ってるか、数えてきてくれ。',
         'The reef scuttlers dig under the wall every night. Drive off five or so.\n'
         'And the Sunken Tower to the north-northwest. It sinks one step a year. Its marker should say "four steps left". ...Count whether four really remain.'),
    ask_ok=('引き受ける', "I'll do it"),
    wait=('磯走りは浜と沼だ。沈み塔は北北西。砂から出てる段を数えろ。', 'The scuttlers are on the beaches and marshes. The tower is north-northwest. Count the steps above the sand.'),
    back=('何段だった。', 'How many?'),
    back_give=('碑は四段、見えるのは三段だった、と伝える', 'Tell him the marker said four, but only three showed'),
    done_line=('三段……。沈むのが速くなっとる。\n砂が沈むんじゃない。下の何かが、沈んでいくんだ。……垣を一段高くする。礼はこれだ。',
               "Three... It's sinking faster.\nIt isn't the sand that's sinking. Something underneath is going down. ...I'll raise the wall a course. Here's your pay."),
    idle=('潮垣はな、潮が来るたびに一段ずつ組み足してきた垣だ。爺さまの代は、膝の高さだった。\n今は見ての通りだ。……海が上がっとるのか、土が下がっとるのか。',
          "The Tidewall's been built up a course every time the tide came. In my grandfather's day it was knee-high.\nNow look at it. ...Is the sea rising, or the land sinking?"),
    hint=('南の沖の、溶けた岩が固まった岬のあたり。夜になると、海の中が赤い。\n……沈み塔が傾いとる先も、あっちの方角なんだ。',
          'Off the south coast, by the cape of hardened lava. At night the sea glows red there.\n...And the Sunken Tower leans that way, too.'),
    after=('潮殻が眠ってから、潮が垣まで来なくなった。……組み足さなくていい。\n手が暇でな。磯走りを数えとる。',
           "Since the tide shell went to sleep, the tide doesn't reach the wall. ...No need to build it up.\nMy hands are idle. I count scuttlers."),
))

person('tidefort_netmender', 't_se', 'folk_b', 'tide', 327, ('網繕いのフサ', 'Fusa the Net Mender'), [
    go([('whaler_wreck', ('鯨捕り船の残骸を見てこよう（砦から北西）', "Visit the Whaler's Wreck (northwest of the fort)")),
        ('brine_hulk', ('塩漬けの船を見てこよう（砦から北北東）', 'Visit the Brine Hulk (north-northeast of the fort)'))],
       ('鯨捕り船の残骸は砦から北西、塩漬けの船は砦から北北東にある。フサの亭主は、空の鯨を追う船に乗っていた。どちらかに、その船の名残があるかもしれない。',
        "The Whaler's Wreck northwest of the fort, and the Brine Hulk to the north-northeast. Fusa's husband sailed on a ship that chased the whale in the sky. One of them may hold a trace of it.")),
], dict(
    title=('東を向いた舵', 'The Rudder Facing East'),
    summary=('網繕いのフサに頼まれて、鯨捕り船の残骸と塩漬けの船を見てくる。', "For Fusa the net mender, visit the Whaler's Wreck and the Brine Hulk."),
    done=('鯨捕り船の舵は、まだ東を向いていた。塩漬けの船には、生き残りの焚き火が三日で消えたとあった。フサは、亭主の網を繕い終えた。',
          "The whaler's rudder still faced east. The Brine Hulk told of survivors whose fire went out in three days. Fusa finished mending her husband's net."),
    back_journal=('舵は東を向き、焚き火は三日で消えていた。フサに伝えよう。', 'The rudder faced east; the fire went out in three days. Tell Fusa.'),
    back_obj=('網繕いのフサに伝えよう', 'Tell Fusa the net mender'),
    ask=('うちの亭主はね、空の鯨を追う船に乗ってたの。笑うだろう？　でも本当に追ってたんだよ。\n'
         '北西の鯨捕り船と、北北東の塩漬けの船。どっちかが、あの人の船かもしれない。見てきておくれ。わたしはこの網を繕い終えるまで、ここを動かないって決めてるから。',
         "My husband sailed on a ship that chased the whale in the sky. You'll laugh. But they really did chase it.\n"
         "The whaler's wreck to the northwest, and the brine hulk to the north-northeast. One of them may be his ship. Go and look for me. I've promised myself not to leave this spot until I've finished mending this net."),
    ask_ok=('見てくる', "I'll go and look"),
    wait=('鯨捕り船は砦から北西、塩漬けの船は北北東。……どっちも、海の方を向いてるはずだよ。', "The whaler's northwest of the fort, the brine hulk north-northeast. ...They'll both be facing the sea."),
    back=('どうだったね。', 'Well?'),
    back_give=('舵は東を向き、焚き火は三日で消えていた、と伝える', 'Tell her the rudder faced east and the fire lasted three days'),
    done_line=('舵が東……。あの人、最後まで鯨の方を向いてたんだね。\nなら、この網はもう要らないね。繕い終わったから、それでいいの。……これは亭主の箱。あんたに持っててほしい。',
               "The rudder east... He faced the whale to the very end.\nThen I won't need this net. It's mended, and that's enough. ...This was his box. I'd like you to have it."),
    idle=('網はね、破れたところから繕うんじゃないの。破れたところのまわりから、少しずつ寄せていくんだよ。\n……人を待つのも、同じだね。',
          "You don't mend a net starting at the tear. You start around it and draw it in, bit by bit.\n...Waiting for someone's the same."),
    hint=('東の沖の、沈んだ社の話を知ってるかい。潮がいちばん引く日に、屋根だけが出るんだって。\n亭主は、あそこで鯨を祀った人たちがいたって言ってた。',
          'Have you heard of the sunken shrine off the east coast? On the lowest tide of the year, only its roof shows.\nMy husband said people once worshipped the whale there.'),
    after=('潮殻が眠った晩、浜に網が一枚打ち上がったの。……うちの亭主の結び方だった。\nどこから流れてきたんだろうね。',
           'The night the tide shell went to sleep, a net washed up on the beach. ...Tied my husband\'s way.\nWhere do you suppose it drifted from.'),
))

# ================================================================ 熾垣の砦村 (余燼の南東)
person('emberfort_smith', 'e_se', 'folk_a', 'ember', 328, ('砦鍛冶のタタラ', 'Tatara the Fort Smith'), [
    kill(['skyabyss:ember_jackal'], 4, ('熾の山犬を払おう（砦のまわりの荒れ地）', 'Drive off ember jackals (badlands around the fort)'),
         ('砦のまわりの荒れ地に、熾の山犬がいる。炭を運ぶ者を群れで追うという。四匹払おう。',
          'Ember jackals roam the badlands around the fort, hunting the charcoal carriers in packs. Drive off four.')),
    go([('black_gate', ('黒門の言葉を読んでこよう（砦から西）', 'Read the Black Gate (west of the fort)'))],
       ('黒門は砦から西にある。灰の民の門だという。門に刻まれた言葉を読んでこよう。', "The Black Gate, west of the fort: the ash folk's gate. Read the words carved on it.")),
], dict(
    title=('下へ返せ', 'Return It Below'),
    summary=('砦鍛冶のタタラに頼まれて、熾の山犬を払い、黒門の言葉を読んでくる。', 'For Tatara the fort smith, drive off the ember jackals and read the words on the Black Gate.'),
    done=('黒門には「火は下から来る。下へ返せ」とあった。タタラは、炉の火を一つ落とした。',
          'The Black Gate read "Fire comes from below. Return it below." Tatara let one of the forge fires die.'),
    back_journal=('「下へ返せ」とあった。タタラに伝えよう。', 'It said "Return it below." Tell Tatara.'),
    back_obj=('砦鍛冶のタタラに伝えよう', 'Tell Tatara the smith'),
    ask=('熾の山犬が、炭運びを群れで追い回しとる。炭が来んと炉が冷える。四匹払ってくれ。\n'
         'それから西の黒門だ。灰の民が門に何か刻んどるそうだ。……わしの炉の火は、地面の割れ目から借りた火でな。返せと書いてあるなら、返さにゃならん。',
         'Ember jackals are running the charcoal carriers ragged. No charcoal, and the forge goes cold. Drive off four.\n'
         "Then the Black Gate to the west. The ash folk carved something on it. ...My forge fire was borrowed from a crack in the ground. If it says to give it back, I'll have to."),
    ask_ok=('引き受ける', "I'll do it"),
    wait=('山犬は砦のまわりの荒れ地だ。黒門は西。門の柱を見ろ。', 'The jackals are in the badlands around the fort. The Black Gate is west. Look at the pillars.'),
    back=('何と刻んであった。', 'What did it say?'),
    back_give=('「火は下から来る。下へ返せ」と伝える', 'Tell him "Fire comes from below. Return it below."'),
    done_line=('下へ返せ、か。……そうか。借りっぱなしだったな。\n炉を一つ落とす。残りの炉で十分打てる。……これは打ち賃の代わりだ。',
               "Return it below, eh. ...Right. I've been borrowing it all this time.\nI'll let one forge go out. The rest are enough to work with. ...Take this in place of wages."),
    idle=('この砦の垣は、炉で焼き締めた土だ。火で固めた垣は、火に強い。\n……と思ってたんだがな。熾の山犬は、垣の土を舐めて温まりに来よる。',
          "The fort's wall is earth baked hard in the forge. A wall fired in flame stands up to flame.\n...So I thought. The ember jackals come and lick the wall to warm themselves."),
    hint=('東南東の岸に、固まった溶岩の筒が口を開けとる。中はまだ熱い。\n……黒門の言う「下」は、ああいう所のことかもしれん。',
          'On the shore to the east-southeast, a tube of hardened lava gapes open. It is still hot inside.\n...Maybe the "below" on the Black Gate means a place like that.'),
    after=('余燼が眠ってから、借りた火が少しおとなしくなった。\n……返せと言われる前に、少しずつ返していこうと思う。',
           "Since the Ember went to sleep, the borrowed fire has calmed a little.\n...I'll give it back bit by bit, before I'm told to."),
))

person('emberfort_survivor', 'e_se', 'folk_b', 'ember', 329, ('灰見のシノ', 'Shino of Ashwatch'), [
    kill(['skyabyss:cinder_mite'], 5, ('熾り蟲を払おう（砦のまわりの荒れ地）', 'Drive off cinder mites (badlands around the fort)'),
         ('灰見砦への道に、熾り蟲が湧いている。灰の中で群れているらしい。五匹払おう。', 'Cinder mites swarm on the way to Ashwatch Fort, nesting in the ash. Drive off five.')),
    go([('ashwatch_fort', ('灰見砦の見張り台を見てこよう（砦から西）', "Look at Ashwatch Fort's watchtower (west of the fort)"))],
       ('灰見砦は砦から西にある。シノが見張りをしていた砦だ。見張り台に何が残っているか、見てこよう。', 'Ashwatch Fort, to the west, where Shino once kept watch. See what remains on its watchtower.')),
], dict(
    title=('空を見ていた見張り', 'The Watch Who Watched the Sky'),
    summary=('灰見砦の生き残りシノに頼まれて、熾り蟲を払い、灰見砦の見張り台を見てくる。', "For Shino, a survivor of Ashwatch Fort, clear the cinder mites and look at the fort's watchtower."),
    done=('灰見砦の碑には「見張りは最後まで空を見ていた。火は下から来た」とあった。シノは、空ではなく足もとを見るようになった。',
          'Ashwatch Fort\'s marker read "The watch looked to the sky to the end. The fire came from below." Shino began watching the ground instead of the sky.'),
    back_journal=('「火は下から来た」とあった。シノに伝えよう。', 'It said "The fire came from below." Tell Shino.'),
    back_obj=('灰見のシノに伝えよう', 'Tell Shino of Ashwatch'),
    ask=('わたしは灰見砦の見張りだった。あの晩も、空を見てた。余燼が来るなら空からだと思ってたから。\n'
         '西の灰見砦まで行って、見張り台に何が残ってるか見てきて。途中の熾り蟲も五匹くらい払ってほしい。灰の中に湧いてて、近づけないの。',
         "I was a watch at Ashwatch Fort. That night too, I was watching the sky. I thought if the Ember came, it'd come from the sky.\n"
         "Go west to Ashwatch Fort and see what's left on the watchtower. And clear about five cinder mites on the way. They breed in the ash and I can't get near."),
    ask_ok=('引き受ける', "I'll do it"),
    wait=('熾り蟲は灰の溜まった所。灰見砦は西だよ。', 'The mites are where the ash piles up. Ashwatch is to the west.'),
    back=('……何があった。', '...What was there?'),
    back_give=('「火は下から来た」と書いてあった、と伝える', 'Tell her it said "The fire came from below"'),
    done_line=('下から。……そう。みんな空を見てたのに。\nこれからは足もとを見る。それが、残ったわたしの見張り。……これ、砦から持ち出した箱。あなたに預ける。',
               "From below. ...Yes. We were all looking at the sky.\nFrom now on I'll watch the ground. That's the watch left to me. ...This is a box I carried out of the fort. I'll leave it with you."),
    idle=('見張りってね、何も来ない日の方がずっと長いの。何も来ない空を、ずっと見てる。\n……来た日のことは、あんまり覚えてない。',
          "Keeping watch — the days when nothing comes are far longer. You watch an empty sky for ever.\n...I don't remember much about the day it came."),
    hint=('足もとを見てるとね、ときどき地面が温かい所がある。東の方ほど温かい。\n……あっちに、まだ下から来てない火があるんだと思う。',
          "Watching the ground, sometimes there are warm patches. The further east, the warmer.\n...I think there's fire over there that hasn't come up yet."),
    after=('余燼が眠った。……空も、足もとも、静か。\n見張ることがないって、こんなに落ち着かないものなんだね。',
           'The Ember is asleep. ...Sky and ground both quiet.\nI never knew having nothing to watch could be so unsettling.'),
))

# ================================================================ 煙見の里 (余燼の北)
person('smokewatch_reader', 'e_n', 'folk_a', 'ember', 330, ('煙読みのユウ', 'Yu the Smoke Reader'), [
    kill(['skyabyss:ash_dragger'], 3, ('灰ひきずりを払おう（里のまわり）', 'Drive off ash draggers (around the hamlet)'),
         ('里のまわりに、灰ひきずりが出る。煙の筋を乱すらしい。三匹払おう。', 'Ash draggers roam around the hamlet, tangling the lines of smoke. Drive off three.')),
    go([('northern_forge', ('北の炉跡を見てこよう（里から西）', 'Visit the Northern Forge (west of the hamlet)'))],
       ('北の炉跡は里から西にある。そこの煙だけ、風に逆らって都の方へ流れるという。何の炉だったか見てこよう。',
        'The Northern Forge, west of the hamlet. Its smoke alone drifts toward the capital, against the wind. Find out what it forged.')),
], dict(
    title=('割れた鐘の炉', 'The Forge of the Cracked Bell'),
    summary=('煙読みのユウに頼まれて、灰ひきずりを払い、北の炉跡を見てくる。', 'For Yu the smoke reader, drive off the ash draggers and visit the Northern Forge.'),
    done=('北の炉跡は、都の鐘を鋳た炉だった。割れた鐘はそこへ戻されたという。ユウは、煙がいつも都の方へ流れるわけがわかったと言った。',
          "The Northern Forge was where the capital's bell was cast; the cracked bell was brought back there. Yu said now he understood why the smoke always drifts toward the capital."),
    back_journal=('都の鐘を鋳た炉だった。ユウに伝えよう。', "It was where the capital's bell was cast. Tell Yu."),
    back_obj=('煙読みのユウに伝えよう', 'Tell Yu the smoke reader'),
    ask=('煙はな、風の向きに流れる。当たり前だ。だが、里から西にある北の炉跡の煙だけは、風に逆らって都の方へ流れよる。\n'
         '何の炉だったか見てきてくれ。それと、灰ひきずりを三匹ほど。あいつらが通ると煙の筋が乱れて、読めんのだ。',
         'Smoke drifts with the wind. Obviously. But the smoke from the Northern Forge to the west drifts toward the capital, against the wind.\n'
         "Go and see what that forge made. And drive off three or so ash draggers. When they pass, the smoke lines tangle and I can't read them."),
    ask_ok=('引き受ける', "I'll do it"),
    wait=('灰ひきずりは里のまわりだ。北の炉跡は西。碑を読め。', 'The draggers are around the hamlet. The Northern Forge is west. Read the marker.'),
    back=('何の炉だった。', 'What did it forge?'),
    back_give=('都の鐘を鋳た炉で、割れた鐘がそこへ戻された、と伝える', "Tell him it cast the capital's bell, and the cracked bell was returned there"),
    done_line=('都の鐘……。割れた鐘が、生まれた炉へ帰ったのか。\nなら、あの煙は鐘を呼んどるんだ。新しい鐘の方へ、「こっちだ」と。……礼だ。持っていけ。',
               "The capital's bell... The cracked bell went home to the forge it was born in.\nThen that smoke is calling to the bell. Toward the new one: \"Over here.\" ...Here, for your trouble."),
    idle=('煙見の里では、朝いちばんに煙を上げて、その曲がり方でその日を決める。\nまっすぐなら出かける。北へ曲がれば家にいる。……南へ曲がった日は、誰も口をきかん。',
          'In Smokewatch we send up smoke first thing and decide the day by how it bends.\nStraight up, we go out. Bends north, we stay in. ...The days it bends south, no one speaks.'),
    hint=('南東の海べりで、地面から煙が上がっとる所がある。あれは誰も焚いとらん。\n……読もうとしたが、あの煙は字が逆さまだ。',
          "Down on the south-east shore there's a spot where smoke comes straight out of the ground. No one lit it.\n...I tried to read it. The letters are upside down."),
    after=('余燼が眠った朝、煙がまっすぐ上がった。……何十年ぶりかな。\n今日は出かける日だ。どこへ行けばいいのかは、煙は教えてくれんがな。',
           "The morning the Ember went to sleep, the smoke went straight up. ...First time in decades.\nToday's a day for going out. Where to, the smoke won't say."),
))

person('smokewatch_twin', 'e_n', 'folk_b', 'ember', 331, ('双子のヨル', 'Yoru the Twin'), [
    go([('twins_house', ('双子の家を見てこよう（里から北西）', "Visit the Twins' House (northwest of the hamlet)")),
        ('drip_cave', ('水滴の洞を見てこよう（里から西北西）', 'Visit the Drip Cave (west-northwest of the hamlet)'))],
       ('双子の家は里から北西、水滴の洞は西北西にある。ヨルの片割れは、洞で水の落ちる音を数えるのが好きだった。',
        "The Twins' House northwest of the hamlet, and the Drip Cave west-northwest. Yoru's twin loved to count the drops falling in the cave.")),
], dict(
    title=('二本の背丈', 'Two Height Marks'),
    summary=('双子のヨルに頼まれて、双子の家と水滴の洞を見てくる。', "For Yoru the twin, visit the Twins' House and the Drip Cave."),
    done=('双子の家の柱の印は、一本が途中で止まっていた。水滴の洞には「九百で眠った」とあった。ヨルは、止まった印の上に小さな印を一つ足した。',
          'On the pillar of the Twins\' House, one height mark stopped partway. The Drip Cave said "Fell asleep at nine hundred." Yoru added one small mark above the one that stopped.'),
    back_journal=('柱の印は一本止まり、洞には「九百で眠った」とあった。ヨルに伝えよう。', 'One mark stopped; the cave said "fell asleep at nine hundred." Tell Yoru.'),
    back_obj=('双子のヨルに伝えよう', 'Tell Yoru'),
    ask=('おれには双子のきょうだいがいたんだ。北西の家で、柱に背丈の印をつけてた。……あいつの印は、途中で止まってる。\n'
         '西北西の水滴の洞にも行ってほしい。あいつ、あそこで水の音を数えるのが好きだった。何か残ってないか見てきて。',
         "I had a twin. At our house to the northwest, we marked our heights on the pillar. ...Their marks stop partway.\n"
         "Go to the Drip Cave to the west-northwest too. They loved counting the drops there. See if anything's left."),
    ask_ok=('見てくる', "I'll go and look"),
    wait=('家は里から北西、洞は西北西。……洞の中で、耳を澄ませてみて。', 'The house is northwest of the hamlet, the cave west-northwest. ...Listen closely inside the cave.'),
    back=('どうだった。', 'Well?'),
    back_give=('印は一本止まっていて、洞には「九百で眠った」とあった、と伝える', 'Tell him one mark stopped, and the cave said "fell asleep at nine hundred"'),
    done_line=('九百……。あいつ、いつも九百まで数えて寝ちゃうんだ。最後まで、そうだったんだな。\n止まった印の上に、小さいのを一つ足しとく。……これ、あいつが拾ってきた箱。開けてやってくれ。',
               "Nine hundred... They always fell asleep counting to nine hundred. Right to the end.\nI'll add a little mark above the one that stopped. ...This is a box they found. Open it for them."),
    idle=('双子ってさ、片方が熱を出すと、もう片方も具合が悪くなるって言うだろ。\n……あいつがいなくなった晩、おれ、ひどく寒かったんだ。余燼の火が、すぐそこまで来てたのに。',
          "They say when one twin runs a fever, the other gets sick too.\n...The night they were gone, I was freezing. Even with the Ember's fire right on top of us."),
    hint=('夢に、あいつが出てくる。北の、白い島で、水の音を数えてる。九百のつぎを数えようとして、また眠っちゃう。\n……北にそんな島、あるのかな。',
          'They come to me in dreams. On a white island in the north, counting the sound of water. They try to count past nine hundred and fall asleep again.\n...Is there an island like that up north?'),
    after=('余燼が眠った晩、おれも九百まで数えてみた。……眠れなかった。\nあいつの番は、まだ終わってないのかもな。',
           "The night the Ember went to sleep, I counted to nine hundred too. ...Couldn't sleep.\nMaybe their turn isn't over yet."),
))

# ================================================================ 霜垣の砦村 (嵐の高地の北西)
person('frostfort_hunter', 's_nw', 'folk_a', 'storm', 332, ('霜垣の狩人シモ', 'Shimo the Frostwall Hunter'), [
    kill(['skyabyss:static_puff'], 5, ('帯電毛玉を払おう（砦のまわり）', 'Drive off static puffs (around the fort)'),
         ('砦のまわりに、帯電毛玉が漂っている。垣の銅の釘にまとわりつくらしい。五匹払おう。', "Static puffs drift around the fort, clinging to the wall's copper nails. Drive off five.")),
    go([('thunderstone_ring', ('雷石の環を見てこよう（砦から南南西）', 'Visit the Thunderstone Ring (south-southwest of the fort)'))],
       ('雷石の環は砦から南南西にある。雷の落ちた石だけを環に並べるという。いくつ並んでいるか、見てこよう。',
        'The Thunderstone Ring, south-southwest of the fort, where only lightning-struck stones are set. See how many are there.')),
], dict(
    title=('九つめの座', 'The Ninth Seat'),
    summary=('霜垣の狩人シモに頼まれて、帯電毛玉を払い、雷石の環を見てくる。', 'For Shimo the Frostwall hunter, drive off the static puffs and look at the Thunderstone Ring.'),
    done=('雷石の環には「九つめの座は、まだ空いている」とあった。シモは、雷の落ちた石を見つけても運ばないことにした。',
          'The Thunderstone Ring read "The ninth seat is still empty." Shimo decided that even if he found a lightning-struck stone, he would leave it be.'),
    back_journal=('九つめの座は空いていた。シモに伝えよう。', 'The ninth seat was empty. Tell Shimo.'),
    back_obj=('霜垣の狩人シモに伝えよう', 'Tell Shimo the hunter'),
    ask=('帯電毛玉が、垣の銅の釘にくっついてパチパチいってやがる。夜も眠れねえ。五匹ほど払ってくれ。\n'
         'それと南南西の雷石の環だ。雷の落ちた石を並べた環でな、親父は「九つ揃うと何かが来る」と言ってた。今いくつ並んでるか見てきてくれ。',
         "The static puffs cling to the wall's copper nails, crackling all night. Can't sleep. Drive off five or so.\n"
         'And the Thunderstone Ring to the south-southwest. A ring of lightning-struck stones. My father said "when there are nine, something comes." See how many are set now.'),
    ask_ok=('引き受ける', "I'll do it"),
    wait=('毛玉は砦のまわりをふわふわしてる。雷石の環は南南西だ。', 'The puffs float around the fort. The Thunderstone Ring is south-southwest.'),
    back=('いくつだった。', 'How many?'),
    back_give=('九つめの座は空いていた、と伝える', 'Tell him the ninth seat was empty'),
    done_line=('まだ八つか。……よかった、と言っていいのかね。\n雷の落ちた石を見つけたら、運んでやろうと思ってたんだが。やめとくか。……ほら、礼だ。',
               "Still eight. ...Can I call that good?\nI'd meant to haul one over if I found a struck stone. Maybe I won't. ...Here, your due."),
    idle=('霜垣ってのはな、冬に垣が凍って霜の壁になるから、そう呼ぶ。雷翼が来ても、霜の壁は燃えねえ。\n……溶けるけどな。',
          "They call it Frostwall because the wall freezes into a wall of frost in winter. When the thunder wing comes, frost doesn't burn.\n...It melts, mind."),
    hint=('西北西の海に、霜の降りる小島がある。夏でも岸が白い。\n獲物を追ってあっちの渡しまで行ったら、渡し守に言われたよ。「渡るのはいい。戻ってくる気があるならな」ってな。',
          "In the sea west-northwest there's a little island where frost falls. Its shore is white even in summer.\nI once tracked game as far as the ferry over there. The ferryman told me: \"Crossing's fine. As long as you mean to come back.\""),
    after=('雷翼が眠ってから、毛玉が減った。垣の釘も静かだ。\n……静かすぎて、かえって目が覚める。',
           'Since the thunder wing went to sleep, there are fewer puffs. The nails are quiet.\n...So quiet it keeps me awake.'),
))

person('frostfort_fletcher', 's_nw', 'folk_b', 'storm', 333, ('矢羽師のハヤ', 'Haya the Fletcher'), [
    go([('brokenwing_tower', ('折れ羽の塔を見てこよう（砦から南）', 'Visit the Brokenwing Tower (south of the fort)')),
        ('cloudwatch_post', ('雲見の櫓を見てこよう（砦から南）', 'Visit the Cloudwatch Post (south of the fort)'))],
       ('折れ羽の塔と雲見の櫓は、砦から南にある。塔には大きな羽が刺さっているという。矢羽にできるか見てこよう。櫓では、羽の主の居場所の読み方がわかるかもしれない。',
        "The Brokenwing Tower and the Cloudwatch Post, both south of the fort. A huge feather is said to be stuck in the tower — see if it could fletch arrows. The post may tell how to read where the feather's owner is.")),
], dict(
    title=('人の背丈より長い羽', 'A Feather Taller Than a Man'),
    summary=('矢羽師のハヤに頼まれて、折れ羽の塔と雲見の櫓を見てくる。', 'For Haya the fletcher, visit the Brokenwing Tower and the Cloudwatch Post.'),
    done=('折れ羽の塔には、人の背丈より長い羽が刺さっていた。雲見の櫓には「雲が東へ流れる日、雷翼は西で眠る」とあった。ハヤは、その羽で矢は作らないと決めた。',
          'A feather taller than a man was stuck in the Brokenwing Tower. The Cloudwatch Post read "On days the clouds flow east, the thunder wing sleeps in the west." Haya decided never to make arrows from that feather.'),
    back_journal=('羽は人の背丈より長かった。ハヤに伝えよう。', 'The feather was taller than a man. Tell Haya.'),
    back_obj=('矢羽師のハヤに伝えよう', 'Tell Haya the fletcher'),
    ask=('矢羽ってのはね、鳥の羽ならなんでもいいわけじゃない。雷を浴びた羽は、矢がまっすぐ飛ぶの。\n'
         '南の折れ羽の塔に、とんでもなく大きい羽が刺さってるって聞いた。見てきてくれない？　ついでに雲見の櫓も。羽の主がどこにいるか、わかるかもしれない。',
         'Not just any feather will fletch an arrow. Feathers that have been struck by lightning fly true.\n'
         "I heard an enormous feather is stuck in the Brokenwing Tower to the south. Would you go and look? And the Cloudwatch Post too. It might tell where the feather's owner is."),
    ask_ok=('見てくる', "I'll go and look"),
    wait=('折れ羽の塔も雲見の櫓も、砦から南。……羽には触らないでね。', "The tower and the post are both south of the fort. ...Don't touch the feather."),
    back=('どうだった？', 'Well?'),
    back_give=('羽は人の背丈より長く、雲が東へ流れる日は雷翼が西で眠る、と伝える', 'Tell her the feather was taller than a man, and when clouds flow east the thunder wing sleeps in the west'),
    done_line=('人の背丈より……。それ、雷翼の羽だよ。抜け落ちたんじゃない。塔にぶつかって、折れたんだ。\nそんな羽で矢は作れない。射たら、持ち主のところへ帰っちゃう。……代わりに、これ。',
               "Taller than a man... That's a thunder wing feather. It didn't moult. It hit the tower and snapped.\nI can't make arrows from that. Shoot one and it'll fly home to its owner. ...Take this instead."),
    idle=('霜垣の矢は、霜の降りた朝に羽を継ぐの。冷えた羽は縮んで、ぴたっと締まる。\n……指も一緒に縮むけどね。',
          'Frostwall arrows are fletched on frosty mornings. The cold shrinks the feather so it grips tight.\n...Shrinks your fingers too.'),
    hint=('西の小島へ渡る船着き場で、羽を拾ったことがあるの。真っ白で、触ると指が凍りそうなくらい冷たかった。\n……雷翼の羽じゃない。何の羽かは、わからない。',
          "Once, at the landing for the western islet, I picked up a feather. Pure white, so cold my fingers nearly froze.\n...Not a thunder wing feather. I don't know whose it was."),
    after=('雷翼が眠ってから、雲がどっちへ流れても関係なくなった。\n櫓の言葉も、もう読む人はいないね。……わたしは読むけど。',
           "Since the thunder wing went to sleep, it doesn't matter which way the clouds flow.\nNo one reads the post's words now. ...I still do."),
))

# ================================================================ 風車の里 (嵐の高地の南西)
person('windmill_keeper', 's_sw', 'folk_a', 'storm', 334, ('風車番のカザミ', 'Kazami the Windmill Keeper'), [
    kill(['skyabyss:peak_fledgling'], 4, ('峰の雛を払おう（里のまわり）', 'Drive off peak fledglings (around the hamlet)'),
         ('里のまわりに、峰の雛が降りてくる。風車の羽をつついて破るらしい。四羽払おう。', 'Peak fledglings come down around the hamlet and peck holes in the windmill sails. Drive off four.')),
    go([('kitemaker_house', ('凧作りの家を見てこよう（里から北東）', "Visit the Kitemaker's House (northeast of the hamlet)"))],
       ('凧作りの家は里から北東にある。雷の日も凧を上げていた家だという。どうやって雷を避けたのか、見てこよう。',
        "The Kitemaker's House, northeast of the hamlet. They flew kites even in thunderstorms. Find out how they kept the lightning off.")),
], dict(
    title=('銅の凧糸', 'Copper Kite String'),
    summary=('風車番のカザミに頼まれて、峰の雛を払い、凧作りの家を見てくる。', "For Kazami the windmill keeper, drive off the peak fledglings and visit the Kitemaker's House."),
    done=('凧作りの家には「凧の糸は銅を撚って作る。雷を地へ逃がすために」とあった。カザミは、風車の軸に銅の糸を巻いた。',
          'The Kitemaker\'s House said "Kite string is twisted from copper, to send the lightning into the ground." Kazami wound copper string around the windmill\'s axle.'),
    back_journal=('凧の糸は銅だった。カザミに伝えよう。', 'The kite string was copper. Tell Kazami.'),
    back_obj=('風車番のカザミに伝えよう', 'Tell Kazami the windmill keeper'),
    ask=('雷の晩になると、風車に雷が落ちる。羽が焦げて、軸が割れる。去年は二度建て直した。\n'
         '北東の凧作りの家の人は、雷の日も平気で凧を上げてたって聞く。どうしてたのか見てきてくれ。それと、峰の雛を四羽払ってくれ。羽をつつきに来やがる。',
         'On thunder nights the lightning hits the windmill. Scorched sails, split axle. Rebuilt it twice last year.\n'
         'They say the kitemakers to the northeast flew kites even in thunderstorms without a care. Go and see how. And four peak fledglings — they come to peck the sails.'),
    ask_ok=('引き受ける', "I'll do it"),
    wait=('雛は里のまわりに降りてくる。凧作りの家は北東だ。', "The fledglings come down around the hamlet. The Kitemaker's House is northeast."),
    back=('どうだった。', 'Well?'),
    back_give=('凧の糸は銅を撚って作り、雷を地へ逃がしていた、と伝える', 'Tell him the string was twisted copper, sending the lightning into the ground'),
    done_line=('銅の糸で、地へ逃がす……。なるほどな。雷を止めるんじゃなくて、通り道を作ってやるのか。\n軸に銅を巻いてみる。これで三度目の建て直しはなしだ。……礼だ。',
               "Copper string, into the ground... I see. You don't stop the lightning — you give it a way through.\nI'll wind copper round the axle. No third rebuild this year. ...Your thanks."),
    idle=('風車の里の粉はな、雷の晩に挽いた粉がいちばんうまいと言われとる。\n……命がけで挽いてるんだ。うまくなきゃ困る。',
          'They say the best flour in Windmill comes from grinding on thunder nights.\n...We grind it at risk of our lives. It had better be good.'),
    hint=('北の山の向こうの海に、霜の小島がある。あっちから来る風は、雷の匂いがしない。\n……冷たすぎて、雷も凍るのかもな。',
          "Past the northern mountains, in the sea, there's a frost isle. The wind from there doesn't smell of thunder.\n...Maybe it's so cold even lightning freezes."),
    after=('雷翼が眠ってから、雷の晩が来ない。粉の味が、少し落ちた気がする。\n……贅沢な話だな。',
           'Since the thunder wing went to sleep, no thunder nights. The flour tastes a bit worse.\n...Listen to me complain.'),
))

person('windmill_mossguard', 's_sw', 'folk_b', 'storm', 335, ('苔守のミドリ', 'Midori the Moss Keeper'), [
    go([('south_moss_shrine', ('南の苔祠を見てこよう（里から北）', 'Visit the South Moss Shrine (north of the hamlet)')),
        ('rumble_cave', ('雷鳴の洞を見てこよう（里から北北東）', 'Visit the Rumble Cave (north-northeast of the hamlet)'))],
       ('里から北にある南の苔祠と、北北東の雷鳴の洞。苔祠の苔は雷を吸うという。洞の雷がどこから来るのか、二つを見比べよう。',
        "The South Moss Shrine north of the hamlet, and the Rumble Cave north-northeast. The shrine's moss is said to drink lightning. Compare the two and see where the cave's thunder comes from.")),
], dict(
    title=('晴れた日の雷鳴', 'Thunder on a Clear Day'),
    summary=('苔守のミドリに頼まれて、南の苔祠と雷鳴の洞を見てくる。', 'For Midori the moss keeper, visit the South Moss Shrine and the Rumble Cave.'),
    done=('南の苔祠の苔は、雷を吸って青く光っていた。雷鳴の洞の奥では、晴れた空の下で雷が鳴っていた。ミドリは、洞から祠へ雷の通う道を描いた。',
          "The moss at the South Moss Shrine glowed blue with drunk lightning. Deep in the Rumble Cave, thunder rolled under a clear sky. Midori drew the path the thunder takes from the cave to the shrine."),
    back_journal=('苔は光り、洞は鳴っていた。ミドリに伝えよう。', 'The moss glowed; the cave rumbled. Tell Midori.'),
    back_obj=('苔守のミドリに伝えよう', 'Tell Midori the moss keeper'),
    ask=('わたしは南の苔祠の苔を守ってるの。雷を吸う苔でね、雷の夜はみんな祠で夜を越すのよ。\n'
         'でもこのごろ、晴れた日にも苔が光るの。北北東の雷鳴の洞は、晴れてても中で雷が鳴るんだって。……つながってる気がするの。両方見てきてくれる？',
         'I keep the moss at the shrine to the north. It drinks lightning — on thunder nights everyone sees the night out at the shrine.\n'
         "But lately the moss glows even on clear days. And they say thunder rolls inside the Rumble Cave to the north-northeast, clear sky or not. ...I think they're connected. Would you look at both?"),
    ask_ok=('見てくる', "I'll go and look"),
    wait=('苔祠は里から北、雷鳴の洞は北北東。……洞の中で髪が逆立ったら、すぐ出てね。', 'The shrine is north of the hamlet, the cave north-northeast. ...If your hair stands on end in the cave, get out at once.'),
    back=('どうだった？', 'Well?'),
    back_give=('苔は光り、洞の奥は晴れていても鳴っていた、と伝える', 'Tell her the moss glowed and the cave rumbled under a clear sky'),
    done_line=('やっぱり。洞の雷が、地面の下を通って苔祠まで来てるのね。苔は、それを吸ってる。\n道を描いておく。雷の夜に、祠のどこに座れば安全かわかるから。……これ、祠のお供えの箱。苔守から旅の人へ。',
               "I knew it. The cave's thunder runs under the ground to the shrine, and the moss drinks it.\nI'll draw the path. Then we'll know where to sit at the shrine on thunder nights. ...This is a box from the shrine's offerings. From the moss keeper to a traveller."),
    idle=('苔はね、雷を吸うと青くなるの。吸いすぎると白くなって、枯れる。\nだから雷の夜は、みんなで少しずつ分けて吸わせるの。苔にも、人にも、ちょうどいい量があるのよ。',
          'Moss turns blue when it drinks lightning. Too much and it goes white and dies.\nSo on thunder nights we share it out a little at a time. Moss and people both have their proper share.'),
    hint=('雷鳴の洞の奥の雷はね、北の方から来てる気がする。もっと北の、雪の降る海の方から。\n……雷の生まれる場所って、寒いところなのかしら。',
          'The thunder deep in the cave seems to come from the north. Further north, from the sea where the snow falls.\n...Is thunder born somewhere cold, I wonder.'),
    after=('雷翼が眠ってから、苔が青くならないの。……少しずつ白くなってきた。\n雷がないと、苔もお腹がすくのね。',
           'Since the thunder wing went to sleep, the moss will not turn blue. ...It is slowly going white.\nWithout thunder, even moss goes hungry.'),
))

# 霜の小島の渡し守 (渡しの二つめ。話して「向こう岸へ渡る」。町 isle_ferry、印 dock)
ISLE = dict(
    name=('霜の渡し守', 'The Frost Strait Ferryman'),
    ask=('向こう岸へ渡るかい。この海峡は、夏でも指がかじかむ。\n乗りな。', 'Crossing over? This strait numbs your fingers even in summer.\nClimb in.'),
    cross=('向こう岸へ渡る', 'Cross over'),
    sea_opt=('霜の小島のこと', 'About Frost Isle'),
    bye=('またにする', 'Not now'),
    sea=('霜の小島はな、昔は島じゃなかったそうだ。陸と氷でつながってて、歩いて渡れた。\n……島の奥は、霜が深すぎて誰も行かん。行った者は、だいたい帰ってこん。',
         'Frost Isle was not always an island, they say. Ice joined it to the mainland and you could walk across.\n...No one goes into the interior. The frost is too deep. Those who do mostly do not come back.'),
)


# ---------------------------------------------------------------- 書き出し
def quest_id(pid):
    return 'town/hamlet_' + pid


def quest_json(pid, f):
    land = LAND[f['land']]
    stages = []
    for st in f['stages']:
        if st['kind'] == 'kill':
            stages.append({'id': 'kill', 'objectives': [{'id': 'kill', 'type': 'kill', 'count': st['count'], 'targets': st['targets']}]})
        else:
            stages.append({'id': 'go', 'objectives': [
                {'id': place, 'type': 'signal', 'target': 'poi:@' + place, 'party': False} for place, _ in st['places']]})
    stages.append({'id': 'back', 'objectives': [{'id': 'back', 'type': 'signal', 'target': 'town_hand:guest_' + pid, 'party': False}]})
    return {
        'category': 'sub',
        'order': f['order'],
        'giver': 'skyabyss:guest_' + pid,
        'chapter': land['chapter'],
        'min_stage': land['min'],
        'stages': stages,
        'reward': {'xp': land['xp'], 'items': [{'item': 'skyabyss:bell_coin', 'count': land['coins']}, {'item': land['box'], 'count': 1}]},
    }


def dialogue_json(pid, f):
    land = LAND[f['land']]
    q = quest_id(pid)
    ask = {
        'id': 'ask', 'goto': 'q_ask', 'kind': 'quest',
        'if': {'type': 'all', 'of': [{'type': 'stage', 'min': land['min']}, {'type': 'quest', 'quest': q, 'is': 'none'}]},
        'do': [{'type': 'quest_start', 'quest': q}],
    }
    leave = {'id': 'bye', 'goto': 'end', 'kind': 'leave'}
    nodes = {}
    for k, face in (('idle', 'neutral'), ('hint', 'grave'), ('after', 'smile')):
        nodes[k] = {'face': face, 'options': [dict(ask), dict(leave)]}
    nodes['q_ask'] = {'face': 'grave', 'options': [{'id': 'ok', 'goto': 'end', 'kind': 'talk'}]}
    nodes['q_wait'] = {'face': 'neutral'}
    give = {'id': 'give', 'goto': 'q_done', 'kind': 'quest', 'do': [{'type': 'signal', 'signal': 'town_hand:guest_' + pid}]}
    nodes['q_back'] = {'face': 'neutral', 'options': [give, {'id': 'later', 'goto': 'end', 'kind': 'leave'}]}
    nodes['q_done'] = {'face': 'smile', 'options': [{'id': 'ok', 'goto': 'end', 'kind': 'talk'}]}
    return {
        'npc': 'skyabyss:guest_' + pid,
        'entries': [
            {'node': 'q_back', 'if': {'type': 'quest', 'quest': q, 'is': 'active', 'stage': 'back'}},
            {'node': 'q_wait', 'if': {'type': 'quest', 'quest': q, 'is': 'active'}},
            # 頼みを済ませたあと: もっと奥をほのめかす
            {'node': 'hint', 'if': {'type': 'quest', 'quest': q, 'is': 'done'}},
            {'node': 'after', 'if': {'type': 'stilled', 'titan': land['titan']}},
            {'node': 'idle'},
        ],
        'nodes': nodes,
    }


def isle_dialogue():
    return {
        'npc': 'skyabyss:guest_isle_ferryman',
        'entries': [{'node': 'ask'}],
        'nodes': {
            'ask': {'face': 'neutral', 'options': [
                {'id': 'cross', 'goto': 'end', 'kind': 'talk', 'do': [{'type': 'town', 'act': 'inner_cross'}]},
                {'id': 'sea', 'goto': 'sea', 'kind': 'talk'},
                {'id': 'bye', 'goto': 'end', 'kind': 'leave'}]},
            'sea': {'face': 'grave', 'next': 'ask'},
        },
    }


def lang(i):
    kv = {}
    for pid, f in FOLK.items():
        t = f['t']
        qb = 'quest.skyabyss.' + quest_id(pid).replace('/', '.')
        db = 'dialogue.skyabyss.guest_' + pid
        kv['entity.skyabyss.guest_' + pid] = f['name'][i]
        kv[qb + '.title'] = t['title'][i]
        kv[qb + '.summary'] = t['summary'][i]
        kv[qb + '.done'] = t['done'][i]
        for st in f['stages']:
            s = st['kind']
            kv[f'{qb}.{s}.journal'] = st['journal'][i]
            if s == 'kill':
                kv[f'{qb}.kill.kill'] = st['obj'][i]
            else:
                for place, obj in st['places']:
                    kv[f'{qb}.go.{place}'] = obj[i]
        kv[f'{qb}.back.journal'] = t['back_journal'][i]
        kv[f'{qb}.back.back'] = t['back_obj'][i]
        for k in ('idle', 'hint', 'after'):
            kv[f'{db}.{k}'] = t[k][i]
            kv[f'{db}.{k}.ask'] = ('困っていることは？', 'Is something troubling you?')[i]
            kv[f'{db}.{k}.bye'] = ('じゃあ、また', 'See you')[i]
        kv[db + '.q_ask'] = t['ask'][i]
        kv[db + '.q_ask.ok'] = t['ask_ok'][i]
        kv[db + '.q_wait'] = t['wait'][i]
        kv[db + '.q_back'] = t['back'][i]
        kv[db + '.q_back.give'] = t['back_give'][i]
        kv[db + '.q_back.later'] = ('また来る', "I'll come back")[i]
        kv[db + '.q_done'] = t['done_line'][i]
        kv[db + '.q_done.ok'] = ('ありがとう', 'Thank you')[i]
    db = 'dialogue.skyabyss.guest_isle_ferryman'
    kv['entity.skyabyss.guest_isle_ferryman'] = ISLE['name'][i]
    kv[db + '.ask'] = ISLE['ask'][i]
    kv[db + '.ask.cross'] = ISLE['cross'][i]
    kv[db + '.ask.sea'] = ISLE['sea_opt'][i]
    kv[db + '.ask.bye'] = ISLE['bye'][i]
    kv[db + '.sea'] = ISLE['sea'][i]
    return kv


def dump(path, data):
    with open(path, 'w', encoding='utf8', newline='\n') as fh:
        json.dump(data, fh, ensure_ascii=False, indent=2)
        fh.write('\n')


def main():
    for pid, f in FOLK.items():
        dump(os.path.join(RES, 'data', 'skyabyss', 'quest', 'town', 'hamlet_' + pid + '.json'), quest_json(pid, f))
        dump(os.path.join(RES, 'data', 'skyabyss', 'dialogue', 'guest_' + pid + '.json'), dialogue_json(pid, f))
    dump(os.path.join(RES, 'data', 'skyabyss', 'dialogue', 'guest_isle_ferryman.json'), isle_dialogue())
    for lg, i in (('ja_jp', 0), ('en_us', 1)):
        p = os.path.join(RES, 'assets', 'skyabyss_town', 'lang', lg + '.json')
        s = open(p, encoding='utf8').read()
        d = json.loads(s)
        kv = lang(i)
        for k, v in kv.items():
            if k in d:
                old = f'  "{k}": {json.dumps(d[k], ensure_ascii=False)}'
                assert old in s, k
                s = s.replace(old, f'  "{k}": {json.dumps(v, ensure_ascii=False)}', 1)
        ins = ''.join(f'  "{k}": {json.dumps(v, ensure_ascii=False)},\n' for k, v in kv.items() if k not in d)
        s = s.replace('{\n', '{\n' + ins, 1)
        json.loads(s)
        open(p, 'w', encoding='utf8', newline='\n').write(s)
    print('ok', len(FOLK), 'folk')


if __name__ == '__main__':
    main()
