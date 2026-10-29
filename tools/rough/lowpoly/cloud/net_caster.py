# -*- coding: utf-8 -*-
"""網打ち (net_caster) 低ポリ 一品一案。灯の島の敵 (docs/design/50 の 4.2.2)。
前の浜の漁師の抜け殻。油引きの外套と鍔の広い雨帽、空ろな顔に朝の光の目。肩から濡れた網を垂らし、両手で網の縁を握る。外套の裾と網に海藻。
網を頭上で振り回し (予告 0.8 秒)、前の扇へ投げる。身長 1.9 ブロック。
骨: root > body > head / arm_l / arm_r / net / leg_l / leg_r。
"""
import math
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from mk import *  # noqa

BONES = {"root": (None, (0, 0, 0)), "body": ("root", (0, 13, 0)), "head": ("body", (0, 25, -1.0)),
         "arm_l": ("body", (-3.6, 23, 0)), "arm_r": ("body", (3.6, 23, 0)), "net": ("body", (0, 22, -2.5)),
         "leg_l": ("body", (-1.5, 12, 0)), "leg_r": ("body", (1.5, 12, 0))}
PAL = make_pal({
    "oil": (OILSKIN, 0.55, dict(crack=0.05, noise=0.2)),
    "weed": (SEAWEED, 0.5, dict(crack=0.0, noise=0.25)),
    "rope": (ROPE, 0.55, dict(crack=0.02, noise=0.18)),
    "hollow": (HOLLOW, 0.4, dict(crack=0.0, noise=0.04, min=0)),
    "dawn": (DAWN, 0.85, dict(crack=0.0, noise=0.05, min=1)),
    "plaster": (PLASTER, 0.55, dict(crack=0.08, noise=0.18)),
}, glow=["dawn"])


def build(k):
    F = []
    # 外套 (油引き。肩が張り、裾は広い)
    rings = [ring(2.5, 3.6, -3.2, 3.2, n=10), ring(8.0, 3.3, -3.0, 3.0, n=10), ring(14.0, 3.0, -2.8, 2.6, n=10, zc=-0.2),
             ring(20.0, 3.6, -3.0, 2.8, n=10, zc=-0.8), ring(23.6, 3.8, -2.8, 2.6, n=10, zc=-1.0), ring(25.2, 2.0, -1.6, 1.4, n=10, zc=-1.0)]
    F += part("coat", loft("body", "oil", rings, caps=("hollow", "oil")))
    # 外套の合わせ目のボタン (縄の留め)
    for i, y in enumerate((9.0, 13.5, 18.0)):
        F += part(f"toggle{i}", tube("body", "rope", [(-0.6, y, -3.1 + (0.2 if y > 15 else 0)), (0.6, y, -3.1 + (0.2 if y > 15 else 0))], ra=0.3, sides=4, hint=(0, 1, 0)))
    # 裾の海藻
    for i in range(6):
        a = i / 6 * 2 * math.pi + 0.5
        F += part(f"kelp{i}", tube("body", "weed", [(math.cos(a) * 3.4, 3.2, math.sin(a) * 3.0), (math.cos(a) * 3.6, 0.8, math.sin(a) * 3.2)],
                                   ra=[0.35, 0.15], rb=[0.7, 0.3], sides=4, hint=(0, 1, 0)))
    # 脚と長靴
    for s, b in ((-1, "leg_l"), (1, "leg_r")):
        F += part(f"leg{s}", tube(b, "oil", [(s * 1.5, 12, 0), (s * 1.6, 1.4, 0.1)], ra=[0.9, 0.75], sides=6, hint=(1, 0, 0)))
        F += part(f"boot{s}", ellipsoid(b, "hollow", (s * 1.6, 0.8, -0.7), (0.9, 0.8, 1.5), segs=6, rings=2))
    # 頭: 空ろな顔と、鍔の広い雨帽 (後ろが長い)
    F += part("face", ellipsoid("head", "hollow", (0, 26.6, -1.6), (1.7, 2.0, 1.6), segs=8, rings=3))
    for s in (-1, 1):
        F += part(f"eye{s}", ellipsoid("head", "dawn", (s * 0.65, 27.0, -3.05), (0.3, 0.24, 0.2), segs=5, rings=2))
    hat = [ring(28.0, 4.2, -3.8, 5.0, n=12, zc=-1.0), ring(28.6, 4.0, -3.6, 4.8, n=12, zc=-1.0), ring(29.0, 2.1, -1.9, 2.0, n=12, zc=-1.0),
           ring(31.0, 1.9, -1.7, 1.8, n=12, zc=-1.0), ring(31.8, 0.8, -0.7, 0.8, n=12, zc=-1.0)]
    F += part("hat", loft("head", "oil", hat, caps=("oil", "oil")))
    F += part("hatband", tube("head", "rope", [(math.cos(a) * 2.05, 29.3, -1.0 + math.sin(a) * 1.95) for a in [i / 12 * 2 * math.pi for i in range(13)]],
                              ra=0.3, sides=4, hint=(0, 1, 0)))
    # 腕: 両手で網の縁を胸の前に握る
    for s, b in ((-1, "arm_l"), (1, "arm_r")):
        F += part(f"up{s}", tube(b, "oil", [(s * 3.6, 23, 0), (s * 4.0, 18.5, -1.2)], ra=[1.1, 0.9], sides=6, hint=(1, 0, 0)))
        F += part(f"fo{s}", tube(b, "oil", [(s * 4.0, 18.5, -1.2), (s * 2.8, 16.0, -4.0)], ra=[0.9, 0.7], sides=6, hint=(1, 0, 0)))
        F += part(f"hand{s}", ellipsoid(b, "plaster", (s * 2.7, 15.8, -4.4), (0.65, 0.65, 0.7), segs=6, rings=2))
    # 網: 左肩から胸の前へ垂れる縄の格子 (縦 5・横 4)。下の縁に浮き
    def netpt(u, v):
        # u: 横 (-1..1)、v: 上から下 (0..1)。上は左肩、下は手の高さで前へ膨らむ
        x = -2.6 + 5.4 * (u + 1) / 2 * (0.5 + 0.5 * v)
        y = 23.5 - 9.5 * v
        z = -3.0 - 2.2 * math.sin(v * math.pi * 0.8) - 0.6 * (1 - abs(u))
        return (x, y, z)
    for i in range(5):
        u = -1 + i / 2
        F += part(f"nv{i}", tube("net", "rope", [netpt(u, v / 4) for v in range(5)], ra=0.18, sides=4, hint=(0, 0, 1)))
    for j in range(1, 5):
        v = j / 4
        F += part(f"nh{j}", tube("net", "rope", [netpt(-1 + i / 4, v) for i in range(9)], ra=0.18, sides=4, hint=(0, 1, 0)))
    for i in range(4):
        x, y, z = netpt(-0.75 + i / 2, 1.0)
        F += part(f"float{i}", ellipsoid("net", "plaster", (x, y - 0.5, z), (0.45, 0.45, 0.45), segs=5, rings=2))
    # 網に絡んだ海藻
    for i, (u, v) in enumerate(((-0.5, 0.5), (0.4, 0.75), (0.9, 0.35))):
        x, y, z = netpt(u, v)
        F += part(f"nweed{i}", tube("net", "weed", [(x, y, z - 0.2), (x + 0.3, y - 2.4, z - 0.4)], ra=[0.25, 0.1], rb=[0.6, 0.2], sides=4, hint=(0, 1, 0)))
    return F, []


POSES = {
    "idle": {},
    "swing": {"arm_l": (-150, 0, -15), "arm_r": (-150, 0, 15), "net": (-40, 0, 0), "body": (-6, 0, 0), "head": (-12, 0, 0)},
}

SPEC = dict(
    id="net_caster", short="網打ち", kind="灯の島の敵 一品一案 (50 の 4.2.2)",
    title="網打ち (net_caster) — 濡れた網を抱える前の浜の漁師の抜け殻・低ポリ + ドット絵 一案",
    sub=["網を頭上で振り回し (予告 0.8 秒)、前 6 の扇へ投げる。当たると 1.5 秒その場から動けない。",
         "油引きの外套と鍔の広い雨帽、空ろな顔に朝の光の目。肩から縄の網を垂らし、裾と網に海藻。身長 1.9 ブロック。"],
    labels=["網打ち"],
    rows=[("前 3/4", "idle", 212, 10, 300, (0, 16, 0), 8.0),
          ("振り回す", "swing", 200, 8, 300, (0, 18, 0), 7.5),
          ("横", "idle", 270, 4, 300, (0, 16, 0), 8.0),
          ("後ろ 3/4", "idle", 32, 12, 300, (0, 16, 0), 8.0)],
    options={"a": dict(name="網を垂らす漁師の抜け殻", desc="油引きの外套と雨帽、空ろな顔、肩から胸の前へ垂れる縄の網と浮き、海藻。")},
    extra="灯の島の言葉 (油引きの緑灰・縄の茶・海藻・朝の光)。骨 8 本。光るのは目だけ。",
    budget="中の敵 600〜1200", pw=420,
    later="二段目 = 網の結び目が朝の光にともる。",
)

if __name__ == "__main__":
    run_one(SPEC, build, PAL, BONES, POSES, hint=256)
