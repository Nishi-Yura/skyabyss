"""NPC v2 の持ち物 (市の三人: 食料屋・武具屋・素材屋。2026-10-27 の声「道具屋がパンまで売るのはおかしい。専門の人に」)。
props.py と同じ決まり (人の作図空間の絶対座標。書き出しで受け口の pivot を引く)。色の名は人の pal か PROP_MATS。"""
from __future__ import annotations

import math

import kit as K
from kit import V, part, box, rod, disc, ellipsoid, tube
from props import hand


# ---------------------------------------------------------------- 食料屋
def sausages(c):
    """左手から提げた腸詰めの連なり (五つ、紐でつながる)"""
    h = hand(c, 1)
    F = part("ss_loop", tube("prop_l", "sack2", [h + V(0, 0.4, 0), h + V(0.2, -0.6, -0.3)], ra=0.14, sides=3, caps=(True, True)))
    p = h + V(0.2, -0.8, -0.3)
    for i in range(5):
        a = -0.35 + i * 0.18
        q = p + V(math.sin(a) * 0.7 * i, -1.55 * i - 0.9, -0.2 * i)
        F += part(f"ss{i}", ellipsoid("prop_l", "meat", q, (0.68, 0.92, 0.68), segs=7, rings=3))
        F += part(f"ss_tie{i}", disc("prop_l", "sack2", q + V(0, 0.96, 0), 0.26, 0.18, n=5))
    return F


def ladle(c):
    """右手の大きな杓子 (柄は長く、先に深い椀)"""
    h = hand(c, -1)
    F = part("ld_handle", rod("prop_r", "wood", h + V(0, 2.4, 0.3), h + V(0, -8.6, -1.6), 0.42, 0.36, sides=6))
    b = h + V(0, -9.8, -2.1)
    F += part("ld_bowl", ellipsoid("prop_r", "metal2", b, (1.8, 1.25, 1.8), segs=10, rings=3))
    F += part("ld_rim", disc("prop_r", "metal2", b + V(0, 0.9, 0), 1.9, 0.22, n=10))
    F += part("ld_soup", disc("prop_r", "soup", b + V(0, 1.0, 0), 1.55, 0.08, n=10))
    return F


def cheese_wheel(c):
    """両手で抱える大きな乾酪の輪 (一切れ欠けている)"""
    hr, hl = hand(c, -1), hand(c, 1)
    cen = (hr + hl) / 2 + V(0, 0.6, -0.8)
    F = part("ch_wheel", disc("prop_r", "cheese", cen, 2.5, 2.0, n=12, axis="z"))
    F += part("ch_rind", disc("prop_r", "rind", cen + V(0, 0, -1.05), 2.56, 0.14, n=12, axis="z"))
    F += part("ch_cut", box("prop_r", "cheese2", cen + V(1.2, 1.2, -1.08), (0.8, 0.8, 0.1)))
    F += part("ch_cloth", box("prop_r", "paper", cen + V(0, -2.2, 0), (2.0, 0.18, 1.2)))
    return F


# ---------------------------------------------------------------- 武具屋
def round_shield(c):
    """左の前腕に付けた丸盾 (板の面・鉄の縁・真ん中の突起)"""
    h = hand(c, 1)
    cen = h + V(0.4, 0.2, -1.0)
    F = part("rs_face", disc("prop_l", "shield", cen, 3.4, 0.45, n=14, axis="z"))
    F += part("rs_rim", disc("prop_l", "metal2", cen + V(0, 0, -0.05), 3.55, 0.3, n=14, axis="z", r_top=3.4))
    F += part("rs_boss", ellipsoid("prop_l", "metal2", cen + V(0, 0, -0.45), (0.95, 0.95, 0.6), segs=8, rings=2))
    for i in range(6):
        a = i * math.pi / 3
        F += part(f"rs_rivet{i}", box("prop_l", "brass", cen + V(math.cos(a) * 2.7, math.sin(a) * 2.7, -0.3), (0.25, 0.25, 0.12)))
    F += part("rs_band", box("prop_l", "shield2", cen + V(0, 0, -0.26), (0.4, 3.3, 0.06)))
    return F


def shoulder_sword(c):
    """右肩に担いだ両刃の剣 (鍔と柄は手もと、刃は肩の後ろへ)"""
    h = hand(c, -1)
    F = part("sw_grip", rod("prop_r", "leather", h + V(0, -0.9, 0.3), h + V(0, 1.3, -0.2), 0.36, sides=6))
    F += part("sw_pommel", ellipsoid("prop_r", "brass", h + V(0, -1.2, 0.35), (0.45, 0.45, 0.45), segs=6, rings=2))
    g = h + V(0, 1.5, -0.25)
    F += part("sw_guard", box("prop_r", "brass", g, (2.0, 0.3, 0.42)))
    tip = h + V(0.6, 13.4, 6.4)
    F += part("sw_blade", tube("prop_r", "steel", [g + V(0, 0.2, 0), (g + tip) / 2, tip], ra=[0.62, 0.55, 0.0], rb=[0.14, 0.14, 0.0],
                               sides=4, caps=(True, False), hint=(1, 0, 0)))
    return F


def quiver(c):
    """背の矢筒 (革の筒に矢が八本、羽が上に出る)"""
    zc = 4.6
    a, b = V(-2.0, 10.6, zc), V(2.4, 21.0, zc + 0.6)
    F = part("qv_tube", tube("prop_back", "leather", [a, b], ra=1.25, sides=8, caps=(True, False)))
    F += part("qv_band0", tube("prop_back", "brass", [a + (b - a) * 0.15, a + (b - a) * 0.2], ra=1.32, sides=8))
    F += part("qv_band1", tube("prop_back", "brass", [a + (b - a) * 0.8, a + (b - a) * 0.85], ra=1.32, sides=8))
    d = (b - a) / max(1e-6, float(math.sqrt(((b - a) ** 2).sum())))
    for i in range(8):
        ang = i * math.pi / 4
        o = V(math.cos(ang) * 0.7, 0, math.sin(ang) * 0.7)
        s0 = b + o - d * 1.0
        s1 = b + o + d * 2.4
        F += part(f"qv_shaft{i}", rod("prop_back", "wood", s0, s1, 0.12, sides=3))
        F += part(f"qv_fl{i}", box("prop_back", "feather" if i % 2 else "paper", s1 - d * 0.6, (0.06, 0.6, 0.32)))
    F += part("qv_strap", tube("prop_back", "leather", [V(-2.6, 10.0, 3.0), V(-2.2, 9.0, -2.6), V(2.8, 19.4, -2.8), V(3.0, 21.2, 3.2)],
                               ra=0.32, rb=0.12, sides=4, caps=(True, True), hint=(0, 0, 1)))
    return F


# ---------------------------------------------------------------- 素材屋
def ingot_stack(c):
    """両手で抱えた地金の山 (鉄・銅・鉄の三本を井桁に)"""
    hr, hl = hand(c, -1), hand(c, 1)
    cen = (hr + hl) / 2 + V(0, 0.5, -0.8)
    F = []
    mats = ["steel", "copper", "steel", "brass"]
    for i in range(4):
        R = K.rot_y(90 if i % 2 else 0)
        F += part(f"ig{i}", box("prop_r", mats[i], cen + V(0, i * 0.95, 0), (2.4, 0.46, 0.92), taper=0.86, R=R))
    F += part("ig_cloth", box("prop_r", "sack", cen + V(0, -0.62, 0), (2.8, 0.14, 2.4)))
    return F


def shoulder_pick(c):
    """右肩に担いだつるはし (柄は手から肩の後ろへ、頭は背の上)"""
    h = hand(c, -1)
    top = h + V(0.4, 12.6, 5.2)
    F = part("pk_handle", rod("prop_r", "wood", h + V(0, -2.2, -0.6), top, 0.46, 0.4, sides=6))
    F += part("pk_head", tube("prop_r", "metal2", [top + V(-4.6, -1.8, 0.8), top + V(0, 0.6, 0), top + V(4.6, -1.8, -0.8)],
                              ra=[0.0, 0.9, 0.0], rb=[0.0, 0.6, 0.0], sides=4, hint=(0, 0, 1)))
    F += part("pk_collar", disc("prop_r", "metal2", top + V(0, -0.3, 0), 0.7, 0.8, n=6))
    return F


def ore_lump(c):
    """左手にのせた鉱石の塊 (光る筋の粒が三つ)"""
    h = hand(c, 1)
    p = h + V(0, 2.0, -0.8)
    F = part("or_rock", ellipsoid("prop_l", "ore", p, (1.9, 1.5, 1.7), segs=7, rings=3))
    for i, (dx, dy, dz) in enumerate(((0.9, 0.8, -1.2), (-0.8, 1.1, -0.9), (0.3, -0.3, -1.6), (-1.2, -0.2, -0.6))):
        F += part(f"or_gem{i}", box("prop_l", "gem", p + V(dx, dy, dz), (0.38, 0.48, 0.26)))
    return F


PROP3_MATS = {
    "meat": dict(ramp=[(70, 26, 20), (120, 46, 34), (166, 74, 52), (196, 112, 80)], base=0.56, lit=0.3, noise=0.08, crack=0.02, min=0),
    "soup": dict(ramp=[(90, 50, 20), (150, 96, 40), (196, 146, 70), (226, 190, 110)], base=0.6, lit=0.3, noise=0.04, crack=0, min=1),
    "cheese": dict(ramp=[(150, 110, 40), (206, 162, 64), (236, 204, 110), (250, 232, 160)], base=0.6, lit=0.3, noise=0.05, crack=0, min=1),
    "cheese2": dict(ramp=[(196, 160, 80), (236, 210, 130), (252, 238, 180)], base=0.6, lit=0.3, noise=0.03, crack=0, min=1),
    "rind": dict(ramp=[(90, 50, 20), (140, 84, 36), (180, 120, 56)], base=0.56, lit=0.3, noise=0.06, crack=0, min=0),
    "shield": dict(ramp=[(56, 36, 24), (96, 62, 40), (136, 92, 58), (172, 124, 80)], base=0.55, lit=0.3, noise=0.1, crack=0.02, min=0,
                   stripe=3, axis="v", streak=0.1),
    "shield2": dict(ramp=[(110, 30, 30), (160, 50, 44), (200, 80, 62)], base=0.56, lit=0.3, noise=0.05, crack=0, min=0),
    "steel": dict(ramp=[(50, 54, 60), (96, 102, 112), (150, 158, 168), (206, 212, 220), (240, 244, 248)], base=0.6, lit=0.45, noise=0.03, crack=0, min=0),
    "copper": dict(ramp=[(80, 40, 24), (140, 70, 40), (196, 110, 64), (232, 160, 110)], base=0.6, lit=0.42, noise=0.03, crack=0, min=0),
    "ore": dict(ramp=[(34, 32, 36), (62, 58, 62), (96, 92, 94), (130, 126, 124)], base=0.55, lit=0.3, noise=0.14, crack=0.06, min=0),
    "gem": dict(ramp=[(40, 90, 110), (80, 170, 190), (150, 230, 240), (220, 252, 255)], base=0.62, lit=0.45, noise=0.0, crack=0, min=1),
    "feather": dict(ramp=[(130, 40, 36), (190, 70, 56), (230, 120, 90)], base=0.6, lit=0.3, noise=0.04, crack=0, min=1),
}


from props import PROP_MATS  # noqa: E402

PROP_MATS.update(PROP3_MATS)


# ---------------------------------------------------------------- 封切り師 (docs/design/52 の 5 章)
def sealed_box(c):
    """左手で胸に抱えた封の箱 (樫の板・真鍮の帯・赤い封蝋の印と紐)"""
    h = hand(c, 1)
    cen = h + V(-1.0, 1.5, -0.7)
    F = part("sb_body", box("prop_l", "wood", cen, (1.9, 1.25, 1.35)))
    F += part("sb_lid", box("prop_l", "wood", cen + V(0, 1.5, 0), (2.05, 0.34, 1.48), taper=0.9))
    for i, dx in enumerate((-1.25, 1.25)):
        F += part(f"sb_band{i}", box("prop_l", "brass", cen + V(dx, 0.25, 0), (0.2, 1.72, 1.46)))
    F += part("sb_cord", box("prop_l", "wax", cen + V(0, 0.35, -1.42), (0.12, 1.3, 0.06)))
    F += part("sb_seal", disc("prop_l", "wax", cen + V(0, 0.55, -1.48), 0.56, 0.2, n=8, axis="z"))
    F += part("sb_lock", box("prop_l", "brass", cen + V(0, 1.15, -1.42), (0.32, 0.3, 0.1)))
    return F
