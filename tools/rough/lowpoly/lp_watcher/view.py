"""確認用 (開発用)。python view.py a out.png"""
from __future__ import annotations

import importlib
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from common import *  # noqa

VIEWS = {"front": (205, 10, (0, 19, -1), 11.0), "q": (235, 12, (0, 19, -1), 11.0), "side": (295, 8, (0, 19, -1), 11.0),
         "back": (25, 10, (0, 19, -1), 11.0), "face": (200, 6, (0, 33, -1), 36.0)}


def get_option(key):
    mod = importlib.import_module("opt_" + key)
    return build_option(key, mod.build, hint=getattr(mod, "HINT", 96))


def bg(w, h):
    return Image.fromarray(np.clip(voxel._background(w, h, "dark"), 0, 255).astype(np.uint8))


if __name__ == "__main__":
    key, out = sys.argv[1], sys.argv[2]
    names = sys.argv[3:] or ["front", "q", "side", "back", "face"]
    r = get_option(key)
    print("tris", tri_count(r["faces"]), tri_by_bone(r["faces"]), "atlas", r["size"])
    ims = []
    for n in names:
        yaw, pitch, cen, sc = VIEWS[n]
        im = render_t(r["faces"], r["atlas"], r["glow"], yaw, pitch, (360, 480), cen, sc)
        b = bg(360, 480)
        b.paste(im, (0, 0), im)
        ims.append(b)
    W = Image.new("RGB", (360 * len(ims), 480))
    for i, im in enumerate(ims):
        W.paste(im, (360 * i, 0))
    W.save(out)
