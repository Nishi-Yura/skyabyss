# -*- coding: utf-8 -*-
"""祠の始めの武器 6 本を、箱を組む模型 (box_model.py) で作る。

  python -I tools/gen/pixel/box_shrine.py
"""
import copy
import math
import sys
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).resolve().parent))
from box_model import SHADE, Box, build  # noqa: E402

# ---------------------------------------------------------------- 色 (祠の品)
WOOD, WOOD_D, WOOD_L = (150, 108, 78), (100, 70, 54), (196, 160, 120)
PATINA, PATINA_L, PATINA_D = (64, 150, 132), (150, 222, 196), (38, 96, 92)
GOLD, GOLD_D, GOLD_L = (232, 178, 76), (172, 110, 44), (255, 228, 150)
STEEL, STEEL_L, STEEL_D, WHITE = (176, 186, 210), (228, 236, 250), (108, 118, 150), (252, 254, 255)
CORD, CORD_D = (196, 52, 58), (128, 30, 44)
LEAF, LEAF_L = (78, 150, 74), (150, 206, 98)
BERRY, PAPER, STRING, DARK = (240, 246, 232), (246, 244, 236), (236, 230, 212), (44, 30, 36)
STAR, STAR_C, GLOW_BLUE = (110, 206, 255), (236, 252, 255), (90, 190, 255)
EMBER, EMBER_L = (255, 128, 40), (255, 222, 120)

# 絵の板の色 (文字 → 色, 光る)
ART_PAL = {
    "O": ((255, 255, 255), True), "D": ((186, 242, 255), True), "C": ((96, 196, 255), True), "c": ((40, 120, 230), True),
    "z": ((255, 236, 150), True), "A": ((255, 150, 46), True), "a": ((214, 70, 26), True),
    "j": (LEAF_L, False), "V": (LEAF, False), "v": ((40, 92, 58), False),
    "P": (PAPER, False), "p": ((222, 218, 206), False), "q": ((184, 180, 170), False),
    "R": (CORD, False), "r": (CORD_D, False),
    # にじむ光 (本体の絵では透明、光の絵だけに出る)
    "1": ((8, 18, 36), "halo"), "2": ((16, 36, 66), "halo"), "3": ((30, 66, 110), "halo"),
    "4": ((30, 12, 4), "halo"), "5": ((60, 26, 8), "halo"), "6": ((110, 50, 14), "halo"),
}


def halo_art(r, warm=False):
    """にじむ光のドット絵: 真ん中ほど明るい丸を 3 段で"""
    n = 2 * r + 1
    keys = "456" if warm else "123"
    rows = []
    for y in range(n):
        row = ""
        for x in range(n):
            d = math.hypot(x - r, y - r) / (r + 0.5)
            row += "." if d >= 1 else keys[min(2, int((1 - d) * 3.2))]
        rows.append(row)
    return rows


def cord_paint(face, w, h):
    """赤い紐: 斜めの巻き (2 色だけ)"""
    if face in ("up", "down"):
        return None
    w, h = max(w, 2), max(h * 2, 2)
    px = np.zeros((h, w, 4), float)
    m = SHADE[face]
    for yy in range(h):
        for xx in range(w):
            c = CORD if (xx + yy) % 3 else CORD_D
            px[yy, xx, :3] = [v * m for v in c]
    px[..., 3] = 255
    return px


def bx(x0, y0, z0, x1, y1, z1, col, **kw):
    return Box((x0, y0, z0), (x1, y1, z1), col, **kw)


def cb(cx, cy, cz, w, h, d, col, **kw):
    return bx(cx - w / 2, cy - h / 2, cz - d / 2, cx + w / 2, cy + h / 2, cz + d / 2, col, **kw)


def seg(p0, p1, t, col, d=None, z=0.0, ext=None, **kw):
    """xy の面で p0 から p1 へ伸びる角棒 (太さ t、奥行き d)。継ぎ目は t/2 ずつ伸ばして重ねる"""
    d = t if d is None else d
    ext = t / 2 if ext is None else ext
    dx, dy = p1[0] - p0[0], p1[1] - p0[1]
    L = math.hypot(dx, dy)
    cx, cy = (p0[0] + p1[0]) / 2, (p0[1] + p1[1]) / 2
    ang = math.degrees(math.atan2(-dx, dy))
    return Box((cx - t / 2, cy - L / 2 - ext, z - d / 2), (cx + t / 2, cy + L / 2 + ext, z + d / 2), col, rot=(0, 0, ang), pivot=(cx, cy, z), **kw)


def seg_dist(px, py, a, b):
    ax, ay = a
    bx_, by = b
    dx, dy = bx_ - ax, by - ay
    L2 = dx * dx + dy * dy
    t = 0.0 if L2 == 0 else max(0.0, min(1.0, ((px - ax) * dx + (py - ay) * dy) / L2))
    return math.hypot(px - ax - t * dx, py - ay - t * dy)


def tones(col):
    base = np.array(col, float)
    light = np.minimum(255, base + (255 - base) * 0.38 + np.array([10, 6, -6]))
    dark = np.clip(base * 0.66 + np.array([-4, -2, 14]), 0, 255)
    return base, light, dark


def run_paint(b):
    def paint(face, w, h):
        cells = b.cells
        n = len(cells)
        if face in ("north", "south"):
            row = cells if face == "south" else cells[::-1]
            px = np.zeros((1, n, 4), float)
            for i, c in enumerate(row):
                px[0, i, :3] = c
        elif face in ("up", "down"):
            px = np.zeros((1, n, 4), float)
            for i, c in enumerate(cells):
                px[0, i, :3] = np.minimum(255, np.array(c) * (1.0 if b.glow else SHADE[face]))
        else:
            px = np.zeros((1, 1, 4), float)
            px[0, 0, :3] = np.array(cells[-1 if face == "east" else 0]) * (1.0 if b.glow else SHADE[face])
        px[..., 3] = 255
        return px
    return paint


def path(pts, t, col, d=None, z=0.0, glow=False, **kw):
    """折れ線をドット絵の線のように 1 マスの段で描く (箱を傾けない)。太さ t、奥行き d。
    面の色は、上が空いたマスは明るく、下が空いたマスは暗く (縁のドット絵の陰)"""
    d = t if d is None else d
    r = max(t / 2, 0.5)
    xs = [q[0] for q in pts]
    ys = [q[1] for q in pts]
    x0, x1 = math.floor(min(xs) - r) - 1, math.ceil(max(xs) + r) + 1
    y0, y1 = math.floor(min(ys) - r) - 1, math.ceil(max(ys) + r) + 1
    W, H = x1 - x0, y1 - y0
    m = np.zeros((H, W), bool)
    for j in range(H):
        for i in range(W):
            cx, cy = x0 + i + 0.5, y0 + j + 0.5
            m[j, i] = min(seg_dist(cx, cy, a, b) for a, b in zip(pts, pts[1:])) <= r
    base, light, dark = tones(col)
    out = []
    for j in range(H):
        i = 0
        while i < W:
            if not m[j, i]:
                i += 1
                continue
            k = i
            cells = []
            while k < W and m[j, k]:
                up = j + 1 < H and m[j + 1, k]
                dn = j - 1 >= 0 and m[j - 1, k]
                lf = k - 1 >= 0 and m[j, k - 1]
                rt = k + 1 < W and m[j, k + 1]
                if glow:
                    c = base if not (up and dn and lf and rt) else np.minimum(255, base + (255 - base) * 0.55)
                elif not up:
                    c = light
                elif not dn:
                    c = dark
                elif not lf:
                    c = (base + light) / 2
                elif not rt:
                    c = (base + dark) / 2
                else:
                    c = base
                cells.append(tuple(c))
                k += 1
            b = Box((x0 + i, y0 + j, z - d / 2), (x0 + k, y0 + j + 1, z + d / 2), col, glow=glow, **kw)
            b.cells = cells
            b.paint = run_paint(b)
            out.append(b)
            i = k
    return out


def mirror(boxes):
    """左右 (x) を裏返した写し"""
    out = []
    for b in boxes:
        m = copy.copy(b)
        m.frm = (-b.to[0], b.frm[1], b.frm[2])
        m.to = (-b.frm[0], b.to[1], b.to[2])
        m.pivot = (-b.pivot[0], b.pivot[1], b.pivot[2])
        m.rot = (b.rot[0], -b.rot[1], -b.rot[2])
        m.parent = [((r[0], -r[1], -r[2]), (-p[0], p[1], p[2])) for r, p in b.parent]
        if b.art is not None:
            m.art = (b.art[0][:, ::-1], b.art[1][:, ::-1])
        if hasattr(b, "cells"):
            m.cells = b.cells[::-1]
            m.paint = run_paint(m)
        out.append(m)
    return out


def flip_y(pts, cy):
    return [(x, 2 * cy - y) for x, y in pts]


def mirror_y(boxes, cy):
    """上下 (y = cy) を裏返した写し"""
    out = []
    for b in boxes:
        m = copy.copy(b)
        m.frm = (b.frm[0], 2 * cy - b.to[1], b.frm[2])
        m.to = (b.to[0], 2 * cy - b.frm[1], b.to[2])
        m.pivot = (b.pivot[0], 2 * cy - b.pivot[1], b.pivot[2])
        m.rot = (-b.rot[0], b.rot[1], -b.rot[2])
        m.parent = [((-r[0], r[1], -r[2]), (p[0], 2 * cy - p[1], p[2])) for r, p in b.parent]
        out.append(m)
    return out


def plane(rows, cx, cy, cz=0.0, unit=1.0, depth=0.2, rot=(0, 0, 0)):
    """絵の板: 1 文字 = 1 画素の絵を、厚み depth の薄い板の前後に貼る (透けるところは描かない)"""
    H, W = len(rows), len(rows[0])
    rgba = np.zeros((H, W, 4), float)
    gm = np.zeros((H, W), bool)
    for y, r in enumerate(rows):
        for x, ch in enumerate(r):
            if ch != ".":
                col, lit = ART_PAL[ch]
                rgba[y, x, :3] = col
                rgba[y, x, 3] = 0 if lit == "halo" else 255
                gm[y, x] = bool(lit)
    b = cb(cx, cy, cz, W * unit, H * unit, depth, (0, 0, 0), rot=rot, pivot=(cx, cy, cz))
    b.art = (rgba, gm)
    return b


def gem(c, r, col=STAR, core=STAR_C, rot=(35, 45, 0)):
    return [cb(*c, 2 * r, 2 * r, 2 * r, col, rot=rot, glow=True), cb(*c, r, r, r, core, rot=rot, glow=True)]


SPARKLE = ["...O...", "...D...", "..cDc..", "ODDODDO", "..cDc..", "...D...", "...O..."]
SHIDE = ["PP.", ".pP", "Pp.", ".pP", "Pq.", ".q."]
LEAF_ART = [".jjV.", "jVVVv", ".Vvv."]
FLAME = ["..z..", "..zA.", ".zAA.", ".AzA.", "AzzaA", "AazaA", ".AaA."]


# ---------------------------------------------------------------- 祠の始めの武器

def shrine_staff():
    """祠の杖: 流木の柄。上で二股に分かれ、腕は外へ小さな渦を出しながら内へ巻いて、浮かぶ星の石を抱く"""
    B = [bx(-1, -9, -1, 1, 12.5, 1, WOOD),
         cb(0.3, 6, 0, 2.6, 1.6, 2.6, WOOD_D), cb(-0.3, -7, 0, 2.4, 1.2, 2.4, WOOD_D),
         cb(0, -10, 0, 3, 2, 3, PATINA), cb(0, -11.5, 0, 2, 1, 2, PATINA_L), cb(0, -12.5, 0, 1, 1, 1, GOLD),
         cb(0, -0.5, 0, 2.6, 6, 2.6, CORD, paint=cord_paint), cb(0, 3, 0, 3, 1, 3, GOLD), cb(0, -4, 0, 3, 1, 3, GOLD),
         cb(0, 10, 0, 3, 2, 3, PATINA), cb(0, 11.2, 0, 3.2, 0.5, 3.2, GOLD_L),
         cb(0, 13, 0, 2.6, 1.6, 2.6, WOOD_D)]
    arm = path([(0, 12.5), (-2.2, 15), (-3.1, 18.4), (-2.8, 21.4), (-3.6, 23.2), (-5.0, 23.3), (-5.6, 22.2)], 1.8, WOOD)
    arm += path([(-2.9, 16.4), (-4.9, 16.6), (-5.8, 15.4), (-5.1, 14.5)], 1.1, WOOD_L)
    arm += [plane(LEAF_ART, -3.4, 13.4, 0.4, 0.8, rot=(0, 25, 25)), plane(LEAF_ART, -2.4, 11.6, -0.4, 0.7, rot=(0, -30, -20)),
            cb(-2.4, 12.2, 1.0, 0.9, 0.9, 0.9, BERRY)]
    B += arm + mirror(arm)
    # 星の石の台と、浮かぶ石・光の粒・後ろのきらめきの絵
    B += [cb(0, 15.6, 0, 2, 1, 2, GOLD), cb(0, 16.3, 0, 3, 0.6, 3, GOLD_D)]
    c = (0, 19, 0)
    B += gem(c, 1.25)
    for dx, dy in ((0, 2.9), (0, -2.9), (2.9, 0), (-2.9, 0)):
        B.append(cb(c[0] + dx, c[1] + dy, 0, 0.8, 0.8, 0.8, STAR_C, glow=True))
    B.append(plane(SPARKLE, 0, 19, -0.05, 0.85, depth=0.1))
    B += halos((0, 19, 0), 4, 0.9)
    build("shrine_staff", B)


def halos(c, r, unit, warm=False):
    """光のまわりのにじみ: 十字に組んだ 2 枚の光の絵"""
    art = halo_art(r, warm)
    return [plane(art, *c, unit, depth=0.02), plane(art, *c, unit, depth=0.02, rot=(0, 90, 0))]


def shrine_sword():
    """祠の剣: 溝に空色の光が走る直刀。鍔は緑青の三日月で、真ん中の金の台に菱の星の石。柄頭は菱の輪と紙垂"""
    B = [bx(-2, 5, -0.5, 2, 19, 0.5, STEEL), bx(-2.6, 5, -0.3, -2, 19, 0.3, STEEL_L), bx(2, 5, -0.3, 2.6, 19, 0.3, STEEL_D),
         bx(-0.5, 6, -0.6, 0.5, 15.5, 0.6, GLOW_BLUE, glow=True), bx(-0.3, 15.5, -0.55, 0.3, 17.5, 0.55, STAR_C, glow=True),
         bx(-2.3, 19, -0.45, 2.3, 20.5, 0.45, STEEL), bx(-1.5, 20.5, -0.4, 1.5, 22, 0.4, STEEL_L),
         bx(-0.8, 22, -0.35, 0.8, 23.3, 0.35, STEEL_L), bx(-0.3, 23.3, -0.3, 0.3, 24.2, 0.3, WHITE),
         cb(0, 4.5, 0, 4.8, 1, 1.8, STEEL_D), cb(0, 3.9, 0, 4.2, 0.6, 2.6, PATINA_L),
         cb(0, 2.4, 0, 5, 2.8, 3, GOLD), cb(0, 0.8, 0, 3.4, 0.6, 2.6, GOLD_D),
         cb(0, 2.4, 0, 2.2, 2.2, 4.0, STAR, rot=(0, 0, 45), glow=True), cb(0, 2.4, 0, 1.1, 1.1, 4.3, STAR_C, rot=(0, 0, 45), glow=True)]
    wing = path([(2.4, 2.6), (4.8, 3.2), (6.8, 4.6), (7.9, 6.8)], 2.0, PATINA) + path([(7.9, 6.8), (8.1, 8.6)], 1.3, PATINA_L)
    wing += path([(2.6, 1.4), (4.8, 1.9), (6.4, 3.0)], 0.8, GOLD, d=2.4)
    B += wing + mirror(wing)
    B += [cb(0, -1.8, 0, 2.4, 5, 2.4, CORD, paint=cord_paint), cb(0, -4.8, 0, 3.4, 1.2, 3.4, GOLD), cb(0, -5.7, 0, 2, 0.6, 2, GOLD_D)]
    B += path([(0, -6), (1.4, -7.4), (0, -8.8), (-1.4, -7.4), (0, -6)], 0.8, PATINA)
    B.append(plane(SHIDE, 0.6, -11, 0, 0.8, rot=(0, 20, 0)))
    B += halos((0, 2.4, 0), 4, 0.8)
    build("shrine_sword", B)


def shrine_hammer():
    """祠の鎚: 緑青の頭に金の帯。前後に炉の窓 (橙の光る隙間)、上の煙出しから炎の絵。背 (-x) は三日月の角、打つ面 (+x) は鋼"""
    B = [bx(-1, -7, -1, 1, 11.5, 1, WOOD), cb(0.2, 6.5, 0, 2.5, 1.4, 2.5, WOOD_D),
         cb(0, -1, 0, 2.6, 6, 2.6, CORD, paint=cord_paint), cb(0, 2.5, 0, 3, 1, 3, GOLD), cb(0, -4.5, 0, 3, 1, 3, GOLD),
         cb(0, -7.6, 0, 3, 1.2, 3, GOLD), cb(0, -9, 0, 1.6, 1.6, 1.6, EMBER, rot=(45, 0, 45), glow=True),
         cb(0, 10.9, 0, 3.2, 1, 3.2, GOLD),
         cb(0, 14.6, 0, 6.4, 6.4, 5.4, PATINA), cb(-2.9, 14.6, 0, 1, 7.2, 6.2, GOLD), cb(2.9, 14.6, 0, 1, 7.2, 6.2, GOLD),
         cb(3.9, 14.6, 0, 1.4, 5.6, 4.8, STEEL), cb(4.75, 14.6, 0, 0.5, 4.8, 4, STEEL_L),
         cb(0, 18.3, 0, 2.6, 1.0, 2.6, PATINA_L), cb(0, 19.0, 0, 1.8, 0.5, 1.8, PATINA_D)]
    for z in (-1, 1):
        B += [cb(0, 14.6, z * 2.75, 4, 4, 0.2, DARK), cb(0, 15.6, z * 2.85, 3.2, 0.8, 0.2, EMBER, glow=True),
              cb(0, 13.6, z * 2.85, 3.2, 0.8, 0.2, EMBER, glow=True), cb(0, 14.6, z * 2.82, 2, 0.4, 0.2, EMBER_L, glow=True)]
    B.append(plane(FLAME, 0, 21.4, 0, 0.6, depth=0.15))
    B.append(plane(FLAME, 0, 21.4, 0, 0.6, depth=0.15, rot=(0, 90, 0)))
    H = [(-3.4, 16.8), (-5.0, 17.8), (-6.0, 19.4)]
    B += path(H, 1.4, PATINA_L) + path(flip_y(H, 14.6), 1.4, PATINA_L)
    B += halos((0, 21.4, 0), 4, 0.8, warm=True)
    build("shrine_hammer", B)


def shrine_dagger():
    """祠の短剣: 葉の形に反った刃 (刃は +x) に光る葉脈。鍔は金で、左右に渦。柄頭は菱の輪と赤い紐"""
    B = path([(0, 3), (0.4, 7)], 3.0, STEEL, d=0.8) + path([(0.4, 7), (1.4, 10.4)], 2.6, STEEL, d=0.75)
    B += path([(1.4, 10.4), (2.7, 12.9)], 1.8, STEEL_L, d=0.7) + path([(2.7, 12.9), (3.8, 14.6)], 1.0, WHITE, d=0.6)
    B += path([(-1.2, 3.2), (-0.8, 7), (0.3, 10.4)], 0.6, STEEL_D, d=0.9)
    B += path([(0.2, 4), (0.8, 8), (2.0, 11.6)], 0.45, GLOW_BLUE, d=0.95, glow=True)
    B += [cb(0, 2.3, 0, 4, 1.4, 2.2, GOLD), cb(0, 1.4, 0, 2.6, 0.5, 1.8, GOLD_D),
          cb(0, 2.3, 0, 1.4, 1.4, 2.8, STAR, rot=(0, 0, 45), glow=True)]
    curl = path([(2, 2.6), (3.4, 3.2), (4.0, 4.4), (3.4, 5.2), (2.6, 4.6)], 0.9, GOLD_L)
    B += curl + mirror(curl)
    B += [cb(0, -0.9, 0, 2, 4, 2, CORD, paint=cord_paint), cb(0, -3.4, 0, 2.6, 1.2, 2.6, GOLD)]
    B += path([(0, -4), (1, -5), (0, -6), (-1, -5), (0, -4)], 0.6, PATINA)
    B += path([(0, -6.2), (0.6, -7.6), (0.2, -9)], 0.5, CORD)
    B.append(cb(0.2, -9.4, 0, 0.8, 0.8, 0.8, (236, 108, 94)))
    B += halos((0, 2.3, 0), 3, 0.8)
    build("shrine_dagger", B)


def bell(cx, cy, z=0.0):
    """鈴: 上が細く下が広い金の段と、暗い口、光る舌"""
    return [cb(cx, cy - 0.4, z, 1.2, 0.8, 1.2, GOLD_D), cb(cx, cy - 1.5, z, 2.4, 1.4, 2.4, GOLD), cb(cx, cy - 2.7, z, 3.2, 1.1, 3.2, GOLD_L),
            cb(cx, cy - 3.27, z, 2.6, 0.06, 2.6, DARK), cb(cx, cy - 3.7, z, 0.8, 0.8, 0.8, STAR, glow=True)]


def shrine_bell_staff():
    """祠の鈴杖: 緑青の細い柄の先が波の渦に巻き、渦の真ん中に石。渦から紐で鈴が二つ下がる。紙垂も一枚"""
    B = [bx(-0.8, -4, -0.8, 0.8, 20.5, 0.8, PATINA), bx(-1, -8, -1, 1, -4, 1, WOOD),
         cb(0, -0.6, 0, 2.4, 6, 2.4, CORD, paint=cord_paint), cb(0, 2.8, 0, 2.8, 0.8, 2.8, GOLD), cb(0, -4, 0, 2.8, 0.8, 2.8, GOLD),
         cb(0, -8.6, 0, 2.6, 1.2, 2.6, PATINA), cb(0, -9.6, 0, 1.4, 0.8, 1.4, GOLD),
         cb(0, 12, 0, 2.2, 0.8, 2.2, GOLD), cb(0, 19.8, 0, 2.4, 1, 2.4, GOLD)]
    pts = [(0, 20), (0, 23), (1, 25.5), (3.5, 26.6), (6, 25.6), (7.1, 23), (6.2, 20.8), (3.9, 20.1), (2.1, 21.1), (1.9, 23.1), (3.4, 24.3),
           (4.9, 23.3), (4.5, 22)]
    B += path(pts[:8], 1.4, PATINA) + path(pts[7:], 1.1, PATINA_L)
    B += gem((3.5, 22.6, 0), 0.65)
    B += [seg((6.6, 20.6), (6.6, 17.6), 0.35, CORD)] + bell(6.6, 17.6)
    B += [seg((3.4, 19.8), (3.4, 15.4), 0.35, CORD)] + bell(3.4, 15.4)
    B.append(plane(SHIDE, -1.6, 16.6, 0, 0.8, rot=(0, 30, 0)))
    B += halos((3.5, 22.6, 0), 4, 0.8)
    build("shrine_bell_staff", B)


def shrine_bow():
    """祠の弓: 流木の弓 (弧は +x へ)。先は緑青で反り返り、握りに赤い紐と紙垂。弦は左で、引くと -x へ。引いたら矢をつがえる"""
    arc = [(5.5 * math.cos(t), 7.5 + 12.5 * math.sin(t)) for t in (math.radians(a) for a in range(-90, 91, 20))]
    for stage, pull in (("", 0), ("_pull0", 3), ("_pull1", 6), ("_pull2", 9)):
        B = path(arc, 1.8, WOOD)
        T = [(0, 20), (-1.2, 21.3), (-0.8, 22.6)]
        B += path(T, 1.2, PATINA_L) + path(flip_y(T, 7.5), 1.2, PATINA_L)
        B += [cb(5.5, 7.5, 0, 3, 6, 3, CORD, paint=cord_paint), cb(5.5, 11, 0, 3.2, 1, 3.2, GOLD), cb(5.5, 4, 0, 3.2, 1, 3.2, GOLD),
              cb(4.6, 12.6, 0, 2.4, 1.2, 2.6, PATINA), cb(4.6, 2.4, 0, 2.4, 1.2, 2.6, PATINA)]
        B.append(plane(SHIDE, 7.6, 4.2, 0, 0.8, rot=(0, -30, 0)))
        top, bot = (-1.0, 21.0), (-1.0, -6.0)
        nock = (-1.0 - pull, 7.5)
        B += [seg(top, nock, 0.3, STRING, ext=0.1), seg(nock, bot, 0.3, STRING, ext=0.1)]
        if pull > 0:
            n = nock[0]
            B += [seg((n + 0.3, 7.5), (11.5, 7.5), 0.6, WOOD_L, ext=0), cb(12.1, 7.5, 0, 1.4, 1.4, 0.5, STEEL, rot=(0, 0, 45)),
                  cb(n + 1.8, 8.3, 0, 2.2, 1.0, 0.2, PAPER), cb(n + 1.8, 6.7, 0, 2.2, 1.0, 0.2, CORD)]
        build("shrine_bow" + stage, B)


if __name__ == "__main__":
    shrine_staff()
    shrine_sword()
    shrine_hammer()
    shrine_dagger()
    shrine_bell_staff()
    shrine_bow()
