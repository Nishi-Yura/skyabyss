"""面に描くドット絵の道具 (kit.py の箱の paint に渡す)。

painter(face, W, H, box) -> (H, W, 4) uint8 か (img, glow)。face は n/s/e/w/u/d。
色は "#rrggbb"。陰影はアニメ塗り: 上を少し明るく、下 1/4 を暗く、縁 1 画素を締める (小さい面では省く)。
"""
from __future__ import annotations

import numpy as np


def C(h):
    h = h.lstrip("#")
    return np.array([int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16)], float)


def mix(a, b, t):
    return a * (1 - t) + b * t


def blank(W, H, col=None, a=255):
    img = np.zeros((H, W, 4), float)
    if col is not None:
        img[..., :3] = C(col) if isinstance(col, str) else col
        img[..., 3] = a
    return img


def out(img):
    return np.clip(np.round(img), 0, 255).astype(np.uint8)


def rnd(box, salt=0):
    s = int(abs(box.frm[0] * 131 + box.frm[1] * 71 + box.frm[2] * 37 + box.size[0] * 13 + box.size[1] * 7 + salt * 977) * 1000) % (2 ** 31)
    return np.random.default_rng(s)


def shade_rows(H, light, base, dark, top=0.3, bottom=0.72):
    t = (np.arange(H) + 0.5) / H
    rows = np.zeros((H, 3))
    for i, x in enumerate(t):
        if x < top:
            rows[i] = mix(light, base, x / top)
        elif x > bottom:
            rows[i] = mix(base, dark, (x - bottom) / (1 - bottom))
        else:
            rows[i] = base
    return rows


def edge(img, dark, top_light=None, sides=True, bottom=True, top=False, k=0.55):
    H, W = img.shape[:2]
    if W >= 3 and sides:
        img[:, 0, :3] = mix(img[:, 0, :3], dark, k)
        img[:, -1, :3] = mix(img[:, -1, :3], dark, k)
    if H >= 3 and bottom:
        img[-1, :, :3] = mix(img[-1, :, :3], dark, k)
    if H >= 3 and top:
        img[0, :, :3] = mix(img[0, :, :3], dark, k)
    if H >= 4 and top_light is not None:
        img[0, 1:-1, :3] = mix(img[0, 1:-1, :3], top_light, 0.6)
    return img


def round_shade(img, d, curve):
    """面の左右の端ほど暗く (丸い面に見せる)。"""
    W = img.shape[1]
    if W < 3 or curve <= 0:
        return img
    u = (np.arange(W) + 0.5) / W * 2 - 1
    k = (np.abs(u) ** 2.2 * curve)[None, :, None]
    img[..., :3] = img[..., :3] * (1 - k) + d * k
    return img


def cloth(base, light=None, dark=None, line=None, edges=True, faces=None, over=None, flat=False, hi_top=True, curve=0.32):
    """布・革・金属などのふつうの面。faces = {面: painter} で面ごとに差し替え、over(img, face, W, H, box) で上描き。
    curve = 横の面の左右の端を暗くする強さ (丸く見せる)。"""
    b, l, d = C(base), C(light) if light else mix(C(base), np.array([255, 255, 255.]), 0.22), C(dark) if dark else C(base) * 0.62
    ln = C(line) if line else d * 0.7

    def p(face, W, H, box):
        if faces and face in faces:
            return faces[face](face, W, H, box)
        img = blank(W, H, b)
        if not flat:
            if face in "nsew":
                img[..., :3] = shade_rows(H, l, b, d)[:, None, :]
                round_shade(img, d, curve)
            elif face == "u":
                img[..., :3] = mix(b, l, 0.6)
            else:
                img[..., :3] = mix(b, d, 0.7)
        if edges and face in "nsew":
            edge(img, ln, l if hi_top else None)
        elif edges and face in "ud" and min(W, H) >= 4:
            edge(img, ln, None, top=True, k=0.35)
        if over:
            r = over(img, face, W, H, box)
            if r is not None:
                img = r
        return out(img)
    return p


def skin(base="#fde4d6", shadow="#f0c3b2", dark="#dea292"):
    b, s, d = C(base), C(shadow), C(dark)

    def p(face, W, H, box):
        img = blank(W, H, b)
        if face in "nsew":
            t = (np.arange(H) + 0.5) / H
            for i, x in enumerate(t):
                if x > 0.8:
                    img[i, :, :3] = mix(b, s, (x - 0.8) / 0.2)
            if W >= 4:
                img[:, 0, :3] = mix(img[:, 0, :3], s, 0.6)
                img[:, -1, :3] = mix(img[:, -1, :3], s, 0.6)
        elif face == "d":
            img[..., :3] = s
        return out(img)
    return p


def hair(base, dark, light, hi=None, jag=0.0, jag_w=(3, 6), band=None, faces=None, seed=0, root_dark=True, strand=(1, 3)):
    """髪。縦の筋 (明暗の細い帯)、下の毛先ほど暗い、band = 天使の輪 (面の高さの割合)。jag = 毛先を透かしてギザギザに (面の高さの割合まで)。"""
    b, d, l = C(base), C(dark), C(light)
    h = C(hi) if hi else None

    def p(face, W, H, box):
        if faces and face in faces:
            return faces[face](face, W, H, box)
        r = rnd(box, seed + ord(face))
        img = blank(W, H, b)
        if face in "ud":
            img[..., :3] = mix(b, l if face == "u" else d, 0.35)
            if face == "u":
                # 上から見た渦: 外へ向かう筋
                for x in range(W):
                    if r.random() < 0.35:
                        img[:, x, :3] = mix(img[:, x, :3], l, 0.35)
            return out(img)
        tone = np.zeros(W)
        x = 0
        while x < W:
            w = int(r.integers(strand[0], strand[1] + 1))
            v = r.choice([-1.0, -0.5, 0.0, 0.0, 0.5, 1.0])
            tone[x:x + w] = v
            x += w
        rows = shade_rows(H, mix(b, l, 0.5), b, d, top=0.25, bottom=0.55)
        for x in range(W):
            col = rows.copy()
            if tone[x] > 0:
                col = mix(col, l, 0.3 * tone[x])
            elif tone[x] < 0:
                col = mix(col, d, 0.35 * -tone[x])
            img[:, x, :3] = col
            # 筋の境は暗い線
            if x > 0 and tone[x] != tone[x - 1] and r.random() < 0.6:
                img[:, x, :3] = mix(img[:, x, :3], d, 0.45)
        if band is not None and h is not None and face in "nsew":
            y0 = int(band * H)
            for x in range(W):
                yy = y0 + int(round(np.sin(x * 1.3 + seed) * 1.2))
                for k in range(max(1, H // 14)):
                    if 0 <= yy + k < H:
                        img[yy + k, x, :3] = mix(img[yy + k, x, :3], h, 0.85 if (x + k) % 3 else 0.5)
                if 0 <= yy - 1 < H:
                    img[yy - 1, x, :3] = mix(img[yy - 1, x, :3], l, 0.5)
        if jag > 0 and face in "nsew":
            cut = jag_edge(W, H, jag, jag_w, r)
            for x in range(W):
                img[cut[x]:, x, 3] = 0
                if cut[x] - 1 >= 0:
                    img[max(0, cut[x] - 2):cut[x], x, :3] = mix(img[max(0, cut[x] - 2):cut[x], x, :3], d, 0.5)
        return out(img)
    return p


def jag_edge(W, H, depth, width, r):
    """毛先のギザギザ: 列ごとの切れ目の行 (三角の房を並べる)。"""
    cut = np.full(W, H)
    x = 0
    while x < W:
        w = int(r.integers(width[0], width[1] + 1))
        dd = int(round(H * depth * r.uniform(0.45, 1.0)))
        for k in range(w):
            if x + k >= W:
                break
            t = abs((k + 0.5) / w - 0.5) * 2  # 房の中心 0 → 端 1
            cut[x + k] = H - int(round(dd * t))
        x += w
    return np.clip(cut, 1, H)


def lace(base="#eee8f2", shade="#c8bfd3", holes=True, scallop=True, faces=None, band=None):
    """白いレース。穴の模様と、下の縁の波 (透かし)。"""
    b, s = C(base), C(shade)

    def p(face, W, H, box):
        if faces and face in faces:
            return faces[face](face, W, H, box)
        img = blank(W, H, b)
        if face in "nsew":
            img[..., :3] = shade_rows(H, b, b, s, top=0.2, bottom=0.6)[:, None, :]
            if holes and H >= 4:
                for y in range(1, H - 2, 3):
                    for x in range((y // 3) % 2 * 2, W, 4):
                        if x < W:
                            img[y, x, :3] = s * 0.92
            if scallop and H >= 3:
                for x in range(W):
                    if x % 4 in (0, 3):
                        img[H - 1, x, 3] = 0
                    if x % 4 == 0 and H >= 4:
                        img[H - 2, x, 3] = 0
            if band is not None:
                img[0:max(1, H // 5), :, :3] = C(band)
        return out(img)
    return p


def clear():
    def p(face, W, H, box):
        return np.zeros((H, W, 4), np.uint8)
    return p


def sprite(rows, pal):
    """文字の絵 → RGBA。'.' と ' ' は透明。pal = {文字: "#rrggbb"}。"""
    H = len(rows)
    W = max(len(r) for r in rows)
    img = np.zeros((H, W, 4), float)
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch in ". ":
                continue
            img[y, x, :3] = C(pal[ch])
            img[y, x, 3] = 255
    return img


def stamp(img, spr, x, y, flip=False):
    if flip:
        spr = spr[:, ::-1]
    H, W = spr.shape[:2]
    for yy in range(H):
        for xx in range(W):
            if spr[yy, xx, 3] > 0 and 0 <= y + yy < img.shape[0] and 0 <= x + xx < img.shape[1]:
                img[y + yy, x + xx] = spr[yy, xx]
    return img


def circle_alpha(W, H, inner=0.0):
    yy, xx = np.mgrid[0:H, 0:W]
    dx = (xx + 0.5) / W * 2 - 1
    dy = (yy + 0.5) / H * 2 - 1
    r = np.sqrt(dx * dx + dy * dy)
    return (r <= 1.0) & (r >= inner)


def band(img, y0, y1, col, hi=None, lo=None):
    """横の帯 (行 y0..y1)。hi = 上の 1 行、lo = 下の 1 行の色。"""
    H = img.shape[0]
    y0, y1 = max(0, y0), min(H, y1)
    if y1 <= y0:
        return img
    img[y0:y1, :, :3] = C(col) if isinstance(col, str) else col
    if hi:
        img[y0, :, :3] = C(hi)
    if lo and y1 - 1 > y0:
        img[y1 - 1, :, :3] = C(lo)
    return img


def vband(img, x0, x1, col, hi=None):
    W = img.shape[1]
    x0, x1 = max(0, x0), min(W, x1)
    if x1 <= x0:
        return img
    img[:, x0:x1, :3] = C(col) if isinstance(col, str) else col
    if hi:
        img[:, x0, :3] = C(hi)
    return img


def dot(img, x, y, col, size=1):
    H, W = img.shape[:2]
    for yy in range(y, y + size):
        for xx in range(x, x + size):
            if 0 <= yy < H and 0 <= xx < W:
                img[yy, xx, :3] = C(col) if isinstance(col, str) else col
                img[yy, xx, 3] = 255
    return img


def metal(base, light=None, dark=None, line=None, streak=True, over=None, faces=None):
    """磨いた金属: 上が明るく下へ暗く、斜めの光の筋、縁は暗い線。"""
    b = C(base)
    l = C(light) if light else mix(b, np.array([255, 255, 255.]), 0.45)
    d = C(dark) if dark else b * 0.55

    def o(img, face, W, H, box):
        if face in "nsew":
            img[..., :3] = shade_rows(H, l, b, d, top=0.35, bottom=0.6)[:, None, :]
            round_shade(img, d, 0.3)
            if streak and W >= 4 and H >= 4:
                for y in range(H):
                    x = int(W * 0.3) - y // 2
                    for k in (0, 1):
                        if 0 <= x + k < W:
                            img[y, x + k, :3] = mix(img[y, x + k, :3], l * 1.08, 0.45)
            edge(img, C(line) if line else d * 0.7, l)
        if over:
            r = over(img, face, W, H, box)
            if r is not None:
                img = r
        return img
    return cloth(base, light, dark, line, edges=False, over=o, faces=faces)
