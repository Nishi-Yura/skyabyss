# -*- coding: utf-8 -*-
"""封の箱 (docs/design/52) の低ポリと絵を assets へ入れる。

入力 (先に作る):
  python -I tools/rough/lowpoly/weapons/wild/build_wild.py o_boxes   形 11 種 → docs/review/roughs_wild/raw/box_<形>/a/
出力:
  形 (9): lowpoly/items/<形>/common.json・textures/lowpoly/items/<形>/common_atlas.png (+ _glow)、models/item_flat/<形>.json
          (手・地面・額縁は client/accessory/AccessoryItemRenderer の低ポリ。形の名前は BoxKind.look(): 巨神の箱と金の箱は品ごと、階級の箱は階級ごと)
  品 (19): textures/item/<品>.png (32x32。階級の箱は左下に帯の数字 I〜V を足す)・models/item/<品>.json (GUI は平絵、ほかは低ポリ)
使い方: python -I tools/install/install_boxes.py
"""
from __future__ import annotations

import json
import shutil
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[1]
sys.path.insert(0, str(HERE))
sys.path.insert(0, str(HERE.parent / "lib"))
import mesh_close  # noqa: E402
from install_wild_gear import bake_glow, ACC_DISPLAY  # noqa: E402
from PIL import Image  # noqa: E402

ASSETS = ROOT / "src/main/resources/assets/skyabyss"
RAW = ROOT / "docs/review/roughs_wild/raw"
LOOKS = {"box_stone_whale": "rare", "box_tide_shell": "rare", "box_thunder_wing": "rare", "box_ember": "rare",
         "box_uncommon": "rare", "box_rare": "rare", "box_epic": "rare", "box_heroic": "legend", "box_artifact": "legend", "box_tower": "legend", "box_cloud": "legend"}
# 品 → 形 (と帯)。loot/box/BoxKind.ALL と同じ並び
ITEMS = [("box_stone_whale", "box_stone_whale", 0), ("box_tide_shell", "box_tide_shell", 0), ("box_thunder_wing", "box_thunder_wing", 0),
         ("box_ember", "box_ember", 0)]
for tier, bands in (("uncommon", (1, 2)), ("rare", (1, 2, 3, 4, 5)), ("epic", (2, 3, 4, 5)), ("heroic", (3, 4, 5))):
    for b in bands:
        ITEMS.append((f"box_{tier}_{b}", f"box_{tier}", b))
ITEMS.append(("box_artifact", "box_artifact", 0))
ITEMS.append(("box_tower", "box_tower", 0))
ITEMS.append(("box_cloud", "box_cloud", 0))
TIER_RGB = {"uncommon": (123, 214, 107), "rare": (90, 169, 255), "epic": (185, 140, 255), "heroic": (255, 122, 61)}
# 3x5 の数字 (I・V だけで書く)
GLYPH = {"I": ["#", "#", "#", "#", "#"], "V": ["#.#", "#.#", "#.#", "#.#", ".#."]}
NUMERAL = {1: "I", 2: "II", 3: "III", 4: "IV", 5: "V"}


def install_look(look, bt):
    src = RAW / look / "a"
    mesh_dir = ASSETS / "lowpoly/items" / look
    tex_dir = ASSETS / "textures/lowpoly/items" / look
    mesh_dir.mkdir(parents=True, exist_ok=True)
    tex_dir.mkdir(parents=True, exist_ok=True)
    for f in list(mesh_dir.glob("*.json")) + list(tex_dir.glob("*.png")):
        f.unlink()
    mesh = json.loads((src / f"{bt}_mesh.json").read_text(encoding="utf-8"))
    mesh["texture"] = f"items/{look}/common_atlas"
    mesh.pop("glowmask", None)
    mesh, _ = mesh_close.close_mesh(mesh)
    (mesh_dir / "common.json").write_text(json.dumps(mesh, separators=(",", ":")), encoding="utf-8")
    shutil.copyfile(src / f"{bt}_atlas.png", tex_dir / "common_atlas.png")
    glow, lit = bake_glow(src / f"{bt}_atlas.png", src / f"{bt}_glowmask.png")
    if lit > 0:
        glow.save(tex_dir / "common_atlas_glow.png")
    shutil.copyfile(src / "icon.png", ASSETS / "textures/item" / f"{look}.png")
    flat = ASSETS / "models/item_flat"
    flat.mkdir(parents=True, exist_ok=True)
    (flat / f"{look}.json").write_text(json.dumps({"parent": "minecraft:item/generated", "textures": {"layer0": f"skyabyss:item/{look}"}},
                                                  separators=(",", ":")), encoding="utf-8")


def band_icon(look, band, tier):
    """形の絵の左下に、帯の数字を階級の色で (黒い縁つき)"""
    im = Image.open(ASSETS / "textures/item" / f"{look}.png").convert("RGBA")
    text = NUMERAL[band]
    cols = []
    for i, ch in enumerate(text):
        if i:
            cols.append(".")
        g = GLYPH[ch]
        for c in range(len(g[0])):
            cols.append("".join(row[c] for row in g))
    w = len(cols)
    x0, y0 = 1, 32 - 5 - 1
    px = im.load()
    on = {(x0 + c, y0 + r) for c, col in enumerate(cols) for r, v in enumerate(col) if v == "#"}
    for (x, y) in on:
        for dx in (-1, 0, 1):
            for dy in (-1, 0, 1):
                q = (x + dx, y + dy)
                if 0 <= q[0] < 32 and 0 <= q[1] < 32 and q not in on:
                    px[q] = (14, 10, 22, 255)
    for (x, y) in on:
        px[x, y] = TIER_RGB[tier] + (255,)
    assert w <= 12
    return im


def install_item(item, look, band):
    tex = ASSETS / "textures/item" / f"{item}.png"
    if band:
        band_icon(look, band, look[4:]).save(tex)
    elif item != look:
        shutil.copyfile(ASSETS / "textures/item" / f"{look}.png", tex)
    t = f"skyabyss:item/{item}"
    model = {"loader": "neoforge:separate_transforms",
             "base": {"parent": "builtin/entity", "gui_light": "front", "display": ACC_DISPLAY, "textures": {"particle": t}},
             "perspectives": {"gui": {"parent": "minecraft:item/generated", "textures": {"layer0": t}}}}
    (ASSETS / "models/item" / f"{item}.json").write_text(json.dumps(model, ensure_ascii=False, separators=(",", ":")), encoding="utf-8")


def main():
    for look, bt in LOOKS.items():
        install_look(look, bt)
    for item, look, band in ITEMS:
        install_item(item, look, band)
    # 形だけの絵 (階級の箱の形の名前) は品の絵ではないので、平らなモデルの絵としてだけ残す
    print("looks", len(LOOKS), "items", len(ITEMS))


if __name__ == "__main__":
    main()
