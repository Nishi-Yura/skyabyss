"""確認用の描画 (開発用)。python view.py a out.png [front back side top face riders pose:<anim>@<秒> ...]"""
from __future__ import annotations

import importlib
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from yt import *  # noqa

CEN = (0, 34, 0)
SC = 1.6
# 名前 → (yaw, pitch, 中心, 縮尺)
VIEWS = {
    "front": (212, 15, CEN, SC),
    "back": (32, 15, CEN, SC),
    "side": (270, 4, CEN, SC),
    "face": (180, 4, CEN, SC),
    "top": (180, 89.9, CEN, SC),
    "close": (212, 15, (0, 40, -20), 3.2),
}


def get_option(key):
    mod = importlib.import_module("opt_" + key)
    return build_option(key, mod.build, mod.PAL, body_hint=getattr(mod, "HINT", 512)), mod


def pose_of(spec):
    """'pose:<anim>@<秒>' → 姿勢 (アニメのファイルの値)"""
    name, t = spec.split(":", 1)[1].split("@")
    return G.pose_at(name, float(t))


def render_view(r, name, size=(480, 400), pose=None, with_riders=False):
    yaw, pitch, cen, sc = VIEWS[name]
    return render_pose(r, pose or {}, yaw, pitch, size, cen, sc, riders() if with_riders else None)


def main():
    key, out = sys.argv[1], sys.argv[2]
    names = sys.argv[3:] or ["front", "back", "side"]
    r, mod = get_option(key)
    print(report(r))
    ims = []
    for n in names:
        pose = None
        view = n
        rid = False
        if n.startswith("pose:"):
            pose = pose_of(n)
            view = "front"
        if n.endswith("+r"):
            view, rid = n[:-2], True
        ims.append(on_bg(render_view(r, view, pose=pose, with_riders=rid)))
    W = Image.new("RGB", (sum(i.width for i in ims), ims[0].height))
    x = 0
    for i in ims:
        W.paste(i, (x, 0))
        x += i.width
    W.save(out)


if __name__ == "__main__":
    main()
