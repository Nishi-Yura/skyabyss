"""深淵 (五章) の敵のラフ用の共通部品。四章の ch4/ck4.py と同じ作り (出力先と色だけ替える)。

使い方 (敵 1 体 = 1 ファイル abyss/<id>.py):
    from ck_ab import *
    BONES / PAL / build(opt) -> (faces, eyes) / SPEC を書いて run(SPEC, build, PAL, BONES, ROWS)
    出力: docs/review/abyss_roughs/<id>.png・<id>_manifest.json・<id>/{a,b,c}/ (メッシュ・アトラス・光る画素のマスク)

ROWS は (見出し, 姿勢名, yaw, pitch, パネルの高さ, 中心, 縮尺, 見せる部品の名前の集合 or None) の列。
見せる部品を指定すると、その部品だけを描く (「見えていない時の痕」などに使う)。
座標: 1 単位 = 1/16 ブロック、y が上、前 = -Z。
mesh 検査は tools/mesh_audit.py の analyze (骨ごとに開いた辺・向きの食い違い・裏返り) を使う。
"""
from __future__ import annotations

import json
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

AB = Path(__file__).resolve().parent
LOW = AB.parent
ROOT = AB.parents[3]
sys.path.insert(0, str(LOW / "ch4"))
sys.path.insert(0, str(ROOT / "tools"))
import ck4 as _c4  # noqa: E402,F401  (ch1 の ck と ck4 の色・パッチを読み込む)
from ck4 import *  # noqa: E402,F401
import ck as _ck  # noqa: E402
import mesh_audit as MA  # noqa: E402

OUT = ROOT / "docs/review/abyss_roughs"
_ck.OUT = OUT
_ck.ra.PUPIL = np.array([(90, 190, 176), (200, 236, 228)])   # 深淵の目は冷たい青緑

# ---- 深淵の色の言葉: 灰の層は黒石・玄武・熾の橙。深みと深淵だけは深い石板・スカルクの青緑・骨の白・虚の黒 (明るい色は使わない)
SLATE = [(12, 16, 24), (26, 34, 48), (46, 58, 76), (72, 86, 106), (100, 114, 134)]
TEAL = [(4, 20, 24), (10, 40, 46), (18, 70, 74), (34, 108, 104)]
BONEW = [(34, 32, 36), (70, 66, 64), (110, 104, 96), (150, 144, 132)]
VOID = [(2, 2, 4), (6, 6, 10), (14, 14, 22)]
FLESH = [(30, 14, 18), (60, 26, 30), (96, 46, 48), (132, 72, 66)]
GLOW_TEAL = [(40, 130, 120), (80, 190, 176)]


def V_(*a):
    return np.array(a, float)


def eye(x, y, z, r=0.3):
    return dict(c=V_(x, y, z), rx=r, ry=r, pr=(0.5, 0.5), tilt=0.0, zr=0.6)


def cone(bone, mat, base, direction, length, r, sides=4, rings=2, bend=(0, 0, 0)):
    """根元に板を張った先細りの棘 (根元の穴を塞ぐ。水密にするため spike ではなくこちらを使う)。"""
    d = np.asarray(direction, float)
    d = d / np.linalg.norm(d)
    base = np.asarray(base, float)
    path = [base + d * length * (i / rings) + np.asarray(bend, float) * (i / rings) ** 2 for i in range(rings + 1)]
    ras = [r * (1 - i / rings) ** 0.8 for i in range(rings + 1)]
    ras[-1] = 0.0 if rings >= 1 else r
    return tube(bone, mat, path, ras, sides=sides, caps=(True, False))


def audit(result):
    """骨ごとに mesh_audit.analyze を当てる。戻り値: {骨: (開いた辺, 多様体でない辺, 向きの食い違い, つぶれた面, 裏返り)}。"""
    out = {}
    for bname, b in result["data"]["bones"].items():
        if not b["triangles"]:
            continue
        Vv = np.array(b["vertices"], float)
        Tt = np.array(b["triangles"], int)
        a = MA.analyze(Vv, Tt)
        out[bname] = (a["open"], a["nonman"], a["wind"], a["degen"], a["inv"])
    return out


def float_check(faces, exempt=()):
    """浮いた部品の検査。部品 (part 名) ごとに、頂点のどれかが他の部品の頂点から 1.0 単位以内にあるか、
    面の中心が他の部品の凸包の中にあれば「接している」とみなす。どちらもなければ浮いている。
    exempt の部品 (床の痕など、本体に付かないもの) は見ない。戻り値: 浮いた部品の名前の一覧。"""
    from scipy.spatial import cKDTree, Delaunay
    parts = {}
    for f in faces:
        parts.setdefault(getattr(f, "part", f.bone), []).append(f)
    if len(parts) <= 1:
        return []
    names = [k for k in parts if k not in exempt]
    allv = []
    owner = []
    for k in parts:
        for f in parts[k]:
            for v in f.verts:
                allv.append(v)
                owner.append(k)
    allv = np.array(allv)
    owner = np.array(owner)
    tree = cKDTree(allv)
    floating = []
    hulls = {}
    for k in parts:
        v = np.vstack([f.verts for f in parts[k]])
        try:
            hulls[k] = Delaunay(v)
        except Exception:
            hulls[k] = None
    for k in names:
        vv = np.vstack([f.verts for f in parts[k]])
        cen = np.array([f.verts.mean(0) for f in parts[k]])
        touch = False
        d, idx = tree.query(vv, k=min(len(allv), 40), distance_upper_bound=1.0)
        for row_d, row_i in zip(d, idx):
            ok = [i for dd, i in zip(row_d, row_i) if np.isfinite(dd) and i < len(owner) and owner[i] != k]
            if ok:
                touch = True
                break
        if not touch:
            for o in parts:
                if o == k or hulls.get(o) is None or o in exempt:
                    continue
                if (hulls[o].find_simplex(cen) >= 0).any():
                    touch = True
                    break
        if not touch:
            floating.append(k)
    return floating


def bbox_float(faces, exempt=(), tol=0.8):
    """部品ごとの箱 (バウンディングボックス) を tol だけ広げて、どの他の部品の箱とも重ならないものを返す。"""
    parts = {}
    for f in faces:
        parts.setdefault(getattr(f, "part", f.bone), []).append(f)
    if len(parts) <= 1:
        return []
    box = {}
    for k, fs in parts.items():
        v = np.vstack([f.verts for f in fs])
        box[k] = (v.min(0), v.max(0))
    out = []
    for k in parts:
        if k in exempt:
            continue
        lo, hi = box[k][0] - tol, box[k][1] + tol
        hit = False
        for o in parts:
            if o == k:
                continue
            olo, ohi = box[o]
            if np.all(lo <= ohi) and np.all(olo <= hi):
                hit = True
                break
        if not hit:
            out.append(k)
    return out


def sheet(spec, results, bones, rows, out_png):
    F = _ck.voxel.font
    n = len(results)
    PW = spec.get("pw", 400)
    GAP, X0 = 14, 20
    W = X0 * 2 + n * PW + (n - 1) * GAP
    top = 110
    total = top + sum(34 + r[4] + 34 for r in rows) + 40 + spec.get("atlas_h", 300) + 80
    cv = Image.new("RGB", (W, total), (16, 16, 22))
    d = ImageDraw.Draw(cv)
    d.text((24, 14), spec["title"], font=F(26), fill=(236, 232, 218))
    for i, line in enumerate(spec.get("sub", [])):
        d.text((24, 50 + 20 * i), line, font=F(15), fill=(160, 160, 176))
    y = top
    for head, pose, yaw, pitch, ph, cen, sc, only in rows:
        d.text((X0, y), head, font=F(20), fill=(150, 232, 255))
        y += 30
        for i, r in enumerate(results):
            x = X0 + i * (PW + GAP)
            rr = dict(r)
            if only is not None:
                rr["faces"] = [f for f in r["faces"] if getattr(f, "part", f.bone) in only]
            im = _ck.render_view(rr, r["atlas"], r["glow"], spec["poses"][pose], bones, yaw, pitch, (PW, ph), cen, sc)
            cv.paste(im, (x, y))
            d.rectangle([x - 1, y - 1, x + PW, y + ph], outline=(60, 60, 78))
            d.text((x + 2, y + ph + 6), f'{spec["labels"][i]}  {r["tris"]} 三角形', font=F(17), fill=(150, 232, 255))
        y += ph + 34
    d.text((X0, y), "テクスチャのアトラス", font=F(20), fill=(150, 232, 255))
    y += 30
    for i, r in enumerate(results):
        x = X0 + i * (PW + GAP)
        s = r["size"]
        k = max(1, min(PW // s, 3))
        at = Image.fromarray(r["atlas"], "RGBA").resize((s * k, s * k), Image.NEAREST)
        bgp = Image.new("RGB", (s * k, s * k), (34, 34, 46))
        bgp.paste(at, (0, 0), at)
        cv.paste(bgp, (x, y))
        d.text((x + 2, y + s * k + 4), f'{spec["labels"][i]} アトラス {s}x{s}', font=F(15), fill=(160, 160, 176))
    hmax = max(r["size"] * max(1, min(PW // r["size"], 3)) for r in results)
    cv = cv.crop((0, 0, W, min(total, y + hmax + 40)))
    cv.save(out_png)


def run(spec, build, pal, bones, rows, hint=128):
    OUT.mkdir(parents=True, exist_ok=True)
    results = []
    for k in "abc":
        faces, eyes = build(k)
        d = OUT / spec["id"] / k
        d.mkdir(parents=True, exist_ok=True)
        size, P, N, M, L = _ck.build_atlas(faces, hint, 0)
        atlas, glow = _ck.paint(faces, size, P, N, M, pal, eyes)
        Image.fromarray(atlas, "RGBA").save(d / f"{spec['id']}_{k}_atlas.png")
        gm = np.zeros((size, size, 4), np.uint8)
        gm[glow] = (255, 255, 255, 255)
        Image.fromarray(gm, "RGBA").save(d / f"{spec['id']}_{k}_glowmask.png")
        data = _ck.export_mesh(faces, size, d / f"{spec['id']}_{k}_mesh.json", f"{spec['id']}_{k}",
                               f"{spec['id']}_{k}_atlas.png", bones, {"glowmask": f"{spec['id']}_{k}_glowmask.png"})
        bad = _ck.check_buried(faces)
        r = dict(key=k, faces=faces, eyes=eyes, size=size, atlas=atlas, glow=glow, data=data, buried=bad,
                 tris=data["triangle_count"])
        r["audit"] = audit(r)
        r["floating"] = float_check(faces, spec.get("exempt", ()))
        r["boxfloat"] = bbox_float(faces, spec.get("exempt", ()))
        results.append(r)
        print(spec["id"], k, "tris", r["tris"], "atlas", size, "buried", bad, "floating", r["floating"], "boxfloat", r["boxfloat"], "audit",
              {b: v for b, v in r["audit"].items() if any(v)})
    sheet(spec, results, bones, rows, OUT / f'{spec["id"]}.png')
    write_manifest(spec, results)
    return results


def write_manifest(spec, results):
    opts, parts = {}, []
    for k, r in zip("abc", results):
        o = spec["options"][k]
        opts[k] = {"name": o["name"] + f' ({r["tris"]} 三角形)', "desc": o["desc"]}
        bad_b = ", ".join(f"{a}-{b}:{f}" for a, b, f in r["buried"]) or "なし"
        bad_o = {b: v for b, v in r["audit"].items() if any(v)}
        open_txt = "開いた辺・裏返しなし" if not bad_o else "; ".join(
            f"{b} 開{v[0]}・多{v[1]}・向{v[2]}・つぶれ{v[3]}・裏返し{v[4]}" for b, v in bad_o.items())
        parts.append(f"{k.upper()} 埋まり {bad_b} / 水密 {open_txt} / 浮き {len(r['floating'])}" + (f" ({', '.join(r['floating'])})" if r['floating'] else "") + f" / 箱の浮き {len(r['boxfloat'])}" + (f" ({', '.join(r['boxfloat'])})" if r['boxfloat'] else ""))
    extra = (spec["extra"] + " 三角形 (全身): " + " / ".join(f'{k.upper()} {r["tris"]}' for k, r in zip("abc", results))
             + f' (目安 {spec["budget"]})。検査: ' + " / ".join(parts)
             + f' 。メッシュ・アトラス・光る画素のマスクは docs/review/abyss_roughs/{spec["id"]}/{{a,b,c}}/。生成: tools/rough/lowpoly/abyss/{spec["id"]}.py。')
    if spec.get("later"):
        extra += " 深い層の変わり果てた姿 (歪み・崩れ、今は作らない): " + " / ".join(
            f"{k.upper()} {t}" for k, t in spec["later"].items()) + "。"
    ent = {"id": "ab_" + spec["id"], "short": spec["short"], "kind": spec["kind"], "title": spec["title"],
           "sheets": [f'abyss_roughs/{spec["id"]}.png'], "extra": extra, "options": opts}
    (OUT / f'{spec["id"]}_manifest.json').write_text(json.dumps(ent, ensure_ascii=False, indent=1), encoding="utf-8")
    return ent
