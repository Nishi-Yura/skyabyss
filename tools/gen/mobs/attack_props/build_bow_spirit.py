#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""番の弓霊 (弓使いの技 bow_spirit。2026-11-13 の声「弓のペットみたいなの召喚して勝手に撃ってくれる」) の形。
rpg/skill/BowSpirits が prop_bow_spirit (肩のそばに浮かぶ霊。AttackProp の FOLLOW) と prop_bow_spirit_fly (霊の放つ矢) を出す。

    python -X utf8 -I tools/gen/mobs/attack_props/build_bow_spirit.py [--install a|b|c]

  A 骨弓の霊   宙に立つ骨の弓。弦の後ろに青緑の霊の灯 (目が二つ)、尾が後ろへ流れる
  B 骨の小鳥   骨の頭の小さな鳥。翼の先が弓の両端になっていて、胸に矢をつがえる
  C 灯の射手   骨の籠に灯った霊火。籠の前に小さな弓が据わる
前 = -Z (持ち主の見ている向き)。大きさ 1 で 16 = 1 ブロック (呼ぶときは 0.75 倍)。絵は docs/review/skill_props/bow_spirit.png

2026-11-13 にユーザーが A を選んだ。清書 (--final。ゲームに入るのはこれ) は final_spirit / final_fly:
  弓   節のある骨の腕が前へ反り、先で後ろへ返る (弭は白い角)。握りは革の巻き、上下に骨の受け。霊が弦を引き絞り、羽根の付いた骨の矢をつがえる。
       握りの下に骨の玉と羽根の下げ飾り
  霊   青緑の炎の体に、角の生えた骨の半面 (目の穴の奥が白く光る)。頭の上に淡い炎の頭巾、後ろへ三筋の炎の尾
  動き 霊はゆっくり上下し、尾はうねり、弓はわずかに傾く (骨 bow・soul・tail)。骨はひび割れ、縁が暗い
絵は docs/review/skill_props/bow_spirit_final.png (ラフ A と並べる)
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

C0 = 8.0  # 霊の芯の高さ


def bow_arc(m, nm, x0=0.0, y0=C0, z0=0.0, h=8.0, bulge=3.0, w=0.75, mat="bone", string=True):
    """縦の弓 (前 = -Z へ膨らむ)。両端に白い弭、後ろに弦"""
    pts = [P(x0, y0 + h * t, z0 - bulge * (1 - t * t)) for t in np.linspace(-1, 1, 11)]
    m.add(nm, tub("body", mat, pts, [w * 0.7] + [w] * 9 + [w * 0.7], None, hint=(1, 0, 0), sides=5))
    for s in (1, -1):
        m.add(f"{nm}_tip{s}", cone("body", "edgew", (x0, y0 + s * h, z0), (0, s, 0.4), 2.2, w * 0.8, sides=4))
    if string:
        m.add(f"{nm}_str", tub("body", "sigil", [P(x0, y0 + h, z0 + 0.3), P(x0, y0 - h, z0 + 0.3)], [0.22, 0.22], None, hint=(1, 0, 0), sides=4))


def arrow(m, nm, x0=0.0, y0=C0, z0=0.5, ln=11.0, w=0.4):
    m.add(nm, tub("body", "bone", [P(x0, y0, z0), P(x0, y0, z0 - ln)], [w, w], None, sides=5))
    m.add(nm + "_head", cone("body", "edgew", (x0, y0, z0 - ln), (0, 0, -1), 2.6, w * 2.4, sides=4))
    for s in (1, -1):
        m.add(f"{nm}_fl{s}", tub("body", "sigil", [P(x0, y0, z0), P(x0 + s * 1.2, y0, z0 + 1.6), P(x0 + s * 1.0, y0, z0 - 0.6)],
                                 [0.2, 0.25, 0.15], None, hint=(0, 1, 0), sides=4))


def wisp(m, nm, c, r=2.4, tail=True):
    """霊の灯: 青緑の芯と白い目、後ろ下へ流れる尾"""
    c = np.asarray(c, float)
    m.add(nm, ell("body", "sigil", tuple(c), (r, r * 1.1, r), segs=8, rings=4))
    # 頭の上にゆらぐ淡い炎 (霊だとわかる印)
    m.add(nm + "_flame", cone("body", "windp", tuple(c + P(0, r * 0.8, 0.3)), (0, 1, 0.35), r * 1.3, r * 0.55, sides=5))
    for s in (1, -1):
        m.add(f"{nm}_eye{s}", ell("body", "edgew", tuple(c + P(s * r * 0.4, r * 0.15, -r * 0.95)), (0.45, 0.6, 0.3), segs=6, rings=2))
    if tail:
        pts = [c + P(0.6 * math.sin(k * 0.9), -0.9 * k, 1.4 * k) for k in range(6)]
        m.add(nm + "_tail", tub("body", "sigil", pts, [r * 0.8 * (1 - k / 6) + 0.15 for k in range(6)], None, hint=(1, 0, 0), sides=5))


# ---------------------------------------------------------------- A 骨弓の霊
def opt_a(m):
    bow_arc(m, "bow", z0=-1.0, h=8.5, bulge=3.2)
    arrow(m, "arw", z0=1.6, ln=12.0)
    wisp(m, "soul", P(0, C0, 3.6), r=2.3)
    # 弓を抱く細い霊の腕 (弓の握りと矢筈へ)
    for s, (a, b) in ((1, (P(1.6, C0 - 0.6, 3.0), P(0.5, C0, -3.6))), (-1, (P(-1.6, C0 - 0.4, 3.0), P(-0.4, C0, 1.8)))):
        m.add(f"arm{s}", tub("body", "windp", [a, (a + b) / 2 + P(s * 1.2, -0.6, 0), b], [0.55, 0.45, 0.35], None, hint=(0, 1, 0), sides=5))


# ---------------------------------------------------------------- B 骨の小鳥
def opt_b(m):
    body = P(0, C0, 1.0)
    m.add("body", ell("body", "bone", tuple(body), (2.6, 2.4, 3.6), segs=8, rings=4))
    m.add("breast", ell("body", "windp", tuple(body + P(0, -0.4, -1.4)), (1.9, 1.8, 2.0), segs=8, rings=3))
    head = body + P(0, 2.6, -2.6)
    m.add("skull", ell("body", "bone", tuple(head), (1.9, 1.8, 2.0), segs=8, rings=4))
    m.add("beak", cone("body", "edgew", tuple(head + P(0, -0.3, -1.6)), (0, -0.35, -1), 2.6, 0.8, sides=4))
    for s in (1, -1):
        m.add(f"eye{s}", ell("body", "sigil", tuple(head + P(s * 1.05, 0.3, -1.0)), (0.5, 0.6, 0.5), segs=6, rings=2))
        # 翼 = 弓の腕 (外へ反り、先が白い弭)
        pts = [body + P(s * (1.8 + 7.0 * t), 1.2 + 3.2 * t * t, -0.8 - 2.4 * math.sin(t * math.pi * 0.8)) for t in np.linspace(0, 1, 8)]
        m.add(f"wing{s}", tub("body", "bone", pts, [1.4, 1.5, 1.3, 1.1, 0.9, 0.7, 0.55, 0.4], None, hint=(0, 1, 0), sides=5))
        m.add(f"wtip{s}", cone("body", "edgew", tuple(pts[-1]), (s * 0.5, 0.7, 0.2), 2.0, 0.5, sides=4))
        m.add(f"str{s}", tub("body", "sigil", [pts[-1], body + P(0, 0.4, 2.8)], [0.18, 0.18], None, hint=(0, 1, 0), sides=4))
    arrow(m, "arw", y0=C0 + 0.4, z0=2.8, ln=10.0, w=0.35)
    tail = [body + P(0, -0.6 - 0.3 * k, 3.0 + 1.3 * k) for k in range(4)]
    m.add("tail", tub("body", "sigil", tail, [1.1, 0.8, 0.5, 0.2], None, hint=(1, 0, 0), sides=5))


# ---------------------------------------------------------------- C 灯の射手
def opt_c(m):
    c = P(0, C0, 1.5)
    wisp(m, "soul", c, r=1.9, tail=False)
    for k in range(6):
        a = k / 6 * math.tau
        pts = [c + P(math.cos(a) * 3.4 * math.sin(t * math.pi), 4.2 * math.cos(t * math.pi), math.sin(a) * 3.4 * math.sin(t * math.pi)) for t in np.linspace(0, 1, 7)]
        m.add(f"rib{k}", tub("body", "bone", pts, [0.45] * 7, None, hint=(0, 1, 0), sides=4))
    m.add("cap", ell("body", "bone", tuple(c + P(0, 4.3, 0)), (1.6, 0.8, 1.6), segs=6, rings=2))
    m.add("foot", cone("body", "bone", tuple(c + P(0, -4.0, 0)), (0, -1, 0), 2.6, 1.2, sides=5))
    bow_arc(m, "bow", z0=-3.4, h=5.2, bulge=2.2, w=0.6)
    arrow(m, "arw", z0=-2.8, ln=7.0, w=0.32)
    m.add("flame", cone("body", "sigil", tuple(c + P(0, 4.9, 0)), (0, 1, 0.2), 3.0, 0.9, sides=5))


def fly(m):
    # 霊の矢: 細い骨の矢と、尾を引く青緑の光 (前 = -Z、芯 y = CY)
    y = KT.CY
    m.add("shaft", tub("body", "bone", [P(0, y, 6), P(0, y, -5)], [0.45, 0.45], None, sides=5))
    m.add("head", cone("body", "edgew", (0, y, -5), (0, 0, -1), 3.0, 1.2, sides=4))
    m.add("trail", tub("body", "sigil", [P(0, y, 3), P(0, y, 8), P(0, y, 13)], [0.9, 0.6, 0.2], None, hint=(1, 0, 0), sides=5))
    for s in (1, -1):
        m.add(f"fl{s}", tub("body", "windp", [P(0, y, 5.5), P(s * 1.4, y, 7.0), P(s * 1.2, y, 4.0)], [0.25, 0.3, 0.2], None, hint=(0, 1, 0), sides=4))


# ================================================================ 清書 (A 骨弓の霊)
CORD = [(40, 26, 18), (64, 42, 28), (92, 62, 40), (120, 86, 58)]
DARK = [(8, 12, 14), (14, 22, 26), (22, 34, 38)]
FEATHER = [(70, 64, 56), (110, 102, 90), (160, 152, 136), (206, 200, 186)]
Z0 = -1.0           # 弓の握りの z
NOCK = Z0 + 3.4     # 引き絞った弦の z
SOUL = P(0, C0 + 0.8, Z0 + 6.2)


def fmats():
    m = rmats()
    m.update({"cord": M_(CORD, lit=0.32, streak=0.06), "dark": M_(DARK, lit=0.1, noise=0.0),
              "feather": M_(FEATHER, lit=0.34, streak=0.1)})
    return m


def limb_pt(s, t):
    """弓の腕の上の点 (s = 上 1 / 下 -1、t = 握り 0 → 先 1)。前 (-Z) へ反り、先で後ろへ返る"""
    y = C0 + s * (1.5 + 7.4 * t)
    z = Z0 - 3.0 * math.sin(t * math.pi * 0.82) + 1.5 * t ** 4
    return P(0, y, z)


def final_bow(m):
    b = "bow"
    ts = np.linspace(0, 1, 13)
    for s in (1, -1):
        pts = [limb_pt(s, t) for t in ts]
        m.add(f"limb{s}", tub(b, "bone", pts, [1.0 - 0.5 * t for t in ts], None, hint=(1, 0, 0), sides=6))
        # 背骨のような節 (腕に半分沈めた平たい輪) と、節の背の小さな棘
        for k, t in enumerate((0.18, 0.36, 0.54, 0.72)):
            c = limb_pt(s, t)
            d = limb_pt(s, t + 0.02) - limb_pt(s, t - 0.02)
            rx = -math.degrees(math.atan2(d[2], abs(d[1]))) * s
            r = (1.0 - 0.5 * t) * 1.18
            m.add(f"knot{s}{k}", ell(b, "bone", tuple(c), (r, 0.3, r * 1.04), segs=8, rings=2, rx=rx))
            m.add(f"spur{s}{k}", cone(b, "bone", tuple(c + P(0, 0, r * 0.7)), (0, s * 0.35, 1), 0.9, 0.28, sides=4))
        tip = limb_pt(s, 1.0)
        m.add(f"horn{s}", cone(b, "edgew", tuple(tip - P(0, s * 0.3, 0.2)), (0, s * 0.7, 0.75), 2.4, 0.55, sides=5))
        # 弦 (先から、引き絞った矢筈へ)
        m.add(f"str{s}", tub(b, "sigil", [tip + P(0, -s * 0.2, 0.4), P(0, C0, NOCK)], [0.17, 0.17], None, hint=(1, 0, 0), sides=4))
    # 握り: 革の巻きと、上下の骨の受け、矢の置き
    m.add("grip", tub(b, "cord", [P(0, C0 - 1.9, Z0), P(0, C0 + 1.9, Z0)], [1.12, 1.12], None, hint=(0, 0, 1), sides=8))
    for s in (1, -1):
        m.add(f"cup{s}", ell(b, "bone", (0, C0 + s * 1.9, Z0), (1.45, 0.55, 1.45), segs=8, rings=2))
    m.add("rest", ell(b, "bone", (0, C0 + 0.1, Z0 - 0.9), (0.55, 0.4, 0.5), segs=6, rings=2))
    # 下げ飾り: 握りの下から紐、骨の玉、羽根
    a0, a1 = P(0.7, C0 - 2.1, Z0 + 0.2), P(1.5, C0 - 4.6, Z0 + 0.9)
    m.add("charm_cord", tub(b, "cord", [a0, (a0 + a1) / 2 + P(0.2, 0, 0), a1], [0.16, 0.16, 0.16], None, hint=(0, 0, 1), sides=4))
    m.add("bead", ell(b, "bone", tuple(a1), (0.62, 0.62, 0.62), segs=8, rings=3))
    m.add("charm_feather", ell(b, "feather", tuple(a1 + P(0.15, -1.6, 0.25)), (0.32, 1.45, 0.62), segs=6, rings=3, rx=-12))
    # 矢: 骨の軸、白い鏃と青緑の帯、三枚の羽根、矢筈
    m.add("shaft", tub(b, "bone", [P(0, C0, NOCK + 0.2), P(0, C0, Z0 - 10.5)], [0.3, 0.3], None, sides=6))
    m.add("arrow_head", cone(b, "edgew", (0, C0, Z0 - 10.3), (0, 0, -1), 2.5, 0.85, sides=4))
    m.add("arrow_collar", ell(b, "sigil", (0, C0, Z0 - 10.2), (0.55, 0.55, 0.3), segs=6, rings=2))
    for k in range(3):
        a = k / 3 * math.tau + math.pi / 2
        o = P(math.cos(a), math.sin(a), 0)
        m.add(f"vane{k}", ell(b, "feather", tuple(P(0, C0, NOCK - 1.6) + o * 0.55), (0.14 + 0.5 * abs(o[0]), 0.14 + 0.5 * abs(o[1]), 1.5),
                              segs=6, rings=2))
    m.add("nock", ell(b, "edgew", (0, C0, NOCK + 0.1), (0.36, 0.36, 0.3), segs=6, rings=2))
    # 霊の腕 (肩から握りへ、もう一方は矢筈をつまむ)。先は三本の指
    sh_r, sh_l = SOUL + P(1.7, -1.0, -0.4), SOUL + P(-1.7, -1.0, -0.4)
    hand_r, hand_l = P(0.9, C0 + 0.2, Z0 + 0.3), P(-0.45, C0 + 0.1, NOCK + 0.1)
    for nm, a, h, out in (("armr", sh_r, hand_r, 1), ("arml", sh_l, hand_l, -1)):
        mid = (a + h) / 2 + P(out * 1.3, -0.9, 0)
        m.add(nm, tub(b, "sigil", [a, mid, h], [0.62, 0.46, 0.36], None, hint=(0, 1, 0), sides=6))
        for k in range(3):
            m.add(f"{nm}_f{k}", cone(b, "sigil", tuple(h), (out * -0.6, 0.5 - k * 0.5, -0.5), 1.1, 0.2, sides=4))


def final_soul(m):
    b = "soul"
    S = SOUL
    m.add("core", ell(b, "sigil", tuple(S), (2.1, 2.4, 2.0), segs=10, rings=5))
    # 胸から下へ細くなる炎の体
    m.add("chest", cone(b, "sigil", tuple(S + P(0, -0.8, 0.2)), (0, -1, 0.25), 3.4, 1.6, sides=7))
    # 頭巾の炎
    m.add("hood", cone(b, "sigil", tuple(S + P(0, 1.1, 0.5)), (0, 1, 0.55), 3.6, 1.7, sides=7))
    m.add("hood_tip", cone(b, "windp", tuple(S + P(0.5, 2.6, 1.4)), (0.25, 1, 0.9), 1.8, 0.55, sides=5))
    # 骨の半面 (前 = -Z)、眉の張り、鼻先、角
    mask = S + P(0, 0.35, -1.75)
    m.add("mask", ell(b, "bone", tuple(mask), (1.75, 1.95, 0.75), segs=10, rings=4))
    m.add("brow", ell(b, "bone", tuple(mask + P(0, 0.75, -0.35)), (1.6, 0.42, 0.55), segs=8, rings=2))
    m.add("snout", cone(b, "bone", tuple(mask + P(0, -0.7, -0.4)), (0, -0.55, -1), 1.1, 0.6, sides=5))
    for s in (1, -1):
        m.add(f"socket{s}", ell(b, "dark", tuple(mask + P(s * 0.62, 0.2, -0.55)), (0.5, 0.42, 0.3), segs=8, rings=2))
        m.add(f"glint{s}", ell(b, "edgew", tuple(mask + P(s * 0.62, 0.2, -0.72)), (0.22, 0.24, 0.14), segs=6, rings=2))
        horn = [mask + P(s * 1.2, 1.3, 0.1), mask + P(s * 1.9, 2.6, 0.9), mask + P(s * 2.1, 3.5, 2.0)]
        m.add(f"mhorn{s}", tub(b, "bone", horn, [0.45, 0.3, 0.12], None, hint=(1, 0, 0), sides=5))


def final_tail(m):
    b = "tail"
    base = SOUL + P(0, -2.2, 1.0)
    for k, (ox, ph) in enumerate(((0.0, 0.0), (1.0, 2.0), (-1.0, 4.0))):
        pts = [base + P(ox * (0.3 + 0.25 * j) + 0.45 * math.sin(j * 0.9 + ph), -0.85 * j, 1.25 * j) for j in range(7)]
        mat = "sigil" if k == 0 else "windp"
        r0 = 1.25 if k == 0 else 0.75
        m.add(f"strand{k}", tub(b, mat, pts, [r0 * (1 - j / 7) + 0.12 for j in range(7)], None, hint=(1, 0, 0), sides=6))


def final_spirit(m):
    final_bow(m)
    final_soul(m)
    final_tail(m)


def final_fly(m):
    # 霊の矢 (清書): 細い骨の軸、白い鏃と青緑の帯、三枚の羽根、後ろへ二筋の光
    y = KT.CY
    m.add("shaft", tub("body", "bone", [P(0, y, 6.5), P(0, y, -5)], [0.36, 0.36], None, sides=6))
    m.add("head", cone("body", "edgew", (0, y, -4.8), (0, 0, -1), 3.0, 1.1, sides=4))
    m.add("collar", ell("body", "sigil", (0, y, -4.7), (0.7, 0.7, 0.35), segs=6, rings=2))
    for k in range(3):
        a = k / 3 * math.tau + math.pi / 2
        o = P(math.cos(a), math.sin(a), 0)
        m.add(f"vane{k}", ell("body", "feather", tuple(P(0, y, 4.6) + o * 0.6), (0.14 + 0.55 * abs(o[0]), 0.14 + 0.55 * abs(o[1]), 1.6), segs=6, rings=2))
    m.add("nock", ell("body", "edgew", (0, y, 6.5), (0.4, 0.4, 0.35), segs=6, rings=2))
    for s in (1, -1):
        pts = [P(s * 0.3, y, 5.5), P(s * 0.9, y + 0.3 * s, 9.0), P(s * 0.5, y - 0.2 * s, 13.0)]
        m.add(f"trail{s}", tub("body", "sigil", pts, [0.55, 0.38, 0.12], None, hint=(0, 1, 0), sides=5))


def fdetail(c, col, glow):
    sel = np.isin(c["mats"], ["bone"]) & c["owned"]
    f1, f2, idv = blp.cell3(c["P"], 1.6, 11)
    crack = sel & ((f2 - f1) < 0.035) & (idv > 0.6)
    col = np.where(crack[..., None], np.array([92, 84, 70]), col)
    col = blp.bands_y(c, col, ["cord"], 0.7, 0.22, k=0.62)
    col = blp.edge_dark(c, col, ["bone", "cord"], k=0.8)
    return col, glow


def build_final(name, fn, center=(0, 0, 0), parts=True):
    m = Mount("prop_" + name, name)
    m.bone("body", "root", center)
    if parts:
        m.bone("bow", "body", (0, C0, Z0))
        m.bone("soul", "body", tuple(SOUL))
        m.bone("tail", "soul", tuple(SOUL + P(0, -2.2, 1.0)))
    fn(m)
    m.pal = dict(mats=fmats(), glow_mats=sorted(RGLOW))
    m.detail = fdetail
    m.hint = 512
    return m


def idle_final():
    g = Gait("idle", 2.4)
    g.rot("body", W(0).s(2.5, 1, 0, "t"), W(0).s(4, 1, 60, "t"), None)
    g.pos("soul", None, W(0).s(0.35, 1, 90, "t"), None)
    g.rot("tail", W(0).s(10, 1, 0, "t"), W(0).s(14, 1, 120, "t"), None)
    g.rot("bow", None, None, W(0).s(3, 1, 200, "t"))
    return g


OPTS = {"a": ("A 骨弓の霊 (推し)", opt_a), "b": ("B 骨の小鳥", opt_b), "c": ("C 灯の射手", opt_c)}


def build(name, fn, center=(0, 0, 0)):
    m = Mount("prop_" + name, name)
    m.bone("body", "root", center)
    fn(m)
    m.pal = dict(mats=rmats(), glow_mats=sorted(RGLOW))
    m.detail = None
    m.hint = 256
    return m


def idle(fly_model):
    g = Gait("idle", 2.0)
    if fly_model:
        g.rot("body", None, None, W(0).s(0.5, 1, 0, "t"))
    else:
        # ゆっくり左右に揺れて、少し前へ傾く
        g.rot("body", W(0).s(4, 1, 0, "t"), W(0).s(6, 1, 0.25, "t"), None)
    return g


def tile(m, yaw=210, pitch=18):
    size, atlas, glow = blp.paint_mount(m, m.pal, m.detail, m.hint)
    lo = np.min([f.verts.min(0) for f in m.F], axis=0)
    hi = np.max([f.verts.max(0) for f in m.F], axis=0)
    c = (lo + hi) / 2
    sc = 220 / max(1.0, float(np.max(hi - lo)))
    return (size, atlas, glow), view(m.F, atlas, glow, yaw, pitch, (300, 300), c, sc, ground=False), sum(len(f.verts) - 2 for f in m.F)


def main_final():
    """清書: ラフ A と並べた絵を出し、ゲームに入れる"""
    REVIEW.mkdir(parents=True, exist_ok=True)
    cv = Image.new("RGB", (4 * 310 + 10, 2 * 334 + 10), (16, 16, 22))
    d = ImageDraw.Draw(cv)
    d.text((10, 10), "ラフ A", font=voxel.font(15), fill=(150, 232, 255))
    cv.paste(tile(build("bow_spirit", opt_a))[1], (10, 34))
    m = build_final("bow_spirit", final_spirit)
    paint = None
    for i, (yaw, pitch) in enumerate(((210, 18), (320, 10), (150, 25), (270, 5))):
        pt, img, tris = tile(m, yaw, pitch)
        paint = paint or pt
        x, y = 10 + ((i + 1) % 4) * 310, 34 + ((i + 1) // 4) * 334
        d.text((x, y - 24), f"清書 {tris}", font=voxel.font(15), fill=(150, 232, 255))
        cv.paste(img, (x, y))
    print("final tris", tris, flush=True)
    blp.install(m, "prop_bow_spirit", *paint, [idle_final()], (4, 8))
    mf = build_final("bow_spirit_fly", final_fly, (0, KT.CY, 0), parts=False)
    pt, img, tris = tile(mf, yaw=250, pitch=20)
    d.text((10 + 310, 34 + 334 - 24), f"霊の矢 清書 {tris}", font=voxel.font(15), fill=(150, 232, 255))
    cv.paste(img, (10 + 310, 34 + 334))
    blp.install(mf, "prop_bow_spirit_fly", *pt, [idle(True)], (4, 8))
    cv.save(REVIEW / "bow_spirit_final.png")
    print("wrote", REVIEW / "bow_spirit_final.png")


def main():
    if "--final" in sys.argv:
        main_final()
        return
    pick = None
    if "--install" in sys.argv:
        pick = sys.argv[sys.argv.index("--install") + 1]
    REVIEW.mkdir(parents=True, exist_ok=True)
    cv = Image.new("RGB", (4 * 310 + 10, 2 * 334 + 10), (16, 16, 22))
    d = ImageDraw.Draw(cv)
    for i, (key, (label, fn)) in enumerate(OPTS.items()):
        m = build("bow_spirit", fn)
        paint, img, tris = tile(m)
        img2 = tile(m, yaw=320, pitch=10)[1]
        d.text((10 + i * 310, 10), f"{label}  {tris}", font=voxel.font(15), fill=(150, 232, 255))
        cv.paste(img, (10 + i * 310, 34))
        cv.paste(img2, (10 + i * 310, 34 + 334))
        print(key, "tris", tris, flush=True)
        if pick == key:
            blp.install(m, "prop_bow_spirit", *paint, [idle(False)], (4, 8))
    mf = build("bow_spirit_fly", fly, (0, KT.CY, 0))
    paint, img, tris = tile(mf, yaw=250, pitch=20)
    d.text((10 + 3 * 310, 10), f"霊の矢  {tris}", font=voxel.font(15), fill=(150, 232, 255))
    cv.paste(img, (10 + 3 * 310, 34))
    if pick:
        blp.install(mf, "prop_bow_spirit_fly", *paint, [idle(True)], (4, 8))
    cv.save(REVIEW / "bow_spirit.png")
    print("wrote", REVIEW / "bow_spirit.png")


if __name__ == "__main__":
    sys.stdout.reconfigure(encoding="utf-8")
    main()
