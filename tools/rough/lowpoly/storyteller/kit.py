"""語り部 (storyteller) 用の形の部品。作図空間は前 = -Z。骨の pivot は pv(骨名) で取る。"""
from __future__ import annotations

from parts import *  # noqa
from lpkit import _mk  # noqa


def lerp(a, b, t):
    return np.asarray(a, float) * (1 - t) + np.asarray(b, float) * t


def pv(bone):
    return flipxz(BONES[bone][1])


def jit(i, a=1.0, salt=0):
    q = math.sin(i * 12.9898 + salt * 78.233) * 43758.5453
    return (q - math.floor(q) - 0.5) * 2 * a


def sring(z, top, bot, w, wt=None, wb=None, cx=0.0, yu=0.28, yl=0.30, sk=0.9):
    """胴の輪 (8 角形、面取りした箱の断面)。top/bot = 上下の y、w = 最大の半幅、wt/wb = 上面・下面の半幅。"""
    wt = w * 0.55 if wt is None else wt
    wb = w * 0.55 if wb is None else wb
    h = top - bot
    y1, y2 = top - yu * h, bot + yl * h
    P = [(-wt, top), (wt, top), (w, y1), (w * sk, y2), (wb, bot), (-wb, bot), (-w * sk, y2), (-w, y1)]
    return [np.array([cx + x, y, z]) for x, y in P]


def torso(bone, mat, rings, caps=(False, False), mats=None):
    return rloft(bone, mat, rings, caps, mats)


def limb(bone, mat, pts, ra, rb=None, sides=5, hint=(1, 0, 0), phase=0.0, caps=(False, False)):
    return tube(bone, mat, pts, ra, rb, sides=sides, hint=hint, phase=phase, caps=caps)


def crystal(bone, mat, base, d, length, r, sides=5, mid=0.35, midk=1.0, bend=(0, 0, 0), r0k=0.75, hint=(0, 1, 0), phase=30.0):
    """水晶の柱: 根元 (r0k*r) → 太い所 (mid の位置、r) → 尖った先。"""
    d = nrm(np.asarray(d, float))
    base = np.asarray(base, float)
    bend = np.asarray(bend, float)
    path = [base, base + d * length * mid + bend * mid * mid, base + d * length + bend]
    return tube(bone, mat, path, [r * r0k, r * midk, 0.0], None, sides=sides, hint=hint, phase=phase, caps=(False, False))


def tuft(bone, mat, base, d, length, r, sides=3, bend=(0, 0, 0)):
    return spike(bone, mat, base, d, length, r, sides=sides, rings=1, bend=bend)


def mkf(pts, locs, bone, mat, ref):
    """面 1 枚を作り、頂点の局所座標 locs (絵を描くための座標) を頂点の並べ替えに合わせて持たせる。"""
    f = _mk(pts, bone, mat, ref)
    if f is None:
        return []
    lp = {tuple(np.round(np.asarray(a, float), 6)): b for a, b in zip(pts, locs)}
    f.loc = np.array([lp.get(tuple(np.round(v, 6)), (0, 0, 0)) for v in f.verts], float)
    return [f]


def seg_dist(Z, Y, a, b):
    """点 (Z, Y) と線分 a-b ((z, y) の組) の距離。"""
    ax, ay = a
    bx, by = b
    dx, dy = bx - ax, by - ay
    t = np.clip(((Z - ax) * dx + (Y - ay) * dy) / max(dx * dx + dy * dy, 1e-9), 0, 1)
    return np.hypot(Z - (ax + t * dx), Y - (ay + t * dy))


class Prof:
    """胴の断面の変化。stations = [(z, top, bot, w, wt, wb), ...] (z は前から後ろへ増える順でも減る順でもよい)。"""

    def __init__(self, stations):
        self.st = sorted(stations, key=lambda s: s[0])

    def at(self, z):
        st = self.st
        z = min(max(z, st[0][0]), st[-1][0])
        for a, b in zip(st[:-1], st[1:]):
            if a[0] <= z <= b[0]:
                t = (z - a[0]) / max(b[0] - a[0], 1e-9)
                return [a[i] * (1 - t) + b[i] * t for i in range(1, 6)]
        return list(st[-1][1:])

    def ring(self, z, infl=0.0):
        top, bot, w, wt, wb = self.at(z)
        r = sring(z, top, bot, w, wt, wb)
        if infl:
            c = np.mean(r, 0)
            r = [c + (p - c) + (p - c) / max(np.linalg.norm((p - c)[:2]), 1e-6) * np.array([1, 1, 0]) * infl for p in r]
        return r


def band(bone, mat, prof, z0, z1, i0, i1, cols=(6, 7, 0, 1, 2, 3), lip=0.7, lip_mat=None):
    """胴に巻く鎧の帯。前の縁 z0 は胴に沿い (i0 だけ膨らむ)、後ろの縁 z1 は i1 だけ浮き、
    さらにその後ろへ lip の段で胴に戻る (瓦のように後ろへ重なる)。"""
    rows = []
    for z, inf in ((z0, i0), (z1, i1), (z1 + math.copysign(lip, z1 - z0), 0.0)):
        r = prof.ring(z, inf)
        rows.append([r[c] for c in cols])
    F = grid(bone, mat, rows[:2], np.array([0, 30.0, (z0 + z1) / 2]))
    F += grid(bone, lip_mat or mat, rows[1:], np.array([0, 30.0, (z0 + z1) / 2]))
    return F
