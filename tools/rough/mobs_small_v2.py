"""小型 Mob 3種の第2回ラフ (骨の番兵 / 骨棘の射手 / 苔這い)。

前回の採用案 A を元に、高解像度の 3D にする (docs/roughs/BRIEF2.md)。
- 立方体は 0.5 単位まで。テクスチャは 1 単位 = 2 画素、面ごとの uv (per-face uv)。
- 形は彫る: 眼窩のくぼみ (穴のあいた前板)、頬骨、顎の段、節のある細い骨、0.5 段の肋骨の弧、
  背骨の椎体 (separate blocks)、盾の縁と鋲と骨の浮き彫り (層を重ねる)。
- 3案: A = 忠実な高解像度化、B = 装飾・装甲を足す、C = 有機的・動的なシルエット。
- 部品は面で接するだけ (check_overlaps が空)。

座標: y が上。前は +z。1 単位 = 1/16 ブロック。
実行: リポジトリ直下で  python tools/rough/mobs_small_v2.py
出力: docs/roughs/v2/<subject>/<a|b|c>/ (geo.json, texture.png, glowmask.png)
      docs/roughs/v2/<subject>.png (比較シート), docs/roughs/v2/<subject>.md (説明と重なり検査)
"""
from __future__ import annotations

import json
import math
import sys
from pathlib import Path

import numpy as np
from PIL import Image

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
import voxel as vx  # noqa: E402
import mobs_small as old  # noqa: E402  (参考列にだけ使う。このスクリプトは前回案のファイルを書かない)

ROOT = vx.ROOT
OUT = ROOT / "docs" / "roughs" / "v2"
TEX_W = 256
VIEW = (360, 360)
FRONT, BACK, ACTION = (25, 18), (205, 18), (30, 14)
FACES = ("north", "south", "east", "west", "up", "down")

RAMP = dict(old.RAMP)
RAMP["bone_dirty"] = old.RAMP["bone_dirty"]
BONE_DEEP = "#241F18"
CRACK = "#5A4E3A"
EYE = "#9FF5FF"
EYE_Y = "#C6FF7A"
TEETH = "#F3EDDA"


def hexrgb(h: str) -> tuple:
    h = h.lstrip("#")
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))


# ---------------------------------------------------------------- 面の塗り (ワールド座標で決める)

def world(b, face, c, r):
    """画素 (c, r) の中心のワールド座標。c は面の左から、r は面の上から。1 画素 = 0.5 単位。"""
    x0, y0, z0, x1, y1, z1 = b
    cx, cy = (c + 0.5) / 2, (r + 0.5) / 2
    if face == "south":
        return x0 + cx, y1 - cy, z1
    if face == "north":
        return x1 - cx, y1 - cy, z0
    if face == "east":
        return x1, y1 - cy, z1 - cx
    if face == "west":
        return x0, y1 - cy, z0 + cx
    if face == "up":
        return x0 + cx, y1, z0 + cy
    return x0 + cx, y0, z0 + cy  # down


def shade(ramp, face, c, r, w, h):
    dk, md, lt, hi = ramp
    if face == "up":
        return hi if (r == 0 and h > 1) else lt
    if face == "down":
        return dk
    if c == 0 or r == 0:
        return lt
    if c == w - 1 or r == h - 1:
        return dk
    return md


def in_rects(X, Y, rects):
    return any(a <= X <= b and c <= Y <= d for a, b, c, d in rects)


# ---------------------------------------------------------------- モデル

class Model:
    def __init__(self, ident: str):
        self.ident = ident
        self.bones: list[dict] = []
        self.boxes: list[dict] = []

    def bone(self, name, pivot, parent=None):
        b = {"name": name, "pivot": [float(v) for v in pivot]}
        if parent:
            b["parent"] = parent
        self.bones.append(b)

    def box(self, bone, x0, y0, z0, x1, y1, z1, mat, detail=None, glow=None):
        v = (x0, y0, z0, x1, y1, z1)
        for a in v:
            assert float(a * 2).is_integer(), (self.ident, bone, v, "0.5 単位の格子ではない")
        assert x1 > x0 and y1 > y0 and z1 > z0, (self.ident, bone, v)
        self.boxes.append(dict(bone=bone, b=tuple(float(a) for a in v), mat=mat, detail=detail, glow=glow))

    def plate(self, bone, x0, y1, z0, cells, t, mat, detail=None, glow=None):
        """0.5 単位のマス目 (cells, 上の行から) を、'#' の所だけ厚み t の板にする。穴は '.'。
        左端 x0、上端 y1、前の面 z0 から +z へ t。隣り合う行は同じ幅なら1つにまとめる。"""
        rows, cols = len(cells), len(cells[0])
        open_, rects = {}, []
        for r in range(rows + 1):
            runs = set()
            if r < rows:
                c = 0
                while c < cols:
                    if cells[r][c] == "#":
                        c0 = c
                        while c < cols and cells[r][c] == "#":
                            c += 1
                        runs.add((c0, c))
                    else:
                        c += 1
            for key in list(open_):
                if key not in runs:
                    rects.append((key[0], key[1], open_.pop(key), r))
            for key in runs:
                open_.setdefault(key, r)
        for c0, c1, r0, r1 in rects:
            self.box(bone, x0 + 0.5 * c0, y1 - 0.5 * r1, z0, x0 + 0.5 * c1, y1 - 0.5 * r0, z0 + t,
                     mat, detail, glow)

    def chain(self, bone, x_in, s, y_top, parts, zc=0.0):
        """節のある細い骨や脚。上から順に積む。x は x_in から s 方向へ w、z は ±d/2 (zc 中心)。
        parts = [(高さ, 幅, 奥行, 材質[, zc]), ...]。戻り値は最後の下端の y。"""
        y = y_top
        for p in parts:
            h, w, d, mat = p[:4]
            zcen = p[4] if len(p) > 4 else zc
            xa, xb = sorted((x_in, x_in + s * w))
            self.box(bone, xa, y - h, zcen - d / 2, xb, y, zcen + d / 2, mat)
            y -= h
        return y

    # 面の uv 割り当て (1 単位 = 2 画素)
    @staticmethod
    def face_px(b, face):
        x0, y0, z0, x1, y1, z1 = b
        sx, sy, sz = x1 - x0, y1 - y0, z1 - z0
        if face in ("north", "south"):
            return int(round(2 * sx)), int(round(2 * sy))
        if face in ("east", "west"):
            return int(round(2 * sz)), int(round(2 * sy))
        return int(round(2 * sx)), int(round(2 * sz))

    def finish(self):
        items = []
        for i, bx in enumerate(self.boxes):
            for f in FACES:
                w, h = self.face_px(bx["b"], f)
                items.append((h, w, i, f))
        items.sort(key=lambda t: (-t[0], -t[1]))
        pos, x, y, shelf = {}, 0, 0, 0
        for h, w, i, f in items:
            if x + w > TEX_W:
                x, y, shelf = 0, y + shelf, 0
            pos[(i, f)] = (x, y, w, h)
            x += w
            shelf = max(shelf, h)
        H = y + shelf
        H += H % 2
        tex = np.zeros((H, TEX_W, 4), np.uint8)
        gl = np.zeros((H, TEX_W, 4), np.uint8)
        for i, bx in enumerate(self.boxes):
            ramp = RAMP[bx["mat"]]
            for f in FACES:
                u, v, w, h = pos[(i, f)]
                for r in range(h):
                    for c in range(w):
                        X, Y, Z = world(bx["b"], f, c, r)
                        col = shade(ramp, f, c, r, w, h)
                        if bx["detail"]:
                            res = bx["detail"](X, Y, Z, f)
                            if res:
                                col = res
                        tex[v + r, u + c] = (*hexrgb(col), 255)
                        if bx["glow"] and bx["glow"](X, Y, Z, f):
                            gl[v + r, u + c] = (*hexrgb(col), 255)
        bones_out = []
        for bn in self.bones:
            cubes = []
            for i, bx in enumerate(self.boxes):
                if bx["bone"] != bn["name"]:
                    continue
                x0, y0, z0, x1, y1, z1 = bx["b"]
                uv = {}
                for f in FACES:
                    u, v, w, h = pos[(i, f)]
                    uv[f] = {"uv": [u, v], "uv_size": [w, h]}
                cubes.append({"origin": [x0, y0, z0], "size": [x1 - x0, y1 - y0, z1 - z0], "uv": uv})
            b = dict(bn)
            b["cubes"] = cubes
            bones_out.append(b)
        geo = {"format_version": "1.12.0", "minecraft:geometry": [{
            "description": {"identifier": f"geometry.{self.ident}", "texture_width": TEX_W,
                            "texture_height": H, "visible_bounds_width": 3, "visible_bounds_height": 3,
                            "visible_bounds_offset": [0, 1.5, 0]},
            "bones": bones_out}]}
        return geo, tex, gl


def grid(cols, rows, fn):
    return ["".join("#" if fn(c, r) else "." for c in range(cols)) for r in range(rows)]


def pts_mask(cols, rows, pts):
    s = set(pts)
    return ["".join("#" if (c, r) in s else "." for c in range(cols)) for r in range(rows)]


# ---------------------------------------------------------------- 盾 (骨の番兵)

def shield_layers(kind: str):
    """盾の層。戻り値は (base, rim, ribs, cross, boss) のマス目 (上の行から、0.5 単位)。
    盾は x 4..12、y 4..22 (16 x 36 マス)。kind = 'kite' (丸い上・尖った下) か 'leaf' (葉型)。"""
    cols, rows = 16, 36

    def hw(vb):
        if kind == "leaf":
            return 4.0 * max(0.0, math.sin(math.pi * vb / 18.0)) ** 0.6
        if vb < 6:
            return 1 + 3 * vb / 6
        if vb <= 16:
            return 4.0
        return math.sqrt(max(0.0, 16 - (vb - 16) ** 2))

    def ux(c):
        return (c + 0.5) / 2 - 4

    def vb(r):
        return 18 - (r + 0.5) / 2

    ins = [[abs(ux(c)) <= hw(vb(r)) for c in range(cols)] for r in range(rows)]

    def is_in(r, c):
        return 0 <= r < rows and 0 <= c < cols and ins[r][c]

    rim = [[ins[r][c] and any(not is_in(r + dr, c + dc) for dr in range(-2, 3) for dc in range(-2, 3))
            for c in range(cols)] for r in range(rows)]
    boss_c = (0.0, 10.0)
    boss = [[(ux(c) - boss_c[0]) ** 2 + (vb(r) - boss_c[1]) ** 2 <= 1.5 ** 2 and ins[r][c]
             for c in range(cols)] for r in range(rows)]
    ribs = [[ins[r][c] and not rim[r][c] and not boss[r][c]
             and abs(abs(ux(c)) - (2.0 + 0.02 * (vb(r) - 9) ** 2)) <= 0.5 and 2 <= vb(r) <= 16
             for c in range(cols)] for r in range(rows)]
    cross = [[ins[r][c] and not rim[r][c] and not ribs[r][c] and abs(vb(r) - 8) <= 0.5
              and abs(ux(c)) <= 3.5 for c in range(cols)] for r in range(rows)]

    def to_cells(m):
        return ["".join("#" if m[r][c] else "." for c in range(cols)) for r in range(rows)]
    return {"base": to_cells(ins), "rim": to_cells(rim), "ribs": to_cells(ribs),
            "cross": to_cells(cross), "boss": to_cells(boss)}


def add_shield(m: Model, opt: str):
    L = shield_layers("leaf" if opt == "c" else "kite")
    if opt == "a":
        m.plate("shield", 4, 22, 1.5, L["base"], 0.5, "bone_dirty")
        m.plate("shield", 4, 22, 2, L["rim"], 0.5, "bone")
        m.plate("shield", 4, 22, 2, L["ribs"], 0.5, "trim")
        m.plate("shield", 4, 22, 2, L["cross"], 0.5, "bone")
        m.plate("shield", 4, 22, 2.5, L["boss"], 0.5, "trim")
    elif opt == "b":
        m.plate("shield", 4, 22, 1.5, L["base"], 0.5, "stone")
        m.plate("shield", 4, 22, 2, L["rim"], 0.5, "trim")
        m.plate("shield", 4, 22, 2, L["ribs"], 0.5, "trim")
        m.plate("shield", 4, 22, 2, L["cross"], 0.5, "stone")
        m.plate("shield", 4, 22, 2.5, L["boss"], 0.5, "stone")
        # 鋲: 縁に 1 単位おきの小さな頭 (面で接する)
        rivets = pts_mask(16, 36, [(c, r) for r in range(1, 36, 4) for c in range(1, 16, 4)])
        rivet_cells = [[rivets[r][c] == "#" and L["rim"][r][c] == "#" for c in range(16)] for r in range(36)]
        m.plate("shield", 4, 22, 2.5, ["".join("#" if x else "." for x in row) for row in rivet_cells],
                0.5, "trim")
    else:
        m.plate("shield", 4, 22, 1.5, L["base"], 0.5, "pale")
        m.plate("shield", 4, 22, 2, L["rim"], 0.5, "pale")
        m.plate("shield", 4, 22, 2, L["ribs"], 0.5, "trim")
        m.plate("shield", 4, 22, 2.5, L["boss"], 0.5, "crystal", glow=lambda X, Y, Z, f: True)


# ---------------------------------------------------------------- 骨の番兵

def chest_det(X, Y, Z, face):
    if face == "south" and -3 < X < 3 and any(abs(Y - y) < 0.25 for y in (15, 17.5, 19)):
        return CRACK
    return None


EYE_RECTS_SENT = [(-3, -1, 27, 29), (1, 3, 27, 29)]


def sentinel(opt: str):
    m = Model(f"bone_sentinel_{opt}")
    BN, DK, TR = "bone", "bone_dirty", "trim"
    ST = "stone" if opt == "b" else BN
    m.bone("body", (0, 12, 0))
    m.bone("head", (0, 24, 0), "body")
    m.bone("arm_r", (-6, 20, 0), "body")
    m.bone("arm_l", (6, 20, 0), "body")
    m.bone("shield", (8, 13, 2), "arm_l")
    m.bone("leg_r", (-2, 10, 0), "body")
    m.bone("leg_l", (2, 10, 0), "body")

    # 胴
    if opt == "b":
        m.box("body", -4, 10, -2, 4, 11, 2, DK)
        m.box("body", -4, 11, -2, 4, 12, 2, TR)
        m.box("body", -1, 11, 2, 1, 12, 2.5, TR)  # 帯の留め金
    else:
        m.box("body", -4, 10, -2, 4, 12, 2, BN)
    m.box("body", -3.5, 12, -1.5, 3.5, 14, 1.5, DK)
    m.box("body", -4, 14, -2, 4, 20, 2, ST, detail=chest_det)
    m.box("body", -3, 20, -1.5, 3, 22, 1.5, DK)
    if opt == "c":
        m.box("body", -2, 15, 2, 2, 19, 2.5, "crystal", glow=lambda X, Y, Z, f: f == "south")
        # 尾の椎骨 3 節 (腰の後ろ面から後ろへ、少しずつ上がる)
        for y0, z0, z1 in ((10, -4, -2), (10.5, -6, -4), (11, -8, -6)):
            m.box("body", -1, y0, z0, 1, y0 + 2, z1, BN)

    # 頭
    m.box("head", -1.5, 22, -1, 1.5, 24, 1, DK)
    eye_col = "#3B3328" if opt == "b" else EYE

    def cranium_det(X, Y, Z, f):
        if f == "south" and Z > 3.4 and in_rects(X, Y, EYE_RECTS_SENT):
            return eye_col
        return None
    m.box("head", -4, 24, -4, 4, 32, 3.5, BN, detail=cranium_det,
          glow=lambda X, Y, Z, f: f == "south" and Z > 3.4 and in_rects(X, Y, EYE_RECTS_SENT) and opt != "b")
    face_cells = []
    for r in range(16):
        if 6 <= r <= 9:
            face_cells.append("##....####....##")
        else:
            face_cells.append("################")

    def face_det(X, Y, Z, f):
        if f == "south" and Z > 3.9 and -0.5 <= X <= 0.5 and 25.5 <= Y <= 27.5:
            return BONE_DEEP
        if f == "south" and Z > 3.9 and in_rects(X, Y, EYE_RECTS_SENT):
            return BONE_DEEP if opt != "b" else "#3B3328"
        return None
    m.plate("head", -4, 32, 3.5, face_cells, 0.5, BN, detail=face_det)
    m.box("head", -3.5, 29, 4, 3.5, 30, 4.5, BN)  # 眉の出っ張り
    m.box("head", -4.5, 25, 0, -4, 27, 3.5, BN)  # 頬骨
    m.box("head", 4, 25, 0, 4.5, 27, 3.5, BN)
    if opt == "c":
        m.box("head", -3, 22, 1.5, 3, 24, 6, "pale")  # 長く突き出た顎
        m.box("head", -3, 22, 6, 3, 22.5, 6.5, "pale")
        for x0 in (-2.5, -1.5, -0.5, 0.5, 1.5):
            m.box("head", x0, 22.5, 6.5, x0 + 0.5, 23.5, 7, TR)
    else:
        m.box("head", -3, 23, 1.5, 3, 24, 4, DK)  # 顎 (頭とは別)
        for x0 in (-2, -1, 0, 1):
            m.box("head", x0, 23, 4, x0 + 0.5, 23.5, 4.5, TR)
    # 鶏冠 (段のある)
    if opt == "c":
        m.box("head", -0.5, 32, -2, 0.5, 33, 3, DK)
        m.box("head", -0.5, 33, -4, 0.5, 34, 1, DK)
        m.box("head", -0.5, 34, -6, 0.5, 35, -1, DK)
        m.box("head", -0.5, 35, -7, 0.5, 35.5, -3, DK)
    else:
        m.box("head", -0.5, 32, -3, 0.5, 33, 3, DK)
        m.box("head", -0.5, 33, -2, 0.5, 34, 2, DK)
        m.box("head", -0.5, 34, -1, 0.5, 35, 1, DK)
    if opt == "b":
        m.box("head", -3, 32, -1, -2, 33.5, 1, TR)  # 角 (兜の飾り)
        m.box("head", 2, 32, -1, 3, 33.5, 1, TR)

    # 腕 (肩の球から手まで、節を持つ)
    m.box("arm_r", -7, 20, -1.5, -3.5, 22, 1.5, DK)
    m.box("arm_l", 3.5, 20, -1.5, 7, 22, 1.5, DK)
    arm_parts = [(5, 3, 3, BN), (0.5, 3.5, 3, DK), (4.5, 2.5, 2, BN), (0.5, 3, 3, DK), (2.5, 3, 3, BN)]
    if opt == "c":
        arm_parts = [(5, 3, 3, BN), (0.5, 3.5, 3, DK), (5, 2.5, 2, BN), (0.5, 3, 3, DK), (2.5, 3, 3, BN)]
    m.chain("arm_l", 4, 1, 20, arm_parts)
    m.chain("arm_r", -4, -1, 20, arm_parts)
    if opt == "b":
        m.box("arm_l", 3, 22, -2, 8, 23, 2, ST)           # 肩当て (1段目)
        m.box("arm_l", 3.5, 23, -1.5, 8.5, 23.5, 1.5, ST)  # 肩当て (2段目)
        m.box("arm_l", 5.5, 23.5, -0.5, 6, 24.5, 0, TR)   # 突起
        m.box("arm_r", -8, 22, -2, -3, 23, 2, ST)
        m.box("arm_r", -8.5, 23, -1.5, -3.5, 23.5, 1.5, ST)
        m.box("arm_r", -6, 23.5, -0.5, -5.5, 24.5, 0, TR)

    # 脚 (膝の節と足)
    leg_parts = [(5.5, 4, 4, BN), (0.5, 4.5, 4, DK), (3.5, 3, 3, BN), (0.5, 4, 6, BN, 1)]
    m.chain("leg_r", 0, -1, 10, leg_parts)
    m.chain("leg_l", 0, 1, 10, leg_parts)
    if opt == "b":
        m.box("leg_r", -4, 5, 2, 0, 8, 2.5, ST)  # 膝当て
        m.box("leg_l", 0, 5, 2, 4, 8, 2.5, ST)

    add_shield(m, opt)
    pose = {"arm_l": (-90, -35, 0), "shield": (90, 0, 0)}
    return m, pose


# ---------------------------------------------------------------- 骨棘の射手

def arch_skull_det(X, Y, Z, f, color=BONE_DEEP):
    if f == "south" and Z > 3.4 and in_rects(X, Y, [(-3, -1, 27.5, 29), (1, 3, 27.5, 29)]):
        return color
    return None


def archer(opt: str):
    m = Model(f"rib_archer_{opt}")
    BN, DK, TR = "bone", "bone_dirty", "trim"
    if opt == "c":
        BN = "pale"
        DK = "pale"
    m.bone("spine", (0, 12, 0))
    m.bone("skull", (0, 24, 0), "spine")
    m.bone("arm_l", (3, 21, 0), "spine")
    m.bone("arm_r", (-3, 21, 0), "spine")
    m.bone("bow", (3, 17, 0), "arm_l")
    m.bone("leg_l", (0.5, 11, 0), "spine")
    m.bone("leg_r", (-0.5, 11, 0), "spine")

    m.box("spine", -1.5, 11, -1, 1.5, 12, 1, DK)  # 骨盤
    # 椎体 (separate blocks)。椎体 1.5 + 円板 0.5 を交互に積む
    ys = [12 + 2 * k for k in range(6)]
    for k, y0 in enumerate(ys):
        zo = 0.5 if (opt == "c" and k >= 3) else 0.0
        m.box("spine", -1, y0, -1 + zo, 1, y0 + 1.5, 1 + zo, BN)
        if opt != "c":
            m.box("spine", -0.5, y0 + 0.5, -1.5, 0.5, y0 + 1, -1, BN)  # 棘突起
        m.box("spine", -0.5, y0 + 1.5, -0.5 + zo, 0.5, y0 + 2, 0.5 + zo, DK)  # 円板
    if opt == "b":
        for k in (1, 3, 5):
            y0 = ys[k]
            m.box("spine", -0.5, y0 + 0.5, -2.5, 0.5, y0 + 1, -1.5, BN)  # 骨棘 (背に生える)
            m.box("spine", -0.5, y0 + 1, -3.5, 0.5, y0 + 1.5, -2.5, BN)
    if opt == "c":
        m.box("spine", -1, 11.5, -2, 1, 12, -1, DK)  # 背骨の反り

    # 肋骨 (段のある弧)。椎体の高さに合わせる
    for y0 in (14, 16, 18, 20):
        zo = 0.5 if (opt == "c" and y0 >= 18) else 0.0
        for s in (1, -1):
            a = sorted((s * 1.0, s * 2.0))
            m.box("spine", a[0], y0, -0.5 + zo, a[1], y0 + 0.5, 0.5 + zo, DK)
            b = sorted((s * 2.0, s * 2.5))
            m.box("spine", b[0], y0 - 0.5, 0 + zo, b[1], y0, 1 + zo, DK)
        m.box("spine", -2.5, y0, 1 + zo, 2.5, y0 + 0.5, 1.5 + zo, DK)  # 前の肋
    # 肩の横木
    m.box("arm_l", 1, 21, -0.5, 3.5, 22, 0.5, DK)
    m.box("arm_r", -3.5, 21, -0.5, -1, 22, 0.5, DK)

    # 腕と脚 (細い骨、節)
    arm_parts = [(4, 1, 1, BN), (0.5, 1.5, 1, DK), (4, 1, 1, BN), (0.5, 1.5, 1, DK), (1, 1, 1, BN)]
    if opt == "c":
        arm_parts = [(4.5, 1, 1, BN), (0.5, 1.5, 1, DK), (4.5, 1, 1, BN), (0.5, 1.5, 1, DK), (1, 1, 1, BN)]
    m.chain("arm_l", 2.5, 1, 21, arm_parts)
    m.chain("arm_r", -2.5, -1, 21, arm_parts)
    leg_parts = [(5, 1, 1, BN), (0.5, 1.5, 2, DK), (4.5, 1, 1, BN), (0.5, 2, 3, BN, 0.5)]
    m.chain("leg_l", 0, 1, 11, leg_parts)
    m.chain("leg_r", 0, -1, 11, leg_parts)

    # 弓 (段のある弧と弦)。弓の下の手が握る
    bow_mat = BN if opt != "b" else DK
    bulge = {"a": 2.5, "b": 2.0, "c": 3.0}[opt]
    y_start = 10.5 if opt == "c" else 10.0
    for k in range(int((24 - y_start) / 0.5)):
        y = y_start + 0.5 * k
        x = 3.5 + bulge * (1 - ((y - 17) / 7.0) ** 2)
        x0 = round(x * 2) / 2 - 0.5
        m.box("bow", x0, y, -0.5, x0 + 0.5, y + 0.5, 0.5, bow_mat)
        if opt == "b":
            m.box("bow", x0, y, -1, x0 + 0.5, y + 0.5, -0.5, BN)
    x_mid = round((3.5 + bulge) * 2) / 2 - 0.5  # 弓の中ほどの左端 (握りはここまで)
    m.box("bow", 3.5, 17, -0.5, x_mid, 17.5, 0.5, DK)  # 握り (弓の中ほど)
    m.box("bow", 3, 10, 0.5, 3.5, 24, 1, "crystal" if opt == "c" else "trim")  # 弦

    # 頭蓋 (眼窩のくぼみ、頬骨、顎の段)
    eye_col = {"a": BONE_DEEP, "b": "#6FD8E6", "c": EYE}[opt]
    eye_glow = opt != "a"

    def sk_glow(X, Y, Z, f):
        return eye_glow and f == "south" and Z > 3.4 and in_rects(X, Y, [(-3, -1, 27.5, 29), (1, 3, 27.5, 29)])
    m.box("skull", -3.5, 24, -3.5, 3.5, 31, 3.5, BN,
          detail=lambda X, Y, Z, f: arch_skull_det(X, Y, Z, f, eye_col), glow=sk_glow)
    face = []
    for r in range(14):
        face.append("#....####....#" if 4 <= r <= 6 else "##############")

    def face_det(X, Y, Z, f):
        if f == "south" and Z > 3.9 and -0.5 <= X <= 0.5 and 26 <= Y <= 27.5:
            return BONE_DEEP
        return None
    m.plate("skull", -3.5, 31, 3.5, face, 0.5, BN, detail=face_det)
    m.box("skull", -3, 29, 4, 3, 30, 4.5, BN)
    m.box("skull", -4, 25, 0, -3.5, 27, 3, BN)
    m.box("skull", 3.5, 25, 0, 4, 27, 3, BN)
    m.box("skull", -2.5, 23, 1.5, 2.5, 24, 4, DK)  # 下顎 (頭と離れて開く)
    for x0 in (-2, -1, 0, 1):
        m.box("skull", x0, 23, 4, x0 + 0.5, 23.5, 4.5, TR)
    if opt == "b":
        m.box("skull", -2.5, 31, -0.5, -1.5, 32, 0.5, TR)  # 角
        m.box("skull", -2.5, 32, -0.5, -2, 32.5, 0.5, TR)
        m.box("skull", 1.5, 31, -0.5, 2.5, 32, 0.5, TR)
        m.box("skull", 2, 32, -0.5, 2.5, 32.5, 0.5, TR)
    if opt == "c":
        m.box("skull", -2.5, 31, -1, -1.5, 33, 0, "crystal", glow=lambda X, Y, Z, f: True)  # 結晶の角
        m.box("skull", 1.5, 31, -1, 2.5, 33, 0, "crystal", glow=lambda X, Y, Z, f: True)
        cloak_cells = []
        for r in range(24):
            row = ""
            for c in range(12):
                torn = r >= 16 and c % 3 == 1
                row += "." if torn else "#"
            cloak_cells.append(row)
        m.plate("spine", -3, 24, -1.5, cloak_cells, 0.5, "cloak")

    pose = {"arm_l": (-90, 0, 0), "bow": (90, 0, 0), "arm_r": (-90, -20, 0)}
    return m, pose


# ---------------------------------------------------------------- 苔這い

def crawler(opt: str):
    m = Model(f"moss_crawler_{opt}")
    BN, DK, TR, ST, MS = "bone", "bone_dirty", "trim", "stone", "moss"
    if opt == "c":
        BN = "pale"
    if opt == "b":
        BN = ST
    m.bone("s1", (0, 4, -6))
    m.bone("s2", (0, 4, -2), "s1")
    m.bone("s3", (0, 4, 2), "s2")
    m.bone("skull", (0, 6, 4), "s3")
    m.bone("jaw", (0, 3, 6), "skull")

    segs = ["s1", "s2", "s3"] if opt != "b" else ["s1", "s3"]
    tops = {"s1": 4, "s2": 4, "s3": 4} if opt != "c" else {"s1": 4, "s2": 5, "s3": 6}
    zcs = {"s1": -6.0, "s2": -2.0, "s3": 2.0} if opt != "b" else {"s1": -3.0, "s3": 2.0}
    legs = []
    for seg in segs:
        for side, sgn in (("l", -1), ("r", 1)):
            name = f"{seg}{side}"
            legs.append((seg, name, sgn))
            m.bone(f"{name}_u", (sgn * 1.5, tops[seg], zcs[seg]), seg)
            m.bone(f"{name}_l", (sgn * 1.5, 2.5, zcs[seg]), f"{name}_u")

    # 胴 (椎体ごとの塊。塊どうしは面で接する)
    if opt == "b":
        m.box("s2", -6, 4, -5, 6, 7, 4, ST)
        m.box("s2", -5.5, 7, -4.5, 5.5, 7.5, 3.5, ST,
              detail=lambda X, Y, Z, f: CRACK if (f == "up" and (round(X) + round(Z)) % 5 == 0) else None)
        m.box("s2", -4.5, 7.5, -3.5, 4.5, 8, 2.5, TR)
        m.box("s2", -1, 8, -3, 1, 8.5, 2, ST)
        m.box("s2", -6.5, 5, -3, -6, 7, 2, DK)
        m.box("s2", 6, 5, -3, 6.5, 7, 2, DK)
    elif opt == "c":
        m.box("s1", -2, 4, -8, 2, 8, -4, BN)
        m.box("s2", -2, 5, -4, 2, 9, 0, BN)
        m.box("s3", -2, 6, 0, 2, 10, 4, BN)
    else:
        m.box("s1", -3, 4, -8, 3, 9, -4.5, BN)
        m.box("s2", -1.5, 5, -4.5, 1.5, 8, -4, DK)  # 椎体の間の円板
        m.box("s2", -3, 4, -4, 3, 9, -0.5, BN)
        m.box("s3", -1.5, 5, -0.5, 1.5, 8, 0, DK)
        m.box("s3", -3, 4, 0, 3, 9, 4, BN)

    # 肋骨の段 (胴の横に、段を下げて流れる弧)。B は甲羅の下に入れない
    if opt != "b":
        ri = 2.0 if opt == "c" else 3.0
        for seg in ("s1", "s2", "s3"):
            zc = zcs[seg]
            for y0 in (tops[seg] + 1.5, tops[seg] + 3):
                for s in (1, -1):
                    a = sorted((s * ri, s * (ri + 0.5)))
                    m.box(seg, a[0], y0, zc - 1, a[1], y0 + 0.5, zc + 1, BN)
                    b = sorted((s * ri, s * (ri + 1.0)))
                    m.box(seg, b[0], y0 - 0.5, zc - 1, b[1], y0, zc + 1, BN)
                    c_ = sorted((s * (ri + 0.5), s * (ri + 1.5)))
                    m.box(seg, c_[0], y0 - 1, zc - 1, c_[1], y0 - 0.5, zc + 1, DK)

    # 脚 (上脚・膝・下脚・足先と爪)。膝の高さは y=2.5 で揃える
    for seg, name, sgn in legs:
        top = tops[seg]
        x_in = sgn * {"a": 1.5, "b": 3.0, "c": 0.5}[opt]
        w_th = 2.0 if opt == "b" else 1.5
        thigh = top - 2.5
        m.chain(f"{name}_u", x_in, sgn, top, [(thigh, w_th, 2, TR if opt == "b" else BN, zcs[seg])], zc=zcs[seg])
        m.chain(f"{name}_l", x_in, sgn, 2.5,
                [(0.5, w_th + 0.5, 2, DK, zcs[seg]), (1.5, w_th, 2, BN if opt != "b" else ST, zcs[seg]),
                 (0.5, w_th + 1, 3, TR, zcs[seg] + 0.5)])
        fx0 = min(x_in, x_in + sgn * (w_th + 1))
        for dx in (0.5, 1.5):
            m.box(f"{name}_l", fx0 + dx, 0, zcs[seg] + 2, fx0 + dx + 0.5, 0.5, zcs[seg] + 2.5, TR)  # 爪

    # 頭 (頭蓋、眼窩、顎)
    eye_c = EYE_Y if opt == "b" else EYE
    eyes = [(-2, -0.5, 6, 8), (0.5, 2, 6, 8)]
    zb = 7.9 if opt == "b" else 8.9  # 眼窩の奥の壁 (頭蓋の前の面) より手前

    def sk_glow(X, Y, Z, f):
        return f == "south" and Z > zb and in_rects(X, Y, eyes)

    def sk_detail(X, Y, Z, f):
        if f == "south" and Z > zb and in_rects(X, Y, eyes):
            return eye_c
        return None
    hd = ST if opt == "b" else BN
    if opt == "b":
        m.box("skull", -3, 4, 4, 3, 8, 8, hd, detail=sk_detail)
        m.box("jaw", -2, 3, 5, 2, 4, 8.5, DK)
        for x0 in (-1.5, -0.5, 0.5):
            m.box("jaw", x0, 3.5, 8.5, x0 + 0.5, 4, 9, TR)
        cells = ["##...##...##" if r <= 3 else "############" for r in range(8)]
        m.plate("skull", -3, 8, 8, cells, 0.5, hd, detail=sk_detail, glow=sk_glow)
    else:
        m.box("skull", -3, 4, 4, 3, 10, 9, hd, detail=sk_detail)
        m.box("jaw", -2, 3, 5, 2, 4, 9.5, DK)
        for x0 in (-1.5, -0.5, 0.5):
            m.box("jaw", x0, 3.5, 9.5, x0 + 0.5, 4, 10, TR)
        cells = ["##...##...##" if 4 <= r <= 7 else "############" for r in range(12)]
        m.plate("skull", -3, 10, 9, cells, 0.5, hd, detail=sk_detail, glow=sk_glow)

    # 苔 (層のある不揃いの房。塊の上に置く)
    if opt == "a":
        moss = [
            ("s1", -2.5, 9, -7.5, -0.5, 9.5, -6), ("s1", 0.5, 9, -5.5, 2.5, 9.5, -4.5),
            ("s1", -2, 9.5, -7, -1, 10, -6.5),
            ("s2", -1.5, 9, -3, 0.5, 9.5, -2),
            ("s3", -2.5, 9, 1, -0.5, 9.5, 2), ("s3", 1, 9, 0.5, 2.5, 9.5, 1.5),
            ("s3", 1.5, 9.5, 0.5, 2.5, 10, 1.5),
            ("skull", -1, 10, 5, 1, 10.5, 7.5),
        ]
    elif opt == "b":
        moss = [("s2", -2, 8.5, -3, 2, 9, -0.5)]
    else:
        moss = [
            ("s1", -1.5, 8, -7.5, 0, 8.5, -6),
            ("s2", -1, 9, -3, 1, 9.5, -1),
            ("s3", -1.5, 10, 1, -0.5, 10.5, 2),
            ("s3", -2.5, 7, 3, -2, 10, 3.5),
            ("s2", -2.5, 6.5, -0.5, -2, 8, 0),
        ]
    for bone, *box in moss:
        m.box(bone, *box, MS)

    if opt == "a":
        pose = {"s1": (20, 0, 0), "s1l_u": (-60, 0, 0), "s1r_u": (-60, 0, 0), "s3l_u": (-60, 0, 0),
                "s3r_u": (-60, 0, 0), "s1l_l": (30, 0, 0), "s1r_l": (30, 0, 0)}
    elif opt == "b":
        pose = {"s1": (15, 0, 0), "s1l_u": (25, 0, 0), "s1r_u": (25, 0, 0), "s3l_u": (-50, 0, 0), "s3r_u": (-50, 0, 0)}
    else:
        pose = {"s1": (20, 0, 0), "s1l_u": (-50, 0, 0), "s1r_u": (-50, 0, 0), "s3l_u": (-70, 0, 0), "s3r_u": (-70, 0, 0)}
    return m, pose


# ---------------------------------------------------------------- 出力

SUBJECTS = {
    "bone_sentinel": sentinel,
    "rib_archer": archer,
    "moss_crawler": crawler,
}

NAMES = {"bone_sentinel": "骨の番兵 (bone_sentinel)", "rib_archer": "骨棘の射手 (rib_archer)",
         "moss_crawler": "苔這い (moss_crawler)"}

CONCEPT = {
    "bone_sentinel": {
        "a": ("A 忠実・高解像度",
              "前回の A (盾を左腕に、鶏冠の頭) を 0.5 単位で作り直す。頭蓋は前板に眼窩の穴を開けて奥へ凹ませ、"
              "頬骨と眉の出っ張り、顎を頭と離した段として置く。腕と脚は肩・肘・手首の節を 0.5 単位で膨らませて細くする。"
              "盾は上が丸く下が尖った形を、縁・骨の筋・中央の鋲の 3 層で浮き彫りにする。",
              "骨 (bone) と暗い骨 (bone_dirty)。目は水色に光る。盾の縁と筋は明るい骨。"),
        "b": ("B 装飾・装甲",
              "A の骨格に、岩の肩当て 2 段と腰の留め金、膝当て、角の兜飾りを足す。盾は岩の板に骨の縁取り、"
              "縁に鋲を並べる。目は弱く、色は落ち着いた暗い茶。",
              "岩の灰 (stone) と骨の縁 (trim)。目は黄緑の光を使わず、暗く抑える。"),
        "c": ("C 有機・動的",
              "顎を長く前へ突き出し、鶏冠を後ろへ反らせた段の流れにする。胸に結晶の核が光る。尾の椎骨 3 節が後ろへ伸びる。"
              "盾は葉の形で、中央に結晶をはめる。",
              "淡い水色の骨 (pale)、結晶 (crystal)。"),
    },
    "rib_archer": {
        "a": ("A 忠実・高解像度",
              "前回の A (細い骸骨、弓を構える) を、椎体 (1.5 単位の塊と 0.5 単位の円板) を重ねた背骨で作る。"
              "肋骨は背骨に接する段のある弧で、胸の前に横木を渡す。頭蓋は眼窩を凹ませ、頬骨と下顎を離す。"
              "腕と脚は節を持つ。弓は段のある弧と弦。",
              "骨 (bone)、目の穴は暗いまま (BONE_DEEP)。歯は明るい骨。"),
        "b": ("B 装飾・装甲",
              "背骨に骨棘を 3 本、段を付けて背の面から生やす。弓は二重の厚み (背の層)。角を頭に 2 本。"
              "骨は汚れた色で、目は弱い青に光る。",
              "汚れた骨 (bone_dirty)、角 (trim)、目 (6FD8E6 相当) は弱く。"),
        "c": ("C 有機・動的",
              "背骨の上部を前へ反らせ、肋骨も一緒に寄せる。結晶の角を 2 本。背中に破れた布の外套を、"
              "マス目で穴を開けた板にする。弓は淡い骨、弦は結晶。",
              "淡い水色の骨 (pale)、結晶 (crystal)、暗い青緑の布 (cloak)。"),
    },
    "moss_crawler": {
        "a": ("A 忠実・高解像度",
              "前回の A (3 節の背骨、四足、頭と顎) を、節ごとに分けた背骨と肋骨の段で作る。脚は上脚・膝・下脚・足先と爪。"
              "頭は前板に眼窩の穴を開け、顎は別の段。苔は層のある不揃いの房。",
              "骨 (bone)、苔 (moss)、目は水色。"),
        "b": ("B 装飾・装甲",
              "岩の甲羅を層で重ね、割れ目を入れる。脚は帯 (trim) で飾る。頭は岩の板と黄緑の目。苔は甲羅の上の一房だけ。",
              "岩の灰 (stone)、骨の縁 (trim)、黄緑の目 (C6FF7A)。"),
        "c": ("C 有機・動的",
              "3 節の体が高さを変えながら連なり、飛びかかる姿勢を出す。節ごとに肋骨と関節のある脚。苔のひもを横に垂らす。"
              "目は水色の結晶。",
              "淡い水色の骨 (pale)、苔 (moss)、結晶の目。"),
    },
}


def old_images(subject: str):
    m, pose = old.SUBJECTS[subject]["a"]()
    for H in (old.S, 2 * old.S):
        tex = np.zeros((H, old.S, 4), np.uint8)
        gl = np.zeros((H, old.S, 4), np.uint8)
        try:
            m.finish(tex, gl)
            break
        except OverflowError:
            continue
    geo = m.geo(H)
    return render_set(geo, tex, gl, pose)


def render_set(geo, tex, gl, pose):
    rest = vx.load_geo(geo, tex, gl)
    posed = vx.load_geo(geo, tex, gl, pose=pose)
    return [vx.render(rest, yaw=FRONT[0], pitch=FRONT[1], size=VIEW),
            vx.render(rest, yaw=BACK[0], pitch=BACK[1], size=VIEW),
            vx.render(posed, yaw=ACTION[0], pitch=ACTION[1], size=VIEW)]


def make(subject: str, letter: str):
    m, pose = SUBJECTS[subject](letter)
    geo, tex, gl = m.finish()
    overlaps = vx.check_overlaps(geo)
    d = OUT / subject / letter
    d.mkdir(parents=True, exist_ok=True)
    (d / "geo.json").write_text(json.dumps(geo, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    Image.fromarray(tex, "RGBA").save(d / "texture.png")
    Image.fromarray(gl, "RGBA").save(d / "glowmask.png")
    imgs = render_set(geo, tex, gl, pose)
    nbox = len(m.boxes)
    return imgs, overlaps, pose, tex.shape[0], nbox


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    for subject in SUBJECTS:
        cols = [("前回の A (参考)", old_images(subject))]
        meta = {}
        for letter in ("a", "b", "c"):
            imgs, overlaps, pose, H, nbox = make(subject, letter)
            meta[letter] = (overlaps, pose, H, nbox)
            cols.append((CONCEPT[subject][letter][0], imgs))
            print(subject, letter, "tex", f"{TEX_W}x{H}", "boxes", nbox,
                  "overlaps:", overlaps if overlaps else "none")
        note = "上段: 正面3/4   中段: 背面3/4   下段: 動きの型 (pose)   2px/単位・0.5 単位の立方体"
        vx.sheet(f"{subject} ラフ 第2回 (高解像度)", cols, note).save(OUT / f"{subject}.png")

        lines = [f"# {NAMES[subject]} 第2回ラフ (高解像度)", "",
                 f"生成: `tools/rough/mobs_small_v2.py`。素材は `docs/roughs/v2/{subject}/<a|b|c>/`、"
                 f"比較シートは `docs/roughs/v2/{subject}.png`。左端の列は前回の採用案 A (参考)。",
                 "立方体は 0.5 単位まで。テクスチャは 1 単位 = 2 画素、面ごとの uv。部品は面で接するだけ。", ""]
        for letter in ("a", "b", "c"):
            label, concept, color = CONCEPT[subject][letter]
            overlaps, pose, H, nbox = meta[letter]
            lines += [f"## {label}", "", concept, "", f"- 色: {color}",
                      f"- テクスチャ: {TEX_W}x{H}",
                      f"- 立方体の数: {nbox}",
                      f"- 動きの型 (pose): {json.dumps(pose, ensure_ascii=False)}",
                      f"- 重なり検査: {'空 (問題なし)' if not overlaps else '; '.join(overlaps)}", ""]
        (OUT / f"{subject}.md").write_text("\n".join(lines), encoding="utf-8")


if __name__ == "__main__":
    main()
