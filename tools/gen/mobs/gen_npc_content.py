"""NPC の会話とクエストの中身 (JSON と文) を一か所で書き、ゲームのデータと設計書の表に書き出す。

会話の構造 (節・選択肢・条件・結果) と、日本語・英語の文を同じ場所に置くことで、
キーの書き間違いや、片方の言語だけ直し忘れる、を防ぐ。

実行 (リポジトリ直下):
    python tools/gen/mobs/gen_npc_content.py
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

ROOT = Path(__file__).resolve().parents[3]
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
        if ja is not None:  # None = 文はほかの名前空間の lang にある (誓いの skyabyss_vow など)
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
# 序盤 (17 の 3 章。ループの「覚えているような」入口のあと): 野営地の帳面から宿りへ来た人に、最初の話の前で「今日あたり、空を見ていなさい」と言う。
# 影を先に見てから来た人には、見たことを察する。影を待つ間に話しかけた人には、一度だけ声をかける
st.entry("shadow0", q(MAIN, stage="follow_smoke"))
st.entry("shadow_first", all_(q(MAIN, stage="ask_storyteller"), not_(flag("met"))))
st.entry("shadow_wait", all_(q(MAIN, stage="watch_sky"), not_(flag("wait_told"))))
st.entry("first", not_(flag("met")))
# 周回 (誓いの仕組み。一度でも心臓を止めて戻った人に一度だけ): 文は assets/skyabyss_vow/lang にある
st.entry("repeat_note", all_({"type": "repeat_run"}, not_(flag("repeat_told"))))
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

# 序盤の語り (17 の 3 章、担当 4)
st.node("shadow0",
        "おや、煙を辿ってきたのかい。……見ない顔だね。\n旅の人が火にあたりに寄るのは、珍しいことじゃない。ゆっくりしておいき。",
        "Oh, you followed the smoke, did you. ...I don't know your face.\nTravelers stopping by to warm themselves is nothing unusual. Stay a while.",
        face="smile", next="shadow0_b", note="序盤: 帳面を持って宿りへ来た (メインの「煙の立つ方へ向かおう」が済む)")
st.node("shadow0_b",
        "何もないけれど、空だけは広い。\n今日あたり、空を見ていなさい。晴れた昼は、とくにね。……勧めたくは、ないんだけど。",
        "There's nothing here, but the sky is wide.\nKeep an eye on the sky today. At midday, especially, if it's clear. ...Not that I like to recommend it.",
        face="grave", options=[
            ("ok", "分かった。", "All right.", {"goto": "menu", "do": [add_aff(1)]}),
            ("why", "どうして、今日だと分かる？", "How do you know it will be today?", {"goto": "shadow0_why"}),
        ], note="序盤: 影の前の言づけ")
st.node("shadow0_why",
        "膝が痛むのさ。それと、鐘がね。朝から、風もないのに小さく鳴っているんだよ。\n……年寄りの気のせいなら、いいんだけどね。",
        "My knee aches. And the bell, too. Since morning it has been ringing softly, with no wind at all.\n...I'd be glad if it were only an old woman's fancy.",
        face="grave", options=[("ok", "覚えておく。", "I'll remember that.", {"goto": "menu", "do": [add_aff(1)]})])
st.node("shadow_first",
        "……その顔。見たんだね、昼の空を。\nここは、見た者が最初に寄る所なんだよ。……まあ、火にあたっておいき。",
        "...That face. You saw it, didn't you. The midday sky.\nThis is where those who have seen it come first. ...Well, come and warm yourself.",
        face="grave", next="shadow2", note="序盤: 影を見てから宿りへ来た (初対面)")
st.node("shadow_wait",
        "まだ、見ていないんだね。\n慌てなくていい。昼のうちは、空はどこへも行きやしないよ。",
        "You haven't seen it yet. No need to hurry.\nIn the daytime, the sky isn't going anywhere.",
        face="smile", next="menu", do=[set_flag("wait_told")], note="序盤: 影を待つ間に話しかけた (一度だけ)")

# 道すがらの見聞 (序盤の痕跡と気配。伝承が記されていて、まだ話していないもの)
SEEN = [("camp", "prologue/camp"), ("prints", "prologue/footprints"), ("steps", "prologue/footsteps"),
        ("bones", "prologue/bone_hill"), ("song", "prologue/sky_song")]


def seen_if(topic, entry):
    return all_(lore(entry), not_(flag(f"{topic}_told")))


SEEN_ANY = any_(*[seen_if(t, e) for t, e in SEEN])
# 語り部の話題の節は選択肢を 13 個までしか置けない (枠の高さ 210)。岩鯨を見た・背中の町を見た話も、この「見たもの」の節へ移した
SEEN_ST = SEEN + [("whale", "chapter1/stone_whale"), ("back", "chapter1/sky_ruins")]
# 都の閉じた戸のことも「見たもの」(伝承は要らない。一度だけ)
CAPITAL_LORE = any_(lore("prologue/capital_skywatch"), lore("prologue/capital_guild_roll"), lore("prologue/capital_graveyard"), lore("prologue/capital_gates"))
CAPITAL_ST = all_(CAPITAL_LORE, not_(flag("capital_told")))
SEEN_ST_ANY = any_(*[seen_if(t, e) for t, e in SEEN_ST], CAPITAL_ST)

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
     {"kind": "quest", "goto": "sub_offer", "if": all_(flag("song_heard"), q(SONG, "none"), q(MAIN, "started"), flag("met", WC))}),
    ("watcher_seen", "夜、遠くに誰かが立っていた。", "At night, someone was standing far off.",
     {"goto": "watcher", "if": all_(not_(lore("prologue/watcher")), watcher(1))}),
    ("watcher_ask", "夜に気をつけることはある？", "Is there anything to beware of at night?",
     {"goto": "watcher", "if": all_(not_(lore("prologue/watcher")), not_(watcher(1)), aff(10))}),
    ("custom", "この村の決まりごとを教えてほしい。", "Tell me the customs of this village.",
     {"goto": "calendar", "if": all_(not_(lore("prologue/seventh_night")), aff(20))}),
    ("book", "古い帳面を見せる。", "Show her the old notebook.",
     {"goto": "book", "if": all_(lore("prologue/the_book"), not_(flag("book_shown")))}),
    ("seen", "道すがら、気になるものを見た。", "I saw something on the way that troubled me.",
     {"goto": "seen_menu", "if": SEEN_ST_ANY}),
    ("bye", "また来る。", "I'll come again.", {"kind": "leave"}),
]
st.node("menu", "何か聞きたいことでもあるのかい。", "Is there something you want to ask?", options=MENU_OPTS,
        note="話題を選ぶ節 (条件で出る話題が変わる)")

# 都 (鐘の国。docs/design/33 の 2.2): 絶えかけの国の都。空見の一族と、最後の鐘守
st.node("capital1",
        "戸が閉じているだろう。ここは都だった。……そう呼ぶ人は、もう少ないけれどね。\n昔は「空見」と呼ばれる一族がいて、国を預かっていた。影が通れば鐘を鳴らし、見たものを帳面に書く。それだけの一族さ。",
        "Shut, yes. This was a capital once. ...Not many call it that anymore.\nThere was a line here called the Skywatchers who kept the nation. When the shadow passed they rang the bell and wrote down what they saw. That was all they were.",
        face="neutral", next="capital2", note="都の話 1 (空見の一族)")
st.node("capital2",
        "その一族は絶えた。砦は、塔だけが生きている。あたしが最後の鐘守さ。\n家は空いているよ。好きなのを使えばいい。……戸の閉まっている所は、まだ開けられない。時が来れば開くよ。",
        "That line is gone. Of the keep only the tower still lives. I am the last bell keeper.\nThe houses are empty. Use whichever you like. ...The doors that are shut cannot be opened yet. They will open when the time comes.",
        face="grave", next="menu", do=[set_flag("capital_told"), add_aff(1)], note="都の話 2 (最後の鐘守。閉じた施設)")

st.node("repeat_note", None, None, face="smile", next="menu", do=[set_flag("repeat_told")],
        note="周回 (一度でも心臓を止めて戻った人に一度だけ。文は skyabyss_vow の lang)")

# 空の影 → 鯨追い (メイン 1 段目)
st.node("shadow1",
        "空のことかい。\n……晴れた昼に、急に暗くなることがある。羊はうずくまり、鳥は黙る。雲の上を、何かが渡っていくんだよ。",
        "The sky, is it.\n...On a clear day it sometimes goes dark all at once. The sheep crouch, the birds fall silent. Something crosses above the clouds.",
        face="grave", next="shadow2", do=[set_flag("sky_asked"), start(MAIN)], note="メインを始める (空のことを聞いた印。序盤の二段を飛ばす)")
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
        "この字、どこかで見た気がするよ。……いや、気のせいだろう。\n大事にしておおき。書いておけば、忘れても残る。",
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

# 序盤の見聞 (17 の 3 章。足跡・足音・骨・歌は、ほのめかすだけ。答えは言わない)
st.node("seen_menu", "ほう。何を見たんだい。", "Oh? What did you see?", options=[
    ("camp", "野営地の跡を見た。", "I found the remains of a camp.", {"goto": "camp0", "if": seen_if("camp", "prologue/camp")}),
    ("prints", "地面に、大きなくぼみが並んでいた。", "Great hollows lay in a row in the ground.",
     {"goto": "prints0", "if": seen_if("prints", "prologue/footprints")}),
    ("steps", "夜、地面が揺れた。", "The ground shook in the night.", {"goto": "steps0", "if": seen_if("steps", "prologue/footsteps")}),
    ("bones", "丘に、巨大な骨が立っていた。", "A huge rib cage stood on a hill.", {"goto": "bones0", "if": seen_if("bones", "prologue/bone_hill")}),
    ("song", "夜空から、歌が降ってきた。", "A song came down from the night sky.", {"goto": "song0", "if": seen_if("song", "prologue/sky_song")}),
    ("whale", "空を泳ぐ岩の山を見た。", "I saw a mountain of rock swimming in the sky.",
     {"goto": "whale", "if": seen_if("whale", "chapter1/stone_whale")}),
    ("ruins", "鯨の背中に、町の跡があった。", "There were ruins of a town on the whale's back.",
     {"goto": "back", "if": seen_if("back", "chapter1/sky_ruins")}),
    ("capital", "この都は、どこも戸が閉じている。", "Every door in this city is shut.",
     {"goto": "capital1", "if": CAPITAL_ST}),
    ("cancel", "やっぱり、いい。", "Never mind.", {"goto": "menu"}),
], note="序盤: 見たものの話題 (見たものだけ並ぶ。岩鯨と背中の話、都の閉じた戸の話もここ)")
st.node("camp0",
        "あの野営地かい。……焚き火は、いつも冷えているだろう。\n誰かが使った跡だけが、あそこにはあるんだ。使うところを見た者は、いないけれどね。",
        "That camp, is it. ...The fire's always cold, isn't it.\nThere's only ever the trace of someone having used it. No one has ever seen them use it.",
        face="grave", next="@entry", do=[set_flag("camp_told"), add_aff(1)], note="序盤: 野営地の跡")
st.node("prints0",
        "足跡……かい。\n数えたかい。数えるのは、三つまでにしておおき。四つ目を探しに行った者は、帰ってこなかったそうだよ。",
        "Footprints, is it.\nDid you count them? Stop at three. They say the ones who went looking for a fourth never came back.",
        face="grave", next="@entry", do=[set_flag("prints_told"), add_aff(1)], note="序盤: 巨大な足跡")
st.node("steps0",
        "揺れたのかい。……三度、だったろう？\nそういう夜は、あたしは耳をふさいで数え歌を歌うことにしている。みっつまで歌うと、いつも静かになるんだよ。",
        "It shook, did it. ...Three times, wasn't it?\nOn nights like that, I cover my ears and sing the counting song. By the time I reach \"three,\" it has always gone quiet.",
        face="grave", next="@entry", do=[set_flag("steps_told"), add_aff(1)], note="序盤: 夜の足音")
st.node("bones0",
        "骨、かい。弧を描いて、立っていたんだね。\n死んだものを見ると、落ち着く者もいる。あたしは逆だよ。終わるものなら、始まりもあったはずだろう？",
        "Bones, is it. Standing in an arc, were they.\nSome find comfort in the sight of dead things. I'm the opposite. If a thing can end, it must have begun, mustn't it?",
        face="grave", next="@entry", do=[set_flag("bones_told"), add_aff(1)], note="序盤: 骨の丘")
st.node("song0",
        "……聞いたんだね、空の歌を。\nあの歌が降りる夜は、星がいくつか消えるんだよ。母さんは、何かが上を通るのだと言っていた。",
        "...You heard it, then. The song from the sky.\nOn nights when it comes down, some of the stars go out. My mother said something was passing overhead.",
        face="grave", next="song0_b", do=[set_flag("song_told"), add_aff(2)], note="序盤: 空の歌")
st.node("song0_b",
        "みっつ、みあげちゃだめよ、と歌うのは、見えないものを見ないようにするためだと思っていたんだ。\n今はね、見られないためかもしれないと思うよ。",
        "I always thought we sang \"three, don't you look up\" so that children wouldn't see what can't be seen.\nNow I think it may be so that we aren't seen.",
        face="grave", options=[
            ("who", "何が、通ったんだ？", "What was passing?", {"goto": "song0_c"}),
            ("ok", "……そうか。", "...I see.", {"goto": "@entry"}),
        ])
st.node("song0_c",
        "名前を呼んだら、振り向かれる気がしてね。\n……だから、あたしは呼ばないことにしているんだ。",
        "I fear that if I called its name, it would turn around.\n...So I've decided not to call it.",
        face="grave", next="@entry")

# 欠片 (メインの終わり)
st.node("fragment",
        "……戻ったんだね。\n手の中のそれ、見せておくれ。",
        "...You came back.\nShow me what's in your hand.",
        next="fragment2", note="メイン最後の段階 (欠片を手にしたあと)")
st.node("fragment2",
        "温かい……。脈を打っているよ。まるで、生きているみたいだ。\n言い伝えでは、空の扉の鍵は四つに分かれているという。ひとつは鯨が。残りは……さあ、どこだろうね。",
        "Warm... It's beating. As if it were alive.\nThe old tales say the key to the sky's door was split in four. One the whale had. The rest... who knows where.",
        face="grave", next="fragment_verse")
st.node("fragment_verse",
        "……数え歌の、ひとつめを覚えているかい。「ひとつ　ひるまにかげがくる」。\nあれは、鯨の影のことだったのかもしれないね。子どもらは、知らずに歌っていたんだ。",
        "...Do you remember the first line of the counting song? \"One, at noon the shadow comes.\"\nPerhaps it was the whale's shadow all along. The children sang it without knowing.",
        face="grave", next="fragment3", note="一章の報告: 数え歌のひとつめを取り戻す (34 の 1 章の五章への糸)")
st.node("fragment3",
        "あんたは、空へ行くんだね。……止めはしないよ。\nただ、七日目の夜だけは、気をつけるんだよ。",
        "You're going to the sky, aren't you. ...I won't stop you.\nOnly, be careful on the seventh night.",
        options=[("thanks", "ありがとう。", "Thank you.", {"do": [add_aff(3)]})],
        note="ここに入るとメインが終わる")

# ================================================================ 鯨追い
wc = Dialogue(WC, "鯨追いのイサナ", "Isana the Whale Chaser")
text("message.skyabyss.whale_chaser.struck", "「おい、よせ。火に灰が入る」", "\"Hey, quit it. You'll get ash in the fire.\"")

wc.entry("c_fragment", q(MAIN, stage="return"))
wc.entry("c_deja", all_(loop(1), remembers("met"), not_(flag("met"))))
wc.entry("c_first", not_(flag("met")))
wc.entry("c_menu")

wc.node("c_first",
        "……ん？　ああ、村の人じゃないな。\n火にあたっていくといい。俺はイサナ。鯨を待っている。空を泳ぐ、岩の鯨をな。",
        "...Hm? Ah, you're not from the village.\nWarm yourself by the fire. I'm Isana. I'm waiting for the whale. The whale of rock that swims in the sky.",
        face="smile", next="c_menu", do=[unlock("chapter1/whale_chaser")], note="初めて話しかけたとき (名乗る。伝承「鯨追いのイサナ」を記す)")
wc.node("c_deja",
        "どこかで会ったか？\n……いや、空ばかり見ていると、人の顔を忘れる。俺はイサナ。気にしないでくれ。",
        "Have we met?\n...No. When you only watch the sky, you forget faces. I'm Isana. Don't mind me.",
        face="grave", next="c_menu", do=[unlock("chapter1/whale_chaser")], note="ループ 1 回目以降、前のループで会っていた")

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
    ("shards", "岩鯨の鱗片を持ってきた。", "I brought scales of the whale.",
     {"kind": "give", "goto": "c_shards_thanks", "if": all_(q(SHARDS, stage="deliver"), item("skyabyss:stone_whale_shard", 3)),
      "do": [take("skyabyss:stone_whale_shard", 3)]}),
    ("seen", "道中で、妙なものを見た。", "I saw something strange on the way.", {"goto": "c_seen_menu", "if": SEEN_ANY}),
    ("bye", "じゃあ、また。", "See you.", {"kind": "leave"}),
]
wc.node("c_menu", "なにか用か。", "Need something?", options=C_MENU, note="話題を選ぶ節")

# 序盤の見聞 (17 の 3 章)。イサナは、見たことを短く話す。足跡は余燼、骨の主は決めない (17 の判断 17-3・17-4)
wc.node("c_seen_menu", "妙なもの？　聞かせろ。", "Strange? Tell me.", options=[
    ("camp", "野営地の跡を見た。", "I found the remains of a camp.", {"goto": "c_camp", "if": seen_if("camp", "prologue/camp")}),
    ("prints", "地面に、大きなくぼみが並んでいた。", "Great hollows lay in a row in the ground.",
     {"goto": "c_prints", "if": seen_if("prints", "prologue/footprints")}),
    ("steps", "夜、地面が揺れた。", "The ground shook in the night.", {"goto": "c_steps", "if": seen_if("steps", "prologue/footsteps")}),
    ("bones", "丘に、巨大な骨が立っていた。", "A huge rib cage stood on a hill.", {"goto": "c_bones", "if": seen_if("bones", "prologue/bone_hill")}),
    ("song", "夜空から、歌が降ってきた。", "A song came down from the night sky.", {"goto": "c_song", "if": seen_if("song", "prologue/sky_song")}),
    ("back", "やっぱり、いい。", "Never mind.", {"goto": "c_menu"}),
], note="序盤: 見たものの話題 (見たものだけ並ぶ)")
wc.node("c_camp",
        "あの野営地か。俺が寄ったときも、誰もいなかった。\n火は冷えているのに、寝袋だけが、いま起きたばかりみたいに温かかった。",
        "That camp. When I stopped there, no one was around either.\nThe fire was cold, but the bedroll was warm, as if someone had only just got up.",
        face="grave", next="@entry", do=[set_flag("camp_told"), add_aff(1)], note="序盤: 野営地の跡")
wc.node("c_prints",
        "三つ並んでいただろう。俺も、その先を探した。\n何も見つからなかった。……見つからないのが、いちばん気味が悪い。",
        "Three in a row, weren't there. I looked beyond them too.\nI found nothing. ...Finding nothing is the worst part.",
        face="grave", next="@entry", do=[set_flag("prints_told"), add_aff(1)], note="序盤: 巨大な足跡")
wc.node("c_steps",
        "ゆうべか。火のそばの湯が、三度、ゆっくり揺れていた。\n……鯨じゃない。鯨は、歩かない。",
        "Last night. The water by the fire shook three times, slowly.\n...It wasn't the whale. Whales don't walk.",
        face="grave", next="@entry", do=[set_flag("steps_told"), add_aff(1)], note="序盤: 夜の足音")
wc.node("c_bones",
        "あの石板か。……俺じゃないぞ。字が、俺のじゃない。\n歌が止まる、か。俺は長いこと歌を追ってきたが、止まったと思ったことは一度もない。",
        "That slab. ...It wasn't me. The hand isn't mine.\nThe song stopped, it says. I've chased that song a long time, and never once thought it had stopped.",
        face="grave", next="@entry", do=[set_flag("bones_told"), add_aff(1)], note="序盤: 骨の丘")
wc.node("c_song",
        "聞いたか、ゆうべの歌。あれが、俺の追っている歌だ。\nだが、いつもより近かった。……近すぎる、と思った。",
        "You heard it, last night's song. That's the one I chase.\nBut it was closer than ever. ...Too close, I thought.",
        face="grave", next="c_song2", do=[set_flag("song_told"), add_aff(2)], note="序盤: 空の歌")
wc.node("c_song2",
        "歌が近いのは、鯨が近いということだ。\n支度ができたら、空をよく見ておけ。覚悟もな。俺も、してはいるんだ。",
        "A close song means a close whale.\nOnce you're ready, keep your eyes on the sky. Brace yourself, too. I'm trying to myself.",
        next="@entry")

wc.node("c_whale1",
        "俺は、もう何年も鯨を追っている。\n影の年に、遠い空から聞こえる低い歌。あれの主が、岩の鯨だ。背中には町があると、昔、誰かに聞いた。",
        "I've been chasing the whale for years now.\nThat low song from the far sky in the shadow years... its singer is a whale of rock. Someone told me long ago there's a town on its back.",
        next="c_whale2", do=[set_flag("c_whale_heard")])
wc.node("c_whale2",
        "ときどき雲の下まで降りてきて、地面に届くほど長い鎖を垂らすんだ。\n俺は……一度も、つかめたことがない。降りてくるのは、いつも遠くの誰かの上なんだ。",
        "Sometimes it comes down below the clouds and lets down a chain long enough to reach the ground.\nI've... never once caught it. It always comes down over someone far away.",
        face="grave", next="c_whale_iron", note="ここに入るとメインの「イサナに話を聞く」が済む")
wc.node("c_whale_iron",
        "鎖は、素手では上れない。……前にここへ来た旅人は、鉄を着ていた。\n都の北門から丘を越えていく石標が、その人の道だ。行き着く先は、骨の坑と呼ばれている。",
        "You cannot climb the chain bare-handed. ...The traveller who came here before wore iron.\nThe cairns from the north gate over the hills are that one's road. Where they end is called the Bone Pit.",
        next="c_whale3", note="一章の支度の段「鉄の跡」(ch1/trail_iron) の理由。この話のあと、支度の段 3 本が出る (34 の 4.2)")
wc.node("c_whale3",
        "鯨が降りてくる前に、支度をしておけ。まずは鉄だ。石標を辿って、骨の坑へ行け。\n刃のことは旅の職人が、盾のことは語り部が知っている。……支度ができたら、あんたの上に来るかもしれないな。",
        "Get ready before the whale comes down. Iron first. Follow the cairns to the Bone Pit.\nThe traveling artisan knows about blades, the storyteller about shields. ...Once you're ready, maybe it'll come over you.",
        options=[
            ("grab", "支度をしてくる。", "I'll go and get ready.", {"do": [add_aff(2)]}),
            ("afraid", "怖くはないのか？", "Aren't you afraid?", {"goto": "c_afraid"}),
        ])
wc.node("c_afraid",
        "怖いさ。\n……でも、あれを見上げていると、呼ばれている気がするんだ。行かなきゃならない場所があるみたいに。",
        "Of course I am.\n...But when I look up at it, it feels like I'm being called. As if there's somewhere I have to go.",
        face="grave", next="@entry")
wc.node("c_when",
        "歌が聞こえたら、空を見ろ。鯨が歌うのは、降りてくる前だ。\nそれまでに、支度を済ませておけ。……鯨は、人のいる所に降りてくる。まるで、誰かを迎えに来るみたいにな。",
        "When you hear the song, look at the sky. The whale sings before it comes down.\nHave yourself ready by then. ...It comes down where there are people. As if it's come to fetch someone.",
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
        "頼みがある。……背中の鱗片を、少し持ってきてくれないか。\n鯨の体のかけらでいい。三つあれば足りる。触れてみたいんだ。俺がたどり着けなかった場所の、手ざわりを。",
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
        options=[("hand", "あの頁の字のことだけど……", "About the writing on that page...", {"if": lore("ch1/trail_iron"), "goto": "c_fragment_hand"}),
                 ("bye", "じゃあ、また。", "See you.", {"kind": "leave"})], note="ここに入るとメインが終わる")
wc.node("c_fragment_hand",
        "坑の荷の頁か。……俺も、気になっていた。\nあの字、鯨骨の船長の日誌と同じ字に見えた。……いや、そんなはずはない。何百年も前の日誌だ。",
        "The page from the pack in the pit. ...It has been on my mind too.\nThat hand looked like the writing in the whalebone captain's log. ...No, it cannot be. That log is centuries old.",
        face="grave", options=[("bye", "じゃあ、また。", "See you.", {"kind": "leave"})], note="報告のほのめかし (34 の 4.2。前の私の頁を見つけた人だけ)")

DIALOGUES = [st, wc]


# ================================================================ クエスト
QUESTS = []


# クエストの章 (QuestLog の章の札。一章 titan_whale)
CHAPTER = {MAIN: "titan_whale"}


def quest(qid, category, order, giver, title, summary, done, stages, reward, start_when=None):
    data = {"category": category}
    if qid in CHAPTER:
        data["chapter"] = CHAPTER[qid]
    data["order"] = order
    data["giver"] = giver
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
        # lead: 段の書き出しの一言 (段に入ったとき字幕に一度。誰が頼み、何のためか。client/quest/QuestClient.leadKey)
        if s.get("lead"):
            text(f"{base}.{s['id']}.lead", *s["lead"])
        for o in s["objectives"]:
            oj = {k: v for k, v in o.items() if k not in ("ja", "en", "sub")}
            sj["objectives"].append(oj)
            text(f"{base}.{s['id']}.{o['id']}", o["ja"], o["en"])
            # sub: 状況で Java が差し替える目的の文 (鎖待ちなど。QuestObjectives)
            for suffix, (sja, sen) in (o.get("sub") or {}).items():
                text(f"{base}.{s['id']}.{o['id']}.{suffix}", sja, sen)
        data["stages"].append(sj)
    data["reward"] = reward
    QUESTS.append((qid, data))


quest(MAIN, "main", 10, ST,
      ("雲の下の歌", "The Song Beneath the Clouds"),
      ("煙の下の宿りへ向かい、空の影の行く先を追う。", "Follow the smoke to the lodge, and trace where the shadow in the sky leads."),
      ("欠片は、ほのかに温かく脈を打っている。残る三つは、ほかの巨神が体の奥に抱えているはずだ。",
       "The fragment is faintly warm, and it beats. The other three must be held deep inside the other titans."),
      [
          # 序盤 (17 の 3 章): 野営地の帳面を手にして始まり、煙の下の宿りへ、そして昼の空へ。
          # 先に影を見た人・先に岩鯨を見た人は、この二段を飛ばして「語り部に尋ねる」から
          {"id": "follow_smoke", "xp": 20,
           "skip_if": any_(lore("prologue/shadow"), lore("chapter1/stone_whale"), flag("sky_asked", ST)),
           "journal": ("野営地に残されていたのは、一冊の帳面だけだった。\n遠くの空に、細い煙が立っている。ときおり、鐘の音も風に乗ってくる。",
                       "All that was left at the camp was a single notebook.\nFar off, a thin column of smoke rises. Now and then a bell is carried on the wind."),
           "objectives": [{"id": "reach", "type": "talk", "target": ST, "topic": "shadow0",
                           "ja": "煙の立つ方へ向かおう", "en": "Head toward the rising smoke"}]},
          {"id": "watch_sky", "xp": 20,
           "skip_if": any_(lore("prologue/shadow"), lore("chapter1/stone_whale"), flag("sky_asked", ST)),
           "journal": ("鐘の宿りの語り部は、今日あたり空を見ていなさい、と言った。\n鐘は朝から、風もないのに小さく鳴っているという。",
                       "The storyteller at the Bell Lodge told me to keep an eye on the sky today.\nThe bell has been ringing softly since morning, she said, though there is no wind."),
           "objectives": [{"id": "look", "type": "lore", "targets": ["prologue/shadow"],
                           "ja": "晴れた昼に、空を見上げてみよう", "en": "Look up at the sky on a clear midday"}]},
          {"id": "ask_storyteller", "xp": 30,
           "skip_if": lore("chapter1/stone_whale"),
           "journal": ("晴れた昼に、空が暗くなった。雲の上を、何かが渡っていった。\n語り部なら、何か知っているかもしれない。",
                       "On a clear day, the sky went dark. Something crossed above the clouds.\nThe storyteller might know something."),
           "objectives": [{"id": "talk", "type": "talk", "target": ST, "topic": "shadow3",
                           "ja": "語り部に、空のことを尋ねよう", "en": "Ask the storyteller about the sky"}]},
          {"id": "find_chaser", "xp": 30,
           "skip_if": lore("chapter1/stone_whale"),
           "journal": ("影が通った年には、決まって遠い空から“歌”が聞こえるという。\nその歌を追う旅人が、少し離れた所で火を焚いている。煙が目印だ。",
                       "In the years the shadow passes, a \"song\" is always heard from the far sky.\nA traveler who chases that song keeps a fire a little way off. Follow the smoke."),
           "lead": ("語り部によると、空の歌を追う旅人が、村の外れで火を焚いているという。",
                    "The storyteller says a traveler who chases the sky's song keeps a fire at the edge of the village."),
           "objectives": [{"id": "talk", "type": "talk", "target": WC, "topic": "c_whale2",
                           "ja": "火を焚くイサナに、話を聞きに行こう", "en": "Talk to the Whale Chaser by the fire"}]},
          # 一章の支度 (34 の 4.2。2026-10-22): 鯨追いの話のあと、支度の段 3 本 (鉄の跡 → 折れた剣 → 盾と糧) を順に。
          # 三本そろうまで岩鯨は現れない (titan.TitanEvents・story.ch1.Ch1Trails.callReady)。帯は支度の段のほうを出す (ObjectiveStrip.current)
          {"id": "prepare",
           "skip_if": lore("chapter1/stone_whale"),
           "journal": ("イサナは言った。鎖は素手では上れない、と。前にここへ来た旅人は、鉄を着て、研いだ刃と盾を持っていたらしい。\n鯨が降りてくる前に、同じ支度をしておこう。鉄はイサナ、刃は旅の職人、盾は語り部が知っている。",
                       "Isana said the chain cannot be climbed bare-handed. The traveller who came before wore iron, and carried a honed blade and a shield.\nGet ready the same way before the whale comes down. Isana knows about the iron, the artisan about the blade, the storyteller about the shield."),
           "lead": ("イサナ「鯨が来る前に、支度をしておけ。鉄と、研いだ刃と、盾だ」",
                    "Isana: \"Get ready before the whale comes. Iron, a honed blade, and a shield.\""),
           "objectives": [{"id": "ready", "type": "condition", "if": {"type": "prep", "chapter": 1},
                           "ja": "鉄と刃と盾で、鯨を迎える支度をしよう", "en": "Ready iron, blade and shield"}]},
          {"id": "see_whale", "xp": 60,
           "journal": ("支度はととのった。鉄を着て、研いだ刃と盾を持っている。前の旅人と同じ身なりだ。\n歌の主は、空を泳ぐ岩の鯨だという。ときどき雲の下まで降りてきて、鎖を垂らす。歌が聞こえたら空を見ろ、とイサナは言っていた。",
                       "You are ready: iron on your back, a honed blade, a shield. Dressed like the traveller who came before.\nThe singer is said to be a whale of rock that swims in the sky. Sometimes it comes below the clouds and lets down a chain. When you hear the song, look at the sky, Isana said."),
           "lead": ("支度はととのった。あとは、イサナの言う岩鯨を待つだけだ。",
                    "You are ready. All that is left is to wait for Isana's whale."),
           "objectives": [{"id": "see", "type": "lore", "targets": ["chapter1/stone_whale"],
                           "ja": "歌に耳をすまし、空を泳ぐ岩鯨を探そう", "en": "Listen for the song; look for the whale",
                           "sub": {"wait": ("空を見上げて、岩鯨を待とう", "Look up and wait for the stone whale")}}]},
          {"id": "board", "xp": 60,
           "journal": ("岩鯨が、すぐそこにいる。鎖をつかめば背中へ上がれる、とイサナは言っていた。\nイサナが一度もたどり着けなかった場所だ。",
                       "The stone whale is right there. Isana said the chain would take you up onto its back.\nIt is the place Isana never once reached."),
           "lead": ("鎖が届けば、背中へ上がれる。イサナが一度もたどり着けなかった場所だ。",
                    "If the chain reaches you, you can climb to its back. The place Isana never once reached."),
           "objectives": [{"id": "ride", "type": "lore", "targets": ["chapter1/sky_ruins"],
                           "ja": "鎖をのぼって、岩鯨の背中へ渡ろう", "en": "Climb the chain to its back",
                           "sub": {"wait": ("空の岩鯨を、追いかけよう", "Follow the stone whale")}}]},
          {"id": "inscriptions", "xp": 90,
           # 碑文は岩鯨のレイドの部屋 1〜3 を越えると記される (TrialRoute.WHALE_ROOM_INSCRIPTIONS)。
           # 読み逃しても心臓を止めれば飛ぶ (潮殻・雷翼と同じ形。fun_review P1-1)
           "skip_if": {"type": "stilled", "titan": "stone_whale"},
           "journal": ("背中には、崩れた町があった。背の奥へ、三つの間が続いている。\n間を越えるごとに、背中の民の碑文がひとつ見つかるはずだ。",
                       "On its back lay a ruined town. Three chambers lead deeper into the back.\nBeyond each one, the people of the back must have left an inscription."),
           "lead": ("背中には、崩れた町があった。ここに誰がいたのか、碑文が語るかもしれない。",
                    "A ruined town lies on its back. The inscriptions may tell who lived here."),
           "objectives": [{"id": "read", "type": "lore", "count": 3,
                           "targets": ["chapter1/inscription_west", "chapter1/inscription_east", "chapter1/inscription_below"],
                           "ja": "三つの間を越えて、背中の碑文を読もう", "en": "Cross the three chambers and read the inscriptions"}]},
          {"id": "heart",
           "journal": ("三つの間の先で、何かが脈を打っている。\n鼓動のそばに、何かが納められているという。",
                       "Past the three chambers, something is beating.\nSomething, they say, was laid to rest beside the heartbeat."),
           "lead": ("三つの間の先で、何かが脈を打っている。",
                    "Past the three chambers, something beats."),
           "objectives": [
               {"id": "still", "type": "lore", "targets": ["chapter1/heart_stilled"],
                "ja": "体の奥で脈打つものを、確かめに行こう", "en": "Find what beats deep inside the body"},
               {"id": "fragment", "type": "lore", "targets": ["chapter1/sky_key"],
                "ja": "鼓動のそばに納められたものを、手に取ろう", "en": "Take what was laid beside the heartbeat"}]},
          {"id": "return", "mode": "any",
           "journal": ("欠片は、ほのかに温かい。\n村へ戻って、このことを話そう。",
                       "The fragment is faintly warm.\nReturn to the village and tell them."),
           "lead": ("手の中の欠片が、ほのかに温かい。語り部とイサナに知らせよう。",
                    "The fragment in your hand is faintly warm. Tell the storyteller and Isana."),
           "objectives": [
               {"id": "storyteller", "type": "talk", "target": ST, "topic": "fragment3",
                "ja": "語り部に、欠片を見せに行こう", "en": "Show the fragment to the storyteller"},
               {"id": "chaser", "type": "talk", "target": WC, "topic": "c_fragment2",
                "ja": "イサナに、欠片を見せに行こう", "en": "Go and show the fragment to Isana"}]},
      ],
      {"xp": 200, "affinity": {ST: 5, WC: 5}},
      start_when=any_(lore("prologue/the_book"), lore("prologue/shadow"), lore("chapter1/stone_whale")))

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
      ("鯨追いのイサナのために、岩鯨の背から鱗片を持ち帰る。", "Bring back Stone Whale Scales from the whale's back for Isana."),
      ("イサナは、石を火にかざして、長いあいだ黙っていた。", "Isana held the stones to the fire and was silent for a long time."),
      [
          {"id": "gather",
           "journal": ("イサナは、たどり着けなかった場所の手ざわりを知りたいという。\n背中の番兵は、ときどき岩鯨の鱗片を落とす。",
                       "Isana wants to feel the place he never reached.\nThe guards on the back sometimes drop shards of the stone whale."),
           "objectives": [{"id": "shards", "type": "collect", "target": "skyabyss:stone_whale_shard", "count": 3,
                           "ja": "岩鯨の鱗片を集める", "en": "Collect stone whale scales"}]},
          {"id": "deliver",
           "journal": ("鱗片が三つそろった。\nイサナの火のところへ持っていこう。",
                       "You have three scales.\nTake them to Isana's fire."),
           "objectives": [{"id": "give", "type": "talk", "target": WC, "topic": "c_shards_thanks",
                           "ja": "イサナに、鱗片を三つ渡す", "en": "Give Isana three scales"}]},
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
    ("gui.skyabyss.quest.empty", "何も始まっていない", "Nothing begun yet"),
    ("gui.skyabyss.quest.section.main", "メイン", "Main"),
    ("gui.skyabyss.quest.section.sub", "サブ", "Side"),
    ("gui.skyabyss.quest.section.done", "終えたもの", "Finished"),
    ("gui.skyabyss.quest.giver", "依頼: %s", "From: %s"),
    ("gui.skyabyss.quest.objectives", "やること", "To do"),
    ("gui.skyabyss.quest.objectives_any", "やること（どれか一つ）", "To do (any one)"),
    ("gui.skyabyss.quest.progress", "%s / %s 段目", "Step %s of %s"),
    ("gui.skyabyss.quest.finished", "このクエストは終えた。", "You have finished this quest."),
    # 終えたクエストの頁 (クエストログ): やったこと・受け取った報酬・日
    ("gui.skyabyss.quest.done_steps", "やったこと", "What you did"),
    ("gui.skyabyss.quest.reward_got", "受け取った報酬: %s", "Received: %s"),
    ("gui.skyabyss.quest.days", "%s 日目に受け、%s 日目に終えた", "Taken on day %s, finished on day %s"),
    ("gui.skyabyss.quest.day_done", "%s 日目に終えた", "Finished on day %s"),
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
        if d.path == "whale_chaser":
            lines.append("### 9.2 イサナ\n")
        lines.append(f"**{name}（`{d.role}`）の入口**\n")
        lines.append("| 順 | 条件 | 始まる節 |\n| --- | --- | --- |")
        for i, e in enumerate(d.entries, 1):
            lines.append(f"| {i} | {cond_text(e.get('if'))} | `{e['node']}` |")
        lines.append("")
        lines.append(f"**{name}のセリフ**\n")
        lines.append("| 節 | 顔 | セリフ | 入ったとき | 次 / 選択肢 |\n| --- | --- | --- | --- | --- |")
        for nid, note in d.order:
            n = d.nodes[nid]
            body = md(JA[f"{d.base}.{nid}"]) if f"{d.base}.{nid}" in JA else "（文は `skyabyss_vow` の lang）"
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
    lines.append("### 9.3 メイン「雲の下の歌」とサブ 2 本\n")
    for qid, data in QUESTS:
        base = f"quest.skyabyss.{qid}"
        lines.append(f"**クエスト「{JA[base + '.title']}」（`{qid}`、{ {'main': 'メイン', 'sub': 'サブ'}[data['category']] }）**\n")
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
                ot = JA[f"{base}.{s['id']}.{o['id']}"] + (f"（{o['count']}）" if o.get("count", 1) > 1 else "")
                for suffix, (sja, _) in (o.get("sub") or {}).items():
                    ot += f"（状況で「{sja}」。`{s['id']}.{o['id']}.{suffix}`）"
                objs.append(ot)
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
    # 同じ lang に tools/gen/items/gen_artisan_dialogue.py が足すキー (旅の職人) は、ここでは持たず、あるものを後ろに残す
    for fn, mine in (("ja_jp.json", JA), ("en_us.json", EN)):
        path = LANG / fn
        kept = {}
        if path.exists():
            for k, v in json.loads(path.read_text(encoding="utf-8")).items():
                if k not in mine and (".artisan" in k or k == "message.skyabyss.heartbeat_ward.remade"):
                    kept[k] = v
        write_json(path, {**mine, **kept})
    if DOC.exists():
        doc = DOC.read_text(encoding="utf-8")
        pat = re.compile(r"(<!-- 生成ここから.*?-->\n).*?(<!-- 生成ここまで -->)", re.S)
        if pat.search(doc):
            doc = pat.sub(lambda m: m.group(1) + "\n" + doc_tables() + "\n" + m.group(2), doc)
            DOC.write_text(doc, encoding="utf-8")
    print(f"dialogues {len(DIALOGUES)}, quests {len(QUESTS)}, lang keys {len(JA)}")


if __name__ == "__main__":
    main()
