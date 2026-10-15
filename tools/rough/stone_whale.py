"""岩鯨 (stone_whale) の3案 A/B/C を GeckoLib の geo.json とテクスチャにする。

使い方 (リポジトリ直下で): python tools/rough/stone_whale.py
出力: docs/roughs/stone_whale/<a|b|c>/ (geo.json, テクスチャ, 光る部分, 4方向の絵)
      docs/roughs/stone_whale.png (比較シート), docs/roughs/stone_whale.md (説明と重なり検査)

作り方:
- 体は z 方向の厚さ 3 の輪切りを並べる。輪切りは 6 本の帯 (腹 → 背) の箱で、面で接するだけ (重なりなし)。
- ひれ・尾びれ・口の線・喉の溝・背の遺跡は、輪切りの位置に合わせて面で接するように置く。
- テクスチャは各面の画素を「ワールド座標」で決める。だから輪切りをまたいで模様がつながる。
- テクスチャの向きは Minecraft の規則 (UV 矩形の上の行 = 面の上)。
- 頭は -Z、尾は +Z。鎖の付け根は z=60 の -X 側 (尾側の左わき腹)。乱数なし。
"""
import json
import math
import sys
from pathlib import Path

import numpy as np
from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "tools/rough"))
from voxel import check_overlaps, load_geo, render, sheet  # noqa: E402

OUT = ROOT / "docs/roughs/stone_whale"
Z0, Z1, SL = -132, 138, 3   # 輪切りの範囲と厚み (Z0 + 3n の所に 60 がある)
YC = 40                     # 体の中心の高さ
CHAIN_Z = 60
BAND_H = [0.15, 0.21, 0.24, 0.22, 0.11, 0.07]   # 腹 -> 背 の帯の厚み (合計 1)
BAND_MAT = ["belly", "hide", "hide", "hide", "hide", "moss"]
FLUKE_HALF = 58             # 尾びれの片側の長さ (全幅 約 124 = 体長の 4 割強)

PAL = {
    "stone": [(96, 100, 110), (80, 84, 96), (68, 72, 84)],
    "mortar": (40, 42, 52),
    "belly": [(190, 182, 164), (168, 160, 144)],
    "belly_mortar": (124, 116, 104),
    "moss": [(86, 122, 60), (68, 100, 46)],
    "dirt": (98, 76, 54),
    "ruin": [(108, 104, 112), (90, 86, 96), (124, 118, 122)],
    "ruin_mortar": (56, 52, 62),
    "cyan": (96, 232, 255),
    "amber": (255, 196, 92),
    "pupil": (30, 24, 20),
    "groove": [(60, 58, 68), (48, 46, 56)],
    "fin": [(72, 78, 90), (58, 62, 74)],
    "fin_edge": (44, 46, 56),
    "bone": [(206, 198, 174), (178, 170, 148)],
    "iron": [(78, 80, 90), (58, 60, 68)],
    "iron_hi": (124, 126, 136),
    "crust": [(152, 142, 120), (120, 112, 94)],
    "speck": (186, 176, 146),
}

BONES = [
    ("body", None, (0, YC, 0)),
    ("head", "body", (0, YC, -60)),
    ("jaw", "head", (0, YC - 6, -132)),
    ("tail1", "body", (0, YC, 60)),
    ("tail2", "tail1", (0, YC, 96)),
    ("tail3", "tail2", (0, YC, 120)),
    ("fluke", "tail3", (0, YC, 138)),
    ("fins", "body", (0, YC, 0)),
    ("ruins", "body", (0, YC, 0)),
    ("chain", "body", (0, YC, 60)),
]


def bone_for(z):
    if z < -60:
        return "head"
    if z < 60:
        return "body"
    if z < 96:
        return "tail1"
    if z < 120:
        return "tail2"
    return "tail3"


def prof(keys, z):
    for (z0, w0, h0), (z1, w1, h1) in zip(keys, keys[1:]):
        if z0 <= z <= z1:
            t = (z - z0) / (z1 - z0)
            t = t * t * (3 - 2 * t)
            return w0 + (w1 - w0) * t, h0 + (h1 - h0) * t
    return keys[-1][1], keys[-1][2]


# ---------------------------------------------------------------- 3案の形
# keys: (z, 全幅, 全高)。頭の先は丸く、尾の柄は細く。案ごとに体の太さ・頭の形が違う。
OPTS = {
    "a": dict(  # A 王道: ザトウクジラ型。太い胴、こぶのある頭、長い胸びれ
        name="A 王道 (ザトウ型)", tint=(1.0, 1.0, 1.0), ls=1.0, rs=1.0, jaw=4, jaw_w=0.8,
        fluke=(0, FLUKE_HALF), wfr=[0.70, 0.93, 1.0, 1.0, 0.94, 0.62],
        keys=[(-132, 36, 30), (-124, 56, 46), (-104, 74, 62), (-70, 80, 70), (-30, 82, 74),
              (10, 74, 64), (50, 54, 46), (84, 34, 28), (114, 18, 16), (138, 9, 8)],
        pect=dict(band=1, zlo=-84, zhi=-14, steps=7, sw=3, drop=1, thick=4, sides="LR"),
    ),
    "b": dict(  # B 重厚: マッコウクジラ型。四角い巨大な頭、重い胴、短い胸びれ
        name="B 重厚 (マッコウ型)", tint=(0.9, 0.9, 0.93), ls=1.0, rs=1.0, jaw=6, jaw_w=0.5,
        fluke=(0, FLUKE_HALF), wfr=[0.80, 0.95, 1.0, 1.0, 0.95, 0.66],
        keys=[(-132, 80, 60), (-112, 92, 74), (-80, 90, 76), (-40, 88, 66), (0, 80, 56),
              (40, 62, 46), (80, 36, 30), (114, 18, 16), (138, 9, 8)],
        pect=dict(band=1, zlo=-50, zhi=-22, steps=4, sw=3, drop=1, thick=4, sides="LR"),
    ),
    "c": dict(  # C 異形: シロナガス型の細身。背に骨の稜線、左右非対称
        name="C 異形 (シロナガス型)", tint=(0.96, 1.0, 1.08), ls=1.08, rs=0.92, jaw=5, jaw_w=0.7,
        fluke=(4, FLUKE_HALF), wfr=[0.60, 0.90, 1.0, 1.0, 0.90, 0.62],
        keys=[(-132, 40, 34), (-110, 52, 44), (-80, 56, 50), (-40, 58, 52), (-5, 56, 50),
              (40, 50, 44), (80, 34, 30), (114, 20, 18), (138, 9, 8)],
        pect=dict(band=1, zlo=-40, zhi=-8, steps=3, sw=3, drop=1, thick=3, sides="LR"),
    ),
}


class Builder:
    def __init__(self, o):
        self.o = o
        self.cubes = {}      # 骨の名前 -> [(origin, size, material)]
        self.slices = {}     # z -> {top, bottom, bands}
        self.fin_zones = []
        self._body()

    def add(self, bone, origin, size, mat):
        org = tuple(int(round(v)) for v in origin)
        sz = tuple(int(round(v)) for v in size)
        assert all(v > 0 for v in sz), (bone, origin, size, mat)
        self.cubes.setdefault(bone, []).append((org, sz, mat))

    def _body(self):
        o = self.o
        for zk in range(Z0, Z1, SL):
            W, H = prof(o["keys"], zk + SL / 2)
            W, H = 2 * round(W / 2), 2 * round(H / 2)
            bottom = YC - H // 2
            cum, acc = [0], 0.0
            for f in BAND_H:
                acc += f
                cum.append(round(H * acc))
            cum[-1] = H
            bands = []
            for i in range(len(BAND_H)):
                y0, y1 = bottom + cum[i], bottom + cum[i + 1]
                hw = W / 2 * o["wfr"][i]
                xl = -max(1, round(hw * o["ls"]))
                xr = max(1, round(hw * o["rs"]))
                bands.append(dict(y0=y0, y1=y1, xl=xl, xr=xr, mat=BAND_MAT[i]))
                if y1 > y0:
                    self.add(bone_for(zk), (xl, y0, zk), (xr - xl, y1 - y0, SL), BAND_MAT[i])
            self.slices[zk] = dict(top=YC + H // 2, bottom=bottom, bands=bands)
            if zk < -90:
                # 下あご: 腹の帯の下に面で接して垂れる。幅は案ごとに違う
                b0 = bands[0]
                jw = max(2, round((b0["xr"] - b0["xl"]) / 2 * o["jaw_w"]))
                self.add("jaw", (-jw, bottom - o["jaw"], zk), (2 * jw, o["jaw"], SL), "hide")

    def zrange(self, zlo, zhi):
        return sorted(z for z in self.slices if zlo <= z < zhi)

    # ---- 体の表面につく部品
    def mouth_and_throat(self):
        """口の線 (頭の側面の暗い線) と、喉の溝 (腹の下の細い垂れ)"""
        for z in self.zrange(Z0, -66):
            bs = self.slices[z]["bands"]
            xmin = min(b["xl"] for b in bs[:3])
            xmax = max(b["xr"] for b in bs[:3])
            y = bs[0]["y1"] - 1
            self.add("head", (xmin - 1, y, z), (1, 1, SL), "groove")
            self.add("head", (xmax, y, z), (1, 1, SL), "groove")
        for z in self.zrange(-90, 30):
            b0 = self.slices[z]["bands"][0]
            for x in (-6, -2, 2, 6):
                self.add("body", (x - 1, b0["y0"] - 1, z), (2, 1, SL), "groove")

    def pectoral(self, band, zlo, zhi, steps, sw, drop, thick, sides):
        """胸びれ。根は体の側面に面で接し、外へ行くほど下がり、先ほど短くなる (階段状の斜めの板)"""
        zs = self.zrange(zlo, zhi)
        n = len(zs)
        for i, z in enumerate(zs):
            b = self.slices[z]["bands"][band]
            mid = (b["y0"] + b["y1"]) // 2 - thick // 2
            for k in range(steps):
                if i < k or i >= n - k:
                    continue
                yk = mid - k * drop
                if "L" in sides:
                    self.add("fins", (b["xl"] - (k + 1) * sw, yk, z), (sw, thick, SL), "fin")
                if "R" in sides:
                    self.add("fins", (b["xr"] + k * sw, yk, z), (sw, thick, SL), "fin")
        self.fin_zones.append((zlo, zhi, sides))

    def fluke(self, lift, half):
        """尾びれ: 左右の葉。根から外へ、後ろへ流れる (外ほど z が伸びる)。中央は後ろが短い (切れ込み)"""
        for side in (-1, 1):
            k = 0
            while True:
                ax = 2 + 4 * k
                if ax > half:
                    break
                x = ax if side > 0 else -(ax + 4)
                zs = 138 + int(0.15 * ax)
                ze = 146 + int(0.28 * ax)
                y = YC - 1 + (lift if side < 0 else 0)
                self.add("fluke", (x, y, zs), (4, 3, ze - zs), "fin")
                k += 1

    def stairs(self, zlo, zhi, steps, x0, x1):
        """背に上がる階段。段の高さを上げていく (ブロックどうしは面で接する)"""
        for (a, b, h) in steps:
            self.ruin_block(a, b, x0, x1, h)

    def ruin_block(self, zlo, zhi, x0, x1, h, base=0, glyph=False):
        for z in self.zrange(zlo, zhi):
            y = self.slices[z]["top"] + base
            self.add("ruins", (x0, y, z), (x1 - x0, h, SL), "ruin")
            if glyph:
                self.add("ruins", (x1, y + h // 2 - 1, z), (1, 2, SL), "glyph")

    def ruin_arch(self, zlo, zhi, R, lift=4):
        """背に立つ半円のアーチ。両脚が背の面に乗り、脚の上端がアーチの端につながる"""
        for z in self.zrange(zlo, zhi):
            s = self.slices[z]
            top = s["top"]
            b = s["bands"][5]
            assert b["xr"] >= R - 2 and -b["xl"] >= R - 2, ("arch legs off the back", z, R, b)
            for th in range(0, 181, 30):
                cx = round(R * math.cos(math.radians(th)))
                cy = round(R * math.sin(math.radians(th)))
                self.add("ruins", (cx - 2, top + lift + cy, z), (4, 4, SL), "ruin")
            for cx in (R, -R):
                self.add("ruins", (cx - 2, top, z), (4, lift, SL), "ruin")

    def ridges(self, every=9):
        """背の骨の稜線: 背の頂に、間をあけて並ぶ小さな骨"""
        taken = {org[2] for org, _, _ in self.cubes.get("ruins", [])}
        for z in sorted(self.slices):
            if (z - Z0) % every == 0 and -120 <= z <= 110 and z not in taken:
                top = self.slices[z]["top"]
                self.add("body", (-1, top, z), (2, 3, SL), "bone")

    def tubercles(self, zlo, zhi):
        """頭のこぶ: 頭の頂に丸く並ぶ岩のこぶ"""
        for z in self.zrange(zlo, zhi):
            if (z - Z0) % 6 != 0:
                continue
            top = self.slices[z]["top"]
            for x in (-9, 0, 6):
                self.add("head", (x, top, z), (3, 3, SL), "crust")

    def eyes(self, z):
        b = self.slices[z]["bands"][2]
        h = b["y1"] - b["y0"] - 2
        self.add("head", (b["xl"] - 1, b["y0"] + 1, z), (1, h, SL), "eye")
        self.add("head", (b["xr"], b["y0"] + 1, z), (1, h, SL), "eye")

    def teeth(self, zs):
        for z in zs:
            jb = self.slices[z]["bottom"] - self.o["jaw"]
            self.add("jaw", (-3, jb - 3, z), (2, 3, SL), "bone")
            self.add("jaw", (1, jb - 3, z), (2, 3, SL), "bone")

    def crusts(self):
        for z in sorted(self.slices):
            if (z - Z0) % 12 != 0 or not (-120 <= z <= 108):
                continue
            side = "L" if ((z - Z0) // 12) % 2 == 0 else "R"
            if side == "L" and 48 <= z <= 66:
                continue
            if any(zl <= z < zh and side in sd for zl, zh, sd in self.fin_zones):
                continue
            b = self.slices[z]["bands"][1]
            if b["y1"] - b["y0"] < 4:
                continue
            if side == "L":
                self.add("body", (b["xl"] - 2, b["y0"] + 1, z), (2, 2, SL), "crust")
            else:
                self.add("body", (b["xr"], b["y0"] + 1, z), (2, 2, SL), "crust")

    def chain(self):
        """鎖の付け根 (尾側の左わき腹の錨石) と、垂れる鎖の短い区間。鎖の本体はエンティティ"""
        b = self.slices[CHAIN_Z]["bands"][3]
        self.add("chain", (b["xl"] - 7, b["y0"], CHAIN_Z), (7, b["y1"] - b["y0"], SL), "iron")
        self.add("chain", (b["xl"] - 9, b["y0"] + 2, CHAIN_Z), (2, 3, 3), "iron_hi")
        y = b["y0"]
        for _ in range(4):
            y -= 6
            self.add("chain", (b["xl"] - 4, y, CHAIN_Z), (3, 6, 3), "iron")
            y -= 1


def dress_a(B):
    B.pectoral(**B.o["pect"])
    B.tubercles(-126, -96)
    B.stairs(-60, -36, [(-60, -48, 3), (-48, -36, 6)], -4, 4)   # 祠へ上る段
    B.ruin_block(-36, -24, -4, 4, 9, glyph=True)
    B.ruin_block(-60, -24, -12, -8, 16, glyph=True)            # 柱の列
    B.ruin_block(-60, -24, 8, 12, 16, glyph=True)
    B.ruin_block(-60, -24, -12, 12, 3, base=16)                # 梁
    B.ruin_block(-24, 0, 8, 12, 9, glyph=True)                 # 折れた柱
    B.ruin_block(6, 30, -10, 10, 10)                           # 祠
    B.ruin_block(6, 30, -5, 5, 4, base=10, glyph=True)
    B.ruin_block(30, 54, -12, -8, 12)
    B.ruin_block(30, 54, 8, 12, 12, glyph=True)
    B.eyes(-111)


def dress_b(B):
    B.pectoral(**B.o["pect"])
    B.stairs(-60, -18, [(-60, -48, 3), (-48, -36, 6), (-36, -24, 9), (-24, -18, 12)], -4, 4)
    B.ruin_block(-18, 18, -16, 16, 22, glyph=True)             # 中央の塔
    B.ruin_block(-18, 18, -16, -12, 3, base=22)                # 狭間
    B.ruin_block(-18, 18, -4, 0, 3, base=22)
    B.ruin_block(-18, 18, 8, 12, 3, base=22)
    B.ruin_block(-60, -42, -12, -6, 14, glyph=True)            # 小塔
    B.ruin_block(-60, -42, 6, 12, 14)
    B.ruin_block(42, 60, -12, -6, 14)
    B.ruin_block(42, 60, 6, 12, 14, glyph=True)
    B.ruin_block(-42, -18, -12, -6, 6)                         # 城壁の足
    B.ruin_block(-42, -18, 6, 12, 6)
    B.eyes(-111)
    B.teeth([-132, -120, -108])


def dress_c(B):
    B.pectoral(**B.o["pect"])
    B.ruin_arch(-6, 6, 14)                                     # 脚が背に乗る半円のアーチ
    B.ruin_arch(30, 42, 14)
    B.ruin_block(-48, -36, -3, 3, 40, glyph=True)              # 細い一本柱
    B.ridges()
    B.eyes(-111)


DRESS = {"a": dress_a, "b": dress_b, "c": dress_c}


def build(key):
    o = OPTS[key]
    B = Builder(o)
    B.mouth_and_throat()
    DRESS[key](B)
    B.crusts()
    B.chain()
    B.fluke(*o["fluke"])
    return B


# ---------------------------------------------------------------- テクスチャ
def face_corners(o, s):
    """load_geo と同じ、面の一隅 (co) と、横方向 (eu)・縦方向 (ev) のベクトル"""
    x0, y0, z0 = o
    sx, sy, sz = s
    x1, y1, z1 = x0 + sx, y0 + sy, z0 + sz
    return {
        "north": ((x1, y0, z0), (-sx, 0, 0), (0, sy, 0)),
        "south": ((x0, y0, z1), (sx, 0, 0), (0, sy, 0)),
        "east": ((x1, y0, z1), (0, 0, -sz), (0, sy, 0)),
        "west": ((x0, y0, z0), (0, 0, sz), (0, sy, 0)),
        "up": ((x0, y1, z1), (sx, 0, 0), (0, 0, -sz)),
        "down": ((x0, y0, z0), (sx, 0, 0), (0, 0, sz)),
    }


def world_grid(o, s, fname, w, h):
    """面の各画素の中心のワールド座標 (h, w, 3)。Minecraft の規則: UV 矩形の上の行が面の上。"""
    co, eu, ev = face_corners(o, s)[fname]
    co, eu, ev = (np.array(v, float) for v in (co, eu, ev))
    cols = (np.arange(w) + 0.5) / w
    rows = 1.0 - (np.arange(h) + 0.5) / h
    return co + cols[None, :, None] * eu + rows[:, None, None] * ev


def paint(tex, glow, u, v, w, h, mat, kind, tint, P):
    """1 面を描く。模様は P (ワールド座標) で決まるので、輪切りをまたいでつながる"""
    if kind == "top" or kind == "bottom":
        A, Bc = P[..., 0], P[..., 2]        # 上下の面: x と z
    else:
        A, Bc = P[..., 2], P[..., 1]        # 側面: z と y
    Ai = np.floor(A).astype(int)
    Bi = np.floor(Bc).astype(int)
    Y, X = np.mgrid[0:h, 0:w]               # 面の中の位置 (個別の模様用)
    base = np.zeros((h, w, 3))
    gm = np.zeros((h, w), bool)
    gcol = PAL["cyan"]
    if mat in ("hide", "fin", "crust", "belly", "groove"):
        tones = np.array({"hide": PAL["stone"], "fin": PAL["fin"], "crust": PAL["crust"],
                          "belly": PAL["belly"], "groove": PAL["groove"]}[mat], float)
        row = np.floor_divide(Bi, 7)
        off = (row % 2) * 4
        ca = np.floor_divide(Ai + off, 9)
        base = tones[(ca * 5 + row * 3) % len(tones)]
        mort = ((Ai + off) % 9 == 0) | (Bi % 7 == 0)
        hi = (((Ai + off) % 9 == 1) | (Bi % 7 == 1)) & ~mort
        base[hi] *= 1.1
        if mat == "fin":
            base[mort] = PAL["fin_edge"]
        elif mat == "belly":
            base[mort] = PAL["belly_mortar"]
        else:
            base[mort] = PAL["mortar"]
        if mat == "crust":
            base[(Ai % 4 == 1) & (Bi % 3 == 1)] = PAL["speck"]
        if mat == "belly":
            base[(Ai % 9 == 4) & (Bi % 9 == 4)] = PAL["speck"]
    elif mat == "moss":
        m = np.array(PAL["moss"], float)
        base = m[np.where(((Ai * 3 + Bi * 5) % 7) < 3, 0, 1)]
        dirt = (((np.floor_divide(Ai, 5) + np.floor_divide(Bi, 5)) % 6) == 0) & ((Ai + Bi) % 5 < 2)
        base[dirt] = PAL["dirt"]
    elif mat == "ruin":
        tones = np.array(PAL["ruin"], float)
        row = np.floor_divide(Bi, 4)
        off = (row % 2) * 2
        base = tones[((np.floor_divide(Ai + off, 4)) * 3 + row * 5) % 3]
        base[((Ai + off) % 4 == 0) | (Bi % 4 == 0)] = PAL["ruin_mortar"]
        if kind == "side":
            line = (Bi % 8 == 4) & (Ai % 6 < 4)
            base[line] = PAL["cyan"]
            gm |= line
    elif mat == "glyph":
        base[:] = PAL["cyan"]
        base[Y % 2 == 0] *= 0.85
        gm[:] = True
    elif mat == "eye":
        base[:] = PAL["amber"]
        base[(X == w // 2) & (Y == h // 2)] = PAL["pupil"]
        gm[:] = True
        gcol = PAL["amber"]
    elif mat == "iron":
        tones = np.array(PAL["iron"], float)
        base = tones[((X + Y) // 2) % 2]
        base[Y % 4 == 0] = PAL["iron_hi"]
    elif mat == "bone":
        tones = np.array(PAL["bone"], float)
        base = tones[(X // 3 + Y // 3) % 2]
        base[Y % 3 == 0] *= 0.8
    base = np.clip(base * np.array(tint), 0, 255)
    tex[v:v + h, u:u + w, :3] = base.astype(np.uint8)
    tex[v:v + h, u:u + w, 3] = 255
    if gm.any():
        gv = glow[v:v + h, u:u + w]
        gv[gm, :3] = np.array(gcol, np.uint8)
        gv[gm, 3] = 255


def face_sizes(s):
    sx, sy, sz = s
    return {"up": (sx, sz), "down": (sx, sz), "north": (sx, sy), "south": (sx, sy),
            "east": (sz, sy), "west": (sz, sy)}


def pack(items, S):
    pos, x, y, shelf = {}, 0, 0, 0
    for key, w, h in items:
        if x + w + 1 > S:
            x, y, shelf = 0, y + shelf + 1, 0
        if y + h > S:
            return None
        pos[key] = (x, y)
        x += w + 1
        shelf = max(shelf, h)
    return pos


def make_geo(key, B):
    o = OPTS[key]
    cubes = []
    for name, _, _ in BONES:
        for org, sz, mat in B.cubes.get(name, []):
            cubes.append((name, org, sz, mat))
    items = []
    for ci, (_, org, sz, _) in enumerate(cubes):
        for fname, (w, h) in face_sizes(sz).items():
            items.append(((ci, fname), w, h))
    items.sort(key=lambda it: (-it[2], -it[1]))
    for S in (256, 512, 1024, 2048):
        pos = pack(items, S)
        if pos:
            break
    tex = np.zeros((S, S, 4), np.uint8)
    glow = np.zeros((S, S, 4), np.uint8)
    cube_uv = {}
    for ci, (_, org, sz, mat) in enumerate(cubes):
        uv = {}
        for fname, (w, h) in face_sizes(sz).items():
            u, v = pos[(ci, fname)]
            uv[fname] = {"uv": [u, v], "uv_size": [w, h]}
            kind = {"up": "top", "down": "bottom"}.get(fname, "side")
            P = world_grid(org, sz, fname, w, h)
            paint(tex, glow, u, v, w, h, mat, kind, o["tint"], P)
        cube_uv[ci] = uv

    bones_out = []
    for name, parent, piv in BONES:
        b = {"name": name, "pivot": list(piv)}
        if parent:
            b["parent"] = parent
        b["cubes"] = [{"origin": list(org), "size": list(sz), "uv": cube_uv[ci]}
                      for ci, (bn, org, sz, _) in enumerate(cubes) if bn == name]
        bones_out.append(b)
    geo = {
        "format_version": "1.12.0",
        "minecraft:geometry": [{
            "description": {
                "identifier": "geometry.stone_whale",
                "texture_width": S,
                "texture_height": S,
                "visible_bounds_width": 80,
                "visible_bounds_height": 50,
                "visible_bounds_offset": [0, 20, 0],
            },
            "bones": bones_out,
        }],
    }
    return geo, tex, glow, cubes


def main():
    results, images = {}, {}
    for key in OPTS:
        B = build(key)
        geo, tex, glow, cubes = make_geo(key, B)
        d = OUT / key
        d.mkdir(parents=True, exist_ok=True)
        (d / "stone_whale.geo.json").write_text(json.dumps(geo, indent=1), encoding="utf-8")
        Image.fromarray(tex, "RGBA").save(d / "stone_whale.png", optimize=True)
        Image.fromarray(glow, "RGBA").save(d / "stone_whale_glowmask.png", optimize=True)
        problems = check_overlaps(geo)
        faces = load_geo(geo, tex, glowmask=glow)
        zs = [o[2] for _, o, s, _ in cubes] + [o[2] + s[2] for _, o, s, _ in cubes]
        ys = [o[1] for _, o, s, _ in cubes] + [o[1] + s[1] for _, o, s, _ in cubes]
        xs = [o[0] for _, o, s, _ in cubes] + [o[0] + s[0] for _, o, s, _ in cubes]
        back = [f for f in faces if -70 <= f.origin[2] <= 62 and f.origin[1] >= YC + 8]
        views = {
            "side": render(faces, yaw=90, pitch=0, size=(420, 420)),
            "top": render(faces, yaw=35, pitch=35, size=(420, 420)),
            "front": render(faces, yaw=0, pitch=0, size=(420, 420)),
            "back": render(back, yaw=25, pitch=30, size=(420, 420)),
        }
        for name, im in views.items():
            im.save(d / f"{name}.png")
        images[key] = list(views.values())
        results[key] = dict(
            problems=problems, cubes=len(cubes), tex=tex.shape[0],
            length=(min(zs), max(zs)), width=(min(xs), max(xs)), height=(min(ys), max(ys)),
        )
        print(key, len(cubes), "cubes tex", tex.shape[0], "overlaps", len(problems))
        for p in problems[:10]:
            print("   ", p)
    sht = sheet(
        "岩鯨 stone_whale 3案 (改訂)",
        [(OPTS[k]["name"], images[k]) for k in OPTS],
        "列: 案。行: 横 (側面) / 3/4 上から / 正面 / 背の遺跡の近景",
    )
    sht.save(ROOT / "docs/roughs/stone_whale.png")
    write_md(results)


CONCEPT = {
    "a": ("ザトウクジラ型。太い胴にこぶのある丸い頭、背の苔と石の層。長い胸びれが斜め下へ広がる。"
          "祠へ上る段と、四本柱の列、折れた柱。尾の柄は細く、大きな尾びれ。錨石は尾側の左わき腹。"
          "色は灰青の石、苔の緑、淡い腹に喉の溝。文字は水色。"),
    "b": ("マッコウクジラ型。四角く大きな頭に、重い胴。背の中央に階段を上る中央の塔、両わきに小塔と城壁の足。"
          "下あごは細く、歯の骨が並ぶ。胸びれは小さい。色は少し暗い石で、苔と影を多めにする。"),
    "c": ("シロナガスクジラ型の細身。背に骨の稜線が並び、左右非対称の胸びれ (左が長い)。"
          "背の上には、脚が背に乗る半円のアーチが二つ、細い一本柱。尾びれは左が高い。色は青みの石。"),
}


def write_md(results):
    lines = [
        "# 岩鯨 (stone_whale) 3案のラフ (改訂)",
        "",
        "生成: `tools/rough/stone_whale.py` (乱数なし。再実行で同じ結果)。",
        "出力: `docs/roughs/stone_whale/<a|b|c>/` (geo.json、テクスチャ、光る部分、側面・3/4上から・正面・背の近景)、比較シート `docs/roughs/stone_whale.png`。",
        "",
        "## 共通",
        "",
        "- 頭は -Z、尾は +Z。全長は約 296 単位 (頭の先から尾びれの先まで)。中心の高さは y=40。",
        "- 体は z 方向の厚さ 3 の輪切り。各輪切りは 6 本の帯 (腹・下・側・背) の箱で、面で接するだけ。",
        "- 胸びれ・尾びれ・喉の溝・口の線・背の骨の稜線・遺跡は、輪切りの位置に合わせて面で接させる。",
        "- テクスチャは 1 画素 = 1 モデル単位。模様はワールド座標で決めるので、輪切りをまたいで石の板・苔がつながる。",
        "- 鎖の付け根は z=60 の -X 側 (尾側の左わき腹)。錨石の突起と、垂れる鎖の短い区間だけを含む。鎖の本体はエンティティで描く。",
        "",
    ]
    for key in OPTS:
        r = results[key]
        p = r["problems"]
        lines += [
            f"## {OPTS[key]['name']}",
            "",
            CONCEPT[key],
            "",
            f"- 直方体の数 {r['cubes']}、テクスチャ {r['tex']}x{r['tex']}",
            f"- 寸法 (単位): 長さ z {r['length'][0]}〜{r['length'][1]}、幅 x {r['width'][0]}〜{r['width'][1]}、高さ y {r['height'][0]}〜{r['height'][1]}",
            f"- 重なり検査: {'重なりなし (check_overlaps は空)' if not p else f'{len(p)} 件'}",
            "",
        ]
    lines += ["## 重なり検査の結果", "", "| 案 | 重なり |", "|---|---|"]
    for key in OPTS:
        p = results[key]["problems"]
        lines.append(f"| {OPTS[key]['name']} | {'0 件' if not p else str(len(p)) + ' 件'} |")
    (ROOT / "docs/roughs/stone_whale.md").write_text("\n".join(lines) + "\n", encoding="utf-8")


if __name__ == "__main__":
    main()
