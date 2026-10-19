#!/usr/bin/env python3
"""対話画面の肖像 (128x128 のドット絵) を、3 案 x 3 人 x 3 表情 = 27 枚、一枚ずつ原寸で描く。

  python tools/gen_portraits.py              -> docs/review/portraits/{a,b,c}_{npc}_{face}.png と prev_*.png
  python tools/gen_portraits.py --install a  -> 選んだ案を assets/skyabyss/textures/gui/npc/face/{npc}_{face}.png へ入れる

人: storyteller (頭巾の語り部)、whale_chaser (イサナ。つば広帽の旅人)、artisan (旅の職人。額の眼鏡)。いまのゲーム内の低ポリの姿に合わせる。
表情: neutral / smile / grave (会話の節の face と同じ)。光はいつも左上の手前、胸から上、顔の中心は (64, 66)。

描き方: 形はマスク (4 倍で塗って縮め、閾値で切る = 画素の縁はくっきり) を重ね、陰影はマスクをぼかした高さの法線と光の内積を
少数の色の段 (ランプ) に切って付ける。案ごとに違うのは段の数・ディザ・縁取り・背景・光の当て方だけで、絵の形は同じ。
  A  粒子のにじむ絵画調: 5 段 + ディザ、縁に冷たい逆光、暗い背景に光の暈
  B  太い輪郭の JRPG 調: 3 段のセル塗り、2 画素の黒い縁取り、放射の帯の背景
  C  くすんだ木版調: 4 段のくすんだ色、斜線の影、紙のざらつきと木目の背景
"""
import argparse
import colorsys
import os
import shutil

import numpy as np
from PIL import Image, ImageDraw, ImageFilter

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "docs", "review", "portraits")
FACE_DIR = os.path.join(ROOT, "src", "main", "resources", "assets", "skyabyss", "textures", "gui", "npc", "face")
N = 128
SS = 4
NPCS = ["storyteller", "whale_chaser", "artisan"]
FACES = ["neutral", "smile", "grave"]
LIGHT = np.array([-0.45, -0.6, 0.66])
LIGHT = LIGHT / np.linalg.norm(LIGHT)
BAYER = np.array([[0, 8, 2, 10], [12, 4, 14, 6], [3, 11, 1, 9], [15, 7, 13, 5]]) / 16.0 - 0.5

# 素材の基準色 (段の真ん中)
MATS = {
    "cloak": (52, 76, 152), "hood": (64, 90, 176), "trim": (150, 176, 214), "inner": (22, 28, 64),
    "shawl": (86, 118, 196), "silver": (206, 212, 224), "gold": (222, 170, 64), "glow": (92, 224, 236),
    "skin_old": (214, 168, 136), "skin_tan": (206, 146, 100), "skin_brown": (176, 118, 80),
    "coat": (60, 112, 74), "leather": (126, 84, 50), "hair_dk": (52, 38, 32), "scarf": (186, 54, 48),
    "hat": (128, 88, 52), "hatband": (196, 192, 176), "feather": (232, 228, 214),
    "shirt": (118, 112, 104), "apron": (116, 76, 46), "stone": (150, 150, 138), "hair_gr": (96, 84, 72),
    "strap": (84, 58, 40), "brass": (186, 142, 62), "lens": (150, 204, 224), "sclera": (236, 230, 218),
    "lip": (170, 84, 80), "line": (60, 36, 34),
}

# 背景 (A: 上の色, 下の色, 光の暈 / B: 地, 帯 / C: 円)
BG = {
    "storyteller": dict(top=(10, 16, 40), bot=(26, 44, 82), glow=(70, 120, 190), b=(40, 58, 120), b2=(56, 80, 150), c=(118, 128, 156)),
    "whale_chaser": dict(top=(12, 28, 34), bot=(34, 70, 72), glow=(236, 172, 96), b=(34, 98, 102), b2=(52, 126, 120), c=(120, 148, 132)),
    "artisan": dict(top=(26, 14, 14), bot=(70, 34, 24), glow=(240, 130, 60), b=(158, 76, 44), b2=(186, 100, 56), c=(176, 118, 86)),
}

STYLES = {
    "a": dict(title="A 粒子のにじむ絵画調", levels=5, dither=0.55, outline=0, rim=True),
    "b": dict(title="B 太い輪郭の JRPG 調", levels=3, dither=0.0, outline=2, rim=False),
    "c": dict(title="C くすんだ木版調", levels=4, dither=0.0, outline=1, rim=False, hatch=True),
}


# ---- マスク ------------------------------------------------------------------------------------------------
def _mask(fn):
    im = Image.new("L", (N * SS, N * SS), 0)
    fn(ImageDraw.Draw(im))
    return np.array(im.resize((N, N), Image.BOX)) >= 128


def ell(cx, cy, rx, ry):
    return _mask(lambda d: d.ellipse([(cx - rx) * SS, (cy - ry) * SS, (cx + rx) * SS, (cy + ry) * SS], fill=255))


def poly(pts):
    return _mask(lambda d: d.polygon([(x * SS, y * SS) for x, y in pts], fill=255))


def rrect(x0, y0, x1, y1, r):
    return _mask(lambda d: d.rounded_rectangle([x0 * SS, y0 * SS, x1 * SS, y1 * SS], r * SS, fill=255))


def bez(p0, p1, p2, n=14):
    t = np.linspace(0, 1, n)[:, None]
    p0, p1, p2 = map(np.array, (p0, p1, p2))
    return [tuple(p) for p in (1 - t) ** 2 * p0 + 2 * (1 - t) * t * p1 + t ** 2 * p2]


def stroke(pts, w):
    def fn(d):
        pp = [(x * SS, y * SS) for x, y in pts]
        d.line(pp, fill=255, width=max(1, int(w * SS)), joint="curve")
        r = w * SS / 2
        for x, y in (pp[0], pp[-1]):
            d.ellipse([x - r, y - r, x + r, y + r], fill=255)
    return _mask(fn)


def taper(pts, w0, w1):
    """太さが変わる線 (眉・髪の房)"""
    def fn(d):
        n = len(pts)
        for i in range(n - 1):
            w = (w0 + (w1 - w0) * i / max(1, n - 2)) * SS
            (x0, y0), (x1, y1) = pts[i], pts[i + 1]
            d.line([(x0 * SS, y0 * SS), (x1 * SS, y1 * SS)], fill=255, width=max(1, int(w)))
            r = w / 2
            d.ellipse([x1 * SS - r, y1 * SS - r, x1 * SS + r, y1 * SS + r], fill=255)
    return _mask(fn)


def mirror(m):
    return m[:, ::-1]


def dilate(m, k):
    out = m.copy()
    for _ in range(k):
        o = out.copy()
        o[1:] |= out[:-1]
        o[:-1] |= out[1:]
        o[:, 1:] |= out[:, :-1]
        o[:, :-1] |= out[:, 1:]
        out = o
    return out


def blur(a, sigma):
    im = Image.fromarray((np.clip(a, 0, 1) * 255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(sigma))
    return np.array(im).astype(float) / 255.0


def shift(a, dx, dy):
    out = np.zeros_like(a)
    xs, xd = (slice(dx, None), slice(0, N - dx)) if dx >= 0 else (slice(0, N + dx), slice(-dx, None))
    ys, yd = (slice(dy, None), slice(0, N - dy)) if dy >= 0 else (slice(0, N + dy), slice(-dy, None))
    out[ys, xs] = a[yd, xd]
    return out


# ---- 色 ----------------------------------------------------------------------------------------------------
def make_ramp(base, style):
    s = STYLES[style]
    L = s["levels"]
    h, sat, v = colorsys.rgb_to_hsv(*[c / 255 for c in base])
    k0 = min(L - 1, int(0.5 * L))
    t0 = (k0 + 0.5) / L * 2 - 1
    out = []
    for k in range(L):
        t = (k + 0.5) / L * 2 - 1 - t0
        if style == "a":
            sh, hi, sa = 0.55, 0.62, 1.0
        elif style == "b":
            sh, hi, sa = 0.5, 0.5, 1.12
        else:
            sh, hi, sa = 0.42, 0.45, 0.58
        if t < 0:
            vv = v * (1 + sh * t)
            ss = min(1, sat * sa * (1 - 0.22 * t))
            hh = _hue_toward(h, 0.68, 0.06 * -t)
        else:
            vv = v + (1 - v) * hi * t
            ss = sat * sa * (1 - 0.38 * t)
            hh = _hue_toward(h, 0.12, 0.035 * t)
        r, g, b = colorsys.hsv_to_rgb(hh % 1.0, max(0, min(1, ss)), max(0, min(1, vv)))
        c = np.array([r, g, b]) * 255
        if style == "c":
            c = c * 0.86 + np.array([214, 198, 160]) * 0.14
        out.append(c)
    return np.round(np.array(out)).astype(int)


def _hue_toward(h, target, amt):
    d = (target - h + 0.5) % 1.0 - 0.5
    return h + d * min(1.0, amt * 4)


# ---- 描画の土台 --------------------------------------------------------------------------------------------
class Art:
    def __init__(self, style, npc):
        self.style = style
        self.npc = npc
        self.cfg = STYLES[style]
        self.ramps = {k: make_ramp(v, style) for k, v in MATS.items()}
        self.val = np.zeros((N, N))
        self.rid = np.full((N, N), -1)
        self.own = np.full((N, N), -1)
        self.flat = np.zeros((N, N, 3), dtype=int)
        self.isflat = np.zeros((N, N), bool)
        self.names = []
        self.rims = []
        self.ink = (16, 12, 26) if style == "b" else (52, 36, 32)
        yy, xx = np.mgrid[0:N, 0:N]
        self.X, self.Y = xx.astype(float), yy.astype(float)

    def paint(self, mask, mat, sigma=3.0, ol=0, tex=None, v=None, rim=None, soft=1.0):
        if ol and self.cfg["outline"]:
            k = min(ol, self.cfg["outline"]) if self.style == "c" else ol
            ring = dilate(mask, k) & ~mask
            self.isflat[ring] = True
            self.flat[ring] = self.ink
            self.rid[ring] = -1
            self.own[ring] = -2
        pid = len(self.names)
        self.names.append(mat)
        if v is None:
            h = blur(mask.astype(float), sigma)
            gy, gx = np.gradient(h)
            kk = sigma * 2.3
            nx, ny = -gx * kk, -gy * kk
            nz = np.ones_like(h)
            nn = np.sqrt(nx * nx + ny * ny + nz * nz)
            ndl = (nx * LIGHT[0] + ny * LIGHT[1] + nz * LIGHT[2]) / nn
            val = 0.5 + (ndl - LIGHT[2]) * 1.15 * soft
        else:
            val = np.full((N, N), float(v))
        if tex is not None:
            val = val + tex(self.X, self.Y)
        self.val[mask] = val[mask]
        self.rid[mask] = pid
        self.own[mask] = pid
        self.isflat[mask] = False
        if rim is not None and self.cfg["rim"]:
            self.rims.append((pid, rim))
        return pid

    def flatpaint(self, mask, rgb):
        self.isflat[mask] = True
        self.flat[mask] = rgb
        self.rid[mask] = -1
        self.own[mask] = -3

    def shade(self, mask, dx, dy, bl, strength):
        """mask の影を、いままでに描いたものへ落とす (mask 自身は除く)"""
        sh = blur(shift(mask.astype(float), dx, dy), bl) * strength
        sh[mask] = 0
        sh[self.rid < 0] = 0
        self.val -= sh

    # 仕上げ
    def render(self, bg):
        L = self.cfg["levels"]
        v = self.val.copy()
        if self.cfg["dither"]:
            v += np.tile(BAYER, (N // 4, N // 4)) * self.cfg["dither"] / L * 1.6
        if self.cfg.get("hatch"):
            # 影の段には斜線、全体にざらつき
            rng = np.random.default_rng(7)
            low = v < 0.5
            line = (((self.X + self.Y) % 4) < 1)
            v = np.where(low & line, v - 0.2, v)
            v = v + (rng.random((N, N)) - 0.5) * 0.16
        idx = np.clip((v * L).astype(int), 0, L - 1)
        out = bg.copy()
        for pid, mat in enumerate(self.names):
            m = (self.rid == pid)
            if not m.any():
                continue
            ramp = self.ramps[mat]
            out[m] = ramp[idx[m]]
        out[self.isflat] = self.flat[self.isflat]
        for pid, col in self.rims:
            m = (self.own == pid) & (self.rid == pid)
            edge = m & ~shift(m.astype(float), -2, 0).astype(bool) & ~shift(m.astype(float), -1, 1).astype(bool)
            edge &= (idx >= 0)
            # 右の縁に細く逆光 (少し内側まで)
            e2 = m & ~np.roll(m, -3, axis=1)
            sel = e2 & (idx >= 2)
            out[sel] = (out[sel] * 0.35 + np.array(col) * 0.65).astype(int)
        return out


# ---- 背景 --------------------------------------------------------------------------------------------------
def background(style, npc):
    c = BG[npc]
    yy, xx = np.mgrid[0:N, 0:N].astype(float)
    if style == "a":
        t = yy / (N - 1)
        base = np.array(c["top"])[None, None, :] * (1 - t[..., None]) + np.array(c["bot"])[None, None, :] * t[..., None]
        d = np.sqrt((xx - 64) ** 2 + ((yy - 52) * 1.1) ** 2)
        glow = np.clip(1 - d / 62, 0, 1) ** 2
        img = base + (np.array(c["glow"]) - base) * glow[..., None] * 0.42
        # 8 段に落としてディザ
        steps = 9
        lum = (img.sum(axis=2) / 3)
        lo, hi = lum.min(), lum.max()
        q = (lum - lo) / (hi - lo) * (steps - 1)
        q = np.floor(q + np.tile(BAYER, (N // 4, N // 4)) + 0.5).clip(0, steps - 1) / (steps - 1)
        col_lo = (img.reshape(-1, 3)[lum.reshape(-1).argmin()]).astype(float)
        col_hi = (img.reshape(-1, 3)[lum.reshape(-1).argmax()]).astype(float)
        # 暈の色を高い側に、暗い側は上の色へ
        hi_col = np.array(c["bot"]) * 0.55 + np.array(c["glow"]) * 0.45
        return np.round(np.array(c["top"])[None, None, :] * (1 - q[..., None]) + hi_col[None, None, :] * q[..., None]).astype(int)
    if style == "b":
        ang = np.arctan2(yy - 56, xx - 64)
        rays = (np.floor((ang + np.pi) / (2 * np.pi) * 16) % 2).astype(bool)
        img = np.where(rays[..., None], np.array(c["b2"]), np.array(c["b"]))
        # 周りを暗く (太い 3 段目)
        d = np.sqrt((xx - 64) ** 2 + (yy - 56) ** 2)
        dark = (np.array(c["b"]) * 0.55).astype(int)
        img = np.where((d > 74)[..., None], dark, img)
        # 半円のドット
        dots = ((xx % 6 == 0) & (yy % 6 == 0) & (d > 40) & (d < 74))
        img = np.where(dots[..., None], np.array(c["b2"]) , img)
        return img.astype(int)
    # c: 紙と木目と大きな円
    rng = np.random.default_rng(11)
    paper = np.array([208, 192, 152])
    grain = np.sin(yy * 0.9 + np.sin(xx * 0.11) * 3.0) * 0.5 + rng.random((N, N)) * 0.5
    img = np.where((grain > 0.62)[..., None], paper * 0.93, paper[None, None, :] + 0 * grain[..., None])
    d = np.sqrt((xx - 64) ** 2 + (yy - 52) ** 2)
    disc = np.array(c["c"]) * 0.45 + paper * 0.55
    img = np.where((d < 50)[..., None], np.where((grain > 0.55)[..., None], disc * 0.9, disc), img)
    return np.round(img).astype(int)


# ---- 顔のパーツ --------------------------------------------------------------------------------------------
def eye(a, x, y, expr, kind, skin, side):
    """kind: glow (語り部の青白い目) / dark (黒目)。side=-1 左, +1 右 (画面上)"""
    ink = a.ink
    if kind == "glow":
        a.paint(ell(x, y, 5.2, 3.6), "inner", 1.2, v=0.3)
        a.paint(ell(x, y, 4.4, 3.0), "glow", 1.5, v=0.55)
        a.flatpaint(ell(x + 0.4 * side * -1, y, 1.8, 1.7), (250, 254, 255))
        a.paint(ell(x, y + 1.8, 3.2, 1.0), "glow", 1, v=0.9)
    else:
        iris = {"whale_chaser": (44, 78, 92), "artisan": (96, 62, 36)}.get(a.npc, (60, 50, 40))
        a.paint(ell(x, y, 5.4, 3.6), "sclera", 1.5, v=0.5)
        ir = ell(x + 0.3 * -side, y + 0.2, 2.7, 3.0) & ell(x, y, 5.4, 3.6)
        a.flatpaint(ir, iris)
        a.flatpaint(ell(x + 0.3 * -side, y + 0.2, 1.4, 1.5), (14, 12, 18))
        a.flatpaint(ell(x - 1.0, y - 1.0, 0.9, 0.9), (255, 255, 255))
    # まぶた
    if expr == "smile":
        a.paint(ell(x, y + 3.6, 6.4, 2.6), skin, 1.5, v=0.52)
        lid = bez((x - 5.8, y + 1.0), (x, y - 5.0), (x + 5.8, y + 1.0), 10)
        a.paint(stroke(bez((x - 5.4, y + 2.6), (x, y - 0.4), (x + 5.4, y + 2.6), 10), 0.9), "line", 0.5, v=0.2)
    elif expr == "grave":
        a.paint(ell(x, y - 3.4, 6.6, 3.0), skin, 1.5, v=0.42)
        a.paint(stroke([(x - 6, y - 0.4 - side * 0.0), (x, y - 0.9), (x + 6, y - 0.1)], 1.1), "line", 0.5, v=0.12)
    else:
        a.paint(stroke(bez((x - 6, y), (x, y - 5.6), (x + 6, y), 10), 1.1), "line", 0.5, v=0.15)
    return


def mouth(a, expr, cx, cy, w=7.5):
    lipc = "lip"
    if expr == "smile":
        a.paint(stroke(bez((cx - w, cy - 2.2), (cx, cy + 4.6), (cx + w, cy - 2.2), 14), 1.9), lipc, 0.6, v=0.22)
        a.paint(stroke([(cx - w - 1.2, cy - 3.6), (cx - w - 0.3, cy - 2.2)], 1.0), "line", 0.5, v=0.2)
        a.paint(stroke([(cx + w + 1.2, cy - 3.6), (cx + w + 0.3, cy - 2.2)], 1.0), "line", 0.5, v=0.2)
        a.paint(ell(cx, cy + 0.7, w * 0.55, 0.9), "sclera", 0.6, v=0.6)
    elif expr == "grave":
        a.paint(stroke(bez((cx - w, cy + 2.2), (cx, cy - 2.0), (cx + w, cy + 2.2), 14), 1.8), lipc, 0.6, v=0.18)
        a.paint(ell(cx, cy + 3.4, 3.4, 1.2), "skin_old" if a.npc == "storyteller" else ("skin_tan" if a.npc == "whale_chaser" else "skin_brown"), 1, v=0.3)
    else:
        a.paint(stroke(bez((cx - w + 1, cy + 0.2), (cx, cy + 1.2), (cx + w - 1, cy + 0.2), 14), 1.7), lipc, 0.6, v=0.22)
        a.paint(ell(cx, cy + 3.0, 3.6, 1.1), lipc, 0.8, v=0.55)


def brows(a, expr, cy, color, xin=3.5, xout=16.0, w0=3.0, w1=1.6, cx=64):
    for side in (-1, 1):
        if expr == "neutral":
            pts = bez((cx + side * xin, cy + 0.4), (cx + side * (xin + xout) / 2, cy - 2.4), (cx + side * xout, cy + 0.2), 10)
        elif expr == "smile":
            pts = bez((cx + side * xin, cy - 0.8), (cx + side * (xin + xout) / 2, cy - 4.2), (cx + side * xout, cy - 0.4), 10)
        else:
            pts = bez((cx + side * xin, cy - 2.4), (cx + side * (xin + xout) / 2, cy - 0.6), (cx + side * xout, cy + 2.6), 10)
        a.paint(taper(pts, w0, w1), color, 0.8, ol=0)


def nose(a, cx, cy, skin, wide=4.0, h=5.0):
    a.paint(ell(cx + 0.6, cy, wide, h), skin, 2.0, v=None, soft=1.3)
    a.paint(ell(cx - 1.5, cy + h - 0.8, 1.1, 0.8), "line", 0.5, v=0.1)
    a.paint(ell(cx + 2.8, cy + h - 0.8, 1.1, 0.8), "line", 0.5, v=0.1)


def head_mask(cx, cy, rx, ry, jaw=0.8):
    return ell(cx, cy - 3, rx, ry) | ell(cx, cy + 8, rx * jaw, ry * 0.78)


def knit(x, y):
    return (((x // 3 + y // 3) % 2) * 0.16 - 0.08)


def cloth(x, y):
    return ((((x * 7 + y * 13) % 11) - 5) * 0.012)


def stubble_fn(x0, x1, y0, y1, amount=0.14):
    def fn(X, Y):
        h = ((X * 73856093 + Y * 19349663).astype(np.int64) % 100) / 100.0
        zone = (X > x0) & (X < x1) & (Y > y0) & (Y < y1)
        return np.where(zone & (h < 0.42), -amount, 0.0)
    return fn


# ---- 人 ----------------------------------------------------------------------------------------------------
def storyteller(a, expr):
    # 肩の外套
    a.paint(poly([(2, 130), (8, 108), (28, 94), (52, 88), (76, 88), (100, 94), (120, 108), (126, 130)]), "cloak", 9, ol=2, rim=(160, 214, 250), tex=cloth)
    # 頭巾 (とがった先)
    hood = ell(64, 62, 38, 42) | poly([(32, 52), (96, 52), (82, 16), (71, 2), (60, 7), (49, 28)])
    a.paint(hood, "hood", 7, ol=2, rim=(170, 222, 255), tex=cloth)
    a.paint(ell(64, 64, 27, 33), "inner", 4)
    # 銀の髪の房 (顔の後ろ、縁の前へ垂れる)
    a.paint(taper([(43, 50), (39, 70), (37, 88), (38, 101)], 4.6, 2.0), "silver", 1.4)
    a.paint(taper([(85, 50), (89, 72), (90, 90), (89, 99)], 4.4, 1.8), "silver", 1.4)
    # 首と顔
    a.paint(rrect(54, 80, 74, 104, 4), "skin_old", 3)
    face = head_mask(64, 66, 21.5, 27, 0.82)
    a.shade(face, 0, 5, 4, 0.55)
    a.paint(face, "skin_old", 6, ol=0, rim=(190, 226, 250))
    # 頭巾の縁 (顔の上へかぶさる)
    ring = ell(64, 62, 31, 37) & ~ell(64, 64, 26.5, 32.5)
    ring &= ~(poly([(0, 96), (128, 96), (128, 128), (0, 128)]))
    a.paint(ring, "trim", 3, ol=2, rim=(220, 244, 255), tex=cloth)
    a.shade(ring, 0, 3, 4, 0.5)
    a.paint(taper([(46, 60), (44, 74), (44, 86)], 2.2, 0.9), "silver", 1.0)
    # 肩掛けと留め具
    a.paint(ell(64, 104, 36, 14) & poly([(0, 94), (128, 94), (128, 128), (0, 128)]), "shawl", 6, ol=2, tex=knit)
    a.shade(face, 0, 5, 4, 0.0)
    a.paint(ell(64, 108, 4.5, 4.5), "gold", 2, ol=1)
    # 顔
    skin = "skin_old"
    for side, x in ((-1, 54), (1, 74)):
        eye(a, x, 66, expr, "glow", skin, side)
    # しわ
    for side in (-1, 1):
        a.paint(stroke(bez((64 + side * 10, 74), (64 + side * 12.5, 80), (64 + side * 9, 86), 8), 1.0), "line", 0.5, v=0.4)
        a.paint(stroke([(64 + side * 16.5, 67.5), (64 + side * 20, 68.5)], 0.8), "line", 0.5, v=0.4)
    a.paint(stroke(bez((58, 50), (64, 52.5), (70, 50), 8), 0.9), "line", 0.5, v=0.42)
    nose(a, 64, 74, skin, 3.8, 4.6)
    mouth(a, expr, 64, 85, 6.5)
    brows(a, expr, 58.5, "silver", xin=4, xout=17, w0=3.4, w1=1.4)
    # 眉の房 (外側へはねる)
    for side in (-1, 1):
        o = 17 if expr != "grave" else 17
        a.paint(taper([(64 + side * (o - 1), 58.8 + (2.4 if expr == "grave" else 0)), (64 + side * (o + 4), 56.5 + (3.4 if expr == "grave" else 0))], 1.4, 0.7), "silver", 0.5)


def whale_chaser(a, expr):
    skin = "skin_tan"
    # 外套と背負いの紐
    a.paint(poly([(4, 130), (8, 110), (28, 96), (52, 90), (76, 90), (100, 96), (120, 110), (124, 130)]), "coat", 9, ol=2, rim=(150, 220, 200), tex=cloth)
    a.paint(poly([(26, 92), (38, 90), (104, 130), (88, 130)]), "leather", 2, ol=1)
    a.paint(rrect(54, 106, 62, 114, 1.5), "brass", 1.5, ol=1)
    # 髪の後ろ
    a.paint(ell(64, 62, 26, 30) | poly([(40, 60), (46, 62), (46, 90), (38, 88)]) | poly([(88, 60), (82, 62), (83, 92), (90, 88)]), "hair_dk", 5, ol=2, tex=cloth)
    a.paint(rrect(55, 80, 73, 100, 4), skin, 3)
    # 襟巻 (顔の下に)
    a.paint(ell(64, 99, 31, 13) & poly([(0, 88), (128, 88), (128, 128), (0, 128)]), "scarf", 6, ol=2, tex=knit)
    a.paint(poly([(70, 102), (92, 108), (96, 128), (80, 128), (72, 114)]), "scarf", 4, ol=2, tex=knit)
    face = head_mask(64, 66, 20.5, 26, 0.78)
    a.shade(face, 0, 5, 4, 0.5)
    a.paint(face, skin, 6, rim=(255, 224, 170), tex=stubble_fn(48, 80, 78, 96, 0.13))
    # 前髪
    a.paint(poly([(44, 44), (46, 56), (52, 50), (58, 60), (64, 51), (71, 60), (76, 50), (83, 57), (84, 44)]), "hair_dk", 2, tex=cloth)
    a.paint(taper([(46, 54), (44, 66), (45, 76)], 3.0, 1.2), "hair_dk", 1)
    a.paint(taper([(82, 54), (84, 66), (83, 76)], 3.0, 1.2), "hair_dk", 1)
    for side, x in ((-1, 54), (1, 74)):
        eye(a, x, 65, expr, "dark", skin, side)
    brows(a, expr, 58.5, "hair_dk", xin=4, xout=15.5, w0=3.0, w1=1.8)
    nose(a, 64, 73.5, skin, 3.4, 4.4)
    mouth(a, expr, 64, 84, 6.0)
    if expr == "grave":
        a.paint(stroke([(60, 55), (60.4, 58.5)], 1.0), "line", 0.5, v=0.25)
        a.paint(stroke([(68, 55), (67.6, 58.5)], 1.0), "line", 0.5, v=0.25)
    # つば広帽
    brim = ell(64, 41, 54, 9.5)
    crown = ell(64, 26, 26, 19) | rrect(38, 20, 90, 41, 10)
    a.shade(brim, 0, 5, 4, 0.7)
    a.paint(brim, "hat", 5, ol=2, rim=(255, 214, 150), tex=cloth)
    a.paint(crown, "hat", 6, ol=2, rim=(255, 214, 150), tex=cloth)
    a.paint(crown & poly([(0, 34), (128, 34), (128, 41), (0, 41)]), "strap", 2.0, ol=0)
    a.paint(taper([(39, 36), (31, 24), (28, 10)], 4.2, 1.0), "feather", 1.5, ol=1)


def artisan(a, expr):
    skin = "skin_brown"
    a.paint(poly([(0, 130), (4, 108), (26, 95), (52, 90), (76, 90), (102, 95), (124, 108), (128, 130)]), "shirt", 9, ol=2, rim=(255, 190, 120), tex=cloth)
    a.paint(poly([(34, 130), (38, 102), (90, 102), (94, 130)]), "apron", 4, ol=2, tex=cloth)
    a.paint(poly([(38, 94), (48, 94), (52, 130), (42, 130)]), "strap", 2, ol=1)
    a.paint(poly([(80, 94), (90, 94), (86, 130), (76, 130)]), "strap", 2, ol=1)
    a.paint(rrect(40, 108, 50, 114, 1.5), "brass", 1.5, ol=1)
    a.paint(rrect(78, 108, 88, 114, 1.5), "brass", 1.5, ol=1)
    # 石の縁の襟 (一章の装い)
    a.paint(ell(64, 98, 27, 9) & poly([(0, 92), (128, 92), (128, 128), (0, 128)]), "stone", 3, ol=2, tex=cloth)
    a.paint(rrect(53, 80, 75, 100, 5), skin, 3)
    # 耳と髪
    a.paint(ell(42.5, 68, 5, 7), skin, 2, ol=1)
    a.paint(ell(85.5, 68, 5, 7), skin, 2, ol=1)
    a.paint(ell(64, 47, 25, 20), "hair_gr", 5, ol=2, tex=cloth)
    face = ell(64, 66, 21, 24) | rrect(45, 70, 83, 91, 11)
    a.shade(face, 0, 5, 4, 0.5)
    a.paint(face, skin, 6, rim=(255, 196, 140), tex=stubble_fn(46, 82, 78, 96, 0.14))
    # 額の眼鏡
    a.paint(rrect(40, 40, 88, 49, 3), "strap", 2, ol=2)
    a.shade(rrect(40, 40, 88, 49, 3), 0, 3, 3, 0.4)
    for cx in (52, 76):
        a.paint(ell(cx, 41, 11, 9), "brass", 2.5, ol=2)
        a.paint(ell(cx, 41, 8, 6.2), "lens", 2.5, v=0.6)
        a.flatpaint(poly([(cx - 5, 38), (cx - 2, 36.6), (cx - 3.4, 39.4)]), (240, 252, 255))
    # 髪 (眼鏡の上)
    a.paint(taper([(44, 56), (42, 62)], 3.4, 1.4), "hair_gr", 1)
    a.paint(taper([(84, 56), (86, 62)], 3.4, 1.4), "hair_gr", 1)
    for side, x in ((-1, 53), (1, 75)):
        eye(a, x, 67, expr, "dark", skin, side)
    brows(a, expr, 60, "hair_gr", xin=3.6, xout=16, w0=3.6, w1=2.2)
    nose(a, 64, 75, skin, 4.4, 5.2)
    mouth(a, expr, 64, 85.5, 7)
    if expr == "grave":
        a.paint(stroke([(61, 56.5), (61.4, 60)], 1.0), "line", 0.5, v=0.2)
        a.paint(stroke([(67, 56.5), (66.6, 60)], 1.0), "line", 0.5, v=0.2)


DRAW = {"storyteller": storyteller, "whale_chaser": whale_chaser, "artisan": artisan}


def portrait(style, npc, expr):
    a = Art(style, npc)
    DRAW[npc](a, expr)
    img = a.render(background(style, npc))
    return Image.fromarray(img.astype(np.uint8), "RGB")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--install", choices=list(STYLES))
    args = ap.parse_args()
    os.makedirs(OUT, exist_ok=True)
    for st in STYLES:
        for npc in NPCS:
            for f in FACES:
                portrait(st, npc, f).save(os.path.join(OUT, f"{st}_{npc}_{f}.png"))
    print("wrote", len(STYLES) * len(NPCS) * len(FACES), "portraits ->", OUT)
    if args.install:
        os.makedirs(FACE_DIR, exist_ok=True)
        for npc in NPCS:
            for f in FACES:
                shutil.copyfile(os.path.join(OUT, f"{args.install}_{npc}_{f}.png"), os.path.join(FACE_DIR, f"{npc}_{f}.png"))
        print("installed style", args.install, "->", FACE_DIR)


if __name__ == "__main__":
    main()
