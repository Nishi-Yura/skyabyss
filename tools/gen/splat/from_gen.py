# -*- coding: utf-8 -*-
"""生成スクリプト (tools/gen/mobs/... の Mount を作る build()) の「設計」から、熔岩の大蛇と同じやり方で点の模型を彫る。

大蛇 (tools/gen/splat/lava_wyrm.py) は、元の生成スクリプトの数字 (骨・部品の位置と大きさ) をそのまま使い、
部品を距離の関数で滑らかにつないで彫り直し、材質ごとの模様と陰を焼き込んだ。ここではそれを全部のボスに使えるようにする:

1. 生成スクリプトを読み込み、形の道具を差し替えて build() を流す。楕円体は本物の楕円体、筒・棘は丸い円すいのつながり
   (どちらも角の無い滑らかな形)。超楕円の胴や板など、それ以外の部品は元の多角形から距離の場を作る。
2. 骨ごとに、その骨の部品を滑らかな和でつなぐ (k は部品の大きさに合わせる)。骨ごとに別の塊にするので、
   アニメで部品が離れても点が散らない。
3. 表面に点を撒き、いちばん近い部品の材質で塗る。色は設計の色の段 (m.pal) に、主光・空の明るさ・窪みの暗さ・尾根の光・
   谷の陰をかけてディザで落とす。設計の模様 (m.detail。割れ目・鱗・甲板など) も点の位置でそのまま描く。光る材質は光る点。
4. 目 (m.eyes) は瞳・虹彩・光の点を描く。

  python -I tools/gen/splat/from_gen.py <生成スクリプトのパス> [--name 名前] [--h 間隔] [--preview]
"""
import argparse
import importlib.util
import math
import sys
import time
from pathlib import Path

import numpy as np
from scipy.ndimage import distance_transform_edt, gaussian_filter, map_coordinates

sys.path.insert(0, str(Path(__file__).resolve().parent))
import splat_lib as S  # noqa: E402
import resculpt as RS  # noqa: E402
import materials as MT  # noqa: E402

ASSETS = S.ROOT / "src/main/resources/assets/skyabyss"


class Part:
    """設計の部品一つ。kind = 'ell' | 'chain' | 'mesh'"""

    def __init__(self, bone, mat, kind, data):
        self.bone, self.mat, self.kind, self.data = bone, mat, kind, data
        self.part = None

    # 形の道具が返す面の並びのふりをする
    def __iter__(self):
        return iter([self])


def _patch(modules):
    """形の道具 (ellipsoid・tube) を、滑らかな部品を返すものに差し替える"""
    import lpkit
    orig_ell = lpkit.ellipsoid
    orig_tube = lpkit.tube

    def ellipsoid(bone, mat, center, radii, segs=8, rings=3, rot=None, phase=None, deform=None):
        if deform is not None:
            return Part(bone, mat, "mesh", orig_ell(bone, mat, center, radii, segs=max(segs, 12), rings=max(rings, 5), rot=rot, phase=phase, deform=deform))
        R = np.eye(3) if rot is None else np.asarray(rot, float)
        return Part(bone, mat, "ell", (np.asarray(center, float), np.asarray(radii, float), R))

    def tube(bone, mat, path, ra, rb=None, sides=4, hint=(0, 1, 0), phase=45.0, caps=(False, False), axis_fn=None, hide_cos=None, ridge_out=False,
             mats=None):
        path = [np.asarray(p, float) for p in path]
        n = len(path)
        ra_ = np.broadcast_to(np.asarray(ra, float), (n,)).astype(float)
        rb_ = ra_ if rb is None else np.broadcast_to(np.asarray(rb, float), (n,)).astype(float)
        r = (ra_ + rb_) / 2
        if mats is not None:
            return Part(bone, mat, "mesh", orig_tube(bone, mat, path, ra, rb, sides=max(sides, 8), hint=hint, phase=phase, caps=caps, axis_fn=axis_fn,
                                                      hide_cos=hide_cos, ridge_out=ridge_out, mats=mats))
        return Part(bone, mat, "chain", (path, r))

    for mod in modules:
        if hasattr(mod, "ellipsoid"):
            mod.ellipsoid = ellipsoid
        if hasattr(mod, "tube"):
            mod.tube = tube


def _mount_add(self, pid, faces, core=False):
    out = []
    loose = {}
    for f in faces:
        if f is None:
            continue
        if isinstance(f, Part) and f.kind == "mesh":
            out.append(Part(f.bone, f.mat, "mesh", list(f.data)))
        elif isinstance(f, Part):
            out.append(f)
        else:
            # 元の多角形の面 (胴の超楕円・板など): 骨と材質ごとに一つの部品にまとめる
            loose.setdefault((f.bone, f.mat), []).append(f)
    for (bone, mat), fs in loose.items():
        out.append(Part(bone, mat, "mesh", fs))
    for f in out:
        f.part = pid
    self.F += out
    if core:
        self.core.add(pid)
    return out


def load_design(path):
    """生成スクリプトを読み、差し替えた道具で build() を流して Mount を返す"""
    path = Path(path).resolve()
    sys.path.insert(0, str(path.parent))
    spec = importlib.util.spec_from_file_location(path.stem + "_design", path)
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    mods = [mod]
    for name in ("mlp", "mlp_extra", "blp", "lpkit"):
        if name in sys.modules:
            mods.append(sys.modules[name])
    _patch(mods)
    import mlp
    mlp.Mount.add = _mount_add
    # 生成スクリプトの中で名前で取り込んだ ell/tub/cone は mlp の関数で、mlp の ellipsoid/tube を引く (差し替え済み)
    return mod.build()


# ---------------------------------------------------------------- 距離の関数
def sdf_ell(c, r, R):
    Rt = R.T

    def f(P):
        q = (P - c) @ Rt.T  # 部品の局所へ (R は局所 → 模型)
        q = (P - c) @ R
        q = q / r
        k0 = np.linalg.norm(q, axis=1)
        k1 = np.linalg.norm(q / r, axis=1)
        return np.where(k1 > 1e-9, k0 * (k0 - 1.0) / np.maximum(k1, 1e-9), -r.min())
    ext = np.abs(R) @ r
    return f, (c - ext, c + ext)


def sdf_chain(path, r):
    """丸い円すいのつながり (の和)"""
    fs = [S.sd_round_cone(path[i], path[i + 1], max(r[i], 0.04), max(r[i + 1], 0.04)) for i in range(len(path) - 1)]
    if not fs:
        return S.sd_ellipsoid(path[0], (r[0],) * 3)

    def f(P):
        d = fs[0][0](P)
        for g, _ in fs[1:]:
            d = np.minimum(d, g(P))
        return d
    lo = np.min([b[0] for _, b in fs], axis=0)
    hi = np.max([b[1] for _, b in fs], axis=0)
    return f, (lo, hi)


def sdf_mesh(faces, h):
    """閉じた多角形の部品から距離の場 (格子に作って線形補間)"""
    tris = []
    for fc in faces:
        v = np.asarray(fc.verts, float)
        for a, b, c in fc.tris():
            tris.append((v[a], v[b], c if False else v[c]))
    V = np.array([p for t in tris for p in t])
    T = np.arange(len(V)).reshape(-1, 3)
    lo = V.min(0) - 3 * h
    hi = V.max(0) + 3 * h
    shape = np.ceil((hi - lo) / h).astype(int) + 1
    wind = RS.winding_grid(V, T, lo, shape, h)
    inside = wind != 0
    if not inside.any():
        return None
    D = (distance_transform_edt(~inside) - distance_transform_edt(inside)) * h + 0.5 * h * inside
    D = gaussian_filter(D.astype(np.float32), 0.7)

    def f(P):
        idx = ((P - lo) / h).T
        return map_coordinates(D, idx, order=1, mode="nearest")
    return f, (lo + 2 * h, hi - 2 * h)


def part_sdf(p, h):
    if p.kind == "ell":
        return sdf_ell(*p.data)
    if p.kind == "chain":
        return sdf_chain(*p.data)
    return sdf_mesh(p.data, h)


def part_size(p):
    if p.kind == "ell":
        return float(np.min(p.data[1]))
    if p.kind == "chain":
        return float(np.max(p.data[1]))
    V = np.concatenate([np.asarray(f.verts, float) for f in p.data])
    return float(np.min(V.max(0) - V.min(0))) / 2


# ---------------------------------------------------------------- 彫る
LEVELS = 6


def sculpt(m, h, glow_mats, rng):
    """骨ごとに部品を滑らかにつないで表面の点を出す。戻り: 点・法線・骨・部品の番号・格子の番号"""
    bones = list(m.bones.keys())
    parts = [p for p in m.F if isinstance(p, Part)]
    out = []
    for bi, bn in enumerate(bones):
        mine = [p for p in parts if p.bone == bn]
        if not mine:
            continue
        sc = S.Sculpt(m.id, bones, h=h)
        for p in mine:
            r = part_sdf(p, h)
            if r is None:
                continue
            # 小さい部品 (歯・棘) ほど鋭く、大きい部品ほど柔らかくつなぐ。光る部品は鋭く (縁がはっきり)
            k = float(np.clip(part_size(p) * 0.28, 0.12, 1.4))
            if p.mat in glow_mats:
                k = min(k, 0.25)
            sc.add(r, p.mat, bn, k=k, glow=p.mat in glow_mats, tag=p.part)
        if not sc.parts:
            continue
        sc.evaluate(margin=2.5 * h)
        # 材質ごとに表面を彫る (割れ目の溝・板の段・継ぎ目と鋲・血管・節・ひだ・毛の筋。materials.relief)
        band = np.argwhere(np.abs(sc.D) < 2.6 * h)
        if len(band):
            Q = band * h + sc.lo
            dq = np.stack([np.abs(f(Q)) for (f, lo, hi, *_r) in sc.parts], 1)
            qp = np.argmin(dq, 1)
            fams = np.array([MT.family(sc.parts[i][3]) for i in qp])
            glowq = np.array([bool(sc.parts[i][6]) for i in qp])
            sc.D[band[:, 0], band[:, 1], band[:, 2]] += MT.relief(Q, fams, h, glowq).astype(sc.D.dtype)
        P, n, pid1, pid2, gap, ijk = sc.surface(seed=bi + 1)
        if len(P) == 0:
            continue
        # 材質は、表面にいちばん近い部品 (|d| が最小。重なった部品の中の部品を選ばない)
        dd = np.stack([np.abs(f(P)) for (f, lo, hi, *_r) in sc.parts], 1)
        pid = np.argmin(dd, 1)
        out.append(dict(sc=sc, P=P, n=n, bone=bi, pid=pid, ijk=ijk))
    return out


def shade_points(m, chunk, pal, rng):
    sc, P, n, pid, ijk = chunk["sc"], chunk["P"], chunk["n"], chunk["pid"], chunk["ijk"]
    mats = np.array([sc.parts[i][3] for i in pid])
    tags = np.array([str(sc.parts[i][7]) for i in pid])
    key = np.array([0.35, 0.9, -0.55])
    key /= np.linalg.norm(key)
    lam = np.clip(n @ key, 0, 1)
    hemi = 0.5 + 0.5 * n[:, 1]
    ao = sc.ao(P, n)
    lap = sc.curvature(P)
    # 熔岩の大蛇と同じ強い光と陰 (主光・空の明るさ・窪みの暗さ)
    Lv = np.clip((0.06 + 0.3 * hemi + 0.68 * lam) * (0.38 + 0.62 * ao), 0, 1)
    dither = S.BAYER4[(ijk[:, 0] + ijk[:, 2]) % 4, ijk[:, 1] % 4]
    col = np.zeros((len(P), 3))
    for name, sp in pal["mats"].items():
        sel = mats == name
        if not sel.any():
            continue
        ramp = np.asarray(sp["ramp"], float)
        nr = len(ramp)
        v = 0.12 + 0.88 * Lv[sel] + 0.15 * (sp.get("base", 0.6) - 0.6) + sp.get("noise", 0.05) * (S.value_noise(P[sel], 3.0, 7) - 0.5) + (dither[sel] - 0.5) * 0.9 / nr
        # 尾根は一段明るく、谷は一段暗く
        idx = np.floor(np.clip(v, 0, 0.999) * nr).astype(int) + (lap[sel] > 0.5 / sc.h).astype(int) - (lap[sel] < -0.3 / sc.h).astype(int)
        idx = np.clip(idx, sp.get("min", 0), nr - 1)
        col[sel] = ramp[idx]
    unknown = ~np.isin(mats, list(pal["mats"].keys()))
    col[unknown] = 160
    glow = np.isin(mats, pal.get("glow_mats", []))
    # 材質ごとの描き足し (溝の奥・鋲・鱗の膨らみ・血管・穴・木目) と照り
    fams = np.array([MT.family(x) for x in mats])
    col, glow, shine = MT.paint(P, n, fams, col, glow, sc.h, lap, key)
    chunk["shine"] = shine
    return col, glow, mats, tags, Lv, dither


def run_detail(m, P, n, mats, col, glow, Lv, dither):
    """設計の模様 (m.detail) を点に描く。画素の辺の情報 (D など) は無いので、辺の暗さ・縫い目は効かない"""
    det = getattr(m, "detail", None)
    if not det:
        return col, glow
    N = len(P)
    c = dict(P=P, N=n, mats=mats, owned=np.ones(N, bool), bay=dither - 0.5, lit=Lv * 2 - 1, D=np.full(N, 9.0), K=np.zeros(N),
             LU=np.zeros(N, int), LV=np.zeros(N, int), INS=np.zeros(N, bool), bones=None)
    try:
        col2, glow2 = det(c, col.copy(), glow.copy())
        return np.clip(col2, 0, 255), np.asarray(glow2, bool)
    except Exception as e:  # noqa: BLE001
        print("  detail skipped:", type(e).__name__, e)
        return col, glow


def seam_glow(m, ch, mats, col, glow, pal):
    """光る材質 (熔岩・熾など) を持つ模型の石: 設計の模様の細かい光る点の代わりに、彫った溝 (relief) に沿って太さのそろった光る線を引く
    (熔岩の大蛇の割れ目と同じ)。光る材質の色の段から色を取る"""
    gm = [g for g in pal.get("glow_mats", []) if g in pal["mats"]]
    if not gm:
        return col, glow
    ramp = np.asarray(pal["mats"][gm[0]]["ramp"], float)
    P, n, h = ch["P"], ch["n"], ch["sc"].h
    st = np.array([MT.family(x) == "stone" for x in mats])
    if not st.any():
        return col, glow
    designed = st & glow
    col[designed] = col[designed] * 0.35
    glow = glow & ~st
    f1, f2, cid = S.worley(P, 7.0 * h, 3)
    e = f2 - f1
    seam = st & (e < 0.05) & (cid > 0.3) & (n[:, 1] < 0.7)
    t = np.clip(1 - e / 0.05, 0, 1)
    idx = np.clip((0.35 + 0.6 * t) * (len(ramp) - 1), 0, len(ramp) - 1).astype(int)
    col[seam] = ramp[idx[seam]]
    rim = st & ~seam & (e < 0.09) & (cid > 0.3) & (n[:, 1] < 0.7)
    col[rim] = col[rim] * 0.5 + ramp[0] * 0.5
    glow = glow | seam
    return col, glow


def paint_eyes(m, P, n, bone_of, col, glow):
    """目: 瞳 (濃い)・虹彩 (目の色)・まぶたの縁 (暗い)・光の点"""
    for e in getattr(m, "eyes", []):
        c = np.asarray(e["c"], float)
        r = float(e["r"])
        d = np.asarray(e["dir"], float)
        sel = (np.linalg.norm(P - c, axis=1) < r * 1.25)
        if not sel.any():
            continue
        q = P[sel] - c
        front = q @ d / r
        lateral = np.linalg.norm(q - np.outer(q @ d, d), axis=1) / r
        cc = col[sel]
        iris = np.asarray(e.get("iris", (220, 180, 80)), float)
        cc = np.where((front > 0.2)[:, None], iris * (0.85 + 0.25 * front[:, None]), cc * 0.6)
        pupil = (front > 0.55) & (lateral < 0.32)
        cc[pupil] = (18, 12, 14)
        rim = (front > -0.1) & (front < 0.25)
        cc[rim] = cc[rim] * 0.45
        hi = (front > 0.5) & (q @ np.array([0.35, 0.9, -0.2]) / r > 0.25) & (lateral > 0.25) & (lateral < 0.5)
        cc[hi] = (255, 250, 236)
        col[sel] = cc
        if e.get("glow"):
            g = glow[sel]
            g |= (front > 0.2) & ~pupil
            glow[sel] = g
    return col, glow


def build_cloud(path, name=None, h=None, preview=False):
    t0 = time.time()
    m = load_design(path)
    name = name or m.id
    pal = getattr(m, "pal", None) or {"mats": {}}
    glow_mats = list(pal.get("glow_mats", []))
    bones = list(m.bones.keys())
    allv = np.concatenate([p.data[0][None] if p.kind == "ell" else (np.array(p.data[0]) if p.kind == "chain" else np.concatenate([np.asarray(f.verts) for f in p.data]))
                           for p in m.F if isinstance(p, Part)])
    maxdim = float((allv.max(0) - allv.min(0)).max())
    # 間隔は大きさの 1/150 だが、細長い模型 (大百足など) は細かい部品が粗くならないよう、点の数の見積もり (表面積) で決める
    if not h:
        ext = allv.max(0) - allv.min(0)
        area = 2 * (ext[0] * ext[1] + ext[1] * ext[2] + ext[0] * ext[2])
        h = max(0.18, min(maxdim / 150.0, (area / 90000.0) ** 0.5))
    rng = np.random.default_rng(abs(hash(name)) % (2 ** 31))
    chunks = sculpt(m, h, glow_mats, rng)
    Ps, ns, cols, flagsL, As = [], [], [], [], []
    for ch in chunks:
        col, glow, mats, tags, Lv, dither = shade_points(m, ch, pal, rng)
        col, glow = run_detail(m, ch["P"], ch["n"], mats, col, glow, Lv, dither)
        col, glow = seam_glow(m, ch, mats, col, glow, pal)
        col, glow = paint_eyes(m, ch["P"], ch["n"], None, col, glow)
        sh = ch.get("shine")
        if sh is not None:
            col = np.where(sh[:, None], col * 0.35 + np.array([255, 248, 238]) * 0.65, col)
        Ps.append(ch["P"])
        ns.append(ch["n"])
        cols.append(col)
        flagsL.append(np.where(glow, S.F_GLOW, 0))
        As.append(np.full(len(ch["P"]), ch["bone"]))
    P = np.concatenate(Ps)
    n = np.concatenate(ns)
    col = np.concatenate(cols)
    flags = np.concatenate(flagsL).astype(np.int32)
    A = np.concatenate(As)
    N = len(P)
    size = h * 1.42 * rng.uniform(0.9, 1.1, N)
    g = np.nonzero(flags & S.F_GLOW)[0]
    gi = g[rng.random(len(g)) < min(0.12, 400.0 / max(1, len(g)))]
    allP = np.concatenate([P, P[gi] + n[gi] * h])
    allS = np.concatenate([size, rng.uniform(3.5, 5.5, len(gi)) * h])
    allC = np.concatenate([col, col[gi] * 0.07])
    allA = np.concatenate([A, A[gi]])
    allF = np.concatenate([flags, np.full(len(gi), S.F_HALO)])
    allN = np.concatenate([n, np.zeros((len(gi), 3))])
    out = ASSETS / "splat" / (name + ".bin")
    S.write_bin3(out, bones, allP, allS, S.argb(allC), allA, allA, np.ones(len(allP)), allF, N=allN)
    print(f"{name}: {N} splats (+{len(gi)} halo) h={h:.2f} {len(chunks)} bones {time.time() - t0:.1f}s")
    if preview:
        pv = S.ROOT / "docs/review/splat_handsculpt" / (name + ".png")
        pv.parent.mkdir(parents=True, exist_ok=True)
        S.preview(allP, allS, allC, allF, pv, yaw_list=(200, 150, 90), px=600)
    return out


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("path")
    ap.add_argument("--name")
    ap.add_argument("--h", type=float)
    ap.add_argument("--preview", action="store_true")
    a = ap.parse_args()
    build_cloud(a.path, a.name, a.h, a.preview)


if __name__ == "__main__":
    main()
