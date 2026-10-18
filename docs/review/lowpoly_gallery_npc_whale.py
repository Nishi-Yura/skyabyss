# -*- coding: utf-8 -*-
# 人物 3 体と岩鯨の低ポリ 3 案の選定ページの manifest をまとめる (各体の *_manifest.json を順に並べる)
import json
from pathlib import Path

here = Path(__file__).parent
order = ["lp_storyteller", "lp_isana", "lp_watcher", "lp_stone_whale"]
subjects = [json.loads((here / "lowpoly_roughs" / f"{o}_manifest.json").read_text(encoding="utf-8")) for o in order]
manifest = {
    "title": "人物3体・岩鯨 低ポリ 3案",
    "intro": "語り部とイサナは、前に選んだキャラデザイン（頭巾の語り部・つば広帽の旅人）を低ポリで作り直した3案です。顔は頭の中に収め、浮いた板にはしていません。"
             "見る者は「人らしくしすぎない」決まりのまま、遠くから見て少し不気味な3案です。"
             "岩鯨は巨神本体の最初の1体で、近・中・遠の3段の細かさで作りました（左右のひれは同じ形）。B と C を選ぶと、背中のダンジョンの外形と色も作り直しになります。"
             "採用する案を選び、直してほしい点があれば書いてください。",
    "done": [
        "岩鯨・潮殻の背の敵をゲームに入れた（箱のモデルより約3倍軽い）",
        "珊瑚憑きと雷翼の背の5体（すべて A）をゲームに入れている途中。余燼の背の5体（灰被り A・火の粉蛾 A・燠虫 B・鍛え手 A・心臓 C）はその次",
    ],
    "subjects": subjects,
}
(here / "lowpoly_gallery_npc_whale.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=1), encoding="utf-8")
print([s["id"] for s in subjects])
