#!/usr/bin/env python3
"""Install the chosen titan bodies (near / mid / far LODs) as low-poly meshes:
stone_whale <- lp_stone_whale/c, tide_shell <- lp_tide_shell/b, thunder_wing <- lp_thunder_wing/a, ember_titan <- lp_ember_titan/c.
Inputs: docs/review/lowpoly_roughs/<folder>/<option>/{near,mid,far}_{mesh.json,atlas.png,glowmask.png}.
Each LOD is installed as its own model: <name> (near), <name>_mid, <name>_far (the cube LOD geos keep those names).
Run from the repository root: python tools/install_lowpoly_titans.py"""
import subprocess
import sys

R = "docs/review/lowpoly_roughs/"
I = [sys.executable, "tools/install_lowpoly.py"]
TITANS = [("stone_whale", "lp_stone_whale/c"), ("tide_shell", "lp_tide_shell/b"),
          ("thunder_wing", "lp_thunder_wing/a"), ("ember_titan", "lp_ember_titan/c")]
for name, folder in TITANS:
    for lod, suffix in (("near", ""), ("mid", "_mid"), ("far", "_far")):
        d = R + folder + "/" + lod
        print("==", name + suffix)
        subprocess.check_call(I + [name + suffix, "--mesh", d + "_mesh.json", "--atlas", d + "_atlas.png", "--glow", d + "_glowmask.png"])
