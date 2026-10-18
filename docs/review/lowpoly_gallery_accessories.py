# -*- coding: utf-8 -*-
# 雷翼・余燼の装飾品の低ポリ 3 案の選定ページの manifest をまとめる
import json
from pathlib import Path

here = Path(__file__).parent
sub = json.loads((here / "lowpoly_roughs" / "accessories_manifest.json").read_text(encoding="utf-8"))
manifest = {
    "title": "雷翼・余燼の装飾品 低ポリ 3案",
    "intro": "雷翼と余燼の装飾品9品（護符・足環・羽根・風切りの羽根・外套・灯・角杯・吊り灯）を、武器と防具に合わせた3案で作りました。"
             "A は羽の重ね・割れ板で武器にいちばん近い案、B は稲妻の折れ・鉄枠で巨神ごとの見分けが強い案、C は翼の輪・岩塊の丸い案です。"
             "階級（通常・レア・伝説）の見た目は B で例を作りました。岩鯨・潮殻の装飾品も今は平らな絵のままなので、選んだ方向で合わせて作ります。",
    "done": [
        "武器13本・防具C・巨神4体・敵・人物をすべて低ポリにした",
    ],
    "subjects": [sub],
}
(here / "lowpoly_gallery_accessories.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=1), encoding="utf-8")
print(sub["id"])
