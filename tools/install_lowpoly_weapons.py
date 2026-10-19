"""プレイヤーの武器 13 種の低ポリメッシュを入れる (docs/design/15 の 4.6.2)。

入力: docs/review/lowpoly_roughs/weapons_final/<id>/{common,rare,legend}[_pullN]_{mesh.json,atlas.png,glowmask.png}
       と {common,rare,legend}[_pullN]_plus{5,10}_{atlas,glowmask}.png
出力:
  assets/skyabyss/lowpoly/items/<id>/<段>[_pullN].json                      メッシュ (texture は items/<id>/<段>[_pullN]_atlas)
  assets/skyabyss/textures/lowpoly/items/<id>/<段>[_pullN][_plusN]_atlas.png  アトラス
  assets/skyabyss/textures/lowpoly/items/<id>/<段>[_pullN][_plusN]_atlas_glow.png  光る画素だけにアトラスの色を載せた画像 (光る画素 0 なら置かない)
  assets/skyabyss/models/item/<id>.json (と引き絵・盾の構え)                 base を builtin/entity にして、手に持つ・地面・額縁は client/weapon/WeaponItemRenderer が描く
  assets/skyabyss/models/item_voxel/<model>.json                              元のボクセルのモデル (フラグ LowPoly.weaponMeshes = false の戻り先)
gui の 32x32 の絵 (perspectives.gui) と display (持ち方。tools/weapon_display.py) はそのまま。
何度流してもよい (models/item/<id>.json が既に builtin/entity なら、ボクセルのコピーは触らない)。
使い方: python tools/install_lowpoly_weapons.py
"""
from __future__ import annotations

import json
import shutil
from pathlib import Path

from PIL import Image
import sys

sys.path.insert(0, str(Path(__file__).resolve().parent))
import mesh_close  # noqa: E402  (書き出す前に水密にする: tools/mesh_close.py)

ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / "docs/review/lowpoly_roughs/weapons_final"
ASSETS = ROOT / "src/main/resources/assets/skyabyss"
ITEMS = ["whalebone_greatsword", "captain_pike", "waterway_fang", "tide_shell_trident", "coral_harpoon", "tidecaller_claw",
         "coral_shield", "thunder_wing_bow", "warden_glaive", "raiju_claw", "ember_war_axe", "smith_hammer", "thrall_poker"]
TIERS = ["common", "rare", "legend"]
LEVELS = ["", "_plus5", "_plus10"]
PULLS = ["", "_pull0", "_pull1", "_pull2"]
# models/item/ にある、元のボクセルのモデル (弓の引き絵と盾の構えを含む)
MODELS = {i: [i] for i in ITEMS}
MODELS["thunder_wing_bow"] = ["thunder_wing_bow", "thunder_wing_bow_pulling_0", "thunder_wing_bow_pulling_1", "thunder_wing_bow_pulling_2"]
MODELS["coral_shield"] = ["coral_shield", "coral_shield_blocking"]


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
            for pull in PULLS if item == "thunder_wing_bow" else [""]:
                stem = tier + pull
                mesh = json.loads((src / f"{stem}_mesh.json").read_text(encoding="utf-8"))
                mesh["texture"] = f"items/{item}/{stem}_atlas"
                mesh.pop("glowmask", None)
                mesh, _ = mesh_close.close_mesh(mesh)
                (mesh_dir / f"{stem}.json").write_text(json.dumps(mesh, separators=(",", ":")), encoding="utf-8")
                for lv in LEVELS:
                    name = f"{stem}{lv}_atlas"
                    atlas = src / (name + ".png")
                    shutil.copyfile(atlas, tex_dir / (name + ".png"))
                    glow, lit = bake_glow(atlas, src / f"{stem}{lv}_glowmask.png")
                    gp = tex_dir / (name + "_glow.png")
                    if lit > 0:
                        glow.save(gp)
                    elif gp.exists():
                        gp.unlink()
                    n += 1
    print("meshes/atlases installed:", n)


def install_models():
    models = ASSETS / "models/item"
    voxel = ASSETS / "models/item_voxel"
    voxel.mkdir(parents=True, exist_ok=True)
    for item in ITEMS:
        for name in MODELS[item]:
            path = models / f"{name}.json"
            data = json.loads(path.read_text(encoding="utf-8"))
            base = data["base"]
            if "elements" in base:  # まだボクセル: 元をコピーしておく (base だけのふつうのモデルにする)
                (voxel / f"{name}.json").write_text(json.dumps(base, separators=(",", ":")), encoding="utf-8")
            elif not (voxel / f"{name}.json").exists():
                raise SystemExit(f"{name}: no voxel copy to fall back to")
            new_base = {"parent": "builtin/entity", "gui_light": base.get("gui_light", "front"), "display": base["display"],
                        "textures": {"particle": base["textures"]["particle"]}}
            data["base"] = new_base
            path.write_text(json.dumps(data, ensure_ascii=False, separators=(",", ":")), encoding="utf-8")
    print("models rewritten")


if __name__ == "__main__":
    install_meshes()
    install_models()
