# -*- coding: utf-8 -*-
"""月の欠片の盾 (core_shard) 低ポリ 一品一案。月の核のまわりを回る盾 (docs/design/46 の 4.7)。
月の面から剝がれた大きな三日月の岩。外の面は月の砂と玄武岩の岩肌、内の面 (核を向く側) は磨かれた銀の鏡で、縁に星の刻み。
骨: root > shard。高さ 3 ブロックほど。核が位置と向きを毎 tick 決める (動きは持たない)。
"""
import math
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from mk import *  # noqa

CY = 24.0
BONES = {"root": (None, (0, 0, 0)), "shard": ("root", (0, CY, 0))}
PAL = make_pal({
    "dust": (DUST, 0.62, dict(crack=0.12, noise=0.24)),
    "basalt": (BASALT, 0.6, dict(crack=0.18, noise=0.2)),
    "silver": (SILVER, 0.7, dict(crack=0.02, noise=0.08)),
    "star": (STAR, 0.86, dict(crack=0.0, noise=0.05, min=1)),
}, glow=["star"])


def build(k):
    F = []
    # 三日月の岩: 縦の弧に沿って厚みのある断面を loft (外は岩、内の面は銀)
    rings = []
    n = 13
    for i in range(n):
        t = i / (n - 1)
        a = math.radians(-75 + t * 150)
        y = CY + math.sin(a) * 20.0
        zc = -math.cos(a) * 9.0 + 4.0
        th = 1.0 + 4.2 * math.sin(t * math.pi)
        w = 1.5 + 7.0 * math.sin(t * math.pi)
        # 断面: 外 (z+) は岩、内 (z-) は平たい鏡
        ring_ = []
        for j in range(8):
            b = 2 * math.pi * j / 8
            ring_.append((math.cos(b) * w, y, zc + math.sin(b) * th * (0.7 if math.sin(b) < 0 else 1.0)))
        rings.append(ring_)
    F += part("rock", loft("shard", "dust", rings, caps=(True, True)))
    # 外の面の玄武岩の瘤 (外の面の上に散らす)
    rnd = np.random.default_rng(5)
    for i in range(9):
        t = 0.12 + 0.76 * rnd.random()
        a = math.radians(-75 + t * 150)
        y = CY + math.sin(a) * 20.0
        th = 1.0 + 4.2 * math.sin(t * math.pi)
        w = 1.5 + 7.0 * math.sin(t * math.pi)
        x = rnd.uniform(-0.6, 0.6) * w
        zc = -math.cos(a) * 9.0 + 4.0 + th * math.sqrt(max(0.0, 1 - (x / w) ** 2)) - 0.3
        r = rnd.uniform(1.2, 2.2)
        F += part(f"knob{i}", ellipsoid("shard", "basalt", (x, y, zc), (r, r * 0.9, r * 0.7), segs=6, rings=3))
    # 内の面の銀の鏡 (内の面に沿う薄い板) と真ん中の星の刻み
    mrings = []
    stars = []
    for i in range(n):
        t = i / (n - 1)
        a = math.radians(-72 + t * 144)
        y = CY + math.sin(a) * 19.4
        th = 1.0 + 4.2 * math.sin(t * math.pi)
        w = (1.5 + 7.0 * math.sin(t * math.pi)) * 0.78
        z0 = -math.cos(a) * 9.0 + 4.0 - th * 0.7 * 0.55
        mrings.append([(-w, y, z0 - 0.5), (w, y, z0 - 0.5), (w, y, z0 + 0.6), (-w, y, z0 + 0.6)])
        if i % 2 == 1:
            stars.append((0.0, y, z0 - 0.7))
    F += part("mirror", loft("shard", "silver", mrings, caps=(True, True)))
    for j, p in enumerate(stars):
        F += part(f"star{j}", ellipsoid("shard", "star", p, (0.7, 0.7, 0.5), segs=5, rings=3))
    return F, []


POSES = {"idle": {}}

SPEC = dict(
    id="core_shard", short="月の欠片の盾", kind="月の核の盾 一品一案 (46 の 4.7)",
    title="月の欠片の盾 (core_shard) — 核を守って回る三日月の岩・低ポリ + ドット絵 一案",
    sub=["段 1 は四つ、段 2 は二つが核のまわりを回る。欠片の向きから打った傷は通らない。欠片の薙ぎで外へ広がって戻る。",
         "大きな三日月の岩。外は月の砂と玄武岩の岩肌、核を向く内の面は磨いた銀の鏡に星の刻み。"],
    labels=["月の欠片の盾"],
    rows=[("外 (前)", "idle", 0, 6, 420, (0, 24, 0), 8.0),
          ("横", "idle", 270, 4, 420, (0, 24, 0), 8.0),
          ("内 (核の側)", "idle", 180, 6, 420, (0, 24, 0), 8.0),
          ("3/4", "idle", 220, 14, 420, (0, 24, 0), 8.0)],
    options={"a": dict(name="三日月の岩", desc="外は月の砂と玄武岩の岩肌、内は銀の鏡に星の刻み。")},
    extra="月の言葉 (月の砂・玄武岩・銀・星の光)。骨 1 本。",
    budget="付きもの 300〜700", pw=560,
    later="段 2 で砕けて二つになる (同じ模型)。",
)

if __name__ == "__main__":
    run_one(SPEC, build, PAL, BONES, POSES, hint=256)
