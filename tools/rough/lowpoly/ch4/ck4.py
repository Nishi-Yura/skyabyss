"""四章 (余燼) の地上の敵のラフ用の共通部品。一章の ch1/ck.py をそのまま使い、出力先・色の言葉・目の色・光る材質だけ替える (二章の ck2.py と同じ作り)。

使い方 (モブ 1 体 = 1 ファイル ch4/<id>.py):
    from ck4 import *
    BONES / PAL / build(opt) / POSES / SPEC は ch1 と同じ書式
    run(SPEC, build, PAL, BONES, POSES)   -> docs/review/ch4_roughs/<id>/{a,b,c}/ と <id>.png・<id>_manifest.json

座標: 1 単位 = 1/16 ブロック、y が上、前 = -Z。テクスチャは 1 ブロック = 32 テクセル。
光る材質: PAL["glow_mats"] に挙げた材質の画素は光る画素のマスクに入る (熾・火の粉・割れ目)。
"""
from __future__ import annotations

import json
import sys
from pathlib import Path

import numpy as np

CH4 = Path(__file__).resolve().parent
sys.path.insert(0, str(CH4.parent / "ch1"))
import ck as _ck  # noqa: E402
from ck import *  # noqa: E402,F401

ROOT = CH4.parents[3]
OUT = ROOT / "docs/review/ch4_roughs"
_ck.OUT = OUT
# 四章の目は熾の橙
_ck.ra.PUPIL = np.array([(230, 110, 30), (255, 210, 120)])

# ---- 四章 (余燼) の色の言葉: 灰・炭・黒石・熾の橙・煤けた赤
ASH = [(40, 38, 42), (78, 74, 76), (118, 112, 110), (158, 152, 146), (198, 192, 182)]
CHAR = [(8, 7, 10), (20, 16, 20), (36, 27, 29), (60, 40, 36)]
BASALT = [(24, 24, 30), (44, 44, 52), (66, 66, 76), (92, 92, 102), (122, 122, 132)]
EMBER = [(150, 46, 16), (210, 84, 24), (255, 146, 40), (255, 210, 96)]
RUST = [(48, 22, 18), (86, 40, 28), (128, 64, 40), (168, 98, 60)]
SOOT = [(14, 12, 14), (30, 26, 28), (50, 44, 44), (74, 66, 62)]
HEMP = [(70, 60, 44), (110, 96, 70), (150, 134, 100), (190, 176, 140)]
DEEP = [(6, 4, 6), (16, 10, 12), (30, 20, 20)]

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
        f' 。メッシュ・アトラス・光る画素のマスクは docs/review/ch4_roughs/{spec["id"]}/{{a,b,c}}/。生成: tools/rough/lowpoly/ch4/{spec["id"]}.py。'
    return {"id": "ch4_" + spec["id"], "short": spec["short"], "kind": spec["kind"], "title": spec["title"],
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
    ent = manifest_entry(spec, results, f'ch4_roughs/{spec["id"]}.png')
    (OUT / f'{spec["id"]}_manifest.json').write_text(json.dumps(ent, ensure_ascii=False, indent=1), encoding="utf-8")
    return results


def lumps(bone, mat_, pts, flat=0.8, segs=5):
    """小さなこぶの群れ (灰の粒・殻のいぼ)。"""
    F = []
    for p, rr in pts:
        F += ellipsoid(bone, mat_, p, (rr, rr * flat, rr), segs=segs, rings=1)
    return F
