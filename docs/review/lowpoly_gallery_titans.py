# -*- coding: utf-8 -*-
# 潮殻・雷翼・余燼の巨神本体の低ポリ 3 案の選定ページの manifest をまとめる (各体の *_manifest.json を順に並べる)
import json
from pathlib import Path

here = Path(__file__).parent
order = ["lp_tide_shell", "lp_thunder_wing", "lp_ember_titan"]
subjects = [json.loads((here / "lowpoly_roughs" / f"{o}_manifest.json").read_text(encoding="utf-8")) for o in order]
manifest = {
    "title": "巨神（潮殻・雷翼・余燼） 低ポリ 3案",
    "intro": "残りの巨神3体を、岩鯨と同じ作り方で近・中・遠の3段の細かさで作りました。岩鯨で C（生き物らしい案）を選ばれたので、どの巨神にも生き物寄りの C を入れています。"
             "A は今の姿に近く、背中のダンジョンもほぼそのまま使えます。B と C は背中の外形と色を作り直します（各体の説明に影響を書きました）。左右のひれ・翼は鏡像で揃えています。"
             "採用する案を選び、直してほしい点があれば書いてください。",
    "done": [
        "岩鯨は C（皮が岩になる鯨）に決定。背中のダンジョンの外形と色も C に合わせて作り直した",
        "語り部 A・イサナ A・見る者 A に決定（ゲームに入れるのは順番待ち）",
    ],
    "subjects": subjects,
}
(here / "lowpoly_gallery_titans.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=1), encoding="utf-8")
print([s["id"] for s in subjects])
