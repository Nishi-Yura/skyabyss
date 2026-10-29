#!/usr/bin/env python3
"""章の小要素 (ch1_*〜ch4_*・tide_*・ember_*) を作り込みの基準 (削り出した岩・縦の層・細部の密度) で作り直す (2026-10-28 のクオリティ点検)。

    PYTHONIOENCODING=utf-8 python -I tools/gen/world/build_chsites.py [名前…]      (名前を省くと全部)

印 (marks)・範囲 (meta.rect)・site は今のまま。印を持つブロックと、上に物を置く印 (top・rod・banner・post・cairn・stake) の下の柱は
今のブロックを残す (ゲームの仕掛けが読む)。そのまわりの場面を作る: 踏まれた地面と道・大きめの削り岩・土地の木・小物・草花。
地下の大きな部屋 (骨の坑・割れ殻の洞・塩の蔵・消えた炉の坑・歌の間・焼けた見張り塔・避雷の塔) は対象外。
撮影: tools/shots/chsite_audit.txt
"""
import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from poi_kit import AIR, BP, CHAIN_Y, column, disc, fence, ground, hash3, lantern, log, noise3, rock, scatter, sl, st, trap, wall  # noqa: E402
import build_pois as P  # noqa: E402

G = "minecraft:grass_block[snowy=false]"
REG = {
    "whale": dict(earth=[(G, 8), ("minecraft:coarse_dirt", 1), ("minecraft:moss_block", 1)],
                  path=[("minecraft:dirt_path", 3), ("minecraft:coarse_dirt", 2), ("minecraft:gravel", 1)],
                  rocks=[(0, ["minecraft:mossy_cobblestone", "minecraft:stone", "minecraft:andesite"]),
                         (99, ["minecraft:stone", "minecraft:andesite", "minecraft:stone", "minecraft:tuff"])],
                  plants=["minecraft:short_grass", "minecraft:short_grass", "minecraft:fern", "minecraft:short_grass", "minecraft:dandelion",
                          "minecraft:sweet_berry_bush[age=1]"],
                  tree="spruce", wood="spruce", cap="minecraft:moss_carpet"),
    "tide": dict(earth=[("minecraft:sand", 6), ("minecraft:gravel", 1), (G, 1), ("minecraft:coarse_dirt", 1)],
                 path=[("minecraft:sand", 2), ("minecraft:gravel", 2), ("minecraft:coarse_dirt", 1)],
                 rocks=[(0, ["minecraft:sandstone", "minecraft:smooth_sandstone", "minecraft:gravel"]),
                        (99, ["minecraft:stone", "minecraft:andesite", "minecraft:diorite", "minecraft:calcite"])],
                 plants=["minecraft:short_grass", "minecraft:dead_bush", "minecraft:short_grass"],
                 tree="birch", wood="birch", cap=None),
    "storm": dict(earth=[("minecraft:stone", 3), ("minecraft:gravel", 2), (G, 4), ("minecraft:andesite", 1)],
                  path=[("minecraft:gravel", 3), ("minecraft:cobblestone", 1), ("minecraft:coarse_dirt", 1)],
                  rocks=[(0, ["minecraft:stone", "minecraft:andesite", "minecraft:cobblestone"]),
                         (99, ["minecraft:stone", "minecraft:andesite", "minecraft:tuff", "minecraft:cobblestone"])],
                  plants=["minecraft:short_grass", "minecraft:fern", "minecraft:snow[layers=1]", "minecraft:snow[layers=1]"],
                  tree="spruce", wood="spruce", cap="minecraft:snow[layers=1]"),
    "ember": dict(earth=[("minecraft:coarse_dirt", 4), ("minecraft:red_sand", 1), ("minecraft:gravel", 1), ("minecraft:blackstone", 1), ("minecraft:terracotta", 1)],
                  path=[("minecraft:coarse_dirt", 2), ("minecraft:gravel", 1), ("minecraft:blackstone", 1)],
                  rocks=[(0, ["minecraft:blackstone", "minecraft:basalt[axis=y]", "minecraft:coarse_dirt"]),
                         (99, ["minecraft:blackstone", "minecraft:basalt[axis=y]", "minecraft:smooth_basalt", "minecraft:tuff"])],
                  plants=["minecraft:dead_bush", "minecraft:dead_bush", "minecraft:short_grass"],
                  tree="dead", wood="dark_oak", cap="minecraft:gray_carpet"),
}
# 上に物を置く印: 印の柱 (y = 0..印) を今のまま残す
COLUMN_MARKS = ("top", "rod", "banner", "post", "cairn", "stake", "scope", "stone", "barrel", "spyglass")


def region(name):
    if name.startswith("ch1_"):
        return "whale"
    if name.startswith(("ch2_", "tide_")):
        return "tide"
    if name.startswith("ch3_"):
        return "storm"
    return "ember"


GROW = 3   # 範囲を各辺 GROW だけ広げる (場面の岩と木を範囲の内に置く。保存は範囲 + 1 までしか残らない)


# 三章の雷の小要素のうち、仕掛けが「小さな設計図」(幅 8 まで) を求めるもの (StormChapterSelfTest)
SMALL = {"ch3_pack": 1, "ch3_tablet": 1, "ch3_vane": 1, "ch1_tablet": 1, "ch4_tablet": 1}


def newbp(name, grow=GROW):
    grow = SMALL.get(name, grow)
    bp = BP(name)
    r = bp.rect
    bp.core = list(r)
    bp.rect = [r[0] - grow, r[1] - grow, r[2] + grow, r[3] + grow]
    bp.meta = dict(bp.meta)
    bp.meta["rect"] = bp.rect
    bp.meta["ring"] = max(int(bp.meta.get("ring", 2)), 3)
    return bp


def keep_core(bp, extra=lambda x, y, z, s: False):
    """印のまわり (印の柱・印のブロック・その下) と extra の今のブロックを残す"""
    cols = set()
    for k, (x, y, z) in bp.marks.items():
        if k in COLUMN_MARKS or k in ("chest", "tablet", "cache", "sign"):
            cols.add((x, z, y))
    bp.keep_old(lambda x, y, z, s: any(x == cx and z == cz and -1 <= y <= cy for cx, cz, cy in cols) or extra(x, y, z, s))


class Scene:
    """場面の部品 (印を避けて置く)"""

    def __init__(self, bp, reg, seed):
        self.bp, self.r, self.seed = bp, REG[reg], seed
        self.rect = bp.rect
        self.marks = [tuple(m) for m in bp.marks.values()]

    def free(self, x, z, d=1):
        if not (self.rect[0] - 1 <= x <= self.rect[2] + 1 and self.rect[1] - 1 <= z <= self.rect[3] + 1):
            return False
        return all(abs(x - m[0]) > d or abs(z - m[2]) > d for m in self.marks)

    def empty(self, x, y, z):
        return self.bp.get(x, y, z) in (None, AIR)

    def earth(self, r=None):
        rr = r or max(abs(v) for v in self.rect)
        ground(self.bp, rr + 1, self.r["earth"], self.seed, edge_fade=0.3)

    def path(self, x0, z0, x1, z1, w=1):
        P.path_line(self.bp, x0, z0, x1, z1, w, self.seed, self.r["path"])

    def boulder(self, x, z, rx, ry, rz, y=0.2):
        if not self.free(x, z, 2):
            return
        rock(self.bp, x, y, z, rx, ry, rz, self.r["rocks"], self.seed + x * 7 + z, 0.42, below=False)
        # 岩の上の苔・雪・灰
        cap = self.r["cap"]
        if cap:
            for dx in range(-int(rx) - 1, int(rx) + 2):
                for dz in range(-int(rz) - 1, int(rz) + 2):
                    for yy in range(int(y + ry) + 2, -1, -1):
                        if not self.empty(x + dx, yy, z + dz):
                            top = self.bp.get(x + dx, yy, z + dz) or ""
                            if any(k in top for k in ("stone", "andesite", "tuff", "cobble", "basalt", "sandstone", "diorite", "calcite"))                                     and "slab" not in top and "stairs" not in top and "wall" not in top and self.empty(x + dx, yy + 1, z + dz) and hash3(x + dx, yy, z + dz, self.seed) < (0.22 if "snow" in cap else 0.45):
                                self.bp.set(x + dx, yy + 1, z + dz, cap)
                            break

    def tree(self, x, z, h=5):
        if not self.free(x, z, 2):
            return
        kind = self.r["tree"]
        b = self.bp
        if kind == "dead":
            column(b, x, z, 0, h - 1, log("stripped_dark_oak_log"))
            for dx, dz, y, ax in ((1, 0, h - 2, "x"), (-1, 0, h - 3, "x"), (0, 1, h - 1, "z")):
                b.set(x + dx, y, z + dz, log("dark_oak_log", ax))
                b.set(x + 2 * dx, y + 1, z + 2 * dz, fence("dark_oak"))
            return
        column(b, x, z, 0, h - 1, log(f"{kind}_log"))
        leaf = f"minecraft:{kind}_leaves[distance=1,persistent=true,waterlogged=false]"
        for y in range(h - 3, h + 2):
            rad = 2.2 if y < h - 1 else 1.5 if y < h + 1 else 0.8
            if kind == "spruce":
                rad = max(0.6, 2.4 - (y - (h - 3)) * 0.45)
            for dx in range(-3, 4):
                for dz in range(-3, 4):
                    if dx * dx + dz * dz <= rad * rad + hash3(x + dx, y, z + dz, self.seed) * 0.7 and not (dx == 0 and dz == 0 and y < h):
                        if self.empty(x + dx, y, z + dz) and self.free(x + dx, z + dz, 0):
                            b.set(x + dx, y, z + dz, leaf)
        b.set(x, h + 1, z, leaf)

    def plants(self, density=0.16):
        rr = max(abs(v) for v in self.rect) + 1
        for x in range(-rr, rr + 1):
            for z in range(-rr, rr + 1):
                if not self.free(x, z, 1) or not self.empty(x, 0, z) or hash3(x, 5, z, self.seed) >= density:
                    continue
                g = self.bp.get(x, -1, z) or ""
                p = self.r["plants"][int(hash3(x, 6, z, self.seed) * len(self.r["plants"])) % len(self.r["plants"])]
                if "berry" in p and "grass" not in g:
                    continue
                if ("grass" in p or "fern" in p) and not any(k in g for k in ("grass", "podzol", "moss", "dirt")):
                    continue
                if "snow" in p and "sand" in g:
                    continue
                if g:
                    self.bp.set(x, 0, z, p)

    def setf(self, x, y, z, s, d=0):
        if self.free(x, z, d) and self.empty(x, y, z):
            self.bp.set(x, y, z, s)

    def logpile(self, x, z, axis="x", n=3):
        if all(self.free(x + (k if axis == "x" else 0), z + (k if axis == "z" else 0), 0) for k in range(n)):
            P.logpile(self.bp, x, z, axis, n, self.r["wood"], self.seed)

    def lamp(self, x, z, h=3):
        if self.free(x, z, 1) and self.free(x + 1, z, 1):
            P.lamp_post(self.bp, x, z, h, self.r["wood"] if self.r["wood"] != "dark_oak" else "dark_oak")

    def firepit(self, x, z, lit=False):
        if not self.free(x, z, 1):
            return
        for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1), (1, 1), (-1, -1), (1, -1), (-1, 1)):
            self.setf(x + dx, 0, z + dz, sl("cobblestone") if (dx + dz) % 2 else "minecraft:cobblestone_wall[east=none,north=none,south=none,up=true,waterlogged=false,west=none]" if False else sl("cobblestone"))
        self.bp.set(x, -1, z, "minecraft:coarse_dirt")
        self.bp.set(x, 0, z, "minecraft:campfire[facing=north,lit=%s,signal_fire=false,waterlogged=false]" % ("true" if lit else "false"))

    def surround(self, rocks=3, trees=1):
        """広げた縁 (元の範囲の外) に、大きめの削り岩と木を置く"""
        c = self.bp.core
        cx, cz = (c[0] + c[2]) / 2, (c[1] + c[3]) / 2
        rad = max(c[2] - c[0], c[3] - c[1]) / 2 + 2.6
        placed_r = placed_t = 0
        for k in range(16):
            a = 2 * math.pi * (k * 0.618 + hash3(k, 3, 1, self.seed) * 0.2)
            x = int(round(cx + math.cos(a) * rad))
            z = int(round(cz + math.sin(a) * rad))
            if not self.free(x, z, 2) or not self.empty(x, 0, z):
                continue
            if placed_r < rocks and k % 3 != 2:
                s_ = 1.8 + hash3(k, 1, 1, self.seed) * 0.9
                self.boulder(x, z, s_, s_ * 0.75, s_ * 0.85, 0.1)
                placed_r += 1
            elif placed_t < trees:
                self.tree(x, z, 5 + int(hash3(k, 2, 2, self.seed) * 2))
                placed_t += 1
            if placed_r >= rocks and placed_t >= trees:
                break

    def done(self, density=0.16, rocks=3, trees=1):
        self.surround(rocks, trees)
        self.plants(density)
        # 立てた灯り・物の下の道 (dirt_path は一段低く、灯りが立たない) は粗い土に
        for (x, y, z), st_ in list(self.bp.cells.items()):
            if st_ not in (None, AIR) and y >= 0 and (self.bp.get(x, y - 1, z) or "").startswith("minecraft:dirt_path"):
                self.bp.set(x, y - 1, z, "minecraft:coarse_dirt")
        self.bp.keep_marks()
        return self.bp


# ---------------------------------------------------------------- 一章 (北の丘)
def ch1_lookout():
    bp = newbp("ch1_lookout")
    keep_core(bp, lambda x, y, z, s: abs(x) <= 1 and abs(z) <= 1 and y <= 1)
    sc = Scene(bp, "whale", 501)
    sc.earth()
    # 削った岩の高台の縁 (西と北に高く、見張りの台を抱く)
    sc.boulder(-4, -3, 2.2, 1.8, 2.0)
    sc.boulder(3, -4, 2.0, 1.4, 1.6)
    sc.boulder(-4, 2, 1.6, 1.2, 1.8, 0.0)
    # 台: 石の床と低い壁、角の柱、望遠鏡の台
    for x in range(-2, 3):
        for z in range(-2, 3):
            if bp.get(x, -1, z) is None or abs(x) == 2 or abs(z) == 2:
                bp.set(x, -1, z, "minecraft:stone_bricks" if hash3(x, 0, z, 1) < 0.7 else "minecraft:cracked_stone_bricks")
    for x, z in ((-2, -2), (2, -2), (-2, 2)):
        column(bp, x, z, 0, 2, wall("stone_brick"))
        bp.set(x, 3, z, sl("stone_brick"))
    for x in range(-1, 2):
        sc.setf(x, 0, -2, wall("mossy_stone_brick"))
    for z in range(-1, 2):
        sc.setf(-2, 0, z, wall("stone_brick"))
    # 見張りの差し掛け (北東): 唐檜の柱に板の屋根
    for x, z in ((2, -2), (3, -2)):
        pass
    sc.setf(3, 0, 1, log("spruce_log"))
    sc.setf(3, 1, 1, log("spruce_log"))
    for x in range(2, 5):
        for z in range(0, 3):
            sc.setf(x, 2, z, sl("spruce", "top"))
    sc.setf(4, 0, 0, "minecraft:barrel[facing=up,open=false]")
    sc.setf(3, 0, 3, sl("spruce"))
    sc.lamp(-3, 4)
    sc.path(0, 4, 0, 6)
    return sc.done()


def ch1_road_shrine():
    bp = newbp("ch1_road_shrine")
    keep_core(bp)
    sc = Scene(bp, "whale", 502)
    sc.earth()
    sc.path(0, 3, 0, 5)
    # 二段の石の台と小さな社 (切妻の黒樫の瓦、中に石の像と蝋燭)
    for x in range(-2, 3):
        for z in range(-3, 1):
            bp.set(x, -1, z, "minecraft:stone_bricks")
            bp.set(x, 0, z, "minecraft:polished_andesite" if abs(x) < 2 and -3 < z < 0 else sl("stone_brick"))
    for x in (-1, 1):
        column(bp, x, -1, 1, 2, log("stripped_dark_oak_log"))
        column(bp, x, -2, 1, 2, log("stripped_dark_oak_log"))
    bp.set(0, 1, -2, "minecraft:dark_oak_planks")
    bp.set(0, 2, -2, "minecraft:dark_oak_planks")
    for x in range(-2, 3):
        bp.set(x, 3, -3, st("deepslate_tile", "south"))
        bp.set(x, 3, 0, st("deepslate_tile", "north"))
        bp.set(x, 4, -2, st("deepslate_tile", "south"))
        bp.set(x, 4, -1, st("deepslate_tile", "north"))
    bp.set(-2, 4, -2, wall("deepslate_tile"))
    bp.set(2, 4, -2, wall("deepslate_tile"))
    bp.set(0, 1, -1, "minecraft:chiseled_stone_bricks")
    bp.set(0, 2, -1, "minecraft:stone_button[face=floor,facing=south,powered=false]")
    bp.set(-1, 1, -1, "minecraft:candle[candles=3,lit=true,waterlogged=false]")
    bp.set(1, 1, -1, "minecraft:flower_pot")
    sc.boulder(3, -3, 1.8, 1.4, 1.6)
    sc.setf(-3, 0, -2, P.lantern(False))
    return sc.done(0.2)


def ch1_bone_cache():
    bp = newbp("ch1_bone_cache")
    keep_core(bp)
    sc = Scene(bp, "whale", 503)
    sc.earth()
    # 骨の山: 骨と方解石の丸い山、半ば埋まった大きな肋が二本の弧で覆う
    rock(bp, 0, -0.2, -0.5, 2.6, 1.6, 2.2, [(99, ["minecraft:bone_block[axis=y]", "minecraft:calcite", "minecraft:bone_block[axis=x]", "minecraft:coarse_dirt"])],
         503, 0.45, below=False)
    for x in (-2, 2):
        for y in range(0, 5):
            z = -3 + int(round(math.sin(math.pi * y / 4) * 0)) + y // 2
            sc.setf(x - (1 if x < 0 else -1) * (y // 3), y, -3 + y // 2, "minecraft:bone_block[axis=y]")
    for x in range(-1, 2):
        sc.setf(x, 5, -1, "minecraft:bone_block[axis=x]")
    sc.setf(1, 2, 1, "minecraft:skeleton_skull[powered=false,rotation=8]")
    sc.boulder(-4, -3, 1.8, 1.2, 1.6)
    sc.logpile(2, 3, "x", 2)
    return sc.done()


def ch1_cart_track():
    bp = newbp("ch1_cart_track")
    keep_core(bp, lambda x, y, z, s: y >= 0)   # 失せ物の荷車の脇の金床 (旅の職人の印) と荷はそのまま
    sc = Scene(bp, "whale", 504)
    sc.earth()
    # 二本の轍 (荷車の跡) と、落ちた荷
    for x in range(-5, 6):
        for z in (-1, 1):
            if sc.free(x, z, 0):
                bp.set(x, -1, z, "minecraft:coarse_dirt" if hash3(x, 0, z, 4) < 0.6 else "minecraft:dirt_path")
    sc.setf(-3, 0, -2, "minecraft:barrel[facing=east,open=false]")
    sc.setf(-3, 0, -3, P.trap("spruce", "north", "bottom"))
    sc.setf(3, 0, 2, "minecraft:spruce_planks")
    sc.setf(4, 0, 2, sl("spruce"))
    sc.setf(3, 1, 2, "minecraft:white_carpet")
    sc.setf(-4, 0, 2, "minecraft:hay_block[axis=x]")
    sc.boulder(4, -3, 1.6, 1.1, 1.4)
    return sc.done()


def ch1_diary_spot():
    bp = newbp("ch1_diary_spot")
    keep_core(bp)
    sc = Scene(bp, "whale", 505)
    sc.earth()
    # 傾いた岩の下の野宿: 北に張り出した岩、寝床の毛布、消えた焚き火、丸太の腰かけ
    sc.boulder(-2, -4, 3.0, 2.2, 1.8, 0.8)
    for x in range(-4, 1):
        sc.setf(x, 3, -2, sl("cobblestone", "top"))
        sc.setf(x, 3, -1, sl("mossy_cobblestone", "top"), 0)
    sc.setf(-1, 0, -1, "minecraft:brown_carpet")
    sc.setf(-1, 0, 0, "minecraft:brown_carpet")
    sc.setf(-2, 0, -1, "minecraft:white_carpet")
    sc.firepit(2, 0)
    sc.logpile(3, -2, "z", 2)
    return sc.done()


def ch1_lost_cart():
    bp = newbp("ch1_lost_cart")
    keep_core(bp)
    sc = Scene(bp, "whale", 506)
    sc.earth()
    sc.path(-6, 3, 6, 3)
    # 傾いた荷車: 荷台の板 (片側は半分の板で傾ける)、外れた車輪、こぼれた荷
    for x in range(-2, 3):
        for z in (0, 3):   # 荷台の板 (箱の蓋の前 z = 2 はあける)
            sc.setf(x, 0, z, "minecraft:spruce_planks" if x < 1 else sl("spruce"), 0)
    for x in (-2, 2):
        sc.setf(x, 1, 0, fence("spruce"), 0)
    sc.setf(-3, 0, -1, P.trap("dark_oak", "north", "bottom", True))
    sc.setf(-3, 1, -1, P.trap("dark_oak", "north", "top", True))
    sc.setf(4, 0, -1, P.trap("dark_oak", "east", "bottom", True))
    sc.setf(3, 0, 0, fence("spruce"))
    sc.setf(4, 0, 0, fence("spruce"))
    for x, z in ((-4, 1), (-4, 2), (4, 2)):
        sc.setf(x, 0, z, "minecraft:hay_block[axis=y]" if hash3(x, 0, z, 6) < 0.5 else "minecraft:barrel[facing=up,open=false]")
    sc.setf(-1, 0, -2, "minecraft:white_carpet")
    sc.boulder(5, -3, 1.6, 1.1, 1.4)
    return sc.done()


def ch1_road_cairn():
    bp = newbp("ch1_road_cairn")
    sc = Scene(bp, "whale", 507)
    sc.earth(2)
    # 道の積み石: 太い石の台に、だんだん小さく四段、てっぺんに供えの石。まわりに小石
    for x in range(-1, 2):
        for z in range(-1, 2):
            bp.set(x, 0, z, "minecraft:mossy_cobblestone" if (x + z) % 2 else "minecraft:cobblestone")
    bp.set(0, 1, 0, "minecraft:cobblestone")
    bp.set(1, 1, 0, sl("cobblestone"))
    bp.set(0, 1, -1, sl("mossy_cobblestone"))
    bp.set(0, 2, 0, "minecraft:mossy_cobblestone")
    bp.set(0, 3, 0, "minecraft:cobblestone")
    bp.set(0, 4, 0, sl("mossy_cobblestone"))
    for x, z in ((-2, 1), (2, -1), (-1, -2)):
        sc.setf(x, 0, z, sl("cobblestone"))
    sc.setf(1, 0, 1, "minecraft:candle[candles=1,lit=false,waterlogged=false]")
    return sc.done(0.3)


def ch1_song_stone(n):
    def build():
        bp = newbp(f"ch1_song_stone_{n}")
        keep_core(bp)
        sc = Scene(bp, "whale", 510 + n)
        sc.earth()
        # 歌い石を囲む苔の輪と、低い立石が三つ (欠けた頭)
        for x, z in P.ring_states(0, 0, 2) if hasattr(P, "ring_states") else []:
            pass
        for (x, z) in [(-2, -2), (2, -2), (-3, 1)]:
            if sc.free(x, z, 0):
                h = 2 + int(hash3(x, 0, z, n) * 2)
                column(bp, x, z, 0, h - 1, "minecraft:mossy_stone_bricks" if hash3(x, 1, z, n) < 0.5 else "minecraft:stone")
                bp.set(x, h, z, wall("mossy_stone_brick"))
        for x in range(-1, 2):
            for z in range(-1, 2):
                if (x, z) != (0, 0):
                    bp.set(x, -1, z, "minecraft:mossy_stone_bricks" if hash3(x, 0, z, n) < 0.6 else "minecraft:moss_block")
        sc.setf(1, 0, -1, "minecraft:moss_carpet", 0)
        sc.setf(-1, 0, 1, "minecraft:moss_carpet", 0)
        return sc.done(0.24)
    return build


def trace_scene(name, seed, kind):
    def build():
        bp = newbp(name)
        keep_core(bp, lambda x, y, z, s: kind == "well" and abs(x) <= 2 and abs(z) <= 2)
        sc = Scene(bp, "whale", seed)
        sc.earth()
        sc.path(0, 3, 0, 5)
        if kind == "altar":
            # 石の祭壇と三本の蝋燭、崩れた石の座
            for x in range(-1, 2):
                bp.set(x, 0, -3, "minecraft:stone_bricks")
                bp.set(x, 1, -3, sl("stone_brick"))
            sc.setf(-1, 2, -3, "minecraft:candle[candles=2,lit=false,waterlogged=false]", 0)
            sc.setf(1, 2, -3, "minecraft:candle[candles=1,lit=false,waterlogged=false]", 0)
            for x in (-3, 3):
                sc.setf(x, 0, -1, "minecraft:cracked_stone_bricks")
                sc.setf(x, 1, -1, sl("stone_brick"))
        elif kind == "camp":
            P.tent(bp, -3, -1, "z", 3, "brown", seed)
            sc.firepit(1, 1)
            sc.logpile(3, -2, "z", 2)
        elif kind == "cart":
            for x in range(-2, 3):
                sc.setf(x, 0, 0, "minecraft:spruce_planks" if x < 2 else sl("spruce"), 0)
            sc.setf(-3, 0, -1, P.trap("dark_oak", "north", "bottom", True))
            sc.setf(3, 0, 1, P.trap("dark_oak", "south", "bottom", True))
            sc.setf(-4, 0, 2, "minecraft:hay_block[axis=x]")
        elif kind == "pack":
            sc.setf(-2, 0, 0, "minecraft:brown_carpet")
            sc.setf(-2, 0, 1, "minecraft:white_carpet")
            sc.setf(1, 0, -1, log("spruce_log"))   # 切り株
            sc.setf(1, 1, -1, "minecraft:lantern[hanging=false,waterlogged=false]")
        elif kind == "well":
            sc.setf(3, 0, 0, sl("cobblestone"))
            sc.setf(-3, 0, 1, "minecraft:cauldron")
        sc.boulder(-4, 3, 1.8, 1.2, 1.5)
        return sc.done()
    return build


# ---------------------------------------------------------------- 二章 (潮の浜)
def ch2_tidal_pool():
    bp = newbp("ch2_tidal_pool")
    keep_core(bp, lambda x, y, z, s: "water" in s or y == -1)
    sc = Scene(bp, "tide", 521)
    # 岩の縁に囲まれた潮溜まり: 縁に削り岩 (藻と貝)、溜まりの底に砂利と海草
    for (x, z, r) in ((-5, -3, 1.8), (4, 3, 2.0), (-3, 5, 1.6), (5, -4, 1.7)):
        sc.boulder(x, z, r, 1.2, r * 0.9, 0.0)
    for (x, y, z), s in list(bp.cells.items()):
        if "water" in s and y == -1 and hash3(x, y, z, 521) < 0.35:
            bp.set(x, -2, z, "minecraft:gravel")
            bp.set(x, -1, z, "minecraft:seagrass" if hash3(x, 1, z, 521) < 0.5 else s)
    for x, z in ((-4, 0), (3, -3), (1, 4)):
        sc.setf(x, 0, z, "minecraft:dead_brain_coral_fan[waterlogged=false]" if hash3(x, 0, z, 3) < 0.5 else "minecraft:dead_tube_coral[waterlogged=false]")
    return sc.done(0.08)


def ch2_drift_raft():
    bp = newbp("ch2_drift_raft")
    keep_core(bp, lambda x, y, z, s: "planks" in s or "log" in s or "slab" in s or "fence" in s)
    sc = Scene(bp, "tide", 522)
    sc.earth()
    # 流れ着いた筏のまわりに流木と網、割れた樽、浜の岩
    sc.logpile(-4, 2, "x", 3)
    for x, z in ((3, -2), (4, -1)):
        sc.setf(x, 0, z, "minecraft:cobweb")
    sc.setf(3, 0, 2, "minecraft:barrel[facing=east,open=true]")
    sc.boulder(4, 4, 1.8, 1.0, 1.4, 0.0)
    sc.boulder(-4, -4, 1.6, 1.1, 1.6, 0.0)
    return sc.done(0.1)


def marker(name, seed, reg, mats):
    """碑・積み石・標: 今の柱は残し、根元に台と崩れた石、まわりに地面と小物"""
    def build():
        bp = newbp(name)
        # 碑・標・積み石は今の形を全部残す (塩の標の矢・積み石の段は仕掛けが読む)
        keep_core(bp, lambda x, y, z, s: True)
        sc = Scene(bp, reg, seed)
        sc.earth()
        for x in range(-1, 2):
            for z in range(-1, 2):
                if (x, z) != (0, 0):
                    sc.setf(x, 0, z, sl(mats[0]) if (x + z) % 2 else mats[1], 0)
        for x, z in ((-2, 1), (2, -1), (1, 2), (-2, -2)):
            sc.setf(x, 0, z, sl(mats[0]))
        rr = max(abs(v) for v in bp.rect)
        if rr >= 3:
            sc.boulder(rr, -rr, 1.6, 1.0, 1.4, 0.0)
        return sc.done(0.2)
    return build


def tide_cargo():
    bp = newbp("tide_cargo")
    keep_core(bp)
    sc = Scene(bp, "tide", 531)
    sc.earth()
    # 打ち上げられた荷: 割れた箱板、流木、網と縄
    sc.logpile(-3, 1, "x", 3)
    for x, z in ((2, -1), (3, 1)):
        sc.setf(x, 0, z, "minecraft:spruce_planks" if hash3(x, 0, z, 9) < 0.5 else sl("spruce"))
    sc.setf(1, 0, 1, "minecraft:barrel[facing=north,open=true]")
    sc.setf(-1, 0, -2, "minecraft:cobweb")
    sc.boulder(4, -2, 1.6, 1.0, 1.4, 0.0)
    return sc.done(0.08)


def ch2_ebb_stakes():
    bp = newbp("ch2_ebb_stakes")
    keep_core(bp, lambda x, y, z, s: "log" in s or "fence" in s)
    sc = Scene(bp, "tide", 532)
    sc.earth()
    for x, z in ((-3, -2), (3, -3), (2, 2)):
        if sc.free(x, z, 1):
            column(bp, x, z, 0, 1 + int(hash3(x, 0, z, 2) * 2), log("stripped_dark_oak_log"))
    sc.boulder(-3, 3, 1.6, 1.0, 1.4, 0.0)
    return sc.done(0.08)


# ---------------------------------------------------------------- 三章 (雷の岩山)
def ch3_wind_cliff():
    bp = newbp("ch3_wind_cliff")
    keep_core(bp, lambda x, y, z, s: y == -1)
    sc = Scene(bp, "storm", 541)
    # 崖の縁: 北に高い削り岩の列、南へ低く。碑と遠眼鏡の樽は今のまま
    sc.boulder(-3, -5, 2.4, 2.4, 1.6)
    sc.boulder(1, -5, 2.0, 1.8, 1.4)
    sc.boulder(4, -2, 1.6, 1.6, 2.0)
    sc.boulder(-5, 2, 1.8, 1.2, 1.6, 0.0)
    for x in range(-1, 3):
        sc.setf(x, 0, 3, sl("cobblestone"))
    return sc.done(0.2)


def ch3_gleaner_field():
    bp = newbp("ch3_gleaner_field")
    keep_core(bp, lambda x, y, z, s: y >= -1 and abs(x) <= 4 and abs(z) <= 4)
    sc = Scene(bp, "storm", 542)
    sc.earth()
    # 雷拾いの原: 焦げた地面の斑と避雷の杭、拾い手の天幕、外側の岩
    for (x, z) in ((-6, -5), (5, -6), (6, 4), (-5, 5)):
        sc.boulder(x, z, 1.8, 1.4, 1.6, 0.0)
    for x, z in ((-5, 0), (5, -1), (0, -6), (1, 6)):
        if sc.free(x, z, 1):
            column(bp, x, z, 0, 1, fence("spruce"))
            bp.set(x, 2, z, "minecraft:lightning_rod[facing=up,powered=false,waterlogged=false]")
    P.tent(bp, -6, -2, "z", 3, "gray", 542)
    return sc.done(0.14)


def ch3_pack():
    bp = newbp("ch3_pack")
    keep_core(bp, lambda x, y, z, s: abs(x) <= 1 and abs(z) <= 1)
    sc = Scene(bp, "storm", 543)
    sc.earth()
    sc.boulder(-3, -3, 1.6, 1.2, 1.4, 0.0)
    sc.boulder(3, 2, 1.4, 1.0, 1.4, 0.0)
    sc.setf(2, 0, -2, "minecraft:lightning_rod[facing=up,powered=false,waterlogged=false]")
    return sc.done(0.18)


# ---------------------------------------------------------------- 四章・余燼
def ch4_ash_watch():
    bp = newbp("ch4_ash_watch")
    keep_core(bp, lambda x, y, z, s: y <= 0 and abs(x) <= 3 and abs(z) <= 3)
    sc = Scene(bp, "ember", 551)
    sc.earth()
    sc.boulder(-4, 3, 2.0, 1.6, 1.8)
    sc.boulder(4, 3, 1.6, 1.2, 1.6, 0.0)
    for x, z in ((3, 1), (-1, 3)):
        sc.setf(x, 0, z, "minecraft:campfire[facing=north,lit=false,signal_fire=false,waterlogged=false]")
    return sc.done(0.12)


def ch4_soot_kiln():
    bp = newbp("ch4_soot_kiln")
    keep_core(bp, lambda x, y, z, s: abs(x) <= 3 and abs(z) <= 3)
    sc = Scene(bp, "ember", 552)
    sc.earth()
    sc.path(0, 4, 0, 7)
    # 窯のまわり: 炭の山、薪の山、灰の溜まり、煙突の帽子
    rock(bp, -5, -0.2, -3, 1.8, 1.2, 1.6, [(99, ["minecraft:coal_block", "minecraft:blackstone", "minecraft:gray_concrete_powder"])], 552, 0.5, below=False)
    sc.logpile(3, -5, "x", 3)
    sc.logpile(4, 3, "z", 2)
    sc.boulder(5, -2, 1.6, 1.2, 1.6, 0.0)
    return sc.done(0.1)


def ch4_warm_rocks():
    bp = newbp("ch4_warm_rocks")
    keep_core(bp)
    sc = Scene(bp, "ember", 553)
    sc.earth()
    # 温かい岩の群れ: 削った玄武岩の岩が輪になり、割れ目に熾火。真ん中の石 (印) は今のまま
    for i in range(6):
        a = i / 6 * 2 * math.pi + 0.4
        x, z = int(round(math.cos(a) * 3.6)), int(round(math.sin(a) * 3.6))
        if sc.free(x, z, 1):
            rock(bp, x, 0.6, z, 1.1, 1.8 + hash3(i, 0, 0, 553), 1.1,
                 [(0, ["minecraft:smooth_basalt", "minecraft:blackstone"]), (99, ["minecraft:basalt[axis=y]", "minecraft:polished_basalt[axis=y]", "minecraft:blackstone"])],
                 553 + i, 0.35, below=False)
            bp.set(x, -1, z, "minecraft:magma_block")
    # 岩の張り出しを削る (下が空いた岩のブロックは頭の高さに出る。建築の点検 HEAD_DENT)
    for (x, y, z), st in sorted(bp.cells.items(), key=lambda kv: kv[0][1]):
        if y >= 1 and st != "minecraft:air" and bp.get(x, y - 1, z) in (None, "minecraft:air"):
            del bp.cells[(x, y, z)]
    return sc.done(0.1)


def ember_small(name, seed):
    def build():
        bp = newbp(name)
        keep_core(bp, lambda x, y, z, s: y >= -1)
        sc = Scene(bp, "ember", seed)
        sc.earth()
        rr = max(abs(v) for v in bp.rect)
        sc.boulder(-rr - 1, rr, 1.6, 1.2, 1.4, 0.0)
        sc.boulder(rr + 1, -rr, 1.4, 1.0, 1.4, 0.0)
        sc.setf(rr, 0, rr, "minecraft:campfire[facing=north,lit=false,signal_fire=false,waterlogged=false]")
        return sc.done(0.12)
    return build


BUILDERS = {
    "ch1_lookout": ch1_lookout, "ch1_road_shrine": ch1_road_shrine, "ch1_bone_cache": ch1_bone_cache, "ch1_cart_track": ch1_cart_track,
    "ch1_diary_spot": ch1_diary_spot, "ch1_lost_cart": ch1_lost_cart, "ch1_road_cairn": ch1_road_cairn,
    "ch1_trace_altar": trace_scene("ch1_trace_altar", 561, "altar"), "ch1_trace_camp": trace_scene("ch1_trace_camp", 562, "camp"),
    "ch1_trace_cart": trace_scene("ch1_trace_cart", 563, "cart"), "ch1_trace_pack": trace_scene("ch1_trace_pack", 564, "pack"),
    "ch1_trace_well": trace_scene("ch1_trace_well", 565, "well"),
    "ch2_tidal_pool": ch2_tidal_pool, "ch2_drift_raft": ch2_drift_raft, "ch2_ebb_stakes": ch2_ebb_stakes,
    "ch2_salt_mark": marker("ch2_salt_mark", 571, "tide", ("smooth_sandstone", "minecraft:calcite")),
    "tide_stele": marker("tide_stele", 572, "tide", ("stone_brick", "minecraft:mossy_stone_bricks")),
    "tide_cairn_a": marker("tide_cairn_a", 573, "tide", ("smooth_stone", "minecraft:stone")),
    "tide_cairn_b": marker("tide_cairn_b", 574, "tide", ("smooth_stone", "minecraft:stone")),
    "tide_cargo": tide_cargo,
    "ch3_wind_cliff": ch3_wind_cliff, "ch3_gleaner_field": ch3_gleaner_field, "ch3_pack": ch3_pack,
    "ch3_ruin": marker("ch3_ruin", 581, "storm", ("cobblestone", "minecraft:mossy_cobblestone")),
    "ch3_tablet": marker("ch3_tablet", 582, "storm", ("stone_brick", "minecraft:cracked_stone_bricks")),
    "ch3_vane": marker("ch3_vane", 583, "storm", ("cobblestone", "minecraft:cobblestone")),
    # 一章の鯨の碑・四章の灰の碑 (2026-10-30。三章の風の碑と同じ形。台の種は data の ch1_tablet / ch4_tablet)
    "ch1_tablet": marker("ch1_tablet", 567, "whale", ("stone_brick", "minecraft:mossy_stone_bricks")),
    "ch4_tablet": marker("ch4_tablet", 595, "ember", ("polished_blackstone_brick", "minecraft:polished_blackstone_bricks")),
    "ch3_wind_flag": marker("ch3_wind_flag", 584, "storm", ("stone_brick", "minecraft:stone_bricks")),
    "ch4_ash_watch": ch4_ash_watch, "ch4_soot_kiln": ch4_soot_kiln, "ch4_warm_rocks": ch4_warm_rocks,
    "ch4_ash_mark": marker("ch4_ash_mark", 591, "ember", ("polished_blackstone", "minecraft:blackstone")),
    "ember_ash_post": marker("ember_ash_post", 592, "ember", ("blackstone", "minecraft:blackstone")),
    "ember_hearth_ruin": ember_small("ember_hearth_ruin", 593), "ember_warm_ledge": ember_small("ember_warm_ledge", 594),
}
for _n in range(1, 6):
    BUILDERS[f"ch1_song_stone_{_n}"] = ch1_song_stone(_n)


def main(names):
    for name, fn in BUILDERS.items():
        if names and name not in names:
            continue
        bp = fn()
        bp.note = "章の小要素 (2026-10-28 の作り直し)。tools/gen/world/build_chsites.py が書く。" + (bp.old.get("note") or "")[:60]
        print(name, bp.save())


if __name__ == "__main__":
    main(sys.argv[1:])
