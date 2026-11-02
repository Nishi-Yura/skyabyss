"""語りの絵 (Undertale 風のドット絵の紙芝居) を作る道具。

絵は 240x135 (16:9) の小さな画面に描き、ゲームでは整数倍に拡大して出す。色は一枚ごとに少ない色の表 (8〜14 色) に落とし、
空や光のぼかしは 4x4 の網目 (Bayer) で色の表の間をつなぐ。巨神や敵は、ゲームに入っている低ポリの形 (assets/skyabyss/lowpoly/<名>.json)
をこの道具の小さな描き手で描いて、同じ色の表に落とす (形がゲームと同じになり、枚数を増やしても絵柄がそろう)。
"""
from __future__ import annotations

import json
import math
from pathlib import Path

import numpy as np
from PIL import Image

ROOT = Path(__file__).resolve().parents[3]
LOWPOLY = ROOT / "src/main/resources/assets/skyabyss/lowpoly"
TEX = ROOT / "src/main/resources/assets/skyabyss/textures/lowpoly"
W, H = 240, 135

BAYER4 = (np.array([[0, 8, 2, 10], [12, 4, 14, 6], [3, 11, 1, 9], [15, 7, 13, 5]]) + 0.5) / 16.0


def hexrgb(h):
    h = h.lstrip("#")
    return np.array([int(h[i:i + 2], 16) for i in (0, 2, 4)], float)


class Canvas:
    """RGB の浮動小数の画面。最後に色の表へ落とす"""

    def __init__(self, w=W, h=H, color=(0, 0, 0)):
        self.w, self.h = w, h
        self.px = np.zeros((h, w, 3), float) + np.array(color, float)
        self.yy, self.xx = np.mgrid[0:h, 0:w]

    # ---- 塗り
    def vgrad(self, stops, y0=0, y1=None):
        """縦のぼかし。stops = [(0..1, (r,g,b)), ...]"""
        y1 = self.h if y1 is None else y1
        t = np.clip((self.yy - y0) / max(1, (y1 - y0)), 0, 1)
        out = np.zeros_like(self.px)
        pos = [s[0] for s in stops]
        cols = [np.array(s[1], float) for s in stops]
        for c in range(3):
            out[..., c] = np.interp(t, pos, [col[c] for col in cols])
        m = (self.yy >= y0) & (self.yy < y1)
        self.px[m] = out[m]

    def disc(self, cx, cy, r, color, soft=0.0, alpha=1.0):
        d = np.hypot(self.xx - cx, self.yy - cy)
        a = np.clip((r - d) / max(soft, 1e-6) + 0.5, 0, 1) if soft > 0 else (d <= r).astype(float)
        self.blend(a * alpha, color)

    def glow(self, cx, cy, r, color, strength=1.0):
        d = np.hypot(self.xx - cx, self.yy - cy)
        a = np.clip(1 - d / r, 0, 1) ** 2 * strength
        self.px += a[..., None] * np.array(color, float)

    def blend(self, a, color):
        a = np.clip(a, 0, 1)[..., None]
        self.px = self.px * (1 - a) + np.array(color, float) * a

    def poly(self, pts, color, alpha=1.0):
        """多角形を塗る (偶奇の内外)"""
        pts = np.array(pts, float)
        inside = np.zeros((self.h, self.w), bool)
        x, y = self.xx + 0.5, self.yy + 0.5
        n = len(pts)
        for i in range(n):
            x0, y0 = pts[i]
            x1, y1 = pts[(i + 1) % n]
            cond = ((y0 > y) != (y1 > y)) & (x < (x1 - x0) * (y - y0) / ((y1 - y0) + 1e-9) + x0)
            inside ^= cond
        self.blend(inside.astype(float) * alpha, color)

    def stars(self, n, seed, color=(230, 230, 240), ymax=None, big=0.1):
        r = np.random.default_rng(seed)
        ymax = self.h if ymax is None else ymax
        for _ in range(n):
            x, y = r.integers(0, self.w), r.integers(0, ymax)
            b = r.uniform(0.35, 1.0)
            self.px[y, x] = self.px[y, x] * (1 - b) + np.array(color) * b
            if r.random() < big:
                for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                    if 0 <= x + dx < self.w and 0 <= y + dy < self.h:
                        self.px[y + dy, x + dx] = self.px[y + dy, x + dx] * 0.6 + np.array(color) * 0.4 * b

    def ridge(self, base_y, amp, freq, seed, color, rough=0.5):
        """横に続く山の稜線 (下を塗る)"""
        r = np.random.default_rng(seed)
        xs = np.arange(self.w)
        y = np.full(self.w, float(base_y))
        a, f = amp, freq
        for _ in range(5):
            ph = r.uniform(0, math.tau)
            y -= a * (0.5 + 0.5 * np.sin(xs * f + ph))
            a *= rough
            f *= 2.1
        m = self.yy >= y[None, :]
        self.px[m] = np.array(color, float)
        return y

    def sprite(self, img: np.ndarray, x, y, alpha=None):
        """RGBA の小さな絵を貼る (img は 0..255 の H×W×4)"""
        h, w = img.shape[:2]
        x0, y0 = int(round(x)), int(round(y))
        xs0, ys0 = max(0, -x0), max(0, -y0)
        xs1, ys1 = min(w, self.w - x0), min(h, self.h - y0)
        if xs1 <= xs0 or ys1 <= ys0:
            return
        sub = img[ys0:ys1, xs0:xs1].astype(float)
        a = sub[..., 3:4] / 255.0 * (1.0 if alpha is None else alpha)
        region = self.px[y0 + ys0:y0 + ys1, x0 + xs0:x0 + xs1]
        self.px[y0 + ys0:y0 + ys1, x0 + xs0:x0 + xs1] = region * (1 - a) + sub[..., :3] * a

    # ---- 仕上げ
    def quantize(self, palette, dither=1.0):
        """色の表へ落とす。近い二色のあいだを網目でつなぐ"""
        pal = np.array([hexrgb(c) if isinstance(c, str) else np.array(c, float) for c in palette])
        px = self.px.reshape(-1, 3)
        d = ((px[:, None, :] - pal[None, :, :]) ** 2).sum(-1)
        order = np.argsort(d, axis=1)
        i1, i2 = order[:, 0], order[:, 1]
        c1, c2 = pal[i1], pal[i2]
        seg = c2 - c1
        t = np.clip(((px - c1) * seg).sum(-1) / np.maximum((seg * seg).sum(-1), 1e-6), 0, 1)
        thr = np.tile(BAYER4, (self.h // 4 + 1, self.w // 4 + 1))[:self.h, :self.w].reshape(-1)
        pick = np.where(t * dither > thr, i2, i1)
        out = pal[pick].reshape(self.h, self.w, 3)
        return Image.fromarray(out.clip(0, 255).astype(np.uint8))


# ---------------------------------------------------------------- 低ポリの形を小さく描く
def load_mesh(name):
    d = json.loads((LOWPOLY / f"{name}.json").read_text(encoding="utf-8"))
    tex_name = d.get("texture", name).split("/")[-1].replace(".png", "")
    tex_path = TEX / f"{tex_name}.png"
    if not tex_path.exists():
        tex_path = TEX / f"{name}.png"
    tex = np.array(Image.open(tex_path).convert("RGBA"), float)
    glow_path = TEX / f"{tex_path.stem}_glowmask.png"
    glow = np.array(Image.open(glow_path).convert("L"), float) / 255.0 if glow_path.exists() else None
    tris, uvs = [], []
    for b in d["bones"].values():
        v = np.array(b["vertices"], float)
        uv = np.array(b["uvs"], float)
        for t in b["triangles"]:
            tris.append(v[t])
            uvs.append(uv[t])
    return np.array(tris), np.array(uvs), tex, glow


def rot(yaw, pitch, roll=0.0):
    cy, sy = math.cos(math.radians(yaw)), math.sin(math.radians(yaw))
    cp, sp = math.cos(math.radians(pitch)), math.sin(math.radians(pitch))
    cr, sr = math.cos(math.radians(roll)), math.sin(math.radians(roll))
    ry = np.array([[cy, 0, sy], [0, 1, 0], [-sy, 0, cy]])
    rx = np.array([[1, 0, 0], [0, cp, -sp], [0, sp, cp]])
    rz = np.array([[cr, -sr, 0], [sr, cr, 0], [0, 0, 1]])
    return rz @ rx @ ry


def render_mesh(name, size, yaw=30, pitch=10, roll=0, light=(-0.5, 0.8, -0.4), ambient=0.45, tint=None, silhouette=None, scale=None, pad=2):
    """形を size (w, h) の RGBA に描く (正射影・平らな陰)。tint = 掛ける色 (夜・霞)。silhouette = 一色の影絵の色。
    戻り値: (画像 0..255 の h×w×4, 縮尺)"""
    tris, uvs, tex, glow = load_mesh(name)
    R = rot(yaw, pitch, roll)
    P = tris.reshape(-1, 3) @ R.T
    P = P.reshape(-1, 3, 3)
    w, h = size
    lo, hi = P.reshape(-1, 3).min(0), P.reshape(-1, 3).max(0)
    if scale is None:
        scale = min((w - 2 * pad) / max(hi[0] - lo[0], 1e-6), (h - 2 * pad) / max(hi[1] - lo[1], 1e-6))
    cx, cy = (lo[0] + hi[0]) / 2, (lo[1] + hi[1]) / 2
    sx = (P[..., 0] - cx) * scale + w / 2
    sy = h / 2 - (P[..., 1] - cy) * scale
    z = P[..., 2]
    img = np.zeros((h, w, 4), float)
    zbuf = np.full((h, w), np.inf)
    L = np.array(light, float)
    L /= np.linalg.norm(L)
    th, tw = tex.shape[:2]
    n_all = np.cross(P[:, 1] - P[:, 0], P[:, 2] - P[:, 0])
    for i in range(len(P)):
        xs, ys, zs = sx[i], sy[i], z[i]
        x0, x1 = int(max(0, math.floor(xs.min()))), int(min(w - 1, math.ceil(xs.max())))
        y0, y1 = int(max(0, math.floor(ys.min()))), int(min(h - 1, math.ceil(ys.max())))
        if x1 < x0 or y1 < y0:
            continue
        n = n_all[i]
        nn = np.linalg.norm(n)
        if nn < 1e-9:
            continue
        n = n / nn
        if n[2] > 0:
            n = -n
        shade = ambient + (1 - ambient) * max(0.0, float(n @ L))
        gx, gy = np.meshgrid(np.arange(x0, x1 + 1) + 0.5, np.arange(y0, y1 + 1) + 0.5)
        (ax, ay), (bx, by), (qx, qy) = (xs[0], ys[0]), (xs[1], ys[1]), (xs[2], ys[2])
        den = (by - qy) * (ax - qx) + (qx - bx) * (ay - qy)
        if abs(den) < 1e-9:
            continue
        l1 = ((by - qy) * (gx - qx) + (qx - bx) * (gy - qy)) / den
        l2 = ((qy - ay) * (gx - qx) + (ax - qx) * (gy - qy)) / den
        l3 = 1 - l1 - l2
        m = (l1 >= -1e-4) & (l2 >= -1e-4) & (l3 >= -1e-4)
        if not m.any():
            continue
        zz = l1 * zs[0] + l2 * zs[1] + l3 * zs[2]
        sub = zbuf[y0:y1 + 1, x0:x1 + 1]
        m &= zz < sub
        if not m.any():
            continue
        u = l1 * uvs[i, 0, 0] + l2 * uvs[i, 1, 0] + l3 * uvs[i, 2, 0]
        v = l1 * uvs[i, 0, 1] + l2 * uvs[i, 1, 1] + l3 * uvs[i, 2, 1]
        tx = np.clip((u * tw).astype(int), 0, tw - 1)
        ty = np.clip((v * th).astype(int), 0, th - 1)
        col = tex[ty, tx, :3] * shade
        if glow is not None:
            gl = glow[ty, tx][..., None]
            col = col * (1 - gl) + tex[ty, tx, :3] * gl * 1.15
        if tint is not None:
            col = col * np.array(tint, float) / 255.0
        if silhouette is not None:
            col = np.broadcast_to(np.array(silhouette, float), col.shape)
        region = img[y0:y1 + 1, x0:x1 + 1]
        region[m, :3] = col[m]
        region[m, 3] = 255
        sub[m] = zz[m]
    return img, scale


def text_block(lines):
    return "\n".join(lines)
