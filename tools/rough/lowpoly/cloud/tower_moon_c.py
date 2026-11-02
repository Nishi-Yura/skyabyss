# -*- coding: utf-8 -*-
"""落ちる月 (tower_moon) 低ポリ 一品一案 案 C「天秤の月」。あいだの塔の落ちる月 (docs/design/54 の 2.5 の New Sun)。
主が体力 50% と 20% で呼ぶ。橋の北から南へ進みながら大きくなる (進みと大きさは TowerFight)。
上半分は白い大理石 (天)、下半分は黒い石 (淵)。赤道を金の輪が一周して二つを分ける。
天から金の鎖が二本、月の頂で合わさって上へ伸びる (天秤の吊り手)。前に金の天秤の梁 (逆に回る)。二つの皿は大理石と黒い石。
骨は巡礼者の月 (pilgrim_moon) と同じ: root > moon / chains / clock (アニメ pilgrim_moon.animation.json がそのまま動く)。
"""
import math
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from mk import *  # noqa

C = (0, 36, 0)
BONES = {"root": (None, (0, 0, 0)), "moon": ("root", C), "chains": ("root", C), "clock": ("root", (0, 36, -20))}
MARBLE = [(170, 166, 156), (206, 202, 192), (230, 228, 220), (246, 244, 238)]
STONE = [(22, 20, 28), (38, 36, 46), (58, 56, 68), (84, 82, 96)]
VIOLET = [(120, 70, 210), (160, 110, 240), (200, 160, 255), (235, 215, 255)]
PAL = make_pal({
    "marble": (MARBLE, 0.62, dict(crack=0.03, noise=0.14)),
    "stone": (STONE, 0.5, dict(crack=0.12, noise=0.22)),
    "gold": (GOLDW, 0.8, dict(crack=0.02, noise=0.1)),
    "violet": (VIOLET, 0.85, dict(crack=0.0, noise=0.05, min=1)),
}, glow=["violet"])

RM = 16.0


def link(bone, mat, a, b, r=0.55, n=10):
    """鎖の一つの輪 (a と b の間。縦は xy の輪、それ以外は yz の輪)"""
    mid = [(a[i] + b[i]) / 2 for i in range(3)]
    R = r * 1.7
    ts = [i / n * 2 * math.pi for i in range(n + 1)]
    if abs(b[1] - a[1]) > 0.5:
        pts = [(mid[0] + math.cos(t) * R, mid[1] + math.sin(t) * R, mid[2]) for t in ts]
    else:
        pts = [(mid[0], mid[1] + math.cos(t) * R, mid[2] + math.sin(t) * R) for t in ts]
    return tube(bone, mat, pts, ra=r * 0.5, sides=4, hint=(0, 0, 1))


def on_moon(az, el, rad):
    """月の中心から、方位 az (度・正 = 右)・高度 el (度・正 = 上) の点。前は -Z"""
    a, e = math.radians(az), math.radians(el)
    return (rad * math.cos(e) * math.sin(a), C[1] + rad * math.sin(e), -rad * math.cos(e) * math.cos(a))


def build(k):
    F = []
    # 月: 下半分は黒い石、上半分は白い大理石 (緯度の輪を loft でつなぎ、輪ごとに材質を分ける)
    lats = [-80 + 16 * i for i in range(11)]
    rings = []
    for lt in lats:
        t = math.radians(lt)
        rho, y = RM * math.cos(t), C[1] + RM * math.sin(t)
        rings.append([np.array([C[0] + rho * math.sin(math.radians(a)), y, C[2] - rho * math.cos(math.radians(a))]) for a in range(0, 360, 18)])
    mats = ["stone"] * 5 + ["marble"] * 5
    F += part("moon", loft("moon", "stone", rings, caps=("stone", "marble"), mats=mats))
    # 赤道の金の輪
    F += part("equator", tube("moon", "gold", [(C[0] + math.sin(math.radians(a)) * (RM + 0.5), C[1], C[2] - math.cos(math.radians(a)) * (RM + 0.5))
                                              for a in range(0, 361, 15)], ra=0.8, sides=4, hint=(0, 1, 0)))
    # 白い大理石の上半分の筋 (灰の斑)
    for i, (az, el, s) in enumerate(((30, 40, 2.0), (-45, 58, 1.6), (100, 30, 1.8), (-120, 46, 1.4))):
        a, e = math.radians(az), math.radians(el)
        x, y, z = (RM - 0.3) * math.cos(e) * math.sin(a), C[1] + (RM - 0.3) * math.sin(e), -(RM - 0.3) * math.cos(e) * math.cos(a)
        F += part(f"spot{i}", ellipsoid("moon", "marble", (x, y, z), (s, s * 0.8, s * 0.35), segs=6, rings=2))
    # 黒い石の下半分の割れ目 (紫)
    F += part("crack", tube("moon", "violet", [on_moon(az, el, RM + 0.3) for az, el in ((-40, -20), (-26, -32), (-10, -26), (6, -40), (20, -30))],
                            ra=0.5, rb=0.7, sides=3, hint=(0, 0, 1)))
    # 天からの金の鎖二本 (月の頂で合わさり、上の一点へ伸びる吊り手)
    apex = (0.0, C[1] + RM + 22.0, 0.0)
    for j, sx in enumerate((-1, 1)):
        top = (sx * 6.0, C[1] + RM - 1.0, 0.0)
        mid = (sx * 3.0, C[1] + RM + 11.0, 0.0)
        pts = [top, mid, apex]
        for s in range(len(pts) - 1):
            a, b = pts[s], pts[s + 1]
            for q in range(3):
                t0 = q / 3.0
                t1 = (q + 1) / 3.0
                pa = tuple(a[i] + (b[i] - a[i]) * t0 for i in range(3))
                pb = tuple(a[i] + (b[i] - a[i]) * t1 for i in range(3))
                F += part(f"lk{j}{s}{q}", link("chains", "gold", pa, pb))
    F += part("apexend", ellipsoid("chains", "violet", (0, apex[1] + 2.4, 0), (1.3, 1.3, 1.3), segs=6, rings=2))
    # 前の天秤 (梁・鎖・二つの皿。逆に回る)
    CR = 9.0
    F += part("cring", tube("clock", "gold", [(math.cos(math.radians(a)) * CR, 36 + math.sin(math.radians(a)) * CR, -20) for a in range(0, 361, 22)],
                            ra=0.6, rb=0.6, sides=4, hint=(0, 0, 1)))
    F += part("beam", tube("clock", "gold", [(-10.0, 36, -20.2), (10.0, 36, -20.2)], ra=0.55, sides=4, hint=(0, 1, 0)))
    for s in (-1, 1):
        pan_y = 28.0
        F += part(f"strl{s}", tube("clock", "gold", [(s * 10.0, 36, -20.2), (s * 9.5, pan_y + 3.0, -20.2), (s * 9.0, pan_y + 1.0, -20.2)], ra=0.3, sides=3, hint=(0, 0, 1)))
        F += part(f"pan{s}", loft("clock", "marble" if s > 0 else "stone",
                                  [[np.array([s * 9.0 + math.cos(a) * 2.6, pan_y - 0.2 - 0.4 * (q == 0), -20.2 + math.sin(a) * 2.6]) for a in
                                    [i / 8 * 2 * math.pi for i in range(8)]] for q in (0, 1)], caps=(True, False)))
    F += part("hub", ellipsoid("clock", "violet", (0, 36, -20.7), (1.2, 1.2, 0.6), segs=6, rings=2))
    return F, []


POSES = {
    "idle": {},
    "pull": {"clock": (0, 0, 120), "moon": (0, 40, 0)},
    "fall": {"moon": (-25, 0, 0), "chains": (-10, 0, 0)},
}

SPEC = dict(
    id="tower_moon_c", short="落ちる月 C", kind="あいだの塔 落ちる月 一品一案 (54 の 2.5)",
    title="落ちる月 案 C 天秤の月 (tower_moon) — 上は白い大理石、下は黒い石。金の赤道と天から吊られた天秤・低ポリ + ドット絵",
    sub=["主が体力 50% と 20% で呼ぶ。橋の北から南へ進みながら大きくなる (1.0 倍 → 2.2 倍)。南の端に着く前に壊す。",
         "上半分は白い大理石 (天)、下半分は黒い石 (淵)。赤道を金の輪が一周する。天から金の鎖が吊り、前に逆に回る天秤の梁と皿。"],
    labels=["落ちる月 案 C"],
    rows=[("前", "idle", 180, 4, 520, (0, 40, 0), 6.0),
          ("前 3/4", "idle", 212, 8, 520, (0, 40, 0), 6.0),
          ("横", "idle", 270, 4, 520, (0, 40, 0), 6.0),
          ("引き", "pull", 200, 6, 520, (0, 40, 0), 6.0)],
    options={"a": dict(name="天秤の月", desc="上は白い大理石、下は黒い石。赤道の金の輪。天から金の鎖が吊る天秤 (梁と二つの皿) が前で逆に回る。")},
    extra="天と淵を赤道で分けた月。骨 4 本 (巡礼者の月と同じ名前)。光るのは紫の割れ目と天秤の芯。",
    budget="月 1800〜2400", pw=440,
    later="二段目 = 赤道の輪が熱を持つ。三段目 = 天秤の梁が傾いて皿が落ちかける。",
)

if __name__ == "__main__":
    run_one(SPEC, build, PAL, BONES, POSES, hint=256)
