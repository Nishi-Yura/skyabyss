"""一章 (岩鯨) の新しい敵・人物・装備・アイコンのラフ用の共通部品。

使い方 (モブ 1 体 = 1 ファイル ch1/<id>.py):
    from ck import *
    BONES = {"root": (None, (0,0,0)), "body": ("root", (0,8,0)), ...}   # 骨の名前・親・pivot (作図空間)
    PAL = {"mats": {...}}                      # 材質ごとの色の階調 (ra_common.paint と同じ書式)
    def build(opt):  -> (faces, eyes)          # opt = "a"/"b"/"c"。part() で部品に名前を付ける
    POSES = {"idle": {}, "telegraph": {"body": (rx, ry, rz)}, ...}
    SPEC = dict(id=..., title=..., center=(x,y,z), scale=..., ...)
    run(SPEC)     # シート・メッシュ・アトラス・manifest の断片を書く

座標: 1 単位 = 1/16 ブロック、y が上、前 = -Z。テクスチャは 1 ブロック = 32 テクセル (lpkit.TEX = 2)。
"""
from __future__ import annotations

import copy
import importlib.util
import json
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

CH1 = Path(__file__).resolve().parent
LP = CH1.parent
ROOT = CH1.parents[3]
sys.path.insert(0, str(LP))
sys.path.insert(0, str(ROOT / "tools/rough"))
from lpkit import *  # noqa
from lpkit import _mk  # noqa
import voxel  # noqa

# rib_archer の汎用の描き (paint) を別名で読み込む (common という名前は各所にあるので衝突を避ける)
_spec = importlib.util.spec_from_file_location("ra_common", LP / "rib_archer" / "common.py")
ra = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(ra)
paint, hash1, hash3, vnoise, BAYER = ra.paint, ra.hash1, ra.hash3, ra.vnoise, ra.BAYER
ra.PUPIL = np.array([(240, 160, 46), (255, 222, 130)])   # 一章の目は琥珀

OUT = ROOT / "docs/review/ch1_roughs"
INSET = 0.02

# ---- 一章 (岩鯨) の色の言葉: 石・骨・苔・琥珀
STONE = [(30, 34, 42), (56, 62, 74), (84, 92, 104), (116, 126, 138), (148, 158, 168), (182, 190, 198)]      # 灰青の岩殻
BONE = [(36, 32, 34), (82, 72, 62), (128, 114, 92), (170, 156, 124), (206, 194, 160), (234, 226, 198)]
MOSS = [(22, 38, 28), (42, 64, 36), (70, 98, 46), (106, 136, 66)]
BROWN = [(30, 22, 18), (58, 42, 32), (90, 66, 46), (124, 94, 64)]                                            # 茶の岩殻の割れ目・革
AMBER = [(110, 54, 12), (190, 100, 22), (240, 160, 46), (255, 214, 120)]
DARK = [(8, 8, 16), (18, 18, 28), (32, 32, 44)]


def V(*a):
    return np.array(a, float)


def part(pid, faces):
    """面の一覧に部品の名前を付ける (重なりの検査用)。"""
    for f in faces:
        f.part = pid
    return faces


def mat(ramp, base=0.7, **kw):
    return dict(ramp=ramp, base=base, **kw)


def limb(bone, m, pts, ra_, rb=None, sides=5, hint=(1, 0, 0), **kw):
    return tube(bone, m, pts, ra_, rb if rb is not None else ra_, sides=sides, hint=hint, **kw)


# ====================================================================== 姿勢 (骨ごとの剛体の回転)

def rot_xyz(rx, ry, rz):
    return rot_z(rz) @ rot_y(ry) @ rot_x(rx)


def _refresh(g, f):
    k = len(g.verts)
    n = np.cross(g.verts[2] - g.verts[0], g.verts[3 % k] - g.verts[1]) if k == 4 else \
        np.cross(g.verts[1] - g.verts[0], g.verts[2] - g.verts[0])
    n = nrm(n)
    if np.dot(n, f.normal) < 0:
        n = -n
    g.normal = n


def pose_point(p, bone, pose, bones):
    while bone is not None:
        parent, pivot = bones[bone]
        pv = np.array(pivot, float)
        a = np.array(pose.get(bone, (0, 0, 0)), float)
        if a.any():
            p = rot_xyz(*a) @ (p - pv) + pv
        bone = parent
    return p


def pose_faces(faces, pose, bones):
    if not pose:
        return faces
    out = []
    for f in faces:
        g = copy.copy(f)
        g.verts = np.array([pose_point(p, f.bone, pose, bones) for p in f.verts])
        _refresh(g, f)
        out.append(g)
    return out


# ====================================================================== 重なりの検査 (埋まった部品を探す)

def check_buried(faces, thr=0.45, tol=0.8):
    """部品 (part の名前) ごとに面の中心・頂点をとり、別の部品の凸包の中に tol 単位より深く入った割合を出す。
    割合が thr を超えた部品の組 (小さい方が埋まっている) を返す。戻り値: [(埋まった部品, 包む部品, 割合)]。
    骨の根元が体の表面に刺さる程度 (棘の根元) は許す。"""
    from scipy.spatial import ConvexHull, Delaunay
    parts = {}
    for f in faces:
        parts.setdefault(getattr(f, "part", f.bone), []).append(f)
    pts, hulls = {}, {}
    for k, fs in parts.items():
        v = np.vstack([f.verts for f in fs])
        samp = np.vstack([f.verts.mean(0)[None] for f in fs] + [f.verts for f in fs])
        pts[k] = samp
        try:
            hulls[k] = (Delaunay(v), ConvexHull(v), v)
        except Exception:
            hulls[k] = None
    bad = []
    keys = list(parts)
    for a in keys:
        for b in keys:
            if a == b or hulls[b] is None:
                continue
            tri, hull, v = hulls[b]
            # a が小さい方の部品だけ見る (同じ大きさなら体積の小さい方)
            if np.ptp(pts[a], 0).prod() > np.ptp(v, 0).prod():
                continue
            inside = tri.find_simplex(pts[a]) >= 0
            if not inside.any():
                continue
            # 凸包の表面からの深さ
            eq = hull.equations
            depth = -(pts[a][inside] @ eq[:, :3].T + eq[:, 3]).max(1)
            deep = (depth > tol).sum()
            frac = deep / len(pts[a])
            if frac > thr:
                bad.append((a, b, round(float(frac), 2)))
    return bad


# ====================================================================== 作る・書き出す

def export_mesh(faces, size, path, name, texture, bones, extra=None):
    bs = {b: {"vertices": [], "uvs": [], "triangles": []} for b in bones}
    for f in faces:
        b = bs[f.bone]
        base = len(b["vertices"])
        cen = f.uv.mean(0)
        for p, uv in zip(f.verts, f.uv):
            d = cen - uv
            nn = np.linalg.norm(d)
            uv = uv + d / nn * min(INSET, nn * 0.4) if nn > 1e-9 else uv
            b["vertices"].append([round(float(x) + 0.0, 4) for x in p])
            b["uvs"].append([round(float(uv[0]) / size, 5), round(float(uv[1]) / size, 5)])
        for a, c, d in f.tris():
            b["triangles"].append([base + a, base + c, base + d])
    tot, out = 0, {}
    for k, (parent, pivot) in bones.items():
        b = bs[k]
        b["triangle_count"] = len(b["triangles"])
        tot += b["triangle_count"]
        out[k] = {"parent": parent, "pivot": [float(x) for x in pivot], **b}
    data = {"format_version": 1, "name": name, "units": "1/16 block, y up, front = -Z",
            "uv": "normalized, origin top-left (v down)", "texture": texture, "texture_size": [size, size],
            "texels_per_unit": TEX, "triangle_count": tot, "bones": out}
    if extra:
        data.update(extra)
    Path(path).write_text(json.dumps(data, ensure_ascii=False), encoding="utf-8")
    return data


def build_option(sid, key, build, pal, bones, hint=128):
    """build(key) -> (faces, eyes)。書き出して dict を返す。"""
    faces, eyes = build(key)
    d = OUT / sid / key
    d.mkdir(parents=True, exist_ok=True)
    size, P, N, M, L = build_atlas(faces, hint, 0)
    atlas, glow = paint(faces, size, P, N, M, pal, eyes)
    Image.fromarray(atlas, "RGBA").save(d / f"{sid}_{key}_atlas.png")
    gm = np.zeros((size, size, 4), np.uint8)
    gm[glow] = (255, 255, 255, 255)
    Image.fromarray(gm, "RGBA").save(d / f"{sid}_{key}_glowmask.png")
    data = export_mesh(faces, size, d / f"{sid}_{key}_mesh.json", f"{sid}_{key}", f"{sid}_{key}_atlas.png", bones,
                       {"glowmask": f"{sid}_{key}_glowmask.png"})
    bad = check_buried(faces)
    return dict(key=key, faces=faces, eyes=eyes, size=size, atlas=atlas, glow=glow, data=data, buried=bad,
                tris=data["triangle_count"])


# ====================================================================== シート

CY, GR, WH = (150, 232, 255), (160, 160, 176), (236, 232, 218)


def bg_panel(w, h):
    return Image.fromarray(np.clip(voxel._background(w, h, "dark"), 0, 255).astype(np.uint8))


def render_view(r, atlas, glow, pose, bones, yaw, pitch, size, center, scale, ref=True):
    ss = size
    faces = pose_faces(r["faces"], pose, bones)
    img = render(faces, atlas, glow, yaw, pitch, ss, center, scale)
    bg = bg_panel(*ss)
    if ref:
        d = ImageDraw.Draw(bg, "RGBA")
        W_, H_ = ss
        # 地面の線と、プレイヤー (高さ 28.8 単位) の目盛り
        V_ = rot_x(pitch) @ rot_y(yaw)
        cen = V_ @ np.asarray(center, float)

        def scr(p):
            c = V_ @ np.asarray(p, float)
            return ((c[0] - cen[0]) * scale + W_ / 2, -(c[1] - cen[1]) * scale + H_ / 2)

        gy = scr((0, 0, 0))[1]
        d.line([(0, gy), (W_, gy)], fill=(120, 130, 150, 90), width=1)
        x = 10
        top = gy - 28.8 * scale
        d.line([(x, gy), (x, top)], fill=(255, 220, 140, 150), width=2)
        d.line([(x - 4, top), (x + 4, top)], fill=(255, 220, 140, 150), width=2)
        for k in range(1, 2):
            yy = gy - 16 * scale * k
            d.line([(x - 3, yy), (x + 3, yy)], fill=(255, 220, 140, 150), width=1)
        d.text((x + 6, top - 2), "人 1.8", font=voxel.font(11), fill=(255, 220, 140, 190))
    bg.paste(img, (0, 0), img)
    return bg


def sheet(spec, results, bones, poses, out_png):
    """spec: title, sub, center, scale, views, labels(案の名前), pose_rows。"""
    F = voxel.font
    n = len(results)
    PW = spec.get("pw", 500)
    GAP, X0 = 14, 20
    W = X0 * 2 + n * PW + (n - 1) * GAP
    rows = spec["rows"]   # [(見出し, pose 名, yaw, pitch, panel_h, center, scale)]
    top = 96
    total = top + sum(30 + r[4] + 34 for r in rows) + 40 + (spec.get("atlas_h", 300) + 80)
    cv = Image.new("RGB", (W, total), (16, 16, 22))
    d = ImageDraw.Draw(cv)
    d.text((24, 14), spec["title"], font=F(26), fill=WH)
    for i, line in enumerate(spec.get("sub", [])):
        d.text((24, 50 + 20 * i), line, font=F(15), fill=GR)
    y = top
    for head, pose, yaw, pitch, ph, cen, sc in rows:
        d.text((X0, y), head, font=F(20), fill=CY)
        y += 30
        for i, r in enumerate(results):
            x = X0 + i * (PW + GAP)
            im = render_view(r, r["atlas"], r["glow"], poses[pose], bones, yaw, pitch, (PW, ph), cen, sc)
            cv.paste(im, (x, y))
            d.rectangle([x - 1, y - 1, x + PW, y + ph], outline=(60, 60, 78))
            d.text((x + 2, y + ph + 6), f'{spec["labels"][i]}  {r["tris"]} 三角形', font=F(17), fill=CY)
        y += ph + 34
    d.text((X0, y), "テクスチャのアトラス (2 倍表示)", font=F(20), fill=CY)
    y += 30
    for i, r in enumerate(results):
        x = X0 + i * (PW + GAP)
        s = r["size"]
        k = max(1, min(PW // s, 3))
        at = Image.fromarray(r["atlas"], "RGBA")
        bgp = Image.new("RGB", (s * k, s * k), (34, 34, 46))
        bgp.paste(at.resize((s * k, s * k), Image.NEAREST), (0, 0), at.resize((s * k, s * k), Image.NEAREST))
        cv.paste(bgp, (x, y))
        d.text((x + 2, y + s * k + 4), f'{spec["labels"][i]} アトラス {s}x{s}', font=F(15), fill=GR)
    cv = cv.crop((0, 0, W, min(total, y + spec.get("atlas_h", 300) + 40)))
    cv.save(out_png)
    return cv


def manifest_entry(spec, results, out_png_rel):
    opts = {}
    for k, r in zip("abc", results):
        o = spec["options"][k]
        bad = ", ".join(f"{a}-{b}:{f}" for a, b, f in r["buried"]) or "なし"
        opts[k] = {"name": o["name"] + f' ({r["tris"]} 三角形)', "desc": o["desc"]}
        r["bad_txt"] = bad
    tri = " / ".join(f'{k.upper()} {r["tris"]}' for k, r in zip("abc", results))
    extra = spec["extra"] + f' 三角形 (全身): {tri} (目安 {spec["budget"]})。埋まった部品の検査: ' + \
        " / ".join(f'{k.upper()} {r["bad_txt"]}' for k, r in zip("abc", results)) + \
        f' 。メッシュ・アトラス・光る画素のマスクは docs/review/ch1_roughs/{spec["id"]}/{{a,b,c}}/。生成: tools/rough/lowpoly/ch1/{spec["id"]}.py。'
    return {"id": "ch1_" + spec["id"], "short": spec["short"], "kind": spec["kind"], "title": spec["title"],
            "sheets": [out_png_rel], "extra": extra, "options": opts}


def run(spec, build, pal, bones, poses, hint=128):
    OUT.mkdir(parents=True, exist_ok=True)
    results = []
    for k in "abc":
        r = build_option(spec["id"], k, build, pal if not isinstance(pal, dict) or "mats" in pal else pal[k], bones, hint)
        results.append(r)
        print(spec["id"], k, "tris", r["tris"], "atlas", r["size"], "buried", r["buried"])
    png = OUT / f'{spec["id"]}.png'
    sheet(spec, results, bones, poses, png)
    ent = manifest_entry(spec, results, f'ch1_roughs/{spec["id"]}.png')
    (OUT / f'{spec["id"]}_manifest.json').write_text(json.dumps(ent, ensure_ascii=False, indent=1), encoding="utf-8")
    return results
