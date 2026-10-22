"""深淵の敵のラフ (docs/review/abyss_roughs/<id>/<案>/*_mesh.json) の閉じ具合と浮いた部品の検査。

  PYTHONIOENCODING=utf-8 python -I tools/rough/lowpoly/abyss/audit_abyss.py [--no-close]

1. tools/lib/mesh_close.py の close_mesh_file で、開いた辺・付け根の隙間を塞ぐ (--no-close で省く)。
2. tools/audit/mesh_audit.py の analyze で、全部の骨をつないだ一つのメッシュとして
   open (開いた辺) / nonman (多様体でない辺) / wind (向きの食い違い) / degen (つぶれた三角形) / inv (内向き) を数える。
3. 浮いた部品: 骨ごとではなく部品 (面の part 名。ここではメッシュの連結成分) の頂点が、ほかのどの成分の頂点からも
   FLOAT_TOL (0.6 単位 = 0.04 ブロック) より離れていれば「浮いた部品」。部品の根元は親に沈めるので、接していれば浮かない。
"""
import glob
import json
import os
import sys
from pathlib import Path

import numpy as np

ROOT = Path(__file__).resolve().parents[4]
sys.path.insert(0, str(ROOT / "tools"))
sys.path.insert(0, str(ROOT / "tools" / "audit"))
sys.path.insert(0, str(ROOT / "tools" / "lib"))
import mesh_audit as ma  # noqa: E402
import mesh_close as mc  # noqa: E402

RES = ROOT / "docs/review/abyss_roughs"


def merged(path):
    d = json.loads(Path(path).read_text(encoding="utf-8"))
    Vs, Ts, owner, off = [], [], [], 0
    for name, b in d["bones"].items():
        V = np.array(b.get("vertices", []), float).reshape(-1, 3)
        T = np.array(b.get("triangles", []), int).reshape(-1, 3)
        if len(T) == 0:
            continue
        Vs.append(V)
        Ts.append(T + off)
        owner += [name] * len(T)
        off += len(V)
    return np.vstack(Vs), np.vstack(Ts), owner


FLOAT_TOL = 0.75


def floating_parts(V, T):
    """浮いた部品の数。連結成分 (頂点を共有しない部品) ごとに、標本点 (頂点・辺の中点・面の中心) が
    ほかの成分の標本点から FLOAT_TOL 以内か、ほかの成分の凸包の中にあれば「くっついている」。どちらもなければ浮いている。"""
    from scipy.spatial import cKDTree, Delaunay
    c = ma.weld_ids(V)
    tri = c[T]
    parent = {}

    def find(x):
        while parent.setdefault(x, x) != x:
            parent[x] = parent[parent[x]]
            x = parent[x]
        return x
    for a, b, cc in tri:
        for u, v in ((a, b), (b, cc)):
            ra, rb = find(u), find(v)
            if ra != rb:
                parent[ra] = rb
    roots = np.array([find(t[0]) for t in tri])
    comps = {}
    for ti, r in enumerate(roots):
        comps.setdefault(r, []).append(ti)
    if len(comps) <= 1:
        return 0
    samples, vsets = {}, {}
    for r, tis in comps.items():
        vi = np.unique(T[tis].ravel())
        vsets[r] = V[vi]
        tp = V[T[tis]]
        mids = np.concatenate([(tp[:, 0] + tp[:, 1]) / 2, (tp[:, 1] + tp[:, 2]) / 2, (tp[:, 2] + tp[:, 0]) / 2, tp.mean(1)])
        samples[r] = np.vstack([V[vi], mids])
    keys = list(comps)
    trees = {r: cKDTree(samples[r]) for r in keys}
    hulls = {}
    for r in keys:
        try:
            hulls[r] = Delaunay(vsets[r])
        except Exception:
            hulls[r] = None
    # 部品どうしのつながり (触れている・中に入っている) を辺にして、塊 (グループ) を作る。
    # 塊が一つだけなら浮いていない。二つ以上なら、一番大きい塊以外の部品の数を浮いた部品とする。
    # (二つの部品が互いに触れていても、胴に付いていなければ浮いた塊として数える)
    gp = {r: r for r in keys}

    def gfind(x):
        while gp[x] != x:
            gp[x] = gp[gp[x]]
            x = gp[x]
        return x
    for r in keys:
        for q in keys:
            if q == r:
                continue
            d, _ = trees[q].query(samples[r])
            touch = d.min() <= FLOAT_TOL
            h = hulls[q]
            inside = h is not None and (h.find_simplex(samples[r]) >= 0).any()
            if touch or inside:
                gp[gfind(r)] = gfind(q)
    groups = {}
    for r in keys:
        groups.setdefault(gfind(r), []).append(r)
    if len(groups) <= 1:
        return 0
    biggest = max(len(g) for g in groups.values())
    return sum(len(g) for g in groups.values()) - biggest


def main(argv):
    do_close = "--no-close" not in argv
    files = sorted(glob.glob(str(RES / "*" / "*" / "*_mesh.json")))
    total_bad = 0
    for p in files:
        rel = os.path.relpath(p, ROOT).replace("\\", "/")
        if do_close:
            mc.close_mesh_file(p, with_collar=True, force=True)
        V, T, owner = merged(p)
        a = ma.analyze(V, T)
        nf = floating_parts(V, T)
        bad = a["open"] + a["nonman"] + a["wind"] + a["degen"] + a["inv"] + nf
        total_bad += bad
        print(f"{rel}: tris={a['tris']} open={a['open']} nonman={a['nonman']} wind={a['wind']} degen={a['degen']} inv={a['inv']} "
              f"floating_parts={nf}")
    print("files", len(files), "total_bad", total_bad)
    return 1 if total_bad else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
