"""大陸の地上の新しい敵 (docs/design/44) の一体一案の形の共通部品。一章の ck.py (骨・部品・塗り・シート) をそのまま使い、
出力先を docs/review/foes_one に替え、案は一つ (a) だけ回す。人の体・骸の体・箱・布の帯などの部品を足す。
座標: 1 単位 = 1/16 ブロック、y が上、前 = -Z。人の背は 28〜30。
"""
from __future__ import annotations

import json
import math
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE.parent / "ch1"))
import ck  # noqa: E402
from ck import *  # noqa: F401,F403,E402
from ck import part, mat, build_option, sheet, V  # noqa: E402,F401
import numpy as np  # noqa: E402

ck.OUT = ck.ROOT / "docs/review/foes_one"
OUT = ck.OUT

# 人型の骨 (老いた歩哨と同じ名前と位置。weapon は右手、offhand は左手の子)
HUMAN_BONES = {
    "body": (None, [0.0, 12.0, 0.0]),
    "head": ("body", [0.0, 24.0, 0.0]),
    "left_arm": ("body", [5.5, 22.0, 0.0]),
    "left_forearm": ("left_arm", [5.8, 16.5, 0.0]),
    "offhand": ("left_forearm", [6.0, 10.5, -0.5]),
    "right_arm": ("body", [-5.5, 22.0, 0.0]),
    "right_forearm": ("right_arm", [-5.8, 16.5, 0.0]),
    "weapon": ("right_forearm", [-6.0, 10.5, -0.5]),
    "leg_l": ("body", [2.1, 11.0, 0.0]),
    "leg_r": ("body", [-2.1, 11.0, 0.0]),
}


def box(bone, m, c, size, taper=(1.0, 1.0), rot=None):
    """箱 (上面の x, z を taper 倍)。lpkit の loft で作り、骨の名前を付ける"""
    sx, sy, sz = size
    cx, cy, cz = c
    R = np.eye(3) if rot is None else rot

    def ring(y, kx, kz):
        pts = [(-sx / 2 * kx, y, -sz / 2 * kz), (sx / 2 * kx, y, -sz / 2 * kz), (sx / 2 * kx, y, sz / 2 * kz), (-sx / 2 * kx, y, sz / 2 * kz)]
        return [R @ np.array(p) + np.array([cx, cy, cz]) for p in pts]
    return loft(bone, m, [ring(-sy / 2, 1, 1), ring(sy / 2, taper[0], taper[1])], caps=(m, m))


def eye(x, y, z, r=0.8, zr=2.0):
    return dict(c=V(x, y, z), rx=r, ry=r * 0.6, pr=(0.55, 0.38), tilt=0.1, zr=zr)


def human(F, skin="skin", shirt="cloth", pants="pants", boots="leather", s=1.0, bulk=1.0):
    """人の体 (前 = -Z)。胴・腰・脚・腕・手・首。頭は呼ぶ側で。s = 背の倍率、bulk = 太さの倍率"""
    F += part("torso", ellipsoid("body", shirt, (0, 18.0 * s, 0), (4.4 * bulk, 5.6 * s, 2.7 * bulk), segs=8, rings=3))
    F += part("hips", ellipsoid("body", pants, (0, 12.2 * s, 0), (4.0 * bulk, 2.4 * s, 2.5 * bulk), segs=8, rings=2))
    F += part("neck", tube("head", skin, [(0, 22.6 * s, 0), (0, 25.0 * s, 0)], ra=[1.3, 1.2], sides=6, hint=(1, 0, 0)))
    for sx, leg in ((1, "leg_l"), (-1, "leg_r")):
        F += part(leg + "_up", tube(leg, pants, [(sx * 2.1, 11.6 * s, 0), (sx * 2.2, 6.0 * s, -0.2)], ra=[1.6 * bulk, 1.35 * bulk], sides=6, hint=(1, 0, 0)))
        F += part(leg + "_lo", tube(leg, pants, [(sx * 2.2, 6.2 * s, -0.2), (sx * 2.2, 1.6 * s, 0)], ra=[1.35 * bulk, 1.2 * bulk], sides=6, hint=(1, 0, 0)))
        F += part(leg + "_boot", box(leg, boots, (sx * 2.2, 1.0 * s, -0.6), (2.8 * bulk, 2.0 * s, 4.2), taper=(0.95, 0.9)))
    for sx, arm, fore in ((1, "left_arm", "left_forearm"), (-1, "right_arm", "right_forearm")):
        F += part(arm, tube(arm, shirt, [(sx * 5.4 * bulk, 22.0 * s, 0), (sx * 5.8 * bulk, 16.6 * s, 0)], ra=[1.35 * bulk, 1.2 * bulk], sides=6, hint=(0, 0, 1)))
        F += part(fore, tube(fore, shirt, [(sx * 5.8 * bulk, 16.8 * s, 0), (sx * 6.0 * bulk, 11.6 * s, -0.4)], ra=[1.2 * bulk, 1.05 * bulk], sides=6, hint=(0, 0, 1)))
        F += part(fore + "_hand", ellipsoid(fore, skin, (sx * 6.0 * bulk, 10.6 * s, -0.5), (1.15, 1.2, 1.15), segs=6, rings=2))
    return F


def skeleton(F, bone_m="bone", s=1.0):
    """骸の体: 背骨・肋の籠・骨盤・細い手足の骨と関節。頭蓋は呼ぶ側で"""
    F += part("spine", tube("body", bone_m, [(0, 12.0 * s, 0.4), (0, 17.0 * s, 0.8), (0, 22.6 * s, 0.4)], ra=[0.65, 0.6, 0.55], sides=5, hint=(1, 0, 0)))
    for k in range(4):
        y = (21.2 - 1.5 * k) * s
        w = 3.6 - 0.25 * k
        for sx in (1, -1):
            F += part(f"rib{k}{sx}", tube("body", bone_m, [(0, y, 0.9), (sx * w, y - 0.4, -0.2), (sx * w * 0.75, y - 0.9, -1.9), (sx * 0.5, y - 1.2, -2.2)],
                                          ra=[0.32, 0.32, 0.3, 0.25], sides=4, hint=(0, 1, 0)))
    F += part("sternum", tube("body", bone_m, [(0, 21.0 * s, -2.2), (0, 16.4 * s, -2.1)], ra=[0.5, 0.4], sides=4, hint=(1, 0, 0)))
    F += part("pelvis", ellipsoid("body", bone_m, (0, 11.8 * s, 0), (3.0, 1.4 * s, 1.8), segs=7, rings=2))
    F += part("shoulders", tube("body", bone_m, [(-4.8, 22.4 * s, 0), (4.8, 22.4 * s, 0)], ra=[0.55, 0.55], sides=5, hint=(0, 1, 0)))
    F += part("neck", tube("head", bone_m, [(0, 22.4 * s, 0.3), (0, 25.0 * s, 0.3)], ra=[0.5, 0.45], sides=5, hint=(1, 0, 0)))
    for sx, leg in ((1, "leg_l"), (-1, "leg_r")):
        F += part(leg + "_b", tube(leg, bone_m, [(sx * 2.1, 11.4 * s, 0), (sx * 2.2, 6.0 * s, -0.2), (sx * 2.2, 1.0 * s, 0)], ra=[0.6, 0.55, 0.5], sides=5, hint=(1, 0, 0)))
        F += part(leg + "_knee", ellipsoid(leg, bone_m, (sx * 2.2, 6.0 * s, -0.4), (0.85, 0.85, 0.85), segs=5, rings=1))
        F += part(leg + "_foot", box(leg, bone_m, (sx * 2.2, 0.5 * s, -1.0), (1.8, 1.0, 3.6), taper=(0.8, 0.8)))
    for sx, arm, fore in ((1, "left_arm", "left_forearm"), (-1, "right_arm", "right_forearm")):
        F += part(arm, tube(arm, bone_m, [(sx * 5.0, 22.2 * s, 0), (sx * 5.8, 16.6 * s, 0)], ra=[0.55, 0.5], sides=5, hint=(0, 0, 1)))
        F += part(arm + "_elbow", ellipsoid(fore, bone_m, (sx * 5.8, 16.6 * s, 0), (0.75, 0.75, 0.75), segs=5, rings=1))
        F += part(fore, tube(fore, bone_m, [(sx * 5.8, 16.6 * s, 0), (sx * 6.0, 11.4 * s, -0.4)], ra=[0.5, 0.45], sides=5, hint=(0, 0, 1)))
        F += part(fore + "_hand", ellipsoid(fore, bone_m, (sx * 6.0, 10.6 * s, -0.5), (0.9, 1.1, 0.8), segs=5, rings=1))
    return F


def skull(F, bone_m="bone", y=27.4, s=1.0):
    F += part("skull", ellipsoid("head", bone_m, (0, y * s, 0), (3.0, 3.1, 3.3), segs=8, rings=3))
    F += part("jaw", box("head", bone_m, (0, (y - 3.2) * s, -1.2), (3.6, 1.4, 3.0), taper=(0.85, 0.8)))
    F += part("sockets", box("head", "dark", (0, (y - 0.2) * s, -3.05), (4.0, 1.4, 0.5)))
    return F


# ---------------------------------------------------------------- 一案だけ回す
def run1(spec, build, pal, bones, poses, hint=128):
    OUT.mkdir(parents=True, exist_ok=True)
    r = build_option(spec["id"], "a", build, pal, bones, hint)
    print(spec["id"], "tris", r["tris"], "atlas", r["size"], "buried", r["buried"])
    png = OUT / f'{spec["id"]}.png'
    spec = dict(spec)
    spec.setdefault("labels", [spec.get("short", spec["id"])])
    spec.setdefault("pw", 560)
    sheet(spec, [r], bones, poses, png)
    return r


# ---------------------------------------------------------------- 光る材質 (目の穴・灯の炎・熾火)。材質名 → 色。面の画素をその色にして光らせる
GLOW = {"glow_amber": (255, 196, 84), "glow_blue": (140, 210, 255), "glow_pale": (200, 236, 255), "glow_red": (255, 96, 70)}
_paint0 = ck.paint


def _paint_glow(faces, size, P, N, M, pal, eyes=()):
    atlas, glow = _paint0(faces, size, P, N, M, pal, eyes)
    owned = M >= 0
    fi = np.where(owned, M, 0)
    mats = np.array([f.mat for f in faces])
    for name, rgb in GLOW.items():
        ids = np.nonzero(mats == name)[0]
        if len(ids):
            sel = np.isin(fi, ids) & owned
            atlas = atlas.copy()
            atlas[sel, :3] = rgb
            atlas[sel, 3] = 255
            glow = glow | sel
    return atlas, glow


ck.paint = _paint_glow
