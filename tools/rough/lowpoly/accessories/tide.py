"""潮殻の装飾品 4 品 (A のみ)。真珠層 (クリーム) + 濡れた石 + 珊瑚の枝 (桃・橙) + 海藻の縄、光は桃 (武器 A 珊瑚の枝槍と同じ言葉)。
A = 珊瑚の枝: 枝分かれする珊瑚が輪郭を作る。どれも直立 (y 上)、広い面は ±Z、正面 -Z。"""
from __future__ import annotations

from common import *

TITAN = "tide"


def cord(c, R=1.3, ax="z"):
    return torus("cord", c, R, 0.32, 0.32, n=5, k=3, ax=ax)


def branch(base, pts, ra, twig=None, mat="acc"):
    """珊瑚の枝 1 本。pts (3 点) の先細りの筒 + 途中から出る小枝 (twig = (どの点から, 向き, 長さ))。"""
    f = curve(mat, pts, ra, [x * 1.1 for x in ra], sides=4, caps=(True, True))
    if twig:
        i, d, ln = twig
        f += spike(BONE, mat, V(*pts[i]), d, ln, ra[i] * 0.8, sides=4, rings=1, hint=(0, 0, 1))
    return f


# ====================================================================== 潮鳴りの護符 (護符・レア)
def chime_a(r):
    # 真珠層の貝扇の円盤に桃の真珠。上へ珊瑚の枝が 2 本、下に濡れた石
    f = hexdisc("main", (0, -0.6, 0), 4.6, 1.8, sides=6, phase=0)
    f += ell("gem", (0, -0.6, -1.0), (1.8, 1.8, 1.0), segs=6, rings=1)
    f += block("guard", (0, -5.4, 0), (5.6, 1.6, 2.4), taper=(0.7, 0.8))
    f += branch(None, [(-1.6, 3.2, 0), (-3.6, 6.0, 0), (-3.2, 8.2, 0)], [0.9, 0.7, 0.2], twig=(1, (-1, 0.3, 0), 2.6))
    f += branch(None, [(1.6, 3.2, 0), (3.2, 6.6, 0), (2.4, 9.0, 0)], [0.9, 0.7, 0.2], twig=(1, (1, 0.4, 0), 2.4))
    f += cord((0, 3.4, 0), R=1.0)
    if r in ("rare", "legend"):
        f += block("trim", (0, -3.7, 0), (5.2, 0.8, 2.2))
    return f


# ====================================================================== 月潮の真珠 (装飾・伝説・胸)
def pearl_a(r):
    # 大きな真珠を、珊瑚の枝の籠が抱く。桃の三日月が内側に光る
    f = ell("main", (0, 0.8, 0), (3.4, 3.4, 3.4), segs=8, rings=2)
    f += ell("gem", (-0.4, 1.4, -2.6), (1.4, 1.6, 0.7), segs=6, rings=1)
    for k in range(3):
        a = math.radians(90 + 120 * k)
        d = V(math.cos(a), 0, math.sin(a))
        f += branch(None, [tuple(d * 2.4 + V(0, -4.4, 0)), tuple(d * 4.6 + V(0, -1.2, 0)), tuple(d * 3.4 + V(0, 3.2, 0))],
                    [0.9, 0.7, 0.2], twig=None)
    f += prism("guard", (0, -6.4, 0), (0, -4.2, 0), 2.6, 3.4, sides=6, caps=(True, True), hint=(1, 0, 0))
    f += cord((0, 5.2, 0), R=1.1)
    if r in ("rare", "legend"):
        f += block("trim", (0, -4.2, 0), (6.4, 0.8, 4.4), rot=rot_y(30))
    if r == "legend":
        f += torus("trim", (0, 0.8, 0), 4.7, 0.3, 0.3, n=5, k=3, ax="z")
    return f


# ====================================================================== 潮見の貝 (語り部の品・手に持つ)
def watch_a(r):
    # 帆立の扇の貝。蝶番の根元に珊瑚の小枝
    f = fan("main", (0, -5.8), 5, 10.5, 24, 2.7, th=0.9, down=False, z=0.0, big=False, dz=0.0)
    f += fan("spine", (0, -5.8), 3, 9.0, 36, 1.0, th=1.1, down=False, z=0.0, big=False, dz=0.0)
    f += prism("guard", (0, -6.8, 0), (0, -5.2, 0), 1.7, 2.1, sides=6, caps=(True, True), hint=(1, 0, 0))
    f += ell("gem", (0, -3.6, -0.9), (1.0, 1.1, 0.6), segs=6, rings=1)
    f += spike(BONE, "acc", V(1.0, -6.6, 0), (0.5, -1, 0), 3.2, 0.8, sides=4, rings=1, hint=(0, 0, 1))
    if r in ("rare", "legend"):
        f += block("trim", (0, -5.3, 0), (3.4, 0.7, 2.0))
    return f


# ====================================================================== 潮の真珠 (庭の真珠・手に持つ)
def tpearl_a(r):
    f = ell("main", (0, 0.8, 0), (3.2, 3.2, 3.2), segs=8, rings=2)
    f += ell("gem", (-0.4, 1.4, -2.5), (1.1, 1.3, 0.6), segs=6, rings=1)
    for k in range(3):
        a = math.radians(90 + 120 * k)
        d = V(math.cos(a), 0, math.sin(a))
        f += spike(BONE, "acc", V(*(d * 2.0)) + V(0, -3.8, 0), tuple(d * 0.5 + V(0, 1, 0)), 7.0, 0.9, sides=4, rings=2, hint=(0, 0, 1))
    f += prism("guard", (0, -6.0, 0), (0, -3.6, 0), 2.6, 3.4, sides=6, caps=(True, True), hint=(1, 0, 0))
    if r in ("rare", "legend"):
        f += torus("trim", (0, -3.8, 0), 3.2, 0.4, 0.4, n=6, k=3, ax="y")
    return f


ITEMS = dict(
    tide_chime_charm=dict(jp="潮鳴りの護符", kind="charm", rar="rare", fn=dict(a=chime_a)),
    moontide_pearl=dict(jp="月潮の真珠", kind="worn", rar="legend", fn=dict(a=pearl_a)),
    tide_watching_shell=dict(jp="潮見の貝", kind="held", rar="common", fn=dict(a=watch_a)),
    tide_pearl=dict(jp="潮の真珠", kind="held", rar="common", fn=dict(a=tpearl_a)),
)
