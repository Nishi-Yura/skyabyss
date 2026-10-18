"""水路の顎: 3 案のテクスチャ (手で打ったドット絵の風合い) と出力。src には触らない。

出力 (docs/review/lowpoly_roughs/waterway_maw/{a,b,c}/):
  waterway_maw_lp_mesh.json / _atlas.png / _glowmask.png
"""
from __future__ import annotations

import sys
from pathlib import Path

import numpy as np
from PIL import Image

HERE = Path(__file__).resolve().parent
LP = HERE.parent
ROOT = HERE.parents[3]
sys.path.insert(0, str(LP))
sys.path.insert(0, str(HERE))
sys.path.insert(0, str(ROOT / "tools/rough"))
from lpkit import build_atlas, edge_fields, fill_outside  # noqa
from bone_sentinel_lp import BAYER, LIGHT, export_mesh, hash1, vnoise  # noqa
from maw_model import BONES, BUILDERS  # noqa

OUT = ROOT / "docs/review/lowpoly_roughs/waterway_maw"

BONE = np.array([(44, 38, 40), (88, 76, 64), (134, 119, 96), (176, 160, 128), (212, 198, 164), (238, 229, 203)])
SPEC = np.array([(170, 208, 232), (214, 238, 250)])
PUPIL = np.array([(120, 205, 232), (200, 246, 255)])


def arr(*c):
    return np.array(c)


PAL = {
    # A: 青緑の濡れた鱗、紫のひれ、象牙の針
    "a": dict(
        skin=arr((10, 22, 36), (18, 40, 62), (28, 64, 92), (42, 92, 122), (64, 124, 150), (96, 156, 176)),
        plate=arr((20, 36, 54), (34, 58, 80), (52, 84, 108), (76, 112, 134), (106, 142, 160), (140, 172, 186)),
        fin=arr((28, 16, 44), (50, 28, 74), (76, 44, 106), (106, 64, 138), (138, 90, 168)),
        mouth=arr((40, 10, 24), (74, 18, 38), (110, 30, 54), (146, 50, 74)),
        belly=arr((50, 76, 96), (78, 108, 128), (110, 140, 158), (146, 174, 188)),
        tooth=BONE, bone=BONE, scale=2.0, scales=True, warts=False),
    # B: なめらかな深い青の肉の皮膚、赤黒い口、黄ばんだ牙
    "b": dict(
        skin=arr((12, 14, 36), (22, 26, 60), (34, 40, 88), (50, 58, 118), (72, 82, 148), (102, 112, 176)),
        plate=arr((26, 24, 48), (44, 40, 72), (66, 60, 98), (92, 84, 124), (124, 114, 150), (156, 146, 178)),
        fin=arr((38, 14, 50), (66, 22, 78), (96, 34, 106), (128, 54, 136), (160, 84, 164)),
        mouth=arr((34, 8, 22), (66, 12, 34), (104, 22, 50), (146, 44, 70)),
        belly=arr((60, 62, 104), (88, 92, 132), (120, 124, 160), (154, 158, 188)),
        tooth=arr((52, 42, 36), (104, 86, 62), (158, 138, 98), (200, 184, 140), (230, 218, 178), (248, 242, 214)),
        bone=BONE, scale=2.0, scales=False, warts=True),
    # C: 青灰の石肌、象牙の骨板、光る刻印
    "c": dict(
        skin=arr((20, 26, 38), (34, 44, 60), (52, 64, 82), (74, 88, 106), (100, 114, 130), (130, 144, 158)),
        plate=arr((50, 44, 40), (86, 76, 64), (128, 114, 92), (168, 152, 122), (204, 190, 158), (232, 222, 192)),
        fin=arr((24, 20, 40), (42, 36, 66), (64, 56, 92), (90, 80, 120), (120, 108, 148)),
        mouth=arr((26, 12, 22), (50, 20, 34), (82, 34, 50), (118, 52, 68)),
        belly=arr((50, 60, 76), (76, 88, 104), (106, 118, 132), (136, 148, 160)),
        tooth=arr((58, 52, 50), (110, 100, 88), (158, 146, 124), (196, 184, 158), (224, 214, 186), (244, 238, 214)),
        bone=BONE, scale=2.0, scales=False, warts=False),
}


def pick(pal, idx):
    return pal[np.clip(idx, 0, len(pal) - 1)].astype(float)


def paint(key, faces, size, P, N, M, extras):
    pal = PAL[key]
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
    near_concave = (D < 2.2) & (K < -24) & INS
    x, y, z = P[..., 0], P[..., 1], P[..., 2]
    col = np.zeros((H, W, 3))

    # ------------------------------------------------ 皮膚・骨板: 面ごとの明るさ + 鱗 / いぼ + 濡れた光
    n_lo = vnoise(P * 0.18 + 3, 1)
    n_hi = vnoise(P * 0.55 + 9, 5)
    base = 0.50 + 0.17 * lit + 0.12 * (n_lo - 0.5) + 0.05 * bay
    belly = (ny < -0.45) & (y < 13)
    base = base + 0.13 * (y > 17) * 0.0
    v = base
    ci = np.floor(v * 6).astype(int)
    if pal["scales"]:
        r = np.maximum(np.sqrt(x ** 2 + (y - 14) ** 2), 3.0)
        ang = np.arctan2(x, y - 14)
        cz = z / 2.4
        row = np.floor(cz)
        arc = ang * r / 2.4 + 0.5 * (row % 2)
        fz = cz - row
        fa = arc - np.floor(arc)
        gap = (fz < 0.16) | (fa < 0.16)
        hi = (fz > 0.16) & (fz < 0.42) & (fa > 0.16) & (fa < 0.5) & (lit > -0.1)
        sc_on = (mats == "skin") & (bones != "head") | ((mats == "skin") & (bones == "head") & (z > -20))
        ci = ci - (gap & sc_on) + (hi & sc_on)
    if pal["warts"]:
        wart = (hash1(np.floor(P[..., 0] * 0.8) * 7 + np.floor(P[..., 1] * 0.8) * 13 + np.floor(P[..., 2] * 0.8) * 29, 3) > 0.9)
        wr = wart & (mats == "skin") & (lit > -0.2)
        ci = ci + wr * 1 - (wart & (mats == "skin") & (lit <= -0.2))
    ci = ci + convex * (1 if True else 0) - concave
    skin_c = pick(pal["skin"], ci)
    # 骨板
    if key == "c":
        pv = 0.52 + 0.20 * lit + 0.10 * (n_lo - 0.5) + 0.05 * bay
        pi = np.floor(pv * 6).astype(int) + convex - 2 * concave
        # 板の継ぎ目の筋: 面の中の 1 本のくねった溝
        seam = (hash1(fi, 21) < 0.5) & (area > 24) & INS & (LU == (hash1(fi, 22) * (CW - 2)).astype(int) + 1) & (LV < CH * 0.8)
        pi = np.where(seam, 0, pi)
        plate_c = pick(pal["plate"], pi)
        # 板の縁取り: 縁沿いの 1 画素は暗く
        plate_c = np.where(((D < 0.85) & INS & ~convex)[..., None], pick(pal["plate"], pi - 2), plate_c)
    else:
        pv = 0.50 + 0.18 * lit + 0.08 * (n_lo - 0.5) + 0.05 * bay
        pi = np.floor(pv * 6).astype(int) + convex - 2 * concave
        plate_c = pick(pal["plate"], pi)
    bel_c = pick(pal["belly"], np.floor((0.45 + 0.25 * lit + 0.1 * (n_hi - 0.5) + 0.05 * bay) * 4).astype(int) + convex - concave)
    col = np.where((mats == "skin")[..., None], np.where(belly[..., None], bel_c, skin_c), col)
    col = np.where((mats == "plate")[..., None], plate_c, col)
    # 濡れた艶: 上を向いた縁に細い光、面にもまばらな照り
    wet = (convex & (lit > 0.45) & (ny > 0.35) & (hash1(LU * 3 + LV * 5 + fi, 4) < 0.16)) | ((hash1(xx * 7 + yy * 13, 8) < 0.022) & (lit > 0.35) & INS)
    wet = wet & np.isin(mats, ["skin", "plate"]) & ~belly
    col = np.where(wet[..., None], SPEC[((lit > 0.65) & (hash1(xx + yy * 3, 9) < 0.5)).astype(int)], col)

    # ------------------------------------------------ ひれ: 紫の膜、骨 (筋) が走る
    fv = 0.46 + 0.22 * lit + 0.12 * (n_hi - 0.5) + 0.05 * bay
    fidx = np.floor(fv * 5).astype(int) + convex - concave
    ray = (LU % 3 == 1) & INS & (area > 6)
    fidx = fidx - ray
    fin_c = pick(pal["fin"], fidx)
    col = np.where((mats == "fin")[..., None], fin_c, col)

    # ------------------------------------------------ 骨 (角・棘・ルアーの茎): 象牙
    bv = 0.50 + 0.22 * lit + 0.08 * (n_lo - 0.5) + 0.05 * bay
    bidx = np.floor(bv * 6).astype(int) + convex - concave
    col = np.where((mats == "bone")[..., None], pick(pal["bone"], bidx), col)

    # ------------------------------------------------ 歯: 根元は歯茎、先ほど白く
    if True:
        by = np.where(bones == "head", 8.4, np.where(bones == "jaw", 6.7, 7.5))
        d = np.abs(y - by)
        # 長い歯でも先が白くなるよう、歯ごとの長さではなく距離で
        t = np.clip(d / 5.0, 0, 1)
        ti = np.floor(1.6 + 3.8 * t + 0.8 * (lit * 0.5 + 0.5) + 0.7 * convex + 0.8 * bay).astype(int)
        ti = np.where(concave, 1, ti)
        tc = pick(pal["tooth"], np.clip(ti, 1, 5))
        gum = (d < 1.0 + 0.3 * bay) & (mats == "tooth")
        tc = np.where(gum[..., None], pick(pal["mouth"], np.array(2) + (lit > 0.2) - (hash1(xx, 2) < 0.3)), tc)
        col = np.where((mats == "tooth")[..., None], tc, col)

    # ------------------------------------------------ 口の中: 赤黒い肉、天井の横しわ
    mv = 0.45 + 0.15 * lit + 0.28 * (hash1(LU * 31 + LV * 17 + fi, 2)) + 0.1 * bay
    wr_ = (np.floor(z * 0.5) % 2 == 0)
    midx = np.floor(mv * 4).astype(int) - (wr_ & (np.abs(x) < 6)) + concave
    mc = pick(pal["mouth"], midx)
    veins = (vnoise(P * 0.5 + 41, 3) > 0.74)
    mc = np.where(veins[..., None] & (midx > 0)[..., None], pick(pal["mouth"], midx - 1), mc)
    col = np.where((mats == "mouth")[..., None], mc, col)

    # ------------------------------------------------ ルアー: 白青の玉 (全面が光る)
    lv = np.clip(np.floor(1.2 + 1.8 * (lit * 0.5 + 0.5) + 0.7 * bay), 0, 2).astype(int)
    lc = np.array([(120, 205, 232), (190, 240, 255), (240, 252, 255)])[lv].astype(float)
    col = np.where((mats == "lure")[..., None], lc, col)
    glow = (mats == "lure") & owned

    # ------------------------------------------------ 目: 暗い窪みと光る虹彩
    hd = (bones == "head") & np.isin(mats, ["skin", "plate"]) & owned
    for e in extras.get("eyes", []):
        c = e["c"]
        s = 1 if c[0] > 0 else -1
        side = (x * s > 0.2)
        dz = (z - c[2]) / e["rz"]
        dw = np.sqrt((x - c[0]) ** 2 + (y - c[1]) ** 2) / e["rw"]
        dw = np.where(side, dw, 9.0)
        r = np.sqrt(dz ** 2 + dw ** 2)
        sock = hd & (r + 0.07 * bay < 1.0)
        dk = np.clip(np.floor(2.7 - (1.0 - r) * 4.0 + 0.4 * bay).astype(int), 0, 2)
        col = np.where(sock[..., None], np.array([(8, 8, 16), (16, 16, 28), (30, 32, 46)])[dk], col)
        pr = np.sqrt((dz / e.get('pz', 0.62)) ** 2 + (dw / e.get('pw', 0.55)) ** 2)
        pup = hd & (pr + 0.06 * bay < 1.0)
        col = np.where(pup[..., None], np.where((pr < 0.45)[..., None], PUPIL[1], PUPIL[0]), col)
        glow |= pup
    # 鼻の穴 (A/B/C 共通の小さな暗点)
    # C: 光る刻印 (頭の天板に走る刻み線)
    if extras.get("runes"):
        top = hd & (ny > 0.35) & (bones == "head")
        rune = top & ((np.abs(x) < 0.55) & (z < -12) & (z > -30) & ((np.floor((z + 30) * 1.0) % 3) != 2)) | \
            (top & (np.abs(np.abs(x) - 4.6) < 0.5) & (np.floor((z + 8) * 0.9) % 4 == 0))
        side_r = hd & (np.abs(N[..., 0]) > 0.55) & (np.abs(y - 15.5) < 0.5) & (z < -12) & (z > -30) & (np.floor(z * 0.8) % 3 != 0)
        rr = rune | side_r
        col = np.where(rr[..., None], PUPIL[0], col)
        glow |= rr

    col = np.clip(col, 0, 255)
    img = np.zeros((H, W, 4), np.uint8)
    img[..., :3] = col.astype(np.uint8)
    img[..., 3] = np.where(owned, 255, 0)
    img[..., :3] = fill_outside(img[..., :3], faces, INS, M)
    gl = fill_outside(np.stack([glow] * 3, -1).astype(np.uint8), faces, INS, M)[..., 0] > 0
    return img, gl & owned


def build_variant(key, size_hint=128):
    faces, extras = BUILDERS[key]()
    size, P, N, M, L = build_atlas(faces, size_hint, 0)
    atlas, glow = paint(key, faces, size, P, N, M, extras)
    return dict(faces=faces, extras=extras, size=size, atlas=atlas, glow=glow)


def write_variant(key, r):
    d = OUT / key
    d.mkdir(parents=True, exist_ok=True)
    Image.fromarray(r["atlas"], "RGBA").save(d / "waterway_maw_lp_atlas.png")
    gm = np.zeros((r["size"], r["size"], 4), np.uint8)
    gm[r["glow"]] = (255, 255, 255, 255)
    Image.fromarray(gm, "RGBA").save(d / "waterway_maw_lp_glowmask.png")
    data = export_mesh(r["faces"], r["size"], d / "waterway_maw_lp_mesh.json", "waterway_maw_lowpoly_" + key,
                       "waterway_maw_lp_atlas.png",
                       {"glowmask": "waterway_maw_lp_glowmask.png",
                        "note": "bones and pivots match geo/entity/waterway_maw.geo.json; jaw bone = lower jaw, lure bone = lantern"},
                       bone_defs=BONES)
    return data


def build_all():
    out = {}
    for k in "abc":
        r = build_variant(k)
        r["data"] = write_variant(k, r)
        out[k] = r
    return out


if __name__ == "__main__":
    for k, r in build_all().items():
        print(k, r["size"], r["data"]["triangle_count"], {b: v["triangle_count"] for b, v in r["data"]["bones"].items()})
