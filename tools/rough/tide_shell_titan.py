"""潮殻の巨神 (Tide Shell titan) 第1回ラフ: 3案 A/B/C。海亀の体は3案で共通、甲羅の上だけを変える。

使い方 (worktree の直下で): python tools/rough/tide_shell_titan.py
出力: docs/review/tide_shell_roughs/titan/<a|b|c>/ (geo.json, texture.png, glowmask.png,
      side/top/front/back_close/far.png) と、比較シート docs/review/tide_shell_roughs/titan.png

作り方 (stone_whale_v2 と同じ):
- 形は 0.5 単位の格子 (1 セル = 0.5 単位 = 1 画素 = 1/32 ブロック) に彫る。格子の 1 セルには 1 つの材料しか入らない。
  同じ材料どうしを貪欲に箱にする (greedy meshing)。箱どうしは重ならない (check_grid で重なり 0 を確かめる)。
- 6 面のうち、隣が空いている面だけを uv に書く。隠れた面は書かない。すべての面が隠れた箱は捨てる。
- テクスチャは 2 px / 単位 (1 セル = 1 画素)。模様はワールド座標で決める。水際 (y=30) より下は藻で暗くし、藤壺の粒を散らす。
- 乱数なし (hz / nzv のハッシュで決める)。
- 座標: 1 単位 = 1/16 ブロック。y 上。頭は -Z、尾は +Z。左 = -X (stone whale と同じ)。
  甲羅の長さは z -104〜96 (200 単位)、全長は頭の先 z -158 から尾の先 z 124 まで (約 282 単位)。
"""
import json
import math
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageEnhance

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "tools/rough"))
from voxel import load_geo, render, sheet  # noqa: E402  (voxel.py は共有のため変更していない)

OUT = ROOT / "docs/review/tide_shell_roughs/titan"
SHEET = ROOT / "docs/review/tide_shell_roughs/titan.png"

# ---------------------------------------------------------------- 格子
CELL = 2                              # 1 単位あたりのセル数 (0.5 単位)
X0, X1 = -166.0, 166.0                # 余白つきの範囲 (前のヒレの先まで入るように広げた)
Y0, Y1 = -4.0, 124.0
Z0, Z1 = -174.0, 136.0                # 頭の先 (嘴の鉤) まで入るように広げた
NX = int((X1 - X0) * CELL)
NY = int((Y1 - Y0) * CELL)
NZ = int((Z1 - Z0) * CELL)
K = None                              # build() で作る (x, y, z) の材料の格子 (int16)

# ---------------------------------------------------------------- 寸法 (単位)
WATER = 30.0          # 水面の高さ。甲羅の下から約 40% が水の下
SH_B, SH_H = 6.0, 62.0   # 甲羅の下端と、ドームの高さ (頂上は y=68)
ZC, HL, HW = -4.0, 100.0, 84.0   # 甲羅の中心 z、半長 (z 方向)、半幅 (x 方向)
NLOBE = 24            # 縁の鋸歯の数
ARC = 2 * math.pi * HW / NLOBE   # 縁の 1 区画の長さ (単位、およそ)

PART = {"body": 1, "ruins": 2, "head": 3, "fin_fl": 4, "fin_fr": 5, "fin_rl": 6, "fin_rr": 7,
        "tail": 8, "coral": 9, "kelp": 10}
PART_NAME = {v: k for k, v in PART.items()}
MAT = {"shell": 1, "scute": 2, "stone": 3, "ruin": 4, "cpink": 5, "corange": 6, "cpurple": 7,
       "cyellow": 8, "kelp": 9, "skin": 10, "belly": 11, "dark": 12, "eye": 13, "cyan": 14,
       "pool": 15, "gate": 16, "flip": 17, "ray": 18, "barnacle": 19}
MAT_NAME = {v: k for k, v in MAT.items()}
CORAL_MATS = [MAT["cpink"], MAT["corange"], MAT["cpurple"], MAT["cyellow"]]

TONES = {
    "shell": [(74, 118, 112), (62, 104, 100), (86, 130, 120)],
    "scute": [(104, 150, 140), (92, 136, 128), (116, 162, 152)],
    "stone": [(218, 210, 190), (200, 192, 170), (184, 176, 156)],
    "ruin": [(132, 128, 126), (116, 112, 110), (100, 98, 100)],
    "kelp": [(60, 120, 58), (48, 100, 50), (72, 138, 66)],
    "skin": [(104, 128, 112), (90, 114, 100), (116, 140, 124)],
    "flip": [(84, 116, 110), (72, 102, 98)],
    "belly": [(210, 202, 170), (188, 180, 150)],
    "cpink": [(240, 126, 146), (222, 104, 128), (250, 150, 166)],
    "corange": [(244, 150, 92), (226, 122, 72), (252, 176, 118)],
    "cpurple": [(168, 116, 196), (140, 94, 170), (186, 140, 214)],
    "cyellow": [(248, 210, 100), (228, 186, 76), (252, 226, 140)],
}
ALGAE = np.array([40, 80, 64], float)
WET_SHEEN = np.array([150, 196, 180], float)
MORTAR = (126, 118, 102)


# ---------------------------------------------------------------- ハッシュ (乱数の代わり)

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


# ---------------------------------------------------------------- 格子の基本

def ci(x):
    return int(round((x - X0) * CELL))


def cj(y):
    return int(round((y - Y0) * CELL))


def ck(z):
    return int(round((z - Z0) * CELL))


def inb(v, a, b):
    return (v >= a) & (v <= b)


def fill(fn, box, part=0, mode="empty"):
    """box (単位) の中を、fn(X, Y, z) で彫る。X, Y は (nx, ny) の配列、z は 1 つの値 (z の 1 セルごと)。
    fn は (mask, mat) を返す。mode: empty = 空いている所だけ / set = 上書き / zero = 削る / replace = 既にある所だけ替える"""
    x0, x1, y0, y1, z0, z1 = box
    i0, i1 = max(ci(x0), 0), min(ci(x1), NX)
    j0, j1 = max(cj(y0), 0), min(cj(y1), NY)
    k0, k1 = max(ck(z0), 0), min(ck(z1), NZ)
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
        else:  # replace
            w = m & (sl != 0)
        sl[w] = kv[w]


def solid_box(box, part, mat, mode="empty"):
    """箱 (単位) をそのまま材料で埋める (ブロック)。"""
    fill(lambda X, Y, z: (np.ones(X.shape, bool), mat), box, part, mode)


# ---------------------------------------------------------------- 甲羅 (ドーム・縁・鱗板)

def plan(X, z):
    """甲羅の平面の位置。r は縁を 1 とする正規化した距離 (超楕円)、th は角度。"""
    rx = X / HW
    rz = (z - ZC) / HL
    n = 2.4
    r = (np.abs(rx) ** n + abs(rz) ** n) ** (1.0 / n)
    th = np.arctan2(rz, rx)
    return r, th


def lobe_frac(th):
    return np.mod(np.mod(th, 2 * math.pi) / (2 * math.pi) * NLOBE, 1.0)


def rim_out(th):
    """縁の鋸歯: 三角の歯が外へ 10% 出る (区画の境目は 0、中央は 1)"""
    return 1.0 + 0.10 * (1.0 - np.abs(2 * lobe_frac(th) - 1))


def dome(r):
    return SH_B + SH_H * (1.0 - np.minimum(r, 1.0) ** 5) ** 0.42


def dome_q(r):
    """ドームの高さを段にそろえる: 中央 (r < 0.85) は 2 単位、縁の近くは 1 単位 (箱を減らす)"""
    d = dome(r)
    step = np.where(r < 0.85, 2.0, 1.0)
    return np.round(d / step) * step


def plate_e(X, z, r, th, groove_e):
    """鱗板の高さの足し (単位)。板の中は +1、板の縁は +0.5 (面取り)、溝は groove_e。
    縁 (r > 0.8) は縁の区画、中央は背骨の列 (|x|<8) と肋の列 (8 + 22k) で、行は 18 単位ごと (千鳥)。"""
    frac = lobe_frac(th)
    dl = np.minimum(frac, 1 - frac) * ARC
    e_m = np.where(dl <= 0.5, groove_e, np.where(dl <= 1.5, 0.5, 1.0))
    # 中央: 8 単位の板を、列ごとに 4 単位ずらす (六角に近い千鳥)。板の縁の 1 単位が溝。板は 1 単位の段
    cxc, czc = cell_center(X, z)
    d = np.minimum(4.0 - np.abs(X - cxc), 4.0 - np.abs(z - czc))
    e_c = np.where(d < 1.0, groove_e, 1.0)
    return np.where(r > 0.8, e_m, e_c)


def cell_center(X, z):
    """中央の板 (8 単位、列ごとに 4 単位ずらす) の中心"""
    col = np.floor(X / 8.0)
    stag = 4.0 * np.mod(col, 2.0)
    row = np.floor((z - ZC + stag) / 8.0)
    return (col + 0.5) * 8.0, ZC + (row + 0.5) * 8.0 - stag


def surf(X, z, ge):
    """甲羅の表面の高さ (単位)。中央は板ごとに高さを平らにそろえ (板の中で段を作らない、箱が減る)、
    縁 (r > 0.8) は格子の点ごとに決める。"""
    Xs, zs = snap1(X, z)
    r, th = plan(Xs, zs)
    e = plate_e(Xs, zs, r, th, ge)
    cx, cz = cell_center(Xs, zs)
    rc, _ = plan(cx, cz)
    xq, zq = np.floor(Xs / 4.0) * 4.0 + 2.0, np.floor(zs / 4.0) * 4.0 + 2.0   # 縁の急な斜面は 4 単位ごと
    rq, _ = plan(xq, zq)
    rb = np.where(r > 0.8, rq, rc)
    return np.where(r <= 1.0, dome_q(rb) + e, SH_B + 6.0)


def top_at(X, z, ge):
    return surf(X, z, ge)


def snap1(X, z):
    """1 単位の区画に寄せる (体の大きな面は、区画どうしで箱がつながるように)"""
    return np.floor(X) + 0.5, np.floor(z) + 0.5


def shell_fn(ge, plate_mat):
    def fn(X, Y, z):
        Xs, zs = snap1(X, z)
        r, th = plan(Xs, zs)
        e = plate_e(Xs, zs, r, th, ge)
        top = surf(Xs, zs, ge)
        inside = (Y >= SH_B) & (Y <= top) & (r <= rim_out(th))
        plate = (r <= 1.0) & (e > 0)   # 板の列ごと (縦につながるので箱が減る)
        return inside, np.where(plate, plate_mat, MAT["shell"])
    return fn


def ring_fn(rhos, ge, gap_seed):
    """C: 同心円の石の壁 (鱗板の上に立つ)。ところどころ切れ目 (水路)"""
    def fn(X, Y, z):
        Xs, zs = snap1(X, z)
        r, th = plan(Xs, zs)
        xq, zq = np.floor(Xs / 4.0) * 4.0 + 2.0, np.floor(zs / 4.0) * 4.0 + 2.0   # 壁の高さは 4 単位ごと
        top = surf(xq, zq, ge)
        m = np.zeros(X.shape, bool)
        for k, rho in enumerate(rhos):
            ring = np.abs(r - rho) < 0.024
            gap = (np.floor(np.mod(th, 2 * math.pi) / (2 * math.pi) * NLOBE) + 3 * k + gap_seed) % 5 == 0
            m |= ring & ~gap
        m &= (Y > top - 0.5) & (Y <= top + 3.0)
        return m, MAT["stone"]
    return fn


# ---------------------------------------------------------------- 形の部品

def seg_dist(X, Y, z, P, Q):
    P = np.array(P, float)
    Q = np.array(Q, float)
    d = Q - P
    L2 = float(d @ d)
    px, py, pz = X - P[0], Y - P[1], z - P[2]
    t = np.clip((px * d[0] + py * d[1] + pz * d[2]) / L2, 0, 1)
    dx, dy, dz = px - t * d[0], py - t * d[1], pz - t * d[2]
    return np.sqrt(dx * dx + dy * dy + dz * dz)


def clump_fn(kind, c, s, seed, mat):
    """サンゴのかたまり。c は根元 (単位)、s は大きさ (1 が標準)。kind: branch / brain / table"""
    cx, cy, cz = c
    if kind == "branch":
        sticks = []
        for i in range(4):
            ang = 2 * math.pi * hz(seed, i, 1)
            el = 0.35 + 0.6 * hz(seed, i, 2)
            dv = np.array([math.cos(ang) * math.cos(el), math.sin(el), math.sin(ang) * math.cos(el)])
            L = s * (2.5 + 3.5 * hz(seed, i, 3))
            sticks.append(((cx, cy, cz), (cx + dv[0] * L, cy + dv[1] * L, cz + dv[2] * L)))
        rad = 0.75 * s

        def fn(X, Y, z):
            m = np.zeros(X.shape, bool)
            for P, Q in sticks:
                m |= seg_dist(X, Y, z, P, Q) <= rad
            return m, mat
        ext = 7.0 * s + 2.0
        return fn, (cx - ext, cx + ext, cy - 1, cy + ext + 2, cz - ext, cz + ext)
    if kind == "brain":
        R = 3.0 * s

        def fn(X, Y, z):
            d = np.sqrt((X - cx) ** 2 + (Y - cy - R) ** 2 + (z - cz) ** 2)
            wr = np.sin(2.6 * (X - cx) + 1.4 * np.sin(1.9 * (z - cz))) * np.sin(2.6 * (Y - cy - R))
            return (d <= R) & (wr < 0.8), mat
        return fn, (cx - R - 1, cx + R + 1, cy, cy + 2 * R + 1, cz - R - 1, cz + R + 1)
    # table: 平たい円盤と短い柄
    R = 3.6 * s

    def fn(X, Y, z):
        rh = np.sqrt((X - cx) ** 2 + (z - cz) ** 2)
        disk = (rh <= R) & (np.abs(Y - (cy + 4.0 * s)) <= 0.9)
        stem = (rh <= 1.0) & (Y >= cy) & (Y <= cy + 4.0 * s)
        return disk | stem, mat
    return fn, (cx - R - 1, cx + R + 1, cy, cy + 6 * s, cz - R - 1, cz + R + 1)


def coral_at(cx, cz, kind, s, seed, mat_pick, ge, lift=0.0):
    base = float(top_at(np.array(cx), cz, ge)) + lift
    fn, bx = clump_fn(kind, (cx, base, cz), s, seed, mat_pick)

    def snapped(X, Y, z):  # サンゴは 1 単位の格子 (箱を減らす)
        Xs, zs = snap1(X, z)
        return fn(Xs, np.floor(Y) + 0.5, zs)
    fill(snapped, bx, PART["coral"])


def kelp_strand(theta, height, seed):
    kx = HW * 1.06 * math.cos(theta)
    kz = ZC + HL * 1.06 * math.sin(theta)
    leaves = [(2.0 + 4.0 * hz(seed, j, 5), 4.0 + j * 5.0 + 3.0 * hz(seed, j, 6), 1 if j % 2 else -1)
              for j in range(max(1, int(height // 6)))]
    lf = [(ly, yy, sd) for (ly, yy, sd) in leaves if yy < height]

    def fn(X, Y, z):
        m = ((X - kx) ** 2 + (z - kz) ** 2 <= 1.0) & (Y >= 0.0) & (Y <= height)
        for _, yy, sd in lf:
            d = ((X - kx - 2.6 * sd) / 2.6) ** 2 + ((Y - yy) / 1.3) ** 2 + ((z - kz) / 1.0) ** 2
            m |= d <= 1.0
        return m, MAT["kelp"]
    return fn, (kx - 4.5, kx + 4.5, -1.0, height + 2.0, kz - 4.5, kz + 4.5)


def fin_fn(R, C, T, wmax, hmax, lead, mat_skin, mat_edge):
    """ヒレ (海亀の櫂): 付け根 R から、外へ膨らんで後ろへ曲がる 2 次曲線 (制御点 C) の背骨に沿って、
    楕円体を並べた板。付け根は厚く (hmax)、先へ行くほど薄く、先は丸い。
    前縁 (lead の側) に盛り上がった縁 (ridge) を付け、その画素は mat_edge (暗い線) にする。"""
    R, C, T = (np.array(v, float) for v in (R, C, T))
    ts = np.linspace(0.0, 1.0, 40)
    P = ((1 - ts) ** 2)[:, None] * R + (2 * (1 - ts) * ts)[:, None] * C + (ts ** 2)[:, None] * T
    D = 2 * (1 - ts)[:, None] * (C - R) + 2 * ts[:, None] * (T - C)
    prof = np.sin(np.pi * (0.06 + 0.86 * ts))         # 付け根は細く、4 割のところで最も広く、先は丸い
    W = 2.5 + (wmax - 2.5) * np.clip(prof, 0, 1) ** 0.6
    Hh = 1.2 + (hmax - 1.2) * (1 - ts) ** 1.2          # 厚みは付け根で hmax、先で 1.2
    lead = np.array(lead, float)
    step = float(np.linalg.norm(P[1] - P[0]))
    blobs = []
    for i in range(len(ts)):
        dh = D[i].copy()
        dh[1] = 0.0
        dh /= np.linalg.norm(dh)
        ch = np.array([-dh[2], 0.0, dh[0]])
        if ch @ lead < 0:
            ch = -ch
        blobs.append((P[i], dh, ch, max(1.6, 0.8 * step), W[i], Hh[i]))
    lo = P.min(0) - wmax - 3.0
    hi = P.max(0) + wmax + 3.0
    lo[1] = P[:, 1].min() - hmax - 2.0
    hi[1] = P[:, 1].max() + hmax + 2.0

    def fn(X, Y, z):
        Xs, zs = snap1(X, z)
        m = np.zeros(X.shape, bool)
        edge = np.zeros(X.shape, bool)
        for p, dh, ch, a, w, h in blobs:
            dx, dz, dy = Xs - p[0], zs - p[2], np.floor(Y) + 0.5 - p[1]   # 厚みは 1 単位の段
            pd = dx * dh[0] + dz * dh[2]
            pc = dx * ch[0] + dz * ch[2]
            m |= (pd / a) ** 2 + (pc / w) ** 2 + (dy / h) ** 2 <= 1.0
            ridge = (pd / a) ** 2 + ((pc - 0.8 * w) / (0.25 * w)) ** 2 + (dy / (1.6 * h)) ** 2 <= 1.0
            m |= ridge
            edge |= ridge
        m &= Y >= 0.0                                      # 地面 (y=0) より下には出さない
        return m, np.where(edge, mat_edge, mat_skin)
    return fn, (lo[0], hi[0], lo[1], hi[1], lo[2], hi[2])


# 頭 (首と嘴): z 方向の断面の半径の折れ線
HZ = np.array([-168.0, -158.0, -146.0, -132.0, -120.0, -108.0, -96.0])
HRX = np.array([3.5, 14.0, 20.0, 21.0, 17.5, 15.5, 14.5])     # 頭は前の版の約 1.4 倍
HRY = np.array([3.5, 13.0, 18.0, 19.5, 16.5, 14.5, 13.5])
HYC = np.array([16.0, 18.0, 20.5, 21.5, 21.5, 21.0, 20.0])


def head_fn(X, Y, z):
    """頭: 大きな頭蓋と首。嘴は下あごを抜き、先に下向きの鉤。目の上に眉の隆起。
    首は 4 単位ごとに皺 (半径 +1 の輪)。頭の上は鱗板 (scute)、嘴は白い角質。"""
    Xs, zs = snap1(X, z)
    Y = np.floor(Y) + 0.5                                                   # 頭の高さも 1 単位の段
    rx = float(np.interp(zs, HZ, HRX))
    ry = float(np.interp(zs, HZ, HRY))
    yc = float(np.interp(zs, HZ, HYC))
    fold = 1.0 if (-126.0 < zs < -100.0 and np.mod(zs, 4.0) < 1.0) else 0.0
    rx += fold
    ry += 0.5 * fold
    m = ((Xs / rx) ** 2 + ((Y - yc) / ry) ** 2) <= 1.0
    m &= ~((zs < -150.0) & (Y < yc - 3.0))                                  # 嘴の先の下あごを抜く
    hook = (zs < -160.0) & (np.abs(Xs) < 4.0) & (Y > yc - 9.0) & (Y < yc - 3.0)  # 下向きの鉤
    brow = (np.abs(Xs) > rx * 0.7) & (np.abs(Xs) <= rx + 1.0) & (Y > yc + 3.0) & (Y <= yc + 5.0) & \
        (zs > -136.0) & (zs < -116.0)                                        # 目の上の眉の隆起
    m = m | hook | brow
    mat = np.full(X.shape, MAT["skin"], np.int64)
    mat[np.broadcast_to((Y > yc + 0.45 * ry) & (zs > -150.0), X.shape)] = MAT["scute"]  # 頭の上の鱗板
    mat[brow] = MAT["scute"]
    mat[np.broadcast_to(zs < -146.0, X.shape)] = MAT["belly"]                # 嘴は白っぽい角質
    mouth = (np.abs(Y - (yc - 1.2)) < 0.4) & (zs < -132.0) & (np.abs(Xs) > 0.4 * rx)
    mat[mouth] = MAT["dark"]
    eye = (np.abs(Xs) > rx * 0.55) & (Y > yc + 0.5) & (Y < yc + 3.0) & (zs > -130.0) & (zs < -120.0)
    mat[eye] = MAT["eye"]
    return m, mat


def tail_fn(X, Y, z):
    X, z = snap1(X, z)
    t = np.clip((z - 92.0) / 32.0, 0, 1)
    rx = 6.5 * (1 - t) + 1.6
    ry = rx * 0.9
    yc = 16.0
    m = (((X / rx) ** 2 + ((Y - yc) / ry) ** 2) <= 1.0) & (z >= 90) & (z <= 124)
    return m, MAT["skin"]


def arch_x(zc, R, cy, leg_base, mat, half_z=2.0):
    """x 方向に渡す半円のアーチ (面は z 方向に 4 単位の厚み)。脚は leg_base から cy まで"""
    def fn(X, Y, z):
        if abs(z - zc) > half_z:
            return False, mat
        d = np.sqrt(X ** 2 + (Y - cy) ** 2)
        ring = (d <= R) & (d >= R - 3) & (Y >= cy)
        legs = (np.abs(np.abs(X) - (R - 1.5)) <= 1.5) & (Y >= leg_base) & (Y <= cy)
        return ring | legs, mat
    return fn, (-R - 3, R + 3, leg_base - 1, cy + R + 1, zc - half_z, zc + half_z)


def arch_z(xc, R, cy, z_mid, leg_base, mat):
    """z 方向に渡すアーチ (x の厚み 4 単位)"""
    def fn(X, Y, z):
        d = np.sqrt((z - z_mid) ** 2 + (Y - cy) ** 2)
        ring = (d <= R) & (d >= R - 3) & (Y >= cy)
        legs = (np.abs(np.abs(z - z_mid) - (R - 1.5)) <= 1.5) & (Y >= leg_base) & (Y <= cy)
        return (ring | legs) & (np.abs(X - xc) <= 2.0), mat
    return fn, (xc - 3, xc + 3, leg_base - 1, cy + R + 1, z_mid - R - 3, z_mid + R + 3)


# ---------------------------------------------------------------- 3案の作り方

def build(kid):
    global K
    K = np.zeros((NX, NY, NZ), np.int16)
    ge = {"a": 0.0, "b": 0.0, "c": -0.5}[kid]          # 鱗板の溝の深さ (C は溝を深く彫る)
    plate_mat = MAT["stone"] if kid == "c" else MAT["scute"]
    topf = lambda X, z: top_at(X, z, ge)  # noqa: E731

    # 1. 甲羅 (共通)
    fill(shell_fn(ge, plate_mat), (-96, 96, 4, 80, -106, 100), PART["body"])

    # 2. 潮門 (後ろ左の縁、水面の近く): 開口 (削る) と、石の枠、奥の水色の光
    fill(lambda X, Y, z: (inb(X, -72, -60) & inb(Y, 10, 30) & inb(z, 56, 72), 0),
         (-74, -58, 9, 31, 55, 73), mode="zero")
    fill(lambda X, Y, z: (inb(X, -76, -62) & inb(Y, 10, 38) & (inb(z, 53, 56) | inb(z, 72, 75)), MAT["stone"]),
         (-77, -61, 9, 39, 52, 76), PART["ruins"])
    fill(lambda X, Y, z: (inb(X, -76, -62) & inb(Y, 36, 40) & inb(z, 53, 75), MAT["stone"]),
         (-77, -61, 35, 41, 52, 76), PART["ruins"])
    fill(lambda X, Y, z: (inb(X, -63, -61) & inb(Y, 13, 30) & inb(z, 58, 70), MAT["gate"]),
         (-64, -60, 12, 31, 57, 71), PART["ruins"], mode="set")
    for zz in (54.0, 73.0):  # 門の両脇の、海のガラスのランタン
        solid_box((-76, -72, 40, 43, zz - 1, zz + 1), PART["ruins"], MAT["cyan"])

    # 3. 頭 (首と嘴)、尾
    fill(head_fn, (-24, 24, 2, 50, -176, -92), PART["head"])
    fill(tail_fn, (-4, 8, 4, 30, 88, 126), PART["tail"])

    # 4. ヒレ: 前は長い櫂 (後ろへ曲がる)、後ろは短く丸い。付け根は甲羅の下の縁
    skin, ray = MAT["flip"], MAT["ray"]
    for R, C, T, wmax, hmax, part in (
            ((-76, 9, -40), (-118, 14, -26), (-146, 6, 2), 10.0, 5.0, "fin_fl"),
            ((76, 9, -40), (118, 14, -26), (146, 6, 2), 10.0, 5.0, "fin_fr"),
            ((-62, 9, 50), (-92, 12, 84), (-106, 6, 100), 8.0, 3.5, "fin_rl"),
            ((62, 9, 50), (92, 12, 84), (106, 6, 100), 8.0, 3.5, "fin_rr")):
        fn, bx = fin_fn(R, C, T, wmax, hmax, (0.0, 0.0, -1.0), skin, ray)
        fill(fn, bx, PART[part])

    # 5. 甲羅の上の遺跡 (案ごとに違う)
    T0 = float(topf(np.array(0.0), ZC))
    if kid == "a":
        build_a(topf, T0)
    elif kid == "b":
        build_b(topf, T0, ge)
    else:
        build_c(topf, T0)

    # 6. 縁のサンゴ・海藻 (案ごとに数を変える)
    rim = {"a": 12, "b": 2, "c": 4}[kid]
    for i in range(rim):
        th = 2 * math.pi * (i + 0.5) / rim + 0.13 * hz(i, 40)
        cx = HW * 1.0 * math.cos(th)
        cz = ZC + HL * 1.0 * math.sin(th)
        kind = ["branch", "brain", "table"][i % 3]
        coral_at(cx, cz, kind, 0.9 + 0.5 * hz(i, 41), i + 7, CORAL_MATS[int(hz(i, 42) * 4) % 4], ge, lift=6.0)
    kelp_n = {"a": 8, "b": 10, "c": 5}[kid]
    for i in range(kelp_n):
        th = 2 * math.pi * (i + 0.2) / kelp_n + 0.08
        fn, bx = kelp_strand(th, 12.0 + 6.0 * hz(i, 50), i)
        fill(fn, bx, PART["kelp"])
    return ge


def build_a(topf, T0):
    """A 遺跡の島: 前の段々の広場 (階段・アーチ・低い壁・潮だまり)、中央の観測塔。主な遺跡は 1.5 倍"""
    s = 1.5
    TX, TZ0, TZ1 = 34.0 * s, -65.0, -11.0      # 広場 (テラス) の範囲
    # 前の段々の広場 (テラス): 鱗板の上に石を積み、上を平らにする
    xs = np.linspace(-TX, TX, 15)
    zs = np.linspace(TZ0, TZ1, 9)
    XG, ZG = np.meshgrid(xs, zs)
    L = float(topf(XG, ZG).min()) + 5.0
    fill(lambda X, Y, z: (inb(X, -TX, TX) & inb(z, TZ0, TZ1) & (Y <= L) & (Y >= 4), MAT["stone"]),
         (-TX - 2, TX + 2, 4, L + 2, TZ0 - 2, TZ1 + 2), PART["ruins"])
    # 広場の縁の低い壁 (3 単位)
    fill(lambda X, Y, z: (inb(X, -TX, TX) & inb(z, TZ0, TZ1) & (Y >= L) & (Y <= L + 3) &
                          ((np.abs(np.abs(X) - TX) <= 1.5) | inb(z, TZ0, TZ0 + 1.5) | inb(z, TZ1 - 1.5, TZ1)),
                          MAT["ruin"]),
         (-TX - 2, TX + 2, L - 1, L + 4, TZ0 - 2, TZ1 + 2), PART["ruins"])
    # 潮だまり (広場の上、深さ 1 単位の水色の水面)
    pool_rect(24, 38, -56, -40, L)
    # 階段 (広場から塔の根元へ、一段 1 単位、2 単位ごと)
    fill(lambda X, Y, z: (inb(X, -7, 7) & inb(z, -24, -6) & (Y >= L - 1) &
                          (Y <= L + np.floor((z + 24.0) / 2.0) + 1.0), MAT["stone"]),
         (-8, 8, L - 2, L + 11, -25, -5), PART["ruins"])
    # 広場を渡すアーチ
    fn, bx = arch_x(-40.0, 18.0, L + 18.0, L, MAT["ruin"])
    fill(fn, bx, PART["ruins"])
    # 背中の後ろのアーチ (甲羅の上に立つ)
    fn, bx = arch_x(40.0, 24.0, 80.0, SH_B, MAT["ruin"])
    fill(fn, bx, PART["ruins"])
    # 甲羅の上の低い壁 (鱗板の列に沿う)
    for xw in (-44.0, 44.0):
        fill(lambda X, Y, z, xw=xw: ((np.abs(np.abs(X) - (-xw)) <= 1.0) & inb(z, -30, 10) &
                                     (Y > topf(X, z) - 1.0) & (Y <= topf(X, z) + 2.5), MAT["ruin"]),
             (-46, -42, 40, 80, -32, 12) if xw < 0 else (42, 46, 40, 80, -32, 12), PART["ruins"])
    tower(topf, T0, h=1.0, sc=1.5)


def build_b(topf, T0, ge):
    """B 珊瑚礁の背: 珊瑚礁が甲羅を覆い、遺跡は半分埋もれる。塔は低く、壁は珊瑚に包まれる"""
    # 半分埋もれた広場の残り (低い壁と石の段)
    fill(lambda X, Y, z: (inb(X, -20, 20) & inb(z, -40, -24) & (Y <= topf(X, z) + 3.0), MAT["ruin"]),
         (-22, 22, 40, 80, -42, -22), PART["ruins"])
    fn, bx = arch_x(20.0, 13.0, 74.0, SH_B, MAT["ruin"])
    fill(fn, bx, PART["ruins"])
    fn, bx = arch_z(-30.0, 11.0, 70.0, 6.0, SH_B, MAT["ruin"])
    fill(fn, bx, PART["ruins"])
    tower(topf, T0, h=0.55)
    # 甲羅の上の潮だまり (珊瑚に囲まれる)
    pool_round(-20.0, 16.0, 4.5, topf)
    pool_round(22.0, -8.0, 3.5, topf)
    # 珊瑚礁: 甲羅の上に多くの群れ (背の後ろ半分が多い)
    n = 0
    # 数は少なく、一つ一つを大きく (小さな粒を散らさない)
    for i in range(7):
        x = -60 + 120 * hz(i, 60)
        z = -80 + 150 * hz(i, 61)
        r, _ = plan(np.array(x), z)
        if float(r) > 0.92 or float(r) < 0.25:
            continue
        kind = ["branch", "brain", "table"][n % 3]
        s = 2.6 + 0.8 * hz(i, 62)
        coral_at(x, z, kind, s, 100 + i, CORAL_MATS[n % 4], ge, lift=0.0)
        n += 1
    # 縁にも大きめの珊瑚の房
    for i in range(3):
        th = 2 * math.pi * (i + 0.5) / 3 + 0.3
        coral_at(HW * math.cos(th), ZC + HL * math.sin(th), "branch", 2.0, 300 + i,
                 CORAL_MATS[(i + 2) % 4], ge, lift=0.0)


def build_c(topf, T0):
    """C 潮門の巨亀: 同心円の石の壁と水路、大きく交わるアーチ、高い観測塔。サンゴは少ない"""
    rings = [0.30, 0.56, 0.80]
    fill(ring_fn(rings, -0.5, 1), (-90, 90, 4, 90, -106, 100), PART["ruins"])
    # 水路: 背骨の両側に、z 方向に走る水路 (削って、底に水色の水)
    for xc in (-18.0, 18.0):
        fill(lambda X, Y, z, xc=xc: ((np.abs(X - xc) <= 1.2) & inb(z, -50, 40) & (Y > topf(X, z) - 2.0) &
                                     (Y <= topf(X, z) + 1.0), 0),
             (xc - 2, xc + 2, 30, 90, -52, 42), mode="zero")
        fill(lambda X, Y, z, xc=xc: ((np.abs(X - xc) <= 1.2) & inb(z, -50, 40) & (Y > topf(X, z) - 3.0) &
                                     (Y <= topf(X, z) - 2.0), MAT["pool"]),
             (xc - 2, xc + 2, 30, 90, -52, 42), PART["ruins"], mode="set")
    # 大きく交わるアーチ: x 方向 (手前) と z 方向 (背骨の上を跨ぐ)
    fn, bx = arch_x(-38.0, 33.0, 62.0, SH_B, MAT["stone"])
    fill(fn, bx, PART["ruins"])
    fn, bx = arch_z(0.0, 39.0, 66.0, -4.0, SH_B, MAT["stone"])
    fill(fn, bx, PART["ruins"])
    tower(topf, T0, h=1.6, sc=1.5)
    # 縁の石の板の上に、鋸歯の先を飾る石の塊
    for i in range(12):
        th = 2 * math.pi * (i + 0.5) / 12
        cx = HW * 1.0 * math.cos(th)
        cz = ZC + HL * math.sin(th)
        fill(lambda X, Y, z, cx=cx, cz=cz: ((np.abs(X - cx) <= 1.5) & (np.abs(z - cz) <= 1.5) &
                                             (Y >= SH_B) & (Y <= SH_B + 4.0), MAT["stone"]),
             (cx - 2, cx + 2, SH_B - 1, SH_B + 5, cz - 2, cz + 2), PART["ruins"])


def pool_rect(x0, x1, z0, z1, L):
    """広場の潮だまり: 上の 1 単位を削り、その下の 1 単位を水色にする"""
    m_zero = lambda X, Y, z: (inb(X, x0, x1) & inb(z, z0, z1) & (Y >= L - 1.0) & (Y <= L + 1.0), 0)  # noqa: E731
    fill(m_zero, (x0 - 1, x1 + 1, L - 2, L + 2, z0 - 1, z1 + 1), mode="zero")
    fill(lambda X, Y, z: (inb(X, x0, x1) & inb(z, z0, z1) & (Y >= L - 2.0) & (Y < L - 1.0), MAT["pool"]),
         (x0 - 1, x1 + 1, L - 3, L, z0 - 1, z1 + 1), PART["ruins"], mode="set")


def pool_round(xc, zc, R, topf):
    """甲羅の上の丸い潮だまり (鱗板の上を削って、底に水色)"""
    fill(lambda X, Y, z: ((np.hypot(X - xc, z - zc) <= R) & (Y > topf(X, z) - 1.0) & (Y <= topf(X, z) + 1.0), 0),
         (xc - R - 2, xc + R + 2, 30, 90, zc - R - 2, zc + R + 2), mode="zero")
    fill(lambda X, Y, z: ((np.hypot(X - xc, z - zc) <= R) & (Y > topf(X, z) - 2.0) & (Y <= topf(X, z) - 1.0),
                          MAT["pool"]),
         (xc - R - 2, xc + R + 2, 30, 90, zc - R - 2, zc + R + 2), PART["ruins"], mode="set")


def tower(topf, T0, h, sc=1.0):
    """甲羅の頂上の観測塔。h が大きいほど高い (B は低く、C は高い)。sc で全体の大きさ (A と C は 1.5)"""
    zc = ZC
    up = (12 * h + 10) * sc
    b, mid = 9 * sc, 6 * sc
    fill(lambda X, Y, z: ((np.abs(X) <= b) & (np.abs(z - zc) <= b) & (np.abs(X) + np.abs(z - zc) <= 15 * sc) &
                          inb(Y, T0 - 6 * sc, T0 + 12 * sc), MAT["stone"]),
         (-b - 1, b + 1, T0 - 7 * sc, T0 + 13 * sc, zc - b - 1, zc + b + 1), PART["ruins"])
    fill(lambda X, Y, z: ((np.abs(X) <= mid) & (np.abs(z - zc) <= mid) & (Y > T0 + 12 * sc) & (Y <= T0 + up),
                          MAT["ruin"]),
         (-mid - 1, mid + 1, T0 + 11 * sc, T0 + up + 1, zc - mid - 1, zc + mid + 1), PART["ruins"])
    # 窓 (暗い穴の縁)
    fill(lambda X, Y, z: ((np.abs(X) >= mid - 0.5) & (np.abs(X) <= mid) & (np.abs(z - zc) <= 1.5 * sc) &
                          inb(Y, T0 + 14 * sc, T0 + 18 * sc), MAT["dark"]),
         (-mid - 1, mid + 1, T0 + 13 * sc, T0 + 19 * sc, zc - 2 * sc, zc + 2 * sc), PART["ruins"], mode="set")
    if h > 0.6:
        for sx in (-5.0 * sc, 5.0 * sc):
            for sz in (-5.0 * sc, 5.0 * sc):
                fill(lambda X, Y, z, sx=sx, sz=sz: ((np.abs(X - sx) <= sc) & (np.abs(z - zc - sz) <= sc) &
                                                    inb(Y, T0 + up, T0 + up + 10 * sc), MAT["ruin"]),
                     (sx - 2 * sc, sx + 2 * sc, T0 + up - 1, T0 + up + 11 * sc, zc + sz - 2 * sc, zc + sz + 2 * sc),
                     PART["ruins"])
    roof_y = T0 + up + (10 * sc if h > 0.6 else 0)
    fill(lambda X, Y, z: ((X ** 2 + (z - zc) ** 2 <= (9 * sc) ** 2) & inb(Y, roof_y, roof_y + 2), MAT["stone"]),
         (-b - 1, b + 1, roof_y - 1, roof_y + 3, zc - b - 1, zc + b + 1), PART["ruins"])
    fill(lambda X, Y, z: ((X ** 2 + (z - zc) ** 2 <= (3 * sc) ** 2) & inb(Y, roof_y + 2, roof_y + 4), MAT["cyan"]),
         (-3 * sc - 1, 3 * sc + 1, roof_y + 1, roof_y + 5, zc - 3 * sc - 1, zc + 3 * sc + 1), PART["ruins"])


# ---------------------------------------------------------------- 面の情報 (greedy meshing)

def mesh(K):
    """格子を箱にする。x 方向の連続 -> z 方向 -> y 方向の順に同じ材料を合わせる。
    返り値: (N, 7) の配列 [key, x0, x1, y0, y1, z0, z1] (セル単位、格子の座標)"""
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
    """箱どうしの重なりを、箱を格子に戻して数える。返り値: (重なったセル数, 箱の体積 == 充填数 か, 充填数)"""
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
        "west": free((slice(x0 - 1, x0), slice(y0, y1), slice(z0, z1))),
        "east": free((slice(x1, x1 + 1), slice(y0, y1), slice(z0, z1))),
        "down": free((slice(x0, x1), slice(y0 - 1, y0), slice(z0, z1))),
        "up": free((slice(x0, x1), slice(y1, y1 + 1), slice(z0, z1))),
        "south": free((slice(x0, x1), slice(y0, y1), slice(z1, z1 + 1))),
        "north": free((slice(x0, x1), slice(y0, y1), slice(z0 - 1, z0))),
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


# ---------------------------------------------------------------- テクスチャ (2 px / 単位)

def pick(tones, idx):
    return np.array(tones, float)[idx]


def mat_base(mat, A2, B2, part):
    """材料ごとの色 (h, w, 3) と光る画素 (h, w)。A2, B2 はセル単位 (1 セル = 1 画素)"""
    h, w = A2.shape
    n = nzv(A2, B2)
    gm = np.zeros((h, w), bool)
    name = MAT_NAME[mat]
    if name in TONES:
        idx = (nzv(A2 // 2, B2 // 2) * len(TONES[name])).astype(int) % len(TONES[name])
        base = pick(TONES[name], idx)
    else:
        base = np.zeros((h, w, 3), float)
    if name == "shell":
        base[(A2 % 16 == 0) | (B2 % 12 == 0)] *= 0.72
    elif name == "scute":
        base[(A2 % 12 == 0) | (B2 % 12 == 0)] = (70, 100, 94)
    elif name in ("stone", "ruin"):
        tw, th = (8, 4) if name == "stone" else (6, 4)
        row = np.floor_divide(B2, th)
        off = (row % 2) * (tw // 2)
        m = ((A2 + off) % tw == 0) | (B2 % th == 0)
        base[m] = MORTAR
        base[(n > 0.92) & ~m] *= 0.75
        if name == "ruin":
            base[(n > 0.86) & ~m] = (98, 122, 88)     # 苔
        if part == PART["ruins"] and name == "stone":
            g = (nzv(A2 // 4, B2 // 4) < 0.08) & np.isin(A2 % 4, [1, 2]) & np.isin(B2 % 4, [1, 2]) & ~m
            base[g] = (96, 232, 255)                  # 刻文の光 (小さな印)
            gm |= g
    elif name in ("cpink", "corange", "cpurple", "cyellow"):
        base[(A2 + B2) % 3 == 0] *= 1.12
    elif name == "flip":
        # ヒレの鱗: 6 画素の板が、列ごとに半分ずれる (溝は暗い)
        base[((A2 + 3 * ((B2 // 3) % 2)) % 6 == 0) | (B2 % 6 == 0)] *= 0.78
    elif name == "kelp":
        base[A2 % 5 == 0] *= 0.8
    elif name == "skin":
        base[(A2 + 2 * B2) % 4 == 0] *= 0.86
    elif name == "dark":
        base[:] = (44, 50, 54)
    elif name == "eye":
        base[:] = (255, 196, 92)
        base[h // 2, w // 2] = (30, 24, 20)
        gm[:] = True
    elif name == "cyan":
        base[:] = (96, 232, 255)
        base[(A2 + B2) % 2 == 0] = (206, 250, 255)
        gm[:] = True
    elif name == "pool":
        base[:] = (36, 150, 152)
        base[(A2 + B2) % 5 == 0] = (84, 190, 186)
    elif name == "gate":
        base[:] = (60, 200, 220)
        base[B2 % 3 == 0] *= 0.7
        gm[:] = True
    elif name == "ray":
        base[:] = (34, 54, 56)
    elif name == "barnacle":
        base[:] = (206, 200, 186)
    return base, gm


WET = {"shell", "scute", "stone", "ruin", "skin", "flip", "belly", "dark"}


def wet_apply(base, mat, A2, B2, Yw):
    """水際より下は藻で暗く、水際には明るい筋。藤壺の白い粒を散らす"""
    name = MAT_NAME[mat]
    if name not in WET:
        return base
    n = nzv(A2, B2)
    t = np.clip((WATER - Yw) / 6.0, 0, 1)
    mix = (t * (0.6 + 0.3 * n))[..., None]
    base = base * (1 - mix) + ALGAE * mix
    band = np.abs(Yw - WATER) < 1.6
    base[band] = base[band] * 0.3 + WET_SHEEN * 0.7
    bar = (Yw < WATER - 0.6) & (Yw > WATER - 14) & (n > 0.86)
    if name not in ("shell", "scute", "stone", "ruin"):
        bar[:] = False                                   # 藤壺は甲羅と遺跡だけ (頭や皮には付けない)
    base[bar] = (222, 216, 200)
    return base


def paint_face(tex, glow, u, v, w, h, key, fname, P):
    part, mat = key // 32, key % 32
    if fname in ("up", "down"):
        A, B = P[..., 0], P[..., 2]
    elif fname in ("north", "south"):
        A, B = P[..., 0], P[..., 1]
    else:
        A, B = P[..., 2], P[..., 1]
    A2 = np.floor(A * CELL).astype(np.int64)
    B2 = np.floor(B * CELL).astype(np.int64)
    Yw = P[..., 1]
    base, gm = mat_base(mat, A2, B2, part)
    base = wet_apply(base, mat, A2, B2, Yw)
    base = np.clip(base, 0, 255)
    tex[v:v + h, u:u + w, :3] = base.astype(np.uint8)
    tex[v:v + h, u:u + w, 3] = 255
    if gm.any():
        gl = glow[v:v + h, u:u + w]
        gl[gm, :3] = base[gm].astype(np.uint8)
        gl[gm, 3] = 255


def pack(items, S):
    pos, x, y, shelf = {}, 1, 0, 0   # (0,0) の 1 画素は、隠れた面の共通の色として空けておく
    for k, w, h in sorted(items, key=lambda it: (-it[2], -it[1])):
        if x + w + 1 > S:
            x, y, shelf = 0, y + shelf + 1, 0
        if y + h > S:
            return None
        pos[k] = (x, y)
        x += w + 1
        shelf = max(shelf, h)
    return pos


# ---------------------------------------------------------------- geo と描画

def make_geo(boxes, idx):
    """箱 (隠れていない箱だけ) を geo にする。隠れた面は uv を書かない。テクスチャの大きさは 512〜4096"""
    cubes = []
    for (k, x0, x1, y0, y1, z0, z1) in boxes:
        k, x0, x1, y0, y1, z0, z1 = (int(v) for v in (k, x0, x1, y0, y1, z0, z1))
        ex = exposed(x0, x1, y0, y1, z0, z1)
        a, b, d = x1 - x0, y1 - y0, z1 - z0
        o = (X0 + x0 / CELL, Y0 + y0 / CELL, Z0 + z0 / CELL)
        s = (a / CELL, b / CELL, d / CELL)
        fp = {"up": (a, d), "down": (a, d), "north": (a, b), "south": (a, b), "east": (d, b), "west": (d, b)}
        cubes.append(dict(key=k, o=o, s=s, cells=(a, b, d), exp=ex, fp=fp, part=k // 32, mat=k % 32, cid=len(cubes)))
    items = []
    for c in cubes:
        for fname, (w, h) in c["fp"].items():
            if c["exp"][fname]:
                items.append(((c["cid"], fname), w, h))
    S = None
    for SZ in (512, 1024, 2048, 4096):
        pos = pack(items, SZ)
        if pos:
            S = SZ
            break
    if S is None:
        raise RuntimeError("texture too big")
    tex = np.zeros((S, S, 4), np.uint8)
    glow = np.zeros((S, S, 4), np.uint8)
    tex[0, 0] = (*TONES["stone"][2], 255)
    bones_cubes = {p: [] for p in PART}
    for c in cubes:
        uv = {}
        for fname, (w, h) in c["fp"].items():
            if not c["exp"][fname]:
                continue                       # 隠れた面は uv を書かない (GeckoLib は描かない)
            u, v = pos[(c["cid"], fname)]
            uv[fname] = {"uv": [u, v], "uv_size": [w, h]}
            P = world_grid(c["o"], c["s"], fname, w, h)
            paint_face(tex, glow, u, v, w, h, c["key"], fname, P)
        if not uv:
            continue
        bones_cubes[PART_NAME[c["part"]]].append(
            {"origin": [round(q, 3) for q in c["o"]], "size": [round(q, 3) for q in c["s"]], "uv": uv})
    bones = []
    pivots = {"body": (0, 30, 0), "ruins": (0, 30, 0), "head": (0, 22, -100), "fin_fl": (-76, 9, -40),
              "fin_fr": (76, 9, -40), "fin_rl": (-62, 9, 50), "fin_rr": (62, 9, 50), "tail": (0, 16, 92),
              "coral": (0, 30, 0), "kelp": (0, 0, 0)}
    for name in PART:
        b = {"name": name, "pivot": list(pivots[name])}
        if name != "body":
            b["parent"] = "body"
        b["cubes"] = bones_cubes[name]
        bones.append(b)
    geo = {"format_version": "1.12.0", "minecraft:geometry": [{
        "description": {"identifier": "geometry.tide_shell_titan", "texture_width": S, "texture_height": S,
                        "visible_bounds_width": 300, "visible_bounds_height": 130,
                        "visible_bounds_offset": [0, 40, 0]},
        "bones": bones}]}
    return geo, tex, glow, cubes


def views(geo, tex, glow, cubes):
    faces = load_geo(geo, tex, glowmask=glow)
    # 遺跡の近景: 塔と広場のあたり (甲羅の上の高さ、頭から離れた中央)
    close = [f for f in faces if f.origin[1] >= 25 and abs(f.origin[0]) <= 60 and -75 <= f.origin[2] <= 10]
    return faces, close


RS = 640      # 描く大きさ (あとで、絵の範囲だけを切り出して縮める)
FIT = 480     # 切り出した絵の長い辺


def frame(im, bg):
    """背景との違いで絵の範囲を求め、その範囲だけを切り出す。少し明るくする。"""
    diff = np.abs(np.asarray(im, np.int16) - np.asarray(bg, np.int16)).max(-1) > 10
    ys, xs = np.nonzero(diff)
    if xs.size == 0:
        return im
    x0, x1 = max(xs.min() - 8, 0), min(xs.max() + 9, im.width)
    y0, y1 = max(ys.min() - 8, 0), min(ys.max() + 9, im.height)
    c = ImageEnhance.Brightness(im.crop((x0, y0, x1, y1))).enhance(1.2)
    k = FIT / max(c.size)
    return c.resize((max(1, round(c.width * k)), max(1, round(c.height * k))), Image.LANCZOS)


def render_all(kid, faces, close):
    bg = render([], size=(RS, RS), margin=0.02)
    spec = {"side": (faces, 90, 0), "top": (faces, 35, 35), "front": (faces, -22, 6),
            "back_close": (close, 25, 30)}
    out = {}
    for name, (fs, yaw, pitch) in spec.items():
        out[name] = frame(render(fs, yaw=yaw, pitch=pitch, size=(RS, RS), margin=0.02), bg)
    far_side = render(faces, yaw=90, pitch=0, size=(140, 140), margin=0.04)
    far_top = render(faces, yaw=35, pitch=35, size=(140, 140), margin=0.04)
    far = Image.new("RGB", (290, 140), (18, 18, 26))
    far.paste(far_side, (0, 0))
    far.paste(far_top, (150, 0))
    out["far"] = far
    return out


def main():
    stats = {}
    images = {}
    kids = sys.argv[1:] or ["a", "b", "c"]
    for kid in kids:
        ge = build(kid)
        boxes = mesh(K)
        over, vol_ok, filled = check_grid(boxes, K)
        # 全部の面が隠れた箱は捨てる (外からは見えない)
        kept = []
        buried = 0
        for bx in boxes:
            k, x0, x1, y0, y1, z0, z1 = (int(v) for v in bx)
            ex = exposed(x0, x1, y0, y1, z0, z1)
            if any(ex.values()):
                kept.append(bx)
            else:
                buried += 1
        geo, tex, glow, cubes = make_geo(kept, kid)
        d = OUT / kid
        d.mkdir(parents=True, exist_ok=True)
        (d / "geo.json").write_text(json.dumps(geo, separators=(",", ":")), encoding="utf-8")
        Image.fromarray(tex, "RGBA").save(d / "texture.png", optimize=True)
        Image.fromarray(glow, "RGBA").save(d / "glowmask.png", optimize=True)
        glow_px = int((glow[..., 3] > 0).sum())
        assert glow_px > 0, "glowmask is empty"
        faces, close = views(geo, tex, glow, cubes)
        imgs = render_all(kid, faces, close)
        for name, im in imgs.items():
            im.save(d / f"{name}.png")
        images[kid] = imgs
        xs = [c["o"][0] for c in cubes] + [c["o"][0] + c["s"][0] for c in cubes]
        ys = [c["o"][1] for c in cubes] + [c["o"][1] + c["s"][1] for c in cubes]
        zs = [c["o"][2] for c in cubes] + [c["o"][2] + c["s"][2] for c in cubes]
        parts = {}
        for c in cubes:
            parts[PART_NAME[c["part"]]] = parts.get(PART_NAME[c["part"]], 0) + 1
        stats[kid] = dict(boxes=len(kept), buried=buried, over=over, vol_ok=vol_ok, tex=tex.shape[0],
                          glow_px=glow_px, x=(min(xs), max(xs)), y=(min(ys), max(ys)), z=(min(zs), max(zs)),
                          parts=parts, faces=len(faces))
        print(kid, json.dumps(stats[kid], ensure_ascii=False), flush=True)

    if len(kids) < 3:
        return
    cols = [("A 遺跡の島", [images["a"][k] for k in ("side", "top", "front", "back_close", "far")]),
            ("B 珊瑚礁の背", [images["b"][k] for k in ("side", "top", "front", "back_close", "far")]),
            ("C 潮門の巨亀", [images["c"][k] for k in ("side", "top", "front", "back_close", "far")])]
    rows = ["側面", "3/4 上から", "正面", "遺跡の近景", "遠景 (140px)"]
    labeled = [(lab, ims) for lab, ims in cols]
    sht = sheet("潮殻の巨神 (Tide Shell titan) 第2回ラフ 3案 (修正)", labeled,
                "行: " + " / ".join(rows) + "。水際 (y=30) より下は藻で暗い。")
    SHEET.parent.mkdir(parents=True, exist_ok=True)
    sht.save(SHEET)
    (OUT / "stats.json").write_text(json.dumps(stats, ensure_ascii=False, indent=1), encoding="utf-8")


if __name__ == "__main__":
    main()
