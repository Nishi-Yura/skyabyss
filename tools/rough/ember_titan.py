"""余燼の巨神 (Ember titan) 第1回ラフ: 3案 A/B/C。火蜥蜴の体は3案で共通、背の上だけを変える。

使い方 (worktree の直下で): PYTHONIOENCODING=utf-8 python -B tools/rough/ember_titan.py [a b c]
出力: docs/review/ember_roughs/titan/<a|b|c>/ (geo.json, texture.png, glowmask.png,
      side/top/front/back_close/far.png)、stats.json、比較シート docs/review/ember_roughs/titan.png

作り方 (tide_shell_titan と同じ):
- 形は 0.5 単位の格子 (1 セル = 0.5 単位 = 1 画素) に彫る。1 セルには 1 つの材料だけ。
  同じ材料を貪欲に箱にする (greedy meshing)。箱どうしは重ならない (check_grid で重なり 0)。
- 隣が空いている面だけ uv を書く。全部の面が隠れた箱は捨てる。
- テクスチャは 2 px / 単位。模様はワールド座標で決める。溶岩の継ぎ目・目・喉・火口は glowmask で光る。
- 乱数なし (hz / nzv のハッシュ)。
- 座標: 1 単位 = 1/16 ブロック。y 上、地面 y=0 (下には描かない)。頭は -Z、尾は +Z。
  頭の先 z -206、胴 z -100〜100 (幅 約 112)、尾の先 z 247 (全長 約 453)。脚を入れた幅 約 230。
- 姿勢: 夜、地面に伏せて眠る。顎は地面 (y=0) に、腹は y=3、四肢は肘を外に張って平たく。
  口は少し開いている (正面から入れる口。のちに乗り込み口になる)。
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

OUT = ROOT / "docs/review/ember_roughs/titan"
SHEET = ROOT / "docs/review/ember_roughs/titan.png"

# ---------------------------------------------------------------- 格子
CELL = 2
X0, X1 = -126.0, 126.0
Y0, Y1 = 0.0, 192.0                   # 地面より下は作らない
Z0, Z1 = -210.0, 250.0
NX = int((X1 - X0) * CELL)
NY = int((Y1 - Y0) * CELL)
NZ = int((Z1 - Z0) * CELL)
K = None
HM = None                             # 体を作った後の高さの地図 (x, z) -> 上面の y (単位)

PART = {"body": 1, "ridge": 2, "head": 3, "jaw": 4, "leg_fl": 5, "leg_fr": 6, "leg_rl": 7, "leg_rr": 8,
        "tail": 9, "smoke": 10}
PART_NAME = {v: k for k, v in PART.items()}
MAT = {"basalt": 1, "sbasalt": 2, "belly": 3, "ash": 4, "slag": 5, "deepred": 6, "lava": 7, "seam": 8,
       "ember": 9, "molten": 10, "eye": 11, "dark": 12, "tongue": 13, "teeth": 14, "claw": 15, "bronze": 16,
       "brick": 17, "column": 18, "smoke": 19, "steam": 20, "slagdark": 21, "crust": 22, "gland": 23,
       "smoke2": 24, "cinder": 25}
MAT_NAME = {v: k for k, v in MAT.items()}
GLOW_ALL = {"lava", "seam", "ember", "molten"}

TONES = {
    "basalt": [(54, 52, 56), (46, 44, 48), (62, 60, 64)],
    "sbasalt": [(92, 92, 96), (82, 82, 88), (102, 100, 104)],
    "belly": [(172, 166, 158), (158, 152, 146), (186, 180, 170)],
    "ash": [(200, 196, 190), (184, 180, 174), (212, 208, 200)],
    "slag": [(104, 76, 58), (90, 64, 50), (116, 86, 64)],
    "deepred": [(122, 32, 26), (104, 26, 22), (140, 42, 30)],
    "tongue": [(150, 48, 42), (134, 40, 36)],
    "bronze": [(156, 114, 62), (136, 96, 52), (172, 128, 72)],
    "brick": [(94, 52, 42), (82, 46, 38), (104, 58, 46)],
    "column": [(72, 72, 78), (64, 64, 70), (80, 80, 86)],
    "smoke": [(122, 120, 120), (108, 106, 108), (136, 134, 132)],
    "smoke2": [(92, 90, 92), (82, 80, 84)],
    "steam": [(222, 222, 218), (208, 208, 206), (234, 234, 230)],
    "claw": [(36, 34, 38), (44, 42, 46)],
    "crust": [(58, 42, 38), (50, 36, 32)],
    "gland": [(78, 74, 78), (70, 66, 70)],
    "cinder": [(132, 126, 120), (118, 112, 108), (144, 138, 130)],
}


# ---------------------------------------------------------------- ハッシュ

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
    """box (単位) の中を fn(X, Y, z) で彫る。mode: empty / set / zero / replace"""
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


def solid_box(box, part, mat, mode="empty"):
    fill(lambda X, Y, z: (np.ones(X.shape, bool), mat), box, part, mode)


def q1(v):
    return np.floor(v) + 0.5


def q2(v):
    return np.floor(v / 2.0) * 2.0 + 1.0


def compute_hm():
    """各 (x, z) の列で一番上の詰まったセルの上端 (単位)。空なら 0"""
    global HM
    occ = K != 0
    rev = occ[:, ::-1, :]
    top = NY - np.argmax(rev, axis=1)          # (NX, NZ)
    top[~occ.any(axis=1)] = 0
    HM = Y0 + top / CELL


def topf(X, z):
    i = np.clip(((np.asarray(X) - X0) * CELL).astype(int), 0, NX - 1)
    k = np.clip(((np.asarray(z) - Z0) * CELL).astype(int), 0, NZ - 1)
    return HM[i, k]


def seg_dist_t(X, Y, z, P, Q):
    P = np.asarray(P, float)
    Q = np.asarray(Q, float)
    d = Q - P
    L2 = float(d @ d)
    px, py, pz = X - P[0], Y - P[1], z - P[2]
    t = np.clip((px * d[0] + py * d[1] + pz * d[2]) / L2, 0, 1)
    dx, dy, dz = px - t * d[0], py - t * d[1], pz - t * d[2]
    return np.sqrt(dx * dx + dy * dy + dz * dz), t


# ---------------------------------------------------------------- 体 (首・胴・尾)

PZ = np.array([-136, -120, -100, -80, -55, -20, 20, 55, 85, 108, 130, 160, 190, 220, 247], float)
PW = np.array([35, 37, 42, 49, 54, 56, 56, 54, 50, 42, 33, 24, 15, 8, 2.5], float)
PT = np.array([30, 32, 38, 47, 56, 61, 62, 60, 55, 46, 37, 28, 19, 11, 4.5], float)
PB = np.array([3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 2.5, 2, 1.5, 1, 1], float)
PL = 12.0                         # 背の鱗板の大きさ


def prof(z):
    w = np.interp(z, PZ, PW)
    t = np.interp(z, PZ, PT)
    b = np.interp(z, PZ, PB)
    return w, t, b, b + (t - b) * 0.42


def cx_of(z):
    """尾は後ろへ行くほど +x へ曲がる"""
    return 26.0 * np.clip((np.asarray(z, float) - 110.0) / 137.0, 0, 1) ** 2


def ytop_raw(u, t, yc):
    return yc + (t - yc) * np.sqrt(np.clip(1 - np.minimum(u, 1.0) ** 2.5, 0, 1))


def plate_info(s, z):
    c = np.round(s / PL)
    odd = np.mod(c, 2)
    rz = np.floor((z + PL / 2 * odd) / PL)
    pz = rz * PL + PL / 2 - PL / 2 * odd
    px = c * PL
    wc, tc, bc, ycc = prof(pz)
    uc = np.abs(px) / wc
    ptop = np.round(ytop_raw(uc, tc, ycc)) + 2.0
    dx = PL / 2 - np.abs(s - px)
    dz = PL / 2 - np.abs(z - pz)
    return c, rz, px, pz, uc, ptop, np.minimum(dx, dz), dx, dz, wc


def seam_mat(c, rz, px, pz, s, z, dx, dz, glow_rate):
    """継ぎ目の材料: 継ぎ目の線ごとに、光る溶岩か暗い鉱滓かを決める"""
    vert = dx < dz
    ex = np.round(px + np.sign(s - px) * PL / 2)
    ez = np.round(pz + np.sign(z - pz) * PL / 2)
    h = np.where(vert, nzv(ex + 1000, rz), nzv(c + 2000, ez))
    return np.where(h < glow_rate, MAT["seam"], MAT["slagdark"])


def body_fn(glow_rate):
    def fn(X, Y, z):
        Xc = q1(X[:, :1])
        zs = math.floor(z) + 0.5
        w, t, b, yc = (float(v) for v in prof(zs))
        cx = float(cx_of(zs))
        s = Xc - cx
        costal = (-92 < zs < 104) and (zs % 9.0 < 1.0)       # 肋の溝 (蜥蜴・山椒魚の脇腹の縦の溝)
        weff = w - 2.0 if costal else w
        s2 = q2(X[:, :1]) - cx
        ue = np.abs(s2) / weff                                # 脇腹は x も y も 2 単位の段 (箱を減らす)
        Yraw = q2(Y[:1, :])
        Yq = q1(Y[:1, :])
        yt = ytop_raw(ue, t, yc)
        low = (ue ** 2.5 + np.abs((yc - Yraw) / (yc - b)) ** 4) <= 1.0
        raw = (ue <= 1.0) & np.where(Yraw >= yc, Yraw <= yt, low)
        c, rz, px, pz, uc, ptop, d, dx, dz, wc = plate_info(s, zs)
        zone = (uc <= 0.7) & (wc > 4.0)
        colTop = np.where(d < 1.0, ptop - 3.0, np.where(d < 2.0, ptop - 1.0, ptop))
        pcell = (Yq <= colTop)
        upper = zone & (Yq >= yc - 2.0)
        inside = np.where(upper, pcell, raw)
        mat = np.full(inside.shape, MAT["sbasalt"], np.int64)
        mat = np.where(zone, MAT["basalt"], mat)
        groove = zone & (d < 1.0)
        mat = np.where(groove, seam_mat(c, rz, px, pz, s, zs, dx, dz, glow_rate), mat)
        if costal:
            mat = np.where(~zone & (np.abs(s2) > weff - 2.5), MAT["slag"], mat)
        belly_line = b + (yc - b) * 0.75
        mat = np.where(Yraw < belly_line, MAT["belly"], mat)
        return inside, mat
    return fn


# ---------------------------------------------------------------- 頭と顎

HZ = np.array([-206, -202, -196, -188, -178, -166, -154, -144, -136], float)
HWa = np.array([8, 20, 29, 35, 39, 41, 40, 37, 35], float)
HT = np.array([20, 25, 28, 30, 31, 32, 33, 33, 31], float)
ML = 6.0          # 下顎の上面 (口の床)
G0 = 11.0         # 口の開き (前で)
ZH = -150.0       # 口の端 (顎の蝶番)
EYES = [(-26.0, -171.0), (26.0, -171.0)]
GLANDS = [(-22.0, -148.0), (22.0, -148.0)]
HPL = 10.0        # 頭の上の板の大きさ


def head_top(Xs, zs):
    w = np.interp(zs, HZ, HWa)
    t = np.interp(zs, HZ, HT)
    u = np.abs(Xs) / w
    return t * np.clip(1 - np.minimum(u, 1) ** 4, 0, 1) ** 0.35, u, w, t


def gap_at(Xs, zs):
    """口の開き (y の幅)。前で G0、口の端 (ZH) で 0。中央 (喉) は奥まで少し開く"""
    g = G0 * np.clip((ZH - zs) / 26.0, 0, 1) ** 0.6
    throat = np.where(np.abs(Xs) <= 12.0, G0 * 0.85 * np.clip((-134.0 - zs) / 8.0, 0, 1) ** 0.4, 0.0)
    return np.maximum(g, throat)


def head_mask(X, Y, z):
    Xs = q1(X[:, :1])
    zs = math.floor(z) + 0.5
    Yq = q1(Y[:1, :])
    yt, u, w, t = head_top(Xs, zs)
    hb = 0.0 if zs < -148 else float(np.interp(zs, [-148, -136], [0, 3]))
    # 頭の上の板 (平たい板と溝)。板の中心の高さにそろえる
    col = np.round(Xs / HPL)
    odd = np.mod(col, 2)
    row = np.floor((zs + HPL / 2 * odd) / HPL)
    pz = row * HPL + HPL / 2 - HPL / 2 * odd
    px = col * HPL
    ytc, uc, _, _ = head_top(px, pz)
    d = np.minimum(HPL / 2 - np.abs(Xs - px), HPL / 2 - np.abs(zs - pz))
    zone = (uc < 0.62) & (pz > -196.0) & (pz < -138.0)
    ptop = np.round(ytc) + 1.0
    ptop = np.where(d < 1.0, ptop - 2.0, np.where(d < 2.0, ptop - 0.5, ptop))
    yt2, _, _, _ = head_top(q2(X[:, :1]), zs)
    ytop = np.where(zone, ptop, np.round(yt2 / 2.0) * 2.0)     # 板の外は x も y も 2 単位の段 (箱を減らす)
    lowr = np.where(Yq < ML, (np.minimum(u, 1) ** 3 + ((ML - Yq) / ML) ** 3) <= 1.0, True)
    m = (u <= 1.0) & (Yq <= ytop) & (Yq >= hb) & lowr & (zs >= -206.0)
    # 下顎は上顎より少し小さい (上顎がかぶさる)
    ul = np.abs(Xs) / np.maximum(w - 1.5, 1.0)
    m &= ~((Yq <= ML) & ((ul > 1.0) | (zs < -204.0)))
    g = np.minimum(gap_at(Xs, zs), np.maximum(yt - 5.0 - ML, 0.0))
    cav = (Yq > ML) & (Yq < ML + g) & (u < 1.0 - 1.0 / w)
    teeth = cav & (Yq > ML + g - 2.5) & ((u > 1.0 - 3.5 / w) | (zs < -199.0)) & \
        (np.mod(np.floor(Xs / 2.0) + np.floor(zs / 2.0), 2) == 0)
    sidegap = (Yq > ML) & (Yq < ML + g) & (u >= 1.0 - 1.0 / w)
    m = m & ~(cav & ~teeth) & ~sidegap
    mat = np.full(m.shape, MAT["sbasalt"], np.int64)
    mat = np.where(zone & (Yq > ytop - 3.5), MAT["basalt"], mat)
    groove = zone & (d < 1.0)
    gh = nzv(np.floor(Xs / 5.0) + 300, np.floor(zs / 5.0))
    mat = np.where(groove, np.where(gh < 0.3, MAT["seam"], MAT["slagdark"]), mat)
    mat = np.where((Yq < 3.0) & (zs > -150), MAT["belly"], mat)
    # 唇・口の中
    inner = u < 1.0 - 3.0 / w
    mat = np.where((Yq > ML - 1.5) & (Yq <= ML) & inner & (g > 0.5), MAT["tongue"], mat)
    mat = np.where((Yq >= ML + g) & (Yq < ML + g + 1.5) & inner & (g > 0.5),
                   np.where(zs < -196.0, MAT["deepred"], MAT["slagdark"]), mat)
    mat = np.where(teeth, MAT["teeth"], mat)
    # 喉の奥は熾火で光る
    mat = np.where((np.abs(Xs) <= 13.0) & (zs > -142.0) & (Yq > ML - 2) & (Yq < ML + G0 + 1), MAT["ember"], mat)
    # 下顎の外側の唇の線 (暗い)
    mat = np.where((Yq > ML - 1.5) & (Yq <= ML) & ~inner & (zs < ZH), MAT["slagdark"], mat)
    return m, mat, Xs, zs, Yq


def head_fn(X, Y, z):
    m, mat, Xs, zs, Yq = head_mask(X, Y, z)
    m = m & ~((Yq <= ML) & (zs < ZH))
    return m, mat


def jaw_fn(X, Y, z):
    m, mat, Xs, zs, Yq = head_mask(X, Y, z)
    m = m & (Yq <= ML) & (zs < ZH)
    mat = np.where(Yq < 2.0, MAT["belly"], mat)
    return m, mat


def eye_fn(X, Y, z):
    """目の膨らみ (小さく光る目)、目の上の眉の隆起、耳下腺の膨らみ (光る孔)"""
    Xs = q1(X)
    zs = math.floor(z) + 0.5
    Yq = q1(Y)
    m = np.zeros(X.shape, bool)
    mat = np.full(X.shape, MAT["sbasalt"], np.int64)
    for ex, ez in EYES:
        yb, _, _, _ = head_top(np.array(ex), ez)
        ey = float(np.round(yb)) + 1.0
        sd = np.sign(ex)
        dd = np.sqrt(((Xs - ex) / 6.5) ** 2 + ((Yq - ey) / 5.0) ** 2 + ((zs - ez) / 6.5) ** 2)
        bump = dd <= 1.0
        brow = (np.sqrt(((Xs - ex + sd * 1.5) / 8.5) ** 2 + ((Yq - ey - 1.0) / 5.5) ** 2 +
                        ((zs - ez - 1.5) / 8.0) ** 2) <= 1.0) & (Yq > ey + 1.5) & ~bump
        m |= bump | brow
        mat = np.where(brow, MAT["basalt"], mat)
        core = np.sqrt((Xs - ex - sd * 3.0) ** 2 + (Yq - ey - 2.0) ** 2 + (zs - ez + 3.0) ** 2) <= 3.6
        mat = np.where(bump & core, MAT["eye"], mat)
    for gx, gz in GLANDS:
        yb, _, _, _ = head_top(np.array(gx), gz)
        gy = float(np.round(yb)) - 1.0
        dd = np.sqrt(((Xs - gx) / 9.0) ** 2 + ((Yq - gy) / 5.0) ** 2 + ((zs - gz) / 12.0) ** 2)
        m |= dd <= 1.0
        mat = np.where(dd <= 1.0, MAT["gland"], mat)
    return m, mat


# ---------------------------------------------------------------- 脚 (肘を外に張る)

def limb_fn(segs, toes, side):
    """segs: [(P, Q, rP, rQ)]、toes: [(P, Q, r)] (指と爪)。1 単位の格子に寄せる"""
    def fn(X, Y, z):
        Xs, Yq, zs = q1(X), q1(Y), math.floor(z) + 0.5
        X2, Y2, z2 = q2(X), q2(Y), math.floor(z / 2.0) * 2.0 + 1.0     # 腕と脚は 2 単位の格子 (箱を減らす)
        m = np.zeros(X.shape, bool)
        mat = np.full(X.shape, MAT["sbasalt"], np.int64)
        for P, Q, rp, rq in segs:
            dist, t = seg_dist_t(X2, Y2, z2, P, Q)
            r = rp + (rq - rp) * t
            inn = dist <= r
            cy = P[1] + (Q[1] - P[1]) * t
            L = float(np.linalg.norm(np.subtract(Q, P)))
            ring = np.mod(t * L, 8.0) < 2.0
            mm = np.where(Y2 > cy + r * 0.15, MAT["basalt"], MAT["sbasalt"])
            mat = np.where(inn & ~m, mm, mat)
            m |= inn
        for P, Q, r in toes:
            dist, t = seg_dist_t(Xs, Yq, zs, P, Q)
            inn = (dist <= r * (1.0 - 0.3 * t)) & ~m
            mat = np.where(inn, np.where(t > 0.72, MAT["claw"], MAT["sbasalt"]), mat)
            m |= inn
        m &= Yq >= 0.0
        return m, mat
    return fn


def make_leg(front, side):
    sx = side
    if front:
        S, E, W = (44 * sx, 24, -76), (84 * sx, 28, -82), (96 * sx, 6, -100)
        palm = (98 * sx, 3.5, -106)
        r = (13.0, 11.0, 7.5)
        angs = [-30, 0, 30, 60]
        fwd = -1.0
        L = 14.0
    else:
        S, E, W = (42 * sx, 24, 84), (84 * sx, 28, 92), (96 * sx, 6, 112)
        palm = (98 * sx, 3.5, 118)
        r = (14.5, 12.0, 8.0)
        angs = [-15, 15, 45, 75, 105]
        fwd = 1.0
        L = 13.0
    segs = [(S, E, r[0], r[1]), (E, W, r[1], r[2]), (W, palm, r[2], 6.0)]
    toes = []
    for a in angs:
        ar = math.radians(a)
        dv = np.array([math.sin(ar) * sx, 0.0, math.cos(ar) * fwd])
        P = np.array(palm, float) + dv * 4.0
        P[1] = 3.0
        Q = P + dv * L
        Q[1] = 1.5
        toes.append((tuple(P), tuple(Q), 3.0))
        C2 = Q + dv * 3.5
        C2[1] = 0.5
        toes.append((tuple(Q), tuple(C2), 1.8))       # 掘る爪の先 (下へ曲がる)
    pts = np.array([S, E, W, palm] + [t[1] for t in toes])
    lo = pts.min(0) - 16
    hi = pts.max(0) + 16
    return limb_fn(segs, toes, side), (lo[0], hi[0], 0.0, hi[1], lo[2], hi[2])


# ---------------------------------------------------------------- 3案に共通の体

def build_body(glow_rate):
    fill(jaw_fn, (-44, 44, 0, ML + 1, -210, ZH), PART["jaw"])
    fill(head_fn, (-44, 44, 0, 40, -210, -136), PART["head"])
    fill(eye_fn, (-40, 40, 14, 48, -182, -134), PART["head"])
    # 鼻孔 (鼻先の上の 2 つの小さな穴)
    for nx in (-8.0, 8.0):
        yb, _, _, _ = head_top(np.array(nx), -200.0)
        ytop = float(np.round(yb))
        fill(lambda X, Y, z, nx=nx, ytop=ytop: (inb(X, nx - 1.5, nx + 1.5) & inb(z, -202, -198) &
                                                (Y > ytop - 1.0), 0), (nx - 2, nx + 2, 10, 40, -203, -197),
             mode="zero")
        fill(lambda X, Y, z, nx=nx, ytop=ytop: (inb(X, nx - 1.5, nx + 1.5) & inb(z, -202, -198) &
                                                (Y > ytop - 2.0) & (Y <= ytop - 1.0), MAT["dark"]),
             (nx - 2, nx + 2, 10, 40, -203, -197), PART["head"], mode="replace")
    fn = body_fn(glow_rate)
    fill(fn, (-62, 62, 0, 72, -136, 108), PART["body"])
    fill(fn, (-40, 90, 0, 72, 108, 248), PART["tail"])
    for front, side, name in ((True, -1, "leg_fl"), (True, 1, "leg_fr"), (False, -1, "leg_rl"),
                              (False, 1, "leg_rr")):
        lf, bx = make_leg(front, side)
        fill(lf, bx, PART[name])


# ---------------------------------------------------------------- A 火山の背

ZV = -40.0


def smoke_puffs(puffs, part="smoke"):
    for i, (cx, cy, cz, h) in enumerate(puffs):
        mat = MAT["smoke"] if i % 3 != 1 else MAT["smoke2"]
        solid_box((cx - h, cx + h, cy - h, cy + h, cz - h, cz + h), PART[part], mat)
        # 塊を 1 つ足して、角ばった煙の房にする
        o = h * 0.55
        solid_box((cx - o + h * 0.5, cx + o + h * 0.5, cy + h * 0.2, cy + h * 0.2 + 2 * o,
                   cz - o - h * 0.4, cz + o - h * 0.4), PART[part], MAT["smoke"])


def build_a():
    Rb, Rr, Yb, YT = 52.0, 16.0, 44.0, 128.0
    rivs = [0.35, 1.55, 2.65, 3.95, 5.15]

    def cone(X, Y, z):
        Xs = q2(X)
        zs = math.floor(z / 2.0) * 2.0 + 1.0
        Yl = q2(Y)
        dx, dz = Xs, zs - ZV
        th = np.arctan2(dz, dx)
        rh = (np.abs(dx) ** 2.3 + np.abs(dz) ** 2.3) ** (1 / 2.3)
        f = np.clip((Yl - Yb) / (YT - Yb), 0, 1)
        r = Rr + (Rb - Rr) * (1 - f) ** 1.8
        r = r * (1 + 0.05 * np.sin(5 * th + 1.0) + 0.03 * np.sin(9 * th + 2.0))
        sect = np.floor(np.mod(th, 2 * math.pi) / (2 * math.pi) * 18)
        rimh = 2.0 * np.floor(3.0 * nzv(sect, 77))
        body = (rh <= r) & (Yl >= Yb) & (Yl <= YT)
        rim = (rh <= Rr + 1) & (rh > Rr - 4.0) & (Yl > YT) & (Yl <= YT + rimh)
        m = body | rim
        crater = (rh < Rr - 4.0) & (Yl > YT - 9.0)
        shaft = (rh < 7.0) & (Yl > 72.0)
        m &= ~crater & ~shaft
        shell = rh > r - 2.5
        mat = np.full(m.shape, MAT["basalt"], np.int64)
        mat = np.where(shell & (f > 0.72), MAT["ash"], mat)
        mat = np.where(shell & (f > 0.6) & (f <= 0.72) & (nzv(Xs, zs) < 0.5), MAT["ash"], mat)
        mat = np.where(rim | (Yl > YT - 2), MAT["slag"], mat)
        for k, a in enumerate(rivs):
            wig = a + 0.10 * np.sin(Yl / 7.0 + k)
            da = np.abs(np.mod(th - wig + math.pi, 2 * math.pi) - math.pi) * np.maximum(rh, 1)
            mat = np.where(shell & (da < 2.2) & (f < 0.97) & (f > 0.03), MAT["lava"], mat)
        # 火口の床・井戸 (火の井戸。体の中の広間へ降りる)
        floor = (rh < Rr - 4.0) & (Yl > YT - 11.0)
        mat = np.where(floor, MAT["crust"], mat)
        mat = np.where(floor & (rh < 11.0), MAT["lava"], mat)
        wall = (rh >= 7.0) & (rh < 9.5) & (Yl > 72.0) & (Yl < YT - 11.0)
        mat = np.where(wall, np.where(Yl < 104.0, MAT["ember"], MAT["deepred"]), mat)
        mat = np.where((rh < 7.0) & (Yl > 70.0) & (Yl <= 72.0), MAT["molten"], mat)
        return m, mat
    fill(cone, (-62, 62, Yb - 1, YT + 8, ZV - 62, ZV + 62), PART["ridge"])
    # 玄武岩の柱の群れ (火山の裾)
    for gi, (gx, gz, n) in enumerate(((-40.0, ZV + 34.0, 9), (38.0, ZV - 30.0, 8), (34.0, ZV + 40.0, 7),
                                     (-30.0, ZV - 44.0, 6))):
        for j in range(n):
            ang = 2 * math.pi * hz(gi, j, 1)
            rr = 9.0 * math.sqrt(hz(gi, j, 2))
            px = math.floor((gx + rr * math.cos(ang)) / 3.0) * 3.0
            pz = math.floor((gz + rr * math.sin(ang)) / 3.0) * 3.0
            base = float(topf(np.array(px + 1.5), pz + 1.5))
            if base < 30:
                continue
            h = 6.0 + 2.0 * math.floor(7.0 * hz(gi, j, 3))
            solid_box((px, px + 3, base - 2, base + h, pz, pz + 3), PART["ridge"], MAT["column"])
    # 溶岩の流れ: 火山の裾から背を下り、脇腹を地面まで (2 筋)
    for side, zf in ((-1, ZV + 16.0), (1, ZV - 6.0)):
        lavafall(zf - 2.0, zf + 2.0, side, 64.0)
    # 火口の床から立つ細い煙 (井戸の真上は空けて、脇から立てる)
    solid_box((5.0, 9.0, YT - 9.0, YT + 12.0, ZV + 6.0, ZV + 10.0), PART["smoke"], MAT["smoke2"])
    solid_box((3.0, 9.0, YT + 6.0, YT + 12.0, ZV + 8.0, ZV + 14.0), PART["smoke"], MAT["smoke2"])
    smoke_puffs([(2.0, YT + 16.0, ZV + 14.0, 6.0), (4.0, YT + 26.0, ZV + 22.0, 8.0),
                 (-1.0, YT + 38.0, ZV + 32.0, 9.0), (5.0, YT + 47.0, ZV + 44.0, 7.0)])


def lavafall(z0, z1, side, ytop):
    """脇腹を流れ落ちる溶岩: 各 (y, z) で体の一番外のセルの、さらに外の 1 単位を溶岩にする。
    流れは高さにつれて z に揺れ、下ほど少し広がる"""
    jt = cj(ytop)
    key = MAT["lava"] + PART["ridge"] * 32
    zc0 = (z0 + z1) / 2
    for j in range(0, jt):
        y = Y0 + (j + 0.5) / CELL
        zc = zc0 + 2.0 * round(2.5 * math.sin(y / 9.0 + z0))
        hw = (z1 - z0) / 2 + (2.0 if y < 6 else 0.0)
        for k in range(ck(zc - hw), ck(zc + hw)):
            row = K[:, j, k] != 0
            if side > 0:
                idx = np.nonzero(row[NX // 2:])[0]
                if idx.size == 0:
                    continue
                i = NX // 2 + idx.max() + 1
                if i + 2 <= NX:
                    K[i:i + 2, j, k] = np.where(K[i:i + 2, j, k] == 0, key, K[i:i + 2, j, k])
            else:
                idx = np.nonzero(row[:NX // 2])[0]
                if idx.size == 0:
                    continue
                i = idx.min()
                if i - 2 >= 0:
                    K[i - 2:i, j, k] = np.where(K[i - 2:i, j, k] == 0, key, K[i - 2:i, j, k])


# ---------------------------------------------------------------- B 炉の都

def slab(x0, x1, z0, z1, ytop, mat, part="ridge", need=16.0):
    """背の上に、上面 ytop の平らな台を積む (体のある所だけ、下は体の上面まで)"""
    fill(lambda X, Y, z: (inb(X, x0, x1) & inb(z, z0, z1) & (Y <= ytop) &
                          (topf(X, z) > ytop - need), mat),
         (x0 - 1, x1 + 1, 24, ytop + 1, z0 - 1, z1 + 1), PART[part])


def hall(x0, x1, z0, z1, yb, h, steps, door_side):
    """段になった鍛冶場: 煉瓦の壁、段の縁は青銅の帯、窓と戸口は炉の火で光る"""
    solid_box((x0, x1, yb, yb + h, z0, z1), PART["ridge"], MAT["brick"])
    # 窓 (両脇、8 単位ごと) と戸口
    for zz in np.arange(z0 + 4, z1 - 3, 8):
        for xx in (x0, x1 - 0.5):
            solid_box((xx, xx + 0.5, yb + h * 0.45, yb + h * 0.45 + 3, zz, zz + 2), PART["ridge"], MAT["ember"],
                      mode="set")
    cxm = (x0 + x1) / 2
    zz = z0 if door_side < 0 else z1 - 0.5
    solid_box((cxm - 3, cxm + 3, yb, yb + min(9.0, h - 2), zz, zz + 0.5), PART["ridge"], MAT["ember"], mode="set")
    y = yb + h
    ins = 3.0
    for i in range(steps):
        solid_box((x0 + ins * i, x1 - ins * i, y, y + 1, z0 + ins * i, z1 - ins * i), PART["ridge"], MAT["bronze"])
        y += 1
        hh = 4.0 if i < steps - 1 else 3.0
        solid_box((x0 + ins * (i + 1), x1 - ins * (i + 1), y, y + hh, z0 + ins * (i + 1), z1 - ins * (i + 1)),
                  PART["ridge"], MAT["slag"] if i % 2 else MAT["brick"])
        y += hh
    return y


def chimney(cx, cz, ytop, broken=False):
    base = float(topf(np.array(cx), cz))
    b0 = base - 4.0
    solid_box((cx - 6, cx + 6, b0, base + 10, cz - 6, cz + 6), PART["ridge"], MAT["slag"])
    solid_box((cx - 4.5, cx + 4.5, base + 10, ytop, cz - 4.5, cz + 4.5), PART["ridge"], MAT["brick"])
    y = base + 22.0
    while y < ytop - 6:
        solid_box((cx - 5, cx + 5, y, y + 1.5, cz - 5, cz + 5), PART["ridge"], MAT["bronze"])
        y += 14.0
    if broken:
        # 折れた先: 片側だけが少し高く残る
        solid_box((cx - 4.5, cx, ytop, ytop + 4, cz - 4.5, cz + 4.5), PART["ridge"], MAT["brick"])
        fill(lambda X, Y, z: (inb(X, cx - 2.5, cx + 2.5) & inb(z, cz - 2.5, cz + 2.5) & (Y > ytop - 3), 0),
             (cx - 3, cx + 3, ytop - 4, ytop + 5, cz - 3, cz + 3), mode="zero")
        solid_box((cx - 2.5, cx + 2.5, ytop - 4, ytop - 3, cz - 2.5, cz + 2.5), PART["ridge"], MAT["ember"],
                  mode="set")
        return ytop + 4
    solid_box((cx - 5.5, cx + 5.5, ytop, ytop + 2, cz - 5.5, cz + 5.5), PART["ridge"], MAT["bronze"])
    fill(lambda X, Y, z: (inb(X, cx - 2.5, cx + 2.5) & inb(z, cz - 2.5, cz + 2.5) & (Y > ytop - 4), 0),
         (cx - 3, cx + 3, ytop - 5, ytop + 3, cz - 3, cz + 3), mode="zero")
    solid_box((cx - 2.5, cx + 2.5, ytop - 5, ytop - 4, cz - 2.5, cz + 2.5), PART["ridge"], MAT["ember"], mode="set")
    return ytop + 2


def build_b():
    # 台 (二段)
    slab(-32, 32, -94, -28, 66.0, MAT["column"])
    slab(-26, 26, -88, -34, 69.0, MAT["slag"])
    slab(-34, 34, -6, 74, 64.0, MAT["column"])
    slab(-28, 28, 0, 68, 67.0, MAT["slag"])
    # 台の間の溶岩の水路 (煉瓦の縁)
    fill(lambda X, Y, z: (inb(X, -5, 5) & inb(z, -30, -4) & (Y <= topf(X, z) + 3.0) & (Y > topf(X, z) - 2.0) &
                          (np.abs(X) > 2.5), MAT["brick"]), (-6, 6, 50, 72, -31, -3), PART["ridge"])
    fill(lambda X, Y, z: (inb(X, -2.5, 2.5) & inb(z, -30, -4) & (Y <= topf(X, z) + 2.0) &
                          (Y > topf(X, z) - 1.0), MAT["lava"]), (-3, 3, 50, 72, -31, -3), PART["ridge"])
    # 鍛冶場
    hall(-20, 20, -82, -44, 69.0, 15.0, 3, -1)
    hall(-27, -5, 6, 40, 67.0, 13.0, 2, -1)
    hall(5, 28, 20, 62, 67.0, 11.0, 3, 1)
    # 崩れ: 鍛冶場の屋根と壁の角が欠けている (灰の民が去った後の廃墟)
    for bx in ((10, 21, 80, 100, -82, -70), (-28, -18, 74, 90, 30, 41), (20, 29, 72, 90, 52, 63)):
        fill(lambda X, Y, z, bx=bx: (inb(X, bx[0], bx[1]) & inb(Y, bx[2], bx[3]) & inb(z, bx[4], bx[5]) &
                                     (Y - bx[2] > 2.0 * np.abs(np.floor(X - (bx[0] + bx[1]) / 2) / 2.0)), 0),
             (bx[0] - 1, bx[1] + 1, bx[2], bx[3], bx[4] - 1, bx[5] + 1), mode="zero")
    # 煙突 (5 本 + 折れた 1 本)
    tops = []
    for cx, cz, yt, br in ((-24.0, -60.0, 124.0, False), (22.0, -68.0, 112.0, False), (0.0, -16.0, 142.0, False),
                           (-22.0, 52.0, 118.0, False), (22.0, 8.0, 132.0, False), (2.0, 84.0, 100.0, True)):
        tops.append((cx, chimney(cx, cz, yt, br), cz, br))
    # 橋: 鍛冶場の屋根から大煙突へ、二つの鍛冶場の間
    solid_box((-3, 3, 91, 92.5, -46, -20.5), PART["ridge"], MAT["bronze"])
    for zz in np.arange(-45, -21, 6):
        for xx in (-3, 2):
            solid_box((xx, xx + 1, 92.5, 95.5, zz, zz + 1), PART["ridge"], MAT["bronze"])
    solid_box((-5.5, 5.5, 80, 81.5, 26, 31), PART["ridge"], MAT["bronze"])
    # 脇腹へ落ちる溶岩 (台の縁から)
    lavafall(30.0, 34.0, -1, 64.0)
    lavafall(-64.0, -60.0, 1, 66.0)
    # 煙
    puffs = []
    for cx, ty, cz, br in tops:
        if br:
            continue
        puffs += [(cx + 0.5, ty + 4.5, cz + 2.0, 4.5), (cx + 2.0, ty + 12.5, cz + 8.0, 5.5)]
    smoke_puffs(puffs)


# ---------------------------------------------------------------- C 割れた背

CZ0, CZ1 = -116.0, 104.0


def crack_cx(z):
    """割れ目の中心 (1 単位にそろえる: 同じ位置の行が続き、箱がつながる)"""
    return np.round(4.0 * np.sin(z / 23.0) + 2.0 * np.sin(z / 9.0 + 1.0))


def crack_hw(z):
    return np.round(3.0 + 3.0 * np.sin(math.pi * np.clip((z - CZ0) / (CZ1 - CZ0), 0, 1)) ** 0.7)


def fin(zf, L, H, xin, side, base, center=False):
    """背骨の板 (鮫の鰭のように後ろが切り立つ三角)。xin は割れ目の側の縁、side は外の向き。
    根元は 7 単位の厚み、上ほど薄い。y と z は 2 単位の段"""
    def fn(X, Y, z, zf=zf, L=L, H=H, xin=xin, side=side, base=base):
        zs = math.floor(z / 2.0) * 2.0 + 1.0
        f = (zs - zf) / L
        if f < 0 or f > 1:
            return False, MAT["basalt"]
        hz_ = H * (f / 0.72) ** 1.1 if f < 0.72 else H * (1 - (f - 0.72) / 0.28) ** 0.6
        h = 2.0 * math.floor(hz_ / 2.0) + 2.0
        Y2 = q2(Y)
        a = np.clip((Y2 - base) / max(H, 1.0), 0, 1)
        th = 8.0 - 5.0 * a
        Xs = q2(X)
        if center:
            m = np.abs(Xs - xin) <= th / 2
        else:
            lo = xin + side * 0.0
            hi = xin + side * th
            m = (Xs >= np.minimum(lo, hi)) & (Xs <= np.maximum(lo, hi))
        m = m & (Y2 >= base) & (Y2 <= base + h)
        mat = np.where(Y2 > base + max(H * 0.78, h - 2.0), MAT["slag"], MAT["basalt"])
        if not center:
            # 割れ目に向いた面の下は熱で赤く光る
            mat = np.where((np.abs(Xs - xin) < 1.5) & (Y2 < base + 8.0), MAT["ember"], mat)
        return m, mat
    xl, xr = (xin - 9, xin + 9)
    fill(fn, (xl, xr, base - 1, base + H + 4, zf - 1, zf + L + 1), PART["ridge"])


def build_c():
    # 割れ目 (首から腰まで 1 本): 削って、底は溶岩、壁の下は熾火
    def crack(X, Y, z):
        zs = math.floor(z) + 0.5
        c = float(crack_cx(zs))
        hw = float(crack_hw(zs))
        Xs = q1(X)
        fl = min(50.0, 2.0 * round(float(topf(np.array(c), zs)) / 2.0) - 10.0)
        return (np.abs(Xs - c) <= hw) & (Y > fl), 0
    fill(crack, (-16, 16, 20, 120, CZ0, CZ1), mode="zero")

    def crack_skin(X, Y, z):
        zs = math.floor(z) + 0.5
        c = float(crack_cx(zs))
        hw = float(crack_hw(zs))
        Xs = q1(X)
        d = np.abs(Xs - c)
        tp = np.round(topf(Xs, zs))
        fl = min(50.0, 2.0 * round(float(topf(np.array(c), zs)) / 2.0) - 10.0)
        floor = (d <= hw) & (Y > fl - 2.0) & (Y <= fl)
        wall = (d > hw) & (d <= hw + 1.5) & (Y > fl - 2.0)
        lip = (d > hw) & (d <= hw + 3.0) & (Y > tp - 2.0)
        m = floor | wall | lip
        mat = np.where(floor, MAT["lava"],
                       np.where(wall & (Y <= fl + 7.0), MAT["ember"],
                                np.where(wall & (Y <= fl + 11.0), MAT["deepred"], MAT["slag"])))
        return m, mat
    fill(crack_skin, (-18, 18, 20, 120, CZ0, CZ1), PART["ridge"], mode="replace")
    # 背骨の板: 割れ目の両脇に互い違い (鮫の鰭の形)。首と尾では 1 列の小さな板
    HZP = np.array([-140, -112, -80, -30, 30, 80, 104, 140, 180, 222], float)
    HHP = np.array([10, 22, 40, 54, 52, 38, 26, 17, 10, 5], float)
    for side in (-1, 1):
        zf = -112.0 + (0.0 if side < 0 else 13.0)
        while zf + 22 < CZ1 + 4:
            zm = zf + 11.0
            c = float(crack_cx(zm))
            hw = float(crack_hw(zm))
            xin = c + side * (hw + 2.0)
            base = float(topf(np.array(xin + side * 3.5), zm)) - 3.0
            fin(zf, 22.0, float(np.interp(zm, HZP, HHP)), xin, side, base)
            zf += 26.0
    for zf in list(np.arange(-150.0, -118.0, 14.0)) + list(np.arange(112.0, 222.0, 14.0)):
        zm = zf + 6.0
        cxz = float(cx_of(zm))
        base = float(topf(np.array(cxz), zm)) - 3.0
        fin(zf, 12.0, float(np.interp(zm, HZP, HHP)) * 0.8, cxz, 1, base, center=True)
    # 灰の吹きだまり (背の脇に、低く広く)
    for dx, dz, rx, rz, h in ((-34.0, -34.0, 16.0, 26.0, 3.0), (32.0, 30.0, 15.0, 28.0, 3.0),
                              (30.0, -72.0, 12.0, 18.0, 2.0), (-30.0, 66.0, 12.0, 20.0, 3.0)):
        def dune(X, Y, z, dx=dx, dz=dz, rx=rx, rz=rz, h=h):
            Xs, Yq, zs = q1(X), q1(Y), math.floor(z) + 0.5
            rr = ((Xs - dx) / rx) ** 2 + ((zs - dz) / rz) ** 2
            wav = 0.15 * np.sin(Xs / 3.0 + zs / 5.0)
            tp = topf(Xs, zs)
            top = tp + np.round(h * np.clip(1 - rr + wav, 0, 1) ** 0.8)
            return (rr <= 1) & (Yq > tp - 1.0) & (Yq <= top) & (tp > 40), MAT["cinder"]
        fill(dune, (dx - rx - 1, dx + rx + 1, 30, 80, dz - rz - 1, dz + rz + 1), PART["ridge"], mode="set")
    # 熱と蒸気の噴き出し (割れ目から 2 本)
    for zj, n in ((-50.0, 4), (40.0, 3)):
        c = float(crack_cx(zj))
        fl = 50.0
        solid_box((c - 2, c + 2, fl - 4, fl + 34, zj - 2, zj + 2), PART["smoke"], MAT["steam"])
        y = fl + 34.0
        for i in range(n):
            h = 4.0 + 1.5 * i
            cx, cz = c + 2.5 * i, zj + 3.5 * i
            solid_box((cx - h, cx + h, y - 1, y + 2 * h - 1, cz - h, cz + h), PART["smoke"],
                      MAT["steam"] if i % 2 == 0 else MAT["smoke"])
            solid_box((cx - h * 0.6 + h * 0.6, cx + h * 0.6 + h * 0.6, y + h * 0.3, y + h * 1.5, cz - h * 1.3,
                       cz - h * 0.1), PART["smoke"], MAT["steam"])
            y += 2 * h - 2
    # 小さな遺跡: 割れ目の始まりに、青銅の門 (試練の入口)
    zg = CZ0 + 6.0
    c = float(crack_cx(zg))
    base = float(topf(np.array(c + 12), zg)) - 2.0
    for px in (c - 12.0, c + 9.0):
        solid_box((px, px + 3, base, base + 16, zg - 1.5, zg + 1.5), PART["ridge"], MAT["brick"])
    solid_box((c - 13.0, c + 13.0, base + 16, base + 19, zg - 2, zg + 2), PART["ridge"], MAT["bronze"])


# ---------------------------------------------------------------- 3案の作り方

def build(kid):
    global K
    K = np.zeros((NX, NY, NZ), np.int16)
    build_body({"a": 0.5, "b": 0.45, "c": 0.3}[kid])
    compute_hm()
    if kid == "a":
        build_a()
    elif kid == "b":
        build_b()
    else:
        build_c()
    drop_islands()


def drop_islands(min_cells=4000):
    """どこにもつながらない小さな塊 (歯のかけら等) を消す。煙も体か煙の柱につながっていれば残る"""
    from scipy import ndimage
    lab, n = ndimage.label(K != 0)
    if n <= 1:
        return
    # (memory: the sizes by bincount and the small labels by a lookup table; the old ndimage.sum over a ones array and np.isin
    #  over the whole label grid needed about 2 GB more.  Same result.)
    sizes = np.bincount(lab.ravel(), minlength=n + 1)[1:]
    small = np.flatnonzero(sizes < min_cells) + 1
    if small.size:
        is_small = np.zeros(n + 1, bool)
        is_small[small] = True
        K[is_small[lab]] = 0
        del is_small
    print(f"  islands removed: {small.size} ({int(sizes[small - 1].sum()) if small.size else 0} cells)", flush=True)


# ---------------------------------------------------------------- greedy meshing (tide_shell_titan と同じ)

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
    del pad, occ
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
        a = K[sl]
        return a.size > 0 and not np.all(a != 0)
    return {
        "west": free((slice(max(x0 - 1, 0), x0), slice(y0, y1), slice(z0, z1))) or x0 == 0,
        "east": free((slice(x1, x1 + 1), slice(y0, y1), slice(z0, z1))) or x1 == NX,
        "down": free((slice(x0, x1), slice(max(y0 - 1, 0), y0), slice(z0, z1))),   # y=0 の下 (地面) は描かない
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


# ---------------------------------------------------------------- テクスチャ (2 px / 単位)

def pick(tones, idx):
    return np.array(tones, float)[idx]


def mat_base(mat, A2, B2, part, fname, Yw):
    h, w = A2.shape
    n = nzv(A2, B2)
    gm = np.zeros((h, w), bool)
    name = MAT_NAME[mat]
    if name in TONES:
        idx = (nzv(A2 // 2, B2 // 2) * len(TONES[name])).astype(int) % len(TONES[name])
        base = pick(TONES[name], idx)
    else:
        base = np.zeros((h, w, 3), float)
    if name == "basalt":
        base[(n > 0.93)] *= 0.8
        if fname == "up":
            base[n < 0.10] = (150, 146, 140)               # 灰が積もった粒
    elif name == "sbasalt":
        base[(A2 + 2 * B2) % 5 == 0] *= 0.9
        if fname == "up":
            base[n < 0.08] = (160, 156, 150)
    elif name in ("belly", "ash", "cinder"):
        base[n > 0.9] *= 0.9
    elif name == "slag":
        base[n > 0.85] = (74, 52, 42)
        base[n < 0.05] = (150, 84, 50)
    elif name == "column":
        base[A2 % 6 == 0] *= 0.7                           # 柱の縦の稜線
        base[B2 % 10 == 0] *= 0.8
    elif name == "brick":
        row = np.floor_divide(B2, 3)
        off = (row % 2) * 3
        m = ((A2 + off) % 6 == 0) | (B2 % 3 == 0)
        base[m] = (62, 50, 48)
    elif name == "bronze":
        base[n > 0.9] = (92, 132, 104)                     # 緑青
        base[B2 % 4 == 0] *= 0.85
    elif name in ("lava", "seam", "ember", "molten"):
        pal = {"lava": [(244, 120, 32), (255, 168, 52), (255, 214, 96)],
               "seam": [(232, 92, 28), (250, 140, 40), (255, 196, 80)],
               "ember": [(255, 132, 36), (226, 78, 22), (255, 176, 64)],
               "molten": [(255, 222, 120), (255, 200, 90), (255, 238, 160)]}[name]
        idx = (nzv(A2 // 2 + 7, B2 // 2 + 3) * 3).astype(int) % 3
        base = pick(pal, idx)
        if name == "lava":
            base[(A2 + B2) % 7 == 0] = (190, 60, 20)       # 冷えた皮の筋
        gm[:] = True
    elif name == "eye":
        base[:] = (255, 210, 90)
        gm[:] = True
        base[:, w // 2: w // 2 + 1] = (40, 18, 10)         # 縦の瞳
        gm[:, w // 2: w // 2 + 1] = False
    elif name == "dark":
        base[:] = (22, 18, 20)
    elif name == "teeth":
        base[:] = (222, 214, 198)
        base[B2 % 3 == 0] = (196, 186, 170)
    elif name == "claw":
        base[n > 0.8] = (78, 74, 80)
    elif name == "slagdark":
        base[:] = (40, 30, 28)
    elif name == "crust":
        g = (np.abs(np.sin(A2 * 0.9 + np.sin(B2 * 0.7) * 2.0)) < 0.18)
        base[g] = (250, 130, 40)
        gm |= g
    elif name == "gland":
        g = (nzv(A2 // 2 + 40, B2 // 2) < 0.12) & (A2 % 2 == 0) & (B2 % 2 == 0)
        base[g] = (255, 150, 50)                           # 耳下腺の孔が熾火で光る
        gm |= g
    elif name in ("smoke", "smoke2", "steam"):
        if fname == "down":
            base *= 0.7
        base[n > 0.88] *= 1.08
    if name in ("basalt", "sbasalt", "slag", "brick", "column"):
        # 地面に近いほど煤で暗い
        t = np.clip((6.0 - Yw) / 6.0, 0, 1)[..., None]
        base = base * (1 - 0.35 * t)
    return base, gm


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
    base, gm = mat_base(mat, A2, B2, part, fname, P[..., 1])
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


PIVOTS = {"body": (0, 30, 0), "ridge": (0, 60, -20), "head": (0, 16, -136), "jaw": (0, ML, ZH),
          "leg_fl": (-44, 24, -76), "leg_fr": (44, 24, -76), "leg_rl": (-42, 24, 84), "leg_rr": (42, 24, 84),
          "tail": (0, 24, 108), "smoke": (0, 130, -40)}


def make_geo(boxes):
    cubes = []
    for (k, x0, x1, y0, y1, z0, z1) in boxes:
        k, x0, x1, y0, y1, z0, z1 = (int(v) for v in (k, x0, x1, y0, y1, z0, z1))
        ex = exposed(x0, x1, y0, y1, z0, z1)
        a, b, d = x1 - x0, y1 - y0, z1 - z0
        o = (X0 + x0 / CELL, Y0 + y0 / CELL, Z0 + z0 / CELL)
        s = (a / CELL, b / CELL, d / CELL)
        fp = {"up": (a, d), "down": (a, d), "north": (a, b), "south": (a, b), "east": (d, b), "west": (d, b)}
        cubes.append(dict(key=k, o=o, s=s, exp=ex, fp=fp, part=k // 32, mat=k % 32, cid=len(cubes)))
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
    tex[0, 0] = (*TONES["basalt"][0], 255)
    bones_cubes = {p: [] for p in PART}
    kept = []
    for c in cubes:
        uv = {}
        for fname, (w, h) in c["fp"].items():
            if not c["exp"][fname]:
                continue
            u, v = pos[(c["cid"], fname)]
            uv[fname] = {"uv": [u, v], "uv_size": [w, h]}
            P = world_grid(c["o"], c["s"], fname, w, h)
            paint_face(tex, glow, u, v, w, h, c["key"], fname, P)
        if not uv:
            continue
        kept.append(c)
        bones_cubes[PART_NAME[c["part"]]].append(
            {"origin": [round(q, 3) for q in c["o"]], "size": [round(q, 3) for q in c["s"]], "uv": uv})
    bones = []
    for name in PART:
        b = {"name": name, "pivot": list(PIVOTS[name])}
        if name != "body":
            b["parent"] = "head" if name == "jaw" else "body"
        b["cubes"] = bones_cubes[name]
        bones.append(b)
    geo = {"format_version": "1.12.0", "minecraft:geometry": [{
        "description": {"identifier": "geometry.ember_titan", "texture_width": S, "texture_height": S,
                        "visible_bounds_width": 480, "visible_bounds_height": 200,
                        "visible_bounds_offset": [0, 60, 0]},
        "bones": bones}]}
    return geo, tex, glow, kept


RS = 640
FIT = 480


def frame(im, bg):
    diff = np.abs(np.asarray(im, np.int16) - np.asarray(bg, np.int16)).max(-1) > 10
    ys, xs = np.nonzero(diff)
    if xs.size == 0:
        return im
    x0, x1 = max(xs.min() - 8, 0), min(xs.max() + 9, im.width)
    y0, y1 = max(ys.min() - 8, 0), min(ys.max() + 9, im.height)
    c = ImageEnhance.Brightness(im.crop((x0, y0, x1, y1))).enhance(1.2)
    k = FIT / max(c.size)
    return c.resize((max(1, round(c.width * k)), max(1, round(c.height * k))), Image.LANCZOS)


def render_all(faces):
    bg = render([], size=(RS, RS), margin=0.02)
    close = [f for f in faces if f.origin[1] >= 40 and abs(f.origin[0]) <= 70 and -120 <= f.origin[2] <= 120]
    # 頭は -Z。yaw 180 付近で前から見る
    spec = {"side": (faces, 90, 0), "top": (faces, 145, 35), "front": (faces, 160, 8),
            "back_close": (close, 140, 32)}
    out = {}
    for name, (fs, yaw, pitch) in spec.items():
        out[name] = frame(render(fs, yaw=yaw, pitch=pitch, size=(RS, RS), margin=0.02), bg)
    far_side = render(faces, yaw=90, pitch=0, size=(140, 140), margin=0.04)
    far_top = render(faces, yaw=145, pitch=35, size=(140, 140), margin=0.04)
    far = Image.new("RGB", (290, 140), (18, 18, 26))
    far.paste(far_side, (0, 0))
    far.paste(far_top, (150, 0))
    out["far"] = far
    return out


def main():
    stats = {}
    if (OUT / "stats.json").exists():
        stats = json.loads((OUT / "stats.json").read_text(encoding="utf-8"))
    kids = sys.argv[1:] or ["a", "b", "c"]
    for kid in kids:
        build(kid)
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
        imgs = render_all(faces)
        for name, im in imgs.items():
            im.save(d / f"{name}.png")
        xs = [c["o"][0] for c in cubes] + [c["o"][0] + c["s"][0] for c in cubes]
        ys = [c["o"][1] for c in cubes] + [c["o"][1] + c["s"][1] for c in cubes]
        zs = [c["o"][2] for c in cubes] + [c["o"][2] + c["s"][2] for c in cubes]
        parts = {}
        for c in cubes:
            parts[PART_NAME[c["part"]]] = parts.get(PART_NAME[c["part"]], 0) + 1
        stats[kid] = dict(boxes=len(cubes), buried=buried + (len(kept) - len(cubes)), overlaps=over,
                          vol_ok=vol_ok, tex=tex.shape[0], glow_px=glow_px,
                          bounds=dict(x=(min(xs), max(xs)), y=(min(ys), max(ys)), z=(min(zs), max(zs))),
                          parts=parts, faces=len(faces))
        print(kid, json.dumps(stats[kid], ensure_ascii=False), flush=True)
        OUT.mkdir(parents=True, exist_ok=True)
        (OUT / "stats.json").write_text(json.dumps(stats, ensure_ascii=False, indent=1), encoding="utf-8")

    if not all((OUT / k / "side.png").exists() for k in "abc"):
        return
    names = ("side", "top", "front", "back_close", "far")
    cols = [(lab, [Image.open(OUT / k / f"{n}.png").convert("RGB") for n in names])
            for lab, k in (("A 火山の背", "a"), ("B 炉の都", "b"), ("C 割れた背", "c"))]
    rows = ["側面", "3/4 上から", "正面", "背の近景", "遠景 (140px)"]
    sht = sheet("余燼の巨神 (Ember titan) ラフ 3案", cols,
                "行: " + " / ".join(rows) + "。火蜥蜴の体は共通、背の上だけが違う。光る所は glowmask。")
    SHEET.parent.mkdir(parents=True, exist_ok=True)
    sht.save(SHEET)


if __name__ == "__main__":
    main()
