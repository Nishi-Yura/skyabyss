# -*- coding: utf-8 -*-
"""火の戦斧（x32・レア）。前の形: 黒い溶岩石の半月の大きな刃（縁は灰色の鉄）、刃に熾火の割れ目が弧に沿って走る、
反対側に下へ曲がる鉄の鉤、頭の上に鉄の尖り、煤けた木の柄に鉄の輪、灰色の鉄を巻いた握り。火のシリーズの基準の品。
レア（60 の 1.6）: 見せ所は熾火の割れ目の一つ。原点は握りの真ん中、刃は +x。
"""
import math

NAME = "ember_war_axe"
SERIES = "ember"

CY = 35          # 半月の中心の高さ
R = 17           # 半月の半径（前の大きな刃に合わせる）


def half_moon(name, mat, r, x0, t, kind):
    """x0 を弦にして +x へ張り出す半月（厚み 2t）。角を落とすため、弦の上下は少し内へ"""
    pts = []
    for k in range(9):
        a = math.radians(-80 + 160 * k / 8)
        pts.append((x0 + r * math.cos(a) * 0.95, CY + r * math.sin(a)))
    V = [(x, y, -t) for x, y in pts] + [(x, y, t) for x, y in pts]
    n = len(pts)
    F = [list(range(n))[::-1], list(range(n, 2 * n))]
    F += [[k, k + 1, n + k + 1, n + k] for k in range(n - 1)]
    F += [[n - 1, 0, n, 2 * n - 1]]
    return ("mesh", name, mat, V, F, kind)


def zseg(name, mat, p0, p1, w, d, kind, z):
    n, m, frm, to, kd, rot, org = seg(name, mat, p0, p1, w, d, kind)
    return (n, m, [frm[0], frm[1], frm[2] + z], [to[0], to[1], to[2] + z], kd, rot, [org[0], org[1], org[2] + z])


PARTS = [
    ("pommel", "ashiron", [-3, -14, -3], [3, -11, 3], "worn"),
    ("grip", "ashiron", [-2.5, -11, -2.5], [2.5, 5, 2.5], "band"),
    ("ring_1", "soot", [-3, 5, -3], [3, 6.5, 3], "plain"),
    ("shaft", "soot", [-2, 6.5, -2], [2, 28, 2], "grain"),
    ("ring_2", "ashiron", [-3, 15, -3], [3, 16.5, 3], "plain"),
    # 頭の台（鉄の筒）と上の尖り
    ("socket_a", "ashiron", [-3, 28, -3], [3, 44, 3], "worn"),
    ("socket_b", "ashiron", [-3.5, 30, -2.5], [3.5, 42, 2.5], "worn"),
    seg("spike_1", "ashiron", (0, 44), (0, 48), 3.5, 3.5, "grad"),
    seg("spike_2", "ashiron", (0, 48), (0, 51), 2, 2, "plain"),
    # 半月の刃（+x）: 黒い溶岩石、縁は灰色の鉄の帯
    half_moon("blade", "obsidian", R, 2.5, 0.9, "blade"),
    half_moon("rim", "ashiron", R + 1.5, 2.5, 0.6, "blade"),
    # 反対側（-x）の鉄の鉤（横へ出て下へ曲がる）
    seg("hook_1", "ashiron", (-3, 40), (-8, 41), 2.5, 2, "grad"),
    seg("hook_2", "ashiron", (-8, 41), (-10.5, 37.5), 2, 1.8, "grad"),
    seg("hook_3", "ashiron", (-10.5, 37.5), (-11, 34.5), 1.5, 1.5, "plain"),
]

# 熾火の割れ目（刃の表と裏、弧に沿って 4 つの短い筋）
for side, z in (("f", -1.05), ("b", 1.05)):
    pts = [(2.5 + 12.5 * math.cos(math.radians(a)) * 0.95, CY + 12.5 * math.sin(math.radians(a))) for a in (-55, -20, 15, 50)]
    for k in range(3):
        PARTS.append(zseg(f"crack_{side}{k}_glow", "ember", pts[k], pts[k + 1], 1, 0.3, "gem", z))
