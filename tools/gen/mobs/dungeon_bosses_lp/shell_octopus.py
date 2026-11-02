"""殻負いの大蛸 (shell_octopus)。潮の洞の主 (docs/design/55 の 1 章、00 の 365 = B 殻負いの大蛸)。
珊瑚とフジツボに覆われた大きな巻貝の殻を背負い、殻の口から五本の太い腕と大きな目の頭を出す。殻は硬く、腕と頭を狙う。

骨: body (殻の口の真ん中) の子に shell (殻)・head (頭と目)・arm<k> と arm<k>_tip (腕の根と先。k = 0..4、左から右)。
動き: idle・walk・slam (一本を振り上げて叩きつける)・sweep (両端の腕で薙ぐ)・ink (墨を吐く)・withdraw (殻にこもる。そのまま止まる)・
      unwithdraw (殻から出る)・hurt・death
"""
import math

import numpy as np

from blp import *  # noqa: F401,F403
from blp import keys, eye

OCT = [(70, 30, 40), (104, 46, 56), (140, 66, 74), (176, 92, 96), (210, 128, 126)]
SUCK = [(196, 156, 146), (224, 190, 178), (244, 220, 208)]
BARN = [(140, 140, 132), (186, 186, 174), (222, 222, 210)]
SHELL = [(96, 60, 34), (150, 102, 58), (200, 148, 92), (232, 196, 140), (246, 226, 190)]
CORAL = [(150, 44, 56), (206, 76, 78), (240, 128, 118)]
WEED = [(28, 58, 40), (44, 86, 56), (66, 116, 74)]
ARMS = 5


def arm_path(k):
    """腕 k (0 = 左の端 … 4 = 右の端) の根から先までの点。前 (-Z) へ扇に開き、先は床に巻く"""
    a = math.radians(-70 + 35 * k)
    d = np.array([math.sin(a), 0, -math.cos(a)])
    side = np.array([math.cos(a), 0, math.sin(a)])
    base = np.array([0, 12, -8]) + d * 5
    pts = [base]
    for i in range(1, 7):
        t = i / 6
        p = base + d * (34 * t) + np.array([0, -11 * min(1, t * 1.6) + 3 * math.sin(t * math.pi), 0]) + side * (5 * t * t * (1 if k < 2 else -1 if k > 2 else 0))
        pts.append(p)
    pts[-1][1] = 2.0
    return pts


def build():
    m = Mount("shell_octopus", "殻負いの大蛸")
    m.bone("body", "root", (0, 14, 0))
    m.bone("shell", "body", (0, 18, 6))
    m.bone("head", "body", (0, 14, -8))
    paths = [arm_path(k) for k in range(ARMS)]
    for k, P in enumerate(paths):
        m.bone(f"arm{k}", "body", P[0])
        m.bone(f"arm{k}_tip", f"arm{k}", P[3])
    # 殻: 渦を重ねた大きな巻貝。口は前 (-Z)
    for k in range(8):
        t = k / 7
        c = np.array([0, 20 + 18 * t, 9 - 5 * t])
        r = 19 - 12 * t
        m.add(f"whorl{k}", ell("shell", "shell", c, (r, r * 0.58, r * 0.92), segs=14, rings=4, rx=-22 + 12 * t), core=k == 0)
        # 渦の縁の筋 (少し張り出した輪)
        m.add(f"rib{k}", ell("shell", "shell", c + np.array([0, -r * 0.22, r * 0.1]), (r * 1.03, r * 0.16, r * 0.95), segs=14, rings=2, rx=-22 + 12 * t))
    m.add("spire", cone("shell", "shell", (0, 40, 4), (0, 1, 0.35), 12.0, 4.6, sides=7, rings=3, bend=(0, 0, 2.0)))
    m.add("lip", ell("shell", "shell", (0, 14, -7), (16, 10, 4.4), segs=14, rings=3))
    # 殻の上の珊瑚・フジツボ・海藻
    for k in range(16):
        a = math.radians(k * 22.5 + 7)
        y = 26 + 8 * math.cos(a * 2) + (k % 3) * 2
        base = np.array([math.sin(a) * 13, y, math.cos(a) * 11 + 7])
        if k % 3 == 0:
            m.add(f"coral{k}", cone("shell", "coral", base, (math.sin(a), 0.9, math.cos(a)), 6.5, 1.6, sides=5, rings=3, bend=(0, 1.6, 0)))
            m.add(f"coralb{k}", cone("shell", "coral", base + np.array([0, 2.6, 0]), (math.sin(a + 0.8), 0.7, math.cos(a + 0.8)), 3.6, 1.0, sides=5, rings=2))
        elif k % 3 == 1:
            m.add(f"barn{k}", cone("shell", "barn", base, (math.sin(a) * 0.4, 1, math.cos(a) * 0.4), 2.6, 1.9, sides=6, rings=1))
        else:
            m.add(f"weed{k}", tub("shell", "weed", [base, base + np.array([math.sin(a) * 2, -3, math.cos(a) * 2]),
                                                  base + np.array([math.sin(a) * 2.6, -7, math.cos(a) * 2.6])], [0.9, 0.7, 0.3], None, sides=4))
    # 頭: 殻の口から出る柔らかい頭、大きな目、くちばし、漏斗
    m.add("mantle", ell("head", "oct", (0, 14, -10), (9.5, 8.0, 7.0), segs=12, rings=4), core=True)
    m.add("brow", ell("head", "oct", (0, 19, -12), (7.6, 3.0, 4.6), segs=10, rings=2))
    m.add("beak", cone("head", "barn", (0, 9, -15.5), (0, -0.6, -1), 3.2, 1.6, sides=5, rings=2))
    m.add("siphon", tub("head", "oct", [(5.6, 10, -12), (8.2, 8, -15), (9.0, 7.5, -17)], [1.8, 1.6, 1.3], None, sides=6))
    for s in (1, -1):
        m.add(f"socket{s}", ell("head", "oct", (s * 6.0, 16.5, -13), (3.6, 3.4, 3.0), segs=10, rings=3))
        eye(m, "head", (s * 6.4, 16.6, -15.2), 2.6, (s * 0.55, 0.15, -0.85), (238, 196, 84), lid=(84, 34, 46))
    # 腕: 根 (arm<k>) と先 (arm<k>_tip)。継ぎ目は重ねる。腹側に吸盤
    for k, P in enumerate(paths):
        r = [5.0, 4.4, 3.8, 3.2, 2.4, 1.6, 0.8]
        m.add(f"armb{k}", tub(f"arm{k}", "oct", [P[0] + np.array([0, 0, 3]), P[1], P[2], P[3] + (P[3] - P[2]) * 0.25], r[0:4], None, sides=7))
        m.add(f"armt{k}", tub(f"arm{k}_tip", "oct", [P[3] - (P[3] - P[2]) * 0.2, P[4], P[5], P[6]], r[3:7], None, sides=7))
        for i in range(1, 6):
            bone = f"arm{k}" if i < 3 else f"arm{k}_tip"
            q = P[i] + np.array([0, -r[i] * 0.72, 0])
            m.add(f"suck{k}{i}", ell(bone, "suck", q, (r[i] * 0.5, 0.5, r[i] * 0.5), segs=7, rings=1))
        m.add(f"curl{k}", ell(f"arm{k}_tip", "oct", P[6] + np.array([0, 1.0, 0]), (1.6, 1.4, 1.6), segs=7, rings=2))
    m.pal = dict(mats={"eye": M_(EYE, base=0.5, lit=0.2, noise=0.0, min=0), "oct": M_(OCT, streak=0.04, ao=0.12), "suck": M_(SUCK),
                       "barn": M_(BARN, lit=0.34), "shell": M_(SHELL, lit=0.34, streak=0.08), "coral": M_(CORAL), "weed": M_(WEED)}, glow_mats=[])

    def detail(c, col, glow):
        col, glow = bands_y(c, col, ["shell"], 3.0, 0.8, k=0.8, wobble=1.0), glow
        col, glow = spots(c, col, ["oct"], (60, 22, 34), scale=0.22, thr=0.7, a=0.75), glow
        return col, glow
    m.detail = detail
    m.hint = 512
    m.view = dict(center=(0, 18, -6), scale=2.3)
    m.gaits = gaits()
    return m


# ---------------------------------------------------------------- 動き
def arms(rx=0.0, ry=0.0, rz=0.0, tip=None, which=range(ARMS)):
    out = {}
    for k in which:
        out[f"arm{k}"] = {"rot": (rx, ry, rz)}
        if tip is not None:
            out[f"arm{k}_tip"] = {"rot": tip}
    return out


def merge(*poses):
    out = {}
    for p in poses:
        for b, ch in p.items():
            out.setdefault(b, {}).update(ch)
    return out


def gaits():
    g = []
    idle = Gait("idle", 3.6)
    for k in range(ARMS):
        idle.rot(f"arm{k}", W(-2.5).s(2.5, 1, k * 70, "t"), W(0).s(4.0, 1, k * 50 + 30, "t"))
        idle.rot(f"arm{k}_tip", W(-4.0).s(4.0, 1, k * 70 + 60, "t"), W(0).s(7.0, 1, k * 50 + 90, "t"))
    idle.rot("head", W(0).s(2.0, 1, 0, "t"))
    idle.pos("head", None, W(0).s(0.5, 1, 90, "t"))
    idle.rot("shell", None, None, W(0).s(1.2, 1, 40, "t"))
    g.append(idle)
    walk = Gait("walk", 1.3)
    for k in range(ARMS):
        walk.rot(f"arm{k}", W(-5.0).s(5.0, 1, k * 72, "t"), W(0).s(6.0, 1, k * 72 + 90, "t"))
        walk.rot(f"arm{k}_tip", W(-6.0).s(6.0, 1, k * 72 + 60, "t"))
    walk.pos("body", None, W(0).s(0.8, 2, 0, "t"))
    walk.rot("shell", None, W(0).s(3.0, 1, 0, "t"), W(0).s(2.0, 1, 90, "t"))
    g.append(walk)
    rest = {}
    lift = merge({"arm2": {"rot": (-38, 0, 0)}, "arm2_tip": {"rot": (-30, 0, 0)}}, arms(rx=6, which=(1, 3)))
    slam = merge({"arm2": {"rot": (5, 0, 0)}, "arm2_tip": {"rot": (2, 0, 0)}}, {"head": {"rot": (6, 0, 0)}})
    g.append(keys("slam", 1.5, [(0.0, rest), (0.65, lift), (0.85, slam), (1.15, slam), (1.5, rest)]))
    wind = merge({"arm0": {"rot": (-8, -40, 0)}, "arm0_tip": {"rot": (0, -20, 0)}, "arm4": {"rot": (-8, 40, 0)}, "arm4_tip": {"rot": (0, 20, 0)}})
    swing = merge({"arm0": {"rot": (-4, 45, 0)}, "arm0_tip": {"rot": (0, 25, 0)}, "arm4": {"rot": (-4, -45, 0)}, "arm4_tip": {"rot": (0, -25, 0)}})
    g.append(keys("sweep", 1.4, [(0.0, rest), (0.6, wind), (0.85, swing), (1.05, swing), (1.4, rest)]))
    pump = {"head": {"rot": (-10, 0, 0), "pos": (0, 1.0, 1.0)}, "shell": {"rot": (-4, 0, 0)}}
    spit = {"head": {"rot": (8, 0, 0), "pos": (0, -0.5, -1.5)}, "shell": {"rot": (3, 0, 0)}}
    g.append(keys("ink", 1.4, [(0.0, rest), (0.5, pump), (0.7, spit), (0.9, pump), (1.1, spit), (1.4, rest)]))
    tucked = merge({"head": {"pos": (0, -3.0, 7.0), "rot": (10, 0, 0)}}, arms(rx=-8, tip=(-48, 0, 0)), {"body": {"pos": (0, -1.0, 0)}})
    g.append(keys("withdraw", 0.8, [(0.0, rest), (0.8, tucked)], hold=True))
    g.append(keys("unwithdraw", 0.8, [(0.0, tucked), (0.8, rest)]))
    g.append(keys("hurt", 0.3, [(0.0, rest), (0.12, merge({"head": {"rot": (-6, 0, 0)}}, arms(rx=-4))), (0.3, rest)]))
    slump = merge({"head": {"rot": (14, 0, 0), "pos": (0, -3, -1)}, "shell": {"rot": (0, 0, 16)}, "body": {"pos": (0, -1.5, 0)}},
                  arms(rx=2, tip=(3, 0, 0)))
    g.append(keys("death", 1.6, [(0.0, rest), (0.6, merge(arms(rx=-12, tip=(-14, 0, 0)))), (1.4, slump), (1.6, slump)], hold=True))
    return g


POSES = [("idle", 0.0), ("slam", 0.65), ("slam", 0.85), ("sweep", 0.6), ("sweep", 0.85), ("ink", 0.7), ("withdraw", 0.8), ("death", 1.6)]
BOUNDS = (8, 6)
