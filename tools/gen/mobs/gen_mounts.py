#!/usr/bin/env python3
"""乗り物 (docs/design/38) の五つの見た目のモデルを作る: GeckoLib の geo (箱モデル)・絵・アニメ・ラフの一覧。

  python -I tools/gen/mobs/gen_mounts.py install   -> assets/skyabyss/geo/entity/mount_<名前>.geo.json
                                                       assets/skyabyss/textures/entity/mount_<名前>.png
                                                       assets/skyabyss/animations/entity/mount.animation.json
  python -I tools/gen/mobs/gen_mounts.py sheets    -> docs/review/mount_roughs/<名前>.png (見た目ごとに 3 案を横に並べた一覧)

案 1 がゲームに入っているもの (install)。案 2・3 は同じ骨 (leg_fl / leg_fr / leg_bl / leg_br / head / tail / wing_l / wing_r) の別の姿で、
ユーザーが最終の見た目を選ぶための下絵。骨は全部の見た目で同じ名前にして、一つのアニメ (idle / walk) を共有する。
作りの決まり (docs/design/01 のアート): 箱は全部閉じている・関節は親に沈める (脚の付け根は体の中、首は体の中、角は頭の中)・箱どうしは同じ面で重ねない。
前は -Z。1 単位 = 1/16 ブロック、1 単位 = 絵の 1 画素。
"""
import hashlib
import json
import math
import os
import sys

import numpy as np
from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__)))))
ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets", "skyabyss")
REVIEW = os.path.join(ROOT, "docs", "review", "mount_roughs")
LOOKS = ["horn_beast", "ash_deer", "great_lizard", "whale_calf", "shell_tortoise", "storm_chick", "ember_fox"]


# ---------------------------------------------------------------- 箱のモデル
class Cube:
    def __init__(self, bone, o, size, color, feat):
        self.bone = bone
        self.o = o
        self.size = tuple(int(math.ceil(v)) for v in size)
        self.color = color
        self.feat = feat or {}
        self.uv = (0, 0)
        self.name = ""


class Model:
    def __init__(self, name):
        self.name = name
        self.bones = [{"name": "root", "pivot": [0, 0, 0], "parent": None}]
        self.cubes = []
        self.tex = (128, 128)

    def bone(self, name, parent, pivot):
        self.bones.append({"name": name, "pivot": list(pivot), "parent": parent})

    def cube(self, bone, o, size, color, **feat):
        c = Cube(bone, o, size, color, feat)
        self.cubes.append(c)
        return c

    def mirror(self, bone_l, bone_r, o, size, color, **feat):
        """左 (x < 0) の箱と、x を反転した右の箱を作る"""
        self.cube(bone_l, o, size, color, **feat)
        self.cube(bone_r, [-o[0] - size[0], o[1], o[2]], size, color, **dict(feat, flip=True))

    # 箱の面の絵の置き場所 (箱 UV)。棚詰め
    def pack(self):
        W = 128
        x = y = rowh = 0
        for c in self.cubes:
            w, h, d = [int(math.ceil(v)) for v in c.size]
            bw, bh = 2 * (w + d), d + h
            if x + bw > W:
                x, y, rowh = 0, y + rowh, 0
            c.uv = (x, y)
            x += bw
            rowh = max(rowh, bh)
        H = 32
        while H < y + rowh:
            H *= 2
        self.tex = (W, H)

    def geo(self):
        self.pack()
        bones = []
        for b in self.bones:
            d = {"name": b["name"], "pivot": b["pivot"]}
            if b["parent"]:
                d["parent"] = b["parent"]
            cs = []
            for c in self.cubes:
                if c.bone == b["name"]:
                    cs.append({"origin": [round(v, 3) for v in c.o], "size": [int(math.ceil(v)) for v in c.size], "uv": list(c.uv)})
            if cs:
                d["cubes"] = cs
            bones.append(d)
        return {"format_version": "1.12.0", "minecraft:geometry": [{
            "description": {"identifier": "geometry.mount_" + self.name, "texture_width": self.tex[0], "texture_height": self.tex[1],
                            "visible_bounds_width": 4, "visible_bounds_height": 4, "visible_bounds_offset": [0, 1, 0]},
            "bones": bones}]}


def _noise(seed, x, y):
    h = hashlib.md5(("%s:%d:%d" % (seed, x, y)).encode()).digest()
    return (h[0] / 255.0 - 0.5)


SHADE = {"north": 0.92, "south": 0.80, "east": 0.86, "west": 0.86, "up": 1.12, "down": 0.66}


def face_rect(c, face):
    w, h, d = [int(math.ceil(v)) for v in c.size]
    u, v = c.uv
    return {"north": (u + d, v + d, w, h), "east": (u, v + d, d, h), "west": (u + d + w, v + d, d, h),
            "south": (u + 2 * d + w, v + d, w, h), "up": (u + d, v, w, d), "down": (u + d + w, v, w, d)}[face]


def paint(model):
    """絵を塗る。箱の色 + 面ごとの陰 + 画素のゆらぎ。feat の eyes / stripe / spots / glow / belly で目や模様を足す"""
    W, H = model.tex
    img = np.zeros((H, W, 4), dtype=np.float32)
    for ci, c in enumerate(model.cubes):
        base = np.array(c.color, dtype=np.float32)
        for face in SHADE:
            x0, y0, fw, fh = face_rect(c, face)
            for py in range(fh):
                for px in range(fw):
                    n = _noise("%s%d" % (model.name, ci), x0 + px, y0 + py) * 18
                    col = base * SHADE[face] + n
                    f = c.feat
                    if "belly" in f and face in ("north", "south", "east", "west") and py >= fh * 0.62:
                        col = np.array(f["belly"], dtype=np.float32) * SHADE[face] + n
                    if "stripe" in f and (px + py // 2) % f.get("every", 4) == 0:
                        col = np.array(f["stripe"], dtype=np.float32) * SHADE[face] + n
                    if "spots" in f and face == "up" and _noise("sp" + model.name + str(ci), px, py) > 0.38:
                        col = np.array(f["spots"], dtype=np.float32) * SHADE[face]
                    if "rim" in f and (py == 0 or py == fh - 1 or px == 0 or px == fw - 1) and face != "up":
                        col = np.array(f["rim"], dtype=np.float32) * SHADE[face]
                    if "hex" in f and face == "up":
                        if (px % 4 == 0) or (py % 4 == 0):
                            col = np.array(f["hex"], dtype=np.float32) * SHADE[face]
                    if "zig" in f and face in ("east", "west") and abs(((px + py) % 6) - 3) == 0 and fh >= 4:
                        col = np.array(f["zig"], dtype=np.float32)
                    img[y0 + py, x0 + px, :3] = np.clip(col, 0, 255)
                    img[y0 + py, x0 + px, 3] = 255
            f = c.feat
            if "eyes" in f:
                # eyes = (顔の面, 左の目の px, py, 目の色)。左右対称に 2 つ置く。面の左右は見る人から
                face, ex, ey, ecol = f["eyes"]
                x0, y0, fw, fh = face_rect(c, face)
                for px in (ex, fw - 1 - ex):
                    for dy in range(f.get("eye_h", 2)):
                        yy = y0 + ey + dy
                        img[yy, x0 + px, :3] = ecol
                        if f.get("eye_w", 1) > 1:
                            img[yy, x0 + px + (1 if px < fw / 2 else -1), :3] = ecol
            if "side_eye" in f:
                # 横顔の目: (px, py, 色)。east / west の両方
                ex, ey, ecol = f["side_eye"]
                for face in ("east", "west"):
                    x0, y0, fw, fh = face_rect(c, face)
                    px = ex if face == "west" else fw - 1 - ex
                    for dy in range(2):
                        img[y0 + ey + dy, x0 + px, :3] = ecol
            if "mouth" in f:
                face, my, mcol = f["mouth"]
                x0, y0, fw, fh = face_rect(c, face)
                for px in range(1, fw - 1):
                    img[y0 + my, x0 + px, :3] = mcol
            if "nose" in f:
                x0, y0, fw, fh = face_rect(c, "north")
                for px in range(fw // 2 - 1, fw // 2 + 1):
                    img[y0, x0 + px, :3] = f["nose"]
                    img[y0 + 1, x0 + px, :3] = f["nose"]
            if "tip" in f:
                # 先を別の色に (上の面と横の上 n 画素)
                n, tcol = f["tip"]
                for face in ("north", "east", "west", "south"):
                    x0, y0, fw, fh = face_rect(c, face)
                    for py in range(min(n, fh)):
                        for px in range(fw):
                            img[y0 + py, x0 + px, :3] = np.array(tcol) * SHADE[face]
    return Image.fromarray(img.astype(np.uint8), "RGBA")


# ---------------------------------------------------------------- 脚の共通部品
def legs(m, xs, zs, w, d, top, bottom_h, color, hoof, hip_in=2):
    """四つ足の脚。xs = (左の x の外端, ...) ではなく、(x 位置, z 位置) の組みの 4 つを fl fr bl br の順に渡す。top = 付け根の高さ"""
    names = ["leg_fl", "leg_fr", "leg_bl", "leg_br"]
    for n, (x, z) in zip(names, [(xs[0], zs[0]), (xs[1], zs[0]), (xs[0], zs[1]), (xs[1], zs[1])]):
        m.bone(n, "body", [x + w / 2, top, z + d / 2])
        m.cube(n, [x, 0, z], [w, top + hip_in, d], color)
        if hoof:
            m.cube(n, [x - 0.5, 0, z - 0.5], [w + 1, bottom_h, d + 1], hoof)


# ---------------------------------------------------------------- 五つの見た目 (v = 案 1〜3)
def horn_beast(v):
    m = Model("horn_beast")
    coat = [(112, 82, 60), (96, 84, 76), (128, 104, 78)][v - 1]
    dark = [(78, 56, 42), (60, 52, 48), (96, 72, 54)][v - 1]
    horn = (226, 214, 184)
    m.bone("body", "root", [0, 14, 0])
    m.cube("body", [-7, 13, -11], [14, 12, 22], coat, belly=dark)
    m.cube("body", [-6, 24, -9], [12, 4, 10], dark)              # 肩のこぶ
    m.cube("body", [-8, 19, -4], [16, 7, 9], (156, 50, 44), rim=(222, 176, 70))  # 荷を載せる赤い敷布
    if v == 2:
        m.cube("body", [-6.5, 20, -15], [13, 9, 8], dark)        # たてがみの厚い襟
    legs(m, (-7.5, 2.5), (-10, 4), 5, 5, 14, 3, coat, (52, 40, 34))
    m.bone("head", "body", [0, 24, -10])
    m.cube("head", [-4, 19, -18], [8, 9, 9], coat, eyes=("north", 1, 5, (24, 20, 20)), side_eye=(2, 5, (24, 20, 20)))
    m.cube("head", [-3, 19, -22], [6, 5, 5], (186, 160, 132), nose=(46, 34, 30), mouth=("north", 3, (70, 50, 40)))
    hs = {1: [(3, 3, 3), (4, 3, 3), (3, 5, 3)], 2: [(4, 4, 4), (5, 3, 3), (2, 3, 2)], 3: [(2, 2, 2), (3, 2, 2), (3, 8, 3)]}[v]
    for sgn in (-1, 1):
        def mx(x, w):
            return x if sgn < 0 else -x - w
        m.cube("head", [mx(-6, hs[0][0]), 25, -14], list(hs[0]), horn)
        m.cube("head", [mx(-9, hs[1][0]), 26, -15], list(hs[1]), horn)
        m.cube("head", [mx(-10.5, hs[2][0]), 28 if v != 3 else 27, -15.5], list(hs[2]), (180, 168, 140) if v != 2 else horn)
    m.bone("tail", "body", [0, 22, 11])
    m.cube("tail", [-1.5, 14, 10], [3, 10, 3], coat)
    m.cube("tail", [-2, 11, 9.5], [4, 4, 4], dark)
    return m


def ash_deer(v):
    m = Model("ash_deer")
    coat = [(142, 138, 130), (120, 124, 128), (160, 150, 136)][v - 1]
    dark = (88, 86, 84)
    cream = (224, 220, 208)
    ant = (196, 184, 156)
    m.bone("body", "root", [0, 18, 0])
    m.cube("body", [-5.5, 17, -10], [11, 10, 20], coat, belly=cream)
    m.cube("body", [-5, 25, -8], [10, 3, 9], dark)                       # 背の黒い筋
    m.cube("body", [-6, 22, -3], [12, 6, 8], (110, 86, 64), rim=(200, 170, 90))  # 荷を載せる鞍布
    legs(m, (-5, 1), (-9, 5), 4, 4, 18, 3, coat, (60, 54, 50))
    m.bone("head", "body", [0, 26, -9])
    m.cube("head", [-3, 26, -14], [6, 11, 6], coat)                      # 長い首
    m.cube("head", [-3.5, 33, -19], [7, 6, 8], coat, eyes=("north", 1, 3, (24, 20, 20)), side_eye=(3, 3, (24, 20, 20)))
    m.cube("head", [-2, 33, -22], [4, 4, 4], cream, nose=(46, 34, 30))
    m.mirror("head", "head", [-5, 37, -16], [2, 4, 2], dark)             # 耳
    for sgn in (-1, 1):
        def mx(x, w):
            return x if sgn < 0 else -x - w
        m.cube("head", [mx(-4, 2), 38, -15], [2, 7, 2], ant)
        m.cube("head", [mx(-7, 3), 43, -15], [3, 2, 2], ant)
        m.cube("head", [mx(-7, 2), 45, -15], [2, 5, 2], ant)
        if v != 3:
            m.cube("head", [mx(-9, 3), 47, -15], [3, 2, 2], ant)
    m.bone("tail", "body", [0, 26, 10])
    m.cube("tail", [-2, 22, 9], [4, 6, 4], cream)
    return m


def great_lizard(v):
    m = Model("great_lizard")
    sk = [(86, 110, 72), (120, 100, 64), (70, 96, 104)][v - 1]
    belly = (200, 190, 140)
    dark = (52, 70, 46)
    m.bone("body", "root", [0, 10, 0])
    m.cube("body", [-7, 9, -12], [14, 9, 24], sk, belly=belly, stripe=dark, every=6)
    m.cube("body", [-6, 17, -10], [12, 3, 20], dark)                     # 背びれのうね
    m.cube("body", [-8, 15, -5], [16, 3, 9], (150, 70, 50), rim=(222, 190, 90))  # 荷を載せる敷布
    legs(m, (-9, 5), (-10, 4), 4, 5, 10, 2, sk, dark, hip_in=2)
    m.bone("head", "body", [0, 14, -11])
    m.cube("head", [-4, 10, -20], [8, 7, 10], sk, eyes=("north", 1, 4, (232, 190, 60)), side_eye=(3, 4, (232, 190, 60)), mouth=("north", 2, (40, 52, 36)))
    m.cube("head", [-3, 10, -23], [6, 4, 4], sk, nose=(30, 40, 28))
    if v == 2:
        m.cube("head", [-1, 16, -18], [2, 4, 6], (190, 70, 50))          # とさか
    m.bone("tail", "body", [0, 13, 11])
    m.cube("tail", [-4, 9, 10], [8, 6, 8], sk)
    m.bone("tail_tip", "tail", [0, 12, 18])
    m.cube("tail_tip", [-2.5, 9.5, 17], [5, 4, 9], sk, stripe=dark, every=4)
    return m


def whale_calf(v):
    m = Model("whale_calf")
    sk = [(96, 118, 140), (88, 112, 116), (122, 130, 150)][v - 1]
    pale = (192, 206, 214)
    rock = (118, 118, 112)
    m.bone("body", "root", [0, 7, 0])
    m.cube("body", [-8, 7, -13], [16, 14, 26], sk)
    m.cube("body", [-7.5, 6.7, -11], [15, 3, 22], pale)          # 腹 (体の下に少しはみ出し、同じ面を避ける)
    m.cube("body", [-5, 20, -8], [10, 4, 6], rock, spots=(86, 112, 78), hex=(98, 98, 94))
    m.cube("body", [-4, 20.5, -1], [8, 3, 6], rock, spots=(86, 112, 78))
    if v == 2:
        for sx in (-1, 1):
            m.cube("body", [7 if sx > 0 else -11, 12, -7], [4, 6, 10], rock, hex=(98, 98, 94))
    legs(m, (-9, 5), (-10, 3), 4, 6, 6, 2, (76, 98, 122), None)
    m.bone("head", "body", [0, 13, -12])
    m.cube("head", [-7, 8, -22], [14, 12, 11], sk, mouth=("north", 4, (50, 62, 78)), side_eye=(2, 6, (20, 22, 30)))
    m.cube("head", [-6, 7.8, -21.5], [12, 3, 9], pale)
    if v == 3:
        m.cube("head", [-1, 11.5, -29], [2, 2, 8], (232, 228, 214))
    m.bone("tail", "body", [0, 13, 12])
    m.cube("tail", [-3, 9, 11], [6, 6, 8], sk)
    m.bone("tail_tip", "tail", [0, 12, 19])
    m.cube("tail_tip", [-8, 10.5, 18], [16, 2, 6], (76, 98, 122) if v != 2 else rock)
    m.cube("tail_tip", [-2, 9.5, 17], [4, 4, 4], sk)
    return m


def shell_tortoise(v):
    m = Model("shell_tortoise")
    skin = (118, 140, 98)
    shell = [(46, 100, 104), (74, 86, 70), (60, 112, 100)][v - 1]
    shell2 = [(72, 138, 140), (104, 116, 96), (96, 150, 130)][v - 1]
    m.bone("body", "root", [0, 10, 0])
    m.cube("body", [-9, 10, -12], [18, 6, 24], skin)
    m.cube("body", [-10, 15, -13], [20, 4, 26], shell, hex=(30, 70, 76), rim=(210, 170, 120))
    if v == 2:
        m.cube("body", [-8, 19, -11], [16, 4, 22], shell2, hex=(80, 90, 76))
        m.cube("body", [-6, 23, -9], [12, 4, 18], shell, hex=(60, 70, 58))
        m.cube("body", [-3.5, 27, -6], [7, 3, 12], shell2)
    else:
        m.cube("body", [-8, 19, -11], [16, 3, 22], shell2, hex=(40, 100, 108))
        m.cube("body", [-5, 22, -8], [10, 2, 16], shell, hex=(30, 70, 76))
    if v == 3:
        for (x, z, c) in ((-7, -6, (232, 120, 110)), (4, 3, (232, 160, 90)), (-4, 6, (210, 90, 120))):
            m.cube("body", [x, 24, z], [3, 4, 3], c)
    legs(m, (-10, 5), (-11, 5), 5, 5, 11, 2, skin, (88, 100, 76))
    m.bone("head", "body", [0, 13, -12])
    m.cube("head", [-3, 10, -18], [6, 6, 7], skin)
    m.cube("head", [-3.5, 10.5, -23], [7, 6, 7], skin, eyes=("north", 1, 2, (20, 24, 20)), mouth=("north", 4, (70, 84, 60)))
    m.bone("tail", "body", [0, 11, 12])
    m.cube("tail", [-1, 10, 11], [2, 2, 5], skin)
    return m


def storm_chick(v):
    m = Model("storm_chick")
    fl = [(236, 224, 168), (214, 226, 236), (240, 210, 140)][v - 1]
    blue = (66, 104, 190)
    m.bone("body", "root", [0, 18, 0])
    body_h = 12 if v != 3 else 14
    m.cube("body", [-7, 18, -8], [14, body_h, 16], fl, belly=(250, 246, 226), zig=(250, 224, 80))
    m.cube("body", [-6, 17.6, -9.5], [12, 4, 8], (250, 246, 226))
    for n, (x, d) in (("leg_fl", (-5, 1)), ("leg_fr", (2, 1))):
        m.bone(n, "body", [x + 1.5, 18, 0.5])
        m.cube(n, [x, 0, -1], [3, 19, 3], (226, 140, 60))
        m.cube(n, [x - 1, 0, -4], [5, 2, 3], (226, 140, 60))
    m.bone("leg_bl", "body", [0, 18, 0])
    m.bone("leg_br", "body", [0, 18, 0])
    for n, sgn in (("wing_l", -1), ("wing_r", 1)):
        px = -7 if sgn < 0 else 7
        m.bone(n, "body", [px, 28, -2])
        ox = -12 if sgn < 0 else 6
        m.cube(n, [ox, 20, -4], [6, 9, 9], blue, rim=(236, 244, 250))
        m.cube(n, [ox - sgn * 1 - (0 if sgn < 0 else 0), 17, -3], [5, 5, 7], (40, 60, 130), tip=(2, (250, 224, 80)))
    m.bone("head", "body", [0, 28, -6])
    hh = 9 if v != 2 else 12
    m.cube("head", [-5, 26, -12], [10, hh, 10], fl, eyes=("north", 2, 4, (24, 24, 40)), eye_h=2)
    m.cube("head", [-2, 29, -16], [4, 3, 5], (240, 150, 50), nose=(200, 100, 30))
    m.cube("head", [-1, 26 + hh - 1, -9], [2, 4, 3], blue)
    m.cube("head", [-1, 26 + hh - 1, -6], [2, 6, 3], (250, 224, 80))
    m.bone("tail", "body", [0, 25, 7])
    m.cube("tail", [-3, 24, 7], [6, 6, 6], fl)
    m.cube("tail", [-2, 28, 10], [4, 6, 3], blue, tip=(2, (250, 224, 80)))
    return m


def ember_fox(v):
    m = Model("ember_fox")
    fur = [(196, 92, 38), (170, 70, 40), (216, 120, 52)][v - 1]
    cream = (240, 224, 196)
    dark = (50, 36, 34)
    glow = (255, 190, 70)
    m.bone("body", "root", [0, 13, 0])
    m.cube("body", [-5, 13, -10], [10, 9, 20], fur, belly=cream, stripe=dark, every=7)
    m.cube("body", [-4.5, 12.6, -11], [9, 6, 5], cream)
    legs(m, (-5.5, 1.5), (-9, 5), 4, 4, 13, 5, dark, None)
    m.bone("head", "body", [0, 20, -9])
    m.cube("head", [-4, 17, -17], [8, 7, 8], fur, eyes=("north", 1, 3, glow), eye_h=2)
    m.cube("head", [-2, 17, -21], [4, 3, 5], cream, nose=(30, 24, 24))
    m.mirror("head", "head", [-4, 24, -13], [3, 5, 2], fur, tip=(2, dark))
    m.bone("tail", "body", [0, 18, 10])
    m.cube("tail", [-3, 15, 9], [6, 6, 10], fur, stripe=glow, every=5)
    m.cube("tail", [-3.5, 14.8, 18], [7, 7, 5], glow, stripe=(255, 120, 40), every=3)
    if v == 2:
        m.cube("tail", [-3, 21, 9], [6, 6, 10], fur)
        m.cube("tail", [-3.5, 20.8, 18], [7, 7, 5], glow)
    if v == 3:
        m.cube("head", [-3, 23, -10], [6, 6, 6], glow)   # 炎のたてがみ
    return m


BUILD = {"horn_beast": horn_beast, "ash_deer": ash_deer, "great_lizard": great_lizard, "whale_calf": whale_calf, "shell_tortoise": shell_tortoise, "storm_chick": storm_chick, "ember_fox": ember_fox}


# ---------------------------------------------------------------- アニメ
def animations():
    legs_ = {}
    swing = 28
    for n, ph in (("leg_fl", 0), ("leg_br", 0), ("leg_fr", 180), ("leg_bl", 180)):
        legs_[n] = {"rotation": ["math.sin(query.anim_time * 450 + %d) * %d" % (ph, swing), "0", "0"]}
    walk = dict(legs_)
    walk["body"] = {"position": ["0", "math.abs(math.sin(query.anim_time * 450)) * 0.5", "0"]}
    walk["head"] = {"rotation": ["math.sin(query.anim_time * 450 + 90) * 3", "0", "0"]}
    walk["tail"] = {"rotation": ["0", "math.sin(query.anim_time * 450) * 12", "0"]}
    walk["wing_l"] = {"rotation": ["0", "0", "math.sin(query.anim_time * 900) * 14"]}
    walk["wing_r"] = {"rotation": ["0", "0", "math.sin(query.anim_time * 900 + 180) * 14"]}
    idle = {
        "body": {"position": ["0", "math.sin(query.anim_time * 72) * 0.25", "0"]},
        "head": {"rotation": ["math.sin(query.anim_time * 72 + 40) * 2", "math.sin(query.anim_time * 36) * 4", "0"]},
        "tail": {"rotation": ["0", "math.sin(query.anim_time * 144) * 8", "0"]},
        "wing_l": {"rotation": ["0", "0", "math.sin(query.anim_time * 72) * 3"]},
        "wing_r": {"rotation": ["0", "0", "math.sin(query.anim_time * 72 + 180) * 3"]},
    }
    return {"format_version": "1.8.0", "animations": {
        "animation.mount.idle": {"loop": True, "animation_length": 5.0, "bones": idle},
        "animation.mount.walk": {"loop": True, "animation_length": 0.8, "bones": walk}}}


# ---------------------------------------------------------------- 描画 (ラフの一覧用。z バッファの箱の描画)
def corners(c, face):
    x, y, z = c.o
    w, h, d = c.size
    x1, y1, z1 = x + w, y + h, z + d
    return {"north": [(x1, y1, z), (x, y1, z), (x, y, z), (x1, y, z)],
            "south": [(x, y1, z1), (x1, y1, z1), (x1, y, z1), (x, y, z1)],
            "east": [(x1, y1, z1), (x1, y1, z), (x1, y, z), (x1, y, z1)],
            "west": [(x, y1, z), (x, y1, z1), (x, y, z1), (x, y, z)],
            "up": [(x, y1, z), (x1, y1, z), (x1, y1, z1), (x, y1, z1)],
            "down": [(x, y, z1), (x1, y, z1), (x1, y, z), (x, y, z)]}[face]


def render(m, tex, yaw, pitch=22, S=7, size=(420, 330), pose=None):
    """yaw 度だけ回して見る (0 = 前から)。tex = PIL の絵。pose = {骨: 回転 (度, x)} (歩き方の見せ用)"""
    texa = np.array(tex)
    Wd, Ht = size
    zbuf = np.full((Ht, Wd), -1e9, dtype=np.float32)
    out = np.zeros((Ht, Wd, 4), dtype=np.uint8)
    out[..., :3] = (236, 232, 222)
    out[..., 3] = 255
    cy, sy_ = math.cos(math.radians(yaw)), math.sin(math.radians(yaw))
    cp, sp = math.cos(math.radians(pitch)), math.sin(math.radians(pitch))
    pivots = {b["name"]: b for b in m.bones}
    chain = {}

    def rot_point(p, name):
        # 骨の x 回転 (歩き方の見せ用)。親の回転も掛ける
        names = []
        n = name
        while n and n != "root":
            names.append(n)
            n = pivots[n]["parent"]
        x, y, z = p
        for n in names:
            ang = (pose or {}).get(n, 0)
            if ang:
                px, py, pz = pivots[n]["pivot"]
                a = math.radians(ang)
                y2 = py + (y - py) * math.cos(a) - (z - pz) * math.sin(a)
                z2 = pz + (y - py) * math.sin(a) + (z - pz) * math.cos(a)
                y, z = y2, z2
        return (x, y, z)

    def proj(p):
        x, y, z = p
        # yaw: 地面の回転 (Y 軸)
        x2 = x * cy + z * sy_
        z2 = -x * sy_ + z * cy
        # pitch: 上から見下ろす
        y3 = y * cp - z2 * sp
        z3 = y * sp + z2 * cp
        return (Wd / 2 + x2 * S, Ht * 0.86 - y3 * S, z3)

    for c in m.cubes:
        for face in SHADE:
            q = [proj(rot_point(p, c.bone)) for p in corners(c, face)]
            x0, y0, fw, fh = face_rect(c, face)
            uvs = [(x0, y0), (x0 + fw, y0), (x0 + fw, y0 + fh), (x0, y0 + fh)]
            for tri in ((0, 1, 2), (0, 2, 3)):
                _tri(out, zbuf, texa, [q[i] for i in tri], [uvs[i] for i in tri])
    return Image.fromarray(out, "RGBA")


def _tri(out, zbuf, texa, q, uv):
    xs = [p[0] for p in q]
    ys = [p[1] for p in q]
    x0, x1 = max(0, int(min(xs))), min(out.shape[1] - 1, int(max(xs)) + 1)
    y0, y1 = max(0, int(min(ys))), min(out.shape[0] - 1, int(max(ys)) + 1)
    if x1 <= x0 or y1 <= y0:
        return
    gx, gy = np.meshgrid(np.arange(x0, x1 + 1) + 0.5, np.arange(y0, y1 + 1) + 0.5)
    (ax, ay, az), (bx, by, bz), (cx, cy_, cz) = q
    den = (by - cy_) * (ax - cx) + (cx - bx) * (ay - cy_)
    if abs(den) < 1e-6:
        return
    l1 = ((by - cy_) * (gx - cx) + (cx - bx) * (gy - cy_)) / den
    l2 = ((cy_ - ay) * (gx - cx) + (ax - cx) * (gy - cy_)) / den
    l3 = 1 - l1 - l2
    eps = -0.002
    inside = (l1 >= eps) & (l2 >= eps) & (l3 >= eps)
    if not inside.any():
        return
    z = l1 * az + l2 * bz + l3 * cz
    u = l1 * uv[0][0] + l2 * uv[1][0] + l3 * uv[2][0]
    v = l1 * uv[0][1] + l2 * uv[1][1] + l3 * uv[2][1]
    ui = np.clip(np.floor(u - 0.001).astype(int), 0, texa.shape[1] - 1)
    vi = np.clip(np.floor(v - 0.001).astype(int), 0, texa.shape[0] - 1)
    sub = out[y0:y1 + 1, x0:x1 + 1]
    zs = zbuf[y0:y1 + 1, x0:x1 + 1]
    ok = inside & (z > zs) & (texa[vi, ui, 3] > 0)
    zs[ok] = z[ok]
    sub[ok] = texa[vi, ui][ok]


# ---------------------------------------------------------------- 呼び笛のアイコン (32x32)
def whistle():
    im = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    px = im.load()
    bone = [(246, 238, 214), (222, 208, 176), (178, 160, 124), (120, 104, 78)]
    # 横長の笛 (口 = 左、息の穴 = 上)
    body = (5, 12, 26, 20)
    for y in range(body[1], body[3] + 1):
        for x in range(body[0], body[2] + 1):
            shade = 0 if y <= 14 else 1 if y <= 17 else 2
            px[x, y] = bone[shade] + (255,)
    for y in range(body[1], body[3] + 1):
        px[body[0], y] = (0, 0, 0, 0) if y in (12, 20) else bone[3] + (255,)
        px[body[2], y] = (0, 0, 0, 0) if y in (12, 20) else bone[3] + (255,)
    for x in range(body[0] + 1, body[2]):
        px[x, body[1] - 1] = bone[3] + (255,)
        px[x, body[3] + 1] = bone[3] + (255,)
    # 吹き口 (左に細く)
    for y in range(14, 19):
        for x in range(2, 5):
            px[x, y] = bone[1] + (255,)
        px[2, y] = bone[3] + (255,)
    for x in range(2, 5):
        px[x, 13] = bone[3] + (255,)
        px[x, 19] = bone[3] + (255,)
    # 息の穴と穴の縁
    for x in range(12, 17):
        for y in range(10, 12):
            px[x, y] = bone[3] + (255,)
    for x in range(13, 16):
        px[x, 11] = (40, 30, 24, 255)
    # 銅の輪と紐
    for x in (20, 21):
        for y in range(11, 22):
            px[x, y] = (196, 120, 62, 255) if y % 2 else (150, 86, 44, 255)
    for (x, y) in ((26, 15), (27, 16), (28, 17), (28, 18), (27, 19), (26, 20), (25, 21), (24, 22), (23, 23), (22, 24)):
        px[x, y] = (122, 62, 52, 255)
    return im


# ---------------------------------------------------------------- 入口
def install():
    for sub in ("geo/entity", "textures/entity", "animations/entity"):
        os.makedirs(os.path.join(ASSETS, sub), exist_ok=True)
    for look in LOOKS:
        m = BUILD[look](1)
        m.pack()   # 絵を塗る前に箱の置き場を決める (後だと全部の箱が左上に重なって塗られる)
        tex = paint(m)
        with open(os.path.join(ASSETS, "geo", "entity", "mount_%s.geo.json" % look), "w", encoding="utf-8") as f:
            json.dump(m.geo(), f, indent=1)
            f.write("\n")
        tex.save(os.path.join(ASSETS, "textures", "entity", "mount_%s.png" % look))
        print("mount_%s: %d cubes, texture %dx%d" % (look, len(m.cubes), m.tex[0], m.tex[1]))
    os.makedirs(os.path.join(ASSETS, "textures", "item"), exist_ok=True)
    whistle().save(os.path.join(ASSETS, "textures", "item", "mount_whistle.png"))
    with open(os.path.join(ASSETS, "models", "item", "mount_whistle.json"), "w", encoding="utf-8") as f:
        json.dump({"parent": "minecraft:item/generated", "textures": {"layer0": "skyabyss:item/mount_whistle"}}, f, indent=2)
        f.write("\n")
    with open(os.path.join(ASSETS, "animations", "entity", "mount.animation.json"), "w", encoding="utf-8") as f:
        json.dump(animations(), f, indent=1)
        f.write("\n")


def sheets():
    os.makedirs(REVIEW, exist_ok=True)
    for look in LOOKS:
        W, H = 420, 330
        sheet = Image.new("RGBA", (W * 3, H * 2), (236, 232, 222, 255))
        for v in (1, 2, 3):
            m = BUILD[look](v)
            m.pack()
            tex = paint(m)
            sheet.paste(render(m, tex, 215), ((v - 1) * W, 0))
            sheet.paste(render(m, tex, 90, pitch=8, pose={"leg_fl": 22, "leg_br": 22, "leg_fr": -22, "leg_bl": -22}), ((v - 1) * W, H))
        sheet.convert("RGB").save(os.path.join(REVIEW, look + ".png"))
        print("wrote", look)


if __name__ == "__main__":
    cmd = sys.argv[1] if len(sys.argv) > 1 else "install"
    if cmd == "install":
        install()
    else:
        sheets()
