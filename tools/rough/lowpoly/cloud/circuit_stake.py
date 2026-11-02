# -*- coding: utf-8 -*-
"""淵の杭 (circuit_stake) 低ポリ 一品一案。巡り路の「壊す」課題で、土地の敵が打ち込んだ杭 (docs/design/54 の 3.1.1)。
高さ約 2.5 ブロック。黒い石の八角の杭が地面に斜めに刺さり、割れ目から淵の紫が漏れる。頭に淵の結晶の房、
胴に錆びた鎖が三巻き、根もとに骨の破片と小石の輪 (敵が積んだ)。打つと割れ目が広がり、結晶が欠ける (段で替える)。
どの土地でも同じ形 (淵の汚れなので土地の色を持たない)。骨: root > body > crown。
"""
import math
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from mk import *  # noqa

DARK = [(18, 16, 24), (32, 30, 40), (50, 48, 60), (72, 70, 84)]
VOID = [(110, 60, 200), (150, 100, 235), (195, 155, 255), (232, 214, 255)]
RUST = [(60, 34, 24), (96, 56, 34), (132, 82, 46), (164, 112, 66)]
BONE = [(150, 140, 120), (186, 176, 152), (214, 206, 184), (236, 230, 212)]
PEBBLE = [(70, 70, 74), (100, 100, 104), (130, 130, 132), (160, 160, 160)]

BONES = {"root": (None, (0, 0, 0)), "body": ("root", (0, 0, 0)), "crown": ("body", (0, 38, -2))}
PAL = make_pal({
    "stone": (DARK, 0.55, dict(crack=0.12, noise=0.22)),
    "void": (VOID, 0.85, dict(crack=0.0, noise=0.06, min=1)),
    "rust": (RUST, 0.6, dict(crack=0.05, noise=0.25)),
    "bone": (BONE, 0.68, dict(crack=0.06, noise=0.14)),
    "pebble": (PEBBLE, 0.6, dict(crack=0.04, noise=0.2)),
}, glow=["void"])

TILT = math.radians(8)


def lean(p):
    # 杭全体を前 (-Z) へ少し傾ける (打ち込まれた向き)
    x, y, z = p
    return (x, y * math.cos(TILT), z - y * math.sin(TILT))


def build(k):
    F = []
    # 杭の胴: 八角、下は地面へ尖りながら埋まり、上へ細る。割れ目の帯は紫
    rings = []
    for y, r in ((-6, 1.5), (-2, 5.0), (4, 6.2), (14, 5.6), (24, 4.8), (32, 4.0), (37, 2.6)):
        rings.append([lean(p) for p in ring(y, r, -r, r, n=8)])
    F += part("shaft", loft("body", "stone", rings, caps=(True, False)))
    # 割れ目 (胴の三か所から漏れる紫の筋。胴へ沈めて置く)
    for i, (a, y0, y1) in enumerate(((20, 6, 22), (150, 10, 30), (260, 3, 18))):
        rad = math.radians(a)
        pts = []
        for t in range(5):
            y = y0 + (y1 - y0) * t / 4
            r = 5.9 - 0.06 * (y - 4)
            jit = 0.6 * math.sin(t * 2.1 + i)
            pts.append(lean((math.cos(rad + jit * 0.1) * (r - 0.4), y, math.sin(rad + jit * 0.1) * (r - 0.4))))
        F += part(f"crack{i}", tube("body", "void", pts, ra=0.9, rb=0.6, sides=4, hint=(0, 0, 1)))
    # 頭の結晶の房 (中心の大きいものと、まわりに小さいもの)
    top = lean((0, 37, 0))
    F += part("crystal0", spike("crown", "void", (top[0], top[1] - 2, top[2]), (0.05, 1.0, -0.12), 11.0, 2.4, sides=5, rings=2))
    for i in range(5):
        a = math.radians(i * 72 + 18)
        d = (math.cos(a) * 0.55, 0.8, math.sin(a) * 0.55 - 0.1)
        F += part(f"crystal{i + 1}", spike("crown", "void", (top[0] + math.cos(a) * 1.6, top[1] - 2.5, top[2] + math.sin(a) * 1.6), d,
                                          5.0 + (i % 2) * 2.0, 1.2, sides=4, rings=2))
    # 錆びた鎖 (胴に三巻き。輪を連ねた筒で、胴へ半分沈める)
    for j, yb in enumerate((9.0, 17.0, 25.0)):
        r = 6.0 - 0.06 * (yb - 4) + 0.5
        pts = [lean((math.cos(math.radians(a)) * r, yb + math.sin(math.radians(a * 2)) * 0.7, math.sin(math.radians(a)) * r)) for a in range(0, 361, 30)]
        F += part(f"chain{j}", tube("body", "rust", pts, ra=0.8, rb=0.8, sides=4, hint=(0, 1, 0)))
    # 垂れた鎖の端 (前に一本)
    F += part("chain_end", tube("body", "rust", [lean((1.5, 9.0, -6.2)), lean((2.2, 4.0, -7.4)), (2.6, 0.6, -8.4)], ra=0.8, rb=0.7, sides=4, hint=(1, 0, 0)))
    # 根もと: 骨の破片 (地面へ斜めに刺す) と小石の輪
    for i in range(6):
        a = math.radians(i * 60 + 10)
        base = (math.cos(a) * 9.0, -1.0, math.sin(a) * 9.0)
        F += part(f"bone{i}", spike("root", "bone", base, (math.cos(a) * 0.35, 1.0, math.sin(a) * 0.35), 5.0 + (i % 3), 0.9, sides=4, rings=2))
    for i in range(10):
        a = math.radians(i * 36)
        F += part(f"pebble{i}", ellipsoid("root", "pebble", (math.cos(a) * 12.5, 0.4, math.sin(a) * 12.5), (1.8, 1.0, 1.6), segs=6, rings=2))
    return F, []


POSES = {
    "idle": {},
    "cracked": {"crown": (0, 25, 8)},
}

SPEC = dict(
    id="circuit_stake", short="淵の杭", kind="巡り路の「壊す」課題の的 一品一案 (54 の 3.1.1)",
    title="淵の杭 (circuit_stake) — 土地の敵が打ち込んだ、紫の割れ目の黒い杭・低ポリ + ドット絵 一案",
    sub=["黒い石の八角の杭が地面に斜めに刺さる。割れ目と頭の結晶の房が淵の紫に光り、錆びた鎖が三巻き。",
         "根もとに骨の破片と小石の輪。どの土地でも同じ形。高さ約 2.5 ブロック、打つと割れ目が広がる。"],
    labels=["淵の杭"],
    rows=[("前 3/4", "idle", 205, 8, 420, (0, 18, 0), 7.5),
          ("横", "idle", 270, 4, 420, (0, 18, 0), 7.5),
          ("後ろ 3/4", "idle", 30, 10, 420, (0, 18, 0), 7.5),
          ("割れかけ (頭が傾く)", "cracked", 215, 8, 420, (0, 18, 0), 7.5)],
    options={"a": dict(name="黒い石の杭", desc="八角の黒い石に紫の割れ目、結晶の房、錆びた鎖、根もとの骨と小石。")},
    extra="骨 3 本。光るのは割れ目と結晶。",
    budget="主 600〜1500", pw=400,
    later="段が進むと割れ目が増え、最後は結晶が砕けて紫の粒。",
)

if __name__ == "__main__":
    run_one(SPEC, build, PAL, BONES, POSES, hint=128)
