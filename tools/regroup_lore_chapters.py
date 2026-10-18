"""伝承の章を「巨神 1 体 = 1 章」に組み替える (docs/review/chapter_per_titan_plan.md の P1、docs/design/05)。

やること (何度動かしても同じ結果):
  1. data/skyabyss/lore/entry/chapter1/*.json の "chapter" 欄を、岩鯨・潮殻・雷翼・余燼の章 (titan_whale/titan_tide/titan_storm/titan_ember) に振り分ける。
     項目の ID (chapter1/<名前>)、本文、unlock、order は変えない (人ごとの保存・クエスト・会話が ID を参照する)。
  2. data/skyabyss/lore/chapter/*.json を作り直す。序章 0、一〜四章 1〜4、chapter2 (五章 深淵) 5、chapter3 (六章 月) 6、finale (終章) 7。旧 chapter1 の章定義は消す。
  3. assets/skyabyss_lore/lang/{ja_jp,en_us}.json の章の文 (lore.skyabyss.chapter.<章>.tab/.name/.title) を差し替える。

使い方: python tools/regroup_lore_chapters.py  (リポジトリ直下)
章の呼び名は src/main/java/jp/revati/skyabyss/story/Chapters.java が唯一の写像。ここの表と合わせること。
"""
import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent / "src/main/resources"
LORE = ROOT / "data/skyabyss/lore"
LANG = ROOT / "assets/skyabyss_lore/lang"

# 章 ID, 順番, ja (しおり, 名, 題字), en (しおり, 名, 題字)
CHAPTERS = [
    ("prologue", 0, ("序章", "空の影", "空の影"), ("Prologue", "Shadows Overhead", "Shadows Overhead")),
    ("titan_whale", 1, ("一章", "岩鯨", "空を泳ぐもの"), ("I", "Stone Whale", "What Swims the Sky")),
    ("titan_tide", 2, ("二章", "潮殻", "引く海"), ("II", "Tide Shell", "The Ebbing Sea")),
    ("titan_storm", 3, ("三章", "雷翼", "風に逆らう嵐"), ("III", "Thunder Wing", "The Storm Against the Wind")),
    ("titan_ember", 4, ("四章", "余燼", "足もとから来るもの"), ("IV", "Ember", "What Comes from Below")),
    ("chapter2", 5, ("五章", "深淵", "深淵"), ("V", "The Abyss", "The Abyss")),
    ("chapter3", 6, ("六章", "月", "月"), ("VI", "The Moon", "The Moon")),
    ("finale", 7, ("終章", "雲海", "雲海"), ("Finale", "The Sea of Clouds", "The Sea of Clouds")),
]

# 旧 chapter1 の項目 → 巨神の章。名前の型で決める (どれにも当たらないものは岩鯨)
EMBER_NAMES = {"ash_smith", "forge_ledger", "fifth_verse", "sky_key_whole"}
STORM_NAMES = {"thunder_wing", "raiju", "wind_ledger"}
TIDE_NAMES = {"beckoner"}


def chapter_of(name):
    if name in EMBER_NAMES or name.startswith("ember") or name.startswith("inscription_ember"):
        return "titan_ember"
    if name in STORM_NAMES or name.startswith("storm_") or name.startswith("inscription_storm"):
        return "titan_storm"
    if name in TIDE_NAMES or name.startswith("tide_") or name.startswith("inscription_tide"):
        return "titan_tide"
    return "titan_whale"


def write_json(path, data):
    path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def regroup_entries():
    counts = {}
    for f in sorted((LORE / "entry/chapter1").glob("*.json")):
        d = json.loads(f.read_text(encoding="utf-8"))
        ch = chapter_of(f.stem)
        counts[ch] = counts.get(ch, 0) + 1
        if d.get("chapter") != ch:
            # "chapter" 欄の位置 (先頭) を保つ
            d["chapter"] = ch
            write_json(f, d)
    return counts


def write_chapter_defs():
    d = LORE / "chapter"
    for f in d.glob("*.json"):
        f.unlink()
    for cid, order, _, _ in CHAPTERS:
        write_json(d / f"{cid}.json", {"order": order})


def patch_lang(path, idx):
    lines = path.read_text(encoding="utf-8").split("\n")
    out = []
    done = False
    for line in lines:
        if line.startswith('  "lore.skyabyss.chapter.') and ".tab\"" in line or line.startswith('  "lore.skyabyss.chapter.') and (".name\"" in line or ".title\"" in line):
            if not done:
                # 最初の章の行の所に、全部の章の文をまとめて置く
                for cid, _, ja, en in CHAPTERS:
                    tab, name, title = (ja, en)[idx]
                    for suffix, text in (("tab", tab), ("name", name), ("title", title)):
                        out.append('  %s: %s,' % (json.dumps(f"lore.skyabyss.chapter.{cid}.{suffix}"), json.dumps(text, ensure_ascii=False)))
                done = True
            continue
        out.append(line)
    if not done:
        raise SystemExit("chapter lang lines not found in " + str(path))
    path.write_text("\n".join(out), encoding="utf-8")
    json.loads(path.read_text(encoding="utf-8"))  # 壊していないか


def main():
    counts = regroup_entries()
    write_chapter_defs()
    patch_lang(LANG / "ja_jp.json", 0)
    patch_lang(LANG / "en_us.json", 1)
    print("entries per chapter:", counts)


if __name__ == "__main__":
    main()
