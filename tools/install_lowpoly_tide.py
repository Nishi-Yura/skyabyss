#!/usr/bin/env python3
"""Install the chosen tide-back low-poly models (reef_scuttler A, bubble_puffer A, tide_beckoner A, tide heart B p1-p3).
Inputs: docs/review/lowpoly_roughs/<folder>/<option>/. Run from the repository root: python tools/install_lowpoly_tide.py"""
import subprocess
import sys

R = "docs/review/lowpoly_roughs/"
I = [sys.executable, "tools/install_lowpoly.py"]


def one(name, folder, stem):
    d = R + folder + "/"
    return [name, "--mesh", d + stem + "_mesh.json", "--atlas", d + stem + "_atlas.png", "--glow", d + stem + "_glowmask.png"]


jobs = [
    one("reef_scuttler", "reef_scuttler/a", "reef_scuttler_a"),
    one("bubble_puffer", "lp_bubble_puffer/a", "lp_bubble_puffer_a"),
    one("tide_beckoner", "lp_tide_beckoner/a", "tide_beckoner_a"),
] + [one("tide_heart_p%d" % k, "lp_tide_heart/b", "p%d" % k) for k in (1, 2, 3)]
for j in jobs:
    print("==", j[0])
    subprocess.check_call(I + j)
