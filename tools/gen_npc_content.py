"""NPC の会話とクエストの中身 (JSON と文) を一か所で書き、ゲームのデータと設計書の表に書き出す。

会話の構造 (節・選択肢・条件・結果) と、日本語・英語の文を同じ場所に置くことで、
キーの書き間違いや、片方の言語だけ直し忘れる、を防ぐ。

実行 (リポジトリ直下):
    python tools/gen_npc_content.py
出力:
    src/main/resources/data/skyabyss/dialogue/<役>.json      会話 (データパックで差し替えられる)
    src/main/resources/data/skyabyss/quest/<クエスト>.json    クエスト
    src/main/resources/assets/skyabyss_npc/lang/{ja_jp,en_us}.json   会話・クエスト・画面の文
    docs/design/07_npc_quest.md の「生成」印の間                 全セリフとクエストの表
"""
from __future__ import annotations

import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
DATA = ROOT / "src/main/resources/data/skyabyss"
LANG = ROOT / "src/main/resources/assets/skyabyss_npc/lang"
DOC = ROOT / "docs/design/07_npc_quest.md"

ST = "skyabyss:storyteller"
WC = "skyabyss:whale_chaser"
MAIN = "main_song"
SONG = "sub_song"
SHARDS = "sub_shards"

JA: dict[str, str] = {}
EN: dict[str, str] = {}


def text(key, ja, en):
    assert key not in JA, key
    JA[key] = ja
    EN[key] = en


# ---------------------------------------------------------------- 条件と結果の短縮
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


def remembers(f):
    return {"type": "remembers", "flag": f}


def loop(mn):
    return {"type": "loop", "min": mn}


def item(i, count=1):
    return {"type": "item", "item": i, "count": count}


def aff(mn):
    return {"type": "affinity", "min": mn}


def watcher(mn=1):
    return {"type": "watcher", "min": mn}


def not_(c):
    return {"type": "not", "of": c}


def any_(*cs):
    return {"type": "any", "of": list(cs)}


def all_(*cs):
    return list(cs)


def set_flag(f):
    return {"type": "flag", "flag": f}


def add_aff(n):
    return {"type": "affinity", "amount": n}


def unlock(entry):
    return {"type": "lore", "entry": entry}


def start(quest):
    return {"type": "quest_start", "quest": quest}


def take(i, count):
    return {"type": "take", "item": i, "count": count}


# ---------------------------------------------------------------- 会話
class Dialogue:
    def __init__(self, role, name_ja, name_en):
        self.role = role
        self.path = role.split(":")[1]
        self.base = f"dialogue.skyabyss.{self.path}"
        self.entries = []
        self.nodes = {}
        self.order = []
        text(f"entity.skyabyss.{self.path}", name_ja, name_en)

    def entry(self, node, cond=None):
        e = {"node": node}
        if cond is not None:
            e["if"] = cond
        self.entries.append(e)

    def node(self, nid, ja, en, face="neutral", next=None, do=None, options=None, note=""):
        n = {"face": face}
        if do:
            n["do"] = do
        if next:
            n["next"] = next
        opts = []
        for o in options or []:
            oid, oja, oen = o[0], o[1], o[2]
            kw = o[3] if len(o) > 3 else {}
            oo = {"id": oid}
            if kw.get("kind", "talk") != "talk":
                oo["kind"] = kw["kind"]
            if "if" in kw:
                oo["if"] = kw["if"]
            if "do" in kw:
                oo["do"] = kw["do"]
            oo["goto"] = kw.get("goto", "end")
            opts.append(oo)
            text(f"{self.base}.{nid}.{oid}", oja, oen)
        if opts:
            n["options"] = opts
        self.nodes[nid] = n
        self.order.append((nid, note))
        text(f"{self.base}.{nid}", ja, en)

    def json(self):
        return {"npc": self.role, "entries": self.entries, "nodes": self.nodes}


LEAVE = ("leave", "（立ち去る）", "(Leave)", {"kind": "leave"})

# ================================================================ 語り部
st = Dialogue(ST, "語り部", "Storyteller")
text("message.skyabyss.storyteller.struck", "「……やめておくれ。年寄りをいじめても、何も出てきやしないよ」",
     "\"...Stop that. Bullying an old woman won't shake anything loose.\"")

st.entry("fragment", q(MAIN, stage="return"))
st.entry("song_done", q(SONG, stage="tell"))
st.entry("deja_vu_song", all_(loop(1), remembers("song_heard"), not_(flag("met"))))
st.entry("deja_vu", all_(loop(1), remembers("met"), not_(flag("met"))))
st.entry("first", not_(flag("met")))
st.entry("menu")

st.node("first",
        "おや、見ない顔だね。旅の人かい。\nこの村には何もないけれど、空だけは広い。……ゆっくりしておいき。",
        "Oh, a face I don't know. A traveler?\nThere's nothing in this village, but the sky is wide. ...Stay a while.",
        face="smile", next="menu", note="初めて話しかけたとき")
st.node("deja_vu",
        "……おや。\n前にも、その顔を見た気がするね。……いや、年寄りの気のせいだ。このごろ、同じ夢ばかり見るんだよ。",
        "...Oh.\nI feel I've seen that face before. ...No, an old woman's fancy. I keep having the same dream lately.",
        face="grave", next="menu", note="ループ 1 回目以降、前のループで会っていた (覚えているような)")
st.node("deja_vu_song",
        "……あんた、あの歌の続きを聞きに来たんだろう。\n……おかしいね。どうして、そう思ったんだか。",
        "...You came to hear the rest of that song, didn't you.\n...How strange. I wonder why I thought so.",
        face="grave", next="menu", note="ループ 1 回目以降、前のループで数え歌を聞いていた")

MENU_OPTS = [
    ("shadow_seen", "昼間、雲の上を何かが渡っていった。", "Something crossed above the clouds at midday.",
     {"kind": "quest", "goto": "shadow1", "if": all_(lore("prologue/shadow"), any_(q(MAIN, "none"), q(MAIN, stage="ask_storyteller")))}),
    ("shadow_ask", "空のことを聞かせてほしい。", "Tell me about the sky.",
     {"kind": "quest", "goto": "shadow1", "if": all_(not_(lore("prologue/shadow")), q(MAIN, "none"))}),
    ("chaser_where", "旅の人は、どこに？", "Where is the traveler?",
     {"kind": "quest", "goto": "chaser_hint", "if": q(MAIN, stage="find_chaser")}),
    ("song", "この村に伝わる歌はある？", "Are there any old songs in this village?",
     {"goto": "song1", "if": not_(flag("song_heard"))}),
    ("song_more", "数え歌の続きが知りたい。", "I want to know the rest of the counting song.",
     {"kind": "quest", "goto": "sub_offer", "if": all_(flag("song_heard"), q(SONG, "none"), q(MAIN, "started"))}),
    ("watcher_seen", "夜、遠くに誰かが立っていた。", "At night, someone was standing far off.",
     {"goto": "watcher", "if": all_(not_(lore("prologue/watcher")), watcher(1))}),
    ("watcher_ask", "夜に気をつけることはある？", "Is there anything to beware of at night?",
     {"goto": "watcher", "if": all_(not_(lore("prologue/watcher")), not_(watcher(1)), aff(10))}),
    ("custom", "この村の決まりごとを教えてほしい。", "Tell me the customs of this village.",
     {"goto": "calendar", "if": all_(not_(lore("prologue/seventh_night")), aff(20))}),
    ("book", "古い帳面を見せる。", "Show her the old notebook.",
     {"goto": "book", "if": all_(lore("prologue/the_book"), not_(flag("book_shown")))}),
    ("whale", "空を泳ぐ岩の山を見た。", "I saw a mountain of rock swimming in the sky.",
     {"goto": "whale", "if": all_(lore("chapter1/stone_whale"), not_(flag("whale_told")))}),
    ("back", "鯨の背中に、町の跡があった。", "There were ruins of a town on the whale's back.",
     {"goto": "back", "if": all_(lore("chapter1/sky_ruins"), not_(flag("back_told")))}),
    ("bye", "また来る。", "I'll come again.", {"kind": "leave"}),
]
st.node("menu", "何か聞きたいことでもあるのかい。", "Is there something you want to ask?", options=MENU_OPTS,
        note="話題を選ぶ節 (条件で出る話題が変わる)")

# 空の影 → 鯨追い (メイン 1 段目)
st.node("shadow1",
        "空のことかい。\n……晴れた昼に、急に暗くなることがある。羊はうずくまり、鳥は黙る。雲の上を、何かが渡っていくんだよ。",
        "The sky, is it.\n...On a clear day it sometimes goes dark all at once. The sheep crouch, the birds fall silent. Something crosses above the clouds.",
        face="grave", next="shadow2", do=[start(MAIN)], note="メインを始める")
st.node("shadow2",
        "あれが何なのかは、誰も知らない。見上げた者は、みんな口をつぐむ。\nただ、影が通った年には、決まって“歌”が聞こえるんだ。遠い空の向こうから、低く、長く。",
        "No one knows what it is. Those who looked up all hold their tongues.\nBut in the years the shadow passes, a \"song\" is always heard. From beyond the far sky, low and long.",
        face="grave", next="shadow3")
st.node("shadow3",
        "その歌を追いかけている、物好きな旅の人がいてね。ゆうべから、村の外れで火を焚いている。\n話を聞きたいなら、煙を目印にお行き。……あたしは、あまり勧めないけれど。",
        "There's a curious traveler who chases that song. Since last night they've kept a fire at the edge of the village.\nIf you want to hear it, follow the smoke. ...Not that I'd advise it.",
        options=[
            ("go", "行ってみる。", "I'll go and see.", {"do": [add_aff(1)]}),
            ("why", "どうして勧めない？", "Why wouldn't you advise it?", {"goto": "shadow_why"}),
        ], note="ここに入るとメインの「語り部に尋ねる」が済む")
st.node("shadow_why",
        "空ばかり見ている者は、足もとを見なくなる。\n……あの人も、いつか空へ行ってしまう気がするよ。",
        "Those who only watch the sky stop watching their feet.\n...I feel that one will go up into the sky someday too.",
        face="grave", options=[("go", "それでも、行ってみる。", "I'll go anyway.", {"do": [add_aff(1)]})])
st.node("chaser_hint",
        "村の外れだよ。煙が上がっているだろう。\n……あの人は、話しはじめると長いからね。",
        "At the edge of the village. You'll see the smoke.\n...Once that one starts talking, it goes on a while.",
        face="smile", next="@entry")

# 数え歌
st.node("song1",
        "歌かい。子どもらが手まりをつくときの歌なら、あるよ。\nひとつ　ひるまにかげがくる\nふたつ　ひつじとふせなさい\nみっつ　みあげちゃだめよ\nよっつ　よるにはつきがくる",
        "A song? There's the one the children sing while bouncing a ball.\nOne, at midday comes the shade\nTwo, lie down beside the sheep\nThree, don't you look up\nFour, at night there comes the moon",
        face="smile", next="song2", do=[set_flag("song_heard"), unlock("prologue/counting_song"), add_aff(3)],
        note="伝承「村の数え歌」を記す")
st.node("song2",
        "……いつつから先は、知らないんだ。あたしの母さんも、そのまた母さんも知らなかった。\n続きを知っている者は、もう誰もいないのさ。",
        "...I don't know what comes after five. Nor did my mother, nor hers.\nThere's no one left who knows the rest.",
        options=[
            ("curious", "続きが気になる。", "I'd like to know the rest.", {"goto": "song_curious", "do": [add_aff(2)]}),
            ("child", "子どもの歌だろう。", "It's just a children's song.", {"goto": "song_child", "do": [add_aff(-2)]}),
        ])
st.node("song_curious",
        "物好きだね。\n……古い頁でも探してごらん。村の家の長持ちには、誰が書いたかわからない紙切れが眠っているものさ。",
        "You're a curious one.\n...Look for old pages. In the chests of village houses, scraps of paper sleep that no one remembers writing.",
        face="smile", next="@entry")
st.node("song_child",
        "そうだね。子どもの歌だよ。\n……子どもに覚えさせたかった誰かが、いたんだろうけど。",
        "Yes. A children's song.\n...Though someone must have wanted the children to remember it.",
        face="grave", next="@entry")
st.node("sub_offer",
        "歌の続きかい……。あたしが知っているのは、歌と、いくつかの言い伝えだけだよ。\n村に残る古い言葉を集めて、あんたの帳面に書きとめておくれ。いつか、続きが見つかるかもしれない。",
        "The rest of the song... All I know is the song and a few old sayings.\nGather the old words left in the villages and write them in that notebook of yours. Someday the rest may turn up.",
        options=[
            ("accept", "探してみる。", "I'll look for them.", {"kind": "quest", "do": [start(SONG), add_aff(2)]}),
            ("later", "今はいい。", "Not now.", {"goto": "@entry"}),
        ], note="サブ「数え歌のつづき」を始める")
st.node("song_done",
        "ずいぶん集めたね。……見せておくれ。\n七日目の夜。平らになった峰。夜に立つ人影。……どれも、歌と同じほうを向いている気がするよ。",
        "You've gathered quite a lot. ...Let me see.\nThe seventh night. The flattened peak. The figure standing in the dark. ...They all seem to face the same way as the song.",
        next="song_done2", note="サブ「数え歌のつづき」の報告")
st.node("song_done2",
        "いつつ……\n……だめだね。ここまで出かかっているのに。思い出そうとすると、空が暗くなる気がするんだ。",
        "Five...\n...It's no use. It's right on the tip of my tongue. When I try to remember, it feels as if the sky goes dark.",
        face="grave", options=[("thanks", "ありがとう。", "Thank you.", {"do": [add_aff(2)]})],
        note="ここに入るとサブ「数え歌のつづき」が終わる")

# 見る者
st.node("watcher",
        "夜にひとりで外にいると、遠くに誰かが立っていることがある。\nあれは“見る者”と呼ばれている。目を合わせなければ、何もしてこない。……そう言われているよ。",
        "If you're outside alone at night, sometimes someone is standing far away.\nThey call it \"the Watcher.\" If you don't meet its eyes, it does nothing. ...So they say.",
        face="grave", next="watcher2", do=[unlock("prologue/watcher"), set_flag("watcher_told")], note="伝承「見る者」を記す")
st.node("watcher2",
        "ただ、あたしの祖母は言っていた。\nあれは、こちらを見ているんじゃない。こちらを“覚えて”いるんだって。",
        "But my grandmother used to say:\nit isn't watching us. It's \"remembering\" us.",
        face="grave", next="@entry")

# 七日目の夜 (好感度 20 から)
st.node("calendar",
        "決まりごとかい。……ひとつだけ、ある。\n七日目の夜は、戸を閉めて外へ出ないこと。古い暦の余白に、同じことが七日おきに書いてある。",
        "Customs? ...There's just one.\nOn the seventh night, shut your door and don't go out. The margins of the old calendar say the same thing every seven days.",
        face="grave", next="calendar2", do=[unlock("prologue/seventh_night"), set_flag("calendar_told")],
        note="好感度 20 から。伝承「暦の書き込み」を記す")
st.node("calendar2",
        "なぜ七日目なのかは、誰も知らない。守らなかった家の話も、聞いたことがないね。\n……守らなかった家が、残っていないだけかもしれないけど。",
        "No one knows why the seventh. I've never heard of a house that didn't keep it, either.\n...Though maybe that's only because none of them are left.",
        face="grave", next="@entry")

# 帳面
st.node("book",
        "古い帳面だね。見せておくれ。\n“忘れないうちに書け”……か。",
        "An old notebook. Let me see.\n\"Write it down before you forget\"... is it.",
        next="book2", do=[set_flag("book_shown"), add_aff(2)], note="伝承の書を持っているとき")
st.node("book2",
        "この字、どこかで見た気がするよ。……いや、気のせいだろう。\n大事にしておき。書いておけば、忘れても残る。",
        "I feel I've seen this hand somewhere. ...No, my imagination.\nKeep it safe. What's written stays, even when you forget.",
        face="grave", next="@entry")

# 鯨と背中
st.node("whale",
        "岩の山が、空を泳いでいた？\n……そうかい。歌の主を、見たんだね。",
        "A mountain of rock, swimming in the sky?\n...I see. So you've seen the singer.",
        face="grave", next="whale2", do=[set_flag("whale_told")])
st.node("whale2",
        "母さんが言っていたよ。あれが降りてくるのは、誰かを迎えに来るときだって。\n誰を迎えに来るのかは、言わなかったけど。",
        "My mother used to say it comes down when it's come to fetch someone.\nShe never said who.",
        face="grave", next="@entry")
st.node("back",
        "背中に、町が……。人が住んでいたのかい。空の上で、地面も持たずに。\n……そこで生まれて、そこで死んだのなら。その人たちにとっては、鯨が世界のすべてだったんだね。",
        "A town, on its back... People lived there? Up in the sky, with no ground of their own.\n...If they were born there and died there, then for them the whale was the whole world.",
        next="@entry", do=[set_flag("back_told"), add_aff(2)])

# 欠片 (メインの終わり)
st.node("fragment",
        "……戻ったんだね。\n手の中のそれ、見せておくれ。",
        "...You came back.\nShow me what's in your hand.",
        next="fragment2", note="メイン最後の段階 (欠片を手にしたあと)")
st.node("fragment2",
        "温かい……。脈を打っているよ。まるで、生きているみたいだ。\n言い伝えでは、空の扉の鍵は四つに分かれているという。ひとつは鯨が。残りは……さあ、どこだろうね。",
        "Warm... It's beating. As if it were alive.\nThe old tales say the key to the sky's door was split in four. One the whale had. The rest... who knows where.",
        face="grave", next="fragment3")
st.node("fragment3",
        "あんたは、空へ行くんだね。……止めはしないよ。\nただ、七日目の夜だけは、気をつけるんだよ。",
        "You're going to the sky, aren't you. ...I won't stop you.\nOnly, be careful on the seventh night.",
        options=[("thanks", "ありがとう。", "Thank you.", {"do": [add_aff(3)]})],
        note="ここに入るとメインが終わる")

# ================================================================ 鯨追い
wc = Dialogue(WC, "鯨追い", "Whale Chaser")
text("message.skyabyss.whale_chaser.struck", "「おい、よせ。火に灰が入る」", "\"Hey, quit it. You'll get ash in the fire.\"")

wc.entry("c_fragment", q(MAIN, stage="return"))
wc.entry("c_deja", all_(loop(1), remembers("met"), not_(flag("met"))))
wc.entry("c_first", not_(flag("met")))
wc.entry("c_menu")

wc.node("c_first",
        "……ん？　ああ、村の人じゃないな。\n火にあたっていくといい。俺は鯨を待っている。空を泳ぐ、岩の鯨をな。",
        "...Hm? Ah, you're not from the village.\nWarm yourself by the fire. I'm waiting for the whale. The whale of rock that swims in the sky.",
        face="smile", next="c_menu", note="初めて話しかけたとき")
wc.node("c_deja",
        "どこかで会ったか？\n……いや、空ばかり見ていると、人の顔を忘れる。気にしないでくれ。",
        "Have we met?\n...No. When you only watch the sky, you forget faces. Don't mind me.",
        face="grave", next="c_menu", note="ループ 1 回目以降、前のループで会っていた")

C_MENU = [
    ("whale_q", "語り部から、あなたのことを聞いた。", "The storyteller told me about you.",
     {"kind": "quest", "goto": "c_whale1", "if": q(MAIN, stage="find_chaser")}),
    ("whale", "鯨のことを聞かせて。", "Tell me about the whale.",
     {"goto": "c_whale1", "if": all_(not_(flag("c_whale_heard")), not_(q(MAIN, stage="find_chaser")))}),
    ("when", "鯨は、いつ来る？", "When does the whale come?",
     {"goto": "c_when", "if": all_(flag("c_whale_heard"), not_(lore("chapter1/stone_whale")))}),
    ("peak", "旅の途中で、何か見た？", "Did you see anything on your travels?",
     {"goto": "c_peak", "if": all_(flag("c_whale_heard"), not_(lore("prologue/shaved_peak")))}),
    ("saw", "鯨を見た。", "I saw the whale.",
     {"goto": "c_saw", "if": all_(lore("chapter1/stone_whale"), not_(flag("c_saw")))}),
    ("back", "鯨の背中に乗った。", "I rode on the whale's back.",
     {"goto": "c_back", "if": all_(lore("chapter1/sky_ruins"), not_(flag("c_back_told")))}),
    ("help", "何か手伝えることは？", "Is there anything I can do for you?",
     {"kind": "quest", "goto": "c_shards_offer", "if": all_(lore("chapter1/sky_ruins"), q(SHARDS, "none"))}),
    ("shards", "岩鯨の欠片を持ってきた。", "I brought shards of the whale.",
     {"kind": "give", "goto": "c_shards_thanks", "if": all_(q(SHARDS, stage="deliver"), item("skyabyss:stone_whale_shard", 3)),
      "do": [take("skyabyss:stone_whale_shard", 3)]}),
    ("bye", "じゃあ、また。", "See you.", {"kind": "leave"}),
]
wc.node("c_menu", "なにか用か。", "Need something?", options=C_MENU, note="話題を選ぶ節")

wc.node("c_whale1",
        "俺は、もう何年も鯨を追っている。\n影の年に、遠い空から聞こえる低い歌。あれの主が、岩の鯨だ。背中には町があると、昔、誰かに聞いた。",
        "I've been chasing the whale for years now.\nThat low song from the far sky in the shadow years... its singer is a whale of rock. Someone told me long ago there's a town on its back.",
        next="c_whale2", do=[set_flag("c_whale_heard")])
wc.node("c_whale2",
        "ときどき雲の下まで降りてきて、地面に届くほど長い鎖を垂らすんだ。\n俺は……一度も、つかめたことがない。降りてくるのは、いつも遠くの誰かの上なんだ。",
        "Sometimes it comes down below the clouds and lets down a chain long enough to reach the ground.\nI've... never once caught it. It always comes down over someone far away.",
        face="grave", next="c_whale3", note="ここに入るとメインの「鯨追いに話を聞く」が済む")
wc.node("c_whale3",
        "あんたの上には、来るかもしれないな。\n……もし鎖が届いたら、迷わずつかめ。",
        "Maybe it'll come over you.\n...If the chain reaches you, grab it without hesitating.",
        options=[
            ("grab", "つかんでみる。", "I'll grab it.", {"do": [add_aff(2)]}),
            ("afraid", "怖くはないのか？", "Aren't you afraid?", {"goto": "c_afraid"}),
        ])
wc.node("c_afraid",
        "怖いさ。\n……でも、あれを見上げていると、呼ばれている気がするんだ。行かなきゃならない場所があるみたいに。",
        "Of course I am.\n...But when I look up at it, it feels like I'm being called. As if there's somewhere I have to go.",
        face="grave", next="@entry")
wc.node("c_when",
        "歌が聞こえたら、空を見ろ。鯨が歌うのは、降りてくる前だ。\nそれと……鯨は、人のいる所に降りてくる。まるで、誰かを迎えに来るみたいにな。",
        "When you hear the song, look at the sky. The whale sings before it comes down.\nAnd... it comes down where there are people. As if it's come to fetch someone.",
        next="@entry")
wc.node("c_peak",
        "北のほうで、峰の頂がなくなっているのを見た。\n崩れたんじゃない。上から削ったみたいに平らで、切り口がまだ白かった。……あれは、鯨じゃない。もっと大きな何かだ。",
        "Up north, I saw a peak with its summit gone.\nIt hadn't crumbled. It was flat, as if shaved from above, and the cut rock was still white. ...That wasn't the whale. It was something bigger.",
        face="grave", next="@entry", do=[unlock("prologue/shaved_peak"), set_flag("c_peak_told")], note="伝承「平らな峰」を記す")
wc.node("c_saw",
        "見たのか！\n……そうか。うらやましいな。俺はいつも、あと少しのところで見失うんだ。",
        "You saw it!\n...I see. I envy you. I always lose it just before I get there.",
        face="smile", next="@entry", do=[set_flag("c_saw"), add_aff(3)])
wc.node("c_back",
        "乗ったのか……！\n背中には、何があった？",
        "You rode it...!\nWhat was on its back?",
        face="smile", do=[set_flag("c_back_told")], options=[
            ("town", "町の跡があった。", "The ruins of a town.", {"goto": "c_back_town", "do": [add_aff(3)]}),
            ("guard", "骨の番兵がいた。", "Guards made of bone.", {"goto": "c_back_guard", "do": [add_aff(2)]}),
            ("secret", "……言わずにおく。", "...I'll keep it to myself.", {"goto": "c_back_secret", "do": [add_aff(-1)]}),
        ])
wc.node("c_back_town",
        "町……やっぱり、人がいたんだな。\nあの歌は、その人たちが聞いていた歌なのかもしれない。",
        "A town... So there were people.\nMaybe that song is the one they used to hear.",
        next="@entry")
wc.node("c_back_guard",
        "番兵……。\n守っているものがあるってことか。鯨の、いちばん奥に。",
        "Guards...\nSo there's something being protected. Deep inside the whale.",
        face="grave", next="@entry")
wc.node("c_back_secret",
        "……そうか。そういうものかもしれないな。\n自分の目で見ろ、ってことか。",
        "...I see. Maybe that's how it should be.\nSee it with my own eyes, you mean.",
        next="@entry")
wc.node("c_shards_offer",
        "頼みがある。……背中の石を、少し持ってきてくれないか。\n鯨の体のかけらでいい。三つあれば足りる。触れてみたいんだ。俺がたどり着けなかった場所の、手ざわりを。",
        "I have a favor to ask. ...Could you bring me a few stones from its back?\nPieces of the whale's body. Three will do. I want to touch them. To feel the place I never reached.",
        options=[
            ("accept", "持ってくる。", "I'll bring them.", {"kind": "quest", "do": [start(SHARDS), add_aff(1)]}),
            ("later", "今はやめておく。", "Not right now.", {"goto": "@entry"}),
        ], note="サブ「鯨のかけら」を始める")
wc.node("c_shards_thanks",
        "これが……鯨の。\n冷たい石だ。……なのに、耳を当てると、遠くで歌が聞こえる気がする。ありがとう。礼に、これを持っていけ。俺にはもう、要らないかもしれない。",
        "So this is... the whale.\nJust cold stone. ...And yet, held to my ear, I think I hear the song, far off. Thank you. Take this as thanks. I may not need it anymore.",
        face="smile", options=[("keep", "大事にする。", "I'll take good care of it.")],
        note="石を渡すとサブ「鯨のかけら」が終わる (遠眼鏡を受け取る)")
wc.node("c_fragment",
        "……その手の中のもの。かすかに脈を打っているな。\n鯨の奥で、何かが止まったのか。……今朝、鯨の歌が少しだけ変わった気がしたんだ。",
        "...That thing in your hand. It's beating, faintly.\nDid something stop, deep inside the whale? ...This morning I thought its song had changed, just a little.",
        face="grave", next="c_fragment2", note="メイン最後の段階")
wc.node("c_fragment2",
        "語り部のばあさんにも見せてやれ。あの人は、何か知っているはずだ。\n俺は……もう少し、ここで空を見ている。",
        "Show it to the old storyteller too. She must know something.\nMe... I'll watch the sky here a while longer.",
        options=[("bye", "じゃあ、また。", "See you.", {"kind": "leave"})], note="ここに入るとメインが終わる")

DIALOGUES = [st, wc]


# ================================================================ クエスト
QUESTS = []


def quest(qid, category, order, giver, title, summary, done, stages, reward, start_when=None):
    data = {"category": category, "order": order, "giver": giver}
    if start_when is not None:
        data["start_when"] = start_when
    data["stages"] = []
    base = f"quest.skyabyss.{qid}"
    text(f"{base}.title", *title)
    text(f"{base}.summary", *summary)
    text(f"{base}.done", *done)
    for s in stages:
        sj = {"id": s["id"]}
        if s.get("mode", "all") != "all":
            sj["mode"] = s["mode"]
        if "skip_if" in s:
            sj["skip_if"] = s["skip_if"]
        if s.get("xp"):
            sj["xp"] = s["xp"]
        sj["objectives"] = []
        text(f"{base}.{s['id']}.journal", *s["journal"])
        for o in s["objectives"]:
            oj = {k: v for k, v in o.items() if k not in ("ja", "en")}
            sj["objectives"].append(oj)
            text(f"{base}.{s['id']}.{o['id']}", o["ja"], o["en"])
        data["stages"].append(sj)
    data["reward"] = reward
    QUESTS.append((qid, data))


quest(MAIN, "main", 10, ST,
      ("雲の下の歌", "The Song Beneath the Clouds"),
      ("空の影の行く先を追う。", "Follow where the shadow in the sky leads."),
      ("欠片は、ほのかに温かく脈を打っている。残る三つは、ほかの巨神が体の奥に抱えているはずだ。",
       "The fragment is faintly warm, and it beats. The other three must be held deep inside the other titans."),
      [
          {"id": "ask_storyteller", "xp": 30,
           "skip_if": lore("chapter1/stone_whale"),
           "journal": ("晴れた昼に、空が暗くなった。雲の上を、何かが渡っていった。\n村の年寄りなら、何か知っているかもしれない。",
                       "On a clear day, the sky went dark. Something crossed above the clouds.\nThe village elders might know something."),
           "objectives": [{"id": "talk", "type": "talk", "target": ST, "topic": "shadow3",
                           "ja": "村の語り部に、空のことを尋ねる", "en": "Ask the village storyteller about the sky"}]},
          {"id": "find_chaser", "xp": 30,
           "skip_if": lore("chapter1/stone_whale"),
           "journal": ("影が通った年には、決まって遠い空から“歌”が聞こえるという。\nその歌を追う旅人が、村の外れで火を焚いている。煙が目印だ。",
                       "In the years the shadow passes, a \"song\" is always heard from the far sky.\nA traveler who chases that song keeps a fire at the edge of the village. Follow the smoke."),
           "objectives": [{"id": "talk", "type": "talk", "target": WC, "topic": "c_whale2",
                           "ja": "村の外れの鯨追いに、話を聞く", "en": "Hear what the Whale Chaser at the village edge has to say"}]},
          {"id": "see_whale", "xp": 60,
           "journal": ("歌の主は、空を泳ぐ岩の鯨だという。ときどき雲の下まで降りてきて、鎖を垂らす。\n歌が聞こえたら、空を見ろ。",
                       "The singer is said to be a whale of rock that swims in the sky. Sometimes it comes below the clouds and lets down a chain.\nWhen you hear the song, look at the sky."),
           "objectives": [{"id": "see", "type": "lore", "targets": ["chapter1/stone_whale"],
                           "ja": "空を泳ぐ岩鯨を、その目で見る", "en": "See the stone whale swimming in the sky"}]},
          {"id": "board", "xp": 60,
           "journal": ("岩鯨が、すぐそこにいる。降りてきたら、地面に届いた鎖をつかめば、背中へ引き上げられるはずだ。",
                       "The stone whale is right there. When it comes down, grab the chain that reaches the ground and it should haul you up onto its back."),
           "objectives": [{"id": "ride", "type": "lore", "targets": ["chapter1/sky_ruins"],
                           "ja": "鎖をのぼり、岩鯨の背中へ渡る", "en": "Climb the chain onto the stone whale's back"}]},
          {"id": "inscriptions", "xp": 90,
           "journal": ("背中には、崩れた町があった。祠の階段を下りると、三つの道が奥へ続いている。\nどの道の先にも、背中の民が碑文を残しているはずだ。",
                       "On its back lay a ruined town. Below the shrine stairs, three paths lead deeper in.\nAt the end of each, the people of the back must have left an inscription."),
           "objectives": [{"id": "read", "type": "lore", "count": 3,
                           "targets": ["chapter1/inscription_west", "chapter1/inscription_east", "chapter1/inscription_below"],
                           "ja": "背中の碑文を三つ読む", "en": "Read the three inscriptions on the back"}]},
          {"id": "heart",
           "journal": ("封印が解けた。奥の扉の向こうで、何かが脈を打っている。\n鍵は、鼓動のそばに納められているという。",
                       "The seal is broken. Beyond the inner door, something is beating.\nThe key, they say, was laid to rest beside the heartbeat."),
           "objectives": [
               {"id": "still", "type": "lore", "targets": ["chapter1/heart_stilled"],
                "ja": "体の奥で脈打つものを、止める", "en": "Still what beats deep inside the body"},
               {"id": "fragment", "type": "lore", "targets": ["chapter1/sky_key"],
                "ja": "天の鍵の欠片を手にする", "en": "Take the fragment of the Sky Key"}]},
          {"id": "return", "mode": "any",
           "journal": ("欠片は、ほのかに温かい。\n村へ戻って、このことを話そう。",
                       "The fragment is faintly warm.\nReturn to the village and tell them."),
           "objectives": [
               {"id": "storyteller", "type": "talk", "target": ST, "topic": "fragment3",
                "ja": "語り部に、欠片を見せる", "en": "Show the fragment to the storyteller"},
               {"id": "chaser", "type": "talk", "target": WC, "topic": "c_fragment2",
                "ja": "鯨追いに、欠片を見せる", "en": "Show the fragment to the Whale Chaser"}]},
      ],
      {"xp": 200, "affinity": {ST: 5, WC: 5}},
      start_when=any_(lore("prologue/shadow"), lore("chapter1/stone_whale")))

quest(SONG, "sub", 20, ST,
      ("数え歌のつづき", "The Rest of the Counting Song"),
      ("村に残る古い言葉を集め、伝承の書に書きとめる。", "Gather the old words left in the villages and write them in the book of lore."),
      ("いつつから先は、まだ誰も知らない。", "No one yet knows what comes after five."),
      [
          {"id": "gather", "xp": 40,
           "journal": ("語り部が知っているのは、数え歌と、いくつかの言い伝えだけだという。\n村の家の長持ちに眠る破れた頁、人の話。古い言葉を伝承の書に集めよう。",
                       "The storyteller knows only the counting song and a few old sayings.\nTorn pages asleep in village chests, the words of people. Gather the old words into the book of lore."),
           "objectives": [{"id": "words", "type": "lore", "count": 4,
                           "targets": ["prologue/counting_song", "prologue/seventh_night", "prologue/shaved_peak", "prologue/watcher"],
                           "ja": "村の言い伝えを、伝承の書に記す", "en": "Record the village sayings in the book of lore"}]},
          {"id": "tell",
           "journal": ("村の言い伝えが、伝承の書にそろった。\n語り部に見せに行こう。",
                       "The village sayings are all in the book of lore.\nGo and show the storyteller."),
           "objectives": [{"id": "talk", "type": "talk", "target": ST, "topic": "song_done2",
                           "ja": "語り部に、集めた言葉を見せる", "en": "Show the storyteller the words you gathered"}]},
      ],
      {"xp": 40, "items": [{"item": "minecraft:clock", "count": 1}], "affinity": {ST: 10}})

quest(SHARDS, "sub", 30, WC,
      ("鯨のかけら", "Pieces of the Whale"),
      ("鯨追いのために、岩鯨の背から欠片を持ち帰る。", "Bring back Stone Whale Shards from the whale's back for the Whale Chaser."),
      ("鯨追いは、石を火にかざして、長いあいだ黙っていた。", "The Whale Chaser held the stones to the fire and was silent for a long time."),
      [
          {"id": "gather",
           "journal": ("鯨追いは、たどり着けなかった場所の手ざわりを知りたいという。\n背中の番兵は、ときどき岩鯨の欠片を落とす。",
                       "The Whale Chaser wants to feel the place he never reached.\nThe guards on the back sometimes drop shards of the stone whale."),
           "objectives": [{"id": "shards", "type": "collect", "target": "skyabyss:stone_whale_shard", "count": 3,
                           "ja": "岩鯨の欠片を集める", "en": "Collect stone whale shards"}]},
          {"id": "deliver",
           "journal": ("欠片が三つそろった。\n鯨追いの火のところへ持っていこう。",
                       "You have three shards.\nTake them to the Whale Chaser's fire."),
           "objectives": [{"id": "give", "type": "talk", "target": WC, "topic": "c_shards_thanks",
                           "ja": "鯨追いに、欠片を三つ渡す", "en": "Give the Whale Chaser three shards"}]},
      ],
      {"xp": 120, "items": [{"item": "minecraft:spyglass", "count": 1}], "affinity": {WC: 10}})

# ================================================================ 画面とコマンドの文
UI = [
    ("key.skyabyss.quest_log", "クエストログ", "Quest Log"),
    ("gui.skyabyss.npc.dialogue", "会話", "Conversation"),
    ("gui.skyabyss.npc.leave", "（立ち去る）", "(Leave)"),
    ("gui.skyabyss.npc.tier.0", "よそよそしい", "Wary"),
    ("gui.skyabyss.npc.tier.1", "見知らぬ人", "Stranger"),
    ("gui.skyabyss.npc.tier.2", "顔見知り", "Acquaintance"),
    ("gui.skyabyss.npc.tier.3", "打ち解けた", "Friendly"),
    ("gui.skyabyss.npc.tier.4", "信頼", "Trusted"),
    ("message.skyabyss.npc.handed", "%s を %s 個渡した", "Handed over %s ×%s"),
    ("growth.skyabyss.reason.npc", "会話", "Conversation"),
    ("gui.skyabyss.quest.log", "クエストログ", "Quest Log"),
    ("gui.skyabyss.quest.empty", "まだ、何も始まっていない。", "Nothing has begun yet."),
    ("gui.skyabyss.quest.section.main", "メイン", "Main"),
    ("gui.skyabyss.quest.section.sub", "サブ", "Side"),
    ("gui.skyabyss.quest.section.done", "終えたもの", "Finished"),
    ("gui.skyabyss.quest.giver", "依頼: %s", "From: %s"),
    ("gui.skyabyss.quest.objectives", "やること", "To do"),
    ("gui.skyabyss.quest.objectives_any", "やること（どれか一つ）", "To do (any one)"),
    ("gui.skyabyss.quest.progress", "%s / %s 段目", "Step %s of %s"),
    ("gui.skyabyss.quest.finished", "このクエストは終えた。", "You have finished this quest."),
    ("gui.skyabyss.quest.reward", "報酬: %s", "Reward: %s"),
    ("gui.skyabyss.quest.reward_xp", "経験値 %s", "%s XP"),
    ("gui.skyabyss.quest.reward_sep", "、", ", "),
    ("gui.skyabyss.quest.track", "追跡する", "Track"),
    ("gui.skyabyss.quest.untrack", "追跡をやめる", "Untrack"),
    ("gui.skyabyss.quest.or", "または", "or"),
    ("gui.skyabyss.quest.lore_hint", "知ったことは、伝承の書に。", "What you learn goes in the book of lore."),
    ("gui.skyabyss.quest.toast.started", "クエスト開始", "Quest started"),
    ("gui.skyabyss.quest.toast.updated", "クエストが進んだ", "Quest updated"),
    ("gui.skyabyss.quest.toast.finished", "クエスト達成", "Quest complete"),
    ("message.skyabyss.quest.started", "クエスト「%s」が始まった", "Quest started: %s"),
    ("message.skyabyss.quest.finished", "クエスト「%s」を終えた", "Quest complete: %s"),
    ("message.skyabyss.quest.open_hint", "%s キーで、クエストログを開ける", "Press %s to open the quest log"),
]
for k, ja, en in UI:
    text(k, ja, en)


# ================================================================ 書き出し
def write_json(path: Path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def cond_text(c) -> str:
    """条件を設計書向けの短い日本語にする。"""
    if c is None:
        return "—"
    if isinstance(c, list):
        return "、".join(cond_text(x) for x in c)
    t = c["type"]
    if t == "quest":
        name = {MAIN: "メイン", SONG: "数え歌", SHARDS: "鯨のかけら"}.get(c["quest"], c["quest"])
        if "stage" in c:
            return f"{name}が「{c['stage']}」"
        return f"{name}が{ {'none': '未着手', 'active': '進行中', 'done': '済み', 'started': '始まっている', 'not_done': '未完'}[c['is']] }"
    if t == "lore":
        return f"伝承 {c['entry']}"
    if t == "flag":
        return f"印 {c['flag']}"
    if t == "remembers":
        return f"前のループの印 {c['flag']}"
    if t == "loop":
        return f"ループ {c['min']} 回以上"
    if t == "item":
        return f"{c['item'].split(':')[1]} ×{c.get('count', 1)}"
    if t == "affinity":
        return f"好感度 {c['min']} 以上"
    if t == "watcher":
        return "見る者に気づいた"
    if t == "not":
        return "not(" + cond_text(c["of"]) + ")"
    if t == "any":
        return "(" + " または ".join(cond_text(x) for x in c["of"]) + ")"
    return t


def act_text(acts) -> str:
    out = []
    for a in acts or []:
        t = a["type"]
        if t == "affinity":
            out.append(f"好感度{a['amount']:+d}")
        elif t == "flag":
            out.append(f"印 {a['flag']}")
        elif t == "lore":
            out.append(f"伝承 {a['entry']}")
        elif t == "quest_start":
            out.append(f"クエスト開始 {a['quest']}")
        elif t == "take":
            out.append(f"{a['item'].split(':')[1]} ×{a['count']} を受け取る")
        else:
            out.append(t)
    return "、".join(out) if out else ""


def md(s: str) -> str:
    return s.replace("\n", "<br>").replace("|", "｜")


def doc_tables() -> str:
    lines = []
    for d in DIALOGUES:
        name = JA[f"entity.skyabyss.{d.path}"]
        lines.append(f"#### {name}（`{d.role}`）の入口\n")
        lines.append("| 順 | 条件 | 始まる節 |\n| --- | --- | --- |")
        for i, e in enumerate(d.entries, 1):
            lines.append(f"| {i} | {cond_text(e.get('if'))} | `{e['node']}` |")
        lines.append("")
        lines.append(f"#### {name}のセリフ\n")
        lines.append("| 節 | 顔 | セリフ | 入ったとき | 次 / 選択肢 |\n| --- | --- | --- | --- | --- |")
        for nid, note in d.order:
            n = d.nodes[nid]
            body = md(JA[f"{d.base}.{nid}"])
            if note:
                body += f"<br>（{note}）"
            nxt = []
            if "next" in n:
                nxt.append(f"▼ `{n['next']}`")
            for o in n.get("options", []):
                s = f"「{JA[d.base + '.' + nid + '.' + o['id']]}」→ `{o['goto']}`"
                extra = []
                if "if" in o:
                    extra.append("条件: " + cond_text(o["if"]))
                if "do" in o:
                    extra.append(act_text(o["do"]))
                if extra:
                    s += "（" + "／".join(extra) + "）"
                nxt.append(md(s))
            lines.append(f"| `{nid}` | {n['face']} | {body} | {act_text(n.get('do'))} | {'<br>'.join(nxt)} |")
        lines.append("")
    for qid, data in QUESTS:
        base = f"quest.skyabyss.{qid}"
        lines.append(f"#### クエスト「{JA[base + '.title']}」（`{qid}`、{ {'main': 'メイン', 'sub': 'サブ'}[data['category']] }）\n")
        lines.append(f"- 依頼主: {JA['entity.skyabyss.' + data['giver'].split(':')[1]]}")
        if "start_when" in data:
            lines.append(f"- 自動で始まる条件: {cond_text(data['start_when'])}")
        r = data["reward"]
        rr = [f"経験値 {r['xp']}"] if r.get("xp") else []
        rr += [f"{i['item'].split(':')[1]} ×{i['count']}" for i in r.get("items", [])]
        rr += [f"好感度 {k.split(':')[1]} {v:+d}" for k, v in r.get("affinity", {}).items()]
        lines.append(f"- 終えたときの報酬: {'、'.join(rr)}（一人一回）")
        lines.append(f"- 終えたあとの書き付け: {md(JA[base + '.done'])}\n")
        lines.append("| 段階 | 書き付け | 目的 | 済ませ方 | 経験値 |\n| --- | --- | --- | --- | --- |")
        for s in data["stages"]:
            objs = []
            how = []
            for o in s["objectives"]:
                objs.append(JA[f"{base}.{s['id']}.{o['id']}"] + (f"（{o['count']}）" if o.get("count", 1) > 1 else ""))
                tg = o.get("targets") or [o.get("target")]
                if o["type"] == "talk":
                    how.append(f"talk {o['target'].split(':')[1]} の節 `{o.get('topic')}`")
                else:
                    how.append(f"{o['type']} " + ", ".join(t for t in tg if t))
            mode = "どれか一つ: " if s.get("mode") == "any" else ""
            skip = f"<br>skip_if: {cond_text(s['skip_if'])}" if "skip_if" in s else ""
            journal = md(JA[f"{base}.{s['id']}.journal"])
            lines.append(f"| `{s['id']}` | {journal} | {mode}{'<br>'.join(objs)} | "
                         f"{'<br>'.join(how)}{skip} | {s.get('xp', 0)} |")
        lines.append("")
    return "\n".join(lines)


def main():
    for d in DIALOGUES:
        write_json(DATA / "dialogue" / f"{d.path}.json", d.json())
    for qid, data in QUESTS:
        write_json(DATA / "quest" / f"{qid}.json", data)
    LANG.mkdir(parents=True, exist_ok=True)
    assert set(JA) == set(EN)
    write_json(LANG / "ja_jp.json", JA)
    write_json(LANG / "en_us.json", EN)
    if DOC.exists():
        doc = DOC.read_text(encoding="utf-8")
        pat = re.compile(r"(<!-- 生成ここから.*?-->\n).*?(<!-- 生成ここまで -->)", re.S)
        if pat.search(doc):
            doc = pat.sub(lambda m: m.group(1) + "\n" + doc_tables() + "\n" + m.group(2), doc)
            DOC.write_text(doc, encoding="utf-8")
    print(f"dialogues {len(DIALOGUES)}, quests {len(QUESTS)}, lang keys {len(JA)}")


if __name__ == "__main__":
    main()
