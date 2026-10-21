#!/usr/bin/env python3
"""都の施設 (docs/design/35 の F2・F3) の会話・店の品ぞろえ・文を書く。

  python tools/gen_facility_content.py

書くもの (毎回すべて書き直す。手で直さないこと):
  data/skyabyss/dialogue/attendant_<役>.json      係の人 15 人の会話 (初めての一言 → できることの選択肢)
  data/skyabyss/shop/{tool_shop,apothecary,black_market}.json   店の品ぞろえ (鐘銭と巨神の素材。エメラルドは使わない)
  assets/skyabyss/models/item/bell_coin.json      鐘銭の手に持つ形
  assets/skyabyss_fac/lang/{ja_jp,en_us}.json     文 (係の人の名前・会話・店とギルドの画面・知らせ・噂)
会話の結果 {"type": "facility", "open": ...} は facility.FacilityActions が受ける。
"""
import json
import os

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "src", "main", "resources")
DIALOGUE = os.path.join(RES, "data", "skyabyss", "dialogue")
SHOP = os.path.join(RES, "data", "skyabyss", "shop")
LANG = os.path.join(RES, "assets", "skyabyss_fac", "lang")

JA = {}
EN = {}


def t(key, ja, en):
    JA[key] = ja
    EN[key] = en


# ====================================================================== 係の人
# 役: (日本語の名前, 英語の名前, 初めての一言 (ja, en), 選択肢の束)
# 選択肢: (id, 種類, 結果 (do) か None, 飛び先の節 (talk の本文) か None, 文 ja, 文 en, 本文 ja, 本文 en)
def fac(open_, **kw):
    d = {"type": "facility", "open": open_}
    d.update(kw)
    return d


ROLES = {
    "guild_clerk": ("組合の受付の書記", "Guild Clerk",
                    ("鯨追いの組合へようこそ。ここではギルドを作るか、入る申請を出せます。言伝ての板の頼みも、ここで見られますよ。",
                     "Welcome to the Whale-Chasers' Hall. You can found a guild here, or apply to join one. The message board's errands are here too."),
                    [("guild", "talk", [fac("guild")], None, "ギルドの窓口 (作る・入る・抜ける)", "Guild desk (found, join, leave)", None, None),
                     ("board", "talk", [{"type": "open_board"}], None, "言伝ての板を見る", "Look at the message board", None, None),
                     ("about", "talk", None, "about", "ギルドとは", "What is a guild?",
                      "ギルドは旅人が作る集まりです。旗の色を選んで名前を付ければ、三十人まで入れます。名札にギルドの印が出ますよ。",
                      "A guild is a fellowship travellers make themselves. Pick banner colours and a name, and up to thirty may join. Its tag shows on your name plate.")]),
    "smith": ("鍛冶師", "Smith",
              ("鍛冶場だ。奥の共鳴の炉で強化と分解と鍛え直し、ここでは傷んだ品の修理を銭で請け負う。",
               "This is the smithy. The resonance forge in the back strengthens, breaks down and reforges. Here I mend worn gear for coin."),
              [("repair", "talk", [fac("repair")], None, "手に持った品を直す (鐘銭)", "Mend the item in hand (bell coins)", None, None),
               ("forge", "talk", None, "forge", "炉の使い方", "How do I use the forge?",
                "炉に品を入れて、強化なら共鳴の欠片、分解なら品そのものを渡す。鍛え直しは素材と相談だ。迷ったら一つずつ試せ。",
                "Put the piece in the forge. Strengthening takes resonance shards; breaking down takes the piece itself. Reforging depends on materials. Try one step at a time.")]),
    "tool_merchant": ("道具屋", "Tool Merchant",
                      ("道具屋だよ。食べ物、松明、矢、寝袋。払いは鐘銭か、巨神の素材でもいい。いらない素材は買い取るよ。",
                       "Tool shop. Food, torches, arrows, bedrolls. Pay in bell coins, or in titan materials. I'll buy spare materials too."),
                      [("shop", "talk", [fac("shop")], None, "品を見る", "Browse the goods", None, None)]),
    "apothecary": ("薬師", "Apothecary",
                   ("薬師の店です。傷薬、毒消し、夜目の薬。醸造台を扱えるようになるまでは、ここで揃えてください。",
                    "The apothecary's. Salves, antidotes, night-eye draughts. Until you learn to brew, stock up here."),
                   [("shop", "talk", [fac("shop")], None, "薬を見る", "Browse remedies", None, None),
                    ("brew", "talk", None, "brew", "醸造のこと", "About brewing",
                     "火の章まで行ったら、醸造を教えましょう。それまでは、薬は買うのがいちばん早い。",
                     "Once you reach the chapter of fire, I'll teach you to brew. Until then, buying is quickest.")]),
    "storage_keeper": ("預かり番", "Storage Keeper",
                       ("預かり所です。品を預ければ、どの町の預かり所からでも同じ中身を出せます。旅嚢の大事な品は、旅嚢のままに。",
                        "This is the storehouse. What you leave here can be taken out at any storehouse, in any town. Keep your satchel's precious things in the satchel."),
                       [("stash", "talk", [fac("stash")], None, "預ける・引き出す", "Deposit or withdraw", None, None)]),
    "innkeeper": ("宿の女将", "Innkeeper",
                  ("鐘の宿へようこそ。寝台を取れば、倒れても目覚めるのはこの宿。噂話なら、いくらでもあるよ。",
                   "Welcome to the Bell Inn. Take a bed, and if you fall, you'll wake here. As for rumours, I have plenty."),
                  [("rest", "talk", [fac("inn_rest")], None, "寝台を取る (目覚める場所をこの宿に)", "Take a bed (wake here)", None, None),
                   ("rumor", "talk", [fac("rumor")], None, "噂を聞く", "Hear a rumour", None, None),
                   ("meet", "talk", None, "meet", "待ち合わせのこと", "About meeting up",
                    "仲間を待つなら、この宿の広間を使いな。誰がどこにいるかは、仲間の札でわかるだろう。",
                    "If you're waiting for companions, use the common room. Your party cards will tell you where everyone is.")]),
    "perch_keeper": ("止まり木の番", "Perch Keeper",
                     ("子の止まり木だ。出会った巨神の子なら、ここから呼んでやれる。行き先は海図で選びな。",
                      "This is the young ones' perch. If you've met a titan, I can call its young for you. Choose where to go on the chart."),
                     [("call", "talk", [fac("perch")], None, "巨神の子を呼ぶ", "Call a titan's young", None, None),
                      ("map", "talk", [fac("map")], None, "地図を見る", "Look at the map", None, None),
                      ("rule", "talk", None, "rule", "旅の決まり", "The rules of travel",
                       "子は眠っている巨神には応えない。パーティは六人まで一緒に乗れる。降りれば子は歌に溶けて帰る。",
                       "The young won't answer for a sleeping titan. A party of up to six can ride together. When you get off, it melts back into song.")]),
    "shrine_priest": ("社の祭司", "Shrine Priest",
                      ("四巨神の社です。転生の台座と、天の鍵の欠片を納める鍵の間があります。",
                       "This is the Shrine of the Four Titans. Here are the altar of rebirth, and the key chamber where sky-key fragments are laid."),
                      [("rebirth", "talk", None, "rebirth", "転生のこと", "About rebirth",
                        "台座に触れれば、積み上げたものを手放して、もう一度歩き直せます。代わりに、魂に残るものがある。",
                        "Touch the altar and you let go of what you built, to walk again. In return, something stays with your soul."),
                       ("vow", "talk", None, "vow", "誓いのこと", "About vows",
                        "誓いは周回の縛りです。重い誓いほど、手に入るものも重くなる。",
                        "A vow binds your next round. The heavier the vow, the heavier the reward.")]),
    "keep_veteran": ("砦の老兵", "Keep Veteran",
                     ("空見の砦だ。順位の石と記録の板がある。巨神の背での働きは、ここに刻まれる。",
                      "Skywatch Keep. Here stand the ranking stone and the record boards. What you do on the titans' backs is carved here."),
                     [("records", "talk", None, "records", "記録の見方", "Reading the records",
                       "石に触れれば今週の順位、板には各巨神の速さが出る。仲間と並んで名が残るのも悪くない。",
                       "Touch the stone for this week's ranking; the boards show each titan's fastest runs. Not bad to have your name beside your friends'.")]),
    "archivist": ("書庫番", "Archivist",
                  ("空見の館の書庫です。伝承の写しと、地図の写しを預かっています。",
                   "The Skywatch Hall library. We keep copies of the lore, and copies of maps."),
                  [("map", "talk", [fac("map")], None, "地図の写しを見る", "See the map copies", None, None),
                   ("lore", "talk", None, "lore", "伝承の読み返し", "Rereading the lore",
                    "伝承の書に記したことは、いつでも読み返せます。閲覧の机は、まだ支度の途中です。",
                    "What you've written in your lore book can always be reread. The reading desks are still being prepared.")]),
    "training_master": ("師範", "Training Master",
                        ("訓練場だ。的を打てば、与えた量が出る。技の振り直しは、いずれここで引き受ける。",
                         "The training yard. Strike the targets to see how much you deal. In time, I'll take on resetting your skills here."),
                        [("respec", "talk", None, "respec", "技の振り直し", "Resetting skills",
                          "振り直しには代価がいる。支度が整ったら声をかけよう。",
                          "A reset has a price. I'll call on you when it's ready.")]),
    "black_marketeer": ("顔を隠した商人", "Masked Trader",
                        ("……見るだけならただだ。品は日ごとに替わる。払いは銭と、巨神の欠片で。",
                         "...Looking is free. The goods change each day. Payment in coin, and in titan shards."),
                        [("shop", "talk", [fac("shop")], None, "今日の品を見る", "See today's goods", None, None)]),
    "gravekeeper": ("墓守", "Gravekeeper",
                    ("墓守だ。名のない墓が増えた。誰が眠っているのか、わしにも分からん。",
                     "I keep the graves. The nameless ones keep growing. Even I don't know who sleeps in them."),
                    [("graves", "talk", None, "graves", "名のない墓のこと", "About the nameless graves",
                      "夜になると、墓の上に誰かが立っているのを見る。こちらを見ている。……気のせいだといいが。",
                      "At night I see someone standing on the graves. Watching. ...I hope it's only my eyes.")]),
    "mapmaker": ("地図屋", "Mapmaker",
                 ("地図屋だよ。跡地の噂と、まだ行っていない印を描き足してやれる。",
                  "Mapmaker. I can add rumours of the old sites, and the marks you haven't reached yet."),
                 [("map", "talk", [fac("map")], None, "地図を広げる", "Spread out the map", None, None),
                  ("sites", "talk", None, "sites", "跡地の噂", "Rumours of the sites",
                   "巨神が眠った跡には、決まって何かが残る。方角は地図の印のとおりだ。",
                   "Where a titan sleeps, something is always left behind. The bearings are as the marks show.")]),
    "drunkard": ("酔いどれ", "Drunkard",
                 ("ひっく……空の下に、もう一つ空があるって知ってるか？",
                  "Hic... did you know there's another sky beneath the sky?"),
                 [("abyss", "talk", None, "abyss", "もう一つの空のこと", "The other sky?",
                   "淵だよ、淵。四つの心臓が止まったあと、底が口を開ける……って、親父が言ってた。ひっく。",
                   "The abyss, the abyss. After the four hearts stop, the bottom opens its mouth... my old man said so. Hic.")]),
}

COMMON = {
    "about_again": ("ここは何をする所", "What is this place?"),
    "bye": ("また来る", "I'll come again"),
}


def dialogue(role, spec):
    name_ja, name_en, (intro_ja, intro_en), options = spec
    rid = "skyabyss:attendant_" + role
    base = "dialogue.skyabyss.attendant_" + role
    nodes = {
        "intro": {"face": "smile", "do": [{"type": "flag", "flag": "intro"}], "next": "menu"},
        "again": {"face": "neutral", "next": "menu", "text": base + ".intro"},
    }
    t(base + ".intro", intro_ja, intro_en)
    t(base + ".menu", "何にするかい。" if role not in ("shrine_priest", "archivist", "apothecary", "storage_keeper", "guild_clerk") else "ご用件は。",
      "What'll it be?" if role not in ("shrine_priest", "archivist", "apothecary", "storage_keeper", "guild_clerk") else "How may I help?")
    menu = []
    for oid, kind, do, node, oja, oen, bja, ben in options:
        op = {"id": oid, "kind": kind}
        if do:
            op["do"] = do
            op["goto"] = "end"
        else:
            op["goto"] = node
            nodes[node] = {"face": "neutral", "next": "menu"}
            t(base + "." + node, bja, ben)
        menu.append(op)
        t(base + ".menu." + oid, oja, oen)
    menu.append({"id": "about", "kind": "talk", "goto": "again"})
    t(base + ".menu.about", *COMMON["about_again"])
    menu.append({"id": "bye", "kind": "leave", "goto": "end"})
    t(base + ".menu.bye", *COMMON["bye"])
    nodes["menu"] = {"face": "neutral", "options": menu}
    t("entity.skyabyss.attendant." + role, name_ja, name_en)
    # 会話の画面・クエストの依頼主の名前 (entity.<会話の役>)
    t("entity.skyabyss.attendant_" + role, name_ja, name_en)
    return {"npc": rid,
            "entries": [{"if": {"type": "not", "of": {"type": "flag", "flag": "intro"}}, "node": "intro"}, {"node": "menu"}],
            "nodes": nodes}


# ====================================================================== 店
def good(gid, item, coins=0, count=1, materials=None, **kw):
    g = {"id": gid, "item": item, "count": count}
    if coins:
        g["coins"] = coins
    if materials:
        g["materials"] = [{"item": m, "count": n} for m, n in materials]
    g.update(kw)
    return g


SW, TS, TW, EM = "skyabyss:stone_whale_shard", "skyabyss:tide_shell_shard", "skyabyss:thunder_wing_shard", "skyabyss:ember_shard"

SHOPS = {
    "tool_shop": {
        "goods": [
            good("hardtack", "skyabyss:road_hardtack", 4, 2),
            good("bread", "minecraft:bread", 3, 3),
            good("cod", "minecraft:cooked_cod", 4, 3),
            good("torch", "minecraft:torch", 2, 8),
            good("arrow", "minecraft:arrow", 3, 8),
            good("lantern", "minecraft:lantern", 6),
            good("bedroll", "minecraft:white_bed", 12),
            good("whetstone", "skyabyss:rock_whetstone", 6),
            good("rope", "minecraft:lead", 5),
            # 巨神の素材で払う品 (周回の意味。35 の 4 章)
            good("resonance", "skyabyss:resonance_shard", 0, 1, [(SW, 3)]),
            good("socket_stone", "skyabyss:socket_shard_stone", 10, 1, [(SW, 6)], chapter=2),
            good("socket_tide", "skyabyss:socket_shard_tide", 10, 1, [(TS, 6)], chapter=3),
            good("socket_wind", "skyabyss:socket_shard_wind", 10, 1, [(TW, 6)], chapter=4),
            good("socket_ash", "skyabyss:socket_shard_ash", 10, 1, [(EM, 6)], chapter=5),
        ],
        "buys": [
            {"item": "skyabyss:bone_chip", "per": 4, "coins": 1},
            {"item": "skyabyss:hare_quill", "per": 4, "coins": 1},
            {"item": "skyabyss:rock_shell_scrap", "per": 4, "coins": 1},
            {"item": "minecraft:bone", "per": 8, "coins": 1},
            {"item": "minecraft:string", "per": 8, "coins": 1},
            {"item": "minecraft:rotten_flesh", "per": 16, "coins": 1},
            {"item": "minecraft:gunpowder", "per": 4, "coins": 1},
            {"item": SW, "per": 1, "coins": 2},
            {"item": TS, "per": 1, "coins": 3},
            {"item": TW, "per": 1, "coins": 4},
            {"item": EM, "per": 1, "coins": 5},
        ],
    },
    "apothecary": {
        "goods": [
            good("salve", "skyabyss:moss_salve", 6),
            good("healing", "minecraft:potion", 8, potion="minecraft:healing"),
            good("antidote", "minecraft:milk_bucket", 5),
            good("night_eye", "minecraft:potion", 10, potion="minecraft:night_vision"),
            good("water_breath", "minecraft:potion", 12, potion="minecraft:water_breathing", chapter=2),
            good("regen", "minecraft:potion", 14, potion="minecraft:regeneration", chapter=2),
            good("fire_res", "minecraft:potion", 14, potion="minecraft:fire_resistance", chapter=4),
            good("strong_healing", "minecraft:potion", 6, potion="minecraft:strong_healing", materials=[(TS, 2)], chapter=3),
        ],
        "buys": [
            {"item": "minecraft:spider_eye", "per": 4, "coins": 1},
            {"item": "skyabyss:glow_anemone", "per": 2, "coins": 1},
            {"item": "skyabyss:bubble_sac", "per": 4, "coins": 1},
            {"item": "skyabyss:tide_dried_shell", "per": 4, "coins": 1},
            {"item": "skyabyss:moth_dust", "per": 4, "coins": 1},
            {"item": "skyabyss:spark_dust", "per": 4, "coins": 1},
            {"item": "skyabyss:cinder_husk", "per": 4, "coins": 1},
        ],
    },
    # 闇市 (35 の 3.3): 日替わり 4 品。一人一日 1〜2 個まで。銭と巨神の欠片で
    "black_market": {
        "goods": [],
        "rotation": {"count": 4, "pool": [
            good("crystal", "skyabyss:resonance_crystal", 30, 1, [(SW, 4)], limit=1),
            good("aspect", "skyabyss:aspect_core", 40, 1, [(TS, 4)], limit=1, chapter=2),
            good("rune", "skyabyss:skill_rune", 35, 1, [(SW, 2)], limit=1),
            good("bone_socket", "skyabyss:socket_shard_bone", 25, 1, [(SW, 3)], limit=1),
            good("anemone", "skyabyss:glow_anemone", 6, 2, limit=2),
            good("jerky", "skyabyss:cloud_jerky", 8, 2, limit=2),
            good("tide_socket", "skyabyss:socket_shard_tide", 30, 1, [(TS, 3)], limit=1, chapter=3),
            good("wind_socket", "skyabyss:socket_shard_wind", 30, 1, [(TW, 3)], limit=1, chapter=4),
            good("ash_socket", "skyabyss:socket_shard_ash", 30, 1, [(EM, 3)], limit=1, chapter=5),
            good("spike_arrow", "skyabyss:bone_spike_arrow", 10, 8, limit=2),
        ]},
        "buys": [
            {"item": "skyabyss:resonance_shard", "per": 1, "coins": 3},
        ],
    },
}
SHOP_NAMES = {
    "tool_shop": ("道具屋", "Tool Shop"),
    "apothecary": ("薬師の店", "Apothecary"),
    "black_market": ("闇市", "Black Market"),
}

# ====================================================================== 画面・知らせ
UI = [
    ("item.skyabyss.bell_coin", "鐘銭", "Bell Coin"),
    ("item.skyabyss.bell_coin.desc", "鐘の国の古い銭。都の店で使える。", "An old coin of the Bell Kingdom. Spent in the capital's shops."),
    ("item.skyabyss.bell_coin.use", "頼み・敵・売り買いで手に入る", "Earned from errands, foes, and trade"),
    ("entity.skyabyss.attendant", "係の人", "Attendant"),
    ("message.skyabyss.attendant.struck", "「やめておくれ……」", "\"Please, stop that...\""),
    ("message.skyabyss.facility.coins_gained", "鐘銭を %s 枚受け取った", "Received %s bell coins"),
    ("gui.skyabyss.shop.buy", "買う", "Buy"),
    ("gui.skyabyss.shop.sell", "売る", "Sell"),
    ("gui.skyabyss.shop.daily", "日替わり", "Today only"),
    ("gui.skyabyss.shop.left", "残り %s", "%s left"),
    ("gui.skyabyss.shop.have", "手持ち %s", "Have %s"),
    ("gui.skyabyss.shop.locked", "？？？ (%s 章から)", "??? (from chapter %s)"),
    ("gui.skyabyss.shop.empty", "今日は品がない", "Nothing for sale today"),
    ("gui.skyabyss.shop.nothing_bought", "買い取る品はない", "Nothing is bought here"),
    ("gui.skyabyss.shop.hint_buy", "押して買う ・ シフトで 8 回ぶん", "Click to buy · Shift for 8"),
    ("gui.skyabyss.shop.hint_sell", "押して一口売る ・ シフトで全部", "Click to sell one lot · Shift for all"),
    ("message.skyabyss.shop.no_coins", "鐘銭が足りない", "Not enough bell coins"),
    ("message.skyabyss.shop.no_materials", "%s が足りない", "Not enough %s"),
    ("message.skyabyss.shop.sold_out", "今日はもう売り切れだ", "Sold out for today"),
    ("message.skyabyss.shop.nothing_to_sell", "売れる品が足りない", "Not enough to sell"),
    ("gui.skyabyss.stash.title", "預かり所", "Storehouse"),
    ("message.skyabyss.inn.rested", "鐘の宿の寝台を取った。倒れたら、ここで目覚める", "You took a bed at the Bell Inn. If you fall, you'll wake here"),
    ("message.skyabyss.inn.rumor_head", "噂: %s", "Rumour: %s"),
    ("message.skyabyss.perch.no_titan", "まだ巨神に出会っていない。子は呼べない", "You haven't met a titan yet. No young will answer"),
    ("message.skyabyss.smith.nothing_to_repair", "手に持った品に傷みはない", "The item in your hand isn't worn"),
    ("message.skyabyss.smith.repair_cost", "修理には鐘銭が %s 枚いる", "Mending costs %s bell coins"),
    ("message.skyabyss.smith.repaired", "%s を直した (鐘銭 %s 枚)", "Mended %s (%s bell coins)"),
    # ギルド
    ("gui.skyabyss.guild.title", "ギルドの窓口", "Guild Desk"),
    ("gui.skyabyss.guild.make_head", "ギルドを作る", "Found a guild"),
    ("gui.skyabyss.guild.name", "名前 (2〜16 字)", "Name (2-16)"),
    ("gui.skyabyss.guild.tag", "名札の印 (1〜4 字)", "Tag (1-4)"),
    ("gui.skyabyss.guild.color", "旗の地の色", "Banner colour"),
    ("gui.skyabyss.guild.accent", "旗の模様の色", "Pattern colour"),
    ("gui.skyabyss.guild.create", "作る", "Found"),
    ("gui.skyabyss.guild.list_head", "ギルドの一覧", "Guilds"),
    ("gui.skyabyss.guild.list_empty", "まだギルドはない", "No guilds yet"),
    ("gui.skyabyss.guild.apply", "申請", "Apply"),
    ("gui.skyabyss.guild.join", "入る", "Join"),
    ("gui.skyabyss.guild.applied", "申請中", "Applied"),
    ("gui.skyabyss.guild.leave", "抜ける", "Leave"),
    ("gui.skyabyss.guild.kick", "外す", "Remove"),
    ("gui.skyabyss.guild.members", "%s / %s 人", "%s / %s members"),
    ("gui.skyabyss.guild.applicants", "申請", "Applications"),
    ("gui.skyabyss.guild.no_applicants", "申請はない", "No applications"),
    ("gui.skyabyss.guild.open_on", "誰でも入れる: 入", "Open to all: on"),
    ("gui.skyabyss.guild.open_off", "誰でも入れる: 切", "Open to all: off"),
    ("message.skyabyss.guild.ok", "済んだ", "Done"),
    ("message.skyabyss.guild.in_guild", "もうギルドに入っている", "You're already in a guild"),
    ("message.skyabyss.guild.not_in_guild", "ギルドに入っていない", "You're not in a guild"),
    ("message.skyabyss.guild.name_length", "名前は 2〜16 字で", "The name must be 2-16 characters"),
    ("message.skyabyss.guild.name_taken", "その名前のギルドはもうある", "A guild with that name already exists"),
    ("message.skyabyss.guild.tag_length", "名札の印は 1〜4 字で", "The tag must be 1-4 characters"),
    ("message.skyabyss.guild.no_guild", "そのギルドはない", "No such guild"),
    ("message.skyabyss.guild.full", "そのギルドはもう 30 人いる", "That guild already has 30 members"),
    ("message.skyabyss.guild.applied", "申請を出した。長が受けるのを待とう", "Application sent. Wait for the leader to accept"),
    ("message.skyabyss.guild.already_applied", "もう申請を出している", "You've already applied"),
    ("message.skyabyss.guild.not_leader", "長だけができる", "Only the leader can do that"),
    ("message.skyabyss.guild.not_applicant", "その人の申請はない", "No application from that person"),
    ("message.skyabyss.guild.not_member", "その人はギルドにいない", "That person isn't in the guild"),
    ("message.skyabyss.guild.joined", "ギルドに入った", "Joined the guild"),
    ("message.skyabyss.guild.new_applicant", "%s が「%s」に入りたいと申請した", "%s applied to join \"%s\""),
    ("message.skyabyss.guild.denied", "「%s」への申請は断られた", "Your application to \"%s\" was declined"),
    ("message.skyabyss.guild.member_joined", "%s が「%s」に入った", "%s joined \"%s\""),
    ("message.skyabyss.guild.left", "「%s」を抜けた", "You left \"%s\""),
    ("message.skyabyss.guild.now_leader", "「%s」の長になった", "You are now the leader of \"%s\""),
    ("message.skyabyss.guild.info", "%s  長: %s  %s/%s 人  %s", "%s  leader: %s  %s/%s  %s"),
    ("message.skyabyss.guild.list_row", "%s  %s/%s 人", "%s  %s/%s"),
    ("message.skyabyss.guild.none", "まだギルドはない", "No guilds yet"),
]

# 宿の噂 (章ごとに 3 つ。町のクエストが来たら FacilityActions.addRumorSource で上書きされる)
RUMORS = {
    1: [("北の岩鯨は、鎖が鳴るたびに少しだけ目を開けるらしい。", "They say the stone whale in the north opens its eyes a little whenever the chains ring."),
        ("墓地の外れで、夜に骨を数える声がするって。", "Someone counts bones at night, out past the graveyard."),
        ("組合の見習いが、最初の登りで足を挫いたそうだ。崖の縄には気をつけな。", "A guild apprentice twisted an ankle on the first climb. Mind the cliff ropes.")],
    2: [("潮が引く日には、海の底の道が見えるそうだよ。", "On low-tide days, they say a road shows on the sea floor."),
        ("漁師が網に真珠ほどの目玉を拾ったって。気味が悪い。", "A fisher netted an eye the size of a pearl. Gives me the creeps."),
        ("東の浜で、殻の鳴る音が夜通し続いたらしい。", "On the east shore, shells rang all night long.")],
    3: [("雷の夜には、子どもを外に出すなって言われてる。", "On thunder nights, keep the children inside, they say."),
        ("西の空の羽は、落ちても燃えないんだとさ。", "The feathers from the western sky don't burn, even when they fall."),
        ("銅の針金を巻いた旅人が、雷を呼んだって噂だ。", "A traveller wrapped in copper wire is said to have called down lightning.")],
    4: [("南の灰は、まだ温かいんだって。", "The ash in the south is still warm, they say."),
        ("鍛冶場の弟子が、火の糸を見たって騒いでた。", "The smith's apprentice was raving about threads of fire."),
        ("四つ目の心臓が止まれば、底が開くって酔いどれが言ってた。", "The drunkard says the bottom opens once the fourth heart stops.")],
}


def main():
    os.makedirs(DIALOGUE, exist_ok=True)
    os.makedirs(SHOP, exist_ok=True)
    os.makedirs(LANG, exist_ok=True)
    for role, spec in ROLES.items():
        with open(os.path.join(DIALOGUE, "attendant_%s.json" % role), "w", encoding="utf-8") as f:
            json.dump(dialogue(role, spec), f, ensure_ascii=False, indent=2)
            f.write("\n")
    for sid, shop in SHOPS.items():
        with open(os.path.join(SHOP, sid + ".json"), "w", encoding="utf-8") as f:
            json.dump(shop, f, ensure_ascii=False, indent=2)
            f.write("\n")
        t("gui.skyabyss.shop." + sid, *SHOP_NAMES[sid])
    for key, ja, en in UI:
        t(key, ja, en)
    for ch, lines in RUMORS.items():
        for i, (ja, en) in enumerate(lines, 1):
            t("message.skyabyss.inn.rumor.ch%d.%d" % (ch, i), ja, en)
    model = os.path.join(RES, "assets", "skyabyss", "models", "item", "bell_coin.json")
    with open(model, "w", encoding="utf-8") as f:
        json.dump({"parent": "minecraft:item/generated", "textures": {"layer0": "skyabyss:item/bell_coin"}}, f, indent=2)
        f.write("\n")
    for name, d in (("ja_jp", JA), ("en_us", EN)):
        with open(os.path.join(LANG, name + ".json"), "w", encoding="utf-8") as f:
            json.dump(dict(sorted(d.items())), f, ensure_ascii=False, indent=2)
            f.write("\n")
    print("dialogues", len(ROLES), "shops", len(SHOPS), "lang keys", len(JA))


if __name__ == "__main__":
    main()
