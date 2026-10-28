# -*- coding: utf-8 -*-
"""押し石 (push_stone) 低ポリ ラフ 3 案。上弦の崖の、宙に浮く岩 (docs/design/46 の 4.4)。
近づくと押しの波 (予告の輪) で人を崖の外へ押し出す。地に足がない (浮く)。押す前に体が縮み、光が強まるのが予告。
A 目のある輪石 (平たい輪の形の岩が縦に浮き、輪の中に銀の芯と星の目。押すとき輪が回る)
B 重ねた石の板 (三枚の平たい岩の板が少しずつずれて重なり、間から星の光が漏れる)
C 芯のまわりの小石 (星の光の芯のまわりを、六つの小さな岩が回る。押すとき外へ広がる)
骨: root > body > core / ring (A) / plate_1..3 (B) / orbit (C)。高さ 1.5 ブロックほど (浮く高さを含めず)。
"""
import math
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from mk import *  # noqa

BONES = {"root": (None, (0, 0, 0)), "body": ("root", (0, 14, 0)), "core": ("body", (0, 14, 0)), "ring": ("body", (0, 14, 0)),
         "plate_1": ("body", (0, 10, 0)), "plate_2": ("body", (0, 14, 0)), "plate_3": ("body", (0, 18, 0)), "orbit": ("body", (0, 14, 0))}
PAL = make_pal({
    "basalt": (BASALT, 0.6, dict(crack=0.18, noise=0.2)),
    "tuff": ([(40, 46, 60), (66, 74, 90), (96, 104, 120), (128, 136, 150)], 0.62, dict(crack=0.14, noise=0.18)),
    "dust": (DUST, 0.66, dict(crack=0.05, noise=0.26)),
    "silver": (SILVER, 0.66, dict(crack=0.05, noise=0.14)),
    "star": (STAR, 0.8, dict(crack=0.0, noise=0.06, min=1)),
}, glow=["star"])


def build_a(k):
    """A 目のある輪石: 平たい輪の岩が縦に浮く。輪の中に銀の芯と星の目。輪の外に岩の瘤。"""
    F = []
    n = 14
    for i in range(n):
        a0, a1 = i / n * 2 * math.pi, (i + 1) / n * 2 * math.pi
        r = 7.0 + (0.6 if i % 3 == 0 else 0)
        F += part(f"seg{i}", tube("ring", "basalt" if i % 2 else "tuff", [(math.cos(a0) * r, 14 + math.sin(a0) * r, 0), (math.cos(a1) * r, 14 + math.sin(a1) * r, 0)],
                                  ra=2.0, sides=6, hint=(0, 0, 1)))
    for i in range(5):
        a = i / 5 * 2 * math.pi + 0.3
        F += part(f"knob{i}", ellipsoid("ring", "dust", (math.cos(a) * 8.6, 14 + math.sin(a) * 8.6, 0), (1.4, 1.4, 1.6), segs=6, rings=3))
    F += part("core", ellipsoid("core", "silver", (0, 14, 0), (3.0, 3.0, 2.4), segs=9, rings=4))
    F += part("iris", ellipsoid("core", "star", (0, 14, -2.1), (1.6, 1.6, 0.6), segs=8, rings=3))
    for s in (-1, 1):
        F += part(f"spoke{s}", tube("core", "silver", [(s * 2.8, 14, 0), (s * 5.4, 14, 0)], ra=0.5, sides=5, hint=(0, 1, 0)))
    return F, [eye_at(0, 14, -2.75, 0.7)]


def build_b(k):
    """B 重ねた石の板: 三枚の平たい岩の板が少しずつずれて重なり浮く。間から星の光が漏れる。"""
    F = []
    for i, (b, y, rx, rz, rot) in enumerate((("plate_1", 10, 6.5, 5.5, 0.0), ("plate_2", 14, 7.2, 6.0, 0.5), ("plate_3", 18, 5.6, 4.8, 1.0))):
        R = []
        for j, (dy, sc) in enumerate(((-1.2, 0.85), (-0.4, 1.0), (0.6, 0.95), (1.2, 0.7))):
            pts = []
            for q in range(10):
                a = q / 10 * 2 * math.pi + rot
                jag = 1.0 + 0.12 * math.sin(q * 2.7 + i)
                pts.append((math.cos(a) * rx * sc * jag, y + dy, math.sin(a) * rz * sc * jag))
            R.append(pts)
        F += part(f"slab{i}", loft(b, "basalt" if i != 1 else "tuff", [ring_pts(p) for p in R], caps=("basalt", "dust")))
        F += part(f"glow{i}", ellipsoid(b, "star", (0, y - 1.6, 0), (rx * 0.55, 0.4, rz * 0.55), segs=8, rings=2))
    F += part("eye", ellipsoid("plate_2", "silver", (0, 14, -5.4), (1.4, 1.2, 0.8), segs=7, rings=3))
    return F, [eye_at(0, 14.1, -6.15, 0.6)]


def ring_pts(pts):
    """loft の輪 (点の列) をそのまま返す (ck の ring と同じ並び)"""
    return pts


def build_c(k):
    """C 芯のまわりの小石: 星の光の芯のまわりを、六つの小さな岩が輪になって回る。芯には銀の籠。"""
    F = []
    F += part("core", ellipsoid("core", "star", (0, 14, 0), (2.6, 2.6, 2.6), segs=9, rings=4))
    for i in range(4):
        a = i / 4 * math.pi
        pts = [(math.cos(a) * 3.0 * math.cos(t), 14 + 3.0 * math.sin(t), math.sin(a) * 3.0 * math.cos(t)) for t in [q / 8 * 2 * math.pi for q in range(9)]]
        F += part(f"cage{i}", tube("core", "silver", pts, ra=0.3, sides=4, hint=(0, 0, 1)))
    for i in range(6):
        a = i / 6 * 2 * math.pi
        y = 14 + (1.5 if i % 2 else -1.5)
        F += part(f"rock{i}", ellipsoid("orbit", "basalt" if i % 2 else "tuff", (math.cos(a) * 8.0, y, math.sin(a) * 8.0), (2.2, 1.8, 2.0), segs=7, rings=3))
        F += part(f"dust{i}", ellipsoid("orbit", "dust", (math.cos(a) * 8.0, y + 1.5, math.sin(a) * 8.0), (1.4, 0.5, 1.3), segs=6, rings=2))
    return F, []


def build(k):
    return {"a": build_a, "b": build_b, "c": build_c}[k](k)


POSES = {
    "idle": {},
    "push": {"ring": (0, 0, 40), "plate_1": (0, 25, 0), "plate_3": (0, -25, 0), "orbit": (0, 30, 0), "core": (-10, 0, 0)},
}

SPEC = dict(
    id="push_stone", short="押し石", kind="上弦の崖の敵 3案 (46 の 4.4)",
    title="押し石 (push_stone) — 宙に浮き、押しの波で崖の外へ押し出す岩・低ポリ + ドット絵 3 案",
    sub=["地に足がなく宙に浮く。近づくと体が縮み光が強まり (予告の輪)、押しの波で人を崖の外へ押し出す。押したあと少し休む。",
         "高さ 1.5 ブロックほど。二段目は押す瞬間 (回る・ずれる・広がる)。"],
    labels=["A 目のある輪石", "B 重ねた石の板", "C 芯のまわりの小石"],
    rows=[("前 3/4", "idle", 212, 12, 300, (0, 14, 0), 9.0),
          ("横", "idle", 270, 4, 300, (0, 14, 0), 9.0),
          ("押す", "push", 222, 10, 300, (0, 14, 0), 9.0),
          ("上から", "idle", 200, 60, 300, (0, 14, 0), 9.0)],
    options={
        "a": dict(name="A 目のある輪石", desc="平たい輪の岩が縦に浮き、輪の中に銀の芯と星の目。押すとき輪が回る。遠くからでも形で分かる。"),
        "b": dict(name="B 重ねた石の板", desc="平たい岩の板三枚が少しずつずれて重なり、間から星の光が漏れる。押すとき板が逆に回ってずれる。"),
        "c": dict(name="C 芯のまわりの小石", desc="星の光の芯 (銀の籠) のまわりを六つの小岩が回る。押すとき小岩が外へ広がる。押しの範囲が見た目で分かる。")},
    extra="月の言葉 (玄武岩・凝灰岩・月の砂・銀・星の光)。骨 3〜8 本。",
    budget="中の敵 500〜900", atlas_h=150, pw=420,
    later="二段目 = 光が強く、縁に星の割れ目。三段目 = 二つに分かれて浮く (押しが二度)。",
)

if __name__ == "__main__":
    run(SPEC, build, PAL, BONES, POSES, hint=256)
