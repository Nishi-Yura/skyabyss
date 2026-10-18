#!/usr/bin/env python3
"""Install the five chosen low-poly models (captain A, crawler B, archer C, maw B, stone whale heart A p1-p3).
Inputs: docs/review/lowpoly_roughs/<folder>/<option>/. Run from the repository root: python tools/install_lowpoly_five.py"""
import subprocess
import sys

R = "docs/review/lowpoly_roughs/"
I = [sys.executable, "tools/install_lowpoly.py"]
jobs = [
    ["whalebone_captain", "--mesh", R + "whalebone_captain/a/whalebone_captain_lowpoly_mesh.json",
     "--atlas", R + "whalebone_captain/a/whalebone_captain_lowpoly_atlas.png",
     "--glow", R + "whalebone_captain/a/whalebone_captain_lowpoly_glowmask.png",
     "--gear", R + "whalebone_captain/a/captain_pike_mesh.json:" + R + "whalebone_captain/a/captain_pike_atlas.png:captain_pike:socket_main"],
    ["moss_crawler", "--mesh", R + "lp_moss_crawler/b/moss_crawler_lowpoly_mesh.json",
     "--atlas", R + "lp_moss_crawler/b/moss_crawler_lowpoly_atlas.png",
     "--glow", R + "lp_moss_crawler/b/moss_crawler_lowpoly_glowmask.png"],
    ["rib_archer", "--mesh", R + "rib_archer/c/rib_archer_c_mesh.json",
     "--atlas", R + "rib_archer/c/rib_archer_c_atlas.png",
     "--glow", R + "rib_archer/c/rib_archer_c_glowmask.png",
     "--gear", R + "rib_archer/c/rib_archer_c_bow_mesh.json:" + R + "rib_archer/c/rib_archer_c_bow_atlas.png:rib_archer_bow:socket_off"],
    ["waterway_maw", "--mesh", R + "waterway_maw/b/waterway_maw_lp_mesh.json",
     "--atlas", R + "waterway_maw/b/waterway_maw_lp_atlas.png",
     "--glow", R + "waterway_maw/b/waterway_maw_lp_glowmask.png"],
] + [["stone_whale_heart_p%d" % k, "--mesh", R + "lp_whale_heart/a/p%d_mesh.json" % k,
      "--atlas", R + "lp_whale_heart/a/p%d_atlas.png" % k,
      "--glow", R + "lp_whale_heart/a/p%d_glowmask.png" % k] for k in (1, 2, 3)]
for j in jobs:
    print("==", j[0])
    subprocess.check_call(I + j)
