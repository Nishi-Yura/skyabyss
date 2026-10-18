"""鯨骨の番兵長 (whalebone_captain) 低ポリ 3 案の共通部品。src には触らない。

- BONES: ゲーム内の geo (whalebone_captain.geo.json) から読んだ 20 骨 (名前・親・pivot)。
- 形の部品: rrect / rloft / fbox / cbox / fin / limb ... (lpkit のプリミティブの上に載せた薄い道具)
- paint: 面ごとのドット絵 (パレットと材質は案ごとに渡す)
- ポーズ: アニメーションの JSON の角度 → 図の角度 (captain_v4.py の to_json の逆) で骨の変換を掛ける
- 鉾は受け口 socket_main の pivot を原点とする別メッシュ (アイテムの 3D と同じ寸法・向き。1.8 倍込み)
"""
from __future__ import annotations

import copy
import json
import math
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[3]
sys.path.insert(0, str(HERE.parent))
sys.path.insert(0, str(HERE.parents[1]))
from lpkit import *  # noqa
import lpkit  # noqa
import voxel  # noqa
from bone_sentinel_lp import BAYER, LIGHT, hash1, vnoise, export_mesh  # noqa

GAME = ROOT / "src/main/resources/assets/skyabyss"
OUT = ROOT / "docs/review/lowpoly_roughs"


def V(*a):
    return np.array(a, float)


def _load_bones():
    g = json.loads((GAME / "geo/entity/whalebone_captain.geo.json").read_text(encoding="utf-8"))
    out = {}
    for b in g["minecraft:geometry"][0]["bones"]:
        out[b["name"]] = (b.get("parent"), [float(x) for x in b["pivot"]])
    return out


BONES = _load_bones()
assert len(BONES) == 20, len(BONES)
SOCK_R = np.array(BONES["socket_main"][1])
SOCK_L = np.array(BONES["socket_off"][1])


# ====================================================================== 形の部品

def rrect(c, u, v, w, h, cut=0.0):
    """中心 c、軸 u, v の矩形の輪 (4 点)。cut > 0 なら角を落とした 8 点 (cut は短辺の半分に対する割合)。"""
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


def fbox(bone, mat, c, size, R=None, taper=(1.0, 1.0), shift=(0.0, 0.0), cut=0.0, caps=(True, True), mats=None):
    """傾けられる箱 (上へ向けて taper で細る錐台)。size = (x, y, z)。R は中心まわりの回転。"""
    sx, sy, sz = size
    R = np.eye(3) if R is None else R
    ex, ey, ez = (R @ np.array(a) for a in ((1, 0, 0), (0, 1, 0), (0, 0, 1)))
    c = np.asarray(c, float)
    r0 = rrect(c - ey * sy / 2, ex, ez, sx, sz, cut)
    r1 = rrect(c + ey * sy / 2 + ex * shift[0] + ez * shift[1], ex, ez, sx * taper[0], sz * taper[1], cut)
    return rloft(bone, mat, [r0, r1], caps, mats)


def cbox(bone, mat, c, size, R=None, taper=0.78, cut=0.45, caps=(True, True)):
    """面取りした箱: 下 (taper) - 中 (全幅) - 上 (taper) の 3 つの八角形の輪。拳・兜・肩の玉など。"""
    sx, sy, sz = size
    R = np.eye(3) if R is None else R
    ex, ey, ez = (R @ np.array(a) for a in ((1, 0, 0), (0, 1, 0), (0, 0, 1)))
    c = np.asarray(c, float)
    rings = [rrect(c - ey * sy / 2, ex, ez, sx * taper, sz * taper, cut),
             rrect(c - ey * sy * 0.18, ex, ez, sx, sz, cut),
             rrect(c + ey * sy * 0.18, ex, ez, sx, sz, cut),
             rrect(c + ey * sy / 2, ex, ez, sx * taper, sz * taper, cut)]
    return rloft(bone, mat, rings, caps)


def rings_along(bone, mat, secs, axis_u, axis_v, caps=(True, True), cut=0.0, mats=None):
    """断面の列: secs = [(center, w, h), ...]。断面は u, v の平面。"""
    return rloft(bone, mat, [rrect(c, axis_u, axis_v, w, h, cut) for c, w, h in secs], caps, mats)


def fin(bone, mat, pts_yz, x, thick, R=None, center=None, tip_thin=0.45):
    """YZ 平面の多角形 (z, y) を x の厚み thick で押し出した板 (先へ行くほど薄くなる)。
    R は center まわりの回転 (省略時は x を中心に回さない)。"""
    pts = [np.array(p, float) for p in pts_yz]
    ymin = min(p[1] for p in pts)
    ymax = max(p[1] for p in pts)

    def mk(side):
        out = []
        for z, y in pts:
            k = (y - ymin) / max(ymax - ymin, 1e-6)
            tt = thick / 2 * (1 - (1 - tip_thin) * k)
            out.append(np.array([x + side * tt, y, z]))
        return out
    a, b = mk(-1), mk(1)
    rings = [a, b]
    fs = rloft(bone, mat, rings, (True, True))
    if R is not None:
        cen = np.array(center if center is not None else (x, 0, 0), float)
        transform(fs, R, cen - R @ cen)
    return fs


def ringp(y, rx, zf, zb, zc=0.0, n=8, phase=22.5, dz=0.0, dzk=()):
    """正面が頂点でなく面になる n 角形の輪 (ring() と同じ向き。phase 度ずらす)。dzk の頂点だけ z を dz ずらす。"""
    pts = []
    for k in range(n):
        a = math.radians(phase + 360.0 * k / n)
        sn, c = math.sin(a), math.cos(a)
        z = zc + (zf * c if c >= 0 else zb * -c)
        if k in dzk:
            z += dz
        pts.append(np.array([rx * sn, y, z]))
    return pts


def extrude(bone, mat, pts, d, taper=1.0, caps=(True, True)):
    """平面の多角形 pts (3D の点列) を d だけ押し出した板。taper で押し出した側が縮む。"""
    pts = [np.asarray(p, float) for p in pts]
    c = np.mean(pts, 0)
    d = np.asarray(d, float)
    r0 = [p - d / 2 for p in pts]
    r1 = [c + (p - c) * taper + d / 2 for p in pts]
    return rloft(bone, mat, [r0, r1], caps)


def mirror(fn, **kw):
    """左右 (s = +1: 左 ... x が正) の両側を作る。"""
    out = []
    for s in (1, -1):
        out += fn(s, **kw)
    return out


def lim(bone, mat, path, ra, rb=None, sides=6, hint=(1, 0, 0), phase=0.0, caps=(True, True)):
    return tube(bone, mat, path, ra, rb, sides=sides, hint=hint, phase=phase, caps=caps)


def torso_pt(rings, y, ang):
    """リング列 (ring() が作る点の列) 上の、高さ y・角度 ang (度。0 が正面、90 が +x) の点。"""
    ys = [r[0][1] for r in rings]
    j = int(np.clip(np.searchsorted(ys, y) - 1, 0, len(rings) - 2))
    t = (y - ys[j]) / max(ys[j + 1] - ys[j], 1e-9)
    n = len(rings[0])
    f = ang / (360.0 / n)
    k0 = int(math.floor(f)) % n
    k1 = (k0 + 1) % n
    u = f - math.floor(f)
    a = rings[j][k0] * (1 - u) + rings[j][k1] * u
    b = rings[j + 1][k0] * (1 - u) + rings[j + 1][k1] * u
    return a * (1 - t) + b * t


# ====================================================================== ポーズ

def fig(j):
    """アニメーション JSON の角度 → 図 (voxel) の角度。captain_v4.to_json の逆: (-x, y, -z)。"""
    return (-j[0], j[1], -j[2])


def pose_from_json(d):
    return {b: fig(v) for b, v in d.items()}


def bone_xform(bone, pose, cache=None):
    cache = {} if cache is None else cache
    if bone in cache:
        return cache[bone]
    parent, pv = BONES[bone]
    pv = np.array(pv, float)
    R = voxel._rot(*pose.get(bone, (0, 0, 0)))
    Rm, t = R, pv - R @ pv
    if parent:
        PR, Pt = bone_xform(parent, pose, cache)
        Rm, t = PR @ Rm, PR @ t + Pt
    cache[bone] = (Rm, t)
    return cache[bone]


def pose_faces(faces, pose, gear_bone=None, gear_off=None):
    """休めの姿勢の面 → pose のときの面。gear_bone を与えると、頂点を (+gear_off) して受け口の骨で動かす。"""
    cache = {}
    out = []
    for f in faces:
        g = copy.copy(f)
        b = gear_bone or f.bone
        R, t = bone_xform(b, pose, cache)
        v = f.verts + (gear_off if gear_off is not None else 0)
        g.verts = v @ R.T + t
        g.normal = R @ f.normal
        out.append(g)
    return out


# ANIM: アニメーション (animations/entity/whalebone_captain.animation.json) の key pose
def load_anim():
    a = json.loads((GAME / "animations/entity/whalebone_captain.animation.json").read_text(encoding="utf-8"))["animations"]
    return a


def anim_pose(name, t):
    """アニメーションの時刻 t の姿勢 (JSON 角度の辞書)。molang の式は使わず、キーだけの動作 (sweep/slam)。"""
    a = load_anim()["animation.whalebone_captain." + name]["bones"]
    out = {}
    for b, d in a.items():
        rot = d["rotation"]
        if isinstance(rot, dict):
            ks = sorted((float(k), v) for k, v in rot.items())
            if t <= ks[0][0]:
                out[b] = tuple(ks[0][1])
            else:
                out[b] = tuple(ks[-1][1])
                for (t0, v0), (t1, v1) in zip(ks, ks[1:]):
                    if t0 <= t <= t1:
                        k = (t - t0) / (t1 - t0)
                        out[b] = tuple((1 - k) * np.array(v0) + k * np.array(v1))
                        break
    return out


IDLE_JSON = {"body": (1.5, 0, 0), "armR": (-13.67, 3.45, 3.31), "forearmR": (-70.18, 0.16, -0.07),
             "handR": (-11.97, 1.74, -2.51), "socket_main": (5.76, 1.27, -1.11), "armL": (0, 0, -4)}


# ====================================================================== 絵 (パレットと材質は案ごと)

def ramp(*cols):
    return np.array(cols, float)


GEM = ramp((40, 140, 170), (95, 232, 255), (210, 250, 255))
PUPIL = ramp((120, 205, 232), (200, 246, 255))
DARK = ramp((8, 10, 16), (18, 20, 28), (32, 34, 44))


def paint(faces, size, P, N, M, L, pal, extras=None):
    """pal: STONE(5) BONE(6) MOSS(4) TRIM(4) WOOD(5) + スタイルの係数。材質:
    stone bone tooth moss(使わない) trim wood dark gem visor。戻り: (RGBA, glow bool)"""
    extras = extras or {}
    STONE, BONE, MOSS, TRIM, WOOD = pal["STONE"], pal["BONE"], pal["MOSS"], pal["TRIM"], pal["WOOD"]
    D, K, LU, LV, CW, CH, INS = edge_fields(faces, size)
    H = W = size
    yy, xx = np.mgrid[0:H, 0:W]
    fi = np.where(M >= 0, M, 0)
    owned = M >= 0
    mats = np.array([f.mat for f in faces])[fi]
    bones = np.array([f.bone for f in faces])[fi]
    bay = BAYER[yy % 4, xx % 4] - 0.5
    ny = N[..., 1]
    lit = N @ LIGHT
    area = (CW * CH).astype(float)
    edge = (D < 1.05) & INS
    convex = edge & (K > 24)
    concave = edge & (K < -24)
    near_concave = (D < 2.4) & (K < -24) & INS
    sg = pal.get("seed", 0)

    # ---------------------------------------------------------------- 骨・歯
    n_lo = vnoise(P * 0.16 + 3 + sg, 1)
    v = pal.get("bone_base", 0.72) + 0.13 * lit + 0.09 * (n_lo - 0.5) + 0.05 * bay
    streak = (hash1(LU * 7 + fi * 13, 3) < 0.15) & (area > 14) & (mats != "tooth")
    v = v - 0.09 * streak
    idx = np.floor(v * 6).astype(int)
    idx = idx + (convex & (lit > 0)) - (convex & (lit <= 0)) - concave
    idx = np.maximum(idx, 1)
    hf = hash1(fi + sg * 3, 5)
    nverts = np.array([len(f.verts) for f in faces])[fi]
    crack_on = (hf < pal.get("crack", 0.24)) & (area > 22) & (mats != "tooth") & (nverts <= 4)
    c0 = (hash1(fi, 6) * np.maximum(CW - 2, 1)).astype(int) + 1
    v0 = (hash1(fi, 7) * 0.35 * CH).astype(int)
    v1 = v0 + (CH * (0.35 + 0.35 * hash1(fi, 8))).astype(int)
    zig = np.round(0.62 * np.sin(LV * 0.7 + fi * 1.7)).astype(int)
    seg = INS & (LV >= v0) & (LV <= v1)
    idx = np.where(crack_on & seg & (LU == c0 + zig), 1, idx)
    idx = np.clip(idx, 0, 5)
    bonecol = BONE[idx].astype(float)
    ti = np.clip(np.floor(3.4 + 2.0 * (lit * 0.5 + 0.5) + 0.6 * convex + 0.8 * bay).astype(int), 2, 5)
    ti = np.where(concave, 2, ti)
    toothcol = BONE[ti].astype(float)

    # ---------------------------------------------------------------- 石の板
    sv = pal.get("stone_base", 0.46) + 0.36 * lit + 0.10 * (vnoise(P * 0.35 + 5 + sg, 8) - 0.5) + 0.07 * bay
    si = np.floor(sv * 5).astype(int) + convex - 2 * concave - 1
    # 板の内側の縁取り (彫り込みの線)
    rim_on = (area > pal.get("rim_area", 70)) & (hash1(fi, 21) < pal.get("rim_p", 0.75))
    rim = rim_on & INS & (D >= 2.0) & (D < 3.0) & (nverts <= 4)
    si = np.where(rim, si - 1, si)
    # 目地・ひびの筋
    sc = (hash1(fi, 11) < pal.get("seam_p", 0.5)) & (area > 36) & INS & (np.abs((LU + LV * 0.5) % 9 - 4) < 0.5) \
         & (hash1(fi, 12) < 0.5)
    si = np.where(sc, 0, si)
    # 鋲: 大きな板の四隅
    rv_ = (area > 60) & (hash1(fi, 31) < pal.get("rivet_p", 0.55)) & (nverts <= 4)
    cx_ = (LU == 2) | (LU == CW - 3)
    cy_ = (LV == 2) | (LV == CH - 3)
    rivet = rv_ & INS & cx_ & cy_ & (CW > 6) & (CH > 6)
    rivet_sh = rv_ & INS & ((LU == 3) | (LU == CW - 2)) & ((LV == 3) | (LV == CH - 2)) & (CW > 6) & (CH > 6)
    si = np.where(rivet, si + 2, si)
    si = np.where(rivet_sh & ~rivet, si - 1, si)
    si = np.clip(si, 0, 4)
    stonecol = STONE[si].astype(float)

    # ---------------------------------------------------------------- 苔 (上向き・凹みの縁・下の方)
    mn = 0.62 * vnoise(P * 0.21 + 21 + sg, 4) + 0.38 * vnoise(P * 0.52 + 7, 9)
    mscore = mn + 0.34 * np.clip(ny, 0, 1) + 0.36 * near_concave + 0.10 * (1 - np.clip(P[..., 1] / 34, 0, 1))
    thr = pal.get("moss_thr", 0.92)
    moss = (mscore + 0.06 * bay > thr) & (ny > -0.2) & ((mats == "stone") | (mats == "bone"))
    depth = np.clip((mscore - thr) * 7 + 0.4 * lit + 0.6 * bay, 0, 3.99)
    midx = np.clip(np.floor(depth).astype(int), 0, 3)
    midx = np.where(convex & (ny > 0.3), 3, midx)
    mosscol = MOSS[midx].astype(float)

    # ---------------------------------------------------------------- 革・鉄・木
    tv = 0.45 + 0.35 * lit + 0.08 * bay + 0.1 * (vnoise(P * 0.5 + 3, 2) - 0.5)
    trimcol = TRIM[np.clip(np.floor(tv * 4).astype(int) + convex - concave, 0, 3)].astype(float)
    wv = 0.50 + 0.32 * lit + 0.06 * bay
    wband = (np.abs(((P[..., 2] * 0.5 + 40) % 3.0) - 1.5) < 0.3)  # 柄の巻き (縞)
    woodcol = WOOD[np.clip(np.floor(wv * 5).astype(int) + convex - concave - wband, 0, 4)].astype(float)
    dk = np.clip(np.floor(0.8 + 1.2 * hash1(LU * 31 + LV * 17 + fi, 2) + 0.4 * bay).astype(int), 0, 2)
    darkcol = DARK[dk].astype(float)
    gemi = np.clip(np.floor(1.0 + 1.8 * (lit * 0.5 + 0.5) + 0.8 * convex - 0.9 * concave + 0.4 * bay).astype(int), 0, 2)
    gemcol = GEM[gemi].astype(float)

    col = bonecol
    col = np.where(((mats == "tooth"))[..., None], toothcol, col)
    col = np.where(((mats == "stone"))[..., None], stonecol, col)
    col = np.where((((mats == "stone") | (mats == "bone")) & moss)[..., None], mosscol, col)
    col = np.where((mats == "trim")[..., None], trimcol, col)
    col = np.where((mats == "wood")[..., None], woodcol, col)
    col = np.where(((mats == "dark") | (mats == "visor"))[..., None], darkcol, col)
    col = np.where((mats == "gem")[..., None], gemcol, col)
    glow = (mats == "gem") & owned

    # ---------------------------------------------------------------- 目 (visor の面に、正面から見た形で描く)
    eye_ok = np.isin(mats, extras.get("eye_mats", ("visor",)))
    vis = eye_ok & owned & (N[..., 2] < extras.get("eye_nz", 0.5))
    for e in extras.get("eyes", []):
        for sgn in (1, -1):
            dx = (P[..., 0] * sgn - e["x"])
            dy = P[..., 1] - e["y"]
            dyy = dy - e.get("tilt", 0.3) * dx
            r = np.sqrt((dx / e["a"]) ** 2 + (dyy / e["b"]) ** 2)
            sock = vis & (r + 0.06 * bay < 1.0)
            dkv = np.clip(np.floor(2.9 - (1.0 - r) * 4.0 + 0.4 * bay).astype(int), 0, 2)
            col = np.where(sock[..., None], DARK[dkv], col)
            pr = np.sqrt(((dx - 0.1 * e["a"]) / (e["a"] * 0.62)) ** 2 + (dyy / (e["b"] * 0.5)) ** 2)
            pup = vis & (pr + 0.05 * bay < 1.0)
            col = np.where(pup[..., None], np.where(pr[..., None] < 0.5, PUPIL[1], PUPIL[0]), col)
            glow |= pup
    # 追加の描き込み (案ごとの手描き)
    if extras.get("extra_paint"):
        col, glow = extras["extra_paint"](dict(faces=faces, P=P, N=N, M=M, L=L, fi=fi, mats=mats, bones=bones,
                                              INS=INS, D=D, K=K, LU=LU, LV=LV, CW=CW, CH=CH, bay=bay, lit=lit,
                                              convex=convex, concave=concave, owned=owned, area=area), col, glow)

    col = np.clip(col, 0, 255)
    img = np.zeros((H, W, 4), np.uint8)
    img[..., :3] = col.astype(np.uint8)
    img[..., 3] = np.where(owned, 255, 0)
    img[..., :3] = fill_outside(img[..., :3], faces, INS, M)
    gl = fill_outside(np.stack([glow] * 3, -1).astype(np.uint8), faces, INS, M)[..., 0] > 0
    return img, gl & owned


# ====================================================================== 組み立てと書き出し

def build_option(mod, size_hint=128):
    body, pike, extras = mod.build()
    size, P, N, M, L = build_atlas(body, size_hint, 0)
    atlas, glow = paint(body, size, P, N, M, L, mod.PAL, extras)
    psize, pP, pN, pM, pL = build_atlas(pike, 64, 0)
    patlas, pglow = paint(pike, psize, pP, pN, pM, pL, mod.PAL, mod.PIKE_EXTRAS if hasattr(mod, "PIKE_EXTRAS") else {})
    return dict(body=body, pike=pike, size=size, atlas=atlas, glow=glow, psize=psize, patlas=patlas, pglow=pglow,
                extras=extras, mod=mod)


def export_option(r, key):
    mod = r["mod"]
    d = OUT / "whalebone_captain" / key
    d.mkdir(parents=True, exist_ok=True)
    Image.fromarray(r["atlas"], "RGBA").save(d / "whalebone_captain_lowpoly_atlas.png")
    gm = np.zeros((r["size"], r["size"], 4), np.uint8)
    gm[r["glow"]] = (255, 255, 255, 255)
    Image.fromarray(gm, "RGBA").save(d / "whalebone_captain_lowpoly_glowmask.png")
    data = export_mesh(r["body"], r["size"], d / "whalebone_captain_lowpoly_mesh.json", "whalebone_captain_lowpoly",
                       "whalebone_captain_lowpoly_atlas.png",
                       {"glowmask": "whalebone_captain_lowpoly_glowmask.png", "option": key.upper(),
                        "note": "20 bones = in-game whalebone_captain.geo.json; socket_main/socket_off are empty bones"},
                       bone_defs=BONES)
    Image.fromarray(r["patlas"], "RGBA").save(d / "captain_pike_atlas.png")
    pg = np.zeros((r["psize"], r["psize"], 4), np.uint8)
    pg[r["pglow"]] = (255, 255, 255, 255)
    if r["pglow"].any():
        Image.fromarray(pg, "RGBA").save(d / "captain_pike_glowmask.png")
    pdata = export_mesh(r["pike"], r["psize"], d / "captain_pike_mesh.json", "captain_pike", "captain_pike_atlas.png",
                        {"attach": {"socket_bone": "socket_main", "socket_pivot": [float(x) for x in SOCK_R],
                                    "note": "vertices are in socket_main's local space (origin = socket pivot), "
                                            "already at the 1.8x mob scale; shaft along z, tip toward -z, blade fluke toward -y"},
                         **({"glowmask": "captain_pike_glowmask.png"} if r["pglow"].any() else {})},
                        bone_defs={"pike": (None, [0.0, 0.0, 0.0])})
    r["data"], r["pdata"] = data, pdata
    return data, pdata
