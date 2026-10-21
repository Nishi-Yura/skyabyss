#!/usr/bin/env python3
"""都の町のクエスト (docs/design/35 の 3.5 F5) と裏通りの頼み (3.3 F6) のデータと文を書く。

  python tools/gen_town_quests.py

書くもの (毎回すべて書き直す。手で直さないこと):
  data/skyabyss/quest/town/<名前>.json               町のクエスト 8 本 (一〜四章に 2 本ずつ) と、墓守の 4 段・酔いどれの 3 段
  data/skyabyss/dialogue/guest_<人>.json             限定の人 8 人の会話
  data/skyabyss/dialogue/attendant_<役>_town.json    係の人の会話の足し書き ("extends": true。選択肢を「ここは何をする所」の前に足す)
  data/skyabyss/lore/entry/town/<名前>.json          伝承の項目 (手で記す。クエストと古井戸が記す)
  assets/skyabyss_town/lang/{ja_jp,en_us}.json       文 (クエスト・会話・人の名前・噂・知らせ・伝承・品)
仕組みは town パッケージ (TownQuests の一覧・TownGuests の置き場・合図)。クエストの ID と章は TownQuests.ALL と合わせる。
合図: town_hand:<相手> (会話で品を渡した)、town_at:<印> (印の近くに立った)、town_tower_bell (頂の鐘)、town_child_inn (子どもを宿へ)。
"""
import json
import os

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "src", "main", "resources")
QUEST = os.path.join(RES, "data", "skyabyss", "quest", "town")
DIALOGUE = os.path.join(RES, "data", "skyabyss", "dialogue")
LORE = os.path.join(RES, "data", "skyabyss", "lore", "entry", "town")
LANG = os.path.join(RES, "assets", "skyabyss_town", "lang")

JA = {}
EN = {}


def t(key, ja, en):
    assert key not in JA, key
    JA[key] = ja
    EN[key] = en


CHAPTER_ID = {1: "titan_whale", 2: "titan_tide", 3: "titan_storm", 4: "titan_ember"}
COIN = "skyabyss:bell_coin"
HOSTILE = ["minecraft:zombie", "minecraft:husk", "minecraft:skeleton", "minecraft:stray", "minecraft:spider",
           "minecraft:cave_spider", "minecraft:creeper", "minecraft:drowned"]


# ====================================================================== 条件・結果の小道具
def at(quest, stage):
    return {"type": "quest", "quest": quest, "is": "active", "stage": stage}


def done(quest):
    return {"type": "quest", "quest": quest, "is": "done"}


def none(quest):
    return {"type": "quest", "quest": quest, "is": "none"}


def item(i, n=1):
    return {"type": "item", "item": i, "count": n}


def all_(*cs):
    return {"type": "all", "of": list(cs)}


def stage_min(n):
    return {"type": "stage", "min": n}


def take(i, n=1):
    return {"type": "take", "item": i, "count": n}


def give(i, n=1):
    return {"type": "give", "item": i, "count": n}


def signal(s):
    return {"type": "signal", "signal": s}


def start(q):
    return {"type": "quest_start", "quest": q}


# ====================================================================== クエスト
QUESTS = []   # (名前, json)


def qkey(name):
    return "quest.skyabyss.town." + name


def quest(name, chapter, giver, title, summary, done_text, rumor, stages, reward, order):
    """stages: [(段 id, (journal ja, en), [目的], do)]。目的: dict に "text": (ja, en) を足したもの。"""
    base = qkey(name)
    t(base + ".title", *title)
    t(base + ".summary", *summary)
    t(base + ".done", *done_text)
    if rumor:
        t(base + ".rumor", *rumor)
    js = []
    for sid, journal, objectives, do in stages:
        t(base + "." + sid + ".journal", *journal)
        obs = []
        for ob in objectives:
            ob = dict(ob)
            text = ob.pop("text")
            t(base + "." + sid + "." + ob["id"], *text)
            obs.append(ob)
        st = {"id": sid, "objectives": obs}
        if do:
            st["do"] = do
        js.append(st)
    q = {"category": "sub", "order": order, "giver": giver, "chapter": CHAPTER_ID[chapter], "min_stage": chapter - 1,
         "stages": js, "reward": reward}
    QUESTS.append((name, q))


def talk(oid, role, topic, ja, en):
    return {"id": oid, "type": "talk", "target": role, "topic": topic, "text": (ja, en)}


def collect(oid, it, n, ja, en):
    return {"id": oid, "type": "collect", "target": it, "count": n, "text": (ja, en)}


def sig(oid, s, ja, en):
    return {"id": oid, "type": "signal", "target": s, "party": False, "text": (ja, en)}


def kill(oid, targets, n, ja, en):
    return {"id": oid, "type": "kill", "targets": targets, "count": n, "text": (ja, en)}


def reward(xp, coins, items=(), affinity=None):
    r = {"xp": xp, "items": [{"item": COIN, "count": coins}] + [dict(i) for i in items]}
    if affinity:
        r["affinity"] = affinity
    return r


# ====================================================================== 会話
DIALOGUES = {}   # ファイル名 → json


class D:
    """一人の会話 (または足し書き)。node の本文と選択肢の文は lang に書く。"""

    def __init__(self, role, extends=False):
        self.role = role
        self.base = "dialogue.skyabyss." + role.split(":")[1]
        self.extends = extends
        self.entries = []
        self.nodes = {}
        self.menu_options = []

    def entry(self, cond, node):
        e = {"node": node}
        if cond is not None:
            e["if"] = cond
        self.entries.append(e)

    def node(self, nid, text, face="neutral", nxt=None, options=None, do=None):
        n = {"face": face}
        if do:
            n["do"] = do
        if nxt:
            n["next"] = nxt
        if options:
            n["options"] = []
            for o in options:
                oid, label, goto = o[0], o[1], o[2]
                op = {"id": oid, "goto": goto}
                extra = o[3] if len(o) > 3 else {}
                op.update({k: v for k, v in extra.items()})
                op.setdefault("kind", "talk")
                n["options"].append(op)
                t(self.base + "." + nid + "." + oid, *label)
        self.nodes[nid] = n
        t(self.base + "." + nid, *text)

    def menu(self, oid, label, goto, cond=None, kind="quest", do=None):
        op = {"id": oid, "goto": goto, "kind": kind}
        if cond is not None:
            op["if"] = cond
        if do:
            op["do"] = do
        self.menu_options.append(op)
        t(self.base + ".menu." + oid, *label)

    def json(self):
        d = {"npc": self.role}
        if self.extends:
            d["extends"] = True
            if self.entries:
                d["entries"] = self.entries
            if self.menu_options:
                d["add_options"] = {"menu": {"before": "about", "options": self.menu_options}}
        else:
            d["entries"] = self.entries
        d["nodes"] = self.nodes
        return d


def guest(gid, ja, en):
    t("entity.skyabyss.guest_" + gid, ja, en)
    d = D("skyabyss:guest_" + gid)
    DIALOGUES["guest_" + gid] = d
    return d


def ext(role):
    name = "attendant_" + role + "_town"
    if name not in DIALOGUES:
        DIALOGUES[name] = D("skyabyss:attendant_" + role, extends=True)
    return DIALOGUES[name]


OK = ("ok", ("わかった", "All right"), "end")
LATER = ("later", ("また来る", "I'll come back"), "end", {"kind": "leave"})


def hand_option(label, cond, do, goto):
    return ("give", label, goto, {"kind": "give", "if": cond, "do": do})


# ====================================================================== 伝承
LORES = []   # (名前, 章, order)


def lore(name, chapter, order, title, body):
    LORES.append((name, chapter, order))
    t("lore.skyabyss.town." + name + ".title", *title)
    t("lore.skyabyss.town." + name + ".body", *body)


# ######################################################################
# 一章: 見習いの鯨追い
# ######################################################################
Q = "town/ch1_apprentice"
quest("ch1_apprentice", 1, "skyabyss:guest_apprentice",
      ("見習いの鯨追い", "The Apprentice Chaser"),
      ("組合の登録の試験を、鯨追いの見習いと一緒に受ける。", "Sit the guild's registration test together with an apprentice whale chaser."),
      ("見習いの名が、組合の名簿に刻まれた。隣の行は、まだ空いている。",
       "The apprentice's name is carved into the guild roll. The line beside it is still blank."),
      ("組合の登録の試験を控えた見習いが、南の通りの元兵士の家に下宿してる。一人じゃ心細いってさ。顔を出してやりな。",
       "An apprentice is lodging at the old soldier's house on the south road, about to sit the guild's test. Says it's lonely going alone. Go say hello."),
      [("meet", ("宿の女将の噂。組合の登録の試験を控えた見習いが、南の通りの元兵士の家に下宿しているという。",
                 "A rumour from the innkeeper: an apprentice due to sit the guild's registration test lodges at the old soldier's house on the south road."),
        [talk("meet", "skyabyss:guest_apprentice", "meet", "鯨追いの見習いに会う（南の通りの元兵士の家）",
              "Meet the apprentice chaser (old soldier's house, south road)")], None),
       ("clerk", ("見習いは、一人で試験を受けるのが怖いという。まず組合の受付で、試験の中身を聞いてこよう。",
                  "The apprentice is afraid to sit the test alone. First, ask the guild desk what the test involves."),
        [talk("clerk", "skyabyss:attendant_guild_clerk", "town_test", "組合の受付で、試験のことを聞く", "Ask about the test at the guild desk")], None),
       ("hunt", ("試験は二つ。夜に出る獣を四つ退けること。骨を三つ持ち帰ること。背の上で要るのは、その二つだけだそうだ。",
                 "Two tasks: drive off four night beasts, and bring back three bones. That's all you need on a titan's back, they say."),
        [kill("beasts", HOSTILE, 4, "夜の獣を退ける", "Drive off night beasts"),
         collect("bones", "minecraft:bone", 3, "骨を集める", "Gather bones")], None),
       ("hand", ("そろった。見習いのところへ戻って、骨を渡そう。", "That's everything. Go back to the apprentice and hand over the bones."),
        [sig("hand", "town_hand:guest_apprentice", "見習いに骨を渡す", "Give the bones to the apprentice")], None)],
      reward(50, 12, [{"item": "minecraft:iron_ingot", "count": 2}]), 210)

g = guest("apprentice", "鯨追いの見習い", "Apprentice Chaser")
g.entry(at(Q, "meet"), "meet")
g.entry(at(Q, "clerk"), "wait_clerk")
g.entry(at(Q, "hunt"), "wait_hunt")
g.entry(at(Q, "hand"), "hand_ask")
g.entry(None, "idle")
g.node("meet", ("あ……あんたも鯨追いか？　おれ、まだ見習いでさ。組合の試験を、一人で受けるのが怖くて。",
                "Oh... are you a whale chaser too? I'm only an apprentice. I'm scared to sit the guild's test alone."), nxt="meet2")
g.node("meet2", ("一緒に受けてくれないか。受付の書記に、試験のことを聞いてきてほしいんだ。背の上に乗る人は、みんなこの試験を通ったんだって。前に来た旅人も、きっと。",
                 "Will you sit it with me? Ask the clerk at the desk what the test is. Everyone who rides a titan's back passed it, they say. The traveller who came before, too, I bet."),
       options=[("ok", ("任せて", "Leave it to me"), "end")])
g.node("wait_clerk", ("受付の書記は、東の大通りの組合にいる。おれはここで縄の結び方を練習しとく。",
                      "The clerk's at the guild hall on the east avenue. I'll practise my knots here."))
g.node("wait_hunt", ("夜の獣を四つ。骨を三つ。……あんたが前に出てくれると助かる。おれは後ろで、数を数えとく。",
                     "Four night beasts. Three bones. ...It'd help if you went first. I'll stay behind and keep count."))
g.node("hand_ask", ("どうだった？　骨は三つあるかい？", "How did it go? Have you got three bones?"),
       options=[hand_option(("骨を三つ渡す", "Hand over three bones"), item("minecraft:bone", 3),
                            [take("minecraft:bone", 3), signal("town_hand:guest_apprentice")], "passed"), LATER])
g.node("passed", ("やった……！　これで二人とも名簿に載る。書記が言ってた。名簿の古い頁に、名前の書いてない行があるんだって。",
                  "We did it...! We'll both be on the roll now. The clerk said there's a line on an old page with no name written in it."),
       face="smile", nxt="passed2")
g.node("passed2", ("誰かが、名を書く前にいなくなったのかな。……これ、親父の形見の鉄。おれにはまだ重い。あんたが使ってくれ。",
                   "Maybe someone left before they could write it. ...Here, my father's iron. Still too heavy for me. You use it."),
       options=[OK])
g.node("idle", ("縄の結び方なら、もう目をつぶってもできるんだ。", "I can tie these knots with my eyes shut now."))

c = ext("guild_clerk")
c.menu("town_test", ("見習いの試験のこと", "About the apprentice's test"), "town_test", at(Q, "clerk"))
c.node("town_test", ("見習いの付き添いですね。試験は二つ。夜の獣を四つ退けること、骨を三つ持ち帰ること。背の上で要るのは、それだけです。",
                     "You're with the apprentice? The test has two parts: drive off four night beasts, and bring back three bones. That's all a back demands."),
       nxt="town_test2")
c.node("town_test2", ("名簿の古い頁ですか？　……ええ、名のない行が一つあります。墨は乾いているのに、字だけがない。",
                      "The old pages of the roll? ...Yes, there's one line without a name. The ink is dry, but there are no letters."),
       face="grave", options=[OK])

# ######################################################################
# 一章: 鐘守の頼み
# ######################################################################
Q = "town/ch1_bell"
quest("ch1_bell", 1, "skyabyss:guest_bellkeeper",
      ("鐘守の頼み", "The Bell Keeper's Request"),
      ("大鐘楼の頂の鐘を、膝の悪い鐘守の代わりに鳴らす。", "Ring the bell atop the great tower for the old bell keeper."),
      ("町はまた日を数えはじめた。鐘守は、二つ目の音のことを二度と口にしなかった。",
       "The town counts its days again. The bell keeper never spoke of the second toll again."),
      ("大鐘楼の鐘守のじいさんが、てっぺんの鐘を鳴らせる若い足を探してたよ。広場の西の、書き役の家に間借りしてる。",
       "The old bell keeper of the great tower is looking for young legs to ring the top bell. He rooms at the scribe's house west of the square."),
      [("meet", ("宿の女将の噂。大鐘楼の鐘守が、頂の鐘を鳴らせる者を探している。広場の西の、書き役の家にいるという。",
                 "A rumour from the innkeeper: the bell keeper wants someone to ring the top bell. He's at the scribe's house west of the square."),
        [talk("meet", "skyabyss:guest_bellkeeper", "meet", "鐘守に会う（広場の西の書き役の家）", "Meet the bell keeper (scribe's house, west of the square)")], None),
       ("ring", ("鐘守の膝は、もう塔の階段に耐えない。頂の鐘は、空の影が通った晩から一度も鳴っていない。大鐘楼を上り、頂の鐘を一度だけ鳴らす。",
                 "His knees can't take the tower stairs. The top bell hasn't rung since the night the shadow passed. Climb the tower and ring it once."),
        [sig("ring", "town_tower_bell", "大鐘楼の頂の鐘を鳴らす", "Ring the bell atop the great tower")], None),
       ("back", ("鳴らした。下りて、鐘守に知らせよう。", "It rang. Go down and tell the bell keeper."),
        [talk("back", "skyabyss:guest_bellkeeper", "back", "鐘守に知らせる", "Tell the bell keeper")], [{"type": "lore", "entry": "town/bell_echo"}])],
      reward(40, 10), 211)

g = guest("bellkeeper", "鐘守", "Bell Keeper")
g.entry(at(Q, "meet"), "meet")
g.entry(at(Q, "ring"), "wait_ring")
g.entry(at(Q, "back"), "back")
g.entry(None, "idle")
g.node("meet", ("おお、若いの。わしは鐘守だ。……鐘守だった、と言うべきかな。膝がもう、塔の階段を許してくれん。",
                "Ah, young one. I'm the bell keeper. ...Was, I should say. My knees won't allow the tower stairs any more."), nxt="meet2")
g.node("meet2", ("頂の鐘は、空の影が通った晩から鳴っておらん。鐘は日を数える。数えるのをやめた町は、自分が何日目かを忘れる。",
                 "The top bell hasn't rung since the night the shadow passed. A bell counts the days. A town that stops counting forgets which day it is."),
       options=[("ok", ("鳴らしてくる", "I'll ring it"), "end"), ("why", ("一階の鐘ではだめなのか", "Won't the bell downstairs do?"), "why")])
g.node("why", ("一階のは人を呼ぶ鐘だ。頂のは、空に聞かせる鐘だ。……一度だけでいい。一度だけ鳴らしたら、すぐ下りておいで。",
               "The one below calls people. The one on top speaks to the sky. ...Once is enough. Ring it once, then come straight down."),
       options=[OK])
g.node("wait_ring", ("塔は北の段の上だ。一度だけ鳴らしたら、すぐ下りておいで。", "The tower stands on the north terrace. Ring it once, then come straight down."))
g.node("back", ("聞こえたよ。……鳴らしたのは、一度だけかい？", "I heard it. ...You rang it only once?"), face="grave", nxt="back2")
g.node("back2", ("二つ目の音がした。遅れて、同じ高さから。こだまにしては、はっきりしすぎていた。",
                 "There was a second toll. Late, and from the same height. Too clear for an echo."), face="grave", nxt="back3")
g.node("back3", ("いや、年寄りの耳だ。ありがとう。今日から、町はまた日を数えられる。", "No, just an old man's ears. Thank you. From today the town can count its days again."),
       face="smile", options=[OK])
g.node("idle", ("鐘の音が、今日も同じ高さで鳴ればいい。", "May the bell ring at the same pitch today, too."))
lore("bell_echo", "titan_whale", 10, ("頂の鐘のこだま", "The Echo of the Top Bell"),
     ("大鐘楼の頂の鐘を、一度だけ鳴らした。\n\n鐘守は、二つ目の音を聞いたという。遅れて、同じ高さから。\n\n塔の上には、私しかいなかった。",
      "I rang the bell atop the great tower, once.\n\nThe bell keeper says he heard a second toll. Late, and from the same height.\n\nThere was no one on the tower but me."))

# ######################################################################
# 二章: 漁師の網
# ######################################################################
Q = "town/ch2_net"
quest("ch2_net", 2, "skyabyss:guest_fisher",
      ("漁師の網", "The Fisher's Net"),
      ("潮殻から逃げてきた漁師の網を繕い、潮の時の市に出られるようにする。",
       "Mend the net of a fisher who fled the tide shell, so he can sell at the tide market."),
      ("潮の時の市に、久しぶりに魚が並んだ。", "For the first time in a long while, fish lie on the tide market's stalls."),
      ("潮殻のせいで浜を捨てた漁師が、東の大通りの家に転がり込んだ。潮の時の市に出たいのに、網が破れたままだとさ。",
       "A fisher who abandoned the shore because of the tide shell has moved into a house on the east avenue. Wants to sell at the tide market, but his net is torn."),
      [("meet", ("宿の女将の噂。浜を捨てた漁師が、東の大通りの漁師の家にいるという。",
                 "A rumour from the innkeeper: a fisher who fled the shore is staying at the fisher's house on the east avenue."),
        [talk("meet", "skyabyss:guest_fisher", "meet", "漁師に会う（東の大通りの漁師の家）", "Meet the fisher (fisher's house, east avenue)")], None),
       ("gather", ("漁師は、潮殻が浜に寄った晩に網を捨てて逃げてきた。網を繕う糸が八本、市に並べる鱈が三尾いる。",
                   "He dropped his net and ran the night the tide shell came ashore. He needs eight strings to mend it and three cod for the market."),
        [collect("string", "minecraft:string", 8, "網を繕う糸を集める", "Gather string to mend the net"),
         collect("cod", "minecraft:cod", 3, "生の鱈を獲る", "Catch raw cod")], None),
       ("hand", ("そろった。漁師に渡そう。", "That's everything. Take it to the fisher."),
        [sig("hand", "town_hand:guest_fisher", "漁師に糸と鱈を渡す", "Give the string and cod to the fisher")], None),
       ("market", ("網は繕えた。市に並べるには、道具屋の店先を借りるのが早いらしい。漁師は口下手だ。",
                   "The net is mended. The quickest way to a stall is to borrow the tool merchant's shopfront. The fisher isn't one for talking."),
        [talk("market", "skyabyss:attendant_tool_merchant", "town_market", "道具屋に、市の場所を頼む", "Ask the tool merchant for a market spot")], None),
       ("back", ("場所が取れた。漁師に知らせよう。", "The spot is secured. Tell the fisher."),
        [talk("back", "skyabyss:guest_fisher", "back", "漁師に知らせる", "Tell the fisher")], None)],
      reward(60, 20, [{"item": "minecraft:fishing_rod", "count": 1}]), 220)

g = guest("fisher", "浜を捨てた漁師", "Shoreless Fisher")
g.entry(at(Q, "meet"), "meet")
g.entry(at(Q, "gather"), "wait_gather")
g.entry(at(Q, "hand"), "hand_ask")
g.entry(at(Q, "market"), "wait_market")
g.entry(at(Q, "back"), "back")
g.entry(None, "idle")
g.node("meet", ("あんた、都の人かい。おれは東の浜の漁師だ。……漁師だった。潮殻が寄った晩、浜の水がいっぺんに引いてな。網を捨てて逃げてきた。",
                "You from the capital? I fished the east shore. ...Used to. The night the tide shell came, the sea pulled back all at once. I dropped my net and ran."),
       nxt="meet2")
g.node("meet2", ("潮が戻る日には市が立つ。潮の時の市だ。そこに魚を並べりゃ、また食っていける。けど網がこのざまだ。糸を八本と、鱈を三尾。頼めるか。",
                 "When the tide returns, there's a market. The tide market. Put fish on a stall there and I could eat again. But look at this net. Eight strings and three cod. Can you manage?"),
       options=[OK])
g.node("wait_gather", ("糸は蜘蛛から、鱈は川か海で。……海が怖けりゃ、川でいい。", "String from spiders, cod from river or sea. ...If the sea scares you, the river's fine."))
g.node("hand_ask", ("糸と鱈、そろったかい？", "Got the string and the cod?"),
       options=[hand_option(("糸と鱈を渡す", "Hand over the string and cod"), all_(item("minecraft:string", 8), item("minecraft:cod", 3)),
                            [take("minecraft:string", 8), take("minecraft:cod", 3), signal("town_hand:guest_fisher")], "handed"), LATER])
g.node("handed", ("ありがてえ。これで網が張れる。市には道具屋の店先を借りたいんだが、おれは口下手でな。あんたから頼んでくれないか。",
                  "Bless you. Now I can cast again. I'd like to borrow the tool merchant's shopfront for the market, but I'm no talker. Would you ask for me?"),
       face="smile", options=[OK])
g.node("wait_market", ("道具屋は広場の東の角だ。", "The tool shop's on the east corner of the square."))
g.node("back", ("道具屋が場所をくれた！　……逃げる前の晩、網に真珠みたいなのがかかった。覗くと、こっちを見返してくる気がして、海へ返した。",
                "The merchant gave us a spot! ...The night before I ran, something like a pearl caught in my net. When I looked in, it seemed to look back. I threw it into the sea."),
       face="smile", nxt="back2")
g.node("back2", ("あんたのほうが、海に出るんだろう。網の代わりに竿をやる。浜に戻れる日が来たら、また会おう。",
                 "You're the one heading out to sea, I reckon. Take a rod instead of a net. When I can go back to the shore, let's meet again."),
       options=[OK])
g.node("idle", ("潮の匂いが、まだ指に残ってる。", "The smell of the tide is still on my fingers."))

c = ext("tool_merchant")
c.menu("town_market", ("漁師の市のこと", "About the fisher's market"), "town_market", at(Q, "market"))
c.node("town_market", ("漁師の魚を並べたい？　潮の時の市の日なら、店先を半分貸してやるよ。……潮殻の噂のせいで、魚は高く売れるだろうさ。",
                       "Lay out the fisher's catch? On tide market days I'll lend him half my shopfront. ...With all the tide shell talk, fish will sell dear."),
       options=[OK])

# ######################################################################
# 二章: 旅の吟遊詩人
# ######################################################################
Q = "town/ch2_bard"
quest("ch2_bard", 2, "skyabyss:guest_bard",
      ("旅の吟遊詩人", "The Wandering Bard"),
      ("途中で切れた海の歌の続きを、都の人から集める。", "Gather the lost verses of a sea song from the people of the capital."),
      ("歌は続いた。海が人を数える歌として。", "The song goes on now: a song of the sea counting people."),
      ("吟遊詩人が南の大通りの家を借りた。海の歌の続きを探してるそうだ。都の人の話を聞きたいって。",
       "A bard has taken a house on the south avenue. He's hunting for the rest of a sea song, and wants to hear from the townsfolk."),
      [("meet", ("宿の女将の噂。吟遊詩人が、南の大通りの家を借りているという。",
                 "A rumour from the innkeeper: a bard is renting a house on the south avenue."),
        [talk("meet", "skyabyss:guest_bard", "meet", "吟遊詩人に会う（南の大通りの仮住まい）", "Meet the bard (lodgings on the south avenue)")], None),
       ("listen", ("吟遊詩人の歌は「潮が引くと、道が出る」で切れている。止まり木の番、宿の女将、預かり番が、続きを知っているかもしれない。",
                   "The bard's song stops at \"When the tide draws back, a road appears.\" The perch keeper, the innkeeper and the storage keeper may know more."),
        [talk("perch", "skyabyss:attendant_perch_keeper", "town_sea", "止まり木の番に、海の歌のことを聞く", "Ask the perch keeper about the sea song"),
         talk("inn", "skyabyss:attendant_innkeeper", "town_sea", "宿の女将に、海の歌のことを聞く", "Ask the innkeeper about the sea song"),
         talk("stash", "skyabyss:attendant_storage_keeper", "town_sea", "預かり番に、海の歌のことを聞く", "Ask the storage keeper about the sea song")], None),
       ("back", ("三つの節がそろった。吟遊詩人に伝えよう。", "Three verses gathered. Take them to the bard."),
        [talk("back", "skyabyss:guest_bard", "back", "吟遊詩人に聞いた節を伝える", "Bring the verses to the bard")], None)],
      reward(50, 15, [{"item": "minecraft:music_disc_far", "count": 1}]), 221)

g = guest("bard", "旅の吟遊詩人", "Wandering Bard")
g.entry(at(Q, "meet"), "meet")
g.entry(at(Q, "listen"), "wait_listen")
g.entry(at(Q, "back"), "back")
g.entry(None, "idle")
g.node("meet", ("やあ、旅の人。わたしは歌を拾って歩く者だ。海の歌を一つ持っているが、途中で切れている。「潮が引くと、道が出る」……その先がない。",
                "Well met, traveller. I gather songs as I walk. I have a sea song, but it breaks off. \"When the tide draws back, a road appears\"... and nothing after."),
       nxt="meet2")
g.node("meet2", ("都の人なら、続きを覚えているかもしれない。止まり木の番、宿の女将、預かり番。三人の話を聞いてきてくれないか。",
                 "Someone in the capital may remember the rest. The perch keeper, the innkeeper, the storage keeper. Would you ask the three of them for me?"),
       options=[OK])
g.node("wait_listen", ("三人の話を、そのまま持ってきておくれ。歌は、ほかの人の口でしか育たない。",
                       "Bring me their words just as they say them. A song only grows in other people's mouths."))
g.node("back", ("聞かせておくれ。……「道の先で、殻が鳴る」「鳴るたびに、一人戻らない」「戻らない者は、潮の底で数を数える」。",
                "Let me hear them. ...\"At the road's end, a shell rings.\" \"Each time it rings, one does not return.\" \"Those who do not return count in the tide's deep.\""),
       face="grave", nxt="back2")
g.node("back2", ("ああ、続いた。続いてしまった。……これは、海が人を数える歌だったのか。礼にこれを。わたしの古い盤だ。遠い所の歌が入っている。",
                 "Ah, it continues. It continued. ...So this was a song of the sea counting people. Take this as thanks. An old disc of mine, with a song from somewhere far away."),
       options=[OK])
g.node("idle", ("♪ 潮が引くと、道が出る……", "♪ When the tide draws back, a road appears..."))

for role, ja, en in (("perch_keeper", "海の歌？　子どもの頃、浜で聞いたよ。「道の先で、殻が鳴る」。それしか覚えてない。",
                      "The sea song? I heard it on the beach as a child. \"At the road's end, a shell rings.\" That's all I remember."),
                     ("innkeeper", "続き？　うちの祖母が歌ってた。「鳴るたびに、一人戻らない」。……寝る前に歌う歌じゃないね。",
                      "The rest? My grandmother used to sing it. \"Each time it rings, one does not return.\" ...Not a song for bedtime."),
                     ("storage_keeper", "預かったまま取りに来ない荷が、浜の者のには多い。……歌なら知ってる。「戻らない者は、潮の底で数を数える」。",
                      "Folk from the shore leave many parcels here they never collect. ...I know the song. \"Those who do not return count in the tide's deep.\"")):
    c = ext(role)
    c.menu("town_sea", ("海の歌のこと", "About the sea song"), "town_sea", at(Q, "listen"))
    c.node("town_sea", (ja, en), face="grave", options=[OK])

# ######################################################################
# 三章: 雷の夜
# ######################################################################
Q = "town/ch3_thunder"
quest("ch3_thunder", 3, "skyabyss:guest_child",
      ("雷の夜", "Thunder Night"),
      ("雷の晩に隠れたまま戻らない子どもを見つけ、宿へ連れて行く。", "Find the child who hid on a thunder night and never came home, and bring her to the inn."),
      ("子どもは宿の寝台で眠った。絵を一枚、置いていった。", "The child fell asleep in a bed at the inn. She left a drawing behind."),
      ("子だくさんの家の末の子が、雷が鳴るとどこかへ隠れちまう。昨夜もそうで、まだ見つからないって。……南の通りの喪の家のほうへ行ったのを見た者がいる。",
       "The youngest of the big family hides whenever thunder rolls. She did it last night and still hasn't turned up. ...Someone saw her heading toward the house of mourning on the south road."),
      [("find", ("子だくさんの家の末の子が、雷の晩に隠れたまま戻らない。南の通りの喪の家のほうへ行ったのを見た者がいる。",
                 "The youngest of the big family hid on a thunder night and hasn't come home. She was seen heading toward the house of mourning on the south road."),
        [talk("find", "skyabyss:guest_child", "find", "子どもを探す（南の通りの喪の家）", "Find the child (house of mourning, south road)")], None),
       ("escort", ("子どもは、使われていない寝台の上で丸くなっていた。宿なら灯りがある。手を引いて、西の大通りの宿「鐘の宿」まで連れて行こう。",
                   "She was curled up on a bed no one uses. The inn has lights. Take her hand and walk her to the Bell Inn on the west avenue."),
        [sig("inn", "town_child_inn", "子どもを宿「鐘の宿」へ連れて行く", "Walk the child to the Bell Inn")], None),
       ("inn", ("宿に着いた。女将に子どもを頼もう。", "You've reached the inn. Ask the innkeeper to look after her."),
        [talk("ask", "skyabyss:attendant_innkeeper", "town_child", "宿の女将に子どもを頼む", "Leave the child with the innkeeper")], None)],
      reward(70, 20, [{"item": "minecraft:paper", "count": 1, "token": "child_drawing", "name": "item.skyabyss.town.child_drawing",
                       "lore": ["item.skyabyss.town.child_drawing.lore"]}]), 230)
t("item.skyabyss.town.child_drawing", "子どもの絵", "A Child's Drawing")
t("item.skyabyss.town.child_drawing.lore", "空に大きな翼。その下に、手をつないだ子が二人。一人は顔が塗りつぶしてある。",
  "A great wing in the sky. Beneath it, two children holding hands. One face is scribbled out.")

g = guest("child", "雷を怖がる子", "Child Afraid of Thunder")
g.entry(at(Q, "find"), "find")
g.entry(at(Q, "escort"), "escort")
g.entry(at(Q, "inn"), "at_inn")
g.entry(None, "idle")
g.node("find", ("……だれ？　かみなりの人？", "...Who's there? Are you the thunder?"), face="grave", nxt="find2")
g.node("find2", ("ここの寝台、だれも使ってないの。ここなら、かみなりに見つからないと思った。", "Nobody uses this bed. I thought the thunder wouldn't find me here."),
       face="grave", nxt="find3")
g.node("find3", ("……でもね、夜になると、この部屋にもう一人いるの。わたしと同じくらいの子。しゃべらないの。",
                 "...But at night there's someone else in this room. A kid my size. She never talks."),
       face="grave", options=[("go", ("宿へ行こう。灯りがある", "Let's go to the inn. There are lights there"), "find_go")])
g.node("find_go", ("……手、つないでいい？", "...Can I hold your hand?"), face="smile", options=[OK])
g.node("escort", ("はなさないでね。", "Don't let go, okay?"))
g.node("at_inn", ("ここ、あったかい。……あの子も、つれてきてあげればよかった。", "It's warm here. ...I should have brought her too."), face="grave")
g.node("idle", ("かみなり、もう行った？", "Is the thunder gone yet?"))

c = ext("innkeeper")
c.menu("town_child", ("迷子の子のこと", "About the lost child"), "town_child", at(Q, "inn"))
c.node("town_child", ("まあ、この子！　家の人がずっと探してたよ。……おいで、温めた乳がある。今夜はうちの寝台でお休み。",
                      "Oh, this child! Her family's been searching all night. ...Come here, there's warm milk. You'll sleep in one of our beds tonight."),
       face="smile", nxt="town_child2")
c.node("town_child2", ("喪の家にいた？　……あそこの子は、三年前の雷の晩にいなくなったんだよ。この子と同じくらいの年でね。",
                       "She was in the house of mourning? ...That family's girl vanished on a thunder night three years ago. About this one's age."),
       face="grave", options=[OK])

# ######################################################################
# 三章: 機織りの針
# ######################################################################
Q = "town/ch3_weaver"
quest("ch3_weaver", 3, "skyabyss:guest_weaver",
      ("機織りの針", "The Weaver's Needle"),
      ("雷で溶けた織り機の針の代わりに、銅と避雷の針を届ける。", "Bring copper and a lightning rod to replace the loom needle melted by lightning."),
      ("織り機はまた動きだした。雷の晩には、翼の模様が勝手に出るという。", "The loom runs again. On thunder nights, they say, a wing pattern weaves itself in."),
      ("西の機織りの家、雷で織り機の針が溶けたそうだ。銅なら雷を逃がすって言い張ってる。",
       "Lightning melted the needle in the weaver's loom, west avenue. She insists copper will let the lightning pass."),
      [("meet", ("宿の女将の噂。西の大通りの機織りの家で、雷が織り機の針を溶かしたという。",
                 "A rumour from the innkeeper: lightning melted a loom needle at the weaver's house on the west avenue."),
        [talk("meet", "skyabyss:guest_weaver", "meet", "機織りに会う（西の大通りの機織りの家）", "Meet the weaver (weaver's house, west avenue)")], None),
       ("gather", ("風の民は銅で雷を逃がしたという。銅の延べ棒を六つと、屋根に立てる避雷針を一本。",
                   "The wind folk let lightning pass through copper, they say. Six copper ingots, and one lightning rod for the roof."),
        [collect("copper", "minecraft:copper_ingot", 6, "銅の延べ棒を集める", "Gather copper ingots"),
         collect("rod", "minecraft:lightning_rod", 1, "避雷針を作る", "Craft a lightning rod")], None),
       ("hand", ("そろった。機織りに渡そう。", "That's everything. Take it to the weaver."),
        [sig("hand", "town_hand:guest_weaver", "機織りに銅と避雷針を渡す", "Give the copper and lightning rod to the weaver")], None)],
      reward(60, 25, [{"item": "minecraft:spyglass", "count": 1}]), 231)

g = guest("weaver", "機織り", "Weaver")
g.entry(at(Q, "meet"), "meet")
g.entry(at(Q, "gather"), "wait")
g.entry(at(Q, "hand"), "hand_ask")
g.entry(None, "idle")
g.node("meet", ("見ておくれ、この針。雷が窓から入って、織り機の鉄の針だけを溶かしていった。布には焦げ一つないのに。",
                "Look at this needle. Lightning came in through the window and melted only the loom's iron needle. Not a scorch on the cloth."),
       nxt="meet2")
g.node("meet2", ("風の民は銅で雷を逃がしたって、母から聞いた。銅を六つ。それと屋根に立てる避雷針を一本。そうすれば、また織れる。",
                 "My mother said the wind folk let lightning pass through copper. Six copper ingots, and a lightning rod for the roof. Then I can weave again."),
       options=[OK])
g.node("wait", ("銅は山の浅い所に、緑の筋で出る。避雷針は、銅を三つ縦に並べて作るんだよ。",
                "Copper shows as green streaks, not deep in the hills. A lightning rod is three ingots stacked upright."))
g.node("hand_ask", ("銅と避雷針、そろったかい？", "Have you got the copper and the rod?"),
       options=[hand_option(("銅と避雷針を渡す", "Hand over the copper and the rod"),
                            all_(item("minecraft:copper_ingot", 6), item("minecraft:lightning_rod", 1)),
                            [take("minecraft:copper_ingot", 6), take("minecraft:lightning_rod", 1), signal("town_hand:guest_weaver")], "handed"), LATER])
g.node("handed", ("これでいい。……雷の晩に織った布には、ときどき模様が勝手に出るんだ。翼の形の。見たことのない鳥なのに、懐かしい。",
                  "That'll do. ...Cloth woven on thunder nights sometimes grows a pattern by itself. A wing. A bird I've never seen, and yet it feels familiar."),
       face="smile", nxt="handed2")
g.node("handed2", ("礼に、遠目の筒をあげよう。父が峰で使っていたものだ。高い所から、空の影がよく見える。",
                   "Take this spyglass as thanks. My father used it on the peaks. From somewhere high, you can see the sky's shadow clearly."),
       options=[OK])
g.node("idle", ("糸は切れても結べる。針は溶けたら、それまで。", "A broken thread can be tied. A melted needle is gone for good."))

# ######################################################################
# 四章: 灰の弟子
# ######################################################################
Q = "town/ch4_ash"
quest("ch4_ash", 4, "skyabyss:guest_ash_apprentice",
      ("灰の弟子", "The Ash Apprentice"),
      ("余燼の山の灰を溶かす新しい炉を、鍛冶場の弟子と組む。", "Help the smith's apprentice build a new forge that can melt the ash of the ember mountain."),
      ("鍛冶場に新しい炉が灯った。火の中に、下へ伸びる糸が見えるという。", "A new forge burns in the smithy. Some say a thread of light runs down through its fire."),
      ("鍛冶場の弟子が、南の山から灰を運んできた。新しい炉を組むんだと息巻いてる。",
       "The smith's apprentice hauled ash back from the southern mountain. He's fired up about building a new forge."),
      [("meet", ("宿の女将の噂。鍛冶場の弟子が、余燼の山の灰で新しい炉を組もうとしている。南の通りの弟子の下宿にいるという。",
                 "A rumour from the innkeeper: the smith's apprentice wants a new forge for the ember mountain's ash. He's at the apprentices' lodging on the south road."),
        [talk("meet", "skyabyss:guest_ash_apprentice", "meet", "鍛冶場の弟子に会う（南の通りの弟子の下宿）", "Meet the smith's apprentice (apprentices' lodging, south road)")], None),
       ("gather", ("親方の炉では、余燼の灰は溶けない。溶鉱炉を一つと、炉の底に敷くネザーのマグマブロックを四つ。",
                   "The master's forge can't melt ember ash. One blast furnace, and four magma blocks from the Nether to line its floor."),
        [collect("furnace", "minecraft:blast_furnace", 1, "溶鉱炉を作る", "Craft a blast furnace"),
         collect("magma", "minecraft:magma_block", 4, "ネザーのマグマブロックを集める", "Gather magma blocks from the Nether")], None),
       ("hand", ("そろった。弟子に渡そう。", "That's everything. Take it to the apprentice."),
        [sig("hand", "town_hand:guest_ash_apprentice", "弟子に溶鉱炉とマグマブロックを渡す", "Give the blast furnace and magma to the apprentice")], None),
       ("smith", ("炉は組めた。弟子は、親方に言うのを怖がっている。代わりに鍛冶師に知らせよう。",
                  "The forge is built. The apprentice is afraid to tell his master. Tell the smith yourself."),
        [talk("smith", "skyabyss:attendant_smith", "town_forge", "鍛冶師に新しい炉のことを話す", "Tell the smith about the new forge")], None),
       ("back", ("鍛冶師は炉を認めた。弟子に伝えよう。", "The smith approved. Let the apprentice know."),
        [talk("back", "skyabyss:guest_ash_apprentice", "back", "弟子に知らせる", "Tell the apprentice")], None)],
      reward(80, 30, [{"item": "minecraft:blaze_powder", "count": 2}]), 240)

g = guest("ash_apprentice", "灰を運ぶ弟子", "Ash-Bearing Apprentice")
g.entry(at(Q, "meet"), "meet")
g.entry(at(Q, "gather"), "wait")
g.entry(at(Q, "hand"), "hand_ask")
g.entry(at(Q, "smith"), "wait_smith")
g.entry(at(Q, "back"), "back")
g.entry(None, "idle")
g.node("meet", ("あんた、余燼の山へ行くんだろ？　おれは鍛冶場の弟子だ。山の灰を袋に詰めて運んできた。この灰は、冷めないんだ。",
                "You're headed for the ember mountain, right? I'm apprenticed at the smithy. I bagged ash from the mountain and hauled it here. This ash never cools."),
       nxt="meet2")
g.node("meet2", ("親方の炉じゃ、この灰は溶けない。新しい炉が要る。溶鉱炉を一つと、ネザーのマグマブロックを四つ。炉の底に敷く。……頼めるか。",
                 "The master's forge won't melt it. I need a new one. One blast furnace, and four magma blocks from the Nether to line the floor. ...Can you help?"),
       options=[OK])
g.node("wait", ("マグマブロックは、ネザーの底のほうに転がってる。足もとに気をつけてな。溶鉱炉は、かまどと鉄と滑らかな石で作れる。",
                "Magma blocks lie around low in the Nether. Watch your feet. A blast furnace takes a furnace, iron, and smooth stone."))
g.node("hand_ask", ("溶鉱炉とマグマ、そろったか？", "Got the blast furnace and the magma?"),
       options=[hand_option(("溶鉱炉とマグマブロックを渡す", "Hand over the blast furnace and magma"),
                            all_(item("minecraft:blast_furnace", 1), item("minecraft:magma_block", 4)),
                            [take("minecraft:blast_furnace", 1), take("minecraft:magma_block", 4), signal("town_hand:guest_ash_apprentice")], "handed"), LATER])
g.node("handed", ("これで組める！　親方に見せてくる……いや、あんたから言ってくれ。おれが言うと、まだ早いって怒られる。",
                  "Now I can build it! I'll go show the master... no, you tell him. If I say it, he'll yell that I'm not ready."),
       face="smile", options=[OK])
g.node("wait_smith", ("親方は鍛冶場の受付の奥だ。", "The master's behind the smithy counter."))
g.node("back", ("炉に火を入れた。灰が溶けた。……その火の中に、糸みたいな光が見えた。下へ、下へ伸びてる。炉の底より、もっと下へ。",
                "I lit the forge. The ash melted. ...And in the fire I saw a thread of light. Running down and down. Below the forge floor, further down."),
       face="grave", nxt="back2")
g.node("back2", ("親方は見なかったことにしろって。でも、あんたには言っておく。あれは、どこかへ帰る道だと思う。",
                 "The master says to forget I saw it. But I'm telling you. I think it's a road home, to somewhere."),
       face="grave", options=[OK])
g.node("idle", ("灰は温かい。手の中で、ゆっくり脈を打つみたいに。", "The ash is warm. In my hand it almost seems to pulse, slowly."))

c = ext("smith")
c.menu("town_forge", ("弟子の新しい炉のこと", "About the apprentice's new forge"), "town_forge", at(Q, "smith"))
c.node("town_forge", ("弟子が炉を組んだ？　……ふん、悪くない。底のマグマの並べ方がいい。誰に習ったんだか。",
                      "The boy built a forge? ...Hmph. Not bad. He laid the magma well. Who taught him that, I wonder."),
       nxt="town_forge2")
c.node("town_forge2", ("あの灰で打った刃は、冷めても赤い線が残るそうだ。……弟子には言うなよ。調子に乗る。",
                       "They say a blade forged in that ash keeps a red line even when it cools. ...Don't tell the boy. It'll go to his head."),
       face="smile", options=[OK])

# ######################################################################
# 四章: 老婆の薬鍋
# ######################################################################
Q = "town/ch4_herbs"
quest("ch4_herbs", 4, "skyabyss:guest_herbalist",
      ("老婆の薬鍋", "The Herbalist's Pot"),
      ("薬草摘みの老婆に、醸造台とネザーウォートを見せて、醸造を教わる。", "Show the old herbalist a brewing stand and Nether wart, and learn to brew."),
      ("薬の煮方を教わった。灰だけは、入れるなと言われた。", "You learned how to brew. Only the ash, she said, must never go in."),
      ("薬草摘みの婆さまが、醸造台の使い方を誰かに教えたがってる。ただし、ネザーの瘤を持ってこいとさ。",
       "The old herbalist wants to teach someone the brewing stand. But you have to bring her Nether wart first."),
      [("meet", ("宿の女将の噂。薬草摘みの老婆が、醸造を教えたがっている。南の通りの薬草の家にいるという。",
                 "A rumour from the innkeeper: the old herbalist wants to teach brewing. She lives in the herb house on the south road."),
        [talk("meet", "skyabyss:guest_herbalist", "meet", "薬草摘みの老婆に会う（南の通りの薬草の家）", "Meet the old herbalist (herb house, south road)")], None),
       ("gather", ("醸造台を自分で組み、ネザーウォートを三つ持ってくる。どれも、ネザーの砦の近くで手に入る。",
                   "Build a brewing stand yourself and bring three Nether wart. Both come from near the Nether fortresses."),
        [collect("stand", "minecraft:brewing_stand", 1, "醸造台を作る", "Craft a brewing stand"),
         collect("wart", "minecraft:nether_wart", 3, "ネザーウォートを集める", "Gather Nether wart")], None),
       ("hand", ("そろった。老婆に見せよう。", "That's everything. Show it to the herbalist."),
        [sig("hand", "town_hand:guest_herbalist", "老婆に醸造台とネザーウォートを見せる", "Show the brewing stand and wart to the herbalist")], None)],
      reward(70, 25, [{"item": "minecraft:glass_bottle", "count": 3}]), 241)

g = guest("herbalist", "薬草摘みの老婆", "Old Herbalist")
g.entry(at(Q, "meet"), "meet")
g.entry(at(Q, "gather"), "wait")
g.entry(at(Q, "hand"), "hand_ask")
g.entry(None, "idle")
g.node("meet", ("おや、灰の匂いのする子だね。薬師の店じゃ売ってない薬を、自分で煮たくはないかい。",
                "My, you smell of ash, child. Wouldn't you like to brew the medicines the apothecary doesn't sell?"),
       nxt="meet2")
g.node("meet2", ("醸造台を一つ、自分で組んでおいで。それと、ネザーの瘤を三つ。あれがなけりゃ、どんな薬も水と同じさ。",
                 "Build yourself a brewing stand. And bring three Nether wart. Without it, every potion is just water."),
       options=[OK])
g.node("wait", ("瘤は、ネザーの砦の階段の脇の、魂の砂に生える。醸造台は、ブレイズロッドと丸石で組める。",
                "The wart grows in soul sand beside the fortress stairs. A brewing stand takes a blaze rod and cobblestone."))
g.node("hand_ask", ("台と瘤は？", "The stand and the wart?"),
       options=[hand_option(("醸造台とネザーウォートを見せる", "Show the brewing stand and the wart"),
                            all_(item("minecraft:brewing_stand", 1), item("minecraft:nether_wart", 3)),
                            [take("minecraft:nether_wart", 3), signal("town_hand:guest_herbalist")], "handed"), LATER])
g.node("handed", ("よし。瘤を水で煮て、まずは素の薬。そこへ足すもので、薬の向きが決まる。……さあ、台はあんたのだ。持ってお行き。",
                  "Good. Boil the wart in water first: that's your base. What you add next decides what it becomes. ...There, the stand is yours. Take it with you."),
       face="smile", nxt="handed2")
g.node("handed2", ("ひとつだけ。余燼の山の灰を、薬に入れるんじゃないよ。夢の中で、ずっと下へ落ちていく。戻ってきた者は、まだいない。",
                   "One thing. Never put the ember mountain's ash into a potion. You fall, down and down, in your dreams. No one has come back yet."),
       face="grave", options=[OK])
g.node("idle", ("草は、摘んだ時の天気を覚えてる。", "Herbs remember the weather they were picked in."))

# ######################################################################
# 裏通り: 墓守の名のない墓 (一〜四章。六章まで続く)
# ######################################################################
GK = "skyabyss:attendant_gravekeeper"
gk = ext("gravekeeper")


def grave_quest(n, chapter, title, summary, done_text, rumor, stages, xp, coins, order):
    quest("grave_%d" % n, chapter, GK, title, summary, done_text, rumor, stages, reward(xp, coins), order)


def grave_offer(n, chapter, prev, label):
    """墓守の選択肢: 頼みを受ける (章・前の段・まだ受けていない)。"""
    q = "town/grave_%d" % n
    cond = [stage_min(chapter - 1), none(q)]
    if prev:
        cond.append(done(prev))
    gk.menu("town_grave%d" % n, label, "town_g%d_ask" % n, all_(*cond), do=[start(q)])


grave_quest(1, 1, ("名のない墓", "The Nameless Grave"),
            ("名のない墓に、誰かが毎朝花を置いていく。墓守と一緒に花を供える。", "Someone leaves flowers on the nameless grave every morning. Lay one with the gravekeeper."),
            ("花は、同じ野の、同じ色だった。", "The flowers were from the same field, the same colour."),
            ("墓守が、名のない墓のことで人手を探してたよ。西門の外の小屋だ。", "The gravekeeper wants help with the nameless grave. His hut is outside the west gate."),
            [("flower", ("西門の外の墓地の、名のない墓。毎朝、誰かが花を置いていくという。花を一輪摘んで、墓守に渡そう。",
                         "The nameless grave outside the west gate. Someone leaves a flower there every morning. Pick a flower and bring it to the gravekeeper."),
              [collect("flower", "#minecraft:small_flowers", 1, "花を一輪摘む", "Pick a flower")], None),
             ("lay", ("花を摘んだ。墓守に渡して、一緒に供えよう。", "You have a flower. Give it to the gravekeeper and lay it together."),
              [sig("lay", "town_hand:attendant_gravekeeper", "墓守に花を渡す", "Give the flower to the gravekeeper")], [{"type": "lore", "entry": "town/grave_1"}])],
            20, 6, 250)
grave_offer(1, 1, None, ("名のない墓の頼み", "The nameless grave"))
gk.node("town_g1_ask", ("頼みを聞いてくれるかい。名のない墓に、毎朝、誰かが花を置いていく。わしは置くところを見たことがない。",
                        "Will you hear a request? Every morning someone leaves a flower on the nameless grave. I've never once seen who."),
        nxt="town_g1_ask2")
gk.node("town_g1_ask2", ("花を一輪、摘んできておくれ。わしの花と並べてやりたい。誰だか知らんが、一人で供えさせておくのは寂しかろう。",
                         "Pick me a flower. I'd like to lay it beside mine. Whoever they are, it's lonely to mourn alone."),
        options=[OK])
gk.menu("town_g1_hand", ("花を渡す", "Give the flower"), "town_g1_done", all_(at("town/grave_1", "lay"), item("#minecraft:small_flowers", 1)),
        kind="give", do=[take("#minecraft:small_flowers", 1), signal("town_hand:attendant_gravekeeper")])
gk.node("town_g1_done", ("……ありがとう。今朝の花も、これと同じ色だった。同じ野の、同じ花だ。",
                         "...Thank you. This morning's flower was this very colour. Same field, same flower."), nxt="town_g1_done2")
gk.node("town_g1_done2", ("あんた、今朝早くにここへ来たかい？　……そうか。来ていないか。", "Did you come by early this morning? ...I see. You didn't."),
        face="grave", options=[OK])
lore("grave_1", "titan_whale", 11, ("名のない墓の花", "Flowers on the Nameless Grave"),
     ("西門の外の、名のない墓に花を供えた。\n\n墓守は、毎朝置かれる花が、私の摘んだものと同じ色だと言った。同じ野の、同じ花だと。\n\n今朝、私は墓地へは行っていない。",
      "I laid a flower on the nameless grave outside the west gate.\n\nThe gravekeeper said the flower left there each morning is the same colour as the one I picked. Same field, same flower.\n\nI did not go to the graveyard this morning."))

grave_quest(2, 2, ("苔の下の字", "Letters Under the Moss"),
            ("名のない墓の石の苔を洗い、下の字を読む。", "Wash the moss from the nameless grave's stone and read what lies beneath."),
            ("字は名前ではなかった。", "The words were not a name."),
            ("墓守が、名のない墓の石に字を見つけたってさ。水が要るらしい。", "The gravekeeper found letters on the nameless grave's stone. He needs water, apparently."),
            [("water", ("名のない墓の石の苔の下に、字があるという。水をひと桶、汲んで墓守に渡そう。",
                        "There are letters under the moss on the nameless grave. Fill a bucket with water and bring it to the gravekeeper."),
              [collect("water", "minecraft:water_bucket", 1, "水を桶に汲む", "Fill a bucket with water")], None),
             ("wash", ("水を汲んだ。墓守に渡そう。", "You have the water. Bring it to the gravekeeper."),
              [sig("wash", "town_hand:attendant_gravekeeper", "墓守に水を渡す", "Give the water to the gravekeeper")], [{"type": "lore", "entry": "town/grave_2"}])],
            30, 10, 251)
grave_offer(2, 2, "town/grave_1", ("苔の下の字のこと", "The letters under the moss"))
gk.node("town_g2_ask", ("名のない墓の石に、苔の下に字があるのを見つけた。わしの目じゃ読めん。水をひと桶、汲んできてくれんか。洗ってみよう。",
                        "I found letters on the nameless grave's stone, under the moss. My eyes can't make them out. Would you fetch a bucket of water? We'll wash it."),
        options=[OK])
gk.menu("town_g2_hand", ("水を渡す", "Give the water"), "town_g2_done", all_(at("town/grave_2", "wash"), item("minecraft:water_bucket", 1)),
        kind="give", do=[take("minecraft:water_bucket", 1), give("minecraft:bucket", 1), signal("town_hand:attendant_gravekeeper")])
gk.node("town_g2_done", ("どれ……出た。名前じゃないな。一行だけだ。", "Let's see... there. Not a name. Just one line."), nxt="town_g2_done2")
gk.node("town_g2_done2", ("「忘れないうちに書け」。……誰に宛てたんだろうね。墓の下の者にか、墓の前に立つ者にか。",
                          "\"Write it down before you forget.\" ...Who was it meant for, I wonder. The one under the stone, or the one standing before it?"),
        face="grave", options=[OK])
lore("grave_2", "titan_tide", 11, ("苔の下の一行", "One Line Under the Moss"),
     ("名のない墓の苔を洗うと、一行だけ字が出た。\n\n「忘れないうちに書け」\n\n野営地の帳面の、最初の一行と同じ字だった。",
      "When we washed the moss from the nameless grave, a single line appeared.\n\n\"Write it down before you forget.\"\n\nThe same hand as the first line in the camp notebook."))

grave_quest(3, 3, ("掘り返された土", "Turned Earth"),
            ("名のない墓を掘りかけた者の足跡を辿る。", "Follow the footprints of whoever tried to dig up the nameless grave."),
            ("足跡は、喪の家で途切れていた。", "The footprints ended at the house of mourning."),
            ("墓守がまた困ってた。名のない墓を、夜のうちに誰かが掘りかけたって。", "The gravekeeper is troubled again. Someone tried to dig up the nameless grave in the night."),
            [("visit", ("夜のうちに、誰かが名のない墓を掘りかけた。足跡は都の中へ、南の通りの喪の家のほうへ続いている。",
                        "Someone tried to dig up the nameless grave in the night. The footprints lead into the capital, toward the house of mourning on the south road."),
              [sig("visit", "town_at:quest_house_11", "足跡を辿る（南の通りの喪の家）", "Follow the footprints (house of mourning, south road)")],
              [{"type": "lore", "entry": "town/grave_3"}]),
             ("back", ("喪の家の空いた椅子に、土が付いていた。卓の上の灰に、指で一行。墓守に知らせよう。",
                       "Earth on the empty chair in the house of mourning. One line traced in the ash on the table. Tell the gravekeeper."),
              [talk("back", GK, "town_g3_back", "墓守に知らせる", "Tell the gravekeeper")], None)],
            40, 12, 252)
grave_offer(3, 3, "town/grave_2", ("掘り返された土のこと", "The turned earth"))
gk.node("town_g3_ask", ("昨夜、誰かが名のない墓を掘りかけた。土が新しい。足跡は都の中へ続いとる。南の通りの、喪の家のほうだ。",
                        "Last night someone started digging up the nameless grave. The earth is fresh. The footprints lead into the capital, toward the house of mourning on the south road."),
        face="grave", nxt="town_g3_ask2")
gk.node("town_g3_ask2", ("足跡の大きさは……あんたの靴と同じくらいだったよ。見てきてくれんか。わしは、あの家には近寄りたくない。",
                         "The prints were about... the size of your boots. Would you go and look? I'd rather not go near that house."),
        face="grave", options=[OK])
gk.menu("town_g3_back", ("喪の家のこと", "About the house of mourning"), "town_g3_back", at("town/grave_3", "back"))
gk.node("town_g3_back", ("どうだった？", "Well?"), nxt="town_g3_back2")
gk.node("town_g3_back2", ("空いた椅子に土。卓の上の灰に、指で一行……「また来た」。", "Earth on the empty chair. A line traced in the ash on the table... \"Back again.\""),
        face="grave", nxt="town_g3_back3")
gk.node("town_g3_back3", ("……あの墓はな、わしが墓守になる前からある。掘ったのが誰か、誰も知らん。続きは、もっと先で分かる気がするよ。",
                          "...That grave was there before I became keeper. No one knows who dug it. I have a feeling the rest will come clear further on."),
        options=[OK])
lore("grave_3", "titan_storm", 11, ("喪の家の卓", "The Table in the House of Mourning"),
     ("名のない墓を掘りかけた足跡は、南の通りの喪の家で途切れていた。\n\n皿が一つ多い卓。空いた椅子の座に、墓地の土。卓に積もった灰に、指でなぞった一行。\n\n「また来た」\n\n指の幅は、私の指と同じだった。",
      "The footprints from the nameless grave ended at the house of mourning on the south road.\n\nA table set with one plate too many. Graveyard earth on the seat of the empty chair. One line traced in the ash on the table.\n\n\"Back again.\"\n\nThe finger that traced it was as wide as mine."))

grave_quest(4, 4, ("蝋燭", "A Candle"),
            ("名のない墓の前に、蝋燭を一本灯す。", "Light a single candle before the nameless grave."),
            ("墓は、誰かが「いつか入る」ために掘ったものだった。続きは、もっと遠くで。",
             "The grave was dug for someone who would \"one day\" lie in it. The rest lies further on."),
            ("墓守が、名のない墓に蝋燭を灯したいってさ。", "The gravekeeper wants to light a candle at the nameless grave."),
            [("candle", ("名のない墓の前に灯す蝋燭を一本、墓守に届ける。", "Bring the gravekeeper a candle to light before the nameless grave."),
              [collect("candle", "minecraft:candle", 1, "蝋燭を作る", "Make a candle")], None),
             ("light", ("蝋燭を作った。墓守に渡そう。", "You have a candle. Give it to the gravekeeper."),
              [sig("light", "town_hand:attendant_gravekeeper", "墓守に蝋燭を渡す", "Give the candle to the gravekeeper")], [{"type": "lore", "entry": "town/grave_4"}])],
            50, 15, 253)
grave_offer(4, 4, "town/grave_3", ("蝋燭のこと", "The candle"))
gk.node("town_g4_ask", ("最後の頼みになるかもしれん。名のない墓の前に、蝋燭を一本灯してやりたい。蝋燭を一本、持ってきておくれ。",
                        "This may be my last request. I'd like to light a candle before the nameless grave. Would you bring me one?"),
        options=[OK])
gk.menu("town_g4_hand", ("蝋燭を渡す", "Give the candle"), "town_g4_done", all_(at("town/grave_4", "light"), item("minecraft:candle", 1)),
        kind="give", do=[take("minecraft:candle", 1), signal("town_hand:attendant_gravekeeper")])
gk.node("town_g4_done", ("灯った。……実はな、昨日、墓の下を見た。棺は空だった。ずっと前から空なんだ。",
                         "It's lit. ...The truth is, yesterday I looked beneath the grave. The coffin was empty. It's been empty all along."),
        face="grave", nxt="town_g4_done2")
gk.node("town_g4_done2", ("この墓は、誰かが「いつか入る」ために掘った墓だよ。名を刻まずに。自分の名が、まだ決まっていない者のために。",
                          "This grave was dug by someone who meant to lie in it \"one day\". With no name carved. For someone whose name isn't settled yet."),
        face="grave", nxt="town_g4_done3")
gk.node("town_g4_done3", ("あんたがもっと遠くへ行って、戻ってきたら、また話そう。そのころには、字が読めるようになっとるかもしれん。",
                          "When you've gone further and come back, we'll talk again. Perhaps by then the letters will be readable."),
        options=[OK])
lore("grave_4", "titan_ember", 11, ("空の棺", "The Empty Coffin"),
     ("名のない墓の前に蝋燭を灯した。\n\n墓守は言った。棺はずっと前から空だと。誰かが「いつか入る」ために掘った墓だと。\n\n名を刻む場所は、まだ空いている。",
      "I lit a candle before the nameless grave.\n\nThe gravekeeper said the coffin has always been empty. That it was dug by someone who meant to lie in it \"one day\".\n\nThe place for a name is still blank."))
t("quest.skyabyss.town.grave_tbc", "墓守の話は、六章で続く。", "The gravekeeper's tale continues in chapter six.")

# ######################################################################
# 裏通り: 酒場の地下の酔いどれ (深淵の手がかり)
# ######################################################################
DK = "skyabyss:attendant_drunkard"
dk = ext("drunkard")


def cellar_offer(n, chapter, prev, label):
    q = "town/cellar_%d" % n
    cond = [stage_min(chapter - 1), none(q)]
    if prev:
        cond.append(done(prev))
    dk.menu("town_cellar%d" % n, label, "town_c%d_ask" % n, all_(*cond), do=[start(q)])


quest("cellar_1", 1, DK, ("もう一つの空", "Another Sky"),
      ("酔いどれに甘い酒を渡して、とっておきの話を聞く。", "Bring the drunkard something sweet, and hear his best story."),
      ("空の下に、もう一つ空がある。酔いどれはそう言った。", "Beneath the sky there is another sky. So the drunkard said."),
      ("酒場の地下の酔いどれが、甘いものをくれたら、とっておきの話をするってさ。", "The drunk in the tavern cellar says he'll tell his best story for something sweet."),
      [("honey", ("酒場「深い杯」の地下の酔いどれが、甘いものを欲しがっている。蜂蜜入りの瓶を一つ。",
                  "The drunkard in the cellar of the Deep Cup tavern wants something sweet. One honey bottle."),
        [collect("honey", "minecraft:honey_bottle", 1, "蜂蜜入りの瓶を手に入れる", "Get a honey bottle")], None),
       ("hand", ("蜂蜜の瓶を手に入れた。酔いどれに渡そう。", "You have the honey. Give it to the drunkard."),
        [sig("hand", "town_hand:attendant_drunkard", "酔いどれに蜂蜜の瓶を渡す", "Give the honey bottle to the drunkard")], [{"type": "lore", "entry": "town/cellar_1"}])],
      reward(20, 5), 260)
cellar_offer(1, 1, None, ("とっておきの話", "Your best story"))
dk.node("town_c1_ask", ("ひっく……兄さん、喉が渇いた。甘いのがいい。蜂蜜の瓶を一つ。そしたら、とっておきの話をしてやる。",
                        "Hic... friend, I'm parched. Something sweet. A honey bottle. Then I'll tell you my best story."),
        options=[OK])
dk.menu("town_c1_hand", ("蜂蜜の瓶を渡す", "Give the honey bottle"), "town_c1_done", all_(at("town/cellar_1", "hand"), item("minecraft:honey_bottle", 1)),
        kind="give", do=[take("minecraft:honey_bottle", 1), give("minecraft:glass_bottle", 1), signal("town_hand:attendant_drunkard")])
dk.node("town_c1_done", ("ああ、甘え。……とっておきの話だ。空の下に、もう一つ空がある。地の底さ。四つの鼓動が、いつか帰っていく所だ。",
                         "Ahh, sweet. ...Here's my best one. Under the sky there's another sky. Down at the bottom of the earth. Where the four heartbeats go home, someday."),
        nxt="town_c1_done2")
dk.node("town_c1_done2", ("親父が言ってた。底の空は、上の空を下から見上げてるって。……ひっく。酔っ払いの戯言さ。忘れな。",
                          "My old man used to say the sky down there looks up at this one from below. ...Hic. Just a drunk's babble. Forget it."),
        face="smile", options=[OK])
lore("cellar_1", "titan_whale", 12, ("底の空", "The Sky Below"),
     ("酒場の地下の酔いどれの話。\n\n空の下に、もう一つ空がある。地の底に。四つの鼓動が、いつか帰っていく所。\n\n底の空は、上の空を下から見上げている。",
      "The drunkard's tale, in the tavern cellar.\n\nBeneath the sky there is another sky, at the bottom of the earth. The place where the four heartbeats go home, someday.\n\nThe sky below looks up at the sky above."))

quest("cellar_2", 3, DK, ("壁の向こうの風", "Wind Behind the Wall"),
      ("酒場の地下の奥の壁に耳を当てる。", "Put your ear to the far wall of the tavern cellar."),
      ("壁の向こうに道がある。鍵は、まだ早い。", "There's a passage behind the wall. Too early for the key."),
      ("酔いどれが、地下の奥の壁が鳴るって騒いでた。", "The drunkard's been going on about the cellar's far wall making noises."),
      [("listen", ("酔いどれは、地下の奥の壁に耳を当ててみろという。", "The drunkard says to put your ear against the far wall of the cellar."),
        [sig("listen", "town_at:cellar_wall", "地下の奥の壁に耳を当てる", "Put your ear to the far cellar wall")], [{"type": "lore", "entry": "town/cellar_2"}]),
       ("back", ("壁の向こうで、風が下へ吸い込まれていく音がした。酔いどれに話そう。", "Behind the wall, wind was being drawn downward. Tell the drunkard."),
        [talk("back", DK, "town_c2_back", "酔いどれに話す", "Tell the drunkard")], None)],
      reward(40, 8), 261)
cellar_offer(2, 3, "town/cellar_1", ("壁の音のこと", "The noise in the wall"))
dk.node("town_c2_ask", ("おい、聞こえるか。……聞こえねえか。この地下の奥の壁に、耳を当ててみな。", "Hey, d'you hear it? ...No? Go put your ear to the far wall down here."),
        options=[OK])
dk.menu("town_c2_back", ("壁の向こうの音のこと", "The sound behind the wall"), "town_c2_back", at("town/cellar_2", "back"))
dk.node("town_c2_back", ("聞こえたろう。風の音だ。地の底へ、上から下へ吸い込まれていく風だ。", "You heard it. That's wind. Wind being sucked down, from above to below, into the deep."),
        face="grave", nxt="town_c2_back2")
dk.node("town_c2_back2", ("壁の向こうに道がある。酒場を建てた奴が塗り込めた。……鍵はおれが持ってる。けど、まだ早え。あんたが、もっと鼓動を返してからだ。",
                          "There's a passage behind it. Whoever built the tavern bricked it up. ...I've got the key. But it's too early. Not till you've given back more heartbeats."),
        options=[OK])
lore("cellar_2", "titan_storm", 12, ("塗り込めた壁", "The Bricked-Up Wall"),
     ("酒場の地下の奥の壁に耳を当てた。\n\n風の音がした。上から下へ、地の底へ吸い込まれていく風。\n\n壁の向こうに道がある。酔いどれが鍵を持っている。",
      "I put my ear to the far wall of the tavern cellar.\n\nI heard wind. Wind drawn from above to below, down into the deep.\n\nThere is a passage behind the wall. The drunkard has the key."))

quest("cellar_3", 4, DK, ("古い鍵", "The Old Key"),
      ("酔いどれの鍵で地下の奥の壁を開け、通路の突き当たりの戸を見る。", "Open the cellar's far wall with the drunkard's key, and see the door at the end of the passage."),
      ("封じた戸の向こうが、底の空への道だという。", "Beyond the sealed door, they say, is the road to the sky below."),
      ("酔いどれが、鍵を渡す時が来たって真顔で言ってたよ。", "The drunkard said, dead sober, that it's time to hand over the key."),
      [("open", ("三つの鼓動を返した。酔いどれが、地下の奥の壁を開けるという。", "Three heartbeats returned. The drunkard says he'll open the far wall of the cellar."),
        [sig("open", "town_cellar_open", "酔いどれに壁を開けてもらう", "Have the drunkard open the wall")], None),
       ("walk", ("壁が開いた。通路の突き当たりの戸を見に行く。開けようとはしないこと。", "The wall is open. Go see the door at the end of the passage. Don't try to open it."),
        [sig("walk", "town_at:abyss_hint", "通路の突き当たりへ行く", "Walk to the end of the passage")], [{"type": "lore", "entry": "town/cellar_3"}]),
       ("back", ("封じた戸を見た。酔いどれに話そう。", "You saw the sealed door. Tell the drunkard."),
        [talk("back", DK, "town_c3_back", "酔いどれに話す", "Tell the drunkard")], None)],
      reward(60, 10), 262)
cellar_offer(3, 4, "town/cellar_2", ("古い鍵のこと", "The old key"))
dk.node("town_c3_ask", ("……来たか。あんたの背中から、灰の匂いがする。三つ返したな。鍵を渡す時だ。", "...You came. You smell of ash. You've given back three. It's time for the key."),
        face="grave", options=[OK])
dk.menu("town_c3_open", ("壁を開けてもらう", "Ask him to open the wall"), "town_c3_open", at("town/cellar_3", "open"),
        do=[{"type": "town", "act": "open_cellar"}, signal("town_cellar_open")])
dk.node("town_c3_open", ("（酔いどれが壁の継ぎ目に古い鍵を差し込むと、石が二つ、奥へ落ちた）……行きな。突き当たりに戸がある。開けようとするなよ。見るだけだ。",
                         "(The drunkard slides an old key into a seam in the wall, and two stones fall away inward.) ...Go on. There's a door at the end. Don't try to open it. Just look."),
        face="grave", options=[OK])
dk.menu("town_c3_back", ("封じた戸のこと", "The sealed door"), "town_c3_back", at("town/cellar_3", "back"))
dk.node("town_c3_back", ("戸を見たか。……あの戸の向こうが、底の空への道だ。四つ目を返したら、鍵が下を指す。そのとき、また来な。",
                         "You saw the door. ...Beyond it is the road to the sky below. Give back the fourth, and the key will point down. Come see me then."),
        face="grave", nxt="town_c3_back2")
dk.node("town_c3_back2", ("おれ？　おれは行かねえ。行って戻ってきた奴を、一人知ってる。……そいつは、自分の名前を忘れてた。",
                          "Me? I'm not going. I knew one who went and came back. ...He'd forgotten his own name."),
        options=[OK])
lore("cellar_3", "titan_ember", 12, ("封じた戸", "The Sealed Door"),
     ("酒場の地下の奥、塗り込めた壁の向こうの通路の突き当たりに、封じた戸がある。\n\n戸には何も書かれていない。ただ、取っ手の高さに、手の脂の跡が残っていた。\n\n私の手と、同じ大きさの。",
      "Deep in the tavern cellar, at the end of the passage behind the bricked-up wall, there is a sealed door.\n\nNothing is written on it. Only, at the height of the handle, the greasy mark of a hand.\n\nThe same size as mine."))

# ######################################################################
# 裏通り: 地図屋・古井戸
# ######################################################################
mm = ext("mapmaker")
mm.menu("town_rumor", ("まだ行っていない所", "Places I haven't been"), "town_rumor", kind="talk")
mm.node("town_rumor", ("噂ならいくらでも。まだあんたの地図にない所を、指でなぞってやろう。", "Rumours, I've plenty. Let me trace the places that aren't on your map yet."),
        do=[{"type": "town", "act": "mapmaker"}], options=[OK])
mm.menu("town_draw", ("地図に描き足してもらう（鐘銭 5）", "Have it drawn on my map (5 bell coins)"), "town_draw", kind="talk")
mm.node("town_draw", ("どれ、描き足してやろう。跡地なら、方角と距離はわしの頭に入っとる。", "Let's see, I'll draw it in. For the titans' grounds, I have the bearings in my head."),
        do=[{"type": "town", "act": "mapmaker_draw"}], options=[OK])
t("message.skyabyss.town.mapmaker.rumor", "地図屋: 「%1$s」は、都から%2$sへ、およそ %3$s ブロック。", "Mapmaker: \"%1$s\" lies %2$s of the capital, about %3$s blocks.")
t("message.skyabyss.town.mapmaker.none", "地図屋: あんたの地図に、もう描き足すことはないね。", "Mapmaker: Nothing left to add to your map, I'm afraid.")
t("message.skyabyss.town.mapmaker.nothing_to_draw", "描き足せる跡地はない。", "There is no ground left to draw in.")
t("message.skyabyss.town.mapmaker.cost", "描き足すには鐘銭が %s 枚いる。", "Drawing it in costs %s bell coins.")
t("message.skyabyss.town.mapmaker.drawn", "地図に「%s」を描き足してもらった（M の地図）。", "\"%s\" has been drawn on your map (M).")
for i, (ja, en) in enumerate([("北", "north"), ("北北東", "north-northeast"), ("北東", "northeast"), ("東北東", "east-northeast"),
                              ("東", "east"), ("東南東", "east-southeast"), ("南東", "southeast"), ("南南東", "south-southeast"),
                              ("南", "south"), ("南南西", "south-southwest"), ("南西", "southwest"), ("西南西", "west-southwest"),
                              ("西", "west"), ("西北西", "west-northwest"), ("北西", "northwest"), ("北北西", "north-northwest")]):
    t("message.skyabyss.town.dir.%d" % i, ja, en)

t("message.skyabyss.town.rumor.well", "南西の古井戸の格子、一つだけ外れるらしい。下に何かあるって、子どもらが言ってたよ。",
  "One of the grates on the old well to the southwest comes loose, they say. The children swear there's something down there.")
t("message.skyabyss.town.well.found", "古井戸の底の箱に、鐘銭と、かすかに鳴る欠片が入っていた。",
  "In the chest at the bottom of the old well: bell coins, and a shard that hums faintly.")
lore("well_song", "titan_whale", 13, ("井戸の底の歌", "The Song at the Bottom of the Well"),
     ("古井戸の下の、細い道の奥。壁に、歌の間と同じ刻みがあった。\n\n刻みは途中で終わっている。続きを刻むはずだった手が、そこで止まったように。\n\n箱の中の欠片を耳に当てると、遠くで同じ歌が鳴った。",
      "Beneath the old well, at the end of a narrow way. On the wall, the same carvings as in the hall of songs.\n\nThe carving stops partway, as if the hand meant to finish it stopped there.\n\nHeld to my ear, the shard from the chest hummed the same song, far away."))

# 限定の人 (エンティティの型の名前・手を上げたとき)
t("entity.skyabyss.town_guest", "町の人", "Townsperson")
t("message.skyabyss.town_guest.struck", "……なにするの。", "...What was that for?")


# ====================================================================== 書き出す
def dump(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(data, f, ensure_ascii=False, indent=2)
        f.write("\n")


def main():
    for name, q in QUESTS:
        dump(os.path.join(QUEST, name + ".json"), q)
    for name, d in DIALOGUES.items():
        dump(os.path.join(DIALOGUE, name + ".json"), d.json())
    for name, chapter, order in LORES:
        dump(os.path.join(LORE, name + ".json"), {"chapter": chapter, "order": order, "unlock": [{"type": "manual"}]})
    dump(os.path.join(LANG, "ja_jp.json"), dict(sorted(JA.items())))
    dump(os.path.join(LANG, "en_us.json"), dict(sorted(EN.items())))
    print("quests", len(QUESTS), "dialogues", len(DIALOGUES), "lore", len(LORES), "lang keys", len(JA))


if __name__ == "__main__":
    main()
