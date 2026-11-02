"""build_gaps に弓の引き絵の段 (stages) を足す。WEAPONS[id]["stages"] > 1 の品は、形の関数を fn(stage) で呼び、
段 0 = <tier>、段 1.. = <tier>_pull0.. の名前で書く (install_lowpoly_abyss_weapons と同じ名前)。アイコンも段ごと (icon / icon_pull0 ..)。
使い方: import staged; staged.install(build_gaps)"""
from __future__ import annotations

import numpy as np


def install(B):
    def make(wid, k, plus=0, stage=0):
        w = B.D.WEAPONS[wid]
        fn, inlay, em, _ = w["opts"][k]
        faces = fn(stage) if w.get("stages", 1) > 1 else fn()
        faces, size, P, N, M, L = B.build(faces)
        atlas, glow = B.PG.paint(faces, size, P, N, M, L, w["glow"], w["tier"], plus, inlay, em)
        return dict(faces=faces, size=size, atlas=atlas, glow=glow, tris=B.tri_count(faces))

    def build_option(wid, k, quick=False):
        w = B.D.WEAPONS[wid]
        d = B.RAW / wid / k
        d.mkdir(parents=True, exist_ok=True)
        tier = w["tier"]
        res0 = {}
        for stg in range(w.get("stages", 1)):
            stem = tier if stg == 0 else f"{tier}_pull{stg - 1}"
            base = None
            for plus in ((0,) if quick else (0, 5, 10)):
                r = make(wid, k, plus, stg)
                B.save_pair(r, d, stem + (f"_plus{plus}" if plus else ""))
                if plus == 0:
                    base = r
                    B.export_mesh(r["faces"], r["size"], d / f"{stem}_mesh.json", f"{wid}_{k}_{stem}", f"{stem}_atlas.png", f"{stem}_glowmask.png")
                else:
                    assert r["size"] == base["size"], "uv layout changed between plus levels"
                if stg == 0:
                    res0[plus] = r
            B.icon(base, wid).save(d / ("icon.png" if stg == 0 else f"icon_pull{stg - 1}.png"))
        return res0

    B.make = make
    B.build_option = build_option
    return B
