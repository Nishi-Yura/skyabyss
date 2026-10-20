"""武器の細かさ D2 (整え): 大きい箱の面取り + 柄と鍔の細い金属の輪 + 宝石 1 つ + 光る細い線 1 本。防具 S2 (sstyle) と揃える。
輪郭と寸法は変えない。各 item_*.py の末尾で wd2.install(ID, __name__) して faces() を D2 にする (どのビルドからも同じ形が出る)。
L=3 は細密 (D3) の試作用で、本番では使わない。"""
from __future__ import annotations

import math
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
import numpy as np
import wkit
from wkit import block, prism, curve, extrude, blade, V, rot_x, rot_y, rot_z, BONE
from td_kit import pearl
from st_kit import plume, band as sband
from wh_kit import tooth
from em_kit import ridge, chain

CH = 0.38


# ------------------------------------------------------------------ 追加の部品
def gbar(pts, r=0.27, mat="gem"):
    """光る細い線 (表面に半分沈める)。"""
    return curve(mat, pts, [r] * len(pts), [r] * len(pts), sides=3, caps=(True, True), hint=(0, 0, 1), phase=90.0)


def ringb(mat, c, r, h, sides=6, axis="y"):
    c = V(*c)
    if axis == "y":
        return prism(mat, tuple(c - V(0, h / 2, 0)), tuple(c + V(0, h / 2, 0)), r, r, sides=sides, caps=(False, False), hint=(0, 0, 1))
    return prism(mat, tuple(c - V(0, 0, h / 2)), tuple(c + V(0, 0, h / 2)), r, r, sides=sides, caps=(True, True), hint=(1, 0, 0))


def chev(mat, x, y, z, w=1.7, t=0.5, up=1, ang=34):
    """山形の浮き彫り (山形の 2 本)。"""
    f = []
    for s in (1, -1):
        f += block(mat, (x - s * w * 0.42, y - up * w * 0.17, z), (w, 0.5, t), rot=rot_z(-s * up * ang))
    return f


def interp(xs, ys):
    return lambda y: float(np.interp(y, xs, ys))


def ex_gs(L):
    cx = interp([5.2, 10, 14.2, 17.4, 20], [0.0, 0.5, 1.1, 1.8, 2.6])
    tf = interp([5.2, 10, 14.2, 17.4, 19.6], [1.3, 1.5, 1.3, 1.0, 0.8])
    f = gbar([(cx(y), y, -tf(y) + 0.05) for y in (6.4, 9.5, 13.0, 16.4)])
    f += ringb("trim", (0, 3.0, 0), 1.75, 0.5)
    f += ringb("trim", (0, -0.9, 0), 1.6, 0.45)
    f += block("gem", (0, -2.5, -1.6), (1.1, 1.1, 0.5))
    if L >= 3:
        for y in (0.5, 1.7):
            f += ringb("wrap", (0, y, 0), 1.5, 0.4)
        for y in (7.8, 10.2):
            f += chev("spine", cx(y), y, -tf(y) + 0.1, w=2.2)
        f += ringb("spine", (0, 2.5, 0), 2.7, 0.6, sides=6)
        f += ringb("trim", (0, 5.0, 0), 3.85, 0.4, sides=6)
        for s in (1, -1):
            f += tooth(s * 3.7, 4.8, (s * 0.5, 1, 0), 3.2, 0.75)
            f += block("gem", (s * 2.3, 4.2, -1.55), (0.7, 0.7, 0.4))
    return f


def spike_acc(s):
    return wkit.spike(BONE, "acc", (s * 1.8, 12.0, 0), (s * 1.0, 0.5, 0), 2.8, 0.6, sides=4, rings=1, hint=(0, 0, 1))


def ex_tr(L):
    f = gbar([(0, 14.3, -0.98), (0, 17.0, -0.98), (0, 19.6, -0.5)])
    f += ringb("trim", (0, 4.9, 0), 1.4, 0.5)
    f += ringb("trim", (0, 8.8, 0), 1.4, 0.45)
    for s in (1, -1):
        f += block("acc", (s * 1.7, 12.3, -2.0), (0.9, 0.9, 0.7), taper=(0.6, 0.6))
    if L >= 3:
        for y in (6.0, 7.2, 10.2):
            f += ringb("trim", (0, y, 0), 1.3, 0.3)
        for y in (15.4, 16.8):
            f += chev("edge", 0, y, -0.95, w=1.5)
        for y in (-0.2, 2.2):
            f += pearl((0.0, y, -1.15), 0.55)
        for s in (1, -1):
            f += spike_acc(s)
        f += ringb("trim", (0, 13.4, 0), 2.9, 0.4, sides=8)
    return f


def ex_bow(L, pull=0.0):
    CYv = 8.0
    up = [(5.0, 10.6), (4.3, 13.0), (1.8 - pull * 0.5, 15.8), (-2.4 - pull, 17.2)]
    zs = [-0.95, -0.85, -0.65, -0.35]
    f = []
    for sg in (1, -1):
        my = (lambda p: p) if sg > 0 else (lambda p: (p[0], 2 * CYv - p[1]))
        f += gbar([(*my(up[i]), zs[i]) for i in range(3)], r=0.25)
        f += sband((*my(up[1]), 0), (1, sg * 0.6, 0), 1.25, 0.6, "trim", sides=6)
        f += sband((*my(up[3]), 0), (1, sg * 0.3, 0), 0.7, 0.5, "trim", sides=6)
    for dy in (-0.55, 0.55):
        f += ringb("wrap", (5.0, CYv + dy, 0), 1.4, 0.35)
    if L >= 3:
        for sg in (1, -1):
            my = (lambda p: p) if sg > 0 else (lambda p: (p[0], 2 * CYv - p[1]))
            f += sband((*my(up[0]), 0), (1, sg * 0.2, 0), 1.2, 0.5, "trim", sides=6)
            f += plume((*my((3.0, 12.4)), 0.7), (*my((7.4, 13.4)), 0.7), 0.7, 0.3, bend=-0.3 * sg, scorch=1)
            f += block("gem", (*my((4.6, 12.0)), -0.95), (0.7, 0.7, 0.4))
            f += plume((6.0, CYv + sg * 1.8, -0.2), (9.4, CYv + sg * 4.6, -0.2), 0.8, 0.3, bend=-0.6 * sg, scorch=1)
        f += ringb("spine", (5.0, CYv + 1.9, 0), 1.35, 0.4)
        f += ringb("spine", (5.0, CYv - 1.9, 0), 1.35, 0.4)
    return f


def wh_charm():
    from wh_kit import charm
    return charm(2.0, 4.4, 2.0, z=-1.4)


def ex_fang(L):
    cx = interp([5.8, 8, 11, 14], [0, 0.4, 1.3, 2.5])
    tf = interp([5.8, 8, 11, 14], [1.1, 1.2, 1.0, 0.6])
    f = gbar([(cx(y), y, -tf(y) + 0.05) for y in (6.8, 9.0, 11.4, 13.0)], r=0.25)
    for y in (1.8, 3.0):
        f += ringb("wrap", (0, y, 0), 1.35, 0.4)
    f += block("gem", (0, 0.2, -1.55), (0.9, 0.8, 0.4))
    f += ringb("trim", (0, 3.9, 0), 1.5, 0.4)
    if L >= 3:
        for y in (7.4, 9.0):
            f += chev("spine", cx(y), y, -tf(y) + 0.1, w=1.8)
        f += ringb("spine", (0, 4.6, 0), 1.75, 0.4)
        for y in (8.6, 10.6, 12.4):
            f += tooth(-2.4 + cx(y) * 0.2, y, (-1, 0.35, 0), 1.9, 0.5)
        f += wh_charm()
    return f


def ex_axe(L):
    cy = 14.0
    f = gbar([(-1.0, cy - 2.5, -1.5), (-2.4, cy - 0.8, -1.5), (-3.4, cy - 1.9, -1.5), (-4.6, cy + 0.5, -1.45)])
    f += ringb("edge", (0, 9.6, 0), 1.45, 0.4)
    f += ringb("edge", (0, 11.2, 0), 1.45, 0.4)
    f += block("gem", (0, -2.7, -1.3), (0.9, 0.8, 0.4))
    if L >= 3:
        for y in (1.2, 2.6, 4.0, 6.4, 8.0):
            f += ringb("wrap", (0, y, 0), 1.3, 0.35)
        pl = [(-1.2, -2.6), (-3.8, -3.4), (-4.9, -0.6), (-4.2, 2.6), (-1.2, 2.8)]
        for z in (-1.3, 1.3):
            f += extrude("spine", pl, 0.7, c=(0, cy, z))
        f += gbar([(-1.2, cy + 1.2, -1.66), (-2.4, cy - 0.2, -1.66), (-3.6, cy + 0.9, -1.66)], r=0.2)
        f += ridge((1.0, cy + 2.4, 0), (0.1, 1, 0), 3, 2.4, (0.0, 0.0, 0.0), 0.6)
        f += ringb("trim", (0, cy - 4.2, 0), 1.6, 0.45)
        f += ringb("trim", (0, cy + 3.6, 0), 1.6, 0.45)
    return f


def ex_shield(L):
    OUT_ = [(0, -1.0), (3.8, 2.4), (5.6, 7.4), (4.8, 12.4), (2.6, 15.8), (0, 17.0), (-2.6, 15.8), (-4.8, 12.4), (-5.6, 7.4), (-3.8, 2.4)]

    def sh(k):
        return [(x * k, 8.0 + (y - 8.0) * k) for x, y in OUT_]
    ring_ = sh(0.66)
    f = gbar([(x, y, -1.62) for x, y in ring_ + [ring_[0]]], r=0.22)
    f += ringb("trim", (0, 8.4, -1.6), 2.1, 0.5, axis="z", sides=8)
    if L >= 3:
        r2 = sh(0.5)
        f += gbar([(x, y, -1.62) for x, y in r2 + [r2[0]]], r=0.2, mat="trim")
        for x, y in sh(0.93)[::2]:
            f += block("acc", (x, y, -1.2), (0.8, 0.8, 0.6), taper=(0.6, 0.6))
        for dx in (-3.0, 3.0):
            f += wkit.spike(BONE, "acc", (dx, 15.0 - abs(dx) * 0.4, -0.3), (dx * 0.12, 1, 0), 2.6, 0.7, sides=4, rings=1, hint=(0, 0, 1))
        f += wkit.spike(BONE, "acc", (0, 16.4, -0.3), (0, 1, 0), 3.0, 0.8, sides=4, rings=1, hint=(0, 0, 1))
    return f


def _blade_gbar(xf_, zf, ys, r=0.25, mat="gem"):
    return gbar([(xf_(y), y, -zf(y) + 0.05) for y in ys], r=r, mat=mat)


def ex_pike(L):
    cx = interp([12.2, 14, 16.6, 19.5], [0, 0.2, 0.8, 1.7])
    tf = interp([12.2, 14, 16.6, 18.4], [1.0, 1.1, 0.8, 0.5])
    f = _blade_gbar(cx, tf, (13.2, 15.2, 17.0))
    f += ringb("trim", (0, 0.4, 0), 1.45, 0.4)
    f += ringb("trim", (0, 5.2, 0), 1.45, 0.4)
    f += ringb("trim", (0, 9.0, 0), 1.3, 0.4)
    f += block("gem", (0, -2.5, -1.4), (0.9, 0.9, 0.5))
    return f


def ex_harpoon(L):
    tf = interp([12.6, 15.6, 18.0, 21.0], [0.8, 0.9, 0.45, 0.0])
    f = _blade_gbar(lambda y: 0.0, tf, (14.0, 16.4, 18.8))
    f += ringb("trim", (0, 0.0, 0), 1.2, 0.4)
    f += ringb("trim", (0, 5.4, 0), 1.25, 0.4)
    f += ringb("trim", (0, 10.4, 0), 1.15, 0.4)
    f += block("gem", (0, 6.4, -0.95), (0.8, 0.8, 0.5))
    return f


def ex_tcclaw(L):
    cx = interp([6.4, 10, 14, 17.4, 19.2], [1.4, 3.8, 4.4, 3.0, 1.2])
    tf = interp([6.4, 10, 14, 17.4], [1.0, 1.1, 0.9, 0.5])
    f = _blade_gbar(cx, tf, (9.0, 11.6, 14.2, 16.4))
    f += ringb("trim", (0, 0.6, 0), 1.35, 0.4)
    f += ringb("trim", (0, 3.8, 0), 1.35, 0.4)
    f += block("gem", (0, 5.0, -1.6), (1.0, 0.8, 0.4))
    return f


def ex_raiju(L):
    c0 = lambda y: 0.025 * max(0.0, y - 6.2) ** 2
    f = gbar([(c0(y), y, -0.85) for y in (7.6, 10.4, 13.2)], r=0.22)
    f += ringb("trim", (0, 0.9, 0), 1.15, 0.4)
    f += ringb("trim", (0, 4.3, 0), 1.2, 0.4)
    f += block("gem", (0, -0.2, -1.1), (0.8, 0.6, 0.4))
    return f


def ex_glaive(L):
    from_ = interp([13.0, 15.0, 17.2, 19.2, 21.0], [0.0, 0.4, 1.4, 3.0, 4.8])
    tf = interp([13.0, 15.0, 17.2, 19.2], [0.9, 1.0, 0.9, 0.7])
    f = _blade_gbar(from_, tf, (14.4, 16.2, 17.8, 19.2), r=0.22)
    f += ringb("trim", (0, -0.6, 0), 1.0, 0.4)
    f += ringb("trim", (0, 4.4, 0), 1.1, 0.4)
    f += ringb("trim", (0, 9.4, 0), 1.1, 0.4)
    f += block("gem", (0, 10.8, -0.85), (0.8, 0.9, 0.4))
    return f


def ex_poker(L):
    f = gbar([(0.4, 16.2, -0.86), (2.0, 18.0, -0.76), (3.4, 17.3, -0.6)], r=0.2)
    f += ringb("trim", (0, -0.6, 0), 1.25, 0.4)
    f += ringb("trim", (0, 2.9, 0), 1.25, 0.4)
    f += ringb("trim", (0, 6.0, 0), 1.15, 0.4)
    f += ringb("trim", (0, 14.4, 0), 1.05, 0.4)
    f += block("gem", (0, -2.7, -1.1), (0.9, 0.8, 0.4))
    return f


def ex_hammer(L):
    f = gbar([(0, 6.4, -1.08), (0, 10.0, -1.08), (0, 13.4, -1.08)], r=0.2)
    f += ringb("trim", (0, -3.6, 0), 1.4, 0.4)
    f += ringb("trim", (0, 8.6, 0), 1.4, 0.4)
    f += ringb("trim", (0, 12.0, 0), 1.35, 0.4)
    f += block("gem", (0, -6.4, -1.3), (0.8, 0.7, 0.4))
    return f


# ---- 深淵の伝説 (選ばれた案だけ: 大剣 B / 鐘の杖 C / 弓 B / 鎌 A)
def ex_a_gs(L):
    f = gbar([(0, 6.8, -1.27), (0, 10.5, -1.25), (0, 14.5, -1.05), (0, 17.8, -0.85)], r=0.25)
    f += ringb("trim", (0, 2.7, 0), 1.3, 0.4)
    f += ringb("trim", (0, -0.9, 0), 1.25, 0.4)
    f += block("gem", (0, -2.3, -1.45), (1.0, 0.8, 0.4))
    return f


def ex_a_st(L):
    f = gbar([(0, 6.4, -0.92), (0, 9.4, -0.92), (0, 12.2, -0.92)], r=0.2)
    f += ringb("trim", (0, 2.4, 0), 1.3, 0.4)
    f += ringb("trim", (0, 9.0, 0), 1.1, 0.4)
    f += ringb("trim", (0, 12.0, 0), 1.1, 0.4)
    return f


def ex_a_bow(L, pull=0.0):
    CYv = 8.0
    up = [(5.0, 10.6), (4.4, 12.8), (2.8, 15.0), (0.4 - pull * 0.5, 16.8)]
    rz = [1.2, 1.05, 0.85, 0.65]
    f = []
    for sg in (1, -1):
        my = (lambda p: p) if sg > 0 else (lambda p: (p[0], 2 * CYv - p[1]))
        f += gbar([(*my(up[i]), -rz[i] * 0.85) for i in (1, 2, 3)], r=0.22)
    for dy in (-1.7, 1.7):
        f += ringb("trim", (5.0, CYv + dy, 0), 1.3, 0.35)
    return f


def ex_a_scythe(L):
    f = gbar([(-3.0, 17.3, -0.78), (-7.0, 18.1, -0.78), (-10.4, 16.3, -0.7)], r=0.22)
    f += ringb("trim", (0, 2.4, 0), 1.3, 0.4)
    f += ringb("trim", (0, 9.0, 0), 1.1, 0.4)
    f += ringb("trim", (0, 12.0, 0), 1.1, 0.4)
    return f


EX = dict(whalebone_greatsword=ex_gs, tide_shell_trident=ex_tr, thunder_wing_bow=ex_bow, waterway_fang=ex_fang,
          ember_war_axe=ex_axe, coral_shield=ex_shield, captain_pike=ex_pike, coral_harpoon=ex_harpoon,
          tidecaller_claw=ex_tcclaw, raiju_claw=ex_raiju, warden_glaive=ex_glaive, thrall_poker=ex_poker,
          smith_hammer=ex_hammer, abyss_greatsword=ex_a_gs, abyss_bell_staff=ex_a_st, abyss_storm_bow=ex_a_bow,
          abyss_ash_scythe=ex_a_scythe)
PULL = [0.0, 0.8, 1.7, 2.6]   # d_thunder.TIP_PULL と同じ (弓の先が弦の側へ寄る量)
BOWS = ("thunder_wing_bow", "abyss_storm_bow")


def d2(wid, fn, level=2):
    """faces 関数を D2 にして返す (大きい箱を面取りして、細部を足す)。"""
    def g(*a, **k):
        wkit.CHAMFER[0] = CH
        try:
            fs = fn(*a, **k)
        finally:
            wkit.CHAMFER[0] = 0.0
        if wid.startswith("abyss_"):
            stage = a[0] if a else k.get("stage", 0)
        elif wid in BOWS:
            stage = a[1] if len(a) > 1 else k.get("stage", 0)
        else:
            stage = 0
        if wid in BOWS:
            return fs + EX[wid](level, pull=PULL[stage])
        return fs + EX[wid](level)
    g.__wrapped__ = fn
    return g


def install(wid, modname):
    m = sys.modules[modname]
    m.faces = d2(wid, m.faces)
