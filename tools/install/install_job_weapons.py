"""全職の専用武器の空き 11 点の低ポリメッシュを入れる (祠の剣・鎚・弓と、職の章の武器 8 点。docs/design/43 の 8 節)。

入力: docs/review/roughs_starter/raw/<id>/<案>/ (祠の品。形の段 common) ・ docs/review/roughs_jobgear/raw/<id>/<案>/ (章の品。形の段 rare)
       {<段>[_pullN]_mesh.json, <段>[_pullN][_plusN]_atlas.png, _glowmask.png, icon[_pullN].png}
       (作り方: tools/rough/lowpoly/weapons/starter/build_starter.py ・ chapter_gear/build_gear.py)
出力は install_lowpoly_abyss_weapons.py と同じ形 (弓は引き絵の段ごとのメッシュとモデル <id>_pulling_N)。持ち方は似た武器のモデルを写す。

使い方: python tools/install/install_job_weapons.py [--option whale_bow=b ...]   (省くと全部 A 案。何度流してもよい)
"""
from __future__ import annotations
import os as _os
import sys as _sys
_sys.path[:0] = [_os.path.join(_os.path.dirname(_os.path.abspath(__file__)), p) for p in ('../lib',)]

import json
import shutil
import sys
from pathlib import Path

from PIL import Image

import mesh_close  # noqa: E402

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / "src/main/resources/assets/skyabyss"
LEVELS = ["", "_plus5", "_plus10"]
# 品 → (ラフの置き場, 形の段, 持ち方を借りる品, 引き絵の段の数)
ITEMS = {
    "shrine_sword": ("roughs_starter", "common", "abyss_lamp_sword", 1),
    "shrine_hammer": ("roughs_starter", "common", "ember_war_axe", 1),
    "shrine_bow": ("roughs_starter", "common", "thunder_wing_bow", 4),
    "whale_hammer": ("roughs_jobgear", "rare", "ember_war_axe", 1),
    "whale_bow": ("roughs_jobgear", "rare", "thunder_wing_bow", 4),
    "tide_hammer": ("roughs_jobgear", "rare", "ember_war_axe", 1),
    "tide_bow": ("roughs_jobgear", "rare", "thunder_wing_bow", 4),
    "storm_hammer": ("roughs_jobgear", "rare", "ember_war_axe", 1),
    "ember_greatsword": ("roughs_jobgear", "rare", "whalebone_greatsword", 1),
    "ember_bow": ("roughs_jobgear", "rare", "thunder_wing_bow", 4),
    "ember_dagger": ("roughs_jobgear", "rare", "waterway_fang", 1),
}


def bake_glow(atlas: Path, mask: Path):
    a = Image.open(atlas).convert("RGBA")
    m = Image.open(mask).convert("RGBA")
    out = Image.new("RGBA", a.size, (0, 0, 0, 0))
    lit = 0
    for y in range(a.size[1]):
        for x in range(a.size[0]):
            if m.getpixel((x, y))[3] > 0:
                out.putpixel((x, y), a.getpixel((x, y)))
                lit += 1
    return out, lit


def install(item: str, opt: str):
    srcdir, tier, analog, stages = ITEMS[item]
    src = ROOT / "docs/review" / srcdir / "raw" / item / opt
    mesh_dir = ASSETS / "lowpoly/items" / item
    tex_dir = ASSETS / "textures/lowpoly/items" / item
    mesh_dir.mkdir(parents=True, exist_ok=True)
    tex_dir.mkdir(parents=True, exist_ok=True)
    for f in list(mesh_dir.glob("*.json")) + list(tex_dir.glob("*.png")):
        f.unlink()
    for stg in range(stages):
        st = tier if stg == 0 else f"{tier}_pull{stg - 1}"
        mesh = json.loads((src / f"{st}_mesh.json").read_text(encoding="utf-8"))
        mesh["texture"] = f"items/{item}/{st}_atlas"
        mesh.pop("glowmask", None)
        mesh, _ = mesh_close.close_mesh(mesh)
        (mesh_dir / f"{st}.json").write_text(json.dumps(mesh, separators=(",", ":")), encoding="utf-8")
        for lv in LEVELS:
            name = f"{st}{lv}_atlas"
            shutil.copyfile(src / (name + ".png"), tex_dir / (name + ".png"))
            glow, lit = bake_glow(src / (name + ".png"), src / f"{st}{lv}_glowmask.png")
            if lit > 0:
                glow.save(tex_dir / (name + "_glow.png"))
    models = ASSETS / "models/item"
    names = [item] + [f"{item}_pulling_{i}" for i in range(stages - 1)]
    for k, name in enumerate(names):
        a_name = name.replace(item, analog)
        data = json.loads((models / f"{a_name}.json").read_text(encoding="utf-8").replace(analog, item))
        (models / f"{name}.json").write_text(json.dumps(data, ensure_ascii=False, separators=(",", ":")), encoding="utf-8")
        shutil.copyfile(src / ("icon.png" if k == 0 else f"icon_pull{k - 1}.png"), ASSETS / "textures/item" / f"{name}.png")
    print(item, "option", opt, "installed")


def main():
    choice = {i: "a" for i in ITEMS}
    args = sys.argv[1:]
    if "--option" in args:
        i = args.index("--option")
        for kv in args[i + 1:]:
            if "=" in kv:
                k, v = kv.split("=")
                choice[k] = v
    for item in ITEMS:
        install(item, choice[item])


if __name__ == "__main__":
    main()
