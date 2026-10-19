#!/usr/bin/env python3
"""Install the chosen young_thunder_wing (雷翼の雛) low-poly option. Default A (placeholder until the user picks).
Inputs: tools/rough/lowpoly/young_thunder_wing/out/<option>/ (re-exported here by sheet.py <option> compare, no rendering).
The cube geo / texture / animations / notes come from tools/gen_young_thunder_wing_assets.py (same 14 bones for every option).
Afterwards it checks the installed files: open mesh edges (must be 0), triangles, animation bones, saddle top = SEAT_Y.
Run from the repository root: python tools/install_lowpoly_young_thunder_wing.py [a|b|c]"""
import json
import os
import subprocess
import sys

opt = (sys.argv[1] if len(sys.argv) > 1 else "a").lower()
if opt not in ("a", "b", "c"):
    sys.exit("option must be a, b or c")
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
os.chdir(ROOT)
NAME = "young_thunder_wing"
d = "tools/rough/lowpoly/young_thunder_wing/out/%s/%s_%s" % (opt, NAME, opt)
subprocess.check_call([sys.executable, "tools/gen_young_thunder_wing_assets.py"])
subprocess.check_call([sys.executable, "tools/rough/lowpoly/young_thunder_wing/sheet.py", opt, "compare"])
subprocess.check_call([sys.executable, "tools/install_lowpoly.py", NAME, "--mesh", d + "_mesh.json",
                       "--atlas", d + "_atlas.png", "--glow", d + "_glowmask.png"])

# ---- checks on the installed files
sys.path.insert(0, os.path.join(ROOT, "tools/rough/lowpoly/young_thunder_wing"))
import yt  # noqa: E402

A = os.path.join(ROOT, "src/main/resources/assets/skyabyss")
mesh = json.load(open(os.path.join(A, "lowpoly", NAME + ".json"), encoding="utf-8"))
skel = json.load(open(os.path.join(A, "geo/entity", NAME + "_lowpoly.geo.json"), encoding="utf-8"))
cube = json.load(open(os.path.join(A, "geo/entity", NAME + ".geo.json"), encoding="utf-8"))
anim = json.load(open(os.path.join(A, "animations/entity", NAME + ".animation.json"), encoding="utf-8"))
oe = yt.open_edges(mesh)
hs = yt.seat_heights(mesh)
sk_bones = {b["name"] for b in skel["minecraft:geometry"][0]["bones"]}
cu_bones = {b["name"] for b in cube["minecraft:geometry"][0]["bones"]}
bad = [(a, b) for a, v in anim["animations"].items() for b in v["bones"] if b not in sk_bones or b not in cu_bones]
glow = os.path.exists(os.path.join(A, "textures/lowpoly", NAME + "_glowmask.png"))
print("check %s option %s: triangles %d, open edges %d %s, saddle top min/max %.3f/%.3f units (SEAT_Y %.1f = %.3f blocks), "
      "anim bones missing %s, lowpoly glowmask %s, anims %s"
      % (NAME, opt, mesh["triangle_count"], sum(oe.values()), dict(oe), hs.min(), hs.max(), yt.SEAT_Y, yt.SEAT_Y / 16,
         bad, glow, {k.split(".")[-1]: v["animation_length"] for k, v in anim["animations"].items()}))
assert sum(oe.values()) == 0 and not bad and abs(hs.min() - yt.SEAT_Y) < 0.01 and abs(hs.max() - yt.SEAT_Y) < 0.01
