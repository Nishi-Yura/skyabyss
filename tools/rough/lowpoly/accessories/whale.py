"""岩鯨の装飾品 3 品 (A のみ)。鯨骨 (象牙色) + 青灰の石 (苔) + 苔の縄、光は水色 (武器 A 肋の曲刃と同じ言葉)。
A = 肋の曲刃: 肋の反りが輪郭を作る。どれも直立 (y 上)、広い面は ±Z、正面 -Z。"""
from __future__ import annotations

from common import *

TITAN = "whale"


def cord(c, R=1.4, ax="z"):
    return torus("cord", c, R, 0.32, 0.32, n=6, k=3, ax=ax)


def rib(s, pts, ra, mat="main"):
    """左右どちらかの肋 (s = -1 / +1)。反って先が尖る。"""
    return curve(mat, [(s * x, y, z) for x, y, z in pts], ra, [r * 1.05 for r in ra], sides=4, caps=(True, True))


# ====================================================================== 共鳴の護符 (護符・レア)
def charm_a(r):
    # 石の六角板に水色の核。左右へ反る 2 本の肋が抱く
    f = hexdisc("guard", (0, 0, 0), 4.6, 2.0, sides=6, phase=0)
    f += ell("gem", (0, 0, -1.0), (2.0, 2.0, 1.0), segs=6, rings=1)
    for s in (1, -1):
        f += rib(s, [(4.4, -3.4, 0), (6.5, 0.4, 0), (4.8, 4.4, 0.2), (1.6, 6.2, 0.2)], [1.0, 1.1, 0.75, 0.25])
    f += block("acc", (-1.2, 4.2, -0.2), (3.4, 1.4, 2.6), taper=(0.7, 0.8))
    f += cord((0, 7.6, 0), R=1.3)
    if r in ("rare", "legend"):
        f += block("trim", (0, -4.6, 0), (4.4, 0.9, 2.4))
    return f


# ====================================================================== 苔の護石 (装飾・レア・腰)
def moss_a(r):
    # 苔むした丸い石を、肋の輪が巻く。前に小さな水色
    f = ell("guard", (0, -0.4, 0), (3.6, 4.2, 2.8), segs=6, rings=2)
    f += ell("acc", (0, 1.8, 0.1), (3.9, 2.4, 3.1), segs=6, rings=1)
    f += torus("main", (0, -0.9, 0), 3.9, 0.5, 0.6, n=6, k=4, ax="z")
    f += ell("gem", (0, -1.6, -2.7), (0.9, 1.0, 0.6), segs=6, rings=1)
    f += cord((0, 5.8, 0), R=1.2)
    if r in ("rare", "legend"):
        f += block("trim", (0, -4.4, 0), (3.8, 0.8, 2.8))
    if r == "legend":
        f += rib(1, [(3.2, -2.0, 0.8), (4.8, 0.6, 0.8), (3.4, 3.6, 0.8)], [0.7, 0.7, 0.2])
    return f


# ====================================================================== 岩鯨の補強板 (装飾・伝説・背中)
def plating_a(r):
    # 石の板に、3 本の肋が反って渡る。苔のかたまりと水色の核
    f = extrude("guard", [(-5.2, -6.6), (5.2, -6.6), (6.4, 0), (5.2, 6.6), (-5.2, 6.6), (-6.4, 0)], 2.0)
    for y, w in ((-3.8, 0.9), (0.0, 1.05), (3.8, 0.9)):
        f += curve("main", [(-6.8, y - 0.6, -1.4), (0, y + 0.9, -1.8), (6.8, y - 0.6, -1.4)], [w * 0.5, w, w * 0.5],
                   [w * 0.5, w, w * 0.5], sides=4, caps=(True, True))
    f += ell("gem", (0, 0.3, -2.5), (1.5, 1.7, 0.8), segs=6, rings=1)
    f += block("acc", (-3.6, 6.4, -0.2), (3.6, 1.3, 2.4), taper=(0.7, 0.8))
    f += block("acc", (3.8, -6.5, -0.2), (2.8, 1.1, 2.2), taper=(0.7, 0.8))
    if r in ("rare", "legend"):
        f += block("trim", (0, 6.9, 0), (5.2, 0.8, 2.2))
    if r == "legend":
        f += block("trim", (0, -7.0, 0), (5.6, 0.8, 2.2))
        f += block("spine", (-6.6, 0, 0), (1.0, 4.6, 1.6), taper=(0.6, 0.8))
        f += block("spine", (6.6, 0, 0), (1.0, 4.6, 1.6), taper=(0.6, 0.8))
    return f


ITEMS = dict(
    resonance_charm=dict(jp="共鳴の護符", kind="charm", rar="rare", fn=dict(a=charm_a)),
    moss_talisman=dict(jp="苔の護石", kind="worn", rar="rare", fn=dict(a=moss_a)),
    stone_whale_plating=dict(jp="岩鯨の補強板", kind="worn", rar="legend", fn=dict(a=plating_a)),
)
