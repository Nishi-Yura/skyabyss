"""items_blocks_32: 採用済みの 16x16 (アイテム9種 + 武器4種 + 防具アイコン4種 + ブロック12面) を
32x32 にする3案 (A/B/C)。形と色 (パレット) は 16x16 の採用案のまま。違いは増えた解像度の使い方だけ。

  A = 忠実な2倍 + 手直し: 斜めのギザギザを Scale2x で直し、外の輪郭を1画素に細くし、
      段の境目に中間色を1段入れ、左上の光で縁を立てる (面取り)。
  B = 材質の描き込み: A に、骨の小穴・石の目・結晶の面の稜線・縄/柄巻の織り目・金属の擦り傷・苔の粒を
      手で置いた小さな型 (スタンプ) で加え、光沢の点を増やす。
  C = 柔らかい塗り: A の明暗を同じ色の範囲の中だけでぼかし、半段ごとの色に 2x2 の規則的な網点
      (中間だけ) で落とす。輪郭は隣の色に寄せた暗色にし、発光の周りをわずかに照らす。

乱数は使わない。色は「元の段の色」「隣り合う段の中間色」「段の上下に1色ずつ足した色」だけを使う
(C の輪郭と発光の照り返しだけは、隣の色との混色)。

実行 (リポジトリ直下):  python tools/rough/items_blocks_32.py
出力:
  docs/roughs/v4/items_blocks/<a|b|c>/item/*.png   (最終ファイル名。防具は stone_whale_<部位>.png)
  docs/roughs/v4/items_blocks/<a|b|c>/block/*.png
  docs/roughs/v4/items_blocks/<a|b|c>/preview/*_tile3x3.png
  docs/roughs/v4/items32.png, weapons32.png, blocks32.png  (列: 現在の16x16 | A | B | C)
"""
from __future__ import annotations

import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "tools/rough"))
from voxel import icon_big, sheet, font  # noqa: E402
from items_blocks import BLOCK_OVERLAY  # noqa: E402  (割れ線の位置)

R = ROOT / "docs/roughs"
OUT = R / "v4"
SUB = OUT / "items_blocks"
STYLES = ["a", "b", "c"]
N = 32


# ---------------------------------------------------------------------------
# 色の道具
# ---------------------------------------------------------------------------
def hx(h):
    h = h.lstrip("#")
    return np.array([int(h[i:i + 2], 16) for i in (0, 2, 4)], float)


def mix(c1, c2, t):
    return np.asarray(c1, float) * (1 - t) + np.asarray(c2, float) * t


def lum(c):
    c = np.asarray(c, float)
    return (0.299 * c[0] + 0.587 * c[1] + 0.114 * c[2]) / 255.0


SHADOW = hx("1a1828")   # 段の下に足す色の向かう先 (青紫の暗色)
LIGHT = hx("fffbec")    # 段の上に足す色の向かう先 (暖かい白)


class Ramp:
    """1つの材質の色の段 (暗い -> 明るい)。ext は半段刻み: [下, c0, c0/c1中間, c1, ..., c_n-1, 上]。"""

    def __init__(self, mat, cols, below=None, above=None):
        self.mat = mat
        self.cols = [hx(c) for c in cols]
        n = len(self.cols)
        b = hx(below) if below else mix(self.cols[0], SHADOW, 0.38)
        a = hx(above) if above else mix(self.cols[-1], LIGHT, 0.5)
        ext = [b]
        for i, c in enumerate(self.cols):
            ext.append(c)
            if i < n - 1:
                ext.append(mix(c, self.cols[i + 1], 0.5))
        ext.append(a)
        self.ext = ext
        self.n = n

    def color(self, s):
        """s は段の番号 (0..n-1)。半段 (x.5) も可。-0.5 と n-0.5 は下と上の足した色。"""
        s = min(max(s, -0.5), self.n - 0.5)
        i = int(round(1 + 2 * s))
        return self.ext[i]


# ---------------------------------------------------------------------------
# 元の 16x16 と、その色 -> (材質の段, 段の番号) の対応 (色を見て手で割り当てた)
# ---------------------------------------------------------------------------
IB = R / "items_blocks/a"
WP = R / "weapons"
AR = R / "armor/a"


def src(kind, name, path, ramps, wrap=False, crack=None, group="items"):
    if kind == "block":
        group = "blocks"
    return dict(kind=kind, name=name, path=path, ramps=ramps, wrap=wrap, crack=crack, group=group)


def O(c):
    return Ramp("outline", [c])


GLOW_RES = ["2fb6e8", "4fd8ff", "7fe8ff", "bff6ff"]
BONE_BLK = ["6f6850", "8f8667", "b3a98a", "d8d0b4", "ece6cc"]

SOURCES = [
    # ---- アイテム 9種
    src("item", "cloud_jerky", IB / "item/cloud_jerky.png",
        [O("3a1b12"), Ramp("meat", ["6a2d1c", "9a4a2f", "b8643f", "d9866a"]),
         Ramp("fat", ["efe3cc"], below="c9b8a0", above="fffaf0")]),
    src("item", "moss_talisman", IB / "item/moss_talisman.png",
        [O("1c1f22"), Ramp("stone", ["41464b", "4a4f54", "6d7176", "b5b9bd"]),
         Ramp("moss", ["2b6a22", "3f8a2e", "4b9a36", "9ad86c"]),
         Ramp("rope", ["7a5a3a"], below="4f3a26", above="a8865c")]),
    src("item", "resonance_charm", IB / "item/resonance_charm.png",
        [O("1b2630"), Ramp("metal", ["4a5560", "8a96a0", "e4ecf2"]),
         Ramp("glow", ["1f6f9a", "3fb8e8", "9ff0ff", "ffffff"])]),
    src("item", "resonance_crystal", IB / "item/resonance_crystal.png",
        [O("16384d"), Ramp("crystal", ["1f5f80", "1d7fb0", "3fc2ee", "8ee6ff", "f0fdff"]),
         Ramp("crystal", ["4b2a86", "7a4fc2"], above="b9a2ff")]),
    src("item", "resonance_shard", IB / "item/resonance_shard.png",
        [O("2a1b4d"), Ramp("crystal", ["4f2d9a", "8a5fe0", "b9a2ff", "f4f0ff"]),
         Ramp("glow", ["7ad8ff"], below="3fa0d0", above="d8f8ff")]),
    src("item", "sky_key_fragment", IB / "item/sky_key_fragment.png",
        [O("2a3038"), Ramp("stone", ["5d6670", "8a929b", "d7dde3"]),
         Ramp("glow", ["3fc8f0"], below="1c7fb2", above="8ff0ff")]),
    src("item", "stone_whale_core", IB / "item/stone_whale_core.png",
        [O("1d2530"), Ramp("stone", ["3c4855", "5e6e7c", "8394a2", "c8d6e0"]),
         Ramp("glow", ["3fd0f5", "c5f8ff"], below="1a6e9a")]),
    src("item", "stone_whale_plating", IB / "item/stone_whale_plating.png",
        [O("2a1c08"), Ramp("gold", ["8a5d1c", "c08a2c", "e6bd5a", "fff1b8"]),
         Ramp("stone", ["2e3440", "4c5566"], above="6b7688"),
         Ramp("glow", ["3fd0f5", "8ae8ff"], below="1a6e9a")]),
    src("item", "stone_whale_shard", IB / "item/stone_whale_shard.png",
        [O("20242a"), Ramp("stone", ["3a4048", "68707a", "8c949e", "c3cad2"]),
         Ramp("glow", ["3fd0f5"], below="1a6e9a", above="8ae8ff")]),
    # ---- 武器 4種 (輪郭なしの細い絵)
    src("item", "whalebone_greatsword", WP / "whalebone_greatsword/a/icon.png",
        [Ramp("grip", ["43301f", "5a4230", "6b4a32", "8a6a4a"]),
         Ramp("bone", ["7a6448", "a88f68", "d9c9a0", "e3d3ac", "fff6dc"]),
         Ramp("glow", ["5fe8ff"], below="2a9fc8", above="d0fbff")], group="weapons"),
    src("item", "captain_pike", WP / "captain_pike/a/icon.png",
        [Ramp("bone", ["6d5d42", "8f7f5e", "9c8b66", "d8cba8", "eee4c8"]),
         Ramp("glow", ["5fe8ff"], below="2a9fc8", above="d0fbff")], group="weapons"),
    src("item", "waterway_fang", WP / "waterway_fang/a/icon.png",
        [Ramp("grip", ["1a2d35", "203540", "2c4a55", "35505a"]),
         Ramp("bone", ["7f8f8a", "c3cfc6", "eef2ea"])], group="weapons"),
    src("item", "bone_spike_arrow", WP / "bone_spike_arrow/a/icon.png",
        [Ramp("wood", ["3b2d21", "cdac7c", "f6e7c4"]),
         Ramp("bone", ["9c9686", "d9d4c7"])], group="weapons"),
    # ---- 防具アイコン 4種 (v1 A のアイコン。採用の v2 B と同じ配色: 石板・鯨骨の帯・水色の結晶)
    *[src("item", f"stone_whale_{p}", AR / f"{p}.png",
          [O("141720"), Ramp("stone", ["2f3847", "55607a"], above="7a86a0"),
           Ramp("bone", ["ece2c8"], below="b8ab8c", above="fffaea"),
           Ramp("glow", ["39d3e6", "c9fbff"], below="1a7f9a")], group="weapons")
      for p in ("helmet", "chestplate", "leggings", "boots")],
    # ---- ブロック 12面 (上下左右がつながる)
    src("block", "crumbling_bone_top", IB / "block/crumbling_bone_top.png", [Ramp("bone", BONE_BLK)], wrap=True),
    src("block", "crumbling_bone_top_cracking", IB / "block/crumbling_bone_top.png", [Ramp("bone", BONE_BLK)],
        wrap=True, crack="crumbling_bone_top_cracking"),
    src("block", "crumbling_bone_side", IB / "block/crumbling_bone_side.png", [Ramp("bone", BONE_BLK)], wrap=True),
    src("block", "crumbling_bone_side_cracking", IB / "block/crumbling_bone_side.png", [Ramp("bone", BONE_BLK)],
        wrap=True, crack="crumbling_bone_side_cracking"),
    src("block", "resonance_stone", IB / "block/resonance_stone.png",
        [Ramp("stone", ["3b4a55"], below="2c3941", above="4d5f6b"), Ramp("glow", GLOW_RES)], wrap=True),
    src("block", "resonance_stone_active", IB / "block/resonance_stone_active.png",
        [Ramp("stone", ["3b4a55"], below="2c3941", above="4d5f6b"), Ramp("glow", GLOW_RES)], wrap=True),
    src("block", "sealed_gate", IB / "block/sealed_gate.png",
        [Ramp("stone", ["2b2636"], below="1d1926", above="3a3448"),
         Ramp("seal", ["4b2a86", "7a4fc2", "9c7ae0"])], wrap=True),
    src("block", "warded_stone", IB / "block/warded_stone.png",
        [Ramp("stone", ["4a4a4a", "7f7f7f", "9a9a9a"]),
         Ramp("glow", ["5fd6ff"], below="2f8fb8", above="bff6ff")], wrap=True),
    *[src("block", f"resonance_forge_{f}", IB / f"block/resonance_forge_{f}.png",
          [Ramp("dark", ["15181c"]), Ramp("metal", ["2f3338", "55595e", "6b7076"]),
           Ramp("seal", ["2a1640"], above="3f2560"), Ramp("glow", GLOW_RES)], wrap=True)
      for f in ("top", "side", "front", "bottom")],
]


# ---------------------------------------------------------------------------
# 16x16 -> 記号の格子 (材質の段の番号 r と段 s)。透明は r = -1。
# ---------------------------------------------------------------------------
def to_symbols(cfg):
    img = np.array(Image.open(cfg["path"]).convert("RGBA"))
    ramps = cfg["ramps"]
    lut = {}
    for ri, rp in enumerate(ramps):
        for si, c in enumerate(rp.cols):
            k = tuple(int(v) for v in c)
            if k in lut:
                raise ValueError(f"{cfg['name']}: color {k} in two ramps")
            lut[k] = (ri, si)
    h, w = img.shape[:2]
    rr = np.full((h, w), -1, int)
    ss = np.zeros((h, w), float)
    for y in range(h):
        for x in range(w):
            p = img[y, x]
            if p[3] < 128:
                continue
            k = tuple(int(v) for v in p[:3])
            if k not in lut:
                raise KeyError(f"{cfg['name']}: no ramp for color #{k[0]:02x}{k[1]:02x}{k[2]:02x}")
            rr[y, x], ss[y, x] = lut[k]
    return rr, ss


def nb(arr, y, x, dy, dx, wrap, fill):
    h, w = arr.shape[:2]
    yy, xx = y + dy, x + dx
    if wrap:
        return arr[yy % h, xx % w]
    if 0 <= yy < h and 0 <= xx < w:
        return arr[yy, xx]
    return fill


def scale2x(rr, ss, wrap, keep=()):
    """Scale2x (EPX)。記号 (r, s) を単位にするので、色が混ざらない。斜めの段差がなめらかになる。
    keep の材質 (輪郭) は中身の色に食われない (斜めの輪郭が切れないように)。"""
    h, w = rr.shape
    key = np.where(rr < 0, -1, rr * 100 + ss.astype(int))
    out = np.zeros((2 * h, 2 * w), int)
    for y in range(h):
        for x in range(w):
            P = key[y, x]
            A = nb(key, y, x, -1, 0, wrap, -1)
            B = nb(key, y, x, 0, 1, wrap, -1)
            C = nb(key, y, x, 0, -1, wrap, -1)
            D = nb(key, y, x, 1, 0, wrap, -1)
            e0 = A if (C == A and C != D and A != B) else P
            e1 = B if (A == B and A != C and B != D) else P
            e2 = C if (D == C and D != B and C != A) else P
            e3 = D if (B == D and B != A and D != C) else P
            if P >= 0 and P // 100 in keep:
                e0, e1, e2, e3 = [P if (e >= 0 and e != P) else e for e in (e0, e1, e2, e3)]
            out[2 * y, 2 * x], out[2 * y, 2 * x + 1] = e0, e1
            out[2 * y + 1, 2 * x], out[2 * y + 1, 2 * x + 1] = e2, e3
    r2 = np.where(out < 0, -1, out // 100)
    s2 = np.where(out < 0, 0, out % 100).astype(float)
    return r2, s2


DIRS4 = [(-1, 0), (0, -1), (1, 0), (0, 1)]


def thin_outline(rr, ss, ramps, wrap):
    """2倍で2画素になった外の輪郭を1画素にする。透明に触れていない内側の半分を、隣の中身の色で置き換える。"""
    if wrap:
        return rr, ss
    h, w = rr.shape
    r2, s2 = rr.copy(), ss.copy()
    for y in range(h):
        for x in range(w):
            r = rr[y, x]
            if r < 0 or ramps[r].mat != "outline":
                continue
            if any(nb(rr, y, x, dy, dx, False, -1) < 0 for dy, dx in DIRS4):
                continue  # 透明に触れている外側の半分は残す
            for dy, dx in DIRS4:
                # 反対側の2画素先までに透明があり、こちら側に中身がある -> 内側の半分
                q = nb(rr, y, x, dy, dx, False, -1)
                far = [nb(rr, y, x, -dy * k, -dx * k, False, -1) for k in (1, 2)]
                if q >= 0 and ramps[q].mat != "outline" and far[0] >= 0 and ramps[far[0]].mat == "outline" \
                        and far[1] < 0:
                    r2[y, x], s2[y, x] = q, ss[y + dy, x + dx]
                    break
    return r2, s2


def draw_cracks(rr, ss, ramps, name):
    """割れ線は 16x16 の位置をつなぐ1画素の線として描く (2倍の太い線にしない)。"""
    rows = BLOCK_OVERLAY[name][1]
    pts = {(x, y) for y, row in enumerate(rows) for x, ch in enumerate(row) if ch == "x"}
    crack_ri = len(ramps)
    ramps.append(Ramp("crack", ["4a3f2c"], below="2e2618", above="6f6850"))
    rr, ss = rr.copy(), ss.copy()

    def plot(x, y):
        rr[y % N, x % N], ss[y % N, x % N] = crack_ri, 0

    for (x, y) in sorted(pts):
        plot(2 * x + 1, 2 * y + 1)
        for dx, dy in ((1, 0), (0, 1), (1, 1), (-1, 1)):
            if (x + dx, y + dy) not in pts:
                continue
            if dx and dy and ((x + dx, y) in pts or (x, y + dy) in pts):
                continue  # 直角の道があれば斜めは引かない
            for k in (1, 2):
                plot(2 * x + 1 + dx * k, 2 * y + 1 + dy * k)
    # 割れ目の左 (光の側の壁) は影になる: 骨の一番暗い段を1画素添える。右の縁は shade_adjust で光る。
    base = rr.copy()
    for y in range(N):
        for x in range(N):
            if base[y, x] == crack_ri and base[y, (x - 1) % N] != crack_ri:
                rr[y, (x - 1) % N], ss[y, (x - 1) % N] = rr[y, (x - 1) % N], 0
    return rr, ss


# ---------------------------------------------------------------------------
# 明暗の手直し (A の芯)。光は左上。
# ---------------------------------------------------------------------------
def shade_adjust(rr, ss, ramps, wrap, facet_glint=False):
    h, w = rr.shape
    adj = np.zeros((h, w))
    for y in range(h):
        for x in range(w):
            r = rr[y, x]
            if r < 0:
                continue
            mp = ramps[r].mat
            if mp in ("outline", "dark", "crack"):
                continue
            s = ss[y, x]
            Lp = lum(ramps[r].color(s))
            a = 0.0
            smooth = 0.0
            for (dy, dx), sign in zip(DIRS4, (1, 1, -1, -1)):
                q = nb(rr, y, x, dy, dx, wrap, -1)
                if q < 0:
                    continue
                sq = nb(ss, y, x, dy, dx, wrap, 0)
                if q == r:
                    ds = sq - s
                    if abs(ds) >= 2:
                        if ds < 0:
                            a += 0.5 * sign            # 段の高い側の縁 (左上は明るく、右下は暗く)
                        elif sign > 0:
                            a -= 0.5                   # 溝の中、左上の段の影
                    elif abs(ds) == 1:
                        if facet_glint and mp in ("crystal", "seal"):
                            if ds < 0:
                                smooth = 0.5           # 結晶: 明るい面の縁を光らせる (稜線)
                        elif ds > 0:
                            if all(nb(rr, y, x, -dy * k, -dx * k, wrap, -1) == r
                                   and nb(ss, y, x, -dy * k, -dx * k, wrap, 0) == s for k in (1, 2)):
                                smooth = 0.5           # 暗い側の境目に中間色を1画素 (広い面だけ)
                else:
                    Lq = lum(ramps[q].color(sq))
                    if Lq < Lp - 0.06:
                        a += 0.5 * sign                # 暗いもの (輪郭・溝) に接する縁
                    elif Lq > Lp + 0.06 and sign > 0 and ramps[q].mat not in ("glow",) \
                            and mp != "glow":
                        a -= 0.5                       # 左上の明るい出っ張りの落とす影
            adj[y, x] = max(-0.5, min(0.5, a)) + smooth
    return np.clip(adj, -0.5, 0.5)


# ---------------------------------------------------------------------------
# B: 材質の型 (手で置いた小さな模様)。'-' 半段暗く  'D' 1段暗く  '+' 半段明るく。
# 大きさは 32 を割り切るもの (ブロックの継ぎ目でずれない)。
# ---------------------------------------------------------------------------
STAMPS = {
    # 骨の小穴: 穴は暗く、右下の内壁が光る
    "bone": [
        "................",
        "..D+............",
        "..+.........D+..",
        "............+...",
        "......-.........",
        "......-.........",
        "..........D+....",
        "...D+.....+.....",
        "...+............",
        "................",
        "........-.......",
        ".D+.....-.......",
        ".+..............",
        "...........D+...",
        "...........+....",
        "................",
    ],
    # 石の目: 短い横の筋と、その上の光る縁
    "stone": [
        "................",
        ".++.......++....",
        ".---......---...",
        "................",
        "......+.........",
        "......--........",
        "..............+.",
        "..............-.",
        "..++............",
        "..---...........",
        "..........+.....",
        "..........---...",
        "................",
        "+...............",
        "-.......++......",
        "........--......",
    ],
    # 干し肉の繊維: 斜めの途切れた筋
    "meat": [
        "-.......",
        ".-......",
        "........",
        "...-..+.",
        "....-...",
        "........",
        ".+....-.",
        "...+...-",
    ],
    # 苔の粒: 盛り上がり (明) と窪み (暗)
    "moss": [
        ".+......",
        "+-+.....",
        ".+...-..",
        ".....+..",
        "...+....",
        "..+-+...",
        "......-.",
        "........",
    ],
    # 縄・柄巻・布の織り目
    "rope": ["+-..", "-+..", "..+-", "..-+"],
    "grip": ["+-..", "-+..", "..+-", "..-+"],
    # 金属の擦り傷と鋲の光
    "metal": [
        "................",
        "..+++...........",
        "................",
        "..........---...",
        "................",
        "................",
        "....-...........",
        ".....+++........",
        "................",
        "...........+....",
        "................",
        "..--............",
        "................",
        "........+++.....",
        "................",
        "................",
    ],
    "gold": [
        "........",
        ".+......",
        "........",
        ".....-..",
        "........",
        "...+....",
        "........",
        "-.......",
    ],
    # 結晶の面の筋 (斜め)
    "crystal": [
        "+.......",
        ".+......",
        "........",
        "........",
        "....-...",
        ".....-..",
        "........",
        "........",
    ],
    "wood": [
        "........",
        "..-.....",
        "........",
        "......+.",
    ],
}


def interior(rr, y, x, wrap):
    r = rr[y, x]
    return all(nb(rr, y, x, dy, dx, wrap, -1) == r for dy, dx in DIRS4)


def interior8(rr, y, x, wrap):
    r = rr[y, x]
    return all(nb(rr, y, x, dy, dx, wrap, -1) == r for dy in (-1, 0, 1) for dx in (-1, 0, 1))


def interior_mat(rr, ramps, y, x, wrap):
    """同じ材質 (段の番号は問わない) に4方向とも囲まれている。"""
    m = ramps[rr[y, x]].mat
    for dy, dx in DIRS4:
        q = nb(rr, y, x, dy, dx, wrap, -1)
        if q < 0 or ramps[q].mat != m:
            return False
    return True


def stamp_adjust(rr, ss, ramps, wrap, offset):
    h, w = rr.shape
    adj = np.zeros((h, w))
    val = {"-": -0.5, "D": -1.0, "+": 0.5, ".": 0.0}
    oy, ox = offset
    for y in range(h):
        for x in range(w):
            r = rr[y, x]
            if r < 0:
                continue
            st = STAMPS.get(ramps[r].mat)
            if not st or not interior_mat(rr, ramps, y, x, wrap):
                continue
            ch = st[(y + oy) % len(st)][(x + ox) % len(st[0])]
            adj[y, x] = val[ch]
    return adj


def specular(rr, st, ramps, wrap):
    """光る材質の一番明るい塊の左上の角に、段の上の色 (光沢の点) を置く。"""
    out = st.copy()
    h, w = rr.shape
    for y in range(h):
        for x in range(w):
            r = rr[y, x]
            if r < 0 or ramps[r].mat not in ("crystal", "glow", "metal", "gold", "bone", "seal"):
                continue
            top = ramps[r].n - 1
            if st[y, x] < top - 0.01:
                continue
            up = nb(rr, y, x, -1, 0, wrap, -1) == r and nb(st, y, x, -1, 0, wrap, 0) >= top - 0.01
            lf = nb(rr, y, x, 0, -1, wrap, -1) == r and nb(st, y, x, 0, -1, wrap, 0) >= top - 0.01
            dn = nb(rr, y, x, 1, 0, wrap, -1) == r and nb(st, y, x, 1, 0, wrap, 0) >= top - 0.01
            rt = nb(rr, y, x, 0, 1, wrap, -1) == r and nb(st, y, x, 0, 1, wrap, 0) >= top - 0.01
            if not up and not lf and (dn or rt):
                out[y, x] = top + 0.5
    return out


# ---------------------------------------------------------------------------
# C: 同じ材質の中だけでぼかし、2x2 の網点で半段の色に落とす
# ---------------------------------------------------------------------------
def soft_field(rr, st, wrap, light_bias):
    h, w = rr.shape
    k = [(dy, dx, wgt) for dy, wy in ((-1, 1), (0, 2), (1, 1)) for dx, wx in ((-1, 1), (0, 2), (1, 1))
         for wgt in [wy * wx]]
    cur = st.copy()
    for _ in range(2):
        nxt = cur.copy()
        for y in range(h):
            for x in range(w):
                r = rr[y, x]
                if r < 0:
                    continue
                num = den = 0.0
                for dy, dx, wgt in k:
                    if nb(rr, y, x, dy, dx, wrap, -1) == r:
                        num += wgt * nb(cur, y, x, dy, dx, wrap, 0)
                        den += wgt
                nxt[y, x] = num / den
        cur = nxt
    out = 0.45 * st + 0.55 * cur
    if light_bias:
        yy, xx = np.mgrid[0:h, 0:w]
        out = out + 0.45 * (0.5 - (xx + yy) / (h + w - 2))
    return out


BAYER2 = np.array([[0.125, 0.625], [0.875, 0.375]])


def dither_quant(rr, field, wrap):
    h, w = rr.shape
    q = np.zeros((h, w))
    for y in range(h):
        for x in range(w):
            v = field[y, x] * 2
            b = np.floor(v)
            f = v - b
            if 0.3 < f < 0.7 and rr[y, x] >= 0 and interior8(rr, y, x, wrap):
                q[y, x] = (b + (1 if f > BAYER2[y % 2, x % 2] else 0)) / 2
            else:
                q[y, x] = np.round(v) / 2
    return q


def render_rgba(rr, st, ramps):
    h, w = rr.shape
    out = np.zeros((h, w, 4), np.uint8)
    for y in range(h):
        for x in range(w):
            r = rr[y, x]
            if r < 0:
                continue
            out[y, x, :3] = np.clip(np.round(ramps[r].color(st[y, x])), 0, 255)
            out[y, x, 3] = 255
    return out


def soften_lines(img, rr, st, ramps, wrap):
    """C: 輪郭・割れ線・炉の黒を、隣の中身の暗い色に寄せる (光の側は多めに寄せる)。"""
    out = img.copy()
    h, w = rr.shape
    for y in range(h):
        for x in range(w):
            r = rr[y, x]
            if r < 0 or ramps[r].mat not in ("outline", "crack", "dark"):
                continue
            best = None
            for (dy, dx), t in zip(DIRS4, (0.25, 0.25, 0.5, 0.5)):
                # 中身が下/右にある -> 輪郭は左上 (光の側) -> 0.5 寄せる
                q = nb(rr, y, x, dy, dx, wrap, -1)
                if q >= 0 and ramps[q].mat not in ("outline", "crack", "dark"):
                    if best is None or t > best[1]:
                        best = (q, t)
            if best is None:
                continue
            q, t = best
            if ramps[r].mat == "outline":
                t *= 0.8
            target = ramps[q].color(0)
            out[y, x, :3] = np.clip(np.round(mix(img[y, x, :3], target, t)), 0, 255)
    return out


def glow_halo(img, rr, ramps, wrap):
    """C: 発光の周り1〜2画素を、発光の中間色にわずかに寄せる (2画素目は網点)。"""
    out = img.copy()
    h, w = rr.shape
    gl = np.array([[rr[y, x] >= 0 and ramps[rr[y, x]].mat == "glow" for x in range(w)] for y in range(h)])
    for y in range(h):
        for x in range(w):
            r = rr[y, x]
            if r < 0 or gl[y, x] or ramps[r].mat in ("outline", "dark"):
                continue
            best = 9
            col = None
            for dy in range(-2, 3):
                for dx in range(-2, 3):
                    yy, xx = (y + dy, x + dx)
                    if wrap:
                        yy, xx = yy % h, xx % w
                    elif not (0 <= yy < h and 0 <= xx < w):
                        continue
                    if gl[yy, xx]:
                        d = max(abs(dy), abs(dx))
                        if d < best:
                            best, col = d, ramps[rr[yy, xx]].color(ramps[rr[yy, xx]].n // 2)
            if col is None:
                continue
            if best == 1:
                t = 0.22
            elif best == 2 and (x + y) % 2 == 0:
                t = 0.12
            else:
                continue
            out[y, x, :3] = np.clip(np.round(mix(img[y, x, :3], col, t)), 0, 255)
    return out


# ---------------------------------------------------------------------------
# 防具アイコン: 採用の v2 B の目印を 32x32 の記号で足す (全案共通)。
#   兜 = 頭頂の段のついた鶏冠、胸 = 中央の結晶を囲む鯨骨の檻 (縦の骨2本と下の横骨)。
# 記号: o 輪郭  s 石板(暗)  S 石板(明)  b 鯨骨  g/G 結晶
# ---------------------------------------------------------------------------
ARMOR_SYM = {"o": (0, 0), "s": (1, 0), "S": (1, 1), "b": (2, 0), "g": (3, 0), "G": (3, 1)}
ARMOR_HINTS = {
    "stone_whale_helmet": {
        2: (15, "oo"),
        3: (14, "oSso"),
        4: (13, "oSSsso"),
        5: (13, "oSssso"),
        6: (13, "oSssso"),
    },
    "stone_whale_chestplate": {
        **{y: (12, "b......b") for y in range(18, 24)},
        24: (12, "bbbbbbbb"),
    },
}


def apply_hint(rr, ss, rows):
    rr, ss = rr.copy(), ss.copy()
    for y, (x0, txt) in rows.items():
        for i, ch in enumerate(txt):
            if ch != ".":
                rr[y, x0 + i], ss[y, x0 + i] = ARMOR_SYM[ch]
    return rr, ss


# ---------------------------------------------------------------------------
# B の武器: 刃や柄の向き (左下 -> 右上) に沿って、一定の間隔で描き込む。
# 規則 = (材質, 対象の段, 周期, 位相, 段の増減, 範囲)。位置は柄の向きの座標 (x - y) で数える。
#   大剣: 骨の刃の中間帯に小穴 (暗) と、最明帯に光の粒
#   船長の槍: 細い柄に巻き (溝 + 光)
#   水路の牙: 牙の成長線 (中間帯を横切る暗い線) と、刃先側の光
#   骨棘の矢: 柄の巻き
# ---------------------------------------------------------------------------
ALL = lambda x, y: True  # noqa: E731
WEAPON_DETAIL = {
    "whalebone_greatsword": [("bone", {3}, 6, 0, -1.0, ALL), ("bone", {4}, 6, 3, 0.5, ALL),
                             ("bone", {1}, 6, 4, -0.5, ALL)],
    "captain_pike": [("bone", {0, 1, 2, 3, 4}, 6, 0, -1.0, lambda x, y: x < 19),
                     ("bone", {0, 1, 2, 3, 4}, 6, 1, 0.5, lambda x, y: x < 19)],
    "waterway_fang": [("bone", {1}, 4, 0, -1.0, ALL), ("bone", {2}, 4, 2, 0.5, ALL)],
    "bone_spike_arrow": [("wood", {1, 2}, 6, 0, -0.5, lambda x, y: x < 24),
                         ("wood", {1}, 6, 1, 0.5, lambda x, y: x < 24)],
}


def weapon_detail(rr, ss, st, ramps, name):
    out = st.copy()
    h, w = rr.shape
    for mat, steps, per, ph, d, where in WEAPON_DETAIL.get(name, []):
        for y in range(h):
            for x in range(w):
                r = rr[y, x]
                if r < 0 or ramps[r].mat != mat or int(ss[y, x]) not in steps or not where(x, y):
                    continue
                if (x - y) % per == ph:
                    out[y, x] += d
    return out


# ---------------------------------------------------------------------------
# 1枚を作る
# ---------------------------------------------------------------------------
def build(cfg, style):
    ramps = list(cfg["ramps"])
    wrap = cfg["wrap"]
    r16, s16 = to_symbols(cfg)
    keep = tuple(i for i, rp in enumerate(ramps) if rp.mat == "outline")
    rr, ss = scale2x(r16, s16, wrap, keep)
    if cfg["name"] in ARMOR_HINTS:
        rr, ss = apply_hint(rr, ss, ARMOR_HINTS[cfg["name"]])
    rr, ss = thin_outline(rr, ss, ramps, wrap)
    if cfg["crack"]:
        rr, ss = draw_cracks(rr, ss, ramps, cfg["crack"])
    if style == "a":
        st = ss + shade_adjust(rr, ss, ramps, wrap)
        return render_rgba(rr, st, ramps)
    if style == "b":
        st = ss + shade_adjust(rr, ss, ramps, wrap, facet_glint=True)
        off = (sum(map(ord, cfg["name"])) % 5, sum(map(ord, cfg["name"])) % 7) if not wrap else (0, 0)
        st = st + stamp_adjust(rr, ss, ramps, wrap, off)
        st = weapon_detail(rr, ss, st, ramps, cfg["name"])
        st = specular(rr, st, ramps, wrap)
        return render_rgba(rr, st, ramps)
    # c
    st = ss + shade_adjust(rr, ss, ramps, wrap)
    field = soft_field(rr, st, wrap, light_bias=not wrap)
    q = dither_quant(rr, field, wrap)
    img = render_rgba(rr, q, ramps)
    img = soften_lines(img, rr, q, ramps, wrap)
    img = glow_halo(img, rr, ramps, wrap)
    return img


def save(arr, path):
    path.parent.mkdir(parents=True, exist_ok=True)
    Image.fromarray(arr, "RGBA").save(path)


def orig16(cfg):
    img = np.array(Image.open(cfg["path"]).convert("RGBA"))
    if cfg["crack"]:
        img = np.array(Image.open(IB / "block" / f"{cfg['name']}.png").convert("RGBA"))
    return img


# ---------------------------------------------------------------------------
# 比較シート
# ---------------------------------------------------------------------------
BG = (18, 18, 26)


def item_cell(arr):
    """拡大 (16 は8倍、32 は4倍 = 同じ大きさ) と、GUI 倍率4 相当 (64px) の実寸見本。"""
    big = icon_big(arr, px=128 // arr.shape[0])
    small = Image.fromarray(arr, "RGBA").resize((64, 64), Image.NEAREST)
    cell = Image.new("RGB", (128 + 12 + 64, 128), BG)
    cell.paste(big, (0, 0))
    plate = Image.new("RGB", (64, 64), (139, 139, 139))  # インベントリの枠の灰色
    plate.paste(small, (0, 0), small)
    cell.paste(plate, (140, 32))
    return cell


def block_cell(arr):
    big = Image.fromarray(arr, "RGBA").convert("RGB").resize((128, 128), Image.NEAREST)
    t = np.tile(arr, (3, 3, 1))
    tile = Image.fromarray(t, "RGBA").convert("RGB").resize((192, 192), Image.NEAREST)
    cell = Image.new("RGB", (128 + 12 + 192, 192), BG)
    cell.paste(big, (0, 32))
    cell.paste(tile, (140, 0))
    return cell


def labeled(img, text):
    out = Image.new("RGB", (img.width, img.height + 22), BG)
    out.paste(img, (0, 22))
    ImageDraw.Draw(out).text((0, 0), text, font=font(15), fill=(170, 170, 190))
    return out


def make_sheet(group, path, title, note):
    cfgs = [c for c in SOURCES if c["group"] == group] if group != "blocks" else \
        [c for c in SOURCES if c["kind"] == "block"]
    cell = block_cell if group == "blocks" else item_cell
    cols = [("現在の 16x16", [labeled(cell(orig16(c)), c["name"]) for c in cfgs])]
    for s in STYLES:
        ims = []
        for c in cfgs:
            arr = np.array(Image.open(SUB / s / c["kind"] / f"{c['name']}.png").convert("RGBA"))
            ims.append(labeled(cell(arr), c["name"]))
        cols.append((s.upper() + {"a": "  忠実2倍+手直し", "b": "  材質の描き込み", "c": "  柔らかい塗り"}[s], ims))
    sheet(title, cols, note).save(path)


def main():
    for s in STYLES:
        for c in SOURCES:
            arr = build(c, s)
            assert arr.shape[:2] == (N, N)
            save(arr, SUB / s / c["kind"] / f"{c['name']}.png")
            if c["kind"] == "block":
                t = np.tile(arr, (3, 3, 1))
                p = SUB / s / "preview" / f"{c['name']}_tile3x3.png"
                p.parent.mkdir(parents=True, exist_ok=True)
                Image.fromarray(t, "RGBA").resize((96 * 4, 96 * 4), Image.NEAREST).save(p)
    make_sheet("items", OUT / "items32.png", "アイテム 9種: 16x16 -> 32x32 (左=拡大、右=GUI倍率4の見本)",
               "列: 現在の16x16 / A 忠実2倍+手直し / B 材質の描き込み / C 柔らかい塗り。拡大は同じ大きさにそろえた")
    make_sheet("weapons", OUT / "weapons32.png", "武器4種 + 防具アイコン4種: 16x16 -> 32x32",
               "列: 現在の16x16 / A / B / C。防具は v1 A のアイコンを元にした (配色は採用の v2 B と同じ)")
    make_sheet("blocks", OUT / "blocks32.png", "ブロック 12面: 16x16 -> 32x32 (左=1面、右=3x3 で継ぎ目の確認)",
               "列: 現在の16x16 / A / B / C")
    print("done")


if __name__ == "__main__":
    main()
