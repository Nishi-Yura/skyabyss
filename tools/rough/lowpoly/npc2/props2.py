"""NPC v2 の持ち物 (都の係の人・町の人)。props.py と同じ決まり (人の作図空間の絶対座標。書き出しで受け口の pivot を引く)。"""
from __future__ import annotations

import math

import numpy as np

import kit as K
from kit import V, part, box, rod, disc, ellipsoid, tube, loft, ringe, spike
from props import hand, PROP_MATS


def hammer(c):
    h = hand(c, -1)
    F = part("h_handle", rod("prop_r", "wood", h + V(0, 1.6, 0.2), h + V(0, -5.6, -0.6), 0.42, 0.36, sides=6))
    hd = h + V(0, -6.2, -0.65)
    F += part("h_head", box("prop_r", "metal2", hd, (0.95, 0.95, 2.0)))
    F += part("h_face", box("prop_r", "metal2", hd + V(0, 0, -2.2), (1.1, 1.1, 0.3)))
    F += part("h_wedge", box("prop_r", "metal2", hd + V(0, 0, 2.15), (0.7, 0.55, 0.35), taper=0.7))
    return F


def tool_pack(c):
    zc = 5.0
    F = part("pack", box("prop_back", "leather", V(0, 16.0, zc), (3.6, 4.6, 1.8), taper=0.92))
    F += part("pack_lid", box("prop_back", "leather2", V(0, 20.4, zc - 0.1), (3.75, 0.7, 2.0)))
    F += part("pack_pocket", box("prop_back", "leather2", V(0, 14.0, zc + 1.9), (2.6, 1.8, 0.45)))
    F += part("pick_h", rod("prop_back", "wood", V(-2.4, 12.0, zc + 1.0), V(-2.0, 27.0, zc + 1.4), 0.38, sides=5))
    F += part("pick_hd", tube("prop_back", "metal2", [V(-5.2, 26.0, zc + 1.4), V(-2.0, 27.2, zc + 1.4), V(1.4, 26.4, zc + 1.4)], ra=[0.0, 0.7, 0.0],
                              rb=[0.0, 0.5, 0.0], sides=4))
    F += part("shovel_h", rod("prop_back", "wood", V(2.4, 12.0, zc + 1.0), V(2.8, 25.0, zc + 1.2), 0.36, sides=5))
    F += part("shovel_b", box("prop_back", "metal2", V(2.9, 26.6, zc + 1.2), (1.3, 1.8, 0.2), taper=0.7))
    F += part("roll", tube("prop_back", "blanket", [V(-3.8, 11.2, zc), V(3.8, 11.2, zc)], ra=1.2, sides=7, caps=(True, True)))
    F += part("pan", disc("prop_back", "metal2", V(0, 17.0, zc + 2.2), 1.6, 0.25, n=8, axis="z"))
    for s in (-1, 1):
        F += part(f"strap{s}", tube("prop_back", "leather2", [V(s * 2.4, 19.6, 3.4), V(s * 2.4, 21.6, 0.8), V(s * 2.2, 21.4, -2.3), V(s * 2.5, 14.6, -2.7)],
                                    ra=0.36, rb=0.14, sides=4, caps=(True, True), hint=(0, 0, 1)))
    return F


def flask(c):
    h = hand(c, 1)
    p = h + V(0, 1.6, -0.3)
    F = part("f_body", ellipsoid("prop_l", "glass", p, (1.05, 1.25, 1.05), segs=8, rings=3))
    F += part("f_neck", disc("prop_l", "glass", p + V(0, 1.5, 0), 0.4, 0.55, n=6))
    F += part("f_cork", disc("prop_l", "wood", p + V(0, 2.25, 0), 0.45, 0.3, n=6))
    F += part("f_label", box("prop_l", "paper", p + V(0, -0.1, -1.0), (0.7, 0.5, 0.1)))
    return F


def crate(c):
    hr, hl = hand(c, -1), hand(c, 1)
    cen = (hr + hl) / 2 + V(0, 0.4, -0.6)
    F = part("crate", box("prop_r", "wood", cen, (3.0, 2.0, 2.0)))
    for i, dy in enumerate((-1.95, 1.95)):
        F += part(f"crate_band{i}", box("prop_r", "metal2", cen + V(0, dy, 0), (3.1, 0.25, 2.1)))
    F += part("crate_slat", box("prop_r", "wood2", cen + V(0, 0, -2.05), (2.6, 0.3, 0.1)))
    F += part("crate_tag", box("prop_r", "paper", cen + V(1.6, 0.6, -2.1), (0.7, 0.5, 0.08)))
    return F


def tray(c):
    h = hand(c, 1)
    top = h + V(-1.2, 0.7, -0.3)
    F = part("tray", disc("prop_l", "wood", top, 3.0, 0.22, n=10))
    F += part("tray_rim", disc("prop_l", "wood2", top + V(0, 0.35, 0), 3.0, 0.16, n=10, r_top=2.9))
    for i, (dx, dz) in enumerate(((-1.2, -0.6), (1.1, 0.4))):
        cp = top + V(dx, 1.3, dz)
        F += part(f"mug{i}", disc("prop_l", "wood2", cp, 0.8, 1.0, n=8))
        F += part(f"foam{i}", ellipsoid("prop_l", "foam", cp + V(0, 1.05, 0), (0.85, 0.4, 0.85), segs=8, rings=1))
        F += part(f"mug_h{i}", tube("prop_l", "wood2", [cp + V(0.75, 0.6, 0), cp + V(1.4, 0.2, 0), cp + V(0.75, -0.5, 0)], ra=0.2, sides=3, caps=(True, True)))
    F += part("bread", ellipsoid("prop_l", "bread", (top[0] + 0.3, top[1] + 0.7, top[2] - 1.6), (1.1, 0.55, 0.7), segs=7, rings=2))
    return F


def bird(c):
    h = hand(c, 1)
    p = h + V(0, 0.9, -0.2)
    F = part("glove", ellipsoid("prop_l", "leather", h, (1.25, 1.45, 1.05), segs=6, rings=2))
    F += part("b_body", ellipsoid("prop_l", "plume", p + V(0, 1.5, 0.2), (1.0, 1.4, 1.5), segs=7, rings=3, rot=K.rot_x(-25)))
    F += part("b_head", ellipsoid("prop_l", "plume2", p + V(0, 3.1, -0.7), (0.75, 0.75, 0.8), segs=6, rings=2))
    F += part("b_beak", spike("prop_l", "brass", p + V(0, 3.0, -1.4), (0, -0.3, -1), 0.8, 0.3, sides=3, rings=1))
    for s in (-1, 1):
        F += part(f"b_wing{s}", tube("prop_l", "plume2", [p + V(s * 0.85, 2.3, -0.3), p + V(s * 1.0, 1.4, 0.9), p + V(s * 0.6, 0.4, 2.4)], ra=[0.4, 0.45, 0.0],
                                     rb=[1.0, 0.9, 0.0], sides=4, caps=(True, False), hint=(0, 0, 1)))
        F += part(f"b_eye{s}", ellipsoid("prop_l", "eyebird", p + V(s * 0.62, 3.25, -0.95), (0.18, 0.2, 0.15), segs=4, rings=1))
    F += part("b_tail", tube("prop_l", "plume2", [p + V(0, 1.0, 1.4), p + V(0, -0.6, 3.2)], ra=[0.6, 0.0], rb=[0.3, 0.0], sides=4, caps=(True, False)))
    return F


def bell_staff(c):
    h = hand(c, -1)
    F = part("st_pole", rod("prop_r", "wood", h + V(0, -11.4, -0.1), h + V(0, 13.0, 0.1), 0.45, 0.4, sides=6))
    top = h + V(0, 13.2, 0.1)
    F += part("st_ring", tube("prop_r", "brass", [top + V(1.6 * math.sin(a), 1.9 - 1.9 * math.cos(a), 0) for a in np.linspace(0, 2 * math.pi, 9)], 0.2,
                              sides=4, caps=(False, False), hint=(0, 0, 1)))
    for i, a in enumerate((-70, 0, 70)):
        p = top + V(1.6 * math.sin(math.radians(a)), 1.9 - 1.9 * math.cos(math.radians(a)) - 0.2, 0)
        F += part(f"bell{i}", ellipsoid("prop_r", "brass", p + V(0, -0.6, 0), (0.5, 0.6, 0.5), segs=6, rings=2))
    F += part("st_tassel", tube("prop_r", "tassel", [top + V(0, -0.4, 0), top + V(0.3, -2.4, -0.2), top + V(0.4, -4.0, -0.2)], ra=[0.35, 0.3, 0.0], sides=4,
                                caps=(True, False)))
    F += part("st_foot", disc("prop_r", "brass", h + V(0, -11.0, -0.1), 0.6, 0.4, n=6))
    return F


def spear(c):
    h = hand(c, -1)
    F = part("sp_pole", rod("prop_r", "wood", h + V(0, -11.4, -0.1), h + V(0, 14.5, 0.1), 0.42, 0.38, sides=6))
    tip = h + V(0, 14.5, 0.1)
    F += part("sp_socket", disc("prop_r", "metal2", tip + V(0, 0.2, 0), 0.6, 0.7, n=6, r_top=0.45))
    F += part("sp_blade", tube("prop_r", "metal2", [tip + V(0, 0.8, 0), tip + V(0, 2.6, 0), tip + V(0, 5.0, 0)], ra=[0.45, 0.95, 0.0], rb=[0.25, 0.35, 0.0],
                               sides=4, caps=(True, False), hint=(1, 0, 0)))
    F += part("sp_flag", box("prop_r", "pennant", tip + V(0, -1.6, 1.4), (0.12, 1.3, 1.4)))
    F += part("sp_foot", disc("prop_r", "metal2", h + V(0, -11.0, -0.1), 0.55, 0.4, n=6))
    return F


def book_stack(c):
    h = hand(c, 1)
    F = []
    cen = h + V(-1.6, 1.0, -0.4)
    cols = ["book", "book2", "book3"]
    for i in range(3):
        R = K.rot_y(-10 + i * 12)
        F += part(f"bk{i}", box("prop_l", cols[i], cen + V(0, i * 0.95, 0), (1.9 - i * 0.15, 0.42, 1.4 - i * 0.08), R=R))
        F += part(f"bkp{i}", box("prop_l", "paper", cen + V(0, i * 0.95, 0) + R @ V(0.12, 0, 0), (1.8 - i * 0.15, 0.36, 1.32 - i * 0.08), R=R))
    F += part("scroll", tube("prop_l", "paper", [cen + V(-2.0, 3.1, 0.2), cen + V(2.0, 3.0, -0.3)], ra=0.55, sides=6, caps=(True, True)))
    F += part("scroll_tie", disc("prop_l", "tassel", cen + V(0.0, 3.05, -0.05), 0.62, 0.18, n=6, axis="x"))
    return F


def bokken(c):
    h = hand(c, -1)
    F = part("bk_grip", rod("prop_r", "wrap", h + V(0, 1.6, 0.4), h + V(0, -1.2, -0.3), 0.42, sides=6))
    F += part("bk_guard", disc("prop_r", "wood2", h + V(0, -1.5, -0.35), 1.0, 0.18, n=8))
    F += part("bk_blade", tube("prop_r", "wood", [h + V(0, -1.6, -0.35), h + V(0, -6.0, -1.6), h + V(0, -10.0, -3.6)], ra=[0.45, 0.42, 0.2], rb=[0.25, 0.25, 0.12],
                               sides=4, caps=(True, True), hint=(0, 0, 1)))
    return F


def coin_pouch(c):
    h = hand(c, 1)
    p = h + V(0, -1.4, -0.2)
    F = part("cp_bag", ellipsoid("prop_l", "velvet", p, (1.3, 1.5, 1.2), segs=7, rings=3))
    F += part("cp_neck", disc("prop_l", "velvet", p + V(0, 1.55, 0), 0.55, 0.35, n=6, r_top=0.75))
    F += part("cp_tie", disc("prop_l", "brass", p + V(0, 1.4, 0), 0.65, 0.15, n=6))
    F += part("cp_coin", disc("prop_l", "brass", h + V(-0.4, 1.5, -1.1), 0.6, 0.1, n=8, axis="z"))
    return F


def shovel(c):
    h = hand(c, -1)
    F = part("sv_pole", rod("prop_r", "wood", h + V(0, 5.6, 0.1), h + V(0, -7.6, -0.5), 0.4, sides=6))
    F += part("sv_grip", tube("prop_r", "wood2", [h + V(-0.9, 6.2, 0.1), h + V(0.9, 6.2, 0.1)], ra=0.35, sides=5, caps=(True, True)))
    b = h + V(0, -9.6, -0.6)
    F += part("sv_blade", loft("prop_r", "metal2", [[b + V(-1.4, 2.0, -0.15), b + V(1.4, 2.0, -0.15), b + V(1.4, 2.0, 0.15), b + V(-1.4, 2.0, 0.15)],
                                                     [b + V(-1.2, -1.2, -0.12), b + V(1.2, -1.2, -0.12), b + V(1.2, -1.2, 0.12), b + V(-1.2, -1.2, 0.12)],
                                                     [b + V(-0.3, -2.0, -0.05), b + V(0.3, -2.0, -0.05), b + V(0.3, -2.0, 0.05), b + V(-0.3, -2.0, 0.05)]],
                                    caps=("metal2", "metal2")))
    F += part("sv_neck", disc("prop_r", "metal2", h + V(0, -7.6, -0.5), 0.55, 0.5, n=6))
    return F


def map_rolls(c):
    h = hand(c, 1)
    F = []
    for i, (dx, dz) in enumerate(((0.0, 0.0), (0.9, 0.6), (-0.8, 0.7))):
        a = h + V(dx - 0.6, 0.6 + 0.3 * i, dz + 2.0)
        b = h + V(dx - 0.2, -0.2 + 0.3 * i, dz - 2.6)
        F += part(f"mr{i}", tube("prop_l", "paper" if i != 1 else "paper2", [a, b], ra=0.6 - i * 0.05, sides=7, caps=(True, True)))
        F += part(f"mrt{i}", disc("prop_l", "tassel", (a + b) / 2, 0.66 - i * 0.05, 0.18, n=7, axis="z"))
    return F


def map_tube(c):
    F = part("mt", tube("prop_back", "leather", [V(-3.6, 8.4, 3.4), V(3.6, 24.0, 3.8)], ra=1.2, sides=8, caps=(True, True)))
    F += part("mt_cap", disc("prop_back", "brass", V(3.75, 24.4, 3.82), 1.3, 0.5, n=8))
    F += part("mt_cap0", disc("prop_back", "brass", V(-3.75, 8.0, 3.38), 1.3, 0.5, n=8))
    return F


def bottle(c):
    h = hand(c, -1)
    p = h + V(0, -1.7, -0.3)
    F = part("bt_body", disc("prop_r", "glassg", p, 0.95, 1.4, n=8))
    F += part("bt_shoulder", disc("prop_r", "glassg", p + V(0, 1.7, 0), 0.95, 0.3, n=8, r_top=0.42))
    F += part("bt_neck", disc("prop_r", "glassg", p + V(0, 2.5, 0), 0.38, 0.6, n=6))
    F += part("bt_cork", disc("prop_r", "wood", p + V(0, 3.25, 0), 0.42, 0.25, n=6))
    F += part("bt_label", box("prop_r", "paper", p + V(0, 0.1, -0.95), (0.6, 0.6, 0.08)))
    return F


def scales(c):
    h = hand(c, 1)
    top = h + V(0, 2.6, -0.2)
    F = part("sc_post", rod("prop_l", "brass", h + V(0, -0.4, -0.2), top, 0.22, sides=5))
    F += part("sc_beam", rod("prop_l", "brass", top + V(-2.6, 0, 0), top + V(2.6, 0, 0), 0.18, sides=4))
    F += part("sc_knob", ellipsoid("prop_l", "brass", top + V(0, 0.4, 0), (0.35, 0.35, 0.35), segs=5, rings=1))
    for s in (-1, 1):
        q = top + V(s * 2.5, 0, 0)
        F += part(f"sc_cord{s}", rod("prop_l", "cord", q, q + V(0, -2.4, 0), 0.08, sides=3))
        F += part(f"sc_pan{s}", disc("prop_l", "brass", q + V(0, -2.6, 0), 1.0, 0.18, n=8, r_top=1.15))
    F += part("sc_coin", disc("prop_l", "brass2", top + V(-2.5, -2.25, 0), 0.4, 0.15, n=6))
    return F


def pitchfork(c):
    h = hand(c, -1)
    F = part("pf_pole", rod("prop_r", "wood", h + V(0, -11.4, -0.1), h + V(0, 10.0, 0.1), 0.4, sides=6))
    top = h + V(0, 10.0, 0.1)
    F += part("pf_bar", rod("prop_r", "metal2", top + V(-1.5, 0, 0), top + V(1.5, 0, 0), 0.22, sides=4))
    for i, dx in enumerate((-1.4, 0.0, 1.4)):
        F += part(f"pf_t{i}", tube("prop_r", "metal2", [top + V(dx, 0, 0), top + V(dx, 2.4, -0.15), top + V(dx * 1.05, 4.0, -0.5)], ra=[0.2, 0.18, 0.0],
                                   sides=3, caps=(True, False)))
    return F


def harpoon(c):
    h = hand(c, -1)
    F = part("hp_pole", rod("prop_r", "wood", h + V(0, -10.6, -0.1), h + V(0, 11.0, 0.1), 0.38, sides=6))
    tip = h + V(0, 11.0, 0.1)
    F += part("hp_head", tube("prop_r", "metal2", [tip, tip + V(0, 1.6, 0), tip + V(0, 3.6, 0)], ra=[0.4, 0.7, 0.0], rb=[0.3, 0.3, 0.0], sides=4, caps=(True, False)))
    for s in (-1, 1):
        F += part(f"hp_barb{s}", spike("prop_r", "metal2", tip + V(s * 0.4, 1.4, 0), (s * 0.6, -1, 0), 1.3, 0.25, sides=3, rings=1))
    F += part("hp_rope", tube("prop_r", "rope", [tip + V(0, -0.6, 0), tip + V(0.6, -1.8, 0.5), tip + V(0.4, -3.6, 0.6), h + V(0.6, 2.4, 0.3)], ra=0.22,
                              sides=3, caps=(True, True)))
    return F


def handbell(c):
    h = hand(c, 1)
    F = part("hb_grip", rod("prop_l", "wood", h + V(0, 1.4, -0.2), h + V(0, -0.6, -0.2), 0.35, sides=6))
    b = h + V(0, -1.4, -0.2)
    F += part("hb_bell", loft("prop_l", "brass", [ringe(b[1], 0.45, -0.45, 0.45, n=8, cx=b[0], zc=b[2]), ringe(b[1] - 1.0, 1.0, -1.0, 1.0, n=8, cx=b[0], zc=b[2]),
                                                  ringe(b[1] - 2.2, 1.6, -1.6, 1.6, n=8, cx=b[0], zc=b[2])], caps=("brass", "brass")))
    F += part("hb_lip", disc("prop_l", "brass2", b + V(0, -2.3, 0), 1.7, 0.18, n=8))
    return F


def fishing_rod(c):
    h = hand(c, -1)
    F = part("fr_pole", tube("prop_r", "wood", [h + V(0, -3.0, 0.3), h + V(0, 6.0, -0.6), h + V(0, 13.0, -2.6), h + V(0, 17.0, -5.4)], ra=[0.42, 0.32, 0.2, 0.1],
                             sides=5, caps=(True, True)))
    F += part("fr_reel", disc("prop_r", "metal2", h + V(0.6, 1.6, 0.0), 0.7, 0.25, n=8, axis="x"))
    F += part("fr_line", rod("prop_r", "cord", h + V(0, 17.0, -5.4), h + V(0, 10.0, -6.4), 0.06, sides=3))
    F += part("fr_float", ellipsoid("prop_r", "pennant", h + V(0, 9.8, -6.4), (0.35, 0.45, 0.35), segs=5, rings=1))
    return F


def lute(c):
    zc = 4.0
    R = K.rot_z(-35)
    cen = V(0, 15.6, zc)
    F = part("lt_body", ellipsoid("prop_back", "wood", cen, (2.6, 3.2, 1.2), segs=9, rings=3, rot=R))
    F += part("lt_face", ellipsoid("prop_back", "wood2", cen + V(0, 0, 0.9), (2.3, 2.9, 0.5), segs=9, rings=1, rot=R))
    F += part("lt_hole", disc("prop_back", "dark", cen + V(0.5, 0.6, 1.35), 0.6, 0.06, n=8, axis="z"))
    neck_a = cen + R @ V(0, 2.8, 0.4)
    neck_b = cen + R @ V(0, 9.4, 0.4)
    F += part("lt_neck", rod("prop_back", "wood2", neck_a, neck_b, 0.5, 0.42, sides=5))
    F += part("lt_peg", box("prop_back", "wood", neck_b + R @ V(0, 0.9, -0.4), (0.7, 1.1, 0.4), R=R))
    F += part("lt_strap", tube("prop_back", "leather", [V(-2.8, 10.6, 2.6), V(-3.4, 15.0, -2.6), V(0.0, 21.6, -2.4), V(2.4, 21.8, 0.6), V(2.4, 20.0, 3.4)],
                               ra=0.32, rb=0.12, sides=4, caps=(True, True), hint=(0, 0, 1)))
    return F


def doll(c):
    h = hand(c, 1)
    p = h + V(-0.4, 0.8, -0.6)
    F = part("dl_body", ellipsoid("prop_l", "doll", p, (0.9, 1.3, 0.7), segs=6, rings=2))
    F += part("dl_head", ellipsoid("prop_l", "paper", p + V(0, 1.8, 0), (0.8, 0.8, 0.75), segs=6, rings=2))
    F += part("dl_hair", ellipsoid("prop_l", "yarn", p + V(0, 2.2, 0.2), (0.88, 0.5, 0.8), segs=6, rings=2))
    for s in (-1, 1):
        F += part(f"dl_arm{s}", rod("prop_l", "doll", p + V(s * 0.7, 0.6, 0), p + V(s * 1.5, -0.4, -0.2), 0.3, sides=4))
        F += part(f"dl_leg{s}", rod("prop_l", "doll", p + V(s * 0.4, -1.0, 0), p + V(s * 0.5, -2.4, -0.2), 0.32, sides=4))
    return F


def cloth_bolt(c):
    h = hand(c, 1)
    F = part("cb_roll", tube("prop_l", "fabric", [h + V(-2.6, 0.9, 0.6), h + V(1.6, 0.9, -1.4)], ra=1.25, sides=8, caps=(True, True)))
    F += part("cb_core", tube("prop_l", "wood", [h + V(-3.1, 0.9, 0.85), h + V(2.1, 0.9, -1.65)], ra=0.4, sides=5, caps=(True, True)))
    F += part("cb_spindle", rod("prop_l", "wood2", h + V(0.4, 2.4, -0.2), h + V(0.4, 5.2, -0.4), 0.18, sides=4))
    F += part("cb_yarn", ellipsoid("prop_l", "yarn", h + V(0.4, 3.6, -0.3), (0.7, 0.9, 0.7), segs=6, rings=2))
    return F


def bucket(c):
    h = hand(c, -1)
    b = h + V(0, -4.4, -0.3)
    F = part("bk_body", loft("prop_r", "wood", [ringe(b[1] - 1.6, 1.4, -1.4, 1.4, n=10, cx=b[0], zc=b[2]), ringe(b[1] + 1.6, 1.8, -1.8, 1.8, n=10, cx=b[0], zc=b[2])],
                             caps=("wood", "wood")))
    F += part("bk_ash", disc("prop_r", "ash", b + V(0, 1.75, 0), 1.7, 0.2, n=10))
    for i, dy in enumerate((-0.9, 0.9)):
        F += part(f"bk_hoop{i}", disc("prop_r", "metal2", b + V(0, dy, 0), 1.6 + dy * 0.12 + 0.1, 0.18, n=10))
    F += part("bk_bail", tube("prop_r", "metal2", [b + V(-1.75, 1.5, 0), b + V(-0.8, 3.9, 0), h + V(0, 0.2, 0), b + V(0.8, 3.9, 0), b + V(1.75, 1.5, 0)],
                              ra=0.15, sides=3, caps=(True, True)))
    return F


def herb_basket_arm(c):
    h = hand(c, 1)
    b = h + V(-0.4, -1.6, -1.4)
    F = part("hba", loft("prop_l", "wicker", [ringe(b[1] - 1.2, 1.6, -1.2, 1.2, n=10, cx=b[0], zc=b[2]), ringe(b[1] + 1.0, 2.2, -1.6, 1.6, n=10, cx=b[0], zc=b[2])],
                         caps=("wicker", "wicker")))
    F += part("hba_handle", tube("prop_l", "wicker", [b + V(-2.0, 0.9, 0), b + V(-1.0, 3.2, 0), b + V(1.0, 3.2, 0), b + V(2.0, 0.9, 0)], ra=0.22, sides=4,
                                 caps=(True, True)))
    for i in range(5):
        a = i / 5 * 2 * math.pi
        F += part(f"hbh{i}", spike("prop_l", "leaf" if i % 2 else "leaf2", b + V(1.1 * math.cos(a), 1.0, 0.8 * math.sin(a)), (math.cos(a) * 0.4, 1, math.sin(a) * 0.3),
                                   2.0, 0.4, sides=3, rings=1))
    F += part("hb_flower", ellipsoid("prop_l", "flower", b + V(0.4, 2.0, -0.4), (0.5, 0.4, 0.5), segs=5, rings=1))
    return F


def rope_coil(c):
    h = hand(c, 1)
    F = []
    cen = h + V(0, -1.2, -0.4)
    for k in range(3):
        ring_pts = [cen + V(0.25 * k - 0.25, 1.8 * math.sin(a), 1.8 * math.cos(a) * 0.9) for a in np.linspace(0, 2 * math.pi, 10)]
        F += part(f"rc{k}", tube("prop_l", "rope", ring_pts, 0.42, sides=4, caps=(False, False), hint=(1, 0, 0)))
    F += part("rc_tie", disc("prop_l", "cord", cen + V(0, 1.8, 0), 0.6, 0.6, n=5, axis="x"))
    return F


def baby(c):
    hr, hl = hand(c, -1), hand(c, 1)
    cen = (hr + hl) / 2 + V(0, 1.2, -0.4)
    R = K.rot_z(-22)
    F = part("by_wrap", ellipsoid("prop_r", "fabric", cen, (2.6, 1.4, 1.4), segs=8, rings=3, rot=R))
    F += part("by_head", ellipsoid("prop_r", "babyskin", cen + R @ V(2.2, 0.4, -0.2), (0.95, 0.95, 0.9), segs=7, rings=2))
    F += part("by_hood", ellipsoid("prop_r", "fabric2", cen + R @ V(2.5, 0.5, 0.25), (1.15, 1.15, 0.9), segs=7, rings=2))
    return F


def bread_basket(c):
    h = hand(c, 1)
    b = h + V(-0.8, 0.9, -0.6)
    F = part("bb", loft("prop_l", "wicker", [ringe(b[1] - 0.9, 2.2, -1.5, 1.5, n=10, cx=b[0], zc=b[2]), ringe(b[1] + 0.6, 2.8, -1.9, 1.9, n=10, cx=b[0], zc=b[2])],
                        caps=("wicker", "wicker")))
    F += part("bb_cloth", box("prop_l", "fabric", b + V(0, 0.65, 0), (2.4, 0.15, 1.6)))
    for i, (dx, dz, r) in enumerate(((-1.0, -0.4, 0.0), (0.9, 0.2, 20), (0.0, 0.6, -30))):
        F += part(f"loaf{i}", ellipsoid("prop_l", "bread", b + V(dx, 1.3, dz), (1.2, 0.65, 0.7), segs=7, rings=2, rot=K.rot_y(r)))
    return F


MORE_MATS = {
    "wood2": dict(ramp=[(32, 20, 14), (60, 40, 26), (92, 64, 40), (124, 90, 58)], base=0.56, lit=0.3, noise=0.08, crack=0, min=0, stripe=3, axis="v"),
    "foam": dict(ramp=[(180, 170, 140), (220, 214, 190), (244, 240, 226)], base=0.65, lit=0.3, noise=0.04, crack=0, min=0),
    "bread": dict(ramp=[(110, 62, 26), (160, 100, 44), (206, 146, 70), (232, 186, 110)], base=0.6, lit=0.34, noise=0.08, crack=0.05, min=0),
    "plume": dict(ramp=[(60, 50, 44), (104, 88, 72), (150, 130, 106), (196, 180, 156), (228, 220, 204)], base=0.6, lit=0.3, noise=0.06, crack=0, min=0,
                  stripe=2, axis="v"),
    "plume2": dict(ramp=[(40, 36, 40), (70, 64, 66), (108, 100, 98), (150, 142, 136)], base=0.58, lit=0.3, noise=0.06, crack=0, min=0, stripe=2, axis="v"),
    "eyebird": dict(ramp=[(20, 14, 10), (40, 26, 14)], base=0.6, lit=0.0, noise=0.0, crack=0, min=0),
    "tassel": dict(ramp=[(90, 20, 20), (150, 40, 36), (200, 70, 56), (236, 110, 90)], base=0.6, lit=0.3, noise=0.04, crack=0, min=0, stripe=2, axis="v"),
    "pennant": dict(ramp=[(80, 20, 20), (140, 36, 32), (190, 60, 50), (220, 100, 80)], base=0.6, lit=0.3, noise=0.04, crack=0, min=0),
    "wrap": dict(ramp=[(20, 20, 30), (40, 40, 60), (70, 70, 96)], base=0.6, lit=0.3, noise=0.0, crack=0, min=0, stripe=2, axis="u"),
    "velvet": dict(ramp=[(30, 16, 36), (60, 34, 70), (96, 60, 104), (130, 92, 136)], base=0.56, lit=0.32, noise=0.06, crack=0, min=0),
    "brass2": dict(ramp=[(90, 62, 20), (150, 112, 40), (210, 170, 80), (246, 220, 140)], base=0.6, lit=0.4, noise=0.02, crack=0, min=0),
    "glassg": dict(ramp=[(20, 46, 30), (40, 84, 56), (74, 130, 90), (130, 180, 140), (200, 230, 200)], base=0.56, lit=0.45, noise=0.0, crack=0, min=0),
    "paper2": dict(ramp=[(120, 100, 70), (166, 142, 104), (204, 184, 142), (226, 210, 172)], base=0.6, lit=0.3, noise=0.06, crack=0, min=1),
    "book3": dict(ramp=[(20, 36, 50), (40, 64, 86), (64, 96, 122), (96, 130, 156)], base=0.56, lit=0.3, noise=0.05, crack=0, min=0),
    "doll": dict(ramp=[(110, 50, 50), (160, 80, 72), (200, 120, 100), (226, 160, 140)], base=0.58, lit=0.3, noise=0.06, crack=0, min=0, stripe=2, axis="u"),
    "yarn": dict(ramp=[(120, 60, 30), (170, 100, 50), (210, 146, 80), (236, 190, 120)], base=0.6, lit=0.3, noise=0.06, crack=0, min=0, stripe=2, axis="u"),
    "fabric": dict(ramp=[(130, 120, 100), (176, 166, 144), (214, 206, 186), (236, 230, 214)], base=0.6, lit=0.3, noise=0.08, crack=0, min=0, stripe=3, axis="u"),
    "fabric2": dict(ramp=[(100, 120, 150), (140, 160, 190), (180, 196, 220), (214, 224, 240)], base=0.6, lit=0.3, noise=0.06, crack=0, min=0),
    "babyskin": dict(ramp=[(170, 120, 96), (210, 160, 132), (236, 196, 170), (250, 220, 198)], base=0.72, lit=0.16, noise=0.0, crack=0, min=1),
    "ash": dict(ramp=[(40, 38, 38), (70, 66, 64), (104, 100, 96), (140, 136, 130)], base=0.6, lit=0.3, noise=0.14, crack=0, min=0),
    "dark": dict(ramp=[(10, 8, 8), (24, 18, 16)], base=0.6, lit=0.0, noise=0.0, crack=0, min=0),
}
PROP_MATS.update(MORE_MATS)
