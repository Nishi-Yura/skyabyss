# -*- coding: utf-8 -*-
"""灰の打ち手 (ash_striker) 低ポリ 一品一案。炉の島の敵 (docs/design/50 の 4.2.3)。
前の灰炉の鍛冶の抜け殻。灰色の肌、焦げた革の前掛け、空ろな顔に朝の光の目。肩に大槌 (鉄の頭・木の柄)。腕の割れ目に残り火。前かがみで肩が大きい。身長 2.0 ブロック。
大槌を振り下ろし (予告 0.8 秒)、その所が 2.5 秒後にもう一度打たれる。
骨: root > body > head / arm_l / arm_r > hammer / leg_l / leg_r。
"""
import math
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from mk import *  # noqa

BONES = {"root": (None, (0, 0, 0)), "body": ("root", (0, 13, 0)), "head": ("body", (0, 26, -2.4)),
         "arm_l": ("body", (-4.6, 24, -0.4)), "arm_r": ("body", (4.6, 24, -0.4)), "hammer": ("arm_r", (5.2, 15.6, -3.6)),
         "leg_l": ("body", (-1.8, 12, 0)), "leg_r": ("body", (1.8, 12, 0))}
PAL = make_pal({
    "skin": (ASHSKIN, 0.5, dict(crack=0.12, noise=0.22)),
    "leather": (LEATHER, 0.5, dict(crack=0.08, noise=0.2)),
    "iron": (IRON, 0.62, dict(crack=0.06, noise=0.15)),
    "wood": (ROPE, 0.55, dict(crack=0.04, noise=0.18)),
    "hollow": (HOLLOW, 0.4, dict(crack=0.0, noise=0.04, min=0)),
    "dawn": (DAWN, 0.85, dict(crack=0.0, noise=0.05, min=1)),
    "ember": (EMBER, 0.85, dict(crack=0.0, noise=0.08, min=1)),
}, glow=["dawn", "ember"])


def build(k):
    F = []
    # 胴 (前かがみ、肩が大きい)
    torso = [ring(12.0, 2.8, -2.4, 2.4, n=10), ring(16.0, 3.0, -2.6, 2.4, n=10, zc=-0.3), ring(20.5, 4.8, -3.2, 3.0, n=10, zc=-1.0),
             ring(24.0, 5.4, -3.2, 3.0, n=10, zc=-1.4), ring(25.6, 2.6, -1.8, 1.8, n=10, zc=-1.8)]
    F += part("torso", loft("body", "skin", torso, caps=("skin", "skin")))
    # 革の前掛け (胸から膝まで、前に垂れる) と肩紐
    apron = [ring(5.0, 3.0, -3.2, -1.6, n=8), ring(12.0, 3.0, -3.0, -1.4, n=8), ring(18.0, 3.2, -3.0, -1.6, n=8, zc=-0.3), ring(22.0, 3.0, -3.3, -2.0, n=8, zc=-1.0)]
    F += part("apron", loft("body", "leather", apron, caps=("leather", "leather")))
    for s in (-1, 1):
        F += part(f"strap{s}", tube("body", "leather", [(s * 2.4, 22.0, -3.4), (s * 2.6, 25.4, -1.6), (s * 2.2, 24.0, 1.4)], ra=0.35, rb=0.7, sides=4, hint=(0, 0, 1)))
    # 脚と短い長靴
    for s, b in ((-1, "leg_l"), (1, "leg_r")):
        F += part(f"leg{s}", tube(b, "leather", [(s * 1.8, 12.5, 0), (s * 2.0, 1.6, 0.1)], ra=[1.3, 1.0], sides=6, hint=(1, 0, 0)))
        F += part(f"boot{s}", ellipsoid(b, "iron", (s * 2.0, 1.0, -0.8), (1.2, 1.0, 1.8), segs=6, rings=2))
    # 頭: 灰の顔の空ろ、朝の光の目、煤けた頭巾
    F += part("face", ellipsoid("head", "hollow", (0, 27.4, -3.4), (1.6, 1.9, 1.4), segs=8, rings=3))
    hood = [ring(25.4, 2.4, -2.0, 2.4, n=10, zc=-2.4), ring(28.0, 2.5, -1.6, 2.6, n=10, zc=-2.6), ring(30.0, 1.8, -0.8, 2.0, n=10, zc=-2.4),
            ring(30.8, 0.8, 0.0, 1.0, n=10, zc=-2.2)]
    F += part("hood", loft("head", "leather", hood, caps=("hollow", "leather")))
    for s in (-1, 1):
        F += part(f"eye{s}", ellipsoid("head", "dawn", (s * 0.65, 27.8, -4.7), (0.3, 0.24, 0.2), segs=5, rings=2))
    # 腕 (太い。割れ目に残り火)
    for s, b in ((-1, "arm_l"), (1, "arm_r")):
        F += part(f"up{s}", tube(b, "skin", [(s * 4.6, 24, -0.4), (s * 5.4, 19.0, -1.4)], ra=[1.9, 1.5], sides=6, hint=(1, 0, 0)))
        F += part(f"fo{s}", tube(b, "skin", [(s * 5.4, 19.0, -1.4), (s * 5.2, 15.8, -3.4)], ra=[1.5, 1.4], sides=6, hint=(1, 0, 0)))
        F += part(f"fist{s}", ellipsoid(b, "skin", (s * 5.2, 15.4, -3.8), (1.4, 1.4, 1.4), segs=6, rings=2))
        F += part(f"crack{s}", tube(b, "ember", [(s * 5.9, 21.6, -0.8), (s * 6.3, 19.6, -1.3), (s * 6.0, 17.6, -2.2)], ra=0.2, sides=3, hint=(0, 0, 1)))
    # 大槌 (右手に握り、柄は前上へ。頭は鉄の角塊、打ち面に残り火)
    F += part("haft", tube("hammer", "wood", [(5.2, 12.0, -3.0), (5.2, 27.0, -7.4)], ra=0.6, sides=6, hint=(1, 0, 0)))
    F += part("head1", tube("hammer", "iron", [(2.2, 27.6, -7.6), (8.2, 27.6, -7.6)], ra=2.2, rb=2.0, sides=4, hint=(0, 1, 0), caps=(True, True)))
    F += part("band", tube("hammer", "iron", [(5.2, 25.0, -7.0), (5.2, 25.6, -7.2)], ra=0.85, sides=6, hint=(1, 0, 0)))
    F += part("face_l", ellipsoid("hammer", "ember", (2.1, 27.6, -7.6), (0.25, 1.6, 1.5), segs=6, rings=2))
    return F, []


POSES = {
    "idle": {},
    "slam": {"arm_r": (-150, 0, 15), "arm_l": (-130, 0, -20), "body": (-10, 0, 0), "head": (-10, 0, 0)},
}

SPEC = dict(
    id="ash_striker", short="灰の打ち手", kind="炉の島の敵 一品一案 (50 の 4.2.3)",
    title="灰の打ち手 (ash_striker) — 大槌を担ぐ前の灰炉の鍛冶の抜け殻・低ポリ + ドット絵 一案",
    sub=["大槌を振り下ろし (予告 0.8 秒、円 半径 2)、その所が 2.5 秒後にもう一度打たれる (残り火の輪)。",
         "灰色の肌、焦げた革の前掛けと頭巾、空ろな顔に朝の光の目、腕の割れ目に残り火。肩に鉄の大槌。身長 2.0 ブロック。"],
    labels=["灰の打ち手"],
    rows=[("前 3/4", "idle", 212, 10, 300, (0, 16, 0), 7.6),
          ("振りかぶる", "slam", 200, 8, 300, (0, 18, 0), 7.0),
          ("横", "idle", 270, 4, 300, (0, 16, 0), 7.6),
          ("後ろ 3/4", "idle", 32, 12, 300, (0, 16, 0), 7.6)],
    options={"a": dict(name="大槌の鍛冶の抜け殻", desc="灰の肌、革の前掛けと頭巾、空ろな顔、腕の残り火の割れ目、鉄の大槌。")},
    extra="炉の島の言葉 (灰・革・鉄・残り火)。骨 8 本。光るのは目と割れ目と槌の打ち面。",
    budget="中の敵 600〜1200", pw=420,
    later="二段目 = 割れ目が広がり、前掛けが焦げる。",
)

if __name__ == "__main__":
    run_one(SPEC, build, PAL, BONES, POSES, hint=256)
