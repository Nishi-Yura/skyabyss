# -*- coding: utf-8 -*-
"""裏面透けが「どこ」にあるかを出す (mesh_audit の see を三角形ごとに数え、骨と場所でまとめる)。

    python tools/audit/see_where.py bone_mortar rewinder        # 名前の一部
    python tools/audit/see_where.py --top 12                    # 透けの多い順に 12 個

出力: メッシュごとに、透けの画素が多い三角形の塊 (骨・中心・法線・画素)。生成器のどの部品かを座標から探す手がかり。
"""
from __future__ import annotations

import os
import sys

import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import mesh_audit as M  # noqa: E402


def where(path, top=8):
    bones = M.load_mesh(path)
    if not bones:
        return None
    detail = {}
    see = M.see_through(bones, detail=detail)
    see.pop("__total", None)
    names = [n for n in bones if len(bones[n].T)]
    offs, tri_owner = [], []
    voff = 0
    allV, allT = [], []
    for n in names:
        b = bones[n]
        allV.append(b.V)
        allT.append(b.T + voff)
        tri_owner += [n] * len(b.T)
        voff += len(b.V)
    V = np.vstack(allV)
    T = np.vstack(allT)
    cells = {}
    for ti, k in detail.items():
        c = V[T[ti]].mean(0)
        key = (tri_owner[ti], tuple(np.round(c / 1.5).astype(int)))
        e = cells.setdefault(key, [0, np.zeros(3), 0])
        e[0] += k
        e[1] += c * k
        e[2] += 1
    rows = sorted(((v[0], k[0], v[1] / v[0], v[2]) for k, v in cells.items()), key=lambda r: -r[0])
    return sum(see.values()), see, rows[:top]


def main(argv):
    top = None
    if argv and argv[0] == "--top":
        top = int(argv[1])
        argv = argv[2:]
    files = M.mesh_files()
    if argv:
        files = [p for p in files if any(a in M.rel(p) for a in argv)]
    out = []
    for p in files:
        r = where(p)
        if r and r[0] > 0:
            out.append((r[0], M.rel(p), r))
    out.sort(key=lambda x: -x[0])
    if top:
        out = out[:top]
    for tot, rel, (_, see, rows) in out:
        print(f"== {rel}  see {tot}  " + ", ".join(f"{k}:{v}" for k, v in sorted(see.items(), key=lambda x: -x[1]) if v))
        for pix, bone, c, n in rows:
            print(f"   {pix:5d} px  {bone:16s} at ({c[0]:6.2f}, {c[1]:6.2f}, {c[2]:6.2f})  tris {n}")


if __name__ == "__main__":
    main(sys.argv[1:])
