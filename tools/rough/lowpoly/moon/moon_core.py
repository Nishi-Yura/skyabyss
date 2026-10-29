# -*- coding: utf-8 -*-
"""月の核 (moon_core) 低ポリ 一品一案。月の奥の主 (docs/design/46 の 4.7)。
時間を縛る核。光る大きな珠の内に、時計の針のような三日月の目。珠のまわりを三つの銀の輪 (傾きの違う天球儀の輪) が回り、
輪には星の刻み。珠の上下に銀の尖った軸、下には根のように垂れる光の筋 (月の芯へ時を流す管)。宙に浮く。
骨: root > core (珠) > eye / ring_a / ring_b / ring_c / spire_t / spire_b。珠の高さ 3 ブロックの上 (床から 10 に浮かべる)。
"""
import math

import numpy as np
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from mk import *  # noqa

CY = 40.0
BONES = {"root": (None, (0, 0, 0)), "core": ("root", (0, CY, 0)), "eye": ("core", (0, CY, -9)),
         "ring_a": ("core", (0, CY, 0)), "ring_b": ("core", (0, CY, 0)), "ring_c": ("core", (0, CY, 0)),
         "spire_t": ("core", (0, CY + 12, 0)), "spire_b": ("core", (0, CY - 12, 0))}
ORB = [(110, 140, 200), (170, 200, 245), (220, 236, 255), (250, 252, 255)]
PAL = make_pal({
    "orb": (ORB, 0.8, dict(crack=0.06, noise=0.12, min=1)),
    "silver": (SILVER, 0.68, dict(crack=0.04, noise=0.12)),
    "shadow": (SHADOW, 0.5, dict(crack=0.0, noise=0.06, min=0)),
    "star": (STAR, 0.88, dict(crack=0.0, noise=0.05, min=1)),
    "basalt": (BASALT, 0.6, dict(crack=0.14, noise=0.18)),
}, glow=["orb", "star"])


def ring(bone, r, tilt_x, tilt_z, width, n=28):
    """継ぎ目のない環 (2026-10-29: 筒で輪を作ると始まりと終わりの口が重なり、入れるときの口塞ぎで ring_a の面が内向きに裏返っていた)。"""
    import lpkit
    pts = []
    for i in range(n):
        a = 2 * math.pi * i / n
        x, y, z = math.cos(a) * r, 0.0, math.sin(a) * r
        # 傾ける (x 軸まわり、そのあと z 軸まわり)
        y, z = y * math.cos(tilt_x) - z * math.sin(tilt_x), y * math.sin(tilt_x) + z * math.cos(tilt_x)
        x, y = x * math.cos(tilt_z) - y * math.sin(tilt_z), x * math.sin(tilt_z) + y * math.cos(tilt_z)
        pts.append((x, CY + y, z))
    P = [np.asarray(q, float) for q in pts]
    c = np.array([0.0, CY, 0.0])
    sides = 4
    rings = []
    for i in range(n):
        t = P[(i + 1) % n] - P[i - 1]
        t /= np.linalg.norm(t)
        out = P[i] - c
        out -= np.dot(out, t) * t
        out /= np.linalg.norm(out)
        up = np.cross(t, out)
        rings.append([P[i] + width * 0.45 * math.cos(math.radians(45 + 90 * k)) * out + width * math.sin(math.radians(45 + 90 * k)) * up
                      for k in range(sides)])
    faces = []
    for i in range(n):
        j = (i + 1) % n
        for k in range(sides):
            quad = [rings[i][k], rings[i][(k + 1) % sides], rings[j][(k + 1) % sides], rings[j][k]]
            f = lpkit._mk(quad, bone, "silver", (P[i] + P[j]) / 2)
            if f is not None:
                faces.append(f)
    return faces


def build(k):
    F = []
    # 珠: 光る大きな珠、表に星の粒、前に影のくぼみと三日月の目
    F += part("orb", ellipsoid("core", "orb", (0, CY, 0), (9.0, 9.0, 9.0), segs=16, rings=10))
    F += part("socket", ellipsoid("eye", "shadow", (0, CY, -7.6), (4.4, 4.4, 2.0), segs=12, rings=4))
    pts = []
    for i in range(11):
        a = math.radians(-120 + i * 240 / 10)
        pts.append((math.sin(a) * 3.0, CY + math.cos(a) * 3.0, -9.4))
    F += part("crescent", tube("eye", "star", pts, ra=[0.3, 0.7, 1.0, 1.2, 1.3, 1.3, 1.3, 1.2, 1.0, 0.7, 0.3], rb=0.45, sides=4, hint=(0, 0, 1),
                               caps=(True, True)))
    # 針: 目の真ん中から上へ細い銀の針
    F += part("hand", tube("eye", "silver", [(0, CY, -9.6), (0.2, CY + 2.6, -9.6)], ra=[0.35, 0.1], sides=4, hint=(0, 0, 1)))
    # 三つの輪 (天球儀): 大きさと傾きを変える。輪に星の刻み
    F += part("ring_a", ring("ring_a", 14.0, math.radians(15), math.radians(0), 0.9))
    # ring_a の留め軸: 珠から輪へ左右に細い銀の軸 (傾きの浅い輪はどこにも触れず、入れるときの付け根の処理で形が折れていた。2026-10-29)
    for sx in (-1, 1):
        F += part(f"ring_a_pin{sx}", tube("ring_a", "silver", [(sx * 8.2, CY, 0), (sx * 14.0, CY, 0)], ra=0.45, sides=4, hint=(0, 1, 0), caps=(True, True)))
    F += part("ring_b", ring("ring_b", 16.5, math.radians(70), math.radians(25), 0.8))
    F += part("ring_c", ring("ring_c", 19.0, math.radians(-40), math.radians(-60), 0.7))
    for bone, r, tx, tz, n in (("ring_a", 14.0, 15, 0, 8), ("ring_b", 16.5, 70, 25, 8), ("ring_c", 19.0, -40, -60, 10)):
        for i in range(n):
            a = 2 * math.pi * i / n
            x, y, z = math.cos(a) * r, 0.0, math.sin(a) * r
            t1, t2 = math.radians(tx), math.radians(tz)
            y, z = y * math.cos(t1) - z * math.sin(t1), y * math.sin(t1) + z * math.cos(t1)
            x, y = x * math.cos(t2) - y * math.sin(t2), x * math.sin(t2) + y * math.cos(t2)
            F += part(f"{bone}_star{i}", ellipsoid(bone, "star", (x, CY + y, z), (0.9, 0.9, 0.9), segs=6, rings=3))
    # 上下の軸: 銀の尖り。上は星の冠、下は光の根
    F += part("spire_t", spike("spire_t", "silver", (0, CY + 8.4, 0), (0, 1, 0), 9.0, 2.2, sides=6, rings=3))
    F += part("crown", ellipsoid("spire_t", "star", (0, CY + 17.8, 0), (1.2, 1.2, 1.2), segs=6, rings=3))
    F += part("spire_b", spike("spire_b", "silver", (0, CY - 8.4, 0), (0, -1, 0), 7.0, 2.0, sides=6, rings=3))
    for i in range(5):
        a = 2 * math.pi * i / 5
        x, z = math.cos(a) * 3.0, math.sin(a) * 3.0
        F += part(f"root{i}", tube("spire_b", "orb", [(x * 0.6, CY - 7.0, z * 0.6), (x * 1.4, CY - 12.0, z * 1.4), (x * 1.0, CY - 17.0, z * 1.0),
                                                       (x * 0.4, CY - 21.0 - i % 2 * 2, z * 0.4)], ra=[0.8, 0.6, 0.4, 0.1], sides=4, hint=(1, 0, 0)))
    return F, []


POSES = {
    "idle": {},
    "spin": {"ring_a": (0, 60, 0), "ring_b": (30, 40, 0), "ring_c": (0, -50, 20)},
    "rewind": {"ring_a": (0, -90, 0), "ring_b": (-40, -60, 0), "ring_c": (0, 80, -30), "eye": (0, 0, -60)},
}

SPEC = dict(
    id="moon_core", short="月の核", kind="月の奥の主 一品一案 (46 の 4.7)",
    title="月の核 (moon_core) — 時を縛る天球儀の珠・低ポリ + ドット絵 一案",
    sub=["段 1: 四つの月の欠片が回って盾になる。段 2: 時計の輪が縮みきると体力を巻き戻す (削り切れば崩れる)。段 3: 満月を落とす (前の自分たち四人の円だけが無事)。",
         "光る大きな珠に三日月の目と針、傾きの違う三つの銀の輪 (星の刻み)、上下の銀の軸、下に垂れる光の根。床から 10 に浮く。"],
    labels=["月の核"],
    rows=[("前", "idle", 180, 8, 480, (0, 40, 0), 7.0),
          ("前 3/4", "spin", 212, 12, 480, (0, 40, 0), 7.0),
          ("巻き戻し", "rewind", 200, 6, 480, (0, 40, 0), 7.0),
          ("後ろ 3/4", "idle", 32, 20, 480, (0, 40, 0), 7.0)],
    options={"a": dict(name="天球儀の珠", desc="光る珠に三日月の目と時計の針。傾きの違う三つの銀の輪が回り、上下に銀の軸、下に光の根。")},
    extra="月の言葉 (月の光・銀・星の光)。骨 8 本。",
    budget="章の主 1600〜2800", pw=560,
    later="段 2 = 輪の一つが逆に回り、珠に割れ目。段 3 = 輪が外れて床のまわりを回り、珠が満月の白に。",
)

if __name__ == "__main__":
    run_one(SPEC, build, PAL, BONES, POSES, hint=256)
