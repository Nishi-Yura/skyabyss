"""炉負いの巨人 (forge_giant)。熔けた坑道の主 (docs/design/55 の 1 章、00 の 367 = A 炉負いの巨人)。
前かがみの玄武岩の巨人。背に大きな炉と煙突 (中が光る)、右手は鎚と一体、肌の割れ目から熔けた光。

骨: body (腰) の子に torso (胸)・leg_l / leg_r。torso の子に head・forge (背の炉)・arm_l → fore_l、arm_r → fore_r (鎚)。
動き: idle・walk・hammer (振り下ろし)・sweep (横に薙ぐ)・stomp (足踏みで熔岩の輪)・blast (炉から火を噴き上げる)・hurt・death
"""
import math

import numpy as np

from blp import *  # noqa: F401,F403
from blp import keys, eye

BAS = [(24, 22, 24), (38, 34, 36), (54, 48, 50), (72, 64, 64), (94, 84, 82)]
MAG = [(160, 50, 16), (220, 96, 30), (252, 156, 56), (255, 214, 120), (255, 244, 200)]
IRON = [(40, 36, 36), (62, 56, 54), (88, 80, 76), (118, 108, 100)]
BRICK = [(80, 34, 24), (112, 50, 34), (146, 70, 46), (176, 96, 66)]
HUNCH = 1.2


def build():
    m = Mount("forge_giant", "炉負いの巨人")
    m.bone("body", "root", (0, 34, 2))
    m.bone("torso", "body", (0, 38, 2))
    m.bone("head", "torso", (0, 54, -12))
    m.bone("forge", "torso", (0, 46, 12))
    for s, side in ((1, "l"), (-1, "r")):
        m.bone(f"leg_{side}", "body", (s * 7, 34, 2))
        m.bone(f"arm_{side}", "torso", (s * 15, 56, -2))
        m.bone(f"fore_{side}", f"arm_{side}", (s * 21, 42, -6))
    # 胴: 前かがみの厚い胸、腰
    torso = [rsec(z, 62 - abs(z + 2) * 0.6 * HUNCH, 34, 14.5 - abs(z) * 0.1, mid=48, n=16) for z in (-10, -5, 0, 5, 10)]
    m.add("chest", lof("torso", "basalt", torso), core=True)
    m.add("belly", ell("torso", "basalt", (0, 40, -4), (12, 7, 8), segs=12, rings=3))
    m.add("hips", ell("body", "basalt", (0, 34, 2), (12.5, 6.5, 9.5), segs=12, rings=3), core=True)
    m.add("belt", ell("body", "iron", (0, 36, 2), (13.4, 2.0, 10.2), segs=14, rings=2))
    # 頭: 低く前に突き出た岩の頭、光る目、あごの岩
    head = np.array([0, 60 - 6 * HUNCH, -10 - 4 * HUNCH])
    m.add("skull", ell("head", "basalt", head, (5.4, 5.0, 5.4), segs=10, rings=3), core=True)
    m.add("brow", ell("head", "basalt", head + np.array([0, 2.2, -2.6]), (5.8, 1.8, 3.0), segs=10, rings=2))
    m.add("chin", ell("head", "basalt", head + np.array([0, -3.4, -2.2]), (4.2, 2.4, 3.4), segs=9, rings=2))
    for s in (1, -1):
        eye(m, "head", head + np.array([s * 2.3, 0.8, -4.5]), 0.95, (s * 0.3, 0, -1), (255, 160, 60), glow=True)
        m.add(f"cheek{s}", ell("head", "basalt", head + np.array([s * 4.2, -1.0, -1.2]), (2.2, 2.6, 2.6), segs=8, rings=2))
    # 脚: 太い柱の脚と岩の足
    for s, side in ((1, "l"), (-1, "r")):
        m.add(f"thigh_{side}", limb(f"leg_{side}", "basalt", [(s * 7, 34, 2), (s * 8.5, 20, 0), (s * 8.5, 8, 2)], [9.5, 8.5, 7.5], [9.5, 8.5, 7.5], n=12))
        m.add(f"knee_{side}", ell(f"leg_{side}", "iron", (s * 8.8, 20, -2.6), (4.2, 4.0, 2.2), segs=8, rings=2))
        m.add(f"foot_{side}", ell(f"leg_{side}", "basalt", (s * 8.5, 3, -1.5), (5.6, 3.2, 7.6), segs=10, rings=2))
        for k in range(3):
            m.add(f"toe_{side}{k}", ell(f"leg_{side}", "basalt", (s * 8.5 + (k - 1) * 2.6, 1.8, -8.0), (1.6, 1.6, 2.0), segs=6, rings=2))
    # 腕: 肩の鉄の当て、上の腕 (arm)、前の腕 (fore)。右手は鎚と一体、左手は大きな拳
    for s, side in ((1, "l"), (-1, "r")):
        sh = np.array([s * 15, 56, -2])
        el = np.array([s * 21, 42, -6])
        wr = np.array([s * 22, 28, -8])
        m.add(f"upper_{side}", limb(f"arm_{side}", "basalt", [sh, (sh + el) / 2, el + (el - sh) * 0.15], [9.5, 8.5, 7.8], [9.5, 8.5, 7.8], n=12))
        m.add(f"pauldron_{side}", ell(f"arm_{side}", "iron", sh + np.array([s * 1.2, 3.2, 0]), (7.6, 5.2, 7.6), segs=10, rings=3))
        m.add(f"lower_{side}", limb(f"fore_{side}", "basalt", [el - (el - sh) * 0.15, (el + wr) / 2, wr], [8.0, 7.4, 6.8], [8.0, 7.4, 6.8], n=12))
        m.add(f"bracer_{side}", ell(f"fore_{side}", "iron", (el + wr) / 2 + np.array([0, -1, 0]), (5.0, 4.0, 5.0), segs=10, rings=2))
        if side == "r":
            m.add("haft", tub("fore_r", "iron", [wr + np.array([0, -3, 0]), wr + np.array([0, -3, -19])], [1.8, 1.8], None, sides=7))
            m.add("hammer", lof("fore_r", "iron", [oct_ring(wr + np.array([-8, -3, -22]), 12, 12, R=rot_y(90)), oct_ring(wr + np.array([-6, -3, -22]), 13, 13, R=rot_y(90)),
                                                    oct_ring(wr + np.array([6, -3, -22]), 13, 13, R=rot_y(90)), oct_ring(wr + np.array([8, -3, -22]), 12, 12, R=rot_y(90))]))
            m.add("hammer_glow", ell("fore_r", "magma", wr + np.array([0, -3, -28.6]), (4.0, 4.0, 0.9), segs=8, rings=1))
            m.add("fist_r", ell("fore_r", "basalt", wr + np.array([0, -3, 0]), (5.4, 5.4, 5.4), segs=9, rings=2))
        else:
            m.add("fist_l", ell("fore_l", "basalt", wr + np.array([0, -3.5, 0]), (6.2, 6.0, 6.2), segs=10, rings=3))
            for k in range(4):
                m.add(f"knuckle{k}", ell("fore_l", "basalt", wr + np.array([(k - 1.5) * 2.4, -6.5, -3.0]), (1.6, 1.6, 1.6), segs=6, rings=2))
    # 背の炉: 煉瓦の胴、火の口、煙突、鉄の帯
    m.add("forge_body", lof("forge", "brick", [oct_ring((0, 46, 13), 19, 23, R=np.eye(3)), oct_ring((0, 46, 21), 18, 22, R=np.eye(3)),
                                               oct_ring((0, 46, 27), 15, 19, R=np.eye(3))]))
    m.add("forge_band", lof("forge", "iron", [oct_ring((0, 46, 19.5), 19.6, 23.6, R=np.eye(3)), oct_ring((0, 46, 21.5), 19.6, 23.6, R=np.eye(3))]))
    m.add("forge_mouth", ell("forge", "magma", (0, 43, 27.4), (5.5, 5.0, 1.0), segs=10, rings=1))
    m.add("forge_grate", box("forge", "iron", (0, 38.5, 27.6), (12, 1.6, 1.4)))
    m.add("chimney", tub("forge", "brick", [(4.5, 56, 20), (5.0, 66, 21.5), (5.5, 76, 23)], [3.6, 3.2, 2.8], None, sides=7))
    m.add("chimney_cap", ell("forge", "iron", (5.5, 76.4, 23), (4.0, 1.2, 4.0), segs=8, rings=1))
    m.add("smoke_glow", ell("forge", "magma", (5.5, 77.2, 23), (2.6, 0.8, 2.6), segs=6, rings=1))
    m.pal = dict(mats={"eye": M_(EYE, base=0.5, lit=0.2, noise=0.0, min=0), "basalt": M_(BAS, lit=0.34, streak=0.05), "magma": M_(MAG, base=0.66, lit=0.2),
                       "iron": M_(IRON, lit=0.36), "brick": M_(BRICK, streak=0.04)}, glow_mats=["magma"])

    def detail(c, col, glow):
        P = c["P"]
        sel = np.isin(c["mats"], ["basalt"]) & c["owned"]
        f1, f2, idv = cell3(P, 4.0, 17)
        crack = sel & ((f2 - f1) < 0.035) & (idv > 0.5)
        col = np.where(crack[..., None], np.array([250, 140, 50]), col)
        col = edge_dark(c, col, ["iron"], k=0.75)
        return col, glow | crack
    m.detail = detail
    m.hint = 512
    m.view = dict(center=(0, 38, 4), scale=1.8)
    m.gaits = gaits()
    return m


# ---------------------------------------------------------------- 動き
def gaits():
    g = []
    idle = Gait("idle", 3.4)
    idle.rot("torso", W(0).s(1.5, 1, 0, "t"))
    idle.rot("head", W(0).s(2.0, 1, 60, "t"), W(0).s(3.0, 1, 0, "t"))
    idle.rot("arm_l", W(0).s(2.0, 1, 30, "t"))
    idle.rot("arm_r", W(0).s(2.0, 1, 210, "t"))
    idle.pos("torso", None, W(0).s(0.4, 1, 0, "t"))
    g.append(idle)
    walk = Gait("walk", 1.6)
    walk.rot("leg_l", W(0).s(16, 1, 0, "t"))
    walk.rot("leg_r", W(0).s(16, 1, 180, "t"))
    walk.rot("arm_l", W(0).s(-10, 1, 0, "t"))
    walk.rot("arm_r", W(0).s(-10, 1, 180, "t"))
    walk.rot("torso", None, W(0).s(4, 1, 0, "t"), W(0).s(2, 1, 90, "t"))
    walk.pos("body", None, W(0).s(-0.8, 2, 90, "t"))
    g.append(walk)
    rest = {}
    raise_ = {"torso": {"rot": (-14, 0, 0)}, "arm_r": {"rot": (-150, 0, -10)}, "fore_r": {"rot": (-30, 0, 0)}, "arm_l": {"rot": (-20, 0, 10)}, "head": {"rot": (-8, 0, 0)}}
    smash = {"torso": {"rot": (24, 0, 0)}, "arm_r": {"rot": (-40, 0, -6)}, "fore_r": {"rot": (-10, 0, 0)}, "arm_l": {"rot": (10, 0, 0)}, "head": {"rot": (10, 0, 0)},
             "body": {"pos": (0, -2, 0)}}
    g.append(keys("hammer", 1.6, [(0.0, rest), (0.7, raise_), (0.9, smash), (1.2, smash), (1.6, rest)]))
    wind = {"torso": {"rot": (0, 40, 0)}, "arm_r": {"rot": (-70, 50, 0)}, "fore_r": {"rot": (-20, 0, 0)}, "arm_l": {"rot": (-10, 0, 0)}}
    swipe = {"torso": {"rot": (0, -45, 0)}, "arm_r": {"rot": (-70, -50, 0)}, "fore_r": {"rot": (-10, 0, 0)}, "arm_l": {"rot": (-10, 0, 0)}}
    g.append(keys("sweep", 1.4, [(0.0, rest), (0.6, wind), (0.85, swipe), (1.05, swipe), (1.4, rest)]))
    lift = {"leg_l": {"rot": (-40, 0, 0)}, "torso": {"rot": (-6, 0, 6)}, "arm_l": {"rot": (-20, 0, 20)}, "arm_r": {"rot": (-20, 0, -20)}}
    stamp = {"leg_l": {"rot": (4, 0, 0)}, "torso": {"rot": (10, 0, -2)}, "body": {"pos": (0, -2, 0)}}
    g.append(keys("stomp", 1.3, [(0.0, rest), (0.55, lift), (0.75, stamp), (1.0, stamp), (1.3, rest)]))
    bow = {"torso": {"rot": (34, 0, 0)}, "head": {"rot": (-20, 0, 0)}, "arm_l": {"rot": (30, 0, 14)}, "arm_r": {"rot": (30, 0, -14)}, "forge": {"rot": (-6, 0, 0)}}
    vent = {"torso": {"rot": (40, 0, 0)}, "head": {"rot": (-26, 0, 0)}, "arm_l": {"rot": (36, 0, 16)}, "arm_r": {"rot": (36, 0, -16)}, "forge": {"rot": (4, 0, 0)}}
    g.append(keys("blast", 1.8, [(0.0, rest), (0.6, bow), (0.8, vent), (1.0, bow), (1.2, vent), (1.4, bow), (1.8, rest)]))
    g.append(keys("hurt", 0.3, [(0.0, rest), (0.12, {"torso": {"rot": (-6, 0, 3)}, "head": {"rot": (-8, 0, 0)}}), (0.3, rest)]))
    kneel = {"body": {"pos": (0, -10, 0)}, "leg_l": {"rot": (-80, 0, 0)}, "leg_r": {"rot": (10, 0, 0)}, "torso": {"rot": (30, 0, 0)}, "head": {"rot": (20, 0, 0)},
             "arm_l": {"rot": (20, 0, 10)}, "arm_r": {"rot": (-30, 0, -20)}}
    fall = {"body": {"pos": (0, -14, -6), "rot": (60, 0, 0)}, "leg_l": {"rot": (-40, 0, 0)}, "leg_r": {"rot": (-30, 0, 0)}, "torso": {"rot": (20, 0, 0)},
            "head": {"rot": (10, 0, 0)}, "arm_l": {"rot": (-60, 0, 30)}, "arm_r": {"rot": (-80, 0, -30)}}
    g.append(keys("death", 2.0, [(0.0, rest), (0.6, kneel), (1.0, kneel), (1.7, fall), (2.0, fall)], hold=True))
    return g


POSES = [("idle", 0.0), ("hammer", 0.7), ("hammer", 0.9), ("sweep", 0.6), ("sweep", 0.85), ("stomp", 0.55), ("blast", 0.8), ("death", 2.0)]
BOUNDS = (8, 8)
