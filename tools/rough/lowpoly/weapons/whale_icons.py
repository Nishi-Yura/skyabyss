"""岩鯨の武器 3 点の GUI アイコン (32x32) を、作り直したメッシュから写して textures/item/ に書く。
傾き -45 度のまま正面から写し、縁は暗い 1 画素。階級は実際の品の階級 (大剣 = レア、鉾・牙 = 伝説)。
実行: python whale_icons.py"""
from __future__ import annotations

import importlib

import numpy as np
from PIL import Image

from scene import *  # noqa: F401,F403
from paint import paint
from mock import xf
from wkit import rot_z, V

NATURAL = {"whalebone_greatsword": ("item_whalebone_greatsword", "rare"), "captain_pike": ("item_captain_pike", "legend"),
           "waterway_fang": ("item_waterway_fang", "legend")}


def icon(mod, rar):
    faces, size, P, N, M, L = build(mod.faces(rar))
    atlas, glow = paint(faces, size, P, N, M, L, mod.TITAN, rar, 0, mod.inlay, mod.EDGE)
    fs = xf(faces, rot_z(-45), V(0, 0, 0), 1.0, V(*mod.GRIP))
    pts = np.concatenate([f.verts for f in fs])
    ext = max(np.ptp(pts[:, 0]), np.ptp(pts[:, 1]))
    sc = 28.0 / ext * 4
    mid = (pts.max(0) + pts.min(0)) / 2
    im = view(fs, atlas, glow, mod.TITAN, 180, 0, (128, 128), (mid[0], mid[1], 0), sc, ss=2, outline=False)
    small = Image.fromarray(np.array(im)).resize((32, 32), Image.BOX)
    arr = np.array(small).astype(float)
    solid = arr[..., 3] >= 70
    rgb = np.clip(arr[..., :3] * 1.35 + 10, 0, 255)
    out = np.zeros((32, 32, 4), np.uint8)
    out[solid, :3] = rgb[solid].astype(np.uint8)
    out[solid, 3] = 255
    near = np.zeros_like(solid)
    for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        near |= np.roll(np.roll(solid, dx, 1), dy, 0)
    out[~solid & near] = (14, 14, 20, 255)
    return Image.fromarray(out, "RGBA")


def main():
    dst = ROOT / "src/main/resources/assets/skyabyss/textures/item"
    for wid, (modname, rar) in NATURAL.items():
        icon(importlib.import_module(modname), rar).save(dst / f"{wid}.png")
        print("icon", wid)


if __name__ == "__main__":
    main()
