"""3 案で共有する、巨神ごとの飾り (motif) と小さな部品。"""
from __future__ import annotations

import math

from akit import *  # noqa: F401,F403
from akit import V, bx, ext, feather, spk, crv, rr, shell, nrm, rot_x, rot_y, rot_z, at

TITANS = ["whale", "tide", "thunder", "ember"]
TITAN_JP = dict(whale="岩鯨", tide="潮殻", thunder="雷翼", ember="余燼")
SETS = dict(whale="stone_whale", tide="tide_shell", thunder="thunder_wing", ember="ember")


def motif(T, bone, base, d, L, w, bend=(0, 0, 0), branch=True, n=None):
    """巨神の飾り 1 本: 岩鯨 = 反った鯨骨の棘 / 潮殻 = 枝分かれする珊瑚 / 雷翼 = 白い羽 / 余燼 = 割れた岩の欠片。"""
    base, d = V(*base), nrm(d)
    if T == "whale":
        return spk(bone, "sec", base, d, L, w * 0.55, sides=4, rings=3, bend=bend)
    if T == "tide":
        f = spk(bone, "acc", base, d, L, w * 0.5, sides=4, rings=2, bend=bend)
        if branch and L > 3:
            side = nrm(np.cross(d, V(0, 0, 1)) if abs(d[2]) < 0.9 else np.cross(d, V(1, 0, 0)))
            mid = base + d * L * 0.5 + np.array(bend) * 0.25
            f += spk(bone, "acc", mid, nrm(d + side * 0.9), L * 0.5, w * 0.32, sides=4, rings=1)
            f += spk(bone, "acc", mid, nrm(d - side * 0.9), L * 0.42, w * 0.3, sides=4, rings=1)
        return f
    if T == "thunder":
        return feather(bone, "sec", base, base + d * L + np.array(bend), w, th=0.55, n=n)
    return spk(bone, "main", base, d, L, w * 0.62, sides=3, rings=2, bend=bend, phase=30)


def gem(bone, c, size=(1.4, 0.8, 0.5), rot=None):
    return bx(bone, "gem", c, size, rot=rot, taper=(0.8, 0.8), skip=("b",))


def band(bone, mat, c, size, **k):
    return bx(bone, mat, c, size, skip=("b", "u"), **k)


def fan(bone, mat, c, w, h, thick, rot=None, k=9):
    """扇 (帆立): 蝶番が下、上が丸い凸多角形。c = 蝶番の位置。"""
    pts = [(0, 0)]
    for i in range(k):
        a = math.radians(-65 + 130.0 * i / (k - 1))
        pts.append((math.sin(a) * w, h * (0.15 + 0.85 * math.cos(a * 0.9)) * 1.0))
    # 蝶番から広がる扇の外周 (凸): 左から右へ
    pts = [(0, 0)] + [(math.sin(math.radians(a)) * w, 0.12 * h + math.cos(math.radians(a * 0.85)) * 0.88 * h)
                      for a in (-80, -58, -34, -11, 11, 34, 58, 80)]
    return ext(bone, mat, pts, thick, c=c, rot=rot)


def back_sig(T, B, sc=1.0, y0=22.4, z0=3.5, nf=3):
    """背の大きな飾り。岩鯨 = 背びれの棘 / 潮殻 = 帆立の殻 + 珊瑚 / 雷翼 = 肩甲骨の羽 3 枚ずつ / 余燼 = 割れた岩の欠片。"""
    f = []
    if T == "whale":
        for k, (dy, L) in enumerate(((0, 4.4), (-3.0, 3.8), (-6.0, 3.0))):
            f += motif(T, B, (0, y0 + dy, z0), (0, 0.35, 1), L * sc, 1.8, bend=(0, -0.7, 1.0))
    elif T == "tide":
        f += fan(B, "edge", (0, y0 - 6.2, z0 + 0.1), 4.4 * sc, 8.6 * sc, 0.9, rot=rot_x(-14))
        f += fan(B, "sec", (0, y0 - 5.6, z0 + 1.0), 3.2 * sc, 6.2 * sc, 0.8, rot=rot_x(-14))
        f += motif(T, B, (-3.0 * sc, y0 + 1.2, z0), (-0.45, 0.8, 0.5), 3.6 * sc, 1.4, bend=(0, 0, 0.8))
        f += motif(T, B, (3.0 * sc, y0 + 0.4, z0), (0.45, 0.8, 0.5), 3.0 * sc, 1.3, bend=(0, 0, 0.8))
    elif T == "thunder":
        for s in (-1, 1):
            for k in range(nf):
                f += motif(T, B, (s * (1.0 + 0.3 * k), y0 + 0.4 - 2.0 * k, z0), (s * (0.30 + 0.30 * k), 0.92 - 0.30 * k, 0.28),
                           (8.2 - 0.9 * k) * sc, 1.9 * sc, n=(0, 0, 1))
    else:
        for k, (x, dy, L, dx) in enumerate(((-1.2, 0.2, 5.4, -0.3), (1.6, -2.4, 4.6, 0.35), (-0.5, -4.6, 3.6, -0.15))):
            f += motif(T, B, (x, y0 + dy, z0), (dx, 0.75, 0.7), L * sc, 2.4, bend=(dx, 0, 1.0))
    return f


def halo(T, bone, mat, cy=27.6, R=7.4, z=5.8, span=128, r=0.7):
    """背の環 (伝説): 頭の後ろに立つ開いた輪。両端は背へ支柱でつなぐ。巨神の飾りを上に 3 つ。"""
    import math as _m
    n = 9
    pts = []
    for i in range(n):
        th = _m.radians(-span + 2.0 * span * i / (n - 1))
        pts.append((R * _m.sin(th), cy + R * _m.cos(th), z))
    f = crv(bone, mat, pts, [r] * n, sides=3, hint=(0, 0, 1))
    for end in (pts[0], pts[-1]):
        f += crv(bone, mat, [(end[0], end[1], end[2]), (end[0] * 0.8, end[1] - 1.0, 4.6), (end[0] * 0.62, end[1] - 2.2, 3.5)],
                 [r * 0.9, r * 0.9, r * 0.7], sides=3, hint=(0, 0, 1))
    for k in (0,):
        a = _m.radians(k * 38)
        bx_, by_ = R * _m.sin(a), cy + R * _m.cos(a)
        d = (_m.sin(a), _m.cos(a), 0.25)
        f += motif(T, bone, (bx_, by_, z), d, 3.2 if k else 2.6, 1.4, n=(0, 0, 1))
    return f
