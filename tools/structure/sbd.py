"""構造の書き出し (.sbd) を読む。書くのは StructureDump.java (/skyabyss dev dump ...)。

形式 (gzip): b"SKYDUMP1" / ヘッダの長さ (4 バイト big endian) / ヘッダの JSON / パレットの番号 (uint16 little endian、y・z・x の順)。

    d = load("run/dumps/titan_back.sbd")
    d.ids[y, z, x]            # パレットの番号 (配列の添字。世界の座標は d.origin を足す)
    d.collide[y, z, x]        # 当たりがある (歩けない・空気でない)
    d.water / d.lava / d.air  # 液体・空気
    d.at(x, y, z)             # 世界の座標でパレットの項目 (dict)
    d.point("arrival")        # ヘッダの起点 (世界の座標)
"""
from __future__ import annotations

import gzip
import json
import struct
from dataclasses import dataclass, field
from pathlib import Path

import numpy as np


@dataclass
class Dump:
    header: dict
    ids: np.ndarray  # (sy, sz, sx) uint16
    palette: list = field(default_factory=list)

    def __post_init__(self):
        p = self.palette
        self.origin = tuple(self.header["origin"])
        self.name = self.header.get("name", "dump")
        self.dimension = self.header.get("dimension", "?")

        def flag(fn):
            return np.array([bool(fn(e)) for e in p], dtype=bool)[self.ids]

        self.air = flag(lambda e: e.get("air"))
        self.collide = flag(lambda e: e.get("collide"))
        self.water = flag(lambda e: e.get("fluid") == "water")
        self.lava = flag(lambda e: e.get("fluid") == "lava")
        self.source = flag(lambda e: e.get("source"))
        self.climbable = flag(lambda e: e.get("climbable"))
        self.colors = np.array([_rgb(e.get("color", "#000000")) for e in p], dtype=np.uint8)
        self.names = [e["name"] for e in p]

    # ---------------------------------------------------------------- 座標
    @property
    def shape(self):
        return self.ids.shape

    def index(self, x, y, z):
        ox, oy, oz = self.origin
        return y - oy, z - oz, x - ox

    def world(self, iy, iz, ix):
        ox, oy, oz = self.origin
        return ix + ox, iy + oy, iz + oz

    def inside(self, x, y, z):
        iy, iz, ix = self.index(x, y, z)
        sy, sz, sx = self.shape
        return 0 <= iy < sy and 0 <= iz < sz and 0 <= ix < sx

    def at(self, x, y, z) -> dict:
        iy, iz, ix = self.index(x, y, z)
        return self.palette[int(self.ids[iy, iz, ix])]

    def point(self, name_or_xyz):
        """'arrival' などヘッダの起点の名前か、'x,y,z' の文字列か、(x, y, z)。'x,~,z' は列でいちばん上の立てる所"""
        if isinstance(name_or_xyz, (tuple, list)):
            return tuple(int(v) for v in name_or_xyz)
        pts = self.header.get("points", {})
        if name_or_xyz in pts:
            return tuple(pts[name_or_xyz])
        parts = [s.strip() for s in name_or_xyz.split(",")]
        if len(parts) != 3:
            raise ValueError(f"not a point: {name_or_xyz} (names: {list(pts)})")
        x, z = int(parts[0]), int(parts[2])
        if parts[1] == "~":
            iy, iz, ix = self.index(x, self.origin[1], z)
            col = self.collide[:, iz, ix]
            for y in range(self.shape[0] - 2, 0, -1):
                if col[y - 1] and not col[y] and not col[y + 1]:
                    return x, y + self.origin[1], z
            raise ValueError(f"no standing place in column {x},{z}")
        return x, int(parts[1]), z

    def block_counts(self):
        counts = np.bincount(self.ids.ravel(), minlength=len(self.palette))
        return sorted(((self.palette[i]["state"], int(c)) for i, c in enumerate(counts) if c), key=lambda t: -t[1])


def _rgb(s: str):
    s = s.lstrip("#")
    return int(s[0:2], 16), int(s[2:4], 16), int(s[4:6], 16)


def load(path) -> Dump:
    raw = gzip.decompress(Path(path).read_bytes())
    if raw[:8] != b"SKYDUMP1":
        raise ValueError(f"{path}: not a skyabyss dump")
    (n,) = struct.unpack(">i", raw[8:12])
    header = json.loads(raw[12:12 + n].decode("utf-8"))
    sx, sy, sz = header["size"]
    ids = np.frombuffer(raw, dtype="<u2", offset=12 + n, count=sx * sy * sz).reshape(sy, sz, sx).copy()
    return Dump(header, ids, header["palette"])
