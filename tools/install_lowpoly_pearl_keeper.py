#!/usr/bin/env python3
"""Install the chosen pearl_keeper (真珠守り) low-poly option. Default A (placeholder until the user picks).
Inputs: docs/review/lowpoly_roughs/lp_pearl_keeper/<option>/ (made by tools/rough/lowpoly/pearl_keeper/sheet.py).
The cube geo / texture / animations come from tools/gen_pearl_keeper_assets.py (same 14 bones for every option).
Run from the repository root: python tools/install_lowpoly_pearl_keeper.py [a|b|c]"""
import subprocess
import sys

opt = (sys.argv[1] if len(sys.argv) > 1 else "a").lower()
if opt not in ("a", "b", "c"):
    sys.exit("option must be a, b or c")
d = "docs/review/lowpoly_roughs/lp_pearl_keeper/%s/pearl_keeper_%s" % (opt, opt)
subprocess.check_call([sys.executable, "tools/gen_pearl_keeper_assets.py"])
subprocess.check_call([sys.executable, "tools/install_lowpoly.py", "pearl_keeper", "--mesh", d + "_mesh.json",
                       "--atlas", d + "_atlas.png", "--glow", d + "_glowmask.png"])
