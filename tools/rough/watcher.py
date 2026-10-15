"""「見る者」(watcher) のラフ 3 案と、採用案の書き出し。

見る者は、夜に一人でいると 40〜70 ブロック先に立ってこちらを見ている人影。近くで全身を見せる存在ではないので、
遠くの人影として成り立つシルエット (細長い・夜に溶ける・目だけかすかに光る) を比べる。

- A「巡礼者の外套」: 頭巾と裾の長い外套、背丈より長い杖。空の巡礼者に仕える者
- B「留まる者の影」: 人の形を引き伸ばした影。細い手足、ぼろの肩掛け、灰のように剥がれて昇るかけら
- C「月を負う者」: 前かがみの細い体に、割れた月のかけら (三日月) を背負う

立方体は 0.5 単位まで、テクスチャは 1 単位 = 2 画素 (1 ブロック = 32px)、面ごとの uv。部品は面で接するだけ
(置くたびに重なりを調べ、重なれば例外。最後に voxel.check_overlaps でも確かめる)。
座標: y が上、前は +z。書き出す geo だけ root 骨を yaw 180 度回して前を -z にする (mod のモブと同じ向き)。

実行 (リポジトリ直下):
    python tools/rough/watcher.py            ラフ 3 案と比較シートを docs/review/watcher_roughs/ に書く
    python tools/rough/watcher.py --install  さらに採用案 (CHOSEN) を src/main/resources に書き出す
出力: docs/review/watcher_roughs/<a|b|c>/ (geo.json, texture.png, glowmask.png)、
      docs/review/watcher_roughs/watcher_roughs.png (比較シート)、night_*.png (夜の遠景の見え方)
"""
from __future__ import annotations

import hashlib
import json
import math
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFilter

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
import voxel as vx  # noqa: E402
import mobs_small_v2 as v2  # noqa: E402

ROOT = vx.ROOT
OUT = ROOT / "docs" / "review" / "watcher_roughs"
SRC = ROOT / "src" / "main" / "resources" / "assets" / "skyabyss"
CHOSEN = "b"
VIEW = (300, 420)

# 材質の4段の明暗: (暗, 中, 明, ハイライト)。夜に溶けるよう、どれも暗く青みに寄せる
v2.RAMP.update({
    "shroud": ("#0B0E13", "#151A22", "#1F2630", "#2A3340"),
    "shroud_edge": ("#171C24", "#232A35", "#313A47", "#424D5C"),
    "ash": ("#08090C", "#101318", "#151920", "#1E232B"),
    "ash_pale": ("#2A3038", "#3E4650", "#56606B", "#6E7984"),
    "void": ("#030304", "#050608", "#08090C", "#0C0E12"),
    "wood": ("#17120D", "#241C15", "#33291F", "#43372A"),
    "moon": ("#3E4752", "#5F6B78", "#808D9A", "#A5B1BD"),
    "eye": ("#7F93A8", "#A3B6C9", "#C4D3E1", "#E3ECF4"),
})
RAMP = v2.RAMP
EYE_GLOW = "#B4C6D8"


class Mdl(v2.Model):
    """v2 の Model に、置くたびの重なり検査を足したもの。"""

    def box(self, bone, x0, y0, z0, x1, y1, z1, mat, detail=None, glow=None):
        v = (x0, y0, z0, x1, y1, z1)
        assert all(float(a * 2).is_integer() for a in v), (bone, v)
        assert x1 > x0 and y1 > y0 and z1 > z0, (bone, v)
        for b in self.boxes:
            o = b["b"]
            if all(min(v[i + 3], o[i + 3]) > max(v[i], o[i]) for i in range(3)):
                raise ValueError(f"重なり: {bone} {v} と {b['bone']} {o}")
        super().box(bone, x0, y0, z0, x1, y1, z1, mat, detail, glow)

    def mirror(self, bone, x0, y0, z0, x1, y1, z1, mat, detail=None, glow=None, other=None):
        """+x 側に置き、x を反転した同じ箱を -x 側 (other の骨) にも置く。"""
        self.box(bone, x0, y0, z0, x1, y1, z1, mat, detail, glow)
        self.box(other or bone, -x1, y0, z0, -x0, y1, z1, mat, detail, glow)


# ---------------------------------------------------------------- 塗り

def _h(*v):
    s = ",".join(f"{a:.2f}" for a in v)
    return int(hashlib.md5(s.encode()).hexdigest()[:6], 16)


def cloth(mat, folds=6):
    """布: 縦のひだ (数単位おきの暗い筋) と、位置で決まる細かなまだら。"""
    dk, md, lt, hi = RAMP[mat]

    def det(X, Y, Z, f):
        if f in ("up", "down"):
            return None
        u = X if f in ("north", "south") else Z
        k = math.floor(u * 2)
        if k % folds == 0:
            return dk
        h = _h(math.floor(X * 2), math.floor(Y * 2), math.floor(Z * 2)) % 9
        if h == 0:
            return lt
        if h < 3:
            return dk
        return None
    return det


def ash(mat="ash"):
    """影の体: ほとんど黒。面の明暗はそのままに、足もとほど暗く、ところどころ焼けた紙の縁のような
    淡い灰の筋と点が出る (近くで見たときだけ分かる程度)。"""
    dk, md, lt, hi = RAMP[mat]
    pale = RAMP["ash_pale"]

    def det(X, Y, Z, f):
        cell = (math.floor(X * 2), math.floor(Y * 2), math.floor(Z * 2))
        h = _h(*cell, len(f)) % 37
        if h == 0:
            return pale[0]
        if f in ("north", "south", "east", "west"):
            u = X if f in ("north", "south") else Z
            # 縦の細いひび: 数か所だけ、途切れながら走る
            if _h(math.floor(u * 2), 7) % 11 == 0 and _h(math.floor(u * 2), math.floor(Y)) % 3:
                return lt
        if Y < 6 and h % 2 == 0:
            return dk
        if h < 5:
            return dk
        return None
    return det


def eyes_at(rects, z):
    """前の面 (z) の、rects の中だけを目の色にして光らせる。"""
    def det(X, Y, Z, f):
        if f == "south" and abs(Z - z) < 1e-6 and v2.in_rects(X, Y, rects):
            return EYE_GLOW
        return None

    def glow(X, Y, Z, f):
        return det(X, Y, Z, f) is not None
    return det, glow


def combine(*dets):
    def det(X, Y, Z, f):
        for d in dets:
            if d:
                r = d(X, Y, Z, f)
                if r:
                    return r
        return None
    return det


def ragged(cols, rows, depth_of):
    """上から垂れる布のマス目。列 c は上から depth_of(c) 行ぶん埋まる (裾がぎざぎざになる)。"""
    return ["".join("#" if r < depth_of(c) else "." for c in range(cols)) for r in range(rows)]


# ---------------------------------------------------------------- A: 巡礼者の外套

def concept_a():
    m = Mdl("watcher")
    m.bone("body", (0, 0, 0))
    m.bone("hem", (0, 8, 0), "body")
    m.bone("head", (0, 31, 0), "body")
    m.bone("armL", (5.5, 28, 0), "body")
    m.bone("armR", (-5.5, 28, 0), "body")
    m.bone("staff", (-7.5, 14, 0), "armR")
    S, E = "shroud", "shroud_edge"
    c = cloth(S)
    ce = cloth(E, 5)

    # 裾: 芯と、縁に並ぶ長さの違う布の歯
    m.box("hem", -4, 0.5, -2.5, 4, 2, 2.5, S, c)
    m.box("hem", -5, 2, -3.5, 5, 8, 3.5, S, c)
    teeth = [(-5, -3.5, 0), (-3.5, -2, 0.5), (-2, -0.5, 0), (-0.5, 1, 1), (1, 2.5, 0), (2.5, 4, 0.5), (4, 5, 0)]
    for x0, x1, yb in teeth:
        m.box("hem", x0, yb, 2.5, x1, 2, 3.5, E, ce)
        m.box("hem", x0, (yb + 0.5) % 1.5, -3.5, x1, 2, -2.5, E, ce)
    for z0, z1, yb in [(-2.5, -1, 0.5), (-1, 0.5, 0), (0.5, 2.5, 1)]:
        m.box("hem", 4, yb, z0, 5, 2, z1, E, ce)
        m.box("hem", -5, (yb + 0.5) % 1.5, z0, -4, 2, z1, E, ce)
    # 胴: 中段・帯・上段
    m.box("body", -4.5, 8, -3, 4.5, 19, 3, S, c)
    m.box("body", -4.5, 19, -3.5, 4.5, 20.5, 3.5, E, ce)
    m.box("body", 1.5, 12, 3, 2.5, 19, 3.5, E, ce)  # 帯の垂れ
    m.box("body", -4.5, 20.5, -3, 4.5, 28, 3, S, c)
    # 肩掛け (ケープ) と、ぎざぎざの下端
    m.box("body", -6, 28, -3.5, 6, 31, 3.5, E, ce)
    for x0, x1, yb in [(-4.5, -3, 26.5), (-3, -1.5, 27), (-1.5, 0, 26), (0, 1.5, 27), (1.5, 3, 26.5), (3, 4.5, 27.5)]:
        m.box("body", x0, yb, 3, x1, 28, 3.5, E, ce)
        m.box("body", x0, yb + 0.5 if yb < 27.5 else 27, -3.5, x1, 28, -3, E, ce)
    # 袖 (長く垂れる) と細い指
    for bone, s in (("armL", 1), ("armR", -1)):
        def bx(x0, y0, z0, x1, y1, z1, mat, det=None, _b=bone, _s=s):
            xa, xb = sorted((_s * x0, _s * x1))
            m.box(_b, xa, y0, z0, xb, y1, z1, mat, det)
        bx(4.5, 17, -1.5, 6.5, 28, 1.5, S, c)
        bx(4.5, 14, -2, 7, 17, 2, E, ce)
        if s > 0:
            bx(5, 10.5, -0.5, 5.5, 14, 0.5, "ash_pale")
            bx(6, 11, -0.5, 6.5, 14, 0.5, "ash_pale")
    # 杖: 右の袖口の外。上に三日月の飾り
    m.box("staff", -8, 0, -0.5, -7, 46, 0.5, "wood", cloth("wood", 4))
    crescent = ["..##..", ".#..#.", "#....#", "#.....", ".#....", "..##.."]
    m.plate("staff", -10, 49, -0.5, crescent, 1.0, "moon")
    m.box("staff", -8, 46, -0.5, -7, 46.5, 0.5, "moon")
    # 頭巾: 奥の壁 (暗い顔) と、前へ張り出した左右と上。とがった先が後ろへ
    m.box("head", -3.5, 31, -3, 3.5, 39, 1, S, c)
    eye_det, eye_glow = eyes_at([(-1.5, -0.5, 35, 35.5), (0.5, 1.5, 35, 35.5)], 1.5)
    m.box("head", -2.5, 31, 1, 2.5, 38, 1.5, "void", eye_det, eye_glow)
    m.box("head", -3.5, 31, 1, -2.5, 39, 4, E, ce)
    m.box("head", 2.5, 31, 1, 3.5, 39, 4, E, ce)
    m.box("head", -2.5, 38, 1.5, 2.5, 39, 4, E, ce)
    m.box("head", -2.5, 38, 1, 2.5, 39, 1.5, S)
    m.box("head", -2, 39, -3, 2, 41, 0, S, c)
    m.box("head", -1, 41, -3, 1, 42, -1.5, S)
    m.bone("eyes", (0, 35.25, 1.6), "head")  # 目の光の位置 (立方体なし。ゲームの WatcherRenderer が読む)
    pose = {"head": (8, 0, 6)}
    return m, pose


# ---------------------------------------------------------------- B: 留まる者の影

def concept_b():
    m = Mdl("watcher")
    m.bone("body", (0, 20, 0))
    m.bone("legL", (1.75, 20, 0))
    m.bone("legR", (-1.75, 20, 0))
    m.bone("armL", (4.75, 31.5, 0), "body")
    m.bone("armR", (-4.75, 31.5, 0), "body")
    m.bone("head", (0, 33, 0), "body")
    m.bone("cloak", (0, 32, -2), "body")
    m.bone("ash", (0, 36, 0), "body")
    A, E = "ash", "shroud_edge"
    a = ash()
    ce = cloth(E, 5)

    # 脚: 細く長い。膝で少し太る
    for bone, s in (("legL", 1), ("legR", -1)):
        def bx(x0, y0, z0, x1, y1, z1, mat=A, det=a, _b=bone, _s=s):
            xa, xb = sorted((_s * x0, _s * x1))
            m.box(_b, xa, y0, z0, xb, y1, z1, mat, det)
        bx(0.5, 0, -1.5, 3, 1, 2)           # 足
        bx(1, 1, -1, 2.5, 10, 1)            # すね
        bx(0.5, 10, -1, 3, 11.5, 1)         # 膝
        bx(0.5, 11.5, -1, 3, 17, 1)         # 腿
        bx(0.5, 17, -1.5, 3, 20, 1.5)       # 腿の付け根 (少し太い)
    # 胴: 腰・くびれ・胸・なで肩。胸の真ん中に胸骨の浮き
    m.box("body", -3.5, 20, -1.5, 3.5, 22.5, 1.5, A, a)
    m.box("body", -2.5, 22.5, -1, 2.5, 26, 1, A, a)
    m.box("body", -4, 26, -2, 4, 31, 2, A, a)
    m.box("body", -3.5, 31, -1.5, 3.5, 32, 1.5, A, a)
    m.box("body", -1.5, 27, 2, 1.5, 30.5, 2.5, A, a)
    m.box("body", -1, 32, -1, 1, 33.5, 1, A, a)  # 首
    # 腕: 細く、手が膝の高さまで垂れる。指は2本だけ長い
    for bone, s in (("armL", 1), ("armR", -1)):
        def bx(x0, y0, z0, x1, y1, z1, mat=A, det=a, _b=bone, _s=s):
            xa, xb = sorted((_s * x0, _s * x1))
            m.box(_b, xa, y0, z0, xb, y1, z1, mat, det)
        bx(3.5, 31, -1, 5, 32, 1)           # 肩
        bx(4, 21.5, -0.5, 5.5, 31, 1)       # 上腕
        bx(4, 20.5, -1, 5.5, 21.5, 1)       # 肘
        bx(4, 12, -0.5, 5.5, 20.5, 0.5)     # 前腕
        bx(4, 9.5, -1, 5.5, 12, 1)          # 手
        bx(4, 6.5, -0.5, 4.5, 9.5, 0.5)     # 指
        bx(5, 7, -0.5, 5.5, 9.5, 0.5)
    # 頭: 細長い。目は前板の穴の奥で光り、眉の張り出しの影に入る
    eye_rects = [(-1.5, -0.5, 37, 37.5), (0.5, 1.5, 37, 37.5)]
    eye_det, eye_glow = eyes_at(eye_rects, 2)
    m.box("head", -2, 33.5, -2, 2, 34.5, 1.5, A, a)                       # あご
    m.box("head", -2.5, 34.5, -2.5, 2.5, 40.5, 2, A, combine(eye_det, a), eye_glow)
    plate = []
    for r in range(12):
        y_top = 40.5 - 0.5 * r
        row = ""
        for col in range(10):
            x = -2.5 + 0.5 * col
            row += "." if v2.in_rects(x + 0.25, y_top - 0.25, eye_rects) else "#"
        plate.append(row)
    m.plate("head", -2.5, 40.5, 2, plate, 0.5, A, a)
    m.box("head", -2.5, 38, 2.5, 2.5, 38.5, 3, A, a)                      # 眉の張り出し
    m.box("head", -2, 40.5, -2, 2, 41.5, 1.5, A, a)                       # 頭頂
    # 肩掛け: 背中に垂れる、裾のぎざぎざなぼろ布。肩から前にも短く垂れる
    depth = [30, 34, 27, 36, 31, 25, 35, 29, 33, 26, 36, 28, 32, 24, 34, 30, 27, 33]
    m.plate("cloak", -4.5, 32, -2.5, ragged(18, 36, lambda c: depth[c]), 0.5, E, ce)
    m.box("cloak", -4.5, 32, -2.5, 4.5, 33, -1, E, ce)                # えり (後ろ)
    m.box("cloak", 1, 32, -1, 4.5, 33, 2, E, ce)                       # えり (左)
    m.box("cloak", -4.5, 32, -1, -1, 33, 2, E, ce)                     # えり (右)
    front = [7, 9, 6, 8]
    m.plate("cloak", 2, 32, 2, ragged(4, 9, lambda c: front[c]), 0.5, E, ce)
    m.plate("cloak", -4, 32, 2, ragged(4, 9, lambda c: front[3 - c] - 1), 0.5, E, ce)
    # 剥がれて昇っていく灰のかけら (体から離れて浮かぶ)
    for x, y, z in [(4, 34.5, 0), (5, 36.5, -0.5), (-4.5, 35, -1), (-2.5, 43, 0.5), (1.5, 44.5, -1),
                    (3.5, 40, -2), (-4, 38.5, 0.5), (0.5, 47, 0)]:
        m.box("ash", x, y, z, x + 0.5, y + 0.5, z + 0.5, "ash_pale")
    m.bone("eyes", (0, 37.25, 2.55), "head")  # 目の光の位置 (立方体なし。ゲームの WatcherRenderer が読む)
    pose = {"head": (6, 0, 10)}
    return m, pose


# ---------------------------------------------------------------- C: 月を負う者

def concept_c():
    m = Mdl("watcher")
    m.bone("body", (0, 18, 0))
    m.bone("legL", (1.5, 18, 0))
    m.bone("legR", (-1.5, 18, 0))
    m.bone("head", (0, 30, 1), "body")
    m.bone("armL", (4, 29, 0.5), "body")
    m.bone("armR", (-4, 29, 0.5), "body")
    m.bone("moon", (0, 32, -4), "body")
    A, E = "ash", "shroud_edge"
    a = ash()
    ce = cloth(E, 5)
    for bone, s in (("legL", 1), ("legR", -1)):
        def bx(x0, y0, z0, x1, y1, z1, mat=A, det=a, _b=bone, _s=s):
            xa, xb = sorted((_s * x0, _s * x1))
            m.box(_b, xa, y0, z0, xb, y1, z1, mat, det)
        bx(0.5, 0, -1, 2.5, 1, 2)
        bx(0.5, 1, -0.5, 2, 18, 0.5)
    m.box("body", -3, 18, -1.5, 3, 21, 1.5, E, ce)
    m.box("body", -2.5, 21, -1, 2.5, 25, 1, A, a)
    m.box("body", -3.5, 25, -1.5, 3.5, 30, 2, A, a)
    m.box("body", -1, 30, 0, 1, 31, 1.5, A, a)
    # ぼろの肩掛け (前に垂れる)
    depth = [10, 14, 9, 16, 12, 8, 15, 11, 13, 9, 14, 10, 12, 16]
    m.plate("body", -3.5, 30, 2, ragged(14, 16, lambda c: depth[c]), 0.5, E, ce)
    eye_det, eye_glow = eyes_at([(-1.5, -0.5, 34, 34.5), (0.5, 1.5, 34, 34.5)], 3.5)
    m.box("head", -2.5, 31, -1.5, 2.5, 37, 3.5, A, combine(eye_det, a), eye_glow)
    for bone, s in (("armL", 1), ("armR", -1)):
        def bx(x0, y0, z0, x1, y1, z1, mat=A, det=a, _b=bone, _s=s):
            xa, xb = sorted((_s * x0, _s * x1))
            m.box(_b, xa, y0, z0, xb, y1, z1, mat, det)
        bx(3.5, 16, 0, 4.5, 29, 1)
        bx(3.5, 12, -0.5, 5, 16, 1)
    # 背負った三日月 (割れた月のかけら)。光る筋が数本
    R, cells = 9, []
    for r in range(36):
        y = 9 - 0.5 * r - 0.25
        row = ""
        for col in range(36):
            x = -9 + 0.5 * col + 0.25
            outer = math.hypot(x, y) <= R
            inner = math.hypot(x - 3.5, y + 0.5) <= 7.5
            row += "#" if outer and not inner else "."
        cells.append(row)

    def moon_det(X, Y, Z, f):
        if f == "south" or f == "north":
            if abs((X + 0.3 * Y) % 4.5 - 2.0) < 0.3 and _h(math.floor(Y * 2)) % 3 == 0:
                return RAMP["eye"][1]
        h = _h(math.floor(X * 2), math.floor(Y * 2)) % 7
        return RAMP["moon"][0] if h == 0 else None

    def moon_glow(X, Y, Z, f):
        return moon_det(X, Y, Z, f) == RAMP["eye"][1]
    m.plate("moon", -9, 41, -5, cells, 0.5, "moon", moon_det, moon_glow)
    m.box("body", 1.5, 27, -4.5, 2.5, 28, -1.5, "wood")
    m.box("body", -2.5, 27, -4.5, -1.5, 28, -1.5, "wood")
    m.bone("eyes", (0, 34.25, 3.55), "head")  # 目の光の位置 (立方体なし。ゲームの WatcherRenderer が読む)
    pose = {"head": (14, 0, 8)}
    return m, pose


CONCEPTS = {
    "a": ("A 巡礼者の外套", concept_a,
          "頭巾と裾の長い外套に、背丈より長い杖。空の巡礼者に仕える者。顔は頭巾の奥の暗がりで、目だけが見える"),
    "b": ("B 留まる者の影", concept_b,
          "人の形を細く引き伸ばした影。手が膝の下まで垂れ、背にぼろの肩掛け。灰のかけらが剥がれて昇る"),
    "c": ("C 月を負う者", concept_c,
          "前かがみの細い体が、割れた月のかけら (三日月) を背負う。三日月にかすかに光る筋"),
}


# ---------------------------------------------------------------- 書き出し

def finish_geo(m):
    geo, tex, gl = m.finish()
    g = geo["minecraft:geometry"][0]
    g["description"]["visible_bounds_width"] = 4
    g["description"]["visible_bounds_height"] = 4
    g["description"]["visible_bounds_offset"] = [0, 1.6, 0]
    for b in g["bones"]:
        if "parent" not in b:
            b["parent"] = "root"
    g["bones"].insert(0, {"name": "root", "pivot": [0.0, 0.0, 0.0], "rotation": [0.0, 180.0, 0.0]})
    return geo, tex, gl


def faces(geo, tex, gl, pose=None):
    return vx.load_geo(geo, tex, gl, pose=pose)


def view(geo, tex, gl, yaw, pitch, pose=None, size=VIEW, gain=1.0, bg="dark"):
    """root で前が -z になっているので、正面から見るには yaw に 180 を足す。
    gain: 確認用の明るさ補正 (本来の色は夜に溶けるほど暗く、そのままでは細部が読めないため)"""
    t = tex.copy()
    t[..., :3] = np.clip(t[..., :3].astype(float) * gain, 0, 255).astype(np.uint8)
    return vx.render(faces(geo, t, gl, pose), yaw=yaw + 180, pitch=pitch, size=size, margin=0.04, bg=bg)


def head_view(geo, tex, gl, pose, yaw=20, size=(300, 300)):
    """頭の近景 (本来の色)。頭の骨の面だけを描く。"""
    F = faces(geo, tex, gl, pose)
    g = geo["minecraft:geometry"][0]
    head = [b for b in g["bones"] if b["name"] == "head"][0]
    keep = []
    hy = head["pivot"][1]
    for f in F:
        c = f.origin + 0.5 * f.eu + 0.5 * f.ev
        if c[1] >= hy - 2:
            keep.append(f)
    return vx.render(keep, yaw=yaw + 180, pitch=6, size=size, margin=0.12)


def night_scene(geo, tex, gl, pose, blocks_away, label, scene=(360, 200)):
    """夜の丘の上に立つ見る者を、blocks_away ブロック先から見たときの大きさで描く (画面の高さ 1080 / 視野 70 度)。
    夜の明るさ (空の光 4 前後) で暗くし、目だけは光のまま。ゲーム内で足す「目の光の点」も重ねる。"""
    F = faces(geo, tex, gl, pose)
    big = 600
    on_dark = np.array(vx.render(F, yaw=180, pitch=2, size=(big, big), bg="dark", bloom=False, outline=False)).astype(float)
    on_light = np.array(vx.render(F, yaw=180, pitch=2, size=(big, big), bg="light", bloom=False, outline=False)).astype(float)
    mask = (np.abs(on_dark - on_light).sum(-1) < 6).astype(float)
    # 光る画素だけ: テクスチャを黒にして描く
    black = tex.copy()
    black[..., :3] = 0
    lit = gl[..., 3] > 0
    black[lit, :3] = gl[lit, :3]
    glow_only = np.array(vx.render(vx.load_geo(geo, black, gl, pose=pose), yaw=180, pitch=2, size=(big, big),
                                   bg="dark", bloom=False, outline=False)).astype(float)
    glow_mask = (glow_only.sum(-1) > 60) & (mask > 0)
    # 縦の広がり (ブロック) から、画面上の高さ (px) を出す
    ys = np.nonzero(mask.any(1))[0]
    model_px = ys.max() - ys.min() + 1
    ys_world = [(f.origin + a * f.eu + b * f.ev)[1] for f in F for a in (0, 1) for b in (0, 1)]
    height_blocks = (max(ys_world) - min(ys_world)) / 16
    screen_px = 1080 / (2 * math.tan(math.radians(35))) * height_blocks / blocks_away
    k = screen_px / model_px

    night = on_dark * np.array([0.26, 0.28, 0.36])
    night[glow_mask] = on_dark[glow_mask] * 0.75
    rgba = np.zeros((big, big, 4))
    rgba[..., :3] = night
    rgba[..., 3] = mask * 255
    fig = Image.fromarray(np.clip(rgba, 0, 255).astype(np.uint8), "RGBA")
    nw, nh = max(1, int(round(big * k))), max(1, int(round(big * k)))
    fig = fig.resize((nw, nh), Image.LANCZOS)

    W, H = scene
    yy = np.linspace(0, 1, H)[:, None]
    sky = np.array([10, 14, 28.0]) * (1 - yy) + np.array([30, 38, 58.0]) * yy
    img = np.repeat(sky[:, None, :], W, 1).reshape(H, W, 3)
    # 星
    rng = np.random.default_rng(3)
    for _ in range(40):
        x, y = rng.integers(0, W), rng.integers(0, H // 2)
        img[y, x] = (120, 125, 140)
    # 丘の稜線
    xs = np.arange(W)
    ridge = (H * 0.68 + 10 * np.sin(xs / 37.0) + 6 * np.sin(xs / 13.0 + 1)).astype(int)
    for x in xs:
        img[ridge[x]:, x] = (9, 12, 11)
    base = Image.fromarray(img.astype(np.uint8))
    cx = W // 2
    top = ridge[cx] - int(round((ys.max() + 1) * k))
    base.paste(fig, (cx - nw // 2, top), fig)
    # 目の光の点 (ゲーム内の billboard と同じ考え: 画面上で 1.5px ほどのにじみ)
    gy, gx = np.nonzero(glow_mask)
    if len(gx):
        ex = cx - nw // 2 + gx.mean() * k
        ey = top + gy.mean() * k
        glint = Image.new("RGB", (W, H), (0, 0, 0))
        d = ImageDraw.Draw(glint)
        r = 1.6
        d.ellipse((ex - r, ey - r, ex + r, ey + r), fill=(80, 92, 108))
        glint = glint.filter(ImageFilter.GaussianBlur(1.2))
        base = Image.fromarray(np.clip(np.array(base).astype(int) + np.array(glint).astype(int), 0, 255).astype(np.uint8))
    out = base.resize((W * 2, H * 2), Image.NEAREST)
    ImageDraw.Draw(out).text((8, 6), f"{label}  {blocks_away} ブロック先 (高さ {screen_px:.0f}px / 1080p)",
                             font=vx.font(16), fill=(200, 200, 210))
    return out


def make(letter):
    label, fn, concept = CONCEPTS[letter]
    m, pose = fn()
    geo, tex, gl = finish_geo(m)
    overlaps = vx.check_overlaps(geo)
    d = OUT / letter
    d.mkdir(parents=True, exist_ok=True)
    (d / "geo.json").write_text(json.dumps(geo, indent=1, ensure_ascii=False) + "\n", encoding="utf-8")
    Image.fromarray(tex, "RGBA").save(d / "texture.png")
    Image.fromarray(gl, "RGBA").save(d / "glowmask.png")
    imgs = [view(geo, tex, gl, 25, 12, pose, gain=2.4), view(geo, tex, gl, 90, 4, pose, gain=2.4),
            view(geo, tex, gl, 210, 14, pose, gain=2.4), head_view(geo, tex, gl, pose)]
    nights = [night_scene(geo, tex, gl, pose, 60, label), night_scene(geo, tex, gl, pose, 40, label)]
    return m, geo, tex, gl, imgs, nights, overlaps


def animations(bone_names=None):
    """待機だけ。呼吸のようなわずかな上下と、肩掛け・腕のゆらぎ。頭の向きと傾きはコードで動かす。
    bone_names を渡すと、その案にある骨の分だけを書く (案を差し替えたときに、無い骨を書かない)。"""
    data = _animations()
    if bone_names is not None:
        for a in data["animations"].values():
            a["bones"] = {k: v for k, v in a["bones"].items() if k in bone_names}
    return data


def _animations():
    return {"format_version": "1.8.0", "animations": {"animation.watcher.idle": {
        "loop": True, "animation_length": 6.0, "bones": {
            "body": {"rotation": ["math.sin(query.anim_time * 60) * 0.8", "0", "math.sin(query.anim_time * 30) * 0.6"]},
            "armL": {"rotation": ["math.sin(query.anim_time * 60 + 40) * 1.2", "0", "math.sin(query.anim_time * 60) * 1.0"]},
            "armR": {"rotation": ["math.sin(query.anim_time * 60 + 100) * 1.2", "0", "-math.sin(query.anim_time * 60 + 20) * 1.0"]},
            "cloak": {"rotation": ["math.sin(query.anim_time * 120) * 1.5", "0", "math.sin(query.anim_time * 60 + 30) * 1.0"]},
            "ash": {"position": ["0", "math.sin(query.anim_time * 60) * 0.5", "0"]},
        }}}}


def main(install=False):
    OUT.mkdir(parents=True, exist_ok=True)
    cols, nights_all, report = [], [], []
    for letter in ("a", "b", "c"):
        m, geo, tex, gl, imgs, nights, overlaps = make(letter)
        label = CONCEPTS[letter][0]
        cols.append((label, imgs))
        nights_all.append(nights)
        report.append(f"{label}: boxes {len(m.boxes)} texture {tex.shape[1]}x{tex.shape[0]} "
                      f"glow px {(gl[..., 3] > 0).sum()} overlaps {overlaps if overlaps else 'none'}")
        print(report[-1], flush=True)
    note = "行: 正面3/4・横・背面3/4 (確認用に明るさ ×2.4)、頭の近景 (本来の色)。1 ブロック = 32px、0.5 単位の立方体"
    vx.sheet("見る者 (watcher) ラフ 3 案", cols, note).save(OUT / "watcher_roughs.png")
    # 夜の遠景: 3 案を縦に並べる
    w = max(n[0].width for n in nights_all) * 2 + 24
    h = sum(n[0].height for n in nights_all) + 24 * 4
    sheet = Image.new("RGB", (w, h + 40), (18, 18, 26))
    ImageDraw.Draw(sheet).text((12, 8), "夜の遠景 (左 60 ブロック、右 40 ブロック。空の光 4 前後の明るさ)", font=vx.font(20), fill=(230, 230, 240))
    y = 48
    for n in nights_all:
        sheet.paste(n[0], (12, y))
        sheet.paste(n[1], (12 + n[0].width + 12, y))
        y += n[0].height + 24
    sheet.save(OUT / "watcher_night.png")
    (OUT / "report.txt").write_text("\n".join(report) + "\n", encoding="utf-8")
    if install:
        _, fn, _ = CONCEPTS[CHOSEN]
        m, pose = fn()
        geo, tex, gl = finish_geo(m)
        assert not vx.check_overlaps(geo)
        assert (gl[..., 3] > 0).any(), "glowmask has no glowing pixel"
        (SRC / "geo" / "entity" / "watcher.geo.json").write_text(json.dumps(geo, ensure_ascii=False), encoding="utf-8")
        Image.fromarray(tex, "RGBA").save(SRC / "textures" / "entity" / "watcher.png")
        Image.fromarray(gl, "RGBA").save(SRC / "textures" / "entity" / "watcher_glowmask.png")
        (SRC / "animations" / "entity" / "watcher.animation.json").write_text(
            json.dumps(animations({b["name"] for b in geo["minecraft:geometry"][0]["bones"]}), indent=1, ensure_ascii=False) + "\n", encoding="utf-8")
        glint = np.zeros((16, 16, 4), np.uint8)
        yy, xx = np.mgrid[0:16, 0:16]
        r = np.hypot(xx - 7.5, yy - 7.5) / 7.5
        a = np.clip(1 - r, 0, 1) ** 2.2
        glint[..., 0] = (180 * a).astype(np.uint8)
        glint[..., 1] = (198 * a).astype(np.uint8)
        glint[..., 2] = (220 * a).astype(np.uint8)
        glint[..., 3] = (255 * a).astype(np.uint8)
        Image.fromarray(glint, "RGBA").save(SRC / "textures" / "entity" / "watcher_glint.png")
        print("installed", CHOSEN, flush=True)


if __name__ == "__main__":
    main(install="--install" in sys.argv)
