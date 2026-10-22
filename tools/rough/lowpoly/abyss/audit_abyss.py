"""深淵の敵のラフ (docs/review/abyss_roughs/<id>/<案>/*_mesh.json) の閉じ具合と浮いた部品の検査。

  PYTHONIOENCODING=utf-8 python -I tools/rough/lowpoly/abyss/audit_abyss.py [--no-close]

1. tools/mesh_close.py の close_mesh_file で、開いた辺・付け根の隙間を塞ぐ (--no-close で省く)。
2. tools/mesh_audit.py の analyze で、全部の骨をつないだ一つのメッシュとして
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


FLOAT_TOL = 0.6


def floating_parts(V, T):
    """連結成分ごとに、ほかの成分の頂点までの最短距離を出し、FLOAT_TOL より遠い成分の数を返す。"""
    from scipy.spatial import cKDTree
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
    comp_of_tri = np.array([find(t[0]) for t in tri])
    labels = {}
    for cid in comp_of_tri:
        labels.setdefault(cid, len(labels))
    comp_v = {}
    for ti, cid in enumerate(comp_of_tri):
        for vi in T[ti]:
            comp_v.setdefault(labels[cid], set()).add(vi)
    if len(comp_v) <= 1:
        return 0
    trees = {k: cKDTree(V[sorted(v)]) for k, v in comp_v.items()}
    n_float = 0
    for k, vs in comp_v.items():
        pts = V[sorted(vs)]
        best = np.inf
        for k2, tr in trees.items():
            if k2 == k:
                continue
            d, _ = tr.query(pts)
            best = min(best, float(d.min()))
        if best > FLOAT_TOL:
            n_float += 1
    return n_float


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
