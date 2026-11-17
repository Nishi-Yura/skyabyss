# -*- coding: utf-8 -*-
"""試作 (client/look/GearLook、5 回目): 手で打ったドット絵を、部分ごとの厚みで立体に起こす。

2026-11-17 の参考 (Blockbench で作られた武器のタイムラプス・作品): 先に 2D のドット絵 (陰も縁も手で塗った絵) があり、
その画素を立方体として押し出す。部分ごとに厚みが違い (刃は薄く、宝石は厚く)、縁の画素は半画素薄くして丸みを出す。
側面は同じ色を暗く、上の面は明るく。光る画素は光る。黒い縁取りは使わない (縁はその素材の暗い色)。

SPRITES の文字の絵 (1 文字 = 1 画素) と PALETTES (色・厚み・光る) から、LowPolyMesh の json と絵を書く:
  lowpoly/look/a/<名>.json, textures/lowpoly/look/a/<名>.png (+ _glow.png)
技の床の絵 (DECALS) は textures/lowpoly/look/a/<名>_decal.png

  python -I tools/gen/pixel/pixel_extrude.py
"""
import json
import math
import random
from pathlib import Path

import numpy as np
from PIL import Image

ROOT = Path(__file__).resolve().parents[3]
ASSETS = ROOT / "src/main/resources/assets/skyabyss"
OUT = "look/a"

# 色 (r, g, b)、半分の厚み (画素)、光るか
SWORD = {
    "k": ((92, 100, 140), 0.5, False), "s": ((146, 158, 196), 0.5, False), "S": ((190, 202, 230), 0.5, False), "L": ((232, 240, 255), 0.5, False),
    "W": ((250, 252, 255), 0.5, False),
    "P": ((90, 220, 255), 0.75, True), "Q": ((236, 255, 255), 0.75, True),
    "v": ((64, 40, 120), 0.75, False), "V": ((112, 76, 190), 0.75, False), "X": ((168, 134, 238), 0.75, False),
    "g": ((150, 90, 30), 1.25, False), "G": ((228, 170, 62), 1.25, False), "Y": ((255, 228, 140), 1.25, False),
    "c": ((80, 190, 250), 1.75, True), "C": ((214, 250, 255), 1.75, True),
    "l": ((64, 34, 34), 1.0, False), "b": ((116, 66, 48), 1.0, False), "n": ((156, 96, 64), 1.0, False),
}
STAFF = {
    "k": ((120, 92, 78), 1.0, False), "b": ((196, 176, 144), 1.0, False), "B": ((238, 228, 200), 1.0, False),
    "w": ((70, 42, 34), 1.0, False), "W": ((118, 74, 48), 1.0, False), "h": ((162, 110, 72), 1.0, False),
    "g": ((150, 92, 32), 1.5, False), "G": ((226, 168, 62), 1.5, False), "Y": ((255, 226, 140), 1.5, False),
    "c": ((36, 104, 186), 2.25, True), "C": ((86, 186, 240), 2.25, True), "D": ((178, 238, 255), 2.25, True), "E": ((255, 255, 255), 2.25, True),
    "t": ((20, 78, 88), 1.5, False), "T": ((44, 140, 138), 1.5, False),
}
# 祠の品 (始めの武器) の色。光は h の段 (左上から光)。厚みは半分の画素
SHRINE = {
    # 鋼: 暗い縁 k → 明るい縁 E。f は刃の溝 (少し薄い)
    "k": ((60, 66, 92), 0.5, False), "s": ((112, 122, 156), 0.5, False), "S": ((164, 176, 206), 0.5, False), "L": ((212, 222, 242), 0.5, False),
    "E": ((250, 252, 255), 0.5, False), "f": ((88, 96, 128), 0.3, False),
    # 溝の光 (空色)
    "i": ((60, 160, 255), 0.3, True), "I": ((180, 236, 255), 0.3, True),
    # 流木
    "w": ((70, 48, 44), 1.0, False), "W": ((112, 80, 64), 1.0, False), "h": ((156, 118, 88), 1.0, False), "H": ((204, 172, 132), 1.0, False),
    # 青銅の緑青
    "t": ((30, 70, 76), 1.25, False), "T": ((48, 116, 110), 1.25, False), "u": ((86, 168, 148), 1.25, False), "U": ((166, 228, 202), 1.25, False),
    # 金 (磨かれた青銅)
    "g": ((126, 76, 34), 1.25, False), "G": ((198, 138, 62), 1.25, False), "y": ((240, 194, 106), 1.25, False), "Y": ((255, 238, 180), 1.25, False),
    # 星の石 (空色の光)
    "c": ((40, 120, 230), 1.5, True), "C": ((96, 196, 255), 1.5, True), "D": ((186, 242, 255), 1.5, True), "O": ((255, 255, 255), 1.5, True),
    # 炉の目 (橙の光)
    "a": ((190, 64, 24), 1.5, True), "A": ((255, 146, 44), 1.5, True), "z": ((255, 228, 140), 1.5, True),
    # 紙垂 (薄い紙の板)
    "q": ((188, 184, 176), 0.12, False), "p": ((232, 228, 218), 0.12, False), "P": ((255, 255, 250), 0.12, False),
    # 赤い紐と革
    "r": ((112, 26, 40), 0.75, False), "R": ((190, 50, 56), 0.75, False), "e": ((236, 108, 94), 0.5, False),
    "m": ((44, 30, 38), 1.0, False), "n": ((84, 52, 54), 1.0, False), "N": ((124, 84, 72), 1.0, False),
    # 宿り木の葉 (薄い板) と白い実
    "v": ((40, 92, 58), 0.25, False), "V": ((78, 150, 74), 0.25, False), "j": ((156, 206, 100), 0.25, False), "b": ((240, 246, 232), 0.75, False),
    # 弦 (細い)
    "x": ((236, 230, 212), 0.15, False),
}
PALS = {"shrine": SHRINE}
ART = Path(__file__).resolve().parent / "art"


def load_art(name):
    """art/<名>.txt: '# key: value' の頭書きと、1 文字 = 1 画素の絵"""
    head, rows = {}, []
    for line in (ART / (name + ".txt")).read_text(encoding="utf-8").splitlines():
        if line.startswith("#"):
            if ":" in line:
                k, v = line[1:].split(":", 1)
                head[k.strip()] = v.strip()
        elif line.strip():
            rows.append(line.rstrip())
    w = max(len(r) for r in rows)
    rows = [r.ljust(w, ".") for r in rows]
    pal = dict(PALS[head.get("pal", "shrine")])
    if "thick" in head:
        chars, d = head["thick"].split()
        for ch in chars:
            col, _, lit = pal[ch]
            pal[ch] = (col, float(d), lit)
    orow, ocol = (int(v) for v in head["origin"].split())
    return pal, rows, float(head.get("unit", "0.62")), orow, ocol, head


def line_cells(grid, r0, c0, r1, c1, ch):
    n = max(abs(r1 - r0), abs(c1 - c0))
    for i in range(n + 1):
        r = round(r0 + (r1 - r0) * i / max(1, n))
        c = round(c0 + (c1 - c0) * i / max(1, n))
        if grid[r][c] == ".":
            grid[r][c] = ch


def bow_stage(rows, head, pull):
    """弓: 弦を張り、pull (画素) だけ握りの高さで後ろ (-x) へ引く。引いたら矢をつがえる"""
    pad = 12
    grid = [list("." * pad + r + "." * 6) for r in rows]
    tr, tc, br, bc = (int(v) for v in head["string"].split())
    gr, gc = (int(v) for v in head["grip"].split())
    tc += pad
    bc += pad
    gc += pad
    nock = tc - pull
    line_cells(grid, tr, tc, gr, nock, "x")
    line_cells(grid, br, bc, gr, nock, "x")
    if pull > 0:
        tip = gc + 5
        for c in range(nock, tip):
            grid[gr][c] = "h" if c > nock + 3 else "W"
        grid[gr][tip] = "S"
        grid[gr][tip + 1] = "E"
        grid[gr - 1][tip] = "s"
        grid[gr + 1][tip] = "k"
        for k in range(3):
            grid[gr - 1][nock + 1 + k] = "P" if k < 2 else "p"
            grid[gr + 1][nock + 1 + k] = "R" if k < 2 else "r"
    return ["".join(r) for r in grid], pad


def preview(names, out):
    """絵の確認用: 正面の絵を 8 倍で横に並べる"""
    S = 8
    arts = []
    for n in names:
        pal, rows, unit, orow, ocol, head = load_art(n)
        arts.append((n, pal, rows))
    H = max(len(a[2]) for a in arts) * S + 16
    W = sum(len(a[2][0]) * S + 24 for a in arts)
    img = Image.new("RGBA", (W, H), (34, 38, 52, 255))
    x0 = 12
    for n, pal, rows in arts:
        for y, r in enumerate(rows):
            for x, ch in enumerate(r):
                if ch != ".":
                    col = pal[ch][0]
                    for yy in range(S):
                        for xx in range(S):
                            img.putpixel((x0 + x * S + xx, 8 + y * S + yy), (*col, 255))
        x0 += len(rows[0]) * S + 24
    img.save(out)


ROCK = {
    "d": ((84, 72, 80), 1.5, False), "r": ((132, 120, 120), 1.5, False), "h": ((182, 172, 160), 1.5, False), "H": ((222, 214, 198), 1.5, False),
    "O": ((255, 128, 36), 1.75, True), "Y": ((255, 214, 96), 1.75, True),
}


def blade_rows():
    """嵐の剣の刃: 先が細く、真ん中に稲妻の形の光る筋 (左へ右へ折れる)"""
    rows = ["........W........", ".......LWs.......", ".......LPs.......", "......LSPSs......"]
    zig = [0, 0, -1, -1, 0, 1, 1, 0, 0, -1, -1, 0, 1, 1, 0, 0, -1, 0]
    for i, o in enumerate(zig):
        cells = list("......LSSSs......")
        cells[8 + o] = "Q" if (i > 0 and zig[i - 1] != o) else "P"
        rows.append("".join(cells))
    return rows


SPRITES = {
    "storm_sword": (SWORD, blade_rows() + [
        ".X.............X.",
        ".VX....sSs....XV.",
        "..VX..kSSsk..XV..",
        "..vVXgGGGGGgXVv..",
        "...vVGYGcGYGVv...",
        "....vgGcCcGgv....",
        ".....ggGcGgg.....",
        "......gGGGg......",
        ".......nbl.......",
        ".......bll.......",
        ".......nbl.......",
        ".......bll.......",
        ".......nbl.......",
        "......gGYGg......",
        "......GcCcG......",
        "......gGGGg......",
        ".......ggg.......",
    ], 0.7, 31),
    "whale_staff": (STAFF, [
        "kB.........Bk",
        ".kB.......Bk.",
        "..kB.ccc.Bk..",
        "..kBcCDDcBk..",
        "..kbCDEDCbk..",
        "...kCCDCCk...",
        "...kcCCCck...",
        "....kccck....",
        ".....kbk.....",
        "..gG.gYg.Gg..",
        "...gGGGGGg...",
        "....gGGGg....",
        ".....hWw.....",
        ".....hWw.....",
        "....tTTTt....",
        "....TtTtT....",
        "....tTTTt....",
        ".....hWw.....",
        ".....hWw.....",
        ".....hWw.....",
        ".....hWw.....",
        ".....hWw.....",
        ".....hWw.....",
        ".....hWw.....",
        ".....hWw.....",
        ".....hWw.....",
        ".....hWw.....",
        "....tTTTt....",
        "....TtTtT....",
        "....tTTTt....",
        ".....hWw.....",
        ".....hWw.....",
        ".....hWw.....",
        ".....hWw.....",
        ".....hWw.....",
        "....gGGGg....",
        ".....gGg.....",
        "......g......",
    ], 0.85, 28),
    "prop_ss_titan_cleave_spike": (ROCK, [
        "....h....",
        "...hr....",
        "...hrd...",
        "...hrrd..",
        "..hhrrd..",
        "..hrOrd..",
        "..hrOrdd.",
        "..hrrOrd.",
        ".hhrrOrd.",
        ".hrrrOrrd",
        ".hrrrrOrd",
        ".hrrOrrrd",
        "hhrrOrrdd",
        "hrrrrYrrd",
        "hrrrrOrrd",
        "hrrrOrrdd",
        "hrrrrrrrd",
        "dhrrrrrdd",
        "ddrrrrddd",
        "ddddddddd",
    ], 1.0, 19),
}


# ---------------------------------------------------------------- 押し出し

def cells_of(rows, pal):
    H = len(rows)
    W = len(rows[0])
    for r in rows:
        assert len(r) == W, (len(r), W, r)
    depth = np.zeros((H, W))
    for y, r in enumerate(rows):
        for x, ch in enumerate(r):
            if ch != ".":
                depth[y, x] = pal[ch][1]
    # 縁の画素 (上下左右のどれかが空、または自分より薄い) は半画素薄く (厚みが 1 以上のとき)
    d2 = depth.copy()
    for y in range(H):
        for x in range(W):
            if depth[y, x] >= 1.0:
                edge = False
                for dy, dx in ((-1, 0), (1, 0), (0, -1), (0, 1)):
                    yy, xx = y + dy, x + dx
                    if not (0 <= yy < H and 0 <= xx < W) or depth[yy, xx] < depth[y, x]:
                        edge = True
                if edge:
                    d2[y, x] = depth[y, x] - 0.5
    return d2


def build_sprite(name, pal, rows, unit, origin_row, origin_col=None):
    H, W = len(rows), len(rows[0])
    depth = cells_of(rows, pal)
    cx = (W - 1) / 2.0 if origin_col is None else float(origin_col)
    # 絵: 正面 (そのまま) / 横 (暗く) / 上 (明るく) / 下 (もっと暗く) を縦に 4 つ
    img = np.zeros((H * 4, W, 4), np.uint8)
    glow = np.zeros((H * 4, W, 4), np.uint8)
    mul = (1.0, 0.82, 1.1, 0.62)
    for y, r in enumerate(rows):
        for x, ch in enumerate(r):
            if ch == ".":
                continue
            col, _, lit = pal[ch]
            for k, m in enumerate(mul):
                c = tuple(min(255, int(v * (1.0 if lit else m))) for v in col)
                img[k * H + y, x] = (*c, 255)
                if lit:
                    glow[k * H + y, x] = (*c, 255)
    V, UV, T = [], [], []

    def quad(pts, region, x, y):
        e = 0.02
        u0, u1 = (x + e) / W, (x + 1 - e) / W
        v0, v1 = (region * H + y + e) / (H * 4), (region * H + y + 1 - e) / (H * 4)
        n0 = len(V)
        for p in pts:
            V.append([round(p[0], 4), round(p[1], 4), round(p[2], 4)])
        UV.extend([[u0, v0], [u0, v1], [u1, v1], [u1, v0]])
        T.extend([[n0, n0 + 1, n0 + 2], [n0, n0 + 2, n0 + 3]])

    for y in range(H):
        for x in range(W):
            d = depth[y, x]
            if d <= 0:
                continue
            x0, x1 = (x - cx - 0.5) * unit, (x - cx + 0.5) * unit
            y1, y0 = (origin_row - y + 1) * unit, (origin_row - y) * unit
            z0, z1 = -d * unit, d * unit
            # 前 (-z) と後 (+z)
            quad([(x1, y1, z0), (x1, y0, z0), (x0, y0, z0), (x0, y1, z0)], 0, x, y)
            quad([(x0, y1, z1), (x0, y0, z1), (x1, y0, z1), (x1, y1, z1)], 0, x, y)
            # 横: 隣より厚い分だけ
            for dx, dy, face in ((1, 0, "east"), (-1, 0, "west"), (0, -1, "up"), (0, 1, "down")):
                xx, yy = x + dx, y + dy
                nd = depth[yy, xx] if (0 <= xx < W and 0 <= yy < H) else 0
                if nd >= d:
                    continue
                for (za, zb) in (((-d) * unit, (-nd) * unit), (nd * unit, d * unit)) if nd > 0 else (((-d) * unit, d * unit),):
                    if face == "east":
                        quad([(x1, y1, zb), (x1, y0, zb), (x1, y0, za), (x1, y1, za)], 1, x, y)
                    elif face == "west":
                        quad([(x0, y1, za), (x0, y0, za), (x0, y0, zb), (x0, y1, zb)], 1, x, y)
                    elif face == "up":
                        quad([(x0, y1, za), (x0, y1, zb), (x1, y1, zb), (x1, y1, za)], 2, x, y)
                    else:
                        quad([(x1, y0, za), (x1, y0, zb), (x0, y0, zb), (x0, y0, za)], 3, x, y)
    return V, UV, T, img, glow


def write_mesh(name, bones, img, glow):
    tex_name = OUT + "/" + name
    (ASSETS / "textures/lowpoly" / OUT).mkdir(parents=True, exist_ok=True)
    Image.fromarray(img, "RGBA").save(ASSETS / "textures/lowpoly" / (tex_name + ".png"))
    gp = ASSETS / "textures/lowpoly" / (tex_name + "_glow.png")
    if glow is not None and glow[..., 3].any():
        Image.fromarray(glow, "RGBA").save(gp)
    elif gp.exists():
        gp.unlink()
    doc = {"format_version": 1, "name": name, "units": "1/16 block", "uv": "normalized, origin top-left (v down)", "texture": tex_name,
           "texture_size": [int(img.shape[1]), int(img.shape[0])], "note": "Generated by tools/gen/pixel/pixel_extrude.py (GearLook pixel pilot)",
           "bones": {k: {"pivot": [0, 0, 0], "vertices": v[0], "uvs": v[1], "triangles": v[2], "triangle_count": len(v[2])} for k, v in bones.items()}}
    (ASSETS / "lowpoly" / OUT).mkdir(parents=True, exist_ok=True)
    (ASSETS / "lowpoly" / OUT / (name + ".json")).write_text(json.dumps(doc, separators=(",", ":")), encoding="utf-8")


def rot_y(p, deg, cx=0.0, cz=0.0):
    t = math.radians(deg)
    c, s = math.cos(t), math.sin(t)
    x, z = p[0] - cx, p[2] - cz
    return [x * c + z * s + cx, p[1], -x * s + z * c + cz]


def titan_cleave():
    """巨神断ち: 前の半円に岩の柱 (押し出したドット絵を十字に組む) を 7 本、外へ傾けて立てる"""
    pal, rows, unit, orow = SPRITES["prop_ss_titan_cleave_spike"]
    V0, UV0, T0, img, glow = build_sprite("spike", pal, rows, unit, orow)
    V, UV, T = [], [], []
    rnd = random.Random(7)
    for i in range(7):
        a = math.radians(200 + 140 * (i + 0.5) / 7)
        r = 12.0
        cx, cz = math.cos(a) * r, math.sin(a) * r
        s = 0.5 + 0.3 * (1 - abs(i - 3) / 3.0) + rnd.uniform(-0.05, 0.05)
        lean = 16 + rnd.uniform(-4, 4)
        face = -math.degrees(a) + 90
        for cross in (0, 90):
            n0 = len(V)
            for p in V0:
                q = rot_y([p[0] * s, p[1] * s, p[2] * s], cross)
                t = math.radians(lean)
                q = [q[0], q[1] * math.cos(t) - q[2] * math.sin(t), q[1] * math.sin(t) + q[2] * math.cos(t)]
                q = rot_y(q, face)
                V.append([q[0] + cx, q[1], q[2] + cz])
            UV.extend(UV0)
            T.extend([[a_ + n0, b_ + n0, c_ + n0] for a_, b_, c_ in T0])
    write_mesh("prop_ss_titan_cleave", {"root": (V, UV, T)}, img, glow)
    decal_cracks("prop_ss_titan_cleave", 64, 7, 11)


def decal_cracks(name, size, branches, seed):
    """床の絵: 真ん中から割れ目が枝分かれして走る。割れ目の芯は明るい黄、外へ橙、先は暗い赤。縁は焦げた暗い色"""
    rnd = random.Random(seed)
    img = np.zeros((size, size, 4), np.uint8)
    core = [(255, 246, 190), (255, 210, 90), (255, 140, 40), (200, 70, 24), (120, 30, 18)]
    c = size // 2
    lines = []

    def walk(x, y, ang, length, depth):
        pts = []
        for i in range(length):
            ang += rnd.uniform(-0.45, 0.45)
            x += math.cos(ang)
            y += math.sin(ang)
            pts.append((int(round(x)), int(round(y)), i / max(1, length - 1)))
            if depth < 2 and rnd.random() < 0.08:
                walk(x, y, ang + rnd.choice((-1, 1)) * rnd.uniform(0.5, 1.0), int(length * 0.5), depth + 1)
        lines.append((pts, depth))

    for b in range(branches):
        walk(c, c, 2 * math.pi * b / branches + rnd.uniform(-0.2, 0.2), int(size * 0.42), 0)
    # 焦げ (線の周り 1 画素)
    for pts, depth in lines:
        for x, y, t in pts:
            for dx in (-1, 0, 1):
                for dy in (-1, 0, 1):
                    xx, yy = x + dx, y + dy
                    if 0 <= xx < size and 0 <= yy < size and img[yy, xx, 3] == 0:
                        img[yy, xx] = (40, 20, 22, 210)
    for pts, depth in lines:
        for x, y, t in pts:
            if 0 <= x < size and 0 <= y < size:
                k = min(4, int(t * 4.2) + depth)
                img[y, x] = (*core[k], 255)
    # 真ん中の割れ口
    for dx in range(-2, 3):
        for dy in range(-2, 3):
            if abs(dx) + abs(dy) <= 2:
                img[c + dy, c + dx] = (*core[0 if abs(dx) + abs(dy) < 2 else 1], 255)
    Image.fromarray(img, "RGBA").save(ASSETS / "textures/lowpoly" / OUT / (name + "_decal.png"))


SHRINE_SET = ("shrine_sword", "shrine_hammer", "shrine_staff", "shrine_bow", "shrine_dagger", "shrine_bell_staff")


def build_art(name):
    pal, rows, unit, orow, ocol, head = load_art(name)
    if "string" in head:
        for stage, pull in (("", 0), ("_pull0", 3), ("_pull1", 6), ("_pull2", 9)):
            r2, pad = bow_stage(rows, head, pull)
            V, UV, T, img, glow = build_sprite(name, pal, r2, unit, orow, ocol + pad)
            write_mesh(name + stage, {"root": (V, UV, T)}, img, glow)
        return
    V, UV, T, img, glow = build_sprite(name, pal, rows, unit, orow, ocol)
    write_mesh(name, {"root": (V, UV, T)}, img, glow)
    print(name, len(T), "tris")


def main():
    import sys
    if len(sys.argv) > 1 and sys.argv[1] == "preview":
        preview(sys.argv[3:] or SHRINE_SET, sys.argv[2])
        return
    for name in SHRINE_SET:
        build_art(name)
    for name in ("storm_sword", "whale_staff"):
        pal, rows, unit, orow = SPRITES[name]
        V, UV, T, img, glow = build_sprite(name, pal, rows, unit, orow)
        write_mesh(name, {"root": (V, UV, T)}, img, glow)
        print(name, len(T), "tris")
    titan_cleave()
    print("titan_cleave ok")


if __name__ == "__main__":
    main()
