# -*- coding: utf-8 -*-
"""章の道中のボス 8 体の作り込みのラフ (2026-11-11 の声「次は章の道中のボスたちを作り込んで」)。

老いた歩哨・潮の番人・鯨骨の番兵長・古い灰被り・灰の鍛え手・焼けた羽守・潮招き・雷獣。
今の模型に、そのボスのためだけに考えた細かい部品を足す三案 (案どうし・ボスどうし・終章と月の主と同じ足し方はしない)。
部品は付ける骨の名前で作る。道具は終章のラフ tools/rough/lowpoly/finale_bosses/build.py のものを使う。

    python -X utf8 -I tools/rough/lowpoly/chapter_bosses/build.py [名前 ...]
出力: docs/review/chapter_bosses/<名前>.png
"""
from __future__ import annotations

import importlib.util
import math
import sys
from pathlib import Path

import numpy as np

ROOT = Path(__file__).resolve().parents[4]
spec = importlib.util.spec_from_file_location("fb", ROOT / "tools/rough/lowpoly/finale_bosses/build.py")
fb = importlib.util.module_from_spec(spec)
spec.loader.exec_module(fb)
bs, b, ring, chain, edge_points, nrm, basis, bell, feather = fb.bs, fb.b, fb.ring, fb.chain, fb.edge_points, fb.nrm, fb.basis, fb.bell, fb.feather
V3 = np.array
fb.OUT = ROOT / "docs/review/chapter_bosses"

bs.PAL.update({
    "moss": [(24, 40, 18), (44, 70, 30), (70, 104, 44), (104, 140, 66)],
    "mush": [(120, 60, 40), (170, 96, 60), (210, 140, 96), (236, 200, 160)],
    "boneo": [(80, 72, 52), (130, 120, 92), (180, 170, 136), (220, 212, 180)],
    "rust": [(40, 22, 14), (80, 42, 24), (124, 70, 40), (170, 108, 66)],
    "ironc": [(26, 28, 32), (50, 54, 60), (84, 90, 98), (124, 130, 140)],
    "amber": [(110, 60, 10), (180, 110, 20), (230, 170, 50), (255, 220, 120)],
    "coral": [(110, 30, 40), (170, 60, 70), (220, 110, 110), (250, 170, 160)],
    "kelp": [(20, 40, 24), (34, 66, 38), (54, 96, 52), (84, 130, 72)],
    "shell": [(150, 130, 110), (196, 178, 156), (226, 212, 192), (246, 238, 226)],
    "rope": [(70, 54, 34), (110, 86, 56), (150, 120, 80), (186, 156, 110)],
    "cloth_r": [(50, 18, 20), (84, 30, 32), (120, 46, 46), (156, 70, 66)],
    "ash": [(40, 38, 36), (70, 66, 62), (106, 100, 94), (146, 140, 132)],
    "char2": [(10, 8, 8), (24, 20, 18), (42, 36, 32), (66, 58, 52)],
    "feather_b": [(70, 50, 34), (120, 86, 56), (170, 126, 84), (210, 170, 120)],
    "stormfur": [(40, 50, 66), (66, 80, 102), (102, 120, 146), (150, 170, 196)],
    "g_amber": [(220, 120, 30), (255, 180, 60), (255, 230, 150)],
    "g_orange": [(230, 90, 20), (255, 150, 40), (255, 210, 120)],
    "g_cyan": [(60, 200, 255), (140, 235, 255), (230, 255, 255)],
    "g_blue": [(60, 140, 255), (130, 200, 255), (220, 245, 255)],
    "g_teal": [(30, 170, 160), (90, 230, 210), (190, 255, 240)],
})


def surf(R, bone, d, at=None):
    """骨の形の表の点: 中心から向き d へいちばん遠い頂点 (at を通る線の近く)。"""
    V = np.concatenate([f.verts for f in R.faces if f.bone == bone])
    c = V.mean(0) if at is None else np.asarray(at, float)
    d = nrm(d)
    rel = V - c
    along = rel @ d
    perp = np.linalg.norm(rel - np.outer(along, d), axis=1)
    near = perp < max(1.0, np.percentile(perp, 15))
    i = np.argmax(np.where(near, along, -1e9))
    return V[i]


# ---------------------------------------------------------------- 老いた歩哨 (骨の番兵の変種。盾に琥珀の目、肩に苔)
def es_a(m, R):
    """A 苔と茸の老骨: 肩と頭に苔が垂れ下がり、小さな茸が群れて生え、腕の骨に蔦が巻く"""
    rng = np.random.default_rng(61)
    for bone in ("head", "left_arm", "right_arm"):
        lo, hi, c = R.box(bone)
        for k in range(7 if bone != "head" else 5):
            p = V3([rng.uniform(lo[0], hi[0]), hi[1] - 0.3, rng.uniform(lo[2], hi[2])])
            L = rng.uniform(2, 5)
            m.add(f"ms{bone}{k}", b.tub(bone, "moss", [p, p + V3([rng.uniform(-0.4, 0.4), -L * 0.6, 0]), p + V3([0, -L, rng.uniform(-0.3, 0.3)])],
                                        [0.35, 0.25, 0.08], None, sides=4))
        for k in range(4):
            p = V3([rng.uniform(lo[0] + 0.5, hi[0] - 0.5), hi[1] - 0.2, rng.uniform(lo[2] + 0.5, hi[2] - 0.5)])
            h = rng.uniform(0.8, 1.6)
            m.add(f"mst{bone}{k}", b.tub(bone, "shell", [p, p + V3([0, h, 0])], [0.18, 0.15], None, sides=5))
            m.add(f"mcp{bone}{k}", b.ell(bone, "mush", p + V3([0, h + 0.2, 0]), (0.7, 0.35, 0.7), segs=8, rings=3))
    for arm in ("left_forearm", "right_forearm"):
        lo, hi, c = R.box(arm)
        pts = [V3([c[0] + math.cos(t) * 1.6, hi[1] - (t / (3 * math.tau)) * (hi[1] - lo[1]), c[2] + math.sin(t) * 1.6]) for t in np.linspace(0, 3 * math.tau, 30)]
        m.add(f"vine{arm}", b.tub(arm, "moss", pts, [0.18] * len(pts), None, sides=4))
        for k in range(0, 30, 6):
            m.add(f"lf{arm}{k}", b.ell(arm, "moss", pts[k] + nrm(pts[k] - V3([c[0], pts[k][1], c[2]])) * 0.4, (0.5, 0.2, 0.35), segs=5, rings=2))


def es_b(m, R):
    """B 矢の刺さった盾の眼: 盾に鉄の縁と鋲、割れ目、琥珀の目に骨の睫毛、折れた矢が何本も刺さる"""
    lo, hi, c = R.box("shield")
    s = 1 if c[0] > 0 else -1

    def face(y, z):
        return surf(R, "shield", (s, 0, 0), at=V3([c[0], y, z]))
    for k in range(12):
        y = lo[1] + 2 + (k % 6) * (hi[1] - lo[1] - 4) / 5
        z = lo[2] + 1.2 if k < 6 else hi[2] - 1.2
        m.add(f"rv{k}", b.ell("shield", "ironc", face(y, z) + V3([s * 0.15, 0, 0]), (0.4, 0.45, 0.45), segs=6, rings=2))
    eye = face(c[1] + 2, c[2])
    for k in range(10):
        u = k / 10 * math.tau
        m.add(f"lash{k}", b.cone("shield", "boneo", eye + V3([0, math.sin(u) * 2.2, math.cos(u) * 2.2]), (s * 0.6, math.sin(u), math.cos(u)), 1.8, 0.2, sides=3, rings=1))
    rng = np.random.default_rng(63)
    for k in range(4):
        p = face(rng.uniform(lo[1] + 3, hi[1] - 3), rng.uniform(lo[2] + 2, hi[2] - 2))
        d = nrm(V3([s, rng.uniform(-0.3, 0.3), rng.uniform(-0.3, 0.3)]))
        m.add(f"ar{k}", b.tub("shield", "wood", [p - d * 0.6, p + d * 5], [0.14, 0.14], None, sides=4))
        for j in (1, -1):
            m.add(f"af{k}{j}", b.slab("shield", "shell", [p + d * 4, p + d * 5.2, p + d * 4.6 + V3([0, j * 0.6, 0])], V3([0, 0, 1]), 0.05))
    crack = [face(hi[1] - 2, c[2] - 2), face(c[1] + 5, c[2] - 1), face(c[1] + 4, c[2] + 1), face(c[1] - 2, c[2] + 2)]
    crack = [q + V3([s * 0.1, 0, 0]) for q in crack]
    m.add("crack", b.tub("shield", "char2", crack, [0.2, 0.18, 0.15, 0.08], None, sides=3))


def es_c(m, R):
    """C 古傷の骨: 胸を錆びた槍の穂が貫き、折れた肋に布の包帯、顎に欠けた歯が増える"""
    lo, hi, c = R.box("body")
    p0 = V3([c[0] + 1, c[1] + 2, lo[2] - 3])
    m.add("spear", b.tub("body", "wood", [p0, p0 + V3([-1.5, 1.2, 14])], [0.35, 0.35], None, sides=5))
    m.add("spearh", b.cone("body", "rust", p0, (0.1, -0.08, -1), 3.0, 0.9, sides=4, rings=2, rb=0.3))
    for k in range(3):
        y = hi[1] - 4 - k * 2.4
        V = np.concatenate([f.verts for f in R.faces if f.bone == "body"])
        sl = V[np.abs(V[:, 1] - y) < 1.0]
        ax = (sl[:, 0].max() - sl[:, 0].min()) / 2 * 0.95 if len(sl) else 5
        az = (sl[:, 2].max() - sl[:, 2].min()) / 2 * 0.95 if len(sl) else 4
        cz = (sl[:, 2].max() + sl[:, 2].min()) / 2 if len(sl) else c[2]
        pts = [V3([c[0] + math.cos(t) * ax, y + math.sin(t * 2) * 0.2, cz + math.sin(t) * az]) for t in np.linspace(0, math.tau, 17)]
        m.add(f"band{k}", b.tub("body", "shell", pts, [0.4] * 17, None, sides=5))
    hlo, hhi, hc = R.box("head")
    for k in range(8):
        x = hlo[0] + 1.5 + k * (hhi[0] - hlo[0] - 3) / 7
        L = 1.0 + (k % 3) * 0.5
        m.add(f"tooth{k}", b.cone("head", "boneo", V3([x, hlo[1] + 3.6, hlo[2] + 0.4]), (0, -1, -0.2), L, 0.32, sides=4, rings=1))


# ---------------------------------------------------------------- 潮の番人 (珊瑚憑きの変種。珊瑚の銛)
def sw_a(m, R):
    """A 珊瑚の枝角と貝: 頭から枝分かれした赤い珊瑚の角、肩に貝とフジツボの群れ"""
    hlo, hhi, hc = R.box("head")
    rng = np.random.default_rng(71)

    def branch(key, p, d, L, r, depth):
        q = p + nrm(d) * L
        m.add(key, b.tub("head", "coral", [p, (p + q) / 2 + V3([0, 0.3, 0]), q], [r, r * 0.8, r * 0.55], None, sides=5))
        if depth > 0:
            for j in (1, -1):
                d2 = nrm(nrm(d) + V3([j * 0.5, 0.3, rng.uniform(-0.3, 0.3)]))
                branch(f"{key}{j}", q, d2, L * 0.65, r * 0.6, depth - 1)
        else:
            m.add(key + "t", b.ell("head", "coral", q, (r * 0.9, r * 0.9, r * 0.9), segs=5, rings=2))
    for s in (1, -1):
        branch(f"cor{s}", V3([hc[0] + s * 3, hhi[1] - 3, hc[2]]), (s * 0.5, 1, 0.2), 4.5, 0.6, 2)
    for arm in ("arm_l", "arm_r"):
        lo, hi, c = R.box(arm)
        for k in range(6):
            p = V3([rng.uniform(lo[0], hi[0]), hi[1] - rng.uniform(0, 2), rng.uniform(lo[2], hi[2])])
            m.add(f"bn{arm}{k}", b.cone(arm, "shell", p, (0, 1, 0), 1.0, 0.6, sides=6, rings=1))
            m.add(f"bh{arm}{k}", b.ell(arm, "char2", p + V3([0, 0.95, 0]), (0.25, 0.08, 0.25), segs=6, rings=1))
        m.add(f"sh{arm}", b.slab(arm, "shell", [V3([c[0] - 1.5, hi[1] + 0.2, c[2] - 1]), V3([c[0], hi[1] + 0.2, c[2] - 2.6]), V3([c[0] + 1.5, hi[1] + 0.2, c[2] - 1]),
                                                V3([c[0], hi[1] + 0.2, c[2] + 1.2])], (0, 1, 0), 0.4))


def sw_b(m, R):
    """B 海藻の外套: 肩と背から長い海藻の帯が垂れ、胸に海星、腰に小さな巻貝の紐"""
    lo, hi, c = R.box("body")
    rng = np.random.default_rng(73)
    for k in range(12):
        u = math.radians(-160 + k * 320 / 11)
        p = V3([c[0] + math.sin(u) * 6, hi[1] - 1, c[2] + math.cos(u) * 6])
        L = rng.uniform(10, 18)
        pts = [p + V3([math.sin(j * 1.3) * 0.8, -L * j / 5, 0.3 * j]) for j in range(6)]
        m.add(f"kp{k}", b.tub("body", "kelp", pts, [0.5, 0.45, 0.4, 0.35, 0.25, 0.1], None, sides=4))
    star = V3([c[0] + 1.5, c[1] + 4, lo[2] - 0.3])
    for k in range(5):
        u = k / 5 * math.tau
        m.add(f"st{k}", b.cone("body", "coral", star, (math.cos(u), math.sin(u), -0.15), 2.4, 0.6, sides=4, rings=2))
    m.add("stc", b.ell("body", "coral", star, (0.8, 0.8, 0.4), segs=6, rings=2))
    for k in range(9):
        u = math.radians(-70 + k * 17.5)
        p = V3([c[0] + math.sin(u) * 8.6, lo[1] + 5, c[2] - math.cos(u) * 8.6])
        m.add(f"sn{k}", b.cone("body", "shell", p, (0, -1, -0.2), 1.4, 0.5, sides=5, rings=2, bend=(0.3, 0, 0)))


def sw_c(m, R):
    """C 返しの銛と綱: 銛の穂に返しが三段、柄に綱が巻いて肩から背へ掛かり、柄の尻に塩の結晶"""
    lo, hi, c = R.box("forearm_r")
    tip = V3([c[0], hi[1] + 6, c[2] - 1])
    base = V3([c[0], lo[1] - 4, c[2] + 1])
    m.add("shaft", b.tub("forearm_r", "wood", [base, tip], [0.4, 0.35], None, sides=6))
    m.add("head_", b.cone("forearm_r", "ironc", tip, (0, 1, -0.1), 4, 0.8, sides=4, rings=2, rb=0.25))
    for k in range(3):
        for s in (1, -1):
            m.add(f"barb{k}{s}", b.cone("forearm_r", "ironc", tip + V3([0, 0.6 + k * 1.1, 0]), (s * 0.8, -1, 0), 1.6, 0.25, sides=3, rings=1))
    pts = [base + V3([math.cos(t) * 0.7, (t / (4 * math.tau)) * 8 + 2, math.sin(t) * 0.7]) for t in np.linspace(0, 4 * math.tau, 40)]
    m.add("wrap", b.tub("forearm_r", "rope", pts, [0.18] * len(pts), None, sides=4))
    blo, bhi, bc = R.box("body")
    m.add("sling", b.tub("body", "rope", [V3([bhi[0] - 1, bhi[1] - 1, bc[2]]), V3([bc[0], bc[1], blo[2] - 0.4]), V3([blo[0] + 1, blo[1] + 3, bc[2]]),
                                         V3([bc[0], bc[1], bhi[2] + 0.4]), V3([bhi[0] - 1, bhi[1] - 1, bc[2]])], [0.35] * 5, None, sides=5))
    for k in range(5):
        u = k / 5 * math.tau
        m.add(f"salt{k}", fb.b.tub("forearm_r", "shell", [base, base + V3([math.cos(u) * 1.2, -1.4 - (k % 2), math.sin(u) * 1.2])], [0.5, 0.05], None, sides=6))


# ---------------------------------------------------------------- 鯨骨の番兵長 (鯨の骨の鎧・棘・背骨)
def wc_a(m, R):
    """A 鯨の顎の兜: 兜の前に鯨の下顎の骨が張り出し、ひげ板が櫛のように並び、歯が上下に"""
    lo, hi, c = R.box("head")
    zf = lo[2] - 0.2
    for s in (1, -1):
        pts = [V3([c[0] + s * 9, lo[1] + 4, c[2]]), V3([c[0] + s * 8, lo[1] + 1, zf - 2]), V3([c[0] + s * 3, lo[1] - 0.5, zf - 5])]
        m.add(f"jaw{s}", b.tub("head", "boneo", pts, [1.4, 1.2, 0.9], None, sides=6))
    m.add("chin", b.ell("head", "boneo", V3([c[0], lo[1] - 0.6, zf - 5.2]), (3.5, 1.2, 1.4), segs=8, rings=3))
    for k in range(14):
        x = c[0] - 6.5 + k
        m.add(f"bal{k}", b.slab("head", "char2", [V3([x - 0.4, lo[1] + 7, zf - 1]), V3([x + 0.4, lo[1] + 7, zf - 1]), V3([x + 0.2, lo[1] + 1.5, zf - 3.5]),
                                                  V3([x - 0.2, lo[1] + 1.5, zf - 3.5])], (0, 0, 1), 0.15))
    for k in range(10):
        x = c[0] - 5 + k * 10 / 9
        m.add(f"lt{k}", b.cone("head", "shell", V3([x, lo[1] + 0.3, zf - 3.5 - abs(x - c[0]) * -0.2]), (0, 1, -0.1), 1.4, 0.35, sides=4, rings=1))


def wc_b(m, R):
    """B 番兵長の装い: 肩に房の付いた肩章、背に裂けた外套、腰に綱の帯と望遠鏡と羅針盤"""
    for arm in ("armL", "armR"):
        lo, hi, c = R.box(arm)
        p = surf(R, arm, (0, 1, 0), at=c) - V3([0, 0.3, 0])
        p = V3([c[0], min(p[1], c[1] + (hi[1] - c[1]) * 0.5), c[2]])
        m.add(f"ep{arm}", b.ell(arm, "cloth_r", p, (4.5, 0.8, 4.0), segs=10, rings=2))
        for k in range(12):
            u = k / 12 * math.tau
            q = p + V3([math.cos(u) * 4.3, -0.4, math.sin(u) * 3.8])
            m.add(f"fr{arm}{k}", b.tub(arm, "amber", [q, q + V3([0, -2.4, 0])], [0.12, 0.08], None, sides=3))
    lo, hi, c = R.box("body")
    m.add("cape", b.lof("body", "cloth_r", [[V3([x, y, hi[2] + 1.0 + (0 if y > hi[1] - 2 else (hi[1] - y) * 0.08)]) for x in np.linspace(lo[0] + 2, hi[0] - 2, 8)]
                                            for y in (hi[1] - 1, hi[1] - 1.01)]) if False else
          b.slab("body", "cloth_r", [V3([lo[0] + 2, hi[1] - 1, hi[2] + 1]), V3([hi[0] - 2, hi[1] - 1, hi[2] + 1]), V3([hi[0] + 1, lo[1] - 6, hi[2] + 3]),
                                     V3([c[0] + 3, lo[1] - 3, hi[2] + 3]), V3([c[0], lo[1] - 7, hi[2] + 3]), V3([c[0] - 3, lo[1] - 3, hi[2] + 3]),
                                     V3([lo[0] - 1, lo[1] - 6, hi[2] + 3])], (0, 0, 1), 0.3))
    belt = [V3([c[0] + math.cos(t) * 11.5, lo[1] + 3, c[2] + math.sin(t) * 9]) for t in np.linspace(0, math.tau, 25)]
    m.add("belt", b.tub("body", "rope", belt, [0.6] * len(belt), None, sides=5))
    m.add("glass", b.tub("body", "amber", [V3([lo[0] + 1, lo[1] + 2, lo[2] + 1]), V3([lo[0] - 0.5, lo[1] - 4, lo[2] + 1])], [0.6, 0.45], None, sides=8))
    m.add("glass2", ring("body", "ironc", V3([lo[0] + 0.5, lo[1], lo[2] + 1]), 0.65, (0.2, 1, 0), 0.15, n=10))
    m.add("compass", b.tub("body", "amber", [V3([hi[0] - 3, lo[1] + 1.5, lo[2] - 0.3]), V3([hi[0] - 3, lo[1] + 1.5, lo[2] - 1.0])], [1.3, 1.3], None, sides=12))
    m.add("needle", b.cone("body", "cloth_r", V3([hi[0] - 3, lo[1] + 1.5, lo[2] - 1.1]), (0.6, 1, 0), 1.0, 0.18, sides=3, rings=1))


def wc_c(m, R):
    """C 背に刺さった銛: 背に折れた銛が五本刺さり、綱が垂れ、刺さった所にフジツボ"""
    lo, hi, c = R.box("body")
    rng = np.random.default_rng(83)
    for k in range(5):
        p = V3([c[0] + rng.uniform(-7, 7), rng.uniform(c[1], hi[1] - 1), hi[2] - 0.5])
        d = nrm(V3([rng.uniform(-0.4, 0.4), rng.uniform(0.1, 0.6), 1]))
        L = rng.uniform(8, 14)
        m.add(f"hp{k}", b.tub("body", "wood", [p - d * 1.5, p + d * L], [0.35, 0.3], None, sides=5))
        m.add(f"hpb{k}", b.ell("body", "ironc", p - d * 0.2, (0.8, 0.8, 0.8), segs=6, rings=2))
        if k % 2 == 0:
            q = p + d * L
            m.add(f"rp{k}", b.tub("body", "rope", [q, q + V3([0.5, -4, 1]), q + V3([1, -9, 1.5])], [0.18, 0.15, 0.1], None, sides=4))
        for j in range(4):
            u = j / 4 * math.tau
            m.add(f"bc{k}{j}", b.cone("body", "shell", p + V3([math.cos(u) * 1.1, math.sin(u) * 1.1, 0.4]), (0, 0, 1), 0.8, 0.4, sides=5, rings=1))


# ---------------------------------------------------------------- 古い灰被り (灰の殻・火掻き)
def et_a(m, R):
    """A 背の煤の釜: 背に鉄の釜を負い、中で燠が燃え、鎖で体に留め、釜の口から煙突"""
    lo, hi, c = R.box("crust_body")
    p = V3([c[0], c[1] + 3, hi[2] + 3.5])
    m.add("pot", b.ell("crust_body", "ironc", p, (4.5, 4.0, 3.5), segs=12, rings=5))
    m.add("rim", ring("crust_body", "ironc", p + V3([0, 3.6, 0]), 3.2, (0, 1, 0), 0.5, n=16))
    m.add("emb", b.ell("crust_body", "glow", p + V3([0, 3.5, 0]), (2.6, 0.4, 2.6), segs=10, rings=2))
    m.add("pipe", b.tub("crust_body", "ironc", [p + V3([1.5, 3.4, 1]), p + V3([2.2, 8, 1.5]), p + V3([2.0, 9, 1.4])], [0.8, 0.7, 0.75], None, sides=8))
    for s in (1, -1):
        chain(m, f"ch{s}", "crust_body", "ironc", p + V3([s * 4.3, 1.5, -1]), V3([c[0] + s * 4.5, hi[1] - 2, lo[2] - 0.3]), link=1.2, th=0.2)
        chain(m, f"cl{s}", "crust_body", "ironc", p + V3([s * 4.0, -2.5, -1]), V3([c[0] + s * 4.0, lo[1] + 8, lo[2] - 0.3]), link=1.2, th=0.2)
    for k in range(6):
        u = k / 6 * math.tau
        m.add(f"rv{k}", b.ell("crust_body", "rust", p + V3([math.cos(u) * 4.4, 0.5, math.sin(u) * 3.4]), (0.35, 0.35, 0.35), segs=5, rings=2))


def et_b(m, R):
    """B 熔けた鉄の冠と錠: 頭に熔けて垂れた鉄の棘の冠、胸に鎖が十字に巻いて大きな錠前"""
    hlo, hhi, hc = R.box("crust_head")
    for k in range(9):
        u = k / 9 * math.tau
        p = V3([hc[0] + math.cos(u) * 4.2, hhi[1] - 3, hc[2] + math.sin(u) * 4.0])
        m.add(f"sp{k}", b.cone("crust_head", "rust", p, (math.cos(u) * 0.2, 1, math.sin(u) * 0.2), 3 + (k % 3) * 1.4, 0.7, sides=4, rings=2))
        m.add(f"dr{k}", b.tub("crust_head", "glow", [p + V3([0, 0.2, 0]), p + V3([0, -1.6 - (k % 2), 0])], [0.25, 0.12], None, sides=4))
    m.add("band", ring("crust_head", "rust", V3([hc[0], hhi[1] - 3, hc[2]]), 4.6, (0, 1, 0), 0.6, n=18))
    lo, hi, c = R.box("crust_body")
    zf = lo[2] - 0.4
    for s in (1, -1):
        chain(m, f"x{s}", "crust_body", "ironc", V3([c[0] + s * 5.5, hi[1] - 2, zf]), V3([c[0] - s * 5.5, lo[1] + 6, zf]), link=1.3, th=0.22)
    m.add("lock", b.box("crust_body", "ironc", V3([c[0], c[1] + 1, zf - 0.8]), (3.0, 3.2, 1.2)))
    m.add("shackle", ring("crust_body", "ironc", V3([c[0], c[1] + 3.2, zf - 0.8]), 1.1, (0, 0, 1), 0.3, n=10))
    m.add("key", b.box("crust_body", "char2", V3([c[0], c[1] + 0.6, zf - 1.5]), (0.5, 1.2, 0.2)))


def et_c(m, R):
    """C 燠の爪と割れ目: 両手の指が燃えさしの長い爪になり、灰の殻に光る割れ目が走る"""
    for arm in ("crust_fore_l", "crust_fore_r"):
        lo, hi, c = R.box(arm)
        for j in range(4):
            p = V3([c[0] + (j - 1.5) * 0.8, lo[1] + 0.2, c[2] - 0.6])
            m.add(f"cw{arm}{j}", b.cone(arm, "char2", p, (0, -1, -0.5), 3.4, 0.35, sides=4, rings=2, bend=(0, 0.3, -0.8)))
            m.add(f"cg{arm}{j}", b.cone(arm, "glow", p + V3([0, -2.6, -1.4]), (0, -1, -0.6), 1.2, 0.2, sides=4, rings=1))
    rng = np.random.default_rng(91)
    for bone in ("crust_body", "crust_head", "crust_arm_l", "crust_arm_r"):
        lo, hi, c = R.box(bone)
        for k in range(4 if bone == "crust_body" else 2):
            p = surf(R, bone, (rng.uniform(-1, 1), rng.uniform(-0.3, 0.6), -1))
            pts = [p]
            for j in range(3):
                pts.append(pts[-1] + V3([rng.uniform(-1, 1), -1.4, -0.05]))
            m.add(f"cr{bone}{k}", b.tub(bone, "glow", pts, [0.22, 0.18, 0.14, 0.06], None, sides=3))


# ---------------------------------------------------------------- 灰の鍛え手 (背の曲がった大きな鍛冶・二つの鞴・大鎚)
def as_a(m, R):
    """A 前掛けの道具: 前掛けに鋲と物入れ、火箸・鏨・やすりが革紐で下がり、腰に鎖"""
    lo, hi, c = R.box("apron")
    zf = lo[2] - 0.3
    for k in range(10):
        x = lo[0] + 1 + k * (hi[0] - lo[0] - 2) / 9
        m.add(f"rv{k}", b.ell("apron", "amber", V3([x, hi[1] - 1.2, zf]), (0.4, 0.4, 0.35), segs=5, rings=2))
    for k, (x, kind) in enumerate(((lo[0] + 4, "tongs"), (c[0] - 3, "chisel"), (c[0] + 3, "file"), (hi[0] - 4, "tongs"))):
        top = V3([x, hi[1] - 2, zf - 0.4])
        m.add(f"st{k}", b.tub("apron", "rope", [top, top + V3([0, -3, 0])], [0.18, 0.18], None, sides=4))
        q = top + V3([0, -3, 0])
        if kind == "tongs":
            for s in (1, -1):
                m.add(f"tg{k}{s}", b.tub("apron", "ironc", [q, q + V3([s * 0.6, -4, 0]), q + V3([s * 0.2, -8, -0.3])], [0.25, 0.22, 0.2], None, sides=4))
        elif kind == "chisel":
            m.add(f"cs{k}", b.tub("apron", "ironc", [q, q + V3([0, -6, 0])], [0.5, 0.4], None, sides=6))
            m.add(f"cst{k}", b.cone("apron", "ironc", q + V3([0, -6, 0]), (0, -1, 0), 1.2, 0.45, sides=4, rings=1, rb=0.15))
        else:
            m.add(f"fl{k}", b.box("apron", "ironc", q + V3([0, -3.5, 0]), (1.0, 6.5, 0.35)))
            for j in range(6):
                m.add(f"flr{k}{j}", b.box("apron", "char2", q + V3([0, -1.2 - j, -0.2]), (1.0, 0.12, 0.1)))
    m.add("pouch", b.box("apron", "rope", V3([c[0], lo[1] + 5, zf - 0.7]), (6, 4, 1.4)))
    m.add("pouchf", b.box("apron", "rope", V3([c[0], lo[1] + 6.5, zf - 1.5]), (6.2, 1.4, 0.3)))


def as_b(m, R):
    """B 鞴の作り込み: 二つの鞴に革の蛇腹と真鍮の口金、鞴から炉へ延びる管、取っ手"""
    for bel in ("bellows_l", "bellows_r"):
        lo, hi, c = R.box(bel)
        s = 1 if c[0] > 0 else -1
        V = np.concatenate([f.verts for f in R.faces if f.bone == bel])
        for k in range(6):
            y = lo[1] + 4 + k * 2.2
            sl = V[np.abs(V[:, 1] - y) < 1.2]
            if not len(sl):
                continue
            cx, cz = (sl[:, 0].max() + sl[:, 0].min()) / 2, (sl[:, 2].max() + sl[:, 2].min()) / 2
            rr = max(sl[:, 0].max() - sl[:, 0].min(), sl[:, 2].max() - sl[:, 2].min()) / 2 + 0.3
            m.add(f"pl{bel}{k}", ring(bel, "rope" if k % 2 else "char2", V3([cx, y, cz]), rr, (0, 1, 0), 0.5, n=16))
        nz = V3([c[0], lo[1] + 1, c[2] - 2])
        m.add(f"nz{bel}", b.cone(bel, "amber", nz + V3([0, 2, 0]), (0, -1, -0.3), 5, 1.4, sides=8, rings=2))
        m.add(f"pp{bel}", b.tub(bel, "ironc", [nz + V3([0, -2.5, -1.2]), nz + V3([-s * 4, -8, -2]), nz + V3([-s * 9, -12, 2])], [0.8, 0.8, 0.8], None, sides=8))
        m.add(f"hd{bel}", b.tub(bel, "wood", [V3([c[0] - 2, hi[1] + 1, c[2]]), V3([c[0], hi[1] + 3, c[2]]), V3([c[0] + 2, hi[1] + 1, c[2]])], [0.5] * 3, None, sides=6))


def as_c(m, R):
    """C 鉄の顎の面: 顔の下半分に鍛えた鉄の顎の面、牙が並び、鋲と、口の隙間に燠"""
    lo, hi, c = R.box("head")
    zf = lo[2] - 0.5
    pts = [V3([c[0] - 13, c[1] - 4, zf + 6]), V3([c[0] - 9, c[1] - 9, zf + 1]), V3([c[0], c[1] - 11, zf - 1]), V3([c[0] + 9, c[1] - 9, zf + 1]), V3([c[0] + 13, c[1] - 4, zf + 6])]
    m.add("jawp", b.tub("head", "ironc", pts, [2.2, 2.6, 2.8, 2.6, 2.2], None, sides=8))
    for k in range(11):
        t = k / 10
        x = c[0] - 9 + t * 18
        p = V3([x, c[1] - 7.5, zf - 1.0 + abs(x - c[0]) * 0.18])
        m.add(f"fang{k}", b.cone("head", "shell", p, (0, 1, -0.2), 2.0 + (1.5 if k in (2, 8) else 0), 0.5, sides=4, rings=1))
    m.add("gap", b.ell("head", "glow", V3([c[0], c[1] - 6.5, zf + 0.4]), (8, 1.0, 1.4), segs=12, rings=3))
    for k in range(8):
        t = k / 7
        p = pts[0] + (pts[-1] - pts[0]) * t
        p = V3([p[0], c[1] - 9.5 + abs(p[0] - c[0]) * 0.35, zf - 1.4 + abs(p[0] - c[0]) * 0.45])
        m.add(f"rv{k}", b.ell("head", "amber", p, (0.45, 0.45, 0.45), segs=5, rings=2))


# ---------------------------------------------------------------- 焼けた羽守 (羽守の変種・焼けた外套・鳥の兜)
def sc_a(m, R):
    """A 燃え残る羽根: 外套の縁に焦げた羽根が重なり、先が燠のように光り、灰がこぼれる"""
    rng = np.random.default_rng(101)
    for cl in ("cloak_l", "cloak_r"):
        for k, e in enumerate(edge_points(R, cl, 7)):
            d = nrm(V3([rng.uniform(-0.2, 0.2), -1, 0.2]))
            feather(m, f"fe{cl}{k}", cl, "char2", e + V3([0, 1.0, 0]), d, rng.uniform(3, 5), 1.2, (0, 0, 1), shaft="feather_b")
            m.add(f"ft{cl}{k}", b.ell(cl, "glow", e + V3([0, 1.0, 0]) + d * 4.2, (0.35, 0.5, 0.25), segs=5, rings=2))
    for arm in ("arm_l", "arm_r"):
        lo, hi, c = R.box(arm)
        for k in range(4):
            feather(m, f"sf{arm}{k}", arm, "feather_b", V3([c[0] + (k - 1.5) * 1.2, hi[1], c[2]]), (0, -0.4, 1), 3.5, 1.0, (0, 1, 0), shaft="char2")


def sc_b(m, R):
    """B 鳥の頭骨の兜: 兜が鳥の頭骨になり、嘴に細かい歯、眼窩に青白い火、骨のとさか"""
    lo, hi, c = R.box("head")
    tip = V3([c[0], c[1] - 1, lo[2] - 1.5])
    m.add("beak_u", b.cone("head", "boneo", V3([c[0], c[1] + 0.5, lo[2] + 3]), (0, -0.25, -1), 6.5, 1.6, sides=5, rings=3, bend=(0, -1.2, 0)))
    m.add("beak_l", b.cone("head", "boneo", V3([c[0], c[1] - 2.2, lo[2] + 3]), (0, -0.1, -1), 4.5, 1.0, sides=5, rings=2))
    for k in range(6):
        z = lo[2] + 2 - k * 0.7
        for s in (1, -1):
            m.add(f"bt{k}{s}", b.cone("head", "shell", V3([c[0] + s * 0.7, c[1] - 1.1, z]), (0, -1, 0), 0.6, 0.15, sides=3, rings=1))
    for s in (1, -1):
        m.add(f"sock{s}", b.ell("head", "char2", V3([c[0] + s * 2.4, c[1] + 2, lo[2] + 3.6]), (1.2, 1.1, 0.8), segs=8, rings=3))
        m.add(f"fire{s}", b.ell("head", "glow", V3([c[0] + s * 2.4, c[1] + 2, lo[2] + 3.0]), (0.5, 0.6, 0.3), segs=6, rings=2))
    for k in range(6):
        z = c[2] - 2 + k * 1.6
        m.add(f"crest{k}", b.cone("head", "boneo", V3([c[0], hi[1] - 1, z]), (0, 1, 0.5), 3 - abs(k - 2) * 0.4, 0.4, sides=4, rings=1))


def sc_c(m, R):
    """C 雷の焦げ筋の鎧: 肩と胸に焦げた鉄の板、板に雷の光る筋、腕に焼けた帯が巻く"""
    for arm in ("arm_l", "arm_r"):
        lo, hi, c = R.box(arm)
        s = 1 if c[0] > 0 else -1
        for k in range(3):
            m.add(f"pd{arm}{k}", b.box(arm, "char2", V3([c[0] + s * 0.5, hi[1] - 0.5 - k * 1.3, c[2]]), (5.5 - k * 0.6, 1.0, 5.0 - k * 0.5), R=b.R_(0, 0, -s * (16 + k * 6))))
        bolt = [V3([c[0] + s * 1.5, hi[1] + 0.2, c[2] - 1.5]), V3([c[0] + s * 0.5, hi[1] - 1, c[2] - 2.6]), V3([c[0] + s * 1.2, hi[1] - 2.2, c[2] - 2.4])]
        m.add(f"bl{arm}", b.tub(arm, "glow", bolt, [0.18, 0.15, 0.08], None, sides=3))
        flo, fhi, fc = R.box("forearm_l" if s < 0 else "forearm_r")
        pts = [V3([fc[0] + math.cos(t) * 1.8, fhi[1] - 1 - t * 0.5, fc[2] + math.sin(t) * 1.8]) for t in np.linspace(0, 2.5 * math.tau, 26)]
        m.add(f"wrap{arm}", b.tub("forearm_l" if s < 0 else "forearm_r", "rust", pts, [0.25] * 26, None, sides=4))
    lo, hi, c = R.box("body")
    fz = surf(R, "body", (0, 0, -1), at=V3([c[0], c[1] + 1, c[2]]))[2]
    m.add("plate", b.box("body", "char2", V3([c[0], c[1] + 1, fz - 0.3]), (7, 6, 0.8)))
    bolt = [V3([c[0] - 2, c[1] + 3.5, fz - 0.8]), V3([c[0] + 0.5, c[1] + 1.5, fz - 0.8]), V3([c[0] - 0.8, c[1] + 0.5, fz - 0.8]), V3([c[0] + 2, c[1] - 1.5, fz - 0.8])]
    m.add("pbolt", b.tub("body", "glow", bolt, [0.25, 0.22, 0.2, 0.1], None, sides=3))


# ---------------------------------------------------------------- 潮招き (片方の大きなはさみの山のような蟹)
def tb_a(m, R):
    """A フジツボと海藻の甲羅: 甲羅にフジツボの群れ、海藻が垂れ、小さな蟹が何匹も乗る"""
    lo, hi, c = R.box("body")
    rng = np.random.default_rng(111)
    for k in range(30):
        x, z = rng.uniform(lo[0] + 8, hi[0] - 8), rng.uniform(lo[2] + 8, hi[2] - 8)
        p = surf(R, "body", (0, 1, 0), at=V3([x, c[1], z]))
        r = rng.uniform(2.4, 4.4)
        m.add(f"bn{k}", b.cone("body", "shell", p - V3([0, 0.3, 0]), (rng.uniform(-0.2, 0.2), 1, rng.uniform(-0.2, 0.2)), r * 1.3, r, sides=6, rings=1))
        m.add(f"bh{k}", b.ell("body", "char2", p + V3([0, r * 1.2, 0]), (r * 0.4, 0.15, r * 0.4), segs=6, rings=1))
    for k, e in enumerate(edge_points(R, "body", 14)):
        L = rng.uniform(5, 10)
        m.add(f"kp{k}", b.tub("body", "kelp", [e + V3([0, 1.5, 0]), e + V3([0.5, -L * 0.5, -0.5]), e + V3([0, -L, 0])], [0.6, 0.45, 0.15], None, sides=4))
    for k in range(4):
        p = surf(R, "body", (0, 1, 0), at=V3([rng.uniform(lo[0] + 12, hi[0] - 12), c[1], rng.uniform(lo[2] + 12, hi[2] - 12)]))
        m.add(f"cb{k}", b.ell("body", "coral", p + V3([0, 0.8, 0]), (2.0, 0.9, 1.6), segs=8, rings=3))
        for s in (1, -1):
            for j in range(3):
                m.add(f"cl{k}{s}{j}", b.tub("body", "coral", [p + V3([s * 1.6, 0.8, (j - 1) * 0.9]), p + V3([s * 2.8, 0.2, (j - 1) * 1.2])], [0.2, 0.15], None, sides=3))


def tb_b(m, R):
    """B 大鋏の歯と甲羅の棘: 大きなはさみの内側に鋸の歯、甲羅の縁に棘が並ぶ"""
    lo, hi, c = R.box("claw_big_pincer")
    for k in range(10):
        t = k / 9
        p = V3([lo[0] - 0.2, c[1] - 4 + t * 6, hi[2] - 4 - t * (hi[2] - lo[2] - 8)])
        m.add(f"tp{k}", b.cone("claw_big_pincer", "shell", p, (-1, -0.2, 0), 5.5 + (k % 2) * 2.5, 1.9, sides=4, rings=1))
    blo, bhi, bc = R.box("claw_big")
    for k in range(8):
        t = k / 7
        p = V3([bhi[0] + 0.2, bc[1] + 2, bhi[2] - 8 - t * (bhi[2] - blo[2] - 30)])
        m.add(f"tc{k}", b.cone("claw_big", "shell", p, (1, 0.3, 0), 5.0, 1.7, sides=4, rings=1))
    for k, e in enumerate(edge_points(R, "body", 20, low=False)):
        m.add(f"sp{k}", b.cone("body", "char2", e - V3([0, 0.5, 0]), (0, 1, -0.3), 6 + (k % 3) * 2, 2.0, sides=4, rings=2))
    lo, hi, c = R.box("body")
    for k in range(14):
        u = k / 14 * math.tau
        p = V3([c[0] + math.cos(u) * (hi[0] - lo[0]) * 0.47, c[1] + 2, c[2] + math.sin(u) * (hi[2] - lo[2]) * 0.47])
        m.add(f"rim{k}", b.cone("body", "shell", p, (math.cos(u), 0.4, math.sin(u)), 6.0, 2.0, sides=4, rings=1))


def tb_c(m, R):
    """C 背の難破船: 甲羅の上に折れた帆柱と裂けた帆、綱と樽、船首の像が刺さる"""
    lo, hi, c = R.box("body")
    top = surf(R, "body", (0, 1, 0))
    mast = [top - V3([0, 2, 0]), top + V3([1, 24, 2])]
    m.add("mast", b.tub("body", "wood", mast, [1.2, 0.9], None, sides=8))
    m.add("yard", b.tub("body", "wood", [top + V3([-9, 18, 2]), top + V3([10, 19, 2.6])], [0.6, 0.6], None, sides=6))
    m.add("sail", b.slab("body", "shell", [top + V3([-8, 17.5, 2.4]), top + V3([9, 18.5, 3.0]), top + V3([7, 8, 6]), top + V3([2, 10, 5]), top + V3([-3, 6, 5.5]),
                                           top + V3([-7, 9, 4.5])], (0, 0, 1), 0.3))
    for s in (1, -1):
        m.add(f"stay{s}", b.tub("body", "rope", [top + V3([0.8, 22, 2]), top + V3([s * 18, 2, -6])], [0.18, 0.18], None, sides=4))
    for k, (dx, dz) in enumerate(((-12, 8), (13, 10))):
        p = surf(R, "body", (0, 1, 0), at=V3([c[0] + dx, c[1], c[2] + dz]))
        m.add(f"barrel{k}", b.tub("body", "wood", [p, p + V3([0, 3.5, 0])], [1.6, 1.6], None, sides=10))
        for j in (0.6, 2.9):
            m.add(f"hoop{k}{j}", ring("body", "ironc", p + V3([0, j, 0]), 1.65, (0, 1, 0), 0.15, n=12))
    p = surf(R, "body", (0, 0.4, -1))
    m.add("fig", b.ell("body", "wood", p + V3([0, 2, -2]), (1.6, 3.0, 1.4), segs=8, rings=4))
    m.add("figh", b.ell("body", "wood", p + V3([0, 5.5, -2.6]), (1.2, 1.3, 1.2), segs=8, rings=4))


# ---------------------------------------------------------------- 雷獣 (狼に近い獣・たてがみ・尾)
def rj_a(m, R):
    """A 雷のたてがみ: たてがみの毛束が長く逆立ち、毛の先が光り、背に沿って雷の弧が走る"""
    lo, hi, c = R.box("mane")
    rng = np.random.default_rng(121)
    for k in range(22):
        z = lo[2] + 2 + k * (hi[2] - lo[2] - 4) / 21
        p = surf(R, "mane", (0, 1, 0), at=V3([c[0], c[1], z]))
        for s in (1, -1):
            d = nrm(V3([s * 0.4, 1, 0.5]))
            L = rng.uniform(9, 15) * (1 - abs(z - c[2]) / (hi[2] - lo[2]) * 0.7)
            m.add(f"tf{k}{s}", b.cone("mane", "stormfur", p + V3([s * 1.0, -1.0, 0]), d, L, 1.3, sides=3, rings=2, bend=(0, -0.8, 1.6)))
            m.add(f"tg{k}{s}", b.cone("mane", "glow", p + V3([s * 1.0, -1.0, 0]) + d * L * 0.7, d, L * 0.35, 0.45, sides=3, rings=1))
    arc = [surf(R, "mane", (0, 1, 0), at=V3([c[0], c[1], z])) + V3([((i % 2) * 2 - 1) * 1.5, 3, 0]) for i, z in enumerate(np.linspace(lo[2] + 4, hi[2] - 4, 9))]
    m.add("arc", b.tub("mane", "glow", arc, [0.45] * 9, None, sides=3))


def rj_b(m, R):
    """B 牙と骨の胸当て: 長い上下の牙、爪が太く長く、胸に肋の骨の板が浮く"""
    lo, hi, c = R.box("head")
    jlo, jhi, jc = R.box("jaw")
    for s in (1, -1):
        m.add(f"fu{s}", b.cone("head", "shell", V3([c[0] + s * 3.0, jhi[1] + 1.0, jlo[2] + 3]), (0, -1, -0.2), 10.0, 1.7, sides=5, rings=3, bend=(0, 0, -1.2)))
        m.add(f"fl{s}", b.cone("jaw", "shell", V3([jc[0] + s * 2.2, jlo[1] + 1, jlo[2] + 2]), (0, 1, -0.1), 5.0, 1.1, sides=5, rings=2))
        for k in range(4):
            m.add(f"ti{s}{k}", b.cone("head", "shell", V3([c[0] + s * 2.4, jhi[1] + 0.5, jlo[2] + 6 + k * 2.5]), (0, -1, 0), 2.6, 0.7, sides=4, rings=1))
    for leg in ("leg_fl_lo", "leg_fr_lo", "leg_bl_lo", "leg_br_lo"):
        V = np.concatenate([f.verts for f in R.faces if f.bone == leg])
        foot = V[np.argmin(V[:, 1])]
        for j in range(4):
            p = V3([foot[0] + (j - 1.5) * 1.1, foot[1] + 1.2, V[:, 2].min() + 0.5])
            m.add(f"cl{leg}{j}", b.cone(leg, "char2", p, (0, -0.5, -1), 5.0, 0.9, sides=4, rings=2, bend=(0, -2.0, 0)))
    clo, chi, cc = R.box("chest")
    for k in range(5):
        y = chi[1] - 8 - k * 4
        pts = [V3([clo[0] + 2, y, cc[2] - 2]), V3([cc[0], y - 2, clo[2] - 0.5]), V3([chi[0] - 2, y, cc[2] - 2])]
        m.add(f"rb{k}", b.tub("chest", "shell", pts, [1.5, 1.7, 1.5], None, sides=5))
    m.add("st", b.tub("chest", "shell", [V3([cc[0], chi[1] - 6, clo[2] - 0.8]), V3([cc[0], chi[1] - 26, clo[2] + 0.5])], [0.9, 0.7], None, sides=6))


def rj_c(m, R):
    """C 切れた枷と雷雲の尾: 四つの足首に千切れた鉄の枷と鎖、尾の先が雷雲の塊になり避雷の棘"""
    for leg in ("leg_fl_lo", "leg_fr_lo", "leg_bl_lo", "leg_br_lo"):
        lo, hi, c = R.box(leg)
        p = V3([c[0], lo[1] + 4, c[2]])
        m.add(f"cuff{leg}", ring(leg, "ironc", p, 3.4, (0, 1, 0), 1.1, n=14))
        chain(m, f"ch{leg}", leg, "ironc", p + V3([0, -0.5, 3.2]), p + V3([0.5, -4, 9]), link=1.8, th=0.35)
    lo, hi, c = R.box("tail_3")
    tip = V3([c[0], c[1], hi[2] - 2])
    rng = np.random.default_rng(131)
    for k in range(9):
        o = V3([rng.uniform(-3, 3), rng.uniform(-2, 3), rng.uniform(-2, 4)])
        m.add(f"cl{k}", b.ell("tail_3", "stormfur", tip + o * 1.8, (rng.uniform(4, 6),) * 3, segs=8, rings=4))
    for k in range(5):
        u = k / 5 * math.tau
        d = nrm(V3([math.cos(u), 0.8, math.sin(u) + 0.5]))
        m.add(f"rod{k}", b.cone("tail_3", "ironc", tip + d * 6, d, 8, 0.8, sides=4, rings=1))
        m.add(f"rodg{k}", b.ell("tail_3", "glow", tip + d * 13.5, (0.8, 0.8, 0.8), segs=6, rings=2))


BOSSES = {
    "elder_sentry": ("老いた歩哨", "g_amber", [("A 苔と茸の老骨", es_a), ("B 矢の刺さった盾の眼", es_b), ("C 古傷の骨", es_c)]),
    "shore_warden": ("潮の番人", "g_cyan", [("A 珊瑚の枝角と貝", sw_a), ("B 海藻の外套", sw_b), ("C 返しの銛と綱", sw_c)]),
    "whalebone_captain": ("鯨骨の番兵長", "g_cyan", [("A 鯨の顎の兜", wc_a), ("B 番兵長の装い", wc_b), ("C 背に刺さった銛", wc_c)]),
    "elder_thrall": ("古い灰被り", "g_amber", [("A 背の煤の釜", et_a), ("B 熔けた鉄の冠と錠", et_b), ("C 燠の爪と割れ目", et_c)]),
    "ash_smith": ("灰の鍛え手", "g_orange", [("A 前掛けの道具", as_a), ("B 鞴の作り込み", as_b), ("C 鉄の顎の面", as_c)]),
    "scorched_warden": ("焼けた羽守", "g_blue", [("A 燃え残る羽根", sc_a), ("B 鳥の頭骨の兜", sc_b), ("C 雷の焦げ筋の鎧", sc_c)]),
    "tide_beckoner": ("潮招き", "g_teal", [("A フジツボと海藻", tb_a), ("B 大鋏の歯と棘", tb_b), ("C 背の難破船", tb_c)]),
    "raiju": ("雷獣", "g_cyan", [("A 雷のたてがみ", rj_a), ("B 牙と骨の胸当て", rj_b), ("C 切れた枷と雷雲の尾", rj_c)]),
}


def main():
    fb.OUT.mkdir(parents=True, exist_ok=True)
    names = [a for a in sys.argv[1:] if not a.startswith("--")] or list(BOSSES)
    for name in names:
        faces, img, glow = bs.load(name)
        title, gl, opts = BOSSES[name]
        R = bs.Ref(faces)
        cols = [("今の姿", faces, img, glow, "", 0)]
        for label, fn in opts:
            af, aa, ag = fb.parts(fn, R, gl)
            mf, ma, mg = bs.merge(faces, img, glow, af, aa, ag)
            cols.append((label, mf, ma, mg, fn.__doc__.split(":")[1].strip()[:30], sum(len(f.tris()) for f in af)))
        fb.sheet(name, title, cols)
        print(name, "ok", flush=True)


if __name__ == "__main__":
    sys.stdout.reconfigure(encoding="utf-8")
    main()
