"""確認用の描画 (開発用)。python view.py a out.png [front back face flared ...]"""
from __future__ import annotations

import importlib
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from shapes import *  # noqa
from PIL import Image  # noqa

POSES = {
    "rest": {},
    # attack: 0.45 s 振りかぶり / 0.55 s 振り下ろし (今のアニメの値)
    "wind": {"arm_r": (140.0, -45.0, 30.0), "forearm_r": (15.0, 0, 0), "socket_main": (-125.0, 0, 8.0),
             "body": (5.0, -6.0, 0.0), "head": (-4.0, 6.0, 0.0), "arm_l": (10.0, 0, -6.0)},
    "slam": {"arm_r": (38.0, 0, 14.0), "forearm_r": (25.0, 0, 0), "socket_main": (-160.0, 0, 8.0),
             "body": (-8.0, 5.0, 0.0), "head": (5.0, -4.0, 0.0), "arm_l": (-12.0, 0, -4.0)},
    "break": {"body": (6.0, 0, 0), "head": (7.0, 0, 0), "arm_r": (20.0, 0, 36.0), "arm_l": (20.0, 0, -36.0),
              "socket_main": (0, 0, -36.0)},
}
# 名前 → (yaw, pitch, 中心, 縮尺, 姿勢, 状態)
VIEWS = {
    "front": (205, 12, (0, 20, -1), 10.5, "rest", "n"),
    "back": (25, 12, (0, 20, -1), 10.5, "rest", "n"),
    "face": (200, 6, (0, 33, -1), 24.0, "rest", "n"),
    "flared": (205, 12, (0, 20, -1), 10.5, "rest", "f"),
    "flared_back": (25, 12, (0, 20, -1), 10.5, "rest", "f"),
    "flared_face": (200, 6, (0, 33, -1), 24.0, "rest", "f"),
    "wind": (200, 10, (0, 24, -4), 8.0, "wind", "n"),
    "slam": (200, 10, (0, 24, -4), 8.0, "slam", "f"),
}


def get_option(key):
    mod = importlib.import_module("opt_" + key)
    return build_option(key, mod.build, body_hint=getattr(mod, "HINT", 128)), mod


def render_view(r, name, size=(480, 580)):
    yaw, pitch, cen, sc, pose, state = VIEWS[name]
    return render_pose(r, POSES[pose], yaw, pitch, size, cen, sc, state=state, weapon=not name.endswith("face"))


def main():
    key, out = sys.argv[1], sys.argv[2]
    names = sys.argv[3:] or ["front", "back", "face", "flared"]
    r, mod = get_option(key)
    from collections import Counter
    c = Counter()
    for f in r["body"]:
        c[f.bone] += len(f.verts) - 2
    print("tris n", tri_state(r["body"], "n"), "f", tri_state(r["body"], "f"), "all", tri_count(r["body"]), "weapon", tri_count(r["weapon"]),
          "atlas", r["size"], r["bsize"], dict(c))
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
