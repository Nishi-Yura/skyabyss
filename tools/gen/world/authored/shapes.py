# -*- coding: utf-8 -*-
"""前もって組む土地の形の部品: 揺らぎ・木・岩・草花。どれも Vol (alib) へ直接置く。

決まり (memory): 継ぎ目に球を足さない・同じ形を並べない (種と大きさを毎回変える)・葉は消えない (persistent=true)。
"""
from __future__ import annotations

import math
import random

import numpy as np


# ---------------------------------------------------------------- 揺らぎ (値のノイズ。numpy で面ごと)
def _hash2(ix, iz, salt):
    h = (ix * 374761393 + iz * 668265263 + salt * 2147483647) & 0xFFFFFFFF
    h = ((h ^ (h >> 13)) * 1274126177) & 0xFFFFFFFF
    return ((h ^ (h >> 16)) & 0xFFFF) / 65536.0


def value_noise(xs, zs, scale, salt):
    """xs, zs: 2 次元の座標の配列。0..1 のなめらかな揺らぎ"""
    fx = xs / scale
    fz = zs / scale
    ix = np.floor(fx).astype(np.int64)
    iz = np.floor(fz).astype(np.int64)
    tx = fx - ix
    tz = fz - iz
    tx = tx * tx * (3 - 2 * tx)
    tz = tz * tz * (3 - 2 * tz)
    a = _hash2(ix, iz, salt)
    b = _hash2(ix + 1, iz, salt)
    c = _hash2(ix, iz + 1, salt)
    d = _hash2(ix + 1, iz + 1, salt)
    return (a * (1 - tx) + b * tx) * (1 - tz) + (c * (1 - tx) + d * tx) * tz


def fbm(xs, zs, scale, salt, octaves=4):
    out = np.zeros_like(xs, dtype=float)
    amp = 1.0
    tot = 0.0
    for o in range(octaves):
        out += amp * value_noise(xs, zs, scale / (2 ** o), salt + o * 101)
        tot += amp
        amp *= 0.5
    return out / tot


def ridged(xs, zs, scale, salt, octaves=3):
    n = fbm(xs, zs, scale, salt, octaves)
    return 1.0 - np.abs(n * 2 - 1)


# ---------------------------------------------------------------- 石の層 (崖の面に縞)
# 鯨の土地の岩: 灰の石に、骨の白 (方解石) と凝灰岩の帯
STRATA = ["minecraft:stone", "minecraft:andesite", "minecraft:calcite", "minecraft:tuff", "minecraft:stone", "minecraft:andesite",
          "minecraft:tuff", "minecraft:calcite", "minecraft:stone", "minecraft:diorite", "minecraft:andesite"]


def strata(x, y, z, salt=0, band_h=2):
    """崖の石。高さで縞、ところどころ横にずれる"""
    wob = int(2.5 * math.sin(x * 0.07 + salt) + 2.0 * math.cos(z * 0.09 - salt))
    band = ((y + wob) // band_h) % len(STRATA)
    s = STRATA[band]
    r = _hash2(x * 7 + y, z * 13 - y, 77 + salt)
    if r < 0.08:
        return "minecraft:mossy_cobblestone"
    if r < 0.14:
        return "minecraft:gravel" if y % 5 == 0 else "minecraft:mossy_cobblestone"
    return s


# ---------------------------------------------------------------- 木 (土地の木: 唐檜の大木・唐檜・樺・低い楢)
LEAF = "minecraft:{}_leaves[distance=1,persistent=true,waterlogged=false]"
LOG = "minecraft:{}_log[axis={}]"


def _leaf(v, x, y, z, kind):
    cur = v.get(x, y, z)
    if cur.endswith(":air") or "short_grass" in cur or "fern" in cur:
        v.set(x, y, z, LEAF.format(kind))


def _log(v, x, y, z, kind, axis="y"):
    v.set(x, y, z, LOG.format(kind, axis))


def giant_spruce(v, x, y, z, rnd: random.Random, h=None):
    """2x2 の幹、根の張り出し、段になった枝の層、先は細く。高さ 24〜34"""
    h = h or rnd.randint(24, 34)
    for dy in range(-2, h):
        for dx in (0, 1):
            for dz in (0, 1):
                _log(v, x + dx, y + dy, z + dz, "spruce")
    # 根: 四方へ斜めに 2〜4
    for ang in range(0, 360, 45):
        if rnd.random() < 0.35:
            continue
        L = rnd.randint(2, 4)
        a = math.radians(ang + rnd.randint(-15, 15))
        for i in range(1, L + 1):
            px = x + 0.5 + math.cos(a) * (i + 0.7)
            pz = z + 0.5 + math.sin(a) * (i + 0.7)
            ax = "x" if abs(math.cos(a)) > abs(math.sin(a)) else "z"
            _log(v, int(math.floor(px)), y + (1 if i == 1 else 0) - 1, int(math.floor(pz)), "spruce", ax)
            if i == 1:
                _log(v, int(math.floor(px)), y, int(math.floor(pz)), "spruce", "y")
    # 枝の層: 下は広く、上へ細く。層ごとに少し回す。層は 2 段で、間は 1 だけ空ける (幹が見えすぎない)
    y0 = y + max(5, h // 5)
    layer = 0
    yy = y0
    top = y + h
    while yy <= top:
        t = (yy - y0) / max(1, (top - y0))
        r = (1 - t) ** 0.85 * rnd.uniform(6.5, 7.5) + 1.6
        for dy in (0, 1):
            rr = r - dy * 1.1
            ir = int(math.ceil(rr))
            for dx in range(-ir, ir + 2):
                for dz in range(-ir, ir + 2):
                    cx, cz = dx - 0.5, dz - 0.5
                    d = math.hypot(cx, cz)
                    ang = math.atan2(cz, cx)
                    edge = rr * (0.8 + 0.2 * math.cos(ang * 5 + layer * 1.7))
                    if d <= edge and _hash2(x + dx, z + dz, yy + dy) > 0.06:
                        _leaf(v, x + dx, yy + dy, z + dz, "spruce")
        layer += 1
        yy += 3
    for dy in range(h, h + 4):
        for dx in (0, 1):
            for dz in (0, 1):
                if dy < h + 2 or (dx == 0 and dz == 0):
                    _leaf(v, x + dx, y + dy, z + dz, "spruce")


def spruce(v, x, y, z, rnd: random.Random):
    h = rnd.randint(9, 16)
    for dy in range(h):
        _log(v, x, y + dy, z, "spruce")
    yy = y + rnd.randint(3, 4)
    k = 0
    while yy <= y + h + 1:
        t = (yy - y) / (h + 1)
        r = (1 - t) * rnd.uniform(3.2, 4.0) + 0.6
        if k % 2:
            r *= 0.6
        ir = int(math.ceil(r))
        for dx in range(-ir, ir + 1):
            for dz in range(-ir, ir + 1):
                if abs(dx) + abs(dz) * 0.9 <= r + 0.2 and (dx or dz):
                    _leaf(v, x + dx, yy, z + dz, "spruce")
        yy += 1
        k += 1
    _leaf(v, x, y + h, z, "spruce")
    _leaf(v, x, y + h + 1, z, "spruce")


def birch(v, x, y, z, rnd: random.Random):
    h = rnd.randint(7, 11)
    for dy in range(h):
        _log(v, x, y + dy, z, "birch")
    cy = y + h - 1
    for dy in range(-3, 3):
        r = 2.6 - abs(dy + 0.5) * 0.45
        ir = int(math.ceil(r))
        for dx in range(-ir, ir + 1):
            for dz in range(-ir, ir + 1):
                if dx * dx + dz * dz <= r * r and _hash2(x + dx, z + dz, cy + dy) > 0.12:
                    _leaf(v, x + dx, cy + dy, z + dz, "birch")


def wide_oak(v, x, y, z, rnd: random.Random):
    """低く横に広がる楢 (幹は曲がり、枝が二〜三本、葉は平たい塊)"""
    h = rnd.randint(5, 7)
    for dy in range(h):
        _log(v, x, y + dy, z, "oak")
    tops = [(x, y + h, z)]
    for k in range(rnd.randint(2, 3)):
        a = rnd.uniform(0, math.tau)
        L = rnd.randint(3, 5)
        px, py, pz = x, y + h - 1 - k, z
        for i in range(1, L + 1):
            nx = x + round(math.cos(a) * i)
            nz = z + round(math.sin(a) * i)
            ny = py + (1 if i % 2 == 0 else 0)
            ax = "x" if abs(math.cos(a)) > abs(math.sin(a)) else "z"
            _log(v, nx, ny, nz, "oak", ax)
            px, py, pz = nx, ny, nz
        tops.append((px, py + 1, pz))
    for (tx, ty, tz) in tops:
        r = rnd.uniform(3.0, 4.2)
        ir = int(math.ceil(r))
        for dy in (-1, 0, 1):
            rr = r - abs(dy) * 1.3 - (0.6 if dy < 0 else 0)
            for dx in range(-ir, ir + 1):
                for dz in range(-ir, ir + 1):
                    if dx * dx + dz * dz <= rr * rr and _hash2(tx + dx, tz + dz, ty + dy) > 0.1:
                        _leaf(v, tx + dx, ty + dy, tz + dz, "oak")


def bush(v, x, y, z, rnd: random.Random, kind="spruce"):
    r = rnd.uniform(1.3, 2.2)
    ir = int(math.ceil(r))
    for dy in range(0, 2):
        for dx in range(-ir, ir + 1):
            for dz in range(-ir, ir + 1):
                if dx * dx + dz * dz + (dy * 1.8) ** 2 <= r * r and _hash2(x + dx, z + dz, y + dy) > 0.15:
                    _leaf(v, x + dx, y + dy, z + dz, kind)


# ---------------------------------------------------------------- 岩
# 自然の地面に数えるものだけ (SiteJob.naturalGround。あとから建つ場所が「人の建物」と見ないように)
ROCK = ["minecraft:andesite", "minecraft:stone", "minecraft:tuff", "minecraft:gravel", "minecraft:mossy_cobblestone"]


def boulder(v, x, y, z, rnd: random.Random, r=None):
    """少し沈めた岩。上は苔"""
    r = r or rnd.uniform(1.4, 3.2)
    ir = int(math.ceil(r)) + 1
    sx, sy, sz = rnd.uniform(0.9, 1.4), rnd.uniform(0.6, 0.9), rnd.uniform(0.9, 1.4)
    for dy in range(-ir, ir + 1):
        for dx in range(-ir, ir + 1):
            for dz in range(-ir, ir + 1):
                d = (dx / sx) ** 2 + (dy / sy) ** 2 + (dz / sz) ** 2
                if d <= r * r * (0.85 + 0.3 * _hash2(x + dx, z + dz, y + dy)):
                    top = (dy + 1) ** 2 / (sy * sy) + (dx / sx) ** 2 + (dz / sz) ** 2 > r * r
                    s = "minecraft:moss_block" if top and dy >= 0 and _hash2(x + dx, z + dz, 5) > 0.35 else \
                        ROCK[int(_hash2(x + dx, z + dz, y + dy + 9) * len(ROCK))]
                    v.set(x + dx, y + dy - 1, z + dz, s)


def spire(v, x, y, z, rnd: random.Random, h=None, r0=None):
    """細く高い岩の塔 (参考: 砂岩の尖塔の群れ)。太い縞、ところどころ大きく張り出す棚 (上は草)、頂は草と茂み、全体が少し傾く"""
    h = h or rnd.randint(18, 34)
    r0 = r0 or rnd.uniform(3.5, 5.5)
    lean = (rnd.uniform(-0.1, 0.1), rnd.uniform(-0.1, 0.1))
    n_ledge = 1 if h < 16 else rnd.randint(2, 3)
    ledges = {int(h * rnd.uniform(0.25 + 0.6 * i / n_ledge, 0.2 + 0.6 * (i + 1) / n_ledge)): rnd.uniform(1.5, 3.0) for i in range(n_ledge)}
    salt = rnd.randint(0, 999)
    band_h = rnd.choice((3, 4))
    cols = {}
    for dy in range(-4, h + 1):
        t = max(0, dy) / h
        # 根もとは広く、中ほどは細く、頂の下で少し膨らむ (帽子)
        r = r0 * (1 - t) ** 0.55 + 1.2 + (1.2 if t > 0.9 else 0)
        for ly, extra in ledges.items():
            k = dy - ly
            if 0 <= k <= 1:
                r += extra * (1 - 0.35 * k)
        cx = x + lean[0] * dy * dy / 8
        cz = z + lean[1] * dy * dy / 8
        ir = int(math.ceil(r)) + 1
        for dx in range(-ir, ir + 1):
            for dz in range(-ir, ir + 1):
                px, pz = int(round(cx)) + dx, int(round(cz)) + dz
                ang = math.atan2(dz, dx)
                rr = r * (0.78 + 0.22 * math.sin(ang * 3 + dy * 0.25 + salt) + 0.08 * math.sin(ang * 7 - salt))
                if dx * dx + dz * dz <= rr * rr:
                    v.set(px, y + dy, pz, strata(px, y + dy, pz, salt, band_h))
                    cols[(px, pz)] = y + dy
    # 上を向いた面 (棚と頂) は草と苔
    for (px, pz), top in cols.items():
        if v.get(px, top + 1, pz).endswith(":air"):
            r = _hash2(px, pz, salt)
            v.set(px, top, pz, "minecraft:grass_block[snowy=false]" if r > 0.35 else "minecraft:moss_block")
            if r > 0.8:
                v.set(px, top + 1, pz, "minecraft:short_grass")
    tx = int(round(x + lean[0] * h * h / 8))
    tz = int(round(z + lean[1] * h * h / 8))
    if h >= 20:
        bush(v, tx, y + h + 1, tz, rnd, "spruce")
    return tx, y + h, tz


def fin(v, x, y, z, rnd: random.Random, length=None, height=None, ang=None):
    """長く傾いた岩の背 (尾根から突き出た板状の岩)。峰がいくつか並び、片側へ傾き、根もとは広い。上を向いた面は草と苔"""
    length = length or rnd.randint(30, 48)
    height = height or rnd.randint(14, 24)
    ang = rnd.uniform(0, math.pi) if ang is None else ang
    ux, uz = math.cos(ang), math.sin(ang)
    nx, nz = -uz, ux
    lean = rnd.uniform(0.45, 0.8) * rnd.choice((-1, 1))
    salt = rnd.randint(0, 999)
    band_h = rnd.choice((2, 3))
    peaks = [(rnd.uniform(-0.4, 0.4), rnd.uniform(0.55, 1.0)) for _ in range(rnd.randint(2, 3))]
    tops = {}
    half = length / 2
    for t in range(-int(half) - 2, int(half) + 3):
        u = t / half
        if abs(u) > 1.05:
            continue
        prof = max(p[1] * max(0.0, 1 - ((u - p[0]) / 0.45) ** 2) for p in peaks)
        prof = max(prof, 0.18 * (1 - u * u))
        hh = int(height * prof)
        for dy in range(-3, hh + 1):
            k = max(0, dy) / max(1, height)
            w = (1 - k) * rnd.uniform(3.4, 4.0) + 0.8
            off = lean * dy
            for s_ in range(-int(w) - 1, int(w) + 2):
                if abs(s_ - 0.0) > w:
                    continue
                px = int(round(x + ux * t + nx * (s_ + off)))
                pz = int(round(z + uz * t + nz * (s_ + off)))
                v.set(px, y + dy, pz, strata(px, y + dy, pz, salt, band_h))
                if tops.get((px, pz), -999) < y + dy:
                    tops[(px, pz)] = y + dy
    for (px, pz), top in tops.items():
        if v.get(px, top + 1, pz).endswith(":air"):
            r = _hash2(px, pz, salt)
            if r > 0.45:
                v.set(px, top, pz, "minecraft:grass_block[snowy=false]" if r > 0.7 else "minecraft:moss_block")
    return tops


def arch(v, ax, ay, az, bx, by, bz, rise, rnd: random.Random, thick=4.0):
    """二つの足 (a, b) を結ぶ岩の門。足は地面まで太く、真ん中は細く高い (下をくぐれる)。上は草と苔"""
    salt = rnd.randint(0, 999)
    L = math.hypot(bx - ax, bz - az)
    n = int(L * 2) + 1
    tops = {}
    for i in range(n + 1):
        t = i / n
        cx = ax + (bx - ax) * t
        cz = az + (bz - az) * t
        base = ay + (by - ay) * t
        cy = base + rise * math.sin(math.pi * t)
        w = thick * (1.0 + 1.3 * (abs(t - 0.5) * 2) ** 3) * (0.9 + 0.2 * _hash2(i, salt, 3))
        h = w * 0.75
        # 足は地面まで (t の端)
        foot = (t < 0.12 or t > 0.88)
        ir = int(math.ceil(w)) + 1
        for dx in range(-ir, ir + 1):
            for dz in range(-ir, ir + 1):
                if dx * dx + dz * dz > w * w:
                    continue
                px, pz = int(round(cx + dx)), int(round(cz + dz))
                y_lo = int(base) - 2 if foot else int(cy - h)
                for py in range(y_lo, int(cy + h) + 1):
                    v.set(px, py, pz, strata(px, py, pz, salt, 3))
                    if tops.get((px, pz), -999) < py:
                        tops[(px, pz)] = py
    for (px, pz), top in tops.items():
        if v.get(px, top + 1, pz).endswith(":air") and _hash2(px, pz, salt) > 0.3:
            v.set(px, top, pz, "minecraft:grass_block[snowy=false]" if _hash2(px, pz, salt + 1) > 0.4 else "minecraft:moss_block")
    return tops


# ---------------------------------------------------------------- 草花
FLOWERS = ["minecraft:dandelion", "minecraft:poppy", "minecraft:azure_bluet", "minecraft:oxeye_daisy", "minecraft:cornflower",
           "minecraft:allium", "minecraft:lily_of_the_valley"]
