"""見どころのうち形は生かして細部を足すもの (焼けた砦・修道院の跡・崩れた塔・墓地・炉の跡・難破船・大樹)。build_pois.py から読む"""
import math

from poi_kit import AIR, BP, CHAIN_Y, column, fence, hash3, lantern, log, noise3, scatter, sl, st

GRASS = "minecraft:grass_block[snowy=false]"
FLOWERS = ["minecraft:short_grass", "minecraft:short_grass", "minecraft:short_grass", "minecraft:fern", "minecraft:dandelion", "minecraft:poppy",
           "minecraft:cornflower", "minecraft:azure_bluet"]
SOLID_SKIP = ("air", "carpet", "torch", "lantern", "chain", "ladder", "sign", "banner", "button", "candle", "skull", "flower", "grass", "fern", "vine", "bush",
              "rail", "pane", "bars", "fence", "wall", "door", "trapdoor", "slab", "stairs", "chest", "tablet", "campfire", "leaves", "sapling", "pot")
STONEISH = ("stone", "brick", "cobble", "andesite", "blackstone", "basalt", "tuff", "deepslate", "diorite", "granite")


def solid(s):
    return s not in (None, AIR) and not any(k in s.split("[")[0] for k in SOLID_SKIP)


def vine_state(face):
    sides = {"north": "false", "south": "false", "east": "false", "west": "false"}
    sides[face] = "true"
    return "minecraft:vine[east=%s,north=%s,south=%s,up=false,west=%s]" % (sides["east"], sides["north"], sides["south"], sides["west"])


def dress(name, seed, rubble, moss=0.14, vines=0.07, ground_mix=None, burnt=False):
    """今の形を残し、地面の混ぜ・上の面の苔 (焼けた所は灰)・壁の蔦・根元の崩れた石を足す"""
    bp = BP(name)
    if "build_pois" in bp.old.get("note", ""):
        # いまのファイルに重ねて足すので、二度目は重ねがけになる。やり直すときは git で前の版に戻してから
        raise SystemExit(name + " is already dressed (restore it from git before dressing again)")
    bp.keep_old(lambda x, y, z, s: True)
    r = max(abs(v) for v in bp.rect)
    cells = dict(bp.cells)
    if ground_mix:
        tot = sum(w for _, w in ground_mix)
        for x in range(-r, r + 1):
            for z in range(-r, r + 1):
                if (x, -1, z) in cells or math.hypot(x, z) > r - 0.5:
                    continue
                if hash3(x, 9, z, seed) < max(0, (math.hypot(x, z) - r * 0.6) / (r * 0.4)):
                    continue
                h = noise3(x, 0, z, 3.0, seed) * 0.6 + hash3(x, 2, z, seed) * 0.4
                acc = 0
                for s_, w in ground_mix:
                    acc += w / tot
                    if h <= acc:
                        bp.set(x, -1, z, s_)
                        break
    marks = {tuple(m) for m in bp.marks.values()} | {(m[0], m[1] + 1, m[2]) for m in bp.marks.values()}
    for (x, y, z), s_ in cells.items():
        if not solid(s_) or y < -1:
            continue
        above = bp.get(x, y + 1, z)
        if above in (None, AIR) and (x, y + 1, z) not in marks and hash3(x, y, z, seed + 1) < moss:
            bp.set(x, y + 1, z, ("minecraft:light_gray_carpet" if hash3(x, y, z, seed + 2) < 0.5 else "minecraft:gray_carpet") if burnt else "minecraft:moss_carpet")
        if not burnt and y >= 1 and any(k in s_ for k in STONEISH) and hash3(x, y, z, seed + 3) < vines:
            for dx, dz, face in ((1, 0, "west"), (-1, 0, "east"), (0, 1, "north"), (0, -1, "south")):
                nx, nz = x + dx, z + dz
                if bp.get(nx, y, nz) is None and math.hypot(nx, nz) <= r and (nx, y, nz) not in marks:
                    for k in range(0, 1 + int(hash3(x, y, z, seed + 4) * 3)):
                        if y - k >= 0 and bp.get(nx, y - k, nz) is None:
                            bp.set(nx, y - k, nz, vine_state(face))
                    break
    for (x, y, z), s_ in cells.items():
        if y == 0 and solid(s_) and any(k in s_ for k in STONEISH):
            for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                nx, nz = x + 2 * dx, z + 2 * dz
                if bp.get(nx, 0, nz) is None and (nx, 0, nz) not in marks and math.hypot(nx, nz) <= r - 1 and hash3(nx, 0, nz, seed + 5) < 0.06:
                    bp.set(nx, 0, nz, rubble[int(hash3(nx, 1, nz, seed) * len(rubble)) % len(rubble)])
    return bp


def burnt_fort():
    bp = dress("poi_burnt_fort", 161, [sl("polished_blackstone_brick"), sl("blackstone"), st("blackstone", "north"), "minecraft:coal_block"], moss=0.1,
               ground_mix=[("minecraft:coarse_dirt", 3), ("minecraft:gray_concrete_powder", 1), ("minecraft:blackstone", 1), ("minecraft:gravel", 1)], burnt=True)
    for x, z in ((-6, 4), (5, 6), (-3, -8)):
        bp.setif(x, 0, z, "minecraft:campfire[facing=north,lit=true,signal_fire=false,waterlogged=false]")
    for x in range(-2, 3):
        bp.setif(x, 0, 7, log("stripped_dark_oak_log", "x"))
    bp.keep_marks()
    return bp


def monastery():
    bp = dress("poi_monastery", 171, [sl("mossy_stone_brick"), sl("stone_brick"), "minecraft:mossy_cobblestone", st("stone_brick", "east")],
               ground_mix=[(GRASS, 4), ("minecraft:moss_block", 1), ("minecraft:coarse_dirt", 1), ("minecraft:gravel", 1)])
    for x, z in ((-8, 8), (8, 8)):
        bp.setif(x, 0, z, "minecraft:azalea")
    bp.keep_marks()
    return bp


def ruined_tower():
    bp = dress("poi_ruined_tower", 181, [sl("mossy_stone_brick"), "minecraft:mossy_cobblestone", sl("cobblestone")],
               ground_mix=[(GRASS, 4), ("minecraft:moss_block", 1), ("minecraft:coarse_dirt", 1)])
    bp.keep_marks()
    return bp


def graveyard():
    bp = dress("poi_graveyard", 191, [sl("mossy_cobblestone"), "minecraft:mossy_cobblestone"], moss=0.12, vines=0.04,
               ground_mix=[(GRASS, 3), ("minecraft:podzol", 2), ("minecraft:coarse_dirt", 1)])
    # 枯れた木と、枝から吊った魂の灯り
    column(bp, -8, -8, 0, 4, log("dark_oak_log"))
    for x, y, z in ((-7, 4, -8), (-8, 5, -7), (-9, 3, -8)):
        bp.setif(x, y, z, fence("dark_oak"))
    bp.setif(-7, 3, -8, CHAIN_Y)
    bp.setif(-7, 2, -8, lantern(True, True))
    bp.keep_marks()
    return bp


def forge_ruin():
    bp = dress("poi_forge_ruin", 201, [sl("polished_blackstone_brick"), "minecraft:basalt[axis=y]", sl("blackstone")], moss=0.06,
               ground_mix=[("minecraft:coarse_dirt", 3), ("minecraft:blackstone", 1), ("minecraft:gravel", 1), ("minecraft:magma_block", 0.15)], burnt=True)
    bp.setif(4, 0, 4, "minecraft:anvil[facing=north]")
    bp.setif(-5, 0, 3, "minecraft:chipped_anvil[facing=east]")
    bp.keep_marks()
    return bp


def beached_wreck():
    bp = dress("poi_beached_wreck", 211, ["minecraft:spruce_planks", sl("spruce"), "minecraft:barrel[facing=up,open=false]"], moss=0.05, vines=0.0,
               ground_mix=[("minecraft:sand", 4), ("minecraft:gravel", 1)])
    for x, z in ((-8, 6), (-7, 6), (7, -7), (8, -6)):
        bp.setif(x, 0, z, "minecraft:cobweb" if (x + z) % 2 else "minecraft:dried_kelp_block")
    bp.setif(6, 0, 5, "minecraft:sea_pickle[pickles=3,waterlogged=false]")
    bp.keep_marks()
    return bp


def great_tree():
    bp = BP("poi_great_tree")
    if "build_pois" in bp.old.get("note", ""):
        raise SystemExit("poi_great_tree is already dressed (restore it from git before dressing again)")
    bp.keep_old(lambda x, y, z, s: True)
    loot = tuple(bp.marks["loot"])
    leaves = ["minecraft:oak_leaves[distance=1,persistent=true,waterlogged=false]", "minecraft:dark_oak_leaves[distance=1,persistent=true,waterlogged=false]",
              "minecraft:oak_leaves[distance=1,persistent=true,waterlogged=false]", "minecraft:flowering_azalea_leaves[distance=1,persistent=true,waterlogged=false]"]
    # 冠のまわりに大きさの違う房 (丸い一つの塊にしない)
    lobes = [(0, 22, 0, 5.5, 3.5), (-4, 19, 2, 3.8, 2.8), (4, 20, -2, 3.6, 2.6), (2, 24, 3, 3.2, 2.4), (-3, 23, -4, 3.4, 2.4), (5, 17, 4, 2.8, 2.0), (-5, 17, -2, 2.8, 2.2)]
    for cx, cy, cz, rh, rv in lobes:
        for x in range(int(cx - rh - 2), int(cx + rh + 3)):
            for y in range(int(cy - rv - 2), int(cy + rv + 3)):
                for z in range(int(cz - rh - 2), int(cz + rh + 3)):
                    d = ((x - cx) / rh) ** 2 + ((y - cy) / rv) ** 2 + ((z - cz) / rh) ** 2
                    if d <= 1 + (noise3(x, y, z, 2.2, 221) - 0.5) * 0.7 and abs(x) <= 9 and abs(z) <= 9:
                        if y <= loot[1] + 2 and math.hypot(x - loot[0], z - loot[2]) < 3.5:
                            continue
                        bp.setif(x, y, z, leaves[int(hash3(x, y, z, 222) * 4) % 4])
    # 下へ垂れる洞窟の蔓 (実のなるものも)
    for (x, y, z), s_ in list(bp.cells.items()):
        if "leaves" in s_ and bp.get(x, y - 1, z) in (None, AIR) and hash3(x, y, z, 223) < 0.12 and y > loot[1] + 1:
            for k in range(1, 2 + int(hash3(x, y, z, 224) * 4)):
                if bp.get(x, y - k, z) is None:
                    bp.set(x, y - k, z, "minecraft:cave_vines[age=0,berries=%s]" % ("true" if hash3(x, y - k, z, 225) < 0.3 else "false"))
    # 根: 幹のまわりに這う太い根と根の土
    for a in range(6):
        ang = a / 6 * math.pi * 2 + 0.3
        for t in range(2, 7):
            x, z = int(round(math.cos(ang) * t)), int(round(math.sin(ang) * t))
            if bp.get(x, 0, z) in (None, AIR) and (x, z) != (0, 6):
                bp.set(x, 0 if t < 5 else -1, z, "minecraft:oak_wood[axis=%s]" % ("x" if abs(math.cos(ang)) > 0.7 else "z"))
            bp.setif(x, -1, z, "minecraft:rooted_dirt")
    scatter(bp, 9, 0, FLOWERS + ["minecraft:moss_carpet"], 0.12, 226, only_on={GRASS, "minecraft:moss_block", "minecraft:rooted_dirt"})
    bp.keep_marks()
    return bp


BUILDERS = {"poi_burnt_fort": burnt_fort, "poi_monastery": monastery, "poi_ruined_tower": ruined_tower, "poi_graveyard": graveyard,
            "poi_forge_ruin": forge_ruin, "poi_beached_wreck": beached_wreck, "poi_great_tree": great_tree}
