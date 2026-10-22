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
         "ash_cooler": ("灰の冷やし水", ["丸い瓶と灰の粒", "四角い瓶", "素焼きの小壺"]),
         "ash_baked_bread": ("灰焼きのパン", ["灰をかぶった丸パン", "灰麻の紐の細長いパン", "焦げ格子の円盤"])}
for iid, (name, notes) in items.items():
    subjects.append({"id": "ch4_" + iid, "short": name, "kind": "小物 3案 (32x32)", "title": f"{name} ({iid}) — 32x32 のドット絵 3 案",
                     "sheets": ["ch4_roughs/items/icons_ch4.png"],
                     "extra": f"絵は docs/review/ch4_roughs/items/icons/{iid}_{{a,b,c}}_32.png (選ばなかった案は tools/sources/{iid}/)。生成: tools/rough/lowpoly/ch4/icons_ch4.py。",
                     "options": {k: {"name": f"{k.upper()} {n}", "desc": n} for k, n in zip("abc", notes)}})
subjects.append({"id": "ch4_ash_walking", "short": "灰歩きの旅装", "kind": "旅装 3案 (4 部位)", "title": "灰歩きの旅装 (ash_walking) — 低ポリの 4 部位 3 案",
                 "sheets": ["ch4_roughs/items/armor_ch4.png"],
                 "extra": "防具の低ポリ様式 C (箱・輪・板)。メッシュとアトラスは docs/review/ch4_roughs/items/armor_ch4/{a,b,c}/。生成: tools/rough/lowpoly/ch4/armor_ch4.py、入れ方は tools/gen/ch4/gen_ch4_gear.py の CHOICE。",
                 "options": {"a": {"name": "A 煤の前掛け", "desc": "黒石の肩 3 段・煤けた革の上着と鍛冶の前掛け・灰麻の襟巻き・兜に煤鉄の立て飾り"},
                             "b": {"name": "B 灰麻の外套", "desc": "灰色の長い外套・煤鉄の帯と縁・煤鉄のボタン 4 つ・後ろの裾"},
                             "c": {"name": "C 灰の頭巾", "desc": "灰麻の布・後ろへ長い頭巾の尾・革の腕当て・黒石の肩と背の板"}}})
manifest = {
    "title": "四章の地上の敵と小物 ラフ 3 案 (熾り蟲・灰被りの屑・灰ひきずり・古い灰被り・煤鉄・灰麻・灰の護り糸・煤の砥石・灰の冷やし水)",
    "intro": "四章 (余燼) の地上の敵と品のラフです (設計書 21 の 6.1、P19・P20)。色の言葉は灰・炭・黒石・熾の橙。"
             "灰被りの屑と古い灰被りは、背中の灰被り (A 灰衣の火番) に部品を足し引きした変種です。"
             "敵と小物 5 つは選定済み (熾り蟲 A・屑 C・灰ひきずり A・古い灰被り A、小物は煤鉄 A・灰麻 A・護り糸 B・砥石 C・冷やし水 A)。灰歩きの旅装と灰焼きのパンは仮に A で入れてあります。"
             "別の案を選ぶときは tools/gen/ch4/gen_ch4_ground_mobs.py の CHOICE / ITEM_CHOICE を変えて流し直します。",
    "done": [],
    "subjects": subjects,
}
(d / "ch4_manifest.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=1), encoding="utf-8")
print(len(subjects), "subjects")
