#!/usr/bin/env python3
"""三章の備えの品の絵を入れる (21 の 1.2・5.1。仮の選定。ユーザーの確認待ち)。

  python -I tools/rough/lowpoly/ch3/armor_ch3.py      # 旅装のラフ 3 案 (先に)
  python -I tools/rough/lowpoly/ch3/icons_ch3.py      # 小物のラフ 3 案ずつ (先に)
  python -I tools/install_ch3_gear_assets.py

  峰越えの旅装 (CHOICE["peak_crossing"]。docs/review/ch3_roughs/items/armor_ch3/<案>):
    assets/skyabyss/lowpoly/armor/peak_crossing/<部位>.json                メッシュ (水密に閉じる。階級はアンコモン = 無印)
    assets/skyabyss/textures/lowpoly/armor/peak_crossing/<部位>.png と _glowmask.png (雷の光の留め金と糸)
    assets/skyabyss/textures/item/peak_crossing_<部位>.png + models/item/peak_crossing_<部位>.json   インベントリのアイコン
  小物 8 つ (CHOICE[品]。docs/review/ch3_roughs/items/icons/<品>_<案>_32.png) -> textures/item/<品>.png + models/item/<品>.json
  雷の刃・避雷の効き目 (icons/thunder_whetted_18.png・grounded_18.png) -> textures/mob_effect/
  3 案の元絵は tools/sources/<品>/rough_{a,b,c}.png に残す (選ばなかった案の控え。選び直すときは CHOICE を替えて流し直す)。何度流しても同じ。
"""
import json
import os
import shutil
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import mesh_close  # noqa: E402  (書き出す前に水密にする)

import numpy as np
from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.path.join(ROOT, "docs", "review", "ch3_roughs", "items")
ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets", "skyabyss")
SOURCES = os.path.join(ROOT, "tools", "sources")
PIECES = ["helmet", "chestplate", "leggings", "boots"]

# 仮の選定 (ユーザーの確認待ち)
CHOICE = {
    "peak_crossing": "a",        # A 羽の肩
    "scorched_feather": "b",     # 青い筋の黒い羽
    "static_cotton": "b",        # 三つの房と稲妻
    "copper_wire": "b",          # 木の芯に巻く
    "thunder_ward_thread": "b",  # 稲妻の房の護り結び
    "thunder_whetstone": "b",    # 黒い石に光るひび
    "wind_dumpling": "a",        # 串の三つ
    "lightning_oil": "a",        # 硝子の小瓶
    "wind_streamer": "a",        # 白い吹き流し
}


def write_json(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(data, f, indent=2)
        f.write("\n")


def item_model(name):
    write_json(os.path.join(ASSETS, "models", "item", name + ".json"),
               {"parent": "minecraft:item/generated", "textures": {"layer0": "skyabyss:item/" + name}})


def armor():
    o = CHOICE["peak_crossing"]
    mesh_dir = os.path.join(ASSETS, "lowpoly", "armor", "peak_crossing")
    tex_dir = os.path.join(ASSETS, "textures", "lowpoly", "armor", "peak_crossing")
    os.makedirs(mesh_dir, exist_ok=True)
    os.makedirs(tex_dir, exist_ok=True)
    tris = 0
    for p in PIECES:
        base = os.path.join(SRC, "armor_ch3", o)
        d = json.load(open(os.path.join(base, p + "_mesh.json"), encoding="utf-8"))
        d.pop("triangle_count", None)
        d.pop("glowmask", None)
        d["texture"] = "armor/peak_crossing/" + p
        d["socket"] = False
        for b in d["bones"].values():
            b.pop("triangle_count", None)
        tris += sum(len(b["triangles"]) for b in d["bones"].values())
        d, _ = mesh_close.close_mesh(d)
        with open(os.path.join(mesh_dir, p + ".json"), "w", encoding="utf-8") as f:
            json.dump(d, f, separators=(",", ":"))
        img = Image.open(os.path.join(base, p + "_atlas.png")).convert("RGBA")
        mask = np.array(Image.open(os.path.join(base, p + "_glowmask.png")).convert("RGBA"))[..., 3] > 0
        if not mask.any():
            sys.exit(p + ": no glow pixels (GeckoLib crashes on an empty glowmask)")
        glow = np.array(img)
        glow[~mask] = 0
        img.save(os.path.join(tex_dir, p + ".png"), optimize=True)
        Image.fromarray(glow, "RGBA").save(os.path.join(tex_dir, p + "_glowmask.png"), optimize=True)
        shutil.copyfile(os.path.join(SRC, "armor_ch3", "icons", "%s_%s_32.png" % (o, p)),
                        os.path.join(ASSETS, "textures", "item", "peak_crossing_%s.png" % p))
        item_model("peak_crossing_" + p)
    # 3 案の控え (アイコンとシート)
    keep = os.path.join(SOURCES, "peak_crossing")
    os.makedirs(keep, exist_ok=True)
    for opt in "abc":
        for p in PIECES:
            shutil.copyfile(os.path.join(SRC, "armor_ch3", "icons", "%s_%s_32.png" % (opt, p)), os.path.join(keep, "rough_%s_%s.png" % (opt, p)))
    shutil.copyfile(os.path.join(SRC, "armor_ch3.png"), os.path.join(keep, "sheet.png"))
    return tris


def small_items():
    tex = os.path.join(ASSETS, "textures", "item")
    for name, o in CHOICE.items():
        if name == "peak_crossing":
            continue
        shutil.copyfile(os.path.join(SRC, "icons", "%s_%s_32.png" % (name, o)), os.path.join(tex, name + ".png"))
        item_model(name)
        keep = os.path.join(SOURCES, name)
        os.makedirs(keep, exist_ok=True)
        for opt in "abc":
            shutil.copyfile(os.path.join(SRC, "icons", "%s_%s_32.png" % (name, opt)), os.path.join(keep, "rough_%s.png" % opt))
    eff = os.path.join(ASSETS, "textures", "mob_effect")
    os.makedirs(eff, exist_ok=True)
    shutil.copyfile(os.path.join(SRC, "icons", "thunder_whetted_18.png"), os.path.join(eff, "thunder_whetted.png"))
    shutil.copyfile(os.path.join(SRC, "icons", "grounded_18.png"), os.path.join(eff, "grounded.png"))


def main():
    tris = armor()
    small_items()
    print("peak crossing armor (%s): 4 meshes (%d triangles), atlases, glow masks, 4 icons; 8 item icons; 2 effect icons"
          % (CHOICE["peak_crossing"], tris))


if __name__ == "__main__":
    main()
