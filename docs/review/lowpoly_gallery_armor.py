# -*- coding: utf-8 -*-
# 防具の低ポリ 3 案の選定ページの manifest をまとめる
import json
from pathlib import Path

here = Path(__file__).parent
sub = json.loads((here / "lowpoly_roughs" / "armor_manifest.json").read_text(encoding="utf-8"))
manifest = {
    "title": "防具 低ポリ 3案",
    "intro": "巨神4体の防具一式を、武器と同じ考え方で作った3案です。どの案も顔は覆っていません。A は巨神の素材の板を成形した重い殻、B は頭巾・外套・前垂れの布で輪郭を作る静かな案、C は薄い布の上に巨神の骨組みが広がる軽い案です。"
             "レア・伝説と強化の見た目は C で例を作りました（武器と同じはしご）。B の頭巾と裾を C に足すような組み合わせも、メモに書いてもらえれば作れます。",
    "done": [
        "武器13本を選んだ方向で作り、ゲームに入れている途中",
        "余燼の背の5体、語り部・イサナ・見る者、巨神4体の本体を低ポリにした",
    ],
    "subjects": [sub],
}
(here / "lowpoly_gallery_armor.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=1), encoding="utf-8")
print(sub["id"], sub["sheets"])
