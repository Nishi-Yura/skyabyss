"""背晶の大蜥蜴 (crystal_lizard)。雷晶の空洞の主 (docs/design/55 の 1 章、00 の 366 = A 背晶の大蜥蜴)。
がっしりした四つ足。背骨に沿って雷の結晶の房が並び (光る)、満ちると雷を放つ。頭に二本の角。

骨: body (胴) の子に head (頭) と jaw (下あご)・fore_l / fore_r / hind_l / hind_r (脚)・tail と tail2 (尾の根と先)・crystals (背の結晶の房)。
動き: idle・walk・bite (噛む)・tail (尾で後ろを薙ぐ)・charge_up (結晶を満たす。後ろ脚で立つ)・discharge (前脚を叩きつけて雷)・
      dash (突進)・hurt・death
"""
import math

import numpy as np

from blp import *  # noqa: F401,F403
from blp import keys, eye

SCL = [(28, 32, 46), (44, 50, 68), (62, 70, 94), (86, 96, 124), (114, 126, 158)]
BELLY = [(96, 100, 112), (132, 136, 146), (170, 172, 180)]
CRY = [(80, 150, 226), (130, 196, 250), (190, 228, 255), (238, 250, 255)]
HORN = [(110, 110, 120), (152, 152, 162), (194, 194, 202)]
CLAW = [(30, 30, 34), (52, 52, 58), (80, 80, 86)]
ZS = [-20.7, -13.8, -4.6, 4.6, 13.8, 20.7]
BULK = 1.15


def build():
    m = Mount("crystal_lizard", "背晶の大蜥蜴")
    m.bone("body", "root", (0, 16, 0))
    m.bone("head", "body", (0, 21, -20))
    m.bone("jaw", "head", (0, 18, -24))
    m.bone("crystals", "body", (0, 28, 0))
    m.bone("tail", "body", (0, 17, 18))
    m.bone("tail2", "tail", (0, 12, 34))
    legs = {"fore_l": (1, ZS[1] + 2), "fore_r": (-1, ZS[1] + 2), "hind_l": (1, ZS[-2]), "hind_r": (-1, ZS[-2])}
    for name, (s, z) in legs.items():
        m.bone(name, "body", (s * 7 * BULK, 15, z))
    # 胴: 幅のある肩と腰、腹は明るい鱗
    body = [rsec(z, 26 * BULK - abs(z) * 0.2, 9 + abs(z) * 0.12, (9.6 - abs(z) * 0.16) * BULK, mid=17, n=16) for z in ZS]
    m.add("torso", lof("body", "scale", body), core=True)
    m.add("belly", ell("body", "belly", (0, 11.5, 0), (8.0, 3.2, 17.0), segs=12, rings=3))
    m.add("shoulders", ell("body", "scale", (0, 22, -12), (11.5, 7.0, 7.0), segs=12, rings=3))
    m.add("hips", ell("body", "scale", (0, 21, 12), (10.5, 6.5, 6.5), segs=12, rings=3))
    m.add("neck", ell("body", "scale", (0, 21, -21), (7.0, 6.0, 6.5), segs=10, rings=3))
    # 頭: 上あご (頭) と下あご (jaw)、角、目、鼻の穴
    hc = np.array([0, 21, -28])
    m.add("skull", lof("head", "scale", [rsec(hc[2] + 6, hc[1] + 5, hc[1] - 2, 6.2, n=12), rsec(hc[2] + 1, hc[1] + 5.4, hc[1] - 2, 6.6, n=12),
                                          rsec(hc[2] - 5, hc[1] + 3, hc[1] - 1.5, 4.6, n=12), rsec(hc[2] - 10, hc[1] + 1.2, hc[1] - 1, 2.6, n=12)]), core=True)
    m.add("jawbone", lof("jaw", "belly", [rsec(hc[2] + 5, hc[1] - 1.5, hc[1] - 5, 5.6, n=10), rsec(hc[2] - 2, hc[1] - 1.5, hc[1] - 4.5, 4.6, n=10),
                                          rsec(hc[2] - 9, hc[1] - 1.2, hc[1] - 3.2, 2.4, n=10)]))
    for k in range(4):
        for s in (1, -1):
            m.add(f"tooth{k}{s}", cone("head", "horn", hc + np.array([s * (3.6 - 0.6 * k), -1.6, -2.0 - 2.4 * k]), (0, -1, -0.1), 1.6, 0.5, sides=4, rings=1))
    for s in (1, -1):
        m.add(f"horn{s}", cone("head", "horn", hc + np.array([s * 3.2, 4.6, 2.6]), (s * 0.45, 0.55, 1), 9.0, 1.5, sides=6, rings=3, bend=(0, 1.4, 0)))
        m.add(f"hornb{s}", cone("head", "horn", hc + np.array([s * 4.6, 2.6, 3.6]), (s * 0.9, 0.1, 0.8), 4.0, 0.9, sides=5, rings=2))
        eye(m, "head", hc + np.array([s * 4.6, 2.8, -2.4]), 1.3, (s, 0.3, -0.5), (250, 230, 120), glow=True)
        m.add(f"nostril{s}", ell("head", "scale", hc + np.array([s * 1.2, 1.6, -9.6]), (0.7, 0.5, 0.7), segs=6, rings=1))
    # 脚: 太い上の脚と、前へ向いた三本の爪
    for name, (s, z) in legs.items():
        hip = np.array([s * 7 * BULK, 15, z])
        knee = np.array([s * 13.5 * BULK, 9.5, z - 1])
        foot = np.array([s * 14.5 * BULK, 1.6, z - 3])
        m.add(f"thigh_{name}", ell(name, "scale", hip + np.array([s * 2, 0, 0]), (5.2, 5.6, 5.6), segs=10, rings=3))
        m.add(f"leg_{name}", tub(name, "scale", [hip, knee, foot], [4.4, 3.4, 2.8], None, sides=7))
        m.add(f"foot_{name}", ell(name, "scale", foot + np.array([0, -0.4, -1.0]), (3.2, 1.6, 3.8), segs=8, rings=2))
        for j in range(3):
            a = math.radians(-32 + 32 * j)
            m.add(f"claw_{name}{j}", cone(name, "claw", foot + np.array([math.sin(a) * 1.6, -0.6, -2.6]), (math.sin(a), -0.25, -math.cos(a)), 3.4, 0.8, sides=4, rings=2))
    # 尾: 根 (tail) と先 (tail2)。先にも小さな結晶
    m.add("tailb", limb("tail", "scale", [np.array([0, 17, 16]), np.array([0, 15, 26]), np.array([0, 12.5, 35])], [12 * BULK, 9, 7], [10 * BULK, 8, 6], n=12))
    m.add("tailt", limb("tail2", "scale", [np.array([0, 12.8, 33]), np.array([0, 10, 44]), np.array([0, 8, 54])], [7.2, 4.6, 1.6], [6.2, 4.0, 1.6], n=12))
    for k, z in enumerate((38, 44, 50)):
        m.add(f"tailcry{k}", cone("tail2", "cry", (0, 13 - k * 1.6, z), (0, 1, 0.3), 4.5 - k, 1.2, sides=6, rings=1))
    # 背の結晶の房 (crystals): 三本ずつ 8 房。真ん中が高い
    for k, z in enumerate(np.linspace(ZS[0] + 2, ZS[-1] + 2, 8)):
        h = 10 - abs(k - 3.5) * 1.3
        y = 26 * BULK - abs(z) * 0.2 - 2.6  # 根を背の肉に深く埋める (傾けても浮かない)
        for j in (-1, 0, 1):
            a = j * 0.38 + (0.12 if k % 2 else -0.12)
            m.add(f"cry{k}{j}", cone("crystals", "cry", (j * 2.4, y, z + j * 0.8), (a, 1, 0.12), h * (0.62 if j else 1.0) + 1.1, 1.9 if j else 2.6, sides=6, rings=2))
    m.pal = dict(mats={"eye": M_(EYE, base=0.5, lit=0.2, noise=0.0, min=0), "scale": M_(SCL, lit=0.34, streak=0.04), "belly": M_(BELLY, lit=0.34),
                       "cry": M_(CRY, base=0.64, lit=0.22), "horn": M_(HORN), "claw": M_(CLAW)}, glow_mats=["cry"])

    def detail(c, col, glow):
        col, glow = scales(c, col, ["scale"], size=1.4, edge=0.12), glow
        col, glow = scales(c, col, ["belly"], size=1.9, edge=0.08, k_edge=0.8), glow
        return col, glow
    m.detail = detail
    m.hint = 512
    m.view = dict(center=(0, 16, 6), scale=2.5)
    m.gaits = gaits()
    return m


# ---------------------------------------------------------------- 動き
def merge(*poses):
    out = {}
    for p in poses:
        for b, ch in p.items():
            out.setdefault(b, {}).update(ch)
    return out


def gaits():
    g = []
    idle = Gait("idle", 3.0)
    idle.rot("head", W(0).s(2.0, 1, 0, "t"), W(0).s(3.0, 1, 90, "t"))
    idle.rot("jaw", W(2).s(2.0, 2, 0, "t"))
    idle.rot("tail", None, W(0).s(4.0, 1, 30, "t"))
    idle.rot("tail2", None, W(0).s(6.0, 1, 80, "t"))
    idle.pos("body", None, W(0).s(0.3, 1, 0, "t"))
    g.append(idle)
    walk = Gait("walk", 1.0)
    walk.rot("fore_l", W(0).s(14, 1, 0, "t"))
    walk.rot("hind_r", W(0).s(14, 1, 0, "t"))
    walk.rot("fore_r", W(0).s(14, 1, 180, "t"))
    walk.rot("hind_l", W(0).s(14, 1, 180, "t"))
    walk.rot("body", None, W(0).s(4.0, 1, 0, "t"), W(0).s(2.0, 1, 90, "t"))
    walk.rot("head", None, W(0).s(-4.0, 1, 0, "t"))
    walk.rot("tail", None, W(0).s(-8.0, 1, 30, "t"))
    walk.rot("tail2", None, W(0).s(-10.0, 1, 80, "t"))
    walk.pos("body", None, W(0).s(0.6, 2, 0, "t"))
    g.append(walk)
    rest = {}
    open_ = {"head": {"rot": (-14, 0, 0)}, "jaw": {"rot": (30, 0, 0)}, "body": {"rot": (-4, 0, 0)}}
    snap = {"head": {"rot": (10, 0, 0)}, "jaw": {"rot": (2, 0, 0)}, "body": {"rot": (4, 0, 0), "pos": (0, 0, -3)}}
    g.append(keys("bite", 1.1, [(0.0, rest), (0.45, open_), (0.6, snap), (0.8, snap), (1.1, rest)]))
    coil = {"tail": {"rot": (0, -35, 0)}, "tail2": {"rot": (0, -25, 0)}, "body": {"rot": (0, 10, 0)}, "head": {"rot": (0, 12, 0)}}
    lash = {"tail": {"rot": (0, 40, 0)}, "tail2": {"rot": (0, 30, 0)}, "body": {"rot": (0, -12, 0)}, "head": {"rot": (0, -10, 0)}}
    g.append(keys("tail", 1.3, [(0.0, rest), (0.55, coil), (0.8, lash), (1.0, lash), (1.3, rest)]))
    rear = {"body": {"rot": (-24, 0, 0), "pos": (0, 3, 2)}, "fore_l": {"rot": (-30, 0, 0)}, "fore_r": {"rot": (-30, 0, 0)},
            "hind_l": {"rot": (24, 0, 0)}, "hind_r": {"rot": (24, 0, 0)}, "head": {"rot": (-14, 0, 0)}, "jaw": {"rot": (24, 0, 0)},
            "tail": {"rot": (16, 0, 0)}, "tail2": {"rot": (8, 0, 0)}, "crystals": {"rot": (0, 0, 0)}}
    rear_b = merge(rear, {"crystals": {"rot": (0, 3, 0)}})
    g.append(keys("charge_up", 1.6, [(0.0, rest), (0.6, rear), (0.9, rear_b), (1.2, rear), (1.6, rear)], hold=True))
    slam = {"body": {"rot": (8, 0, 0), "pos": (0, -1, -1)}, "fore_l": {"rot": (14, 0, 0)}, "fore_r": {"rot": (14, 0, 0)}, "head": {"rot": (10, 0, 0)},
            "jaw": {"rot": (20, 0, 0)}}
    g.append(keys("discharge", 1.0, [(0.0, rear), (0.25, slam), (0.6, slam), (1.0, rest)]))
    run = {"body": {"rot": (6, 0, 0)}, "fore_l": {"rot": (-16, 0, 0)}, "fore_r": {"rot": (14, 0, 0)}, "hind_l": {"rot": (14, 0, 0)}, "hind_r": {"rot": (-16, 0, 0)},
           "head": {"rot": (8, 0, 0)}, "tail": {"rot": (-6, 0, 0)}}
    run_b = {"body": {"rot": (6, 0, 0)}, "fore_l": {"rot": (14, 0, 0)}, "fore_r": {"rot": (-16, 0, 0)}, "hind_l": {"rot": (-16, 0, 0)}, "hind_r": {"rot": (14, 0, 0)},
             "head": {"rot": (8, 0, 0)}, "tail": {"rot": (-6, 0, 0)}}
    g.append(keys("dash", 2.2, [(0.0, rest), (0.5, {"body": {"rot": (8, 0, 0), "pos": (0, -1.5, 1)}, "head": {"rot": (10, 0, 0)}}), (1.0, run), (1.2, run_b),
                                (1.4, run), (1.6, run_b), (1.8, run), (2.2, rest)]))
    g.append(keys("hurt", 0.3, [(0.0, rest), (0.12, {"head": {"rot": (-8, 6, 0)}, "body": {"rot": (0, 0, 3)}}), (0.3, rest)]))
    fall = {"body": {"rot": (0, 0, 70), "pos": (0, -4, 0)}, "head": {"rot": (6, 0, 8)}, "jaw": {"rot": (18, 0, 0)}, "fore_l": {"rot": (-30, 0, 0)},
            "hind_l": {"rot": (-20, 0, 0)}, "tail": {"rot": (0, 12, 0)}}
    g.append(keys("death", 1.6, [(0.0, rest), (0.5, {"body": {"rot": (-10, 0, 10)}, "head": {"rot": (-16, 0, 0)}}), (1.2, fall), (1.6, fall)], hold=True))
    return g


POSES = [("idle", 0.0), ("bite", 0.45), ("bite", 0.6), ("tail", 0.8), ("charge_up", 0.9), ("discharge", 0.3), ("dash", 1.2), ("death", 1.6)]
BOUNDS = (8, 5)
