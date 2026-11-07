# -*- coding: utf-8 -*-
"""地上の巨神の祠 (titan/shrine/ShrineBuild が置く。docs/design/32 の 3.1)。巨神ごとに別の作り (2026-11-07 の頁の 2 回目で決めた)。
data/skyabyss/opening/shrine_<巨神>.json (部品の形) を書く。

  python -X utf8 tools/gen/world/build_titan_shrines.py

  岩鯨  鯨骨の聖堂   段の台地の上の身廊を、背骨と 7 対の肋 (陰の面は方解石、根元は苔、先は彫った深層岩の台) が覆う。肋から旗。
                     北に鯨の頭骨 (眉の張り出し・口の上下の歯・ひげの板)。口の中の祭壇に碑。眼窩に魂の火、噴気孔から煙。入口は顎の骨の門
  潮殻  潮の神殿     潮だまりの環と珊瑚の岩の庭。北に大きな巻貝の祠 (碑は貝の口の中)。海晶のアーチと滝が潮だまりへ落ちる
  雷翼  雷の歌の環   岩の台地の上に、銅と方解石の尖塔 8 本 (先に避雷針)。尖塔から白い羽。上に避雷針の冠の環が浮き、光の柱が集合台へ
  余燼  炉の門       黒石の台地に、溶けた石の流れの溝 (光る橙のガラス)。北に炉の形の大門 (中は熾の光)、門の前に大きな金床の碑。煙突

原点 (0, 0, 0) は広場の真ん中で、y 0 = 地面の一つ上。参道は南 (+z) へ下りて y 0 で終わる。印:
  tablet (碑)・pad (集合台の真ん中。立つ高さ)・pad_r ([半径, 0, 0])・forge (共鳴の炉)・path_end ([参道の半幅, y, 終わりの z])・radius ([台地の半径, 0, 0])
立てる床で光の届かない所には、頭の上に見えない光 (light) を置く (湧き止め)
"""
import json
import math
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
OUT = ROOT / "src/main/resources/data/skyabyss/opening"
NOTE = "tools/gen/world/build_titan_shrines.py が書く。手で直さない"
AIR = "minecraft:air"


def h(x, y, z, s=0):
    n = (x * 73856093) ^ (y * 19349663) ^ (z * 83492791) ^ (s * 2654435761)
    n &= 0xFFFFFFFF
    n = ((n ^ (n >> 13)) * 1274126177) & 0xFFFFFFFF
    return ((n ^ (n >> 16)) & 0xFFFF) / 65535.0


def pick(lst, x, y, z, s=0):
    tot = sum(w for w, _ in lst)
    r = h(x, y, z, s) * tot
    for w, b in lst:
        r -= w
        if r <= 0:
            return b
    return lst[-1][1]


def st(b, f, half="bottom"):
    return f"minecraft:{b}_stairs[facing={f},half={half},shape=straight,waterlogged=false]"


def sl(b, t="bottom"):
    return f"minecraft:{b}_slab[type={t},waterlogged=false]"


def wall(b):
    return f"minecraft:{b}_wall"


LANT = "minecraft:lantern[hanging=false,waterlogged=false]"
LANT_H = "minecraft:lantern[hanging=true,waterlogged=false]"
SOUL = "minecraft:soul_lantern[hanging=false,waterlogged=false]"
SOUL_H = "minecraft:soul_lantern[hanging=true,waterlogged=false]"
CHAIN = "minecraft:chain[axis=y,waterlogged=false]"
WATER = "minecraft:water[level=0]"


def tablet(t):
    return f"skyabyss:titan_shrine[facing=south,titan={t}]"


FORGE = "skyabyss:resonance_forge[facing=west]"


class V:
    def __init__(self):
        self.c = {}
        self.marks = {}

    def put(self, x, y, z, b):
        self.c[(int(round(x)), int(y), int(round(z)))] = b

    def get(self, x, y, z):
        return self.c.get((x, y, z))

    def free(self, x, y, z):
        return self.c.get((x, y, z)) in (None, AIR)

    def disc(self, cx, y, cz, r, b, r0=-1.0):
        for x in range(int(cx - r - 1), int(cx + r + 2)):
            for z in range(int(cz - r - 1), int(cz + r + 2)):
                d = math.hypot(x - cx, z - cz)
                if r0 < d <= r + 0.35:
                    self.put(x, y, z, b(x, y, z) if callable(b) else b)

    def column(self, x, z, y0, y1, b):
        for y in range(y0, y1 + 1):
            self.put(x, y, z, b(x, y, z) if callable(b) else b)

    def line(self, a, b, blk, thick=0):
        """二点を結ぶ太さ thick の線 (面で触れる)"""
        (x0, y0, z0), (x1, y1, z1) = a, b
        n = int(max(abs(x1 - x0), abs(y1 - y0), abs(z1 - z0)) * 2) + 1
        for i in range(n + 1):
            t = i / n
            x, y, z = x0 + (x1 - x0) * t, y0 + (y1 - y0) * t, z0 + (z1 - z0) * t
            for dx in range(-thick, thick + 1):
                for dz in range(-thick, thick + 1):
                    for dy in range(-thick, thick + 1):
                        if dx * dx + dy * dy + dz * dz <= thick * thick + 0.5:
                            self.put(round(x + dx), round(y + dy), round(z + dz), blk(round(x), round(y), round(z)) if callable(blk) else blk)

    def light_up(self):
        """立てる床 (固い物の上の二段が空き) で、光るものから 9 歩より遠い所の頭の上に見えない光"""
        glow = ("lantern", "sea_lantern", "shroomlight", "end_rod", "campfire", "bulb[lit=true", "lit=true", "glowstone", "magma", "pickle", "light[")
        src = [k for k, b in self.c.items() if any(g in b for g in glow)]
        solid = lambda b: b is not None and b != AIR and not any(k in b for k in ("carpet", "water", "chain", "lantern", "button", "candle", "rod", "slab[type=bottom",
                                                                                   "coral[", "pickle", "kelp", "banner", "skull", "light["))
        n = 0
        for (x, y, z), b in list(self.c.items()):
            if not solid(b):
                continue
            if self.c.get((x, y + 1, z)) not in (None, AIR) or self.c.get((x, y + 2, z)) not in (None, AIR):
                continue
            if any(abs(sx - x) + abs(sy - y - 1) + abs(sz - z) <= 9 for (sx, sy, sz) in src):
                continue
            self.c[(x, y + 2, z)] = "minecraft:light[level=12,waterlogged=false]"
            src.append((x, y + 2, z))
            n += 1
        return n

    def write(self, name):
        for (x, y, z), b in list(self.c.items()):
            base = b.split("[")[0]
            if base.endswith("_wall") or base.endswith("_fence") or base.endswith("_pane") or base.endswith("iron_bars"):
                sides = {}
                for d, (dx, dz) in {"north": (0, -1), "south": (0, 1), "east": (1, 0), "west": (-1, 0)}.items():
                    nb = self.c.get((x + dx, y, z + dz))
                    sides[d] = nb is not None and nb != AIR and not any(k in nb for k in ("lantern", "slab", "stairs", "chain", "banner", "candle", "rod", "campfire",
                                                                                         "carpet", "pickle", "button", "torch", "coral_fan", "water", "bulb[", "skull"))
                if base.endswith("_wall"):
                    s = ",".join(f"{d}={'low' if sides[d] else 'none'}" for d in ("east", "north", "south", "west"))
                    self.c[(x, y, z)] = f"{base}[{s},up=true,waterlogged=false]"
                else:
                    s = ",".join(f"{d}={'true' if sides[d] else 'false'}" for d in ("east", "north", "south", "west"))
                    self.c[(x, y, z)] = f"{base}[{s},waterlogged=false]"
        pal, idx, blocks = [], {}, []
        xs = [k[0] for k in self.c]
        zs = [k[2] for k in self.c]
        for (x, y, z), b in sorted(self.c.items(), key=lambda kv: (kv[0][1], kv[0][0], kv[0][2])):
            if b not in idx:
                idx[b] = len(pal)
                pal.append(b)
            blocks.append([x, y, z, idx[b]])
        data = {"note": NOTE, "palette": pal, "blocks": blocks, "marks": self.marks, "meta": {"rect": [min(xs), min(zs), max(xs), max(zs)]}}
        (OUT / f"{name}.json").write_text(json.dumps(data, separators=(",", ":")), encoding="utf8")
        return len(blocks)


# ====================================================================== 共通の部品
def terrace(v, R, top, wallmat, capmat, floor, rim, buttress, seed, ragged=1.4, skirt=None):
    """段の台地: 外の擁壁 (控え壁つき)、縁の笠石、床。下は地面まで。y 0..top"""
    def edge(x, z):
        a = math.atan2(z, x)
        return R + ragged * math.sin(a * 6 + seed) + 0.8 * math.sin(a * 13 + seed * 0.3)
    for x in range(-R - 4, R + 5):
        for z in range(-R - 4, R + 5):
            d = math.hypot(x, z)
            e = edge(x, z)
            if d <= e:
                for y in range(0, top):
                    v.put(x, y, z, pick(wallmat, x, y, z, seed))
                if d > e - 1.2:
                    v.put(x, top, z, capmat)
                elif d > e - 2.4:
                    v.put(x, top, z, rim(x, z) if callable(rim) else rim)
                else:
                    v.put(x, top, z, floor(x, z))
            elif skirt and d <= e + 2.2:
                # 足もとの裾 (岩や植え込み)
                hh = 1 + int(h(x, 0, z, seed) * 2)
                for y in range(0, hh):
                    v.put(x, y, z, pick(skirt, x, y, z, seed + 1))
    # 控え壁 (16 方向。外へ斜めに下りる)
    for k in range(16):
        a = k * math.tau / 16 + seed * 0.1
        if abs(math.degrees(a) % 360 - 90) < 18:   # 南 (参道) はあける
            continue
        x0, z0 = math.cos(a) * edge(math.cos(a) * R, math.sin(a) * R), math.sin(a) * edge(math.cos(a) * R, math.sin(a) * R)
        for i in range(0, top + 1):
            x = x0 + math.cos(a) * (top - i) * 0.5
            z = z0 + math.sin(a) * (top - i) * 0.5
            v.put(x, i, z, buttress)
            if i == top:
                v.put(x, i + 1, z, capmat)


def path(v, half, z0, z1, top, floor, edge, post, lamp, seed):
    """南の参道: 台地の縁の内 (z0) から始め、台地の外 (z = z0 + 4) から段で地面へ。縁石と、5 ごとに灯りの柱"""
    flat = z0 + 4
    for z in range(z0, z1 + 1):
        y = top if z <= flat else max(0, top - (z - flat + 1) // 2)
        for x in range(-half, half + 1):
            for yy in range(0, y + 1):
                v.put(x, yy, z, edge if abs(x) == half else floor(x, z))
            for yy in range(y + 1, top + 4):
                if v.get(x, yy, z) is not None:
                    v.put(x, yy, z, AIR)
        if (z - z0) % 5 == 3:
            for sx in (-half - 1, half + 1):
                v.column(sx, z, 0, y + 2, post)
                v.put(sx, y + 3, z, lamp)


def pad(v, cx, cz, y, dark, rim, glow, r=3.3):
    """集合台: 円い壇 (一段上げる)。縁は光る石と暗い縁石"""
    v.disc(cx, y, cz, r, dark)
    v.disc(cx, y, cz, r, rim, r0=r - 0.8)
    for k in range(8):
        a = math.radians(k * 45 + 22.5)
        v.put(cx + math.cos(a) * (r - 0.4), y, cz + math.sin(a) * (r - 0.4), glow)
    v.put(cx, y, cz, glow)


def ranks(v, y, spots, stone, cap):
    for (x, z) in spots:
        v.column(x, z, y + 1, y + 2, stone)
        v.put(x, y + 3, z, cap)


# ====================================================================== 岩鯨: 鯨骨の聖堂
def whale():
    v = V()
    R, top = 16, 3
    FLOOR = [(5, "minecraft:polished_deepslate"), (4, "minecraft:deepslate_tiles"), (3, "minecraft:deepslate_bricks"), (1, "minecraft:cracked_deepslate_tiles")]
    WALLS = [(5, "minecraft:deepslate_bricks"), (3, "minecraft:cobbled_deepslate"), (2, "minecraft:cracked_deepslate_bricks"), (1, "minecraft:mossy_cobblestone"), (1, "minecraft:tuff")]

    def floor(x, z):
        if abs(x) <= 2 and z > -10:
            return "minecraft:polished_deepslate" if abs(x) < 2 else "minecraft:deepslate_tiles"
        if abs(x) == 3 and z > -10 and z % 3 == 0:
            return "minecraft:chiseled_deepslate"
        return pick(FLOOR, x // 2, 0, z // 2, 3)
    terrace(v, R, top, WALLS, sl("deepslate_tile"), floor, "minecraft:polished_deepslate", "minecraft:deepslate_bricks", 2,
            skirt=[(4, "minecraft:moss_block"), (3, "minecraft:cobbled_deepslate"), (2, "minecraft:tuff"), (1, "minecraft:mossy_cobblestone")])
    path(v, 3, R - 3, R + 11, top, lambda x, z: pick(FLOOR, x, 0, z, 6), "minecraft:polished_deepslate", wall("deepslate_brick"), SOUL, 1)
    Y = top + 1

    # 背骨 (南から北の頭骨へ、y 14 → 16。椎骨ごとに棘突起と横突起)
    spine = {}
    for z in range(-9, 13):
        y = Y + 13 - round(2 * (z + 9) / 22)
        spine[z] = y
        v.put(0, y, z, "minecraft:bone_block[axis=z]")
        if z % 2 == 0:
            v.put(0, y + 1, z, "minecraft:bone_block[axis=y]")
            v.put(0, y + 2, z, "minecraft:bone_block[axis=y]" if z % 4 == 0 else "minecraft:calcite")
            v.put(-1, y, z, "minecraft:bone_block[axis=x]")
            v.put(1, y, z, "minecraft:bone_block[axis=x]")
    # 身廊の床を一段上げる (肋の内。縁は彫った深層岩、真ん中に骨の筋の象嵌)
    for x in range(-8, 9):
        for z in range(-7, 14):
            if abs(x) == 8:
                v.put(x, Y - 1, z, "minecraft:chiseled_deepslate" if z % 3 == 0 else "minecraft:polished_deepslate")
            elif abs(x) <= 1 and z % 2 == 0:
                v.put(x, Y - 1, z, "minecraft:bone_block[axis=z]")
    # 肋 (7 対。背骨から外へ大きく弧を描いて床へ。奥行き 2。外の面は骨、内の面は方解石の陰。根元に苔、先は彫った台石)
    for i, z in enumerate(range(-6, 13, 3)):
        span = 10.5 - abs(i - 2.5) * 0.35
        top_y = spine[z]
        for sx in (-1, 1):
            last = None
            for t in range(0, 61):
                a = t / 60 * math.pi / 2
                x = sx * (1 + math.sin(a) * span)
                y = Y + (top_y - Y) * math.cos(a) ** 0.8
                X, Yi = int(round(x)), int(round(y))
                for dz in (0, 1):
                    outer = "minecraft:bone_block[axis=y]"
                    inner = "minecraft:calcite" if dz == 1 else "minecraft:bone_block[axis=y]"
                    v.put(X, Yi, z + dz, outer if dz == 0 else inner)
                    if t > 35:
                        v.put(X - sx, Yi, z + dz, "minecraft:bone_block[axis=y]" if dz == 0 else "minecraft:calcite")
                    if t > 52 and h(X, Yi, z + dz, 4) < 0.5:
                        v.put(X + sx, Yi, z + dz, "minecraft:moss_block")
                last = X
            for dz in (0, 1):
                v.put(last, Y - 1, z + dz, "minecraft:chiseled_deepslate")
                v.put(last + sx, Y - 1, z + dz, "minecraft:polished_deepslate")
                v.put(last + sx, Y, z + dz, "minecraft:polished_deepslate_wall")
            # 肋から下がる旗 (内向き、一つおき)
            if i % 2 == 0:
                bx = sx * 6
                by = int(round(Y + (top_y - Y) * math.cos(math.asin(min(1.0, 5 / span))) ** 0.8)) - 1
                v.put(bx, by, z + 1, f"minecraft:light_gray_wall_banner[facing={'east' if sx < 0 else 'west'}]")
    # 頭骨 (北。長くて平たい鯨の頭骨。口は南の身廊へ開く。上に噴気孔、左右に眼窩)
    hz0, hz1 = -21, -8
    for x in range(-10, 11):
        for z in range(hz0, hz1 + 1):
            u = (z - hz0) / (hz1 - hz0)            # 0 = 鼻先 (北), 1 = 後ろ
            half = 3.5 + 6.2 * math.sin(min(1.0, u * 1.1) * math.pi / 2)
            height = 4 + 7 * math.sin(min(1.0, u * 1.05) * math.pi / 2)
            if abs(x) > half:
                continue
            ty = Y + height * math.sqrt(max(0.0, 1 - (x / half) ** 2))
            for y in range(Y, int(ty) + 1):
                inner = abs(x) < half - 1.6 and y < ty - 1.4 and z > hz0 + 1.5
                if inner:
                    continue
                b = "minecraft:bone_block[axis=z]" if (y + z) % 5 else "minecraft:calcite"
                if abs(x) < 2 and y > ty - 1.5 and z % 3 == 0:
                    b = "minecraft:bone_block[axis=y]"
                v.put(x, y, z, b)
    # 口 (南の面を開ける) と、口の縁の上顎の歯 (骨の柱)
    for x in range(-7, 8):
        for y in range(Y, Y + 9):
            if abs(x) < 7 - max(0, y - Y - 5):
                v.put(x, y, hz1, AIR)
    for x in range(-6, 7, 2):
        v.put(x, Y + 7 - abs(x) // 3, hz1 - 1, "minecraft:bone_block[axis=y]")
        v.put(x, Y + 6 - abs(x) // 3, hz1 - 1, "minecraft:pointed_dripstone[thickness=tip,vertical_direction=down,waterlogged=false]")
    # 眉の張り出し (眼窩の上の厚い骨) と、口の上下の歯・ひげの板
    for sx in (-1, 1):
        for dx in range(5, 11):
            v.put(sx * dx, Y + 8, -12, "minecraft:bone_block[axis=x]")
            v.put(sx * dx, Y + 8, -11, "minecraft:calcite")
    for x in range(-6, 7):
        if x % 2 == 0:
            v.put(x, Y, hz1 - 1, "minecraft:pointed_dripstone[thickness=tip,vertical_direction=up,waterlogged=false]")
    for sx in (-1, 1):
        for z in range(hz1 - 6, hz1):
            for y in range(Y + 2, Y + 6):
                if (z + y) % 2 == 0:
                    v.put(sx * 5, y, z, "minecraft:chain[axis=y,waterlogged=false]")
    # 眼窩 (魂の火) と噴気孔 (骨の輪から上がる煙)
    for sx in (-1, 1):
        for dy in range(0, 2):
            for dz in range(0, 2):
                v.put(sx * 8, Y + 5 + dy, -12 + dz, AIR)
        v.put(sx * 8, Y + 4, -12, "minecraft:soul_campfire[facing=south,lit=true,signal_fire=false,waterlogged=false]")
    v.put(0, Y + 10, -15, "minecraft:campfire[facing=south,lit=true,signal_fire=true,waterlogged=false]")
    # 顎の骨 (下顎二本。頭骨の口から南へ、身廊の入口の門まで弧を描く)
    for sx in (-1, 1):
        for t in range(0, 31):
            z = hz1 + 1 + t * 0.7
            x = sx * (6.8 - math.sin(t / 30 * math.pi) * 1.2)
            y = Y + 1.2 + math.sin(t / 30 * math.pi) * 1.6
            v.put(x, int(round(y)), z, "minecraft:bone_block[axis=z]")
            v.put(x, int(round(y)) - 1, z, "minecraft:bone_block[axis=z]")
    # 入口の門: 顎の先の二本の骨の柱と、間の鯨のひげ (鎖のすだれ)
    for sx in (-1, 1):
        v.column(sx * 5, 14, Y, Y + 8, "minecraft:bone_block[axis=y]")
        v.put(sx * 5, Y + 9, 14, "minecraft:calcite")
    for x in range(-4, 5):
        v.put(x, Y + 9, 14, "minecraft:bone_block[axis=x]")
        if abs(x) >= 2:
            for y in range(Y + 6, Y + 9):
                v.put(x, y, 14, CHAIN)
    # 祭壇と碑 (頭骨の口の中)
    for x in range(-3, 4):
        for z in range(-17, -12):
            v.put(x, Y, z, "minecraft:polished_deepslate" if abs(x) < 3 and z > -17 else "minecraft:deepslate_tiles")
    for x in range(-2, 3):
        v.put(x, Y + 1, -15, sl("polished_deepslate") if abs(x) > 0 else "minecraft:chiseled_deepslate")
    v.put(0, Y + 2, -15, tablet("whale"))
    for sx in (-2, 2):
        v.put(sx, Y + 2, -15, "minecraft:candle[candles=4,lit=true,waterlogged=false]")
        v.column(sx * 2, -16, Y + 1, Y + 3, "minecraft:polished_deepslate_wall")
        v.put(sx * 2, Y + 4, -16, SOUL)
    # 吊り灯り (椎骨から)
    for z in range(-4, 13, 4):
        y = spine[z]
        for k in range(1, 4):
            v.put(0, y - k, z, CHAIN)
        v.put(0, y - 4, z, SOUL_H)
    # 集合台 (身廊の中ほど)
    pad(v, 0, 5, Y - 1, "minecraft:black_concrete", "minecraft:polished_blackstone", "minecraft:sea_lantern")
    # 身廊の両脇: 骨の欄干と魂の灯り、苔と骨のかけら
    for z in range(-6, 13):
        for sx in (-1, 1):
            if z % 3 != 0 and v.free(sx * 9, Y, z):
                v.put(sx * 9, Y, z, wall("polished_deepslate"))
    for k in range(30):
        x = round((h(k, 1, 0) - 0.5) * 2 * (R - 3))
        z = round((h(k, 2, 0) - 0.5) * 2 * (R - 3))
        if math.hypot(x, z) < R - 3 and abs(x) > 10 and v.free(x, Y, z):
            v.put(x, Y, z, ["minecraft:moss_carpet", "minecraft:bone_block[axis=x]", "minecraft:moss_carpet", "minecraft:skeleton_skull[rotation=4]"][k % 4])
    for k, (bx, bz) in enumerate(((-14, -4), (14, 2), (-13, 10), (12, -10), (-11, -12))):
        for t in range(0, 5):
            v.put(bx + t * (1 if k % 2 else -1) // 2, Y + (1 if t in (1, 2, 3) else 0), bz + t, "minecraft:bone_block[axis=z]")
    for sx in (-1, 1):
        v.put(sx * 7, Y, 14, "minecraft:skeleton_skull[rotation=8]")
        v.put(sx * 8, Y, 13, "minecraft:bone_block[axis=x]")
        v.put(sx * 7, Y, 12, "minecraft:candle[candles=3,lit=true,waterlogged=false]")
    v.put(13, Y, -3, FORGE)
    v.marks = {"tablet": [0, Y + 2, -15], "pad": [0, Y, 5], "pad_r": [3, 0, 0], "forge": [13, Y, -3],
                "path_end": [3, 0, R + 11], "radius": [R + 3, 0, 0]}
    return v


# ====================================================================== 潮殻: 潮の神殿
def tide():
    v = V()
    R, top = 16, 2
    WALLS = [(5, "minecraft:prismarine_bricks"), (3, "minecraft:prismarine"), (2, "minecraft:sandstone"), (1, "minecraft:dark_prismarine")]

    def floor(x, z):
        d = math.hypot(x, z)
        a = (math.degrees(math.atan2(z, x)) + 360) % 360
        if abs(d - 7.5) < 0.6 or abs(d - 12.5) < 0.6:
            return "minecraft:dark_prismarine"
        if (a % 30) < 3 and 7.5 < d < 12.5:
            return "minecraft:prismarine_bricks"
        return "minecraft:smooth_sandstone" if (x // 2 + z // 2) % 2 == 0 else "minecraft:cut_sandstone"
    terrace(v, R, top, WALLS, sl("prismarine_brick"), floor, "minecraft:prismarine_bricks", "minecraft:prismarine_bricks", 5,
            skirt=[(4, "minecraft:sand"), (3, "minecraft:sandstone"), (2, "minecraft:prismarine"), (1, "minecraft:gravel")])
    path(v, 3, R - 3, R + 11, top, lambda x, z: "minecraft:smooth_sandstone" if (x + z) % 3 else "minecraft:cut_sandstone",
         "minecraft:prismarine_bricks", wall("prismarine"), "minecraft:sea_lantern", 4)
    Y = top + 1
    # 潮だまりの環 (半径 8.5〜11.5 の帯を一段掘って水。中に海晶の飛び石・シーピクルス・昆布・珊瑚)
    for x in range(-12, 13):
        for z in range(-12, 13):
            d = math.hypot(x, z)
            if 8.6 < d < 11.4 and not (abs(x) <= 2 and z > 0):
                v.put(x, top, z, WATER)
                v.put(x, top - 1, z, "minecraft:sand" if h(x, 0, z) < 0.6 else "minecraft:gravel")
                r = h(x, 1, z, 5)
                if r < 0.07:
                    v.put(x, top, z, "minecraft:sea_pickle[pickles=4,waterlogged=true]")
                elif r < 0.14:
                    v.put(x, top, z, "minecraft:tube_coral[waterlogged=true]")
                elif r < 0.2:
                    v.put(x, top, z, "minecraft:brain_coral[waterlogged=true]")
                elif r < 0.26:
                    v.put(x, top, z, "minecraft:kelp_plant")
                    v.put(x, top - 1, z, "minecraft:sand")
            if abs(d - 11.8) < 0.5 and not (abs(x) <= 2 and z > 0):
                v.put(x, Y, z, sl("prismarine_brick"))
    # 参道の橋 (潮だまりを南から渡る)
    for z in range(8, 13):
        for x in range(-2, 3):
            v.put(x, top, z, "minecraft:prismarine_bricks" if abs(x) == 2 else "minecraft:smooth_sandstone")
    # 珊瑚の岩 (環の外の四隅に、海晶と珊瑚の塊の小山)
    for k, (cx, cz) in enumerate(((-12, -6), (12, -6), (-12, 7), (12, 7))):
        for x in range(cx - 2, cx + 3):
            for z in range(cz - 2, cz + 3):
                hh = 3 - int(math.hypot(x - cx, z - cz))
                for y in range(Y, Y + max(0, hh)):
                    v.put(x, y, z, pick([(3, "minecraft:prismarine"), (2, "minecraft:dark_prismarine"), (1, "minecraft:dead_brain_coral_block"),
                                         (1, "minecraft:dead_tube_coral_block")], x, y, z, k))
        v.put(cx, Y + 3, cz, "minecraft:sea_lantern")
    # 大きな巻貝 (北。殻の口を南へ向けた螺旋の塔。縞は方解石と桃色の陶器、内は空洞で碑がある)
    sx0, sz0 = 0, -11
    for y in range(Y, Y + 21):
        t = (y - Y) / 20.0
        r = 8.5 * (1 - t) ** 0.85 + 0.6
        if r < 0.8:
            break
        for x in range(sx0 - 10, sx0 + 11):
            for z in range(sz0 - 10, sz0 + 11):
                dx, dz = x - sx0, z - sz0
                d = math.hypot(dx, dz)
                a = math.atan2(dz, dx)
                spiral = (a + y * 0.55) % math.tau
                rr = r * (0.82 + 0.18 * math.sin(spiral))
                if d <= rr and d > rr - 1.6:
                    band = int((spiral / math.tau) * 6 + y * 0.5) % 3
                    b = ["minecraft:calcite", "minecraft:smooth_sandstone", "minecraft:white_terracotta", "minecraft:calcite", "minecraft:smooth_quartz",
                         "minecraft:light_gray_terracotta"][(band + int(spiral * 2)) % 6]
                    v.put(x, y, z, b)
                elif d <= rr - 1.6 and y == Y:
                    v.put(x, y - 1, z, "minecraft:dark_prismarine")
    # 殻の口 (南)。縁を厚く波うたせ、中を空ける
    for x in range(-5, 6):
        for y in range(Y, Y + 10):
            if math.hypot(x / 5.3, (y - Y) / 9.5) < 1:
                for z in range(sz0, sz0 + 9):
                    if v.get(x, y, z) is not None and math.hypot(x, z - sz0) < 9:
                        v.put(x, y, z, AIR)
    for x in range(-6, 7):
        y = Y + int(9.5 * math.sqrt(max(0.0, 1 - (x / 6.3) ** 2)))
        v.put(x, y, sz0 + 6, "minecraft:pink_terracotta")
        v.put(x, y + 1, sz0 + 6, "minecraft:smooth_quartz" if x % 2 else "minecraft:calcite")
    for y in range(Y, Y + 8):
        for sx in (-1, 1):
            v.put(sx * 6, y, sz0 + 6, "minecraft:pink_terracotta" if y % 3 == 0 else "minecraft:smooth_quartz")
    # 殻の中: 祭壇と碑、真珠 (海のランタン)、天井から吊り灯り
    for x in range(-3, 4):
        for z in range(sz0 - 3, sz0 + 2):
            v.put(x, Y - 1, z, "minecraft:dark_prismarine" if (x + z) % 2 else "minecraft:prismarine_bricks")
    v.put(0, Y, sz0 - 1, "minecraft:dark_prismarine")
    v.put(0, Y + 1, sz0 - 1, tablet("tide"))
    for sx in (-2, 2):
        v.put(sx, Y, sz0 - 1, "minecraft:sea_lantern")
        v.put(sx, Y + 1, sz0 - 1, "minecraft:sea_pickle[pickles=3,waterlogged=false]")
    for y in range(Y + 6, Y + 9):
        v.put(0, y, sz0, CHAIN)
    v.put(0, Y + 5, sz0, SOUL_H)
    # 殻の上から潮だまりへ落ちる滝 (東西の二筋)
    for sx in (-1, 1):
        for y in range(Y, Y + 7):
            v.put(sx * 8, y, sz0 + 3, "minecraft:prismarine_bricks")
        v.put(sx * 8, Y + 7, sz0 + 3, WATER)
        v.put(sx * 7, Y + 7, sz0 + 3, "minecraft:prismarine_bricks")
        v.put(sx * 9, Y + 7, sz0 + 3, "minecraft:prismarine_bricks")
        v.put(sx * 8, Y + 7, sz0 + 2, "minecraft:prismarine_bricks")
    # 海晶のアーチ (東西と南。潮だまりをまたぐ)
    for ang in (0, 180, 90):
        a = math.radians(ang)
        cx, cz = math.cos(a) * 10, math.sin(a) * 10
        tx, tz = -math.sin(a), math.cos(a)
        for t in range(-3, 4):
            ht = 6 + round(2 * math.cos(t / 3 * math.pi / 2))
            px, pz = cx + tx * t, cz + tz * t
            if abs(t) == 3:
                v.column(round(px), round(pz), Y, Y + ht, "minecraft:prismarine_bricks")
                v.put(px, Y + ht + 1, pz, "minecraft:sea_lantern")
            v.put(px, Y + ht, pz, "minecraft:dark_prismarine")
            v.put(px, Y + ht + 1, pz, sl("dark_prismarine") if abs(t) < 3 else "minecraft:sea_lantern")
            if abs(t) == 1:
                v.put(px, Y + ht - 1, pz, SOUL_H)
    # 集合台 (真ん中。潮だまりの内の砂の島)
    pad(v, 0, 2, top, "minecraft:black_concrete", "minecraft:dark_prismarine", "minecraft:sea_lantern")
    v.put(13, Y, -1, FORGE)
    v.marks = {"tablet": [0, Y + 1, sz0 - 1], "pad": [0, top + 1, 2], "pad_r": [3, 0, 0], "forge": [13, Y, -1],
                "path_end": [3, 0, R + 11], "radius": [R + 3, 0, 0]}
    return v


# ====================================================================== 雷翼: 雷の歌の環
def storm():
    v = V()
    R, top = 16, 4
    WALLS = [(5, "minecraft:polished_andesite"), (4, "minecraft:andesite"), (2, "minecraft:calcite"), (1, "minecraft:tuff"), (1, "minecraft:stone")]

    def floor(x, z):
        d = math.hypot(x, z)
        a = (math.degrees(math.atan2(z, x)) + 360) % 360
        if d < 4.6:
            return "minecraft:calcite"
        if (a % 45) < 4 and d < 14:
            return "minecraft:waxed_cut_copper"
        if abs(d - 9) < 0.55:
            return "minecraft:waxed_oxidized_cut_copper"
        return "minecraft:polished_diorite" if (x // 2 + z // 2) % 2 == 0 else "minecraft:smooth_stone"
    terrace(v, R, top, WALLS, sl("polished_diorite"), floor, "minecraft:calcite", "minecraft:polished_andesite", 9, ragged=2.4,
            skirt=[(4, "minecraft:andesite"), (3, "minecraft:stone"), (2, "minecraft:gravel"), (1, "minecraft:moss_block")])
    path(v, 3, R - 3, R + 12, top, lambda x, z: "minecraft:polished_diorite" if (x + z) % 2 else "minecraft:smooth_stone",
         "minecraft:waxed_cut_copper", wall("diorite"), "minecraft:waxed_copper_bulb[lit=true,powered=false]", 9)
    Y = top + 1
    # 尖塔 8 本 (半径 12。銅の帯と方解石。中ほどに銅の電球、先に避雷針。外へ羽根の形の控え)
    tops = []
    for k in range(8):
        a = math.radians(k * 45 + 22.5)
        cx, cz = math.cos(a) * 12, math.sin(a) * 12
        if k in (1, 2):    # 南の二本は参道をあけて少し外へ
            cx, cz = math.cos(a) * 13, math.sin(a) * 13
        ht = 15 + (k % 2) * 3
        for y in range(Y, Y + ht):
            t = (y - Y) / ht
            rr = 1.4 * (1 - t) + 0.4
            for x in range(int(cx - 2), int(cx + 3)):
                for z in range(int(cz - 2), int(cz + 3)):
                    if math.hypot(x - cx, z - cz) <= rr:
                        b = "minecraft:calcite"
                        if (y - Y) % 5 == 0:
                            b = "minecraft:waxed_cut_copper"
                        if (y - Y) == ht // 2:
                            b = "minecraft:waxed_copper_bulb[lit=true,powered=false]"
                        v.put(x, y, z, b)
        v.put(cx, Y + ht, cz, "minecraft:waxed_copper_block")
        v.put(cx, Y + ht + 1, cz, "minecraft:lightning_rod[facing=up,powered=false,waterlogged=false]")
        tops.append((round(cx), Y + ht, round(cz)))
        # 外への羽根 (尖塔の外の面から、段に下がる白い羽三枚。根元は尖塔に付ける)。
        # 段どうしは面で接する (斜めに進むときは間に一マス足す。2026-11-07「ハーフブロックがブロックに接していない」)。
        # 先は上付きの半ブロック (上の段の下付きの半ブロックと面で合う)
        for f in range(3):
            fy = Y + 4 + f * 4
            prev = None
            for i in range(0, 4 - f):
                px = int(round(cx + math.cos(a) * (1 + i)))
                pz = int(round(cz + math.sin(a) * (1 + i)))
                tip = i == 3 - f
                b = sl("polished_diorite", "top") if tip else "minecraft:white_concrete"
                if prev is not None and prev[0] != px and prev[1] != pz:
                    # 斜め: 前の段の横に、同じ高さの一マスを足す
                    v.put(px, fy - i, prev[1], b)
                    v.put(px, fy - i + 1, prev[1], sl("polished_diorite"))
                v.put(px, fy - i, pz, b)
                if i > 0:
                    v.put(px, fy - i + 1, pz, sl("polished_diorite"))
                prev = (px, pz)
    # 尖塔どうしを結ぶ銅の環 (高さ Y+10。尖塔の中ほど)
    for k in range(8):
        a0, a1 = math.radians(k * 45 + 22.5), math.radians((k + 1) * 45 + 22.5)
        if k == 1:
            continue
        for i in range(0, 25):
            a = a0 + (a1 - a0) * i / 24
            v.put(math.cos(a) * 12, Y + 10, math.sin(a) * 12, "minecraft:waxed_oxidized_cut_copper_slab[type=bottom,waterlogged=false]")
    # 浮かぶ冠 (銅の環と羽根の紋。高さ Y+19) と、尖塔の先へ伸びる鎖
    cy = Y + 19
    for x in range(-6, 7):
        for z in range(-6, 7):
            d = math.hypot(x, z)
            if 4.4 < d <= 6.2:
                v.put(x, cy, z, "minecraft:waxed_copper_block" if d > 5.4 else "minecraft:waxed_cut_copper")
            if 4.4 < d <= 5.4 and (x + z) % 3 == 0:
                v.put(x, cy + 1, z, "minecraft:lightning_rod[facing=up,powered=false,waterlogged=false]")
    for k in range(4):
        a = math.radians(k * 90)
        for i in range(1, 5):
            v.put(math.cos(a) * (4.4 - i), cy, math.sin(a) * (4.4 - i), "minecraft:waxed_cut_copper")
    v.put(0, cy, 0, "minecraft:waxed_copper_bulb[lit=true,powered=false]")
    v.put(0, cy + 1, 0, "minecraft:lightning_rod[facing=up,powered=false,waterlogged=false]")
    # 冠の縁から下がる銅の鎖と電球 (八本、真下へ)
    for k in range(8):
        a = math.radians(k * 45)
        x, z = round(math.cos(a) * 5.8), round(math.sin(a) * 5.8)
        for y in range(cy - 4, cy):
            v.put(x, y, z, CHAIN)
        v.put(x, cy - 5, z, "minecraft:waxed_copper_bulb[lit=true,powered=false]")
    # 光の柱 (冠の真ん中から集合台へ)
    for y in range(Y, cy):
        v.put(0, y, 0, "minecraft:end_rod[facing=up]")
    # 碑の祭壇 (北の尖塔の内。三段の方解石)
    for i, r in enumerate((3.4, 2.4, 1.4)):
        v.disc(0, Y + i - 1, -8, r, "minecraft:calcite" if i < 2 else "minecraft:polished_diorite")
    v.put(0, Y + 2, -8, tablet("storm"))
    for sx in (-2, 2):
        v.put(sx, Y + 1, -8, "minecraft:waxed_copper_bulb[lit=true,powered=false]")
    # 白い旗 (尖塔の内向き)
    for k in range(8):
        a = math.radians(k * 45 + 22.5)
        x, z = round(math.cos(a) * 10.5), round(math.sin(a) * 10.5)
        if v.free(x, Y + 6, z):
            f = "north" if abs(math.sin(a)) > abs(math.cos(a)) and math.sin(a) > 0 else "south" if abs(math.sin(a)) > abs(math.cos(a)) else ("west" if math.cos(a) > 0 else "east")
            v.put(x, Y + 6, z, f"minecraft:white_banner[rotation=0]")
    # 集合台 (真ん中)
    pad(v, 0, 1, Y - 1, "minecraft:black_concrete", "minecraft:waxed_oxidized_cut_copper", "minecraft:waxed_copper_bulb[lit=true,powered=false]")
    v.put(13, Y, -3, FORGE)
    v.marks = {"tablet": [0, Y + 2, -8], "pad": [0, Y, 1], "pad_r": [3, 0, 0], "forge": [13, Y, -3],
                "path_end": [3, 0, R + 12], "radius": [R + 3, 0, 0]}
    return v


# ====================================================================== 余燼: 炉の門
def ember():
    v = V()
    R, top = 16, 3
    WALLS = [(5, "minecraft:polished_blackstone_bricks"), (3, "minecraft:blackstone"), (2, "minecraft:basalt[axis=y]"), (1, "minecraft:cracked_polished_blackstone_bricks")]

    def floor(x, z):
        return pick([(5, "minecraft:polished_blackstone"), (3, "minecraft:polished_blackstone_bricks"), (2, "minecraft:smooth_basalt"), (1, "minecraft:blackstone")],
                    x // 2, 0, z // 2, 7)
    terrace(v, R, top, WALLS, sl("polished_blackstone_brick"), floor, "minecraft:chiseled_polished_blackstone", "minecraft:polished_basalt[axis=y]", 13,
            skirt=[(4, "minecraft:basalt[axis=y]"), (3, "minecraft:blackstone"), (2, "minecraft:magma_block"), (1, "minecraft:tuff")])
    path(v, 3, R - 3, R + 11, top, lambda x, z: "minecraft:polished_blackstone" if (x + z) % 2 else "minecraft:polished_blackstone_bricks",
         "minecraft:chiseled_polished_blackstone", wall("polished_blackstone_brick"), LANT, 13)
    Y = top + 1
    # 溶けた石の流れ (門から放射状の溝。底は橙のガラスの下に菌光、上は鉄格子のふた)
    for k in range(7):
        a = math.radians(-160 + k * 23)
        for i in range(4, 15):
            x = round(math.cos(a) * i)
            z = round(-9 + math.sin(a) * -i)
            if math.hypot(x, z) > R - 2.5 or (abs(x) <= 3 and z > 0):
                continue
            v.put(x, top, z, "minecraft:orange_stained_glass")
            v.put(x, top - 1, z, "minecraft:shroomlight")
    # 炉の門 (北。玄武岩の大柱二本、上に炉の口の形の大きなまぐさ石。中は熾の光の面)
    gz = -11
    for sx in (-1, 1):
        cx = sx * 8
        for y in range(Y, Y + 20):
            for x in range(cx - 2, cx + 3):
                for z in range(gz - 2, gz + 3):
                    edge = abs(x - cx) == 2 or abs(z - gz) == 2
                    if not edge:
                        v.put(x, y, z, "minecraft:blackstone")
                        continue
                    b = "minecraft:polished_basalt[axis=y]"
                    if (y - Y) % 6 == 5:
                        b = "minecraft:gilded_blackstone"
                    if (y - Y) in (0, 1):
                        b = "minecraft:polished_blackstone_bricks"
                    v.put(x, y, z, b)
        # 柱の頭の煙突 (焚き火の煙)
        for y in range(Y + 20, Y + 25):
            for x in range(cx - 1, cx + 2):
                for z in range(gz - 1, gz + 2):
                    v.put(x, y, z, "minecraft:polished_blackstone_bricks" if (x, z) != (cx, gz) else AIR)
        v.put(cx, Y + 20, gz, "minecraft:campfire[facing=south,lit=true,signal_fire=true,waterlogged=false]")
        # 鎖と灯り
        for y in range(Y + 10, Y + 14):
            v.put(cx + sx * -3, y, gz + 2, CHAIN)
        v.put(cx + sx * -3, Y + 9, gz + 2, LANT_H)
    # まぐさ石 (炉の口: 上がすぼまる台形。金の混じる黒石の縁)
    for x in range(-6, 7):
        top_y = Y + 13 + (6 - abs(x)) // 2
        for y in range(Y + 12, Y + 20):
            for z in range(gz - 1, gz + 2):
                if y >= top_y:
                    v.put(x, y, z, "minecraft:polished_blackstone_bricks" if y > top_y else "minecraft:gilded_blackstone")
    # 熾の面 (門の中。橙と赤のガラス、ところどころ菌光)
    for x in range(-5, 6):
        top_y = Y + 13 + (6 - abs(x)) // 2
        for y in range(Y, top_y):
            b = "minecraft:orange_stained_glass" if h(x, y, 2) < 0.55 else "minecraft:red_stained_glass"
            if h(x, y, 3) < 0.12:
                b = "minecraft:shroomlight"
            v.put(x, y, gz, b)
    # 門の前の大きな金床 (碑の台) と、ふいご (樽と板)
    for x in range(-3, 4):
        for z in range(gz + 3, gz + 6):
            v.put(x, Y, z, "minecraft:polished_blackstone_bricks")
    for x in range(-2, 3):
        v.put(x, Y + 1, gz + 4, "minecraft:polished_blackstone")
    for x in range(-3, 4):
        v.put(x, Y + 2, gz + 4, "minecraft:anvil[facing=east]" if abs(x) == 3 else "minecraft:polished_blackstone_bricks")
    v.put(0, Y + 3, gz + 4, tablet("ember"))
    for sx in (-5, 5):
        v.put(sx, Y, gz + 4, "minecraft:barrel[facing=up,open=false]")
        v.put(sx, Y + 1, gz + 4, sl("dark_oak", "bottom"))
    # 縁の火の台と煙突の小塔 (四隅)
    for sx in (-1, 1):
        for sz in (-1, 1):
            cx, cz = sx * 11, sz * 9 + 1
            for y in range(Y, Y + 7):
                for x in range(cx - 1, cx + 2):
                    for z in range(cz - 1, cz + 2):
                        if (x, z) != (cx, cz):
                            v.put(x, y, z, "minecraft:polished_blackstone_bricks" if y < Y + 6 else "minecraft:gilded_blackstone")
            v.put(cx, Y + 6, cz, "minecraft:campfire[facing=south,lit=true,signal_fire=false,waterlogged=false]")
            v.put(cx, Y, cz, "minecraft:shroomlight")
    # 集合台 (広場の真ん中)
    pad(v, 0, 3, Y - 1, "minecraft:black_concrete", "minecraft:gilded_blackstone", "minecraft:shroomlight")
    v.put(13, Y, -2, FORGE)
    v.marks = {"tablet": [0, Y + 3, gz + 4], "pad": [0, Y, 3], "pad_r": [3, 0, 0], "forge": [13, Y, -2],
                "path_end": [3, 0, R + 11], "radius": [R + 3, 0, 0]}
    return v


if __name__ == "__main__":
    OUT.mkdir(parents=True, exist_ok=True)
    for name, fn in (("stone_whale", whale), ("tide_shell", tide), ("thunder_wing", storm), ("ember", ember)):
        v = fn()
        lit = v.light_up()
        print(name, v.write(f"shrine_{name}"), "cells,", lit, "lights")
