#!/usr/bin/env python3
"""ラフで選んだ「足す部品」を、入っている低ポリの模型に入れる (2026-11-11)。
部品は tools/rough/lowpoly/late_boss_horror/build.py の案の関数で作り、決めた骨に付ける (骨と一緒に動く)。
最初に流したときに元の模型を tools/sources/boss_addon_base/ へ写し、二度目からはそこから作り直す (かけ重ならない)。

  heart_warden  C 鍵の後光      (頭の骨)
  lord_ash      B 燠の腹        (体の骨)

    python -X utf8 -I tools/install/install_boss_addons.py
"""
from __future__ import annotations

import importlib.util
import json
import math
import shutil
import sys
from pathlib import Path

import numpy as np

ROOT = Path(__file__).resolve().parents[2]
spec_ = importlib.util.spec_from_file_location("late_boss_horror", ROOT / "tools/rough/lowpoly/late_boss_horror/build.py")
lb = importlib.util.module_from_spec(spec_)
spec_.loader.exec_module(lb)
spec2 = importlib.util.spec_from_file_location("install_heart_eyes", ROOT / "tools/install/install_heart_eyes.py")
he = importlib.util.module_from_spec(spec2)
spec2.loader.exec_module(he)
from blp import *  # noqa: E402,F401,F403
import blp  # noqa: E402

A = ROOT / "src/main/resources/assets/skyabyss"
BASE = ROOT / "tools/sources/boss_addon_base"
ADDONS = {
    "heart_warden": (lb.warden_c, "head"),
    "lord_ash": (lb.ash_lord_b, "body"),
}


def install(stem, fn, bone):
    BASE.mkdir(parents=True, exist_ok=True)
    for f in he.files(stem):
        dst = BASE / f.name
        if f.exists() and not dst.exists():
            shutil.copy2(f, dst)
    data = json.loads((BASE / f"{stem}.json").read_text(encoding="utf-8"))
    geo = json.loads((A / f"geo/entity/{stem}_lowpoly.geo.json").read_text(encoding="utf-8"))["minecraft:geometry"][0]
    xf = he.rest_xf(geo)
    # 今の姿を、ラフと同じ読み方で組む (部品の置き場所をラフと同じにする)
    base = lb.hh.load_base(stem) if False else None
    faces = []
    for bname, b in data["bones"].items():
        if not b["vertices"]:
            continue
        Rm, t = xf(bname)
        V = np.array(b["vertices"], float) @ Rm.T + t
        for tr in b["triangles"]:
            v = V[tr]
            from lpkit import Face
            f = Face(verts=v, bone=bname, mat="x", ref=v.mean(0))
            faces.append(f)
    R = lb.Ref(faces)
    m = Mount("addon_" + stem, stem)
    fn(m, R)
    m.pal = dict(mats={"eye": M_(EYE, base=0.5, lit=0.2, noise=0.0, min=0), **{k: (M_(v, base=0.75, lit=0.15, min=1) if k in lb.GLOW else M_(v, lit=0.36))
                                                                            for k, v in lb.PAL.items()}}, glow_mats=sorted(lb.GLOW))
    size, atlas, glow = blp.paint_mount(m, m.pal, None, 256)
    base_img = Image.open(BASE / f"{stem}.png").convert("RGBA")
    S = base_img.size[0]
    N = 1 << int(math.ceil(math.log2(S + size)))
    canvas = Image.new("RGBA", (N, N), (0, 0, 0, 0))
    canvas.paste(base_img, (0, 0))
    canvas.paste(Image.fromarray(atlas, "RGBA"), (S, 0))
    canvas.save(A / f"textures/lowpoly/{stem}.png")
    gsrc = BASE / f"{stem}_glowmask.png"
    gc = Image.new("RGBA", (N, N), (0, 0, 0, 0))
    if gsrc.exists():
        gc.paste(Image.open(gsrc).convert("RGBA"), (0, 0))
    add = np.zeros((size, size, 4), np.uint8)
    add[glow] = (255, 255, 255, 255)
    gc.paste(Image.fromarray(add, "RGBA"), (S, 0))
    if np.array(gc)[..., 3].any():
        gc.save(A / f"textures/lowpoly/{stem}_glowmask.png")
    k = S / N
    for b in data["bones"].values():
        b["uvs"] = [[round(u * k, 6), round(v * k, 6)] for u, v in b["uvs"]]
    Rm, t = xf(bone)
    b = data["bones"][bone]
    added = 0
    for f in m.F:
        base_i = len(b["vertices"])
        cen = f.uv.mean(0)
        for p, uv in zip(f.verts, f.uv):
            local = Rm.T @ (np.asarray(p) - t)
            dd = cen - uv
            nn = np.linalg.norm(dd)
            uv = uv + dd / nn * min(0.5, nn * 0.4) if nn > 1e-9 else uv
            b["vertices"].append([round(float(x), 4) for x in local])
            b["uvs"].append([round((float(uv[0]) + S) / N, 6), round(float(uv[1]) / N, 6)])
        for a_, c_, d_ in f.tris():
            b["triangles"].append([base_i + a_, base_i + c_, base_i + d_])
            added += 1
    for bb in data["bones"].values():
        bb["triangle_count"] = len(bb["triangles"])
    data["triangle_count"] = sum(len(bb["triangles"]) for bb in data["bones"].values())
    data["texture_size"] = [N, N]
    data["note_addon"] = f"{fn.__doc__.split(':')[0].strip()} (tools/install/install_boss_addons.py)"
    (A / f"lowpoly/{stem}.json").write_text(json.dumps(data, ensure_ascii=False), encoding="utf-8")
    return added


if __name__ == "__main__":
    sys.stdout.reconfigure(encoding="utf-8")
    for stem, (fn, bone) in ADDONS.items():
        print(stem, "tris", install(stem, fn, bone), flush=True)
