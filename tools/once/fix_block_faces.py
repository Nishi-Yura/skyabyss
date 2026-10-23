#!/usr/bin/env python3
"""ブロックモデルの、外へ向いたまま欠けている面 (tools/audit/mesh_audit.py が「exposed faces missing」と報告するもの) を足して閉じる。

足す面は、その部品に既にある面 (最初の一枚) の絵 (texture / uv) の写し。cullface や rotation は写さない
(板の裏側や側面は、隣の面を隠すためのものではないので)。何度流してもよい。

  python tools/once/fix_block_faces.py [ファイル名の一部]...
"""
# tools 内の他グループのモジュールを import できるようにする（フォルダ移動後も兄弟モジュールを解決）
import os as _os
import sys as _sys
_sys.path[:0] = [_os.path.join(_os.path.dirname(_os.path.abspath(__file__)), p) for p in ('../audit',)]
import json
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import mesh_audit as ma  # noqa: E402


def main(argv):
    n_fixed = 0
    for p in sorted(os.listdir(ma.BLOCKS)):
        if not p.endswith(".json") or (argv and not any(a in p for a in argv)):
            continue
        path = os.path.join(ma.BLOCKS, p)
        res = ma.audit_block(path)
        if not res["missing"]:
            continue
        with open(path, encoding="utf-8") as f:
            d = json.load(f)
        changed = False
        for note in res["notes"]:
            if "exposed faces missing" not in note:
                continue
            idx = int(note.split()[1].rstrip(":"))
            names = note.split("missing ")[1].split(",")
            e = d["elements"][idx]
            src = next(iter(e["faces"].values()))
            for k in names:
                face = {"uv": list(src.get("uv", [0, 0, 16, 16])), "texture": src["texture"]}
                e["faces"][k] = face
                changed = True
        if changed:
            with open(path, "w", encoding="utf-8") as f:
                json.dump(d, f, indent=2, ensure_ascii=False)
                f.write("\n")
            n_fixed += 1
            print("fixed", p)
    print("fixed %d file(s)" % n_fixed)
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
