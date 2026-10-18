"""装飾品・補助の品 16 種の低ポリメッシュを入れる (docs/design/15 の 4.6.3、03 の 6.3)。

入力: docs/review/lowpoly_roughs/accessories_final/<id>/{common,rare,legend}_{mesh.json,atlas.png,glowmask.png}
       (tools/rough/lowpoly/accessories/build_final.py が作る。採用した A 案)
出力:
  assets/skyabyss/lowpoly/items/<id>/<段>.json                      メッシュ (texture は items/<id>/<段>_atlas)
  assets/skyabyss/textures/lowpoly/items/<id>/<段>_atlas.png         アトラス
  assets/skyabyss/textures/lowpoly/items/<id>/<段>_atlas_glow.png    光る画素だけ (無ければ置かない)
  assets/skyabyss/models/item/<id>.json                             base を builtin/entity にして、手・地面・額縁・頭は client/accessory/AccessoryItemRenderer が描く。
                                                                    gui は今までの 32x32 の絵のまま
  assets/skyabyss/models/item_flat/<id>.json                        元の平絵のモデル (フラグ LowPoly.accessoryMeshes = false の戻り先)
装飾品は鍛えない (03 の 4 章) ので、強化 (+5 / +10) の絵は無い。
何度流してもよい。使い方: python tools/install_lowpoly_accessories.py
"""
from __future__ import annotations

import json
import shutil
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / "docs/review/lowpoly_roughs/accessories_final"
ASSETS = ROOT / "src/main/resources/assets/skyabyss"
ITEMS = ["storm_charm", "storm_anklet", "storm_eye_feather", "gale_feather",
         "kindling_charm", "ember_mantle", "ember_lantern", "ember_horn", "cinder_lamp",
         "resonance_charm", "moss_talisman", "stone_whale_plating",
         "tide_chime_charm", "moontide_pearl", "tide_watching_shell", "tide_pearl"]
TIERS = ["common", "rare", "legend"]

# 持ち方 (バニラの item/generated の表を土台に、メッシュ用に調えたもの。全品共通)
DISPLAY = {
    "thirdperson_righthand": {"rotation": [0, -45, 0], "translation": [0, 3, 1], "scale": [0.85, 0.85, 0.85]},
    "thirdperson_lefthand": {"rotation": [0, 45, 0], "translation": [0, 3, 1], "scale": [0.85, 0.85, 0.85]},
    "firstperson_righthand": {"rotation": [0, -60, 10], "translation": [0.8, 2.5, 1.5], "scale": [0.55, 0.55, 0.55]},
    "firstperson_lefthand": {"rotation": [0, 60, -10], "translation": [0.8, 2.5, 1.5], "scale": [0.55, 0.55, 0.55]},
    "ground": {"rotation": [0, 0, 0], "translation": [0, 2, 0], "scale": [0.55, 0.55, 0.55]},
    "head": {"rotation": [0, 180, 0], "translation": [0, 13, 7], "scale": [0.9, 0.9, 0.9]},
    "fixed": {"rotation": [0, 180, 0], "translation": [0, 0, 0], "scale": [0.85, 0.85, 0.85]},
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


def install_meshes():
    n = 0
    for item in ITEMS:
        src = SRC / item
        mesh_dir = ASSETS / "lowpoly/items" / item
        tex_dir = ASSETS / "textures/lowpoly/items" / item
        mesh_dir.mkdir(parents=True, exist_ok=True)
        tex_dir.mkdir(parents=True, exist_ok=True)
        for tier in TIERS:
            mesh = json.loads((src / f"{tier}_mesh.json").read_text(encoding="utf-8"))
            mesh["texture"] = f"items/{item}/{tier}_atlas"
            mesh.pop("glowmask", None)
            (mesh_dir / f"{tier}.json").write_text(json.dumps(mesh, separators=(",", ":")), encoding="utf-8")
            name = f"{tier}_atlas"
            shutil.copyfile(src / f"{tier}_atlas.png", tex_dir / (name + ".png"))
            glow, lit = bake_glow(src / f"{tier}_atlas.png", src / f"{tier}_glowmask.png")
            gp = tex_dir / (name + "_glow.png")
            if lit > 0:
                glow.save(gp)
            elif gp.exists():
                gp.unlink()
            n += 1
    print("meshes/atlases installed:", n)


def install_models():
    models = ASSETS / "models/item"
    flat = ASSETS / "models/item_flat"
    flat.mkdir(parents=True, exist_ok=True)
    for item in ITEMS:
        path = models / f"{item}.json"
        data = json.loads(path.read_text(encoding="utf-8"))
        if data.get("loader") == "neoforge:separate_transforms":
            if not (flat / f"{item}.json").exists():
                raise SystemExit(f"{item}: no flat copy to fall back to")
        else:  # まだ平絵 (または燠の灯のブロックのモデル): 元をコピーしておく
            (flat / f"{item}.json").write_text(json.dumps(data, separators=(",", ":")), encoding="utf-8")
        tex = f"skyabyss:item/{item}"
        new = {"loader": "neoforge:separate_transforms",
               "base": {"parent": "builtin/entity", "gui_light": "front", "display": DISPLAY, "textures": {"particle": tex}},
               "perspectives": {"gui": {"parent": "minecraft:item/generated", "textures": {"layer0": tex}}}}
        path.write_text(json.dumps(new, ensure_ascii=False, separators=(",", ":")), encoding="utf-8")
    print("models rewritten")


if __name__ == "__main__":
    install_meshes()
    install_models()
