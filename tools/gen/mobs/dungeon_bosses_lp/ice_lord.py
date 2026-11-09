"""氷鎧の大熊 (ice_lord)。吹雪の氷穴の主 (docs/design/55 の 7 章。2026-11-09 の頁で A)。
2026-11-09 の声「ボスのモデルもラフのままだし、めっちゃ小さい。クオリティ低い」で、ラフ (tools/rough/lowpoly/late_foes) から作り直した。
肩の盛り上がった白い大熊が、凍りついた氷の鎧をまとう。背には氷柱の列、頭には氷の兜と青く光る目、前脚に氷の籠手と黒い爪、
毛先には霜。雷の土地の主なので、氷の芯に雷の光が通る。

骨: body の子に head (→ jaw)・arm_l / arm_r (前脚)・leg_l / leg_r (後ろ脚)・tail。
動き: idle・walk・swipe (薙ぎ払い)・roar (立ち上がって吠え、氷柱を落とす)・charge (氷上の突進)・hurt・death
"""
import math

import numpy as np

from blp import *  # noqa: F401,F403
from blp import keys, eye

FURW = [(120, 126, 138), (160, 166, 178), (196, 202, 212), (226, 232, 238), (246, 248, 252)]
FURD = [(70, 76, 90), (100, 108, 122), (132, 140, 154)]
ICE = [(70, 120, 170), (110, 170, 220), (160, 210, 244), (210, 238, 255)]
DEEP = [(30, 60, 110), (46, 90, 150), (70, 124, 190)]
CLAW = [(14, 14, 18), (30, 30, 36), (52, 52, 60)]
NOSE = [(16, 16, 20), (34, 32, 36), (54, 50, 54)]
BOLT = [(80, 160, 255), (150, 210, 255), (220, 244, 255), (255, 255, 255)]


def crystal(m, name, bone, base, d, length, r, mat="ice"):
    """氷柱: 六角の柱の先を尖らせる (根元は体に沈める)"""
    m.add(name, cone(bone, mat, base, d, length, r, sides=6, rings=3))


def build():
    m = Mount("ice_lord", "氷鎧の大熊")
    m.bone("body", "root", (0, 28, 4))
    m.bone("head", "body", (0, 34, -22))
    m.bone("jaw", "head", (0, 30.5, -27))
    m.bone("tail", "body", (0, 30, 27))
    for s, side in ((1, "l"), (-1, "r")):
        m.bone(f"arm_{side}", "body", (s * 9.5, 31, -12))
        m.bone(f"leg_{side}", "body", (s * 9.0, 30, 18))
    # ---- 胴: 肩の盛り上がり (こぶ) から腰へ下がる太い胴
    secs = []
    for z, top, bot, w in ((-20, 38, 18, 10.0), (-14, 44, 15, 13.0), (-6, 45, 14, 14.0), (2, 43, 14, 13.6), (10, 41, 15, 13.0),
                           (18, 39, 17, 12.0), (24, 36, 20, 10.0), (28, 32, 24, 6.0)):
        secs.append(rsec(z, top, bot, w, mid=(top + bot) / 2 + 1, n=16, p=2.1))
    m.add("torso", lof("body", "fur", secs), core=True)
    m.add("chest_ruff", ell("body", "fur", (0, 26, -18), (9.0, 9.0, 5.6), segs=12, rings=3))
    for k in range(10):
        a = math.radians(-60 + k * 13)
        p = np.array([math.sin(a) * 9.6, 20 + math.cos(a) * 2.0 - abs(k - 4.5) * 0.4, -19.0])
        m.add(f"ruff_tuft{k}", cone("body", "fur", p, (math.sin(a) * 0.4, -1, -0.5), 3.4, 1.4, sides=4))
    for k in range(12):
        z = -12 + k * 3.4
        for s in (1, -1):
            m.add(f"belly_tuft{k}{s}", cone("body", "fur_d", (s * (8.0 - abs(z) * 0.05), 15.6 + abs(z - 4) * 0.04, z), (s * 0.3, -1, 0.2), 2.6, 1.2, sides=4))
    # ---- 氷の鎧: 肩の鞍の板 (重ねた三枚)・背の氷柱の列・腰の板
    for k in range(3):
        z = -12 + k * 6.5
        m.add(f"plate{k}", lof("body", "ice", [rsec(z - 2.8, 46.6 - k * 0.6, 34, 14.6 - k * 0.4, mid=38, n=14, p=2.4),
                                               rsec(z + 2.8, 46.0 - k * 0.6, 34.6, 14.2 - k * 0.4, mid=38, n=14, p=2.4)]))
        m.add(f"plate_rim{k}", lof("body", "deep", [rsec(z + 2.9, 46.1 - k * 0.6, 34.8, 14.3 - k * 0.4, mid=38, n=14, p=2.4),
                                                    rsec(z + 3.5, 45.6 - k * 0.6, 35.4, 13.8 - k * 0.4, mid=38, n=14, p=2.4)]))
    for k in range(9):
        z = -15 + k * 4.6
        h = 9.0 - abs(k - 2.5) * 0.9
        crystal(m, f"spike{k}", "body", (0, 44.5 - max(0, k - 3) * 0.7, z), (0, 1, 0.25), max(3.0, h), 1.6 - k * 0.06)
        if k % 2 == 0:
            for s in (1, -1):
                crystal(m, f"spike_side{k}{s}", "body", (s * 6.0, 42.0 - max(0, k - 3) * 0.7, z + 0.8), (s * 0.7, 1, 0.2), max(2.4, h * 0.55), 1.0)
    m.add("hip_plate", lof("body", "ice", [rsec(18, 41.6, 31, 12.4, mid=34, n=14, p=2.4), rsec(24, 38.6, 30, 10.4, mid=33, n=14, p=2.4)]))
    # 鎧の縫い目の雷 (光る細い帯)
    for s in (1, -1):
        m.add(f"vein{s}", tub("body", "bolt", [(s * 13.6, 38, -14), (s * 14.0, 36, -7), (s * 13.4, 38, 0), (s * 13.0, 36, 6)], [0.35] * 4, None, sides=4))
    # ---- 頭: 太い首、広い頭、鼻づら、耳、氷の兜
    m.add("neck", lof("head", "fur", [rsec(-15, 38, 18, 9.0, n=14), rsec(-21, 37, 22, 7.6, n=14), rsec(-25, 37, 26, 6.6, n=14)]))
    head = np.array([0, 33.5, -27.5])
    m.add("skull", ell("head", "fur", head, (6.0, 5.6, 6.0), segs=12, rings=4), core=True)
    m.add("muzzle", lof("head", "fur", [rsec(-30, 35, 27.5, 4.6, n=12), rsec(-34, 33.6, 28.0, 3.6, n=12), rsec(-36.4, 32.4, 28.6, 2.8, n=12)]))
    m.add("nose", ell("head", "nose", (0, 32.6, -36.6), (1.6, 1.1, 0.9), segs=8, rings=2))
    for s in (1, -1):
        m.add(f"ear{s}", ell("head", "fur", head + np.array([s * 4.4, 4.6, 2.0]), (1.8, 2.0, 1.2), segs=8, rings=2))
        m.add(f"ear_in{s}", ell("head", "fur_d", head + np.array([s * 4.4, 4.6, 1.2]), (1.1, 1.3, 0.5), segs=7, rings=2))
        eye(m, "head", head + np.array([s * 2.8, 1.6, -5.0]), 0.75, (s * 0.3, 0.1, -1), (120, 220, 255), glow=True)
        m.add(f"cheek_tuft{s}", cone("head", "fur", head + np.array([s * 5.2, -2.2, -1.0]), (s * 1, -0.5, 0.2), 3.0, 1.4, sides=4))
    # 氷の兜: 額から首へ流れる板と、二本の氷の角
    m.add("helm", lof("head", "ice", [oct_ring(head + np.array([0, 3.6, -4.6]), 8.0, 3.0), oct_ring(head + np.array([0, 5.2, -1.4]), 10.4, 4.0),
                                      oct_ring(head + np.array([0, 4.8, 2.8]), 10.0, 4.4), oct_ring(head + np.array([0, 3.4, 6.0]), 7.0, 3.6)]))
    m.add("helm_ridge", tub("head", "deep", [head + np.array([0, 5.6, -5.2]), head + np.array([0, 7.6, -0.6]), head + np.array([0, 6.6, 5.4])], [0.9, 1.1, 0.7], None, sides=5))
    for s in (1, -1):
        crystal(m, f"horn{s}", "head", head + np.array([s * 3.6, 6.0, 0.0]), (s * 0.6, 1, 0.5), 7.0, 1.3)
        crystal(m, f"horn_small{s}", "head", head + np.array([s * 4.8, 4.6, 2.6]), (s * 1, 0.6, 0.6), 3.6, 0.8)
    # あご: 下あごと牙
    m.add("jaw", lof("jaw", "fur", [rsec(-28, 30.4, 27.6, 4.0, n=10), rsec(-33, 30.0, 28.0, 3.0, n=10), rsec(-35.6, 29.6, 28.4, 2.2, n=10)]))
    for s in (1, -1):
        m.add(f"fang{s}", cone("head", "claw_w", (s * 1.8, 30.6, -35.0), (0, -1, -0.1), 2.0, 0.55, sides=4))
        m.add(f"lfang{s}", cone("jaw", "claw_w", (s * 1.6, 29.8, -34.6), (0, 1, -0.1), 1.4, 0.45, sides=4))
    # ---- 脚: 太い前脚 (氷の籠手と黒い爪)、後ろ脚
    for s, side in ((1, "l"), (-1, "r")):
        top = np.array([s * 9.5, 31, -12])
        elb = np.array([s * 10.4, 17, -13.6])
        paw = np.array([s * 10.2, 3.4, -15.0])
        m.add(f"fore_up_{side}", limb(f"arm_{side}", "fur", [top, (top + elb) / 2, elb], [9.0, 8.2, 7.0], [10.0, 8.8, 7.4], n=12))
        m.add(f"fore_lo_{side}", limb(f"arm_{side}", "fur", [elb, (elb + paw) / 2, paw], [7.0, 6.6, 6.6], [7.4, 7.0, 7.0], n=12))
        m.add(f"gaunt_{side}", lof(f"arm_{side}", "ice", [oct_ring(paw + np.array([0, 3.0, 0]), 8.0, 8.0, R=R_(90, 0, 0)),
                                                         oct_ring(paw + np.array([0, 8.6, 0.4]), 8.4, 8.6, R=R_(90, 0, 0)),
                                                         oct_ring(paw + np.array([0, 11.0, 0.6]), 7.6, 8.0, R=R_(90, 0, 0))]))
        crystal(m, f"elbow_spike_{side}", f"arm_{side}", elb + np.array([0, 1.0, 3.6]), (s * 0.2, 0.4, 1), 4.0, 1.2)
        m.add(f"paw_{side}", ell(f"arm_{side}", "fur", paw + np.array([0, -1.6, -1.2]), (4.4, 2.4, 5.0), segs=10, rings=2))
        for k in range(4):
            m.add(f"claw_{side}{k}", cone(f"arm_{side}", "claw", paw + np.array([(k - 1.5) * 2.1, -2.0, -5.4]), (0, -0.5, -1), 2.6, 0.7, sides=4, rings=2, bend=(0, -0.8, 0)))
        hip = np.array([s * 9.0, 30, 18])
        knee = np.array([s * 9.8, 17, 15.0])
        hpaw = np.array([s * 9.6, 3.4, 18.6])
        m.add(f"thigh_{side}", limb(f"leg_{side}", "fur", [hip, (hip + knee) / 2, knee], [10.0, 9.0, 7.0], [12.0, 10.6, 8.0], n=12))
        m.add(f"hshin_{side}", limb(f"leg_{side}", "fur", [knee, (knee + hpaw) / 2, hpaw], [6.8, 6.4, 6.2], [7.6, 7.0, 6.8], n=12))
        m.add(f"hpaw_{side}", ell(f"leg_{side}", "fur", hpaw + np.array([0, -1.6, -1.6]), (4.0, 2.2, 5.0), segs=10, rings=2))
        for k in range(4):
            m.add(f"hclaw_{side}{k}", cone(f"leg_{side}", "claw", hpaw + np.array([(k - 1.5) * 1.9, -2.2, -6.0]), (0, -0.4, -1), 2.0, 0.6, sides=4))
        m.add(f"knee_plate_{side}", ell(f"leg_{side}", "ice", knee + np.array([s * 1.6, 1.0, -3.2]), (3.2, 3.4, 1.6), segs=8, rings=2))
    m.add("tail", ell("tail", "fur", (0, 30, 29), (2.6, 2.4, 2.6), segs=8, rings=2))

    m.pal = dict(mats={"eye": M_(BOLT, base=0.7, lit=0.2, noise=0.0, min=1), "fur": M_(FURW, lit=0.32), "fur_d": M_(FURD, lit=0.3), "ice": M_(ICE, lit=0.42),
                       "deep": M_(DEEP, lit=0.36), "claw": M_(CLAW, lit=0.3), "claw_w": M_([(170, 170, 160), (210, 208, 196), (236, 234, 224)], lit=0.36),
                       "nose": M_(NOSE, lit=0.2), "bolt": M_(BOLT, base=0.7, lit=0.2, min=1)}, glow_mats=["eye", "bolt"])

    def detail(c, col, glow):
        P = c["P"]
        col = fur(c, col, ["fur", "fur_d"], amt=0.18)
        sel = np.isin(c["mats"], ["ice"]) & c["owned"]
        f1, f2, idv = cell3(P, 2.6, 13)
        facet = sel & ((f2 - f1) < 0.05)
        col = np.where(facet[..., None], np.array([236, 248, 255]), col)
        core = sel & (idv > 0.88) & (f1 < 0.25)
        col = np.where(core[..., None], np.array([150, 214, 255]), col)
        frost = np.isin(c["mats"], ["fur"]) & c["owned"] & (P[..., 1] > 40)
        col = np.where(frost[..., None], np.clip(col * 1.06, 0, 255).astype(col.dtype), col)
        col = edge_dark(c, col, ["deep"], k=0.75)
        return col, glow | core
    m.detail = detail
    m.hint = 512
    m.view = dict(center=(0, 24, 0), scale=2.0)
    m.gaits = gaits()
    return m


def gaits():
    g = []
    idle = Gait("idle", 4.0)
    idle.rot("head", W(0).s(4.0, 1, 0, "t"), W(0).s(8.0, 1, 90, "t"))
    idle.pos("body", None, W(0).s(0.3, 1, 0, "t"))
    idle.rot("jaw", W(0).s(-3.0, 2, 0, "t"))
    idle.rot("tail", None, W(0).s(10, 1, 0, "t"))
    g.append(idle)
    walk = Gait("walk", 1.3)
    walk.rot("arm_l", W(0).s(20, 1, 0, "t"))
    walk.rot("arm_r", W(0).s(20, 1, 180, "t"))
    walk.rot("leg_l", W(0).s(20, 1, 180, "t"))
    walk.rot("leg_r", W(0).s(20, 1, 0, "t"))
    walk.rot("body", None, W(0).s(3, 1, 0, "t"), W(0).s(2, 1, 90, "t"))
    walk.rot("head", W(0).s(3, 2, 0, "t"), W(0).s(-4, 1, 0, "t"))
    walk.pos("body", None, W(0).s(-0.6, 2, 90, "t"))
    g.append(walk)
    rest = {}
    wind = {"arm_r": {"rot": (-60, 0, -30)}, "body": {"rot": (0, 18, 0)}, "head": {"rot": (0, 12, 0)}}
    swipe = {"arm_r": {"rot": (-50, 0, 40)}, "body": {"rot": (6, -24, 0)}, "head": {"rot": (0, -16, 0)}}
    g.append(keys("swipe", 1.1, [(0.0, rest), (0.6, wind), (0.8, swipe), (0.9, swipe), (1.1, rest)]))
    rear = {"body": {"rot": (-38, 0, 0), "pos": (0, 4, 4)}, "leg_l": {"rot": (38, 0, 0)}, "leg_r": {"rot": (38, 0, 0)},
            "arm_l": {"rot": (-50, 0, 14)}, "arm_r": {"rot": (-50, 0, -14)}, "head": {"rot": (14, 0, 0)}, "jaw": {"rot": (30, 0, 0)}}
    g.append(keys("roar", 1.8, [(0.0, rest), (0.8, rear), (1.4, rear), (1.8, rest)]))
    low = {"body": {"rot": (8, 0, 0)}, "head": {"rot": (10, 0, 0)}}
    r1 = dict(low, arm_l={"rot": (-40, 0, 0)}, arm_r={"rot": (30, 0, 0)}, leg_l={"rot": (30, 0, 0)}, leg_r={"rot": (-40, 0, 0)})
    r2 = dict(low, arm_l={"rot": (30, 0, 0)}, arm_r={"rot": (-40, 0, 0)}, leg_l={"rot": (-40, 0, 0)}, leg_r={"rot": (30, 0, 0)})
    g.append(keys("charge", 1.6, [(0.0, rest), (0.9, low), (1.05, r1), (1.2, r2), (1.35, r1), (1.6, rest)]))
    g.append(keys("hurt", 0.3, [(0.0, rest), (0.1, {"body": {"rot": (-6, 0, 4)}, "head": {"rot": (-12, 0, 0)}}), (0.3, rest)]))
    slump = {"body": {"pos": (0, -8, 0), "rot": (0, 0, 10)}, "arm_l": {"rot": (-40, 0, 30)}, "arm_r": {"rot": (-30, 0, -20)}, "head": {"rot": (20, 0, 0)}}
    side = {"body": {"pos": (0, -14, 0), "rot": (0, 0, 80)}, "arm_l": {"rot": (-30, 0, 30)}, "arm_r": {"rot": (-30, 0, -10)}, "leg_l": {"rot": (-20, 0, 0)},
            "head": {"rot": (10, 0, -20)}, "jaw": {"rot": (16, 0, 0)}}
    g.append(keys("death", 2.0, [(0.0, rest), (0.6, slump), (1.4, side), (2.0, side)], hold=True))
    return g


POSES = [("idle", 0.0), ("walk", 0.3), ("swipe", 0.6), ("swipe", 0.8), ("roar", 0.8), ("charge", 1.05), ("hurt", 0.1), ("death", 2.0)]
BOUNDS = (7, 6)
