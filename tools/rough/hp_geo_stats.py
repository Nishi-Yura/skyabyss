"""Tabulate cube counts / smallest cube / texture size / bones for every geo model (high-poly baseline).

python tools/hp/geo_stats.py [--md]
"""
import glob
import json
import os
import sys

root = "src/main/resources/assets/skyabyss/geo"


def stats(path):
    d = json.load(open(path, encoding="utf-8"))
    g = d["minecraft:geometry"][0]
    desc = g["description"]
    n = 0
    mn = 1e9
    big = 0
    for b in g["bones"]:
        for c in b.get("cubes", []):
            n += 1
            nz = [x for x in c["size"] if x > 0]
            if nz:
                mn = min(mn, min(nz))
            big = max(big, max(c["size"]))
    return (n, len(g["bones"]), mn if n else 0, big, desc.get("texture_width"), desc.get("texture_height"))


def main():
    rows = []
    for f in sorted(glob.glob(root + "/**/*.geo.json", recursive=True)):
        rel = os.path.relpath(f, root).replace("\\", "/").replace(".geo.json", "")
        rows.append((rel,) + stats(f))
    if "--md" in sys.argv:
        print("| model | cubes | bones | min | max | tex |\n|---|---|---|---|---|---|")
        for r in rows:
            print("| %s | %d | %d | %g | %g | %sx%s |" % r)
    else:
        for r in rows:
            print("%-34s cubes=%4d bones=%3d min=%-5g max=%-5g tex=%sx%s" % r)
    print("total cubes", sum(r[1] for r in rows))


if __name__ == "__main__":
    main()
