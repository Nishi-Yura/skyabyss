# -*- coding: utf-8 -*-
"""残りのボスの作り込みのラフ (2026-11-11 の声「仕上げる」): ダンジョンの中ボス 4 体と、塗り分けだけだった層の主 3 体。

坑の頭・鐘の隠者・晶洞の巨像・牛の鍛冶・肋の主・白殻の主・鼓動まねの主。そのボスのためだけに考えた細かい部品を足す三案。
(ほかのボスの足し方 (苔・海藻・フジツボ・鎖の札・小鐘など) は使わない)
道具は終章・章の道中のラフのもの (tools/rough/lowpoly/finale_bosses・chapter_bosses) を使う。

    python -X utf8 -I tools/rough/lowpoly/mid_lord_bosses/build.py [名前 ...]
出力: docs/review/mid_lord_bosses/<名前>.png
"""
from __future__ import annotations

import importlib.util
import math
import sys
from pathlib import Path

import numpy as np

ROOT = Path(__file__).resolve().parents[4]
spec = importlib.util.spec_from_file_location("cb", ROOT / "tools/rough/lowpoly/chapter_bosses/build.py")
cb = importlib.util.module_from_spec(spec)
spec.loader.exec_module(cb)
fb = cb.fb
bs, b, ring, chain, edge_points, nrm, basis, surf = fb.bs, fb.b, fb.ring, fb.chain, fb.edge_points, fb.nrm, fb.basis, cb.surf
V3 = np.array
fb.OUT = ROOT / "docs/review/mid_lord_bosses"
fb.VIEWS = ((180, 6), (215, 14), (30, 12))  # 正面・斜め・後ろ斜め (背の部品を見る)

bs.PAL.update({
    "wicker": [(70, 50, 26), (110, 82, 44), (150, 116, 66), (190, 152, 96)],
    "ore": [(40, 40, 46), (70, 68, 74), (104, 100, 104), (140, 136, 138)],
    "bronze2": [(80, 54, 24), (130, 92, 44), (180, 134, 70), (220, 180, 110)],
    "anem": [(120, 40, 80), (170, 70, 120), (210, 110, 160), (240, 170, 200)],
    "stone2": [(50, 54, 62), (80, 86, 96), (114, 120, 132), (150, 156, 168)],
    "fossil": [(150, 140, 116), (190, 180, 152), (220, 212, 186), (240, 234, 214)],
    "fur": [(36, 26, 20), (60, 44, 32), (88, 66, 48), (118, 92, 68)],
    "cloth_g": [(30, 26, 24), (52, 46, 40), (78, 70, 62), (106, 96, 86)],
    "vein": [(70, 10, 20), (110, 20, 34), (150, 40, 50), (190, 70, 80)],
    "flesh": [(90, 30, 40), (140, 56, 66), (180, 90, 96), (214, 130, 134)],
    "saltw": [(170, 176, 176), (204, 212, 210), (228, 236, 234), (246, 250, 250)],
    "g_cyan": [(60, 200, 255), (140, 235, 255), (230, 255, 255)],
    "g_amber": [(220, 120, 30), (255, 180, 60), (255, 230, 150)],
    "g_orange": [(230, 90, 20), (255, 150, 40), (255, 210, 120)],
    "g_red": [(200, 30, 30), (255, 80, 60), (255, 170, 140)],
    "g_teal": [(30, 170, 160), (90, 230, 210), (190, 255, 240)],
    "g_violet": [(140, 80, 255), (190, 140, 255), (235, 215, 255)],
})


# ---------------------------------------------------------------- 坑の頭 (坑夫の頭・肩に岩・前掛け)
def pf_a(m, R):
    """A 坑夫の道具: 頭に光る坑夫の灯、背にツルハシ、肩に巻いた綱、腰に鏨とハンマー"""
    hlo, hhi, hc = R.box("head")
    lamp = surf(R, "head", (0, 0.3, -1), at=V3([hc[0], hc[1] + 3, hc[2]]))
    m.add("lampb", b.tub("head", "ironc", [lamp + V3([0, 0, 0.5]), lamp + V3([0, 0, -2.4])], [2.4, 2.8], None, sides=8))
    m.add("lampg", b.ell("head", "glow", lamp + V3([0, 0, -2.5]), (2.1, 2.1, 0.6), segs=8, rings=2))
    m.add("strap", ring("head", "leather", V3([hc[0], lamp[1], hc[2]]), (hhi[0] - hlo[0]) / 2 + 0.2, (0, 1, 0), 0.4, n=16))
    lo, hi, c = R.box("torso")
    back = hi[2] + 0.6
    m.add("pick_h", b.tub("torso", "wood", [V3([c[0] - 8, hi[1] - 2, back]), V3([c[0] + 7, lo[1] + 2, back])], [0.6, 0.6], None, sides=6))
    m.add("pick_t", b.tub("torso", "ironc", [V3([c[0] - 11, hi[1] - 4, back + 0.3]), V3([c[0] - 8, hi[1] - 2, back + 0.3]), V3([c[0] - 4, hi[1] + 1, back + 0.3])],
                          [0.5, 0.9, 0.3], None, sides=5))
    for k in range(4):
        y = c[1] + 4 - k * 0.9
        m.add(f"rope{k}", ring("torso", "rope", V3([c[0] - 9, y - 4, c[2]]), 4.0, (1, 0.3, 0), 0.6, n=14))
    for k, x in enumerate((-4, -1, 2)):
        p = V3([c[0] + x, lo[1] + 1, lo[2] - 0.6])
        m.add(f"ch{k}", b.tub("torso", "ironc", [p, p + V3([0, -5.5, 0])], [0.55, 0.35], None, sides=5))
    m.add("hm_h", b.tub("torso", "wood", [V3([c[0] + 6, lo[1] + 1, lo[2] - 0.8]), V3([c[0] + 6, lo[1] - 6, lo[2] - 0.8])], [0.5, 0.5], None, sides=5))
    m.add("hm_t", b.box("torso", "ironc", V3([c[0] + 6, lo[1] - 6.3, lo[2] - 0.8]), (4.0, 2.0, 2.0)))


def pf_b(m, R):
    """B 鉱石の背負い籠: 背に編んだ籠、中に鉱石の塊と光る晶、胸を交差する負い紐"""
    lo, hi, c = R.box("torso")
    base = V3([c[0], c[1] - 2, hi[2] + 4.5])
    rings_ = []
    for k in range(6):
        y = base[1] + k * 2.2
        r = 4.5 + k * 0.3
        rings_.append([V3([base[0] + math.cos(u) * r, y, base[2] + math.sin(u) * r * 0.8]) for u in np.linspace(0, math.tau, 12, endpoint=False)])
        m.add(f"bw{k}", ring("torso", "wicker", V3([base[0], y, base[2]]), r, (0, 1, 0), 0.35, n=14))
    for k in range(12):
        u = k / 12 * math.tau
        m.add(f"bs{k}", b.tub("torso", "wicker", [V3([base[0] + math.cos(u) * 4.5, base[1], base[2] + math.sin(u) * 3.6]),
                                                   V3([base[0] + math.cos(u) * 6.0, base[1] + 11, base[2] + math.sin(u) * 4.8])], [0.3, 0.3], None, sides=4))
    m.add("bbot", b.ell("torso", "wicker", base, (4.6, 0.8, 3.7), segs=12, rings=3))
    rng = np.random.default_rng(141)
    for k in range(9):
        p = base + V3([rng.uniform(-3, 3), 11 + rng.uniform(-0.5, 2), rng.uniform(-2.5, 2.5)])
        m.add(f"ore{k}", b.ell("torso", "ore", p, (rng.uniform(1.2, 2.2),) * 3, segs=6, rings=3))
    for k in range(4):
        p = base + V3([rng.uniform(-2.5, 2.5), 12.5, rng.uniform(-2, 2)])
        m.add(f"cry{k}", b.cone("torso", "glow", p, (rng.uniform(-0.4, 0.4), 1, rng.uniform(-0.4, 0.4)), 3, 0.7, sides=6, rings=1))
    for s in (1, -1):
        m.add(f"sl{s}", b.tub("torso", "leather", [V3([c[0] + s * 6, hi[1] - 1, c[2]]), V3([c[0] - s * 3, c[1], lo[2] - 0.5]), V3([c[0] - s * 7, lo[1] + 2, c[2]])],
                              [0.5, 0.5, 0.5], None, sides=5))


def pf_c(m, R):
    """C 坑道の支え梁: 背に十字に組んだ坑道の梁、梁の先から灯籠が下がる"""
    lo, hi, c = R.box("torso")
    back = hi[2] + 1.5
    m.add("post", b.box("torso", "wood", V3([c[0], c[1] + 6, back]), (1.8, 26, 1.8)))
    m.add("beam", b.box("torso", "wood", V3([c[0], hi[1] + 6, back]), (26, 1.8, 1.8)))
    for s in (1, -1):
        m.add(f"brace{s}", b.tub("torso", "wood", [V3([c[0], hi[1] + 1, back]), V3([c[0] + s * 7, hi[1] + 6, back])], [0.6, 0.6], None, sides=4))
        tip = V3([c[0] + s * 12, hi[1] + 5, back])
        m.add(f"lh{s}", b.tub("torso", "ironc", [tip, tip + V3([0, -3, 0])], [0.12, 0.12], None, sides=4))
        lc = tip + V3([0, -4.6, 0])
        m.add(f"lg{s}", b.ell("torso", "glow", lc, (0.9, 1.3, 0.9), segs=8, rings=3))
        m.add(f"lt{s}", b.cone("torso", "ironc", lc + V3([0, 1.3, 0]), (0, 1, 0), 1.0, 1.2, sides=6, rings=1))
        for j in range(4):
            v = j / 4 * math.tau
            m.add(f"lf{s}{j}", b.tub("torso", "ironc", [lc + V3([math.cos(v) * 1.1, 1.3, math.sin(v) * 1.1]), lc + V3([math.cos(v) * 1.1, -1.2, math.sin(v) * 1.1])],
                                     [0.08, 0.08], None, sides=3))
    for k in range(5):
        m.add(f"nail{k}", b.ell("torso", "ironc", V3([c[0] - 10 + k * 5, hi[1] + 6, back - 1.0]), (0.4, 0.4, 0.3), segs=5, rings=2))


# ---------------------------------------------------------------- 鐘の隠者 (殻を負うやどかり)
def bh_a(m, R):
    """A 鐘の殻: 殻の口に青銅の大きな鐘が吊られ、殻のてっぺんに鐘楼の小屋根"""
    lo, hi, c = R.box("shell")
    top = surf(R, "shell", (0, 1, 0))
    m.add("roof", b.cone("shell", "bronze2", top + V3([0, 7, 0]), (0, 1, 0), 6.0, 7.5, sides=8, rings=2))
    for k in range(4):
        u = k / 4 * math.tau + math.pi / 4
        m.add(f"pil{k}", b.tub("shell", "wood", [top + V3([math.cos(u) * 5, -0.5, math.sin(u) * 5]), top + V3([math.cos(u) * 5, 7.2, math.sin(u) * 5])],
                               [0.6, 0.6], None, sides=5))
    m.add("rope", b.tub("shell", "rope", [top + V3([0, 7, 0]), top + V3([0, 6.0, 0])], [0.25, 0.25], None, sides=4))
    fb.bell(m, "bigbell", "shell", "bronze2", top + V3([0, 6.4, 0]), 5.0)
    mouth = surf(R, "shell", (0, -0.3, -1))
    fb.bell(m, "mouthbell", "shell", "bronze2", mouth + V3([0, 2, -1.5]), 4.5)
    m.add("bar", b.tub("shell", "wood", [mouth + V3([-5, 2.6, -1]), mouth + V3([5, 2.6, -1])], [0.5, 0.5], None, sides=6))


def bh_b(m, R):
    """B 殻の上のイソギンチャク: 殻に揺れる触手のイソギンチャクの群れと、ヒトデ"""
    rng = np.random.default_rng(151)
    for k in range(7):
        p = surf(R, "shell", (rng.uniform(-0.7, 0.7), 1, rng.uniform(-0.7, 0.7)))
        n_ = nrm(p - R.box("shell")[2])
        m.add(f"an{k}", b.tub("shell", "anem", [p - n_ * 0.5, p + n_ * 3.0], [2.4, 2.0], None, sides=8))
        for j in range(12):
            u = j / 12 * math.tau
            a1, a2 = basis(n_)
            q = p + n_ * 3.0 + (a1 * math.cos(u) + a2 * math.sin(u)) * 1.7
            m.add(f"tn{k}{j}", b.cone("shell", "anem", q, n_ + (a1 * math.cos(u) + a2 * math.sin(u)) * 0.5, 3.5 + rng.uniform(0, 1.5), 0.45, sides=4, rings=2,
                                      bend=tuple((a1 * math.cos(u) + a2 * math.sin(u)) * 1.0)))
        m.add(f"ac{k}", b.ell("shell", "glow", p + n_ * 3.1, (1.0, 1.0, 1.0), segs=6, rings=2))
    for k in range(2):
        p = surf(R, "shell", (rng.uniform(-1, 1), 0.3, rng.uniform(-1, 1)))
        for j in range(5):
            u = j / 5 * math.tau
            m.add(f"sf{k}{j}", b.cone("shell", "coral", p, (math.cos(u), math.sin(u) * 0.5, math.sin(u)), 2.6, 0.7, sides=4, rings=2))


def bh_c(m, R):
    """C 殻の住まい: 殻に小さな丸窓が開いて灯りが漏れ、煙突と扉、梯子が掛かる"""
    rng = np.random.default_rng(161)
    for k in range(5):
        d = V3([rng.uniform(-1, 1), rng.uniform(-0.2, 0.8), rng.uniform(-0.2, 1)])
        p = surf(R, "shell", d)
        n = nrm(d)
        m.add(f"win{k}", ring("shell", "wood", p + n * 0.3, 2.4, n, 0.5, n=14))
        m.add(f"wg{k}", b.ell("shell", "glow", p + n * 0.1, (2.0, 2.0, 2.0), segs=8, rings=3))
        m.add(f"wx{k}", b.tub("shell", "wood", [p + n * 0.5 + nrm(np.cross(n, (0, 1, 0))) * 2.2, p + n * 0.5 - nrm(np.cross(n, (0, 1, 0))) * 2.2], [0.25, 0.25],
                              None, sides=4))
    top = surf(R, "shell", (0.3, 1, 0.3))
    m.add("chim", b.tub("shell", "stone2", [top - V3([0, 1, 0]), top + V3([0, 8, 0])], [1.8, 1.6], None, sides=6))
    m.add("chimc", b.tub("shell", "stone2", [top + V3([0, 8, 0]), top + V3([0, 9.2, 0])], [2.3, 2.3], None, sides=6))
    side = surf(R, "shell", (1, -0.2, 0))
    m.add("door", b.box("shell", "wood", side + V3([0.2, 0, 0]), (0.4, 3.2, 2.0)))
    m.add("knob", b.ell("shell", "bronze2", side + V3([0.5, 0, 0.6]), (0.2, 0.2, 0.2), segs=5, rings=2))
    for s in (1, -1):
        m.add(f"lad{s}", b.tub("shell", "wood", [side + V3([1.5, -6, s * 1.0]), side + V3([0.6, 1, s * 1.0])], [0.2, 0.2], None, sides=4))
    for k in range(4):
        m.add(f"rung{k}", b.tub("shell", "wood", [side + V3([1.4 - k * 0.25, -5 + k * 1.7, -1.0]), side + V3([1.4 - k * 0.25, -5 + k * 1.7, 1.0])], [0.15, 0.15],
                                None, sides=4))


# ---------------------------------------------------------------- 晶洞の巨像 (石の巨人・胸の晶)
def gg_a(m, R):
    """A 突き出す晶洞: 肩と背と腕から、六角の晶の群れが突き出し、内が光る"""
    rng = np.random.default_rng(171)
    for bone, n, d0 in (("torso", 9, (0, 0.6, 1)), ("arm_l", 4, (0.6, 1, 0)), ("arm_r", 4, (-0.6, 1, 0))):
        for k in range(n):
            d = nrm(V3(d0) + V3([rng.uniform(-0.6, 0.6), rng.uniform(-0.3, 0.3), rng.uniform(-0.6, 0.6)]))
            p = surf(R, bone, d)
            for j in range(3):
                d2 = nrm(d + V3([rng.uniform(-0.4, 0.4), rng.uniform(-0.2, 0.4), rng.uniform(-0.4, 0.4)]))
                m.add(f"gc{bone}{k}{j}", fb.b.tub(bone, "glow" if j == 0 else "saltw", [p - d2 * 0.8, p + d2 * rng.uniform(2.5, 5.0) * 0.75, p + d2 * rng.uniform(3, 5.5)],
                                                  [0.9 - j * 0.15, 0.85 - j * 0.15, 0.05], None, sides=6))


def gg_b(m, R):
    """B 光る鉱脈: 体中に光る鉱脈の筋が走り、筋の交わる所に宝石の鋲"""
    rng = np.random.default_rng(181)
    for bone in ("torso", "arm_l", "arm_r", "leg_l", "leg_r", "head"):
        for k in range(4 if bone == "torso" else 2):
            p = surf(R, bone, (rng.uniform(-1, 1), rng.uniform(-0.5, 0.8), -1))
            pts = [p]
            cb_ = R.box(bone)[2]
            for j in range(4):
                q = pts[-1] + V3([rng.uniform(-1.6, 1.6), -1.8, 0])
                pts.append(surf(R, bone, (0, 0, -1), at=V3([q[0], q[1], cb_[2]])))
            m.add(f"vn{bone}{k}", b.tub(bone, "glow", [x + V3([0, 0, -0.15]) for x in pts], [0.3, 0.26, 0.22, 0.16, 0.08], None, sides=4))
            m.add(f"gm{bone}{k}", b.ell(bone, "anem", p + V3([0, 0, -0.3]), (0.6, 0.6, 0.4), segs=6, rings=3))


def gg_c(m, R):
    """C 埋まった化石: 石の体に巻貝・魚の骨・葉の化石が浮き彫りに突き出し、欠けた縁"""
    rng = np.random.default_rng(191)
    for k in range(6):
        bone = ("torso", "arm_l", "arm_r", "leg_l", "leg_r", "torso")[k]
        d = V3([rng.uniform(-1, 1), rng.uniform(-0.4, 0.6), -1])
        p = surf(R, bone, d)
        n = nrm(d)
        kind = k % 3
        if kind == 0:
            m.add(f"spiral{k}", b.spiral(bone, "fossil", p, n, nrm(np.cross(n, (0, 1, 0))), 2.2, 0.4, 2.6, 26, 0.6, 0.2))
        elif kind == 1:
            spine = [p + V3([-2.5 + j, math.sin(j) * 0.3, 0]) - n * 0.0 for j in range(6)]
            m.add(f"fish{k}", b.tub(bone, "fossil", spine, [0.25] * 6, None, sides=4))
            for j in range(5):
                for s in (1, -1):
                    m.add(f"fr{k}{j}{s}", b.tub(bone, "fossil", [spine[j], spine[j] + V3([0.3, s * 1.2, 0])], [0.12, 0.06], None, sides=3))
        else:
            m.add(f"leaf{k}", b.slab(bone, "fossil", [p + V3([0, 2, 0]), p + V3([1, 0.5, 0]), p + V3([0, -2, 0]), p + V3([-1, 0.5, 0])], n, 0.3))
            m.add(f"lv{k}", b.tub(bone, "stone2", [p + V3([0, 2, 0]) + n * 0.2, p + V3([0, -2, 0]) + n * 0.2], [0.08, 0.08], None, sides=3))


# ---------------------------------------------------------------- 牛の鍛冶 (牛頭・前掛け・金床の盾?)
def ox_a(m, R):
    """A 鼻輪と角の輪: 大きな真鍮の鼻輪、角に巻いた真鍮の輪と鎖、耳に飾りの輪"""
    lo, hi, c = R.box("head")
    nose = surf(R, "head", (0, -0.4, -1))
    m.add("nring", ring("head", "bronze2", nose + V3([0, -1.6, -0.6]), 1.6, (1, 0, 0), 0.35, n=16))
    for s in (1, -1):
        for k in range(3):
            p = V3([c[0] + s * (6.5 + k * 2.2), hi[1] - 6 + k * 2.5, c[2]])
            m.add(f"hr{s}{k}", ring("head", "bronze2", p, 1.3 - k * 0.2, (1, 0.6 * s, 0), 0.3, n=12))
        chain(m, f"hc{s}", "head", "ironc", V3([c[0] + s * 7, hi[1] - 5.6, c[2]]), V3([c[0] + s * 4, lo[1] + 2, c[2] - 3]), link=1.0, th=0.16)
        ear = V3([c[0] + s * 6, c[1] + 2, c[2]])
        m.add(f"ear{s}", ring("head", "bronze2", ear, 0.7, (1, 0, 0), 0.18, n=10))


def ox_b(m, R):
    """B 背負った金床: 背に金床を鎖で負い、上に赤く焼けた鉄の延べ棒、金床の脚に鋲"""
    lo, hi, c = R.box("torso")
    p = V3([c[0], c[1] + 2, hi[2] + 3.5])
    m.add("anv_top", b.box("torso", "ironc", p + V3([0, 2.5, 0]), (12, 2.2, 5)))
    m.add("anv_horn", b.cone("torso", "ironc", p + V3([6, 2.5, 0]), (1, 0, 0), 4.5, 1.1, sides=6, rings=2, rb=0.8))
    m.add("anv_waist", b.box("torso", "ironc", p + V3([0, 0, 0]), (6, 3, 3.6), taper=(0.8, 1.0)))
    m.add("anv_foot", b.box("torso", "ironc", p + V3([0, -2.5, 0]), (10, 2, 5)))
    for k in range(3):
        m.add(f"ingot{k}", b.box("torso", "glow", p + V3([-3 + k * 2.6, 4.2, 0]), (2.2, 0.8, 1.2)))
    for s in (1, -1):
        chain(m, f"ac{s}", "torso", "ironc", p + V3([s * 4, 2.5, -2.5]), V3([c[0] + s * 6, hi[1] - 1, lo[2] - 0.3]), link=1.2, th=0.22)
        chain(m, f"al{s}", "torso", "ironc", p + V3([s * 4, -2.5, -2.5]), V3([c[0] + s * 6, lo[1] + 2, lo[2] - 0.3]), link=1.2, th=0.22)
    for k in range(4):
        m.add(f"rv{k}", b.ell("torso", "bronze2", p + V3([-4.5 + k * 3, -2.5, -2.6]), (0.35, 0.35, 0.25), segs=5, rings=2))


def ox_c(m, R):
    """C 毛深い首と編んだ顎鬚: 首と肩に粗い毛の房、顎に編んだ長い鬚と留め輪"""
    rng = np.random.default_rng(201)
    lo, hi, c = R.box("torso")
    for k in range(26):
        u = rng.uniform(-math.pi, math.pi)
        p = V3([c[0] + math.sin(u) * 9, hi[1] - rng.uniform(0, 3), c[2] + math.cos(u) * 6])
        d = nrm(V3([math.sin(u) * 0.6, -1, math.cos(u) * 0.6]))
        m.add(f"tuft{k}", b.cone("torso", "fur", p, d, rng.uniform(3, 5.5), 1.0, sides=4, rings=2, bend=(0, -0.5, 0)))
    hlo, hhi, hc = R.box("head")
    chin = surf(R, "head", (0, -1, -0.6))
    pts = [chin + V3([math.sin(j * 1.4) * 0.5, -j * 1.4, -0.3 * j]) for j in range(7)]
    m.add("beard", b.tub("head", "fur", pts, [1.4, 1.3, 1.1, 0.9, 0.8, 0.6, 0.3], None, sides=6))
    for j in (2, 4):
        m.add(f"bring{j}", ring("head", "bronze2", pts[j], [1.2, 1.0, 0.9, 0.8, 0.7][j - 1] + 0.2, nrm(pts[j + 1] - pts[j]), 0.25, n=12))


# ---------------------------------------------------------------- 肋の主 (骸骨の灯台守)
def lr_a(m, R):
    """A 肋に吊った灯: 肋の骨ひとつずつから小さな灯の籠が細い紐で下がり、赤く灯る"""
    lo, hi, c = R.box("chest")
    for k in range(10):
        s = 1 if k % 2 else -1
        y = hi[1] - 6 - (k // 2) * 3.4
        p = surf(R, "chest", (s, 0, -0.6), at=V3([c[0], y, c[2]]))
        m.add(f"st{k}", b.tub("chest", "cloth_g", [p, p + V3([0, -2.2, -0.3])], [0.06, 0.06], None, sides=3))
        lc = p + V3([0, -3.8, -0.6])
        m.add(f"lg{k}", b.ell("chest", "glow", lc, (0.9, 1.3, 0.9), segs=6, rings=3))
        for j in range(4):
            v = j / 4 * math.tau
            m.add(f"lf{k}{j}", b.tub("chest", "ironc", [lc + V3([math.cos(v) * 1.05, 1.4, math.sin(v) * 1.05]), lc + V3([math.cos(v) * 1.05, -1.4, math.sin(v) * 1.05])],
                                     [0.1, 0.1], None, sides=3))
        m.add(f"lt{k}", b.cone("chest", "ironc", lc + V3([0, 1.4, 0]), (0, 1, 0), 1.0, 1.2, sides=5, rings=1))


def lr_b(m, R):
    """B 破れた外套と骨の冠: 肩から裾まで破れた外套の帯が垂れ、頭の棘の冠が大きく尖る"""
    lo, hi, c = R.box("chest")
    rng = np.random.default_rng(211)
    for k in range(14):
        u = math.radians(-140 + k * 280 / 13)
        top = V3([c[0] + math.sin(u) * 8, hi[1] - 2, c[2] + math.cos(u) * 5])
        L = rng.uniform(16, 30)
        out = nrm(V3([math.sin(u), 0, math.cos(u)]))
        side = nrm(np.cross(out, (0, 1, 0)))
        m.add(f"rag{k}", b.slab("chest", "cloth_g", [top - side * 1.1, top + side * 1.1, top + side * 0.6 - V3([0, L, 0]) + out * 1.5,
                                                    top - side * 0.4 - V3([0, L * 0.85, 0]) + out * 1.5], out, 0.25))
    hlo, hhi, hc = R.box("head")
    for k in range(9):
        u = k / 9 * math.tau
        p = V3([hc[0] + math.cos(u) * 3.4, hhi[1] - 2, hc[2] + math.sin(u) * 3.4])
        m.add(f"cr{k}", b.cone("head", "fossil", p, (math.cos(u) * 0.35, 1, math.sin(u) * 0.35), 4 + (k % 2) * 2.5, 0.6, sides=5, rings=2))


def lr_c(m, R):
    """C 肋の中の心臓: 肋の籠の中に赤く脈打つ心臓が吊られ、血管が肋に絡む"""
    lo, hi, c = R.box("chest")
    front = surf(R, "chest", (0, 0, -1), at=V3([c[0], c[1] + 1, c[2]]))
    hp = V3([c[0], c[1] + 1, front[2] + 1.2])
    m.add("heart", b.ell("chest", "flesh", hp, (4.2, 5.0, 3.6), segs=12, rings=6))
    m.add("heartg", b.ell("chest", "glow", hp + V3([0, 0, -3.3]), (2.2, 2.8, 0.6), segs=8, rings=3))
    for s in (1, -1):
        m.add(f"aorta{s}", b.tub("chest", "flesh", [hp + V3([s * 1.0, 3.5, 0]), hp + V3([s * 1.8, 6.5, 0.5]), hp + V3([s * 1.2, 8, 1.5])], [1.0, 0.8, 0.6], None, sides=6))
    rng = np.random.default_rng(221)
    for k in range(10):
        d = nrm(V3([rng.uniform(-1, 1), rng.uniform(-0.6, 0.8), rng.uniform(-1, 0.3)]))
        start = hp + d * 3.2
        end = surf(R, "chest", d, at=hp) - d * 0.3
        mid = (start + end) / 2 + V3([0, rng.uniform(-1, 1), 0])
        m.add(f"vein{k}", b.tub("chest", "vein", [start, mid, end], [0.35, 0.25, 0.15], None, sides=4))


# ---------------------------------------------------------------- 白殻の主 (塩の巻貝)
def ls_a(m, R):
    """A 螺旋の棘の列: 殻の巻きに沿って、殻の表から塩の棘が一列に並び、根元に小さな瘤"""
    lo, hi, c = R.box("shell")
    for i in range(30):
        t = i / 29
        u = t * 2.6 * math.tau
        y = lo[1] + 4 + t * (hi[1] - lo[1] - 5)
        d = nrm(V3([math.cos(u), 0.25, math.sin(u)]))
        p = surf(R, "shell", (math.cos(u), 0, math.sin(u)), at=V3([c[0], y, c[2]]))
        m.add(f"kn{i}", b.ell("shell", "saltw", p, (0.9 - t * 0.4,) * 3, segs=8, rings=4))
        m.add(f"sp{i}", b.cone("shell", "saltw", p - d * 0.3, d, 3.4 - t * 1.6, 0.6 - t * 0.2, sides=5, rings=2))


def ls_b(m, R):
    """B 塩の垂れ: 殻の下の縁と体から、塩の鍾乳石が氷柱のように垂れ、先に雫"""
    rng = np.random.default_rng(231)
    for k, e in enumerate(edge_points(R, "shell", 16)):
        L = rng.uniform(2, 6)
        m.add(f"st{k}", b.cone("shell", "saltw", e + V3([0, 0.6, 0]), (rng.uniform(-0.1, 0.1), -1, rng.uniform(-0.1, 0.1)), L, 0.7, sides=6, rings=3))
        m.add(f"dr{k}", b.ell("shell", "glow", e + V3([0, 0.6 - L - 0.3, 0]), (0.25, 0.35, 0.25), segs=6, rings=2))
    for k, e in enumerate(edge_points(R, "head", 6)):
        m.add(f"hs{k}", b.cone("head", "saltw", e + V3([0, 0.4, 0]), (0, -1, 0), rng.uniform(1.5, 3), 0.45, sides=6, rings=2))


def ls_c(m, R):
    """C 光る粘液の跡: 這った足の縁から光る粘液が糸を引き、口から雫が垂れる"""
    rng = np.random.default_rng(241)
    for k, e in enumerate(edge_points(R, "body", 14)):
        m.add(f"sl{k}", b.ell("body", "glow", e + V3([0, -0.1, 0]), (rng.uniform(1.0, 2.0), 0.2, rng.uniform(1.0, 2.0)), segs=8, rings=2))
        if k % 2:
            m.add(f"th{k}", b.tub("body", "glow", [e + V3([0, 1.2, 0]), e + V3([0.3, 0.2, 0.2])], [0.12, 0.05], None, sides=3))
    mouth = surf(R, "head", (0, -0.6, -1))
    for k in range(3):
        p = mouth + V3([-1 + k, 0, 0])
        m.add(f"md{k}", b.tub("head", "glow", [p, p + V3([0, -1.5 - k * 0.6, -0.2])], [0.2, 0.08], None, sides=4))
        m.add(f"mdd{k}", b.ell("head", "glow", p + V3([0, -1.8 - k * 0.6, -0.2]), (0.3, 0.4, 0.3), segs=6, rings=2))


# ---------------------------------------------------------------- 鼓動まねの主 (心臓の体・六本脚)
def lm_a(m, R):
    """A 体を覆う血管の網: 心臓の体の表を太い血管が網のように這い、脈が光る"""
    rng = np.random.default_rng(251)
    lo, hi, c = R.box("body")
    for k in range(12):
        u = rng.uniform(0, math.tau)
        pts = []
        for j in range(6):
            t = j / 5
            d = nrm(V3([math.cos(u + t * 0.5), 0.7 - t * 1.3, math.sin(u + t * 0.5)]))
            q = surf(R, "body", d, at=c)
            pts.append(q + d * 0.05)
        # 間を細かく: 隣の点のあいだも表に寄せる
        fine = []
        for j in range(5):
            for t in (0.0, 0.5):
                q = pts[j] + (pts[j + 1] - pts[j]) * t
                d = nrm(q - c)
                fine.append(surf(R, "body", d, at=c) + d * 0.05)
        fine.append(pts[-1])
        m.add(f"vn{k}", b.tub("body", "vein", fine, [0.75 - 0.05 * i for i in range(len(fine))], None, sides=5))
        m.add(f"pl{k}", b.ell("body", "glow", pts[2] + nrm(pts[2] - c) * 0.4, (0.4, 0.4, 0.4), segs=6, rings=2))


def lm_b(m, R):
    """B 心臓の管と縫い目: 体の上から太い動脈の管が三本伸びて曲がり、体に縫い合わせた跡"""
    lo, hi, c = R.box("body")
    top = surf(R, "body", (0, 1, 0.2))
    for k, (dx, dz, L) in enumerate(((-3, 1, 9), (0, 3, 12), (3, 1, 8))):
        b0 = top + V3([dx, -0.5, dz])
        pts = [b0, b0 + V3([dx * 0.3, L * 0.5, 0.5]), b0 + V3([dx * 0.8 + 1.5, L * 0.85, -1.5]), b0 + V3([dx + 3, L * 0.7, -3])]
        m.add(f"art{k}", b.tub("body", "flesh", pts, [1.6, 1.4, 1.2, 1.0], None, sides=8))
        m.add(f"artc{k}", ring("body", "vein", pts[-1], 1.0, nrm(pts[-1] - pts[-2]), 0.3, n=12))
    for s in (1, -1):
        pts = []
        for j in range(7):
            d = nrm(V3([s, 0.8 - j * 0.28, -0.6]))
            pts.append(surf(R, "body", d, at=c) + d * 0.15)
        m.add(f"seam{s}", b.tub("body", "vein", pts, [0.35] * 7, None, sides=4))
        for j in range(7):
            n = nrm(pts[j] - c)
            t = nrm(np.cross(n, (0, 1, 0)))
            m.add(f"stc{s}{j}", b.tub("body", "saltw", [pts[j] - t * 1.6 + n * 0.25, pts[j] + t * 1.6 + n * 0.25], [0.22, 0.22], None, sides=4))


def lm_c(m, R):
    """C 脈打つ瘤と目: 体のあちこちに膨らんだ瘤があり、瘤のいくつかに目が開く"""
    rng = np.random.default_rng(261)
    for k in range(12):
        d = nrm(V3([rng.uniform(-1, 1), rng.uniform(-0.5, 1), rng.uniform(-1, 1)]))
        p = surf(R, "body", d)
        r = rng.uniform(1.4, 2.6)
        m.add(f"tum{k}", b.ell("body", "flesh", p + d * (r * 0.4), (r, r * 0.9, r), segs=10, rings=5))
        if k % 3 == 0:
            m.add(f"ew{k}", b.ell("body", "saltw", p + d * (r * 1.05), (r * 0.55, r * 0.55, r * 0.3), segs=8, rings=3))
            m.add(f"ep{k}", b.ell("body", "glow", p + d * (r * 1.3), (r * 0.25, r * 0.3, r * 0.15), segs=6, rings=2))


BOSSES = {
    "pit_foreman": ("坑の頭", "g_amber", [("A 坑夫の道具", pf_a), ("B 鉱石の背負い籠", pf_b), ("C 坑道の支え梁", pf_c)]),
    "bell_hermit": ("鐘の隠者", "g_teal", [("A 鐘の殻", bh_a), ("B 殻の上のイソギンチャク", bh_b), ("C 殻の住まい", bh_c)]),
    "geode_golem": ("晶洞の巨像", "g_cyan", [("A 突き出す晶洞", gg_a), ("B 光る鉱脈", gg_b), ("C 埋まった化石", gg_c)]),
    "ox_smith": ("牛の鍛冶", "g_orange", [("A 鼻輪と角の輪", ox_a), ("B 背負った金床", ox_b), ("C 毛深い首と編んだ鬚", ox_c)]),
    "lord_rib": ("肋の主", "g_red", [("A 肋に吊った灯", lr_a), ("B 破れた外套と骨の冠", lr_b), ("C 肋の中の心臓", lr_c)]),
    "lord_salt": ("白殻の主", "g_teal", [("A 螺旋の稜の棘", ls_a), ("B 塩の垂れ", ls_b), ("C 光る粘液の跡", ls_c)]),
    "lord_storm": ("鼓動まねの主", "g_violet", [("A 体を覆う血管の網", lm_a), ("B 心臓の管と縫い目", lm_b), ("C 脈打つ瘤と目", lm_c)]),
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
