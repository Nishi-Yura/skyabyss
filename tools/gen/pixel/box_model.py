# -*- coding: utf-8 -*-
"""試作 (client/look/GearLook、6 回目): 箱を一つずつ置いて傾けて組む模型 (Blockbench と同じ作り方)。

2026-11-17 の参考 (箱の杖): 部品は太さも奥行きもある四角い棒。色は部品ごとに 1〜2 色の平らな面で、
陰は面の向き (上が明るく、横が暗い) で出す。光る部品はまわりににじむ (ここでは一回り大きい薄い光の箱を重ねる)。

単位は 1 = 1/16 ブロック (1 マス)。原点は握り。+y が先、+x が刃 (打つ面) の向き。
  python -I tools/gen/pixel/box_model.py
"""
import json
import math
import zlib
from pathlib import Path

import numpy as np
from PIL import Image

ROOT = Path(__file__).resolve().parents[3]
ASSETS = ROOT / "src/main/resources/assets/skyabyss"
OUT = "look/a"

# 面の向きごとの明るさ (上が明るく、横が暗い。マイクラの面の陰に合わせる)
SHADE = {"up": 1.12, "north": 0.94, "south": 0.94, "east": 0.84, "west": 0.84, "down": 0.62}


class Box:
    def __init__(self, frm, to, col, rot=(0, 0, 0), pivot=None, glow=False, halo=0.0, paint=None, shade=True):
        self.frm, self.to, self.col = frm, to, col
        self.rot = rot
        self.pivot = pivot if pivot is not None else tuple((a + b) / 2 for a, b in zip(frm, to))
        self.glow, self.halo, self.paint, self.shade = glow, halo, paint, shade
        self.parent = []  # 外側の回転 (群) を順に
        self.art = None  # 絵の板: (rgba h×w×4, 光る h×w)。前後の面に絵、横の面は透明


def rotate(p, rot, pivot):
    x, y, z = (p[i] - pivot[i] for i in range(3))
    ax, ay, az = (math.radians(a) for a in rot)
    # z → y → x の順 (Blockbench と同じ)
    x, y = x * math.cos(az) - y * math.sin(az), x * math.sin(az) + y * math.cos(az)
    x, z = x * math.cos(ay) + z * math.sin(ay), -x * math.sin(ay) + z * math.cos(ay)
    y, z = y * math.cos(ax) - z * math.sin(ax), y * math.sin(ax) + z * math.cos(ax)
    return (x + pivot[0], y + pivot[1], z + pivot[2])


def group(boxes, rot, pivot):
    for b in boxes:
        b.parent.append((rot, pivot))
    return boxes


def faces(b, inflate=0.0):
    (x0, y0, z0), (x1, y1, z1) = b.frm, b.to
    x0, y0, z0, x1, y1, z1 = x0 - inflate, y0 - inflate, z0 - inflate, x1 + inflate, y1 + inflate, z1 + inflate
    return {
        "north": ([(x1, y1, z0), (x1, y0, z0), (x0, y0, z0), (x0, y1, z0)], x1 - x0, y1 - y0),
        "south": ([(x0, y1, z1), (x0, y0, z1), (x1, y0, z1), (x1, y1, z1)], x1 - x0, y1 - y0),
        "east": ([(x1, y1, z1), (x1, y0, z1), (x1, y0, z0), (x1, y1, z0)], z1 - z0, y1 - y0),
        "west": ([(x0, y1, z0), (x0, y0, z0), (x0, y0, z1), (x0, y1, z1)], z1 - z0, y1 - y0),
        "up": ([(x0, y1, z0), (x0, y1, z1), (x1, y1, z1), (x1, y1, z0)], x1 - x0, z1 - z0),
        "down": ([(x0, y0, z1), (x0, y0, z0), (x1, y0, z0), (x1, y0, z1)], x1 - x0, z1 - z0),
    }


def face_pixels(b, face, w, h):
    """面の絵 (h × w × 4)。平らな色に、3 マス以上の面だけ左上を明るく・右下を暗く 1 マスの縁"""
    if b.paint is not None:
        px = b.paint(face, w, h)
        if px is not None:
            return px
    m = SHADE[face] if (b.shade and not b.glow) else 1.0
    base = np.array([min(255, v * m) for v in b.col], float)
    px = np.zeros((h, w, 4), float)
    px[..., :3] = base
    px[..., 3] = 255
    if b.glow:
        # 光る面: 縁はその色、内側は白へ寄せ、真ん中は白
        if w >= 3 and h >= 3:
            px[1:-1, 1:-1, :3] = base + (255 - base) * 0.55
            px[h // 2, w // 2, :3] = 255
        return px
    # ドット絵の面取り: 上と左の 1 マスは明るく (暖かい色へ)、下と右の 1 マスは暗く (冷たい色へ)
    light = np.minimum(255, base + (255 - base) * 0.38 + np.array([10, 6, -6]))
    dark = np.clip(base * 0.66 + np.array([-4, -2, 14]), 0, 255)
    if h >= 2:
        px[0, :, :3] = light
        px[-1, :, :3] = dark
    if w >= 2:
        px[:, 0, :3] = light if h < 2 else np.where(np.arange(h)[:, None] == h - 1, dark, light)
        px[:, -1, :3] = dark if h < 2 else np.where(np.arange(h)[:, None] == 0, (light + dark) / 2, dark)
    # 広い面には少しだけ斑 (同じ色の明暗を 1 マスずつ)
    if w >= 4 and h >= 4:
        rnd = np.random.default_rng(zlib.crc32(repr((b.frm, b.to, face)).encode()))
        spots = rnd.random((h - 2, w - 2)) < 0.12
        inner = px[1:-1, 1:-1, :3]
        inner[spots] = (base * 0.86 + dark * 0.14)
    return px


def build(name, boxes):
    # 面を並べる (行に詰める)
    quads = []  # (pts, pixels, glow)
    for b in boxes:
        layers = [(0.0, False)]
        if b.halo > 0:
            layers.append((b.halo, True))
        for inflate, halo in layers:
            for face, (pts, fw, fh) in faces(b, inflate).items():
                w, h = max(1, int(math.ceil(fw - 1e-6))), max(1, int(math.ceil(fh - 1e-6)))
                pts = [rotate(p, b.rot, b.pivot) for p in pts]
                for rot, pivot in b.parent:
                    pts = [rotate(p, rot, pivot) for p in pts]
                if b.art is not None:
                    rgba, gm = b.art
                    if face in ("north", "south"):
                        a, g = (rgba[:, ::-1], gm[:, ::-1]) if face == "north" else (rgba, gm)
                        quads.append((pts, a.astype(float), g))
                    else:
                        quads.append((pts, np.zeros((1, 1, 4), float), ""))
                    continue
                if halo:
                    px = np.zeros((h, w, 4), float)
                    px[..., :3] = np.array(b.col, float) * 0.32
                    px[..., 3] = 0  # 本体の絵では見えない
                    quads.append((pts, px, "halo"))
                else:
                    quads.append((pts, face_pixels(b, face, w, h), "glow" if b.glow else ""))
    AW = 128
    x = y = rowh = 0
    place = []
    for pts, px, kind in quads:
        h, w = px.shape[:2]
        if x + w + 1 > AW:
            x, y, rowh = 0, y + rowh + 1, 0
        place.append((x, y))
        x += w + 1
        rowh = max(rowh, h)
    AH = 1 << max(4, int(math.ceil(math.log2(y + rowh + 1))))
    img = np.zeros((AH, AW, 4), np.uint8)
    glow = np.zeros((AH, AW, 4), np.uint8)
    V, UV, T = [], [], []
    for (pts, px, kind), (ox, oy) in zip(quads, place):
        h, w = px.shape[:2]
        p8 = np.clip(px, 0, 255).astype(np.uint8)
        if isinstance(kind, str) and kind == "halo":
            g = p8.copy()
            g[..., 3] = 255
            glow[oy:oy + h, ox:ox + w] = g
        else:
            img[oy:oy + h, ox:ox + w] = p8
            if isinstance(kind, np.ndarray):
                g = p8.copy()
                g[~kind] = 0
                g[kind, 3] = 255
                glow[oy:oy + h, ox:ox + w] = g
            elif kind == "glow":
                glow[oy:oy + h, ox:ox + w] = p8
        e = 0.02
        u0, u1 = (ox + e) / AW, (ox + w - e) / AW
        v0, v1 = (oy + e) / AH, (oy + h - e) / AH
        n0 = len(V)
        for p in pts:
            V.append([round(p[0], 4), round(p[1], 4), round(p[2], 4)])
        UV.extend([[u0, v0], [u0, v1], [u1, v1], [u1, v0]])
        T.extend([[n0, n0 + 1, n0 + 2], [n0, n0 + 2, n0 + 3]])
    tex = OUT + "/" + name
    (ASSETS / "textures/lowpoly" / OUT).mkdir(parents=True, exist_ok=True)
    Image.fromarray(img, "RGBA").save(ASSETS / "textures/lowpoly" / (tex + ".png"))
    Image.fromarray(glow, "RGBA").save(ASSETS / "textures/lowpoly" / (tex + "_glow.png"))
    doc = {"format_version": 1, "name": name, "units": "1/16 block", "uv": "normalized, origin top-left (v down)", "texture": tex,
           "texture_size": [AW, AH], "note": "Generated by tools/gen/pixel/box_model.py (GearLook box models)",
           "bones": {"root": {"pivot": [0, 0, 0], "vertices": V, "uvs": UV, "triangles": T, "triangle_count": len(T)}}}
    (ASSETS / "lowpoly" / OUT / (name + ".json")).write_text(json.dumps(doc, separators=(",", ":")), encoding="utf-8")
    print(name, len(boxes), "boxes", len(T), "tris")


