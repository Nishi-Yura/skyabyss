"""骨鎧の野盗王 (fort_warlord)。崩れた見張りの砦の主 (docs/design/55 の 7 章。2026-11-09 の頁で A)。
2026-11-09 の声「ボスのモデルもラフのままだし、めっちゃ小さい。クオリティ低い」で、ラフ (tools/rough/lowpoly/late_foes) から作り直した。
岩鯨の骨を打ち付けた鎧 (胸に肋骨の弧・肩に背骨の当て)、鯨の頭骨の兜と角、毛皮の肩掛けと裂けた外套、片刃の大鉈、腰に骨の角笛。

作り込み (2026-11-11 の声「他のボスたちもこの方向性で」。骨掘りの大百足と同じ方向): 膝で折れる脚、はためく外套 (cape)、
兜の鯨の頭骨に下あご (helm_jaw。吠えると外れるように開く、骨の歯)。動きは溜め → 一気に → 行き過ぎ → 戻る。

骨: body (腰) の子に torso・leg_l → leg_l_lo・leg_r → leg_r_lo。torso の子に head → helm_jaw・cape・arm_l → fore_l、arm_r → fore_r (大鉈)。
動き: idle・walk・sweep (大薙ぎ)・combo (三連: 右から・左から・振り下ろし)・leap (跳び叩き)・charge (突進)・
      intro (登場: 上から跳び降り、鉈を引きずり、角笛を吹く)・scream (後半: 角笛を吹いて吠える)・hurt・death
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
    m.bone("cape", "torso", (0, 42, 7.4))
    m.bone("helm_jaw", "head", (0, 47.6, 1.0))
    for s, side in ((1, "l"), (-1, "r")):
        m.bone(f"leg_{side}", "body", (s * 4.6, 26, 0))
        m.bone(f"leg_{side}_lo", f"leg_{side}", (s * 5.2, 14, -0.6))
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
        m.add(f"shin_{side}", limb(f"leg_{side}_lo", "leather", [knee, (knee + ank) / 2, ank], [5.0, 4.8, 4.4], [5.2, 5.0, 4.6], n=10))
        m.add(f"kneecap_{side}", ell(f"leg_{side}_lo", "bone", knee + np.array([0, 0.4, -2.8]), (2.6, 2.6, 1.6), segs=8, rings=2))
        m.add(f"kneespike_{side}", cone(f"leg_{side}_lo", "bone", knee + np.array([0, 0.6, -3.6]), (0, 0.3, -1), 2.2, 0.9, sides=4))
        m.add(f"greave_{side}", lof(f"leg_{side}_lo", "iron", [oct_ring(ank + np.array([0, 2.0, -0.6]), 5.6, 5.8, R=R_(90, 0, 0)),
                                                             oct_ring(ank + np.array([0, 8.0, -0.8]), 5.8, 6.0, R=R_(90, 0, 0))]))
        m.add(f"boot_{side}", ell(f"leg_{side}_lo", "dark", ank + np.array([0, -1.4, -1.6]), (3.4, 2.4, 5.0), segs=10, rings=2))
        m.add(f"boot_cuff_{side}", ell(f"leg_{side}_lo", "fur", ank + np.array([0, 1.2, 0]), (3.6, 1.2, 3.6), segs=10, rings=2))
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
        m.add(f"cape{k}", box("cape", "cloth", (x, 42.0 - L / 2, 7.4 + abs(k - 2) * 0.3), (3.6, L, 0.7), taper=(0.75, 1.0)))
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
    # 兜の下あご: 鯨の頭骨のあごの骨。吠えると外れるように開き、骨の歯が並ぶ
    m.add("helm_jawbone", lof("helm_jaw", "bone", [oct_ring(head + np.array([0, -1.4, -4.6]), 6.6, 1.6, R=np.eye(3)),
                                                   oct_ring(head + np.array([0, -1.0, -1.0]), 7.6, 1.8, R=np.eye(3)),
                                                   oct_ring(head + np.array([0, -0.4, 1.6]), 7.4, 1.6, R=np.eye(3))]))
    for k in range(5):
        x = (k - 2) * 1.5
        m.add(f"helm_tooth{k}", cone("helm_jaw", "bone", head + np.array([x, -0.8, -4.6 + abs(x) * 0.4]), (0, 1, -0.2), 1.4, 0.45, sides=4, rings=1))
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


# ---------------------------------------------------------------- 動き (腕は x が負 = 前へ上がる、胴は x が正 = 前へかがむ。脚は x が負 = 前へ上がる)
def merge(*poses):
    out = {}
    for p in poses:
        for b, ch in p.items():
            out.setdefault(b, {}).update(ch)
    return out


def R(**kw):
    return {b: {"rot": v} for b, v in kw.items()}


def P(**kw):
    return {b: {"pos": v} for b, v in kw.items()}


def jaw(a):
    return R(helm_jaw=(a, 0, 0))


def legs(l=0.0, r=0.0, kl=0.0, kr=0.0):
    """脚: 太腿の前上げ (度) と膝の曲げ"""
    return R(leg_l=(-l, 0, 0), leg_r=(-r, 0, 0), leg_l_lo=(kl, 0, 0), leg_r_lo=(kr, 0, 0))


def cape(a):
    return R(cape=(-a, 0, 0))


def shake(base, bones, amp, t0, t1, step=0.08, axis=1):
    out = []
    t, k = t0, 0
    while t <= t1 + 1e-6:
        p = merge(base)
        for b in bones:
            r = list(p.get(b, {}).get("rot", (0, 0, 0)))
            r[axis] += amp if k % 2 == 0 else -amp
            p.setdefault(b, {})["rot"] = tuple(r)
        out.append((round(t, 3), p))
        t += step
        k += 1
    return out


def gaits():
    g = []
    idle = Gait("idle", 3.2)
    idle.rot("torso", W(2).s(1.6, 1, 0, "t"))
    idle.rot("head", W(0).s(2.0, 1, 60, "t"), W(0).s(6.0, 1, 0, "t"))
    idle.rot("arm_r", W(-8).s(2.5, 1, 30, "t"))
    idle.rot("arm_l", W(0).s(2.0, 1, 210, "t"), None, W(4))
    idle.rot("cape", W(-4).s(3.0, 1, 40, "t").s(1.5, 3, 0, "t"))
    idle.rot("helm_jaw", W(2).s(2.0, 2, 0, "t"))
    idle.pos("torso", None, W(0).s(0.35, 1, 0, "t"))
    g.append(idle)
    walk = Gait("walk", 1.1)
    walk.rot("leg_l", W(0).s(26, 1, 0, "t"))
    walk.rot("leg_r", W(0).s(26, 1, 180, "t"))
    walk.rot("leg_l_lo", W(14).s(14, 1, 270, "t"))
    walk.rot("leg_r_lo", W(14).s(14, 1, 90, "t"))
    walk.rot("arm_l", W(0).s(-18, 1, 0, "t"))
    walk.rot("arm_r", W(-10).s(-8, 1, 180, "t"))
    walk.rot("torso", W(5), W(0).s(6, 1, 0, "t"))
    walk.rot("cape", W(-14).s(6, 2, 0, "t"))
    walk.pos("body", None, W(0).s(-0.8, 2, 90, "t"))
    g.append(walk)
    rest = {}
    # 大薙ぎ (SWEEP_WARN 18 = 0.90 秒): 体を捻って鉈を大きく引き、震え、振り抜いて行き過ぎる
    wind = merge(R(torso=(-4, 46, 0), arm_r=(-84, 44, 0), fore_r=(-34, 0, 0), arm_l=(-24, 0, 24), head=(0, -24, 0)), legs(14, -6, 10, 4), cape(10), jaw(12))
    cut = merge(R(torso=(10, -50, 0), arm_r=(-80, -58, 0), fore_r=(-8, 0, 0), arm_l=(12, 0, 34), head=(0, 16, 0)), legs(-10, 16, 4, 14), cape(24), jaw(24),
                P(body=(0, -1, -1.5)))
    cut_o = merge(cut, R(torso=(12, -58, 0), arm_r=(-78, -66, 0)))
    g.append(keys("sweep", 1.4, [(0.0, rest), (0.6, wind), (0.8, merge(wind, R(torso=(-4, 50, 0)))), (0.90, cut), (1.0, cut_o), (1.12, cut), (1.4, rest)]))
    # 三連 (0.70・1.20・1.90 秒 = COMBO_HIT1/2/3): 右から斬り、返して左から斬り、跳ねて振りかぶり、叩きつける
    c1w = merge(R(torso=(-2, 38, 0), arm_r=(-90, 40, 0), fore_r=(-30, 0, 0), head=(0, -18, 0)), legs(10, 0, 8, 4), jaw(10))
    c1 = merge(R(torso=(8, -40, 0), arm_r=(-82, -50, 0), fore_r=(-8, 0, 0), head=(0, 14, 0)), legs(0, 14, 4, 12), cape(18), jaw(20), P(body=(0, -1, -1.5)))
    c2 = merge(R(torso=(8, 40, 0), arm_r=(-60, 50, 0), fore_r=(-20, 0, 0), head=(0, -14, 0)), legs(14, 0, 12, 4), cape(20), jaw(22), P(body=(0, -1, -3)))
    up = merge(R(torso=(-16, 0, 0), arm_r=(-170, 0, -8), fore_r=(-30, 0, 0), arm_l=(-150, 0, 10), head=(-12, 0, 0)), legs(20, -4, 30, 4), cape(30), jaw(30),
               P(body=(0, 1.5, -3)))
    down = merge(R(torso=(30, 0, 0), arm_r=(-36, 0, -6), fore_r=(-8, 0, 0), arm_l=(-24, 0, 10), head=(12, 0, 0)), legs(36, -16, 36, 10), cape(10), jaw(34),
                 P(body=(0, -4, -5)))
    g.append(keys("combo", 2.5, [(0.0, rest), (0.45, c1w), (0.70, c1), (0.80, merge(c1, R(torso=(10, -46, 0)))), (1.05, merge(c2, R(arm_r=(-90, -30, 0)))),
                                 (1.20, c2), (1.30, merge(c2, R(torso=(10, 46, 0)))), (1.62, up), (1.78, merge(up, R(arm_r=(-176, 0, -8)))), (1.90, down),
                                 (2.0, merge(down, P(body=(0, -4.6, -5)))), (2.5, rest)]))
    # 跳び叩き (LEAP_WARN 24 = 1.20 秒に落ちる): 深く沈み、両手で鉈を掲げて跳び、落ちざまに叩きつける
    crouch = merge(R(torso=(14, 0, 0), arm_r=(-40, 0, -8), arm_l=(-30, 0, 10)), legs(40, 40, 70, 70), P(body=(0, -6, 0)), cape(4), jaw(10))
    air = merge(R(torso=(-12, 0, 0), arm_r=(-172, 0, -8), fore_r=(-24, 0, 0), arm_l=(-160, 0, 10), head=(-10, 0, 0)), legs(40, 10, 50, 30), P(root=(0, 26, 0)),
                cape(50), jaw(30))
    slam = merge(R(torso=(30, 0, 0), arm_r=(-36, 0, -6), fore_r=(-8, 0, 0), arm_l=(-30, 0, 10), head=(12, 0, 0)), legs(40, -20, 60, 10), P(body=(0, -5, -1)),
                 cape(12), jaw(36))
    g.append(keys("leap", 1.8, [(0.0, rest), (0.5, crouch), (0.65, merge(crouch, P(body=(0, -7, 0)))), (0.85, air), (1.05, merge(air, P(root=(0, 22, 0)))),
                                (1.20, slam), (1.32, merge(slam, P(body=(0, -6, -1)))), (1.8, rest)]))
    # 突進 (CHARGE_WARN 22 = 1.10 秒に走り出す): 身を低く構えて鉈を脇に、地を蹴って走る
    lean = merge(R(torso=(26, 0, 0), arm_r=(-60, 0, 24), fore_r=(-40, 0, 0), arm_l=(20, 0, 10), head=(-16, 0, 0)), legs(20, -10, 30, 20), P(body=(0, -2, 0)),
                 cape(20), jaw(18))
    run1 = merge(lean, legs(50, -40, 20, 60), cape(40))
    run2 = merge(lean, legs(-40, 50, 60, 20), cape(46))
    g.append(keys("charge", 1.8, [(0.0, rest), (0.7, lean), (0.95, merge(lean, P(body=(0, -3, 0)))), (1.10, run1), (1.25, run2), (1.40, run1), (1.55, run2), (1.8, rest)]))
    # 後半: 左手を口へ当てて角笛を吹き鳴らすように天へ吠え、兜のあごが外れるほど開く
    call = merge(R(torso=(-18, 0, 0), head=(-28, 0, 0), arm_l=(-150, 0, -40), fore_l=(-60, 0, 0), arm_r=(-50, 0, -30)), jaw(44), cape(30), legs(-6, 10, 4, 10))
    fw = merge(R(torso=(16, 0, 0), head=(10, 0, 0), arm_r=(-90, 0, -10), arm_l=(10, 0, 30)), jaw(44), cape(14), legs(16, -6, 12, 4))
    g.append(keys("scream", 1.6, [(0.0, rest), (0.32, call)] + shake(call, ["head"], 3, 0.38, 1.04, step=0.07, axis=2) + [(1.20, fw), (1.6, rest)]))
    # 登場 (4.6 秒): 砦の上から跳び降りて (体が落ちてくる)、深く着地し、ゆっくり身を起こし、鉈を床に引きずって歩み、
    # 天へ吠える (名前の札はここ 2.90 秒 = introCardAt 58)、鉈の切っ先を人へ向けて構える
    fall = merge(R(torso=(-10, 0, 0), arm_r=(-120, 0, -20), arm_l=(-100, 0, 30), head=(-10, 0, 0)), legs(30, 10, 40, 20), P(root=(0, 90, 0)), cape(70), jaw(20))
    land = merge(R(torso=(34, 0, 0), arm_r=(-30, 0, -20), arm_l=(10, 0, 30), head=(10, 0, 0)), legs(60, 20, 100, 60), P(body=(0, -10, 0)), cape(6), jaw(10))
    rise = merge(R(torso=(16, 0, 0), arm_r=(-10, 0, -10), head=(-6, 0, 0)), legs(30, 10, 40, 20), P(body=(0, -4, 0)), cape(4), jaw(6))
    drag = merge(R(torso=(8, -10, 0), arm_r=(10, 0, -20), fore_r=(-6, 0, 0), head=(0, 10, 0)), legs(16, -10, 10, 10), cape(10))
    roar = merge(R(torso=(-20, 0, 0), head=(-30, 0, 0), arm_r=(-160, 0, -16), fore_r=(-20, 0, 0), arm_l=(-60, 0, 50)), jaw(46), cape(34), legs(-6, 10, 4, 10))
    point = merge(R(torso=(4, -20, 0), arm_r=(-90, -10, 0), fore_r=(-4, 0, 0), arm_l=(10, 0, 30), head=(0, 6, 0)), jaw(12), legs(16, -8, 10, 6), cape(12))
    it = [(0.0, fall), (0.35, merge(fall, P(root=(0, 30, 0)))), (0.55, land), (0.7, merge(land, P(body=(0, -11, 0)))), (1.3, rise), (1.75, drag),
          (2.1, merge(drag, legs(-12, 16, 6, 12), R(torso=(8, 10, 0)))), (2.6, merge(roar, jaw(10))), (2.9, roar)]
    it += shake(roar, ["head"], 3, 2.96, 3.6, step=0.07, axis=2)
    it += [(3.9, point), (4.3, point), (4.6, rest)]
    g.append(keys("intro", 4.6, it))
    g.append(keys("hurt", 0.3, [(0.0, rest), (0.1, merge(R(torso=(-8, 0, 4), head=(-10, 0, 0)), jaw(20), cape(10))), (0.3, rest)]))
    kneel = merge(P(body=(0, -8, 0)), legs(80, -10, 90, 10), R(torso=(24, 0, 0), head=(20, 0, 0), arm_r=(-20, 0, -10), arm_l=(10, 0, 10)), jaw(30), cape(6))
    fall2 = merge(P(body=(0, -18, -6)), R(body=(70, 0, 0), torso=(10, 0, 0), head=(10, 0, 0), arm_l=(-60, 0, 30), arm_r=(-80, 0, -30)), legs(-8, -14, 10, 20),
                  jaw(40), cape(-20))
    g.append(keys("death", 2.2, [(0.0, rest), (0.3, merge(R(torso=(-16, 0, 0), head=(-24, 0, 0)), jaw(46))), (0.8, kneel), (1.2, kneel), (1.9, fall2), (2.2, fall2)],
                  hold=True))
    return g


POSES = [("idle", 0.0), ("walk", 0.3), ("sweep", 0.8), ("sweep", 0.9), ("combo", 0.7), ("combo", 1.2), ("combo", 1.78), ("combo", 1.9),
         ("leap", 0.65), ("leap", 1.05), ("charge", 1.25), ("scream", 0.5), ("intro", 0.55), ("intro", 1.75), ("intro", 2.9), ("intro", 3.9)]
BOUNDS = (7, 7)
