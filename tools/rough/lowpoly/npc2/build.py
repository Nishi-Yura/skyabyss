"""NPC v2 の書き出しと見本の絵。cast.py の人を組んで、メッシュ・アトラス・持ち物のメッシュを docs/review/npc_models_v2/roughs/<名>/ へ書く。

  python -I tools/rough/lowpoly/npc2/build.py [名 ...]        (名を省くと全員)
  -> docs/review/npc_models_v2/roughs/<名>/<名>_mesh.json, <名>_atlas.png, <名>_<受け口>_mesh.json, <名>_<受け口>_atlas.png
     docs/review/npc_models_v2/roughs/<名>.png (前・3/4・横・後ろ・顔・話す姿)
     docs/review/npc_models_v2/roughs/report.json (三角形の数・埋まった部品)
"""
from __future__ import annotations

import copy
import json
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
import kit as K  # noqa: E402
import cast as C  # noqa: E402

sys.path.insert(0, str(K.LP / "ch1"))
import ck as CK  # noqa: E402
import voxel  # noqa: E402

OUT = K.ROOT / "docs/review/npc_models_v2/roughs"


def export(faces, size, path, name, bones, shift=None):
    bs = {b: {"vertices": [], "uvs": [], "triangles": []} for b in bones}
    sh = np.zeros(3) if shift is None else np.asarray(shift, float)
    for f in faces:
        b = bs[f.bone]
        base = len(b["vertices"])
        cen = f.uv.mean(0)
        for p, uv in zip(f.verts, f.uv):
            d = cen - uv
            nn = np.linalg.norm(d)
            uv = uv + d / nn * min(K.INSET, nn * 0.4) if nn > 1e-9 else uv
            b["vertices"].append([round(float(x) + 0.0, 4) for x in (p - sh)])
            b["uvs"].append([round(float(uv[0]) / size, 5), round(float(uv[1]) / size, 5)])
        for a, c, d in f.tris():
            b["triangles"].append([base + a, base + c, base + d])
    tot, out = 0, {}
    for k, (parent, pivot) in bones.items():
        b = bs[k]
        b["triangle_count"] = len(b["triangles"])
        tot += b["triangle_count"]
        out[k] = {"parent": parent, "pivot": [float(x) for x in pivot], **b}
    if "root" in out:
        out["root"]["rotation"] = [0.0, 0.0, 0.0]
    data = {"format_version": 1, "name": name, "units": "1/16 block, y up, front = -Z", "uv": "normalized, origin top-left (v down)",
            "texture": name, "texture_size": [size, size], "texels_per_unit": K.TEX, "uv_inset_texels": K.INSET,
            "triangle_count": tot, "bones": out}
    Path(path).write_text(json.dumps(data, ensure_ascii=False), encoding="utf-8")
    return data


def paint(c, faces, hint=128):
    size, P, N, M, L = K.build_atlas(faces, hint, 0)
    pal = K.palette(c)
    import props as PR
    for k, v in list(PR.PROP_MATS.items()) + list(PR.LANTERN_MATS.items()):
        pal["mats"].setdefault(k, v)
    pal["mats"].setdefault("book2", K.cloth(tuple(int(x * 0.6) for x in c["pal"].get("book", (120, 44, 40)))))
    pal["glow_mats"] = list(pal.get("glow_mats", [])) + ["flame", "lglass"]
    c["_pal_face"] = K.face_palette(c)

    def ex(ctx, col, glow):
        return K.face_paint(c, ctx, col, glow)
    atlas, glow = K.paint_st(faces, size, P, N, M, pal, (), ex, L)
    return size, atlas, glow


HEAD_K = 1.12
HEAD_PIVOT = np.array([0.0, 21.6, 0.0])


def grow(c, faces):
    """頭の骨 (頭・髪・かぶり物) を首の付け根を中心に大きくする (語り部・鯨追いと同じ頭の大きさ)。
    絵は元の大きさで描いてから形だけ広げる (UV はそのまま。頭のテクセルが少し粗くなるだけ)。"""
    k = c.get("head_k", HEAD_K)
    out = []
    for f in faces:
        if f.bone in ("head", "hairB"):
            g = copy.copy(f)
            g.verts = HEAD_PIVOT + (f.verts - HEAD_PIVOT) * k
            out.append(g)
        else:
            out.append(f)
    return out


def grow_bones(c, bones):
    k = c.get("head_k", HEAD_K)
    b = dict(bones)
    par, piv = b["hairB"]
    b["hairB"] = (par, tuple(np.round(HEAD_PIVOT + (np.array(piv, float) - HEAD_PIVOT) * k, 4)))
    return b


def build(name):
    c = copy.deepcopy(C.CAST[name])
    c["name"] = name
    c["pal"].update(c.get("pal_extra", {}))
    c["face"] = dict(c.get("face", {}))
    if c.get("beard") == "stubble":
        c["face"]["stubble"] = True
    c["hair_col"] = c["pal"]["hair"]
    body = K.build_body(c)
    bones = K.bones_for(c)
    props = {}
    for sock, fn in c.get("props", {}).items():
        pf = fn(c)
        for f in pf:
            f.bone = sock
        props[sock] = pf
    return c, body, bones, props


def write(name):
    c, body, bones, props = build(name)
    d = OUT / name
    d.mkdir(parents=True, exist_ok=True)
    size, atlas, glow = paint(c, body, 256)
    Image.fromarray(atlas, "RGBA").save(d / f"{name}_atlas.png")
    bones = grow_bones(c, bones)
    data = export(grow(c, body), size, d / f"{name}_mesh.json", name, bones)
    rep = {"tris": data["triangle_count"], "atlas": size, "buried": CK.check_buried(body), "props": {}}
    res = dict(c=c, body=body, bones=bones, atlas=atlas, glow=glow, props={})
    for sock, pf in props.items():
        ps, pa, pg = paint(c, pf, 64)
        pn = f"{name}_{sock}"
        Image.fromarray(pa, "RGBA").save(d / f"{pn}_atlas.png")
        gp = d / f"{pn}_glowmask.png"
        if pg.any():
            gm = np.zeros((ps, ps, 4), np.uint8)
            gm[pg] = (255, 255, 255, 255)
            Image.fromarray(gm, "RGBA").save(gp)
        elif gp.exists():
            gp.unlink()
        pdata = export(pf, ps, d / f"{pn}_mesh.json", pn, {sock: (None, (0.0, 0.0, 0.0))}, shift=bones[sock][1])
        rep["props"][sock] = {"tris": pdata["triangle_count"], "atlas": ps}
        res["props"][sock] = (pf, pa, pg)
    return res, rep


# ====================================================================== 見本の絵

def combined(res, pose):
    """体と持ち物を一つのアトラスに描き直した見本用の面 (絵の出方は書き出しとほぼ同じ)。"""
    c = res["c"]
    faces = list(res["body"])
    for sock, (pf, pa, pg) in res["props"].items():
        faces += pf
    faces = [copy.copy(f) for f in faces]
    for f in faces:
        f.verts = f.verts.copy()
    size, atlas, glow = paint(c, faces, 256)
    return grow(c, faces), atlas, glow


def view(faces, atlas, glow, bones, pose, yaw, pitch, size, center, scale):
    fs = CK.pose_faces(faces, pose, bones)
    img = K.render(fs, atlas, glow, yaw, pitch, size, center, scale)
    bg = Image.fromarray(np.clip(voxel._background(*size, "dark"), 0, 255).astype(np.uint8))
    bg.paste(img, (0, 0), img)
    return bg


TALK = {"armR": (38, 0, 8), "foreR": (40, 0, 0), "head": (-4, 10, 0)}


def sheet(name, res):
    faces, atlas, glow = combined(res, {})
    bones = res["bones"]
    W, H = 300, 400
    views = [("前", 180, 6, {}), ("3/4", 212, 10, {}), ("横", 270, 6, {}), ("後ろ 3/4", 32, 10, {}), ("話す", 200, 8, TALK)]
    cv = Image.new("RGB", (W * (len(views) + 1), H + 40), (16, 16, 22))
    d = ImageDraw.Draw(cv)
    d.text((10, 8), f"{C.CAST[name].get('ja', name)}  ({name})", font=voxel.font(22), fill=(236, 232, 218))
    for i, (t, yaw, pit, pose) in enumerate(views):
        im = view(faces, atlas, glow, bones, pose, yaw, pit, (W, H), (0, 16.0, 0), 11.0)
        cv.paste(im, (i * W, 40))
        d.text((i * W + 8, 44), t, font=voxel.font(16), fill=(150, 232, 255))
    im = view(faces, atlas, glow, bones, {}, 190, 4, (W, H), (0, 25.2, 0), 30.0)
    cv.paste(im, (len(views) * W, 40))
    cv.save(OUT / f"{name}.png")


def main(names):
    OUT.mkdir(parents=True, exist_ok=True)
    rp = OUT / "report.json"
    report = json.loads(rp.read_text(encoding="utf-8")) if rp.exists() else {}
    for n in names:
        res, rep = write(n)
        sheet(n, res)
        report[n] = rep
        print(n, rep["tris"], "tris", rep["atlas"], "px", "buried", rep["buried"], {k: v["tris"] for k, v in rep["props"].items()})
    rp.write_text(json.dumps(report, ensure_ascii=False, indent=1), encoding="utf-8")


if __name__ == "__main__":
    main(sys.argv[1:] or list(C.CAST))
