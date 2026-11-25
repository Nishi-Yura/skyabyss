# -*- coding: utf-8 -*-
"""前もって組む土地の大きな形: 岩山 (張り出し・えぐれ) と mod の木 (ラフ 3 案。バニラの形にしない)。

決まり (memory): 継ぎ目に球を足さない・同じ形を並べない・葉は消えない (persistent=true)。
"""
from __future__ import annotations

import math
import random

import numpy as np

from shapes import LEAF, _hash2, strata, value_noise


def _noise3(x, y, z, scale, salt):
    """3 次元の揺らぎ (2 次元の揺らぎを 2 枚ずらして重ねる。0..1)"""
    return 0.5 * value_noise(x + y * 0.37, z - y * 0.21, scale, salt) + 0.5 * value_noise(x - y * 0.29, z + y * 0.43, scale, salt + 7)


# 岩山の石: くすんだ灰の水平な層 (白い方解石は細い筋だけ)
CRAG_BANDS = ["minecraft:stone", "minecraft:andesite", "minecraft:stone", "minecraft:tuff", "minecraft:andesite",
              "minecraft:stone", "minecraft:cobblestone", "minecraft:andesite", "minecraft:tuff", "minecraft:stone"]


def _crag_stone(x, y, z, salt, band_h):
    band = (y // band_h + salt) % len(CRAG_BANDS)
    s = CRAG_BANDS[band]
    r = _hash2(x * 3 + y, z * 5 - y, salt)
    if s == "minecraft:cobblestone":
        s = "minecraft:andesite"
    if r < 0.05:
        return "minecraft:mossy_cobblestone"
    if r < 0.08 and (y // band_h) % 7 == 3:
        return "minecraft:calcite"
    return s


def _top_of(v, x, z, y_hi):
    for y in range(y_hi, y_hi - 80, -1):
        st = v.get(x, y, z)
        if not st.endswith(":air") and "grass" not in st.split("[")[0].replace("grass_block", "") and "fern" not in st:
            return y
    return y_hi - 80


def massif(v, cx, base, cz, rnd: random.Random, radius=24.0, height=50.0, peaks=3, steep=False, hold=None):
    """岩山 (2026-11-25 の 3 回目): 盛り上がった裾野から、切り立った壁の岩の柱の群れが立つ (岩塔・トア)。
    - 裾野: その場の地面から、真ん中へなだらかに盛る (縁に段を作らない)。岩のそばは崩れ石
    - 柱: 壁はほぼ垂直、頂は平らに近く、高さは柱ごとにばらつく。縦の割れ目 (節理) で柱に分かれる
    - 棚: 壁の途中にところどころ 1〜2 張り出す岩の縁 (上は草)
    - 水平な層 (くすんだ灰。白い筋は少し)。上を向いた面は草と苔、棚に小さな唐檜
    返り値: 置いた列の上面 {(x, z): y}"""
    salt = rnd.randint(0, 999)
    band_h = rnd.choice((3, 4))
    foot_h = height * 0.38
    R2 = radius * 2.4
    # ---- 裾野 (その場の地面 → 真ん中の高さへ)
    peak_foot = base + foot_h
    for dx in range(-int(R2), int(R2) + 1):
        for dz in range(-int(R2), int(R2) + 1):
            x, z = cx + dx, cz + dz
            # 輪郭と斜面をゆがめる (同心円の段にしない)
            rmod = 0.7 + 0.6 * float(value_noise(np.array(math.atan2(dz, dx) * 6.0 + 50.0), np.array(float(salt)), 1.3, salt + 3))
            d = math.hypot(dx, dz) / (R2 * rmod)
            if d >= 1:
                continue
            top = _top_of(v, x, z, int(peak_foot) + 14)
            t = (1 - d) ** 1.4
            t = t * t * (3 - 2 * t)
            if hold is not None:  # 道・守る所のそばでは裾野を盛らない (hold(x, z) = 0..1)
                t *= hold(x, z)
            wob = 0.7 + 0.6 * float(value_noise(np.array(float(x)), np.array(float(z)), 13.0, salt))
            bump = 2.0 * (float(value_noise(np.array(float(x)), np.array(float(z)), 5.0, salt + 4)) - 0.5) * t
            target = int(round(top + (peak_foot - top) * t * wob + bump))
            if target <= top:
                continue
            for y in range(top + 1, target + 1):
                v.set(x, y, z, "minecraft:dirt")
            r = _hash2(x, z, salt + 5)
            if d < radius * 1.2 / R2 and r < 0.6:
                v.set(x, target, z, ["minecraft:gravel", "minecraft:andesite", "minecraft:stone", "minecraft:mossy_cobblestone"][int(r * 7) % 4])
            else:
                v.set(x, target, z, "minecraft:grass_block[snowy=false]" if r > 0.12 else "minecraft:coarse_dirt")
    rock_base = int(base + foot_h * 0.6)
    # ---- 柱の高さの図
    pk = [(cx, cz, (height - foot_h * 0.6) * 0.85, radius * 0.9, rnd.uniform(0, math.pi), rnd.uniform(0.55, 0.85))]
    for _ in range(peaks - 1):
        a = rnd.uniform(0, math.tau)
        dd = rnd.uniform(0.4, 0.7) * radius
        pk.append((cx + math.cos(a) * dd, cz + math.sin(a) * dd, (height - foot_h * 0.6) * rnd.uniform(0.4, 0.7),
                   radius * rnd.uniform(0.45, 0.65), rnd.uniform(0, math.pi), rnd.uniform(0.55, 0.9)))
    R = int(radius * 1.5) + 3
    gx, gz = np.meshgrid(np.arange(cx - R, cx + R + 1), np.arange(cz - R, cz + R + 1))
    gxf, gzf = gx.astype(float), gz.astype(float)
    wx = gxf + 4.0 * (value_noise(gxf, gzf, 8.0, salt + 51) - 0.5)
    wz = gzf + 4.0 * (value_noise(gxf, gzf, 8.0, salt + 53) - 0.5)
    hc = np.zeros(gx.shape)
    for (px, pz, ph, pr, ang, asp) in pk:
        dx, dz = wx - px, wz - pz
        u = dx * math.cos(ang) + dz * math.sin(ang)
        w = (-dx * math.sin(ang) + dz * math.cos(ang)) / asp
        d = np.hypot(u, w) / pr
        # 斜面: 根もとはなだらか、上ほど少し急 (切り立った壁にしない。11/25 の声「急すぎる」)
        # 滝の岩だけは切り立った壁 (steep)
        prof = np.where(d < 1, ph * (np.clip(1 - d ** 5, 0, 1) ** 0.35 if steep else np.clip(1 - d ** 1.8, 0, 1) ** 1.1), 0)
        hc = np.maximum(hc, prof)
    # 柱ごとの高さのばらつき (割れ目で分かれた柱)、頂のぎざぎざ
    cell = value_noise(np.floor(gxf / 4) * 4, np.floor(gzf / 4) * 4, 1.0, salt + 77)
    hc = np.where(hc > 0, hc * (0.72 + 0.28 * cell) + 2.5 * (value_noise(gxf, gzf, 4.0, salt + 9) - 0.5), 0)
    crack = value_noise(gxf, gzf, 3.0, salt + 11)
    hc = np.where((crack > 0.8) & (hc > 6), hc * 0.55, hc)
    hc = np.round(hc).astype(int)
    tops = {}
    for (iz, ix) in np.argwhere(hc > 0):
        x, z = int(gx[iz, ix]), int(gz[iz, ix])
        y0 = _top_of(v, x, z, rock_base + 6) - 2
        y1 = rock_base + int(hc[iz, ix])
        for y in range(y0, y1 + 1):
            v.set(x, y, z, _crag_stone(x, y, z, salt, band_h))
        r = _hash2(x, z, salt + y1)
        v.set(x, y1, z, "minecraft:grass_block[snowy=false]" if r < 0.55 else ("minecraft:moss_block" if r < 0.75 else _crag_stone(x, y1, z, salt, band_h)))
        if r < 0.02 and hc[iz, ix] > 8:
            for k in range(1, 4):
                v.set(x, y1 + k, z, "minecraft:spruce_log[axis=y]")
            for k, rr in ((2, 1), (3, 1), (4, 0)):
                for ddx in range(-rr, rr + 1):
                    for ddz in range(-rr, rr + 1):
                        if (ddx or ddz or k == 4) and v.get(x + ddx, y1 + k, z + ddz).endswith(":air"):
                            v.set(x + ddx, y1 + k, z + ddz, LEAF.format("spruce"))
        tops[(x, z)] = y1
    # ---- 棚: 壁のすぐ外に、途中の高さで 1〜2 張り出す縁
    tall = hc > 14
    ring = np.zeros_like(tall)
    for sh in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        ring |= np.roll(tall, sh, axis=(0, 1))
    ring &= ~tall
    for lv in (0.35, 0.6):
        for (iz, ix) in np.argwhere(ring):
            x, z = int(gx[iz, ix]), int(gz[iz, ix])
            if value_noise(np.array(float(x)), np.array(float(z) + lv * 50), 5.0, salt + 21) < 0.6:
                continue
            nb = max(hc[(iz + a) % hc.shape[0], (ix + b2) % hc.shape[1]] for a, b2 in ((1, 0), (-1, 0), (0, 1), (0, -1)))
            ly = rock_base + int(nb * lv)
            if ly <= rock_base + hc[iz, ix] + 2:
                continue
            v.set(x, ly, z, _crag_stone(x, ly, z, salt, band_h))
            v.set(x, ly + 1, z, "minecraft:grass_block[snowy=false]" if _hash2(x, z, salt + 31) > 0.3 else "minecraft:moss_block")
            if _hash2(x, z, salt + 32) < 0.4:
                v.set(x, ly + 2, z, "minecraft:fern")
    return tops


def big_arch(v, ax, ay, az, bx, by, bz, rise, rnd: random.Random, thick=7.0):
    """二つの足を結ぶ大きな岩の門。足は広く地面に埋まり、上は揺らぎで欠けた岩の帯。上は草と苔"""
    salt = rnd.randint(0, 999)
    L = math.hypot(bx - ax, bz - az)
    n = int(L * 2) + 1
    for i in range(n + 1):
        t = i / n
        cx = ax + (bx - ax) * t
        cz = az + (bz - az) * t
        base = ay + (by - ay) * t
        cy = base + rise * math.sin(math.pi * t)
        w = thick * (1.0 + 1.6 * (abs(t - 0.5) * 2) ** 3)
        h = thick * 0.8
        foot = t < 0.14 or t > 0.86
        ir = int(math.ceil(w)) + 1
        for dx in range(-ir, ir + 1):
            for dz in range(-ir, ir + 1):
                wob = 0.85 + 0.3 * _hash2(int(cx) + dx, int(cz) + dz, salt)
                if dx * dx + dz * dz > (w * wob) ** 2:
                    continue
                px, pz = int(round(cx + dx)), int(round(cz + dz))
                lo = int(base) - 3 if foot else int(cy - h * wob)
                for py in range(lo, int(cy + h * wob) + 1):
                    v.set(px, py, pz, strata(px, py, pz, salt, 3))
                top = int(cy + h * wob)
                r = _hash2(px, pz, salt + 3)
                if r > 0.35 and v.get(px, top + 1, pz).endswith(":air"):
                    v.set(px, top, pz, "minecraft:grass_block[snowy=false]" if r > 0.6 else "minecraft:moss_block")


# ---------------------------------------------------------------- mod の木
def _wood(v, x, y, z, state):
    v.set(int(round(x)), int(round(y)), int(round(z)), state)


def _blob(v, cx, cy, cz, r, states, salt, flat=1.0):
    ir = int(math.ceil(r)) + 1
    for dy in range(-ir, ir + 1):
        for dx in range(-ir, ir + 1):
            for dz in range(-ir, ir + 1):
                d = (dx * dx + dz * dz) / (r * r) + (dy * dy) / ((r * flat) ** 2)
                if d <= 1.0 and _hash2(cx + dx + dy * 7, cz + dz, salt) > 0.08 + 0.4 * max(0.0, d - 0.75):
                    px, py, pz = int(cx + dx), int(cy + dy), int(cz + dz)
                    cur = v.get(px, py, pz)
                    if cur.endswith(":air") or ("grass" in cur and "block" not in cur) or "fern" in cur:
                        v.set(px, py, pz, states[int(_hash2(px, pz, py + salt) * len(states))])


def _hang(v, x, y, z, n):
    """葉の下に垂れる光る実のつる (洞窟のつる)。上が葉でないなら垂らさない"""
    if v.get(x, y + 1, z).endswith(":air"):
        return
    for k in range(n):
        if not v.get(x, y - k, z).endswith(":air"):
            return
        tip = k == n - 1
        v.set(x, y - k, z, "minecraft:cave_vines[age=25,berries=true]" if tip else "minecraft:cave_vines_plant[berries=false]")


def whalebone_tree(v, x, y, z, rnd: random.Random, h=None):
    """A 鯨骨の巨木: 白い幹がねじれて立ち、肋骨のように反る枝。枝先に濃い葉の房、下に光る実のつる"""
    h = h or rnd.randint(22, 30)
    salt = rnd.randint(0, 999)
    bark = "minecraft:stripped_birch_wood[axis=y]"
    phase = rnd.uniform(0, math.tau)
    for t in range(-2, h):
        cx = x + 1.6 * math.sin(t * 0.18 + phase)
        cz = z + 1.2 * math.cos(t * 0.15 + phase)
        rr = 2.2 if t < h * 0.3 else (1.7 if t < h * 0.7 else 1.2)
        for dx in range(-3, 4):
            for dz in range(-3, 4):
                if dx * dx + dz * dz <= rr * rr:
                    _wood(v, cx + dx, y + t, cz + dz, bark)
    for k in range(6):
        a = k * math.tau / 6 + rnd.uniform(-0.3, 0.3)
        for i in range(1, rnd.randint(4, 7)):
            _wood(v, x + math.cos(a) * (i + 1.5), y + 1 - i * 0.45, z + math.sin(a) * (i + 1.5), bark)
    n = rnd.randint(6, 8)
    for k in range(n):
        a = k * math.tau / n + rnd.uniform(-0.25, 0.25)
        y0 = y + int(h * rnd.uniform(0.45, 0.85))
        L = rnd.uniform(8, 13)
        tip = (x, y0, z)
        for i in range(int(L * 2)):
            t = i / (L * 2)
            r = L * math.sin(t * math.pi * 0.62)
            yy = y0 + L * 0.72 * (1 - math.cos(t * math.pi * 0.62))
            tip = (x + math.cos(a) * r, yy, z + math.sin(a) * r)
            _wood(v, *tip, bark)
        cx_, cy_, cz_ = int(tip[0]), int(tip[1]), int(tip[2])
        _blob(v, cx_, cy_ + 1, cz_, rnd.uniform(3.2, 4.4), [LEAF.format("dark_oak"), LEAF.format("spruce"), LEAF.format("dark_oak")],
              salt + k, 0.6)
        for q in range(6):
            _hang(v, cx_ + rnd.randint(-3, 3), cy_ - 1, cz_ + rnd.randint(-3, 3), rnd.randint(2, 6))
    _blob(v, x, y + h + 1, z, 4.5, [LEAF.format("dark_oak"), LEAF.format("spruce")], salt + 99, 0.7)


def moss_great_tree(v, x, y, z, rnd: random.Random, h=None):
    """B 苔衣の大樹: 太い暗い幹、根が地面へアーチを作る。平たい大きな葉の段 3〜4、下に胞子の花"""
    h = h or rnd.randint(16, 22)
    salt = rnd.randint(0, 999)
    bark = "minecraft:dark_oak_wood[axis=y]"
    for t in range(-2, h):
        rr = 3.2 - t / h * 1.4 + (1.6 if t < 3 else 0)
        for dx in range(-5, 6):
            for dz in range(-5, 6):
                if dx * dx + dz * dz <= rr * rr:
                    _wood(v, x + dx, y + t, z + dz, bark if _hash2(x + dx, z + dz, t) > 0.15 else "minecraft:moss_block")
    for k in range(rnd.randint(5, 7)):
        a = k * math.tau / 6 + rnd.uniform(-0.3, 0.3)
        L = rnd.uniform(7, 11)
        for i in range(int(L * 2)):
            t = i / (L * 2)
            r = 2.5 + L * t
            yy = y + 4.5 * math.sin(math.pi * (0.35 + 0.65 * t)) - 1
            for w in (-0.5, 0.5):
                _wood(v, x + math.cos(a) * r - math.sin(a) * w, yy, z + math.sin(a) * r + math.cos(a) * w, bark)
    tiers = rnd.randint(3, 4)
    for k in range(tiers):
        ty = y + int(h * (0.55 + 0.45 * k / max(1, tiers - 1)))
        for j in range(rnd.randint(2, 3)):
            a = rnd.uniform(0, math.tau)
            L = rnd.uniform(5, 9) * (1 - 0.25 * k / tiers)
            for i in range(int(L) + 1):
                _wood(v, x + math.cos(a) * i, ty - 2 + i * 0.35, z + math.sin(a) * i, "minecraft:dark_oak_wood[axis=x]")
            ex, ez = x + math.cos(a) * L, z + math.sin(a) * L
            r = rnd.uniform(6.5, 9.5) * (1 - 0.2 * k / tiers)
            _blob(v, int(ex), ty + 1, int(ez), r, [LEAF.format("azalea"), LEAF.format("flowering_azalea"), LEAF.format("azalea")],
                  salt + k * 10 + j, 0.32)
            for q in range(8):
                bx, bz = int(ex + rnd.uniform(-r * 0.7, r * 0.7)), int(ez + rnd.uniform(-r * 0.7, r * 0.7))
                for yy in range(ty + 1, ty - 4, -1):
                    if v.get(bx, yy, bz).endswith(":air") and "leaves" in v.get(bx, yy + 1, bz):
                        v.set(bx, yy, bz, "minecraft:spore_blossom")
                        break
    _blob(v, x, y + h + 2, z, 6.0, [LEAF.format("azalea"), LEAF.format("flowering_azalea")], salt + 7, 0.45)


def teal_lantern_tree(v, x, y, z, rnd: random.Random, h=None):
    """C 灯り葉の樹: 暗い幹が三〜四股に分かれて弓なりに張り、枝ごとに青緑の房 (歪んだ菌の塊と唐檜の葉) が
    垂れ下がる。房の芯に光る菌の灯り、下に光る実のつる"""
    h = h or rnd.randint(18, 24)
    salt = rnd.randint(0, 999)
    bark = "minecraft:spruce_wood[axis=y]"
    split = int(h * 0.4)
    for t in range(-2, split + 2):
        rr = 2.0 if t < 3 else 1.5
        for dx in range(-2, 3):
            for dz in range(-2, 3):
                if dx * dx + dz * dz <= rr * rr:
                    _wood(v, x + dx, y + t, z + dz, bark)
    n = rnd.randint(4, 5)
    for k in range(n):
        a = k * math.tau / n + rnd.uniform(-0.3, 0.3)
        reach = rnd.uniform(9, 13)
        rise = rnd.uniform(8, 13)
        steps = int(reach * 2.2)
        for i in range(steps + 1):
            t = i / steps
            r = reach * t
            yy = y + split + rise * math.sin(t * math.pi * 0.75)
            px, pz = x + math.cos(a) * r, z + math.sin(a) * r
            _wood(v, px, yy, pz, bark)
            if t > 0.3 and i % 5 == 0 or i == steps:
                cy = int(yy) + 1
                rr = rnd.uniform(3.2, 4.6) * (0.7 + 0.5 * t)
                _blob(v, int(px), cy, int(pz), rr, ["minecraft:warped_wart_block", LEAF.format("spruce"), LEAF.format("spruce"), "minecraft:warped_wart_block"],
                      salt + i + k * 50, 0.55)
                v.set(int(px), cy - 1, int(pz), "minecraft:shroomlight")
                # 房の縁から垂れる (柳のように)
                for q in range(int(rr * 3)):
                    qa = rnd.uniform(0, math.tau)
                    hx, hz = int(px + math.cos(qa) * rr * 0.85), int(pz + math.sin(qa) * rr * 0.85)
                    top = cy
                    while top > cy - 4 and v.get(hx, top, hz).endswith(":air"):
                        top -= 1
                    if not v.get(hx, top, hz).endswith(":air"):
                        _hang(v, hx, top - 1, hz, rnd.randint(3, 7))
    _blob(v, x, y + split + int(h * 0.55), z, 4.5, ["minecraft:warped_wart_block", LEAF.format("spruce")], salt + 7, 0.6)


# ---------------------------------------------------------------- 森のふつうの木 (2026-11-25。バニラの形にしない。C と同じ「弓なりの枝・垂れる房」の言葉で、色は森の緑)
def weeping_fir(v, x, y, z, rnd: random.Random, h=None, big=False):
    """垂れ枝の樅: まっすぐな幹、段ごとに下へ垂れる枝の房 (濃い針葉)、先は細い槍。大きいものは幹が 2x2、根が張る。
    房の芯にまれに青緑 (灯り葉の樹の親戚)"""
    h = h or (rnd.randint(26, 34) if big else rnd.randint(13, 20))
    salt = rnd.randint(0, 999)
    bark = "minecraft:spruce_log[axis=y]"
    tw = 2 if big else 1
    for t in range(-2, h):
        for dx in range(tw):
            for dz in range(tw):
                v.set(x + dx, y + t, z + dz, bark)
    if big:
        for k in range(6):
            a = k * math.tau / 6 + rnd.uniform(-0.3, 0.3)
            for i in range(1, rnd.randint(3, 6)):
                _wood(v, x + 0.5 + math.cos(a) * (i + 1), y + 1 - i * 0.5, z + 0.5 + math.sin(a) * (i + 1), "minecraft:spruce_wood[axis=y]")
    c = x + (tw - 1) * 0.5
    d = z + (tw - 1) * 0.5
    y0 = y + max(4, int(h * 0.25))
    tier = 0
    yy = y0
    while yy < y + h - 1:
        t = (yy - y0) / max(1, (y + h - y0))
        reach = (1 - t) ** 0.8 * (7.0 if big else 4.2) + 1.0
        n = 5 if reach > 4 else 4
        for k in range(n):
            a = k * math.tau / n + tier * 0.7 + rnd.uniform(-0.2, 0.2)
            # 枝: 外へ出て先が下がる
            steps = int(reach * 1.6) + 1
            for i in range(steps + 1):
                q = i / steps
                bx = c + math.cos(a) * reach * q
                bz = d + math.sin(a) * reach * q
                by = yy + 0.6 * q - 1.6 * q * q
                _wood(v, bx, by, bz, "minecraft:spruce_wood[axis=x]") if reach > 5 and q < 0.7 else None
                if i % 2 and i != steps:
                    continue
                rr = 0.9 + 0.7 * q
                _blob(v, int(round(bx)), int(round(by)), int(round(bz)), rr, [LEAF.format("spruce"), LEAF.format("spruce"),
                      LEAF.format("dark_oak")], salt + tier * 31 + k * 7 + i, 0.7)
            if rnd.random() < (0.25 if big else 0.08):
                tx, tz = int(round(c + math.cos(a) * reach * 0.8)), int(round(d + math.sin(a) * reach * 0.8))
                v.set(tx, int(yy - 0.6), tz, "minecraft:warped_wart_block")
        tier += 1
        yy += 4 if big else 3
    # 先: 幹の先を葉で包んで細い槍に
    for k in range(-2, 3 if big else 2):
        r = 1 if k < 1 else 0
        for ddx in range(-r, r + 1):
            for ddz in range(-r, r + 1):
                if abs(ddx) + abs(ddz) <= r and v.get(int(c) + ddx, y + h + k, int(d) + ddz).endswith(":air"):
                    v.set(int(c) + ddx, y + h + k, int(d) + ddz, LEAF.format("spruce"))


def pale_slender(v, x, y, z, rnd: random.Random, h=None):
    """骨白の細木: 白っぽい細い幹が少し曲がり、二〜三の短い枝に平たい葉の房。房の端から短いつるが垂れる"""
    h = h or rnd.randint(9, 14)
    salt = rnd.randint(0, 999)
    bark = "minecraft:birch_wood[axis=y]"
    lean = (rnd.uniform(-0.25, 0.25), rnd.uniform(-0.25, 0.25))
    for t in range(-1, h):
        _wood(v, x + lean[0] * t * t / h, y + t, z + lean[1] * t * t / h, bark)
    tx, tz = x + lean[0] * h, z + lean[1] * h
    for k in range(rnd.randint(2, 3)):
        a = rnd.uniform(0, math.tau)
        by = y + int(h * rnd.uniform(0.55, 0.9))
        L = rnd.uniform(2.5, 4.5)
        ex, ez = x + lean[0] * (by - y) ** 2 / h + math.cos(a) * L, z + lean[1] * (by - y) ** 2 / h + math.sin(a) * L
        for i in range(int(L) + 1):
            _wood(v, ex - math.cos(a) * (L - i), by + i * 0.4, ez - math.sin(a) * (L - i), "minecraft:birch_wood[axis=x]")
        _blob(v, int(ex), int(by + L * 0.4) + 1, int(ez), rnd.uniform(2.4, 3.3), [LEAF.format("birch"), LEAF.format("dark_oak")], salt + k, 0.5)
        if rnd.random() < 0.5:
            _hang(v, int(ex) + rnd.randint(-2, 2), int(by + L * 0.4) - 1, int(ez) + rnd.randint(-2, 2), rnd.randint(2, 3))
    _blob(v, int(tx), y + h, int(tz), 2.6, [LEAF.format("birch"), LEAF.format("dark_oak")], salt + 9, 0.6)


def moss_low_tree(v, x, y, z, rnd: random.Random):
    """苔の低木: 短くねじれた太い幹、横に広い平たい苔色の傘 (つつじの葉)。草地にぽつんと立つ"""
    salt = rnd.randint(0, 999)
    h = rnd.randint(4, 6)
    bark = "minecraft:oak_wood[axis=y]"
    for t in range(-1, h):
        for dx in range(2):
            for dz in range(2):
                if t < 2 or (dx + dz) % 2 == 0:
                    _wood(v, x + dx + 0.6 * math.sin(t * 0.8 + salt), y + t, z + dz, bark)
    for k in range(rnd.randint(3, 4)):
        a = k * math.tau / 4 + rnd.uniform(-0.4, 0.4)
        L = rnd.uniform(3.5, 6)
        for i in range(int(L) + 1):
            _wood(v, x + 0.5 + math.cos(a) * i, y + h - 1 + i * 0.3, z + 0.5 + math.sin(a) * i, "minecraft:oak_wood[axis=x]")
        _blob(v, int(x + math.cos(a) * L), int(y + h + L * 0.3) + 1, int(z + math.sin(a) * L), rnd.uniform(3.5, 4.8),
              [LEAF.format("azalea"), LEAF.format("azalea"), LEAF.format("oak")], salt + k, 0.38)
    _blob(v, x, y + h + 2, z, 4.0, [LEAF.format("azalea"), LEAF.format("oak")], salt + 7, 0.4)


TREES = {"a": whalebone_tree, "b": moss_great_tree, "c": teal_lantern_tree}
