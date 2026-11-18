#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""技ごとの専用の形 (2026-11-12 の声「攻撃とバフ・デバフでモデルの見た目変えた方がいい。スキルの種類によっても変えないと。使い回しは良くない」。
見分け方は案 A「形で分ける」に決まった: rough_skill_kinds.py / docs/review/skill_props/kinds_a.png)。rpg/skill/SkillProps.place が使う。

    python -X utf8 -I tools/gen/mobs/attack_props/build_skill_each.py [種類 ...] [--no-install]

  攻撃   外へ尖る刃。刃先が白く光る (edgew)。材は職の材 (戦士 = 花崗岩、暗殺者 = 風、呪術師 = 潮、鍛冶師 = 赤熱の鉄、弓使い = 骨、魔法使い = 星、淵 = 淵、前世 = 光)
  強化   丸い形が自分を包み、上へ昇る輪 (金と青緑)。尖りがない
  弱体   枷と鎖で締める。紫と黒、下へ滴る雫
  移動   通った道に残る、線だけの残像 (前 = -Z へ並ぶ。動き出す所に置く)
  名前   prop_ss_<技>。飛ぶものは ss_<技>_fly (芯 y=4、前 = -Z)、降るものは ss_<技>_fall (先が下、y=0 が床に着く所)
  大きさ 範囲のものは半径 16 = 1 ブロック (大きさ = 半径)。ほかは 1 で人ほどの大きさ
絵は docs/review/skill_props/each_<組>.png
"""
from __future__ import annotations

import math
import sys
from pathlib import Path

import numpy as np

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
import rough_skill_kinds as RSK  # noqa: E402
from rough_skill_kinds import *  # noqa: E402,F401,F403

RSK.dummy = lambda *a, **k: None  # 立ち位置の人形は絵の見本だけ


def U(v):
    v = np.asarray(v, float)
    return v / max(1e-9, float(np.linalg.norm(v)))


# ---------------------------------------------------------------- 文法の部品
def tipblade(m, nm, base, out, h, mat, w=2.6, lean=0.5, tipc="edgew"):
    """攻撃の刃: base から out へ傾いて立つ平たい刃、刃先が白"""
    out = U(out)
    side = P(-out[2], 0, out[0])
    mid = base + out * h * lean * 0.4 + P(0, h * 0.5, 0)
    top = base + out * h * lean + P(0, h, 0)
    m.add(nm, tub("body", mat, [base + P(0, -4, 0), mid, top], [w, w * 0.7, 0.3], [w * 0.4, w * 0.32, 0.15], hint=tuple(side), sides=4, phase=45))
    d = U(out * lean + P(0, 1, 0))
    m.add(nm + "_t", cone("body", tipc, tuple(top - d * h * 0.28), tuple(d), h * 0.36, w * 0.45, sides=4))


def blade_ring(m, nm, n, r, h, mat, lean=0.6, twist=0.0, w=2.4, phase=0.0):
    for k in range(n):
        a = phase + k / n * math.tau
        out = P(math.cos(a), 0, math.sin(a))
        tang = P(-math.sin(a), 0, math.cos(a))
        tipblade(m, f"{nm}{k}", out * r, out + tang * twist, h, mat, w=w, lean=lean)


def halo_stack(m, nm, r0, y0, n=3, dy=5.0, shrink=0.72, w=0.5):
    """強化の昇る輪 (金と青緑を交互に)"""
    for k in range(n):
        loop(m, f"{nm}{k}", "goldl" if k % 2 == 0 else "sigil", r0 * shrink ** k, y0 + k * dy, w)


def rising_orbs(m, nm, n, r, y0, dy, mat="foam", s=1.1, turn=1.2):
    for k in range(n):
        a = k * turn
        m.add(f"{nm}{k}", ell("body", mat, (math.cos(a) * r, y0 + k * dy, math.sin(a) * r), (s, s, s), segs=8, rings=3))


def shackle(m, nm, r, y=2.0):
    loop(m, nm, "chain", r, y, 1.3)
    loop(m, nm + "_g", "violet", r, y + 1.5, 0.45)


def drips(m, nm, n, r, y, ln=4.0):
    for k in range(n):
        a = k / n * math.tau
        m.add(f"{nm}{k}", cone("body", "violet", (math.cos(a) * r, y, math.sin(a) * r), (0, -1, 0), ln + (k % 3) * 1.6, 0.7, sides=4))


def figure_outline(s, z):
    return [P(-2.6 * s, 0, z), P(-3.0 * s, 12 * s, z), P(-3.8 * s, 21 * s, z), P(-2.0 * s, 23 * s, z), P(2.0 * s, 23 * s, z), P(3.8 * s, 21 * s, z),
            P(3.0 * s, 12 * s, z), P(2.6 * s, 0, z), P(-2.6 * s, 0, z)]


def ghost_trail(m, nm, mat, n=3, z0=-14.0, step=-22.0, shape="figure", streak="wind"):
    """移動の残像: 前 (-Z) へ線だけの人影が並び、薄く小さくなる。最初の一つは職の材、あとは青緑"""
    for k in range(n):
        z = z0 + step * k
        s = 1.0 - k * 0.18
        if shape == "figure":
            m.add(f"{nm}{k}", tub("body", mat if k == 0 else "sigil", figure_outline(s, z), [0.5] * 9, None, hint=(0, 0, 1), sides=4, caps=(False, False)))
            m.add(f"{nm}{k}_h", tub("body", mat if k == 0 else "sigil", ring_pts(3.0 * s, 26.4 * s, 12, tilt=math.pi / 2), [0.45] * 13, None, hint=(0, 0, 1),
                                    sides=4, caps=(False, False)))
        else:  # shield
            pts = [P(math.cos(t / 16 * math.tau) * 7 * s, 11 * s + math.sin(t / 16 * math.tau) * 10 * s, z) for t in range(17)]
            m.add(f"{nm}{k}", tub("body", mat if k == 0 else "sigil", pts, [0.6] * 17, None, hint=(0, 0, 1), sides=4, caps=(False, False)))
    for k, (x, y) in enumerate(((-6, 5), (6, 9), (-3, 17), (4, 21))):
        m.add(f"{nm}_st{k}", tub("body", streak, [P(x, y, z0 + 6), P(x, y, z0 + step * (n - 1) * 0.6), P(x * 0.6, y, z0 + step * (n - 0.6))],
                                 [0.7, 0.45, 0.1], None, hint=(0, 1, 0), sides=4))


def crack_star(m, nm, n, r, mat="sigil", y=1.4):
    for k in range(n):
        a = k / n * math.tau + 0.4
        m.add(f"{nm}{k}", tub("body", mat, [P(math.cos(a) * 2, y, math.sin(a) * 2), P(math.cos(a + 0.2) * r * 0.55, y, math.sin(a + 0.2) * r * 0.55),
                                            P(math.cos(a) * r, y, math.sin(a) * r)], [0.5, 0.45, 0.2], None, hint=(0, 1, 0), sides=4))


# ================================================================ 戦士 (花崗岩)
def ss_titan_cleave(m):
    # 攻撃: 前の扇 (半径 1) に、前へ倒れた花崗岩の刃が五枚
    for k, a in enumerate((-50, -25, 0, 25, 50)):
        r = math.radians(a)
        f = P(math.sin(r), 0, -math.cos(r))
        tipblade(m, f"blade{k}", f * (9 + abs(a) * 0.04), f, 18 - abs(a) * 0.1, "granite", w=3.0, lean=0.4)
    base_debris(m, "granite", 10, seed=81)
    sig_loop(m, 5)


def ss_rock_call(m):
    # 弱体 (挑発): 外の輪の鉤から、紫の鎖が自分の足もとの枷へ引き寄せられる
    shackle(m, "shackle", 4, 2)
    for k in range(6):
        a = k / 6 * math.tau
        o = P(math.cos(a), 0, math.sin(a))
        post = o * 16
        m.add(f"post{k}", tub("body", "granite", [post + P(0, -4, 0), post + P(0, 8, 0)], [2.0, 1.6], None, sides=4, phase=45, caps=(True, True)))
        m.add(f"hook{k}", tub("body", "chain", [post + P(0, 8, 0), post + P(0, 11, 0) - o * 1.4, post + P(0, 10, 0) - o * 3.0], [0.8, 0.7, 0.4], None, sides=4))
        chain_line(m, f"ch{k}_", post + P(0, 9, 0) - o * 2.4, o * 4.6 + P(0, 3, 0), links=8)
        m.add(f"pg{k}", ell("body", "violet", tuple(post + P(0, 5, 0) - o * 1.9), (1.0, 1.4, 1.0), segs=4, rings=1))
    drips(m, "drip", 6, 4, 3.2, 2.4)


def ss_whale_sweep(m):
    # 範囲攻撃: 回る向きに寝た花崗岩のひれの輪 (半径 1)
    n = 9
    for k in range(n):
        a = k / n * math.tau
        out = P(math.cos(a), 0, math.sin(a))
        tang = P(-math.sin(a), 0, math.cos(a))
        b = out * 15
        pts = [b + P(0, -4, 0), b + out * 2 + tang * 3 + P(0, 5, 0), b + out * 4 + tang * 7 + P(0, 8, 0)]
        m.add(f"fin{k}", tub("body", "granite", pts, [2.6, 1.6, 0.3], [1.0, 0.8, 0.2], hint=(0, 1, 0), sides=4, phase=45))
        m.add(f"tip{k}", cone("body", "edgew", tuple(b + out * 3.4 + tang * 5.6 + P(0, 7, 0)), tuple(out * 0.6 + tang), 3.6, 0.8, sides=4))
    sig_loop(m, 15, 0.4)
    base_debris(m, "granite", 13, seed=82)


def ss_shield_rush(m):
    # 移動 (踏み込み): 前へ並ぶ盾の輪郭の残像、先に白い刃の縁を持つ花崗岩の盾
    ghost_trail(m, "g", "granite", n=3, z0=-10, step=-24, shape="shield", streak="granite")
    z = -10 - 24 * 2.6
    m.add("shield", ell("body", "granite", (0, 12, z), (8.0, 11.0, 1.6), segs=10, rings=3))
    loop_pts = [P(math.cos(t / 20 * math.tau) * 8.2, 12 + math.sin(t / 20 * math.tau) * 11.2, z - 0.8) for t in range(21)]
    m.add("rim", tub("body", "edgew", loop_pts, [0.5] * 21, None, hint=(0, 0, 1), sides=4, caps=(False, False)))
    m.add("boss", ell("body", "sigil", (0, 12, z - 1.6), (1.6, 1.6, 0.8), segs=6, rings=2))


def ss_shield_bash(m):
    # 攻撃: 前に突き出る大盾の面、縁から外へ白い棘、足もとに割れ
    z = -14
    m.add("face", ell("body", "granite", (0, 12, z), (10.0, 12.0, 2.0), segs=12, rings=3))
    for k in range(8):
        a = k / 8 * math.tau
        o = P(math.cos(a), math.sin(a), 0)
        b = P(0, 12, z - 0.5) + o * P(10, 12, 0)
        m.add(f"sp{k}", cone("body", "edgew", tuple(b), tuple(U(o + P(0, 0, -0.8))), 5.0, 1.2, sides=4))
    m.add("boss", ell("body", "sigil", (0, 12, z - 2.2), (2.0, 2.0, 1.0), segs=6, rings=2))
    crack_star(m, "cr", 5, 12, "sigil")
    base_debris(m, "granite", 8, n=5, seed=83)


def ss_bone_swing(m):
    # 攻撃: 振り下ろす鯨骨の大棍 (椎骨を連ねた弧)、外縁が白い刃
    pts = arc_pts(16, -60, 60, 9, y=10)
    for k, p in enumerate(pts):
        s = 2.2 + 1.6 * math.sin(math.pi * k / 8)
        m.add(f"vert{k}", ell("body", "bone", tuple(p), (s, s * 1.2, s), segs=6, rings=2, ry=k * 15))
        m.add(f"spine{k}", cone("body", "bone", tuple(p + P(0, s, 0)), (0, 1, 0), s * 1.4, s * 0.5, sides=4))
    crescent(m, "edge", "edgew", 19.5, 110, 0.7, n=11, y=10)
    m.add("head", ell("body", "bone", tuple(pts[-1] + P(0, 0, 0)), (4.4, 5.0, 4.4), segs=8, rings=3))
    sig_loop(m, 5)


def ss_spin_cut(m):
    # 範囲攻撃 (前へ進みながら): 昇る渦巻きの刃 (三段)
    for row in range(3):
        n = 5
        for k in range(n):
            a = k / n * math.tau + row * 0.6
            r = 15 - row * 3
            out = P(math.cos(a), 0, math.sin(a))
            tang = P(-math.sin(a), 0, math.cos(a))
            b = out * r + P(0, row * 7, 0)
            pts = [b, b + tang * 4 + out * 1.4 + P(0, 2, 0), b + tang * 8 + out * 2.6 + P(0, 3, 0)]
            m.add(f"bl{row}_{k}", tub("body", "granite", pts, [2.2, 1.4, 0.3], [0.8, 0.6, 0.15], hint=(0, 1, 0), sides=4, phase=45))
            m.add(f"tp{row}_{k}", cone("body", "edgew", tuple(pts[2] - tang * 1.6), tuple(tang), 3.0, 0.7, sides=4))
    sig_loop(m, 14, 0.4)


# ================================================================ 暗殺者 (風)
def ss_lunge(m):
    # 移動して突く: 前へ並ぶ人影の残像、その先に白い切っ先の風の槍
    ghost_trail(m, "g", "windp", n=3, z0=-10, step=-22)
    z = -10 - 22 * 2.5
    m.add("spear", tub("body", "windp", [P(0, 14, z + 8), P(0, 14, z - 6)], [1.6, 0.8], [3.4, 1.4], hint=(0, 1, 0), sides=4, phase=45))
    m.add("point", cone("body", "edgew", (0, 14, z - 5), (0, 0, -1), 8, 1.4, sides=4))
    for k in range(3):
        loop_pts = [P(math.cos(t / 12 * math.tau) * (3 + k), 14 + math.sin(t / 12 * math.tau) * (3 + k), z + 6 + k * 5) for t in range(13)]
        m.add(f"ring{k}", tub("body", "wind", loop_pts, [0.4] * 13, None, hint=(0, 0, 1), sides=4, caps=(False, False)))


def ss_backstep(m):
    # 移動 (後ろへ跳ぶ): 低くかがんだ残像が二つと、置いていく羽根のような風
    for k in range(2):
        z = -12 - 20 * k
        s = 0.8 - 0.15 * k
        m.add(f"g{k}", tub("body", "windp" if k == 0 else "sigil", figure_outline(s, z), [0.5] * 9, None, hint=(0, 0, 1), sides=4, caps=(False, False)))
    for k in range(5):
        a = -0.8 + k * 0.4
        b = P(math.sin(a) * 8, 2 + k, 4)
        m.add(f"feather{k}", ell("body", "wind", tuple(b), (1.0, 0.5, 4.4), segs=6, rings=2, ry=math.degrees(a)))
    m.add("st", tub("body", "wind", [P(0, 6, 0), P(0, 8, -18), P(0, 12, -40)], [0.8, 0.5, 0.1], None, hint=(1, 0, 0), sides=4))
    sig_loop(m, 6)


def ss_wind_rend_fly(m):
    # 攻撃 (飛ぶ): X に交わる二枚の風の刃、白い刃先
    for s in (1, -1):
        pts = []
        for k in range(11):
            t = k / 10
            a = math.radians(-60 + 120 * t)
            pts.append(P(math.sin(a) * 12, CY + s * math.sin(a) * 9, -math.cos(a) * 12 + 6))
        rad = [1.6 * (0.25 + 0.75 * math.sin(math.pi * k / 10)) for k in range(11)]
        m.add(f"blade{s}", tub("body", "windp", pts, rad, None, hint=(0, 0, 1), sides=4, phase=45))
        m.add(f"edge{s}", tub("body", "edgew", [p + P(0, 0, -1.4) for p in pts[2:9]], [0.45] * 7, None, hint=(0, 0, 1), sides=4))
    m.add("core", ell("body", "sigil", (0, CY, -6), (1.4, 1.4, 1.4), segs=6, rings=2))


def ss_mist_veil(m):
    # 強化 (姿を消す): 自分を包む霧の丸い塊が昇り、金と青緑の輪
    # 霧の帯が足もとの輪から頭の上の一点へ、玉ねぎの形に閉じて包む
    for k in range(8):
        a = k / 8 * math.tau
        pts = []
        for j in range(9):
            t = j / 8
            r = 13 * math.sin(math.pi * (0.12 + 0.88 * t)) if t < 1 else 0.6
            aa = a + t * 0.9
            pts.append(P(math.cos(aa) * r, t * 34, math.sin(aa) * r))
        m.add(f"veil{k}", tub("body", "windp" if k % 2 == 0 else "wind", pts, [1.0, 1.2, 1.2, 1.1, 1.0, 0.8, 0.6, 0.4, 0.3], None, sides=4))
    m.add("knot", ell("body", "goldl", (0, 34.6, 0), (1.6, 1.6, 1.6), segs=8, rings=3))
    loop(m, "halo", "goldl", 4, 38, 0.45)
    sig_loop(m, 13, 0.4)


def ss_twin_fang(m):
    # 攻撃: 敵を挟んで突き上がる二本の長い牙 (白い切っ先)
    for s in (1, -1):
        b = P(s * 6, -4, 0)
        pts = [b, b + P(-s * 1.4, 12, -2), b + P(-s * 4.4, 22, -3)]
        m.add(f"fang{s}", tub("body", "windp", pts, [2.6, 1.8, 0.4], [1.2, 0.9, 0.2], hint=(0, 0, 1), sides=4, phase=45))
        m.add(f"tip{s}", cone("body", "edgew", tuple(pts[2] + P(s * 1.0, -2.6, 0.2)), tuple(U(P(-s * 0.4, 1, -0.1))), 5, 1.0, sides=4))
    crack_star(m, "cr", 4, 9, "sigil")


# ================================================================ 呪術師 (潮)
def ss_ward_song(m):
    RSK.a_ward(m)


def ss_ebb_tide(m):
    # 弱体 (引き寄せ・鈍足): 前の弧から水の鉤爪が内へ曲がり、紫の枷に締まる
    for k, a in enumerate((-45, -15, 15, 45)):
        r = math.radians(a)
        f = P(math.sin(r), 0, -math.cos(r))
        b = f * 16
        pts = [b + P(0, -3, 0), b + P(0, 7, 0) + f * 1.0, b + P(0, 11, 0) - f * 3.4, b + P(0, 8, 0) - f * 6.6]
        m.add(f"claw{k}", tub("body", "seaw", pts, [2.4, 1.8, 1.2, 0.3], None, sides=5))
        m.add(f"cuff{k}", tub("body", "chain", [q + b + P(0, 3, 0) for q in ring_pts(2.8, 0, 10)], [0.6] * 11, None, hint=(0, 1, 0), sides=4, caps=(False, False)))
        m.add(f"drop{k}", cone("body", "violet", tuple(pts[3]), (0, -1, 0), 4, 0.7, sides=4))
    shackle(m, "shackle", 5, 1.4)
    for k in range(3):
        loop(m, f"rip{k}", "foam", 8 + k * 4, 0.8, 0.35)


def ss_cycle_song(m):
    # 強化 (マナの巡り): 足もとから螺旋に昇る水の帯と、金と青緑の輪、昇る泡
    for k in range(2):
        pts, rad = [], []
        for j in range(12):
            t = j / 11
            a = k * math.pi + t * 5.0
            pts.append(P(math.cos(a) * (13 - t * 4), 1 + t * 30, math.sin(a) * (13 - t * 4)))
            rad.append(1.3 * (1 - t) + 0.4)
        m.add(f"stream{k}", tub("body", "seaw", pts, rad, None, sides=5))
    loop(m, "halo", "goldl", 5, 33, 0.5)
    rising_orbs(m, "bub", 7, 6, 4, 4.2)
    sig_loop(m, 13, 0.4)


def ss_curse_mark(m):
    RSK.a_curse(m)


def ss_lamp_charm(m):
    # 強化 (置く灯): 彫り柱に吊った丸い灯、まわりへ昇る金の輪と灯の玉
    m.add("post", tub("body", "totem", [P(0, -4, 0), P(0, 22, 0)], [1.8, 1.4], None, sides=6, caps=(True, True)))
    m.add("arm", tub("body", "totem", [P(0, 21, 0), P(0, 24, -4), P(0, 23, -7)], [1.0, 0.9, 0.7], None, sides=5))
    m.add("lamp", ell("body", "lantern", (0, 18, -7), (3.2, 3.8, 3.2), segs=10, rings=4))
    m.add("lamp_cap", ell("body", "totem", (0, 22, -7), (2.6, 1.0, 2.6), segs=8, rings=1))
    loop(m, "ground", "goldl", 14, 0.8, 0.5)
    rising_orbs(m, "mote", 8, 10, 3, 3.4, mat="lantern", s=0.9, turn=0.8)
    sig_loop(m, 15, 0.4)


# ================================================================ 鍛冶師 (赤熱の鉄)
def ss_temper(m):
    # 強化 (武器を熾す): 自分のまわりを昇る熾の輪と、丸い火の玉、金の輪
    # 金の子午線の籠 (球) の中を、熾の玉が螺旋に昇る
    for k in range(4):
        a = k / 4 * math.pi
        pts = [P(math.cos(a) * math.sin(t / 16 * math.pi) * 12, 16 - math.cos(t / 16 * math.pi) * 14, math.sin(a) * math.sin(t / 16 * math.pi) * 12) for t in range(17)]
        m.add(f"mer{k}", tub("body", "goldl", pts, [0.5] * 17, None, sides=4))
    loop(m, "equator", "sigil", 12, 16, 0.5)
    rising_orbs(m, "spark", 9, 6, 4, 2.8, mat="ember", s=1.3, turn=0.9)
    m.add("flame", ell("body", "ember", (0, 30.5, 0), (2.0, 2.8, 2.0), segs=8, rings=3))
    sig_loop(m, 13, 0.4)


def ss_ember_hammer(m):
    # 攻撃: 叩きつけた大槌の頭 (赤熱の縁)、まわりへ白い刃先の鉄の棘
    m.add("head", tub("body", "iron", [P(-9, 6, 0), P(9, 6, 0)], [5.6, 5.6], [5.0, 5.0], hint=(0, 1, 0), sides=4, phase=45, caps=(True, True)))
    for s in (1, -1):
        loop_pts = [P(s * 9.4, 6 + math.sin(t / 12 * math.tau) * 5.4, math.cos(t / 12 * math.tau) * 5.4) for t in range(13)]
        m.add(f"rim{s}", tub("body", "ironhot", loop_pts, [0.6] * 13, None, hint=(1, 0, 0), sides=4, caps=(False, False)))
    m.add("haft", tub("body", "wood", [P(0, 11, 0), P(0, 30, 4)], [1.4, 1.2], None, sides=6))
    for k in range(7):
        a = k / 7 * math.tau
        o = P(math.cos(a), 0, math.sin(a))
        tipblade(m, f"sp{k}", o * 13, o, 9, "ironhot", w=1.8, lean=0.8)
    crack_star(m, "cr", 6, 14, "ember")


def ss_ash_scatter(m):
    RSK.a_ash(m)


def ss_forge_stomp(m):
    # 範囲攻撃: 熔けた鉄の円の縁から、外へめくれ上がる厚い鉄の板 (白熱の縁)
    m.add("pool", ell("body", "magma", (0, 0.4, 0), (11, 0.8, 11), segs=14, rings=1))
    n = 9
    for k in range(n):
        a = k / n * math.tau
        o = P(math.cos(a), 0, math.sin(a))
        t = P(-math.sin(a), 0, math.cos(a))
        b = o * 13
        pts = [b + P(0, -3, 0), b + o * 2.4 + P(0, 4, 0), b + o * 5.4 + P(0, 8, 0)]
        m.add(f"slab{k}", tub("body", "iron", pts, [4.6, 4.0, 3.0], [1.4, 1.2, 0.8], hint=tuple(t), sides=4, phase=45))
        m.add(f"lip{k}", tub("body", "edgew", [pts[2] + t * 3.0 + o * 0.6, pts[2] - t * 3.0 + o * 0.6], [0.6, 0.6], None, hint=tuple(o), sides=4))
        m.add(f"drop{k}", ell("body", "ironhot", tuple(b + o * 1.4 + P(0, 1.2, 0)), (1.6, 1.0, 1.6), segs=6, rings=2))


def ss_spark_burst(m):
    # 範囲攻撃 (燃やす): 胸の高さで弾けて外へ飛ぶ鉄の破片、先が白熱
    m.add("core", ell("body", "ember", (0, 14, 0), (3.0, 3.0, 3.0), segs=8, rings=3))
    for k in range(14):
        a = k * 2.4
        e = (k % 3 - 1) * 0.35
        d = U(P(math.cos(a), e, math.sin(a)))
        b = P(0, 14, 0) + d * 5
        m.add(f"shard{k}", tub("body", "iron", [b, b + d * 6], [1.0, 0.6], None, sides=4, phase=45))
        m.add(f"tip{k}", cone("body", "edgew", tuple(b + d * 5.5), tuple(d), 3.6, 0.8, sides=4))
    loop(m, "ring", "ember", 12, 0.8, 0.5)
    sig_loop(m, 6)


# ================================================================ 弓使い (骨)
def ss_cloud_pierce_fly(m):
    # 攻撃 (飛ぶ): 大きな骨の矢、白い鏃、軸のまわりに風の輪
    m.add("shaft", tub("body", "bone", [P(0, CY, 16), P(0, CY, -10)], [1.0, 1.0], None, sides=6))
    m.add("head", cone("body", "edgew", (0, CY, -10), (0, 0, -1), 7, 2.2, sides=4))
    for s in (1, -1):
        m.add(f"fl{s}", tub("body", "sigil", [P(0, CY, 16), P(s * 3.4, CY, 19), P(s * 3.0, CY, 12)], [0.4, 0.5, 0.3], None, hint=(0, 1, 0), sides=4))
    for k in range(3):
        pts = [P(math.cos(t / 12 * math.tau) * (2.6 + k * 1.4), CY + math.sin(t / 12 * math.tau) * (2.6 + k * 1.4), -2 + k * 7) for t in range(13)]
        m.add(f"ring{k}", tub("body", "wind", pts, [0.4] * 13, None, hint=(0, 0, 1), sides=4, caps=(False, False)))


def ss_bone_trap(m):
    # 弱体 (縛る): 骨の顎の罠 (二枚の歯の列) と、床に打った杭へ伸びる鎖
    for s in (1, -1):
        pts = [P(math.cos(math.radians(t)) * 8, 1 + s * 0.6, math.sin(math.radians(t)) * 8 * s) for t in range(0, 181, 20)]
        m.add(f"jaw{s}", tub("body", "bone", pts, [1.4] * len(pts), None, hint=(0, 1, 0), sides=4))
        for k, p in enumerate(pts[1:-1]):
            m.add(f"tooth{s}{k}", cone("body", "bone", tuple(p), (0, 1, 0), 4.4, 0.9, sides=4))
    shackle(m, "ring", 9, 0.6)
    for k in range(3):
        a = k / 3 * math.tau + 0.5
        st = P(math.cos(a) * 15, 0, math.sin(a) * 15)
        m.add(f"stake{k}", tub("body", "chain", [st + P(0, -3, 0), st + P(0, 6, 0)], [1.2, 0.8], None, sides=4, phase=45, caps=(True, True)))
        chain_line(m, f"ch{k}_", st + P(0, 4, 0), P(math.cos(a) * 9, 2, math.sin(a) * 9), links=5)
    drips(m, "drip", 4, 6, 3, 2)


def ss_sky_rain(m):
    # 範囲攻撃 (場): 外へ傾いて刺さった骨の矢の輪 (白い矢羽)
    for k in range(12):
        a = k / 12 * math.tau
        o = P(math.cos(a), 0, math.sin(a))
        b = o * 14
        top = b + o * 3 + P(0, 10, 0)
        m.add(f"ar{k}", tub("body", "bone", [b + P(0, -3, 0), top], [0.7, 0.7], None, sides=5))
        m.add(f"fl{k}", cone("body", "edgew", tuple(top - U(top - b) * 2), tuple(U(top - b)), 3.6, 1.4, sides=4))
    loop(m, "ring", "sigil", 14, 0.6, 0.45)


def ss_sky_rain_fall(m):
    # 降る骨の矢 (先が下)
    m.add("shaft", tub("body", "bone", [P(0, 4, 0), P(0, 24, 0)], [0.8, 0.8], None, sides=5))
    m.add("head", cone("body", "edgew", (0, 5, 0), (0, -1, 0), 5, 1.6, sides=4))
    for s in (1, -1):
        m.add(f"fl{s}", tub("body", "sigil", [P(0, 24, 0), P(s * 2.6, 27, 0), P(s * 2.4, 21, 0)], [0.35, 0.45, 0.3], None, hint=(0, 0, 1), sides=4))


def ss_snare_shot_fly(m):
    # 弱体の矢 (飛ぶ): 紫の縄を引く骨の矢、鏃は輪縄
    m.add("shaft", tub("body", "bone", [P(0, CY, 12), P(0, CY, -8)], [0.8, 0.8], None, sides=6))
    m.add("head", cone("body", "violet", (0, CY, -8), (0, 0, -1), 5, 1.8, sides=4))
    m.add("rope", tub("body", "chain", [P(0, CY, 12), P(1.6, CY - 1, 18), P(-1.2, CY - 1.6, 24), P(0.6, CY - 2.4, 30)], [0.5] * 4, None, sides=4))
    m.add("noose", tub("body", "violet", ring_pts(2.4, CY, 12, tilt=math.pi / 2), [0.4] * 13, None, hint=(0, 0, 1), sides=4, caps=(False, False)))


def ss_snare_shot(m):
    # 弱体 (当たり): 敵の足に紫の縄の枷、床の三本の杭へ締める
    shackle(m, "noose", 7, 2)
    for k in range(3):
        a = k / 3 * math.tau
        st = P(math.cos(a) * 14, 0, math.sin(a) * 14)
        m.add(f"stake{k}", tub("body", "bone", [st + P(0, -3, 0), st + P(0, 5, 0)], [1.1, 0.7], None, sides=4, phase=45, caps=(True, True)))
        chain_line(m, f"ch{k}_", st + P(0, 4, 0), P(math.cos(a) * 7, 3, math.sin(a) * 7), links=5)
    m.add("arrow", tub("body", "bone", [P(1, 0, 0), P(4, 14, 3)], [0.6, 0.6], None, sides=5))
    drips(m, "drip", 5, 7, 3.4, 2.4)


# ================================================================ 魔法使い (星)
def ss_thunder_bolt_fly(m):
    # 攻撃 (飛ぶ): ぎざぎざの稲妻の槍、白い芯
    pts = [P(0, CY, 14), P(1.6, CY + 1.2, 8), P(-1.4, CY - 0.6, 3), P(1.2, CY + 0.8, -3), P(0, CY, -9)]
    m.add("bolt", tub("body", "bolt", pts, [1.4, 1.6, 1.6, 1.4, 0.6], None, sides=4, phase=45))
    m.add("core", tub("body", "edgew", pts[1:], [0.5, 0.5, 0.5, 0.2], None, sides=4))
    m.add("tip", cone("body", "edgew", (0, CY, -8), (0, 0, -1), 5, 1.2, sides=4))


def ss_thunder_bolt(m):
    # 攻撃 (当たり): 天から床へ折れ曲がって落ちた雷の柱と、床で割れて跳ねる星の晶
    pts = [P(0, 40, 0), P(3, 30, 1), P(-2.4, 21, -1), P(2.2, 11, 0.6), P(0, 0, 0)]
    m.add("bolt", tub("body", "bolt", pts, [2.0, 2.2, 2.2, 2.0, 1.4], None, sides=4, phase=45))
    m.add("core", tub("body", "edgew", pts, [0.7] * 5, None, sides=4))
    for k in range(5):
        a = k / 5 * math.tau + 0.3
        o = P(math.cos(a), 0, math.sin(a))
        m.add(f"sh{k}", cone("body", "star", tuple(o * 4), tuple(U(o * 1.4 + P(0, 1, 0))), 6, 1.4, sides=4))
    crack_star(m, "cr", 5, 12, "bolt")


def ss_ember_orb_fly(m):
    # 攻撃 (飛ぶ): 熾の玉と、まわりを回る二つの輪、後ろへ引く尾
    m.add("orb", ell("body", "ember", (0, CY, 0), (4.0, 4.0, 4.0), segs=10, rings=5))
    m.add("core", ell("body", "edgew", (0, CY, -1.6), (1.8, 1.8, 1.8), segs=6, rings=2))
    for k, t in enumerate((0.5, -0.5)):
        m.add(f"ring{k}", tub("body", "star", ring_pts(6.0, CY, 18, tilt=t), [0.4] * 19, None, hint=(0, 1, 0), sides=4, caps=(False, False)))
    m.add("tail", tub("body", "ember", [P(0, CY, 3), P(0, CY + 0.6, 10), P(0, CY + 1.2, 16)], [3.0, 1.6, 0.2], None, sides=6))


def ss_ember_orb(m):
    # 攻撃 (弾ける): 外へ咲く熾の刃の花
    blade_ring(m, "pet", 8, 6, 12, "ember", lean=1.0, w=2.2)
    blade_ring(m, "pet2", 8, 10, 7, "magma", lean=1.2, w=1.6, phase=0.4)
    m.add("core", ell("body", "edgew", (0, 3, 0), (3.0, 2.0, 3.0), segs=8, rings=2))


def ss_frost_tide(m):
    # 範囲攻撃 (鈍足つき): 凍った潮の床から、六か所に外へ倒れた六角の氷柱の束
    m.add("sheet", ell("body", "ice", (0, 0.4, 0), (16, 0.9, 16), segs=16, rings=1))
    for k in range(6):
        a = k / 6 * math.tau
        o = P(math.cos(a), 0, math.sin(a))
        t = P(-math.sin(a), 0, math.cos(a))
        for j, (dt, h, w) in enumerate(((0, 13, 2.4), (2.6, 8, 1.7), (-2.4, 9, 1.8))):
            b = o * 11 + t * dt
            d = U(o * 0.7 + P(0, 1, 0))
            m.add(f"col{k}{j}", tub("body", "ice", [b + P(0, -3, 0), b + d * h], [w, w], None, sides=6, caps=(True, False)))
            m.add(f"tip{k}{j}", cone("body", "edgew", tuple(b + d * h), tuple(d), w * 1.6, w, sides=6))
    loop(m, "rim", "foam", 15.5, 1.2, 0.4)


def ss_blink(m):
    # 移動 (転移): 前へ並ぶ星の残像と筋
    ghost_trail(m, "g", "star", n=3, z0=-12, step=-22, streak="star")
    sig_loop(m, 6)


def ss_stardust(m):
    # 範囲攻撃 (場): 床に開く五つ角の星 (平たい星の晶の板)、角の先が白
    for k in range(5):
        a = k / 5 * math.tau
        o = P(math.cos(a), 0, math.sin(a))
        t = P(-math.sin(a), 0, math.cos(a))
        m.add(f"arm{k}", tub("body", "star", [o * 2, o * 9, o * 14], [4.4, 2.6, 0.6], [1.0, 0.8, 0.4], hint=tuple(t), sides=4, phase=45))
        m.add(f"pt{k}", cone("body", "edgew", tuple(o * 13 + P(0, 0.4, 0)), tuple(U(o + P(0, 0.5, 0))), 4.0, 1.0, sides=4))
    m.add("core", ell("body", "edgew", (0, 1.4, 0), (2.6, 1.4, 2.6), segs=10, rings=2))
    loop(m, "ring", "sigil", 15, 0.6, 0.4)


def ss_stardust_fall(m):
    # 降る星 (先が下): 尖った星の晶と、上へ引く尾
    m.add("cry", cone("body", "star", (0, 6, 0), (0, -1, 0), 7, 2.4, sides=5))
    m.add("top", cone("body", "star", (0, 6, 0), (0, 1, 0), 3, 2.4, sides=5))
    m.add("tip", cone("body", "edgew", (0, 2.4, 0), (0, -1, 0), 3, 1.0, sides=4))
    m.add("tail", tub("body", "sigil", [P(0, 9, 0), P(0, 18, 0), P(0, 26, 0)], [1.4, 0.8, 0.1], None, sides=5))


# ================================================================ 淵・前世
def ss_deep_pull(m):
    # 攻撃 (寄せて打つ): 外の輪から内へ鉤のように曲がる淵の刃、刃先が白
    for k in range(8):
        a = k / 8 * math.tau
        o = P(math.cos(a), 0, math.sin(a))
        b = o * 16
        pts = [b + P(0, -4, 0), b + P(0, 8, 0), b - o * 4 + P(0, 12, 0)]
        m.add(f"hook{k}", tub("body", "deep", pts, [2.2, 1.6, 0.4], [1.0, 0.7, 0.2], hint=tuple(P(-o[2], 0, o[0])), sides=4, phase=45))
        m.add(f"tip{k}", cone("body", "edgew", tuple(b - o * 3 + P(0, 11.4, 0)), tuple(U(-o + P(0, -0.3, 0))), 3.4, 0.8, sides=4))
    m.add("eye", ell("body", "deep", (0, 1, 0), (5, 1.2, 5), segs=12, rings=1))
    sig_loop(m, 6, 1.4)


def ss_deep_echo(m):
    # 強化 (技を強める): 足もとから昇る淵の珠と、金と青緑の輪
    # 床に広がる淵の波紋 (三重) と、三本の柱になって昇る淵の珠、頭上に金の輪
    for k in range(3):
        loop(m, f"rip{k}", "deep", 6 + k * 4.5, 0.8, 0.55 - k * 0.1)
    for c in range(3):
        a = c / 3 * math.tau
        for j in range(4):
            m.add(f"orb{c}{j}", ell("body", "deep", (math.cos(a + j * 0.25) * 8, 6 + j * 7, math.sin(a + j * 0.25) * 8), (1.5 - j * 0.2,) * 3, segs=8, rings=3))
    loop(m, "halo", "goldl", 6, 34, 0.5)
    sig_loop(m, 13, 0.4)


def ss_deep_maw(m):
    # 範囲攻撃 (噛み砕く): 床から突き上がる大顎の上下の牙の列 (先が白)
    for s in (1, -1):
        pts = [P(math.cos(math.radians(t)) * 14, 0, s * math.sin(math.radians(t)) * 14) for t in range(0, 181, 15)]
        m.add(f"jaw{s}", tub("body", "deep", [p + P(0, 2 + abs(p[0]) * 0.1, 0) for p in pts], [2.4] * len(pts), None, hint=(0, 1, 0), sides=4))
        for k, p in enumerate(pts[1:-1]):
            o = U(P(-p[0], 0, -p[2]))
            tipblade(m, f"fang{s}{k}", p + P(0, 3, 0), o, 10 - abs(k - 5) * 0.8, "deep", w=1.6, lean=0.4)
    m.add("throat", ell("body", "night", (0, 0.4, 0), (11, 0.8, 11), segs=14, rings=1))


def ss_echo_strike(m):
    # 攻撃 (一撃と残響): 敵に突き立つ光の槍と、残響で外へ開く光の刃の輪
    m.add("spear", tub("body", "light", [P(0, 34, 0), P(0, 8, 0)], [1.6, 2.4], None, sides=6))
    m.add("tip", cone("body", "edgew", (0, 8, 0), (0, -1, 0), 10, 2.4, sides=4))
    blade_ring(m, "echo", 7, 12, 8, "goldl", lean=0.8, w=1.6)
    loop(m, "ring", "goldl", 12, 0.6, 0.45)


def ss_grave_bell_fall(m):
    # 攻撃 (降る鐘): 口が下の青銅の鐘、縁から外へ白い刃
    m.add("bell", tub("body", "bronze", [P(0, 24, 0), P(0, 14, 0), P(0, 2, 0)], [3.0, 7.0, 10.0], None, sides=12, caps=(True, False)))
    m.add("crown", ell("body", "bronze", (0, 25, 0), (3, 2, 3), segs=8, rings=2))
    for k in range(10):
        a = k / 10 * math.tau
        o = P(math.cos(a), 0, math.sin(a))
        m.add(f"sp{k}", cone("body", "edgew", tuple(o * 10 + P(0, 2, 0)), tuple(U(o + P(0, -0.4, 0))), 4.0, 1.0, sides=4))
    m.add("band", tub("body", "goldl", ring_pts(8.0, 9, 20), [0.6] * 21, None, hint=(0, 1, 0), sides=4, caps=(False, False)))


def ss_final_light(m):
    # 範囲攻撃 (三度ひろがる光): 金の輪の上に立つ四角い光の柱の輪、頭が白い角錐
    for k in range(12):
        a = k / 12 * math.tau
        o = P(math.cos(a), 0, math.sin(a))
        b = o * 14
        h = 9 if k % 2 == 0 else 6
        m.add(f"pil{k}", tub("body", "light", [b + P(0, -3, 0), b + P(0, h, 0)], [1.4, 1.4], None, sides=4, phase=45, caps=(True, False)))
        m.add(f"cap{k}", cone("body", "edgew", tuple(b + P(0, h, 0)), (0, 1, 0), 3.0, 1.4, sides=4))
    loop(m, "ring", "goldl", 14, 0.6, 0.6)
    loop(m, "ring2", "sigil", 11, 0.6, 0.4)


ATTACK = ["titan_cleave", "whale_sweep", "shield_bash", "bone_swing", "spin_cut", "wind_rend_fly", "twin_fang", "ember_hammer", "forge_stomp", "spark_burst",
          "cloud_pierce_fly", "sky_rain", "sky_rain_fall", "thunder_bolt_fly", "thunder_bolt", "ember_orb_fly", "ember_orb", "frost_tide", "stardust",
          "stardust_fall", "deep_pull", "deep_maw", "echo_strike", "grave_bell_fall", "final_light"]
BUFF = ["ward_song", "cycle_song", "lamp_charm", "mist_veil", "temper", "deep_echo"]
DEBUFF = ["rock_call", "ebb_tide", "curse_mark", "ash_scatter", "bone_trap", "snare_shot_fly", "snare_shot"]
MOVE = ["shield_rush", "lunge", "backstep", "blink"]  # 弓の番の弓霊は build_bow_spirit.py
ALL = ATTACK + BUFF + DEBUFF + MOVE
GROUPS = {"attack": ATTACK, "buff": BUFF, "debuff": DEBUFF, "move": MOVE}
LABEL = {"attack": "攻撃", "buff": "強化", "debuff": "弱体", "move": "移動"}


def build(key):
    kind = "ss_" + key
    m = Mount("prop_" + kind, kind)
    m.bone("body", "root", (0, KT.CY, 0) if key.endswith("_fly") else (0, 0, 0))
    globals()["ss_" + key](m)
    m.pal = dict(mats=rmats(), glow_mats=sorted(RGLOW))
    m.detail = None
    m.hint = 256
    return m


def idle(key):
    g = Gait("idle", 1.6)
    if key in BUFF:
        g.rot("body", None, W(0).s(25, 1, 0, "t"), None)
    elif key.endswith("_fly"):
        g.rot("body", None, None, W(0).s(6, 1, 0, "t") if key.startswith("ember") else W(0).s(0.5, 1, 0, "t"))
    else:
        g.rot("body", W(0).s(0.6, 1, 0, "t"))
    return g


def main():
    args = [a for a in sys.argv[1:] if not a.startswith("--")]
    keys = args or ALL
    REVIEW.mkdir(parents=True, exist_ok=True)
    tiles = {}
    for key in keys:
        m = build(key)
        size, atlas, glow = blp.paint_mount(m, m.pal, m.detail, m.hint)
        if "--no-install" not in sys.argv:
            blp.install(m, "prop_ss_" + key, size, atlas, glow, [idle(key)], (4, 8))
        lo = np.min([f.verts.min(0) for f in m.F], axis=0)
        hi = np.max([f.verts.max(0) for f in m.F], axis=0)
        c = (lo + hi) / 2
        sc = 220 / max(1.0, float(np.max(hi - lo)))
        tiles[key] = (view(m.F, atlas, glow, 210, 24, (300, 300), c, sc, ground=False), sum(len(f.verts) - 2 for f in m.F))
        print(key, "tris", tiles[key][1], flush=True)
    for g, ks in GROUPS.items():
        ks = [k for k in ks if k in tiles]
        if not ks:
            continue
        cols = 5
        rows = (len(ks) + cols - 1) // cols
        cv = Image.new("RGB", (cols * 310 + 10, rows * 334 + 10), (16, 16, 22))
        d = ImageDraw.Draw(cv)
        for i, k in enumerate(ks):
            x, y = 10 + (i % cols) * 310, 10 + (i // cols) * 334
            d.text((x, y), f"{LABEL[g]}：{k}  {tiles[k][1]}", font=voxel.font(15), fill=(150, 232, 255))
            cv.paste(tiles[k][0], (x, y + 24))
        cv.save(REVIEW / f"each_{g}.png")


if __name__ == "__main__":
    sys.stdout.reconfigure(encoding="utf-8")
    main()
