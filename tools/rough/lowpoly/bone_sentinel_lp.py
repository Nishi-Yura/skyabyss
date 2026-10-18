"""骨の番兵 (bone_sentinel) の低ポリ版。ラフ。src には触らない。

出力 (docs/review/lowpoly_roughs/):
  bone_sentinel_lowpoly_mesh.json        骨 → 頂点・UV・三角形 (本体)
  bone_sentinel_lowpoly_atlas.png        本体のテクスチャ (+ _glowmask.png)
  bone_sentinel_shield_mesh.json         盾 (socket_off の pivot が原点)
  bone_sentinel_shield_atlas.png         盾のテクスチャ
"""
from __future__ import annotations

import json
import math
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[2]
sys.path.insert(0, str(HERE))
sys.path.insert(0, str(ROOT / "tools/rough"))
from lpkit import *  # noqa
from bone_sentinel_model import BONES, build_model  # noqa
import voxel  # noqa

INSET = 0.02   # テクセル
OUT = ROOT / "docs/review/lowpoly_roughs"
A = ROOT / "src/main/resources/assets/skyabyss/"


# ====================================================================== 絵 (手で打ったドット絵の風合い)

def hash1(a, salt=0):
    """整数配列 → [0,1) の決まった乱数。"""
    q = np.sin(np.asarray(a, float) * 12.9898 + salt * 78.233) * 43758.5453
    return q - np.floor(q)


def hash3(p, seed=0):
    q = (p * np.array([127.1, 311.7, 74.7]) + seed * 17.13).sum(-1)
    return np.modf(np.sin(q) * 43758.5453)[0] % 1.0


def vnoise(p, seed=0):
    i = np.floor(p)
    f = p - i
    f = f * f * (3 - 2 * f)
    out = 0
    for dx in (0, 1):
        for dy in (0, 1):
            for dz in (0, 1):
                w = (f[..., 0] if dx else 1 - f[..., 0]) * (f[..., 1] if dy else 1 - f[..., 1]) * \
                    (f[..., 2] if dz else 1 - f[..., 2])
                out = out + w * hash3(i + np.array([dx, dy, dz]), seed)
    return out


BAYER = np.array([[0, 8, 2, 10], [12, 4, 14, 6], [3, 11, 1, 9], [15, 7, 13, 5]]) / 16.0
BONE = np.array([(44, 38, 40), (88, 76, 64), (134, 119, 96), (176, 160, 128), (212, 198, 164), (238, 229, 203)])
MOSS = np.array([(24, 40, 28), (44, 66, 36), (72, 100, 48), (110, 140, 70)])
STONE = np.array([(36, 40, 50), (62, 68, 80), (92, 100, 112), (122, 130, 142), (154, 162, 172)])
DARK = np.array([(10, 10, 18), (20, 20, 30), (34, 34, 46)])
PUPIL = np.array([(120, 205, 232), (200, 246, 255)])
LIGHT = np.array([-0.35, 0.85, -0.4])
LIGHT = LIGHT / np.linalg.norm(LIGHT)


def paint(faces, size, P, N, M, extras=None):
    extras = extras or {}
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

    # ---------------------------------------------------------------- 骨・歯
    # 面ごとの明るさはゲームの陰影が付くので弱く。ゆるい染みと、面ごとの縦の繊維、縁の欠けで手描きらしさを出す
    n_lo = vnoise(P * 0.16 + 3, 1)
    ao = 0.10 * np.clip((14 - P[..., 1]) / 14, 0, 1)                # 足元ほど少し汚れる
    v = 0.72 + 0.13 * lit + 0.09 * (n_lo - 0.5) - ao + 0.05 * bay
    streak = (hash1(LU * 7 + fi * 13, 3) < 0.15) & (area > 14) & (mats != "tooth")
    v = v - 0.09 * streak
    idx = np.floor(v * 6).astype(int)
    hi = convex & (lit > 0.0)
    lo = convex & (lit <= 0.0)
    idx = idx + hi - lo
    idx = idx - concave
    idx = np.maximum(idx, 1)
    # ひび: 面の中を骨の長さの向き (島の v 方向) にくねらせて入れる。形に沿って 1 枚の面の中で止まる
    hf = hash1(fi, 5)
    nverts = np.array([len(f.verts) for f in faces])[fi]
    crack_on = (hf < 0.30) & (area > 30) & (mats != "tooth") & (nverts <= 4)
    c0 = (hash1(fi, 6) * np.maximum(CW - 2, 1)).astype(int) + 1
    v0 = (hash1(fi, 7) * 0.35 * CH).astype(int)
    v1 = v0 + (CH * (0.4 + 0.4 * hash1(fi, 8))).astype(int)
    zig = np.round(1.1 * np.sin(LV * 0.8 + fi * 1.7)).astype(int)
    seg = INS & (LV >= v0) & (LV <= v1)
    on_crack = crack_on & seg & (LU == c0 + zig)
    idx = np.where(on_crack, 1, idx)
    nb = crack_on & seg & (LU == c0 + zig + 1) & (lit > -0.2)
    idx = np.where(nb & ~on_crack, np.minimum(idx + 1, 5), idx)
    idx = np.clip(idx, 0, 5)
    col = BONE[idx].astype(float)

    # 苔: 上を向いた面・凹みの縁・下の方に溜まる。大きなかたまりで、縁だけ画素をちらす
    mn = 0.62 * vnoise(P * 0.21 + 21, 4) + 0.38 * vnoise(P * 0.52 + 7, 9)
    mscore = mn + 0.34 * np.clip(ny, 0, 1) + 0.36 * near_concave + 0.12 * (1 - np.clip(P[..., 1] / 34, 0, 1))
    thr = 0.90
    moss = (mscore + 0.06 * bay > thr) & (ny > -0.25) & (mats != "tooth") & (mats != "stone")
    depth = np.clip(((mscore - thr) * 7 + 0.4 * lit + 0.6 * bay), 0, 3.99)
    midx = np.clip(np.floor(depth).astype(int), 0, 3)
    midx = np.where(convex & (ny > 0.3), 3, midx)
    col = np.where(moss[..., None], MOSS[midx], col)

    # 歯: 骨の明るい階調 (先は白く)
    ti = np.clip(np.floor(3.4 + 2.0 * (lit * 0.5 + 0.5) + 0.6 * convex + 0.8 * bay).astype(int), 2, 5)
    ti = np.where(concave, 2, ti)
    col = np.where((mats == "tooth")[..., None], BONE[ti], col)

    # 口の中: 暗い穴
    mi = np.clip(np.floor(0.8 + 1.2 * (hash1(LU * 31 + LV * 17 + fi, 2)) + 0.4 * bay).astype(int), 0, 2)
    col = np.where((mats == "mouth")[..., None], DARK[mi], col)

    # 石: 面ごとの平らな明るさ + 欠けた縁 + まばらなひび + 上面の苔
    sv = 0.46 + 0.36 * lit + 0.10 * (vnoise(P * 0.35 + 5, 8) - 0.5) + 0.07 * bay
    si = np.floor(sv * 5).astype(int) + convex - 2 * concave
    # 石の目地: 面を斜めに横切る一本の筋
    sc = (hash1(fi, 11) < 0.55) & (area > 30) & INS & (np.abs((LU + LV * 0.5) % 9 - 4) < 0.5) & (hash1(fi, 12) < 0.5)
    si = np.where(sc, 0, si)
    si = np.clip(si, 0, 4)
    col = np.where((mats == "stone")[..., None], STONE[si], col)
    smoss = (mscore + 0.06 * bay > 0.99) & (ny > 0.15) & (mats == "stone")
    col = np.where(smoss[..., None], MOSS[np.clip(np.floor((mscore - 0.99) * 8 + 0.6 * lit + 0.5).astype(int), 0, 3)], col)

    # ---------------------------------------------------------------- 盾 (局所座標 = P。x 外、y 上、z 横)
    ly, lz = P[..., 1], P[..., 2]
    wid = np.interp(ly, [-9.5, -4, 2.5, 8], [0.0, 3.3, 4.5, 4.1])
    rim = (np.abs(lz) > wid - 1.0) | (ly > 8.0 - 1.0)
    pv = 0.54 + 0.30 * lit + 0.10 * (vnoise(P * 0.4 + 33, 6) - 0.5) + 0.05 * bay
    groove = ((np.abs(ly - 0.5) < 0.5) | (np.abs(ly + 6.0) < 0.5)) & (np.abs(lz) < wid - 1.0) & (np.abs(lz) > 1.5)
    pidx = np.floor(pv * 6).astype(int) + convex - 2 * concave - 2 * groove
    pcol = BONE[np.clip(pidx, 0, 5)].astype(float)
    rv = 0.46 + 0.34 * lit + 0.07 * bay + 0.1 * (vnoise(P * 0.5 + 4, 7) - 0.5)
    ri = np.clip(np.floor(rv * 5).astype(int) + convex - 2 * concave, 0, 4)
    shv = np.where(rim[..., None], STONE[ri], pcol)
    shmoss = (mscore + 0.06 * bay > 0.93) & (ny > -0.1) & (ly > -5)
    shv = np.where(shmoss[..., None], MOSS[np.clip(np.floor((mscore - 0.93) * 8 + 0.8 * lit + 0.6).astype(int), 0, 3)], shv)
    col = np.where((mats == "shield")[..., None], shv, col)
    bv = 0.36 + 0.22 * lit + 0.08 * (vnoise(P * 0.5, 2) - 0.5) + 0.05 * bay
    col = np.where((mats == "shield_back")[..., None], STONE[np.clip(np.floor(bv * 5).astype(int) , 0, 4)], col)
    ev = 0.38 + 0.3 * lit + 0.06 * bay
    col = np.where((mats == "shield_edge")[..., None], STONE[np.clip(np.floor(ev * 5).astype(int) + convex, 0, 4)], col)

    # ---------------------------------------------------------------- 顔: 眼窩の闇と光る瞳、鼻の穴 (正面から見た形で描く)
    glow = np.zeros((H, W), bool)
    hd = (bones == "head") & (mats == "bone") & owned & (P[..., 2] < 0.0) & (N[..., 2] < 0.45)
    for c in extras.get("eyes", []):
        s = 1 if c[0] > 0 else -1
        dx = (P[..., 0] - c[0]) * s
        dy = P[..., 1] - c[1]
        dyy = dy - 0.34 * dx                                      # 目尻が上がる
        r = np.sqrt((dx / 1.7) ** 2 + (dyy / 1.12) ** 2)
        sock = hd & (r + 0.06 * bay < 1.0)
        dkv = np.clip(np.floor(2.9 - (1.0 - r) * 4.0 + 0.4 * bay).astype(int), 0, 2)
        col = np.where(sock[..., None], DARK[dkv], col)
        pr = np.sqrt(((dx - 0.2) / 0.95) ** 2 + (dyy / 0.46) ** 2)
        pup = hd & (pr + 0.05 * bay < 1.0)
        col = np.where(pup[..., None], np.where(pr[..., None] < 0.5, PUPIL[1], PUPIL[0]), col)
        glow |= pup
    if "nose" in extras:
        c = extras["nose"]
        t = np.clip((P[..., 1] - (c[1] - 1.0)) / 2.0, -1, 2)
        hw = 0.62 * (1 - t) + 0.12
        nz = hd & (np.abs(P[..., 0] - c[0]) < hw + 0.05 * bay) & (t > 0) & (t < 1.0)
        col = np.where(nz[..., None], DARK[np.clip(np.floor(1.0 + 1.2 * hash1(LU + LV * 5 + fi, 4)).astype(int), 0, 2)], col)

    col = np.clip(col, 0, 255)
    img = np.zeros((H, W, 4), np.uint8)
    img[..., :3] = col.astype(np.uint8)
    img[..., 3] = np.where(owned, 255, 0)
    img[..., :3] = fill_outside(img[..., :3], faces, INS, M)
    gl = fill_outside(np.stack([glow] * 3, -1).astype(np.uint8), faces, INS, M)[..., 0] > 0
    return img, gl & owned


# ====================================================================== 出力

def export_mesh(faces, size, path, name, texture, extra=None, bone_defs=BONES, tpu=TEX):
    bones = {b: {"vertices": [], "uvs": [], "triangles": []} for b in bone_defs}
    for f in faces:
        b = bones[f.bone]
        base = len(b["vertices"])
        cen = f.uv.mean(0)
        for p, uv in zip(f.verts, f.uv):
            d = cen - uv
            nn = np.linalg.norm(d)
            uv = uv + d / nn * min(INSET, nn * 0.4) if nn > 1e-9 else uv   # 隣の島へにじまないよう、わずかに内側へ
            b["vertices"].append([round(float(x), 4) for x in p])
            b["uvs"].append([round(float(uv[0]) / size, 5), round(float(uv[1]) / size, 5)])
        for a, c, d in f.tris():
            b["triangles"].append([base + a, base + c, base + d])
    tot = 0
    out = {}
    for k, (parent, pivot) in bone_defs.items():
        b = bones[k]
        b["triangle_count"] = len(b["triangles"])
        tot += b["triangle_count"]
        out[k] = {"parent": parent, "pivot": pivot, **b}
    data = {"format_version": 1, "name": name, "units": "1/16 block, y up, front = -Z",
            "uv": "normalized, origin top-left (v down)", "texture": texture, "texture_size": [size, size],
            "texels_per_unit": tpu, "uv_inset_texels": INSET, "triangle_count": tot}
    if extra:
        data.update(extra)
    data["bones"] = out
    Path(path).write_text(json.dumps(data, ensure_ascii=False), encoding="utf-8")
    return data


def build_all(size_hint=128):
    OUT.mkdir(parents=True, exist_ok=True)
    body, shield, extras = build_model()
    size, P, N, M, L = build_atlas(body, size_hint, 0)
    atlas, glow = paint(body, size, P, N, M, extras)
    Image.fromarray(atlas, "RGBA").save(OUT / "bone_sentinel_lowpoly_atlas.png")
    gm = np.zeros((size, size, 4), np.uint8)
    gm[glow] = (255, 255, 255, 255)
    Image.fromarray(gm, "RGBA").save(OUT / "bone_sentinel_lowpoly_glowmask.png")
    data = export_mesh(body, size, OUT / "bone_sentinel_lowpoly_mesh.json", "bone_sentinel_lowpoly",
                       "bone_sentinel_lowpoly_atlas.png",
                       {"glowmask": "bone_sentinel_lowpoly_glowmask.png",
                        "note": "jaw is merged into head (no jaw bone in the in-game geo); socket_off is an empty bone"})
    ssize, sP, sN, sM, sL = build_atlas(shield, 64)
    satlas, _ = paint(shield, ssize, sP, sN, sM, {})
    Image.fromarray(satlas, "RGBA").save(OUT / "bone_sentinel_shield_atlas.png")
    sdata = export_mesh(shield, ssize, OUT / "bone_sentinel_shield_mesh.json", "bone_sentinel_shield",
                        "bone_sentinel_shield_atlas.png",
                        {"attach": {"socket_bone": "socket_off", "socket_pivot": [float(x) for x in BONES["socket_off"][1]],
                                    "note": "vertices are in the socket bone's local space (origin = socket pivot), "
                                            "same as geo/gear/bone_sentinel_shield.geo.json; plate faces +x"}},
                        bone_defs={"shield": (None, [0.0, 0.0, 0.0])})
    return dict(body=body, shield=shield, size=size, atlas=atlas, glow=glow, data=data,
                ssize=ssize, satlas=satlas, sdata=sdata, extras=extras)


def main():
    r = build_all()
    per = {k: v["triangle_count"] for k, v in r["data"]["bones"].items()}
    print("atlas", r["size"], "tris", r["data"]["triangle_count"], per)
    print("shield atlas", r["ssize"], "tris", r["sdata"]["triangle_count"])
    return r


if __name__ == "__main__":
    main()
