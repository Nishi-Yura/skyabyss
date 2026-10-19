"""確認用の描画 (開発用)。python view.py a out.png [front back side top pose:<anim>@<秒> ...]"""
from __future__ import annotations

import importlib
import sys
from collections import Counter
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from pk import *  # noqa

CEN = (0, 26, -6)
# 名前 → (yaw, pitch, 中心, 縮尺)
VIEWS = {
    "front": (212, 14, CEN, 6.0),
    "back": (32, 14, CEN, 6.0),
    "side": (270, 6, CEN, 6.0),
    "face": (180, 4, CEN, 6.0),
    "top": (212, 60, CEN, 6.0),
    "far": (212, 22, CEN, 1.6),
}


def get_option(key):
    mod = importlib.import_module("opt_" + key)
    return build_option(key, mod.build, mod.PAL, body_hint=getattr(mod, "HINT", 256)), mod


def pose_of(spec):
    """'pose:<anim>@<秒>' → 姿勢 (アニメのファイルの値)"""
    sys.path.insert(0, str(ROOT / "tools"))
    import gen_pearl_keeper_assets as g
    name, t = spec.split(":", 1)[1].split("@")
    return g.pose_at(name, float(t))


def render_view(r, name, size=(420, 420), pose=None):
    yaw, pitch, cen, sc = VIEWS[name]
    return render_pose(r, pose or {}, yaw, pitch, size, cen, sc)


def main():
    key, out = sys.argv[1], sys.argv[2]
    names = sys.argv[3:] or ["front", "back", "side"]
    r, mod = get_option(key)
    c = Counter()
    for f in r["body"]:
        c[f.bone] += len(f.verts) - 2
    lo = np.min([f.verts.min(0) for f in r["body"]], 0)
    hi = np.max([f.verts.max(0) for f in r["body"]], 0)
    print("tris", tri_count(r["body"]), "atlas", r["size"], dict(c), "lo", lo.round(1), "hi", hi.round(1))
    ims = []
    for n in names:
        pose = None
        view = n
        if n.startswith("pose:"):
            pose = pose_of(n)
            view = "front"
        ims.append(on_bg(render_view(r, view, pose=pose)))
    W = Image.new("RGB", (sum(i.width for i in ims), ims[0].height))
    x = 0
    for i in ims:
        W.paste(i, (x, 0))
        x += i.width
    W.save(out)


if __name__ == "__main__":
    main()
