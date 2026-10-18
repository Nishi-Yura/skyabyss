"""C 骨組み (frame): 体は薄い布の下着で、その上に巨神の「骨組み」(肋・珊瑚の枝・羽軸・割れた板の帯) が
輪郭を作る。細く長い曲線の部材が胸・背・肩・脚を巡り、体の箱の外へ広がる。すき間が多く、軽く、生き物の構造に見える。"""
from __future__ import annotations

from common import *  # noqa: F401,F403
from common import motif, gem, band, back_sig, halo
from style_b import cape

NAME = "C 骨組み (frame)"
# 伝説の三角形を 1,100 以下に収める 2 巨神: 背の環を軽くし、背の弧・羽の 3 組目 (レアより増える分) をやめる
LEAN = ("whale", "thunder")
FR = dict(whale="sec", tide="acc", thunder="acc", ember="sec")


def arc(bone, mat, pts, r, sides=3):
    n = len(pts)
    ra = [r * 0.85] + [r] * (n - 2) + [r * 0.55]
    return crv(bone, mat, pts, ra, sides=sides, hint=(0, 1, 0), phase=90)


def helmet(T, rar):
    b = "armorHead"
    f = []
    f += shell(b, "trim", [rr(29.6, 4.85, -4.9, 5.0, cut=1.2), rr(31.2, 4.85, -4.9, 5.0, cut=1.2)])
    f += shell(b, "main", [rr(31.2, 4.5, -4.5, 4.7, cut=1.2), rr(33.0, 3.3, -3.4, 3.8, cut=1.2)], caps=(False, "main"))
    for s in (-1, 1):   # 顔の横に立つ骨組みと耳の板
        f += arc(b, FR[T], [(s * 4.9, 29.6, -3.6), (s * 5.3, 26.8, -2.4), (s * 4.8, 25.0, 1.0)], 0.55)
        f += bx(b, "main", (s * 4.9, 27.4, 1.6), (0.9, 4.4, 5.0), taper=(0.8, 0.9), skip=("b",))
    f += bx(b, "main", (0, 27.4, 4.9), (9.0, 4.6, 1.0), taper=(0.9, 1.0), skip=("b", "-z"))
    f += gem(b, (0, 30.4, -5.45))
    if T == "whale":
        for z, L in ((-1.6, 4.4), (1.0, 4.0), (3.4, 3.2)):
            f += motif(T, b, (0, 32.8, z), (0, 1, 0.55), L, 1.9, bend=(0, -0.6, 1.4))
    elif T == "tide":
        f += motif(T, b, (-2.4, 32.8, 0.2), (-0.3, 1, 0.1), 5.0, 1.9, bend=(0, 0, 1.0))
        f += motif(T, b, (2.4, 32.8, 1.4), (0.3, 1, 0.1), 3.6, 1.5, bend=(0, 0, 1.0))
    elif T == "thunder":
        for s in (-1, 1):
            f += motif(T, b, (s * 3.0, 32.6, 0.6), (s * 0.3, 0.55, 0.8), 5.8, 1.5)
            f += motif(T, b, (s * 1.8, 32.8, 2.2), (s * 0.15, 0.45, 0.9), 4.6, 1.2)
    else:
        f += motif(T, b, (3.0, 32.8, -0.6), (0.3, 1, 0.1), 5.0, 2.2, bend=(-0.6, 0, 1.6))
        f += motif(T, b, (-2.4, 32.8, 1.4), (-0.2, 1, 0.2), 2.8, 1.7, bend=(0.4, 0, 1.0))
    if rar in ("rare", "legend"):   # レア: 側頭の飾りを 1 対足す
        for s in (-1, 1):
            f += motif(T, b, (s * 4.2, 31.6, -1.4), (s * 0.7, 0.8, -0.1), 3.4, 1.3, n=(0, 0, 1))
    return {b: f}


def chest(T, rar):
    B = "armorBody"
    f = []
    f += shell(B, "cloth", [rr(12.3, 4.6, -2.7, 2.6, cut=0.9), rr(18.2, 4.6, -2.9, 2.8, cut=0.9), rr(24.2, 4.6, -2.7, 2.7, cut=0.9)])
    f += shell(B, "trim", [rr(12.3, 4.85, -3.1, 3.0, cut=0.9), rr(14.0, 4.85, -3.1, 3.0, cut=0.9)])
    f += bx(B, "main", (0, 18.6, -3.2), (2.2, 10.4, 1.0), taper=(0.7, 1.0), skip=("b", "+z"))
    fm = FR[T]
    for s in (-1, 1):
        for k in range(3 if rar != "common" else 2):
            y = 21.8 - 3.2 * k
            f += arc(B, fm, [(s * 0.9, y + 0.2, -3.5), (s * 2.6, y - 0.2, -3.5), (s * 4.0, y - 0.9, -3.0), (s * 4.5, y - 1.3, -1.4)], 0.62)
    f += gem(B, (0, 23.0, -4.0), (1.3, 1.1, 0.6)) if T in ("whale", "ember") else []
    if T == "tide":
        f += gem(B, (0, 17.2, -3.9), (1.5, 1.0, 0.6))
    elif T == "thunder":
        f += ext(B, "gem", [(0.2, 22.2), (1.6, 22.2), (0.4, 19.4), (-0.9, 19.4)], 0.7, c=(0, 0, -3.95))
        f += ext(B, "gem", [(-0.9, 19.8), (1.4, 19.8), (0.2, 17.2), (-1.1, 17.2)], 0.7, c=(0, 0, -3.95))
    elif T == "ember":
        f += bx(B, "gem", (0, 15.4, -3.7), (2.6, 0.9, 0.7), skip=("b", "+z"))
    f += bx(B, "main", (0, 19.0, 3.2), (2.0, 10.0, 1.0), taper=(0.7, 1.0), skip=("b", "-z"))
    if T in ("whale", "tide"):
        for s in (-1, 1):
            for k in range(2 if (rar != "legend" or T in LEAN) else 3):
                y = 22.6 - 3.4 * k
                pts = [(s * 0.9, y, 3.3), (s * 2.6, y + 0.8, 4.9), (s * 5.0, y + 1.0, 5.5), (s * 6.6, y - 1.0, 5.0)]
                f += arc(B, fm, pts, 0.62)
                if T == "tide":
                    f += spk(B, "acc", V(s * 5.0, y + 1.0, 5.5), V(s * 0.4, 1, 0.1), 2.4 - 0.4 * k, 0.45, sides=3, rings=1)
        if T == "whale":
            f += motif(T, B, (0, 23.4, 3.6), (0, 0.5, 1), 4.0, 1.8, bend=(0, -0.6, 1.0))
    else:
        f += back_sig(T, B, 1.0, nf=2 if (rar != 'legend' or T in LEAN) else 3)
    if rar == "legend":   # 伝説: 背の環と、背の長い布 (祭具の帯)
        f += halo(T, B, fm, lean=T in LEAN)
        f += cape(T, B, "cloth", -2.2, 2.2, 23.2, 12.6, 3.0, 0.8, 2, flare=0.5)
    out = {B: f}
    for s in (-1, 1):
        ab = ARM[s]
        g = []
        g += bx(ab, "cloth", (s * 6.75, 18.0, 0), (4.3, 11.0, 4.8), taper=(0.97, 0.97), skip=("u",))
        g += band(ab, "trim", (s * 6.75, 21.4, 0), (4.6, 0.8, 5.1))
        g += band(ab, "trim", (s * 6.75, 14.4, 0), (4.6, 0.8, 5.1))
        g += bx(ab, "main", (s * 6.75, 13.0, -2.4), (3.4, 2.4, 0.9), skip=("b", "+z"))
        g += bx(ab, "main", (s * 8.2, 24.4, 0), (6.6, 1.2, 5.4), rot=rot_z(-s * 18), taper=(0.7, 0.75))
        g += bx(ab, "main", (s * 9.3, 22.6, 0), (5.2, 1.0, 4.4), rot=rot_z(-s * 28), taper=(0.7, 0.75))
        if T == "whale":
            ks = ((-1.6, 6.8), (0.2, 8.0), (1.8, 5.6)) if s == -1 else ((0.2, 4.8),)
            for k, (dz, L) in enumerate(ks):
                g += motif(T, ab, (s * (8.4 + 0.2 * k), 25.0, dz), (s * 0.45, 1, 0), L, 2.2, bend=(s * 1.2, 0, 0))
        elif T == "tide":
            g += motif(T, ab, (s * 8.4, 25.0, 0.2), (s * 0.35, 1, 0), 5.8 if s == -1 else 4.2, 2.0, bend=(s * 0.8, 0, 0))
        elif T == "thunder":
            for k, ang in enumerate((0.6, 1.2)):
                g += motif(T, ab, (s * (9.2 + 0.3 * k), 23.4 - 1.5 * k, 0.0), (s * ang, 0.5 - 0.35 * k, 0), 5.2 - 0.5 * k, 1.7, n=(0, 0, 1))
        elif T == "ember" and s == 1:
            g += bx(ab, "main", (s * 9.9, 26.2, 0), (6.4, 2.2, 5.4), rot=rot_z(-s * 6), taper=(0.7, 0.8))
            g += motif(T, ab, (s * 10.0, 27.2, 0.2), (s * 0.3, 1, 0), 5.0, 2.8, bend=(s * 0.4, 0, 0.8))
        elif T == "ember":
            g += motif(T, ab, (s * 8.6, 25.0, 0.2), (s * 0.3, 1, 0), 3.0, 1.9, bend=(s * 0.3, 0, 0.6))
        if rar in ("rare", "legend"):
            g += motif(T, ab, (s * 8.9, 15.4, 0.0), (s * 0.9, 0.5, 0), 3.8, 1.3, n=(0, 0, 1))
        out[ab] = g
    return out


def legs(T, rar):
    out = {}
    for s in (-1, 1):
        lb = LEG[s]
        g = []
        cx = s * 2.15
        g += bx(lb, "cloth", (cx, 8.4, 0), (4.3, 7.0, 4.8), taper=(0.96, 0.96), skip=("b", "u"))
        g += band(lb, "trim", (cx, 11.6, 0), (4.6, 0.8, 5.1))
        g += arc(lb, FR[T], [(s * 4.7, 11.8, 0.6), (s * 5.0, 8.4, 0.0), (s * 4.3, 4.8, -1.4)], 0.65)
        g += bx(lb, "main", (cx, 6.8, -2.6), (3.4, 2.2, 1.0), taper=(0.6, 0.6), skip=("b", "+z"))
        if T == "whale":
            g += motif(T, lb, (cx, 10.6, 3.0), (0, 0.5, 1), 3.4, 1.5, bend=(0, -0.5, 0.8))
        elif T == "tide":
            g += motif(T, lb, (s * 4.9, 8.2, 0.6), (s * 0.7, 0.5, 0.2), 3.2, 1.3, branch=False)
        elif T == "thunder":
            g += motif(T, lb, (s * 4.8, 10.4, 0.2), (s * 0.5, -0.7, 0), 5.0, 1.4, n=(0, 0, 1))
        else:
            g += bx(lb, "gem", (cx, 9.0, -2.6), (2.2, 0.7, 0.6), skip=("b", "+z"))
        if s == -1 and T != "ember":   # 光る画素を各部位に 1 つ (右の膝の宝石)
            g += gem(lb, (s * 2.15, 8.6, -2.65), (1.2, 1.0, 0.6))
        out[lb] = g
    return out


def boots(T, rar):
    out = {}
    for s in (-1, 1):
        bb = BOOT[s]
        g = []
        cx = s * 2.15
        g += bx(bb, "cloth", (cx, 2.4, 0), (4.4, 4.4, 4.7), taper=(0.94, 0.94), skip=("b",))
        g += band(bb, "trim", (cx, 3.9, 0), (4.8, 0.8, 5.1))
        g += bx(bb, "main", (cx, 1.0, -3.0), (4.0, 2.0, 2.0), taper=(0.55, 0.5), skip=("b", "+z"))
        g += arc(bb, FR[T], [(s * 4.6, 4.4, 1.6), (s * 5.0, 2.4, -0.6), (s * 4.0, 0.7, -3.0)], 0.55)
        if T == "whale":
            g += motif(T, bb, (cx, 3.2, 2.5), (0, 0.2, 1), 2.8, 1.3)
        elif T == "tide":
            g += motif(T, bb, (s * 4.6, 2.6, 0.4), (s * 0.8, 0.3, 0.3), 3.0, 1.2, branch=False)
        elif T == "thunder":
            g += motif(T, bb, (s * 4.6, 3.0, 0.4), (s * 0.7, 0.25, 0.7), 4.2, 1.1, n=(0, 0, 1))
        else:
            g += motif(T, bb, (cx, 3.4, 2.5), (0, 0.4, 1), 3.0, 1.4)
        if s == -1 and T != "ember":   # 右の靴の甲の宝石
            g += gem(bb, (s * 2.15, 3.0, -2.7), (1.2, 1.0, 0.6))
        out[bb] = g
    return out


def build(T, rar="common"):
    pieces = {"helmet": helmet(T, rar), "chestplate": chest(T, rar), "leggings": legs(T, rar), "boots": boots(T, rar)}
    return {k: [f for bone_faces in v.values() for f in bone_faces] for k, v in pieces.items()}
