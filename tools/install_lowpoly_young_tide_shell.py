#!/usr/bin/env python3
"""Install the chosen young_tide_shell (潮殻の稚貝, the tide shell's young carrier) low-poly option. Default A (placeholder until the user picks).
Inputs: docs/review/young_roughs/young_tide_shell_mesh/<option>/ (made by tools/rough/lowpoly/young_tide_shell/sheet.py <option>).
The cube geo / texture / glowmask / animations / notes come from tools/gen_young_tide_shell_assets.py (same 10 bones for every option).
After installing, the installed mesh is audited: open edges must be 0 and the saddle top must be SEAT_Y.
Run from the repository root: python tools/install_lowpoly_young_tide_shell.py [a|b|c]"""
import json
import os
import subprocess
import sys
from collections import Counter

opt = (sys.argv[1] if len(sys.argv) > 1 else "a").lower()
if opt not in ("a", "b", "c"):
    sys.exit("option must be a, b or c")
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
os.chdir(ROOT)
d = "docs/review/young_roughs/young_tide_shell_mesh/%s/young_tide_shell_%s" % (opt, opt)
if not os.path.exists(d + "_mesh.json"):
    subprocess.check_call([sys.executable, "tools/rough/lowpoly/young_tide_shell/sheet.py", opt])
subprocess.check_call([sys.executable, "tools/gen_young_tide_shell_assets.py"])
subprocess.check_call([sys.executable, "tools/install_lowpoly.py", "young_tide_shell", "--mesh", d + "_mesh.json",
                       "--atlas", d + "_atlas.png", "--glow", d + "_glowmask.png"])

# ---- audit the installed mesh (same open-edge rule as tools/titan_mesh_audit.py)
A = os.path.join(ROOT, "src/main/resources/assets/skyabyss")
m = json.load(open(os.path.join(A, "lowpoly/young_tide_shell.json"), encoding="utf-8"))
geo = json.load(open(os.path.join(A, "geo/entity/young_tide_shell.geo.json"), encoding="utf-8"))
anim = json.load(open(os.path.join(A, "animations/entity/young_tide_shell.animation.json"), encoding="utf-8"))
notes = json.load(open(os.path.join(ROOT, "tools/sources/young/young_tide_shell_notes.json"), encoding="utf-8"))
edges = Counter()
tris = 0
for bn, b in m["bones"].items():
    v = b["vertices"]
    for t in b["triangles"]:
        tris += 1
        for a, c in ((0, 1), (1, 2), (2, 0)):
            p, q = tuple(round(x, 2) for x in v[t[a]]), tuple(round(x, 2) for x in v[t[c]])
            edges[(bn,) + tuple(sorted((p, q)))] += 1
open_edges = sum(1 for c in edges.values() if c == 1)
geo_bones = {b["name"] for b in geo["minecraft:geometry"][0]["bones"]}
assert set(m["bones"]) <= geo_bones, set(m["bones"]) - geo_bones
for an, a in anim["animations"].items():
    for b in a["bones"]:
        assert b in geo_bones, (an, b)
seat = notes["seat_y"] * 16.0
deck_top = sum(1 for x, y, z in m["bones"]["saddle"]["vertices"] if abs(y - seat) < 0.01)
above = sum(1 for b in m["bones"].values() for x, y, z in b["vertices"] if abs(x) <= 25.6 and abs(z) <= 51.2 and y > seat + 0.01)
print("young_tide_shell (%s): tris=%d open_edges=%d saddle vertices at SEAT_Y=%.2f blocks: %d, vertices above the seat area: %d, "
      "bones=%d anims=%s" % (opt, tris, open_edges, notes["seat_y"], deck_top, above, len(m["bones"]), sorted(anim["animations"])))
assert open_edges == 0 and deck_top > 0 and above == 0
