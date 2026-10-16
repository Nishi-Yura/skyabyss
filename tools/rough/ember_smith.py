"""中ボス「灰の鍛え手」(ash_smith) のラフ 3 案。4 番目の巨人 (燠と溶岩と灰の火蜥蜴) の体内、
消えた灰の民の大鍛冶場を守る、背を丸めた大きな鍛冶師。背に鞴 (ふいご) を肺のように背負う。

実行: リポジトリ直下で  PYTHONIOENCODING=utf-8 python -B tools/rough/ember_smith.py
出力:
  docs/review/ember_roughs/ash_smith/<a|b|c>/{geo.json, texture.png, glowmask.png}
      {front34, side, back, windup}.png   (確認の絵。windup は骨を回した「鎚を振り上げた」姿)
      hammer/ tongs/                      (鎚と火箸は別のモデル。geo.json, texture.png, glowmask.png, render.png)
  docs/review/ember_roughs/ash_smith/stats.json
  docs/review/ember_roughs/ash_smith.png  (3 案の比較シート)

座標: y が上、前は +Z、床は y=0、1 単位 = 1/16 ブロック。高さ約 72 単位 (4.5 ブロック) を実寸で作る。
  右手 (-X、骨 _r) に大鎚、左手 (+X、骨 _l) に火箸。武器は体に焼き込まず、受け口の骨
  socket_main (forearm_r の子) と socket_off (forearm_l の子) に付ける別の geo。
  武器の geo は受け口の点を原点にして作る (原点 = 握りの中心)。前 +Z・上 +Y は体と同じ向き。

体つき (2 周目): 強い猫背 (背の瘤が一番高く、頭は肩の線より前・下へ、首は前へ突き出す)、
  樽の胸から細い腰へ絞る、上腕より太い前腕と拳、短く太い脚で膝を前へ曲げる。
  肌は煤けた赤茶の灰色 (上の面に灰の白、まれに汗の光)、前掛けは明るい革で腰から膝下へ段になって垂れ、裾はぎざぎざ。

大鎚の待機の姿: 鎚の頭を床に突き、柄を斜めに握る。受け口 socket_main に静止の回転 (前へ 45 度) を持たせ、
  頭の後ろ下の角を 45 度の面取りにして、その面がちょうど床に載るように柄の長さを決めている。
  geo.json に書く回転は GeckoLib の決まり (X と Z の符号を反転して書くと意図どおりに見える) に合わせて (-x, y, -z)。
  確認の絵は意図した回転 (SOCKET_REST) で描く。

作り方 (tide_caller と同じ):
- 箱は 0.5 単位の格子。先に置いた箱が優先し、後の箱は引かれて重ならない (tide_caller.Builder)。
- 浮き彫り (鋲・帯・板) は先に置いて、後の本体の箱を削る。穴は "void" の箱を先に置いて最後に外す。
- 曲面は段で近似する。6 面とも他の箱に覆われた箱は外す (埋まり 0)。
- 光る画素は光る材料だけ: 燠 (ember)・溶けた金属 (molten)・熱した鎚の面 (hot)。
"""
from __future__ import annotations

import copy
import json
import math
import sys
from collections import Counter
from pathlib import Path

import numpy as np
from PIL import Image

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
import voxel as vx  # noqa: E402
import tide_mobs as tm  # noqa: E402
import tide_caller as tc  # noqa: E402

ROOT = vx.ROOT
REVIEW = ROOT / "docs" / "review" / "ember_roughs"
OUT = REVIEW / "ash_smith"
SHEET = REVIEW / "ash_smith.png"
OPTIONS = ("a", "b", "c")
OPTION_NAME = {"a": "A 鞴の巨人", "b": "B 鉱滓の鎧", "c": "C 炉を背負う者"}
VIEW = (440, 440)
VIEWS = (("front34", 205, 14), ("side", 270, 6), ("back", 28, 20))
SOCKET_REST = (-45.0, 0.0, 0.0)  # 大鎚の受け口の静止の回転 (意図した値。前へ 45 度倒す)

# ---------------------------------------------------------------- 材料 (tide_mobs の色表に足す。ファイルは変えない)

RAMP = {
    "skin": ("#3E2C27", "#634740", "#866253", "#A6826E"),      # 煤けた赤茶の灰色の肌
    "skin_dk": ("#2C211F", "#4A3732", "#6A5046", "#86695B"),   # 煤の濃い手と前腕
    "soot": ("#141011", "#231C1C", "#382C2A", "#4E3E39"),
    "cloth": ("#2B2420", "#41362F", "#5A4B40", "#726053"),     # 脚の厚手の股引
    "apron": ("#5C3920", "#87552F", "#AD7341", "#C8925C"),     # 明るい革の前掛け
    "iron": ("#3A3B3F", "#5C5E63", "#84878C", "#ABAEB2"),
    "iron_dk": ("#202125", "#33353A", "#4A4C52", "#63656B"),
    "rust": ("#4A2213", "#7A3A1E", "#A4552C", "#C77846"),
    "bronze": ("#5A3B17", "#8E6328", "#BE8D3E", "#E0B565"),
    "leather": ("#2E1C12", "#4C2F1D", "#6B442B", "#8A5D3D"),
    "haft": ("#3A2414", "#5A3920", "#7A5130", "#966A42"),
    "ash": ("#8C8783", "#B3ADA6", "#D3CEC6", "#EEEAE3"),
    "smoke": ("#4D4A49", "#6E6A68", "#8F8A86", "#B0AAA4"),
    "slag": ("#231D1A", "#3D342E", "#584B43", "#74645A"),
    "brick": ("#4A1E14", "#6E2E1E", "#94452B", "#B4613F"),
    "ember": ("#8A2A06", "#D2560E", "#F48A22", "#FFC15A"),
    "molten": ("#D27A12", "#F6B52A", "#FFE27A", "#FFF8DA"),
    "hot": ("#A8320A", "#E8661A", "#FFA640", "#FFD58A"),
    "void": ("#000000",) * 4,
}
GLOW = {"ember", "molten", "hot"}
tm.RAMP.update(RAMP)
tm.GLOW_MATS.update(GLOW)


def h01(X, Y, Z):
    """位置で決まる 0..1 の擬似乱数 (tm.h01 は下位ビットが規則的で点が格子に並ぶので、混ぜ直す)。"""
    i, j, k = int(np.floor(X * 2)), int(np.floor(Y * 2)), int(np.floor(Z * 2))
    h = ((i * 73856093) ^ (j * 19349663) ^ (k * 83492791)) & 0xFFFFFFFF
    h = (h ^ (h >> 13)) * 1274126177 & 0xFFFFFFFF
    h ^= h >> 16
    return (h & 0xFFFF) / 65536.0


def ash_style(X, Y, Z, f, mat):
    """材料ごとの模様。None なら面の明るさだけで塗る (tm.shade)。"""
    r = RAMP.get(mat)
    if r is None:
        return None
    h = h01(X, Y, Z)
    if mat in ("skin", "skin_dk"):
        if f == "up" and h < 0.22:
            return RAMP["ash"][2] if h < 0.1 else RAMP["ash"][0]     # 肩や腕の上に積もった灰
        if h > 0.988:
            return "#F3E6D3"                                          # 汗の光 (光らない、明るい点)
        if h < 0.035:
            return RAMP["ash"][1]
        if 0.5 < h < 0.56:
            return r[0]                                               # 煤のしみ
        return None
    if mat == "soot":
        if h < 0.03:
            return r[3]
        return "#3E2116" if h > 0.985 else None
    if mat == "cloth":
        return r[0] if h < 0.08 else (RAMP["ash"][0] if h > 0.985 else None)
    if mat == "apron":
        if f not in ("up", "down") and int(np.floor(Y * 2)) % 8 == 0:
            return r[0]                                               # 縫い目
        if h > 0.93:
            return "#3A2416"                                          # 焦げ
        return r[3] if h < 0.04 else None
    if mat in ("iron", "iron_dk"):
        if h < 0.06:
            return RAMP["rust"][1]
        return r[3] if h > 0.96 else None
    if mat == "rust":
        return r[0] if h < 0.12 else (r[3] if h > 0.94 else None)
    if mat == "slag":
        if h < 0.14:
            return r[0]
        if h < 0.20:
            return RAMP["rust"][1]
        return r[3] if h > 0.94 else None
    if mat == "leather":
        if f not in ("up", "down") and int(np.floor(Y * 2)) % 6 == 0:
            return r[0]
        return r[3] if h > 0.96 else None
    if mat == "haft":
        return r[0] if int(np.floor((X + Z) * 2)) % 5 == 0 else None
    if mat == "bronze":
        return r[3] if h < 0.025 else (r[0] if h > 0.98 else None)
    if mat == "brick":
        row = int(np.floor(Y / 2.0))
        u = X if f in ("north", "south", "up", "down") else Z
        if (Y % 2.0) < 0.5 or ((u + 2.0 * (row % 2)) % 4.0) < 0.5:
            return "#2A2220"
        return r[2] if h < 0.08 else None
    if mat == "ash":
        return r[0] if h < 0.12 else None
    if mat == "smoke":
        return r[3] if h < 0.15 else (r[0] if h > 0.85 else None)
    if mat == "ember":
        return r[3] if h < 0.25 else (r[2] if h < 0.6 else None)
    if mat == "molten":
        return r[3] if h < 0.45 else r[2]
    if mat == "hot":
        return r[3] if h < 0.2 else (r[2] if h < 0.6 else None)
    return None


# ---------------------------------------------------------------- 箱の組み立て

class SB(tc.Builder):
    """tide_caller.Builder に、角を落とした板・左右の鏡写し・骨をまたいだ削りの記録を足したもの。"""

    def __init__(self):
        super().__init__()
        self.cross = Counter()

    def box(self, bone, lo, hi, mat):
        lo = [float(v) for v in lo]
        hi = [float(v) for v in hi]
        pieces = [(lo, hi)]
        for b2, l2, h2, m2 in self.items:
            nxt = []
            for p in pieces:
                r = tc._subtract(p, (l2, h2))
                if not (len(r) == 1 and r[0] is p) and b2 != bone and "void" not in (m2, mat):
                    self.cross[f"{bone}<-{b2}"] += 1
                nxt += r
            pieces = nxt
        for l, h in pieces:
            if all(h[a] - l[a] > 1e-9 for a in range(3)):
                self.items.append((bone, l, h, mat))

    def slab(self, bone, x0, x1, y0, y1, z0, z1, mat, ch=0.0, round_=False):
        """角 (XZ の 4 隅) を ch だけ落とした板 (十字の 2 箱。round_ なら中間の段も足して丸くする)。"""
        if ch > 0 and x1 - x0 > 2 * ch and z1 - z0 > 2 * ch:
            self.box(bone, (x0 + ch, y0, z0), (x1 - ch, y1, z1), mat)
            self.box(bone, (x0, y0, z0 + ch), (x1, y1, z1 - ch), mat)
            if round_:
                hc = math.floor(ch) / 2
                if hc >= 0.5:
                    self.box(bone, (x0 + hc, y0, z0 + hc), (x1 - hc, y1, z1 - hc), mat)
        else:
            self.box(bone, (x0, y0, z0), (x1, y1, z1), mat)

    def s(self, bone, s, a, b, y0, y1, z0, z1, mat, ch=0.0, round_=False):
        """x が s 側の a..b (s<0 なら -b..-a) の板。"""
        x0, x1 = (a, b) if s > 0 else (-b, -a)
        self.slab(bone, x0, x1, y0, y1, z0, z1, mat, ch, round_)

    def both(self, bone, a, b, y0, y1, z0, z1, mat, ch=0.0, round_=False):
        for s in (-1, 1):
            self.s(bone, s, a, b, y0, y1, z0, z1, mat, ch, round_)

    def solid(self):
        return [it for it in self.items if it[3] != "void"]


def hidden_flags(items):
    """6 面すべてが他の箱に覆われた箱 (tm.buried_count と同じ調べ方)。"""
    lo = np.array([it[1] for it in items], float)
    hi = np.array([it[2] for it in items], float)
    flags = []
    for i in range(len(items)):
        hidden = True
        for ax in range(3):
            for side in (0, 1):
                plane = hi[i, ax] + 0.25 if side else lo[i, ax] - 0.25
                a1, a2 = [k for k in range(3) if k != ax]
                g1 = np.arange(lo[i, a1] + 0.25, hi[i, a1], 0.5)
                g2 = np.arange(lo[i, a2] + 0.25, hi[i, a2], 0.5)
                M1, M2 = np.meshgrid(g1, g2, indexing="ij")
                P = np.zeros((M1.size, 3))
                P[:, ax] = plane
                P[:, a1] = M1.ravel()
                P[:, a2] = M2.ravel()
                near = (lo[:, ax] <= plane) & (hi[:, ax] >= plane)
                near[i] = False
                L, H = lo[near], hi[near]
                if not len(L):
                    hidden = False
                    break
                inside = ((P[:, None, :] > L[None] + 1e-6) & (P[:, None, :] < H[None] - 1e-6)).all(-1)
                if not inside.any(1).all():
                    hidden = False
                    break
            if not hidden:
                break
        flags.append(hidden)
    return flags


def to_model(ident, B, bones):
    """bones = [(名前, 支点, 親, 回転 or None)]。"""
    items = B.solid()
    fl = hidden_flags(items)
    items = [it for it, h in zip(items, fl) if not h]
    m = tm.Model(ident)
    m.extra = ash_style
    for name, piv, parent, *rot in bones:
        m.bone(name, piv, parent)
        if rot and rot[0] is not None:
            m.bones[-1]["rotation"] = [float(v) for v in rot[0]]
    for bone, lo, hi, mat in items:
        m.box(bone, *lo, *hi, mat)
    return m, sum(fl)


# ---------------------------------------------------------------- 共通の部品

def barrel(B, bone, rows, mat):
    """樽の胸: rows = [(y0, y1, 半幅 x, 背の z, 胸の z, 角)]。角は 2 段で丸める。"""
    for y0, y1, hw, zb, zf, ch in rows:
        B.slab(bone, -hw, hw, y0, y1, zb, zf, mat, ch, round_=True)


def front_z(rows, y):
    for y0, y1, hw, zb, zf, ch in rows:
        if y0 <= y < y1:
            return zf
    raise ValueError(y)


def legs(B, x0, x1, armour=False):
    """短く太い脚。膝を前へ曲げる (腿は前下へ、すねは後ろ下へ)。腿の上 y=22。"""
    for s, bone in ((-1, "leg_r"), (1, "leg_l")):
        S = lambda *a, **k: B.s(bone, s, *a, **k)  # noqa: E731
        S(x0 - 0.5, x1 + 0.5, 0, 1, -6, 12.5, "iron_dk")
        S(x0 + 0.5, x1 - 0.5, 1, 5, 9, 12.5, "iron")                 # つま先の鉄
        S(x0, x1, 1, 6, -5.5, 9, "leather", ch=1)
        S(x0 - 0.5, x1 + 0.5, 6, 8, -5.5, 7, "leather", ch=1.5)      # 長靴の折り返し
        if armour:
            S(x0 + 0.5, x1 - 0.5, 8, 12, 5, 6.5, "slag")             # すね当て
            S(x0 - 0.5, x1 + 0.5, 17, 21, -4.5, 8, "slag", ch=2)     # 腿の板
        S(x0 + 1, x1 - 1, 8, 12, -5, 5, "cloth", ch=1)               # すね (後ろへ)
        S(x0 + 2, x1 - 2, 12, 17, 8, 10, "iron")                     # 膝当て (前へ出た膝)
        S(x0 + 0.5, x1 - 0.5, 12, 17, -3.5, 8, "cloth", ch=1.5)      # 膝
        S(x0, x1, 17, 22, -5, 7, "cloth", ch=1.5, round_=True)       # 腿


def waist(B, hw, belt_z=(-9, 10)):
    """細い腰と革の帯 (骨 belly)。帯 y 28..31。"""
    B.box("belly", (-2.5, 27.5, belt_z[1]), (2.5, 31.5, belt_z[1] + 1), "bronze")   # 留め金
    B.slab("belly", -hw - 1, hw + 1, 28, 31, belt_z[0], belt_z[1], "leather", 2.5)
    B.slab("belly", -hw, hw, 22, 28, belt_z[0] + 1, belt_z[1] - 2, "cloth", 2.5, round_=True)
    B.slab("belly", -hw + 0.5, hw - 0.5, 31, 32, belt_z[0] + 1, belt_z[1] - 1, "skin", 2.5)


def apron(B, kind, w=10.5):
    """前掛け (骨 apron): 腰から膝下まで 3 段で前へ張り出して垂れ、裾はぎざぎざ。"""
    a = "apron"
    if kind == "a":
        plates = [(-9, -1, 16, 21, 11.5, 12.5), (1, 9, 16, 21, 11.5, 12.5), (-9.5, -1, 9, 14, 12, 13), (1, 9.5, 9, 14, 12, 13)]
    elif kind == "b":
        plates = [(-9.5, -0.5, 23, 27.5, 10, 11), (0.5, 9.5, 23, 27.5, 10, 11),
                  (-10, -0.5, 15.5, 21.5, 11.5, 12.5), (0.5, 10, 15.5, 21.5, 11.5, 12.5),
                  (-10.5, -0.5, 8.5, 14.5, 12, 13), (0.5, 10.5, 8.5, 14.5, 12, 13)]
    else:
        plates = [(-5, 5, 15.5, 21, 11.5, 12.5)]
    for x0, x1, y0, y1, z0, z1 in plates:
        for rx, ry in ((x0 + 1, y1 - 1.5), (x1 - 2, y1 - 1.5), (x0 + 1, y0 + 0.5), (x1 - 2, y0 + 0.5)):
            B.box(a, (rx, ry, z1), (rx + 1, ry + 1, z1 + 0.5), "bronze")
    for x0, x1, y0, y1, z0, z1 in plates:
        B.box(a, (x0, y0, z0), (x1, y1, z1), "slag" if kind != "c" else "iron")
    B.box(a, (-w, 22, 8), (w, 28, 10), "apron")                  # 上の段 (腰に沿う)
    B.box(a, (-w - 0.5, 15, 10), (w + 0.5, 22, 11.5), "apron")   # 中の段 (前へ一段)
    B.box(a, (-w - 1, 8, 10.5), (w + 1, 15, 12), "apron")        # 下の段 (膝の前)
    hem = [(-1, -0.72, 5.5), (-0.72, -0.5, 7), (-0.5, -0.25, 6), (-0.25, -0.03, 7.5),
           (-0.03, 0.22, 5.5), (0.22, 0.45, 6.5), (0.45, 0.72, 7), (0.72, 1, 6)]
    W = w + 1
    for u0, u1, yb in hem:
        x0 = round(u0 * W * 2) / 2
        x1 = round(u1 * W * 2) / 2
        mat = "ash" if (kind == "c" and yb < 6.5) else "apron"
        B.box(a, (x0, yb, 10.5), (x1, 8, 12), mat)


def fist(B, s, bone, cx, cz, fy, hw, hh, hz, kind):
    """大きな拳。kind="hammer": 穴なし (柄は斜めに拳を貫く)、kind="tongs": 中央に前後の穴 (3 x 2)。
    戻り値: 受け口の点 (拳の中心)。"""
    X = lambda a, b: (a, b) if s > 0 else (-b, -a)  # noqa: E731
    if kind == "tongs":
        a, b = X(cx - 1.5, cx + 1.5)
        B.box(bone, (a, fy - 1, cz - hz - 1), (b, fy + 1, cz + hz + 1), "void")
    # 外側の拳の節 (4 つ)、前の指の筋 (3 本)、上の親指
    for k in range(4):
        z0 = cz - hz + 1 + k * (2 * hz - 2) / 4
        z0 = round(z0 * 2) / 2
        a, b = X(cx + hw, cx + hw + 0.5)
        B.box(bone, (a, fy - 2.5, z0), (b, fy + 2.5, z0 + 1.5), "skin_dk")
    if kind == "hammer":
        for y0 in (fy - 3.5, fy - 1, fy + 1.5):
            a, b = X(cx - hw + 1.5, cx + hw - 1.5)
            B.box(bone, (a, y0, cz + hz), (b, y0 + 1.5, cz + hz + 0.5), "skin_dk")
    a, b = X(cx - hw + 0.5, cx + 1)
    B.box(bone, (a, fy + hh, cz - 1), (b, fy + hh + 1, cz + hz - 1.5), "skin_dk")
    a, b = X(cx - hw, cx + hw)
    B.slab(bone, a, b, fy - hh, fy + hh, cz - hz, cz + hz, "skin_dk", ch=2, round_=True)
    return (X(cx, cx)[0], fy, cz)


def arm(B, s, P):
    """腕: 肩の玉・細めの上腕・肘・太い前腕 (腕輪)・手首・大きな拳。P は寸法の辞書。戻り値: 受け口の点。"""
    ab, fb = ("arm_r", "forearm_r") if s < 0 else ("arm_l", "forearm_l")
    cx, cz = P["cx"], P["cz"]

    def S(bone, hw, y0, y1, hz, mat, ch=0, r=False):
        B.s(bone, s, cx - hw, cx + hw, y0, y1, cz - hz, cz + hz, mat, ch, r)
    if P.get("pauldron"):
        B.s(ab, s, cx - 5.5, cx + 7.5, 52, 56, cz - 9.5, cz + 8.5, "rust", ch=3)
        B.s(ab, s, cx - 5.5, cx + 7, 56, 62, cz - 9, cz + 8, "slag", ch=3, round_=True)
        B.s(ab, s, cx - 4, cx + 4, 62, 63, cz - 6, cz + 5, "slag", ch=1.5)
        for zz in (cz - 6, cz, cz + 6):
            B.s(ab, s, cx + 7, cx + 7.5, 58.5, 59.5, zz - 0.5, zz + 0.5, "bronze")
    if P.get("armlet"):
        S(ab, P["up_hw"] + 0.5, P["armlet"], P["armlet"] + 1.5, P["up_hw"] + 0.5, "bronze", 1.5)
    S(ab, P["sh_hw"], P["sh"][0], P["sh"][1], P["sh_hw"] + 0.5, "skin", 2.5, True)
    S(ab, P["up_hw"], P["up"][0], P["up"][1], P["up_hw"] + 0.5, "skin", 1.5)
    S(fb, P["el_hw"], P["el"][0], P["el"][1], P["el_hw"], "skin", 2)
    for y0, y1 in P.get("bracers", ()):
        S(fb, P["fa_hw"] + 1, y0, y1, P["fa_hz"] + 1, P.get("bracer_mat", "bronze"), 3)
    S(fb, P["fa_hw"] - 1, P["fa"][1] - P["fa_split"], P["fa"][1], P["fa_hz"] - 1, "skin_dk", 2.5, True)
    S(fb, P["fa_hw"], P["fa"][0], P["fa"][1] - P["fa_split"], P["fa_hz"], "skin_dk", 3, True)
    S(fb, P["fi_hw"] - 0.5, P["fy"] + P["fi_hh"], P["fa"][0], P["fi_hz"] - 0.5, "leather", 2)
    kind = "hammer" if s < 0 else "tongs"
    return fist(B, s, fb, cx, cz, P["fy"], P["fi_hw"], P["fi_hh"], P["fi_hz"], kind)


ARM_AB = dict(cx=27, cz=5, sh=(50, 60), sh_hw=6, up=(45, 50), up_hw=4.5, el=(42, 45), el_hw=5.5,
              fa=(33, 42), fa_hw=7.5, fa_hz=8, fa_split=4, bracers=((35.5, 37.5),),
              fy=27, fi_hw=6.5, fi_hh=5, fi_hz=6.5)
ARM_B = dict(ARM_AB, cx=28, pauldron=True, bracer_mat="iron", bracers=((34, 35.5), (38.5, 40)))
ARM_C = dict(cx=25, cz=5, sh=(50, 59), sh_hw=5, up=(41, 50), up_hw=4, el=(38, 41), el_hw=5, armlet=46,
             fa=(30, 38), fa_hw=6.5, fa_hz=7, fa_split=3, bracers=((33, 35),), bracer_mat="leather",
             fy=24, fi_hw=6, fi_hh=5, fi_hz=6)

ROWS_AB = [(32, 36, 14, -8, 10, 3), (36, 40, 17, -9, 13, 4), (40, 44, 19.5, -8.5, 16, 4),
           (44, 48, 21, -7.5, 18, 5), (48, 52, 21, -6, 19.5, 5), (52, 55, 19.5, -4.5, 20, 4),
           (55, 58, 16, -3, 19, 4)]
ROWS_C = [(32, 36, 12.5, -8, 9, 3), (36, 40, 15, -8.5, 12, 3), (40, 44, 17, -8, 14, 4),
          (44, 48, 18, -7, 16, 4), (48, 52, 18, -5.5, 17, 4), (52, 55, 16.5, -4, 17, 4),
          (55, 58, 13.5, -3, 16, 3)]


def shoulders_hump(B, mat_hump="skin", traps_hw=21):
    B.both("body", 9, traps_hw, 52, 60, -3, 14, "skin", ch=2.5, round_=True)    # 僧帽 (肩の線 y=60)
    B.slab("body", -13, 13, 58, 62, -6, 12, mat_hump, 3, round_=True)          # 丸めた背の瘤
    B.slab("body", -9, 9, 62, 65, -5, 7, mat_hump, 2.5, round_=True)


def bellows(B, bone, rows, sx, layers, hole, board="bronze", mount=(-2.0, 60.0), grow=0.5, studs=True):
    """鞴 (蛇腹)。rows = [(y0, y1, a, b)] は +X 側の形 (sx<0 なら鏡写し)。
    layers = [(z0, z1, 種類)]、種類 L = 革の蛇腹 (少し膨らむ)、S = 燠の継ぎ目 (1.5 引っ込む)、B = 板。
    hole = (x0, x1, y0, y1): 板の弁の穴。穴の下は白熱 (molten)、鉄の格子。弱点として背から見える。"""
    X = lambda a, b: (a, b) if sx > 0 else (-b, -a)  # noqa: E731
    bz = [l for l in layers if l[2] == "B"][0]
    under = [l for l in layers if l[2] == "S"][-1]
    hx0, hx1, hy0, hy1 = hole
    a, b = X(hx0, hx1)
    zl, zh = min(under[0], under[1]), max(under[0], under[1])
    B.box(bone, (a, hy0, zl), (b, hy1, zh), "molten")
    bl, bh = min(bz[0], bz[1]), max(bz[0], bz[1])
    ym = (hy0 + hy1) / 2
    B.box(bone, (a, ym - 0.5, bl), (b, ym + 0.5, bh), "iron")
    xm = (hx0 + hx1) / 2
    c, d = X(xm - 0.5, xm + 0.5)
    B.box(bone, (c, hy0, bl), (d, hy1, bh), "iron")
    B.box(bone, (a, hy0, bl), (b, hy1, bh), "void")
    a2, b2 = X(hx0 - 1, hx1 + 1)
    B.box(bone, (a2, hy0 - 1, bl - 0.5), (b2, hy0, bl), "iron")
    B.box(bone, (a2, hy1, bl - 0.5), (b2, hy1 + 1, bl), "iron")
    if studs:
        for k, (y0, y1, ra, rb) in enumerate(rows):
            if k % 2 == 1:
                yc = (y0 + y1) / 2
                for xa in (ra + 1, rb - 2):
                    p, q = X(xa, xa + 1)
                    B.box(bone, (p, yc - 0.5, bl - 0.5), (q, yc + 0.5, bl), "bronze")
    n = len(rows)
    for z0, z1, kind in layers:
        zl, zh = min(z0, z1), max(z0, z1)
        for k, (y0, y1, ra, rb) in enumerate(rows):
            if kind == "L":
                p, q = X(ra - grow if ra > 2.5 else ra, rb + grow)
                yy0 = y0 - (grow if k == 0 else 0)
                yy1 = y1 + (grow if k == n - 1 else 0)
                B.box(bone, (p, yy0, zl), (q, yy1, zh), "leather")
            elif kind == "S":
                p, q = X(ra + 1.5, rb - 1.5)
                yy0 = y0 + (1.5 if k == 0 else 0)
                yy1 = y1 - (1.5 if k == n - 1 else 0)
                if q > p:
                    B.box(bone, (p, yy0, zl), (q, yy1, zh), "ember")
            else:
                p, q = X(ra + 0.5, rb - 0.5)
                yy0 = y0 + (0.5 if k == 0 else 0)
                yy1 = y1 - (0.5 if k == n - 1 else 0)
                B.box(bone, (p, yy0, zl), (q, yy1, zh), board)
    z_in = max(max(l[0], l[1]) for l in layers)
    for y0, y1, ra, rb in rows:
        if y1 <= mount[1]:
            p, q = X(ra + 1.5, rb - 2.5)
            if q > p:
                B.box(bone, (p, y0, z_in), (q, y1, mount[0]), "iron_dk")


def neck(B, z0=17):
    B.slab("body", -5, 5, 46, 54, z0, 22.5, "skin", 1.5)   # 前へ突き出す太い首


def bones_common(cxa, sock_m, sock_o, extra, head_piv=(0, 50, 22), legs_x=10):
    return [("body", (0, 30, 0), None), ("belly", (0, 30, 0), "body"), ("apron", (0, 30, 9), "belly"),
            ("head", head_piv, "body"),
            ("arm_r", (-cxa, 56, 5), "body"), ("forearm_r", (-cxa, 43, 5), "arm_r"),
            ("socket_main", sock_m, "forearm_r", SOCKET_REST),
            ("arm_l", (cxa, 56, 5), "body"), ("forearm_l", (cxa, 43, 5), "arm_l"), ("socket_off", sock_o, "forearm_l"),
            ("leg_r", (-legs_x, 22, 0), None), ("leg_l", (legs_x, 22, 0), None)] + extra


# ---------------------------------------------------------------- 頭 (肩の線 y=60 より下、首の先 z 22..32)

def head_a(B):
    h = "head"
    B.box(h, (-4, 50.5, 31.5), (4, 51.5, 32), "molten")          # 面の細い目の隙間 (白熱)
    B.box(h, (-4, 50.5, 32), (4, 51.5, 33), "void")
    for x0 in (-5, 4):
        for y0 in (47, 53):
            B.box(h, (x0, y0, 33), (x0 + 1, y0 + 1, 33.5), "bronze")
    B.box(h, (-2, 47, 33), (2, 48, 33.5), "iron_dk")             # 面の口の格子
    B.slab(h, -7, 7, 54, 56.5, 23, 34.5, "iron", 1)              # 重い眉庇
    B.box(h, (-5.5, 46, 31.5), (5.5, 54, 33), "iron")            # 鍛冶の面
    B.both(h, 5.5, 6, 50, 51.5, 23, 31.5, "leather")             # 面を留める革紐
    B.slab(h, -5.5, 5.5, 45, 54, 22.5, 31.5, "skin", 1)
    B.slab(h, -4.5, 4.5, 44, 45, 24, 31, "skin", 1)              # 顎
    B.slab(h, -5, 5, 56.5, 58, 24, 30, "leather", 1)             # 革の帽子


def head_b(B):
    h = "head"
    for s in (-1, 1):
        B.s(h, s, 1.5, 3.5, 50.5, 51.5, 31.5, 32, "molten")       # 眉の下の二つの目
    B.slab(h, -7.5, 7.5, 52, 54.5, 29, 34.5, "slag", 1)          # 鉱滓の重い眉
    B.slab(h, -4.5, 4.5, 56, 58, 24, 30.5, "iron", 1)
    B.slab(h, -6, 6, 52, 56, 22.5, 32, "iron", 1.5)              # 鉄の兜
    B.both(h, 5.5, 7, 46, 52, 24, 31.5, "iron")                  # 頬当て
    B.box(h, (-3, 45, 31.5), (3, 46.5, 32.5), "iron_dk")         # 顎の鉄
    B.slab(h, -5.5, 5.5, 45, 52, 22.5, 31.5, "skin", 1)


def head_c(B):
    h = "head"
    for s in (-1, 1):
        B.s(h, s, 1.5, 3.5, 51, 52.5, 31.5, 32, "ember")          # 遮光眼鏡の玉 (炉の照り返し)
        B.s(h, s, 1.5, 3.5, 51, 52.5, 32, 32.5, "void")
        B.s(h, s, 0.5, 4.5, 50.5, 53, 31.5, 32.5, "bronze")
    B.box(h, (-4.5, 47.5, 31.5), (4.5, 49, 32.5), "ash")         # 灰の口髭
    B.box(h, (-4, 44, 28), (4, 47.5, 32), "ash")                 # 短い灰の顎鬚
    B.slab(h, -5, 5, 53.5, 55, 30.5, 33, "skin", 0.5)            # 眉
    B.box(h, (-5, 45, 22.5), (5, 56, 31.5), "skin")
    B.both(h, 5.5, 6, 51, 52.5, 23, 30.5, "leather")             # 眼鏡の革紐
    B.slab(h, -6, 6, 48, 57.5, 22.5, 30.5, "leather", 1.5)       # 頭巾


# ---------------------------------------------------------------- 案 A 鞴の巨人

def build_a():
    B = SB()
    head_a(B)
    # 胸の焼け跡 (燠が光る細い傷。頭の下で、正面から見える)
    for lo, hi in (((-6, 41, 15.5), (-1, 41.5, 16)), ((-3.5, 40, 15.5), (-3, 43.5, 16)),
                   ((-5, 37.5, 12.5), (0, 38, 13)), ((-2, 38, 12.5), (-1.5, 40, 13)),
                   ((-1, 42.5, 15.5), (1.5, 43, 16))):
        B.box("body", lo, hi, "ember")
    # 鞴を背負う革の帯 (胸の前を縦に、瘤を越えて背へ)
    for y0, y1, hw, zb, zf, ch in ROWS_AB:
        B.both("body", 6, 9, y0, y1, zf, zf + 0.5, "leather")
    B.both("body", 6, 9, 58, 62, 12, 12.5, "leather")
    B.both("body", 6, 9, 62, 62.5, 7, 12.5, "leather")
    B.both("body", 7, 8, 40, 41, 16.5, 17, "bronze")
    neck(B)
    barrel(B, "body", ROWS_AB, "skin")
    shoulders_hump(B)
    B.box("body", (-2.5, 38, -12), (2.5, 63, -2), "iron")         # 背骨の鉄板 (左右の鞴のあいだ)
    waist(B, 11.5)
    apron(B, "a")
    sock_m = arm(B, -1, ARM_AB)
    sock_o = arm(B, 1, ARM_AB)
    legs(B, 5, 16)
    rows = [(34, 38, 5, 10), (38, 42, 3.5, 13), (42, 47, 3, 16), (47, 53, 3, 18), (53, 59, 3, 19),
            (59, 64, 3.5, 18.5), (64, 68, 4.5, 16), (68, 71, 6.5, 13.5)]
    layers = [(-11, -13, "L"), (-13, -13.5, "S"), (-13.5, -16, "L"), (-16, -16.5, "S"),
              (-16.5, -19, "L"), (-19, -19.5, "S"), (-19.5, -21, "B")]
    for sx, bone in ((-1, "bellows_r"), (1, "bellows_l")):
        X = lambda a, b: (a, b) if sx > 0 else (-b, -a)  # noqa: E731
        p, q = X(8.5, 10.5)
        B.box(bone, (p, 77, -15.5), (q, 77.5, -13.5), "ember")
        B.box(bone, (p, 77.5, -15.5), (q, 79, -13.5), "void")
        p, q = X(7.5, 11.5)
        B.box(bone, (p, 77, -16.5), (q, 79, -12.5), "bronze")
        p, q = X(8, 11)
        B.box(bone, (p, 71, -16), (q, 77, -13), "bronze")
        bellows(B, bone, rows, sx, layers, hole=(7, 14, 47, 58), board="bronze")
    bones = bones_common(27, sock_m, sock_o, [("bellows_l", (10, 52, -11), "body"),
                                              ("bellows_r", (-10, 52, -11), "body")])
    return B, bones


# ---------------------------------------------------------------- 案 B 鉱滓の鎧

def build_b():
    B = SB()
    head_b(B)
    # 樽の胸を覆う鉱滓の板 (段ごとに 1 出る)、鋲、真ん中の継ぎ目
    for y0, y1, hw, zb, zf, ch in ROWS_AB[1:]:
        for s in (-1, 1):
            xr = hw - ch
            if y1 - y0 >= 4:
                for rx in (1.5, xr - 2):
                    B.s("body", s, rx, rx + 1, y0 + 1.5, y0 + 2.5, zf + 1, zf + 1.5, "bronze")
            B.s("body", s, 0.5, xr, y0, y1, zf - 1, zf + 1, "slag")
    B.slab("body", -6.5, 6.5, 44, 47, 17, 22.5, "iron", 1)          # 首の鉄の輪
    for x0 in (-8, -4, 3, 7):
        B.box("body", (x0, 58, -5.5), (x0 + 1, 59, -5), "bronze")
    B.box("body", (-9, 55, -5), (9, 60, -3), "slag")                # 背の上の板
    neck(B)
    barrel(B, "body", ROWS_AB, "skin")
    shoulders_hump(B, mat_hump="slag")
    waist(B, 11.5)
    apron(B, "b")
    sock_m = arm(B, -1, ARM_B)
    sock_o = arm(B, 1, ARM_B)
    legs(B, 5, 16, armour=True)
    # 左肩の後ろから立つ煙突 (骨 chimney) と煙 (骨 smoke)
    c = "chimney"
    B.box(c, (13, 84, -8.5), (17, 84.5, -4.5), "ember")
    B.box(c, (13, 84.5, -8.5), (17, 86, -4.5), "void")
    B.slab(c, 11.5, 18.5, 84, 86, -10, -3, "iron", 1)
    for y0 in (68, 76):
        B.slab(c, 12, 18, y0, y0 + 1.5, -9.5, -3.5, "iron", 1)
    B.slab(c, 12.5, 17.5, 62, 84, -9, -4, "rust", 1)
    B.slab(c, 11, 19, 55, 62, -10, -2, "iron_dk", 1.5)
    for lo, hi in (((13, 87, -9), (17, 90.5, -5)), ((12, 90.5, -11), (16.5, 94, -6.5)),
                   ((13.5, 94, -13.5), (17.5, 97, -9.5)), ((12.5, 97, -15.5), (15.5, 99.5, -12.5)),
                   ((14, 99.5, -17), (16, 101, -15))):
        B.box("smoke", lo, hi, "smoke")
    rows = [(37, 41, -6, 6), (41, 47, -8.5, 8.5), (47, 53, -9, 9), (53, 57, -7, 7)]
    layers = [(-11, -13, "L"), (-13, -13.5, "S"), (-13.5, -16, "L"), (-16, -16.5, "S"), (-16.5, -18, "B")]
    bellows(B, "bellows", rows, 1, layers, hole=(-4, 4, 44, 52), board="iron")
    bones = bones_common(28, sock_m, sock_o, [("bellows", (0, 47, -11), "body"),
                                              ("chimney", (15, 58, -6), "body"), ("smoke", (15, 87, -7), "chimney")])
    return B, bones


# ---------------------------------------------------------------- 案 C 炉を背負う者

def build_c():
    B = SB()
    head_c(B)
    # 胸の炉: 鉄の焚き口が樽の胸から突き出し、アーチの口の奥が白熱 (骨 furnace)
    f = "furnace"
    B.box(f, (-6, 33, 5.5), (6, 44, 6), "molten")
    B.box(f, (-6, 33, 6), (6, 34.5, 13), "ember")
    for x0 in (-4.5, -1, 2.5):
        B.box(f, (x0, 34.5, 8), (x0 + 2, 35.5, 11), "soot")         # 燃える石炭の塊
    for s in (-1, 1):
        B.s("body", s, 2, 3, 34.5, 41, 13, 14, "iron")               # 格子
    for lo, hi in (((-6, 33, 6), (6, 41, 16)), ((-4.5, 41, 6), (4.5, 43, 16)), ((-2.5, 43, 6), (2.5, 44, 16))):
        B.box("body", lo, hi, "void")
    for x0, y0 in ((-7.5, 32), (6.5, 32), (-7.5, 44), (6.5, 44)):
        B.box("body", (x0, y0, 15), (x0 + 1, y0 + 1, 15.5), "bronze")
    B.box("body", (-1.5, 44, 15), (1.5, 45.5, 15.5), "bronze")      # 要石
    B.slab("body", -8, 8, 31.5, 45.5, 7, 15, "iron_dk", 1)          # 焚き口の鉄の枠
    # 炉の体 (煉瓦の樽の胸) と鉄の輪
    for yy in (36, 52):
        for y0, y1, hw, zb, zf, ch in ROWS_C:
            if y0 <= yy < y1:
                B.slab("body", -hw - 0.5, hw + 0.5, yy, yy + 1, zb - 0.5, zf + 0.5, "iron", ch, round_=True)
    neck(B, z0=15)
    barrel(B, "body", ROWS_C, "brick")
    B.both("body", 9, 19, 52, 60, -3, 13, "skin", ch=2.5, round_=True)   # 肩
    B.slab("body", -11, 11, 58, 61, -5, 11, "brick", 3, round_=True)     # 炉の頂 (背の瘤)
    fl = "flue"
    B.box(fl, (-2, 70.5, -4.5), (2, 71, -0.5), "ember")
    B.box(fl, (-2, 71, -4.5), (2, 72.5, -0.5), "void")
    B.slab(fl, -4.5, 4.5, 70, 72.5, -7.5, 2, "iron", 1)
    B.slab(fl, -4, 4, 65, 66.5, -7, 1.5, "iron", 1)
    B.slab(fl, -3.5, 3.5, 61, 70, -6.5, 1, "brick", 1)
    waist(B, 10.5)
    apron(B, "c", w=10)
    sock_m = arm(B, -1, ARM_C)
    sock_o = arm(B, 1, ARM_C)
    legs(B, 5, 14.5)
    rows = [(23, 27, 3, 8), (27, 32, 2.5, 11), (32, 38, 2.5, 12), (38, 42, 3, 10)]
    layers = [(-10.5, -12.5, "L"), (-12.5, -13, "S"), (-13, -15, "L"), (-15, -15.5, "S"), (-15.5, -17, "B")]
    for sx, bone in ((-1, "bellows_r"), (1, "bellows_l")):
        X = lambda a, b: (a, b) if sx > 0 else (-b, -a)  # noqa: E731
        p, q = X(6, 8.5)
        B.box(bone, (p, 42.5, -14), (q, 46, -11.5), "bronze")       # 鞴から炉へ風を送る管
        p, q = X(8.5, 20.5)
        B.box(bone, (p, 43.5, -14), (q, 46, -11.5), "bronze")
        p, q = X(18, 20.5)
        B.box(bone, (p, 43.5, -11.5), (q, 46, 3), "bronze")
        p, q = X(17.5, 21)
        B.box(bone, (p, 43, 3), (q, 46.5, 4), "iron")
        bellows(B, bone, rows, sx, layers, hole=(4.5, 9.5, 30, 37), board="haft", mount=(-2, 43))
    bones = bones_common(25, sock_m, sock_o, [("furnace", (0, 38, 8), "body"), ("flue", (0, 61, -3), "body"),
                                              ("bellows_l", (8, 32, -10), "body"),
                                              ("bellows_r", (-8, 32, -10), "body")], legs_x=9.75)
    return B, bones


# ---------------------------------------------------------------- 武器 (別のモデル。原点 = 握りの中心)

def hammer_head(B, h, w, yb, yt, zb, zf, cbb, c=1.0, mat="iron"):
    """鎚の頭。y の 0.5 の薄切りごとに z の範囲を決め、横から見た角を落とす。
    後ろ下の角 cbb は 45 度の面取り: 外側の角が平面 (y-yb)+(z-zb)=cbb に載る。受け口を前へ 45 度倒すと、
    この面がちょうど水平になって床に載る。"""
    spans = []
    y = yb
    while y < yt - 1e-9:
        dy_b, dy_t = y - yb, yt - (y + 0.5)
        z0 = zb + max(cbb - dy_b, c - dy_t, 0)
        z1 = zf - max(c - dy_b, c - dy_t, 0)
        spans.append((y, z0, z1))
        y += 0.5
    k = 0
    while k < len(spans):
        y0, z0, z1 = spans[k]
        j = k
        while j + 1 < len(spans) and spans[j + 1][1:] == (z0, z1):
            j += 1
        B.box(h, (-w, y0, z0), (w, spans[j][0] + 0.5, z1), mat)
        k = j + 1


def hammer(opt):
    B = SB()
    h = "hammer"
    if opt == "a":   # 角の大鎚: 鉄の頭、錆の帯、前の打つ面が熱を持つ。柄は前の 1.7 倍
        yb, yt = -33.5, -20.5
        B.box(h, (-2, 5, -2), (2, 6.5, 2), "iron")                    # 柄頭 (拳の中)
        for y0 in (-8, -14):
            B.box(h, (-2, y0, -2), (2, y0 + 1.5, 2), "leather")      # 両手で握る位置の革巻き
        B.box(h, (-2.5, -20.5, -2.5), (2.5, -19, 2.5), "bronze")
        B.box(h, (-1.5, -19, -1.5), (1.5, 5, 1.5), "haft")
        B.box(h, (-5, yb + 1.5, 7.5), (5, yt - 1.5, 8.5), "hot")
        hammer_head(B, h, 6.5, yb, yt, -7.5, 7.5, 3)
        for z0 in (2, -3):   # 錆の帯 (頭の後に置いて、外に出る分だけが残る)
            B.box(h, (-7, yb, z0), (7, yt + 0.5, z0 + 1.5), "rust")
    elif opt == "b":  # 鉱滓の大鎚: 太い頭に鉱滓の瘤と鋲、鉄の中帯
        yb, yt = -33, -19
        B.box(h, (-2, 4, -2), (2, 5.5, 2), "iron")
        for s in (-1, 1):
            B.s(h, s, 1.5, 2, -17.5, -11, -0.5, 0.5, "iron")          # 柄の鉄の添え板
        B.box(h, (-2, -9, -2), (2, -7.5, 2), "leather")
        B.box(h, (-3, -19, -3), (3, -17.5, 3), "iron")
        B.box(h, (-1.5, -17.5, -1.5), (1.5, 4, 1.5), "haft")
        B.box(h, (-6, yb + 1.5, 8), (6, yt - 1.5, 9), "hot")
        for lo, hi in (((-8, -30, -5), (-7.5, -26, -1)), ((7.5, -28, 2), (8, -23.5, 6)), ((-8, -24.5, 2.5), (-7.5, -21, 6.5)),
                       ((7.5, -31.5, -5.5), (8, -28.5, -2)), ((-4, -19, -6), (0, -18.5, -2.5)), ((1, -19, 2.5), (5, -18.5, 6))):
            B.box(h, lo, hi, "slag")
        for s in (-1, 1):
            for yy in (-30, -23):
                B.s(h, s, 7.5, 8, yy, yy + 1, 5.5, 6.5, "bronze")
        hammer_head(B, h, 7.5, yb, yt, -8, 8, 3, mat="slag")
        B.box(h, (-8, yb + 2, -1), (8, yt + 0.5, 1), "iron")
    else:  # 長柄の鍛冶鎚: 前は四角い打つ面、後ろ上に細る横の刃 (クロスペイン)
        yb, yt = -32, -21
        B.box(h, (-2, 5, -2), (2, 6.5, 2), "iron")
        for y0 in (-6, -13):
            B.box(h, (-2, y0, -2), (2, y0 + 1.5, 2), "leather")
        B.box(h, (-2.5, -21, -2.5), (2.5, -19.5, 2.5), "bronze")
        B.box(h, (-1.5, -19.5, -1.5), (1.5, 5, 1.5), "haft")
        B.box(h, (-3.5, yb + 1, 6), (3.5, yt - 1, 7), "hot")
        B.box(h, (-2.5, -25, -6.5), (2.5, -21.5, -4), "iron")          # ペイン (後ろ上)
        B.box(h, (-2, -24, -8.5), (2, -22, -6.5), "iron")
        B.box(h, (-1.5, -23.5, -9.5), (1.5, -22.5, -8.5), "iron_dk")
        hammer_head(B, h, 4, yb, yt, -4, 6, 2.5)
        B.box(h, (-4.5, -27.5, -1), (4.5, -25.5, 1), "rust")
    return to_model(f"ash_smith_hammer_{opt}", B, [("hammer", (0, 0, 0), None)])


def tongs(opt):
    B = SB()
    t = "tongs"
    L = {"a": 0, "b": 0, "c": 5}[opt]       # 案 C は長い火箸
    w = 1.5 if opt == "b" else 1.0
    j0 = 14 + L
    if opt == "b":   # 鉱滓まじりの白熱の塊
        B.box(t, (-3.5, -2, j0 + 4), (3.5, 2, j0 + 10), "molten")
        B.box(t, (-2.5, -1.5, j0 + 3.5), (2.5, 1.5, j0 + 4), "ember")
        B.box(t, (-2.5, -1.5, j0 + 10), (2, 1.5, j0 + 11), "ember")
        B.box(t, (3.5, -1, j0 + 5), (4, 1, j0 + 8), "ember")
        it_ = 2
    else:            # 四角い延べ棒
        ln = 7 if opt == "a" else 10
        B.box(t, (-3, -1.5, j0 + 4.5), (3, 1.5, j0 + 3.5 + ln), "molten")
        B.box(t, (-3, -1.5, j0 + 3.5), (3, 1.5, j0 + 4.5), "ember")
        B.box(t, (-3, -1.5, j0 + 3.5 + ln), (3, 1.5, j0 + 4.5 + ln), "ember")
        it_ = 1.5
    end = (j0 + 12) if opt == "b" else (j0 + 5.5 + (7 if opt == "a" else 10))
    for s in (1, -1):     # 上の顎 (s=1) と下の顎
        def Y(a, b):
            return (a, b) if s > 0 else (-b, -a)
        y0, y1 = Y(1, 2.5)
        B.box(t, (-w, y0, j0), (w, y1, j0 + 3), "iron")
        y0, y1 = Y(it_, it_ + 1.5)
        B.box(t, (-w, y0, j0 + 3), (w, y1, end - 1.5), "iron")
        y0, y1 = Y(0.5, it_ + 1.5)
        B.box(t, (-w, y0, end - 1.5), (w, y1, end), "iron_dk")
    for s in (-1, 1):
        B.s(t, s, 2, 2.5, -0.5, 0.5, 12 + L, 13 + L, "bronze")
    B.box(t, (-2, -1.5, 11 + L), (2, 1.5, 14 + L), "iron")
    hz = 11 + L
    B.box(t, (-1.5, 1, -9), (1.5, 2, -8), "iron")
    B.box(t, (-1.5, -2, -9), (1.5, -1, -8), "iron_dk")
    B.box(t, (-1.5, 0, -9), (1.5, 1, hz), "iron")
    B.box(t, (-1.5, -1, -9), (1.5, 0, hz), "iron_dk")
    if opt == "b":
        B.box(t, (-2, -1.5, 8), (2, 1.5, 9.5), "bronze")
    return to_model(f"ash_smith_tongs_{opt}", B, [("tongs", (0, 0, 0), None)])


# ---------------------------------------------------------------- 描画

POSE_UP = {  # 鎚を振り上げた姿 (表示用の骨の回転、意図した値)
    "a": {"arm_r": (-160, 0, -8), "forearm_r": (-25, 0, 0), "arm_l": (-20, 0, 6), "forearm_l": (-15, 0, 0),
          "body": (-10, 0, 0), "head": (8, 0, 0), "socket_main": (20, 0, 0)},
    "b": {"arm_r": (-160, 0, -10), "forearm_r": (-25, 0, 0), "arm_l": (-20, 0, 6), "forearm_l": (-15, 0, 0),
          "body": (-10, 0, 0), "head": (8, 0, 0), "socket_main": (20, 0, 0)},
    "c": {"arm_r": (-165, 0, -8), "forearm_r": (-25, 0, 0), "arm_l": (-20, 0, 6), "forearm_l": (-15, 0, 0),
          "body": (-10, 0, 0), "head": (8, 0, 0), "socket_main": (20, 0, 0)},
}


def weapon_geo(geo_game, wgeo, socket, prefix):
    g = copy.deepcopy(geo_game)
    gb = g["minecraft:geometry"][0]
    p = next(b["pivot"] for b in gb["bones"] if b["name"] == socket)
    for b in gb["bones"]:
        b["cubes"] = []
    wb = copy.deepcopy(wgeo)["minecraft:geometry"][0]["bones"]
    for b in wb:
        b["name"] = prefix + b["name"]
        b["parent"] = socket if "parent" not in b else prefix + b["parent"]
        b["pivot"] = [a + c for a, c in zip(b["pivot"], p)]
        for c in b.get("cubes", []):
            c["origin"] = [a + q for a, q in zip(c["origin"], p)]
    gb["bones"] += wb
    return g


def attach(geo_game, tex, gl, weapons, pose=None):
    """体の面 + 受け口に付けた武器の面 (武器は別の geo・別のテクスチャのまま)。"""
    faces = vx.load_geo(geo_game, tex, gl, pose=pose)
    for (wgeo, wtex, wgl), socket, prefix in weapons:
        faces += vx.load_geo(weapon_geo(geo_game, wgeo, socket, prefix), wtex, wgl, pose=pose)
    return faces


def min_y(faces):
    ys = [(f.origin + a * f.eu + b * f.ev)[1] for f in faces for a in (0, 1) for b in (0, 1)]
    return float(min(ys))


def game_render(faces, yaw, pitch, size=VIEW):
    return vx.render(faces, yaw=-yaw, pitch=pitch, size=size).transpose(tm.FLIP)


def weapons_image(hr, tr):
    img = Image.new("RGB", VIEW, (18, 18, 26))
    a = vx.render(vx.load_geo(hr["geo"], hr["tex"], hr["gl"]), yaw=35, pitch=14, size=(190, 430))
    b = vx.render(vx.load_geo(tr["geo"], tr["tex"], tr["gl"]), yaw=115, pitch=28, size=(250, 430))
    img.paste(a, (0, 5))
    img.paste(b, (190, 5))
    return img


def export_weapon(m, buried, folder):
    r = tm.export(m, folder, root=False)
    r["buried"] = buried
    faces = vx.load_geo(r["geo"], r["tex"], r["gl"])
    yaw, pitch = (35, 14) if "hammer" in m.ident else (115, 28)
    vx.render(faces, yaw=yaw, pitch=pitch, size=(360, 360)).save(folder / "render.png")
    return r


def write_geckolib_rotations(folder):
    """geo.json の骨の回転 (root 以外) を GeckoLib の決まりで (-x, y, -z) にして書き直す。"""
    p = folder / "geo.json"
    geo = json.loads(p.read_text(encoding="utf-8"))
    for b in geo["minecraft:geometry"][0]["bones"]:
        if b["name"] != "root" and "rotation" in b:
            x, y, z = b["rotation"]
            b["rotation"] = [-x, y, -z]
    p.write_text(json.dumps(geo, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")


def run():
    OUT.mkdir(parents=True, exist_ok=True)
    stats = {}
    cols = []
    builders = {"a": build_a, "b": build_b, "c": build_c}
    for opt in OPTIONS:
        d = OUT / opt
        B, bones = builders[opt]()
        m, pruned = to_model(f"ash_smith_{opt}", B, bones)
        r = tm.export(m, d)
        write_geckolib_rotations(d)
        geo_raw, _, _ = m.finish()
        over = vx.check_overlaps(geo_raw)
        buried = tm.buried_count(m.boxes)
        hm, hb = hammer(opt)
        tg, tb = tongs(opt)
        hr = export_weapon(hm, hb, d / "hammer")
        tr = export_weapon(tg, tb, d / "tongs")
        wpn = [((hr["geo"], hr["tex"], hr["gl"]), "socket_main", "hammer_"),
               ((tr["geo"], tr["tex"], tr["gl"]), "socket_off", "tongs_")]
        ham_floor = min_y(vx.load_geo(weapon_geo(r["geo"], hr["geo"], "socket_main", "hammer_"), hr["tex"], hr["gl"]))
        imgs = []
        for name, yaw, pitch in VIEWS:
            im = game_render(attach(r["geo"], r["tex"], r["gl"], wpn), yaw, pitch)
            im.save(d / f"{name}.png")
            imgs.append(im)
        up = game_render(attach(r["geo"], r["tex"], r["gl"], wpn, POSE_UP[opt]), 235, 10)
        up.save(d / "windup.png")
        imgs.append(up)
        imgs.append(weapons_image(hr, tr))
        lo = np.min([bx["b"][:3] for bx in m.boxes], 0)
        hi = np.max([bx["b"][3:] for bx in m.boxes], 0)
        glow = int((r["gl"][..., 3] > 0).sum())
        st = dict(
            boxes=dict(boss=r["n"], hammer=hr["n"], tongs=tr["n"]),
            overlaps=dict(boss=len(over), hammer=len(hr["overlaps"]), tongs=len(tr["overlaps"])),
            buried=dict(boss=buried, hammer=hr["buried"], tongs=tr["buried"], pruned_hidden=pruned),
            glow_px=dict(boss=glow, hammer=int((hr["gl"][..., 3] > 0).sum()), tongs=int((tr["gl"][..., 3] > 0).sum())),
            bounds=dict(lo=[float(v) for v in lo], hi=[float(v) for v in hi],
                        height_blocks=round(float(hi[1] - lo[1]) / 16, 2)),
            hammer_idle_lowest_y=round(ham_floor, 2),
            socket_main_rest_rotation=dict(intended=list(SOCKET_REST),
                                           geo_json=[-SOCKET_REST[0], SOCKET_REST[1], -SOCKET_REST[2]]),
            texture=[int(r["tex"].shape[1]), int(r["tex"].shape[0])],
            cross_bone_carves=dict(B.cross),
            bones=[b[0] for b in bones],
        )
        stats[opt] = st
        assert r["n"] <= 900 and hr["n"] <= 80 and tr["n"] <= 40, st["boxes"]
        assert not over and not hr["overlaps"] and not tr["overlaps"]
        assert glow > 0
        assert -0.3 < ham_floor < 1.0, (opt, "鎚の頭が床に載っていない", ham_floor)
        print(f"{opt}: 箱 {st['boxes']}、重なり {st['overlaps']}、埋まり {st['buried']}、光 {st['glow_px']}、"
              f"高さ {st['bounds']['height_blocks']} ブロック、鎚の最下点 y={st['hammer_idle_lowest_y']}")
        if B.cross:
            print("   骨をまたいだ削り:", dict(B.cross))
        cols.append((OPTION_NAME[opt], imgs))
    (OUT / "stats.json").write_text(json.dumps(stats, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    sheet = vx.sheet("中ボス 灰の鍛え手 ラフ 3案", cols,
                     note="行: 正面 3/4 / 横 / 背面 (鞴) / 鎚を振り上げた姿 / 武器だけ (左 大鎚・右 火箸)。武器は別モデル (socket_main / socket_off)")
    sheet.save(SHEET)


if __name__ == "__main__":
    run()
