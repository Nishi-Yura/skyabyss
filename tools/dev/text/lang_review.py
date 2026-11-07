# -*- coding: utf-8 -*-
"""手分けの差し替えを見比べる・まとめて当てる (docs/design/57_text_style.md)。

  python -X utf8 tools/dev/text/lang_review.py <差し替えのフォルダ> show [件数]   元 → 新 を出す (ファイルごと、件数まで)
  python -X utf8 tools/dev/text/lang_review.py <差し替えのフォルダ> apply          全部の差し替えを、キーのあるファイルへ当てる

差し替えのキーがどのファイルのものかは、言語ファイルを全部読んで探す
"""
import glob
import json
import os
import subprocess
import sys

LANGS = sorted(glob.glob("src/main/resources/assets/*/lang/ja_jp.json"))


def owner_map():
    m = {}
    for p in LANGS:
        for k in json.load(open(p, encoding="utf8")):
            m.setdefault(k, p)
    return m


def main():
    folder, mode = sys.argv[1], sys.argv[2]
    owner = owner_map()
    patches = sorted(glob.glob(os.path.join(folder, "*.json")))
    if mode == "show":
        limit = int(sys.argv[3]) if len(sys.argv) > 3 else 20
        for pf in patches:
            p = json.load(open(pf, encoding="utf8"))
            print(f"=== {os.path.basename(pf)}: {len(p)} changes")
            for k, v in list(p.items())[:limit]:
                f = owner.get(k)
                old = json.load(open(f, encoding="utf8"))[k] if f else "(no key)"
                print(f"- {k}\n    {old}\n  → {v}")
        return
    by_file = {}
    for pf in patches:
        for k, v in json.load(open(pf, encoding="utf8")).items():
            f = owner.get(k)
            if f:
                by_file.setdefault(f, {})[k] = v
            else:
                print("no key", k)
    tmp = os.path.join(folder, "_merged")
    os.makedirs(tmp, exist_ok=True)
    for f, kv in by_file.items():
        mp = os.path.join(tmp, f.replace("/", "_").replace("\\", "_"))
        json.dump(kv, open(mp, "w", encoding="utf8"), ensure_ascii=False)
        r = subprocess.run([sys.executable, "-X", "utf8", "tools/dev/text/lang_apply.py", f, mp], capture_output=True, text=True, encoding="utf8")
        print(f, r.stdout.strip().replace("\n", " | "))


if __name__ == "__main__":
    main()
