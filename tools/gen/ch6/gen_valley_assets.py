#!/usr/bin/env python3
"""新月の谷の仕掛けのブロックと品の、ブロックの状態・模型・品の模型を書く (docs/design/46 の 4.3)。

  PYTHONIOENCODING=utf-8 python -I tools/gen/ch6/gen_valley_assets.py

絵 (textures/block/moon_*.png・textures/item/<品>.png) は選んだラフから入れる (tools/rough/moon_valley_icons.py)。
絵がまだ無い物はバニラの絵を借りる (模型の textures の BORROW)。
"""
import json
import os

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__)))))
A = os.path.join(ROOT, "src", "main", "resources", "assets", "skyabyss")


def w(path, data):
    path = os.path.join(A, path)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(data, f, ensure_ascii=False, indent=2)
        f.write("\n")


def tex(name, fallback):
    """自前の絵があればそれ、なければバニラの絵"""
    p = os.path.join(A, "textures", "block", name + ".png")
    return "skyabyss:block/" + name if os.path.exists(p) else fallback


def itex(name, fallback):
    p = os.path.join(A, "textures", "item", name + ".png")
    return "skyabyss:item/" + name if os.path.exists(p) else fallback


def box(f, t, faces_tex, uv=None, tint=None):
    faces = {}
    for d in ("north", "south", "east", "west", "up", "down"):
        faces[d] = {"texture": faces_tex.get(d, faces_tex.get("all", "#side"))}
    return {"from": f, "to": t, "faces": faces}


# ---------------------------------------------------------------- 影の橋 (足場のときは暗い硝子、消えたときは薄い影)
w("blockstates/shadow_bridge.json", {"variants": {
    "solid=true,wide=false": {"model": "skyabyss:block/shadow_bridge"}, "solid=true,wide=true": {"model": "skyabyss:block/shadow_bridge"},
    "solid=false,wide=false": {"model": "skyabyss:block/shadow_bridge_faded"}, "solid=false,wide=true": {"model": "skyabyss:block/shadow_bridge_faded"}}})
w("models/block/shadow_bridge.json", {"parent": "minecraft:block/cube_all", "render_type": "minecraft:translucent",
                                      "textures": {"all": tex("moon_shadow_bridge", "minecraft:block/tinted_glass")}})
w("models/block/shadow_bridge_faded.json", {"render_type": "minecraft:translucent", "textures": {"t": tex("moon_shadow_bridge_faded", "minecraft:block/black_stained_glass"),
                                            "particle": tex("moon_shadow_bridge_faded", "minecraft:block/black_stained_glass")},
                                            "elements": [box([0, 15, 0], [16, 15.2, 16], {"all": "#t"})]})

# ---------------------------------------------------------------- 欠片の台 (黒い石の台、上に星の欠片)
w("blockstates/star_pedestal.json", {"variants": {"": {"model": "skyabyss:block/star_pedestal"}}})
w("models/block/star_pedestal.json", {"textures": {"stone": tex("moon_pedestal", "minecraft:block/polished_blackstone_bricks"),
                                                   "top": tex("moon_pedestal_top", "minecraft:block/chiseled_polished_blackstone"),
                                                   "star": tex("moon_star", "minecraft:block/amethyst_block"),
                                                   "particle": tex("moon_pedestal", "minecraft:block/polished_blackstone_bricks")},
                                      "elements": [box([2, 0, 2], [14, 3, 14], {"all": "#stone", "up": "#top"}),
                                                   box([4, 3, 4], [12, 9, 12], {"all": "#stone", "up": "#top"}),
                                                   box([3, 9, 3], [13, 11, 13], {"all": "#stone", "up": "#top"}),
                                                   {"from": [6.5, 11, 6.5], "to": [9.5, 15, 9.5], "shade": False,
                                                    "rotation": {"angle": 45, "axis": "y", "origin": [8, 13, 8]},
                                                    "faces": {d: {"texture": "#star"} for d in ("north", "south", "east", "west", "up", "down")}}]})

# ---------------------------------------------------------------- 鏡の石 (台と、立てた銀の鏡。FACING = 鏡の面の向き)
w("blockstates/mirror_stone.json", {"variants": {"facing=north": {"model": "skyabyss:block/mirror_stone"},
                                                 "facing=east": {"model": "skyabyss:block/mirror_stone", "y": 90},
                                                 "facing=south": {"model": "skyabyss:block/mirror_stone", "y": 180},
                                                 "facing=west": {"model": "skyabyss:block/mirror_stone", "y": 270}}})
w("models/block/mirror_stone.json", {"textures": {"stone": tex("moon_mirror_base", "minecraft:block/polished_diorite"),
                                                  "glass": tex("moon_mirror", "minecraft:block/white_stained_glass"),
                                                  "frame": tex("moon_mirror_frame", "minecraft:block/iron_block"),
                                                  "particle": tex("moon_mirror_base", "minecraft:block/polished_diorite")},
                                     "elements": [box([1, 0, 1], [15, 3, 15], {"all": "#stone"}),
                                                  box([3, 3, 6], [13, 15, 9], {"all": "#frame"}),
                                                  box([4, 4, 5.9], [12, 14, 6], {"all": "#glass"})]})

# ---------------------------------------------------------------- 星の門 (藍の星空の石。真ん中は明るい)
w("blockstates/star_gate.json", {"variants": {"core=false": {"model": "skyabyss:block/star_gate"}, "core=true": {"model": "skyabyss:block/star_gate_core"}}})
w("models/block/star_gate.json", {"parent": "minecraft:block/cube_all", "textures": {"all": tex("moon_star_gate", "minecraft:block/crying_obsidian")}})
w("models/block/star_gate_core.json", {"parent": "minecraft:block/cube_all", "textures": {"all": tex("moon_star_gate_core", "minecraft:block/amethyst_block")}})

# ---------------------------------------------------------------- 灯りの台 (柱と、上の星の灯籠。灯ると明るい)
w("blockstates/lamp_post.json", {"variants": {"lit=false": {"model": "skyabyss:block/lamp_post"}, "lit=true": {"model": "skyabyss:block/lamp_post_lit"}}})
for name, glow in (("lamp_post", tex("moon_lamp_dark", "minecraft:block/tinted_glass")), ("lamp_post_lit", tex("moon_lamp_lit", "minecraft:block/sea_lantern"))):
    w("models/block/%s.json" % name, {"textures": {"stone": tex("moon_pedestal", "minecraft:block/polished_blackstone_bricks"), "glow": glow,
                                                    "particle": tex("moon_pedestal", "minecraft:block/polished_blackstone_bricks")},
                                       "elements": [box([3, 0, 3], [13, 2, 13], {"all": "#stone"}), box([5, 2, 5], [11, 9, 11], {"all": "#stone"}),
                                                    box([4, 9, 4], [12, 15, 12], {"all": "#glow"}), box([3.5, 15, 3.5], [12.5, 16, 12.5], {"all": "#stone"})]})

# ---------------------------------------------------------------- 星の溜まり (方解石の鉢の真ん中の光る水面)
w("blockstates/star_pool.json", {"variants": {"": {"model": "skyabyss:block/star_pool"}}})
w("models/block/star_pool.json", {"textures": {"rim": tex("moon_pool_rim", "minecraft:block/calcite"), "surface": tex("moon_pool", "minecraft:block/amethyst_block"),
                                               "particle": tex("moon_pool_rim", "minecraft:block/calcite")},
                                  "elements": [box([1, 0, 1], [15, 4, 15], {"all": "#rim", "up": "#surface"}),
                                               box([1, 4, 1], [15, 6, 3], {"all": "#rim"}), box([1, 4, 13], [15, 6, 15], {"all": "#rim"}),
                                               box([1, 4, 3], [3, 6, 13], {"all": "#rim"}), box([13, 4, 3], [15, 6, 13], {"all": "#rim"})]})

# ---------------------------------------------------------------- 星の印 (小さな光の結晶。交差の模型)
w("blockstates/star_mark.json", {"variants": {"": {"model": "skyabyss:block/star_mark"}}})
w("models/block/star_mark.json", {"parent": "minecraft:block/cross", "render_type": "minecraft:cutout",
                                  "textures": {"cross": tex("moon_star_mark", "minecraft:block/amethyst_cluster")}})

# ---------------------------------------------------------------- 品
for name, fb in (("star_lamp", "minecraft:item/amethyst_shard"), ("star_shard", "minecraft:item/echo_shard"), ("moon_dust", "minecraft:item/gunpowder"),
                 ("crescent_shell", "minecraft:item/nautilus_shell"), ("shadow_thread", "minecraft:item/string")):
    w("models/item/%s.json" % name, {"parent": "minecraft:item/generated", "textures": {"layer0": itex(name, fb)}})
for egg in ("dust_crawler", "shell_hare", "shadow_stitcher", "shadow_embracer"):
    w("models/item/%s_spawn_egg.json" % egg, {"parent": "minecraft:item/template_spawn_egg"})
print("done")
