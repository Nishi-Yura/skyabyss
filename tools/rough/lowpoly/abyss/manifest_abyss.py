# -*- coding: utf-8 -*-
# 深淵の敵 9 体のラフ 3 案の manifest をまとめる (設計書 36 の 4、A3)
# 実行: python -I tools/rough/lowpoly/abyss/manifest_abyss.py
#   -> docs/review/abyss_roughs/abyss_manifest.json
#   -> python -I tools/rough/build_gallery.py docs/review/abyss_roughs/abyss_manifest.json docs/review _gallery_abyss
import json
from pathlib import Path

root = Path(__file__).resolve().parents[4]
d = root / "docs/review/abyss_roughs"
order = [
    # 骨の層
    "bone_picker", "rib_guard", "bone_flute",
    # 塩の層
    "salt_husk", "tide_hands", "white_shell_keeper",
    # 雷の層
    "charged_moth", "lightning_idol", "scorched_bird",
]
subjects = []
for o in order:
    p = d / f"{o}_manifest.json"
    if p.exists():
        subjects.append(json.loads(p.read_text(encoding="utf-8")))
    else:
        print("missing", o)
manifest = {
    "title": "深淵の敵 9 体 ラフ 3 案 (骨・塩・雷の層の基本の姿)",
    "intro": "五章「深淵」の敵のラフです (設計書 36 の 4、A3)。層ごとに 3 種ずつ: 骨 (骨拾い・肋の番・骨笛)、塩 (塩の抜け殻・潮だまりの手・白い貝守)、雷 (帯電の羽虫・避雷の像・焦げた鳥の影)。"
             "各案は基本の姿で、色の言葉は層ごと (骨は骨・方解石・灰、塩は白・塩の皮・海の緑、雷は凝灰岩・酸化した銅・白い雷)。"
             "「歪み」「崩れ」 (深いほど不気味な変わり果てた姿) は、各案の説明の末尾に一行ずつ書いてあります (今は決めるのは基本の案だけ)。"
             "メッシュは閉じた面 (開いた辺・向きの食い違い 0) で、光る材質は雷・鼓動の目印の所だけ。ラフなので登録・動きの実装はまだです。",
    "done": [],
    "subjects": subjects,
}
(d / "abyss_manifest.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=1), encoding="utf-8")
print(len(subjects), "subjects")
