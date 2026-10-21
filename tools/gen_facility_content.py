#!/usr/bin/env python3
"""都の施設 (docs/design/35 の F2・F3) の会話・店の品ぞろえ・文を書く。

  python tools/gen_facility_content.py

書くもの (毎回すべて書き直す。手で直さないこと):
  data/skyabyss/dialogue/attendant_<役>.json      係の人 16 人の会話 (初めての一言 → できることの選択肢)
  data/skyabyss/shop/{tool_shop,apothecary,black_market}.json   店の品ぞろえ (鐘銭と巨神の素材。エメラルドは使わない)
  assets/skyabyss/models/item/bell_coin.json      鐘銭の手に持つ形
  assets/skyabyss_fac/lang/{ja_jp,en_us}.json     文 (係の人の名前・会話・店とギルドの画面・知らせ・噂)
会話の結果 {"type": "facility", "open": ...} は facility.FacilityActions が受ける。
選択肢の 9 番目に dict を書くと: "if" = 出す条件、"goto" = 自分で書いた節 (NODES) へ飛ぶ。
NODES[役] = 節の名前 → {"text": (ja, en) か "text_key": 鍵, "do": [...], "next": 節, "options": [(id, 飛び先, do, (ja, en) か 鍵, if)]}
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
                    [("guild", "talk", [fac("guild")], None, "ギルドの窓口 (作る・入る・頼み・旗・功績)", "Guild desk (found, join, requests, banner, deeds)", None, None),
                     ("storage", "talk", [fac("guild_storage")], None, "ギルドの倉庫を開く", "Open the guild storehouse", None, None),
                     ("board", "talk", [{"type": "open_board"}], None, "言伝ての板を見る", "Look at the message board", None, None),
                     ("what", "talk", None, "what", "ギルドとは", "What is a guild?",
                      "ギルドは旅人が作る集まりです。旗の色と模様を選んで名前を付ければ、三十人まで入れます。名札にギルドの印が出ますよ。倉庫を分け合い、週ごとの頼みを一緒に片づけてください。頼みを重ねると、特別な模様や名札の色が使えるようになります。長は抜けられません。抜けるなら、先に長を譲るか、解散を。",
                      "A guild is a fellowship travellers make themselves. Pick banner colours, a pattern and a name, and up to thirty may join. Its tag shows on your name plate. Members share a storehouse and take on a weekly request together. Finish enough requests and special patterns and tag colours open up. The leader can't leave: hand over leadership or disband first.")]),
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
                     "火の章まで行ったら、醸造を教えましょう。それまでは、薬は買うのがいちばん早いですよ。",
                     "Once you reach the chapter of fire, I'll teach you to brew. Until then, buying is quickest.",
                     {"if": {"type": "stage", "max": 2}}),
                    ("learn", "talk", None, None, "醸造を教わる", "Learn to brew", None, None,
                     {"if": {"type": "all", "of": [{"type": "stage", "min": 3}, {"type": "not", "of": {"type": "flag", "flag": "brew_lesson"}}]},
                      "goto": "lesson"}),
                    ("review", "talk", None, None, "醸造のおさらい", "Go over brewing again", None, None,
                     {"if": {"type": "flag", "flag": "brew_lesson"}, "goto": "lesson_review"})]),
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
                        "奥の祭壇が転生の台座です。Lv 50 を越え、共鳴の結晶を五つ納めれば、台座に触れて積み上げたものを手放し、もう一度歩き直せます。代わりに、魂に残るものがある。",
                        "The altar at the back is the altar of rebirth. Past level 50, with five resonance crystals to offer, touch it to let go of what you built and walk again. In return, something stays with your soul."),
                       ("vow", "talk", None, None, "誓いのこと (周回の縛り)", "About vows (bindings for repeat runs)", None, None, {"goto": "vow"}),
                       ("offer", "talk", [fac("key_offer")], None, "鍵の間に欠片を納める", "Lay fragments in the key chamber", None, None),
                       ("return", "talk", [fac("key_return")], None, "納めた欠片を返してもらう", "Take back the laid fragments", None, None,
                        {"if": {"type": "stage", "min": 1}}),
                       ("keys", "talk", None, "keys", "鍵の間のこと", "About the key chamber",
                        "四つの石は、四つの巨神の座です。心臓を止めた巨神の石に触れれば、天の鍵の欠片が一つ石に預けられる。四つそろえば、鍵はここで形になる。預けた欠片は失くさないし、いつでも返します。",
                        "The four stones are the seats of the four titans. Touch the stone of a titan whose heart you stilled, and one sky-key fragment rests in it. When four rest here, the key takes shape. Laid fragments are never lost, and I'll return them any time.")]),
    "keep_veteran": ("砦の老兵", "Keep Veteran",
                     ("空見の砦だ。順位の石と記録の板がある。巨神の背での働きは、ここに刻まれる。",
                      "Skywatch Keep. Here stand the ranking stone and the record boards. What you do on the titans' backs is carved here."),
                     [("codex", "talk", [fac("records")], None, "試練の記録を見る (図鑑)", "See the trial records (codex)", None, None),
                      ("records", "talk", None, "records", "記録の見方", "Reading the records",
                       "石に触れれば今週の順位、板には各巨神の速さが出る。試練の図鑑の写しは、わしが預かっている。仲間と並んで名が残るのも悪くない。",
                       "Touch the stone for this week's ranking; the boards show each titan's fastest runs. I keep a copy of the trial codex. Not bad to have your name beside your friends'."),
                      ("page", "talk", None, "page", "本丸の奥の頁のこと", "The page deep in the keep",
                       "本丸の奥に、前の空見が残した頁が一枚ある。……まだ見せられん。三章の話が進めば、そのときにな。",
                       "Deep in the keep there's a page the last skywatcher left. ...I can't show it yet. When the third chapter's tale moves on, then.")]),
    "archivist": ("書庫番", "Archivist",
                  ("空見の館の書庫です。伝承の写しと、地図の写しを預かっています。",
                   "The Skywatch Hall library. We keep copies of the lore, and copies of maps."),
                  [("lore", "talk", [fac("lore_book")], None, "伝承を読み返す", "Reread the lore", None, None),
                   ("map", "talk", [fac("map_copy")], None, "地図の写しをもらう (祠と跡地を M の地図に)", "Get a map copy (shrines and sites on your map)", None, None),
                   ("desk", "talk", None, "desk", "閲覧の机のこと", "About the reading desks",
                    "伝承の書に記したことは、ここでも読み返せます。書をなくしても、書庫の写しは消えません。地図の写しには、祠と、開いた章の跡地のありかを描き足します。",
                    "What you've written in your lore book can be reread here too. Even if you lose the book, the library's copy remains. A map copy adds the shrines, and the true places of the sites of the chapters you've opened.")]),
    "training_master": ("師範", "Training Master",
                        ("訓練場だ。木偶を打てば、与えた量が出る。技や力の振り直しも、ここで引き受ける。炉まで行かなくていい。",
                         "The training yard. Strike the dummies to see how much you deal. I'll also reset your skills or stats here, no forge needed."),
                        [("respec", "talk", None, None, "振り直し (技・力)", "Reset (skills, stats)", None, None, {"goto": "respec"}),
                         ("dummy", "talk", None, "dummy", "木偶のこと", "About the dummies",
                          "わらの木偶を打てば、頭の上に与えた量が浮かぶ。五秒の合計と、一秒あたりも教えてやる。技でも矢でもいい。",
                          "Strike a straw dummy and the damage floats above its head. I'll tell you the five-second total and the rate per second too. Skills and arrows count.")]),
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
    "trade_keeper": ("取引所の番", "Trade Keeper",
                     ("取引所だよ。旅人どうしで品を出し合う所さ。出す品はここで預かる。ほしい物を書いておけば、誰かが申請してくる。",
                      "This is the exchange. Travellers trade with each other here. I hold what you put up, and if you write what you want, someone will apply."),
                     [("trade", "talk", [fac("trade")], None, "取引所を開く (出す・申請する・受ける)", "Open the exchange (list, apply, accept)", None, None),
                      ("rules", "talk", None, "rules", "取引の決まり", "The rules of trade",
                       "出す品は、出したときに預かる。ほしいものは品でも鐘銭でもいい。申請しても、品はまだ動かない。出した人が受けた瞬間に、両方の品が入れ替わる。ただし、そのとき申請した人がここにいて、品を持っていて、持ち物に空きがあること。一人十品まで。七日たつと品は戻って、受け取りに置いておく。受けた・断ったの知らせは、いなければ次に来たときに伝えるよ。",
                       "What you list, I hold from the moment you list it. Ask for goods or bell coins, as you like. Applying moves nothing. The moment the seller accepts, both sides swap at once, as long as the applicant still has the goods and room to carry. An absent applicant can't be accepted. Ten listings each; after seven days the goods come back to you, waiting in your mailbox. If you're away when someone answers, I'll tell you next time.")]),
}

# 周回の誓い (titan/vow/Vows の登録と同じ id。名前と説明は誓いの文 vow.skyabyss.<id>(.desc) をそのまま使う。
# 足りない誓いがあれば自己テスト facility extras が落ちる)
VOWS = ["hardy", "sharp", "thin", "bare", "hasty"]

BACK = ("戻る", "Back")

NODES = {
    "training_master": {
        "respec": {"text": ("振り直すと、覚えた技・刻印・鍵石 (技)、または振った力 (力) がすべて戻る。代価は共鳴の結晶。なければ鐘銭でもいい (結晶一つにつき三十枚)。Lv 15 までの技は、ただだ。",
                            "A reset returns every learned skill, rune and keystone (skills), or every stat point (stats). The price is resonance crystals; bell coins will do instead (thirty a crystal). Skills are free until level 15."),
                   "do": [fac("respec_quote")],
                   "options": [("skills", "end", [fac("respec_skills")], ("技を振り直す (結晶)", "Reset skills (crystals)")),
                               ("skills_coins", "end", [fac("respec_skills_coins")], ("技を振り直す (鐘銭)", "Reset skills (bell coins)")),
                               ("stats", "end", [fac("respec_stats")], ("力を振り直す (結晶)", "Reset stats (crystals)")),
                               ("stats_coins", "end", [fac("respec_stats_coins")], ("力を振り直す (鐘銭)", "Reset stats (bell coins)")),
                               ("back", "menu", None, BACK)]},
    },
    "shrine_priest": dict(
        [("vow", {"text": ("誓いは周回の縛りです。心臓を一度止めた巨神の祠のそばで、乗る前に三つまで立てられる。重い誓いほど、素材が増え、共鳴の欠片が出やすく、称号も付く。どの誓いを聞きますか。",
                           "A vow is a binding for repeat runs. By the shrine of a titan whose heart you have stilled, before you ride, you may swear up to three. The heavier the vows, the more materials, the likelier resonance shards, and a title besides. Which vow would you hear of?"),
                  "options": [(v, "vow_" + v, None, "vow.skyabyss." + v) for v in VOWS] + [("back", "menu", None, BACK)]})]
        + [("vow_" + v, {"text_key": "vow.skyabyss." + v + ".desc", "next": "vow"}) for v in VOWS]),
    "apothecary": {
        "lesson": {"text": ("では、手ほどきを。醸造台にブレイズパウダーをくべ、水の瓶にネザーウォートで奇妙な薬を作ります。そこに火の章の素材を足せば、火を退ける薬になります。台と最初の一揃いは、持っていってください。",
                            "Then a lesson. Feed blaze powder to the brewing stand, and brew nether wart into water bottles for an awkward potion. Add the right ingredient and it becomes a draught that turns fire aside. Take the stand and a first set with you."),
                   "do": [{"type": "flag", "flag": "brew_lesson"}, fac("brew_lesson")], "next": "menu"},
        "lesson_review": {"text": ("醸造台に燃料のブレイズパウダー、瓶には水、まずネザーウォートで奇妙な薬。そこからが本番です。マグマクリームなら耐火、金のニンジンなら暗視の薬になります。",
                                   "Blaze powder fuels the stand, bottles hold water, and nether wart makes the awkward potion first. That's where it begins: magma cream for fire resistance, a golden carrot for night vision."),
                          "next": "menu"},
    },
}

COMMON = {
    "about_again": ("ここは何をする所？", "What is this place?"),
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
    t(base + ".menu", "……何か用か。" if role not in ("shrine_priest", "archivist", "apothecary", "storage_keeper", "guild_clerk") else "ご用件は。",
      "What'll it be?" if role not in ("shrine_priest", "archivist", "apothecary", "storage_keeper", "guild_clerk") else "How may I help?")
    menu = []
    for spec_o in options:
        oid, kind, do, node, oja, oen, bja, ben = spec_o[:8]
        extra = spec_o[8] if len(spec_o) > 8 else {}
        op = {"id": oid, "kind": kind}
        if "if" in extra:
            op["if"] = extra["if"]
        if do:
            op["do"] = do
            op["goto"] = "end"
        elif "goto" in extra:
            op["goto"] = extra["goto"]
        else:
            op["goto"] = node
            nodes[node] = {"face": "neutral", "next": "menu"}
            t(base + "." + node, bja, ben)
        menu.append(op)
        t(base + ".menu." + oid, oja, oen)
    for nid, nd in NODES.get(role, {}).items():
        n = {"face": nd.get("face", "neutral")}
        if "text_key" in nd:
            n["text"] = nd["text_key"]
        else:
            t(base + "." + nid, *nd["text"])
        if "do" in nd:
            n["do"] = nd["do"]
        if "options" in nd:
            ops = []
            for o in nd["options"]:
                oid2, goto, do2, text, cond = (list(o) + [None] * 5)[:5]
                oo = {"id": oid2, "kind": "talk", "goto": goto}
                if do2:
                    oo["do"] = do2
                if cond:
                    oo["if"] = cond
                if isinstance(text, str):
                    oo["text"] = text
                else:
                    t(base + "." + nid + "." + oid2, *text)
                ops.append(oo)
            n["options"] = ops
        else:
            n["next"] = nd.get("next", "menu")
        nodes[nid] = n
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
    ("message.skyabyss.smith.repair_cost", "修理には鐘銭が %s 枚要る", "Mending costs %s bell coins"),
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
    # ギルド: 位・倉庫・頼み・家と旗
    ("gui.skyabyss.guild.tab.members", "人", "Members"),
    ("gui.skyabyss.guild.tab.request", "頼み", "Request"),
    ("gui.skyabyss.guild.tab.banner", "旗", "Banner"),
    ("gui.skyabyss.guild.tab.achievements", "功績", "Deeds"),
    ("gui.skyabyss.guild.rank.0", "人", "Member"),
    ("gui.skyabyss.guild.rank.1", "副長", "Officer"),
    ("gui.skyabyss.guild.rank.2", "長", "Leader"),
    ("gui.skyabyss.guild.promote", "副長に", "Promote"),
    ("gui.skyabyss.guild.demote", "戻す", "Demote"),
    ("gui.skyabyss.guild.storage", "倉庫を開く", "Open storehouse"),
    ("gui.skyabyss.guild.storage_title", "%s の倉庫", "%s Storehouse"),
    ("gui.skyabyss.guild.take_rank.0", "取り出せる: 誰でも", "Withdraw: anyone"),
    ("gui.skyabyss.guild.take_rank.1", "取り出せる: 副長から", "Withdraw: officers"),
    ("gui.skyabyss.guild.take_rank.2", "取り出せる: 長だけ", "Withdraw: leader only"),
    ("gui.skyabyss.guild.banner", "旗を受け取る (鐘銭 %s)", "Take a banner (%s coins)"),
    ("gui.skyabyss.guild.emblem_head", "旗を作り直す", "Remake the banner"),
    ("gui.skyabyss.guild.emblem", "作り直す", "Remake"),
    ("gui.skyabyss.guild.house_head", "倉庫と旗", "Storehouse & banner"),
    ("gui.skyabyss.guild.pattern_head", "模様", "Pattern"),
    ("gui.skyabyss.guild.plate_head", "名札の色 (特別な色は功績で)", "Tag colour (special ones from deeds)"),
    ("gui.skyabyss.guild.locked_by", "功績「%s」で開く", "Opens with the deed \"%s\""),
    ("gui.skyabyss.guild.special", "功績の模様", "A deed pattern"),
    ("gui.skyabyss.guild.banner_rule", "特別な色は名札にだけ出る (旗は染料の色)", "Special colours show on name plates only"),
    ("gui.skyabyss.guild.disband", "解散する", "Disband"),
    ("gui.skyabyss.guild.disband_sure", "本当に解散？", "Really disband?"),
    ("gui.skyabyss.guild.leader_hint", "長は抜けられない (★ で長を譲る・解散する)", "The leader can't leave (★ hands over, or disband)"),
    ("gui.skyabyss.guild.transfer_sure", "もう一度 ★ を押すと長を譲る", "Press ★ again to hand over leadership"),
    ("gui.skyabyss.guild.ach_head", "済ませた頼み %s ・ 納めた量 %s ・ 人 %s", "Requests %s · delivered %s · members %s"),
    ("gui.skyabyss.guild.reward_pattern", "模様「%s」", "pattern \"%s\""),
    ("gui.skyabyss.guild.reward_plate", "名札の色「%s」", "tag colour \"%s\""),
    ("gui.skyabyss.guild.req_head", "今週の頼み", "This week's request"),
    ("gui.skyabyss.guild.req.bones", "骨片を %s 納める", "Deliver %s bone chips"),
    ("gui.skyabyss.guild.req.iron", "鉄の延べ棒を %s 納める", "Deliver %s iron ingots"),
    ("gui.skyabyss.guild.req.titan", "巨神の素材を %s 納める (どの巨神でも)", "Deliver %s titan materials (any titan)"),
    ("gui.skyabyss.guild.req.bread", "パンを %s 納める", "Deliver %s bread"),
    ("gui.skyabyss.guild.req.resonance", "共鳴の欠片を %s 納める", "Deliver %s resonance shards"),
    ("gui.skyabyss.guild.req.hunt", "敵を %s 倒す", "Defeat %s foes"),
    ("gui.skyabyss.guild.req_how_give", "持ち物から「納める」で", "Use Deliver to hand them in"),
    ("gui.skyabyss.guild.req_how_kill", "ギルドの人が倒すと、どこでも数える", "Counts wherever a member defeats one"),
    ("gui.skyabyss.guild.req_progress", "%s / %s", "%s / %s"),
    ("gui.skyabyss.guild.req_done", "済んだ (%s / %s)", "Done (%s / %s)"),
    ("gui.skyabyss.guild.req_mine", "自分の分 %s", "Yours: %s"),
    ("gui.skyabyss.guild.req_top", "多く納めた人", "Top contributors"),
    ("gui.skyabyss.guild.req_reward", "済むと、一つでも納めた人みなに 鐘銭 20・共鳴の欠片 1", "When done, everyone who helped gets 20 bell coins and 1 resonance shard"),
    ("gui.skyabyss.guild.deliver", "納める", "Deliver"),
    ("item.skyabyss.guild_banner", "%s の旗", "%s Banner"),
    ("message.skyabyss.guild.not_officer", "長か副長だけができる", "Only the leader or officers can do that"),
    ("message.skyabyss.guild.no_coins", "鐘銭が足りない", "Not enough bell coins"),
    ("message.skyabyss.guild.req_done", "今週の頼みはもう済んだ", "This week's request is already done"),
    ("message.skyabyss.guild.nothing_to_give", "納められる品を持っていない", "You have nothing to deliver"),
    ("message.skyabyss.guild.banner", "ギルドの旗を受け取った", "Received the guild banner"),
    ("message.skyabyss.guild.leader_stays", "長は抜けられない。先に長を譲る (★ か /guild leader <人>) か、解散する (/guild disband confirm)",
     "The leader can't leave. Hand over leadership first (★ or /guild leader <name>), or disband (/guild disband confirm)"),
    ("message.skyabyss.guild.new_leader", "長を譲った。副長になった", "Leadership handed over. You are now an officer"),
    ("message.skyabyss.guild.leader_changed", "「%s」の長が %s になった", "%2$s now leads \"%1$s\""),
    ("message.skyabyss.guild.disbanded", "ギルドを解散した。倉庫の中身は受け取った", "Guild disbanded. You received the storehouse's contents"),
    ("message.skyabyss.guild.disbanded_by", "「%s」は解散した", "\"%s\" was disbanded"),
    ("message.skyabyss.guild.disband_confirm", "本当に解散するなら /guild disband confirm (倉庫の中身は長へ。もとに戻せない)", "To really disband, use /guild disband confirm (the storehouse goes to the leader; this can't be undone)"),
    ("message.skyabyss.guild.pattern_locked", "その模様はまだ使えない (功績で開く)", "That pattern isn't open yet (earn it with a deed)"),
    ("message.skyabyss.guild.plate_locked", "その名札の色はまだ使えない (功績で開く)", "That tag colour isn't open yet (earn it with a deed)"),
    ("message.skyabyss.guild.achieved", "「%s」の功績「%s」: %s が使えるようになった", "\"%s\" earned the deed \"%s\": %s now available"),
    ("message.skyabyss.guild.delivered", "頼みの品を納めた", "Delivered for the request"),
    ("message.skyabyss.guild.storage_deposit_only", "この倉庫には預けるだけ (取り出すのは長が決めた位から)", "You can only deposit here (withdrawing is limited by rank)"),
    ("message.skyabyss.guild.req_complete", "「%s」の今週の頼みが済んだ", "\"%s\" finished this week's request"),
    ("message.skyabyss.guild.req_reward", "頼みの報い: 鐘銭 %s・共鳴の欠片 %s", "Request reward: %s bell coins, %s resonance shard"),
    # 訓練場
    ("message.skyabyss.training.hit", "木偶へ %s ・ 5 秒で %s (毎秒 %s)", "Dummy: %s · %s in 5 s (%s/s)"),
    ("message.skyabyss.training.quote", "振り直しの代価: 技 結晶 %s (鐘銭 %s) ・ 力 結晶 %s (鐘銭 %s)", "Reset price: skills %s crystals (%s coins) · stats %s crystals (%s coins)"),
    ("message.skyabyss.training.no_coins", "鐘銭が %s 枚要る", "You need %s bell coins"),
    ("message.skyabyss.training.no_crystals", "共鳴の結晶が %s つ要る", "You need %s resonance crystals"),
    ("message.skyabyss.training.nothing_skills", "戻す技がない", "No skills to reset"),
    ("message.skyabyss.training.nothing_stats", "振った力がない", "No stat points to reset"),
    ("entity.skyabyss.training_dummy", "木偶", "Training Dummy"),
    # 書庫
    ("message.skyabyss.archive.map_copied", "地図の写しをもらった。祠と跡地のありかが M の地図に載る", "You got a map copy. Shrines and sites now show on your map (M)"),
    ("message.skyabyss.archive.map_again", "写しはもう渡してある。新しい印も描き足しておいた", "You already have the copy. New marks are drawn in"),
    # 鍵の間
    ("message.skyabyss.keyroom.offered", "%s の石に欠片を納めた (鍵の間に %s つ)", "Laid a fragment in the %s stone (%s in the chamber)"),
    ("message.skyabyss.keyroom.all", "%s の石に欠片を納めた。四つの石が、同じ音で鳴っている", "Laid a fragment in the %s stone. The four stones ring as one"),
    ("message.skyabyss.keyroom.offered_some", "欠片を %s つ納めた (鍵の間に %s つ)", "Laid %s fragments (%s in the chamber)"),
    ("message.skyabyss.keyroom.all_at_once", "欠片を %s つ納めた。四つの石が、同じ音で鳴っている (%s)", "Laid %s fragments. The four stones ring as one (%s)"),
    ("message.skyabyss.keyroom.already", "%s の石には、もう欠片が眠っている", "A fragment already rests in the %s stone"),
    ("message.skyabyss.keyroom.no_fragment", "納める欠片を持っていない", "You have no fragment to lay"),
    ("message.skyabyss.keyroom.nothing_to_offer", "止めた心臓の石には、みな欠片が納めてある", "Every stone of a stilled heart already holds its fragment"),
    ("message.skyabyss.keyroom.empty", "鍵の間に納めた欠片はない", "No fragments rest in the chamber"),
    ("message.skyabyss.keyroom.returned", "欠片を %s つ返してもらった", "Took back %s fragments"),
    ("skyabyss_record.d.place_key_room", "・鍵の間 ×%s", "- Key chamber x%s"),
    # 醸造
    ("message.skyabyss.apothecary.not_yet", "醸造は火の章 (四章) で教える", "Brewing is taught in the chapter of fire (chapter 4)"),
    ("message.skyabyss.apothecary.lesson_again", "手ほどきの品はもう渡した", "The starter set was already given"),
    ("message.skyabyss.apothecary.lesson_kit", "醸造台・ブレイズパウダー・ネザーウォート 2・ガラス瓶 3 を受け取った", "Received a brewing stand, blaze powder, 2 nether wart and 3 glass bottles"),
    # 砦の記録
    ("gui.skyabyss.records.title", "試練の記録", "Trial Records"),
    ("gui.skyabyss.records.route", "順路を越えた回数 %s ・ 最速 %s", "Route cleared %s times · best %s"),
    ("gui.skyabyss.records.route_none", "順路はまだ越えていない", "Route not yet cleared"),
    ("gui.skyabyss.records.unmet", "まだ出会っていない巨神だ", "A titan you have not met yet"),
    ("gui.skyabyss.records.foot", "済んだ試練 %s ・ 称号 %s", "Trials cleared %s · titles %s"),
]

# ギルドの旗の模様 (facility/guild/GuildEmblems.ALL と同じ ID。足りなければ自己テスト lang が落ちる)
PATTERNS = {
    "band": ("帯と縁", "Band and border"), "fess": ("横の帯", "Fess"), "pale": ("縦の帯", "Pale"), "cross": ("十字", "Cross"),
    "saltire": ("斜め十字", "Saltire"), "chevron": ("山形", "Chevron"), "chief": ("上下の帯", "Chief and base"),
    "per_pale": ("左右の二色", "Per pale"), "per_fess": ("上下の二色", "Per fess"), "per_bend": ("斜めの二色", "Per bend"),
    "bend": ("斜めの帯", "Bend"), "corners": ("三つの角", "Three corners"), "lozenge": ("菱", "Lozenge"), "roundel": ("丸", "Roundel"),
    "eye": ("見る目", "The eye"), "bordure": ("縁", "Bordure"), "indented": ("波の縁", "Indented border"), "dancetty": ("鋸の歯", "Dancetty"),
    "paly": ("縦縞", "Paly"), "bricks": ("石積み", "Masonry"), "gradient": ("暮れ", "Dusk"),
    "flower": ("花", "Flower"), "flow": ("渦", "Whirl"), "skull": ("頭骨", "Skull"), "globe": ("天球", "Globe"), "guster": ("風", "Gust"),
    "tide": ("深い潮", "Deep tide"), "abyss_eye": ("淵の目", "Eye of the abyss"),
}
PLATES = {"gold": ("金", "Gold"), "teal": ("鐘の青緑", "Bell teal"), "silver": ("白銀", "Silver"), "abyss": ("淵の紫", "Abyss violet")}
ACHIEVEMENTS = {
    "requests_1": ("初めての頼み", "First request"), "requests_5": ("頼み 5 回", "5 requests"), "requests_10": ("頼み 10 回", "10 requests"),
    "requests_15": ("頼み 15 回", "15 requests"), "requests_30": ("頼み 30 回", "30 requests"),
    "contrib_500": ("納めた量 500", "500 delivered"), "contrib_1500": ("納めた量 1500", "1,500 delivered"),
    "contrib_4000": ("納めた量 4000", "4,000 delivered"), "members_10": ("10 人そろう", "10 members"), "members_25": ("25 人そろう", "25 members"),
}

# 取引所 (35 の 6.11。facility/trade/)
TRADE = [
    ("gui.skyabyss.trade.title", "取引所", "Exchange"),
    ("gui.skyabyss.trade.tab.market", "市", "Market"),
    ("gui.skyabyss.trade.tab.sell", "出す", "List"),
    ("gui.skyabyss.trade.tab.mine", "自分の品 %s/%s", "Mine %s/%s"),
    ("gui.skyabyss.trade.tab.applied", "申請", "Applied"),
    ("gui.skyabyss.trade.tab.mail", "受け取り", "Mailbox"),
    ("gui.skyabyss.trade.tab.mail_n", "受け取り (%s)", "Mailbox (%s)"),
    ("gui.skyabyss.trade.coins", "鐘銭", "Bell coins"),
    ("gui.skyabyss.trade.search", "品の名前で探す", "Search by name"),
    ("gui.skyabyss.trade.do_list", "出す", "List it"),
    ("gui.skyabyss.trade.do_offer", "申し出る", "Offer"),
    ("gui.skyabyss.trade.back", "戻る", "Back"),
    ("gui.skyabyss.trade.cancel", "取り下げる", "Withdraw"),
    ("gui.skyabyss.trade.cancel_sure", "本当に取り下げ？", "Really withdraw?"),
    ("gui.skyabyss.trade.claim", "受け取る", "Collect"),
    ("gui.skyabyss.trade.not_at_keeper", "出す・申請は取引所の番の前で (/trade では見る・受ける・断る・受け取るだけ)",
     "Listing and applying only at the trade keeper (/trade lets you view, accept, decline and collect)"),
    ("gui.skyabyss.trade.hint.market", "「申請」= ほしいもののまま、「別に」= 自分で選んだ品で申し出る。申請しても品は動かない",
     "Apply = pay what they ask; Other = offer your own goods. Applying moves nothing"),
    ("gui.skyabyss.trade.hint.sell", "持ち物を押すと出す品に (右で 1 つずつ)。出した品は取引所が預かり、7 日で戻る",
     "Click your items to put them up (right-click for one). The exchange holds them; they return after 7 days"),
    ("gui.skyabyss.trade.hint.sell_offer", "持ち物を押して申し出る品を選ぶ。受けられた瞬間に入れ替わる",
     "Click your items to choose an offer. Goods swap the moment it's accepted"),
    ("gui.skyabyss.trade.hint.mine", "✔ 受ける ・ ✘ 断る (相手がいて品がそろうとき)", "✔ accept · ✘ decline (while they're online with the goods)"),
    ("gui.skyabyss.trade.hint.applied", "相手が受けた瞬間に入れ替わる。申し出の品は持っておくこと", "Goods swap the moment the seller accepts. Keep your offer on you"),
    ("gui.skyabyss.trade.hint.mail", "取り下げ・期限切れで戻った品と、持ちきれなかった品", "Goods returned by withdrawal or expiry, and anything that didn't fit"),
    ("gui.skyabyss.trade.days_left", "あと %s 日", "%s days left"),
    ("gui.skyabyss.trade.hours_left", "あと %s 時間", "%s h left"),
    ("gui.skyabyss.trade.market_empty", "出品はまだない", "Nothing listed yet"),
    ("gui.skyabyss.trade.applied", "申請中", "Applied"),
    ("gui.skyabyss.trade.apply", "申請", "Apply"),
    ("gui.skyabyss.trade.counter", "別に", "Other"),
    ("gui.skyabyss.trade.give_head", "出す品 (取引所が預かる)", "You give (held by the exchange)"),
    ("gui.skyabyss.trade.offer_head", "申し出る品", "Your offer"),
    ("gui.skyabyss.trade.inventory", "持ち物", "Inventory"),
    ("gui.skyabyss.trade.coins_offer", "足す鐘銭", "Add bell coins"),
    ("gui.skyabyss.trade.for", "%s の出品へ", "To %s's listing"),
    ("gui.skyabyss.trade.they_give", "もらえる品", "You get"),
    ("gui.skyabyss.trade.they_want", "渡すもの", "You give"),
    ("gui.skyabyss.trade.col_pay", "渡すもの", "You give"),
    ("gui.skyabyss.trade.col_get", "もらえる品", "You get"),
    ("gui.skyabyss.trade.want_head", "ほしいもの (鐘銭と品)", "You want (coins and goods)"),
    ("gui.skyabyss.trade.search_head", "ほしい品 (左 +1・右 -1・シフト 8)", "Wanted (left +1 · right -1 · shift 8)"),
    ("gui.skyabyss.trade.search_tip", "名前を入れると品が出る", "Type a name to find goods"),
    ("gui.skyabyss.trade.search_none", "見つからない", "Nothing found"),
    ("gui.skyabyss.trade.mine_empty", "出品はない", "Nothing listed"),
    ("gui.skyabyss.trade.apps_head", "申請 %s ・ %s", "Applications %s · %s"),
    ("gui.skyabyss.trade.no_apps", "申請はまだない", "No applications yet"),
    ("gui.skyabyss.trade.away", "(不在)", "(away)"),
    ("gui.skyabyss.trade.ready", "そろっている", "ready"),
    ("gui.skyabyss.trade.short", "足りない", "short"),
    ("gui.skyabyss.trade.applied_empty", "出した申請はない", "No applications sent"),
    ("gui.skyabyss.trade.withdraw", "取り下げ", "Withdraw"),
    ("gui.skyabyss.trade.mail_empty", "受け取る品はない", "Nothing to collect"),
    ("message.skyabyss.trade.listed", "出品した。品は取引所が預かる", "Listed. The exchange holds your goods"),
    ("message.skyabyss.trade.applied", "申請した。品は受けられるまで手もとに", "Applied. Your goods stay with you until accepted"),
    ("message.skyabyss.trade.accepted", "取引が済んだ", "Trade complete"),
    ("message.skyabyss.trade.rejected", "申請を断った", "Application declined"),
    ("message.skyabyss.trade.withdrawn", "申請を取り下げた", "Application withdrawn"),
    ("message.skyabyss.trade.cancelled", "出品を取り下げた。品は戻した", "Listing withdrawn. Your goods are back"),
    ("message.skyabyss.trade.claimed", "受け取った", "Collected"),
    ("message.skyabyss.trade.claimed_part", "持ちきれない分は受け取りに残した", "What didn't fit stays in the mailbox"),
    ("message.skyabyss.trade.not_at_keeper", "出す・申請は取引所の番の前で", "List and apply at the trade keeper"),
    ("message.skyabyss.trade.too_many_listings", "出品は一人 10 まで", "Up to 10 listings each"),
    ("message.skyabyss.trade.too_many_apps", "出せる申請は 10 まで", "Up to 10 applications each"),
    ("message.skyabyss.trade.empty_give", "出す品を選んで", "Choose what to give"),
    ("message.skyabyss.trade.empty_want", "ほしいもの (品か鐘銭) を決めて", "Set what you want (goods or coins)"),
    ("message.skyabyss.trade.too_many_items", "品は 4 つまで", "Up to 4 kinds of goods"),
    ("message.skyabyss.trade.not_allowed", "大事な品は取引に出せない", "Important items can't be traded"),
    ("message.skyabyss.trade.bad_slot", "その枠は使えない", "That slot can't be used"),
    ("message.skyabyss.trade.lack", "品が足りない", "You don't have the goods"),
    ("message.skyabyss.trade.no_listing", "その出品はもうない", "That listing is gone"),
    ("message.skyabyss.trade.own_listing", "自分の出品には申請できない", "You can't apply to your own listing"),
    ("message.skyabyss.trade.already_applied", "もう申請している", "You've already applied"),
    ("message.skyabyss.trade.listing_full", "その出品は申請でいっぱい", "That listing has too many applications"),
    ("message.skyabyss.trade.not_seller", "出した人だけができる", "Only the seller can do that"),
    ("message.skyabyss.trade.no_application", "その申請はもうない", "That application is gone"),
    ("message.skyabyss.trade.applicant_away", "相手がいない。相手がいるときに受けて", "They're away. Accept while they're online"),
    ("message.skyabyss.trade.applicant_lacks", "相手の品が足りない。何も動かしていない", "They're short of the goods. Nothing moved"),
    ("message.skyabyss.trade.room_applicant", "相手の持ち物に空きがない。何も動かしていない", "They have no room. Nothing moved"),
    ("message.skyabyss.trade.room_seller", "持ち物に空きがない。空けてから受けて", "No room in your inventory. Make space first"),
    ("message.skyabyss.trade.nothing_to_claim", "受け取る品はない", "Nothing to collect"),
    ("message.skyabyss.trade.empty_offer", "申し出る品か鐘銭を選んで", "Choose goods or coins to offer"),
    ("message.skyabyss.trade.note_applied", "取引所: %s が出品 %s に申請した", "Exchange: %s applied to listing %s"),
    ("message.skyabyss.trade.note_withdrawn", "取引所: %s が出品 %s への申請を取り下げた", "Exchange: %s withdrew from listing %s"),
    ("message.skyabyss.trade.note_rejected", "取引所: %s が出品 %s への申請を断った", "Exchange: %s declined your application to listing %s"),
    ("message.skyabyss.trade.note_cancelled", "取引所: %s が出品 %s を取り下げた", "Exchange: %s withdrew listing %s"),
    ("message.skyabyss.trade.note_lacked", "取引所: %s が出品 %s で受けようとしたが、申し出の品が足りなかった", "Exchange: %s tried to accept you on listing %s, but you were short"),
    ("message.skyabyss.trade.note_no_room", "取引所: %s が出品 %s で受けようとしたが、持ち物に空きがなかった", "Exchange: %s tried to accept you on listing %s, but you had no room"),
    ("message.skyabyss.trade.note_sold", "取引所: %s との取引が済んだ (出品 %s)", "Exchange: trade with %s complete (listing %s)"),
    ("message.skyabyss.trade.note_accepted", "取引所: %s が申請を受けた。品を受け取った (出品 %s)", "Exchange: %s accepted. Goods received (listing %s)"),
    ("message.skyabyss.trade.note_taken", "取引所: %s の出品 %s は、ほかの人に決まった", "Exchange: %s's listing %s went to someone else"),
    ("message.skyabyss.trade.note_expired", "取引所: 出品 %s の期限が切れた。品は受け取りにある", "Exchange: listing %s expired. Your goods wait in the mailbox"),
    ("message.skyabyss.trade.note_closed", "取引所: %s の出品 %s は期限で閉じた", "Exchange: %s's listing %s closed"),
    ("message.skyabyss.trade.mail_waiting", "取引所の受け取りに品がある (/trade 4)", "Goods wait in your exchange mailbox (/trade 4)"),
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
    for key, ja, en in UI + TRADE:
        t(key, ja, en)
    for k, (ja, en) in PATTERNS.items():
        t("gui.skyabyss.guild.pattern." + k, ja, en)
    for k, (ja, en) in PLATES.items():
        t("gui.skyabyss.guild.plate." + k, ja, en)
    for k, (ja, en) in ACHIEVEMENTS.items():
        t("gui.skyabyss.guild.ach." + k, ja, en)
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
