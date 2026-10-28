"""六章 (月) の敵・主・人のラフの共通部品 (docs/design/46)。深淵の ck5 をそのまま使い、出力先・色の言葉・目の色だけ替える。

使い方 (1 体 = 1 ファイル moon/<id>.py):
    from mk import *
    BONES / PAL / build(opt) / POSES / SPEC は深淵と同じ書式
    run(SPEC, build, PAL, BONES, POSES)  -> docs/review/moon_roughs/<id>/{a,b,c}/ と <id>.png・<id>_manifest.json

色の言葉 (46 の 3.1、01 の「月 = 青白と銀」):
  月の砂 DUST (青みの薄灰)・玄武岩 BASALT (青黒)・凝灰岩 TUFF・影 SHADOW (藍の黒)・銀 SILVER・星の光 STAR (光る白青)・前の時代の布 FADED
目は星の白青。光る材質は PAL の glow に挙げたもの。
"""
from __future__ import annotations

import json
import sys
from pathlib import Path

import numpy as np

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE.parent / "abyss"))
import ck5 as _c5  # noqa: E402
from ck5 import *  # noqa: E402,F401
import ck as _ck  # noqa: E402
import ck4 as _c4mod  # noqa: E402

ROOT = HERE.parents[3]
OUT = ROOT / "docs/review/moon_roughs"
_ck.OUT = OUT
_c4mod.OUT = OUT
_c5.OUT = OUT
_ck.ra.PUPIL = np.array([(18, 26, 60), (190, 225, 255)])   # 藍の瞳に星の光のハイライト (淡い毛にも暗い殻にも見える)

DUST = [(70, 76, 88), (112, 118, 130), (152, 158, 168), (192, 196, 204), (226, 228, 232)]
BASALT = [(14, 16, 24), (30, 34, 46), (52, 58, 72), (80, 88, 104), (110, 118, 134)]
TUFFM = [(40, 40, 46), (70, 70, 76), (104, 102, 106), (140, 138, 140)]
SHADOW = [(4, 4, 10), (12, 12, 26), (24, 24, 46), (40, 40, 70)]
SILVER = [(60, 66, 80), (110, 118, 134), (168, 176, 190), (214, 220, 230), (244, 248, 252)]
STAR = [(90, 140, 210), (150, 196, 250), (210, 232, 255), (250, 252, 255)]
FADED = [(60, 58, 54), (100, 96, 88), (140, 134, 122), (180, 174, 160), (212, 206, 192)]
INDIGO_F = [(24, 28, 40), (40, 46, 64), (62, 70, 92), (90, 98, 122)]
LEATHER_F = [(44, 36, 30), (74, 62, 50), (106, 92, 76), (140, 124, 104)]


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
        f' 。メッシュ・アトラス・光る画素のマスクは docs/review/moon_roughs/{spec["id"]}/{{a,b,c}}/。生成: tools/rough/lowpoly/moon/{spec["id"]}.py。' + \
        " 三段の姿 (区画が進むと) の方向: " + spec.get("later", "")
    return {"id": "moon_" + spec["id"], "short": spec["short"], "kind": spec["kind"], "title": spec["title"],
            "sheets": [out_png_rel], "extra": extra, "options": opts}


def run(spec, build, pal, bones, poses, hint=128):
    OUT.mkdir(parents=True, exist_ok=True)
    results = []
    for k in "abc":
        r = _ck.build_option(spec["id"], k, build, pal, bones, hint)
        results.append(r)
        print(spec["id"], k, "tris", r["tris"], "atlas", r["size"], "buried", r["buried"])
    png = OUT / f'{spec["id"]}.png'
    _ck.sheet(spec, results, bones, poses, png)
    ent = manifest_entry(spec, results, f'moon_roughs/{spec["id"]}.png')
    (OUT / f'{spec["id"]}_manifest.json').write_text(json.dumps(ent, ensure_ascii=False, indent=1), encoding="utf-8")
    return results
