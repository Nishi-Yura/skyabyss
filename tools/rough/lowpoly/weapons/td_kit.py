"""潮殻の武器の共通部品。祭具の流れる優雅さ (曲線・真珠層・珊瑚・布の帯) + 使い込み (藤壺・海藻の巻き・欠け)。
座標は他の武器と同じ (1 単位 = 1/16 ブロック、y 上、正面 -Z、握りは各 item の GRIP)。"""
from __future__ import annotations

import math

from wkit import *  # noqa: F401,F403
from wkit import block, blade, prism, curve, extrude, at, V, rot_x, rot_y, rot_z
import lpkit


def sash(pts, w0, mat="cloth", th=0.22, hint=(1, 0, 0), taper=0.35):
    """布の帯: 薄い平らな筒。pts に沿って流れ、先が細る。hint = 幅の向き。"""
    n = len(pts)
    ra = [w0 * (1 - (1 - taper) * i / (n - 1)) for i in range(n)]
    ra[-1] = w0 * taper * 0.6
    return curve(mat, pts, ra, [th] * n, sides=4, caps=(False, True), hint=hint)


def pearl(c, r, mat="gem", segs=6, rings=1):
    return lpkit.ellipsoid(BONE, mat, c, (r, r, r), segs=segs, rings=rings)


def barnacles(c, n=3, spread=0.9, r=0.42, h=0.9, dirv=(0, 0, -1), mat="guard"):
    """藤壺: 小さな三角錐の群れ。c の周りに n 個。"""
    out = []
    d = V(*dirv)
    for i in range(n):
        a = 2.4 * i + 0.7
        off = V(math.cos(a) * spread, math.sin(a * 1.3) * spread * 0.9, 0)
        if abs(d[2]) < 0.5:
            off = V(0, off[1], off[0])
        out += spike(BONE, mat, V(*c) + off, d, h * (0.7 + 0.3 * (i % 2)), r, sides=3, rings=1, hint=(0, 1, 0))
    return out


def kelp_wrap(y0, y1, rad=1.14, bands=3, mat="wrap"):
    """海藻の巻き: 柄に帯を数本巻く (太い輪の積み)。"""
    out = []
    for i in range(bands):
        y = y0 + (y1 - y0) * (i + 0.5) / bands
        out += prism(mat, (0, y - 0.45, 0), (0, y + 0.45, 0), rad, rad, sides=6, caps=(False, False), phase=15 * i)
    return out


def scallop(c, n, length, spread, w, th=0.6, mat="main", z=0.0, mid=0.0, mats=None):
    """帆立の扇 (xy 平面、広い面は ±Z): 根元 c から n 枚の板が開く。"""
    out = []
    for i in range(n):
        a = math.radians(mid + (i - (n - 1) / 2) * spread)
        L = length * (1.0 - 0.14 * abs(i - (n - 1) / 2) / max(1, (n - 1) / 2))
        tip = (c[0] + math.sin(a) * L, c[1] + math.cos(a) * L, z)
        out += feather(mat, (c[0], c[1], z), tip, w, th, big=False, mats=mats)
    return out
