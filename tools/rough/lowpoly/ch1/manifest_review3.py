# -*- coding: utf-8 -*-
# 一章のラフ 12 項目 + 留め袋の画面案 2 項目を 1 枚の選定ページにまとめる
# 実行: python tools/rough/lowpoly/ch1/manifest_review3.py   (先に manifest.py)
#   -> docs/review/review3_manifest.json
#   -> python tools/rough/build_gallery.py docs/review/review3_manifest.json docs/review _gallery_review3
import json
from pathlib import Path

root = Path(__file__).resolve().parents[4]
ch1 = json.loads((root / "docs/review/ch1_roughs_manifest.json").read_text(encoding="utf-8"))
pouch = json.loads((root / "docs/review/_gallery_pouch/manifest.json").read_text(encoding="utf-8"))
for s in pouch["subjects"]:
    s["sheets"] = ["_gallery_pouch/" + f for f in s["sheets"]]
manifest = {
    "title": "一章の新しい中身のラフ・留め袋の画面案",
    "intro": ch1["intro"] + " 末尾の 2 つは、mod の留め袋 (装飾品と鍵・笛などをしまう袋) の画面の置き方 3 案と、使い方 2 案です。",
    "done": [],
    "subjects": ch1["subjects"] + pouch["subjects"],
}
(root / "docs/review/review3_manifest.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=1), encoding="utf-8")
print(len(manifest["subjects"]), "subjects")
