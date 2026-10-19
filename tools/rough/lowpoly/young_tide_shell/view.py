"""確認用の描画 (開発用)。python view.py a out.png [front back side top face riders pose:<anim>@<秒> ...]"""
from __future__ import annotations

import importlib
import sys
from collections import Counter
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from ys import *  # noqa

CEN = (0, 22, -6)
SC = 2.3
# 名前 → (yaw, pitch, 中心, 縮尺)。yaw 180 = 正面 (前から見る)
VIEWS = {
    "front": (212, 15, CEN, SC),
    "back": (32, 15, CEN, SC),
    "side": (270, 4, CEN, SC),
    "face": (180, 4, CEN, SC),
    "top": (180, 90, (0, 26, 0), SC),
}


def get_option(key):
    mod = importlib.import_module("opt_" + key)
    return build_option(key, mod.build, mod.PAL, body_hint=getattr(mod, "HINT", 512)), mod


def pose_of(spec):
    """'pose:<anim>@<秒>' → 姿勢 (アニメのファイルの値)"""
    name, t = spec.split(":", 1)[1].split("@")
    return G.pose_at(name, float(t))


def stats(r):
    c = Counter()
    for f in r["body"]:
        c[f.bone] += len(f.verts) - 2
    lo, hi = bounds(r["body"])
    oe, per = open_edges(r["data"])
    top, above = seat_check(r["data"])
    return dict(tris=tri_count(r["body"]), atlas=r["size"], per_bone=dict(c), lo=(lo / 16).round(2).tolist(),
                hi=(hi / 16).round(2).tolist(), open_edges=oe, open_per_bone=per, seat_top=top,
                above_seat=above)


def main():
    key, out = sys.argv[1], sys.argv[2]
    names = sys.argv[3:] or ["front", "back", "side", "top"]
    r, mod = get_option(key)
    print(stats(r))
    ims = []
    for n in names:
        pose, view, riders = None, n, False
        if n.startswith("pose:"):
            pose = pose_of(n)
            view = "front"
        if n.startswith("riders"):
            riders = True
            view = n.split("_")[1] if "_" in n else "top"
        yaw, pitch, cen, sc = VIEWS[view]
        ims.append(on_bg(render_pose(r, pose, yaw, pitch, (440, 340), cen, sc, riders=riders)))
    W = Image.new("RGB", (sum(i.width for i in ims), ims[0].height))
    x = 0
    for i in ims:
        W.paste(i, (x, 0))
        x += i.width
    W.save(out)


if __name__ == "__main__":
    main()
