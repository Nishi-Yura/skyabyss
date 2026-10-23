#!/usr/bin/env python3
"""Install the chosen young_stone_whale (岩鯨の仔) low-poly option. Default A (placeholder until the user picks).
Inputs: tools/rough/lowpoly/young_stone_whale/out/<option>/ (made by tools/rough/lowpoly/young_stone_whale/sheet.py,
which is re-run here when the option's files are missing).
The cube geo / texture / animations / notes come from tools/gen/world/gen_young_stone_whale_assets.py (same 9 bones for every option).
Then the installed mesh is audited: open edges (must be 0), triangle count, animation bones exist, saddle top = SEAT_Y.
Run from the repository root: python tools/install/install_lowpoly_young_stone_whale.py [a|b|c]"""
import json
import os
import subprocess
import sys
from collections import Counter

opt = (sys.argv[1] if len(sys.argv) > 1 else "a").lower()
if opt not in ("a", "b", "c"):
    sys.exit("option must be a, b or c")
NAME = "young_stone_whale"
d = "tools/rough/lowpoly/young_stone_whale/out/%s/%s_%s" % (opt, NAME, opt)
if not os.path.exists(d + "_mesh.json"):
    subprocess.check_call([sys.executable, "tools/rough/lowpoly/young_stone_whale/sheet.py"])
subprocess.check_call([sys.executable, "tools/gen/world/gen_young_stone_whale_assets.py"])
subprocess.check_call([sys.executable, "tools/install/install_lowpoly.py", NAME, "--mesh", d + "_mesh.json",
                       "--atlas", d + "_atlas.png", "--glow", d + "_glowmask.png"])

# ---------------------------------------------------------------- audit of the installed files
A = os.path.join("src", "main", "resources", "assets", "skyabyss")
mesh = json.load(open(os.path.join(A, "lowpoly", NAME + ".json"), encoding="utf-8"))
anim = json.load(open(os.path.join(A, "animations", "entity", NAME + ".animation.json"), encoding="utf-8"))
geo = json.load(open(os.path.join(A, "geo", "entity", NAME + "_lowpoly.geo.json"), encoding="utf-8"))
bones = {b["name"] for b in geo["minecraft:geometry"][0]["bones"]}
assert geo["minecraft:geometry"][0]["bones"][0]["name"] == "root"
tris = 0
open_edges = 0
seat_hits = []
for bn, b in mesh["bones"].items():
    v = b["vertices"]
    edges = Counter()
    for t in b["triangles"]:
        tris += 1
        for a, c in ((0, 1), (1, 2), (2, 0)):
            edges[tuple(sorted((tuple(round(x, 2) for x in v[t[a]]), tuple(round(x, 2) for x in v[t[c]]))))] += 1
    open_edges += sum(1 for k, c in edges.items() if c == 1)
for an, a in anim["animations"].items():
    for b in a["bones"]:
        assert b in bones, (an, b)
# saddle top: the highest mesh surface straight above the 6 seats (geo space; seats are symmetric so the x/z flip does not matter)
SEAT_Y = mesh.get("seat_y", 58.0)


def top_at(x, z):
    best = None
    for b in mesh["bones"].values():
        v = b["vertices"]
        for t in b["triangles"]:
            A_, B_, C_ = (v[i] for i in t)
            den = (B_[2] - C_[2]) * (A_[0] - C_[0]) + (C_[0] - B_[0]) * (A_[2] - C_[2])
            if abs(den) < 1e-9:
                continue
            l0 = ((B_[2] - C_[2]) * (x - C_[0]) + (C_[0] - B_[0]) * (z - C_[2])) / den
            l1 = ((C_[2] - A_[2]) * (x - C_[0]) + (A_[0] - C_[0]) * (z - C_[2])) / den
            l2 = 1 - l0 - l1
            if min(l0, l1, l2) >= -1e-6:
                y = l0 * A_[1] + l1 * B_[1] + l2 * C_[1]
                best = y if best is None else max(best, y)
    return best


for sx in (-16, 16):
    for sz in (-32, 0, 32):
        seat_hits.append(top_at(sx, sz))
print("audit %s (option %s): tris=%d open_edges=%d anims=%s seat tops=%s (SEAT_Y=%s units = %.3f blocks)"
      % (NAME, opt, tris, open_edges, sorted(k.split(".")[-1] for k in anim["animations"]),
         [round(s, 3) for s in seat_hits], SEAT_Y, SEAT_Y / 16))
assert open_edges == 0
assert tris < 4000   # the other young: 3,100-3,400
assert all(s is not None and abs(s - SEAT_Y) < 0.01 for s in seat_hits)
