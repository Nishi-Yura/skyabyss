"""序盤 (17 の 3〜6 章) の語りの文を一か所で書き、ゲームのデータに書き出す。担当 4 (語り)。

書くもの:
  data/skyabyss/lore/entry/prologue/{camp,the_book_margin,footprints,footsteps,bone_hill,sky_song}.json   新しい伝承の項目
  assets/skyabyss_lore/lang/{ja_jp,en_us}.json   上の項目の題・本文・碑文 (この項目の行だけ書き直す)
  assets/skyabyss/lang/{ja_jp,en_us}.json         石板の看板 sign.skyabyss.lore_bone_slab.1〜4 (SignFit が読む名前空間)
  assets/skyabyss_opening/lang/{ja_jp,en_us}.json 開幕の一行 (opening.skyabyss.*) と、目的の帯の文 (objective.skyabyss.*)。このファイルは全部この生成が持つ
  data/skyabyss/quest/*.json                       言伝ての板に出す頼みを、イサナと会うまで閉じる (17 の判断 17-10)
  data/skyabyss/dialogue/storyteller_free.json    語り部の「ほかに、頼みごとはある？」を、イサナと会うまで出さない
  assets/skyabyss_quest/lang/{ja_jp,en_us}.json   上の閉じた頼みの「手がかり」(unlock_hint、足りない分だけ)

語り部・イサナの会話と、メイン「雲の下の歌」の始まりの作り直しは tools/gen_npc_content.py が書く (07 の生成印の間に全文が出る)。
gen_free_quests.py と gen_story_quests.py は、書き出したあとの手直しと食い違っているので、再実行しない。そのため
17-10 の「閉じる」は、この生成が書き出されたデータを直接直す (何度実行しても同じ結果)。

文は「ほのめかし、言いすぎない」。答えを書かず、問いか物で終える。見る者の正体、ループ、天の鍵の意味には触れない。

実行 (リポジトリ直下):
    python tools/gen_opening_text.py
"""
from __future__ import annotations

import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
DATA = ROOT / "src/main/resources/data/skyabyss"
ASSETS = ROOT / "src/main/resources/assets"
DOC17 = ROOT / "docs/design/17_opening_and_rpg_feel.md"

WC = "skyabyss:whale_chaser"

# ================================================================ 伝承の項目 (17 の 5.4節)
# (ID, 順, 記される条件, 関連, 題, 本文, 碑文)。記されるきっかけは、拍子の担当が Lore.unlock(player, ID, true) で送る (17 の 8.4節)
LORE = [
    ("prologue/camp", 12, [{"type": "manual"}], ["prologue/the_book", "prologue/the_book_margin"],
     ("旅の野営地", "The Travelers' Camp"),
     ("焚き火は、輪にした石だけを残して冷えきっていた。荷車は倒れ、寝袋は口を開けたまま、人の形にくぼんでいる。\n\n"
      "荷はあらかた持ち去られ、帳面だけが残されていた。持ち去った者の足あとは、草の上に一つもない。\n\n"
      "誰が、ここで夜を明かしたのだろう。",
      "The fire had gone cold, nothing left but its ring of stones. The cart lay on its side, and the bedroll gaped open, "
      "still hollowed in the shape of a person.\n\n"
      "Most of the baggage was gone. Only the notebook had been left behind. There was not a single footprint in the grass from whoever took it.\n\n"
      "Who spent the night here?"),
     None),
    ("prologue/the_book_margin", 14, [{"type": "manual"}], ["prologue/the_book", "prologue/camp"],
     ("帳面の余白", "A Line in the Margin"),
     ("いつ書かれたのか、分からない。ゆうべまでは白かったはずの余白だ。\n\n"
      "筆跡は、最初の頁の一行と同じだった。\n\n"
      "誰が、先回りして書いているのだろう。",
      "I cannot tell when it was written. The margin was blank last night.\n\n"
      "The handwriting matches the line on the first page.\n\n"
      "Who keeps writing ahead of me?"),
     ("鐘の方へ。", "Toward the bell.")),
    ("prologue/footprints", 15, [{"type": "manual"}], ["prologue/camp", "prologue/footsteps"],
     ("巨大な足跡", "Giant Footprints"),
     ("道を少し外れた地面に、足の形をしたくぼみが三つ、同じ間をおいて並んでいた。底は固い石で、雨水がたまっている。\n\n"
      "一歩で、家が一軒、沈む。\n\n"
      "前にも後ろにも、続きは見つからなかった。歩いた者は、どちらへ向かったのだろう。",
      "A little off the road, hollows in the shape of feet lay in the ground, three of them, evenly spaced. "
      "Their floors were hard stone, and rainwater had gathered in them.\n\n"
      "One step, and a house would sink.\n\n"
      "I could find no trace of them before or behind. Where was the one who walked here headed?"),
     None),
    ("prologue/footsteps", 27, [{"type": "manual"}], ["prologue/footprints", "prologue/sky_song"],
     ("夜の足音", "Night Footsteps"),
     ("日が落ちて間もなく、虫の声が止んだ。風も止んだ。\n\n"
      "地面が三度、ゆっくりと揺れた。一度ごとに強くなり、やがて遠のいていった。\n\n"
      "暗がりには、何も見えなかった。それでも、何かが通り過ぎたことだけは分かった。\n\n"
      "何が、歩いていたのだろう。",
      "Not long after sundown, the insects fell silent. The wind stopped too.\n\n"
      "The ground shook three times, slowly, each stronger than the last, and then faded into the distance.\n\n"
      "I could see nothing in the dark. Even so, I knew something had passed.\n\n"
      "What was walking?"),
     None),
    ("prologue/bone_hill", 28, [{"type": "manual"}], ["prologue/footprints", "prologue/sky_song"],
     ("骨の丘", "The Bone Hill"),
     ("丘の上に、巨大なあばら骨が弧を描いて立っていた。長く風に磨かれて、石のように白い。数えようとして、途中でやめた。\n\n"
      "足もとの石板に、一行だけ刻まれていた。\n\n"
      "これほど大きなものにも、終わりがあるのか。",
      "On a hill stood a gigantic rib cage, arching up from the ground, polished stone-white by long years of wind. "
      "I tried to count the ribs and gave up partway.\n\n"
      "A single line was carved into the slab at its foot.\n\n"
      "Can something this vast really end?"),
     "sign.skyabyss.lore_bone_slab"),
    ("prologue/sky_song", 29, [{"type": "manual"}], ["prologue/footsteps", "prologue/shadow"],
     ("空の歌", "The Song from the Sky"),
     ("夜、空から歌が降ってきた。低く長く、どちらから来るとも分からない声だった。\n\n"
      "星が、帯のかたちに消えていった。何かが、空の上を通っていくように。歌が終わると、星は何ごともなかったように戻った。\n\n"
      "星を隠したのは、何だったのだろう。",
      "At night, a song came down from the sky. A low, long voice, with no way to tell where it came from.\n\n"
      "The stars went out in a broad band, as if something were passing high above. "
      "When the song ended, the stars returned as though nothing had happened.\n\n"
      "What was it that hid the stars?"),
     None),
]

# 石板 (骨の丘の足もと)。1 行は日本語 8 字、英語 90 px まで (SignFit)。4 行目は空
BONE_SLAB = (("歌は", "ここで", "止まった", ""), ("The song", "stopped", "here", ""))

# ================================================================ 開幕の一行 と 目的の帯の文 (17 の 3.1節・5.3節)
# 目的の帯は「今やること」を、ゲームらしいやさしい言い方 (〜してみよう / 〜に話しかけよう / 〜を探そう) で必ず示す (空・名前だけにしない。日本語は 20 字まで、英語は 52 字まで)。操作のキー (WASD など) は書かない。
# 方角と距離は帯の側が右に足す。各拍子の担当 (3) は、目的の ID で文を引く: Component.translatable("objective.skyabyss." + id)
OPENING = {
    "opening.skyabyss.line": ("……ここは、どこだ。", "...Where am I?"),
    # 判断 5: 日 3 の朝になっても宿りに着かない人へ、帳面の余白に増える一行 (伝承 prologue/the_book_margin を記す)
    "opening.skyabyss.margin_line": ("鐘の方へ。", "Toward the bell."),
    # 骨の丘の前触れ (日 2 の昼、イサナのあと。方角のにおい・遠い白・足もとの欠片・風の止む順。字幕で一度ずつ。{0} は方角)
    "opening.skyabyss.bone_rumor": ("乾いた石のにおいが、風に混じっている。{0}の丘のほうだ。", "A smell of dry stone rides the wind. It comes from the hills to the {0}."),
    "opening.skyabyss.bone_far": ("遠く、丘の稜線が妙に白い。雲の影ではない。", "Far off, the ridge of a hill looks strangely white. It is not a cloud's shadow."),
    "opening.skyabyss.bone_mid": ("足もとに、白い欠片が転がっている。……大きすぎる。", "White fragments lie at your feet. ...Too large."),
    "opening.skyabyss.bone_near": ("風が止んだ。丘は、丘のかたちをしていない。", "The wind has stopped. The hill is not shaped like a hill."),
}
OBJECTIVES = [
    # (ID, 日本語, 英語, いつ出すか)
    ("examine_camp", "野営地の跡を調べてみよう", "Look around the abandoned camp", "B0 目覚めてから、帳面を拾うまで"),
    ("follow_smoke", "煙の立つ方へ向かってみよう", "Head toward the rising smoke", "B1 帳面を拾ってから、鐘の宿りの語り部と話すまで (メインの段 follow_smoke)"),
    ("watch_sky", "周りを探索して強くなろう（目安 Lv5）", "Explore and grow stronger (about Lv5)", "B2〜B4 語り部と話してから、最初の影を見るまで (メインの段 watch_sky)"),
    ("ask_storyteller", "宿りの語り部に話しかけよう", "Talk to the storyteller at the lodge", "B4 影を見てから、語り部に尋ねるまで (メインの段 ask_storyteller)"),
    ("find_isana", "外れの焚き火の旅人に話しかけよう", "Talk to the traveler at the fire beyond the lodge", "B5 語り部のあと、イサナと話すまで (メインの段 find_chaser)"),
    ("see_whale", "探索しながら、夜空の歌に耳をすまそう", "Keep exploring, and listen for the song at night", "B7〜B10 イサナと話してから、岩鯨を見るまで (メインの段 see_whale)"),
    ("bone_hill", "白く霞む丘を確かめに行ってみよう", "Go and see the hill that looks white in the haze", "B8 日 2 の昼の寄り道 (任意。出さなくてもよい)"),
]
for oid, ja, en, _ in OBJECTIVES:
    OPENING[f"objective.skyabyss.{oid}"] = (ja, en)

# ================================================================ 17-10: 序盤のサブクエストは、イサナと会うまで出さない
MET = {"type": "flag", "flag": "met", "npc": WC}
UNLOCK_HINT = ("村外れの旅人と話すと、受けられる", "Available once you have spoken to the traveler by the fire")


# ================================================================ 書き出し
def dump(path: Path, data) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def load(path: Path):
    return json.loads(path.read_text(encoding="utf-8"))


def merge_lang(path: Path, prefixes: tuple[str, ...], rows: dict[str, str]) -> None:
    """この生成が持つ行 (prefixes で始まる鍵) を消して、末尾に書き直す。ほかの行と順は変えない。"""
    data = load(path) if path.exists() else {}
    data = {k: v for k, v in data.items() if not k.startswith(prefixes)}
    data.update(rows)
    dump(path, data)


# ================================================================ 設計書 17 の「担当 4 の記録」(全文の一覧)
NEW_NODES_ST = ["shadow0", "shadow0_b", "shadow0_why", "shadow_first", "shadow_wait", "seen_menu", "camp0", "prints0", "steps0",
                "bones0", "song0", "song0_b", "song0_c"]
NEW_NODES_WC = ["c_seen_menu", "c_camp", "c_prints", "c_steps", "c_bones", "c_song", "c_song2"]
CUES = {
    "prologue/camp": "B1 帳面を拾って 3 秒後（野営地の中心から 12 ブロック以内にいるとき）",
    "prologue/the_book_margin": "判断 17-5: 日 3 の朝（tick 0〜6000）で、メインがまだ `follow_smoke` のとき",
    "prologue/footprints": "B3 足跡のくぼみの 24 ブロック以内に初めて入ったとき（起きている人だけ）",
    "prologue/footsteps": "B6 足音の 3 度目が終わって 5 秒後",
    "prologue/bone_hill": "B8 石板の 12 ブロック以内に入ったとき（または石板の看板を読んだとき）",
    "prologue/sky_song": "B9 歌が終わり、星が戻ったあと",
}


def md(t: str) -> str:
    return t.replace("\n", "<br>").replace("|", "｜")


def doc_tables() -> str:
    sys.path.insert(0, str(ROOT / "tools"))
    import gen_npc_content as npc  # 会話の文は gen_npc_content.py が持つ (ここでは読むだけ。ファイルは書かない)

    L = []
    L.append("#### 新しい伝承の頁（序章の章、`data/skyabyss/lore/entry/prologue/`）\n")
    L.append("| ID | 順 | 記す合図（拍子の担当が `Lore.unlock(player, ID, true)`） | 題 | 本文（日本語） | 本文（英語） |\n| --- | --- | --- | --- | --- | --- |")
    for lid, order, unlock, related, title, body, inscription in LORE:
        ins = ""
        if isinstance(inscription, tuple):
            ins = f"<br>（碑文）{inscription[0]}"
        elif isinstance(inscription, str):
            ins = "<br>（碑文）" + "／".join(x for x in BONE_SLAB[0] if x)
        L.append(f"| `{lid}` | {order} | {CUES[lid]} | {title[0]}<br>{title[1]} | {md(body[0])}{ins} | {md(body[1])} |")
    L.append("")
    L.append("#### 開幕の一行と目的の帯の文（`assets/skyabyss_opening/lang/`）\n")
    L.append("| 鍵 | 日本語 | 英語 | 出す場面 |\n| --- | --- | --- | --- |")
    line, margin = OPENING["opening.skyabyss.line"], OPENING["opening.skyabyss.margin_line"]
    L.append(f"| `opening.skyabyss.line` | {line[0]} | {line[1]} | B0 題字「天と淵のあいだ」の下 |")
    L.append(f"| `opening.skyabyss.margin_line` | {margin[0]} | {margin[1]} | 判断 17-5 の余白の一行（頁 `prologue/the_book_margin` の碑文と同じ文） |")
    for oid, ja, en, when in OBJECTIVES:
        L.append(f"| `objective.skyabyss.{oid}` | {ja} | {en} | {when} |")
    L.append("")
    L.append("#### 石板（`sign.skyabyss.lore_bone_slab.1〜4`、骨の丘の足もと。看板で置く）\n")
    L.append("| 行 | 日本語 | 英語 |\n| --- | --- | --- |")
    for i in range(4):
        L.append(f"| {i + 1} | {BONE_SLAB[0][i] or '（空）'} | {BONE_SLAB[1][i] or '(blank)'} |")
    L.append("")
    groups = (("語り部", npc.st, NEW_NODES_ST, [("shadow0", "メインが `follow_smoke`"),
                                             ("shadow_first", "メインが `ask_storyteller` で、初めて話す"),
                                             ("shadow_wait", "メインが `watch_sky` で、この話をまだ聞いていない")]),
              ("イサナ", npc.wc, NEW_NODES_WC, []))
    for title, role, nodes, entries in groups:
        L.append(f"#### {title}の足し書き（`tools/gen_npc_content.py`。全部の会話は 07 の生成表）\n")
        if entries:
            L.append("入口（上から調べる。先頭の `fragment`・`song_done` のあと、`deja_vu_song` の前）:\n")
            L.append("| 節 | 条件 |\n| --- | --- |")
            for nid, cond in entries:
                L.append(f"| `{nid}` | {cond} |")
            L.append("")
        L.append("| 節 | 日本語 | 英語 | 選択肢 |\n| --- | --- | --- | --- |")
        for nid in nodes:
            n = role.nodes[nid]
            opts = "<br>".join("「" + npc.JA[role.base + "." + nid + "." + o["id"]] + "」→ `" + o["goto"] + "`" for o in n.get("options", []))
            if "next" in n:
                opts = f"▼ `{n['next']}`" + ("<br>" + opts if opts else "")
            L.append(f"| `{nid}` | {md(npc.JA[role.base + '.' + nid])} | {md(npc.EN[role.base + '.' + nid])} | {opts} |")
        L.append("")
    return "\n".join(L)


SECTION17 = """

---

## 11. 担当 4（語り）の記録

- 日付: 2026-10-17。文は「ほのめかし、言いすぎない」。巨神・見る者・ループ・天の鍵の意味は書かない（自己テスト `OpeningStorySelfTest` が言葉で確かめる）。
- 生成元: `tools/gen_opening_text.py`（伝承・看板・開幕の一行・目的の帯・17-10 の閉じ方）、`tools/gen_npc_content.py`（語り部とイサナの足し書き、メインの始まり）。下の表は、この 2 つの文を読んで書き出したもの（印の間は手で直さない）。

<!-- 担当4 生成ここから（tools/gen_opening_text.py が書く） -->
<!-- 担当4 生成ここまで -->
"""


def write_doc17() -> None:
    if not DOC17.exists():
        return
    doc = DOC17.read_text(encoding="utf-8")
    if "<!-- 担当4 生成ここから" not in doc:
        doc = doc.rstrip("\n") + "\n" + SECTION17
    pat = re.compile(r"(<!-- 担当4 生成ここから[^>]*-->\n).*?(<!-- 担当4 生成ここまで -->)", re.S)
    doc = pat.sub(lambda m: m.group(1) + "\n" + doc_tables() + "\n" + m.group(2), doc)
    DOC17.write_text(doc, encoding="utf-8")


def main() -> None:
    ja_lore, en_lore = {}, {}
    prefixes = []
    for lid, order, unlock, related, title, body, inscription in LORE:
        entry = {"chapter": "prologue", "order": order}
        base = "lore.skyabyss." + lid.replace("/", ".")
        prefixes.append(base + ".")
        if inscription is not None and isinstance(inscription, tuple):
            entry["inscription"] = True
            ja_lore[base + ".inscription"], en_lore[base + ".inscription"] = inscription
        elif isinstance(inscription, str):
            entry["inscription_sign"] = inscription
        entry["unlock"] = unlock
        entry["related"] = related
        dump(DATA / "lore/entry" / f"{lid}.json", entry)
        ja_lore[base + ".title"], en_lore[base + ".title"] = title
        ja_lore[base + ".body"], en_lore[base + ".body"] = body
    for code, rows in (("ja_jp", ja_lore), ("en_us", en_lore)):
        merge_lang(ASSETS / "skyabyss_lore/lang" / f"{code}.json", tuple(prefixes), rows)
    # 石板
    for idx, code in ((0, "ja_jp"), (1, "en_us")):
        rows = {f"sign.skyabyss.lore_bone_slab.{i + 1}": BONE_SLAB[idx][i] for i in range(4)}
        merge_lang(ASSETS / "skyabyss/lang" / f"{code}.json", ("sign.skyabyss.lore_bone_slab.",), rows)
    # 開幕の一行と目的の帯
    for idx, code in ((0, "ja_jp"), (1, "en_us")):
        dump(ASSETS / "skyabyss_opening/lang" / f"{code}.json", {k: v[idx] for k, v in OPENING.items()})

    # 17-10: 言伝ての板の頼みを、イサナと会うまで閉じる
    hint_added = 0
    for path in sorted((DATA / "quest").glob("*.json")):
        q = load(path)
        if not q.get("board"):
            continue
        qid = path.stem
        old = q.get("unlock")
        if old is None:
            q["unlock"] = MET
        elif old != MET and not (old.get("type") == "all" and MET in old.get("of", [])):
            q["unlock"] = {"type": "all", "of": [old, MET]}
        else:
            continue
        dump(path, q)
        for idx, code in ((0, "ja_jp"), (1, "en_us")):
            lang_path = ASSETS / "skyabyss_quest/lang" / f"{code}.json"
            lang = load(lang_path)
            key = f"quest.skyabyss.{qid}.unlock_hint"
            if key not in lang:
                lang[key] = UNLOCK_HINT[idx]
                dump(lang_path, lang)
                hint_added += 1
    # 語り部の「ほかに、頼みごとはある？」
    free = DATA / "dialogue/storyteller_free.json"
    d = load(free)
    for opt in d["add_options"]["menu"]["options"]:
        if opt["id"] == "errands":
            opt["if"] = MET
    dump(free, d)
    write_doc17()
    print(f"lore {len(LORE)}, objectives {len(OBJECTIVES)}, quest unlock hints {hint_added}")


if __name__ == "__main__":
    main()
