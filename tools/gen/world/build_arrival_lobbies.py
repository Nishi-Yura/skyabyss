# -*- coding: utf-8 -*-
"""巨神の背の待機場所「渦の口」(2026-11-07 の頁で B を選んだ。Wynncraft の TNA の大きな虚の穴のつもり)。四体とも。
trial/ArrivalDeck が到着の足もと (RouteSeals.safeFeet) に合わせて置く。書くのは data/skyabyss/opening/ の二つ:
  arrival_<titan>.json       固い部品: 部品どおりに置く (空気も。広場の上・堀・渦・洞)。残すもの (天然磁石・鐘・鎖・骨の梁・封じの戸・箱) は ArrivalDeck が残す
  arrival_<titan>_soft.json  柔らかい部品: 空き・地形・前の台の石の所にだけ置く (広場の下の岩の塊・渦の殻)

  python -X utf8 tools/gen/world/build_arrival_lobbies.py

座標は足もと (0, 0, 0) から。床は y -1、立つのは y 0。どの背も同じ部品の組:
  広場     欠けた縁の円い広場 (中心 C・半径 R)。割れ目の線が渦から放射状に走り、ところどころ光る。足もとのまわり (半径 4.5) は集まる所の紋
  渦       広場の端の大きな穴 (中心 V・半径 HR)。光る腕が回りながら深みへ下り、底に光の芯。縁は欠けた石の輪。順路の口
  アーチ   渦のまわりの折れて傾いたアーチ 5 つ
  浮き岩   渦の上に浮かぶ岩と、吊った魂の灯り (余燼は洞の天井から垂れる岩)
  祠       立石八本の輪と、宙の冠石から鎖で吊った灯り。真ん中に帰りの石 (印 home_stone)
  縁       欠けた柱と火の台。広場の外は堀 (雷翼・潮殻の陸)・空 (岩鯨)・海 (潮殻)・口の外の落ち込み (余燼)。落ちると足もとへ戻す (ArrivalDeck の見張り)
  下       広場の下の岩の塊 (下ほど細る)。岩鯨・潮殻は宙/海の上に浮く島に見える
印: home_stone (帰りの石)、center (広場の中心)、radius ([R, 0, 0])、vortex (渦の中心)
"""
import json
import math
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
OUT = ROOT / "src/main/resources/data/skyabyss/opening"
NOTE = "tools/gen/world/build_arrival_lobbies.py が書く。手で直さない"
AIR = "minecraft:air"


def h(x, y, z, s=0):
    n = (x * 73856093) ^ (y * 19349663) ^ (z * 83492791) ^ (s * 2654435761)
    n &= 0xFFFFFFFF
    n = ((n ^ (n >> 13)) * 1274126177) & 0xFFFFFFFF
    return ((n ^ (n >> 16)) & 0xFFFF) / 65535.0


def pick(lst, x, y, z, s=0):
    """[(重み, ブロック), ...] から決まった一つ"""
    tot = sum(w for w, _ in lst)
    r = h(x, y, z, s) * tot
    for w, b in lst:
        r -= w
        if r <= 0:
            return b
    return lst[-1][1]


def stairs(b, f, half="bottom"):
    return f"{b}_stairs[facing={f},half={half},shape=straight,waterlogged=false]"


def slab(b, t="bottom"):
    return f"{b}_slab[type={t},waterlogged=false]"


def wall(b):
    return f"{b}_wall[east=none,north=none,south=none,up=true,waterlogged=false,west=none]"


LANT_H = "minecraft:lantern[hanging=true,waterlogged=false]"
SOUL_H = "minecraft:soul_lantern[hanging=true,waterlogged=false]"
SOUL = "minecraft:soul_lantern[hanging=false,waterlogged=false]"
CHAIN = "minecraft:chain[axis=y,waterlogged=false]"

# ====================================================================== 巨神ごと
P = {
    "stone_whale": dict(
        floor=[(5, "minecraft:polished_deepslate"), (4, "minecraft:deepslate_tiles"), (3, "minecraft:deepslate_bricks"),
               (2, "minecraft:cracked_deepslate_tiles"), (1, "minecraft:tuff_bricks")],
        gather="minecraft:polished_tuff", gather2="minecraft:chiseled_deepslate", crack="minecraft:cobbled_deepslate",
        crack_glow="minecraft:sculk_catalyst", glint="minecraft:bone_block[axis=y]",
        mass=[(5, "minecraft:deepslate"), (3, "minecraft:tuff"), (2, "minecraft:cobbled_deepslate"), (1, "minecraft:calcite")],
        rim=[(3, "minecraft:deepslate_bricks"), (2, "minecraft:cracked_deepslate_bricks"), (1, "minecraft:bone_block[axis=y]")],
        arch=[(4, "minecraft:deepslate_bricks"), (2, "minecraft:cracked_deepslate_bricks"), (2, "minecraft:polished_deepslate")],
        arch_band="minecraft:bone_block[axis=y]", arch_cap="minecraft:chiseled_deepslate",
        shard_top=[(3, "minecraft:tuff"), (2, "minecraft:moss_block"), (1, "minecraft:calcite")],
        shard_bot=[(3, "minecraft:deepslate"), (1, "minecraft:cobbled_deepslate")],
        stone=[(3, "minecraft:deepslate_bricks"), (2, "minecraft:polished_deepslate"), (1, "minecraft:bone_block[axis=y]")],
        stone_cap="minecraft:sea_lantern", cap="minecraft:chiseled_deepslate", cap2="minecraft:deepslate_tiles",
        arm="minecraft:sea_lantern", arm_glass="minecraft:cyan_stained_glass", core="minecraft:sea_lantern", void="minecraft:black_concrete",
        wall_b="minecraft:deepslate_brick", slab_b="minecraft:deepslate_tile", hang=SOUL_H, fire="minecraft:soul_campfire[facing=north,lit=true,signal_fire=false,waterlogged=false]",
        dais="minecraft:polished_deepslate", dais_edge="minecraft:deepslate_tiles", plant="minecraft:moss_carpet"),
    "tide_shell": dict(
        floor=[(5, "minecraft:prismarine_bricks"), (4, "minecraft:smooth_sandstone"), (3, "minecraft:prismarine"),
               (2, "minecraft:cut_sandstone"), (1, "minecraft:dark_prismarine")],
        gather="minecraft:smooth_sandstone", gather2="minecraft:dark_prismarine", crack="minecraft:dark_prismarine",
        crack_glow="minecraft:sea_lantern", glint="minecraft:chiseled_sandstone",
        mass=[(5, "minecraft:prismarine"), (3, "minecraft:sandstone"), (2, "minecraft:dark_prismarine"), (1, "minecraft:smooth_sandstone")],
        rim=[(3, "minecraft:prismarine_bricks"), (2, "minecraft:dark_prismarine"), (1, "minecraft:prismarine")],
        arch=[(4, "minecraft:prismarine_bricks"), (2, "minecraft:prismarine"), (1, "minecraft:smooth_sandstone")],
        arch_band="minecraft:dark_prismarine", arch_cap="minecraft:sea_lantern",
        shard_top=[(3, "minecraft:prismarine"), (2, "minecraft:sand"), (1, "minecraft:smooth_sandstone")],
        shard_bot=[(3, "minecraft:dark_prismarine"), (2, "minecraft:prismarine")],
        stone=[(3, "minecraft:prismarine"), (2, "minecraft:dark_prismarine"), (1, "minecraft:prismarine_bricks")],
        stone_cap="minecraft:sea_lantern", cap="minecraft:dark_prismarine", cap2="minecraft:prismarine_bricks",
        arm="minecraft:sea_lantern", arm_glass="minecraft:light_blue_stained_glass", core="minecraft:sea_lantern", void="minecraft:black_concrete",
        wall_b="minecraft:prismarine", slab_b="minecraft:dark_prismarine", hang=SOUL_H, fire="minecraft:soul_campfire[facing=north,lit=true,signal_fire=false,waterlogged=false]",
        dais="minecraft:smooth_sandstone", dais_edge="minecraft:dark_prismarine", plant="minecraft:sea_pickle[pickles=3,waterlogged=false]"),
    "thunder_wing": dict(
        floor=[(5, "minecraft:polished_diorite"), (4, "minecraft:calcite"), (3, "minecraft:smooth_stone"),
               (2, "minecraft:polished_andesite"), (1, "minecraft:waxed_cut_copper")],
        gather="minecraft:calcite", gather2="minecraft:waxed_oxidized_cut_copper", crack="minecraft:polished_andesite",
        crack_glow="minecraft:waxed_copper_bulb[lit=true,powered=false]", glint="minecraft:waxed_chiseled_copper",
        mass=[(5, "minecraft:diorite"), (3, "minecraft:calcite"), (2, "minecraft:andesite"), (1, "minecraft:stone")],
        rim=[(3, "minecraft:polished_diorite"), (2, "minecraft:calcite"), (1, "minecraft:waxed_weathered_cut_copper")],
        arch=[(4, "minecraft:calcite"), (3, "minecraft:polished_diorite"), (1, "minecraft:waxed_oxidized_cut_copper")],
        arch_band="minecraft:waxed_weathered_cut_copper", arch_cap="minecraft:waxed_copper_bulb[lit=true,powered=false]",
        shard_top=[(3, "minecraft:calcite"), (2, "minecraft:diorite"), (1, "minecraft:white_wool")],
        shard_bot=[(3, "minecraft:andesite"), (2, "minecraft:diorite")],
        stone=[(3, "minecraft:calcite"), (2, "minecraft:polished_diorite"), (1, "minecraft:waxed_oxidized_copper")],
        stone_cap="minecraft:waxed_copper_bulb[lit=true,powered=false]", cap="minecraft:waxed_oxidized_cut_copper", cap2="minecraft:calcite",
        arm="minecraft:waxed_copper_bulb[lit=true,powered=false]", arm_glass="minecraft:white_stained_glass", core="minecraft:sea_lantern",
        void="minecraft:gray_concrete", wall_b="minecraft:diorite", slab_b="minecraft:polished_diorite", hang=LANT_H,
        fire="minecraft:campfire[facing=north,lit=true,signal_fire=false,waterlogged=false]",
        dais="minecraft:calcite", dais_edge="minecraft:waxed_cut_copper", plant="minecraft:white_carpet"),
    "ember": dict(
        floor=[(5, "minecraft:polished_blackstone"), (4, "minecraft:polished_blackstone_bricks"), (3, "minecraft:blackstone"),
               (2, "minecraft:cracked_polished_blackstone_bricks"), (1, "minecraft:smooth_basalt")],
        gather="minecraft:polished_blackstone", gather2="minecraft:gilded_blackstone", crack="minecraft:basalt[axis=y]",
        crack_glow="minecraft:shroomlight", glint="minecraft:gilded_blackstone",
        mass=[(5, "minecraft:blackstone"), (3, "minecraft:basalt[axis=y]"), (2, "minecraft:smooth_basalt"), (1, "minecraft:tuff")],
        rim=[(3, "minecraft:polished_blackstone_bricks"), (2, "minecraft:cracked_polished_blackstone_bricks"), (1, "minecraft:gilded_blackstone")],
        arch=[(4, "minecraft:polished_blackstone_bricks"), (2, "minecraft:blackstone"), (2, "minecraft:polished_basalt[axis=y]")],
        arch_band="minecraft:gilded_blackstone", arch_cap="minecraft:shroomlight",
        shard_top=[(3, "minecraft:basalt[axis=y]"), (2, "minecraft:blackstone")],
        shard_bot=[(3, "minecraft:blackstone"), (2, "minecraft:basalt[axis=y]")],
        stone=[(3, "minecraft:polished_basalt[axis=y]"), (2, "minecraft:polished_blackstone_bricks"), (1, "minecraft:gilded_blackstone")],
        stone_cap="minecraft:shroomlight", cap="minecraft:chiseled_polished_blackstone", cap2="minecraft:polished_blackstone_bricks",
        arm="minecraft:shroomlight", arm_glass="minecraft:orange_stained_glass", core="minecraft:shroomlight", void="minecraft:black_concrete",
        wall_b="minecraft:polished_blackstone_brick", slab_b="minecraft:polished_blackstone", hang=LANT_H,
        fire="minecraft:campfire[facing=north,lit=true,signal_fire=false,waterlogged=false]",
        dais="minecraft:polished_blackstone", dais_edge="minecraft:gilded_blackstone", plant="minecraft:blackstone_slab[type=bottom,waterlogged=false]"),
}

# 配置 (足もとからのずれ)。allowed: 広場を置いてよい所。moat: 広場の外の堀を掘る。cave: 洞 (天井のある所に掘る)。island: 下の塊が宙/海に浮く
L = {
    # 岩鯨: 東は体 (x >= +3)。台は西の空へ張り出す。北東の登り口の階段 (x +12・z -11〜-7) と錨石・鎖は残す
    "stone_whale": dict(C=(-13, 0), R=19, V=(-24, 1), HR=8, S=(-6, -13), allowed=lambda x, z: x <= 2, moat=False, cave=None, island=True, depth=17),
    # 潮殻: 西と南は海、北と東は陸 (浜の砂が上がる)。渦は西の海の上
    "tide_shell": dict(C=(-6, -2), R=19, V=(-19, -6), HR=8, S=(4, -14), allowed=lambda x, z: True, moat=True, cave=None, island=True, depth=13),
    # 雷翼: 北は順路の通路 (体の上)、南の z +12 より先は空。渦は北
    "thunder_wing": dict(C=(0, -6), R=21, V=(0, -20), HR=8, S=(0, 11), allowed=lambda x, z: True, moat=True, cave=None, island=True, depth=14),
    # 余燼: 口の中の洞。北 (z -20 より先) は口の外の落ち込み。渦 (喉) は南の奥
    "ember": dict(C=(0, 4), R=17, V=(0, 13), HR=8, S=(-11, 1), allowed=lambda x, z: True, moat=True,
                  cave=dict(cx=0, cz=3, rx=19, rz=18, ry=13), island=False, depth=6),
}


class V:
    def __init__(self):
        self.hard = {}
        self.soft = {}

    def put(self, x, y, z, b):
        self.hard[(int(x), int(y), int(z))] = b
        self.soft.pop((int(x), int(y), int(z)), None)

    def under(self, x, y, z, b):
        k = (int(x), int(y), int(z))
        if k not in self.hard:
            self.soft[k] = b

    def air(self, x, y, z):
        k = (int(x), int(y), int(z))
        if k not in self.hard:
            self.hard[k] = AIR

    def write(self, name):
        conn_fix(self.hard)
        n = []
        for key, cells in (("", self.hard), ("_soft", self.soft)):
            pal, idx, blocks = [], {}, []
            for (x, y, z), b in sorted(cells.items(), key=lambda kv: (kv[0][1], kv[0][0], kv[0][2])):
                if b not in idx:
                    idx[b] = len(pal)
                    pal.append(b)
                blocks.append([x, y, z, idx[b]])
            xs = [k[0] for k in cells] or [0]
            zs = [k[2] for k in cells] or [0]
            data = {"note": NOTE, "palette": pal, "blocks": blocks, "marks": self.marks,
                    "meta": {"rect": [min(xs), min(zs), max(xs), max(zs)]}}
            (OUT / f"arrival_{name}{key}.json").write_text(json.dumps(data, separators=(",", ":")), encoding="utf8")
            n.append(len(blocks))
        return n


def conn_fix(c):
    """塀・板ガラス・鉄格子を隣とつなぐ (置いたあと ArrivalDeck も直す)"""
    for (x, y, z), b in list(c.items()):
        base = b.split("[")[0]
        if not base.endswith("_wall"):
            continue
        sides = {}
        for d, (dx, dz) in {"north": (0, -1), "south": (0, 1), "east": (1, 0), "west": (-1, 0)}.items():
            nb = c.get((x + dx, y, z + dz))
            sides[d] = nb is not None and nb != AIR and not any(k in nb for k in ("lantern", "slab", "stairs", "chain", "campfire", "carpet", "pickle", "roots"))
        st = ",".join(f"{d}={'low' if sides[d] else 'none'}" for d in ("east", "north", "south", "west"))
        c[(x, y, z)] = f"{base}[{st},up=true,waterlogged=false]"


def build(name):
    s, l = P[name], L[name]
    v = V()
    cx, cz = l["C"]
    R = l["R"]
    vx, vz = l["V"]
    HR = l["HR"]
    sx, sz = l["S"]
    allowed = l["allowed"]
    seed = sum(map(ord, name))

    def edge_r(x, z):
        a = math.atan2(z - cz, x - cx)
        return R + 1.8 * math.sin(a * 5 + seed) + 1.3 * math.sin(a * 11 + seed * 0.7) + 1.4 * (h(x, 0, z, seed) - 0.5)

    def in_plaza(x, z):
        return allowed(x, z) and math.hypot(x - cx, z - cz) <= edge_r(x, z)

    def in_vortex(x, z):
        return math.hypot(x - vx, z - vz) <= HR

    cave = l["cave"]

    def in_cave(x, y, z):
        if not cave:
            return False
        return ((x - cave["cx"]) / cave["rx"]) ** 2 + ((z - cave["cz"]) / cave["rz"]) ** 2 + (max(0, y) / cave["ry"]) ** 2 <= 1.0

    span = R + 8
    # ---------------------------------------------------------------- 広場の上を空ける (洞はその形に掘る)
    for x in range(cx - span, cx + span + 1):
        for z in range(cz - span, cz + span + 1):
            if not allowed(x, z):
                continue
            d = math.hypot(x - cx, z - cz)
            if in_plaza(x, z):
                top = 12 if not cave else 0
                for y in range(0, top + 1):
                    v.air(x, y, z)
                if cave:
                    for y in range(0, cave["ry"] + 1):
                        if in_cave(x, y, z):
                            v.air(x, y, z)
            # 堀: 広場の外の帯 (幅 6)。足もと -16 から上 10 まで空ける
            elif l["moat"] and d <= edge_r(x, z) + 6.5:
                for y in range(-16, 11):
                    v.air(x, y, z)
    # ---------------------------------------------------------------- 床と下の塊
    for x in range(cx - span, cx + span + 1):
        for z in range(cz - span, cz + span + 1):
            if not in_plaza(x, z) or in_vortex(x, z):
                continue
            df = math.hypot(x, z)
            d = math.hypot(x - cx, z - cz)
            er = edge_r(x, z)
            # 割れ目: 渦から外へ放射する線 (ゆらぎつき)。光るのはまれ
            av = math.atan2(z - vz, x - vx)
            dv = math.hypot(x - vx, z - vz)
            crack = False
            for k in range(7):
                a0 = k * math.tau / 7 + seed * 0.3 + 0.25 * math.sin(dv * 0.35 + k)
                diff = (av - a0 + math.pi) % math.tau - math.pi
                if abs(diff) * dv < 0.55 and dv < HR + 22 - k * 1.5:
                    crack = True
            if df <= 4.6:
                b = s["gather2"] if (abs(df - 4.0) < 0.55 or df < 1.0) else s["gather"]
            elif crack:
                b = s["crack_glow"] if h(x, 1, z, seed) < 0.12 else s["crack"]
            elif er - d < 1.2:
                b = pick(s["rim"], x, -1, z, seed)
            else:
                b = pick(s["floor"], x // 2, -1, z // 2, seed)
                if h(x, 2, z, seed) < 0.03:
                    b = s["glint"]
            v.put(x, -1, z, b)
            # 下の塊: 縁ほど浅く、下ほど細る (岩鯨・潮殻は宙/海に浮いて見える)
            frac = max(0.0, 1.0 - (d / max(1.0, er)) ** 1.6)
            depth = 2 + int(l["depth"] * frac + 3 * h(x, 3, z, seed))
            for y in range(-2, -depth - 1, -1):
                blk = pick(s["mass"], x, y, z, seed + 1)
                if y >= -3:
                    v.put(x, y, z, blk)
                else:
                    v.under(x, y, z, blk)
    # ---------------------------------------------------------------- 渦 (順路の口)
    depth_v = int(HR * 1.9) + 2
    for x in range(vx - HR - 2, vx + HR + 3):
        for z in range(vz - HR - 2, vz + HR + 3):
            d = math.hypot(x - vx, z - vz)
            if d > HR + 1.6 or not allowed(x, z):
                continue
            if d <= HR:
                bot = -2 - int((HR - d) * 1.9)
                a = math.degrees(math.atan2(z - vz, x - vx))
                spiral = (a + d * 26) % 120 < 38
                for y in range(bot + 1, 13):
                    if not cave or in_cave(x, y, z) or y < 1:
                        v.put(x, y, z, AIR)
                if d < 1.7:
                    v.put(x, bot, z, s["core"])
                    v.put(x, bot - 1, z, s["core"])
                else:
                    v.put(x, bot, z, s["arm"] if spiral else (s["void"] if d < HR * 0.7 else pick(s["mass"], x, bot, z, seed)))
                    if spiral:
                        v.put(x, bot + 1, z, s["arm_glass"])
                for y in range(bot - 3, bot):
                    if (x, y, z) not in v.hard:
                        v.put(x, y, z, pick(s["mass"], x, y, z, seed + 2))
            else:
                # 欠けた縁の輪 (高さ 0〜2。ところどころ途切れる)
                if h(x, 4, z, seed) < 0.7:
                    ht = 1 if h(x, 5, z, seed) < 0.35 else 0
                    v.put(x, -1, z, pick(s["rim"], x, -1, z, seed))
                    for y in range(0, ht):
                        v.put(x, y, z, pick(s["rim"], x, y, z, seed + 3))
                    if ht == 0 and h(x, 6, z, seed) < 0.25:
                        v.put(x, 0, z, slab(s["slab_b"]))
                for y in range(-2, -6, -1):
                    v.put(x, y, z, pick(s["mass"], x, y, z, seed))
    # ---------------------------------------------------------------- 渦の上に立ち上がる光の渦巻き
    top_w = 16 if not cave else cave["ry"] - 2
    for arm in range(3):
        prev = None
        for i in range(0, top_w * 3):
            y = 1 + i / 3.0
            r = 2.2 + (y - 1) * 0.42
            a = math.radians(i * 9 + arm * 120)
            X, Y, Z = round(vx + math.cos(a) * r), round(y), round(vz + math.sin(a) * r)
            # 前の点と面で触れるように間を埋める (宙に一つだけ浮く塊を作らない)
            path = []
            if prev is not None:
                px, py, pz = prev
                while (px, py, pz) != (X, Y, Z):
                    if px != X:
                        px += 1 if X > px else -1
                    elif pz != Z:
                        pz += 1 if Z > pz else -1
                    else:
                        py += 1 if Y > py else -1
                    path.append((px, py, pz))
            else:
                path.append((X, Y, Z))
            prev = (X, Y, Z)
            for (qx, qy, qz) in path:
                if not allowed(qx, qz) or (cave and not in_cave(qx, qy, qz)):
                    continue
                v.put(qx, qy, qz, s["arm"] if (qx + qy + qz) % 7 == 0 else s["arm_glass"])

    # ---------------------------------------------------------------- 渦のまわりの折れたアーチ
    vang = math.atan2(cz - vz, cx - vx)  # 渦から広場の中心へ向く角 (アーチは渦の奥側に並ぶ)
    for k in range(5):
        a = vang + math.pi + math.radians(-80 + k * 40)
        ax, az = vx + math.cos(a) * (HR + 4.5), vz + math.sin(a) * (HR + 4.5)
        tx, tz = -math.sin(a), math.cos(a)
        broken = k in (1, 4)
        lean = 0.12 + 0.06 * k
        top_h = (13 if not cave else 10) + (k % 3)
        for t in range(-3, 4):
            if broken and t > 0:
                continue
            ht = top_h - 2 + round(3 * math.cos(t / 3.0 * math.pi / 2))
            for y in range(0, ht + 1):
                if abs(t) < 2 and y < ht - 1:
                    continue
                px = ax + tx * t + math.cos(a) * lean * y
                pz = az + tz * t + math.sin(a) * lean * y
                X, Z = round(px), round(pz)
                if not allowed(X, Z):
                    continue
                if cave and not in_cave(X, y, Z):
                    continue
                b = s["arch_band"] if y in (4, 9) else pick(s["arch"], X, y, Z, seed + k)
                if y == ht and abs(t) <= 1:
                    b = s["arch_cap"] if t == 0 else s["cap"]
                v.put(X, y, Z, b)
                if abs(t) >= 2 and y == 0:
                    v.put(X, -1, Z, s["cap"])
        # 折れたアーチの欠片が足もとに
        if broken:
            for j in range(3):
                X = round(ax + tx * (1.5 + j) + math.cos(a) * 1.5)
                Z = round(az + tz * (1.5 + j) + math.sin(a) * 1.5)
                if allowed(X, Z) and in_plaza(X, Z) and not in_vortex(X, Z):
                    v.put(X, 0, Z, pick(s["arch"], X, 0, Z, seed))
                    if j == 1:
                        v.put(X, 1, Z, slab(s["slab_b"]))
    # ---------------------------------------------------------------- 浮き岩 (余燼は洞の天井から垂れる岩)
    for k in range(10):
        a = h(k, 0, 0, seed) * math.tau
        r = 3 + h(k, 1, 0, seed) * (HR + 6)
        fx, fz = vx + math.cos(a) * r, vz + math.sin(a) * r
        if not allowed(round(fx), round(fz)):
            continue
        if cave:
            # 天井から垂れる円錐
            X, Z = round(fx), round(fz)
            ceil = 0
            for y in range(cave["ry"], 0, -1):
                if in_cave(X, y, Z):
                    ceil = y
                    break
            ln = 3 + int(h(k, 2, 0, seed) * 5)
            for i in range(ln):
                rr = 1.6 * (1 - i / ln)
                for x in range(X - 2, X + 3):
                    for z in range(Z - 2, Z + 3):
                        if math.hypot(x - X, z - Z) <= rr + 0.3 and in_cave(x, ceil - i, z):
                            v.put(x, ceil - i, z, pick(s["shard_bot"], x, i, z, seed))
            if k % 2 == 0:
                v.put(X, ceil - ln, Z, s["hang"])
            continue
        cy = 9 + h(k, 2, 0, seed) * 11
        sz_ = 1.2 + h(k, 3, 0, seed) * 2.3
        for x in range(int(fx - sz_ - 1), int(fx + sz_ + 2)):
            for z in range(int(fz - sz_ - 1), int(fz + sz_ + 2)):
                for y in range(int(cy - sz_ * 1.4), int(cy + sz_ + 1)):
                    dd = math.sqrt((x - fx) ** 2 + (z - fz) ** 2 + ((y - cy) * (1.6 if y > cy else 0.9)) ** 2)
                    if dd <= sz_ and allowed(x, z):
                        v.put(x, y, z, pick(s["shard_top"] if y > cy else s["shard_bot"], x, y, z, seed))
        if k % 3 == 0:
            # 浮き岩のいちばん下の石の真下に鎖と灯り
            X, Z = round(fx), round(fz)
            ys = [yy for (xx, yy, zz), b in v.hard.items() if xx == X and zz == Z and b != AIR and int(cy - sz_ * 2) <= yy <= int(cy + sz_ + 1)]
            if ys:
                low = min(ys) - 1
                v.put(X, low, Z, CHAIN)
                v.put(X, low - 1, Z, s["hang"])
    # ---------------------------------------------------------------- 祠 (立石の輪と冠石。真ん中に帰りの石)
    for x in range(sx - 5, sx + 6):
        for z in range(sz - 5, sz + 6):
            d = math.hypot(x - sx, z - sz)
            if d <= 3.6:
                v.put(x, -1, z, s["dais"] if d > 1.2 else s["cap"])
            elif d <= 4.4:
                v.put(x, -1, z, s["dais_edge"])
            if d <= 4.4:
                v.put(x, -2, z, pick(s["mass"], x, -2, z, seed))
    stone_top = 9 if not cave else 8
    for k in range(8):
        a = math.radians(k * 45 + 22.5)
        X, Z = sx + round(math.cos(a) * 4.5), sz + round(math.sin(a) * 4.5)
        ht = 5 + int(h(k, 7, 0, seed) * 3) + (1 if k % 2 else 0)
        ht = min(ht, stone_top)
        for y in range(0, ht):
            v.put(X, y, Z, pick(s["stone"], X, y, Z, seed + 7))
        v.put(X, -1, Z, s["cap"])
        if k % 2 == 0:
            v.put(X, ht, Z, s["stone_cap"])
        else:
            v.put(X, ht, Z, slab(s["slab_b"]))
    cap_y = 12 if not cave else 10
    for x in range(sx - 1, sx + 2):
        for z in range(sz - 1, sz + 2):
            v.put(x, cap_y, z, s["cap"] if (x, z) != (sx, sz) else s["cap2"])
            if abs(x - sx) + abs(z - sz) == 1:
                v.put(x, cap_y + 1, z, s["cap2"])
    v.put(sx, cap_y + 1, sz, s["stone_cap"])
    for y in range(7, cap_y):
        v.put(sx, y, sz, CHAIN)
    v.put(sx, 6, sz, s["hang"])
    v.put(sx, 0, sz, "minecraft:lodestone")
    # ---------------------------------------------------------------- 縁の欠けた柱と火の台
    for k in range(16):
        a = math.radians(k * 22.5 + seed % 13)
        X = round(cx + math.cos(a) * (R - 1.5))
        Z = round(cz + math.sin(a) * (R - 1.5))
        if not in_plaza(X, Z) or in_vortex(X, Z) or math.hypot(X - vx, Z - vz) < HR + 3 or math.hypot(X - sx, Z - sz) < 6 or math.hypot(X, Z) < 7:
            continue
        if k % 3 == 0:
            v.put(X, 0, Z, s["cap"])
            v.put(X, 1, Z, s["fire"])
        else:
            ht = 2 + int(h(k, 8, 0, seed) * 4)
            if cave:
                ht = min(ht, 6)
            for y in range(0, ht):
                v.put(X, y, Z, pick(s["arch"], X, y, Z, seed + 9))
            v.put(X, ht, Z, wall(s["wall_b"]) if k % 2 else slab(s["slab_b"]))
    # 集まる所の四つの灯りの柱 (足もとから 6。鐘は 2〜4 に置かれる)
    for k in range(4):
        a = math.radians(45 + k * 90)
        X, Z = round(math.cos(a) * 6), round(math.sin(a) * 6)
        if in_plaza(X, Z) and not in_vortex(X, Z):
            v.put(X, 0, Z, s["cap"])
            v.put(X, 1, Z, wall(s["wall_b"]))
            v.put(X, 2, Z, SOUL if s["hang"] == SOUL_H else "minecraft:lantern[hanging=false,waterlogged=false]")
    # 苔・根などの小物 (床の上に少し。空いている所だけ)
    for x in range(cx - R, cx + R + 1):
        for z in range(cz - R, cz + R + 1):
            if not in_plaza(x, z) or in_vortex(x, z) or math.hypot(x, z) <= 6 or math.hypot(x - sx, z - sz) <= 5.5:
                continue
            if v.hard.get((x, 0, z), AIR) == AIR and h(x, 9, z, seed) < 0.035 and (x, -1, z) in v.hard:
                v.put(x, 0, z, s["plant"])
    # 洞の明かり (見えない灯り。天井の近くに格子で)
    if cave:
        for x in range(-cave["rx"], cave["rx"] + 1, 6):
            for z in range(cave["cz"] - cave["rz"], cave["cz"] + cave["rz"] + 1, 6):
                y = 6
                if in_cave(x, y, z) and v.hard.get((x, y, z)) == AIR:
                    v.put(x, y, z, "minecraft:light[level=13,waterlogged=false]")
    v.marks = {"home_stone": [sx, 0, sz], "center": [cx, 0, cz], "radius": [R + (6 if l["moat"] else 2), 0, 0], "vortex": [vx, 0, vz]}
    # 足もとと鐘の所は必ず立てる
    for x in range(-4, 5):
        for z in range(-4, 5):
            if math.hypot(x, z) <= 4.6:
                v.put(x, 0, z, AIR)
                v.put(x, 1, z, AIR)
    return v


if __name__ == "__main__":
    OUT.mkdir(parents=True, exist_ok=True)
    for name in P:
        print(name, build(name).write(name), "cells (hard, soft)")
