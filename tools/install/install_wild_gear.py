# -*- coding: utf-8 -*-
"""野の装備 (docs/design/51) の低ポリを assets へ入れる。

入力 (先に作る):
  python -I tools/rough/lowpoly/weapons/wild/build_wild.py o_wild1..5 [--noviews]   武器 102 本 → docs/review/roughs_wild/raw/<id>/a/
  python -I tools/rough/lowpoly/armor/wild/build_wild_armor.py                        防具 18 式 → docs/review/roughs_wild/raw/armor_<一式>/
出力:
  武器: lowpoly/items/<id>/<形の段>.json (階級の形の段 GearTier.meshTier: アンコモン = common、レア・エピック = rare、ヒロイック = legend)、
        textures/lowpoly/items/<id>/<段>[_plus5|_plus10]_atlas.png (+ _glow)、models/item/<id>.json (同じ型の武器を写す)・textures/item/<id>.png。
        振りの動きは tools/gen/items/gen_weapon_moves.py を流し直す
  防具: lowpoly/armor/<一式>/<部位>{,_<段>}.json・textures/lowpoly/armor/<一式>/...、textures/item/<一式>_<部位>.png と平らなモデル
使い方: python -I tools/install/install_wild_gear.py [weapons|armor] [id ...]
"""
from __future__ import annotations

import json
import shutil
import subprocess
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[1]
sys.path.insert(0, str(HERE.parent / "lib"))
sys.path.insert(0, str(ROOT / "tools/gen/wild_gear"))
import mesh_close  # noqa: E402
import spec as S  # noqa: E402
from PIL import Image  # noqa: E402

ASSETS = ROOT / "src/main/resources/assets/skyabyss"
RAW = ROOT / "docs/review/roughs_wild/raw"
LEVELS = ["", "_plus5", "_plus10"]
HOLD = {"sword": "whalebone_greatsword", "greatsword": "whalebone_greatsword", "spear": "captain_pike", "glaive": "warden_glaive",
        "hammer": "ember_war_axe", "maul": "ember_war_axe", "mace": "ember_war_axe", "axe": "ember_war_axe", "staff": "abyss_bell_staff",
        "bell": "abyss_bell_staff", "totem": "abyss_bell_staff", "censer": "abyss_bell_staff", "dagger": "waterway_fang", "fang": "waterway_fang",
        "claw": "raiju_claw", "bow": "thunder_wing_bow", "longbow": "thunder_wing_bow"}
MESH_TIER = {"uncommon": "common", "rare": "rare", "epic": "rare", "heroic": "legend"}
BUILD_TIER = {"uncommon": "rare", "rare": "rare", "epic": "rare", "heroic": "legend"}


def bake_glow(atlas: Path, mask: Path):
    a = Image.open(atlas).convert("RGBA")
    m = Image.open(mask).convert("L")
    out = Image.new("RGBA", a.size, (0, 0, 0, 0))
    lit = 0
    ap, mp, op = a.load(), m.load(), out.load()
    for y in range(a.size[1]):
        for x in range(a.size[0]):
            if mp[x, y] > 0:
                op[x, y] = ap[x, y]
                lit += 1
    return out, lit


def install_weapon(w):
    item = w["id"]
    src = RAW / item / "a"
    bt, dest = BUILD_TIER[w["tier"]], MESH_TIER[w["tier"]]
    stages = 4 if w["kind"] in ("bow", "longbow") else 1
    analog = HOLD[w["kind"]]
    mesh_dir = ASSETS / "lowpoly/items" / item
    tex_dir = ASSETS / "textures/lowpoly/items" / item
    mesh_dir.mkdir(parents=True, exist_ok=True)
    tex_dir.mkdir(parents=True, exist_ok=True)
    for f in list(mesh_dir.glob("*.json")) + list(tex_dir.glob("*.png")):
        f.unlink()
    for stg in range(stages):
        st = bt if stg == 0 else f"{bt}_pull{stg - 1}"
        dt = st.replace(bt, dest, 1)
        mesh = json.loads((src / f"{st}_mesh.json").read_text(encoding="utf-8"))
        mesh["texture"] = f"items/{item}/{dt}_atlas"
        mesh.pop("glowmask", None)
        mesh, _ = mesh_close.close_mesh(mesh)
        (mesh_dir / f"{dt}.json").write_text(json.dumps(mesh, separators=(",", ":")), encoding="utf-8")
        for lv in LEVELS:
            shutil.copyfile(src / f"{st}{lv}_atlas.png", tex_dir / f"{dt}{lv}_atlas.png")
            glow, lit = bake_glow(src / f"{st}{lv}_atlas.png", src / f"{st}{lv}_glowmask.png")
            if lit > 0:
                glow.save(tex_dir / f"{dt}{lv}_atlas_glow.png")
    models = ASSETS / "models/item"
    names = [item] + [f"{item}_pulling_{i}" for i in range(stages - 1)]
    for k, name in enumerate(names):
        a_name = name.replace(item, analog)
        data = json.loads((models / f"{a_name}.json").read_text(encoding="utf-8").replace(analog, item))
        (models / f"{name}.json").write_text(json.dumps(data, ensure_ascii=False, separators=(",", ":")), encoding="utf-8")
        shutil.copyfile(src / ("icon.png" if k == 0 else f"icon_pull{k - 1}.png"), ASSETS / "textures/item" / f"{name}.png")


def flat_model(name):
    (ASSETS / "models/item" / f"{name}.json").write_text(
        json.dumps({"parent": "minecraft:item/generated", "textures": {"layer0": f"skyabyss:item/{name}"}}), encoding="utf-8")


def install_armor(a):
    sid = a["id"]
    src = RAW / f"armor_{sid}" / "a"
    icons = RAW / f"armor_{sid}" / "icons"
    suffix = {"uncommon": "", "rare": "_rare", "epic": "_rare", "heroic": "_legend"}[a["tier"]]
    mesh_dir = ASSETS / "lowpoly/armor" / sid
    tex_dir = ASSETS / "textures/lowpoly/armor" / sid
    mesh_dir.mkdir(parents=True, exist_ok=True)
    tex_dir.mkdir(parents=True, exist_ok=True)
    for f in list(mesh_dir.glob("*.json")) + list(tex_dir.glob("*.png")):
        f.unlink()
    for p in S.PIECES:
        d = json.loads((src / f"{p}_mesh.json").read_text(encoding="utf-8"))
        d["socket"] = False
        d.pop("glowmask", None)
        d, _ = mesh_close.close_mesh(d)
        for sfx in sorted({"", suffix}):
            d["texture"] = f"armor/{sid}/{p}{sfx}"
            (mesh_dir / f"{p}{sfx}.json").write_text(json.dumps(d, separators=(",", ":")), encoding="utf-8")
            for lv in LEVELS:
                atlas, mask = src / f"{p}{lv}_atlas.png", src / f"{p}{lv}_glowmask.png"
                glow, lit = bake_glow(atlas, mask)
                if lit == 0:
                    sys.exit(f"{sid} {p}{lv}: no glow pixels (GeckoLib crashes on an empty glowmask)")
                Image.open(atlas).convert("RGBA").save(tex_dir / f"{p}{sfx}{lv}.png", optimize=True)
                glow.save(tex_dir / f"{p}{sfx}{lv}_glowmask.png", optimize=True)
        shutil.copyfile(icons / f"a_{p}_32.png", ASSETS / "textures/item" / f"{sid}_{p}.png")
        flat_model(f"{sid}_{p}")


# 装飾の枠の品 (手・地面・額縁・頭は client/accessory/AccessoryItemRenderer の低ポリ、GUI は 32x32 の平絵。
# 持ち方は今の装飾品と同じ表 (tools/install/install_lowpoly_accessories.py の DISPLAY)。鍛えないので強化の絵は入れない
ACC_DISPLAY = {
    "thirdperson_righthand": {"rotation": [0, -45, 0], "translation": [0, 3, 1], "scale": [0.85, 0.85, 0.85]},
    "thirdperson_lefthand": {"rotation": [0, 45, 0], "translation": [0, 3, 1], "scale": [0.85, 0.85, 0.85]},
    "firstperson_righthand": {"rotation": [0, -60, 10], "translation": [0.8, 2.5, 1.5], "scale": [0.55, 0.55, 0.55]},
    "firstperson_lefthand": {"rotation": [0, 60, -10], "translation": [0.8, 2.5, 1.5], "scale": [0.55, 0.55, 0.55]},
    "ground": {"rotation": [0, 0, 0], "translation": [0, 2, 0], "scale": [0.55, 0.55, 0.55]},
    "head": {"rotation": [0, 180, 0], "translation": [0, 13, 7], "scale": [0.9, 0.9, 0.9]},
    "fixed": {"rotation": [0, 180, 0], "translation": [0, 0, 0], "scale": [0.85, 0.85, 0.85]},
}


def install_accessory(x):
    item = x["id"]
    src = RAW / item / "a"
    bt, dest = BUILD_TIER[x["tier"]], MESH_TIER[x["tier"]]
    mesh_dir = ASSETS / "lowpoly/items" / item
    tex_dir = ASSETS / "textures/lowpoly/items" / item
    mesh_dir.mkdir(parents=True, exist_ok=True)
    tex_dir.mkdir(parents=True, exist_ok=True)
    for f in list(mesh_dir.glob("*.json")) + list(tex_dir.glob("*.png")):
        f.unlink()
    mesh = json.loads((src / f"{bt}_mesh.json").read_text(encoding="utf-8"))
    mesh["texture"] = f"items/{item}/{dest}_atlas"
    mesh.pop("glowmask", None)
    mesh, _ = mesh_close.close_mesh(mesh)
    (mesh_dir / f"{dest}.json").write_text(json.dumps(mesh, separators=(",", ":")), encoding="utf-8")
    shutil.copyfile(src / f"{bt}_atlas.png", tex_dir / f"{dest}_atlas.png")
    glow, lit = bake_glow(src / f"{bt}_atlas.png", src / f"{bt}_glowmask.png")
    if lit > 0:
        glow.save(tex_dir / f"{dest}_atlas_glow.png")
    shutil.copyfile(src / "icon.png", ASSETS / "textures/item" / f"{item}.png")
    tex = f"skyabyss:item/{item}"
    flat = ASSETS / "models/item_flat"
    flat.mkdir(parents=True, exist_ok=True)
    (flat / f"{item}.json").write_text(json.dumps({"parent": "minecraft:item/generated", "textures": {"layer0": tex}}, separators=(",", ":")),
                                       encoding="utf-8")
    model = {"loader": "neoforge:separate_transforms",
             "base": {"parent": "builtin/entity", "gui_light": "front", "display": ACC_DISPLAY, "textures": {"particle": tex}},
             "perspectives": {"gui": {"parent": "minecraft:item/generated", "textures": {"layer0": tex}}}}
    (ASSETS / "models/item" / f"{item}.json").write_text(json.dumps(model, ensure_ascii=False, separators=(",", ":")), encoding="utf-8")


def main():
    args = sys.argv[1:]
    what = args[0] if args and args[0] in ("weapons", "armor", "accessories") else "all"
    only = [a for a in args if a not in ("weapons", "armor", "accessories")]
    if what in ("all", "weapons"):
        n = 0
        for w in S.WEAPONS:
            if (not only or w["id"] in only) and (RAW / w["id"] / "a").is_dir():
                install_weapon(w)
                n += 1
        print("weapons installed:", n)
        subprocess.check_call([sys.executable, "-I", str(ROOT / "tools/gen/items/gen_weapon_moves.py")])
    if what in ("all", "armor"):
        n = 0
        for a in S.ARMORS:
            if (not only or a["id"] in only) and (RAW / f"armor_{a['id']}").is_dir():
                install_armor(a)
                n += 1
        print("armor sets installed:", n)
    if what in ("all", "accessories"):
        n = 0
        for x in S.ACCESSORIES:
            if (not only or x["id"] in only) and (RAW / x["id"] / "a").is_dir():
                install_accessory(x)
                n += 1
        print("accessories installed:", n)


if __name__ == "__main__":
    main()
