# -*- coding: utf-8 -*-
"""潮の杖（x32・レア）。前の形の言葉: 扇の形の貝の頭（放射の筋の貝殻）、暗い海の石の柄、昆布の握り、頭の中心に真珠。
潮のシリーズの品。基準は tide_hammer（珊瑚の柄頭・昆布の巻きの握り・淡い青の留め具・貝の台の真珠）。
レア（60 の 1.6）: 骨格と素材だけ。見せ所は真珠の一つ（表と裏の台に一つずつ、同じ見せ所）。
扇は放射の貝の筋（肋）を 11 本と、外の縁の弧で作る。原点は握りの真ん中、+y が先。
"""
import math

NAME = "tide_staff"
SERIES = "tide"

HUB = (0, 31.5)      # 扇の要（collar の上）
FAN_R = 10


def arc(prefix, mat, cx, cy, R, a0, a1, n, w, d, kind="grain"):
    """中心 (cx, cy)・半径 R の弧を、n 本の短い箱（seg）でつなぐ。角度は度（a0 から a1 へ）"""
    out = []
    for k in range(n):
        t0 = math.radians(a0 + (a1 - a0) * k / n)
        t1 = math.radians(a0 + (a1 - a0) * (k + 1) / n)
        p0 = (cx + R * math.cos(t0), cy + R * math.sin(t0))
        p1 = (cx + R * math.cos(t1), cy + R * math.sin(t1))
        out.append(seg(f"{prefix}_{k}", mat, p0, p1, w, d, kind))
    return out


def ray(deg, r0, r1):
    t = math.radians(deg)
    return ((HUB[0] + r0 * math.cos(t), HUB[1] + r0 * math.sin(t)),
            (HUB[0] + r1 * math.cos(t), HUB[1] + r1 * math.sin(t)))


PARTS = [
    # 石突き（珊瑚の柄頭。十字に 2 箱）
    ("pommel_a", "coral", [-2.5, -30, -2.5], [2.5, -26, 2.5], "worn"),
    ("pommel_b", "coral", [-3, -27, -2], [3, -23, 2], "worn"),
    # 握り（昆布の巻き）と淡い青の留め具の輪
    ("grip", "kelp", [-3, -23, -3], [3, 4, 3], "band"),
    ("ring_low", "tidering", [-3.5, -22, -3.5], [3.5, -21, 3.5], "plain"),
    ("ring_high", "tidering", [-3.5, 3, -3.5], [3.5, 4, 3.5], "plain"),
    # 海の石の柄
    ("shaft", "seastone", [-2, 4, -2], [2, 30, 2], "grain"),
    ("collar", "tidering", [-4, 29, -4], [4, 31.5, 4], "plain"),
    # 扇の貝の頭: 放射の肋 11 本（20〜160 度を 14 度ずつ）と、外の縁の弧
    *[seg(f"rib_{k}", "shell", *ray(20 + 14 * k, 0, FAN_R - 0.5), 2.8, 2.5, "grain") for k in range(11)],
    *arc("rim", "shell", HUB[0], HUB[1], FAN_R, 20, 160, 8, 2.5, 2.5, "plain"),
    # 見せ所: 扇の要の貝の台に真珠（表と裏。台は肋の前後に張る）
    ("cup_f", "shell", [-2.5, 30.5, 1], [2.5, 35, 3.5], "plain"),
    ("pearl_f_glow", "pearl", [-1.5, 31.5, 3.5], [1.5, 34.5, 5.5], "gem"),
    ("cup_b", "shell", [-2.5, 30.5, -3.5], [2.5, 35, -1], "plain"),
    ("pearl_b_glow", "pearl", [-1.5, 31.5, -5.5], [1.5, 34.5, -3.5], "gem"),
]
