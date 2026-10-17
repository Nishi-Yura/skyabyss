"""Install the four titan armors of design base v2 (docs/design/16_armor_design.md).

  python tools/install_armor_v2.py [--force] [--no-icons]

Builds stone_whale / tide_shell / thunder_wing / ember from tools/rough/armor_v2_sets.py (deterministic) and writes
  geo/armor/<name>_armor.geo.json, textures/armor/<name>_armor{,_glowmask}.png
  textures/item/<name>_{helmet,chestplate,leggings,boots}.png   (32x32, drawn from the 3D pieces by armor_v2_icons.py)
Bones, pivots and names (armorHead ... armorLeftBoot) are the GeckoLib armor bones; animations/vanilla layers untouched.
Refuses to write when an oriented-box overlap remains (--force to override) or the glow mask is empty.
"""
import argparse
import json
import sys
from pathlib import Path

import numpy as np
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "tools/rough"))
import armor_v2_sets as S  # noqa: E402

RES = ROOT / "src/main/resources/assets/skyabyss"
NAMES = {"stone_whale": "stone_whale_armor", "tide_shell": "tide_shell_armor", "thunder_wing": "thunder_wing_armor", "ember": "ember_armor"}
FACE_BUDGET = 3000


def slot_stats(geo, glow):
    """{slot: (faces, glow pixels)} from the written geo: glow pixels are counted in the uv rectangles of the slot's bones"""
    out = {}
    bones = {b["name"]: b for b in geo["minecraft:geometry"][0]["bones"]}
    for slot, names in __import__("armor_kit").PIECE_BONES.items():
        faces = gl = 0
        for bn in names:
            for cd in bones[bn]["cubes"]:
                for f in cd["uv"].values():
                    faces += 1
                    u, v = f["uv"]
                    w, h = f["uv_size"]
                    x0, x1 = sorted((int(u), int(u + w)))
                    y0, y1 = sorted((int(v), int(v + h)))
                    gl += int((glow[y0:max(y1, y0 + 1), x0:max(x1, x0 + 1), 3] > 0).sum())
        out[slot] = (faces, gl)
    return out


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--force", action="store_true")
    ap.add_argument("--no-icons", action="store_true")
    ap.add_argument("names", nargs="*", default=list(NAMES))
    ar = ap.parse_args()
    import os
    os.chdir(ROOT)
    built = {}
    for n in ar.names:
        a = S.SETS[n]()
        ov = a.overlaps()
        geo, tex, glow = a.build(chamfer=S.CHAMFER)
        cubes, faces = a.stats()
        g = geo["minecraft:geometry"][0]
        axis_ov = [x for x in __import__("voxel").check_overlaps(geo) if "overlap" in x]
        print(f"{n}: cubes {cubes} faces {faces} tex {tex.shape[0]} overlaps(oriented) {len(ov)} overlaps(axis, after chamfer) {len(axis_ov)}")
        assert (glow[..., 3] > 0).any(), f"{n}: empty glow mask"
        for slot, (fq, gp) in slot_stats(geo, glow).items():
            print(f"   {slot}: {fq} faces, {gp} glow px")
            assert gp > 0, f"{n} {slot}: no glowing pixel"
        for pose, msgs in a.pose_collisions(tol=2.5).items():
            for m in msgs:
                print("   pose", pose, m)
        assert faces <= FACE_BUDGET, f"{n}: {faces} faces > {FACE_BUDGET}"
        if (ov or axis_ov) and not ar.force:
            for x in (ov + axis_ov)[:10]:
                print("   ", x)
            sys.exit(f"{n}: overlaps remain (use --force to write anyway)")
        built[n] = (geo, tex, glow)
    for n, (geo, tex, glow) in built.items():
        nm = NAMES[n]
        (RES / "geo/armor" / f"{nm}.geo.json").write_text(json.dumps(geo, ensure_ascii=False, separators=(",", ":")), encoding="utf-8")
        Image.fromarray(tex).save(RES / "textures/armor" / f"{nm}.png")
        Image.fromarray(glow).save(RES / "textures/armor" / f"{nm}_glowmask.png")
        print("wrote", nm)
    if not ar.no_icons:
        try:
            import armor_v2_icons
        except ImportError:
            print("icons: armor_v2_icons.py not present yet, skipped")
        else:
            armor_v2_icons.install(ar.names)


if __name__ == "__main__":
    main()
