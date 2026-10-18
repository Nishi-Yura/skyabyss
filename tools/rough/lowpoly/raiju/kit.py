"""雷獣 (raiju) 用の形の部品。作図空間は前 = -Z。骨の pivot は pv(骨名) で取る。"""
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


def feather(bone, mat, base, tip, width, hint=(0, 1, 0), bend=(0, 0, 0), shoulder=0.4, twist=0.0):
    """羽根 1 枚: 4 頂点の凧形の板 (2 三角形)。loc = (横の長さ, 縦の長さ, 0..1 の割合)。hint 側が表。"""
    base, tip = np.asarray(base, float), np.asarray(tip, float)
    bend = np.asarray(bend, float)
    d = tip - base
    ln = float(np.linalg.norm(d))
    dn = nrm(d)
    h = np.asarray(hint, float)
    across = nrm(np.cross(dn, h))
    if twist:
        across = nrm(across * math.cos(math.radians(twist)) + h * math.sin(math.radians(twist)))
    mid = base + d * shoulder + bend * shoulder * shoulder
    tipb = tip + bend
    L, R = mid - across * width / 2, mid + across * width / 2
    pts = [base, L, tipb, R]
    locs = [(0, 0, 0), (-width / 2, shoulder * ln, shoulder), (0, ln, 1), (width / 2, shoulder * ln, shoulder)]
    ref = base - nrm(np.cross(across, dn)) * 6.0
    return mkf(pts, locs, bone, mat, ref)


def feather6(bone, mat, base, tip, width, hint=(0, 1, 0), bend=(0, 0, 0), twist=0.0):
    """6 頂点の羽根 (先が丸め気味): 根元 / 左肩 / 左先 / 先端 / 右先 / 右肩。"""
    base, tip = np.asarray(base, float), np.asarray(tip, float)
    bend = np.asarray(bend, float)
    d = tip - base
    dn = nrm(d)
    h = np.asarray(hint, float)
    across = nrm(np.cross(dn, h))
    if twist:
        across = nrm(across * math.cos(math.radians(twist)) + h * math.sin(math.radians(twist)))

    def at(t):
        return base + d * t + bend * t * t
    pts = [base, at(0.3) - across * width * 0.5, at(0.75) - across * width * 0.38, at(1.0), at(0.75) + across * width * 0.38,
           at(0.3) + across * width * 0.5]
    ln = float(np.linalg.norm(d))
    locs = [(0, 0, 0), (-width * 0.5, 0.3 * ln, 0.3), (-width * 0.38, 0.75 * ln, 0.75), (0, ln, 1), (width * 0.38, 0.75 * ln, 0.75), (width * 0.5, 0.3 * ln, 0.3)]
    ref = base - nrm(np.cross(across, dn)) * 6.0
    return mkf(pts, locs, bone, mat, ref)


def paw(bone, mat, claw_mat, c, wid, ln, ht, dirz=-1.0, claws=3, cl=2.6, cut=0.3):
    """足先: 先細りの低い台形 + 爪。c = 地面の中心、前 = dirz (作図空間 -1 が前)。"""
    c = np.asarray(c, float)
    r0 = rrect(c + np.array([0, ht, 0]), (1, 0, 0), (0, 0, 1), wid * 0.8, ln * 0.55, cut)
    mid = rrect(c + np.array([0, ht * 0.35, dirz * ln * 0.12]), (1, 0, 0), (0, 0, 1), wid, ln * 0.85, cut)
    low = rrect(c + np.array([0, 0.0, dirz * ln * 0.2]), (1, 0, 0), (0, 0, 1), wid * 0.92, ln, cut)
    F = rloft(bone, mat, [r0, mid, low], (False, mat))
    for k in range(claws):
        x = (k - (claws - 1) / 2) * wid / claws * 1.1
        F += spike(bone, claw_mat, c + np.array([x, 0.5, dirz * ln * 0.62]), (0, -0.25, dirz), cl, 0.85, sides=3, rings=1,
                   bend=(0, -0.8, dirz * 0.6))
    return F


def seg_dist(Z, Y, a, b):
    """点 (Z, Y) と線分 a-b ((z, y) の組) の距離。"""
    ax, ay = a
    bx, by = b
    dx, dy = bx - ax, by - ay
    t = np.clip(((Z - ax) * dx + (Y - ay) * dy) / max(dx * dx + dy * dy, 1e-9), 0, 1)
    return np.hypot(Z - (ax + t * dx), Y - (ay + t * dy))


def fur_extra(c, col, glow, vein=None, belly=None, seed=7, scale=0.09, bolts=None):
    """毛の腹側を明るくし、稲妻の筋 (ノイズの等高線) と、体の横の折れ線の稲妻模様 (bolts) を描く。
    bolts = dict(lines=[[(z, y), ...], ...], color=(r, g, b), w=太さ, mats=[...], glow=False)"""
    P, N, mats, bay, lit, ny = c["P"], c["N"], c["mats"], c["bay"], c["lit"], c["ny"]
    owned = c["owned"]
    if belly is not None:
        mat, ramp = belly
        ramp = np.array(ramp, float)
        sel = (mats == mat) & (ny < -0.2) & owned
        k = np.clip(np.floor((0.30 + 0.35 * lit + 0.1 * bay + 0.45 * (-ny)) * len(ramp)).astype(int), 0, len(ramp) - 1)
        col = np.where(sel[..., None], ramp[k], col)
    if vein is not None:
        vc, thr, vm = vein
        n1 = vnoise(P * scale + seed, 3)
        n2 = vnoise(P * scale * 2.1 + seed * 2, 5)
        r = np.abs(0.7 * n1 + 0.3 * n2 - 0.5)
        sel = np.isin(mats, vm) & (r < thr) & (ny > -0.45) & owned
        v = np.clip(0.55 + 0.45 * lit + 0.15 * bay, 0.35, 1.0)
        col = np.where(sel[..., None], np.array(vc, float) * v[..., None], col)
    if bolts is not None:
        Z, Y = P[..., 2], P[..., 1]
        d = np.full(Z.shape, 99.0)
        for line in bolts["lines"]:
            for a_, b_ in zip(line[:-1], line[1:]):
                d = np.minimum(d, seg_dist(Z, Y, a_, b_))
        w = bolts.get("w", 0.9)
        sel = np.isin(mats, bolts["mats"]) & (d < w) & (np.abs(N[..., 0]) > 0.3) & owned
        v = np.clip(0.7 + 0.3 * lit + 0.1 * bay, 0.55, 1.0)
        col = np.where(sel[..., None], np.array(bolts["color"], float) * v[..., None], col)
        if bolts.get("glow"):
            glow = glow | sel
    return col, glow


def feather_extra(c, col, glow, mats, ramp, tip, shaft, tip_from=0.8, tip_glow=False, barb=2.0):
    """羽根の面 (loc = 横の長さ・縦の長さ・割合) に、軸の線・羽枝の斜め縞・先の白い帯を描く。
    ramp: 根元から先へ明るくなる階調。tip: 先の帯の色 (2 色)。shaft: 軸の色。"""
    L, bay, lit = c["L"], c["bay"], c["lit"]
    sel = np.isin(c["mats"], mats) & c["owned"]
    u, v, fr = L[..., 0], L[..., 1], L[..., 2]
    ramp = np.array(ramp, float)
    t = np.clip(fr * 0.9 + 0.25 * lit + 0.05 * bay, 0, 0.999)
    k = np.clip(np.floor(t * len(ramp)).astype(int), 0, len(ramp) - 1)
    # 羽枝の斜め縞 (軸から外へ後ろ向きに流れる)
    stripe = (np.floor((v * 2.0 + np.abs(u) * barb) / 2.0) % 2 == 0) & (np.abs(u) > 0.5) & (fr < 0.97)
    k = np.clip(k - stripe.astype(int), 0, len(ramp) - 1)
    out = ramp[k]
    sh = (np.abs(u) < 0.42) & (fr > 0.04)
    out = np.where(sh[..., None], np.array(shaft, float) * np.clip(0.75 + 0.3 * lit, 0.5, 1.1)[..., None], out)
    tp = (fr > tip_from) & (np.abs(u) > 0.42 if False else True)
    tcol = np.where((fr > tip_from + 0.1)[..., None], np.array(tip[1], float), np.array(tip[0], float))
    tcol = np.where(sh[..., None], np.array(shaft, float), tcol)
    out = np.where(tp[..., None], tcol, out)
    col = np.where(sel[..., None], out, col)
    if tip_glow:
        glow = glow | (sel & tp & ~sh)
    return col, glow


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
