"""島の祠の始めの武器 3 点 (shrine_staff / shrine_bell_staff / shrine_dagger) の低ポリメッシュを入れる (docs/design/43 の 4 節)。

入力: docs/review/roughs_starter/raw/<id>/<案 a|b|c>/{common_mesh.json, common[_plusN]_atlas.png, _glowmask.png, icon.png}
       (作り方: python -I tools/rough/lowpoly/weapons/starter/build_starter.py。3 案の絵は docs/review/roughs_starter/<id>/)
出力 (assets/skyabyss/ 以下):
  lowpoly/items/<id>/common.json                                メッシュ (mesh_close で水密にしてから書く)
  textures/lowpoly/items/<id>/common[_plusN]_atlas{,_glow}.png
  models/item/<id>.json                                         持ち方は似た武器のモデルを写す (杖 2 つは深淵の鈴杖、短剣は水路の牙)
  textures/item/<id>.png                                        32x32 のアイコン
階級はふつうだけ (StarterWeaponItem は GearTier.COMMON) なので common しか作らない。

使い方: python tools/install/install_starter_weapons.py [--option shrine_staff=b ...]   (省くと全部 A 案。何度流してもよい)
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
SRC = ROOT / "docs/review/roughs_starter/raw"
ASSETS = ROOT / "src/main/resources/assets/skyabyss"
ITEMS = ["shrine_staff", "shrine_bell_staff", "shrine_dagger"]
LEVELS = ["", "_plus5", "_plus10"]
ANALOG = {"shrine_staff": "abyss_bell_staff", "shrine_bell_staff": "abyss_bell_staff", "shrine_dagger": "waterway_fang"}
# 一人称の手元を直す (鈴杖の B 案は鉤の先に大きな鈴が下がるので、借りた持ち方のままだと鈴が視界をふさぐ)
FIRST_PERSON = {"shrine_bell_staff": {"scale": [0.5, 0.5, 0.5], "translation": [1.6, 0.2, -0.6]}}


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
    src = SRC / item / opt
    mesh_dir = ASSETS / "lowpoly/items" / item
    tex_dir = ASSETS / "textures/lowpoly/items" / item
    mesh_dir.mkdir(parents=True, exist_ok=True)
    tex_dir.mkdir(parents=True, exist_ok=True)
    for f in list(mesh_dir.glob("*.json")) + list(tex_dir.glob("*.png")):
        f.unlink()
    mesh = json.loads((src / "common_mesh.json").read_text(encoding="utf-8"))
    mesh["texture"] = f"items/{item}/common_atlas"
    mesh.pop("glowmask", None)
    mesh, _ = mesh_close.close_mesh(mesh)
    (mesh_dir / "common.json").write_text(json.dumps(mesh, separators=(",", ":")), encoding="utf-8")
    for lv in LEVELS:
        name = f"common{lv}_atlas"
        shutil.copyfile(src / (name + ".png"), tex_dir / (name + ".png"))
        glow, lit = bake_glow(src / (name + ".png"), src / f"common{lv}_glowmask.png")
        if lit > 0:
            glow.save(tex_dir / (name + "_glow.png"))
    analog = ANALOG[item]
    models = ASSETS / "models/item"
    data = json.loads((models / f"{analog}.json").read_text(encoding="utf-8").replace(analog, item))
    for hand in ("firstperson_righthand", "firstperson_lefthand"):
        data["base"]["display"][hand].update(FIRST_PERSON.get(item, {}))
    (models / f"{item}.json").write_text(json.dumps(data, ensure_ascii=False, separators=(",", ":")), encoding="utf-8")
    shutil.copyfile(src / "icon.png", ASSETS / "textures/item" / f"{item}.png")
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
