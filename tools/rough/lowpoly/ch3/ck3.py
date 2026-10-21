"""三章 (雷翼) の地上の敵のラフ用の共通部品。一章の ch1/ck.py をそのまま使い、出力先・色の言葉・目の色・光る材質だけ替える。

使い方 (モブ 1 体 = 1 ファイル ch3/<id>.py):
    from ck3 import *
    BONES / PAL / build(opt) / POSES / SPEC は ch1 と同じ書式
    run(SPEC, build, PAL, BONES, POSES)   -> docs/review/ch3_roughs/<id>/{a,b,c}/ と <id>.png・<id>_manifest.json

座標: 1 単位 = 1/16 ブロック、y が上、前 = -Z。テクスチャは 1 ブロック = 32 テクセル。
光る材質: PAL["glow_mats"] に挙げた材質の画素は光る画素のマスクに入る (雷の青白の光・目)。
"""
from __future__ import annotations

import json
import sys
from pathlib import Path

import numpy as np

CH3 = Path(__file__).resolve().parent
sys.path.insert(0, str(CH3.parent / "ch1"))
import ck as _ck  # noqa: E402
from ck import *  # noqa: E402,F401

ROOT = CH3.parents[3]
OUT = ROOT / "docs/review/ch3_roughs"
_ck.OUT = OUT
# 三章の目は雷の青白
_ck.ra.PUPIL = np.array([(150, 214, 255), (240, 250, 255)])

# ---- 三章 (雷翼) の地上の色の言葉: 峰の灰石・焦げ・銅・白青の羽・雷の青白
ROCK = [(26, 30, 38), (48, 54, 64), (76, 84, 96), (108, 118, 130), (146, 156, 166), (186, 194, 202)]
CHAR = [(12, 12, 16), (26, 24, 28), (44, 40, 42), (70, 62, 60), (100, 88, 80)]
COPPER = [(36, 18, 12), (84, 42, 20), (136, 72, 32), (184, 104, 46), (226, 146, 72), (252, 196, 120)]
VERD = [(36, 70, 66), (62, 110, 98), (92, 148, 128), (130, 184, 160)]
FEATHER = [(30, 38, 54), (62, 76, 100), (108, 124, 150), (158, 174, 198), (204, 216, 234), (240, 246, 252)]
DOWN = [(120, 120, 132), (168, 168, 180), (206, 206, 216), (232, 232, 240), (250, 250, 255)]
STEEL = [(14, 20, 32), (30, 40, 58), (50, 64, 88), (76, 94, 122), (110, 130, 158), (156, 176, 202)]
BOLT = [(110, 170, 230), (150, 214, 255), (210, 240, 255), (250, 252, 255)]
AMBER = [(110, 54, 12), (190, 100, 22), (240, 160, 46), (255, 214, 120)]
DEEP = [(6, 8, 16), (14, 18, 30), (28, 34, 50)]
SAND = [(80, 66, 48), (136, 114, 82), (188, 164, 120), (220, 200, 154), (240, 226, 188)]

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
        f' 。メッシュ・アトラス・光る画素のマスクは docs/review/ch3_roughs/{spec["id"]}/{{a,b,c}}/。生成: tools/rough/lowpoly/ch3/{spec["id"]}.py。'
    return {"id": "ch3_" + spec["id"], "short": spec["short"], "kind": spec["kind"], "title": spec["title"],
            "sheets": [out_png_rel], "extra": extra, "options": opts}


_ck.manifest_entry = manifest_entry


def run(spec, build, pal, bones, poses, hint=128):
    OUT.mkdir(parents=True, exist_ok=True)
    results = []
    for k in "abc":
        r = _ck.build_option(spec["id"], k, build, pal if "mats" in pal else pal[k], bones, hint)
        results.append(r)
        print(spec["id"], k, "tris", r["tris"], "atlas", r["size"], "buried", r["buried"])
    png = OUT / f'{spec["id"]}.png'
    _ck.sheet(spec, results, bones, poses, png)
    ent = manifest_entry(spec, results, f'ch3_roughs/{spec["id"]}.png')
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
