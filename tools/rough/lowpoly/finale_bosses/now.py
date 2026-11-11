# -*- coding: utf-8 -*-
import importlib.util, sys, json
from pathlib import Path
import numpy as np
ROOT = Path(__file__).resolve().parents[4]
spec = importlib.util.spec_from_file_location("bs", ROOT / "tools/rough/lowpoly/boss_split/build.py")
bs = importlib.util.module_from_spec(spec); spec.loader.exec_module(bs)
from PIL import Image, ImageDraw
import sys as _s
names = _s.argv[1:] or ["founder", "last_bellkeeper", "lamp_eye", "rewinder", "pilgrim_plume", "pilgrim_hand", "pilgrim_eye", "pilgrim_moon"]
W, H = 300, 320
cv = Image.new("RGB", (W * 4, H * 4), (20, 20, 28))
for i, n in enumerate(names):
    faces, img, glow = bs.load(n)
    a2, g2 = bs.square(img, glow)
    V = np.concatenate([f.verts for f in faces]); lo, hi = V.min(0), V.max(0); c = (lo + hi) / 2; R = float(np.max(hi - lo)) / 2
    tris = sum(1 for _ in faces)
    for j, (yaw, pitch) in enumerate(((180, 6), (220, 14))):
        im = bs.render(faces, a2[..., :3], g2, yaw, pitch, (W, H), c, W * 0.44 / R)
        bg = Image.new("RGBA", (W, H), (28, 28, 38, 255)); bg.alpha_composite(im)
        x = (i % 2) * 2 * W + j * W; y = (i // 2) * H
        cv.paste(bg.convert("RGB"), (x, y))
    ImageDraw.Draw(cv).text(((i % 2) * 2 * W + 8, (i // 2) * H + 6), f"{n} ({tris} tris, {lo.round(0)}..{hi.round(0)})", font=bs.voxel.font(14), fill=(230, 230, 230))
cv.save(ROOT / ("docs/review/finale_now.png" if not _s.argv[1:] else "docs/review/now_" + names[0] + ".png"))
