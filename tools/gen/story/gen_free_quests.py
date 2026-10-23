"""自由なモードのサブクエスト (語り部・鯨追いの頼みと、言伝ての板の日替わり・週替わり) を一か所で書き、
ゲームのデータと設計書の表に書き出す。tools/gen/mobs/gen_npc_content.py の続き (同じ考え方: 構造と日英の文を同じ場所に置く)。

元の会話 (storyteller.json / whale_chaser.json) とその文 (assets/skyabyss_npc) には手を入れない。
会話は「足し書き」のファイル (extends: true) で、元の話題の節に一つだけ選択肢を足し、そこから先を新しい節にする。

実行 (リポジトリ直下):
    python tools/gen/story/gen_free_quests.py
出力:
    src/main/resources/data/skyabyss/quest/<クエスト>.json
    src/main/resources/data/skyabyss/dialogue/{storyteller,whale_chaser}_free.json   会話の足し書き
    src/main/resources/data/skyabyss/lore/entry/...                                    新しい伝承の項目 (2 つ)
    src/main/resources/assets/skyabyss_quest/lang/{ja_jp,en_us}.json                  文
    docs/design/07_npc_quest.md の「自由なモード 生成」印の間                         クエストの表と全セリフ
"""
from __future__ import annotations

import json
import re
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[2] / "lib"))
from langmerge import merged  # noqa: E402

ROOT = Path(__file__).resolve().parents[3]
DATA = ROOT / "src/main/resources/data/skyabyss"
LANG = ROOT / "src/main/resources/assets/skyabyss_quest/lang"
DOC = ROOT / "docs/design/07_npc_quest.md"

ST = "skyabyss:storyteller"
WC = "skyabyss:whale_chaser"

JA: dict[str, str] = {}
EN: dict[str, str] = {}


def text(key, ja, en):
    assert key not in JA, key
    JA[key] = ja
    EN[key] = en


# ---------------------------------------------------------------- 条件と結果
def q(quest, is_="active", stage=None):
    c = {"type": "quest", "quest": quest, "is": is_}
    if stage:
        c["stage"] = stage
    return c


def lore(entry):
    return {"type": "lore", "entry": entry}


def flag(f, npc=None):
    c = {"type": "flag", "flag": f}
    if npc:
        c["npc"] = npc
    return c


def item(i, count=1, token=None):
    c = {"type": "item", "item": i, "count": count}
    if token:
        c["token"] = token
    return c


def not_(c):
    return {"type": "not", "of": c}


def all_(*cs):
    return {"type": "all", "of": list(cs)}


def any_(*cs):
    return {"type": "any", "of": list(cs)}


def stilled(titan):
    return {"type": "stilled", "titan": titan}


def start(quest):
    return {"type": "quest_start", "quest": quest}


def give(i, count=1, token=None, name=None, lore_=None):
    a = {"type": "give", "item": i, "count": count}
    if token:
        a["token"] = token
    if name:
        a["name"] = name
    if lore_:
        a["lore"] = lore_
    return a


def take(i, count=1, token=None):
    a = {"type": "take", "item": i, "count": count}
    if token:
        a["token"] = token
    return a


def unlock(entry):
    return {"type": "lore", "entry": entry}


def add_aff(n):
    return {"type": "affinity", "amount": n}


# ---------------------------------------------------------------- 品 (見た目の小物と、運ぶ品)
ITEMS = {}


def keepsake(kid, item_id, name_ja, name_en, lore_ja, lore_en, token=None):
    nk = f"item.skyabyss.keepsake.{kid}"
    lk = f"item.skyabyss.keepsake.{kid}.lore"
    text(nk, name_ja, name_en)
    text(lk, lore_ja, lore_en)
    ITEMS[kid] = {"item": item_id, "name": nk, "lore": [lk], "token": token}
    return ITEMS[kid]


def give_item(kid, count=1):
    it = ITEMS[kid]
    return give(it["item"], count, it["token"], it["name"], it["lore"])


def reward_item(kid, count=1):
    it = ITEMS[kid]
    r = {"item": it["item"], "count": count, "name": it["name"], "lore": it["lore"]}
    if it["token"]:
        r["token"] = it["token"]
    return r


PARCEL = keepsake("parcel", "minecraft:leather", "語り部の包み", "The Storyteller's Bundle",
                  "干した肉と、古い肩掛け。鯨追いに届ける。", "Dried meat and an old shawl, for the whale chaser.", token="parcel")
CLASP_RETURN = keepsake("clasp_return", "minecraft:bone", "肩掛けの隠しにあった留め具", "A Clasp from the Shawl's Pocket",
                        "鯨追いから語り部へ。", "From the whale chaser, for the storyteller.", token="clasp_return")
CLASP = keepsake("whalebone_clasp", "minecraft:bone", "鯨骨の留め具", "Whalebone Clasp",
                 "語り部の母の肩掛けに付いていた。小さな鯨の形をしている。",
                 "It once held the shawl of the storyteller's mother. Carved in the shape of a small whale.")
BOOKMARK = keepsake("indigo_bookmark", "minecraft:blue_dye", "藍染めの栞「書き継ぐ者」", "Indigo Bookmark: \"One Who Writes On\"",
                    "細い字で縫い取りがある。語り部の母が使っていたもの。",
                    "Embroidered in fine stitches. The storyteller's mother used it.")
NOTCH_STONE = keepsake("notched_stone", "minecraft:flint", "刻み目の石", "Notched Stone",
                       "鯨追いが旅の目印にしていた石。一本だけ、深い刻み目がある。",
                       "A stone the whale chaser used to mark his way. One notch is cut deeper than the rest.")


# ---------------------------------------------------------------- 会話の足し書き
class Extension:
    def __init__(self, role, base_key):
        self.role = role
        self.base = base_key
        self.entries = []
        self.nodes = {}
        self.add_options = {}
        self.order = []

    def entry(self, node, cond):
        self.entries.append({"if": cond, "node": node})

    def node(self, nid, ja, en, face="neutral", next=None, do=None, options=None, note=""):
        assert nid not in self.nodes, nid
        n = {"face": face}
        if do:
            n["do"] = do
        if next:
            n["next"] = next
        if options:
            n["options"] = []
            for o in options:
                oo = {"id": o["id"], "goto": o.get("goto", "end")}
                if o.get("kind"):
                    oo["kind"] = o["kind"]
                if o.get("if"):
                    oo["if"] = o["if"]
                if o.get("do"):
                    oo["do"] = o["do"]
                n["options"].append(oo)
                text(f"{self.base}.{nid}.{o['id']}", o["ja"], o["en"])
        text(f"{self.base}.{nid}", ja, en)
        self.nodes[nid] = n
        self.order.append((nid, ja, face, do, next, options, note))

    def add_option(self, node, before, o):
        oo = {"id": o["id"], "goto": o.get("goto", "end")}
        for k in ("kind", "if", "do"):
            if o.get(k):
                oo[k] = o[k]
        self.add_options.setdefault(node, {"before": before, "options": []})["options"].append(oo)
        text(f"{self.base}.{node}.{o['id']}", o["ja"], o["en"])
        self.order.append((f"{node}（元の節に足す）", None, None, None, None, [o], ""))

    def json(self):
        out = {"npc": self.role, "extends": True}
        if self.entries:
            out["entries"] = self.entries
        if self.add_options:
            out["add_options"] = self.add_options
        out["nodes"] = self.nodes
        return out


def opt(oid, ja, en, goto="end", kind=None, if_=None, do=None):
    o = {"id": oid, "ja": ja, "en": en, "goto": goto}
    if kind:
        o["kind"] = kind
    if if_:
        o["if"] = if_
    if do:
        o["do"] = do
    return o


S = Extension(ST, "dialogue.skyabyss.storyteller")
C = Extension(WC, "dialogue.skyabyss.whale_chaser")

SHADOW = "sub_shadow_log"
PARCEL_Q = "sub_parcel"
GAPS = "sub_lore_gaps"
WAY = "sub_waymarks"
TIDE = "sub_tide_watch"

# ================================================================ 語り部
# 報告は話しかけたときにすぐ (入口)。頼みを受けるのは「ほかに、頼みごとはある？」から
S.entry("fq_shadow_done", q(SHADOW, stage="tell"))
S.entry("fq_parcel_back", all_(q(PARCEL_Q, stage="return"), item(CLASP_RETURN["item"], 1, "clasp_return")))
S.entry("fq_gaps_done", q(GAPS, stage="tell"))

S.add_option("menu", "bye", opt("errands", "ほかに、頼みごとはある？", "Is there anything I can help with?", goto="fq_menu", kind="quest", if_=flag("met", WC)))
S.node("fq_menu", "頼みごとかい。……そうだね。\n村の人の頼みなら、鐘のそばの言伝ての板に貼ってあるよ。", "Help, is it? ...Let me think.\n"
       "The village's requests are pinned to the message board by the bell.", options=[
    opt("board", "村の頼みごとを見せて。", "Show me the village's requests.", kind="talk", do=[{"type": "open_board"}]),
    opt("shadow", "影のことを、もっと知りたい。", "I want to know more about the shadow.", goto="fq_shadow_offer", kind="quest",
        if_=all_(lore("prologue/shadow"), q("main_song", "started"), q(SHADOW, "none"))),
    opt("parcel", "外れの旅の人に、何か届けようか。", "Shall I take something to the traveler at the edge of the village?",
        goto="fq_parcel_offer", kind="quest", if_=all_(flag("c_whale_heard", WC), q(PARCEL_Q, "none"))),
    opt("parcel_lost", "預かった包みを、なくしてしまった。", "I lost the bundle you gave me.", goto="fq_parcel_lost", kind="give",
        if_=all_(q(PARCEL_Q, stage="deliver"), not_(item(PARCEL["item"], 1, "parcel")))),
    opt("gaps", "帳面の白いところが気になる。", "The blank pages in my book bother me.", goto="fq_gaps_offer", kind="quest",
        if_=all_(q("sub_song", "done"), q(GAPS, "none"))),
    opt("back", "また今度。", "Another time.", goto="@entry", kind="leave"),
])

# 空の影の帳面
S.node("fq_shadow_offer", "影のことかい。\n……あたしの母さんは、影が通るたびに、戸口の柱へ小刀で刻み目を入れていたよ。",
       "The shadow, is it.\n...My mother used to cut a notch into the doorpost with her knife, every time the shadow passed.",
       face="grave", next="fq_shadow_offer2")
S.node("fq_shadow_offer2", "あんたも数えてごらん。晴れた昼に空を見上げて、影の下に立つんだよ。\n二度見たら、話しに来ておくれ。",
       "Why don't you count them too. Look up on a clear day and stand beneath the shadow.\n"
       "When you have seen it twice, come and tell me.", options=[
    opt("accept", "数えてみる。", "I'll count them.", kind="quest", do=[start(SHADOW), add_aff(1)]),
    opt("later", "今はいい。", "Not now.", goto="@entry"),
], note="サブ「空の影の帳面」を始める")
S.node("fq_shadow_done", "影を、二度見てきたんだね。\n……どっちから来た？　いや、言わなくていい。", "You saw the shadow twice, then.\n"
       "...Which way did it come from? No, you needn't say.", face="grave", next="fq_shadow_done2", note="サブ「空の影の帳面」の報告")
S.node("fq_shadow_done2", "母さんの柱の刻み目は、どれも同じ向きに斜めに入っていたんだよ。峰のほうから。\n"
       "……最後の一本だけ、途中で止まっている。あの日、母さんは何を見たんだろうね。",
       "The notches on my mother's doorpost all slant the same way. Down from the peaks.\n"
       "...Only the last one stops halfway. I wonder what she saw that day.",
       face="grave", do=[unlock("prologue/shadow_heading")], options=[
    opt("thanks", "話してくれて、ありがとう。", "Thank you for telling me.", do=[add_aff(1)]),
], note="ここに入るとサブ「空の影の帳面」が終わる。伝承「柱の刻み目」を記す")

# 火のそばへ (運ぶ)
S.node("fq_parcel_offer", "おや、ちょうどよかった。これを、あの人に持っていっておくれ。干した肉と、古い肩掛けだよ。\n"
       "空ばかり見ている人は、食べるのも、寒いのも忘れるからね。",
       "Oh, good timing. Take this to him, would you? Some dried meat and an old shawl.\n"
       "People who only look at the sky forget to eat, and forget the cold.", face="smile", options=[
    opt("accept", "届けてくる。", "I'll take it to him.", kind="give", do=[start(PARCEL_Q), give_item("parcel"), add_aff(1)]),
    opt("later", "今は手がふさがっている。", "My hands are full right now.", goto="@entry"),
], note="サブ「火のそばへ」を始める (包みを渡す)")
S.node("fq_parcel_lost", "なくしたのかい。……しょうがないね。もう一つ包んであげるよ。\n今度は、落とさないようにね。",
       "Lost it, did you? ...Oh, never mind. I'll wrap another.\nDon't drop this one.", do=[give_item("parcel")], next="@entry",
       note="包みをなくしたとき (詰まらないように、何度でも)")
S.node("fq_parcel_back", "あの人から？　……これは。", "From him? ...Why, this is—", face="grave",
       do=[take(CLASP_RETURN["item"], 1, "clasp_return")], next="fq_parcel_back2", note="サブ「火のそばへ」の報告 (留め具を受け取る)")
S.node("fq_parcel_back2", "母さんの留め具だよ。どこへいったかと、ずっと思っていた。……肩掛けの隠しに入っていたんだね。\n"
       "これは、あんたが持っておいき。母さんも、よく空を見上げる人だったから。",
       "My mother's clasp. I wondered for years where it had gone. ...So it was in the shawl's pocket.\n"
       "You keep it. She was one for looking at the sky, too.", face="smile", options=[
    opt("thanks", "大事にする。", "I'll take good care of it.", do=[add_aff(2)]),
], note="ここに入るとサブ「火のそばへ」が終わる")

# 書の空白を埋める
S.node("fq_gaps_offer", "帳面かい。……空いている頁があるね。\n書けるものは、みんな書いておおき。村のことも、空の上のことも。",
       "Your book? ...There are pages still empty.\nWrite down everything you can. The village, and what lies above the sky.", options=[
    opt("accept", "埋めてみる。", "I'll fill them.", kind="quest", do=[start(GAPS), add_aff(1)]),
    opt("later", "まだいい。", "Not yet.", goto="@entry"),
], note="サブ「書の空白を埋める」を始める")
S.node("fq_gaps_done", "見せておくれ。……ずいぶん書いたね。字も、前よりしっかりしてきた。",
       "Let me see. ...You've written a great deal. Your hand is steadier than before.", face="smile", next="fq_gaps_done2",
       note="サブ「書の空白を埋める」の報告")
S.node("fq_gaps_done2", "……おかしなことを言うけど。この字の癖、母さんの帳面に似ているんだよ。\n"
       "気のせいだろうね。これを挟んでおおき。母さんが使っていた栞だ。",
       "...This will sound strange. The way you shape your letters is like my mother's notebook.\n"
       "Just my fancy, I'm sure. Put this in it. It was her bookmark.", face="grave", options=[
    opt("thanks", "ありがとう。", "Thank you."),
], note="ここに入るとサブ「書の空白を埋める」が終わる")

# ================================================================ 鯨追い
C.entry("fq_c_parcel", all_(q(PARCEL_Q, stage="deliver"), item(PARCEL["item"], 1, "parcel")))
C.entry("fq_c_way_done", q(WAY, stage="tell"))
C.entry("fq_c_tide_done", q(TIDE, stage="tell"))

C.add_option("c_menu", "bye", opt("errands", "ほかに、頼みは？", "Anything else I can do for you?", goto="fq_c_menu", kind="quest"))
C.node("fq_c_menu", "頼みか。……そうだな。", "Something to do? ...Let me see.", options=[
    opt("way", "岩鯨に乗って、旅ができるようになった。", "The stone whale lets me ride it now.", goto="fq_c_way_offer", kind="quest",
        if_=all_(stilled("stone_whale"), q(WAY, "none"))),
    opt("tide", "ほかの巨神のことは、知らないか。", "Do you know anything about other titans?", goto="fq_c_tide_offer", kind="quest",
        if_=all_({"type": "stage", "min": 1}, q(TIDE, "none"))),
    opt("clasp_lost", "預かった留め具を、なくしてしまった。", "I lost the clasp you gave me.", goto="fq_c_clasp_lost", kind="give",
        if_=all_(q(PARCEL_Q, stage="return"), not_(item(CLASP_RETURN["item"], 1, "clasp_return")))),
    opt("board", "板に、あんたの字の頼みがあった。", "There was a request in your hand on the board.", goto="fq_c_board",
        if_={"type": "stage", "min": 1}),
    opt("back", "なんでもない。", "Never mind.", goto="@entry", kind="leave"),
])
C.node("fq_c_board", "ああ、あれか。……鯨の背は、放っておくと番兵がまた起きあがる。\n"
       "誰かが見回ってくれれば、それでいい。俺は、鎖をつかめないからな。",
       "Ah, that. ...Leave the whale's back alone and the sentinels rise again.\n"
       "If someone walks the rounds, that's enough. I can't grab the chain myself.", face="grave", next="@entry")

# 火のそばへ (鯨追いの側)
C.node("fq_c_parcel", "……ばあさんからか。\n（包みを開く）……肩掛けか。古いな。それに、干し肉。",
       "...From the old woman?\n(He opens the bundle.) ...A shawl. An old one. And dried meat.",
       face="smile", do=[take(PARCEL["item"], 1, "parcel")], next="fq_c_parcel2", note="サブ「火のそばへ」の届け先 (包みを受け取る)")
C.node("fq_c_parcel2", "礼を言っておいてくれ。……それと、肩掛けの隠しに、これが入っていた。\n"
       "骨を削った留め具だ。俺のものじゃない。ばあさんに返してやってくれ。",
       "Tell her thank you. ...And this was in the shawl's pocket.\n"
       "A clasp carved from bone. It isn't mine. Give it back to her.", do=[give_item("clasp_return")], options=[
    opt("ok", "返しておく。", "I'll give it back."),
], note="ここに入るとサブ「火のそばへ」の 1 段目が済む (留め具を受け取る)")
C.node("fq_c_clasp_lost", "なくした？　……火のそばに落ちていたぞ。気をつけろ。", "Lost it? ...It was lying by the fire. Be careful.",
       do=[give_item("clasp_return")], next="@entry", note="留め具をなくしたとき")

# 雲間の道標 (旅)
C.node("fq_c_way_offer", "……鯨に、乗せてもらえるのか。\n頼みがある。鯨の上から見た地上の話を、聞かせてくれないか。",
       "...It lets you ride it?\nThen I have a favor to ask. Tell me what the land looks like from up there.", face="grave",
       next="fq_c_way_offer2")
C.node("fq_c_way_offer2", "遠くへ行って、降りる。それを二度。帰ってきたら、話を聞かせてくれ。\n行き先は、巨神の碑を立てれば増えるそうだ。",
       "Fly somewhere far and come down. Twice. Then come back and tell me.\nThey say raising a titan waymark gives it more places to go.",
       options=[
    opt("accept", "話しに来る。", "I'll come and tell you.", kind="quest", do=[start(WAY), add_aff(1)]),
    opt("later", "また今度。", "Another time.", goto="@entry"),
], note="サブ「雲間の道標」を始める")
C.node("fq_c_way_done", "戻ったか。……どうだった。", "You're back. ...Well?", face="smile", options=[
    opt("sea", "雲の海の上を泳いでいた。", "It swam across a sea of clouds.", goto="fq_c_way_sea"),
    opt("small", "村が、豆粒みたいだった。", "The villages looked like grains of sand.", goto="fq_c_way_small"),
], note="サブ「雲間の道標」の報告")
C.node("fq_c_way_sea", "雲の、海……。\n下から見上げると、雲は天井だ。上から見ると、海になるのか。",
       "A sea of clouds...\nFrom below, the clouds are a ceiling. From above, they become a sea.", next="fq_c_way_done2")
C.node("fq_c_way_small", "豆粒か。……そうだろうな。\n空の上の誰かには、俺たちはそう見えているんだろう。",
       "Grains of sand. ...I suppose so.\nThat must be how we look to whoever is up there.", face="grave", next="fq_c_way_done2")
C.node("fq_c_way_done2", "……俺はたぶん、一生そこへは行けない。でも、あんたの話で少し見えた気がする。\n"
       "礼だ。旅の目印に使っていた石だよ。もう、俺には要らない。",
       "...I'll likely never go there. But your words let me see a little of it.\n"
       "Here, for your trouble. A stone I used to mark my way. I don't need it anymore.", options=[
    opt("thanks", "受け取っておく。", "I'll take it."),
], note="ここに入るとサブ「雲間の道標」が終わる")

# 干潮を数える
C.node("fq_c_tide_offer", "海のほうで、妙な話を聞いた。沖の島が、朝と夕に岸へ寄ってくるそうだ。潮が引くころにな。",
       "I heard a strange tale by the sea. An island offshore drifts in to the coast, morning and evening, when the tide goes out.",
       face="grave", next="fq_c_tide_offer2")
C.node("fq_c_tide_offer2", "俺は海が苦手だ。……確かめてきてくれないか。\n潮が引くころに海辺で待って、寄ってくるものを、三度その目で。",
       "I'm no good with the sea. ...Would you see for yourself?\nWait by the shore when the tide goes out, and watch what comes in. Three times.",
       options=[
    opt("accept", "見てくる。", "I'll go and see.", kind="quest", do=[start(TIDE), add_aff(1)]),
    opt("later", "今はやめておく。", "Not now.", goto="@entry"),
], note="サブ「干潮を数える」を始める")
C.node("fq_c_tide_done", "島じゃなかったんだな。……甲羅を背負った、山みたいなもの、か。",
       "So it wasn't an island. ...Something like a mountain with a shell on its back.", face="grave", next="fq_c_tide_done2",
       note="サブ「干潮を数える」の報告")
C.node("fq_c_tide_done2", "朝と夕に一度ずつ、潮が引くころ。……鯨は空で、あれは海で、刻を守って動いている。\n"
       "誰に言われたわけでもないだろうに。何かを待っているみたいだな。",
       "Once at dawn and once at dusk, as the tide ebbs. ...The whale in the sky, that thing in the sea, both keeping time.\n"
       "As if someone told them to. As if they were waiting for something.", face="grave",
       do=[unlock("chapter1/tide_hours")], options=[
    opt("thanks", "そうかもしれない。", "Perhaps they are."),
], note="ここに入るとサブ「干潮を数える」が終わる。伝承「干潮の刻」を記す")


# ================================================================ クエスト
QUESTS = []


def quest(qid, order, title, summary, done, stages, reward, giver=None, repeat="", board=False, min_stage=0, unlock_=None,
          unlock_hint=None, story="", chapter="", unlock_last=None):
    base = f"quest.skyabyss.{qid}"
    text(f"{base}.title", *title)
    text(f"{base}.summary", *summary)
    text(f"{base}.done", *done)
    if unlock_hint:
        text(f"{base}.unlock_hint", *unlock_hint)
    st_json = []
    for s in stages:
        text(f"{base}.{s['id']}.journal", *s["journal"])
        obs = []
        for o in s["objectives"]:
            text(f"{base}.{s['id']}.{o['id']}", *o["text"])
            oo = {k: v for k, v in o.items() if k not in ("text",)}
            obs.append(oo)
        sj = {"id": s["id"], "objectives": obs}
        if s.get("mode"):
            sj["mode"] = s["mode"]
        if s.get("xp"):
            sj["xp"] = s["xp"]
        st_json.append(sj)
    out = {"category": "sub"}
    if chapter:  # 章 (巨神ごと)
        out["chapter"] = chapter
    out["order"] = order
    if giver:
        out["giver"] = giver
    if repeat:
        out["repeat"] = repeat
    if board:
        out["board"] = True
    if min_stage:
        out["min_stage"] = min_stage
    if unlock_:
        out["unlock"] = unlock_
    out["stages"] = st_json
    out["reward"] = reward
    if unlock_last:  # 後から足した解放条件は、書き付けの最後に置く (コミット済みの並び)
        out["unlock"] = unlock_last
    QUESTS.append((qid, out, title, story))


def ob(oid, type_, ja, en, **kw):
    o = {"id": oid, "type": type_, "text": (ja, en)}
    o.update(kw)
    return o


def stage(sid, journal, objectives, xp=0, mode=None):
    s = {"id": sid, "journal": journal, "objectives": objectives}
    if xp:
        s["xp"] = xp
    if mode:
        s["mode"] = mode
    return s


# ---- 一度きり (NPC の頼み)
quest(SHADOW, 40, ("空の影の帳面", "The Shadow Tally"),
      ("影が通るのを、その下で二度見て、語り部に話す。", "Watch the shadow pass overhead twice, then tell the storyteller."),
      ("柱の最後の刻み目は、途中で止まっている。", "The last notch on the doorpost stops halfway."),
      [stage("watch", ("語り部の母は、影が通るたびに、戸口の柱へ刻み目を入れていたという。\n晴れた昼に空を見上げ、影の下に立って数えよう。",
                       "The storyteller's mother cut a notch into her doorpost each time the shadow passed.\n"
                       "On clear days I will look up, stand beneath it, and count."),
             [ob("see", "signal", "空の影が通るのを、その下で見よう", "Watch the sky shadow pass overhead", target="sky_shadow_seen",
                 count=2, party=False)]),
       stage("tell", ("影を二度、その下で見た。\n語り部に話しに行こう。", "I have seen the shadow twice.\nI should tell the storyteller."),
             [ob("talk", "talk", "語り部に、影のことを話そう", "Tell the storyteller about the shadow", target=ST,
                 topic="fq_shadow_done2")])],
      {"xp": 60, "affinity": {ST: 5}}, giver=ST,
      story="語り部の母は影が通るたびに柱へ刻み目を入れた。刻み目はどれも峰のほうから斜めに入り、最後の一本だけ途中で止まっている。")

quest(PARCEL_Q, 41, ("火のそばへ", "To the Fireside"),
      ("語り部の包みを外れのイサナへ届け、預かりものを持ち帰る。", "Take the storyteller's bundle to the whale chaser, and bring back what he sends."),
      ("留め具は、小さな鯨の形をしていた。", "The clasp was carved in the shape of a small whale."),
      [stage("deliver", ("語り部から、イサナへの包みを預かった。干した肉と、古い肩掛けが入っている。\n村の外れの火のところへ持っていこう。",
                         "The storyteller gave me a bundle for the whale chaser: dried meat and an old shawl.\n"
                         "I'll take it to his fire at the edge of the village."),
             [ob("give", "talk", "イサナに、包みを届けよう", "Deliver the bundle to the whale chaser", target=WC, topic="fq_c_parcel2")]),
       stage("return", ("肩掛けの隠しに、骨を削った留め具が入っていた。イサナは、自分のものではないという。\n語り部に返しに行こう。",
                        "A bone clasp was tucked in the shawl's pocket. The whale chaser says it isn't his.\n"
                        "I'll return it to the storyteller."),
             [ob("give", "talk", "語り部に、留め具を返そう", "Return the clasp to the storyteller", target=ST, topic="fq_parcel_back2")])],
      {"xp": 50, "items": [reward_item("whalebone_clasp"), {"item": "skyabyss:cloud_jerky", "count": 2}],
       "affinity": {ST: 5, WC: 5}}, giver=ST,
      story="語り部がイサナに干し肉と母の肩掛けを送る。肩掛けの隠しから、母がなくした鯨骨の留め具が出てくる。語り部はそれをプレイヤーに託す。")

quest(GAPS, 42, ("書の空白を埋める", "Filling the Blank Pages"),
      ("伝承の書の序章と一章を書き埋め、語り部に見せる。", "Fill in the prologue and chapter one of the Book of Lore, then show the storyteller."),
      ("この字の癖は、語り部の母の帳面に似ているという。", "She says my handwriting resembles her mother's notebook."),
      [stage("prologue", ("序章の頁には、まだ白いところがある。村の言い伝えと、空の影の話。\n語り部や村の長持ち、空そのものが、続きを知っている。",
                          "The prologue still has blank pages: the village's old sayings, the shadow in the sky.\n"
                          "The storyteller, the village chests, and the sky itself know the rest."),
             [ob("lore", "lore", "伝承の書の序章を、すべて記そう", "Complete the prologue of the Book of Lore",
                 targets=["prologue/the_book", "prologue/shadow", "prologue/counting_song", "prologue/seventh_night",
                          "prologue/shaved_peak", "prologue/watcher", "prologue/shadow_heading"])], xp=40),
       stage("chapter1", ("岩鯨の背には、まだ読んでいない碑文や、拾っていない頁があるはずだ。\n海の巨神のことも、書けることがある。",
                          "There must be inscriptions I haven't read and pages I haven't found on the stone whale's back.\n"
                          "And there are things to write about the titan of the sea."),
             [ob("lore", "lore", "伝承の書の一章を、十一項目まで記そう", "Record eleven entries in chapter one of the Book of Lore",
                 targets=["chapter1/stone_whale", "chapter1/sky_ruins", "chapter1/inscription_west", "chapter1/inscription_east",
                          "chapter1/inscription_below", "chapter1/inscription_heart", "chapter1/inscription_vault",
                          "chapter1/captain", "chapter1/heart_stilled", "chapter1/sky_key", "chapter1/carried_people",
                          "chapter1/return_fire", "chapter1/whale_song", "chapter1/tide_hours"], count=11)], xp=60),
       stage("tell", ("書けることは、ほとんど書いた。\n語り部に見せに行こう。", "I have written down almost everything I can.\nI'll show the storyteller."),
             [ob("talk", "talk", "語り部に、書を見せよう", "Show the book to the storyteller", target=ST, topic="fq_gaps_done2")])],
      {"xp": 150, "items": [reward_item("indigo_bookmark")], "affinity": {ST: 5}}, giver=ST,
      story="数え歌のつづきのあと。書を埋めて見せると、語り部は字の癖が母の帳面に似ていると言い、母の栞を渡す（留まる者への伏線）。")

quest(WAY, 43, ("雲間の道標", "Waymarks Among the Clouds"),
      ("岩鯨に乗って二度旅をし、上から見た地上をイサナに話す。", "Travel twice on the stone whale and tell the whale chaser what the land looks like from above."),
      ("イサナは、刻み目の石をくれた。もう要らないという。", "The whale chaser gave me his notched stone. He says he no longer needs it."),
      [stage("ride", ("イサナは、鯨の上から見た地上の話を聞きたいという。\n呼び笛を吹くと、岩鯨の子が迎えに来る。空の海図の行き先へ、二度旅をしよう。行き先は、巨神の碑で増やせる。（笛の旅は地上から地上へ。背の迷宮には入らない）",
                      "The whale chaser wants to hear about the land seen from above.\n"
                      "Blow the call whistle and the stone whale's young will come for you. Travel to two destinations on the sky chart. "
                      "Titan waymarks add more. (Whistle travel goes from ground to ground; it never enters the maze on the back.)"),
             [ob("ride", "signal", "岩鯨に乗って、旅をしよう", "Travel on the stone whale", target="titan_voyage", count=2, party=False)]),
       stage("tell", ("二度、鯨の上から地上を見た。\nイサナの火のところへ、話をしに行こう。", "I have seen the land from the whale's back twice.\n"
                      "I'll go and tell the whale chaser."),
             [ob("talk", "talk", "イサナに、旅の話をしよう", "Tell the whale chaser about your travels", target=WC, topic="fq_c_way_done2")])],
      {"xp": 120, "items": [reward_item("notched_stone")], "affinity": {WC: 5}}, giver=WC, min_stage=1,
      unlock_=stilled("stone_whale"),
      story="鎖をつかめなかった鯨追いの代わりに、岩鯨で旅をして話を聞かせる。")

quest(TIDE, 44, ("干潮を数える", "Counting the Low Tides"),
      ("干潮の刻に岸へ寄るものを三度見て、イサナに話す。", "Watch what comes to the shore at low tide three times, then tell the whale chaser."),
      ("朝と夕に一度ずつ、潮が引くころ。何かを待っているように。", "Once at dawn, once at dusk, as the tide ebbs. As if waiting for something."),
      [stage("watch", ("沖の島が、朝と夕の潮が引くころに、岸へ寄ってくるという。\n干潮の刻に海辺で待ち、寄ってきたものをそばで見よう。三度。",
                       "They say an island drifts to the shore at dawn and dusk, when the tide goes out.\n"
                       "I'll wait by the sea at low tide and watch it up close. Three times."),
             [ob("watch", "signal", "干潮の刻に、岸に寄ったものをそばで見よう", "Watch what comes ashore at low tide, up close",
                 target="tide_low_watched", count=3, party=False)]),
       stage("tell", ("三度とも、同じ刻に寄ってきた。\nイサナに話しに行こう。", "All three times, it came at the same hour.\nI'll tell the whale chaser."),
             [ob("talk", "talk", "イサナに、干潮のことを話そう", "Tell the whale chaser about the low tides", target=WC, topic="fq_c_tide_done2")])],
      {"xp": 150, "affinity": {WC: 5}}, giver=WC, min_stage=1, chapter="titan_tide",
      story="潮殻の巨神が干潮の刻に岸へ寄ることを、自分の目で三度確かめる（08 の「自分で気づく」を壊さない程度の手がかり）。")

# ---- 言伝ての板 (日替わり)
quest("daily_lost_flock", 60, ("夜を越す羊", "Sheep Before Nightfall"),
      ("柵を越えた羊を三頭、板のそばまで連れ戻す。", "Bring three sheep that strayed from the pen back to the board."),
      ("羊は、板のそばで落ち着いて草を食んでいる。", "The sheep graze calmly by the board."),
      [stage("herd", ("板の紙：「羊が三頭、柵を越えて出ていった。暗くなる前に、この板のそばまで連れ戻してほしい。影の年は、羊が落ち着かない」\n"
                      "小麦を持っていけば、ついてくるはずだ。",
                      "A note on the board: \"Three sheep got over the fence. Please bring them back to this board before dark. "
                      "In a shadow year the sheep are restless.\"\nIf I carry wheat, they should follow."),
             [ob("herd", "herd", "羊を、板のそば（10 ブロック）へ連れてこよう", "Lead sheep to within 10 blocks of the message board",
                 target="minecraft:sheep", count=3, radius=10)])],
      {"xp": 30, "items": [{"item": "minecraft:bread", "count": 3}, {"item": "minecraft:white_wool", "count": 2}]},
      repeat="daily", board=True,
      unlock_last=flag("met", WC), unlock_hint=("イサナと話すと、受けられる", "Talk to Isana to take this on"),
      story="影の年は羊が落ち着かない。連れ戻すだけの短い頼み。")

quest("daily_night_watch", 61, ("夜回り", "Night Watch"),
      ("村のまわりで、夜にうろつくものを六体倒す。仲間の分も数える。", "Defeat six night prowlers around the village. Party members count too."),
      ("今夜は、静かに眠れそうだ。", "Tonight, the village can sleep in peace."),
      [stage("watch", ("板の紙：「夜になると、村のまわりをうろつくものが増えた。七日目の夜は戸を閉めるが、ほかの夜は誰かが見張らないと」",
                       "A note on the board: \"More things prowl around the village at night. On the seventh night we bar the doors, "
                       "but on other nights someone must keep watch.\""),
             [ob("kill", "kill", "夜にうろつくものを倒そう（ゾンビ・スケルトン・クモなど）",
                 "Defeat night prowlers (zombies, skeletons, spiders...)",
                 targets=["minecraft:zombie", "minecraft:skeleton", "minecraft:spider", "minecraft:creeper", "minecraft:husk",
                          "minecraft:stray", "minecraft:drowned", "minecraft:zombie_villager", "minecraft:witch"], count=6)])],
      {"xp": 40, "items": [{"item": "minecraft:torch", "count": 8}, {"item": "minecraft:bread", "count": 2}]},
      repeat="daily", board=True,
      unlock_last=flag("met", WC), unlock_hint=("イサナと話すと、受けられる", "Talk to Isana to take this on"),
      story="七日目の夜の決まりの裏で、ほかの夜を見張る人がいる。")

quest("daily_firewood", 62, ("炉の薪", "Firewood for the Hearth"),
      ("原木を十六本、言伝ての板に納める。", "Hand in sixteen logs at the message board."),
      ("炉の火は、今夜も絶えない。", "The hearth fire will not go out tonight."),
      [stage("wood", ("板の紙：「炉の薪が足りない。原木を十六本、この板のところへ。七日目の夜は、火を絶やせないから」\n"
                      "原木を持って板を開けば、受け取ってもらえる。",
                      "A note on the board: \"We're short of firewood. Sixteen logs, please, left at this board. "
                      "On the seventh night the fire must not go out.\"\nOpening the board with the logs in hand will hand them over."),
             [ob("wood", "turn_in", "原木を言伝ての板に納めよう", "Hand in logs at the message board", target="#minecraft:logs", count=16)])],
      {"xp": 30, "items": [{"item": "minecraft:emerald", "count": 1}, {"item": "minecraft:bread", "count": 2}]},
      repeat="daily", board=True,
      unlock_last=flag("met", WC), unlock_hint=("イサナと話すと、受けられる", "Talk to Isana to take this on"),
      story="七日目の夜に火を絶やさないための薪。暦の言い伝えを、日々の暮らしの側から見せる。")

quest("daily_bone_sentry", 63, ("骨の番兵の見回り", "Sentinel Rounds"),
      ("岩鯨の背で、骨の番兵を八体眠らせる。仲間の分も数える。", "Put eight bone sentinels to rest on the stone whale's back. Party members count too."),
      ("番兵たちは、しばらく起きあがらない。", "The sentinels will stay down for a while."),
      [stage("rounds", ("板の紙（見慣れない、角ばった字）：「鯨の背の骨の番兵が、また起きあがっている。八つ、眠らせてきてほしい。——鯨追い」",
                        "A note on the board, in an angular, unfamiliar hand: \"The bone sentinels on the whale's back are rising again. "
                        "Put eight of them to rest. —The whale chaser\""),
             [ob("kill", "kill", "岩鯨の背で、骨の番兵を倒そう", "Defeat bone sentinels on the stone whale's back",
                 target="skyabyss:bone_sentinel", count=8)])],
      {"xp": 60, "items": [{"item": "skyabyss:stone_whale_shard", "count": 2}]},
      repeat="daily", board=True, min_stage=1,
      unlock_last=flag("met", WC), unlock_hint=("イサナと話すと、受けられる", "Talk to Isana to take this on"),
      story="鎖をつかめないイサナが、板に頼みを貼っている。")

# ---- 言伝ての板 (週替わり)
quest("weekly_back_trials", 80, ("背中の試練", "Trials of the Back"),
      ("岩鯨の背の試練を三つ越える。一緒にいた仲間の分も数える。", "Clear three trials on the stone whale's back. Party members with you count too."),
      ("三つの試練を越えた。", "Three trials passed."),
      [stage("trials", ("板の紙：「鯨の背の試練を、三つ越えてきてほしい。光る印に着いたと聞いたら、それでいい。——鯨追い」",
                        "A note on the board: \"Clear three of the trials on the whale's back. Once I hear you reached the glowing marks, "
                        "that's enough. —The whale chaser\""),
             [ob("trial", "signal", "岩鯨の背の試練を越えよう（光る印に着く）", "Clear a trial on the stone whale's back (reach the glowing mark)",
                 target="back_trial_cleared", count=3)])],
      {"xp": 100, "items": [{"item": "skyabyss:stone_whale_shard", "count": 3}, {"item": "skyabyss:resonance_shard_stone_whale", "count": 1}]},
      repeat="weekly", board=True, unlock_=all_(lore("chapter1/sky_ruins"), flag("met", WC)),
      unlock_hint=("岩鯨の背に渡ると、受けられる", "Available once you have reached the stone whale's back"),
      story="岩鯨の背は誰でも乗れる（10 の 3 章）。序章のうちから周回に触れる入口。")

# 順路を越える (docs/design/32 の 3.1。祠から順路を心臓まで越える。どの巨神でもよい)
quest("weekly_route", 83, ("順路を越える", "Cross a Route"),
      ("巨神の祠から順路に挑み、心臓まで越える。どの巨神でもよい。", "Take a route from a titan shrine and cross it to the heart. Any titan will do."),
      ("順路の果てで、心臓が止まった。", "The heart stilled at the end of the route."),
      [stage("route", ("板の紙：「祠の歌に呼ばれて、もう一度背を渡ってきてほしい。——鯨追い」",
                       "A note on the board: \"Answer a shrine's song and cross a back once more. — the Whale Chaser\""),
             [ob("route", "signal", "順路を心臓まで越えよう", "Cross a route to the heart", target="route_cleared", count=1)])],
      {"xp": 160, "items": [{"item": "skyabyss:resonance_crystal", "count": 2}, {"item": "skyabyss:song_shard_stone_whale", "count": 1}]},
      repeat="weekly", board=True, unlock_={"type": "stilled", "titan": "stone_whale"},
      unlock_hint=("巨神の心臓を一度止めると、受けられる", "Available once you have stilled a titan's heart"),
      story="祠（32 の 3.1）から挑む周回の入口。報いの歌片は岩鯨のもの。")

quest("weekly_heart_again", 81, ("心臓をもう一度", "The Heart, Once More"),
      ("岩鯨の心臓を、もう一度止める。戦いに加わった仲間の分も数える。", "Still the stone whale's heart once more. Party members in the fight count too."),
      ("鼓動は止まった。けれど、また打ちはじめるのだろう。", "The beating has stopped. But it will start again, surely."),
      [stage("heart", ("板の隅の紙：「止めた鼓動は、また打ちはじめる。七日に一度は、確かめてきてほしい」\n誰が書いたのか、名前はない。",
                       "A note in the corner of the board: \"A stilled heartbeat starts again. Go and make sure of it, once every seven days.\"\n"
                       "It is unsigned."),
             [ob("heart", "kill", "岩鯨の心臓を止めよう", "Still the stone whale's heart", target="skyabyss:stone_whale_heart", count=1)])],
      {"xp": 150, "items": [{"item": "skyabyss:resonance_crystal", "count": 1}, {"item": "skyabyss:resonance_shard_stone_whale", "count": 2}]},
      repeat="weekly", board=True, min_stage=1,
      unlock_last=flag("met", WC), unlock_hint=("イサナと話すと、受けられる", "Talk to Isana to take this on"),
      story="名前のない紙。七日という数が、月のループ（01）と七日目の夜をほのめかす。")

# ================================================================ 伝承の項目 (2 つ。manual: 会話でだけ記される)
LORE = [
    ("prologue/shadow_heading", "prologue", 25, ["prologue/shadow", "prologue/shaved_peak"],
     ("柱の刻み目", "Notches on the Doorpost"),
     ("語り部の家の戸口の柱には、細い刻み目が並んでいる。影が通るたびに、語り部の母が小刀で入れたものだという。\n\n"
      "刻み目はどれも同じ向きに斜めで、峰のほうから下りてくる。\n\n最後の一本だけが、途中で止まっている。",
      "Fine notches line the doorpost of the storyteller's house. Her mother cut one with a knife each time the shadow passed.\n\n"
      "Every notch slants the same way, coming down from the direction of the peaks.\n\nOnly the last one stops halfway.")),
    ("chapter1/tide_hours", "titan_tide", 140, ["chapter1/stone_whale"],
     ("干潮の刻", "The Hour of Low Tide"),
     ("朝と夕、潮が引きはじめるころに、沖の島が岸へ寄ってくる。三度見て、三度とも同じ刻だった。\n\n"
      "寄ってきた甲羅の縁には、古い門が水から顔を出していた。\n\n刻が過ぎるまで、何かを待つように動かない。",
      "At dawn and at dusk, as the tide begins to ebb, the offshore island drifts to the coast. I watched three times; "
      "three times it came at the same hour.\n\nOn the rim of its shell, an old gate rose out of the water.\n\n"
      "It does not move until the hour has passed, as if waiting for something.")),
]
for lid, ch, order, related, title, body in LORE:
    key = "lore.skyabyss." + lid.replace("/", ".")
    text(key + ".title", *title)
    text(key + ".body", *body)

# ================================================================ 画面の文
text("block.skyabyss.message_board", "言伝ての板", "Message Board")
text("gui.skyabyss.board.title", "言伝ての板", "Message Board")
text("gui.skyabyss.board.title_npc", "村の頼みごと（語り部が預かっている分）", "Village Requests (storyteller)")
text("gui.skyabyss.board.quota", "今日受けた頼み %s / %s", "Taken today %s / %s")
text("gui.skyabyss.board.next", "次の張り替えまで、およそ %s 分", "Next posting in about %s min")
text("gui.skyabyss.board.empty", "今日は、頼みごとが貼られていない。", "No requests are posted today.")
text("gui.skyabyss.board.footer", "品を納める頼みは、品を持って板を開けば渡せる。", "To hand in goods, open the board holding them.")
text("gui.skyabyss.board.accept", "受ける", "Accept")
text("gui.skyabyss.board.hidden", "？？？", "???")
text("gui.skyabyss.board.state.active", "受けている", "In progress")
text("gui.skyabyss.board.state.done", "済んだ", "Done")
text("gui.skyabyss.board.state.full", "今日はもう受けられない", "Daily limit reached")
text("gui.skyabyss.board.state.locked", "まだ", "Not yet")
text("gui.skyabyss.board.mates", "仲間も %s 人", "+%s in party")
text("message.skyabyss.board.handed", "「%s」の品を、板に納めた。", "Handed in the items for \"%s\" at the board.")
text("gui.skyabyss.quest.repeat.", "", "")
text("gui.skyabyss.quest.repeat.daily", "日替わり", "Daily")
text("gui.skyabyss.quest.repeat.weekly", "週替わり", "Weekly")
text("gui.skyabyss.quest.repeat_note.daily", "日替わりの頼み（夜明けに張り替え）", "Daily request (reposted at dawn)")
text("gui.skyabyss.quest.repeat_note.weekly", "週替わりの頼み（七日ごとに張り替え）", "Weekly request (reposted every seven days)")
text("gui.skyabyss.quest.from_board", "依頼: 言伝ての板", "From: the message board")
text("gui.skyabyss.quest.hidden_row", "？？？", "???")
text("gui.skyabyss.quest.locked.0", "", "")
text("gui.skyabyss.quest.locked.1", "一章のあと", "After chapter I")
text("gui.skyabyss.quest.locked.2", "二章のあと", "After chapter II")
text("gui.skyabyss.quest.locked.3", "三章のあと", "After chapter III")
text("gui.skyabyss.quest.locked.4", "四章のあと", "After chapter IV")
# ほかの生成スクリプトが作る板のクエスト (daily_tide_rounds・weekly_tide_heart は gen_story_quests.py、
# daily_storm_rounds・weekly_storm_heart は gen_storm_lore.py) にも、同じ「鯨追いと会ってから」の案内文を付ける
for _qid in ("daily_tide_rounds", "weekly_tide_heart", "daily_storm_rounds", "weekly_storm_heart"):
    text(f"quest.skyabyss.{_qid}.unlock_hint", "イサナと話すと、受けられる", "Talk to Isana to take this on")


# ================================================================ 書き出し
def write_json(path: Path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        f.write(json.dumps(data, ensure_ascii=False, indent=2) + "\n")


def md(s: str) -> str:
    return s.replace("|", "\\|").replace("\n", "<br>")


def cond_text(c) -> str:
    if c is None:
        return "—"
    t = c["type"]
    if t == "all":
        return "、".join(cond_text(x) for x in c["of"])
    if t == "any":
        return "(" + " または ".join(cond_text(x) for x in c["of"]) + ")"
    if t == "not":
        return f"not({cond_text(c['of'])})"
    if t == "quest":
        is_ = c.get("is", "active")
        if c.get("stage"):
            return f"{c['quest']} が「{c['stage']}」"
        return f"{c['quest']} が {is_}"
    if t == "lore":
        return f"伝承 {c['entry']}"
    if t == "flag":
        return f"印 {c['flag']}" + (f"（{c['npc'].split(':')[1]}）" if c.get("npc") else "")
    if t == "item":
        return f"{c['item'].split(':')[1]} ×{c.get('count', 1)}" + (f"（印 {c['token']}）" if c.get("token") else "")
    if t == "stilled":
        return f"{c['titan']} の心臓を自分で止めた"
    if t == "stage":
        return f"段階 {c['min']} 以上"
    return json.dumps(c, ensure_ascii=False)


def act_text(acts) -> str:
    out = []
    for a in acts or []:
        t = a["type"]
        if t == "quest_start":
            out.append(f"クエスト開始 {a['quest']}")
        elif t == "affinity":
            out.append(f"好感度{a['amount']:+d}")
        elif t == "lore":
            out.append(f"伝承 {a['entry']}")
        elif t == "give":
            out.append(f"渡す {JA.get(a.get('name'), a['item'])}")
        elif t == "take":
            out.append(f"受け取る {a['item'].split(':')[1]}" + (f"（印 {a['token']}）" if a.get("token") else ""))
        elif t == "open_board":
            out.append("板の画面を開く")
        else:
            out.append(t)
    return "、".join(out)


def objective_text(o) -> str:
    t = o["type"]
    if t == "signal":
        return f"合図 {o['target']} ×{o.get('count', 1)}" + ("（仲間も）" if o.get("party", True) else "")
    if t == "kill":
        tg = o.get("targets") or [o["target"]]
        names = ", ".join(x.split(":")[1] for x in tg)
        return f"倒す {names} ×{o.get('count', 1)}（仲間も）"
    if t == "talk":
        return f"{o['target'].split(':')[1]} の節 `{o['topic']}`"
    if t == "lore":
        return f"伝承 {o.get('count', len(o['targets']))}/{len(o['targets'])} 項目"
    if t == "turn_in":
        return f"板に納める {o['target']} ×{o['count']}"
    if t == "herd":
        return f"受けた板の {o['radius']} ブロック以内に {o['target'].split(':')[1]} ×{o['count']}"
    return t


def reward_text(r) -> str:
    parts = []
    if r.get("xp"):
        parts.append(f"経験値 {r['xp']}")
    for it in r.get("items", []):
        name = JA.get(it.get("name"), it["item"].split(":")[1])
        parts.append(f"{name} ×{it['count']}")
    for role, n in r.get("affinity", {}).items():
        parts.append(f"好感度 {role.split(':')[1]} +{n}")
    return "、".join(parts)


def doc_tables() -> str:
    L = []
    L.append("#### クエストの一覧（生成）\n")
    L.append("| クエスト | 依頼 | 解放 | 繰り返し | 目的 | 報酬 | 共有 | 短い筋 |")
    L.append("| --- | --- | --- | --- | --- | --- | --- | --- |")
    for qid, out, title, story in QUESTS:
        giver = {ST: "語り部", WC: "鯨追い"}.get(out.get("giver"), "言伝ての板" if out.get("board") else "—")
        unlock_ = f"段階 {out.get('min_stage', 0)}"
        if out.get("unlock"):
            unlock_ += "、" + cond_text(out["unlock"])
        rep = {"daily": "日替わり", "weekly": "週替わり"}.get(out.get("repeat", ""), "一度きり")
        objs = " → ".join("、".join(objective_text(o) for o in s["objectives"]) for s in out["stages"])
        shared = "あり" if any(o["type"] in ("kill",) or (o["type"] == "signal" and o.get("party", True))
                              for s in out["stages"] for o in s["objectives"]) else "人ごと"
        stage_xp = sum(s.get("xp", 0) for s in out["stages"])
        rw = reward_text(out["reward"]) + (f"（段階で計 {stage_xp}）" if stage_xp else "")
        L.append(f"| {title[0]} `{qid}` | {giver} | {md(unlock_)} | {rep} | {md(objs)} | {md(rw)} | {shared} | {md(story)} |")
    for ext, name in ((S, "語り部"), (C, "鯨追い")):
        L.append(f"\n#### {name}の足し書き（`{ext.role}`）\n")
        L.append("入口（元の最後の入口の手前に足す）:\n")
        for e in ext.entries:
            L.append(f"- {md(cond_text(e['if']))} → `{e['node']}`")
        L.append("\n| 節 | 顔 | セリフ | 入ったとき | 次 / 選択肢 |")
        L.append("| --- | --- | --- | --- | --- |")
        for nid, ja, face, do, nxt, options, note in ext.order:
            if ja is None:
                o = options[0]
                L.append(f"| `{nid}` | | | | 「{md(o['ja'])}」→ `{o['goto']}` |")
                continue
            body = md(ja) + (f"<br>（{md(note)}）" if note else "")
            if options:
                after = "<br>".join(f"「{md(o['ja'])}」→ `{o['goto']}`"
                                    + (f"（条件: {md(cond_text(o['if']))}）" if o.get("if") else "")
                                    + (f"（{md(act_text(o['do']))}）" if o.get("do") else "") for o in options)
            else:
                after = f"▼ `{nxt}`"
            L.append(f"| `{nid}` | {face} | {body} | {md(act_text(do))} | {after} |")
    L.append("\n#### 書き付けと目的の文（生成）\n")
    L.append("| クエスト | 段階 | 書き付け | 目的 |")
    L.append("| --- | --- | --- | --- |")
    for qid, out, title, story in QUESTS:
        for s in out["stages"]:
            j = JA[f"quest.skyabyss.{qid}.{s['id']}.journal"]
            o = "<br>".join(JA[f"quest.skyabyss.{qid}.{s['id']}.{x['id']}"] for x in s["objectives"])
            L.append(f"| {title[0]} | `{s['id']}` | {md(j)} | {md(o)} |")
    return "\n".join(L) + "\n"


def main():
    for qid, out, _, _ in QUESTS:
        write_json(DATA / "quest" / f"{qid}.json", out)
    write_json(DATA / "dialogue" / "storyteller_free.json", S.json())
    write_json(DATA / "dialogue" / "whale_chaser_free.json", C.json())
    for lid, ch, order, related, _, _ in LORE:
        write_json(DATA / "lore" / "entry" / f"{lid}.json",
                   {"chapter": ch, "order": order, "unlock": [{"type": "manual"}], "related": related})
    # 持たないキーと並びは、いまの lang のまま (tools/lib/langmerge.py)
    write_json(LANG / "ja_jp.json", merged(LANG / "ja_jp.json", JA))
    write_json(LANG / "en_us.json", merged(LANG / "en_us.json", EN))
    doc = DOC.read_text(encoding="utf-8")
    start_mark = "<!-- 自由なモード 生成ここから (tools/gen/story/gen_free_quests.py が書き換える。手で直さない) -->"
    end_mark = "<!-- 自由なモード 生成ここまで -->"
    if start_mark in doc:
        doc = re.sub(re.escape(start_mark) + ".*?" + re.escape(end_mark), lambda m: start_mark + "\n\n" + doc_tables() + "\n" + end_mark,
                     doc, flags=re.S)
        with open(DOC, "w", encoding="utf-8", newline="\n") as f:
            f.write(doc)
    print(f"{len(QUESTS)} quests, {len(JA)} keys")


if __name__ == "__main__":
    main()
