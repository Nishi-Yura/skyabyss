"""Print bone tree / cube key summary for named geo models.  python tools/hp/geo_info.py raiju tide_heart_p1"""
import json
import sys

A = "src/main/resources/assets/skyabyss/geo/"
for n in sys.argv[1:]:
    path = A + (n if "/" in n else "entity/" + n) + ".geo.json"
    g = json.load(open(path, encoding="utf-8"))["minecraft:geometry"][0]
    print(n, g["description"])
    keys = set()
    for b in g["bones"]:
        print("  ", b["name"], b.get("parent"), len(b.get("cubes", [])), b.get("rotation"), b.get("pivot"))
        for c in b.get("cubes", []):
            keys |= set(c.keys())
    print("cube keys:", keys)
