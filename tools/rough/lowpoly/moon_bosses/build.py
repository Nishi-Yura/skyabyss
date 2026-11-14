# -*- coding: utf-8 -*-
"""月の 5 体の作り込みのラフ (2026-11-11 の声「次は月のボスたちを作り込んで」。最初の目的の一つ目「部品数と、細かい形」)。

今の模型に、そのボスのためだけに考えた細かい部品を足す三案 (案どうし・ボスどうし・終章の主と同じ足し方はしない)。
部品は付ける骨の名前で作る。道具 (輪・鎖・縁の点・描き方) は終章のラフ tools/rough/lowpoly/finale_bosses/build.py のものを使う。

    python -X utf8 -I tools/rough/lowpoly/moon_bosses/build.py [名前 ...]
出力: docs/review/moon_bosses/<名前>.png
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
bs, b, ring, chain, edge_points, nrm, basis = fb.bs, fb.b, fb.ring, fb.chain, fb.edge_points, fb.nrm, fb.basis
V3 = np.array
fb.OUT = ROOT / "docs/review/moon_bosses"

# 月の色の段 (螺鈿の藍・薄紫・青白に合わせる)
bs.PAL.update({
    "mrock": [(40, 40, 60), (78, 76, 104), (120, 118, 150), (168, 168, 196)],
    "silverm": [(90, 96, 120), (150, 158, 184), (206, 212, 232), (240, 244, 252)],
    "indigo": [(14, 14, 30), (26, 26, 50), (44, 42, 76), (66, 62, 104)],
    "cloth_m": [(40, 36, 70), (66, 60, 106), (98, 92, 146), (134, 128, 184)],
    "g_moon": [(150, 190, 255), (200, 225, 255), (245, 250, 255)],
})


# ---------------------------------------------------------------- 月の核 (天球儀の珠・目・輪・上下の尖塔)
def mc_a(m, R):
    """A 天球儀の目盛りと小さな月: 輪に目盛りの刻み、輪の上を巡る小さな月が三つ、輪どうしを留める金具"""
    for rn in ("ring_a", "ring_b", "ring_c"):
        V = np.concatenate([f.verts for f in R.faces if f.bone == rn])
        c = V.mean(0)
        d = V - c
        nrm_v = nrm(np.linalg.svd(d - d.mean(0))[2][2])
        r = float(np.median(np.linalg.norm(d, axis=1)))
        a1, a2 = basis(nrm_v)
        for k in range(24):
            u = k / 24 * math.tau
            p = c + (a1 * math.cos(u) + a2 * math.sin(u)) * r
            out = nrm(p - c)
            L = 2.8 if k % 6 == 0 else 1.5
            m.add(f"tk{rn}{k}", b.cone(rn, "silverm", p, out, L, 0.42, sides=4, rings=1))
        for j in range(1 if rn != "ring_b" else 2):
            u = (j * 2.4 + {"ring_a": 0.4, "ring_b": 1.6, "ring_c": 3.0}[rn])
            p = c + (a1 * math.cos(u) + a2 * math.sin(u)) * r
            m.add(f"mn{rn}{j}", b.ell(rn, "mrock", p, (2.6, 2.6, 2.6), segs=12, rings=6))
            m.add(f"mng{rn}{j}", b.ell(rn, "glow", p + nrm(p - c) * 1.9, (1.3, 1.3, 0.6), segs=8, rings=2))
            for q in range(3):
                d2 = nrm(np.cross(nrm(p - c), (0, 1, 0)) * math.cos(q * 2.1) + V3([0, 1, 0]) * math.sin(q * 2.1))
                m.add(f"mcr{rn}{j}{q}", ring(rn, "indigo", p + d2 * 2.4, 0.6, d2, 0.15, n=8))
    lo, hi, c = R.box("core")
    for k in range(6):
        u = k / 6 * math.tau
        p = c + V3([math.cos(u) * 13.5, 0, math.sin(u) * 13.5])
        m.add(f"clip{k}", b.ell("core", "silverm", p, (0.8, 1.6, 0.8), segs=8, rings=3))


def mc_b(m, R):
    """B 割れた月の殻: 光る珠の半分を、割れた月の岩の殻が覆い、割れ目から光が漏れる。殻に小さな穴"""
    lo, hi, c = R.box("core")
    rr = (hi[0] - lo[0]) / 2
    rng = np.random.default_rng(21)
    for k in range(16):
        d = nrm(rng.normal(size=3))
        if d[2] < -0.35 and abs(d[1]) < 0.5:
            d[2] = 0.3
            d = nrm(d)
        a1, a2 = basis(d)
        cen = c + d * (rr + 0.4)
        s = rng.uniform(3.2, 5.0)
        poly = [cen + (a1 * math.cos(u) + a2 * math.sin(u)) * s * (0.75 + 0.25 * rng.uniform()) for u in np.linspace(0, math.tau, 6, endpoint=False)]
        m.add(f"pl{k}", b.slab("core", "mrock", poly, d, 1.0))
        if k % 3 == 0:
            m.add(f"cr{k}", ring("core", "indigo", cen + d * 0.55, s * 0.35, d, 0.25, n=10))
    for k in range(6):
        u = k / 6 * math.tau
        m.add(f"leak{k}", b.cone("core", "glow", c + V3([math.cos(u) * rr, math.sin(u) * rr * 0.6, 2]), (math.cos(u), math.sin(u) * 0.6, 0.6), 3.5, 0.5,
                                 sides=4, rings=1))


def mc_c(m, R):
    """C 目の神経: 目の裏から神経の束が垂れて下の尖塔に絡み、目には細い血管、瞳に虹彩の輪"""
    lo, hi, c = R.box("eye")
    blo, bhi, bc = R.box("spire_b")
    rng = np.random.default_rng(23)
    for k in range(7):
        u = k / 7 * math.tau
        start = V3([c[0] + math.cos(u) * 2.5, lo[1] + 0.5, c[2] + 1.5])
        pts = [start]
        for j in range(5):
            t = (j + 1) / 5
            pts.append(V3([bc[0] + math.cos(u + t * 2) * (3.5 - t * 1.5), start[1] - t * (start[1] - blo[1] - 2), bc[2] + math.sin(u + t * 2) * (3.5 - t * 1.5)]))
        pts = [q + V3([0, 0, -2.5 * (1 - abs(0.5 - j / 5) * 2)]) for j, q in enumerate(pts)]
        m.add(f"nv{k}", b.tub("eye", "indigo", pts, [0.9, 0.8, 0.7, 0.55, 0.4, 0.2], None, sides=6))
        m.add(f"nvg{k}", b.ell("eye", "glow", pts[2], (0.45, 0.45, 0.45), segs=6, rings=2))
    for k in range(8):
        u = rng.uniform(0, math.tau)
        pts = [V3([c[0] + math.cos(u + j * 0.2) * (4.0 - j * 0.6), c[1] + math.sin(u + j * 0.2) * (4.0 - j * 0.6), lo[2] - 0.1 + j * 0.2]) for j in range(4)]
        m.add(f"vn{k}", b.tub("eye", "g_pink_m", pts, [0.12, 0.1, 0.08, 0.05], None, sides=3))
    m.add("iris", ring("eye", "silverm", V3([c[0], c[1], lo[2] - 0.2]), 2.4, (0, 0, 1), 0.2))


# ---------------------------------------------------------------- 月秤 (天秤を担ぐ巨人)
def ms_a(m, R):
    """A 錘と針: 天秤の真ん中に釣り合いの針と目盛り、皿の縁に彫りの輪、梁から鎖で錘が下がる"""
    lo, hi, c = R.box("beam")
    top = V3([c[0], hi[1] + 0.2, c[2]])
    m.add("needle", b.cone("beam", "silverm", top, (0, 1, 0), 7.0, 0.6, sides=4, rings=1))
    m.add("arc", b.tub("beam", "silverm", [top + V3([math.sin(u) * 5.5, math.cos(u) * 5.5 - 0.5, 0]) for u in np.linspace(-0.7, 0.7, 9)], [0.25] * 9, None, sides=4))
    for k, u in enumerate(np.linspace(-0.7, 0.7, 7)):
        m.add(f"mk{k}", b.cone("beam", "glow", top + V3([math.sin(u) * 5.5, math.cos(u) * 5.5 - 0.5, 0]), (math.sin(u), math.cos(u), 0), 1.0, 0.18, sides=3, rings=1))
    for s in (1, -1):
        p = V3([c[0] + s * 13, lo[1], c[2]])
        chain(m, f"wc{s}", "beam", "indigo", p, p + V3([0, -10, 0]), link=1.2, th=0.2)
        m.add(f"wt{s}", b.tub("beam", "mrock", [p + V3([0, -10.4, 0]), p + V3([0, -13.4, 0])], [1.6, 1.0], None, sides=6))
        m.add(f"wh{s}", ring("beam", "silverm", p + V3([0, -10.0, 0]), 0.6, (1, 0, 0), 0.15, n=8))
    V = np.concatenate([f.verts for f in R.faces if f.bone in ("arm_l", "arm_r")])
    for s, arm in ((1, "arm_r"), (-1, "arm_l")):
        Vp = np.concatenate([f.verts for f in R.faces if f.bone == arm])
        low = Vp[Vp[:, 1] < Vp[:, 1].min() + 3]
        cc = low.mean(0)
        rr = max(2.0, float(np.max(np.linalg.norm((low - cc)[:, [0, 2]], axis=1))))
        m.add(f"pan{arm}", ring(arm, "silverm", V3([cc[0], low[:, 1].max() + 0.2, cc[2]]), rr * 0.95, (0, 1, 0), 0.3, n=20))


def ms_b(m, R):
    """B 月の岩の巨人: 肩と背に月の岩の塊と結晶が生え、体のあちこちに穴、膝に岩の当て"""
    rng = np.random.default_rng(31)
    blo, bhi, bc = R.box("body")
    for k in range(9):
        u = rng.uniform(-1.2, 1.2) + (math.pi if k % 2 else 0)
        p = V3([bc[0] + math.sin(u) * 8, bhi[1] - rng.uniform(1, 8), bc[2] + math.cos(u) * 7])
        m.add(f"rk{k}", b.ell("body", "mrock", p, (rng.uniform(1.8, 3), rng.uniform(1.4, 2.4), rng.uniform(1.8, 3)), segs=7, rings=3))
    for k in range(7):
        u = rng.uniform(-0.8, 0.8)
        p = V3([bc[0] + math.sin(u) * 6, bhi[1] - 3, bhi[2] - 1])
        m.add(f"cy{k}", fb.b.tub("body", "glow", [p, p + V3([math.sin(u) * 2, 4 + k % 3 * 1.5, 2]) * 1.0], [0.8, 0.05], None, sides=6))
    for arm in ("arm_l", "arm_r"):
        alo, ahi, ac = R.box(arm)
        for k in range(4):
            p = V3([ac[0] + rng.uniform(-3, 3), ahi[1] - 4 - k * 4, ac[2] + rng.uniform(-2, 2)])
            m.add(f"ar{arm}{k}", b.ell(arm, "mrock", p, (rng.uniform(1.5, 2.4), rng.uniform(1.2, 2.0), rng.uniform(1.5, 2.4)), segs=7, rings=3))
    for leg in ("leg_l", "leg_r"):
        lo, hi, c = R.box(leg)
        m.add(f"kn{leg}", b.ell(leg, "mrock", V3([c[0], c[1] + 1, lo[2] - 0.2]), (3.0, 2.6, 1.6), segs=8, rings=3))


def ms_c(m, R):
    """C 天秤の祭司: 梁に願いの紙垂と鈴の房がいくつも結ばれ、腰に布の前垂れ、頭に月の冠"""
    lo, hi, c = R.box("beam")
    for k in range(10):
        x = lo[0] + 2 + k * (hi[0] - lo[0] - 4) / 9
        if abs(x) < 6:
            continue
        p = V3([x, lo[1], c[2] - 1.2])
        for j in range(3):
            q = p + V3([0, -1.2 - j * 2.2, 0])
            m.add(f"sh{k}{j}", b.slab("beam", "silverm", [q + V3([-0.9, 0.9, 0]), q + V3([0.9, 0.9, 0]), q + V3([0.5, -1.0, 0]), q + V3([-0.5, -1.0, 0])],
                                      (0, 0, 1), 0.1))
        m.add(f"sr{k}", b.tub("beam", "cloth_m", [p, p + V3([0, -7, 0])], [0.08, 0.08], None, sides=3))
    blo, bhi, bc = R.box("body")
    m.add("apron", b.slab("body", "cloth_m", [V3([-5, blo[1] + 6, blo[2] - 0.3]), V3([5, blo[1] + 6, blo[2] - 0.3]), V3([4, blo[1] - 6, blo[2] - 1.2]),
                                               V3([-4, blo[1] - 6, blo[2] - 1.2])], (0, 0, 1), 0.4))
    for k in range(5):
        m.add(f"ap{k}", b.box("body", "silverm", V3([-3.2 + k * 1.6, blo[1] - 4.5, blo[2] - 1.4]), (0.5, 2.0, 0.2)))
    hlo, hhi, hc = R.box("head")
    for k in range(7):
        u = math.radians(-90 + k * 30)
        p = V3([hc[0] + math.sin(u) * 4, hhi[1] - 1, hc[2] - math.cos(u) * 4])
        m.add(f"cw{k}", b.cone("head", "silverm", p, (math.sin(u) * 0.3, 1, -math.cos(u) * 0.3), 2.5 + (3 - abs(k - 3)) * 0.8, 0.45, sides=4, rings=1))
    m.add("cwb", ring("head", "silverm", V3([hc[0], hhi[1] - 1.2, hc[2]]), 4.2, (0, 1, 0), 0.35))


# ---------------------------------------------------------------- 望の灯守 (珠を抱く灯台守・杖・衣)
def fk_a(m, R):
    """A 灯台の冠: 頭に灯台の灯室の冠 (硝子の窓と屋根)、杖の先の灯籠に格子と吊り鎖"""
    hlo, hhi, hc = R.box("head")
    top = V3([hc[0], hhi[1] + 0.3, hc[2]])
    m.add("lamp_base", b.tub("head", "silverm", [top, top + V3([0, 0.8, 0])], [3.6, 3.6], None, sides=10))
    for k in range(8):
        u = k / 8 * math.tau
        m.add(f"lp{k}", b.tub("head", "silverm", [top + V3([math.cos(u) * 3.2, 0.8, math.sin(u) * 3.2]), top + V3([math.cos(u) * 3.0, 4.8, math.sin(u) * 3.0])],
                              [0.22, 0.22], None, sides=4))
    m.add("lamp_glow", b.ell("head", "glow", top + V3([0, 2.8, 0]), (2.4, 2.0, 2.4), segs=10, rings=4))
    m.add("lamp_roof", b.cone("head", "indigo", top + V3([0, 4.8, 0]), (0, 1, 0), 3.0, 3.8, sides=10, rings=2))
    m.add("lamp_tip", b.ell("head", "silverm", top + V3([0, 8.0, 0]), (0.5, 0.5, 0.5), segs=6, rings=2))
    slo, shi, sc = R.box("staff")
    p = V3([sc[0], shi[1] - 6, sc[2]])
    chain(m, "stc", "staff", "indigo", p + V3([2.2, 3, 0]), p + V3([2.2, -2, 0]), link=0.9, th=0.12)
    lc = p + V3([2.2, -3.6, 0])
    m.add("stl_g", b.ell("staff", "glow", lc, (0.9, 1.2, 0.9), segs=8, rings=3))
    for k in range(6):
        u = k / 6 * math.tau
        m.add(f"stl{k}", b.tub("staff", "silverm", [lc + V3([math.cos(u) * 1.2, 1.5, math.sin(u) * 1.2]), lc + V3([math.cos(u) * 1.2, -1.5, math.sin(u) * 1.2])],
                               [0.1, 0.1], None, sides=3))
    m.add("stl_t", b.cone("staff", "indigo", lc + V3([0, 1.5, 0]), (0, 1, 0), 1.2, 1.5, sides=6, rings=1))


def fk_b(m, R):
    """B 裾の灯籠: 衣の帯と裾から小さな灯籠がいくつも鎖で下がり、ひとつずつ灯る"""
    lo, hi, c = R.box("robe")
    for k in range(10):
        u = k / 10 * math.tau
        V = np.concatenate([f.verts for f in R.faces if f.bone == "robe"])
        yb = hi[1] - 3 - (k % 2) * 7
        band = V[np.abs(V[:, 1] - yb) < 1.5]
        rad = float(np.max(np.hypot(band[:, 0] - c[0], band[:, 2] - c[2]))) if len(band) else 10
        top = V3([c[0] + math.cos(u) * (rad + 0.3), yb, c[2] + math.sin(u) * (rad + 0.3)])
        L = 2.5 + (k % 3)
        chain(m, f"rl{k}_", "robe", "indigo", top, top + V3([0, -L, 0]), link=1.1, th=0.16)
        lc = top + V3([0, -L - 2.4, 0])
        m.add(f"rlg{k}", b.ell("robe", "glow", lc, (1.2, 1.6, 1.2), segs=8, rings=3))
        m.add(f"rlt{k}", b.cone("robe", "indigo", lc + V3([0, 1.7, 0]), (0, 1, 0), 1.5, 1.7, sides=6, rings=1))
        m.add(f"rlb{k}", b.tub("robe", "silverm", [lc + V3([0, -1.9, 0]), lc + V3([0, -1.4, 0])], [1.5, 1.5], None, sides=6))
        for j in range(4):
            v = j / 4 * math.tau
            m.add(f"rlf{k}{j}", b.tub("robe", "silverm", [lc + V3([math.cos(v) * 1.45, 1.7, math.sin(v) * 1.45]), lc + V3([math.cos(v) * 1.45, -1.5, math.sin(v) * 1.45])],
                                      [0.12, 0.12], None, sides=3))


def fk_c(m, R):
    """C 月の満ち欠けの縁取り: 衣の裾と袖口に、満ち欠けの月の円盤が一周並び、胸の珠を細い枠が抱く"""
    lo, hi, c = R.box("robe")
    for k in range(16):
        u = k / 16 * math.tau
        p = V3([c[0] + math.cos(u) * 16.5, lo[1] + 3.0, c[2] + math.sin(u) * 15.0])
        out = V3([math.cos(u), 0, math.sin(u)])
        m.add(f"ph{k}", b.ell("robe", "silverm", p, (1.4, 1.4, 1.4), segs=10, rings=4))
        ph = (k % 8) / 8 * math.tau
        m.add(f"phs{k}", b.ell("robe", "indigo", p + out * 0.5 + V3([0, 0, 0]) + nrm(np.cross(out, (0, 1, 0))) * math.cos(ph) * 0.9, (1.15, 1.2, 1.15), segs=8, rings=3))
    for arm in ("fore_l", "fore_r"):
        alo, ahi, ac = R.box(arm)
        m.add(f"cuff{arm}", ring(arm, "silverm", V3([ac[0], alo[1] + 1.5, ac[2]]), 4.0, (0, 1, 0), 0.4, n=16))
        for k in range(6):
            u = k / 6 * math.tau
            m.add(f"cm{arm}{k}", b.ell(arm, "glow", V3([ac[0] + math.cos(u) * 4.0, alo[1] + 1.5, ac[2] + math.sin(u) * 4.0]), (0.35, 0.35, 0.35), segs=6, rings=2))
    olo, ohi, oc = R.box("orb")
    for i, nv in enumerate(((1, 0, 0), (0, 0, 1))):
        m.add(f"cage{i}", ring("orb", "silverm", oc, 7.4, nv, 0.25, n=24))


# ---------------------------------------------------------------- 残月の剣守 (剣士・頭の三日月・外套・剣)
def wb_a(m, R):
    """A 鎧の作り込み: 肩に重なる三枚の板、籠手の指、腰の帯と鞘、胸当ての鋲"""
    for s, arm in ((1, "arm_r"), (-1, "arm_l")):
        lo, hi, c = R.box(arm)
        for k in range(3):
            y = hi[1] - 0.5 - k * 1.6
            m.add(f"pa{arm}{k}", b.box(arm, "silverm" if k % 2 == 0 else "indigo", V3([c[0] + s * 0.6, y, c[2]]), (6.4 - k * 0.8, 1.2, 5.6 - k * 0.6),
                                       R=b.R_(0, 0, -s * (14 + k * 6))))
    for s, fore in ((1, "fore_r"), (-1, "fore_l")):
        lo, hi, c = R.box(fore)
        for j in range(4):
            x0 = c[0] + (j - 1.5) * 0.7
            m.add(f"fg{fore}{j}", b.tub(fore, "silverm", [V3([x0, lo[1] + 0.5, c[2] - 0.8]), V3([x0, lo[1] - 1.2, c[2] - 1.4]), V3([x0, lo[1] - 2.2, c[2] - 0.8])],
                                        [0.28, 0.24, 0.16], None, sides=4))
        m.add(f"gc{fore}", ring(fore, "silverm", V3([c[0], lo[1] + 3, c[2]]), 2.6, (0, 1, 0), 0.35, n=12))
    blo, bhi, bc = R.box("body")
    m.add("belt", ring("body", "indigo", V3([bc[0], bhi[1] - 2, bc[2]]), 6.6, (0, 1, 0), 0.6, n=20))
    m.add("sheath", b.tub("body", "indigo", [V3([-7, bhi[1] - 2, bc[2]]), V3([-9, bhi[1] - 10, bc[2] + 3]), V3([-10, bhi[1] - 18, bc[2] + 5])],
                          [0.9, 0.85, 0.6], None, sides=6))
    m.add("sheath_t", ring("body", "silverm", V3([-7.3, bhi[1] - 3.2, bc[2] + 0.4]), 1.0, (0.2, 1, -0.3), 0.25, n=10))
    clo, chi, cc = R.box("chest")
    for k in range(8):
        u = k / 8 * math.tau
        m.add(f"rv{k}", b.ell("chest", "silverm", V3([cc[0] + math.cos(u) * 4.5, cc[1] + math.sin(u) * 4.5, clo[2] - 0.2]), (0.35, 0.35, 0.3), segs=5, rings=2))


def wb_b(m, R):
    """B 背の三日月の刃: 背から七枚の三日月の刃が扇に開き、刃の縁が光る"""
    clo, chi, cc = R.box("chest")
    base = V3([cc[0], chi[1] - 3, chi[2] + 1.0])
    for k in range(7):
        u = math.radians(-75 + k * 25)
        d = nrm(V3([math.sin(u), math.cos(u) * 0.9 + 0.2, 0.35]))
        side = nrm(np.cross(d, (0, 0, 1)))
        L = 10 + (3 - abs(k - 3)) * 2.5
        pts_o = [base + d * L * t + side * math.sin(t * math.pi) * L * 0.22 for t in np.linspace(0.15, 1, 7)]
        pts_i = [base + d * L * t + side * math.sin(t * math.pi) * L * 0.08 for t in np.linspace(0.15, 1, 7)]
        poly = pts_o + pts_i[::-1]
        for j in range(6):
            quad = [pts_o[j], pts_o[j + 1], pts_i[j + 1], pts_i[j]]
            m.add(f"bl{k}{j}", b.slab("chest", "silverm", quad, (0, 0, 1), 0.35))
        m.add(f"be{k}", b.tub("chest", "glow", pts_o, [0.14] * 7, None, sides=3))
    m.add("hub", b.ell("chest", "indigo", base, (2.2, 2.2, 1.2), segs=10, rings=3))


def wb_c(m, R):
    """C 裂けた外套と鎖: 外套の裾が裂けて細い帯に垂れ、左腕に鎖が巻き、鞘に房"""
    lo, hi, c = R.box("cape")
    rng = np.random.default_rng(41)
    for k, e in enumerate(edge_points(R, "cape", 11)):
        L = rng.uniform(4, 9)
        top = e + V3([0, 1.2, 0])
        m.add(f"rag{k}", b.slab("cape", "indigo", [top + V3([-0.8, 0, 0]), top + V3([0.8, 0, 0]), top + V3([0.3, -L, 0.6]), top + V3([-0.4, -L * 0.9, 0.6])],
                                (0, 0, 1), 0.25))
    lo, hi, c = R.box("fore_l")
    pts = [V3([c[0] + math.cos(t) * 2.0, hi[1] - 1 - t * 0.9, c[2] + math.sin(t) * 2.0]) for t in np.linspace(0, 3 * math.tau, 34)]
    for k in range(len(pts) - 1):
        if k % 2 == 0:
            mid = (pts[k] + pts[k + 1]) / 2
            m.add(f"ch{k}", ring("fore_l", "silverm", mid, 0.45, pts[k + 1] - pts[k] + V3([0, 0.01, 0]), 0.12, n=8))
    chain(m, "chd", "fore_l", "silverm", pts[-1], pts[-1] + V3([0, -6, 1]), link=0.9, th=0.12)
    slo, shi, sc = R.box("sword")
    p = V3([sc[0], shi[1] - 0.5, slo[2] + 2])
    for k in range(5):
        u = k / 5 * math.tau
        m.add(f"ts{k}", b.tub("sword", "cloth_m", [p, p + V3([math.cos(u) * 0.6, -3.5, math.sin(u) * 0.6])], [0.15, 0.06], None, sides=3))


# ---------------------------------------------------------------- 影抱き (影の母・四本の腕・珠)
def se_a(m, R):
    """A しがみつく子ら: 衣の裾に、頭巾をかぶった小さな影の子が五つしがみつき、白い目だけ光る"""
    lo, hi, c = R.box("body")
    for k in range(5):
        u = math.radians(-70 + k * 35)
        base = V3([c[0] + math.sin(u) * 13, lo[1] + 6 + (k % 2) * 4, c[2] - math.cos(u) * 13])
        out = nrm(V3([math.sin(u), 0, -math.cos(u)]))
        side = nrm(np.cross(out, (0, 1, 0)))
        m.add(f"cb{k}", b.ell("body", "indigo", base + out * 1.2, (1.6, 2.6, 1.4), segs=8, rings=4))
        m.add(f"ch{k}", b.ell("body", "indigo", base + out * 1.4 + V3([0, 2.8, 0]), (1.3, 1.4, 1.3), segs=8, rings=4))
        m.add(f"chp{k}", b.cone("body", "indigo", base + out * 1.2 + V3([0, 3.8, 0]), (0, 1, 0.2), 1.6, 0.8, sides=5, rings=2, bend=(0, -0.4, 0.6)))
        for s in (1, -1):
            m.add(f"ce{k}{s}", b.ell("body", "glow", base + out * 2.6 + V3([0, 2.9, 0]) + side * s * 0.5, (0.22, 0.3, 0.12), segs=5, rings=2))
            m.add(f"ca{k}{s}", b.tub("body", "indigo", [base + out * 1.0 + side * s * 1.0 + V3([0, 1.4, 0]), base + out * 0.2 + side * s * 2.0 + V3([0, 2.4, 0])],
                                     [0.4, 0.3], None, sides=4))


def se_b(m, R):
    """B 長い指と爪: 四本の腕の先に関節の多い長い指と黒い爪、手の甲に骨の筋"""
    for arm in ("arm_l", "arm_r", "arm_l2", "arm_r2"):
        V = np.concatenate([f.verts for f in R.faces if f.bone == arm])
        tip = V[np.argmin(V[:, 2])]
        s = 1 if tip[0] > 0 else -1
        for j in range(4):
            off = (j - 1.5) * 0.8
            p0 = tip + V3([off, 0, 0.5])
            p1 = p0 + V3([off * 0.3 + s * 0.3, -1.6, -2.4])
            p2 = p1 + V3([off * 0.2, -2.0, -1.2])
            m.add(f"fn{arm}{j}", b.tub(arm, "indigo", [p0, p1, p2], [0.35, 0.28, 0.2], None, sides=5))
            m.add(f"fk{arm}{j}", b.ell(arm, "indigo", p1, (0.4, 0.4, 0.4), segs=6, rings=2))
            m.add(f"cl{arm}{j}", b.cone(arm, "silverm", p2, (0, -0.7, -0.5), 1.8, 0.22, sides=4, rings=2, bend=(0, -0.4, 0.2)))


def se_c(m, R):
    """C 目と影の糸: 衣のあちこちに細い目が開き、四本の腕から黒い影の糸が床へ垂れる"""
    lo, hi, c = R.box("body")
    rng = np.random.default_rng(51)
    for k in range(14):
        u = rng.uniform(-1.4, 1.4)
        y = rng.uniform(lo[1] + 6, hi[1] - 12)
        rad = 9 + (y - lo[1]) / (hi[1] - lo[1]) * -4 + 4
        p = V3([c[0] + math.sin(u) * rad, y, c[2] - math.cos(u) * rad])
        out = nrm(V3([math.sin(u), 0, -math.cos(u)]))
        a1 = nrm(np.cross(out, (0, 1, 0)))
        poly = [p + a1 * 1.3, p + V3([0, 0.45, 0]), p - a1 * 1.3, p - V3([0, 0.45, 0])]
        m.add(f"ey{k}", b.slab("body", "silverm", poly, out, 0.2))
        m.add(f"ep{k}", b.ell("body", "glow", p + out * 0.15, (0.25, 0.35, 0.1), segs=5, rings=2))
    for arm in ("arm_l", "arm_r", "arm_l2", "arm_r2"):
        V = np.concatenate([f.verts for f in R.faces if f.bone == arm])
        low = V[np.argmin(V[:, 1])]
        for j in range(3):
            p = low + V3([(j - 1) * 0.8, 0, 0])
            L = 10 + j * 4
            m.add(f"th{arm}{j}", b.tub(arm, "indigo", [p, p + V3([0.3, -L * 0.5, 0.4]), p + V3([0.6, -L, 0.2])], [0.18, 0.12, 0.04], None, sides=3))


BOSSES = {
    "moon_core": ("月の核", [("A 目盛りと小さな月", mc_a), ("B 割れた月の殻", mc_b), ("C 目の神経", mc_c)]),
    "moon_scale": ("月秤", [("A 錘と針", ms_a), ("B 月の岩の巨人", ms_b), ("C 天秤の祭司", ms_c)]),
    "full_keeper": ("望の灯守", [("A 灯台の冠", fk_a), ("B 裾の灯籠", fk_b), ("C 満ち欠けの縁取り", fk_c)]),
    "waning_blade": ("残月の剣守", [("A 鎧の作り込み", wb_a), ("B 背の三日月の刃", wb_b), ("C 裂けた外套と鎖", wb_c)]),
    "shadow_embracer": ("影抱き", [("A しがみつく子ら", se_a), ("B 長い指と爪", se_b), ("C 目と影の糸", se_c)]),
}


def main():
    fb.OUT.mkdir(parents=True, exist_ok=True)
    names = [a for a in sys.argv[1:] if not a.startswith("--")] or list(BOSSES)
    for name in names:
        faces, img, glow = bs.load(name)
        title, opts = BOSSES[name]
        R = bs.Ref(faces)
        cols = [("今の姿", faces, img, glow, "", 0)]
        for label, fn in opts:
            af, aa, ag = fb.parts(fn, R, "g_moon")
            mf, ma, mg = bs.merge(faces, img, glow, af, aa, ag)
            cols.append((label, mf, ma, mg, fn.__doc__.split(":")[1].strip()[:30], sum(len(f.tris()) for f in af)))
        fb.sheet(name, title, cols)
        print(name, "ok", flush=True)


if __name__ == "__main__":
    sys.stdout.reconfigure(encoding="utf-8")
    main()
