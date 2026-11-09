"""嵐と灰の土地に足す敵 6 種 (issue #20。2026-11-09 の選定「3 種ずつ」) のラフ 3 案の共通部品。
一章の ch1/ck.py をそのまま使い、出力先を docs/review/roughs_late_foes に替える (ch4/ck4.py と同じ作り)。

使い方 (モブ 1 体 = 1 ファイル late_foes/<id>.py):
    from lf import *
    BONES / PAL / build(opt) / POSES / SPEC は ch1 と同じ書式。storm(True) で目を青白に、False で熾の橙に
    run(SPEC, build, PAL, BONES, POSES)   -> docs/review/roughs_late_foes/<id>/{a,b,c}/ と <id>.png・<id>_manifest.json
座標: 1 単位 = 1/16 ブロック、y が上、前 = -Z。人の背は 29。
"""
from __future__ import annotations

import json
import sys
from pathlib import Path

import numpy as np

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE.parent / "ch1"))
import ck as _ck  # noqa: E402
from ck import *  # noqa: E402,F401,F403

ROOT = HERE.parents[3]
OUT = ROOT / "docs/review/roughs_late_foes"
_ck.OUT = OUT

# ---- 嵐の高地: 嵐雲の灰青・岩・雪・雷の青白
CLOUD = [(52, 58, 76), (78, 86, 108), (108, 118, 140), (142, 152, 172), (180, 188, 204)]
CRAG = [(40, 42, 48), (62, 64, 72), (88, 90, 98), (116, 118, 126), (148, 150, 156)]
SNOW = [(170, 180, 196), (204, 212, 224), (232, 238, 246)]
VOLT = [(70, 140, 220), (130, 196, 255), (200, 236, 255), (240, 252, 255)]
FUR = [(48, 44, 50), (74, 68, 76), (104, 96, 104), (138, 128, 134)]
HORN = [(70, 60, 50), (110, 96, 78), (156, 140, 112), (198, 184, 152)]
# ---- 灰の荒野: 灰・炭・黒石・熾の橙・煤けた赤
ASH = [(40, 38, 42), (78, 74, 76), (118, 112, 110), (158, 152, 146), (198, 192, 182)]
CHAR = [(8, 7, 10), (20, 16, 20), (36, 27, 29), (60, 40, 36)]
BASALT = [(24, 24, 30), (44, 44, 52), (66, 66, 76), (92, 92, 102), (122, 122, 132)]
EMBER = [(150, 46, 16), (210, 84, 24), (255, 146, 40), (255, 210, 96)]
RUST = [(48, 22, 18), (86, 40, 28), (128, 64, 40), (168, 98, 60)]
OBSID = [(10, 8, 16), (24, 18, 32), (44, 34, 56), (70, 58, 86)]
SKIN = [(60, 40, 30), (96, 64, 44), (136, 96, 64), (176, 132, 92)]

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


def storm(on: bool):
    _ck.ra.PUPIL = np.array([(120, 190, 255), (220, 245, 255)]) if on else np.array([(230, 110, 30), (255, 210, 120)])


def eye_at(x, y, z, r=0.55):
    return dict(c=V(x, y, z), rx=r, ry=r * 0.8, pr=(0.5, 0.4), tilt=0.0, zr=0.8)


def lumps(bone, mat_, pts, flat=0.8, segs=5):
    F = []
    for p, rr in pts:
        F += ellipsoid(bone, mat_, p, (rr, rr * flat, rr), segs=segs, rings=1)
    return F


def manifest_entry(spec, results, out_png_rel):
    opts = {}
    for k, r in zip("abc", results):
        o = spec["options"][k]
        bad = ", ".join(f"{a}-{b}:{f}" for a, b, f in r["buried"]) or "なし"
        opts[k] = {"name": o["name"] + f' ({r["tris"]} 三角形)', "desc": o["desc"]}
        r["bad_txt"] = bad
    tri = " / ".join(f'{k.upper()} {r["tris"]}' for k, r in zip("abc", results))
    extra = spec["extra"] + f' 三角形: {tri}。埋まった部品の検査: ' + " / ".join(f'{k.upper()} {r["bad_txt"]}' for k, r in zip("abc", results))
    return {"id": "late_" + spec["id"], "short": spec["short"], "kind": spec["kind"], "title": spec["title"],
            "sheets": [out_png_rel], "extra": extra, "options": opts, "role": spec.get("role", ""), "land": spec.get("land", "")}


def run(spec, build, pal, bones, poses, hint=128):
    OUT.mkdir(parents=True, exist_ok=True)
    results = []
    for k in "abc":
        r = _ck.build_option(spec["id"], k, build, pal, bones, hint)
        results.append(r)
        print(spec["id"], k, "tris", r["tris"], "atlas", r["size"], "buried", r["buried"])
    png = OUT / f'{spec["id"]}.png'
    _ck.sheet(spec, results, bones, poses, png)
    ent = manifest_entry(spec, results, f'roughs_late_foes/{spec["id"]}.png')
    (OUT / f'{spec["id"]}_manifest.json').write_text(json.dumps(ent, ensure_ascii=False, indent=1), encoding="utf-8")
    return results


def box(bone, m, c, size, taper=(1.0, 1.0), rot=None):
    """箱 (上面の x, z を taper 倍)"""
    sx, sy, sz = size
    cx, cy, cz = c
    R = np.eye(3) if rot is None else rot

    def ring_(y, kx, kz):
        pts = [(-sx / 2 * kx, y, -sz / 2 * kz), (sx / 2 * kx, y, -sz / 2 * kz), (sx / 2 * kx, y, sz / 2 * kz), (-sx / 2 * kx, y, sz / 2 * kz)]
        return [R @ np.array(p) + np.array([cx, cy, cz]) for p in pts]
    return loft(bone, m, [ring_(-sy / 2, 1, 1), ring_(sy / 2, taper[0], taper[1])], caps=(m, m))


def rows_std(cy, sc, h=360, extra=()):
    """前 3/4・横・後ろ 3/4 と、追加の姿勢の行"""
    r = [("前 3/4", "idle", 212, 12, h, (0, cy, 0), sc), ("横", "idle", 270, 6, h, (0, cy, 0), sc), ("後ろ 3/4", "idle", 32, 14, h, (0, cy, 0), sc)]
    for head, pose in extra:
        r.append((head, pose, 220, 10, h, (0, cy, 0), sc))
    return r


HUMAN_BONES = {"root": (None, (0, 0, 0)), "body": ("root", (0, 12.0, 0)), "head": ("body", (0, 24.0, 0)),
               "arm_l": ("body", (5.5, 22.0, 0)), "arm_r": ("body", (-5.5, 22.0, 0)), "weapon": ("arm_r", (-6.0, 11.0, -0.5)),
               "offhand": ("arm_l", (6.0, 11.0, -0.5)), "leg_l": ("body", (2.1, 11.0, 0)), "leg_r": ("body", (-2.1, 11.0, 0))}


def biped(F, top="cloth", legs_m="cloth", skin="skin", boots="dark", bulk=1.0):
    """人の体 (頭は呼ぶ側で)。前 = -Z。背 29"""
    F += part("torso", ellipsoid("body", top, (0, 18.0, 0), (4.4 * bulk, 5.6, 2.8 * bulk), segs=8, rings=3))
    F += part("hips", ellipsoid("body", legs_m, (0, 12.4, 0), (4.0 * bulk, 2.4, 2.6 * bulk), segs=8, rings=2))
    F += part("neck", tube("head", skin, [(0, 22.6, 0), (0, 25.0, 0)], ra=[1.3, 1.2], sides=6, hint=(1, 0, 0)))
    for sx, b in ((1, "leg_l"), (-1, "leg_r")):
        F += part(b + "_up", tube(b, legs_m, [(sx * 2.1, 11.8, 0), (sx * 2.2, 6.0, -0.2)], ra=[1.7 * bulk, 1.4 * bulk], sides=6, hint=(1, 0, 0)))
        F += part(b + "_lo", tube(b, legs_m, [(sx * 2.2, 6.2, -0.2), (sx * 2.2, 1.6, 0)], ra=[1.4 * bulk, 1.25 * bulk], sides=6, hint=(1, 0, 0)))
        F += part(b + "_boot", box(b, boots, (sx * 2.2, 1.0, -0.6), (2.9 * bulk, 2.0, 4.2), taper=(0.95, 0.9)))
    for sx, b in ((1, "arm_l"), (-1, "arm_r")):
        F += part(b + "_up", tube(b, top, [(sx * 5.4 * bulk, 22.0, 0), (sx * 5.8 * bulk, 16.6, 0)], ra=[1.45 * bulk, 1.25 * bulk], sides=6, hint=(0, 0, 1)))
        F += part(b + "_lo", tube(b, top, [(sx * 5.8 * bulk, 16.8, 0), (sx * 6.0 * bulk, 11.6, -0.4)], ra=[1.25 * bulk, 1.1 * bulk], sides=6, hint=(0, 0, 1)))
        F += part(b + "_hand", ellipsoid(b, skin, (sx * 6.0 * bulk, 10.6, -0.5), (1.2, 1.25, 1.2), segs=6, rings=2))
    return F


def grow(F, bones, k):
    """形を k 倍に (骨の支点も)。主を大きくするとき"""
    for f in F:
        f.verts = np.asarray(f.verts, float) * k
        f.ref = np.asarray(f.ref, float) * k
        if getattr(f, "loc", None) is not None:
            f.loc = np.asarray(f.loc, float) * k
    return {n: (p, tuple(x * k for x in v)) for n, (p, v) in bones.items()}
