"""燠虫 (cinder_roller) の低ポリ 3 案: ドット絵テクスチャ・書き出し・描画。

  python tools/rough/lowpoly/cinder_roller/lp.py     # メッシュ JSON・アトラス・glowmask を 3 案ぶん書く
出力: docs/review/lowpoly_roughs/lp_cinder_roller/{a,b,c}/cinder_roller_lowpoly_{mesh.json,atlas.png,glowmask.png}
"""
from __future__ import annotations

import inspect
import json
import math
import sys
import textwrap
from pathlib import Path

import numpy as np
from PIL import Image, ImageFilter

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

OUT = MD.ROOT / "docs/review/lowpoly_roughs/lp_cinder_roller"

# ---------------------------------------------------------------------- レンダラ: にじみの色を画素の色から取る
_src = textwrap.dedent(inspect.getsource(lpkit.render))
_new = ("gm = Image.fromarray((gbuf * 255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(3.0 * ss))\n"
        "        gm = np.array(gm, float)[..., None] / 255.0\n"
        "        gc = np.stack([np.array(Image.fromarray((np.where(gbuf, color[..., c], 0)).clip(0, 255).astype(np.uint8))"
        ".filter(ImageFilter.GaussianBlur(3.0 * ss)), float) for c in range(3)], -1) / 255.0\n"
        "        add = gc / np.maximum(gm, 1e-3) * gm * 0.9 * 1.0")
_lines = _src.split("\n")
_out, _i = [], 0
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
STONE = {   # 玄武 (青黒い石)
    "a": np.array([(12, 14, 22), (28, 32, 44), (48, 54, 70), (74, 82, 98), (108, 118, 136)]),
    # 熔皮 (暗い赤茶の皮殻)
    "b": np.array([(26, 10, 12), (62, 22, 18), (104, 40, 26), (146, 66, 38), (186, 100, 54)]),
    # 炭 (黒茶)
    "c": np.array([(18, 14, 14), (38, 30, 26), (62, 50, 42), (90, 76, 64), (122, 106, 90)]),
}
ASH = np.array([(112, 108, 104), (150, 146, 140), (186, 182, 174), (220, 216, 206)])
LAVA = np.array([(96, 18, 8), (176, 44, 10), (236, 100, 20), (255, 160, 48), (255, 218, 120)])
EYEC = {"a": np.array([(150, 76, 12), (255, 170, 40), (255, 222, 110)]),
        "b": np.array([(170, 60, 14), (255, 128, 28), (255, 232, 160)]),
        "c": np.array([(150, 40, 14), (240, 84, 24), (255, 170, 70)])}
LIGHT = np.array([-0.35, 0.85, 0.4])
LIGHT = LIGHT / np.linalg.norm(LIGHT)


def voronoi(P, scale, seed=0):
    """3 次元のセル: (F1, F2) 距離を返す (単位 = モデルの単位)。P: (...,3)"""
    q = P / scale
    base = np.floor(q)
    f1 = np.full(q.shape[:-1], 9.0)
    f2 = np.full(q.shape[:-1], 9.0)
    cid = np.zeros(q.shape[:-1])
    for dx in (-1, 0, 1):
        for dy in (-1, 0, 1):
            for dz in (-1, 0, 1):
                cell = base + np.array([dx, dy, dz])
                h = np.stack([hash1(cell[..., 0] * 7 + cell[..., 1] * 13 + cell[..., 2] * 29, seed + j) for j in range(3)], -1)
                d = np.linalg.norm(cell + 0.2 + 0.6 * h - q, axis=-1)
                cid = np.where(d < f1, h[..., 0], cid)
                nf1 = np.minimum(f1, d)
                f2 = np.minimum(f2, np.maximum(f1, d))
                f1 = nf1
    return f1 * scale, f2 * scale, cid


def paint(faces, size, P, N, M, style):
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
    near_concave = (D < 2.0) & (K < -24) & INS
    pal = STONE[style]
    n_lo = vnoise(P * 0.2 + 3, 1)
    hf = hash1(fi, 5)                                   # 面ごとの明暗のばらつき
    segidx = np.zeros((H, W))
    zc = np.zeros((H, W))
    for k in range(9):
        zc = np.where(bones == f"seg{k}", MD.ZK[k], zc)
    col = np.zeros((H, W, 3))
    glow = np.zeros((H, W), bool)

    cell = {"a": 2.3, "b": 3.4, "c": 1.55}[style]
    f1, f2, cid = voronoi(P, cell, 11)
    border = (f2 - f1)
    bw = {"a": 0.5, "b": 0.7, "c": 0.42}[style]
    on_border = (border < bw) & (area > 3)

    # ---- 甲の板
    v = 0.50 + 0.30 * lit + 0.10 * (n_lo - 0.5) + 0.05 * bay + 0.05 * (hf - 0.5)
    if style == "a":
        v = v + 0.06 * np.where(f1 < 0.9, 1, 0)                     # 柱の頭の面を少し明るく
    idx = np.floor(v * 5).astype(int) + convex * 1 - concave * 1
    base = pal[np.clip(idx, 0, 4)].astype(float)

    if style == "a":
        # 玄武の柱の継ぎ目: 暗い溝。ところどころ熱い割れ目 (溶岩色)
        seam_dark = on_border & (area > 4)
        hot = on_border & (cid < 0.16)
        base = np.where(seam_dark[..., None], pal[0].astype(float), base)
        li = np.clip(np.floor(1.2 + 2.2 * (1 - np.clip(border / bw, 0, 1)) + 0.9 * bay + 0.3 * (hf - 0.5)).astype(int), 0, 4)
        base = np.where(hot[..., None], LAVA[li], base)
        glow_plate = hot
    elif style == "b":
        # 熔皮: 大きな皮のかたまりと、その間の広い割れ目 (中から赤く光る)
        crack = on_border & (cid < 0.3)
        hot_i = np.clip(np.floor(2.0 + 2.4 * (1 - np.clip(border / bw, 0, 1)) + 0.9 * bay + 0.4 * (hf - 0.5)).astype(int), 1, 4)
        # 皮の縁は焦げて暗い
        rim = (border < bw * 2.0) & ~crack & (cid < 0.3)
        base = np.where((on_border & (cid >= 0.3))[..., None], pal[0].astype(float), base)
        base = np.where(rim[..., None], pal[np.clip(idx - 2, 0, 4)].astype(float), base)
        base = np.where(crack[..., None], LAVA[hot_i], base)
        glow_plate = crack
        # 皮のしわ: 面内の短い暗い筋
        wr = (hash1(LU * 7 + fi * 13, 3) < 0.12) & (area > 10) & INS & ~crack
        base = np.where(wr[..., None], pal[np.clip(idx - 1, 0, 4)].astype(float), base)
    else:
        # 炭: ワニ皮のような細かい割れ目。割れ目の交点だけ熾が赤く光る
        crack = on_border
        base = np.where(crack[..., None], pal[0].astype(float), base)
        junction = (cid < 0.045) & (f1 < cell * 0.42) & (area > 4)
        li = np.clip(np.floor(1.0 + 2.0 * hash1(LU * 3 + LV * 5 + fi, 9) + 0.8 * bay).astype(int), 0, 3)
        base = np.where(junction[..., None], LAVA[li], base)
        glow_plate = junction
        # 灰: 上を向いた面・縁に積もる (大きなかたまり + 縁だけ画素をちらす)
        sc = 0.62 * vnoise(P * 0.25 + 21, 4) + 0.38 * vnoise(P * 0.6 + 7, 9) + 0.45 * np.clip(ny, 0, 1) + 0.05 * bay
        ash = (sc > 0.97) & (ny > 0.25) & ~junction
        ai = np.clip(np.floor((sc - 0.97) * 9 + 0.8 * lit + 0.9).astype(int), 0, 3)
        base = np.where(ash[..., None], ASH[ai], base)
    plate_like = np.isin(mats, ["plate", "wing", "head"])
    col = np.where(plate_like[..., None], base, col)
    glow = glow | (plate_like & owned & glow_plate)

    # ---- 段 (riser): 影の中。下の縁が熱い
    rv = 0.30 + 0.25 * lit + 0.05 * bay
    ri = np.clip(np.floor(rv * 5).astype(int), 0, 3)
    rcol = pal[ri].astype(float)
    hot_edge = (mats == "riser") & (D < 1.0) & (K < 0) & (hash1(LU * 5 + fi, 2) < 0.7) & INS
    rcol = np.where(hot_edge[..., None], LAVA[np.clip(np.floor(1.0 + 1.5 * hash1(LU + fi, 1) + 0.6 * bay).astype(int), 0, 3)], rcol)
    col = np.where((mats == "riser")[..., None], rcol, col)
    glow = glow | (hot_edge & owned)

    # ---- 床 (溶岩の継ぎ目): 溶岩。冷えた皮のかけらが浮く
    fl = 2.2 + 1.5 * vnoise(P * 0.5 + 5, 3) + 0.8 * bay - 1.2 * (f1 > cell * 0.28)
    crust = (vnoise(P * 0.9 + 17, 7) > 0.62) & (mats == "floor")
    fi_ = np.clip(np.floor(fl).astype(int), 0, 4)
    fcol = LAVA[fi_].astype(float)
    fcol = np.where(crust[..., None], pal[np.clip(np.floor(1.5 + 1.5 * bay + 1.0 * lit).astype(int), 0, 3)].astype(float), fcol)
    col = np.where((mats == "floor")[..., None], fcol, col)
    glow = glow | ((mats == "floor") & ~crust & owned)

    # ---- 腹と膜: 溶岩の腹 (目を回したとき見える弱み)。細かい皮のしま
    bi = np.clip(np.floor(2.4 + 1.6 * vnoise(P * 0.6 + 2, 3) + 0.9 * bay).astype(int), 1, 4)
    bcol = LAVA[bi].astype(float)
    cells = (border < 0.5) & (mats == "belly")
    bcol = np.where(cells[..., None], LAVA[np.clip(bi - 2, 0, 4)], bcol)
    col = np.where((mats == "belly")[..., None], bcol, col)
    glow = glow | ((mats == "belly") & owned)
    col = np.where((mats == "seam")[..., None], LAVA[np.clip(np.floor(1.5 + 2.0 * bay + 1.0).astype(int), 0, 3)].astype(float), col)
    glow = glow | ((mats == "seam") & owned)

    # ---- 脚・触角・尾: 石の肌。先端が熾で赤い (触角の先は光る)
    lv = 0.38 + 0.28 * lit + 0.05 * bay
    limb = pal[np.clip(np.floor(lv * 5).astype(int) + convex, 0, 4)].astype(float)
    col = np.where(np.isin(mats, ["leg", "tail"])[..., None], limb, col)
    # 先端 (触角: x が外へ、z が前へ進むほど熱い)
    tipt = np.clip((np.abs(P[..., 0]) - 2.0) / 2.0 + (P[..., 2] - 12.5) / 4.0, 0, 1)
    tip = (mats == "ant") & (tipt > 0.62 + 0.1 * bay)
    ant = pal[np.clip(np.floor(lv * 5).astype(int), 0, 4)].astype(float)
    ant = np.where(tip[..., None], LAVA[np.clip(np.floor(2 + 2 * tipt + 0.5 * bay).astype(int), 2, 4)], ant)
    col = np.where((mats == "ant")[..., None], ant, col)
    glow = glow | (tip & owned)
    # 脚の先の熾 (足先が焦げて赤い。光らない)
    foot = (mats == "leg") & (P[..., 1] < 0.9)
    col = np.where(foot[..., None], LAVA[np.clip(np.floor(0.5 + 1.5 * bay + 1.0), 0, 2).astype(int)].astype(float) * 0.7, col)

    # ---- 棘 (B): 根元は暗い皮殻、先へ熱くなる
    if style == "b":
        bk = np.zeros((H, W))
        for k in range(9):
            c = np.array([0, MD.PY, MD.ZK[k]])
            bk = np.where(bones == f"seg{k}", np.linalg.norm(P - c, axis=-1) - (MD.R_K[k] + MD.STY["b"]["dR"]), bk)
        t = np.clip(bk / 2.4, 0, 1)
        sp = pal[np.clip(np.floor(0.9 + 3.0 * lit * 0.5 + 0.6 * bay + 1.0), 0, 4).astype(int)].astype(float)
        hotp = t > 0.55 + 0.12 * bay
        sp = np.where(hotp[..., None], LAVA[np.clip(np.floor(1.6 + 3.0 * (t - 0.5) + 0.5 * bay).astype(int), 1, 4)], sp)
        col = np.where((mats == "spike")[..., None], sp, col)
        glow = glow | ((mats == "spike") & hotp & owned)
    # ---- 灰のひれ (C): 灰色。縁だけ熾
    if style == "c":
        fv = 0.45 + 0.4 * (lit * 0.5 + 0.5) + 0.2 * bay
        fcol = ASH[np.clip(np.floor(fv * 4).astype(int), 0, 3)].astype(float)
        fedge = (mats == "fin") & (D < 1.2) & INS & (hash1(LU * 3 + LV * 7 + fi, 8) < 0.6)
        fcol = np.where(fedge[..., None], LAVA[np.clip(np.floor(1.0 + 2.0 * hash1(LU + LV + fi, 3) + 0.5 * bay).astype(int), 0, 3)], fcol)
        col = np.where((mats == "fin")[..., None], fcol, col)
        glow = glow | (fedge & owned)

    # ---- 目: 光る小面。真ん中の縦 1 画素が瞳 (暗い)
    dx = LU + 0.5 - CW / 2.0
    ev = EYEC[style]
    ei = np.clip(np.floor(1.3 + 1.2 * (1 - np.abs(dx) / np.maximum(CW / 2.0, 1)) + 0.5 * bay).astype(int), 0, 2)
    ecol = ev[ei].astype(float)
    pupil = (np.abs(dx) < 0.55) & (mats == "eye") & (CW >= 3)
    ecol = np.where(pupil[..., None], pal[0].astype(float), ecol)
    col = np.where((mats == "eye")[..., None], ecol, col)
    glow = glow | ((mats == "eye") & owned & ~pupil)

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
    atlas, glow = paint(faces, size, P, N, M, style)
    return dict(style=style, faces=faces, size=size, atlas=atlas, glow=glow)


def save_option(r):
    d = OUT / r["style"]
    d.mkdir(parents=True, exist_ok=True)
    Image.fromarray(r["atlas"], "RGBA").save(d / "cinder_roller_lowpoly_atlas.png")
    gm = np.zeros((r["size"], r["size"], 4), np.uint8)
    gm[r["glow"]] = (255, 255, 255, 255)
    Image.fromarray(gm, "RGBA").save(d / "cinder_roller_lowpoly_glowmask.png")
    data = export_mesh(r["faces"], r["size"], d / "cinder_roller_lowpoly_mesh.json", "cinder_roller_lowpoly_" + r["style"],
                       "cinder_roller_lowpoly_atlas.png",
                       {"glowmask": "cinder_roller_lowpoly_glowmask.png",
                        "note": "coordinates are in the geo space (head = +Z, root has yaw 180 in the geo); "
                                "each bone's rest rotation is written in 'rotation'; seg0..seg8 are the body plates "
                                "(curled: 40 degrees apart around (0,6,0)); mem*/legs/antennae/uropods sit inside the ball"},
                       bone_defs=BONES)
    # 休めの回転 (geo の rotation) を骨ごとに書く
    p = d / "cinder_roller_lowpoly_mesh.json"
    j = json.loads(p.read_text(encoding="utf-8"))
    for b, rot in MD.REST_ROT.items():
        j["bones"][b]["rotation"] = rot
    p.write_text(json.dumps(j, ensure_ascii=False), encoding="utf-8")
    r["data"] = j
    return j


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
