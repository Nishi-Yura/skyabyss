# -*- coding: utf-8 -*-
"""鯨の杖（x32・レア）。前の形の言葉: 骨の輪に囲まれた空色の結晶の頭（閉じた輪）、骨の柄、苔の巻きに骨の輪、尖った暗い石の石突き、
柄の途中の骨の節、台から垂れる苔の筋。金は使わない（鯨のシリーズ）。
レア（60 の 1.6）: 骨格と素材だけ。見せ所は台の上の空色の結晶一つ（光るのは芯の一つ）。
伝説（whale_star_staff）とは頭の形で分ける: こちらは閉じた丸い輪、伝説は上へ開いた三日月と浮く結晶。
原点は握りの真ん中、+y が先。
"""
import math

NAME = "whale_staff"
SERIES = "whale"


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


def on_arc(cx, cy, R, deg):
    t = math.radians(deg)
    return (cx + R * math.cos(t), cy + R * math.sin(t))


RING_C = (0, 40)     # 頭の輪の中心
RING_R = 8.5         # 輪の中心線の半径（輪の外は +1.5、先まで 50）
TIP_L = on_arc(RING_C[0], RING_C[1], RING_R, 200)
TIP_R = on_arc(RING_C[0], RING_C[1], RING_R, -20)

PARTS = [
    # 石突き（尖った暗い石・段のある台）
    ("spike", "slate", [-1.5, -30, -1.5], [1.5, -24, 1.5], "plain"),
    ("cap", "slate", [-2.5, -24, -2.5], [2.5, -20, 2.5], "worn"),
    # 握り（苔の巻き）と骨の輪 3 本
    ("grip", "moss", [-3, -20, -3], [3, 4, 3], "band"),
    ("ring_1", "bone", [-3.5, -15, -3.5], [3.5, -14, 3.5], "plain"),
    ("ring_2", "bone", [-3.5, -8, -3.5], [3.5, -7, 3.5], "plain"),
    ("ring_3", "bone", [-3.5, -1, -3.5], [3.5, 0, 3.5], "plain"),
    # 骨の柄と、柄の途中の節
    ("shaft", "bone", [-2, 4, -2], [2, 26, 2], "grain"),
    ("knuckle", "bone", [-2.5, 12, -2.5], [2.5, 14, 2.5], "plain"),
    # 台（暗い石の受け皿）と、結晶を載せる細い柱
    ("cup", "slate", [-5, 26, -4], [5, 29, 4], "worn"),
    ("neck", "slate", [-2.5, 29, -2.5], [2.5, 34, 2.5], "worn"),
    # 苔の筋（台の角から短く垂れる。台に沿う短い筋だけ）
    ("moss_l", "moss", [-6, 22, -4], [-4, 26, -2], "grad"),
    ("moss_r", "moss", [4, 22, -4], [6, 26, -2], "grad"),
    # 骨の輪（結晶を囲む、底だけ開いた輪。22 度ずつ 10 本）
    *arc("ring", "bone", RING_C[0], RING_C[1], RING_R, -20, 200, 10, 3, 3),
    # 輪の両端から台へ下りる骨（台に食い込ませる）
    seg("tail_l", "bone", TIP_L, (-4.5, 29.5), 2.5, 2.5),
    seg("tail_r", "bone", TIP_R, (4.5, 29.5), 2.5, 2.5),
    # 見せ所: 台の上の空色の結晶（芯が光る。大きさは祠の星石の目安の内）
    ("crystal_glow", "sky", [-2, 34, -1.5], [2, 44, 1.5], "gem"),
]
