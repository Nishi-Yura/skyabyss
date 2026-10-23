"""四章「足もとから来るもの」(余燼。docs/design/13 の 17 章) の中身の生成元。P4d。

書くもの (どれも生成物。直すときはここを直して実行する):
  src/main/resources/data/skyabyss/opening/ember_{hearth_ruin,ash_post,warm_ledge}.json   小要素の部品 (ChapterSites の Blueprint)
  src/main/resources/data/skyabyss/lore/entry/chapter1/{ember_hearth,ember_prev_page}.json 伝承 2 項目
  src/main/resources/data/skyabyss/quest/ch4/fragments.json                                 メイン「ひとつだった鍵」(語り部とイサナ)
  src/main/resources/data/skyabyss/dialogue/ch4/{storyteller,whale_chaser}_ch4.json         会話の追加ファイル ("extends": true、節は ch4_)
  src/main/resources/assets/skyabyss_ch4/lang/{ja_jp,en_us}.json                            四章の文 (語り・クエスト・会話・余韻)
  src/main/resources/assets/skyabyss_ember/lang/{ja_jp,en_us}.json の「__ember.H」の下      伝承 2 項目の文 (LoreSelfTest が読む lang に置く)

実行 (リポジトリ直下):  python tools/gen/ch4/gen_ch4_content.py
"""
from __future__ import annotations

import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
RES = ROOT / "src/main/resources"

JA: dict[str, str] = {}
EN: dict[str, str] = {}


def text(key: str, ja: str, en: str) -> None:
    assert key not in JA, key
    JA[key] = ja
    EN[key] = en


def dump(path: Path, obj) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(obj, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


# ---------------------------------------------------------------- 小要素の部品
def blueprint(name, palette, blocks, marks, rect, level, ring=2):
    cells = [[x, y, z, palette.index(b)] for (x, y, z, b) in blocks]
    obj = {"site": name, "palette": palette, "blocks": cells, "marks": marks,
           "meta": {"rect": rect, "level": level, "ring": ring}}
    path = RES / "data/skyabyss/opening" / f"{name}.json"
    path.write_text(json.dumps(obj, ensure_ascii=False, separators=(",", ":")), encoding="utf-8")


def write_blueprints() -> None:
    # 灰の民の炉跡: 焼き締めた黒石の縁、火の消えた焚き口、欠けた金床、樽 (前の私の頁)
    BS, CR, BA, FIRE, ANVIL, BARREL, WALL = (
        "minecraft:blackstone", "minecraft:cracked_polished_blackstone_bricks", "minecraft:smooth_basalt",
        "minecraft:campfire[lit=false]", "minecraft:chipped_anvil[facing=north]", "minecraft:barrel[facing=up]",
        "minecraft:polished_blackstone_brick_wall")
    pal = [BS, CR, BA, FIRE, ANVIL, BARREL, WALL]
    blocks = []
    for i in range(-2, 3):
        for z in (-2, 2):
            blocks.append((i, 0, z, CR if (i + z) % 2 == 0 else BS))
    for z in (-1, 0, 1):
        for x in (-2, 2):
            blocks.append((x, 0, z, CR if z == 0 else BS))
    blocks += [(0, 0, 0, FIRE), (1, 0, -1, ANVIL), (-1, 0, 1, BARREL), (-1, 0, -1, BA), (1, 0, 1, BA)]
    blocks += [(-2, 1, -2, WALL), (2, 1, -2, WALL), (-2, 1, 2, WALL), (-2, 2, -2, WALL), (2, 1, 0, WALL)]
    blueprint("ember_hearth_ruin", pal, blocks, {"barrel": [-1, 0, 1], "stand": [0, 0, 3]}, [-2, -2, 2, 3], True)

    # 灰降り観測点: 一本の杭と、足もとの低い石
    SL, POST, TOP = "minecraft:polished_blackstone_slab[type=bottom]", "minecraft:polished_blackstone_brick_wall", "minecraft:blackstone_slab[type=bottom]"
    pal = [SL, POST, TOP, BA]
    blocks = [(0, 0, 0, BA), (0, 1, 0, POST), (0, 2, 0, POST), (0, 3, 0, TOP),
              (1, 0, 0, SL), (-1, 0, 0, SL), (0, 0, 1, SL), (0, 0, -1, SL), (2, -1, 0, BA)]
    # 立つ所は杭の東 (足もとは玄武岩。ならさない部品なので、足もとを部品で置く)
    blueprint("ember_ash_post", pal, blocks, {"top": [0, 3, 0], "stand": [2, 0, 0]}, [-1, -1, 1, 1], False, 1)

    # 温かい岩棚: 三かける三の玄武岩の台と、樽 (温石が一つ)
    BASALT = "minecraft:basalt[axis=y]"
    pal = [BA, BASALT, BARREL, TOP]
    blocks = []
    for x in range(-1, 2):
        for z in range(-1, 2):
            blocks.append((x, -1, z, BASALT if (x + z) % 2 else BA))
    blocks += [(0, 0, 0, BARREL), (-1, 0, -1, TOP), (1, 0, -1, TOP)]
    # 台は地面の高さ (y=-1 で地面の一番上を置き換える。地面から浮いた台にしない)。樽はその上
    blueprint("ember_warm_ledge", pal, blocks, {"barrel": [0, 0, 0], "stand": [0, 0, 2]}, [-2, -2, 2, 2], True, 2)


# ---------------------------------------------------------------- 文
def write_text() -> None:
    p = "message.skyabyss.ember_ch4."
    # 予兆 3 拍
    text(p + "beat.ground", "足もとの土が、ほんのりと温かい。霜の降りていた所だけ、円く消えている。",
         "The soil underfoot is faintly warm. Only where the frost lay, a round patch has vanished.")
    text(p + "beat.hum", "桶の水が、ぬるい。……地の底で、低いものが、ひとつ鳴った。",
         "The water in the pail is lukewarm. ... Far below, something low sounded once.")
    text(p + "beat.ridge", "遠くの丘の煙が、昨日と違う所から上がっている。",
         "The smoke of the far hill rises from a different place than yesterday.")
    # 小要素
    text(p + "site.hearth", "崩れた炉の跡。灰は、手のかたちに掻き寄せてある。",
         "The ruin of a hearth. The ash has been raked together in the shape of a hand.")
    text(p + "site.post", "杭の目盛りに、灰が薄く積もっている。昨日より、ほんの少し高い。",
         "Ash lies thin along the marks of the post. A little higher than yesterday.")
    text(p + "site.ledge", "岩棚の石だけが、手を当てたように温かい。",
         "Only the stones of this ledge are warm, as if a hand had rested on them.")
    text(p + "warm_base", "寝床のまわりだけ、地面が温かい。", "Only the ground around your bed is warm.")
    # 中盤の出来事
    text(p + "rise", "丘が、持ち上がった。……山だと思っていたものが、息をしている。",
         "The hill lifted. ... What you took for a mountain is breathing.")
    # 余韻
    text(p + "aftermath.teller", "……よく戻ったね。その鍵は、あんたが持っておいき。上ばかり見ていたのは、あたしたちのほうだったよ。",
         "... Welcome back. Keep that key. It was we who kept looking only upward.")
    text(p + "aftermath.end", "足の下の、ずっと深いところで、鍵と同じ拍が、ひとつ打った。",
         "Far beneath your feet, a beat the same as the key's sounded once.")
    text("chapter.skyabyss.aftermath.titan_ember", "灰が止んだ。拍が、ひとつだけ遅れて鳴った。",
         "The ash has stopped. One beat sounded, a little late.")

    # クエスト「ひとつだった鍵」
    q = "quest.skyabyss.ch4.fragments."
    text(q + "title", "ひとつだった鍵", "The Key That Was One")
    text(q + "summary", "語り部とイサナから、鍵の話をそれぞれに聞く。", "Hear about the key from the storyteller and from Isana, each in their own way.")
    text(q + "done", "二人の話は、同じ鍵の、別の端を持っていた。", "Their two tales held different ends of the same key.")
    text(q + "teller.journal", "灰の朝が続く。語り部が、鍵のことで話したそうにしている。", "The ash mornings go on. The storyteller seems to want to talk about the key.")
    text(q + "teller.talk", "語り部の話を聞こう", "Hear the storyteller out")
    text(q + "isana.journal", "イサナも、鍵のことを話したがっている。", "Isana, too, wants to speak of the key.")
    text(q + "isana.talk", "イサナの話を聞こう", "Hear Isana out")

    # 会話 (語り部)
    s = "dialogue.skyabyss.storyteller."
    text(s + "ch4_s_one", "……灰が、また一段降りたね。\n昔、祖母が言っていたよ。鍵は、もともと一つだったって。",
         "... The ash has come down another step. My grandmother used to say the key was one, long ago.")
    text(s + "ch4_s_two", "四つに割れたんじゃない。四つの場所へ、預けたんだと。\n割れたものなら、集めれば元に戻る。預けたものは……返しに行くものだろう？",
         "Not broken in four, she said. Entrusted to four places. A broken thing mends when gathered. A thing entrusted... one goes to return it, no?")
    text(s + "ch4_s_three", "……おかしな話だね。ただの言い伝えだよ。\n四つ持っていくなら、せめて、落とさないでおくれ。",
         "... A strange tale. Only an old saying. If you carry all four, at least do not drop them.")
    text(s + "ch4_s_three.ok", "預かっておく。", "I'll keep them safe.")
    # 会話 (イサナ)
    i = "dialogue.skyabyss.whale_chaser."
    text(i + "ch4_i_one", "俺の見方は、語り部とは違う。\n鍵ってのは、持つ者が選ぶ道具だと思ってきた。",
         "I see it differently from the storyteller. I always thought a key was a tool for its bearer to choose with.")
    text(i + "ch4_i_two", "四つ集めりゃ、空の扉が開く。そう信じて、鯨を追ってきた。\nだが……集めるたびに、足が重くなる。鍵の重さじゃない。",
         "Gather four and the door in the sky opens. I chased the whales believing that. But each one I gathered made my feet heavier. Not the key's weight.")
    text(i + "ch4_i_three", "四つ目を手にしたら、わかる気がするんだ。\n何を開けるのかじゃなく、何を持たされているのか、をな。",
         "When you hold the fourth, I think you will know. Not what it opens, but what you were made to carry.")
    text(i + "ch4_i_three.ok", "そうかもしれない。", "Perhaps.")

    text("item.skyabyss.lore_page.desc.ember_hearth", "灰の民の炉跡で拾った。見覚えのある字で、短く書いてある",
         "Found at the ash people's hearth. A few lines in a hand you almost know.")
    # 会話: イサナは主の選択肢 c_menu に足す。語り部の主の選択肢は 13 個の上限いっぱいなので、入口に足す (ほかの入口が先に取る条件つき)
    text("dialogue.skyabyss.whale_chaser.c_menu.ch4", "鍵のことを、どう思ってる？", "What do you make of the key?")

    # 伝承 2 項目 (文は skyabyss_ember の lang へ)
    text("lore.skyabyss.chapter1.ember_hearth.title", "灰の民の炉跡", "A Hearth of the Ash People")
    text("lore.skyabyss.chapter1.ember_hearth.body",
         "地面に、焼き締めた石で小さな炉が組んである。灰は、手のかたちに掻き寄せられていた。\n\n炉の口は、いつも丘のほうを向いている。火を絶やさないためではなく、丘を起こさないために焚いていたのだろう。",
         "A small hearth of fired stone is set into the ground. The ash had been raked together in the shape of a hand.\n\nIts mouth always faces the hill. They kept it lit not so the fire would live, but so the hill would not wake.")
    text("lore.skyabyss.chapter1.ember_prev_page.title", "炉跡の頁", "A Page from the Hearth")
    text("lore.skyabyss.chapter1.ember_prev_page.body",
         "破れた頁。見覚えのある字で、短い覚え書きが残っている。\n\n「足の下が温かい日は、丘を見ない。見ると、目が合う気がするから」\n\n……この字は、自分のものに似ている。",
         "A torn page. A short note remains in a hand that looks familiar.\n\n\"On days when the ground is warm, I do not look at the hill. I feel it would meet my eyes.\"\n\n... This handwriting resembles your own.")



def write_lang() -> None:
    ember = {k: v for k, v in JA.items() if k.startswith("lore.skyabyss.chapter1.")}
    ember_en = {k: EN[k] for k in ember}
    for code, texts, only in (("ja_jp", JA, ember), ("en_us", EN, ember_en)):
        # 四章の lang (伝承以外)
        # 自分の持たないキー (四章の地上 tools/gen/ch4/gen_ch4_ground.py の分) は残す
        path4 = RES / f"assets/skyabyss_ch4/lang/{code}.json"
        merged = json.loads(path4.read_text(encoding="utf-8")) if path4.exists() else {}
        merged.update({k: v for k, v in texts.items() if k not in only})
        dump(path4, dict(sorted(merged.items())))
        # 余燼の lang の「__ember.H」の行と伝承の行だけを書き直す (ほかの行は文字のまま触らない。H のあとにほかの作業の行が続いてよい)
        path = RES / f"assets/skyabyss_ember/lang/{code}.json"
        lines = path.read_text(encoding="utf-8").rstrip().split(chr(10))
        assert lines[-1] == "}"
        add = [("__ember.H", "四章の伝承 (lore.skyabyss.chapter1.ember_hearth、ember_prev_page。生成元 tools/gen/ch4/gen_ch4_content.py)")] + list(only.items())
        keys = {k for k, _ in add}
        body = [l for l in lines[:-1] if not any(l.startswith("  %s:" % json.dumps(k, ensure_ascii=False)) for k in keys)]
        body[-1] = body[-1].rstrip(",") + ","
        for n, (k, v) in enumerate(add):
            body.append("  %s: %s%s" % (json.dumps(k, ensure_ascii=False), json.dumps(v, ensure_ascii=False), "," if n < len(add) - 1 else ""))
        path.write_text(chr(10).join(body + ["}"]) + chr(10), encoding="utf-8")
        json.loads(path.read_text(encoding="utf-8"))


# ---------------------------------------------------------------- データ
def write_data() -> None:
    lore = RES / "data/skyabyss/lore/entry/chapter1"
    dump(lore / "ember_hearth.json", {"chapter": "titan_ember", "order": 179, "unlock": [{"type": "manual"}],
                                      "related": ["chapter1/ember_ruins", "chapter1/ember_prev_page"]})
    dump(lore / "ember_prev_page.json", {"chapter": "titan_ember", "order": 179,
                                         "unlock": [{"type": "page", "target": "ember_hearth"}, {"type": "manual"}],
                                         "related": ["chapter1/ember_hearth"]})

    dump(RES / "data/skyabyss/quest/ch4/fragments.json", {
        "category": "main", "chapter": "titan_ember", "order": 45, "giver": "skyabyss:storyteller",
        "min_stage": 3,
        "start_when": {"type": "stage", "min": 3},
        "stages": [
            {"id": "teller", "objectives": [{"id": "talk", "type": "talk", "target": "skyabyss:storyteller", "topic": "ch4_s_three"}]},
            {"id": "isana", "objectives": [{"id": "talk", "type": "talk", "target": "skyabyss:whale_chaser", "topic": "ch4_i_three"}]},
        ],
        "reward": {"xp": 400, "affinity": {"skyabyss:storyteller": 3, "skyabyss:whale_chaser": 3}},
    })

    def node(face, nxt=None, ok=None):
        n = {"face": face}
        if nxt:
            n["next"] = nxt
        if ok:
            n["options"] = [{"id": "ok", "goto": "end", "do": [{"type": "affinity", "amount": 2}]}]
        return n

    def quest_at(stage):
        return {"type": "quest", "quest": "ch4/fragments", "is": "active", "stage": stage}

    def option(menu, node_id, stage):
        # 主の選択肢に足す (入口には足さない: ほかの会話の入口の順番を取らない)
        return {menu: {"before": "bye", "options": [{"id": "ch4", "goto": node_id, "kind": "quest", "if": quest_at(stage)}]}}

    # 語り部: 入口に足す。ほかの入口 (冷めない炭・天の鍵・潮殻と雷翼の報告) の条件が合うあいだは、そちらを先にする
    others = [
        {"type": "quest", "quest": "main_tide", "is": "active", "stage": "report"},
        {"type": "quest", "quest": "main_thunder", "is": "active", "stage": "rumor"},
        {"type": "quest", "quest": "main_thunder", "is": "active", "stage": "report"},
        {"type": "quest", "quest": "main_key", "is": "active", "stage": "show"},
        {"type": "quest", "quest": "sub_warm_coal", "is": "active", "stage": "tell"},
    ]
    teller_entry = {"if": {"type": "all", "of": [quest_at("teller"), {"type": "not", "of": {"type": "any", "of": others}}]}, "node": "ch4_s_one"}

    dump(RES / "data/skyabyss/dialogue/ch4/storyteller_ch4.json", {
        "npc": "skyabyss:storyteller", "extends": True,
        "entries": [teller_entry],
        "nodes": {"ch4_s_one": node("grave", "ch4_s_two"), "ch4_s_two": node("grave", "ch4_s_three"),
                  "ch4_s_three": node("smile", ok=True)},
    })
    dump(RES / "data/skyabyss/dialogue/ch4/whale_chaser_ch4.json", {
        "npc": "skyabyss:whale_chaser", "extends": True,
        "add_options": option("c_menu", "ch4_i_one", "isana"),
        "nodes": {"ch4_i_one": node("neutral", "ch4_i_two"), "ch4_i_two": node("grave", "ch4_i_three"),
                  "ch4_i_three": node("grave", ok=True)},
    })


def main() -> None:
    write_text()
    write_blueprints()
    write_data()
    write_lang()
    print("ok: %d texts" % len(JA))


if __name__ == "__main__":
    main()
