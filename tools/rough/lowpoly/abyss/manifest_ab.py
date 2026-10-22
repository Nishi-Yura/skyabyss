# -*- coding: utf-8 -*-
# 深淵の敵 9 体のラフ 3 案の選定ページの manifest をまとめる (五章 A3)
# 実行: python -I tools/rough/lowpoly/abyss/manifest_ab.py
#   -> docs/review/abyss_roughs/abyss_manifest_b.json
#   -> python -I tools/rough/build_gallery.py docs/review/abyss_roughs/abyss_manifest_b.json docs/review _gallery_abyss_b
import json
from pathlib import Path

root = Path(__file__).resolve().parents[4]
d = root / "docs/review/abyss_roughs"
order = ["ash_trail", "ember_keeper", "soot_child", "past_shadow", "nameless_shape",
         "lamp_eater", "ceiling_walker", "heartbeat_mimic", "rope_hanger"]
subjects = []
for o in order:
    p = d / f"{o}_manifest.json"
    if p.exists():
        subjects.append(json.loads(p.read_text(encoding="utf-8")))
    else:
        print("missing", o)
manifest = {
    "title": "深淵の敵 9 体 ラフ 3 案 (灰の層 3 種・深み 2 種・深淵だけ 4 種) — 五章 A3",
    "intro": "五章「深淵」の敵のラフです (設計書 36 の 4 節、A3)。層の敵は灰・炭・黒石・熾の橙、深みと深淵だけの敵は石板の深い青灰・スカルクの青緑・骨の白・虚の黒 (明るい色は使わない)。"
             "各敵は元の姿の 3 案で、「歪み」「崩れ」の変わり果てた姿は今は描かず、各案の説明に一言だけ書きました。"
             "灰の這い跡だけは「見えない時の痕」の行を入れてあります。ここでは基の形を選ぶだけで、登録や動きはまだ作っていません。"
             "メッシュ・アトラス・光る画素のマスクは docs/review/abyss_roughs/<id>/{a,b,c}/ に、生成は tools/rough/lowpoly/abyss/ です。",
    "done": [],
    "subjects": subjects,
}
(d / "abyss_manifest_b.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=1), encoding="utf-8")
print(len(subjects), "subjects")
