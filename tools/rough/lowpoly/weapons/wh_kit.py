"""岩鯨の武器の共通部品 (様式 mix: A の量塊 + C の使い込み)。縄に吊る護符・ぼろ布・牙。"""
from __future__ import annotations

from wkit import *  # noqa: F401,F403
from wkit import block, blade, prism, curve, extrude, at, V, rot_x, rot_y, rot_z


def rag(c, length, w, sign=1, mat="cloth", th=0.3, tilt=0.0):
    """ぼろ布: 先が不ぞろいに裂けている。"""
    pts = [(0, 0), (w * 0.8 + tilt * 0.3, -length * 0.45),
           (w * 0.1 + tilt, -length), (-w * 0.3 + tilt * 0.8, -length * 0.7),
           (-w * 0.7, -length * 0.3)]
    pts = [(sign * x, y) for x, y in pts]
    if sign < 0:
        pts = pts[::-1]
    return extrude(mat, pts, th, c=c)


def charm(x, y, drop=2.4, mat="edge", z=0.0):
    """縄に吊るした骨の護符。"""
    f = prism("cord", (x, y, z), (x, y - drop, z), 0.2, 0.2, sides=3, caps=(False, False), hint=(0, 0, 1))
    f += block(mat, (x, y - drop - 0.7, z), (0.9, 1.4, 0.8), taper=(0.6, 0.7))
    return f


def tooth(x, y, d, L, r, mat="edge", rings=2):
    return spike(BONE, mat, (x, y, 0), d, L, r, sides=4, rings=rings, hint=(0, 0, 1))
