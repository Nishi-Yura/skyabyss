"""python tools/rough/hp_midboss_probe.py name x0 y0 z0 x1 y1 z1 : boxes of the ORIGINAL model touching the region"""
import sys
sys.path.insert(0, "tools/rough")
import hp_midboss as H

m = H.load(sys.argv[1])
lo = [float(v) for v in sys.argv[2:5]]
hi = [float(v) for v in sys.argv[5:8]]
for b in m.g["bones"]:
    for i, c in enumerate(b.get("cubes", [])):
        o, s = c["origin"], c["size"]
        if all(min(hi[a], o[a] + s[a]) - max(lo[a], o[a]) > 1e-6 for a in range(3)):
            print(b["name"], i, o, s)
