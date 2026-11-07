# -*- coding: utf-8 -*-
"""地上の巨神の祠のラフ 3 案 (2026-11-07 の声「次は祠の建築」。周回のたびに使う入口 = Wynncraft のレイドの入口にあたる所)。岩鯨を例に。
data/skyabyss/opening/rough_shrine_<a|b|c>.json (部品の形) を書き、tools/shots/titan_shrine_roughs.txt が平らな世界に置いて撮る。

  python -X utf8 tools/rough/titan_shrine/roughs.py

どの案も同じ働き: 碑 (祠の画面・歌片) / 集合台 (パーティが立つ所。今は黒い 5×3) / 共鳴の炉 / 順位の石。参道は南 (+z) から
  A 巨神の門    段の円い広場の北に、骨の帯を巻いた二本の大柱と弧の大門。門の中は雲の帳 (光るもや)。碑は門の足もと、集合台は門の前の円い壇
  B 歌の環      石の輪 12 本 (二本ずつ梁で結ぶ) の真ん中に円い集合台。上に巨神の紋の冠石が浮き、鎖で輪とつながり、光の柱が天へ
  C 巨神の遺骸  大きな骨の頭と肋の列が身廊をつくる。肋の下を歩いて奥の頭骨の口の中の碑へ。集合台は身廊の中ほど
置く高さ: 部品の y 0 = 地面の一つ上
"""
import json
import math
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
OUT = ROOT / "src/main/resources/data/skyabyss/opening"
NOTE = "tools/rough/titan_shrine/roughs.py が書くラフ。決まったら消す"

TABLET = "skyabyss:titan_shrine[facing=south,titan=whale]"
FORGE = "skyabyss:resonance_forge[facing=west]"
LANT = "minecraft:lantern[hanging=false,waterlogged=false]"
LANT_H = "minecraft:lantern[hanging=true,waterlogged=false]"
SOUL_H = "minecraft:soul_lantern[hanging=true,waterlogged=false]"
SOUL = "minecraft:soul_lantern[hanging=false,waterlogged=false]"
CHAIN = "minecraft:chain[axis=y,waterlogged=false]"
BONE = "minecraft:bone_block[axis=y]"
BONE_X = "minecraft:bone_block[axis=x]"
BONE_Z = "minecraft:bone_block[axis=z]"


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


def stairs(b, f, half="bottom"):
    return f"minecraft:{b}_stairs[facing={f},half={half},shape=straight,waterlogged=false]"


def slab(b, t="bottom"):
    return f"minecraft:{b}_slab[type={t},waterlogged=false]"


FLOOR = [(5, "minecraft:polished_deepslate"), (4, "minecraft:deepslate_tiles"), (3, "minecraft:deepslate_bricks"), (1, "minecraft:cracked_deepslate_tiles")]
STONE = [(4, "minecraft:deepslate_bricks"), (2, "minecraft:polished_deepslate"), (1, "minecraft:cracked_deepslate_bricks")]


class V:
    def __init__(self):
        self.c = {}

    def put(self, x, y, z, b):
        self.c[(int(x), int(y), int(z))] = b

    def disc(self, cx, y, cz, r, b, r0=-1.0):
        for x in range(int(cx - r - 1), int(cx + r + 2)):
            for z in range(int(cz - r - 1), int(cz + r + 2)):
                d = math.hypot(x - cx, z - cz)
                if r0 < d <= r + 0.35:
                    self.put(x, y, z, b(x, y, z) if callable(b) else b)

    def column(self, x, z, y0, y1, b):
        for y in range(y0, y1 + 1):
            self.put(x, y, z, b(x, y, z) if callable(b) else b)

    def write(self, name):
        for (x, y, z), b in list(self.c.items()):
            base = b.split("[")[0]
            if base.endswith("_wall"):
                sides = {}
                for d, (dx, dz) in {"north": (0, -1), "south": (0, 1), "east": (1, 0), "west": (-1, 0)}.items():
                    nb = self.c.get((x + dx, y, z + dz))
                    sides[d] = nb is not None and nb != "minecraft:air" and not any(k in nb for k in ("lantern", "slab", "stairs", "chain", "banner", "candle", "rod"))
                st = ",".join(f"{d}={'low' if sides[d] else 'none'}" for d in ("east", "north", "south", "west"))
                self.c[(x, y, z)] = f"{base}[{st},up=true,waterlogged=false]"
        pal, idx, blocks = [], {}, []
        xs = [k[0] for k in self.c]
        zs = [k[2] for k in self.c]
        for (x, y, z), b in sorted(self.c.items(), key=lambda kv: (kv[0][1], kv[0][0], kv[0][2])):
            if b not in idx:
                idx[b] = len(pal)
                pal.append(b)
            blocks.append([x, y, z, idx[b]])
        data = {"note": NOTE, "palette": pal, "blocks": blocks, "marks": {}, "meta": {"rect": [min(xs), min(zs), max(xs), max(zs)]}}
        (OUT / f"{name}.json").write_text(json.dumps(data, separators=(",", ":")), encoding="utf8")
        return len(blocks)


def plaza(v, R, top=2):
    """段の円い広場 (縁は板の段)。床は y = top"""
    for y in range(0, top + 1):
        r = R + (top - y)
        v.disc(0, y, 0, r, lambda x, y, z: pick(STONE, x, y, z, 1))
    for y in range(0, top + 1):
        r = R + (top - y) + 1
        v.disc(0, y, 0, r, slab("deepslate_tile"), r0=r - 0.65)
    for x in range(-R, R + 1):
        for z in range(-R, R + 1):
            d = math.hypot(x, z)
            if d <= R + 0.35:
                b = pick(FLOOR, x // 2, top, z // 2, 3)
                if abs(d - (R - 1.5)) < 0.55:
                    b = "minecraft:chiseled_deepslate"
                v.put(x, top, z, b)


def path(v, x_half, z0, z1, top=2):
    """南の参道 (広場の縁から段で地面へ下りる)。両脇に灯りの柱"""
    for z in range(z0, z1 + 1):
        y = max(0, top - max(0, (z - z0) // 2))
        for x in range(-x_half, x_half + 1):
            for yy in range(0, y + 1):
                v.put(x, yy, z, "minecraft:polished_deepslate" if abs(x) == x_half else pick(FLOOR, x, yy, z, 5))
        if (z - z0) % 5 == 2:
            for sx in (-x_half - 1, x_half + 1):
                v.column(sx, z, 0, y + 2, "minecraft:deepslate_brick_wall")
                v.put(sx, y + 3, z, LANT)


def pad(v, cx, cz, y, ring_glow="minecraft:sea_lantern"):
    """集合台 (円い黒の壇。縁は光る輪)。ラフでは半径 3"""
    v.disc(cx, y, cz, 3.2, "minecraft:black_concrete")
    v.disc(cx, y, cz, 3.2, "minecraft:polished_blackstone", r0=2.5)
    for k in range(8):
        a = math.radians(k * 45)
        v.put(round(cx + math.cos(a) * 3), y, round(cz + math.sin(a) * 3), ring_glow)


def extras(v, y, forge_at, stones):
    v.put(forge_at[0], y + 1, forge_at[1], FORGE)
    for (x, z) in stones:
        v.column(x, z, y + 1, y + 2, "minecraft:polished_deepslate")
        v.put(x, y + 3, z, slab("polished_deepslate"))


# ====================================================================== A 巨神の門
def rough_a():
    v = V()
    R = 14
    plaza(v, R)
    path(v, 3, R + 1, R + 9)
    # 北の大門: 二本の大柱 (5×5、高さ 22。骨の帯と角の控え壁)
    gz = -9
    for sx in (-1, 1):
        cx = sx * 8
        for y in range(3, 25):
            for x in range(cx - 2, cx + 3):
                for z in range(gz - 2, gz + 3):
                    edge = abs(x - cx) == 2 or abs(z - gz) == 2
                    if not edge and y < 23:
                        v.put(x, y, z, "minecraft:deepslate")
                        continue
                    if y in (8, 9, 16, 17):
                        b = BONE
                    elif y in (3, 4):
                        b = "minecraft:polished_deepslate"
                    else:
                        b = pick(STONE, x, y, z, 7)
                    v.put(x, y, z, b)
            # 柱の頭 (段の冠と、頂の骨の角)
        for y in range(25, 28):
            r = 3 - (y - 25)
            for x in range(cx - r, cx + r + 1):
                for z in range(gz - r, gz + r + 1):
                    v.put(x, y, z, "minecraft:chiseled_deepslate" if y == 25 else "minecraft:polished_deepslate")
        v.column(cx, gz, 28, 31, BONE)
        v.put(cx, 32, gz, "minecraft:sea_lantern")
        # 控え壁 (柱の外側へ下りる)
        for k in range(6):
            for z in range(gz - 1, gz + 2):
                v.put(cx + sx * (3 + k), 3 + (5 - k) * 2, z, stairs("deepslate_brick", "east" if sx < 0 else "west"))
                for y in range(3, 3 + (5 - k) * 2):
                    v.put(cx + sx * (3 + k), y, z, pick(STONE, cx, y, z, 8))
    # 弧の梁 (柱どうしを結ぶ、厚さ 3)
    for x in range(-6, 7):
        top = 19 + round(3 * math.cos(x / 6.0 * math.pi / 2))
        for y in range(top, 25):
            for z in range(gz - 1, gz + 2):
                v.put(x, y, z, BONE_X if y == top else pick(STONE, x, y, z, 9))
    for x in range(-5, 6, 2):
        v.put(x, 18 + round(3 * math.cos(x / 6.0 * math.pi / 2)), gz + 2, SOUL_H)
    # 門の中の雲の帳 (白と水色のガラス、ところどころ光る)
    for x in range(-5, 6):
        top = 19 + round(3 * math.cos(x / 6.0 * math.pi / 2))
        for y in range(3, top):
            b = "minecraft:white_stained_glass" if h(x, y, 0) < 0.55 else "minecraft:light_blue_stained_glass"
            if h(x, y, 1) < 0.08:
                b = "minecraft:sea_lantern"
            v.put(x, y, gz, b)
    # 碑と、門の足もとの壇
    for x in range(-4, 5):
        for z in range(gz + 2, gz + 5):
            v.put(x, 3, z, "minecraft:polished_deepslate" if abs(x) < 4 else stairs("polished_deepslate", "south"))
    v.put(0, 4, gz + 3, TABLET)
    for sx in (-3, 3):
        v.column(sx, gz + 3, 4, 5, "minecraft:deepslate_brick_wall")
        v.put(sx, 6, gz + 3, SOUL)
    # 集合台 (広場の真ん中)
    pad(v, 0, 1, 2)
    # 広場の縁の骨の弧 (左右に二つずつ。肋の形の半円で、足は広場の縁)
    for sx in (-1, 1):
        for zc in (-1, 7):
            x = sx * (R - 2)
            for i in range(0, 61):
                a = i / 60 * math.pi
                z = round(zc + math.cos(a) * 3.5)
                y = 3 + round(math.sin(a) * 6)
                v.put(x, y, z, BONE)
                v.put(x - sx, y, z, BONE)
            v.put(x - sx, 8, zc, SOUL_H)
    extras(v, 2, (R - 3, -3), [(-9, 6), (-10, 2), (9, 6)])
    return v


# ====================================================================== B 歌の環
def rough_b():
    v = V()
    R = 14
    plaza(v, R)
    path(v, 3, R + 1, R + 9)
    pad(v, 0, 0, 2)
    # 内の円い段 (集合台を一段上げる)
    v.disc(0, 3, 0, 4.4, slab("polished_deepslate"), r0=3.5)
    # 石の輪 12 本 (高さ 9〜12。内へわずかに傾く。二本ずつ梁で結ぶ = 6 組の三石塔)
    RR = 10
    tops = []
    for k in range(12):
        a = math.radians(k * 30 + 15)
        ht = 9 + (k % 3)
        pts = []
        for y in range(3, 3 + ht):
            lean = (y - 3) * 0.06
            x = round(math.cos(a) * (RR - lean))
            z = round(math.sin(a) * (RR - lean))
            for dx in (0, 1):
                for dz in (0, 1):
                    b = BONE if (y - 3) % 4 == 2 else pick(STONE, x + dx, y, z + dz, 11 + k)
                    if k % 3 == 0 and y == 3 + ht - 2:
                        b = "minecraft:sea_lantern"
                    v.put(x + dx, y, z + dz, b)
            pts.append((x, y, z))
        tops.append(pts[-1])
    for k in range(0, 12, 2):
        (x0, y0, z0), (x1, y1, z1) = tops[k], tops[k + 1]
        y = max(y0, y1) + 1
        n = max(abs(x1 - x0), abs(z1 - z0)) + 2
        for i in range(n + 1):
            t = i / n
            x = round(x0 + (x1 - x0) * t)
            z = round(z0 + (z1 - z0) * t)
            for dx in (0, 1):
                for dz in (0, 1):
                    v.put(x + dx, y, z + dz, "minecraft:chiseled_deepslate" if i in (0, n) else "minecraft:polished_deepslate")
    # 浮かぶ冠石 (巨神の紋: 鯨の背の弧。骨と深層岩) と、輪へ下りる鎖
    cy = 19
    for x in range(-5, 6):
        for z in range(-5, 6):
            d = math.hypot(x, z)
            if d <= 5.3:
                v.put(x, cy, z, "minecraft:polished_deepslate" if d > 4.3 else "minecraft:chiseled_deepslate")
            if d <= 3.6:
                v.put(x, cy + 1, z, BONE if abs(x) <= 1 else "minecraft:deepslate_tiles")
            if 4.3 < d <= 5.3:
                v.put(x, cy - 1, z, slab("deepslate_tile", "top"))
    for x in range(-2, 3):
        v.put(x, cy + 2, 0, BONE_X)
    v.column(0, 0, cy + 2, cy + 4, BONE)
    v.put(0, cy + 5, 0, "minecraft:sea_lantern")
    # 冠石の縁から下がる鎖と灯り (八本)
    for k in range(8):
        a = math.radians(k * 45 + 22.5)
        x, z = round(math.cos(a) * 4.6), round(math.sin(a) * 4.6)
        for y in range(cy - 5, cy - 1):
            v.put(x, y, z, CHAIN)
        v.put(x, cy - 6, z, SOUL_H)
    # 光の柱 (冠石の下から集合台へ。途切れない光の棒)
    for y in range(3, cy - 1):
        v.put(0, y, 0, "minecraft:end_rod[facing=up]")
    # 碑 (集合台の北の縁の外、輪の内)
    v.disc(0, 3, -6, 1.5, "minecraft:polished_deepslate")
    v.put(0, 4, -6, TABLET)
    extras(v, 2, (6, -6), [(-6, 6), (-7, 3), (6, 6)])
    return v


# ====================================================================== C 巨神の遺骸
def rough_c():
    v = V()
    R = 14
    plaza(v, R)
    path(v, 3, R + 1, R + 9)
    # 背骨 (北から南へ、身廊の上 13 の高さ)
    for z in range(-12, 12):
        y = 15 - round(2 * (z + 12) / 24)
        v.put(0, y, z, BONE_Z)
        if z % 3 == 0:
            v.put(0, y + 1, z, BONE)
    # 肋の列 (7 対。弧で床へ下りる。上ほど細い)
    for i, z in enumerate(range(-6, 12, 3)):
        span = 9 - abs(i - 2) * 0.4
        topy = 14 - round(2 * (z + 12) / 24)
        for sx in (-1, 1):
            for t in range(0, 26):
                a = t / 25 * math.pi / 2
                x = sx * round(math.sin(a) * span)
                y = 3 + round(math.cos(a) * (topy - 3))
                v.put(x, y, z, BONE)
                if t > 18:
                    v.put(x + sx, y, z, BONE)
    # 頭骨 (北の奥。口を南へ開けた半球の殻と、上顎・下顎)
    sz = -10
    for x in range(-9, 10):
        for y in range(3, 15):
            for z in range(-18, -5):
                dx, dy, dz = x / 9.0, (y - 3) / 11.0, (z - sz) / 7.5
                d = dx * dx + dy * dy + dz * dz
                if 0.72 < d <= 1.0 and not (z > sz and abs(x) < 6 and y < 10):
                    v.put(x, y, z, BONE if h(x, y, z) < 0.8 else "minecraft:calcite")
    for y in range(3, 15):
        for x in range(-6, 7):
            if (x, y, -6) in v.c and abs(x) < 6 and y < 10:
                del v.c[(x, y, -6)]
    # 眼窩の灯り
    for sx in (-5, 5):
        v.put(sx, 10, -7, "minecraft:soul_lantern[hanging=false,waterlogged=false]")
    # 碑 (口の中、奥の壇の上)
    for x in range(-3, 4):
        for z in range(-14, -10):
            v.put(x, 3, z, "minecraft:polished_deepslate")
    v.put(0, 4, -12, TABLET)
    for sx in (-2, 2):
        v.put(sx, 4, -13, "minecraft:candle[candles=3,lit=true,waterlogged=false]")
    # 吊り灯り (背骨から)
    for z in range(-3, 12, 4):
        y = 15 - round(2 * (z + 12) / 24)
        for k in range(1, 4):
            v.put(0, y - k, z, CHAIN)
        v.put(0, y - 4, z, SOUL_H)
    # 集合台 (身廊の中ほど)
    pad(v, 0, 4, 2)
    extras(v, 2, (11, -2), [(-10, 7), (-11, 3), (10, 7)])
    return v


if __name__ == "__main__":
    OUT.mkdir(parents=True, exist_ok=True)
    for name, fn in (("rough_shrine_a", rough_a), ("rough_shrine_b", rough_b), ("rough_shrine_c", rough_c)):
        print(name, fn().write(name), "cells")
