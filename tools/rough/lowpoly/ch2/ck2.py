"""二章 (潮殻) の地上の敵のラフ用の共通部品。一章の ch1/ck.py をそのまま使い、出力先・色の言葉・目の色・光る材質だけ替える。

使い方 (モブ 1 体 = 1 ファイル ch2/<id>.py):
    from ck2 import *
    BONES / PAL / build(opt) / POSES / SPEC は ch1 と同じ書式
    run(SPEC, build, PAL, BONES, POSES)   -> docs/review/ch2_roughs/<id>/{a,b,c}/ と <id>.png・<id>_manifest.json

座標: 1 単位 = 1/16 ブロック、y が上、前 = -Z。テクスチャは 1 ブロック = 32 テクセル。
光る材質: PAL["glow_mats"] に挙げた材質の画素は光る画素のマスクに入る (真珠・潮の青の目)。
"""
from __future__ import annotations

import json
import sys
from pathlib import Path

import numpy as np

CH2 = Path(__file__).resolve().parent
sys.path.insert(0, str(CH2.parent / "ch1"))
import ck as _ck  # noqa: E402
from ck import *  # noqa: E402,F401

ROOT = CH2.parents[3]
OUT = ROOT / "docs/review/ch2_roughs"
_ck.OUT = OUT
# 二章の目は海硝子の青緑
_ck.ra.PUPIL = np.array([(90, 200, 196), (200, 252, 240)])

# ---- 二章 (潮殻) の色の言葉: 珊瑚・真珠層・青緑・白 (塩)
CORAL = [(70, 26, 40), (128, 48, 60), (186, 84, 84), (228, 128, 112), (250, 180, 156)]
NACRE = [(88, 84, 104), (140, 136, 158), (190, 186, 206), (224, 220, 232), (248, 244, 242)]
TEAL = [(12, 36, 44), (22, 64, 72), (36, 100, 104), (64, 142, 134), (112, 186, 172)]
SALT = [(116, 122, 128), (170, 176, 180), (210, 214, 214), (234, 236, 232), (252, 252, 248)]
SAND = [(80, 66, 48), (136, 114, 82), (188, 164, 120), (220, 200, 154), (240, 226, 188)]
DRIFT = [(38, 32, 30), (70, 60, 52), (108, 92, 76), (148, 130, 106), (186, 170, 144)]
PEARL = [(170, 160, 176), (214, 206, 218), (240, 234, 240), (255, 252, 250)]
DEEP = [(6, 10, 18), (14, 20, 32), (28, 36, 52)]
KELP = [(14, 30, 34), (24, 52, 54), (40, 80, 76), (66, 112, 100)]

_paint0 = _ck.paint


def _paint_glow(faces, size, P, N, M, pal, eyes=()):
    atlas, glow = _paint0(faces, size, P, N, M, pal, eyes)
    gm = pal.get("glow_mats", [])
    if gm:
        ids = [i for i, f in enumerate(faces) if f.mat in gm]
        if ids:
            sel = np.isin(np.where(M >= 0, M, 0), ids) & (M >= 0)
            glow = glow | sel
    return atlas, glow


_ck.paint = _paint_glow


def manifest_entry(spec, results, out_png_rel):
    opts = {}
    for k, r in zip("abc", results):
        o = spec["options"][k]
        bad = ", ".join(f"{a}-{b}:{f}" for a, b, f in r["buried"]) or "なし"
        opts[k] = {"name": o["name"] + f' ({r["tris"]} 三角形)', "desc": o["desc"]}
        r["bad_txt"] = bad
    tri = " / ".join(f'{k.upper()} {r["tris"]}' for k, r in zip("abc", results))
    extra = spec["extra"] + f' 三角形 (全身): {tri} (目安 {spec["budget"]})。埋まった部品の検査: ' + \
        " / ".join(f'{k.upper()} {r["bad_txt"]}' for k, r in zip("abc", results)) + \
        f' 。メッシュ・アトラス・光る画素のマスクは docs/review/ch2_roughs/{spec["id"]}/{{a,b,c}}/。生成: tools/rough/lowpoly/ch2/{spec["id"]}.py。'
    return {"id": "ch2_" + spec["id"], "short": spec["short"], "kind": spec["kind"], "title": spec["title"],
            "sheets": [out_png_rel], "extra": extra, "options": opts}


_ck.manifest_entry = manifest_entry


def run(spec, build, pal, bones, poses, hint=128):
    OUT.mkdir(parents=True, exist_ok=True)
    results = []
    for k in "abc":
        r = _ck.build_option(spec["id"], k, build, pal, bones, hint)
        results.append(r)
        print(spec["id"], k, "tris", r["tris"], "atlas", r["size"], "buried", r["buried"])
    png = OUT / f'{spec["id"]}.png'
    _ck.sheet(spec, results, bones, poses, png)
    ent = manifest_entry(spec, results, f'ch2_roughs/{spec["id"]}.png')
    (OUT / f'{spec["id"]}_manifest.json').write_text(json.dumps(ent, ensure_ascii=False, indent=1), encoding="utf-8")
    return results


def lumps(bone, mat_, pts, flat=0.8, segs=5):
    """小さなこぶの群れ (フジツボ・塩の粒)。"""
    F = []
    for p, rr in pts:
        F += ellipsoid(bone, mat_, p, (rr, rr * flat, rr), segs=segs, rings=1)
    return F


def stag(bone, mat_, base, d, length, r, branches=(), bend=(0, 0, 0), sides=4, rings=3, hint=(1, 0, 0)):
    """枝珊瑚: 根元から d へ伸びる先細りの幹と、途中から出る枝 (t, 向きの足し, 長さ, 半径[, 材質])。"""
    d = nrm(d)
    base = np.asarray(base, float)
    bend = np.asarray(bend, float)
    F = spike(bone, mat_, base, d, length, r, sides=sides, rings=rings, bend=bend, hint=hint)
    for (t, add, ln, rr, *m) in branches:
        p = base + d * length * t + bend * t * t
        dd = nrm(nrm(d * length + 2 * bend * t) + np.asarray(add, float))
        F += spike(bone, (m[0] if m else mat_), p, dd, ln, rr, sides=max(3, sides - 1), rings=1)
    return F
