# -*- coding: utf-8 -*-
# 潮殻の背の低ポリ 3 案の選定ページの manifest をまとめる (各体の *_manifest.json を順に並べる)
import json
from pathlib import Path

here = Path(__file__).parent
order = ["coral_thrall", "reef_scuttler", "lp_bubble_puffer", "lp_tide_beckoner", "lp_tide_heart"]
subjects = [json.loads((here / "lowpoly_roughs" / f"{o}_manifest.json").read_text(encoding="utf-8")) for o in order]
manifest = {
    "title": "潮殻の背 低ポリ 3案",
    "intro": "岩鯨の背と同じ作り方（低ポリの非立方体＋1ブロック32画素のドット絵、軽さ重視）で、潮殻の背の5体を3案ずつ作りました。"
             "どの案も骨の名前と位置は今のモデルと同じで、今のアニメーションがそのまま動きます。武器は別のモデルです。"
             "採用する案を選び、直してほしい点があれば書いてください。",
    "done": [
        "岩鯨の背: 番兵長 A 石鎧の将・苔這い B 骨・棘・肋の射手 C ひげ板の頭巾・水路の顎 B 提灯鮟鱇・心臓 A 地層の板 をゲームに入れている途中",
    ],
    "subjects": subjects,
}
(here / "lowpoly_gallery_tide.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=1), encoding="utf-8")
print([s["id"] for s in subjects])
