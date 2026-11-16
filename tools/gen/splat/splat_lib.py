# -*- coding: utf-8 -*-
"""点の模型 (ピクセルアートのスプラット) を作る共通の道具。

形は距離の関数 (SDF) の部品を滑らかにつないで彫り、表面に点を撒く。
点ごとに: 位置 (geo の座標、1/16 ブロック)・大きさ・色 (陰と模様を焼き込み、ディザで段に落とす)・骨二本と重み・印 (光る / にじみ / 火の粉)。
ゲーム側は client/splat/ (SplatCloud・SplatDraw)。書き出しは assets/skyabyss/splat/<名前>.bin。

部品は Sculpt.add(sdf の関数, bbox, 材質, 骨の重み, k=つなぎの丸み, glow=光るか) で足す。
"""
from __future__ import annotations

import math
import struct
from pathlib import Path

import numpy as np
from scipy.ndimage import map_coordinates
from scipy.spatial import cKDTree

ROOT = Path(__file__).resolve().parents[3]

# 印 (Java の SplatCloud と同じ)
F_GLOW = 1    # 光る (明るさ一定、にじみの元)
F_HALO = 2    # にじみ (足し算の柔らかい丸。光の滲み)
F_EMBER = 4   # 火の粉 (時間で上へ昇って消える。足し算)


# ---------------------------------------------------------------- 距離の関数 (numpy、P: (N,3))
def sd_ellipsoid(c, r):
    c = np.asarray(c, float)
    r = np.asarray(r, float)

    def f(P):
        q = (P - c) / r
        k0 = np.linalg.norm(q, axis=1)
        k1 = np.linalg.norm(q / r, axis=1)
        return np.where(k1 > 1e-9, k0 * (k0 - 1.0) / np.maximum(k1, 1e-9), -r.min())
    lo = c - r
    hi = c + r
    return f, (lo, hi)


def sd_round_cone(a, b, r1, r2):
    """a 側の半径 r1、b 側の半径 r2 の丸い円すい (iq の式)"""
    a = np.asarray(a, float)
    b = np.asarray(b, float)
    ba = b - a
    l2 = float(ba @ ba)
    rr = r1 - r2
    a2 = l2 - rr * rr
    il2 = 1.0 / max(l2, 1e-9)

    def f(P):
        pa = P - a
        y = pa @ ba
        z = y - l2
        w = pa * l2 - np.outer(y, ba)
        x2 = np.einsum("ij,ij->i", w, w)
        y2 = y * y * l2
        z2 = z * z * l2
        k = math.copysign(1.0, rr) * rr * rr * x2
        d_b = np.sqrt(x2 + z2) * il2 - r2
        d_a = np.sqrt(x2 + y2) * il2 - r1
        d_m = (np.sqrt(np.maximum(x2 * a2 * il2, 0)) + y * rr) * il2 - r1
        return np.where(np.sign(z) * a2 * z2 > k, d_b, np.where(np.sign(y) * a2 * y2 < k, d_a, d_m))
    m = max(r1, r2)
    return f, (np.minimum(a, b) - m, np.maximum(a, b) + m)


def sd_triangle_slab(a, b, c, th):
    """三角の膜 (厚み th の半分ずつ)"""
    a, b, c = (np.asarray(v, float) for v in (a, b, c))
    ba, cb, ac = b - a, c - b, a - c
    nor = np.cross(ba, ac)

    def dot2(v):
        return np.einsum("ij,ij->i", v, v)

    def f(P):
        pa, pb, pc = P - a, P - b, P - c
        s = (np.sign(np.cross(ba, nor) @ pa.T) + np.sign(np.cross(cb, nor) @ pb.T) + np.sign(np.cross(ac, nor) @ pc.T))
        e1 = dot2(np.outer(np.clip(pa @ ba / (ba @ ba), 0, 1), ba) - pa)
        e2 = dot2(np.outer(np.clip(pb @ cb / (cb @ cb), 0, 1), cb) - pb)
        e3 = dot2(np.outer(np.clip(pc @ ac / (ac @ ac), 0, 1), ac) - pc)
        edge = np.minimum(np.minimum(e1, e2), e3)
        face = (pa @ nor) ** 2 / (nor @ nor)
        return np.sqrt(np.where(s < 2.0, edge, face)) - th
    lo = np.minimum(np.minimum(a, b), c) - th
    hi = np.maximum(np.maximum(a, b), c) + th
    return f, (lo, hi)


def sd_loft(secs, p=2.4):
    """z に沿って並べた超楕円の輪 (z, 上, 下, 半幅, 中)。輪の間は線形に補う。前後の端は丸める"""
    secs = sorted(secs, key=lambda s: s[0])
    zs = np.array([s[0] for s in secs])
    tops = np.array([s[1] for s in secs])
    bots = np.array([s[2] for s in secs])
    ws = np.array([s[3] for s in secs])
    mids = np.array([s[4] for s in secs])

    def f(P):
        z = np.clip(P[:, 2], zs[0], zs[-1])
        top = np.interp(z, zs, tops)
        bot = np.interp(z, zs, bots)
        w = np.interp(z, zs, ws)
        mid = np.interp(z, zs, mids)
        dy = P[:, 1] - mid
        hy = np.where(dy >= 0, top - mid, mid - bot)
        qx = np.abs(P[:, 0]) / np.maximum(w, 1e-3)
        qy = np.abs(dy) / np.maximum(hy, 1e-3)
        n = (qx ** p + qy ** p) ** (1.0 / p)
        d2 = (n - 1.0) * np.minimum(w, hy)
        dz = np.maximum(zs[0] - P[:, 2], P[:, 2] - zs[-1])
        out = np.maximum(dz, 0)
        return np.where(dz > 0, np.sqrt(np.maximum(d2, 0) ** 2 + out ** 2), np.maximum(d2, dz))
    lo = np.array([-ws.max(), bots.min(), zs[0]])
    hi = np.array([ws.max(), tops.max(), zs[-1]])
    return f, (lo, hi)


def catmull(pts, n_per=8):
    """点の列を Catmull-Rom で滑らかにした点の列"""
    pts = [np.asarray(p, float) for p in pts]
    if len(pts) < 3:
        return pts
    ext = [pts[0] * 2 - pts[1]] + pts + [pts[-1] * 2 - pts[-2]]
    out = []
    for i in range(1, len(ext) - 2):
        p0, p1, p2, p3 = ext[i - 1], ext[i], ext[i + 1], ext[i + 2]
        for k in range(n_per):
            t = k / n_per
            t2, t3 = t * t, t * t * t
            out.append(0.5 * ((2 * p1) + (-p0 + p2) * t + (2 * p0 - 5 * p1 + 4 * p2 - p3) * t2 + (-p0 + 3 * p1 - 3 * p2 + p3) * t3))
    out.append(pts[-1])
    return out


# ---------------------------------------------------------------- 彫る
class Sculpt:
    def __init__(self, name, bones, h=0.5):
        """bones: 骨の名前の列 (Java の骨の番号はこの順)。h: 格子の間隔 (1/16 ブロック)"""
        self.name = name
        self.bones = list(bones)
        self.h = h
        self.parts = []  # (f, lo, hi, mat, weights{骨: 重み}, k, glow, tag)

    def add(self, fb, mat, weights, k=1.0, glow=False, tag=None):
        f, (lo, hi) = fb
        if isinstance(weights, str):
            weights = {weights: 1.0}
        self.parts.append((f, np.asarray(lo, float), np.asarray(hi, float), mat, dict(weights), k, glow, tag))
        return len(self.parts) - 1

    def sub(self, fb, k=0.4):
        """削る (滑らかな差)。それまでに足した形から取り除く。目の窪み・鼻の穴など"""
        f, (lo, hi) = fb
        self.parts.append((f, np.asarray(lo, float), np.asarray(hi, float), None, {}, k, False, "__sub__"))
        return len(self.parts) - 1

    def tube(self, pts, radii, mat, weights_fn, k=1.2, glow=False, tag=None, n_per=6):
        """点の列を滑らかにした管。weights_fn(t: 0..1) → {骨: 重み}。区間ごとに部品を分け、t を覚える"""
        sp = catmull(pts, n_per) if len(pts) >= 3 else [np.asarray(p, float) for p in pts]
        rr = np.interp(np.linspace(0, 1, len(sp)), np.linspace(0, 1, len(radii)), radii)
        ids = []
        for i in range(len(sp) - 1):
            t = (i + 0.5) / (len(sp) - 1)
            ids.append(self.add(sd_round_cone(sp[i], sp[i + 1], rr[i], rr[i + 1]), mat, weights_fn(t), k=k, glow=glow, tag=tag))
        return ids

    # 格子の上で全部品を評価し、滑らかにつないだ距離・いちばん近い部品・二番目に近い (骨の違う) 部品を持つ
    def evaluate(self, margin=3.0, box=None):
        """box: (lo, hi) を渡すとその範囲だけを評価する (頭だけ細かい格子で、など)"""
        lo = np.min([p[1] for p in self.parts], axis=0) - margin
        hi = np.max([p[2] for p in self.parts], axis=0) + margin
        if box is not None:
            lo = np.maximum(lo, np.asarray(box[0], float) - margin)
            hi = np.minimum(hi, np.asarray(box[1], float) + margin)
        h = self.h
        shape = np.ceil((hi - lo) / h).astype(int) + 1
        self.lo, self.shape = lo, shape
        big = 1e3
        D = np.full(shape, big, np.float32)
        d1 = np.full(shape, big, np.float32)
        i1 = np.full(shape, -1, np.int32)
        d2 = np.full(shape, big, np.float32)
        i2 = np.full(shape, -1, np.int32)
        for pid, (f, plo, phi, mat, w, k, glow, tag) in enumerate(self.parts):
            a = np.maximum(np.floor((plo - k - 1.0 - lo) / h).astype(int), 0)
            b = np.minimum(np.ceil((phi + k + 1.0 - lo) / h).astype(int) + 1, shape)
            if np.any(b <= a):
                continue
            gx, gy, gz = (np.arange(a[j], b[j]) * h + lo[j] for j in range(3))
            X, Y, Z = np.meshgrid(gx, gy, gz, indexing="ij")
            P = np.stack([X.ravel(), Y.ravel(), Z.ravel()], 1)
            d = f(P).reshape(X.shape).astype(np.float32)
            sl = tuple(slice(a[j], b[j]) for j in range(3))
            Dd = D[sl]
            if tag == "__sub__":
                # 滑らかな差 (smax(D, -d))
                hh = np.clip(0.5 - 0.5 * (Dd + d) / k, 0, 1)
                D[sl] = (Dd * (1 - hh) - d * hh + k * hh * (1 - hh)).astype(np.float32)
                continue
            # 滑らかな和 (多項式の smin)
            hh = np.clip(0.5 + 0.5 * (Dd - d) / k, 0, 1)
            D[sl] = (Dd * (1 - hh) + d * hh - k * hh * (1 - hh)).astype(np.float32)
            # いちばん近い / 二番目
            c1, ci1, c2, ci2 = d1[sl], i1[sl], d2[sl], i2[sl]
            better = d < c1
            nc2 = np.where(better, c1, np.where(d < c2, d, c2))
            nci2 = np.where(better, ci1, np.where(d < c2, pid, ci2))
            d1[sl] = np.where(better, d, c1)
            i1[sl] = np.where(better, pid, ci1)
            d2[sl] = nc2
            i2[sl] = nci2
        self.D, self.d1, self.i1, self.d2, self.i2 = D, d1, i1, d2, i2

    def sample(self, P, order=1):
        """格子の距離を点で引く (線形補間)"""
        idx = ((P - self.lo) / self.h).T
        return map_coordinates(self.D, idx, order=order, mode="nearest")

    def grad(self, P):
        e = self.h * 0.75
        g = np.stack([self.sample(P + np.array(v) * e) - self.sample(P - np.array(v) * e) for v in ((1, 0, 0), (0, 1, 0), (0, 0, 1))], 1)
        n = np.linalg.norm(g, axis=1, keepdims=True)
        return g / np.maximum(n, 1e-9)

    def surface(self, seed=7):
        """表面を横切る格子の点を表面へ寄せた点。戻り: 位置・法線・最寄りの部品・二番目の部品・二番目までの差"""
        D = self.D
        inside = D < 0
        cross = np.zeros_like(inside)
        for ax in range(3):
            a = np.swapaxes(inside, 0, ax)
            c = np.swapaxes(cross, 0, ax)
            diff = a[1:] != a[:-1]
            c[1:] |= diff
            c[:-1] |= diff
        cross &= np.abs(D) < self.h * 1.2
        ijk = np.argwhere(cross)
        rng = np.random.default_rng(seed)
        P0 = ijk * self.h + self.lo + rng.uniform(-0.35, 0.35, ijk.shape) * self.h
        P = P0.copy()
        for _ in range(3):
            n = self.grad(P)
            P = P - n * self.sample(P)[:, None]
        n = self.grad(P)
        # 寄せたときに飛んだ点 (勾配がほぼ 0 の所) と、表面に乗らなかった点は捨てる
        ok = (np.linalg.norm(P - P0, axis=1) < 2.0 * self.h) & (np.abs(self.sample(P)) < 0.5 * self.h)
        P, n, ijk = P[ok], n[ok], ijk[ok]
        # 部品は格子の最寄りの点から取る
        q = np.clip(np.rint((P - self.lo) / self.h).astype(int), 0, self.shape - 1)
        pid1 = self.i1[q[:, 0], q[:, 1], q[:, 2]]
        pid2 = self.i2[q[:, 0], q[:, 1], q[:, 2]]
        gap = self.d2[q[:, 0], q[:, 1], q[:, 2]] - self.d1[q[:, 0], q[:, 1], q[:, 2]]
        return P, n, pid1, pid2, gap, ijk

    def curvature(self, P, e=None):
        """距離のラプラシアン。正 = 出っ張り (半径 r の球で 2/r)、負 = 窪み"""
        e = self.h if e is None else e
        c = self.sample(P)
        lap = np.zeros(len(P))
        for v in ((1, 0, 0), (0, 1, 0), (0, 0, 1)):
            v = np.array(v) * e
            lap += self.sample(P + v) + self.sample(P - v) - 2 * c
        return lap / (e * e)

    def ao(self, P, n):
        """表面から法線の向きに少し出た所の距離で、窪みの暗さ (0 暗い..1)"""
        s = 0.0
        for dist, wt in ((1.0, 0.25), (2.2, 0.35), (4.0, 0.4)):
            s += wt * np.clip(self.sample(P + n * dist) / dist, 0, 1)
        return s


def bone_weights(parts, pid1, pid2, gap, bones, blend=1.6):
    """点ごとの骨二本と重み (骨 A の割合)。最寄りの部品の重みに、近くに別の骨の部品があれば混ぜる"""
    bi = {b: i for i, b in enumerate(bones)}
    N = len(pid1)
    A = np.zeros(N, np.int32)
    B = np.zeros(N, np.int32)
    W = np.ones(N, np.float32)
    for i in range(N):
        acc = {}
        for b, w in parts[pid1[i]][4].items():
            acc[b] = acc.get(b, 0) + w
        if pid2[i] >= 0:
            s = 0.5 * max(0.0, 1.0 - gap[i] / blend)
            if s > 0:
                for b in acc:
                    acc[b] *= (1 - s)
                for b, w in parts[pid2[i]][4].items():
                    acc[b] = acc.get(b, 0) + w * s
        top = sorted(acc.items(), key=lambda kv: -kv[1])
        a = top[0]
        bb = top[1] if len(top) > 1 else (a[0], 0.0)
        tot = a[1] + bb[1]
        A[i] = bi[a[0]]
        B[i] = bi[bb[0]]
        W[i] = a[1] / tot if tot > 0 else 1.0
    return A, B, W


# ---------------------------------------------------------------- 模様
def worley(P, cell, seed=0):
    """3D の細胞の模様。戻り: 一番近い種までの距離 f1・二番目 f2 (どちらも cell で割った値)・細胞の乱数 (0..1)"""
    Q = P / cell
    base = np.floor(Q).astype(np.int64)
    f1 = np.full(len(P), 9.0)
    f2 = np.full(len(P), 9.0)
    idv = np.zeros(len(P))
    for dx in (-1, 0, 1):
        for dy in (-1, 0, 1):
            for dz in (-1, 0, 1):
                c = base + np.array([dx, dy, dz])
                h = (c[:, 0] * 73856093) ^ (c[:, 1] * 19349663) ^ (c[:, 2] * 83492791) ^ (seed * 2654435761)
                h = (h & 0xFFFFFFFF).astype(np.uint64)
                r = []
                for s in range(4):
                    h = (h * np.uint64(1103515245) + np.uint64(12345)) & np.uint64(0x7FFFFFFF)
                    r.append(h.astype(np.float64) / 0x7FFFFFFF)
                fp = c + np.stack(r[:3], 1)
                d = np.linalg.norm(Q - fp, axis=1)
                closer = d < f1
                f2 = np.where(closer, f1, np.minimum(f2, d))
                idv = np.where(closer, r[3], idv)
                f1 = np.where(closer, d, f1)
    return f1, f2, idv


def value_noise(P, scale, seed=0):
    f1, f2, idv = worley(P, scale, seed)
    return idv


BAYER4 = np.array([[0, 8, 2, 10], [12, 4, 14, 6], [3, 11, 1, 9], [15, 7, 13, 5]]) / 16.0


def ramp_pick(ramp, L, dither):
    """明るさ L (0..1) を段 (色の列) に落とす。dither (0..1) で段の境をばらす"""
    ramp = np.asarray(ramp, float)
    n = len(ramp)
    f = np.clip(L * (n - 1) + (dither - 0.5) * 0.9, 0, n - 1)
    return ramp[np.rint(f).astype(int)]


# ---------------------------------------------------------------- 書き出し
def oct_encode(N):
    """法線を八面体の 2 つの u8 に (誤差 1 度以下)。(0, 0) は「法線なし」(長さ 0) に取っておく
    (八面体の四隅はどれも真下 (0, 0, -1) なので、真下は (255, 255) で書く)"""
    N = np.asarray(N, float)
    s = np.abs(N).sum(1, keepdims=True)
    zero = s[:, 0] < 1e-9
    n = N / np.where(s < 1e-9, 1, s)
    x, y = n[:, 0], n[:, 1]
    neg = n[:, 2] < 0
    sx = np.where(x >= 0, 1.0, -1.0)
    sy = np.where(y >= 0, 1.0, -1.0)
    x2 = np.where(neg, (1 - np.abs(y)) * sx, x)
    y2 = np.where(neg, (1 - np.abs(x)) * sy, y)
    u = np.rint((x2 * 0.5 + 0.5) * 255).astype(np.int64)
    v = np.rint((y2 * 0.5 + 0.5) * 255).astype(np.int64)
    corner = (u == 0) & (v == 0)
    u[corner] = 255
    v[corner] = 255
    u[zero] = 0
    v[zero] = 0
    return u, v


def oct_decode(u, v):
    """oct_encode の逆 (読む側 SplatCloud.readSpl4 と同じ計算)。戻り: 単位ベクトル ((0, 0) は長さ 0)"""
    x = np.asarray(u, float) / 255 * 2 - 1
    y = np.asarray(v, float) / 255 * 2 - 1
    z = 1 - np.abs(x) - np.abs(y)
    t = np.clip(-z, 0, None)
    x = x - np.where(x >= 0, 1.0, -1.0) * t
    y = y - np.where(y >= 0, 1.0, -1.0) * t
    n = np.stack([x, y, z], 1)
    n = n / np.linalg.norm(n, axis=1, keepdims=True)
    n[(np.asarray(u) == 0) & (np.asarray(v) == 0)] = 0
    return n


def pack4(bones, lo, step, smax, q, size8, rgb, A, B, W8, flags, octu, octv):
    """形式 SPL4 の中身 (圧縮前)。並びはそのまま書く (並べ替えは write_bin がする)"""
    n = len(q)
    out = bytearray()
    out.extend(struct.pack(">I", len(bones)))
    for b in bones:
        e = b.encode("utf-8")
        out.extend(struct.pack(">H", len(e)) + e)
    out.extend(struct.pack(">I", n))
    out.extend(struct.pack(">fffff", float(lo[0]), float(lo[1]), float(lo[2]), float(step), float(smax)))

    def varints(arr):
        d = np.diff(np.concatenate([[0], np.asarray(arr, np.int64)]))
        z = (d << 1) ^ (d >> 63)
        buf = bytearray()
        for v in z.tolist():
            while v >= 0x80:
                buf.append((v & 0x7F) | 0x80)
                v >>= 7
            buf.append(v)
        return buf
    for k in range(3):
        out.extend(varints(q[:, k]))
    out.extend(np.asarray(size8, np.uint8).tobytes())
    # 色: 使っている色の一覧 (多い順) と、点ごとの番号 (一覧が 256 色を超えるときは下位・上位の 2 列)
    rgb = np.asarray(rgb, np.int64)
    key = (rgb[:, 0] << 16) | (rgb[:, 1] << 8) | rgb[:, 2]
    pal, inv = np.unique(key, return_inverse=True)
    inv = inv.reshape(-1)
    rank = np.argsort(-np.bincount(inv), kind="stable")
    remap = np.empty_like(rank)
    remap[rank] = np.arange(len(rank))
    idx = remap[inv]
    pal = pal[rank]
    out.extend(struct.pack(">I", len(pal)))
    for sh in (16, 8, 0):
        out.extend(((pal >> sh) & 255).astype(np.uint8).tobytes())
    out.extend((idx & 255).astype(np.uint8).tobytes())
    if len(pal) > 256:
        out.extend((idx >> 8).astype(np.uint8).tobytes())
    for col in (A, B, W8, flags, octu, octv):
        out.extend(np.asarray(col, np.uint8).tobytes())
    return bytes(out)


def save4(path, body):
    import zlib
    path = Path(path)
    path.parent.mkdir(parents=True, exist_ok=True)
    with open(path, "wb") as fo:
        fo.write(b"SPL4")
        fo.write(zlib.compress(body, 9))
    return path


def write_bin(path, bones, P, size, rgba, A, B, W, flags, N=None, step=None):
    """形式 SPL4。"SPL4" のあとを zlib で圧縮: 骨の数・骨の名前・点の数・原点 (float 3)・刻み (float)・大きさの最大 (float)、
    そのあと項目ごとの列: 位置 (刻みの整数を並び順の差で zigzag の varint、x・y・z)、大きさ u8、
    色 (一覧の数 u32・一覧の r 列・g 列・b 列・点ごとの番号 u8 (一覧が 256 色を超えるときは上位の u8 列も))、
    骨 A・骨 B・重み・印 u8、法線 (八面体の u8 2 列。(0, 0) は法線なし)。
    点は骨・位置の順に並べて書く (圧縮が効く)。遠くの間引きのための乱数の並べ替えは、読むとき (SplatCloud) にする"""
    P = np.asarray(P, float)
    lo = P.min(0)
    if step is None:
        step = float(np.median(size)) / 10.0
    q = np.rint((P - lo) / step).astype(np.int64)
    order = np.lexsort((q[:, 2], q[:, 1], q[:, 0], np.asarray(A)))
    smax = float(np.max(size))
    c = np.asarray(rgba, np.int64)[order]
    rgb = np.stack([(c >> 16) & 255, (c >> 8) & 255, c & 255], 1)
    Nn = np.zeros((len(P), 3)) if N is None else np.asarray(N, float)
    u, v = oct_encode(Nn[order])
    body = pack4(bones, lo, step, smax, q[order],
                 np.clip(np.rint(np.asarray(size)[order] / smax * 255), 1, 255),
                 rgb, np.asarray(A)[order], np.asarray(B)[order],
                 np.clip(np.rint(np.asarray(W)[order] * 255), 0, 255), np.asarray(flags)[order], u, v)
    return save4(path, body)


def read_bin(path):
    """SPL4 を読む。戻り: dict (bones, lo, step, smax, q, size8, rgb, A, B, W8, flags, octu, octv, P, size, N)"""
    import zlib
    raw = Path(path).read_bytes()
    assert raw[:4] == b"SPL4", path
    b = zlib.decompress(raw[4:])
    o = 0
    nb = struct.unpack_from(">I", b, o)[0]
    o += 4
    bones = []
    for _ in range(nb):
        ln = struct.unpack_from(">H", b, o)[0]
        bones.append(b[o + 2:o + 2 + ln].decode("utf-8"))
        o += 2 + ln
    n = struct.unpack_from(">I", b, o)[0]
    o += 4
    lx, ly, lz, step, smax = struct.unpack_from(">fffff", b, o)
    o += 20
    q = np.zeros((n, 3), np.int64)
    for k in range(3):
        acc = 0
        for i in range(n):
            v = 0
            sh = 0
            while True:
                c = b[o]
                o += 1
                v |= (c & 0x7F) << sh
                sh += 7
                if not c & 0x80:
                    break
            acc += (v >> 1) ^ -(v & 1)
            q[i, k] = acc

    def col(m):
        nonlocal o
        a = np.frombuffer(b, np.uint8, count=m, offset=o)
        o += m
        return a
    d = {"bones": bones, "lo": np.array([lx, ly, lz]), "step": step, "smax": smax, "q": q, "size8": col(n)}
    npal = struct.unpack_from(">I", b, o)[0]
    o += 4
    pal = np.stack([col(npal), col(npal), col(npal)], 1)
    idx = col(n).astype(np.int64)
    if npal > 256:
        idx = idx | (col(n).astype(np.int64) << 8)
    d["rgb"] = pal[idx]
    for k in ("A", "B", "W8", "flags", "octu", "octv"):
        d[k] = col(n)
    d["P"] = d["lo"] + q * step
    d["size"] = d["size8"] / 255 * smax
    d["N"] = oct_decode(d["octu"], d["octv"])
    return d


def argb(col, a=255):
    col = np.clip(np.rint(col), 0, 255).astype(np.int64)
    return (np.int64(a) << 24) | (col[:, 0] << 16) | (col[:, 1] << 8) | col[:, 2]


# ---------------------------------------------------------------- 下見の絵 (正射影、点を四角で塗る)
def preview(P, size, col, flags, out, yaw_list=(200, 160, 270), pitch=12, px=900, scale=None, bg=(14, 12, 18)):
    from PIL import Image
    tiles = []
    solid = (flags & (F_HALO | F_EMBER)) == 0
    for yaw in yaw_list:
        a = math.radians(yaw)
        b = math.radians(pitch)
        R1 = np.array([[math.cos(a), 0, math.sin(a)], [0, 1, 0], [-math.sin(a), 0, math.cos(a)]])
        R2 = np.array([[1, 0, 0], [0, math.cos(b), -math.sin(b)], [0, math.sin(b), math.cos(b)]])
        Q = (P @ R1.T) @ R2.T
        c = (Q.min(0) + Q.max(0)) / 2
        span = (Q.max(0) - Q.min(0))[:2].max() * 1.1
        s = px / span if scale is None else scale
        img = np.zeros((px, px, 3), np.float32) + np.array(bg, np.float32)
        zb = np.full((px, px), -1e9, np.float32)
        X = ((Q[:, 0] - c[0]) * s + px / 2)
        Y = (-(Q[:, 1] - c[1]) * s + px / 2)
        Zd = Q[:, 2]
        order = np.argsort(Zd)
        for i in order:
            if not solid[i]:
                continue
            r = max(1, int(size[i] * s * 0.5))
            x0, x1 = int(X[i]) - r, int(X[i]) + r + 1
            y0, y1 = int(Y[i]) - r, int(Y[i]) + r + 1
            if x1 <= 0 or y1 <= 0 or x0 >= px or y0 >= px:
                continue
            x0, y0 = max(x0, 0), max(y0, 0)
            x1, y1 = min(x1, px), min(y1, px)
            img[y0:y1, x0:x1] = col[i]
        # にじみは足し算で重ねる
        for i in np.nonzero(~solid & ((flags & F_HALO) != 0))[0]:
            r = max(2, int(size[i] * s * 0.5))
            x, y = int(X[i]), int(Y[i])
            x0, x1, y0, y1 = max(x - r, 0), min(x + r + 1, px), max(y - r, 0), min(y + r + 1, px)
            if x1 <= x0 or y1 <= y0:
                continue
            yy, xx = np.mgrid[y0:y1, x0:x1]
            fall = np.clip(1 - np.hypot(xx - x, yy - y) / r, 0, 1) ** 2
            img[y0:y1, x0:x1] += fall[..., None] * np.asarray(col[i], np.float32)
        tiles.append(np.clip(img, 0, 255).astype(np.uint8))
    Image.fromarray(np.concatenate(tiles, 1)).save(out)
    return out
