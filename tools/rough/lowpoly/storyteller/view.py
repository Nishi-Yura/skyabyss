"""確認用の描画 (開発用)。python view.py <old|a|b|c> out.png [front back head slam tongs ...]"""
from __future__ import annotations

import importlib
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from common import *  # noqa
from PIL import Image  # noqa

# 姿勢は今のアニメのファイルの値 (animations/entity/storyteller.animation.json)
POSES = {
    "rest": {},
    "talk": {"armR": (22, 0, 0), "armL": (6, 0, 0), "shawl": (1, 0, 0)},
    "talk2": {"armR": (34, 0, -4), "armL": (8, 0, 3), "head": (-6, 8, 0)},
}
VIEWS = {
    "front": (200, 6, (0, 15, 0), 14.0, "rest"),
    "q": (222, 8, (0, 15, 0), 14.0, "rest"),
    "side": (270, 4, (0, 15, 0), 14.0, "rest"),
    "back": (28, 8, (0, 15, 0), 14.0, "rest"),
    "head": (205, 6, (0, 21, -2), 40.0, "rest"),
    "talk": (212, 6, (0, 15, 0), 14.0, "talk"),
}


def get_option(key):
    mod = importlib.import_module("opt_" + key)
    return build_option(key, mod.build, mod.PAL, body_hint=getattr(mod, "HINT", 128)), mod


def bgpanel(w, h):
    return Image.fromarray(np.clip(voxel._background(w, h, "dark"), 0, 255).astype(np.uint8))


def old_render(faces, yaw, pitch, size, center, scale):
    cy, sy_ = math.cos(math.radians(yaw)), math.sin(math.radians(yaw))
    cp, sp_ = math.cos(math.radians(pitch)), math.sin(math.radians(pitch))
    Ry = np.array([[cy, 0, sy_], [0, 1, 0], [-sy_, 0, cy]])
    Rx = np.array([[1, 0, 0], [0, cp, -sp_], [0, sp_, cp]])
    V = Rx @ Ry
    pts = []
    for f in faces:
        for a in (0, 1):
            for b in (0, 1):
                pts.append(V @ (f.origin + a * f.eu + b * f.ev))
    sc = V @ np.asarray(center, float)
    pts = np.array(pts)
    lo, hi = pts.min(0), pts.max(0)
    span = max(hi[0] - lo[0], hi[1] - lo[1])
    S = int(math.ceil(span * scale))
    eff = S / span
    bgf = voxel._background
    voxel._background = lambda W_, H_, bg: np.full((H_, W_, 3), 3.0)
    try:
        im = voxel.render(faces, yaw=yaw, pitch=pitch, size=(S, S), margin=0.0)
    finally:
        voxel._background = bgf
    mask = Image.fromarray((np.asarray(im).max(-1) > 6).astype(np.uint8) * 255)
    cx_, cy_ = (lo[0] + hi[0]) / 2, (lo[1] + hi[1]) / 2
    px = S / 2 + (sc[0] - cx_) * eff
    py = S / 2 - (sc[1] - cy_) * eff
    out = bgpanel(size[0], size[1])
    x0, y0 = int(round(px - size[0] / 2)), int(round(py - size[1] / 2))
    out.paste(im, (-x0, -y0), mask)
    return out


def main():
    key, out = sys.argv[1], sys.argv[2]
    names = sys.argv[3:] or ["front", "back", "head"]
    size = (520, 470)
    ims = []
    if key == "old":
        for n in names:
            yaw, pitch, cen, sc, pose = VIEWS[n]
            f, nb = old_faces(POSES[pose])
            ims.append(old_render(f, yaw, pitch, size, cen, sc))
    else:
        r, mod = get_option(key)
        from collections import Counter
        c = Counter()
        for f in r["body"]:
            c[f.bone] += len(f.verts) - 2
        print("tris", tri_count(r["body"]), 
              "atlas", r["size"], dict(c))
        for n in names:
            yaw, pitch, cen, sc, pose = VIEWS[n]
            im = render_pose(r, POSES[pose], yaw, pitch, size, cen, sc)
            bg = bgpanel(*im.size)
            bg.paste(im, (0, 0), im)
            ims.append(bg)
    W = Image.new("RGB", (sum(i.width for i in ims), ims[0].height))
    x = 0
    for i in ims:
        W.paste(i, (x, 0))
        x += i.width
    W.save(out)


if __name__ == "__main__":
    main()
