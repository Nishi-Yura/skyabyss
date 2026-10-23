"""層の主・歪んだ王の本番モデルの共通部品 (docs/design/15 の 9.7、docs/design/36 の 8.5・9.4)。

ラフ (lord_*.py・king_*.py、docs/review/roughs_abyss/) で選ばれた案を、ボスの出来に作り直すための道具:
  - 形: ab_common / lpkit の部品 (tube・ellipsoid・loft・cone …) に、彫り (sculpt: 頂点を位置の関数でずらす) と、
        骨ごとの部品の付け根を親へ沈める決まり (水密の部品を重ねる。開いた辺なし、中は見えない)。
  - UV: 部品 × 向き (主軸と符号) の島。島の中で重なる面は別の島へ逃がす (同じ画素を二つの面が使わない)。
  - 絵: 材質ごとの決まり (色の段 + 粒の雑音 + 縁の暗い線 + 上からの光 + 足もとの陰)。段に量子化して、
        Bayer のディザで画素の絵にする (マイクラの絵の密度 32/ブロック前後)。光る材質は発光マスクへ。
  - 歪み (王の周): warp(faces, bones, w) が頂点と骨の pivot を同じ関数でずらす (同じ骨・同じ動きのまま)。
  - 書き出し: docs/review/abyss_boss_models/src/<name>/ (メッシュ・アトラス・発光マスク・確かめの絵)。

座標: 1 単位 = 1/16 ブロック、y が上、前 = -Z。
"""
from __future__ import annotations

import copy
import json
import math
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
from ab_common import *  # noqa: E402,F401,F403
import ab_common as AC  # noqa: E402
import ck_ab as AB  # noqa: E402

_ck = AB._ck
ROOT = HERE.parents[3]
OUT = ROOT / "docs/review/abyss_boss_models/src"
PAD = 3

BAYER4 = np.array([[0, 8, 2, 10], [12, 4, 14, 6], [3, 11, 1, 9], [15, 7, 13, 5]], float) / 16.0 - 0.47


# ====================================================================== 形の道具
def P(*a):
    return np.array(a, float)


def sculpt(faces, fn):
    """頂点を位置の関数 fn(p) -> p' でずらす (面の向きは作り直す)。同じ頂点は同じ所へ動くので閉じたまま。"""
    out = []
    for f in faces:
        g = copy.copy(f)
        g.verts = np.array([fn(np.asarray(p, float)) for p in f.verts])
        _ck._refresh(g, f)
        out.append(g)
    return out


def bumpy(amp, freq, seed=1.0, center=None):
    """面を外向きに雑音で凸凹させる fn (中心からの向きに押す)。"""
    def fn(p):
        c = np.zeros(3) if center is None else np.asarray(center, float)
        d = p - c
        n = np.linalg.norm(d)
        if n < 1e-6:
            return p
        k = math.sin(p[0] * freq + seed) * math.sin(p[1] * freq * 1.3 + seed * 2) * math.sin(p[2] * freq * 0.9 + seed * 3)
        return p + d / n * amp * k
    return fn


def mirror(fn_side):
    """左右対称に二度呼ぶ (s = -1 左、+1 右)。"""
    F = []
    for s in (-1, 1):
        F += fn_side(s)
    return F


def side_bone(s, base):
    return base + ("_l" if s < 0 else "_r")


# ====================================================================== 材質 (色の段・模様)
# 段は暗い → 明るい。光る材質 (glow=True) は発光マスクに入る
MATS = {
    "bone":    dict(ramp=[(40, 34, 30), (78, 68, 56), (118, 104, 84), (158, 144, 116), (196, 184, 152), (226, 216, 186)], grain=0.22, crev=0.55, crack=0.18),
    "calc":    dict(ramp=[(34, 32, 34), (64, 60, 58), (98, 92, 84), (134, 126, 112), (168, 160, 142)], grain=0.2, crev=0.6, crack=0.22),
    "tooth":   dict(ramp=[(96, 88, 70), (160, 150, 124), (214, 206, 180), (240, 236, 220)], grain=0.1, crev=0.4),
    "glass":   dict(ramp=[(14, 12, 12), (30, 26, 24), (52, 46, 40), (84, 74, 62)], grain=0.16, crev=0.4, sheen=0.9),
    "hollow":  dict(ramp=[(4, 3, 5), (12, 8, 10), (24, 16, 18)], grain=0.1, crev=0.2),
    "void":    dict(ramp=[(2, 2, 4), (8, 8, 14), (18, 18, 28)], grain=0.1, crev=0.1),
    "iron":    dict(ramp=[(16, 16, 20), (36, 36, 42), (62, 62, 70), (98, 98, 108)], grain=0.14, crev=0.5, rim=0.35),
    "rust":    dict(ramp=[(30, 16, 12), (62, 32, 20), (98, 52, 30), (130, 76, 44)], grain=0.3, crev=0.5),
    "salt":    dict(ramp=[(92, 100, 110), (140, 150, 160), (188, 198, 204), (222, 230, 232), (246, 250, 248)], grain=0.12, crev=0.45, facet=0.25),
    "saltd":   dict(ramp=[(52, 60, 70), (86, 96, 106), (124, 134, 142), (164, 172, 178)], grain=0.2, crev=0.55, crack=0.2),
    "shell":   dict(ramp=[(110, 108, 104), (154, 150, 142), (196, 192, 182), (228, 224, 214), (246, 244, 236)], grain=0.12, crev=0.5, band=0.35),
    "sea":     dict(ramp=[(10, 26, 34), (20, 48, 58), (36, 76, 86), (60, 108, 112), (92, 140, 138)], grain=0.18, crev=0.45),
    "flesh":   dict(ramp=[(40, 12, 16), (74, 22, 28), (112, 38, 42), (150, 62, 60), (184, 96, 88)], grain=0.2, crev=0.5, vein=0.5),
    "meat":    dict(ramp=[(60, 14, 18), (104, 28, 30), (150, 48, 44), (196, 84, 70)], grain=0.24, crev=0.45, vein=0.35),
    "membrane": dict(ramp=[(54, 24, 34), (92, 44, 58), (134, 72, 84), (172, 108, 116)], grain=0.16, crev=0.35, vein=0.6),
    "sinew":   dict(ramp=[(52, 30, 26), (88, 54, 44), (124, 84, 66), (160, 120, 94)], grain=0.3, crev=0.5),
    "ash":     dict(ramp=[(30, 28, 30), (56, 54, 56), (86, 82, 82), (118, 114, 110), (150, 146, 140)], grain=0.3, crev=0.5),
    "cloth":   dict(ramp=[(18, 16, 18), (34, 30, 32), (54, 48, 48), (78, 70, 68), (104, 96, 90)], grain=0.26, crev=0.5, weave=0.3),
    "soot":    dict(ramp=[(6, 6, 8), (14, 12, 14), (26, 22, 24), (42, 36, 36)], grain=0.2, crev=0.3),
    "basalt":  dict(ramp=[(10, 10, 12), (22, 22, 26), (38, 38, 44), (58, 56, 64), (82, 80, 88)], grain=0.2, crev=0.6, crack=0.2),
    "bronze":  dict(ramp=[(26, 52, 50), (40, 84, 78), (62, 120, 108), (96, 160, 140), (140, 196, 172)], grain=0.24, crev=0.55, rim=0.25),
    "copper":  dict(ramp=[(54, 30, 18), (96, 56, 30), (140, 86, 46), (186, 124, 70)], grain=0.2, crev=0.5, rim=0.3),
    "shadow":  dict(ramp=[(4, 6, 10), (10, 14, 22), (18, 26, 38), (30, 42, 58), (50, 66, 86)], grain=0.14, crev=0.3, sheen=0.5),
    "tar":     dict(ramp=[(2, 3, 5), (6, 8, 12), (12, 16, 22), (22, 28, 38)], grain=0.1, crev=0.2, sheen=0.7),
    "wax":     dict(ramp=[(120, 104, 80), (168, 150, 116), (210, 194, 156), (236, 224, 192)], grain=0.12, crev=0.4),
    # 光る
    "teal":    dict(ramp=[(28, 120, 116), (60, 176, 164), (120, 228, 212), (190, 255, 240)], grain=0.1, crev=0.15, glow=True),
    "red":     dict(ramp=[(130, 20, 18), (190, 40, 30), (236, 76, 52), (255, 130, 96)], grain=0.1, crev=0.15, glow=True),
    "ember":   dict(ramp=[(150, 44, 10), (214, 96, 20), (246, 156, 46), (255, 214, 120)], grain=0.14, crev=0.15, glow=True),
    "flame":   dict(ramp=[(230, 120, 30), (252, 176, 60), (255, 222, 130), (255, 246, 210)], grain=0.08, crev=0.05, glow=True),
    "bolt":    dict(ramp=[(110, 150, 210), (164, 200, 240), (212, 232, 252), (240, 250, 255)], grain=0.06, crev=0.1, glow=True),
    "heart":   dict(ramp=[(150, 26, 40), (206, 50, 60), (240, 96, 92), (255, 160, 140)], grain=0.14, crev=0.2, vein=0.4, glow=True),
    "pale":    dict(ramp=[(150, 210, 230), (190, 236, 248), (226, 250, 255)], grain=0.06, crev=0.1, glow=True),
}
# 歪みの割れ目から光る材質 (crackglow): 割れ目の線の画素だけ光る
CRACK_GLOW = {"bone": "red", "calc": "red", "salt": "teal", "saltd": "teal", "basalt": "bolt", "bronze": "bolt",
              "ash": "ember", "cloth": "ember", "shadow": "pale", "tar": "pale", "iron": "bolt"}


def value_noise(p, scale, seed):
    q = p * scale + seed
    i = np.floor(q)
    t = q - i
    t = t * t * (3 - 2 * t)

    def h(ix, iy, iz):
        v = np.sin(ix * 127.1 + iy * 311.7 + iz * 74.7 + seed * 1.3) * 43758.5453
        return v - np.floor(v)

    c = [[[h(i[:, 0] + a, i[:, 1] + b, i[:, 2] + cc) for cc in (0, 1)] for b in (0, 1)] for a in (0, 1)]
    x00 = c[0][0][0] + (c[1][0][0] - c[0][0][0]) * t[:, 0]
    x10 = c[0][1][0] + (c[1][1][0] - c[0][1][0]) * t[:, 0]
    x01 = c[0][0][1] + (c[1][0][1] - c[0][0][1]) * t[:, 0]
    x11 = c[0][1][1] + (c[1][1][1] - c[0][1][1]) * t[:, 0]
    y0 = x00 + (x10 - x00) * t[:, 1]
    y1 = x01 + (x11 - x01) * t[:, 1]
    return y0 + (y1 - y0) * t[:, 2]


LIGHT = np.array([0.35, 0.85, -0.4])
LIGHT = LIGHT / np.linalg.norm(LIGHT)


def shade(mat, p, n, edge, px, py, h, crackglow=0.0, seed=0.0):
    """材質の色 (k,3) と光る印 (k,)。p 位置・n 面の向き・edge 三角形の縁までの近さ (0 縁)・px py 画素・h 体の高さ。"""
    m = MATS.get(mat, MATS["calc"])
    ramp = np.asarray(m["ramp"], float)
    k = len(p)
    lam = np.clip(n @ LIGHT, -1, 1)
    up = np.clip(n[:, 1], -1, 1)
    g = value_noise(p, 0.9, 3.0 + seed)
    fine = value_noise(p, 3.1, 9.0 + seed)
    t = 0.48 + 0.26 * lam + 0.08 * up + m.get("grain", 0.2) * (g - 0.5) + 0.12 * (fine - 0.5)
    # 足もとの陰 (下ほど暗い)
    t -= 0.18 * np.clip(1.0 - p[:, 1] / max(8.0, h * 0.35), 0, 1)
    # 縁の暗い線 (面の境目が読める)
    crev = np.clip((edge - 0.03) / 0.14, 0, 1)
    t -= m.get("crev", 0.5) * 0.35 * (1 - crev)
    if m.get("rim"):
        t += m["rim"] * np.clip(1 - edge / 0.18, 0, 1) * (lam > 0)
    if m.get("band"):
        t += m["band"] * 0.5 * np.sign(np.sin(p[:, 1] * 0.9 + 0.6 * np.sin(p[:, 0] * 0.4)))
    if m.get("facet"):
        t += m["facet"] * (value_noise(p, 0.5, 21.0 + seed) - 0.5)
    if m.get("weave"):
        t += m["weave"] * 0.3 * ((np.floor(px) + np.floor(py)) % 2 - 0.5)
    if m.get("sheen"):
        t += m["sheen"] * np.clip(lam, 0, 1) ** 6
    lit = np.full(k, bool(m.get("glow")))
    crackline = np.zeros(k, bool)
    if m.get("crack") or crackglow > 0:
        r = value_noise(p, 0.32, 31.0 + seed) + 0.25 * value_noise(p, 1.1, 7.0 + seed)
        line = np.abs(r - 0.62) < (0.012 + 0.014 * crackglow) * (1 + m.get("crack", 0.0) * 2)
        crackline = line
        t = np.where(line, t - 0.45, t)
    if m.get("vein"):
        r = value_noise(p, 0.45, 41.0 + seed) + 0.2 * value_noise(p, 1.6, 3.0 + seed)
        vline = np.abs(r - 0.6) < 0.02
        t = np.where(vline, t - m["vein"] * 0.5, t)
    # 段に量子化 (ディザ)
    d = BAYER4[(py.astype(int) % 4), (px.astype(int) % 4)] * 0.9
    idx = np.clip(np.floor(t * len(ramp) + d), 0, len(ramp) - 1).astype(int)
    col = ramp[idx]
    if crackglow > 0 and mat in CRACK_GLOW:
        gm = MATS[CRACK_GLOW[mat]]
        gr = np.asarray(gm["ramp"], float)
        hot = crackline & (value_noise(p, 0.25, 55.0 + seed) < 0.35 + 0.4 * crackglow)
        col = np.where(hot[:, None], gr[np.clip(2 + (fine > 0.5).astype(int), 0, len(gr) - 1)], col)
        lit = lit | hot
    return col, lit


# ====================================================================== UV (部品 × 向きの島。重なりは別の島へ)
def _tri_cover(A, B, C, shrink=0.35):
    lo = np.floor(np.minimum(np.minimum(A, B), C)).astype(int)
    hi = np.ceil(np.maximum(np.maximum(A, B), C)).astype(int)
    xs = np.arange(lo[0], hi[0] + 1) + 0.5
    ys = np.arange(lo[1], hi[1] + 1) + 0.5
    gx, gy = np.meshgrid(xs, ys)
    q = np.stack([gx.ravel(), gy.ravel()], -1)
    T = np.array([[B[0] - A[0], C[0] - A[0]], [B[1] - A[1], C[1] - A[1]]])
    if abs(np.linalg.det(T)) < 1e-9:
        return np.zeros((0, 2), int)
    bc = np.linalg.solve(T, (q - A).T).T
    w = np.stack([1 - bc[:, 0] - bc[:, 1], bc[:, 0], bc[:, 1]], -1)
    # 縁から shrink 画素ぶん内側だけ (隣の面と縁を共有しても重なりにしない)
    per = shrink / max(1e-6, min(np.linalg.norm(B - A), np.linalg.norm(C - A), np.linalg.norm(C - B)))
    ok = (w >= per).all(1)
    return np.floor(q[ok]).astype(int)


def unwrap(faces, tpu):
    groups = {}
    for f in faces:
        n = np.asarray(f.normal, float)
        ax = int(np.argmax(np.abs(n)))
        key = (getattr(f, "part", f.bone), f.bone, ax, bool(n[ax] >= 0))
        groups.setdefault(key, []).append(f)
    charts = []
    for key, fs in groups.items():
        others = [i for i in range(3) if i != key[2]]
        flip = (key[2] == 0 and not key[3]) or (key[2] == 2 and key[3])
        subs = []   # [(faces, occupied set)]
        for f in fs:
            uv = f.verts[:, others] * tpu
            if flip:
                uv = uv * np.array([-1.0, 1.0])
            uv = uv * np.array([1.0, -1.0])  # v は下向き
            f.uv = uv
            cov = set()
            for a, b, c in f.tris():
                for x, y in _tri_cover(uv[a], uv[b], uv[c]):
                    cov.add((int(x), int(y)))
            for sub in subs:
                if not (cov & sub[1]):
                    sub[0].append(f)
                    sub[1].update(cov)
                    break
            else:
                subs.append(([f], set(cov)))
        for fs2, _ in subs:
            pts = np.concatenate([f.uv for f in fs2])
            lo = pts.min(0)
            for f in fs2:
                f.uv = f.uv - lo
            w, h = np.ceil(pts.max(0) - lo) + 1
            charts.append((int(w), int(h), fs2))
    charts.sort(key=lambda c: (-c[1], -c[0]))
    best = None
    for W in (256, 512, 1024, 2048):
        if max(w for w, _, _ in charts) + 2 * PAD > W:
            continue
        x = y = rowh = 0
        place = []
        for w, h, fs in charts:
            W_, H_ = w + 2 * PAD, h + 2 * PAD
            if x + W_ > W:
                x, y, rowh = 0, y + rowh, 0
            place.append((x + PAD, y + PAD, fs))
            x += W_
            rowh = max(rowh, H_)
        used = y + rowh
        H = 64
        while H < used:
            H *= 2
        if H > W:
            continue
        if best is None or W * H < best[0] * best[1]:
            best = (W, H, place)
    W, H, place = best
    for ox, oy, fs in place:
        for f in fs:
            f.uv = f.uv + np.array([ox, oy], float)
    return W, H


def paint(faces, wh, height, crackglow=0.0, seed=0.0):
    SW, SH = wh
    atlas = np.zeros((SH, SW, 4), np.uint8)
    glow = np.zeros((SH, SW), bool)
    owner = np.full((SH, SW), -1, int)
    for fi, f in enumerate(faces):
        cg = crackglow * getattr(f, "cg", 1.0)
        for a, b, c in f.tris():
            A, B, C = f.uv[a], f.uv[b], f.uv[c]
            lo = np.maximum(np.floor(np.minimum(np.minimum(A, B), C)).astype(int) - 1, 0)
            hi = np.minimum(np.ceil(np.maximum(np.maximum(A, B), C)).astype(int) + 2, np.array([SW, SH]))
            xs = np.arange(lo[0], hi[0]) + 0.5
            ys = np.arange(lo[1], hi[1]) + 0.5
            if len(xs) == 0 or len(ys) == 0:
                continue
            gx, gy = np.meshgrid(xs, ys)
            q = np.stack([gx.ravel(), gy.ravel()], -1)
            T = np.array([[B[0] - A[0], C[0] - A[0]], [B[1] - A[1], C[1] - A[1]]])
            if abs(np.linalg.det(T)) < 1e-9:
                continue
            bc = np.linalg.solve(T, (q - A).T).T
            w = np.stack([1 - bc[:, 0] - bc[:, 1], bc[:, 0], bc[:, 1]], -1)
            # 縁の画素も塗る (最近傍で縁の外の画素を拾っても同じ色)
            L = max(1e-6, min(np.linalg.norm(B - A), np.linalg.norm(C - A), np.linalg.norm(C - B)))
            inside = (w >= -0.9 / L).all(1)
            if not inside.any():
                continue
            wi = np.clip(w[inside], 0, None)
            wi = wi / wi.sum(1, keepdims=True)
            qq = q[inside]
            pos = wi @ f.verts[[a, b, c]]
            edge = np.clip(w[inside].min(1) * L / 3.0, 0, 1)
            nn = np.repeat(np.asarray(f.normal, float)[None], len(pos), 0)
            col, lit = shade(f.mat, pos, nn, np.clip(edge, 0, 1), qq[:, 0], qq[:, 1], height, cg, seed)
            xi = np.clip(qq[:, 0].astype(int), 0, SW - 1)
            yi = np.clip(qq[:, 1].astype(int), 0, SH - 1)
            strict = (w[inside] >= 0).all(1)
            free = (owner[yi, xi] < 0) | strict
            xi, yi, col, lit = xi[free], yi[free], col[free], lit[free]
            atlas[yi, xi, :3] = np.clip(col, 0, 255).astype(np.uint8)
            atlas[yi, xi, 3] = 255
            glow[yi, xi] = lit
            owner[yi, xi] = fi
    # 島のまわりへ 2 画素のばす (継ぎ目のにじみ止め)
    for _ in range(2):
        a = atlas[..., 3] > 0
        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            src = np.roll(np.roll(atlas, dy, 0), dx, 1)
            sg = np.roll(np.roll(glow, dy, 0), dx, 1)
            m = (~a) & (src[..., 3] > 0)
            atlas[m] = src[m]
            glow[m] = sg[m]
            a = atlas[..., 3] > 0
    return atlas, glow


# ====================================================================== 歪み (王の周)
def warp_point(p, w, h, lean=1.0, twist=1.0):
    x, y, z = float(p[0]), float(p[1]), float(p[2])
    t = max(0.0, y) / h
    y2 = y * (1 + 0.16 * w)
    x2 = x * (1 + 0.10 * w * math.sin(y / 9.0)) + lean * w * 5.0 * t * t
    z2 = z + twist * w * 2.6 * math.sin(y / 11.0 + 0.8) + w * 3.0 * t * t
    return np.array([x2, y2, z2])


def warp(faces, bones, w, h, **kw):
    fs = sculpt(faces, lambda p: warp_point(p, w, h, **kw))
    bs = {k: (par, tuple(float(v) for v in warp_point(np.asarray(pv, float), w, h, **kw))) for k, (par, pv) in bones.items()}
    return fs, bs


# ====================================================================== 書き出しと確かめ
def tri_count(faces):
    return sum(len(f.verts) - 2 for f in faces)


def export(name, faces, bones, tpu=2.5, crackglow=0.0, seed=0.0, views=None, center=None, scale=4.0):
    d = OUT / name
    d.mkdir(parents=True, exist_ok=True)
    for f in faces:
        f.normal = np.asarray(f.normal, float)
    height = max(float(f.verts[:, 1].max()) for f in faces)
    W, H = unwrap(faces, tpu)
    atlas, glow = paint(faces, (W, H), height, crackglow, seed)
    Image.fromarray(atlas, "RGBA").save(d / f"{name}_atlas.png")
    gm = np.zeros((H, W, 4), np.uint8)
    gm[glow] = (255, 255, 255, 255)
    Image.fromarray(gm, "RGBA").save(d / f"{name}_glowmask.png")
    data = export_mesh(faces, (W, H), d / f"{name}_mesh.json", name, f"{name}_atlas.png", bones, {"glowmask": f"{name}_glowmask.png"}, tpu)
    size = max(W, H)
    sq = np.zeros((size, size, 4), np.uint8)
    sq[:H, :W] = atlas
    sg = np.zeros((size, size), bool)
    sg[:H, :W] = glow
    res = dict(faces=faces, size=size, atlas=sq, glow=sg, data=data, wh=(W, H))
    au = AB.audit(res)
    open_edges = sum(v[0] for v in au.values())
    fl = AB.float_check(faces)
    info = dict(name=name, tris=data["triangle_count"], atlas=[W, H], open=open_edges, floating=fl,
                bones={b: sum(len(f.verts) - 2 for f in faces if f.bone == b) for b in bones}, glow=int(glow.sum()),
                height=round(height, 1))
    (d / f"{name}_info.json").write_text(json.dumps(info, ensure_ascii=False, indent=1), encoding="utf-8")
    print(name, "tris", info["tris"], "atlas", W, H, "open", open_edges, "floating", fl, "glow", info["glow"], "h", info["height"])
    if views is not False:
        preview(name, res, bones, center or (0, height * 0.45, 0), scale, views)
    return res


def export_mesh(faces, wh, path, name, texture, bones, extra, tpu):
    """_ck.export_mesh と同じ形 (横長のアトラスにも対応: u は幅、v は高さで割る)。"""
    W, H = wh
    bs = {b: {"vertices": [], "uvs": [], "triangles": []} for b in bones}
    for f in faces:
        b = bs[f.bone]
        base = len(b["vertices"])
        cen = f.uv.mean(0)
        for p, uv in zip(f.verts, f.uv):
            dd = cen - uv
            nn = np.linalg.norm(dd)
            uv = uv + dd / nn * min(0.02, nn * 0.4) if nn > 1e-9 else uv
            b["vertices"].append([round(float(x) + 0.0, 4) for x in p])
            b["uvs"].append([round(float(uv[0]) / W, 5), round(float(uv[1]) / H, 5)])
        for a, c, e in f.tris():
            b["triangles"].append([base + a, base + c, base + e])
    tot, out = 0, {}
    for k, (parent, pivot) in bones.items():
        b = bs[k]
        b["triangle_count"] = len(b["triangles"])
        tot += b["triangle_count"]
        out[k] = {"parent": parent, "pivot": [float(x) for x in pivot], **b}
    data = {"format_version": 1, "name": name, "units": "1/16 block, y up, front = -Z", "uv": "normalized, origin top-left (v down)",
            "texture": texture, "texture_size": [W, H], "texels_per_unit": tpu, "triangle_count": tot, "bones": out}
    data.update(extra or {})
    Path(path).write_text(json.dumps(data, ensure_ascii=False), encoding="utf-8")
    return data


def preview(name, res, bones, center, scale, poses=None, size=(360, 420)):
    rows = [("前", {}, 180, 6), ("3/4", {}, 215, 12), ("横", {}, 270, 4), ("後ろ", {}, 0, 8)]
    for lab, pose in (poses or {}).items():
        rows.append((lab, pose, 215, 10))
    F = _ck.voxel.font
    W = 16 + len(rows) * (size[0] + 10)
    cv = Image.new("RGB", (W, size[1] + 56), (16, 16, 22))
    dr = ImageDraw.Draw(cv)
    dr.text((14, 8), "%s  %d 三角形  アトラス %dx%d" % (name, res["data"]["triangle_count"], res["wh"][0], res["wh"][1]), font=F(20), fill=(236, 232, 218))
    for i, (lab, pose, yaw, pitch) in enumerate(rows):
        im = _ck.render_view(res, res["atlas"], res["glow"], pose, bones, yaw, pitch, size, center, scale)
        x = 12 + i * (size[0] + 10)
        cv.paste(im, (x, 40))
        dr.text((x + 4, 44), lab, font=F(16), fill=(150, 232, 255))
    cv.save(OUT / name / f"{name}_views.png")
    # 寄りの絵 (3/4 と前、上半分と下半分)
    big = (560, 640)
    cv2 = Image.new("RGB", (big[0] * 3 + 40, big[1] + 20), (16, 16, 22))
    c = np.asarray(center, float)
    hi = c + np.array([0, c[1] * 0.55, 0])
    for i, (yaw, pitch, cc, sc) in enumerate(((215, 12, hi, scale * 2.0), (180, 4, hi, scale * 2.0), (200, 16, c * np.array([1, 0.5, 1]), scale * 2.0))):
        im = _ck.render_view(res, res["atlas"], res["glow"], {}, bones, yaw, pitch, big, tuple(cc), sc, ref=False)
        cv2.paste(im, (10 + i * (big[0] + 10), 10))
    cv2.save(OUT / name / f"{name}_close.png")
