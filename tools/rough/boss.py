"""岩鯨の心臓 (最終ボス) のラフ。A/B/C 各3段階 (100-66%, 66-33%, 33-0%) の
geo.json、テクスチャ (1単位=1画素)、発光マスク、3/4 描画、ボスバーの図。
seed 固定。実行: リポジトリ直下で python tools/rough/boss.py

構造: 各段階は、部品 (bone) ごとに立方体を並べた1つの geo.json。段階の違いは
部品の出し入れ (ある段階にだけある bone) と、同じ bone の形の変化で表す。
立方体は、部品のボクセルを 2D 貪欲法で直方体に分割したもの (重なりなし、面で接する)。
"""
from __future__ import annotations

import json
import math
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "tools/rough"))
from voxel import FACE_SHADE, Face, check_overlaps, font, render, sheet  # noqa: E402

OUT = ROOT / "docs/roughs/boss"
N = 32          # 格子の一辺 (単位 = テクスチャ1画素 = 1/16 ブロック)
AW = 512        # テクスチャ・アトラスの幅
rng = np.random.default_rng(20261015)
_g = np.arange(N) + 0.5 - 16.0
X, Y, Z = np.meshgrid(_g, _g, _g, indexing="ij")   # 格子の中心座標 (y が上)
TH = np.degrees(np.arctan2(Z, X)) % 360.0           # 水平角 (+x=0, +z=90 が正面)
NOISE = rng.uniform(0.9, 1.1, X.shape)

# 色 (RGB)
FLESH = (150, 52, 52)
FLESH_D = (98, 30, 36)
VEIN_D = (60, 14, 20)
BONE = (232, 223, 200)
BONE_D = (190, 176, 150)
CYAN = (127, 244, 255)
EMBER = (255, 110, 60)
AMBER = (255, 176, 70)
WHITE = (255, 244, 236)
STONE = (92, 108, 112)
STONE_L = (116, 134, 138)
STONE_D = (62, 74, 78)
ORB = (36, 64, 90)
HELIX = (79, 155, 176)
SPOKE = (43, 90, 110)
ROSE = (217, 182, 192)
RED_GLOW = (255, 90, 80)
DARK = (20, 30, 40)


# ---------------------------------------------------------------- 形の部品

def E(c, r):
    """楕円体 (中心 c, 半径 r)。r は数か 3 要素"""
    r = (r, r, r) if np.isscalar(r) else r
    return ((X - c[0]) / r[0]) ** 2 + ((Y - c[1]) / r[1]) ** 2 + ((Z - c[2]) / r[2]) ** 2 <= 1.0


def heart(c, R):
    """古典的な心形の陰影式 (頂点は下)。c は中心、R は大きさ"""
    a = (X - c[0]) / R
    v = (Y - c[1]) / R
    b = (Z - c[2]) / R
    f = (a * a + 2.25 * b * b + v * v - 1) ** 3 - a * a * v ** 3 - (9 / 80) * b * b * v ** 3
    return f <= 0


def seg(p, q, r):
    P = np.stack([X, Y, Z], -1)
    p = np.asarray(p, float)
    q = np.asarray(q, float)
    d = q - p
    t = np.clip(((P - p) @ d) / (d @ d), 0, 1)
    diff = P - (p + t[..., None] * d)
    return (diff * diff).sum(-1) <= r * r


def poly(pts, r):
    m = np.zeros(X.shape, bool)
    for p, q in zip(pts[:-1], pts[1:]):
        m |= seg(p, q, r)
    return m


def ring(y, r, a0, a1, rx, rz):
    """水平の輪のうち、角度 a0..a1 (度, +x=0, +z=90) の弧"""
    angs = np.radians(np.linspace(a0, a1, max(6, int((a1 - a0) / 5))))
    return poly([(rx * math.cos(a), y, rz * math.sin(a)) for a in angs], r)


def octa(c, r):
    return (np.abs(X - c[0]) + np.abs(Y - c[1]) + np.abs(Z - c[2])) <= r


def chain(p, q, n=6, amp=0.9, r=1.0):
    p = np.array(p, float)
    q = np.array(q, float)
    pts = []
    for i in range(n + 1):
        t = i / n
        pt = p + (q - p) * t
        if 0 < i < n:
            pt = pt + np.array([amp * (1 if i % 2 else -1), 0, 0])
        pts.append(tuple(pt))
    return poly(pts, r)


def dir3(theta, elev):
    th, el = math.radians(theta), math.radians(elev)
    return np.array([math.cos(el) * math.cos(th), math.sin(el), math.cos(el) * math.sin(th)])


# ---------------------------------------------------------------- 貪欲法で直方体に分割

def mesh(mask):
    """部品のボクセル (x, y, z) を、重ならない直方体 (x0, y0, z0, x1, y1, z1) の一覧にする"""
    boxes = []
    open_ = {}
    for y in range(N):
        layer = mask[:, y, :]
        used = np.zeros_like(layer)
        rects = []
        for x in range(N):
            for z in range(N):
                if layer[x, z] and not used[x, z]:
                    z1 = z
                    while z1 + 1 < N and layer[x, z1 + 1] and not used[x, z1 + 1]:
                        z1 += 1
                    x1 = x
                    while x1 + 1 < N and layer[x1 + 1, z:z1 + 1].all() and not used[x1 + 1, z:z1 + 1].any():
                        x1 += 1
                    used[x:x1 + 1, z:z1 + 1] = True
                    rects.append((x, x1 + 1, z, z1 + 1))
        new = {}
        for r in rects:
            new[r] = open_.pop(r, y)
        for r, y0 in open_.items():
            boxes.append((r[0], y0, r[2], r[1], y, r[3]))
        open_ = new
    for r, y0 in open_.items():
        boxes.append((r[0], y0, r[2], r[1], N, r[3]))
    return boxes


# ---------------------------------------------------------------- 色づけ

def paint(parts):
    """部品 (名前, マスク, 色, 発光) を順に取る。先の部品が優先し、後の部品は重なる所を削る。"""
    claimed = np.zeros(X.shape, bool)
    got = []
    for name, m, rgb, glow in parts:
        m = m & ~claimed
        claimed |= m
        got.append((name, m, rgb, glow))
    occ = claimed
    empty = ~np.pad(occ, 1)
    nx = empty[2:, 1:-1, 1:-1].astype(float) - empty[:-2, 1:-1, 1:-1]
    ny = empty[1:-1, 2:, 1:-1].astype(float) - empty[1:-1, :-2, 1:-1]
    nz = empty[1:-1, 1:-1, 2:].astype(float) - empty[1:-1, 1:-1, :-2]
    nexp = (empty[2:, 1:-1, 1:-1].astype(int) + empty[:-2, 1:-1, 1:-1] + empty[1:-1, 2:, 1:-1]
            + empty[1:-1, :-2, 1:-1] + empty[1:-1, 1:-1, 2:] + empty[1:-1, 1:-1, :-2])
    L = np.array([-0.45, 0.75, 0.5])
    L = L / np.linalg.norm(L)
    nv = np.stack([nx, ny, nz], -1)
    nl = np.linalg.norm(nv, axis=-1)
    lamb = np.where(nl > 0, (nv @ L) / np.maximum(nl, 1e-9), 0.0)
    lum = 0.6 + 0.45 * np.clip(lamb, 0, 1)
    shade = lum * NOISE * np.where((nexp >= 2) & occ, 0.7, 1.0)   # 角と縁は暗く
    col = np.zeros((N, N, N, 4), np.uint8)
    glow = np.zeros((N, N, N), bool)
    for name, m, rgb, g in got:
        if not m.any():
            continue
        base = np.array(rgb, float)
        if g:
            c = np.outer(NOISE[m] * 0.92, base)
            glow[m] = True
        else:
            c = np.outer(shade[m], base)
        col[m, :3] = np.clip(c, 0, 255)
        col[m, 3] = 255
    return got, col, glow, occ


def components(occ):
    seen = np.zeros_like(occ)
    count = 0
    for start in zip(*np.nonzero(occ)):
        if seen[start]:
            continue
        count += 1
        stack = [start]
        seen[start] = True
        while stack:
            x, y, z = stack.pop()
            for dx, dy, dz in ((1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0), (0, 0, 1), (0, 0, -1)):
                a, b, c = x + dx, y + dy, z + dz
                if 0 <= a < N and 0 <= b < N and 0 <= c < N and occ[a, b, c] and not seen[a, b, c]:
                    seen[a, b, c] = True
                    stack.append((a, b, c))
    return count


# ---------------------------------------------------------------- 面 (UV の向きを、描画と一致させる)

def face_specs(box):
    x0, y0, z0, x1, y1, z1 = box
    w, h, d = x1 - x0, y1 - y0, z1 - z0
    # 名前: (幅 W, 高さ H, 原点 o, 横 eu, 縦 ev, 外向き法線)。テクセル行0は ev の先端 (上)
    return {
        "east": (d, h, (x1, y0, z1), (0, 0, -d), (0, h, 0), (1, 0, 0)),
        "west": (d, h, (x0, y0, z0), (0, 0, d), (0, h, 0), (-1, 0, 0)),
        "up": (w, d, (x0, y1, z1), (w, 0, 0), (0, 0, -d), (0, 1, 0)),
        "down": (w, d, (x0, y0, z0), (w, 0, 0), (0, 0, d), (0, -1, 0)),
        "south": (w, h, (x0, y0, z1), (w, 0, 0), (0, h, 0), (0, 0, 1)),
        "north": (w, h, (x1, y0, z0), (-w, 0, 0), (0, h, 0), (0, 0, -1)),
    }


def voxel_of_texel(face, box, rr, cc):
    """テクセル (行 rr, 列 cc) に対応する格子の立方 (x, y, z)"""
    x0, y0, z0, x1, y1, z1 = box
    z = np.zeros_like(rr)
    if face == "east":
        return x1 - 1 + z, y1 - 1 - rr, z1 - 1 - cc
    if face == "west":
        return x0 + z, y1 - 1 - rr, z0 + cc
    if face == "up":
        return x0 + cc, y1 - 1 + z, z0 + rr
    if face == "down":
        return x0 + cc, y0 + z, z1 - 1 - rr
    if face == "south":
        return x0 + cc, y1 - 1 - rr, z1 - 1 + z
    return x1 - 1 - cc, y1 - 1 - rr, z0 + z  # north


def build(parts, label):
    got, col, glow, occ = paint(parts)
    pad = np.pad(occ, 1)
    recs = []
    bones = []
    for name, m, rgb, g in got:
        if not m.any():
            continue
        cubes = []
        for box in mesh(m):
            x0, y0, z0, x1, y1, z1 = box
            cube = {"origin": [x0 - 16, y0, z0 - 16], "size": [x1 - x0, y1 - y0, z1 - z0], "uv": {}}
            for face, (W, H, o, eu, ev, n) in face_specs(box).items():
                rr, cc = np.meshgrid(np.arange(H), np.arange(W), indexing="ij")
                xi, yi, zi = voxel_of_texel(face, box, rr, cc)
                exposed = ~pad[xi + 1 + n[0], yi + 1 + n[1], zi + 1 + n[2]]
                if not exposed.any():
                    continue   # 内側の面は作らない
                recs.append({"W": W, "H": H, "o": o, "eu": eu, "ev": ev, "face": face,
                             "tex": col[xi, yi, zi], "gl": glow[xi, yi, zi], "cube": cube})
            cubes.append(cube)
        bones.append({"name": name, "pivot": [0, 0, 0], "cubes": cubes})
    # 幅の大きい面から、棚詰めでアトラスに並べる
    recs.sort(key=lambda r: -r["H"])
    x = y = shelf = 0
    for r in recs:
        if x + r["W"] > AW:
            x, y, shelf = 0, y + shelf, 0
        r["u"], r["v"] = x, y
        x += r["W"]
        shelf = max(shelf, r["H"])
    AH = y + shelf
    tex = np.zeros((AH, AW, 4), np.uint8)
    gtex = np.zeros((AH, AW, 4), np.uint8)
    faces = []
    for r in recs:
        u, v, W, H = r["u"], r["v"], r["W"], r["H"]
        tex[v:v + H, u:u + W] = r["tex"]
        gtex[v:v + H, u:u + W, :3] = r["tex"][..., :3] * r["gl"][..., None]
        gtex[v:v + H, u:u + W, 3] = np.where(r["gl"], 255, 0)
        r["cube"]["uv"][r["face"]] = {"uv": [u, v], "uv_size": [W, H]}
        faces.append(Face(np.array(r["o"], float), np.array(r["eu"], float), np.array(r["ev"], float),
                          tex[v:v + H, u:u + W], gtex[v:v + H, u:u + W, 3] > 0, FACE_SHADE[r["face"]]))
    geo = {"format_version": "1.12.0", "minecraft:geometry": [{
        "description": {"identifier": f"geometry.skyabyss.stone_whale_heart.{label}",
                        "texture_width": AW, "texture_height": AH,
                        "visible_bounds_width": 4, "visible_bounds_height": 4,
                        "visible_bounds_offset": [0, 1.5, 0]},
        "bones": bones}]}
    stats = {"bones": len(bones), "boxes": sum(len(b["cubes"]) for b in bones),
             "faces": len(recs), "atlas": [AW, AH], "overlaps": check_overlaps(geo),
             "components": components(occ)}
    return geo, faces, tex, gtex, stats


# ---------------------------------------------------------------- 三案の段階

def heart_veins(hm, freq, th):
    return hm & (np.abs(np.sin(1.1 * X + 0.7 * Y) * np.cos(0.9 * Z)) < th)


def parts_a(ph):
    """A: 肋骨の心臓 (王道)。肋骨の籠が心臓を囲む → 籠が割れ結晶が生える → 心核が露出"""
    spine = poly([(0, -10, -10), (0, 10, -10)], 1.2)
    if ph == 1:
        hm = heart((0, 0, 0), 7.0)
        veins = heart_veins(hm, 1, 0.12)
        cage = (ring(-6, 1.0, 130, 410, 11, 10) | ring(-2, 1.0, 130, 410, 11, 10)
                | ring(2, 1.0, 130, 410, 11, 10) | ring(6, 1.0, 130, 410, 11, 10))
        tendon = poly([(0, 0, -1), (0, 0, -9.5)], 0.9)
        return [("heart_veins", veins, VEIN_D, False), ("heart", hm & ~veins, FLESH, False),
                ("tendon", tendon, FLESH_D, False), ("spine", spine, BONE_D, False),
                ("ribs", cage, BONE, False)]
    if ph == 2:
        hm = heart((0, 0, 0), 8.2)
        veins = heart_veins(hm, 1, 0.10)
        cage = (ring(-6, 1.0, 130, 410, 11, 10) | ring(6, 1.0, 200, 410, 11, 10))
        tips = [(-6, 130), (-6, 50), (6, 200), (6, 50)]
        xt = np.zeros(X.shape, bool)
        for y, a in tips:
            a = math.radians(a)
            c = (11 * math.cos(a) * 1.15, y, 10 * math.sin(a) * 1.15)
            xt |= octa(c, 2.0)
        tendon = poly([(0, 0, -1), (0, 0, -9.5)], 0.9)
        return [("heart_veins", veins, CYAN, True), ("heart", hm & ~veins, FLESH, False),
                ("tendon", tendon, FLESH_D, False), ("spine", spine, BONE_D, False),
                ("ribs", cage, BONE, False), ("crystals", xt, CYAN, True)]
    # 第3段階: 心核が露出し、白赤に変わる。鎖が垂れる
    cav = E((0, 1, 4.6), 2.9)
    hm = heart((0, 0, 0), 9.5) & ~cav
    veins = heart_veins(hm, 1, 0.07)
    core = E((0, 1, 4.4), 2.3)
    stalk = poly([(0, 1, 4.4), (0, 1, 0)], 0.9)
    tendon = poly([(0, -2, -2), (0, -2, -9.5)], 0.9)
    spine3 = poly([(0, -10, -10), (0, -3, -10)], 1.2)
    ribs = ring(-6, 1.0, 200, 340, 11, 10)
    chains = (chain((0, -6, 0), (-6, -15, 0)) | chain((0, -6, 0), (5, -15, 0))
              | chain((0, -6, 0), (0, -15, 4)))
    return [("core", core, WHITE, True), ("stalk", stalk, BONE, False),
            ("heart_veins", veins, WHITE, True), ("heart", hm & ~veins & ~core, (120, 26, 34), False),
            ("tendon", tendon, FLESH_D, False), ("spine", spine3, BONE_D, False),
            ("ribs", ribs, BONE, False), ("chains", chains, BONE_D, False)]


def parts_b(ph):
    """B: 石の殻 (重厚)。八枚の殻が心臓を閉じ込める → 殻が割れ、心臓がのぞく → 殻が崩れ、白赤の心核が露出"""
    shell = E((0, 0, 0), 12) & ~E((0, 0, 0), 10)
    seam = shell & (np.minimum(TH % 45, 45 - TH % 45) < 5)
    plates = [shell & (TH >= 45 * i) & (TH < 45 * (i + 1)) & ~seam for i in range(8)]
    tend = np.zeros(X.shape, bool)
    for th, el in [(0, 0.2), (60, 0.1), (120, -0.3), (180, 0.2), (240, 0.0), (300, -0.2)]:
        tend |= poly([(0, 0, 0), tuple(dir3(th, el) * 10.6)], 1.0)
    if ph == 1:
        hm = heart((0, 0, 0), 5.0)
        out = [("heart", hm, FLESH_D, False), ("tendrils", tend, FLESH, False),
               ("seams", seam, EMBER, True)]
        for i in range(8):
            out.append((f"plate_{i}", plates[i], STONE if i % 2 == 0 else STONE_L, False))
        return out
    if ph == 2:
        hm = heart((0, 0, 0), 6.5)
        veins = heart_veins(hm, 1, 0.14)
        crack = (plates[0] | plates[1]) & (np.abs(np.sin(2.1 * X + 0.9 * Y)) < 0.10)
        spikes = np.zeros(X.shape, bool)
        for th, ph_deg in [(20, 40), (70, 50), (320, 40), (290, 55)]:
            c = dir3(th, 90 - ph_deg) * 13.0
            spikes |= octa(tuple(c * 0.97), 2.2)
        out = [("heart_veins", veins, AMBER, True), ("heart", hm & ~veins, FLESH, False),
               ("tendrils", tend, FLESH, False), ("cracks", crack, EMBER, True)]
        for i in (0, 1, 2, 3, 6, 7):
            p = plates[i] & ~crack
            out.append((f"plate_{i}", p, STONE if i % 2 == 0 else STONE_L, False))
        out.append(("spikes", spikes, BONE, False))
        return out
    # 第3段階: 殻の下半分が受け皿になり、上の二枚が鎖で心臓から吊られる。心核は白赤
    hm = heart((0, 0, 0), 8.5) & ~E((0, 0, 4), 3.5)
    core = E((0, 0, 4), 2.4)
    veins = heart_veins(hm, 1, 0.07)
    bowl = shell & (Y < -3)
    shard = shell & (Y > 4) & (TH >= 0) & (TH < 120)
    chains = chain((8, 6, 4), (4, 3, 2)) | chain((0, 6, 10), (0, 2, 4))
    stalk = poly([(0, 0, 4), (0, 0, 0.5)], 0.9)
    return [("core", core, WHITE, True), ("stalk", stalk, BONE, False),
            ("heart_veins", veins, (255, 200, 190), True), ("heart", hm & ~veins & ~core, (216, 67, 58), False),
            ("bowl", bowl, STONE_D, False), ("shard", shard, STONE, False), ("chains", chains, BONE_D, False)]


def parts_c(ph):
    """C: 花の神秘 (異形)。らせんの殻が球を包む → 殻が三つに裂け、水色の核と結晶が出る → 六本の腕が伸び、赤白の眼が開く"""
    orb = E((0, 0, 0), 6.5)
    if ph == 1:
        lines = orb & (np.abs(np.sin(3 * np.arctan2(Z, X) + 0.5 * Y)) < 0.14)
        t = np.linspace(0, 3 * math.pi, 90)
        helix = poly([(9 * math.cos(s), -10 + 20 * s / (3 * math.pi), 9 * math.sin(s)) for s in t], 1.0)
        spokes = np.zeros(X.shape, bool)
        for tk in (1.0, 3.1, 5.2):
            yk = -10 + 20 * tk / (3 * math.pi)
            spokes |= poly([(0, yk, 0), (9 * math.cos(tk), yk, 9 * math.sin(tk))], 0.9)
        return [("orb_lines", lines, DARK, False), ("orb", orb & ~lines, ORB, False),
                ("spokes", spokes, SPOKE, False), ("helix", helix, HELIX, False)]
    if ph == 2:
        shell = E((0, 0, 0), 6.5) & ~E((0, 0, 0), 4.5)
        gapd = np.full(X.shape, 999.0)
        for g in (0, 120, 240):
            gapd = np.minimum(gapd, np.abs(((TH - g + 180) % 360) - 180))
        petals = shell & (gapd >= 12)
        core = E((0, 0, 0), 3.6)
        spokes = np.zeros(X.shape, bool)
        for a in (60, 180, 300):
            r_ = math.radians(a)
            spokes |= poly([(0, 0, 0), (5.0 * math.cos(r_), 0, 5.0 * math.sin(r_))], 0.9)
        xt = np.zeros(X.shape, bool)
        for g in (0, 120, 240):
            r_ = math.radians(g)
            xt |= poly([(0, 0, 0), (9.6 * math.cos(r_), 0, 9.6 * math.sin(r_))], 0.9)
            xt |= octa((9.6 * math.cos(r_) * 1.1, 0, 9.6 * math.sin(r_) * 1.1), 2.0)
        return [("core", core, CYAN, True), ("spokes", spokes, SPOKE, False),
                ("petals", petals, ORB, False), ("crystals", xt, CYAN, True)]
    # 第3段階: 球は赤白の核と、赤道の帯だけになり、六本の腕が伸びる。核に眼が開く
    pupil = E((0, 0, 4.3), 1.7)
    core = E((0, 0, 0), 4.5)
    band = E((0, 0, 0), 6.5) & ~E((0, 0, 0), 4.5) & (np.abs(Y) < 1.6)
    arms = np.zeros(X.shape, bool)
    tips = np.zeros(X.shape, bool)
    for k in range(6):
        th0 = 60 * k
        pts = []
        for s in np.linspace(0, 1, 12):
            R = 6.0 + 7.5 * s
            ang = math.radians(th0 + 70 * s)
            y = 3.5 * math.sin(math.pi * s) * (1 if k % 2 == 0 else -1)
            pts.append((R * math.cos(ang), y, R * math.sin(ang)))
        arms |= poly(pts, 1.0)
        tips |= octa(pts[-1], 1.6)
    return [("pupil", pupil, WHITE, True), ("core", core, RED_GLOW, True), ("band", band, SPOKE, False),
            ("arms", arms, ROSE, False), ("tips", tips, (255, 160, 170), True)]


OPTS = {"a": parts_a, "b": parts_b, "c": parts_c}
NAMES = {"a": "A 肋骨の心臓 (王道)", "b": "B 石の殻 (重厚)", "c": "C 花の神秘 (異形)"}
RANGES = {1: "100-66%", 2: "66-33%", 3: "33-0%"}
PCT = {1: 0.82, 2: 0.50, 3: 0.18}


# ---------------------------------------------------------------- ボスバー

BW, BH = 640, 132
BX0, BY0, BWID, BHGT = 84, 58, 480, 15     # 182x5 を 3倍にした帯


def lerp(a, b, t):
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(3))


def bar_fill(d, opt, ph, x0, x1, y0, y1):
    w = max(1, x1 - x0)
    for x in range(x0, x1):
        t = (x - x0) / w
        if (opt, ph) == ("a", 1):
            col = lerp((120, 22, 30), (220, 60, 54), t)
        elif (opt, ph) == ("a", 2):
            col = lerp((150, 30, 70), (205, 44, 62), t)
        elif (opt, ph) == ("a", 3):
            col = (255, 226, 206) if (x // 6) % 2 == 0 else (226, 40, 40)
        elif (opt, ph) == ("b", 1):
            col = lerp((52, 104, 112), (104, 164, 172), t)
        elif (opt, ph) == ("b", 2):
            col = lerp((190, 96, 26), (255, 176, 70), t)
        elif (opt, ph) == ("b", 3):
            col = (230, 40, 24) if t < 0.9 else (255, 230, 210)
        elif (opt, ph) == ("c", 1):
            col = lerp((18, 62, 86), (84, 206, 224), t)
        elif (opt, ph) == ("c", 2):
            col = lerp((70, 200, 230), (170, 250, 255), t)
        else:
            col = lerp((214, 64, 110), (255, 236, 230), t)
        d.line((x, y0, x, y1), fill=col)
    if (opt, ph) in (("a", 2), ("c", 2)):
        d.line((x0, y0, x1, y0), fill=CYAN, width=2)


def bar_frame(d, opt, ph, x0, x1, y0, y1):
    if opt == "a":   # 肋骨の枠
        col = (232, 222, 200) if ph < 3 else (255, 120, 110)
        d.rounded_rectangle((x0 - 12, y0 - 10, x1 + 12, y1 + 10), radius=7, outline=col, width=4)
        for k in range(1, 10):
            x = x0 + k * (x1 - x0) / 10
            if ph == 1:
                d.line((x, y0 - 16, x, y0 - 10), fill=col, width=3)
            elif ph == 2:
                d.polygon([(x - 4, y0 - 10), (x + 4, y0 - 10), (x, y0 - 19)], fill=CYAN)
            else:
                d.ellipse((x - 3, y0 - 19, x + 3, y0 - 13), fill=(255, 90, 80))
        if ph == 2:
            for xe in (x0 - 14, x1 + 14):
                d.polygon([(xe - 6, y0 + 7), (xe, y0 - 14), (xe + 6, y0 + 7)], fill=CYAN)
        if ph == 3:
            d.rounded_rectangle((x0 - 16, y0 - 14, x1 + 16, y1 + 14), radius=9, outline=(255, 60, 60), width=2)
    elif opt == "b":   # 石の枠 (ボルト止め)
        d.rectangle((x0 - 14, y0 - 12, x1 + 14, y1 + 12), fill=(44, 50, 52), outline=(20, 24, 26), width=3)
        d.line((x0 - 12, y0 - 10, x1 + 12, y0 - 10), fill=(104, 118, 122), width=2)
        for xr, yr in ((x0 - 6, y0 - 6), (x1 + 6, y0 - 6), (x0 - 6, y1 + 6), (x1 + 6, y1 + 6)):
            d.ellipse((xr - 3, yr - 3, xr + 3, yr + 3), fill=(150, 160, 160))
        if ph == 2:
            for xc in (x0 + 0.25 * (x1 - x0), x0 + 0.7 * (x1 - x0)):
                d.line([(xc, y0 - 14), (xc + 5, y0 - 6), (xc - 3, y0 + 1), (xc + 4, y1 + 14)],
                       fill=(255, 150, 60), width=2)
        if ph == 3:   # 枠が割れ、中央が離れる
            mid = (x0 + x1) / 2
            d.rectangle((mid - 9, y0 - 16, mid + 9, y1 + 16), fill=(20, 19, 28))
            d.rectangle((x0 - 14, y0 - 12, x1 + 14, y1 + 12), outline=(255, 90, 40), width=2)
    else:   # 花の枠 (細い線と渦)
        col = (90, 200, 210) if ph < 3 else (255, 150, 170)
        d.rounded_rectangle((x0 - 12, y0 - 10, x1 + 12, y1 + 10), radius=12, outline=col, width=2)
        for xe, flip in ((x0 - 22, 1), (x1 + 22, -1)):
            d.arc((xe - 12, y0 - 14, xe + 12, y1 + 14), 90 if flip > 0 else 270, 270 if flip > 0 else 90,
                  fill=col, width=2)
        if ph == 2:
            for xe in (x0 - 24, x1 + 24):
                d.ellipse((xe - 9, y0 - 11, xe + 9, y1 + 11), outline=CYAN, width=2)
        if ph == 3:
            for k in range(6):
                x = x0 + 30 + k * 85
                d.polygon([(x - 6, y0 - 10), (x + 6, y0 - 10), (x, y0 - 22)], fill=(255, 200, 210))


def bar_icon(d, opt, ph, cx, cy):
    if opt == "a":
        col = (255, 226, 206) if ph == 3 else (200, 60, 60)
        d.ellipse((cx - 22, cy - 18, cx, cy + 4), fill=col)
        d.ellipse((cx, cy - 18, cx + 22, cy + 4), fill=col)
        d.polygon([(cx - 22, cy - 4), (cx + 22, cy - 4), (cx, cy + 24)], fill=col)
        d.line((cx - 6, cy - 8, cx, cy - 2, cx + 6, cy - 10), fill=(60, 14, 20), width=2)
    elif opt == "b":
        d.ellipse((cx - 22, cy - 18, cx + 22, cy + 22), fill=(92, 108, 112), outline=(20, 24, 26), width=2)
        d.line((cx - 22, cy + 2, cx + 22, cy + 2), fill=(40, 46, 48), width=2)
        d.line((cx, cy - 18, cx, cy + 22), fill=(40, 46, 48), width=2)
        d.ellipse((cx - 8, cy - 6, cx + 8, cy + 10), fill=(255, 90, 60) if ph < 3 else (255, 240, 230))
    else:
        d.ellipse((cx - 16, cy - 16, cx + 16, cy + 16), fill=ORB if ph < 3 else (255, 90, 80))
        for a in range(0, 360, 60):
            r = math.radians(a)
            d.line((cx, cy, cx + 24 * math.cos(r), cy + 24 * math.sin(r)), fill=(120, 200, 220), width=3)
        d.ellipse((cx - 6, cy - 6, cx + 6, cy + 6), fill=CYAN if ph < 3 else (255, 245, 240))


def bar_img(opt, ph):
    pct = PCT[ph]
    img = Image.new("RGB", (BW, BH), (20, 19, 28))
    d = ImageDraw.Draw(img)
    x0, y0 = BX0, BY0
    x1, y1 = x0 + BWID, y0 + BHGT
    d.rectangle((x0 - 60, y0 - 40, x1 + 40, y1 + 34), fill=(30, 28, 40))   # 下地
    bar_frame(d, opt, ph, x0, x1, y0, y1)
    # 体力の帯 (左から減る)。残りの割合だけ塗る
    fill_end = x0 + int(BWID * pct)
    d.rectangle((x0, y0, x1, y1), fill=(14, 12, 18))
    bar_fill(d, opt, ph, x0, fill_end, y0, y1)
    if opt == "a":
        for k in range(1, 10):
            x = x0 + k * BWID / 10
            d.line((x, y0, x, y1), fill=(40, 20, 24), width=2)
    # 段階の境目 (66%, 33%) を、帯の下の三角で示す
    for frac in (0.66, 0.33):
        x = x0 + BWID * frac
        d.polygon([(x - 6, y1 + 14), (x + 6, y1 + 14), (x, y1 + 6)], fill=(240, 240, 240))
    bar_icon(d, opt, ph, 52, 40)
    f_big, f_small = font(20), font(15)
    d.text((x0, 8), f"{NAMES[opt][2:]}  第{ph}段階", font=f_big, fill=(235, 235, 245))
    d.text((x1 - 40, 10), f"{pct * 100:.0f}%", font=f_big, fill=(235, 235, 245))
    d.text((x0 - 2, y1 + 22), "66%", font=f_small, fill=(170, 170, 190))
    d.text((x0 + BWID * 0.33 - 12, y1 + 22), "33%", font=f_small, fill=(170, 170, 190))
    d.text((x1 - 90, y1 + 22), RANGES[ph], font=f_small, fill=(170, 170, 190))
    return img


# ---------------------------------------------------------------- 実行

def main():
    OUT.mkdir(parents=True, exist_ok=True)
    renders = {}
    for opt, fn in OPTS.items():
        odir = OUT / opt
        odir.mkdir(parents=True, exist_ok=True)
        for ph in (1, 2, 3):
            geo, faces, tex, gtex, stats = build(fn(ph), f"{opt}.p{ph}")
            name = f"phase{ph}"
            (odir / f"{name}.geo.json").write_text(json.dumps(geo, ensure_ascii=False, indent=1), encoding="utf-8")
            Image.fromarray(tex, "RGBA").save(odir / f"{name}.tex.png")
            Image.fromarray(gtex, "RGBA").save(odir / f"{name}.glow.png")
            img = render(faces, yaw=35, pitch=28, size=(640, 640))
            img.save(odir / f"{name}.render.png")
            bar_img(opt, ph).save(odir / f"{name}.bar.png")
            renders[(opt, ph)] = (img.resize((440, 440), Image.LANCZOS), bar_img(opt, ph))
            print(opt, ph, json.dumps(stats, ensure_ascii=False))

    cols = []
    for opt in OPTS:
        ims = [renders[(opt, ph)][0] for ph in (1, 2, 3)] + [renders[(opt, ph)][1] for ph in (1, 2, 3)]
        cols.append((NAMES[opt], ims))
    sh = sheet("岩鯨の心臓 ラフ (A/B/C 各3段階)", cols,
               "上3段: 段階1〜3 の 3/4 描画 (100-66%, 66-33%, 33-0%)。下3段: ボスバー (段階ごとの枠と色)")
    sh.save(ROOT / "docs/roughs/boss.png")
    print("saved", ROOT / "docs/roughs/boss.png")


if __name__ == "__main__":
    main()
