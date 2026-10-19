"""深淵の専用伝説 4 点 (abyss_greatsword / abyss_bell_staff / abyss_storm_bow / abyss_ash_scythe) の低ポリメッシュを入れる (docs/design/03 の 3.7)。

入力: docs/review/lowpoly_roughs/abyss_weapons/<id>/<案 a|b|c>/{legend[_pullN]_mesh.json, legend[_pullN][_plusN]_atlas.png, _glowmask.png, icon[_pullN].png}
       (作り方: python tools/rough/lowpoly/weapons/abyss/build_abyss.py。3 案の絵と選定ページは docs/review/abyss_weapons/)
出力 (assets/skyabyss/ 以下):
  lowpoly/items/<id>/legend[_pullN].json                         メッシュ (mesh_close で水密にしてから書く)
  textures/lowpoly/items/<id>/legend[_pullN][_plusN]_atlas{,_glow}.png
  models/item/<id>[_pulling_N].json                              base を builtin/entity (client/weapon/WeaponItemRenderer が描く)。持ち方は tools/weapon_display.py
  textures/item/<id>[_pulling_N].png                             32x32 のアイコン (メッシュを傾き -45 度のまま正面から写したもの)
階級は伝説だけ (abyss の品は GearTier.LEGENDARY 固定) なので legend しか作らない。

使い方: python tools/install_lowpoly_abyss_weapons.py [--option abyss_greatsword=b ...]   (省くと全部 A 案。何度流してもよい)
"""
from __future__ import annotations

import copy
import json
import shutil
import sys
from pathlib import Path

from PIL import Image

sys.path.insert(0, str(Path(__file__).resolve().parent))
import mesh_close  # noqa: E402

ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / "docs/review/lowpoly_roughs/abyss_weapons"
ASSETS = ROOT / "src/main/resources/assets/skyabyss"
ITEMS = ["abyss_greatsword", "abyss_bell_staff", "abyss_storm_bow", "abyss_ash_scythe"]
LEVELS = ["", "_plus5", "_plus10"]
# 持ち方と枠の元にする、すでに低ポリ化している武器のモデル (display は weapon_display.py が整える)
ANALOG = {"abyss_greatsword": "whalebone_greatsword", "abyss_bell_staff": "thrall_poker", "abyss_storm_bow": "thunder_wing_bow",
          "abyss_ash_scythe": "ember_war_axe"}
STAGES = {"abyss_storm_bow": 4}


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


def stem(stage: int) -> str:
    return "legend" if stage == 0 else f"legend_pull{stage - 1}"


def model_names(item: str):
    n = STAGES.get(item, 1)
    return [item] + [f"{item}_pulling_{i}" for i in range(n - 1)]


def install(item: str, opt: str):
    src = SRC / item / opt
    mesh_dir = ASSETS / "lowpoly/items" / item
    tex_dir = ASSETS / "textures/lowpoly/items" / item
    mesh_dir.mkdir(parents=True, exist_ok=True)
    tex_dir.mkdir(parents=True, exist_ok=True)
    # 前の案の残りを消す
    for f in list(mesh_dir.glob("*.json")) + list(tex_dir.glob("*.png")):
        f.unlink()
    for stg in range(STAGES.get(item, 1)):
        st = stem(stg)
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
    # item モデルとアイコン
    analog = ANALOG[item]
    models = ASSETS / "models/item"
    for k, name in enumerate(model_names(item)):
        a_name = name.replace(item, analog)
        text = (models / f"{a_name}.json").read_text(encoding="utf-8").replace(analog, item)
        data = json.loads(text)
        (models / f"{name}.json").write_text(json.dumps(data, ensure_ascii=False, separators=(",", ":")), encoding="utf-8")
        icon = src / (("icon" if k == 0 else f"icon_pull{k - 1}") + ".png")
        shutil.copyfile(icon, ASSETS / "textures/item" / f"{name}.png")
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
