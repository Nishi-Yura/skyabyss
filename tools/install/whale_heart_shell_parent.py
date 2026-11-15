# -*- coding: utf-8 -*-
"""岩鯨の心臓の第一段・第二段で、殻 (板・冠・棘・継ぎ目・ひび・血管) を「heart」の骨の子にする。
動き (stone_whale_heart.animation.json) の脈打ち・突き・薙ぎ・跳ねは heart の骨に付いている。第三段は heart が体そのものだが、
第一段・第二段の heart は殻の中の小さな芯で、殻は別の骨なので、殻を閉じたままの段では体がほとんど動かなかった
(2026-11-15 の心臓の動きの調べ docs/review/heart_anim)。殻の骨を heart の子にすると、殻ごと脈打ち・突き出る。
板ごとの開き (咆哮・ひび) は板の骨に残るので、heart の動きの上に重なる。
メッシュを作り直す道具 (install_heart_v4.py など) を流したあとは、これも流し直す。
使い方: python tools/install/whale_heart_shell_parent.py
"""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SHELL = ["crown", "spikes", "heart_veins", "seams", "cracks", "crack_3", "crack_7"] + ["plate_%d" % i for i in range(8)]

for p in (1, 2):
    path = ROOT / ("src/main/resources/assets/skyabyss/lowpoly/stone_whale_heart_p%d.json" % p)
    d = json.loads(path.read_text(encoding="utf-8"))
    bones = d["bones"]
    changed = 0
    for name in SHELL:
        b = bones.get(name)
        if b is not None and b["parent"] is None:
            b["parent"] = "heart"
            changed += 1
    # 親の骨を先に並べる (読み手が順に親をたどる)
    order = ["heart"] + [k for k in bones if k != "heart"]
    d["bones"] = {k: bones[k] for k in order}
    path.write_text(json.dumps(d, ensure_ascii=False), encoding="utf-8")
    print("p%d mesh: %d bones now follow heart" % (p, changed))
    # 動きの骨の親子は GeckoLib の骨だけの geo (geo/entity/stone_whale_heart_pN_lowpoly(_debug).geo.json) が決める
    for suffix in ("_lowpoly.geo.json", "_lowpoly_debug.geo.json"):
        gpath = ROOT / ("src/main/resources/assets/skyabyss/geo/entity/stone_whale_heart_p%d%s" % (p, suffix))
        if not gpath.exists():
            continue
        g = json.loads(gpath.read_text(encoding="utf-8"))
        gb = g["minecraft:geometry"][0]["bones"]
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
        print("p%d %s: %d bones now follow heart" % (p, suffix, n))
