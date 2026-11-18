# -*- coding: utf-8 -*-
"""嵐の雲の杖（x32・伝説）。レア（storm_staff）と同じ柄・石突き・銅の巻き・留め具の輪。頭だけ違う:
鉄紺の輪（閉じた丸い輪）に、外へ放つ白い棘の羽根（長い 8 本と短い 8 本）、輪の中心に雷の光の芯。
前の形の言葉: 棘の輪の頭（銅の輪の周りに棘、中心の目）。
伝説（60 の 1.6）: 骨格を一回り大きく。棘を長短に分けて奇抜な輪郭を一つ。光は中心の芯一つ（表と裏で同じ芯）。
原点は握りの真ん中、+y が先。
"""
import math

NAME = "storm_cloud_staff"
SERIES = "storm"

RING_C = (0, 38)
RING_R = 8.5


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
    return ((RING_C[0] + r0 * math.cos(t), RING_C[1] + r0 * math.sin(t)),
            (RING_C[0] + r1 * math.cos(t), RING_C[1] + r1 * math.sin(t)))


PARTS = [
    # 石突き・握り・柄（レアと同じ）
    ("pommel_a", "ironnavy", [-2.5, -30, -2.5], [2.5, -26, 2.5], "worn"),
    ("pommel_b", "ironnavy", [-3, -27, -2], [3, -23, 2], "worn"),
    ("cap", "copper", [-3.5, -23.5, -3.5], [3.5, -22, 3.5], "plain"),
    ("grip", "ironnavy", [-3, -22, -3], [3, 4, 3], "band"),
    ("ring_low", "stormring", [-3.5, -14, -3.5], [3.5, -13, 3.5], "plain"),
    ("ring_high", "stormring", [-3.5, 3, -3.5], [3.5, 4, 3.5], "plain"),
    ("shaft", "ironnavy", [-2, 4, -2], [2, 30, 2], "grain"),
    ("coil_1", "copper", [-2.8, 7, -2.8], [2.8, 8, 2.8], "plain"),
    ("coil_2", "copper", [-2.8, 11, -2.8], [2.8, 12, 2.8], "plain"),
    ("coil_3", "copper", [-2.8, 15, -2.8], [2.8, 16, 2.8], "plain"),
    ("coil_4", "copper", [-2.8, 19, -2.8], [2.8, 20, 2.8], "plain"),
    ("neck", "copper", [-3, 27.5, -3], [3, 30, 3], "plain"),
    # 鉄紺の輪（閉じた丸い輪。10 本の箱）
    *arc("ring", "ironnavy", RING_C[0], RING_C[1], RING_R, 0, 360, 10, 3, 3),
    # 銅の帯（輪の下半分を巻く、一本）
    *arc("band", "copper", RING_C[0], RING_C[1], RING_R + 0.5, 200, 340, 3, 1.5, 3.5, "plain"),
    # 長い棘 8 本（輪の外へ放つ白い羽根。45 度ずつ）
    *[seg(f"spike_l_{k}", "feather", *ray(45 * k, 6, 15), 2.5, 2.5, "grad") for k in range(8)],
    # 短い棘 4 本（長い棘の間、22.5 度ずらす）
    *[seg(f"spike_s_{k}", "feather", *ray(22.5 + 90 * k, 8, 12), 2, 2, "grad") for k in range(4)],
    # 見せ所: 輪の中心の雷の光（表と裏。芯の一つ）
    ("core_glow", "bolt", [-2.5, 35.5, -2.5], [2.5, 40.5, 2.5], "gem"),
]
