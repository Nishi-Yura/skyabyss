# -*- coding: utf-8 -*-
"""嵐の杖（x32・レア）。前の形の言葉: 羽根の束の頭（白い羽根・先が黒い）、銅の巻きが柄を登る、暗い鉄紺の柄。
嵐のシリーズの品。基準は tide_hammer と同じ作りの水準（留め具の淡い青の輪・柄頭・握りの帯）。
色（series/storm.py）: 鉄紺の柄と輪、銅の帯と巻き、白い羽根、雷の光（一つ）。
レア（60 の 1.6）: 骨格と素材だけ。見せ所は雷の光の芯一つ（表と裏の台に一つずつ、同じ見せ所）。
羽根は要から扇に 7 本。原点は握りの真ん中、+y が先。
"""
import math  # noqa: F401  (seg の角度計算は paint_cubes 側)

NAME = "storm_staff"
SERIES = "storm"

HUB = (0, 29)
FEATHER_L = 14


def ray(deg, r0, r1):
    t = math.radians(deg)
    return ((HUB[0] + r0 * math.cos(t), HUB[1] + r0 * math.sin(t)),
            (HUB[0] + r1 * math.cos(t), HUB[1] + r1 * math.sin(t)))


PARTS = [
    # 石突き（鉄紺の石突き・銅の口金）と握り
    ("pommel_a", "ironnavy", [-2.5, -30, -2.5], [2.5, -26, 2.5], "worn"),
    ("pommel_b", "ironnavy", [-3, -27, -2], [3, -23, 2], "worn"),
    ("cap", "copper", [-3.5, -23.5, -3.5], [3.5, -22, 3.5], "plain"),
    ("grip", "ironnavy", [-3, -22, -3], [3, 4, 3], "band"),
    ("ring_low", "stormring", [-3.5, -14, -3.5], [3.5, -13, 3.5], "plain"),
    ("ring_high", "stormring", [-3.5, 3, -3.5], [3.5, 4, 3.5], "plain"),
    # 柄（鉄紺）と、柄を登る銅の巻き 4 本
    ("shaft", "ironnavy", [-2, 4, -2], [2, 27.5, 2], "grain"),
    ("coil_1", "copper", [-2.8, 7, -2.8], [2.8, 8, 2.8], "plain"),
    ("coil_2", "copper", [-2.8, 11, -2.8], [2.8, 12, 2.8], "plain"),
    ("coil_3", "copper", [-2.8, 15, -2.8], [2.8, 16, 2.8], "plain"),
    ("coil_4", "copper", [-2.8, 19, -2.8], [2.8, 20, 2.8], "plain"),
    # 羽根の束の根元の銅の襟
    ("collar", "copper", [-3.5, 27.5, -3.5], [3.5, 29.5, 3.5], "plain"),
    # 羽根 7 本（要から上へ扇に開く。白い羽根、先は暗い方へ）
    *[seg(f"feather_{k}", "feather", *ray(30 + 20 * k, 0, FEATHER_L), 3, 1.5, "grad") for k in range(7)],
    # 見せ所: 要の銅の台に雷の光（表と裏）
    ("cup_f", "copper", [-2.5, 28, 1], [2.5, 32, 3.5], "plain"),
    ("bolt_f_glow", "bolt", [-1.5, 29, 3.5], [1.5, 32, 5.5], "gem"),
    ("cup_b", "copper", [-2.5, 28, -3.5], [2.5, 32, -1], "plain"),
    ("bolt_b_glow", "bolt", [-1.5, 29, -5.5], [1.5, 32, -3.5], "gem"),
]
