"""A 案: 骨の輿 — 丸くふっくらした石の仔鯨。背に鯨骨の肋が両わきから立ち上がって 6 人の輿を囲む。
肋の先に小さな青緑の光の粒 (歌のかけら)、額に光る歌の石。左わきに親ゆずりの小さな錨石と鎖。腹の下に小さな雲。"""
from __future__ import annotations

from ysw import *  # noqa

NAME = "A 骨の輿"
HINT = 512
PAL = dict(mats=dict(BASE_MATS), glow_mats=["mote", "glowbone"])

KEYS = [(-86, 30, 9, 10), (-83, 30, 17, 19), (-74, 30, 23, 27), (-58, 30, 26, 32), (-32, 30, 26, 35), (0, 30, 26, 35),
        (28, 31, 25, 33), (50, 32, 21, 27), (66, 34, 14, 18), (80, 35, 8, 10)]
TAIL = [(56, 36, 12, 15), (72, 37, 9, 10), (88, 38, 5.5, 6), (98, 38, 4, 4.5)]


def body_common(keys, tail, eye_z=-64, eye_r=(4.5, 5.5, 5.5), belly=0.9, fin_len=1.0, fin_w=1.0):
    F = hull("body", "stone", keys, n=12, belly=belly)
    cy, hh, hw = hull_at(keys, eye_z)
    eyes = []
    for s in (1, -1):
        c = V(s * (hw - 1.5), cy + 1.0, eye_z)
        F += ellipsoid("body", "eye", c, eye_r, segs=8, rings=3)
        # 瞼の骨の筋 (目の上の白い弧)
        F += tube("body", "bone", [c + V(-s * 2.5, 5.0, -5.0), c + V(s * 0.8, 6.6, 0), c + V(-s * 2.5, 5.0, 5.5)],
                  [1.3, 1.6, 1.3], sides=4, hint=(0, 1, 0), caps=(True, True))
        eyes.append(dict(c=tuple(c), r=eye_r[2], dir=(s * 1.0, 0.05, -0.3), a=eye_r[1] * 0.78, b=eye_r[1] * 0.86,
                         tilt=0.0, bone="body"))
    # 胸びれ
    for s, b in ((1, "fin_r"), (-1, "fin_l")):
        p = pv(b)
        path = [p + V(-s * 6, 1, -1), p + V(s * 14 * fin_len, -4, 4), p + V(s * 28 * fin_len, -9, 12)]
        F += flat_blade(b, "stone2", path, [8 * fin_w, 6.5 * fin_w, 0], [2.8, 2.0, 0])
    # 尾と尾びれ
    F += hull("tail", "stone", tail, n=10, belly=1.0)
    fp = pv("fluke")
    F += ellipsoid("fluke", "stone2", fp + V(0, 0, -1), (6, 3.2, 6), segs=8, rings=2)
    for s in (1, -1):
        F += flat_blade("fluke", "stone2", [fp + V(0, 0, -2), fp + V(s * 16, 0, 2), fp + V(s * 32, 0, 8)], [6, 7, 0],
                        [2.6, 2.0, 0])
    # 額の歌のかけら
    F += ellipsoid("mote", "mote", pv("mote") + V(0, 0.5, 0), (4.0, 3.2, 4.6), segs=8, rings=2)
    F += tube("mote", "bone", [pv("mote") + V(0, -1.5, 4.2), pv("mote") + V(0, 2.2, 5.4)], [3.0, 2.2], 3.0,
              sides=6, hint=(0, 1, 0), caps=(True, True))
    F += cloud_puffs()
    return F, eyes


def build():
    F, eyes = body_common(KEYS, TAIL)
    # ================================================================ 鞍 (骨の輿)
    # 鞍の台 (背に沈む下の輪 → 上で広がる) と、その上の板張り (上面 = SEAT_Y)
    F += fbox("saddle", "stone2", (0, 48.5, 0), (40, 13, 90), taper=(54 / 40, 106 / 90), cut=0.15)
    F += fbox("saddle", "deck", (0, SEAT_Y - 1.5, 0), (56, 3, 108), cut=0.12)
    for s in (1, -1):
        # 板張りの縁の骨 (低い桟)
        F += tube("saddle", "bone", [V(s * 27.6, 58.5, -52), V(s * 27.6, 58.5, 52)], [1.4, 1.4], sides=5,
                  hint=(0, 1, 0), caps=(True, True))
        # 鯨骨の肋: わき腹から立ち上がり、外へ張って座席を囲む。先に光る粒
        for z in (-48, -16, 16, 48):
            k = 1.0 - 0.12 * (abs(z) > 30)
            path = [V(s * 22, 42, z), V(s * 31, 52, z), V(s * 33.5, 62, z), V(s * 32.5, 62 + 9 * k, z + 1),
                    V(s * 29.5, 63 + 13 * k, z + 2)]
            F += tube("saddle", "bone", path, [2.8, 2.6, 2.3, 1.8, 1.0], sides=5, hint=(0, 0, 1), caps=(True, True))
            F += ellipsoid("saddle", "glowbone", path[-1] + V(0, 0.6, 0), (1.4, 1.4, 1.4), segs=5, rings=2)
    # 前の鞍頭と後ろの背もたれ (骨の弧)
    F += tube("saddle", "bone", [V(-26, 57, -55.5), V(-17, 66, -57), V(0, 70, -58), V(17, 66, -57), V(26, 57, -55.5)],
              [2.2, 2.4, 2.6, 2.4, 2.2], sides=5, hint=(0, 0, 1), caps=(True, True))
    F += tube("saddle", "bone", [V(-26, 57, 55.5), V(-16, 64, 56.5), V(0, 67, 57), V(16, 64, 56.5), V(26, 57, 55.5)],
              [2.2, 2.4, 2.6, 2.4, 2.2], sides=5, hint=(0, 0, 1), caps=(True, True))
    F += ellipsoid("saddle", "glowbone", V(0, 71.5, -58), (1.8, 1.8, 1.8), segs=6, rings=2)
    # 左わきの小さな錨石と鎖 (親の錨の写し)
    F += chain("body", "chain", V(-28.5, 55, 40), V(-32.0, 37, 41.5), links=5, r=1.5)
    F += anchor_stone("body", "stone2", V(-32.4, 37.5, 41.5), size=6.5, mat2="chain")
    return F, eyes, whale_extra()
