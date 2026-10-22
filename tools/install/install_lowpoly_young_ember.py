#!/usr/bin/env python3
"""Install the chosen young_ember (余燼の仔) low-poly option. Default A (provisional until the user picks).
Inputs: tools/rough/lowpoly/young_ember/out/<option>/ (made by tools/rough/lowpoly/young_ember/sheet.py; rebuilt here if missing).
The cube geo / texture / glowmask / animations come from tools/gen/world/gen_young_ember_assets.py (same 13 bones for every option).
Run from the repository root: python tools/install/install_lowpoly_young_ember.py [a|b|c]"""
import os
import subprocess
import sys

opt = (sys.argv[1] if len(sys.argv) > 1 else "a").lower()
if opt not in ("a", "b", "c"):
    sys.exit("option must be a, b or c")
d = "tools/rough/lowpoly/young_ember/out/%s/young_ember_%s" % (opt, opt)
subprocess.check_call([sys.executable, "tools/gen/world/gen_young_ember_assets.py"])
if not os.path.exists(d + "_mesh.json"):
    subprocess.check_call([sys.executable, "tools/rough/lowpoly/young_ember/sheet.py"])
subprocess.check_call([sys.executable, "tools/install/install_lowpoly.py", "young_ember", "--mesh", d + "_mesh.json",
                       "--atlas", d + "_atlas.png", "--glow", d + "_glowmask.png"])
