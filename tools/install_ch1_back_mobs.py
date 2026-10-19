#!/usr/bin/env python3
"""岩鯨の背の新しい敵 3 体の低ポリを入れる (選んだ案: 骨の砲手 A・骨鳥 A・老番兵 B。21 の 3.3、15 の 9.6)。

    python tools/install_ch1_back_mobs.py

入力は docs/review/ch1_roughs/<名>/<案>/ のメッシュ・アトラス・光る画像。ラフの案違いの空の骨 (cannon_b など、頂点 0 で子もない骨) は
落として入れる (骨の名前と枢軸はラフのまま。砲身 cannon の枢軸は台座の上 (0,7,-0.5)、頭 head は盾の奥 (-3.5,7.2,-1.5)、
骨鳥の翼は胴の側面、老番兵は骨の番兵と同じ骨の名前と親)。何度流してもよい。
元のキューブのモデルは無いので、LowPoly.enabled = false のときの戻り先として、骨だけの geo (<名>.geo.json) と
アトラスのコピー (textures/entity/<名>.png) を置く (戻すとキューブが無いので体は描かれない。落ちないための置き物)。
動き (animations/entity/<名>.animation.json) は tools/gen_ch1_back_mob_anims.py が書く。
"""
import json
import os
import shutil
import subprocess
import sys
import tempfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets", "skyabyss")
R = os.path.join(ROOT, "docs", "review", "ch1_roughs")

JOBS = [
    ("bone_mortar", "bone_mortar", "a"),
    ("bone_gull", "bone_gull", "a"),
    ("elder_sentinel", "elder_sentinel", "b"),
]


def main():
    tmp = tempfile.mkdtemp(prefix="ch1back_")
    for name, folder, opt in JOBS:
        d = os.path.join(R, folder, opt)
        stem = "%s_%s_" % (folder, opt)
        mesh = json.load(open(os.path.join(d, stem + "mesh.json"), encoding="utf-8"))
        parents = {b.get("parent") for b in mesh["bones"].values()}
        for n in [n for n, b in mesh["bones"].items() if not b["vertices"] and n not in parents]:
            print("drop empty bone", n)
            del mesh["bones"][n]
        cleaned = os.path.join(tmp, name + "_mesh.json")
        json.dump(mesh, open(cleaned, "w", encoding="utf-8"))
        subprocess.check_call([sys.executable, os.path.join(ROOT, "tools", "install_lowpoly.py"), name, "--mesh", cleaned,
                               "--atlas", os.path.join(d, stem + "atlas.png"), "--glow", os.path.join(d, stem + "glowmask.png")])
        geo_dir = os.path.join(ASSETS, "geo", "entity")
        skeleton = json.load(open(os.path.join(geo_dir, name + "_lowpoly.geo.json"), encoding="utf-8"))
        skeleton["minecraft:geometry"][0]["description"]["identifier"] = "geometry." + name
        json.dump(skeleton, open(os.path.join(geo_dir, name + ".geo.json"), "w", encoding="utf-8"), separators=(",", ":"))
        tex = os.path.join(ASSETS, "textures", "entity")
        os.makedirs(tex, exist_ok=True)
        shutil.copyfile(os.path.join(d, stem + "atlas.png"), os.path.join(tex, name + ".png"))
    shutil.rmtree(tmp, ignore_errors=True)


if __name__ == "__main__":
    main()
