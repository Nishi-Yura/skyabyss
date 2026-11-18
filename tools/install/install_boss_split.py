#!/usr/bin/env python3
"""層の主・淵の王の「見た目を分ける」ラフで選んだ案を入れる (2026-11-11。ラフは tools/rough/lowpoly/boss_split/build.py)。
  主: 絵を由来の色の段へ塗り直す (形はそのまま)
  王: 絵を塗り直し、共通の金の籠の冠 (と案によって衣・脚) の三角形を外し、王ごとの部品を足す。三つの周 (c0・c1・c2) すべてに
部品は高さで頭の骨 (head) か体の骨 (body) に付ける (頭と一緒に動く物・体と一緒に動く物)。
最初に流したときに元の模型を tools/sources/boss_split_base/ へ写し、二度目からはそこから作り直す (かけ重ならない)。
灯り喰いの主は、燠の腹 (install_boss_addons.py) を入れたあとの姿を元にする。あちらを流し直したら、こちらも流し直す。

    python -X utf8 -I tools/install/install_boss_split.py
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
spec_ = importlib.util.spec_from_file_location("boss_split", ROOT / "tools/rough/lowpoly/boss_split/build.py")
bs = importlib.util.module_from_spec(spec_)
spec_.loader.exec_module(bs)
from blp import *  # noqa: E402,F401,F403
import blp  # noqa: E402
spec_fi = importlib.util.spec_from_file_location("install_finale_addons", ROOT / "tools/install/install_finale_addons.py")
fi = importlib.util.module_from_spec(spec_fi)
spec_fi.loader.exec_module(fi)
import re  # noqa: E402

# 仕上げ (2026-11-11 の声「ラフレベルの見た目じゃない？ちゃんとアニメーションある？」): 部品の分け目を増やし、垂れる部品は揺れる骨に
KMOTION = {
    "king_bone": [dict(re=r"^rib(\d)$", bone="sw_rib{0}", kind="sway", amp=(4, 4), freq=50)],
    "king_storm": [dict(re=r"^tail(\d)$", bone="sw_tail{0}", kind="sway", amp=(12, 12), freq=90)],
    "king_ash": [dict(re=r"^rag(\d+)$", bone="sw_rag{0}", kind="sway", amp=(10, 4), freq=70)],
    "king_shadow": [dict(re=r"^train(\d+)$", bone="sw_train{0}", kind="sway", amp=(6, 6), freq=45)],
}


def fine_parts(fn, R, light):
    """bs.parts を、分け目を増やした形で (boss_split の部品と king_heads の頭の両方)。"""
    (b_, tub0, cone0, ell0), (tub, cone, ell) = fi._fine()
    saved = (bs.tub, bs.cone, bs.ell)
    bs.tub, bs.cone, bs.ell = tub, cone, ell
    blp.tub, blp.cone, blp.ell = tub, cone, ell
    try:
        return bs.parts(fn, R, light)
    finally:
        bs.tub, bs.cone, bs.ell = saved
        blp.tub, blp.cone, blp.ell = tub0, cone0, ell0

A = ROOT / "src/main/resources/assets/skyabyss"
BASE = ROOT / "tools/sources/boss_split_base"

# 選んだ案 (2026-11-11): 主は案の番号、王は案の番号 (三つの周に同じ案)
PICKS = {"lord_rib": 2, "lord_salt": 0, "lord_storm": 1, "lord_ash": 0,
         "king_bone": 1, "king_salt": 0, "king_storm": 1, "king_ash": 1, "king_shadow": 0}


def whole_parts(faces, head_from):
    """つながった面 (頂点を 0.01 で溶接して辺を共有する面) の群れごとに、頂点の高さの平均で head / body を決める。戻り値 {id(面): 骨}"""
    par = list(range(len(faces)))

    def find(x):
        while par[x] != x:
            par[x] = par[par[x]]
            x = par[x]
        return x
    owner = {}
    for i, f in enumerate(faces):
        for p in f.verts:
            k = tuple(np.round(np.asarray(p, float) / 0.01).astype(int))
            j = owner.setdefault(k, i)
            if j != i:
                par[find(i)] = find(j)
    ys = {}
    for i, f in enumerate(faces):
        s = ys.setdefault(find(i), [0.0, 0])
        s[0] += float(np.asarray(f.verts, float)[:, 1].sum())
        s[1] += len(f.verts)
    return {id(f): ("head" if ys[find(i)][0] / ys[find(i)][1] >= head_from else "body") for i, f in enumerate(faces)}


def keep(stem, tex):
    BASE.mkdir(parents=True, exist_ok=True)
    for f in (A / f"lowpoly/{stem}.json", A / f"textures/lowpoly/{tex}.png", A / f"textures/lowpoly/{tex}_glowmask.png"):
        dst = BASE / f.name
        if f.exists() and not dst.exists():
            shutil.copy2(f, dst)


def install(stem, opt_lord=None, opt_king=None):
    src = json.loads(((BASE / f"{stem}.json") if (BASE / f"{stem}.json").exists() else (A / f"lowpoly/{stem}.json")).read_text(encoding="utf-8"))
    tex = src.get("texture", stem)
    keep(stem, tex)
    data = json.loads((BASE / f"{stem}.json").read_text(encoding="utf-8"))
    img = np.array(Image.open(BASE / f"{tex}.png").convert("RGBA"))
    gp = BASE / f"{tex}_glowmask.png"
    glow = np.array(Image.open(gp).convert("RGBA"))[..., 3] > 0 if gp.exists() else np.zeros(img.shape[:2], bool)
    if opt_lord is not None:
        _, body, metal, light, _ = opt_lord
        # 元の絵の細かい明るさの粒が、塗り直すと白い点のまだらになっていた (2026-11-11)。光らない所は 3×3 の中央値でならしてから塗る
        sm = img.copy()
        solid = img[..., 3] > 0
        # ひとつだけぽつんと光る画素 (まわり 8 つに光る画素が 2 つ未満) は光らせない (体中の白い点に見えていた)
        nb = sum(np.roll(np.roll(glow, dy, 0), dx, 1).astype(int) for dy in (-1, 0, 1) for dx in (-1, 0, 1)) - glow.astype(int)
        glow = glow & (nb >= 2)
        stack = []
        for dy in (-1, 0, 1):
            for dx in (-1, 0, 1):
                stack.append(np.roll(np.roll(img[..., :3].astype(np.int16), dy, 0), dx, 1))
                stack[-1] = np.where((np.roll(np.roll(solid, dy, 0), dx, 1))[..., None], stack[-1], img[..., :3].astype(np.int16))
        med = np.median(np.stack(stack), axis=0).astype(np.uint8)
        keep_px = glow | ~solid
        sm[..., :3] = np.where(keep_px[..., None], img[..., :3], med)
        out = bs.recolor(sm, glow, bs.PAL[body], bs.PAL[metal], bs.PAL[light])
        Image.fromarray(out, "RGBA").save(A / f"textures/lowpoly/{tex}.png")
        if gp.exists():
            gm = np.zeros(img.shape[:2] + (4,), np.uint8)
            gm[glow] = (255, 255, 255, 255)
            Image.fromarray(gm, "RGBA").save(A / f"textures/lowpoly/{tex}_glowmask.png")
        (A / f"lowpoly/{stem}.json").write_text(json.dumps(data, ensure_ascii=False), encoding="utf-8")
        return 0
    label, body, metal, light, drop, fn = opt_king
    recol = bs.recolor(img, glow, bs.PAL[body], bs.PAL[metal], bs.PAL[light])
    # 今の姿を組んで (部品の置き場所を決めるため)
    geo = json.loads((A / f"geo/entity/{stem}_lowpoly.geo.json").read_text(encoding="utf-8"))["minecraft:geometry"][0]
    xf = bs.he.rest_xf(geo)
    faces, _, _ = bs.load(stem) if False else (None, None, None)
    H, W = img.shape[:2]
    wf = []
    for bn, b in data["bones"].items():
        if not b["vertices"]:
            continue
        Rm, t = xf(bn)
        Vw = np.array(b["vertices"], float) @ Rm.T + t
        for tr in b["triangles"]:
            v = Vw[tr]
            wf.append(bs.Face(verts=v, bone=bn, mat="x", ref=v.mean(0)))
    R = bs.Ref(wf)
    addf, adda, addg = fine_parts(fn, R, light)
    # 外す骨の三角形
    for bn in drop:
        if bn in data["bones"]:
            data["bones"][bn]["vertices"], data["bones"][bn]["uvs"], data["bones"][bn]["triangles"] = [], [], []
    # 絵を合わせる (元の絵を左、部品の絵を右)
    size = adda.shape[0]
    NW = 1 << int(math.ceil(math.log2(W + size)))
    NH = 1 << int(math.ceil(math.log2(max(H, size))))
    canvas = np.zeros((NH, NW, 4), np.uint8)
    canvas[:H, :W] = recol
    canvas[:size, W:W + size] = adda
    Image.fromarray(canvas, "RGBA").save(A / f"textures/lowpoly/{tex}.png")
    gm = np.zeros((NH, NW, 4), np.uint8)
    gm[:H, :W][glow] = (255, 255, 255, 255)
    gm[:size, W:W + size][addg] = (255, 255, 255, 255)
    Image.fromarray(gm, "RGBA").save(A / f"textures/lowpoly/{tex}_glowmask.png")
    for b in data["bones"].values():
        b["uvs"] = [[round(u * W / NW, 6), round(v * H / NH, 6)] for u, v in b["uvs"]]
    hlo, hhi, _ = R.box("head")
    head_from = hlo[1] - 1.0
    family = stem.rsplit("_c", 1)[0]
    groups = {}
    for f in addf:
        for spec in KMOTION.get(family, []):
            mt = re.match(spec["re"], getattr(f, "part", "") or "")
            if mt:
                groups.setdefault(spec["bone"].format(*mt.groups()), (spec, []))[1].append(f)
    new_bones = {}
    face_bone = {}
    for gb, (spec, fs) in groups.items():
        V = np.concatenate([f.verts for f in fs])
        parent = "head" if fs[0].verts.mean(0)[1] >= head_from else "body"
        new_bones[gb] = (parent, [round(float(x), 4) for x in V[np.argmax(V[:, 1])]])
        for f in fs:
            face_bone[id(f)] = gb
    for gb, (parent, pv) in new_bones.items():
        data["bones"][gb] = {"parent": parent, "pivot": pv, "vertices": [], "uvs": [], "triangles": [], "triangle_count": 0}
    # 頭か体かは、つながった部品ごとに決める (面ごとに高さで分けると、境の高さをまたぐ部品が二つの骨に切れて、
    # 首が回ると切り口が開いていた。2026-11-11 の監査)
    side = whole_parts(addf, head_from)
    added = 0
    for f in addf:
        bone = side[id(f)]
        if id(f) in face_bone:
            bone = new_bones[face_bone[id(f)]][0]
        Rm, t = xf(bone)
        b = data["bones"][face_bone.get(id(f), bone)]
        base_i = len(b["vertices"])
        cen = f.uv.mean(0)
        for p, uv in zip(f.verts, f.uv):
            local = Rm.T @ (np.asarray(p) - t)
            dd = cen - uv
            nn = np.linalg.norm(dd)
            uv = uv + dd / nn * min(0.5, nn * 0.4) if nn > 1e-9 else uv
            b["vertices"].append([round(float(x), 4) for x in local])
            b["uvs"].append([round((float(uv[0]) + W) / NW, 6), round(float(uv[1]) / NH, 6)])
        for a_, c_, d_ in f.tris():
            b["triangles"].append([base_i + a_, base_i + c_, base_i + d_])
            added += 1
    for gb in new_bones:
        data["bones"][gb]["cap_vertex_start"] = len(data["bones"][gb]["vertices"])
    for b in data["bones"].values():
        b["triangle_count"] = len(b["triangles"])
    data["triangle_count"] = sum(len(b["triangles"]) for b in data["bones"].values())
    data["texture_size"] = [NW, NH]
    data["note_split"] = f"{label} (tools/install/install_boss_split.py)"
    fi.mc.tidy_bones(data)
    (A / f"lowpoly/{stem}.json").write_text(json.dumps(data, ensure_ascii=False), encoding="utf-8")
    # 揺れの骨を骨組みへ (元の写しから) と、王のアニメへ (三つの周で同じ名前の骨)
    for gname in (f"{stem}_lowpoly.geo.json", f"{stem}_lowpoly_debug.geo.json"):
        gp = A / f"geo/entity/{gname}"
        if not gp.exists():
            continue
        keepg = BASE / gname
        if not keepg.exists():
            shutil.copy2(gp, keepg)
        doc = json.loads(keepg.read_text(encoding="utf-8"))
        bl = doc["minecraft:geometry"][0]["bones"]
        bl[:] = [x for x in bl if not x["name"].startswith("sw_")]
        for gb, (parent, pv) in sorted(new_bones.items()):
            bl.append({"name": gb, "pivot": pv, "parent": parent})
        gp.write_text(json.dumps(doc, ensure_ascii=False), encoding="utf-8")
    if stem.endswith("_c0"):
        fi.write_anims(family, fi.motion_tracks(family, {k: (groups[k][0], None) for k in groups}))
    return added


if __name__ == "__main__":
    sys.stdout.reconfigure(encoding="utf-8")
    for name, k in PICKS.items():
        if name in bs.LORDS:
            opt = bs.LORDS[name][1][k]
            install(name, opt_lord=opt)
            print(name, opt[0], flush=True)
        else:
            opt = bs.KINGS[name + "_c0"][1][k]
            for c in (0, 1, 2):
                n = install(f"{name}_c{c}", opt_king=opt)
                print(f"{name}_c{c}", opt[0], "tris", n, flush=True)
