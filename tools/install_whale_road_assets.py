#!/usr/bin/env python3
"""一章の導きの絵を入れる (21 の P4・P5。ユーザーの選定: 旅装 = A 殻肩、旅の覚書 = A、鼓動守 = A)。

  python tools/install_whale_road_assets.py

  鯨道の旅装 (A 殻肩。docs/review/ch1_roughs/armor_ch1/a):
    assets/skyabyss/lowpoly/armor/whale_road/<部位>.json                 メッシュ (階級はアンコモン = 無印)
    assets/skyabyss/textures/lowpoly/armor/whale_road/<部位>.png と _glowmask.png (琥珀の留め金)
    assets/skyabyss/textures/item/whale_road_<部位>.png + models/item/whale_road_<部位>.json   インベントリのアイコン (icons/a_<部位>_32.png)
    骨だけの geo は共通の geo/armor/lowpoly_armor.geo.json (同じ 8 本の骨。tools/install_armor_lowpoly.py)
  旅の覚書 A (icons/notebook_a_32.png) -> textures/item/wayfarer_notes.png
  鼓動守 A (icons/pulse_ward_a_32.png) -> textures/item/heartbeat_ward.png (4 巨神共通。ラフは 1 枚だけ。巨神ごとに分けるなら models/item/heartbeat_ward_<titan>.json)
仮の絵 (右上のマゼンタの印) を置き換える。何度流しても同じ。
"""
import json
import os
import shutil
import sys

import numpy as np
from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.path.join(ROOT, "docs", "review", "ch1_roughs")
ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets", "skyabyss")
PIECES = ["helmet", "chestplate", "leggings", "boots"]


def write_json(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(data, f, indent=2)
        f.write("\n")


def main():
    mesh_dir = os.path.join(ASSETS, "lowpoly", "armor", "whale_road")
    tex_dir = os.path.join(ASSETS, "textures", "lowpoly", "armor", "whale_road")
    os.makedirs(mesh_dir, exist_ok=True)
    os.makedirs(tex_dir, exist_ok=True)
    tris = 0
    for p in PIECES:
        d = json.load(open(os.path.join(SRC, "armor_ch1", "a", p + "_mesh.json"), encoding="utf-8"))
        d.pop("triangle_count", None)
        d.pop("glowmask", None)
        d["texture"] = "armor/whale_road/" + p
        d["socket"] = False
        for b in d["bones"].values():
            b.pop("triangle_count", None)
        tris += sum(len(b["triangles"]) for b in d["bones"].values())
        with open(os.path.join(mesh_dir, p + ".json"), "w", encoding="utf-8") as f:
            json.dump(d, f, separators=(",", ":"))
        img = Image.open(os.path.join(SRC, "armor_ch1", "a", p + "_atlas.png")).convert("RGBA")
        mask = np.array(Image.open(os.path.join(SRC, "armor_ch1", "a", p + "_glowmask.png")).convert("RGBA"))[..., 3] > 0
        if not mask.any():
            sys.exit(p + ": no glow pixels (GeckoLib crashes on an empty glowmask)")
        glow = np.array(img)
        glow[~mask] = 0
        img.save(os.path.join(tex_dir, p + ".png"), optimize=True)
        Image.fromarray(glow, "RGBA").save(os.path.join(tex_dir, p + "_glowmask.png"), optimize=True)
        # インベントリのアイコン (32x32)
        icon = os.path.join(ASSETS, "textures", "item", "whale_road_%s.png" % p)
        shutil.copyfile(os.path.join(SRC, "armor_ch1", "icons", "a_%s_32.png" % p), icon)
        write_json(os.path.join(ASSETS, "models", "item", "whale_road_%s.json" % p),
                   {"parent": "minecraft:item/generated", "textures": {"layer0": "skyabyss:item/whale_road_" + p}})
    shutil.copyfile(os.path.join(SRC, "icons", "notebook_a_32.png"), os.path.join(ASSETS, "textures", "item", "wayfarer_notes.png"))
    shutil.copyfile(os.path.join(SRC, "icons", "pulse_ward_a_32.png"), os.path.join(ASSETS, "textures", "item", "heartbeat_ward.png"))
    print("whale road armor: 4 meshes (%d triangles), atlases, glow masks, 4 icons; notebook A and heartbeat ward A icons" % tris)


if __name__ == "__main__":
    main()
