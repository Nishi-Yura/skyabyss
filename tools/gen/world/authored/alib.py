# -*- coding: utf-8 -*-
"""前もって組む土地 (world/continent/ContinentAuthored) の道具の共通部品。

元は今の世界の書き出し (.sbd、dev/StructureDump)。ここで地形・道・木をブロック一つずつ組み直し、
同じ形式 (SKYDUMP1) で data/skyabyss/continent/authored/<名>.sbd に書く。
配列は ids[y, z, x] (パレットの番号)。世界の座標は origin を足す。
"""
from __future__ import annotations

import gzip
import json
import math
import struct
import sys
from pathlib import Path

import numpy as np

ROOT = Path(__file__).resolve().parents[4]
sys.path.insert(0, str(ROOT / "tools" / "structure"))
import sbd  # noqa: E402

OUT_DIR = ROOT / "src" / "main" / "resources" / "data" / "skyabyss" / "continent" / "authored"

# 自然の地面・石・草木 (これ以外が列にあれば、人の作った物 = 町・見どころ・道の飾り)
NATURAL_PREFIX = (
    "minecraft:air", "minecraft:cave_air", "minecraft:water", "minecraft:grass_block", "minecraft:dirt", "minecraft:coarse_dirt",
    "minecraft:podzol", "minecraft:rooted_dirt", "minecraft:moss_block", "minecraft:moss_carpet", "minecraft:stone[",
    "minecraft:stone", "minecraft:andesite", "minecraft:diorite", "minecraft:granite", "minecraft:tuff", "minecraft:gravel",
    "minecraft:sand", "minecraft:clay", "minecraft:mud", "minecraft:deepslate", "minecraft:calcite", "minecraft:short_grass",
    "minecraft:tall_grass", "minecraft:fern", "minecraft:large_fern", "minecraft:dandelion", "minecraft:poppy",
    "minecraft:azure_bluet", "minecraft:oxeye_daisy", "minecraft:cornflower", "minecraft:allium", "minecraft:blue_orchid",
    "minecraft:lily_of_the_valley", "minecraft:sweet_berry_bush", "minecraft:spruce_log", "minecraft:spruce_leaves",
    "minecraft:oak_log", "minecraft:oak_leaves", "minecraft:birch_log", "minecraft:birch_leaves", "minecraft:dark_oak_log",
    "minecraft:dark_oak_leaves", "minecraft:snow", "minecraft:mossy_cobblestone", "minecraft:cobblestone", "minecraft:bedrock",
    "minecraft:coal_ore", "minecraft:iron_ore", "minecraft:copper_ore", "minecraft:dead_bush", "minecraft:pink_petals",
    "minecraft:bush", "minecraft:seagrass", "minecraft:kelp", "minecraft:vine", "minecraft:glow_lichen", "minecraft:mushroom",
    "minecraft:brown_mushroom", "minecraft:red_mushroom", "minecraft:pointed_dripstone", "minecraft:dripstone_block",
    "minecraft:dirt_path", "minecraft:packed_mud", "minecraft:mangrove_roots", "minecraft:hanging_roots",
)


class Vol:
    """書き換えられる立体。ids[y, z, x]"""

    def __init__(self, dump: "sbd.Dump"):
        self.header = dump.header
        self.ox, self.oy, self.oz = dump.origin
        self.ids = dump.ids.copy()
        self.states = [e["state"] for e in dump.palette]
        self.index = {s: i for i, s in enumerate(self.states)}
        self.sy, self.sz, self.sx = self.ids.shape

    # ------------------------------------------------------------ パレット
    def id(self, state: str) -> int:
        if ":" not in state:
            state = "minecraft:" + state
        i = self.index.get(state)
        if i is None:
            i = len(self.states)
            self.states.append(state)
            self.index[state] = i
        return i

    def mask_of(self, pred) -> np.ndarray:
        """パレットの項目ごとの真偽を、立体の形の配列に"""
        flags = np.array([bool(pred(s)) for s in self.states], dtype=bool)
        return flags[self.ids]

    def natural_flags(self) -> np.ndarray:
        return np.array([s.startswith(NATURAL_PREFIX) for s in self.states], dtype=bool)

    # ------------------------------------------------------------ 座標
    def local(self, x, y, z):
        return y - self.oy, z - self.oz, x - self.ox

    def set(self, x, y, z, state):
        iy, iz, ix = y - self.oy, z - self.oz, x - self.ox
        if 0 <= iy < self.sy and 0 <= iz < self.sz and 0 <= ix < self.sx:
            self.ids[iy, iz, ix] = self.id(state) if isinstance(state, str) else state

    def get(self, x, y, z) -> str:
        iy, iz, ix = y - self.oy, z - self.oz, x - self.ox
        if 0 <= iy < self.sy and 0 <= iz < self.sz and 0 <= ix < self.sx:
            return self.states[int(self.ids[iy, iz, ix])]
        return "minecraft:air"

    # ------------------------------------------------------------ 書く
    def write(self, name: str, extra: dict | None = None) -> Path:
        used = np.unique(self.ids)
        remap = np.zeros(len(self.states), dtype=np.uint16)
        remap[used] = np.arange(len(used), dtype=np.uint16)
        ids = remap[self.ids]
        head = {
            "name": name,
            "dimension": "minecraft:overworld",
            "origin": [self.ox, self.oy, self.oz],
            "size": [self.sx, self.sy, self.sz],
            "order": "yzx",
            "palette": [{"state": self.states[int(i)], "name": self.states[int(i)].split("[")[0],
                         "air": self.states[int(i)].endswith(":air")} for i in used],
            "sites": self.header.get("sites", {"entries": []}),
        }
        if extra:
            head.update(extra)
        js = json.dumps(head, ensure_ascii=False).encode("utf-8")
        OUT_DIR.mkdir(parents=True, exist_ok=True)
        path = OUT_DIR / f"{name}.sbd"
        body = b"SKYDUMP1" + struct.pack(">i", len(js)) + js + ids.astype("<u2").tobytes()
        path.write_bytes(gzip.compress(body, 6))
        idx = OUT_DIR / "index.json"
        names = json.loads(idx.read_text(encoding="utf-8")) if idx.exists() else []
        if name not in names:
            names.append(name)
        idx.write_text(json.dumps(names) + "\n", encoding="utf-8")
        return path


# ---------------------------------------------------------------- 区画の形式 (ContinentAuthored。2026-11-25 の土地全体から)
TILE = 256


def write_tile(land: str, tx: int, tz: int, ids: np.ndarray, states: list, oy: int, mask: np.ndarray | None = None) -> int:
    """区画 1 枚を書く。ids[y, z, x] は 256×256 の列 (y は oy から)。mask[z, x] が False の列は組まない (生成器のまま)。
    列は「oy より下は石、oy から (数, パレット) の並び、上は空気」。返り値: ファイルの大きさ"""
    sy, sz, sx = ids.shape
    assert sz == TILE and sx == TILE
    air = np.array([s.endswith(":air") for s in states], dtype=bool)
    used = np.unique(ids)
    remap = np.zeros(len(states), dtype=np.int64)
    remap[used] = np.arange(len(used))
    pal = [states[int(i)] for i in used]
    cols = []
    total = 0
    for iz in range(TILE):
        for ix in range(TILE):
            if mask is not None and not mask[iz, ix]:
                cols.append(None)
                continue
            col = ids[:, iz, ix]
            nz = np.nonzero(~air[col])[0]
            top = int(nz[-1]) + 1 if len(nz) else 0
            col = col[:top]
            if top == 0:
                cols.append(np.zeros(0, dtype=np.uint16))
                continue
            edges = np.nonzero(col[1:] != col[:-1])[0] + 1
            starts = np.concatenate(([0], edges))
            lens = np.diff(np.concatenate((starts, [top])))
            pairs = np.empty(len(starts) * 2, dtype=np.uint16)
            pairs[0::2] = lens
            pairs[1::2] = remap[col[starts]]
            cols.append(pairs)
            total += 1 + len(pairs)
    head = json.dumps({"tile": [tx, tz], "y0": oy, "palette": pal}, ensure_ascii=False).encode("utf-8")
    parts = [b"SKYTILE1", struct.pack(">i", len(head)), head, struct.pack(">i", total)]
    for c in cols:
        if c is None:
            parts.append(struct.pack(">H", 0xFFFF))
        else:
            parts.append(struct.pack(">H", len(c) // 2))
            parts.append(c.astype(">u2").tobytes())
    out = OUT_DIR / land
    out.mkdir(parents=True, exist_ok=True)
    path = out / f"{tx}_{tz}.bin"
    path.write_bytes(gzip.compress(b"".join(parts), 6))
    return path.stat().st_size


def write_land_index(land: str, tiles: list, sites: dict):
    out = OUT_DIR / land
    out.mkdir(parents=True, exist_ok=True)
    (out / "index.json").write_text(json.dumps({"tile": TILE, "tiles": sorted([list(t) for t in tiles]), "sites": sites},
                                               ensure_ascii=False, indent=1) + "\n", encoding="utf-8")
    idx = OUT_DIR / "index.json"
    names = json.loads(idx.read_text(encoding="utf-8")) if idx.exists() else []
    names = [n for n in names if (OUT_DIR / n / "index.json").exists() or n == land]
    if land not in names:
        names.append(land)
    idx.write_text(json.dumps(names) + "\n", encoding="utf-8")


def vol_to_tiles(v: "Vol", land: str) -> list:
    """立体 (試作の 1 枚) を区画に切って書く。立体の外の列は組まない"""
    tiles = []
    for tx in range(math.floor(v.ox / TILE), math.floor((v.ox + v.sx - 1) / TILE) + 1):
        for tz in range(math.floor(v.oz / TILE), math.floor((v.oz + v.sz - 1) / TILE) + 1):
            ids = np.zeros((v.sy, TILE, TILE), dtype=v.ids.dtype)
            mask = np.zeros((TILE, TILE), dtype=bool)
            x0, z0 = tx * TILE, tz * TILE
            ax0, ax1 = max(x0, v.ox), min(x0 + TILE, v.ox + v.sx)
            az0, az1 = max(z0, v.oz), min(z0 + TILE, v.oz + v.sz)
            if ax0 >= ax1 or az0 >= az1:
                continue
            ids[:, az0 - z0:az1 - z0, ax0 - x0:ax1 - x0] = v.ids[:, az0 - v.oz:az1 - v.oz, ax0 - v.ox:ax1 - v.ox]
            mask[az0 - z0:az1 - z0, ax0 - x0:ax1 - x0] = True
            write_tile(land, tx, tz, ids, v.states, v.oy, mask)
            tiles.append((tx, tz))
    return tiles


_TILE_CACHE = {}


def read_tile(land: str, tx: int, tz: int):
    """区画を読む → (y0, palette, cols)。cols[iz * 256 + ix] は [(数, パレット), ...] か None (組んでいない)"""
    k = (land, tx, tz)
    if k in _TILE_CACHE:
        return _TILE_CACHE[k]
    raw = gzip.decompress((OUT_DIR / land / f"{tx}_{tz}.bin").read_bytes())
    assert raw[:8] == b"SKYTILE1"
    (n,) = struct.unpack(">i", raw[8:12])
    head = json.loads(raw[12:12 + n].decode("utf-8"))
    p = 12 + n + 4
    cols = []
    for _ in range(TILE * TILE):
        (c,) = struct.unpack(">H", raw[p:p + 2])
        p += 2
        if c == 0xFFFF:
            cols.append(None)
            continue
        arr = np.frombuffer(raw, dtype=">u2", count=c * 2, offset=p)
        p += c * 4
        cols.append(list(zip(arr[0::2].tolist(), arr[1::2].tolist())))
    _TILE_CACHE[k] = (head["y0"], head["palette"], cols)
    return _TILE_CACHE[k]


def tile_ground(land: str, x: int, z: int, skip=("leaves", "_log", "lantern", "fence", "grass", "fern", "flower", "vine")) -> int:
    """組んだ列の、木や草花を除いたいちばん上の高さ (撮影の目の高さ用)"""
    tx, tz = x // TILE, z // TILE
    y0, pal, cols = read_tile(land, tx, tz)
    col = cols[(z - tz * TILE) * TILE + (x - tx * TILE)]
    y = y0
    last = y0 - 1
    for n, i in col:
        s = pal[i]
        name = s.split("[")[0]
        if not s.endswith(":air") and not (any(w in name for w in skip) and "grass_block" not in name):
            last = y + n - 1
        y += n
    return last
