"""苔這い (moss_crawler) の低ポリ 3 案: 絵 (ドット絵テクスチャ)・書き出し・描画。

  python tools/rough/lowpoly/moss_crawler/lp.py        # メッシュ JSON・アトラス・glowmask を 3 案ぶん書く
出力: docs/review/lowpoly_roughs/lp_moss_crawler/{a,b,c}/moss_crawler_lowpoly_{mesh.json,atlas.png,glowmask.png}
"""
from __future__ import annotations

import inspect
import math
import sys
import textwrap
from pathlib import Path

import numpy as np
from PIL import Image

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
sys.path.insert(0, str(HERE.parent))
sys.path.insert(0, str(HERE.parents[1]))
import lpkit  # noqa
from lpkit import build_atlas, edge_fields, fill_outside  # noqa
import model as MD  # noqa
from model import BONES  # noqa
import bone_sentinel_lp as BS  # noqa
from bone_sentinel_lp import BAYER, export_mesh, hash1, vnoise  # noqa

OUT = MD.ROOT / "docs/review/lowpoly_roughs/lp_moss_crawler"

# ---------------------------------------------------------------------- レンダラ: にじみの色を画素の色から取る
_src = textwrap.dedent(inspect.getsource(lpkit.render))
_new = ("gm = Image.fromarray((gbuf * 255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(3.0 * ss))\n"
        "        gm = np.array(gm, float)[..., None] / 255.0\n"
        "        gc = np.stack([np.array(Image.fromarray((np.where(gbuf, color[..., c], 0)).clip(0, 255).astype(np.uint8))"
        ".filter(ImageFilter.GaussianBlur(3.0 * ss)), float) for c in range(3)], -1) / 255.0\n"
        "        add = gc / np.maximum(gm, 1e-3) * gm * 0.9 * 1.0")
_src = _src.replace("\n    ".join(_old.split("\n        ")), _new) if False else _src
_lines = _src.split("\n")
# 行単位で差し替え (インデントを保つ)
_out = []
_i = 0
while _i < len(_lines):
    ln = _lines[_i]
    if "gm = Image.fromarray((gbuf * 255)" in ln:
        ind = ln[:len(ln) - len(ln.lstrip())]
        for t in _new.split("\n"):
            _out.append(ind + t.lstrip())
        _i += 4
        continue
    _out.append(ln)
    _i += 1
_ns = dict(vars(lpkit))
exec("\n".join(_out), _ns)
render = _ns["render"]

# ---------------------------------------------------------------------- パレット
FLESH = {
    "a": np.array([(52, 46, 44), (92, 82, 72), (134, 120, 100), (172, 158, 130), (206, 192, 160)]),
    "b": np.array([(40, 36, 38), (72, 66, 64), (108, 100, 92), (146, 136, 118), (184, 172, 148)]),
    "c": np.array([(62, 44, 42), (104, 76, 64), (148, 110, 90), (186, 148, 118), (216, 184, 150)]),
}
BONE = np.array([(44, 38, 40), (88, 76, 64), (134, 119, 96), (176, 160, 128), (212, 198, 164), (238, 229, 203)])
MOSS = np.array([(22, 38, 26), (42, 66, 34), (70, 102, 44), (106, 142, 66), (150, 182, 92)])
DARK = np.array([(10, 10, 18), (22, 20, 30), (38, 34, 46)])
IRIS = np.array([(88, 28, 16), (164, 58, 18), (232, 118, 38), (255, 178, 80), (255, 226, 148)])
SCLERA = np.array([(120, 100, 88), (190, 172, 150), (226, 214, 190), (244, 236, 214)])
LUREC = np.array([(96, 176, 196), (150, 224, 238), (206, 248, 255), (244, 255, 255)])
LIGHT = np.array([-0.35, 0.85, 0.4])
LIGHT = LIGHT / np.linalg.norm(LIGHT)


def paint(faces, size, P, N, M, extras, style):
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
    n_lo = vnoise(P * 0.18 + 3, 1)
    col = np.zeros((H, W, 3))

    # 苔の量 (面の素材ごとの基本量)
    def mossy(thr, up=0.34, concave_w=0.3):
        mn = 0.62 * vnoise(P * 0.23 + 21, 4) + 0.38 * vnoise(P * 0.55 + 7, 9)
        s = mn + up * np.clip(ny, 0, 1) + concave_w * near_concave + 0.06 * bay
        return s, s > thr

    # ---- 肉 (芋虫の皮): 縦の節の筋と、ところどころの斑
    pal = FLESH[style]
    zc = np.zeros((H, W))
    for i, z in enumerate(MD.ZS):
        zc = np.where(bones == f"seg{i + 1}", z, zc)
    dz = np.abs(P[..., 2] - zc)
    v = 0.60 + 0.26 * lit + 0.12 * (n_lo - 0.5) + 0.05 * bay - 0.13 * np.clip((dz - 1.4) / 1.0, 0, 1)
    v = v - 0.10 * np.clip((1.2 - np.clip(P[..., 1], 0, 6)) / 1.2, 0, 1)
    wrinkle = ((hash1(np.floor(P[..., 2] * 2) * 5 + np.floor(P[..., 0] * 2), 3) < 0.10) & (area > 6)) * 0.12
    fi5 = np.floor((v - wrinkle) * 5).astype(int) + convex * 1 - concave
    fcol = pal[np.clip(fi5, 0, 4)].astype(float)
    ms, mm = mossy(0.93)
    fcol = np.where((mm & (ny > 0))[..., None], MOSS[np.clip(np.floor((ms - 0.93) * 7 + 0.5 * lit + 0.8).astype(int), 0, 3)], fcol)
    col = np.where((mats == "flesh")[..., None], fcol, col)

    # ---- 骨
    bv = 0.70 + 0.14 * lit + 0.09 * (n_lo - 0.5) + 0.05 * bay
    bv = bv - 0.10 * np.clip((1.5 - P[..., 1]) / 1.5, 0, 1)
    streak = (hash1(LU * 7 + fi * 13, 3) < 0.15) & (area > 12)
    bidx = np.floor((bv - 0.09 * streak) * 6).astype(int) + (convex & (lit > 0)) - (convex & (lit <= 0)) - concave
    bidx = np.clip(np.maximum(bidx, 1), 0, 5)
    bcol = BONE[bidx].astype(float)
    ms, mm = mossy(0.90, 0.34, 0.36)
    mm = mm & (ny > -0.2)
    bcol = np.where(mm[..., None], MOSS[np.clip(np.floor((ms - 0.90) * 7 + 0.4 * lit + 0.7).astype(int), 0, 3)], bcol)
    isb = (mats == "bone") | (mats == "bone2")
    b2dark = np.where((mats == "bone2")[..., None], np.clip(bcol - np.array([14, 14, 12]), 0, 255), bcol)
    col = np.where(isb[..., None], b2dark, col)
    # 歯
    ti = np.clip(np.floor(3.5 + 2.0 * (lit * 0.5 + 0.5) + 0.7 * convex + 0.8 * bay).astype(int), 2, 5)
    ti = np.where(concave, 2, ti)
    col = np.where((mats == "tooth")[..., None], BONE[ti], col)

    # ---- 苔
    for nm, off in (("moss", 0), ("moss2", 1)):
        d = 1.55 + 1.55 * lit + 0.9 * (vnoise(P * 0.5 + 11 + off, 5) - 0.5) + 0.9 * bay
        d = d + 0.2 * off - 0.9 * near_concave - 0.7 * concave
        idx = np.floor(d).astype(int)
        idx = np.where(convex & (ny > 0.2), idx + 1, idx)
        idx = np.clip(idx, 0, 3)
        speck = (hash1(np.floor(P[..., 0] * 2) * 9 + np.floor(P[..., 2] * 2) * 5 + off, 21) < 0.07) & (ny > 0.2)
        mc = MOSS[idx].astype(float)
        mc = np.where(speck[..., None], MOSS[4], mc)
        col = np.where((mats == nm)[..., None], mc, col)

    # ---- 口の奥
    mi = np.clip(np.floor(0.8 + 1.2 * hash1(LU * 31 + LV * 17 + fi, 2) + 0.4 * bay).astype(int), 0, 2)
    col = np.where((mats == "mouth")[..., None], DARK[mi], col)

    # ---- 誘いの光 (触角の先)
    li = np.clip(np.floor(1.2 + 2.2 * (lit * 0.5 + 0.5) + 0.8 * bay + 0.5 * convex).astype(int), 0, 3)
    col = np.where((mats == "lure")[..., None], LUREC[li], col)
    glow = (mats == "lure") & owned

    # ---- 一つ目: 前の八角の板 = 虹彩 (島の中心から数えた画素の同心円) と瞳、斜めの帯 = 白目 (赤い血管と、根元の影)
    if "eye" in extras:
        ec = extras["eye"]
        rel = P - ec
        # 白目
        wi = np.clip(np.floor(1.3 + 1.6 * (lit * 0.5 + 0.5) + 0.7 * bay - 1.2 * np.clip((12.9 - P[..., 2]) / 1.0, 0, 1)).astype(int), 0, 3)
        scl = SCLERA[wi].astype(float)
        vein = (hash1(np.floor(P[..., 0] * 2) * 7 + np.floor(P[..., 1] * 2) * 3 + np.floor(P[..., 2] * 2), 8) < 0.2)
        scl = np.where(vein[..., None], np.array([188, 96, 88.0]), scl)
        col = np.where(((mats == "eye_w") & owned)[..., None], scl, col)
        # 虹彩
        dx = LU + 0.5 - CW / 2.0
        dy = LV + 0.5 - CH / 2.0
        rho = np.sqrt(dx * dx + dy * dy)
        er0 = extras["eye_r"][0] * 0.76 * 2.0 / 1.0     # 板の半径 (テクセル)
        t = rho / (CW / 2.0)
        ang = np.arctan2(dy, dx)
        ray = np.sin(ang * 6.0 + 0.7) > 0.5
        iris_i = np.where(rho < 2.0, 4, np.where(rho < 2.7, 3, 2))
        iris_i = np.where((rho >= 2.0) & ray, iris_i - 1, iris_i)
        iris_i = np.where(t > 0.86, 0, iris_i)
        icol = IRIS[np.clip(iris_i, 0, 4)].astype(float)
        pup = rho < 1.45
        icol = np.where(pup[..., None], np.array([12, 8, 14.0]), icol)
        # ハイライト: 板の上 (y 最大) 側、左の 1 画素
        hx = (np.abs(rel[..., 0] - extras["eye_r"][0] * 0.3) < 0.26) & (np.abs(rel[..., 1] - extras["eye_r"][1] * 0.3) < 0.26)
        hl = hx & ~pup & (rho > 1.3) & (rho < 3.0)
        icol = np.where(hl[..., None], np.array([255, 244, 214.0]), icol)
        is_iris = (mats == "eye") & owned
        col = np.where(is_iris[..., None], icol, col)
        glow = glow | (is_iris & ~pup & (t <= 0.86) & ~hl)

    col = np.clip(col, 0, 255)
    img = np.zeros((H, W, 4), np.uint8)
    img[..., :3] = col.astype(np.uint8)
    img[..., 3] = np.where(owned, 255, 0)
    img[..., :3] = fill_outside(img[..., :3], faces, INS, M)
    gl3 = fill_outside(np.stack([glow] * 3, -1).astype(np.uint8), faces, INS, M)[..., 0] > 0
    return img, gl3 & owned


# ---------------------------------------------------------------------- 書き出し
def build_option(style, size_hint=128):
    faces, extras = MD.build(style)
    size, P, N, M, L = build_atlas(faces, size_hint, 0)
    atlas, glow = paint(faces, size, P, N, M, extras, style)
    return dict(style=style, faces=faces, size=size, atlas=atlas, glow=glow, extras=extras)


def save_option(r):
    d = OUT / r["style"]
    d.mkdir(parents=True, exist_ok=True)
    Image.fromarray(r["atlas"], "RGBA").save(d / "moss_crawler_lowpoly_atlas.png")
    gm = np.zeros((r["size"], r["size"], 4), np.uint8)
    gm[r["glow"]] = (255, 255, 255, 255)
    Image.fromarray(gm, "RGBA").save(d / "moss_crawler_lowpoly_glowmask.png")
    data = export_mesh(r["faces"], r["size"], d / "moss_crawler_lowpoly_mesh.json", "moss_crawler_lowpoly_" + r["style"],
                       "moss_crawler_lowpoly_atlas.png",
                       {"glowmask": "moss_crawler_lowpoly_glowmask.png",
                        "note": "coordinates are in the geo space (head = +Z, root has yaw 180 in the geo); "
                                "teeth/eyes/lure_* are their own bones; the glowmask marks the iris and the lure lights"},
                       bone_defs=BONES)
    r["data"] = data
    return data


def main():
    res = {}
    for s in "abc":
        r = build_option(s)
        data = save_option(r)
        per = {k: v["triangle_count"] for k, v in data["bones"].items() if v["triangle_count"]}
        print(s, "atlas", r["size"], "tris", data["triangle_count"], "glow px", int(r["glow"].sum()))
        print("   ", per)
        res[s] = r
    return res


if __name__ == "__main__":
    main()
