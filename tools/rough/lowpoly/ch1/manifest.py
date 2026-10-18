# -*- coding: utf-8 -*-
# 一章の新しい中身 (敵・人物・旅装・アイコン) のラフ 3 案の選定ページの manifest をまとめる
# 実行: python tools/rough/lowpoly/ch1/manifest.py
#   -> docs/review/ch1_roughs_manifest.json
#   -> python tools/rough/build_gallery.py docs/review/ch1_roughs_manifest.json docs/review _gallery_ch1
import json
from pathlib import Path

root = Path(__file__).resolve().parents[4]
d = root / "docs/review/ch1_roughs"
order = ["rock_shell_beetle", "spine_hare", "bone_hauler", "elder_sentry", "bone_mortar", "bone_gull", "elder_sentinel",
         "artisan", "artisan_colorways", "armor_ch1", "icons_notebook", "icons_pulse"]
subjects = []
for o in order:
    p = d / f"{o}_manifest.json"
    if p.exists():
        subjects.append(json.loads(p.read_text(encoding="utf-8")))
    else:
        print("missing", o)
manifest = {
    "title": "一章の新しい敵・人物・旅装・アイコン ラフ 3 案",
    "intro": "一章 (岩鯨) の新しい中身のラフです (設計書 21)。色の言葉は石・骨・苔・琥珀の目・茶の岩殻の割れ目。"
             "敵は地上の 4 体 (岩殻虫・棘うさぎ・骨負い・老いた歩哨) と背中の 3 体 (骨の砲手・骨鳥・老番兵)、人物は旅の職人 (3 案 + 4 章の色)、"
             "防具は鯨道の旅装 (防具 C の様式)、アイコンは旅の覚書と鼓動守 (2 案ずつ)。"
             "敵の列は全身の前・横・斜めと、攻撃の予告の姿勢まで並べています (左の黄色い目盛りは人の高さ 1.8 ブロック)。"
             "老いた歩哨と老番兵は元の骨の番兵に部品を足した変種で、A・B・C の作りは 2 体で同じです。採用する案を選び、直したい点があれば書いてください。",
    "done": [],
    "subjects": subjects,
}
(root / "docs/review/ch1_roughs_manifest.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=1), encoding="utf-8")
print(len(subjects), "subjects")
