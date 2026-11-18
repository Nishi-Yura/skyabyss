# -*- coding: utf-8 -*-
"""鯨の星杖（x32・伝説）。レア（whale_staff）と同じ柄・石突き・苔の巻き・骨の輪・台、頭だけ違う:
上へ開いた骨の三日月（レアの閉じた輪より大きく、底だけを包む）、三日月の先から張る二本の弦、
その上に浮く空色の結晶（光る芯）。浮くのは魔法使いの決まり（2.3）。
伝説（60 の 1.6）: 骨格を一回り大きく、奇抜な輪郭（三日月）を一つ。見せ所の結晶は一つ。
金は使わない（鯨のシリーズ）。原点は握りの真ん中、+y が先。
"""
import math

NAME = "whale_star_staff"
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


CRES_C = (0, 39)     # 三日月の中心（底は台の中に沈む。先は上へ開く）
CRES_R = 11
TIP_L = on_arc(CRES_C[0], CRES_C[1], CRES_R, 200)    # 三日月の左の先（上へ開く）
TIP_R = on_arc(CRES_C[0], CRES_C[1], CRES_R, 340)    # 三日月の右の先

PARTS = [
    # 石突き・握り・骨の輪・柄・節・台・苔の筋（レアと同じ）
    ("spike", "slate", [-1.5, -30, -1.5], [1.5, -24, 1.5], "plain"),
    ("cap", "slate", [-2.5, -24, -2.5], [2.5, -20, 2.5], "worn"),
    ("grip", "moss", [-3, -20, -3], [3, 4, 3], "band"),
    ("ring_1", "bone", [-3.5, -15, -3.5], [3.5, -14, 3.5], "plain"),
    ("ring_2", "bone", [-3.5, -8, -3.5], [3.5, -7, 3.5], "plain"),
    ("ring_3", "bone", [-3.5, -1, -3.5], [3.5, 0, 3.5], "plain"),
    ("shaft", "bone", [-2, 4, -2], [2, 26, 2], "grain"),
    ("knuckle", "bone", [-2.5, 12, -2.5], [2.5, 14, 2.5], "plain"),
    ("cup", "slate", [-5, 26, -4], [5, 29, 4], "worn"),
    ("moss_l", "moss", [-6, 22, -4], [-4, 26, -2], "grad"),
    ("moss_r", "moss", [4, 22, -4], [6, 26, -2], "grad"),
    # 上へ開いた三日月（底だけを包む 140 度を 20 度ずつ 7 本。太さ 3.5 で、レアの輪より一回り大きい）
    *arc("cres", "bone", CRES_C[0], CRES_C[1], CRES_R, 200, 340, 7, 3.5, 3.5),
    # 三日月の先から上へ張る二本の弦（光らない。結晶を吊る）
    seg("string_l", "string", TIP_L, (-2, 44.5), 1, 1, "band"),
    seg("string_r", "string", TIP_R, (2, 44.5), 1, 1, "band"),
    # 見せ所: 三日月の上に浮く空色の結晶（光る芯。弦が下の角で結ぶ）
    ("crystal_glow", "sky", [-2, 44.5, -1.5], [2, 50, 1.5], "gem"),
]
