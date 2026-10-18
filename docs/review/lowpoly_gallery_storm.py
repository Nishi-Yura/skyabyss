# -*- coding: utf-8 -*-
# 珊瑚憑きの作り直しと雷翼の背の低ポリ 3 案の選定ページの manifest をまとめる (各体の *_manifest.json を順に並べる)
import json
from pathlib import Path

here = Path(__file__).parent
order = ["coral_thrall2", "lp_feather_warden", "lp_static_moth", "lp_thunder_supplicant", "lp_raiju", "lp_storm_heart"]
subjects = [json.loads((here / "lowpoly_roughs" / f"{o}_manifest.json").read_text(encoding="utf-8")) for o in order]
manifest = {
    "title": "珊瑚憑きの作り直し・雷翼の背 低ポリ 3案",
    "intro": "いちばん上は、「鯨の時の敵の再利用みたい」とのことで作り直した珊瑚憑きの3案です（骨・肋・頭蓋をやめ、色も鯨の背と分けました）。"
             "その下は雷翼の背の5体の3案です。どれも雷翼の言葉（嵐・静電気・銅・羽・鳥籠）だけで作り、ほかの巨神の敵と形や素材が重ならないようにしました。"
             "骨の名前と位置は今のモデルと同じで、今のアニメーションがそのまま動きます。採用する案を選び、直してほしい点があれば書いてください。",
    "done": [
        "岩鯨の背の5体（番兵長 A・苔這い B・肋の射手 C・水路の顎 B・心臓 A）をゲームに入れた（箱のモデルより約3.3倍軽い）",
        "潮殻の背の4体（磯走り A・泡吹き A・潮招き A・心臓 B）をゲームに入れている途中",
    ],
    "subjects": subjects,
}
(here / "lowpoly_gallery_storm.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=1), encoding="utf-8")
print([s["id"] for s in subjects])
