"""雷翼の装飾品 4 品 x 3 案。銅・白い羽・紺の鋼、光は白青 (武器 B 翼弓と同じ言葉)。
A = 風切羽の重ね (羽の扇が輪郭) / B = 稲妻の折れ (銅の折れ板と光る筋) / C = 翼の輪 (輪と左右の翼)。
どれも直立 (y 上)。広い面は ±Z。"""
from __future__ import annotations

from common import *

TITAN = "thunder"


def cord(c, R=1.4, ax="z"):
    return torus("cord", c, R, 0.32, 0.32, n=6, k=3, ax=ax)


# ====================================================================== 嵐の護符 (護符・レア)
def charm_a(r):
    f = hexdisc("main", (0, 0, 0), 5.2, 1.8, sides=8, phase=22.5)
    f += extrude("gem", [(1.0, 4.0), (2.8, 4.0), (0.5, -0.2), (-1.3, -0.2)], 0.4, c=(0, 0.2, -1.05))
    f += extrude("gem", [(-1.8, 0.5), (2.0, 0.5), (-1.0, -4.0)], 0.4, c=(0, 0.0, -1.05))
    f += fan("acc", (0, -4.0), 3, 7.0, 26, 1.7, z=1.4)
    f += cord((0, 6.9, 0))
    if r in ("rare", "legend"):
        f += block("trim", (0, 5.3, 0), (2.4, 0.9, 2.2))
        f += block("trim", (0, -5.2, 0.4), (2.6, 0.8, 2.4))
    return f


def charm_b(r):
    f = extrude("main", [(-1.0, 8.0), (3.6, 8.0), (0.9, 1.3), (-3.7, 1.3)], 1.7)
    f += extrude("edge", [(-3.7, 1.6), (2.7, 1.6), (2.0, -1.3), (-4.4, -1.3)], 1.7, c=(0, 0, 0.1))
    f += extrude("main", [(-1.6, -1.1), (2.4, -1.1), (-2.9, -8.2)], 1.7)
    f += ell("gem", (-0.4, 1.4, -1.0), (1.2, 1.2, 0.6), segs=6, rings=1)
    f += feather("acc", (3.0, 6.4, 0.2), (7.4, 9.4, 0.2), 1.2, 0.5, big=False)
    f += feather("acc", (-0.6, 7.0, 0.4), (-5.2, 8.2, 0.4), 1.2, 0.5, big=False)
    f += cord((1.3, 9.2, 0))
    if r in ("rare", "legend"):
        f += block("trim", (0.9, 1.3, -0.9), (5.4, 0.7, 0.7), rot=rot_z(-12))
    return f


def charm_c(r):
    f = torus("main", (0, 0, 0), 4.5, 0.9, 1.0, n=6, k=4, ax="z")
    f += ell("gem", (0, 0, 0), (2.1, 2.1, 1.1), segs=6, rings=2)
    f += fan("acc", (5.0, 0.8), 2, 6.0, 30, 1.5, down=False, mid=72, z=0.2)
    f += mirror_x(fan("acc", (5.0, 0.8), 2, 6.0, 30, 1.5, down=False, mid=72, z=0.2))
    f += block("edge", (0, 5.9, 0), (2.0, 1.4, 1.8))
    if r in ("rare", "legend"):
        f += block("trim", (0, -5.8, 0), (2.4, 0.9, 1.8))
    return f


# ====================================================================== 嵐纏の足環 (装飾・レア・足首)
# 平らに置いた輪 (半径 6.5)。足に付けるときは 0.5 倍にして足首へ。羽は後ろ (+Z)・翼は左右 (±X)
def anklet_a(r):
    f = torus("main", (0, 0, 0), 6.5, 0.9, 1.6, n=8, k=4, ax="y")
    f += ell("gem", (0, 0, -7.3), (1.1, 1.3, 0.8), segs=6, rings=1)
    f += fan("acc", (0, -0.8), 3, 6.8, 24, 1.5, z=6.9)
    if r in ("rare", "legend"):
        f += torus("trim", (0, 1.9, 0), 6.5, 0.6, 0.4, n=8, k=4, ax="y")
    return f


def anklet_b(r):
    f = torus("main", (0, 0, 0), 6.5, 0.9, 1.5, n=8, k=4, ax="y", mats=["main", "spine"] * 4)
    for k in range(4):
        a = math.radians(45 + 90 * k)
        d = V(math.cos(a), 0, math.sin(a))
        f += spike(BONE, "edge", V(*(d * 6.9)) + V(0, 1.0, 0), V(*(d * 0.45)) + V(0, 1, 0), 3.8, 0.85, sides=4, rings=1,
                   hint=(0, 0, 1))
    f += ell("gem", (0, 0, -7.2), (1.1, 1.3, 0.8), segs=6, rings=1)
    if r in ("rare", "legend"):
        f += torus("trim", (0, -1.8, 0), 6.5, 0.6, 0.4, n=8, k=4, ax="y")
    return f


def anklet_c(r):
    f = torus("main", (0, 0, 0), 6.5, 0.7, 1.1, n=8, k=4, ax="y")
    f += fan("acc", (7.2, 0.4), 2, 5.8, 26, 1.3, down=False, mid=76, z=0.0)
    f += mirror_x(fan("acc", (7.2, 0.4), 2, 5.8, 26, 1.3, down=False, mid=76, z=0.0))
    f += ell("gem", (0, 0, -7.1), (1.0, 1.2, 0.7), segs=6, rings=1)
    if r in ("rare", "legend"):
        f += torus("trim", (0, 1.5, 0), 6.5, 0.5, 0.35, n=8, k=4, ax="y")
    return f


# ====================================================================== 嵐の目の羽根 (装飾・伝説・髪に挿す)
def eye_a(r):
    f = feather("acc", (0, -6.0, 0), (0.8, 9.0, 0), 2.8, 0.7, big=True)
    f += feather("acc", (0, -4.6, -0.5), (-4.0, 3.2, -0.5), 1.5, 0.5, big=False)
    f += feather("acc", (0, -4.6, 0.5), (4.0, 2.4, 0.5), 1.5, 0.5, big=False)
    f += ell("gem", (0.3, 2.4, -0.6), (1.3, 2.0, 0.6), segs=6, rings=2)
    f += prism("main", (0, -4.0, -0.5), (0.5, 7.5, -0.5), 0.35, 0.2, sides=4, caps=(True, True), hint=(0, 0, 1))
    f += prism("main", (0, -7.6, 0), (0, -3.8, 0), 1.4, 1.7, sides=6, caps=(True, True), hint=(1, 0, 0))
    if r in ("rare", "legend"):
        f += block("trim", (0, -3.7, 0), (3.6, 0.8, 3.0))
    if r == "legend":
        f += block("trim", (0, -7.8, 0), (3.2, 0.7, 2.8))
        f += feather("acc", (0, -3.6, 0.9), (0, 12.0, 0.9), 1.4, 0.4, big=False)
    return f


def eye_b(r):
    f = extrude("acc", [(0, -6.0), (-2.6, -1.0), (-3.0, 3.0), (-1.0, 9.0), (0, 9.0)], 0.7)
    f += extrude("acc", [(0, -6.0), (2.6, -1.0), (3.0, 3.0), (1.0, 6.4), (0, 6.4)], 0.7, c=(0, 0, 0.35))
    f += prism("spine", (0, -7.0, 0), (0.1, 9.4, 0), 0.4, 0.25, sides=4, caps=(True, True), hint=(0, 0, 1))
    f += block("gem", (-0.6, 3.6, -0.6), (2.6, 0.8, 0.4), rot=rot_z(-28))
    f += block("gem", (0.6, 0.6, -0.6), (2.6, 0.8, 0.4), rot=rot_z(28))
    f += prism("main", (0, -7.6, 0), (0, -3.8, 0), 1.3, 1.6, sides=6, caps=(True, True), hint=(1, 0, 0))
    if r in ("rare", "legend"):
        f += block("trim", (0, -3.7, 0), (3.4, 0.8, 2.8))
    if r == "legend":
        f += block("trim", (0, -7.8, 0), (3.0, 0.7, 2.6))
        f += block("gem", (0.0, -2.0, -0.6), (1.4, 0.8, 0.4), rot=rot_z(-28))
    return f


def eye_c(r):
    f = feather("acc", (0, -6.0, 0.6), (0.6, 9.0, 0.6), 2.8, 0.7, big=True)
    f += torus("main", (0.3, 1.6, -0.5), 2.6, 0.5, 0.6, n=6, k=4, ax="z")
    f += ell("gem", (0.3, 1.6, -0.5), (1.3, 1.3, 0.7), segs=6, rings=1)
    f += prism("main", (0, -7.6, 0), (0, -3.8, 0), 1.4, 1.7, sides=6, caps=(True, True), hint=(1, 0, 0))
    f += feather("acc", (0, -4.4, 1.2), (3.6, 1.2, 1.2), 1.3, 0.4, big=False)
    if r in ("rare", "legend"):
        f += block("trim", (0, -3.7, 0.2), (3.6, 0.8, 3.0))
    if r == "legend":
        f += torus("trim", (0.3, 1.6, -0.5), 3.5, 0.35, 0.4, n=6, k=4, ax="z")
    return f


# ====================================================================== 風切りの羽根 (滑空の道具・手に持つ)
def gale_a(r):
    f = fan("acc", (0, -6.5), 3, 15.5, 9, 3.0, down=False, z=0.0, big=True, dz=0.35)
    for k in range(2):
        f += prism("main", (0, -7.4 + 1.4 * k, 0), (0, -6.5 + 1.4 * k, 0), 1.0, 1.0, sides=6, caps=(True, True), hint=(1, 0, 0))
    f += block("cord", (0.9, -8.8, 0), (0.7, 2.2, 0.7))
    if r in ("rare", "legend"):
        f += block("trim", (0, -4.6, 0), (2.4, 0.7, 2.0))
    return f


def gale_b(r):
    f = feather("spine", (0, -6.5, 0.0), (0.4, 9.0, 0.0), 3.0, 0.7, big=True)
    f += feather("acc", (0, -4.0, -0.6), (0.8, 8.0, -0.6), 1.7, 0.5, big=True)
    f += prism("gem", (0, -5.0, -0.8), (0.4, 6.0, -0.8), 0.35, 0.2, sides=4, caps=(True, True), hint=(0, 0, 1))
    f += prism("main", (0, -7.8, 0), (0, -5.4, 0), 0.7, 1.1, sides=6, caps=(True, True), hint=(1, 0, 0))
    if r in ("rare", "legend"):
        f += block("trim", (0, -5.2, 0), (2.2, 0.7, 1.8))
    return f


def gale_c(r):
    f = fan("acc", (0, -6.0), 3, 15.0, 11, 1.4, down=False, z=0.0, big=True)
    f += prism("main", (0, -7.0, 0), (0, -4.6, 0), 1.0, 1.4, sides=6, caps=(True, True), hint=(1, 0, 0))
    f += ell("gem", (0, -3.6, -0.8), (0.9, 0.9, 0.6), segs=6, rings=1)
    f += block("edge", (0, -8.4, 0), (1.0, 2.0, 1.0))
    if r in ("rare", "legend"):
        f += block("trim", (0, -4.4, 0), (2.4, 0.7, 2.0))
    return f


ITEMS = dict(
    storm_charm=dict(jp="嵐の護符", kind="charm", rar="rare", fn=dict(a=charm_a, b=charm_b, c=charm_c)),
    storm_anklet=dict(jp="嵐纏の足環", kind="worn", rar="rare", fn=dict(a=anklet_a, b=anklet_b, c=anklet_c)),
    storm_eye_feather=dict(jp="嵐の目の羽根", kind="worn", rar="legend", fn=dict(a=eye_a, b=eye_b, c=eye_c)),
    gale_feather=dict(jp="風切りの羽根", kind="held", rar="common", fn=dict(a=gale_a, b=gale_b, c=gale_c)),
)
