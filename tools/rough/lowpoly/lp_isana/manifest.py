"""manifest 書き出し。python tools/rough/lowpoly/lp_isana/manifest.py (sheet.py のあと)"""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[4]
R = ROOT / "docs/review/lowpoly_roughs"
t = {k: json.load(open(R / "lp_isana" / k / f"whale_chaser_{k}_lowpoly_mesh.json"))["triangle_count"] for k in "abc"}
man = {
    "id": "lp_isana", "short": "イサナ 低ポリ", "kind": "低ポリ 3案",
    "title": "イサナ (whale_chaser) — 低ポリ非立方体 + ドット絵 3 案 (鯨追い・巨神へ導く旅人)",
    "sheets": ["lowpoly_roughs/lp_isana.png"],
    "extra": "統一モデル様式 (低ポリ + 1 ブロック 32 テクセルのドット絵) のイサナ。選んだ「つば広帽の旅人」(つばが額に影を落とす・無精髭・細めた目・小さな荷) の人物像を守った 3 つの解釈。"
             "骨 11 本 (root[Y 180 度] > body > head > hat / hair / hood2、body > armL / armR / legL / legR / pack) の名前・階層・pivot は今の geo のまま、今のアニメ 3 本 (idle・walk・talk) がそのまま動く。"
             "顔は頭の面に描いた画素 (白目・黒目・まぶた・眉・髭・口) と鼻の小さな三角錐で、頭の中に収まり浮いた板なし。光る画素なし (glowmask は置かない)。"
             f"三角形 (全身): A {t['a']:,}、B {t['b']:,}、C {t['c']:,} (NPC の目安 約 800 以下)。アトラス A・C 128x128、B 256x256。今は箱 690 個。"
             "シートは今 / A / B / C を前・前 3/4 (待機)・横・後ろ・顔のアップ・顔の 3/4・話す・歩く・アトラスで並べる。"
             "メッシュは lowpoly_roughs/lp_isana/{a,b,c}/whale_chaser_{a,b,c}_lowpoly_mesh.json (+ atlas)。生成: tools/rough/lowpoly/lp_isana/sheet.py と manifest.py。",
    "options": {
        "a": {"name": "A つば広帽の旅人",
              "desc": "今の A に忠実。深緑の長い外套 (縦の縫い目・裾が広がる)・赤い襟巻き (二本の尾)・茶の帽子に帯と白い羽・革の帯と留め金・灰色のズボンと茶の長靴。"
                      "背に茶の荷と巻いた寝袋。帽子の影で額が暗く、細めた目と無精髭。三案のうち最も今の面影が残る。"},
        "b": {"name": "B 荒くれの鯨獲り",
              "desc": "鯨を追って長い、くたびれた旅人。紺の油布の長い外套 (高い襟・ぎざぎざの裾) に灰の襟巻きと骨の留め具、胸を斜めに銛の綱が通る。"
                      "低くしおれた灰褐色の帽子。顔は灰色の髭・頬の古傷・眉間と目尻のしわ。背に小さな荷と、頭の上へ立つ銛 (返し付き) と綱の束。一番影が重く、鯨獲りの職が見える。"},
        "c": {"name": "C 若い旅人",
              "desc": "軽装の若いイサナ。青緑の短いジャケット (裾は腰) と明るいズボン、細いつばの麦わら帽に橙の帯。長い黄の襟巻きが背へ流れる。"
                      "目は大きめで眉も穏やか、口はわずかに上がり、頬にそばかす、髭はほとんどない。腰に真鍮の望遠鏡 (巨神を探す)、背に小さな荷と寝袋。一番親しみやすい。"},
    },
}
json.dump(man, open(R / "lp_isana_manifest.json", "w", encoding="utf-8"), ensure_ascii=False, indent=2)
print(t)
