"""雷翼の巨神 (Thunder Wing titan) 第1回ラフ: 3案 A/B/C。翼を広げて飛ぶ嵐の鳥。

使い方 (worktree の直下で): python -B tools/rough/thunder_wing_titan.py [a b c]
出力: docs/review/thunder_wing_roughs/titan/<a|b|c>/ (geo.json, texture.png, glowmask.png,
      top/side/front/below/back_close/far.png) と比較シート docs/review/thunder_wing_roughs/titan.png

案:
  A 嵐の大鷲   幅の広い翼、指のように開いた初列風切、銅の避雷針の冠、扇の尾。背に風の民の塔
  B 雷の鷺     細長い翼、長い S 字の首、後ろへ流れる長い脚と短い尾、白い飾り羽の冠
  C 雲纏い     A の体に嵐の雲の塊がまとわりつく (翼の付け根・背・尾)。雲の中で稲妻が光る

作り方 (tide_shell_titan.py と同じ):
- 0.5 単位の格子 (1 セル = 1 画素 = 1/32 ブロック) に材料を彫る。1 セルに 1 つの材料。
  同じ材料 (骨ごと) を貪欲に箱にする (重なりは作れない。check_grid で格子に戻して 0 を確かめる)。
- 大きな面 (胴・翼の芯) は 1 単位の区画に寄せて段をそろえ、箱を減らす。顔・冠・門・瘤は 0.5 単位。
- 隣が空いている面だけ uv を書く。すべての面が隠れた箱は捨てる。テクスチャは 2 px / 単位。
- 乱数なし (hz / nzv のハッシュ)。
- 座標: 1 単位 = 1/16 ブロック (ゲームでは 8 倍で描くので 2 単位 = 1 ブロック)。y 上。頭は -Z、尾は +Z。左 = -X。
  翼の幅 (翼端から翼端) 約 400 単位 = ゲームで約 200 ブロック (空の影の 270 より小さくする: 06 の 2章)。
"""
from __future__ import annotations

import json
import math
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageEnhance

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "tools/rough"))
from voxel import load_geo, render, sheet  # noqa: E402

OUT = ROOT / "docs/review/thunder_wing_roughs/titan"
SHEET = ROOT / "docs/review/thunder_wing_roughs/titan.png"

CELL = 2
X0, X1 = -238.0, 238.0
Y0, Y1 = -6.0, 112.0
Z0, Z1 = -184.0, 168.0
NX = int((X1 - X0) * CELL)
NY = int((Y1 - Y0) * CELL)
NZ = int((Z1 - Z0) * CELL)
K = None

PART = {"body": 1, "head": 2, "wing_l": 3, "wing_r": 4, "hand_l": 5, "hand_r": 6, "tail": 7,
        "legs": 8, "crest": 9, "ruins": 10, "clouds": 11}
PART_NAME = {v: k for k, v in PART.items()}
MAT = {"back": 1, "belly": 2, "cov": 3, "sec": 4, "prim": 5, "vein": 6, "copper": 7, "beak": 8,
       "eye": 9, "talon": 10, "leg": 11, "stone": 12, "gate": 13, "knob_w": 14, "knob_t": 15,
       "cloud": 16, "cloud_d": 17, "bolt": 18, "glass": 19, "plume": 20, "brow": 21, "tailf": 22,
       "tip": 23, "edge": 24, "beak_d": 25, "under": 26}
MAT_NAME = {v: k for k, v in MAT.items()}
GLOW = {"vein", "eye", "gate", "knob_w", "knob_t", "bolt", "glass"}

TONES = {
    "back": [(70, 78, 100), (62, 70, 92), (78, 86, 108)],
    "belly": [(214, 216, 224), (198, 202, 212), (226, 228, 234)],
    "cov": [(88, 98, 122), (78, 88, 112), (98, 108, 132)],
    "sec": [(56, 62, 82), (50, 56, 74), (62, 68, 88)],
    "prim": [(44, 48, 64), (38, 42, 56), (50, 54, 70)],
    "tip": [(222, 226, 236), (206, 210, 222)],
    "edge": [(150, 160, 182), (138, 148, 170)],
    "tailf": [(66, 72, 94), (58, 64, 86)],
    "copper": [(204, 116, 72), (186, 102, 62), (222, 140, 92)],
    "beak": [(206, 196, 160), (190, 180, 146)],
    "beak_d": [(70, 64, 60), (58, 54, 52)],
    "talon": [(40, 38, 42), (52, 48, 50)],
    "leg": [(150, 150, 140), (136, 136, 128)],
    "stone": [(196, 198, 204), (180, 182, 190), (166, 168, 178)],
    "cloud": [(206, 210, 222), (190, 196, 210), (220, 224, 234)],
    "cloud_d": [(120, 126, 146), (106, 112, 132)],
    "plume": [(236, 238, 244), (222, 226, 236)],
    "brow": [(36, 38, 50), (44, 46, 58)],
    "under": [(166, 172, 190), (154, 160, 180), (176, 182, 198)],
}
MORTAR = (130, 132, 142)


def hz(*vals):
    h = 2166136261
    for v in vals:
        h = ((h ^ (int(v) & 0xFFFFFFFF)) * 16777619) & 0xFFFFFFFF
    h ^= h >> 15
    h = (h * 2246822519) & 0xFFFFFFFF
    h ^= h >> 13
    return (h & 0xFFFF) / 65536.0


def nzv(a, b):
    h = (np.asarray(a).astype(np.int64) * 73856093) ^ (np.asarray(b).astype(np.int64) * 19349663)
    h = ((h ^ (h >> 13)) * 1274126177) & 0xFFFFFFFF
    h ^= h >> 16
    return (h & 0xFFFF) / 65536.0


def ci(x):
    return int(round((x - X0) * CELL))


def cj(y):
    return int(round((y - Y0) * CELL))


def ck(z):
    return int(round((z - Z0) * CELL))


def inb(v, a, b):
    return (v >= a) & (v <= b)


def fill(fn, box, part=0, mode="empty"):
    """box (単位) の中を fn(X, Y, z) で彫る。fn は (mask, mat) を返す。
    mode: empty = 空いている所だけ / set = 上書き / zero = 削る / replace = 既にある所だけ替える"""
    x0, x1, y0, y1, z0, z1 = box
    i0, i1 = max(ci(x0), 0), min(ci(x1), NX)
    j0, j1 = max(cj(y0), 0), min(cj(y1), NY)
    k0, k1 = max(ck(z0), 0), min(ck(z1), NZ)
    if i1 <= i0 or j1 <= j0:
        return
    xs = X0 + (np.arange(i0, i1) + 0.5) / CELL
    ys = Y0 + (np.arange(j0, j1) + 0.5) / CELL
    X, Y = np.meshgrid(xs, ys, indexing="ij")
    for k in range(k0, k1):
        z = Z0 + (k + 0.5) / CELL
        m, mat = fn(X, Y, z)
        m = np.broadcast_to(np.asarray(m, bool), X.shape)
        if not m.any():
            continue
        sl = K[i0:i1, j0:j1, k]
        if mode == "zero":
            sl[m] = 0
            continue
        kv = np.broadcast_to(np.asarray(mat, np.int64) + part * 32, X.shape).astype(np.int16)
        if mode == "empty":
            w = m & (sl == 0)
        elif mode == "set":
            w = m
        else:
            w = m & (sl != 0)
        sl[w] = kv[w]


def snap1(*a):
    return tuple(np.floor(v) + 0.5 for v in a)


def seg_t(X, Y, z, P, Q):
    """線分 PQ への距離と、線分の上の位置 t (0..1)"""
    P = np.asarray(P, float)
    Q = np.asarray(Q, float)
    d = Q - P
    L2 = float(d @ d)
    px, py, pz = X - P[0], Y - P[1], z - P[2]
    t = np.clip((px * d[0] + py * d[1] + pz * d[2]) / L2, 0, 1)
    dx, dy, dz = px - t * d[0], py - t * d[1], pz - t * d[2]
    return np.sqrt(dx * dx + dy * dy + dz * dz), t


def tube_fn(pts, rads, mat_fn):
    """折れ線の管 (点ごとの半径で先細り)。mat_fn(t_all, X, Y, z) で材料 (t_all は 0..1 の通しの位置)"""
    segs = list(zip(pts[:-1], pts[1:], rads[:-1], rads[1:]))
    lens = [float(np.linalg.norm(np.subtract(b, a))) for a, b, _, _ in segs]
    tot = sum(lens)

    def fn(X, Y, z):
        m = np.zeros(X.shape, bool)
        tall = np.zeros(X.shape)
        acc = 0.0
        for (a, b, ra, rb), L in zip(segs, lens):
            d, t = seg_t(X, Y, z, a, b)
            mm = d <= ra + (rb - ra) * t
            tall = np.where(mm & ~m, (acc + t * L) / tot, tall)
            m |= mm
            acc += L
        return m, mat_fn(tall, X, Y, z)
    pa = np.array(pts, float)
    rm = max(rads) + 1
    box = (pa[:, 0].min() - rm, pa[:, 0].max() + rm, pa[:, 1].min() - rm, pa[:, 1].max() + rm,
           pa[:, 2].min() - rm, pa[:, 2].max() + rm)
    return fn, box


def ell_fn(c, r, mat):
    cx, cy, cz = c
    rx, ry, rz = r

    def fn(X, Y, z):
        d = ((X - cx) / rx) ** 2 + ((Y - cy) / ry) ** 2 + ((z - cz) / rz) ** 2
        return d <= 1.0, (mat(X, Y, z) if callable(mat) else mat)
    return fn, (cx - rx - 1, cx + rx + 1, cy - ry - 1, cy + ry + 1, cz - rz - 1, cz + rz + 1)


# ---------------------------------------------------------------- 寸法 (案ごと)

def params(kid):
    P = dict(
        BY=40.0,                     # 胴の中心の高さ
        body_rx=29.0, body_ry=21.0,  # 胴の半幅・半高
        body_z0=-58.0, body_z1=66.0,  # 胴の前 (胸) と後ろ (腰)
        # 翼: 肩 x=30 → 肘 → 手首 → 翼端。(x, 前縁 z, 後縁 z, 芯の厚み)
        wing=[(26, -34, 46, 24), (60, -40, 44, 21), (95, -42, 40, 18), (125, -40, 34, 16), (150, -36, 26, 14),
              (180, -28, 6, 8)],
        knob_u=150.0,
        dihedral=0.10, gull=6.0,
        knob_r=16.0,
        prim_n=7, prim_len=(58, 40), prim_w=(15, 11), prim_ang=(-12, 44),
        neck=[(0, 45, -52), (0, 48, -68), (0, 51, -80)], neck_r=[16, 14.5, 13],
        head_c=(0, 53, -90), head_r=(14, 13, 17),
        tail="fan", legs="tucked", crest="rods", clouds=False, towers=True,
    )
    if kid == "b":
        P.update(
            body_rx=26.0, body_ry=21.0, body_z0=-50.0, body_z1=58.0,
            wing=[(24, -26, 34, 18), (60, -30, 28, 16), (100, -30, 22, 14), (135, -27, 16, 12), (160, -22, 10, 10),
                  (186, -16, 0, 7)],
            knob_u=160.0,
            dihedral=0.05, gull=3.0, knob_r=12.0,
            prim_n=6, prim_len=(52, 36), prim_w=(11, 8), prim_ang=(-4, 28),
            neck=[(0, 44, -46), (0, 40, -70), (0, 46, -96), (0, 56, -118), (0, 59, -132)],
            neck_r=[13, 10, 8.5, 8, 8],
            head_c=(0, 61, -140), head_r=(9.5, 9.5, 12),
            tail="short", legs="trailing", crest="plumes", towers=False,
        )
    if kid == "c":
        P.update(clouds=True, towers=False)
    return P


def interp(u, pts, idx):
    xs = [p[0] for p in pts]
    return np.interp(u, xs, [p[idx] for p in pts])


def wing_center_y(u, P):
    """翼の芯の高さ: 上反角と、付け根近くの軽いかもめ形 (肩で少し上がり、肘で下がる)"""
    return P["BY"] + 4.0 + P["dihedral"] * (u - 26) + P["gull"] * np.sin(np.clip((u - 26) / 70.0, 0, 1) * math.pi) * 0.6


# ---------------------------------------------------------------- 胴・頭・首

def body_fn(P):
    BY, rx0, ry0, z0, z1 = P["BY"], P["body_rx"], P["body_ry"], P["body_z0"], P["body_z1"]
    zc = (z0 + z1) / 2
    hl = (z1 - z0) / 2

    def fn(X, Y, z):
        Xs, Ys = snap1(X, Y)
        t = (z - zc) / hl
        if abs(t) > 1:
            return np.zeros(X.shape, bool), MAT["back"]
        # 卵形: 胸 (前) が太く、腰 (後ろ) へ細る
        k = (1 - t * t) ** 0.5 * (1.0 - 0.18 * t)
        rx, ry = rx0 * k, ry0 * k
        # 胸の竜骨: 腹の真ん中が下へ張り出す
        keel = 5.0 * np.clip(1 - np.abs(Xs) / 10.0, 0, 1) * max(0.0, 1 - abs(t + 0.25) * 1.4)
        yc = BY - 2.0 * t
        dy = np.where(Ys < yc, (Ys - yc) / (ry + keel), (Ys - yc) / ry)
        d = (Xs / rx) ** 2 + dy ** 2
        m = d <= 1.0
        # 背の羽の列 (鱗のような段): 上の面だけ +0.5 の段
        mat = np.where(Ys < yc - ry * 0.25, MAT["belly"], MAT["back"])
        return m, mat
    return fn, (-P["body_rx"] - 2, P["body_rx"] + 2, BY - 40, BY + 32, z0 - 1, z1 + 1)


def head_parts(P, kid):
    out = []
    # 首
    fn, bx = tube_fn(P["neck"], P["neck_r"],
                     lambda t, X, Y, z: np.where(Y < np.interp(t, [0, 1], [P["neck"][0][1], P["neck"][-1][1]]) - 4,
                                                 MAT["belly"], MAT["back"]))
    out.append((fn, bx, "head"))
    hc, hr = P["head_c"], P["head_r"]

    def head_mat(X, Y, z):
        m = np.full(X.shape, MAT["back"])
        m = np.where(Y < hc[1] - hr[1] * 0.35, MAT["belly"], m)     # 喉と頬は白
        return m
    fn, bx = ell_fn(hc, hr, head_mat)
    out.append((fn, bx, "head"))
    # 眉 (目の上の張り出し、暗い): 頭の横の上
    for s in (-1, 1):
        ex = s * (hr[0] - 1.5)
        fn, bx = ell_fn((ex, hc[1] + 3.5, hc[2] - 3.5), (2.6, 1.6, 6.5), MAT["brow"])
        out.append((fn, bx, "head"))
    # 嘴 (鉤): 付け根は太く、先が下へ曲がる
    zf = hc[2] - hr[2]
    bpts = [(0, hc[1] - 1.5, zf + 4), (0, hc[1] - 2.5, zf - 6), (0, hc[1] - 5.5, zf - 14), (0, hc[1] - 11, zf - 17),
            (0, hc[1] - 13.5, zf - 14.5)]
    brad = [6.5, 5.0, 3.6, 2.2, 1.1] if kid != "b" else [5.0, 3.6, 2.6, 1.6, 0.9]
    if kid == "b":   # 鷺: 長くまっすぐな嘴 (鉤は小さい)
        bpts = [(0, hc[1] - 1.5, zf + 3), (0, hc[1] - 2.5, zf - 10), (0, hc[1] - 4.0, zf - 24), (0, hc[1] - 5.5, zf - 32),
                (0, hc[1] - 7.0, zf - 34)]
    fn, bx = tube_fn(bpts, brad, lambda t, X, Y, z: np.where(t > 0.72, MAT["beak_d"], MAT["beak"]))
    out.append((fn, bx, "head"))
    return out


def eye_cut(P):
    """目: 頭の横を 0.5 くぼませ、奥に光る目を入れる (眉の下)"""
    hc, hr = P["head_c"], P["head_r"]
    res = []
    for s in (-1, 1):
        cx = s * (hr[0] - 0.2)
        cy, cz = hc[1] + 1.0, hc[2] - 4.0

        def zero(X, Y, z, cx=cx, cy=cy, cz=cz):
            return (np.abs(X - cx) <= 1.2) & (np.abs(Y - cy) <= 1.6) & (np.abs(z - cz) <= 2.4), 0

        def eye(X, Y, z, cx=cx, cy=cy, cz=cz, s=s):
            m = (np.abs(X - (cx - s * 0.9)) <= 0.6) & (np.abs(Y - cy) <= 1.4) & (np.abs(z - cz) <= 2.2)
            return m, MAT["eye"]
        bx = (cx - 3, cx + 3, cy - 3, cy + 3, cz - 4, cz + 4)
        res.append((zero, eye, bx))
    return res


# ---------------------------------------------------------------- 翼

def wing_fn(P, s, u_lo=-1e9, u_hi=1e9):
    W = P["wing"]
    u_end = W[-1][0]

    def fn(X, Y, z):
        Xs, Ys = snap1(X, Y)
        zz = math.floor(z) + 0.5
        u = s * Xs
        inner = (u >= W[0][0] - 8) & (u <= u_end + 2) & (u >= u_lo) & (u < u_hi)
        zle = interp(u, W, 1)
        zte0 = interp(u, W, 2)
        # 次列風切の先: 10 単位ごとの羽の先 (後ろへ三角に出る)
        fr = np.mod(u / 10.0, 1.0)
        zte = zte0 + 4.0 * (1 - np.abs(2 * fr - 1))
        chord = np.maximum(zte - zle, 1.0)
        f = (zz - zle) / chord
        tmax = interp(u, W, 3)
        # 厚み: 前縁の骨のあたり (f 0.12) が一番厚く、後縁へ薄くなる
        prof = np.where(f < 0.14, np.sqrt(np.clip(f / 0.14, 0, 1)) * 0.85 + 0.15,
                        np.clip(1 - (f - 0.14) / 0.86, 0, 1) ** 1.6 * 0.92 + 0.08)
        t = np.maximum(tmax * prof, 2.0)
        yc = wing_center_y(u, P)
        top = yc + t * 0.62
        bot = yc - t * 0.38
        m = inner & (f >= 0) & (f <= 1) & (Ys >= bot) & (Ys <= top)
        # 材料: 上の面は前から 雨覆 (cov) → 次列風切 (sec)、下の面は白
        mat = np.where(f < 0.5, MAT["cov"], MAT["sec"])
        mat = np.where((f >= 0.5) & (f < 0.58), MAT["edge"], mat)        # 雨覆の縁の明るい帯
        mat = np.where(f > 0.9, MAT["tip"], mat)                         # 次列風切の先は白
        under = Ys < yc - t * 0.18
        mat = np.where(under, np.where(f > 0.72, MAT["sec"], np.where(f > 0.66, MAT["edge"], MAT["under"])), mat)
        # 稲妻の筋 (上の面だけ)。手首から肩へ向かって折れながら走る 2 本と、そこから枝分かれする短い筋
        topmost = Ys >= top - 1.0
        kz = np.floor(u / 9.0)
        zig1 = 0.24 + 0.07 * np.where(np.mod(kz, 2) == 0, 1.0, -1.0) * (np.mod(u, 9.0) / 9.0 * 2 - 1)
        zig2 = 0.44 + 0.06 * np.where(np.mod(kz + 1, 2) == 0, 1.0, -1.0) * (np.mod(u, 9.0) / 9.0 * 2 - 1)
        w_ = 1.0
        vein = (np.abs(f - zig1) * chord <= w_) | ((np.abs(f - zig2) * chord <= w_) & (u > 70))
        # 枝: 18 単位ごとに後ろへ短く
        br = (np.mod(u, 18.0) < 1.2) & (f > zig1) & (f < zig1 + 0.16)
        vein = topmost & (vein | br) & (u > 38) & (f < 0.9)
        mat = np.where(vein, MAT["vein"], mat)
        return m, mat
    x_lo, x_hi = (W[0][0] - 8, u_end + 4)
    box = (x_lo, x_hi) if s > 0 else (-x_hi, -x_lo)
    return fn, (box[0], box[1], P["BY"] - 20, P["BY"] + 50, -60, 60)


def knob_fn(P, s):
    """手首の瘤: 銅の殻に光る割れ目。左 (-X) は白青 (風)、右 (+X) は金 (雷)"""
    u = P["knob_u"]
    R = P["knob_r"]
    c = (s * u, float(wing_center_y(u, P)) + 2.5, float(interp(u, P["wing"], 1)) + R * 0.75)
    gm = MAT["knob_w"] if s < 0 else MAT["knob_t"]

    def fn(X, Y, z):
        # 平たい瘤 (高さは半径の 0.62)。上の面だけ銅の板で覆われ、割れ目が光る。下は羽
        d = ((X - c[0]) ** 2 + ((Y - c[1]) / 0.62) ** 2 + (z - c[2]) ** 2) ** 0.5
        m = d <= R
        cr = np.abs(np.sin(0.55 * X + 0.9) + np.sin(0.62 * Y) + np.sin(0.5 * z + 1.7))
        up = Y > c[1] + 1.5
        plate = up & (d > R - 2.0)
        mat = np.where(plate & (cr < 0.42), gm, np.where(up, MAT["copper"], MAT["under"]))
        mat = np.where(~up & (Y > c[1] - 1.5), MAT["cov"], mat)
        return m, mat
    return fn, (c[0] - R - 1, c[0] + R + 1, c[1] - R, c[1] + R, c[2] - R - 1, c[2] + R + 1), c


def primaries(P, s, knob_c):
    """初列風切: 手首から指のように開く羽 (先は白、軸に稲妻の筋)"""
    out = []
    n = P["prim_n"]
    L0, L1 = P["prim_len"]
    w0, w1 = P["prim_w"]
    a0, a1 = P["prim_ang"]
    ux = abs(knob_c[0])
    W = P["wing"]
    u_r = (ux + W[-1][0]) / 2 + 4                 # 羽の根元は手首と翼の先の間 (手の楔に重なる所は空きだけ埋まる)
    zl = float(interp(u_r, W, 1))
    zt = float(interp(u_r, W, 2)) + 4
    for i in range(n):
        f = i / max(n - 1, 1)
        ang = math.radians(a0 + (a1 - a0) * f)
        L = L0 + (L1 - L0) * f
        w = w0 + (w1 - w0) * f
        rz = zl + 3 + f * (zt - zl - 2)
        root = np.array([s * (u_r - 6 * f), float(wing_center_y(np.array(u_r), P)) - 0.5 - 1.0 * f, rz])
        dirv = np.array([s * math.cos(ang), 0.10 - 0.05 * f, math.sin(ang)])
        tip = root + dirv * L

        def fn(X, Y, z, root=root, tip=tip, w=w, i=i):
            Xs = np.floor(X) + 0.5
            d2 = tip - root
            L2 = float(d2[[0, 2]] @ d2[[0, 2]])
            px, pz = Xs - root[0], z - root[2]
            t = np.clip((px * d2[0] + pz * d2[2]) / L2, 0, 1)
            qx, qz = px - t * d2[0], pz - t * d2[2]
            dperp = np.sqrt(qx * qx + qz * qz)
            half = w * 0.5 * np.where(t < 0.8, 1.0, 1 - (t - 0.8) / 0.2 * 0.85)
            yl = root[1] + t * d2[1] - 0.5 * t * t * 6.0          # 先が少し下がる
            th = 3.2 * (1 - 0.6 * t)
            m = (dperp <= half) & (np.abs(Y - yl) <= th / 2)
            mat = np.where(t > 0.78, MAT["tip"], MAT["prim"])
            shaft = (dperp <= 0.6) & (Y >= yl + th / 2 - 1.0) & (t > 0.08) & (t < 0.9)
            mat = np.where(shaft, MAT["vein"], mat)
            return m, mat
        lo = np.minimum(root, tip) - w - 4
        hi = np.maximum(root, tip) + w + 4
        out.append((fn, (lo[0], hi[0], lo[1] - 6, hi[1] + 4, lo[2], hi[2])))
    return out


# ---------------------------------------------------------------- 尾・脚

def tail_parts(P):
    out = []
    BY, z1 = P["BY"], P["body_z1"]
    base = np.array([0.0, BY + 1.0, z1 - 10])
    if P["tail"] == "fan":
        n = 11
        for i in range(n):
            f = i / (n - 1)
            ang = math.radians(-46 + 92 * f)
            L = 74 - 10 * abs(f - 0.5) * 2
            d = np.array([math.sin(ang), 0.06, math.cos(ang)])
            tip = base + d * L

            def fn(X, Y, z, tip=tip, i=i):
                Xs = np.floor(X) + 0.5
                d2 = tip - base
                L2 = float(d2[[0, 2]] @ d2[[0, 2]])
                px, pz = Xs - base[0], z - base[2]
                t = np.clip((px * d2[0] + pz * d2[2]) / L2, 0, 1)
                qx, qz = px - t * d2[0], pz - t * d2[2]
                dp = np.sqrt(qx * qx + qz * qz)
                half = 3.0 + 4.0 * t
                yl = base[1] + t * d2[1] + 0.8 * ((i % 2) - 0.5)
                m = (dp <= half) & (np.abs(Y - yl) <= 1.5) & (t > 0.05)
                mat = np.where(t > 0.86, MAT["tip"], np.where((t > 0.66) & (t < 0.74), MAT["edge"], MAT["tailf"]))
                return m, mat
            tip_lo = np.minimum(base, tip) - 12
            tip_hi = np.maximum(base, tip) + 12
            out.append((fn, (tip_lo[0], tip_hi[0], BY - 6, BY + 10, tip_lo[2], tip_hi[2]), "tail"))
    else:
        n = 7
        for i in range(n):
            f = i / (n - 1)
            ang = math.radians(-22 + 44 * f)
            L = 34
            d = np.array([math.sin(ang), 0.02, math.cos(ang)])
            tip = base + d * L
            fn, bx = tube_fn([tuple(base), tuple(tip)], [3.0, 2.0],
                             lambda t, X, Y, z: np.where(t > 0.8, MAT["tip"], MAT["tailf"]))
            out.append((fn, bx, "tail"))
    return out


def leg_parts(P):
    out = []
    BY = P["BY"]
    for s in (-1, 1):
        if P["legs"] == "tucked":
            hip = (s * 12, BY - 14, 26)
            fn, bx = ell_fn(hip, (8, 8, 11), MAT["belly"])
            out.append((fn, bx, "legs"))
            pts = [(s * 12, BY - 18, 32), (s * 11, BY - 22, 44), (s * 10, BY - 21, 52)]
            fn, bx = tube_fn(pts, [3.2, 2.6, 2.4], lambda t, X, Y, z: np.full(X.shape, MAT["leg"]))
            out.append((fn, bx, "legs"))
            for j in range(4):   # 握った爪
                a = math.radians(-50 + j * 33)
                p0 = (s * 10, BY - 21, 52)
                p1 = (s * 10 + s * 3.5 * math.cos(a), BY - 24.5, 52 + 3.5 * math.sin(a) + 2)
                p2 = (p1[0], BY - 26.5, p1[2] - 2.0)
                fn, bx = tube_fn([p0, p1, p2], [1.6, 1.2, 0.7],
                                 lambda t, X, Y, z: np.where(t > 0.5, MAT["talon"], MAT["leg"]))
                out.append((fn, bx, "legs"))
        else:   # 鷺: 長い脚が尾の後ろへ流れる
            pts = [(s * 9, BY - 10, 30), (s * 8, BY - 13, 60), (s * 7, BY - 12, 104), (s * 7, BY - 12, 120)]
            fn, bx = tube_fn(pts, [5, 2.4, 1.8, 1.6], lambda t, X, Y, z: np.where(t < 0.18, MAT["belly"], MAT["leg"]))
            out.append((fn, bx, "legs"))
            for j in range(3):
                a = math.radians(-30 + j * 30)
                p0 = (s * 7, BY - 12, 120)
                p1 = (s * 7 + s * 6 * math.sin(a), BY - 13, 120 + 7 * math.cos(a))
                fn, bx = tube_fn([p0, p1], [1.2, 0.6], lambda t, X, Y, z: np.full(X.shape, MAT["talon"]))
                out.append((fn, bx, "legs"))
    return out


# ---------------------------------------------------------------- 冠・背の遺跡・雲

def crest_parts(P):
    out = []
    hc, hr = P["head_c"], P["head_r"]
    if P["crest"] == "rods":
        # 銅の避雷針の冠: 頭の上に 7 本、外ほど低く後ろへ傾く。途中に銅の玉、先が光る
        for i in range(7):
            f = i / 6
            x = (f - 0.5) * 14.0
            z0 = hc[2] - 2 + abs(f - 0.5) * 8
            y0 = hc[1] + hr[1] - 2.0 - abs(f - 0.5) * 4
            L = 13 + 12 * (1 - abs(f - 0.5) * 2)
            lean = 0.35 + 0.5 * abs(f - 0.5)
            p0 = (x, y0, z0)
            p2 = (x * 1.25, y0 + L, z0 + L * lean)
            fn, bx = tube_fn([p0, p2], [1.5, 0.8],
                             lambda t, X, Y, z: np.where(t > 0.88, MAT["vein"], MAT["copper"]))
            out.append((fn, bx, "crest"))
            pb = (x * 1.15, y0 + L * 0.6, z0 + L * lean * 0.6)
            fn, bx = ell_fn(pb, (1.8, 1.8, 1.8), MAT["copper"])
            out.append((fn, bx, "crest"))
    else:
        # 白い飾り羽: 後頭部から後ろへ長く流れる 3 本 (先が光る)
        for i in range(3):
            x = (i - 1) * 3.0
            p0 = (x, hc[1] + hr[1] * 0.6, hc[2] + hr[2] * 0.4)
            p1 = (x * 1.6, hc[1] + hr[1] + 4, hc[2] + hr[2] + 10)
            p2 = (x * 2.0, hc[1] + hr[1] + 2, hc[2] + hr[2] + 26 - i * 3)
            fn, bx = tube_fn([p0, p1, p2], [2.2, 1.6, 0.8],
                             lambda t, X, Y, z: np.where(t > 0.88, MAT["vein"], MAT["plume"]))
            out.append((fn, bx, "crest"))
    return out


def body_top(P, x, z):
    """胴の上の面の高さ (おおよそ)"""
    zc = (P["body_z0"] + P["body_z1"]) / 2
    hl = (P["body_z1"] - P["body_z0"]) / 2
    t = (z - zc) / hl
    k = max(0.0, (1 - t * t)) ** 0.5 * (1.0 - 0.18 * t)
    rx, ry = P["body_rx"] * k, P["body_ry"] * k
    yc = P["BY"] - 2.0 * t
    return yc + ry * max(0.0, 1 - (x / max(rx, 1e-3)) ** 2) ** 0.5


def ruin_parts(P):
    """背中のダンジョンの入口の目印: 腰の上の羽根の門 (到着から見える)、背の天窓 (広間の上)、風の民の塔"""
    out = []
    # 羽根の門 (腰の上、南を向く楕円の枠)
    zg = P["body_z1"] - 22
    yb = body_top(P, 0, zg)

    def gate(X, Y, z):
        cy = yb + 7.0
        e = ((X / 7.5) ** 2 + ((Y - cy) / 9.0) ** 2) ** 0.5
        frame = (e <= 1.0) & (e >= 0.68) & inb(z, zg - 2, zg + 2) & (Y >= yb - 2)
        face = (e < 0.68) & inb(z, zg - 0.5, zg + 0.5) & (Y >= yb - 1)
        mat = np.where(face, MAT["gate"], np.where((np.abs(X) < 1.0) & (Y > cy + 6), MAT["copper"], MAT["stone"]))
        return frame | face, mat
    out.append((gate, (-10, 10, yb - 3, yb + 18, zg - 3, zg + 3), "ruins"))
    # 門の脇の低い石段 (尾の側から門へ上る)
    for i in range(3):
        zz = zg + 3 + i * 3
        yy = body_top(P, 0, zz)

        def step(X, Y, z, zz=zz, yy=yy, i=i):
            return inb(X, -5, 5) & inb(z, zz, zz + 3) & inb(Y, yy - 1.5, yy + 1.0 + 0.0 * i), MAT["stone"]
        out.append((step, (-6, 6, yy - 3, yy + 3, zz - 1, zz + 4), "ruins"))
    # 天窓 (背の中ほど): 銅の輪と光るガラス
    zs = -6.0
    ys = body_top(P, 0, zs)

    def sky(X, Y, z):
        r = np.sqrt(X ** 2 + (z - zs) ** 2)
        ringm = (r <= 10.5) & (r >= 8.0) & inb(Y, ys - 2.0, ys + 1.5)
        glass = (r < 8.0) & inb(Y, ys - 1.5, ys + 0.5)
        spokes = glass & ((np.abs(X) < 0.6) | (np.abs(z - zs) < 0.6))
        mat = np.where(ringm | spokes, MAT["copper"], MAT["glass"])
        return ringm | glass, mat
    out.append((sky, (-12, 12, ys - 3, ys + 3, zs - 12, zs + 12), "ruins"))
    if P["towers"]:
        # 風の民の塔 2 本 (細い石の柱、上に銅の鐘の円盤が 3 枚)
        for s in (-1, 1):
            tx, tz = s * 15.0, -8.0
            ty = body_top(P, tx, tz)

            def tower(X, Y, z, tx=tx, tz=tz, ty=ty):
                r = np.sqrt((X - tx) ** 2 + (z - tz) ** 2)
                col = (r <= 2.2) & inb(Y, ty - 3, ty + 20)
                base = (r <= 3.6) & inb(Y, ty - 3, ty + 1.5)
                discs = np.zeros(X.shape, bool)
                for k in range(3):
                    discs |= (r <= 4.6 - k * 0.8) & (r > 2.2) & inb(Y, ty + 10 + k * 3.5, ty + 10.8 + k * 3.5)
                cap = (r <= 1.2) & inb(Y, ty + 20, ty + 24)
                mat = np.where(discs, MAT["copper"], np.where(cap, MAT["vein"], MAT["stone"]))
                return col | base | discs | cap, mat
            out.append((tower, (tx - 6, tx + 6, ty - 4, ty + 26, tz - 6, tz + 6), "ruins"))
    return out


def cloud_parts(P):
    """嵐の雲の塊: 翼の付け根・背・尾の付け根を覆う。上は白、下は灰。中で稲妻が光る (C 案)"""
    out = []
    BY = P["BY"]
    blobs = []
    for s in (-1, 1):
        for j in range(5):
            u = 34 + j * 15 + 6 * hz(j, 3)
            zc = -18 + 30 * hz(j, s, 4)
            yc = float(wing_center_y(np.array(u), P)) + 6 + 3 * hz(j, 9)
            blobs.append(((s * u, yc, zc), (11 + 4 * hz(j, 5), 7 + 3 * hz(j, 6), 13 + 5 * hz(j, 7))))
    for j in range(4):
        blobs.append(((10 * (hz(j, 21) - 0.5), BY + 18 + 4 * hz(j, 22), -30 + j * 22), (16, 8, 14)))
    blobs.append(((0, BY + 8, P["body_z1"] + 6), (22, 8, 16)))

    def fn(X, Y, z):
        Xs, Ys = snap1(X, Y)
        m = np.zeros(X.shape, bool)
        for (c, r) in blobs:
            d = ((Xs - c[0]) / r[0]) ** 2 + ((Ys - c[1]) / r[1]) ** 2 + ((z - c[2]) / r[2]) ** 2
            wob = 0.12 * np.sin(0.4 * Xs + 0.3 * z) * np.sin(0.5 * Ys)
            m |= d <= 1.0 + wob
        mat = np.where(Ys < BY + 4 + 0.05 * np.abs(Xs), MAT["cloud_d"], MAT["cloud"])
        return m, mat
    out.append((fn, (-130, 130, BY - 10, BY + 40, -60, 100), "clouds"))
    # 雲の中の稲妻 (雲の表から少しのぞく)
    for j in range(6):
        s = -1 if j % 2 else 1
        u = 40 + j * 12
        y0 = float(wing_center_y(np.array(u), P)) + 13
        z0 = -10 + 6 * j

        pts = [(s * u, y0, z0)]
        for k in range(4):
            pts.append((s * (u + 3 * (1 if k % 2 else -1) + k * 1.5), y0 - 3 - k * 3.2, z0 + 3 * k))
        fn2, bx = tube_fn(pts, [1.0] * len(pts), lambda t, X, Y, z: np.full(X.shape, MAT["bolt"]))
        out.append((fn2, bx, "clouds"))
    return out


# ---------------------------------------------------------------- 組み立て

def build(kid):
    global K
    K = np.zeros((NX, NY, NZ), np.int16)
    P = params(kid)
    fn, bx = body_fn(P)
    fill(fn, bx, PART["body"])
    for fn, bx, part in head_parts(P, kid):
        fill(fn, bx, PART[part])
    for zero, eye, bx in eye_cut(P):
        fill(zero, bx, mode="zero")
        fill(eye, bx, PART["head"])
    for s, wl, hl_ in ((-1, "wing_l", "hand_l"), (1, "wing_r", "hand_r")):
        fn, bx = wing_fn(P, s, u_hi=P["knob_u"])
        fill(fn, bx, PART[wl])
        fn, bx = wing_fn(P, s, u_lo=P["knob_u"])
        fill(fn, bx, PART[hl_])
        fn, bx, kc = knob_fn(P, s)
        fill(fn, bx, PART[hl_], mode="set")
        for fn, bx in primaries(P, s, kc):
            fill(fn, bx, PART[hl_])
    for fn, bx, part in tail_parts(P):
        fill(fn, bx, PART[part])
    for fn, bx, part in leg_parts(P):
        fill(fn, bx, PART[part])
    for fn, bx, part in crest_parts(P):
        fill(fn, bx, PART[part])
    for fn, bx, part in ruin_parts(P):
        fill(fn, bx, PART[part], mode="set")
    if P["clouds"]:
        for fn, bx, part in cloud_parts(P):
            fill(fn, bx, PART[part], mode="empty" if part == "clouds" else "set")
    return P


# ---------------------------------------------------------------- 箱と書き出し (tide_shell_titan と同じ)

def mesh(K):
    Kx = np.moveaxis(K, 0, -1)
    nx = K.shape[0]
    pad = np.zeros((K.shape[1], K.shape[2], nx + 2), np.int16)
    pad[..., 1:-1] = Kx
    cur, prv, nxt = pad[..., 1:-1], pad[..., :-2], pad[..., 2:]
    occ = cur != 0
    st = np.argwhere(occ & (prv != cur))
    en = np.argwhere(occ & (nxt != cur))
    kk = cur[st[:, 0], st[:, 1], st[:, 2]].astype(np.int64)
    y, z, x0, x1 = st[:, 0], st[:, 1], st[:, 2], en[:, 2] + 1
    order = np.lexsort((z, y, x1, x0, kk))
    kk, y, z, x0, x1 = kk[order], y[order], z[order], x0[order], x1[order]
    cont = np.zeros(len(kk), bool)
    cont[1:] = (kk[1:] == kk[:-1]) & (x0[1:] == x0[:-1]) & (x1[1:] == x1[:-1]) & \
        (y[1:] == y[:-1]) & (z[1:] == z[:-1] + 1)
    gs = np.flatnonzero(~cont)
    ge = np.r_[gs[1:], len(kk)] - 1
    kk, x0, x1, y, z0, z1 = kk[gs], x0[gs], x1[gs], y[gs], z[gs], z[ge] + 1
    order = np.lexsort((y, z1, z0, x1, x0, kk))
    kk, x0, x1, y, z0, z1 = kk[order], x0[order], x1[order], y[order], z0[order], z1[order]
    cont = np.zeros(len(kk), bool)
    cont[1:] = (kk[1:] == kk[:-1]) & (x0[1:] == x0[:-1]) & (x1[1:] == x1[:-1]) & \
        (z0[1:] == z0[:-1]) & (z1[1:] == z1[:-1]) & (y[1:] == y[:-1] + 1)
    gs = np.flatnonzero(~cont)
    ge = np.r_[gs[1:], len(kk)] - 1
    return np.stack([kk[gs], x0[gs], x1[gs], y[gs], y[ge] + 1, z0[gs], z1[gs]], 1)


def check_grid(boxes, K):
    cnt = np.zeros(K.shape, np.uint8)
    for (_, x0, x1, y0, y1, z0, z1) in boxes:
        cnt[x0:x1, y0:y1, z0:z1] += 1
    over = int((cnt > 1).sum())
    vol = int(sum((x1 - x0) * (y1 - y0) * (z1 - z0) for (_, x0, x1, y0, y1, z0, z1) in boxes))
    filled = int((K != 0).sum())
    return over, vol == filled, filled


def exposed(x0, x1, y0, y1, z0, z1):
    def free(sl):
        return not np.all(K[sl] != 0)
    return {
        "west": free((slice(max(x0 - 1, 0), x0), slice(y0, y1), slice(z0, z1))) or x0 == 0,
        "east": free((slice(x1, x1 + 1), slice(y0, y1), slice(z0, z1))) or x1 == NX,
        "down": free((slice(x0, x1), slice(max(y0 - 1, 0), y0), slice(z0, z1))) or y0 == 0,
        "up": free((slice(x0, x1), slice(y1, y1 + 1), slice(z0, z1))) or y1 == NY,
        "south": free((slice(x0, x1), slice(y0, y1), slice(z1, z1 + 1))) or z1 == NZ,
        "north": free((slice(x0, x1), slice(y0, y1), slice(max(z0 - 1, 0), z0))) or z0 == 0,
    }


def face_corners(o, s):
    x0, y0, z0 = o
    sx, sy, sz = s
    x1, y1, z1 = x0 + sx, y0 + sy, z0 + sz
    return {
        "north": ((x1, y0, z0), (-sx, 0, 0), (0, sy, 0)),
        "south": ((x0, y0, z1), (sx, 0, 0), (0, sy, 0)),
        "east": ((x1, y0, z1), (0, 0, -sz), (0, sy, 0)),
        "west": ((x0, y0, z0), (0, 0, sz), (0, sy, 0)),
        "up": ((x0, y1, z1), (sx, 0, 0), (0, 0, -sz)),
        "down": ((x0, y0, z0), (sx, 0, 0), (0, 0, sz)),
    }


def world_grid(o, s, fname, w, h):
    co, eu, ev = face_corners(o, s)[fname]
    co, eu, ev = (np.array(v, float) for v in (co, eu, ev))
    cols = (np.arange(w) + 0.5) / w
    rows = 1.0 - (np.arange(h) + 0.5) / h
    return co + cols[None, :, None] * eu + rows[:, None, None] * ev


def pick(tones, idx):
    return np.array(tones, float)[idx]


def mat_base(mat, A2, B2, P3, fname):
    """材料ごとの色 (h, w, 3) と光る画素 (h, w)。A2, B2 は面の上のセルの番地、P3 は世界の位置"""
    h, w = A2.shape
    n = nzv(A2, B2)
    gm = np.zeros((h, w), bool)
    name = MAT_NAME[mat]
    if name in TONES:
        idx = (nzv(A2 // 2, B2 // 2) * len(TONES[name])).astype(int) % len(TONES[name])
        base = pick(TONES[name], idx)
    else:
        base = np.zeros((h, w, 3), float)
    X, Y, Z = P3[..., 0], P3[..., 1], P3[..., 2]
    if name in ("back", "cov", "tailf"):
        # 鱗のように重なる羽: 4 単位ごとの行、行ごとに半分ずらす。羽の先 (行の後ろの端) が明るい
        row = np.floor(Z / 4.0)
        col = np.floor((X + 2.0 * np.mod(row, 2)) / 4.0)
        fz = np.mod(Z, 4.0) / 4.0
        fx = np.abs(np.mod(X + 2.0 * np.mod(row, 2), 4.0) / 4.0 - 0.5)
        scallop = fz > 0.62 + 0.3 * fx
        base[scallop] *= 1.22
        base[(fz < 0.12)] *= 0.82
    elif name == "sec":
        base[np.mod(np.floor(X), 10) < 1] *= 0.7                        # 羽と羽の境
        base[np.mod(np.floor(Z * 2), 3) == 0] *= 1.08
    elif name == "prim":
        base[np.mod(np.floor((X + Z) * 2), 4) == 0] *= 1.15
    elif name == "belly":
        bar = np.mod(np.floor(Z / 3.0) + np.floor(X / 6.0), 3) == 0     # 腹の細かい横縞
        base[bar] *= 0.84
    elif name == "copper":
        base[n > 0.9] = (236, 172, 120)
        base[n < 0.06] = (120, 70, 46)
    elif name == "stone":
        tw, th = 8, 4
        rowb = np.floor_divide(B2, th)
        off = (rowb % 2) * (tw // 2)
        mm = ((A2 + off) % tw == 0) | (B2 % th == 0)
        base[mm] = MORTAR
    elif name == "vein":
        base[:] = (255, 244, 196)
        base[n > 0.6] = (255, 226, 130)
        gm[:] = True
    elif name == "eye":
        base[:] = (255, 236, 150)
        base[(A2 + B2) % 3 == 0] = (255, 255, 230)
        gm[:] = True
    elif name == "gate":
        base[:] = (150, 206, 255)
        base[B2 % 3 == 0] = (210, 236, 255)
        gm[:] = True
    elif name == "knob_w":
        base[:] = (196, 232, 255)
        gm[:] = True
    elif name == "knob_t":
        base[:] = (255, 214, 110)
        gm[:] = True
    elif name == "bolt":
        base[:] = (250, 246, 214)
        gm[:] = True
    elif name == "glass":
        base[:] = (150, 196, 236)
        base[(A2 + B2) % 4 == 0] = (220, 240, 255)
        gm[:] = (A2 + B2) % 2 == 0
    elif name in ("cloud", "cloud_d"):
        base *= (0.92 + 0.12 * nzv(A2 // 3, B2 // 3))[..., None]
    elif name == "beak":
        base[(A2 + 2 * B2) % 7 == 0] *= 0.9
    return base, gm


def paint_face(tex, glow, u, v, w, h, key, fname, P3):
    mat = key % 32
    if fname in ("up", "down"):
        A, B = P3[..., 0], P3[..., 2]
    elif fname in ("north", "south"):
        A, B = P3[..., 0], P3[..., 1]
    else:
        A, B = P3[..., 2], P3[..., 1]
    A2 = np.floor(A * CELL).astype(np.int64)
    B2 = np.floor(B * CELL).astype(np.int64)
    base, gm = mat_base(mat, A2, B2, P3, fname)
    base = np.clip(base, 0, 255)
    tex[v:v + h, u:u + w, :3] = base.astype(np.uint8)
    tex[v:v + h, u:u + w, 3] = 255
    if gm.any():
        gl = glow[v:v + h, u:u + w]
        gl[gm, :3] = base[gm].astype(np.uint8)
        gl[gm, 3] = 255


def pack(items, S):
    pos, x, y, shelf = {}, 1, 0, 0
    for k, w, h in sorted(items, key=lambda it: (-it[2], -it[1])):
        if x + w + 1 > S:
            x, y, shelf = 0, y + shelf + 1, 0
        if y + h > S:
            return None
        pos[k] = (x, y)
        x += w + 1
        shelf = max(shelf, h)
    return pos


PIVOTS = {"body": (0, 40, 0), "head": (0, 46, -52), "wing_l": (-28, 44, 0), "wing_r": (28, 44, 0),
          "hand_l": (-150, 50, -30), "hand_r": (150, 50, -30), "tail": (0, 42, 56), "legs": (0, 26, 26),
          "crest": (0, 66, -96), "ruins": (0, 60, 0), "clouds": (0, 50, 0)}
PARENTS = {"head": "body", "wing_l": "body", "wing_r": "body", "hand_l": "wing_l", "hand_r": "wing_r",
           "tail": "body", "legs": "body", "crest": "head", "ruins": "body", "clouds": "body"}


def make_geo(boxes):
    cubes = []
    for (k, x0, x1, y0, y1, z0, z1) in boxes:
        k, x0, x1, y0, y1, z0, z1 = (int(v) for v in (k, x0, x1, y0, y1, z0, z1))
        ex = exposed(x0, x1, y0, y1, z0, z1)
        a, b, d = x1 - x0, y1 - y0, z1 - z0
        o = (X0 + x0 / CELL, Y0 + y0 / CELL, Z0 + z0 / CELL)
        s = (a / CELL, b / CELL, d / CELL)
        fp = {"up": (a, d), "down": (a, d), "north": (a, b), "south": (a, b), "east": (d, b), "west": (d, b)}
        cubes.append(dict(key=k, o=o, s=s, exp=ex, fp=fp, part=k // 32, cid=len(cubes)))
    items = []
    for c in cubes:
        for fname, (w, h) in c["fp"].items():
            if c["exp"][fname]:
                items.append(((c["cid"], fname), w, h))
    S = None
    for SZ in (1024, 2048, 4096):
        pos = pack(items, SZ)
        if pos:
            S = SZ
            break
    if S is None:
        raise RuntimeError("texture too big")
    tex = np.zeros((S, S, 4), np.uint8)
    glow = np.zeros((S, S, 4), np.uint8)
    tex[0, 0] = (*TONES["back"][0], 255)
    bones_cubes = {p: [] for p in PART}
    for c in cubes:
        uv = {}
        for fname, (w, h) in c["fp"].items():
            if not c["exp"][fname]:
                continue
            u, v = pos[(c["cid"], fname)]
            uv[fname] = {"uv": [u, v], "uv_size": [w, h]}
            P3 = world_grid(c["o"], c["s"], fname, w, h)
            paint_face(tex, glow, u, v, w, h, c["key"], fname, P3)
        if not uv:
            continue
        bones_cubes[PART_NAME[c["part"]]].append(
            {"origin": [round(q, 3) for q in c["o"]], "size": [round(q, 3) for q in c["s"]], "uv": uv})
    # テクスチャを使った高さまで切り詰める (正方形のままだと 4096 になりやすい)
    used = int(np.nonzero(tex[..., 3].any(1))[0].max()) + 1
    TH = int(math.ceil(used / 16.0) * 16)
    tex, glow = tex[:TH], glow[:TH]
    bones = []
    for name in PART:
        if not bones_cubes[name]:
            continue
        b = {"name": name, "pivot": list(PIVOTS[name])}
        if name in PARENTS and bones_cubes.get(PARENTS[name]) is not None:
            b["parent"] = PARENTS[name]
        b["cubes"] = bones_cubes[name]
        bones.append(b)
    names = {b["name"] for b in bones}
    for b in bones:
        if b.get("parent") not in names:
            b.pop("parent", None)
    geo = {"format_version": "1.12.0", "minecraft:geometry": [{
        "description": {"identifier": "geometry.thunder_wing_titan", "texture_width": S, "texture_height": TH,
                        "visible_bounds_width": 420, "visible_bounds_height": 130,
                        "visible_bounds_offset": [0, 40, 0]},
        "bones": bones}]}
    return geo, tex, glow, cubes


# ---------------------------------------------------------------- 描画

RS = 720
FIT = 520


def frame(im, bg):
    diff = np.abs(np.asarray(im, np.int16) - np.asarray(bg, np.int16)).max(-1) > 10
    ys, xs = np.nonzero(diff)
    if xs.size == 0:
        return im
    x0, x1 = max(xs.min() - 8, 0), min(xs.max() + 9, im.width)
    y0, y1 = max(ys.min() - 8, 0), min(ys.max() + 9, im.height)
    c = ImageEnhance.Brightness(im.crop((x0, y0, x1, y1))).enhance(1.15)
    k = FIT / max(c.size)
    return c.resize((max(1, round(c.width * k)), max(1, round(c.height * k))), Image.LANCZOS)


def storm_sky(W, H):
    """嵐の空の背景 (遠景の確認用): 上は暗い灰青、下は少し明るい"""
    yy = np.linspace(0, 1, H)[:, None, None]
    top = np.array([46, 50, 66.0])
    bot = np.array([96, 100, 116.0])
    return np.broadcast_to(top * (1 - yy) + bot * yy, (H, W, 3)).copy()


def far_view(faces, yaw, pitch, px):
    """遠景: 小さく描いて、嵐の空の上に重ねる (形が鳥と読めるか)"""
    im = render(faces, yaw=yaw, pitch=pitch, size=(px, px), margin=0.06, bloom=True)
    bg = np.asarray(render([], size=(px, px), margin=0.06)).astype(int)
    a = np.asarray(im).astype(int)
    mask = (np.abs(a - bg).max(-1) > 6)[..., None]
    sky = storm_sky(px, px)
    out = np.where(mask, a, sky)
    return Image.fromarray(np.clip(out, 0, 255).astype(np.uint8))


def render_all(faces, close):
    bg = render([], size=(RS, RS), margin=0.02)
    spec = {"top": (faces, 30, 38), "side": (faces, 90, 4), "front": (faces, 180, 10),
            "below": (faces, 20, -52), "back_close": (close, 205, 32)}
    out = {}
    for name, (fs, yaw, pitch) in spec.items():
        out[name] = frame(render(fs, yaw=yaw, pitch=pitch, size=(RS, RS), margin=0.02), bg)
    far = Image.new("RGB", (330, 160), (18, 18, 26))
    far.paste(far_view(faces, 20, -55, 150), (4, 5))      # 地上から見上げた形
    far.paste(far_view(faces, 180, 6, 150), (170, 5))     # 正面 (向かってくる)
    out["far"] = far
    return out


def main():
    stats = {}
    images = {}
    kids = sys.argv[1:] or ["a", "b", "c"]
    for kid in kids:
        P = build(kid)
        boxes = mesh(K)
        over, vol_ok, filled = check_grid(boxes, K)
        kept, buried = [], 0
        for bx in boxes:
            k, x0, x1, y0, y1, z0, z1 = (int(v) for v in bx)
            if any(exposed(x0, x1, y0, y1, z0, z1).values()):
                kept.append(bx)
            else:
                buried += 1
        geo, tex, glow, cubes = make_geo(kept)
        d = OUT / kid
        d.mkdir(parents=True, exist_ok=True)
        (d / "geo.json").write_text(json.dumps(geo, separators=(",", ":")), encoding="utf-8")
        Image.fromarray(tex, "RGBA").save(d / "texture.png", optimize=True)
        Image.fromarray(glow, "RGBA").save(d / "glowmask.png", optimize=True)
        glow_px = int((glow[..., 3] > 0).sum())
        assert glow_px > 0, "glowmask is empty"
        faces = load_geo(geo, tex, glowmask=glow)
        zs = -6.0
        close = [f for f in faces if f.origin[1] >= P["BY"] + 5 and abs(f.origin[0]) <= 34
                 and -40 <= f.origin[2] <= P["body_z1"] + 30]
        imgs = render_all(faces, close)
        for name, im in imgs.items():
            im.save(d / f"{name}.png")
        images[kid] = imgs
        parts = {}
        for c in cubes:
            parts[PART_NAME[c["part"]]] = parts.get(PART_NAME[c["part"]], 0) + 1
        xs = [c["o"][0] for c in cubes] + [c["o"][0] + c["s"][0] for c in cubes]
        zs_ = [c["o"][2] for c in cubes] + [c["o"][2] + c["s"][2] for c in cubes]
        ys = [c["o"][1] for c in cubes] + [c["o"][1] + c["s"][1] for c in cubes]
        stats[kid] = dict(boxes=len(kept), buried=buried, over=over, vol_ok=vol_ok, tex=list(tex.shape[:2]),
                          glow_px=glow_px, x=(min(xs), max(xs)), y=(min(ys), max(ys)), z=(min(zs_), max(zs_)),
                          span_blocks_game=round((max(xs) - min(xs)) / 2, 1),
                          length_blocks_game=round((max(zs_) - min(zs_)) / 2, 1), parts=parts)
        print(kid, json.dumps(stats[kid], ensure_ascii=False), flush=True)
    (OUT / f"stats_{''.join(kids)}.json").write_text(json.dumps(stats, ensure_ascii=False, indent=1), encoding="utf-8")
    if len(kids) < 3:
        return
    rows = ["3/4 上から", "側面", "正面", "真下から (地上から見上げる)", "背の門・天窓の近く", "遠景 150px (見上げ / 正面)"]
    keys = ("top", "side", "front", "below", "back_close", "far")
    cols = [("A 嵐の大鷲", [images["a"][k] for k in keys]),
            ("B 雷の鷺", [images["b"][k] for k in keys]),
            ("C 雲纏い", [images["c"][k] for k in keys])]
    sht = sheet("雷翼の巨神 (Thunder Wing titan) ラフ 3案", cols,
                "行: " + " / ".join(rows) + "。左の手首の瘤は白青 (風)、右は金 (雷)。")
    SHEET.parent.mkdir(parents=True, exist_ok=True)
    sht.save(SHEET)


if __name__ == "__main__":
    main()
