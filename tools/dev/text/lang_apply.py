# -*- coding: utf-8 -*-
"""推敲した文言を言語ファイルに当てる (docs/design/57_text_style.md)。差し替えの JSON は {キー: 新しい文言} (改行は本物の改行か \n)。

  python -X utf8 tools/dev/text/lang_apply.py <ja_jp.json> <差し替え.json> [--check]

--check は当てずに確かめるだけ (手分けの作業は --check で確かめ、当てるのはまとめ役)。

確かめること (通らなかったキーは当てずに理由を出す):
  - キーがファイルにある
  - 書式の記号 (%s・%d・%1$s・%%・§x) の並びと数、改行の数が同じ
  - 看板 (sign.*) は 1 行 9 字まで (元が 9 字を超えていたら元の長さまで)
  - 短い札 (gui.・key.・container.・label.・hud.・button・.tab・.title で終わる・subtitles.) は元の長さ + 2 字まで
  - 空にしない
キーの順と書き方 (2 字下げ・最後に改行) は元のまま
"""
import json
import re
import sys

FMT = re.compile(r"%(?:\d+\$)?[sdf]|%%|§.")
SHORT = ("gui.", "key.", "container.", "label.", "hud.", "subtitles.")


def short_key(k):
    return k.startswith(SHORT) or ".button" in k or k.endswith((".tab", ".title", ".name"))


def check(k, old, new):
    if not new.strip():
        return "empty"
    if FMT.findall(old) != FMT.findall(new):
        return f"format codes differ {FMT.findall(old)} -> {FMT.findall(new)}"
    if old.count("\n") != new.count("\n"):
        return f"line breaks differ {old.count(chr(10))} -> {new.count(chr(10))}"
    if k.startswith("sign."):
        lim = max(9, max(len(x) for x in old.split("\n")))
        if any(len(x) > lim for x in new.split("\n")):
            return f"sign line longer than {lim}"
    elif short_key(k) and len(new) > len(old) + 2:
        return f"short label grew {len(old)} -> {len(new)} (max +2)"
    return None


def main():
    path, patch = sys.argv[1], sys.argv[2]
    only_check = "--check" in sys.argv[3:]
    d = json.load(open(path, encoding="utf8"))
    p = json.load(open(patch, encoding="utf8"))
    ok, bad = 0, []
    for k, v in p.items():
        if not isinstance(v, str):
            bad.append((k, "not a string"))
            continue
        v = v.replace("\n", "\n")
        if k not in d:
            bad.append((k, "no such key"))
            continue
        why = check(k, d[k], v)
        if why:
            bad.append((k, why))
            continue
        if d[k] != v:
            d[k] = v
            ok += 1
    if not only_check:
        with open(path, "w", encoding="utf8", newline=chr(10)) as f:
            json.dump(d, f, ensure_ascii=False, indent=2)
            f.write(chr(10))
    print(("would apply" if only_check else "applied") + f" {ok}, rejected {len(bad)}")
    for k, why in bad:
        print(f"REJECTED {k}: {why}")


if __name__ == "__main__":
    main()
