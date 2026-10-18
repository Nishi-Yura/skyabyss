"""今の geo (近景) の外形の統計: python tools/rough/lowpoly/lp_ember_titan/geostat.py"""
import json
from pathlib import Path

import numpy as np

GEO = Path(__file__).resolve().parents[4] / "src/main/resources/assets/skyabyss/geo/entity"
g = json.loads((GEO / "ember_titan.geo.json").read_text(encoding="utf-8"))["minecraft:geometry"][0]
for b in g["bones"]:
    cs = b.get("cubes", [])
    if not cs:
        continue
    o = np.array([c["origin"] for c in cs])
    s = np.array([c["size"] for c in cs])
    print(b["name"], len(cs), o.min(0), (o + s).max(0))


def prof(name, axis, step, key):
    bone = [b for b in g["bones"] if b["name"] == name][0]
    cs = bone["cubes"]
    o = np.array([c["origin"] for c in cs])
    s = np.array([c["size"] for c in cs])
    hi = o + s
    zs = np.arange(-216, 256, step)
    for z in zs:
        m = (o[:, axis] <= z + step) & (hi[:, axis] >= z)
        if m.any():
            print(name, z, "x", o[m, 0].min(), hi[m, 0].max(), "y", o[m, 1].min(), hi[m, 1].max())


import sys

if len(sys.argv) > 1:
    prof(sys.argv[1], 2, int(sys.argv[2]), 0)
