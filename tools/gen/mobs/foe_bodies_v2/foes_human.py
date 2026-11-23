# -*- coding: utf-8 -*-
"""人の形の敵 (案 A 作り込み): 沈み船の水夫・熾の番人・灰の打ち手・盗賊の一家・淵の塩の抜け殻・肋の守り・雷の像。"""
from __future__ import annotations

from fb import *  # noqa: F401,F403
import foes as F


def taper_limb(m, pid, bone, mat, pts, rs, n=10):
    """丸い管の腕・脚 (断面は円に近い超楕円)"""
    m.add(pid, limb(bone, mat, [P(*p) for p in pts], [r * 2 for r in rs], [r * 2 for r in rs], n=n))


def boot(m, bone, mat, ank, s=1.0, toe=-1.0, sole_mat=None):
    """足: 甲・つま先・かかと・底"""
    a = P(*ank)
    m.add(f"{bone}_boot", ell(bone, mat, tuple(a + P(0, -0.4, -0.6 * s)), (1.7 * s, 1.5 * s, 2.2 * s), segs=10, rings=3))
    m.add(f"{bone}_toe", ell(bone, mat, tuple(a + P(0, -1.0, toe * 2.2 * s)), (1.5 * s, 1.0 * s, 1.5 * s), segs=8, rings=2))
    m.add(f"{bone}_sole", lof(bone, sole_mat or mat, [ysec(a[1] - 2.0, 3.2 * s, 6.2 * s, zc=a[2] - 1.0 * s), ysec(a[1] - 1.4, 3.4 * s, 6.4 * s, zc=a[2] - 1.0 * s)]))


# ================================================================ 沈み船の水夫 (drowned_sailor)
def drowned_sailor():
    m = start("drowned_sailor", "沈み船の水夫")
    for s, b in ((1, "leg_l"), (-1, "leg_r")):
        taper_limb(m, f"{b}_limb", b, "cloth", [(s * 3.2, 13.6, 0), (s * 3.5, 9.5, -0.5), (s * 3.6, 6.6, -0.9), (s * 3.5, 2.6, 0)], [1.85, 1.75, 1.6, 1.4])
        m.add(f"{b}_knee", ell(b, "patch", (s * 3.6, 7.0, -1.9), (1.3, 1.4, 0.6), segs=8, rings=2))
        m.add(f"{b}_cuff", lof(b, "cloth", [ysec(2.4, 3.6, 3.6, xc=s * 3.5), ysec(3.6, 3.4, 3.4, xc=s * 3.5)]))
        m.add(f"{b}_foot", ell(b, "sea", (s * 3.5, 1.2, -1.0), (1.7, 1.3, 2.6), segs=10, rings=3))
        for k in range(3):
            m.add(f"{b}_toe{k}", ell(b, "sea", (s * 3.5 + (k - 1) * 0.9, 0.6, -3.4), (0.5, 0.55, 0.7), segs=6, rings=2))
        m.add(f"{b}_barn", cone(b, "barn", P(s * 4.9, 9.0, 0.6), (s, 0.2, 0.2), 1.0, 0.8, sides=6, rings=1))
    m.add("hips", ell("body", "cloth", (0, 14, 0), (5.4, 2.8, 3.6), segs=14, rings=4), core=True)
    m.add("torso_c", lof("torso", "cloth", [ysec(14.6, 10.8, 7.2), ysec(16.4, 11.8, 7.8), ysec(19.0, 12.4, 8.3), ysec(22.0, 12.4, 8.2),
                                            ysec(24.4, 11.0, 7.0), ysec(25.6, 6.4, 4.8)]))
    m.add("belly", ell("torso", "sea", (0, 17.8, -3.5), (3.6, 3.3, 1.6), segs=12, rings=4))
    m.add("tear_edge", tub("torso", "cloth", [P(-3.8, 21.0, -4.0), P(-2.6, 17.6, -4.6), P(-0.6, 15.2, -4.4), P(2.2, 15.4, -4.3),
                                              P(3.6, 18.0, -4.3), P(3.0, 20.8, -4.1)], [0.42] * 6, None, sides=5))
    m.add("belt", lof("torso", "leather", [ysec(14.8, 11.4, 7.7), ysec(16.0, 11.4, 7.7)]))
    m.add("buckle", lof("torso", "iron", [ysec(14.7, 2.0, 0.6, zc=-3.95), ysec(16.1, 2.0, 0.6, zc=-3.95)]))
    m.add("rope_l", tub("torso", "rope", [P(4.4, 15.4, -3.4), P(5.4, 13.0, -3.0), P(5.2, 11.6, -2.6)], [0.35, 0.3, 0.25], None, sides=5))
    for k in range(2):
        m.add(f"button{k}", ell("torso", "iron", (3.2, 21.6 + k * 1.8, -4.15), (0.4, 0.4, 0.22), segs=6, rings=1))
    m.add("collar", lof("torso", "cloth", [ysec(24.2, 11.6, 7.6), ysec(25.0, 10.6, 7.0), ysec(25.8, 8.0, 5.8)]))
    m.add("kerchief", lof("torso", "scarf", [ysec(24.6, 8.2, 6.4, zc=-0.4), ysec(25.6, 7.6, 6.0, zc=-0.4)]))
    m.add("kerchief_tail", cone("torso", "scarf", P(-1.2, 24.6, -3.2), (0.2, -1, -0.3), 3.4, 1.1, sides=4, rings=2, bend=(0.4, 0, -0.3)))
    m.add("patch_back", lof("torso", "patch", [ysec(17.0, 5.0, 8.6, xc=1.6, zc=0.3), ysec(20.4, 5.0, 8.7, xc=1.6, zc=0.3)]))
    for k, (x, y, z) in enumerate(((3, 22, -3.9), (-4.4, 18.5, -3.4), (5.2, 24, 1), (-2, 25, 3.6), (4.8, 17, 2.6), (-5.6, 21.0, 1.6), (1.0, 19.4, 4.2))):
        for j in range(2):
            m.add(f"barn{k}{j}", cone("torso", "barn", P(x + j * 0.8, y + j * 0.6, z), (x * 0.08, 0.2, z * 0.08), 1.3 - j * 0.3, 0.95 - j * 0.2, sides=6, rings=1))
    for k, x in enumerate((-4.6, 4.2)):
        m.add(f"kelp_t{k}", tub("torso", "kelp", [P(x, 15.0, -3.0), P(x + 0.4, 12.4, -3.4), P(x - 0.2, 10.4, -3.0)], [0.55, 0.45, 0.2], [0.14] * 3,
                                 hint=(0, 0, 1), sides=4))
    # 頭: ふくれた顔、深い眼窩、垂れた顎、帽子
    m.add("neck", ell("head", "sea", (0, 26.0, -0.4), (2.3, 1.9, 2.3), segs=10, rings=2))
    m.add("headc", ell("head", "sea", (0, 29, -0.6), (3.4, 3.6, 3.4), segs=14, rings=5))
    m.add("jaw", ell("head", "sea", (0, 27.1, -2.0), (2.7, 1.5, 2.1), segs=12, rings=3))
    m.add("mouth", ell("head", "hollow", (0, 27.0, -3.7), (1.2, 0.35, 0.4), segs=8, rings=2))
    m.add("brow", lof("head", "sea", [ysec(30.4, 6.4, 1.6, zc=-3.2), ysec(31.0, 6.0, 1.2, zc=-3.0)]))
    m.add("nose", cone("head", "sea", P(0, 29.6, -3.3), (0, -0.5, -1), 1.3, 0.55, sides=5, rings=1))
    for s in (1, -1):
        m.add(f"cheek{s}", ell("head", "sea", (s * 2.2, 28.0, -2.6), (1.1, 1.0, 0.9), segs=8, rings=2))
        m.add(f"ear{s}", ell("head", "sea", (s * 3.3, 29.0, -0.2), (0.45, 0.9, 0.7), segs=6, rings=2))
        m.add(f"socket{s}", ell("head", "hollow", (s * 1.3, 29.4, -3.2), (0.95, 0.85, 0.6), segs=8, rings=2))
        eye(m, "head", (s * 1.3, 29.4, -3.6), 0.62, (s * 0.2, 0, -1), (170, 255, 240), glow=True)
    m.add("hat", lof("head", "hatc", [ysec(31.2, 9, 9), ysec(31.6, 9.2, 9.2), ysec(32.2, 6.4, 6.4), ysec(34.0, 5.0, 5.0), ysec(34.6, 3.6, 3.6)]))
    m.add("hat_dent", ell("head", "hatc", (0, 34.4, 0), (1.8, 0.5, 1.4), segs=8, rings=2))
    m.add("hatband", lof("head", "leather", [ysec(32.4, 6.6, 6.6), ysec(33.0, 6.2, 6.2)]))
    m.add("kelp_hat", tub("head", "kelp", [P(3.2, 32.0, 2.6), P(4.2, 29.0, 3.6), P(3.6, 26.0, 3.8)], [0.5, 0.4, 0.2], None, sides=4))
    m.add("hat_barn", cone("head", "barn", P(-3.6, 31.6, 2.4), (-0.6, 0.6, 0.4), 1.0, 0.8, sides=6, rings=1))
    # 腕: 袖・袖口・手と指
    taper_limb(m, "arm_l_up", "arm_l", "cloth", [(5.6, 23.8, 0), (7.2, 20.6, -1.0), (8.0, 18.0, -2.0)], [1.75, 1.6, 1.45])
    taper_limb(m, "arm_l_lo", "arm_l", "sea", [(8.0, 18.0, -2.0), (7.2, 15.6, -3.6), (6.4, 14.4, -4.6)], [1.3, 1.15, 1.0])
    m.add("arm_l_cuff", ell("arm_l", "cloth", (7.9, 18.2, -2.1), (1.9, 1.0, 1.9), segs=10, rings=2))
    hand(m, "arm_l", "sea", (6.3, 14.2, -4.8), (-0.1, -1, -0.35), (0, 0, 1), size=1.15, n=4, length=1.6)
    taper_limb(m, "arm_r_up", "arm_r", "cloth", [(-5.6, 23.8, 0), (-7.4, 22.6, -2.0), (-8.0, 22.0, -4.0)], [1.75, 1.6, 1.45])
    taper_limb(m, "arm_r_lo", "arm_r", "sea", [(-8.0, 22.0, -4.0), (-6.8, 23.0, -6.6), (-5.4, 23.6, -8.4)], [1.3, 1.15, 1.0])
    m.add("arm_r_cuff", ell("arm_r", "cloth", (-7.9, 22.1, -4.1), (1.9, 1.9, 1.0), segs=10, rings=2))
    m.add("arm_r_fist", ell("arm_r", "sea", (-5.0, 23.2, -9.2), (1.25, 1.35, 1.3), segs=10, rings=3))
    for k in range(4):
        m.add(f"arm_r_knk{k}", ell("arm_r", "sea", (-4.2 - k * 0.0, 22.2 + k * 0.55 - 0.3, -10.2), (0.4, 0.32, 0.45), segs=6, rings=2))
    for s, b, p in ((1, "arm_l", (7.6, 21.0, 0.2)), (-1, "arm_r", (-7.4, 23.6, -0.8))):
        m.add(f"{b}_barn", cone(b, "barn", P(*p), (s, 0.4, 0.2), 1.0, 0.8, sides=6, rings=1))
    # 銛: 柄・巻き・返しの付いた穂先・綱
    m.add("spear", tub("arm_r", "wood", [P(-5, 22.8, 3), P(-5, 22.8, -22)], [0.55, 0.55], None, sides=8))
    for k, z in enumerate((-8.2, -10.6)):
        m.add(f"wrap{k}", tub("arm_r", "rope", [P(-5, 22.8, z + 0.6), P(-5, 22.8, z - 0.6)], [0.72, 0.72], None, sides=8))
    m.add("ferrule", tub("arm_r", "iron", [P(-5, 22.8, -20.6), P(-5, 22.8, -22.4)], [0.75, 0.7], None, sides=8))
    m.add("tip", cone("arm_r", "iron", P(-5, 22.8, -22.2), (0, 0, -1), 3.4, 1.0, sides=4, rings=1))
    for s in (1, -1):
        m.add(f"barb{s}", cone("arm_r", "iron", P(-5 + s * 0.5, 22.8, -23.0), (s * 0.7, 0, 1), 1.6, 0.35, sides=4, rings=1))
    m.add("line", tub("arm_r", "rope", [P(-5, 22.4, 2.6), P(-5.4, 20.0, 3.6), P(-5.8, 18.0, 3.0)], [0.25] * 3, None, sides=5))
    m.pal = mats(sea=M_(SKINW, streak=0.05), cloth=M_(STRIPE, stripe=4, axis="u"), patch=M_(BROWN, lit=0.3), hatc=M_(STRIPE, lit=0.3), barn=M_(BARN, lit=0.34),
                 wood=M_(WOOD, streak=0.1), iron=M_(IRON, lit=0.4), leather=M_(LEATHR), rope=M_(STRAW, stripe=3, axis="v"), kelp=M_(KELP, streak=0.1),
                 scarf=M_(RED, lit=0.3), hollow=M_(HOLLOW, min=0))
    m.detail = lambda c, col, glow: (speckle(c, col, ["sea"], (30, 90, 80), density=0.08, size=0.8), glow)
    return m


# ================================================================ 熾の番人 (ember_keeper, 灰の僧)
def ember_keeper():
    m = start("ember_keeper", "熾の番人")
    # 上の外套 (body): ひだのある布、胸の合わせ目
    R = [fsec(11.6, 4.0, 3.0, 3.0, folds=9, amp=0.05), fsec(14.5, 3.9, 2.8, 2.9, folds=9, amp=0.06, ph=0.3), fsec(17.6, 3.8, 2.6, 2.8, folds=9, amp=0.05, ph=0.5),
         fsec(20.4, 4.2, 2.5, 2.7, folds=7, amp=0.03), fsec(22.4, 3.8, 2.2, 2.4), fsec(23.6, 2.0, 1.5, 1.7)]
    m.add("cloak", lof("body", "ash", R), core=True)
    M = [fsec(23.9, 2.5, 1.9, 2.1), fsec(23.0, 4.0, 2.7, 2.9, folds=10, amp=0.04), fsec(21.4, 4.8, 3.2, 3.4, folds=10, amp=0.07),
         fsec(20.0, 5.0, 3.35, 3.55, folds=10, amp=0.09, dy=lambda t: 0.35 * math.sin(t * 10))]
    m.add("mantle", lof("body", "ashd", M))
    for k in range(10):
        t = -math.pi * 0.75 + (k + 0.5) / 10 * math.tau
        x, z = math.cos(t) * 5.0, math.sin(t) * (3.35 if math.sin(t) < 0 else 3.55)
        m.add(f"fringe{k}", cone("body", "ashd", P(x * 0.97, 20.4, z * 0.97), (x * 0.04, -1, z * 0.04), 1.3, 0.42, sides=4, rings=1))
    m.add("seam", ell("body", "char", (0, 16.4, -2.62), (0.75, 3.6, 0.45), segs=8, rings=4))
    for k, (x, y, l) in enumerate(((-0.25, 14.8, 1.2), (0.3, 16.6, 1.4), (-0.2, 18.3, 1.0))):
        m.add(f"crack{k}", tub("body", "ember", [P(x - 0.45, y - 0.5 * l, -2.72), P(x + 0.2, y, -2.95), P(x - 0.15, y + 0.5 * l, -2.72)], [0.16, 0.24, 0.16], None, sides=5))
    for k in range(4):
        m.add(f"stitch{k}", tub("body", "rope", [P(-0.9, 15.0 + k * 1.3, -2.68), P(0.9, 15.3 + k * 1.3, -2.68)], [0.12, 0.12], None, sides=4))
    pts = []
    for j in range(17):
        a = -math.pi * 0.75 + j / 16 * math.tau
        pts.append(P(math.cos(a) * 4.1, 12.6 + 0.2 * math.sin(a * 2), math.sin(a) * (3.1 if math.sin(a) < 0 else 3.2)))
    m.add("belt", tub("body", "rope", pts, [0.42] * len(pts), None, sides=6))
    m.add("knot", ell("body", "rope", (0.2, 12.5, -3.3), (0.8, 0.65, 0.55), segs=8, rings=2))
    for k, dx in enumerate((-0.2, 0.6)):
        m.add(f"tassel{k}", tub("body", "rope", [P(dx, 12.2, -3.4), P(dx + 0.3, 10.6, -3.7), P(dx + 0.1, 8.8, -3.6)], [0.3, 0.26, 0.2], None, sides=5))
        m.add(f"tassel_end{k}", cone("body", "rope", P(dx + 0.1, 9.0, -3.6), (0, -1, 0), 1.2, 0.36, sides=6, rings=1))
    m.add("charm", ell("body", "iron", (-2.6, 11.4, -2.9), (0.55, 0.75, 0.25), segs=6, rings=2))
    m.add("charm_str", tub("body", "rope", [P(-2.4, 12.5, -3.05), P(-2.6, 11.9, -3.0)], [0.1, 0.1], None, sides=4))
    # 裾 (hem): ひだが深く、縁が波打つ
    H = [fsec(13.6, 3.85, 2.85, 2.95, n=27, folds=9, amp=0.05), fsec(9.5, 4.4, 3.3, 3.4, n=27, folds=9, amp=0.1, ph=0.2),
         fsec(5.0, 4.9, 3.7, 3.9, n=27, folds=9, amp=0.15, ph=0.4), fsec(1.6, 5.3, 4.0, 4.3, n=27, folds=9, amp=0.17, ph=0.5),
         fsec(0.4, 5.5, 4.1, 4.5, n=27, folds=9, amp=0.18, ph=0.5, dy=lambda t: 0.4 * math.sin(t * 9 + 0.5))]
    m.add("skirt", lof("hem", "ash", H, mats=["ash", "ash", "ash", "ashd"]))
    m.add("hem_band", lof("hem", "ashd", [fsec(1.0, 5.55, 4.15, 4.55, n=27, folds=9, amp=0.18, ph=0.5), fsec(2.2, 5.35, 4.05, 4.4, n=27, folds=9, amp=0.17, ph=0.5)]))
    for k, a in enumerate((150, 205, 30, 300)):
        sa, ca = math.sin(math.radians(a)), math.cos(math.radians(a))
        x, z = 5.3 * sa, (-4.0 if ca > 0 else 4.3) * abs(ca)
        m.add(f"rag{k}", cone("hem", "ashd", P(x * 0.96, 1.9, z * 0.96), (sa * 0.1, -1.0, ca * 0.1), 1.8, 0.6, sides=4, rings=2))
    # 足: 包んだ足先
    for s, b in ((1, "leg_l"), (-1, "leg_r")):
        taper_limb(m, b, b, "char", [(s * 1.8, 12.5, 0), (s * 1.9, 6.5, -0.6), (s * 1.9, 1.6, -1.6)], [1.3, 1.05, 0.9])
        m.add(b + "_foot", ell(b, "wrap", (s * 1.9, 0.8, -3.2), (1.1, 0.85, 2.0), segs=10, rings=3))
        for k in range(3):
            m.add(f"{b}_wrap{k}", tub(b, "rope", [P(s * 1.9 - 1.1, 1.0, -2.4 - k * 0.9), P(s * 1.9 + 1.1, 1.0, -2.4 - k * 0.9)], [0.22, 0.22], None, sides=5))
        m.add(b + "_toe", ell(b, "char", (s * 1.9, 0.6, -4.9), (0.75, 0.5, 0.6), segs=8, rings=2))
    # 頭巾: 高い頭巾、顔の縁の折り返し、奥の虚、熾の目
    m.add("hood", ell("head", "ash", (0, 25.6, 0.4), (2.5, 3.3, 2.6), segs=14, rings=5))
    m.add("hood_peak", cone("head", "ash", P(0, 28.0, 0.8), (0, 1, 0.5), 1.6, 1.3, sides=8, rings=2))
    m.add("face", ell("head", "void", (0, 25.0, -1.85), (1.45, 1.85, 0.65), segs=10, rings=3))
    brim = [P(-1.55, 22.9, -1.7), P(-1.95, 24.8, -2.05), P(-1.4, 26.8, -2.15), P(0, 27.6, -2.2), P(1.4, 26.8, -2.15), P(1.95, 24.8, -2.05), P(1.55, 22.9, -1.7)]
    m.add("brim", tub("head", "ashd", brim, [0.5, 0.58, 0.58, 0.6, 0.58, 0.58, 0.5], None, sides=6, hint=(0, 0, 1)))
    m.add("neck", ell("head", "char", (0, 23.0, 0.2), (1.6, 1.0, 1.5), segs=10, rings=2))
    for s in (1, -1):
        eye(m, "head", (s * 0.6, 25.2, -2.25), 0.24, (s * 0.15, 0, -1), (255, 170, 70), glow=True)
        m.add(f"eyeglow{s}", ell("head", "ember", (s * 0.6, 25.2, -2.2), (0.36, 0.2, 0.2), segs=6, rings=1))
    m.add("tail1", tub("hood_t1", "ash", [P(0, 26.4, 1.0), P(0, 28.0, 2.9), P(0, 28.9, 5.2)], [1.35, 1.15, 0.95], None, sides=8))
    m.add("tail2", tub("hood_t2", "ashd", [P(0, 28.9, 4.9), P(0, 29.3, 7.1), P(0, 28.7, 9.0), P(0, 27.6, 10.2)], [0.95, 0.75, 0.5, 0.25], None, sides=8))
    m.add("tail_tip", cone("hood_t2", "ashd", P(0, 27.7, 10.0), (0, -0.7, 0.6), 1.2, 0.3, sides=6, rings=1))
    # 腕: 袖・広がる袖口・炭の手
    for s, a_, f_ in ((1, "arm_l", "forearm_l"), (-1, "arm_r", "forearm_r")):
        m.add(a_ + "_sh", ell(a_, "ashd", (s * 4.3, 21.6, 0.1), (1.75, 1.65, 1.85), segs=10, rings=3))
        taper_limb(m, a_, a_, "ash", [(s * 4.3, 21.4, 0.1), (s * 4.8, 19.4, -0.4), (s * 5.0, 17.4, -0.9)], [1.35, 1.2, 1.1])
        m.add(f_, tub(f_, "ash", [P(s * 5.0, 17.9, -0.8), P(s * 5.05, 16.4, -1.6), P(s * 5.1, 15.0, -2.4)], [1.1, 1.35, 1.65], None, sides=10))
        m.add(f_ + "_cuff", tub(f_, "ashd", [P(s * 5.1, 15.3, -2.25), P(s * 5.12, 14.75, -2.55)], [1.78, 1.72], None, sides=10))
        m.add(f_ + "_hand", ell(f_, "char", (s * 5.12, 14.2, -2.75), (0.8, 0.95, 0.8), segs=8, rings=3))
    for j, (dx, dz) in enumerate(((-0.45, -0.45), (0.0, -0.6), (0.45, -0.45), (-0.6, 0.25))):
        m.add(f"finger{j}", cone("forearm_r", "char", P(-5.12 + dx * 0.6, 13.7, -2.75 + dz * 0.6), (dx * 0.6, -1.0, dz), 1.4, 0.3, sides=5, rings=2,
                                  bend=(0, 0, -0.2)))
    m.add("palm_glow", ell("forearm_r", "ember", (-5.12, 13.55, -2.75), (0.45, 0.22, 0.45), segs=8, rings=1))
    for j, (dx, dz) in enumerate(((-0.4, -0.5), (0.1, -0.6), (0.5, -0.3))):
        m.add(f"lfinger{j}", cone("forearm_l", "char", P(5.12 + dx * 0.6, 13.8, -2.75 + dz * 0.6), (dx * 0.3, -1.0, dz * 0.4), 0.9, 0.28, sides=5, rings=1))
    # 鎖と灯し
    m.add("grip", ell("chain", "iron", (5.12, 14.0, -2.75), (0.55, 0.35, 0.55), segs=8, rings=2))
    for j in range(4):
        y = 13.6 - j * 0.6
        m.add(f"link{j}", ell("chain", "iron", (5.13, y, -2.76), (0.16 if j % 2 else 0.34, 0.42, 0.34 if j % 2 else 0.16), segs=8, rings=2))
    m.add("lamp_ring", ell("lamp", "iron", (5.15, 11.75, -2.8), (0.35, 0.4, 0.35), segs=8, rings=2))
    m.add("lamp_top", lof("lamp", "soot", [fsec(11.0, 1.2, 1.2, xc=5.15, zc=-2.8), fsec(11.3, 1.15, 1.15, xc=5.15, zc=-2.8), fsec(11.7, 0.5, 0.5, xc=5.15, zc=-2.8)]))
    m.add("lamp_core", ell("lamp", "ember", (5.15, 10.15, -2.8), (0.8, 1.05, 0.8), segs=10, rings=3))
    for j in range(6):
        a = j / 6 * math.tau
        x, z = 5.15 + 0.98 * math.cos(a), -2.8 + 0.98 * math.sin(a)
        m.add(f"bar{j}", tub("lamp", "iron", [P(x, 11.1, z), P(x + 0.1 * math.cos(a), 10.15, z + 0.1 * math.sin(a)), P(x, 9.2, z)], [0.13] * 3, None, sides=4))
    m.add("lamp_mid", ring_band(m, "lamp", 10.15, 5.15, -2.8, 1.1, 0.14))
    m.add("lamp_base", lof("lamp", "soot", [fsec(8.9, 0.6, 0.6, xc=5.15, zc=-2.8), fsec(9.2, 1.05, 1.05, xc=5.15, zc=-2.8), fsec(9.45, 1.0, 1.0, xc=5.15, zc=-2.8)]))
    m.add("lamp_tip", cone("lamp", "iron", P(5.15, 8.95, -2.8), (0, -1, 0), 1.0, 0.35, sides=6, rings=2))
    m.pal = mats(ash=M_(ASH, streak=0.08, lit=0.3), ashd=M_([(48, 46, 46), (70, 66, 64), (96, 90, 86), (124, 116, 110)], streak=0.06), char=M_(CHAR),
                 soot=M_(CHAR, lit=0.3), ember=M_(EMBER, base=0.75, lit=0.15, min=1), void=M_(HOLLOW, min=0), rope=M_(BROWN, stripe=3, axis="v", lit=0.3),
                 wrap=M_(BROWN), iron=M_(IRON, lit=0.4), glow=["ember"])
    m.detail = lambda c, col, glow: (speckle(c, col, ["ash", "ashd"], (60, 58, 56), density=0.015, size=0.5), glow)

    def stages(atlas, glow):
        return {"_s1": stage_tex(atlas, glow, (255, 150, 60), 0.82, 0.05, 1), "_s2": stage_tex(atlas, glow, (255, 120, 40), 0.66, 0.1, 2)}
    m.stages = stages
    return m


# ================================================================ 灰の打ち手 (ash_striker): 前かがみの鍛冶の抜け殻と大槌
def ash_striker():
    m = start("ash_striker", "灰の打ち手")
    T = [fsec(11.6, 2.9, 2.4, 2.4, n=16), fsec(14.0, 3.0, 2.5, 2.4, n=16, zc=-0.1), fsec(16.6, 3.4, 2.7, 2.5, n=16, zc=-0.4),
         fsec(19.6, 4.6, 3.1, 2.9, n=16, zc=-0.9), fsec(22.4, 5.4, 3.3, 3.1, n=16, zc=-1.2), fsec(24.4, 5.0, 3.0, 2.8, n=16, zc=-1.5),
         fsec(25.6, 2.6, 1.8, 1.8, n=16, zc=-1.8)]
    m.add("torso", lof("body", "skin", T), core=True)
    for s in (-1, 1):
        m.add(f"pec{s}", ell("body", "skin", (s * 2.0, 21.6, -3.6), (2.0, 1.6, 1.0), segs=10, rings=3))
        m.add(f"trap{s}", ell("body", "skin", (s * 2.6, 24.4, -1.0), (2.2, 1.2, 2.0), segs=10, rings=3))
    for k in range(3):
        m.add(f"rib_crack{k}", tub("body", "ember", [P(2.8, 17.6 + k * 1.4, -2.6), P(3.4, 18.0 + k * 1.4, -2.3), P(3.6, 17.5 + k * 1.4, -1.8)], [0.12, 0.17, 0.1], None, sides=4))
    # 革の前掛け: 胸から膝まで。縁の縫い目、焦げ、ポケットと鋲
    A = [fsec(5.0, 3.1, 3.3, -1.3, n=16, folds=5, amp=0.04), fsec(8.6, 3.0, 3.25, -1.25, n=16, folds=5, amp=0.035), fsec(12.0, 3.05, 3.05, -1.35, n=16),
         fsec(15.2, 3.15, 3.0, -1.5, n=16, zc=-0.15), fsec(18.0, 3.25, 3.1, -1.6, n=16, zc=-0.35), fsec(21.4, 3.05, 3.45, -2.0, n=16, zc=-0.95)]
    m.add("apron", lof("body", "leather", A))
    m.add("apron_hem", lof("body", "leatherd", [fsec(4.9, 3.15, 3.38, -1.25, n=16, folds=5, amp=0.04), fsec(5.8, 3.12, 3.36, -1.25, n=16, folds=5, amp=0.04)]))
    m.add("pocket", lof("body", "leatherd", [fsec(9.6, 1.6, 3.42, -2.9, n=12, xc=-0.9, zc=0.0), fsec(12.0, 1.6, 3.32, -2.8, n=12, xc=-0.9, zc=0.0)]))
    m.add("tongs_in_pocket", tub("body", "iron", [P(-1.3, 11.0, -3.5), P(-1.4, 14.4, -3.4)], [0.2, 0.18], None, sides=4))
    for k, (x, y) in enumerate(((-2.6, 20.4), (2.6, 20.4), (-2.6, 8.0), (2.6, 8.0))):
        m.add(f"rivet{k}", ell("body", "brass", (x, y, -3.35 if y > 15 else -3.32), (0.3, 0.3, 0.2), segs=6, rings=1))
    for s in (-1, 1):
        m.add(f"strap{s}", tub("body", "leather", [P(s * 2.4, 21.6, -3.7), P(s * 2.9, 25.6, -1.6), P(s * 2.4, 24.0, 1.6), P(s * 1.0, 18.0, 2.6)],
                                [0.5, 0.5, 0.5, 0.45], [0.2] * 4, sides=4, hint=(0, 0, 1)))
    m.add("belt", lof("body", "leatherd", [fsec(12.2, 3.25, 3.15, 2.6, n=16), fsec(13.2, 3.25, 3.15, 2.6, n=16)]))
    m.add("buckle", lof("body", "iron", [ysec(12.0, 1.6, 0.5, zc=-3.25), ysec(13.4, 1.6, 0.5, zc=-3.25)]))
    m.add("rag", cone("body", "cloth", P(2.6, 12.4, 1.8), (0.2, -1, 0.3), 3.6, 0.9, sides=4, rings=2, bend=(0.2, 0, 0.4)))
    # 脚と長靴
    for s, b in ((-1, "leg_l"), (1, "leg_r")):
        taper_limb(m, f"leg{s}", b, "trous", [(s * 1.8, 12.4, 0), (s * 1.95, 7.6, -0.3), (s * 2.0, 3.6, 0.1)], [1.35, 1.2, 1.05])
        m.add(f"bootsh{s}", lof(b, "boot", [ysec(1.4, 2.6, 2.8, xc=s * 2.0, zc=0.0), ysec(5.2, 2.7, 2.8, xc=s * 2.0, zc=0.0), ysec(5.9, 3.0, 3.1, xc=s * 2.0, zc=0.0)]))
        m.add(f"boot{s}", ell(b, "boot", (s * 2.0, 1.0, -0.9), (1.4, 1.1, 2.1), segs=10, rings=3))
        m.add(f"sole{s}", lof(b, "char", [ysec(0.0, 2.8, 4.6, xc=s * 2.0, zc=-0.9), ysec(0.5, 2.9, 4.7, xc=s * 2.0, zc=-0.9)]))
        m.add(f"cap{s}", ell(b, "iron", (s * 2.0, 1.0, -2.4), (1.1, 0.8, 0.8), segs=8, rings=2))
    # 頭: 煤けた頭巾、空ろな顔と朝の光の目
    m.add("face", ell("head", "hollow", (0, 27.4, -3.4), (1.6, 1.9, 1.4), segs=10, rings=3))
    m.add("hood", lof("head", "hood", [fsec(25.2, 2.5, 2.1, 2.5, n=16, zc=-2.4), fsec(27.0, 2.65, 1.9, 2.7, n=16, zc=-2.5), fsec(28.6, 2.55, 1.6, 2.7, n=16, zc=-2.5),
                                       fsec(30.0, 1.9, 0.9, 2.1, n=16, zc=-2.3), fsec(30.8, 0.9, 0.1, 1.1, n=16, zc=-2.2)]))
    face_rim = [P(-1.75, 25.4, -4.0), P(-2.0, 27.4, -4.1), P(-1.3, 29.3, -3.9), P(0, 29.8, -3.8), P(1.3, 29.3, -3.9), P(2.0, 27.4, -4.1), P(1.75, 25.4, -4.0)]
    m.add("hood_rim", tub("head", "hoodd", face_rim, [0.45] * 7, None, sides=6, hint=(0, 0, 1)))
    m.add("cowl", lof("head", "hood", [fsec(24.0, 3.6, 2.6, 3.0, n=16, zc=-1.6), fsec(25.4, 2.8, 2.3, 2.7, n=16, zc=-2.2)]))
    for s in (-1, 1):
        m.add(f"eye{s}", ell("head", "dawn", (s * 0.65, 27.8, -4.55), (0.32, 0.26, 0.2), segs=8, rings=2))
        m.add(f"brow{s}", ell("head", "char", (s * 0.75, 28.4, -4.3), (0.6, 0.18, 0.25), segs=6, rings=1))
    m.add("goggles", tub("head", "leatherd", [P(-2.4, 29.4, -2.4), P(-1.4, 29.6, -3.6), P(0, 29.7, -3.9), P(1.4, 29.6, -3.6), P(2.4, 29.4, -2.4)], [0.3] * 5, None, sides=5))
    for s in (-1, 1):
        m.add(f"lens{s}", tub("head", "brass", [P(s * 0.9, 29.7, -3.6), P(s * 0.9, 29.9, -4.2)], [0.55, 0.55], None, sides=8))
        m.add(f"glass{s}", ell("head", "glass", (s * 0.9, 29.92, -4.25), (0.42, 0.42, 0.1), segs=8, rings=1))
    # 腕: 太い。割れ目の残り火、焦げた手袋、前腕の巻き
    for s, b in ((-1, "arm_l"), (1, "arm_r")):
        m.add(f"delt{s}", ell(b, "skin", (s * 4.9, 23.6, -0.6), (2.0, 1.8, 2.0), segs=10, rings=3))
        taper_limb(m, f"up{s}", b, "skin", [(s * 4.7, 23.6, -0.5), (s * 5.2, 21.0, -0.9), (s * 5.4, 19.0, -1.4)], [1.9, 1.75, 1.5])
        taper_limb(m, f"fo{s}", b, "skin", [(s * 5.4, 19.0, -1.4), (s * 5.4, 17.6, -2.3), (s * 5.2, 16.2, -3.3)], [1.55, 1.6, 1.4])
        m.add(f"wrapf{s}", lof(b, "cloth", [fsec(0, 1.0, 1.0)]) if False else tub(b, "cloth", [P(s * 5.35, 17.9, -2.1), P(s * 5.25, 16.6, -3.1)], [1.62, 1.5], None, sides=10))
        m.add(f"glove{s}", ell(b, "leatherd", (s * 5.2, 15.4, -3.8), (1.45, 1.45, 1.45), segs=10, rings=3))
        for k in range(4):
            m.add(f"knk{s}{k}", ell(b, "leatherd", (s * 5.2 + (k - 1.5) * 0.62 * -s * 0, 14.6 + (k - 1.5) * 0.0, -5.0 + 0 * k) if False else (s * 4.4 + s * k * 0.55, 14.6, -4.9),
                                     (0.36, 0.42, 0.42), segs=6, rings=2))
        m.add(f"crack{s}", tub(b, "ember", [P(s * 6.2, 22.0, -0.8), P(s * 6.6, 20.0, -1.3), P(s * 6.4, 18.2, -2.0)], [0.15, 0.24, 0.15], None, sides=4))
        m.add(f"crackb{s}", tub(b, "ember", [P(s * 6.5, 20.8, -1.0), P(s * 7.0, 20.4, -0.4)], [0.12, 0.08], None, sides=4))
    # 大槌: 柄 (木目と巻き)、鉄の頭 (面取り・帯・鋲)、打ち面の残り火
    m.add("haft", tub("hammer", "wood", [P(5.2, 11.6, -2.9), P(5.2, 27.0, -7.4)], [0.62, 0.55], None, sides=8))
    m.add("pommel", ell("hammer", "iron", (5.2, 11.4, -2.85), (0.8, 0.55, 0.8), segs=8, rings=2))
    for k in range(4):
        t = 0.1 + k * 0.06
        a, b_ = P(5.2, 11.6, -2.9), P(5.2, 27.0, -7.4)
        p0, p1 = a + (b_ - a) * t, a + (b_ - a) * (t + 0.035)
        m.add(f"grip{k}", tub("hammer", "leatherd", [p0, p1], [0.78, 0.78], None, sides=8))
    m.add("head1", lof("hammer", "iron", [[P(2.4, 27.6 + y, -7.6 + z) for y, z in oct2(2.1, 1.9)], [P(3.0, 27.6 + y, -7.6 + z) for y, z in oct2(2.3, 2.1)],
                                          [P(7.4, 27.6 + y, -7.6 + z) for y, z in oct2(2.3, 2.1)], [P(8.0, 27.6 + y, -7.6 + z) for y, z in oct2(2.1, 1.9)]]))
    m.add("socket", tub("hammer", "iron", [P(5.2, 24.4, -6.8), P(5.2, 25.8, -7.2)], [0.95, 1.0], None, sides=8))
    for x in (3.4, 7.0):
        m.add(f"hband{x}", lof("hammer", "char", [[P(x, 27.6 + y, -7.6 + z) for y, z in oct2(2.42, 2.22)], [P(x + 0.5, 27.6 + y, -7.6 + z) for y, z in oct2(2.42, 2.22)]]))
    for k, (y, z) in enumerate(((1.2, -2.2), (-1.2, -2.2), (1.2, 2.2), (-1.2, 2.2))):
        m.add(f"hrivet{k}", ell("hammer", "brass", (5.2, 27.6 + y, -7.6 + z * 0.98), (0.3, 0.3, 0.3), segs=6, rings=1))
    m.add("face_l", ell("hammer", "ember", (2.3, 27.6, -7.6), (0.28, 1.65, 1.5), segs=10, rings=3))
    m.add("face_r", ell("hammer", "iron", (8.1, 27.6, -7.6), (0.25, 1.5, 1.35), segs=8, rings=2))
    m.pal = mats(skin=M_(ASHSKIN, streak=0.06, crack=0.08), leather=M_(LEATHR, streak=0.08), leatherd=M_([(30, 20, 14), (50, 34, 22), (72, 50, 32), (96, 70, 46)]),
                 trous=M_(TROUS, stripe=3, axis="v", lit=0.28), boot=M_(BROWN), char=M_(CHAR), iron=M_(IRON, lit=0.42), brass=M_(BRASSY, lit=0.4),
                 wood=M_(WOOD, streak=0.12), cloth=M_(ASH, streak=0.08), hood=M_([(56, 44, 36), (82, 64, 50), (110, 88, 68), (138, 114, 90)], streak=0.06),
                 hoodd=M_([(36, 28, 24), (56, 44, 36), (78, 62, 50), (100, 82, 66)]), hollow=M_(HOLLOW, min=0), dawn=M_(DAWN, base=0.8, lit=0.1, min=1),
                 ember=M_(EMBER, base=0.75, lit=0.15, min=1), glass=M_([(40, 60, 70), (70, 100, 110), (120, 150, 160), (180, 210, 214)], lit=0.5),
                 glow=["dawn", "ember"])
    m.detail = lambda c, col, glow: (speckle(c, col, ["skin"], (60, 58, 56), density=0.03, size=0.6), glow)

    def stages(atlas, glow):
        return {"_s1": stage_tex(atlas, glow, (255, 170, 80), 0.84, 0.05, 3), "_s2": stage_tex(atlas, glow, (255, 130, 50), 0.68, 0.1, 4)}
    m.stages = stages
    return m


def oct2(hy, hz, cut=0.32):
    """縦横 (y・z) の面取りの八角形の点 (x の向きの柱の断面)。x→ の向きで外を向く順"""
    k = cut * min(hy, hz)
    pts = [(-hy + k, -hz), (hy - k, -hz), (hy, -hz + k), (hy, hz - k), (hy - k, hz), (-hy + k, hz), (-hy, hz - k), (-hy, -hz + k)]
    return pts


def ring_band(m, bone, y, cx, cz, r, tr, n=14):
    """横の輪 (籠の帯) の面を返す"""
    pts = [P(cx + math.cos(i / n * math.tau) * r, y, cz + math.sin(i / n * math.tau) * r) for i in range(n + 1)]
    return tub(bone, "iron", pts, [tr] * len(pts), None, sides=4)


ALL = {f.__name__: f for f in (drowned_sailor, ember_keeper, ash_striker)}
