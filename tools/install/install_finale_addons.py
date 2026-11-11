#!/usr/bin/env python3
"""終章の 8 体の作り込みのラフで選んだ案を、本物の模型へ仕上げて入れる (2026-11-11。ラフは tools/rough/lowpoly/finale_bosses/build.py)。

仕上げ (2026-11-11 の声「ラフレベルの見た目じゃない？ちゃんとアニメーションある？」):
  ・ 形: ラフの部品の作り (管・棘・球) の分け目を増やして、なめらかで密にする (FINE)
  ・ 揺れ・回り: 垂れる部品 (鎖・鈴・札・涙の滴) は自分の骨を持ち、吊る所を軸に揺れる。歯車・時計の針は回る (MOTION)。
    骨は模型 (lowpoly/<名前>.json) と骨組み (geo/entity/<名前>_lowpoly.geo.json と _debug) に足し、動きはその主のすべてのアニメに
    式 (query.anim_time) で足す (技の途中でも揺れが止まらない)
部品は付ける骨の名前で作ってあるので、その骨の空間へ戻して足す。絵は元の絵の右へ部品の絵を並べる。
同じ並びの絵 (段 _s1・_s2、燃え上がった姿 _flared、光の重ね _glow など) も揃えて広げ、段の絵では部品を雲と同じ割合で暗くする。
最初に流したときに元の模型・骨組み・絵を tools/sources/finale_addons_base/ へ写し、二度目からはそこから作り直す (かけ重ならない)。
技の動きの緩急 (tools/gen/anim/heart_moves.py) を流し直したら、そのあとでこれを流し直す (揺れの式を足し直す)。

    python -X utf8 -I tools/install/install_finale_addons.py
"""
from __future__ import annotations

import importlib.util
import json
import math
import re
import shutil
import sys
from pathlib import Path

import numpy as np

ROOT = Path(__file__).resolve().parents[2]
spec_ = importlib.util.spec_from_file_location("fb", ROOT / "tools/rough/lowpoly/finale_bosses/build.py")
fb = importlib.util.module_from_spec(spec_)
spec_.loader.exec_module(fb)
bs = fb.bs
from PIL import Image  # noqa: E402

A = ROOT / "src/main/resources/assets/skyabyss"
BASE = ROOT / "tools/sources/finale_addons_base"

# 選んだ案 (2026-11-11): 0 = A, 1 = B, 2 = C
PICKS = {"founder": 1, "last_bellkeeper": 1, "lamp_eye": 2, "rewinder": 0, "pilgrim_plume": 2, "pilgrim_hand": 2, "pilgrim_eye": 1, "pilgrim_moon": 1}
STAGE_K = {"": 1.0, "_s1": 0.84, "_s2": 0.66}
# 雲の喪章の主は光を白に (始祖は青緑のまま)
bs.PAL["g_cloud"] = [(200, 206, 226), (232, 240, 255), (255, 255, 255)]

# 揺れ・回り: 部品の名前 (正規表現) → 骨の名前の型。sway = 一番上を軸に揺れる (amp 度, freq 度/秒)、spin = 真ん中を軸に回る (axis, speed 度/秒)
MOTION = {
    "founder": [
        dict(re=r"^cl_(ch\d+|tongue|ball)$", bone="sw_clapper", kind="sway", amp=(9, 6), freq=110),
        dict(re=r"^hb_[tb](\d)", bone="sw_hipbell{0}", kind="sway", amp=(14, 10), freq=150),
    ],
    "last_bellkeeper": [
        dict(re=r"^h[tb](\d+)", bone="sw_hem{0}", kind="sway", amp=(12, 9), freq=130),
        dict(re=r"^sbb?(arm_[lr])(\d)", bone="sw_{0}_{1}", kind="sway", amp=(12, 9), freq=140),
    ],
    "rewinder": [
        dict(re=r"^g[hts]?(\d)", bone="sw_gear{0}", kind="spin", axis="z", speed=(-70, 95)),
    ],
    "pilgrim_plume": [
        dict(re=r"^(?:pc(\d)_\d+|pcb(\d)|tagi?(\d))", bone="sw_tag{0}", kind="sway", amp=(10, 12), freq=90),
    ],
    "pilgrim_eye": [
        dict(re=r"^t[dl](\d)", bone="sw_tear{0}", kind="sway", amp=(6, 5), freq=80),
    ],
    "pilgrim_moon": [
        dict(re=r"^hd(\d)$", bone="sw_hand{0}", kind="spin", axis="z", speed=(8, 40, 160), pivot_part="pin"),
        dict(re=r"^cg[t]?(\d)", bone="sw_cgear{0}", kind="spin", axis="z", speed=(60, -80)),
    ],
    # 月 (2026-11-11)
    "moon_core": [
        dict(re=r"^nvg?(\d)", bone="sw_nerve{0}", kind="sway", amp=(9, 9), freq=60),
    ],
    "full_keeper": [
        dict(re=r"^cage(\d)", bone="sw_orbring{0}", kind="spin", axis="y", speed=(40, -55)),
    ],
    # 章の道中 (2026-11-11)
    "elder_sentry": [
        dict(re=r"^ms(head|left_arm|right_arm)\d", bone="sw_moss_{0}", kind="sway", amp=(7, 6), freq=70),
    ],
    "whalebone_captain": [
        dict(re=r"^fr(arm[LR])\d", bone="sw_fringe_{0}", kind="sway", amp=(12, 10), freq=120),
        dict(re=r"^cape$", bone="sw_cape", kind="sway", amp=(7, 3), freq=60),
    ],
    "scorched_warden": [
        dict(re=r"^f[et](cloak_[lr])\d", bone="sw_feath_{0}", kind="sway", amp=(8, 5), freq=90),
    ],
    "tide_beckoner": [
        dict(re=r"^sail$", bone="sw_sail", kind="sway", amp=(5, 3), freq=45),
    ],
    # 残りのボス (2026-11-11)
    "lord_rib": [
        dict(re=r"^(?:st|l[gft])(\d)", bone="sw_lamp{0}", kind="sway", amp=(12, 10), freq=110),
    ],
}


# ---------------------------------------------------------------- 仕上げの形: 分け目を増やす
def _fine():
    b = fb.b
    tub0, cone0, ell0 = b.tub, b.cone, b.ell

    def tub(bone, m, pts, ra, rb=None, sides=6, hint=(1, 0, 0), phase=None, caps=(True, True)):
        return tub0(bone, m, pts, ra, rb, sides=max(sides, 7 if sides >= 5 else 5), hint=hint, phase=phase, caps=caps)

    def cone(bone, m, base, d, length, r, sides=4, rings=2, bend=(0, 0, 0), rb=None, hint=(0, 1, 0)):
        s2 = sides if (sides == 3 and rb is not None) else max(sides, 6 if sides >= 4 else 5)
        return cone0(bone, m, base, d, length, r, sides=s2, rings=max(rings, 3), bend=bend, rb=rb, hint=hint)

    def ell(bone, m, c, r, segs=8, rings=3, rx=0.0, ry=0.0, rz=0.0, deform=None):
        return ell0(bone, m, c, r, segs=max(segs, 12), rings=max(rings, 5), rx=rx, ry=ry, rz=rz, deform=deform)
    return (b, tub0, cone0, ell0), (tub, cone, ell)


def fine_parts(fn, R, glow_ramp):
    (b, tub0, cone0, ell0), (tub, cone, ell) = _fine()
    b.tub, b.cone, b.ell = tub, cone, ell
    try:
        return fb.parts(fn, R, glow_ramp)
    finally:
        b.tub, b.cone, b.ell = tub0, cone0, ell0


def suffixes(tex):
    """同じ並びの絵 (段 _s1・_s2、燃え上がった姿 _flared、光の絵 _glow など)。光の型 (_glowmask) は除く。"""
    out = {}
    for f in sorted((A / "textures/lowpoly").glob(f"{tex}*.png")):
        rest = f.stem[len(tex):]
        if rest.endswith("_glowmask") or (rest and not rest.startswith("_")):
            continue
        out[rest] = STAGE_K.get(rest, 1.0)
    for f in sorted(BASE.glob(f"{tex}*.png")) if BASE.exists() else []:
        rest = f.stem[len(tex):]
        if not rest.endswith("_glowmask") and (not rest or rest.startswith("_")):
            out.setdefault(rest, STAGE_K.get(rest, 1.0))
    return out


def geo_paths(stem):
    out = []
    for nm in (f"{stem}_lowpoly.geo.json", f"{stem}_lowpoly_debug.geo.json"):
        p = A / f"geo/entity/{nm}"
        if p.exists():
            out.append(p)
    return out


def keep(stem, tex):
    BASE.mkdir(parents=True, exist_ok=True)
    files = [A / f"lowpoly/{stem}.json"] + geo_paths(stem)
    for suf in suffixes(tex):
        files += [A / f"textures/lowpoly/{tex}{suf}.png", A / f"textures/lowpoly/{tex}{suf}_glowmask.png"]
    first = not (BASE / f"{stem}.json").exists()
    for f in files:
        dst = BASE / f.name
        # 元を写すのは初めて流した時だけ (あとから、入れた結果の光の型などを元として写さない)
        if f.exists() and not dst.exists() and (first or not f.name.endswith("_glowmask.png")):
            shutil.copy2(f, dst)


def group_of(stem, part):
    for spec in MOTION.get(stem, []):
        mt = re.match(spec["re"], part or "")
        if mt:
            g = [x for x in mt.groups() if x is not None]
            return spec, spec["bone"].format(*g)
    return None, None


def motion_tracks(stem, groups):
    """骨ごとの動きの式 (すべてのアニメに足す)。"""
    out = {}
    for i, (bone, (spec, _)) in enumerate(sorted(groups.items())):
        if spec["kind"] == "sway":
            ax, az = spec["amp"]
            f = spec["freq"] * (0.85 + 0.3 * ((i * 37) % 10) / 10)
            ph = (i * 71) % 360
            out[bone] = {"rotation": [f"math.sin(query.anim_time * {f:.0f} + {ph}) * {ax}", 0,
                                      f"math.sin(query.anim_time * {f * 0.83:.0f} + {(ph + 70) % 360}) * {az}"]}
        else:
            sp = spec["speed"]
            idx = int(re.findall(r"\d+", bone)[-1]) if re.findall(r"\d+", bone) else 0
            v = sp[idx % len(sp)] if isinstance(sp, (list, tuple)) else sp
            expr = f"query.anim_time * {v}"
            rot = {"x": [expr, 0, 0], "y": [0, expr, 0], "z": [0, 0, expr]}[spec["axis"]]
            out[bone] = {"rotation": rot}
    return out


def write_anims(stem, tracks):
    p = A / f"animations/entity/{stem}.animation.json"
    if not p.exists():
        return 0
    txt = p.read_text(encoding="utf-8")
    line1 = txt.splitlines()[1] if len(txt.splitlines()) > 1 else ""
    ind = len(line1) - len(line1.lstrip()) or None
    data = json.loads(txt)
    n = 0
    for anim in data.get("animations", {}).values():
        bones = anim.setdefault("bones", {})
        for k in [k for k in bones if k.startswith("sw_")]:
            del bones[k]
        for bone, tr in tracks.items():
            bones[bone] = tr
        n += 1
    p.write_text(json.dumps(data, ensure_ascii=False, indent=ind), encoding="utf-8")
    return n


def install(stem, pick, glow_ramp=None, base_dir=None):
    global BASE
    if base_dir is not None:
        BASE = base_dir
    cur = json.loads((A / f"lowpoly/{stem}.json").read_text(encoding="utf-8"))
    tex = cur.get("texture", stem)
    keep(stem, tex)
    data = json.loads((BASE / f"{stem}.json").read_text(encoding="utf-8"))
    title, body, metal, light, opts = fb.BOSSES[stem]
    label, fn = opts[pick]
    glow_ramp = glow_ramp or (light if stem == "founder" else "g_cloud")
    # 元の模型を組んで部品の置き場所を決める (骨組みは元の写しから)
    gpaths = geo_paths(stem)
    if gpaths:
        geo_doc = json.loads((BASE / gpaths[0].name).read_text(encoding="utf-8"))
    else:
        geo_doc = json.loads((A / f"geo/entity/{bs.GEO_ALIAS.get(stem, stem)}_lowpoly.geo.json").read_text(encoding="utf-8"))
    geo = geo_doc["minecraft:geometry"][0]
    xf = bs.he.rest_xf(geo)
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
    addf, adda, addg = fine_parts(fn, R, glow_ramp)
    size = adda.shape[0]
    img0 = np.array(Image.open(BASE / f"{tex}.png").convert("RGBA"))
    H, W = img0.shape[:2]
    NW = 1 << int(math.ceil(math.log2(W + size)))
    NH = 1 << int(math.ceil(math.log2(max(H, size))))
    for suf, k in suffixes(tex).items():
        tp = BASE / f"{tex}{suf}.png"
        if not tp.exists():
            continue
        img = np.array(Image.open(tp).convert("RGBA"))
        canvas = np.zeros((NH, NW, 4), np.uint8)
        canvas[:H, :W] = img
        part = adda.copy()
        part[..., :3] = np.where(addg[..., None], part[..., :3], np.clip(part[..., :3] * k, 0, 255)).astype(np.uint8)
        solid = img[..., 3] > 0
        if solid.any() and (img[..., :3][solid].max(axis=1) < 8).mean() > 0.7:
            # 光だけの重ねの絵 (黒 = 光らない。灰の鍛え手の _glow など): 部品は光る所だけ色、ほかは黒
            part[..., :3] = np.where(addg[..., None], adda[..., :3], 0).astype(np.uint8)
            part[..., 3] = 255
        canvas[:size, W:W + size] = part
        Image.fromarray(canvas, "RGBA").save(A / f"textures/lowpoly/{tex}{suf}.png")
        gp = BASE / f"{tex}{suf}_glowmask.png"
        if not gp.exists() and suf:
            stray = A / f"textures/lowpoly/{tex}{suf}_glowmask.png"
            if stray.exists():
                stray.unlink()
            continue
        gm = np.zeros((NH, NW, 4), np.uint8)
        if gp.exists():
            g = np.array(Image.open(gp).convert("RGBA"))
            gm[:H, :W] = g
        gm[:size, W:W + size][addg] = (255, 255, 255, 255)
        Image.fromarray(gm, "RGBA").save(A / f"textures/lowpoly/{tex}{suf}_glowmask.png")
    for b in data["bones"].values():
        b["uvs"] = [[round(u * W / NW, 6), round(v * H / NH, 6)] for u, v in b["uvs"]]
    # 揺れ・回りの骨: 部品の群れごとに、付ける骨の子の骨を作る (向きは無し。軸は吊る所・真ん中)
    groups = {}
    for f in addf:
        spec, gb = group_of(stem, getattr(f, "part", None))
        if gb:
            groups.setdefault(gb, (spec, f.bone, []))[2].append(f)
    pin = {}
    for f in addf:
        pin.setdefault(getattr(f, "part", None), []).append(f.verts)
    bone_of_face = {}
    new_bones = {}
    for gb, (spec, parent, fs) in groups.items():
        V = np.concatenate([f.verts for f in fs])
        if spec["kind"] == "sway":
            pv = V[np.argmax(V[:, 1])]
        elif spec.get("pivot_part") and spec["pivot_part"] in pin:
            pv = np.concatenate(pin[spec["pivot_part"]]).mean(0)
        else:
            pv = (V.min(0) + V.max(0)) / 2
        parent = parent if parent in data["bones"] else next(iter(data["bones"]))
        new_bones[gb] = (parent, [round(float(x), 4) for x in pv])
        for f in fs:
            bone_of_face[id(f)] = gb
    for gb, (parent, pv) in new_bones.items():
        data["bones"][gb] = {"parent": parent, "pivot": pv, "vertices": [], "uvs": [], "triangles": [], "triangle_count": 0}
    added = 0
    for f in addf:
        attach = f.bone if f.bone in data["bones"] else next(iter(data["bones"]))
        target = bone_of_face.get(id(f), attach)
        Rm, t = xf(attach)
        b = data["bones"][target]
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
    data["note_finale"] = f"{label} (tools/install/install_finale_addons.py)"
    (A / f"lowpoly/{stem}.json").write_text(json.dumps(data, ensure_ascii=False), encoding="utf-8")
    # 骨組みへ揺れの骨を足す
    for gp in gpaths:
        doc = json.loads((BASE / gp.name).read_text(encoding="utf-8"))
        bl = doc["minecraft:geometry"][0]["bones"]
        bl[:] = [x for x in bl if not x["name"].startswith("sw_")]
        for gb, (parent, pv) in sorted(new_bones.items()):
            bl.append({"name": gb, "pivot": pv, "parent": parent})
        gp.write_text(json.dumps(doc, ensure_ascii=False), encoding="utf-8")
    na = write_anims(stem, motion_tracks(stem, {k: (groups[k][0], None) for k in groups}))
    return label, added, len(new_bones), na


if __name__ == "__main__":
    sys.stdout.reconfigure(encoding="utf-8")
    for name, k in PICKS.items():
        label, n, nb, na = install(name, k)
        print(name, label, "tris", n, "motion bones", nb, "anims", na, flush=True)
