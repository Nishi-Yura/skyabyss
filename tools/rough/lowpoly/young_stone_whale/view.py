"""確認用の描画 (開発用)。python view.py a out.png [front back side top riders pose:<anim>@<秒> ...]
数値: 三角形の数、骨ごとの開いた辺、鞍の上面の高さ (格子の最小・最大)。"""
from __future__ import annotations

import importlib
import sys
from collections import Counter
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from ysw import *  # noqa

CEN = (0, 40, 8)
# 名前 → (yaw, pitch, 中心, 縮尺)。yaw 180 = 正面
VIEWS = {
    "front": (212, 15, CEN, 2.0),
    "back": (32, 15, CEN, 2.0),
    "side": (270, 4, CEN, 2.0),
    "face": (180, 4, CEN, 2.0),
    "top": (180, 89.9, (0, 40, 8), 2.0),
    "far": (212, 22, CEN, 0.8),
}


def get_option(key):
    mod = importlib.import_module("opt_" + key)
    return build_option(key, mod.build, mod.PAL, body_hint=getattr(mod, "HINT", 512), fit=getattr(mod, "FIT", None)), mod


def pose_of(spec):
    name, t = spec.split(":", 1)[1].split("@")
    return G.pose_at(name, float(t))


def stats(r):
    c = Counter()
    for f in r["body"]:
        c[f.bone] += len(f.verts) - 2
    lo = np.min([f.verts.min(0) for f in r["body"]], 0)
    hi = np.max([f.verts.max(0) for f in r["body"]], 0)
    oe = open_edges(r["data"])
    sh = seat_heights(r["body"])
    return dict(tris=tri_count(r["body"]), atlas=r["size"], per_bone=dict(c), lo=lo.round(1), hi=hi.round(1),
                open_edges=sum(oe.values()), open_by_bone={k: v for k, v in oe.items() if v},
                seat_min=float(np.nanmin(sh)), seat_max=float(np.nanmax(sh)), seat_nan=int(np.isnan(sh).sum()))


def main():
    key, out = sys.argv[1], sys.argv[2]
    names = sys.argv[3:] or ["front", "back", "side"]
    r, mod = get_option(key)
    print(stats(r))
    ims = []
    for n in names:
        pose, view, extra = None, n, None
        if n.startswith("pose:"):
            pose = pose_of(n)
            view = "front"
        if n == "riders":
            view, extra = "front", riders()
        yaw, pitch, cen, sc = VIEWS[view]
        ims.append(on_bg(render_pose(r, pose or {}, yaw, pitch, (420, 420), cen, sc, extra)))
    W = Image.new("RGB", (sum(i.width for i in ims), ims[0].height))
    x = 0
    for i in ims:
        W.paste(i, (x, 0))
        x += i.width
    W.save(out)


if __name__ == "__main__":
    main()
