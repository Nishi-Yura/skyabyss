"""岩鯨の背に棲む者たちのモデル・テクスチャ・アニメーションを作る (GeckoLib / Bedrock形式)。

使い方: python tools/gen_mobs.py [--preview 出力先ディレクトリ]
出力先: src/main/resources/assets/skyabyss/
  geo/entity/<名前>.geo.json, textures/entity/<名前>.png (+ _glowmask.png), animations/entity/<名前>.animation.json
  textures/entity/bone_spike.png (骨棘の射手が放つ骨の棘)

  bone_sentinel     骨の番兵     : 鯨骨の鎧をまとった剣士。正面からの攻撃を骨の盾で受ける
  rib_archer        骨棘の射手   : 肋骨の弓で骨の棘を放つ。細身で、背に棘の矢筒
  moss_crawler      苔這い       : 苔に覆われた四つ足の獣。低く素早く、飛びかかる
  waterway_maw      水路の顎     : 闇の水路を泳ぐ、骨の顎を持つ長い魚
  whalebone_captain 鯨骨の番兵長 : 巨大な鉾を持つ番兵の長。骨の冠と、胸に光る核

単位は px (16px = 1ブロック)。足元が y=0、正面は -Z。テクスチャは宣言サイズの2倍で描く (32px/ブロック相当)。
テクスチャは全て決定的 (シード固定の乱数のみ)。光る部分は _glowmask.png に書き、それ以外は透明にする。
"""
import json
import math
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parent.parent / "src/main/resources/assets/skyabyss"
RES = 2

PALETTE = {
    "bone": ((226, 216, 192), (168, 154, 128)),
    "darkbone": ((176, 162, 136), (118, 104, 84)),
    "stone": ((98, 104, 118), (56, 60, 72)),
    "moss": ((108, 146, 70), (48, 74, 36)),
    "hide": ((70, 86, 92), (40, 52, 58)),
    "flesh": ((120, 70, 72), (76, 40, 44)),
    "eye": ((20, 18, 24), (14, 12, 16)),
    "core": ((60, 140, 170), (30, 80, 100)),
    "wood": ((96, 74, 56), (62, 46, 34)),
}
GLOW = {"eye": (120, 235, 255), "core": (150, 240, 255)}
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
            b["parent"] = parent
        if rotation:
            b["rotation"] = list(rotation)
        self.bones.append(b)
        return b

    def cube(self, bone, origin, size, material, inflate=0, decal=None):
        c = {"origin": list(origin), "size": list(size), "material": material, "inflate": inflate}
        if decal:
            c["decal"] = decal
        bone["cubes"].append(c)


def _leg(m, b, x0, s, lw, leg_h):
    """太い腿から細い脛へ。足首に向かって細くなる"""
    m.cube(b, (x0, leg_h * 0.45, -lw / 2), (lw, leg_h * 0.55, lw), "bone")
    m.cube(b, (x0 + 0.5 * s, 0, -lw / 2 + 0.5), (lw - 1.2 * s, leg_h * 0.45, lw - 1.0), "bone")
    m.cube(b, (x0 - 0.2 * s, leg_h * 0.45, -lw / 2 - 0.4), (lw + 0.4, 3 * s, lw + 0.8), "stone")
    m.cube(b, (x0, 0, -lw / 2 - 1.2), (lw, 2.5 * s, 1), "darkbone")


def humanoid(m, scale, material_body="darkbone", width=10, arm=3, with_head=True):
    """二本足の骨の体。scale は全体の大きさの倍率"""
    s = scale
    leg_h = 14 * s
    body_h = 13 * s
    m.bone("root", None, (0, 0, 0))
    lw = 4 * s
    ll = m.bone("left_leg", "root", (2.5 * s, leg_h, 0))
    _leg(m, ll, 0.5 * s, s, lw, leg_h)
    rl = m.bone("right_leg", "root", (-2.5 * s, leg_h, 0))
    _leg(m, rl, -0.5 * s - lw, s, lw, leg_h)
    body = m.bone("body", "root", (0, leg_h, 0))
    w = width * s
    base = leg_h + body_h
    # 厚い胸と、くびれた腰
    m.cube(body, (-w / 2, leg_h + body_h * 0.42, -3 * s), (w, body_h * 0.58, 6 * s), material_body)
    m.cube(body, (-w * 0.4, leg_h, -2.6 * s), (w * 0.8, body_h * 0.42, 5.2 * s), material_body)
    for i in range(3):  # 肋骨の板
        m.cube(body, (-w / 2 - 0.5, leg_h + body_h * 0.5 + i * 2.8 * s, -3 * s - 0.6), (w + 1, 1.4 * s, 1), "bone")
    m.cube(body, (-w / 2 + 1, leg_h - 0.5, -3 * s - 1.1), (w - 2, 2.5 * s, 1), "stone")
    m.cube(body, (-w / 2 + 2, leg_h - 2.8, -3 * s - 1.5), (w - 4, 2 * s, 1), "darkbone")
    for i in range(4):  # 背の棘
        m.cube(body, (-0.6, leg_h + 2 + i * 2.8 * s, 3 * s), (1.2, 1.8 * s, 1.2), "bone")
    head = None
    if with_head:
        head = m.bone("head", "body", (0, base, 0))
        hs = 8 * s
        m.cube(head, (-hs / 2, base, -hs / 2), (hs, hs, hs), "bone", decal="skull")
        m.cube(head, (-hs / 2 - 0.3, base + hs - 2.2 * s, -hs / 2 - 1.3), (hs + 0.6, 1.3 * s, 1.3), "bone")  # 眉の突起
        m.cube(head, (-hs * 0.32, base + 0.6 * s, -hs / 2 - 2.4), (hs * 0.64, 2.8 * s, 2.4), "bone", decal="snout")  # 突き出た顎
        m.cube(head, (-hs / 2 - 0.6, base + 1.5 * s, -hs / 2 - 0.8), (1.0, 1.4 * s, 1.6), "bone")  # 頬骨
        m.cube(head, (hs / 2 - 0.4, base + 1.5 * s, -hs / 2 - 0.8), (1.0, 1.4 * s, 1.6), "bone")
        m.cube(head, (-hs / 2 + 1, base + 1.5 * s, -hs / 2 - 0.5), (2 * s, 1.5 * s, 1), "eye")
        m.cube(head, (hs / 2 - 1 - 2 * s, base + 1.5 * s, -hs / 2 - 0.5), (2 * s, 1.5 * s, 1), "eye")
    a = arm * s
    shoulder = base - 1 * s
    la = m.bone("left_arm", "body", (w / 2 + a / 2, shoulder, 0))
    m.cube(la, (w / 2, shoulder - 12 * s, -a / 2), (a, 12 * s, a), "bone")
    m.cube(la, (w / 2 - 0.5, shoulder - 3 * s, -a / 2 - 0.5), (a + 1.5, 3.5 * s, a + 1), "stone")
    m.cube(la, (w / 2 - 0.2, shoulder - 13 * s, -a / 2 - 0.2), (a + 0.4, 1.4 * s, a + 0.4), "darkbone")  # 手甲
    ra = m.bone("right_arm", "body", (-w / 2 - a / 2, shoulder, 0))
    m.cube(ra, (-w / 2 - a, shoulder - 12 * s, -a / 2), (a, 12 * s, a), "bone")
    m.cube(ra, (-w / 2 - a - 1, shoulder - 3 * s, -a / 2 - 0.5), (a + 1.5, 3.5 * s, a + 1), "stone")
    m.cube(ra, (-w / 2 - a - 0.2, shoulder - 13 * s, -a / 2 - 0.2), (a + 0.4, 1.4 * s, a + 0.4), "darkbone")
    return {"body": body, "head": head, "left_arm": la, "right_arm": ra, "w": w, "shoulder": shoulder,
            "leg_h": leg_h, "body_h": body_h, "a": a, "base": base}


def pauldrons(h, s, big=1.0):
    """肩当て (両肩に二段)"""
    w, a, sh = h["w"], h["a"], h["shoulder"]
    la, ra = h["left_arm"], h["right_arm"]
    t = 3.2 * big * s
    return [
        (la, (w / 2 - 0.8, sh - 1.2 * s, -a / 2 - 1.0), (a + 1.6, t * 0.6, a + 2.0), "stone"),
        (la, (w / 2 - 0.3, sh - 2.8 * s, -a / 2 - 1.4), (a + 0.6, t * 0.5, a + 2.8), "darkbone"),
        (ra, (-w / 2 - a - 0.8, sh - 1.2 * s, -a / 2 - 1.0), (a + 1.6, t * 0.6, a + 2.0), "stone"),
        (ra, (-w / 2 - a - 0.3, sh - 2.8 * s, -a / 2 - 1.4), (a + 0.6, t * 0.5, a + 2.8), "darkbone"),
    ]


def bone_sentinel():
    m = Model("bone_sentinel", 192, 192)
    h = humanoid(m, 1.05)
    s = 1.05
    T = h["base"] + 8 * s  # 頭頂
    # 曲がった角: 三段のひねった骨 (左右対称)
    for sx, sign, side in ((-1, 1, "l"), (1, -1, "r")):
        xb = sx * 2.2 * s
        hb = m.bone(f"horn_{side}", "head", (xb, T - 0.5, -1), rotation=[0, 0, 25 * sign])
        m.cube(hb, (xb - 1, T - 0.5, -2), (2, 3, 2), "darkbone")
        h2 = m.bone(f"horn_{side}2", f"horn_{side}", (xb, T + 2.5, -1), rotation=[0, 0, 30 * sign])
        m.cube(h2, (xb - 0.8, T + 2.5, -1.8), (1.6, 2.6, 1.6), "bone")
        h3 = m.bone(f"horn_{side}3", f"horn_{side}2", (xb, T + 5.1, -1), rotation=[0, 0, 25 * sign])
        m.cube(h3, (xb - 0.6, T + 5.1, -1.6), (1.2, 2.2, 1.2), "darkbone")
    for (la, o, sz, mat) in pauldrons(h, s):
        m.cube(la, o, sz, mat)
    blade = m.bone("weapon", "right_arm", (-h["w"] / 2 - 1.5 * s, h["shoulder"] - 11 * s, 0))
    m.cube(blade, (-h["w"] / 2 - 2.5 * s, h["shoulder"] - 12.5 * s, -15), (2, 3, 14), "bone")
    m.cube(blade, (-h["w"] / 2 - 2.5 * s, h["shoulder"] - 12.5 * s, -16), (2, 1, 2), "stone")  # 剣先
    m.cube(blade, (-h["w"] / 2 - 3.5 * s, h["shoulder"] - 13 * s, -2), (4, 4, 2), "stone")  # 鍔
    m.cube(blade, (-h["w"] / 2 - 2.8 * s, h["shoulder"] - 13.4 * s, -3.6), (2.6, 3, 1.8), "darkbone")  # 柄
    shield = m.bone("shield", "left_arm", (h["w"] / 2 + 3.5 * s, h["shoulder"] - 7 * s, 0))
    m.cube(shield, (h["w"] / 2 + 3.2 * s, h["shoulder"] - 12 * s, -5), (1.5, 11, 10), "darkbone")
    for i in range(3):
        m.cube(shield, (h["w"] / 2 + 4.5 * s, h["shoulder"] - 11 * s + i * 3.5, -5.5), (1, 1.5, 11), "bone")
    m.cube(shield, (h["w"] / 2 + 4.6 * s, h["shoulder"] - 7.5 * s, -1.5), (1.2, 2.2, 2.2), "stone")
    return m


def rib_archer():
    m = Model("rib_archer", 192, 192)
    h = humanoid(m, 0.98, width=8, arm=2.6)
    s = 0.98
    m.cube(h["head"], (-4.5 * s, h["base"] + 5 * s, -4.5 * s), (9 * s, 4 * s, 9 * s), "moss")  # 頭巾
    m.cube(h["head"], (-4.6 * s, h["base"] - 2 * s, 3.4 * s), (9.2 * s, 6 * s, 1.6 * s), "moss")  # 頭巾の垂れ
    m.cube(h["head"], (-3.4 * s, h["base"] + 1.2 * s, -4.6 * s), (6.8 * s, 1.4 * s, 0.8), "hide")  # 頭巾の縁
    body = h["body"]
    # 胸の前の肋骨の弧 (間に隙間を残した細い骨の棒)
    for i in range(4):
        y = h["leg_h"] + 3.2 * s + i * 2.6 * s
        m.cube(body, (-h["w"] / 2 + 1.0, y, -3 * s - 1.5), (h["w"] - 2.0, 0.9 * s, 1.0), "bone")
        m.cube(body, (-h["w"] / 2 + 2.2, y - 0.6, -3 * s - 1.9), (h["w"] - 4.4, 0.7, 0.7), "darkbone")
    quiver = m.bone("quiver", "body", (0, h["leg_h"] + 8 * s, 3 * s))
    m.cube(quiver, (-1.5, h["leg_h"] + 3 * s, 3 * s), (3, 10, 3), "wood")
    for i in range(3):
        m.cube(quiver, (-1 + i, h["leg_h"] + 13 * s, 3.5 * s), (1, 4, 1), "bone")
    m.cube(quiver, (-1.8, h["leg_h"] + 2.5 * s, 3 * s), (3.6, 1, 3.6), "hide")
    bow = m.bone("bow", "left_arm", (h["w"] / 2 + 1.3 * s, h["shoulder"] - 11 * s, 0))
    m.cube(bow, (h["w"] / 2 + 0.5 * s, h["shoulder"] - 20 * s, -4), (1.5, 18, 1.5), "bone")
    m.cube(bow, (h["w"] / 2 + 0.5 * s, h["shoulder"] - 21 * s, -2.5), (1.5, 2, 2), "darkbone")
    m.cube(bow, (h["w"] / 2 + 0.5 * s, h["shoulder"] - 3 * s, -2.5), (1.5, 2, 2), "darkbone")
    m.cube(bow, (h["w"] / 2 + 0.6 * s, h["shoulder"] - 19 * s, -1.6), (0.4, 16, 0.4), "hide")
    for (la, o, sz, mat) in pauldrons(h, s, big=0.7):
        m.cube(la, o, sz, mat)
    return m


def moss_crawler():
    m = Model("moss_crawler", 192, 192)
    m.bone("root", None, (0, 0, 0))
    body = m.bone("body", "root", (0, 7, 0))
    m.cube(body, (-4.5, 5, -9), (9, 6, 18), "hide")
    m.cube(body, (-3.5, 11, -4), (7, 3, 8), "hide")  # 背の猫背の盛り上がり
    m.cube(body, (-4, 13.8, -5), (8, 1.4, 9), "moss")
    m.cube(body, (-4, 3.6, -8), (8, 1.4, 16), "darkbone")  # 腹の骨板
    for i in range(4):
        m.cube(body, (-3 + (i % 2) * 3, 14.8, -6 + i * 3.5), (2, 1.6 + i % 2, 2), "moss")
    for x, y, z in ((-5.5, 3, -6), (4.7, 2.5, -2), (-5.5, 3.5, 4), (4.7, 4, 6)):  # 蔦
        m.cube(body, (x, y, z), (0.8, 4, 0.8), "moss")
    head = m.bone("head", "body", (0, 9, -9))
    # 平たく幅の広い頭
    m.cube(head, (-5, 5, -16), (10, 5, 7), "hide")
    m.cube(head, (-4.5, 3.5, -17.5), (9, 2.5, 6), "bone", decal="snout")  # 上顎
    m.cube(head, (-4.5, 9.8, -16.6), (3, 1, 1.2), "moss")  # 眉の苔
    m.cube(head, (1.5, 9.8, -16.6), (3, 1, 1.2), "moss")
    m.cube(head, (-4, 8.4, -16.6), (2, 1.6, 1), "eye")
    m.cube(head, (2, 8.4, -16.6), (2, 1.6, 1), "eye")
    m.cube(head, (-3, 2.2, -17.8), (0.8, 1.4, 0.8), "bone")  # 上向きの牙 (下顎側)
    m.cube(head, (1.8, 2.2, -17.8), (0.8, 1.4, 0.8), "bone")
    jaw = m.bone("crawl_jaw", "head", (0, 3, -11), rotation=[14, 0, 0])
    m.cube(jaw, (-4, 0.6, -17.5), (8, 2.2, 6.5), "hide")
    m.cube(jaw, (-3.2, 2.8, -17.8), (1, 1.6, 1), "bone")  # 下顎の牙
    m.cube(jaw, (1.8, 2.8, -17.8), (1, 1.6, 1), "bone")
    m.cube(jaw, (-4, 0.6, -17.6), (8, 0.7, 6.4), "darkbone")
    for name, x, z in (("leg_fl", 4, -7), ("leg_fr", -4, -7), ("leg_bl", 4, 7), ("leg_br", -4, 7)):
        leg = m.bone(name, "body", (x, 6, z))
        m.cube(leg, (x - 1.7, 2.5, z - 1.7), (3.4, 4, 3.4), "hide")  # 上腕/大腿
        m.cube(leg, (x - 1.5, 4, z - 1.7), (3, 2, 0.8), "moss")
        lo = m.bone(f"{name}_lo", name, (x, 2.5, z), rotation=[-18, 0, 0])
        m.cube(lo, (x - 1.2, 0.6, z - 1.2), (2.4, 2.5, 2.4), "hide")  # 脛 (関節で折れる)
        m.cube(lo, (x - 2, 0, z - 2.6), (4, 1.2, 4), "bone")
        m.cube(lo, (x - 1.8, 0, z - 2.7), (3.6, 0.6, 1.2), "darkbone")
    tail = m.bone("tail", "body", (0, 9, 9))
    m.cube(tail, (-1.5, 8, 9), (3, 3, 8), "hide")
    m.cube(tail, (-1, 8.5, 17), (2, 2, 5), "moss")
    m.cube(tail, (-1.6, 10.5, 12), (3.2, 2.2, 1.6), "moss")
    return m


def waterway_maw():
    m = Model("waterway_maw", 192, 192)
    m.bone("root", None, (0, 0, 0))
    head = m.bone("head", "root", (0, 5, -6))
    m.cube(head, (-4, 4, -16), (8, 5, 10), "hide")
    m.cube(head, (-4.5, 3.5, -17), (9, 1, 9), "bone")
    m.cube(head, (-4.5, 4, -17.5), (9, 4, 0.8), "bone", decal="snout")
    # 側面の眼窩 (頭の横に大きく開く)
    m.cube(head, (-4.6, 6.2, -12), (0.8, 2.2, 2.2), "eye")
    m.cube(head, (3.8, 6.2, -12), (0.8, 2.2, 2.2), "eye")
    for i in range(6):  # 上顎の針状の歯
        m.cube(head, (-3.4 + i * 1.3, 1.9, -16.4), (0.6, 1.6, 0.6), "bone")
    m.cube(head, (-0.5, 9.5, -8), (1, 2, 4), "darkbone")
    jaw = m.bone("jaw", "head", (0, 4, -7))
    m.cube(jaw, (-3.5, 1, -19), (7, 3, 13), "hide")  # 長い下顎 (受け口)
    for i in range(6):  # 下顎の針状の歯 (上向き)
        m.cube(jaw, (-3.2 + i * 1.3, 4, -18.5), (0.6, 1.6, 0.6), "bone")
    m.cube(jaw, (-3.5, 0.2, -19.5), (7, 0.8, 13), "darkbone")
    prev = "root"
    z = -6
    for i, (w, hgt, length) in enumerate([(7, 6, 6), (6, 5, 6), (5, 4, 6), (4, 3, 6), (3, 2.5, 5)]):
        seg = m.bone(f"seg{i}", prev, (0, 5, z))
        dx = round(0.9 * math.sin(i * 0.8 + 0.4), 2)  # 体をゆるく湾曲させる
        m.cube(seg, (-w / 2 + dx, 5 - hgt / 2, z), (w, hgt, length), "hide")
        m.cube(seg, (-0.5 + dx, 5 + hgt / 2, z + 1), (1, 1.5, length - 2), "bone")
        # 斜めに張り出すひれ (左右、子の骨で角度を付ける)
        zc = z + length / 2
        fl = m.bone(f"fin_l{i}", f"seg{i}", (-w / 2 + dx, 5, zc), rotation=[0, 0, 35])
        m.cube(fl, (-w / 2 - 1.4 + dx, 4.1, zc - 1.1), (1.4, 1.8, 2.2), "darkbone")
        fr = m.bone(f"fin_r{i}", f"seg{i}", (w / 2 + dx, 5, zc), rotation=[0, 0, -35])
        m.cube(fr, (w / 2 + dx, 4.1, zc - 1.1), (1.4, 1.8, 2.2), "darkbone")
        m.cube(seg, (-w / 2 + 0.5 + dx, 5 - hgt / 2 - 0.8, z + 1), (w - 1, 0.8, length - 2), "bone")  # 白い腹
        prev = f"seg{i}"
        z += length
    fin = m.bone("tailfin", prev, (0, 5, z))
    m.cube(fin, (-0.5, 1.5, z), (1, 7, 5), "darkbone")
    m.cube(fin, (-0.5, 9.5, z), (1, 3, 5), "darkbone")
    m.cube(fin, (-1.2, 5, z + 1), (2.2, 2, 3), "hide")
    return m


def whalebone_captain():
    m = Model("whalebone_captain", 256, 256)
    h = humanoid(m, 1.0, width=12, arm=4)
    lh, bh = h["leg_h"], h["body_h"]
    body = h["body"]
    m.cube(body, (-2, lh + 6, -3.8), (4, 4, 1), "core")
    m.cube(body, (-8, lh + 10, -4), (16, 4, 8), "stone")
    m.cube(body, (-5.5, lh + 1.5, -4.2), (11, 2, 1.2), "stone", decal="rune")
    m.cube(body, (-8.5, lh + 4.8, -4.1), (17, 0.8, 1), "darkbone")
    for (la, o, sz, mat) in pauldrons(h, 1.0, big=1.7):
        m.cube(la, o, sz, mat)
    # 三段目の厚い肩当て
    m.cube(h["left_arm"], (h["w"] / 2 - 1.2, h["shoulder"] + 0.6, -h["a"] / 2 - 1.6), (h["a"] + 2.4, 1.6, h["a"] + 3.2), "bone")
    m.cube(h["right_arm"], (-h["w"] / 2 - h["a"] - 1.2, h["shoulder"] + 0.6, -h["a"] / 2 - 1.6), (h["a"] + 2.4, 1.6, h["a"] + 3.2), "bone")
    m.cube(body, (-h["w"] / 2 - 1.5, lh + bh * 0.42, -3.5), (1.5, bh * 0.5, 7), "stone")  # 広い胸の側板
    m.cube(body, (h["w"] / 2, lh + bh * 0.42, -3.5), (1.5, bh * 0.5, 7), "stone")
    m.cube(body, (-7, lh - 1, -4.6), (14, 2.6, 1), "stone")  # 腰甲
    m.cube(body, (-5.5, lh - 3.2, -5.0), (11, 2.4, 1), "darkbone")
    cape = m.bone("cape", "body", (0, lh + bh, 3))
    m.cube(cape, (-6.5, lh + 4, 3), (13, bh - 4, 1), "darkbone")
    # 角度の付いた垂れ布の層 (子の骨で傾ける)
    ca = m.bone("cape_a", "cape", (-6, lh + bh, 3.5), rotation=[-10, 0, 0])
    m.cube(ca, (-6.5, lh + 1, 3.5), (13, bh - 4, 0.8), "hide")
    cb = m.bone("cape_b", "cape_a", (-6, lh + 3, 4.3), rotation=[8, 0, 0])
    m.cube(cb, (-5, lh - 2.2, 4.3), (3, 2.6, 0.8), "hide")
    m.cube(cb, (-1, lh - 1.6, 4.3), (2.5, 2.1, 0.8), "hide")
    m.cube(cb, (3, lh - 2.6, 4.3), (2.5, 2.6, 0.8), "hide")
    for i in range(4):
        m.cube(body, (-0.6, lh + 2 + i * 2.8, 3), (1.2, 1.8, 1.2), "bone")
    crown_y = lh + bh + 8
    for i, x in enumerate((-4, -2, 0, 2, 4)):
        m.cube(h["head"], (x - 0.75, crown_y, -4.5 if i % 2 == 0 else -3.5), (1.5, 3 + (2 if x == 0 else i % 2), 1.5), "bone")
    m.cube(h["head"], (-4.5, lh + bh - 0.5, -4.5), (9, 2.5, 9), "stone")
    m.cube(h["head"], (-4.5, lh + bh + 1.5, -4.6), (9, 1, 0.8), "darkbone")
    # 巨大な鉾: 長い柄と、大きく反った鉤状の刃
    pole = m.bone("weapon", "right_arm", (-8, h["shoulder"] - 11, 0))
    m.cube(pole, (-9, h["shoulder"] - 24, -1), (2, 34, 2), "wood")
    m.cube(pole, (-9.5, h["shoulder"] + 16, -1), (3, 4, 2), "darkbone")
    blade = m.bone("blade", "weapon", (-9.5, h["shoulder"] + 8, 0), rotation=[0, 0, -18])
    m.cube(blade, (-9.5, h["shoulder"] + 2, -11), (3, 14, 12), "bone")
    m.cube(blade, (-9.5, h["shoulder"] + 3, -13), (3, 6, 3), "stone")
    tip = m.bone("blade_tip", "blade", (-9.5, h["shoulder"] + 16, -11), rotation=[0, 0, -40])
    m.cube(tip, (-9.5, h["shoulder"] + 16, -14), (3, 4, 4), "bone")
    m.cube(tip, (-9.5, h["shoulder"] + 18, -14.6), (3, 2, 1.5), "stone")
    m.cube(pole, (-9.5, h["shoulder"] + 18, -7), (3, 2, 3), "stone")
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
    if material == "eye":
        rng = np.random.default_rng(seed)
        light, dark = (np.array(c, float) for c in PALETTE["eye"])
        col = dark + (light - dark) * rng.random((H, W))[..., None]
        rx, ry = max(1.0, W * 0.32), max(1.0, H * 0.36)
        inside = ((xx - cx) / rx) ** 2 + ((yy - cy) / ry) ** 2 < 1.0
        col[inside] = GLOW["eye"]
        gl[inside, :3] = GLOW["eye"]
        gl[inside, 3] = 255
    else:  # core: 中心が白く、外へ向かって青く消える halo
        d = np.sqrt(((xx - cx) / max(1.0, W / 2)) ** 2 + ((yy - cy) / max(1.0, H / 2)) ** 2)
        halo = np.clip(1.15 - d, 0, 1)
        core_col = np.array(GLOW["core"], float)
        col = core_col * (1 - halo[..., None]) + np.array((236, 252, 255.0)) * halo[..., None]
        gl[..., :3] = col
        gl[..., 3] = (0.42 + 0.58 * halo) * 255
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
    fx = xx / max(1, W - 1)
    edge = np.minimum(np.minimum(xx, W - 1 - xx), np.minimum(yy, H - 1 - yy))
    vertical = face in ("side", "front", "back")

    # 基本の濃淡 (ムラ)
    n = value_noise(H, W, 4, seed) * 0.5 + value_noise(H, W, 1, seed + 1) * 0.3 + rng.random((H, W)) * 0.2
    col = dark + (light - dark) * n[..., None]

    # 素材ごとの模様
    if material in ("bone", "darkbone"):
        # 滑らかなクリーム色の骨: 低い周波数のムラと、数本の細いひび、関節の暗い線
        col = dark + (light - dark) * (0.72 * value_noise(H, W, 8, seed) + 0.28 * value_noise(H, W, 3, seed + 1))[..., None]
        ridge = 0.5 + 0.5 * np.sin(xx * 0.6 + value_noise(H, W, 8, seed + 4) * 4)
        col *= (0.95 + 0.06 * ridge)[..., None]
        crack = np.abs(value_noise(H, W, 6, seed + 2) - 0.5) < 0.006
        col[crack] *= 0.55
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
    elif material == "hide":
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

    # 面ごとの陰影。縦面は上が明るく、下に向かって暗くなる (AO)
    if vertical:
        col *= (1.12 - 0.3 * fy)[..., None]
        col[fy > 0.85] *= 0.86
    else:
        col *= (1.05 if face == "top" else 0.9)

    # 1px 相当の面取り。上と左に明るい縁、外周は暗い縁
    bw = RES if min(W, H) >= 6 else 0
    if bw:
        col[(yy >= bw) & (yy < 2 * bw) & (edge >= bw)] *= 1.15
        col[(xx >= bw) & (xx < 2 * bw) & (edge >= bw)] *= 1.05
        col[edge < bw] *= 0.62

    gl = np.zeros((H, W, 4), float)
    if decal and face == "front":
        if decal == "skull":
            col[fy < 0.2] *= 0.7  # 眉の陰
            col[(fy >= 0.2) & (fy < 0.28)] *= 1.06  # 眉の稜線
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
def sin(speed, amp, phase=0):
    return f"math.sin(query.anim_time * {speed} + {phase}) * {amp}"


def humanoid_anims(prefix, extra):
    anims = {
        f"animation.{prefix}.idle": {"loop": True, "animation_length": 3.0, "bones": {
            "body": {"rotation": [sin(120, 1.5), "0", "0"]},
            "left_arm": {"rotation": [sin(120, 3, 1), "0", "-3"]},
            "right_arm": {"rotation": [sin(120, 3, 2), "0", "3"]}}},
        f"animation.{prefix}.walk": {"loop": True, "animation_length": 1.0, "bones": {
            "left_leg": {"rotation": [sin(360, 32), "0", "0"]},
            "right_leg": {"rotation": [sin(360, -32), "0", "0"]},
            "left_arm": {"rotation": [sin(360, -22), "0", "-3"]},
            "right_arm": {"rotation": [sin(360, 22), "0", "3"]},
            "body": {"rotation": ["4", sin(360, 3), "0"], "position": ["0", f"math.abs({sin(360, 0.8)})", "0"]}}},
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
            "body": {"rotation": keyframes((0, (0, 0, 0)), (0.22, (0, 15, 0)), (0.38, (8, -20, 0)), (0.6, (0, 0, 0)))}}},
        "animation.bone_sentinel.block": {"animation_length": 0.5, "bones": {
            "left_arm": {"rotation": keyframes((0, (0, 0, 0)), (0.1, (-70, -30, 0)), (0.4, (-70, -30, 0)), (0.5, (0, 0, 0)))}}}}))
    write_anims("rib_archer", humanoid_anims("rib_archer", {
        "animation.rib_archer.shoot": {"animation_length": 0.8, "bones": {
            "left_arm": {"rotation": keyframes((0, (0, 0, 0)), (0.25, (-90, 10, 0)), (0.65, (-90, 10, 0)), (0.8, (0, 0, 0)))},
            "right_arm": {"rotation": keyframes((0, (0, 0, 0)), (0.25, (-90, -25, 0)), (0.55, (-85, -40, 0)), (0.62, (-90, -10, 0)), (0.8, (0, 0, 0)))}}}}))
    write_anims("moss_crawler", {
        "animation.moss_crawler.idle": {"loop": True, "animation_length": 2.0, "bones": {
            "body": {"position": ["0", sin(180, 0.4), "0"]}, "tail": {"rotation": ["0", sin(180, 12), "0"]}}},
        "animation.moss_crawler.walk": {"loop": True, "animation_length": 0.5, "bones": {
            "leg_fl": {"rotation": [sin(720, 35), "0", "0"]}, "leg_br": {"rotation": [sin(720, 35), "0", "0"]},
            "leg_fr": {"rotation": [sin(720, -35), "0", "0"]}, "leg_bl": {"rotation": [sin(720, -35), "0", "0"]},
            "body": {"position": ["0", f"math.abs({sin(720, 0.6)})", "0"]}, "tail": {"rotation": ["0", sin(720, 18), "0"]}}},
        "animation.moss_crawler.pounce": {"animation_length": 0.6, "bones": {
            "body": {"rotation": keyframes((0, (0, 0, 0)), (0.15, (15, 0, 0)), (0.35, (-20, 0, 0)), (0.6, (0, 0, 0)))},
            "head": {"rotation": keyframes((0, (0, 0, 0)), (0.3, (-25, 0, 0)), (0.6, (0, 0, 0)))},
            "leg_fl": {"rotation": keyframes((0, (0, 0, 0)), (0.3, (-60, 0, 0)), (0.6, (0, 0, 0)))},
            "leg_fr": {"rotation": keyframes((0, (0, 0, 0)), (0.3, (-60, 0, 0)), (0.6, (0, 0, 0)))}}}})
    segs = {f"seg{i}": {"rotation": ["0", sin(270, 8 + i * 3, -i * 0.8), "0"]} for i in range(5)}
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
            "body": {"rotation": keyframes((0, (0, 0, 0)), (0.45, (0, 35, 0)), (0.62, (0, -40, 0)), (1.0, (0, 0, 0)))}}},
        "animation.whalebone_captain.leap": {"animation_length": 0.8, "bones": {
            "body": {"rotation": keyframes((0, (0, 0, 0)), (0.3, (25, 0, 0)), (0.5, (-15, 0, 0)), (0.8, (0, 0, 0)))},
            "left_leg": {"rotation": keyframes((0, (0, 0, 0)), (0.3, (-40, 0, 0)), (0.8, (0, 0, 0)))},
            "right_leg": {"rotation": keyframes((0, (0, 0, 0)), (0.3, (-40, 0, 0)), (0.8, (0, 0, 0)))},
            "right_arm": {"rotation": keyframes((0, (0, 0, 0)), (0.4, (-170, 0, 0)), (0.8, (-170, 0, 0)))},
            "left_arm": {"rotation": keyframes((0, (0, 0, 0)), (0.4, (-170, 0, 0)), (0.8, (-170, 0, 0)))}}},
        "animation.whalebone_captain.slam": {"animation_length": 0.7, "bones": {
            "right_arm": {"rotation": keyframes((0, (-170, 0, 0)), (0.15, (20, 0, 0)), (0.7, (0, 0, 0)))},
            "left_arm": {"rotation": keyframes((0, (-170, 0, 0)), (0.15, (20, 0, 0)), (0.7, (0, 0, 0)))},
            "body": {"rotation": keyframes((0, (-15, 0, 0)), (0.15, (30, 0, 0)), (0.7, (0, 0, 0)))}}},
        "animation.whalebone_captain.roar": {"animation_length": 1.4, "bones": {
            "head": {"rotation": keyframes((0, (0, 0, 0)), (0.3, (-35, 0, 0)), (1.1, (-35, 0, 0)), (1.4, (0, 0, 0)))},
            "body": {"rotation": keyframes((0, (0, 0, 0)), (0.3, (-12, 0, 0)), (1.1, (-12, 0, 0)), (1.4, (0, 0, 0)))},
            "left_arm": {"rotation": keyframes((0, (0, 0, 0)), (0.3, (-30, 0, -70)), (1.1, (-30, 0, -70)), (1.4, (0, 0, 0)))},
            "right_arm": {"rotation": keyframes((0, (0, 0, 0)), (0.3, (-30, 0, 70)), (1.1, (-30, 0, 70)), (1.4, (0, 0, 0)))}}}}))


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


def preview(models, out_dir):
    out_dir.mkdir(parents=True, exist_ok=True)
    for model in models:
        cubes = [(c["origin"], c["size"]) for b in model.bones for c in b["cubes"]]
        S = 6
        front = Image.new("RGB", (80 * S, 70 * S), (200, 215, 235))
        side = Image.new("RGB", (80 * S, 70 * S), (200, 215, 235))
        df, ds = ImageDraw.Draw(front), ImageDraw.Draw(side)
        for (x, y, z), (w, h, d) in sorted(cubes, key=lambda c: -c[0][2]):
            df.rectangle([(x + 40) * S, (64 - y - h) * S, (x + w + 40) * S, (64 - y) * S], fill=(150, 145, 130), outline=(40, 40, 50))
        for (x, y, z), (w, h, d) in sorted(cubes, key=lambda c: c[0][0]):
            ds.rectangle([(z + 40) * S, (64 - y - h) * S, (z + d + 40) * S, (64 - y) * S], fill=(150, 145, 130), outline=(40, 40, 50))
        img = Image.new("RGB", (160 * S, 70 * S))
        img.paste(front, (0, 0))
        img.paste(side, (80 * S, 0))
        img.resize((img.width // 3, img.height // 3)).save(out_dir / f"{model.name}.png")


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
