"""1 章のメインクエストの続き (潮殻・雷翼・余燼・天の鍵) と、潮殻〜余燼の段階で開くサブクエストを一か所で書き、
ゲームのデータと設計書の表に書き出す。gen_npc_content.py・gen_free_quests.py の続き (同じ考え方: 構造と日英の文を同じ場所に置く)。

元の会話とその足し書き (storyteller.json / *_free.json) には手を入れない。会話は「足し書き」(*_story.json、extends: true) で混ぜる。
足し書きは名前の順に混ざるので、*_free の節 (fq_menu・fq_c_menu) に選択肢を足せる。

雷翼の固有名は 12 の設計書で決まった (2026-10-17、担当 6)。余燼は 13 ができるまで仮。名前は NAMES の一か所だけ直せば、文・伝承・クエストがそろって替わる。
雷翼の背の周回 (*storm*)・伝承・碑文の文は tools/gen_storm_lore.py (skyabyss_storm) が書く。ここは雷翼のメインと古い地図だけ。

実行 (リポジトリ直下):
    python tools/gen_story_quests.py
出力:
    src/main/resources/data/skyabyss/quest/<クエスト>.json
    src/main/resources/data/skyabyss/dialogue/{storyteller,whale_chaser}_story.json   会話の足し書き
    src/main/resources/data/skyabyss/lore/entry/chapter1/...                           新しい伝承の項目
    src/main/resources/assets/skyabyss_story/lang/{ja_jp,en_us}.json                  文 (天の鍵の場面の文も)
    docs/design/07_npc_quest.md の「1 章の続き 生成」印の間                           クエストの表と全セリフ
"""
from __future__ import annotations

import json
import re
from pathlib import Path

from regroup_lore_chapters import chapter_of as _chapter_of  # 伝承の章 (巨神ごと)。写像は regroup_lore_chapters.py

ROOT = Path(__file__).resolve().parents[1]
DATA = ROOT / "src/main/resources/data/skyabyss"
LANG = ROOT / "src/main/resources/assets/skyabyss_story/lang"
DOC = ROOT / "docs/design/07_npc_quest.md"

ST = "skyabyss:storyteller"
WC = "skyabyss:whale_chaser"

# 雷翼・余燼の名前 (日本語, 英語の文中の形, 英語の題の形)。雷翼は 12 で決まった。余燼は 13 で決まったらここだけ直す
NAMES = {
    "thunder_wing": ("雷翼の巨神", "the Thunder Wing Titan", "The Thunder Wing Titan"),
    "ember": ("余燼の巨神", "the Ember Titan", "The Ember Titan"),
}
# 雷翼の担当が登録する品 (12 の 11.1 節)。別の担当が登録するので optional (まだないうちは渡さない)
WEATHERCOCK = "skyabyss:weathercock"

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


def item(i, count=1, optional=False):
    c = {"type": "item", "item": i, "count": count}
    if optional:
        c["optional"] = True
    return c


def not_(c):
    return {"type": "not", "of": c}


def all_(*cs):
    return {"type": "all", "of": list(cs)}


def any_(*cs):
    return {"type": "any", "of": list(cs)}


def stilled(titan):
    return {"type": "stilled", "titan": titan}


def stage_min(n):
    return {"type": "stage", "min": n}


def start(quest):
    return {"type": "quest_start", "quest": quest}


def unlock(entry):
    return {"type": "lore", "entry": entry}


def add_aff(n):
    return {"type": "affinity", "amount": n}


def set_flag(f):
    return {"type": "flag", "flag": f}


def give(i, count=1, name=None, lore_=None, optional=False):
    a = {"type": "give", "item": i, "count": count}
    if optional:
        a["optional"] = True
    if name:
        a["name"] = name
    if lore_:
        a["lore"] = lore_
    return a


def take(i, count=1):
    return {"type": "take", "item": i, "count": count}


ITEMS = {}


def keepsake(kid, item_id, name_ja, name_en, lore_ja, lore_en):
    nk = f"item.skyabyss.keepsake.{kid}"
    lk = f"item.skyabyss.keepsake.{kid}.lore"
    text(nk, name_ja, name_en)
    text(lk, lore_ja, lore_en)
    ITEMS[kid] = {"item": item_id, "name": nk, "lore": [lk]}


def reward_item(kid, count=1):
    it = ITEMS[kid]
    return {"item": it["item"], "count": count, "name": it["name"], "lore": it["lore"]}


keepsake("map_scrap", "minecraft:paper", "古い地図の切れ端", "A Scrap of an Old Map",
         "鯨追いの地図の端。嵐の通り道が、細い線で一本だけ引いてある。",
         "The corner of the whale chaser's map. A single thin line marks the path of the storms.")
keepsake("warm_coal", "minecraft:charcoal", "冷めない炭", "A Coal That Will Not Cool",
         "語り部の炉の灰の底から出てきた。握ると、いつまでもほんのり温かい。",
         "Found at the bottom of the storyteller's hearth. It stays faintly warm in your hand.")


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
                for k in ("kind", "if", "do"):
                    if o.get(k):
                        oo[k] = o[k]
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

TIDE = "main_tide"
THUNDER = "main_thunder"
EMBER = "main_ember"
KEY = "main_key"
MAP = "sub_old_map"
ASH = "sub_warm_coal"

TW_JA, TW_EN, TW_TITLE = NAMES["thunder_wing"]
EM_JA, EM_EN, EM_TITLE = NAMES["ember"]

# ================================================================ 語り部
S.entry("ms_tide_back", q(TIDE, stage="report"))
S.entry("ms_thunder", q(THUNDER, stage="rumor"))
S.entry("ms_thunder_back", q(THUNDER, stage="report"))
S.entry("ms_key", q(KEY, stage="show"))

S.node("ms_tide_back", "おや、潮の匂いがするね。……二つ目を、見つけたのかい。",
       "Why, you smell of the sea. ...You found the second one, then?", face="smile", next="ms_tide_back2",
       note="メイン「満ち引きの門」の報告")
S.node("ms_tide_back2", "海の底にも、鼓動があったんだね。\n空にも、海にも。……世界のあちこちで、何かが脈を打っているんだね。",
       "So there was a heartbeat at the bottom of the sea, too.\nIn the sky, and in the sea. ...Things are beating all over the world, it seems.",
       face="grave", next="ms_tide_memory",
       note="ここに入るとメイン「満ち引きの門」が終わる。続きの村の記憶と数え歌のふたつめ (ms_tide_memory → ms_tide_verse) は dialogue/ch2/storyteller_trail.json (tools/gen_ch2_ground.py)")

S.node("ms_thunder", "二つ目かい。……並べると、少し引き合っているように見えるね。\n三つ目のことなら、ひとつだけ心当たりがあるよ。",
       "The second one. ...Side by side, they almost seem to pull at each other.\nAbout the third, there's one thing I can think of.",
       face="grave", next="ms_thunder2", note="メイン「嵐を渡る翼」の始まり")
S.node("ms_thunder2", "雷の晩に、雲の中を大きな鳥が渡る。あたしの祖母は、それを見た晩に、数えるのをやめたそうだ。\n"
       "何を数えていたのかは、とうとう教えてくれなかったけどね。",
       "On thunder nights, a great bird crosses inside the clouds. My grandmother said she stopped counting the night she saw it.\n"
       "She never did tell me what she had been counting.", face="grave", options=[
           opt("go", "嵐の晩に、空を見てみる。", "I'll watch the sky on a stormy night.", do=[add_aff(1)]),
           opt("why", "何を数えていたんだろう。", "What was she counting, I wonder?", goto="ms_thunder_why"),
       ], note="ここに入るとメインの「語り部に尋ねる」が済む")
S.node("ms_thunder_why", "さあね。……稲光から音が来るまでを、数えるだろう。あれかもしれないし、違うかもしれない。",
       "Who knows. ...People count from the flash to the thunder, don't they. Maybe that. Maybe not.", next="@entry")

S.node("ms_thunder_back", "三つ目だね。……手が、少し焦げた匂いがするよ。",
       "The third one. ...Your hands smell a little scorched.", face="smile", next="ms_thunder_back2",
       note="メイン「嵐を渡る翼」の報告")
S.node("ms_thunder_back2", "残りは一つ。……そろうところを見てみたい気もするし、見たくない気もするんだよ。",
       "Only one left. ...Part of me wants to see them come together. Part of me doesn't.", face="grave", options=[
           opt("ok", "そろったら、見せに来る。", "I'll show you when they're together.", do=[add_aff(2)]),
       ], note="ここに入るとメイン「嵐を渡る翼」が終わる")

S.node("ms_key", "……そろったんだね。見せておくれ。", "...They came together, then. Let me see.", face="grave", next="ms_key2",
       note="メイン「天の鍵」(鍵がそろったあと)")
S.node("ms_key2", "空の扉の鍵だと、言い伝えでは聞いていたけれど……。\nおかしいね。この鍵、下を向いて打っているよ。",
       "The old tales call it the key to a door in the sky...\nStrange. This key is beating downward.", face="grave", next="ms_key3")
S.node("ms_key3", "……あ。\nいつつ　いきづく　つちのそこ\n……母さんは、いつつめをそう歌っていた。\nどうして今まで、思い出せなかったんだろう。",
       "...Oh.\nFive, five, a breath below the ground.\n...That's how my mother sang the fifth. Why couldn't I remember it until now?",
       do=[unlock("chapter1/fifth_verse")], next="ms_key4", note="伝承「数え歌のいつつ」を記す")
S.node("ms_key4", "むっつから先は、まだ出てこない。\n……鍵というのは、開けたい扉のほうを向くものだよ。\nそれだけは、覚えておおき。",
       "Six and beyond still won't come.\n...A key always turns toward the door it wants to open. Remember that much.", face="grave", options=[
           opt("ok", "覚えておく。", "I'll remember.", do=[add_aff(3)]),
       ], note="ここに入るとメイン「天の鍵」が終わる (ベータの終わり)")

# 冷めない炭 (段階 3)
S.add_option("fq_menu", "back", opt("coal", "炉の火のことで、困っていない？", "Is anything troubling you about the hearth?", goto="ms_coal_offer",
                                    kind="quest", if_=all_(stage_min(3), q(ASH, "none"))))
S.node("ms_coal_offer", "炉の火かい。……よく分かったね。\nこのごろ、七日目の夜まで火がもたないんだよ。昔は、灰の底に冷めない炭が一つあったんだけど。",
       "The hearth? ...How did you know.\nLately the fire won't last until the seventh night. There used to be a coal at the bottom of the ash that never cooled.",
       next="ms_coal_offer2")
S.node("ms_coal_offer2", "地の底の火の、かけらだったと聞いたよ。\n地の底まで行ける人なんて、いやしないけどね。……あんたなら、行けるのかい。",
       "They said it was a piece of the fire under the earth.\nNot that anyone can go that far down. ...Could you?", face="grave", options=[
           opt("accept", "火を持って帰る。", "I'll bring fire back.", kind="quest", do=[start(ASH), add_aff(1)]),
           opt("later", "今はいい。", "Not now.", goto="@entry"),
       ], note="サブ「冷めない炭」を始める")
S.entry("ms_coal_done", all_(q(ASH, stage="tell"), item("minecraft:magma_cream", 4)))
S.node("ms_coal_done", "これが、地の底の……。熱いね。手を離しても、まだ熱い。",
       "So this is from beneath... It's hot. Even after I let go, it's hot.", do=[take("minecraft:magma_cream", 4)], next="ms_coal_done2",
       note="サブ「冷めない炭」の報告 (マグマクリームを受け取る)")
S.node("ms_coal_done2", "炉に入れたら、灰の底で何かが応えたよ。……古い炭が、出てきた。\nこれは、あんたが持っておいき。下の火に、覚えがあるみたいだから。",
       "When I put it in the hearth, something answered at the bottom of the ash. ...An old coal came up.\n"
       "You keep it. It seems to remember the fire below.", face="grave", options=[
           opt("ok", "受け取っておく。", "I'll take it."),
       ], note="ここに入るとサブ「冷めない炭」が終わる")

# ================================================================ 鯨追い
C.entry("ms_c_tide", q(TIDE, stage="rumor"))
C.entry("ms_c_tide_back", q(TIDE, stage="report"))
C.entry("ms_c_thunder_back", q(THUNDER, stage="report"))
C.entry("ms_c_ember", q(EMBER, stage="rumor"))
C.entry("ms_c_key", all_(lore("chapter1/sky_key_whole"), not_(flag("c_key_seen"))))
C.entry("ms_c_map_done", q(MAP, stage="tell"))

C.node("ms_c_tide", "戻ったか。……欠片は、ばあさんに見せたか。\n四つに分かれているって話なら、俺も聞いたことがある。ひとつは空、ひとつは海だと。",
       "You're back. ...Did you show the old woman the fragment?\nI've heard it was split in four, too. One in the sky, one in the sea, they say.",
       face="grave", next="ms_c_tide2", note="メイン「満ち引きの門」の始まり")
C.node("ms_c_tide2", "海のほうで、沖の島が朝と夕に岸へ寄ってくるそうだ。潮が引くころにな。\n……島が動くと思うか？　俺は思わない。",
       "By the sea, they say an island drifts to the shore at dawn and dusk. When the tide goes out.\n"
       "...Do you think islands move? I don't.", options=[
           opt("go", "確かめてくる。", "I'll go and see.", do=[add_aff(1)]),
           opt("count", "干潮の刻も、数えてこようか。", "Shall I count the low tides too?", goto="fq_c_tide_offer2", kind="quest",
               if_=q("sub_tide_watch", "none")),
       ], note="ここに入るとメインの「イサナに話を聞く」が済む")
C.node("ms_c_tide_back", "甲羅の山に乗ったのか。……どうだった。", "You got onto the shelled mountain? ...How was it?", face="smile",
       options=[
           opt("town", "中に、海の底の町があった。", "There was a town from the sea floor inside.", goto="ms_c_tide_back2"),
           opt("beat", "鼓動が、潮のように満ち引きしていた。", "Its heartbeat rose and fell like the tide.", goto="ms_c_tide_back2"),
       ], note="メイン「満ち引きの門」の報告")
C.node("ms_c_tide_back2", "鯨は空で歌い、亀は海で刻を守る。\n……あいつらは、誰かに言いつけられたことを、ずっと続けているみたいだ。",
       "The whale sings in the sky, the turtle keeps the hours in the sea.\n...As if they were both still doing what someone told them to, long ago.",
       face="grave", options=[
           opt("ok", "じゃあ、また。", "See you."),
       ], note="ここに入るとメイン「満ち引きの門」が終わる")
C.node("ms_c_thunder_back", "三つ目か。……鳴る雲の、もっと上を飛んでいたやつだな。\n俺は、鳴る雲を見上げるのが怖かった。あんたは、そこへ行ったのか。",
       "The third. ...That one flew above the thundering clouds.\nI was always afraid to look up at them. And you went there.", face="grave",
       options=[
           opt("ok", "行ってきた。", "I did.", do=[add_aff(2)]),
       ], note="メイン「嵐を渡る翼」の報告 (ここに入ると終わる)")
C.node("ms_c_ember", "旅の途中で、灰が空へ昇っていく山を見たことがある。\n火の気のない晩でも、その山だけは、うっすら赤かった。",
       "Once on the road I saw a mountain where the ash rose into the sky.\nEven on nights without a fire anywhere, that mountain glowed a little red.",
       face="grave", next="ms_c_ember2", note="メイン「灰の昇る山」の始まり")
C.node("ms_c_ember2", "あれは燃えているんじゃない。じっと、くすぶって待っている。\n四つ目は、たぶんあそこだ。",
       "It isn't burning. It's smoldering, waiting.\nThe fourth is there, I think.", options=[
           opt("go", "行ってみる。", "I'll go.", do=[add_aff(1)]),
       ], note="ここに入るとメインの「イサナに話を聞く」が済む")
C.node("ms_c_key", "それが……鍵か。\n空の扉を開ける鍵だと、ずっと思っていた。……下を向いているな。",
       "So that's... the key.\nI always thought it opened a door in the sky. ...It's pointing down.", face="grave",
       do=[set_flag("c_key_seen")], next="ms_c_key2", note="天の鍵がそろったあと、一度だけ")
C.node("ms_c_key2", "俺はずっと、上ばかり見ていた。\n……足もとのことは、考えたこともなかったな。",
       "I've spent my whole life looking up.\n...I never once thought about what's under my feet.", options=[
           opt("ok", "……そうだね。", "...Yeah."),
       ])

# 鯨追いの古い地図 (段階 2)
C.add_option("fq_c_menu", "back", opt("map", "次の巨神の手がかりは、ないか。", "Any clue about the next titan?", goto="ms_c_map_offer",
                                      kind="quest", if_=all_(stage_min(2), q(MAP, "none"))))
C.node("ms_c_map_offer", "手がかりか。……これを見ろ。俺の古い地図だ。\n余白に一本だけ線を引いてある。嵐が来るたび、雲がこの線の上を走っていくんだ。",
       "A clue. ...Look at this. My old map.\nI drew one line in the margin. Every time a storm comes, the clouds run along it.", next="ms_c_map_offer2")
C.node("ms_c_map_offer2", "雷の晩に、高い所で空を見てくれ。……俺は、雷の晩に外へ出るのが、どうしても駄目でな。",
       "On a thunder night, watch the sky from somewhere high. ...I just can't make myself go out when it thunders.",
       face="grave", options=[
           opt("accept", "見てくる。", "I'll go and watch.", kind="quest", do=[start(MAP), add_aff(1)]),
           opt("later", "また今度。", "Another time.", goto="@entry"),
       ], note="サブ「鯨追いの古い地図」を始める")
C.node("ms_c_map_done", "戻ったか。……光ったか？", "You're back. ...Did it flash?", face="smile", options=[
    opt("wing", "稲光の中に、翼の形が見えた気がした。", "I thought I saw the shape of wings in the lightning.", goto="ms_c_map_done2"),
    opt("count", "光ってから音が来るまで、ずいぶん長かった。", "The thunder came a long time after the flash.", goto="ms_c_map_done2"),
], note="サブ「鯨追いの古い地図」の報告")
C.node("ms_c_map_done2", "……そうか。やっぱり、あの線の上を何かが渡っている。\nこの切れ端をやる。線のところだけ、破いておいた。",
       "...I see. Something does cross along that line.\nTake this scrap. I tore off just the part with the line.", face="grave",
       do=[unlock("chapter1/storm_path")], next="ms_c_map_done3",
       note="ここに入るとサブ「鯨追いの古い地図」が終わる (報酬で風見鶏も渡る)。伝承「嵐の通り道」を記す")
C.node("ms_c_map_done3", "それと、これも持っていけ。じいさんの風見鶏だ。\n屋根から下ろしてから、こいつは風じゃなく、嵐のほうばかり向くようになった。",
       "And take this, too. My grandfather's weathercock.\nEver since he took it down from the roof, it points at storms instead of the wind.", next="ms_c_map_done4",
       note="風見鶏 (12 の 2.6 節) の話")
C.node("ms_c_map_done4", "高い所で掲げると、嵐のほうが気づくらしい。\n……俺は、試したことがないがな。",
       "Raise it somewhere high, and they say the storm takes notice.\n...Not that I've ever tried.", face="grave", options=[
           opt("ok", "預かっておく。", "I'll keep it."),
       ])

# 風見鶏をなくしたとき (古い地図を終えた人。前の版で地図を終えて、風見鶏を受け取っていない人も)
C.add_option("fq_c_menu", "back", opt("cock", "風見鶏を、もう一つ持っていないか。", "Do you have another weathercock?", goto="ms_c_cock",
                                      if_=all_(q(MAP, "done"), not_(item(WEATHERCOCK, optional=True)))))
C.node("ms_c_cock", "嵐に持っていかれたか。\n……じいさんは、屋根ごとに一つずつ作っていた。まだ残っている。",
       "Did a storm take it?\n...My grandfather made one for every roof. There are still a few left.",
       do=[give(WEATHERCOCK, optional=True)], options=[
           opt("ok", "今度は、なくさない。", "I won't lose this one."),
       ], note="風見鶏を渡す (なくしたとき)")


# ================================================================ クエスト
QUESTS = []


def quest(qid, category, order, title, summary, done, stages, reward, giver=None, start_when=None, repeat="", board=False,
          min_stage=0, unlock_=None, unlock_hint=None, story=""):
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
            obs.append({k: v for k, v in o.items() if k != "text"})
        sj = {"id": s["id"], "objectives": obs}
        for k in ("mode", "xp", "skip_if"):
            if s.get(k):
                sj[k] = s[k]
        st_json.append(sj)
    out = {"category": category, "order": order}
    if giver:
        out["giver"] = giver
    if start_when:
        out["start_when"] = start_when
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
    QUESTS.append((qid, out, title, story))


def ob(oid, type_, ja, en, **kw):
    o = {"id": oid, "type": type_, "text": (ja, en)}
    o.update(kw)
    return o


def stage(sid, journal, objectives, xp=0, mode=None, skip_if=None):
    s = {"id": sid, "journal": journal, "objectives": objectives}
    if xp:
        s["xp"] = xp
    if mode:
        s["mode"] = mode
    if skip_if:
        s["skip_if"] = skip_if
    return s


def cond_ob(oid, ja, en, c):
    return ob(oid, "condition", ja, en, **{"if": c})


# ---- メイン 2: 満ち引きの門 (潮殻)
quest(TIDE, "main", 20, ("満ち引きの門", "The Gate of Ebb and Flow"),
      ("二つ目の欠片を求めて、海の巨神の背へ渡る。", "Cross onto the back of the titan of the sea, in search of the second fragment."),
      ("空にも、海にも、何かが脈を打っている。", "Something beats in the sky, and in the sea."),
      [stage("rumor", ("語り部は、鍵は四つに分かれていると言った。残りは、ほかの巨神が抱えているのだろうか。\nイサナなら、何か聞いているかもしれない。",
                       "The storyteller said the key was split in four. Do the other titans hold the rest?\nIsana might have heard something."),
             [ob("talk", "talk", "イサナに、話を聞きに行こう", "Ask Isana", target=WC, topic="ms_c_tide2")], xp=40,
             skip_if=any_(lore("chapter1/tide_shell"), lore("chapter1/tide_hours"))),
       stage("see", ("沖の島が、潮の引くころに岸へ寄ってくるという。\n……島ではないのかもしれない。",
                     "They say an island comes to the shore when the tide goes out.\n...Perhaps it isn't an island."),
             [ob("see", "lore", "海辺で、潮とともに寄る島を探そう", "Look for the tide's island", targets=["chapter1/tide_shell"])], xp=60),
       stage("board", ("甲羅の縁に、古い門があるという。潮が引いたときにだけ、口を開ける。",
                       "There is an old gate on the rim of the shell, they say. It opens only when the tide is out."),
             [ob("board", "lore", "潮が引いたら、門をくぐって背へ渡ろう", "Cross the gate at low tide",
                 targets=["chapter1/tide_ruins"])], xp=60),
       stage("inscriptions", ("甲羅の中にも、背中の民の字が残っているはずだ。",
                              "The people of the back must have left writing inside the shell as well."),
             [ob("read", "lore", "甲羅の中の碑文を読んでみよう", "Read the inscriptions in the shell",
                 targets=["chapter1/inscription_tide_beach", "chapter1/inscription_tide_hub", "chapter1/inscription_tide_west",
                          "chapter1/inscription_tide_east", "chapter1/inscription_tide_front", "chapter1/inscription_tide_heart"], count=2)],
             xp=90, skip_if=stilled("tide_shell")),
       stage("heart", ("甲羅の奥で、潮のように満ち引きする鼓動がある。\n欠片は、そのそばに納められているのだろう。",
                       "Deep in the shell, a heartbeat rises and falls like the tide.\nThe fragment must lie beside it."),
             [cond_ob("still", "甲羅の奥で脈打つものを、止めに行こう", "Go and still what beats deep in the shell", stilled("tide_shell"))]),
       stage("report", ("二つ目の欠片が手に入った。一つ目のほうへ、かすかに引かれている気がする。\n村へ戻って、話をしよう。",
                        "I have the second fragment. It seems to pull, faintly, toward the first.\nI'll go back to the village and tell them."),
             [ob("st", "talk", "語り部に、欠片を見せに行こう", "Go and show the storyteller the fragment", target=ST, topic="ms_tide_back2"),
              ob("wc", "talk", "イサナに、甲羅の山の話をしに行こう", "Tell Isana about the shelled mountain", target=WC, topic="ms_c_tide_back2")],
             mode="any")],
      {"xp": 300, "affinity": {ST: 5, WC: 5}}, giver=WC, start_when=all_(q("main_song", "done"), stilled("stone_whale")),
      story="鍵は四つに分かれている。イサナは海の噂を話し、プレイヤーは潮殻の背へ渡って二つ目の欠片を手にする。")

# ---- メイン 3: 嵐を渡る翼 (雷翼。12 の 9 章・11.1 節。見つける → 乗る → 碑文と風鳴り石 → 心臓)
# 進みは伝承 (見た・渡った・碑文)、背中の担当の合図 storm_trial_<west|east|front>、心臓の記録 (Progression.onTitanStopped) で数える。
# 本体がなくても /skyabyss lore unlock と /skyabyss quest signal で通せる
STORM_SIGHT = ["chapter1/thunder_wing", "chapter1/storm_wing"]
STORM_INSCRIPTIONS = [f"chapter1/inscription_storm_{p}" for p in ("tail", "hub", "west", "east", "eye", "heart")]
quest(THUNDER, "main", 30, ("嵐を渡る翼", "Wings Across the Storm"),
      ("三つ目の欠片を求めて、風に逆らう嵐を追い、その背へ渡る。", "In search of the third fragment, chase the storm that moves against the wind and cross onto its back."),
      ("残りは一つ。", "Only one left."),
      [stage("rumor", ("欠片が二つになった。並べると、少し引き合っている。\n語り部に、三つ目の心当たりを聞いてみよう。",
                       "I have two fragments now. Side by side, they pull at each other a little.\nI'll ask the storyteller about the third."),
             [ob("talk", "talk", "語り部に、三つ目のことを尋ねよう", "Ask the storyteller about the third", target=ST, topic="ms_thunder2")], xp=40,
             skip_if=any_(*[lore(x) for x in STORM_SIGHT])),
       stage("see", ("雷の晩、雲の中を大きな鳥が渡るという。\n風に逆らって動く嵐を見かけたら、それかもしれない。",
                     "On thunder nights, they say, a great bird crosses inside the clouds.\nIf I see a storm moving against the wind, that may be it."),
             [ob("see", "lore", "風に逆らって動く嵐を探そう", "Find the storm against the wind",
                 targets=STORM_SIGHT, count=1)], xp=80),
       stage("board", (f"{TW_JA}は、午後の夕立の刻に、近くでいちばん高い峰へ降りてくるらしい。\n足もとに立つ風の柱に入れば、背まで運ばれるかもしれない。",
                       f"At the hour of the afternoon squall, {TW_EN} is said to come down onto the highest peak nearby.\n"
                       "If I step into the column of wind at its feet, it may carry me up to its back."),
             [ob("board", "lore", "夕立の刻、昇り風に乗って背へ上がろう", "At the hour of the squall, ride the updraft onto its back",
                 targets=["chapter1/storm_ruins"])], xp=60, skip_if=stilled("thunder_wing")),
       stage("trials", ("鳥の背の中に、風の民の字が残っている。\n三つのゴールに着けば、\n奥へ続く門が開くのだろう。",
                        "The wind folk left writing inside the bird's back.\nIf I reach the goal, the way deeper in should open."),
             [ob("read", "lore", "背の中の碑文を読んでみよう", "Read the inscriptions inside its back", targets=STORM_INSCRIPTIONS, count=2),
              ob("stone", "signal", "ゴールの光る印に一つ着いてみよう", "Reach one of the goal marks", targets=[f"storm_trial_{t}" for t in ("west", "east", "front")],
                 count=1, party=False)],
             xp=90, skip_if=stilled("thunder_wing")),
       stage("heart", ("竜骨の門の下で、雷のような鼓動が打っている。\n音は、上からではなく、下から昇ってくる。",
                       "Below the keel gate, a heartbeat strikes like thunder.\nThe sound rises from below, not from above."),
             [cond_ob("still", "竜骨の奥で脈打つものを、止めに行こう", "Go and still what beats behind the keel", stilled("thunder_wing"))]),
       stage("report", ("三つ目の欠片が手に入った。手が、少し焦げた匂いがする。\n村へ戻ろう。",
                        "I have the third fragment. My hands smell faintly scorched.\nBack to the village."),
             [ob("st", "talk", "語り部に、欠片を見せに行こう", "Go and show the storyteller the fragment", target=ST, topic="ms_thunder_back2"),
              ob("wc", "talk", "イサナに、嵐の上の話をしに行こう", "Tell Isana what lies above the storm", target=WC, topic="ms_c_thunder_back")],
             mode="any")],
      {"xp": 400, "affinity": {ST: 5, WC: 5}}, giver=ST, start_when=q(TIDE, "done"),
      story="語り部の祖母は、雷の晩に雲の中を渡る鳥を見て、何かを数えるのをやめた。風に逆らう嵐を追い、夕立の刻に昇り風で背へ。碑文とゴールの印、竜骨の奥の心臓。")

# ---- メイン 4: 灰の昇る山 (余燼。中身は 13 の担当が作る)
quest(EMBER, "main", 40, ("灰の昇る山", "The Mountain Where Ash Rises"),
      ("四つ目の欠片を求めて、灰が空へ昇る山へ向かう。", "Head for the mountain where the ash rises into the sky, in search of the fourth fragment."),
      ("四つの欠片が、手の中で一つになった。", "The four fragments became one in my hands."),
      [stage("rumor", ("残りは一つ。イサナは、旅の途中でいろいろなものを見ている。\n火のところへ、話を聞きに行こう。",
                       "Only one left. Isana has seen many things on his travels.\nI'll go to his fire and ask."),
             [ob("talk", "talk", "イサナに、四つ目のことを尋ねよう", "Ask Isana about the fourth", target=WC, topic="ms_c_ember2")], xp=40,
             skip_if=lore("chapter1/ember")),
       stage("see", ("灰が空へ昇っていく山があるという。火の気のない晩でも、その山だけはうっすら赤い。",
                     "There is a mountain where the ash rises into the sky. Even on nights without fire, it glows faintly red."),
             [ob("see", "lore", f"灰の昇る山へ向かい、{EM_JA}を探そう", f"Head for the mountain where ash rises and look for {EM_EN}",
                 targets=["chapter1/ember"])], xp=80),
       stage("heart", ("山だと思っていたものが、ゆっくりと息をしている。\nその奥で、くすぶっている鼓動を止めよう。",
                       "What I took for a mountain is slowly breathing.\nI'll still the heartbeat smoldering inside it."),
             [cond_ob("still", "熾火の奥で脈打つものを、止めに行こう", "Go and still what beats inside the embers", stilled("ember"))]),
       stage("key", ("四つ目の欠片が手に入った。欠片どうしが、手の中で引き合っている。",
                     "I have the fourth fragment. The fragments pull at each other in my hands."),
             [ob("key", "lore", "四つの欠片を、手もとにそろえよう", "Hold all four fragments together", targets=["chapter1/sky_key_whole"])])],
      {"xp": 400, "affinity": {WC: 5}}, giver=WC, start_when=q(THUNDER, "done"),
      story="イサナは灰が空へ昇る山を見たことがある。四つ目の欠片を手にすると、欠片が一つの鍵になる。")

# ---- メイン 5: 天の鍵 (ベータの終わり)
quest(KEY, "main", 50, ("天の鍵", "The Sky Key"),
      ("一つになった鍵を、語り部に見せる。", "Show the storyteller the key that the fragments became."),
      ("鍵は、まだ足もとへ向かって打っている。", "The key is still beating downward."),
      [stage("show", ("鍵は、雲の上を指さなかった。足もとのずっと下へ向かって、かすかに脈を打っている。\n語り部に見せに行こう。",
                      "The key did not point above the clouds. It beats faintly, toward somewhere far beneath my feet.\n"
                      "I'll show it to the storyteller."),
             [ob("talk", "talk", "語り部に、鍵を見せに行こう", "Go and show the storyteller the key", target=ST, topic="ms_key4")])],
      {"xp": 500, "affinity": {ST: 5, WC: 5}}, giver=ST, start_when=any_(q(EMBER, "done"), lore("chapter1/sky_key_whole")),
      story="語り部は鍵を見て、数え歌のいつつめを思い出す。「鍵というのは、開けたい扉のほうを向くものだよ」。")

# ---- サブ (一度きり)
quest(MAP, "sub", 45, ("鯨追いの古い地図", "The Whale Chaser's Old Map"),
      ("雷の晩に高い所で空を見て、嵐の通り道をイサナに話す。", "Watch the sky from somewhere high on a thunder night, and tell Isana about the path of the storms."),
      ("地図の切れ端には、嵐の通り道が一本だけ引いてある。風見鶏は、いつも嵐のほうを向いている。",
       "The scrap of map shows a single line: the path of the storms. The weathercock always points at a storm."),
      [stage("watch", ("イサナの古い地図の余白に、線が一本引いてある。嵐が来るたび、雲がその上を走るという。\n雷の晩に、高い所で空を見よう。",
                       "A single line is drawn in the margin of Isana's old map. Every storm, the clouds run along it.\n"
                       "On a thunder night, I'll watch the sky from somewhere high."),
             [cond_ob("storm", "雷雨のときに、空の見える高い所（高さ 100 以上）に立つ", "Stand somewhere high under open sky (height 100+) during a thunderstorm",
                      all_({"type": "weather", "thunder": True}, {"type": "height", "min": 100}))]),
       stage("tell", ("雷の晩、高い所から空を見た。\nイサナの火のところへ、話をしに行こう。", "I watched the sky from up high on a thunder night.\n"
                      "I'll go and tell Isana."),
             [ob("talk", "talk", "イサナに、嵐の空の話をする", "Tell Isana about the stormy sky", target=WC, topic="ms_c_map_done2")])],
      {"xp": 300, "items": [reward_item("map_scrap"), {"item": WEATHERCOCK, "count": 1, "optional": True}], "affinity": {WC: 5}},
      giver=WC, min_stage=2,
      story="雷が怖いイサナの代わりに、嵐の晩に高い所で空を見る。雷翼の手がかり (伝承「嵐の通り道」) と、イサナの祖父の風見鶏 (12 の 2.6 節)。背中には入らない。")

quest(ASH, "sub", 46, ("冷めない炭", "The Coal That Will Not Cool"),
      ("地の底の火のかけらを持ち帰り、語り部の炉に入れる。", "Bring back a piece of the fire beneath the earth for the storyteller's hearth."),
      ("灰の底から出てきた炭は、いつまでも温かい。", "The coal from the bottom of the ash stays warm."),
      [stage("fetch", ("語り部の炉の火が、七日目の夜までもたないという。昔は、灰の底に「地の底の火」のかけらがあったそうだ。\n"
                       "地の底へ……ネザーへ行けば、冷めない火が見つかるかもしれない。",
                       "The storyteller's hearth won't last to the seventh night. There was once a piece of 'fire from beneath the earth' in the ash.\n"
                       "Beneath the earth... In the Nether, I might find a fire that won't cool."),
             [ob("go", "visit", "ネザーへ行く", "Go to the Nether", target="minecraft:the_nether"),
              ob("cream", "collect", "マグマクリームを集める", "Gather magma cream", target="minecraft:magma_cream", count=4)]),
       stage("tell", ("冷めない火のかけらが、四つそろった。\n語り部の炉へ持っていこう。", "I have four pieces of a fire that won't cool.\n"
                      "I'll take them to the storyteller's hearth."),
             [ob("talk", "talk", "語り部に、火のかけらを渡す", "Give the storyteller the pieces of fire", target=ST, topic="ms_coal_done2")])],
      {"xp": 350, "items": [reward_item("warm_coal"), {"item": "minecraft:bread", "count": 3}], "affinity": {ST: 10}}, giver=ST, min_stage=3,
      story="七日目の夜まで火がもたない炉。地の底の火を入れると、灰の底で何かが応える (2 章の深淵へのほのめかし)。")

# ---- 言伝ての板 (潮殻の背の周回)
quest("daily_tide_rounds", "sub", 64, ("潮殻の背の見回り", "Rounds on the Tide Shell"),
      ("潮殻の背で、珊瑚憑き・磯走り・泡吹きを八体倒す。仲間も数える。",
       "Defeat eight coral thralls, reef scuttlers or bubble puffers on the tide shell's back. Party members count too."),
      ("甲羅の中は、しばらく静かになるだろう。", "It will be quiet inside the shell for a while."),
      [stage("rounds", ("板の紙（潮で滲んだ字）：「亀の甲羅の中で、珊瑚に憑かれたものがまた動きはじめた。八つ、鎮めてきてほしい」",
                        "A note on the board, the ink blurred by seawater: \"The coral-haunted things are stirring again inside the turtle's shell. "
                        "Put eight of them to rest.\""),
             [ob("kill", "kill", "潮殻の背で、珊瑚憑き・磯走り・泡吹きを倒す", "Defeat coral thralls, reef scuttlers and bubble puffers on the tide shell",
                 targets=["skyabyss:coral_thrall", "skyabyss:reef_scuttler", "skyabyss:bubble_puffer"], count=8)])],
      {"xp": 90, "items": [{"item": "skyabyss:tide_shell_shard", "count": 2}]}, repeat="daily", board=True, min_stage=2,
      story="潮殻の背の周回の入口 (10 の 8.3 節: 段階 2)。")

quest("weekly_tide_heart", "sub", 82, ("潮の鼓動をもう一度", "The Tide's Heart, Once More"),
      ("潮殻の心臓を、もう一度止める。戦いに加わった仲間も数える。", "Still the tide shell's heart once more. Party members in the fight count too."),
      ("満ち引きは止まった。けれど、また満ちてくるのだろう。", "The ebb and flow has stopped. But the tide will come in again, surely."),
      [stage("heart", ("板の隅の紙（同じ、名前のない字）：「潮は七日で満ちる。満ちきる前に、確かめてきてほしい」",
                       "A note in the corner of the board, in the same unsigned hand: \"The tide fills in seven days. Go and make sure, before it is full.\""),
             [ob("heart", "kill", "潮殻の心臓を止める", "Still the tide shell's heart", target="skyabyss:tide_heart", count=1)])],
      {"xp": 220, "items": [{"item": "skyabyss:resonance_crystal", "count": 1}, {"item": "skyabyss:tide_shell_shard", "count": 3}]},
      repeat="weekly", board=True, min_stage=2,
      story="「心臓をもう一度」と同じ名前のない紙。七日という数を、もう一度ほのめかす。")

# ================================================================ 伝承の項目 (manual: 会話・天の鍵・巨神の担当が記す)
# 項目: (ID, 順, 関わる項目, 題, 本文[, 記される条件])。条件を省くと manual だけ
LORE = [
    # 雷翼を近くで見た (96 ブロック以内。本体の担当が manual でも記す)。遠くの嵐を見た「風に逆らう嵐」(chapter1/storm_wing) は gen_storm_lore.py
    ("chapter1/thunder_wing", 160, ["chapter1/storm_wing", "chapter1/storm_path"], (TW_JA, TW_TITLE),
     ("稲妻が近くに落ちた一瞬、雨の幕の向こうに、山のような鳥がいた。\n\n冠の銅の針に、稲妻が何度も落ちる。鳥は、まばたきひとつしなかった。",
      "In the instant lightning struck close by, there was a bird the size of a mountain beyond the curtain of rain.\n\n"
      "Again and again the lightning struck the copper spikes of its crown. It did not so much as blink."),
     [{"type": "near_entity", "target": "skyabyss:thunder_wing", "radius": 96}, {"type": "manual"}]),
    ("chapter1/storm_path", 158, ["chapter1/thunder_wing"], ("嵐の通り道", "The Path of the Storms"),
     ("鯨追いの古い地図の余白に、線が一本引いてある。嵐が来るたび、雲はこの線の上を走る。\n\n"
      "雷の晩に高い所から見ると、稲光は線に沿って、同じ向きに続けて光った。何かが、決まった道を渡っている。",
      "A single line is drawn in the margin of the whale chaser's old map. Whenever a storm comes, the clouds run along it.\n\n"
      "From high ground on a thunder night, the lightning flashed along that line, one after another, always the same way. "
      "Something crosses by a fixed road.")),
    ("chapter1/ember", 176, ["chapter1/thunder_wing"], (EM_JA, EM_TITLE),
     ("灰が、空へ昇っていく。火の気のない晩でも、その山だけはうっすらと赤い。\n\n山だと思っていたものが、ゆっくりと息をした。",
      "Ash rises into the sky. Even on nights without a fire anywhere, that one mountain glows faintly red.\n\n"
      "What I had taken for a mountain slowly drew a breath.")),
    ("chapter1/sky_key_whole", 180, ["chapter1/sky_key", "chapter1/fifth_verse"], ("天の鍵", "The Sky Key"),
     ("四つの欠片が手の中で触れ合うと、継ぎ目がひとりでに消えて、一つの鍵になった。\n\n"
      "言い伝えでは、雲の上へ続く扉の鍵だという。けれど鍵は空を指さない。足もとのずっと下へ向かって、かすかに脈を打っている。",
      "When the four fragments touched in my hand, the seams faded away on their own, and they became a single key.\n\n"
      "The old tales say it opens a door that leads above the clouds. Yet the key does not point to the sky. "
      "It beats faintly, toward somewhere far beneath my feet.")),
    ("chapter1/fifth_verse", 182, ["prologue/counting_song", "chapter1/sky_key_whole"], ("数え歌のいつつ", "The Fifth Verse"),
     ("語り部が、天の鍵を見て思い出した。\n\nいつつ　いきづく　つちのそこ\n\nむっつから先は、まだ誰も知らない。",
      "The storyteller remembered it when she saw the sky key.\n\nFive, five, a breath below the ground.\n\nNo one yet knows six, or what comes after.")),
]
LORE = [e if len(e) == 6 else e + ([{"type": "manual"}],) for e in LORE]
for lid, order, related, title, body, _ in LORE:
    key = "lore.skyabyss." + lid.replace("/", ".")
    text(key + ".title", *title)
    text(key + ".body", *body)

# ================================================================ 天の鍵 (品・場面・知らせ)
text("item.skyabyss.sky_key", "天の鍵", "Sky Key")
text("item.skyabyss.sky_key.desc", "四つの欠片が一つになった。空を指さず、足もとへ向かってかすかに脈を打つ",
     "Four fragments made one. It does not point to the sky; it beats faintly toward the ground beneath you")
text("message.skyabyss.story.key_formed", "四つの欠片が手の中で触れ合い、一つの鍵になった。",
     "The four fragments touched in your hand and became a single key.")
text("message.skyabyss.story.key_formed_world", "%sの手の中で、天の鍵が形をとった。", "In %s's hand, the sky key took shape.")
text("scene.skyabyss.sky_key.title", "天の鍵", "The Sky Key")
text("scene.skyabyss.sky_key.line1", "四つの欠片が、手の中で触れ合った。", "The four fragments touched in your hand.")
text("scene.skyabyss.sky_key.line2", "継ぎ目が、ひとりでに消えていく。", "The seams fade away on their own.")
text("scene.skyabyss.sky_key.line3", "鍵は、空を指さなかった。", "The key does not point to the sky.")
text("scene.skyabyss.sky_key.line4", "足もとの、ずっと下。何かが、ゆっくりと打っている。", "Far beneath your feet, something is slowly beating.")
text("scene.skyabyss.sky_key.end_title", "一章　天の鍵", "Chapter One: The Sky Key")
text("scene.skyabyss.sky_key.end_sub", "ベータ版はここまでです", "This is the end of the beta")
text("scene.skyabyss.sky_key.end_thanks", "遊んでくれて、ありがとう。", "Thank you for playing.")
text("scene.skyabyss.sky_key.end_days", "この世界の %s 日目", "Day %s of this world")
text("scene.skyabyss.sky_key.end_hint", "Esc かクリックで戻る　（巨神の背の周回と頼みごとは、このあとも続けられる）",
     "Esc or click to return  (titan backs and requests remain open)")
text("subtitles.skyabyss_story.key_form", "欠片が触れ合う", "Fragments meet")
text("subtitles.skyabyss_story.deep_beat", "足もとの遠い鼓動", "A distant beat below")
text("message.skyabyss.story.show_key", "天の鍵: %s", "Sky key: %s")
text("message.skyabyss.story.key_yes", "そろえた（%s 日目）", "assembled (day %s)")
text("message.skyabyss.story.key_no", "欠片 %s / 4", "fragments %s / 4")


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
        if c.get("stage"):
            return f"{c['quest']} が「{c['stage']}」"
        return f"{c['quest']} が {c.get('is', 'active')}"
    if t == "lore":
        return f"伝承 {c['entry']}"
    if t == "flag":
        return f"印 {c['flag']}"
    if t == "item":
        return f"{c['item'].split(':')[1]} ×{c.get('count', 1)}"
    if t == "stilled":
        return f"{c['titan']} の心臓を自分で止めた"
    if t == "stage":
        return f"段階 {c['min']} 以上"
    if t == "weather":
        return "雷雨で空の下"
    if t == "height":
        return f"高さ {c['min']} 以上"
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
        elif t == "take":
            out.append(f"受け取る {a['item'].split(':')[1]} ×{a.get('count', 1)}")
        elif t == "flag":
            out.append(f"印 {a['flag']}")
        else:
            out.append(t)
    return "、".join(out)


def objective_text(o) -> str:
    t = o["type"]
    if t == "kill":
        tg = o.get("targets") or [o["target"]]
        return f"倒す {', '.join(x.split(':')[1] for x in tg)} ×{o.get('count', 1)}（仲間も）"
    if t == "talk":
        return f"{o['target'].split(':')[1]} の節 `{o['topic']}`"
    if t == "lore":
        tg = o["targets"]
        return f"伝承 {tg[0]}" if len(tg) == 1 else f"伝承 {o.get('count', len(tg))}/{len(tg)} 項目"
    if t == "condition":
        return f"条件 {cond_text(o['if'])}"
    if t == "visit":
        return f"行く {o['target']}"
    if t == "collect":
        return f"集める {o['target'].split(':')[1]} ×{o.get('count', 1)}"
    return t


def reward_text(r) -> str:
    parts = []
    if r.get("xp"):
        parts.append(f"経験値 {r['xp']}")
    for it in r.get("items", []):
        parts.append(f"{JA.get(it.get('name'), it['item'].split(':')[1])} ×{it['count']}")
    for role, n in r.get("affinity", {}).items():
        parts.append(f"好感度 {role.split(':')[1]} +{n}")
    return "、".join(parts)


def doc_tables() -> str:
    L = ["#### クエストの一覧（生成）\n",
         "| クエスト | 種類 | 依頼 | 始まり・解放 | 繰り返し | 目的（段階の順） | 報酬 | 短い筋 |",
         "| --- | --- | --- | --- | --- | --- | --- | --- |"]
    for qid, out, title, story in QUESTS:
        giver = {ST: "語り部", WC: "イサナ"}.get(out.get("giver"), "言伝ての板" if out.get("board") else "—")
        opened = cond_text(out["start_when"]) if out.get("start_when") else f"段階 {out.get('min_stage', 0)}"
        rep = {"daily": "日替わり", "weekly": "週替わり"}.get(out.get("repeat", ""), "一度きり")
        objs = " → ".join(("どれか: " if s.get("mode") == "any" else "") + "、".join(objective_text(o) for o in s["objectives"])
                          + (f"（skip_if: {cond_text(s['skip_if'])}）" if s.get("skip_if") else "") for s in out["stages"])
        stage_xp = sum(s.get("xp", 0) for s in out["stages"])
        rw = reward_text(out["reward"]) + (f"（段階で計 {stage_xp}）" if stage_xp else "")
        kind = "メイン" if out["category"] == "main" else "サブ"
        L.append(f"| {title[0]} `{qid}` | {kind} | {giver} | {md(opened)} | {rep} | {md(objs)} | {md(rw)} | {md(story)} |")
    for ext, name in ((S, "語り部"), (C, "イサナ")):
        L.append(f"\n#### {name}の足し書き（`{ext.role}`、`*_story.json`）\n")
        L.append("入口（元の最後の入口の手前に足す。`*_free` の入口のあと）:\n")
        for e in ext.entries:
            L.append(f"- {md(cond_text(e['if']))} → `{e['node']}`")
        L.append("\n| 節 | 顔 | セリフ | 入ったとき | 次 / 選択肢 |")
        L.append("| --- | --- | --- | --- | --- |")
        for nid, ja, face, do, nxt, options, note in ext.order:
            if ja is None:
                o = options[0]
                L.append(f"| `{nid}` | | | | 「{md(o['ja'])}」→ `{o['goto']}`（条件: {md(cond_text(o.get('if')))}） |")
                continue
            body = md(ja) + (f"<br>（{md(note)}）" if note else "")
            if options:
                after = "<br>".join(f"「{md(o['ja'])}」→ `{o['goto']}`" + (f"（条件: {md(cond_text(o['if']))}）" if o.get("if") else "")
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


START_MARK = "<!-- 1 章の続き 生成ここから (tools/gen_story_quests.py が書き換える。手で直さない) -->"
END_MARK = "<!-- 1 章の続き 生成ここまで -->"


def main():
    for qid, out, _, _ in QUESTS:
        write_json(DATA / "quest" / f"{qid}.json", out)
    write_json(DATA / "dialogue" / "storyteller_story.json", S.json())
    write_json(DATA / "dialogue" / "whale_chaser_story.json", C.json())
    for lid, order, related, _, _, unlock_ in LORE:
        write_json(DATA / "lore" / "entry" / f"{lid}.json", {"chapter": _chapter_of(lid.split("/")[-1]), "order": order, "unlock": unlock_, "related": related})
    write_json(LANG / "ja_jp.json", dict(sorted(JA.items())))
    write_json(LANG / "en_us.json", dict(sorted(EN.items())))
    doc = DOC.read_text(encoding="utf-8")
    if START_MARK in doc:
        doc = re.sub(re.escape(START_MARK) + ".*?" + re.escape(END_MARK), lambda m: START_MARK + "\n\n" + doc_tables() + "\n" + END_MARK,
                     doc, flags=re.S)
        with open(DOC, "w", encoding="utf-8", newline="\n") as f:
            f.write(doc)
    print(f"{len(QUESTS)} quests, {len(LORE)} lore entries, {len(JA)} keys")


if __name__ == "__main__":
    main()
