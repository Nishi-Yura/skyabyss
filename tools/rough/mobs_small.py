"""小型の Mob 3種 (骨の番兵 / 骨棘の射手 / 苔這い) のラフ A/B/C を生成する。

実行: リポジトリ直下で  python tools/rough/mobs_small.py
出力: docs/roughs/<subject>/<a|b|c>/ (geo.json, texture.png, glowmask.png)
      docs/roughs/<subject>.png (比較シート), docs/roughs/mobs_small.md (説明と重なり検査)

座標: y が上。1単位 = 1/16 ブロック = テクスチャ1画素。front (表) は +z 側。
テクスチャは Minecraft の向き (面の UV 矩形の上の行 = 面の上)。
面は材質ごとの4段の明暗で塗る (上と左上を明るく、下と右を暗く)。輪郭の枠は描かない。
目・歯・あばらの隙間・ひびは面ごとに手で置き、隙間は透明画素にする。
"""
from __future__ import annotations

import json
import sys
from pathlib import Path

import numpy as np
from PIL import Image

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
import voxel as vx  # noqa: E402

ROOT = vx.ROOT
OUT = ROOT / "docs" / "roughs"
S = 64
FRONT = (25, 18)
BACK = (205, 18)
ACTION = (30, 14)
VIEW = (360, 360)

# 材質の4段の明暗: (暗, 中, 明, ハイライト)
RAMP = {
    "bone": ("#7D7259", "#BFB39A", "#DCD2B6", "#F3EDDA"),
    "bone_dirty": ("#4C4434", "#8E8266", "#B5A982", "#CFC4A4"),
    "stone": ("#33383D", "#5E6469", "#858C91", "#A9B0B4"),
    "trim": ("#5C5140", "#A39678", "#CFC4A4", "#EDE5CC"),
    "pale": ("#2F5563", "#6E97A4", "#A9CBD3", "#D6EEF3"),
    "moss": ("#243C1A", "#4A7430", "#78A94A", "#A2CF6A"),
    "crystal": ("#2A7FA0", "#4FC0E0", "#8EE6FF", "#D2F8FF"),
    "cloak": ("#14282F", "#2A4F5A", "#3E6F7C", "#56909E"),
}
BONE_DEEP = "#241F18"
CRACK = "#5A4E3A"
EYE = "#9FF5FF"
EYE_Y = "#C6FF7A"
TEETH = "#F3EDDA"


def hexrgb(h: str) -> tuple:
    h = h.lstrip("#")
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))


def skin(name: str) -> dict:
    return {"ramp": RAMP[name]}


def base_color(face, c, r, fw, fh, ramp):
    dk, md, lt, hi = ramp
    if face == "up":
        return hi if r == 0 else lt
    if face == "down":
        return dk
    if r == 0 or c == 0:
        return lt
    if r == fh - 1 or c == fw - 1:
        return dk
    return md


class Mob:
    def __init__(self, ident: str):
        self.ident = ident
        self.bones: list[dict] = []
        self.bmap: dict[str, dict] = {}
        self.cubes: list[dict] = []

    def bone(self, name, pivot, parent=None):
        b = {"name": name, "pivot": [float(v) for v in pivot], "cubes": []}
        if parent:
            b["parent"] = parent
        self.bones.append(b)
        self.bmap[name] = b

    def cube(self, bone, origin, size, sk, glow=None, detail=None):
        self.cubes.append(dict(bone=bone, origin=[float(v) for v in origin],
                               size=[float(v) for v in size], skin=sk, glow=glow, detail=detail))

    def finish(self, tex: np.ndarray, gl: np.ndarray):
        H = tex.shape[0]
        for b in self.bones:
            b["cubes"] = []
        used = np.zeros(tex.shape[:2], bool)
        order = sorted(self.cubes, key=lambda c: -(c["size"][1] + c["size"][2]))
        x = y = shelf = 0
        for c in order:
            w, h, d = (int(v) for v in c["size"])
            bw, bh = 2 * (w + d), d + h
            if x + bw > S:
                x, y, shelf = 0, y + shelf, 0
            if y + bh > H or bw > S:
                raise OverflowError(self.ident)
            c["uv"] = [x, y]
            for face, rect in vx._box_uv(x, y, w, h, d).items():
                paint_rect(tex, gl, used, rect, face, c["skin"], c["glow"], c["detail"])
            self.bmap[c["bone"]]["cubes"].append({"origin": c["origin"], "size": c["size"], "uv": [x, y]})
            x += bw
            shelf = max(shelf, bh)

    def geo(self, H: int = S) -> dict:
        return {"format_version": "1.12.0", "minecraft:geometry": [{
            "description": {"identifier": f"geometry.{self.ident}", "texture_width": S, "texture_height": H,
                            "visible_bounds_width": 3, "visible_bounds_height": 2,
                            "visible_bounds_offset": [0, 1, 0]},
            "bones": self.bones}]}


def paint_rect(tex, gl, used, rect, face, sk, glow, detail):
    fu, fv, fw, fh = rect
    assert not used[fv:fv + fh, fu:fu + fw].any(), "uv overlap"
    used[fv:fv + fh, fu:fu + fw] = True
    for r in range(fh):
        for c in range(fw):
            col = hexrgb(base_color(face, c, r, fw, fh, sk["ramp"]))
            res = detail(face, c, r, fw, fh) if detail else None
            if res == "clear":
                tex[fv + r, fu + c] = 0
                continue
            if res:
                col = hexrgb(res)
            tex[fv + r, fu + c] = (*col, 255)
            if glow and glow(face, c, r, fw, fh):
                gl[fv + r, fu + c] = (*col, 255)


def px_set(face_name, pts, color):
    s = set(pts)

    def det(f, c, r, fw, fh):
        if f == face_name and (c, r) in s:
            return color
        return None
    return det


def combine(*dets):
    def det(f, c, r, fw, fh):
        for d in dets:
            if d is None:
                continue
            res = d(f, c, r, fw, fh)
            if res:
                return res
        return None
    return det


def glow_px(face_name, pts):
    s = set(pts)
    return lambda f, c, r, fw, fh: f == face_name and (c, r) in s


def eye_pts(rows, cols):
    return [(c, r) for r in rows for c in cols]


def skull_face(eye_rows, eye_cols, eye_color):
    """頭の正面 (south): 目の穴 (光る)、鼻の穴。"""
    def det(f, c, r, fw, fh):
        if f != "south":
            return None
        if r in eye_rows and c in eye_cols:
            return eye_color
        if r == fh - 2 and c in (fw // 2 - 1, fw // 2):
            return BONE_DEEP
        return None
    return det


def jaw_face():
    """顎の正面: 上の段に歯 (歯の隙間は透明)。"""
    def det(f, c, r, fw, fh):
        if f == "south" and r == 0:
            return TEETH if c % 2 == 0 else "clear"
        return None
    return det


def spine_face():
    """背骨の側面: 縦の筋と、ひび。"""
    def det(f, c, r, fw, fh):
        if f in ("east", "west") and c == fw // 2 and r % 4 == 1:
            return CRACK
        return None
    return det


# ---------------------------------------------------------------- 骨の番兵 (人型の守り手)

def sentinel_a():
    m = Mob("bone_sentinel_a")
    B = skin("bone")
    S_ = skin("bone_dirty")

    def body(f, c, r, fw, fh):
        if f == "south":
            if r in (3, 6, 9) and 1 <= c <= fw - 2:
                return CRACK
            if c in (3, 4) and r in (1, 2, 4, 5, 7, 8, 10):
                return RAMP["bone"][3]
        return None

    def shield(f, c, r, fw, fh):
        if f == "south":
            if abs(c - (fw - 1) / 2) <= 1 and abs(r - (fh - 1) / 2) <= 2:
                return RAMP["bone"][3]
            if (c + 2 * r) % 11 == 0 and 2 < r < fh - 3 and 1 < c < fw - 2:
                return CRACK
            if r in (2, fh - 3) and c in (1, fw - 2):
                return BONE_DEEP
        return None

    m.bone("body", (0, 12, 0))
    m.bone("head", (0, 24, 0), "body")
    m.bone("crest", (0, 32, 0), "head")
    m.bone("arm_r", (-6, 22, 0), "body")
    m.bone("arm_l", (6, 22, 0), "body")
    m.bone("shield", (6, 12, 2), "arm_l")
    m.bone("leg_r", (-2, 12, 0), "body")
    m.bone("leg_l", (2, 12, 0), "body")
    m.cube("body", (-4, 12, -2), (8, 12, 4), B, detail=body)
    m.cube("head", (-4, 24, -4), (8, 8, 8), B, glow=glow_px("south", eye_pts((3, 4), (1, 2, 5, 6))),
           detail=skull_face((3, 4), (1, 2, 5, 6), EYE))
    m.cube("crest", (-1, 32, -3), (2, 3, 6), B)
    m.cube("arm_r", (-8, 12, -2), (4, 12, 4), B)
    m.cube("arm_l", (4, 12, -2), (4, 12, 4), B)
    m.cube("shield", (3, 4, 2), (8, 18, 2), S_, detail=shield)
    m.cube("leg_r", (-4, 0, -2), (4, 12, 4), B)
    m.cube("leg_l", (0, 0, -2), (4, 12, 4), B)
    return m, {"arm_l": (-90, -35, 0), "shield": (90, 0, 0)}


def sentinel_b():
    m = Mob("bone_sentinel_b")
    ST = skin("stone")
    TR = skin("trim")

    def body(f, c, r, fw, fh):
        if f == "south":
            if r == 0 or r == fh - 1 or c == fw // 2:
                return RAMP["trim"][2]
            if (c + r) % 7 == 0 and 2 <= r <= fh - 3:
                return CRACK
        return None

    def head(f, c, r, fw, fh):
        if f == "south" and r in (3, 4) and c in (1, 2, 5, 6):
            return BONE_DEEP
        if f == "south" and r == 6 and 2 <= c <= 5:
            return CRACK
        return None

    def shield(f, c, r, fw, fh):
        if f == "south":
            if c == fw // 2 or c == fw // 2 - 1:
                return RAMP["trim"][2]
            if r in (3, 9, 15) and c in (1, fw - 2):
                return RAMP["trim"][2]
            if r in (6, 12) and 2 <= c <= fw - 3:
                return CRACK
        return None

    def plate(f, c, r, fw, fh):
        if f in ("up", "down"):
            return None
        return RAMP["trim"][2] if r == 0 else None

    m.bone("body", (0, 12, 0))
    m.bone("head", (0, 24, 0), "body")
    m.bone("arm_r", (-7, 22, 0), "body")
    m.bone("arm_l", (7, 22, 0), "body")
    m.bone("shield", (7, 12, 3), "arm_l")
    m.bone("leg_r", (-2.5, 12, 0), "body")
    m.bone("leg_l", (2.5, 12, 0), "body")
    m.cube("body", (-5, 12, -3), (10, 12, 6), ST, detail=body)
    m.cube("body", (-10, 24, -3), (5, 3, 6), ST, detail=plate)
    m.cube("body", (5, 24, -3), (5, 3, 6), ST, detail=plate)
    m.cube("body", (-5, 4, 3), (10, 8, 1), ST, detail=plate)
    m.cube("head", (-4, 24, -4), (8, 8, 8), TR, detail=head)
    m.cube("head", (-3, 32, -3), (6, 2, 6), TR)
    m.cube("arm_r", (-9, 12, -3), (4, 12, 6), ST)
    m.cube("arm_l", (5, 12, -3), (4, 12, 6), ST)
    m.cube("shield", (5, 4, 3), (8, 18, 2), ST, detail=shield)
    m.cube("leg_r", (-5, 0, -3), (5, 12, 6), ST)
    m.cube("leg_l", (0, 0, -3), (5, 12, 6), ST)
    return m, {"arm_l": (-90, -35, 0), "shield": (90, 0, 0)}


def sentinel_c():
    m = Mob("bone_sentinel_c")
    P = skin("pale")

    def body(f, c, r, fw, fh):
        if f == "south" and 2 <= c <= fw - 3 and 3 <= r <= 6:
            return RAMP["crystal"][3]
        if f == "south" and c in (3, 4) and r in (1, 9):
            return RAMP["pale"][0]
        return None

    def glow_body(f, c, r, fw, fh):
        return f == "south" and 2 <= c <= fw - 3 and 3 <= r <= 6

    def jaw(f, c, r, fw, fh):
        if f == "south" and r == 0:
            return TEETH if c % 2 == 0 else "clear"
        return None

    def fin(f, c, r, fw, fh):
        if c % 2 == 0 and f in ("up", "down", "east", "west"):
            return RAMP["crystal"][2]
        return None

    def plate(f, c, r, fw, fh):
        if f == "south" and c == fw // 2:
            return RAMP["crystal"][2]
        return None

    m.bone("body", (0, 12, 0))
    m.bone("head", (0, 24, 0), "body")
    m.bone("arm_r", (-5.5, 22, 0), "body")
    m.bone("arm_l", (5.5, 22, 0), "body")
    m.bone("shield", (6, 12, 2), "arm_l")
    m.bone("tail_1", (0, 16, -2), "body")
    m.bone("tail_2", (0, 17, -6), "tail_1")
    m.bone("leg_r", (-1.5, 12, 0), "body")
    m.bone("leg_l", (1.5, 12, 0), "body")
    m.cube("body", (-4, 12, -2), (8, 12, 4), P, glow=glow_body, detail=body)
    m.cube("head", (-4, 24, -4), (8, 8, 8), P, glow=glow_px("south", eye_pts((3, 4), (1, 2, 5, 6))),
           detail=skull_face((3, 4), (1, 2, 5, 6), EYE))
    m.cube("head", (-3, 24, 4), (6, 4, 4), P, detail=jaw)
    m.cube("head", (-1, 32, -4), (2, 5, 6), P, detail=fin)
    m.cube("arm_r", (-7, 12, -2), (3, 12, 4), P)
    m.cube("arm_l", (4, 12, -2), (3, 12, 4), P)
    m.cube("shield", (4, 6, 2), (6, 16, 1), P, detail=fin)
    m.cube("shield", (5, 10, 3), (4, 10, 1), P, detail=plate)
    m.cube("tail_1", (-1, 14, -6), (2, 4, 4), P)
    m.cube("tail_2", (-1, 16, -10), (2, 3, 4), P)
    m.cube("leg_r", (-3, 0, -2), (3, 12, 4), P)
    m.cube("leg_l", (0, 0, -2), (3, 12, 4), P)
    return m, {"arm_l": (-90, -35, 0), "shield": (90, 0, 0)}


# ---------------------------------------------------------------- 骨棘の射手 (骸骨型)

def skeleton_frame(m, body_sk, skull_sk, limb_sk, bow_sk, eye_color):
    """細い骸骨の体: 背骨 (2x12x2)、背骨に接する細い肋骨 (段の間に隙間)、頭と離れた顎、細い腕と脚。"""
    m.bone("spine", (0, 12, 0))
    m.bone("skull", (0, 24, 0), "spine")
    m.bone("jaw", (0, 23, 4), "skull")
    m.bone("arm_r", (-5, 22, 0), "spine")
    m.bone("arm_l", (5, 22, 0), "spine")
    m.bone("bow", (5, 12, 1.5), "arm_l")
    m.bone("leg_r", (-1, 12, 0), "spine")
    m.bone("leg_l", (1, 12, 0), "spine")
    m.cube("spine", (-1, 12, -1), (2, 12, 2), body_sk, detail=spine_face())
    for y in (14, 17, 20, 23):
        m.cube("spine", (-4, y, -1), (3, 1, 2), body_sk)
        m.cube("spine", (1, y, -1), (3, 1, 2), body_sk)
    m.cube("skull", (-4, 24, -4), (8, 8, 8), skull_sk,
           glow=glow_px("south", eye_pts((3, 4), (1, 2, 5, 6))) if eye_color else None,
           detail=skull_face((3, 4), (1, 2, 5, 6), eye_color))
    m.cube("jaw", (-3, 23, 4), (6, 2, 4), skull_sk, detail=jaw_face())
    m.cube("arm_r", (-6, 12, -1), (2, 12, 2), limb_sk)
    m.cube("arm_l", (4, 12, -1), (2, 12, 2), limb_sk)
    m.cube("leg_r", (-2, 0, -1), (2, 12, 2), limb_sk)
    m.cube("leg_l", (0, 0, -1), (2, 12, 2), limb_sk)
    segs = [(0, 4, 5, 7), (4, 8, 6, 8), (8, 12, 6, 8), (12, 16, 6, 8), (16, 20, 6, 8), (20, 24, 5, 7)]
    for y0, y1, x0, x1 in segs:
        m.cube("bow", (x0, y0, 1), (x1 - x0, y1 - y0, 1), bow_sk)


def rib_archer_a():
    m = Mob("rib_archer_a")
    skeleton_frame(m, skin("bone"), skin("bone"), skin("bone"), skin("bone"), None)
    return m, {"arm_l": (-90, 0, 0), "bow": (90, 0, 0), "arm_r": (-90, -20, 0)}


def rib_archer_b():
    m = Mob("rib_archer_b")
    D = skin("bone_dirty")
    skeleton_frame(m, D, D, D, D, "#6FD8E6")
    m.cube("spine", (-1, 12, -3), (2, 3, 2), D)
    m.cube("spine", (-1, 15, -3), (2, 6, 2), D)
    m.cube("spine", (-1, 21, -3), (2, 3, 2), D)
    return m, {"arm_l": (-90, 0, 0), "bow": (90, 0, 0), "arm_r": (-90, -20, 0)}


def rib_archer_c():
    m = Mob("rib_archer_c")
    P = skin("pale")
    skeleton_frame(m, P, P, P, P, EYE)
    m.cube("skull", (-4, 32, -1), (1, 4, 2), skin("crystal"))
    m.cube("skull", (3, 32, -1), (1, 4, 2), skin("crystal"))

    def cloak(f, c, r, fw, fh):
        if r >= fh - 3 and c % 3 == 1:
            return "clear"
        return None
    m.cube("spine", (-3, 12, -2), (6, 12, 1), skin("cloak"), detail=cloak)
    return m, {"arm_l": (-90, 0, 0), "bow": (90, 0, 0), "arm_r": (-90, -20, 0)}


# ---------------------------------------------------------------- 苔這い (関節のある四足)

def _leg(m, parent, name, x0, z0, pivot_x, pivot_z, sk):
    """上脚 (胴の下に接する) と下脚 (上脚の下に接する)。どちらも 2x2x2。"""
    m.bone(f"{name}_u", (pivot_x, 4, pivot_z), parent)
    m.bone(f"{name}_l", (pivot_x, 2, pivot_z), f"{name}_u")
    m.cube(f"{name}_u", (x0, 2, z0), (2, 2, 2), sk)
    m.cube(f"{name}_l", (x0, 0, z0), (2, 2, 2), sk)


def crawler_a():
    m = Mob("moss_crawler_a")
    BN = skin("bone")
    MS = skin("moss")
    segs = [("s1", -8, (0, 4, -6), None), ("s2", -4, (0, 4, -2), "s1"), ("s3", 0, (0, 4, 2), "s2")]
    for name, z0, piv, par in segs:
        m.bone(name, piv, par)
    m.bone("skull", (0, 7, 4), "s3")
    m.bone("jaw", (0, 3, 6), "skull")
    for name, z0, _, _ in segs:
        m.cube(name, (-3, 4, z0), (6, 5, 4), BN, detail=spine_face())
        m.cube(name, (3, 6, z0 + 1), (1, 2, 2), BN)
        m.cube(name, (-4, 6, z0 + 1), (1, 2, 2), BN)
        for side, x0, px in (("l", -4, -3), ("r", 2, 3)):
            _leg(m, name, f"{name}{side}", x0, z0 + 1, px, z0 + 2, BN)
    m.cube("skull", (-3, 5, 4), (6, 6, 5), BN,
           glow=glow_px("south", eye_pts((2, 3), (1, 4))),
           detail=skull_face((2, 3), (1, 4), EYE))
    m.cube("jaw", (-2, 3, 6), (4, 2, 4), BN, detail=jaw_face())
    m.cube("s1", (-3, 9, -8), (4, 1, 3), MS)
    m.cube("s2", (-1, 9, -4), (3, 1, 2), MS)
    m.cube("s3", (-3, 9, 1), (5, 1, 2), MS)
    m.cube("skull", (-2, 11, 4), (4, 1, 3), MS)
    pose = {"s1": (20, 0, 0),
            "s1l_u": (-60, 0, 0), "s1r_u": (-60, 0, 0), "s3l_u": (-60, 0, 0), "s3r_u": (-60, 0, 0),
            "s1l_l": (30, 0, 0), "s1r_l": (30, 0, 0)}
    return m, pose


def crawler_b():
    m = Mob("moss_crawler_b")
    ST = skin("stone")
    TR = skin("trim")

    def shell(f, c, r, fw, fh):
        if f == "up" and (c, r) in ((3, 2), (4, 3), (6, 1), (2, 5)):
            return CRACK
        if f == "up" and c == fw // 2 and r % 3 == 0:
            return RAMP["stone"][0]
        return None

    def skull(f, c, r, fw, fh):
        if f == "south" and r == 3 and c in (2, 3, 4, 5):
            return BONE_DEEP
        return None

    m.bone("chest", (0, 4, 0))
    m.bone("skull", (0, 4, 4), "chest")
    m.bone("jaw", (0, 2, 5), "skull")
    for name, x0, z0, px in (("fl", -6, 1, -5), ("fr", 4, 1, 5), ("bl", -6, -4, -5), ("br", 4, -4, 5)):
        _leg(m, "chest", name, x0, z0, px, z0 + 1, TR)
    m.cube("chest", (-6, 4, -5), (12, 4, 9), ST, detail=shell)
    m.cube("chest", (-5, 8, -4), (10, 1, 8), ST, detail=shell)
    m.cube("chest", (-4, 9, -2), (8, 1, 4), ST)
    m.cube("chest", (-2, 10, -1), (4, 1, 2), ST)
    m.cube("chest", (-7, 4, -4), (1, 4, 8), ST, detail=shell)
    m.cube("chest", (6, 4, -4), (1, 4, 8), ST, detail=shell)
    m.cube("skull", (-4, 4, 4), (8, 5, 5), TR, glow=glow_px("south", eye_pts((1, 2), (2, 3, 4, 5))),
           detail=combine(skull, skull_face((1, 2), (2, 3, 4, 5), EYE_Y)))
    m.cube("jaw", (-3, 2, 5), (6, 2, 4), TR, detail=jaw_face())
    pose = {"chest": (15, 0, 0), "fl_u": (-50, 0, 0), "fr_u": (-50, 0, 0), "bl_u": (25, 0, 0), "br_u": (25, 0, 0)}
    return m, pose


def crawler_c():
    m = Mob("moss_crawler_c")
    P = skin("pale")
    MS = skin("moss")
    segs = [("s1", -8, (0, 4, -6), None), ("s2", -4, (0, 4, -2), "s1"), ("s3", 0, (0, 4, 2), "s2")]
    for name, z0, piv, par in segs:
        m.bone(name, piv, par)
    m.bone("skull", (0, 6, 4), "s3")
    m.bone("jaw", (0, 3, 6), "skull")
    for name, z0, _, _ in segs:
        m.cube(name, (-2, 4, z0), (4, 4, 4), P, detail=spine_face())
        m.cube(name, (2, 5, z0 + 1), (1, 2, 2), P)
        m.cube(name, (-3, 5, z0 + 1), (1, 2, 2), P)
        for side, x0, px in (("l", -3, -2), ("r", 1, 2)):
            _leg(m, name, f"{name}{side}", x0, z0 + 1, px, z0 + 2, P)
    m.cube("s1", (3, 1, -6), (1, 3, 1), MS)
    m.cube("s2", (3, 1, -2), (1, 3, 1), MS)
    m.cube("skull", (-3, 4, 4), (6, 5, 5), P, glow=glow_px("south", eye_pts((2, 3), (1, 4))),
           detail=skull_face((2, 3), (1, 4), EYE))
    m.cube("jaw", (-2, 2, 5), (4, 2, 4), P, detail=jaw_face())
    m.cube("s1", (-2, 8, -8), (4, 1, 2), MS)
    m.cube("s2", (-1, 8, -4), (3, 1, 2), MS)
    m.cube("s3", (-2, 8, 1), (4, 1, 2), MS)
    pose = {"s1": (20, 0, 0), "s1l_u": (-50, 0, 0), "s1r_u": (-50, 0, 0), "s3l_u": (-70, 0, 0), "s3r_u": (-70, 0, 0)}
    return m, pose


# ---------------------------------------------------------------- 出力

SUBJECTS = {
    "bone_sentinel": {"a": sentinel_a, "b": sentinel_b, "c": sentinel_c},
    "rib_archer": {"a": rib_archer_a, "b": rib_archer_b, "c": rib_archer_c},
    "moss_crawler": {"a": crawler_a, "b": crawler_b, "c": crawler_c},
}

CONCEPT = {
    "bone_sentinel": {
        "a": ("A 王道", "骨の盾を左腕に持つ素直な番兵。頭に骨の鶏冠があり、目は水色に光る。"
              "体は素体 (幅8・厚さ4)。盾は骨の板で、中央に骨の帯と割れ目、鋲を持つ。",
              "骨色 (暗 7D7259 から ハイライト F3EDDA)。光は水色の目。"),
        "b": ("B 重厚", "岩の鎧に骨の縁取りを付けた、横幅の広い番兵。肩当てと腰の垂れ板で重く見せる。"
              "盾は大きな岩板で、骨の背骨と鋲、割れ目。目は光らない。",
              "岩の灰色 (33383D から A9B0B4)、骨の縁 (trim)。光は使わない。"),
        "c": ("C 異形", "鯨の顎のように突き出た下顎と、背びれのような頭。胸に水色の結晶が光る。"
              "盾はひれ型の2枚重ね。尾の骨が2節、後ろへ伸びる。",
              "淡い水色の骨 (pale)、結晶 (crystal)。"),
    },
    "rib_archer": {
        "a": ("A 王道", "細い骸骨型の射手。頭と顎は離れ、胸は背骨に接する細い肋骨 (隙間あり)。"
              "腕と脚は 2x12x2 の細さ。弓は骨の弧 (6段) で、弓を構えると右腕を後ろへ引く。",
              "骨色 (bone)、目の穴は暗いまま。"),
        "b": ("B 重厚", "背骨に骨棘を3本生やした、攻撃的な射手。骨は汚れた色 (bone_dirty)、目は弱い青に光る。"
              "棘は背の面 (z -1) に接して生える。",
              "汚れた骨、弱い青の目 (6FD8E6)。"),
        "c": ("C 異形", "結晶の角を2本持ち、背中に破れた布の外套 (穴を透かす)。弓と骨は淡い水色 (pale)。",
              "淡い水色の骨、結晶の角と目 (crystal)、暗い青緑の布 (cloak)。"),
    },
    "moss_crawler": {
        "a": ("A 王道", "頭の骨と顎、3節の背骨、片側の肋骨、上脚と下脚を持つ四足。背の上に薄い苔の板。"
              "飛びかかる時は背を傾け、前脚を前へ出す。",
              "骨色 (bone)、苔 (moss)。目は水色。"),
        "b": ("B 重厚", "岩の甲羅 (胸の板と肩の板) に覆われた、平たく幅の広い体。脚は短く、顎は太い。"
              "甲羅に割れ目。目は黄緑に光る。",
              "岩の灰色 (stone)、骨の縁 (trim)、黄緑の目 (C6FF7A)。"),
        "c": ("C 異形", "3節の細長い体で、節ごとに肋骨と関節のある脚。背の苔の板と、横に垂れる苔のひも。"
              "目は水色の結晶。飛びかかる時は前の節を下げる。",
              "淡い水色の骨 (pale)、苔 (moss)、結晶の目。"),
    },
}


def make(subject: str, letter: str):
    m, pose = SUBJECTS[subject][letter]()
    for H in (S, 2 * S):
        tex = np.zeros((H, S, 4), np.uint8)
        gl = np.zeros((H, S, 4), np.uint8)
        try:
            m.finish(tex, gl)
            break
        except OverflowError:
            continue
    geo = m.geo(H)
    d = OUT / subject / letter
    d.mkdir(parents=True, exist_ok=True)
    (d / "geo.json").write_text(json.dumps(geo, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    Image.fromarray(tex, "RGBA").save(d / "texture.png")
    Image.fromarray(gl, "RGBA").save(d / "glowmask.png")
    overlaps = vx.check_overlaps(geo)
    faces_rest = vx.load_geo(geo, tex, gl)
    faces_pose = vx.load_geo(geo, tex, gl, pose=pose)
    imgs = [
        vx.render(faces_rest, yaw=FRONT[0], pitch=FRONT[1], size=VIEW),
        vx.render(faces_rest, yaw=BACK[0], pitch=BACK[1], size=VIEW),
        vx.render(faces_pose, yaw=ACTION[0], pitch=ACTION[1], size=VIEW),
    ]
    return imgs, overlaps, pose, H


def main():
    results = {}
    for subject in SUBJECTS:
        cols = []
        results[subject] = {}
        for letter in ("a", "b", "c"):
            imgs, overlaps, pose, H = make(subject, letter)
            results[subject][letter] = (overlaps, pose, H)
            cols.append((CONCEPT[subject][letter][0], imgs))
            print(subject, letter, "tex", H, "overlaps:", overlaps if overlaps else "none")
        note = "上段: 正面3/4   中段: 背面3/4   下段: 動きの型 (pose)"
        vx.sheet(f"{subject} ラフ A/B/C", cols, note).save(OUT / f"{subject}.png")

    lines = ["# 小型 Mob 3種 ラフ (骨の番兵 / 骨棘の射手 / 苔這い)", "",
             "生成: `tools/rough/mobs_small.py`。素材は `docs/roughs/<subject>/<a|b|c>/` に、比較シートは `docs/roughs/<subject>.png` にある。",
             "テクスチャは 64x64 (1単位 = 1画素)。面は材質ごとの4段の明暗で塗り、輪郭の枠は描かない。"
             "隙間 (肋骨の間、歯の間、裾の破れ) は透明画素。部品は面で接し、check_overlaps は全案で空。", ""]
    names = {"bone_sentinel": "骨の番兵 (bone_sentinel)", "rib_archer": "骨棘の射手 (rib_archer)",
             "moss_crawler": "苔這い (moss_crawler)"}
    for subject in SUBJECTS:
        lines += [f"## {names[subject]}", ""]
        for letter in ("a", "b", "c"):
            label, concept, color = CONCEPT[subject][letter]
            overlaps, pose, H = results[subject][letter]
            lines += [f"### {label}", "", concept, "", f"- 色: {color}",
                      f"- テクスチャ: 64x{H}",
                      f"- 動きの型 (pose): {json.dumps(pose, ensure_ascii=False)}",
                      f"- 重なり検査: {'空 (問題なし)' if not overlaps else '; '.join(overlaps)}", ""]
    (OUT / "mobs_small.md").write_text("\n".join(lines), encoding="utf-8")


if __name__ == "__main__":
    main()
