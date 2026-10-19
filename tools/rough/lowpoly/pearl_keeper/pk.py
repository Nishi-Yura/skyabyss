"""真珠守り (pearl_keeper) 低ポリ ラフ 3 案の共通部分。

座標: 1 単位 = 1/16 ブロック。作図は「前 = -Z」(潮招きと同じ)。書き出すとき (x, z) の符号を反転して geo の空間へ
(geo の root は Y 軸 180 度回る。前は geo の +Z)。作図の +x が大きいはさみの側 (claw_r = ゲームの右)。
骨は 3 案で共通 (名前・親・pivot)。ゲームの箱の geo (tools/gen_pearl_keeper_assets.py) も同じ骨を使う。

絵の具 (paint / eye_marks / export_mesh) は潮招きの common.py をそのまま借りる。
"""
from __future__ import annotations

import copy
import importlib.util
import json
import math
import sys
from pathlib import Path

import numpy as np
from PIL import Image

HERE = Path(__file__).resolve().parent
LP = HERE.parent
ROOT = HERE.parents[3]
sys.path.insert(0, str(LP))
sys.path.insert(0, str(ROOT / "tools/rough"))
from lpkit import *  # noqa
import voxel  # noqa

_spec = importlib.util.spec_from_file_location("tb_common", LP / "tide_beckoner" / "common.py")
tbc = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(tbc)

OUT = ROOT / "docs/review/lowpoly_roughs"
FOLDER = OUT / "lp_pearl_keeper"
flipxz = tbc.flipxz
rot_geo = tbc.rot_geo


# ====================================================================== 骨 (作図空間 = 前が -Z)
# 名前: (親, pivot)。geo へは flipxz して書く。
sys.path.insert(0, str(ROOT / "tools"))
from gen_pearl_keeper_assets import SKELETON_DRAW  # noqa: E402  (骨はゲームの箱の geo と一つの定義)
BONES = {k: (p, [float(x) + 0.0 for x in flipxz(v)]) for k, (p, v) in SKELETON_DRAW.items()}


def pv(bone):
    return np.array(SKELETON_DRAW[bone][1], float)


def V(*a):
    return np.array(a, float)


# ====================================================================== 形の部品 (潮招きの parts / kit から写した薄い道具)

def rrect(c, u, v, w, h, cut=0.0):
    c, u, v = (np.asarray(a, float) for a in (c, u, v))
    u, v = nrm(u), nrm(v)
    hw, hh = w / 2.0, h / 2.0
    if cut <= 0:
        return [c - hw * u - hh * v, c + hw * u - hh * v, c + hw * u + hh * v, c - hw * u + hh * v]
    k = cut * min(hw, hh)
    pts = [(-hw + k, -hh), (hw - k, -hh), (hw, -hh + k), (hw, hh - k), (hw - k, hh), (-hw + k, hh), (-hw, hh - k),
           (-hw, -hh + k)]
    return [c + a * u + b * v for a, b in pts]


def rloft(bone, mat, rings, caps=(True, True), mats=None):
    return loft(bone, mat, rings, caps=tuple(mat if x is True else x for x in caps), mats=mats)


def fbox(bone, mat, c, size, R=None, taper=(1.0, 1.0), cut=0.0, caps=(True, True)):
    sx, sy, sz = size
    R = np.eye(3) if R is None else R
    ex, ey, ez = (R @ np.array(a) for a in ((1, 0, 0), (0, 1, 0), (0, 0, 1)))
    c = np.asarray(c, float)
    r0 = rrect(c - ey * sy / 2, ex, ez, sx, sz, cut)
    r1 = rrect(c + ey * sy / 2, ex, ez, sx * taper[0], sz * taper[1], cut)
    return rloft(bone, mat, [r0, r1], caps)


def rings_along(bone, mat, secs, axis_u, axis_v, caps=(True, True), cut=0.0, mats=None):
    return rloft(bone, mat, [rrect(c, axis_u, axis_v, w, h, cut) for c, w, h in secs], caps, mats)


def sp(bone, mat, base, d, length, r, sides=3, rings=1, bend=(0, 0, 0)):
    return spike(bone, mat, base, d, length, r, sides=sides, rings=rings, bend=bend)


def coral(bone, mat, base, d, length, r, branches, sides=4, rings=2, bend=(0, 0, 0), bmat=None, bsides=3, brings=1):
    """枝分かれする珊瑚。branches = [(幹の位置 0..1, 向き(度), 長さの割合, 開き(度))]。"""
    d = nrm(np.asarray(d, float))
    base = np.asarray(base, float)
    bend = np.asarray(bend, float)
    out = spike(bone, mat, base, d, length, r, sides=sides, rings=rings, bend=bend)
    ref = np.array([0, 1.0, 0]) if abs(d[1]) < 0.9 else np.array([1.0, 0, 0])
    u = nrm(np.cross(d, ref))
    v = np.cross(d, u)
    for br in branches:
        t, ang, fr, tilt = br[:4]
        p = base + d * length * t + bend * t * t
        a, tl = math.radians(ang), math.radians(tilt)
        dd = nrm(d * math.cos(tl) + (u * math.cos(a) + v * math.sin(a)) * math.sin(tl))
        out += spike(bone, bmat or mat, p, dd, length * fr, r * (1 - t) * 0.8 + 0.5, sides=bsides, rings=brings)
    return out


def bez(p0, p1, p2, n=4):
    """二次ベジェの点列 (n 区間)。"""
    p0, p1, p2 = (np.asarray(p, float) for p in (p0, p1, p2))
    return [(1 - t) ** 2 * p0 + 2 * (1 - t) * t * p1 + t * t * p2 for t in (i / n for i in range(n + 1))]


def helix_shell(bone, mat, base, axis_R, turns, r_tube, r_spiral, height, n=None, sides=7, start=0.0, cap_mat=None,
                rib_every=0, rib_mat=None, rib_k=1.12, mats=None, aim=None, anchor=None, mirror_x=False):
    """巻貝の殻: 円錐の螺旋に沿う太さの変わる筒。t=0 が殻口 (太い・下)、t=1 が殻頂 (細い・上)。
    r_tube(t), r_spiral(t), height(t) は関数。axis_R は局所 (軸 = +Y) → 作図空間の回転、base は軸の根元。
    aim を与えると、殻口の向き (水平の成分) が aim になるよう Y 軸で回し、anchor を与えると殻口の中心をそこへ動かす。
    rib_every > 0 なら、その点ごとに一回り太い畝の輪を足す。"""
    n = n or max(8, int(turns * 10))
    path, ra = [], []
    base = np.asarray(base, float)
    for i in range(n + 1):
        t = i / n
        th = math.radians(start) + 2 * math.pi * turns * t
        rs = r_spiral(t)
        p = np.array([rs * math.cos(th), height(t), rs * math.sin(th)])
        if mirror_x:
            p[0] = -p[0]
        path.append(axis_R @ p + base)
        ra.append(r_tube(t))
    ra[-1] = 0.0
    if aim is not None:
        d0 = path[0] - path[1]
        a_now = math.atan2(d0[0], -d0[2])
        a_want = math.atan2(aim[0], -aim[2])
        Ry = rot_y(-math.degrees(a_want - a_now))
        path = [Ry @ (p - base) + base for p in path]
        axis_R = Ry @ axis_R
    if anchor is not None:
        sh = np.asarray(anchor, float) - path[0]
        path = [p + sh for p in path]
    F = tube(bone, mat, path, ra, sides=sides, hint=axis_R @ np.array([0, 1.0, 0]), phase=0.0, caps=(False, False),
             mats=mats)
    # 殻口の蓋 (暗い中)
    if cap_mat:
        F += tube(bone, cap_mat, [path[0] + (path[0] - path[1]) * 0.02, path[0]], [ra[0] * 0.92, ra[0] * 0.92],
                  sides=sides, hint=axis_R @ np.array([0, 1.0, 0]), phase=0.0, caps=(True, False))
    if rib_every:
        for i in range(rib_every, n - 1, rib_every):
            p0, p1 = path[i] - (path[i + 1] - path[i]) * 0.12, path[i] + (path[i + 1] - path[i]) * 0.12
            F += tube(bone, rib_mat or mat, [p0, p1], [ra[i] * rib_k, ra[i] * rib_k], sides=sides,
                      hint=axis_R @ np.array([0, 1.0, 0]), phase=0.0, caps=(False, False))
    return F, path, ra


def pearl_ball(r=6.5, c=None, segs=8, rings=4):
    c = pv("pearl") if c is None else np.asarray(c, float)
    return ellipsoid("pearl", "pearl", c, (r, r, r), segs=segs, rings=rings)


# ====================================================================== 姿勢

def pose_point(p, bone, pose):
    """geo 空間の点 p を pose ({骨: {rotation:(x,y,z), scale:(..), position:(..)}} か {骨: (rx,ry,rz)}) のときの位置へ。
    GeckoLib の順: pivot へ寄せ → 拡大 → 回転 → 戻す → 位置。root の 180 度は含めない (作図空間で描くため)。"""
    while bone is not None and bone != "root":
        parent, pivot = BONES[bone]
        pv_ = np.array(pivot, float)
        st = pose.get(bone)
        if st is not None:
            if not isinstance(st, dict):
                st = {"rotation": st}
            q = p - pv_
            if "scale" in st:
                q = q * np.array(st["scale"], float)
            if "rotation" in st:
                q = rot_geo(*st["rotation"]) @ q
            p = q + pv_
            if "position" in st:
                ps = np.array(st["position"], float)
                p = p + np.array([-ps[0], ps[1], ps[2]])  # GeckoLib は位置の x を反転して読む
        bone = parent
    return p


def pose_body(faces, pose):
    out = []
    for f in faces:
        g = copy.copy(f)
        g.verts = np.array([flipxz(pose_point(flipxz(p), f.bone, pose)) for p in f.verts])
        tbc._refresh(g, f)
        out.append(g)
    return out


# ====================================================================== 書き出し

def build_option(key, build, pal, body_hint=256):
    body, eyes, extra = build()
    d = FOLDER / key
    d.mkdir(parents=True, exist_ok=True)
    size, P, N, M, L = build_atlas(body, body_hint, 0)

    def ex(c, col, glow):
        if eyes:
            col, glow = tbc.eye_marks(c, col, glow, eyes)
        if extra:
            col, glow = extra(c, col, glow)
        return col, glow
    atlas, glow = tbc.paint(body, size, P, N, M, pal, (), ex)
    stem = f"pearl_keeper_{key}"
    Image.fromarray(atlas, "RGBA").save(d / f"{stem}_atlas.png")
    gm = np.zeros((size, size, 4), np.uint8)
    gm[glow] = (255, 255, 255, 255)
    Image.fromarray(gm, "RGBA").save(d / f"{stem}_glowmask.png")
    data = tbc.export_mesh(body, size, d / f"{stem}_mesh.json", stem, f"{stem}_atlas.png", BONES,
                           {"glowmask": f"{stem}_glowmask.png",
                            "note": "geo space (root rotated 180 deg about Y: root carries rotation [0,180,0]); "
                                    "vertices are absolute like geo cubes; 14 bones = geo/entity/pearl_keeper.geo.json; no gear"})
    return dict(key=key, body=body, eyes=eyes, size=size, atlas=atlas, glow=glow, data=data)


def render_pose(r, pose, yaw, pitch, size, center, scale):
    return render(pose_body(r["body"], pose or {}), r["atlas"], r["glow"], yaw, pitch, size, center, scale)


def bgpanel(w, h):
    return Image.fromarray(np.clip(voxel._background(w, h, "dark"), 0, 255).astype(np.uint8))


def on_bg(im):
    bg = bgpanel(*im.size)
    bg.paste(im, (0, 0), im)
    return bg
