# -*- coding: utf-8 -*-
"""心臓の後のボスと塔の超大型レイドの「見せ方」の案 (2026-11-08 の声「まがまがしく、美しい見た目に。いくつか案作って見せて。
全部見た目同じだと統一感出てしまう。ストーリーや環境由来の見た目は変えずにどう魅せるか。ここが重要」)。

形 (いまの本番のメッシュ) と物語の由来はそのまま。変えるのは、塗り (色の段・光る所・縁の線)、体のまわりの飾り (後光・輪・漂うもの)、
背景の光だけ。ボスごとに三案 (A/B/C)。全部を同じ見た目にしないよう、ボスごとに違う題を立てる。

塗りの元は B 切り絵に塗り直す前の絵 (tools/sources/paper_bosses/<名前>.png)。光る所は本番の光の型 (<名前>_glowmask.png)。

    python -I -X utf8 tools/rough/lowpoly/boss_looks/boss_looks.py [ボスの鍵 ...]   → docs/review/roughs_boss_looks/<鍵>.png
"""
from __future__ import annotations

import json
import math
import os
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT = Path(__file__).resolve().parents[4]
sys.path.insert(0, str(ROOT / "tools/audit"))
import mesh_audit as ma  # noqa: E402

LP = ROOT / "src/main/resources/assets/skyabyss/lowpoly"
TEX = ROOT / "src/main/resources/assets/skyabyss/textures/lowpoly"
SRC = ROOT / "tools/sources/paper_bosses"
OUT = ROOT / "docs/review/roughs_boss_looks"
PW, PH = 440, 470


def font(size):
    for p in ("C:/Windows/Fonts/YuGothB.ttc", "C:/Windows/Fonts/YuGothM.ttc", "C:/Windows/Fonts/meiryo.ttc"):
        if os.path.exists(p):
            return ImageFont.truetype(p, size)
    return ImageFont.load_default()


# ---------------------------------------------------------------- メッシュと絵
def load(name):
    data = json.loads((LP / (name + ".json")).read_text(encoding="utf-8"))
    tex = data.get("texture", name).split(":")[-1]
    tex = tex[:-4] if tex.endswith(".png") else tex
    src = SRC / (tex + ".png")
    atlas = np.array(Image.open(src if src.exists() else TEX / (tex + ".png")).convert("RGBA")).astype(float)
    gp = TEX / (tex + "_glowmask.png")
    glow = np.array(Image.open(gp).convert("RGBA"))[..., 3] > 40 if gp.exists() else np.zeros(atlas.shape[:2], bool)
    if glow.shape != atlas.shape[:2]:
        glow = np.array(Image.fromarray(glow.astype(np.uint8) * 255).resize(atlas.shape[1::-1], Image.NEAREST)) > 0
    return data, atlas, glow


def pose_matrices(data):
    bones = data["bones"]
    mats = {}

    def get(n):
        if n in mats:
            return mats[n]
        pn = bones[n].get("parent")
        mats[n] = get(pn) if pn in bones else np.eye(4)
        return mats[n]
    for n in bones:
        get(n)
    return mats


def render(data, atlas, glowmap, yaw, pitch, size, ss=2):
    """正射影。色 (RGB)、覆い (α)、光る所 (0/1) を返す"""
    mats = pose_matrices(data)
    W, H = size[0] * ss, size[1] * ss
    allv = np.concatenate([np.array(b.get("vertices", []), float).reshape(-1, 3) for b in data["bones"].values() if b.get("vertices")])
    lo, hi = allv.min(0), allv.max(0)
    ctr = (lo + hi) / 2
    ya, pa = math.radians(yaw), math.radians(pitch)
    fwd = np.array([math.sin(ya) * math.cos(pa), -math.sin(pa), math.cos(ya) * math.cos(pa)])
    right = np.cross(np.array([0, 1.0, 0]), fwd)
    right /= np.linalg.norm(right)
    up = np.cross(fwd, right)
    ext = max(np.abs((allv - ctr) @ right).max() * 2, np.abs((allv - ctr) @ up).max() * 2)
    scale = min(W, H) * 0.78 / ext / ss
    light = np.array([0.35, 0.8, -0.5])
    light /= np.linalg.norm(light)
    zbuf = np.full((H, W), np.inf)
    img = np.zeros((H, W, 3), float)
    glow = np.zeros((H, W), float)
    for n, b in data["bones"].items():
        V = np.array(b.get("vertices", []), float).reshape(-1, 3)
        if not len(V):
            continue
        T = np.array(b["triangles"], int).reshape(-1, 3)
        UV = np.array(b["uvs"], float).reshape(-1, 2)
        P = (np.c_[V, np.ones(len(V))] @ mats[n].T)[:, :3]
        sx = ((P - ctr) @ right) * scale * ss + W / 2
        sy = -((P - ctr) @ up) * scale * ss + H / 2 + H * 0.04
        sz = (P - ctr) @ fwd
        for a, bb, c in T:
            xs = (sx[a], sx[bb], sx[c])
            ys = (sy[a], sy[bb], sy[c])
            x0, x1 = int(max(0, math.floor(min(xs)))), int(min(W - 1, math.ceil(max(xs))))
            y0, y1 = int(max(0, math.floor(min(ys)))), int(min(H - 1, math.ceil(max(ys))))
            if x1 < x0 or y1 < y0:
                continue
            nrm = np.cross(P[bb] - P[a], P[c] - P[a])
            ln = np.linalg.norm(nrm)
            if ln < 1e-9 or np.dot(nrm / ln, fwd) >= 0:
                continue
            nrm /= ln
            gx, gy = np.meshgrid(np.arange(x0, x1 + 1) + 0.5, np.arange(y0, y1 + 1) + 0.5)
            den = (ys[1] - ys[2]) * (xs[0] - xs[2]) + (xs[2] - xs[1]) * (ys[0] - ys[2])
            if abs(den) < 1e-9:
                continue
            l1 = ((ys[1] - ys[2]) * (gx - xs[2]) + (xs[2] - xs[1]) * (gy - ys[2])) / den
            l2 = ((ys[2] - ys[0]) * (gx - xs[2]) + (xs[0] - xs[2]) * (gy - ys[2])) / den
            l3 = 1 - l1 - l2
            m = (l1 >= 0) & (l2 >= 0) & (l3 >= 0)
            if not m.any():
                continue
            z = l1 * sz[a] + l2 * sz[bb] + l3 * sz[c]
            sub = zbuf[y0:y1 + 1, x0:x1 + 1]
            upd = m & (z < sub)
            if not upd.any():
                continue
            u = l1 * UV[a, 0] + l2 * UV[bb, 0] + l3 * UV[c, 0]
            v = l1 * UV[a, 1] + l2 * UV[bb, 1] + l3 * UV[c, 1]
            ix = np.clip((u * atlas.shape[1]).astype(int), 0, atlas.shape[1] - 1)
            iy = np.clip((v * atlas.shape[0]).astype(int), 0, atlas.shape[0] - 1)
            tex = atlas[iy, ix][..., :3]
            g = glowmap[iy, ix].astype(float)
            shade = 0.62 + 0.38 * max(0.0, float(np.dot(nrm, light)))
            px = tex * (shade + (1 - shade) * g[..., None])
            img[y0:y1 + 1, x0:x1 + 1][upd] = px[upd]
            glow[y0:y1 + 1, x0:x1 + 1][upd] = g[upd]
            sub[upd] = z[upd]
    alpha = np.isfinite(zbuf).astype(float)
    def down(a):
        return np.array(Image.fromarray(a).resize(size, Image.LANCZOS))
    rgb = down(np.clip(img, 0, 255).astype(np.uint8))
    al = down((alpha * 255).astype(np.uint8))
    gl = down((glow * 255).astype(np.uint8))
    return rgb, al, gl


# ---------------------------------------------------------------- 塗り (絵の上で)
def lum(a):
    return (a[..., :3] @ np.array([0.299, 0.587, 0.114])) / 255.0


def gradient_map(a, stops):
    """明るさを色の段へ (stops: [(位置 0..1, (r,g,b)), ...])"""
    L = lum(a)
    out = np.zeros(a.shape[:2] + (3,))
    pos = np.array([s[0] for s in stops])
    cols = np.array([s[1] for s in stops], float)
    for k in range(3):
        out[..., k] = np.interp(L, pos, cols[:, k])
    return out


def edges(a, levels=3):
    """明るさの段の境 (切り抜いた縁・金継ぎの線)"""
    L = lum(a)
    band = np.digitize(L, np.linspace(0, 1, levels + 1)[1:-1])
    e = np.zeros(L.shape, bool)
    e[:-1] |= band[:-1] != band[1:]
    e[:, :-1] |= band[:, :-1] != band[:, 1:]
    return e


def treat(atlas, glow, look):
    a = atlas.copy()
    solid = a[..., 3] > 0
    rgb = gradient_map(a, look["stops"])
    if look.get("pearl"):
        h, w = a.shape[:2]
        yy, xx = np.mgrid[0:h, 0:w]
        t = (xx / w * 3.0 + yy / h * 2.0) * math.pi
        tint = np.stack([np.sin(t) * 0.5 + 0.5, np.sin(t + 2.1) * 0.5 + 0.5, np.sin(t + 4.2) * 0.5 + 0.5], -1) * 60
        rgb = np.clip(rgb + tint - 30, 0, 255)
    if look.get("hatch"):
        h, w = a.shape[:2]
        yy, xx = np.mgrid[0:h, 0:w]
        dark = lum(a) < look["hatch"]
        lines = ((xx + yy) % 4) == 0
        rgb[dark & lines] = rgb[dark & lines] * 0.45
    if look.get("edge"):
        e = edges(a, look.get("edge_levels", 3)) & solid
        rgb[e] = look["edge"]
    if look.get("veins"):
        # 金継ぎ・脈: 暗い所の中に、細い明るい筋 (絵の明るさの細かい揺れの峰)
        L = lum(a)
        k = np.abs(np.roll(L, 1, 0) - np.roll(L, -1, 0)) + np.abs(np.roll(L, 1, 1) - np.roll(L, -1, 1))
        v = (k > np.percentile(k[solid], 90)) & solid & (L < 0.6)
        rgb[v] = look["veins"]
        glow = glow | v
    gc = look.get("glow")
    if gc is not None:
        rgb[glow] = gc
    a[..., :3] = rgb
    return a, glow


# ---------------------------------------------------------------- 飾り (絵の外)
def bg(size, top, bottom, spot=None):
    w, h = size
    t = np.linspace(0, 1, h)[:, None, None]
    img = np.array(top, float)[None, None] * (1 - t) + np.array(bottom, float)[None, None] * t
    img = np.broadcast_to(img, (h, w, 3)).copy()
    if spot:
        yy, xx = np.mgrid[0:h, 0:w]
        d = np.hypot(xx - w / 2, yy - h * 0.42) / (w * 0.6)
        img += np.array(spot, float)[None, None] * np.clip(1 - d, 0, 1)[..., None] ** 2
    return Image.fromarray(np.clip(img, 0, 255).astype(np.uint8)).convert("RGBA")


def layer(size):
    return Image.new("RGBA", size, (0, 0, 0, 0))


def halo(size, kind, col, cx=0.5, cy=0.3, r=0.24, rnd=None):
    w, h = size
    L = layer(size)
    d = ImageDraw.Draw(L)
    X, Y, R = w * cx, h * cy, w * r
    c = tuple(col) + (230,)
    if kind == "ring":
        d.ellipse([X - R, Y - R, X + R, Y + R], outline=c, width=5)
        d.ellipse([X - R * 0.8, Y - R * 0.8, X + R * 0.8, Y + R * 0.8], outline=tuple(col) + (120,), width=2)
    elif kind == "eclipse":
        for k in range(36):
            a = k / 36 * math.tau
            d.line([X + math.cos(a) * R * 0.95, Y + math.sin(a) * R * 0.95, X + math.cos(a) * R * (1.35 + 0.25 * (k % 3 == 0)),
                    Y + math.sin(a) * R * (1.35 + 0.25 * (k % 3 == 0))], fill=c, width=3)
        d.ellipse([X - R, Y - R, X + R, Y + R], fill=(8, 6, 10, 255), outline=c, width=6)
    elif kind == "crescent":
        d.ellipse([X - R, Y - R, X + R, Y + R], fill=c)
        d.ellipse([X - R * 0.7, Y - R * 1.05, X + R * 1.2, Y + R * 0.9], fill=(0, 0, 0, 0))
    elif kind == "chandelier":
        d.ellipse([X - R, Y - R * 0.35, X + R, Y + R * 0.35], outline=c, width=4)
        for k in range(9):
            a = k / 9 * math.tau
            px, py = X + math.cos(a) * R, Y + math.sin(a) * R * 0.35
            d.line([px, py, px, py - 14], fill=(240, 230, 210, 255), width=4)
            d.ellipse([px - 5, py - 26, px + 5, py - 13], fill=(255, 200, 90, 255))
    elif kind == "keyhole":
        d.ellipse([X - R * 0.5, Y - R * 0.9, X + R * 0.5, Y + R * 0.1], outline=c, width=5)
        d.polygon([(X - R * 0.3, Y), (X + R * 0.3, Y), (X + R * 0.55, Y + R * 1.1), (X - R * 0.55, Y + R * 1.1)], outline=c)
        d.ellipse([X - R * 1.3, Y - R * 1.3, X + R * 1.3, Y + R * 1.3], outline=tuple(col) + (110,), width=3)
    elif kind == "sound":
        for k in range(5):
            rr = R * (0.6 + k * 0.35)
            d.ellipse([X - rr, Y - rr * 0.55, X + rr, Y + rr * 0.55], outline=tuple(col) + (220 - k * 40,), width=3)
    elif kind == "stars":
        rnd = rnd or np.random.default_rng(3)
        pts = [(X + rnd.uniform(-1.6, 1.6) * R, Y + rnd.uniform(-1.0, 1.2) * R) for _ in range(14)]
        for i in range(len(pts) - 1):
            if i % 3 != 2:
                d.line([pts[i], pts[i + 1]], fill=tuple(col) + (150,), width=2)
        for p in pts:
            d.ellipse([p[0] - 4, p[1] - 4, p[0] + 4, p[1] + 4], fill=(255, 255, 255, 255))
    elif kind == "rays":
        # 上の光源から下へ広がる筋 (cy が上なら下向き)
        base = math.pi / 2 if cy < 0.2 else -math.pi / 2
        for k in range(14):
            a = base + (k - 6.5) * 0.12
            d.polygon([(X, Y), (X + math.cos(a - 0.03) * R * 4, Y + math.sin(a - 0.03) * R * 4), (X + math.cos(a + 0.03) * R * 4, Y + math.sin(a + 0.03) * R * 4)],
                      fill=tuple(col) + (70,))
    elif kind == "gate":
        d.rectangle([X - R * 0.9, Y - R * 1.2, X + R * 0.9, Y + R * 1.4], outline=c, width=6)
        d.arc([X - R * 0.9, Y - R * 2.0, X + R * 0.9, Y - R * 0.4], 180, 360, fill=c, width=6)
    return L.filter(ImageFilter.GaussianBlur(0.6))


def motes(size, kind, col, n=60, seed=1):
    w, h = size
    rnd = np.random.default_rng(seed)
    L = layer(size)
    d = ImageDraw.Draw(L)
    for _ in range(n):
        x, y = rnd.uniform(0, w), rnd.uniform(0, h)
        s = rnd.uniform(2, 6) * (2.2 if kind in ("tag", "ofuda") else 1.0)
        al = int(rnd.uniform(120, 240))
        c = tuple(col) + (al,)
        if kind == "petal":
            a = rnd.uniform(0, math.pi)
            d.polygon([(x + math.cos(a) * s * 2, y + math.sin(a) * s), (x, y + s * 0.6), (x - math.cos(a) * s * 2, y - math.sin(a) * s), (x, y - s * 0.6)], fill=c)
        elif kind == "bubble":
            d.ellipse([x - s, y - s, x + s, y + s], outline=c, width=1)
        elif kind == "ash":
            d.rectangle([x, y, x + s * 0.6, y + s * 0.6], fill=c)
        elif kind == "spark":
            d.line([x - s, y, x + s, y], fill=c, width=1)
            d.line([x, y - s, x, y + s], fill=c, width=1)
        elif kind == "tag":
            d.rectangle([x, y, x + s * 3, y + s * 1.6], fill=(236, 226, 200, al), outline=(60, 40, 30, al))
            d.line([x + s * 1.5, y, x + s * 1.5, y - s * 2.5], fill=(200, 170, 80, al), width=1)
        elif kind == "ofuda":
            d.rectangle([x, y, x + s * 1.4, y + s * 4], fill=(240, 232, 214, al))
            d.line([x + s * 0.7, y + s * 0.6, x + s * 0.7, y + s * 3.4], fill=(170, 30, 30, al), width=1)
        elif kind == "feather":
            d.ellipse([x - s * 0.5, y - s * 2, x + s * 0.5, y + s * 2], fill=c)
        elif kind == "glyph":
            d.arc([x - s * 1.5, y - s * 1.5, x + s * 1.5, y + s * 1.5], 0, 270, fill=c, width=1)
    return L


def ribbon(size, col, seed=2, n=2):
    w, h = size
    rnd = np.random.default_rng(seed)
    L = layer(size)
    d = ImageDraw.Draw(L)
    for k in range(n):
        y0 = h * rnd.uniform(0.3, 0.7)
        amp = h * rnd.uniform(0.05, 0.12)
        ph = rnd.uniform(0, math.tau)
        pts = [(x, y0 + math.sin(x / w * math.tau * 1.3 + ph) * amp) for x in np.linspace(-10, w + 10, 60)]
        d.line(pts, fill=tuple(col) + (210,), width=7)
        d.line(pts, fill=(255, 255, 255, 90), width=2)
    return L


def bloom(rgb, gl, col, strength=1.0):
    g = Image.fromarray(gl).filter(ImageFilter.GaussianBlur(9))
    ga = np.array(g).astype(float) / 255 * strength
    c = np.array(col if col is not None else (255, 255, 255), float)
    out = np.clip(rgb.astype(float) + ga[..., None] * c * 0.9, 0, 255)
    return out.astype(np.uint8), (np.clip(ga, 0, 1) * 255).astype(np.uint8)


def panel(name, opt, yaw, pitch):
    data, atlas, glow = load(name)
    a2, g2 = treat(atlas, glow, opt)
    size = (PW, PH)
    rgb, al, gl = render(data, a2, g2, yaw, pitch, size)
    out = bg(size, opt["bg"][0], opt["bg"][1], opt["bg"][2] if len(opt["bg"]) > 2 else None)
    for h in opt.get("behind", []):
        out = Image.alpha_composite(out, halo(size, *h))
    for m in opt.get("back_motes", []):
        out = Image.alpha_composite(out, motes(size, *m))
    rgb2, ga = bloom(rgb, gl, opt.get("glow"), opt.get("bloom", 1.0))
    # 光る所のにじみ (体の外にも少し漏れる)
    halo_glow = Image.fromarray(np.dstack([np.full(ga.shape + (3,), opt.get("glow") or (255, 255, 255), np.uint8), (ga * 0.6).astype(np.uint8)]))
    out = Image.alpha_composite(out, halo_glow)
    if opt.get("rim"):
        # 逆光の縁: 覆いの縁を色で太らせて体の後ろへ
        edge = Image.fromarray(al).filter(ImageFilter.MaxFilter(7)).filter(ImageFilter.GaussianBlur(4))
        rim = Image.new("RGBA", size, tuple(opt["rim"]) + (0,))
        rim.putalpha(edge)
        out = Image.alpha_composite(out, rim)
    body = Image.fromarray(np.dstack([rgb2, al]))
    out = Image.alpha_composite(out, body)
    for r in opt.get("ribbons", []):
        out = Image.alpha_composite(out, ribbon(size, *r))
    for m in opt.get("motes", []):
        out = Image.alpha_composite(out, motes(size, *m))
    return out


def sheet(key):
    b = BOSSES[key]
    title_h, text_h = 66, 150
    W = PW * 3 + 40
    H = title_h + PH + text_h + 20
    img = Image.new("RGB", (W, H), (16, 15, 20))
    d = ImageDraw.Draw(img)
    d.text((20, 14), b["title"], font=font(26), fill=(240, 232, 214))
    d.text((20, 46), b["keep"], font=font(14), fill=(170, 165, 180))
    for i, opt in enumerate(b["options"]):
        x = 10 + i * (PW + 10)
        p = panel(b["mesh"], opt, b.get("yaw", 22), b.get("pitch", 8))
        img.paste(p.convert("RGB"), (x, title_h))
        d.text((x + 8, title_h + PH + 8), opt["name"], font=font(20), fill=(255, 220, 150))
        y = title_h + PH + 36
        for line in opt["text"]:
            d.text((x + 8, y), line, font=font(14), fill=(220, 215, 225))
            y += 20
    OUT.mkdir(parents=True, exist_ok=True)
    img.save(OUT / (key + ".png"))
    print("wrote", OUT / (key + ".png"))


# ---------------------------------------------------------------- 案
INK = (12, 10, 16)
BOSSES = {
    "lord": {
        "mesh": "lord_ash", "title": "五章 淵の主 (灰の主で試す。塩・肋・嵐の主も同じ決まりで、色だけ巨神ごと)",
        "keep": "残すもの: 四体の巨神の写しという由来・形・巨神ごとの色 (灰=熾の橙、塩=潮の青緑、肋=骨の金白、嵐=紫)",
        "options": [
            {"name": "A 金継ぎの漆", "stops": [(0, INK), (0.45, (40, 30, 34)), (1, (120, 96, 90))], "veins": (255, 150, 60), "glow": (255, 170, 80),
             "bg": [(24, 10, 8), (6, 4, 6), (60, 22, 8)], "behind": [("ring", (255, 160, 70), 0.5, 0.26, 0.26)], "motes": [("ash", (255, 140, 60), 70, 4)],
             "text": ["黒漆の体に、割れ目を金で継いだ筋が熾の色に光る。", "割れるほど美しい。壊れた写しを継ぎ直した主。", "後ろに細い輪の後光。熾の粉が舞う。"]},
            {"name": "B 水底の影絵", "stops": [(0, (6, 8, 18)), (0.6, (16, 22, 40)), (1, (40, 52, 80))], "glow": (255, 190, 110), "rim": (255, 140, 60),
             "bg": [(4, 14, 24), (2, 4, 10), (20, 50, 70)], "behind": [("rays", (120, 200, 230), 0.5, 0.0, 0.2)], "back_motes": [("bubble", (150, 210, 240), 40, 5)],
             "text": ["淵の底から見上げた影絵。体はほぼ影で、", "縁だけが巨神の色に逆光で燃える。", "上から差す水底の光の筋、昇る泡。"]},
            {"name": "C 標本箱", "stops": [(0, (70, 56, 46)), (0.5, (190, 176, 150)), (1, (240, 232, 214))], "hatch": 0.45, "edge": (40, 28, 24), "glow": (230, 60, 40),
             "bg": [(40, 34, 30), (14, 12, 12), (90, 70, 50)], "motes": [("tag", (0, 0, 0), 14, 6)],
             "text": ["古い博物誌の銅版画の色。線の陰影で描かれ、", "金の針と名札が体のまわりに浮く。", "「写し」を標本にした、という見せ方。"]},
        ]},
    "king": {
        "mesh": "king_bone_c1", "title": "五章 深みの王 (骨の王・中の姿で試す。灰・塩・影・嵐の王も同じ決まり)",
        "keep": "残すもの: 三つの姿の移り変わり・王冠のある形・王ごとの元素",
        "options": [
            {"name": "A 喪服の王", "stops": [(0, (10, 4, 16)), (0.5, (44, 18, 60)), (1, (150, 110, 170))], "edge": (210, 170, 90), "glow": (240, 200, 120),
             "bg": [(20, 8, 26), (4, 2, 6), (60, 20, 70)], "behind": [("ring", (220, 180, 90), 0.5, 0.2, 0.2), ("ring", (220, 180, 90), 0.5, 0.2, 0.3)],
             "motes": [("petal", (30, 10, 30), 60, 7)],
             "text": ["深い紫の天鵞絨と金の縁。弔いの王。", "王冠の上に二重の金の輪、", "黒い花びらが降りつづける。"]},
            {"name": "B 黒い太陽", "stops": [(0, INK), (0.6, (30, 26, 30)), (1, (90, 80, 80))], "edge": (255, 120, 60), "glow": (255, 90, 50),
             "bg": [(30, 8, 4), (4, 2, 2), (120, 30, 10)], "behind": [("eclipse", (255, 120, 50), 0.5, 0.26, 0.2)],
             "text": ["背に皆既の日蝕。黒い円から炎の冠。", "体は墨、縁だけ朱。", "姿が変わるたびに冠の炎が伸びる。"]},
            {"name": "C 骨の燭台", "stops": [(0, (60, 50, 40)), (0.5, (200, 186, 160)), (1, (250, 244, 230))], "edge": (90, 70, 50), "glow": (255, 200, 100),
             "bg": [(30, 22, 14), (8, 6, 4), (90, 60, 20)], "behind": [("chandelier", (230, 200, 140), 0.5, 0.2, 0.28)], "motes": [("spark", (255, 210, 120), 30, 3)],
             "text": ["象牙の骨に、燭台の輪をいただく王。", "蝋燭の火が王の頭上で揺れ、", "王の怒りで火が青くなる。"]},
        ]},
    "warden": {
        "mesh": "heart_warden", "title": "心臓の番人",
        "keep": "残すもの: 古い骨の番人 (2026-10-28 に選んだ茶色の染み・刻み)・深淵の心臓を守る役",
        "options": [
            {"name": "A 脈打つ聖櫃", "stops": [(0, (40, 26, 20)), (0.5, (150, 120, 96)), (1, (226, 210, 186))], "veins": (230, 40, 60), "glow": (255, 60, 80),
             "bg": [(30, 6, 10), (6, 2, 4), (110, 10, 30)], "behind": [("ring", (255, 80, 100), 0.5, 0.36, 0.14)], "bloom": 1.4,
             "text": ["古い骨の割れ目に、心臓の赤い脈が走る。", "胸の前に、心臓を納めた聖櫃の光。", "鼓動に合わせて赤が明滅する。"]},
            {"name": "B 錆の門番", "stops": [(0, (30, 16, 10)), (0.5, (120, 64, 34)), (1, (210, 160, 110))], "edge": (240, 200, 120), "glow": (120, 230, 220),
             "bg": [(10, 16, 18), (2, 4, 6), (30, 70, 70)], "behind": [("keyhole", (120, 230, 220), 0.5, 0.3, 0.2)],
             "text": ["錆びた鉄と骨。背に大きな鍵穴の後光。", "鍵穴の奥に、深淵の青緑の光。", "門そのものが歩いているような姿。"]},
            {"name": "C 祈りの包帯", "stops": [(0, (60, 54, 50)), (0.55, (200, 194, 180)), (1, (246, 242, 232))], "hatch": 0.4, "glow": (200, 30, 30),
             "bg": [(36, 34, 34), (10, 10, 12), (80, 74, 70)], "motes": [("ofuda", (0, 0, 0), 22, 8)],
             "text": ["骨を白い包帯で巻き、", "朱で書いた札が体のまわりを巡る。", "封じられた番人という見せ方。"]},
        ]},
    "moon": {
        "mesh": "moon_core", "title": "六章 月の核 (月の主たちも同じ決まり: 影抱き・月秤・満ちの番・欠けの刃)",
        "keep": "残すもの: 月の岩の形・巻き戻る月という由来・月の満ち欠けごとの主",
        "options": [
            {"name": "A 螺鈿の月", "stops": [(0, (30, 34, 50)), (0.5, (150, 160, 180)), (1, (240, 244, 250))], "pearl": True, "glow": (200, 240, 255),
             "bg": [(10, 14, 30), (2, 4, 10), (40, 60, 100)], "behind": [("crescent", (230, 240, 255), 0.5, 0.25, 0.22)], "motes": [("spark", (230, 240, 255), 40, 5)],
             "text": ["月の岩に、貝の内側のような虹の螺鈿。", "背に銀の三日月。", "見る向きで色が移ろう、冷たい美しさ。"]},
            {"name": "B 赤い月蝕", "stops": [(0, INK), (0.6, (40, 20, 24)), (1, (120, 60, 60))], "edge": (220, 40, 40), "glow": (255, 60, 50),
             "bg": [(30, 4, 6), (4, 2, 2), (120, 10, 20)], "behind": [("eclipse", (230, 50, 40), 0.5, 0.28, 0.24)],
             "text": ["蝕で赤く染まった月。体は墨で、", "縁と割れ目だけ血の色。", "巻き戻りの禍々しさを前に出す。"]},
            {"name": "C 星図", "stops": [(0, (6, 10, 30)), (0.6, (20, 34, 80)), (1, (70, 100, 170))], "edge": (220, 220, 255), "glow": (255, 240, 180),
             "bg": [(6, 8, 24), (2, 2, 8), (20, 30, 70)], "behind": [("stars", (200, 210, 255), 0.5, 0.35, 0.4)],
             "text": ["夜空を切り抜いた体に、星座の線。", "星術 (六章の術) と同じ光の色。", "近くで見ると体の中にも星が瞬く。"]},
        ]},
    "founder": {
        "mesh": "founder", "title": "終章 雲海の主 (始祖。最後の鐘守・巡礼者たちも同じ決まり)",
        "keep": "残すもの: 前の都の鐘・雲海・夜明けへ向かう終章の流れ",
        "options": [
            {"name": "A 夜明けの金箔", "stops": [(0, (60, 50, 50)), (0.5, (220, 210, 200)), (1, (255, 250, 240))], "edge": (220, 170, 70), "glow": (255, 210, 120),
             "bg": [(255, 200, 150), (120, 140, 190), (255, 240, 200)], "behind": [("rays", (255, 230, 170), 0.5, 0.35, 0.2), ("ring", (230, 180, 80), 0.5, 0.2, 0.18)],
             "text": ["雲の白と金箔。夜明けの逆光の中に立つ。", "まがまがしさは、美しすぎて怖い方向へ。", "雲海の主らしい明るい終わりの主。"]},
            {"name": "B 鐘の残響", "stops": [(0, (20, 30, 26)), (0.5, (70, 110, 90)), (1, (170, 200, 170))], "edge": (210, 160, 80), "glow": (160, 255, 220),
             "bg": [(20, 30, 34), (4, 8, 10), (40, 80, 70)], "behind": [("sound", (170, 255, 220), 0.5, 0.25, 0.22)],
             "text": ["青緑に錆びた青銅。体のまわりに", "鐘の音の輪が広がっては消える。", "技を使った音を聞く主の性質と合う。"]},
            {"name": "C 雲の喪章", "stops": [(0, (40, 40, 46)), (0.6, (170, 170, 180)), (1, (236, 236, 242))], "edge": (20, 20, 24), "glow": (255, 255, 255),
             "bg": [(150, 154, 170), (60, 62, 74), (220, 220, 230)], "ribbons": [((14, 12, 16), 5, 2)], "motes": [("feather", (240, 240, 245), 30, 9)],
             "text": ["灰白の雲の体に、黒いレースの喪章が巻く。", "白い羽根が降る。", "前の都を弔う主として。"]},
        ]},
    "tower": {
        "mesh": "tower_master", "title": "塔の超大型レイド 塔の主 (見た目・動き・攻撃の見せ方)", "yaw": 18, "pitch": 6,
        "keep": "残すもの: 案 C「門を背負う者」の形 (淵から生える巨人)・一日一回の大きな戦い",
        "options": [
            {"name": "A 星の傷", "stops": [(0, (6, 6, 12)), (0.6, (26, 24, 40)), (1, (80, 76, 110))], "veins": (190, 220, 255), "glow": (220, 240, 255),
             "bg": [(8, 8, 20), (2, 2, 6), (30, 30, 80)], "behind": [("gate", (190, 220, 255), 0.5, 0.3, 0.18)], "motes": [("spark", (220, 230, 255), 60, 8)],
             "text": ["黒曜の巨人の割れ目から星空がのぞく。", "動き: 門をこじ開けると夜空が落ちてくる。", "攻撃: 星が降る円、割れ目から光の柱。"]},
            {"name": "B 淵の大樹", "stops": [(0, (8, 12, 10)), (0.6, (24, 40, 34)), (1, (70, 110, 90))], "edge": (60, 220, 180), "glow": (80, 255, 210),
             "bg": [(4, 20, 18), (2, 6, 6), (10, 70, 60)], "rim": (60, 220, 180), "back_motes": [("glyph", (80, 255, 210), 50, 10)],
             "text": ["根のような黒い体に、淵の青緑の光る苔。", "動き: 根を床へ突き刺し、場を這わせる。", "攻撃: 床から根の槍、光る胞子の雲。"]},
            {"name": "C 月を背負う", "stops": [(0, (40, 38, 44)), (0.55, (160, 156, 160)), (1, (236, 232, 230))], "edge": (30, 26, 30), "glow": (255, 80, 90),
             "bg": [(60, 60, 80), (10, 10, 16), (180, 180, 200)], "behind": [("eclipse", (255, 90, 100), 0.5, 0.22, 0.26)],
             "text": ["白い石の巨人が、背に欠けた月を負う。", "動き: 月が欠けるほど荒くなる (段階)。", "攻撃: 月光の薙ぎ、月の欠片が落ちる。"]},
        ]},
}


def main(argv):
    keys = argv or list(BOSSES)
    for k in keys:
        sheet(k)


if __name__ == "__main__":
    main(sys.argv[1:])
