"""確認用の描画 (開発用)。python view.py a out.png [front back head leap claw ...]"""
from __future__ import annotations

import importlib
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from common import *  # noqa
from PIL import Image  # noqa

LEAP = {"body": (-16, 0, 0), "chest": (-6, 0, 0), "head": (14, 0, 0), "jaw": (-8, 0, 0),
        "leg_fl": (-40, 0, 0), "leg_fl_lo": (-20, 0, 0), "leg_fr": (-40, 0, 0), "leg_fr_lo": (-20, 0, 0),
        "leg_bl": (51, 0, 0), "leg_bl_lo": (25, 0, 0), "leg_br": (51, 0, 0), "leg_br_lo": (25, 0, 0),
        "tail": (51, 0, 0), "tail_2": (-10, 0, 0), "tail_3": (-10, 0, 0)}
POSES = {
    "rest": {},
    "leap": LEAP,
    "claw": {"leg_fr": (-80, 0, 8), "leg_fr_lo": (-30, 0, 0), "body": (-10, 0, 0), "jaw": (17.5, 0, 0)},
    "crouch": {"body": (6, 0, 0), "chest": (6, 0, 0), "head": (-6, 0, 0), "jaw": (12, 0, 0),
               "leg_fl": (30, 0, 0), "leg_fr": (30, 0, 0), "leg_fl_lo": (-30, 0, 0), "leg_fr_lo": (-30, 0, 0),
               "leg_bl": (-20, 0, 0), "leg_br": (-20, 0, 0), "leg_bl_lo": (30, 0, 0), "leg_br_lo": (30, 0, 0),
               "tail": (45, 0, 0), "tail_2": (20, 0, 0), "tail_3": (15, 0, 0)},
    "roar": {"body": (-14, 0, 0), "head": (-32, 0, 0), "jaw": (34, 0, 0), "leg_fl": (14, 0, 0), "leg_fr": (14, 0, 0),
             "tail": (50, 0, 0), "tail_2": (25, 0, 0)},
}
VIEWS = {
    "front": (212, 14, (0, 30, 8), 3.8, "rest"),
    "back": (32, 14, (0, 30, 8), 3.8, "rest"),
    "side": (270, 6, (0, 30, 8), 3.8, "rest"),
    "head": (205, 8, (0, 40, -36), 10.0, "rest"),
    "leap": (250, 10, (0, 30, 6), 3.0, "leap"),
    "claw": (200, 8, (0, 36, -10), 3.8, "claw"),
    "crouch": (230, 12, (0, 28, 6), 3.3, "crouch"),
    "roar": (240, 8, (0, 36, -4), 3.4, "roar"),
}


def get_option(key):
    mod = importlib.import_module("opt_" + key)
    return build_option(key, mod.build, mod.PAL, body_hint=getattr(mod, "HINT", 256)), mod


def render_view(r, name, size=(520, 470)):
    yaw, pitch, cen, sc, pose = VIEWS[name]
    return render_pose(r, POSES[pose], yaw, pitch, size, cen, sc)


def main():
    key, out = sys.argv[1], sys.argv[2]
    names = sys.argv[3:] or ["front", "back", "head"]
    r, mod = get_option(key)
    from collections import Counter
    c = Counter()
    for f in r["body"]:
        c[f.bone] += len(f.verts) - 2
    print("tris", tri_count(r["body"]), "atlas", r["size"], dict(c))
    ims = []
    for n in names:
        im = render_view(r, n)
        bg = Image.fromarray(np.clip(voxel._background(*im.size, "dark"), 0, 255).astype(np.uint8))
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
