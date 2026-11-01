# -*- coding: utf-8 -*-
"""塔の側の目 (tower_eye) 低ポリ 一品一案 案 C「二つ割りの眼」。あいだの塔の側の目 (docs/design/54 の 2.5 の Eyes of War)。
右半分は白い大理石と金 (天)、左半分は黒い石と紫 (淵)。真ん中で割れて、その線に天秤の印が座る (塔の主と同じ二つの側)。
籠の柱も右は大理石・左は黒い石。鎧戸は右が大理石、左が黒い石 (閉じると二つの板が前で合う)。
小 (1 倍) と大 (1.8 倍) は同じ形を拡大する。宙に浮く。
骨は灯守の目 (lamp_eye) と同じ: root > cage > eye / shutter_l / shutter_r (アニメ lamp_eye.animation.json がそのまま動く)。
"""
import math
import sys
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).resolve().parent))
from mk import *  # noqa
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from lpkit import _mk  # noqa

BONES = {"root": (None, (0, 0, 0)), "cage": ("root", (0, 22, 0)), "eye": ("cage", (0, 22, 0)),
          "shutter_l": ("cage", (0, 22, 0)), "shutter_r": ("cage", (0, 22, 0))}
MARBLE = [(170, 166, 156), (206, 202, 192), (230, 228, 220), (246, 244, 238)]
STONE = [(22, 20, 28), (38, 36, 46), (58, 56, 68), (84, 82, 96)]
VIOLET = [(120, 70, 210), (160, 110, 240), (200, 160, 255), (235, 215, 255)]
PAL = make_pal({
    "marble": (MARBLE, 0.62, dict(crack=0.03, noise=0.14)),
    "stone": (STONE, 0.5, dict(crack=0.10, noise=0.2)),
    "gold": (GOLDW, 0.72, dict(crack=0.03, noise=0.12)),
    "violet": (VIOLET, 0.85, dict(crack=0.0, noise=0.05, min=1)),
    "hollow": (HOLLOW, 0.4, dict(crack=0.0, noise=0.04, min=0)),
}, glow=["violet"])

R = 9.6  # 籠の半径


def half_loft(bone, mat, specs, side, n=8, caps=(True, True)):
    """回転体の半分 (side = +1 は x が正 = 右、-1 は左)。specs = [(y, rx, rz)] の半だ円の輪を loft でつなぐ。
    輪の両端は x = 0 の割れ目で閉じる。割れ目の両側の半分を別の材質にすると二つ割りになる。"""
    rings = []
    base = -90.0 if side > 0 else 90.0
    for (y, rx, rz) in specs:
        pts = []
        for k in range(n + 1):
            a = math.radians(base + 180.0 * k / n)
            pts.append(np.array([rx * math.cos(a), y, rz * math.sin(a)]))
        rings.append(pts)
    # 割れ目 (x = 0) の両側を別々に閉じる: 輪の端どうし (割れ目) はつながず、二つの半分が割れ目で合わさる
    rings = [np.array(r, float) for r in rings]
    cen = [r.mean(0) for r in rings]
    k = len(rings[0])
    faces = []
    for i in range(len(rings) - 1):
        ref = (cen[i] + cen[i + 1]) / 2
        for j in range(k - 1):
            quad = [rings[i][j], rings[i][j + 1], rings[i + 1][j + 1], rings[i + 1][j]]
            f = _mk(quad, bone, mat, ref)
            if f is not None:
                faces.append(f)
    for idx, cap in zip((0, len(rings) - 1), caps):
        if cap:
            other = cen[1] if idx == 0 else cen[-2]
            f = _mk(list(rings[idx]), bone, cap if isinstance(cap, str) else mat, other)
            if f is not None:
                faces.append(f)
    return faces


def sphere_specs(c, r, lat=5):
    out = []
    for i in range(lat):
        t = math.radians(-80 + 160 * i / (lat - 1))
        out.append((c[1] + r * math.sin(t), r * math.cos(t), r * math.cos(t)))
    return out


def hoop(bone, mat, y, r, w=0.6, n=16):
    return tube(bone, mat, [(math.cos(a) * r, y, math.sin(a) * r) for a in [i / n * 2 * math.pi for i in range(n + 1)]], ra=w, sides=4, hint=(0, 1, 0))


def build(k):
    F = []
    # 台: 右は大理石、左は黒い石 (割れ目が台の真ん中を通る)
    dish = [(6.0, 3.0, 3.0), (7.2, 7.4, 7.4), (8.6, R + 0.8, R + 0.8), (9.6, R + 0.9, R + 0.9)]
    F += part("baseR", half_loft("cage", "marble", dish, 1, caps=(True, "gold")))
    F += part("baseL", half_loft("cage", "stone", dish, -1, caps=(True, "gold")))
    F += part("baserim", hoop("cage", "gold", 9.6, R + 1.1, 0.5))
    # 屋根: 右半分は大理石、左半分は黒い石 (天と淵の二段の屋根)
    roof = [(34.6, R + 1.0, R + 1.0), (35.6, R + 0.6, R + 0.6), (38.6, 7.0, 7.0), (41.4, 3.2, 3.2), (42.4, 1.6, 1.6)]
    F += part("roofR", half_loft("cage", "marble", roof, 1, caps=("gold", "marble")))
    F += part("roofL", half_loft("cage", "stone", roof, -1, caps=("gold", "stone")))
    F += part("roofrim", hoop("cage", "gold", 35.0, R + 1.1, 0.55))
    F += part("finial", spike("cage", "gold", (0, 42.0, 0), (0, 1, 0), 5.0, 0.9, sides=4, rings=2, hint=(1, 0, 0)))
    # 柱 6 本 (右は大理石、左は黒い石。根に金の座)
    for i in range(6):
        a = i / 6 * 2 * math.pi + math.pi / 6
        x, z = math.cos(a) * R, math.sin(a) * R
        m = "marble" if x > 0 else "stone"
        F += part(f"post{i}", tube("cage", m, [(x, 9.4, z), (x * 1.03, 22.0, z * 1.03), (x, 34.8, z)], ra=[0.9, 0.7, 0.9], sides=4, hint=(0, 1, 0)))
        F += part(f"capb{i}", ellipsoid("cage", "gold", (x, 9.6, z), (1.5, 1.0, 1.5), segs=6, rings=2))
    # 目: 右半分は白い大理石、左半分は黒い石。割れ目の真ん中に金の虹彩と天秤の梁
    F += part("ballR", half_loft("eye", "marble", sphere_specs((0, 22.0, 0), 6.8), 1, n=8, caps=(True, True)))
    F += part("ballL", half_loft("eye", "stone", sphere_specs((0, 22.0, 0), 6.8), -1, n=8, caps=(True, True)))
    F += part("iris", ellipsoid("eye", "gold", (0, 22.0, -6.1), (4.0, 4.0, 1.2), segs=12, rings=3))
    F += part("pupil", ellipsoid("eye", "violet", (0, 22.0, -7.1), (1.2, 2.4, 0.6), segs=8, rings=2))
    F += part("beam", tube("eye", "gold", [(-3.6, 22.0, -7.3), (3.6, 22.0, -7.3)], ra=0.45, sides=4, hint=(0, 1, 0)))
    F += part("panl", ellipsoid("eye", "stone", (-3.6, 19.6, -7.3), (1.1, 0.5, 1.1), segs=6, rings=2))
    F += part("panr", ellipsoid("eye", "marble", (3.6, 19.6, -7.3), (1.1, 0.5, 1.1), segs=6, rings=2))
    # 鎧戸: 左 (shutter_l) は黒い石、右 (shutter_r) は大理石。金の帯
    for side, bone_name, mid, mat in ((-1, "shutter_l", math.radians(150), "stone"), (1, "shutter_r", math.radians(30), "marble")):
        arc = [(-0.95 + 1.9 * t / 8) for t in range(9)]
        pts = [(math.cos(mid + d) * (R - 1.6), 22.0, math.sin(mid + d) * (R - 1.6)) for d in arc]
        F += part(f"shutter{side}", tube(bone_name, mat, pts, ra=6.0, rb=0.3, sides=4, hint=(0, 1, 0)))
        F += part(f"shband{side}", tube(bone_name, "gold", [(math.cos(mid + d) * (R - 1.25), 22.0, math.sin(mid + d) * (R - 1.25)) for d in arc],
                                         ra=0.4, rb=0.25, sides=4, hint=(0, 1, 0)))
        F += part(f"handle{side}", ellipsoid(bone_name, "gold", (math.cos(mid) * (R - 1.1), 22.0, math.sin(mid) * (R - 1.1)), (0.7, 0.7, 0.7), segs=5, rings=2))
    # 台の下の金の鎖と紫の玉
    for i in range(3):
        a = i / 3 * 2 * math.pi + 0.4
        x, z = math.cos(a) * 3.0, math.sin(a) * 3.0
        F += part(f"chain{i}", tube("cage", "gold", [(x, 6.2, z), (x * 1.1, 3.0, z * 1.1), (x * 1.15, 0.8, z * 1.15)], ra=0.35, sides=4, hint=(1, 0, 0)))
        F += part(f"gem{i}", ellipsoid("cage", "violet", (x * 1.15, 0.0, z * 1.15), (1.0, 1.4, 1.0), segs=6, rings=3))
    return F, []


POSES = {
    "idle": {},
    "closed": {"shutter_l": (0, -100, 0), "shutter_r": (0, 100, 0)},
    "stare": {"eye": (8, 0, 0), "shutter_l": (0, 15, 0), "shutter_r": (0, -15, 0)},
}

SPEC = dict(
    id="tower_eye_c", short="塔の目 C", kind="あいだの塔 側の目 一品一案 (54 の 2.5)",
    title="塔の側の目 案 C 二つ割りの眼 (tower_eye) — 右は白い大理石、左は黒い石。割れ目に天秤・低ポリ + ドット絵",
    sub=["橋の左右の台に据わる。小 (1 倍) 4 つと大 (1.8 倍) 2 つは同じ形。凝視・閉じる・被弾・死。",
         "右半分が白い大理石と金 (天)、左半分が黒い石と紫 (淵)。割れ目の真ん中に金の虹彩と天秤の梁。鎧戸も二つに分かれる。"],
    labels=["塔の側の目 案 C"],
    rows=[("前 3/4", "idle", 205, 8, 420, (0, 24, 0), 6.6),
          ("閉じる", "closed", 190, 6, 420, (0, 24, 0), 6.6),
          ("凝視", "stare", 225, -8, 420, (0, 24, 0), 6.6),
          ("横", "idle", 270, 4, 420, (0, 24, 0), 6.6)],
    options={"a": dict(name="二つ割りの眼", desc="右は白い大理石と金、左は黒い石と紫。柱・屋根・鎧戸も右左で分け、割れ目に天秤の梁。")},
    extra="天と淵の二つ割り (右 = 大理石と金、左 = 黒い石と紫)。骨 5 本 (灯守の目と同じ名前)。光るのは瞳の紫。",
    budget="目 1400〜1800", pw=440,
    later="二段目 = 割れ目の金が太る。三段目 = 天秤の梁が二つの色の皿を吊る。",
)

if __name__ == "__main__":
    run_one(SPEC, build, PAL, BONES, POSES, hint=256)
