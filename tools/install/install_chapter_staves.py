"""章ごとの杖と鈴杖 8 点の低ポリメッシュを入れる (docs/design/43 の 5 段目。item/ChapterGear)。

入力: docs/review/roughs_staves/raw/<id>/<案 a|b|c>/{rare_mesh.json, rare[_plusN]_atlas.png, _glowmask.png, icon.png}
       (作り方: python -I tools/rough/lowpoly/weapons/chapter_staves/build_staves.py。3 案の絵は docs/review/roughs_staves/<id>/)
出力は祠の始めの武器 (install_starter_weapons.py) と同じ形。階級はエピック (形の段 rare) なので rare だけを入れる。

使い方: python tools/install/install_chapter_staves.py [--option whale_staff=b ...]   (省くと全部 A 案。何度流してもよい)
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
SRC = ROOT / "docs/review/roughs_staves/raw"
ASSETS = ROOT / "src/main/resources/assets/skyabyss"
ITEMS = ["whale_staff", "whale_bell_staff", "tide_staff", "tide_bell_staff", "storm_staff", "storm_bell_staff", "ember_staff", "ember_censer"]
LEVELS = ["", "_plus5", "_plus10"]
STEM = "rare"
# 持ち方は祠の杖 (深淵の鈴杖を借りた形)。鉤から鐘が垂れる案は一人称を小さく (祠の鈴杖の B 案と同じ)
ANALOG = "abyss_bell_staff"
HOOKED = {"whale_bell_staff": "a", "tide_bell_staff": "a", "storm_bell_staff": "a", "ember_censer": "a"}
FIRST_PERSON_HOOK = {"scale": [0.5, 0.5, 0.5], "translation": [1.6, 0.2, -0.6]}


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
    mesh = json.loads((src / f"{STEM}_mesh.json").read_text(encoding="utf-8"))
    mesh["texture"] = f"items/{item}/{STEM}_atlas"
    mesh.pop("glowmask", None)
    mesh, _ = mesh_close.close_mesh(mesh)
    (mesh_dir / f"{STEM}.json").write_text(json.dumps(mesh, separators=(",", ":")), encoding="utf-8")
    for lv in LEVELS:
        name = f"{STEM}{lv}_atlas"
        shutil.copyfile(src / (name + ".png"), tex_dir / (name + ".png"))
        glow, lit = bake_glow(src / (name + ".png"), src / f"{STEM}{lv}_glowmask.png")
        if lit > 0:
            glow.save(tex_dir / (name + "_glow.png"))
    models = ASSETS / "models/item"
    data = json.loads((models / f"{ANALOG}.json").read_text(encoding="utf-8").replace(ANALOG, item))
    if HOOKED.get(item) == opt:
        for hand in ("firstperson_righthand", "firstperson_lefthand"):
            data["base"]["display"][hand].update(FIRST_PERSON_HOOK)
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
