"""岩鯨の背に棲む者たちのモデル・テクスチャ・アニメーションを作る (GeckoLib / Bedrock形式)。

使い方: python tools/gen_mobs.py [--preview 出力先ディレクトリ]
出力先: src/main/resources/assets/skyabyss/
  geo/entity/<名前>.geo.json, textures/entity/<名前>.png (+ _glowmask.png), animations/entity/<名前>.animation.json
  textures/entity/bone_spike.png (骨棘の射手が放つ骨の棘)

  bone_sentinel     骨の番兵     : 鯨骨の鎧の剣士。ぼろ布のマントと骨の盾
  rib_archer        骨棘の射手   : 肋骨の弓を持つ細身の射手。フードと棘の矢筒
  moss_crawler      苔這い       : 苔に覆われた骨の四つ足。低く素早く飛びかかる
  waterway_maw      水路の顎     : 闇の水路を泳ぐ、琥珀の目の細長い魚の骨格
  whalebone_captain 鯨骨の番兵長 : 鯨骨の鉾と破れたマントの番兵の長。胸に青い核

単位は px (16px = 1ブロック)。足元が y=0、正面は -Z。テクスチャは宣言サイズの2倍で描く (32px/ブロック相当)。
アニメーションの sin/cos は Molang の度数法 (1秒あたりの角度)。ループ長 x 速度 は 360 の倍数にする。
テクスチャは全て決定的 (シード固定の乱数のみ)。光る部分は _glowmask.png に書き、それ以外は透明にする。
"""

# ---------------------------------------------------------------------------
# 上書き防止 (docs/review/assets_audit.md の S1): 実行すると採用済みの素材を古い版で上書きする。
#   上書きするもの: 番兵・射手・苔這い・顎・番兵長の geo / png / glowmask / animation
#   採用版の生成元: tools/rough/rib_archer_v4.py、tools/rough/captain_v4.py (ほかは今の src/ が正本)
# どうしても動かすときだけ、環境変数 SKYABYSS_ALLOW_REGEN=1 を付ける。一覧は tools/README.md。
# ---------------------------------------------------------------------------
import os as _os
import sys as _sys

if __name__ == "__main__" and _os.environ.get("SKYABYSS_ALLOW_REGEN") != "1":
    _sys.exit("tools/gen_mobs.py は停止中です: 採用済みの素材 (番兵・射手・苔這い・顎・番兵長の geo / png / glowmask / animation) を上書きするため。"
              "採用版の生成元は tools/rough/rib_archer_v4.py、tools/rough/captain_v4.py (ほかは今の src/ が正本) です。どうしても実行するときは SKYABYSS_ALLOW_REGEN=1 を付けてください (tools/README.md)。")
# ハイポリ化 (docs/design/15 の 8 章、担当 1a): 肋の射手・苔這い・水路の顎は、採用済みの geo / png を tools/sources/hp_base/entity/ に控えてあり、
# tools/rough/hp_mobs_whale_tide.py (--install) がそこから細かく作り直して assets/ に置く。このスクリプトを動かす (止めてある) 場合は、
# そのあとで hp_mobs_whale_tide.py --install を実行し直す。
import json
import math
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parent.parent / "src/main/resources/assets/skyabyss"
RES = 2

PALETTE = {
    "bone": ((230, 220, 196), (164, 150, 124)),
    "darkbone": ((178, 164, 138), (112, 98, 80)),
    "stone": ((104, 112, 128), (54, 58, 70)),
    "moss": ((112, 152, 72), (46, 72, 34)),
    "hide": ((72, 90, 96), (38, 50, 56)),
    "teal": ((58, 110, 116), (22, 48, 56)),
    "cloth": ((96, 92, 104), (46, 42, 54)),
    "brass": ((184, 146, 88), (104, 76, 44)),
    "socket": ((56, 48, 44), (24, 20, 20)),
    "eye": ((20, 18, 24), (14, 12, 16)),
    "eye_green": ((24, 34, 20), (12, 18, 10)),
    "eye_amber": ((44, 28, 12), (20, 12, 6)),
    "core": ((60, 140, 170), (30, 80, 100)),
    "wood": ((96, 74, 56), (62, 46, 34)),
}
GLOW = {"eye": (120, 235, 255), "eye_green": (140, 255, 120), "eye_amber": (255, 190, 90), "core": (150, 240, 255)}
YELLOW = np.array((196, 170, 112.0))


# ---------------------------------------------------------------- 形の組み立て
class Model:
    def __init__(self, name, tex_w, tex_h):
        self.name = name
        self.tex_w = tex_w
        self.tex_h = tex_h
        self.bones = []

    def bone(self, name, parent, pivot, rotation=None):
        b = {"name": name, "pivot": list(pivot), "cubes": []}
        if parent:
            b["parent"] = parent["name"] if isinstance(parent, dict) else parent
        if rotation:
            b["rotation"] = list(rotation)
        self.bones.append(b)
        return b

    def cube(self, bone, origin, size, material, inflate=0, decal=None):
        c = {"origin": list(origin), "size": list(size), "material": material, "inflate": inflate}
        if decal:
            c["decal"] = decal
        bone["cubes"].append(c)


def box(first, *rest, inflate=0, decal=None):
    """対角の2点から箱を作る (どちらが小さくてもよい)。第1引数は Model でも骨 (dict) でもよい"""
    if isinstance(first, Model):
        bone, (x0, x1, y0, y1, z0, z1, mat) = rest[0], rest[1:]
    else:
        bone, (x0, x1, y0, y1, z0, z1, mat) = first, rest
    Model.cube(None, bone, (min(x0, x1), min(y0, y1), min(z0, z1)),
               (abs(x1 - x0), abs(y1 - y0), abs(z1 - z0)), mat, inflate, decal)


def _leg(m, b, x0, s, lw, leg_h):
    """太い腿、細い脛、膝当て、足と骨の爪"""
    box(m, b, x0, x0 + lw, leg_h * 0.5, leg_h, -lw / 2, lw / 2, "bone")  # 大腿
    box(m, b, x0 + 0.6 * s, x0 + lw - 0.6 * s, 1.6 * s, leg_h * 0.5, -lw / 2 + 0.5, lw / 2 - 0.5, "bone")  # 脛
    box(m, b, x0 - 0.2 * s, x0 + lw + 0.2 * s, leg_h * 0.5 - 1.5 * s, leg_h * 0.5 + 1.5 * s, -lw / 2 - 0.5, -lw / 2 + 0.3, "stone")  # 膝当て
    box(m, b, x0 - 0.2 * s, x0 + lw + 0.2 * s, 3.0 * s, 4.6 * s, -lw / 2 - 0.2, -lw / 2 + 0.3, "stone", inflate=0.1)  # 脛の覆い
    box(m, b, x0 - 0.4 * s, x0 + lw + 0.4 * s, 0, 1.8 * s, -lw / 2 - 2.4, -lw / 2 + 0.6, "darkbone")  # 足
    for k in range(3):  # 骨の爪
        tx = x0 + 0.4 * s + k * (lw - 1.2 * s) / 2
        box(m, b, tx, tx + 1.0 * s, 0, 1.0 * s, -lw / 2 - 3.4, -lw / 2 - 2.2, "bone")


def skull(m, head, base, hs, s, eye_mat="eye", jaw_name="jaw"):
    """層になった頭蓋: 脳頭、後頭部、眉、眼窩 (光る目)、鼻、上顎、歯。下顎は head の子 (jaw_name) として返す"""
    h = hs
    box(m, head, -h / 2, h / 2, base + 2.4 * s, base + h, -h / 2, h / 2, "bone", decal="skull")
    box(m, head, -h * 0.34, h * 0.34, base + h, base + h + 1.2 * s, h / 2 - 1.8 * s, h / 2 - 0.2, "darkbone")  # 後頭部
    box(m, head, -h / 2 - 0.5, h / 2 + 0.5, base + h - 2.6 * s, base + h - 1.2 * s, -h / 2 - 1.1, -h / 2 + 0.2, "darkbone")  # 眉
    for sx in (-1, 1):
        cx = sx * h * 0.24
        box(m, head, cx - h * 0.16, cx + h * 0.16, base + h * 0.36, base + h * 0.62, -h / 2 - 0.3, -h / 2 + 0.5, "socket")
        box(m, head, cx - h * 0.1, cx + h * 0.1, base + h * 0.42, base + h * 0.56, -h / 2 - 0.9, -h / 2 - 0.1, eye_mat)
    box(m, head, -h * 0.08, h * 0.08, base + h * 0.22, base + h * 0.34, -h / 2 - 0.7, -h / 2 + 0.2, "socket")  # 鼻孔
    box(m, head, -h * 0.36, h * 0.36, base + 1.8 * s, base + 3.0 * s, -h / 2 - 2.0, -h / 2 + 1.0, "bone", decal="snout")  # 上顎
    for i in range(4):  # 上の歯 (下向き)
        tx = -h * 0.27 + i * h * 0.18
        box(m, head, tx - 0.4, tx + 0.4, base + 1.1 * s, base + 1.8 * s, -h / 2 - 1.8, -h / 2 - 1.0, "bone")
    jaw = m.bone(jaw_name, "head", (0, base + 1.1 * s, -h / 2 + 0.5))  # 下顎 (蝶番は上顎の下)
    box(m, jaw, -h * 0.32, h * 0.32, base - 0.8 * s, base + 1.1 * s, -h / 2 - 1.6, -h / 2 + 0.8, "bone")
    for i in range(3):  # 下の歯 (下向き)
        tx = -h * 0.2 + i * h * 0.2
        box(m, jaw, tx - 0.4, tx + 0.4, base - 1.4 * s, base - 0.8 * s, -h / 2 - 1.9, -h / 2 - 1.2, "bone")
    return jaw


def humanoid(m, s, width=10, arm=3, body_mat="darkbone", with_head=True, ribs=True, eye_mat="eye"):
    """二本足の骨の体。肋骨、脊椎、腕 (前腕と手の子骨つき)、頭蓋を持つ。scale は全体の倍率"""
    leg_h, body_h = 14 * s, 13 * s
    w, a, lw = width * s, arm * s, 4 * s
    base = leg_h + body_h
    shoulder = base - 1 * s
    m.bone("root", None, (0, 0, 0))
    for side, x0, px in (("left", 0.5 * s, 2.5 * s), ("right", -0.5 * s - lw, -2.5 * s)):
        leg = m.bone(f"{side}_leg", "root", (px, leg_h, 0))
        _leg(m, leg, x0, s, lw, leg_h)

    body = m.bone("body", "root", (0, leg_h, 0))
    box(m, body, -w * 0.4, w * 0.4, leg_h, leg_h + body_h * 0.42, -2.6 * s, 2.6 * s, "darkbone")  # 腹
    box(m, body, -w * 0.44, w * 0.44, leg_h - 1.2 * s, leg_h + 1.0 * s, -3.0 * s, 3.0 * s, "stone")  # 骨盤の帯
    box(m, body, -w / 2, w / 2, leg_h + body_h * 0.42, base - 0.6 * s, -3 * s, 3 * s, body_mat)  # 胸郭の奥
    if ribs:
        for i in range(4):  # 肋骨: 正面の弧と、横へ回り込む二段の節
            y0 = leg_h + body_h * 0.46 + i * 2.2 * s
            box(m, body, -w * 0.34, w * 0.34, y0, y0 + 1.0 * s, -3 * s - 1.3, -3 * s - 0.4, "bone")
            for sx in (-1, 1):
                box(m, body, sx * w * 0.34, sx * w * 0.44, y0 + 0.1 * s, y0 + 0.9 * s, -3 * s - 1.0, -3 * s - 0.2, "bone")
                box(m, body, sx * w * 0.44, sx * (w * 0.5 + 0.4), y0 + 0.2 * s, y0 + 0.8 * s, -3 * s - 0.2, -3 * s + 0.9, "bone")
    for i in range(5):  # 背骨 (背中の棘つき)
        y0 = leg_h + 1.0 * s + i * 2.5 * s
        box(m, body, -0.9 * s, 0.9 * s, y0, y0 + 1.7 * s, 3 * s, 3 * s + 1.5, "bone")
        if i % 2 == 1:
            box(m, body, -0.35 * s, 0.35 * s, y0 + 1.0 * s, y0 + 2.2 * s, 3 * s + 1.5, 3 * s + 2.5, "bone")
    box(m, body, -1.6 * s, 1.6 * s, base - 1.2 * s, base + 0.6, -1.4 * s, 1.4 * s, "darkbone")  # 首
    box(m, body, -w / 2 - 0.5, w / 2 + 0.5, base - 2.0 * s, base - 0.4 * s, -3.6 * s, 2.6 * s, "bone")  # 鎖骨の板

    head = None
    if with_head:
        head = m.bone("head", "body", (0, base, 0))
        skull(m, head, base, 8 * s, s, eye_mat=eye_mat)

    arms = {}
    for side, sx in (("left", 1), ("right", -1)):
        px = sx * (w / 2 + a / 2)
        xa, xb = sorted((sx * w / 2, sx * (w / 2 + a)))
        arm = m.bone(f"{side}_arm", "body", (px, shoulder, 0))
        box(m, arm, xa, xb, shoulder - 7.0 * s, shoulder, -a / 2, a / 2, "bone")  # 上腕
        box(m, arm, xa - 0.2, xb + 0.2, shoulder - 8.0 * s, shoulder - 6.0 * s, -a / 2 - 0.2, a / 2 + 0.2, "darkbone")  # 肘
        fore = m.bone(f"{side}_forearm", f"{side}_arm", (px, shoulder - 7.0 * s, 0))
        box(m, fore, xa, xb, shoulder - 14.5 * s, shoulder - 7.0 * s, -a / 2, a / 2, "bone")  # 前腕
        box(m, fore, xa - 0.5, xb + 0.5, shoulder - 13.5 * s, shoulder - 9.5 * s, -a / 2 - 0.5, a / 2 + 0.5, "stone")  # 籠手
        box(m, fore, xa - 0.2, xb + 0.2, shoulder - 16.5 * s, shoulder - 14.5 * s, -a / 2 - 0.2, a / 2 + 0.2, "darkbone")  # 手
        for k in range(3):  # 骨の爪
            fx = xa + (k + 0.2) * a / 3
            box(m, fore, fx, fx + a / 3 - 0.3, shoulder - 18.0 * s, shoulder - 16.2 * s, -a / 2 - 0.4, -a / 2 + 0.6, "bone")
        arms[side] = (arm, fore)
    return {"body": body, "head": head, "left_arm": arms["left"][0], "right_arm": arms["right"][0],
            "left_forearm": arms["left"][1], "right_forearm": arms["right"][1],
            "w": w, "a": a, "shoulder": shoulder, "leg_h": leg_h, "body_h": body_h, "base": base,
            "hand_y": shoulder - 15.5 * s}


def pauldrons(m, h, s, big=1.0):
    """肩当て: 二段の板、縁の骨、肩の棘 (腕の子骨に付く)"""
    w, a, sh = h["w"], h["a"], h["shoulder"]
    t = 3.0 * big * s
    for side, sx in (("left", 1), ("right", -1)):
        arm = h[f"{side}_arm"]
        xa, xb = sorted((sx * (w / 2 - 0.8), sx * (w / 2 + a + 0.8)))
        box(m, arm, xa, xb, sh - 1.4 * s, sh - 1.4 * s + 0.6 * t, -a / 2 - 1.2, a / 2 + 1.2, "stone", inflate=0.2)
        box(m, arm, xa + 0.4, xb - 0.4, sh - 3.0 * s, sh - 3.0 * s + 0.5 * t, -a / 2 - 1.6, a / 2 + 1.6, "darkbone")
        box(m, arm, xa, xb, sh - 1.4 * s + 0.6 * t - 0.2, sh - 1.4 * s + 0.6 * t + 0.4, -a / 2 - 1.2, a / 2 + 1.2, "bone")
        xs = sx * (w / 2 + a + 0.2)
        box(m, arm, xs - 0.6, xs + 0.6, sh + 0.4, sh + 0.4 + 2.0 * big * s, -0.8, 0.8, "bone")


def tattered_cape(m, parent, px, py, pz, width, upper, tatters, name="cape", mat="cloth", lean=10, depth=0.8):
    """肩から垂れる布。上半分は一枚、下半分は別の子骨 (name_lo) に付き、長さの違う破れた帯になる。
    子骨は後ろへ少し傾き、アニメーションで遅れて揺れる"""
    top = m.bone(name, parent, (px, py, pz))
    box(m, top, px - width / 2, px + width / 2, py - upper, py, pz, pz + depth, mat)
    lo = m.bone(f"{name}_lo", name, (px, py - upper, pz), rotation=[lean, 0, 0])
    n = len(tatters)
    cw = width / n
    for i, L in enumerate(tatters):
        x = px - width / 2 + (i + 0.5) * cw
        box(m, lo, x - cw * 0.42, x + cw * 0.42, py - upper - L, py - upper, pz, pz + depth * 0.8, mat)
    return top


def _rod(m, bone, x, y0, y1, z, mat, r=0.5):
    box(m, bone, x - r, x + r, y0, y1, z - r, z + r, mat)


def bone_sentinel():
    m = Model("bone_sentinel", 192, 192)
    s = 1.05
    h = humanoid(m, s, width=10, arm=3)
    head, base, hs = h["head"], h["base"], 8 * s
    T = base + hs  # 頭頂
    # 曲がった角: 三段の骨 + 先端
    for sx, sign, side in ((-1, 1, "l"), (1, -1, "r")):
        xb = sx * 2.6 * s
        hb = m.bone(f"horn_{side}", "head", (xb, T - 0.5, -1), rotation=[0, 0, 25 * sign])
        box(m, hb, xb - 1.1, xb + 1.1, T - 0.5, T + 2.2, -2.2, -0.0, "darkbone")
        h2 = m.bone(f"horn_{side}2", f"horn_{side}", (xb, T + 2.5, -1), rotation=[0, 0, 30 * sign])
        box(m, h2, xb - 0.9, xb + 0.9, T + 2.2, T + 4.8, -1.9, -0.1, "bone")
        h3 = m.bone(f"horn_{side}3", f"horn_{side}2", (xb, T + 5.1, -1), rotation=[0, 0, 25 * sign])
        box(m, h3, xb - 0.6, xb + 0.6, T + 4.8, T + 7.0, -1.6, -0.2, "darkbone")
        box(m, h3, xb - 0.25, xb + 0.25, T + 6.9, T + 8.4, -1.2, -0.6, "bone")  # 角の先
    box(m, head, -0.6, 0.6, T - 0.2, T + 1.2, -hs / 2 + 1.0, hs / 2 - 1.0, "darkbone")  # 兜の鰭
    # 肩当て、ぼろ布のマント
    pauldrons(m, h, s, big=1.0)
    tattered_cape(m, h["body"], 0, h["leg_h"] + h["body_h"] - 1.0, 3.6 * s, h["w"] * 0.9, 9, [7, 11, 5, 9, 6],
                  name="cape", mat="cloth", lean=10)
    # 大剣 (右の手)
    hx, hy = -(h["w"] / 2 + h["a"] / 2), h["hand_y"]
    wp = m.bone("weapon", "right_forearm", (hx, hy, 0))
    box(wp, hx - 0.9, hx + 0.9, hy - 0.6, hy + 1.8, -17, -2, "bone")  # 刃
    box(wp, hx - 0.9, hx + 0.9, hy - 0.1, hy + 1.4, -19, -17, "stone")  # 切っ先
    box(wp, hx - 2.8, hx + 2.8, hy - 0.7, hy + 1.7, -2.4, -1.2, "stone")  # 鍔
    box(wp, hx - 0.7, hx + 0.7, hy - 0.6, hy + 1.4, -1.2, 2.0, "darkbone")  # 柄
    box(wp, hx - 1.1, hx + 1.1, hy - 0.9, hy + 1.9, 2.0, 3.0, "stone")  # 柄頭
    # 骨の盾 (左の前腕の外側)
    sh = m.bone("shield", "left_forearm", (h["w"] / 2 + h["a"] / 2, h["shoulder"] - 7.0 * s, 0))
    ox = h["w"] / 2 + h["a"]
    box(sh, ox + 0.5, ox + 2.0, h["shoulder"] - 15.5 * s, h["shoulder"] - 4.5 * s, -5, 5, "darkbone")
    for yb in (h["shoulder"] - 13.0 * s, h["shoulder"] - 9.0 * s, h["shoulder"] - 5.4 * s):
        box(sh, ox + 2.0, ox + 2.6, yb, yb + 1.0, -4.8, 4.8, "bone")  # 肋状の骨
    box(sh, ox + 2.0, ox + 3.0, h["shoulder"] - 10.5 * s, h["shoulder"] - 8.5 * s, -1.5, 1.5, "stone")  # 盾の中心
    return m


def rib_archer():
    m = Model("rib_archer", 192, 192)
    s = 0.98
    h = humanoid(m, s, width=8, arm=2.6, eye_mat="eye_green")
    head, base, hs = h["head"], h["base"], 8 * s
    # 深いフード (上と後ろを覆い、顔と目は開けておく)
    box(m, head, -hs / 2 - 0.8, hs / 2 + 0.8, base + hs - 0.6, base + hs + 2.6 * s, -hs / 2 - 0.8, hs / 2 + 1.0, "moss")
    box(m, head, -hs / 2 - 0.8, hs / 2 + 0.8, base + 1.0, base + hs - 0.6, hs / 2, hs / 2 + 1.4, "moss")
    for sx in (-1, 1):
        box(m, head, sx * (hs / 2 + 0.2), sx * (hs / 2 + 1.0), base + 1.0, base + hs, -hs / 2 - 0.8, hs / 2, "moss")
    box(m, head, -hs / 2 - 0.8, hs / 2 + 0.8, base + hs - 0.4, base + hs + 0.4, -hs / 2 - 1.8, -hs / 2 - 0.6, "hide")  # 庇
    box(m, head, -2, 2, base - 1.5, base + 1.0, hs / 2 + 1.0, hs / 2 + 1.6, "cloth")  # 垂れ布
    # ぼろのマント、背の矢筒
    tattered_cape(m, h["body"], 0, h["leg_h"] + h["body_h"] - 1.0, 3.6 * s, h["w"] + 0.5, 9, [6, 10, 7, 9],
                  name="cape", mat="hide", lean=12, depth=0.7)
    quiver = m.bone("quiver", "body", (0, h["leg_h"] + 8 * s, 3 * s), rotation=[0, 0, 10])
    box(quiver, -2.4, -0.2, h["leg_h"] + 3 * s, h["leg_h"] + 12 * s, 3 * s + 1.4, 3 * s + 3.0, "wood")
    for i in range(3):
        _rod(m, quiver, -2.0 + i * 0.8, h["leg_h"] + 12 * s, h["leg_h"] + 15 * s, 3 * s + 2.0, "bone", r=0.3)
    box(quiver, -2.6, 0, h["leg_h"] + 2.5 * s, h["leg_h"] + 3.5 * s, 3 * s + 1.2, 3 * s + 3.2, "hide")
    # 肋骨の弓 (左の手の外側)
    hy = h["hand_y"]
    xb = h["w"] / 2 + h["a"] + 1.4 * s
    bow = m.bone("bow", "left_forearm", (xb, hy, 0))
    box(bow, xb - 0.6, xb + 0.6, hy - 8.0, hy + 8.0, -4.6, -2.6, "bone")
    box(bow, xb - 1.0, xb + 1.0, hy - 1.5, hy + 1.5, -4.8, -2.4, "darkbone")  # 握り
    box(bow, xb - 1.3, xb - 0.9, hy - 8.0, hy + 8.0, -3.9, -3.5, "hide")  # 弦
    box(bow, xb - 0.6, xb + 0.6, hy - 9.2, hy - 7.6, -4.6, -2.6, "darkbone")
    box(bow, xb - 0.6, xb + 0.6, hy + 7.6, hy + 9.2, -4.6, -2.6, "darkbone")
    pauldrons(m, h, s, big=0.7)
    return m


def moss_crawler():
    m = Model("moss_crawler", 192, 192)
    m.bone("root", None, (0, 0, 0))
    body = m.bone("body", "root", (0, 7, 0))
    box(m, body, -4.5, 4.5, 5, 11, -9, 9, "hide")  # 胴
    box(m, body, -3.5, 3.5, 11, 14, -4, 4, "hide")  # 猫背の盛り上がり
    box(m, body, -4, 4, 3.8, 5.0, -8, 8, "darkbone")  # 腹の骨板
    box(m, body, -4.6, 4.6, 10.9, 11.5, -8.5, 7.5, "moss")  # 背の苔
    for i in range(4):  # 脇腹の肋骨
        z0 = -6 + i * 3.5
        for sx in (-1, 1):
            box(m, body, sx * 4.4, sx * 5.6, 5.4, 9.2, z0, z0 + 1.2, "bone")
    for i in range(5):  # 背の骨板と苔の房
        z0 = -7 + i * 3.4
        box(m, body, -1.6, 1.6, 13.5, 15.2, z0, z0 + 2.2, "bone")
        box(m, body, -2.2 + (i % 2) * 0.6, 1.8 - (i % 2) * 0.4, 15.2, 16.2, z0 - 0.3, z0 + 2.6, "moss")
    head = m.bone("head", "body", (0, 9, -9))
    box(m, head, -5, 5, 5, 10.5, -16, -9, "hide")  # 平たい頭蓋
    box(m, head, -5.4, 5.4, 9.6, 10.8, -16.6, -14.6, "darkbone")  # 眉の骨
    box(m, head, -0.6, 0.6, 10.5, 12.3, -14, -10, "bone")  # 頭の棘
    box(m, head, -5.2, 5.2, 10.8, 11.4, -15.5, -9.5, "moss")  # 頭頂の苔
    for sx in (-1, 1):
        box(m, head, sx * 3.0 - 1.2, sx * 3.0 + 1.2, 6.2, 9.0, -16.6, -15.8, "socket")
        box(m, head, sx * 3.0 - 0.8, sx * 3.0 + 0.8, 6.8, 8.6, -17.0, -16.4, "eye_green")
    box(m, head, -4.5, 4.5, 3.4, 5.2, -18.4, -16, "bone", decal="snout")  # 上顎
    for x in (-3.2, -1.2, 0.8, 2.8):
        box(m, head, x, x + 0.9, 2.2, 3.4, -18.6, -17.7, "bone")  # 上の牙
    jaw = m.bone("crawl_jaw", "head", (0, 3.2, -12), rotation=[12, 0, 0])
    box(jaw, -4, 4, 0.6, 3.2, -18, -11, "hide")
    for x in (-2.2, -0.2, 1.8):
        box(jaw, x, x + 0.9, 2.6, 4.0, -17.4, -16.6, "bone")  # 下の牙
    for name, x, z in (("leg_fl", 4, -7), ("leg_fr", -4, -7), ("leg_bl", 4, 7), ("leg_br", -4, 7)):
        leg = m.bone(name, "body", (x, 6, z))
        box(leg, x - 1.7, x + 1.7, 2.5, 6.5, z - 1.7, z + 1.7, "hide")  # 上腕/大腿
        box(leg, x - 1.9, x + 1.9, 5.6, 6.6, z - 2.0, z + 2.0, "moss")  # 苔の袖
        lo = m.bone(f"{name}_lo", name, (x, 2.5, z), rotation=[-12, 0, 0])
        box(lo, x - 1.2, x + 1.2, 0.6, 2.5, z - 1.2, z + 1.2, "hide")  # 脛
        box(lo, x - 2.0, x + 2.0, 0, 1.0, z - 2.6, z + 1.2, "bone")  # 足の骨
        box(lo, x - 0.4, x + 0.4, 0, 0.8, z - 3.4, z - 2.6, "darkbone")  # 爪
    tail = m.bone("tail", "body", (0, 9, 9))
    box(m, tail, -1.6, 1.6, 6.5, 10.5, 8.5, 11.5, "hide")
    box(m, tail, -0.4, 0.4, 10.2, 11.6, 9.5, 10.5, "bone")
    tail_b = m.bone("tail_b", "tail", (0, 8, 11.5))
    box(m, tail_b, -1.2, 1.2, 5.5, 9.5, 11.5, 17, "hide")
    box(m, tail_b, -0.3, 0.3, 9.5, 10.8, 12, 16, "bone")
    box(m, tail_b, -1.8, 1.8, 6.2, 9.4, 16.5, 19.5, "moss")  # 尾の苔
    return m


def waterway_maw():
    m = Model("waterway_maw", 192, 192)
    m.bone("root", None, (0, 0, 0))
    head = m.bone("head", "root", (0, 5, -6))
    box(m, head, -4, 4, 3.0, 9.5, -16, -6, "hide")  # 平たい頭蓋
    box(m, head, -4.4, 4.4, 8.6, 9.6, -16.4, -13.8, "darkbone")  # 眉の張り出し
    box(m, head, -0.5, 0.5, 9.5, 11.4, -15, -8, "darkbone")  # 背の鰭の根
    for z in (-14, -11.5, -9):
        box(m, head, -0.3, 0.3, 11.2, 12.4, z - 0.3, z + 0.3, "bone")
    for sx in (-1, 1):  # 側面の眼 (琥珀色)
        x0, x1 = sx * 4.0, sx * 4.7
        box(m, head, x0, x1 + sx * 0.6, 5.2, 7.6, -13.6, -9.8, "socket")
        box(m, head, x0 + sx * 0.1, x1 + sx * 0.7, 5.8, 7.0, -13.0, -10.4, "eye_amber")
    box(m, head, -4.2, 4.2, 2.6, 4.4, -19.5, -6, "hide")  # 上顎
    for i in range(6):  # 上の針状の歯
        tx = -3.4 + i * 1.4
        box(m, head, tx, tx + 0.6, 1.4, 2.6, -19.2, -18.6, "bone")
    for sx in (-1, 1):  # 長い牙
        box(m, head, sx * 2.6, sx * 1.8, 0.0, 2.6, -19.6, -19.0, "bone")
    jaw = m.bone("jaw", "head", (0, 1.4, -7))
    box(jaw, -3.8, 3.8, -0.6, 1.4, -19.5, -7, "hide")  # 下顎
    for i in range(6):  # 下の歯 (上向き)
        tx = -3.4 + i * 1.4 + 0.7
        box(jaw, tx, tx + 0.6, 1.4, 2.6, -19.0, -18.4, "bone")
    box(jaw, -3.6, 3.6, -0.8, -0.3, -19.5, -7, "darkbone")
    prev = "root"
    z = -6
    spec = [(7, 6, 6), (6.5, 5.5, 6), (5.5, 4.8, 6), (4.5, 4, 6), (3.5, 3, 6), (2.6, 2.4, 5)]
    for i, (w, hgt, length) in enumerate(spec):
        seg = m.bone(f"seg{i}", prev, (0, 5, z))
        dx = round(0.9 * math.sin(i * 0.8 + 0.4), 2)  # 体をゆるく湾曲させる
        box(m, seg, -w / 2 + dx, w / 2 + dx, 5 - hgt / 2, 5 + hgt / 2, z, z + length, "hide")
        box(m, seg, dx - 0.5, dx + 0.5, 5 + hgt / 2, 5 + hgt / 2 + 1.8, z + 1, z + length - 1, "bone")  # 背骨の棘
        box(m, seg, -w / 2 + 0.5 + dx, w / 2 - 0.5 + dx, 5 - hgt / 2 - 0.8, 5 - hgt / 2, z + 1, z + length - 1, "bone")  # 白い腹
        if i < 3:  # えら
            for sx in (-1, 1):
                box(m, seg, dx + sx * (w / 2 - 0.4), dx + sx * (w / 2 + 0.2), 3.8, 5.8, z + 1.4, z + 1.9, "darkbone")
        zc = z + length / 2
        fl = m.bone(f"fin_l{i}", f"seg{i}", (-w / 2 + dx, 5, zc), rotation=[0, 0, 35])
        box(fl, -w / 2 - 1.4 + dx, -w / 2 + dx, 4.1, 5.9, zc - 1.1, zc + 1.1, "darkbone")
        fr = m.bone(f"fin_r{i}", f"seg{i}", (w / 2 + dx, 5, zc), rotation=[0, 0, -35])
        box(fr, w / 2 + dx, w / 2 + 1.4 + dx, 4.1, 5.9, zc - 1.1, zc + 1.1, "darkbone")
        prev = f"seg{i}"
        z += length
    fin = m.bone("tailfin", prev, (0, 5, z))
    box(fin, -0.5, 0.5, 1.5, 8.5, z, z + 5, "darkbone")
    box(fin, -0.5, 0.5, 9.5, 12.5, z, z + 4, "darkbone")
    box(fin, -1.2, 1.2, 4.5, 5.5, z + 1, z + 4, "hide")
    return m


def whalebone_captain():
    m = Model("whalebone_captain", 256, 256)
    s = 1.0
    h = humanoid(m, s, width=12, arm=4, body_mat="darkbone", ribs=False, eye_mat="core")
    lh, bh, w = h["leg_h"], h["body_h"], h["w"]
    body, head, base, hs = h["body"], h["head"], h["base"], 9.0
    # 胸甲: 石の胸当てに鯨骨の竜骨が三本走る。中央に青い核
    box(m, body, -w / 2 - 0.2, w / 2 + 0.2, lh + bh * 0.42, base - 0.5, -3.8, -3.0, "stone")
    for x in (-4.2, 0.0, 4.2):
        box(m, body, x - 0.6, x + 0.6, lh + bh * 0.5, base - 1.2, -4.4, -3.8, "bone")
    box(m, body, -2, 2, lh + bh * 0.6, lh + bh * 0.6 + 4, -4.4, -3.8, "core")
    box(m, body, -5.5, 5.5, lh + 1.5, lh + 3.5, -4.2, -3.8, "stone", decal="rune")  # 刻文の帯
    # 腰の帯と腰甲 (前を覆う石の板)
    box(m, body, -w * 0.44, w * 0.44, lh - 1.2, lh + 0.6, -3.4, 3.4, "brass")
    box(m, body, -1.0, 1.0, lh - 1.0, lh + 0.4, -3.8, -3.3, "stone")  # 留め金
    box(m, body, -w * 0.36, w * 0.36, lh - 5.0, lh - 2.2, -3.6, -3.0, "stone")
    box(m, body, -w * 0.34, w * 0.34, lh - 7.0, lh - 5.2, -3.4, -2.8, "darkbone")
    # 背中の骨の盾 (背の棘)
    for i in range(3):
        y0 = lh + 2 + i * 3.4
        box(m, body, -0.8, 0.8, y0, y0 + 2.2, 3.0, 4.2, "bone")
    # 兜: 石の頭蓋覆いと、鯨骨の冠
    skull(m, head, base, hs, s, eye_mat="core")
    box(m, head, -hs / 2 - 0.8, hs / 2 + 0.8, base + hs - 0.4, base + hs + 1.6, -hs / 2 - 0.8, hs / 2 + 0.8, "stone")
    crown_y = base + hs + 1.6
    box(m, head, -hs / 2 - 0.2, hs / 2 + 0.2, crown_y - 0.5, crown_y + 0.4, -hs / 2 - 0.9, hs / 2 + 0.9, "darkbone")
    for i, x in enumerate((-3.6, -1.8, 0.0, 1.8, 3.6)):
        ht = 4.2 if x == 0 else 2.6 + (i % 2) * 0.8
        box(m, head, x - 0.55, x + 0.55, crown_y, crown_y + ht, -1.0, 1.0, "bone")
    # 肩当て (大きい) と、大マント
    pauldrons(m, h, s, big=1.7)
    tattered_cape(m, body, 0, lh + bh - 1.0, 3.8, w + 1, bh * 0.5, [9, 13, 7, 11, 8],
                  name="cape", mat="cloth", lean=10, depth=0.9)
    # 鯨骨の鉾 (右の手): 反った刃と石の穂先
    hx, hy = -(w / 2 + h["a"] / 2), h["hand_y"]
    pole = m.bone("weapon", "right_forearm", (hx, hy, 0))
    box(pole, hx - 0.7, hx + 0.7, hy - 10, hy + 15, -0.7, 0.7, "wood")
    box(pole, hx - 0.6, hx + 0.6, hy - 11.5, hy - 10, -0.6, 0.6, "stone")  # 石突き
    box(pole, hx - 0.9, hx + 0.9, hy + 13, hy + 14.5, -1.0, 1.0, "brass")  # 口金
    blade = m.bone("blade", "weapon", (hx, hy + 15, 0))
    box(blade, hx - 1.0, hx + 1.0, hy + 15, hy + 24, -10, -1, "bone")
    box(blade, hx - 1.3, hx - 0.8, hy + 15.5, hy + 23.5, -12.5, -10, "stone")  # 刃の縁
    box(blade, hx - 1.0, hx + 1.0, hy + 23, hy + 27, -13, -8, "bone")  # 反った先
    box(blade, hx - 1.0, hx + 1.0, hy + 26, hy + 28.5, -15, -12.5, "stone")  # 鉤
    box(blade, hx - 1.0, hx + 1.0, hy + 13.5, hy + 15.5, -9, -2, "darkbone")  # 下の反り
    return m


# ---------------------------------------------------------------- テクスチャ
def value_noise(h, w, scale, seed):
    r = np.random.default_rng(seed)
    g = r.random((h // scale + 2, w // scale + 2))
    img = Image.fromarray((g * 255).astype(np.uint8)).resize((w + 2 * scale, h + 2 * scale), Image.BICUBIC)
    return np.asarray(img, float)[scale:scale + h, scale:scale + w] / 255.0


def _glow_face(img, glow, X, Y, W, H, material, seed):
    """目と核: 光る中心と、暗いくぼみ/halo の勾配"""
    yy, xx = np.mgrid[0:H, 0:W]
    cx, cy = (W - 1) / 2, (H - 1) / 2
    gl = np.zeros((H, W, 4), float)
    if material == "core":  # 中心が白く、外へ向かって青く消える halo
        d = np.sqrt(((xx - cx) / max(1.0, W / 2)) ** 2 + ((yy - cy) / max(1.0, H / 2)) ** 2)
        halo = np.clip(1.15 - d, 0, 1)
        core_col = np.array(GLOW["core"], float)
        col = core_col * (1 - halo[..., None]) + np.array((236, 252, 255.0)) * halo[..., None]
        gl[..., :3] = col
        gl[..., 3] = (0.42 + 0.58 * halo) * 255
    else:  # 目: 暗い縁、光る楕円の中心
        rng = np.random.default_rng(seed)
        light, dark = (np.array(c, float) for c in PALETTE[material])
        col = dark + (light - dark) * rng.random((H, W))[..., None]
        rx, ry = max(1.0, W * 0.32), max(1.0, H * 0.36)
        inside = ((xx - cx) / rx) ** 2 + ((yy - cy) / ry) ** 2 < 1.0
        col[inside] = GLOW[material]
        gl[inside, :3] = GLOW[material]
        gl[inside, 3] = 255
    img[Y:Y + H, X:X + W, :3] = np.clip(col, 0, 255)
    img[Y:Y + H, X:X + W, 3] = 255
    glow[Y:Y + H, X:X + W] = np.clip(gl, 0, 255).astype(np.uint8)


def paint(img, glow, x, y, w, h, material, face, seed, decal=None):
    """1面を描く。face は top/bottom/side/front/back。decal は front 面にだけ適用する。"""
    W = max(1, int(round(w * RES)))
    H = max(1, int(round(h * RES)))
    X, Y = int(round(x * RES)), int(round(y * RES))
    if material in GLOW:
        _glow_face(img, glow, X, Y, W, H, material, seed)
        return
    light, dark = (np.array(c, float) for c in PALETTE[material])
    rng = np.random.default_rng(seed)
    yy, xx = np.mgrid[0:H, 0:W]
    fy = yy / max(1, H - 1)
    edge = np.minimum(np.minimum(xx, W - 1 - xx), np.minimum(yy, H - 1 - yy))
    vertical = face in ("side", "front", "back")

    # 基本の濃淡 (ムラ)
    n = value_noise(H, W, 4, seed) * 0.5 + value_noise(H, W, 1, seed + 1) * 0.3 + rng.random((H, W)) * 0.2
    col = dark + (light - dark) * n[..., None]

    # 素材ごとの模様
    if material in ("bone", "darkbone"):
        # 滑らかな骨: 低い周波数のムラ、細いひび、摩耗した小さな穴、関節の黄ばみ
        col = dark + (light - dark) * (0.72 * value_noise(H, W, 8, seed) + 0.28 * value_noise(H, W, 3, seed + 1))[..., None]
        ridge = 0.5 + 0.5 * np.sin(xx * 0.6 + value_noise(H, W, 8, seed + 4) * 4)
        col *= (0.95 + 0.06 * ridge)[..., None]
        crack = np.abs(value_noise(H, W, 6, seed + 2) - 0.5) < 0.006
        col[crack] *= 0.55
        col[rng.random((H, W)) < 0.02] *= 0.7  # 摩耗の小穴
        yel = np.clip(1 - edge / (2.0 * RES), 0, 1) * 0.22
        col = col * (1 - yel[..., None]) + YELLOW * yel[..., None]
    elif material == "stone":
        P = 4 * RES
        joint = ((yy % P) == 0) | ((xx % P) == 0)
        joint &= value_noise(H, W, 2, seed + 5) > 0.22  # 欠けた目地
        col[joint] *= 0.6
        rivet = ((yy % P) == 2) & ((xx % P) == 2)
        col[rivet] = light * 1.12
        lip = ((yy % P) == 1) & ~joint
        col[lip] *= 1.12
    elif material == "moss":
        clump = value_noise(H, W, 4, seed + 6)
        cm = clump > 0.42
        tips = cm & ~np.roll(cm, 1, axis=0)
        col[~cm] *= 0.78
        col[tips] = light * 1.1
        if vertical:
            drip = (fy > 0.4) & (value_noise(H, W, 3, seed + 7) > 0.6)
            col[drip] *= 0.72
            col[fy < 0.2] *= 1.1  # 上縁の苔の張り出し
    elif material in ("hide", "teal"):
        # 重なり合う丸い鱗 (千鳥配置)。鱗の縁は暗く、先端は明るい。上面は湿った光沢
        cell = 4 * RES
        row = yy // cell
        off = (row % 2) * (cell // 2)
        cx = (xx + off) // cell
        lx = ((xx + off) % cell) - (cell - 1) / 2
        ly = (yy % cell) - (cell - 1) / 2
        dist = np.sqrt((lx / (cell / 2)) ** 2 + (ly / (cell / 2)) ** 2)
        inside = dist < 0.95
        rs = np.random.default_rng(seed + 9).random((H // cell + 3, W // cell + 3))
        tone = 0.88 + 0.24 * rs[np.minimum(row, rs.shape[0] - 1), np.minimum(cx, rs.shape[1] - 1)]
        col *= np.where(inside, tone, 0.5)[..., None]
        col[inside & (ly < -0.25 * cell / 2)] *= 1.12
        col[inside & (dist > 0.7)] *= 0.92
        if face == "top":
            sheen = np.exp(-(((xx - W * 0.38) / max(1.0, W * 0.16)) ** 2))
            col *= (1 + 0.35 * sheen)[..., None]
        elif vertical:
            col[fy < 0.22] *= 1.12
        if face == "bottom":
            col *= 1.22
    elif material == "wood":
        grain = np.sin(xx * 0.8 + value_noise(H, W, 8, seed + 8) * 7)
        col *= (0.84 + 0.16 * grain)[..., None]
    elif material == "cloth":
        # 織り目と糸のムラ
        weave = ((xx + yy) % 3 == 0).astype(float)
        col *= (0.9 + 0.1 * weave)[..., None]
        col *= (0.88 + 0.24 * value_noise(H, W, 2, seed + 12))[..., None]
        if vertical:
            col *= (1 + 0.06 * np.sin(xx * 1.4))[..., None]
    elif material == "brass":
        sheen = np.clip(1 - fy * 1.2, 0, 1)
        col = dark + (light - dark) * (0.35 + 0.65 * sheen)[..., None]
        scratch = np.sin(xx * 0.9 + value_noise(H, W, 6, seed + 13) * 6)
        col *= (0.93 + 0.07 * scratch)[..., None]
    elif material == "socket":
        col *= (0.9 + 0.2 * value_noise(H, W, 2, seed + 14))[..., None]

    # 面ごとの陰影。左上からの光。縦面は上が明るく、下に向かって暗くなる (AO)
    if face == "top":
        col *= 1.05
    elif face == "bottom":
        col *= 0.8
    elif vertical:
        col *= (1.12 - 0.3 * fy)[..., None]
        col[fy > 0.85] *= 0.86
    else:
        col *= 0.9
    col *= 0.93 + 0.14 * np.random.default_rng(seed + 77).random()  # 部品ごとの色の揺れ

    # 1px 相当の面取り。上と左に明るい縁、外周は暗い縁
    bw = RES if min(W, H) >= 6 else 0
    if bw:
        col[(yy >= bw) & (yy < 2 * bw) & (edge >= bw)] *= 1.15
        col[(xx >= bw) & (xx < 2 * bw) & (edge >= bw)] *= 1.05
        col[edge < bw] *= 0.62

    gl = np.zeros((H, W, 4), float)
    fx = xx / max(1, W - 1)
    if decal and face == "front":
        if decal == "skull":
            col[fy < 0.2] *= 0.7  # 眉の陰
            col[(fy >= 0.2) & (fy < 0.28)] *= 1.06
            socket = (fy > 0.5) & (fy < 0.9) & (((fx > 0.06) & (fx < 0.42)) | ((fx > 0.58) & (fx < 0.94)))
            col[socket] *= 0.42  # 眼窩
            nost = (fy > 0.62) & (fy < 0.72) & ((np.abs(fx - 0.46) < 0.05) | (np.abs(fx - 0.54) < 0.05))
            col[nost] = dark * 0.5
            band = fy > 0.84
            tooth = ((xx // (2 * RES)) % 2) == 0
            col[band & tooth] = light * 1.08
            col[band & ~tooth] *= 0.3
        elif decal == "snout":
            nost = (fy > 0.2) & (fy < 0.34) & ((np.abs(fx - 0.3) < 0.07) | (np.abs(fx - 0.7) < 0.07))
            col[nost] = dark * 0.5
            band = fy > 0.7
            tooth = ((xx // (2 * RES)) % 2) == 0
            col[band & tooth] = light * 1.1
            col[band & ~tooth] *= 0.35
        elif decal == "rune":
            for ry in (0.3, 0.7):
                line = (np.abs(fy - ry) < 0.14) & (fx > 0.08) & (fx < 0.92) & ~((fx > 0.44) & (fx < 0.56))
                gl[line, :3] = GLOW["core"]
                gl[line, 3] = 255
            for rx in (0.2, 0.8):
                tick = (np.abs(fx - rx) < 0.06) & (fy > 0.1) & (fy < 0.9)
                gl[tick, :3] = GLOW["core"]
                gl[tick, 3] = 255
            lit = gl[..., 3] > 0
            col[lit] = GLOW["core"]

    img[Y:Y + H, X:X + W, :3] = np.clip(col, 0, 255)
    img[Y:Y + H, X:X + W, 3] = 255
    glow[Y:Y + H, X:X + W] = np.clip(gl, 0, 255).astype(np.uint8)


def build(model):
    cubes = [c for b in model.bones for c in b["cubes"]]
    # ボックスUVを棚詰めで並べる
    order = sorted(range(len(cubes)), key=lambda i: -(cubes[i]["size"][2] + cubes[i]["size"][1]))
    x = y = shelf = 0
    for i in order:
        w, hgt, d = cubes[i]["size"]
        cw = math.ceil(2 * (d + w)) + 1
        ch = math.ceil(d + hgt) + 1
        if x + cw > model.tex_w:
            x, y, shelf = 0, y + shelf, 0
        cubes[i]["uv"] = [x, y]
        x += cw
        shelf = max(shelf, ch)
    if y + shelf > model.tex_h:
        raise SystemExit(f"{model.name}: texture overflow {y + shelf} > {model.tex_h}")
    img = np.zeros((model.tex_h * RES, model.tex_w * RES, 4), np.uint8)
    glow = np.zeros_like(img)
    for n, c in enumerate(cubes):
        u, v = c["uv"]
        w, hgt, d = c["size"]
        mat = c["material"]
        seed = 1000 + n * 13
        paint(img, glow, u + d, v, w, d, mat, "top", seed)
        paint(img, glow, u + d + w, v, w, d, mat, "bottom", seed + 1)
        paint(img, glow, u, v + d, d, hgt, mat, "side", seed + 2)
        paint(img, glow, u + d, v + d, w, hgt, mat, "front", seed + 3, decal=c.get("decal"))
        paint(img, glow, u + d + w, v + d, d, hgt, mat, "side", seed + 4)
        paint(img, glow, u + 2 * d + w, v + d, w, hgt, mat, "back", seed + 5)
    bones = []
    for b in model.bones:
        out = {k: v for k, v in b.items() if k != "cubes"}
        out["cubes"] = [{"origin": [round(v, 3) for v in c["origin"]], "size": [round(v, 3) for v in c["size"]], "uv": c["uv"],
                         **({"inflate": c["inflate"]} if c["inflate"] else {})} for c in b["cubes"]]
        bones.append(out)
    geo = {"format_version": "1.12.0", "minecraft:geometry": [{
        "description": {"identifier": f"geometry.{model.name}", "texture_width": model.tex_w, "texture_height": model.tex_h,
                        "visible_bounds_width": 4, "visible_bounds_height": 5, "visible_bounds_offset": [0, 1.5, 0]},
        "bones": bones}]}
    (ROOT / "geo/entity").mkdir(parents=True, exist_ok=True)
    (ROOT / "textures/entity").mkdir(parents=True, exist_ok=True)
    (ROOT / f"geo/entity/{model.name}.geo.json").write_text(json.dumps(geo, indent=1), encoding="utf-8")
    Image.fromarray(img, "RGBA").save(ROOT / f"textures/entity/{model.name}.png", optimize=True)
    Image.fromarray(glow, "RGBA").save(ROOT / f"textures/entity/{model.name}_glowmask.png", optimize=True)
    return geo


# ---------------------------------------------------------------- アニメーション
# Molang の math.sin/cos は度数法。sin(速度, 振幅, 位相) は 速度 度/秒 の振動。
# ループ長 x 速度 は 360 の倍数にする (継ぎ目が出ないように)。
def sin(speed, amp, phase=0):
    return f"math.sin(query.anim_time * {speed} + {phase}) * {amp}"


def humanoid_anims(prefix, extra, cape=True):
    cape_idle = {"cape": {"rotation": [sin(120, 2.0, -50), "0", "0"]},
                 "cape_lo": {"rotation": [f"8 + {sin(120, 3.0, -130)}", "0", "0"]}} if cape else {}
    cape_walk = {"cape": {"rotation": [f"6 + {sin(360, 4, -70)}", "0", "0"]},
                 "cape_lo": {"rotation": [f"10 + {sin(360, 7, -150)}", "0", "0"]}} if cape else {}
    anims = {
        f"animation.{prefix}.idle": {"loop": True, "animation_length": 3.0, "bones": {
            "body": {"position": ["0", sin(120, 0.3), "0"], "rotation": [sin(120, 1.2), "0", "0"]},
            "head": {"rotation": [sin(120, 1.0, 90), sin(120, 3.0, 60), "0"]},
            "left_arm": {"rotation": [sin(120, 2.5, 40), "0", "-3"]},
            "right_arm": {"rotation": [sin(120, 2.5, -40), "0", "3"]},
            "left_forearm": {"rotation": [sin(120, 2.0, 60), "0", "0"]},
            "right_forearm": {"rotation": [sin(120, 2.0, 0), "0", "0"]},
            **cape_idle}},
        f"animation.{prefix}.walk": {"loop": True, "animation_length": 1.0, "bones": {
            "left_leg": {"rotation": [sin(360, 32), "0", "0"]},
            "right_leg": {"rotation": [sin(360, -32), "0", "0"]},
            "left_arm": {"rotation": [sin(360, -22), "0", "-3"]},
            "right_arm": {"rotation": [sin(360, 22), "0", "3"]},
            "left_forearm": {"rotation": [f"-8 + {sin(360, 8, 90)}", "0", "0"]},
            "right_forearm": {"rotation": [f"-8 + {sin(360, -8, 90)}", "0", "0"]},
            "body": {"rotation": ["4", sin(360, 3), "0"], "position": ["0", f"math.abs({sin(360, 0.8)})", "0"]},
            "head": {"rotation": ["0", sin(360, 3, 180), "0"]},
            **cape_walk}},
    }
    anims.update(extra)
    return anims


def keyframes(*frames):
    return {f"{t:.2f}": list(v) for t, v in frames}


def write_anims(name, anims):
    (ROOT / "animations/entity").mkdir(parents=True, exist_ok=True)
    (ROOT / f"animations/entity/{name}.animation.json").write_text(
        json.dumps({"format_version": "1.8.0", "animations": anims}, indent=1), encoding="utf-8")


def all_anims():
    write_anims("bone_sentinel", humanoid_anims("bone_sentinel", {
        "animation.bone_sentinel.attack": {"animation_length": 0.6, "bones": {
            "right_arm": {"rotation": keyframes((0, (0, 0, 0)), (0.22, (-130, 10, 0)), (0.38, (30, -10, 0)), (0.6, (0, 0, 0)))},
            "right_forearm": {"rotation": keyframes((0, (0, 0, 0)), (0.22, (-40, 0, 0)), (0.38, (20, 0, 0)), (0.6, (0, 0, 0)))},
            "body": {"rotation": keyframes((0, (0, 0, 0)), (0.22, (0, 15, 0)), (0.38, (8, -20, 0)), (0.6, (0, 0, 0)))},
            "cape_lo": {"rotation": keyframes((0, (10, 0, 0)), (0.3, (30, 0, 0)), (0.6, (10, 0, 0)))}}},
        "animation.bone_sentinel.block": {"animation_length": 0.5, "bones": {
            "left_arm": {"rotation": keyframes((0, (0, 0, 0)), (0.1, (-70, -30, 0)), (0.4, (-70, -30, 0)), (0.5, (0, 0, 0)))},
            "left_forearm": {"rotation": keyframes((0, (0, 0, 0)), (0.1, (-60, 0, 0)), (0.4, (-60, 0, 0)), (0.5, (0, 0, 0)))}}}}))
    write_anims("rib_archer", humanoid_anims("rib_archer", {
        "animation.rib_archer.shoot": {"animation_length": 0.8, "bones": {
            "left_arm": {"rotation": keyframes((0, (0, 0, 0)), (0.25, (-90, 10, 0)), (0.65, (-90, 10, 0)), (0.8, (0, 0, 0)))},
            "left_forearm": {"rotation": keyframes((0, (0, 0, 0)), (0.25, (-10, 0, 0)), (0.65, (-10, 0, 0)), (0.8, (0, 0, 0)))},
            "right_arm": {"rotation": keyframes((0, (0, 0, 0)), (0.25, (-90, -25, 0)), (0.55, (-85, -40, 0)), (0.62, (-90, -10, 0)), (0.8, (0, 0, 0)))},
            "right_forearm": {"rotation": keyframes((0, (0, 0, 0)), (0.25, (-40, -10, 0)), (0.55, (-50, -20, 0)), (0.62, (-20, 0, 0)), (0.8, (0, 0, 0)))},
            "cape_lo": {"rotation": keyframes((0, (12, 0, 0)), (0.3, (22, 0, 0)), (0.8, (12, 0, 0)))}}}}))
    write_anims("moss_crawler", {
        "animation.moss_crawler.idle": {"loop": True, "animation_length": 2.0, "bones": {
            "body": {"position": ["0", sin(180, 0.4), "0"], "rotation": [sin(180, 1.0), "0", "0"]},
            "head": {"rotation": [sin(180, 2.0, 90), sin(90, 6), "0"]},
            "tail": {"rotation": ["0", sin(180, 12), "0"]},
            "tail_b": {"rotation": ["0", sin(180, 16, -70), "0"]}}},
        "animation.moss_crawler.walk": {"loop": True, "animation_length": 0.5, "bones": {
            "leg_fl": {"rotation": [sin(720, 35), "0", "0"]}, "leg_br": {"rotation": [sin(720, 35), "0", "0"]},
            "leg_fr": {"rotation": [sin(720, -35), "0", "0"]}, "leg_bl": {"rotation": [sin(720, -35), "0", "0"]},
            "leg_fl_lo": {"rotation": [f"-12 + {sin(720, 14, 90)}", "0", "0"]},
            "leg_br_lo": {"rotation": [f"-12 + {sin(720, 14, 90)}", "0", "0"]},
            "leg_fr_lo": {"rotation": [f"-12 + {sin(720, -14, 90)}", "0", "0"]},
            "leg_bl_lo": {"rotation": [f"-12 + {sin(720, -14, 90)}", "0", "0"]},
            "body": {"position": ["0", f"math.abs({sin(720, 0.6)})", "0"], "rotation": [sin(720, 1.5), "0", "0"]},
            "tail": {"rotation": ["0", sin(720, 14, -40), "0"]},
            "tail_b": {"rotation": ["0", sin(720, 18, -120), "0"]}}},
        "animation.moss_crawler.pounce": {"animation_length": 0.6, "bones": {
            "body": {"rotation": keyframes((0, (0, 0, 0)), (0.15, (15, 0, 0)), (0.35, (-20, 0, 0)), (0.6, (0, 0, 0)))},
            "head": {"rotation": keyframes((0, (0, 0, 0)), (0.3, (-25, 0, 0)), (0.6, (0, 0, 0)))},
            "crawl_jaw": {"rotation": keyframes((0, (12, 0, 0)), (0.3, (30, 0, 0)), (0.6, (12, 0, 0)))},
            "leg_fl": {"rotation": keyframes((0, (0, 0, 0)), (0.3, (-60, 0, 0)), (0.6, (0, 0, 0)))},
            "leg_fr": {"rotation": keyframes((0, (0, 0, 0)), (0.3, (-60, 0, 0)), (0.6, (0, 0, 0)))},
            "tail": {"rotation": keyframes((0, (0, 0, 0)), (0.3, (-25, 0, 0)), (0.6, (0, 0, 0)))}}}})
    segs = {f"seg{i}": {"rotation": ["0", sin(270, 8 + i * 2.5, -i * 0.8), "0"]} for i in range(6)}
    segs["tailfin"] = {"rotation": ["0", sin(270, 20, -4.5), "0"]}
    segs["head"] = {"rotation": ["0", sin(270, 4, 0.6), "0"]}
    write_anims("waterway_maw", {
        "animation.waterway_maw.swim": {"loop": True, "animation_length": 1.333, "bones": segs},
        "animation.waterway_maw.bite": {"animation_length": 0.4, "bones": {
            "jaw": {"rotation": keyframes((0, (0, 0, 0)), (0.12, (35, 0, 0)), (0.22, (-5, 0, 0)), (0.4, (0, 0, 0)))},
            "head": {"rotation": keyframes((0, (0, 0, 0)), (0.12, (-12, 0, 0)), (0.22, (8, 0, 0)), (0.4, (0, 0, 0)))}}}})
    write_anims("whalebone_captain", humanoid_anims("whalebone_captain", {
        "animation.whalebone_captain.sweep": {"animation_length": 1.0, "bones": {
            "right_arm": {"rotation": keyframes((0, (0, 0, 0)), (0.45, (-80, 70, -20)), (0.62, (-80, -80, -20)), (1.0, (0, 0, 0)))},
            "right_forearm": {"rotation": keyframes((0, (0, 0, 0)), (0.45, (-20, 0, 0)), (0.62, (-30, 0, 0)), (1.0, (0, 0, 0)))},
            "body": {"rotation": keyframes((0, (0, 0, 0)), (0.45, (0, 35, 0)), (0.62, (0, -40, 0)), (1.0, (0, 0, 0)))},
            "cape_lo": {"rotation": keyframes((0, (10, 0, 0)), (0.5, (35, 0, 0)), (1.0, (10, 0, 0)))}}},
        "animation.whalebone_captain.leap": {"animation_length": 0.8, "bones": {
            "body": {"rotation": keyframes((0, (0, 0, 0)), (0.3, (25, 0, 0)), (0.5, (-15, 0, 0)), (0.8, (0, 0, 0)))},
            "left_leg": {"rotation": keyframes((0, (0, 0, 0)), (0.3, (-40, 0, 0)), (0.8, (0, 0, 0)))},
            "right_leg": {"rotation": keyframes((0, (0, 0, 0)), (0.3, (-40, 0, 0)), (0.8, (0, 0, 0)))},
            "right_arm": {"rotation": keyframes((0, (0, 0, 0)), (0.4, (-170, 0, 0)), (0.8, (-170, 0, 0)))},
            "left_arm": {"rotation": keyframes((0, (0, 0, 0)), (0.4, (-170, 0, 0)), (0.8, (-170, 0, 0)))},
            "cape_lo": {"rotation": keyframes((0, (10, 0, 0)), (0.4, (40, 0, 0)), (0.8, (10, 0, 0)))}}},
        "animation.whalebone_captain.slam": {"animation_length": 0.7, "bones": {
            "right_arm": {"rotation": keyframes((0, (-170, 0, 0)), (0.15, (20, 0, 0)), (0.7, (0, 0, 0)))},
            "right_forearm": {"rotation": keyframes((0, (-30, 0, 0)), (0.15, (10, 0, 0)), (0.7, (0, 0, 0)))},
            "left_arm": {"rotation": keyframes((0, (-170, 0, 0)), (0.15, (20, 0, 0)), (0.7, (0, 0, 0)))},
            "body": {"rotation": keyframes((0, (-15, 0, 0)), (0.15, (30, 0, 0)), (0.7, (0, 0, 0)))},
            "cape_lo": {"rotation": keyframes((0, (10, 0, 0)), (0.2, (45, 0, 0)), (0.7, (10, 0, 0)))}}},
        "animation.whalebone_captain.roar": {"animation_length": 1.4, "bones": {
            "head": {"rotation": keyframes((0, (0, 0, 0)), (0.3, (-35, 0, 0)), (1.1, (-35, 0, 0)), (1.4, (0, 0, 0)))},
            "body": {"rotation": keyframes((0, (0, 0, 0)), (0.3, (-12, 0, 0)), (1.1, (-12, 0, 0)), (1.4, (0, 0, 0)))},
            "left_arm": {"rotation": keyframes((0, (0, 0, 0)), (0.3, (-30, 0, -70)), (1.1, (-30, 0, -70)), (1.4, (0, 0, 0)))},
            "right_arm": {"rotation": keyframes((0, (0, 0, 0)), (0.3, (-30, 0, 70)), (1.1, (-30, 0, 70)), (1.4, (0, 0, 0)))},
            "cape_lo": {"rotation": keyframes((0, (10, 0, 0)), (0.3, (25, 0, 0)), (1.1, (25, 0, 0)), (1.4, (10, 0, 0)))}}}}))


# ---------------------------------------------------------------- 骨の棘 (矢の形の飛び道具)
def bone_spike():
    # バニラの矢と同じ配置 (32x32): 側面 0..16 x 0..5、後ろの面 0..5 x 5..10
    img = np.zeros((32, 32, 4), np.uint8)
    light, dark = (np.array(c, float) for c in PALETTE["bone"])
    for x in range(16):
        for y in range(5):
            thick = 2 if x < 3 else (1 if x > 12 else 2)
            if abs(y - 2) <= thick:
                t = 0.4 + 0.6 * (x / 15)
                img[y, x, :3] = dark + (light - dark) * t
                img[y, x, 3] = 255
    for x in range(5):
        for y in range(5, 10):
            if abs(x - 2) + abs(y - 7) <= 2:
                img[y, x, :3] = light
                img[y, x, 3] = 255
    Image.fromarray(img, "RGBA").save(ROOT / "textures/entity/bone_spike.png")


# ---------------------------------------------------------------- プレビュー (静止姿勢、Bedrock と同じ回転規約)
def _rot(deg):
    # Bedrock の回転は y 軸が上向きで、MC と比べて x と z の符号が反転する。Rz * Ry * Rx の順ではなく Rx * Ry * Rz
    ax, ay, az = math.radians(-deg[0]), math.radians(deg[1]), math.radians(-deg[2])
    Rx = np.array([[1, 0, 0], [0, math.cos(ax), -math.sin(ax)], [0, math.sin(ax), math.cos(ax)]])
    Ry = np.array([[math.cos(ay), 0, math.sin(ay)], [0, 1, 0], [-math.sin(ay), 0, math.cos(ay)]])
    Rz = np.array([[math.cos(az), -math.sin(az), 0], [math.sin(az), math.cos(az), 0], [0, 0, 1]])
    return Rx @ Ry @ Rz


def _bone_fns(model):
    by = {b["name"]: b for b in model.bones}
    cache = {}

    def fn(name):
        if name not in cache:
            b = by[name]
            R = _rot(b.get("rotation", [0, 0, 0]))
            p = np.array(b["pivot"], float)
            par = fn(b["parent"]) if b.get("parent") else None

            def f(pt, R=R, p=p, par=par):
                q = R @ (pt - p) + p
                return par(q) if par else q
            cache[name] = f
        return cache[name]
    return fn


def preview(models, out_dir):
    out_dir.mkdir(parents=True, exist_ok=True)
    S, W, H = 7.0, 520, 440
    for model in models:
        fn = _bone_fns(model)
        quads = []
        for b in model.bones:
            f = fn(b["name"])
            for c in b["cubes"]:
                inf = c.get("inflate", 0)
                o = np.array(c["origin"], float) - inf
                sz = np.array(c["size"], float) + 2 * inf
                pts = {}
                for i in (0, 1):
                    for j in (0, 1):
                        for k in (0, 1):
                            pts[(i, j, k)] = f(o + sz * np.array([i, j, k]))
                center = np.mean(list(pts.values()), axis=0)
                for a in range(3):
                    for side in (0, 1):
                        others = [q for q in range(3) if q != a]
                        quad = []
                        for u, v in ((0, 0), (1, 0), (1, 1), (0, 1)):
                            idx = [0, 0, 0]
                            idx[a] = side
                            idx[others[0]] = u
                            idx[others[1]] = v
                            quad.append(pts[tuple(idx)])
                        n = np.cross(quad[1] - quad[0], quad[3] - quad[0])
                        nl = np.linalg.norm(n)
                        if nl < 1e-9:
                            continue
                        n = n / nl
                        if np.dot(n, np.mean(quad, axis=0) - center) < 0:
                            n = -n
                        quads.append((quad, n, c["material"]))
        img = Image.new("RGB", (W * 2, H), (196, 210, 228))
        L = np.array([-0.5, 0.75, -0.6])
        L = L / np.linalg.norm(L)
        for vi, yaw in enumerate((0.0, 35.0)):
            ca, sa = math.cos(math.radians(yaw)), math.sin(math.radians(yaw))
            draw = ImageDraw.Draw(img)
            ox = vi * W
            items = []
            for quad, n, mat in quads:
                def view(p):
                    x, y, z = p
                    return np.array([x * ca + z * sa, y, -x * sa + z * ca])
                vq = [view(p) for p in quad]
                vn = view(n) - view(np.zeros(3))
                if vn[2] >= 0:  # 観察者 (-Z) から見えない面
                    continue
                base = np.array(GLOW[mat], float) if mat in GLOW else np.array(PALETTE[mat][0], float)
                inten = 0.4 + 0.6 * max(0.0, float(np.dot(vn, L)))
                col = tuple(int(min(255, c * inten)) for c in base)
                depth = float(np.mean([q[2] for q in vq]))
                pts2 = [(ox + W / 2 + q[0] * S, H * 0.88 - q[1] * S) for q in vq]
                items.append((depth, pts2, col))
            for depth, pts2, col in sorted(items, key=lambda t: -t[0]):
                draw.polygon(pts2, fill=col, outline=(40, 40, 50))
        img.save(out_dir / f"{model.name}.png")


def main():
    models = [bone_sentinel(), rib_archer(), moss_crawler(), waterway_maw(), whalebone_captain()]
    for model in models:
        build(model)
    all_anims()
    bone_spike()
    if len(sys.argv) > 2 and sys.argv[1] == "--preview":
        preview(models, Path(sys.argv[2]))
    print("ok", [m.name for m in models])


if __name__ == "__main__":
    main()
