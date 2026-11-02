# -*- coding: utf-8 -*-
"""地下のダンジョンの主 4 体の形のラフ (docs/design/55 の 4 章の 1)。主ごとに三案。
部品は乗り物の低ポリの部品 (tools/gen/mobs/mounts_lp/mlp.py)。主ごとに一枚の比べシートを書く。

    python -I tools/rough/lowpoly/dungeon_bosses/roughs.py [centipede|octopus|lizard|giant ...]   → docs/review/roughs_dungeon_bosses/<主>.png
"""
from __future__ import annotations

import math
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[3]
sys.path.insert(0, str(ROOT / "tools/gen/mobs/mounts_lp"))
from mlp_extra import *  # noqa: E402,F401,F403

OUT = ROOT / "docs/review/roughs_dungeon_bosses"


def bpal(mats, glow=()):
    base = {"eye": M_(EYE, base=0.5, lit=0.2, noise=0.0, min=0)}
    base.update(mats)
    return dict(mats=base, glow_mats=list(glow))


def curve(pts, n):
    """折れ線 pts を n 点の滑らかな列に (Catmull-Rom)。"""
    P = [np.asarray(p, float) for p in pts]
    P = [P[0]] + P + [P[-1]]
    out = []
    segs = len(P) - 3
    for i in range(n):
        t = i / (n - 1) * segs
        k = min(int(t), segs - 1)
        u = t - k
        p0, p1, p2, p3 = P[k], P[k + 1], P[k + 2], P[k + 3]
        out.append(0.5 * ((2 * p1) + (-p0 + p2) * u + (2 * p0 - 5 * p1 + 4 * p2 - p3) * u * u + (-p0 + 3 * p1 - 3 * p2 + p3) * u ** 3))
    return out


def eye(m, bone, c, r, d, iris, glow=False, lid=(20, 20, 24)):
    c = np.asarray(c, float)
    m.add(f"eye{len(m.eyes)}", ell(bone, "eye", c, (r, r, r), segs=8, rings=2))
    m.eyes.append(dict(c=c, r=r * 1.08, dir=tuple(nrm(np.asarray(d, float))), a=r * 0.8, b=r * 0.8, bone=bone, iris=iris, lid=lid, pa=0.5, pb=0.5, glow=glow))


# ====================================================================== 骨掘りの大百足 (鯨骨の坑)

BONE = [(110, 100, 84), (150, 140, 120), (190, 180, 158), (220, 212, 192), (240, 234, 218)]
FLESH = [(40, 22, 22), (62, 34, 32), (88, 50, 44), (116, 70, 60)]
STONE = [(40, 40, 46), (58, 58, 66), (80, 80, 90), (104, 104, 114)]
ORE = [(150, 90, 30), (210, 140, 50), (250, 196, 96), (255, 236, 170)]


def centipede_path(rear=True):
    pts = [(0, 34, -36), (0, 30, -28), (0, 20, -18), (0, 10, -6), (0, 7, 8), (0, 7, 22), (0, 7, 36), (4, 7, 50), (10, 7, 62)]
    if not rear:
        pts = [(0, 12, -36), (0, 10, -24), (0, 8, -10), (0, 7, 4), (0, 7, 18), (-4, 7, 32), (-8, 7, 46), (-6, 7, 60)]
    return curve(pts, 14)


def centipede_a():
    """A 骨殻の大百足: 白い骨の甲板を重ねた長い体、節ごとに尖った脚、頭は鶴嘴のような大顎。前の三分の一を持ち上げる"""
    m = Mount("cent_a", "骨殻の大百足")
    m.bone("body", "root", (0, 8, 0))
    path = centipede_path()
    for i, p in enumerate(path):
        r = 7.0 - 2.2 * (i / len(path)) ** 1.4
        d = nrm(path[min(i + 1, len(path) - 1)] - path[max(i - 1, 0)])
        m.add(f"seg{i}", ell("body", "flesh", p, (r * 1.15, r * 0.8, r * 0.9), segs=10, rings=3), core=True)
        # 骨の甲板 (背に一枚、横へ反る)
        top = p + np.array([0, r * 0.62, 0])
        m.add(f"plate{i}", ell("body", "bone", top, (r * 1.35, r * 0.34, r * 0.72), segs=10, rings=2, rx=-math.degrees(math.atan2(d[1], abs(d[2]) + 1e-6))))
        m.add(f"spine{i}", cone("body", "bone", top + np.array([0, r * 0.2, 0]), (0, 1, 0.3), r * 0.5, r * 0.3, sides=4, rings=1))
        if i >= 3:
            for s in (1, -1):
                hip = p + np.array([s * r * 0.9, -r * 0.2, 0])
                knee = hip + np.array([s * r * 1.1, r * 0.6, 0.5])
                foot = np.array([hip[0] + s * r * 1.8, 0.4, p[2] + 1.0])
                m.add(f"leg{i}{s}", tub("body", "bone", [hip, knee, foot], [1.1, 0.8, 0.25], None, sides=5))
    head = path[0] + np.array([0, 2, -4])
    m.add("head", ell("body", "bone", head, (6.4, 4.6, 6.0), segs=10, rings=3))
    for s in (1, -1):
        m.add(f"jaw{s}", cone("body", "flesh", head + np.array([s * 4.0, -2.6, -3.0]), (s * 0.3, -0.5, -1), 11.0, 2.2, sides=5, rings=3, bend=(-s * 4.0, -2.0, 0)))
        m.add(f"pick{s}", cone("body", "bone", head + np.array([s * 4.6, -1.6, -4.0]), (s * 0.5, -0.9, -0.6), 9.0, 1.8, sides=4, rings=2, bend=(0, -2.0, -2.0)))
        eye(m, "body", head + np.array([s * 3.2, 1.8, -4.6]), 1.1, (s, 0.3, -1), (240, 120, 60), glow=True)
        m.add(f"antenna{s}", tub("body", "bone", [head + np.array([s * 2, 3, -3]), head + np.array([s * 7, 9, -8]), head + np.array([s * 12, 10, -6])], [0.7, 0.5, 0.2], None, sides=4))
    m.pal = bpal({"bone": M_(BONE, lit=0.34), "flesh": M_(FLESH, streak=0.06)})
    m.detail = lambda c, col, glow: (speckle(c, col, ["bone"], (120, 110, 92), density=0.06, size=0.8), glow)
    m.view = dict(center=(0, 14, 8), scale=2.6)
    m.caption = ["骨の甲板を重ねた長い体（14 節）", "鶴嘴のような大顎と節ごとの尖った脚", "前の三分の一を持ち上げて構える"]
    return m


def centipede_b():
    """B 巨神の背骨の百足: 掘り出された巨神の背骨そのものが動く。節は椎骨 (棘突起つき)、脚は肋骨、頭は鯨の頭骨の欠片に二本の牙"""
    m = Mount("cent_b", "背骨の百足")
    m.bone("body", "root", (0, 8, 0))
    path = centipede_path()
    for i, p in enumerate(path):
        r = 6.4 - 2.0 * (i / len(path)) ** 1.3
        m.add(f"vert{i}", ell("body", "bone", p, (r, r * 0.8, r * 0.75), segs=8, rings=2), core=True)
        m.add(f"disc{i}", ell("body", "flesh", p + np.array([0, 0, r * 0.75]), (r * 0.8, r * 0.6, r * 0.35), segs=8, rings=2))
        m.add(f"process{i}", cone("body", "bone", p + np.array([0, r * 0.6, 0]), (0, 1, 0.45), r * 1.4, r * 0.45, sides=4, rings=2, rb=r * 0.2))
        if i >= 2:
            for s in (1, -1):
                a = p + np.array([s * r * 0.8, 0, 0])
                rib = [a, a + np.array([s * r * 1.6, r * 0.8, 0]), np.array([a[0] + s * r * 2.6, 0.6, p[2] - 1.0])]
                m.add(f"rib{i}{s}", tub("body", "bone", rib, [1.3, 1.0, 0.5], None, sides=5))
    head = path[0] + np.array([0, 3, -5])
    m.add("skull", lof("body", "bone", [rsec(head[2] + 5, head[1] + 4, head[1] - 4, 6, n=12), rsec(head[2], head[1] + 5, head[1] - 4, 7.5, n=12),
                                       rsec(head[2] - 6, head[1] + 3, head[1] - 3, 5.5, n=12), rsec(head[2] - 11, head[1] + 1, head[1] - 2, 2.6, n=12)]))
    for s in (1, -1):
        m.add(f"tusk{s}", cone("body", "bone", head + np.array([s * 4.0, -2.0, -6.0]), (s * 0.3, -0.4, -1), 13.0, 1.8, sides=5, rings=3, bend=(s * 1.0, 3.0, 0)))
        m.add(f"socket{s}", ell("body", "eye", head + np.array([s * 4.4, 2.2, -2.0]), (1.6, 1.4, 1.6), segs=7, rings=2))
        m.eyes.append(dict(c=head + np.array([s * 4.4, 2.2, -2.0]), r=1.7, dir=(s, 0.2, -0.6), a=0.7, b=0.7, bone="body", iris=(120, 230, 240), lid=(10, 10, 12),
                           pa=0.5, pb=0.5, glow=True))
    m.add("lamp", ell("body", "ore", head + np.array([0, 6.0, 1.0]), (1.8, 2.2, 1.8), segs=8, rings=2))
    m.add("lamp_chain", tub("body", "stone", [head + np.array([0, 4.5, 1.0]), head + np.array([0, 6.0, 1.0])], [0.5, 0.5], None, sides=4))
    m.pal = bpal({"bone": M_(BONE, lit=0.34), "flesh": M_(FLESH), "ore": M_(ORE, base=0.64, lit=0.2), "stone": M_(STONE)}, glow=["ore"])
    m.detail = lambda c, col, glow: (speckle(c, col, ["bone"], (120, 110, 92), density=0.07, size=0.8), glow)
    m.view = dict(center=(0, 14, 8), scale=2.6)
    m.caption = ["掘り出された巨神の背骨そのもの", "節は椎骨、脚は肋骨、頭は鯨の頭骨と二本の牙", "額に坑夫の灯り（光る）"]
    return m


def centipede_c():
    """C 鉱脈の甲百足: 短く太い体を岩と鉱石の甲で覆う。頭は掘り進むための円錐の角、甲の継ぎ目に光る鉱脈"""
    m = Mount("cent_c", "鉱脈の甲百足")
    m.bone("body", "root", (0, 8, 0))
    path = curve([(0, 26, -30), (0, 20, -22), (0, 12, -12), (0, 10, 0), (0, 10, 14), (0, 9, 28), (0, 8, 40)], 10)
    for i, p in enumerate(path):
        r = 9.0 - 2.6 * (i / len(path)) ** 1.3
        m.add(f"seg{i}", lof("body", "stone", [rsec(p[2] - r * 0.55, p[1] + r * 0.9, p[1] - r * 0.7, r * 1.2, mid=p[1], n=10, p=3.0),
                                              rsec(p[2] + r * 0.55, p[1] + r * 0.8, p[1] - r * 0.7, r * 1.1, mid=p[1], n=10, p=3.0)]), core=True)
        m.add(f"seam{i}", ell("body", "ore", p + np.array([0, 0, r * 0.6]), (r * 1.05, r * 0.75, 0.6), segs=10, rings=2))
        for j in range(2):
            a = math.radians(30 + 60 * j + 13 * i)
            m.add(f"crystal{i}{j}", cone("body", "ore", p + np.array([math.cos(a) * r * 0.9 * (1 if j else -1), r * 0.7, 0]), (math.cos(a) * 0.5 * (1 if j else -1), 1, 0.2),
                                         r * 0.6, r * 0.25, sides=5, rings=1))
        if i >= 2:
            for s in (1, -1):
                hip = p + np.array([s * r * 1.0, -r * 0.4, 0])
                m.add(f"leg{i}{s}", tub("body", "stone", [hip, hip + np.array([s * r * 0.8, -1.0, -1.0]), np.array([hip[0] + s * r * 1.2, 0.5, p[2] - 2.0])],
                                        [1.6, 1.3, 0.6], None, sides=5))
    head = path[0] + np.array([0, 1, -6])
    m.add("head", ell("body", "stone", head, (7.0, 6.0, 6.0), segs=10, rings=3))
    m.add("drill", cone("body", "ore", head + np.array([0, 0, -4.0]), (0, -0.1, -1), 16.0, 5.0, sides=8, rings=4))
    for s in (1, -1):
        eye(m, "body", head + np.array([s * 4.8, 2.6, -3.0]), 1.3, (s, 0.4, -0.6), (255, 200, 90), glow=True)
        m.add(f"mand{s}", cone("body", "stone", head + np.array([s * 5.0, -3.0, -3.0]), (s * 0.4, -0.3, -1), 8.0, 2.0, sides=5, rings=2, bend=(-s * 2.5, 0, 0)))
    m.pal = bpal({"stone": M_(STONE, lit=0.36, streak=0.04), "ore": M_(ORE, base=0.64, lit=0.2)}, glow=["ore"])
    m.detail = lambda c, col, glow: (plates(c, col, ["stone"], size=3.0, edge=0.06, k_edge=0.7), glow)
    m.view = dict(center=(0, 14, 4), scale=2.6)
    m.caption = ["短く太い体を岩と鉱石の甲で覆う（10 節）", "頭は掘り進むための円錐の角", "甲の継ぎ目と背の結晶が光る"]
    return m


# ====================================================================== 洞の大蛸 (潮の洞)

OCT = [(70, 30, 40), (104, 46, 56), (140, 66, 74), (176, 92, 96), (210, 128, 126)]
SUCK = [(200, 160, 150), (226, 192, 180), (244, 220, 208)]
BARN = [(150, 150, 140), (190, 190, 178), (222, 222, 210)]
SHELL = [(110, 70, 40), (160, 110, 64), (204, 152, 96), (232, 196, 140)]
DEEP = [(30, 20, 60), (50, 32, 92), (74, 50, 126), (104, 74, 160), (140, 110, 196)]
LUM = [(80, 200, 220), (140, 236, 246), (220, 252, 255)]


def arm(m, i, n, base_r, length, mat, sucker="suck", lift=0.0, curl=1.0, spread=1.0, y0=10.0):
    a = math.radians(i * 360 / n + 22.5)
    d = np.array([math.sin(a), 0, -math.cos(a)])
    pts = [np.array([0, y0, 0]) + d * 6]
    for k in range(1, 6):
        t = k / 5
        pts.append(np.array([0, y0, 0]) + d * (6 + length * t * spread) + np.array([0, -y0 * min(1, t * 2) + lift * math.sin(t * math.pi), 0])
                   + np.array([math.cos(a), 0, math.sin(a)]) * curl * 6 * t * t)
    pts[-1][1] = max(pts[-1][1], 0.5) + curl * 4
    radii = [base_r * (1 - 0.8 * k / 5) for k in range(6)]
    m.add(f"arm{i}", tub("body", mat, pts, radii, None, sides=6))
    for k in range(1, 5):
        p = pts[k] + np.array([0, -radii[k] * 0.7, 0])
        m.add(f"suck{i}{k}", ell("body", sucker, p, (radii[k] * 0.45, 0.4, radii[k] * 0.45), segs=6, rings=1))
    return pts


def octopus_a():
    """A 洞の大蛸: 大きくふくらんだ頭（外套）に八本の太い腕、大きな目、外套にフジツボ"""
    m = Mount("oct_a", "洞の大蛸")
    m.bone("body", "root", (0, 10, 0))
    mantle = [rsec(z, 36 - 0.02 * z * z, 12, 13 - 0.012 * z * z, mid=24, n=14) for z in (-12, -8, -2, 4, 10, 15)]
    m.add("mantle", lof("body", "oct", mantle), core=True)
    m.add("base", ell("body", "oct", (0, 12, 0), (12, 6, 12), segs=12, rings=3))
    for i in range(8):
        arm(m, i, 8, 4.4, 30.0, "oct")
    for s in (1, -1):
        eye(m, "body", (s * 10.5, 20, -6), 3.0, (s, 0.1, -0.6), (240, 200, 90), lid=(90, 40, 50))
    for k in range(9):
        a = math.radians(k * 40)
        m.add(f"barn{k}", cone("body", "barn", (math.sin(a) * 8, 32 - 2 * (k % 3), math.cos(a) * 8 + 2), (math.sin(a) * 0.4, 1, math.cos(a) * 0.4), 2.4, 1.6, sides=5, rings=1))
    m.pal = bpal({"oct": M_(OCT, streak=0.04, ao=0.12), "suck": M_(SUCK), "barn": M_(BARN, lit=0.34)})
    m.detail = lambda c, col, glow: (spots(c, col, ["oct"], (60, 24, 34), scale=0.22, thr=0.68, a=0.7), glow)
    m.view = dict(center=(0, 16, 0), scale=2.5)
    m.caption = ["大きくふくらんだ外套と八本の太い腕", "大きな目・外套にフジツボ", "腕を一本ずつ切ると攻め手が減る"]
    return m


def octopus_b():
    """B 殻負いの大蛸: 珊瑚とフジツボに覆われた巨大な巻貝の殻を背負い、殻の口から腕を出す"""
    m = Mount("oct_b", "殻負いの大蛸")
    m.bone("body", "root", (0, 10, 0))
    for k in range(7):
        t = k / 6
        c = np.array([0, 18 + 14 * t, 8 - 4 * t])
        r = 15 - 9 * t
        m.add(f"whorl{k}", ell("body", "shell", c, (r, r * 0.55, r * 0.9), segs=12, rings=3, rx=-20 + 10 * t), core=k == 0)
    m.add("spire", cone("body", "shell", (0, 34, 4), (0, 1, 0.3), 10.0, 4.0, sides=6, rings=2))
    m.add("lip", ell("body", "shell", (0, 13, -6), (13, 8, 4), segs=12, rings=2))
    m.add("head", ell("body", "oct", (0, 12, -9), (8, 7, 6), segs=10, rings=3))
    for i in range(8):
        if i in (3, 4, 5):
            continue
        arm(m, i, 8, 3.8, 28.0, "oct", curl=1.4)
    for s in (1, -1):
        eye(m, "body", (s * 5.0, 15, -13), 2.4, (s * 0.6, 0.2, -0.8), (240, 200, 90), lid=(90, 40, 50))
    for k in range(12):
        a = math.radians(k * 30 + 7)
        m.add(f"coral{k}", cone("body", "coral" if k % 2 else "barn", (math.sin(a) * 11, 24 + 6 * math.cos(a * 2), math.cos(a) * 9 + 6), (math.sin(a), 0.8, math.cos(a)), 4.0, 1.4,
                                 sides=5, rings=2, bend=(0, 1.0, 0)))
    m.pal = bpal({"oct": M_(OCT, streak=0.04), "suck": M_(SUCK), "barn": M_(BARN, lit=0.34), "shell": M_(SHELL, lit=0.34, streak=0.08),
                  "coral": M_([(160, 50, 60), (210, 80, 80), (240, 130, 120)])})
    m.detail = lambda c, col, glow: (bands_y(c, col, ["shell"], 3.0, 0.8, k=0.82, wobble=1.0), glow)
    m.view = dict(center=(0, 16, 0), scale=2.5)
    m.caption = ["珊瑚に覆われた巨大な巻貝の殻を背負う", "殻の口から五本の腕と大きな目", "殻は硬く、腕と頭を狙う"]
    return m


def octopus_c():
    """C 灯りの深蛸: 深い海の蛸。紫の外套に光る斑、腕のあいだの膜 (傘)、長い二本の捕まえ腕"""
    m = Mount("oct_c", "灯りの深蛸")
    m.bone("body", "root", (0, 10, 0))
    mantle = [rsec(z, 40 - 0.03 * z * z, 16, 10 - 0.012 * z * z, mid=26, n=14) for z in (-10, -6, 0, 6, 11)]
    m.add("mantle", lof("body", "deep", mantle), core=True)
    for s in (1, -1):
        m.add(f"fin{s}", ell("body", "deep", (s * 9, 36, 2), (6, 1.2, 4), segs=8, rings=2, rz=s * 30))
        eye(m, "body", (s * 8.0, 22, -6), 2.6, (s, 0.1, -0.6), (140, 240, 250), glow=True, lid=(30, 20, 60))
    m.add("base", ell("body", "deep", (0, 16, 0), (10, 6, 10), segs=12, rings=3))
    tips = []
    for i in range(8):
        pts = arm(m, i, 8, 3.2, 26.0, "deep", sucker="lum", y0=16.0, curl=0.6)
        tips.append(pts[3])
    for i in range(8):
        a, b = tips[i], tips[(i + 1) % 8]
        m.add(f"web{i}", slab("body", "deep", [np.array([0, 16.0, 0]), a, b], (0, 1, 0), 0.5))
    for s in (1, -1):
        m.add(f"hunt{s}", tub("body", "deep", curve([(s * 3, 14, -6), (s * 6, 10, -22), (s * 4, 18, -38), (s * 2, 26, -44)], 6), [1.6, 1.4, 1.2, 1.0, 1.6, 2.2], None, sides=6))
    m.pal = bpal({"deep": M_(DEEP, lit=0.32, streak=0.03), "lum": M_(LUM, base=0.64, lit=0.2)}, glow=["lum"])

    def detail(c, col, glow):
        P = c["P"]
        sel = np.isin(c["mats"], ["deep"]) & c["owned"]
        n = RC.hash3(np.floor(P / 1.6), 9)
        dot = sel & (n < 0.05)
        col = np.where(dot[..., None], np.array([150, 240, 250]), col)
        return col, glow | dot
    m.detail = detail
    m.view = dict(center=(0, 18, -4), scale=2.4)
    m.caption = ["深い海の蛸。紫の外套に光る斑", "腕のあいだの膜（傘）と長い二本の捕まえ腕", "暗い洞で光る斑が目印になる"]
    return m


# ====================================================================== 晶の雷蜥蜴 (雷晶の空洞)

SCL = [(30, 34, 48), (46, 52, 70), (64, 72, 96), (88, 98, 126), (116, 128, 160)]
CRY = [(90, 160, 230), (140, 200, 250), (196, 230, 255), (240, 250, 255)]
BEL = [(120, 120, 130), (160, 160, 170), (196, 196, 204)]


def lizard_body(m, length=1.0, bulk=1.0, neck=0.0):
    m.bone("body", "root", (0, 14, 0))
    zs = [-18 * length, -12 * length, -4 * length, 4 * length, 12 * length, 18 * length]
    for i, z in enumerate(zs):
        pass
    body = [rsec(z, 26 * bulk - abs(z) * 0.18, 10 + abs(z) * 0.1, (9 - abs(z) * 0.15) * bulk, mid=17, n=14) for z in zs]
    m.add("torso", lof("body", "scale", body), core=True)
    head_c = np.array([0, 20 + neck, zs[0] - 8 - neck * 0.4])
    if neck:
        m.add("neck", limb("body", "scale", [np.array([0, 20, zs[0] + 2]), np.array([0, 20 + neck * 0.6, zs[0] - 3]), head_c + np.array([0, 0, 3])], [9, 7, 6], [9, 7, 6], n=10))
    m.add("skull", lof("body", "scale", [rsec(head_c[2] + 4, head_c[1] + 4, head_c[1] - 4, 5.5, n=12), rsec(head_c[2] - 1, head_c[1] + 4.5, head_c[1] - 4, 6, n=12),
                                       rsec(head_c[2] - 7, head_c[1] + 2, head_c[1] - 3, 4, n=12), rsec(head_c[2] - 11, head_c[1] + 0.5, head_c[1] - 2, 2, n=12)]))
    for s in (1, -1):
        eye(m, "body", head_c + np.array([s * 4.6, 2.4, -2.0]), 1.2, (s, 0.3, -0.5), (250, 230, 120), glow=True)
    tail = curve([(0, 16, zs[-1] - 2), (0, 14, zs[-1] + 10), (0, 10, zs[-1] + 22), (0, 8, zs[-1] + 34)], 6)
    m.add("tail", limb("body", "scale", tail, [12 * bulk, 9, 7, 5, 3, 1.4], [10 * bulk, 8, 6, 4.4, 2.8, 1.4], n=10))
    for k, z in ((0, zs[1] + 2), (1, zs[-2])):
        for s in (1, -1):
            hip = np.array([s * 7 * bulk, 14, z])
            knee = np.array([s * 13 * bulk, 10, z - 1])
            foot = np.array([s * 14 * bulk, 1.5, z - 3])
            m.add(f"leg{k}{s}", tub("body", "scale", [hip, knee, foot], [4.0 * bulk, 3.2, 2.6], None, sides=6))
            for j in range(3):
                a = math.radians(-35 + 35 * j)
                m.add(f"claw{k}{s}{j}", cone("body", "horn", foot + np.array([0, -0.6, 0]), (math.sin(a), -0.2, -math.cos(a)), 3.2, 0.8, sides=4, rings=1))
    return head_c, tail, zs


def lizard_a():
    """A 背晶の大蜥蜴: がっしりした四つ足、背骨に沿って大きな雷の結晶の房が並ぶ (満ちると光が強まる)。頭に二本の角"""
    m = Mount("liz_a", "背晶の大蜥蜴")
    head, tail, zs = lizard_body(m, bulk=1.15)
    for k, z in enumerate(np.linspace(zs[0] + 2, zs[-1] + 10, 8)):
        h = 9 - abs(k - 3) * 1.2
        y = 26 * 1.15 - abs(z) * 0.18 - 1 if z < zs[-1] else 16
        for j in (-1, 0, 1):
            m.add(f"cry{k}{j}", cone("body", "cry", (j * 2.2, y, z + j), (j * 0.35, 1, 0.1), h * (0.6 if j else 1.0), 1.8 if j else 2.4, sides=6, rings=1))
    for s in (1, -1):
        m.add(f"horn{s}", cone("body", "horn", head + np.array([s * 3, 3, 2]), (s * 0.4, 0.6, 1), 8.0, 1.4, sides=5, rings=2, bend=(0, 1.0, 0)))
    m.pal = bpal({"scale": M_(SCL, lit=0.34, streak=0.04), "cry": M_(CRY, base=0.64, lit=0.22), "horn": M_(BEL)}, glow=["cry"])
    m.detail = lambda c, col, glow: (scales(c, col, ["scale"], size=1.4, edge=0.12), glow)
    m.view = dict(center=(0, 16, 6), scale=2.7)
    m.caption = ["がっしりした四つ足の大蜥蜴", "背骨に沿って雷の結晶の房が並ぶ（光る）", "結晶が満ちると光が強まり、雷を放つ"]
    return m


def lizard_b():
    """B 晶鎧の蜥蜴: 体を結晶の板が鎧のように覆い、首のまわりに結晶の襟、尾の先は結晶の棍"""
    m = Mount("liz_b", "晶鎧の蜥蜴")
    head, tail, zs = lizard_body(m, bulk=1.0)
    bt = tri_arrays(m.faces_of(["torso"]))
    k = 0
    for z in np.linspace(zs[0] + 3, zs[-1] - 2, 6):
        for a in (-60, -25, 25, 60):
            d = np.array([math.sin(math.radians(a)), math.cos(math.radians(a)), 0])
            p = ray_far(bt, (0, 17, z), d)
            if p is None:
                continue
            m.add(f"plate{k}", ell("body", "cry", p, (3.2, 1.0, 3.6), segs=6, rings=1, rz=-a))
            k += 1
    for j in range(9):
        a = math.radians(-80 + j * 20)
        m.add(f"frill{j}", cone("body", "cry", head + np.array([math.sin(a) * 4, 2 + math.cos(a) * 2, 6]), (math.sin(a), math.cos(a) * 0.8 + 0.4, 0.6), 7.0, 1.6, sides=4, rings=1))
    m.add("club", ell("body", "cry", tail[-1], (4.0, 3.2, 4.4), segs=7, rings=2))
    for j in range(5):
        a = math.radians(j * 72)
        m.add(f"clubspike{j}", cone("body", "cry", tail[-1], (math.cos(a), math.sin(a), 0.3), 4.0, 1.0, sides=4, rings=1))
    m.pal = bpal({"scale": M_(SCL, lit=0.34, streak=0.04), "cry": M_(CRY, base=0.62, lit=0.22), "horn": M_(BEL)}, glow=["cry"])
    m.detail = lambda c, col, glow: (scales(c, col, ["scale"], size=1.3, edge=0.12), glow)
    m.view = dict(center=(0, 16, 6), scale=2.7)
    m.caption = ["体を結晶の板が鎧のように覆う", "首のまわりの結晶の襟・尾の先の結晶の棍", "板が満ちて光ると、割れるまで硬い"]
    return m


def lizard_c():
    """C 雷角の竜蜥蜴: 首の長い細身の竜蜥蜴。枝分かれした結晶の角、尾に沿って結晶の玉 (蓄える器) が並ぶ"""
    m = Mount("liz_c", "雷角の竜蜥蜴")
    head, tail, zs = lizard_body(m, length=0.9, bulk=0.85, neck=10.0)
    for s in (1, -1):
        base = head + np.array([s * 2.6, 4, 2])
        m.add(f"antler{s}", tub("body", "cry", [base, base + np.array([s * 3, 6, 3]), base + np.array([s * 6, 12, 2])], [1.2, 0.9, 0.4], None, sides=5))
        for j, (dy, dz) in enumerate(((4, 4), (8, 1))):
            m.add(f"tine{s}{j}", cone("body", "cry", base + np.array([s * 2.0 * (j + 1), dy, 2.5]), (s * 0.8, 1, -0.4 if j else 0.4), 4.0, 0.6, sides=4, rings=1))
    for k, p in enumerate(tail[1:5]):
        m.add(f"orb{k}", ell("body", "cry", p + np.array([0, 4.0 - k * 0.6, 0]), (2.2 - k * 0.3,) * 3, segs=8, rings=2))
    m.pal = bpal({"scale": M_(SCL, lit=0.34, streak=0.04), "cry": M_(CRY, base=0.64, lit=0.22), "horn": M_(BEL)}, glow=["cry"])
    m.detail = lambda c, col, glow: (scales(c, col, ["scale"], size=1.2, edge=0.12), glow)
    m.view = dict(center=(0, 20, 0), scale=2.6)
    m.caption = ["首の長い細身の竜蜥蜴", "枝分かれした結晶の角", "尾に沿って雷を蓄える結晶の玉（光る）"]
    return m


# ====================================================================== 坑の鍛え巨人 (熔けた坑道)

BAS = [(24, 22, 24), (38, 34, 36), (54, 48, 50), (72, 64, 64), (94, 84, 82)]
MAG = [(160, 50, 16), (220, 96, 30), (252, 156, 56), (255, 214, 120), (255, 244, 200)]
IRON = [(40, 36, 36), (62, 56, 54), (88, 80, 76), (118, 108, 100)]
BRICK = [(80, 34, 24), (112, 50, 34), (146, 70, 46), (176, 96, 66)]


def giant_body(m, hunch=1.0):
    m.bone("body", "root", (0, 30, 0))
    torso = [rsec(z, 62 - abs(z + 2) * 0.6 * hunch, 34, 14 - abs(z) * 0.1, mid=48, n=14) for z in (-10, -5, 0, 5, 10)]
    m.add("torso", lof("body", "basalt", torso), core=True)
    m.add("hips", ell("body", "basalt", (0, 34, 2), (12, 6, 9), segs=10, rings=3))
    head = np.array([0, 60 - 6 * hunch, -10 - 4 * hunch])
    m.add("head", ell("body", "basalt", head, (5, 5, 5), segs=9, rings=2))
    for s in (1, -1):
        eye(m, "body", head + np.array([s * 2.2, 0.8, -4.2]), 0.9, (s * 0.3, 0, -1), (255, 160, 60), glow=True)
        # 脚
        m.add(f"thigh{s}", limb("body", "basalt", [(s * 7, 34, 2), (s * 8, 20, 0), (s * 8, 8, 2)], [9, 8, 7], [9, 8, 7], n=10))
        m.add(f"foot{s}", ell("body", "basalt", (s * 8, 3, -1), (5, 3, 7), segs=8, rings=2))
    return head


def giant_arm(m, s, hand_mat="basalt", hammer=False, tongs=False):
    sh = np.array([s * 15, 56, -2])
    el = np.array([s * 21, 42, -6])
    wr = np.array([s * 22, 28, -8])
    m.add(f"arm{s}", limb("body", "basalt", [sh, el, wr], [9, 8, 7], [9, 8, 7], n=10))
    m.add(f"shoulder{s}", ell("body", "iron", sh + np.array([s * 1, 3, 0]), (7, 5, 7), segs=9, rings=2))
    m.add(f"fist{s}", ell("body", hand_mat, wr + np.array([0, -3, 0]), (5, 5, 5), segs=8, rings=2))
    if hammer:
        m.add(f"haft{s}", tub("body", "iron", [wr + np.array([0, -3, 0]), wr + np.array([0, -3, -14])], [1.2, 1.2], None, sides=6))
        m.add(f"hammer{s}", lof("body", "iron", [oct_ring(wr + np.array([-5, -3, -16]), 7, 7, R=rot_y(90)), oct_ring(wr + np.array([5, -3, -16]), 7, 7, R=rot_y(90))]))
    if tongs:
        for k in (1, -1):
            m.add(f"tong{s}{k}", tub("body", "iron", [wr + np.array([0, -3, 0]), wr + np.array([k * 1.5, -6, -10]), wr + np.array([k * 0.5, -6, -16])], [0.9, 0.8, 0.6], None, sides=5))


def giant_a():
    """A 炉負いの巨人: 前かがみの玄武岩の巨人。背に大きな炉と煙突 (中が光る)、右手は鎚と一体、肌の割れ目から熔けた光"""
    m = Mount("giant_a", "炉負いの巨人")
    giant_body(m, hunch=1.2)
    for s in (1, -1):
        giant_arm(m, s, hammer=s > 0)
    m.add("forge", lof("body", "brick", [oct_ring((0, 46, 14), 18, 22, R=np.eye(3)), oct_ring((0, 46, 26), 16, 20, R=np.eye(3))]))
    m.add("mouth", ell("body", "magma", (0, 44, 26.4), (5, 5, 1.0), segs=8, rings=1))
    m.add("chimney", tub("body", "brick", [(4, 56, 20), (5, 74, 22)], [3.2, 2.6], None, sides=6))
    m.add("smoke_glow", ell("body", "magma", (5, 74.5, 22), (2.4, 1.0, 2.4), segs=6, rings=1))
    m.pal = bpal({"basalt": M_(BAS, lit=0.34, streak=0.05), "magma": M_(MAG, base=0.66, lit=0.2), "iron": M_(IRON, lit=0.36), "brick": M_(BRICK, streak=0.04)},
                 glow=["magma"])

    def detail(c, col, glow):
        P = c["P"]
        sel = np.isin(c["mats"], ["basalt"]) & c["owned"]
        f1, f2, idv = cell3(P, 4.0, 17)
        crack = sel & ((f2 - f1) < 0.03) & (idv > 0.55)
        col = np.where(crack[..., None], np.array([250, 140, 50]), col)
        return col, glow | crack
    m.detail = detail
    m.view = dict(center=(0, 38, 4), scale=1.9)
    m.caption = ["前かがみの玄武岩の巨人", "背に大きな炉と煙突（中が光る）、右手は鎚", "肌の割れ目から熔けた光"]
    return m


def giant_b():
    """B 炉胸の巨人: 胸そのものが開いた炉 (火の口が光る)。肩は金床、左手は火ばさみ、右手は鎚。まっすぐ立つ"""
    m = Mount("giant_b", "炉胸の巨人")
    giant_body(m, hunch=0.2)
    for s in (1, -1):
        giant_arm(m, s, hammer=s > 0, tongs=s < 0)
        m.add(f"anvil{s}", box("body", "iron", (s * 15, 63, -2), (10, 3, 6), taper=(0.7, 0.9), cut=0.3))
    m.add("furnace", ell("body", "magma", (0, 48, -12.6), (7, 6, 1.6), segs=10, rings=2))
    m.add("grate", box("body", "iron", (0, 41, -13), (12, 2, 3)))
    for k in range(3):
        m.add(f"bar{k}", tub("body", "iron", [((k - 1) * 4, 42, -13.4), ((k - 1) * 4, 54, -13.4)], [0.7, 0.7], None, sides=4))
    m.pal = bpal({"basalt": M_(BAS, lit=0.34, streak=0.05), "magma": M_(MAG, base=0.66, lit=0.2), "iron": M_(IRON, lit=0.36)}, glow=["magma"])
    m.detail = lambda c, col, glow: (plates(c, col, ["basalt"], size=5.0, edge=0.05, k_edge=0.6), glow)
    m.view = dict(center=(0, 38, 0), scale=1.9)
    m.caption = ["胸そのものが開いた炉（火の口が光る）", "肩は金床、左手は火ばさみ、右手は鎚", "まっすぐ立つ鍛冶頭"]
    return m


def giant_c():
    """C 坑の鍛冶車: 人の形でない坑道の機械。炉を積んだ車の体に四本の脚、前に二本の鎚の腕 (蟹のよう)"""
    m = Mount("giant_c", "坑の鍛冶車")
    m.bone("body", "root", (0, 20, 0))
    m.add("cart", box("body", "iron", (0, 24, 0), (30, 14, 36), taper=(0.9, 0.9), cut=0.25), core=True)
    m.add("kiln", lof("body", "brick", [ysec(31, 22, 24), ysec(44, 16, 18), ysec(48, 10, 12)]))
    m.add("kiln_mouth", ell("body", "magma", (0, 38, -10.5), (5, 4, 1.0), segs=8, rings=1))
    m.add("stack", tub("body", "brick", [(6, 46, 6), (7, 62, 8)], [2.8, 2.2], None, sides=6))
    for s in (1, -1):
        for k, z in enumerate((-12, 12)):
            hip = np.array([s * 15, 22, z])
            m.add(f"leg{s}{k}", tub("body", "iron", [hip, hip + np.array([s * 8, 6, 0]), np.array([s * 26, 1, z + (2 if k else -2)])], [2.6, 2.2, 1.6], None, sides=6))
        sh = np.array([s * 10, 30, -18])
        el = np.array([s * 16, 36, -28])
        m.add(f"claw_arm{s}", tub("body", "iron", [sh, el, el + np.array([-s * 2, -10, -8])], [2.6, 2.2, 2.0], None, sides=6))
        m.add(f"hammer{s}", box("body", "iron", el + np.array([-s * 2, -12, -10]), (8, 6, 6), taper=(0.9, 0.9), cut=0.3))
        eye(m, "body", (s * 6, 33, -18.2), 1.4, (0, 0, -1), (255, 160, 60), glow=True)
    m.pal = bpal({"iron": M_(IRON, lit=0.36, streak=0.05), "brick": M_(BRICK), "magma": M_(MAG, base=0.66, lit=0.2)}, glow=["magma"])
    m.detail = lambda c, col, glow: (plates(c, col, ["iron"], size=4.0, edge=0.05, k_edge=0.6), glow)
    m.view = dict(center=(0, 26, 0), scale=2.2)
    m.caption = ["人の形でない坑道の機械", "炉を積んだ車の体と四本の脚", "前に二本の鎚の腕（蟹のよう）"]
    return m


SUBJECTS = {
    "centipede": ("骨掘りの大百足（鯨骨の坑・Lv 10）", [centipede_a, centipede_b, centipede_c]),
    "octopus": ("洞の大蛸（潮の洞・Lv 16）", [octopus_a, octopus_b, octopus_c]),
    "lizard": ("晶の雷蜥蜴（雷晶の空洞・Lv 22）", [lizard_a, lizard_b, lizard_c]),
    "giant": ("坑の鍛え巨人（熔けた坑道・Lv 28）", [giant_a, giant_b, giant_c]),
}


def render(m, PW=340, PH=300):
    size, atlas, glow = paint_mount(m, m.pal, m.detail, 256)
    cen, sc = np.array(m.view["center"], float), m.view["scale"]
    ims = [view(m.F, atlas, glow, 215, 14, (PW, PH), cen, sc), view(m.F, atlas, glow, 270, 6, (PW, PH), cen, sc),
           view(m.F, atlas, glow, 330, 26, (PW, PH), cen, sc)]
    tris = sum(len(f.verts) - 2 for f in m.F)
    return ims, tris


def sheet(key):
    title, fns = SUBJECTS[key]
    F = voxel.font
    PW, PH = 340, 300
    cols = []
    for i, fn in enumerate(fns):
        m = fn()
        ims, tris = render(m, PW, PH)
        cols.append((m, ims, tris))
        print(key, "ABC"[i], m.title, tris, flush=True)
    W_ = 24 + 3 * (PW + 16)
    H_ = 80 + 120 + 3 * (PH + 8) + 20
    cv = Image.new("RGB", (W_, H_), (16, 16, 22))
    d = ImageDraw.Draw(cv)
    d.text((20, 14), f"{title} — 三案のラフ", font=F(28), fill=(236, 232, 218))
    d.text((20, 50), "上から: 斜め前 / 横 / 斜め上の後ろ。左の黄色い目盛りが人 (1.8 ブロック)", font=F(15), fill=(176, 176, 192))
    for i, (m, ims, tris) in enumerate(cols):
        x = 20 + i * (PW + 16)
        d.text((x, 80), f"{'ABC'[i]}  {m.title}", font=F(24), fill=(150, 232, 255))
        for j, line in enumerate(m.caption):
            d.text((x, 114 + j * 22), line, font=F(15), fill=(210, 208, 196))
        d.text((x, 114 + 3 * 22), f"{tris} 三角形（ラフ）", font=F(13), fill=(150, 150, 166))
        y = 200
        for im in ims:
            cv.paste(im, (x, y))
            y += PH + 8
    OUT.mkdir(parents=True, exist_ok=True)
    cv.save(OUT / f"{key}.png")
    for i, (m, ims, tris) in enumerate(cols):
        big = Image.new("RGB", (PW * 3 + 16, PH), (16, 16, 22))
        for j, im in enumerate(ims):
            big.paste(im, (j * (PW + 8), 0))
        big.save(OUT / f"{key}_{'abc'[i]}.png")


if __name__ == "__main__":
    sys.stdout.reconfigure(encoding="utf-8")
    for key in (sys.argv[1:] or list(SUBJECTS)):
        sheet(key)
