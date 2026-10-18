"""確認用の描画 (開発用)。python view.py a out.png [front back face cast ...]"""
from __future__ import annotations

import importlib
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from common import *  # noqa
from PIL import Image  # noqa

POSES = {
    "rest": {},
    "cast": {"arm_r": (136.0, 0, 0), "socket_main": (-85.0, 0, 0), "arm_l": (72.0, 0, 11.0), "body": (10.0, 0, -3.0),
             "head": (32.0, 0, 0)},
    "cast_hi": {"arm_r": (138.0, 0, 0), "socket_main": (-85.0, 0, 0), "arm_l": (75.0, 0, 12.0), "body": (12.0, 0, 3.0),
                "head": (34.0, 0, 0)},
    "swing": {"arm_r": (60.0, 0, 0), "socket_main": (-30.0, 0, 0), "body": (-8.0, 0, 0)},
}
# 名前 → (yaw, pitch, 中心, 縮尺, 姿勢)
VIEWS = {
    "front": (205, 14, (0, 19, -2), 10.0, "rest"),
    "back": (25, 14, (0, 19, -2), 10.0, "rest"),
    "side": (270, 8, (0, 19, -2), 10.0, "rest"),
    "face": (200, 8, (0, 26, -2), 20.0, "rest"),
    "face_ns": (200, 8, (0, 26, -2), 20.0, "rest"),
    "cast": (205, 14, (0, 24, -6), 8.0, "cast"),
    "cast_side": (250, 12, (0, 24, -6), 8.0, "cast"),
}


def get_option(key):
    mod = importlib.import_module("opt_" + key)
    return build_option(key, mod.build, mod.PAL, getattr(mod, "STAFF_PAL", None), body_hint=getattr(mod, "HINT", 128)), mod


def render_view(r, name, size=(480, 580), staff=True):
    ns = name.endswith("_ns")
    yaw, pitch, cen, sc, pose = VIEWS[name[:-3] if ns else name]
    staff = staff and not ns
    return render_pose(r, POSES[pose], yaw, pitch, size, cen, sc, staff=staff)


def main():
    key, out = sys.argv[1], sys.argv[2]
    names = sys.argv[3:] or ["front", "back", "face"]
    r, mod = get_option(key)
    from collections import Counter
    c = Counter()
    for f in r["body"]:
        c[f.bone] += len(f.verts) - 2
    print("tris", tri_count(r["body"]), "staff", tri_count(r["staff"]), "atlas", r["size"], r["bsize"], dict(c))
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
