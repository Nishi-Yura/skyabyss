"""二章「引く海」(潮殻。docs/design/08 の 2.8節) のデータ JSON を書く: 伝承の項目 3、クエスト 2、語り部の追加の会話。

使い方: python tools/gen/ch2/gen_ch2_data.py
出力 (語り部の追加の会話には、貝をなくした人への「新しい貝」の節 ch2_conch_ask も含む。入口は墓の話題 lost_ask):
  src/main/resources/data/skyabyss/lore/entry/chapter1/ch2_drift_log.json, ch2_beach_stele.json, ch2_ebb_page.json
  src/main/resources/data/skyabyss/quest/ch2/shore_signs.json, ebb_road.json   (クエスト ID は ch2/shore_signs, ch2/ebb_road)
  src/main/resources/data/skyabyss/dialogue/ch2/storyteller.json                (追加の会話。"extends": true、節の名前は ch2_ で始める)
文は assets/skyabyss_ch2/lang/ja_jp.json, en_us.json (手で書いてある)。
"""
import json
from pathlib import Path

RES = Path(__file__).resolve().parent.parent.parent.parent / "src/main/resources/data/skyabyss"


def w(rel, obj):
    p = RES / rel
    p.parent.mkdir(parents=True, exist_ok=True)
    p.write_text(json.dumps(obj, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def lore():
    for name, order, related in [
        ("ch2_drift_log", 141, ["chapter1/tide_shell"]),
        ("ch2_beach_stele", 142, ["chapter1/tide_ruins"]),
        ("ch2_ebb_page", 143, ["chapter1/tide_ruins", "chapter1/ch2_beach_stele"]),
    ]:
        w(f"lore/entry/chapter1/{name}.json", {"chapter": "titan_tide", "order": order, "unlock": [{"type": "manual"}], "related": related})


def quests():
    sig = lambda i, t: {"id": i, "type": "signal", "target": t, "count": 1, "party": False}
    w("quest/ch2/shore_signs.json", {
        "category": "sub", "chapter": "titan_tide", "order": 45,
        "start_when": {"type": "stage", "min": 1, "max": 1},
        "stages": [{"id": "gather", "xp": 120, "objectives": [sig("cairn", "ch2_cairn"), sig("cargo", "ch2_cargo"), sig("stele", "ch2_stele")]}],
        "reward": {"xp": 160},
    })
    w("quest/ch2/ebb_road.json", {
        "category": "sub", "chapter": "titan_tide", "order": 46,
        # 大干潮が始まったとき TideEbb が語り部の会話の印 ch2_ebb を二章の人に立てる → ここで自動で始まる
        "start_when": {"type": "flag", "flag": "ch2_ebb", "npc": "skyabyss:storyteller"},
        "stages": [{"id": "walk", "objectives": [sig("walk", "ch2_ebb_walked")]}],
        "reward": {"xp": 220},
    })


def dialogue():
    w("dialogue/ch2/storyteller.json", {
        "npc": "skyabyss:storyteller", "extends": True,
        "entries": [{
            "if": {"type": "all", "of": [
                {"type": "stage", "min": 1, "max": 1},
                {"type": "not", "of": {"type": "flag", "flag": "ch2_ledger"}},
            ]},
            "node": "ch2_ledger",
        }],
        # 潮見の貝をなくした人 (二章以降で、どこにも持っていない) に、語り部が新しい貝をくれる。
        # 話題の入口は、墓の話題と一つにまとめた「なくしたものがある」(storyteller_aa_grave.json の lost_ask。tools/gen/world/gen_grave_assets.py)
        "nodes": {
            "ch2_conch_ask": {"face": "grave", "options": [
                {"id": "yes", "kind": "give",
                 "if": {"type": "no_keepsake", "item": "skyabyss:tide_watching_shell"},
                 "do": {"type": "give", "item": "skyabyss:tide_watching_shell", "count": 1},
                 "goto": "ch2_conch_done"},
                {"id": "no", "kind": "talk", "goto": "menu"},
            ]},
            "ch2_conch_done": {"face": "smile", "next": "menu"},
            "ch2_ledger": {"face": "neutral", "next": "ch2_ledger2"},
            "ch2_ledger2": {"face": "grave", "next": "ch2_ledger3"},
            "ch2_ledger3": {"face": "grave", "options": [
                {"id": "look", "goto": "ch2_ledger4"},
                {"id": "decline", "goto": "ch2_ledger5"},
            ]},
            "ch2_ledger4": {"face": "grave", "do": [{"type": "flag", "flag": "ch2_ledger"}, {"type": "affinity", "amount": 2}],
                            "options": [{"id": "ok", "goto": "end"}]},
            "ch2_ledger5": {"face": "neutral", "do": [{"type": "flag", "flag": "ch2_ledger"}],
                            "options": [{"id": "ok", "goto": "end"}]},
        },
    })


if __name__ == "__main__":
    lore()
    quests()
    dialogue()
    print("done")
