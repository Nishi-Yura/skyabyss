"""確認用の描画: 案のメッシュを組み立て、前・後ろ・頭・鉾を描く。python view.py A out.png [front|back|head|pike|sweep|slam ...]"""
from __future__ import annotations

import importlib
import sys

from ck import *

YAW_FRONT, YAW_BACK, PITCH = 212, 32, 12


def combined(r):
    S, ps = r["size"], r["psize"]
    side = max(S, ps)
    big = np.zeros((S + ps, side, 4), np.uint8)
    big = np.zeros((max(S + ps, side), max(S, ps), 4), np.uint8)
    big[:S, :S] = r["atlas"]
    big[S:S + ps, :ps] = r["patlas"]
    glow = np.zeros(big.shape[:2], bool)
    glow[:S, :S] = r["glow"]
    glow[S:S + ps, :ps] = r["pglow"]
    # render() は正方形を仮定しない (S = atlas.shape[0] で clip) ので、正方形にそろえる
    n = max(big.shape[:2])
    B = np.zeros((n, n, 4), np.uint8)
    B[:big.shape[0], :big.shape[1]] = big
    G = np.zeros((n, n), bool)
    G[:big.shape[0], :big.shape[1]] = glow
    return B, G, S


def render_pose(r, big, glow, yoff, pose, yaw, pitch, size, center, scale, with_pike=True):
    faces = pose_faces(r["body"], pose)
    if with_pike:
        pk = pose_faces(r["pike"], pose, gear_bone="socket_main", gear_off=SOCK_R)
        for f in pk:
            f.uv = f.uv + np.array([0, yoff])
        faces = faces + pk
    return render(faces, big, glow, yaw, pitch, size, center, scale)


def get_option(key):
    mod = importlib.import_module("opt" + key.upper())
    return build_option(mod)


def main():
    key = sys.argv[1]
    out = sys.argv[2]
    views = sys.argv[3:] or ["front", "back", "head"]
    r = get_option(key)
    big, glow, yoff = combined(r)
    tris = tri_count(r["body"])
    ptris = tri_count(r["pike"])
    print("tris body", tris, "pike", ptris, "atlas", r["size"], r["psize"])
    from collections import Counter
    c = Counter()
    for f in r["body"]:
        c[f.bone] += len(f.verts) - 2
    print(dict(c))
    ims = []
    pose = pose_from_json(IDLE_JSON)
    for v in views:
        if v == "front":
            ims.append(render_pose(r, big, glow, yoff, pose, YAW_FRONT, PITCH, (540, 700), (0, 22, 0), 10.2))
        elif v == "back":
            ims.append(render_pose(r, big, glow, yoff, pose, YAW_BACK, PITCH, (540, 700), (0, 22, 0), 10.2))
        elif v == "head":
            ims.append(render_pose(r, big, glow, yoff, pose, 200, 8, (480, 480), (0, 40, 0), 30, with_pike=False))
        elif v == "headb":
            ims.append(render_pose(r, big, glow, yoff, pose, 40, 8, (480, 480), (0, 40, 0), 30, with_pike=False))
        elif v == "pike":
            ims.append(render_pose(r, big, glow, yoff, {}, 270, 6, (480, 300), (0, 0, -7), 9, with_pike=True))
        elif v in ("sweep", "slam"):
            t = 0.50 if v == "sweep" else 0.18
            p = pose_from_json({**IDLE_JSON, **anim_pose(v, t)})
            ims.append(render_pose(r, big, glow, yoff, p, 150, PITCH, (540, 700), (0, 22, 0), 10.2))
    W = sum(i.size[0] for i in ims) + 8 * len(ims)
    H = max(i.size[1] for i in ims)
    canvas = Image.new("RGB", (W, H), (20, 20, 28))
    x = 0
    for i in ims:
        bg = Image.new("RGB", i.size, (24, 24, 34))
        bg.paste(i, (0, 0), i)
        canvas.paste(bg, (x, 0))
        x += i.size[0] + 8
    canvas.save(out)


if __name__ == "__main__":
    main()
