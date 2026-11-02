# -*- coding: utf-8 -*-
"""落ち羽 (fallen_plume) 低ポリ 一品一案。約束の島の敵 (docs/design/50 の 4.2.4)。
巡礼者の翼から抜け落ちた羽が、ひとりでに浮かぶ。長さ 2.2 ブロックの白い羽。軸は金、軸の上の半分に朝の光が通う。羽の根 (羽柄) は下へ尖る。
まわりを小さな羽根の矢 3 本が回る。狙った人へ向くと横に倒れ、羽根の矢を一列に撃つ (地面に光の筋の予告)。
骨: root > body > quills。
"""
import math
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from mk import *  # noqa

BONES = {"root": (None, (0, 0, 0)), "body": ("root", (0, 20, 0)), "quills": ("body", (0, 22, 0))}
PAL = make_pal({
    "vane": (CLOUDW, 0.62, dict(crack=0.0, noise=0.14)),
    "vane2": (CLOUDW, 0.48, dict(crack=0.0, noise=0.2)),
    "gold": (GOLDW, 0.7, dict(crack=0.02, noise=0.1)),
    "dawn": (DAWN, 0.85, dict(crack=0.0, noise=0.05, min=1)),
}, glow=["dawn"])


def vane(F, name, side, y0, y1, w, mat, tilt=0.0):
    """羽の片側 (平たい葉の形)。side = -1 / 1。根から先へ幅が w の形に変わる"""
    n = len(w)
    pts = []
    for i in range(n):
        t = i / (n - 1)
        y = y0 + (y1 - y0) * t
        pts.append((side * (w[i] * 0.5 + 0.35), y, tilt * w[i] * 0.3))
    F += part(name, tube("body", mat, pts, ra=0.3, rb=[x * 0.5 + 0.2 for x in w], sides=4, hint=(0, 0, 1)))


def build(k):
    F = []
    # 軸 (金。根は下へ尖る羽柄、先は細い)
    F += part("rachis", tube("body", "gold", [(0, 9.0, 0), (0, 20.0, 0), (0, 32.0, 0.2), (0, 42.0, 0.8)], ra=[0.75, 0.6, 0.45, 0.15], sides=6, hint=(1, 0, 0)))
    F += part("calamus", spike("body", "gold", (0, 9.4, 0), (0, -1, 0), 7.5, 0.85, sides=6, rings=3))
    # 羽の面: 三つに割れた片側ずつ (割れ目で羽らしく)
    for s in (-1, 1):
        vane(F, f"low{s}", s, 11.0, 21.0, [1.0, 3.6, 4.6, 4.2], "vane2", tilt=-s * 0.2)
        vane(F, f"mid{s}", s, 21.4, 33.0, [4.0, 5.2, 5.0, 3.8], "vane", tilt=s * 0.15)
        vane(F, f"tip{s}", s, 33.4, 42.0, [3.4, 2.8, 1.6, 0.4], "vane")
        # 羽の縁の金の線 (先の半分)
        F += part(f"edge{s}", tube("body", "gold", [(s * 3.1, 26.0, -0.25), (s * 3.0, 33.0, -0.25), (s * 1.8, 39.0, -0.2)], ra=0.18, rb=0.3, sides=3, hint=(0, 0, 1)))
    # 軸に通う朝の光 (前と後ろ)
    for z in (-0.55, 0.55):
        F += part(f"glow{z}", tube("body", "dawn", [(0, 22.0, z), (0, 31.0, z * 1.1), (0, 39.0, z * 1.2 + 0.4)], ra=0.2, rb=0.35, sides=3, hint=(0, 0, 1)))
    # まわりを回る羽根の矢 3 本 (下を向く小さな羽)
    for i in range(3):
        a = i * math.pi * 2 / 3
        cx, cz = math.cos(a) * 7.0, math.sin(a) * 7.0
        F += part(f"q{i}", spike("quills", "gold", (cx, 24.0, cz), (0, -1, 0), 5.0, 0.35, sides=4, rings=2))
        F += part(f"qv{i}", tube("quills", "vane", [(cx, 24.0, cz), (cx, 27.4, cz), (cx, 29.4, cz)], ra=0.2, rb=[0.9, 1.0, 0.25], sides=4,
                                  hint=(-math.sin(a), 0, math.cos(a))))
    return F, []


POSES = {
    "idle": {},
    "volley": {"body": (-75, 0, 0), "quills": (0, 60, 0)},
}

SPEC = dict(
    id="fallen_plume", short="落ち羽", kind="約束の島の敵 一品一案 (50 の 4.2.4)",
    title="落ち羽 (fallen_plume) — 巡礼者の翼から抜け落ちた羽・低ポリ + ドット絵 一案",
    sub=["宙に浮き、狙った人へ横に倒れて羽根の矢を一列に撃つ (地面に光の筋 1 秒の予告)。",
         "長さ 2.2 ブロックの白い羽。金の軸に朝の光が通い、根は下へ尖る。小さな羽根の矢 3 本が回る。"],
    labels=["落ち羽"],
    rows=[("前", "idle", 180, 6, 300, (0, 24, 0), 7.0),
          ("前 3/4", "idle", 212, 10, 300, (0, 24, 0), 7.0),
          ("撃つ", "volley", 250, 8, 300, (0, 24, 0), 7.0),
          ("横", "idle", 270, 4, 300, (0, 24, 0), 7.0)],
    options={"a": dict(name="浮かぶ大きな羽", desc="白い羽が一枚、根を下にして浮かぶ。金の軸と縁、軸に朝の光。まわりを小さな羽根の矢が回る。")},
    extra="雲海の言葉 (雲の白・淡い金・朝の光)。骨 3 本。",
    budget="小さな敵 400〜800", pw=420,
    later="二段目 = 羽の縁の金が広がる。三段目 = 回る矢が 5 本に。",
)

if __name__ == "__main__":
    run_one(SPEC, build, PAL, BONES, POSES, hint=256)
