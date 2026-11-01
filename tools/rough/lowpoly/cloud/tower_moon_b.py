# -*- coding: utf-8 -*-
"""落ちる月 (tower_moon) 低ポリ 一品一案 案 B「欠けた月」。あいだの塔の落ちる月 (docs/design/54 の 2.5 の New Sun)。
主が体力 50% と 20% で呼ぶ。橋の北から南へ進みながら大きくなる (進みと大きさは TowerFight)。
白い大理石の月。右の縁が黒い石の欠片に食われ、そこから新しい太陽の光 (金の弧) が漏れる。
前に金の日輪 (十二の光の角。逆に回る)。上へ光の尾が四本流れて、紫の先で途切れる。
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
MARBLE2 = [(120, 116, 108), (160, 156, 146), (196, 192, 182), (224, 220, 210)]
STONE = [(22, 20, 28), (38, 36, 46), (58, 56, 68), (84, 82, 96)]
VIOLET = [(120, 70, 210), (160, 110, 240), (200, 160, 255), (235, 215, 255)]
PAL = make_pal({
    "marble": (MARBLE, 0.62, dict(crack=0.03, noise=0.14)),
    "marble2": (MARBLE2, 0.5, dict(crack=0.1, noise=0.22)),
    "stone": (STONE, 0.5, dict(crack=0.12, noise=0.22)),
    "gold": (GOLDW, 0.8, dict(crack=0.02, noise=0.1)),
    "violet": (VIOLET, 0.85, dict(crack=0.0, noise=0.05, min=1)),
}, glow=["violet", "gold"])

RM = 16.0


def on_moon(az, el, rad):
    """月の中心から、方位 az (度・正 = 右)・高度 el (度・正 = 上) の点。前は -Z"""
    a, e = math.radians(az), math.radians(el)
    return (rad * math.cos(e) * math.sin(a), C[1] + rad * math.sin(e), -rad * math.cos(e) * math.cos(a))


def build(k):
    F = []
    # 月の本体 (白い大理石。少し灰の斑)
    F += part("moon", ellipsoid("moon", "marble", C, (RM, RM, RM), segs=24, rings=14))
    for i, (az, el, s) in enumerate(((-50, 30, 2.4), (-20, -35, 2.0), (-70, -10, 1.6), (-15, 55, 1.3), (-95, 40, 1.8), (-40, -60, 1.5))):
        x, y, z = on_moon(az, el, RM - 0.4)
        F += part(f"crater{i}", ellipsoid("moon", "marble2", (x, y, z), (s, s * 0.8, s * 0.4), segs=6, rings=2))
    # 右の縁を食う黒い石の欠片 (三つ。月の縁から外へ出っ張る)
    for i, (az, el, s) in enumerate(((62, 18, 4.2), (88, -8, 3.4), (110, 30, 2.8), (72, -34, 2.6), (125, -12, 2.2))):
        x, y, z = on_moon(az, el, RM + 0.6)
        F += part(f"shard{i}", ellipsoid("moon", "stone", (x, y, z), (s, s * 0.9, s * 0.6), segs=8, rings=4))
    # 新しい太陽の光が漏れる弧 (左の縁を流れる金。光る)
    arc = [on_moon(az, -4 + 10 * math.sin(math.radians(az)), RM + 0.9) for az in range(-125, -29, 8)]
    arc_r = [0.5 + 0.6 * math.sin(math.pi * i / (len(arc) - 1)) for i in range(len(arc))]
    F += part("arc", tube("moon", "gold", arc, ra=arc_r, sides=4, hint=(0, 1, 0)))
    # 紫の割れ目 (月の表面の筋)
    F += part("crack", tube("moon", "violet", [on_moon(az, el, RM + 0.25) for az, el in ((-80, 20), (-70, 14), (-62, 18), (-52, 10))],
                            ra=0.5, rb=0.7, sides=3, hint=(0, 0, 1)))
    # 上へ流れる光の尾 (四本。根は太く、先は細く紫)
    for j, az in enumerate((-36, -12, 12, 36)):
        a = math.radians(az)
        x0, z0 = math.sin(a) * 6.0, -math.cos(a) * 6.0
        top = C[1] + RM - 1.5
        pts = [(x0 * 0.4, top, z0 * 0.4), (x0 * 0.7, top + 9.0, z0 * 0.7), (x0 * 1.0, top + 18.0, z0 * 1.0), (x0 * 1.3, top + 26.0, z0 * 1.3)]
        F += part(f"tail{j}", tube("chains", "gold", pts, ra=[1.5, 1.0, 0.6, 0.25], sides=4, hint=(1, 0, 0)))
        x1, y1, z1 = pts[1]
        F += part(f"tailring{j}", tube("chains", "gold", [(x1 + math.cos(t) * 1.8, y1, z1 + math.sin(t) * 1.8) for t in [i / 10 * 2 * math.pi for i in range(11)]],
                                       ra=0.4, sides=4, hint=(0, 1, 0)))
        F += part(f"tip{j}", ellipsoid("chains", "violet", (pts[-1][0], pts[-1][1] + 2.0, pts[-1][2]), (0.9, 1.4, 0.9), segs=6, rings=2))
    # 前の日輪 (金の輪と十二の光の角。逆に回る)
    CR = 7.0
    F += part("ring", tube("clock", "gold", [(math.cos(math.radians(a)) * CR, 36 + math.sin(math.radians(a)) * CR, -20) for a in range(0, 361, 15)],
                           ra=0.7, rb=0.7, sides=4, hint=(0, 0, 1)))
    for i in range(12):
        a = math.radians(i * 30)
        p0 = (math.cos(a) * (CR + 0.6), 36 + math.sin(a) * (CR + 0.6), -20)
        p1 = (math.cos(a) * (CR + 4.6), 36 + math.sin(a) * (CR + 4.6), -20)
        F += part(f"ray{i}", tube("clock", "violet" if i % 3 == 0 else "gold", [p0, p1], ra=[0.8, 0.2], sides=4, hint=(0, 0, 1)))
    F += part("hub", ellipsoid("clock", "violet", (0, 36, -20.6), (1.6, 1.6, 0.7), segs=6, rings=2))
    return F, []


POSES = {
    "idle": {},
    "pull": {"clock": (0, 0, 120), "moon": (0, 40, 0)},
    "fall": {"moon": (-25, 0, 0), "chains": (-10, 0, 0)},
}

SPEC = dict(
    id="tower_moon_b", short="落ちる月 B", kind="あいだの塔 落ちる月 一品一案 (54 の 2.5)",
    title="落ちる月 案 B 欠けた月 (tower_moon) — 白い大理石の月を黒い欠片が食い、新しい太陽の光が漏れる・低ポリ + ドット絵",
    sub=["主が体力 50% と 20% で呼ぶ。橋の北から南へ進みながら大きくなる (1.0 倍 → 2.2 倍)。南の端に着く前に壊す。",
         "白い大理石の月。右の縁に黒い石の欠片、左の縁から金の光の弧。前に十二の光の角の日輪 (逆に回る)。上へ光の尾が四本。"],
    labels=["落ちる月 案 B"],
    rows=[("前", "idle", 180, 4, 520, (0, 40, 0), 6.0),
          ("前 3/4", "idle", 212, 8, 520, (0, 40, 0), 6.0),
          ("横", "idle", 270, 4, 520, (0, 40, 0), 6.0),
          ("引き", "pull", 200, 6, 520, (0, 40, 0), 6.0)],
    options={"a": dict(name="欠けた月", desc="白い大理石の月、右の縁は黒い石の欠片に食われ、左の縁から金の光の弧。前に十二の光の角の日輪、上へ光の尾。")},
    extra="天の白い月 (大理石と金)。黒い欠片は淵。骨 4 本 (巡礼者の月と同じ名前)。光るのは金の弧・日輪・紫の先。",
    budget="月 1800〜2400", pw=440,
    later="二段目 = 欠片が増え弧が太る。三段目 = 日輪の光の角が逆に回り続ける。",
)

if __name__ == "__main__":
    run_one(SPEC, build, PAL, BONES, POSES, hint=256)
