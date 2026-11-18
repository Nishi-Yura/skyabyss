# -*- coding: utf-8 -*-
"""彫り直し (resculpt.py) の上に重ねる「手彫りの層」。心臓など大物用。

材質は絵の色から見分ける (肉・石・骨や貝・目・氷や光・金属)。
1. 形を彫る (距離の場を材質ごとに動かす): 石は割れ目の溝を削り板を段違いに、肉は血管を盛り上げて筋を刻む、骨・貝は細かい穴を掘る。目は触らない。
2. 色を描く: 石の溝の奥は暗く、肉の血管は濃い紅、濡れた肉と目には照り (白い点)、骨の穴は暗い点。
大きさは格子の間隔 h の倍数で決めるので、模型の大きさが違っても点の上で同じ細かさになる。
"""
import colorsys

import numpy as np

import splat_lib as S

FLESH, STONE, PALE, EYE, GLOWY, METAL, OTHER = range(7)


def classify(col):
    """色 (N,3 0..255) → 材質"""
    c = np.clip(col / 255.0, 0, 1)
    hsv = np.array([colorsys.rgb_to_hsv(*x) for x in c]) if len(c) else np.zeros((0, 3))
    hdeg, s, v = hsv[:, 0] * 360, hsv[:, 1], hsv[:, 2]
    m = np.full(len(c), OTHER)
    m[(s < 0.22) & (v < 0.62)] = STONE
    m[(s < 0.28) & (v >= 0.62)] = PALE
    m[((hdeg < 22) | (hdeg > 330)) & (s >= 0.35) & (v > 0.22)] = FLESH
    m[(hdeg >= 30) & (hdeg <= 62) & (s > 0.55) & (v > 0.55)] = EYE
    m[(hdeg >= 165) & (hdeg <= 215) & (s > 0.35) & (v > 0.5)] = GLOWY
    m[(hdeg >= 200) & (hdeg <= 250) & (s >= 0.12) & (s < 0.5) & (v < 0.7)] = METAL
    return m


def displace(D, lo, h, color_at, prof):
    """距離の場 D (格子) を、表面の近くだけ材質ごとに動かす。color_at(P) → (色, 光るか)"""
    band = np.abs(D) < 2.6 * h
    ijk = np.argwhere(band)
    P = ijk * h + lo
    col, lit = color_at(P)
    mat = classify(col)
    d = np.zeros(len(P))
    st = prof.get("stone", 1.0)
    fl = prof.get("flesh", 1.0)
    pa = prof.get("pale", 1.0)
    # 石: 大きな割れ目の溝 + 板の段違い
    f1, f2, idv = S.worley(P, 7.0 * h, 3)
    e = f2 - f1
    groove = np.clip(1 - e / 0.07, 0, 1)
    sm = (mat == STONE) | (mat == METAL)
    d += sm * st * (0.95 * h * groove - 0.35 * h * (idv - 0.5))
    # 肉: 盛り上がった血管 (細胞の縁) + 筋 (波打つ縞)
    v1, v2, vid = S.worley(P, 6.0 * h, 11)
    vein = np.clip(1 - (v2 - v1) / 0.06, 0, 1) * (vid > 0.25)
    wave = np.sin((P @ np.array([0.25, 1.0, 0.15])) / (1.1 * h) + 3.0 * S.worley(P, 9.0 * h, 17)[2])
    fm = mat == FLESH
    d += fm * fl * (-0.7 * h * vein + 0.16 * h * wave)
    # 骨・貝: 細かい穴
    p1, p2, pid = S.worley(P, 2.4 * h, 23)
    pore = (p1 < 0.22) & (pid > 0.55)
    d += (mat == PALE) * pa * pore * 0.55 * h
    # 光る所 (熔岩・光) は彫らない
    d[lit] = 0
    D2 = D.copy()
    D2[band] = D[band] + d.astype(D.dtype)
    return D2


def paint(P, n, col, lit, h, lap):
    """表面の点の色を材質ごとに描き足す (陰を掛ける前の色)。戻り: 色・照り (陰の後で白くする点)"""
    mat = classify(col)
    col = col.copy()
    # 石の溝の奥は暗い
    f1, f2, idv = S.worley(P, 7.0 * h, 3)
    groove = (f2 - f1) < 0.05
    sm = (mat == STONE) | (mat == METAL)
    col[sm & groove] *= 0.45
    col[sm] *= (0.9 + 0.2 * idv[sm])[:, None]
    # 肉の血管は濃い紅
    v1, v2, vid = S.worley(P, 6.0 * h, 11)
    vein = ((v2 - v1) < 0.05) & (vid > 0.25) & (mat == FLESH)
    col[vein] = col[vein] * 0.55 + np.array([70, 0, 30]) * 0.45
    # 骨の穴は暗い点
    p1, p2, pid = S.worley(P, 2.4 * h, 23)
    pore = (p1 < 0.2) & (pid > 0.55) & (mat == PALE)
    col[pore] *= 0.55
    # 照り: 濡れた肉と目 (光の向きと視線の中間の向きに近い面)
    half = np.array([0.25, 0.85, -0.47])
    half /= np.linalg.norm(half)
    spec = np.clip(n @ half, 0, 1)
    shine = ((mat == FLESH) & (spec > 0.975) & (lap > 0)) | ((mat == EYE) & (spec > 0.88)) | ((mat == GLOWY) & (spec > 0.96))
    shine &= ~lit
    return col, shine
