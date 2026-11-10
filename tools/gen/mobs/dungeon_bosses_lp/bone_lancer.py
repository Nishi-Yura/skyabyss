"""骨馬の槍騎兵 (bone_lancer)。崩れた見張りの砦の中ボス (2026-11-09 のラフ tools/rough/lowpoly/deep_mids で C)。
岩鯨の骨でできた馬 (肋の透けた胴・頭骨・骨の脚) に、骨の兜と革の鎧の野盗の騎兵が乗る。長い槍と赤い小旗、馬には赤い馬衣。

骨: body (馬の胴) の子に neck (→ head)・leg_fl / leg_fr / leg_bl / leg_br・tail・rider (騎手の胴)。rider の子に rhead・arm_l・arm_r (槍)。
動き: idle・walk・charge (槍の突撃)・rear (棹立ち)・sweep (槍の薙ぎ)・hurt・death
"""
import math

import numpy as np

from blp import *  # noqa: F401,F403
from blp import keys, eye, punch

BONE = [(96, 88, 72), (140, 130, 110), (184, 174, 150), (214, 206, 186), (236, 230, 214)]
LEATHER = [(34, 22, 16), (56, 36, 24), (82, 54, 36), (110, 76, 52)]
CLOTH = [(52, 18, 16), (82, 30, 24), (116, 46, 34), (146, 66, 46)]
IRON = [(34, 34, 40), (58, 58, 66), (88, 88, 96), (124, 124, 132), (164, 164, 170)]
WOOD = [(40, 26, 16), (62, 42, 26), (88, 60, 38)]
DARK = [(10, 8, 8), (22, 18, 18), (36, 30, 30)]
EMBER = [(150, 46, 16), (210, 84, 24), (255, 146, 40), (255, 210, 96)]


def build():
    m = Mount("bone_lancer", "骨馬の槍騎兵")
    m.bone("body", "root", (0, 22, 0))
    m.bone("neck", "body", (0, 27, -12))
    m.bone("head", "neck", (0, 36, -18))
    m.bone("tail", "body", (0, 26, 13))
    for k, (x, z) in (("fl", (4.2, -9)), ("fr", (-4.2, -9)), ("bl", (4.2, 9.5)), ("br", (-4.2, 9.5))):
        m.bone(f"leg_{k}", "body", (x, 20, z))
    m.bone("rider", "body", (0, 30, 1))
    m.bone("rhead", "rider", (0, 44, 0.4))
    for s, side in ((1, "l"), (-1, "r")):
        m.bone(f"arm_{side}", "rider", (s * 5.4, 41, 0.6))
    # ---- 馬の胴: 背骨と肋の籠 (中は暗い)、腰と胸の骨
    m.add("spine", tub("body", "bone", [(0, 28.5, -12), (0, 29.2, 0), (0, 28.0, 12)], [1.4, 1.5, 1.3], None, sides=6), core=True)
    m.add("cavity", ell("body", "dark", (0, 23.5, 0), (5.4, 4.6, 10.6), segs=10, rings=3))
    for k in range(7):
        z = -9 + k * 3.0
        for s in (1, -1):
            m.add(f"rib{k}{s}", tub("body", "bone", [(s * 0.8, 28.4, z), (s * 5.4, 26.0, z + 0.4), (s * 5.8, 21.0, z + 0.8), (s * 2.6, 18.2, z + 1.0)],
                                    [0.7, 0.75, 0.7, 0.5], None, sides=4, hint=(0, 0, 1)))
    m.add("sternum", tub("body", "bone", [(0, 18.0, -9), (0, 17.6, 4)], [1.0, 0.8], None, sides=5))
    m.add("pelvis", ell("body", "bone", (0, 25.0, 11.0), (5.4, 3.6, 3.6), segs=10, rings=3))
    m.add("chest_bone", ell("body", "bone", (0, 24.0, -10.5), (5.0, 4.4, 3.4), segs=10, rings=3))
    # 馬衣 (肋の上にかけた赤い布、縁は房) と鞍
    m.add("blanket", lof("body", "cloth", [[np.array([math.cos(math.radians(a)) * 6.6, 25.0 + math.sin(math.radians(a)) * 5.4, z]) for a in range(-20, 201, 20)]
                                           for z in (-5, 0, 5)], caps=(False, False)))
    m.add("blanket_in", lof("body", "cloth", [[np.array([math.cos(math.radians(a)) * 6.2, 25.0 + math.sin(math.radians(a)) * 5.0, z]) for a in range(200, -21, -20)]
                                              for z in (-5, 0, 5)], caps=(False, False)))
    for k in range(6):
        z = -4.5 + k * 1.8
        for s in (1, -1):
            m.add(f"fringe{k}{s}", cone("body", "cloth", (s * 6.4, 23.0, z), (s * 0.2, -1, 0), 2.0, 0.5, sides=3))
    m.add("saddle", ell("body", "leather", (0, 30.4, 1.0), (3.6, 1.2, 4.6), segs=10, rings=2))
    m.add("pommel", ell("body", "leather", (0, 31.6, -2.8), (1.2, 1.4, 0.9), segs=7, rings=2))
    # ---- 首と頭骨 (長い鼻づら・眼窩の火・鬣の代わりの骨の棘)
    m.add("neck", tub("neck", "bone", [(0, 27, -12), (0, 32, -15.5), (0, 36, -18)], [2.6, 2.2, 2.0], None, sides=8, hint=(1, 0, 0)))
    for k in range(5):
        m.add(f"mane{k}", cone("neck", "bone", (0, 29 + k * 1.7, -12.4 - k * 1.2 + 2.0), (0, 0.6, 1), 2.4 - k * 0.2, 0.6, sides=4))
    hd = np.array([0, 37.5, -20.5])
    m.add("skull", lof("head", "bone", [rsec(-18.5, 39.8, 35.0, 2.8, n=12), rsec(-21.5, 39.6, 34.6, 2.6, n=12), rsec(-25.0, 38.4, 34.8, 2.0, n=12),
                                        rsec(-27.6, 37.2, 35.0, 1.5, n=12)]), core=True)
    m.add("jaw", lof("head", "bone", [rsec(-19.5, 35.4, 33.4, 2.2, n=10), rsec(-26.6, 35.6, 34.2, 1.3, n=10)]))
    for s in (1, -1):
        m.add(f"socket{s}", ell("head", "dark", hd + np.array([s * 2.4, 1.0, 0.2]), (0.9, 1.0, 1.2), segs=6, rings=2))
        eye(m, "head", hd + np.array([s * 2.5, 1.0, -0.2]), 0.45, (s * 1, 0, -0.3), (255, 140, 40), glow=True)
        m.add(f"ear{s}", cone("head", "bone", hd + np.array([s * 1.6, 2.8, 1.8]), (s * 0.3, 1, 0.4), 2.6, 0.7, sides=4))
        for k in range(3):
            m.add(f"tooth{s}{k}", cone("head", "bone", (s * 1.0, 35.2, -26.0 + k * 1.6), (0, -1, 0), 0.9, 0.35, sides=3))
    m.add("bridle", tub("head", "leather", [(-2.8, 36.6, -23.6), (0, 37.2, -24.0), (2.8, 36.6, -23.6)], [0.35, 0.35, 0.35], None, sides=4))
    # ---- 脚: 骨の脚 (膝の玉なし。曲げた骨) と蹄
    for k, (x, z, back) in (("fl", (4.2, -9, False)), ("fr", (-4.2, -9, False)), ("bl", (4.2, 9.5, True)), ("br", (-4.2, 9.5, True))):
        knee = (x * 1.05, 11.5, z + (1.6 if back else -0.8))
        m.add(f"thigh_{k}", tub(f"leg_{k}", "bone", [(x, 21.5, z), knee], [1.6, 1.1], None, sides=6))
        m.add(f"shin_{k}", tub(f"leg_{k}", "bone", [knee, (x * 1.05, 2.4, z - 0.2)], [1.0, 0.85], None, sides=6))
        m.add(f"hoof_{k}", ell(f"leg_{k}", "iron", (x * 1.05, 1.2, z - 0.6), (1.6, 1.2, 1.9), segs=8, rings=2))
        m.add(f"hip_{k}", ell(f"leg_{k}", "bone", (x, 21.0, z), (2.0, 2.4, 2.4), segs=8, rings=2))
    for k in range(5):
        m.add(f"tail{k}", ell("tail", "bone", (0, 26 - k * 1.8, 13.4 + k * 1.2), (0.9 - k * 0.1, 1.0, 0.9 - k * 0.1), segs=6, rings=2))
    # ---- 騎手: 革の胴と骨の当て、骨の兜と赤い目、外套、左手は手綱、右手に長槍と小旗
    m.add("rtorso", lof("rider", "leather", [rsec(z, 43.5 - abs(z) * 0.2, 31, 5.0 - abs(z) * 0.15, mid=38, n=14) for z in (-3, -1.5, 0, 1.5, 3)]), core=True)
    m.add("rbelt", ell("rider", "iron", (0, 32.6, 0), (5.2, 1.0, 3.4), segs=12, rings=2))
    for k in range(3):
        m.add(f"rrib{k}", tub("rider", "bone", [(-3.8, 41 - k * 2.2, -2.6), (0, 40.4 - k * 2.2, -3.4), (3.8, 41 - k * 2.2, -2.6)], [0.45, 0.5, 0.45], None, sides=4))
    for s in (1, -1):
        m.add(f"rthigh{s}", limb("rider", "leather", [(s * 2.6, 31.5, 0), (s * 6.8, 29.6, -1.2), (s * 7.2, 24.0, -0.4)], [3.2, 3.0, 2.6], [3.2, 3.0, 2.6], n=8))
        m.add(f"rboot{s}", ell("rider", "dark", (s * 7.3, 22.4, -1.2), (1.6, 1.6, 2.6), segs=8, rings=2))
        m.add(f"spaul{s}", ell("rider", "bone", (s * 5.2, 43.2, 0.4), (2.8, 1.4, 2.6), segs=8, rings=2))
        m.add(f"spike{s}", cone("rider", "bone", (s * 6.4, 44.0, 0.4), (s * 0.6, 1, 0), 2.2, 0.6, sides=4))
    m.add("cloak", box("rider", "cloth", (0, 36.5, 3.6), (8.6, 14, 0.6), taper=(0.8, 1.0)))
    rh = np.array([0, 46.6, -0.2])
    m.add("rneck", ell("rhead", "leather", (0, 44.2, 0.2), (1.6, 1.4, 1.6), segs=8, rings=2))
    m.add("rskull", ell("rhead", "bone", rh, (2.3, 2.6, 2.4), segs=10, rings=3))
    m.add("rhelm", lof("rhead", "bone", [oct_ring(rh + np.array([0, 1.6, -2.4]), 5.0, 2.0), oct_ring(rh + np.array([0, 2.4, 0]), 5.6, 3.2),
                                        oct_ring(rh + np.array([0, 1.6, 2.6]), 4.6, 2.6)]))
    m.add("rcrest", tub("rhead", "cloth", [rh + np.array([0, 3.6, -1.6]), rh + np.array([0, 4.6, 1.0]), rh + np.array([0, 3.2, 4.2])], [0.6, 0.8, 0.3], None, sides=4))
    for s in (1, -1):
        eye(m, "rhead", rh + np.array([s * 0.9, 0.2, -2.1]), 0.4, (s * 0.2, 0, -1), (255, 140, 40), glow=True)
    for s, side in ((1, "l"), (-1, "r")):
        sh = np.array([s * 5.4, 41, 0.6])
        el = np.array([s * 6.8, 35.4, -1.6])
        wr = np.array([s * 5.4, 32.4, -5.0])
        m.add(f"rupper_{side}", limb(f"arm_{side}", "leather", [sh, el], [2.8, 2.4], [2.8, 2.4], n=8))
        m.add(f"rlower_{side}", limb(f"arm_{side}", "leather", [el, wr], [2.4, 2.1], [2.4, 2.1], n=8))
        m.add(f"rglove_{side}", ell(f"arm_{side}", "iron", wr, (1.4, 1.5, 1.4), segs=7, rings=2))
    # 長槍 (右手。前へ構え、穂先は前)
    g = np.array([-5.4, 32.4, -5.0])
    m.add("lance", tub("arm_r", "wood", [g + np.array([0, -0.6, 12.0]), g + np.array([0, 0.8, -26.0])], [0.7, 0.55], None, sides=6))
    m.add("vamplate", lof("arm_r", "iron", [oct_ring(g + np.array([0, 0.1, -1.6]), 2.2, 2.2), oct_ring(g + np.array([0, 0.1, -3.4]), 4.4, 4.4)]))
    m.add("lance_tip", cone("arm_r", "iron", g + np.array([0, 0.85, -26.0]), (0, 0.03, -1), 5.0, 1.0, sides=4, rings=2))
    m.add("pennant", slab("arm_r", "cloth", [g + np.array([0, 1.4, -21.0]), g + np.array([0, 4.6, -20.0]), g + np.array([0, 3.0, -15.6]), g + np.array([0, 1.4, -16.0])],
                          (1, 0, 0), 0.3))
    m.add("rein", tub("arm_l", "leather", [np.array([5.4, 32.4, -5.0]), (2.8, 34.6, -15.0), (2.4, 36.6, -23.0)], [0.25, 0.25, 0.25], None, sides=3))

    m.pal = dict(mats={"eye": M_(EMBER, base=0.7, lit=0.2, noise=0.0, min=1), "bone": M_(BONE, lit=0.36, streak=0.05), "leather": M_(LEATHER, lit=0.3),
                       "cloth": M_(CLOTH, lit=0.3, streak=0.06), "iron": M_(IRON, lit=0.4), "wood": M_(WOOD, streak=0.08), "dark": M_(DARK, lit=0.15)},
                 glow_mats=["eye"])

    def detail(c, col, glow):
        P = c["P"]
        sel = np.isin(c["mats"], ["bone"]) & c["owned"]
        f1, f2, idv = cell3(P, 2.4, 7)
        crack = sel & ((f2 - f1) < 0.03) & (idv > 0.45)
        col = np.where(crack[..., None], np.array([70, 62, 50]), col)
        col = stitches(c, col, ["leather"], rgb=(150, 120, 80), every=4)
        col = edge_dark(c, col, ["iron", "bone"], k=0.74)
        return col, glow
    m.detail = detail
    m.hint = 512
    m.view = dict(center=(0, 24, -2), scale=2.2)
    m.gaits = gaits()
    return m



def _merge(*poses):
    out = {}
    for p in poses:
        for b, ch in p.items():
            out.setdefault(b, {}).update(ch)
    return out


def _shake(base, bones, amp, t0, t1, step=0.07, axis=1):
    out = []
    t, k = t0, 0
    while t <= t1 + 1e-6:
        p = _merge(base)
        for b in bones:
            r = list(p.get(b, {}).get("rot", (0, 0, 0)))
            r[axis] += amp if k % 2 == 0 else -amp
            p.setdefault(b, {})["rot"] = tuple(r)
        out.append((round(t, 3), p))
        t += step
        k += 1
    return out

# 作り込み (2026-11-11 の声「他のボスたちもこの方向性で」): 技の動きに溜めの震えと行き過ぎ (blp.punch)、登場 (intro) と後半の咆哮 (scream) を足した

def gaits():
    g = []
    idle = Gait("idle", 3.0)
    idle.rot("neck", W(0).s(4.0, 1, 0, "t"))
    idle.rot("head", W(0).s(3.0, 1, 60, "t"), W(0).s(5.0, 1, 0, "t"))
    idle.rot("tail", None, W(0).s(10, 1, 0, "t"))
    idle.rot("rider", W(0).s(1.5, 1, 30, "t"))
    idle.rot("leg_fl", W(0).s(-6, 1, 0, "t"))
    g.append(idle)
    walk = Gait("walk", 0.9)
    for k, ph in (("fl", 0), ("br", 0), ("fr", 180), ("bl", 180)):
        walk.rot(f"leg_{k}", W(0).s(28, 1, ph, "t"))
    walk.rot("neck", W(0).s(5, 2, 0, "t"))
    walk.rot("tail", W(0).s(8, 2, 0, "t"))
    walk.pos("body", None, W(0).s(-0.8, 2, 90, "t"))
    walk.rot("rider", W(0).s(-3, 2, 0, "t"))
    g.append(walk)
    rest = {}
    couch = {"neck": {"rot": (14, 0, 0)}, "rider": {"rot": (14, 0, 0)}, "arm_r": {"rot": (-6, 0, 0)}, "rhead": {"rot": (-10, 0, 0)}}
    gal1 = dict(couch, leg_fl={"rot": (-40, 0, 0)}, leg_fr={"rot": (-30, 0, 0)}, leg_bl={"rot": (35, 0, 0)}, leg_br={"rot": (25, 0, 0)})
    gal2 = dict(couch, leg_fl={"rot": (30, 0, 0)}, leg_fr={"rot": (40, 0, 0)}, leg_bl={"rot": (-30, 0, 0)}, leg_br={"rot": (-40, 0, 0)})
    g.append(keys("charge", 2.2, [(0.0, rest), (1.0, couch), (1.25, gal1), (1.45, gal2), (1.65, gal1), (1.85, gal2), (2.2, rest)]))
    up = {"body": {"rot": (-30, 0, 0), "pos": (0, 3, 2)}, "leg_fl": {"rot": (-70, 0, 0)}, "leg_fr": {"rot": (-50, 0, 0)}, "leg_bl": {"rot": (25, 0, 0)},
          "leg_br": {"rot": (25, 0, 0)}, "neck": {"rot": (-20, 0, 0)}, "rider": {"rot": (20, 0, 0)}, "arm_r": {"rot": (-70, 0, 0)}}
    down = {"body": {"rot": (8, 0, 0), "pos": (0, -1, 0)}, "leg_fl": {"rot": (-10, 0, 0)}, "leg_fr": {"rot": (-8, 0, 0)}, "neck": {"rot": (20, 0, 0)},
            "rider": {"rot": (-6, 0, 0)}, "arm_r": {"rot": (10, 0, 0)}}
    g.append(keys("rear", 1.8, punch([(0.0, rest), (0.9, up), (1.05, down), (1.3, down), (1.8, rest)], 2)))
    wind = {"rider": {"rot": (0, 50, 0)}, "arm_r": {"rot": (-30, 30, 30)}, "neck": {"rot": (0, -10, 0)}}
    cut = {"rider": {"rot": (6, -55, 0)}, "arm_r": {"rot": (-20, -40, 20)}, "neck": {"rot": (0, 10, 0)}}
    g.append(keys("sweep", 1.2, punch([(0.0, rest), (0.6, wind), (0.75, cut), (0.85, cut), (1.2, rest)], 2)))
    # 登場 (3.8 秒): 骨の馬が乗り手ごと床を割って棹立ちに迫り上がり、前脚を叩きつけ、乗り手が槍を天へ掲げる (名前の札 2.40 秒 = introCardAt 48)
    lift_ = _merge(up, {"arm_r": {"rot": (-150, 0, 0)}, "rhead": {"rot": (-20, 0, 0)}, "neck": {"rot": (-30, 0, 0)}})
    g.append(keys("intro", 3.8, [(0.0, _merge(up, {"root": {"pos": (0, -50, 0)}})), (0.8, _merge(up, {"root": {"pos": (0, -30, 0)}})),
                                 (1.5, _merge(up, {"root": {"pos": (0, -6, 0)}})), (1.8, down), (1.95, _merge(down, {"body": {"pos": (0, -2, 0)}})),
                                 (2.4, lift_)] + _shake(lift_, ["neck", "rhead"], 4, 2.46, 3.0) + [(3.3, down), (3.8, rest)]))
    g.append(keys("scream", 1.6, [(0.0, rest), (0.32, lift_)] + _shake(lift_, ["neck", "rhead"], 4, 0.38, 1.04) + [(1.2, down), (1.6, rest)]))
    g.append(keys("hurt", 0.3, [(0.0, rest), (0.1, {"neck": {"rot": (-14, 0, 0)}, "rider": {"rot": (-10, 0, 6)}}), (0.3, rest)]))
    fold = {"body": {"pos": (0, -10, 0), "rot": (0, 0, 20)}, "leg_fl": {"rot": (-80, 0, 0)}, "leg_fr": {"rot": (-80, 0, 0)}, "leg_bl": {"rot": (70, 0, 0)},
            "leg_br": {"rot": (70, 0, 0)}, "neck": {"rot": (40, 0, 0)}, "rider": {"rot": (30, 0, -30)}}
    g.append(keys("death", 2.0, [(0.0, rest), (0.6, {"neck": {"rot": (-20, 0, 0)}, "body": {"rot": (-10, 0, 0)}}), (1.4, fold), (2.0, fold)], hold=True))
    return g


POSES = [("idle", 0.0), ("walk", 0.3), ("charge", 1.25), ("rear", 0.9), ("rear", 1.05), ("sweep", 0.75), ("hurt", 0.1), ("death", 2.0), ("intro", 1.5), ("intro", 2.4), ("scream", 0.5)]
BOUNDS = (6, 6)
