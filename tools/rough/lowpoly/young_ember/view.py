"""確認用の描画 (開発用)。python view.py a out.png [front back side top riders pose:<anim>@<秒> ...]
三角形の数・開いた辺・鞍の高さも出す。"""
from __future__ import annotations

import importlib
import sys
from collections import Counter
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from ye import *  # noqa

CEN = (0, 30, -4)
SC = 1.7
# 名前 → (yaw, pitch, 中心, 縮尺)
VIEWS = {
    "front": (212, 15, CEN, SC),
    "back": (32, 15, CEN, SC),
    "side": (270, 0, CEN, SC),
    "face": (180, 4, CEN, SC),
    "top": (180, 90, (0, 30, -4), SC),
    "close": (212, 15, (0, 40, -60), 4.0),
    "closeb": (40, 20, (0, 36, 40), 4.0),
}


def get_option(key):
    mod = importlib.import_module("opt_" + key)
    return build_option(key, mod.build, mod.PAL, body_hint=getattr(mod, "HINT", 512)), mod


def pose_of(spec):
    name, t = spec.split(":", 1)[1].split("@")
    return G.pose_at(name, float(t))


def stats(r):
    oe, per = open_edges(r["data"])
    top, above, miss = seat_check(r["body"])
    lo, hi = bounds(r["body"])
    return dict(tris=tri_count(r["body"]), atlas=r["size"], open_edges=oe, open_per_bone=per, seat_top=round(float(top), 3),
                above_seat=above, seat_miss=miss, lo=lo.round(1).tolist(), hi=hi.round(1).tolist(),
                size_blocks=((hi - lo) / 16).round(2).tolist())


def main():
    key, out = sys.argv[1], sys.argv[2]
    names = sys.argv[3:] or ["front", "back", "side"]
    r, mod = get_option(key)
    print(stats(r))
    ims = []
    for n in names:
        pose, view, ex = None, n, None
        if n.startswith("pose:"):
            pose = pose_of(n)
            view = "front"
        if n == "riders":
            view, ex = "top", riders()
        yaw, pitch, cen, sc = VIEWS[view]
        ims.append(on_bg(render_pose(r, pose or {}, yaw, pitch, (420, 420), cen, sc, ex)))
    W = Image.new("RGB", (sum(i.width for i in ims), ims[0].height))
    x = 0
    for i in ims:
        W.paste(i, (x, 0))
        x += i.width
    W.save(out)


if __name__ == "__main__":
    main()
