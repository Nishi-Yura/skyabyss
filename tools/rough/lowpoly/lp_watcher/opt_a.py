"""A 案「伸びた影」: プレイヤーの輪郭を少しだけ引き伸ばした影。脚と腕が長く、肩が細く、頭が縦に長く前へ傾く。
手足の先は指のない尖り。顔は目の切れ目が 1 本だけ。"""
from __future__ import annotations

from common import *  # noqa

SLATE = [(12, 15, 24), (24, 30, 44), (40, 49, 68), (60, 72, 96), (86, 100, 126)]
PALE = [(30, 38, 54), (52, 64, 86), (78, 92, 118), (110, 126, 152), (140, 156, 180)]
EYE = [(228, 243, 255), (228, 243, 255), (235, 247, 255)]
MIST = [(60, 90, 130), (110, 150, 200), (170, 205, 240)]

PAL = dict(mats={
    "slate": dict(ramp=SLATE, base=0.55, lit=0.32, noise=0.08, min=0, stripe=4, up=0.05),
    "limb": dict(ramp=SLATE, base=0.5, lit=0.3, noise=0.08, min=0),
    "head": dict(ramp=PALE, base=0.5, lit=0.32, noise=0.05, min=0),
    "eye": dict(ramp=EYE, base=0.5, lit=0.2, noise=0.0),
    "mist": dict(ramp=MIST, base=0.6, lit=0.3, noise=0.05),
}, glow_mats=["eye", "mist"])


def extra(ctx, col, glow, alpha):
    """縦の筋 (影が引き伸ばされた痕) と、腕・脚の先ほど薄い色。"""
    col = col.copy()
    P = ctx["P"]
    sel = np.isin(ctx["mats"], ["slate", "limb"]) & ctx["owned"] & ctx["INS"]
    streak = (hash1(np.floor(P[..., 0] * 2.0) + np.floor(P[..., 2] * 2.0) * 7, 4) < 0.16) & sel & (ctx["area"] > 10)
    col[streak] = col[streak] * 0.72
    # 足先・指先へ向かって少し明るく (影がほどける)
    tip = sel & (((P[..., 1] < 7.0) & (np.abs(P[..., 0]) > 4.5)) | (P[..., 1] < 3.0))
    col[tip & (ctx["bay"] > 0.0)] = col[tip & (ctx["bay"] > 0.0)] * 1.35
    return col, glow, alpha


def build():
    F = []
    # ============================================================ 頭 (縦に長く、前へ傾く)
    H = "head"
    hr = [oring(29.0, 1.3, -1.4, 1.3, n=8, zc=-0.2), oring(30.8, 2.3, -2.7, 2.5, n=8, zc=-0.6),
          oring(33.6, 3.0, -3.0, 3.0, n=8, zc=-1.0), oring(36.0, 2.7, -2.7, 2.8, n=8, zc=-1.2),
          oring(37.4, 1.5, -1.5, 1.7, n=8, zc=-1.3)]
    F += loft_skip(H, "head", hr, caps=(False, "head"))
    # 目の切れ目 (真ん中から少しずれた縦の 1 本。顔の面のすぐ手前に、面の傾きに沿って置く)
    ez = lambda y: (float(np.interp(y, [r[0][1] for r in hr], [(r[0][2] + r[7][2]) / 2 for r in hr])) - 0.35)
    ex, hw = -0.55, 0.42
    eye = [V(ex, 31.6, ez(31.6)), V(ex + hw, 32.6, ez(32.6)), V(ex + hw, 34.0, ez(34.0)), V(ex, 35.0, ez(35.0)),
           V(ex - hw, 34.0, ez(34.0)), V(ex - hw, 32.6, ez(32.6))]
    F += poly("eyes", "eye", eye, V(ex, 33.3, ez(33.3) + 3.0))
    # ============================================================ 首と胴 (肩が細い)
    B = "body"
    F += tube(B, "slate", [V(0, 27.0, -0.6), V(0, 29.1, -0.5)], [1.5, 1.2], sides=6, hint=(1, 0, 0), phase=0, caps=(False, False))
    br = [oring(14.0, 3.1, -1.9, 1.9, n=8), oring(19.5, 3.0, -1.9, 2.0, n=8), oring(24.0, 3.7, -2.1, 2.1, n=8, zc=-0.2),
          oring(26.4, 4.3, -2.1, 2.3, n=8, zc=-0.4), oring(27.4, 2.6, -1.7, 1.9, n=8, zc=-0.5)]
    F += loft_skip(B, "slate", br, caps=(False, "slate"))
    # ============================================================ 腕 (膝まで届く長さ。肘が少し後ろへ、先は指のない尖り)
    M = "mantle"
    F += tube(M, "limb", [V(5.3, 26.0, -0.2), V(5.9, 17.5, 0.5), V(5.5, 9.5, -0.3), V(5.5, 4.2, -1.2)],
              [1.8, 1.3, 0.95, 0.0], sides=5, hint=(1, 0, 0), phase=0, caps=(True, False))
    F += tube(M, "limb", [V(-5.3, 26.0, -0.2), V(-5.7, 17.5, -0.4), V(-6.1, 9.8, -1.0), V(-5.6, 3.4, -1.8)],
              [1.8, 1.3, 0.95, 0.0], sides=5, hint=(1, 0, 0), phase=0, caps=(True, False))
    # ============================================================ 脚 (長い。膝が少し後ろへ)
    Hm = "hem"
    for s in (1, -1):
        F += tube(Hm, "limb", [V(s * 2.0, 14.0, -0.3), V(s * 2.2, 7.4, 0.5), V(s * 2.0, 1.4, -0.2)],
                  [1.9, 1.45, 1.0], sides=5, hint=(1, 0, 0), phase=0, caps=(False, False))
        F += spike(Hm, "limb", V(s * 2.0, 1.4, -0.2), (0, -0.2, -1), 6.0, 1.05, sides=4, rings=2, bend=(0, 0, 0), hint=(1, 0, 0))
    # ============================================================ 漂う欠片
    for i, (x, y, z, r) in enumerate([(-6.5, 28.0, -1.0, 0.55), (5.0, 31.5, 1.0, 0.45), (-3.0, 39.5, 0.5, 0.4),
                                      (7.5, 20.0, -2.0, 0.4), (-8.0, 14.0, 1.0, 0.35), (3.5, 3.0, -4.5, 0.35)]):
        F += tetra("mist", "mist", V(x, y, z), r, rot=i * 40.0)
    return dict(faces=F, pal=PAL, extra=extra)
