"""Tiny pixel-art helpers for the README art (Pillow + numpy, no anti-aliasing)."""
import os
import numpy as np
from PIL import Image, ImageDraw, ImageFont

OUT = os.path.join(os.path.dirname(__file__), "..", "..", "docs", "readme")
OUT = os.path.abspath(OUT)

# ---- palette (mod colours) -------------------------------------------------
def H(s):
    s = s.lstrip("#")
    return (int(s[0:2], 16), int(s[2:4], 16), int(s[4:6], 16), 255)

INK = H("14101e")        # outline
SKY1 = H("3d7fd0"); SKY2 = H("5aa9e6"); SKY3 = H("8cc8f0"); SKY4 = H("bfe3f5")
CLOUD = H("f4f8fb"); CLOUD_S = H("c4d6e6"); CLOUD_D = H("9fb6cf")
ABYSS0 = H("070b18"); ABYSS1 = H("0e1730"); ABYSS2 = H("17264a"); ABYSS3 = H("233a68")
TEAL = H("3aa6a0"); TEAL_D = H("246f72"); TEAL_L = H("7fd9cf")
ROCK = H("6b7078"); ROCK_L = H("8d939c"); ROCK_D = H("484d57"); BONE = H("dcd2b6"); BONE_D = H("a99f84")
CORAL = H("e0765f"); CORAL_L = H("f2a88c"); CORAL_D = H("a9473f"); MOSS = H("3f9a6a"); MOSS_D = H("2a6b4a")
STORM = H("2d4a9a"); STORM_L = H("4f78d6"); STORM_D = H("1b2d62"); GOLD = H("e8b83a"); GOLD_L = H("fbe27a"); GOLD_D = H("a8791e")
COAL = H("1c1819"); COAL_L = H("342c2e"); EMBER = H("e8742a"); EMBER_L = H("ffb04a"); EMBER_D = H("a8401a")
STONE = H("7d7d82"); STONE_L = H("9b9ba1"); STONE_D = H("57575c"); STONE_DD = H("3b3b40")
WHITE = H("f4f4f4"); TRANSP = (0, 0, 0, 0)

FONT_PATHS = {
    "gothic": "C:/Windows/Fonts/msgothic.ttc",
    "yugo": "C:/Windows/Fonts/YuGothB.ttc",
    "biz": "C:/Windows/Fonts/BIZ-UDGothicB.ttc",
}


def font(size, face="gothic"):
    return ImageFont.truetype(FONT_PATHS[face], size)


# ---- canvas ------------------------------------------------------------------
def canvas(w, h, fill=TRANSP):
    a = np.zeros((h, w, 4), np.uint8)
    a[:, :] = fill
    return a


def to_img(a):
    return Image.fromarray(a, "RGBA")


def save(a, name, scale, maxkb=300):
    img = to_img(a).resize((a.shape[1] * scale, a.shape[0] * scale), Image.NEAREST)
    path = os.path.join(OUT, name)
    img.save(path, optimize=True)
    kb = os.path.getsize(path) / 1024
    if kb > maxkb:
        q = img.convert("RGB").quantize(64, method=Image.MEDIANCUT, dither=Image.NONE)
        q.save(path, optimize=True)
        kb = os.path.getsize(path) / 1024
    return path, kb


def mask(w, h, fn):
    m = Image.new("L", (w, h), 0)
    fn(ImageDraw.Draw(m))
    return np.array(m) > 0


def fill(a, m, color):
    a[m] = color


def shift(m, dx, dy):
    out = np.zeros_like(m)
    h, w = m.shape
    ys, xs = slice(max(dy, 0), h + min(dy, 0)), slice(max(dx, 0), w + min(dx, 0))
    yd, xd = slice(max(-dy, 0), h + min(-dy, 0)), slice(max(-dx, 0), w + min(-dx, 0))
    out[ys, xs] = m[yd, xd]
    return out


def dilate(m, n=1, diag=False):
    r = m.copy()
    for _ in range(n):
        s = r.copy()
        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            s |= shift(r, dx, dy)
        if diag:
            for dx, dy in ((1, 1), (-1, 1), (1, -1), (-1, -1)):
                s |= shift(r, dx, dy)
        r = s
    return r


def outline(a, m, color=INK, diag=False):
    ring = dilate(m, 1, diag) & ~m
    a[ring] = color


def outline_alpha(a, color=INK, diag=False):
    """Outline every opaque pixel region in `a` onto transparent neighbours."""
    m = a[:, :, 3] > 0
    ring = dilate(m, 1, diag) & ~m
    a[ring] = color


def bevel(a, m, hi, lo):
    """Top-left lit edge, bottom-right shaded edge, inside the mask."""
    top = m & ~shift(m, 1, 1)
    bot = m & ~shift(m, -1, -1)
    a[bot] = lo
    a[top] = hi


def band(a, m, color, y0, dither=True):
    """Paint `color` over mask pixels at rows >= y0, with a checker transition row."""
    h, w = m.shape
    yy, xx = np.mgrid[0:h, 0:w]
    sel = m & (yy >= y0)
    a[sel] = color
    if dither:
        t = m & (yy == y0 - 1) & ((xx % 2) == 0)
        a[t] = color


def vgrad(a, stops, x0=0, x1=None, y0=0, y1=None):
    """Banded vertical gradient with dithered seams. stops = [(y_end, colour)...]."""
    h, w = a.shape[:2]
    x1 = w if x1 is None else x1
    y1 = h if y1 is None else y1
    y = y0
    for i, (ye, col) in enumerate(stops):
        ye = min(ye, y1)
        a[y:ye, x0:x1] = col
        if i + 1 < len(stops):
            nxt = stops[i + 1][1]
            for xx in range(x0, x1):
                if (xx % 2) == 0 and ye < y1:
                    a[ye, xx] = nxt
                if (xx % 2) == 1 and ye - 1 >= y0:
                    pass
        y = ye


def cloud(a, cx, cy, w, h=5, light=CLOUD, shade=CLOUD_S, dark=None):
    """Flat-bottomed puffy pixel cloud centred at (cx, cy)."""
    H_, W_ = a.shape[:2]

    def f(d):
        n = max(3, w // 6)
        for i in range(n):
            t = i / (n - 1) if n > 1 else 0.5
            rx = int(w * (0.14 + 0.1 * np.sin(t * np.pi)))
            ry = int(h * (0.45 + 0.55 * np.sin(t * np.pi)))
            x = int(cx - w / 2 + t * w)
            d.ellipse((x - rx, cy - ry, x + rx, cy + 1), fill=255)
        d.rectangle((cx - w // 2 + 2, cy - 1, cx + w // 2 - 2, cy + 1), fill=255)

    m = mask(W_, H_, f)
    m &= ~(np.mgrid[0:H_, 0:W_][0] > cy + 1)
    a[m] = light
    band(a, m, shade, cy - 1, dither=False)
    if dark is not None:
        band(a, m, dark, cy + 1, dither=False)
    return m


def tube(w, h, path, radii):
    pts = list(zip(path, radii))

    def f(d):
        for (x, y), r in pts:
            d.ellipse((x - r, y - r, x + r, y + r), fill=255)

    return mask(w, h, f)


def bez(p0, p1, p2, n=60):
    out = []
    for i in range(n + 1):
        t = i / n
        x = (1 - t) ** 2 * p0[0] + 2 * (1 - t) * t * p1[0] + t * t * p2[0]
        y = (1 - t) ** 2 * p0[1] + 2 * (1 - t) * t * p1[1] + t * t * p2[1]
        out.append((x, y))
    return out


def bez3(p0, p1, p2, p3, n=80):
    out = []
    for i in range(n + 1):
        t = i / n
        u = 1 - t
        x = u**3 * p0[0] + 3 * u * u * t * p1[0] + 3 * u * t * t * p2[0] + t**3 * p3[0]
        y = u**3 * p0[1] + 3 * u * u * t * p1[1] + 3 * u * t * t * p2[1] + t**3 * p3[1]
        out.append((x, y))
    return out


# ---- text --------------------------------------------------------------------
def text_mask(s, size, face="gothic", pad=2):
    f = font(size, face)
    l, t, r, b = ImageDraw.Draw(Image.new("L", (1, 1))).textbbox((0, 0), s, font=f)
    w, h = r - l + pad * 2, b - t + pad * 2
    img = Image.new("L", (w, h), 0)
    d = ImageDraw.Draw(img)
    d.fontmode = "1"
    d.text((pad - l, pad - t), s, font=f, fill=255)
    m = np.array(img) > 127
    ys, xs = np.where(m)
    if len(xs) == 0:
        return m
    return m[ys.min():ys.max() + 1, xs.min():xs.max() + 1]


def put_mask(a, m, x, y, color):
    h, w = m.shape
    H_, W_ = a.shape[:2]
    y0, x0 = max(y, 0), max(x, 0)
    y1, x1 = min(y + h, H_), min(x + w, W_)
    if y1 <= y0 or x1 <= x0:
        return
    sub = m[y0 - y:y1 - y, x0 - x:x1 - x]
    a[y0:y1, x0:x1][sub] = color


def text(a, x, y, s, color=WHITE, size=12, shadow=None, face="gothic"):
    """Un-antialiased text. (x, y) is the top-left of the ink box."""
    m = text_mask(s, size, face)
    if shadow is not None:
        put_mask(a, m, x + 1, y + 1, shadow)
    put_mask(a, m, x, y, color)
    return m.shape[1]


def text_width(s, size, face="gothic"):
    return text_mask(s, size, face).shape[1]


def fancy_title(a, cx, y, s, size=16, k=2, face="gothic", light=STONE_L, mid=STONE, dark=STONE_D, side=STONE_DD, depth=3):
    """Minecraft-logo style: scaled solid face, extruded side, dark outline."""
    m = text_mask(s, size, face, pad=0)
    m = np.kron(m, np.ones((k, k), bool))
    h, w = m.shape
    x = cx - w // 2
    H_, W_ = a.shape[:2]
    full = np.zeros((H_, W_), bool)
    face_m = np.zeros((H_, W_), bool)
    face_m[y:y + h, x:x + w] = m
    ext = face_m.copy()
    for d in range(1, depth + 1):
        ext |= shift(face_m, 0, d)
    full |= ext
    ol = dilate(full, 1, diag=True) & ~full
    a[ol] = INK
    a[ext & ~face_m] = side
    a[face_m] = mid
    # lit top rows / shaded bottom rows of the face
    top = face_m & ~shift(face_m, 0, 1)
    top2 = face_m & ~shift(face_m, 0, 2)
    a[top2 & face_m] = light
    bot = face_m & ~shift(face_m, 0, -1)
    a[bot] = dark
    return x, y, w, h


# ---- inventory-style frames --------------------------------------------------
def frame(a, x0, y0, x1, y1, face=STONE, hi=STONE_L, lo=STONE_D, edge=INK):
    """Inclusive rectangle with a beveled stone border."""
    a[y0:y1 + 1, x0:x1 + 1] = edge
    a[y0 + 1:y1, x0 + 1:x1] = face
    a[y0 + 1, x0 + 1:x1] = hi
    a[y0 + 1:y1, x0 + 1] = hi
    a[y1 - 1, x0 + 1:x1] = lo
    a[y0 + 1:y1, x1 - 1] = lo


def slot(a, x0, y0, size=18):
    """Inventory slot: dark sunken inside, light bottom-right rim."""
    x1, y1 = x0 + size - 1, y0 + size - 1
    a[y0:y1 + 1, x0:x1 + 1] = H("373737")
    a[y0:y1 + 1, x0] = H("373737")
    a[y0, x0:x1 + 1] = H("373737")
    a[y1, x0:x1 + 1] = H("ffffff")
    a[y0:y1 + 1, x1] = H("ffffff")
    a[y0 + 1:y1, x0 + 1:x1] = H("8b8b8b")
    a[y0, x1] = H("c6c6c6"); a[y1, x0] = H("c6c6c6")


def tooltip(a, x0, y0, lines, pad=3):
    """Minecraft tooltip. lines = [(string, colour)]. Returns (w, h)."""
    ms = [text_mask(s, 12) for s, _ in lines]
    w = max(m.shape[1] for m in ms) + pad * 2 + 2
    h = sum(14 for _ in lines) - 2 + pad * 2 + 2
    bg = H("100010"); pur = H("3a0c78"); pur2 = H("28074f")
    a[y0:y0 + h, x0:x0 + w] = bg
    a[y0 + 1:y0 + h - 1, x0 + 1:x0 + w - 1] = pur
    a[y0 + 2:y0 + h - 2, x0 + 2:x0 + w - 2] = pur2
    a[y0 + 2:y0 + h - 2, x0 + 2:x0 + w - 2] = bg
    # notch the corners
    yy = y0 + pad + 1
    for (s, c), m in zip(lines, ms):
        put_mask(a, m, x0 + pad + 1 + 1, yy + 1, tuple(int(v * 0.25) for v in c[:3]) + (255,))
        put_mask(a, m, x0 + pad + 1, yy, c)
        yy += 14
    return w, h


def paste(a, b, x, y):
    """Alpha-over paste of RGBA array b onto a (binary alpha)."""
    h, w = b.shape[:2]
    H_, W_ = a.shape[:2]
    y0, x0 = max(y, 0), max(x, 0)
    y1, x1 = min(y + h, H_), min(x + w, W_)
    if y1 <= y0 or x1 <= x0:
        return
    sub = b[y0 - y:y1 - y, x0 - x:x1 - x]
    sel = sub[:, :, 3] > 0
    a[y0:y1, x0:x1][sel] = sub[sel]


def upscale(b, k):
    return np.kron(b, np.ones((k, k, 1), np.uint8))
