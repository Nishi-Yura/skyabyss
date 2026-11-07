# -*- coding: utf-8 -*-
"""主の見た目の方向 (2026-11-08 の声「中ダンジョンや心臓の後のボスたちは、まがまがしいけど美しさがある見た目にしたい」)。
骨掘りの大百足 (選んだ A の体) に、三つの方向の飾りを着せて比べる。ほかの主へは、選んだ方向を同じ決まりで広げる。
  A 聖骨の飾り箱  : 節ごとに金の透かし枠、背に光る色硝子のひれ、節の上に燭台の火、頭にレースの襟と後光の輪
  B 切り絵の悪夢  : 黒い紙を切り抜いたような平たい節と白い縁、まわりに浮かぶ切り絵の菱形、頭に大きな一つ目、桃色のリボン
  C 茨の花葬      : 体に巻きつく茨と赤い花、頭から垂れる薄紫のヴェール、茨と金の冠、花の芯が光る

    python -I tools/rough/lowpoly/dungeon_bosses/witch_style.py   → docs/review/roughs_boss_style/centipede.png
"""
from __future__ import annotations

import math
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
import roughs as R  # noqa: E402
from roughs import *  # noqa: E402,F401,F403

OUT = R.ROOT / "docs/review/roughs_boss_style"

GOLD = [(96, 66, 20), (150, 108, 36), (204, 160, 64), (240, 206, 110), (255, 238, 180)]
GLASS_A = [(60, 20, 90), (110, 40, 150), (170, 90, 210), (220, 160, 245)]
GLASS_B = [(20, 60, 110), (40, 110, 170), (90, 170, 220), (170, 220, 250)]
FLAME = [(200, 90, 30), (250, 160, 60), (255, 220, 140), (255, 250, 220)]
WAX = [(170, 160, 140), (210, 200, 180), (236, 228, 212)]
LACE = [(180, 176, 170), (220, 216, 210), (246, 244, 240)]
INK = [(10, 10, 14), (22, 20, 26), (36, 32, 40), (52, 46, 56)]
PAPER = [(196, 188, 170), (226, 220, 204), (246, 242, 230)]
PINK = [(150, 30, 80), (210, 60, 120), (246, 120, 170), (255, 190, 214)]
THORN = [(20, 30, 22), (34, 50, 34), (52, 72, 48), (74, 96, 66)]
ROSE = [(80, 8, 20), (130, 16, 34), (180, 34, 52), (220, 80, 90)]
VEIL = [(120, 100, 140), (160, 140, 180), (200, 186, 216), (230, 222, 240)]
LUMEN = [(200, 160, 230), (230, 200, 250), (250, 236, 255)]


def ring(m, name, mat, c, d, r, th=0.45, n=10):
    """向き d に垂直な輪 (細い管で閉じる)"""
    c = np.asarray(c, float)
    d = nrm(np.asarray(d, float))
    a = nrm(np.cross(d, (0, 1, 0)) if abs(d[1]) < 0.9 else np.cross(d, (1, 0, 0)))
    b = np.cross(d, a)
    pts = [c + (a * math.cos(t) + b * math.sin(t)) * r for t in np.linspace(0, 2 * math.pi, n + 1)]
    m.add(name, tub("body", mat, pts, [th] * len(pts), None, sides=4))


def base_body(m, flesh="flesh", plate="bone", legs=True):
    """選んだ A (骨殻の大百足) の体の形だけ。飾りは方向ごとに足す"""
    m.bone("body", "root", (0, 8, 0))
    path = R.centipede_path()
    segs = []
    for i, p in enumerate(path):
        r = 7.0 - 2.2 * (i / len(path)) ** 1.4
        d = nrm(path[min(i + 1, len(path) - 1)] - path[max(i - 1, 0)])
        m.add(f"seg{i}", ell("body", flesh, p, (r * 1.15, r * 0.8, r * 0.9), segs=10, rings=3), core=True)
        top = p + np.array([0, r * 0.62, 0])
        if plate:
            m.add(f"plate{i}", ell("body", plate, top, (r * 1.35, r * 0.34, r * 0.72), segs=10, rings=2, rx=-math.degrees(math.atan2(d[1], abs(d[2]) + 1e-6))))
        if legs and i >= 3:
            for s in (1, -1):
                hip = p + np.array([s * r * 0.9, -r * 0.2, 0])
                knee = hip + np.array([s * r * 1.1, r * 0.6, 0.5])
                foot = np.array([hip[0] + s * r * 1.8, 0.4, p[2] + 1.0])
                m.add(f"leg{i}{s}", tub("body", plate or flesh, [hip, knee, foot], [1.1, 0.8, 0.25], None, sides=5))
        segs.append((p, r, d, top))
    head = path[0] + np.array([0, 2, -4])
    return segs, head


def style_a():
    """A 聖骨の飾り箱"""
    m = Mount("style_a", "A 聖骨の飾り箱")
    segs, head = base_body(m)
    for i, (p, r, d, top) in enumerate(segs):
        ring(m, f"frame{i}", "gold", p, d, r * 1.08, th=0.42)
        if i % 2 == 0:
            # 色硝子のひれ (光る)
            poly = [top + np.array([0, 0.4, -r * 0.5]), top + np.array([0, r * 2.4, -r * 0.2]), top + np.array([0, r * 1.9, r * 0.7]), top + np.array([0, 0.4, r * 0.5])]
            m.add(f"glass{i}", slab("body", "glass_a" if i % 4 == 0 else "glass_b", poly, (1, 0, 0), 0.4))
            m.add(f"glassrim{i}", tub("body", "gold", poly + [poly[0]], [0.3] * 5, None, sides=4))
        else:
            # 燭台と火
            m.add(f"candle{i}", tub("body", "wax", [top, top + np.array([0, 4.0, 0])], [0.9, 0.8], None, sides=6))
            m.add(f"flame{i}", cone("body", "flame", top + np.array([0, 4.0, 0]), (0, 1, 0), 2.8, 0.8, sides=5, rings=1))
    m.add("head", ell("body", "bone", head, (6.4, 4.6, 6.0), segs=10, rings=3))
    for k in range(14):
        a = math.radians(-90 + k * 180 / 13)
        base = head + np.array([math.sin(a) * 5.4, math.cos(a) * 1.0 - 1.0, 3.0])
        m.add(f"lace{k}", cone("body", "lace", base, (math.sin(a) * 0.8, 0.4, 0.4), 6.5, 2.0, sides=3, rings=1))
    ring(m, "halo", "gold", head + np.array([0, 9.0, 4.0]), (0, 0.2, 1), 11.0, th=0.9, n=18)
    for k in range(8):
        a = math.radians(k * 45)
        m.add(f"halospike{k}", cone("body", "gold", head + np.array([math.cos(a) * 11.0, 9.0 + math.sin(a) * 11.0, 4.0]), (math.cos(a), math.sin(a), 0), 4.0, 0.9, sides=4, rings=1))
    for s in (1, -1):
        m.add(f"jaw{s}", cone("body", "gold", head + np.array([s * 4.0, -2.6, -3.0]), (s * 0.3, -0.5, -1), 11.0, 2.0, sides=5, rings=3, bend=(-s * 4.0, -2.0, 0)))
        R.eye(m, "body", head + np.array([s * 3.2, 1.8, -4.6]), 1.1, (s, 0.3, -1), (220, 160, 255), glow=True)
    m.pal = R.bpal({"bone": M_(R.BONE, lit=0.34), "flesh": M_(INK, streak=0.04), "gold": M_(GOLD, lit=0.4), "glass_a": M_(GLASS_A, base=0.6, lit=0.2),
                    "glass_b": M_(GLASS_B, base=0.6, lit=0.2), "wax": M_(WAX), "flame": M_(FLAME, base=0.7, lit=0.1), "lace": M_(LACE, lit=0.3)},
                   glow=["glass_a", "glass_b", "flame"])
    m.detail = lambda c, col, glow: (speckle(c, col, ["bone"], (120, 110, 92), density=0.05, size=0.8), glow)
    m.view = dict(center=(0, 14, 8), scale=2.6)
    m.caption = ["節ごとに金の透かし枠、背に光る色硝子のひれ", "節の上に燭台の火、頭にレースの襟と後光の輪", "体は黒、骨は白、金と色硝子で飾る（聖堂の飾り箱）"]
    return m


def style_b():
    """B 切り絵の悪夢"""
    m = Mount("style_b", "B 切り絵の悪夢")
    segs, head = base_body(m, flesh="ink", plate=None, legs=False)
    for i, (p, r, d, top) in enumerate(segs):
        # 平たい切り絵の節 (縦の板) と白い縁
        w = r * 1.5
        poly = [p + np.array([0, -r * 0.6, 0]), p + np.array([0, r * 0.2, -w * 0.5]), p + np.array([0, r * 1.6, -w * 0.15]), p + np.array([0, r * 2.2, 0]),
                p + np.array([0, r * 1.6, w * 0.15]), p + np.array([0, r * 0.2, w * 0.5])]
        m.add(f"cut{i}", slab("body", "ink", poly, (1, 0, 0), 0.6))
        m.add(f"cutrim{i}", tub("body", "paper", poly + [poly[0]], [0.28] * (len(poly) + 1), None, sides=4))
        if i >= 3:
            for s in (1, -1):
                hip = p + np.array([s * r * 0.8, -r * 0.2, 0])
                m.add(f"leg{i}{s}", tub("body", "paper", [hip, hip + np.array([s * r * 1.4, r * 0.9, 0]), np.array([hip[0] + s * r * 2.0, 0.4, p[2]])], [0.5, 0.4, 0.2], None, sides=4))
    # まわりに浮かぶ切り絵の菱形 (桃色・白)
    for k in range(16):
        a = math.radians(k * 22.5)
        c = np.array([math.cos(a) * 16, 24 + 8 * math.sin(a * 2), 12 + math.sin(a) * 30])
        poly = [c + np.array([0, 6.0, 0]), c + np.array([3.6, 0, 0]), c + np.array([0, -6.0, 0]), c + np.array([-3.6, 0, 0])]
        m.add(f"float{k}", slab("body", "pink" if k % 2 else "paper", poly, (0, 0, 1), 0.3))
    # 頭: 黒い円盤に大きな一つ目
    m.add("head", ell("body", "ink", head, (7.0, 7.0, 2.4), segs=12, rings=2))
    ring(m, "headrim", "paper", head, (0, 0, -1), 7.0, th=0.45, n=16)
    m.add("bigeye", ell("body", "paper", head + np.array([0, 0.5, -2.2]), (4.2, 3.0, 0.6), segs=12, rings=2))
    m.eyes.append(dict(c=head + np.array([0, 0.5, -2.8]), r=2.4, dir=(0, 0, -1), a=1.8, b=1.8, bone="body", iris=(230, 40, 110), lid=(10, 10, 14), pa=0.5, pb=0.5, glow=True))
    for k in range(7):
        a = math.radians(-60 + k * 20)
        m.add(f"lash{k}", cone("body", "ink", head + np.array([math.sin(a) * 4.6, 3.2 + math.cos(a) * 0.8, -2.6]), (math.sin(a), 1, -0.3), 3.0, 0.4, sides=3, rings=1))
    # リボン
    rib = [head + np.array([0, 4, 2])] + [np.array([math.sin(t) * 8, 24 - t * 1.4, 10 + t * 7]) for t in np.linspace(0, 7, 8)]
    m.add("ribbon", tub("body", "pink", rib, [1.6] * len(rib), None, sides=3))
    m.pal = R.bpal({"ink": M_(INK, lit=0.25, streak=0.02), "paper": M_(PAPER, lit=0.3), "pink": M_(PINK, base=0.6, lit=0.25)}, glow=["pink"])
    m.detail = lambda c, col, glow: (col, glow)
    m.view = dict(center=(0, 14, 8), scale=2.6)
    m.caption = ["黒い紙を切り抜いたような平たい節と白い縁", "まわりに浮かぶ切り絵の菱形、頭に大きな一つ目", "黒・生成り・桃色の三色だけ（絵本の悪夢）"]
    return m


def style_c():
    """C 茨の花葬"""
    m = Mount("style_c", "C 茨の花葬")
    segs, head = base_body(m, flesh="thorn", plate="bone")
    # 体に巻きつく茨 (らせん) と棘
    pts = []
    for i, (p, r, d, top) in enumerate(segs):
        for k in range(3):
            t = (i * 3 + k) / (len(segs) * 3) * 2 * math.pi * 5
            pts.append(p + np.array([math.cos(t) * r * 1.05, math.sin(t) * r * 0.85, 0]))
    m.add("vine", tub("body", "thorn", pts, [0.6] * len(pts), None, sides=4))
    for j in range(0, len(pts), 3):
        q = pts[j]
        m.add(f"spike{j}", cone("body", "thorn", q, (0.3, 1, 0.2), 1.6, 0.35, sides=3, rings=1))
    # 赤い花 (背に一つおき) と光る芯
    for i, (p, r, d, top) in enumerate(segs):
        if i % 2 == 1:
            c = top + np.array([0, r * 0.4, 0])
            for k in range(5):
                a = math.radians(k * 72)
                m.add(f"petal{i}{k}", ell("body", "rose", c + np.array([math.cos(a) * 2.6, 0.6, math.sin(a) * 2.6]), (3.0, 1.1, 2.2), segs=6, rings=2, ry=-math.degrees(a)))
            m.add(f"stamen{i}", ell("body", "lumen", c + np.array([0, 0.6, 0]), (1.5, 1.5, 1.5), segs=6, rings=2))
    m.add("head", ell("body", "bone", head, (6.4, 4.6, 6.0), segs=10, rings=3))
    # 茨と金の冠
    for k in range(9):
        a = math.radians(k * 40)
        base = head + np.array([math.cos(a) * 5.0, 3.8, math.sin(a) * 5.0])
        m.add(f"crown{k}", cone("body", "gold" if k % 2 else "thorn", base, (math.cos(a) * 0.3, 1, math.sin(a) * 0.3), 7.0 if k % 2 else 5.0, 1.1, sides=4, rings=1))
    ring(m, "crownband", "gold", head + np.array([0, 3.8, 0]), (0, 1, 0), 5.0, th=0.5, n=12)
    # ヴェール (頭の後ろから垂れる薄紫の幕)
    for k in range(5):
        x = -6 + k * 3
        poly = [head + np.array([x, 3.0, 2.0]), head + np.array([x + 3, 3.0, 2.0]), head + np.array([x + 4.6, -12.0 - k % 2 * 3, 16.0]), head + np.array([x - 1.6, -12.0 - (k + 1) % 2 * 3, 16.0])]
        m.add(f"veil{k}", slab("body", "veil", poly, (0, 0.6, -0.8), 0.25))
    for s in (1, -1):
        m.add(f"jaw{s}", cone("body", "bone", head + np.array([s * 4.0, -2.6, -3.0]), (s * 0.3, -0.5, -1), 11.0, 2.0, sides=5, rings=3, bend=(-s * 4.0, -2.0, 0)))
        R.eye(m, "body", head + np.array([s * 3.2, 1.8, -4.6]), 1.1, (s, 0.3, -1), (230, 60, 80), glow=True)
    m.pal = R.bpal({"bone": M_(R.BONE, lit=0.34), "thorn": M_(THORN, lit=0.26), "rose": M_(ROSE, lit=0.3), "gold": M_(GOLD, lit=0.4),
                    "veil": M_(VEIL, lit=0.3), "lumen": M_(LUMEN, base=0.7, lit=0.1)}, glow=["lumen"])
    m.detail = lambda c, col, glow: (speckle(c, col, ["bone"], (120, 110, 92), density=0.05, size=0.8), glow)
    m.view = dict(center=(0, 14, 8), scale=2.6)
    m.caption = ["体に巻きつく茨と、背に赤い花（芯が光る）", "頭に茨と金の冠、後ろへ垂れる薄紫のヴェール", "葬列の花のように、暗い緑と赤と金"]
    return m


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    R.SUBJECTS["boss_style"] = ("主の見た目の方向（骨掘りの大百足で比べる）", [style_a, style_b, style_c])
    R.OUT = OUT
    R.sheet("boss_style")


if __name__ == "__main__":
    sys.stdout.reconfigure(encoding="utf-8")
    main()
