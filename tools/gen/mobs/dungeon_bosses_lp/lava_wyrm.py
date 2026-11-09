"""熔岩の大蛇 (lava_wyrm)。熔岩の管の坑の主 (docs/design/55 の 7 章。2026-11-09 の頁で A)。
2026-11-09 の声「ボスのモデルもラフのままだし、めっちゃ小さい。クオリティ低い」で、ラフ (tools/rough/lowpoly/late_foes) から作り直した。
とぐろを巻いた玄武岩の大蛇が、上体を高く起こす。背は重なった黒い鱗の板で、板の間と腹から熔けた光。
頭は角の生えた楔の形、首のまわりに熾の襟巻 (ひだ)、口の奥が光る。

骨: body (とぐろの中心) の子に neck (→ head → jaw) と seg1 → seg2 → seg3 → seg4 (尾へ続くとぐろ)。
動き: idle・walk・bite (噛みつき)・breath (熔岩の吐き)・dive (熔岩に潜る)・hurt・death
"""
import math

import numpy as np

from blp import *  # noqa: F401,F403
from blp import keys, eye

BAS = [(20, 18, 20), (34, 30, 32), (50, 44, 46), (68, 60, 60), (88, 78, 76)]
BELLY = [(120, 40, 14), (176, 70, 22), (222, 112, 34), (250, 166, 64)]
MAG = [(160, 50, 16), (220, 96, 30), (252, 156, 56), (255, 214, 120), (255, 244, 200)]
HORN = [(30, 24, 22), (56, 46, 40), (90, 76, 64), (130, 114, 96)]
FRILL = [(80, 22, 14), (130, 40, 20), (180, 66, 30), (220, 104, 46)]
TOOTH = [(170, 160, 140), (210, 200, 180), (236, 228, 210)]

# とぐろの道すじ (body の中心のまわりを一回り半。前 (z 負) で首へ立ち上がる)
COIL_R = 15.0


def coil_point(t):
    """t: 0 (首の付け根、前) → 1 (尾の先)"""
    a = math.radians(-90 + t * 470)
    r = COIL_R * (1 - 0.42 * t)
    return np.array([math.cos(a) * r, 6.0 + 2.6 * (1 - t) + 1.2 * math.sin(t * math.pi * 3), math.sin(a) * r + 2.0])


def build():
    m = Mount("lava_wyrm", "熔岩の大蛇")
    m.bone("body", "root", (0, 6, 2))
    m.bone("neck", "body", (0, 10, -13))
    m.bone("head", "neck", (0, 46, -16))
    m.bone("jaw", "head", (0, 44.5, -18))
    segs = ["body", "seg1", "seg2", "seg3", "seg4"]
    cuts = [0.0, 0.25, 0.45, 0.65, 0.82, 1.0]
    for i in range(1, 5):
        m.bone(segs[i], segs[i - 1], tuple(coil_point(cuts[i])))
    # ---- とぐろ: 区間ごとに骨を分けた太い管 (隣の区間と少し重ねる)
    for i in range(5):
        t0, t1 = max(0.0, cuts[i] - 0.02), min(1.0, cuts[i + 1] + 0.02)
        n = 7
        pts = [coil_point(t0 + (t1 - t0) * k / (n - 1)) for k in range(n)]
        rad = [7.2 * (1 - 0.78 * (t0 + (t1 - t0) * k / (n - 1))) + 0.6 for k in range(n)]
        m.add(f"coil{i}", tub(segs[i], "basalt", pts, rad, [r_ * 0.86 for r_ in rad], sides=10, hint=(0, 1, 0)), core=True)
        # 腹の熔けた帯 (下側の細い管)
        m.add(f"belly{i}", tub(segs[i], "belly", [p + np.array([0, -r_ * 0.55, 0]) for p, r_ in zip(pts, rad)], [r_ * 0.62 for r_ in rad],
                               [r_ * 0.45 for r_ in rad], sides=8, hint=(0, 1, 0)))
        # 背の鱗の板 (重ねた板と、二枚おきに棘)
        for k in range(1, n - 1, 1):
            tt = t0 + (t1 - t0) * k / (n - 1)
            p = coil_point(tt)
            r_ = 7.2 * (1 - 0.78 * tt) + 0.6
            d = nrm(coil_point(min(1, tt + 0.01)) - coil_point(max(0, tt - 0.01)))
            m.add(f"plate{i}_{k}", ell(segs[i], "basalt", p + np.array([0, r_ * 0.78, 0]) + d * 0.6, (r_ * 0.62, r_ * 0.28, r_ * 0.5), segs=8, rings=2))
            if k % 2 == 1:
                m.add(f"spike{i}_{k}", cone(segs[i], "horn", p + np.array([0, r_ * 0.9, 0]), (0, 1, 0) + -d * 0.6, 1.4 + r_ * 0.35, r_ * 0.22, sides=5))
    m.add("tail_tip", cone("seg4", "horn", coil_point(1.0), nrm(coil_point(1.0) - coil_point(0.97)), 4.0, 1.4, sides=6, rings=2))
    # ---- 首: とぐろの前から高く立ち上がり、頭の下で前へ曲がる
    path = [np.array(p, float) for p in ((0, 8, -13), (0, 18, -17.5), (0, 28, -15.0), (0, 36.5, -11.5), (0, 43.5, -14.5))]
    rads = [6.6, 6.0, 5.4, 4.8, 4.4]
    m.add("neck", tub("neck", "basalt", path, rads, [r_ * 0.9 for r_ in rads], sides=10, hint=(1, 0, 0)), core=True)
    m.add("throat", tub("neck", "belly", [p + np.array([0, 0, -r_ * 0.5]) for p, r_ in zip(path, rads)], [r_ * 0.6 for r_ in rads], [r_ * 0.4 for r_ in rads],
                        sides=8, hint=(1, 0, 0)))
    for k in range(1, len(path)):
        p, r_ = path[k], rads[k]
        m.add(f"nplate{k}", ell("neck", "basalt", p + np.array([0, 0, r_ * 0.72]), (r_ * 0.7, r_ * 0.55, r_ * 0.32), segs=8, rings=2))
        m.add(f"nspike{k}", cone("neck", "horn", p + np.array([0, 0.6, r_ * 0.85]), (0, 0.6, 1), 2.6, 0.9, sides=5))
    # 熾の襟巻 (首のまわりのひだ。骨の筋を六本と、間の膜)
    collar = np.array([0, 38.5, -13.5])
    for k in range(7):
        a = math.radians(-160 + k * (320 / 6))
        tip = collar + np.array([math.sin(a) * 12.0, 3.4 + math.cos(a) * 2.2, math.cos(a) * 3.0 + 3.6])
        m.add(f"frill_rib{k}", tub("neck", "horn", [collar + np.array([math.sin(a) * 3.6, 0, math.cos(a) * 1.4 + 1.4]), tip], [0.9, 0.4], None, sides=4))
        if k < 6:
            a2 = math.radians(-160 + (k + 1) * (320 / 6))
            tip2 = collar + np.array([math.sin(a2) * 12.0, 3.4 + math.cos(a2) * 2.2, math.cos(a2) * 3.0 + 3.6])
            mid = collar + np.array([math.sin((a + a2) / 2) * 4.0, 0.4, math.cos((a + a2) / 2) * 1.4 + 1.6])
            m.add(f"frill_web{k}", slab("neck", "frill", [mid, tip, (tip + tip2) / 2 - np.array([0, 0.8, 0]), tip2], (0, 0.3, 1), 0.7))
    # ---- 頭: 楔の頭、眉の角、光る目、鼻の穴、上あごの牙。下あごは jaw
    hb = np.array([0, 46, -16])
    m.add("head", lof("head", "basalt", [rsec(-12.5, 49.2, 43.0, 4.8, mid=46, n=12, p=2.4), rsec(-16.0, 49.4, 43.6, 4.6, mid=46.2, n=12, p=2.4),
                                         rsec(-20.0, 48.4, 44.2, 3.8, mid=46.0, n=12, p=2.4), rsec(-24.0, 47.0, 44.6, 2.8, mid=45.8, n=12, p=2.4),
                                         rsec(-26.4, 46.0, 44.8, 1.8, mid=45.4, n=12, p=2.4)]), core=True)
    m.add("snout_glow", box("head", "magma", (0, 44.6, -21.0), (5.6, 0.6, 9.0), taper=(0.6, 1.0)))
    for s in (1, -1):
        eye(m, "head", hb + np.array([s * 3.4, 2.0, -5.6]), 0.8, (s * 0.5, 0.2, -1), (255, 190, 60), glow=True)
        m.add(f"brow{s}", ell("head", "basalt", hb + np.array([s * 3.2, 3.3, -5.0]), (1.8, 0.9, 2.6), segs=8, rings=2))
        m.add(f"horn{s}", cone("head", "horn", hb + np.array([s * 3.0, 3.4, -2.4]), (s * 0.5, 0.6, 1), 9.0, 1.5, sides=6, rings=4, bend=(s * 0.6, 2.6, 1.0)))
        m.add(f"horn_b{s}", cone("head", "horn", hb + np.array([s * 4.2, 1.2, -1.0]), (s * 1, 0.1, 0.8), 4.6, 0.9, sides=5, rings=2, bend=(0, 0.6, 0)))
        m.add(f"nostril{s}", ell("head", "magma", hb + np.array([s * 1.0, 0.6, -10.6]), (0.5, 0.3, 0.5), segs=5, rings=1))
        for k in range(3):
            m.add(f"tooth{s}{k}", cone("head", "tooth", hb + np.array([s * (2.4 - k * 0.6), -1.8, -6.0 - k * 2.4]), (0, -1, -0.1), 1.6 - k * 0.3, 0.45, sides=4))
    m.add("crest", tub("head", "horn", [hb + np.array([0, 3.4, -7.0]), hb + np.array([0, 4.2, -2.0]), hb + np.array([0, 3.6, 2.8])], [0.5, 0.9, 0.4], None, sides=4))
    m.add("jaw", lof("jaw", "basalt", [rsec(-13.5, 44.2, 41.6, 3.8, mid=43.0, n=10, p=2.3), rsec(-19.0, 44.0, 42.0, 3.2, mid=43.0, n=10, p=2.3),
                                       rsec(-24.6, 44.2, 43.0, 1.8, mid=43.6, n=10, p=2.3)]))
    m.add("mouth_glow", box("jaw", "magma", (0, 44.3, -19.0), (4.6, 0.4, 9.6), taper=(0.6, 1.0)))
    for s in (1, -1):
        for k in range(2):
            m.add(f"ltooth{s}{k}", cone("jaw", "tooth", (s * (1.8 - k * 0.5), 44.0, -20.0 - k * 2.6), (0, 1, -0.1), 1.2, 0.4, sides=4))

    m.pal = dict(mats={"eye": M_(MAG, base=0.7, lit=0.2, noise=0.0, min=1), "basalt": M_(BAS, lit=0.34, streak=0.04), "belly": M_(BELLY, lit=0.3),
                       "magma": M_(MAG, base=0.66, lit=0.2, min=1), "horn": M_(HORN, lit=0.36), "frill": M_(FRILL, lit=0.3), "tooth": M_(TOOTH, lit=0.36)},
                 glow_mats=["eye", "magma"])

    def detail(c, col, glow):
        P = c["P"]
        sel = np.isin(c["mats"], ["basalt"]) & c["owned"]
        f1, f2, idv = cell3(P, 2.4, 19)
        seam = sel & ((f2 - f1) < 0.028) & (idv > 0.35)
        col = np.where(seam[..., None], np.array([250, 132, 40]), col)
        belly = np.isin(c["mats"], ["belly"]) & c["owned"]
        b1, b2, bid = cell3(P, 1.6, 23)
        hot = belly & (bid > 0.72)
        col = np.where(hot[..., None], np.array([255, 190, 80]), col)
        col = edge_dark(c, col, ["horn"], k=0.72)
        return col, glow | seam | hot
    m.detail = detail
    m.hint = 512
    m.view = dict(center=(0, 24, 0), scale=2.2)
    m.gaits = gaits()
    return m


def gaits():
    g = []
    idle = Gait("idle", 4.0)
    idle.rot("neck", W(0).s(3.0, 1, 0, "t"), W(0).s(8.0, 1, 90, "t"))
    idle.rot("head", W(0).s(4.0, 1, 45, "t"))
    idle.rot("jaw", W(0).s(4.0, 2, 0, "t"))
    for i, b in enumerate(("seg1", "seg2", "seg3", "seg4")):
        idle.rot(b, None, W(0).s(5.0, 1, i * 60, "t"))
    g.append(idle)
    walk = Gait("walk", 2.0)
    walk.rot("neck", W(4).s(3.0, 1, 0, "t"), W(0).s(10.0, 1, 90, "t"))
    walk.rot("head", W(0).s(4.0, 1, 45, "t"))
    for i, b in enumerate(("seg1", "seg2", "seg3", "seg4")):
        walk.rot(b, None, W(0).s(9.0, 1, i * 70, "t"))
    walk.rot("body", None, W(0).s(5.0, 1, 0, "t"))
    g.append(walk)
    rest = {}
    reel = {"neck": {"rot": (-24, 0, 0)}, "head": {"rot": (-10, 0, 0)}, "jaw": {"rot": (20, 0, 0)}}
    strike = {"neck": {"rot": (34, 0, 0)}, "head": {"rot": (16, 0, 0)}, "jaw": {"rot": (40, 0, 0)}, "body": {"pos": (0, 0, -3)}}
    g.append(keys("bite", 1.0, [(0.0, rest), (0.5, reel), (0.7, strike), (0.8, {"neck": {"rot": (34, 0, 0)}, "head": {"rot": (16, 0, 0)}}), (1.0, rest)]))
    rise = {"neck": {"rot": (-22, 0, 0)}, "head": {"rot": (-16, 0, 0)}, "jaw": {"rot": (10, 0, 0)}}
    spew = {"neck": {"rot": (14, 0, 0)}, "head": {"rot": (20, 0, 0)}, "jaw": {"rot": (48, 0, 0)}}
    spew2 = {"neck": {"rot": (14, 18, 0)}, "head": {"rot": (20, 0, 0)}, "jaw": {"rot": (48, 0, 0)}}
    g.append(keys("breath", 2.0, [(0.0, rest), (1.0, rise), (1.3, spew), (1.6, spew2), (1.8, spew), (2.0, rest)]))
    down = {"body": {"pos": (0, -30, 0)}, "neck": {"rot": (40, 0, 0)}, "head": {"rot": (20, 0, 0)}}
    g.append(keys("dive", 3.0, [(0.0, rest), (0.6, down), (2.6, down), (3.0, rest)]))
    g.append(keys("hurt", 0.3, [(0.0, rest), (0.1, {"neck": {"rot": (-12, 0, 6)}, "head": {"rot": (-10, 0, 0)}}), (0.3, rest)]))
    droop = {"neck": {"rot": (40, 0, 10)}, "head": {"rot": (20, 0, 0)}, "jaw": {"rot": (24, 0, 0)}}
    flat = {"neck": {"rot": (80, 0, 20)}, "head": {"rot": (10, 0, 10)}, "jaw": {"rot": (18, 0, 0)}, "body": {"pos": (0, -2, 0)}}
    g.append(keys("death", 2.0, [(0.0, rest), (0.6, droop), (1.4, flat), (2.0, flat)], hold=True))
    return g


POSES = [("idle", 0.0), ("walk", 0.5), ("bite", 0.5), ("bite", 0.7), ("breath", 1.3), ("dive", 0.6), ("hurt", 0.1), ("death", 2.0)]
BOUNDS = (7, 7)
