# -*- coding: utf-8 -*-
"""鐘の亡者 (bell_mourner) 低ポリ 一品一案。鐘の島の敵 (docs/design/50 の 4.2.1)。
前の都の人の抜け殻。色あせた漆喰色の頭巾と外套、顔は空ろ (奥に朝の光の目が二つ)、胸に割れた青銅の鐘を両手で抱える。
自分の鐘を鳴らすと、まわりの鐘雲がほどける (振り上げて鳴らす動き)。少し前かがみ。身長 1.9 ブロック。
骨: root > body > head / arm_l / arm_r / leg_l / leg_r / bell。
"""
import math
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from mk import *  # noqa

BONES = {"root": (None, (0, 0, 0)), "body": ("root", (0, 14, 0)), "head": ("body", (0, 26, -2.0)),
         "arm_l": ("body", (-3.4, 23, 0)), "arm_r": ("body", (3.4, 23, 0)), "leg_l": ("body", (-1.4, 12, 0)), "leg_r": ("body", (1.4, 12, 0)),
         "bell": ("body", (0, 18, -4.2))}
PAL = make_pal({
    "plaster": (PLASTER, 0.55, dict(crack=0.08, noise=0.2)),
    "robe": (ROBE, 0.5, dict(crack=0.05, noise=0.2)),
    "hollow": (HOLLOW, 0.4, dict(crack=0.0, noise=0.04, min=0)),
    "bronze": (BRONZE, 0.66, dict(crack=0.1, noise=0.18)),
    "verdi": (VERDI, 0.55, dict(crack=0.06, noise=0.22)),
    "dawn": (DAWN, 0.85, dict(crack=0.0, noise=0.05, min=1)),
}, glow=["dawn"])


def build(k):
    F = []
    # 外套 (少し前かがみ: 上ほど前へ)
    rings = [ring(3.0, 3.4, -3.0, 3.0, n=10), ring(8.0, 3.2, -2.9, 2.8, n=10), ring(14.0, 2.8, -2.6, 2.4, n=10, zc=-0.4),
             ring(20.0, 3.3, -2.9, 2.6, n=10, zc=-1.3), ring(24.0, 3.5, -2.8, 2.4, n=10, zc=-2.0), ring(25.6, 2.2, -1.9, 1.6, n=10, zc=-2.2)]
    F += part("robe", loft("body", "plaster", rings, caps=("robe", "plaster")))
    for i in range(7):
        a = i / 7 * 2 * math.pi + 0.2
        F += part(f"tat{i}", spike("body", "plaster", (math.cos(a) * 3.3, 3.4, math.sin(a) * 2.9), (math.cos(a) * 0.3, -1, math.sin(a) * 0.3), 2.0, 0.7, sides=4, rings=1))
    # 帯 (腰の縄)
    F += part("belt", tube("body", "robe", [(math.cos(a) * 3.1, 14.2, math.sin(a) * 2.7 - 0.3) for a in [i / 9 * 2 * math.pi for i in range(10)]],
                           ra=0.35, sides=4, hint=(0, 1, 0)))
    # 脚 (裾の下にすね)
    for s, b in ((-1, "leg_l"), (1, "leg_r")):
        F += part(f"leg{s}", tube(b, "robe", [(s * 1.4, 12, 0), (s * 1.5, 1.2, 0.2)], ra=[0.9, 0.6], sides=6, hint=(1, 0, 0)))
        F += part(f"foot{s}", ellipsoid(b, "robe", (s * 1.5, 0.6, -0.9), (0.7, 0.55, 1.3), segs=6, rings=2))
    # 頭巾と空ろな顔
    hood = [ring(25.0, 2.6, -2.4, 2.2, n=10, zc=-2.2), ring(28.0, 2.6, -2.5, 2.2, n=10, zc=-2.6), ring(30.6, 1.8, -1.4, 1.8, n=10, zc=-2.2),
            ring(31.8, 0.7, 0.0, 1.1, n=10, zc=-1.6)]
    F += part("hood", loft("head", "plaster", hood, caps=("hollow", "plaster")))
    F += part("face", ellipsoid("head", "hollow", (0, 27.6, -4.6), (1.6, 1.9, 0.7), segs=8, rings=3))
    # 空ろの奥の朝の光の目
    for s_ in (-1, 1):
        F += part(f"eye{s_}", ellipsoid("head", "dawn", (s_ * 0.65, 28.0, -5.15), (0.32, 0.26, 0.2), segs=5, rings=2))
    # 腕: 肩から前へ、鐘を下から抱える
    for s, b in ((-1, "arm_l"), (1, "arm_r")):
        F += part(f"up{s}", tube(b, "plaster", [(s * 3.4, 23, 0), (s * 3.6, 19.5, -1.8)], ra=[1.0, 0.8], sides=6, hint=(1, 0, 0)))
        F += part(f"fo{s}", tube(b, "plaster", [(s * 3.6, 19.5, -1.8), (s * 2.6, 16.4, -4.0)], ra=[0.8, 0.6], sides=6, hint=(1, 0, 0)))
        F += part(f"hand{s}", ellipsoid(b, "hollow", (s * 2.5, 16.0, -4.2), (0.6, 0.6, 0.7), segs=6, rings=2))
    # 割れた鐘 (口が下)
    bell = [ring(16.0, 3.0, -7.2, -1.2, n=12), ring(16.7, 2.7, -6.9, -1.5, n=12), ring(18.5, 2.2, -6.4, -2.0, n=12), ring(20.5, 1.8, -6.0, -2.4, n=12),
            ring(21.6, 1.0, -5.0, -3.4, n=12)]
    F += part("bellbody", loft("bell", "bronze", bell, caps=("hollow", "bronze")))
    F += part("crown", ellipsoid("bell", "bronze", (0, 22.2, -4.2), (0.7, 0.6, 0.7), segs=6, rings=2))
    # 鐘の肩の緑青の帯 (鐘の面に沿う細い輪)
    F += part("verdband", tube("bell", "verdi", [(math.cos(a) * 1.95, 20.3, -4.2 + math.sin(a) * 1.95) for a in [i / 12 * 2 * math.pi for i in range(13)]],
                               ra=0.3, sides=4, hint=(0, 1, 0)))
    # 割れ目 (正面に縦の黒い筋と欠けた口)
    F += part("crack", tube("bell", "hollow", [(0.4, 21.0, -6.25), (-0.2, 19.0, -6.6), (0.5, 17.0, -7.0), (0.0, 16.0, -7.25)], ra=0.22, sides=4, hint=(0, 0, 1)))
    F += part("clapper", ellipsoid("bell", "bronze", (0, 15.6, -4.2), (0.6, 0.6, 0.6), segs=5, rings=2))
    return F, []


POSES = {
    "idle": {},
    "ring": {"arm_l": (-120, 0, -10), "arm_r": (-120, 0, 10), "bell": (-20, 0, 0), "body": (8, 0, 0), "head": (-15, 0, 0)},
}

SPEC = dict(
    id="bell_mourner", short="鐘の亡者", kind="鐘の島の敵 一品一案 (50 の 4.2.1)",
    title="鐘の亡者 (bell_mourner) — 割れた鐘を抱く前の都の人の抜け殻・低ポリ + ドット絵 一案",
    sub=["自分の鐘を振り上げて鳴らすと、まわりの鐘雲がほどける (1 秒の予告)。倒すと止む。",
         "色あせた漆喰色の頭巾と外套、空ろな顔に朝の光の目。胸に割れた青銅の鐘。身長 1.9 ブロック。"],
    labels=["鐘の亡者"],
    rows=[("前 3/4", "idle", 212, 10, 300, (0, 16, 0), 8.0),
          ("鐘を振り上げる", "ring", 200, 8, 300, (0, 16, 0), 8.0),
          ("横", "idle", 270, 4, 300, (0, 16, 0), 8.0),
          ("後ろ 3/4", "idle", 32, 12, 300, (0, 16, 0), 8.0)],
    options={"a": dict(name="割れた鐘を抱く亡者", desc="漆喰色の頭巾と外套、空ろな顔、胸に割れた青銅の鐘 (緑青の斑と縦の割れ目)。")},
    extra="雲海の言葉 (古い漆喰・鐘の青銅・朝の光)。骨 8 本。光るのは目だけ。",
    budget="中の敵 600〜1000", pw=420,
    later="二段目 = 鐘の割れ目から朝の光が漏れる。三段目 = 鐘が二つ (背にも)。",
)

if __name__ == "__main__":
    run_one(SPEC, build, PAL, BONES, POSES, hint=256)
