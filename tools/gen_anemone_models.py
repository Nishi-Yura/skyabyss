"""灯の台・苗床・数え石・品のモデルと blockstate を作る (docs/design/23)。python -I tools/gen_anemone_models.py"""
import json
from pathlib import Path

A = Path(__file__).resolve().parents[1] / "src/main/resources/assets/skyabyss"


def w(path, obj):
    p = A / path
    p.parent.mkdir(parents=True, exist_ok=True)
    with open(p, "w", encoding="utf-8", newline="\n") as f:
        json.dump(obj, f, indent=2, ensure_ascii=False)
        f.write("\n")


def box(frm, to, tex="#t"):
    return {"from": frm, "to": to, "faces": {d: {"texture": tex} for d in ("north", "south", "east", "west", "up", "down")}}


def plane(frm, to, angle, tex):
    return {"from": frm, "to": to, "rotation": {"angle": angle, "axis": "y", "origin": [8, 8, 8]}, "shade": False,
            "faces": {f: {"texture": tex} for f in ("north", "south")}}


ped = [box([3, 0, 3], [13, 2, 13]), box([5, 2, 5], [11, 10, 11]), box([3, 10, 3], [13, 13, 13])]
w("models/block/anemone_pedestal.json", {"parent": "minecraft:block/block",
   "textures": {"t": "skyabyss:block/anemone_pedestal", "particle": "skyabyss:block/anemone_pedestal"}, "elements": ped})
w("models/block/anemone_pedestal_lit.json", {"parent": "minecraft:block/block", "render_type": "minecraft:cutout",
   "textures": {"t": "skyabyss:block/anemone_pedestal", "b": "skyabyss:block/anemone_bloom", "particle": "skyabyss:block/anemone_bloom"},
   "elements": ped + [plane([2, 13, 8], [14, 29, 8], 45, "#b"), plane([2, 13, 8], [14, 29, 8], -45, "#b")]})
w("blockstates/anemone_pedestal.json", {"variants": {
    "lit=false,waterlogged=false": {"model": "skyabyss:block/anemone_pedestal"},
    "lit=false,waterlogged=true": {"model": "skyabyss:block/anemone_pedestal"},
    "lit=true,waterlogged=false": {"model": "skyabyss:block/anemone_pedestal_lit"},
    "lit=true,waterlogged=true": {"model": "skyabyss:block/anemone_pedestal_lit"}}})
w("models/item/anemone_pedestal.json", {"parent": "skyabyss:block/anemone_pedestal_lit"})

w("models/block/anemone_bed.json", {"parent": "minecraft:block/block", "render_type": "minecraft:cutout",
   "textures": {"t": "skyabyss:block/anemone_bed", "b": "skyabyss:block/anemone_bloom", "particle": "skyabyss:block/anemone_bed"},
   "elements": [box([1, 0, 1], [15, 5, 15]), plane([3, 5, 8], [13, 15, 8], 45, "#b"), plane([3, 5, 8], [13, 15, 8], -45, "#b")]})
w("blockstates/anemone_bed.json", {"variants": {"waterlogged=false": {"model": "skyabyss:block/anemone_bed"},
                                               "waterlogged=true": {"model": "skyabyss:block/anemone_bed"}}})
w("models/item/anemone_bed.json", {"parent": "skyabyss:block/anemone_bed"})

# 数え石: 立方体 + 灯った丸 (丸 k=0..4 を 4 面に貼る。丸の中心は 2+3k、高さ 6.5〜8.5)
w("models/block/anemone_tally.json", {"parent": "minecraft:block/cube_all", "textures": {"all": "skyabyss:block/anemone_tally"}})
for k in range(5):
    x0, x1 = 1 + 3 * k, 3 + 3 * k
    y0, y1 = 6.5, 8.5
    uv = [7, 7, 9, 9]

    def face(name):
        return {name: {"texture": "#d", "uv": uv}}
    els = [
        {"from": [x0, y0, -0.02], "to": [x1, y1, 0], "faces": face("north")},
        {"from": [x0, y0, 16], "to": [x1, y1, 16.02], "faces": face("south")},
        {"from": [-0.02, y0, x0], "to": [0, y1, x1], "faces": face("west")},
        {"from": [16, y0, x0], "to": [16.02, y1, x1], "faces": face("east")},
    ]
    w(f"models/block/anemone_tally_dot{k}.json", {"parent": "minecraft:block/block", "render_type": "minecraft:cutout",
       "textures": {"d": "skyabyss:block/anemone_tally_dot"}, "elements": els})
multipart = [{"apply": {"model": "skyabyss:block/anemone_tally"}}]
for k in range(5):
    when = "|".join(str(c) for c in range(k + 1, 6))
    multipart.append({"when": {"count": when}, "apply": {"model": f"skyabyss:block/anemone_tally_dot{k}"}})
w("blockstates/anemone_tally.json", {"multipart": multipart})
w("models/item/anemone_tally.json", {"parent": "skyabyss:block/anemone_tally"})

w("models/item/glow_anemone.json", {"parent": "minecraft:item/generated", "textures": {"layer0": "skyabyss:item/glow_anemone"}})
print("ok")
