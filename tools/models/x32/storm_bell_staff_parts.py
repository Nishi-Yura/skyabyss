# -*- coding: utf-8 -*-
"""雷の鈴杖（x32・レア）。前の形: 鉄紺の柄の先に鉄紺の輪、輪の中に銅の鈴、鈴の上に白い羽根 2 枚、
握りに銅の帯、柄の上の輪と鈴が一つの飾り。
鈴は銅の鈴: 大小の箱を十字に重ねた釣り鐘形（段を積まない）。輪の中に、輪に触れず吊る。
レア（60 の 1.6）: 骨格と素材だけ。見せ所は鈴の舌の雷の光一つ。
原点は握りの真ん中、+y が先。輪・鈴・羽根は +x 側。
"""
import math

NAME = "storm_bell_staff"
SERIES = "storm"


def bell(tag, mat, cx, y0, y1, hw, hd, lip=1.5):
    """大小の箱を十字に重ねた鈴（y0 が口、y1 が肩）。口の縁は少し広がる"""
    return [
        (tag + "_a", mat, [cx - hw, y0 + lip, -hd * 0.5], [cx + hw, y1, hd * 0.5], "grad"),
        (tag + "_b", mat, [cx - hw * 0.5, y0 + lip, -hd], [cx + hw * 0.5, y1, hd], "grad"),
        (tag + "_lip_a", mat, [cx - hw - 1.5, y0, -hd * 0.5 - 1], [cx + hw + 1.5, y0 + lip, hd * 0.5 + 1], "plain"),
        (tag + "_lip_b", mat, [cx - hw * 0.5 - 1, y0, -hd - 1], [cx + hw * 0.5 + 1, y0 + lip, hd + 1], "plain"),
    ]


RING_C = (8, 44)   # 輪の中心（+x 側）。左の端が柄の上に触れる
RING_R = 8


def ring():
    """輪（鉄紺）: 8 本の短い箱を円に沿ってつなぐ"""
    out = []
    cx, cy = RING_C
    pts = [(cx + RING_R * math.cos(math.radians(a)), cy + RING_R * math.sin(math.radians(a))) for a in range(0, 361, 45)]
    for k in range(8):
        out.append(seg("ring_%d" % k, "ironnavy", pts[k], pts[k + 1], 2.5, 2.5, "plain"))
    return out


PARTS = [
    # 柄頭（鉄紺の丸い節）と握り（鉄紺・銅の帯 3 本）
    ("butt_a", "ironnavy", [-3, -17, -2], [3, -13, 2], "worn"),
    ("butt_b", "ironnavy", [-2, -18, -3], [2, -12, 3], "worn"),
    ("shaft_low", "ironnavy", [-2, -13, -2], [2, -7, 2], "grain"),
    ("grip", "ironnavy", [-3, -8, -3], [3, 6, 3], "band"),
    ("band_low", "copper", [-3.5, -7, -3.5], [3.5, -5.5, 3.5], "plain"),
    ("band_mid", "copper", [-3.5, 0, -3.5], [3.5, 1.5, 3.5], "plain"),
    ("band_high", "copper", [-3.5, 5, -3.5], [3.5, 6.5, 3.5], "plain"),
    # 柄（鉄紺）。上端は輪の左の端まで
    ("shaft", "ironnavy", [-2, 6, -2], [2, 44, 2], "grain"),
    # 輪（鉄紺）と、輪から柄へ下りる腕
    *ring(),
    # 鈴（銅）: 輪の中に吊る
    *bell("bell", "copper", 8, 38, 48, 3, 3, 1.5),
    # 見せ所: 鈴の舌の雷の光（鈴の口の下）
    ("clapper_glow", "bolt", [7, 35.5, -1], [9, 37.5, 1], "gem"),
    # 白い羽根 2 枚（輪の上から立つ。軸は銅）
    ("feather_stem", "copper", [7, 51, -0.5], [9, 53, 0.5], "band"),
    seg("feather_l", "feather", (8, 52), (4, 59), 2.5, 1.5, "grad"),
    seg("feather_r", "feather", (8, 52), (12, 59), 2.5, 1.5, "grad"),
]
