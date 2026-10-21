# -*- coding: utf-8 -*-
# 二章の地上の敵 (P12) のラフ 3 案の選定ページの manifest をまとめる
# 実行: python -I tools/rough/lowpoly/ch2/manifest_mobs.py
#   -> docs/review/ch2_roughs/ch2_mobs_manifest.json
#   -> python -I tools/rough/build_gallery.py docs/review/ch2_roughs/ch2_mobs_manifest.json docs/review _gallery_ch2_mobs
import json
from pathlib import Path

root = Path(__file__).resolve().parents[4]
d = root / "docs/review/ch2_roughs"
order = ["tidepool_clam", "sand_burrower", "drift_hermit", "shore_warden"]
subjects = []
for o in order:
    p = d / f"{o}_manifest.json"
    if p.exists():
        subjects.append(json.loads(p.read_text(encoding="utf-8")))
    else:
        print("missing", o)
manifest = {
    "title": "二章の地上の敵 ラフ 3 案 (潮だまり貝・砂潜り・宿借り・潮の番人)",
    "intro": "二章 (潮殻) の地上の敵のラフです (設計書 21 の 4.1、P12)。色の言葉は珊瑚・真珠層・青緑・白い塩。"
             "敵の列は全身の前・横・斜めと、技の予告の姿勢まで並べています (左の黄色い目盛りは人の高さ 1.8 ブロック)。"
             "潮の番人は珊瑚憑きに部品を足した変種で、銛は三案共通です。"
             "実装は仮の選定 (貝 A・砂潜り C・宿借り B・番人 A) で入れてあります。別の案を選ぶときは tools/gen_ch2_ground_mobs.py の CHOICE を変えて流し直します。",
    "done": [],
    "subjects": subjects,
}
(d / "ch2_mobs_manifest.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=1), encoding="utf-8")
print(len(subjects), "subjects")
