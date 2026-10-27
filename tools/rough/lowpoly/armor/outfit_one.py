"""市の武具屋の防具 3 品 x 3 案の形 (2026-10-27 の声「ブロック以外はすべて mod で」。バニラの革・鉄・ダイヤの防具の代わり)。
  初めの旅装 (start_*)  = 革の防具の代わり。島で初めに着る旅の服。いちばん軽い
  鉄張りの旅装 (iron_*) = 鉄の防具の代わり。一章の支度 (鯨の背の鎖を上る前) に武具屋で買う
  空石の胸当て (sky_*)  = ダイヤの胸当ての代わり。三章の支度「空の石」。胸当てだけ
作りは旅装 (travel_one) と同じ (材質: main sec edge acc cloth cord fur metal glass gem trim dark。塗りは outfit_paint)。
一品一案 (色違いにしない): 案ごとに形が違う。
"""
from __future__ import annotations

import math

from akit import *  # noqa: F401,F403
from akit import V, bx, ext, spk, crv, rr, shell, rot_x, rot_y, rot_z, ARM, LEG, BOOT, hide_front
from travel_one import sleeve, wrap_rings

H_, B_ = "armorHead", "armorBody"


def empty():
    return {"helmet": [], "chestplate": [], "leggings": [], "boots": []}


def torso(f, mat="cloth"):
    f += bx(B_, mat, (0, 18.0, 0), (9.0, 12.0, 4.9), taper=(0.98, 0.98), skip=("u", "b"))
    return f


def legs_plain(p, mat="cloth"):
    for s in (-1, 1):
        p["leggings"] += bx(LEG[s], mat, (s * 2.15, 8.6, 0), (4.6, 6.6, 4.9), skip=("b", "u"))


def shoes(p, mat="sec", top=3.6, toe=None, sole="dark"):
    for s in (-1, 1):
        cx = s * 2.15
        g = bx(BOOT[s], mat, (cx, top / 2, 0), (4.8, top, 5.0), skip=("b",))
        if toe:
            g += bx(BOOT[s], toe, (cx, 1.0, -3.1), (4.4, 2.0, 1.6), taper=(0.85, 0.8), skip=("b",))
        g += bx(BOOT[s], sole, (cx, 0.2, 0), (5.0, 0.4, 5.6), skip=("u",))
        p["boots"] += g


# ================================================================== 初めの旅装
def start_a():
    """A 麻の旅着: 生成りの麻の上着 (前で合わせて帯で締める)、布の頭巾、脛に巻いた布、草鞋。いちばん素朴な旅人。"""
    p = empty()
    rings = [rr(24.4, 4.9, -3.4, 5.2, cut=1.4), rr(28.8, 5.2, -4.4, 5.3, cut=1.6), rr(32.2, 4.6, -4.2, 4.9, cut=1.8), rr(33.6, 2.4, -1.8, 3.2, cut=1.2)]
    p["helmet"] += hide_front(shell(H_, "cloth", rings, caps=(False, "cloth")), 31.0, zcos=0.55)
    p["helmet"] += bx(H_, "cord", (0, 31.4, -4.7), (9.2, 0.6, 0.8), skip=("b",))
    f = torso([], "main")
    f += bx(B_, "main", (-1.6, 18.0, -2.7), (5.2, 11.6, 0.6), rot=rot_z(-4))           # 前の合わせ (左前)
    f += bx(B_, "sec", (0, 13.6, 0), (9.6, 1.6, 5.4), skip=("u",))                     # 帯
    f += bx(B_, "cord", (3.2, 12.4, -2.9), (0.8, 2.4, 0.4))                             # 帯の結び目の垂れ
    f += bx(B_, "acc", (-3.0, 12.2, -2.9), (2.0, 2.2, 1.2), taper=(0.85, 0.85))          # 小さな巾着
    p["chestplate"] += f
    for s in (-1, 1):
        g = sleeve(ARM[s], s, "main")
        g += bx(ARM[s], "cord", (s * 6.0, 14.4, 0), (5.0, 0.5, 5.1), skip=("u", "b"))
        p["chestplate"] += g
    legs_plain(p, "main")
    for s in (-1, 1):
        p["leggings"] += wrap_rings(LEG[s], s * 2.15, 1.0, 6.0, 4, mat="cloth")
    for s in (-1, 1):
        cx = s * 2.15
        g = bx(BOOT[s], "fur", (cx, 0.5, -0.2), (4.8, 1.0, 5.6), skip=())                  # 草鞋の底 (藁)
        for z in (-1.8, 0.6):
            g += bx(BOOT[s], "cord", (cx, 1.4, z), (5.0, 0.4, 0.5))
        g += bx(BOOT[s], "cloth", (cx, 2.4, 0), (4.6, 2.4, 4.8), skip=("b",))
        p["boots"] += g
    return p


def start_b():
    """B 革の胴着: 布の肌着の上に、紐で締める革の胴着。革の帽子 (耳当て)、厚手の脚衣、紐の革靴。町の若い職人の旅支度。"""
    p = empty()
    p["helmet"] += bx(H_, "sec", (0, 32.0, 0), (9.2, 2.6, 9.2), taper=(0.86, 0.86), skip=("b",))
    p["helmet"] += bx(H_, "sec", (0, 30.2, 0), (9.6, 1.2, 9.6), skip=())
    for s in (-1, 1):
        p["helmet"] += bx(H_, "sec", (s * 4.7, 27.6, 0.6), (0.8, 4.0, 4.0), taper=(1.0, 0.8))   # 耳当て
    f = torso([], "cloth")
    f += bx(B_, "sec", (0, 17.4, 0), (9.6, 9.4, 5.5), taper=(1.0, 0.98), skip=("u", "b"))
    for y in (20.4, 18.4, 16.4, 14.4):
        f += crv(B_, "cord", [(-0.9, y, -2.85), (0.9, y - 0.9, -2.85)], [0.16, 0.16], sides=3)
        f += crv(B_, "cord", [(0.9, y, -2.85), (-0.9, y - 0.9, -2.85)], [0.16, 0.16], sides=3)
    f += bx(B_, "acc", (0, 12.8, 0), (9.8, 1.0, 5.6), skip=("u",))
    f += bx(B_, "metal", (0, 12.8, -2.9), (1.4, 1.2, 0.5))
    p["chestplate"] += f
    for s in (-1, 1):
        g = sleeve(ARM[s], s, "cloth")
        g += bx(ARM[s], "sec", (s * 6.0, 14.6, 0), (5.2, 3.0, 5.3), taper=(0.95, 0.95))
        g += bx(ARM[s], "sec", (s * 6.4, 23.8, 0), (5.4, 1.4, 5.6), taper=(0.9, 0.95))
        p["chestplate"] += g
    legs_plain(p, "main")
    for s in (-1, 1):
        p["leggings"] += bx(LEG[s], "sec", (s * 2.15, 5.4, -2.7), (3.4, 2.2, 0.8), taper=(0.85, 0.9))
    shoes(p, "sec", top=4.2)
    for s in (-1, 1):
        for y in (2.0, 3.2):
            p["boots"] += bx(BOOT[s], "cord", (s * 2.15, y, -2.6), (2.0, 0.3, 0.3))
    return p


def start_c():
    """C 笠と半合羽: 藁の三度笠と、肩を覆う短い合羽 (前を紐で留める)、裾を絞った野袴、足袋と脚絆。街道を歩く旅人の形。"""
    p = empty()
    hat = [rr(31.6, 1.2, -1.2, 1.2), rr(32.6, 8.4, -8.4, 8.4), rr(33.4, 6.4, -6.4, 6.4), rr(35.6, 1.2, -1.2, 1.2)]
    p["helmet"] += shell(H_, "fur", hat, caps=("fur", "fur"))
    p["helmet"] += crv(H_, "cord", [(-4.4, 32.0, 0), (-3.6, 26.2, -2.2), (0, 25.0, -3.2), (3.6, 26.2, -2.2), (4.4, 32.0, 0)], [0.16] * 5, sides=3)
    f = torso([], "cloth")
    f += bx(B_, "main", (0, 22.0, 0.2), (11.0, 4.6, 6.2), taper=(0.84, 1.05))             # 合羽の肩
    f += bx(B_, "main", (0, 18.2, 3.0), (10.4, 4.0, 0.9), taper=(1.0, 1.04), skip=("-z",))
    for s in (-1, 1):
        f += bx(B_, "main", (s * 3.9, 19.0, -2.9), (2.8, 4.6, 0.9), skip=("+z",))
    f += crv(B_, "cord", [(-1.2, 22.4, -3.4), (0, 21.6, -3.5), (1.2, 22.4, -3.4)], [0.2] * 3, sides=3)
    f += bx(B_, "sec", (0, 13.4, 0), (9.6, 1.4, 5.4), skip=("u",))
    f += bx(B_, "acc", (3.2, 12.6, -2.9), (1.4, 2.6, 1.4), taper=(0.7, 0.9))              # 腰の竹筒
    p["chestplate"] += f
    for s in (-1, 1):
        p["chestplate"] += sleeve(ARM[s], s, "cloth", top=21.0)
    for s in (-1, 1):
        cx = s * 2.15
        p["leggings"] += bx(LEG[s], "main", (cx, 8.0, 0), (4.9, 7.6, 5.2), taper=(0.94, 1.0), skip=("b", "u"))
        p["leggings"] += bx(LEG[s], "cord", (cx, 4.6, 0), (4.8, 0.5, 5.0), skip=("u", "b"))
        p["leggings"] += wrap_rings(LEG[s], cx, 1.2, 4.4, 3, mat="sec")
    for s in (-1, 1):
        cx = s * 2.15
        g = bx(BOOT[s], "cloth", (cx, 1.4, 0), (4.6, 2.8, 4.9), skip=("b",))
        g += bx(BOOT[s], "fur", (cx, 0.3, -0.2), (4.8, 0.6, 5.6))
        g += bx(BOOT[s], "cord", (cx, 0.9, -2.0), (0.4, 1.2, 0.5))
        p["boots"] += g
    return p


# ================================================================== 鉄張りの旅装
def iron_a():
    """A 鎖帷子: 膝までの鎖帷子 (細かい輪の目)、上に革の帯と肩の革。鉄の鉢の兜に鎖の垂れ。鉄の膝当てと爪先を鉄で包んだ靴。"""
    p = empty()
    p["helmet"] += bx(H_, "metal", (0, 32.0, 0), (9.6, 3.6, 9.6), taper=(0.7, 0.7), skip=("b",))     # 鉢
    p["helmet"] += bx(H_, "edge", (0, 30.2, 0), (9.8, 0.8, 9.8))
    p["helmet"] += bx(H_, "trim", (0, 34.0, 0), (1.2, 0.8, 1.2))
    for s in (-1, 1):
        p["helmet"] += bx(H_, "main", (s * 4.8, 27.0, 0.8), (0.7, 5.2, 7.4))                         # 鎖の垂れ (頬と首)
    p["helmet"] += bx(H_, "main", (0, 27.0, 4.6), (9.4, 5.2, 0.7))
    f = torso([], "main")
    f += bx(B_, "main", (0, 10.6, 0), (9.8, 3.2, 5.6), taper=(1.06, 1.0), skip=("u",))               # 腰までの裾
    f += bx(B_, "sec", (0, 13.0, 0), (9.8, 1.2, 5.6), skip=("u",))
    f += bx(B_, "edge", (0, 13.0, -2.9), (1.4, 1.2, 0.5))
    f += crv(B_, "sec", [(4.6, 23.6, -2.7), (0.2, 18.6, -2.9), (-4.4, 13.6, -2.7)], [0.5] * 3, sides=4)
    p["chestplate"] += f
    for s in (-1, 1):
        g = sleeve(ARM[s], s, "main", bot=17.0)
        g += bx(ARM[s], "sec", (s * 6.0, 14.6, 0), (5.2, 3.4, 5.3), taper=(0.95, 0.95))
        g += bx(ARM[s], "metal", (s * 6.6, 24.2, 0), (5.8, 1.6, 5.8), taper=(0.85, 0.9))
        p["chestplate"] += g
    legs_plain(p, "cloth")
    for s in (-1, 1):
        cx = s * 2.15
        p["leggings"] += bx(LEG[s], "main", (cx, 9.6, 0), (4.8, 4.6, 5.1), skip=("u",))
        p["leggings"] += bx(LEG[s], "metal", (cx, 5.6, -2.8), (3.6, 2.6, 1.2), taper=(0.8, 0.9))
        p["leggings"] += bx(LEG[s], "edge", (cx, 5.6, -3.5), (0.7, 0.7, 0.4))
    shoes(p, "sec", top=4.0, toe="metal")
    return p


def iron_b():
    """B 都の衛兵の板鎧: 鉄の胸板に真鍮の都の紋 (鐘と四つの星)、肩は重ねた鉄の板、腰に鉄の草摺。鼻当ての付いた丸兜。脛当てと鉄の靴。"""
    p = empty()
    p["helmet"] += bx(H_, "metal", (0, 31.4, 0), (9.6, 4.6, 9.6), taper=(0.78, 0.78), skip=("b",))
    p["helmet"] += bx(H_, "edge", (0, 29.2, 0), (9.9, 0.8, 9.9))
    p["helmet"] += bx(H_, "metal", (0, 27.4, -5.0), (1.0, 3.2, 0.6), taper=(0.7, 1.0))               # 鼻当て
    p["helmet"] += bx(H_, "trim", (0, 33.6, 0), (0.8, 1.0, 7.2))                                    # 鉢の筋
    f = torso([], "cloth")
    f += bx(B_, "metal", (0, 19.2, -0.4), (9.4, 9.2, 5.6), taper=(0.96, 1.0), skip=("u", "b"))
    f += bx(B_, "edge", (0, 23.8, -0.4), (9.6, 0.8, 5.8))
    f += bx(B_, "trim", (0, 19.6, -3.3), (2.2, 2.4, 0.5), taper=(0.55, 1.0))                        # 都の鐘
    f += bx(B_, "trim", (0, 21.0, -3.3), (0.6, 0.6, 0.5))
    for dx, dy in ((-2.0, 21.0), (2.0, 21.0), (-2.0, 18.0), (2.0, 18.0)):
        f += bx(B_, "trim", (dx, dy, -3.3), (0.6, 0.6, 0.45), rot=rot_z(45))
    for i, y in enumerate((13.6, 11.6)):
        f += bx(B_, "metal", (0, y, -0.2), (10.0 + i * 0.6, 2.0, 5.8 + i * 0.4), taper=(1.0, 1.04), skip=("u",))
    p["chestplate"] += f
    for s in (-1, 1):
        g = sleeve(ARM[s], s, "cloth")
        for k, y in enumerate((24.4, 22.6, 20.8)):
            g += bx(ARM[s], "metal", (s * (6.4 + k * 0.2), y, 0), (5.8 - k * 0.3, 1.8, 5.8 - k * 0.3), rot=rot_z(-s * 10), taper=(0.9, 0.95))
        g += bx(ARM[s], "metal", (s * 6.0, 14.6, 0), (5.2, 3.2, 5.3), taper=(0.95, 0.95))
        p["chestplate"] += g
    legs_plain(p, "cloth")
    for s in (-1, 1):
        p["leggings"] += bx(LEG[s], "metal", (s * 2.15, 6.2, -1.6), (4.8, 6.8, 2.4), taper=(0.9, 1.0))
    shoes(p, "metal", top=4.2, sole="dark")
    for s in (-1, 1):
        p["boots"] += bx(BOOT[s], "edge", (s * 2.15, 4.0, 0), (5.0, 0.6, 5.2), skip=("b",))
    return p


def iron_c():
    """C 鉄の小札鎧: 小さな鉄の札を紐で綴った胴 (横の段が 5 つ)、袖も札の板。鉄の鉢に錣 (しころ) の段の兜。脛当てと草鞋。"""
    p = empty()
    p["helmet"] += bx(H_, "metal", (0, 32.2, 0), (9.4, 3.4, 9.4), taper=(0.72, 0.72), skip=("b",))
    for k, y in enumerate((29.8, 28.4, 27.0)):                                                     # 錣
        p["helmet"] += bx(H_, "metal", (0, y, 1.2 + k * 0.3), (10.0 + k * 0.6, 1.4, 8.4 + k * 0.6), taper=(1.0, 1.02), skip=())
        p["helmet"] += bx(H_, "cord", (0, y - 0.6, 1.2 + k * 0.3), (10.2 + k * 0.6, 0.3, 8.6 + k * 0.6), skip=("u", "b"))
    p["helmet"] += bx(H_, "trim", (0, 32.6, -4.4), (2.6, 1.2, 0.6), taper=(1.4, 1.0))              # 前立て (小さな鐘)
    f = torso([], "cloth")
    for k in range(5):
        y = 22.6 - k * 2.2
        f += bx(B_, "metal", (0, y, 0), (9.6 + k * 0.1, 2.0, 5.6), skip=("u", "b"))
        f += bx(B_, "cord", (0, y - 1.0, 0), (9.7 + k * 0.1, 0.3, 5.7), skip=("u", "b"))
        for x in (-3.6, -1.2, 1.2, 3.6):
            f += bx(B_, "dark", (x, y, -2.85), (0.2, 1.8, 0.2))
    p["chestplate"] += f
    for s in (-1, 1):
        g = sleeve(ARM[s], s, "cloth")
        for k, y in enumerate((24.2, 22.4)):
            g += bx(ARM[s], "metal", (s * 6.6, y, 0), (6.0, 1.8, 6.0), taper=(0.92, 0.96))
            g += bx(ARM[s], "cord", (s * 6.6, y - 0.9, 0), (6.1, 0.3, 6.1), skip=("u", "b"))
        g += bx(ARM[s], "sec", (s * 6.0, 14.6, 0), (5.2, 3.2, 5.3))
        p["chestplate"] += g
    for s in (-1, 1):
        cx = s * 2.15
        p["leggings"] += bx(LEG[s], "cloth", (cx, 8.0, 0), (4.9, 7.6, 5.2), taper=(0.94, 1.0), skip=("b", "u"))
        p["leggings"] += bx(LEG[s], "metal", (cx, 4.2, -1.8), (4.4, 5.4, 1.6), taper=(0.9, 1.0))
        for y in (2.2, 4.0, 5.8):
            p["leggings"] += bx(LEG[s], "dark", (cx, y, -2.7), (4.0, 0.2, 0.2))
    for s in (-1, 1):
        cx = s * 2.15
        g = bx(BOOT[s], "cloth", (cx, 1.4, 0), (4.6, 2.8, 4.9), skip=("b",))
        g += bx(BOOT[s], "fur", (cx, 0.3, -0.2), (4.8, 0.6, 5.6))
        p["boots"] += g
    return p


# ================================================================== 空石の胸当て (胸当てだけ)
def sky_a():
    """A 空石を嵌めた革の胸当て: 厚い革の胸当てに、淡い空色の石の板を六枚、銅の縁で嵌める。肩は革の帯。"""
    p = empty()
    f = torso([], "cloth")
    f += bx(B_, "sec", (0, 18.6, -0.2), (9.6, 10.6, 5.6), skip=("u", "b"))
    for x, y in ((-2.3, 21.4), (2.3, 21.4), (-2.3, 18.4), (2.3, 18.4), (-2.3, 15.4), (2.3, 15.4)):
        f += bx(B_, "acc", (x, y, -3.05), (3.8, 2.6, 0.5))
        f += bx(B_, "glass", (x, y, -3.35), (3.2, 2.0, 0.4), taper=(0.9, 0.9))
    f += bx(B_, "sec", (0, 13.0, 0), (9.8, 1.2, 5.8), skip=("u",))
    p["chestplate"] += f
    for s in (-1, 1):
        g = sleeve(ARM[s], s, "cloth")
        g += bx(ARM[s], "sec", (s * 6.4, 24.0, 0), (5.6, 1.6, 5.8), taper=(0.9, 0.95))
        p["chestplate"] += g
    return p


def sky_b():
    """B 空石の一枚胸甲: 胸を覆う一枚の空石 (面を削った結晶の板)、銅の縁と蝶番の肩当て。胸の真ん中に雷除けの銅の針。"""
    p = empty()
    f = torso([], "cloth")
    f += bx(B_, "glass", (0, 19.6, -0.6), (9.0, 8.6, 5.4), taper=(0.92, 1.0), skip=("u", "b"))
    f += bx(B_, "glass", (0, 19.6, -3.2), (6.4, 6.2, 0.8), taper=(0.7, 0.7))                      # 削った面 (前に出る稜)
    f += bx(B_, "acc", (0, 23.8, -0.6), (9.4, 0.9, 5.8))
    f += bx(B_, "acc", (0, 15.2, -0.6), (9.4, 0.9, 5.8))
    f += bx(B_, "metal", (0, 19.6, -3.8), (0.5, 4.4, 0.4))
    f += bx(B_, "sec", (0, 13.0, 0), (9.8, 1.4, 5.8), skip=("u",))
    p["chestplate"] += f
    for s in (-1, 1):
        g = sleeve(ARM[s], s, "cloth")
        g += bx(ARM[s], "acc", (s * 6.6, 24.2, 0), (5.8, 1.8, 6.0), taper=(0.85, 0.9))
        g += bx(ARM[s], "glass", (s * 7.6, 22.6, 0), (2.4, 2.4, 4.6), rot=rot_z(-s * 24), taper=(0.75, 0.85))
        p["chestplate"] += g
    return p


def sky_c():
    """C 空石の鱗の胴: 空石を小さな鱗に割って、布の胴に段に縫い付ける (雷が鱗の上を流れる)。肩に毛皮、腰に銅の鎖。"""
    p = empty()
    f = torso([], "cloth")
    for k in range(5):
        y = 22.6 - k * 2.1
        for j in range(5):
            x = -3.6 + j * 1.8 + (0.9 if k % 2 else 0)
            if abs(x) > 4.0:
                continue
            f += bx(B_, "glass", (x, y, -2.95), (1.9, 2.2, 0.5), taper=(0.6, 1.0))
    f += bx(B_, "fur", (0, 23.6, 0.4), (10.4, 1.8, 6.0), taper=(0.92, 0.96))
    f += crv(B_, "acc", [(-4.6, 13.4, -2.9), (0, 12.6, -3.1), (4.6, 13.4, -2.9)], [0.3] * 3, sides=4)
    f += bx(B_, "sec", (0, 12.6, 0), (9.8, 1.0, 5.6), skip=("u",))
    p["chestplate"] += f
    for s in (-1, 1):
        g = sleeve(ARM[s], s, "cloth")
        g += bx(ARM[s], "fur", (s * 6.4, 24.0, 0), (5.6, 1.8, 5.8), taper=(0.9, 0.95))
        p["chestplate"] += g
    return p


SETS = {"start_a": start_a, "start_b": start_b, "start_c": start_c,
        "iron_a": iron_a, "iron_b": iron_b, "iron_c": iron_c,
        "sky_a": sky_a, "sky_b": sky_b, "sky_c": sky_c}
JP = {"start": "初めの旅装", "iron": "鉄張りの旅装", "sky": "空石の胸当て"}
