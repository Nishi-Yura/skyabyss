"""マニフェストを書く: python manifest.py (compare.py のあとで)"""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[4]
LOW = ROOT / "docs/review/lowpoly_roughs"
tri = {s: json.loads((LOW / f"lp_cinder_roller/{s}/cinder_roller_lowpoly_mesh.json").read_text(encoding="utf-8"))["triangle_count"] for s in "abc"}
m = {
    "id": "lp_cinder_roller", "short": "燠虫", "kind": "低ポリ 3案", "title": "燠虫 (cinder_roller) 低ポリ・ドット絵 3案",
    "sheets": ["lowpoly_roughs/lp_cinder_roller.png"],
    "extra": f"団子虫が丸まって転がる敵。熾の巨人の言葉 (灰・熾・玄武の板・冷える溶岩・熱の割れ目) で、潮殻の蟹に見えない形に描き直した。骨 37 本の名前・階層・pivot・休めの回転は今の geo と同じで、今のアニメ (歩く・丸まる・転がる・開く・目を回す) がそのまま動く。1ブロック=32テクセル・最近傍・限られたパレット。三角形 A {tri['a']} / B {tri['b']} / C {tri['c']} (目安 約600)。各節の甲は x 軸まわりの回転体の板 (外の層・段・次の節の下へ上がる溶岩の床) で作り、玉では40度ごとに隙間も交差もなく一周する (殻の面どうしの交差は休め・玉とも0を検査)。玉では脚・触角・尾・膜の骨を隠す想定。光る画素は熱い割れ目・継ぎ目・腹・目・触角の先。メッシュ・アトラス・glowmask は docs/review/lowpoly_roughs/lp_cinder_roller/{{a,b,c}}/ 。",
    "options": {
        "a": {"name": f"A 玄武の層鎧 ({tri['a']} 三角形)", "desc": "広く平たい玄武の板が屋根瓦のように重なる、青黒い石の団子虫。柱状節理のような継ぎ目にところどころ熱い割れ目が走り、板のすき間と腹は溶岩色。頭は低い兜形で目は琥珀色。今の「玄武の殻」に最も近く、落ち着いて読みやすい。"},
        "b": {"name": f"B 熔皮の棘玉 ({tri['b']} 三角形)", "desc": "丸く盛り上がった冷えかけの溶岩の皮殻。背に一節一本の棘 (先が熱く光る)、割れ目から赤く透ける。頭は尖った鼻づらと長い触角、尾は長い二本の針。丸まると棘の玉になり、転がる姿が危険に見える。三案でいちばん派手で発光が多い。"},
        "c": {"name": f"C 灰炭の団子 ({tri['c']} 三角形)", "desc": "黒い炭のかたまり。ワニ皮のような細かい割れ目の交点だけ熾が赤く光り、上面に灰が積もる。背に灰の板 (縁だけ熾) が一列に立つ。ふっくらした丸い体と短い脚で、丸まるとずんぐりした灰まみれの玉。発光は控えめ。"},
    },
}
(LOW / "lp_cinder_roller_manifest.json").write_text(json.dumps(m, ensure_ascii=False, indent=1), encoding="utf-8")
j = json.loads((LOW / "lp_cinder_roller/a/cinder_roller_lowpoly_mesh.json").read_text(encoding="utf-8"))
print(tri, len(j["bones"]), j["bones"]["root"].get("rotation"))
