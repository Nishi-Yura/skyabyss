"""雷獣の文の断片 (assets/skyabyss_storm/lang/parts/raiju.json) を、ゲームが読む lang に書き出す (仮)。

使い方: リポジトリ直下で  python tools/gen_raiju_lang.py
出力: src/main/resources/assets/skyabyss_raiju/lang/{en_us,ja_jp}.json

文の正本は断片のほう (12 の 11.1節: 伝承の担当が skyabyss_storm の lang へまとめる)。
まとめるまでのあいだ、ゲームで雷獣の文が鍵のまま出ないように、この仮の名前空間に写す。
伝承の担当がまとめたら、出力のフォルダ (assets/skyabyss_raiju) ごと消してよい。
"""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / "src/main/resources/assets/skyabyss_storm/lang/parts/raiju.json"
OUT = ROOT / "src/main/resources/assets/skyabyss_raiju/lang"


def main():
    parts = json.loads(SRC.read_text(encoding="utf-8"))
    OUT.mkdir(parents=True, exist_ok=True)
    for code in ("en_us", "ja_jp"):
        (OUT / f"{code}.json").write_text(json.dumps(parts[code], ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    assert parts["en_us"].keys() == parts["ja_jp"].keys(), "en_us と ja_jp の鍵がそろっていない"
    print("raiju lang:", len(parts["en_us"]), "keys ->", OUT)


if __name__ == "__main__":
    main()
