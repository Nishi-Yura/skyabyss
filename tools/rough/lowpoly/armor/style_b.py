"""B 衣と環 (vestments): 布 (頭巾・外套・垂れ布・前垂れ) が体の輪郭を作り、板は肩・腰・腕・胸の要所だけ。
体は細く、縦に長い。顔を覆わない頭巾、背の長い外套 (裾の形が巨神の素材)、肩から広がる袖。"""
from __future__ import annotations

from common import *  # noqa: F401,F403
from common import motif, gem, band

NAME = "B 衣と環 (vestments)"


def hem(T, k):
    """裾の布の細片ごとの下端のずれ (負 = 長い)。None の細片は先が尖る三角。"""
    if T == "whale":
        return [0.0] * k
    if T == "tide":
        return [(-1.2 if i % 2 == 0 else 0.0) for i in range(k)]
    if T == "thunder":
        return [None] * k
    pat = [-1.8, 0.0, -0.8, None, -2.4, -0.4, None, -1.4]
    return [pat[i % len(pat)] for i in range(k)]


def cape(T, bone, mat, xl, xr, ytop, ybot, z, thick, k, flare=0.35, rot=None, ymid_flare=0.0):
    """縦の細片を並べた垂れ布。裾が巨神ごとに違う。"""
    f = []
    hm = hem(T, k)
    for i in range(k):
        x0 = xl + (xr - xl) * i / k
        x1 = xl + (xr - xl) * (i + 1) / k
        c = (x0 + x1) / 2
        fl0 = flare * (x0 - (xl + xr) / 2) / max(1e-6, (xr - xl) / 2)
        fl1 = flare * (x1 - (xl + xr) / 2) / max(1e-6, (xr - xl) / 2)
        if hm[i] is None:
            pts = [(c + (fl0 + fl1) / 2, ybot - 2.2), (x1 + fl1, ybot + 0.6), (x1, ytop), (x0, ytop), (x0 + fl0, ybot + 0.6)]
        else:
            yb = ybot + hm[i]
            pts = [(x0 + fl0, yb), (x1 + fl1, yb), (x1, ytop), (x0, ytop)]
        f += ext(bone, mat, pts, thick, c=(0, 0, z), rot=rot)
    return f


def helmet(T, rar):
    b = "armorHead"
    f = []
    rings = [rr(25.0, 4.9, -4.9, 5.3, cut=1.4), rr(29.6, 4.9, -4.9, 5.2, cut=1.3), rr(32.9, 4.0, -4.2, 5.0, cut=1.4),
             rr(36.0, 1.5, -0.8, 5.6, cut=0.6), rr(37.4, 0.4, 1.2, 6.9, cut=0.2)]
    f += hide_front(shell(b, "cloth", rings), 29.6)
    f += bx(b, "trim", (0, 30.2, -5.05), (8.8, 0.9, 0.8), skip=("b", "+z"))
    f += gem(b, (0, 30.2, -5.55))
    for s in (-1, 1):
        f += bx(b, "trim", (s * 4.95, 30.2, -0.2), (0.8, 0.9, 9.4), skip=("b", "+x" if s > 0 else "-x"))
    if T == "whale":
        for k, (x, L) in enumerate(((-2.6, 2.6), (-1.3, 3.4), (0, 4.0), (1.3, 3.4), (2.6, 2.6))):
            f += motif(T, b, (x, 30.9, -5.0), (x * 0.1, 1, -0.25), L, 1.4, bend=(0, 0, -0.4))
    elif T == "tide":
        f += motif(T, b, (-3.2, 30.8, -4.8), (-0.4, 1, -0.2), 4.8, 1.8, bend=(-0.3, 0, -0.4))
        f += motif(T, b, (2.4, 30.8, -4.8), (0.3, 1, -0.2), 3.4, 1.4, bend=(0.2, 0, -0.3))
    elif T == "thunder":
        for s in (-1, 1):
            f += motif(T, b, (s * 5.2, 30.4, -1.6), (s * 0.85, 0.5, 0.15), 5.4, 1.5, n=(0, 0, 1))
            f += motif(T, b, (s * 5.2, 30.0, 0.4), (s * 0.8, 0.2, 0.4), 4.4, 1.2, n=(0, 1, 0))
    else:
        f += motif(T, b, (3.4, 30.8, -4.6), (0.4, 1, -0.2), 4.8, 2.0, bend=(-0.6, 0, -0.6))
        f += motif(T, b, (-3.4, 30.8, -4.6), (-0.3, 1, -0.2), 2.6, 1.5, bend=(0.4, 0, -0.5))
    return {b: f}


def chest(T, rar):
    B = "armorBody"
    f = []
    f += shell(B, "cloth", [rr(12.3, 4.6, -2.7, 2.6, cut=0.9), rr(18.2, 4.6, -2.9, 2.8, cut=0.9), rr(24.2, 4.6, -2.7, 2.7, cut=0.9)])
    f += shell(B, "trim", [rr(12.3, 4.85, -3.1, 3.0, cut=0.9), rr(14.1, 4.85, -3.1, 3.0, cut=0.9)])
    f += bx(B, "main", (0, 22.6, -3.15), (5.6, 2.2, 1.2), taper=(0.8, 0.9), skip=("b", "+z"))
    f += cape(T, B, "cloth", -1.9, 1.9, 21.6, 12.8, -3.3, 0.8, 2, flare=0.6)
    # 背の長い外套 (胴の部分)
    f += cape("whale", B, "cloth", -3.9, 3.9, 24.0, 12.4, 3.1, 1.0, 4, flare=0.5)
    f += band(B, "trim", (0, 24.1, 3.1), (8.4, 0.8, 1.3))
    if T == "whale":
        f += crv(B, "sec", [(-2.8, 22.4, -3.8), (-1.4, 21.4, -4.3), (0, 21.0, -4.5), (1.4, 21.4, -4.3), (2.8, 22.4, -3.8)],
                 [0.6, 0.6, 0.75, 0.6, 0.6], sides=4)
        f += gem(B, (0, 18.0, -4.3), (1.4, 1.4, 0.6))
        f += motif(T, B, (0, 23.6, 3.8), (0, 0.5, 1), 4.6, 2.0, bend=(0, -0.6, 1.0))
    elif T == "tide":
        pts = [(0, -1.9), (-2.4, -0.3), (-2.0, 1.2), (-0.9, 2.0), (0, 2.2), (0.9, 2.0), (2.0, 1.2), (2.4, -0.3)]
        f += ext(B, "edge", pts, 0.9, c=(0, 17.6, -4.1), rot=rot_z(180))
        f += gem(B, (0, 15.9, -4.65), (1.4, 0.9, 0.6))
        f += motif(T, B, (-2.4, 23.4, 3.8), (-0.4, 0.5, 1), 4.2, 1.6, bend=(0, 0, 0.8))
        f += motif(T, B, (2.0, 22.6, 3.8), (0.4, 0.4, 1), 3.2, 1.3, bend=(0, 0, 0.8))
    elif T == "thunder":
        f += ext(B, "gem", [(0.2, 22.0), (1.9, 22.0), (0.5, 19.2), (-1.2, 19.2)], 0.7, c=(0, 0, -3.8))
        f += ext(B, "gem", [(-1.2, 19.6), (1.7, 19.6), (0.2, 17.0), (-1.4, 17.0)], 0.7, c=(0, 0, -3.85))
        f += ext(B, "gem", [(-0.4, 17.2), (0.9, 17.2), (-0.7, 14.6), (-1.6, 14.6)], 0.7, c=(0, 0, -3.8))
        for s in (-1, 1):
            f += motif(T, B, (s * 2.4, 23.4, 3.9), (s * 0.5, 0.45, 0.5), 5.0, 1.6, n=(0, 0, 1))
    else:
        for x in (-1.2, 0, 1.2):
            f += bx(B, "gem", (x, 19.0, -3.5), (0.8, 4.6, 0.8), skip=("b", "u", "+z"))
        f += motif(T, B, (-1.4, 23.4, 3.8), (-0.2, 0.7, 0.9), 4.4, 2.1, bend=(0.5, 0, 1.0))
    out = {B: f}
    for s in (-1, 1):
        ab = ARM[s]
        g = []
        g += bx(ab, "cloth", (s * 7.7, 21.0, 0), (5.6, 6.6, 5.6), rot=rot_z(-s * 9), taper=(0.78, 0.88), skip=("u",))
        g += bx(ab, "main", (s * 8.0, 24.3, 0), (6.0, 1.2, 5.2), rot=rot_z(-s * 14), taper=(0.78, 0.82))
        g += band(ab, "trim", (s * 6.75, 17.6, 0), (4.5, 0.8, 5.1))
        g += bx(ab, "sec", (s * 6.75, 14.8, 0), (4.4, 5.4, 4.9), taper=(0.96, 0.96), skip=("b", "u"))
        g += bx(ab, "cloth", (s * 6.75, 12.7, 0), (4.8, 2.0, 5.2), skip=("b", "u"))
        if T == "whale" and s == -1:
            g += motif(T, ab, (-9.2, 25.0, 0), (-0.5, 1, 0), 5.2, 2.2, bend=(-1.0, 0, 0))
        elif T == "tide" and s == -1:
            g += motif(T, ab, (-9.0, 24.9, 0.2), (-0.4, 1, 0), 4.6, 1.8, bend=(-0.7, 0, 0))
        elif T == "thunder":
            g += motif(T, ab, (s * 10.4, 20.6, 0.0), (s * 0.45, -1.0, 0), 5.4, 1.6, n=(0, 0, 1))
        elif T == "ember" and s == 1:
            g += bx(ab, "main", (s * 9.4, 26.0, 0), (5.8, 2.0, 4.8), rot=rot_z(-s * 6), taper=(0.7, 0.8))
        out[ab] = g
    return out


def legs(T, rar):
    out = {}
    for s in (-1, 1):
        lb = LEG[s]
        g = []
        g += cape(T, lb, "cloth", s * 2.15 - 1.9, s * 2.15 + 1.9, 12.0, 4.4, -2.95, 0.8, 2, flare=0.4)
        g += cape("whale" if T == "whale" else T, lb, "cloth", s * 2.15 - 2.0, s * 2.15 + 2.0, 12.0, 4.0, 3.15, 0.8, 2, flare=0.4)
        g += bx(lb, "main", (s * 4.7, 9.5, 0), (1.0, 4.6, 4.4), rot=rot_z(-s * 8), taper=(0.8, 0.9), skip=("b",))
        g += band(lb, "trim", (s * 2.15, 10.9, 0), (4.5, 0.8, 5.1))
        g += band(lb, "sec", (s * 2.15, 7.9, 0), (4.4, 1.4, 5.0))
        if s == -1 and T != "ember":   # 光る画素を各部位に 1 つ (右の膝の宝石)
            g += gem(lb, (s * 2.15, 8.6, -3.5), (1.2, 1.0, 0.6))
        out[lb] = g
    return out


def boots(T, rar):
    out = {}
    for s in (-1, 1):
        bb = BOOT[s]
        g = []
        g += bx(bb, "cloth", (s * 2.15, 2.3, 0), (4.4, 4.2, 4.7), taper=(0.94, 0.94), skip=("b",))
        g += bx(bb, "main", (s * 2.15, 1.0, -3.0), (4.0, 2.0, 2.0), taper=(0.6, 0.55), skip=("b", "+z"))
        g += band(bb, "trim", (s * 2.15, 3.6, 0), (4.9, 0.8, 5.2))
        if T == "whale":
            g += motif(T, bb, (s * 2.15, 3.2, 2.5), (0, 0.2, 1), 2.4, 1.1)
        elif T == "tide":
            g += motif(T, bb, (s * 4.5, 2.6, 0.4), (s * 0.8, 0.3, 0.3), 2.8, 1.1, branch=False)
        elif T == "thunder":
            g += motif(T, bb, (s * 4.5, 3.0, 0.4), (s * 0.7, 0.25, 0.7), 3.8, 1.0, n=(0, 0, 1))
        else:
            g += motif(T, bb, (s * 2.15, 3.4, 2.5), (0, 0.4, 1), 2.6, 1.3)
        if s == -1 and T != "ember":   # 右の靴の甲の宝石
            g += gem(bb, (s * 2.15, 3.0, -2.65), (1.2, 1.0, 0.6))
        out[bb] = g
    return out


def build(T, rar="common"):
    pieces = {"helmet": helmet(T, rar), "chestplate": chest(T, rar), "leggings": legs(T, rar), "boots": boots(T, rar)}
    return {k: [f for bone_faces in v.values() for f in bone_faces] for k, v in pieces.items()}
