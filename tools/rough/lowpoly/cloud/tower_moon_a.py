# -*- coding: utf-8 -*-
"""落ちる月 (tower_moon) 低ポリ 一品一案 案 A「半分の月」。あいだの塔の落ちる月 (docs/design/54 の 2.5 の New Sun)。
主が体力 50% と 20% で呼ぶ。橋の北から南へ進みながら大きくなる (進みと大きさは TowerFight)。
月の右半分は白い大理石 (天)、左半分は黒い石に紫の割れ目 (淵)。真ん中を金の輪が縦に通る (天秤の割れ目)。
前に金の天秤の梁 (逆に回る)。上へ金の鎖が三本伸びて途切れる (落ちてくる月の尾)。
骨は巡礼者の月 (pilgrim_moon) と同じ: root > moon / chains / clock (アニメ pilgrim_moon.animation.json がそのまま動く)。
"""
import math
import sys
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).resolve().parent))
from mk import *  # noqa
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from lpkit import _mk  # noqa

C = (0, 36, 0)
BONES = {"root": (None, (0, 0, 0)), "moon": ("root", C), "chains": ("root", C), "clock": ("root", (0, 36, -20))}
MARBLE = [(170, 166, 156), (206, 202, 192), (230, 228, 220), (246, 244, 238)]
STONE = [(22, 20, 28), (38, 36, 46), (58, 56, 68), (84, 82, 96)]
VIOLET = [(120, 70, 210), (160, 110, 240), (200, 160, 255), (235, 215, 255)]
PAL = make_pal({
    "marble": (MARBLE, 0.62, dict(crack=0.03, noise=0.14)),
    "stone": (STONE, 0.5, dict(crack=0.10, noise=0.2)),
    "basalt": (STONE, 0.4, dict(crack=0.16, noise=0.26)),
    "gold": (GOLDW, 0.72, dict(crack=0.03, noise=0.12)),
    "violet": (VIOLET, 0.85, dict(crack=0.0, noise=0.05, min=1)),
}, glow=["violet"])

RM = 16.0  # 月の半径


def half_loft(bone, mat, specs, side, n=10, caps=(True, True)):
    """回転体の半分 (side = +1 は x が正、-1 は x が負)。specs = [(y, rx, rz)] の半だ円の輪を loft でつなぐ。"""
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


def sphere_specs(c, r, lat=9, lim=84):
    out = []
    for i in range(lat):
        t = math.radians(-lim + 2 * lim * i / (lat - 1))
        out.append((c[1] + r * math.sin(t), r * math.cos(t), r * math.cos(t)))
    return out


def on_moon(az, el, rad):
    """月の中心から、方位 az (度・正 = 右)・高度 el (度・正 = 上) の点。前は -Z"""
    a, e = math.radians(az), math.radians(el)
    return (rad * math.cos(e) * math.sin(a), C[1] + rad * math.sin(e), -rad * math.cos(e) * math.cos(a))


def link(bone, mat, a, b, r=0.55, n=10):
    """鎖の一つの輪 (a と b の間に立つ小さな輪)。縦の鎖は xy の輪、それ以外は yz の輪"""
    mid = [(a[i] + b[i]) / 2 for i in range(3)]
    R = r * 1.7
    ts = [i / n * 2 * math.pi for i in range(n + 1)]
    if abs(b[1] - a[1]) > 0.5:
        pts = [(mid[0] + math.cos(t) * R, mid[1] + math.sin(t) * R, mid[2]) for t in ts]
    else:
        pts = [(mid[0], mid[1] + math.cos(t) * R, mid[2] + math.sin(t) * R) for t in ts]
    return tube(bone, mat, pts, ra=r * 0.5, sides=4, hint=(0, 0, 1))


def build(k):
    F = []
    # 月: 右半分は白い大理石、左半分は黒い石 (割れ目は中央の金の輪)
    F += part("moonR", half_loft("moon", "marble", sphere_specs(C, RM), 1, n=10))
    F += part("moonL", half_loft("moon", "stone", sphere_specs(C, RM), -1, n=10))
    # 左の半分の割れ目の窪み (黒い石の暗い皿)
    for i, (az, el, s) in enumerate(((-40, 20, 2.6), (-18, -30, 2.0), (-55, -10, 1.6), (-28, 50, 1.4))):
        x, y, z = on_moon(az, el, RM - 0.2)
        F += part(f"crater{i}", ellipsoid("moon", "basalt", (x, y, z), (s, s * 0.8, s * 0.35), segs=6, rings=2))
    # 紫の割れ目 (左の半分の表面を走る)
    for i, pts in enumerate((((-30, 10), (-24, 4), (-14, 2), (-6, -4)), ((-60, 30), (-50, 22), (-42, 26)))):
        F += part(f"crack{i}", tube("moon", "violet", [on_moon(az, el, RM + 0.3) for az, el in pts], ra=0.5, rb=0.7, sides=3, hint=(0, 0, 1)))
    # 中央の金の輪 (天と淵の割れ目。縦に一周)
    F += part("seam", tube("moon", "gold", [(0, C[1] + math.sin(t) * (RM + 0.5), math.cos(t) * (RM + 0.5)) for t in [i / 16 * 2 * math.pi for i in range(17)]],
                           ra=0.8, sides=4, hint=(1, 0, 0)))
    # 上へ伸びる金の鎖 (三本。落ちてくる月の尾。途切れて終わる)
    for j, az in enumerate((-30, 0, 30)):
        x0, z0 = math.sin(math.radians(az)) * 2.0, -math.cos(math.radians(az)) * 2.0
        pts = [(x0, C[1] + RM - 1.0, z0)]
        for s in range(1, 5):
            pts.append((x0 * (1 + 0.5 * s), pts[0][1] + s * 6.0, z0 * (1 + 0.5 * s)))
        for s in range(len(pts) - 1):
            F += part(f"ch{j}{s}", link("chains", "gold", pts[s], pts[s + 1]))
        F += part(f"tip{j}", ellipsoid("chains", "violet", (pts[-1][0], pts[-1][1] + 2.0, pts[-1][2]), (1.1, 1.1, 1.1), segs=6, rings=2))
    # 前の天秤の輪 (逆に回る。金の輪・梁・二つの皿・中心の紫)
    CR = 9.0
    F += part("cring", tube("clock", "gold", [(math.cos(math.radians(a)) * CR, 36 + math.sin(math.radians(a)) * CR, -20) for a in range(0, 361, 22)],
                            ra=0.6, rb=0.6, sides=4, hint=(0, 0, 1)))
    F += part("beam", tube("clock", "gold", [(-8.0, 36, -20.2), (8.0, 36, -20.2)], ra=0.45, sides=4, hint=(0, 1, 0)))
    for s in (-1, 1):
        F += part(f"hang{s}", tube("clock", "gold", [(s * 8.0, 36, -20.2), (s * 7.5, 30.5, -20.2)], ra=0.25, sides=3, hint=(0, 0, 1)))
        F += part(f"pan{s}", ellipsoid("clock", "gold" if s > 0 else "stone", (s * 7.5, 29.6, -20.2), (1.8, 0.7, 1.8), segs=6, rings=2))
    F += part("hub", ellipsoid("clock", "violet", (0, 36, -20.6), (1.1, 1.1, 0.6), segs=6, rings=2))
    return F, []


POSES = {
    "idle": {},
    "pull": {"clock": (0, 0, 120), "moon": (0, 40, 0)},
    "fall": {"moon": (-25, 0, 0), "chains": (-10, 0, 0)},
}

SPEC = dict(
    id="tower_moon_a", short="落ちる月 A", kind="あいだの塔 落ちる月 一品一案 (54 の 2.5)",
    title="落ちる月 案 A 半分の月 (tower_moon) — 白い大理石と黒い石を金の輪で割った月・低ポリ + ドット絵",
    sub=["主が体力 50% と 20% で呼ぶ。橋の北から南へ進みながら大きくなる (1.0 倍 → 2.2 倍)。南の端に着く前に壊す。",
         "右半分は白い大理石 (天)、左半分は黒い石に紫の割れ目 (淵)。中央の金の輪が縦に通る。前に逆に回る天秤の輪。上へ金の鎖の尾。"],
    labels=["落ちる月 案 A"],
    rows=[("前", "idle", 180, 4, 520, (0, 40, 0), 6.0),
          ("前 3/4", "idle", 212, 8, 520, (0, 40, 0), 6.0),
          ("横", "idle", 270, 4, 520, (0, 40, 0), 6.0),
          ("引き", "pull", 200, 6, 520, (0, 40, 0), 6.0)],
    options={"a": dict(name="半分の月", desc="右は白い大理石、左は黒い石と紫の割れ目。中央の金の輪、前に逆に回る天秤の輪、上へ伸びる金の鎖。")},
    extra="天と淵を割った月 (大理石と金 / 黒い石と紫)。骨 4 本 (巡礼者の月と同じ名前)。光るのは割れ目と天秤の芯。",
    budget="月 1800〜2400", pw=440,
    later="二段目 = 割れ目が広がり紫が漏れる。三段目 = 金の輪が赤く熱を持つ。",
)

if __name__ == "__main__":
    run_one(SPEC, build, PAL, BONES, POSES, hint=256)
