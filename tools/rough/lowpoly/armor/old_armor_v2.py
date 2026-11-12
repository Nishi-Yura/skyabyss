"""古い防具 7 組の作り直し (2026-11-12。直し方は「C 大きな輪郭 + A の細部」に決まった: rough_old_armor.py / docs/review/armor_old/)。

頭巾・つば・肩・裾を大きく誇張して箱の輪郭から外し (C)、丸い輪を重ねた体に沿う形に縫い目・帯・留め具・小物を足す (A)。
文書 16 の決まり: 腰より下の長い布は脚の骨に左右二枚で付ける (胴に垂らすと歩くとき脚が貫く)。肩の張り出しは腕の骨。背の張り出しは y 15 以上。
  鯨道の旅装 (whale_road)    巡礼: 背まで垂れる大きな苔の頭巾、骨の玉の紐、外套 (裾は脚へ)、左肩の二段の岩殻、斜めの縄と革の水筒、背の寝具、脚絆、岩殻の爪先
  潮渡りの旅装 (tide_wader)   浜の漁師: 大きな編み笠 (先に真珠)、網の合羽と浮きの栓、胸までの胴長と肩紐、貝の腰板、膝の帯、塩の吹いた長靴
  峠越えの旅装 (peak_crossing) 雪の峠: 毛皮の縁の大きな頭巾と銅の雪眼鏡、羽を詰めた段の外套、胸と腰の命綱と銅の環、肩の縄の輪、毛皮の脚巻き、爪の靴
  灰歩きの旅装 (ash_walking)  灰の地: つばの広い革の帽子と灰除けの垂れ、鉄の濾し筒の面当て、膝までの麻の長外套 (裾は脚へ)、鋲の鉄の肩板、熱除けの籠手、鉄の厚底
  初めの旅装 (wayfarer)       島を出る旅人: つば広の帽子と帯と羽根、麻の上衣、肩を覆う頭巾つきの藍の短い外套、帯と袋二つと瓢箪、当て布の脚衣、脛の巻き布、折り返しの靴
  鉄張りの旅装 (ironclad)     鎖帷子 (裾は脚へ) と藍の陣羽織、真鍮の鯨の紋、喉当て、三段の丸い肩当て (右は大袖)、鍋形の大つばの兜と鎖の垂れ、肘・膝・爪先の鉄
  空石の胸当て (sky_stone)    胴だけ: 空石の鱗を段に縫い付けた布の胴、大きな毛皮の肩、腰の銅の鎖
"""
from __future__ import annotations

import math

from akit import V, bx, crv, spk, rr, shell, hide_front, rot_x, rot_y, rot_z, ARM, LEG, BOOT
from rough_old_armor import empty, ring_at, tube_body, sleeve_r, leg_r, boot_r, plate, H_, B_


def skirt(p, mat, top, bot, r0, r1, wave=0.5, zoff=0.0):
    """腰から下の長い裾を、脚の骨に左右一枚ずつ (歩いても脚が貫かない)"""
    for s in (-1, 1):
        p["leggings" if "leggings" in p else "chestplate"] += leg_r(s, mat, top, bot, r0, r1, wave=wave, zoff=zoff)


def hem_band(p, piece, mat, y, r):
    for s in (-1, 1):
        p[piece] += tube_body(LEG[s], mat, [y + 0.35, y - 0.35], [r, r], [-r, -r], [r, r], cut=r * 0.4, cx=s * 2.15)


def stitches(f, bone, mat, pts, n=6):
    """縫い目 (短い糸の並び)"""
    (x0, y0, z0), (x1, y1, z1) = pts
    for k in range(n):
        t = (k + 0.5) / n
        x, y, z = x0 + (x1 - x0) * t, y0 + (y1 - y0) * t, z0 + (z1 - z0) * t
        f += bx(bone, "cord", (x, y, z), (0.18, 0.5, 0.12))
    return f


def pouch(f, bone, x, y, z, mat="sec", flap="acc", w=1.1, h=1.2):
    f += tube_body(bone, mat, [y + h / 2, y - h / 2], [w, w * 1.05], [z - 0.7, z - 0.75], [z + 0.6, z + 0.6], cut=0.4, cx=x, caps=(mat, mat))
    f += bx(bone, flap, (x, y + h / 2 - 0.1, z - 0.78), (w * 2.1, 0.6, 0.2))
    f += bx(bone, "edge", (x, y + h / 2 - 0.35, z - 0.92), (0.4, 0.4, 0.15))
    return f


def belt(f, y, mat="sec", buckle="edge", r=(5.05, 3.05)):
    f += tube_body(B_, mat, [y + 0.6, y - 0.6], [r[0], r[0]], [-r[1], -r[1]], [r[1], r[1]], cut=1.0)
    f += bx(B_, buckle, (0, y, -r[1] - 0.2), (1.6, 1.4, 0.4))
    f += bx(B_, "dark", (0, y, -r[1] - 0.42), (0.6, 0.6, 0.12))
    return f


# ================================================================ 鯨道の旅装
def whale_road():
    p = empty()
    rings = [rr(24.0, 5.6, -4.2, 6.0, cut=2.0), rr(27.0, 6.2, -5.4, 6.6, cut=2.3), rr(30.2, 6.2, -5.6, 6.6, cut=2.4), rr(33.0, 5.0, -4.2, 6.0, cut=2.0),
             rr(35.0, 2.4, -1.2, 3.6, cut=1.0)]
    p["helmet"] += hide_front(shell(H_, "main", rings, caps=(False, "main")), 31.0, zcos=0.5)
    p["helmet"] += crv(H_, "main", [(-5.2, 31.4, -5.4), (0, 32.2, -6.0), (5.2, 31.4, -5.4)], [1.0, 1.1, 1.0], sides=6)        # 前の折り返し
    p["helmet"] += crv(H_, "main", [(0, 34.4, 3.2), (0, 31.0, 7.2), (0.2, 27.0, 7.6), (0.3, 24.0, 6.8)], [2.4, 2.2, 1.6, 0.4], sides=6)
    for x in (-3.4, -1.7, 0.0, 1.7, 3.4):
        p["helmet"] += spk(H_, "edge", (x, 31.2, -5.9), (0, -1, -0.2), 1.0, 0.38, sides=5)                                  # 骨の玉の紐
    p["helmet"] = stitches(p["helmet"], H_, "cord", ((0, 34.6, 2.6), (0, 30.6, 6.8)), n=5)
    f = tube_body(B_, "cloth", [24.0, 19.0, 13.0], [4.5, 4.6, 4.7], [-2.6, -2.7, -2.7], [2.6, 2.6, 2.7], cut=1.0)
    f += tube_body(B_, "main", [25.0, 21.0, 16.0, 13.0], [6.0, 5.8, 5.7, 5.8], [-0.6, 0.2, 0.6, 1.0], [4.0, 4.0, 4.0, 4.2], cut=1.4)   # 外套 (背と脇)
    for s in (-1, 1):
        f += tube_body(B_, "main", [24.8, 18.0, 13.0], [1.8, 1.9, 2.0], [-3.4, -3.6, -3.8], [-1.4, -1.6, -1.8], cut=0.6, cx=s * 3.9)  # 前身頃 (開く)
        f += spk(B_, "edge", (s * 2.3, 21.8, -3.8), (0, 0, -1), 0.9, 0.6, sides=6)
        f = stitches(f, B_, "cord", ((s * 2.2, 20.6, -3.75), (s * 2.6, 12.2, -3.95)), n=6)
    f += crv(B_, "cord", [(-2.3, 21.8, -4.1), (0, 21.3, -4.2), (2.3, 21.8, -4.1)], [0.2] * 3, sides=4)
    f += crv(B_, "cord", [(5.0, 24.0, -3.2), (2.6, 21.0, -3.6), (0.0, 18.0, -3.7), (-2.8, 15.2, -3.6), (-5.0, 13.0, -3.0)], [0.45] * 5, sides=5)
    f += tube_body(B_, "sec", [16.8, 15.0, 12.8], [1.3, 1.5, 1.4], [-3.8, -4.0, -3.9], [-1.4, -1.4, -1.5], cut=0.5, cx=-5.0, caps=("sec", "sec"))
    f += crv(B_, "dark", [(-5.0, 16.8, -2.6), (-5.0, 17.9, -2.6)], [0.42, 0.32], sides=6)
    f = belt(f, 12.9)
    for x in (-2.4, 2.4):
        f = pouch(f, B_, x, 11.4, -3.3)
    f += crv(B_, "fur", [(-5.2, 23.0, 4.8), (5.2, 23.0, 4.8)], [1.9, 1.9], sides=10, caps=(True, True), hint=(0, 1, 0))
    for x in (-2.8, 2.8):
        f += crv(B_, "cord", [(x, 25.0, 4.8), (x, 23.0, 6.9), (x, 21.0, 4.8)], [0.22] * 3, sides=4)
    f += tube_body(B_, "acc", [19.4, 17.0], [1.4, 1.5], [3.0, 3.0], [5.6, 5.6], cut=0.5, cx=2.6, caps=("acc", "acc"))       # 小鍋
    p["chestplate"] += f
    for s in (-1, 1):
        g = sleeve_r(s, "main", 24.2, 13.6, 2.9, 3.3, wave=0.35)
        g += sleeve_r(s, "sec", 15.6, 12.4, 3.1, 3.05)
        g += crv(ARM[s], "cord", [(s * 6.0 - 3.0, 15.0, -1), (s * 6.0, 15.0, -3.2), (s * 6.0 + 3.0, 15.0, -1)], [0.18] * 3, sides=3)
        if s == 1:
            g += plate(ARM[s], "acc", (7.0, 25.2, 0), (7.0, 2.2, 7.0), tilt_z=-14)
            g += plate(ARM[s], "acc", (9.2, 23.0, 0), (4.0, 3.6, 6.2), tilt_z=-28, taper=(0.72, 0.85))
            for z in (-1.8, 0.0, 1.8):
                g += spk(ARM[s], "edge", (8.0, 26.4, z), (0.3, 1, 0), 1.2, 0.45, sides=5)
        else:
            g += tube_body(ARM[s], "main", [25.4, 23.4], [3.6, 3.8], [-3.6, -3.8], [3.6, 3.8], cut=1.2, cx=-6.2, wave_last=0.3)
        p["chestplate"] += g
    for s in (-1, 1):
        g = leg_r(s, "cloth", 12.0, 5.0, 2.55, 2.45)
        g += leg_r(s, "main", 12.0, 5.6, 2.7, 3.4, wave=0.5)                                                                # 外套の裾 (脚へ)
        for i in range(4):
            y = 1.4 + i * 1.2
            g += tube_body(LEG[s], "main", [y + 0.5, y - 0.5], [2.62, 2.66], [-2.62, -2.66], [2.62, 2.66], cut=0.9, cx=s * 2.15)
        g += plate(LEG[s], "sec", (s * 2.15, 6.0, -2.8), (3.4, 2.4, 0.9), tilt_x=10, taper=(0.85, 0.9))
        p["leggings"] += g
    for s in (-1, 1):
        g = boot_r(s, "sec", 4.4, toe=3.8, r=2.95)
        g += tube_body(BOOT[s], "fur", [5.0, 3.8], [3.3, 3.2], [-3.3, -3.2], [3.3, 3.2], cut=1.1, cx=s * 2.15, wave_last=0.3)
        g += plate(BOOT[s], "acc", (s * 2.15, 1.2, -3.6), (4.6, 2.2, 2.2), tilt_x=-12, taper=(0.78, 0.72))
        g += bx(BOOT[s], "dark", (s * 2.15, 0.15, -0.4), (5.6, 0.3, 7.0))
        p["boots"] += g
    return p


# ================================================================ 潮渡りの旅装
def tide_wader():
    p = empty()
    hat = [rr(31.0, 9.0, -9.0, 9.0, cut=3.4), rr(32.0, 6.6, -6.6, 6.6, cut=2.6), rr(34.4, 3.6, -3.6, 3.6, cut=1.4), rr(36.4, 0.8, -0.8, 0.8, cut=0.3)]
    p["helmet"] += shell(H_, "main", hat, caps=("main", "main"))                                                            # 大きな編み笠
    p["helmet"] += shell(H_, "cord", [rr(32.8, 5.3, -5.3, 5.3, cut=2.0), rr(33.4, 5.0, -5.0, 5.0, cut=1.9)], caps=(False, False))
    p["helmet"] += spk(H_, "metal", (0, 36.6, 0), (0, 1, 0), 1.1, 0.6, sides=8)                                            # 先の真珠
    for s in (-1, 1):
        p["helmet"] += crv(H_, "cord", [(s * 4.6, 31.0, 0), (s * 4.0, 27.0, -1.6), (s * 1.2, 24.6, -3.6)], [0.16] * 3, sides=3)  # 顎紐
    f = tube_body(B_, "cloth", [24.6, 20.0, 15.4], [6.2, 6.4, 6.6], [-3.8, -4.0, -4.2], [4.2, 4.4, 4.6], cut=1.6, wave_last=0.6)   # 網の合羽
    for k, (x, y, z) in enumerate(((-4.6, 16.6, -4.0), (4.4, 17.4, -4.0), (-1.4, 15.8, -4.4), (5.6, 17.0, 3.2), (-5.2, 18.0, 3.6))):
        f += tube_body(B_, "fur", [y + 0.6, y - 0.6], [0.7, 0.7], [z - 0.6, z - 0.6], [z + 0.6, z + 0.6], cut=0.3, cx=x, caps=("fur", "fur"))  # 浮きの栓
    f += tube_body(B_, "sec", [21.0, 16.0, 13.0], [4.8, 4.9, 5.0], [-2.9, -3.0, -3.1], [2.9, 3.0, 3.1], cut=1.0)                # 胴長
    for s in (-1, 1):
        f += crv(B_, "sec", [(s * 2.6, 21.0, -3.0), (s * 2.8, 24.6, -2.0), (s * 2.6, 25.0, 1.0), (s * 2.6, 21.0, 3.0)], [0.5] * 4, sides=4)  # 肩紐
        f += bx(B_, "metal", (s * 2.6, 20.6, -3.25), (0.8, 0.8, 0.3))
    for k in range(5):                                                                                                       # 貝の腰板
        a = -0.9 + k * 0.45
        f += plate(B_, "acc", (math.sin(a) * 5.0, 12.0, -math.cos(a) * 3.2), (2.0, 2.6, 0.5), tilt_x=12 * math.cos(a), tilt_z=-12 * math.sin(a))
    f = belt(f, 13.6, mat="cord", buckle="metal")
    f += crv(B_, "edge", [(-3.6, 19.0, -3.3), (0, 18.6, -3.4), (3.6, 19.0, -3.3)], [0.3] * 3, sides=4)                     # 塩の吹いた線
    p["chestplate"] += f
    for s in (-1, 1):
        g = sleeve_r(s, "cloth", 24.0, 14.0, 3.0, 3.4, wave=0.4)
        g += sleeve_r(s, "sec", 15.0, 12.4, 3.1, 3.1)
        p["chestplate"] += g
    for s in (-1, 1):
        g = leg_r(s, "sec", 11.4, 5.0, 2.7, 2.7)
        g += tube_body(LEG[s], "cord", [6.6, 5.6], [2.85, 2.85], [-2.85, -2.85], [2.85, 2.85], cut=1.0, cx=s * 2.15)           # 膝の帯
        g += crv(LEG[s], "edge", [(s * 2.15 - 1.6, 9.0, -2.85), (s * 2.15 + 1.4, 8.2, -2.85)], [0.25, 0.2], sides=3)
        p["leggings"] += g
    for s in (-1, 1):
        g = boot_r(s, "sec", 5.0, toe=3.6, r=3.0)
        g += tube_body(BOOT[s], "edge", [5.4, 4.6], [3.15, 3.15], [-3.15, -3.15], [3.15, 3.15], cut=1.1, cx=s * 2.15)
        g += bx(BOOT[s], "dark", (s * 2.15, 0.15, -0.3), (5.8, 0.3, 6.8))
        p["boots"] += g
    return p


# ================================================================ 峠越えの旅装
def peak_crossing():
    p = empty()
    rings = [rr(24.6, 5.6, -4.4, 5.8, cut=2.2), rr(27.4, 6.2, -5.6, 6.8, cut=2.4), rr(31.0, 6.0, -5.4, 6.4, cut=2.4), rr(34.0, 4.0, -3.4, 4.6, cut=1.6),
             rr(35.2, 1.4, -1.0, 2.0, cut=0.5)]
    p["helmet"] += hide_front(shell(H_, "main", rings, caps=(False, "main")), 31.4, zcos=0.5)
    # 毛皮の縁: 顔の開きのまわりの輪 (顔はふさがない)
    rim = [(-4.9, 23.8, -4.4), (-5.4, 27.0, -5.2), (-4.8, 30.4, -5.6), (-2.4, 31.8, -5.9), (0, 32.2, -6.0), (2.4, 31.8, -5.9), (4.8, 30.4, -5.6),
           (5.4, 27.0, -5.2), (4.9, 23.8, -4.4)]
    p["helmet"] += crv(H_, "fur", rim, [1.1] * len(rim), sides=6)
    # 銅の縁の雪眼鏡 (頭巾の額に上げてある)
    p["helmet"] += crv(H_, "cord", [(-5.6, 32.6, -3.4), (-3.0, 33.4, -5.4), (3.0, 33.4, -5.4), (5.6, 32.6, -3.4)], [0.3] * 4, sides=4)
    for s_ in (-1, 1):
        p["helmet"] += crv(H_, "metal", [(s_ * 1.8, 33.6, -5.2), (s_ * 1.8, 33.6, -6.4)], [1.15, 1.15], sides=8, caps=(True, False), hint=(0, 1, 0))
        p["helmet"] += crv(H_, "glass", [(s_ * 1.8, 33.6, -6.3), (s_ * 1.8, 33.6, -6.6)], [0.85, 0.85], sides=8, caps=(False, True), hint=(0, 1, 0))
    f = tube_body(B_, "cloth", [24.0, 18.0, 13.0], [4.5, 4.6, 4.7], [-2.6, -2.7, -2.7], [2.6, 2.6, 2.7], cut=1.0)
    for k, (y0, y1) in enumerate(((25.0, 21.4), (21.6, 18.0), (18.2, 14.8), (15.0, 12.2))):                                  # 羽を詰めた段
        r = 5.6 + k * 0.2
        f += tube_body(B_, "main", [y0, y1], [r, r + 0.4], [-3.6 - k * 0.1, -3.9 - k * 0.1], [3.8, 4.0], cut=1.4, wave_last=0.3)
        f += tube_body(B_, "sec", [y1 + 0.4, y1 - 0.1], [r + 0.45, r + 0.5], [-3.95 - k * 0.1, -4.0 - k * 0.1], [4.05, 4.1], cut=1.4)
    for y in (20.0, 13.0):                                                                                                   # 命綱と銅の環
        f += tube_body(B_, "cord", [y + 0.3, y - 0.3], [6.0, 6.0], [-4.2, -4.2], [4.2, 4.2], cut=1.4)
        f += shell(B_, "metal", [rr(y - 0.7, 0.8, -4.9, -4.3, cut=0.3), rr(y + 0.7, 0.8, -4.9, -4.3, cut=0.3)], caps=("metal", "metal"))
    p["chestplate"] += f
    for s in (-1, 1):
        g = sleeve_r(s, "main", 24.0, 13.8, 3.1, 3.4, wave=0.3)
        g += sleeve_r(s, "cloth", 15.0, 12.4, 3.2, 3.1)
        if s == -1:                                                                                                          # 右肩の縄の輪 (非対称)
            for k in range(3):
                a0 = k * 0.25
                pts = [(s * 6.0 + math.cos(t) * 3.6, 22.0 + math.sin(t) * 3.6, -3.4 + a0) for t in [i / 10 * 2 * math.pi for i in range(11)]]
                g += crv(ARM[s], "cord", pts, [0.35] * 11, sides=4)
        g += tube_body(ARM[s], "fur", [25.6, 23.6], [3.8, 4.0], [-3.8, -4.0], [3.8, 4.0], cut=1.3, cx=s * 6.2, wave_last=0.3)
        p["chestplate"] += g
    for s in (-1, 1):
        g = leg_r(s, "cloth", 12.0, 5.0, 2.55, 2.5)
        g += leg_r(s, "main", 12.0, 7.0, 2.7, 3.2, wave=0.3)
        for i in range(3):
            y = 1.4 + i * 1.5
            g += tube_body(LEG[s], "fur", [y + 0.7, y - 0.7], [2.85, 2.9], [-2.85, -2.9], [2.85, 2.9], cut=1.0, cx=s * 2.15, wave_last=0.2)
        p["leggings"] += g
    for s in (-1, 1):
        g = boot_r(s, "cloth", 4.6, toe=3.6, r=3.0)
        g += tube_body(BOOT[s], "fur", [5.0, 4.0], [3.3, 3.2], [-3.3, -3.2], [3.3, 3.2], cut=1.1, cx=s * 2.15, wave_last=0.3)
        for k in range(3):                                                                                                   # 爪
            g += spk(BOOT[s], "metal", (s * 2.15 - 1.4 + k * 1.4, 0.2, -3.0), (0, -0.5, -1), 0.9, 0.3, sides=4)
        g += bx(BOOT[s], "dark", (s * 2.15, 0.15, -0.3), (5.8, 0.3, 6.8))
        p["boots"] += g
    return p


# ================================================================ 灰歩きの旅装
def ash_walking():
    p = empty()
    p["helmet"] += shell(H_, "acc", [rr(31.0, 5.2, -5.2, 5.2, cut=2.0), rr(34.0, 4.4, -4.4, 4.4, cut=1.8), rr(35.4, 3.4, -3.4, 3.4, cut=1.4)], caps=(False, "acc"))
    p["helmet"] += shell(H_, "acc", [rr(30.8, 9.4, -9.4, 9.4, cut=3.4), rr(31.4, 8.6, -8.6, 8.6, cut=3.2)], caps=("acc", "acc"))       # 広いつば
    p["helmet"] += shell(H_, "cord", [rr(31.6, 5.4, -5.4, 5.4, cut=2.1), rr(32.4, 5.2, -5.2, 5.2, cut=2.0)], caps=(False, False))
    p["helmet"] += hide_front(shell(H_, "cloth", [rr(30.6, 5.6, -5.0, 6.0, cut=2.0), rr(26.4, 5.8, -4.0, 6.2, cut=2.2, dy=[0.4, -0.4] * 4)]), 31.0, zcos=0.3)  # 灰除けの垂れ
    p["helmet"] += tube_body(H_, "sec", [27.4, 25.0], [2.4, 2.6], [-5.4, -5.6], [-3.6, -3.6], cut=0.8, caps=("sec", "sec"))   # 面当て
    for s in (-1, 1):
        p["helmet"] += crv(H_, "sec", [(s * 1.8, 25.6, -5.4), (s * 2.6, 24.0, -6.4)], [0.9, 0.8], sides=8, caps=(True, True))  # 濾し筒
        p["helmet"] += bx(H_, "dark", (s * 2.7, 23.8, -6.6), (1.0, 1.0, 0.2))
    f = tube_body(B_, "cloth", [24.0, 18.0, 13.0], [4.5, 4.6, 4.7], [-2.6, -2.7, -2.7], [2.6, 2.6, 2.7], cut=1.0)
    f += tube_body(B_, "main", [24.8, 19.0, 14.6, 13.0], [5.4, 5.4, 5.5, 5.6], [-3.4, -3.6, -3.8, -3.9], [3.6, 3.8, 4.0, 4.1], cut=1.4)
    f += crv(B_, "edge", [(0, 24.6, -3.5), (0, 12.4, -4.0)], [0.25, 0.25], sides=4)
    for y in (22.0, 19.0, 16.0):
        f += bx(B_, "edge", (0.6, y, -3.9), (0.6, 0.6, 0.3))
    f = belt(f, 13.0, mat="acc", buckle="edge", r=(6.05, 4.05))
    f = pouch(f, B_, 3.4, 11.6, -4.2, mat="acc", flap="sec")
    p["chestplate"] += f
    for s in (-1, 1):
        g = sleeve_r(s, "main", 24.0, 15.0, 3.0, 3.2)
        for i in range(3):                                                                                                   # 鋲の鉄の肩板 (外が上がる)
            g += plate(ARM[s], "sec", (s * (6.8 + i * 0.5), 25.2 - i * 1.5, 0), (5.8 - i * 0.4, 1.4, 6.0 - i * 0.4), tilt_z=-s * (10 + i * 3))
            for z in (-2.4, 2.4):
                g += bx(ARM[s], "edge", (s * (6.8 + i * 0.5), 26.0 - i * 1.5, z), (0.5, 0.4, 0.5))
        g += tube_body(ARM[s], "acc", [16.8, 12.0], [3.6, 3.3], [-3.6, -3.3], [3.6, 3.3], cut=1.2, cx=s * 6.0, wave_last=0.3)     # 熱除けの籠手
        g += crv(ARM[s], "cord", [(s * 6.0 - 3.5, 16.2, 0), (s * 6.0, 16.2, -3.6), (s * 6.0 + 3.5, 16.2, 0)], [0.2] * 3, sides=3)
        p["chestplate"] += g
    for s in (-1, 1):
        g = leg_r(s, "cloth", 12.0, 5.0, 2.55, 2.5)
        g += leg_r(s, "main", 12.0, 4.6, 2.7, 3.6, wave=0.5)                                                                 # 長外套の裾 (脚へ)
        g += plate(LEG[s], "sec", (s * 2.15, 6.0, -3.6), (3.4, 2.6, 0.8), tilt_x=10)
        p["leggings"] += g
    for s in (-1, 1):
        g = boot_r(s, "acc", 4.6, toe=3.6, r=3.0)
        g += tube_body(BOOT[s], "sec", [1.2, 0.0], [3.3, 3.4], [-4.0, -4.2], [3.3, 3.4], cut=1.0, cx=s * 2.15, caps=(False, "sec"))  # 鉄の厚底
        g += tube_body(BOOT[s], "sec", [5.0, 4.2], [3.2, 3.2], [-3.2, -3.2], [3.2, 3.2], cut=1.1, cx=s * 2.15)
        p["boots"] += g
    return p


# ================================================================ 初めの旅装
def wayfarer():
    p = empty()
    p["helmet"] += shell(H_, "sec", [rr(31.0, 5.0, -5.0, 5.0, cut=2.0), rr(33.6, 4.4, -4.4, 4.4, cut=1.8), rr(35.0, 3.2, -3.2, 3.2, cut=1.2)], caps=(False, "sec"))
    p["helmet"] += shell(H_, "sec", [rr(30.8, 8.4, -8.6, 8.4, cut=3.2, dy=[0.3, 0, -0.3, 0, 0.3, 0, -0.3, 0]), rr(31.3, 7.8, -8.0, 7.8, cut=3.0)],
                         caps=("sec", "sec"))
    p["helmet"] += shell(H_, "cloth", [rr(31.6, 5.2, -5.2, 5.2, cut=2.0), rr(32.6, 5.0, -5.0, 5.0, cut=1.9)], caps=(False, False))   # 帯
    p["helmet"] += crv(H_, "fur", [(4.8, 32.0, 1.0), (6.4, 34.6, 2.6), (7.0, 37.0, 4.6)], [0.7, 0.55, 0.15], sides=4)           # 羽根
    f = tube_body(B_, "main", [24.0, 18.0, 13.0], [4.5, 4.6, 4.7], [-2.6, -2.7, -2.75], [2.6, 2.6, 2.75], cut=1.0)
    f += tube_body(B_, "main", [12.6, 10.8], [4.9, 5.2], [-3.0, -3.2], [3.0, 3.2], cut=1.2, wave_last=0.4)
    f += tube_body(B_, "cloth", [25.4, 22.0, 18.6], [6.4, 6.6, 6.8], [-3.8, -4.0, -4.2], [4.4, 4.6, 4.8], cut=1.8, wave_last=0.5)   # 頭巾つきの短い外套
    f += crv(B_, "cloth", [(0, 25.2, 4.4), (0, 23.4, 5.4), (0, 21.4, 5.3)], [2.2, 1.9, 1.1], sides=6)                           # 背に下ろした頭巾
    f += crv(B_, "cord", [(-2.0, 24.4, -4.0), (0, 23.6, -4.2), (2.0, 24.4, -4.0)], [0.2] * 3, sides=4)
    f += bx(B_, "edge", (0, 23.7, -4.3), (0.8, 0.8, 0.3))
    f = stitches(f, B_, "cord", ((-1.0, 17.6, -2.85), (-1.0, 12.8, -2.95)), n=5)
    f = belt(f, 13.0)
    for x in (-2.6, 2.6):
        f = pouch(f, B_, x, 11.6, -3.3)
    f += tube_body(B_, "fur", [12.4, 11.0, 9.6], [0.9, 1.2, 1.0], [-1.0, -1.3, -1.1], [1.0, 1.3, 1.1], cut=0.4, cx=-5.4, caps=("fur", "fur"))  # 瓢箪
    f += tube_body(B_, "fur", [13.8, 13.0], [0.7, 0.8], [-0.8, -0.9], [0.8, 0.9], cut=0.3, cx=-5.4, caps=("fur", "fur"))
    p["chestplate"] += f
    for s in (-1, 1):
        g = sleeve_r(s, "main", 24.0, 13.0, 2.75, 2.85, wave=0.25)
        g += tube_body(ARM[s], "cloth", [25.6, 23.0], [3.8, 4.0], [-3.8, -4.0], [3.8, 4.0], cut=1.3, cx=s * 6.2, wave_last=0.4)
        g += crv(ARM[s], "sec", [(s * 6.0 - 2.9, 14.2, -1), (s * 6.0, 14.2, -3.0), (s * 6.0 + 2.9, 14.2, -1)], [0.35] * 3, sides=4)
        p["chestplate"] += g
    for s in (-1, 1):
        g = leg_r(s, "acc", 12.0, 4.6, 2.55, 2.5)
        g += plate(LEG[s], "main", (s * 2.15, 6.2, -2.65), (2.8, 2.6, 0.4), tilt_x=6)                                         # 当て布
        g = stitches(g, LEG[s], "cord", ((s * 2.15 - 1.3, 7.4, -2.9), (s * 2.15 + 1.3, 7.4, -2.9)), n=4)
        for i in range(3):
            y = 1.0 + i * 1.3
            g += tube_body(LEG[s], "main", [y + 0.55, y - 0.55], [2.62, 2.66], [-2.62, -2.66], [2.62, 2.66], cut=0.9, cx=s * 2.15)
        p["leggings"] += g
    for s in (-1, 1):
        g = boot_r(s, "sec", 4.0, toe=3.6, r=2.9)
        g += tube_body(BOOT[s], "sec", [4.8, 3.8], [3.2, 3.1], [-3.2, -3.1], [3.2, 3.1], cut=1.1, cx=s * 2.15, wave_last=0.4)   # 折り返し
        g += bx(BOOT[s], "dark", (s * 2.15, 0.15, -0.3), (5.6, 0.3, 6.8))
        p["boots"] += g
    return p


# ================================================================ 鉄張りの旅装
def ironclad():
    p = empty()
    p["helmet"] += shell(H_, "metal", [rr(31.0, 5.4, -5.4, 5.4, cut=2.2), rr(34.4, 4.2, -4.2, 4.2, cut=1.8), rr(36.0, 1.6, -1.6, 1.6, cut=0.6)], caps=(False, "metal"))
    p["helmet"] += shell(H_, "edge", [rr(30.4, 8.6, -8.6, 8.6, cut=3.0), rr(31.2, 8.0, -8.0, 8.0, cut=2.8)], caps=("edge", "edge"))
    for k in range(8):                                                                                                       # 鉢の鋲
        a = k / 8 * 2 * math.pi
        p["helmet"] += bx(H_, "trim", (math.sin(a) * 5.0, 32.4, -math.cos(a) * 5.0), (0.5, 0.5, 0.5))
    p["helmet"] += spk(H_, "trim", (0, 36.0, 0), (0, 1, 0), 1.0, 0.5, sides=6)
    p["helmet"] += hide_front(shell(H_, "main", [rr(31.0, 5.0, -4.6, 5.0, cut=1.8), rr(26.0, 5.6, -3.6, 6.0, cut=2.0), rr(24.8, 5.6, -3.2, 5.8, cut=2.0,
                                                                                                                   dy=[0.3, -0.3] * 4)]), 31.5, zcos=0.4)
    f = tube_body(B_, "main", [24.2, 18.0, 13.0], [4.8, 4.9, 5.1], [-2.9, -3.0, -3.1], [2.9, 3.0, 3.1], cut=1.0)               # 鎖帷子
    for sg in (-1, 1):                                                                                                       # 陣羽織の前後
        zf, zb = (-3.3, -3.0) if sg < 0 else (3.0, 3.3)
        f += tube_body(B_, "cloth", [24.0, 17.0, 13.0], [3.4, 3.5, 3.6], [zf, zf - 0.1 * (sg < 0), zf - 0.2 * (sg < 0)], [zb, zb + 0.1 * (sg > 0), zb + 0.2 * (sg > 0)],
                       cut=0.5)
        f += crv(B_, "trim", [(-3.4, 12.4, sg * 3.55), (0, 12.2, sg * 3.7), (3.4, 12.4, sg * 3.55)], [0.25] * 3, sides=4)
        f += crv(B_, "trim", [(-3.4, 23.8, sg * 3.3), (-3.5, 12.4, sg * 3.5)], [0.2, 0.2], sides=4)
        f += crv(B_, "trim", [(3.4, 23.8, sg * 3.3), (3.5, 12.4, sg * 3.5)], [0.2, 0.2], sides=4)
    f += spk(B_, "trim", (0, 18.8, -3.4), (0, 0, -1), 0.6, 1.6, sides=4)                                                    # 真鍮の鯨の紋
    f += crv(B_, "trim", [(-1.4, 18.8, -3.9), (0, 18.4, -4.0), (1.4, 18.8, -3.9), (2.0, 19.6, -3.8)], [0.18] * 4, sides=3)
    f += shell(B_, "metal", [rr(26.0, 3.8, -3.4, 3.6, cut=1.3), rr(24.0, 4.4, -3.6, 3.6, cut=1.5)], caps=("metal", False))     # 喉当て
    f = belt(f, 13.2, mat="sec", buckle="trim", r=(5.15, 3.5))
    f = pouch(f, B_, -3.4, 11.8, -3.6)
    p["chestplate"] += f
    for s in (-1, 1):
        g = sleeve_r(s, "main", 24.0, 16.0, 2.75, 2.85)
        n = 4 if s == -1 else 3                                                                                              # 右は大袖 (非対称)
        for i in range(n):
            y = 25.6 - i * 1.6
            g += shell(ARM[s], "metal" if i % 2 == 0 else "edge",
                       [rr(y + 0.7, 3.8 - i * 0.1, -3.8 + i * 0.05, 3.8 - i * 0.05, cut=1.5, cx=s * (6.6 + i * 0.35)),
                        rr(y - 0.8, 4.1 - i * 0.1, -4.1 + i * 0.05, 4.1 - i * 0.05, cut=1.6, cx=s * (7.0 + i * 0.35))], caps=("metal", False))
            g += bx(ARM[s], "trim", (s * (7.0 + i * 0.35), y + 0.3, -3.6), (0.4, 0.4, 0.3))
        g += shell(ARM[s], "metal", [rr(19.2, 2.9, -3.3, 2.2, cut=1.2, cx=s * 6.0), rr(17.0, 2.9, -3.5, 2.2, cut=1.2, cx=s * 6.0)], caps=("metal", "metal"))
        g += spk(ARM[s], "edge", (s * 6.0, 18.1, -3.6), (0, 0, -1), 0.5, 0.45, sides=6)
        g += sleeve_r(s, "sec", 16.2, 12.6, 2.95, 3.0)
        p["chestplate"] += g
    for s in (-1, 1):
        g = leg_r(s, "cloth", 12.0, 5.0, 2.55, 2.45)
        g += leg_r(s, "main", 12.0, 7.0, 2.7, 3.1, wave=0.35)                                                              # 鎖の裾 (脚へ)
        g += shell(LEG[s], "metal", [rr(7.4, 2.0, -3.4, -1.8, cut=0.8, cx=s * 2.15), rr(4.4, 1.9, -3.2, -1.8, cut=0.8, cx=s * 2.15)], caps=("metal", "metal"))
        g += spk(LEG[s], "edge", (s * 2.15, 6.0, -3.4), (0, 0, -1), 0.5, 0.5, sides=6)
        p["leggings"] += g
    for s in (-1, 1):
        g = boot_r(s, "sec", 4.4, toe=3.6)
        g += tube_body(BOOT[s], "metal", [5.0, 4.1], [3.0, 3.0], [-3.0, -3.0], [3.0, 3.0], cut=1.0, cx=s * 2.15)
        g += shell(BOOT[s], "metal", [rr(2.6, 2.6, -3.9, -1.6, cut=1.0, cx=s * 2.15), rr(0.4, 2.8, -3.9, -1.6, cut=1.0, cx=s * 2.15)], caps=("metal", "metal"))
        for y in (1.8, 2.8, 3.6):
            g += crv(BOOT[s], "cord", [(s * 2.15 - 1.0, y, -3.0), (s * 2.15 + 1.0, y + 0.5, -3.0)], [0.14, 0.14], sides=3)
        g += bx(BOOT[s], "dark", (s * 2.15, 0.15, -0.3), (5.4, 0.3, 7.0))
        p["boots"] += g
    return p


# ================================================================ 空石の胸当て (胴だけ)
def sky_stone():
    p = empty()
    f = tube_body(B_, "cloth", [24.0, 18.0, 13.0], [4.6, 4.7, 4.8], [-2.7, -2.8, -2.85], [2.7, 2.7, 2.85], cut=1.0)
    for k in range(6):                                                                                                       # 空石の鱗 (段ごとに半分ずらす)
        y = 23.0 - k * 1.9
        for j in range(6):
            x = -4.0 + j * 1.6 + (0.8 if k % 2 else 0)
            if abs(x) > 4.2:
                continue
            f += spk(B_, "glass", (x, y + 0.6, -2.9), (0, -1, -0.35), 2.0, 0.85, sides=5)
    for k in range(4):
        y = 22.6 - k * 2.6
        for j in range(4):
            x = -3.4 + j * 2.3
            f += spk(B_, "glass", (x, y + 0.6, 2.9), (0, -1, 0.35), 2.0, 0.85, sides=5)
    f += tube_body(B_, "fur", [25.6, 23.6, 22.4], [6.2, 6.4, 6.0], [-3.8, -4.0, -3.6], [4.2, 4.4, 4.0], cut=1.8, wave_last=0.5)  # 大きな毛皮の肩
    for k in range(7):                                                                                                       # 腰の銅の鎖
        a = -1.2 + k * 0.4
        f += shell(B_, "acc", [rr(12.4 + 0.3 * math.cos(a * 2), 0.45, math.sin(a) * 0 - 3.6, -2.8, cut=0.2, cx=math.sin(a) * 5.0),
                               rr(13.2 + 0.3 * math.cos(a * 2), 0.45, -3.6, -2.8, cut=0.2, cx=math.sin(a) * 5.0)], caps=("acc", "acc"))
    f = belt(f, 13.6, mat="sec", buckle="acc")
    p["chestplate"] += f
    for s in (-1, 1):
        g = sleeve_r(s, "cloth", 24.0, 14.0, 2.75, 2.85)
        g += tube_body(ARM[s], "fur", [26.0, 23.4], [4.0, 4.2], [-4.0, -4.2], [4.0, 4.2], cut=1.4, cx=s * 6.4, wave_last=0.4)
        p["chestplate"] += g
    return {k: v for k, v in p.items() if v}


SETS = {"whale_road": whale_road, "tide_wader": tide_wader, "peak_crossing": peak_crossing, "ash_walking": ash_walking,
        "wayfarer": wayfarer, "ironclad": ironclad, "sky_stone": sky_stone}
