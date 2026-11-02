"""深淵の形 A の層 (骨・雷) の道の部屋と印の部屋に、小物を足す (2026-10-28 夜の見直し。雷の層の部屋が特に空いていた)。

形は変えない。置くのは:
  - 壁ぎわの床: 壁に接する床のマスで、壁の反対側に 4 マス以上の空きがある所だけ (通路や細い所はふさがない)
      骨 = 骨のブロックの小山・頭骨・蝋燭・方解石の欠片 (段)   雷 = 避雷針・銅の板 (段)・荒れた深層岩の段・焦げた銅
  - 天井: 天井の下に 4 マス以上の空気がある所に、垂れる鍾乳石 (骨) か鎖 (雷) を 1〜2
避ける所: 野営と主の間 (印から 14)・仕掛けの箱 (<名>_a / <名>_b)・印のまわり 3・灯りや仕掛けのブロックの隣。光るブロックは置かない。
二度は置かない (設計図の dress に room_props があればやめる)。建て直しには AbyssBuild.VERSION を上げる。

  python -I tools/abyss/dress_rooms.py
"""
import os
import random
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
sys.path.insert(0, os.path.join(ROOT, "tools", "gen", "world"))
import town_outskirts as T  # noqa: E402  (設計図の読み書き)

PATH = os.path.join(T.DIR, "abyss_layers.json")
TAG = "room_props"
BAND = {"bone": (150, 215), "storm": (-90, -30)}
# 形 B (塩・灰) の待ち場と下り道は二度目の仕上げ (room_props_b)。python -I tools/abyss/dress_rooms.py b
TAG_B = "room_props_b"
BAND_B = {"salt": (25, 85), "ash": (-215, -155)}
ROCK = {"bone": {"minecraft:calcite", "minecraft:diorite", "minecraft:bone_block[axis=y]", "minecraft:dripstone_block", "minecraft:tuff"},
        "salt": {"minecraft:calcite", "minecraft:white_terracotta", "minecraft:snow_block", "minecraft:diorite", "minecraft:smooth_quartz"},
        "ash": {"minecraft:blackstone", "minecraft:basalt[axis=y]", "minecraft:smooth_basalt", "minecraft:cobbled_deepslate"},
        "storm": {"minecraft:tuff", "minecraft:deepslate", "minecraft:cobbled_deepslate", "minecraft:deepslate_copper_ore", "minecraft:copper_ore",
                  "minecraft:oxidized_copper", "minecraft:weathered_copper"}}
FLOOR = {
    "bone": [("minecraft:bone_block[axis=y]", 4), ("minecraft:skeleton_skull[powered=false,rotation={r}]", 3),
             ("minecraft:candle[candles=2,lit=false,waterlogged=false]", 2), ("minecraft:polished_diorite_slab[type=bottom,waterlogged=false]", 3),
             ("minecraft:bone_block[axis=x]", 2)],
    "storm": [("minecraft:lightning_rod[facing=up,powered=false,waterlogged=false]", 3), ("minecraft:cut_copper_slab[type=bottom,waterlogged=false]", 3),
              ("minecraft:cobbled_deepslate_slab[type=bottom,waterlogged=false]", 3), ("minecraft:exposed_cut_copper_slab[type=bottom,waterlogged=false]", 2),
              ("minecraft:weathered_copper", 1)],
}
FLOOR["salt"] = [("minecraft:amethyst_cluster[facing=up,waterlogged=false]", 2), ("minecraft:calcite", 2), ("minecraft:smooth_quartz_slab[type=bottom,waterlogged=false]", 3),
                 ("minecraft:barrel[facing=up,open=false]", 1), ("minecraft:white_carpet", 2), ("minecraft:small_amethyst_bud[facing=up,waterlogged=false]", 2)]
FLOOR["ash"] = [("minecraft:basalt[axis=y]", 3), ("minecraft:blackstone_slab[type=bottom,waterlogged=false]", 3), ("minecraft:skeleton_skull[powered=false,rotation={r}]", 1),
                ("minecraft:polished_blackstone_button[face=floor,facing=north,powered=false]", 2), ("minecraft:gray_carpet", 2)]
HANG = {"bone": "minecraft:pointed_dripstone[thickness={t},vertical_direction=down,waterlogged=false]",
        "salt": "minecraft:pointed_dripstone[thickness={t},vertical_direction=down,waterlogged=false]",
        "ash": "minecraft:chain[axis=y,waterlogged=false]",
        "storm": "minecraft:chain[axis=y,waterlogged=false]"}


def main(second=False):
    global TAG, BAND
    if second:
        TAG, BAND = TAG_B, BAND_B
    d, c = T.load(PATH)
    if TAG in d.get("dress", []):
        print("already done")
        return
    m = d["marks"]
    boxes = []
    for k in m:
        if k.endswith("_a") and k[:-2] + "_b" in m:
            a, b = m[k], m[k[:-2] + "_b"]
            boxes.append((min(a[0], b[0]) - 2, min(a[1], b[1]) - 2, min(a[2], b[2]) - 2, max(a[0], b[0]) + 2, max(a[1], b[1]) + 2, max(a[2], b[2]) + 2))
    far = [v for k, v in m.items() if k.startswith(("camp_", "arena_", "arrive_", "arrival", "lord_", "descend_", "gate_", "pool_", "survivor_", "rest_"))]
    near = [v for k, v in m.items() if not k.startswith("esc_")]   # 下り道の芯の点 (数百) は除く。壁ぎわ 4 マスの空きで道は残る

    trail = {}
    for k, v in m.items():
        if k.startswith("esc_"):
            for dx in range(-2, 3):
                for dz in range(-2, 3):
                    trail.setdefault((v[0] + dx, v[2] + dz), []).append((v[1] - 3, v[1] + 4))

    def air(p):
        s = c.get(p)
        return s is not None and (s.endswith(":air") or "cave_air" in s)

    def blocked(x, y, z):
        if any(b[0] <= x <= b[3] and b[1] <= y <= b[4] and b[2] <= z <= b[5] for b in boxes):
            return True
        if any(abs(x - f[0]) <= 14 and abs(y - f[1]) <= 8 and abs(z - f[2]) <= 14 for f in far):
            return True
        if any(abs(x - n[0]) <= 3 and abs(y - n[1]) <= 3 and abs(z - n[2]) <= 3 for n in near):
            return True
        # 下り道の芯 (護りの人が歩く所) から 2 以内には置かない (2026-10-28: 灰の芯の 3 マスが歩けなくなった)
        return (x, z) in trail and any(lo <= y <= hi for lo, hi in trail[(x, z)])

    rnd = random.Random("abyss-room-props")
    placed = {k: 0 for k in BAND}
    hung = {k: 0 for k in BAND}
    cells = list(c.items())
    for layer, (lo, hi) in BAND.items():
        rock = ROCK[layer]
        for (x, y, z), s in cells:
            if not (lo <= y <= hi) or s not in rock:
                continue
            # 床の上 (この岩の上が空気で、その上も空気)
            above = (x, y + 1, z)
            if air(above) and air((x, y + 2, z)) and not blocked(x, y + 1, z):
                for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                    if c.get((x + dx, y + 1, z + dz)) in rock and all(air((x - dx * k, y + 1, z - dz * k)) and c.get((x - dx * k, y, z - dz * k)) is not None
                                                                     and not air((x - dx * k, y, z - dz * k)) for k in range(1, 5)):
                        if rnd.random() < 0.4:
                            st = pick(FLOOR[layer], rnd)
                            st = st.replace("{r}", str(rnd.randrange(16)))
                            c[above] = st
                            placed[layer] += 1
                        break
            # 天井の下 (この岩の下が空気で、下に 4 マス以上の空気)
            below = (x, y - 1, z)
            if air(below) and all(air((x, y - k, z)) for k in range(1, 5)) and not blocked(x, y - 2, z) and rnd.random() < 0.035:
                n = 1 if rnd.random() < 0.6 else 2
                if layer in ("bone", "salt"):
                    if n == 1:
                        c[below] = HANG["bone"].format(t="tip")
                    else:
                        c[below] = HANG["bone"].format(t="frustum")
                        c[(x, y - 2, z)] = HANG["bone"].format(t="tip")
                else:
                    for k in range(1, n + 2):
                        c[(x, y - k, z)] = HANG["storm"]
                hung[layer] += 1
    d.setdefault("dress", []).append(TAG)
    T.save(PATH, d, c)
    print("floor props", placed, "hanging", hung)


def pick(table, rnd):
    total = sum(w for _, w in table)
    r = rnd.random() * total
    for s, w in table:
        r -= w
        if r < 0:
            return s
    return table[-1][0]


if __name__ == "__main__":
    main(len(sys.argv) > 1 and sys.argv[1] == "b")
