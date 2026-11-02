# -*- coding: utf-8 -*-
"""戻り火 (backfire) 低ポリ 一品一案。炉の島の敵 (docs/design/50 の 4.2.3)。
炉から漏れた火の霊。小さな鉄の火の籠 (格子 6 本と輪) の中に残り火の芯、籠の上に炎の冠、下に三本の炎の尾がたなびく。高さ 1.2 ブロック。宙に浮く。
離れた人の足もとへ火の玉 (円 0.8 秒)。落ちた所が 2.5 秒後にもう一度燃える。
骨: root > body > core / tail。
"""
import math
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from mk import *  # noqa

BONES = {"root": (None, (0, 0, 0)), "body": ("root", (0, 10, 0)), "core": ("body", (0, 10, 0)), "tail": ("body", (0, 6.5, 0))}
PAL = make_pal({
    "iron": (IRON, 0.62, dict(crack=0.06, noise=0.15)),
    "ember": (EMBER, 0.85, dict(crack=0.0, noise=0.08, min=1)),
    "dawn": (DAWN, 0.85, dict(crack=0.0, noise=0.05, min=1)),
    "hollow": (HOLLOW, 0.4, dict(crack=0.0, noise=0.04, min=0)),
}, glow=["ember", "dawn"])


def hoop(bone, mat, y, r, w=0.35, n=12):
    return tube(bone, mat, [(math.cos(a) * r, y, math.sin(a) * r) for a in [i / n * 2 * math.pi for i in range(n + 1)]], ra=w, sides=4, hint=(0, 1, 0))


def build(k):
    F = []
    # 鉄の籠 (格子 6 本、上下と中の輪)
    for i in range(6):
        a = i / 6 * 2 * math.pi
        F += part(f"bar{i}", tube("body", "iron", [(math.cos(a) * 2.6, 6.6, math.sin(a) * 2.6), (math.cos(a) * 3.2, 10.0, math.sin(a) * 3.2),
                                                    (math.cos(a) * 2.4, 13.6, math.sin(a) * 2.4)], ra=0.3, sides=4, hint=(0, 1, 0)))
    for y, r in ((6.6, 2.6), (10.0, 3.2), (13.6, 2.4)):
        F += part(f"hoop{y}", hoop("body", "iron", y, r))
    F += part("cap", ellipsoid("body", "iron", (0, 14.0, 0), (1.4, 0.6, 1.4), segs=6, rings=2))
    # 芯 (残り火の塊と、二つの小さな目の光)
    F += part("heart", ellipsoid("core", "ember", (0, 10.0, 0), (1.8, 2.2, 1.8), segs=8, rings=4))
    for s in (-1, 1):
        F += part(f"eye{s}", ellipsoid("core", "hollow", (s * 0.6, 10.6, -1.65), (0.32, 0.4, 0.2), segs=5, rings=2))
    # 炎の冠 (上へ伸びる尖り 5)
    for i in range(5):
        a = i / 5 * 2 * math.pi + 0.3
        F += part(f"flame{i}", spike("body", "ember", (math.cos(a) * 1.0, 14.2, math.sin(a) * 1.0), (math.cos(a) * 0.3, 1, math.sin(a) * 0.3),
                                     2.6 + (i % 2) * 1.2, 0.6, sides=4, rings=2))
    F += part("flamec", spike("body", "dawn", (0, 14.4, 0), (0, 1, 0), 3.8, 0.7, sides=4, rings=2))
    # 炎の尾 (下へ、後ろへたなびく 3 本)
    for i, dx in enumerate((-0.9, 0.0, 0.9)):
        F += part(f"tail{i}", tube("tail", "ember", [(dx * 0.6, 6.4, 0.0), (dx * 1.2, 4.0, 1.2), (dx * 1.6, 1.6, 2.8 + i * 0.3)],
                                   ra=[0.8, 0.5, 0.08], sides=4, hint=(0, 0, 1)))
    return F, []


POSES = {
    "idle": {},
    "throw": {"body": (-25, 0, 0), "core": (0, 0, 0), "tail": (30, 0, 0)},
}

SPEC = dict(
    id="backfire", short="戻り火", kind="炉の島の敵 一品一案 (50 の 4.2.3)",
    title="戻り火 (backfire) — 炉から漏れた火の霊・低ポリ + ドット絵 一案",
    sub=["離れた人の足もとへ火の玉 (円 0.8 秒)。落ちた所が 2.5 秒後にもう一度燃える。",
         "鉄の火の籠の中に残り火の芯と二つの目、上に炎の冠、下に三本の炎の尾。高さ 1.2 ブロック。宙に浮く。"],
    labels=["戻り火"],
    rows=[("前 3/4", "idle", 212, 10, 300, (0, 9, 0), 11.0),
          ("投げる", "throw", 220, 6, 300, (0, 9, 0), 11.0),
          ("横", "idle", 270, 4, 300, (0, 9, 0), 11.0),
          ("上から", "idle", 200, 50, 300, (0, 9, 0), 11.0)],
    options={"a": dict(name="火の籠の霊", desc="鉄の格子の籠、残り火の芯と目、炎の冠と尾。")},
    extra="炉の島の言葉 (鉄・残り火)。骨 4 本。光るのは芯と炎。",
    budget="小さな敵 400〜900", pw=420,
    later="二段目 = 尾が二本増える。",
)

if __name__ == "__main__":
    run_one(SPEC, build, PAL, BONES, POSES, hint=256)
