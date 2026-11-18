# -*- coding: utf-8 -*-
"""盗賊の一家 (案 A 作り込み): 野盗・野盗の弓手・野盗の頭目。体は共通 (bandit_body)、服と持ち物で分ける。前 = -Z。"""
from __future__ import annotations

from fb import *  # noqa: F401,F403
from foes_human import taper_limb


def bandit_body(m, s=1.0, bulk=1.0, shirt="cloth", pants="pants", boots="boot", fist_r=True):
    """盗賊の体。胴着の下の服・腰・脚・長靴・腕と手。s = 背の倍率、bulk = 太さの倍率"""
    b = bulk
    m.add("torso", lof("body", shirt, [fsec(11.6 * s, 3.9 * b, 2.5 * b, 2.5 * b, n=16), fsec(14.0 * s, 3.9 * b, 2.5 * b, 2.4 * b, n=16),
                                        fsec(17.0 * s, 4.3 * b, 2.75 * b, 2.6 * b, n=16), fsec(20.0 * s, 4.7 * b, 2.85 * b, 2.7 * b, n=16),
                                        fsec(22.2 * s, 4.4 * b, 2.5 * b, 2.5 * b, n=16), fsec(23.4 * s, 1.9 * b, 1.5 * b, 1.6 * b, n=16)]), core=True)
    for sx in (1, -1):
        m.add(f"delt{sx}", ell("body", shirt, (sx * 4.6 * b, 21.6 * s, 0), (1.9 * b, 1.6 * b, 2.0 * b), segs=10, rings=3))
    m.add("hips", lof("body", pants, [fsec(9.8 * s, 3.6 * b, 2.3 * b, 2.3 * b, n=16), fsec(11.4 * s, 4.0 * b, 2.55 * b, 2.55 * b, n=16),
                                       fsec(12.6 * s, 3.9 * b, 2.5 * b, 2.5 * b, n=16)]))
    m.add("neck", tub("head", "skin", [P(0, 22.4 * s, 0.2), P(0, 25.2 * s, 0.2)], [1.35, 1.2], None, sides=10))
    for sx, leg in ((1, "leg_l"), (-1, "leg_r")):
        x = sx * 2.1 * min(b, 1.1)
        xb = x + sx * 0.1
        taper_limb(m, leg + "_up", leg, pants, [(x, 11.8 * s, 0), (xb, 8.6 * s, -0.2), (xb, 6.0 * s, -0.3)], [1.62 * b, 1.48 * b, 1.36 * b])
        taper_limb(m, leg + "_lo", leg, pants, [(xb, 6.2 * s, -0.3), (xb, 3.6 * s, -0.1)], [1.36 * b, 1.25 * b])
        m.add(leg + "_knee", ell(leg, pants, (xb, 6.2 * s, -1.0), (1.2 * b, 1.3, 0.7), segs=8, rings=2))
        m.add(leg + "_shaft", lof(leg, boots, [fsec(1.0, 1.45 * b, 1.55, 1.45, n=14, xc=xb), fsec(3.6 * s, 1.5 * b, 1.55, 1.5, n=14, xc=xb),
                                               fsec(4.4 * s, 1.75 * b, 1.75, 1.7, n=14, xc=xb, dy=lambda t: 0.25 * math.sin(t))]))
        m.add(leg + "_foot", ell(leg, boots, (xb, 1.0, -1.0), (1.45 * b, 1.1, 2.2), segs=10, rings=3))
        m.add(leg + "_sole", lof(leg, "dark", [fsec(0.0, 1.5 * b, 3.3, 1.6, n=14, xc=xb, zc=-0.6), fsec(0.5, 1.55 * b, 3.35, 1.65, n=14, xc=xb, zc=-0.6)]))
        m.add(leg + "_strap", tub(leg, "dark", [P(xb - 1.6, 2.4, -0.4), P(xb + 1.6, 2.4, -0.4)], [0.28, 0.28], None, sides=6))
        m.add(leg + "_buckle", ell(leg, "iron", (xb + sx * 1.55, 2.4, -0.4), (0.25, 0.35, 0.35), segs=6, rings=1))
    for sx, arm, fore in ((1, "left_arm", "left_forearm"), (-1, "right_arm", "right_forearm")):
        sh, el, wr = P(sx * 5.4 * b, 22.0 * s, 0), P(sx * 5.8 * b, 16.6 * s, 0), P(sx * 6.0 * b, 11.6 * s, -0.4)
        taper_limb(m, arm, arm, shirt, [tuple(sh), tuple((sh + el) / 2), tuple(el)], [1.4 * b, 1.32 * b, 1.22 * b])
        m.add(arm + "_elbow", ell(fore, shirt, tuple(el), (1.25 * b, 1.25 * b, 1.25 * b), segs=10, rings=3))
        taper_limb(m, fore, fore, shirt, [tuple(el), tuple((el + wr) / 2), tuple(wr)], [1.22 * b, 1.18 * b, 1.02 * b])
        hc = P(sx * 6.0 * b, 10.5 * s, -0.5)
        if sx < 0 and fist_r:
            m.add(fore + "_fist", ell(fore, "skin", tuple(hc), (1.05, 1.2, 1.1), segs=10, rings=3))
            for k in range(4):
                m.add(f"{fore}_knk{k}", ell(fore, "skin", tuple(hc + P(0, 0.9 - k * 0.6, -1.0)), (0.42, 0.34, 0.42), segs=6, rings=2))
            m.add(fore + "_thumb", ell(fore, "skin", tuple(hc + P(-sx * 0.2, 0.9, -0.9)), (0.4, 0.55, 0.4), segs=6, rings=2))
        else:
            hand(m, fore, "skin", tuple(hc + P(0, 0.7, 0)), (0, -1, -0.15), (0, 0, 1), size=1.0, n=4, length=1.3)


def face_basic(m, s=1.0, z=-3.0):
    """顔の彫り: 眉・眼窩・目・鼻・耳"""
    for sx in (1, -1):
        m.add(f"socket{sx}", ell("head", "skind", (sx * 1.15, 27.9 * s, z + 0.15), (0.75, 0.42, 0.3), segs=8, rings=2))
        m.add(f"eye{sx}", ell("head", "dark", (sx * 1.15, 27.9 * s, z - 0.02), (0.45, 0.26, 0.2), segs=6, rings=1))
        m.add(f"brow{sx}", ell("head", "hair", (sx * 1.15, 28.55 * s, z + 0.05), (0.85, 0.24, 0.32), segs=6, rings=1, rz=-sx * 8))
        m.add(f"ear{sx}", ell("head", "skin", (sx * 3.05, 27.6 * s, 0.2), (0.45, 0.85, 0.65), segs=6, rings=2))
    m.add("nose", cone("head", "skin", P(0, 28.0 * s, z + 0.1), (0, -0.6, -1), 1.2, 0.5, sides=5, rings=1))


STEEL_L = [(170, 176, 184), (200, 206, 212), (228, 232, 236), (246, 248, 250)]


def bandit_pal(**extra):
    base = dict(skin=M_(SKIN), skind=M_([(80, 50, 38), (110, 74, 56), (140, 100, 76), (164, 124, 96)]), hair=M_([(30, 22, 16), (46, 34, 24), (64, 48, 34), (84, 64, 46)]),
                cloth=M_([(70, 56, 40), (98, 80, 58), (126, 104, 76), (154, 130, 98)], streak=0.06), pants=M_(TROUS, stripe=3, axis="v", lit=0.28),
                boot=M_(BROWN, lit=0.3), leather=M_(LEATHR, streak=0.08), dark=M_(CHAR), iron=M_(STEEL, lit=0.45), wood=M_(WOOD, streak=0.12),
                rope=M_(STRAW, stripe=3, axis="v", lit=0.26), mask=M_(RED, lit=0.3), hood=M_(BROWN, streak=0.06), steel_l=M_(STEEL_L, lit=0.5))
    base.update(extra)
    return mats(**base)


def belt_pts(r, y, rz, n=17, wav=0.15):
    return [P(math.cos(a) * r, y + wav * math.sin(a * 2), math.sin(a) * rz) for a in np.linspace(-math.pi * 0.75, math.pi * 1.25, n)]


def bandit():
    m = start("bandit", "野盗")
    bandit_body(m)
    m.add("head", ell("head", "skin", (0, 27.6, 0), (3.0, 3.3, 3.1), segs=14, rings=5))
    face_basic(m, z=-2.95)
    m.add("mask", lof("head", "mask", [fsec(24.8, 3.05, 3.1, 3.05, n=16, folds=5, amp=0.03), fsec(26.0, 3.2, 3.3, 3.15, n=16), fsec(27.2, 3.12, 3.22, 3.1, n=16)]))
    m.add("mask_knot", ell("head", "mask", (0, 26.4, 3.25), (0.75, 0.6, 0.5), segs=8, rings=2))
    for k, dx in enumerate((-0.4, 0.5)):
        m.add(f"mask_tail{k}", cone("head", "mask", P(dx, 26.2, 3.4), (dx * 0.4, -1, 0.5), 2.6, 0.5, sides=4, rings=2, bend=(0, 0, 0.4)))
    m.add("hood", ell("head", "hood", (0, 28.5, 0.5), (3.6, 3.5, 3.6), segs=14, rings=5))
    m.add("hood_brim", tub("head", "hood", [P(-3.3, 26.6, -1.6), P(-3.0, 28.8, -2.9), P(0, 30.4, -3.55), P(3.0, 28.8, -2.9), P(3.3, 26.6, -1.6)],
                            [0.55, 0.65, 0.7, 0.65, 0.55], None, sides=6, hint=(0, 0, 1)))
    m.add("hood_tail", tub("head", "hood", [P(0, 30.6, 2.6), P(0, 29.6, 5.4), P(0.4, 26.6, 7.2), P(0.8, 24.2, 7.5)], [1.8, 1.4, 0.9, 0.35], None, sides=8))
    # 胴着 (前が開いた革)、縄の帯、斜めの革の帯と袋
    for sx in (1, -1):
        m.add(f"vest{sx}", lof("body", "leather", [fsec(12.6, 2.4, 2.8, 2.75, n=12, xc=sx * 2.2), fsec(17.0, 2.6, 3.0, 2.9, n=12, xc=sx * 2.3),
                                                    fsec(20.0, 2.75, 3.1, 3.0, n=12, xc=sx * 2.5), fsec(22.4, 2.4, 2.75, 2.7, n=12, xc=sx * 2.5)]))
        m.add(f"vest_sh{sx}", ell("body", "leather", (sx * 4.3, 21.9, 0), (2.0, 1.25, 2.2), segs=10, rings=3))
    for k in range(3):
        m.add(f"lace{k}", tub("body", "rope", [P(-0.9, 15.4 + k * 2.0, -2.95), P(0.9, 16.2 + k * 2.0, -2.95)], [0.13, 0.13], None, sides=4))
    pts = belt_pts(4.15, 13.7, 2.75)
    m.add("belt", tub("body", "rope", pts, [0.5] * len(pts), None, sides=6))
    m.add("belt_knot", ell("body", "rope", (-1.6, 13.6, -2.85), (0.7, 0.55, 0.45), segs=8, rings=2))
    m.add("sash", tub("body", "leather", [P(4.3, 22.2, -2.2), P(1.6, 19.6, -3.25), P(-1.6, 16.6, -3.15), P(-4.1, 14.4, -2.3)], [0.75] * 4, [0.25] * 4, sides=4, hint=(0, 0, 1)))
    m.add("sash_back", tub("body", "leather", [P(4.3, 22.2, -2.2), P(3.0, 23.0, 1.8), P(-1.0, 18.4, 3.0), P(-4.1, 14.4, 2.4)], [0.75] * 4, [0.25] * 4, sides=4, hint=(0, 0, 1)))
    m.add("pouch", lof("body", "leather", [ysec(11.4, 2.1, 1.5, xc=3.5, zc=-2.3), ysec(13.4, 2.2, 1.6, xc=3.5, zc=-2.3), ysec(13.9, 2.0, 1.4, xc=3.5, zc=-2.3)]))
    m.add("pouch_flap", lof("body", "dark", [ysec(12.9, 2.3, 1.7, xc=3.5, zc=-2.35), ysec(13.9, 2.25, 1.65, xc=3.5, zc=-2.35)]))
    m.add("flask", ell("body", "wood", (4.4, 12.0, 0.8), (1.0, 1.6, 1.0), segs=10, rings=3))
    m.add("flask_cap", tub("body", "dark", [P(4.4, 13.4, 0.8), P(4.4, 14.2, 0.8)], [0.4, 0.35], None, sides=6))
    for sx, fore in ((1, "left_forearm"), (-1, "right_forearm")):
        for y in (15.0, 13.4):
            m.add(f"{fore}_wrap{y}", tub(fore, "cloth", [P(sx * 5.9, y, -0.2), P(sx * 5.95, y - 0.9, -0.3)], [1.35, 1.3], None, sides=10))
    for sx, leg in ((1, "leg_l"), (-1, "leg_r")):
        m.add(leg + "_rag", lof(leg, "pants", [fsec(4.0, 1.55, 1.55, 1.5, n=12, folds=4, amp=0.08, xc=sx * 2.2, zc=-0.2),
                                               fsec(5.0, 1.5, 1.5, 1.45, n=12, xc=sx * 2.2, zc=-0.2, dy=lambda t: 0.3 * math.sin(t * 3))]))
        m.add(leg + "_patch", ell(leg, "cloth", (sx * 2.5, 8.6, -1.5), (0.9, 1.0, 0.3), segs=6, rings=2))
    # 鉈: 柄の巻き、刃の背と刃こぼれ
    m.add("handle", tub("weapon", "wood", [P(-6.0, 11.6, -0.5), P(-6.0, 7.8, -0.5)], [0.45, 0.45], None, sides=8))
    m.add("pommel", ell("weapon", "iron", (-6.0, 11.7, -0.5), (0.55, 0.4, 0.55), segs=8, rings=2))
    m.add("guard", lof("weapon", "iron", [ysec(7.6, 0.6, 1.6, xc=-6.0, zc=-0.8), ysec(8.0, 0.6, 1.6, xc=-6.0, zc=-0.8)]))
    blade = [[P(-6.0 + dx, y0, z) for dx, z in ((-0.06, z0), (0.06, z0), (0.2, z0 - 0.4), (0.2, z1 + 0.5), (0.06, z1), (-0.06, z1), (-0.2, z1 + 0.5), (-0.2, z0 - 0.4))]
             for y0, z0, z1 in ((7.7, -0.1, -2.2), (5.0, -0.1, -2.6), (2.2, -0.1, -2.9), (1.0, -0.3, -2.6))]
    m.add("cleaver", lof("weapon", "iron", blade))
    m.add("edge", tub("weapon", "steel_l", [P(-6.0, 7.5, -2.25), P(-6.0, 5.0, -2.65), P(-6.0, 2.2, -2.95), P(-6.0, 1.15, -2.65)], [0.12] * 4, None, sides=4))
    for k, y in enumerate((6.0, 3.6)):
        m.add(f"nick{k}", ell("weapon", "dark", (-6.0, y, -2.7), (0.3, 0.35, 0.25), segs=6, rings=1))
    m.pal = bandit_pal()
    m.detail = None
    return m


def bandit_archer():
    m = start("bandit_archer", "野盗の弓手")
    bandit_body(m, bulk=0.92, fist_r=False)
    m.add("head", ell("head", "skin", (0, 27.6, 0), (2.95, 3.25, 3.05), segs=14, rings=5))
    face_basic(m, z=-2.9)
    m.add("scarf", lof("head", "mask", [fsec(24.4, 3.0, 3.0, 3.0, n=16, folds=6, amp=0.04), fsec(25.6, 3.25, 3.25, 3.2, n=16, folds=6, amp=0.04),
                                        fsec(26.6, 3.05, 3.1, 3.0, n=16)]))
    m.add("scarf_tail", tub("head", "mask", [P(1.6, 25.0, 2.6), P(2.4, 22.6, 3.6), P(2.0, 20.2, 3.8)], [0.7, 0.6, 0.3], [0.25, 0.22, 0.15], sides=4, hint=(1, 0, 0)))
    # 編み笠: つばの広い円錐、編み目の段、てっぺんの結び、顎紐
    hat = [fsec(29.4, 7.0, 7.0, n=24), fsec(29.9, 7.1, 7.1, n=24), fsec(30.3, 6.4, 6.4, n=24), fsec(31.6, 3.6, 3.6, n=24), fsec(32.6, 1.4, 1.4, n=24), fsec(32.9, 0.4, 0.4, n=24)]
    m.add("hat", lof("head", "straw", hat))
    for k, (y, r) in enumerate(((30.6, 5.6), (31.2, 4.4))):
        m.add(f"hat_row{k}", lof("head", "strawd", [fsec(y - 0.15, r + 0.12, r + 0.12, n=24), fsec(y + 0.15, r - 0.2, r - 0.2, n=24)]))
    m.add("hat_knot", ell("head", "rope", (0, 33.0, 0), (0.6, 0.45, 0.6), segs=8, rings=2))
    m.add("hat_rim", lof("head", "strawd", [fsec(29.3, 7.15, 7.15, n=24, dy=lambda t: 0.12 * math.sin(t * 11)), fsec(29.7, 7.2, 7.2, n=24, dy=lambda t: 0.12 * math.sin(t * 11))]))
    for sx in (1, -1):
        m.add(f"strap{sx}", tub("head", "rope", [P(sx * 3.0, 29.4, -0.4), P(sx * 2.6, 27.2, -1.2), P(sx * 1.6, 25.4, -2.2)], [0.15] * 3, None, sides=4))
    # 片掛けの外套、帯、背の矢筒
    m.add("cloak", lof("body", "cloak", [fsec(14.0, 4.3, 2.95, 3.1, n=18, folds=6, amp=0.06, xc=0.6, zc=0.2), fsec(17.0, 4.5, 3.0, 3.15, n=18, folds=6, amp=0.05, xc=0.6),
                                         fsec(20.0, 4.7, 3.0, 3.1, n=18, folds=4, amp=0.03, xc=0.7), fsec(22.6, 4.0, 2.6, 2.8, n=18, xc=0.5)]))
    m.add("cloak_hem", lof("body", "cloak", [fsec(12.0, 4.4, 2.9, 3.4, n=18, folds=6, amp=0.1, xc=0.6, zc=0.6, dy=lambda t: 0.4 * math.sin(t * 6)),
                                             fsec(14.2, 4.35, 3.0, 3.2, n=18, folds=6, amp=0.06, xc=0.6, zc=0.2)]))
    m.add("brooch", ell("body", "iron", (3.0, 21.6, -2.75), (0.55, 0.55, 0.25), segs=8, rings=2))
    pts = belt_pts(4.0, 13.5, 2.65, wav=0.0)
    m.add("belt", tub("body", "leather", pts, [0.45] * len(pts), None, sides=6))
    m.add("belt_buckle", lof("body", "iron", [ysec(13.0, 1.3, 0.5, zc=-2.75), ysec(14.0, 1.3, 0.5, zc=-2.75)]))
    m.add("knife", tub("body", "dark", [P(-3.2, 14.0, -1.8), P(-3.4, 10.6, -1.8)], [0.4, 0.3], None, sides=6))
    q0, q1 = P(-1.6, 13.4, 3.3), P(1.2, 21.8, 3.7)
    m.add("quiver", tub("body", "leather", [q0, q1], [1.3, 1.45], None, sides=10))
    for k, t in enumerate((0.2, 0.75)):
        q = q0 + (q1 - q0) * t
        m.add(f"quiver_band{k}", tub("body", "dark", [q - (q1 - q0) * 0.03, q + (q1 - q0) * 0.03], [1.5, 1.52], None, sides=10))
    m.add("quiver_strap", tub("body", "leather", [P(1.6, 21.4, 3.4), P(3.6, 22.4, 0.4), P(2.0, 20.0, -2.9), P(-2.4, 15.6, -2.8), P(-3.6, 14.2, 1.4)],
                               [0.6] * 5, [0.2] * 5, sides=4, hint=(0, 0, 1)))
    for k, dx in enumerate((-0.4, 0.4, 1.1)):
        m.add(f"arrow{k}", tub("body", "wood", [P(1.0 + dx, 21.4, 3.6), P(1.4 + dx, 24.2, 3.7)], [0.15, 0.15], None, sides=5))
        for j, a in enumerate((0, 2.1, 4.2)):
            m.add(f"fletch{k}{j}", cone("body", "feather", P(1.4 + dx, 23.6, 3.7), (math.cos(a) * 0.6, 1, math.sin(a) * 0.6), 1.0, 0.45, sides=3, rings=1, rb=0.08))
    for sx, leg in ((1, "leg_l"), (-1, "leg_r")):
        for y in (4.6, 3.0):
            m.add(f"{leg}_wrap{y}", tub(leg, "cloth", [P(sx * 2.2, y, -0.2), P(sx * 2.2, y - 1.0, -0.2)], [1.42, 1.38], None, sides=10))
    # 左手の短い反り弓 (握り・弦・端の角)
    bow = [P(6.2, 17.2, -1.2), P(6.6, 14.6, -1.8), P(6.0, 10.6, -1.6), P(6.6, 6.6, -1.8), P(6.2, 4.0, -1.2)]
    m.add("bow", tub("offhand", "wood", bow, [0.25, 0.42, 0.5, 0.42, 0.25], [0.2, 0.3, 0.45, 0.3, 0.2], sides=6, hint=(0, 0, 1)))
    m.add("bow_grip", tub("offhand", "leather", [P(6.05, 11.6, -1.62), P(6.05, 9.6, -1.62)], [0.62, 0.62], None, sides=8))
    for y in (17.2, 4.0):
        m.add(f"bow_tip{y}", cone("offhand", "bone", P(6.2, y, -1.2), (0, 1 if y > 10 else -1, 0.4), 0.9, 0.28, sides=5, rings=1))
    m.add("string", tub("offhand", "rope", [P(6.2, 17.0, -1.0), P(6.2, 4.2, -1.0)], [0.07, 0.07], None, sides=4))
    m.pal = bandit_pal(straw=M_(STRAW, lit=0.3), strawd=M_([(100, 76, 36), (140, 110, 56), (176, 144, 80), (204, 176, 110)]), cloak=M_(GREEN, streak=0.06),
                       feather=M_([(160, 150, 140), (196, 188, 178), (226, 220, 212), (246, 242, 236)]), bone=M_(BONE),
                       mask=M_([(80, 40, 30), (120, 60, 44), (156, 86, 62), (186, 116, 86)]))
    m.detail = None
    return m


def bandit_chief():
    m = start("bandit_chief", "野盗の頭目")
    S = 1.2
    bandit_body(m, s=S, bulk=1.15)
    m.add("head", ell("head", "skin", (0, 27.6 * S, 0), (3.2, 3.4, 3.3), segs=14, rings=5))
    for sx in (1, -1):
        m.add(f"ear{sx}", ell("head", "skin", (sx * 3.15, 27.6 * S, 0.2), (0.45, 0.85, 0.65), segs=6, rings=2))
        m.add(f"brow{sx}", ell("head", "hair", (sx * 1.2, 28.6 * S, -3.0), (0.9, 0.26, 0.34), segs=6, rings=1, rz=-sx * 10))
    m.add("nose", cone("head", "skin", P(0, 28.1 * S, -3.1), (0, -0.6, -1), 1.3, 0.55, sides=5, rings=1))
    m.add("socket_r", ell("head", "skind", (-1.2, 28.0 * S, -2.95), (0.75, 0.42, 0.3), segs=8, rings=2))
    m.add("eye_r", ell("head", "dark", (-1.2, 28.0 * S, -3.08), (0.45, 0.26, 0.2), segs=6, rings=1))
    m.add("scar", tub("head", "skind", [P(-1.9, 29.4 * S, -2.95), P(-1.3, 28.0 * S, -3.2), P(-0.8, 26.8 * S, -3.15)], [0.12] * 3, None, sides=4))
    m.add("patch", ell("head", "dark", (1.2, 28.0 * S, -3.05), (0.95, 0.75, 0.4), segs=8, rings=2))
    m.add("patch_strap", tub("head", "dark", [P(-3.2, 28.6 * S, -1.0), P(-1.8, 28.9 * S, -2.8), P(0, 28.6 * S, -3.3), P(1.8, 28.2 * S, -3.1), P(3.2, 27.8 * S, -1.0)],
                              [0.15] * 5, None, sides=4))
    m.add("beard", ell("head", "fur", (0, 25.6 * S, -2.0), (2.4, 1.9, 1.4), segs=10, rings=3))
    m.add("mous", tub("head", "fur", [P(-1.5, 26.5 * S, -2.85), P(0, 27.0 * S, -3.25), P(1.5, 26.5 * S, -2.85)], [0.3, 0.42, 0.3], None, sides=6))
    for k, dx in enumerate((-0.8, 0.8)):
        m.add(f"braid{k}", tub("head", "fur", [P(dx, 24.6 * S, -2.4), P(dx * 1.2, 23.4 * S, -2.6), P(dx * 1.1, 22.4 * S, -2.5)], [0.42, 0.34, 0.2], None, sides=6))
        m.add(f"bead{k}", ell("head", "iron", (dx * 1.15, 23.1 * S, -2.6), (0.3, 0.35, 0.3), segs=6, rings=1))
    # 古い兜: 帯・鋲・鼻当て、片方の折れた角
    m.add("helm", ell("head", "iron", (0, 29.0 * S, 0.2), (3.6, 2.8, 3.7), segs=14, rings=4))
    m.add("helm_rim", lof("head", "irond", [fsec(28.0 * S - 0.4, 3.75, 3.85, 3.85, n=20), fsec(28.0 * S + 0.3, 3.65, 3.75, 3.8, n=20)]))
    m.add("helm_crest", tub("head", "irond", [P(0, 30.0 * S, -3.6), P(0, 31.7 * S, -1.0), P(0, 31.8 * S, 1.4), P(0, 30.6 * S, 3.4)], [0.35] * 4, [0.5] * 4, sides=4, hint=(1, 0, 0)))
    m.add("nasal", tub("head", "irond", [P(0, 29.6 * S, -3.75), P(0, 27.8 * S, -3.6)], [0.3, 0.25], [0.15, 0.12], sides=4, hint=(1, 0, 0)))
    for k in range(6):
        a = -math.pi * 0.75 + k / 6 * math.tau
        m.add(f"hrivet{k}", ell("head", "brass", (math.cos(a) * 3.82, 28.0 * S, math.sin(a) * 3.9), (0.25, 0.25, 0.25), segs=6, rings=1))
    m.add("horn_l", cone("head", "rock", P(3.0, 30.4 * S, 0.4), (0.8, 0.9, 0.2), 4.6, 0.9, sides=7, rings=3, bend=(0.2, 0.6, 0.6)))
    m.add("horn_r", cone("head", "rock", P(-3.0, 30.4 * S, 0.4), (-0.8, 0.7, 0.2), 1.6, 0.9, sides=7, rings=1))
    # 胴: 打ち付けた鉄板 (鋲・へこみ)、毛皮の肩掛け (狼の頭)、赤い腰帯
    m.add("chest", lof("body", "iron", [fsec(15.6 * S, 3.6, 3.55, -1.6, n=16, zc=-0.2), fsec(19.0 * S, 4.1, 3.85, -1.7, n=16, zc=-0.3), fsec(22.0 * S, 3.8, 3.7, -1.7, n=16, zc=-0.2)]))
    for x, y in ((-2.4, 21.6), (2.4, 21.6), (-2.2, 16.2), (2.2, 16.2), (0, 21.9)):
        m.add(f"rivet{x}{y}", ell("body", "brass", (x, y * S, -3.85), (0.32, 0.32, 0.22), segs=6, rings=1))
    m.add("dent", ell("body", "irond", (1.0, 18.6 * S, -4.05), (0.8, 0.6, 0.15), segs=6, rings=1))
    m.add("mantle", lof("body", "fur", [fsec(23.6 * S, 3.2, 2.4, 2.6, n=20), fsec(22.8 * S, 5.6, 3.4, 3.9, n=20, folds=10, amp=0.06),
                                         fsec(21.0 * S, 6.2, 3.6, 4.3, n=20, folds=10, amp=0.1, dy=lambda t: 0.5 * math.sin(t * 10))]))
    for k in range(8):
        a = -math.pi * 0.2 + k / 8 * math.pi * 1.4
        m.add(f"fur_tuft{k}", cone("body", "fur", P(math.cos(a) * 5.8, 21.4 * S, math.sin(a) * 4.0 + 0.2), (math.cos(a) * 0.3, -1, math.sin(a) * 0.3), 1.8, 0.7,
                                    sides=5, rings=2))
    m.add("wolf_head", ell("body", "fur", (3.6, 22.4 * S, -2.2), (1.4, 1.1, 1.6), segs=8, rings=3))
    m.add("wolf_snout", cone("body", "fur", P(3.8, 22.2 * S, -3.4), (0.1, -0.3, -1), 1.4, 0.65, sides=6, rings=1))
    for sx in (1, -1):
        m.add(f"wolf_ear{sx}", cone("body", "fur", P(3.6 + sx * 0.7, 23.1 * S, -1.8), (sx * 0.3, 1, 0.3), 0.9, 0.38, sides=4, rings=1))
    pts = belt_pts(4.7, 14.0 * S, 3.0, n=19, wav=0.25)
    m.add("sash", tub("body", "sash", pts, [0.95] * len(pts), [0.55] * len(pts), sides=6, hint=(0, 1, 0)))
    m.add("sash_knot", ell("body", "sash", (3.2, 14.0 * S, -2.7), (0.9, 0.75, 0.6), segs=8, rings=2))
    for k, dx in enumerate((0, 0.8)):
        m.add(f"sash_tail{k}", tub("body", "sash", [P(3.3 + dx, 13.6 * S, -2.8), P(3.9 + dx, 11.6 * S, -3.0), P(4.3 + dx, 9.6 * S, -2.8)], [0.75, 0.65, 0.45], [0.22] * 3,
                                    sides=4, hint=(0, 0, 1)))
    # 右肩の岩殻の板 (二段・革紐・こぶ)
    m.add("pauldron", ell("right_arm", "rock", (-6.8, 22.8 * S, 0), (3.2, 2.0, 3.2), segs=12, rings=4))
    m.add("pauldron2", ell("right_arm", "rock", (-7.6, 21.0 * S, 0), (2.6, 1.4, 2.8), segs=12, rings=3))
    m.add("pauldron_strap", tub("right_arm", "leather", [P(-5.0, 22.0 * S, -2.4), P(-7.0, 20.6 * S, -2.6), P(-8.8, 20.0 * S, 0), P(-7.0, 20.6 * S, 2.6), P(-5.0, 22.0 * S, 2.4)],
                                 [0.3] * 5, None, sides=5))
    for k, (x, z) in enumerate(((-6.4, -1.8), (-7.6, 1.0), (-5.6, 1.6))):
        m.add(f"knob{k}", ell("right_arm", "rockd", (x, 24.2 * S + 0.6 - abs(x + 6.8) * 0.3, z), (0.6, 0.45, 0.6), segs=6, rings=2))
    m.add("bracer", tub("left_forearm", "leather", [P(7.0, 15.0 * S, -0.2), P(7.15, 12.6 * S, -0.4)], [1.45, 1.35], None, sides=10))
    # 両手の大きな手斧: 柄 (巻きと石突)、刃 (面取り・刃の光り)、背の棘
    m.add("haft", tub("weapon", "wood", [P(-6.6, 14.4, -0.6), P(-6.6, 1.0, -0.6)], [0.55, 0.55], None, sides=8))
    m.add("butt", cone("weapon", "iron", P(-6.6, 1.4, -0.6), (0, -1, 0), 1.2, 0.65, sides=6, rings=1))
    for k, y in enumerate((12.6, 11.4, 10.2)):
        m.add(f"hgrip{k}", tub("weapon", "leather", [P(-6.6, y + 0.4, -0.6), P(-6.6, y - 0.4, -0.6)], [0.7, 0.7], None, sides=8))
    axe = [[P(-6.6 + dx, y, z) for dx, y, z in sec] for sec in (
        ((-0.35, 4.6, -1.0), (0.35, 4.6, -1.0), (0.35, 8.4, -1.0), (-0.35, 8.4, -1.0)),
        ((-0.3, 3.4, -3.6), (0.3, 3.4, -3.6), (0.3, 9.6, -3.6), (-0.3, 9.6, -3.6)),
        ((-0.12, 1.9, -6.0), (0.12, 1.9, -6.0), (0.12, 11.2, -6.0), (-0.12, 11.2, -6.0)))]
    m.add("axe", lof("weapon", "iron", axe))
    m.add("axe_edge", tub("weapon", "steel_l", [P(-6.6, 1.9, -6.05), P(-6.6, 4.2, -6.4), P(-6.6, 6.6, -6.5), P(-6.6, 9.0, -6.4), P(-6.6, 11.2, -6.05)], [0.15] * 5, None, sides=4))
    m.add("axe_eye", tub("weapon", "irond", [P(-6.6, 4.4, -0.6), P(-6.6, 8.6, -0.6)], [0.95, 0.95], None, sides=8))
    m.add("axe_back", cone("weapon", "iron", P(-6.6, 6.5, 0.2), (0, 0, 1), 2.4, 0.8, sides=4, rings=1))
    m.pal = bandit_pal(fur=M_([(48, 40, 34), (80, 68, 56), (116, 100, 82), (150, 134, 110)], noise=0.2), rock=M_([(48, 52, 60), (70, 76, 86), (96, 102, 112), (124, 130, 138)], crack=0.08),
                       rockd=M_([(36, 38, 44), (52, 56, 64), (72, 76, 86), (94, 98, 108)]), sash=M_(RED, lit=0.32), irond=M_(IRON), brass=M_(BRASSY, lit=0.4))
    m.detail = lambda c, col, glow: (fur(c, col, ["fur"], amt=0.08), glow)
    return m


ALL = {f.__name__: f for f in (bandit, bandit_archer, bandit_chief)}
