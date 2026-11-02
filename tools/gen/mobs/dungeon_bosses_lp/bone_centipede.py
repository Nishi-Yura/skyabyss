"""骨掘りの大百足 (bone_centipede)。鯨骨の坑の主 (docs/design/55 の 1 章、00 の 364 = A 骨殻の大百足)。
白い骨の甲板を重ねた長い体 (14 節)、節ごとに尖った脚、頭は鶴嘴のような大顎。前の三分の一を持ち上げて構える。

骨: body (真ん中の節 6) から前へ f5…f0 (f0 = 頭)、後ろへ r7…r13 (r13 = 尾) の鎖。大顎は jaw_l / jaw_r (頭の子)。
動き: idle・walk・bite (噛む)・sweep (尾で薙ぐ)・burrow (潜る)・emerge (掘り穴から出る)・roar (落石を呼ぶ)・hurt・death
"""
import math

import numpy as np

from blp import *  # noqa: F401,F403
from blp import keys

BONE = [(108, 98, 82), (148, 138, 118), (188, 178, 156), (220, 212, 192), (242, 236, 220)]
FLESH = [(38, 20, 22), (60, 32, 32), (86, 48, 44), (114, 68, 58)]
CHITIN = [(30, 26, 26), (52, 44, 40), (78, 66, 58), (104, 90, 78)]
N = 14
MID = 6


def path():
    pts = [(0, 34, -36), (0, 30, -28), (0, 20, -18), (0, 10, -6), (0, 7, 8), (0, 7, 22), (0, 7, 36), (4, 7, 50), (10, 7, 62)]
    P = [np.asarray(p, float) for p in pts]
    P = [P[0]] + P + [P[-1]]
    out = []
    segs = len(P) - 3
    for i in range(N):
        t = i / (N - 1) * segs
        k = min(int(t), segs - 1)
        u = t - k
        p0, p1, p2, p3 = P[k], P[k + 1], P[k + 2], P[k + 3]
        out.append(0.5 * ((2 * p1) + (-p0 + p2) * u + (2 * p0 - 5 * p1 + 4 * p2 - p3) * u * u + (-p0 + 3 * p1 - 3 * p2 + p3) * u ** 3))
    return out


def seg_bone(i):
    return "body" if i == MID else (f"f{i}" if i < MID else f"r{i}")


FRONT = [f"f{i}" for i in range(MID - 1, -1, -1)]   # f5 … f0
REAR = [f"r{i}" for i in range(MID + 1, N)]          # r7 … r13


def build():
    m = Mount("bone_centipede", "骨掘りの大百足")
    P = path()
    m.bone("body", "root", P[MID])
    for i in range(MID - 1, -1, -1):
        m.bone(f"f{i}", seg_bone(i + 1), (P[i] + P[i + 1]) / 2)
    for i in range(MID + 1, N):
        m.bone(f"r{i}", seg_bone(i - 1), (P[i] + P[i - 1]) / 2)
    head = P[0] + np.array([0, 2, -4])
    m.bone("jaw_l", "f0", head + np.array([4.0, -2.4, -2.0]))
    m.bone("jaw_r", "f0", head + np.array([-4.0, -2.4, -2.0]))

    for i, p in enumerate(P):
        b = seg_bone(i)
        r = 7.0 - 2.4 * (i / N) ** 1.3
        d = nrm(P[min(i + 1, N - 1)] - P[max(i - 1, 0)])
        pitch = -math.degrees(math.atan2(d[1], abs(d[2]) + 1e-6))
        # 体の肉 (節の玉。隣と重なって継ぎ目が開かない)
        m.add(f"seg{i}", ell(b, "flesh", p, (r * 1.12, r * 0.80, r * 0.95), segs=12, rings=4, rx=pitch), core=True)
        # 腹の板 (黒い甲)
        m.add(f"belly{i}", ell(b, "chitin", p + np.array([0, -r * 0.42, 0]), (r * 0.86, r * 0.34, r * 0.74), segs=10, rings=2, rx=pitch))
        # 背の骨の甲板: 大きな一枚と、縁の小さな棘 (左右に三つずつ)
        top = p + np.array([0, r * 0.60, 0])
        m.add(f"plate{i}", ell(b, "bone", top, (r * 1.42, r * 0.36, r * 0.78), segs=12, rings=3, rx=pitch))
        m.add(f"ridge{i}", ell(b, "bone", top + np.array([0, r * 0.28, 0.6]), (r * 0.36, r * 0.24, r * 0.66), segs=8, rings=2, rx=pitch))
        m.add(f"spine{i}", cone(b, "bone", top + np.array([0, r * 0.30, 0.4]), (0, 1, 0.35), r * 0.62, r * 0.28, sides=5, rings=2))
        for s in (1, -1):
            for k, dz in enumerate((-0.5, 0.0, 0.5)):
                base = top + np.array([s * r * 1.15, -r * 0.05, dz * r * 0.9])
                m.add(f"edge{i}{s}{k}", cone(b, "bone", base, (s, -0.25, 0.1), r * 0.42, r * 0.13, sides=4, rings=1))
        # 脚 (前の三節には無い。持ち上げた前は大顎だけ)
        if i >= 3:
            for s in (1, -1):
                hip = p + np.array([s * r * 0.85, -r * 0.25, 0.4])
                knee = hip + np.array([s * r * 1.15, r * 0.75, 0.6])
                foot = np.array([hip[0] + s * r * 1.9, 0.3, p[2] + 1.6])
                m.add(f"thigh{i}{s}", tub(b, "chitin", [hip, knee], [1.25, 0.95], None, sides=6))
                m.add(f"shin{i}{s}", tub(b, "bone", [knee, (knee + foot) / 2 + np.array([0, 0.6, 0]), foot], [0.95, 0.65, 0.22], None, sides=5))
                m.add(f"knee{i}{s}", ell(b, "bone", knee, (1.15, 1.05, 1.15), segs=6, rings=2))
    # 頭: 骨の兜と鶴嘴の大顎
    m.add("skull", ell("f0", "bone", head, (6.8, 4.8, 6.4), segs=12, rings=4), core=True)
    m.add("brow", ell("f0", "bone", head + np.array([0, 3.0, -1.0]), (5.2, 1.8, 4.6), segs=10, rings=2))
    m.add("face", ell("f0", "flesh", head + np.array([0, -1.6, -3.4]), (4.4, 3.0, 3.0), segs=10, rings=3))
    for s, jb in ((1, "jaw_l"), (-1, "jaw_r")):
        root = head + np.array([s * 4.0, -2.4, -2.0])
        m.add(f"mandible{s}", cone(jb, "chitin", root, (s * 0.35, -0.45, -1.0), 12.0, 2.4, sides=6, rings=4, bend=(-s * 4.6, -2.4, 0)))
        m.add(f"pick{s}", cone(jb, "bone", root + np.array([s * 0.8, 0.8, -1.6]), (s * 0.45, -0.85, -0.7), 9.5, 1.9, sides=5, rings=3, bend=(0, -2.2, -2.4)))
        for k in range(3):
            m.add(f"tooth{s}{k}", cone(jb, "bone", root + np.array([s * (1.2 - 0.6 * k), -1.2 - 0.8 * k, -4.0 - 2.4 * k]), (-s, -0.2, -0.2), 1.6, 0.5, sides=4, rings=1))
        m.add(f"antenna{s}", tub("f0", "chitin", [head + np.array([s * 2.2, 3.0, -3.0]), head + np.array([s * 7.0, 9.0, -8.0]),
                                                  head + np.array([s * 12.0, 10.0, -6.0]), head + np.array([s * 15.0, 8.0, -3.0])], [0.75, 0.55, 0.35, 0.15], None, sides=5))
        for k, (dx, dy) in enumerate(((3.4, 1.8), (2.2, 2.6), (4.2, 0.6))):
            eye(m, "f0", head + np.array([s * dx, dy, -4.8 + 0.4 * k]), 0.9 - 0.15 * k, (s, 0.3, -1), (240, 120, 60), glow=True)
    # 尾の先: 骨の二又
    tail = P[-1]
    for s in (1, -1):
        m.add(f"tailfork{s}", cone("r13", "bone", tail + np.array([s * 1.6, 1.0, 2.0]), (s * 0.5, 0.25, 1), 9.0, 1.6, sides=5, rings=3, bend=(s * 1.6, 1.6, 0)))
    m.pal = dict(mats={"eye": M_(EYE, base=0.5, lit=0.2, noise=0.0, min=0), "bone": M_(BONE, lit=0.34), "flesh": M_(FLESH, streak=0.06),
                       "chitin": M_(CHITIN, lit=0.36, streak=0.04)}, glow_mats=[])

    def detail(c, col, glow):
        col, glow = speckle(c, col, ["bone"], (118, 108, 90), density=0.06, size=0.8), glow
        col = edge_dark(c, col, ["bone"], k=0.8)
        P_ = c["P"]
        sel = np.isin(c["mats"], ["bone"]) & c["owned"]
        f1, f2, idv = cell3(P_, 3.0, 23)
        crack = sel & ((f2 - f1) < 0.05) & (idv > 0.5)
        col = np.where(crack[..., None], col * 0.62, col)
        return col, glow
    m.detail = detail
    m.hint = 512
    m.view = dict(center=(0, 14, 10), scale=2.4)
    m.gaits = gaits()
    return m


# ---------------------------------------------------------------- 動き
def chain(names, rx=0.0, ry=0.0, rz=0.0):
    return {b: {"rot": (rx, ry, rz)} for b in names}


def merge(*poses):
    out = {}
    for p in poses:
        for b, ch in p.items():
            out.setdefault(b, {}).update(ch)
    return out


def gaits():
    g = []
    idle = Gait("idle", 3.2)
    for k, b in enumerate(FRONT):
        idle.rot(b, W(0).s(1.6, 1, k * 30, "t"), W(0).s(2.4, 1, k * 40 + 90, "t"))
    for k, b in enumerate(REAR):
        idle.rot(b, None, W(0).s(1.6, 1, k * 35, "t"))
    idle.rot("jaw_l", None, W(-4).s(6, 2, 0, "t"))
    idle.rot("jaw_r", None, W(4).s(-6, 2, 0, "t"))
    g.append(idle)
    walk = Gait("walk", 1.1)
    for k, b in enumerate(FRONT):
        walk.rot(b, W(0).s(2.0, 1, k * 50, "t"), W(0).s(4.0, 1, k * 55, "t"))
    for k, b in enumerate(REAR):
        walk.rot(b, None, W(0).s(5.0, 1, k * 55 + 180, "t"))
    walk.rot("body", None, W(0).s(3.0, 1, 0, "t"))
    walk.rot("jaw_l", None, W(-6).s(5, 2, 0, "t"))
    walk.rot("jaw_r", None, W(6).s(-5, 2, 0, "t"))
    g.append(walk)
    rest = {}
    rear_back = merge(chain(["f5", "f4", "f3"], rx=-8), chain(["f2", "f1"], rx=-6), {"jaw_l": {"rot": (0, -28, 0)}, "jaw_r": {"rot": (0, 28, 0)}})
    strike = merge(chain(["f5", "f4"], rx=8), chain(["f3", "f2", "f1"], rx=9), {"f0": {"rot": (5, 0, 0)}, "jaw_l": {"rot": (0, 10, 0)}, "jaw_r": {"rot": (0, -10, 0)}})
    g.append(keys("bite", 1.2, [(0.0, rest), (0.45, rear_back), (0.6, strike), (0.9, strike), (1.2, rest)]))
    wind = merge(chain(REAR, ry=7), chain(["f5", "f4"], ry=-6))
    whip = merge(chain(REAR, ry=-9), chain(["f5", "f4"], ry=8))
    g.append(keys("sweep", 1.4, [(0.0, rest), (0.55, wind), (0.8, whip), (1.05, whip), (1.4, rest)]))
    dive = merge(chain(FRONT, rx=14), {"root": {"pos": (0, -18, 0)}})
    under = merge(chain(FRONT, rx=18), {"root": {"pos": (0, -70, 0)}})
    g.append(keys("burrow", 1.0, [(0.0, rest), (0.35, dive), (1.0, under)], hold=True))
    burst = merge(chain(FRONT, rx=-10), {"root": {"pos": (0, -8, 0)}, "jaw_l": {"rot": (0, -30, 0)}, "jaw_r": {"rot": (0, 30, 0)}})
    g.append(keys("emerge", 1.0, [(0.0, under), (0.5, burst), (1.0, rest)]))
    rise = merge(chain(["f5", "f4", "f3", "f2"], rx=-9), chain(["f1", "f0"], rx=-4), {"jaw_l": {"rot": (0, -34, 0)}, "jaw_r": {"rot": (0, 34, 0)}})
    shake1 = merge(rise, chain(["f2", "f1"], ry=5))
    shake2 = merge(rise, chain(["f2", "f1"], ry=-5))
    g.append(keys("roar", 1.6, [(0.0, rest), (0.4, rise), (0.6, shake1), (0.8, shake2), (1.0, shake1), (1.2, shake2), (1.6, rest)]))
    g.append(keys("hurt", 0.3, [(0.0, rest), (0.1, merge(chain(["f2", "f1"], rx=-5), chain(["r9", "r10"], ry=4))), (0.3, rest)]))
    fall = merge(chain(FRONT, rx=9), chain(REAR, ry=3), {"root": {"rot": (0, 0, 18), "pos": (0, -3, 0)},
                                                          "jaw_l": {"rot": (0, -20, 0)}, "jaw_r": {"rot": (0, 20, 0)}})
    g.append(keys("death", 1.6, [(0.0, rest), (0.5, merge(chain(FRONT, rx=-6))), (1.2, fall), (1.6, fall)], hold=True))
    return g


POSES = [("idle", 0.0), ("bite", 0.45), ("bite", 0.6), ("sweep", 0.8), ("burrow", 0.5), ("emerge", 0.5), ("roar", 0.6), ("death", 1.6)]
BOUNDS = (10, 6)
