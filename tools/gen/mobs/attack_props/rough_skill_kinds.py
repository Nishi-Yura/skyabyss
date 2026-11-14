#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""技の種類 (攻撃・強化・弱体・移動) の見分け方の案 A/B/C (2026-11-12 の声「攻撃とバフ・デバフでモデルの見た目変えた方がいい。
スキルの種類によっても変えないと。使い回しは良くない」)。見本の技 6 つを 3 案で作り、絵だけ出す (ゲームには入れない)。

    python -X utf8 -I tools/gen/mobs/attack_props/rough_skill_kinds.py

  A 形で分ける   攻撃 = 外へ尖る刃 (白い刃先) / 強化 = 丸く昇る輪 (金と青緑) / 弱体 = 枷と鎖で締める (紫と黒) / 移動 = 残る軌跡
  B 印で分ける   形は職の材のまま技ごとに作り、種類は足もと・頭上の共通の印で言う (攻撃 = 赤い棘の輪 / 強化 = 金の冠 / 弱体 = 紫の滴る輪)
  C 場所で分ける 攻撃 = 敵の下から大きく突き上がる / 強化 = 自分の体のまわりを小さく回る / 弱体 = 敵の頭上から押しつぶす。色は職の色のまま
灰色の人形は立ち位置の目安 (絵だけ)。絵は docs/review/skill_props/kinds_<a|b|c>.png
"""
from __future__ import annotations

import math
import sys
from pathlib import Path

import numpy as np

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
import build_skill_kit as KT  # noqa: E402
from build_skill_kit import *  # noqa: E402,F401,F403

EDGEW = [(200, 220, 230), (232, 244, 250), (248, 252, 255), (255, 255, 255)]
ATK = [(150, 30, 20), (210, 60, 30), (250, 110, 50), (255, 180, 120)]
CHAIN = [(40, 36, 48), (64, 60, 76), (92, 88, 108), (128, 124, 146)]
DUMMY = [(70, 72, 78), (96, 98, 104), (122, 124, 130), (150, 152, 158)]


def rmats():
    m = KT.kmats()
    m.update({"edgew": M_(EDGEW, base=0.9, lit=0.1, noise=0.0, min=1), "atk": M_(ATK, base=0.9, lit=0.1, min=1),
              "chain": M_(CHAIN, lit=0.45), "dummy": M_(DUMMY, lit=0.3, noise=0.0)})
    return m


RGLOW = set(KT.KGLOW) | {"edgew", "atk"}


def dummy(m, x=0.0, z=0.0, tag="d"):
    """立ち位置の目安の灰色の人形 (絵だけ)"""
    m.add(tag + "_legs", tub("body", "dummy", [P(x, 0, z), P(x, 12, z)], [2.4, 2.8], None, sides=6, caps=(True, True)))
    m.add(tag + "_torso", tub("body", "dummy", [P(x, 12, z), P(x, 23, z)], [3.2, 3.6], None, sides=6, caps=(True, True)))
    m.add(tag + "_head", ell("body", "dummy", (x, 27.5, z), (3.0, 3.2, 3.0), segs=8, rings=3))


def ring_pts(r, y, n=24, tilt=0.0):
    return [P(math.cos(k / n * math.tau) * r, y + math.sin(k / n * math.tau) * r * math.sin(tilt), math.sin(k / n * math.tau) * r * math.cos(tilt)) for k in range(n + 1)]


def chain_line(m, name, a, b, links=5):
    """a から b への鎖 (輪を交互に向けて並べる)"""
    a, b = np.asarray(a, float), np.asarray(b, float)
    d = b - a
    L = float(np.linalg.norm(d))
    u = d / L
    for k in range(links):
        c = a + d * (k + 0.5) / links
        ang = math.degrees(math.atan2(u[0], u[2]))
        m.add(f"{name}{k}", ell("body", "chain", tuple(c), (0.9 if k % 2 else 1.6, 1.6 if k % 2 else 0.9, L / links * 0.62), segs=6, rings=2, ry=ang,
                                rx=-math.degrees(math.asin(max(-1, min(1, u[1]))))))


# ================================================================ A 形で分ける
def a_cleave(m):
    # 攻撃 (巨神斬り): 前の扇に、前へ倒れた花崗岩の刃が五枚突き出る。刃先は白
    for k, a in enumerate((-50, -25, 0, 25, 50)):
        r = math.radians(a)
        f = P(math.sin(r), 0, -math.cos(r))
        base = f * (10 + abs(a) * 0.05)
        h = 20 - abs(a) * 0.12
        pts = [base + P(0, -4, 0), base + f * 3 + P(0, h * 0.5, 0), base + f * 7 + P(0, h, 0)]
        m.add(f"blade{k}", tub("body", "granite", pts, [3.2, 2.2, 0.3], [1.2, 0.9, 0.2], hint=tuple(P(math.cos(r), 0, math.sin(r))), sides=4, phase=45))
        m.add(f"tip{k}", cone("body", "edgew", tuple(base + f * 5.6 + P(0, h * 0.78, 0)), tuple(f * 0.6 + P(0, 1, 0)), h * 0.32, 1.1, sides=4))
    base_debris(m, "granite", 12, seed=71)
    sig_loop(m, 6)
    dummy(m)


def a_sweep(m):
    # 攻撃 (鯨尾薙ぎ): 回る向きに寝た刃の輪。外へ尖り、刃先が白
    n = 9
    for k in range(n):
        a = k / n * math.tau
        out = P(math.cos(a), 0, math.sin(a))
        tang = P(-math.sin(a), 0, math.cos(a))
        b = out * 15
        pts = [b + P(0, -4, 0), b + out * 2 + tang * 3 + P(0, 5, 0), b + out * 4 + tang * 7 + P(0, 8, 0)]
        m.add(f"fin{k}", tub("body", "granite", pts, [2.6, 1.6, 0.3], [1.0, 0.8, 0.2], hint=tuple(P(0, 1, 0)), sides=4, phase=45))
        m.add(f"tip{k}", cone("body", "edgew", tuple(b + out * 3.4 + tang * 5.6 + P(0, 7, 0)), tuple(out * 0.6 + tang), 3.6, 0.8, sides=4))
    sig_loop(m, 15, 0.4)
    base_debris(m, "granite", 13, seed=72)
    dummy(m)


def a_ward(m):
    # 強化 (守りの唄): 自分を包む泡の半球 (水の板)、上へ重なる金の輪。尖りがない
    for row, (el, n) in enumerate(((15, 12), (45, 9), (72, 4))):
        e = math.radians(el)
        for k in range(n):
            az = (k + row * 0.5) / n * math.tau
            c = P(math.cos(e) * math.cos(az) * 16, 2 + math.sin(e) * 26, math.cos(e) * math.sin(az) * 16)
            m.add(f"plate{row}_{k}", ell("body", "water", tuple(c), (4.6 - row, 3.6 - row * 0.6, 1.0), segs=6, rings=1,
                                         ry=-math.degrees(az) + 90, rx=-(90 - el) * 0.9))
    for k, (r, y) in enumerate(((10, 32), (7, 37), (4, 41))):
        loop(m, f"halo{k}", "goldl" if k != 1 else "sigil", r, y, 0.5)
    for k in range(5):
        a = k * 1.25
        m.add(f"bub{k}", ell("body", "foam", (math.cos(a) * 11, 6 + k * 5, math.sin(a) * 11), (1.1, 1.1, 1.1), segs=6, rings=2))
    sig_loop(m, 16, 0.4)
    dummy(m)


def a_curse(m):
    # 弱体 (呪いの印): 敵の足に紫の枷、四本の鎖が頭上の錠前の目へ締め上げる
    loop(m, "shackle", "chain", 10, 2, 1.4)
    loop(m, "shackle_glow", "violet", 10, 3.6, 0.45)
    top = P(0, 42, 0)
    m.add("lock", ell("body", "chain", tuple(top), (4.2, 4.8, 2.4), segs=8, rings=3))
    m.add("lock_bow", tub("body", "chain", [top + P(math.cos(k / 8 * math.pi) * 3, 3.6 + math.sin(k / 8 * math.pi) * 3.6, 0) for k in range(9)],
                          [0.7] * 9, None, hint=(0, 0, 1), sides=4, caps=(False, False)))
    m.add("eye", ell("body", "violet", tuple(top + P(0, 0, -2.2)), (2.0, 1.2, 0.8), segs=8, rings=2))
    m.add("pupil", ell("body", "curse", tuple(top + P(0, 0, -2.8)), (0.6, 1.0, 0.4), segs=6, rings=1))
    for k in range(4):
        a = k / 4 * math.tau + math.pi / 4
        chain_line(m, f"ch{k}_", P(math.cos(a) * 10, 3, math.sin(a) * 10), top + P(math.cos(a) * 3.4, -3.6, math.sin(a) * 1.8), links=9)
    for k in range(6):
        a = k / 6 * math.tau
        m.add(f"drip{k}", cone("body", "violet", (math.cos(a) * 3.4, 36, math.sin(a) * 1.6), (0, -1, 0), 4 + (k % 3) * 2, 0.7, sides=4))
    dummy(m)


def a_ash(m):
    # 弱体の範囲 (灰散らし): 灰の円に六本の杭が内へ打ち込まれ、鎖で結ばれる。刃は外へ向かない
    m.add("ash", ell("body", "ash", (0, 0.4, 0), (16, 1.0, 16), segs=16, rings=1))
    loop(m, "rim", "violet", 15, 1.6, 0.4)
    n = 6
    tops = []
    for k in range(n):
        a = k / n * math.tau
        out = P(math.cos(a), 0, math.sin(a))
        b = out * 13
        top = b + out * 2.4 + P(0, 9, 0)
        m.add(f"stake{k}", tub("body", "chain", [b + P(0, -3, 0), top], [1.4, 1.0], None, sides=4, phase=45, caps=(True, True)))
        m.add(f"head{k}", ell("body", "violet", tuple(top + P(0, 0.6, 0)), (1.4, 0.8, 1.4), segs=4, rings=1, ry=45))
        tops.append(top)
    for k in range(n):
        chain_line(m, f"link{k}_", tops[k] + P(0, -1, 0), tops[(k + 1) % n] + P(0, -1, 0), links=6)
    for k in range(6):
        a = k / 6 * math.tau + 0.5
        m.add(f"crack{k}", tub("body", "violet", [P(math.cos(a) * 3, 1.5, math.sin(a) * 3), P(math.cos(a + 0.25) * 8, 1.5, math.sin(a + 0.25) * 8),
                                                  P(math.cos(a) * 12, 1.5, math.sin(a) * 12)], [0.5, 0.45, 0.25], None, hint=(0, 1, 0), sides=4))
    sig_loop(m, 6, 0.6)
    dummy(m)


def a_blink(m):
    # 移動 (転移): 通った道に残る星の残像 (薄くなる三つの人形の輪郭) と流れる筋
    for k in range(3):
        z = 14 + k * 10
        s = 1.0 - k * 0.22
        outline = [P(-2.6 * s, 0, z), P(-3.0 * s, 12 * s, z), P(-3.8 * s, 21 * s, z), P(-2.0 * s, 23 * s, z), P(2.0 * s, 23 * s, z), P(3.8 * s, 21 * s, z),
                   P(3.0 * s, 12 * s, z), P(2.6 * s, 0, z), P(-2.6 * s, 0, z)]
        m.add(f"ghost_body{k}", tub("body", "star" if k == 0 else "sigil", outline, [0.5] * 9, None, hint=(0, 0, 1), sides=4, caps=(False, False)))
        m.add(f"ghost_head{k}", tub("body", "star", ring_pts(3.0 * s, 26.4 * s, 12, tilt=math.pi / 2), [0.45] * 13, None, hint=(0, 0, 1), sides=4,
                                    caps=(False, False)))
    for k, (x, y) in enumerate(((-6, 6), (6, 9), (-3, 18), (4, 22))):
        m.add(f"streak{k}", tub("body", "wind", [P(x, y, 4), P(x, y, 22), P(x * 0.6, y, 40)], [0.8, 0.5, 0.1], None, hint=(0, 1, 0), sides=4))
    sig_loop(m, 6)
    dummy(m)


# ================================================================ B 印で分ける (形は職の材のまま。種類は共通の印)
def mark_attack(m, r=11):
    # 攻撃の印: 足もとの赤い輪から、外へ向いた低い棘
    loop(m, "atk_ring", "atk", r, 0.8, 0.6)
    for k in range(10):
        a = k / 10 * math.tau
        m.add(f"atk_t{k}", cone("body", "atk", (math.cos(a) * r, 0.8, math.sin(a) * r), (math.cos(a), 0.3, math.sin(a)), 4.0, 1.2, sides=4))


def mark_buff(m, y=36):
    # 強化の印: 頭上の金の冠 (上を向く山形)
    loop(m, "buf_ring", "goldl", 6, y, 0.5)
    for k in range(6):
        a = k / 6 * math.tau
        m.add(f"buf_t{k}", cone("body", "goldl", (math.cos(a) * 6, y, math.sin(a) * 6), (0, 1, 0), 3.6, 1.0, sides=4))


def mark_debuff(m, y=36):
    # 弱体の印: 頭上の紫の輪から、下へ滴る雫
    loop(m, "deb_ring", "violet", 6, y, 0.5)
    for k in range(6):
        a = k / 6 * math.tau
        m.add(f"deb_t{k}", cone("body", "violet", (math.cos(a) * 6, y, math.sin(a) * 6), (0, -1, 0), 3 + (k % 2) * 2.4, 0.9, sides=4))


def mark_move(m):
    # 移動の印: 青緑の矢じりが三つ、進む向きへ並ぶ
    for k in range(3):
        z = -6 - k * 6
        m.add(f"mv{k}", tub("body", "sigil", [P(-3, 0.8, z + 3), P(0, 0.8, z), P(3, 0.8, z + 3)], [0.6, 0.7, 0.6], None, hint=(0, 1, 0), sides=4))


def b_cleave(m):
    # 巨神斬り: 前に立つ大きな花崗岩の板刃 (墓石のような一枚)
    m.add("slab", tub("body", "granite", [P(0, -4, -12), P(0, 14, -13), P(0, 26, -14)], [7, 6, 2.0], [1.8, 1.6, 0.6], hint=(1, 0, 0), sides=4, phase=45))
    m.add("groove", tub("body", "sigil", [P(0, 2, -13.6), P(0, 20, -14.6)], [0.5, 0.4], None, hint=(1, 0, 0), sides=4))
    base_debris(m, "granite", 8, seed=73)
    mark_attack(m)
    dummy(m)


def b_sweep(m):
    # 鯨尾薙ぎ: 低い花崗岩の壁が円を半周、端に鯨の尾びれ
    pts = arc_pts(15, -150, 150, 13, y=3)
    m.add("wall", tub("body", "granite", pts, [3.0] * 13, [1.6] * 13, hint=(0, 1, 0), sides=4, phase=45))
    end = pts[-1]
    m.add("fluke", ell("body", "granite", tuple(end + P(0, 6, 0)), (6.4, 1.2, 3.0), segs=6, rings=1, ry=60))
    mark_attack(m, 9)
    dummy(m)


def b_ward(m):
    # 守りの唄: 鯨の霊を載せた彫り柱 (呪術師の材)
    m.add("pole", tub("body", "totem", [P(-10, -2, 0), P(-10, 22, 0)], [2.0, 1.6], None, sides=6, caps=(True, True)))
    m.add("whale", ell("body", "seaw", (-10, 26, 0), (3.2, 2.8, 6.6), segs=8, rings=3))
    m.add("eye", ell("body", "sigil", (-11.6, 27, -4), (0.6, 0.6, 0.6), segs=4, rings=1))
    loop(m, "foam", "foam", 12, 1, 0.6)
    mark_buff(m)
    dummy(m)


def b_curse(m):
    # 呪いの印: 敵の足もとの潮だまりに、黒い呪いの杭が二本
    m.add("pool", ell("body", "water", (0, 0.4, 0), (10, 0.8, 10), segs=12, rings=1))
    for k, x in enumerate((-7, 7)):
        m.add(f"stake{k}", tub("body", "totem", [P(x, -2, 3), P(x, 14, 3)], [1.4, 1.0], None, sides=6, caps=(True, True)))
        m.add(f"eye{k}", ell("body", "curse", (x, 12, 1.8), (1.2, 0.8, 0.6), segs=6, rings=1))
    mark_debuff(m)
    dummy(m)


def b_ash(m):
    # 灰散らし: 灰の山と、半ば埋もれた鍛冶の槌
    m.add("heap", ell("body", "ash", (0, 0, 0), (15, 4, 15), segs=14, rings=2))
    m.add("haft", tub("body", "wood", [P(6, 2, 4), P(11, 16, 8)], [1.0, 0.9], None, sides=6))
    m.add("head", tub("body", "iron", [P(8.6, 17, 6), P(13.4, 15, 10)], [2.6, 2.6], None, sides=4, phase=45, caps=(True, True)))
    for k in range(6):
        a = k * 1.05
        m.add(f"ember{k}", ell("body", "ember", (math.cos(a) * 9, 4 + k, math.sin(a) * 9), (0.8, 0.8, 0.8), segs=4, rings=1))
    mark_debuff(m)
    dummy(m)


def b_blink(m):
    # 転移: 着いた先に立つ小さな星の晶の柱
    for k, (x, z, h) in enumerate(((0, 6, 16), (4, 8, 10), (-4, 7, 9))):
        m.add(f"cry{k}", cone("body", "star", (x, -2, z), (x * 0.05, 1, 0), h, 2.2, sides=5))
    mark_move(m)
    dummy(m)


# ================================================================ C 場所で分ける (色は職の色のまま)
def c_cleave(m):
    # 攻撃は敵の下から大きく: 敵を貫いて突き上がる岩の大剣と、割れた床
    m.add("blade", tub("body", "granite", [P(0, -6, 0), P(0, 30, 0), P(0, 44, 0)], [8, 6.4, 0.4], [2.4, 2.0, 0.4], hint=(1, 0, 0), sides=4, phase=45))
    m.add("guard", tub("body", "granite", [P(-11, -2, 0), P(11, -2, 0)], [2.4, 2.4], None, hint=(0, 1, 0), sides=4, phase=45))
    m.add("groove", tub("body", "sigil", [P(0, 0, -2.6), P(0, 34, -2.4)], [0.6, 0.4], None, hint=(1, 0, 0), sides=4))
    base_debris(m, "granite", 16, n=10, seed=74)
    dummy(m, 0, -9)


def c_sweep(m):
    # 範囲攻撃も敵の足もとから: 床を割って弧を描く巨大な鯨の尾
    pts = [P(-18, -6, 6), P(-14, 10, -2), P(-4, 20, -8), P(8, 22, -8), P(18, 14, -2)]
    m.add("tail", tub("body", "granite", pts, [6, 5, 4, 3, 2], None, sides=6))
    m.add("fluke", ell("body", "granite", (22, 13, 0), (8, 1.6, 4.0), segs=8, rings=1, ry=-30, rz=-30))
    for k in range(4):
        m.add(f"scar{k}", ell("body", "sigil", tuple(pts[k + 1] + P(0, 0, -3.6)), (0.8, 0.8, 0.5), segs=4, rings=1))
    base_debris(m, "granite", 18, n=9, seed=75)
    for x in (-8, 8):
        dummy(m, x, 0, tag=f"d{x}")


def c_ward(m):
    # 強化は自分の体に小さく: 胸の高さを回る三匹の水の鯨と、頭の泡
    for k in range(3):
        a = k / 3 * math.tau
        x, z = math.cos(a) * 9, math.sin(a) * 9
        tang = math.degrees(math.atan2(-math.sin(a), math.cos(a)))
        m.add(f"whale{k}", ell("body", "seaw", (x, 16 + k * 2, z), (2.0, 1.8, 4.0), segs=8, rings=3, ry=tang))
        m.add(f"eye{k}", ell("body", "sigil", (x - math.sin(a) * 3, 16.6 + k * 2, z + math.cos(a) * 3), (0.4, 0.4, 0.4), segs=4, rings=1))
    m.add("orbit", tub("body", "foam", ring_pts(9, 17, 24, tilt=0.25), [0.3] * 25, None, hint=(0, 1, 0), sides=4, caps=(False, False)))
    m.add("bubble", tub("body", "water", ring_pts(6, 28, 16, tilt=math.pi / 2), [0.5] * 17, None, hint=(0, 0, 1), sides=4, caps=(False, False)))
    m.add("bubble2", tub("body", "water", ring_pts(6, 28, 16, tilt=0), [0.5] * 17, None, hint=(0, 1, 0), sides=4, caps=(False, False)))
    dummy(m)


def c_curse(m):
    # 弱体は頭上から押しつぶす: 敵の上に浮かぶ逆さの重い鐘 (呪術師の彫り木) が下を向く
    m.add("bell", tub("body", "totem", [P(0, 46, 0), P(0, 40, 0), P(0, 33, 0)], [2.4, 6.0, 8.0], None, sides=10, caps=(True, False)))
    m.add("rim", tub("body", "seaw", ring_pts(8.2, 33, 20), [0.8] * 21, None, hint=(0, 1, 0), sides=4, caps=(False, False)))
    for k in range(5):
        a = k / 5 * math.tau
        m.add(f"drip{k}", cone("body", "seaw", (math.cos(a) * 7, 33, math.sin(a) * 7), (0, -1, 0), 3 + (k % 2) * 3, 0.9, sides=4))
    m.add("eye", ell("body", "curse", (0, 40, -6.4), (2.0, 1.2, 0.6), segs=8, rings=2))
    dummy(m)


def c_ash(m):
    # 範囲の弱体は上から降る: 敵たちの上に灰の雲、下向きの灰の棘が降る
    for k in range(9):
        a = k * 0.7
        r = 4 + (k % 3) * 5
        m.add(f"cloud{k}", ell("body", "ash", (math.cos(a) * r, 40 + (k % 2) * 2, math.sin(a) * r), (5.4, 2.6, 5.4), segs=12, rings=3))
    for k in range(10):
        a = k * 1.9
        r = 3 + (k % 4) * 3.4
        m.add(f"spike{k}", cone("body", "ash", (math.cos(a) * r, 38, math.sin(a) * r), (0, -1, 0), 6 + (k % 3) * 2, 1.2, sides=4))
        if k % 3 == 0:
            m.add(f"glow{k}", ell("body", "ember", (math.cos(a) * r, 37, math.sin(a) * r), (0.9, 0.9, 0.9), segs=4, rings=1))
    for x in (-9, 9):
        dummy(m, x, 0, tag=f"d{x}")


def c_blink(m):
    # 移動は出た所で砕ける: 消えた場所に残る、割れて飛び散る星の晶
    for k in range(9):
        a = k / 9 * math.tau
        r = 6 + (k % 3) * 3
        d = (math.cos(a), 0.6 + (k % 2) * 0.4, math.sin(a))
        m.add(f"shard{k}", cone("body", "star", (math.cos(a) * r, 8 + (k % 4) * 4, math.sin(a) * r), d, 4 + (k % 3), 1.4, sides=4))
    loop(m, "ring", "star", 7, 1, 0.5)
    m.add("spark", ell("body", "star", (0, 14, 0), (1.6, 1.6, 1.6), segs=6, rings=2))


SAMPLES = [("cleave", "巨神斬り", "攻撃"), ("sweep", "鯨尾薙ぎ", "範囲攻撃"), ("ward", "守りの唄", "強化"),
           ("curse", "呪いの印", "弱体"), ("ash", "灰散らし", "範囲の弱体"), ("blink", "転移", "移動")]


def build(opt, key):
    kind = f"rk_{opt}_{key}"
    m = Mount("prop_" + kind, kind)
    m.bone("body", "root", (0, 0, 0))
    globals()[f"{opt}_{key}"](m)
    m.pal = dict(mats=rmats(), glow_mats=sorted(RGLOW))
    m.detail = None
    m.hint = 256
    return m


def main():
    REVIEW.mkdir(parents=True, exist_ok=True)
    for opt in "abc":
        tiles = []
        for key, name, cat in SAMPLES:
            m = build(opt, key)
            size, atlas, glow = blp.paint_mount(m, m.pal, m.detail, m.hint)
            lo = np.min([f.verts.min(0) for f in m.F], axis=0)
            hi = np.max([f.verts.max(0) for f in m.F], axis=0)
            c = (lo + hi) / 2
            sc = 220 / max(1.0, float(np.max(hi - lo)))
            tris = sum(len(f.verts) - 2 for f in m.F)
            tiles.append((f"{cat}：{name}", view(m.F, atlas, glow, 210, 24, (300, 300), c, sc, ground=False)))
            print(opt, key, "tris", tris, flush=True)
        cv = Image.new("RGB", (3 * 310 + 10, 2 * 334 + 10), (16, 16, 22))
        d = ImageDraw.Draw(cv)
        for i, (label, img) in enumerate(tiles):
            x, y = 10 + (i % 3) * 310, 10 + (i // 3) * 334
            d.text((x, y), label, font=voxel.font(17), fill=(150, 232, 255))
            cv.paste(img, (x, y + 24))
        cv.save(REVIEW / f"kinds_{opt}.png")


if __name__ == "__main__":
    sys.stdout.reconfigure(encoding="utf-8")
    main()
