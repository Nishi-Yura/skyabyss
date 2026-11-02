# -*- coding: utf-8 -*-
"""戻し手 (rewinder) 低ポリ 一品一案。炉の島の主 (docs/design/50 の 4.2.3)。
前の灰炉の最後の鍛冶頭の影。痩せて背が高い (3.6 ブロック)。灰色の革の長い前掛け、煤けた頭巾と鉄の面 (目の切れ目に朝の光)、長い腕。
右手に大きな槌、左手に長い火箸。背に割れた青銅の歯車の輪 (巻き戻しの印。逆に回る)。歯車の割れ目と前掛けの焦げに残り火。
骨: root > body > head / arm_l > tongs / arm_r > hammer / wheel / leg_l / leg_r。
"""
import math
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from mk import *  # noqa

BONES = {"root": (None, (0, 0, 0)), "body": ("root", (0, 26, 0)), "head": ("body", (0, 47, -1.4)),
         "arm_l": ("body", (-6.0, 44, 0)), "arm_r": ("body", (6.0, 44, 0)), "tongs": ("arm_l", (-7.4, 22.0, -4.2)),
         "hammer": ("arm_r", (7.4, 22.0, -4.2)), "wheel": ("body", (0, 42, 5.2)),
         "leg_l": ("body", (-2.2, 24, 0)), "leg_r": ("body", (2.2, 24, 0))}
PAL = make_pal({
    "skin": (ASHSKIN, 0.5, dict(crack=0.12, noise=0.22)),
    "leather": (LEATHER, 0.5, dict(crack=0.08, noise=0.2)),
    "robe": (ROBE, 0.5, dict(crack=0.04, noise=0.18)),
    "iron": (IRON, 0.62, dict(crack=0.06, noise=0.15)),
    "bronze": (BRONZE, 0.66, dict(crack=0.08, noise=0.18)),
    "wood": (ROPE, 0.55, dict(crack=0.04, noise=0.18)),
    "hollow": (HOLLOW, 0.4, dict(crack=0.0, noise=0.04, min=0)),
    "dawn": (DAWN, 0.85, dict(crack=0.0, noise=0.05, min=1)),
    "ember": (EMBER, 0.85, dict(crack=0.0, noise=0.08, min=1)),
}, glow=["dawn", "ember"])


def build(k):
    F = []
    # 胴 (痩せて長い。暗い上着)
    torso = [ring(22.0, 3.4, -2.8, 2.8, n=10), ring(30.0, 3.2, -2.6, 2.6, n=10), ring(38.0, 4.2, -3.0, 2.8, n=10, zc=-0.4),
             ring(44.0, 5.4, -3.2, 3.0, n=10, zc=-0.6), ring(46.4, 2.8, -2.0, 2.0, n=10, zc=-0.8)]
    F += part("torso", loft("body", "robe", torso, caps=("robe", "robe")))
    # 長い革の前掛け (胸から足首まで、前に垂れる。裾が焦げる)
    apron = [ring(3.0, 4.0, -4.0, -2.0, n=8), ring(14.0, 3.8, -3.6, -1.8, n=8), ring(26.0, 3.6, -3.2, -1.6, n=8), ring(36.0, 3.6, -3.4, -1.8, n=8, zc=-0.3),
             ring(42.0, 3.4, -3.6, -2.2, n=8, zc=-0.6)]
    F += part("apron", loft("body", "leather", apron, caps=("leather", "leather")))
    for i, x in enumerate((-2.6, -0.4, 1.8)):
        F += part(f"burn{i}", tube("body", "ember", [(x, 3.4, -4.15), (x + 0.4, 6.0 + i, -4.0)], ra=0.25, rb=0.6, sides=4, hint=(0, 0, 1)))
    for s in (-1, 1):
        F += part(f"strap{s}", tube("body", "leather", [(s * 3.0, 42.0, -3.7), (s * 3.2, 46.0, -1.8), (s * 2.8, 44.4, 2.6)], ra=0.4, rb=0.8, sides=4, hint=(0, 0, 1)))
    # 脚 (長い) と鉄の靴
    for s, b in ((-1, "leg_l"), (1, "leg_r")):
        F += part(f"leg{s}", tube(b, "robe", [(s * 2.2, 24, 0), (s * 2.4, 2.0, 0.2)], ra=[1.5, 1.1], sides=6, hint=(1, 0, 0)))
        F += part(f"boot{s}", ellipsoid(b, "iron", (s * 2.4, 1.2, -1.0), (1.4, 1.2, 2.2), segs=6, rings=2))
    # 頭巾と鉄の面
    hood = [ring(46.0, 3.0, -2.6, 3.0, n=12, zc=-1.4), ring(50.0, 3.2, -2.6, 3.2, n=12, zc=-1.6), ring(53.4, 2.4, -1.6, 2.6, n=12, zc=-1.4),
            ring(55.4, 1.0, -0.4, 1.4, n=12, zc=-1.0)]
    F += part("hood", loft("head", "leather", hood, caps=("hollow", "leather")))
    F += part("mask", ellipsoid("head", "iron", (0, 49.6, -3.6), (2.2, 2.8, 1.0), segs=10, rings=4))
    F += part("slit", ellipsoid("head", "hollow", (0, 50.4, -4.5), (1.7, 0.4, 0.3), segs=8, rings=2))
    for s in (-1, 1):
        F += part(f"eye{s}", ellipsoid("head", "dawn", (s * 0.8, 50.4, -4.7), (0.42, 0.22, 0.2), segs=5, rings=2))
    for i, y in enumerate((48.0, 47.2)):
        F += part(f"rivet{i}", ellipsoid("head", "bronze", (0, y, -4.5), (0.3, 0.3, 0.2), segs=5, rings=2))
    # 腕 (長い)
    for s, b in ((-1, "arm_l"), (1, "arm_r")):
        F += part(f"up{s}", tube(b, "robe", [(s * 6.0, 44, 0), (s * 7.6, 33.0, -1.2)], ra=[1.7, 1.3], sides=6, hint=(1, 0, 0)))
        F += part(f"fo{s}", tube(b, "skin", [(s * 7.6, 33.0, -1.2), (s * 7.4, 23.0, -3.8)], ra=[1.2, 1.1], sides=6, hint=(1, 0, 0)))
        F += part(f"cuff{s}", tube(b, "leather", [(s * 7.6, 31.0, -1.6), (s * 7.5, 28.4, -2.4)], ra=1.5, sides=6, hint=(1, 0, 0)))
        F += part(f"hand{s}", ellipsoid(b, "skin", (s * 7.4, 22.0, -4.2), (1.3, 1.4, 1.3), segs=6, rings=2))
        F += part(f"crack{s}", tube(b, "ember", [(s * 8.6, 27.0, -2.6), (s * 8.4, 24.6, -3.6)], ra=0.2, sides=3, hint=(0, 0, 1)))
    # 槌 (右手。柄は下へ、頭は手の下)
    F += part("haft", tube("hammer", "wood", [(7.4, 26.0, -4.2), (7.4, 8.0, -4.2)], ra=0.7, sides=6, hint=(1, 0, 0)))
    F += part("hhead", tube("hammer", "iron", [(4.6, 7.0, -4.2), (10.2, 7.0, -4.2)], ra=2.4, rb=2.2, sides=4, hint=(0, 1, 0), caps=(True, True)))
    F += part("hface", ellipsoid("hammer", "ember", (10.25, 7.0, -4.2), (0.25, 1.7, 1.6), segs=6, rings=2))
    # 火箸 (左手。長い二本の鉄、先が少し開く)
    for dz in (-0.5, 0.5):
        F += part(f"tong{dz}", tube("tongs", "iron", [(-7.4, 24.0, -4.2 + dz * 0.4), (-7.6, 10.0, -4.4 + dz * 1.0), (-7.8, 4.0, -4.6 + dz * 2.0)],
                                    ra=0.35, sides=4, hint=(1, 0, 0)))
    F += part("tongpin", ellipsoid("tongs", "bronze", (-7.4, 19.0, -4.2), (0.5, 0.5, 0.6), segs=5, rings=2))
    # 背の割れた歯車の輪 (青銅。歯 12、一か所欠け、割れ目に残り火。真ん中の軸)
    R = 9.0
    rim = [(math.cos(a) * R, 42.0 + math.sin(a) * R, 5.2) for a in [math.radians(20 + i * 10) for i in range(33)]]
    F += part("rim", tube("wheel", "bronze", rim, ra=0.8, rb=0.7, sides=4, hint=(0, 0, 1)))
    for i in range(12):
        a = math.radians(30 + i * 27)
        if a > math.radians(340):
            break
        F += part(f"tooth{i}", tube("wheel", "bronze", [(math.cos(a) * (R + 0.4), 42.0 + math.sin(a) * (R + 0.4), 5.2), (math.cos(a) * (R + 2.0), 42.0 + math.sin(a) * (R + 2.0), 5.2)],
                                    ra=0.9, rb=0.6, sides=4, hint=(0, 0, 1)))
    for i in range(4):
        a = math.radians(45 + i * 90)
        F += part(f"spoke{i}", tube("wheel", "bronze", [(0, 42.0, 5.2), (math.cos(a) * (R - 0.5), 42.0 + math.sin(a) * (R - 0.5), 5.2)], ra=0.5, sides=4, hint=(0, 0, 1)))
    F += part("hub", ellipsoid("wheel", "bronze", (0, 42.0, 5.4), (1.6, 1.6, 0.9), segs=8, rings=2))
    F += part("wcrack", tube("wheel", "ember", [(math.cos(math.radians(15)) * R, 42.0 + math.sin(math.radians(15)) * R, 5.4),
                                               (math.cos(math.radians(8)) * (R - 2.4), 42.0 + math.sin(math.radians(8)) * (R - 2.4), 5.4)], ra=0.3, sides=3, hint=(0, 0, 1)))
    return F, []


POSES = {
    "idle": {},
    "slam": {"arm_r": (-150, 0, 10), "hammer": (180, 0, 0), "body": (-8, 0, 0)},
    "sweep": {"arm_r": (-70, 60, 30), "arm_l": (-40, -30, -20), "body": (0, 30, 0)},
}

SPEC = dict(
    id="rewinder", short="戻し手", kind="炉の島の主 一品一案 (50 の 4.2.3)",
    title="戻し手 (rewinder) — 背に割れた歯車、槌と火箸の鍛冶頭の影・低ポリ + ドット絵 一案",
    sub=["槌の打ち下ろし・薙ぎは 3 秒後に同じ所でもう一度 (段 3 は二度)。足跡が燃える。灰の打ち手を呼ぶ。",
         "痩せて背の高い鍛冶頭。革の長い前掛け、煤けた頭巾と鉄の面、右手に槌、左手に火箸、背に割れた青銅の歯車。身長 3.6 ブロック。"],
    labels=["戻し手"],
    rows=[("前 3/4", "idle", 212, 8, 480, (0, 30, 0), 6.6),
          ("打ち下ろし", "slam", 200, 6, 480, (0, 32, 0), 6.2),
          ("薙ぎ", "sweep", 220, 6, 480, (0, 30, 0), 6.6),
          ("後ろ 3/4", "idle", 32, 10, 480, (0, 30, 0), 6.6)],
    options={"a": dict(name="歯車を負う鍛冶頭", desc="革の長い前掛け、鉄の面、長い腕に槌と火箸、背に割れた青銅の歯車の輪。")},
    extra="炉の島の言葉 (灰・革・鉄・青銅・残り火)。骨 10 本。光るのは面の切れ目・割れ目・槌の打ち面。",
    budget="主 1500〜3000", pw=440,
    later="二段目 = 歯車の割れ目が広がり、残り火が漏れる。",
)

if __name__ == "__main__":
    run_one(SPEC, build, PAL, BONES, POSES, hint=256)
