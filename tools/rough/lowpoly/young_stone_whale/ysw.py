"""岩鯨の仔 (young_stone_whale) 低ポリ ラフ 3 案の共通部分。

座標: 1 単位 = 1/16 ブロック。作図は「前 = -Z」。書き出すとき (x, z) の符号を反転して geo の空間へ
(geo の root は Y 軸 180 度回る。最後の姿の前は -Z)。作図の +x = ゲームの右 (fin_r)。
骨は 3 案で共通 (名前・親・pivot)。ゲームの箱の geo (tools/gen/world/gen_young_stone_whale_assets.py) と一つの定義。
鞍の上面は 3 案とも SEAT_Y = 58 単位で、x ±25.6・z ±51.2 の範囲は平ら (座席 6 つ)。

絵の具 (paint / eye_marks / export_mesh) は潮招きの common.py をそのまま借りる (真珠守りと同じ)。
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
sys.path.insert(0, str(ROOT / "tools" / "gen/world"))
from lpkit import *  # noqa
from lpkit import _mk  # noqa
import voxel  # noqa

_spec = importlib.util.spec_from_file_location("tb_common", LP / "tide_beckoner" / "common.py")
tbc = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(tbc)

import gen_young_stone_whale_assets as G  # noqa: E402

OUT = ROOT / "docs/review/young_roughs"
FOLDER = HERE / "out"   # 案ごとのメッシュ・アトラス・光の型 (取り込みの入力)
flipxz = tbc.flipxz
SKELETON_DRAW = G.SKELETON_DRAW
SEAT_Y = G.SEAT_Y
BONES = {k: (p, [float(x) + 0.0 for x in flipxz(v)]) for k, (p, v) in SKELETON_DRAW.items()}


def pv(bone):
    return np.array(SKELETON_DRAW[bone][1], float)


def V(*a):
    return np.array(a, float)


# ====================================================================== 形の部品

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
    """面取りの箱 (下の面 → 上の面の 2 輪)。上の面は c.y + sy/2 の高さで平ら。"""
    sx, sy, sz = size
    R = np.eye(3) if R is None else R
    ex, ey, ez = (R @ np.array(a) for a in ((1, 0, 0), (0, 1, 0), (0, 0, 1)))
    c = np.asarray(c, float)
    r0 = rrect(c - ey * sy / 2, ex, ez, sx, sz, cut)
    r1 = rrect(c + ey * sy / 2, ex, ez, sx * taper[0], sz * taper[1], cut)
    return rloft(bone, mat, [r0, r1], caps)


def sp(bone, mat, base, d, length, r, sides=4, rings=1, bend=(0, 0, 0)):
    """閉じた棘 (根元の輪を板で塞ぐ。lpkit.spike は根元が開いているので使わない)。"""
    d = nrm(np.asarray(d, float))
    base = np.asarray(base, float)
    path = [base + d * length * (i / rings) + np.asarray(bend, float) * (i / rings) ** 2 for i in range(rings + 1)]
    ras = [r * max(0.0, 1 - i / rings) ** 0.8 for i in range(rings + 1)]
    ras[-1] = 0.0
    return tube(bone, mat, path, ras, sides=sides, hint=(0, 1, 0) if abs(d[1]) < 0.9 else (1, 0, 0), phase=45.0,
                caps=(True, False))


def xsec(z, cy, hh, hw, n=12, belly=0.85, top=1.0, cx=0.0):
    """鯨の胴の輪切り (z 一定の面の n 角形)。0 番が真上。belly < 1 で腹を平たく、top < 1 で背を平たく。"""
    pts = []
    for k in range(n):
        a = 2 * math.pi * k / n
        s, c = math.sin(a), math.cos(a)
        y = (top if c > 0 else belly) * hh * math.copysign(abs(c) ** 0.85, c)
        x = hw * math.copysign(abs(s) ** 0.85, s)
        pts.append(np.array([cx + x, cy + y, z]))
    return pts


def hull(bone, mat, keys, n=12, belly=0.85, top=1.0, mats=None):
    """keys = [(z, cy, hh, hw)] 前から後ろへ。両端は板で塞ぐ (閉じた形)。"""
    rings = [xsec(z, cy, hh, hw, n, belly, top) for z, cy, hh, hw in keys]
    return rloft(bone, mat, rings, (True, True), mats)


def hull_at(keys, z):
    """keys の z での (cy, hh, hw) (線形補間)。"""
    for (z0, *a), (z1, *b) in zip(keys, keys[1:]):
        if z0 <= z <= z1:
            u = (z - z0) / (z1 - z0)
            return tuple(np.array(a) * (1 - u) + np.array(b) * u)
    return tuple(keys[0][1:] if z < keys[0][0] else keys[-1][1:])


def flat_blade(bone, mat, path, width, thick, hint=(0, 1, 0)):
    """平たい刃 (ひれ・尾びれ)。width は幅 (hint と直交する向き)、thick は厚み (hint の向き)。先端の幅 0 で尖る。"""
    return tube(bone, mat, path, list(thick), list(width), sides=6, hint=hint, phase=0.0, caps=(True, True))


def oval_wall(bone, mat, cy0, cy1, ax, az, thick, n=20, cx=0.0, cz=0.0, flare=0.0, rim_wave=0.0, waves=0, mats=None,
              p=2.0, top_fn=None):
    """楕円 (p > 2 で角の丸い四角) の輪の壁。閉じた形で断面は四角。内側の半径 (ax, az)、y は cy0..cy1。
    flare で上を外へ開く。rim_wave と waves で上の縁を波打たせる (帆立の縁)。
    mats の順: [下の面, 外の面, 上の面 (縁), 内の面]。"""
    secs = []
    e = 2.0 / p
    for k in range(n):
        a = 2 * math.pi * k / n
        s, c = math.sin(a), -math.cos(a)
        x = ax * math.copysign(abs(s) ** e, s)
        z = az * math.copysign(abs(c) ** e, c)
        gx = math.copysign(abs(x / ax) ** (p - 1), x) / ax
        gz = math.copysign(abs(z / az) ** (p - 1), z) / az
        o = nrm(np.array([gx, 0, gz]))
        p0 = np.array([cx + x, 0, cz + z])
        top = cy1 + (rim_wave * math.cos(waves * a) if waves else 0.0) + (top_fn(a, x, z) if top_fn else 0.0)
        fl = flare * (top - cy0) / max(cy1 - cy0, 1e-6)
        pts = [p0 + V(0, cy0, 0), p0 + o * thick + V(0, cy0, 0), p0 + o * (thick + fl) + V(0, top, 0),
               p0 + o * fl + V(0, top, 0)]
        secs.append(pts)
    F = []
    for k in range(n):
        A, B = secs[k], secs[(k + 1) % n]
        ref = (np.mean(A, 0) + np.mean(B, 0)) / 2
        for j in range(4):
            q = [A[j], A[(j + 1) % 4], B[(j + 1) % 4], B[j]]
            f = _mk(q, bone, mats[j] if mats else mat, ref)
            if f is not None:
                F.append(f)
    return F, secs


def chain(bone, mat, a, b, links=4, r=1.6):
    """鎖: a → b に交互に向きを変えた小さな輪 (つぶした八面体) を並べる。"""
    a, b = np.asarray(a, float), np.asarray(b, float)
    d = b - a
    L = np.linalg.norm(d)
    F = []
    for i in range(links):
        c = a + d * (i + 0.5) / links
        rot = rot_y(90 * (i % 2))
        F += ellipsoid(bone, mat, c, (r * 0.55, L / links * 0.62, r), segs=4, rings=1, rot=rot)
    return F


def anchor_stone(bone, mat, top, size=6.0, mat2=None):
    """小さな錨石: 鎖の先の四角い石 + 下の二つの爪。"""
    top = np.asarray(top, float)
    F = fbox(bone, mat, top - V(0, size * 0.55, 0), (size * 0.9, size * 1.1, size * 0.7), taper=(0.7, 0.7), cut=0.35)
    for s in (1, -1):
        F += sp(bone, mat2 or mat, top - V(0, size * 0.95, 0), (s * 0.8, -0.2, 0), size * 0.75, size * 0.22, sides=4)
    return F


def cloud_puffs(bone="cloud", mat="cloud", puffs=None):
    c = pv(bone)
    puffs = puffs or [((0, 0, 0), (16, 3.6, 18)), ((10, -0.5, 18), (10, 3, 11)), ((-9, 0, -12), (10, 3, 10)),
                      ((-4, 0.5, 26), (8, 3, 8))]
    F = []
    for off, r in puffs:
        F += ellipsoid(bone, mat, c + V(*off), r, segs=8, rings=3)
    return F


# ====================================================================== 絵の具 (共通の色)

STONE = [(40, 44, 54), (52, 56, 68), (74, 80, 94), (100, 106, 120), (124, 128, 138)]
STONE_W = [(70, 66, 66), (96, 92, 92), (112, 108, 108), (136, 132, 130), (158, 154, 150)]
BELLY = [(84, 74, 64), (104, 92, 80), (122, 110, 98), (146, 134, 116), (170, 158, 138)]
MOSS = [(52, 74, 44), (72, 100, 54), (98, 128, 66)]
BONE = [(120, 112, 98), (176, 168, 150), (214, 206, 186), (240, 232, 214)]
DECK = [(96, 84, 70), (138, 124, 102), (172, 158, 130), (200, 188, 160)]
TEAL = [(40, 150, 160), (100, 226, 230), (200, 252, 250)]
CLOUD = [(170, 182, 196), (204, 214, 226), (228, 236, 244), (248, 250, 254)]
EYE = [(8, 10, 16), (18, 20, 28), (32, 34, 44)]
CHAIN = [(30, 34, 42), (54, 60, 70), (84, 90, 100), (110, 116, 126)]

BASE_MATS = {
    "stone": dict(ramp=STONE, base=0.58, lit=0.30, noise=0.10, crack=0.14, min=0, streak=0.06,
                  stain=dict(ramp=MOSS, thr=0.93, up=0.35, conc=0.2, low=0.0)),
    "stone2": dict(ramp=STONE, base=0.50, lit=0.30, noise=0.08, crack=0.10, min=0),
    "plate": dict(ramp=STONE_W, base=0.56, lit=0.30, noise=0.08, crack=0.18, min=0, ao=0.1),
    "belly": dict(ramp=BELLY, base=0.58, lit=0.25, noise=0.06, crack=0.0, min=0),
    "bone": dict(ramp=BONE, base=0.62, lit=0.30, noise=0.06, crack=0.10, min=0, streak=0.1),
    "deck": dict(ramp=DECK, base=0.60, lit=0.20, noise=0.06, crack=0.0, min=0, stripe=7, axis="u"),
    "chain": dict(ramp=CHAIN, base=0.55, lit=0.35, noise=0.05, crack=0.0, min=0),
    "eye": dict(ramp=EYE, base=0.5, lit=0.2, noise=0.05, crack=0, min=0),
    "mote": dict(ramp=TEAL, base=0.55, lit=0.40, noise=0.05, crack=0, min=0),
    "glowbone": dict(ramp=[(90, 200, 204), (150, 240, 240), (210, 255, 252)], base=0.55, lit=0.3, noise=0.04, crack=0, min=0),
    "cloud": dict(ramp=CLOUD, base=0.62, lit=0.35, noise=0.06, crack=0, min=0, ao=0.0),
    "barn": dict(ramp=[(110, 104, 96), (160, 152, 140), (200, 194, 180), (226, 220, 206)], base=0.55, lit=0.32,
                 noise=0.12, crack=0.1, min=0),
    "shell": dict(ramp=[(88, 86, 84), (128, 124, 118), (166, 160, 148), (200, 194, 178)], base=0.58, lit=0.30, noise=0.08,
                  crack=0.14, min=0, stripe=5, axis="v",
                  stain=dict(ramp=MOSS, thr=0.95, up=0.2, conc=0.3, low=0.0)),
    "rim": dict(ramp=BONE, base=0.58, lit=0.30, noise=0.06, crack=0.06, min=0),
}


def whale_extra(mouth_z=(-86, -54), mouth_y0=27.0, belly_cut=-0.25):
    """胴の下向きの面を腹の砂色にして畝 (喉の溝) を引き、口の線を描く。"""
    belly_r = np.array(BELLY, float)
    dark = np.array([(40, 26, 26), (70, 36, 34)], float)

    def ex(c, col, glow):
        P, N, mats, bones, bay, lit = c["P"], c["N"], c["mats"], c["bones"], c["bay"], c["lit"]
        on = (mats == "stone") & (bones == "body") & c["owned"]
        down = on & (N[..., 1] < belly_cut + 0.06 * bay)
        v = 0.58 + 0.25 * lit + 0.04 * bay
        idx = np.clip((v * len(belly_r)).astype(int), 0, len(belly_r) - 1)
        groove = (np.abs(((P[..., 0] + 100) % 5.0) - 2.5) < 0.55) & (P[..., 2] < 20)
        idx = np.where(groove, np.maximum(idx - 1, 0), idx)
        col = np.where(down[..., None], belly_r[idx], col)
        # 口の線 (前から後ろへわずかに上がる)
        z = P[..., 2]
        my = mouth_y0 + (z - mouth_z[0]) * 0.06
        m = on & (z > mouth_z[0]) & (z < mouth_z[1]) & (np.abs(P[..., 1] - my) < 0.75) & (np.abs(N[..., 0]) > 0.15)
        m |= on & (z <= mouth_z[0] + 3) & (np.abs(P[..., 1] - my) < 0.75)
        col = np.where(m[..., None], dark[0], col)
        col = np.where((m & (P[..., 1] > my))[..., None], dark[1], col)
        return col, glow
    return ex


def chain_extra(*fns):
    def ex(c, col, glow):
        for f in fns:
            col, glow = f(c, col, glow)
        return col, glow
    return ex


# ====================================================================== 姿勢

def pose_point(p, bone, pose):
    """作図空間の点 → geo 空間で姿勢をかけて → 作図空間へ。root の拡大も含める。"""
    return flipxz(G.pose_point_geo(flipxz(p), bone, pose))


def pose_body(faces, pose):
    out = []
    for f in faces:
        g = copy.copy(f)
        g.verts = np.array([pose_point(p, f.bone, pose) for p in f.verts])
        tbc._refresh(g, f)
        out.append(g)
    return out


# ====================================================================== 書き出し

def fit_atlas(body, size):
    """テクセルの密度 (lpkit.TEX) を上げて、島がちょうど size の正方形に収まるようにする (アトラスを広く使う)。
    収まる一番高い密度を二分で探す。書き出しの texels_per_unit (tbc.TEX) も同じ値にする。"""
    import lpkit
    lo, hi = 1.0, 8.0
    for _ in range(12):
        mid = (lo + hi) / 2
        lpkit.TEX = mid
        if _pack_size(body, size) <= size:
            lo = mid
        else:
            hi = mid
    lpkit.TEX = tbc.TEX = round(lo, 3)
    return build_atlas(body, size, 0)


def _pack_size(faces, size):
    """build_atlas と同じ詰め方で、size に収まるかだけを調べる (テクセルの配列は作らない)。"""
    import lpkit
    charts = []
    for f in faces:
        n = f.normal
        u = nrm(f.verts[1] - f.verts[0])
        w = np.cross(n, u)
        q = np.array([[np.dot(p - f.verts[0], u), np.dot(p - f.verts[0], w)] for p in f.verts]) * lpkit.TEX
        q = q - q.min(0)
        charts.append((max(1, int(math.ceil(q[:, 0].max() - 1e-6))), max(1, int(math.ceil(q[:, 1].max() - 1e-6)))))
    x = y = rowh = 0
    for cw, ch in sorted(charts, key=lambda c: (-c[1], -c[0])):
        if x + cw > size:
            x, y, rowh = 0, y + rowh, 0
        if y + ch > size or cw > size:
            return size * 2
        x += cw
        rowh = max(rowh, ch)
    return size


def build_option(key, build, pal, body_hint=512, fit=None):
    body, eyes, extra = build()
    d = FOLDER / key
    d.mkdir(parents=True, exist_ok=True)
    if fit:
        size, P, N, M, L = fit_atlas(body, fit)
    else:
        import lpkit
        lpkit.TEX = tbc.TEX = 2.0
        size, P, N, M, L = build_atlas(body, body_hint, 0)

    def ex(c, col, glow):
        if eyes:
            col, glow = tbc.eye_marks(c, col, glow, eyes, pupil=np.array([(110, 220, 220), (210, 255, 250)]))
        if extra:
            col, glow = extra(c, col, glow)
        return col, glow
    atlas, glow = tbc.paint(body, size, P, N, M, pal, (), ex)
    stem = f"young_stone_whale_{key}"
    Image.fromarray(atlas, "RGBA").save(d / f"{stem}_atlas.png")
    gm = np.zeros((size, size, 4), np.uint8)
    gm[glow] = (255, 255, 255, 255)
    Image.fromarray(gm, "RGBA").save(d / f"{stem}_glowmask.png")
    data = tbc.export_mesh(body, size, d / f"{stem}_mesh.json", stem, f"{stem}_atlas.png", BONES,
                           {"glowmask": f"{stem}_glowmask.png",
                            "seat_y": SEAT_Y,
                            "note": "geo space (root rotated 180 deg about Y: root carries rotation [0,180,0]); "
                                    "vertices are absolute like geo cubes; 9 bones = geo/entity/young_stone_whale.geo.json; "
                                    "saddle top flat at y=58 over x +-25.6, z +-51.2 (6 seats)"})
    return dict(key=key, body=body, eyes=eyes, size=size, atlas=atlas, glow=glow, data=data)


def open_edges(data):
    """書き出したメッシュの開いた辺 (骨ごと、頂点を小数 2 桁で丸めて数える。titan_mesh_audit と同じ)。"""
    per = Counter()
    for bn, b in data["bones"].items():
        v = b["vertices"]
        edges = Counter()

        def key(i):
            return tuple(round(c, 2) for c in v[i])
        for t in b["triangles"]:
            for a, c in ((0, 1), (1, 2), (2, 0)):
                edges[tuple(sorted((key(t[a]), key(t[c]))))] += 1
        per[bn] = sum(1 for k, c in edges.items() if c == 1)
    return per


def seat_heights(faces, step=3.2):
    """鞍の範囲 (x ±25.6, z ±51.2) を格子で上から見て、一番上の面の高さ (作図空間)。"""
    hx, hz = G.SEAT_RECT
    tris = []
    for f in faces:
        for a, b, c in f.tris():
            tris.append(f.verts[[a, b, c]])
    T = np.array(tris)
    out = []
    for x in np.arange(-hx + 0.4, hx - 0.39, step):
        for z in np.arange(-hz + 0.4, hz - 0.39, step):
            A, B, C = T[:, 0], T[:, 1], T[:, 2]
            den = (B[:, 2] - C[:, 2]) * (A[:, 0] - C[:, 0]) + (C[:, 0] - B[:, 0]) * (A[:, 2] - C[:, 2])
            ok = np.abs(den) > 1e-9
            den = np.where(ok, den, 1)
            l0 = ((B[:, 2] - C[:, 2]) * (x - C[:, 0]) + (C[:, 0] - B[:, 0]) * (z - C[:, 2])) / den
            l1 = ((C[:, 2] - A[:, 2]) * (x - C[:, 0]) + (A[:, 0] - C[:, 0]) * (z - C[:, 2])) / den
            l2 = 1 - l0 - l1
            ins = ok & (l0 >= -1e-6) & (l1 >= -1e-6) & (l2 >= -1e-6)
            y = l0 * A[:, 1] + l1 * B[:, 1] + l2 * C[:, 1]
            out.append(float(y[ins].max()) if ins.any() else float("nan"))
    return np.array(out)


def render_pose(r, pose, yaw, pitch, size, center, scale, extra_faces=None):
    faces = pose_body(r["body"], pose or {})
    atlas, glow = r["atlas"], r["glow"]
    if extra_faces:
        S = atlas.shape[0]
        a2 = np.zeros((2 * S, 2 * S, 4), np.uint8)
        a2[:S, :S] = atlas
        a2[S:, S:] = (128, 130, 140, 255)
        g2 = np.zeros((2 * S, 2 * S), bool)
        g2[:S, :S] = glow
        ef = []
        for f in extra_faces:
            g = copy.copy(f)
            g.uv = np.full((len(f.verts), 2), S + 4.5)
            ef.append(g)
        faces = faces + ef
        atlas, glow = a2, g2
    return render(faces, atlas, glow, yaw, pitch, size, center, scale)


def riders():
    """座席の上に座る人の影 (幅 0.6、座って 1.2 ブロックの灰色の箱)。"""
    F = []
    for x, y, z in G.SEATS:
        F += fbox("root", "rider", (x, y + 9.6, z), (9.6, 19.2, 9.6))
        F += fbox("root", "rider", (x, y + 2.4, z - 6.0), (9.6, 4.8, 6.0))   # 前へ出した膝
    return F


def bgpanel(w, h):
    return Image.fromarray(np.clip(voxel._background(w, h, "dark"), 0, 255).astype(np.uint8))


def on_bg(im):
    bg = bgpanel(*im.size)
    bg.paste(im, (0, 0), im)
    return bg
