"""C 案「うつろな殻」: プレイヤーの輪郭の薄い殻 (外と内の二重の面)。背中に縦の割れ目、手足と首は口が開いた筒で、
中は空っぽ。胸と頭の中にだけ、かすかな青白い光が浮いている。頭は肩から少し浮く。目は顔の切れ目の奥の光 1 本。"""
from __future__ import annotations

from common import *  # noqa
from common import _mk  # noqa

SHELL = [(14, 18, 28), (28, 35, 50), (46, 56, 76), (68, 82, 106), (96, 112, 138)]
INNER = [(6, 8, 14), (12, 15, 24), (22, 28, 40), (36, 46, 64)]
LAMP = [(70, 105, 150), (120, 165, 215), (190, 225, 250), (235, 247, 255)]
EYE = [(228, 243, 255), (228, 243, 255), (235, 247, 255)]

PAL = dict(mats={
    "shell": dict(ramp=SHELL, base=0.55, lit=0.32, noise=0.07, min=0, stripe=4, up=0.04),
    "rim": dict(ramp=SHELL, base=0.8, lit=0.2, noise=0.04, min=2),
    "inner": dict(ramp=INNER, base=0.5, lit=0.25, noise=0.1, min=0),
    "lamp": dict(ramp=LAMP, base=0.5, lit=0.3, noise=0.05),
    "eye": dict(ramp=EYE, base=0.5, lit=0.2, noise=0.0),
    "mist": dict(ramp=LAMP, base=0.6, lit=0.3, noise=0.05),
}, glow_mats=["eye", "lamp", "mist"])

WALL = 0.75


def hollow(bone, rings, skip=None, seam=None, ends=(True, True), mat="shell", imat="inner"):
    """二重の殻。rings: 外の輪 (同じ点数)。skip(i, j): その四角 (外・内とも) を作らない。
    seam: (j のリスト) 切れ目の縁の帯を作る、ends: 上下の口の縁の帯を作る (最初・最後の輪)。"""
    inner = [inset_ring(r, WALL) for r in rings]
    F = loft_skip(bone, mat, rings, skip=skip)
    F += flipf(loft_skip(bone, imat, inner, skip=skip))
    k = len(rings[0])
    cen = [np.array(r).mean(0) for r in rings]
    for idx, on in zip((0, len(rings) - 1), ends):
        if not on:
            continue
        other = cen[1] if idx == 0 else cen[-2]
        row = 0 if idx == 0 else len(rings) - 2
        for j in range(k):
            if skip is not None and skip(row, j):
                continue
            quad = [rings[idx][j], rings[idx][(j + 1) % k], inner[idx][(j + 1) % k], inner[idx][j]]
            f = _mk(quad, bone, "rim", other)      # 口の縁: 軸の外側 (口の向き) が表
            if f:
                F.append(f)
    if skip is not None:
        for i in range(len(rings) - 1):
            for j in range(k):
                if not skip(i, j):
                    continue
                for dj, a in ((-1, j), (1, (j + 1) % k)):
                    nb = (j + dj) % k
                    if skip(i, nb):
                        continue
                    quad = [rings[i][a], rings[i + 1][a], inner[i + 1][a], inner[i][a]]
                    ref = (np.array(rings[i][nb]) + np.array(rings[i + 1][nb])) / 2   # 隣の殻側。表は切れ目の側
                    f = _mk(quad, bone, "rim", ref)
                    if f:
                        F.append(f)
    return F


def build():
    F = []
    # ============================================================ 頭 (肩から少し浮く。顔の切れ目と背中の割れ目)
    H = "head"
    A = [8, 26, 72, 116, 160, 200, 244, 288, 334, 352]          # 10 角形。前 (0 度) 寄りの 8〜26 度が目の切れ目
    hr = [oring(25.6, 3.0, -3.2, 3.1, angs=A), oring(26.5, 3.9, -4.0, 3.9, angs=A), oring(31.6, 3.9, -4.0, 3.9, angs=A),
          oring(33.0, 2.9, -3.0, 3.0, angs=A)]
    slit = lambda i, j: (i == 1 and j == 0)
    back = lambda i, j: j == 4
    skip_h = lambda i, j: slit(i, j) or back(i, j)
    F += hollow(H, hr, skip=skip_h, ends=(True, True))
    # 目の光 (切れ目の奥、内側の壁の手前)
    ez = -3.0
    eye = [V(1.6, 27.9, ez), V(2.3, 28.9, ez), V(2.3, 30.7, ez), V(1.6, 31.7, ez), V(0.9, 30.7, ez), V(0.9, 28.9, ez)]
    # 切れ目は 8〜26 度 (x が正) なので、光はその奥に置く。向きは前向き
    F += poly("eyes", "eye", eye, V(1.6, 29.8, ez + 3.0))
    # 頭の中の灯 (後頭部寄りに浮く、小さな八面体)
    F += octa(H, "lamp", V(0.0, 29.8, 0.9), 0.8, 1.1, 0.8)
    # ============================================================ 胴 (背中に縦の割れ目、首と腰が口)
    B = "body"
    N8 = 8
    br = [oring(12.2, 3.6, -2.0, 2.0, n=N8), oring(17.5, 3.6, -2.1, 2.1, n=N8), oring(21.5, 4.0, -2.1, 2.2, n=N8),
          oring(23.8, 4.0, -2.0, 2.1, n=N8)]
    back_b = lambda i, j: j == 3
    F += hollow(B, br, skip=back_b, ends=(True, True))
    # 胸の灯 (背中の割れ目から見える、浮かぶ八面体と、その下の小さな欠片)
    F += octa(B, "lamp", V(0.0, 18.2, 0.2), 1.35, 2.0, 1.1)
    F += octa(B, "lamp", V(0.0, 14.4, 0.4), 0.5, 0.8, 0.5)
    # ============================================================ 腕 (口の開いた筒。指はない)
    M = "mantle"
    for s, wob in ((1, 0.0), (-1, 0.5)):
        path = [V(s * 5.8, 23.0, 0.0), V(s * 6.0, 17.5, 0.3 + wob), V(s * 5.8, 12.0, -0.2 - wob)]
        rr = [oring(p[1], ra, -ra, ra, n=5, phase=0.0, cx=p[0], zc=p[2]) for p, ra in zip(path, (2.2, 1.9, 1.7))]
        F += hollow(M, rr, ends=(True, True))
    # ============================================================ 脚
    Hm = "hem"
    for s in (1, -1):
        path = [V(s * 2.0, 12.0, 0.0), V(s * 2.1, 6.0, 0.1), V(s * 2.0, 0.0, 0.0)]
        rr = [oring(p[1], ra, -ra, ra, n=5, phase=0.0, cx=p[0], zc=p[2]) for p, ra in zip(path, (2.3, 2.1, 2.2))]
        F += hollow(Hm, rr, ends=(True, True))
    # ============================================================ 漂う欠片
    for i, (x, y, z, r) in enumerate([(-6.5, 28.0, -1.0, 0.5), (5.2, 33.5, 1.0, 0.45), (-2.0, 38.0, 0.5, 0.4),
                                      (7.5, 18.0, -2.0, 0.4), (-8.0, 12.0, 1.0, 0.35)]):
        F += tetra("mist", "mist", V(x, y, z), r, rot=i * 40.0)
    return dict(faces=F, pal=PAL, extra=extra)


def extra(ctx, col, glow, alpha):
    """内側の灯 (灯の面と、近くの内壁がかすかに明るい)、外殻の縦筋、口の縁。"""
    col = col.copy()
    P = ctx["P"]
    mats = ctx["mats"]
    owned, INS = ctx["owned"], ctx["INS"]
    # 灯: 面の中心ほど白い (面の縁からの距離で段)
    lamp = (mats == "lamp") & owned
    v = np.clip((ctx["D"] * 0.9 + ctx["bay"] * 0.7), 0, 3).astype(int)
    col[lamp] = np.array(LAMP, float)[np.clip(1 + v, 0, 3)][lamp]
    # 胸・頭の内壁に灯の照り返しをうっすら (光る画素だが暗い色。かすか)
    inner = (mats == "inner") & owned & INS
    for cy, cz, rad in ((18.2, 0.2, 7.0), (29.8, 0.9, 5.0)):
        d = np.sqrt((P[..., 0]) ** 2 + (P[..., 1] - cy) ** 2 * 0.8 + (P[..., 2] - cz) ** 2)
        warm = inner & (d < rad) & (hash2(P[..., 0] * 2.0, P[..., 1] * 2.0, 2) < 0.55 * (1 - d / rad))
        col[warm] = (44, 70, 104)
        glow = glow | warm
    # 切れ目の奥の内壁 (顔の切れ目の裏) は少し明るく
    face = inner & (P[..., 1] > 27.0) & (P[..., 1] < 32.0) & (P[..., 2] < -1.0) & (P[..., 0] > 0.0)
    col[face] = (30, 40, 58)
    return col, glow, alpha
