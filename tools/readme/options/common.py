"""Shared helpers for the three README concept options (A / B / C). Reuses ../pix.py, ../scenes.py, ../icons*.py."""
import os, sys
HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.dirname(HERE))
import numpy as np
import pix
from pix import *
import icons, icons2  # noqa: F401
import scenes

ROOT = os.path.abspath(os.path.join(HERE, "..", "..", "..", "docs", "readme", "options"))
VERSION = "v0.2.2"


def outdir(name):
    d = os.path.join(ROOT, name)
    os.makedirs(d, exist_ok=True)
    pix.OUT = d
    return d


def noise_fill(a, x0, y0, x1, y1, tones, weights=None, seed=1):
    rs = np.random.RandomState(seed)
    p = None if weights is None else np.array(weights, float) / sum(weights)
    idx = rs.choice(len(tones), size=(y1 - y0 + 1, x1 - x0 + 1), p=p)
    for i, t in enumerate(tones):
        a[y0:y1 + 1, x0:x1 + 1][idx == i] = t


def clip_corners(a, n=2):
    """Notch the four corners transparent (pixel-rounded)."""
    h, w = a.shape[:2]
    for i in range(n):
        for j in range(n - i):
            a[i, j] = a[i, w - 1 - j] = a[h - 1 - i, j] = a[h - 1 - i, w - 1 - j] = TRANSP


def star_field(a, y0, y1, n, seed, cols=(SKY4, SKY3, TEAL_L)):
    rs = np.random.RandomState(seed)
    w = a.shape[1]
    for _ in range(n):
        a[rs.randint(y0, y1), rs.randint(0, w)] = cols[rs.randint(len(cols))]


def twinkle(a, x, y, col=WHITE, dim=SKY3):
    a[y, x] = col
    for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        if 0 <= y + dy < a.shape[0] and 0 <= x + dx < a.shape[1]:
            a[y + dy, x + dx] = dim


def lines(a, x, y, ls, color, size=12, step=14, shadow=None, center=None):
    """Draw text lines. center=cx centres each line on cx instead of using x."""
    for s in ls:
        if s:
            w = text_width(s, size)
            text(a, (center - w // 2) if center is not None else x, y, s, color, size, shadow=shadow)
        y += step
    return y


def scene_img(name):
    return {"whale": scenes.scene_whale, "tide": scenes.scene_tide, "storm": scenes.scene_storm, "ember": scenes.scene_ember}[name]()


def keycap_arr(ch, face=H("d4d4da"), hi=H("f4f4f8"), lo=H("a0a0aa"), ink=H("22222a")):
    a = canvas(16, 17)
    a[0:15, 0:16] = INK
    a[1:14, 1:15] = face
    a[1, 1:15] = hi; a[1:14, 1] = hi
    a[13, 1:15] = lo; a[1:14, 14] = lo
    a[15:16, 1:15] = H("52525c")
    a[16, 1:15] = INK
    w = text_width(ch, 12)
    text(a, 8 - w // 2, 2, ch, ink, 12)
    return a


def framed(arr, border=INK, w=1):
    h, wd = arr.shape[:2]
    out = canvas(wd + 2 * w, h + 2 * w, border)
    out[w:w + h, w:w + wd] = arr
    return out


def silhouette_whale(a, x0, y0, L, col, edge):
    """Flat single-colour stone whale (for the minimal concept)."""
    pal = dict(body=col, lite=col, dark=col, side=col, plate=col, plate_l=col, plate_d=col, moss=col, spike=col,
               ol=edge, tooth=col, eye=col, detail=False)
    scenes.whale(a, x0, y0, L, pal, chain=False)
