"""潮殻の稚貝 (young_tide_shell) 低ポリ ラフ 3 案の共通部分。真珠守り (pearl_keeper/pk.py) の写し。

座標: 1 単位 = 1/16 ブロック。作図は「前 = -Z」。書き出すとき (x, z) の符号を反転して geo の空間へ
(geo の root は Y 軸 180 度回る。最後の前は -Z)。作図の +x = ゲームの右 (fin_fr)。
骨は 3 案で共通 (名前・親・pivot)。ゲームの箱の geo (tools/gen_young_tide_shell_assets.py) と一つの定義。

閉じた形だけを使う (水密): 筒は両端を塞ぐか先を尖らせる。棘は cone() (根元も塞ぐ)。
鞍 (骨 saddle) の上面は y = SEAT の平らな板。座る範囲 (x ±25.6、z ±51.2) には何も載せない。
"""
from __future__ import annotations

import copy
import importlib.util
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
from lpkit import *  # noqa
from lpkit import _mk  # noqa
import voxel  # noqa

_spec = importlib.util.spec_from_file_location("tb_common", LP / "tide_beckoner" / "common.py")
tbc = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(tbc)

import gen_young_tide_shell_assets as G  # noqa: E402

FOLDER = ROOT / "docs/review/young_roughs/young_tide_shell_mesh"   # メッシュ・アトラス (取り込みの入力)
PAGE = ROOT / "docs/review/young_roughs"
flipxz = tbc.flipxz
rot_geo = tbc.rot_geo
SEAT = G.SEAT
SKELETON_DRAW = G.SKELETON_DRAW
BONES = {k: (p, [float(x) + 0.0 for x in flipxz(v)]) for k, (p, v) in SKELETON_DRAW.items()}
SEAT_X, SEAT_Z = 25.6, 51.2   # 座る範囲 (単位、± の半分)


def pv(bone):
    return np.array(SKELETON_DRAW[bone][1], float)


def V(*a):
    return np.array(a, float)


# ====================================================================== 形の部品 (すべて閉じた形)

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


def rloft(bone, mat, rings, caps=(True, True), mats=None):
    return loft(bone, mat, rings, caps=tuple(mat if x is True else x for x in caps), mats=mats)


def fbox(bone, mat, c, size, R=None, taper=(1.0, 1.0), cut=0.0, caps=(True, True)):
    sx, sy, sz = size
    R = np.eye(3) if R is None else R
    ex, ey, ez = (R @ np.array(a) for a in ((1, 0, 0), (0, 1, 0), (0, 0, 1)))
    c = np.asarray(c, float)
    r0 = rrect(c - ey * sy / 2, ex, ez, sx, sz, cut)
    r1 = rrect(c + ey * sy / 2, ex, ez, sx * taper[0], sz * taper[1], cut)
    return rloft(bone, mat, [r0, r1], caps)


def cone(bone, mat, base, d, length, r, sides=4, rings=1, bend=(0, 0, 0), phase=45.0):
    """根元を塞いだ先細りの棘 (水密)。"""
    d = nrm(np.asarray(d, float))
    base = np.asarray(base, float)
    path = [base + d * length * (i / rings) + np.asarray(bend, float) * (i / rings) ** 2 for i in range(rings + 1)]
    ras = [r * max(0.0, 1 - i / rings) ** 0.8 for i in range(rings + 1)]
    ras[-1] = 0.0
    hint = (0, 1, 0) if abs(d[1]) < 0.9 else (1, 0, 0)
    return tube(bone, mat, path, ras, sides=sides, hint=hint, phase=phase, caps=(True, False))


def rod(bone, mat, path, r, sides=5, rb=None, hint=(0, 1, 0), phase=0.0):
    """両端を塞いだ筒。"""
    return tube(bone, mat, path, r, rb, sides=sides, hint=hint, phase=phase, caps=(True, True))


def coral(bone, mat, base, d, length, r, branches, sides=4, bmat=None):
    """枝分かれする珊瑚 (幹と枝は別々の閉じた棘)。branches = [(幹の位置 0..1, 向き(度), 長さの割合, 開き(度))]。"""
    d = nrm(np.asarray(d, float))
    base = np.asarray(base, float)
    out = cone(bone, mat, base, d, length, r, sides=sides, rings=2)
    ref = np.array([0, 1.0, 0]) if abs(d[1]) < 0.9 else np.array([1.0, 0, 0])
    u = nrm(np.cross(d, ref))
    v = np.cross(d, u)
    for t, ang, fr, tilt in branches:
        p = base + d * length * t
        a, tl = math.radians(ang), math.radians(tilt)
        dd = nrm(d * math.cos(tl) + (u * math.cos(a) + v * math.sin(a)) * math.sin(tl))
        out += cone(bone, bmat or mat, p - dd * 0.6, dd, length * fr, r * (1 - t) * 0.8 + 0.5, sides=3, rings=1)
    return out


def paddle(bone, mat, root, tip, chord, thick, up=(0, 1, 0), lead=(0, 0, -1), sweep=0.0, mats=None):
    """ひれ (平たい板の輪切りを根元から先へつなぐ。両端を塞ぐ)。chord・thick は根元から先までの列 (4 つ程度)。
    sweep は先ほど後ろ (+z) へ流す量。lead は前縁の向き。"""
    root, tip = np.asarray(root, float), np.asarray(tip, float)
    up = nrm(np.asarray(up, float))
    n = len(chord)
    ax = nrm(tip - root)
    ld = np.asarray(lead, float)
    ld = nrm(ld - np.dot(ld, ax) * ax)
    rings = []
    for i in range(n):
        t = i / (n - 1)
        c = root + (tip - root) * t + V(0, 0, sweep * t * t)
        uu = nrm(np.cross(ax, ld))
        if np.dot(uu, up) < 0:
            uu = -uu
        # 前縁は丸く、後縁は細く (6 角の断面)
        w, h = chord[i], thick[i]
        pts = [c + ld * w * 0.5, c + ld * w * 0.15 + uu * h * 0.5, c - ld * w * 0.3 + uu * h * 0.3, c - ld * w * 0.5,
               c - ld * w * 0.3 - uu * h * 0.3, c + ld * w * 0.15 - uu * h * 0.5]
        rings.append(pts)
    return rloft(bone, mat, rings, caps=(True, True), mats=mats)


def lathe(bone, mat, prof, n=12, cx=0.0, cz=0.0, sx=1.0, sz=1.0, mats=None, phase=None):
    """回転体 (楕円の輪切り)。prof = [(y, r)]。最初と最後の輪は塞ぐ (r=0 は点)。sx・sz で x・z を伸ばす。"""
    ph = 180.0 / n if phase is None else phase
    rings = []
    for y, r in prof:
        rings.append([V(cx + sx * r * math.sin(math.radians(ph + 360 * k / n)), y,
                        cz - sz * r * math.cos(math.radians(ph + 360 * k / n))) for k in range(n)])
    return rloft(bone, mat, rings, caps=(True, True), mats=mats)


def sring(rx, rz, y, n=16, p=2.5, cx=0.0, cz=0.0, phase=None):
    """超楕円の輪 (|x/rx|^p + |z/rz|^p = 1)。前 (-z) の真ん中から。p が大きいほど角ばる。"""
    ph = 180.0 / n if phase is None else phase
    out = []
    for k in range(n):
        a = math.radians(ph + 360.0 * k / n)
        s, c = math.sin(a), -math.cos(a)
        x = rx * math.copysign(abs(s) ** (2.0 / p), s)
        z = rz * math.copysign(abs(c) ** (2.0 / p), c)
        out.append(V(cx + x, y, cz + z))
    return out


def sloft(bone, mat, prof, n=16, p=2.5, mats=None, cz=0.0):
    """prof = [(y, rx, rz)] の超楕円の輪をつなぐ閉じた形 (両端を塞ぐ)。"""
    return rloft(bone, mat, [sring(rx, rz, y, n, p, cz=cz) for y, rx, rz in prof], caps=(True, True), mats=mats)


def superellipse_inside(x, z, rx, rz, p=2.5):
    return (abs(x) / rx) ** p + (abs(z) / rz) ** p <= 1.0


def pearl(bone, mat, c, r, segs=8, rings=3):
    return ellipsoid(bone, mat, c, (r, r, r), segs=segs, rings=rings)


def deck(mat="deck", w=60.0, l=118.0, thick=3.0, cut=0.25, y=None, rim=None):
    """鞍の板 (上面 = SEAT)。座る範囲を必ず覆う。"""
    y = SEAT if y is None else y
    F = fbox("saddle", mat, V(0, y - thick / 2, 0), (w, thick, l), cut=cut)
    return F


def oval_ring_pts(rx, rz, n, y=0.0, phase=0.0):
    return [V(rx * math.sin(math.radians(phase + 360 * k / n)), y, -rz * math.cos(math.radians(phase + 360 * k / n)))
            for k in range(n)]


def scutes(c, col, mats, centers, dark, light, width=1.3, rings=True, ymin=None):
    """甲羅の鱗板: centers (x, z) の Voronoi の継ぎ目を暗く、板の中に年輪 (同心の筋) を描く。mats は材の名の組。"""
    P, owned, bay = c["P"], c["owned"], c["bay"]
    sel = np.isin(c["mats"], list(mats)) & owned
    if ymin is not None:
        sel &= P[..., 1] > ymin
    if not sel.any():
        return col
    C = np.array(centers, float)
    d = np.sqrt((P[..., 0:1] - C[:, 0]) ** 2 + (P[..., 2:3] - C[:, 1]) ** 2)
    ds = np.sort(d, -1)
    seam = sel & (ds[..., 1] - ds[..., 0] < width + 0.4 * bay)
    dark, light = np.array(dark, float), np.array(light, float)
    if rings:
        ring = sel & ~seam & ((ds[..., 0] % 6.0) < 0.9) & (ds[..., 1] - ds[..., 0] > 4.0)
        col = np.where(ring[..., None], col * 0.82 + light * 0.0, col)
        core = sel & ~seam & (ds[..., 0] < 3.2 + bay)
        col = np.where(core[..., None], col * 0.6 + light * 0.4, col)
    col = np.where(seam[..., None], dark, col)
    return col


# ====================================================================== 姿勢

def pose_point(p, bone, pose):
    """geo 空間の点 p を pose ({骨: {rotation, scale, position}}) のときの位置へ。root は回転 180 を含めず、拡大だけ当てる。"""
    while bone is not None:
        parent, pivot = BONES[bone]
        pv_ = np.array(pivot, float)
        st = pose.get(bone)
        if st is not None:
            if not isinstance(st, dict):
                st = {"rotation": st}
            q = p - pv_
            if "scale" in st:
                q = q * np.array(st["scale"], float)
            if "rotation" in st and bone != "root":
                q = rot_geo(*st["rotation"]) @ q
            p = q + pv_
            if "position" in st:
                ps = np.array(st["position"], float)
                p = p + np.array([-ps[0], ps[1], ps[2]])
        bone = parent
    return p


def pose_body(faces, pose):
    out = []
    for f in faces:
        g = copy.copy(f)
        g.verts = np.array([flipxz(pose_point(flipxz(p), f.bone, pose)) for p in f.verts])
        tbc._refresh(g, f)
        out.append(g)
    return out


# ====================================================================== 書き出し

def build_option(key, build, pal, body_hint=512):
    body, eyes, extra = build()
    d = FOLDER / key
    d.mkdir(parents=True, exist_ok=True)
    size, P, N, M, L = build_atlas(body, body_hint, 0)

    def ex(c, col, glow):
        if eyes:
            col, glow = tbc.eye_marks(c, col, glow, eyes)
        if extra:
            col, glow = extra(c, col, glow)
        return col, glow
    atlas, glow = tbc.paint(body, size, P, N, M, pal, (), ex)
    stem = f"young_tide_shell_{key}"
    Image.fromarray(atlas, "RGBA").save(d / f"{stem}_atlas.png")
    gm = np.zeros((size, size, 4), np.uint8)
    gm[glow] = (255, 255, 255, 255)
    Image.fromarray(gm, "RGBA").save(d / f"{stem}_glowmask.png")
    data = tbc.export_mesh(body, size, d / f"{stem}_mesh.json", stem, f"{stem}_atlas.png", BONES,
                           {"glowmask": f"{stem}_glowmask.png", "seat_y_blocks": G.SEAT_Y,
                            "note": "geo space (root rotated 180 deg about Y: root carries rotation [0,180,0]); "
                                    "vertices are absolute like geo cubes; 10 bones = geo/entity/young_tide_shell.geo.json"})
    return dict(key=key, body=body, eyes=eyes, size=size, atlas=atlas, glow=glow, data=data)


# ====================================================================== 確かめ

def open_edges(data):
    """titan_mesh_audit と同じ数え方 (骨ごと、頂点を 0.01 に丸めて、一度しか使われない辺)。"""
    edges = Counter()
    for bn, b in data["bones"].items():
        v = b["vertices"]
        for t in b["triangles"]:
            for a, c in ((0, 1), (1, 2), (2, 0)):
                p, q = (tuple(round(x, 2) for x in v[t[a]]), tuple(round(x, 2) for x in v[t[c]]))
                edges[(bn,) + tuple(sorted((p, q)))] += 1
    per = Counter(k[0] for k, c in edges.items() if c % 2 == 1)
    return sum(per.values()), dict(per)


def seat_check(data, n=9):
    """座る範囲 (x ±1.6、z ±3.2 ブロック) に n×n の点を取り、真上から下へ線を落として最初に当たる面の高さを測る。
    戻り値: (鞍の上面の高さの最小・最大 (ブロック), 鞍以外の骨が鞍より上に当たった点の数)。"""
    tris = []
    for bn, b in data["bones"].items():
        v = np.array(b["vertices"], float)
        for t in b["triangles"]:
            tris.append((bn, v[t]))
    hs, other = [], 0
    for x in np.linspace(-SEAT_X, SEAT_X, n):
        for z in np.linspace(-SEAT_Z, SEAT_Z, n):
            best, bb = -1e9, None
            for bn, T in tris:
                a, b_, c = T[:, [0, 2]]
                den = (b_[1] - c[1]) * (a[0] - c[0]) + (c[0] - b_[0]) * (a[1] - c[1])
                if abs(den) < 1e-9:
                    continue
                l0 = ((b_[1] - c[1]) * (x - c[0]) + (c[0] - b_[0]) * (z - c[1])) / den
                l1 = ((c[1] - a[1]) * (x - c[0]) + (a[0] - c[0]) * (z - c[1])) / den
                l2 = 1 - l0 - l1
                if min(l0, l1, l2) < -1e-6:
                    continue
                y = l0 * T[0, 1] + l1 * T[1, 1] + l2 * T[2, 1]
                if y > best:
                    best, bb = y, bn
            if bb == "saddle":
                hs.append(best)
            else:
                other += 1
    if not hs:
        return (None, None), other
    return (round(min(hs) / 16.0, 4), round(max(hs) / 16.0, 4)), other


# ====================================================================== 描画

def rider_faces():
    """座る人の影 (灰色の箱 0.6 幅・座高 1.2)。骨 saddle に付ける (鞍と一緒に動く)。"""
    F = []
    for sx, sz in G.SEATS:
        F += fbox("saddle", "rider", V(sx * 16, SEAT + 9.6, sz * 16), (9.6, 19.2, 9.6))
    return F


def with_riders(r):
    """人の面を足した (faces, atlas, glow)。人の面の uv はアトラスの外に足した灰色の区画を指す。"""
    S = r["atlas"].shape[0]
    at = np.zeros((S + 8, S + 8, 4), np.uint8)
    at[:S, :S] = r["atlas"]
    at[S:, S:] = (150, 150, 160, 255)
    gl = np.zeros((S + 8, S + 8), bool)
    gl[:S, :S] = r["glow"]
    F = rider_faces()
    for f in F:
        f.uv = np.full((len(f.verts), 2), S + 4.0)
    return r["body"] + F, at, gl


def render_pose(r, pose, yaw, pitch, size, center, scale, riders=False):
    if riders:
        body, at, gl = with_riders(r)
    else:
        body, at, gl = r["body"], r["atlas"], r["glow"]
    return render(pose_body(body, pose or {}), at, gl, yaw, pitch, size, center, scale)


def bgpanel(w, h):
    return Image.fromarray(np.clip(voxel._background(w, h, "dark"), 0, 255).astype(np.uint8))


def on_bg(im, water=False):
    bg = bgpanel(*im.size)
    bg.paste(im, (0, 0), im)
    return bg


def bounds(faces):
    lo = np.min([f.verts.min(0) for f in faces], 0)
    hi = np.max([f.verts.max(0) for f in faces], 0)
    return lo, hi
