"""一品一案の武器 (2026-10-26 の声「色違いの手抜きはやめて、それぞれの良さが見えるように」) の共通の部品。
形はそれぞれの品の構想から作る (o_whale / o_tide / o_storm / o_ember / o_abyss)。ここには「骨」「椎骨」「石の塊」「垂れ」などの素の部品だけ置く。
座標は他の武器と同じ (1 単位 = 1/16 ブロック、+Y が先、x = 0・z = 0 が握り、広い面は ±Z、正面 = -Z)。部品は親へ沈めて継ぐ。
材質: main edge spine guard grip wrap acc gem trim dark cord cloth soot chain void (../paint.py)。
"""
from __future__ import annotations

import math
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
for p in (HERE.parent, HERE.parent.parent, HERE.parent / "chapter_gear", HERE.parent / "chapter_staves"):
    if str(p) not in sys.path:
        sys.path.insert(0, str(p))
import numpy as np  # noqa: E402
from wkit import block, blade, prism, curve, extrude, feather, at, V, rot_x, rot_y, rot_z, BONE  # noqa: E402,F401
from lpkit import ellipsoid, spike, loft, nrm  # noqa: E402,F401
from st_kit import plume, torus, bolt  # noqa: E402,F401
from td_kit import pearl, barnacles, sash, scallop  # noqa: E402,F401
from wh_kit import rag, charm  # noqa: E402,F401
from em_kit import chain, ridge  # noqa: E402,F401
from paint import line_mask, spot_mask  # noqa: E402,F401
import d_thunder  # noqa: E402

CY = d_thunder.CY
TIP_PULL = d_thunder.TIP_PULL


def ell(mat, c, r, segs=6, rings=2, rot=None, deform=None):
    return ellipsoid(BONE, mat, c, r, segs=segs, rings=rings, rot=rot, deform=deform)


def lump(mat, c, r, seed=1, segs=7, rings=3, rough=0.18, rot=None):
    """ごつごつした塊 (石・玄武岩・殻)。r = (rx, ry, rz)"""
    rng = np.random.default_rng(seed)
    amp = rng.uniform(1 - rough, 1 + rough, (64,))

    def de(p):
        k = int((math.atan2(p[2], p[0]) + math.pi) / (2 * math.pi) * 8 + (p[1] > 0) * 8 + (abs(p[1]) > 0.5 * max(r)) * 16) % 64
        return p * amp[k]
    return ellipsoid(BONE, mat, c, r, segs=segs, rings=rings, rot=rot, deform=de)


def bone(mat, pts, r, knob=True, sides=6):
    """骨: pts に沿う筒、両端に関節の膨らみ"""
    n = len(pts)
    rr = np.broadcast_to(np.asarray(r, float), (n,))
    f = curve(mat, pts, list(rr), list(rr * 0.9), sides=sides, caps=(True, True), phase=30)
    if knob:
        for i in (0, n - 1):
            f += ell(mat, pts[i], (rr[i] * 1.35, rr[i] * 1.2, rr[i] * 1.25), segs=6, rings=2)
    return f


def vertebra(mat, c, R, h, procs=4, plen=None, axis="y", pmat=None, ph=45.0):
    """椎骨: 円盤 (椎体) と放射の突起"""
    c = V(*c)
    ax = {"y": V(0, 1, 0), "x": V(1, 0, 0), "z": V(0, 0, 1)}[axis]
    f = prism(mat, tuple(c - ax * h / 2), tuple(c + ax * h / 2), R, R, sides=8, caps=(True, True),
              hint=(0, 0, 1) if axis != "z" else (1, 0, 0))
    plen = plen or R * 1.1
    for k in range(procs):
        a = math.radians(ph + 360.0 * k / procs)
        if axis == "y":
            d = V(math.cos(a), 0, math.sin(a))
        elif axis == "x":
            d = V(0, math.cos(a), math.sin(a))
        else:
            d = V(math.cos(a), math.sin(a), 0)
        f += spike(BONE, pmat or mat, c + d * R * 0.7, d, plen, h * 0.42, sides=4, rings=1, hint=tuple(ax))
    return f


def strand(mat, p, length, sway=0.4, r=0.28, n=4, z=0.0):
    """垂れ (苔・海藻・房): p から下へ、先が細る"""
    x, y, zz = p
    pts = [(x + sway * math.sin(i * 1.3) * (i / n), y - length * i / n, zz + z * i / n) for i in range(n + 1)]
    rs = [r * (1 - 0.8 * i / n) for i in range(n + 1)]
    return curve(mat, pts, rs, rs, sides=4, caps=(False, True), hint=(0, 0, 1))


def ringy(mat, y, r, h=0.5, cx=0.0, cz=0.0, sides=8):
    """柄に巻く細い輪"""
    return prism(mat, (cx, y - h / 2, cz), (cx, y + h / 2, cz), r, r, sides=sides, caps=(False, False), hint=(0, 0, 1))


def gbar(pts, r=0.26, mat="gem"):
    """光る細い線 (表面に半分沈める)"""
    return curve(mat, pts, [r] * len(pts), [r] * len(pts), sides=3, caps=(True, True), hint=(0, 0, 1), phase=90.0)


def gem(c, rx, ry=None, rz=None, mat="gem", segs=6, rings=2):
    return ell(mat, c, (rx, ry or rx * 1.3, rz or rx), segs=segs, rings=rings)


def arc(cx, cy, R, a0, a1, n, z=0.0, ry=None):
    """xy 平面の弧の点列 (度)"""
    ry = R if ry is None else ry
    return [(cx + R * math.cos(math.radians(a0 + (a1 - a0) * i / (n - 1))), cy + ry * math.sin(math.radians(a0 + (a1 - a0) * i / (n - 1))), z)
            for i in range(n)]


# ---------------------------------------------------------------- 弓の枠 (握り (5, CY)、弦は -x。d_thunder と同じ)
def bend(stage, up):
    """引きの段で、腕の先の三つを弦の側へ寄せる"""
    p = TIP_PULL[stage]
    n = len(up)
    return [(x - (p * (i - (n - 4)) / 3.0 if i >= n - 3 else 0), y, z) for i, (x, y, z) in enumerate(up)]


def mirror_y(path):
    return [(x, 2 * CY - y, z) for x, y, z in path]


def mirror_faces_y(faces):
    """y = CY で鏡に映す (弓の下の腕)"""
    import copy
    out = []
    for f in faces:
        g = copy.deepcopy(f)
        g.verts = (f.verts * np.array([1, -1, 1]) + np.array([0, 2 * CY, 0]))[::-1].copy()
        g.ref = f.ref * np.array([1, -1, 1]) + np.array([0, 2 * CY, 0])
        g.normal = f.normal * np.array([1, -1, 1])
        g.loc = g.verts.copy()
        out.append(g)
    return out


def string_and_arrow(stage, tier, up, lo):
    return d_thunder.string_and_arrow(stage, tier, ((up[-1][0], up[-1][1]), (lo[-1][0], lo[-1][1])))


def inlay_line(mats_, y0, y1, cx=lambda y: 0.0 * y):
    """光る筋の型: エピック = 短い筋 / 伝説 = 長い筋 / +5 点線 / +10 太い筋"""
    def f(env, kind):
        if kind == "rare":
            return line_mask(env, mats_, cx, y0, y0 + (y1 - y0) * 0.4, 0.35)
        if kind == "legend":
            return line_mask(env, mats_, cx, y0, y1, 0.38)
        if kind == "p5":
            return line_mask(env, mats_, cx, y0, y1, 0.36, dash=(1.6, 1.0))
        if kind == "p10":
            return line_mask(env, mats_, cx, y0 - 1, y1 + 1, 0.5)
        return env["lit"] < -9
    return f


# ---------------------------------------------------------------- 伝説の仕上げ (深淵から上の豪華さ: 頭のまわりに浮く淵の欠片と星の粒)
HEADS = {"staff": ((0.0, 18.5, 0.0), 4.4), "hammer": ((0.0, 13.0, 0.0), 5.0), "sword": ((0.0, 13.5, 0.0), 3.6),
         "dagger": ((0.0, 7.5, 0.0), 2.7), "bow": ((7.2, CY, 0.0), 2.2)}


def legend_finish(kind, seed=0):
    """浮く淵の欠片 3 つ (縦長の八面) と星の粒 3 つ。部品は親に触れない (浮いて見える)"""
    (cx, cy, cz), R = HEADS[kind]
    f = []
    for k in range(3):
        a = math.radians(seed * 37 + 120 * k + 20)
        if kind == "bow":
            p = (cx + 0.4 * math.cos(a), cy + (k - 1) * 3.2, cz + R * math.sin(a))
        elif kind in ("sword", "dagger"):
            p = (cx + R * (1 if k % 2 else -1), cy + (k - 1) * R * 1.1, cz + 0.6 * math.sin(a))
        else:
            p = (cx + R * math.cos(a), cy + 0.6 * math.sin(a * 2), cz + R * math.sin(a))
        s = 0.9 if kind != "dagger" else 0.55
        f += ellipsoid(BONE, "void", p, (s, s * 2.0, s), segs=4, rings=1)
        f += ellipsoid(BONE, "gem", (p[0] * 0.92 + cx * 0.08, p[1] + s * 2.4, p[2]), (s * 0.32, s * 0.32, s * 0.32), segs=4, rings=1)
    return f
