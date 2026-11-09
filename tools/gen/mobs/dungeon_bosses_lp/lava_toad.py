"""熔岩の大蛙 (lava_toad)。熔岩の管の坑の中ボス (2026-11-09 のラフ tools/rough/lowpoly/deep_mids で B)。
平たく大きな蛙。焦げ茶のいぼだらけの皮、橙の腹、背に玄武岩の噴き口が五つ (口の中が熔けて光る)、大きな口の縁と喉袋が熱く光る。
太い後ろ脚で跳ぶ。

骨: body の子に head (→ jaw・tongue)・arm_l / arm_r (前脚)・leg_l / leg_r (後ろ脚)・vents。
動き: idle・walk (這う)・hop (跳びかかり)・erupt (噴き口)・tongue (舌)・hurt・death
"""
import math

import numpy as np

from blp import *  # noqa: F401,F403
from blp import keys, eye

SKIN = [(36, 26, 20), (58, 42, 30), (84, 62, 42), (112, 86, 58)]
BELLY = [(150, 70, 30), (196, 110, 44), (230, 150, 70), (248, 196, 120)]
BAS = [(18, 16, 18), (32, 28, 30), (50, 44, 46), (70, 62, 62)]
MAG = [(160, 50, 16), (220, 96, 30), (252, 156, 56), (255, 214, 120)]
TONGUE = [(120, 30, 30), (176, 50, 44), (220, 90, 70)]
VENTS = [(-5.0, 27.0, 2.0, 4.6), (5.5, 26.6, 0.5, 4.0), (0.0, 26.0, 9.0, 3.6), (-7.5, 22.0, 11.0, 3.0), (7.5, 22.4, 10.0, 3.2)]


def build():
    m = Mount("lava_toad", "熔岩の大蛙")
    m.bone("body", "root", (0, 14, 2))
    m.bone("head", "body", (0, 17, -12))
    m.bone("jaw", "head", (0, 14, -14))
    m.bone("tongue", "jaw", (0, 14, -20))
    m.bone("vents", "body", (0, 24, 4))
    for s, side in ((1, "l"), (-1, "r")):
        m.bone(f"arm_{side}", "body", (s * 9, 12, -8))
        m.bone(f"leg_{side}", "body", (s * 11, 14, 11))
    # ---- 胴: 平たく丸い胴、橙の腹、いぼ
    m.add("torso", lof("body", "skin", [rsec(z, 24 - abs(z - 3) * 0.25, 6, 13.0 - abs(z - 3) * 0.28, mid=14, n=18, p=2.1) for z in (-10, -4, 2, 8, 14, 18)]),
          core=True)
    m.add("belly", ell("body", "belly", (0, 9.0, 2), (11.0, 3.8, 14.0), segs=14, rings=3))
    for k in range(26):
        a = k * 2.399
        r = 4 + (k % 7) * 1.3
        p = np.array([math.cos(a) * r, 0, 2 + math.sin(a) * r * 1.2])
        y = 24 - (p[0] ** 2) / 30 - ((p[2] - 3) ** 2) / 60
        m.add(f"wart{k}", ell("body", "skin", (p[0], y, p[2]), (1.0, 0.7, 1.0), segs=6, rings=2))
    # ---- 噴き口 (背の玄武岩の筒、口の中が光る)
    for k, (x, y, z, r) in enumerate(VENTS):
        m.add(f"vent{k}", lof("vents", "bas", [oct_ring((x, y - 1.6, z), r * 1.6, r * 1.6, R=R_(90, 0, 0)), oct_ring((x, y + 1.6, z), r * 1.1, r * 1.1, R=R_(90, 0, 0)),
                                                oct_ring((x, y + 2.6, z), r * 1.2, r * 1.2, R=R_(90, 0, 0))]))
        m.add(f"vglow{k}", ell("vents", "mag", (x, y + 2.7, z), (r * 0.45, 0.4, r * 0.45), segs=8, rings=1))
        for j in range(3):
            a = j * 2.1 + k
            m.add(f"crack{k}{j}", box("vents", "mag", (x + math.cos(a) * r * 0.7, y + 0.4, z + math.sin(a) * r * 0.7), (0.4, 1.6, 0.4)))
    # ---- 頭: 広い頭、上に盛り上がる目、熱い口の縁、喉袋
    m.add("head", lof("head", "skin", [rsec(-8, 22, 10, 12.0, n=16, p=2.2), rsec(-14, 21, 13, 11.0, n=16, p=2.2), rsec(-19, 19, 14.4, 8.4, n=16, p=2.4),
                                       rsec(-22, 17, 14.6, 5.0, n=16, p=2.4)]), core=True)
    for s in (1, -1):
        m.add(f"eyemound{s}", ell("head", "skin", (s * 6.4, 22.0, -13.0), (3.0, 2.8, 3.0), segs=10, rings=3))
        eye(m, "head", (s * 6.8, 23.4, -14.6), 1.5, (s * 0.4, 0.3, -1), (255, 170, 40), glow=True)
        m.add(f"nostril{s}", ell("head", "bas", (s * 1.8, 19.0, -21.4), (0.5, 0.4, 0.4), segs=5, rings=1))
    m.add("lip", tub("head", "mag", [(-10.8, 14.4, -9), (-8.6, 14.6, -17), (0, 14.8, -22.2), (8.6, 14.6, -17), (10.8, 14.4, -9)], [0.5] * 5, None, sides=4))
    m.add("jaw", lof("jaw", "belly", [rsec(-8, 14.4, 7.6, 11.0, n=16, p=2.2), rsec(-15, 14.4, 9.6, 9.4, n=16, p=2.2), rsec(-21.4, 14.4, 12.4, 4.6, n=16, p=2.4)]))
    m.add("throat", ell("jaw", "belly", (0, 9.6, -12), (6.4, 3.2, 5.0), segs=10, rings=3))
    m.add("mouth_in", box("jaw", "mag", (0, 14.2, -14.5), (16, 0.4, 12), taper=(0.7, 1.0)))
    m.add("tongue", tub("tongue", "tongue", [(0, 14.6, -12.0), (0, 14.6, -17.0), (0, 14.7, -20.5)], [1.4, 1.6, 1.8], [0.6, 0.6, 0.8], sides=6, hint=(1, 0, 0)))
    m.add("tongue_tip", ell("tongue", "tongue", (0, 14.8, -21.0), (2.0, 0.9, 1.6), segs=8, rings=2))
    # ---- 前脚: 短く太い、指は四本
    for s, side in ((1, "l"), (-1, "r")):
        sh = np.array([s * 9.0, 12.0, -8.0])
        el = np.array([s * 12.6, 7.0, -11.0])
        hand = np.array([s * 12.8, 1.4, -13.0])
        m.add(f"upper_{side}", limb(f"arm_{side}", "skin", [sh, el], [4.4, 3.6], [4.4, 3.6], n=8))
        m.add(f"fore_{side}", limb(f"arm_{side}", "skin", [el, hand], [3.4, 3.0], [3.4, 3.0], n=8))
        for k in range(4):
            a = math.radians(-60 + k * 40)
            d = np.array([math.sin(a) * s, 0, -math.cos(a)])
            m.add(f"finger_{side}{k}", tub(f"arm_{side}", "skin", [hand, hand + d * 3.6 + np.array([0, -0.6, 0])], [0.9, 0.6], None, sides=5))
            m.add(f"pad_{side}{k}", ell(f"arm_{side}", "belly", hand + d * 3.8 + np.array([0, -0.6, 0]), (0.8, 0.5, 0.8), segs=6, rings=2))
    # ---- 後ろ脚: 太い腿を折りたたんだ形、長い足と水かき
    for s, side in ((1, "l"), (-1, "r")):
        hip = np.array([s * 11.0, 14.0, 11.0])
        knee = np.array([s * 18.0, 11.0, 4.0])
        ankle = np.array([s * 17.0, 5.0, 15.0])
        foot = np.array([s * 15.0, 1.2, 6.0])
        m.add(f"thigh_{side}", limb(f"leg_{side}", "skin", [hip, (hip + knee) / 2 + np.array([s * 1.0, 1.0, 0]), knee], [8.0, 8.4, 5.4], [8.0, 8.4, 5.4], n=10))
        m.add(f"calf_{side}", limb(f"leg_{side}", "skin", [knee, ankle], [5.0, 3.6], [5.0, 3.6], n=8))
        m.add(f"foot_{side}", limb(f"leg_{side}", "skin", [ankle, foot], [3.2, 2.4], [3.2, 2.4], n=8))
        m.add(f"web_{side}", slab(f"leg_{side}", "belly", [foot + np.array([-3.0, 0, -0.6]), foot + np.array([3.0, 0, -0.6]), foot + np.array([4.0, 0, -6.0]),
                                                          foot + np.array([-4.0, 0, -6.0])], (0, 1, 0), 0.5))
        for k in range(4):
            m.add(f"toe_{side}{k}", tub(f"leg_{side}", "skin", [foot, foot + np.array([(k - 1.5) * 2.4, -0.2, -6.6])], [0.8, 0.5], None, sides=5))
        m.add(f"knee_glow_{side}", ell(f"leg_{side}", "mag", knee + np.array([s * 1.4, 1.6, 0]), (1.0, 1.0, 1.0), segs=6, rings=2))

    m.pal = dict(mats={"eye": M_(MAG, base=0.7, lit=0.2, noise=0.0, min=1), "skin": M_(SKIN, lit=0.32), "belly": M_(BELLY, lit=0.3),
                       "bas": M_(BAS, lit=0.34), "mag": M_(MAG, base=0.66, lit=0.2, min=1), "tongue": M_(TONGUE, lit=0.3)}, glow_mats=["eye", "mag"])

    def detail(c, col, glow):
        col = spots(c, col, ["skin"], (24, 18, 14), scale=0.22, thr=0.7, a=0.8)
        col = speckle(c, col, ["skin"], (140, 70, 30), density=0.05)
        P = c["P"]
        sel = np.isin(c["mats"], ["bas"]) & c["owned"]
        f1, f2, idv = cell3(P, 1.6, 5)
        seam = sel & ((f2 - f1) < 0.04)
        col = np.where(seam[..., None], np.array([250, 132, 40]), col)
        return col, glow | seam
    m.detail = detail
    m.hint = 512
    m.view = dict(center=(0, 14, 0), scale=2.0)
    m.gaits = gaits()
    return m


def gaits():
    g = []
    idle = Gait("idle", 2.6)
    idle.pos("body", None, W(0).s(0.4, 2, 0, "t"))
    idle.rot("jaw", W(0).s(2.0, 2, 0, "t"))
    idle.rot("head", W(0).s(2.0, 1, 0, "t"))
    idle.pos("vents", None, W(0).s(0.3, 2, 90, "t"))
    g.append(idle)
    walk = Gait("walk", 1.2)
    walk.rot("arm_l", W(0).s(20, 1, 0, "t"))
    walk.rot("arm_r", W(0).s(20, 1, 180, "t"))
    walk.rot("leg_l", W(0).s(14, 1, 180, "t"))
    walk.rot("leg_r", W(0).s(14, 1, 0, "t"))
    walk.rot("body", None, W(0).s(5, 1, 0, "t"), W(0).s(3, 1, 90, "t"))
    g.append(walk)
    rest = {}
    crouch = {"body": {"pos": (0, -3, 2), "rot": (-8, 0, 0)}, "leg_l": {"rot": (10, 0, 0)}, "leg_r": {"rot": (10, 0, 0)}, "head": {"rot": (-10, 0, 0)}}
    jump = {"body": {"pos": (0, 10, -4), "rot": (-16, 0, 0)}, "leg_l": {"rot": (60, 0, 0)}, "leg_r": {"rot": (60, 0, 0)}, "arm_l": {"rot": (-50, 0, 0)},
            "arm_r": {"rot": (-50, 0, 0)}}
    slam = {"body": {"pos": (0, -2, 0), "rot": (6, 0, 0)}, "arm_l": {"rot": (-20, 0, 20)}, "arm_r": {"rot": (-20, 0, -20)}}
    g.append(keys("hop", 1.6, [(0.0, rest), (0.7, crouch), (0.95, jump), (1.1, slam), (1.25, slam), (1.6, rest)]))
    puff = {"body": {"pos": (0, 1, 0)}, "jaw": {"rot": (10, 0, 0)}, "vents": {"pos": (0, 1.5, 0)}}
    blast = {"body": {"pos": (0, -1, 0)}, "vents": {"pos": (0, -0.5, 0)}, "head": {"rot": (-14, 0, 0)}, "jaw": {"rot": (24, 0, 0)}}
    g.append(keys("erupt", 1.6, [(0.0, rest), (0.8, puff), (1.0, blast), (1.2, puff), (1.6, rest)]))
    aim = {"head": {"rot": (6, 0, 0)}, "jaw": {"rot": (8, 0, 0)}, "body": {"rot": (6, 0, 0)}}
    lash = {"head": {"rot": (4, 0, 0)}, "jaw": {"rot": (34, 0, 0)}, "tongue": {"pos": (0, -1, -7), "rot": (10, 0, 0)}}
    g.append(keys("tongue", 1.4, [(0.0, rest), (0.7, aim), (0.85, lash), (1.0, lash), (1.2, aim), (1.4, rest)]))
    g.append(keys("hurt", 0.3, [(0.0, rest), (0.1, {"body": {"rot": (-6, 0, 6)}, "head": {"rot": (-10, 0, 0)}}), (0.3, rest)]))
    flop = {"body": {"pos": (0, -4, 0), "rot": (0, 0, 170)}, "leg_l": {"rot": (-30, 0, 30)}, "leg_r": {"rot": (-30, 0, -30)}, "arm_l": {"rot": (-40, 0, 20)},
            "arm_r": {"rot": (-40, 0, -20)}, "jaw": {"rot": (20, 0, 0)}}
    g.append(keys("death", 2.0, [(0.0, rest), (0.6, {"body": {"pos": (0, 6, 0)}}), (1.2, flop), (2.0, flop)], hold=True))
    return g


POSES = [("idle", 0.0), ("walk", 0.3), ("hop", 0.7), ("hop", 0.95), ("erupt", 1.0), ("tongue", 0.85), ("hurt", 0.1), ("death", 2.0)]
BOUNDS = (7, 5)
