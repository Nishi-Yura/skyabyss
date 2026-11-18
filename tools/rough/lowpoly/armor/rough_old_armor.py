"""古い防具 7 組 (旅装 4 組・初めの旅装・鉄張りの旅装・空石の胸当て) の作り直しの案 A/B/C (2026-11-12 の全モデルの確かめで、
箱を重ねただけの形 (箱の面の割合 0.8〜0.9。新しい防具は 0.3〜0.5) と分かった。声「古い防具」)。
見本は二組 (鯨道の旅装 = 布と骨の巡礼、鉄張りの旅装 = 鎖と板)。絵だけ (ゲームには入れない)。

    python -X utf8 -I tools/rough/lowpoly/armor/rough_old_armor.py

  A 仕立て     丸い輪を重ねた体に沿う形 (shell)。裾は波打ち、布と革を重ね着する。縫い目・帯・留め具。輪郭は肩と裾と頭で箱から外す
  B 重ね板     文書 16 の基盤 (重ね板の枠)。8〜16 度傾けた板を重ね、肩は外が上がり、裾は外へ開く。一点だけ左右非対称
  C 大きな輪郭 頭巾・つば・肩・裾を大きく誇張し、遠くからでも形で読める。細部は少なめ
絵は docs/review/armor_old/rough_<a|b|c>.png
"""
from __future__ import annotations

import math
import sys

from PIL import Image, ImageDraw

import build_one
import build_outfit  # noqa: F401  (鉄張りの旅装の塗りの表を tpaint へ入れる)
import tpaint
import travel_one
from akit import V, bx, crv, spk, rr, shell, hide_front, rot_x, rot_y, rot_z, ARM, LEG, BOOT, ROOT

H_, B_ = "armorHead", "armorBody"
OUTDIR = ROOT / "docs/review/armor_old"


def empty():
    return {"helmet": [], "chestplate": [], "leggings": [], "boots": []}


def ring_at(y, hx, zf, zb, cut, cx=0.0, wave=0.0, k=0):
    """丸めた輪 (裾を波打たせる: wave > 0 で頂点ごとに上下)"""
    dy = None
    if wave:
        dy = [wave * math.sin(i * 1.7 + k) for i in range(8)]
    return rr(y, hx, zf, zb, cut=cut, cx=cx, dy=dy)


def tube_body(bone, mat, ys, hx, zf, zb, cut=1.4, cx=0.0, wave_last=0.0, caps=(False, False)):
    """y の段ごとの輪 (hx/zf/zb は段ごとの列でもよい) を重ねた筒"""
    n = len(ys)
    L = lambda v, i: v[i] if isinstance(v, (list, tuple)) else v  # noqa: E731
    rings = [ring_at(ys[i], L(hx, i), L(zf, i), L(zb, i), cut, cx=cx, wave=wave_last if i == n - 1 else 0.0, k=i) for i in range(n)]
    return shell(bone, mat, rings, caps=caps)


def sleeve_r(s, mat, top, bot, r0, r1, wave=0.0):
    return tube_body(ARM[s], mat, [top, (top + bot) / 2, bot], [r0, (r0 + r1) / 2, r1], [-r0, -(r0 + r1) / 2, -r1], [r0, (r0 + r1) / 2, r1],
                     cut=r0 * 0.4, cx=s * 6.0, wave_last=wave)


def leg_r(s, mat, top, bot, r0, r1, wave=0.0, zoff=0.0):
    return tube_body(LEG[s], mat, [top, (top + bot) / 2, bot], [r0, (r0 + r1) / 2, r1], [-r0 + zoff, -(r0 + r1) / 2 + zoff, -r1 + zoff],
                     [r0 + zoff, (r0 + r1) / 2 + zoff, r1 + zoff], cut=r0 * 0.4, cx=s * 2.15, wave_last=wave)


def boot_r(s, mat, top, toe=3.4, r=2.75):
    cx = s * 2.15
    rings = [rr(top, r, -r, r, cut=1.0, cx=cx), rr(2.2, r * 0.98, -r - 0.6, r, cut=1.0, cx=cx), rr(0.4, r * 1.02, -toe, r * 1.02, cut=1.3, cx=cx)]
    return shell(BOOT[s], mat, rings, caps=(False, mat))


def plate(bone, mat, c, size, tilt_x=0.0, tilt_z=0.0, taper=(0.92, 0.95)):
    R = rot_x(tilt_x) @ rot_z(tilt_z)
    return bx(bone, mat, c, size, rot=R, taper=taper)


# ================================================================ 鯨道の旅装 (巡礼: 苔の毛の頭巾つき外套、骨の留め具、左肩の岩殻、斜めの縄と水筒、脚絆、岩殻の爪先)
def whale_a():
    p = empty()
    # 頭巾: 丸い輪を重ね、顔を開け、後ろへ垂らす
    rings = [rr(24.2, 5.0, -3.6, 5.3, cut=1.8), rr(27.6, 5.4, -4.8, 5.6, cut=2.0), rr(31.0, 5.0, -4.7, 5.3, cut=2.2), rr(33.4, 3.4, -2.8, 4.0, cut=1.6),
             rr(34.2, 1.2, -0.6, 1.6, cut=0.5)]
    p["helmet"] += hide_front(shell(H_, "main", rings, caps=(False, "main")), 31.2, zcos=0.5)
    p["helmet"] += crv(H_, "main", [(-4.6, 31.6, -4.8), (0, 32.2, -5.4), (4.6, 31.6, -4.8)], [0.9, 1.0, 0.9], sides=6)  # 前の折り返し
    for i, x in enumerate((-2.8, -1.4, 0.0, 1.4, 2.8)):
        p["helmet"] += spk(H_, "edge", (x, 31.0, -5.3), (0, -1, -0.2), 0.9, 0.35, sides=5)                   # 骨の玉の紐
    p["helmet"] += crv(H_, "main", [(0, 30.4, 5.4), (0, 27.0, 6.6), (0.3, 23.6, 6.4), (0.4, 21.6, 5.6)], [2.0, 1.6, 1.0, 0.4], sides=6)
    # 胴: 麻の上衣 (体に沿う) + 外套 (肩から裾へ広がり、前は開く) + 斜めの縄 + 水筒 + 帯と小袋
    f = tube_body(B_, "cloth", [24.0, 19.0, 13.0], [4.5, 4.6, 4.7], [-2.6, -2.7, -2.7], [2.6, 2.6, 2.7], cut=1.0)
    f += tube_body(B_, "main", [24.6, 20.0, 15.0, 10.6], [5.4, 5.3, 5.4, 5.8], [-0.4, 0.2, 0.6, 1.0], [3.4, 3.6, 3.8, 4.2], cut=1.2, wave_last=0.5)
    for s in (-1, 1):  # 外套の前身頃 (左右に分かれて開く)
        f += tube_body(B_, "main", [24.4, 18.0, 11.4], [1.6, 1.7, 1.9], [-3.2, -3.4, -3.7], [-1.4, -1.6, -1.8], cut=0.6, cx=s * 3.6, wave_last=0.4)
        f += spk(B_, "edge", (s * 2.2, 21.6, -3.6), (0, 0, -1), 0.8, 0.55, sides=6)                         # 骨の留め具
    f += crv(B_, "cord", [(-2.2, 21.6, -3.9), (0, 21.2, -4.0), (2.2, 21.6, -3.9)], [0.18] * 3, sides=4)
    f += crv(B_, "cord", [(4.6, 23.8, -3.0), (2.4, 21.0, -3.4), (0.0, 18.0, -3.5), (-2.6, 15.4, -3.4), (-4.6, 13.4, -2.8)], [0.42] * 5, sides=5)
    f += tube_body(B_, "sec", [16.4, 14.8, 12.8], [1.2, 1.4, 1.3], [-3.6, -3.8, -3.7], [-1.4, -1.4, -1.5], cut=0.5, cx=-4.6, caps=("sec", "sec"))  # 水筒
    f += crv(B_, "dark", [(-4.6, 16.4, -2.6), (-4.6, 17.4, -2.6)], [0.4, 0.3], sides=6)
    f += tube_body(B_, "sec", [13.4, 12.4], [4.95, 4.95], [-2.95, -2.95], [2.95, 2.95], cut=1.0)        # 帯
    f += bx(B_, "edge", (0, 12.9, -3.15), (1.2, 1.0, 0.4))
    for x in (-2.4, 2.2):
        f += tube_body(B_, "sec", [12.2, 10.6], [1.0, 1.1], [-3.8, -3.9], [-2.6, -2.6], cut=0.4, cx=x, caps=("sec", "sec"))
    # 背の寝具の巻きと小鍋
    f += crv(B_, "fur", [(-4.8, 22.6, 4.4), (4.8, 22.6, 4.4)], [1.7, 1.7], sides=10, caps=(True, True), hint=(0, 1, 0))
    for x in (-2.6, 2.6):
        f += crv(B_, "cord", [(x, 24.4, 4.4), (x, 22.6, 6.3), (x, 20.8, 4.4)], [0.2] * 3, sides=4)
    p["chestplate"] += f
    for s in (-1, 1):
        g = sleeve_r(s, "main", 24.0, 14.0, 2.7, 2.9, wave=0.3)
        g += sleeve_r(s, "sec", 16.0, 12.6, 2.95, 2.9)                                                        # 革の籠手
        g += crv(ARM[s], "cord", [(s * 6.0 - 2.9, 15.4, -1), (s * 6.0, 15.4, -3.1), (s * 6.0 + 2.9, 15.4, -1)], [0.18] * 3, sides=3)
        if s == 1:  # 左肩だけの岩殻 (二段、外が上がる)
            g += plate(ARM[s], "acc", (6.8, 24.8, 0), (6.0, 1.8, 6.2), tilt_z=-12)
            g += plate(ARM[s], "acc", (8.4, 23.0, 0), (3.4, 3.0, 5.6), tilt_z=-26, taper=(0.75, 0.85))
            g += spk(ARM[s], "edge", (7.6, 25.8, -1.4), (0.3, 1, 0), 1.0, 0.45, sides=5)
            g += spk(ARM[s], "edge", (7.6, 25.8, 1.4), (0.3, 1, 0), 1.0, 0.45, sides=5)
        else:
            g += tube_body(ARM[s], "main", [25.0, 23.4], [3.4, 3.5], [-3.4, -3.5], [3.4, 3.5], cut=1.0, cx=-6.2)
        p["chestplate"] += g
    for s in (-1, 1):
        g = leg_r(s, "cloth", 12.0, 5.0, 2.55, 2.45)
        for i in range(4):  # 脚絆 (交互に傾けた帯)
            y = 1.4 + i * 1.25
            g += tube_body(LEG[s], "main", [y + 0.55, y - 0.55], [2.62, 2.66], [-2.62, -2.66], [2.62, 2.66], cut=0.9, cx=s * 2.15)
        g += plate(LEG[s], "sec", (s * 2.15, 6.0, -2.7), (3.4, 2.4, 0.9), tilt_x=10, taper=(0.85, 0.9))
        p["leggings"] += g
    for s in (-1, 1):
        g = boot_r(s, "sec", 4.2)
        g += tube_body(BOOT[s], "fur", [4.6, 3.6], [2.95, 2.9], [-2.95, -2.9], [2.95, 2.9], cut=1.0, cx=s * 2.15, wave_last=0.25)
        g += plate(BOOT[s], "acc", (s * 2.15, 1.1, -3.2), (4.2, 1.8, 1.8), tilt_x=-10, taper=(0.8, 0.75))
        g += bx(BOOT[s], "dark", (s * 2.15, 0.15, -0.3), (5.2, 0.3, 6.6))
        p["boots"] += g
    return p


def whale_b():
    p = empty()
    # 兜: 重ねた骨の板の頭巾形、低い背びれ (後ろへ流す)
    for i, (y, r) in enumerate(((25.2, 5.3), (28.0, 5.4), (30.8, 5.0))):
        for s in (-1, 1):
            p["helmet"] += plate(H_, "main" if i % 2 == 0 else "acc", (s * 4.6, y, 0.6), (1.2, 3.0, 9.4), tilt_z=s * 10)
        p["helmet"] += plate(H_, "main", (0, y, 4.8), (9.6, 3.0, 1.2), tilt_x=-10)
    p["helmet"] += plate(H_, "acc", (0, 32.8, 0.6), (9.0, 1.4, 9.6), taper=(0.8, 0.8))
    for k in range(4):
        p["helmet"] += plate(H_, "edge", (0, 33.6 + k * 0.4, 1.0 + k * 1.2), (0.6, 2.2 - k * 0.3, 1.6), tilt_x=-25)
    p["helmet"] += bx(H_, "edge", (0, 31.6, -4.9), (8.8, 0.8, 0.6))
    # 胴: 重ね板の胸 (段ごとに外へ開く)、背の肋の弧、腰の尾びれの垂れ
    f = bx(B_, "cloth", (0, 18.0, 0), (9.0, 12.0, 4.9), skip=("u", "b"))
    for i, y in enumerate((22.6, 20.0, 17.4, 14.8)):
        f += plate(B_, "main" if i % 2 == 0 else "acc", (0, y, -2.7 - i * 0.12), (9.4 + i * 0.3, 2.8, 0.9), tilt_x=12)
        f += plate(B_, "main", (0, y, 2.8 + i * 0.1), (9.4 + i * 0.3, 2.8, 0.9), tilt_x=-12)
    for k in range(3):
        f += crv(B_, "edge", [(-4.0, 22 - k * 2.4, 3.6), (0, 22.8 - k * 2.4, 4.4), (4.0, 22 - k * 2.4, 3.6)], [0.45] * 3, sides=5)
    for s in (-1, 1):
        f += plate(B_, "acc", (s * 2.6, 10.4, -2.6), (3.6, 3.6, 0.8), tilt_x=14, tilt_z=s * 8)
        f += plate(B_, "acc", (s * 2.6, 10.4, 2.6), (3.6, 3.6, 0.8), tilt_x=-14, tilt_z=s * 8)
    f += bx(B_, "edge", (0, 12.8, 0), (9.8, 1.0, 5.6), skip=("u",))
    p["chestplate"] += f
    for s in (-1, 1):
        g = bx(ARM[s], "cloth", (s * 6.0, 18.5, 0), (4.6, 11.0, 4.6), skip=("u",))
        n = 4 if s == 1 else 3
        for i in range(n):  # 肩の重ね板 (外が上がる)。左肩は一段多い (非対称)
            g += plate(ARM[s], "main" if i % 2 == 0 else "acc", (s * (6.8 + i * 0.5), 25.0 - i * 1.4, 0), (6.2 - i * 0.4, 1.4, 6.4 - i * 0.3), tilt_z=-s * (10 + i * 3))
        g += plate(ARM[s], "sec", (s * 6.0, 14.6, 0), (5.2, 3.2, 5.3))
        p["chestplate"] += g
    for s in (-1, 1):
        g = bx(LEG[s], "cloth", (s * 2.15, 8.6, 0), (4.6, 6.6, 4.9), skip=("u", "b"))
        for i, y in enumerate((9.6, 7.2)):
            g += plate(LEG[s], "main", (s * 2.3, y, -0.1), (5.0, 2.6, 5.4), tilt_z=s * 8)
        g += plate(LEG[s], "acc", (s * 2.15, 5.6, -2.9), (3.6, 2.6, 1.0), tilt_x=12)
        p["leggings"] += g
    for s in (-1, 1):
        g = bx(BOOT[s], "sec", (s * 2.15, 2.0, 0), (4.8, 4.0, 5.0), skip=("b",))
        g += plate(BOOT[s], "acc", (s * 2.15, 1.0, -3.3), (4.4, 2.0, 2.0), tilt_x=-12, taper=(0.8, 0.75))
        g += plate(BOOT[s], "main", (s * 2.4, 4.2, 0), (5.4, 1.2, 5.6), tilt_z=s * 10)
        g += bx(BOOT[s], "dark", (s * 2.15, 0.2, 0), (5.0, 0.4, 5.6), skip=("u",))
        p["boots"] += g
    return p


def whale_c():
    p = empty()
    # 大きな頭巾 (先が背まで垂れる三角)
    rings = [rr(24.0, 5.6, -4.2, 6.0, cut=2.0), rr(29.0, 6.4, -5.6, 7.0, cut=2.4), rr(33.0, 5.0, -4.0, 6.4, cut=2.0), rr(36.0, 1.0, 1.0, 3.0, cut=0.4)]
    p["helmet"] += hide_front(shell(H_, "main", rings, caps=(False, "main")), 31.0, zcos=0.5)
    p["helmet"] += crv(H_, "main", [(0, 35.0, 2.6), (0, 30.0, 8.0), (0, 22.0, 8.6), (0, 17.0, 7.0)], [2.6, 2.4, 1.6, 0.4], sides=5)
    # 大きな外套 (膝まで、裾が広がる)
    f = tube_body(B_, "main", [25.0, 19.0, 12.0, 6.0], [6.0, 6.4, 7.2, 8.4], [-3.0, -3.4, -4.0, -4.8], [4.4, 5.0, 5.8, 6.6], cut=1.6, wave_last=0.8)
    f += crv(B_, "cord", [(4.8, 24.0, -3.4), (-4.8, 13.0, -4.2)], [0.7, 0.7], sides=5)
    f += tube_body(B_, "sec", [15.0, 11.6], [1.8, 1.8], [-5.2, -5.4], [-2.6, -2.6], cut=0.6, cx=-5.0, caps=("sec", "sec"))
    f += crv(B_, "fur", [(-6.0, 23.2, 5.6), (6.0, 23.2, 5.6)], [2.4, 2.4], sides=8, caps=(True, True), hint=(0, 1, 0))
    p["chestplate"] += f
    for s in (-1, 1):
        g = sleeve_r(s, "main", 24.0, 13.0, 3.2, 3.8, wave=0.4)
        if s == 1:
            g += plate(ARM[s], "acc", (7.6, 25.6, 0), (8.0, 3.0, 7.6), tilt_z=-20, taper=(0.7, 0.8))
        p["chestplate"] += g
    for s in (-1, 1):
        p["leggings"] += leg_r(s, "cloth", 12.0, 1.0, 2.6, 3.0)
    for s in (-1, 1):
        p["boots"] += boot_r(s, "sec", 5.0, toe=4.6, r=3.2)
        p["boots"] += plate(BOOT[s], "acc", (s * 2.15, 1.4, -3.9), (5.6, 2.6, 2.4), tilt_x=-14, taper=(0.75, 0.7))
    return p


# ================================================================ 鉄張りの旅装 (鎖帷子と陣羽織、真鍮の鯨の紋、重ねた肩当て、鍋形の兜と鎖の垂れ、肘・膝・爪先の鉄)
def iron_a():
    p = empty()
    # 鍋形の兜 (丸い鉢・つば・天辺の鋲) と鎖の垂れ (後ろと横)
    p["helmet"] += shell(H_, "metal", [rr(31.0, 5.2, -5.2, 5.2, cut=2.0), rr(33.4, 4.6, -4.6, 4.6, cut=1.8), rr(35.0, 2.6, -2.6, 2.6, cut=1.0)], caps=(False, "metal"))
    p["helmet"] += shell(H_, "edge", [rr(30.6, 6.6, -6.6, 6.6, cut=2.4), rr(31.2, 6.2, -6.2, 6.2, cut=2.2)], caps=("edge", "edge"))
    p["helmet"] += spk(H_, "trim", (0, 35.2, 0), (0, 1, 0), 1.0, 0.5, sides=6)
    p["helmet"] += hide_front(shell(H_, "main", [rr(31.0, 4.9, -4.6, 4.9, cut=1.8), rr(27.0, 5.0, -4.4, 5.2, cut=1.8), rr(24.0, 5.2, -3.4, 5.6, cut=2.0, dy=[0.3, -0.3] * 4)]),
                                 31.5, zcos=0.4)
    # 胴: 鎖帷子 (膝上まで、裾が波打つ) + 陣羽織 (前後の垂れ、縁取り) + 真鍮の紋 + 喉当て + 帯
    f = tube_body(B_, "main", [24.2, 18.0, 12.0, 9.0], [4.7, 4.8, 5.0, 5.4], [-2.8, -2.9, -3.0, -3.3], [2.8, 2.8, 3.0, 3.3], cut=1.0, wave_last=0.4)
    for z, sg in ((-1, -1), (1, 1)):
        f += tube_body(B_, "cloth", [23.6, 16.0, 9.6], [3.2, 3.3, 3.4], [sg * 3.0 if sg < 0 else 2.9, sg * 3.15 if sg < 0 else 3.05, sg * 3.4 if sg < 0 else 3.3],
                       [sg * 2.9 if sg < 0 else 3.0, sg * 3.05 if sg < 0 else 3.15, sg * 3.3 if sg < 0 else 3.4], cut=0.5, wave_last=0.3)
        f += crv(B_, "trim", [(-3.2, 9.4, sg * 3.45), (0, 9.2, sg * 3.6), (3.2, 9.4, sg * 3.45)], [0.25] * 3, sides=4)
    f += spk(B_, "trim", (0, 18.8, -3.2), (0, 0, -1), 0.6, 1.4, sides=4)                                    # 真鍮の紋 (菱)
    f += shell(B_, "metal", [rr(25.6, 3.6, -3.2, 3.4, cut=1.2), rr(24.0, 4.0, -3.4, 3.4, cut=1.4)], caps=("metal", False))
    f += tube_body(B_, "sec", [13.8, 12.6], [5.05, 5.05], [-3.05, -3.05], [3.05, 3.05], cut=1.0)
    f += bx(B_, "trim", (0, 13.2, -3.25), (1.6, 1.4, 0.4))
    f += tube_body(B_, "sec", [12.6, 10.6], [1.1, 1.2], [-4.1, -4.2], [-2.8, -2.8], cut=0.4, cx=-3.4, caps=("sec", "sec"))
    p["chestplate"] += f
    for s in (-1, 1):
        g = sleeve_r(s, "main", 24.0, 16.0, 2.7, 2.8)
        for i in range(3):  # 重ねた肩当て (丸い板が外へ傾く)
            y = 25.0 - i * 1.5
            g += shell(ARM[s], "metal" if i % 2 == 0 else "edge", [rr(y + 0.6, 3.4 - i * 0.15, -3.5 + i * 0.1, 3.5 - i * 0.1, cut=1.4, cx=s * (6.4 + i * 0.25)),
                                                                   rr(y - 0.7, 3.6 - i * 0.15, -3.7 + i * 0.1, 3.7 - i * 0.1, cut=1.5, cx=s * (6.7 + i * 0.25))],
                       caps=("metal", False))
        g += shell(ARM[s], "metal", [rr(19.0, 2.9, -3.2, 2.2, cut=1.2, cx=s * 6.0), rr(17.0, 2.9, -3.4, 2.2, cut=1.2, cx=s * 6.0)], caps=("metal", "metal"))
        g += sleeve_r(s, "sec", 16.2, 12.6, 2.95, 3.0)
        p["chestplate"] += g
    for s in (-1, 1):
        g = leg_r(s, "cloth", 12.0, 5.0, 2.55, 2.45)
        g += leg_r(s, "main", 12.0, 8.4, 2.75, 2.8, wave=0.3)
        g += shell(LEG[s], "metal", [rr(7.2, 1.9, -3.3, -1.8, cut=0.8, cx=s * 2.15), rr(4.6, 1.8, -3.1, -1.8, cut=0.8, cx=s * 2.15)], caps=("metal", "metal"))
        g += spk(LEG[s], "edge", (s * 2.15, 6.0, -3.3), (0, 0, -1), 0.5, 0.45, sides=6)
        p["leggings"] += g
    for s in (-1, 1):
        g = boot_r(s, "sec", 4.4)
        g += tube_body(BOOT[s], "metal", [4.9, 4.1], [2.95, 2.95], [-2.95, -2.95], [2.95, 2.95], cut=1.0, cx=s * 2.15)
        g += shell(BOOT[s], "metal", [rr(2.4, 2.5, -3.6, -1.6, cut=1.0, cx=s * 2.15), rr(0.4, 2.7, -3.6, -1.6, cut=1.0, cx=s * 2.15)], caps=("metal", "metal"))
        for y in (1.8, 2.8, 3.6):
            g += crv(BOOT[s], "cord", [(s * 2.15 - 1.0, y, -2.9), (s * 2.15 + 1.0, y + 0.5, -2.9)], [0.14, 0.14], sides=3)
        g += bx(BOOT[s], "dark", (s * 2.15, 0.15, -0.3), (5.2, 0.3, 6.6))
        p["boots"] += g
    return p


def iron_b():
    p = empty()
    # 重ね板の兜 (縦の板を並べた鉢、頬当て、低い前立て)
    for k in range(8):
        a = k / 8 * 2 * math.pi
        x, z = math.sin(a) * 4.6, -math.cos(a) * 4.6
        if z < -3.5:
            continue
        p["helmet"] += plate(H_, "metal" if k % 2 == 0 else "edge", (x, 31.4, z), (3.4, 4.6, 0.8), tilt_x=-12 * math.cos(a), tilt_z=12 * math.sin(a))
    p["helmet"] += plate(H_, "metal", (0, 34.0, 0), (8.6, 1.4, 8.6), taper=(0.7, 0.7))
    for s in (-1, 1):
        p["helmet"] += plate(H_, "metal", (s * 4.8, 27.6, -1.0), (0.8, 4.0, 4.6), tilt_z=s * 12)
    p["helmet"] += plate(H_, "trim", (0, 34.8, -3.4), (2.0, 2.6, 0.5), tilt_x=-15)
    # 胴: 縅の重ね板 (前後 4 段)、陣羽織は背だけ、紋
    f = bx(B_, "main", (0, 18.0, 0), (9.2, 12.2, 5.0), skip=("u", "b"))
    for i, y in enumerate((22.8, 20.2, 17.6, 15.0, 12.4)):
        f += plate(B_, "metal" if i % 2 == 0 else "edge", (0, y, -2.8 - i * 0.12), (9.6 + i * 0.3, 2.8, 0.9), tilt_x=12)
        f += crv(B_, "cord", [(-4.6 - i * 0.15, y - 1.3, -3.3 - i * 0.12), (4.6 + i * 0.15, y - 1.3, -3.3 - i * 0.12)], [0.12, 0.12], sides=3)
    f += plate(B_, "cloth", (0, 16.4, 3.0), (8.4, 14.0, 0.6), tilt_x=-6)
    f += spk(B_, "trim", (0, 20.0, -3.6), (0, 0, -1), 0.5, 1.2, sides=4)
    for s in (-1, 1):
        f += plate(B_, "metal", (s * 2.8, 9.6, -2.8), (3.8, 4.0, 0.8), tilt_x=14, tilt_z=s * 10)
        f += plate(B_, "metal", (s * 2.8, 9.6, 2.8), (3.8, 4.0, 0.8), tilt_x=-14, tilt_z=s * 10)
    p["chestplate"] += f
    for s in (-1, 1):
        g = bx(ARM[s], "main", (s * 6.0, 18.5, 0), (4.6, 11.0, 4.6), skip=("u",))
        for i in range(4 if s == -1 else 3):  # 右だけ一段多い大袖 (非対称)
            g += plate(ARM[s], "metal" if i % 2 == 0 else "edge", (s * (6.8 + i * 0.5), 25.0 - i * 1.5, 0), (6.4 - i * 0.3, 1.5, 6.4 - i * 0.2), tilt_z=-s * (12 + i * 3))
        g += plate(ARM[s], "metal", (s * 6.0, 18.0, -1.6), (4.6, 2.2, 2.0), tilt_x=10)
        g += plate(ARM[s], "sec", (s * 6.0, 14.6, 0), (5.2, 3.2, 5.3))
        p["chestplate"] += g
    for s in (-1, 1):
        g = bx(LEG[s], "main", (s * 2.15, 8.6, 0), (4.6, 6.6, 4.9), skip=("u", "b"))
        for i, y in enumerate((10.2, 8.0)):
            g += plate(LEG[s], "metal" if i == 0 else "edge", (s * 2.4, y, -0.1), (5.0, 2.4, 5.4), tilt_z=s * 9)
        g += plate(LEG[s], "metal", (s * 2.15, 5.0, -2.9), (3.8, 4.0, 1.0), tilt_x=8)
        p["leggings"] += g
    for s in (-1, 1):
        g = bx(BOOT[s], "sec", (s * 2.15, 2.0, 0), (4.8, 4.0, 5.0), skip=("b",))
        g += plate(BOOT[s], "metal", (s * 2.15, 1.0, -3.2), (4.4, 2.0, 1.8), tilt_x=-12, taper=(0.8, 0.75))
        g += plate(BOOT[s], "metal", (s * 2.4, 4.2, 0), (5.4, 1.2, 5.6), tilt_z=s * 10)
        g += bx(BOOT[s], "dark", (s * 2.15, 0.2, 0), (5.0, 0.4, 5.6), skip=("u",))
        p["boots"] += g
    return p


def iron_c():
    p = empty()
    # 大きなつばの鍋兜と長い鎖の垂れ
    p["helmet"] += shell(H_, "metal", [rr(31.0, 5.4, -5.4, 5.4, cut=2.2), rr(34.6, 4.0, -4.0, 4.0, cut=1.6), rr(36.4, 1.4, -1.4, 1.4, cut=0.6)], caps=(False, "metal"))
    p["helmet"] += shell(H_, "edge", [rr(30.4, 8.6, -8.6, 8.6, cut=3.0), rr(31.2, 8.0, -8.0, 8.0, cut=2.8)], caps=("edge", "edge"))
    p["helmet"] += hide_front(shell(H_, "main", [rr(31.0, 5.0, -4.6, 5.0, cut=1.8), rr(21.0, 6.0, -3.0, 6.4, cut=2.2)]), 31.5, zcos=0.4)
    f = tube_body(B_, "main", [24.4, 16.0, 8.0, 4.0], [5.2, 5.6, 6.6, 7.4], [-3.0, -3.4, -4.0, -4.6], [3.0, 3.4, 4.0, 4.6], cut=1.2, wave_last=0.6)
    f += tube_body(B_, "cloth", [24.0, 12.0, 5.0], [3.8, 4.2, 4.8], [-3.6, -3.8, -4.6], [-3.1, -3.4, -4.1], cut=0.6)
    f += spk(B_, "trim", (0, 19.0, -3.8), (0, 0, -1), 0.8, 2.0, sides=4)
    p["chestplate"] += f
    for s in (-1, 1):
        g = sleeve_r(s, "main", 24.0, 13.0, 2.9, 3.2)
        g += plate(ARM[s], "metal", (s * 7.6, 25.6, 0), (8.6, 3.4, 7.8), tilt_z=-s * 22, taper=(0.75, 0.85))
        p["chestplate"] += g
    for s in (-1, 1):
        p["leggings"] += leg_r(s, "cloth", 12.0, 4.0, 2.6, 2.8)
        p["leggings"] += plate(LEG[s], "metal", (s * 2.15, 6.0, -3.2), (4.4, 5.2, 1.4), tilt_x=8)
    for s in (-1, 1):
        p["boots"] += boot_r(s, "metal", 4.8, toe=4.2, r=3.1)
    return p


SETS = {"wr_a": whale_a, "wr_b": whale_b, "wr_c": whale_c, "ir_a": iron_a, "ir_b": iron_b, "ir_c": iron_c}


def main():
    OUTDIR.mkdir(parents=True, exist_ok=True)
    for k, fn in SETS.items():
        travel_one.SETS[k] = fn
        tpaint.SETS[k] = tpaint.SETS["whale_road"] if k.startswith("wr") else build_outfit.IRON
        build_one.TRAVEL_GLOW[k] = "whale"
    for v, label in (("a", "A 仕立て"), ("b", "B 重ね板"), ("c", "C 大きな輪郭")):
        rows = []
        for key, nm in ((f"wr_{v}", "鯨道の旅装"), (f"ir_{v}", "鉄張りの旅装")):
            rows.append(build_one.strip(build_one.travel_set(key), "whale", f"{label}・{nm}"))
            print(key, flush=True)
        S = Image.new("RGB", (rows[0].width, sum(r.height for r in rows)))
        y = 0
        for r in rows:
            S.paste(r, (0, y))
            y += r.height
        S.save(OUTDIR / f"rough_{v}.png")
    # 今の作り (比べる)
    import outfit_two
    travel_one.SETS["iron2_a"] = outfit_two.iron2_a
    tpaint.SETS["iron2_a"] = build_outfit.IRON
    rows = [build_one.strip(build_one.travel_set("whale_road"), "whale", "今・鯨道の旅装"), build_one.strip(build_one.travel_set("iron2_a"), "whale", "今・鉄張りの旅装")]
    S = Image.new("RGB", (rows[0].width, sum(r.height for r in rows)))
    S.paste(rows[0], (0, 0))
    S.paste(rows[1], (0, rows[0].height))
    S.save(OUTDIR / "now.png")


if __name__ == "__main__":
    sys.stdout.reconfigure(encoding="utf-8")
    main()
