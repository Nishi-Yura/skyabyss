"""野の装備 帯 I (Lv 1〜10。はじまりの島・都のまわりの草原・岩鯨の丘陵) の武器 12 本 (docs/design/51 の 4.1)。一品一案。
都の衛士・石切り・写本師・鳥追い・鼠捕り・案山子 (草原の人の道具が武器になった品) と、昔の戦の兵・岩背の大猪・苔の亡者・骨棘の射手・岩殻虫・骨鳥 (丘陵の古いもの)。
"""
from __future__ import annotations

import math

from kit_wild import *  # noqa: F401,F403
from kit_wild import W, haft, bow_grip, bell, lash, flag, leaf_blade, claw_hand, _slab


def wild_guard_sword(stage=0):
    """衛士の払い剣: 都の鐘の衛士が使い古した片手の長剣。真っすぐな鍔の真ん中に小さな鐘の透かし、柄頭は鐘の形の青銅、柄は革に鋲。刃の根に研ぎ減りの段、先の近くに小さな刃こぼれ"""
    f = haft(-4.2, 2.6, r=0.78, wrap=(-3.6, 2.0))
    for y in (-2.6, -0.8, 1.0):
        f += ell("acc", (0, y, -0.9), (0.22, 0.22, 0.14), segs=4, rings=1)
    # 柄頭: 鐘の形
    f += bell((0, -4.0, 0), r=1.05, h=1.5, mat="acc")
    # 鍔: 真っすぐな鉄の棒、両端が少し下がる。真ん中に鐘の透かしの円盤
    f += curve("guard", [(-4.0, 2.5, 0), (-2.0, 3.0, 0), (0, 3.1, 0), (2.0, 3.0, 0), (4.0, 2.5, 0)], [0.5, 0.55, 0.6, 0.55, 0.5],
               [0.55, 0.6, 0.65, 0.6, 0.55], sides=6, caps=(True, True), hint=(0, 0, 1))
    for s in (-1, 1):
        f += ell("guard", (s * 4.1, 2.45, 0), (0.6, 0.6, 0.6), segs=6, rings=1)
    f += prism("guard", (0, 3.0, -0.75), (0, 3.0, 0.75), 1.5, 1.5, sides=10, caps=(True, True), hint=(1, 0, 0))
    f += bell((0, 3.85, -0.8), r=0.55, h=0.95, mat="acc")
    f += bell((0, 3.85, 0.8), r=0.55, h=0.95, mat="acc")
    # 刃: 根の研ぎ減りの段 (少しくびれ) と、先の近くの刃こぼれ
    st = [(3.6, -1.15, 1.15, 0.42, 0.42), (5.6, -1.3, 1.3, 0.42, 0.42), (6.4, -1.12, 1.12, 0.4, 0.4), (12.0, -1.22, 1.22, 0.4, 0.4),
          (16.5, -1.12, 1.12, 0.38, 0.38), (17.3, -0.85, 1.1, 0.36, 0.36), (17.9, -1.05, 1.06, 0.36, 0.36), (20.6, -0.6, 0.6, 0.28, 0.28),
          (22.4, 0, 0, 0, 0)]
    f += blade("main", st)
    f += gbar([(0, 5.0, -0.4), (0, 15.0, -0.38)], r=0.14, mat="spine")
    f += gbar([(0, 5.0, 0.4), (0, 15.0, 0.38)], r=0.14, mat="spine")
    return f


def wild_banner_spear(stage=0):
    """古戦の旗槍: 骸の旗手の槍。黒い長柄の先に欠けた広い穂、穂の下の横木から破れた軍旗が垂れて柄に巻き付く。石突きに錆びた鈴"""
    f = haft(-7.0, 19.0, r=0.7, mat="grip", wrap=(-2.6, 2.4))
    f += lash(-3.0, 0.95, 2)
    f += lash(2.6, 0.95, 2)
    # 石突きと錆びた鈴
    f += spike(BONE, "spine", (0, -6.8, 0), (0, -1, 0), 1.8, 0.78, sides=6, rings=1, hint=(0, 0, 1))
    f += prism("cord", (0.6, -5.6, 0), (1.3, -6.6, 0), 0.08, 0.08, sides=3, caps=(False, False))
    f += bell((1.4, -6.4, 0), r=0.6, h=1.0, mat="acc")
    # 穂の口金と横木
    f += prism("spine", (0, 18.6, 0), (0, 20.6, 0), 0.95, 0.75, sides=8, caps=(True, True))
    f += ringy("trim", 19.0, 1.05, 0.4)
    f += prism("spine", (-0.2, 16.2, 0), (5.4, 16.2, 0), 0.32, 0.3, sides=6, caps=(True, True), hint=(0, 1, 0))
    f += ell("spine", (5.5, 16.2, 0), (0.45, 0.45, 0.45), segs=6, rings=1)
    # 穂 (欠けた広刃: 左の縁に欠け)
    st = [(20.4, -0.8, 0.8, 0.45, 0.45), (22.0, -2.0, 2.0, 0.5, 0.5), (23.4, -2.3, 2.3, 0.5, 0.5), (24.2, -1.6, 2.2, 0.48, 0.48),
          (24.9, -1.9, 2.0, 0.46, 0.46), (27.2, -0.9, 0.9, 0.34, 0.34), (29.4, 0, 0, 0, 0)]
    f += blade("main", st)
    # 破れた軍旗 (横木から垂れて、柄の側は巻き付く)
    f += flag((0.6, 16.0), 4.8, 7.5, z=0.1, mat="cloth", tears=3, wave=0.45)
    f += prism("cloth", (0, 15.0, 0), (0, 16.4, 0), 0.95, 0.95, sides=8, caps=(False, False))
    f += ell("acc", (3.0, 12.6, -0.55), (0.75, 0.75, 0.15), segs=8, rings=1)
    return f


def wild_quarry_hammer(stage=0):
    """石切りの鎚: 都の石切り場の大鎚。樫の柄に縄の巻き。鉄の頭の片側は平らな打ち面、もう片側は鑿の楔。頭に石の粉の欠片が噛む"""
    f = haft(-4.4, 11.6, r=0.9, mat="grip", wrap=(-3.6, 1.4), wmat="cord")
    f += ell("spine", (0, -4.6, 0), (1.05, 0.7, 1.05), segs=8, rings=1)
    # 頭の芯 (柄を通す目)
    f += block("main", (0, 12.6, 0), (3.4, 3.6, 3.0))
    f += ringy("trim", 10.4, 1.15, 0.5)
    # 打ち面 (+x): 平らで少し広がる
    f += block("main", (3.3, 12.6, 0), (3.2, 3.0, 2.7), rot=rot_z(-90), taper=(1.12, 1.12))
    f += prism("edge", (4.9, 12.6, 0), (5.3, 12.6, 0), 1.75, 1.75, sides=8, caps=(True, True), hint=(0, 1, 0), phase=22.5)
    # 鑿の楔 (-x): 先が細い刃
    f += block("main", (-2.9, 12.6, 0), (2.4, 2.6, 2.6), rot=rot_z(90), taper=(0.35, 1.0))
    f += block("edge", (-4.5, 12.6, 0), (0.9, 0.7, 2.62), rot=rot_z(90), taper=(0.3, 1.0))
    # 石の粉の欠片
    f += lump("acc", (1.1, 14.3, -0.9), (0.7, 0.5, 0.5), seed=3)
    f += lump("acc", (-1.4, 14.2, 0.9), (0.55, 0.45, 0.5), seed=5)
    return f


def wild_tusk_axe(stage=0):
    """猪牙の斧: 岩背の大猪の反った牙を刃にした片手斧。牙の根を革と鉄の輪で柄の頭に締め、牙の内の弧が刃。柄尻に猪の毛の房"""
    f = haft(-4.0, 11.0, r=0.85, mat="grip", wrap=(-3.4, 1.0))
    # 柄尻の毛の房
    for a in range(5):
        ang = math.radians(-60 + 30 * a)
        f += strand("cloth", (0.3 * math.sin(ang), -4.0, 0.3 * math.cos(ang)), 2.4, sway=0.5, r=0.35, n=3)
    f += ringy("trim", -3.7, 1.0, 0.45)
    # 柄の頭の締め (革と鉄の輪)
    f += prism("wrap", (0, 9.0, 0), (0, 12.6, 0), 1.25, 1.15, sides=8, caps=(True, True))
    f += ringy("spine", 9.6, 1.35, 0.5)
    f += ringy("spine", 11.8, 1.3, 0.5)
    # 牙: 根は柄の頭、前 (+x) へ大きく反り、先は下へ。断面は平たい (前後に薄い)
    pts = [(0.4, 11.0, 0), (2.4, 12.6, 0), (4.6, 12.4, 0), (6.2, 10.6, 0), (6.6, 8.0, 0), (5.9, 5.8, 0)]
    f += curve("main", pts, [1.25, 1.35, 1.25, 1.05, 0.75, 0.15], [0.75, 0.7, 0.62, 0.5, 0.38, 0.1], sides=6, caps=(True, True), hint=(0, 0, 1))
    # 内の弧の刃 (研いだ縁)
    inner = [(2.6, 11.4, 0), (4.3, 11.1, 0), (5.4, 9.8, 0), (5.7, 7.8, 0)]
    f += curve("edge", inner, [0.35, 0.38, 0.35, 0.25], [0.5, 0.48, 0.42, 0.3], sides=4, caps=(True, True), hint=(0, 0, 1))
    # 背に小さな牙 (二本目の短い牙)
    f += curve("main", [(-0.3, 12.0, 0), (-1.8, 12.9, 0), (-2.7, 12.4, 0)], [0.8, 0.55, 0.12], [0.5, 0.38, 0.1], sides=5, caps=(True, True), hint=(0, 0, 1))
    return f


def wild_scribe_staff(stage=0):
    """写本師の羽杖: 都の写本師の羽根ペンを杖にしたもの。黒い柄の先に真鍮の籠のインク壺 (青く光る結晶)、そこから大きな白い羽根が反って立つ。柄に紙の帯が巻いて一筋垂れる"""
    f = haft(-4.0, 13.0, r=0.72, mat="grip", wrap=(-2.4, 2.0))
    f += spike(BONE, "acc", (0, -3.8, 0), (0, -1, 0), 1.6, 0.75, sides=8, rings=1, hint=(0, 0, 1))
    # インク壺 (真鍮の籠と光る結晶)
    f += prism("acc", (0, 12.6, 0), (0, 13.4, 0), 0.9, 1.4, sides=8, caps=(True, True))
    f += ell("gem", (0, 14.8, 0), (1.25, 1.4, 1.25), segs=8, rings=3)
    for k in range(4):
        a = math.radians(45 + 90 * k)
        f += curve("acc", [(1.35 * math.cos(a), 13.4, 1.35 * math.sin(a)), (1.6 * math.cos(a), 14.8, 1.6 * math.sin(a)),
                           (1.0 * math.cos(a), 16.2, 1.0 * math.sin(a))], [0.16, 0.18, 0.16], [0.16, 0.18, 0.16], sides=4, caps=(True, True))
    f += prism("acc", (0, 16.0, 0), (0, 16.6, 0), 1.1, 0.8, sides=8, caps=(True, True))
    # 羽根: 軸と、左右の羽の面 (根は細く、真ん中が広く、先は尖る。前後に薄い板。少し後ろへ反る)
    shaft = [(0, 16.4, 0), (0.3, 19.0, 0.2), (0.9, 22.0, 0.6), (1.8, 25.0, 1.2), (2.9, 27.4, 1.8)]
    f += curve("spine", shaft, [0.3, 0.26, 0.22, 0.16, 0.06], [0.3, 0.26, 0.22, 0.16, 0.06], sides=4, caps=(False, True), hint=(0, 0, 1))
    widths = [(0.5, 0.3), (1.5, 1.0), (1.9, 1.3), (1.4, 1.0), (0.0, 0.0)]
    for s, side in ((-1, 0), (1, 1)):
        for i in range(len(shaft) - 1):
            (x0, y0, z0), (x1, y1, z1) = shaft[i], shaft[i + 1]
            w0, w1 = widths[i][side], widths[i + 1][side]
            quad = [V(x0, y0, z0 - 0.1), V(x1, y1, z1 - 0.1), V(x1 + s * w1, y1 - 0.8, z1 - 0.1), V(x0 + s * w0, y0 - 0.8, z0 - 0.1)]
            if w1 == 0:
                quad = [V(x0, y0, z0 - 0.1), V(x1, y1, z1 - 0.1), V(x0 + s * w0, y0 - 0.8, z0 - 0.1)]
            f += _slab("cloth", quad, 0.2)
        # 羽の切れ込み (縁に小さな欠け)
        x, y, z = shaft[2]
        f += _slab("soot", [V(x + s * 1.6, y - 1.2, z - 0.12), V(x + s * 2.1, y - 1.9, z - 0.12), V(x + s * 1.3, y - 1.6, z - 0.12)], 0.22)
    # 紙の帯
    f += prism("soot", (0, 10.6, 0), (0, 11.8, 0), 0.82, 0.82, sides=8, caps=(False, False))
    f += sash([(0.6, 11.0, 0.6), (1.0, 9.0, 0.9), (0.8, 6.8, 1.2)], 0.9, mat="soot")
    return f


def wild_moss_staff(stage=0):
    """苔灯りの杖: 苔の亡者から剥いだ光る苔の玉を、ねじれた骨の枝が三本の指で抱く杖。苔の房が垂れ、柄は継いだ骨"""
    f = []
    # 継いだ骨の柄 (関節の膨らみ)
    f += bone("grip", [(0, -4.0, 0), (0.2, 3.0, 0)], 0.8)
    f += bone("grip", [(0.2, 3.0, 0), (-0.1, 10.0, 0)], 0.75)
    f += bone("grip", [(-0.1, 10.0, 0), (0.0, 13.6, 0)], 0.7)
    f += prism("wrap", (0, -2.2, 0), (0, 1.8, 0), 0.95, 0.95, sides=8, caps=(False, False))
    # 三本の指の枝が玉を抱く
    C = (0.0, 17.0, 0.0)
    for k in range(3):
        a = math.radians(90 + 120 * k)
        d = (math.cos(a), math.sin(a))
        pts = [(0, 13.4, 0), (d[0] * 1.6, 14.4, d[1] * 1.6), (d[0] * 2.5, 16.6, d[1] * 2.5), (d[0] * 1.8, 18.8, d[1] * 1.8), (d[0] * 0.7, 19.6, d[1] * 0.7)]
        f += curve("spine", pts, [0.6, 0.5, 0.42, 0.34, 0.12], [0.6, 0.5, 0.42, 0.34, 0.12], sides=5, caps=(False, True))
        f += ell("spine", pts[2], (0.48, 0.48, 0.48), segs=5, rings=1)
    # 光る苔の玉 (芯が光り、表に苔の塊)
    f += ell("gem", C, (1.7, 1.8, 1.7), segs=8, rings=3)
    for i, (dx, dy, dz) in enumerate(((1.3, 0.6, -0.6), (-1.0, 1.0, -0.9), (0.2, -1.2, -1.2), (-1.2, -0.4, 0.9), (0.9, 1.3, 0.8))):
        f += lump("acc", (C[0] + dx, C[1] + dy, C[2] + dz), (0.75, 0.6, 0.7), seed=i + 2)
    # 垂れる苔の房
    for x, z, ln in ((1.6, -0.6, 3.6), (-1.5, 0.5, 4.4), (0.4, 1.6, 3.0), (-0.6, -1.5, 2.6)):
        f += strand("acc", (x, 15.6, z), ln, sway=0.5, r=0.3, n=4)
    return f


def _bow_tip(half, up, mat="spine"):
    tx, ty, _ = up[-1]
    half += ell(mat, (tx, ty, 0), (0.5, 0.5, 0.5), segs=6, rings=1)
    return half


def wild_crow_bow(stage=0):
    """鳥追いの弓: 麦畑の鳥追いの短い木の弓。腕に麦の穂の束を縄で巻き付け、上の先に木の鳴子 (板と竹の管) が下がる"""
    f = bow_grip("grip", "cord")
    up = bend(stage, [(5.0, CY + 2.4, 0), (4.6, CY + 5.0, 0), (3.4, CY + 7.4, 0), (1.6, CY + 9.2, 0), (-0.4, CY + 10.0, 0)])
    half = []
    half += curve("main", up, [0.85, 0.8, 0.7, 0.55, 0.38], [1.0, 0.92, 0.8, 0.62, 0.42], sides=5, caps=(False, True), hint=(0, 0, 1))
    # 麦の穂の束 (腕の外に沿う藁と、先の穂)
    stalk = [(x + 0.85, y, 0.0) for x, y, z in up[:4]]
    half += curve("cloth", stalk, [0.35, 0.34, 0.3, 0.2], [0.35, 0.34, 0.3, 0.2], sides=4, caps=(True, True), hint=(0, 0, 1))
    sx, sy, _ = stalk[-1]
    for k, (dx, dy) in enumerate(((0.6, 1.1), (1.1, 0.4), (0.0, 1.3))):
        half += ell("cloth", (sx + dx, sy + dy, -0.15 + 0.15 * k), (0.32, 0.75, 0.32), segs=5, rings=2,
                    rot=rot_z(-30 + 25 * k))
    for i in (1, 2):
        x, y, z = up[i]
        half += curve("cord", [(x - 0.9, y, -0.9), (x + 1.3, y, -0.9), (x + 1.3, y, 0.9), (x - 0.9, y, 0.9)], 0.15, 0.15, sides=3, caps=(True, True))
    half = _bow_tip(half, up)
    f += half + mirror_faces_y(half)
    # 鳴子 (上の先から下がる。上側だけ)
    tx, ty, _ = up[-1]
    f += prism("cord", (tx - 0.2, ty - 0.3, 0), (tx - 0.8, ty - 2.4, 0), 0.08, 0.08, sides=3, caps=(False, False))
    f += block("main", (tx - 0.9, ty - 3.2, 0), (1.9, 1.2, 0.3))
    for dx in (-0.55, 0.0, 0.55):
        f += prism("acc", (tx - 0.9 + dx, ty - 3.8, 0), (tx - 0.9 + dx, ty - 5.0, 0), 0.24, 0.24, sides=6, caps=(True, True))
    lo = [(x, 2 * CY - y, z) for x, y, z in up]
    return f + string_and_arrow(stage, "rare", up, lo)


def wild_bonespike_bow(stage=0):
    """骨棘の弓: 骨棘の射手の弓。白い骨の腕の背 (外側) に棘が並び、関節ごとに骨の節。握りは鯨見の革、腕の根を腱の紐で締める"""
    f = bow_grip("wrap", "grip")
    up = bend(stage, [(5.0, CY + 2.4, 0), (4.7, CY + 5.4, 0), (3.5, CY + 8.2, 0), (1.4, CY + 10.4, 0), (-1.2, CY + 11.4, 0)])
    half = []
    half += curve("main", up, [0.95, 0.88, 0.78, 0.6, 0.4], [1.05, 0.96, 0.84, 0.64, 0.42], sides=6, caps=(False, True), hint=(0, 0, 1))
    for i in range(1, 4):
        half += ell("main", up[i], (1.05, 1.0, 1.1), segs=6, rings=1)
    # 背の棘 (外側 +x へ)
    for i in range(len(up) - 1):
        (x0, y0, _), (x1, y1, _) = up[i], up[i + 1]
        dx, dy = x1 - x0, y1 - y0
        ln = math.hypot(dx, dy) or 1
        nx, ny = dy / ln, -dx / ln
        for t in (0.3, 0.75):
            px, py = x0 + dx * t, y0 + dy * t
            half += spike(BONE, "spine", (px + nx * 0.5, py + ny * 0.5, 0), (nx + dx / ln * 0.5, ny + dy / ln * 0.5, 0), 1.6 - 0.25 * i, 0.38,
                          sides=4, rings=1, hint=(0, 0, 1))
    half += curve("cord", [(5.9, CY + 2.6, -1.0), (6.1, CY + 3.4, 0), (5.9, CY + 4.2, 1.0)], 0.13, 0.13, sides=3)
    half = _bow_tip(half, up, mat="spine")
    f += half + mirror_faces_y(half)
    lo = [(x, 2 * CY - y, z) for x, y, z in up]
    return f + string_and_arrow(stage, "rare", up, lo)


def wild_rat_knife(stage=0):
    """鼠捕りの小刀: 穀物倉の鼠捕りの細い小刀。鍔は罠のばねを巻いた輪、柄は麦藁の巻き。刃の背に小さな鉤"""
    f = haft(-3.0, 2.6, r=0.66, mat="grip", wrap=(-2.6, 2.1), wmat="cloth")
    f += ell("spine", (0, -3.2, 0), (0.75, 0.55, 0.75), segs=6, rings=1)
    # ばねの鍔 (巻いた輪を三つ)
    for i in range(3):
        f += torus("guard", (0, 2.8 + i * 0.32, 0), 1.25 - i * 0.12, 0.13, 0.13, n=10, k=4, ax="y")
    f += prism("guard", (0, 2.6, 0), (0, 3.7, 0), 0.65, 0.55, sides=6, caps=(True, True))
    # 細い刃と背の鉤
    st = [(3.6, -0.55, 0.62, 0.3, 0.3), (6.0, -0.6, 0.7, 0.3, 0.3), (9.6, -0.42, 0.58, 0.26, 0.26), (12.0, -0.2, 0.3, 0.18, 0.18), (13.2, 0, 0, 0, 0)]
    f += blade("main", st)
    f += curve("spine", [(-0.5, 7.4, 0), (-1.3, 7.9, 0), (-1.4, 8.8, 0), (-0.9, 9.0, 0)], [0.24, 0.22, 0.18, 0.08], [0.2, 0.18, 0.15, 0.06],
               sides=4, caps=(True, True), hint=(0, 0, 1))
    return f


def wild_shell_claw(stage=0):
    """岩殻の爪: 岩殻虫の殻を三枚の指の鉤にした爪。拳を包む甲は重ねた殻の板、殻の縁は研いで薄く、甲の上に苔"""
    f = claw_hand()
    f += ell("spine", (0, -1.3, 0), (0.95, 0.6, 0.95), segs=6, rings=1)
    # 甲: 重ねた殻の板 (三段)
    for i, (y, w) in enumerate(((4.4, 5.4), (5.4, 5.0), (6.3, 4.4))):
        f += block("guard", (0, y, 0.15 * i), (w, 1.2, 2.4 - 0.2 * i), taper=(0.92, 0.9))
    f += lump("acc", (0.8, 6.8, 0.9), (1.1, 0.4, 0.6), seed=4)
    # 三本の殻の鉤 (前へ反る。平たい板の形)
    for x, h, lean in ((-1.7, 8.6, -0.7), (0.0, 10.2, 0.0), (1.7, 8.6, 0.7)):
        pts = [(x, 6.6, 0), (x + lean * 0.5, 6.6 + h * 0.4, -0.3), (x + lean, 6.6 + h * 0.75, -1.2), (x + lean * 0.8, 6.6 + h, -2.4)]
        f += curve("main", pts, [0.9, 0.82, 0.55, 0.1], [0.42, 0.38, 0.3, 0.08], sides=6, caps=(False, True), hint=(1, 0, 0))
        f += curve("edge", [(x + lean * 0.3, 6.6 + h * 0.3, -0.42), (x + lean * 0.8, 6.6 + h * 0.7, -1.4), (x + lean * 0.8, 6.6 + h * 0.95, -2.3)],
                   [0.18, 0.15, 0.06], [0.28, 0.22, 0.08], sides=4, caps=(True, True), hint=(1, 0, 0))
    return f


def wild_scarecrow_bell_staff(stage=0):
    """案山子の鳴子杖: 竹の杖の先に、ボタンの目の麦藁の頭と、つば広の麦藁帽子。横木の腕から布が垂れ、竹の鳴子が紐で下がる"""
    f = haft(-4.0, 16.0, r=0.72, mat="grip", wrap=(-2.4, 2.0), wmat="cord")
    for y in (5.0, 9.4):
        f += ringy("spine", y, 0.82, 0.35)
    # 横木の腕
    f += prism("grip", (-4.2, 13.2, 0), (4.2, 13.2, 0), 0.45, 0.45, sides=6, caps=(True, True), hint=(0, 1, 0))
    for s in (-1, 1):
        f += rag((s * 2.6, 13.0, 0.0), 3.8, 1.5, sign=s, mat="cloth")
        # 鳴子
        f += prism("cord", (s * 3.8, 13.0, 0), (s * 3.8, 11.2, 0), 0.07, 0.07, sides=3, caps=(False, False))
        f += block("main", (s * 3.8, 10.8, 0), (1.5, 0.8, 0.3))
        for dx in (-0.4, 0.4):
            f += prism("acc", (s * 3.8 + dx, 10.4, 0), (s * 3.8 + dx, 9.2, 0), 0.22, 0.22, sides=6, caps=(True, True))
    # 麦藁の頭とボタンの目
    f += ell("cloth", (0, 17.6, 0), (2.0, 2.2, 1.9), segs=8, rings=3)
    f += torus("cord", (0, 16.1, 0), 1.2, 0.28, 0.28, n=8, k=4, ax="y")
    for s in (-1, 1):
        f += prism("dark", (s * 0.75, 18.0, -1.75), (s * 0.75, 18.0, -1.95), 0.38, 0.38, sides=6, caps=(True, True), hint=(1, 0, 0))
    f += curve("dark", [(-0.8, 16.9, -1.85), (0, 16.6, -1.95), (0.8, 16.9, -1.85)], 0.09, 0.09, sides=3)
    # つば広の帽子
    f += prism("soot", (0, 19.2, 0), (0, 19.6, 0), 3.4, 3.4, sides=12, caps=(True, True))
    f += prism("soot", (0, 19.6, 0), (0, 21.4, 0), 1.7, 1.4, sides=10, caps=(True, True))
    f += ringy("cloth", 20.0, 1.65, 0.45, sides=10)
    return f


def wild_bonebird_totem(stage=0):
    """骨鳥の祈り柱: 流木の柱の頂に、骨鳥の頭骨を三つ積んだ小さな祈りの柱。翼の骨を左右に広げ、骨のあいだに羽根と鈴を結ぶ"""
    f = haft(-4.0, 13.2, r=0.85, mat="grip", wrap=(-2.4, 2.0), wmat="cord")
    f += spike(BONE, "grip", (0, -3.8, 0), (0, -1, 0), 1.4, 0.8, sides=6, rings=1, hint=(0, 0, 1))
    # 三つの頭骨 (下ほど大きい。嘴は前 -Z へ)
    for i, (y, r) in enumerate(((14.4, 1.55), (16.9, 1.35), (19.1, 1.15))):
        f += ell("main", (0, y, 0), (r, r * 0.9, r * 1.05), segs=8, rings=3)
        f += spike(BONE, "spine", (0, y - 0.1, -r * 0.8), (0, -0.25, -1), r * 1.7, r * 0.42, sides=4, rings=2, hint=(1, 0, 0))
        for s in (-1, 1):
            f += ell("dark", (s * r * 0.48, y + 0.25, -r * 0.82), (r * 0.26, r * 0.28, r * 0.12), segs=6, rings=1)
    # 翼の骨 (左右に広げる) と羽根
    for s in (-1, 1):
        wing = [(s * 1.2, 15.0, 0.3), (s * 3.4, 16.8, 0.4), (s * 5.6, 16.2, 0.4), (s * 7.0, 14.2, 0.4)]
        f += bone("spine", wing[:2], 0.38)
        f += bone("spine", wing[1:3], 0.32)
        f += curve("spine", wing[2:], [0.3, 0.1], [0.3, 0.1], sides=5, caps=(True, True))
        for k, (wx, wy, _) in enumerate(wing[1:]):
            f += feather("cloth", (wx, wy - 0.2, 0.55), (wx + s * 0.4, wy - 3.2 + 0.4 * k, 0.6), 0.7, th=0.2, big=True)
        f += prism("cord", (s * 4.6, 16.2, 0.4), (s * 4.6, 14.6, 0.4), 0.07, 0.07, sides=3, caps=(False, False))
        f += bell((s * 4.6, 14.6, 0.4), r=0.5, h=0.85, mat="acc")
    return f


WEAPONS = dict([
    W(wild_guard_sword, "衛士の払い剣", "uncommon", "sword", "lamp",
      dict(main="steel", edge="steel", spine="iron", guard="iron", acc="bronze", grip="leather", wrap="leather")),
    W(wild_banner_spear, "古戦の旗槍", "rare", "spear", "lamp",
      dict(main="rust", edge="iron", spine="iron", grip="darkwood", wrap="leather", cord="rope", acc="bronze", cloth="redcloth")),
    W(wild_quarry_hammer, "石切りの鎚", "uncommon", "hammer", "lamp",
      dict(main="iron", edge="steel", spine="iron", grip="oak", cord="rope", acc="stone")),
    W(wild_tusk_axe, "猪牙の斧", "rare", "axe", "ember",
      dict(main="tusk", edge="bone", spine="iron", grip="darkwood", wrap="leather", cloth="brownfur")),
    W(wild_scribe_staff, "写本師の羽杖", "uncommon", "staff", "ink",
      dict(grip="darkwood", wrap="leather", acc="brass", spine="bone", cloth="whitecloth", soot="paper")),
    W(wild_moss_staff, "苔灯りの杖", "rare", "staff", "moss",
      dict(grip="bone", wrap="leather", spine="bone", acc="moss")),
    W(wild_crow_bow, "鳥追いの弓", "uncommon", "bow", "lamp",
      dict(main="oak", grip="oak", cord="rope", cloth="straw", spine="oak", acc="bamboo")),
    W(wild_bonespike_bow, "骨棘の弓", "rare", "bow", "moss",
      dict(main="bone", spine="bone", grip="leather", wrap="leather", cord="rope")),
    W(wild_rat_knife, "鼠捕りの小刀", "uncommon", "dagger", "lamp",
      dict(main="steel", edge="steel", spine="iron", guard="iron", grip="oak", cloth="straw")),
    W(wild_shell_claw, "岩殻の爪", "rare", "claw", "moss",
      dict(main="rockshell", edge="stone", guard="rockshell", spine="iron", grip="leather", wrap="leather", acc="moss")),
    W(wild_scarecrow_bell_staff, "案山子の鳴子杖", "uncommon", "bell", "lamp",
      dict(grip="bamboo", spine="rope", cord="rope", main="oak", acc="bamboo", cloth="straw", soot="straw", dark="dark")),
    W(wild_bonebird_totem, "骨鳥の祈り柱", "rare", "totem", "lamp",
      dict(main="bone", spine="bone", grip="driftwood", cord="rope", cloth="whitecloth", acc="bronze")),
])
