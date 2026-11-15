# -*- coding: utf-8 -*-
"""心臓の殻の骨を「heart」の骨の子にする (岩鯨の心臓の第一段・第二段、燠の心臓の全段)。
燠の心臓も同じで、第一段の殻 (crust)・第三段の灰の殻 (ash_shell) が heart の兄弟だったので、殻の段では体がほとんど動かなかった。
以下は岩鯨の心臓の説明:
岩鯨の心臓の第一段・第二段で、殻 (板・冠・棘・継ぎ目・ひび・血管) を「heart」の骨の子にする。
動き (stone_whale_heart.animation.json) の脈打ち・突き・薙ぎ・跳ねは heart の骨に付いている。第三段は heart が体そのものだが、
第一段・第二段の heart は殻の中の小さな芯で、殻は別の骨なので、殻を閉じたままの段では体がほとんど動かなかった
(2026-11-15 の心臓の動きの調べ docs/review/heart_anim)。殻の骨を heart の子にすると、殻ごと脈打ち・突き出る。
板ごとの開き (咆哮・ひび) は板の骨に残るので、heart の動きの上に重なる。
メッシュを作り直す道具 (install_heart_v4.py など) を流したあとは、これも流し直す。
使い方: python tools/install/heart_shell_parent.py
"""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
TABLE = {
    "stone_whale_heart": ((1, 2), ["crown", "spikes", "heart_veins", "seams", "cracks", "crack_3", "crack_7"] + ["plate_%d" % i for i in range(8)]),
    "ember_heart": ((1, 2, 3), ["crust", "crust_glow", "heart_crust", "ash_shell", "drips", "ash_drips"]),
}

for heart_name, (phases, SHELL) in TABLE.items():
    for p in phases:
        path = ROOT / ("src/main/resources/assets/skyabyss/lowpoly/%s_p%d.json" % (heart_name, p))
        d = json.loads(path.read_text(encoding="utf-8"))
        bones = d["bones"]
        changed = 0
        for bone in SHELL:
            b = bones.get(bone)
            if b is not None and b["parent"] in (None, "flinch") and "heart" in bones:
                b["parent"] = "heart"
                changed += 1
        if "heart" in bones:
            # 親の骨を先に並べる (読み手が順に親をたどる)
            d["bones"] = {k: bones[k] for k in ["heart"] + [k for k in bones if k != "heart"]}
        path.write_text(json.dumps(d, ensure_ascii=False), encoding="utf-8")
        print("%s p%d mesh: %d bones now follow heart" % (heart_name, p, changed))
        # 動きの骨の親子は GeckoLib の骨だけの geo (geo/entity/<心臓>_pN_lowpoly(_debug).geo.json) が決める
        for suffix in ("_lowpoly.geo.json", "_lowpoly_debug.geo.json"):
            gpath = ROOT / ("src/main/resources/assets/skyabyss/geo/entity/%s_p%d%s" % (heart_name, p, suffix))
            if not gpath.exists():
                continue
            g = json.loads(gpath.read_text(encoding="utf-8"))
            gb = g["minecraft:geometry"][0]["bones"]
            if not any(b["name"] == "heart" for b in gb):
                print("  no heart bone in", gpath.name)
                continue
            n = 0
            for b in gb:
                if b["name"] in SHELL and b.get("parent") in (None, "flinch"):
                    b["parent"] = "heart"
                    n += 1
            heart = [b for b in gb if b["name"] == "heart"]
            rest = [b for b in gb if b["name"] != "heart"]
            # heart を flinch (根) のすぐ後へ
            g["minecraft:geometry"][0]["bones"] = rest[:1] + heart + rest[1:]
            gpath.write_text(json.dumps(g, ensure_ascii=False, separators=(",", ":")), encoding="utf-8")
            print("%s p%d %s: %d bones now follow heart" % (heart_name, p, suffix, n))
