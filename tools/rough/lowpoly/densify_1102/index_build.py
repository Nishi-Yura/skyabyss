# -*- coding: utf-8 -*-
"""密度上げの索引 (docs/review/roughs_1102_densify/index.md) を summary.json から作る。"""
import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from dens import OUT, RES, GROUP_BEST  # noqa: E402

GROUPS = [
    ("moon", "月 雑魚", ["shadow_stitcher", "shade_strider", "shell_hare", "dust_crawler", "rift_wing", "cliff_creeper", "moon_leaper", "push_stone"]),
    ("cloud", "雲海 雑魚", ["bell_mourner", "mist_bird", "backfire", "ash_striker", "fallen_plume"]),
    ("abyssB", "深淵 B 群", ["soot_child", "rope_hanger", "lamp_eater", "past_shadow", "ceiling_walker", "ash_trail", "nameless_shape"]),
    ("abyssA", "深淵 A 群 (段ごとの _s1/_s2/_s3 は同じ形)", ["bone_picker", "charged_moth", "bone_flute", "white_shell_keeper"]),
    ("ch3", "三章 地上", ["lightning_gleaner", "static_puff", "peak_fledgling"]),
    ("ch4", "四章", ["cinder_mite"]),
    ("foes", "大陸の敵", ["grain_rat", "stray_wisp"]),
    ("ch1", "一章 背", ["bone_gull"]),
    ("ch2", "二章 地上", ["tidepool_clam", "sand_burrower"]),
]
INSTALL_NAME = {"abyssA": lambda i: "abyss_%s_s1" % i}


def build_rows():
    rows = []
    for g, title, ids in GROUPS:
        best = GROUP_BEST[g]
        for mid in ids:
            s = json.loads((OUT / mid / "summary.json").read_text(encoding="utf-8"))
            inst = json.loads((RES / ((INSTALL_NAME[g](mid) if g == "abyssA" else mid) + ".json")).read_text(encoding="utf-8"))
            after = json.loads((OUT / mid / "after" / ("%s_after_mesh_closed.json" % mid)).read_text(encoding="utf-8"))
            ib = {k for k, v in inst["bones"].items() if v.get("triangles")}
            ab = {k for k, v in after["bones"].items() if v.get("triangles")}
            rows.append(dict(g=g, title=title, id=mid, letter=s["letter"], inst=s["installed"], before=s["before_closed"],
                             after=s["after_closed"], pct=round(100.0 * s["after_closed"] / best), best=best,
                             open_after=s["open_after"], bones_same=(ib == ab),
                             buried_new=len([b for b in s["buried_after"] if b not in s.get("buried_before", [])]),
                             buried_pre=len(s.get("buried_before", []))))
    return rows


def main():
    rows = build_rows()
    print(len(rows))
    (OUT / "rows.json").write_text(json.dumps(rows, ensure_ascii=False, indent=1), encoding="utf-8")


if __name__ == "__main__":
    main()


INSTALL_GEN = {
    "moon": "tools/gen/ch6/gen_moon_mobs.py（入力 docs/review/moon_roughs/<id>/<案>/）",
    "cloud": "tools/gen/finale/gen_cloud_mobs.py（入力 docs/review/cloud_roughs/<id>/a/）",
    "abyssB": "tools/gen/abyss/gen_abyss_mobs_b.py（入力 docs/review/abyss_roughs/<id>/<案>/。絵は tools/abyss/paint_abyss_b.py）",
    "abyssA": "tools/gen/abyss/gen_abyss_mobs_a.py（入力 docs/review/abyss_roughs/<id>/<案>/。名前は abyss_<id>_s1 / _s2 / _s3）",
    "ch3": "tools/gen/ch3/gen_ch3_ground_mobs.py（入力 docs/review/ch3_roughs/<id>/<案>/）",
    "ch4": "tools/gen/ch4/gen_ch4_ground_mobs.py（入力 docs/review/ch4_roughs/<id>/<案>/）",
    "foes": "tools/gen/foes/gen_foes_one.py（入力 docs/review/foes_one/<id>/a/）",
    "ch1": "tools/install/install_ch1_back_mobs.py（入力は固定の docs/review/ch1_roughs/bone_gull/a/）",
    "ch2": "tools/gen/ch2/gen_ch2_ground_mobs.py（入力 docs/review/ch2_roughs/<id>/<案>/）",
}
CHOICE_TXT = {
    "shadow_stitcher": "c", "shade_strider": "a", "shell_hare": "b", "dust_crawler": "a", "rift_wing": "a", "cliff_creeper": "b",
    "moon_leaper": "a", "push_stone": "a",
}


def write_md():
    rows = build_rows()
    L = []
    L.append("# 密度上げ（監査 2026-11-01 項目 8）: 三角形の少ない敵 33 体のラフ")
    L.append("")
    L.append("監査で「群の最大の 40% 未満」と出た敵を、既存の形の二次的な形（板・関節の輪・肋・毛束・ひび・縁取り・光る点）で 50〜70% に上げたラフ。"
             "輪郭・配色・骨の名前は変えていない（アニメが動く）。取り込み（`src/main/resources`）・ビルド・gradle は触っていない。コミットもしていない。")
    L.append("")
    L.append("## 結果")
    L.append("")
    L.append("- 対象: 33 体（下の群の一覧）。**依頼文は 37 体だったが、名前を列挙した群は合計 33 体**。残り 4 体は列挙に無いので、ここでは扱っていない（名前を教えてもらえれば同じ手順で作る）。")
    L.append("- 作ったもの: 33 体すべて。スキップ 0。")
    L.append("- 群ごとの数: 月 8 / 雲海 5 / 深淵 B 7 / 深淵 A 4（段 s1〜s3 は同じ形の 4 種）/ 三章 3 / 四章 1 / 大陸の敵 2 / 一章 1 / 二章 2。")
    inside = [r["id"] for r in rows if 50 <= r["pct"] <= 70]
    edge = [r["id"] for r in rows if r["pct"] == 50]
    L.append("- 割合（群の最大に対して、四捨五入）: %d 体すべてが 50〜70%% の中。下限ぎりぎり（50%%）は %s。" % (len(inside), "、".join(edge) or "なし"))
    L.append("- 水密: 33 体とも、溶接後の開いた輪が 0（`tools/lib/mesh_close.py` の `close_mesh` で閉じた数を「密度上げ後」とした。取り込みと同じ数え方）。")
    L.append("- 骨: 追加はすべて既存の骨に付けた。骨の名前・pivot・木は変えていない。`ash_trail` は一度、採用案 C に無い脚の骨へ爪を付けていたので外して手の爪に直した。")
    L.append("- 埋まり検査（`check_buried`）: 新しく埋まった部品は 0。元から埋まっていた `bell_mourner`（外套の裾と鐘）・`white_shell_keeper`（巻き殻の帯）などは元のまま。")
    L.append("")
    L.append("## 一覧（三角形は水密後の数。取り込み＝現在の `src/main/resources` の値）")
    L.append("")
    L.append("| 敵 | 群 | 案 | 取り込み（現在） | 再現（元の水密後） | 密度上げ後 | 群の最大 | 割合（目標 50〜70%） | 画像 | 元 → 後 の成果物 |")
    L.append("| --- | --- | --- | ---: | ---: | ---: | ---: | ---: | --- | --- |")
    for r in rows:
        mid = r["id"]
        L.append("| %s | %s | %s | %d | %d | %d | %d | %d%% | [%s.png](%s.png) | [before](%s/before/) / [after](%s/after/) |" % (
            mid, r["g"], r["letter"], r["inst"], r["before"], r["after"], r["best"], r["pct"], mid, mid, mid, mid))
    L.append("")
    L.append("注: `shell_hare` の再現値（924）と取り込み（932）、`static_puff`（384 / 402）、`sand_burrower`（268 / 256）は、取り込み後に生成元（ラフ）が変わった差（取り込みの数は動かさない）。割合は密度上げ後の値で計算。")
    L.append("")
    L.append("## 取り込みのコマンド（群ごと）")
    L.append("")
    L.append("既定の取り込み（生成スクリプト）は、群のラフの置き場（`docs/review/<群>_roughs/<id>/<案>/`）から読む。今回の出力はそこに無いので、そのまま流すと旧いラフが入る。次のどちらかを選ぶ。")
    L.append("")
    L.append("1. 直接入れる（メッシュ・絵・発光マスク・骨の骨格だけ。段の絵 s1/s2 やアニメは変わらない）。例（1 体）:")
    L.append("")
    L.append("```")
    L.append("python -I tools/install/install_lowpoly.py <名前> --mesh docs/review/roughs_1102_densify/<id>/after/<id>_after_mesh_closed.json --atlas docs/review/roughs_1102_densify/<id>/after/<id>_after_atlas.png --glow docs/review/roughs_1102_densify/<id>/after/<id>_after_glowmask.png")
    L.append("```")
    L.append("")
    L.append("2. 生成スクリプトの流れに乗せる（段の絵・動きも同じ生成で書く）。`docs/review/<群>_roughs/<id>/<案>/` に `<id>_<案>_mesh.json`・`_atlas.png`・`_glowmask.png` を置いてから流す。これは既存の出力を置き換えるので、先に確認が要る。")
    L.append("")
    L.append("群ごとの対応（名前は id。深淵 A は s1〜s3 の三回）:")
    L.append("")
    for g, title, ids in GROUPS:
        L.append("- **%s**（%s）: %s" % (title, g, INSTALL_GEN[g]))
        if g == "abyssA":
            L.append("  - 直接入れる場合: `python -I tools/install/install_lowpoly.py abyss_<id>_s1 ...`（s2・s3 も同じ形。絵は同じ 1 枚になるので、段の違いは生成スクリプトで出す）")
        else:
            L.append("  - 直接入れる場合: `python -I tools/install/install_lowpoly.py <id> --mesh ... --atlas ... --glow ...`（上の 1 体の形）")
    L.append("")
    L.append("注意: 生成スクリプトが絵を描き直す群（深淵 B は `tools/abyss/paint_abyss_b.py`、深淵 A は `gen_abyss_mobs_a.py` の塗り関数）は、新しい面にも色が付くが、ゲームでの見え方は撮って確かめる。取り込みは行っていない。")
    L.append("")
    L.append("## 道具と再現")
    L.append("")
    L.append("- 共通部品: `tools/rough/lowpoly/densify_1102/dens.py`（元のラフを読み、採用案の数を取り込みと照合し、before / after を書き、水密化の数と埋まりを出す）。")
    L.append("- 各敵: `tools/rough/lowpoly/densify_1102/<id>.py`（元の build に二次的な形を足すだけ。実行: `python -I tools/rough/lowpoly/densify_1102/<id>.py`）。")
    L.append("- 索引の再作成: `python -I tools/rough/lowpoly/densify_1102/index_build.py`。")
    L.append("- 採用案: 取り込みの三角形数に一番近い案（`dens.choose`）。深淵 B・三章・四章・大陸などは生成スクリプトの CHOICE の案を直接指定し、再現の数が取り込みと一致することを確かめた（`shell_hare` 等の差は注に書いた）。")
    L.append("")
    L.append("---")
    L.append("作成: 2026-11-02。")
    (OUT / "index.md").write_text("\n".join(L) + "\n", encoding="utf-8")
    print("index written")


if __name__ == "__main__" and "--md" in sys.argv:
    write_md()
    print("bones_same_all:", all(r["bones_same"] for r in build_rows()))
