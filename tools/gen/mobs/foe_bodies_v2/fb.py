# -*- coding: utf-8 -*-
"""敵の体の作り直し (2026-11-12、案 A「作り込み」) の共通部品。

今のメッシュ (assets/skyabyss/lowpoly/<名前>.json) の骨の名前・親・pivot をそのまま使い、形と絵だけを入れ替える。
動き (animations/entity/<名前>.animation.json) と箱の geo は触らない。
部品は乗り物の低ポリの部品 (mounts_lp/mlp.py)。長さは 1/16 ブロック。
"""
from __future__ import annotations

import json
import math
import sys
from pathlib import Path

import numpy as np

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE.parent / "dungeon_foes_lp"))
from foes import *  # noqa: E402,F401,F403
import foes as F  # noqa: E402
import blp  # noqa: E402

RES = ROOT / "src/main/resources/assets/skyabyss"
LOW = RES / "lowpoly"
TEXLP = RES / "textures/lowpoly"
REVIEW = ROOT / "docs/review/foe_bodies"

# ---------------------------------------------------------------------- 色 (foes.py にない分)
DOWN = [(150, 156, 176), (186, 192, 210), (216, 222, 236), (240, 244, 252)]
COPPER = [(90, 48, 26), (136, 76, 40), (184, 112, 60), (224, 156, 96)]
KELP = [(26, 50, 30), (40, 76, 42), (60, 104, 56), (88, 134, 74)]
SKINW = [(70, 92, 96), (96, 122, 124), (124, 150, 150), (156, 180, 176)]
ASH = [(70, 68, 66), (100, 96, 92), (134, 128, 122), (170, 164, 156)]
ASHSKIN = [(74, 72, 72), (102, 98, 96), (132, 126, 120), (160, 154, 146)]
CHAR = [(18, 16, 16), (32, 28, 26), (50, 44, 40), (72, 64, 58)]
EMBER = [(150, 50, 14), (220, 100, 30), (255, 170, 70), (255, 230, 160)]
DAWN = [(200, 150, 70), (250, 200, 110), (255, 236, 170), (255, 250, 220)]
SALT = [(150, 156, 160), (190, 196, 198), (220, 224, 224), (244, 246, 244)]
SALTD = [(70, 80, 88), (100, 110, 118), (134, 142, 148), (170, 176, 180)]
HOLLOW = [(6, 6, 10), (12, 12, 18), (20, 20, 28)]
CALC = [(130, 118, 96), (164, 150, 124), (196, 184, 156), (224, 214, 190)]
BASALT = [(30, 32, 38), (46, 48, 56), (66, 68, 78), (90, 92, 102)]
BOLT = [(110, 190, 255), (170, 225, 255), (220, 245, 255), (250, 254, 255)]
SKIN = [(120, 80, 60), (156, 110, 84), (190, 142, 110), (216, 174, 140)]
BROWN = [(46, 30, 20), (74, 50, 32), (104, 74, 48), (134, 100, 66)]
GREEN = [(38, 52, 30), (56, 76, 42), (78, 102, 56), (104, 130, 74)]
TROUS = [(30, 30, 36), (46, 46, 54), (64, 64, 74), (86, 86, 96)]
RED = [(90, 20, 18), (140, 34, 28), (184, 56, 44), (220, 92, 72)]
STRAW = [(130, 100, 50), (176, 140, 74), (210, 176, 104), (236, 210, 140)]
STEEL = [(60, 64, 70), (92, 98, 106), (130, 138, 146), (176, 184, 190)]
WOOL = [(96, 100, 108), (132, 136, 144), (166, 170, 178), (196, 200, 208), (224, 228, 236)]
FACE = [(14, 14, 18), (28, 28, 34), (46, 46, 54), (66, 66, 76)]
HORN = [(20, 22, 28), (36, 38, 46), (56, 58, 68), (78, 80, 92)]
CLAY = [(80, 50, 36), (120, 80, 56), (160, 116, 84), (196, 154, 118)]
DUST = [(96, 92, 88), (128, 124, 118), (160, 156, 148), (196, 192, 184)]
SILVER = [(120, 128, 140), (160, 168, 180), (200, 208, 218), (236, 240, 246)]
STAR = [(160, 200, 255), (200, 225, 255), (235, 245, 255), (255, 255, 255)]
SHELL = [(110, 40, 46), (160, 64, 66), (204, 104, 92), (236, 150, 130)]
NACRE = [(150, 160, 176), (190, 196, 210), (222, 226, 236), (246, 246, 250)]
MANTLE = [(20, 70, 80), (36, 104, 112), (60, 140, 140), (100, 180, 170)]
TEAL = [(30, 56, 60), (44, 80, 84), (64, 108, 110), (90, 136, 134)]


def mats(**kw):
    """pal_ に渡す材の辞書を、名前=ramp で作る (光るものは glow=[...] で)"""
    glow = kw.pop("glow", [])
    out = {}
    for k, v in kw.items():
        out[k] = v if isinstance(v, dict) else M_(v)
    return pal_(out, glow=glow)


def start(name, title):
    """今のメッシュの骨 (名前・親・pivot) で Mount を作る"""
    old = json.loads((LOW / f"{name}.json").read_text(encoding="utf-8"))
    m = Mount(name, title)
    m.bones = {}
    for k, b in old["bones"].items():
        m.bones[k] = (b["parent"], tuple(float(x) for x in b["pivot"]))
    m.old = old
    return m


def fsec(y, rx, rzf, rzb=None, n=20, folds=0, amp=0.0, ph=0.0, xc=0.0, zc=0.0, dy=None):
    """横に切った輪 (y 一定)。前 (-z) の半径 rzf、後ろ rzb。folds 本のひだ (半径の波 amp)。dy(角度) で縁を上下に波打たせる"""
    rzb = rzf if rzb is None else rzb
    pts = []
    for k in range(n):
        t = -math.pi * 0.75 + k / n * math.tau
        c, s = math.cos(t), math.sin(t)
        w = 1.0 + amp * math.sin(t * folds + ph)
        z = s * (rzf if s < 0 else rzb)
        pts.append(np.array([xc + c * rx * w, y + (dy(t) if dy else 0.0), zc + z * w]))
    return pts


def ring_parts(m, pid, bone, mat, c, r, tube_r, n=12, axis="y", sides=5, tilt=0.0):
    """輪 (帯・縁): 中心 c、半径 r の閉じた管 (n 区切り)"""
    c = np.asarray(c, float)
    pts = []
    for i in range(n + 1):
        a = i / n * math.tau
        if axis == "y":
            p = c + P(math.cos(a) * r[0], math.sin(a) * tilt, math.sin(a) * r[1])
        elif axis == "z":
            p = c + P(math.cos(a) * r[0], math.sin(a) * r[1], 0)
        else:
            p = c + P(0, math.sin(a) * r[1], math.cos(a) * r[0])
        pts.append(p)
    m.add(pid, tub(bone, mat, pts, [tube_r] * len(pts), None, sides=sides, caps=(True, True)))


def fingers(m, bone, mat, wrist, down, side, n=4, length=1.6, r=0.42, spread=0.55, thumb=True):
    """手の指 (先細りの棘を並べる)。wrist = 手の甲の中心、down = 指の向き、side = 指の並ぶ向き"""
    wrist, down, side = P(*wrist), nrm(P(*down)), nrm(P(*side))
    for k in range(n):
        o = (k - (n - 1) / 2) * spread
        m.add(f"{bone}_fing{k}", cone(bone, mat, tuple(wrist + side * o), tuple(down + side * o * 0.12), length * (1 - abs(o) * 0.12), r, sides=5, rings=2,
                                      bend=tuple(-np.cross(side, down) * 0.25)))
    if thumb:
        fwd = np.cross(side, down)
        m.add(f"{bone}_thumb", cone(bone, mat, tuple(wrist - side * spread * (n / 2 + 0.2) + down * 0.2), tuple(nrm(down * 0.6 - side * 0.5 + fwd * 0.4)),
                                    length * 0.75, r * 1.05, sides=5, rings=2))


def hand(m, bone, mat, wrist, down, side, size=1.4, n=4, length=1.5):
    """手のひら (平たい楕円) と指"""
    wrist, down, side = P(*wrist), nrm(P(*down)), nrm(P(*side))
    fwd = np.cross(side, down)
    c = wrist + down * size * 0.55
    # 球を手の厚みの向き (fwd) だけ縮める
    m.add(f"{bone}_palm", ell(bone, mat, tuple(c), (size * 0.95, size * 1.05, size * 0.95), segs=8, rings=3,
                              deform=lambda p, fwd=fwd: p - fwd * np.dot(p, fwd) * 0.45))
    fingers(m, bone, mat, tuple(c + down * size * 0.6), tuple(down), tuple(side), n=n, length=length, r=size * 0.3, spread=size * 0.42)


def stage_tex(atlas, glow, rgb, k_dark, vein_thr, seed=0):
    """段の絵 (歪み・崩れ): 暗くして、割れ目の光る筋を足す。同じ UV のまま"""
    a = atlas.copy()
    a[..., :3] = (a[..., :3].astype(float) * k_dark).clip(0, 255).astype(np.uint8)
    H, Wd = a.shape[:2]
    rng = np.random.default_rng(seed)
    yy, xx = np.mgrid[0:H, 0:Wd]
    v = np.zeros((H, Wd))
    for _ in range(4):
        fx, fy, ph = rng.uniform(0.02, 0.09), rng.uniform(0.02, 0.09), rng.uniform(0, 6.3)
        v += np.sin(xx * fx * 6.3 + ph + np.sin(yy * fy * 6.3) * 2.0)
    veins = (np.abs(v) < vein_thr) & (a[..., 3] > 0)
    a[veins, :3] = rgb
    g = glow | veins
    return a, g


def install(name, m, size, atlas, glow, stages=None):
    """骨の名前・親・pivot と見出しを残して、各骨の頂点・UV・三角形と絵を入れ替える。
    stages = {接尾辞: (atlas, glow)} は同じ UV の段の絵 (textures/lowpoly/<名前><接尾辞>.png)"""
    path = LOW / f"{name}.json"
    old = json.loads(path.read_text(encoding="utf-8"))
    work = ROOT / "build/foe_bodies_v2" / name
    work.mkdir(parents=True, exist_ok=True)
    blp.export_named(m, name, size, atlas, glow, work)
    mesh = json.loads((work / f"{name}_mesh.json").read_text(encoding="utf-8"))
    tri = 0
    for bn, ob in old["bones"].items():
        nb = mesh["bones"].get(bn) or {"vertices": [], "uvs": [], "triangles": []}
        ob["vertices"], ob["uvs"], ob["triangles"] = nb["vertices"], nb["uvs"], nb["triangles"]
        ob["triangle_count"] = len(nb["triangles"])
        tri += ob["triangle_count"]
    extra = set(mesh["bones"]) - set(old["bones"])
    assert not any(mesh["bones"][b]["triangles"] for b in extra), extra
    old["texture"] = name
    old["texture_size"] = [atlas.shape[1], atlas.shape[0]]
    old["texels_per_unit"] = mesh["texels_per_unit"]
    old["uv_inset_texels"] = mesh["uv_inset_texels"]
    old["triangle_count"] = tri
    old["note"] = "foe body v2 (2026-11-12, A detail). Generated by tools/gen/mobs/foe_bodies_v2/build_foe_bodies_v2.py"
    old["skyabyss_closed"] = True
    if glow.any():
        old["glowmask"] = f"{name}_glowmask.png"
    else:
        old.pop("glowmask", None)
    path.write_bytes(json.dumps(old, ensure_ascii=False, separators=(",", ":")).encode("utf-8"))
    for suf, (a, g) in [("", (atlas, glow))] + list((stages or {}).items()):
        Image.fromarray(a, "RGBA").save(TEXLP / f"{name}{suf}.png")
        gp = TEXLP / f"{name}{suf}_glowmask.png"
        if g.any():
            gm = np.zeros_like(a)
            gm[g] = (255, 255, 255, 255)
            Image.fromarray(gm, "RGBA").save(gp)
        elif gp.exists():
            gp.unlink()
    return tri


def render_now(name, yaw, pitch, wh, cen, scale):
    sys.path.insert(0, str(ROOT / "tools/audit"))
    sys.path.insert(0, str(ROOT / "tools/rough"))
    import model_gallery as G
    import lpkit
    f, d, a, gl = G.load(LOW / f"{name}.json")
    return lpkit.render(f, a, gl, yaw, pitch, wh, cen, scale, ss=2)


def cell_cracks(c, col, glow, mats_, s, thr, rgb, seed=0, lit=False, a=0.9):
    """材 mats_ の面に、3D のセルの境目の筋 (割れ目・雷の筋) を描く。lit = True で光る画素にもする"""
    from mlp import cell3, tint
    sel = np.isin(c["mats"], mats_) & c["owned"]
    f1, f2, _ = cell3(c["P"], s, seed)
    on = sel & ((f2 - f1) < thr)
    col = tint(col, on, rgb, a)
    if lit:
        glow = glow | on
    return col, glow
