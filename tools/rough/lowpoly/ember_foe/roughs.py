# -*- coding: utf-8 -*-
"""灰の荒野 (灰炉の土地) に足す敵一種の形のラフ (2026-11-05 の頁 d_ember_foes = A)。三案。
部品と描き方は地下の敵のラフ (tools/rough/lowpoly/dungeon_foes/roughs.py) と同じ。長さの単位は 1/16 ブロック (人 = 29)。前は -z。

    python -X utf8 -I tools/rough/lowpoly/ember_foe/roughs.py
    → docs/review/roughs_ember_foe/ember_foe.png と ember_foe_<a|b|c>.png
"""
from __future__ import annotations

import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[3]
sys.path.insert(0, str(ROOT / "tools/rough/lowpoly/dungeon_foes"))
import roughs as base  # noqa: E402
from mlp_extra import *  # noqa: E402,F401,F403

P = base.P
eye = base.eye
bpal = base.bpal
MAG = base.MAG
ASH = base.ASH
CHAR = [(24, 20, 20), (38, 32, 30), (54, 46, 42), (72, 62, 56)]
SOOT = [(48, 44, 46), (70, 64, 64), (96, 88, 84), (126, 116, 108)]
HORN = [(60, 56, 52), (90, 84, 76), (122, 114, 102), (156, 146, 130)]
KILN = [(90, 46, 34), (124, 66, 44), (156, 90, 58), (186, 120, 80)]


def eyes(m, c, r, iris, spread, glow=False):
    for s in (1, -1):
        eye(m, "body", (s * spread, c[1], c[2]), r, (s * 0.5, 0.1, -1), iris, glow=glow)


def look(m, cy, scale, caption):
    m.view = dict(center=(0, cy, 0), scale=scale)
    m.caption = caption


def ember_foe_a():
    """A 熾の山犬: 痩せた灰色の山犬。肩と背の毛の割れ目から熾が透ける。群れで回り込んで噛む"""
    m = Mount("ember_foe_a", "熾の山犬")
    m.bone("body", "root", (0, 10, 0))
    m.add("torso", ell("body", "fur", (0, 11.0, 0.4), (3.0, 3.2, 7.0), segs=12, rings=4), core=True)
    m.add("chest", ell("body", "fur", (0, 10.4, -4.6), (3.0, 3.6, 3.0), segs=9, rings=3))
    m.add("neck", tub("body", "fur", [P(0, 12.6, -5.6), P(0, 14.4, -7.4)], [2.0, 1.6], None, sides=7))
    m.add("head", ell("body", "fur", (0, 15.0, -8.8), (2.2, 2.0, 2.6), segs=9, rings=3))
    m.add("muzzle", cone("body", "fur", P(0, 14.2, -10.6), (0, -0.25, -1), 3.0, 1.1, sides=6, rings=2))
    m.add("nose", ell("body", "dark", (0, 13.6, -13.4), (0.5, 0.4, 0.4), segs=5, rings=2))
    for s in (1, -1):
        m.add(f"ear{s}", cone("body", "fur", P(s * 1.2, 16.6, -8.4), (s * 0.3, 1, 0.2), 2.6, 0.9, sides=4, rings=2))
        m.add(f"seam{s}", box("body", "ember", (s * 1.6, 13.2, -1.0 + s * 0.4), (0.4, 0.4, 6.0), taper=(1, 1), cut=0.1))
    m.add("ridge", box("body", "ember", (0, 14.0, 0.4), (0.6, 0.5, 8.0), taper=(1, 0.6), cut=0.1))
    eyes(m, (0, 15.6, -10.4), 0.45, (255, 150, 60), 1.3, glow=True)
    for s in (1, -1):
        for k, z in (("f", -4.4), ("b", 4.8)):
            top = P(s * 2.0, 9.4, z)
            mid = P(s * 2.2, 5.0, z + (1.2 if k == "b" else -0.6))
            low = P(s * 2.2, 0.9, z)
            m.add(f"leg{k}{s}", limb("body", "fur", [top, mid, low], [1.2, 0.9, 0.7], [1.2, 0.9, 0.7], n=6))
            m.add(f"paw{k}{s}", ell("body", "dark", low + P(0, -0.3, -0.5), (0.9, 0.6, 1.4), segs=6, rings=2))
    m.add("tail", tub("body", "fur", [P(0, 11.6, 7.0), P(0, 10.4, 10.0), P(0, 8.2, 12.0)], [1.0, 1.2, 0.6], None, sides=6))
    m.add("tailtip", ell("body", "ember", (0, 7.8, 12.4), (0.7, 0.7, 0.9), segs=6, rings=2))
    m.pal = bpal({"fur": M_(SOOT, lit=0.3), "dark": M_(CHAR), "ember": M_(MAG, base=0.72, lit=0.2)}, glow=["ember"])
    m.detail = lambda c, col, glow: (speckle(c, col, ["fur"], (40, 36, 36), density=0.06, size=0.6), glow)
    look(m, 9, 9.6, ["痩せた灰色の山犬（膝より高い）", "肩と背の毛の割れ目から熾が透ける", "三、四頭で回り込み、後ろから噛む"])
    return m


def ember_foe_b():
    """B 灰の鷺: 灰の池に立つ背の高い鷺。灰をかぶった羽と、熾の色の目。長い嘴で突く"""
    m = Mount("ember_foe_b", "灰の鷺")
    m.bone("body", "root", (0, 18, 0))
    m.add("torso", ell("body", "ash", (0, 19.0, 1.0), (3.4, 3.8, 5.6), segs=12, rings=4), core=True)
    m.add("breast", ell("body", "pale", (0, 18.2, -2.6), (2.6, 3.0, 2.6), segs=8, rings=3))
    m.add("neck", tub("body", "pale", [P(0, 21.0, -3.0), P(0, 25.0, -3.8), P(0, 27.6, -2.6), P(0, 29.0, -4.0)], [1.4, 1.1, 1.0, 0.9], None, sides=7))
    m.add("head", ell("body", "ash", (0, 29.6, -4.6), (1.4, 1.5, 2.0), segs=8, rings=3))
    m.add("crest", cone("body", "char", P(0, 30.4, -3.4), (0, 0.3, 1), 3.4, 0.4, sides=4, rings=2, bend=(0, -0.6, 0)))
    m.add("beak", cone("body", "horn", P(0, 29.4, -6.2), (0, -0.15, -1), 6.4, 0.55, sides=5, rings=2))
    eyes(m, (0, 30.0, -5.6), 0.35, (255, 140, 50), 0.95, glow=True)
    for s in (1, -1):
        m.add(f"wing{s}", ell("body", "ash", (s * 3.4, 19.6, 1.6), (1.0, 3.2, 5.6), segs=8, rings=3))
        m.add(f"tip{s}", cone("body", "char", P(s * 3.4, 18.4, 6.4), (0, -0.4, 1), 4.0, 0.9, sides=4, rings=2))
        m.add(f"leg{s}", limb("body", "horn", [P(s * 1.4, 16.2, 1.0), P(s * 1.5, 9.0, 2.2), P(s * 1.5, 0.9, 1.2)], [0.55, 0.45, 0.4], [0.55, 0.45, 0.4], n=6))
        m.add(f"foot{s}", ell("body", "horn", (s * 1.5, 0.6, 0.0), (1.2, 0.3, 2.4), segs=6, rings=2))
    for k, dx in enumerate((-0.8, 0.0, 0.8)):
        m.add(f"tail{k}", cone("body", "char", P(dx, 18.6, 6.0), (dx * 0.2, -0.3, 1), 3.4, 0.7, sides=4, rings=2))
    m.add("ashfall", box("body", "ember", (0, 21.6, 1.2), (2.0, 0.2, 3.0), taper=(0.6, 0.6), cut=0.1))
    m.pal = bpal({"ash": M_(ASH, lit=0.3), "pale": M_(SOOT), "char": M_(CHAR), "horn": M_(HORN), "ember": M_(MAG, base=0.7, lit=0.2)}, glow=["ember"])
    m.detail = lambda c, col, glow: (speckle(c, col, ["ash", "pale"], (60, 56, 56), density=0.07, size=0.5), glow)
    look(m, 15, 6.8, ["灰の池に立つ背の高い鷺（人ほど）", "灰をかぶった羽、熾の色の目、背の熾", "長い嘴で突き、羽ばたいて灰を吹きつける"])
    return m


def ember_foe_c():
    """C 炉負いの甲虫: 小さな炉のような殻を背負った大きな甲虫。殻の口から熾を吐き、投げる"""
    m = Mount("ember_foe_c", "炉負いの甲虫")
    m.bone("body", "root", (0, 6, 0))
    m.add("torso", ell("body", "shell", (0, 6.4, 0.6), (5.0, 3.6, 7.0), segs=12, rings=4), core=True)
    m.add("kiln", ell("body", "kiln", (0, 10.2, 1.4), (3.8, 3.4, 4.4), segs=10, rings=4))
    m.add("mouth", ell("body", "ember", (0, 11.6, -1.8), (1.6, 1.2, 0.6), segs=8, rings=2))
    m.add("chimney", tub("body", "kiln", [P(0, 12.6, 3.4), P(0, 16.0, 4.2)], [1.4, 1.1], None, sides=7))
    m.add("smoke", ell("body", "ember", (0, 16.2, 4.2), (0.9, 0.3, 0.9), segs=7, rings=1))
    for k, z in enumerate((-0.2, 2.2, 4.4)):
        m.add(f"band{k}", box("body", "dark", (0, 9.4, z), (7.6 - abs(z - 2.2) * 0.6, 0.5, 0.5), taper=(1, 1), cut=0.1))
    m.add("head", ell("body", "shell", (0, 5.8, -7.0), (2.8, 2.2, 2.4), segs=9, rings=3))
    for s in (1, -1):
        m.add(f"mandible{s}", cone("body", "dark", P(s * 1.2, 5.0, -9.0), (s * -0.3, -0.1, -1), 3.0, 0.6, sides=4, rings=2, bend=(s * -0.8, 0, 0)))
        m.add(f"antenna{s}", cone("body", "dark", P(s * 1.0, 7.4, -8.4), (s * 0.4, 0.6, -1), 4.0, 0.25, sides=3, rings=2, bend=(0, 0.6, 0)))
        for k, z in enumerate((-4.0, 0.6, 5.0)):
            top = P(s * 4.2, 5.4, z)
            mid = P(s * 7.0, 4.6, z - 0.6)
            low = P(s * 7.8, 0.6, z - 0.2)
            m.add(f"leg{k}{s}", limb("body", "dark", [top, mid, low], [0.7, 0.55, 0.35], [0.7, 0.55, 0.35], n=5))
    eyes(m, (0, 6.6, -9.0), 0.45, (255, 140, 50), 1.6, glow=True)
    m.pal = bpal({"shell": M_(CHAR, lit=0.35), "kiln": M_(KILN, lit=0.3), "dark": M_(SOOT), "ember": M_(MAG, base=0.74, lit=0.2)}, glow=["ember"])
    m.detail = lambda c, col, glow: (speckle(c, col, ["kiln"], (70, 36, 26), density=0.08, size=0.7), glow)
    look(m, 7, 10.0, ["大きな甲虫（腰ほど・長い）", "背に小さな炉の殻、煙突から煙", "殻の口から熾を吐き、弧を描いて投げる"])
    return m


SUBJECTS = {"ember_foe": ("灰の荒野に足す敵 3 案", [ember_foe_a, ember_foe_b, ember_foe_c])}


if __name__ == "__main__":
    sys.stdout.reconfigure(encoding="utf-8")
    base.SUBJECTS = SUBJECTS
    base.OUT = ROOT / "docs/review/roughs_ember_foe"
    for key in (sys.argv[1:] or list(SUBJECTS)):
        base.sheet(key)
