"""五章「深淵」の装備 (docs/design/03 の 12 章) の低ポリと 32x32 のアイコンを assets へ入れる。

入力 (ラフ 3 案。先に作る):
  python -I tools/rough/lowpoly/weapons/gear_gaps/build_gaps.py      武器 3 点 → docs/review/roughs_gear_gaps/raw/<id>/<案>/
  python -I tools/rough/lowpoly/weapons/gear_gaps/acc_gaps.py        装飾品・護符 3 点と錨鉄 → 同上
  python -I tools/rough/lowpoly/gear_gaps/armor_abyss.py             淵潜りの鎧 4 部位 (前の案) → docs/review/roughs_gear_gaps/raw/armor_abyss/<案>/
  python -I tools/rough/lowpoly/gear_gaps/armor_abyss_v2.py a        淵潜りの鎧 v2 (今の A 案、強化 +5/+10 の絵つき) → raw/armor_abyss_v2/a/
出力 (assets/skyabyss/ 以下):
  lowpoly/items/<id>/<階級>.json, textures/lowpoly/items/<id>/<階級>[_plusN]_atlas{,_glow}.png   武器 (WeaponItemRenderer)・装飾品 (AccessoryItemRenderer)
  lowpoly/armor/abyss_diver/<部位>{,_rare}.json, textures/lowpoly/armor/abyss_diver/<部位>{,_rare}{,_plus5,_plus10}{,_glowmask}.png   鎧 (LowPolyArmorRenderer)
  models/item/<id>.json, textures/item/<id>.png                                                  品のモデル (base は builtin/entity) と、メッシュから写した 32x32 のアイコン
  共鳴の欠片 (深淵)・深淵の心核のアイコンは、余燼の同じ品の絵の色を深淵の紫に替えたもの (欠片と心核は巨神ごとに色だけ違う決まり)
  v2 (2026-10-24 の作り直し。選ばれた A 案を基準の鯨骨の大剣より細かく): python -I tools/rough/lowpoly/weapons/abyss_v2/build_v2.py → raw/<id>/v2/
       v2 の品のアイコンはメッシュから写す (tools/rough/lowpoly/weapons/icon_from_mesh.py の auto_icon)。武器の持ち方は tools/lib/weapon_display.py の表
使い方: python -I tools/install/install_abyss_gear.py [--option abyss_lamp_sword=b abyss_diver=c ...]   (省くと武器・装飾品は v2、鎧は v2 の A 案 = v2a。何度流してもよい)
"""
from __future__ import annotations
import os as _os
import sys as _sys
_sys.path[:0] = [_os.path.join(_os.path.dirname(_os.path.abspath(__file__)), p) for p in ('../lib',)]

import colorsys
import json
import shutil
import sys
from pathlib import Path

import numpy as np
from PIL import Image

sys.path.insert(0, str(Path(__file__).resolve().parent))
import mesh_close  # noqa: E402
import weapon_display  # noqa: E402
sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "rough/lowpoly/weapons"))
import icon_from_mesh  # noqa: E402

ROOT = Path(__file__).resolve().parents[2]
SRC = ROOT / "docs/review/roughs_gear_gaps/raw"
ASSETS = ROOT / "src/main/resources/assets/skyabyss"
# 武器: (階級, 持ち方を借りる武器のモデル)
WEAPONS = {"abyss_lamp_sword": ("rare", "whalebone_greatsword"), "abyss_sinking_anchor": ("legend", "smith_hammer"),
           "abyss_pulse_dagger": ("legend", "waterway_fang")}
ACCESSORIES = {"abyss_pulse_listener": "rare", "abyss_lantern_charm": "rare", "abyss_surveyor_plumb": "legend"}
PIECES = ["helmet", "chestplate", "leggings", "boots"]
LEVELS = ["", "_plus5", "_plus10"]
# 装飾品の持ち方 (install_lowpoly_accessories.py と同じ)
DISPLAY = {
    "thirdperson_righthand": {"rotation": [0, -45, 0], "translation": [0, 3, 1], "scale": [0.85, 0.85, 0.85]},
    "thirdperson_lefthand": {"rotation": [0, 45, 0], "translation": [0, 3, 1], "scale": [0.85, 0.85, 0.85]},
    "firstperson_righthand": {"rotation": [0, -60, 10], "translation": [0.8, 2.5, 1.5], "scale": [0.55, 0.55, 0.55]},
    "firstperson_lefthand": {"rotation": [0, 60, -10], "translation": [0.8, 2.5, 1.5], "scale": [0.55, 0.55, 0.55]},
    "ground": {"rotation": [0, 0, 0], "translation": [0, 2, 0], "scale": [0.55, 0.55, 0.55]},
    "head": {"rotation": [0, 180, 0], "translation": [0, 13, 7], "scale": [0.9, 0.9, 0.9]},
    "fixed": {"rotation": [0, 180, 0], "translation": [0, 0, 0], "scale": [0.85, 0.85, 0.85]},
}


HANG = {"abyss_lantern_charm": {
    "thirdperson_righthand": {"rotation": [-90, 0, 0], "translation": [0, -1.5, 3.5], "scale": [0.75, 0.75, 0.75]},
    "thirdperson_lefthand": {"rotation": [-90, 0, 0], "translation": [0, -1.5, 3.5], "scale": [0.75, 0.75, 0.75]}}}


def bake_glow(atlas: Path, mask: Path):
    a = np.array(Image.open(atlas).convert("RGBA"))
    m = np.array(Image.open(mask).convert("RGBA"))[..., 3] > 0
    out = a.copy()
    out[~m] = 0
    return Image.fromarray(out, "RGBA"), int(m.sum())


def close(mesh: dict) -> dict:
    mesh.pop("glowmask", None)
    mesh.pop("triangle_count", None)
    for b in mesh["bones"].values():
        b.pop("triangle_count", None)
    mesh, _ = mesh_close.close_mesh(mesh)
    return mesh


def write_json(path: Path, obj, compact=True):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(obj, ensure_ascii=False, separators=(",", ":")) if compact else json.dumps(obj, ensure_ascii=False, indent=2) + "\n",
                    encoding="utf-8")


def install_mesh_item(item: str, opt: str, tier: str, levels):
    src = SRC / item / opt
    mesh_dir = ASSETS / "lowpoly/items" / item
    tex_dir = ASSETS / "textures/lowpoly/items" / item
    mesh_dir.mkdir(parents=True, exist_ok=True)
    tex_dir.mkdir(parents=True, exist_ok=True)
    for f in list(mesh_dir.glob("*.json")) + list(tex_dir.glob("*.png")):
        f.unlink()
    mesh = json.loads((src / f"{tier}_mesh.json").read_text(encoding="utf-8"))
    mesh["texture"] = f"items/{item}/{tier}_atlas"
    (mesh_dir / f"{tier}.json").write_text(json.dumps(close(mesh), separators=(",", ":")), encoding="utf-8")
    for lv in levels:
        name = f"{tier}{lv}_atlas"
        shutil.copyfile(src / (name + ".png"), tex_dir / (name + ".png"))
        glow, lit = bake_glow(src / (name + ".png"), src / f"{tier}{lv}_glowmask.png")
        if lit > 0:
            glow.save(tex_dir / (name + "_glow.png"))
    if opt == "v2":
        icon_from_mesh.auto_icon(item, tier).save(ASSETS / "textures/item" / f"{item}.png", optimize=True)
    else:
        shutil.copyfile(src / "icon.png", ASSETS / "textures/item" / f"{item}.png")


def install_weapon(item: str, opt: str):
    tier, analog = WEAPONS[item]
    install_mesh_item(item, opt, tier, LEVELS)
    text = (ASSETS / "models/item" / f"{analog}.json").read_text(encoding="utf-8").replace(analog, item)
    model = json.loads(text)
    model["base"]["display"] = weapon_display.display_for(item, model["base"]["display"])
    write_json(ASSETS / "models/item" / f"{item}.json", model)
    print(item, "option", opt, "installed")


def install_accessory(item: str, opt: str):
    tier = ACCESSORIES[item]
    install_mesh_item(item, opt, tier, [""])
    display = json.loads(json.dumps(DISPLAY))
    if item in HANG:
        # 吊るして持つ品 (灯籠): 三人称は吊り環を手に、本体が下へ垂れる向き
        for k, v in HANG[item].items():
            display[k] = v
    model = {"loader": "neoforge:separate_transforms",
             "base": {"parent": "builtin/entity", "gui_light": "front", "display": display, "textures": {"particle": f"skyabyss:item/{item}"}},
             "perspectives": {"gui": {"parent": "minecraft:item/generated", "textures": {"layer0": f"skyabyss:item/{item}"}}}}
    write_json(ASSETS / "models/item" / f"{item}.json", model)
    # 平絵の戻り先 (LowPoly.accessoryMeshes = false のとき AccessoryItemRenderer が描く)
    write_json(ASSETS / "models/item_flat" / f"{item}.json", {"parent": "minecraft:item/generated", "textures": {"layer0": f"skyabyss:item/{item}"}})
    print(item, "option", opt, "installed")


def flat_model(name: str):
    write_json(ASSETS / "models/item" / f"{name}.json", {"parent": "minecraft:item/generated", "textures": {"layer0": "skyabyss:item/" + name}}, compact=False)


def install_armor(opt: str):
    """opt: 前の 3 案 a/b/c (raw/armor_abyss/<案>) か、作り直しの v2a/v2b/v2c (raw/armor_abyss_v2/<案>。強化 +5/+10 の絵つき)"""
    if opt.startswith("v2"):
        src, icons, o = SRC / "armor_abyss_v2" / opt[2:], SRC / "armor_abyss_v2/icons", opt[2:]
    else:
        src, icons, o = SRC / "armor_abyss" / opt, SRC / "armor_abyss/icons", opt
    mesh_dir = ASSETS / "lowpoly/armor/abyss_diver"
    tex_dir = ASSETS / "textures/lowpoly/armor/abyss_diver"
    mesh_dir.mkdir(parents=True, exist_ok=True)
    tex_dir.mkdir(parents=True, exist_ok=True)
    for f in list(mesh_dir.glob("*.json")) + list(tex_dir.glob("*.png")):
        f.unlink()
    for p in PIECES:
        d = json.loads((src / f"{p}_mesh.json").read_text(encoding="utf-8"))
        d["socket"] = False
        d = close(d)
        for suffix in ("", "_rare"):
            d["texture"] = f"armor/abyss_diver/{p}{suffix}"
            (mesh_dir / f"{p}{suffix}.json").write_text(json.dumps(d, separators=(",", ":")), encoding="utf-8")
            # 強化の段の絵 (+5 / +10) はレアのメッシュにだけ (LowPolyArmor.atlas は無ければ基準の絵を使う)
            for lv in ("",) + (("_plus5", "_plus10") if suffix == "_rare" else ()):
                atlas, mask = src / f"{p}{lv}_atlas.png", src / f"{p}{lv}_glowmask.png"
                if not atlas.exists():
                    continue
                glow, lit = bake_glow(atlas, mask)
                if lit == 0:
                    sys.exit(p + lv + ": no glow pixels (GeckoLib crashes on an empty glowmask)")
                Image.open(atlas).convert("RGBA").save(tex_dir / f"{p}{suffix}{lv}.png", optimize=True)
                glow.save(tex_dir / f"{p}{suffix}{lv}_glowmask.png", optimize=True)
        shutil.copyfile(icons / f"{o}_{p}_32.png", ASSETS / "textures/item" / f"abyss_diver_{p}.png")
        flat_model(f"abyss_diver_{p}")
    print("abyss_diver armor option", opt, "installed")


def recolor(src_name: str, dst_name: str, hue=0.76, sat_boost=1.0):
    """巨神の欠片・心核の絵の色相を深淵の紫へ回す (明るさはそのまま)"""
    im = np.array(Image.open(ASSETS / "textures/item" / f"{src_name}.png").convert("RGBA")).astype(float) / 255.0
    out = im.copy()
    for y in range(im.shape[0]):
        for x in range(im.shape[1]):
            r, g, b, a = im[y, x]
            if a <= 0:
                continue
            h, l, s = colorsys.rgb_to_hls(r, g, b)
            nr, ng, nb = colorsys.hls_to_rgb(hue, l, min(1.0, s * sat_boost))
            out[y, x, :3] = (nr, ng, nb)
    Image.fromarray((out * 255).round().astype(np.uint8), "RGBA").save(ASSETS / "textures/item" / f"{dst_name}.png")
    flat_model(dst_name)


def main():
    choice = {k: "v2" for k in list(WEAPONS) + list(ACCESSORIES)}
    choice["abyss_diver"] = "v2a"   # 2026-10-24: 作り直しの A 案「潜水兜と錨鉄の潜水服」
    args = sys.argv[1:]
    if "--option" in args:
        for kv in args[args.index("--option") + 1:]:
            if "=" in kv:
                k, v = kv.split("=")
                choice[k] = v
    for item in WEAPONS:
        install_weapon(item, choice[item])
    for item in ACCESSORIES:
        install_accessory(item, choice[item])
    install_armor(choice["abyss_diver"])
    # 錨鉄 (素材): メッシュから写したアイコンだけ (手には平らな絵)
    shutil.copyfile(SRC / "abyss_anchor_iron/a/icon.png", ASSETS / "textures/item/abyss_anchor_iron.png")
    flat_model("abyss_anchor_iron")
    recolor("resonance_shard_ember", "resonance_shard_abyss")
    recolor("ember_core", "abyss_core", sat_boost=1.1)
    print("materials installed")


if __name__ == "__main__":
    main()
