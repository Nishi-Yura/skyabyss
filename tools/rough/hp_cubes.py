"""List cubes of a bone: python tools/rough/hp_cubes.py bone_sentinel head [kind]"""
import json
import sys

name, bone = sys.argv[1], sys.argv[2]
kind = sys.argv[3] if len(sys.argv) > 3 else "entity"
g = json.load(open(f"src/main/resources/assets/skyabyss/geo/{kind}/{name}.geo.json", encoding="utf-8"))["minecraft:geometry"][0]
for b in g["bones"]:
    if b["name"] == bone:
        print("pivot", b.get("pivot"), "parent", b.get("parent"))
        for i, c in enumerate(b.get("cubes", [])):
            print(i, c["origin"], c["size"], sorted(c["uv"].keys()) if isinstance(c.get("uv"), dict) else c.get("uv"))
