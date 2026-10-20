"""巨神シリーズ (S2) の防具アイコン 32x32 を 3D から描いて textures/item/<set>_<piece>.png に入れる。
実行: python build_series_icons.py [S2] [preview.png]"""
from __future__ import annotations

import sys

import numpy as np
from PIL import Image, ImageEnhance

import aview
import scene
import spaint
import sstyle
from akit import ROOT
from common import TITANS, SETS

aview.apaint = spaint
aview.SS = 2
ITEM = ROOT / "src/main/resources/assets/skyabyss/textures/item"
VIEW = {"helmet": (205, 14), "chestplate": (195, 6), "leggings": (190, 6), "boots": (215, 18)}
R = 320          # 描く大きさ
FIT = 30         # 32 の中で使う大きさ
OUTLINE = (22, 20, 28, 255)


def render_piece(res, T, piece):
    faces, atlas, glow = aview.scene_of(res, only=[piece], mann=False)
    v = np.concatenate([f.verts for f in faces])
    lo, hi = v.min(0), v.max(0)
    cen = tuple((lo + hi) / 2)
    ext = float(max(hi - lo)) * 1.25
    yaw, pitch = VIEW[piece]
    im = scene.view(faces, atlas, glow, T, yaw, pitch, (R, R), cen, R / ext, ss=3)
    return im.transpose(Image.FLIP_LEFT_RIGHT)


def to_icon(im):
    im = im.convert("RGBA")
    bb = im.getchannel("A").point(lambda a: 255 if a > 40 else 0).getbbox()
    im = im.crop(bb)
    w, h = im.size
    k = FIT / max(w, h)
    nw, nh = max(1, round(w * k)), max(1, round(h * k))
    # 色は混ぜずに平均、縁は二値
    a = np.array(im).astype(np.float32)
    a[..., :3] *= a[..., 3:4] / 255.0
    small = np.array(Image.fromarray(a.clip(0, 255).astype(np.uint8), "RGBA").resize((nw, nh), Image.BOX)).astype(np.float32)
    al = small[..., 3]
    rgb = np.where(al[..., None] > 0, small[..., :3] * 255.0 / np.maximum(al[..., None], 1), 0)
    out = np.zeros((nh, nw, 4), np.uint8)
    out[..., :3] = rgb.clip(0, 255)
    out[..., 3] = np.where(al > 110, 255, 0)
    ic = Image.fromarray(out, "RGBA")
    ic = ImageEnhance.Contrast(ic).enhance(1.12)
    ic = ImageEnhance.Color(ic).enhance(1.1)
    can = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    can.alpha_composite(ic, ((32 - nw) // 2, (32 - nh) // 2 + (1 if nh < 30 else 0)))
    c = np.array(can)
    op = c[..., 3] > 0
    ring = np.zeros_like(op)
    for dy, dx in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        ring |= np.roll(np.roll(op, dy, 0), dx, 1)
    ring &= ~op
    c[ring] = OUTLINE
    return Image.fromarray(c, "RGBA")


def main(v="S2", preview=None):
    sheet = Image.new("RGBA", (32 * 4 * 5, 32 * 4 * 5), (40, 44, 52, 255))
    for r, T in enumerate(TITANS):
        res = aview.build_set(sstyle.build(T, "rare", v), T, "rare")
        for c, piece in enumerate(VIEW):
            ic = to_icon(render_piece(res, T, piece))
            ic.save(ITEM / f"{SETS[T]}_{piece}.png", optimize=True)
            sheet.alpha_composite(ic.resize((128, 128), Image.NEAREST), (c * 160 + 16, r * 160 + 16))
        print(T, flush=True)
    if preview:
        sheet.save(preview)


if __name__ == "__main__":
    a = sys.argv[1:]
    main(a[0] if a else "S2", a[1] if len(a) > 1 else None)
