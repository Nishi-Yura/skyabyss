# -*- coding: utf-8 -*-
# 余燼の背の低ポリ 3 案の選定ページの manifest をまとめる (各体の *_manifest.json を順に並べる)
import json
from pathlib import Path

here = Path(__file__).parent
order = ["lp_ash_thrall", "lp_ember_moth", "lp_cinder_roller", "lp_ash_smith", "lp_ember_heart"]
subjects = [json.loads((here / "lowpoly_roughs" / f"{o}_manifest.json").read_text(encoding="utf-8")) for o in order]
manifest = {
    "title": "余燼の背 低ポリ 3案",
    "intro": "余燼の背の5体の3案です。どれも余燼の言葉（灰・燠火・玄武岩・冷えかけの溶岩・熱のひび・鍛冶）だけで作り、ほかの巨神の敵と形や素材が重ならないようにしました。"
             "火の粉蛾は雷蛾と並べて違いが分かるようにしています。骨の名前と位置は今のモデルと同じで、今のアニメーションがそのまま動きます。"
             "採用する案を選び、直してほしい点があれば書いてください。",
    "done": [
        "岩鯨の背の5体と潮殻の背の4体をゲームに入れた（箱のモデルより約3倍軽い）",
        "珊瑚憑き（作り直し A）と雷翼の背の5体（すべて A）をゲームに入れている途中",
    ],
    "subjects": subjects,
}
(here / "lowpoly_gallery_ember.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=1), encoding="utf-8")
print([s["id"] for s in subjects])
