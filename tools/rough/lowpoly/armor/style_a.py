"""A 外殻 (carapace): 巨神の殻・骨・羽・岩を、体に着けた第二の皮として成形する。
肩が大きく (3 段の傾けた板)、胸は中央が尖る (keel)、背に大きな飾り、腰から脚へ開く。重く、上が大きい輪郭。"""
from __future__ import annotations

from common import *  # noqa: F401,F403
from common import motif, gem, band, back_sig

NAME = "A 外殻 (carapace)"


def helmet(T, rar):
    b = "armorHead"
    f = []
    r0, r1, r2 = rr(25.4, 4.8, -4.8, 4.9, cut=1.1), rr(29.6, 4.9, -4.9, 5.0, cut=1.1), rr(32.7, 3.5, -3.9, 4.1, cut=1.2)
    f += hide_front(shell(b, "main", [r0, r1]), 29.6)
    f += shell(b, "main", [r1, r2], caps=(False, "main"))
    for s in (-1, 1):   # 耳の張り出し
        f += bx(b, "sec", (s * 5.4, 27.6, 1.0), (1.0, 3.4, 5.2), rot=rot_z(-s * 14), taper=(0.7, 0.9))
    f += bx(b, "trim", (0, 30.2, -5.1), (8.8, 0.9, 0.8), skip=("b", "+z"))
    f += gem(b, (0, 30.2, -5.6))
    if T == "whale":
        for z, L in ((-1.6, 4.2), (1.0, 3.8), (3.4, 3.0)):
            f += motif(T, b, (0, 32.5, z), (0, 1, 0.55), L, 1.8, bend=(0, -0.6, 1.4))
    elif T == "tide":
        f += motif(T, b, (-2.2, 32.4, 0.4), (-0.25, 1, 0.1), 4.8, 1.8, bend=(0, 0, 1.0))
        f += motif(T, b, (2.6, 32.4, 1.6), (0.3, 1, 0.1), 3.4, 1.4, bend=(0, 0, 1.0))
    elif T == "thunder":
        for s in (-1, 1):
            f += motif(T, b, (s * 3.0, 32.3, 0.6), (s * 0.3, 0.55, 0.8), 5.6, 1.5)
            f += motif(T, b, (s * 1.8, 32.5, 2.2), (s * 0.15, 0.45, 0.9), 4.4, 1.1)
    else:
        f += motif(T, b, (3.0, 32.4, -0.6), (0.3, 1, 0.1), 4.8, 2.1, bend=(-0.6, 0, 1.6))
        f += motif(T, b, (-2.4, 32.4, 1.4), (-0.2, 1, 0.2), 2.6, 1.6, bend=(0.4, 0, 1.0))
    return {b: f}


def chest(T, rar):
    B = "armorBody"
    f = []
    rings = [rr(12.3, 4.6, -2.7, 2.6, cut=0.9, keel=0.3), rr(16.4, 4.6, -3.0, 2.8, cut=0.9, keel=0.7),
             rr(20.6, 4.6, -3.2, 3.0, cut=0.9, keel=1.1), rr(24.3, 4.6, -2.8, 2.8, cut=0.9, keel=0.5)]
    f += shell(B, "main", rings)
    f += shell(B, "trim", [rr(12.3, 4.85, -3.0, 2.9, cut=0.9, keel=0.35), rr(13.9, 4.85, -3.0, 2.9, cut=0.9, keel=0.35)])
    f += gem(B, (0, 22.7, -4.35), (1.3, 1.3, 0.6)) if T in ("whale", "ember") else []
    if T == "whale":
        for y, w in ((21.2, 3.5), (18.6, 3.3), (16.0, 2.9)):
            f += crv(B, "sec", [(-w, y, -2.7), (-w * 0.5, y - 0.5, -3.7), (0, y - 0.7, -4.3), (w * 0.5, y - 0.5, -3.7), (w, y, -2.7)],
                     [0.55, 0.55, 0.7, 0.55, 0.55], sides=4)
    elif T == "tide":
        pts = [(0, -2.4), (-2.9, -0.4), (-2.5, 1.4), (-1.2, 2.5), (0, 2.8), (1.2, 2.5), (2.5, 1.4), (2.9, -0.4)]
        f += ext(B, "edge", pts, 1.0, c=(0, 19.6, -3.9), rot=rot_z(180))
        f += gem(B, (0, 17.3, -4.6), (1.5, 1.0, 0.6))
    elif T == "thunder":
        f += ext(B, "gem", [(0.2, 22.0), (2.0, 22.0), (0.5, 19.4), (-1.3, 19.4)], 0.7, c=(0, 0, -3.5))
        f += ext(B, "gem", [(-1.3, 19.8), (1.8, 19.8), (0.2, 17.2), (-1.5, 17.2)], 0.7, c=(0, 0, -3.55))
        f += ext(B, "gem", [(-0.4, 17.4), (1.0, 17.4), (-0.6, 14.6), (-1.6, 14.6)], 0.7, c=(0, 0, -3.5))
    else:
        for x in (-1.5, 0, 1.5):
            f += bx(B, "gem", (x, 19.6, -3.5), (0.9, 5.2, 0.9), skip=("b", "u", "+z"))
        f += bx(B, "sec", (0, 22.6, -3.2), (6.2, 0.9, 1.1), skip=("b", "+z"))
        f += bx(B, "sec", (0, 16.6, -3.2), (6.2, 0.9, 1.1), skip=("b", "+z"))
    f += bx(B, "sec", (0, 19.0, 3.3), (4.4, 8.4, 1.1), taper=(0.6, 1.0), skip=("b", "-z"))
    f += back_sig(T, B, 1.0)
    out = {B: f}
    for s in (-1, 1):
        ab = ARM[s]
        g = []
        g += bx(ab, "main", (s * 8.1, 24.4, 0), (7.4, 1.5, 6.4), rot=rot_z(-s * 14), taper=(0.75, 0.8))
        g += bx(ab, "main", (s * 8.9, 22.7, 0), (7.0, 1.3, 5.8), rot=rot_z(-s * 20), taper=(0.75, 0.8))
        g += bx(ab, "main", (s * 9.6, 21.1, 0), (6.2, 1.2, 5.0), rot=rot_z(-s * 26), taper=(0.7, 0.8))
        g += bx(ab, "main", (s * 6.75, 18.0, 0), (4.3, 5.4, 4.9), taper=(0.95, 0.95), skip=("b", "u"))
        g += band(ab, "trim", (s * 6.75, 15.2, 0), (4.6, 0.9, 5.2))
        g += bx(ab, "sec", (s * 6.75, 13.5, 0), (4.4, 3.4, 4.9), skip=("b",))
        if T == "whale" and s == -1:   # 右肩の牙
            for k, (dz, L) in enumerate(((-1.4, 6.0), (0.4, 7.0), (2.0, 5.2))):
                g += motif(T, ab, (-8.4 - k * 0.2, 25.0, dz), (-0.45, 1, 0), L, 2.2, bend=(-1.0, 0, 0))
        elif T == "tide":
            g += motif(T, ab, (s * 8.6, 25.0, 0.2), (s * 0.4, 1, 0), 5.2 if s == -1 else 3.6, 1.9, bend=(s * 0.8, 0, 0))
        elif T == "thunder":
            for k, ang in enumerate((0.55, 0.85, 1.25)):
                g += motif(T, ab, (s * (9.4 + 0.4 * k), 22.4 - 1.3 * k, 0.0), (s * ang, -0.35, 0), 4.8 - 0.4 * k, 1.6, n=(0, 0, 1))
        elif T == "ember" and s == 1:   # 左だけ大肩
            g += bx(ab, "main", (s * 9.8, 26.2, 0), (6.4, 2.2, 5.4), rot=rot_z(-s * 6), taper=(0.7, 0.8))
            g += motif(T, ab, (s * 10.0, 27.2, 0.2), (s * 0.3, 1, 0), 4.4, 2.6, bend=(s * 0.4, 0, 0.8))
        out[ab] = g
    return out


def legs(T, rar):
    out = {}
    for s in (-1, 1):
        lb = LEG[s]
        g = []
        g += bx(lb, "main", (s * 2.15, 9.3, 0), (4.3, 5.4, 4.9), taper=(0.92, 0.92), skip=("u",))
        g += bx(lb, "sec", (s * 4.95, 9.4, 0.0), (1.1, 5.4, 4.2), rot=rot_z(-s * 12), taper=(0.8, 0.9), skip=("b",))
        g += bx(lb, "sec", (s * 2.15, 6.0, -2.75), (3.2, 1.7, 1.3), taper=(0.55, 0.5), skip=("b", "+z"))
        g += band(lb, "trim", (s * 2.15, 6.5, 0), (4.6, 0.7, 5.2))
        if T == "whale":
            g += motif(T, lb, (s * 2.15, 10.6, 3.0), (0, 0.5, 1), 3.2, 1.5, bend=(0, -0.5, 0.8))
        elif T == "tide":
            g += motif(T, lb, (s * 5.2, 10.4, 0.6), (s * 0.6, 0.6, 0.2), 3.4, 1.4, branch=False)
        elif T == "thunder":
            g += motif(T, lb, (s * 5.0, 10.8, 0.2), (s * 0.5, -0.5, 0), 4.6, 1.3, n=(0, 0, 1))
        else:
            g += bx(lb, "gem", (s * 2.15, 11.2, -2.6), (2.4, 0.7, 0.6), skip=("b", "+z"))
        if s == -1 and T != "ember":   # 光る画素を各部位に 1 つ (右の膝の宝石)
            g += gem(lb, (s * 2.15, 8.6, -2.75), (1.2, 1.0, 0.6))
        out[lb] = g
    return out


def boots(T, rar):
    out = {}
    for s in (-1, 1):
        bb = BOOT[s]
        g = []
        g += bx(bb, "main", (s * 2.15, 2.4, 0), (4.5, 4.4, 4.8), taper=(0.9, 0.92), skip=("b",))
        g += bx(bb, "sec", (s * 2.15, 1.0, -3.1), (4.2, 2.0, 2.2), taper=(0.55, 0.5), skip=("b", "+z"))
        g += band(bb, "trim", (s * 2.15, 4.2, 0), (4.9, 0.8, 5.2))
        if T == "whale":
            g += motif(T, bb, (s * 2.15, 3.2, 2.5), (0, 0.2, 1), 2.6, 1.3)
        elif T == "tide":
            g += motif(T, bb, (s * 4.5, 2.6, 0.4), (s * 0.8, 0.3, 0.3), 3.0, 1.2, branch=False)
        elif T == "thunder":
            g += motif(T, bb, (s * 4.5, 3.0, 0.4), (s * 0.7, 0.25, 0.7), 4.0, 1.1, n=(0, 0, 1))
        else:
            g += motif(T, bb, (s * 2.15, 3.4, 2.5), (0, 0.4, 1), 2.8, 1.4)
        if s == -1 and T != "ember":   # 右の靴の甲の宝石
            g += gem(bb, (s * 2.15, 3.0, -2.7), (1.2, 1.0, 0.6))
        out[bb] = g
    return out


def build(T, rar="common"):
    pieces = {"helmet": helmet(T, rar), "chestplate": chest(T, rar), "leggings": legs(T, rar), "boots": boots(T, rar)}
    return {k: [f for bone_faces in v.values() for f in bone_faces] for k, v in pieces.items()}
