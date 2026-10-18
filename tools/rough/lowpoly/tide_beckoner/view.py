"""確認用の描画 (開発用)。python view.py a out.png [front back claw side raise beckon ...]"""
from __future__ import annotations

import importlib
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from common import *  # noqa
from PIL import Image  # noqa

POSES = {
    "rest": {},
    "raise": {'claw_big': (25, -150, 10), 'claw_big_pincer': (30, 0, 0), 'body': (6, 0, 0)},
    "strike": {'claw_big': (-5, 5, 10), 'claw_big_pincer': (-10, 0, 0), 'body': (-8, 0, 0)},
    "beckon": {'claw_big': (55, -20, -5), 'claw_big_pincer': (-10, 0, 0)},
    "beckon_open": {'claw_big': (30, -20, -5), 'claw_big_pincer': (30, 0, 0)},
    "roar": {'claw_big': (12, -70, 5), 'body': (6, 0, 0), 'leg_r1': (0, 0, 25), 'leg_l1': (0, 0, -25)},
}
# 名前 → (yaw, pitch, 中心, 縮尺, 姿勢, 骨だけ)
VIEWS = {
    "front": (212, 14, (0, 22, -34), 3.4, "rest"),
    "back": (32, 14, (0, 22, -34), 3.4, "rest"),
    "side": (270, 8, (0, 22, -34), 3.4, "rest"),
    "top": (212, 60, (0, 22, -34), 3.4, "rest"),
    "claw": (225, 16, (40, 20, -62), 7.0, "rest"),
    "claw2": (150, 12, (40, 20, -62), 7.0, "rest"),
    "head": (205, 8, (0, 34, -22), 8.5, "rest"),
    "raise": (150, 14, (0, 32, -34), 3.4, "raise"),
    "strike": (212, 14, (0, 22, -34), 3.4, "strike"),
    "beckon": (212, 12, (0, 30, -34), 3.4, "beckon"),
}


def get_option(key, hint=None):
    mod = importlib.import_module("opt_" + key)
    return build_option(key, mod.build, mod.PAL, body_hint=getattr(mod, "HINT", 256)), mod


def render_view(r, name, size=(520, 470)):
    yaw, pitch, cen, sc, pose = VIEWS[name]
    return render_pose(r, POSES[pose], yaw, pitch, size, cen, sc)


def main():
    key, out = sys.argv[1], sys.argv[2]
    names = sys.argv[3:] or ["front", "back", "claw"]
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
