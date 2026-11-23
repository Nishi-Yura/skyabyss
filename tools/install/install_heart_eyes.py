#!/usr/bin/env python3
"""心臓 4 体の作り込み「C 眼の群れ」を入れる (2026-11-11 の声「全部C」。ラフは tools/rough/lowpoly/heart_horror/build.py の C)。
入っている低ポリ (lowpoly/<心臓>_p<段>.json と textures/lowpoly/<心臓>_p<段>.png ほか) に、目を埋め込む:
  殻 (心臓の芯) の外の面に大小の目が開き、まわりへ血の筋。正面に大きな一つ目。段が進むほど目が増える (5 → 8 → 11)。
  目はぶつかった面の骨に付ける (殻の板が開けば目も一緒に動く)。
最初に流したときに元の模型を tools/sources/heart_eyes_base/ へ写し、二度目からはそこから作り直す (かけ重ならない)。

    python -X utf8 -I tools/install/install_heart_eyes.py
確かめの絵: docs/review/heart_horror/installed_<心臓>.png (三つの段の正面)
"""
from __future__ import annotations

import json
import math
import shutil
import sys
from pathlib import Path

import numpy as np

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "tools/gen/mobs/dungeon_bosses_lp"))
from blp import *  # noqa: E402,F401,F403
import blp  # noqa: E402
from lpkit import Face, render  # noqa: E402

A = ROOT / "src/main/resources/assets/skyabyss"
BASE = ROOT / "tools/sources/heart_eyes_base"
REVIEW = ROOT / "docs/review/heart_horror"

HEARTS = {
    "stone_whale_heart": dict(iris=(255, 170, 60), socket=[(40, 6, 12), (80, 14, 22), (124, 26, 34)], vein=[(120, 20, 24), (170, 40, 36), (220, 80, 60)], core=None),
    "tide_heart": dict(iris=(120, 240, 255), socket=[(30, 10, 30), (60, 20, 56), (96, 34, 84)], vein=[(40, 120, 150), (70, 170, 200), (130, 220, 240)], core={"heart"}, scale=1.6),
    "storm_heart": dict(iris=(210, 240, 255), socket=[(16, 18, 40), (30, 34, 70), (50, 60, 110)], vein=[(90, 160, 255), (150, 200, 255), (220, 240, 255)], core={"heart"}, scale=1.7),
    "ember_heart": dict(iris=(255, 220, 120), socket=[(120, 30, 10), (190, 70, 20), (240, 130, 40)], vein=[(200, 70, 20), (250, 140, 40), (255, 210, 110)], core=None),
}
COUNT = {1: 5, 2: 8, 3: 11}


def files(name):
    return [A / f"lowpoly/{name}.json", A / f"textures/lowpoly/{name}.png", A / f"textures/lowpoly/{name}_glowmask.png", A / f"textures/lowpoly/{name}_glow.png"]


def keep_base(name):
    BASE.mkdir(parents=True, exist_ok=True)
    for f in files(name):
        dst = BASE / f.name
        if f.exists() and not dst.exists():
            shutil.copy2(f, dst)


def rest_xf(geo):
    defs = {g["name"]: (g.get("parent"), np.array(g["pivot"], float), [float(x) for x in g.get("rotation", [0, 0, 0])]) for g in geo["bones"]}
    cache = {}

    def xf(n):
        if n not in defs:
            return np.eye(3), np.zeros(3)
        if n in cache:
            return cache[n]
        par, pv, rot = defs[n]
        Rm = rot_z(rot[2]) @ rot_y(rot[1]) @ rot_x(rot[0])
        t = pv - Rm @ pv
        if par is not None and par in defs:
            Rp, tp = xf(par)
            Rm, t = Rp @ Rm, Rp @ t + tp
        cache[n] = (Rm, t)
        return cache[n]
    return xf


def world_tris(data, xf, only=None):
    out = []
    for bname, b in data["bones"].items():
        if not b["vertices"] or (only is not None and bname not in only):
            continue
        Rm, t = xf(bname)
        V = np.array(b["vertices"], float) @ Rm.T + t
        for tr in b["triangles"]:
            out.append((V[tr], bname))
    return out


def raycast(tris, o, d):
    """o から d へ、いちばん近い当たり (点, 骨, 法線)"""
    best = None
    for P, bone in tris:
        e1, e2 = P[1] - P[0], P[2] - P[0]
        h = np.cross(d, e2)
        a = np.dot(e1, h)
        if abs(a) < 1e-9:
            continue
        f = 1 / a
        s = o - P[0]
        u = f * np.dot(s, h)
        if u < 0 or u > 1:
            continue
        q = np.cross(s, e1)
        v = f * np.dot(d, q)
        if v < 0 or u + v > 1:
            continue
        t = f * np.dot(e2, q)
        if t > 1e-4 and (best is None or t < best[0]):
            n = np.cross(e1, e2)
            n = n / (np.linalg.norm(n) + 1e-9)
            if np.dot(n, d) > 0:
                n = -n
            best = (t, o + d * t, bone, n)
    return None if best is None else best[1:]


def eye_parts(m, hits, R, spec):
    """当たった点ごとに目 (窪み・目玉・血の筋)。部品は骨 eyeN に分けておき、あとで当たった骨へ移す"""
    rng = np.random.default_rng(11)
    for k, (p, bone, n) in enumerate(hits):
        r = R * spec.get("scale", 1.0) * (0.16 if k == 0 else rng.uniform(0.07, 0.11))
        bn = f"eye{k}"
        m.bone(bn, "root", tuple(p))
        m.add(f"socket{k}", ell(bn, "socket", p - n * r * 0.25, (r * 1.4, r * 1.4, r * 1.4), segs=8, rings=3))
        eye(m, bn, p + n * r * 0.2, r, tuple(n), spec["iris"], glow=True, lid=(40, 8, 14))
        side = np.cross(n, [0, 1, 0])
        if np.linalg.norm(side) < 1e-3:
            side = np.array([1.0, 0, 0])
        side = side / np.linalg.norm(side)
        up = np.cross(side, n)
        for j in range(3 if k else 5):
            a = rng.uniform(0, math.tau)
            tv = side * math.cos(a) + up * math.sin(a)
            q0 = p + tv * r * 1.25 - n * r * 0.1
            q1 = q0 + tv * r * 1.7 + n * r * 0.05
            q2 = q1 + (tv + rng.normal(size=3) * 0.35) * r * 1.3
            m.add(f"vein{k}{j}", tub(bn, "vein", [q0, q1, q2], [r * 0.17, r * 0.12, r * 0.05], None, sides=4))


def directions(count):
    """正面 (-Z) の大きな目と、前の半球に散らばる目の向き"""
    rng = np.random.default_rng(5)
    ds = [np.array([0, 0.05, -1.0])]
    while len(ds) < count:
        th = rng.uniform(-0.95, 0.95)
        ph = rng.uniform(-1.35, 1.35)
        d = np.array([math.sin(ph) * math.cos(th), math.sin(th), -math.cos(ph) * math.cos(th)])
        if all(np.dot(d / np.linalg.norm(d), q / np.linalg.norm(q)) < 0.9 for q in ds):
            ds.append(d)
    return [d / np.linalg.norm(d) for d in ds]


def install(name, phase, spec):
    stem = f"{name}_p{phase}"
    keep_base(stem)
    data = json.loads((BASE / f"{stem}.json").read_text(encoding="utf-8"))
    geo = json.loads((A / f"geo/entity/{stem}_lowpoly.geo.json").read_text(encoding="utf-8"))["minecraft:geometry"][0]
    xf = rest_xf(geo)
    core = world_tris(data, xf, spec["core"])
    allt = world_tris(data, xf)
    V = np.concatenate([P for P, _ in core])
    lo, hi = V.min(0), V.max(0)
    c = (lo + hi) / 2
    R = float(min(hi[0] - lo[0], hi[2] - lo[2])) / 2
    hits = []
    for d in directions(COUNT[phase]):
        h = raycast(allt if spec["core"] is None else core, c + d * R * 3, -d)
        if h is not None:
            hits.append(h)
    m = Mount("heart_eyes_" + stem, stem)
    eye_parts(m, hits, R, spec)
    m.pal = dict(mats={"eye": M_(EYE, base=0.5, lit=0.2, noise=0.0, min=0), "socket": M_(spec["socket"], lit=0.2, streak=0.06),
                       "vein": M_(spec["vein"], base=0.7, lit=0.2, min=1)}, glow_mats=["vein"] if name == "ember_heart" else [])
    size, atlas, glow = blp.paint_mount(m, m.pal, None, 256)
    # 絵を合わせる: 元の絵 (256) を左上、目の絵を右に。全体は正方形
    base_img = Image.open(BASE / f"{stem}.png").convert("RGBA")
    S = base_img.size[0]
    N = 1 << int(math.ceil(math.log2(S + size)))
    canvas = Image.new("RGBA", (N, N), (0, 0, 0, 0))
    canvas.paste(base_img, (0, 0))
    canvas.paste(Image.fromarray(atlas, "RGBA"), (S, 0))
    canvas.save(A / f"textures/lowpoly/{stem}.png")
    for suffix in ("_glowmask", "_glow"):
        src = BASE / f"{stem}{suffix}.png"
        if not src.exists():
            continue
        g0 = Image.open(src).convert("RGBA")
        gc = Image.new("RGBA", (N, N), (0, 0, 0, 0))
        gc.paste(g0, (0, 0))
        add = np.zeros((size, size, 4), np.uint8)
        add[glow] = (255, 255, 255, 255)
        gc.paste(Image.fromarray(add, "RGBA"), (S, 0))
        gc.save(A / f"textures/lowpoly/{stem}{suffix}.png")
    # 元の uv を縮め、目の三角形を当たった骨へ (骨の休めの回しの前の座標へ戻して) 足す
    k = S / N
    for b in data["bones"].values():
        b["uvs"] = [[round(u * k, 6), round(v * k, 6)] for u, v in b["uvs"]]
    added = 0
    for f in m.F:
        bone = hits[int(f.bone[3:])][1]
        Rm, t = xf(bone)
        b = data["bones"][bone]
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
    for b in data["bones"].values():
        b["triangle_count"] = len(b["triangles"])
    data["triangle_count"] = sum(len(b["triangles"]) for b in data["bones"].values())
    data["texture_size"] = [N, N]
    data["note_eyes"] = "C 眼の群れ (tools/install/install_heart_eyes.py)"
    (A / f"lowpoly/{stem}.json").write_text(json.dumps(data, ensure_ascii=False), encoding="utf-8")
    return len(hits), added


def preview(name):
    from PIL import ImageDraw
    REVIEW.mkdir(parents=True, exist_ok=True)
    tiles = []
    for ph in (1, 2, 3):
        stem = f"{name}_p{ph}"
        data = json.loads((A / f"lowpoly/{stem}.json").read_text(encoding="utf-8"))
        geo = json.loads((A / f"geo/entity/{stem}_lowpoly.geo.json").read_text(encoding="utf-8"))["minecraft:geometry"][0]
        xf = rest_xf(geo)
        img = np.array(Image.open(A / f"textures/lowpoly/{stem}.png").convert("RGBA"))
        gp = A / f"textures/lowpoly/{stem}_glowmask.png"
        glow = np.array(Image.open(gp).convert("RGBA"))[..., 3] > 0 if gp.exists() else np.zeros(img.shape[:2], bool)
        N = img.shape[0]
        faces = []
        for bname, b in data["bones"].items():
            if not b["vertices"]:
                continue
            Rm, t = xf(bname)
            Vw = np.array(b["vertices"], float) @ Rm.T + t
            UV = np.array(b["uvs"], float) * N
            for tr in b["triangles"]:
                v = Vw[tr]
                n = np.cross(v[1] - v[0], v[2] - v[0])
                if np.linalg.norm(n) < 1e-9:
                    continue
                n = n / np.linalg.norm(n)
                f = Face(verts=v, bone=bname, mat="x", ref=v.mean(0) - n)
                f.normal = n
                f.uv = UV[tr]
                faces.append(f)
        Vall = np.concatenate([f.verts for f in faces])
        c = (Vall.min(0) + Vall.max(0)) / 2
        r = float(np.max(Vall.max(0) - Vall.min(0))) / 2
        im = render(faces, img[..., :3], glow, 200, 8, (360, 360), c, 360 * 0.42 / r)
        bg = Image.new("RGBA", (360, 360), (28, 28, 38, 255))
        bg.alpha_composite(im)
        tiles.append(bg.convert("RGB"))
    cv = Image.new("RGB", (3 * 370 + 10, 400), (16, 16, 22))
    d = ImageDraw.Draw(cv)
    for i, t in enumerate(tiles):
        cv.paste(t, (10 + i * 370, 34))
        d.text((10 + i * 370, 8), f"{name} 段{i + 1}", font=voxel.font(16), fill=(150, 232, 255))
    cv.save(REVIEW / f"installed_{name}.png")


if __name__ == "__main__":
    sys.stdout.reconfigure(encoding="utf-8")
    for name, spec in HEARTS.items():
        for ph in (1, 2, 3):
            n, tris = install(name, ph, spec)
            print(name, ph, "eyes", n, "tris", tris, flush=True)
        preview(name)
