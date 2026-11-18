"""NPC の持ち物 v3 (props_v3.py) を書き出してゲームへ入れる。体には触れない (持ち物の受け口のメッシュと絵だけ)。

    python -X utf8 -I tools/rough/lowpoly/npc2/build_props_v3.py [人 ...] [--no-install]

人を省くと、いま入っている持ち物 (assets/skyabyss/lowpoly/npc_<人>_<受け口>.json) の全員。
入れる物: lowpoly/npc_<人>_<受け口>.json (受け口の pivot からの相対・水密に閉じる)、textures/lowpoly/npc_<人>_<受け口>.png (+ _glow.png)。
見本の絵: docs/review/gear_props/npc_props_<番号>.png
"""
from __future__ import annotations

import copy
import json
import os
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
import build as B  # noqa: E402
import cast as C  # noqa: E402
import cast_folk  # noqa: E402,F401
import cast_hamlet  # noqa: E402,F401
import cast_moon  # noqa: E402,F401
import kit as K  # noqa: E402
import props as PR  # noqa: E402
import props_v3 as P3V  # noqa: E402

ROOT = K.ROOT
sys.path.insert(0, str(ROOT / "tools/lib"))
sys.path.insert(0, str(ROOT / "tools/install"))
import mesh_close as MC  # noqa: E402
import install_npc2 as IN  # noqa: E402
import voxel  # noqa: E402

ASSETS = ROOT / "src/main/resources/assets/skyabyss"
WORK = ROOT / "build/npc_props_v3"
REVIEW = ROOT / "docs/review/gear_props"


def paint(c, faces, hint=128):
    size, P, N, M, L = K.build_atlas(faces, hint, 0)
    pal = K.palette(c)
    for k, v in list(PR.PROP_MATS.items()) + list(PR.LANTERN_MATS.items()) + list(P3V.V3_MATS.items()):
        pal["mats"].setdefault(k, v)
    pal["mats"].setdefault("book2", K.cloth(tuple(int(x * 0.6) for x in c["pal"].get("book", (120, 44, 40)))))
    pal["glow_mats"] = list(pal.get("glow_mats", [])) + ["flame", "lglass"] + P3V.V3_GLOW
    c["_pal_face"] = K.face_palette(c)

    def ex(ctx, col, glow):
        return K.face_paint(c, ctx, col, glow)
    atlas, glow = K.paint_st(faces, size, P, N, M, pal, (), ex, L)
    return size, atlas, glow


def installed():
    out = []
    for n in sorted(os.listdir(ASSETS / "lowpoly")):
        if n.startswith("npc_") and "_prop_" in n and n.endswith(".json"):
            who, sock = n[4:-5].rsplit("_prop_", 1)
            out.append((who, "prop_" + sock))
    return out


def spec(who):
    c = copy.deepcopy(C.CAST[who])
    c["name"] = who
    c["pal"].update(c.get("pal_extra", {}))
    c["face"] = dict(c.get("face", {}))
    c["hair_col"] = c["pal"]["hair"]
    return c


def build_one(who, sock):
    c = spec(who)
    fn = c["props"][sock]
    nf = P3V.new_fn(fn)
    if nf is None:
        raise SystemExit(f"no v3 for {fn.__name__} ({who} {sock})")
    faces = nf(c)
    for f in faces:
        f.bone = sock
    bones = K.bones_for(c)
    return c, faces, bones


def install(who, sock, c, faces, bones):
    pn = f"{who}_{sock}"
    d = WORK / who
    d.mkdir(parents=True, exist_ok=True)
    size, atlas, glow = paint(c, faces, 128)
    Image.fromarray(atlas, "RGBA").save(d / f"{pn}_atlas.png")
    mask = d / f"{pn}_glowmask.png"
    if glow.any():
        gm = np.zeros((size, size, 4), np.uint8)
        gm[glow] = (255, 255, 255, 255)
        Image.fromarray(gm, "RGBA").save(mask)
    elif mask.exists():
        mask.unlink()
    data = B.export(faces, size, d / f"{pn}_mesh.json", pn, {sock: (None, (0.0, 0.0, 0.0))}, shift=bones[sock][1])
    full = "npc_" + pn
    dst = ASSETS / "lowpoly" / (full + ".json")
    g = json.loads((d / f"{pn}_mesh.json").read_text(encoding="utf-8"))
    g["socket"] = True
    g["name"] = full
    g["texture"] = full
    IN.write_json(str(dst), g)
    MC.close_mesh_file(str(dst), with_collar=False, force=True)
    tex = ASSETS / "textures/lowpoly"
    import shutil
    shutil.copyfile(d / f"{pn}_atlas.png", tex / (full + ".png"))
    gl = tex / (full + "_glow.png")
    lit = IN.bake_glow(str(d / f"{pn}_atlas.png"), str(mask), str(gl)) if mask.exists() else 0
    if not lit and gl.exists():
        gl.unlink()
    return data["triangle_count"], size, atlas, glow


def main(args):
    P3V.patch_lantern()
    todo = installed()
    names = [a for a in args if not a.startswith("--")]
    if names:
        todo = [(w, s) for w, s in todo if w in names]
    tiles = []
    skipped = []
    for who, sock in todo:
        if who not in C.CAST or sock not in C.CAST[who].get("props", {}):
            skipped.append(f"{who}_{sock}")
            continue
        c, faces, bones = build_one(who, sock)
        if "--no-install" in args:
            size, atlas, glow = paint(c, faces, 128)
            tris = sum(len(f.tris()) for f in faces)
        else:
            tris, size, atlas, glow = install(who, sock, c, faces, bones)
        V = np.concatenate([f.verts for f in faces])
        lo, hi = V.min(0), V.max(0)
        cen = (lo + hi) / 2
        sc = 200 / max(1.0, float(np.max(hi - lo)))
        img = K.render(faces, atlas, glow, 215, 16, (240, 240), cen, sc)
        tiles.append((f"{who} {sock}", tris, img))
        print(who, sock, tris, size, flush=True)
    REVIEW.mkdir(parents=True, exist_ok=True)
    per, cols = 24, 6
    for k in range(0, len(tiles), per):
        chunk = tiles[k:k + per]
        rows = (len(chunk) + cols - 1) // cols
        cv = Image.new("RGB", (cols * 250 + 10, rows * 270 + 10), (16, 16, 22))
        dr = ImageDraw.Draw(cv)
        for i, (label, tris, img) in enumerate(chunk):
            x, y = 10 + (i % cols) * 250, 10 + (i // cols) * 270
            dr.text((x, y), f"{label[:26]} {tris}", font=voxel.font(12), fill=(150, 232, 255))
            cv.paste(img, (x, y + 22), img)
        cv.save(REVIEW / f"npc_props_{k // per + 1:02d}.png")
    if skipped:
        print("skipped (not in the cast):", skipped)


if __name__ == "__main__":
    sys.stdout.reconfigure(encoding="utf-8")
    main(sys.argv[1:])
