"""雷翼の雛 (young_thunder_wing) 低ポリ ラフ 3 案の共通部分 (真珠守りの pk.py を写した)。

座標: 1 単位 = 1/16 ブロック。作図は「前 = -Z」、作図の +x = 右 (wing_r)。書き出すとき (x, z) の符号を反転して geo の空間へ
(geo の root は Y 軸 180 度回るので、最終の前は -Z のまま)。骨は 3 案で共通 (tools/gen_young_thunder_wing_assets.py)。
鞍の上面は SEAT_Y (36 単位 = 2.25 ブロック) の平らな面で、|x| <= 25.6、|z| <= 51.2 を覆う。その範囲に出っ張りを置かない。
すべての部品は閉じた面 (蓋つきの筒・楕円体・蓋つきの輪切り・輪環) で作り、継ぎ目は親へ沈める。

絵の具 (paint / eye_marks / export_mesh) は潮招きの common.py を借りる。
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

import gen_young_thunder_wing_assets as G  # noqa: E402  (骨・鞍の高さ・アニメはゲームの箱の geo と一つの定義)

OUT = HERE / "out"
REVIEW = ROOT / "docs/review/young_roughs"
flipxz = tbc.flipxz
rot_geo = tbc.rot_geo
SEAT_Y = G.SEAT_Y
SEATS = G.SEATS
SKELETON_DRAW = G.SKELETON_DRAW
BONES = {k: (p, [float(x) + 0.0 for x in flipxz(v)]) for k, (p, v) in SKELETON_DRAW.items()}


def pv(bone):
    return np.array(SKELETON_DRAW[bone][1], float)


def V(*a):
    return np.array(a, float)


# ====================================================================== 形の部品 (どれも閉じている)

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


def fbox(bone, mat, c, size, R=None, taper=(1.0, 1.0), cut=0.0):
    sx, sy, sz = size
    R = np.eye(3) if R is None else R
    ex, ey, ez = (R @ np.array(a) for a in ((1, 0, 0), (0, 1, 0), (0, 0, 1)))
    c = np.asarray(c, float)
    r0 = rrect(c - ey * sy / 2, ex, ez, sx, sz, cut)
    r1 = rrect(c + ey * sy / 2, ex, ez, sx * taper[0], sz * taper[1], cut)
    return rloft(bone, mat, [r0, r1])


def oval(c, rx, rz, n=12, y=None):
    """水平の楕円の多角形 (前 -Z の頂点が 0 番)。"""
    c = np.asarray(c, float)
    return [c + V(rx * math.sin(2 * math.pi * k / n), 0, -rz * math.cos(2 * math.pi * k / n)) for k in range(n)]


def cspike(bone, mat, base, d, length, r, sides=4, rings=2, bend=(0, 0, 0), hint=(0, 1, 0), phase=45.0, rb=None):
    """根元に蓋のある (閉じた) 棘。先端は点。"""
    d = nrm(d)
    base = np.asarray(base, float)
    path = [base + d * length * (i / rings) + np.asarray(bend, float) * (i / rings) ** 2 for i in range(rings + 1)]
    ras = [r * max(0.0, 1 - i / rings) ** 0.8 for i in range(rings + 1)]
    ras[-1] = 0.0
    rbv = None if rb is None else [rb * x / r for x in ras]
    return tube(bone, mat, path, ras, rbv, sides=sides, hint=hint, phase=phase, caps=(True, False))


def ctube(bone, mat, path, ra, rb=None, sides=5, hint=(0, 1, 0), phase=0.0, mats=None):
    """両端に蓋のある筒 (半径 0 の端は点)。"""
    ra_ = np.broadcast_to(np.asarray(ra, float), (len(path),))
    caps = (bool(ra_[0] > 0), bool(ra_[-1] > 0))
    return tube(bone, mat, path, ra, rb, sides=sides, hint=hint, phase=phase, caps=caps, mats=mats)


def feather(bone, mat, root, tip, w, t=1.2, up=(0, 1, 0), wtip=0.6, bow=0.0, twist=0.0, mid=0.45):
    """平たい羽根 (根元 → 先)。断面は w × t の長方形、先は細い。bow は上へのしなり。閉じている。"""
    root, tip = np.asarray(root, float), np.asarray(tip, float)
    d = nrm(tip - root)
    up = np.asarray(up, float)
    up = nrm(up - d * np.dot(up, d))
    side = nrm(np.cross(d, up))
    if twist:
        side, up = nrm(side * math.cos(math.radians(twist)) + up * math.sin(math.radians(twist))), \
            nrm(up * math.cos(math.radians(twist)) - side * math.sin(math.radians(twist)))
    m = root + (tip - root) * mid + up * bow
    rings = [rrect(root, side, up, w * 0.55, t), rrect(m, side, up, w, t), rrect(tip, side, up, wtip, t * 0.5)]
    return rloft(bone, mat, rings)


def torus(bone, mat, c, R, Rz, r, n=20, m=5, mats=None, phase=0.0, p=2.0):
    """水平の輪環 (楕円の輪 R×Rz、管の半径 r。p > 2 で角の丸い四角へ寄る超楕円)。閉じている。"""
    c = np.asarray(c, float)

    def se(a):
        return math.copysign(abs(a) ** (2.0 / p), a)
    cen = [c + V(R * se(math.sin(2 * math.pi * i / n)), 0, -Rz * se(math.cos(2 * math.pi * i / n))) for i in range(n)]
    rings = []
    for i in range(n):
        tng = nrm(cen[(i + 1) % n] - cen[i - 1])
        out = nrm(np.cross(tng, V(0, 1, 0)))
        if np.dot(out, cen[i] - c) < 0:
            out = -out
        rings.append([cen[i] + r * (out * math.cos(2 * math.pi * k / m + phase) + V(0, 1, 0) * math.sin(2 * math.pi * k / m + phase))
                      for k in range(m)])
    F = []
    for i in range(n):
        j = (i + 1) % n
        ref = (cen[i] + cen[j]) / 2
        for k in range(m):
            k2 = (k + 1) % m
            f = _mk([rings[i][k], rings[i][k2], rings[j][k2], rings[j][k]], bone,
                    mats[i % len(mats)] if mats else mat, ref)
            if f is not None:
                F.append(f)
    return F, cen


def wing_plate(bone, mat, secs, side=1, cut=0.45, mats=None):
    """翼の膜/腕の板。secs = [(x, y, z中心, 弦, 厚み)] (右翼の x)。左は side=-1 で鏡。"""
    rings = [rrect(V(side * x, y, z), V(0, 0, 1), V(0, 1, 0), ch, th, cut) for x, y, z, ch, th in secs]
    return rloft(bone, mat, rings, mats=mats)


def saddle_plate(bone, mat, outline, top=None, thick=4.0, rim_mat=None):
    """鞍の板: 上面が SEAT_Y の平らな多角形 (outline = 水平の点列)。下へ thick の厚み。"""
    top = SEAT_Y if top is None else top
    up = [np.array([p[0], top, p[2]]) for p in outline]
    lo = [np.array([p[0] * 0.94, top - thick, p[2] * 0.97]) for p in outline]
    return rloft(bone, mat, [lo, up], caps=(rim_mat or mat, mat))


def mirror(fn):
    """fn(side) を左右で呼んで足す。"""
    return fn(1) + fn(-1)


# ====================================================================== 姿勢 (root の大きさも含める)

def pose_point(p, bone, pose):
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


# ====================================================================== 絵 (稲妻の筋・座布団)

VEIN = np.array([(170, 214, 255), (220, 240, 255), (255, 255, 255)], float)
GOLD = np.array([(255, 218, 120), (255, 240, 180)], float)


def vein_paint(mats=("cov", "wing", "prim"), period=7.0, amp=2.2, rows=((-14.0, 1.0),), width=0.6):
    """翼の上面に光る稲妻の筋を描く extra。rows = [(z の位置, x へのずれの割合)]。"""
    def ex(c, col, glow):
        P, N, owned = c["P"], c["N"], c["owned"]
        sel = owned & np.isin(c["mats"], mats) & (N[..., 1] > 0.25)
        ax = np.abs(P[..., 0])
        for z0, k in rows:
            tri = np.abs(((ax / period) % 2.0) - 1.0) * 2 - 1
            zl = z0 + amp * tri + 0.04 * k * ax
            on = sel & (np.abs(P[..., 2] - zl) < width) & (ax > 30)
            col = np.where(on[..., None], VEIN[1], col)
            glow = glow | on
        return col, glow
    return ex


def seat_paint(col_rgb=(96, 110, 140)):
    """鞍の上面に 6 か所の座布団 (少し暗い楕円と縁)。"""
    def ex(c, col, glow):
        P, N, owned = c["P"], c["N"], c["owned"]
        top = owned & (c["bones"] == "saddle") & (N[..., 1] > 0.9) & (np.abs(P[..., 1] - SEAT_Y) < 0.2)
        for sx, sz in SEATS:
            r = np.sqrt(((P[..., 0] - sx) / 6.0) ** 2 + ((P[..., 2] - sz) / 7.0) ** 2)
            col = np.where((top & (r < 1.0))[..., None], np.array(col_rgb, float) * (1.0 + 0.12 * (r < 0.8))[..., None], col)
            col = np.where((top & (r >= 1.0) & (r < 1.15))[..., None], np.array(col_rgb, float) * 0.7, col)
        return col, glow
    return ex


def chain(*fns):
    def ex(c, col, glow):
        for f in fns:
            col, glow = f(c, col, glow)
        return col, glow
    return ex


# ====================================================================== 書き出し

def build_option(key, build, pal, body_hint=512):
    body, eyes, extra = build()
    d = OUT / key
    d.mkdir(parents=True, exist_ok=True)
    size, P, N, M, L = build_atlas(body, body_hint, 0)

    def ex(c, col, glow):
        if eyes:
            col, glow = tbc.eye_marks(c, col, glow, eyes, pupil=GOLD)
        if extra:
            col, glow = extra(c, col, glow)
        return col, glow
    atlas, glow = tbc.paint(body, size, P, N, M, pal, (), ex)
    stem = f"young_thunder_wing_{key}"
    Image.fromarray(atlas, "RGBA").save(d / f"{stem}_atlas.png")
    gm = np.zeros((size, size, 4), np.uint8)
    gm[glow] = (255, 255, 255, 255)
    Image.fromarray(gm, "RGBA").save(d / f"{stem}_glowmask.png")
    data = tbc.export_mesh(body, size, d / f"{stem}_mesh.json", stem, f"{stem}_atlas.png", BONES,
                           {"glowmask": f"{stem}_glowmask.png", "seat_y": SEAT_Y,
                            "note": "geo space (root rotated 180 deg about Y: root carries rotation [0,180,0]); "
                                    "vertices are absolute like geo cubes; 14 bones = geo/entity/young_thunder_wing.geo.json; "
                                    "final front = -Z; saddle top y = 36 (2.25 blocks)"})
    return dict(key=key, body=body, eyes=eyes, size=size, atlas=atlas, glow=glow, data=data)


def render_pose(r, pose, yaw, pitch, size, center, scale, extra_faces=None):
    faces = pose_body(r["body"], pose or {})
    atlas, glow = r["atlas"], r["glow"]
    if extra_faces:
        # 乗り手の灰色の箱: アトラスを右下に広げ、そこの灰色の 1 画素を指す
        S = atlas.shape[0]
        big = np.zeros((S * 2, S * 2, 4), np.uint8)
        big[:S, :S] = atlas
        big[S:, S:] = (150, 150, 158, 255)
        gl = np.zeros((S * 2, S * 2), bool)
        gl[:S, :S] = glow
        for f in extra_faces:
            f.uv = np.full((len(f.verts), 2), S + 4.5)
        faces = faces + extra_faces
        atlas, glow = big, gl
    # 2 倍で描いて縮める (遠目の縮尺だと平らな面にまで奥行きの線が出るため)
    im = render(faces, atlas, glow, yaw, pitch, (size[0] * 2, size[1] * 2), center, scale * 2)
    return im.resize(size, Image.LANCZOS)


def riders():
    """6 人の座る灰色の箱 (幅 0.6、座高 1.2 ブロック): 胴・頭・腿。前 = -Z。"""
    F = []
    for sx, sz in SEATS:
        b = V(sx, SEAT_Y, sz)
        F += fbox("rider", "rider", b + V(0, 6.0, 1.0), (9.6, 12.0, 5.0))       # 胴 (腰から肩)
        F += fbox("rider", "rider", b + V(0, 15.6, 1.0), (7.0, 7.2, 7.0))       # 頭 (座高 1.2 = 19.2)
        F += fbox("rider", "rider", b + V(0, 2.0, -4.0), (9.6, 4.0, 8.0))       # 腿
    return F


def bgpanel(w, h):
    return Image.fromarray(np.clip(voxel._background(w, h, "dark"), 0, 255).astype(np.uint8))


def on_bg(im):
    bg = bgpanel(*im.size)
    bg.paste(im, (0, 0), im)
    return bg


# ====================================================================== 確かめ

def open_edges(data):
    """書き出したメッシュの開いた辺 (三角形 1 枚にしか属さない辺) の数を骨ごとに (titan_mesh_audit と同じ数え方)。"""
    per = Counter()
    for bn, b in data["bones"].items():
        v = b["vertices"]
        edges = Counter()
        for t in b["triangles"]:
            for a, c in ((0, 1), (1, 2), (2, 0)):
                p = tuple(round(x, 2) for x in v[t[a]])
                q = tuple(round(x, 2) for x in v[t[c]])
                if p != q:
                    edges[tuple(sorted((p, q)))] += 1
        for k, c in edges.items():
            if c == 1:
                per[bn] += 1
    return per


def seat_heights(data, step=(3.2, 6.4)):
    """鞍の範囲の格子で、上から下ろした線が最初に当たる面の高さ (単位)。geo 空間 (範囲は左右前後対称なのでそのまま)。"""
    tris = []
    for b in data["bones"].values():
        v = np.array(b["vertices"], float)
        for t in b["triangles"]:
            tris.append(v[t])
    T = np.array(tris)
    rx, rz = G.SADDLE_REGION
    hs = []
    for x in np.arange(-rx, rx + 1e-6, step[0]):
        for z in np.arange(-rz, rz + 1e-6, step[1]):
            a, b, c = T[:, 0], T[:, 1], T[:, 2]
            den = (b[:, 2] - c[:, 2]) * (a[:, 0] - c[:, 0]) + (c[:, 0] - b[:, 0]) * (a[:, 2] - c[:, 2])
            ok = np.abs(den) > 1e-9
            den = np.where(ok, den, 1)
            l0 = ((b[:, 2] - c[:, 2]) * (x - c[:, 0]) + (c[:, 0] - b[:, 0]) * (z - c[:, 2])) / den
            l1 = ((c[:, 2] - a[:, 2]) * (x - c[:, 0]) + (a[:, 0] - c[:, 0]) * (z - c[:, 2])) / den
            l2 = 1 - l0 - l1
            ins = ok & (l0 >= -1e-6) & (l1 >= -1e-6) & (l2 >= -1e-6)
            y = l0 * a[:, 1] + l1 * b[:, 1] + l2 * c[:, 1]
            hs.append(float(y[ins].max()) if ins.any() else float("nan"))
    return np.array(hs)


def report(r):
    data = r["data"]
    oe = open_edges(data)
    hs = seat_heights(data)
    lo = np.min([f.verts.min(0) for f in r["body"]], 0)
    hi = np.max([f.verts.max(0) for f in r["body"]], 0)
    return dict(tris=data["triangle_count"], open_edges=sum(oe.values()), open_bones=dict(oe), atlas=r["size"],
                seat_min=float(np.nanmin(hs)), seat_max=float(np.nanmax(hs)), seat_nan=int(np.isnan(hs).sum()),
                lo=(lo / 16).round(2).tolist(), hi=(hi / 16).round(2).tolist())
