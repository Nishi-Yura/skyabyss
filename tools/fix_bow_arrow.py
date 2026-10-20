"""弓の引き絵 (pull1・pull2) の矢を、弦といっしょに後ろへ動かす (矢尻が前に残って矢が伸びていたのを直す)。
引き絵のメッシュの矢 (細長い独立した部品。羽根も) を、pull0 の矢を「弦の引いた分」だけ後ろへずらしたものに差し替える。
入力は元のメッシュ (docs/review/lowpoly_roughs/...)。直したあと install_lowpoly_weapons.py / install_lowpoly_abyss_weapons.py で入れ直す。
使い方: python tools/fix_bow_arrow.py <元のメッシュのフォルダ> <階級の前置き (common / rare / legend)>    (何度流しても同じ。pull0 を基準にする)"""
import json
import sys
from pathlib import Path

import numpy as np


def components(mesh):
    b = mesh["bones"]["weapon"]
    v = np.array(b["vertices"], float).reshape(-1, 3)
    tri = np.array(b["triangles"]).reshape(-1, 3)
    key, w = {}, np.zeros(len(v), int)
    for i, p in enumerate(v):
        w[i] = key.setdefault(tuple(np.round(p, 3)), len(key))
    par = list(range(len(key)))

    def f(x):
        while par[x] != x:
            par[x] = par[par[x]]
            x = par[x]
        return x
    for t in tri:
        a, bb, c = w[t]
        par[f(a)] = f(bb)
        par[f(bb)] = f(c)
    comp = {}
    for i in range(len(v)):
        comp.setdefault(f(w[i]), []).append(i)
    return v, [np.array(ids) for ids in comp.values()]


def classify(v, comps):
    """(弦の部品, 矢の部品たち)。弦 = 縦に長く一番後ろにある部品、矢 = 中ほどの高さ (y 7〜9) の細い部品で握り (x 5.8 以上) でないもの: 軸・やじり・羽根"""
    tall = [c for c in comps if np.ptp(v[c][:, 1]) > 10]
    string = min(tall, key=lambda c: v[c][:, 0].min())
    arrow = [c for c in comps if c is not string and v[c][:, 1].min() > 6.85 and v[c][:, 1].max() < 9.15 and not (v[c][:, 0].min() > 5 and v[c][:, 0].max() < 8)]
    return string, arrow


def main():
    folder, tier = Path(sys.argv[1]), sys.argv[2]
    m0 = json.loads((folder / f"{tier}_pull0_mesh.json").read_text(encoding="utf-8"))
    v0, c0 = components(m0)
    s0, a0 = classify(v0, c0)
    for n in (1, 2):
        p = folder / f"{tier}_pull{n}_mesh.json"
        m = json.loads(p.read_text(encoding="utf-8"))
        v, c = components(m)
        s, a = classify(v, c)
        dx = v[s][:, 0].min() - v0[s0][:, 0].min()
        # 頂点の番号が pull0 と同じ並びなので、pull0 の矢の頂点を dx だけずらして入れる
        idx = np.concatenate(a0)
        assert len(v) == len(v0) and sorted(idx.tolist()) == sorted(np.concatenate(a).tolist()), "矢の部品が pull0 と合わない"
        v[idx] = v0[idx] + np.array([dx, 0, 0])
        m["bones"]["weapon"]["vertices"] = np.round(v, 4).flatten().tolist() if not isinstance(m["bones"]["weapon"]["vertices"][0], list) else np.round(v, 4).tolist()
        p.write_text(json.dumps(m, separators=(",", ":")), encoding="utf-8")
        print(p.name, "弦の引き", round(float(dx), 2), "矢", len(idx), "頂点 → 矢の先 x =", round(float(v[idx][:, 0].max()), 2))


if __name__ == "__main__":
    main()
