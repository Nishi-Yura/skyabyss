"""余燼の仔 (young_ember) 低ポリ ラフ 3 案の共通部分 (真珠守りの pk.py の写し)。

座標: 1 単位 = 1/16 ブロック。作図は「前 = -Z」。書き出すとき (x, z) の符号を反転して geo の空間へ
(geo の root は Y 軸 180 度回る。最後の前は -Z)。作図の +x = r (leg_fr・leg_br の側)。
骨は 3 案で共通 (名前・親・pivot)。ゲームの箱の geo (tools/gen/world/gen_young_ember_assets.py) も同じ骨を使う。
すべての部品は閉じた形 (筒は両端を塞ぎ、棘は根元を塞ぐ)。部品は親へ沈めてつなぐ。

絵の具 (paint / export_mesh) は潮招きの common.py をそのまま借り、燠のひび (光る) を extra で足す。
"""
from __future__ import annotations

import copy
import importlib.util
import json
import math
import sys
from collections import Counter
from pathlib import Path

import numpy as np
from PIL import Image

HERE = Path(__file__).resolve().parent
LP = HERE.parent
ROOT = HERE.parents[3]
sys.path.insert(0, str(LP))
sys.path.insert(0, str(ROOT / "tools/rough"))
sys.path.insert(0, str(ROOT / "tools"))
sys.path.insert(0, str(ROOT / "tools" / "gen/world"))
from lpkit import *  # noqa
import voxel  # noqa

_spec = importlib.util.spec_from_file_location("tb_common", LP / "tide_beckoner" / "common.py")
tbc = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(tbc)

import gen_young_ember_assets as G  # noqa: E402  (骨・鞍の高さ・アニメは一つの定義)

FOLDER = HERE / "out"            # 案ごとのメッシュ・アトラス (取り込みの入力)
REVIEW = ROOT / "docs/review/young_roughs"
flipxz = tbc.flipxz
SEAT_Y = G.SEAT_Y
SKELETON_DRAW = G.SKELETON_DRAW
BONES = {k: (p, [float(x) + 0.0 for x in flipxz(v)]) for k, (p, v) in SKELETON_DRAW.items()}


def pv(bone):
    return np.array(SKELETON_DRAW[bone][1], float)


def V(*a):
    return np.array(a, float)


# ====================================================================== 絵の具 (親の余燼の色の言葉)
ROCK = [(14, 13, 16), (24, 23, 27), (36, 35, 40), (50, 48, 54), (66, 64, 70), (84, 82, 88)]
SKIN = [(26, 22, 24), (40, 34, 35), (56, 48, 47), (74, 64, 61), (94, 82, 76)]
ASH = [(92, 90, 92), (122, 120, 122), (152, 150, 150), (180, 178, 176), (208, 206, 202)]
ASHD = [(44, 40, 40), (62, 58, 56), (84, 78, 74), (108, 102, 96), (134, 128, 120)]
BELLY = [(60, 52, 50), (82, 72, 68), (106, 94, 88), (130, 118, 108), (152, 140, 128)]
LAVA = [(120, 28, 10), (190, 56, 14), (236, 104, 24), (255, 160, 44), (255, 214, 100)]
TOOTH = [(120, 110, 96), (160, 150, 130), (196, 188, 164), (226, 218, 194)]
HORN = [(16, 14, 18), (30, 26, 32), (48, 42, 48), (68, 60, 66)]
DARK = [(8, 8, 10), (16, 15, 18), (26, 25, 28), (38, 36, 40)]
MOUTH = [(36, 10, 10), (62, 18, 16), (92, 28, 22), (122, 42, 30)]
BONE = [(62, 54, 48), (92, 82, 70), (124, 112, 94), (156, 144, 120), (186, 174, 148)]
CLAW = [(40, 36, 36), (70, 64, 60), (104, 96, 88), (140, 130, 116)]
CORE = [(236, 104, 24), (255, 160, 44), (255, 214, 100), (255, 240, 190)]


def base_pal(**over):
    mats = {
        "rock": dict(ramp=ROCK, base=0.50, lit=0.30, noise=0.10, crack=0.10, min=1, streak=0.06),
        "plate": dict(ramp=HORN, base=0.55, lit=0.30, noise=0.08, crack=0.06, min=0),
        "skin": dict(ramp=SKIN, base=0.55, lit=0.30, noise=0.10, crack=0.08, min=1),
        "belly": dict(ramp=BELLY, base=0.50, lit=0.25, noise=0.08, crack=0.0, min=0, stripe=4),
        "bone": dict(ramp=BONE, base=0.58, lit=0.28, noise=0.06, crack=0.08, min=0, streak=0.1),
        "bone2": dict(ramp=BONE, base=0.44, lit=0.28, noise=0.06, crack=0.08, min=0, streak=0.1),
        "slab": dict(ramp=ASHD, base=0.5, lit=0.25, noise=0.10, crack=0.12, min=0),
        "lava": dict(ramp=LAVA, base=0.62, lit=0.30, noise=0.08, crack=0, min=1),
        "core": dict(ramp=CORE, base=0.6, lit=0.35, noise=0.04, crack=0, min=1),
        "eye": dict(ramp=CORE, base=0.55, lit=0.3, noise=0.04, crack=0, min=1),
        "crystal": dict(ramp=LAVA, base=0.55, lit=0.40, noise=0.06, crack=0, min=1),
        "claw": dict(ramp=CLAW, base=0.55, lit=0.30, noise=0.06, crack=0, min=0),
        "mouth": dict(ramp=MOUTH, base=0.45, lit=0.2, noise=0.06, crack=0, min=0),
        "dark": dict(ramp=DARK, base=0.4, lit=0.1, noise=0.1, crack=0, min=0),
    }
    mats.update(over)
    return dict(mats=mats, glow_mats=["lava", "core", "eye", "crystal"])


SEAM_MATS = ("rock", "plate", "skin")


def ember_seams(density=0.055, width=0.02, mats=SEAM_MATS, seed=3):
    """燠のひび: 3D の値の雑音の 0.5 の等高線を細い光る線にする (面をまたいでつながる)。"""
    def ex(c, col, glow):
        P, bay, owned = c["P"], c["bay"], c["owned"]
        sel = np.isin(c["mats"], mats) & owned & c["INS"]
        n1 = tbc.vnoise(P * density + 11, seed)
        n2 = tbc.vnoise(P * density * 2.3 + 5, seed + 4)
        n = 0.7 * n1 + 0.3 * n2
        d = np.abs(n - 0.5)
        line = sel & (d < width + 0.006 * bay)
        hot = line & (d < width * 0.45)
        lv = np.array(LAVA, float)
        col = np.where(line[..., None], lv[2], col)
        col = np.where(hot[..., None], lv[4], col)
        # ひびのまわりを赤く焼く (光らない)
        warm = sel & ~line & (d < width * 2.2)
        col = np.where(warm[..., None], col * 0.55 + np.array([110, 30, 12.0]) * 0.45, col)
        return col, glow | line
    return ex


# ====================================================================== 形の部品 (閉じた形だけ)

def rrect(c, u, v, w, h, cut=0.0):
    c, u, v = (np.asarray(a, float) for a in (c, u, v))
    u, v = nrm(u), nrm(v)
    hw, hh = w / 2.0, h / 2.0
    if cut <= 0:
        return [c - hw * u - hh * v, c + hw * u - hh * v, c + hw * u + hh * v, c - hw * u + hh * v]
    k = cut * min(hw, hh)
    pts = [(-hw + k, -hh), (hw - k, -hh), (hw, -hh + k), (hw, hh - k), (hw - k, hh), (-hw + k, hh), (-hw, hh - k),
           (-hw, -hh + k)]
    return [c + a * u + b * v for a, b in pts]


def fbox(bone, mat, c, size, R=None, taper=(1.0, 1.0), cut=0.0, top_mat=None):
    """面取りの箱 (閉じる)。size = (x, y, z)、上面は y + size/2。"""
    sx, sy, sz = size
    R = np.eye(3) if R is None else R
    ex, ey, ez = (R @ np.array(a) for a in ((1, 0, 0), (0, 1, 0), (0, 0, 1)))
    c = np.asarray(c, float)
    r0 = rrect(c - ey * sy / 2, ex, ez, sx, sz, cut)
    r1 = rrect(c + ey * sy / 2, ex, ez, sx * taper[0], sz * taper[1], cut)
    return loft(bone, mat, [r0, r1], caps=(mat, top_mat or mat))


def cone(bone, mat, base, d, length, r, sides=4, rings=1, bend=(0, 0, 0), phase=45.0, hint=(0, 1, 0)):
    """根元を塞いだ棘 (閉じる)。"""
    d = nrm(np.asarray(d, float))
    base = np.asarray(base, float)
    bend = np.asarray(bend, float)
    path = [base + d * length * (i / rings) + bend * (i / rings) ** 2 for i in range(rings + 1)]
    ras = [r * max(0.0, 1 - i / rings) ** 0.8 for i in range(rings + 1)]
    ras[-1] = 0.0
    return tube(bone, mat, path, ras, sides=sides, hint=hint, phase=phase, caps=(True, False))


def rod(bone, mat, path, r, sides=5, hint=(0, 1, 0), rb=None, phase=0.0):
    """両端を塞いだ筒 (閉じる)。"""
    return tube(bone, mat, path, r, rb, sides=sides, hint=hint, phase=phase, caps=(True, True))


def xsec(z, cy, rx, top, bot, n=10, squ=0.35, cx=0.0):
    """z の位置の、xy 面の断面 (頂点 0 が真上)。top / bot は中心からの上・下の高さ。squ で角張らせる。"""
    pts = []
    for k in range(n):
        a = 2 * math.pi * k / n
        s, c = math.sin(a), math.cos(a)
        # 丸と四角のあいだ (超楕円)
        e = 1.0 / (1.0 + squ)
        x = math.copysign(abs(s) ** e, s) * rx
        y = math.copysign(abs(c) ** e, c) * (top if c >= 0 else bot)
        pts.append(np.array([cx + x, cy + y, z]))
    return pts


def body_loft(bone, mat, secs, n=10, squ=0.35, mats=None):
    """断面 (z, cy, rx, top, bot) の列をつなぐ胴 (両端を塞ぐ)。"""
    rings = [xsec(z, cy, rx, top, bot, n, squ) for z, cy, rx, top, bot in secs]
    return loft(bone, mat, rings, caps=(mat, mat), mats=mats)


def hring(y, rx, rz, n=8, cx=0.0, cz=0.0, jit=0.0, seed=0, squ=0.0, rz_back=None):
    """水平の輪 (頂点 0 が前 = -Z)。jit で岩らしく半径をゆらす。"""
    pts = []
    rb = rz if rz_back is None else rz_back
    for k in range(n):
        a = 2 * math.pi * k / n
        s, c = math.sin(a), math.cos(a)
        e = 1.0 / (1.0 + squ)
        j = 1.0 + jit * (math.sin(k * 12.9898 + seed * 78.233) * 43758.5453 % 1.0 - 0.5)
        x = math.copysign(abs(s) ** e, s) * rx * j
        z = -math.copysign(abs(c) ** e, c) * (rz if c >= 0 else rb) * j
        pts.append(np.array([cx + x, y, cz + z]))
    return pts


def crater(bone, base, r0, h, cz=0.0, cx=0.0, n=8, mat="rock", seed=1):
    """親の火山を縮めた小さな火口 (中は溶岩。閉じる)。"""
    y0 = base
    rings = [hring(y0, r0, r0, n, cx, cz, 0.18, seed), hring(y0 + h * 0.6, r0 * 0.68, r0 * 0.68, n, cx, cz, 0.12, seed + 1),
             hring(y0 + h, r0 * 0.5, r0 * 0.5, n, cx, cz, 0.0), hring(y0 + h - 1.5, r0 * 0.34, r0 * 0.34, n, cx, cz)]
    return loft(bone, mat, rings, caps=(mat, "lava"))


def claws(bone, foot, fwd=(0, 0, -1), n=3, spread=4.0, length=4.0, r=1.4, mat="claw"):
    F = []
    foot = np.asarray(foot, float)
    for i in range(n):
        x = (i - (n - 1) / 2) * spread
        F += cone(bone, mat, foot + V(x, 0.8, 0), V(fwd[0] + x * 0.03, -0.25, fwd[2]), length, r, sides=4)
    return F


def core_mote(c, r=5.5, ring_mat="bone", ring=True):
    """胸の核 = 歌のかけら (光る)。まわりに骨の留め具 4 本 (沈めてつなぐ)。"""
    c = np.asarray(c, float)
    F = ellipsoid("core", "core", c, (r, r, r * 0.8), segs=8, rings=4)
    if ring:
        for k in range(4):
            a = math.radians(45 + 90 * k)
            d = V(math.cos(a), math.sin(a), 0.0)
            F += cone("core", ring_mat, c + d * r * 0.7 + V(0, 0, 1.5), d + V(0, 0, -0.6), r * 0.9, 1.6, sides=4)
    return F


# ====================================================================== 姿勢

def pose_body(faces, pose):
    out = []
    for f in faces:
        g = copy.copy(f)
        g.verts = np.array([G.pose_point_draw(p, f.bone, pose) for p in f.verts])
        tbc._refresh(g, f)
        out.append(g)
    return out


# ====================================================================== 書き出し

def build_option(key, build, pal, body_hint=512):
    body, extra = build()
    d = FOLDER / key
    d.mkdir(parents=True, exist_ok=True)
    size, P, N, M, L = build_atlas(body, body_hint, 0)
    seams = ember_seams()

    def ex(c, col, glow):
        col, glow = seams(c, col, glow)
        if extra:
            col, glow = extra(c, col, glow)
        return col, glow
    atlas, glow = tbc.paint(body, size, P, N, M, pal, (), ex)
    stem = f"young_ember_{key}"
    Image.fromarray(atlas, "RGBA").save(d / f"{stem}_atlas.png")
    gm = np.zeros((size, size, 4), np.uint8)
    gm[glow] = (255, 255, 255, 255)
    Image.fromarray(gm, "RGBA").save(d / f"{stem}_glowmask.png")
    data = tbc.export_mesh(body, size, d / f"{stem}_mesh.json", stem, f"{stem}_atlas.png", BONES,
                           {"glowmask": f"{stem}_glowmask.png", "seat_y": SEAT_Y,
                            "note": "geo space (root rotated 180 deg about Y: root carries rotation [0,180,0]); "
                                    "vertices are absolute like geo cubes; 13 bones = geo/entity/young_ember.geo.json; final front -Z"})
    return dict(key=key, body=body, size=size, atlas=atlas, glow=glow, data=data)


def open_edges(data):
    """書き出したメッシュの開いた辺 (一つの三角形にしか使われない辺) の数 (骨ごと。titan_mesh_audit と同じ数え方)。"""
    per = Counter()
    for bn, b in data["bones"].items():
        v = b["vertices"]
        edges = Counter()
        for t in b["triangles"]:
            for a, c in ((0, 1), (1, 2), (2, 0)):
                p = tuple(round(x, 2) for x in v[t[a]])
                q = tuple(round(x, 2) for x in v[t[c]])
                edges[tuple(sorted((p, q)))] += 1
        per[bn] = sum(1 for k, c in edges.items() if c == 1)
    return sum(per.values()), {k: c for k, c in per.items() if c}


def seat_check(body):
    """鞍の上面: 座席の範囲 (x +-25.6, z +-51.2) で一番高い面の高さ (= SEAT_Y であること) と、それより上に出る頂点の数。"""
    hx, hz = G.SEAT_HALF
    top = -1e9
    above = 0
    for f in body:
        for p in f.verts:
            if abs(p[0]) <= hx and abs(p[2]) <= hz:
                top = max(top, p[1])
                if p[1] > SEAT_Y + 1e-3:
                    above += 1
    # 鞍の骨の上向きの面が座席の範囲を覆っているか (格子の点を上から射る)
    ups = [f for f in body if f.bone == "saddle" and f.normal[1] > 0.99 and abs(f.verts[:, 1].mean() - SEAT_Y) < 1e-3]
    miss = 0
    for x in np.linspace(-hx, hx, 9):
        for z in np.linspace(-hz, hz, 17):
            hit = False
            for f in ups:
                for a, b, c in f.tris():
                    A, B, C = f.verts[a][[0, 2]], f.verts[b][[0, 2]], f.verts[c][[0, 2]]
                    T = np.array([B - A, C - A]).T
                    if abs(np.linalg.det(T)) < 1e-9:
                        continue
                    l = np.linalg.solve(T, np.array([x, z]) - A)
                    if l.min() >= -1e-6 and l.sum() <= 1 + 1e-6:
                        hit = True
                        break
                if hit:
                    break
            miss += not hit
    return top, above, miss


def bounds(body):
    lo = np.min([f.verts.min(0) for f in body], 0)
    hi = np.max([f.verts.max(0) for f in body], 0)
    return lo, hi


def render_pose(r, pose, yaw, pitch, size, center, scale, extra_faces=None):
    faces = pose_body(r["body"], pose or {})
    atlas, glow = r["atlas"], r["glow"]
    if extra_faces:
        S = atlas.shape[0]
        big = np.zeros((S * 2, S * 2, 4), np.uint8)
        big[:S, :S] = atlas
        big[S:S + 8, :8] = (150, 150, 158, 255)
        gl = np.zeros((S * 2, S * 2), bool)
        gl[:S, :S] = glow
        for f in extra_faces:
            f.uv = np.array([[2.0 + (i % 2) * 3, S + 2.0 + (i // 2) * 3] for i in range(len(f.verts))])
        faces = faces + extra_faces
        atlas, glow = big, gl
    return render(faces, atlas, glow, yaw, pitch, size, center, scale)


def riders():
    """座席の 6 人 (灰色の箱。幅 0.6・座って高さ 1.2 ブロック)。"""
    F = []
    for (x, y, z) in G.SEATS:
        F += fbox("saddle", "rider", (x, y + 9.6, z), (9.6, 19.2, 9.6))
    return F


def bgpanel(w, h):
    return Image.fromarray(np.clip(voxel._background(w, h, "dark"), 0, 255).astype(np.uint8))


def on_bg(im):
    bg = bgpanel(*im.size)
    bg.paste(im, (0, 0), im)
    return bg
