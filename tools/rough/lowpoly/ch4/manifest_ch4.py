# -*- coding: utf-8 -*-
# 四章の地上の敵 4 種と小物 5 つのラフ 3 案の選定ページの manifest をまとめる (P19・P20)
# 実行: python -I tools/rough/lowpoly/ch4/manifest_ch4.py
#   -> docs/review/ch4_roughs/ch4_manifest.json
#   -> python -I tools/rough/build_gallery.py docs/review/ch4_roughs/ch4_manifest.json docs/review _gallery_ch4
import json
from pathlib import Path

root = Path(__file__).resolve().parents[4]
d = root / "docs/review/ch4_roughs"
order = ["cinder_mite", "ash_husk", "ash_dragger", "elder_thrall"]
subjects = []
for o in order:
    p = d / f"{o}_manifest.json"
    if p.exists():
        subjects.append(json.loads(p.read_text(encoding="utf-8")))
    else:
        print("missing", o)
items = {"soot_iron": ("煤鉄", ["煤けた延べ棒", "煤の塊", "二本重ね"]),
         "ash_hemp": ("灰麻", ["縛った茎の束", "繊維の玉", "葉の付いた茎"]),
         "ash_ward_thread": ("灰の護り糸", ["かせに熾の玉", "護り結び", "煤鉄の糸巻き"]),
         "soot_whetstone": ("煤の砥石", ["黒い砥石", "煤鉄の帯", "割れ目に熾"]),
         "ash_cooler": ("灰の冷やし水", ["丸い瓶と灰の粒", "四角い瓶", "素焼きの小壺"])}
for iid, (name, notes) in items.items():
    subjects.append({"id": "ch4_" + iid, "short": name, "kind": "小物 3案 (32x32)", "title": f"{name} ({iid}) — 32x32 のドット絵 3 案",
                     "sheets": ["ch4_roughs/items/icons_ch4.png"],
                     "extra": f"絵は docs/review/ch4_roughs/items/icons/{iid}_{{a,b,c}}_32.png (選ばなかった案は tools/sources/{iid}/)。生成: tools/rough/lowpoly/ch4/icons_ch4.py。",
                     "options": {k: {"name": f"{k.upper()} {n}", "desc": n} for k, n in zip("abc", notes)}})
manifest = {
    "title": "四章の地上の敵と小物 ラフ 3 案 (熾り蟲・灰被りの屑・灰ひきずり・古い灰被り・煤鉄・灰麻・灰の護り糸・煤の砥石・灰の冷やし水)",
    "intro": "四章 (余燼) の地上の敵と品のラフです (設計書 21 の 6.1、P19・P20)。色の言葉は灰・炭・黒石・熾の橙。"
             "灰被りの屑と古い灰被りは、背中の灰被り (A 灰衣の火番) に部品を足し引きした変種です。"
             "実装は仮の選定 (熾り蟲 A・屑 B・灰ひきずり A・古い灰被り A、小物はすべて A) で入れてあります。"
             "別の案を選ぶときは tools/gen_ch4_ground_mobs.py の CHOICE / ITEM_CHOICE を変えて流し直します。",
    "done": [],
    "subjects": subjects,
}
(d / "ch4_manifest.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=1), encoding="utf-8")
print(len(subjects), "subjects")
