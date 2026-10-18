# -*- coding: utf-8 -*-
# 岩鯨の背の低ポリ 3 案の選定ページの manifest をまとめる (各体の *_manifest.json を順に並べる)
import json
from pathlib import Path

here = Path(__file__).parent
order = ["rib_archer", "lp_moss_crawler", "waterway_maw", "whalebone_captain", "lp_whale_heart"]
subjects = [json.loads((here / "lowpoly_roughs" / f"{o}_manifest.json").read_text(encoding="utf-8")) for o in order]
manifest = {
    "title": "岩鯨の背 低ポリ 3案",
    "intro": "新しい共通の作り方（低ポリの非立方体＋1ブロック32画素のドット絵、軽さ重視）で、岩鯨の背の5体を3案ずつ作りました。"
             "骨の番兵は見本で方向が決まったので外しています。どの案も骨の名前と位置は今のモデルと同じで、今のアニメーションがそのまま動きます。"
             "武器は別のモデルです。採用する案を選び、直してほしい点があれば書いてください。",
    "done": ["骨の番兵: 見本の低ポリ版をゲームに入れた（今の箱のモデルより約2.5倍軽い）"],
    "subjects": subjects,
}
(here / "lowpoly_gallery_whale.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=1), encoding="utf-8")
print([s["id"] for s in subjects])
