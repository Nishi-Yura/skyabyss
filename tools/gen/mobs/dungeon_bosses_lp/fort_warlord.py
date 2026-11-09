"""骨鎧の野盗王 (fort_warlord)。崩れた見張りの砦の主 (docs/design/55 の 7 章。2026-11-09 の頁で A)。
2026-11-09 の声「ボスのモデルもラフのままだし、めっちゃ小さい。クオリティ低い」で、ラフ (tools/rough/lowpoly/late_foes) から作り直した。
岩鯨の骨を打ち付けた鎧 (胸に肋骨の弧・肩に背骨の当て)、鯨の頭骨の兜と角、毛皮の肩掛けと裂けた外套、片刃の大鉈、腰に骨の角笛。

骨: body (腰) の子に torso・leg_l / leg_r。torso の子に head・arm_l → fore_l、arm_r → fore_r (大鉈)。
動き: idle・walk・sweep (大薙ぎ)・leap (跳び叩き)・charge (突進)・hurt・death
"""
import math

import numpy as np

from blp import *  # noqa: F401,F403
from blp import keys, eye

BONE = [(96, 88, 72), (140, 130, 110), (184, 174, 150), (214, 206, 186), (236, 230, 214)]
LEATHER = [(34, 22, 16), (56, 36, 24), (82, 54, 36), (110, 76, 52)]
CLOTH = [(52, 18, 16), (82, 30, 24), (116, 46, 34), (146, 66, 46)]
FUR = [(30, 26, 24), (52, 44, 38), (78, 68, 58), (104, 92, 80)]
IRON = [(34, 34, 40), (58, 58, 66), (88, 88, 96), (124, 124, 132), (164, 164, 170)]
SKIN = [(78, 50, 36), (112, 76, 56), (146, 104, 78), (176, 134, 104)]
WOOD = [(40, 26, 16), (62, 42, 26), (88, 60, 38)]
DARK = [(12, 10, 10), (24, 20, 20), (38, 34, 32)]
EMBER = [(150, 46, 16), (210, 84, 24), (255, 146, 40), (255, 210, 96)]


def build():
    m = Mount("fort_warlord", "骨鎧の野盗王")
    m.bone("body", "root", (0, 26, 0))
    m.bone("torso", "body", (0, 29, 0))
    m.bone("head", "torso", (0, 44, -1))
    for s, side in ((1, "l"), (-1, "r")):
        m.bone(f"leg_{side}", "body", (s * 4.6, 26, 0))
        m.bone(f"arm_{side}", "torso", (s * 10.5, 41, 0))
        m.bone(f"fore_{side}", f"arm_{side}", (s * 12.5, 31, -1))
    # ---- 腰と脚
    m.add("hips", ell("body", "leather", (0, 26, 0), (8.2, 4.6, 5.6), segs=12, rings=3), core=True)
    m.add("belt", ell("body", "iron", (0, 27.6, 0), (8.8, 1.6, 6.1), segs=14, rings=2))
    m.add("buckle", box("body", "bone", (0, 27.6, -6.1), (3.2, 2.6, 1.0), taper=(0.8, 0.8)))
    for k, (x, z) in enumerate(((-4.5, -5.4), (4.8, -5.2), (7.6, -1.0))):
        m.add(f"belt_skull{k}", ell("body", "bone", (x, 25.6, z), (1.3, 1.5, 1.1), segs=7, rings=2))
        m.add(f"belt_socket{k}", box("body", "dark", (x, 25.9, z - 1.0), (1.4, 0.5, 0.4)))
    # 前垂れと後ろ垂れ (布を二枚ずつ、長さ違い)
    for k, (x, L) in enumerate(((-2.2, 11), (2.4, 9))):
        m.add(f"tasset_f{k}", box("body", "cloth", (x, 25 - L / 2, -5.2), (4.0, L, 0.8), taper=(0.85, 1.0)))
        m.add(f"tasset_b{k}", box("body", "cloth", (x, 25 - L / 2 - 1, 5.2), (4.2, L + 2, 0.8), taper=(0.85, 1.0)))
    for s, side in ((1, "l"), (-1, "r")):
        hip = np.array([s * 4.6, 25, 0])
        knee = np.array([s * 5.2, 14, -0.6])
        ank = np.array([s * 5.4, 3.5, 0.2])
        m.add(f"thigh_{side}", limb(f"leg_{side}", "leather", [hip, (hip + knee) / 2, knee], [6.4, 6.0, 5.2], [6.6, 6.2, 5.4], n=10))
        m.add(f"shin_{side}", limb(f"leg_{side}", "leather", [knee, (knee + ank) / 2, ank], [5.0, 4.8, 4.4], [5.2, 5.0, 4.6], n=10))
        m.add(f"kneecap_{side}", ell(f"leg_{side}", "bone", knee + np.array([0, 0.4, -2.8]), (2.6, 2.6, 1.6), segs=8, rings=2))
        m.add(f"kneespike_{side}", cone(f"leg_{side}", "bone", knee + np.array([0, 0.6, -3.6]), (0, 0.3, -1), 2.2, 0.9, sides=4))
        m.add(f"greave_{side}", lof(f"leg_{side}", "iron", [oct_ring(ank + np.array([0, 2.0, -0.6]), 5.6, 5.8, R=R_(90, 0, 0)),
                                                             oct_ring(ank + np.array([0, 8.0, -0.8]), 5.8, 6.0, R=R_(90, 0, 0))]))
        m.add(f"boot_{side}", ell(f"leg_{side}", "dark", ank + np.array([0, -1.4, -1.6]), (3.4, 2.4, 5.0), segs=10, rings=2))
        m.add(f"boot_cuff_{side}", ell(f"leg_{side}", "fur", ank + np.array([0, 1.2, 0]), (3.6, 1.2, 3.6), segs=10, rings=2))
    # ---- 胴: 厚い胸と腹、骨の胸当て (鯨の肋骨の弧を五本)、背骨の当て
    torso = [rsec(z, 43.5 - abs(z) * 0.25, 28, 9.6 - abs(z) * 0.18, mid=38, n=16) for z in (-6, -3, 0, 3, 6)]
    m.add("chest", lof("torso", "leather", torso), core=True)
    m.add("belly", ell("torso", "leather", (0, 31.5, -1.6), (7.6, 4.0, 5.0), segs=12, rings=3))
    m.add("sternum", box("torso", "bone", (0, 37.0, -6.4), (2.2, 10.0, 1.4), taper=(0.7, 1.0)))
    for k in range(5):
        y = 41.0 - k * 2.4
        span = 7.6 - k * 0.5
        for s in (1, -1):
            pts = [(s * 1.0, y, -6.6), (s * span * 0.55, y - 0.6, -6.6), (s * span, y - 1.6, -4.4), (s * (span + 0.6), y - 2.4, -1.0)]
            m.add(f"rib{k}{s}", tub("torso", "bone", pts, [0.75, 0.8, 0.75, 0.6], None, sides=5, hint=(0, 1, 0)))
    for k in range(6):
        m.add(f"spine{k}", ell("torso", "bone", (0, 42.5 - k * 2.4, 6.4), (1.6, 1.1, 1.2), segs=7, rings=2))
        m.add(f"spinefin{k}", cone("torso", "bone", (0, 42.8 - k * 2.4, 7.0), (0, 0.5, 1), 1.8 - k * 0.15, 0.7, sides=4))
    # 毛皮の肩掛け (首のまわりの輪) と毛の房
    m.add("mantle", ell("torso", "fur", (0, 43.2, 0.6), (9.6, 2.8, 7.2), segs=14, rings=3))
    for k in range(9):
        a = math.radians(200 + k * 17.5)
        p = np.array([math.cos(a) * 9.0, 42.4, 0.6 + math.sin(a) * 6.6 + 2.0])
        m.add(f"tuft{k}", cone("torso", "fur", p, (math.cos(a) * 0.6, -1, math.sin(a) * 0.4 + 0.3), 2.6, 1.0, sides=4))
    # 裂けた外套 (背に五枚の垂れ、長さ違い)
    for k in range(5):
        x = (k - 2) * 3.4
        L = 26 - (k % 2) * 5 - abs(k - 2) * 2
        m.add(f"cape{k}", box("torso", "cloth", (x, 42.0 - L / 2, 7.4 + abs(k - 2) * 0.3), (3.6, L, 0.7), taper=(0.75, 1.0)))
    # ---- 頭: 顔・あごひげ・鯨の頭骨の兜・角・光る目
    head = np.array([0, 48.5, -1.4])
    m.add("neck", ell("head", "skin", (0, 44.5, -0.6), (3.0, 2.4, 3.0), segs=9, rings=2))
    m.add("skull", ell("head", "skin", head, (3.4, 3.8, 3.6), segs=11, rings=4), core=True)
    m.add("nose", ell("head", "skin", head + np.array([0, -0.4, -3.6]), (0.9, 1.3, 1.0), segs=6, rings=2))
    m.add("beard", ell("head", "fur", head + np.array([0, -3.2, -2.4]), (2.8, 2.6, 1.8), segs=9, rings=3))
    for s in (1, -1):
        m.add(f"braid{s}", tub("head", "fur", [head + np.array([s * 1.2, -4.8, -2.8]), head + np.array([s * 1.4, -8.0, -3.0]), head + np.array([s * 1.2, -10.0, -2.6])],
                                [0.7, 0.6, 0.4], None, sides=5))
        m.add(f"bead{s}", ell("head", "bone", head + np.array([s * 1.3, -8.6, -3.0]), (0.7, 0.7, 0.7), segs=6, rings=2))
        eye(m, "head", head + np.array([s * 1.35, 0.6, -3.2]), 0.6, (s * 0.2, 0, -1), (255, 150, 50), glow=True)
    # 兜: 鯨の頭骨をかぶる (額から後ろへ長い殻、目の上のひさし、頬の当て)
    m.add("helm", lof("head", "bone", [oct_ring(head + np.array([0, 2.4, -3.8]), 7.6, 3.4, R=np.eye(3)),
                                       oct_ring(head + np.array([0, 3.0, -1.0]), 8.4, 4.8, R=np.eye(3)),
                                       oct_ring(head + np.array([0, 2.6, 2.4]), 8.0, 5.2, R=np.eye(3)),
                                       oct_ring(head + np.array([0, 1.4, 5.4]), 6.0, 4.0, R=np.eye(3))]))
    m.add("brow", box("head", "bone", head + np.array([0, 1.6, -4.0]), (7.8, 1.2, 1.6), taper=(0.9, 1.0)))
    m.add("snout_crest", tub("head", "bone", [head + np.array([0, 4.6, -4.4]), head + np.array([0, 5.8, -0.4]), head + np.array([0, 5.4, 4.6])],
                              [1.0, 1.3, 0.8], None, sides=5, hint=(1, 0, 0)))
    for s in (1, -1):
        m.add(f"cheek_guard{s}", box("head", "bone", head + np.array([s * 3.9, -1.0, -1.2]), (1.0, 4.0, 4.4), taper=(0.8, 1.0)))
        m.add(f"horn{s}", cone("head", "bone", head + np.array([s * 3.6, 3.4, 0.4]), (s * 1, 0.7, 0.4), 6.0, 1.4, sides=6, rings=4, bend=(0, 2.6, 1.6)))
        m.add(f"horn_ring{s}", ell("head", "iron", head + np.array([s * 4.6, 4.0, 0.6]), (1.2, 1.2, 1.2), segs=7, rings=2))
    # ---- 腕: 背骨の肩当て (重ねた椎骨と棘)・腕・籠手・拳。右は大鉈、左は角笛を握らず開いた拳
    for s, side in ((1, "l"), (-1, "r")):
        sh = np.array([s * 10.5, 41, 0])
        el = np.array([s * 12.5, 31, -1])
        wr = np.array([s * 13.0, 22.5, -2.4])
        m.add(f"upper_{side}", limb(f"arm_{side}", "skin", [sh, (sh + el) / 2, el], [5.8, 5.2, 4.6], [5.8, 5.2, 4.6], n=10))
        m.add(f"lower_{side}", limb(f"fore_{side}", "skin", [el, (el + wr) / 2, wr], [4.6, 4.4, 4.0], [4.6, 4.4, 4.0], n=10))
        for k in range(3):
            m.add(f"pauldron_{side}{k}", ell(f"arm_{side}", "bone", sh + np.array([s * (0.6 + k * 0.4), 2.6 - k * 1.8, 0]), (5.2 - k * 0.7, 2.0, 4.6 - k * 0.5),
                                             segs=10, rings=2))
        for k in range(3):
            m.add(f"pspike_{side}{k}", cone(f"arm_{side}", "bone", sh + np.array([s * 2.4, 3.6, (k - 1) * 2.4]), (s * 0.5, 1, (k - 1) * 0.2), 3.4 - abs(k - 1), 0.9, sides=4))
        m.add(f"bracer_{side}", lof(f"fore_{side}", "iron", [oct_ring((el * 0.35 + wr * 0.65) + np.array([0, -2.4, 0]), 5.4, 5.4, R=R_(90, 0, 0)),
                                                               oct_ring((el * 0.6 + wr * 0.4) + np.array([0, 0.6, 0]), 5.6, 5.6, R=R_(90, 0, 0))]))
        m.add(f"bracer_strap_{side}", ell(f"fore_{side}", "leather", el * 0.5 + wr * 0.5, (2.9, 0.7, 2.9), segs=9, rings=1))
        m.add(f"fist_{side}", ell(f"fore_{side}", "skin", wr + np.array([0, -1.6, 0]), (2.6, 2.8, 2.6), segs=9, rings=3))
        for k in range(4):
            m.add(f"knuckle_{side}{k}", ell(f"fore_{side}", "skin", wr + np.array([s * 0.4, -2.2 - k * 0.0, -2.2 + (k - 1.5) * 0.0]) + np.array([0, -(k - 1.5) * 1.0, 0]),
                                            (0.8, 0.8, 0.8), segs=5, rings=2))
        if side == "r":
            grip = wr + np.array([0, -1.6, 0])
            m.add("haft", tub("fore_r", "wood", [grip + np.array([0, 3.4, 0]), grip + np.array([0, -4.0, -2.0])], [0.9, 0.9], None, sides=6))
            for k in range(3):
                m.add(f"wrap{k}", ell("fore_r", "leather", grip + np.array([0, 2.0 - k * 1.9, -0.5 * k]), (1.15, 0.6, 1.15), segs=7, rings=1))
            m.add("pommel", ell("fore_r", "bone", grip + np.array([0, 3.8, 0.1]), (1.3, 1.3, 1.3), segs=7, rings=2))
            # 片刃の大鉈: 柄の先から前下へ伸びる幅広の刃 (背は厚く、刃先は薄い)
            base = grip + np.array([0, -4.0, -2.0])
            d = nrm(np.array([0, -1.0, -0.42]))
            perp = nrm(np.cross(d, np.array([1.0, 0, 0])))
            rings = []
            xv = np.array([1.0, 0, 0])
            for t, w_, th in ((0.0, 4.4, 0.7), (5.0, 6.0, 0.7), (11.0, 7.4, 0.65), (16.5, 7.8, 0.55), (19.5, 5.2, 0.4)):
                c = base + d * t
                back, edge = c - perp * 1.0, c + perp * (w_ - 1.0)
                rings.append([back + xv * th, edge + xv * 0.12, edge - xv * 0.12, back - xv * th])
            m.add("blade", lof("fore_r", "iron", rings))
            m.add("blade_spine", tub("fore_r", "dark", [base + perp * -3.4 * 0 + d * 0.5 - perp * 1.0, base + d * 19.0 - perp * 1.6], [0.7, 0.5], None, sides=4))
            for k in range(3):
                m.add(f"notch{k}", box("fore_r", "dark", base + d * (5 + k * 5) + perp * (3.0 + k * 0.4), (1.6, 1.0, 1.0)))
            m.add("blade_bone", box("fore_r", "bone", base + d * 1.6 + perp * 0.2, (1.8, 2.4, 3.2)))
    # 腰の角笛 (手下を呼ぶ)
    m.add("horn_body", cone("body", "bone", (8.6, 24.6, 2.6), (0.15, -0.4, 1), 6.4, 1.6, sides=7, rings=4, bend=(0, -1.6, -0.6)))
    m.add("horn_band", ell("body", "iron", (8.6, 24.6, 2.6), (1.8, 1.8, 0.8), segs=8, rings=1))
    m.add("horn_strap", tub("body", "leather", [(8.4, 27.6, -1.4), (8.8, 25.2, 2.4)], [0.5, 0.5], None, sides=4))

    m.pal = dict(mats={"eye": M_(EMBER, base=0.7, lit=0.2, noise=0.0, min=1), "bone": M_(BONE, lit=0.36, streak=0.05), "leather": M_(LEATHER, lit=0.3),
                       "cloth": M_(CLOTH, lit=0.3, streak=0.06), "fur": M_(FUR, lit=0.26), "iron": M_(IRON, lit=0.4), "skin": M_(SKIN, lit=0.32),
                       "wood": M_(WOOD, streak=0.08), "dark": M_(DARK, lit=0.2)}, glow_mats=["eye"])

    def detail(c, col, glow):
        P = c["P"]
        col = fur(c, col, ["fur"], amt=0.16)
        sel = np.isin(c["mats"], ["bone"]) & c["owned"]
        f1, f2, idv = cell3(P, 3.0, 11)
        crack = sel & ((f2 - f1) < 0.03) & (idv > 0.45)
        col = np.where(crack[..., None], np.array([70, 62, 50]), col)
        col = stitches(c, col, ["leather"], rgb=(150, 120, 80), every=4)
        col = edge_dark(c, col, ["iron", "bone"], k=0.72)
        col = speckle(c, col, ["iron"], (120, 64, 40), density=0.08)
        return col, glow
    m.detail = detail
    m.hint = 512
    m.view = dict(center=(0, 28, 0), scale=2.4)
    m.gaits = gaits()
    return m


# ---------------------------------------------------------------- 動き (腕は x が負 = 前へ上がる、胴は x が正 = 前へかがむ。forge_giant と同じ)
def gaits():
    g = []
    idle = Gait("idle", 3.2)
    idle.rot("torso", W(0).s(1.4, 1, 0, "t"))
    idle.rot("head", W(0).s(2.0, 1, 60, "t"), W(0).s(4.0, 1, 0, "t"))
    idle.rot("arm_r", W(-6).s(2.0, 1, 30, "t"))
    idle.rot("arm_l", W(0).s(2.0, 1, 210, "t"), None, W(4))
    idle.pos("torso", None, W(0).s(0.3, 1, 0, "t"))
    g.append(idle)
    walk = Gait("walk", 1.1)
    walk.rot("leg_l", W(0).s(24, 1, 0, "t"))
    walk.rot("leg_r", W(0).s(24, 1, 180, "t"))
    walk.rot("arm_l", W(0).s(-16, 1, 0, "t"))
    walk.rot("arm_r", W(-8).s(-8, 1, 180, "t"))
    walk.rot("torso", W(4), W(0).s(5, 1, 0, "t"))
    walk.pos("body", None, W(0).s(-0.7, 2, 90, "t"))
    g.append(walk)
    rest = {}
    wind = {"torso": {"rot": (0, 42, 0)}, "arm_r": {"rot": (-80, 40, 0)}, "fore_r": {"rot": (-30, 0, 0)}, "arm_l": {"rot": (-20, 0, 20)},
            "leg_l": {"rot": (-10, 0, 0)}}
    cut = {"torso": {"rot": (8, -48, 0)}, "arm_r": {"rot": (-80, -55, 0)}, "fore_r": {"rot": (-10, 0, 0)}, "arm_l": {"rot": (10, 0, 30)},
           "leg_r": {"rot": (-10, 0, 0)}}
    g.append(keys("sweep", 1.4, [(0.0, rest), (0.8, wind), (1.0, cut), (1.15, cut), (1.4, rest)]))
    crouch = {"body": {"pos": (0, -3, 0)}, "torso": {"rot": (-12, 0, 0)}, "arm_r": {"rot": (-165, 0, -8)}, "fore_r": {"rot": (-20, 0, 0)},
              "arm_l": {"rot": (-150, 0, 10)}, "leg_l": {"rot": (-40, 0, 0)}, "leg_r": {"rot": (30, 0, 0)}}
    air = {"body": {"pos": (0, 6, 0)}, "torso": {"rot": (-8, 0, 0)}, "arm_r": {"rot": (-170, 0, -8)}, "fore_r": {"rot": (-20, 0, 0)},
           "arm_l": {"rot": (-160, 0, 10)}, "leg_l": {"rot": (-30, 0, 0)}, "leg_r": {"rot": (20, 0, 0)}}
    slam = {"body": {"pos": (0, -3, 0)}, "torso": {"rot": (26, 0, 0)}, "arm_r": {"rot": (-40, 0, -6)}, "fore_r": {"rot": (-10, 0, 0)},
            "arm_l": {"rot": (-30, 0, 10)}, "leg_l": {"rot": (-40, 0, 0)}, "leg_r": {"rot": (30, 0, 0)}}
    g.append(keys("leap", 1.4, [(0.0, rest), (0.6, crouch), (0.85, air), (1.0, slam), (1.2, slam), (1.4, rest)]))
    lean = {"torso": {"rot": (20, 0, 0)}, "arm_r": {"rot": (-70, 0, 20)}, "fore_r": {"rot": (-40, 0, 0)}, "arm_l": {"rot": (20, 0, 10)}, "head": {"rot": (-14, 0, 0)}}
    run1 = dict(lean, **{"leg_l": {"rot": (-45, 0, 0)}, "leg_r": {"rot": (40, 0, 0)}})
    run2 = dict(lean, **{"leg_l": {"rot": (40, 0, 0)}, "leg_r": {"rot": (-45, 0, 0)}})
    g.append(keys("charge", 1.6, [(0.0, rest), (0.9, lean), (1.05, run1), (1.2, run2), (1.35, run1), (1.6, rest)]))
    g.append(keys("hurt", 0.3, [(0.0, rest), (0.1, {"torso": {"rot": (-8, 0, 4)}, "head": {"rot": (-10, 0, 0)}}), (0.3, rest)]))
    kneel = {"body": {"pos": (0, -8, 0)}, "leg_l": {"rot": (-80, 0, 0)}, "leg_r": {"rot": (10, 0, 0)}, "torso": {"rot": (24, 0, 0)}, "head": {"rot": (20, 0, 0)},
             "arm_r": {"rot": (-20, 0, -10)}, "arm_l": {"rot": (10, 0, 10)}}
    fall = {"body": {"pos": (0, -18, -6), "rot": (70, 0, 0)}, "leg_l": {"rot": (8, 0, 0)}, "leg_r": {"rot": (14, 0, 0)}, "torso": {"rot": (10, 0, 0)},
            "head": {"rot": (10, 0, 0)}, "arm_l": {"rot": (-60, 0, 30)}, "arm_r": {"rot": (-80, 0, -30)}}
    g.append(keys("death", 2.0, [(0.0, rest), (0.6, kneel), (1.0, kneel), (1.7, fall), (2.0, fall)], hold=True))
    return g


POSES = [("idle", 0.0), ("walk", 0.3), ("sweep", 0.8), ("sweep", 1.0), ("leap", 0.85), ("leap", 1.0), ("charge", 1.05), ("death", 2.0)]
BOUNDS = (6, 6)
