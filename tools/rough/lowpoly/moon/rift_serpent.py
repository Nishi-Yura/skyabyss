# -*- coding: utf-8 -*-
"""逆さ砂の蛇 (rift_serpent) 低ポリ 一品一案。下弦の淵の敵 (docs/design/46 の 4.6)。
割れた砂時計からこぼれた月の砂が蛇の形になったもの。地に傾いて埋もれた大きな銀の砂時計 (下の硝子の玉が割れている) から、
光る月の砂の胴がとぐろを巻いて立ち上がる。頭は砂を固めた三日月の顎 (上顎の縁に銀の欠片)、目は星の白二つ、
首のまわりに砂時計の割れた硝子の欠片が浮く。砂の礫を口から放つ。
骨: root > base (砂時計) > seg1 > seg2 > seg3 > head > jaw。高さ 2 ブロックほど。
"""
import math
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from mk import *  # noqa

HX = -4.0   # 胴の立つ所 (x)。砂時計は x -16〜-2 に横たわる
BONES = {"root": (None, (0, 0, 0)), "base": ("root", (-8, 4, 0)), "seg1": ("base", (HX + 1, 5, 0)), "seg2": ("seg1", (HX + 4, 13, -0.5)),
         "seg3": ("seg2", (HX + 3, 21, -1.0)), "head": ("seg3", (HX + 3, 28, -3.0)), "jaw": ("head", (HX + 3, 28.6, -5.5))}
SAND = [(96, 110, 140), (140, 156, 186), (184, 198, 222), (222, 230, 244)]
GLASS = [(40, 60, 96), (80, 112, 160), (140, 176, 220), (210, 230, 250)]
PAL = make_pal({
    "sand": (SAND, 0.66, dict(crack=0.03, noise=0.26)),
    "glow": ([(120, 150, 200), (176, 204, 240), (226, 238, 255), (250, 252, 255)], 0.75, dict(crack=0.0, noise=0.12, min=1)),
    "silver": (SILVER, 0.66, dict(crack=0.05, noise=0.14)),
    "glass": (GLASS, 0.6, dict(crack=0.08, noise=0.08)),
    "shadow": (SHADOW, 0.5, dict(crack=0.0, noise=0.06, min=0)),
    "star": (STAR, 0.85, dict(crack=0.0, noise=0.05, min=1)),
}, glow=["star", "glow"])


def build(k):
    F = []
    # 砂時計: 横に倒れて半ば砂に埋もれる。左右の銀の円い板と三本の柱、左の硝子の玉は無事、右の玉は割れて口を開ける
    # 左の板だけ残り、右の板は外れて砂に半ば埋もれて傾く
    F += part("plate_l", ellipsoid("base", "silver", (-16.0, 5.0, 0), (1.1, 6.2, 6.2), segs=12, rings=3))
    F += part("boss_l", ellipsoid("base", "silver", (-16.9, 5.0, 0), (0.8, 2.2, 2.2), segs=8, rings=2))
    F += part("plate_r", ellipsoid("base", "silver", (-1.0, 1.6, 7.6), (5.6, 1.0, 5.6), segs=12, rings=3))
    for i in range(3):
        a = math.radians(90 + i * 120)
        y, z = 5.0 + math.sin(a) * 5.0, math.cos(a) * 5.0
        F += part(f"post{i}", tube("base", "silver", [(-15.4, y, z), (-4.0 - i * 0.8, y, z)], ra=0.75, sides=5, hint=(0, 1, 0), caps=(True, True)))
    F += part("bulb_l", ellipsoid("base", "glass", (-12.0, 5.0, 0), (3.4, 3.8, 3.8), segs=10, rings=5))
    F += part("neck", tube("base", "glass", [(-9.0, 5.0, 0), (-7.6, 5.0, 0)], ra=1.2, sides=6, hint=(0, 1, 0), caps=(True, True)))
    # 右の玉は割れた殻の欠片が口の縁に残る
    for i in range(7):
        a = math.radians(i * 360 / 7 + 15)
        c = (-5.2, 5.0 + math.sin(a) * 3.4, math.cos(a) * 3.4)
        F += part(f"shard{i}", spike("base", "glass", c, (0.9, math.sin(a) * 0.5, math.cos(a) * 0.5), 2.2 + (i % 3) * 0.8, 0.9, sides=4, rings=1))
    F += part("pile", ellipsoid("base", "sand", (-8.0, 1.0, 0), (11.0, 2.4, 8.0), segs=14, rings=3))
    F += part("spill", ellipsoid("base", "sand", (-2.0, 1.6, 1.0), (6.0, 2.0, 5.0), segs=10, rings=3))
    # 胴: 割れた玉の口から外へ流れ出し、立ち上がって S の字に頭をもたげる砂の一本の筒 (下ほど太い)
    segs = [("seg1", [(-6.0, 5.0, 0), (HX + 1, 5.2, 0.4), (HX + 4.4, 7.6, 0.8), (HX + 5.0, 11.0, 0.2), (HX + 4.0, 13.4, -0.5)], [3.0, 3.3, 3.4, 3.2, 3.0]),
            ("seg2", [(HX + 4.0, 13.0, -0.5), (HX + 2.2, 16.4, 0.6), (HX + 2.4, 19.0, 0.2), (HX + 3.0, 21.4, -1.0)], [3.0, 2.8, 2.6, 2.5]),
            ("seg3", [(HX + 3.0, 21.0, -1.0), (HX + 4.0, 24.0, -1.4), (HX + 3.6, 26.6, -2.4), (HX + 3.0, 28.4, -3.2)], [2.5, 2.4, 2.3, 2.2])]
    for bone, pts, rr in segs:
        F += part("body" + bone, tube(bone, "sand", pts, ra=rr, sides=9, hint=(1, 0, 0), caps=(True, True)))
        m = pts[len(pts) // 2]
        F += part("band" + bone, ellipsoid(bone, "glow", m, (rr[len(pts) // 2] + 0.2, 0.55, rr[len(pts) // 2] + 0.2), segs=10, rings=2))
    # 頭: 三日月の形の上顎 (前へ尖る) と下顎、銀の角、星の目二つ
    hx = HX + 3.0
    F += part("skull", ellipsoid("head", "sand", (hx, 30.0, -5.0), (3.2, 2.6, 4.2), segs=10, rings=4))
    F += part("snout", spike("head", "sand", (hx, 29.8, -8.0), (0, -0.15, -1), 4.0, 2.2, sides=6, rings=2))
    F += part("jawb", spike("jaw", "sand", (hx, 27.8, -6.0), (0, -0.3, -1), 4.4, 1.8, sides=6, rings=2))
    F += part("mouth", ellipsoid("jaw", "shadow", (hx, 28.7, -7.0), (1.6, 0.6, 2.2), segs=7, rings=2))
    for sx in (-1, 1):
        F += part(f"eye{sx}", ellipsoid("head", "star", (hx + sx * 2.1, 31.0, -6.6), (0.7, 0.55, 0.7), segs=6, rings=3))
        pts = [(hx + sx * 2.8, 32.0, -2.6), (hx + sx * 3.2, 33.0, -0.4), (hx + sx * 2.6, 33.8, 1.8)]
        F += part(f"horn{sx}", tube("head", "silver", pts, ra=[0.7, 0.55, 0.1], sides=4, hint=(0, 1, 0)))
    # 首のまわりに浮く割れた硝子の欠片 4
    for i in range(4):
        a = math.radians(i * 90 + 45)
        c = (hx + math.cos(a) * 5.4, 23.0 + (i % 2) * 2.4, -1.4 + math.sin(a) * 5.4)
        F += part(f"float{i}", spike("seg3", "glass", c, (0.2, 1, -0.1), 1.8, 0.7, sides=4, rings=1) +
                  spike("seg3", "glass", c, (-0.2, -1, 0.1), 1.2, 0.7, sides=4, rings=1))
    return F, []


POSES = {
    "idle": {},
    "spit": {"seg2": (-10, 0, 0), "seg3": (-15, 0, 0), "head": (-10, 0, 0), "jaw": (30, 0, 0)},
    "sink": {"seg1": (40, 0, 0), "seg2": (40, 0, 0), "seg3": (30, 0, 0), "head": (20, 0, 0)},
}

SPEC = dict(
    id="rift_serpent", short="逆さ砂の蛇", kind="下弦の淵の敵 一品一案 (46 の 4.6)",
    title="逆さ砂の蛇 (rift_serpent) — 割れた砂時計からこぼれた月の砂の蛇・低ポリ + ドット絵 一案",
    sub=["6〜16 の獲物へ砂の礫 3 つ (飛び物。一瞬止めで止まる)。近づかれると砂に潜って離れる。倒れると 3 秒前の所から残像で一度立ち上がる。",
         "高さ 2 ブロック。横に倒れて半ば埋もれた銀の砂時計の割れた玉から、光る砂の胴が流れ出して立ち上がる。三日月の顎、星の目、浮く硝子の欠片。"],
    labels=["逆さ砂の蛇"],
    rows=[("前 3/4", "idle", 212, 10, 420, (-4, 16, 0), 8.0),
          ("横", "idle", 270, 4, 420, (-4, 16, 0), 8.0),
          ("礫を吐く", "spit", 225, 8, 420, (-4, 16, 0), 8.0),
          ("潜る", "sink", 32, 14, 420, (-4, 16, 0), 8.0)],
    options={"a": dict(name="砂時計の蛇", desc="横に倒れて埋もれた銀の砂時計の割れた玉から、光る月の砂の胴が立ち上がる。三日月の顎と銀の角、星の目、浮く硝子の欠片。")},
    extra="月の言葉 (月の砂・銀・硝子・星の光)。骨 7 本。",
    budget="中の敵 800〜1300", pw=560,
    later="二段目 = 上の玉も割れて砂が両方から立つ。三段目 = 胴が二本になる。",
)

if __name__ == "__main__":
    run_one(SPEC, build, PAL, BONES, POSES, hint=256)
