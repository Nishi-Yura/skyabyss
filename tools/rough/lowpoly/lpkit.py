"""低ポリ (非立方体) モデルの小さな道具一式。

- プリミティブ: tube (断面を持つ筒。骨・肋骨・牙・棘・面取り箱), ellipsoid (面の見える楕円体),
  grid (板)。すべて「面 (3〜4 頂点の多角形)」の一覧を返す。
- build_atlas: 面ごとに平らに展開した島を作り、一定の密度 (1 単位 = TEX テクセル) で
  アトラスへ詰める。各テクセルの 3D 位置と法線も保持するので、絵は 3D 位置から手続きで描ける。
- render: z バッファ + 最近傍サンプリング + 面ごとの平らな陰影の正射影ソフトレンダラ。

座標: 1 単位 = 1/16 ブロック。y が上、モデルの正面は -Z。
"""
from __future__ import annotations

import math
from dataclasses import dataclass, field

import numpy as np
from PIL import Image

TEX = 2.0  # 1 単位あたりのテクセル数 (= 1 ブロック 32 テクセル)


def nrm(v):
    v = np.asarray(v, float)
    n = np.linalg.norm(v)
    return v / n if n > 1e-12 else v


def rot_x(a):
    c, s = math.cos(math.radians(a)), math.sin(math.radians(a))
    return np.array([[1, 0, 0], [0, c, -s], [0, s, c]])


def rot_y(a):
    c, s = math.cos(math.radians(a)), math.sin(math.radians(a))
    return np.array([[c, 0, s], [0, 1, 0], [-s, 0, c]])


def rot_z(a):
    c, s = math.cos(math.radians(a)), math.sin(math.radians(a))
    return np.array([[c, -s, 0], [s, c, 0], [0, 0, 1]])


@dataclass
class Face:
    verts: np.ndarray            # (k,3) 3 or 4 vertices, model space
    bone: str
    mat: str
    ref: np.ndarray              # 外向き判定の基準点
    loc: np.ndarray = None       # 絵を描くための局所座標 (省略なら verts)
    normal: np.ndarray = None
    uv: np.ndarray = None        # (k,2) texel units (v は画像の上から)
    chart: tuple = None          # (x, y, w, h) 余白を除く島の矩形
    frame: tuple = None

    def tris(self):
        return [(0, i, i + 1) for i in range(1, len(self.verts) - 1)]


def _dedupe(pts, eps=1e-6):
    out = []
    for p in pts:
        if not out or np.linalg.norm(p - out[-1]) > eps:
            out.append(p)
    if len(out) > 1 and np.linalg.norm(out[0] - out[-1]) <= eps:
        out.pop()
    return out


def _mk(pts, bone, mat, ref, loc=None):
    pts = _dedupe([np.asarray(p, float) for p in pts])
    if len(pts) < 3:
        return None
    v = np.array(pts)
    if len(v) == 4:
        n = np.cross(v[2] - v[0], v[3] - v[1])
    else:
        n = np.cross(v[1] - v[0], v[2] - v[0])
    if np.linalg.norm(n) < 1e-9:
        return None
    n = nrm(n)
    fc = v.mean(0)
    if np.dot(n, fc - np.asarray(ref, float)) < 0:
        n = -n
        v = v[::-1].copy()
    f = Face(v, bone, mat, np.asarray(ref, float), None, n)
    f.loc = v if loc is None else loc
    return f


# ------------------------------------------------------------------ 筒

def tube(bone, mat, path, ra, rb=None, sides=4, hint=(0, 1, 0), phase=45.0, caps=(False, False),
         axis_fn=None, hide_cos=None, ridge_out=False, mats=None):
    """path に沿った筒。断面は半径 (ra: 'a' 方向, rb: 'b' 方向) の正 n 角形。
    ra, rb は点ごとの配列かスカラー。先端の半径が 0 なら尖った先になる。
    axis_fn(p) は p の属する中心軸上の点 (外向き判定と hide 用)。
    hide_cos: 中心軸の側を向く面 (法線と軸方向の cos がこれ以上) を作らない。"""
    path = [np.asarray(p, float) for p in path]
    n = len(path)
    ra = np.broadcast_to(np.asarray(ra, float), (n,)).copy()
    rb = ra if rb is None else np.broadcast_to(np.asarray(rb, float), (n,)).copy()
    hint = np.asarray(hint, float)

    def rings(ph):
        out = []
        for i, p in enumerate(path):
            t = nrm(path[min(i + 1, n - 1)] - path[max(i - 1, 0)])
            h = hint if abs(np.dot(nrm(hint), t)) < 0.98 else np.array([1.0, 0, 0])
            a = nrm(h - np.dot(h, t) * t)
            b = np.cross(t, a)
            ring = [p + ra[i] * math.cos(math.radians(ph + 360.0 * k / sides)) * a
                    + rb[i] * math.sin(math.radians(ph + 360.0 * k / sides)) * b for k in range(sides)]
            out.append(ring)
        return out

    R = rings(phase)
    if ridge_out and axis_fn is not None:
        mid = n // 2
        c = path[mid]
        outv = c - axis_fn(c)
        best = max(range(sides), key=lambda k: np.dot(R[mid][k] - c, outv))
        if best != 0:
            R = rings(phase + 360.0 * best / sides)  # 頂点 0 が外を向くようにずらす
        if np.dot(R[mid][0] - c, outv) < 0:
            R = rings(phase + 180.0)
    faces = []
    for i in range(n - 1):
        ref_pt = (path[i] + path[i + 1]) / 2
        ref = ref_pt
        for k in range(sides):
            quad = [R[i][k], R[i][(k + 1) % sides], R[i + 1][(k + 1) % sides], R[i + 1][k]]
            f = _mk(quad, bone, (mats[k % len(mats)] if mats else mat), ref)
            if f is None:
                continue
            if hide_cos is not None and axis_fn is not None:
                d = nrm(axis_fn(ref_pt) - f.verts.mean(0))
                if np.dot(f.normal, d) > hide_cos:
                    continue
            faces.append(f)
    for idx, cap in zip((0, n - 1), caps):
        if cap:
            f = _mk(R[idx], bone, mat, path[idx] + (path[idx] - path[1 if idx == 0 else n - 2]) * -1)
            if f:
                faces.append(f)
    return faces


def spike(bone, mat, base, direction, length, r, sides=4, rings=2, bend=(0, 0, 0), hint=(0, 1, 0),
          phase=45.0, rb=None, taper=0.55):
    """根元 base から direction へ伸びる先細りの棘。bend は先端へ向けた曲がり。"""
    d = nrm(direction)
    base = np.asarray(base, float)
    path, ras = [], []
    for i in range(rings + 1):
        t = i / rings
        path.append(base + d * length * t + np.asarray(bend, float) * t * t)
        ras.append(0.0 if i == rings else r * (1 - t) ** 0.9 * (1 - (1 - taper) * 0) if i else r)
    ras = [r * max(0.0, 1 - i / rings) ** 0.8 if i < rings else 0.0 for i in range(rings + 1)]
    ras[0] = r
    rbv = None if rb is None else [rb * x / r for x in ras]
    return tube(bone, mat, path, ras, rbv, sides=sides, hint=hint, phase=phase,
                axis_fn=lambda p: base - d * 1.0)


# ------------------------------------------------------------------ 楕円体

def ellipsoid(bone, mat, center, radii, segs=8, rings=3, rot=None, phase=None, deform=None):
    """面の見える楕円体。rings は極を除く緯度の輪の数。正面 (-Z) の真ん中に面の中心が来る。"""
    center = np.asarray(center, float)
    R = np.eye(3) if rot is None else rot
    rx, ry, rz = radii
    ph = 180.0 / segs if phase is None else phase

    def pt(lat, k):
        phi = math.radians(ph + 360.0 * k / segs)
        L = math.radians(lat)
        p = np.array([math.cos(L) * math.sin(phi) * rx, math.sin(L) * ry, -math.cos(L) * math.cos(phi) * rz])
        if deform:
            p = deform(p)
        return R @ p + center

    lats = [-90 + 180.0 * (j + 1) / (rings + 1) for j in range(rings)]
    faces = []
    top, bot = center + R @ np.array([0, ry, 0]), center - R @ np.array([0, ry, 0])
    if deform:
        top = R @ deform(np.array([0, ry, 0.0])) + center
        bot = R @ deform(np.array([0, -ry, 0.0])) + center
    for k in range(segs):
        k2 = (k + 1) % segs
        faces.append(_mk([bot, pt(lats[0], k2), pt(lats[0], k)], bone, mat, center))
        faces.append(_mk([top, pt(lats[-1], k), pt(lats[-1], k2)], bone, mat, center))
        for j in range(rings - 1):
            faces.append(_mk([pt(lats[j], k), pt(lats[j], k2), pt(lats[j + 1], k2), pt(lats[j + 1], k)],
                             bone, mat, center))
    return [f for f in faces if f]


def grid(bone, mat, pts, ref, loc_pts=None):
    """pts[row][col] の格子を四角形の板にする。行がつぶれていれば三角形になる。"""
    faces = []
    rows, cols = len(pts), len(pts[0])
    for r in range(rows - 1):
        for c in range(cols - 1):
            q = [pts[r][c], pts[r][c + 1], pts[r + 1][c + 1], pts[r + 1][c]]
            lq = None
            if loc_pts is not None:
                lq = [loc_pts[r][c], loc_pts[r][c + 1], loc_pts[r + 1][c + 1], loc_pts[r + 1][c]]
            f = _mk(q, bone, mat, ref, None)
            if f is not None:
                if lq is not None:
                    # 頂点順が反転した場合に合わせて局所座標も対応させる
                    lp = {tuple(np.round(a, 6)): b for a, b in zip(q, lq)}
                    f.loc = np.array([lp[tuple(np.round(v, 6))] for v in f.verts])
                faces.append(f)
    return faces


def poly(bone, mat, pts, ref):
    f = _mk(pts, bone, mat, ref)
    return [f] if f else []


def transform(faces, R=None, t=(0, 0, 0), keep_loc=True):
    R = np.eye(3) if R is None else R
    t = np.asarray(t, float)
    for f in faces:
        if keep_loc and f.loc is f.verts:
            f.loc = f.verts.copy()
        f.verts = f.verts @ R.T + t
        f.ref = R @ f.ref + t
        f.normal = R @ f.normal
    return faces


def tri_count(faces):
    return sum(len(f.verts) - 2 for f in faces)


# ------------------------------------------------------------------ 輪切りをつなぐ形 (頭蓋骨・骨盤)

def loft(bone, mat, rings, caps=(False, False), mats=None):
    """同じ点数の輪 (閉じた多角形) を順につなぐ。輪の中心を外向き判定の基準にするので、
    輪の頂点を内側へ引くと凹み (眼窩など) になる。caps=(最初の輪, 最後の輪) を板で塞ぐ。"""
    rings = [np.array(r, float) for r in rings]
    cen = [r.mean(0) for r in rings]
    k = len(rings[0])
    faces = []
    for i in range(len(rings) - 1):
        ref = (cen[i] + cen[i + 1]) / 2
        for j in range(k):
            quad = [rings[i][j], rings[i][(j + 1) % k], rings[i + 1][(j + 1) % k], rings[i + 1][j]]
            f = _mk(quad, bone, (mats[i] if mats else mat), ref)
            if f is not None:
                faces.append(f)
    for idx, cap in zip((0, len(rings) - 1), caps):
        if cap:
            other = cen[1] if idx == 0 else cen[-2]
            f = _mk(list(rings[idx]), bone, cap if isinstance(cap, str) else mat, other)
            if f is not None:
                faces.append(f)
    return faces


def ring(y, rx, zf, zb, zc=0.0, n=8, cx=0.0):
    """前 (-Z) の頂点が 0 番の n 角形の輪。zf < 0 は前の出っ張り、zb > 0 は後ろの出っ張り。"""
    pts = []
    for k in range(n):
        a = math.radians(360.0 * k / n)
        s, c = math.sin(a), math.cos(a)
        pts.append(np.array([cx + rx * s, y, zc + (zf * c if c >= 0 else zb * -c)]))
    return pts


# ------------------------------------------------------------------ 面の縁の情報 (絵を手で描いた風にするため)

def edge_fields(faces, size, pad=0):
    """アトラスの各テクセルについて、面の縁までの距離 D (テクセル)、一番近い縁の折れ曲がり K
    (度。正は出っ張る角、負は凹む角)、島の中の座標 LU, LV、島の大きさ CW, CH、多角形の内側か INS を返す。"""
    from collections import defaultdict
    D = np.full((size, size), 9.0)
    K = np.zeros((size, size))
    LU = np.zeros((size, size), int)
    LV = np.zeros((size, size), int)
    CW = np.ones((size, size), int)
    CH = np.ones((size, size), int)
    INS = np.zeros((size, size), bool)
    keyf = lambda p: tuple(np.round(p, 3))
    emap = defaultdict(list)
    fed = []
    for fi, f in enumerate(faces):
        k = len(f.verts)
        es = []
        for i in range(k):
            key = tuple(sorted((keyf(f.verts[i]), keyf(f.verts[(i + 1) % k]))))
            emap[key].append(fi)
            es.append(key)
        fed.append(es)
    cent = [f.verts.mean(0) for f in faces]
    for fi, f in enumerate(faces):
        k = len(f.verts)
        angs = []
        for i in range(k):
            others = [g for g in emap[fed[fi][i]] if g != fi]
            if not others:
                angs.append(80.0)
                continue
            best = 0.0
            mid = (f.verts[i] + f.verts[(i + 1) % k]) / 2
            for g in others:
                c = math.degrees(math.acos(max(-1, min(1, float(np.dot(f.normal, faces[g].normal))))))
                sg = 1.0 if np.dot(f.normal, cent[g] - mid) < -1e-6 else -1.0
                if c > abs(best):
                    best = sg * c
            angs.append(best)
        x0, y0, cw, ch = f.chart
        xs = np.arange(x0 - pad, x0 + cw + pad)
        ys = np.arange(y0 - pad, y0 + ch + pad)
        gx, gy = np.meshgrid(xs + 0.5, ys + 0.5)
        q = np.stack([gx, gy], -1).reshape(-1, 2)
        best = np.full(len(q), 1e9)
        kk = np.zeros(len(q))
        for i in range(k):
            A, B = f.uv[i], f.uv[(i + 1) % k]
            AB = B - A
            t = np.clip(((q - A) @ AB) / max(AB @ AB, 1e-9), 0, 1)
            d = np.linalg.norm(q - (A + t[:, None] * AB), axis=1)
            sel = d < best
            best[sel] = d[sel]
            kk[sel] = angs[i]
        ins = np.zeros(len(q), bool)
        for (a, b, c) in f.tris():
            A, B, C = f.uv[a], f.uv[b], f.uv[c]
            T = np.array([[B[0] - A[0], C[0] - A[0]], [B[1] - A[1], C[1] - A[1]]])
            if abs(np.linalg.det(T)) < 1e-9:
                continue
            bc = np.linalg.solve(T, (q - A).T).T
            w = np.stack([1 - bc[:, 0] - bc[:, 1], bc[:, 0], bc[:, 1]], -1)
            ins |= w.min(1) >= -1e-6
        yy = gy.reshape(-1).astype(int)
        xx = gx.reshape(-1).astype(int)
        D[yy, xx] = best
        K[yy, xx] = kk
        LU[yy, xx] = xx - x0
        LV[yy, xx] = yy - y0
        CW[yy, xx] = cw
        CH[yy, xx] = ch
        INS[yy, xx] = ins
    return D, K, LU, LV, CW, CH, INS


def fill_outside(img, faces, INS, M, pad=0):
    """島の外側 (余白と三角形の余り) を、同じ面の一番近い内側のテクセルの色で埋める。"""
    out = img.copy()
    for fi, f in enumerate(faces):
        x0, y0, cw, ch = f.chart
        sub = (slice(y0 - pad, y0 + ch + pad), slice(x0 - pad, x0 + cw + pad))
        ins = INS[sub]
        if ins.all() or not ins.any():
            continue
        iy, ix = np.nonzero(ins)
        oy, ox = np.nonzero(~ins)
        # chunked so a big chart never builds an (outside x inside) matrix of gigabytes
        step = max(1, 1_500_000 // max(1, len(iy)))
        nn = np.empty(len(oy), np.int64)
        for s0 in range(0, len(oy), step):
            d = (oy[s0:s0 + step, None] - iy[None]) ** 2 + (ox[s0:s0 + step, None] - ix[None]) ** 2
            nn[s0:s0 + step] = d.argmin(1)
        block = out[sub]
        block[oy, ox] = block[iy[nn], ix[nn]]
    return out


# ------------------------------------------------------------------ アトラス

def build_atlas(faces, size=128, pad=0):
    """面ごとの島を作ってアトラスに詰める。戻り値: (size, pos, nor, matid, owner) の配列。"""
    charts = []
    for fi, f in enumerate(faces):
        n = f.normal
        u = nrm(f.verts[1] - f.verts[0])
        w = np.cross(n, u)
        q = np.array([[np.dot(p - f.verts[0], u), np.dot(p - f.verts[0], w)] for p in f.verts]) * TEX
        lo = q.min(0)
        q = q - lo
        cw, ch = max(1, int(math.ceil(q[:, 0].max() - 1e-6))), max(1, int(math.ceil(q[:, 1].max() - 1e-6)))
        f.uv = q
        charts.append((fi, cw, ch))
    order = sorted(charts, key=lambda c: (-c[2], -c[1]))
    while True:
        x = y = rowh = 0
        place = {}
        ok = True
        for fi, cw, ch in order:
            W, H = cw + 2 * pad, ch + 2 * pad
            if x + W > size:
                x, y, rowh = 0, y + rowh, 0
            if y + H > size or W > size:
                ok = False
                break
            place[fi] = (x, y, cw, ch)
            x += W
            rowh = max(rowh, H)
        if ok:
            break
        size *= 2
    P = np.zeros((size, size, 3))
    N = np.zeros((size, size, 3))
    M = np.full((size, size), -1, int)
    O = np.full((size, size), -1, int)
    L = np.zeros((size, size, 3))
    for fi, (x, y, cw, ch) in place.items():
        f = faces[fi]
        f.chart = (x + pad, y + pad, cw, ch)
        f.uv = f.uv + np.array([x + pad, y + pad])
        # テクセル中心 → 面内の座標 → 3D
        xs = np.arange(x, x + cw + 2 * pad) + 0.5
        ys = np.arange(y, y + ch + 2 * pad) + 0.5
        gx, gy = np.meshgrid(xs, ys)
        q = np.stack([gx, gy], -1).reshape(-1, 2)
        pos = np.zeros((len(q), 3))
        loc = np.zeros((len(q), 3))
        best = np.full(len(q), -1e9)
        for (a, b, c) in f.tris():
            A, B, C = f.uv[a], f.uv[b], f.uv[c]
            T = np.array([[B[0] - A[0], C[0] - A[0]], [B[1] - A[1], C[1] - A[1]]])
            if abs(np.linalg.det(T)) < 1e-9:
                continue
            bc = np.linalg.solve(T, (q - A).T).T
            w = np.stack([1 - bc[:, 0] - bc[:, 1], bc[:, 0], bc[:, 1]], -1)
            score = w.min(1)
            sel = score > best
            best[sel] = score[sel]
            pos[sel] = w[sel] @ f.verts[[a, b, c]]
            loc[sel] = w[sel] @ f.loc[[a, b, c]]
        yy = (gy.reshape(-1)).astype(int)
        xx = (gx.reshape(-1)).astype(int)
        P[yy, xx] = pos
        L[yy, xx] = loc
        N[yy, xx] = f.normal
        M[yy, xx] = fi
    return size, P, N, M, L


def upscale_atlas(faces, size, k):
    """build_atlas で詰めた島の並びを、そのまま k 倍の解像度で取り直す (島の位置も大きさも k 倍)。
    正規化した UV は変わらないので、詰め直したメッシュ (閉じた面などを足したもの) にそのまま新しい絵を当てられる。
    絵は 3D 位置から描き直すので、拡大と違って細部が増える。戻り値は build_atlas と同じ (size*k, P, N, M, L)。"""
    S = size * k
    P = np.zeros((S, S, 3))
    N = np.zeros((S, S, 3))
    M = np.full((S, S), -1, int)
    L = np.zeros((S, S, 3))
    for fi, f in enumerate(faces):
        x, y, cw, ch = f.chart
        x, y, cw, ch = x * k, y * k, cw * k, ch * k
        f.chart = (x, y, cw, ch)
        f.uv = f.uv * k
        xs = np.arange(x, x + cw) + 0.5
        ys = np.arange(y, y + ch) + 0.5
        gx, gy = np.meshgrid(xs, ys)
        q = np.stack([gx, gy], -1).reshape(-1, 2)
        pos = np.zeros((len(q), 3))
        loc = np.zeros((len(q), 3))
        best = np.full(len(q), -1e9)
        for (a, b, c) in f.tris():
            A, B, C = f.uv[a], f.uv[b], f.uv[c]
            T = np.array([[B[0] - A[0], C[0] - A[0]], [B[1] - A[1], C[1] - A[1]]])
            if abs(np.linalg.det(T)) < 1e-9:
                continue
            bc = np.linalg.solve(T, (q - A).T).T
            w = np.stack([1 - bc[:, 0] - bc[:, 1], bc[:, 0], bc[:, 1]], -1)
            score = w.min(1)
            sel = score > best
            best[sel] = score[sel]
            pos[sel] = w[sel] @ f.verts[[a, b, c]]
            loc[sel] = w[sel] @ f.loc[[a, b, c]]
        yy = gy.reshape(-1).astype(int)
        xx = gx.reshape(-1).astype(int)
        P[yy, xx] = pos
        L[yy, xx] = loc
        N[yy, xx] = f.normal
        M[yy, xx] = fi
    return S, P, N, M, L


# ------------------------------------------------------------------ レンダラ

def shade_of(n, cam_dir):
    """Minecraft 風の向きによる明るさ (上 1.0、南北 0.8、東西 0.6、下 0.5) と、
    斜面を読ませるための弱い光。"""
    nx, ny, nz = n
    s2 = nx * nx + ny * ny + nz * nz
    mc = (1.0 * max(ny, 0) ** 2 + 0.5 * max(-ny, 0) ** 2 + 0.6 * nx * nx + 0.8 * nz * nz) / s2
    key = np.array([-0.35, 0.8, 0.45])
    key = nrm(key + cam_dir * 0.6)
    k = max(0.0, float(np.dot(n, key)))
    return 0.62 * mc + 0.55 * (0.25 + 0.75 * k) * 0.62 + 0.0


def render(faces, atlas, glow, yaw, pitch, size, center, scale, outline=True, ss=2,
           bg=(0, 0, 0, 0), light_cam=True):
    """正射影のレンダラ。atlas: (S,S,3|4) uint8、glow: (S,S) bool。
    center: 画面の中心に置くモデル座標、scale: 1 単位あたりのピクセル数。RGBA を返す。"""
    W, H = size[0] * ss, size[1] * ss
    V = rot_x(pitch) @ rot_y(yaw)
    cam_dir = V.T @ np.array([0, 0, 1.0])
    color = np.zeros((H, W, 3))
    covered = np.zeros((H, W), bool)
    zbuf = np.full((H, W), -1e9)
    fid = np.full((H, W), -1, int)
    gbuf = np.zeros((H, W), bool)
    S = atlas.shape[0]
    cen = V @ np.asarray(center, float)
    sc = scale * ss
    for fi, f in enumerate(faces):
        cv = f.verts @ V.T
        sx = (cv[:, 0] - cen[0]) * sc + W / 2
        sy = -(cv[:, 1] - cen[1]) * sc + H / 2
        sz = cv[:, 2]
        n = f.normal
        sh = shade_of(n, cam_dir)
        for (a, b, c) in f.tris():
            xs, ys = sx[[a, b, c]], sy[[a, b, c]]
            x0, x1 = int(max(0, math.floor(xs.min()))), int(min(W - 1, math.ceil(xs.max())))
            y0, y1 = int(max(0, math.floor(ys.min()))), int(min(H - 1, math.ceil(ys.max())))
            if x1 < x0 or y1 < y0:
                continue
            den = (ys[1] - ys[2]) * (xs[0] - xs[2]) + (xs[2] - xs[1]) * (ys[0] - ys[2])
            if abs(den) < 1e-9:
                continue
            gx, gy = np.meshgrid(np.arange(x0, x1 + 1) + 0.5, np.arange(y0, y1 + 1) + 0.5)
            l0 = ((ys[1] - ys[2]) * (gx - xs[2]) + (xs[2] - xs[1]) * (gy - ys[2])) / den
            l1 = ((ys[2] - ys[0]) * (gx - xs[2]) + (xs[0] - xs[2]) * (gy - ys[2])) / den
            l2 = 1 - l0 - l1
            inside = (l0 >= -1e-9) & (l1 >= -1e-9) & (l2 >= -1e-9)
            if not inside.any():
                continue
            z = l0 * sz[a] + l1 * sz[b] + l2 * sz[c]
            sub = zbuf[y0:y1 + 1, x0:x1 + 1]
            upd = inside & (z > sub)
            if not upd.any():
                continue
            u = l0 * f.uv[a, 0] + l1 * f.uv[b, 0] + l2 * f.uv[c, 0]
            v = l0 * f.uv[a, 1] + l1 * f.uv[b, 1] + l2 * f.uv[c, 1]
            ui = np.clip(np.floor(u).astype(int), 0, S - 1)
            vi = np.clip(np.floor(v).astype(int), 0, S - 1)
            tex = atlas[vi, ui, :3].astype(float)
            g = glow[vi, ui]
            # 裏面から見える面は、見えている側の法線で陰影をつける
            shv = sh if np.dot(n, cam_dir) >= 0 else shade_of(-n, cam_dir)
            col = np.where(g[..., None], tex * 1.0, tex * shv)
            sub[upd] = z[upd]
            color[y0:y1 + 1, x0:x1 + 1][upd] = col[upd]
            covered[y0:y1 + 1, x0:x1 + 1][upd] = True
            fid[y0:y1 + 1, x0:x1 + 1][upd] = fi
            gbuf[y0:y1 + 1, x0:x1 + 1][upd] = g[upd]
    out = color.copy()
    if outline:
        line = np.zeros((H, W), bool)
        t = max(1, ss)
        for dy, dx in ((0, t), (t, 0)):
            a = covered[: H - dy, : W - dx]
            b = covered[dy:, dx:]
            za, zb = zbuf[: H - dy, : W - dx], zbuf[dy:, dx:]
            sil = a != b
            jump = a & b & (np.abs(za - zb) > 2.2)
            m = sil | jump
            # 線は手前側ではなく奥側 (または背景側) に引く
            far_a = (~b) | (za < zb)
            line[: H - dy, : W - dx] |= m & a & ~far_a & False
            sub1 = line[: H - dy, : W - dx]
            sub2 = line[dy:, dx:]
            # 奥 (z が小さい) 側のピクセル、または背景に接する物体側を暗くする
            sub1 |= m & a & (~b | (za < zb))
            sub2 |= m & b & (~a | (zb < za))
        line &= covered
        out[line] = out[line] * 0.35
    # 光る画素のにじみ
    if gbuf.any():
        from PIL import ImageFilter
        gm = Image.fromarray((gbuf * 255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(3.0 * ss))
        gm = np.array(gm, float)[..., None] / 255.0
        gcol = np.array([140, 230, 255], float)
        add = gm * gcol * 0.9
        out = out + add * (~covered[..., None] * 0.6 + covered[..., None] * 0.25)
        covered = covered | (gm[..., 0] > 0.04)
    out = np.clip(out, 0, 255)
    rgba = np.zeros((H, W, 4))
    rgba[..., :3] = out
    rgba[..., 3] = covered * 255
    im = Image.fromarray(rgba.astype(np.uint8), "RGBA")
    if ss > 1:
        # 色のにじみを避けるため、アルファ乗算してから縮小
        arr = np.array(im, float)
        a = arr[..., 3:4] / 255.0
        pm = np.concatenate([arr[..., :3] * a, arr[..., 3:4]], -1)
        pm = pm.reshape(size[1], ss, size[0], ss, 4).mean((1, 3))
        al = pm[..., 3:4]
        rgb = np.where(al > 0, pm[..., :3] / np.maximum(al / 255.0, 1e-6), 0)
        im = Image.fromarray(np.concatenate([rgb, al], -1).clip(0, 255).astype(np.uint8), "RGBA")
    return im
