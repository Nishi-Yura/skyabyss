"""確認用の描画 (開発用)。python view.py <old|a|b|c> out.png [front back head slam tongs ...]"""
from __future__ import annotations

import importlib
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from common import *  # noqa
from PIL import Image  # noqa

# 姿勢は今のアニメのファイルの値 (animations/entity/ash_smith.animation.json)
POSES = {
    "rest": {},
    # slam の 0.9 秒 (振りかぶりの頂点) と 1.05 秒 (打ち下ろしの着地)
    "slam_up": {"arm_r": (160, 0, 8), "forearm_r": (25, 0, 0), "socket_main": (-20, 0, 0), "body": (10, 0, 0),
                "head": (-8, 0, 0), "arm_l": (20, 0, -6), "forearm_l": (15, 0, 0)},
    "slam_hit": {"arm_r": (30, 0, 3), "forearm_r": (10, 0, 0), "socket_main": (-30, 0, 0), "body": (-12, 0, 0),
                 "head": (9, 0, 0), "arm_l": (-14, 0, -4), "forearm_l": (12, 0, 0)},
    # sparks 0.4〜1.2 秒: 左の火箸を前へ差し出す
    "tongs": {"arm_l": (72, 0, -10), "forearm_l": (22, 0, 0), "arm_r": (118, 0, 10), "forearm_r": (24, 0, 0),
              "socket_main": (-14, 0, 0), "body": (4, 0, 0), "head": (-4, 0, 0)},
    "rage": {"body": (16, 0, 0), "head": (18, 0, 0), "arm_r": (36, 0, 62), "arm_l": (36, 0, -62),
             "forearm_r": (20, 0, 0), "forearm_l": (20, 0, 0)},
}
VIEWS = {
    "front": (212, 12, (0, 40, 0), 3.8, "rest"),
    "back": (32, 12, (0, 40, 0), 3.8, "rest"),
    "side": (270, 5, (0, 40, 0), 3.8, "rest"),
    "head": (205, 8, (0, 54, -28), 11.0, "rest"),
    "slam": (235, 10, (0, 48, 0), 3.0, "slam_up"),
    "hit": (235, 10, (0, 36, -8), 3.2, "slam_hit"),
    "tongs": (215, 10, (0, 40, -8), 3.2, "tongs"),
}


def get_option(key):
    mod = importlib.import_module("opt_" + key)
    return build_option(key, mod.build, mod.PAL, body_hint=getattr(mod, "HINT", 256)), mod


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
        print("tris", tri_count(r["body"]), "gear", {k: tri_count(g["faces"]) for k, g in r["gear"].items()},
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
