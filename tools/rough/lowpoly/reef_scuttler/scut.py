"""磯走り (reef_scuttler) 低ポリ ラフ 3 案の共通部分。src には触らない。

作図の向きは「前 = -Z」(ゲーム内 geo は root が Y 軸 180 度回っていて cube は +Z 向きで焼いてあるため、
書き出すとき (x, z) の符号を反転して geo の空間へ戻す)。骨の名前・階層・pivot は geo/entity/reef_scuttler.geo.json と同じ。
作図空間では geo の "_r" の骨が +X 側に来る (geo の x が負 = 作図で正)。
"""
from __future__ import annotations

import copy
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
sys.path.insert(0, str(LP / "rib_archer"))
from lpkit import *  # noqa
from lpkit import _mk  # noqa
import common as RC  # noqa  (rib_archer: paint / export_mesh / hash)
from common import hash1, vnoise, BAYER, LIGHT, flipxz, rot_geo  # noqa
import voxel  # noqa

OUT = ROOT / "docs/review/lowpoly_roughs"
ASSETS = ROOT / "src/main/resources/assets/skyabyss/"
GEO = ASSETS / "geo/entity/reef_scuttler.geo.json"

BONES = {
    "root": (None, [0.0, 0.0, 0.0]),
    "body": ("root", [0.0, 3.0, 0.0]),
    "leg_r": ("body", [-5.0, 3.0, 0.0]),
    "leg_l": ("body", [5.0, 3.0, 0.0]),
    "claw_r": ("body", [-4.0, 3.0, 4.0]),
    "claw_l": ("body", [4.0, 3.0, 4.0]),
}


def V(*a):
    return np.array(a, float)


def side(s):
    """作図空間の x の符号 -> geo の骨の接尾辞 (+x = r)。"""
    return "r" if s > 0 else "l"


LAMP = [(34, 96, 124), (84, 176, 206), (150, 228, 246), (214, 252, 255)]


# ====================================================================== 姿勢
def pose_faces(faces, state):
    """state: {骨: (rx, ry, rz)} アニメのファイルの値 (度)。作図空間の面を返す。位置の移動は省く。"""
    out = []
    for f in faces:
        g = copy.copy(f)
        Rs = []
        b = f.bone
        while b is not None and b != "root":
            Rs.append(rot_geo(*state.get(b, (0, 0, 0))))
            b = BONES[b][0]
        pts = []
        for p in f.verts:
            q = flipxz(p)
            b = f.bone
            while b is not None and b != "root":
                parent, pv = BONES[b]
                pv = np.array(pv, float)
                q = rot_geo(*state.get(b, (0, 0, 0))) @ (q - pv) + pv
                b = parent
            pts.append(flipxz(q))
        g.verts = np.array(pts)
        n = flipxz(f.normal)
        for R in Rs:
            n = R @ n
        g.normal = nrm(flipxz(n))
        out.append(g)
    return out


# ---- アニメの評価 (animation.json)
ANIM = json.loads((ASSETS / "animations/entity/reef_scuttler.animation.json").read_text(encoding="utf-8"))["animations"]


def _ev(x, t):
    if isinstance(x, (int, float)):
        return float(x)
    sd = lambda a: math.sin(math.radians(a))
    e = str(x).replace("math.sin(", "sd(").replace("math.abs(", "abs(").replace("query.anim_time", "t")
    return float(eval(e, {"__builtins__": {}}, {"sd": sd, "t": t, "abs": abs}))


def channel(ch, t):
    if isinstance(ch, list):
        return [_ev(x, t) for x in ch]
    ks = sorted(((float(k), v["vector"] if isinstance(v, dict) else v) for k, v in ch.items()))
    if t <= ks[0][0]:
        return [_ev(x, t) for x in ks[0][1]]
    for (t0, v0), (t1, v1) in zip(ks[:-1], ks[1:]):
        if t0 <= t <= t1:
            k = (t - t0) / max(t1 - t0, 1e-9)
            return [_ev(a, t) * (1 - k) + _ev(b, t) * k for a, b in zip(v0, v1)]
    return [_ev(x, t) for x in ks[-1][1]]


def anim_pose(name, t):
    out = {}
    for b, chs in ANIM["animation.reef_scuttler." + name]["bones"].items():
        if "rotation" in chs:
            out[b] = tuple(channel(chs["rotation"], t))
    return out


# ====================================================================== 部品
def carapace_ring(y, hw, front, back, corner=0.78, cz=0.0, pinch=1.0, fc=0.55):
    """甲羅の輪 (8 角形)。頂点 0 = 前の真ん中 (-Z)、2 = +x 側の横、4 = 後ろ。
    corner: 斜め前の頂点の x (hw に対する割合)、fc: 斜め前の頂点の z (front に対する割合)。"""
    P = []
    P.append(V(0, y, cz - front))
    P.append(V(hw * corner, y, cz - front * fc))
    P.append(V(hw, y, cz + 0.0))
    P.append(V(hw * 0.78 * pinch, y, cz + back * 0.72))
    P.append(V(0, y, cz + back))
    P.append(V(-hw * 0.78 * pinch, y, cz + back * 0.72))
    P.append(V(-hw, y, cz + 0.0))
    P.append(V(-hw * corner, y, cz - front * fc))
    return P


def leg_chain(bone, mat, pts, ra, sides=4, hint=(0, 0, 1), tip=True, mats=None):
    ra = list(ra)
    if tip:
        ra[-1] = 0.0
    return tube(bone, mat, pts, ra, ra, sides=sides, hint=hint, phase=45.0, mats=mats)


def barnacle(bone, mat, base, nrm_dir, h=1.3, r=0.9):
    return spike(bone, mat, base, nrm_dir, h, r, sides=3, rings=1)


# ====================================================================== 絵
def paint_all(faces, size, P, N, M, pal):
    img, glow = RC.paint(faces, size, P, N, M, pal, ())
    D, K, LU, LV, CW, CH, INS = edge_fields(faces, size)
    yy, xx = np.mgrid[0:size, 0:size]
    fi = np.where(M >= 0, M, 0)
    owned = M >= 0
    mats = np.array([f.mat for f in faces])[fi]
    bay = BAYER[yy % 4, xx % 4] - 0.5
    lit = N @ LIGHT
    lamp = (mats == "lamp") & owned
    if lamp.any():
        edge = (D < 1.05) & INS
        v = 0.6 + 2.0 * (lit * 0.5 + 0.5) + 0.8 * bay + 0.4 * (edge & (K > 24)) - 0.6 * (edge & (K < -24))
        li = np.clip(np.floor(v).astype(int), 0, 3)
        lc = np.array(pal.get("lamp", LAMP))[li]
        img[..., :3] = np.where(lamp[..., None], lc.astype(np.uint8), img[..., :3])
        glow = glow | lamp
        img[..., :3] = fill_outside(img[..., :3], faces, INS, M)
        gl3 = fill_outside(np.stack([glow] * 3, -1).astype(np.uint8), faces, INS, M)[..., 0] > 0
        glow = gl3 & owned
    return img, glow


def build_option(key, build, pal, hint=128):
    faces = build()
    d = OUT / "reef_scuttler" / key
    d.mkdir(parents=True, exist_ok=True)
    size, P, N, M, L = build_atlas(faces, hint, 0)
    atlas, glow = paint_all(faces, size, P, N, M, pal)
    Image.fromarray(atlas, "RGBA").save(d / f"reef_scuttler_{key}_atlas.png")
    gm = np.zeros((size, size, 4), np.uint8)
    gm[glow] = (255, 255, 255, 255)
    Image.fromarray(gm, "RGBA").save(d / f"reef_scuttler_{key}_glowmask.png")
    data = RC.export_mesh(faces, size, d / f"reef_scuttler_{key}_mesh.json", f"reef_scuttler_{key}",
                          f"reef_scuttler_{key}_atlas.png", BONES,
                          {"glowmask": f"reef_scuttler_{key}_glowmask.png",
                           "note": "geo space (root carries rotation [0,180,0]); vertices are absolute like geo cubes. "
                                   "bones: body / leg_r / leg_l / claw_r / claw_l, same names, hierarchy and pivots as the geo"})
    return dict(key=key, faces=faces, size=size, atlas=atlas, glow=glow, data=data)
