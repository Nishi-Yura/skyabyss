"""NPC v2 の持ち物 (受け口ごとの別メッシュ)。どれも人の作図空間の絶対座標で作る (書き出しで受け口の pivot を引く)。
関数は c (人の仕様) を受け、面の一覧を返す。手の位置は c["_hand_r"] / c["_hand_l"] (kit.bones_for の受け口と同じ)。"""
from __future__ import annotations

import math

import numpy as np

import kit as K
from kit import V, part, box, rod, disc, ellipsoid, tube, loft, ringe, spike


def hand(c, side):
    b = K.bones_for(c)
    return np.array(b["prop_r" if side < 0 else "prop_l"][1], float)


def lantern(c, center, s=1.0, bone="prop_r", hook=None):
    """角灯: 台・四本の枠・ガラス・炎・笠・提げ手。center は炎の中心。"""
    x, y, z = center
    F = []
    F += part("l_base", box(bone, "metal2", V(x, y - 1.7 * s, z), (1.25 * s, 0.35 * s, 1.25 * s)))
    F += part("l_foot", box(bone, "metal2", V(x, y - 2.15 * s, z), (1.0 * s, 0.15 * s, 1.0 * s)))
    F += part("l_glass", box(bone, "lglass", V(x, y, z), (0.95 * s, 1.4 * s, 0.95 * s)))
    F += part("l_fire", ellipsoid(bone, "flame", (x, y - 0.2 * s, z), (0.45 * s, 0.85 * s, 0.45 * s), segs=6, rings=2))
    for i, (dx, dz) in enumerate(((-1, -1), (1, -1), (1, 1), (-1, 1))):
        F += part(f"l_bar{i}", rod(bone, "metal2", V(x + dx * 1.05 * s, y - 1.5 * s, z + dz * 1.05 * s), V(x + dx * 1.05 * s, y + 1.5 * s, z + dz * 1.05 * s),
                                   0.18 * s, sides=4))
    F += part("l_cap", box(bone, "metal2", V(x, y + 1.85 * s, z), (1.3 * s, 0.35 * s, 1.3 * s), taper=0.55))
    F += part("l_top", disc(bone, "metal2", V(x, y + 2.45 * s, z), 0.45 * s, 0.25 * s, n=6))
    top = hook if hook is not None else V(x, y + 3.6 * s, z)
    F += part("l_hand", tube(bone, "metal2", [V(x - 0.9 * s, y + 2.2 * s, z), V(x - 0.6 * s, top[1], top[2]), V(x + 0.6 * s, top[1], top[2]), V(x + 0.9 * s, y + 2.2 * s, z)],
                             ra=0.14 * s, sides=3, caps=(True, True)))
    return F


LANTERN_MATS = {
    "lglass": dict(ramp=[(120, 84, 40), (190, 140, 66), (236, 196, 110), (255, 230, 160)], base=0.62, lit=0.3, noise=0.04, crack=0, min=1),
    "flame": dict(ramp=[(200, 90, 20), (250, 160, 40), (255, 214, 110), (255, 244, 200)], base=0.7, lit=0.2, noise=0.05, crack=0, min=1),
}


# ---------------------------------------------------------------- 組合の書記: 帳面 (左手で胸に抱える)
def ledger(c):
    h = hand(c, 1)
    R = K.rot_y(-15) @ K.rot_x(-12)
    cen = h + V(-1.4, 1.2, -0.2)
    F = []
    F += part("cover", box("prop_l", "book", cen, (1.9, 2.5, 0.55), R=R))
    F += part("pages", box("prop_l", "paper", cen + R @ V(0.12, 0, 0), (1.75, 2.35, 0.48), R=R))
    F += part("spine", box("prop_l", "book2", cen + R @ V(-1.85, 0, 0), (0.25, 2.55, 0.62), R=R))
    F += part("clasp", box("prop_l", "brass", cen + R @ V(1.9, 0.2, 0), (0.2, 0.4, 0.62), R=R))
    F += part("quill", spike("prop_l", "feather", cen + R @ V(1.0, 2.2, -0.7), (0.3, 1.0, -0.15), 3.4, 0.45, sides=3, rings=2, bend=(0.4, 0, 0)))
    return F


# ---------------------------------------------------------------- 灯り番ミオ: 抱えた大角灯 (両手)
def mio_lantern(c):
    h = hand(c, -1)
    return lantern(c, V(0.0, h[1] - 3.2, h[2] - 0.4), s=1.45, hook=V(0, h[1] + 0.2, h[2] - 0.4))


# ---------------------------------------------------------------- 測量士ハヤセ: 三脚の測量器 (右手で立てる) と画板 (左手)
def hayase_tripod(c):
    h = hand(c, -1)
    top = h + V(0.0, 1.4, -0.4)
    F = []
    for i, (dx, dz) in enumerate(((-2.6, -2.2), (2.2, -2.6), (0.2, 3.0))):
        F += part(f"tleg{i}", rod("prop_r", "wood", top + V(0, -0.4, 0), V(top[0] + dx, 0.3, top[2] + dz), 0.38, 0.28, sides=4))
        F += part(f"tfoot{i}", spike("prop_r", "metal2", V(top[0] + dx * 0.98, 0.9, top[2] + dz * 0.98), (dx * 0.05, -1, dz * 0.05), 0.9, 0.3, sides=3, rings=1))
    F += part("thead", disc("prop_r", "metal2", top, 1.2, 0.35, n=8))
    F += part("tpost", disc("prop_r", "metal2", top + V(0, 0.9, 0), 0.45, 0.6, n=6))
    F += part("tscope", tube("prop_r", "brass", [top + V(0, 1.9, 1.8), top + V(0, 2.1, -3.0)], ra=[0.75, 0.9], sides=8, caps=(True, True)))
    F += part("tlens", disc("prop_r", "lens", top + V(0, 2.1, -3.15), 0.7, 0.12, n=8, axis="z"))
    F += part("tring", disc("prop_r", "metal2", top + V(0, 2.0, -0.6), 1.0, 0.25, n=8, axis="z"))
    F += part("tbubble", box("prop_r", "lens", top + V(0, 2.95, 0.4), (0.3, 0.2, 0.8)))
    return F


def hayase_board(c):
    h = hand(c, 1)
    F = []
    R = K.rot_x(-35) @ K.rot_y(-20)
    cen = h + V(-1.6, 0.6, -0.9)
    F += part("board", box("prop_l", "wood", cen, (2.4, 0.22, 3.0), R=R))
    F += part("paper", box("prop_l", "paper", cen + R @ V(0, 0.25, 0), (2.05, 0.06, 2.65), R=R))
    F += part("clip", box("prop_l", "brass", cen + R @ V(0, 0.32, -2.6), (0.9, 0.12, 0.35), R=R))
    return F


# ---------------------------------------------------------------- 薬師シズ: 背の薬草籠
def shizu_basket(c):
    F = []
    zc = 6.0
    rings = [ringe(7.4, 3.2, -2.3, 2.5, n=10, zc=zc), ringe(12.0, 4.2, -2.9, 3.2, n=10, zc=zc + 0.4), ringe(18.6, 4.8, -3.2, 3.6, n=10, zc=zc + 0.8)]
    F += part("basket", loft("prop_back", "wicker", rings, caps=("wicker", "wicker")))
    F += part("rim", loft("prop_back", "leather", [ringe(18.3, 5.0, -3.4, 3.8, n=10, zc=zc + 0.8), ringe(19.4, 5.0, -3.4, 3.8, n=10, zc=zc + 0.8)], caps=("leather", "leather")))
    F += part("band", loft("prop_back", "leather", [ringe(11.0, 4.1, -2.85, 3.15, n=10, zc=zc + 0.35), ringe(11.8, 4.2, -2.9, 3.2, n=10, zc=zc + 0.38)], caps=(False, False)))
    rng = np.random.RandomState(7)
    for i in range(9):
        a = i / 9 * 2 * math.pi
        base = V(3.2 * math.cos(a), 19.0, zc + 0.8 + 2.4 * math.sin(a))
        d = (math.cos(a) * 0.35 + rng.uniform(-0.2, 0.2), 1.0, math.sin(a) * 0.35)
        m = "leaf" if i % 3 else "leaf2"
        F += part(f"herb{i}", spike("prop_back", m, base, d, 4.0 + rng.uniform(0, 3.0), 0.55, sides=3, rings=2, bend=(d[0] * 1.2, -0.4, d[2] * 1.2)))
    for i, (x, y) in enumerate(((-2.0, 20.2), (1.8, 20.6))):
        F += part(f"flower{i}", ellipsoid("prop_back", "flower", (x, y + 3.6, zc + 1.0), (0.7, 0.55, 0.7), segs=6, rings=1))
    for s in (-1, 1):
        F += part(f"strap{s}", tube("prop_back", "leather", [V(s * 2.6, 18.0, zc - 2.6), V(s * 2.4, 21.6, 1.0), V(s * 2.2, 21.4, -2.3), V(s * 2.6, 15.0, -2.7)],
                                    ra=0.38, rb=0.15, sides=4, caps=(True, True), hint=(0, 0, 1)))
    return F


# ---------------------------------------------------------------- 隊長トウヤ: 肩のツルハシ (右手)
def toya_pick(c):
    h = hand(c, -1)
    F = []
    a = h + V(-0.2, -3.2, -0.6)
    b = h + V(1.2, 12.0, 6.4)
    F += part("handle", rod("prop_r", "wood", a, b, 0.48, 0.42, sides=6))
    hd = b + V(0, -0.6, -0.2)
    F += part("collar", disc("prop_r", "metal2", hd, 0.85, 0.7, n=6))
    F += part("pick_a", tube("prop_r", "metal2", [hd + V(0, 0, 0), hd + V(-3.5, -0.5, -1.0), hd + V(-6.6, -2.2, -1.6)], ra=[0.85, 0.6, 0.0], rb=[0.55, 0.45, 0.0],
                             sides=4, caps=(True, False), hint=(0, 1, 0)))
    F += part("pick_b", tube("prop_r", "metal2", [hd, hd + V(3.0, -0.4, 0.8), hd + V(5.2, -1.6, 1.3)], ra=[0.85, 0.6, 0.2], rb=[0.55, 0.45, 0.15],
                             sides=4, caps=(True, True), hint=(0, 1, 0)))
    F += part("wrap", disc("prop_r", "leather", a + (b - a) * 0.08, 0.6, 0.6, n=6)) if False else []
    return F


# ---------------------------------------------------------------- 荷運びロク: 巨大な荷袋
def roku_sack(c):
    F = []
    zc = 8.0
    F += part("sack", ellipsoid("prop_back", "sack", (0.0, 17.0, zc), (6.4, 8.0, 5.4), segs=10, rings=5))
    F += part("sack_neck", disc("prop_back", "sack", V(0, 25.2, zc - 0.2), 2.2, 0.9, n=8, r_top=1.4))
    F += part("sack_tie", disc("prop_back", "rope", V(0, 25.0, zc - 0.2), 2.4, 0.35, n=8))
    F += part("sack_patch", box("prop_back", "sack2", V(2.4, 14.0, zc + 5.2), (1.6, 1.8, 0.3)))
    F += part("roll", tube("prop_back", "blanket", [V(-7.8, 26.6, zc - 1.0), V(7.8, 26.6, zc - 1.0)], ra=2.2, sides=8, caps=(True, True)))
    for i, x in enumerate((-4.0, 3.6)):
        F += part(f"roll_band{i}", disc("prop_back", "rope", V(x, 26.6, zc - 1.0), 2.4, 0.4, n=8, axis="x"))
    F += part("kettle", ellipsoid("prop_back", "metal2", (-6.0, 10.8, zc + 1.0), (1.9, 1.8, 1.9), segs=8, rings=3))
    F += part("kettle_lid", disc("prop_back", "metal2", V(-6.0, 12.6, zc + 1.0), 1.0, 0.3, n=6))
    F += part("kettle_spout", rod("prop_back", "metal2", V(-6.0, 11.2, zc - 0.6), V(-6.2, 12.4, zc - 2.4), 0.4, 0.25, sides=4))
    F += part("kettle_rope", rod("prop_back", "rope", V(-6.0, 12.8, zc + 1.0), V(-5.0, 17.0, zc - 1.0), 0.22, sides=3))
    F += part("pan", disc("prop_back", "metal2", V(6.2, 12.4, zc + 1.6), 2.2, 0.35, n=10, axis="x"))
    F += part("pan_handle", rod("prop_back", "wood", V(6.4, 14.4, zc + 1.6), V(6.6, 18.2, zc + 1.0), 0.35, sides=4))
    for s in (-1, 1):
        F += part(f"strap{s}", tube("prop_back", "rope", [V(s * 2.6, 20.6, 3.0), V(s * 2.5, 21.8, 0.6), V(s * 2.4, 21.6, -2.4), V(s * 2.8, 14.8, -2.8),
                                                          V(s * 3.0, 12.0, 0.0), V(s * 3.0, 12.2, 2.8)],
                                    ra=0.38, sides=4, caps=(True, True)))
    return F


PROP_MATS = {
    "wood": dict(ramp=[(40, 26, 16), (74, 50, 30), (112, 80, 50), (150, 112, 72), (180, 140, 94)], base=0.55, lit=0.3, noise=0.12, crack=0.02, min=0,
                 stripe=3, axis="v", streak=0.12),
    "paper": dict(ramp=[(130, 118, 92), (176, 162, 128), (214, 202, 168), (236, 228, 200)], base=0.6, lit=0.3, noise=0.06, crack=0, min=1),
    "brass": dict(ramp=[(70, 46, 16), (130, 92, 32), (188, 146, 62), (230, 194, 110), (252, 232, 170)], base=0.6, lit=0.42, noise=0.02, crack=0, min=0),
    "lens": dict(ramp=[(40, 70, 90), (90, 140, 160), (160, 210, 220), (220, 246, 250)], base=0.6, lit=0.4, noise=0.0, crack=0, min=1),
    "wicker": dict(ramp=[(56, 40, 22), (100, 74, 40), (146, 112, 64), (186, 150, 96), (214, 184, 128)], base=0.56, lit=0.3, noise=0.1, crack=0, min=0,
                   stripe=2, axis="u", streak=0.2),
    "leaf": dict(ramp=[(24, 44, 26), (44, 80, 40), (78, 120, 56), (120, 160, 84)], base=0.6, lit=0.3, noise=0.12, crack=0, min=0),
    "leaf2": dict(ramp=[(40, 52, 22), (74, 96, 36), (120, 140, 56), (166, 180, 90)], base=0.6, lit=0.3, noise=0.12, crack=0, min=0),
    "flower": dict(ramp=[(120, 60, 100), (180, 100, 150), (220, 150, 196), (246, 200, 230)], base=0.6, lit=0.3, noise=0.04, crack=0, min=1),
    "sack": dict(ramp=[(66, 58, 44), (104, 94, 72), (142, 130, 100), (176, 164, 130), (200, 190, 156)], base=0.56, lit=0.3, noise=0.14, crack=0.05,
                 min=0, streak=0.16),
    "sack2": dict(ramp=[(60, 40, 30), (100, 68, 48), (140, 100, 70), (170, 130, 94)], base=0.56, lit=0.3, noise=0.12, crack=0, min=0, stripe=2, axis="u"),
    "blanket": dict(ramp=[(60, 22, 20), (110, 40, 34), (150, 64, 50), (184, 96, 72)], base=0.56, lit=0.3, noise=0.08, crack=0, min=0, stripe=2, axis="v"),
}
