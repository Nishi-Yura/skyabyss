"""余燼のゲア (戦斧・鍛え手の大鎚・灰被りの火掻き) と、護符・灯・角杯・心核・欠片・粉・殻のアイコンを採用案で書き出す。

使い方:
    python tools/gen_ember_items.py                          # 推奨案 (RECOMMEND) で書く
    python tools/gen_ember_items.py --option smith_hammer=b  # 案を指定 (複数可)
    python tools/gen_ember_items.py --from-sources           # tools/sources/ember_gear/ から書き直す

入力: docs/review/ember_gear_roughs/<item>/<a|b|c>/ (icon.png, voxels.npz)
      選んだ案を tools/sources/ember_gear/<item>/ にコピーし、そこから書く (再実行できるように)。
出力:
  src/main/resources/assets/skyabyss/models/item/<item>.json
  src/main/resources/assets/skyabyss/textures/item/<item>.png         (32x32 のアイコン)
  src/main/resources/assets/skyabyss/textures/item/3d/<item>.png      (3D のパレット。武器だけ)
  docs/review/previews/ember_gear_<item>.png                          (確認用の描画。採用外ではない確認画像)

3D の作り: tools/install_tide_gear.py の build_elements (ボクセルを直方体にまとめる) をそのまま使う。
武器は -45 度に傾ける (刃先が右上。アイコンと同じ向き)。持ち方はバニラの handheld (gen_weapon_models_v2 の
HANDHELD_DISPLAY) と同じ。鍛え手の大鎚は両手の重い武器なので、拡大を下げた値 (HAMMER_DISPLAY) にする。
値は目分量の最初の案。実機で確かめて直すこと。
"""
from __future__ import annotations

import argparse
import copy
import json
import shutil
import sys
from pathlib import Path

import numpy as np
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "tools"))
sys.path.insert(0, str(ROOT / "tools" / "rough"))
from gen_weapon_models_v2 import HANDHELD_DISPLAY  # noqa: E402
from install_tide_gear import build_elements, write_json, write_palette  # noqa: E402
from ember_gear import ITEMS, OPTS, ROT_DEG, preview_3d  # noqa: E402

ASSETS = ROOT / "src/main/resources/assets/skyabyss"
REVIEW = ROOT / "docs/review/ember_gear_roughs"
SOURCES = ROOT / "tools/sources/ember_gear"
PREVIEW = ROOT / "docs/review/previews"
MAX_ELEMENTS = 1500

# 推奨案 (理由は docs/review/ember_gear_roughs/README.md)
RECOMMEND = {
    "ember_war_axe": "a",
    "smith_hammer": "a",
    "thrall_poker": "a",
    "ember_mantle": "a",
    "kindling_charm": "a",
    "ember_lantern": "a",
    "ember_horn": "a",
    "ember_core": "a",
    "ember_shard": "a",
    "spark_dust": "a",
    "cinder_husk": "a",
}

# 大鎚は重い両手の武器で、頭が大きい (中ボスの大鎚と同じ比)。拡大を下げる。
# 台本 tools/shots/ember_gear.txt・ember_gear_hands.txt で、潮殻の三叉槍と並べて決めた (2026-10-16)。
# 戦斧と火掻きはバニラの handheld のまま (一人称で三叉槍と同じ大きさに見えた)
HAMMER_DISPLAY = copy.deepcopy(HANDHELD_DISPLAY)
for _k in ("thirdperson_righthand", "thirdperson_lefthand"):
    HAMMER_DISPLAY[_k]["scale"] = [0.6, 0.6, 0.6]
    HAMMER_DISPLAY[_k]["translation"] = [0, 2.5, 0.5]
for _k in ("firstperson_righthand", "firstperson_lefthand"):
    HAMMER_DISPLAY[_k]["scale"] = [0.5, 0.5, 0.5]
WEAPON_DISPLAY = HANDHELD_DISPLAY


def install_item(item: str, opt: str):
    cfg = ITEMS[item]
    src = REVIEW / item / opt
    dst = SOURCES / item
    dst.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(src / "icon.png", dst / "icon.png")
    if cfg["kind"] == "weapon":
        shutil.copyfile(src / "voxels.npz", dst / "voxels.npz")
    (dst / "option.txt").write_text(opt + "\n", encoding="utf-8")


def overlap_count(elements) -> int:
    """直方体どうしの体積のある重なりを数える (回転前の座標で比べる。回転は全体に同じ)。"""
    boxes = [(np.array(e["from"]), np.array(e["to"])) for e in elements]
    n = 0
    for i in range(len(boxes)):
        a0, a1 = boxes[i]
        for j in range(i + 1, len(boxes)):
            b0, b1 = boxes[j]
            ov = np.minimum(a1, b1) - np.maximum(a0, b0)
            if (ov > 1e-6).all():
                n += 1
    return n


def write_item(item: str):
    cfg = ITEMS[item]
    src = SOURCES / item
    icon = np.array(Image.open(src / "icon.png").convert("RGBA"))
    assert icon.shape == (32, 32, 4), icon.shape
    Image.fromarray(icon, "RGBA").save(ASSETS / "textures/item" / f"{item}.png")
    info = {"item": item, "option": (src / "option.txt").read_text(encoding="utf-8").strip()}
    if cfg["kind"] == "icon":
        write_json(ASSETS / "models/item" / f"{item}.json",
                   {"parent": "minecraft:item/generated", "textures": {"layer0": f"skyabyss:item/{item}"}})
        return info
    d = np.load(src / "voxels.npz")
    grid, glow = d["grid"], d["glow"].astype(bool)
    elements, palette, s, length = build_elements(grid, glow, float(cfg["target"]), ROT_DEG)
    if len(elements) > MAX_ELEMENTS:
        raise SystemExit(f"{item}: elements {len(elements)} > {MAX_ELEMENTS}")
    ov = overlap_count(elements)
    if ov:
        raise SystemExit(f"{item}: {ov} overlapping elements")
    write_palette(item, palette)
    display = HAMMER_DISPLAY if item == "smith_hammer" else WEAPON_DISPLAY
    model = {
        "loader": "neoforge:separate_transforms",
        "base": {
            "gui_light": "front",
            "display": display,
            "textures": {"pal": f"skyabyss:item/3d/{item}", "particle": f"skyabyss:item/{item}"},
            "texture_size": [16, 16],
            "elements": elements,
        },
        "perspectives": {
            "gui": {"parent": "minecraft:item/generated", "textures": {"layer0": f"skyabyss:item/{item}"}},
        },
    }
    write_json(ASSETS / "models/item" / f"{item}.json", model)
    info.update({"elements": len(elements), "faces": sum(len(e["faces"]) for e in elements),
                 "palette": len(palette), "voxel_size": s, "length_cells": length, "overlaps": ov})
    PREVIEW.mkdir(parents=True, exist_ok=True)
    front, hand = preview_3d(grid, glow, ROT_DEG)
    Image.fromarray(np.hstack([np.array(front.convert("RGB")), np.array(hand.convert("RGB"))]), "RGB").save(
        PREVIEW / f"ember_gear_{item}.png")
    return info


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--option", action="append", default=[], help="item=a|b|c")
    ap.add_argument("--from-sources", action="store_true", help="tools/sources/ember_gear/ から書き直す")
    args = ap.parse_args()
    choice = dict(RECOMMEND)
    for kv in args.option:
        item, opt = kv.split("=")
        if item not in ITEMS or opt not in OPTS:
            raise SystemExit(f"bad option {kv}")
        choice[item] = opt
    results = []
    for item in ITEMS:
        if not args.from_sources:
            install_item(item, choice[item])
        results.append(write_item(item))
    for r in results:
        print(json.dumps(r, ensure_ascii=False))


if __name__ == "__main__":
    main()
