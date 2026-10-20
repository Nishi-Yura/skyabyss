"""武器の組み立て・描き・回転の並び。"""
from __future__ import annotations

import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE)); sys.path.insert(0, str(HERE.parent / "weapons"))
from scene import *  # noqa: F401,F403,E402
import spaint  # noqa: E402
from PIL import ImageDraw, ImageFont  # noqa: E402

PLAT = (30, 34, 42)
FONTS = ["C:/Windows/Fonts/YuGothB.ttc", "C:/Windows/Fonts/meiryob.ttc", "C:/Windows/Fonts/meiryo.ttc"]


def font(sz):
    for p in FONTS:
        try:
            return ImageFont.truetype(p, sz)
        except Exception:
            pass
    return ImageFont.load_default()


def make(faces, style, rarity="rare"):
    faces, size, P, N, M, L = build([f for f in faces if f is not None])
    atlas, glow = spaint.paint(faces, size, P, N, M, L, style, rarity)
    return dict(faces=faces, size=size, atlas=atlas, glow=glow, tris=tri_count(faces))


def tile(r, yaw, pitch, size, cen, scale, bg=PLAT):
    im = view(r["faces"], r["atlas"], r["glow"], "whale", yaw, pitch, size, cen, scale)
    b = Image.new("RGBA", size, bg + (255,))
    b.alpha_composite(im)
    return b.convert("RGB")


def turntable(r, cen=(0, 8, 0), scale=11.0, size=(170, 280), yaws=(180, 135, 90, 215)):
    return [tile(r, y, -8, size, cen, scale) for y in yaws]
