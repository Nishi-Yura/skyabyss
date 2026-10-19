#!/usr/bin/env python3
"""全モデルの「閉じ具合」の監査 (水密性・関節の隙間・裏面の透け)。

人間のモデラーならしない失敗: 面が足りなくて中が見える / 部品の付け根が離れる / 法線が逆。
これを機械で数える。対象:
  - assets/skyabyss/lowpoly/**.json   低ポリの三角形メッシュ (モブ・NPC・巨神・心臓・武器・防具・装身具)。骨ごとに調べる
  - assets/skyabyss/models/block/**.json  ブロックモデル (elements の直方体)

骨ごとの項目 (メッシュ):
  open     開いた辺 (一枚の三角形にしか使われていない辺。頂点は 0.01 単位で溶接して数える)
  nonman   三本以上の面が集まる辺 (多様体でない)
  wind     向きの食い違い (同じ向きの有向辺が二度出る = 隣の面と裏表が逆)
  degen    つぶれた三角形 (面積ほぼ 0)
  inv      閉じた塊のうち体積の符号が負 (外側が内向き = 法線が逆)
  see      裏面透け: 外から正射影の光線 (14 方向) を飛ばし、最初に当たった面が「裏から見た面」の画素数
           (手前の面が抜けていて中が見えている所。骨ごとに数える)
関節 (親子の骨):
  detach       子の面が親の体積にまったく埋まっていない (休めの姿勢、またはアニメの最大回転で親から離れる)
  shallow      子の面のうち親に埋まっている点が 4% 未満 / 埋まりが 0.5 単位より浅い (回転で隙間ができやすい)
  (pivot-out   子の回転軸が親の体積の外。情報として出すだけで、失敗にはしない。関節の玉 (mesh_close.joint_ball) は pivot が中心なので回しても形が変わらない)
アニメの回転幅は animations/**.animation.json から読む (数値のキーフレームと molang の式を標本化)。

使い方 (リポジトリ直下で):
  python tools/mesh_audit.py                  # 検査して docs/review/mesh_audit_report.md を書く
  python tools/mesh_audit.py --check          # 開いた辺・向きの食い違い・逆法線・裏面透けがあれば終了コード 1 (ゲート)
  python tools/mesh_audit.py --check --joints # 関節 (detach) も失敗にする
  python tools/mesh_audit.py --selftest       # この道具自身の自己テスト (箱が閉じている・穴を数えられる)
  python tools/mesh_audit.py <名前の一部>...    # 名前で絞る
"""
from __future__ import annotations

import collections
import glob
import json
import math
import os
import re
import sys

import numpy as np
from scipy.spatial import cKDTree

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets", "skyabyss")
LOWPOLY = os.path.join(ASSETS, "lowpoly")
BLOCKS = os.path.join(ASSETS, "models", "block")
ANIMS = os.path.join(ASSETS, "animations")
REPORT = os.path.join(ROOT, "docs", "review", "mesh_audit_report.md")

WELD = 0.01          # 頂点の溶接距離 (1/16 ブロック単位)
DEGEN_AREA = 1e-4
GEO_LIMIT = 0.002    # キューブ geo の光線 2000 本のうち裏面が先に当たる割合 (0.2% = 4 本) を超えたら失敗。乱数の光線の取りこぼしを許す
SEE_LIMIT = 0.002    # 裏面透けの画素が全体の 0.2% を超えたらゲートの失敗 (重なった面の z 競合の数画素は許す)


# ----------------------------------------------------------------- 読み込み

def rel(path):
    return os.path.relpath(path, LOWPOLY).replace("\\", "/")


def load_json(path):
    with open(path, encoding="utf-8") as f:
        return json.load(f)


def mesh_files():
    return sorted(glob.glob(os.path.join(LOWPOLY, "**", "*.json"), recursive=True))


class Bone:
    def __init__(self, name, data):
        self.name = name
        self.parent = data.get("parent")
        if self.parent in ("None", ""):
            self.parent = None
        self.pivot = np.array(data.get("pivot", [0, 0, 0]), float)
        self.V = np.array(data.get("vertices", []), float).reshape(-1, 3)
        self.T = np.array(data.get("triangles", []), int).reshape(-1, 3)


def load_mesh(path):
    d = load_json(path)
    if "bones" not in d:
        return None
    return {n: Bone(n, b) for n, b in d["bones"].items()}


# ----------------------------------------------------------------- 溶接と辺の解析

def weld_ids(V, tol=WELD):
    """tol 以内の頂点を同じ番号にまとめる。"""
    n = len(V)
    if n == 0:
        return np.zeros(0, int)
    par = list(range(n))

    def find(x):
        while par[x] != x:
            par[x] = par[par[x]]
            x = par[x]
        return x
    for a, b in cKDTree(V).query_pairs(tol):
        ra, rb = find(a), find(b)
        if ra != rb:
            par[ra] = rb
    return np.array([find(i) for i in range(n)])


def analyze(V, T):
    """一つの骨のメッシュの解析。戻り値は dict。"""
    r = dict(tris=len(T), open=0, nonman=0, wind=0, degen=0, inv=0, comps=0, loops=[], open_edges=[])
    if len(T) == 0:
        return r
    c = weld_ids(V)
    tri = c[T]
    area = np.linalg.norm(np.cross(V[T[:, 1]] - V[T[:, 0]], V[T[:, 2]] - V[T[:, 0]]), axis=1) / 2
    bad = (tri[:, 0] == tri[:, 1]) | (tri[:, 1] == tri[:, 2]) | (tri[:, 2] == tri[:, 0]) | (area < DEGEN_AREA)
    r["degen"] = int(bad.sum())
    keep = ~bad
    directed = collections.Counter()
    for a, b, d in tri[keep]:
        directed[(a, b)] += 1
        directed[(b, d)] += 1
        directed[(d, a)] += 1
    und = collections.Counter()
    for (a, b), k in directed.items():
        und[(min(a, b), max(a, b))] += k
    r["nonman"] = sum(1 for k in und.values() if k > 2)
    r["wind"] = sum(1 for (a, b), k in directed.items() if k > 1 and und[(min(a, b), max(a, b))] == 2)
    openset = {e for e, k in und.items() if k == 1}
    r["open"] = len(openset)
    r["open_edges"] = sorted(openset)
    # 開いた縁の輪 (向き付き辺をたどる)
    nxt = collections.defaultdict(list)
    for (a, b), k in directed.items():
        if und[(min(a, b), max(a, b))] == 1:
            nxt[a].append(b)
    seen = set()
    loops = []
    for s in list(nxt):
        for t0 in list(nxt[s]):
            if (s, t0) in seen:
                continue
            L = [s]
            cur = t0
            seen.add((s, t0))
            while cur != s and len(L) < 20000:
                L.append(cur)
                n2 = [z for z in nxt[cur] if (cur, z) not in seen]
                if not n2:
                    break
                seen.add((cur, n2[0]))
                cur = n2[0]
            loops.append(L)
    r["loops"] = [len(L) for L in loops]
    # 塊 (連結成分) と体積の符号
    par = {}

    def find(x):
        par.setdefault(x, x)
        while par[x] != x:
            par[x] = par[par[x]]
            x = par[x]
        return x
    for a, b, d in tri[keep]:
        for p, q in ((a, b), (b, d)):
            rp, rq = find(p), find(q)
            if rp != rq:
                par[rp] = rq
    comp_of = np.array([find(t[0]) for t in tri])
    r["comps"] = len(set(comp_of[keep]))
    comp_open = collections.Counter()
    for (a, b) in openset:
        comp_open[find(a)] += 1
    o = V[T[:, 0]]
    vol = np.einsum("ij,ij->i", o, np.cross(V[T[:, 1]], V[T[:, 2]])) / 6.0
    # 開いた塊は、輪を重心の扇で仮に塞いだ体積で符号を見る
    pos = {}
    for i, cid in enumerate(c):
        pos.setdefault(cid, V[i])
    capvol = collections.Counter()
    for L in loops:
        if len(L) < 3:
            continue
        P = np.array([pos[i] for i in L])
        apex = P.mean(0)
        v = 0.0
        for k in range(len(L)):
            a, b = P[k], P[(k + 1) % len(L)]
            v += np.dot(b, np.cross(a, apex)) / 6.0
        capvol[find(L[0])] += v
    for comp in set(comp_of[keep]):
        m = keep & (comp_of == comp)
        if vol[m].sum() + capvol.get(find(comp), 0.0) < -1e-6:
            r["inv"] += 1
    return r


# ----------------------------------------------------------------- 透け (裏面が最初に見える)

DIRS = None


def _dirs():
    global DIRS
    if DIRS is None:
        ds = []
        for i in range(3):
            for s in (1, -1):
                v = np.zeros(3)
                v[i] = s
                ds.append(v)
        for sx in (1, -1):
            for sy in (1, -1):
                for sz in (1, -1):
                    ds.append(np.array([sx, sy, sz], float) / math.sqrt(3))
        DIRS = ds
    return DIRS


def see_through(bones, max_pix=18000, detail=None):
    """骨ごとの裏面透けの画素数 (全方向の合計)。bones は {名前: Bone}。"""
    Vs, Ts, owner = [], [], []
    off = 0
    names = list(bones)
    for bi, n in enumerate(names):
        b = bones[n]
        if len(b.T) == 0:
            continue
        Vs.append(b.V)
        Ts.append(b.T + off)
        owner.append(np.full(len(b.T), bi))
        off += len(b.V)
    if not Vs:
        return {}
    V = np.vstack(Vs)
    T = np.vstack(Ts)
    own = np.concatenate(owner)
    lo, hi = V.min(0), V.max(0)
    ext = float((hi - lo).max())
    step = max(0.35, math.sqrt(max(1.0, ((hi - lo)[0] * (hi - lo)[1] + (hi - lo)[1] * (hi - lo)[2] + (hi - lo)[0] * (hi - lo)[2]) / 3) / max_pix))
    P0, P1, P2 = V[T[:, 0]], V[T[:, 1]], V[T[:, 2]]
    N = np.cross(P1 - P0, P2 - P0)
    nl = np.linalg.norm(N, axis=1)
    ok = nl > 1e-9
    N[ok] /= nl[ok][:, None]
    count = collections.Counter()
    ctr = (lo + hi) / 2
    for d in _dirs():
        # 光線の方向 d。平面の基底 (u, w) へ射影
        h = np.array([0, 1, 0.0]) if abs(d[1]) < 0.9 else np.array([1, 0, 0.0])
        u = np.cross(d, h)
        u /= np.linalg.norm(u)
        w = np.cross(d, u)
        pu = (V - ctr) @ u
        pw = (V - ctr) @ w
        pd = (V - ctr) @ d
        umin, umax, wmin, wmax = pu.min(), pu.max(), pw.min(), pw.max()
        W = int((umax - umin) / step) + 2
        H = int((wmax - wmin) / step) + 2
        zf = np.full((H, W), np.inf)
        zb = np.full((H, W), np.inf)
        bb = np.full((H, W), -1)
        bt = np.full((H, W), -1)
        front = (N @ d) < 0  # 光線に向いている面 = 表
        for ti in range(len(T)):
            if not ok[ti]:
                continue
            ia, ib, ic = T[ti]
            ax, ay, bx, by, cx, cy = pu[ia], pw[ia], pu[ib], pw[ib], pu[ic], pw[ic]
            x0 = min(ax, bx, cx)
            x1 = max(ax, bx, cx)
            y0 = min(ay, by, cy)
            y1 = max(ay, by, cy)
            i0 = int((x0 - umin) / step)
            i1 = int((x1 - umin) / step) + 1
            j0 = int((y0 - wmin) / step)
            j1 = int((y1 - wmin) / step) + 1
            gx = umin + (np.arange(i0, i1 + 1) + 0.5) * step
            gy = wmin + (np.arange(j0, j1 + 1) + 0.5) * step
            X, Y = np.meshgrid(gx, gy)
            den = (by - cy) * (ax - cx) + (cx - bx) * (ay - cy)
            if abs(den) < 1e-12:
                continue
            l1 = ((by - cy) * (X - cx) + (cx - bx) * (Y - cy)) / den
            l2 = ((cy - ay) * (X - cx) + (ax - cx) * (Y - cy)) / den
            l3 = 1 - l1 - l2
            e = 1e-6
            inside = (l1 >= -e) & (l2 >= -e) & (l3 >= -e)
            if not inside.any():
                continue
            z = l1 * pd[ia] + l2 * pd[ib] + l3 * pd[ic]
            sl = (slice(j0, j1 + 1), slice(i0, i1 + 1))
            if front[ti]:
                cur = zf[sl]
                zf[sl] = np.where(inside & (z < cur), z, cur)
            else:
                cur = zb[sl]
                upd = inside & (z < cur)
                zb[sl] = np.where(upd, z, cur)
                bb[sl] = np.where(upd, own[ti], bb[sl])
                bt[sl] = np.where(upd, ti, bt[sl])
        # 光線は -d から +d へ進む (pd が小さいほど手前)。裏面が表面より手前なら透けている
        eps = 0.05
        bad = np.isfinite(zb) & (zb < zf - eps)
        if detail is not None:
            for ti_, k_ in zip(*np.unique(bt[bad], return_counts=True)):
                detail[int(ti_)] = detail.get(int(ti_), 0) + int(k_)
        for k in np.unique(bb[bad]):
            count[names[k]] += int((bad & (bb == k)).sum())
        count["__total"] += int((np.isfinite(zf) | np.isfinite(zb)).sum())
    return dict(count)


# ----------------------------------------------------------------- 点が体積の中か (光線の偶奇)

def inside_mask(P, V, T):
    """点 P (m,3) がメッシュ (V,T) の内側か。3 方向の光線の多数決。"""
    if len(T) == 0 or len(P) == 0:
        return np.zeros(len(P), bool)
    A = V[T[:, 0]]
    E1 = V[T[:, 1]] - A
    E2 = V[T[:, 2]] - A
    votes = np.zeros(len(P), int)
    for d in (np.array([0.5773, 0.5801, 0.5726]), np.array([-0.3711, 0.8123, -0.4521]), np.array([0.2214, -0.4419, 0.8693])):
        d = d / np.linalg.norm(d)
        pv = np.cross(d, E2)
        det = np.einsum("ij,ij->i", E1, pv)
        good = np.abs(det) > 1e-12
        inv = np.zeros_like(det)
        inv[good] = 1.0 / det[good]
        res = np.zeros(len(P), int)
        for s in range(0, len(P), 256):
            Q = P[s:s + 256]
            tv = Q[:, None, :] - A[None, :, :]
            u = np.einsum("ijk,jk->ij", tv, pv) * inv[None, :]
            qv = np.cross(tv, E1[None, :, :])
            v = np.einsum("jk,ijk->ij", d[None, :].repeat(len(T), 0), qv) * inv[None, :]
            t = np.einsum("ijk,jk->ij", qv, E2) * inv[None, :]
            hit = good[None, :] & (u >= 0) & (v >= 0) & (u + v <= 1) & (t > 1e-9)
            res[s:s + 256] = hit.sum(1) % 2
        votes += res
    return votes >= 2


def surface_samples(b: Bone):
    if len(b.T) == 0:
        return np.zeros((0, 3))
    P0, P1, P2 = b.V[b.T[:, 0]], b.V[b.T[:, 1]], b.V[b.T[:, 2]]
    return np.vstack([(P0 + P1 + P2) / 3, (P0 + P1) / 2, (P1 + P2) / 2, (P2 + P0) / 2, b.V])


def depth_in(P, V, T, samples):
    """内側の点 P から親の表面までの距離 (標本点の最近傍で近似)。"""
    if len(P) == 0:
        return np.zeros(0)
    return cKDTree(samples).query(P)[0]


# ----------------------------------------------------------------- アニメの回転幅

def molang_range(expr):
    """molang の式を time 0..8 で標本化して (最小, 最大)。分からない変数は 0。"""
    s = str(expr).lower().replace("math.", "").replace("query.anim_time", "T").replace("query.life_time", "T")
    s = re.sub(r"(query|variable|v|q)\.[a-z_0-9]+", "0", s)
    s = s.replace("^", "**")
    env = {
        "sin": lambda x: math.sin(math.radians(x)), "cos": lambda x: math.cos(math.radians(x)),
        "abs": abs, "min": min, "max": max, "clamp": lambda x, a, b: max(a, min(b, x)), "pi": math.pi,
        "sqrt": math.sqrt, "floor": math.floor, "ceil": math.ceil, "pow": pow, "mod": math.fmod, "round": round,
        "lerp": lambda a, b, t: a + (b - a) * t, "random": lambda a=0, b=1: (a + b) / 2,
    }
    vals = []
    for i in range(80):
        env["T"] = i * 0.1
        try:
            vals.append(float(eval(s, {"__builtins__": {}}, env)))
        except Exception:
            return None
    return min(vals), max(vals)


def _vec_range(vec):
    out = []
    for c in vec:
        if isinstance(c, (int, float)):
            out.append((float(c), float(c)))
        else:
            rg = molang_range(c)
            out.append(rg if rg else (0.0, 0.0))
    return out


def _channel_ranges(ch):
    """rotation チャンネル (配列 / 時刻の辞書) -> [(min,max)]*3。"""
    rs = []
    if isinstance(ch, list):
        rs.append(_vec_range(ch))
    elif isinstance(ch, dict):
        for k, v in ch.items():
            if isinstance(v, dict):
                v = v.get("vector") or v.get("post") or v.get("pre")
            if isinstance(v, list) and len(v) == 3:
                rs.append(_vec_range(v))
    if not rs:
        return [(0.0, 0.0)] * 3
    return [(min(r[i][0] for r in rs), max(r[i][1] for r in rs)) for i in range(3)]


_ANIM_CACHE = {}


def anim_ranges(mesh_name):
    """メッシュ名 -> {骨: [(min,max)*3]}。対応する animation.json が無ければ空。"""
    base = re.sub(r"_(far|mid)$", "", re.sub(r"_p[123]$", "", mesh_name))
    cands = [mesh_name, base, base.replace("_heart", "_heart")]
    out = {}
    for sub in ("entity", "gear", "armor"):
        for cn in cands:
            for p in (os.path.join(ANIMS, sub, cn + ".animation.json"), os.path.join(ANIMS, sub, cn + "_armor.animation.json")):
                if p in _ANIM_CACHE:
                    d = _ANIM_CACHE[p]
                elif os.path.exists(p):
                    d = _ANIM_CACHE[p] = load_json(p)
                else:
                    continue
                for an, a in d.get("animations", {}).items():
                    for bn, bd in a.get("bones", {}).items():
                        if "rotation" not in bd:
                            continue
                        rg = _channel_ranges(bd["rotation"])
                        if bn in out:
                            out[bn] = [(min(out[bn][i][0], rg[i][0]), max(out[bn][i][1], rg[i][1])) for i in range(3)]
                        else:
                            out[bn] = rg
    return out


def euler_matrix(rx, ry, rz):
    """GeckoLib / Bedrock の順 (Z → Y → X の順に回す) の回転行列。度。"""
    ax, ay, az = map(math.radians, (rx, ry, rz))
    cx, sx, cy, sy, cz, sz = math.cos(ax), math.sin(ax), math.cos(ay), math.sin(ay), math.cos(az), math.sin(az)
    Rx = np.array([[1, 0, 0], [0, cx, -sx], [0, sx, cx]])
    Ry = np.array([[cy, 0, sy], [0, 1, 0], [-sy, 0, cy]])
    Rz = np.array([[cz, -sz, 0], [sz, cz, 0], [0, 0, 1]])
    return Rx @ Ry @ Rz


def rotation_corners(rng):
    """回転幅の 3 軸のそれぞれの端 (+ 各軸単独の端) の組。"""
    out = []
    for i in range(3):
        for e in rng[i]:
            v = [0.0, 0.0, 0.0]
            v[i] = e
            out.append(tuple(v))
    for xs in rng[0]:
        for ys in rng[1]:
            for zs in rng[2]:
                out.append((xs, ys, zs))
    out = sorted({o for o in out if any(abs(x) > 1e-6 for x in o)}, key=lambda o: -sum(abs(x) for x in o))
    return out[:10]


# ----------------------------------------------------------------- 関節

def nearest_mesh_ancestor(bones, name):
    p = bones[name].parent
    seen = 0
    while p and p in bones and seen < 50:
        if len(bones[p].T):
            return p
        p = bones[p].parent
        seen += 1
    return None


def joint_check(bones, mesh_name):
    """親子の付け根の検査。戻り値は {子の骨: dict(status, ...)}"""
    rng_all = anim_ranges(mesh_name)
    out = {}
    cache = {}
    for n, b in bones.items():
        if len(b.T) == 0:
            continue
        pn = nearest_mesh_ancestor(bones, n)
        if pn is None:
            continue
        par = bones[pn]
        if pn not in cache:
            cache[pn] = surface_samples(par)
        psamp = cache[pn]
        pivot_in = bool(inside_mask(b.pivot[None, :], par.V, par.T)[0])
        cs = surface_samples(b)
        if len(cs) > 250:
            cs = cs[np.random.RandomState(0).choice(len(cs), 250, replace=False)]
        buried = inside_mask(cs, par.V, par.T)
        frac0 = float(buried.mean())
        depth0 = float(depth_in(cs[buried], par.V, par.T, psamp).max()) if buried.any() else 0.0
        rng = rng_all.get(n)
        worst = None
        if rng:
            for rot in rotation_corners(rng):
                R = euler_matrix(*rot)
                cs2 = (cs - b.pivot) @ R.T + b.pivot
                # 親も回る分は付け根の相対回転ではないので子だけを回す
                f1 = float(inside_mask(cs2, par.V, par.T).mean())
                if worst is None or f1 < worst[0]:
                    worst = (f1, rot)
        status = "ok"
        if frac0 == 0.0 or (worst is not None and worst[0] == 0.0):
            status = "detach"
        elif frac0 < 0.04 or depth0 < 0.5:
            status = "shallow"
        out[n] = dict(parent=pn, status=status, pivot_in=pivot_in, buried=frac0, depth=depth0,
                      moves=bool(rng), worst=worst)
    return out


# ----------------------------------------------------------------- ブロックモデル

FACE_AXIS = {"west": (0, -1), "east": (0, 1), "down": (1, -1), "up": (1, 1), "north": (2, -1), "south": (2, 1)}


def audit_block(path):
    """ブロックモデルの elements。欠けた面は、ほかの部品の中・接面に隠れていればよい (隠れた面は描かないのが普通)。
    外へ向いた面が欠けているのと、どの部品にも・ブロックの外枠にも付いていない宙に浮く部品を数える。"""
    d = load_json(path)
    els = d.get("elements") or []
    res = dict(elements=len(els), missing=0, thin=0, floating=0, notes=[])
    boxes = []
    for e in els:
        fr, to = np.array(e["from"], float), np.array(e["to"], float)
        boxes.append(None if "rotation" in e else (np.minimum(fr, to), np.maximum(fr, to)))
    eps = 1e-4
    for i, e in enumerate(els):
        f = e.get("faces", {})
        bx = boxes[i]
        if bx is None:
            fr_, to_ = np.array(e["from"], float), np.array(e["to"], float)
            if np.any(np.abs(to_ - fr_) < 1e-6):
                # 回した薄い板: 両面 (その軸の二面) があればよい
                ax_ = int(np.argmin(np.abs(to_ - fr_)))
                names_ = [("west", "east"), ("down", "up"), ("north", "south")][ax_]
                if not all(k in f for k in names_):
                    res["thin"] += 1
                    res["notes"].append("element %d (rotated plane): needs both sides" % i)
                continue
            miss = [k for k in FACE_AXIS if k not in f]
            if miss and len(f) < 6:
                res["notes"].append("element %d (rotated): faces missing %s (not checked for cover)" % (i, ",".join(miss)))
                res["missing"] += len(miss)
            continue
        lo, hi = bx
        dims = hi - lo
        flat = [k for k in range(3) if dims[k] < 1e-6]
        if flat:
            ax = flat[0]
            names = [("west", "east"), ("down", "up"), ("north", "south")][ax]
            have = [k for k in names if k in f]
            if len(have) < 2:
                res["thin"] += 1
                res["notes"].append("element %d: zero-thickness plane has only %d side(s)" % (i, len(have)))
            continue
        exposed = []
        for k, (ax, side) in FACE_AXIS.items():
            if k in f:
                continue
            plane = hi[ax] if side > 0 else lo[ax]
            o1, o2 = [t for t in range(3) if t != ax]
            covered = False
            for j, by in enumerate(boxes):
                if j == i or by is None:
                    continue
                if by[0][o1] - eps <= lo[o1] and hi[o1] <= by[1][o1] + eps and by[0][o2] - eps <= lo[o2] and hi[o2] <= by[1][o2] + eps                         and by[0][ax] - eps <= plane <= by[1][ax] + eps:
                    covered = True
                    break
            if not covered:
                exposed.append(k)
        if exposed:
            res["missing"] += len(exposed)
            res["notes"].append("element %d: exposed faces missing %s" % (i, ",".join(exposed)))
    # 部品が宙に浮いていないか: 他の部品に接している、またはブロックの外枠 (0..16) に接している
    for i, bx in enumerate(boxes):
        if bx is None or len(els) < 2:
            continue
        lo, hi = bx
        touch = bool(np.any(lo <= eps) or np.any(hi >= 16 - eps))
        for j, by in enumerate(boxes):
            if touch:
                break
            if i == j or by is None:
                continue
            if np.all(lo <= by[1] + eps) and np.all(by[0] <= hi + eps):
                touch = True
        if not touch and any(b is None for b in boxes):
            continue
        if not touch:
            res["floating"] += 1
            res["notes"].append("element %d floats (touches no other element)" % i)
    return res


# ----------------------------------------------------------------- キューブの geo (低ポリが無い・元に戻す先の GeckoLib モデル)

GEO_DIR = os.path.join(ASSETS, "geo")


def audit_geo(filters=()):
    """キューブの geo.json の裏面透け (tools/fix_geo_face_sides.holes と同じ光線の検査)。戻り値 [(相対パス, 当たり数, 光線数, {骨: 数})]"""
    sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
    import fix_geo_face_sides as fg
    out = []
    for p in sorted(glob.glob(os.path.join(GEO_DIR, "**", "*.geo.json"), recursive=True)):
        n = os.path.relpath(p, GEO_DIR).replace("\\", "/")
        if "_lowpoly" in n:
            continue  # 骨だけの骨組み / 位置合わせの箱
        if filters and not any(f in n for f in filters):
            continue
        try:
            bad, tot, per = fg.holes(p, fg.GECKO)
        except Exception as e:  # noqa: BLE001
            out.append((n, -1, 0, {"error": str(e)}))
            continue
        out.append((n, bad, tot, per))
    return out


# ----------------------------------------------------------------- 実行

def audit_all(filters=(), do_see=True, do_joints=True):
    rows = []
    for p in mesh_files():
        name = os.path.splitext(os.path.basename(p))[0]
        r = rel(p)
        if filters and not any(f in r for f in filters):
            continue
        bones = load_mesh(p)
        if bones is None:
            continue
        per = {n: analyze(b.V, b.T) for n, b in bones.items()}
        see = see_through(bones) if do_see else {}
        pixels = see.pop("__total", 0)
        mesh_base = name
        # 防具・装身具など骨が一つのものは関節なし
        joints = joint_check(bones, mesh_base) if do_joints and len(bones) > 1 else {}
        rows.append(dict(path=r, name=name, bones=per, see=see, pixels=pixels, joints=joints))
    return rows


def score(row):
    s = 0
    for n, a in row["bones"].items():
        s += a["open"] + 3 * a["nonman"] + 3 * a["wind"] + 20 * a["inv"]
    s += sum(row["see"].values()) // 20
    s += sum(5 for j in row["joints"].values() if j["status"] == "detach")
    s += sum(1 for j in row["joints"].values() if j["status"] == "shallow")
    return s


def write_report(rows, blocks, path=REPORT, geos=()):
    ranked = sorted(rows, key=score, reverse=True)
    tot_open = sum(a["open"] for r in rows for a in r["bones"].values())
    tot_see = sum(sum(r["see"].values()) for r in rows)
    lines = ["# メッシュ監査 (tools/mesh_audit.py が生成)", "",
             "水密性・関節の隙間・裏面透けの数え上げ。`python tools/mesh_audit.py` で作り直す。手で編集しない。", "",
             "## 要約", "",
             "- 調べたメッシュ: %d 個 / 開いた辺の合計: **%d** / 裏面透けの画素数の合計: **%d**" % (len(rows), tot_open, tot_see),
             "- 逆法線の塊: %d / 向きの食い違い: %d / 多様体でない辺: %d / つぶれた三角形: %d" % (
                 sum(a["inv"] for r in rows for a in r["bones"].values()),
                 sum(a["wind"] for r in rows for a in r["bones"].values()),
                 sum(a["nonman"] for r in rows for a in r["bones"].values()),
                 sum(a["degen"] for r in rows for a in r["bones"].values())),
             "- 関節: detach %d / shallow %d / ok %d (うち pivot が親の外: %d)" % (
                 sum(1 for r in rows for j in r["joints"].values() if j["status"] == "detach"),
                 sum(1 for r in rows for j in r["joints"].values() if j["status"] == "shallow"),
                 sum(1 for r in rows for j in r["joints"].values() if j["status"] == "ok"),
                 sum(1 for r in rows for j in r["joints"].values() if not j["pivot_in"])),
             "- ブロックモデル: %d 個中、面の欠け・宙に浮く部品のあるもの %d" % (len(blocks), sum(1 for b in blocks if b[1]["missing"] or b[1]["thin"] or b[1]["floating"])),
             "", "## ランキング (悪い順)", "",
             "| # | メッシュ | 点 | 開いた辺 | 非多様体 | 向き違い | つぶれ | 逆 | 透け画素 | 関節 NG |", "|---|---|---|---|---|---|---|---|---|---|"]
    for i, r in enumerate(ranked):
        if score(r) == 0:
            continue
        b = r["bones"]
        ng = sum(1 for j in r["joints"].values() if j["status"] in ("detach", "shallow"))
        lines.append("| %d | %s | %d | %d | %d | %d | %d | %d | %d | %d |" % (
            i + 1, r["path"], score(r), sum(a["open"] for a in b.values()), sum(a["nonman"] for a in b.values()),
            sum(a["wind"] for a in b.values()), sum(a["degen"] for a in b.values()), sum(a["inv"] for a in b.values()),
            sum(r["see"].values()), ng))
    lines += ["", "## 骨ごとの詳細 (上位 25 個)", ""]
    for r in ranked[:25]:
        if score(r) == 0:
            continue
        lines.append("### %s" % r["path"])
        lines.append("")
        lines.append("| 骨 | 三角形 | 開いた辺 | 輪 | 非多様体 | 向き違い | つぶれ | 逆 | 透け | 付け根 |")
        lines.append("|---|---|---|---|---|---|---|---|---|---|")
        for n, a in sorted(r["bones"].items(), key=lambda kv: -kv[1]["open"]):
            j = r["joints"].get(n)
            js = "-" if not j else "%s(埋%.0f%%/深%.1f)" % (j["status"], j["buried"] * 100, j["depth"])
            if a["open"] or a["nonman"] or a["wind"] or a["inv"] or r["see"].get(n) or (j and j["status"] != "ok"):
                lines.append("| %s | %d | %d | %d | %d | %d | %d | %d | %d | %s |" % (
                    n, a["tris"], a["open"], len(a["loops"]), a["nonman"], a["wind"], a["degen"], a["inv"], r["see"].get(n, 0), js))
        lines.append("")
    bad_blocks = [b for b in blocks if b[1]["missing"] or b[1]["thin"] or b[1]["floating"]]
    lines += ["## キューブの geo (低ポリが無いときの戻り先・防具・武器の元モデル)", ""]
    badg = [g for g in geos if g[1] != 0]
    lines.append("%d 個を、外から撃った光線の最初の当たりが裏面かどうかで検査 (tools/fix_geo_face_sides.holes)。透けあり %d 個。" % (len(geos), len(badg)))
    for gn, gb, gt, per in badg:
        lines.append("- `%s`: %s/%s 本 %s" % (gn, gb, gt, dict(list(per.items())[:4])))
    lines += ["", "## ブロックモデル", ""]
    if not bad_blocks:
        lines.append("面の欠け・宙に浮く部品なし。")
    for name, b in bad_blocks:
        lines.append("- `%s`: %s" % (name, " / ".join(b["notes"][:6])))
    lines.append("")
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        f.write("\n".join(lines))
    return ranked


def audit_blocks(filters=()):
    out = []
    for p in sorted(glob.glob(os.path.join(BLOCKS, "*.json"))):
        n = os.path.basename(p)
        if filters and not any(f in n for f in filters):
            continue
        d = load_json(p)
        if d.get("elements"):
            out.append((n, audit_block(p)))
    return out


# ----------------------------------------------------------------- 自己テスト

def box_mesh(lo, hi, flip=False, drop=0):
    x0, y0, z0 = lo
    x1, y1, z1 = hi
    V = [(x0, y0, z0), (x1, y0, z0), (x1, y1, z0), (x0, y1, z0), (x0, y0, z1), (x1, y0, z1), (x1, y1, z1), (x0, y1, z1)]
    F = [(0, 2, 1), (0, 3, 2), (4, 5, 6), (4, 6, 7), (0, 1, 5), (0, 5, 4), (3, 6, 2), (3, 7, 6), (0, 4, 7), (0, 7, 3), (1, 2, 6), (1, 6, 5)]
    if flip:
        F = [(a, c, b) for a, b, c in F]
    F = F[: len(F) - drop] if drop else F
    return np.array(V, float), np.array(F, int)


def selftest():
    ok = True

    def expect(cond, msg):
        nonlocal ok
        print(("OK   " if cond else "FAIL ") + msg)
        ok = ok and cond
    V, T = box_mesh((0, 0, 0), (4, 4, 4))
    a = analyze(V, T)
    expect(a["open"] == 0 and a["inv"] == 0 and a["wind"] == 0, "closed box: no open edges / not inverted")
    V, T = box_mesh((0, 0, 0), (4, 4, 4), flip=True)
    expect(analyze(V, T)["inv"] == 1, "flipped box is detected as inverted")
    V, T = box_mesh((0, 0, 0), (4, 4, 4), drop=2)
    a = analyze(V, T)
    expect(a["open"] == 4 and len(a["loops"]) == 1, "box with one face missing: 4 open edges in one loop")
    b = {"x": Bone("x", {})}
    b["x"].V, b["x"].T = V, T
    expect(see_through(b).get("x", 0) > 0, "missing face is seen through (back face first)")
    V, T = box_mesh((0, 0, 0), (4, 4, 4))
    b["x"].V, b["x"].T = V, T
    expect(see_through(b).get("x", 0) == 0, "closed box is not see-through")
    V, T = box_mesh((0, 0, 0), (4, 4, 4))
    expect(bool(inside_mask(np.array([[2.0, 2, 2]]), V, T)[0]) and not bool(inside_mask(np.array([[9.0, 2, 2]]), V, T)[0]), "point-in-mesh")
    # 閉じる道具: 穴のある箱を mesh_close で塞ぐと、開いた辺 0・透け 0・体積が正になる
    import mesh_close
    V, T = box_mesh((0, 0, 0), (4, 4, 4), drop=2)
    data = {"bones": {"x": {"parent": None, "pivot": [0, 0, 0], "vertices": V.tolist(), "uvs": [[0.1, 0.1]] * len(V), "triangles": T.tolist()}}}
    closed, _ = mesh_close.close_mesh(data)
    cb = Bone("x", closed["bones"]["x"])
    a = analyze(cb.V, cb.T)
    expect(a["open"] == 0 and a["inv"] == 0 and a["wind"] == 0, "mesh_close seals the missing face")
    expect(see_through({"x": cb}).get("x", 0) == 0, "sealed box is not see-through")
    # ゲート: 入っている全メッシュに開いた辺・向きの食い違い・逆法線が無い
    bad = failures(audit_all((), do_see=False, do_joints=False))
    expect(not bad, "all installed meshes are watertight (%d failures)" % len(bad))
    for line in bad[:10]:
        print("   ", line)
    print("selftest:", "PASS" if ok else "FAIL")
    return 0 if ok else 1


def failures(rows, joints=False):
    """ゲートの失敗。非多様体の辺 (閉じた塊どうしが辺で接しているだけ) は警告で、失敗にしない。"""
    bad = []
    for r in rows:
        for n, a in r["bones"].items():
            if a["open"] or a["wind"] or a["inv"]:
                bad.append("%s/%s: open=%d wind=%d inv=%d" % (r["path"], n, a["open"], a["wind"], a["inv"]))
        tot = sum(r["see"].values())
        if tot and tot > SEE_LIMIT * max(1, r["pixels"]):
            top = sorted(r["see"].items(), key=lambda kv: -kv[1])[:3]
            bad.append("%s: see-through %d/%d px (%.2f%%) %s" % (r["path"], tot, r["pixels"], 100.0 * tot / max(1, r["pixels"]), top))
        if joints:
            for n, j in r["joints"].items():
                if j["status"] in ("detach", "shallow"):
                    bad.append("%s/%s: joint %s" % (r["path"], n, j["status"]))
    return bad


def main(argv):
    if "--selftest" in argv:
        return selftest()
    flt = [a for a in argv if not a.startswith("--")]
    check = "--check" in argv
    full = "--full" in argv or not check
    joints = "--joints" in argv
    rows = audit_all(flt, do_see=full, do_joints=full or joints)
    blocks = audit_blocks(flt)
    geos = audit_geo(flt) if (full or check) else []
    ranked = write_report(rows, blocks, geos=geos) if (not flt and full) else sorted(rows, key=score, reverse=True)
    for r in ranked[:15]:
        print("%6d  %s" % (score(r), r["path"]))
    bad = failures(rows, joints)
    for bn, b in blocks:
        if b["missing"] or b["thin"]:
            bad.append("block %s: %s" % (bn, "; ".join(b["notes"][:3])))
    for gn, gb, gt, per in geos:
        if gb > GEO_LIMIT * max(gt, 1):
            bad.append("geo %s: back-face-first rays %s/%s %s" % (gn, gb, gt, per))
    print("meshes=%d geo=%d blocks=%d failures=%d" % (len(rows), len(geos), len(blocks), len(bad)))
    if check:
        for line in bad[:60]:
            print("  ", line)
        return 1 if bad else 0
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
