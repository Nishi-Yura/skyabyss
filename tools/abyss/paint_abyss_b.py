# -*- coding: utf-8 -*-
"""五章の深淵の敵 9 体 (灰の層 3・深み 2・深淵だけ 4) の絵を描き直す (docs/design/36 の 4 と 4.1、A3)。

  python -I tools/abyss/paint_abyss_b.py

入力: docs/review/abyss_roughs/<id>/<案>/<id>_<案>_{atlas.png,glowmask.png}  (選んだ案。ラフは粗いので描き直す)
出力: docs/review/abyss_painted/<id>_{base,s1,s2}_atlas.png と <id>_{base,s1,s2}_glowmask.png
  base = 元の姿、s1 = 歪み、s2 = 崩れ (同じ骨・同じ UV。絵だけ替える。段の決まりは docs/design/36 の 4.2)

描き方 (手で描いたのではなく、決まりで描く。ラフの画素を見て、面ごとに一つの素材を決めて、その素材の模様を足す):
  - 2 倍に拡大して描く (UV は正規化されているので、骨と UV は変わらない)
  - 面 = 色を 12 色に量子化したあとの塊 (3x3 の多数決で雑音を消す)。塊ごとに、明るさと彩度で素材を決める
  - 模様: 低い周波数の濃淡 (まだら) + 細かい粒 + 面の縁の暗い線 (読みやすくするため) + 上からの光 (上縁は明るく)
    + 布は縦の筋、白いもの (骨・殻) は小さい穴
  - 光る画素 (発光マスク) は元の色を強めて、中心は白く寄せる
  - s1 歪み: 色相を少しずらし、彩度を落とし、暗いひびを入れる
  - s2 崩れ: 彩度をさらに落とし、ひびを増やし、一部を抜く (透けた穴。切り抜き)、光る所は光の輪を足す
"""
import os
import random
import sys

from PIL import Image, ImageChops, ImageDraw, ImageFilter

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
ROUGH = os.path.join(ROOT, "docs", "review", "abyss_roughs")
OUT = os.path.join(ROOT, "docs", "review", "abyss_painted")

# 選んだ案 (2026-10-22 の選定。docs/design/36 の 4.1)
CHOICE = {
    "ash_trail": "c", "ember_keeper": "c", "soot_child": "a",
    "past_shadow": "c", "nameless_shape": "a",
    "lamp_eater": "a", "ceiling_walker": "a", "heartbeat_mimic": "b", "rope_hanger": "b",
}
SCALE = 2

# 光る点 (ラフに光る画素がないもの。目・膜のこぶ)。骨の UV の範囲の中の割合 (u, v)、半径 (画素)、色
SPOTS = {
    "past_shadow": [("head", 0.35, 0.55, 1, (210, 235, 255)), ("head", 0.65, 0.55, 1, (210, 235, 255))],
    "ceiling_walker": [("head", 0.32, 0.5, 1, (255, 80, 40)), ("head", 0.68, 0.5, 1, (255, 80, 40))],
    "heartbeat_mimic": [("bulge_a", 0.5, 0.5, 2, (255, 90, 70)), ("bulge_b", 0.5, 0.5, 1, (255, 90, 70)),
                        ("bulge_c", 0.5, 0.5, 1, (255, 90, 70))],
    "rope_hanger": [("head", 0.35, 0.5, 1, (255, 220, 160)), ("head", 0.65, 0.5, 1, (255, 220, 160))],
}


def _spots(mob, mesh_path, atlas, glow):
    """光る点を atlas と glow に描く。描いた画素 (小さい画像の座標) → 色 を返す"""
    import json
    m = json.load(open(mesh_path, encoding="utf-8"))
    tw, th = m["texture_size"]
    forced = {}
    aw, ah = atlas.size
    for bname, fu, fv, r, col in SPOTS.get(mob, []):
        uvs = m["bones"][bname]["uvs"]
        us = [u for u, v in uvs]
        vs = [v for u, v in uvs]
        u0, u1, v0, v1 = min(us), max(us), min(vs), max(vs)
        cx = (u0 + (u1 - u0) * fu) * aw
        cy = (v0 + (v1 - v0) * fv) * ah
        for y in range(int(cy) - r, int(cy) + r + 1):
            for x in range(int(cx) - r, int(cx) + r + 1):
                if 0 <= x < aw and 0 <= y < ah and (x - cx) ** 2 + (y - cy) ** 2 <= r * r + 0.5:
                    forced[(x, y)] = col
                    atlas.putpixel((x, y), col + (255,))
                    glow.putpixel((x, y), (255, 255, 255, 255))
    return forced


def _label_grid(img, n=12):
    """opaque の画素を n 色に分け、3x3 の多数決で塊をそろえる。透明は -1"""
    w, h = img.size
    px = img.load()
    rgb = Image.new("RGB", (w, h), (0, 0, 0))
    mask = Image.new("L", (w, h), 0)
    rp, mp = rgb.load(), mask.load()
    for y in range(h):
        for x in range(w):
            r, g, b, a = px[x, y]
            if a > 0:
                rp[x, y] = (r, g, b)
                mp[x, y] = 255
    q = rgb.quantize(colors=n, method=Image.Quantize.MEDIANCUT, dither=Image.Dither.NONE)
    qp = q.load()
    lab = [[-1] * w for _ in range(h)]
    for y in range(h):
        for x in range(w):
            if mp[x, y]:
                lab[y][x] = qp[x, y]
    # 多数決 (3x3)
    out = [row[:] for row in lab]
    for y in range(h):
        for x in range(w):
            if lab[y][x] < 0:
                continue
            cnt = {}
            for dy in (-1, 0, 1):
                for dx in (-1, 0, 1):
                    yy, xx = y + dy, x + dx
                    if 0 <= yy < h and 0 <= xx < w and lab[yy][xx] >= 0:
                        v = lab[yy][xx]
                        cnt[v] = cnt.get(v, 0) + (2 if (dx == 0 and dy == 0) else 1)
            out[y][x] = max(cnt, key=lambda k: cnt[k])
    return out, q.getpalette()[: n * 3]


def _material(mean):
    """面の平均色から素材を決める: 布 (暗い灰) / 石 (中の灰) / 骨・殻 (明るい) / 肉・膜 (赤み) / 炭 (とても暗い)"""
    r, g, b = mean
    lum = 0.3 * r + 0.59 * g + 0.11 * b
    sat = max(mean) - min(mean)
    if lum < 28:
        return "char"
    if r > g + 18 and r > b + 12:
        return "flesh"
    if lum > 150 and sat < 40:
        return "bone"
    if sat < 26:
        return "cloth" if lum < 90 else "stone"
    return "cloth"


def _paint(img, glow, seed, scale=SCALE, forced=None):
    rnd = random.Random(seed)
    a = img.convert("RGBA").resize((img.width * scale, img.height * scale), Image.NEAREST)
    gm = glow.convert("L").resize(a.size, Image.NEAREST)
    w, h = a.size
    lab, _ = _label_grid(img)
    # 拡大して塊を当てはめる
    labs = [[lab[y // scale][x // scale] if lab[y // scale][x // scale] >= 0 else -1 for x in range(w)] for y in range(h)]
    apx = a.load()
    # 塊ごとの平均色 (元の画素)
    sums = {}
    for y in range(h):
        for x in range(w):
            l = labs[y][x]
            if l < 0:
                continue
            r, g, b, _ = apx[x, y]
            s = sums.setdefault(l, [0, 0, 0, 0])
            s[0] += r
            s[1] += g
            s[2] += b
            s[3] += 1
    base = {l: (s[0] / s[3], s[1] / s[3], s[2] / s[3]) for l, s in sums.items()}
    mat = {l: _material(c) for l, c in base.items()}
    # 低い周波数のまだら (塊ごとに別の乱数の畳み込み)
    noise = Image.effect_noise((w // 8 + 1, h // 8 + 1), 40).resize((w, h), Image.BICUBIC).filter(ImageFilter.GaussianBlur(2))
    npx = noise.load()
    grain = Image.effect_noise((w, h), 18).load()
    out = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    op = out.load()
    gpx = gm.load()
    for y in range(h):
        for x in range(w):
            l = labs[y][x]
            if l < 0:
                continue
            r0, g0, b0 = base[l]
            m = mat[l]
            t = (npx[x, y] - 128) / 128.0  # -1..1
            k = 1.0 + 0.10 * t + 0.05 * ((grain[x, y] - 128) / 128.0)
            # 上からの光: 上の画素が別の塊なら明るく、下の画素が別の塊なら暗く
            up = labs[y - 1][x] if y > 0 else -1
            dn = labs[y + 1][x] if y < h - 1 else -1
            lf = labs[y][x - 1] if x > 0 else -1
            rt = labs[y][x + 1] if x < w - 1 else -1
            if up != l:
                k *= 1.12
            if dn != l and dn >= 0:
                k *= 0.82
            if lf != l and lf >= 0:
                k *= 0.95
            if rt != l and rt >= 0:
                k *= 0.9
            if m == "cloth":
                k *= 0.94 + 0.08 * (1 if (x // 3 + rnd.randint(0, 1)) % 2 == 0 else -1) * 0.5 + 0.03 * ((y // 2) % 2)
            if m == "bone" and (x * 7 + y * 13 + seed) % 29 == 0:
                k *= 0.6  # 小さい穴 (骨・殻のくぼみ)
            if m == "flesh":
                k *= 0.9 + 0.1 * t  # 脂のつや
            if m == "char":
                k *= 1.25  # 黒い塊の縁に、わずかに灰色を残す
            # 塊の縁の線 (読みやすさ)
            if any(v >= 0 and v != l for v in (up, dn, lf, rt)) and m != "char":
                k *= 0.78
            rr = min(255, max(0, int(r0 * k)))
            gg = min(255, max(0, int(g0 * k)))
            bb = min(255, max(0, int(b0 * k)))
            op[x, y] = (rr, gg, bb, 255)
            if forced and (x // scale, y // scale) in forced and gpx[x, y] > 16:
                fc = forced[(x // scale, y // scale)]
                op[x, y] = (fc[0], fc[1], fc[2], 255)
                continue
            if gpx[x, y] > 16:
                # 光る所: 元の色を強め、中心を白く
                c = min(255, int(gpx[x, y]))
                op[x, y] = (min(255, int(r0 * 1.35) + 20), min(255, int(g0 * 1.2) + 10), min(255, int(b0 * 1.05)), 255)
                if c > 220:
                    op[x, y] = (255, min(255, int(g0 * 1.5) + 60), min(255, int(b0 * 1.3) + 40), 255)
    # 発光マスク (二値に寄せる)
    # 発光マスクは rough と同じ書き方 (白の不透明 = 光る、透明 = 光らない)
    lit = gm.point(lambda v: 255 if v > 16 else 0)
    gmask = Image.merge("RGBA", (lit, lit, lit, lit))
    return out, gmask


def _stage(img, mask, seed, level):
    """歪み (1) と崩れ (2)。img は描いたあとの絵 (RGBA)、mask は発光マスク"""
    rnd = random.Random(seed * 31 + level)
    im = img.copy()
    w, h = im.size
    # 色を落とす (灰へ寄せる)
    gray = im.convert("L").convert("RGBA")
    amount = 0.18 if level == 1 else 0.45
    im = Image.blend(im, Image.merge("RGBA", (gray.split()[0], gray.split()[0], gray.split()[0], im.split()[3])), amount)
    # ひび (暗い折れ線)
    d = ImageDraw.Draw(im)
    cracks = 3 if level == 1 else 8
    px = im.load()
    opaque = [(x, y) for y in range(h) for x in range(w) if px[x, y][3] > 0]
    if opaque:
        for _ in range(cracks):
            x, y = rnd.choice(opaque)
            pts = [(x, y)]
            for _ in range(rnd.randint(6, 14)):
                x += rnd.choice((-1, 0, 1)) * SCALE
                y += rnd.choice((0, 1, 1)) * SCALE
                pts.append((x, y))
            d.line(pts, fill=(18, 14, 16, 255), width=1)
    if level == 2:
        # 抜け (切り抜き): 面の中に小さい穴を数か所
        holes = Image.new("L", (w, h), 0)
        hd = ImageDraw.Draw(holes)
        for _ in range(7):
            x, y = rnd.choice(opaque)
            r = rnd.randint(2, 4)
            hd.ellipse((x - r, y - r, x + r, y + r), fill=255)
        alpha = im.split()[3]
        alpha = ImageChops.multiply(alpha, holes.point(lambda v: 0 if v else 255))
        im.putalpha(alpha)
        # 光の輪 (発光の周り)
        ring = mask.convert("RGBA").split()[3].filter(ImageFilter.MaxFilter(3))
        halo = Image.new("RGBA", (w, h), (255, 170, 60, 0))
        halo.putalpha(ring.point(lambda v: 90 if v else 0))
        im = Image.alpha_composite(halo, im)
    return im


def main():
    os.makedirs(OUT, exist_ok=True)
    for mob, opt in CHOICE.items():
        stem = "%s_%s" % (mob, opt)
        src = os.path.join(ROUGH, mob, opt)
        atlas = Image.open(os.path.join(src, stem + "_atlas.png")).convert("RGBA")
        glow = Image.open(os.path.join(src, stem + "_glowmask.png")).convert("RGBA")
        seed = sum(ord(c) for c in mob)
        mesh_path = os.path.join(src, stem + "_mesh.json")
        forced = _spots(mob, mesh_path, atlas, glow)
        base, gmask = _paint(atlas, glow, seed, forced=forced)
        s1 = _stage(base, gmask, seed, 1)
        s2 = _stage(base, gmask, seed, 2)
        for tag, img in (("base", base), ("s1", s1), ("s2", s2)):
            img.save(os.path.join(OUT, "%s_%s_atlas.png" % (mob, tag)))
            gmask.save(os.path.join(OUT, "%s_%s_glowmask.png" % (mob, tag)))
        print("painted", mob, base.size)


if __name__ == "__main__":
    sys.stdout.reconfigure(encoding="utf-8")
    main()
