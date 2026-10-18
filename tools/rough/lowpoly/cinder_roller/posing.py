"""燠虫: アニメの評価・ポーズ・三角形どうしの交差の検査。"""
from __future__ import annotations

import copy
import json
import math
import sys
from collections import Counter
from pathlib import Path

import numpy as np

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
sys.path.insert(0, str(HERE.parent))
import model as MD  # noqa
import lpkit  # noqa

A = MD.ROOT / "src/main/resources/assets/skyabyss/"
ANIM = json.loads((A / "animations/entity/cinder_roller.animation.json").read_text(encoding="utf-8"))["animations"]


def _rot(rx, ry, rz):
    rx, ry, rz = (math.radians(a) for a in (rx, ry, rz))
    cx, sx, cy, sy, cz, sz = math.cos(rx), math.sin(rx), math.cos(ry), math.sin(ry), math.cos(rz), math.sin(rz)
    Rx = np.array([[1, 0, 0], [0, cx, -sx], [0, sx, cx]])
    Ry = np.array([[cy, 0, sy], [0, 1, 0], [-sy, 0, cy]])
    Rz = np.array([[cz, -sz, 0], [sz, cz, 0], [0, 0, 1]])
    return Rz @ Ry @ Rx


def _ev(x, t):
    if isinstance(x, (int, float)):
        return float(x)
    e = str(x).replace("math.sin(", "sin_d(").replace("math.cos(", "cos_d(").replace("math.abs(", "abs(") \
        .replace("query.anim_time", "t")
    env = {"sin_d": lambda a: math.sin(math.radians(a)), "cos_d": lambda a: math.cos(math.radians(a)), "t": t, "abs": abs}
    return float(eval(e, {"__builtins__": {}}, env))


def channel(ch, t):
    if isinstance(ch, list):
        return [_ev(x, t) for x in ch]
    ks = sorted(((float(k), v["vector"] if isinstance(v, dict) else v) for k, v in ch.items()))
    if t <= ks[0][0]:
        return [_ev(x, t) for x in ks[0][1]]
    for (t0, v0), (t1, v1) in zip(ks[:-1], ks[1:]):
        if t0 <= t <= t1:
            k = (t - t0) / max(t1 - t0, 1e-9)
            return [_ev(a, t) * (1 - k) + _ev(b, t) * k for a, b in zip(v0, v1)]
    return [_ev(x, t) for x in ks[-1][1]]


def anim_state(name, t):
    """{骨: (回転 (ファイルの値), 位置)}"""
    out = {}
    for b, chs in ANIM["animation.cinder_roller." + name]["bones"].items():
        out[b] = (channel(chs["rotation"], t) if "rotation" in chs else [0, 0, 0],
                  channel(chs["position"], t) if "position" in chs else [0, 0, 0])
    return out


def intent_rot(r):
    return (-r[0], r[1], -r[2])


def pose_faces(faces, state, yaw180=True):
    """休めのメッシュの面を state のポーズへ動かす。最後に root の yaw 180 (前を -Z) を掛ける。"""
    xf = {}

    def get(b):
        if b in xf:
            return xf[b]
        parent, pv = MD.BONES[b]
        pv = np.array(pv)
        if parent is None:
            R = lpkit.rot_y(180) if yaw180 else np.eye(3)
            xf[b] = (R, np.zeros(3), R)
            return xf[b]
        Ap, tp, Rp = get(parent)
        rot, pos = state.get(b, ([0, 0, 0], [0, 0, 0]))
        rx, ry, rz = intent_rot(rot)
        R = _rot(rx, ry, rz)
        pos = np.array([-pos[0], pos[1], pos[2]])
        A = Ap @ R
        t = Ap @ (pv - R @ pv + pos) + tp
        xf[b] = (A, t, Rp @ R)
        return xf[b]

    out = []
    for f in faces:
        A_, t_, R_ = get(f.bone)
        g = copy.copy(f)
        g.verts = f.verts @ A_.T + t_
        g.normal = R_ @ f.normal
        out.append(g)
    return out


# ---------------------------------------------------------------------- 交差の検査
def tri_list(faces):
    T, meta = [], []
    for fi, f in enumerate(faces):
        for a, b, c in f.tris():
            T.append(f.verts[[a, b, c]])
            meta.append((fi, f.bone, f.mat))
    return np.array(T), meta


def _seg_tri(p0, p1, tri, eps=1e-3):
    """線分 p0-p1 (n,3) と三角形 tri (3,3) の交差 (端点は除く)"""
    a, b, c = tri
    e1, e2 = b - a, c - a
    d = p1 - p0
    h = np.cross(d, e2)
    det = h @ e1
    ok = np.abs(det) > 1e-9
    inv = np.where(ok, 1.0 / np.where(ok, det, 1.0), 0.0)
    s = p0 - a
    u = (s * h).sum(1) * inv
    q = np.cross(s, e1)
    v = (d * q).sum(1) * inv
    t = (e2 @ q.T) * inv
    return ok & (u > eps) & (v > eps) & (u + v < 1 - eps) & (t > eps) & (t < 1 - eps)


def intersections(faces, skip=lambda m1, m2: False):
    T, meta = tri_list(faces)
    lo, hi = T.min(1), T.max(1)
    n = len(T)
    hits = []
    for i in range(n):
        ov = np.all((lo[i] <= hi[i + 1:] - 1e-3) & (hi[i] >= lo[i + 1:] + 1e-3), axis=1)
        for j in (np.nonzero(ov)[0] + i + 1):
            if meta[i][0] == meta[j][0]:
                continue
            # 頂点を共有 (接している) 組は除く
            d = np.linalg.norm(T[i][:, None, :] - T[j][None, :, :], axis=2)
            if (d < 1e-3).any():
                continue
            if skip(meta[i], meta[j]):
                continue
            ti, tj = T[i], T[j]
            hit = False
            for e in ((0, 1), (1, 2), (2, 0)):
                if _seg_tri(ti[e[0]][None], ti[e[1]][None], tj)[0] or _seg_tri(tj[e[0]][None], tj[e[1]][None], ti)[0]:
                    hit = True
                    break
            if hit:
                hits.append((meta[i], meta[j]))
    return hits


def summarize(hits, top=25):
    cnt = Counter()
    for a, b in hits:
        key = tuple(sorted(((a[1], a[2]), (b[1], b[2]))))
        cnt[key] += 1
    return cnt.most_common(top)
