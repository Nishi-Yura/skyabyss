# -*- coding: utf-8 -*-
"""巨神の待機場所 (到着の台) と祠のラフ 3 案 (2026-11-07 の声「Wynncraft の NOL・TNA のように建築を凝れる？待機場所。祠も合わせて。例の画像を作って」)。
潮殻を例に。data/skyabyss/opening/rough_lobby_<a|b|c>.json (部品の形) を書き、tools/shots/arrival_lobby_roughs.txt が平らな世界に置いて撮る。

  python -X utf8 tools/rough/arrival_lobby/roughs.py

  A 潮の宮の門   (NOL の宮殿の入口のつもり) 円い広場・列柱の回廊・双塔と大門 (順路の口)・真ん中の泉と鐘の台・南に円屋根の祠
  B 渦の口       (TNA の大きな虚の穴のつもり) 割れた広場・北に巨大な渦の穴 (順路の口)・傾いた折れたアーチ・宙に浮く岩・南に立石の輪の祠
  C 殻の岬       (地形と建物を混ぜる) 削った崖の台地・段の庭と池・崖から落ちる滝・北に巻貝の岩の塔と洞の門・崖に彫った祠
置く高さ: 部品の y 0 = 地面の一つ上。集まる所の足もとは (0, 3, 0)
"""
import json
import math
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
OUT = ROOT / "src/main/resources/data/skyabyss/opening"
NOTE = "tools/rough/arrival_lobby/roughs.py が書くラフ。決まったら消す"


def h(x, y, z, s=0):
    n = (x * 73856093) ^ (y * 19349663) ^ (z * 83492791) ^ (s * 2654435761)
    n = (n ^ (n >> 13)) * 1274126177
    return ((n ^ (n >> 16)) & 0xFFFF) / 65535.0


class V:
    def __init__(self):
        self.c = {}

    def put(self, x, y, z, b):
        self.c[(int(x), int(y), int(z))] = b

    def get(self, x, y, z):
        return self.c.get((x, y, z))

    def box(self, x0, y0, z0, x1, y1, z1, b):
        for x in range(min(x0, x1), max(x0, x1) + 1):
            for y in range(min(y0, y1), max(y0, y1) + 1):
                for z in range(min(z0, z1), max(z0, z1) + 1):
                    self.put(x, y, z, b(x, y, z) if callable(b) else b)

    def disc(self, cx, y, cz, r, b, r0=-1):
        for x in range(int(cx - r - 1), int(cx + r + 2)):
            for z in range(int(cz - r - 1), int(cz + r + 2)):
                d = math.hypot(x - cx, z - cz)
                if r0 < d <= r + 0.35:
                    self.put(x, y, z, b(x, y, z) if callable(b) else b)

    def column(self, x, z, y0, y1, b):
        for y in range(y0, y1 + 1):
            self.put(x, y, z, b(x, y, z) if callable(b) else b)

    def dome(self, cx, cy, cz, r, b, hollow=True):
        for x in range(int(cx - r - 1), int(cx + r + 2)):
            for z in range(int(cz - r - 1), int(cz + r + 2)):
                for y in range(cy, int(cy + r + 2)):
                    d = math.sqrt((x - cx) ** 2 + (z - cz) ** 2 + ((y - cy) * 1.15) ** 2)
                    if d <= r + 0.4 and (not hollow or d > r - 1.0):
                        self.put(x, y, z, b(x, y, z) if callable(b) else b)

    def write(self, name):
        # 塀・板ガラス・鉄格子を隣とつなぐ
        conn = ("wall", "pane", "iron_bars", "fence")
        for (x, y, z), b in list(self.c.items()):
            base = b.split("[")[0]
            if not (base.endswith("_wall") or base.endswith("_pane") or base.endswith("iron_bars") or base.endswith("_fence")):
                continue
            sides = {}
            for d, (dx, dz) in {"north": (0, -1), "south": (0, 1), "east": (1, 0), "west": (-1, 0)}.items():
                nb = self.c.get((x + dx, y, z + dz))
                ok = nb is not None and not nb.startswith("minecraft:air") and "lantern" not in nb and "banner" not in nb and "stairs" not in nb \
                    and "slab" not in nb and "button" not in nb and "chain" not in nb and "candle" not in nb and "rod" not in nb
                sides[d] = ok
            if base.endswith("_wall"):
                st = ",".join(f"{d}={'low' if sides[d] else 'none'}" for d in ("east", "north", "south", "west"))
                straight = (sides["east"] and sides["west"] and not sides["north"] and not sides["south"]) or \
                           (sides["north"] and sides["south"] and not sides["east"] and not sides["west"])
                above = self.c.get((x, y + 1, z))
                up = not straight or (above is not None and not above.startswith("minecraft:air"))
                self.c[(x, y, z)] = f"{base}[{st},up={'true' if up else 'false'},waterlogged=false]"
            else:
                st = ",".join(f"{d}={'true' if sides[d] else 'false'}" for d in ("east", "north", "south", "west"))
                self.c[(x, y, z)] = f"{base}[{st},waterlogged=false]"
        pal, idx, blocks = [], {}, []
        xs = [k[0] for k in self.c]
        zs = [k[2] for k in self.c]
        for (x, y, z), b in sorted(self.c.items(), key=lambda kv: (kv[0][1], kv[0][0], kv[0][2])):
            if b not in idx:
                idx[b] = len(pal)
                pal.append(b)
            blocks.append([x, y, z, idx[b]])
        data = {"note": NOTE, "palette": pal, "blocks": blocks, "marks": {"feet": [0, 3, 0]},
                "meta": {"rect": [min(xs), min(zs), max(xs), max(zs)]}}
        (OUT / f"{name}.json").write_text(json.dumps(data, separators=(",", ":")), encoding="utf8")
        return len(blocks)


def stairs(b, f, half="bottom"):
    return f"minecraft:{b}_stairs[facing={f},half={half},shape=straight,waterlogged=false]"


def slab(b, t="bottom"):
    return f"minecraft:{b}_slab[type={t},waterlogged=false]"


LANT = "minecraft:lantern[hanging=false,waterlogged=false]"
LANT_H = "minecraft:lantern[hanging=true,waterlogged=false]"
SOUL_H = "minecraft:soul_lantern[hanging=true,waterlogged=false]"
CHAIN = "minecraft:chain[axis=y,waterlogged=false]"
WATER = "minecraft:water[level=0]"


def facing_out(dx, dz):
    if abs(dx) >= abs(dz):
        return "east" if dx > 0 else "west"
    return "south" if dz > 0 else "north"


# ====================================================================== A 潮の宮の門
def rough_a():
    v = V()
    R = 18
    # 段の台座: 地面から三段上がる円い台 (縁は階段)
    for y, r in ((0, R + 4), (1, R + 3), (2, R + 2)):
        v.disc(0, y, 0, r, lambda x, y, z: "minecraft:prismarine_bricks" if h(x, y, z) < 0.7 else "minecraft:dark_prismarine")
    for x in range(-R - 6, R + 7):
        for z in range(-R - 6, R + 7):
            d = math.hypot(x, z)
            for y, r in ((0, R + 5), (1, R + 4), (2, R + 3)):
                if r - 0.6 < d <= r + 0.35 and (x, y, z) not in v.c:
                    v.put(x, y, z, slab("prismarine_brick"))
    # 床の模様 (y 2): 同心の帯・放射の線・砂岩の地
    for x in range(-R - 2, R + 3):
        for z in range(-R - 2, R + 3):
            d = math.hypot(x, z)
            if d > R + 2.35:
                continue
            a = (math.degrees(math.atan2(z, x)) + 360) % 360
            if d < 1.5:
                b = "minecraft:sea_lantern"
            elif abs(d - 7) < 0.6 or abs(d - 12.5) < 0.6:
                b = "minecraft:dark_prismarine"
            elif abs(d - 17) < 0.6:
                b = "minecraft:prismarine_bricks"
            elif (a % 22.5 < 2.2 or a % 22.5 > 20.3) and 7.5 < d < 17:
                b = "minecraft:cut_sandstone"
            elif d < 7:
                b = "minecraft:smooth_quartz" if (x + z) % 2 == 0 else "minecraft:smooth_sandstone"
            else:
                b = "minecraft:smooth_sandstone"
            v.put(x, 2, z, b)
    # 真ん中の泉 (半径 4〜6 の水の輪) と鐘の台
    v.disc(0, 2, 0, 5.4, WATER, r0=3.6)
    v.disc(0, 3, 0, 6.2, "minecraft:prismarine_wall", r0=5.5)
    v.disc(0, 3, 0, 3.6, "minecraft:prismarine_wall", r0=2.9)
    for k in range(4):
        a = math.radians(45 + k * 90)
        v.box(round(math.cos(a) * 6), 2, round(math.sin(a) * 6), round(math.cos(a) * 6), 2, round(math.sin(a) * 6), "minecraft:cut_sandstone")
        v.put(round(math.cos(a) * 6), 3, round(math.sin(a) * 6), "minecraft:air")
    v.box(-1, 3, -1, 1, 3, 1, "minecraft:chiseled_quartz_block")
    v.put(0, 4, 0, "minecraft:bell[attachment=floor,facing=south,powered=false]")
    # 列柱の回廊 (半径 R の柱 24 本・高さ 9・上の帯と軒)
    for k in range(24):
        a = math.radians(k * 15 + 7.5)
        cx, cz = math.cos(a) * R, math.sin(a) * R
        if cz < -R * 0.75 and abs(cx) < 9:
            continue
        x, z = round(cx), round(cz)
        v.put(x, 3, z, "minecraft:chiseled_sandstone" if False else "minecraft:cut_sandstone")
        v.column(x, z, 4, 10, "minecraft:quartz_pillar[axis=y]")
        v.put(x, 11, z, "minecraft:chiseled_quartz_block")
    for x in range(-R - 2, R + 3):
        for z in range(-R - 2, R + 3):
            d = math.hypot(x, z)
            if R - 1.2 < d <= R + 1.35 and not (z < -R * 0.75 and abs(x) < 9):
                v.put(x, 12, z, "minecraft:smooth_quartz" if R - 0.6 < d <= R + 0.6 else slab("smooth_quartz"))
                if R + 0.6 < d:
                    v.put(x, 12, z, slab("dark_prismarine"))
            if R - 0.6 < d <= R + 0.6 and not (z < -R * 0.75 and abs(x) < 9) and (x, 4, z) not in v.c:
                v.put(x, 3, z, "minecraft:prismarine_wall")
    for k in range(24):
        a = math.radians(k * 15 + 7.5)
        x, z = round(math.cos(a) * (R - 1)), round(math.sin(a) * (R - 1))
        if not (z < -R * 0.7 and abs(x) < 10):
            v.put(x, 11, z, LANT_H)
    # 北の大門 (双塔と門の口。順路の口は光る格子で閉じている)
    for sx in (-1, 1):
        cx = sx * 8
        for y in range(3, 24):
            for x in range(cx - 3, cx + 4):
                for z in range(-R - 4, -R + 3):
                    edge = x in (cx - 3, cx + 3) or z in (-R - 4, -R + 2)
                    if not edge:
                        continue
                    if y in (11, 18):
                        b = "minecraft:dark_prismarine"
                    elif (y % 7 == 5) and z == -R + 2 and x == cx:
                        b = "minecraft:light_blue_stained_glass"
                    else:
                        b = "minecraft:prismarine_bricks" if y < 11 else "minecraft:smooth_quartz"
                    v.put(x, y, z, b)
        v.dome(cx, 24, -R - 1, 4, lambda x, y, z: "minecraft:dark_prismarine" if y < 27 else "minecraft:prismarine")
        v.column(cx, -R - 1, 29, 31, "minecraft:gold_block")
        v.put(cx, 32, -R - 1, "minecraft:lightning_rod[facing=up,powered=false,waterlogged=false]")
    # 門の口と、上の大きな弧
    for x in range(-5, 6):
        top = 15 + round(3 * math.cos(x / 5.0 * math.pi / 2))
        for y in range(3, 22):
            for z in (-R - 2, -R - 1, -R):
                if y >= top:
                    v.put(x, y, z, "minecraft:smooth_quartz" if y != top else "minecraft:chiseled_quartz_block")
                elif z == -R - 1:
                    v.put(x, y, z, "minecraft:light_blue_stained_glass" if (x + y) % 3 else "minecraft:sea_lantern")
                else:
                    v.put(x, y, z, "minecraft:air")
    v.box(-5, 22, -R - 2, 5, 22, -R, "minecraft:dark_prismarine")
    for x in range(-6, 7):
        v.put(x, 23, -R - 1, stairs("dark_prismarine", "south" if x % 2 else "north"))
    v.box(-1, 23, -R - 2, 1, 25, -R - 2, "minecraft:gold_block")
    # 門の前の階段 (台の上へ)
    for i, z in enumerate(range(-R + 3, -R + 6)):
        v.box(-5, 3, z, 5, 3, z, stairs("quartz", "south") if i == 2 else "minecraft:smooth_quartz")
    # 南の祠 (円屋根の小堂。六本の柱。真ん中に帰りの石)
    sz = R - 4
    v.disc(0, 2, sz, 4.6, "minecraft:chiseled_quartz_block" if False else "minecraft:smooth_quartz")
    v.disc(0, 3, sz, 4.6, slab("smooth_quartz"), r0=3.4)
    for k in range(6):
        a = math.radians(k * 60 + 30)
        x, z = round(math.cos(a) * 4), sz + round(math.sin(a) * 4)
        v.column(x, z, 3, 8, "minecraft:quartz_pillar[axis=y]")
    v.disc(0, 9, sz, 5.2, "minecraft:dark_prismarine")
    v.dome(0, 10, sz, 4, lambda x, y, z: "minecraft:prismarine" if (x + z + y) % 3 else "minecraft:dark_prismarine")
    v.put(0, 15, sz, "minecraft:gold_block")
    v.put(0, 16, sz, "minecraft:end_rod[facing=up]")
    v.put(0, 3, sz, "minecraft:lodestone")
    v.put(0, 8, sz, SOUL_H)
    # 旗と灯りの柱 (東・西)
    for sx in (-1, 1):
        x = sx * (R - 3)
        v.column(x, 0, 3, 7, "minecraft:prismarine_bricks")
        v.put(x, 8, 0, "minecraft:sea_lantern")
        v.put(x - sx, 6, 0, f"minecraft:cyan_wall_banner[facing={'east' if sx > 0 else 'west'}]".replace("east" if sx > 0 else "west", "west" if sx > 0 else "east"))
    return v


# ====================================================================== B 渦の口
def rough_b():
    v = V()
    R = 21
    # 割れた広場: 島のような欠けた縁、すき間は深い闇
    for x in range(-R - 3, R + 4):
        for z in range(-R - 3, R + 6):
            d = math.hypot(x, z * 0.9)
            ragged = R + 2.5 * math.sin(math.atan2(z, x) * 5) + 2 * (h(x, 0, z, 3) - 0.5)
            if d > ragged:
                continue
            crack = abs(math.sin(x * 0.35 + z * 0.12)) < 0.05 or abs(math.sin(z * 0.31 - x * 0.2)) < 0.05
            if crack and d > 6:
                v.put(x, 0, z, "minecraft:black_concrete")
                continue
            top = 2 if d < ragged - 2 else 1
            low = -16 + int(6 * d / R) + int(3 * h(x, 9, z))
            for y in range(low, top):
                v.put(x, y, z, "minecraft:dark_prismarine" if y > -4 else "minecraft:prismarine" if h(x, y, z) < 0.5 else "minecraft:sandstone")
            r = h(x, top, z, 1)
            v.put(x, top, z, "minecraft:prismarine_bricks" if r < 0.45 else "minecraft:cracked_stone_bricks" if r < 0.6
                  else "minecraft:smooth_sandstone" if r < 0.85 else "minecraft:prismarine")
    # 北の渦の穴 (半径 10。らせんの縁が深みへ下りる。底は光る渦)
    hx, hz, HR = 0, -16, 10
    for x in range(hx - HR - 3, hx + HR + 4):
        for z in range(hz - HR - 3, hz + HR + 4):
            d = math.hypot(x - hx, z - hz)
            if d > HR + 2.5:
                continue
            a = math.atan2(z - hz, x - hx)
            for y in range(-14, 6):
                v.c.pop((x, y, z), None)
            if d <= HR:
                depth = int((HR - d) * 1.9) + 1
                spiral = (math.degrees(a) + d * 28) % 120 < 40
                for y in range(-depth - 1, 1):
                    pass
                bot = -depth
                # 渦の腕は光る (海のランタンの筋に水色のガラスを重ねる)。腕のあいだは闇
                v.put(x, bot, z, "minecraft:sea_lantern" if spiral else ("minecraft:black_concrete" if d < 6 else "minecraft:dark_prismarine"))
                if spiral and d > 2:
                    v.put(x, bot + 1, z, "minecraft:light_blue_stained_glass")
                for y in range(bot - 3, bot):
                    v.put(x, y, z, "minecraft:dark_prismarine")
                if d < 2.2:
                    v.put(x, bot - 1, z, "minecraft:sea_lantern")
            else:
                for y in range(-1, 3 + int(2 * h(x, 1, z, 9))):
                    v.put(x, y, z, "minecraft:dark_prismarine" if y < 2 else "minecraft:prismarine")
    v.put(hx, -15, hz, "minecraft:sea_lantern")
    # 渦のまわりの折れたアーチ (五つ。傾き、片足が欠ける)
    for k in range(5):
        a = math.radians(-160 + k * 35)
        cx, cz = hx + math.cos(a) * (HR + 5), hz + math.sin(a) * (HR + 5)
        tx, tz = -math.sin(a), math.cos(a)
        broken = k in (1, 3)
        for t in range(-3, 4):
            if broken and t > 1:
                continue
            ht = 11 + round(4 * math.cos(t / 3.0 * math.pi / 2))
            px, pz = cx + tx * t, cz + tz * t
            lean = (t + 3) * 0.15
            for y in range(2, ht):
                if abs(t) == 3 or y >= ht - 2:
                    v.put(round(px + math.cos(a) * lean * (y / 10)), y, round(pz + math.sin(a) * lean * (y / 10)),
                          "minecraft:prismarine_bricks" if h(k, y, t) < 0.75 else "minecraft:dark_prismarine")
    # 宙に浮く岩 (渦の上)
    for k in range(9):
        a = h(k, 0, 0, 5) * math.tau
        r = 4 + h(k, 1, 0, 5) * 9
        cx, cz = hx + math.cos(a) * r, hz + math.sin(a) * r
        cy = 9 + h(k, 2, 0, 5) * 12
        s = 1.2 + h(k, 3, 0, 5) * 2.2
        for x in range(int(cx - s - 1), int(cx + s + 2)):
            for z in range(int(cz - s - 1), int(cz + s + 2)):
                for y in range(int(cy - s), int(cy + s + 1)):
                    dd = math.sqrt((x - cx) ** 2 + (z - cz) ** 2 + ((y - cy) * 1.6) ** 2)
                    if dd <= s:
                        v.put(x, y, z, "minecraft:prismarine" if y > cy else "minecraft:dark_prismarine")
        if k % 3 == 0:
            v.put(round(cx), int(cy - s) - 1, round(cz), SOUL_H)
    # 集まる所 (真ん中の円い段と鐘)
    v.disc(0, 3, 4, 4.5, "minecraft:smooth_sandstone")
    v.disc(0, 3, 4, 4.5, "minecraft:prismarine_bricks", r0=3.6)
    v.put(0, 4, 4, "minecraft:bell[attachment=floor,facing=north,powered=false]")
    # 南の祠: 立石の輪 (八本) と、宙に吊った石から鎖と魂の灯り。真ん中に帰りの石
    sx0, sz0 = 0, 17
    v.disc(sx0, 3, sz0, 4.4, "minecraft:dark_prismarine")
    for k in range(8):
        a = math.radians(k * 45 + 22.5)
        x, z = sx0 + round(math.cos(a) * 4.5), sz0 + round(math.sin(a) * 4.5)
        ht = 6 + int(h(k, 0, 0, 7) * 4)
        v.column(x, z, 3, 3 + ht, lambda x, y, z: "minecraft:prismarine" if h(x, y, z) < 0.6 else "minecraft:dark_prismarine")
        if k % 2 == 0:
            v.put(x, 4 + ht, z, "minecraft:sea_lantern")
    v.box(sx0 - 1, 13, sz0 - 1, sx0 + 1, 14, sz0 + 1, "minecraft:dark_prismarine")
    v.column(sx0, sz0, 8, 12, CHAIN)
    v.put(sx0, 7, sz0, SOUL_H)
    v.put(sx0, 4, sz0, "minecraft:lodestone")
    # 縁の折れた柱と、闇の欠け目に光の粒 (海のランタンの欠片)
    for k in range(14):
        a = math.radians(k * 360 / 14 + 10)
        x, z = round(math.cos(a) * (R - 2)), round(math.sin(a) * (R - 2) * 1.05)
        if z < -6 and abs(x) < 14:
            continue
        ht = 2 + int(h(k, 5, 0) * 5)
        v.column(x, z, 2, 2 + ht, "minecraft:prismarine_bricks")
        v.put(x, 3 + ht, z, "minecraft:prismarine_wall" if k % 2 else slab("dark_prismarine"))
    return v


# ====================================================================== C 殻の岬
def rough_c():
    v = V()
    R = 20
    # 台地: 段の地層 (砂・砂岩・海晶・テラコッタ) の削った崖。上の面は y 7
    strata = ["minecraft:sandstone", "minecraft:smooth_sandstone", "minecraft:white_terracotta", "minecraft:prismarine",
              "minecraft:sandstone", "minecraft:cut_sandstone", "minecraft:light_gray_terracotta", "minecraft:prismarine_bricks"]
    for x in range(-R - 6, R + 7):
        for z in range(-R - 6, R + 7):
            a = math.atan2(z, x)
            edge = R + 2.2 * math.sin(a * 3 + 0.7) + 1.5 * math.sin(a * 7) + 1.2 * (h(x, 0, z, 2) - 0.5)
            d = math.hypot(x, z)
            for y in range(0, 8):
                shrink = max(0, (y - 2)) * 0.35 + (0.9 if y % 3 == 2 else 0)
                if d <= edge - shrink:
                    v.put(x, y, z, strata[y] if h(x, y, z, 4) < 0.85 else "minecraft:sandstone")
            if d <= edge - 2.3:
                v.put(x, 7, z, "minecraft:sand" if h(x, 7, z) < 0.3 else "minecraft:smooth_sandstone")
            # 裾の岩
            if edge - 0.5 < d <= edge + 3 and h(x, 0, z, 6) < 0.35:
                v.column(x, z, 0, int(h(x, 1, z, 6) * 3), "minecraft:prismarine" if h(x, 2, z) < 0.5 else "minecraft:sandstone")
    # 上の段の庭: 二段の池と、そこから崖の外へ落ちる滝 (東)
    v.disc(9, 7, 4, 4.2, "minecraft:prismarine_bricks")
    v.disc(9, 7, 4, 3.4, WATER)
    v.disc(9, 6, 4, 3.4, "minecraft:sea_lantern" if False else "minecraft:dark_prismarine")
    for z in range(3, 6):
        for x in range(13, 24):
            if (x, 6, z) in v.c or (x, 7, z) in v.c:
                v.put(x, 7, z, WATER)
                v.put(x, 6, z, "minecraft:prismarine_bricks")
    # 庭の苔とつつじ・珊瑚の色の石
    for k in range(40):
        a = h(k, 0, 0, 8) * math.tau
        r = 6 + h(k, 1, 0, 8) * (R - 9)
        x, z = round(math.cos(a) * r), round(math.sin(a) * r)
        if z < -10 or math.hypot(x - 9, z - 4) < 5:
            continue
        v.put(x, 7, z, "minecraft:moss_block")
        v.put(x, 8, z, ["minecraft:flowering_azalea[]".replace("[]", ""), "minecraft:azalea", "minecraft:fern", "minecraft:sea_pickle[pickles=2,waterlogged=false]"][k % 4])
    # 集まる所: 板張りの円い台 (木の床・縁は縄の柵) と鐘
    v.disc(-3, 8, 2, 6.3, "minecraft:stripped_oak_wood[axis=y]")
    v.disc(-3, 8, 2, 6.3, slab("spruce"), r0=5.6)
    v.disc(-3, 9, 2, 7.1, "minecraft:spruce_fence", r0=6.4)
    for k in range(8):
        a = math.radians(k * 45)
        x, z = -3 + round(math.cos(a) * 6.8), 2 + round(math.sin(a) * 6.8)
        v.column(x, z, 9, 11, "minecraft:stripped_spruce_log[axis=y]")
        v.put(x, 12, z, LANT)
    for x in range(-4, -1):
        for z in (-5, -4, -3):
            v.c.pop((x, 9, z), None)
    v.put(-3, 9, 2, "minecraft:bell[attachment=floor,facing=north,powered=false]")
    # 北の巻貝の岩の塔 (らせんに細る塔。足もとに洞の門 = 順路の口)
    tx, tz = 0, -15
    for y in range(7, 34):
        r = 7.5 - (y - 7) * 0.22
        if r < 1:
            break
        a0 = (y - 7) * 0.45
        for x in range(int(tx - r - 2), int(tx + r + 3)):
            for z in range(int(tz - r - 2), int(tz + r + 3)):
                dx, dz = x - tx, z - tz
                d = math.hypot(dx, dz)
                a = math.atan2(dz, dx)
                ridge = 0.9 * math.cos(a * 1 - a0)
                if d <= r + ridge:
                    band = (math.degrees(a - a0) % 360) < 40
                    v.put(x, y, z, "minecraft:prismarine_bricks" if band else ("minecraft:smooth_quartz" if y % 4 else "minecraft:calcite"))
    # 洞の門 (塔の南の面に、高さ 7 の口。中は光る渦)
    for x in range(-3, 4):
        top = 14 + round(1.5 * math.cos(x / 3.0 * math.pi / 2))
        for y in range(8, top + 1):
            for z in range(tz + 2, tz + 9):
                if (x, y, z) in v.c:
                    v.c.pop((x, y, z))
            v.put(x, y, tz + 1, "minecraft:light_blue_stained_glass" if (x + y) % 3 else "minecraft:sea_lantern")
        v.put(x, top + 1, tz + 8, "minecraft:dark_prismarine")
    for sx in (-4, 4):
        v.column(sx, tz + 7, 8, 14, "minecraft:dark_prismarine")
        v.put(sx, 15, tz + 7, "minecraft:sea_lantern")
    # 塔への板の道
    for z in range(tz + 9, -3):
        v.box(-1, 7, z, 1, 7, z, "minecraft:spruce_planks")
    # 崖に彫った祠 (西の崖。岩の窪みに帰りの石と、垂れる蔦と灯り)
    gx, gz = -R + 1, 2
    for x in range(gx - 1, gx + 5):
        for y in range(3, 8):
            for z in range(gz - 3, gz + 4):
                if (x, y, z) in v.c and y >= 3 and abs(z - gz) <= 2 and x >= gx:
                    v.c.pop((x, y, z))
    v.box(gx, 2, gz - 2, gx + 4, 2, gz + 2, "minecraft:smooth_sandstone")
    v.put(gx + 3, 3, gz, "minecraft:lodestone")
    v.put(gx + 4, 6, gz, LANT_H)
    for z in range(gz - 2, gz + 3):
        v.put(gx - 1, 7, z, "minecraft:dark_prismarine")
        if z != gz:
            v.put(gx - 1, 6, z, "minecraft:vine[east=true,north=false,south=false,up=false,west=false]")
    for z in (gz - 3, gz + 3):
        v.column(gx - 1, z, 3, 6, "minecraft:prismarine_bricks")
        v.put(gx - 1, 7, z, "minecraft:sea_lantern")
    # 祠へ下りる崖の階段 (台地から)
    for i in range(4):
        v.box(gx + 5 + i, 3 + i, gz - 1, gx + 5 + i, 3 + i, gz + 1, stairs("sandstone", "west"))
        for y in range(4 + i, 8):
            v.c.pop((gx + 5 + i, y, gz), None)
            v.c.pop((gx + 5 + i, y, gz - 1), None)
            v.c.pop((gx + 5 + i, y, gz + 1), None)
    return v


if __name__ == "__main__":
    OUT.mkdir(parents=True, exist_ok=True)
    for name, fn in (("rough_lobby_a", rough_a), ("rough_lobby_b", rough_b), ("rough_lobby_c", rough_c)):
        print(name, fn().write(name), "cells")
