"""C 案: 藤壺の椀 — いちばん丸い仔鯨が、背に藤壺だらけの半分の貝殻 (帆立の縁の深い椀) を背負う。
椀の中が 6 人の床。藤壺の口のいくつかが青緑に光る (歌のかけら)。椀の前の縁から親ゆずりの鎖と小さな錨石が垂れる。"""
from __future__ import annotations

from ysw import *  # noqa
from opt_a import body_common  # noqa

NAME = "C 藤壺の椀"
HINT = 512
PAL = dict(mats=dict(BASE_MATS), glow_mats=["mote", "glowbone"])

KEYS = [(-84, 29, 10, 11), (-80, 29, 19, 21), (-70, 29, 25, 30), (-52, 29, 27, 35), (-24, 29, 27, 37), (8, 29, 27, 37),
        (32, 30, 25, 34), (52, 32, 20, 27), (68, 34, 13, 17), (80, 35, 7, 9)]
TAIL = [(56, 34, 12, 14), (72, 36, 8.5, 9.5), (88, 37, 5.5, 6), (98, 38, 4, 4.5)]
AX, AZ, PW, TH = 31.5, 60.0, 4.0, 3.5    # 椀の内側の半径 (角の丸い四角の指数 PW)、壁の厚み
Y0, Y1, FL, NW = 34.0, 64.0, 8.0, 42      # 椀の壁の下と縁の高さ、縁の外への開き、輪の分割


def bowl_point(a, y, out=0.0):
    """椀の外の面の上の点と外向き (角度 a、高さ y)。"""
    s, c = math.sin(a), -math.cos(a)
    e = 2.0 / PW
    x = AX * math.copysign(abs(s) ** e, s)
    z = AZ * math.copysign(abs(c) ** e, c)
    gx = math.copysign(abs(x / AX) ** (PW - 1), x) / AX
    gz = math.copysign(abs(z / AZ) ** (PW - 1), z) / AZ
    o = nrm(V(gx, 0, gz))
    flare = FL * (y - Y0) / (Y1 - Y0)
    return V(x, y, z) + o * (TH + flare + out), o


def rim_top(a, x=None, z=None):
    """縁の高さの足し: 前は低く、後ろは背もたれのように高い (開いた貝の上の殻)。"""
    zz = -math.cos(a)
    return (3.0 * zz) if zz < 0 else (5.0 * zz + (4.0 * (zz - 0.5) if zz > 0.5 else 0.0))


def build():
    F, eyes = body_common(KEYS, TAIL, eye_z=-62, eye_r=(5.0, 6.0, 6.0), belly=0.88, fin_len=0.85, fin_w=1.2)
    # ================================================================ 椀 (半分の貝殻)
    wall, secs = oval_wall("saddle", "shell", Y0, Y1, AX, AZ, TH, n=NW, flare=FL, rim_wave=2.4, waves=7,
                           mats=["shell", "shell", "rim", "rim"], p=PW, top_fn=rim_top)
    F += wall
    # 椀の床 (上面 = SEAT_Y)
    F += fbox("saddle", "deck", (0, SEAT_Y - 1.5, 0), (60, 3, 116), cut=0.3)
    # 外の面の放射の肋 (帆立の畝): 縦の細い骨
    for k in range(NW):
        if k % 3:
            continue
        a = 2 * math.pi * k / NW
        p0, o = bowl_point(a, Y0 + 3, -0.6)
        p1, _ = bowl_point(a, Y1 - 1.5 + 2.4 * math.cos(7 * a) + rim_top(a), -0.6)
        F += tube("saddle", "rim", [p0, p1], [1.3, 1.6], sides=4, hint=o, caps=(True, True))
    # 藤壺 (光る口がいくつか)
    rng = np.random.default_rng(31)
    spots = [(0.3, 44), (0.9, 52), (1.4, 40), (2.0, 50), (2.6, 46), (3.3, 54), (3.9, 42), (4.5, 50), (5.1, 44),
             (5.7, 52), (0.6, 58), (2.3, 58), (4.0, 58), (5.4, 58), (1.7, 36), (4.8, 37)]
    for i, (a, y) in enumerate(spots):
        p, o = bowl_point(a, y, -0.6)
        d = nrm(o + V(0, 0.45, 0))
        r = 2.2 + 1.2 * rng.random()
        F += tube("saddle", "barn", [p, p + d * 2.6, p + d * 3.4], [r, r * 0.75, r * 0.45], sides=6, hint=(0, 1, 0),
                  caps=(True, True))
        if i % 4 == 1:
            F += ellipsoid("saddle", "glowbone", p + d * 3.3, (r * 0.4, r * 0.4, r * 0.4), segs=5, rings=2)
    # 体の藤壺 (頭と尾)
    for (x, y, z) in ((10, 50, -70), (-12, 48, -72), (16, 44, 54), (-14, 46, 50)):
        cy, hh, hw = hull_at(KEYS, z)
        d = nrm(V(x / hw, (y - cy) / hh, 0))
        F += tube("body", "barn", [V(x, y, z) - d * 1.0, V(x, y, z) + d * 2.4], [2.4, 1.4], sides=6, hint=(0, 0, 1),
                  caps=(True, True))
    # 椀の前の縁から垂れる鎖と小さな錨石
    F += chain("saddle", "chain", V(-24, 61, -63), V(-38, 44, -52), links=6, r=1.6)
    F += anchor_stone("saddle", "stone2", V(-38.5, 44.5, -52), size=6.5, mat2="chain")
    return F, eyes, whale_extra(mouth_z=(-84, -54), mouth_y0=25.5)
