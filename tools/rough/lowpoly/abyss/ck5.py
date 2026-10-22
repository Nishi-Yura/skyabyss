"""深淵 (五章) の敵のラフ用の共通部品。四章の ck4.py (一章の ck.py) をそのまま使い、出力先・色の言葉・目の色・光る材質だけ替える。

使い方 (敵 1 体 = 1 ファイル abyss/<id>.py):
    from ck5 import *
    BONES / PAL / build(opt) / POSES / SPEC は ch1 と同じ書式
    run(SPEC, build, PAL, BONES, POSES)  -> docs/review/abyss_roughs/<id>/{a,b,c}/ と <id>.png・<id>_manifest.json

座標: 1 単位 = 1/16 ブロック、y が上、前 = -Z。
層の色の言葉: 骨 (骨・方解石・灰)、塩 (白・塩の皮・海)、雷 (凝灰岩・酸化した銅・白い雷)。
光る材質: PAL["glow_mats"] の画素が光る (雷の層の雷・骨の層の鼓動の穴)。
"""
from __future__ import annotations

import json
import sys
from pathlib import Path

import numpy as np

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE.parent / "ch4"))
import ck4 as _c4  # noqa: E402
from ck4 import *  # noqa: E402,F401
import ck4 as _c4mod  # noqa: E402
import ck as _ck  # noqa: E402

ROOT = HERE.parents[3]
OUT = ROOT / "docs/review/abyss_roughs"
_ck.OUT = OUT
_c4mod.OUT = OUT
# 深淵の目は層ごとの色 (骨は洞の赤、塩は白、雷は青白い光)
_ck.ra.PUPIL = np.array([(200, 60, 60), (255, 200, 200)])

# ---- 深淵の層の色の言葉
BONEC = [(34, 30, 28), (80, 72, 62), (126, 114, 92), (168, 156, 126), (210, 200, 170), (236, 228, 204)]   # 骨
CALC = [(56, 58, 62), (104, 106, 110), (152, 154, 158), (200, 202, 204), (234, 234, 230)]                 # 方解石
ASHY = [(38, 36, 38), (72, 70, 72), (110, 108, 108), (150, 148, 144)]                                     # 灰
HOLLOW = [(10, 6, 8), (22, 14, 16), (40, 26, 28)]                                                        # 洞の黒
SALT = [(84, 92, 102), (140, 150, 158), (196, 204, 208), (228, 236, 236), (250, 252, 250)]               # 塩
SALTC = [(60, 70, 80), (104, 116, 126), (150, 162, 170), (200, 210, 214)]                                # 塩の皮の陰
SEA = [(16, 36, 46), (30, 64, 78), (52, 98, 110), (86, 136, 140)]                                        # 海の緑
TUFF = [(36, 34, 34), (66, 62, 58), (100, 94, 86), (138, 130, 116)]                                      # 凝灰岩
OXCU = [(26, 56, 54), (44, 92, 84), (70, 134, 118), (106, 176, 150)]                                     # 酸化した銅
LIGHT = [(96, 130, 180), (160, 192, 232), (210, 228, 250), (246, 252, 255)]                              # 白い雷


def eye_at(x, y, z, r=0.35):
    return dict(c=V(x, y, z), rx=r, ry=r, pr=(0.5, 0.5), tilt=0.0, zr=0.6)


def make_pal(mats, glow=()):
    """材質の辞書を作る。mats: {名前: (階調, 基の明るさ, 追加の引数)}。"""
    out = {}
    for k, v in mats.items():
        ramp, base = v[0], v[1]
        kw = v[2] if len(v) > 2 else {}
        out[k] = mat(ramp, base, **kw)
    return {"mats": out, "skull_bone": "none", "face_mat": "none", "glow_mats": list(glow)}


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
        f' 。メッシュ・アトラス・光る画素のマスクは docs/review/abyss_roughs/{spec["id"]}/{{a,b,c}}/。生成: tools/rough/lowpoly/abyss/{spec["id"]}.py。' + \
        " 歪み・崩れ (深いほど不気味な変わり果てた姿) の方向: " + spec.get("later", "")
    return {"id": "abyss_" + spec["id"], "short": spec["short"], "kind": spec["kind"], "title": spec["title"],
            "sheets": [out_png_rel], "extra": extra, "options": opts}


_ck.manifest_entry = manifest_entry


import pattern5  # noqa: E402,F401  (アトラスの表面の模様。_ck.paint を差し替える)


def run(spec, build, pal, bones, poses, hint=128):
    OUT.mkdir(parents=True, exist_ok=True)
    results = []
    for k in "abc":
        r = _ck.build_option(spec["id"], k, build, pal, bones, hint)
        results.append(r)
        print(spec["id"], k, "tris", r["tris"], "atlas", r["size"], "buried", r["buried"])
    png = OUT / f'{spec["id"]}.png'
    _ck.sheet(spec, results, bones, poses, png)
    ent = manifest_entry(spec, results, f'abyss_roughs/{spec["id"]}.png')
    (OUT / f'{spec["id"]}_manifest.json').write_text(json.dumps(ent, ensure_ascii=False, indent=1), encoding="utf-8")
    return results
