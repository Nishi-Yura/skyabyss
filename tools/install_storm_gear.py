"""雷翼の装備 (弓・雷獣の爪・羽守の鉤槍) と、アイコンだけの品 (護符・足環・羽根・欠片・心核・干し果・鱗粉) を採用案で書き出す。
羽衣 (防具 4 部位) は tools/install_storm_armor.py。

使い方:
    python tools/install_storm_gear.py                          # 推奨案 (RECOMMEND) で書く
    python tools/install_storm_gear.py --option raiju_claw=b    # 案を指定 (複数可)
    python tools/install_storm_gear.py --from-sources           # tools/sources/storm_gear/ から書き直す

入力: docs/review/storm_gear_roughs/<item>/<a|b|c>/ (icon.png, voxels.npz。弓は icon_pull_<n>.png も)。
      tools/rough/storm_items.py が作る。選んだ案を tools/sources/storm_gear/<item>/ にコピーし、そこから書く。
出力 (src/main/resources/assets/skyabyss/):
  models/item/<item>.json                 3D (手に持つ) と、インベントリは 32x32 の平たい絵 (neoforge:separate_transforms)
  models/item/thunder_wing_bow_pulling_{0,1,2}.json   弓の引き絵 (pull / pulling の印は StormGearClient が登録)
  textures/item/<item>.png                32x32 のアイコン (弓は thunder_wing_bow_pulling_<n>.png も)
  textures/item/3d/<item>.png             3D のパレット (16 幅)
  textures/mob_effect/static_ward.png     静電避け (雲間の干し果) の 18x18 の印
  docs/review/previews/storm_gear_<item>.png   確認用の描画 (git に入れない)

作りは潮殻の install_tide_gear.py と同じ (ボクセルを直方体にまとめ、外に見える面だけ書く)。
武器は -45 度に傾け (刃先が右上。アイコンと同じ向き)、持ち方はバニラの handheld と同じ値。
弓も同じ向きに傾け、持ち方はバニラの item/bow.json の値。
"""
from __future__ import annotations

import argparse
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
from install_tide_gear import MAX_ELEMENTS, PAL_H, PAL_W, build_elements, write_json  # noqa: E402
from storm_items import ITEMS, OPTS, ROT_DEG  # noqa: E402
from tide_gear import preview_3d  # noqa: E402

ASSETS = ROOT / "src/main/resources/assets/skyabyss"
REVIEW = ROOT / "docs/review/storm_gear_roughs"
SOURCES = ROOT / "tools/sources/storm_gear"
PREVIEW = ROOT / "docs/review/previews"

# 推奨案 (理由は docs/review/storm_gear_roughs/README.md)
RECOMMEND = {
    "thunder_wing_bow": "a",
    "raiju_claw": "a",
    "warden_glaive": "c",
    "storm_charm": "b",
    "storm_anklet": "c",
    "storm_eye_feather": "a",
    "thunder_wing_shard": "a",
    "thunder_wing_core": "a",
    "cloudgap_fruit": "a",
    "moth_dust": "c",
}

# バニラの item/bow.json の持ち方 (平たい弓の絵と同じ向きの 3D に、そのまま使う)
BOW_DISPLAY = {
    "thirdperson_righthand": {"rotation": [-80, 260, -40], "translation": [-1, -2, 2.5], "scale": [0.9, 0.9, 0.9]},
    "thirdperson_lefthand": {"rotation": [-80, -280, 40], "translation": [-1, -2, 2.5], "scale": [0.9, 0.9, 0.9]},
    "firstperson_righthand": {"rotation": [0, -90, 25], "translation": [1.13, 3.2, 1.13], "scale": [0.68, 0.68, 0.68]},
    "firstperson_lefthand": {"rotation": [0, 90, -25], "translation": [1.13, 3.2, 1.13], "scale": [0.68, 0.68, 0.68]},
    "ground": {"rotation": [0, 0, 0], "translation": [0, 2, 0], "scale": [0.5, 0.5, 0.5]},
    "fixed": {"rotation": [0, 180, 0], "translation": [0, 0, 0], "scale": [1, 1, 1]},
}
# 引き絵へ替える印 (バニラの弓と同じ)
BOW_OVERRIDES = [
    {"predicate": {"pulling": 1}, "model": "skyabyss:item/thunder_wing_bow_pulling_0"},
    {"predicate": {"pulling": 1, "pull": 0.65}, "model": "skyabyss:item/thunder_wing_bow_pulling_1"},
    {"predicate": {"pulling": 1, "pull": 0.9}, "model": "skyabyss:item/thunder_wing_bow_pulling_2"},
]


def write_palette(name, palette):
    H = (len(palette) + PAL_W - 1) // PAL_W
    pal = np.zeros((H, PAL_W, 4), np.uint8)
    for i, (r, g, b, _) in enumerate(palette):
        pal[i // PAL_W, i % PAL_W] = (r, g, b, 255)
    arr = pal[np.arange(PAL_H) % H]   # 16 行に繰り返す (1px 高さはミップで崩れるため)
    out = ASSETS / "textures/item/3d" / f"{name}.png"
    out.parent.mkdir(parents=True, exist_ok=True)
    Image.fromarray(arr, "RGBA").save(out)


def model_json(name, icon, elements, display, overrides=None):
    data = {
        "loader": "neoforge:separate_transforms",
        "base": {
            "gui_light": "front",
            "display": display,
            "textures": {"pal": f"skyabyss:item/3d/{name}", "particle": f"skyabyss:item/{icon}"},
            "texture_size": [PAL_W, PAL_H],
            "elements": elements,
        },
        "perspectives": {
            "gui": {"parent": "minecraft:item/generated", "textures": {"layer0": f"skyabyss:item/{icon}"}},
        },
    }
    if overrides:
        data["overrides"] = overrides
    return data


def install_item(item: str, opt: str):
    src = REVIEW / item / opt
    dst = SOURCES / item
    dst.mkdir(parents=True, exist_ok=True)
    for f in src.iterdir():
        if f.name == "icon.png" or f.name == "voxels.npz" or f.name.startswith("icon_pull_"):
            shutil.copyfile(f, dst / f.name)
    (dst / "option.txt").write_text(opt + "\n", encoding="utf-8")


def save_icon(src: Path, name: str):
    icon = np.array(Image.open(src).convert("RGBA"))
    assert icon.shape == (32, 32, 4), icon.shape
    Image.fromarray(icon, "RGBA").save(ASSETS / "textures/item" / f"{name}.png")


def solid_model(name, icon, grid, glow, target, display, overrides=None):
    elements, palette, s, length = build_elements(grid, glow.astype(bool), float(target), ROT_DEG)
    if len(elements) > MAX_ELEMENTS:
        raise SystemExit(f"{name}: elements {len(elements)} > {MAX_ELEMENTS}")
    write_palette(name, palette)
    write_json(ASSETS / "models/item" / f"{name}.json", model_json(name, icon, elements, display, overrides))
    return {"elements": len(elements), "palette": len(palette), "voxel_size": s, "length_cells": length}


def write_item(item: str):
    cfg = ITEMS[item]
    src = SOURCES / item
    save_icon(src / "icon.png", item)
    info = {"item": item, "option": (src / "option.txt").read_text(encoding="utf-8").strip()}
    if cfg["kind"] == "icon":
        write_json(ASSETS / "models/item" / f"{item}.json",
                   {"parent": "minecraft:item/generated", "textures": {"layer0": f"skyabyss:item/{item}"}})
        return info
    d = np.load(src / "voxels.npz")
    if cfg["kind"] == "bow":
        info.update(solid_model(item, item, d["grid"], d["glow"], cfg["target"], BOW_DISPLAY, BOW_OVERRIDES))
        for k in range(3):
            name = f"{item}_pulling_{k}"
            save_icon(src / f"icon_pull_{k}.png", name)
            info[name] = solid_model(name, name, d[f"grid_pull_{k}"], d[f"glow_pull_{k}"], cfg["target"], BOW_DISPLAY)["elements"]
    else:
        info.update(solid_model(item, item, d["grid"], d["glow"], cfg["target"], HANDHELD_DISPLAY))
    PREVIEW.mkdir(parents=True, exist_ok=True)
    front, hand = preview_3d(d["grid"], d["glow"].astype(bool), ROT_DEG)
    Image.fromarray(np.hstack([np.array(front.convert("RGB")), np.array(hand.convert("RGB"))]), "RGB").save(
        PREVIEW / f"storm_gear_{item}.png")
    return info


# 静電避け (雲間の干し果の効き目) の 18x18 の印: 灰青の丸に青白い稲妻。輪郭は濃い色
BOLT = ["........##", ".......#B#", "......#BB#", ".....#BB#.", "....#BB#..", "...#BBB###", "..#BBBBBB#",
        "..###BBB#.", "....#BB#..", "...#BB#...", "..#BB#....", "..#B#.....", "..##......"]


def write_effect_icon():
    img = Image.new("RGBA", (18, 18), (0, 0, 0, 0))
    px = img.load()
    for y in range(18):
        for x in range(18):
            d = ((x - 8.5) ** 2 + (y - 8.5) ** 2) ** 0.5
            if d <= 8.4:
                px[x, y] = (40, 44, 56, 255) if d > 7.4 else (78, 96, 128, 255) if y > 8 else (98, 118, 150, 255)
    for j, row in enumerate(BOLT):
        for i, c in enumerate(row):
            if c != ".":
                px[i + 4, j + 2] = (226, 238, 255, 255) if c == "B" else (30, 40, 70, 255)
    out = ASSETS / "textures/mob_effect/static_ward.png"
    out.parent.mkdir(parents=True, exist_ok=True)
    img.save(out)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--option", action="append", default=[], help="item=a|b|c")
    ap.add_argument("--from-sources", action="store_true", help="tools/sources/storm_gear/ から書き直す")
    args = ap.parse_args()
    choice = dict(RECOMMEND)
    for kv in args.option:
        item, opt = kv.split("=")
        if item not in ITEMS or opt not in OPTS:
            raise SystemExit(f"bad option {kv}")
        choice[item] = opt
    for item in ITEMS:
        if not args.from_sources:
            install_item(item, choice[item])
        print(json.dumps(write_item(item), ensure_ascii=False))
    write_effect_icon()


if __name__ == "__main__":
    main()
