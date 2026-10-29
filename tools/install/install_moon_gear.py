# -*- coding: utf-8 -*-
"""六章「月」の装備 (docs/design/03 の 12.2) の低ポリを assets へ入れる。

入力 (先に作る):
  python -I tools/rough/lowpoly/gear_gaps/armor_moon.py                       月渡りの装束 4 部位 → docs/review/roughs_gear_gaps/raw/armor_moon/a/
  python -I tools/rough/lowpoly/weapons/one/build_one.py o_moon               武器 6 本 → docs/review/roughs_one/raw/<id>/a/
  python -I tools/gen/ch6/gen_moon_gear_assets.py                             環・涙・素材の平らな絵と月の暦のブロック
出力:
  lowpoly/armor/moon_walker/<部位>{,_rare}.json, textures/lowpoly/armor/moon_walker/<部位>{,_rare}{,_plus5,_plus10}{,_glowmask}.png   (LowPolyArmorRenderer)
  textures/item/moon_walker_<部位>.png と平らなモデル
  武器は install_job_weapons.py に任せる (持ち方は同じ型の武器を借りる)
使い方: python -I tools/install/install_moon_gear.py
"""
from __future__ import annotations

import subprocess
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
sys.path.insert(0, str(HERE.parent / "lib"))
import install_abyss_gear as G  # noqa: E402

SRC = G.SRC / "armor_moon"
WEAPONS = ["moon_scale_hammer", "moon_tide_staff", "moon_waning_bow", "moon_crescent_dagger", "moon_starlight_bell_staff", "moon_waning_blade"]


def install_armor():
    import json
    import shutil
    from PIL import Image
    src, icons = SRC / "a", SRC / "icons"
    mesh_dir = G.ASSETS / "lowpoly/armor/moon_walker"
    tex_dir = G.ASSETS / "textures/lowpoly/armor/moon_walker"
    mesh_dir.mkdir(parents=True, exist_ok=True)
    tex_dir.mkdir(parents=True, exist_ok=True)
    for f in list(mesh_dir.glob("*.json")) + list(tex_dir.glob("*.png")):
        f.unlink()
    for p in G.PIECES:
        d = json.loads((src / f"{p}_mesh.json").read_text(encoding="utf-8"))
        d["socket"] = False
        d = G.close(d)
        for suffix in ("", "_rare"):
            d["texture"] = f"armor/moon_walker/{p}{suffix}"
            (mesh_dir / f"{p}{suffix}.json").write_text(json.dumps(d, separators=(",", ":")), encoding="utf-8")
            for lv in ("",) + (("_plus5", "_plus10") if suffix == "_rare" else ()):
                atlas, mask = src / f"{p}{lv}_atlas.png", src / f"{p}{lv}_glowmask.png"
                glow, lit = G.bake_glow(atlas, mask)
                if lit == 0:
                    sys.exit(p + lv + ": no glow pixels (GeckoLib crashes on an empty glowmask)")
                Image.open(atlas).convert("RGBA").save(tex_dir / f"{p}{suffix}{lv}.png", optimize=True)
                glow.save(tex_dir / f"{p}{suffix}{lv}_glowmask.png", optimize=True)
        shutil.copyfile(icons / f"a_{p}_32.png", G.ASSETS / "textures/item" / f"moon_walker_{p}.png")
        G.flat_model(f"moon_walker_{p}")
    print("moon_walker armor installed")


def main():
    install_armor()
    subprocess.check_call([sys.executable, "-I", str(HERE / "install_job_weapons.py")] + WEAPONS)
    subprocess.check_call([sys.executable, "-I", str(HERE.parent / "lib/weapon_display.py")])


if __name__ == "__main__":
    main()
