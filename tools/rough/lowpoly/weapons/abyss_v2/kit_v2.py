"""五章「深淵」の装備の作り直し (v2) の部品。wkit / lpkit の上に載せる。
座標は他の武器と同じ (1 単位 = 1/16 ブロック、y 上、正面 -Z、広い面は ±Z)。部品はどれも閉じた塊で、継ぎ目は親へ沈める
(入れるときに tools/lib/mesh_close.py が筒の端の穴を塞ぐ)。

材質 (paint_v2.py):
  main  虚の黒紫 (刃・錨の腕)      edge  研いだ縁 (明るい紫鋼)     iron / spine / acc / guard  錨鉄 (青灰の鉄。打ち跡・鋲・錆)
  brass 古い真鍮 (灯の枠・聴き筒)   leather  革の巻き (縫い目)     wrap  布/腱の巻き       cord  紐・縄
  bone  骨 (肋の刃・骨の環)         chain  鎖の鉄                  barn  付着物 (藤壺・塩)  dark  内側の闇
  glass 灯の硝子 (光る)            gem  光る核                     trim  階級の縁 (階級の色)
"""
from __future__ import annotations

import math
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE.parent))
import numpy as np  # noqa: E402
import wkit  # noqa: E402
from wkit import block, blade, prism, curve, extrude, V, rot_x, rot_y, rot_z, nrm, BONE  # noqa: E402,F401
import lpkit  # noqa: E402
from lpkit import ellipsoid, spike, loft  # noqa: E402,F401

CH = 0.34   # 大きめの箱の面取り (wd2 の D2 と同じ考え)


def chamfered(fn):
    """faces 関数を面取りありで呼ぶ"""
    def g(*a, **k):
        wkit.CHAMFER[0] = CH
        try:
            return fn(*a, **k)
        finally:
            wkit.CHAMFER[0] = 0.0
    g.__name__ = fn.__name__
    g.__doc__ = fn.__doc__
    return g


def ell(mat, c, r, segs=8, rings=3, rot=None, deform=None):
    return ellipsoid(BONE, mat, c, r, segs=segs, rings=rings, rot=rot, deform=deform)


def band(mat, c, r, h, sides=8, axis="y", phase=22.5):
    """輪 (柄や胴に巻く帯)。中身の筒より少し太く。端は開いた筒 (中の筒に埋まる)"""
    c = V(*c)
    ax = {"y": V(0, 1, 0), "x": V(1, 0, 0), "z": V(0, 0, 1)}[axis]
    hint = (0, 0, 1) if axis == "y" else (0, 1, 0)
    return prism(mat, tuple(c - ax * h / 2), tuple(c + ax * h / 2), r, r, sides=sides, caps=(True, True), hint=hint, phase=phase)


def disc(mat, c, r, t, sides=8, axis="z", phase=22.5):
    """平たい円盤 (正面向きは axis="z")"""
    return band(mat, c, r, t, sides=sides, axis=axis, phase=phase)


def rivet(c, r=0.32, h=0.3, n=(0, 0, -1), mat="iron"):
    """鋲: 面から少し出る小さな四角錐台 (半分は面に沈める)"""
    n = nrm(V(*n))
    c = V(*c)
    return prism(mat, tuple(c - n * h), tuple(c + n * h * 0.6), r, r * 0.6, sides=4, caps=(True, True),
                 hint=(0, 1, 0) if abs(n[1]) < 0.9 else (1, 0, 0), phase=45)


def link(c, axis_dir, L=1.6, W=1.0, t=0.32, flat="z", mat="chain"):
    """鎖の輪 1 つ: 長さ L・幅 W の楕円の輪を、4 本の箱で。axis_dir = 輪の長い向き、flat = 輪の面の法線の目安"""
    c = V(*c)
    d = nrm(V(*axis_dir))
    f = nrm(V(*{"z": (0, 0, 1), "x": (1, 0, 0), "y": (0, 1, 0)}[flat]) if isinstance(flat, str) else V(*flat))
    s = nrm(np.cross(f, d))
    if np.linalg.norm(np.cross(f, d)) < 1e-6:
        s = nrm(np.cross(V(0, 1, 0), d))
    # 輪の座標系 (x = s 横, y = d 長さ, z = 面の法線)
    R = np.stack([s, d, nrm(np.cross(s, d))], axis=1)
    out = []
    out += block(mat, tuple(c + R @ V(W / 2 - t / 2, 0, 0)), (t, L - t * 0.4, t), rot=R)
    out += block(mat, tuple(c + R @ V(-(W / 2 - t / 2), 0, 0)), (t, L - t * 0.4, t), rot=R)
    out += block(mat, tuple(c + R @ V(0, L / 2 - t / 2, 0)), (W, t, t), rot=R, taper=(0.62, 1.0))
    out += block(mat, tuple(c + R @ V(0, -(L / 2 - t / 2), 0)), (W * 0.62, t, t), rot=R, taper=(1 / 0.62, 1.0))
    return out


def chain_path(pts, L=1.5, W=0.95, t=0.3, mat="chain"):
    """点列に沿った鎖。輪を交互に 90 度回す。輪どうしは端で重ねる"""
    pts = [V(*p) for p in pts]
    out = []
    k = 0
    for a, b in zip(pts, pts[1:]):
        d = b - a
        n = max(1, int(round(np.linalg.norm(d) / (L * 0.72))))
        for i in range(n):
            c = a + d * ((i + 0.5) / n)
            dd = nrm(d)
            flat = V(0, 0, 1) if abs(dd[2]) < 0.9 else V(1, 0, 0)
            if k % 2:
                flat = nrm(np.cross(dd, flat))
            out += link(tuple(c), tuple(dd), L, W, t, flat=tuple(flat), mat=mat)
            k += 1
    return out


def torus(mat, c, R, r, n=10, m=4, axis="z", phase=0.0):
    """輪 (環)。n = 周の分割、m = 断面の分割。axis = 環の面の法線"""
    c = V(*c)
    rings = []
    for i in range(n):
        a = math.radians(phase + 360.0 * i / n)
        ring = []
        for j in range(m):
            b = math.radians(360.0 * j / m + 45)
            rr = R + r * math.cos(b)
            p = V(rr * math.cos(a), rr * math.sin(a), r * math.sin(b))
            if axis == "y":
                p = V(p[0], p[2], p[1])
            elif axis == "x":
                p = V(p[2], p[1], p[0])
            ring.append(c + p)
        rings.append(ring)
    out = []
    for i in range(n):
        A, B = rings[i], rings[(i + 1) % n]
        for j in range(m):
            q = [A[j], A[(j + 1) % m], B[(j + 1) % m], B[j]]
            # 外向き: 断面の中心 (環の芯) を基準に
            a = math.radians(phase + 360.0 * (i + 0.5) / n)
            core = V(R * math.cos(a), R * math.sin(a), 0)
            if axis == "y":
                core = V(core[0], core[2], core[1])
            elif axis == "x":
                core = V(core[2], core[1], core[0])
            f = lpkit._mk(q, BONE, mat, c + core)
            if f is not None:
                out.append(f)
    return out


def lathe(mat, prof, sides=8, phase=22.5, caps=(True, True), mats=None, cx=0.0, cz=0.0):
    """回転体: prof = [(y, r), ...] を y 軸まわりに回す (下から順)。mats = 段ごとの材質"""
    rings = []
    for y, r in prof:
        rings.append([V(cx + r * math.cos(math.radians(phase + 360.0 * k / sides)), y,
                        cz + r * math.sin(math.radians(phase + 360.0 * k / sides))) for k in range(sides)])
    out = []
    for i in range(len(rings) - 1):
        ref = V(cx, (prof[i][0] + prof[i + 1][0]) / 2, cz)
        m = mats[i] if mats else mat
        for k in range(sides):
            q = [rings[i][k], rings[i][(k + 1) % sides], rings[i + 1][(k + 1) % sides], rings[i + 1][k]]
            f = lpkit._mk(q, BONE, m, ref)
            if f is not None:
                out.append(f)
    for idx, cap in zip((0, len(rings) - 1), caps):
        if cap and prof[idx][1] > 1e-6:
            other = V(cx, prof[1 if idx == 0 else -2][0], cz)
            f = lpkit._mk(rings[idx], BONE, mat if cap is True else cap, other)
            if f is not None:
                out.append(f)
    return out


def slab(mat, pts, t, c=(0, 0, 0), rot=None, bevel=0.0, mats=None):
    """凸多角形の板 (xy 平面、厚み t)。bevel > 0 で表裏の面を内へ縮めた面取り (縁が斜面になる)"""
    if bevel <= 0:
        return extrude(mat, pts, t, c=c, rot=rot, mats=mats)
    R = np.eye(3) if rot is None else rot
    c = V(*c)
    cen = np.mean(np.array(pts, float), 0)
    inner = [tuple(cen + (np.array(p) - cen) * (1 - bevel)) for p in pts]
    h = t / 2
    n = len(pts)
    P = lambda x, y, z: R @ V(x, y, z) + c
    mid_f = [P(x, y, -h * 0.45) for x, y in pts]
    mid_b = [P(x, y, h * 0.45) for x, y in pts]
    fr = [P(x, y, -h) for x, y in inner]
    bk = [P(x, y, h) for x, y in inner]
    out = []
    for ring_ in (fr, bk[::-1]):
        f = lpkit._mk(ring_, BONE, mat, c)
        if f:
            out.append(f)
    for i in range(n):
        j = (i + 1) % n
        m = mats[i % len(mats)] if mats else mat
        for q in ([fr[i], fr[j], mid_f[j], mid_f[i]], [mid_f[i], mid_f[j], mid_b[j], mid_b[i]], [mid_b[i], mid_b[j], bk[j], bk[i]]):
            f = lpkit._mk(q, BONE, m, c)
            if f:
                out.append(f)
    return out


def lantern(c, w=2.4, h=3.4, mat="brass", glass="glass", sides=4, roof=1.4, finial=True, base_h=0.7, bar=0.42):
    """角灯: 台座・柱 (sides 本)・硝子 (光る)・屋根・頂の環。c = 硝子の中心。硝子は柱より内側で閉じた塊"""
    x, y, z = c
    f = []
    r = w / 2 / math.cos(math.pi / sides)
    ph = 180.0 / sides if sides == 4 else 0.0
    # 硝子 (光る芯)
    f += lathe(glass, [(y - h / 2, r * 0.82), (y + h / 2, r * 0.82)], sides=sides, phase=ph, cx=x, cz=z)
    # 台座と上の縁
    f += lathe(mat, [(y - h / 2 - base_h, r * 0.9), (y - h / 2 - base_h * 0.4, r * 1.08), (y - h / 2 + 0.15, r * 1.02)], sides=sides, phase=ph, cx=x, cz=z)
    f += lathe(mat, [(y + h / 2 - 0.15, r * 1.02), (y + h / 2 + 0.35, r * 1.1)], sides=sides, phase=ph, cx=x, cz=z)
    # 屋根 (反った四角錐) と頂
    f += lathe(mat, [(y + h / 2 + 0.3, r * 1.18), (y + h / 2 + roof * 0.45, r * 0.7), (y + h / 2 + roof, r * 0.28), (y + h / 2 + roof + 0.25, 0.0)],
               sides=sides, phase=ph, cx=x, cz=z)
    # 柱
    for k in range(sides):
        a = math.radians(ph + 360.0 * k / sides)
        px, pz = x + r * 0.92 * math.cos(a), z + r * 0.92 * math.sin(a)
        f += block(mat, (px, y, pz), (bar, h + 0.1, bar), rot=rot_y(-math.degrees(a)))
    # 硝子の横桟 (表と裏、真ん中の高さ)
    for k in range(sides):
        a = math.radians(360.0 * k / sides + (0 if sides == 4 else 180.0 / sides))
        fx, fz = x + r * 0.8 * math.cos(a), z + r * 0.8 * math.sin(a)
        L = 2 * r * 0.82 * math.sin(math.pi / sides) * 1.02
        f += block(mat, (fx, y + h * 0.08, fz), (0.22, 0.26, L), rot=rot_y(-math.degrees(a)))
    if finial:
        f += torus(mat, (x, y + h / 2 + roof + 0.75, z), 0.55, 0.16, n=8, m=4, axis="z")
    return f


def edge_of(st, y):
    """blade の断面表 st から高さ y の (xl, xr, tf, cx) を線形補間"""
    ys = [s[0] for s in st]
    i = int(np.clip(np.searchsorted(ys, y) - 1, 0, len(st) - 2))
    a, b = st[i], st[i + 1]
    u = (y - a[0]) / (b[0] - a[0]) if b[0] != a[0] else 0
    lerp = lambda k, d=0.0: (a[k] if len(a) > k else d) * (1 - u) + (b[k] if len(b) > k else d) * u
    return lerp(1), lerp(2), lerp(3), lerp(5)
