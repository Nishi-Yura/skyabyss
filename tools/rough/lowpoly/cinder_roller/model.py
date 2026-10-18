"""燠虫 (cinder_roller) の低ポリ メッシュ 3 案 (A 玄武の層鎧 / B 熔皮の棘玉 / C 灰炭の団子)。

座標は geo (cinder_roller.geo.json) と同じ: 1 単位 = 1/16 ブロック、y が上、頭が +Z (root の yaw 180 で前が -Z になる)。
骨の名前・階層・pivot・休めの回転は geo から読む (37 本)。頂点は骨ごとの別パーツで絶対座標。

丸まりの考え方: 節 k は pivot (0,1.5,z_k) まわりに 40 度ずつ回って玉の中心 (0,6,0) に集まる。
そこで各節の甲は「x 軸まわりの回転体」(玉の経線の板) として作る。甲の外側の層は φ=-23..+1 度、
頭側の縁から段 (riser) を落として、次の節の尾の縁の下へもぐる傾いた床 (floor / 溶岩の継ぎ目) を φ=+1..+17 度に置く。
休めの姿勢では隣の節の甲が床の上にかぶさり (交わらない)、玉では 40 度ごとに隙間なく一周する。
"""
from __future__ import annotations

import json
import math
import sys
from pathlib import Path

import numpy as np

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[3]
sys.path.insert(0, str(HERE.parent))
from lpkit import (Face, _mk, grid, loft, nrm, poly, rot_x, rot_y, rot_z, spike, transform, tri_count, tube)  # noqa

GEO = ROOT / "src/main/resources/assets/skyabyss/geo/entity/cinder_roller.geo.json"


def load_bones():
    g = json.loads(GEO.read_text(encoding="utf-8"))["minecraft:geometry"][0]
    out, rot = {}, {}
    for b in g["bones"]:
        out[b["name"]] = (b.get("parent"), [float(x) for x in b.get("pivot", [0, 0, 0])])
        if "rotation" in b:
            rot[b["name"]] = [float(x) for x in b["rotation"]]
    return out, rot


BONES, REST_ROT = load_bones()
PY = 1.5
ZK = [-9.75 + 2.5 * k for k in range(9)]
R_K = [4.25, 4.75, 5.25, 5.75, 5.75, 5.75, 5.25, 4.75, 4.25]     # 背の高さ (pivot から)
W_K = [3.5, 4.5, 5.0, 5.5, 5.5, 5.5, 5.0, 4.5, 3.5]               # 半幅
FLOOR_RISE = 0.2
PHI_T, PHI_H, PHI_R = -23.0, 1.0, 17.0                              # 尾の縁 / 頭側の縁 / 床の終わり (度)


def V(*a):
    return np.array(a, float)


def P(k, x, r, phi):
    a = math.radians(phi)
    return V(x, PY + r * math.cos(a), ZK[k] + r * math.sin(a))


# ---------------------------------------------------------------------- スタイルごとの形
STY = {
    # xs: 列の x の割合、rho: その列の半径の割合 (端は r_end)、phis/m: 側面 (φ) の行と半径の倍率
    "a": dict(xs=[-1, -0.66, 0, 0.66, 1], rho=[0, 0.90, 1.0, 0.90, 0], phis=[PHI_T, -10, PHI_H], m=[0.90, 1.0, 0.93],
              dR=0.0, dW=0.0, drop=1.05, r_end=2.3, dl=[16, 6, 0, 6, 16]),
    "b": dict(xs=[-1, -0.55, 0, 0.55, 1], rho=[0, 0.80, 1.0, 0.80, 0], phis=[PHI_T, -9, PHI_H], m=[0.88, 1.0, 0.90],
              dR=0.55, dW=-0.1, drop=1.15, r_end=2.1, dl=[16, 6, 0, 6, 16]),
    "c": dict(xs=[-1, -0.56, 0, 0.56, 1], rho=[0, 0.97, 1.0, 0.97, 0], phis=[PHI_T, -9, PHI_H], m=[0.92, 1.0, 0.95],
              dR=0.35, dW=0.35, drop=0.95, r_end=2.4, dl=[16, 6, 0, 6, 16]),
}


def _mm(S, i, nc, j):
    """列 i の行 j の半径の倍率 (端の列は弱める)"""
    m = S["m"][j]
    return m if 0 < i < nc - 1 else (m * 0.93 + 0.07)


def _col_r(k, s, i, j=1):
    S = STY[s]
    R = R_K[k] + S["dR"]
    nc = len(S["xs"])
    return max(S["r_end"], R * S["rho"][i] * _mm(S, i, nc, j) if 0 < i < nc - 1 else S["r_end"])


def dl_of(k, s, i):
    """列 i の尾の縁を広げる角度 (度)。休めで前の節の頭側の縁 (z = r sin 1°) を越えない範囲。"""
    S = STY[s]
    cap = S["dl"][i]
    if cap == 0:
        return 0.0
    if k == 0:
        return 0.0
    r = _col_r(k, s, i, 0)
    rp = _col_r(k - 1, s, i, len(S["phis"]) - 1)
    q = (2.5 - 0.12 - rp * math.sin(math.radians(1.0))) / r
    ang = math.degrees(math.asin(min(1.0, q))) - 23.0
    return max(0.0, min(cap, ang))


def shell(k, s):
    """節 k の甲 (外の層 + 段 + 床)。床は頭側の隣の節の尾の縁の半径の少し下まで上がる。"""
    S = STY[s]
    W = W_K[k] + S["dW"]
    xs = [W * f for f in S["xs"]]
    nc = len(xs)
    bone = f"seg{k}"
    c = V(0, PY, ZK[k])
    rows = []
    for j, phi in enumerate(S["phis"]):
        rows.append([P(k, xs[i], _col_r(k, s, i, j), phi - (dl_of(k, s, i) if j == 0 else 0)) for i in range(nc)])
    F = grid(bone, "plate", rows, c)
    top = rows[-1]
    rlow = []
    for i in range(nc):
        rt = float(np.linalg.norm(top[i][1:] - c[1:]))
        lim = 1e9 if k == 8 else 0.92 * _col_r(k + 1, s, i, 0) - 0.95
        rlow.append(max(1.0, min(rt - S["drop"], lim)))
    fx = min(1.0, (W_K[k + 1] + STY[s]["dW"] - 0.15) / W) if k < 8 else 1.0
    low = [P(k, xs[i] * fx, rlow[i], PHI_H) for i in range(nc)]
    riser = grid(bone, "riser", [top, low], c + V(0, 0, -50.0))
    for f in riser:                      # 面は頭の側 (+z) へ向ける
        if f.normal[2] < 0:
            f.verts = f.verts[::-1].copy()
            f.normal = -f.normal
    F += riser
    if k < 8:
        low2 = low
        end = [P(k, xs[i] * fx, rlow[i] + FLOOR_RISE, PHI_R) for i in range(nc)]
        F += grid(bone, "floor", [low2, end], c)
    return F, xs, rows


def belly(k, s):
    W = W_K[k] + STY[s]["dW"]
    x = W * 0.78
    z0, z1 = ZK[k] - 1.15, ZK[k] + 1.15
    y = PY + 1.0
    f = _mk([V(-x, y, z0), V(x, y, z0), V(x, y, z1), V(-x, y, z1)], f"seg{k}", "belly", V(0, y + 5, ZK[k]))
    return [f] if f else []


def wings(k, s):
    """側面の三角の垂れ (pleura): 甲の縁の下へ尖る三角。玉では極の蓋の扇になる (x = ±W の平面)。"""
    S = STY[s]
    W = W_K[k] + S["dW"]
    out = []
    dl = dl_of(k, s, 0)
    for sg in (-1, 1):
        x = sg * W
        a0 = P(k, x, S["r_end"], PHI_T - dl)
        a1 = P(k, x, S["r_end"], PHI_H)
        ap = V(x, PY + 0.95, ZK[k] + (a0[2] - ZK[k] + a1[2] - ZK[k]) / 2)
        f = _mk([a0, a1, ap], f"seg{k}", "wing", V(0, PY + 1.5, ZK[k]) * 1.0 + V(-sg * 0.0, 0, 0) - V(sg * -5, 0, 0) * 0)
        if f:
            if f.normal[0] * sg < 0:
                f.verts = f.verts[::-1].copy()
                f.normal = -f.normal
            out.append(f)
    return out


def build_tail(F, s):
    """節 0 の尾: 甲の尾の縁から下へ巻き込む (玉では内側に収まる)。"""
    S = STY[s]
    W = W_K[0] + S["dW"]
    xs = [W * f for f in S["xs"]]
    nc = len(xs)
    c = V(0, PY, ZK[0])
    row0 = [P(0, xs[i], _col_r(0, s, i, 0), PHI_T - S["dl"][i]) for i in range(nc)]
    row1 = [P(0, xs[i] * 0.86, max(1.5, _col_r(0, s, i, 0) * 0.62), -38.0 - S["dl"][i]) for i in range(nc)]
    return  # 尾の巻き込みは頭と交差するので作らない (玉の底で頭が見える)


def build_segments(s):
    F = []
    for k in range(9):
        sh, xs, rows = shell(k, s)
        if k == 8:
            sh = [f for f in sh if f.mat == "plate"]      # 頭へ続くので段と床は作らない
        F += sh
        F += belly(k, s)
        F += wings(k, s)
    return F


def head_spec(s):
    """頭: 節 8 の頭側の縁の列から、急に下がる額と鼻づらへ。行 = (z, 上面の y, 半幅の割合)"""
    if s == "a":
        return [(0.9, PY + 2.7, 0.84), (2.0, PY + 1.8, 0.58), (2.9, PY + 1.1, 0.46)]
    if s == "b":
        return [(0.9, PY + 2.6, 0.78), (1.9, PY + 1.5, 0.46), (2.8, PY + 1.0, 0.32)]
    return [(0.9, PY + 2.7, 0.90), (1.9, PY + 1.8, 0.70), (2.6, PY + 1.15, 0.56)]


def build_head(F, s):
    k = 8
    S = STY[s]
    W = W_K[k] + S["dW"]
    xs = [W * f for f in S["xs"]]
    nc = len(xs)
    last = [P(k, xs[i], _col_r(k, s, i, len(S["phis"]) - 1), PHI_H) for i in range(nc)]
    rows = [last]
    for z, yt, hwf in head_spec(s):
        row = []
        for i, f in enumerate(S["xs"]):
            side = 1.0 - abs(f) ** 2
            y = max(PY + 1.0, yt - (1 - side) * (1.1 if s != "c" else 0.8))
            row.append(V(W * hwf * f, y, ZK[k] + z))
        rows.append(row)
    F += grid("seg8", "head", rows, V(0, PY, ZK[k] + 0.5))
    # 目: 頭の傾いた面に貼る光る小面 (0.12 浮かせる)。2 行目と 3 行目の間の斜面
    r1, r2 = rows[1], rows[2]
    for sg in (-1, 1):
        i = 3 if sg > 0 else 1
        a0, a1 = r1[i], r2[i]
        ctr = (a0 + a1) / 2
        v = nrm(a1 - a0)
        u = V(1, 0, 0)
        nrmv = nrm(np.cross(u, v))
        if nrmv[1] < 0:
            nrmv = -nrmv
        ctr = ctr + nrmv * 0.22
        if s == "a":
            w, h = 0.9, 0.55
        elif s == "b":
            w, h = 0.8, 0.45
        else:
            w, h = 0.7, 0.7
        F += poly("seg8", "eye", [ctr - u * w - v * h, ctr + u * w - v * h, ctr + u * w + v * h, ctr - u * w + v * h],
                  ctr - nrmv * 3)
    return rows


# ---------------------------------------------------------------------- 脚・触角・尾・膜
def build_legs(F, s):
    for kk in range(1, 8):
        z = ZK[kk]
        W = W_K[kk] + STY[s]["dW"]
        for side, sg in (("l", 1), ("r", -1)):
            b = f"leg{kk}_{side}"
            hip = V(sg * 1.5, 2.5, z)
            reach = min(W - 0.55, 4.9 if s != "c" else 4.4)
            foot = V(sg * reach, 0.2 if s != "c" else 0.5, z + 0.1)
            knee = V(sg * (reach - 1.0), 1.5, z)
            ra, rb = {"a": ([0.65, 0.55, 0.0], [0.55, 0.5, 0.0]), "b": ([0.5, 0.4, 0.0], [0.45, 0.35, 0.0]),
                      "c": ([0.8, 0.65, 0.0], [0.7, 0.6, 0.0])}[s]
            F += tube(b, "leg", [hip, knee, foot], ra=ra, rb=rb, sides=3, hint=(0, 0, 1))


def build_antennae(F, s):
    for sg, nm in ((-1, "antenna_r"), (1, "antenna_l")):
        p0 = V(sg * 1.25, 2.9, 11.25)
        if s == "a":
            path = [p0, V(sg * 2.4, 3.3, 13.2), V(sg * 3.7, 3.5, 15.0)]
            ra, rb = [0.55, 0.45, 0.0], [0.45, 0.4, 0.0]
        elif s == "b":
            path = [p0, V(sg * 2.6, 4.2, 13.4), V(sg * 4.6, 3.2, 15.2)]
            ra, rb = [0.5, 0.4, 0.0], [0.4, 0.3, 0.0]
        else:
            path = [p0, V(sg * 2.2, 3.9, 13.4), V(sg * 3.0, 5.4, 14.9)]
            ra, rb = [0.6, 0.5, 0.0], [0.55, 0.45, 0.0]
        F += tube(nm, "ant", path, ra=ra, rb=rb, sides=3, hint=(0, 1, 0))


def build_uropods(F, s):
    z0 = ZK[0] - 1.6
    for sg in (-1, 1):
        if s == "a":
            path = [V(sg * 0.9, 2.1, z0), V(sg * 1.7, 1.9, z0 - 2.4), V(sg * 2.0, 1.8, z0 - 4.1)]
            ra, rb = [0.9, 0.7, 0.0], [0.5, 0.4, 0.0]
        elif s == "b":
            path = [V(sg * 0.8, 2.2, z0), V(sg * 1.8, 2.4, z0 - 2.8), V(sg * 3.0, 3.0, z0 - 5.0)]
            ra, rb = [0.8, 0.55, 0.0], [0.5, 0.4, 0.0]
        else:
            path = [V(sg * 0.9, 2.0, z0), V(sg * 1.6, 1.8, z0 - 1.9), V(sg * 1.9, 1.7, z0 - 2.9)]
            ra, rb = [1.1, 0.8, 0.0], [0.8, 0.6, 0.0]
        F += tube("uropods", "tail", path, ra=ra, rb=rb, sides=3, hint=(0, 1, 0))


def build_mem(F, s):
    """節の間の膜 (mem0..mem7): 腹側の溶岩色の帯。休めでは甲の下、玉では内側。"""
    for k in range(8):
        W = W_K[k] + STY[s]["dW"]
        W2 = W_K[k + 1] + STY[s]["dW"]
        zz = ZK[k] + 1.25
        x, x2 = W * 0.7, W2 * 0.7
        y = PY + 1.05
        f = _mk([V(-x, y, zz - 0.3), V(x, y, zz - 0.3), V(x2, y, zz + 0.3), V(-x2, y, zz + 0.3)], f"mem{k}", "seam",
                V(0, y + 5, zz))
        if f:
            F.append(f)


# ---------------------------------------------------------------------- スタイル固有の飾り
def build_extras(F, s):
    if s == "b":
        for k in range(9):
            r0 = R_K[k] + STY[s]["dR"]
            base = P(k, 0, r0 * 0.99, -9)
            d = nrm(V(0, math.cos(math.radians(-9)), math.sin(math.radians(-9))) + V(0, 0, -0.35))
            L = 2.6 if 1 <= k <= 7 else 1.8
            F += spike(f"seg{k}", "spike", base - d * 0.2, d, L, 0.95 if 1 <= k <= 7 else 0.7, sides=4, rings=1,
                       hint=(1, 0, 0))
    if s == "c":
        for k in range(9):
            r0 = R_K[k] + STY[s]["dR"]
            a = P(k, 0, r0 * 0.99, -18)
            b = P(k, 0, r0 * 1.0 + 3.0, -13)
            c_ = P(k, 0, r0 * 0.97, 1)
            F += poly(f"seg{k}", "fin", [c_, b, a], V(0, 0, 0))


def build(s):
    F = build_segments(s)
    build_tail(F, s)
    rows = build_head(F, s)
    build_legs(F, s)
    build_antennae(F, s)
    build_uropods(F, s)
    build_mem(F, s)
    build_extras(F, s)
    return F, {"head_rows": rows}
