# -*- coding: utf-8 -*-
"""2026-11-02 夕方の声 (店の名札・ペット・地下のダンジョン・わかりにくい文・正式版) の報告と選ぶページ (選んだものは db の choices) と、同じ中身の md を書く。
絵は docs/review/pets_dungeons_1102/img/ (名札の撮影 tools/shots/facility_labels.txt と、乗り物のラフ tools/rough/lowpoly/mounts2/roughs.py)。"""
import html
import json
import os

ROOT = "C:/Users/nisimoto/Desktop/mod"
OUT = os.path.join(ROOT, "docs/review/pets_dungeons_1102")

MOUNT_Q = [
    {"id": "q346", "no": 346, "title": "深淵の乗り物（五章で店に並ぶ）", "img": "mount_abyss",
     "why": "今の見た目は四章（炉の狐）まで。五章からあとに開く見た目が無い。値段の目安は鐘銭 220。",
     "options": [
         {"v": "a", "label": "A 灯り守の大守宮", "sub": "坑道の灯り番に飼われた大きな守宮。平たい体・吸盤の指・肩と腰と尾の先の灯り袋が光る。五章の灯り番の話とつながる", "rec": True},
         {"v": "b", "label": "B 塩殻の甲虫", "sub": "塩の層の大甲虫。六本の脚・前へ突き出た一本角・殻に塩の結晶。今の乗り物に無い虫の形"},
         {"v": "c", "label": "C 骨の大狼", "sub": "骨の層をうろつく大狼。毛の下からのぞく肋・骨の面・目の奥の青い灯。怖さを残す"},
         {"v": "none", "label": "作らない"}]},
    {"id": "q347", "no": 347, "title": "月の乗り物（六章で店に並ぶ）", "img": "mount_moon",
     "why": "六章の月の面の見た目。値段の目安は鐘銭 260。",
     "options": [
         {"v": "a", "label": "A 月の兎", "sub": "長い耳・大きな後ろ足・白銀の毛にクレーターの斑・額の三日月が光る。月の兎は昔からの月の絵", "rec": True},
         {"v": "b", "label": "B 星読みの大猫", "sub": "夜の青の毛に星の点（光る）・房の耳・頬の襟毛・尾の先の三日月が光る。細く速い見た目"},
         {"v": "c", "label": "C 月の大蛙", "sub": "低く幅広い体・上に飛び出た目・クレーターの環の肌・背の三日月の斑が光る"},
         {"v": "none", "label": "作らない"}]},
    {"id": "q348", "no": 348, "title": "空を飛ぶ乗り物", "img": "mount_sky",
     "why": "声「飛べるペットがほしい」。地上の乗り物とは別の一体で、笛で呼んで乗ると空を飛べる。値段の目安は鐘銭 400。",
     "options": [
         {"v": "a", "label": "A 空鱏", "sub": "雲海を泳ぐ大きな鱏。菱形の体と大きな翼・二つの頭びれ・尾の先の鐘の房。背は夕べの青に金の斑、腹は雲の白、翼の先が光る。鞍が平らな背に座りやすい", "rec": True},
         {"v": "b", "label": "B 鐘鳴りの大鳥", "sub": "鐘の国の伝えの白い大鳥。長い首・頭に鐘の形の金の冠・喉の小さな鐘・大きな翼と流れる尾羽。羽ばたくと鐘の音"},
         {"v": "c", "label": "C 雲の竜", "sub": "雲の上に棲む細身の翼竜。空色の鱗・首と背と尾の先の雲の房・膜の翼。二本の後ろ足で立つ"}]},
    {"id": "q349", "no": 349, "title": "飛ぶ乗り物はいつから", "why": "早く飛べると、大陸の道・見どころ・町を通らずに先へ行けてしまう。",
     "options": [
         {"v": "a", "label": "終章「雲海」を越えたら（巡礼者を退けたあと）", "sub": "物語を通したあとのごほうび。大陸の見どころはもう見たあと", "rec": True},
         {"v": "b", "label": "六章（月の核）を越えたら", "sub": "終章の前から飛べる"},
         {"v": "c", "label": "あいだの塔を一度越えたら", "sub": "いちばん遅い。塔のごほうびとして"}]},
    {"id": "q350", "no": 350, "title": "飛び方", "why": "乗っている人の操作。しゃがみは今までどおり降りる。",
     "options": [
         {"v": "a", "label": "見ている向きへ飛ぶ（上を向けば上がる・下を向けば下がる）。跳びのキーで羽ばたく。地面から 40 ブロックまで", "sub": "呼べない所は今の乗り物と同じ（巨神の背・淵・主の部屋）", "rec": True},
         {"v": "b", "label": "同じ操作で、高さの上限なし", "sub": "雲の壁と世界の高さの上限までは行ける"},
         {"v": "c", "label": "滑空だけ（高い所から飛び降りて滑る。上がれない）", "sub": "移動は速いが、上へは行けない"}]},
]

DUNGEON_Q = [
    {"id": "q351", "no": 351, "title": "数と場所", "why": "大陸の地下は今は何も無い（洞窟もマインクラフトの構造物も出さない作り）。",
     "options": [
         {"v": "a", "label": "4 つ。巨神の土地にひとつずつ（岩鯨の丘陵・潮の浜・嵐の高地・灰の荒野）", "sub": "目安 Lv 10・16・22・28。土地の敵と粉に合わせる", "rec": True},
         {"v": "b", "label": "5 つ。4 つ + 鐘の野（都のまわり）の入門の 1 つ", "sub": "入門は Lv 6 ほど。最初のダンジョンで遊び方を覚える"},
         {"v": "c", "label": "2 つ。大きいのを北の丘陵と南の荒野に", "sub": "一つずつを大きく作れる"}]},
    {"id": "q352", "no": 352, "title": "作り方（地下のどこに建てるか）", "why": "大陸の本物の地下に建てると、ほかの組と同じ敵・同じ主を取り合う。巨神の背は組ごとの区画で、ぶつからない。",
     "options": [
         {"v": "a", "label": "入口の大洞窟は大陸の本物の地下、奥は組ごとの写し", "sub": "地図で見える・歩いて下りる地下の広間はみなで通り、層の門から先は巨神の背と同じ区画の仕組みで組ごと。主を倒すと入口の洞窟へ戻る", "rec": True},
         {"v": "b", "label": "全部を大陸の本物の地下に建てる", "sub": "地下がいちばん「ある」感じ。主は順番待ち（倒したあと数分で戻る）、宝箱は人ごと"},
         {"v": "c", "label": "全部を別の次元に（入口だけ地上）", "sub": "作るのは軽いが、地下に何も無いのは今と同じ"}]},
    {"id": "q353", "no": 353, "title": "大きさ",
     "options": [
         {"v": "a", "label": "3 層 + 主の間（30〜45 分）", "sub": "層の終わりに戻り石（倒れたらそこから。抜けて戻っても続きから）", "rec": True},
         {"v": "b", "label": "2 層 + 主の間（20〜30 分）", "sub": "短く遊べる"},
         {"v": "c", "label": "5 層 + 主の間（1 時間ほど）", "sub": "大きな遠征"}]},
    {"id": "q354", "no": 354, "title": "中身の遊び",
     "options": [
         {"v": "a", "label": "探索と戦い。曲がる坑道・鍵と扉・罠・中ボス 1・ダンジョンごとの仕掛け 1 つ", "sub": "仕掛けの例: 水が満ち引きする・暗くて灯りが要る・雷で扉が動く・溶岩の流れが変わる（下の 358〜361）", "rec": True},
         {"v": "b", "label": "戦いの波。部屋ごとに敵の波を越える", "sub": "Wynncraft のダンジョンに近い。探索は少ない"},
         {"v": "c", "label": "謎解きを多め", "sub": "戦いは少なく、仕掛けを解いて進む"}]},
    {"id": "q355", "no": 355, "title": "入り方",
     "options": [
         {"v": "a", "label": "地上の洞窟の口から誰でも入れる。地図に印、近くの町の人の噂でサブクエストが始まる", "sub": "クエストを受けなくても入れる。物語とは関係しない", "rec": True},
         {"v": "b", "label": "町の人のサブクエストを受けると口が開く", "sub": "入る前に話がある"},
         {"v": "c", "label": "鍵の品で入る（店で買う・敵が落とす）", "sub": "Wynncraft のダンジョンの鍵の形"}]},
    {"id": "q356", "no": 356, "title": "繰り返しと報酬",
     "options": [
         {"v": "a", "label": "何度でも。初回の主はそのダンジョンだけの装飾品 1 つ・称号・図鑑。主の封印の箱は一日一度、道中の宝箱は人ごとに 20 分", "sub": "封印の箱はその土地の階級。土地の粉と経験値も", "rec": True},
         {"v": "b", "label": "一度きり（越えると閉じる）"},
         {"v": "c", "label": "何度でも、毎回同じ報酬"}]},
    {"id": "q357", "no": 357, "title": "人数",
     "options": [
         {"v": "a", "label": "1〜4 人。人数で敵と主が強くなる（今の背と同じ式）", "rec": True},
         {"v": "b", "label": "1〜6 人（パーティの上限と同じ）"}]},
    {"id": "q358", "no": 358, "title": "岩鯨の丘陵のダンジョン（Lv 10 ほど）",
     "options": [
         {"v": "a", "label": "鯨骨の坑", "sub": "昔、巨神の骨を掘った坑道。トロッコの道で層を下り、骨の兵が掘り場を守る。主: 骨掘りの大百足（坑の壁から出て潜る）", "rec": True},
         {"v": "b", "label": "崩れた僧院の地下墓所", "sub": "丘の僧院の下。鐘を鳴らすと開く扉と、鳴らすと起きる墓守。主: 鐘の墓守"},
         {"v": "c", "label": "苔の大洞", "sub": "地下の湖と光る苔の大きな洞。主: 苔の大蝦蟇（湖から跳ぶ）"}]},
    {"id": "q359", "no": 359, "title": "潮の浜のダンジョン（Lv 16 ほど）",
     "options": [
         {"v": "a", "label": "潮の洞", "sub": "層ごとに水が満ち引きする海の洞窟（引いたあいだに先の道が出る）。主: 洞の大蛸（腕を一本ずつ切る）", "rec": True},
         {"v": "b", "label": "沈んだ灯台の根", "sub": "灯台の土台を海の底へ下りる。灯りの届く所だけ安全。主: 灯台守の亡霊"},
         {"v": "c", "label": "珊瑚の迷宮", "sub": "伸びる珊瑚の壁の迷路。主: 珊瑚の女王"}]},
    {"id": "q360", "no": 360, "title": "嵐の高地のダンジョン（Lv 22 ほど）",
     "options": [
         {"v": "a", "label": "雷晶の空洞", "sub": "雷を溜める結晶に技を当てて扉を動かす晶の洞。主: 晶の雷蜥蜴（結晶を背負い、満ちると雷を放つ）", "rec": True},
         {"v": "b", "label": "風の民の地下砦", "sub": "風の通り道に押される砦。主: 風の砦の将"},
         {"v": "c", "label": "凍えた氷穴", "sub": "滑る床と氷の柱。主: 霜の大狼"}]},
    {"id": "q361", "no": 361, "title": "灰の荒野のダンジョン（Lv 28 ほど）",
     "options": [
         {"v": "a", "label": "熔けた坑道", "sub": "灰の民の古い坑道。時間で流れの変わる溶岩の道。主: 坑の鍛え巨人（炉を背負う）", "rec": True},
         {"v": "b", "label": "灰の民の地下墓所", "sub": "燠の壺と亡霊の並ぶ墓所。主: 灰の大司祭"},
         {"v": "c", "label": "黒曜の巣", "sub": "火の虫の巣。卵を割らずに進む。主: 熾の女王蟲"}]},
]

RELEASE_Q = [
    {"id": "q362", "no": 362, "title": "正式版の出し方",
     "options": [
         {"v": "a", "label": "次を 0.9.23 の候補版として出し、通しの試遊（終章と塔を 1 人と 4 人）のあとで 1.0.0", "sub": "全段の自己テストは今回流した（下の結果）。人が通していない所が残る", "rec": True},
         {"v": "b", "label": "次をそのまま 1.0.0", "sub": "試遊で出たものは 1.0.1 で直す"},
         {"v": "c", "label": "地下のダンジョンと新しい乗り物を入れてから 1.0.0"}]},
    {"id": "q363", "no": 363, "title": "公開サイトと README", "why": "サイトは 0.9.20 のまま。README・遊び方・サイトに「ベータ版」と古いコマンドの案内が残る。",
     "options": [
         {"v": "a", "label": "1.0 を出すときにまとめて直す（ベータ版の表記・古いコマンドの案内・更新履歴）", "rec": True},
         {"v": "b", "label": "今の 0.9.22 で先に更新する"}]},
]

BOSS_Q = [
    {"id": "q364", "no": 364, "title": "骨掘りの大百足（鯨骨の坑・Lv 10）の形", "img": "boss_centipede", "wide": True,
     "options": [
         {"v": "a", "label": "A 骨殻の大百足", "sub": "骨の甲板を重ねた長い体（14 節）・鶴嘴のような大顎・節ごとの尖った脚。前の三分の一を持ち上げて構える", "rec": True},
         {"v": "b", "label": "B 背骨の百足", "sub": "掘り出された巨神の背骨そのもの。節は椎骨、脚は肋骨、頭は鯨の頭骨と二本の牙、額に坑夫の灯り"},
         {"v": "c", "label": "C 鉱脈の甲百足", "sub": "短く太い体を岩と鉱石の甲で覆う。頭は掘り進む円錐の角、甲の継ぎ目と背の結晶が光る"}]},
    {"id": "q365", "no": 365, "title": "洞の大蛸（潮の洞・Lv 16）の形", "img": "boss_octopus", "wide": True,
     "options": [
         {"v": "a", "label": "A 洞の大蛸", "sub": "ふくらんだ外套と八本の太い腕・大きな目・外套にフジツボ"},
         {"v": "b", "label": "B 殻負いの大蛸", "sub": "珊瑚に覆われた巨大な巻貝の殻を背負い、殻の口から腕を出す。潮殻の土地の「殻」の形の言葉とつながる", "rec": True},
         {"v": "c", "label": "C 灯りの深蛸", "sub": "深い海の蛸。紫の外套に光る斑・腕のあいだの膜・長い二本の捕まえ腕"}]},
    {"id": "q366", "no": 366, "title": "晶の雷蜥蜴（雷晶の空洞・Lv 22）の形", "img": "boss_lizard", "wide": True,
     "options": [
         {"v": "a", "label": "A 背晶の大蜥蜴", "sub": "がっしりした四つ足。背骨に沿って雷の結晶の房が並び、満ちると光が強まって雷を放つ（溜まり具合が見て分かる）", "rec": True},
         {"v": "b", "label": "B 晶鎧の蜥蜴", "sub": "体を結晶の板が鎧のように覆う。首の結晶の襟・尾の先の結晶の棍"},
         {"v": "c", "label": "C 雷角の竜蜥蜴", "sub": "首の長い細身。枝分かれした結晶の角・尾に沿って雷を蓄える結晶の玉"}]},
    {"id": "q367", "no": 367, "title": "坑の鍛え巨人（熔けた坑道・Lv 28）の形", "img": "boss_giant", "wide": True,
     "options": [
         {"v": "a", "label": "A 炉負いの巨人", "sub": "前かがみの玄武岩の巨人。背に大きな炉と煙突（中が光る）、右手は鎚、肌の割れ目から熔けた光", "rec": True},
         {"v": "b", "label": "B 炉胸の巨人", "sub": "胸そのものが開いた炉（火の口が光る）。肩は金床、左手は火ばさみ、右手は鎚"},
         {"v": "c", "label": "C 坑の鍛冶車", "sub": "人の形でない坑道の機械。炉を積んだ車の体と四本の脚、前に二本の鎚の腕"}]},
]

ACC_Q = [
    {"id": "q368", "no": 368, "title": "骨掘りの顎飾り（鯨骨の坑）の形", "img": "acc_bone_pit", "wide": True,
     "why": "鯨骨の坑の主を初めて倒した人に一つ。装飾・レア。効き目の案: 攻撃 +3%、まわりに敵が 3 体以上いると受ける傷 −6%。材質は岩鯨の装備と同じ（鯨骨・青灰の石・水色の光）。",
     "options": [
         {"v": "a", "label": "A 骨の円盤と交わる二本の大顎", "sub": "大百足の鶴嘴の大顎が左右から抱く。真ん中に光る目", "rec": True},
         {"v": "b", "label": "B 大百足の節の短い鎖", "sub": "骨の甲板の節を三つつなぎ、節ごとに小さな脚"},
         {"v": "c", "label": "C 鶴嘴の形の大顎一本", "sub": "反った骨の刃を一本、根に光る目と骨の留め具"}]},
    {"id": "q369", "no": 369, "title": "殻負いの螺貝（潮の洞）の形", "img": "acc_tide_cave", "wide": True,
     "why": "潮の洞の主を初めて倒した人に一つ。装飾・レア。効き目の案: 最大マナ +10、水の中で息が 2 倍続く。材質は潮殻の装備と同じ（真珠層・珊瑚・桃の光）。",
     "options": [
         {"v": "a", "label": "A 珊瑚の生えた巻貝と真珠", "sub": "大蛸の殻を小さくした巻貝。殻の口から真珠がのぞく", "rec": True},
         {"v": "b", "label": "B 真珠を抱いて巻く蛸の腕", "sub": "一本の腕が真珠を巻く。吸盤の並び"},
         {"v": "c", "label": "C フジツボの殻の灯籠", "sub": "殻の窓から桃の光がもれる小さな灯籠"}]},
    {"id": "q370", "no": 370, "title": "背晶の欠片（雷晶の空洞）の形", "img": "acc_storm_hollow", "wide": True,
     "why": "雷晶の空洞の主を初めて倒した人に一つ。装飾・エピック。効き目の案: 技の待ち −4%、雷の粉の効き +10%。材質は雷翼の装備と同じ（銅・紺の鋼・白青の光）。",
     "options": [
         {"v": "a", "label": "A 銅の台に雷の結晶の房", "sub": "大蜥蜴の背の房を小さくした三本の結晶"},
         {"v": "b", "label": "B 紺の鱗から生える結晶", "sub": "一枚の鱗から一本の結晶"},
         {"v": "c", "label": "C 角の枠に浮かぶ菱形の結晶", "sub": "大蜥蜴の二本の角の枠のあいだに、浮かぶ菱形の結晶", "rec": True}]},
    {"id": "q371", "no": 371, "title": "炉の熾（熔けた坑道）の形", "img": "acc_molten_mine", "wide": True,
     "why": "熔けた坑道の主を初めて倒した人に一つ。装飾・エピック。効き目の案: 体力 +24、燃える傷 −30%。材質は余燼の装備と同じ（玄武岩の黒い板・鍛冶の鉄・橙の光）。",
     "options": [
         {"v": "a", "label": "A 火の口の光る小さな炉", "sub": "巨人の背の炉を小さくした煉瓦の炉と煙突", "rec": True},
         {"v": "b", "label": "B 割れ目の光る鎚の頭", "sub": "黒い玄武岩の鎚の頭、割れ目が熔けて光る"},
         {"v": "c", "label": "C 玄武岩の輪と熾の玉", "sub": "輪の真ん中に熾の玉"}]},
]

DUNGEON_SHOTS = [("dg_bone_pit_mouth", "鯨骨の坑の入口（大陸の岩の口）"), ("dg_bone_pit_dig", "鯨骨の坑 一層目の掘り場"),
                 ("dg_bone_pit_lair", "骨掘りの大百足の間"), ("dg_tide_cave_pool", "潮の洞 一層目の潮だまりの大洞"),
                 ("dg_tide_cave_lair", "殻負いの大蛸の間"), ("dg_storm_hollow_geode", "雷晶の空洞 二層目の晶洞"),
                 ("dg_storm_hollow_lair", "背晶の大蜥蜴の間"), ("dg_molten_mine_hall", "熔けた坑道の入口の間（層の門）"),
                 ("dg_molten_mine_lair", "炉負いの巨人の間")]

DUNGEON_DONE = [
    "四つとも作った（設計書 55 の 5 章）: 大陸の入口（道から見える岩の口・階段の坑・本物の地下の入口の間と層の門）、組ごとの写し（2 層 + 主の間）、部屋に寄ると出る土地の敵、鍵持ちの敵の鍵の扉、中ボス（土地の名のある強敵）の扉、罠（落石・骨の棘・潮・雷・蒸気）、戻り石と続きから、主を倒すと奥の光から入口の間へ。",
    "仕掛け: 鯨骨の坑はトロッコで下の層へ、潮の洞は 70 秒で満ち引きする低い抜け道、雷晶の空洞は結晶に 3 回当てて扉を開ける（主の間の結晶は大蜥蜴の溜まりを抜く）、熔けた坑道は 20 秒ごとに入れ替わる溶岩の渡り。",
    "主 4 体（選んだ形）: 骨掘りの大百足（噛む・薙ぐ・潜って掘り穴から突進・天井崩し）、殻負いの大蛸（叩きつけ・薙ぎ・墨・殻ごもり、体力が減るごとに腕が落ちる）、背晶の大蜥蜴（噛む・尾・突進、溜まると雷）、炉負いの巨人（振り下ろし・横薙ぎ・熔けた足踏み・炉の噴き上げ）。どれも予告の輪・技の名前・体力の帯つき、人数で強くなる。",
    "報酬: 経験値・鐘銭・土地の粉、一日一度の主の封印の箱、初めての称号 4 つ（坑の骨を鎮めた者ほか）、敵の図鑑。町の案内人の「噂を聞く」で始まる噂の頼み 4 つ。",
    "自己テスト `dungeons` が通る（入口・写し・扉・結晶・満ち引き・溶岩・罠・中ボス・四体の主・続きから・噂の頼み）。手ざわり（主の強さ・罠の傷）は遊んで確かめてほしい。",
]

PICKS = [
    "乗り物: 深淵 A 灯り守の大守宮・月 B 星読みの大猫・空 A 空鱏。飛ぶのは終章を越えたら、見ている向きへ・地面から 40 まで（346〜350）。→ 入れた（下の「乗り物」）。",
    "地下のダンジョン: 4 つ（巨神の土地にひとつずつ）、入口の大洞窟は本物の地下で奥は組ごとの写し、2 層 + 主の間、探索と戦い + 仕掛け 1、洞窟の口から誰でも、何度でも、1〜6 人、四つとも案 A（351〜361）。→ 作った（下の「地下のダンジョン（作った）」）。",
    "主の形: 骨殻の大百足・殻負いの大蛸・背晶の大蜥蜴・炉負いの巨人（364 A・365 B・366 A・367 A）。→ 作り込んで入れた。",
    "装飾品の形: 骨の円盤と交わる二本の大顎・珊瑚の生えた巻貝と真珠・角の枠に浮かぶ菱形の結晶・火の口の光る小さな炉（368 A・369 A・370 C・371 A）。→ 作って主の報酬に入れた。",
    "正式版: 0.9.23 を候補版 → 通しの試遊 → 1.0.0、サイトと README は 1.0 のときまとめて（362・363）。→ docs/release.md の 7 章に書いた。",
]

MOUNT_SHOTS = [("m_abyss_gecko_run_a", "灯り守の大守宮に乗って駆ける"), ("m_star_lynx_run_a", "星読みの大猫に乗って駆ける"),
               ("m_sky_ray_climb", "空鱏: 上を向いて飛ぶと上がる"), ("m_sky_ray_hover", "空鱏: 押さなければ宙に止まる"), ("m_row", "十の見た目の並び")]

LABEL_SHOTS = [("label_cap_inn_front", "都の宿（宿「鐘の宿」）"), ("label_cap_trade_front", "都の取引所（題と中身の行）"),
               ("label_cap_storage_front", "都の預かり所"), ("label_cap_apothecary_side", "都の薬師の店を通りから"),
               ("label_whale_inn_front", "鯨見の町の宿（旅の宿）"), ("label_whale_shrine_front", "鯨見の町の社"),
               ("label_whale_travel_front", "鯨見の町の旅の館"), ("label_whale_training_front", "鯨見の町の訓練場（職の間）")]

LABEL_DONE = [
    "前の名札は戸の前の一つ外・床から 4 上に 1 枚。向きが人へ回るので、横から見ると壁・旗・吊り看板にめり込んでいた（都と町の 38 枚のうち 36 枚）。",
    "置き場を探す形に: 戸の前から通りの側へ 0〜6・上下 −1〜6・横へ −6〜6 の中で、名札がどの向きを向いてもブロックに触れず、下に人が通れる空きがある、いちばん戸に近く低い所（`opening/site/FacilityLabels`）。38 枚すべてで空いた所が見つかる（自己テスト `facility labels`）。",
    "字は縦の軸だけで回る（近くで見上げても寝ない）・明るさ固定で夜も読める・見える距離 64。",
    "長い名前を「題（大きく）＋ 中身の行（小さく）」に分けた（例: 市 / 道具・食料・武具・素材）。",
    "名前の直し: 土地の町の宿が都の「鐘の宿」になっていた → 旅の宿（町の碑と同じ）。止まり木 / 巨神の子を呼ぶ・乗り物（旅の番の店）。鍛冶場 / 鍛冶師・封切り師。社 / 鍵の間・転生・誓い。訓練場 / 職を替える・技の振り直し・稽古。取引所 / 旅人どうしの売り買い。",
    "都の訓練場（職の間）に名札が無かった → 西の門の上に足した。前の版で建った都と町にも、見回りが新しい名札を置き直す。",
]

MOUNT_DONE = [
    "ペット = 笛で呼ぶ乗り物（`mount/`。お供は 54 で「作らない」になっている）。",
    "体力の段をやめた: 体力はいつも 60（前の最大）。前に買った体力の段は、入ったときに鐘銭で返す（一度だけ。`upgrade/PlayerUpgrade` の段 2）。店の行は速さと跳びの二つに。",
    "速さの段を 5 → 8 に: 段 5 の 0.35（馬の最上位）の先に 0.38・0.41・0.44（鐘銭 300・420・600）。",
    "自己テスト `mount`（買う・段・返金・二度払わない）が通る。手ざわりの速さは遊んで確かめてほしい。",
    "選んだ三つを入れた: 灯り守の大守宮（五章で並ぶ・鐘銭 220）・星読みの大猫（月へ渡ると・260）・空鱏（終章のあと・400）。形・動き（待機・歩き・駆け・跳び）・光る所。メッシュは閉じている（`mesh_audit` 0 件）。店の見た目の一覧は 10 になり、ホイールで送る。",
    "空鱏は飛ぶ: 乗ると見ている向きへ飛ぶ（上を向けば上がる）・跳びのキーで羽ばたく・地面から 40 ブロックより上へは上がらない・降りると地面へ下りる（落下では傷つかない）。速さの段がそのまま効く（地上の 1.2 倍ほど）。",
]

TEXT_DONE = [
    "目的の行の形をそろえた: 月・裏ボス・終章の歩きが「〜する」「〜の欠片」だった → ほかの章と同じ「〜しよう」（55 行）。",
    "誤り: 塩の蔵の「潮が引けば喉は乾いて歩ける」→「入口の通路は乾いて歩ける」。",
    "店の案内の誤り: 道の固焼きは「薬師が誂える」→ 市の食料屋。空石の剣は売っていない → 空石の鱗の胴。避雷針は作れない → 市の素材屋で手に入れる。熾火の棒（素材屋）→（市の素材屋）。",
    "なくなった仕組みの言葉: 月の欠片の「修理」（すり減らない）、乗り物の画面の題「厩舎」→「乗り物」、笛の案内「巨神を呼ぶ」→「巨神の子を呼ぶ」、吟遊詩人の頼みの「止まり木の番」→「止まり木の旅の番」（名札と同じ名）。",
    "技の木 v2 と合わない言葉: 奥義（型の要）の稽古「Lv 20 で要が開いた」→ 型に 20 点で開く奥義。稽古の「点」→ スキルポイント（SP）。鍛え手の溜まるもの「焼き入れが満ち直し」→「焼き入れがかけ直され」。",
    "係の人の言う品と店の品が違った: 道具屋「灯り、縄、寝袋、地図に舟」→「舟に方位盤、海図、斧につるはし、釣り竿」（置ける品はもう売らない）、武具屋の「剣」「砥石」（売っていない。砥石は鍛冶場）を外した、食料屋の「堅焼き」→「固焼き」（品の名と同じ）。",
    "目的の帯（幅 230 px）からはみ出した行を短くした（避雷針の行と英語 7 行。月の欠片の英語は元の名詞の形に）。帯の幅の検査 `check_band_width` も 0 件。",
    "記号: 塔の知らせの半角の「!」→「！」。旅嚢の「mod の品」→「旅嚢に入る品」。英語（en_us）も同じ意味に合わせた。`check_lang_keys`・`check_text_fit` は 0 件。",
]

RELEASE_DONE = [
    ("直した", "舞台の主の卵 8 つ（潮招き・潮殻の心臓・雷獣・雷翼の心臓・灰の鍛え手・余燼の心臓・心臓の番人・側の大きな目）に持ち物の形が無く、創造の欄で紫と黒の箱になっていた → 足した。"),
    ("直した", "技の木 v2 の K には星読み・深淵術師の木のつまみが無いのに、その木が開くと「技の画面に新しい札がある」と稽古が始まり、終わらない頼みが残った → この二つの稽古は始めない（自己テスト `skill lesson` を淵の木で確かめる形に）。"),
    ("直した", "乗り物とラフの道具が、jar の外へ移した雷獣の箱の geo を読んで止まっていた → 移した先を読む。"),
    ("確かめ", "全段の自己テストを新しい大陸の世界で流した: 名前の出る段 216 が PASSED、11 が FAILED。11 件は、今回の文で目的の帯からはみ出した 8 行（直した）と、0.9.22 で荷から松明を外したのに自己テストが松明を数えていた 3 件（`ch1 p6`・`ch1`・`storm chapter (sites)`。自己テストを今の荷に合わせた）。直したあとの段の流し直しは HANDOFF。"),
    ("残り", "人が通していない所: 終章の通し・あいだの塔・巡り路・大陸の大事件・4 人でのマルチ。数値（神器の能力・溜まるもの・塔・釣り・箱の確率）は遊んで詰める。"),
    ("残り", "配布物: README・遊び方・サイトが「ベータ版」、遊び方に古いコマンド（`/skyabyss titan` ほか）、サイトは 0.9.20（363）。"),
    ("残り", "リモートの wip ブランチ 79 本の削除は持ち主の作業（HANDOFF）。jar 74 MB のうち自己テストのコードが 2 割（別のソースセットに分けるのは大きい直し）。"),
    ("残り", "都の空き家（旧厩舎・地図屋の屋根裏・空見の館）は碑で「空き家」と出るだけ。地下のダンジョンの入口や新しい施設に使える。"),
]


def esc(s):
    return html.escape(s, quote=False)


def md_code(s):
    return s


def write_md():
    L = ["# 2026-11-02 夕方の声: 店の名札・ペット・地下のダンジョン・文・正式版", "",
         "声（2026-11-02 夕方）: 店の浮いている名札の名前と位置（「ブロックにめり込んで全部上手く見えない」）、ペットの種類・飛べるペット・体力はいらない・速さ、地下に大きめのダンジョン（主を倒すと入口へ戻る、サブクエストのような遊び）、わかりにくい文、正式版に向けた調整。", "",
         "選ぶページ（`page.html`、選んだものは db の `choices`）とこの md は `tools/gen/review/pets_dungeons_page.py` が書く。未決の 346〜363（[00](../../design/00_decisions_needed.md)）と同じ中身。", ""]
    for head, items in (("店の名札（直した）", LABEL_DONE), ("乗り物（直した）", MOUNT_DONE), ("地下のダンジョン（作った）", DUNGEON_DONE), ("文（直した）", TEXT_DONE)):
        L += ["## " + head, ""] + ["- " + x for x in items] + [""]
    L += ["## 選んだ答え（2026-11-02）", ""] + ["- " + x for x in PICKS] + [""]
    for head, qs in (("乗り物の形と飛び方（346〜350。答えた）", MOUNT_Q), ("地下のダンジョン（351〜361。答えた）", DUNGEON_Q), ("ダンジョンの主の形（364〜367。答えた）", BOSS_Q), ("ダンジョンの装飾品の形（368〜371。答えた）", ACC_Q),
                     ("正式版（362〜363。答えた）", RELEASE_Q)):
        L += ["## " + head, ""]
        if qs is DUNGEON_Q:
            L += ["決まっていること: 一番奥の主を倒すと洞窟の入り口へ戻る。物語とは関係しないサブクエストのような遊び。主と敵の形は、案が決まったら 3 案のラフを出す。", ""]
        for q in qs:
            L += ["### %d. %s" % (q["no"], q["title"]), ""]
            if q.get("why"):
                L += [q["why"], ""]
            if q.get("img"):
                L += ["絵: `img/%s_{a,b,c}.jpg`" % q["img"], ""]
            for o in q["options"]:
                L.append("- **%s**%s%s" % (o["label"], "（推奨）" if o.get("rec") else "", "。" + o["sub"] if o.get("sub") else ""))
            L.append("")
    L += ["## 正式版に向けて見つけたもの", ""] + ["- %s: %s" % (k, v) for k, v in RELEASE_DONE] + [""]
    with open(os.path.join(OUT, "index.md"), "w", encoding="utf-8", newline="\n") as f:
        f.write("\n".join(L))


def items(lst):
    return "".join("<li>%s</li>" % inline(x) for x in lst)


def inline(s):
    out, code = [], False
    for part in s.split("`"):
        out.append(("<code>%s</code>" % esc(part)) if code else esc(part))
        code = not code
    return "".join(out)


def write_html():
    qs = []
    for group, lst in (("mount", MOUNT_Q), ("dungeon", DUNGEON_Q), ("boss", BOSS_Q), ("acc", ACC_Q), ("release", RELEASE_Q)):
        for q in lst:
            d = dict(q)
            d["group"] = group
            qs.append(d)
    mshots = "".join('<figure><img src="img/%s.jpg" alt="%s" loading="lazy" width="960" height="540"><figcaption>%s</figcaption></figure>' % (n, esc(c), esc(c))
                     for n, c in MOUNT_SHOTS)
    shots = "".join('<figure><img src="img/%s.jpg" alt="%s" loading="lazy" width="960" height="540"><figcaption>%s</figcaption></figure>' % (n, esc(c), esc(c))
                    for n, c in LABEL_SHOTS)
    rel = "".join('<li><span class="tag tag-%s">%s</span><span>%s</span></li>' % ({"直した": "done", "確かめ": "check", "残り": "open"}[k], k, inline(v))
                  for k, v in RELEASE_DONE)
    dshots = "".join('<figure><img src="img/%s.jpg" alt="%s" loading="lazy" width="960" height="540"><figcaption>%s</figcaption></figure>' % (n, esc(c), esc(c))
                     for n, c in DUNGEON_SHOTS)
    page = (TEMPLATE.replace("%%DUNGEON_DONE%%", items(DUNGEON_DONE)).replace("%%DSHOTS%%", dshots).replace("%%LABEL_DONE%%", items(LABEL_DONE)).replace("%%MOUNT_DONE%%", items(MOUNT_DONE)).replace("%%TEXT_DONE%%", items(TEXT_DONE))
            .replace("%%PICKS%%", items(PICKS)).replace("%%SHOTS%%", shots).replace("%%MSHOTS%%", mshots).replace("%%RELEASE%%", rel).replace("%%DATA%%", json.dumps(qs, ensure_ascii=False)))
    with open(os.path.join(OUT, "page.html"), "w", encoding="utf-8", newline="\n") as f:
        f.write(page)


TEMPLATE = r"""<title>名札・乗り物・地下の案 11/02</title>
<meta name="claude:viewport-fit-sides" content="cover">
<link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=DotGothic16&family=Zen+Antique&display=swap">
<style>
/* Layout: one reading column — what was fixed (labels, mounts, text) with proof shots, then the choices (mount roughs, dungeon plan, release), then the pre-1.0 list */
:root {
  --bg: #eef0f2; --panel: #ffffff; --fg: #1c2128; --soft: #58606c; --line: #cfd5dc;
  --accent: #1f6f8b; --gold: #8a5d0c; --pick: #e3f1f6; --done: #2f7a3d; --open: #a3523a;
  --display: "Zen Antique", "Hiragino Mincho ProN", "Yu Mincho", serif;
  --body: "DotGothic16", "Hiragino Sans", "Yu Gothic", system-ui, sans-serif;
}
@media (prefers-color-scheme: dark) { :root:not([data-theme="light"]) {
  --bg: #11151a; --panel: #1a2027; --fg: #e6e2d8; --soft: #a5adb8; --line: #2c3540;
  --accent: #7cc8e0; --gold: #efc25c; --pick: #18323b; --done: #86d08f; --open: #ef9f86; color-scheme: dark } }
:root[data-theme="dark"] {
  --bg: #11151a; --panel: #1a2027; --fg: #e6e2d8; --soft: #a5adb8; --line: #2c3540;
  --accent: #7cc8e0; --gold: #efc25c; --pick: #18323b; --done: #86d08f; --open: #ef9f86; color-scheme: dark }
body { background: var(--bg); color: var(--fg); font-family: var(--body); line-height: 1.75;
  padding-left: max(16px, env(safe-area-inset-left, 0px)); padding-right: max(16px, env(safe-area-inset-right, 0px)); padding-block: 28px 72px; }
main { max-width: 820px; margin: 0 auto; display: grid; gap: 34px; }
h1, h2 { font-family: var(--display); font-weight: 400; text-wrap: balance; margin: 0; }
h1 { font-size: 1.9rem; letter-spacing: .04em; }
h2 { font-size: 1.35rem; color: var(--gold); border-bottom: 1px solid var(--line); padding-bottom: 6px; }
h3 { font-size: 1rem; margin: 0; font-weight: 400; }
p { margin: 0; }
.lede { color: var(--soft); }
section { display: grid; gap: 14px; min-width: 0; }
ul { margin: 0; padding-left: 1.2em; display: grid; gap: 5px; }
code { font-size: .88em; background: var(--pick); padding: 0 4px; border-radius: 3px; overflow-wrap: anywhere; }
.shots { display: grid; grid-template-columns: repeat(auto-fill, minmax(min(100%, 360px), 1fr)); gap: 12px; }
figure { margin: 0; display: grid; gap: 4px; min-width: 0; }
figure img { width: 100%; height: auto; border-radius: 4px; border: 1px solid var(--line); background: #000; }
figcaption { color: var(--soft); font-size: .85rem; }
.qs { display: grid; gap: 12px; }
.q { background: var(--panel); border: 1px solid var(--line); border-radius: 6px; padding: 14px 16px; display: grid; gap: 9px; min-width: 0; }
.q .head { display: flex; flex-wrap: wrap; gap: 4px 12px; align-items: baseline; }
.q .no { color: var(--soft); font-size: .85rem; font-variant-numeric: tabular-nums; }
.q .why { color: var(--soft); font-size: .92rem; }
.pics { display: grid; grid-template-columns: repeat(auto-fill, minmax(min(100%, 230px), 1fr)); gap: 8px; }
.pics figure img { aspect-ratio: 688 / 568; object-fit: cover; }
.pics.wide { grid-template-columns: 1fr; }
.pics.wide figure img { aspect-ratio: 1036 / 300; object-fit: contain; }
.opts { display: grid; gap: 6px; }
.opt { display: flex; gap: 10px; align-items: flex-start; border: 1px solid var(--line); border-radius: 5px; padding: 7px 10px; cursor: pointer; }
.opt input { margin-top: 6px; accent-color: var(--accent); flex: none; }
.opt.on { background: var(--pick); border-color: var(--accent); }
.opt span { min-width: 0; }
.opt small { display: block; color: var(--soft); font-size: .88rem; }
.rec { color: var(--gold); font-size: .82rem; margin-left: 6px; }
textarea { width: 100%; box-sizing: border-box; font: inherit; color: var(--fg); background: var(--bg); border: 1px solid var(--line); border-radius: 4px; padding: 6px 8px; min-height: 2.2em; }
.row { display: flex; gap: 10px; align-items: center; flex-wrap: wrap; }
button { font: inherit; background: var(--accent); color: var(--bg); border: 0; border-radius: 4px; padding: 5px 14px; cursor: pointer; }
button.ghost { background: transparent; color: var(--accent); border: 1px solid var(--accent); }
button:focus-visible, textarea:focus-visible, .opt:focus-within { outline: 2px solid var(--gold); outline-offset: 2px; }
.state { color: var(--soft); font-size: .85rem; }
.bar { position: sticky; top: env(safe-area-inset-top, 0px); z-index: 5; background: var(--bg); border-bottom: 1px solid var(--line); padding-block: 8px; display: flex; flex-wrap: wrap; gap: 8px 16px; align-items: center; }
body::before { content: ""; position: fixed; inset: 0 0 auto; height: env(safe-area-inset-top, 0px); z-index: 10; background: var(--bg); }
.count { font-variant-numeric: tabular-nums; color: var(--soft); font-size: .9rem; }
.fixed { border-left: 3px solid var(--line); padding-left: 12px; color: var(--soft); font-size: .92rem; }
.rel { list-style: none; padding: 0; gap: 8px; }
.rel li { display: flex; gap: 10px; align-items: baseline; }
.rel li > span:last-child { min-width: 0; }
.tag { flex: none; font-size: .78rem; border: 1px solid currentColor; border-radius: 3px; padding: 0 6px; letter-spacing: .06em; }
.tag-done { color: var(--done); } .tag-check { color: var(--accent); } .tag-open { color: var(--open); }
@media (prefers-reduced-motion: reduce) { * { scroll-behavior: auto !important; } }
</style>

<main>
  <header style="display:grid;gap:8px">
    <h1>名札・乗り物・地下の案</h1>
    <p class="lede">2026-11-02 夕方の声への返事です。名札・乗り物の体力と速さ・わかりにくい文は直しました。選んでもらった乗り物は入れ、ダンジョンと正式版の答えは設計書に書きました。主の形（364〜367）も選んでもらったので、地下のダンジョンを四つ作りました（撮影つき）。装飾品の形（368〜371）も選んでもらったので、作って主の報酬に入れました。今選んでほしいものはありません。おすすめは「おすすめ」の印の案です。</p>
  </header>

  <section>
    <h2>選んだ答え（11/02）</h2>
    <ul>%%PICKS%%</ul>
  </section>

  <section>
    <h2>店の浮いている名札（直した）</h2>
    <ul>%%LABEL_DONE%%</ul>
    <div class="shots">%%SHOTS%%</div>
  </section>

  <section>
    <h2>乗り物（直した分と、入れた三つ）</h2>
    <ul>%%MOUNT_DONE%%</ul>
    <div class="shots">%%MSHOTS%%</div>
  </section>

  <div class="bar">
    <span class="count" id="count">選んだ数 0 / 0</span>
    <button type="button" id="allrec">まだ選んでいないものを、おすすめで保存</button>
    <span class="state" id="dbstate">選ぶと保存されます（この画面を開いた人みなで同じ記録）。</span>
  </div>

  <section>
    <h2>乗り物の新しい形と飛び方（346〜350）</h2>
    <p class="fixed">絵は 4 方向（斜め前・横・乗った姿・斜め上の後ろ）。左の黄色い目盛りが人の背（1.8 ブロック）。形は乗り物の低ポリの部品で作ったラフで、選んだ案を作り込み（動き・絵・骨）、ゲームの中で撮ってから入れます。</p>
    <div class="qs" data-group="mount"></div>
  </section>

  <section>
    <h2>地下のダンジョン（351〜361）</h2>
    <p class="fixed">決まっていること: 一番奥の主を倒すと洞窟の入り口へ戻る。物語とは関係しないサブクエストのような遊び。主と敵の形は、案が決まったら 3 案のラフを出します。</p>
    <div class="qs" data-group="dungeon"></div>
  </section>

  <section>
    <h2>地下のダンジョン（作った）</h2>
    <ul>%%DUNGEON_DONE%%</ul>
    <div class="shots">%%DSHOTS%%</div>
  </section>

  <section>
    <h2>ダンジョンの装飾品の形（368〜371。答えた）</h2>
    <p class="fixed">選んだ形（A・A・C・A）で作り、主を初めて倒した人に一つずつ渡すようにしました。体に付けた姿（胸・右の腰・左の腰・背の帯）と持ち物欄の絵もゲームの中で確かめました。</p>
    <div class="qs" data-group="acc"></div>
  </section>

  <section>
    <h2>ダンジョンの主の形（364〜367。答えた）</h2>
    <p class="fixed">選んだ形で作り込み、動き（予告と隙）と一緒に入れました。</p>
    <div class="qs" data-group="boss"></div>
  </section>

  <section>
    <h2>わかりにくい文（直した）</h2>
    <ul>%%TEXT_DONE%%</ul>
  </section>

  <section>
    <h2>正式版に向けて（362〜363 と一覧）</h2>
    <ul class="rel">%%RELEASE%%</ul>
    <div class="qs" data-group="release"></div>
  </section>
</main>

<script>
const QUESTIONS = %%DATA%%;
const picks = {};
let db = null;
const stateEl = document.getElementById("dbstate");
const countEl = document.getElementById("count");

function card(q) {
  const c = document.createElement("div"); c.className = "q";
  const head = document.createElement("div"); head.className = "head";
  const n = document.createElement("span"); n.className = "no"; n.textContent = String(q.no); head.append(n);
  const h = document.createElement("h3"); h.textContent = q.title; head.append(h);
  c.append(head);
  if (q.why) { const w = document.createElement("p"); w.className = "why"; w.textContent = q.why; c.append(w); }
  if (q.img) {
    const pics = document.createElement("div"); pics.className = q.wide ? "pics wide" : "pics";
    for (const o of q.options) {
      if (o.v === "none") continue;
      const f = document.createElement("figure");
      const im = document.createElement("img"); im.src = "img/" + q.img + "_" + o.v + ".jpg"; im.alt = o.label; im.loading = "lazy";
      if (q.wide) { im.width = 1036; im.height = 300; } else { im.width = 688; im.height = 568; }
      const cap = document.createElement("figcaption"); cap.textContent = o.label;
      f.append(im, cap); pics.append(f);
    }
    c.append(pics);
  }
  const opts = document.createElement("div"); opts.className = "opts";
  const cur = picks[q.id] || {};
  for (const o of q.options) {
    const lab = document.createElement("label"); lab.className = "opt" + (cur.pick === o.v ? " on" : "");
    const inp = document.createElement("input"); inp.type = "radio"; inp.name = q.id; inp.id = q.id + "_" + o.v; inp.value = o.v; inp.checked = cur.pick === o.v;
    inp.addEventListener("change", () => save(q.id, o.v, null));
    const t = document.createElement("span"); t.textContent = o.label;
    if (o.rec) { const r = document.createElement("span"); r.className = "rec"; r.textContent = "おすすめ"; t.append(r); }
    if (o.sub) { const s = document.createElement("small"); s.textContent = o.sub; t.append(s); }
    lab.append(inp, t); opts.append(lab);
  }
  c.append(opts);
  const ta = document.createElement("textarea"); ta.id = q.id + "_note"; ta.placeholder = "ひとこと（あれば）"; ta.value = cur.note || "";
  const row = document.createElement("div"); row.className = "row";
  const b = document.createElement("button"); b.type = "button"; b.className = "ghost"; b.textContent = "ひとことを保存";
  const st = document.createElement("span"); st.className = "state"; st.textContent = cur.pick ? "保存済み" : "";
  b.addEventListener("click", () => save(q.id, (picks[q.id] || {}).pick || "", ta.value, st));
  row.append(b, st);
  c.append(ta, row);
  return c;
}

function render() {
  for (const box of document.querySelectorAll(".qs")) {
    const g = box.dataset.group;
    box.textContent = "";
    for (const q of QUESTIONS) if (q.group === g) box.append(card(q));
  }
  const done = QUESTIONS.filter(q => (picks[q.id] || {}).pick).length;
  countEl.textContent = "選んだ数 " + done + " / " + QUESTIONS.length;
}

async function save(id, pick, note, st) {
  const prev = picks[id] || {};
  picks[id] = { pick, note: note === null ? (prev.note || "") : note };
  render();
  if (!db) { stateEl.textContent = "この画面では保存できません（選んだものはチャットで伝えてください）。"; return; }
  try {
    await db.collection("choices").doc(id).set({ pick: picks[id].pick, note: picks[id].note, at: new Date().toISOString() });
    if (st) st.textContent = "保存しました";
  } catch (e) {
    stateEl.textContent = "保存できませんでした: " + (e && e.message ? e.message : "不明");
  }
}

document.getElementById("allrec").addEventListener("click", async () => {
  const todo = QUESTIONS.filter(q => !(picks[q.id] || {}).pick);
  if (!todo.length) { stateEl.textContent = "どれも選んであります。"; return; }
  for (const q of todo) {
    const rec = q.options.find(o => o.rec);
    if (rec) await save(q.id, rec.v, null);
  }
  if (db) stateEl.textContent = todo.length + " 件をおすすめで保存しました。";
});

render();
(async () => {
  try { db = await window.claude?.use?.("db"); } catch (e) { db = null; }
  if (!db) { stateEl.textContent = "この画面では保存できません（選んだものはチャットで伝えてください）。"; return; }
  const timer = setTimeout(() => { stateEl.textContent = "記録の読み込みに時間がかかっています。選ぶと保存は試みます。"; }, 10000);
  db.collection("choices").onSnapshot((snap) => {
    clearTimeout(timer);
    for (const d of snap.docs) { const v = d.data() || {}; picks[d.id] = { pick: String(v.pick || ""), note: String(v.note || "") }; }
    stateEl.textContent = "選ぶと保存されます（この画面を開いた人みなで同じ記録）。";
    const active = document.activeElement && document.activeElement.tagName === "TEXTAREA";
    if (!active) render();
  }, () => { clearTimeout(timer); stateEl.textContent = "記録を読めませんでした。選んだものはチャットで伝えてください。"; });
})();
</script>
"""

if __name__ == "__main__":
    write_md()
    write_html()
    print(len(MOUNT_Q) + len(DUNGEON_Q) + len(BOSS_Q) + len(RELEASE_Q), "questions")
