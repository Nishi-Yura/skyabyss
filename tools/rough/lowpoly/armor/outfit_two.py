"""初めの旅装・鉄張りの旅装の作り直し 3 案ずつ (2026-10-28 のクオリティ点検。旅装 (travel_one) の作り込みに届いていなかった)。
作りは outfit_one と同じ部品 (akit)。一品一案 (色違いにしない)。どの案も、重ね着・帯・留め具・縫い目・小物まで描く。
  初めの旅装 (start2_*): 島で初めに着る旅の服。いちばん軽い (金物は留め具だけ)
  鉄張りの旅装 (iron2_*): 一章の支度で買う鉄の防具。鎖と板を重ねる
"""
from __future__ import annotations

from akit import *  # noqa: F401,F403
from akit import V, bx, spk, crv, feather, rot_x, rot_z, ARM, LEG, BOOT
from travel_one import sleeve, wrap_rings

H_, B_ = "armorHead", "armorBody"


def empty():
    return {"helmet": [], "chestplate": [], "leggings": [], "boots": []}


def belt(f, y=13.0, mat="acc", buckle="metal", pouches=((-3.2, "sec"), (3.4, "sec"))):
    f += bx(B_, mat, (0, y, 0), (9.9, 1.2, 5.7), skip=("u",))
    f += bx(B_, buckle, (0, y, -2.95), (1.6, 1.4, 0.5))
    f += bx(B_, "dark", (0, y, -3.15), (0.6, 0.6, 0.2))
    for x, m in pouches:
        f += bx(B_, m, (x, y - 1.6, -2.9), (2.2, 2.4, 1.2), taper=(0.9, 1.0))
        f += bx(B_, "acc", (x, y - 0.6, -3.55), (2.3, 0.7, 0.3))
    return f


def laced_boots(p, mat="sec", cuff="acc", top=4.4, toe=None):
    for s in (-1, 1):
        cx = s * 2.15
        g = bx(BOOT[s], mat, (cx, top / 2, 0), (4.8, top, 5.0), skip=("b",))
        g += bx(BOOT[s], cuff, (cx, top + 0.3, 0), (5.2, 0.9, 5.4), skip=("b",))
        g += bx(BOOT[s], "dark", (cx, 0.2, 0), (5.0, 0.4, 5.8), skip=("u",))
        if toe:
            g += bx(BOOT[s], toe, (cx, 1.0, -3.1), (4.4, 2.0, 1.4), taper=(0.85, 0.8), skip=("b",))
        for y in (1.6, 2.6, 3.6):
            g += crv(BOOT[s], "cord", [(cx - 1.0, y, -2.6), (cx + 1.0, y + 0.5, -2.6)], [0.14, 0.14], sides=3)
            g += crv(BOOT[s], "cord", [(cx + 1.0, y, -2.6), (cx - 1.0, y + 0.5, -2.6)], [0.14, 0.14], sides=3)
        p["boots"] += g


# ================================================================== 初めの旅装
def start2_a():
    """A 旅の外套: 麻の上衣に、肩を覆う短い頭巾つきの外套 (藍)。腰に帯と袋二つと瓢箪の水筒、つば広の帽子 (帯と羽根)。
    膝に当て布の脚衣と脛の巻き布、折り返しの革靴。島を出る旅人の、いちばん初めの支度"""
    p = empty()
    # つば広の帽子
    p["helmet"] += bx(H_, "sec", (0, 33.0, 0), (8.6, 2.8, 8.6), taper=(0.82, 0.82), skip=("b",))
    p["helmet"] += bx(H_, "sec", (0, 31.4, 0), (13.0, 0.5, 13.0), skip=())
    p["helmet"] += bx(H_, "cloth", (0, 32.0, 0), (8.9, 0.8, 8.9))
    p["helmet"] += feather(H_, "edge", (3.6, 32.2, 1.4), (5.4, 35.6, 3.0), 1.0, th=0.3, n=(1, 0, 0), big=False)
    # 胴: 麻の上衣・革の前合わせ・短い外套
    f = bx(B_, "main", (0, 18.0, 0), (9.2, 12.2, 5.0), taper=(0.98, 0.98), skip=("u", "b"))
    f += bx(B_, "sec", (-1.6, 17.0, -2.6), (5.0, 8.4, 0.5), taper=(1.0, 0.94))
    for y in (20.4, 18.2, 16.0):
        f += bx(B_, "metal", (0.6, y, -2.95), (0.7, 0.7, 0.3))
    f += bx(B_, "cloth", (0, 22.4, 0), (11.2, 4.4, 6.6), taper=(0.86, 1.06), skip=("b",))                 # 肩の外套
    f += bx(B_, "cloth", (0, 24.6, 2.2), (8.4, 2.2, 2.4), taper=(0.9, 0.9))                               # 下ろした頭巾
    f += bx(B_, "edge", (0, 20.2, 0), (11.6, 0.4, 7.0), skip=("u", "b"))                                  # 外套の縁
    f += crv(B_, "cord", [(-1.8, 24.0, -3.2), (0, 23.0, -3.6), (1.8, 24.0, -3.2)], [0.18] * 3, sides=3)
    f = belt(f, pouches=((-3.4, "sec"),))
    # 腰の瓢箪
    f += bx(B_, "fur", (3.6, 11.0, -2.9), (1.8, 2.0, 1.8), taper=(0.7, 1.0))
    f += bx(B_, "fur", (3.6, 9.4, -2.9), (2.4, 1.8, 2.2))
    f += crv(B_, "cord", [(3.6, 12.6, -3.0), (3.6, 12.0, -3.0)], [0.12, 0.12], sides=3)
    p["chestplate"] += f
    for s in (-1, 1):
        g = sleeve(ARM[s], s, "main")
        g += bx(ARM[s], "main", (s * 6.0, 15.4, 0), (5.3, 1.6, 5.4))                                     # 折り返した袖
        g += bx(ARM[s], "sec", (s * 6.0, 13.6, 0), (5.0, 2.2, 5.1), taper=(0.96, 0.96))                  # 革の籠手
        g += crv(ARM[s], "cord", [(s * 6.0 - 2.6, 13.6, -2.6), (s * 6.0 + 2.6, 13.6, -2.6)], [0.14] * 2, sides=3)
        p["chestplate"] += g
    for s in (-1, 1):
        lb = LEG[s]
        cx = s * 2.15
        g = bx(lb, "cloth", (cx, 8.6, 0), (4.6, 6.6, 4.9), skip=("b", "u"))
        g += bx(lb, "sec", (cx, 7.0, -2.6), (3.2, 2.6, 0.5))                                              # 膝の当て布
        g += wrap_rings(lb, cx, 4.4, 6.0, 3, mat="main", r=2.45, d=2.55)                                  # 脛の巻き布
        p["leggings"] += g
    laced_boots(p, "sec", "main", top=4.0)
    return p


def start2_b():
    """B 革の胴着 (今の B を作り込む): 紐で締める革の胴着に、肩の革の当て・斜めに掛けた肩掛け鞄の帯・首の布。
    耳当ての革の帽子に額の上げ眼鏡。手首の革の籠手、厚手の脚衣に腿の革帯、紐の革靴"""
    p = empty()
    p["helmet"] += bx(H_, "sec", (0, 32.2, 0), (9.2, 2.8, 9.2), taper=(0.86, 0.86), skip=("b",))
    p["helmet"] += bx(H_, "acc", (0, 30.4, 0), (9.6, 1.2, 9.6), skip=())
    for s in (-1, 1):
        p["helmet"] += bx(H_, "sec", (s * 4.8, 27.8, 0.6), (0.8, 4.2, 4.2), taper=(1.0, 0.8))            # 耳当て
        p["helmet"] += crv(H_, "cord", [(s * 4.9, 25.8, 0.4), (s * 3.0, 24.6, -1.0)], [0.14] * 2, sides=3)
        p["helmet"] += crv(H_, "metal", [(s * 2.0 - 1.0, 31.8, -4.6), (s * 2.0, 32.7, -4.7), (s * 2.0 + 1.0, 31.8, -4.6), (s * 2.0, 30.9, -4.7),
                                         (s * 2.0 - 1.0, 31.8, -4.6)], [0.22] * 5, sides=4)
        p["helmet"] += bx(H_, "glass", (s * 2.0, 31.8, -4.75), (1.4, 1.4, 0.2))
    f = bx(B_, "cloth", (0, 18.0, 0), (9.0, 12.0, 4.9), taper=(0.98, 0.98), skip=("u", "b"))
    f += bx(B_, "sec", (0, 17.4, 0), (9.6, 9.4, 5.5), taper=(1.0, 0.98), skip=("u", "b"))
    for y in (20.6, 18.8, 17.0, 15.2):
        f += crv(B_, "cord", [(-1.0, y, -2.85), (1.0, y - 0.9, -2.85)], [0.16, 0.16], sides=3)
        f += crv(B_, "cord", [(1.0, y, -2.85), (-1.0, y - 0.9, -2.85)], [0.16, 0.16], sides=3)
    for x in (-3.6, 3.6):                                                                                 # 縫い目
        f += crv(B_, "acc", [(x, 21.6, -2.8), (x * 1.04, 13.6, -2.8)], [0.12, 0.12], sides=3)
    f += bx(B_, "main", (0, 23.8, 0), (8.0, 1.6, 5.8), taper=(0.9, 1.0))                                  # 首の布
    f += bx(B_, "main", (-1.6, 22.0, -3.0), (2.0, 2.6, 0.4), rot=rot_z(12))
    # 肩掛け鞄 (右肩から左の腰)
    f += crv(B_, "acc", [(4.4, 24.0, -2.6), (0.4, 18.6, -3.2), (-4.2, 13.6, -2.9)], [0.42] * 3, sides=4)
    f += crv(B_, "acc", [(4.4, 24.0, 2.6), (0.4, 18.6, 3.2), (-4.2, 13.6, 2.9)], [0.42] * 3, sides=4)
    f += bx(B_, "sec", (-4.6, 11.8, 0), (1.8, 3.4, 4.4), taper=(1.0, 0.92))
    f += bx(B_, "acc", (-5.4, 12.6, 0), (0.4, 1.8, 4.5))
    f = belt(f, y=12.8, pouches=())
    p["chestplate"] += f
    for s in (-1, 1):
        g = sleeve(ARM[s], s, "cloth")
        g += bx(ARM[s], "sec", (s * 6.4, 23.6, 0), (5.8, 2.0, 5.8), taper=(0.86, 0.95))                  # 肩の革
        g += bx(ARM[s], "acc", (s * 6.4, 22.4, 0), (5.9, 0.4, 5.9))
        g += bx(ARM[s], "sec", (s * 6.0, 14.4, 0), (5.2, 3.0, 5.3), taper=(0.95, 0.95))                  # 籠手
        for y in (13.6, 15.2):
            g += bx(ARM[s], "metal", (s * 6.0, y, -2.7), (0.6, 0.6, 0.3))
        p["chestplate"] += g
    for s in (-1, 1):
        lb = LEG[s]
        cx = s * 2.15
        g = bx(lb, "main", (cx, 8.6, 0), (4.6, 6.6, 4.9), skip=("b", "u"))
        g += bx(lb, "acc", (cx, 10.2, 0), (4.9, 0.8, 5.2))                                                # 腿の革帯
        g += bx(lb, "metal", (cx + s * 2.4, 10.2, -1.0), (0.4, 0.9, 0.9))
        g += bx(lb, "sec", (cx, 5.6, -2.7), (3.4, 2.4, 0.8), taper=(0.85, 0.9))
        p["leggings"] += g
    laced_boots(p, "sec", "acc", top=4.4)
    return p


def start2_c():
    """C 浜の漁り装束: 袖なしの革の胴衣を麻の上衣に重ね、結び目の垂れる腰帯 (藍)。額に布を巻き、首に貝の首飾り。
    巻きつけた脛当てと、底の厚い紐の履物。港の若い漁り人の支度"""
    p = empty()
    p["helmet"] += bx(H_, "cloth", (0, 30.6, 0), (9.4, 1.8, 9.4), skip=("u", "b"))                        # 額の巻き布
    p["helmet"] += bx(H_, "cloth", (1.6, 30.0, 4.9), (1.4, 2.6, 0.6), rot=rot_z(-14))                    # 結び目の垂れ
    p["helmet"] += bx(H_, "cloth", (2.6, 29.4, 4.9), (1.2, 3.0, 0.5), rot=rot_z(-28))
    f = bx(B_, "main", (0, 18.0, 0), (9.0, 12.0, 4.9), taper=(0.98, 0.98), skip=("u", "b"))
    f += bx(B_, "sec", (0, 17.8, 0), (9.6, 10.0, 5.5), taper=(1.0, 0.98), skip=("u", "b"))
    f += bx(B_, "main", (0, 19.0, -2.85), (2.4, 7.6, 0.4), taper=(0.6, 1.0))                             # 前の開き (上衣が見える)
    for y in (21.0, 18.0, 15.0):
        f += bx(B_, "edge", (-1.4, y, -2.95), (0.6, 0.8, 0.3))
        f += bx(B_, "edge", (1.4, y, -2.95), (0.6, 0.8, 0.3))
    # 貝の首飾り
    for i, (x, y) in enumerate([(-2.4, 23.2), (-1.4, 22.2), (0, 21.8), (1.4, 22.2), (2.4, 23.2)]):
        f += bx(B_, "edge" if i % 2 == 0 else "metal", (x, y, -3.0), (0.9, 0.9, 0.5), rot=rot_z(45))
    # 結び目の腰帯
    f += bx(B_, "cloth", (0, 13.2, 0), (10.0, 1.8, 5.8), skip=("u",))
    f += bx(B_, "cloth", (2.8, 13.2, -3.1), (1.6, 1.6, 0.8))
    f += bx(B_, "cloth", (3.2, 11.0, -3.0), (1.2, 3.0, 0.4), rot=rot_z(10))
    f += bx(B_, "cloth", (2.2, 10.6, -3.0), (1.0, 3.4, 0.4), rot=rot_z(-6))
    f += bx(B_, "sec", (-3.4, 11.4, -2.9), (2.0, 2.2, 1.0))
    p["chestplate"] += f
    for s in (-1, 1):
        g = sleeve(ARM[s], s, "main", bot=16.0)
        g += bx(ARM[s], "main", (s * 6.0, 16.6, 0), (5.3, 1.2, 5.4))                                      # まくった袖
        g += wrap_rings(ARM[s], s * 6.0, 12.6, 15.0, 3, mat="cloth", r=2.5, d=2.6)                         # 手首の巻き布
        p["chestplate"] += g
    for s in (-1, 1):
        lb = LEG[s]
        cx = s * 2.15
        g = bx(lb, "main", (cx, 8.6, 0), (4.6, 6.6, 4.9), skip=("b", "u"))
        g += wrap_rings(lb, cx, 4.2, 8.0, 4, mat="cord", r=2.5, d=2.6)                                    # 巻きつけた脛当て
        p["leggings"] += g
    for s in (-1, 1):
        cx = s * 2.15
        g = bx(BOOT[s], "main", (cx, 1.8, 0), (4.6, 3.2, 4.8), skip=("b",))
        g += bx(BOOT[s], "sec", (cx, 0.5, 0), (5.2, 1.0, 6.0), skip=())                                   # 厚い底
        for z in (-2.0, 0.0, 2.0):
            g += crv(BOOT[s], "cord", [(cx - 2.5, 1.0, z), (cx, 2.6, z - 0.6), (cx + 2.5, 1.0, z)], [0.15] * 3, sides=3)
        p["boots"] += g
    return p


# ================================================================== 鉄張りの旅装
def iron2_a():
    """A 鎖帷子と陣羽織: 膝までの鎖帷子に、藍の陣羽織 (真鍮の鯨の紋)。重ねた鉄の肩当て、首の鉄の喉当て。
    つばのある鉄の帽子 (鍋形) と鎖の垂れ、肘と膝の鉄の当て、爪先を鉄で包んだ靴"""
    p = empty()
    p["helmet"] += bx(H_, "metal", (0, 32.6, 0), (9.0, 3.2, 9.0), taper=(0.72, 0.72), skip=("b",))
    p["helmet"] += bx(H_, "edge", (0, 31.0, 0), (12.0, 0.6, 12.0))                                       # つば
    p["helmet"] += bx(H_, "trim", (0, 34.4, 0), (1.0, 0.6, 1.0))
    for s in (-1, 1):
        p["helmet"] += bx(H_, "main", (s * 4.8, 27.4, 0.8), (0.7, 5.2, 7.4))
        for y in (25.4, 27.4, 29.4):
            p["helmet"] += bx(H_, "edge", (s * 5.2, y, -2.0), (0.3, 0.3, 0.3))
    p["helmet"] += bx(H_, "main", (0, 27.4, 4.6), (9.4, 5.2, 0.7))
    f = bx(B_, "main", (0, 18.0, 0), (9.2, 12.2, 5.0), taper=(0.98, 0.98), skip=("u", "b"))
    f += bx(B_, "main", (0, 10.4, 0), (9.8, 3.4, 5.6), taper=(1.06, 1.0), skip=("u",))
    # 陣羽織 (前と後ろの垂れ、肩で留める)
    for z, sgn in ((-2.85, -1), (2.85, 1)):
        f += bx(B_, "cloth", (0, 16.0, z), (6.4, 14.0, 0.4), taper=(1.0, 0.9))
        f += bx(B_, "trim", (0, 9.2, z + sgn * 0.05), (6.6, 0.5, 0.5))
    f += bx(B_, "trim", (0, 18.8, -3.1), (2.4, 2.8, 0.3), rot=rot_z(45))                                 # 真鍮の紋
    f += bx(B_, "dark", (0, 18.8, -3.25), (1.0, 1.2, 0.2), rot=rot_z(45))
    f += bx(B_, "metal", (0, 24.2, 0), (7.6, 1.6, 5.8), taper=(0.9, 1.0))                                  # 喉当て
    f = belt(f, y=13.2, mat="sec", buckle="trim", pouches=((-3.6, "sec"),))
    p["chestplate"] += f
    for s in (-1, 1):
        g = sleeve(ARM[s], s, "main", bot=17.0)
        for i, y in enumerate((24.2, 22.8, 21.4)):                                                       # 重ねた肩当て
            g += bx(ARM[s], "metal", (s * (6.6 + 0.2 * i), y, 0), (5.9 - 0.3 * i, 1.4, 6.0 - 0.3 * i), taper=(0.9, 0.95))
            g += bx(ARM[s], "edge", (s * (6.6 + 0.2 * i), y - 0.75, 0), (5.9 - 0.3 * i, 0.25, 6.0 - 0.3 * i))
        g += bx(ARM[s], "metal", (s * 6.0, 18.0, -1.0), (4.6, 2.0, 3.0))                                  # 肘
        g += bx(ARM[s], "sec", (s * 6.0, 14.4, 0), (5.2, 3.4, 5.3), taper=(0.95, 0.95))
        p["chestplate"] += g
    for s in (-1, 1):
        lb = LEG[s]
        cx = s * 2.15
        g = bx(lb, "cloth", (cx, 8.6, 0), (4.6, 6.6, 4.9), skip=("b", "u"))
        g += bx(lb, "main", (cx, 9.8, 0), (4.8, 4.4, 5.1), skip=("u",))
        g += bx(lb, "metal", (cx, 5.8, -2.8), (3.8, 2.8, 1.2), taper=(0.8, 0.9))
        g += bx(lb, "edge", (cx, 5.8, -3.5), (0.8, 0.8, 0.4))
        g += bx(lb, "metal", (cx, 4.0, -2.6), (3.6, 1.6, 0.8))                                            # 脛の板の上端
        p["leggings"] += g
    laced_boots(p, "sec", "metal", top=4.2, toe="metal")
    return p


def iron2_b():
    """B 鋲打ちの胴: 革に鉄の板を裏打ちし、表に鋲の列 (ブリガンダイン)。腰に三枚の草摺。鼻当ての兜に鎖の垂れ。
    小手と脛当ては鉄の板を革紐で締める。重さを散らす旅の兵の作り"""
    p = empty()
    p["helmet"] += bx(H_, "metal", (0, 32.4, 0), (9.4, 3.8, 9.4), taper=(0.66, 0.66), skip=("b",))
    p["helmet"] += bx(H_, "edge", (0, 30.6, 0), (9.8, 0.8, 9.8))
    p["helmet"] += bx(H_, "metal", (0, 28.0, -4.9), (1.0, 4.4, 0.6), taper=(0.7, 1.0))                   # 鼻当て
    for s in (-1, 1):
        p["helmet"] += bx(H_, "main", (s * 4.8, 27.2, 1.4), (0.7, 5.0, 6.4))
    p["helmet"] += bx(H_, "main", (0, 27.2, 4.6), (9.4, 5.0, 0.7))
    f = bx(B_, "cloth", (0, 18.0, 0), (9.0, 12.0, 4.9), taper=(0.98, 0.98), skip=("u", "b"))
    f += bx(B_, "sec", (0, 18.2, 0), (9.8, 10.8, 5.6), taper=(1.0, 0.98), skip=("u", "b"))
    for y in (22.4, 20.2, 18.0, 15.8):                                                                    # 鋲の列
        for x in (-3.6, -1.8, 0.0, 1.8, 3.6):
            f += bx(B_, "edge", (x, y, -2.95), (0.5, 0.5, 0.3))
    for x in (-4.9, 4.9):
        f += bx(B_, "metal", (x, 18.2, 0), (0.4, 10.0, 5.0))
    for i, x in enumerate((-3.0, 0.0, 3.0)):                                                              # 草摺
        f += bx(B_, "sec", (x, 10.6, -2.4), (2.9, 4.0, 0.7), rot=rot_x(-6))
        f += bx(B_, "edge", (x, 11.6, -2.85), (0.5, 0.5, 0.3))
    f = belt(f, y=13.0, mat="acc", buckle="metal", pouches=())
    p["chestplate"] += f
    for s in (-1, 1):
        g = sleeve(ARM[s], s, "main", bot=17.0)
        g += bx(ARM[s], "sec", (s * 6.6, 23.6, 0), (5.8, 2.2, 5.8), taper=(0.86, 0.95))
        for z in (-1.6, 0.0, 1.6):
            g += bx(ARM[s], "edge", (s * 9.5, 23.6, z), (0.3, 0.5, 0.5))
        g += bx(ARM[s], "metal", (s * 6.0, 14.6, 0), (5.2, 3.6, 5.3), taper=(0.92, 0.95))                # 小手
        for y in (13.6, 15.6):
            g += bx(ARM[s], "acc", (s * 6.0, y, 0), (5.4, 0.5, 5.5))
        p["chestplate"] += g
    for s in (-1, 1):
        lb = LEG[s]
        cx = s * 2.15
        g = bx(lb, "cloth", (cx, 8.6, 0), (4.6, 6.6, 4.9), skip=("b", "u"))
        g += bx(lb, "metal", (cx, 5.6, -2.6), (3.8, 5.2, 0.8), taper=(0.9, 1.0))                          # 脛当て
        for y in (4.2, 6.8):
            g += bx(lb, "acc", (cx, y, 0), (4.9, 0.5, 5.2))
        p["leggings"] += g
    laced_boots(p, "sec", "acc", top=4.0, toe="metal")
    return p


def iron2_c():
    """C 小札の鎧: 鉄の小札を横の段に綴じた胴 (段ごとに色の紐)。袖は三段の小札、首の後ろに錣のある鉢の兜。
    腿まで垂れる小札の佩楯、鉄の脛当て。鯨の町の古い守りの作りを旅向けに軽くしたもの"""
    p = empty()
    p["helmet"] += bx(H_, "metal", (0, 32.6, 0), (9.2, 3.4, 9.2), taper=(0.74, 0.74), skip=("b",))
    for i, y in enumerate((30.4, 29.0, 27.6)):                                                            # 錣 (後ろと横)
        p["helmet"] += bx(H_, "metal", (0, y, 1.6), (10.2 + 0.4 * i, 1.2, 7.4 + 0.4 * i), skip=("u",))
        p["helmet"] += bx(H_, "cord", (0, y + 0.55, 1.6), (10.3 + 0.4 * i, 0.25, 7.5 + 0.4 * i), skip=("u", "b"))
    p["helmet"] += bx(H_, "trim", (0, 33.6, -4.4), (2.6, 1.6, 0.4))                                       # 前立て
    f = bx(B_, "cloth", (0, 18.0, 0), (9.0, 12.0, 4.9), taper=(0.98, 0.98), skip=("u", "b"))
    for i, y in enumerate((23.2, 21.4, 19.6, 17.8, 16.0, 14.2)):                                          # 小札の段
        f += bx(B_, "metal", (0, y, 0), (9.8 + 0.08 * i, 1.6, 5.6 + 0.06 * i), skip=("u", "b"))
        f += bx(B_, "cord", (0, y - 0.75, 0), (9.9 + 0.08 * i, 0.25, 5.7 + 0.06 * i), skip=("u", "b"))
        for x in (-3.6, -1.2, 1.2, 3.6):
            f += bx(B_, "edge", (x, y, -2.85 - 0.03 * i), (0.3, 1.2, 0.2))
    f = belt(f, y=12.8, mat="cloth", buckle="trim", pouches=((3.6, "sec"),))
    p["chestplate"] += f
    for s in (-1, 1):
        g = sleeve(ARM[s], s, "cloth", bot=15.0)
        for i, y in enumerate((23.8, 22.0, 20.2)):
            g += bx(ARM[s], "metal", (s * (6.4 + 0.3 * i), y, 0), (5.6, 1.6, 5.8), skip=("u",))
            g += bx(ARM[s], "cord", (s * (6.4 + 0.3 * i), y - 0.75, 0), (5.7, 0.25, 5.9), skip=("u", "b"))
        g += bx(ARM[s], "metal", (s * 6.0, 14.2, 0), (5.1, 2.8, 5.2), taper=(0.94, 0.96))
        p["chestplate"] += g
    for s in (-1, 1):
        lb = LEG[s]
        cx = s * 2.15
        g = bx(lb, "cloth", (cx, 8.6, 0), (4.6, 6.6, 4.9), skip=("b", "u"))
        for i, y in enumerate((11.0, 9.4)):                                                               # 佩楯
            g += bx(lb, "metal", (cx, y, -2.7), (4.4, 1.5, 0.7))
            g += bx(lb, "cord", (cx, y - 0.7, -3.05), (4.5, 0.25, 0.25))
        g += bx(lb, "metal", (cx, 5.4, -2.6), (3.8, 4.6, 0.8), taper=(0.9, 1.0))
        g += bx(lb, "edge", (cx, 7.4, -3.05), (3.8, 0.3, 0.3))
        p["leggings"] += g
    laced_boots(p, "sec", "cloth", top=4.0, toe="metal")
    return p


SETS = {"start2_a": start2_a, "start2_b": start2_b, "start2_c": start2_c,
        "iron2_a": iron2_a, "iron2_b": iron2_b, "iron2_c": iron2_c}
JP = {"start2": "初めの旅装 (作り直し)", "iron2": "鉄張りの旅装 (作り直し)"}
