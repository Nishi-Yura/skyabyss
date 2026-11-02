# -*- coding: utf-8 -*-
"""新しい乗り物の見た目のラフ (2026-11-02 の声「ペットの種類を増やしたい。飛べるペットがほしい」)。
題は三つ (深淵 = 五章、月 = 六章、空 = 終章のあとの飛ぶ乗り物)、題ごとに三案。形は乗り物の低ポリの部品 (tools/gen/mobs/mounts_lp/mlp.py) で作り、
鞍・乗った人も入れて、題ごとに一枚の比べシートを書く。選んだ案は mounts_lp/<id>.py に移して作り込む (動き・絵・骨)。

    python -I tools/rough/lowpoly/mounts2/roughs.py [abyss|moon|sky ...]   → docs/review/roughs_mounts2/<題>.png
"""
from __future__ import annotations

import math
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[3]
sys.path.insert(0, str(ROOT / "tools/gen/mobs/mounts_lp"))
from mlp import *  # noqa: E402,F401,F403
import build_all as BA  # noqa: E402

OUT = ROOT / "docs/review/roughs_mounts2"

LEATH = [(46, 28, 18), (76, 48, 28), (108, 72, 42), (142, 102, 62), (172, 132, 86)]
LEATHD = [(28, 18, 14), (48, 32, 22), (70, 48, 32), (94, 66, 44)]
BRASS = [(78, 56, 22), (126, 94, 38), (178, 138, 62), (222, 184, 98), (248, 222, 150)]
EYE = [(8, 8, 14), (16, 16, 26), (30, 30, 42)]


def M_(ramp, **kw):
    d = dict(ramp=ramp, base=0.58, lit=0.3, noise=0.05, facevar=0.02, crack=0, min=1)
    d.update(kw)
    return d


def pal(mats, glow=(), cloth=((40, 50, 92), (56, 72, 128), (80, 100, 164), (116, 136, 196))):
    base = {"eye": M_(EYE, base=0.5, lit=0.2, noise=0.0, min=0), "leath": M_(LEATH, min=0, streak=0.06), "leathd": M_(LEATHD, min=0),
            "clothr": M_(list(cloth), min=0, stripe=4, axis="u", lit=0.24), "brass": M_(BRASS, min=0, lit=0.36)}
    base.update(mats)
    return dict(mats=base, glow_mats=list(glow))


def sides(fn):
    for s, k in ((1, "l"), (-1, "r")):
        fn(s, k)


def slab(bone, mat, poly, normal, th):
    """平らな多角形 (凸) を厚み th の板にする (膜・ひれ)。"""
    n = nrm(np.asarray(normal, float))
    a = [np.asarray(p, float) + n * th / 2 for p in poly]
    b = [np.asarray(p, float) - n * th / 2 for p in poly]
    return lof(bone, mat, [a, b])


def wing(bone, mat, stations, n=10, mats=None):
    """翼: stations = [(x, z_前, z_後, y, 厚み)]。x 方向へ並ぶ断面 (z-y 平面の楕円) をつなぐ。"""
    rings = []
    for x, z0, z1, y, th in stations:
        zc, hz = (z0 + z1) / 2, abs(z1 - z0) / 2
        rings.append([np.array([x, y + th / 2 * math.sin(math.radians(360 * k / n)), zc + hz * math.cos(math.radians(360 * k / n))]) for k in range(n)])
    return lof(bone, mat, rings, mats=mats)


def insect_leg(m, k, hip, knee, foot, mat, w=3.0, claw="chitin_d"):
    up, lo = "leg_" + k, "leg_" + k + "_lo"
    m.bone(up, "body", hip)
    m.bone(lo, up, knee)
    hip, knee, foot = (np.asarray(v, float) for v in (hip, knee, foot))
    m.add(f"{k}_coxa", ell(up, mat, hip, (w * 0.8, w * 0.8, w * 0.8), segs=7, rings=2))
    m.add(f"{k}_femur", limb(up, mat, [hip, lerp(hip, knee, 0.5) + np.array([0, 1.0, 0]), knee], [w, w * 1.15, w * 0.8], [w * 0.8, w, w * 0.7], n=8))
    m.add(f"{k}_knee", ell(lo, mat, knee, (w * 0.55, w * 0.55, w * 0.55), segs=6, rings=2))
    m.add(f"{k}_tibia", limb(lo, mat, [knee, lerp(knee, foot, 0.5), foot + np.array([0, 1.2, 0])], [w * 0.75, w * 0.62, w * 0.45],
                             [w * 0.7, w * 0.6, w * 0.42], n=8))
    d = nrm(foot - knee)
    m.add(f"{k}_claw", cone(lo, claw, foot + np.array([0, 1.4, 0]), (d[0] * 0.4, -1, d[2] * 0.4 - 0.2), 2.4, w * 0.4, sides=4, rings=1))


def splay_foot(m, bone, mat, c, s, toes=5, toe_len=3.0, disc="", fwd=-1.0):
    """広がった指 (守宮・蛙)。指先に吸盤の円盤 (disc の材質)。"""
    c = np.asarray(c, float)
    m.add(f"{bone}_palm", ell(bone, mat, c, (2.4, 1.2, 2.4), segs=7, rings=2))
    for j in range(toes):
        a = math.radians(-70 + 140 * j / (toes - 1)) * s
        d = np.array([math.sin(a) * 0.9 * s * s, 0, math.cos(a) * fwd])
        d = nrm(np.array([math.sin(a), -0.08, fwd * math.cos(a)]))
        tip = c + d * toe_len
        m.add(f"{bone}_toe{j}", tub(bone, mat, [c + d * 0.6, tip], [0.75, 0.55], None, sides=5))
        if disc:
            m.add(f"{bone}_disc{j}", ell(bone, disc, tip + np.array([0, -0.1, 0]), (0.95, 0.5, 0.95), segs=6, rings=1))


# ====================================================================== 深淵 (五章)

def abyss_a():
    """A 灯り守の大守宮: 坑道の灯り番に飼われた大きな守宮。平たい体、吸盤の指、背と尾の先に灯りの袋 (光る)。"""
    SK = [(18, 28, 30), (28, 42, 44), (40, 58, 58), (54, 76, 74), (72, 96, 92), (94, 118, 112)]
    BELLY = [(120, 120, 108), (160, 158, 140), (196, 192, 170), (222, 218, 196)]
    LAMP = [(170, 92, 24), (226, 150, 52), (252, 202, 104), (255, 236, 176), (255, 250, 228)]
    m = Mount("abyss_gecko", "灯り守の大守宮")
    m.bone("body", "root", (0, 16, 0))
    m.bone("head", "body", (0, 18, -14))
    m.bone("tail", "body", (0, 17, 13))
    body = [rsec(-15, 20.6, 12.8, 5.0, 16.4, n=14, p=2.6), rsec(-11, 22.8, 11.4, 8.0, 16.6, n=14, p=2.6), rsec(-4, 23.4, 11.0, 9.0, 16.8, n=14, p=2.6),
            rsec(4, 23.0, 11.0, 9.0, 16.8, n=14, p=2.6), rsec(10, 22.0, 12.0, 7.4, 16.8, n=14, p=2.6), rsec(15, 20.0, 13.4, 5.0, 16.8, n=14, p=2.6)]
    m.add("torso", lof("body", "skin", body), core=True)
    hd = [rsec(-13, 22.0, 14.0, 5.6, 17.6, n=14, p=2.4), rsec(-17, 23.2, 13.4, 7.2, 17.8, n=14, p=2.4), rsec(-21, 22.4, 13.8, 7.4, 17.8, n=14, p=2.4),
          rsec(-25, 20.4, 14.4, 5.8, 17.4, n=14, p=2.4), rsec(-28, 18.6, 15.2, 3.0, 17.0, n=14, p=2.2)]
    m.add("skull", lof("head", "skin", hd), core=True)
    m.add("jaw", ell("head", "belly", (0, 15.4, -21.0), (6.0, 1.6, 5.6), segs=10, rings=2))
    sk = tri_arrays(m.faces_of(["skull"]))

    def eye(s, k):
        d = nrm(np.array([s * 0.86, 0.42, -0.28]))
        c = ray_far(sk, (0, 18.6, -19.6), d) - d * 0.9
        m.add(f"eye_{k}", ell("head", "eye", c, (2.4, 2.3, 2.5), segs=9, rings=3))
        m.eyes.append(dict(c=c, r=2.6, dir=tuple(d), a=2.0, b=1.0, bone="head", iris=(236, 196, 96), lid=(28, 42, 44), pa=0.2, pb=0.9, glow=True))
    sides(eye)
    tail = [(0, 17.0, 11.0), (0, 17.2, 19.0), (0, 18.4, 27.0), (0, 21.0, 34.0), (0, 25.0, 38.6), (0, 29.4, 39.6)]
    m.add("tail", limb("tail", "skin", tail, [10.0, 8.4, 6.4, 4.8, 3.6, 2.6], [8.0, 6.6, 5.2, 4.0, 3.2, 2.4], n=10))
    m.add("tail_lamp", ell("tail", "lamp", (0, 31.6, 39.8), (2.7, 3.1, 2.7), segs=9, rings=3))
    m.add("tail_lamp_cap", cone("tail", "skin_d", (0, 33.8, 39.8), (0, 1, 0), 1.6, 1.6, sides=6, rings=1))
    for i, z in enumerate((14.0, 19.0, 24.0, 29.0)):
        t = (z - 11) / 28
        m.add(f"spine{i}", cone("tail", "skin_d", (0, 17.2 + 8 * t * t + 3.4 - 1.2 * t, z), (0, 1, 0.3), 2.0 - 0.8 * t, 1.3 - 0.4 * t, sides=4, rings=1))
    # 灯りの袋 (肩と腰のわき。鞍の下を避ける)
    bt = tri_arrays(m.faces_of(["torso"]))

    def sacs(s, k):
        for j, (z, r) in enumerate(((-11.5, 2.4), (9.5, 2.2), (13.5, 1.7))):
            d = nrm(np.array([s * 0.8, 0.6, 0]))
            p = ray_far(bt, (0, 16.6, z), d)
            m.add(f"sac_{k}{j}", ell("body", "lamp", p - d * 0.6, (r, r * 0.8, r * 1.1), segs=8, rings=2))
    sides(sacs)

    def leg(k, s, z):
        m.bone("leg_" + k, "body", (s * 7.0, 15.0, z))
        m.bone("leg_" + k + "_lo", "leg_" + k, (s * 13.0, 13.4, z - 1.0))
        hip, knee, foot = np.array([s * 5.0, 15.6, z]), np.array([s * 13.0, 13.4, z - 1.0]), np.array([s * 14.2, 1.6, z - 3.0])
        m.add(f"{k}_up", limb("leg_" + k, "skin", [hip, lerp(hip, knee, 0.5) + np.array([0, 0.8, 0]), knee], [5.2, 4.6, 3.8], [4.6, 4.2, 3.6], n=8, side=(0, 1, 0)))
        m.add(f"{k}_elbow", ell("leg_" + k + "_lo", "skin", knee, (2.2, 2.2, 2.2), segs=7, rings=2))
        m.add(f"{k}_lo", limb("leg_" + k + "_lo", "skin", [knee, lerp(knee, foot, 0.5), foot + np.array([0, 0.8, 0])], [3.6, 3.0, 2.6], [3.4, 2.8, 2.4], n=8))
        splay_foot(m, "leg_" + k + "_lo", "skin", foot + np.array([0, 0.4, 0]), s, toes=5, toe_len=3.4, disc="belly", fwd=-1.0 if z < 0 else -0.3)
    for k, s, z in (("fl", 1, -10.0), ("fr", -1, -10.0), ("bl", 1, 8.0), ("br", -1, 8.0)):
        leg(k, s, z)
    m.saddle_kw = dict(cy=17.0, length=6.0, half_angle=46, seat_len=5.0, seat_half=32, blanket=0.8,
                       mats=dict(pad="clothr", trim="brass", seat="leath", strap="leathd", metal="brass"))
    m.prefer_pose = (-30.0, 34.0)
    m.pal = pal({"skin": M_(SK, streak=0.04, ao=0.12), "skin_d": M_(SK[:4], min=0), "belly": M_(BELLY),
                 "lamp": M_(LAMP, base=0.64, lit=0.2, noise=0.03)}, glow=["lamp"], cloth=((60, 30, 20), (90, 46, 28), (124, 66, 36), (160, 92, 50)))

    def detail(c, col, glow):
        col = scales(c, col, ["skin"], size=1.3, edge=0.12, k_edge=0.8)
        col = countershade(c, col, ["skin"], (176, 172, 150), top_k=0.88, belly_from=-0.2)
        col = spots(c, col, ["skin"], (20, 30, 32), scale=0.22, thr=0.7, a=0.6, up_only=True)
        return col, glow
    m.detail = detail
    m.view = dict(center=(0, 16, 6), scale=4.0)
    m.caption = ["坑道の灯り番に飼われた大守宮", "平たい体・吸盤の指・肩と腰と尾の先の灯り袋", "低く速い。壁や天井は歩かない（見た目だけ）"]
    return m


def abyss_b():
    """B 塩殻の甲虫: 塩の層の大甲虫。六本の脚、前へ突き出た一本角、背の殻に塩の結晶。人は胸と殻のあいだに座る。"""
    CH = [(20, 14, 12), (34, 24, 20), (52, 36, 28), (72, 52, 40), (96, 72, 56), (124, 96, 76)]
    SALT = [(176, 168, 170), (206, 198, 200), (230, 224, 226), (246, 242, 244), (255, 252, 252)]
    PINK = [(150, 104, 112), (190, 138, 146), (222, 176, 182), (244, 212, 216)]
    m = Mount("salt_beetle", "塩殻の甲虫")
    m.bone("body", "root", (0, 18, 0))
    m.bone("head", "body", (0, 16, -16))
    thor = [rsec(-15.0, 19.6, 12.6, 5.6, 16.0, n=16, p=2.3), rsec(-12.5, 23.6, 11.0, 8.8, 16.6, n=16, p=2.3), rsec(-7.0, 25.0, 10.8, 10.0, 17.0, n=16, p=2.3),
            rsec(-1.6, 24.4, 11.2, 9.6, 17.0, n=16, p=2.3)]
    m.add("thorax", lof("body", "chitin", thor), core=True)
    ely = [rsec(-2.6, 25.2, 11.0, 10.2, 17.2, n=16, p=2.1), rsec(3.0, 28.0, 10.2, 12.0, 17.6, n=16, p=2.1), rsec(10.0, 28.4, 10.0, 12.6, 17.8, n=16, p=2.1),
           rsec(16.5, 25.6, 10.8, 10.6, 17.6, n=16, p=2.1), rsec(21.0, 21.0, 12.6, 6.4, 16.8, n=16, p=2.1), rsec(23.4, 17.6, 14.0, 2.4, 15.8, n=16, p=2.1)]
    m.add("elytra", lof("body", "chitin", ely), core=True)
    m.add("seam", tub("body", "chitin_d", [(0, 27.6, 3.0), (0, 28.6, 10.0), (0, 25.9, 17.0), (0, 21.2, 21.4)], [0.7, 0.7, 0.6, 0.5], None, sides=5))
    m.add("head", ell("head", "chitin", (0, 16.4, -17.6), (5.8, 5.0, 5.2), segs=10, rings=3))
    m.add("horn", cone("head", "chitin_d", (0, 19.6, -19.6), (0, 0.4, -1), 16.0, 3.4, sides=6, rings=4, bend=(0, 7.0, 0), rb=2.6))
    m.add("horn_fork", cone("head", "chitin_d", (0, 26.0, -31.0), (0, 1, 0.15), 4.0, 1.4, sides=5, rings=1))

    def front(s, k):
        m.add(f"mandible_{k}", cone("head", "chitin_d", (s * 3.4, 14.2, -21.0), (s * 0.15, -0.15, -1), 7.4, 2.0, sides=5, rings=3, bend=(-s * 3.0, 0, 0)))
        c = np.array([s * 4.0, 17.6, -19.6])
        m.add(f"eye_{k}", ell("head", "eye", c, (1.3, 1.4, 1.3), segs=7, rings=2))
        m.eyes.append(dict(c=c, r=1.5, dir=(s * 0.8, 0.2, -0.6), a=1.0, b=1.0, bone="head", iris=(40, 40, 46), lid=(20, 14, 12), pa=0.5, pb=0.5))
        m.add(f"antenna_{k}", tub("head", "chitin_d", [(s * 2.2, 19.0, -20.2), (s * 4.0, 23.0, -24.0), (s * 6.0, 25.0, -27.4)], [0.5, 0.4, 0.3], None, sides=4))
        m.add(f"antenna_club_{k}", ell("head", "chitin_d", (s * 6.2, 25.2, -27.8), (0.9, 0.8, 1.2), segs=6, rings=1))
    sides(front)
    # 六本の脚 (前・中・後)
    for k, s, z, dz in (("fl", 1, -11.0, -4.0), ("fr", -1, -11.0, -4.0), ("ml", 1, -2.0, 0.0), ("mr", -1, -2.0, 0.0), ("bl", 1, 8.0, 5.0), ("br", -1, 8.0, 5.0)):
        insect_leg(m, k, (s * 7.6, 13.0, z), (s * 13.6, 17.0, z + dz * 0.5), (s * 15.4, 1.0, z + dz), "chitin", w=4.2)
    # 塩の結晶 (殻の後ろと胸の縁。鞍の下は避ける)
    et = tri_arrays(m.faces_of(["elytra", "thorax"]))
    n = 0
    for i, (a, z) in enumerate(((50, 8.0), (-50, 8.0), (30, 12.5), (-30, 12.5), (0, 15.5), (65, 15.0), (-65, 15.0), (20, 19.0), (-20, 19.0),
                                (75, -10.0), (-75, -10.0), (60, -13.0), (-60, -13.0))):
        d = np.array([math.sin(math.radians(a)), math.cos(math.radians(a)), 0])
        p = ray_far(et, (0, 17.0, z), d)
        if p is None:
            continue
        for j in range(3):
            ang = math.radians(j * 120 + i * 37)
            dd = nrm(d + np.array([math.cos(ang) * 0.35, 0.1, math.sin(ang) * 0.35]))
            m.add(f"salt{n}", cone("body", "salt" if (i + j) % 3 else "pink", p - d * 0.6, dd, 3.4 + 1.6 * hash1(i, j), 1.2, sides=5, rings=1))
            n += 1
    m.saddle_kw = dict(cy=18.0, length=5.6, half_angle=50, seat_len=5.0, seat_half=32, blanket=0.8,
                       mats=dict(pad="clothr", trim="brass", seat="leath", strap="leathd", metal="brass"))
    m.prefer_pose = (-34.0, 40.0)
    m.pal = pal({"chitin": M_(CH, lit=0.4, noise=0.03, streak=0.05), "chitin_d": M_(CH[:4], min=0, lit=0.4), "salt": M_(SALT, lit=0.36, noise=0.03),
                 "pink": M_(PINK, lit=0.36)}, cloth=((86, 82, 90), (112, 108, 118), (146, 140, 152), (186, 180, 192)))

    def detail(c, col, glow):
        col = plates(c, col, ["chitin"], size=3.6, edge=0.05, rings=0.25, k_edge=0.7)
        col = speckle(c, col, ["chitin"], (220, 214, 216), density=0.05, size=0.9)
        return col, glow
    m.detail = detail
    m.view = dict(center=(0, 16, 0), scale=3.8)
    m.caption = ["塩の層の大甲虫", "六本の脚・前へ突き出た一本角・殻に塩の結晶", "重く、跳びは低いが段差をよく越える"]
    return m


def abyss_c():
    """C 骨の大狼: 骨の層をうろつく大狼。毛の下から肋の骨がのぞき、顔は骨の面。目の奥に青い灯。"""
    FUR = [(14, 14, 18), (24, 24, 30), (36, 36, 44), (52, 52, 62), (72, 72, 84)]
    BONE = [(120, 112, 96), (162, 152, 132), (200, 190, 168), (226, 218, 198), (244, 238, 222)]
    SOUL = [(30, 90, 110), (60, 160, 180), (130, 220, 230), (210, 248, 252)]
    m = Mount("bone_wolf", "骨の大狼")
    m.bone("body", "root", (0, 22, 0))
    m.bone("neck", "body", (0, 26, -12))
    m.bone("head", "neck", (0, 29, -18))
    m.bone("tail", "body", (0, 25, 13))
    quad_bones(m, {"fl": ((4.0, 20.0, -11.0), (4.2, 10.4, -11.6)), "fr": ((-4.0, 20.0, -11.0), (-4.2, 10.4, -11.6)),
                   "bl": ((4.0, 21.0, 8.0), (4.2, 10.6, 10.4)), "br": ((-4.0, 21.0, 8.0), (-4.2, 10.6, 10.4))})
    body = [rsec(-15, 27.4, 17.0, 5.6, 22.4, n=14), rsec(-10, 30.0, 15.4, 8.0, 22.8, n=14), rsec(-3, 30.2, 15.8, 8.2, 23.0, n=14),
            rsec(4, 29.2, 16.6, 7.4, 23.0, n=14), rsec(10, 28.2, 18.0, 6.4, 23.2, n=14), rsec(14, 26.4, 19.6, 4.6, 23.2, n=14)]
    m.add("torso", lof("body", "fur", body), core=True)
    m.add("neck", limb("neck", "fur", [(0, 23.0, -9.0), (0, 25.6, -13.0), (0, 28.4, -16.6), (0, 30.0, -18.4)], [9.4, 8.2, 6.6, 6.0], [9.4, 8.2, 6.8, 6.2], n=12))
    for i in range(9):
        t = i / 8
        p = lerp((0, 31.0, -6.0), (0, 33.0, -17.0), t)
        a = math.radians(-40 + 80 * ((i * 5) % 9) / 8)
        m.add(f"mane{i}", cone("neck", "fur_d", p + np.array([math.sin(a) * 3.0, 0, 0]), (math.sin(a) * 0.7, 0.4, 1.0), 7.0 - 2.4 * t, 2.2, sides=4, rings=2,
                               bend=(0, -1.6, 0.6)))
    bt = tri_arrays(m.faces_of(["torso"]))

    def ribs(s, k):
        for j, z in enumerate((-6.0, -1.0, 4.0)):
            pts = []
            for a in (22, 2, -18, -38, -58):
                d = np.array([s * math.cos(math.radians(a)), math.sin(math.radians(a)), 0])
                p = ray_far(bt, (0, 22.6, z), d)
                pts.append(p + d * 0.5 + np.array([0, 0, 0.6 * (a / 60.0)]))
            m.add(f"rib_{k}{j}", tub("body", "bone", pts, [0.95, 1.0, 0.95, 0.8, 0.6], None, sides=5))
    sides(ribs)
    for i, z in enumerate((-12.0, 9.0, 12.0)):
        m.add(f"vert{i}", cone("body", "bone", (0, 28.0 - 0.1 * z, z), (0, 1, 0.2), 2.4, 1.6, sides=4, rings=1))
    # 骨の面の頭
    piv = m.pv("head")
    hd = [rsec(-15.6, 34.0, 26.4, 4.4, 30.2, n=12), rsec(-19.0, 34.8, 26.6, 4.6, 30.4, n=12), rsec(-22.0, 33.0, 27.4, 3.4, 30.2, n=12),
          rsec(-25.2, 31.4, 27.8, 2.2, 29.8, n=12), rsec(-27.6, 30.6, 28.2, 1.4, 29.6, n=12)]
    m.add("skull", lof("head", "bone", hd), core=True)
    m.add("jaw", lof("head", "bone", [rsec(-16.4, 28.0, 25.0, 3.6, 26.6, n=10), rsec(-21.0, 27.6, 25.4, 3.0, 26.6, n=10),
                                      rsec(-26.0, 27.8, 26.4, 1.6, 27.0, n=10)]))
    for j in range(5):
        x = (j - 2) * 1.2
        m.add(f"tooth{j}", cone("head", "bone", (x, 27.4, -25.0 + abs(x) * 0.8), (0, -1, -0.1), 1.8, 0.5, sides=3, rings=1))
    sk = tri_arrays(m.faces_of(["skull"]))

    def head_side(s, k):
        d = nrm(np.array([s * 0.8, 0.25, -0.55]))
        c = ray_far(sk, (0, 31.0, -20.0), d) - d * 0.9
        m.add(f"socket_{k}", ell("head", "eye", c, (1.5, 1.2, 1.4), segs=8, rings=2))
        m.eyes.append(dict(c=c, r=1.6, dir=tuple(d), a=0.6, b=0.6, bone="head", iris=(120, 230, 240), lid=(10, 10, 14), pa=0.6, pb=0.6, glow=True))
        ep = np.array([s * 2.6, 33.6, -16.4])
        m.add(f"ear_{k}", tub("head", "fur", [ep, ep + np.array([s * 0.8, 3.4, 0.6]), ep + np.array([s * 1.4, 6.2, 1.4])], [2.2, 1.5, 0.2], [1.2, 0.9, 0.2], sides=4))
    sides(head_side)
    tail = [(0, 25.4, 11.6), (0, 26.4, 17.0), (0, 25.6, 23.0), (0, 22.6, 28.4), (0, 19.0, 31.0)]
    m.add("tail", limb("tail", "fur", tail, [3.0, 5.4, 6.0, 4.8, 1.4], [3.0, 5.4, 5.8, 4.6, 1.4], n=10))
    for k in ("fl", "fr"):
        s = 1 if k.endswith("l") else -1
        hoof_leg(m, k, None, (s * 4.2, 3.8, -12.4), (6.8, 4.4, 3.4), (7.4, 4.6, 3.6), mat="fur", lo_mat="fur", hoof_mat="bone", hoof=(4.2, 2.4, 4.8), foot="paw")
    for k in ("bl", "br"):
        s = 1 if k.endswith("l") else -1
        hoof_leg(m, k, (s * 4.4, 15.0, 6.0), (s * 4.2, 3.8, 9.6), (7.6, 4.4, 3.4), (10.4, 4.8, 3.6), mat="fur", lo_mat="fur", hoof_mat="bone",
                 hoof=(4.2, 2.4, 4.8), foot="paw")
    m.saddle_kw = dict(cy=22.4, length=6.2, half_angle=52, seat_len=5.2, seat_half=34, blanket=0.8,
                       mats=dict(pad="clothr", trim="brass", seat="leath", strap="leathd", metal="brass"))
    m.prefer_pose = (-30.0, 26.0)
    m.pal = pal({"fur": M_(FUR, streak=0.1, ao=0.14), "fur_d": M_(FUR[:4], streak=0.1, min=0), "bone": M_(BONE, lit=0.34, noise=0.04),
                 "soul": M_(SOUL, base=0.64, lit=0.2)}, glow=["soul"], cloth=((40, 40, 46), (58, 58, 66), (80, 80, 90), (110, 110, 122)))

    def detail(c, col, glow):
        col = fur(c, col, ["fur", "fur_d"], scale=(1.0, 0.24, 1.0), amt=0.12)
        col = speckle(c, col, ["bone"], (130, 120, 100), density=0.08, size=0.7)
        return col, glow
    m.detail = detail
    m.view = dict(center=(0, 24, -2), scale=3.9)
    m.caption = ["骨の層をうろつく大狼", "毛の下からのぞく肋・骨の面・目の奥の青い灯", "怖さを残す見た目（体の芯は毛で暗い）"]
    return m


# ====================================================================== 月 (六章)

def moon_a():
    """A 月の兎: 月の面の大兎。長い耳、大きな後ろ足、白銀の毛にクレーターの斑、額に三日月。"""
    WH = [(120, 124, 140), (160, 164, 180), (196, 200, 214), (224, 228, 238), (244, 246, 252)]
    GR = [(80, 84, 100), (108, 112, 128), (138, 142, 158)]
    PINK = [(150, 110, 130), (196, 150, 170), (228, 190, 206)]
    GLOW = [(150, 190, 240), (196, 224, 252), (236, 248, 255)]
    m = Mount("moon_hare", "月の兎")
    m.bone("body", "root", (0, 18, 2))
    m.bone("head", "body", (0, 27, -12))
    body = [rsec(-12, 25.0, 16.0, 6.0, 20.4, n=14, p=2.0), rsec(-8, 29.0, 12.0, 9.0, 20.6, n=14, p=2.0), rsec(-1, 31.0, 10.0, 10.6, 20.6, n=14, p=2.0),
            rsec(6, 31.0, 9.2, 11.0, 20.4, n=14, p=2.0), rsec(12, 28.0, 9.6, 10.0, 19.8, n=14, p=2.0), rsec(16, 23.4, 12.0, 7.0, 18.4, n=14, p=2.0),
            rsec(18.6, 19.2, 14.4, 3.4, 17.0, n=14, p=2.0)]
    m.add("torso", lof("body", "fur", body), core=True)
    hd = [rsec(-9, 33.0, 23.0, 5.6, 28.0, n=14, p=2.0), rsec(-12, 35.0, 22.0, 7.0, 28.4, n=14, p=2.0), rsec(-16, 34.6, 22.4, 7.0, 28.4, n=14, p=2.0),
          rsec(-19.5, 31.6, 23.6, 5.4, 27.8, n=14, p=2.0), rsec(-21.6, 29.2, 24.6, 3.0, 27.2, n=14, p=2.0)]
    m.add("skull", lof("head", "fur", hd), core=True)
    m.add("nose", ell("head", "pink", (0, 27.6, -21.8), (1.2, 0.9, 0.8), segs=6, rings=1))
    m.add("muzzle", ell("head", "fur", (0, 26.0, -20.0), (3.2, 2.0, 2.2), segs=8, rings=2))
    sk = tri_arrays(m.faces_of(["skull"]))

    def side_parts(s, k):
        d = nrm(np.array([s * 0.86, 0.2, -0.46]))
        c = ray_far(sk, (0, 29.6, -15.6), d) - d * 0.8
        m.add(f"eye_{k}", ell("head", "eye", c, (1.9, 2.1, 1.9), segs=8, rings=2))
        m.eyes.append(dict(c=c, r=2.2, dir=tuple(d), a=1.5, b=1.6, bone="head", iris=(110, 150, 220), lid=(70, 74, 90), pa=0.6, pb=0.6))
        m.bone("ear_" + k, "head", (s * 2.6, 34.0, -12.0))
        path = [(s * 2.6, 33.4, -12.4), (s * 3.4, 41.0, -10.6), (s * 4.0, 49.0, -7.6), (s * 4.3, 55.4, -4.4)]
        m.add(f"ear_{k}", tub("ear_" + k, "fur", path, [2.6, 3.1, 2.8, 1.2], [1.3, 1.3, 1.1, 0.6], sides=6, hint=(0, 0, 1)))
        inner = [(s * 2.7, 35.0, -13.4), (s * 3.5, 41.4, -11.7), (s * 4.0, 48.4, -8.8)]
        m.add(f"earin_{k}", tub("ear_" + k, "pink", inner, [1.4, 1.9, 1.3], [0.5, 0.5, 0.4], sides=5, hint=(0, 0, 1)))
        cp = ray_far(sk, (0, 26.4, -15.0), (s * 1.0, -0.3, -0.1))
        m.add(f"cheek_{k}", cone("head", "fur", cp - np.array([s * 0.5, 0, 0]), (s * 1, -0.3, 0.2), 3.2, 1.6, sides=4, rings=1))
        # 前脚 (短い) と後ろ脚 (大きな腿と、地に寝た長い足)
        m.bone("leg_f" + k, "body", (s * 4.6, 14.0, -10.0))
        m.add(f"fleg_{k}", limb("leg_f" + k, "fur", [(s * 4.6, 15.0, -9.4), (s * 4.8, 8.0, -11.4), (s * 4.8, 2.4, -12.6)], [3.6, 3.0, 2.8], [3.6, 3.0, 2.6], n=8))
        m.add(f"fpaw_{k}", ell("leg_f" + k, "fur", (s * 4.8, 1.3, -13.6), (2.0, 1.3, 2.6), segs=7, rings=2))
        m.bone("leg_b" + k, "body", (s * 8.4, 15.0, 9.0))
        m.add(f"haunch_{k}", ell("leg_b" + k, "fur", (s * 8.2, 15.0, 9.4), (4.4, 8.2, 8.8), segs=10, rings=3))
        m.add(f"bfoot_{k}", limb("leg_b" + k, "fur", [(s * 8.6, 6.0, 14.6), (s * 8.8, 2.2, 10.0), (s * 8.8, 1.4, 3.4)], [3.6, 3.4, 3.2], [3.4, 2.6, 2.4], n=8))
    sides(side_parts)
    m.add("tail", ell("body", "fur", (0, 20.6, 18.6), (4.0, 4.2, 3.6), segs=9, rings=3))
    # 額の三日月 (光る)
    arc = [np.array([2.6 * math.cos(math.radians(a)), 33.6 + 1.6 * math.sin(math.radians(a)), -18.4 + 0.6 * abs(math.cos(math.radians(a)))]) for a in range(200, 341, 28)]
    m.add("crescent", tub("head", "glow", arc, [0.5, 0.75, 0.9, 0.9, 0.75, 0.5], None, sides=5))
    m.saddle_kw = dict(cy=20.6, length=6.2, half_angle=56, seat_len=5.0, seat_half=34, blanket=0.8,
                       mats=dict(pad="clothr", trim="brass", seat="leath", strap="leathd", metal="brass"))
    m.prefer_pose = (-36.0, 30.0)
    m.pal = pal({"fur": M_(WH, streak=0.08, ao=0.12), "pink": M_(PINK, min=0), "glow": M_(GLOW, base=0.64, lit=0.2)}, glow=["glow"],
                cloth=((40, 50, 92), (56, 72, 128), (80, 100, 164), (116, 136, 196)))

    def detail(c, col, glow):
        col = fur(c, col, ["fur"], scale=(1.0, 0.26, 1.0), amt=0.08)
        col = spots(c, col, ["fur"], (150, 154, 172), scale=0.2, thr=0.7, a=0.7, up_only=True)
        return col, glow
    m.detail = detail
    m.view = dict(center=(0, 26, 0), scale=3.6)
    m.caption = ["月の面の大兎", "長い耳・大きな後ろ足・白銀の毛にクレーターの斑", "額の三日月が光る。跳びがいちばん高い見た目"]
    return m


def moon_b():
    """B 星読みの大猫: 月の野の大山猫。夜の青の毛に星の点 (光る)、房のある耳、頬の襟毛、尾の先は三日月。"""
    NB = [(12, 16, 40), (20, 28, 62), (32, 44, 88), (48, 64, 116), (70, 90, 146)]
    CR = [(130, 136, 160), (170, 176, 198), (206, 212, 228), (232, 236, 246)]
    STAR = [(170, 190, 240), (210, 226, 255), (244, 250, 255)]
    m = Mount("star_lynx", "星読みの大猫")
    m.bone("body", "root", (0, 21, 0))
    m.bone("neck", "body", (0, 24, -11))
    m.bone("head", "neck", (0, 29, -16))
    m.bone("tail", "body", (0, 24, 13))
    quad_bones(m, {"fl": ((3.6, 19.0, -10.4), (3.7, 10.0, -10.8)), "fr": ((-3.6, 19.0, -10.4), (-3.7, 10.0, -10.8)),
                   "bl": ((3.6, 20.0, 8.4), (3.7, 10.2, 11.0)), "br": ((-3.6, 20.0, 8.4), (-3.7, 10.2, 11.0))})
    body = [rsec(-14, 25.0, 16.0, 4.8, 20.5, n=14), rsec(-9, 28.0, 14.6, 7.0, 21.0, n=14), rsec(-2, 28.0, 15.4, 6.6, 21.5, n=14),
            rsec(5, 27.6, 16.0, 6.6, 21.6, n=14), rsec(11, 27.0, 17.0, 6.0, 22.0, n=14), rsec(14.6, 25.0, 18.6, 4.4, 22.0, n=14)]
    m.add("torso", lof("body", "fur", body), core=True)
    m.add("chest", ell("body", "cream", (0, 18.6, -12.6), (4.0, 4.6, 3.0), segs=10, rings=3))
    m.add("neck", limb("neck", "fur", [(0, 22.6, -9.0), (0, 25.4, -12.8), (0, 28.4, -15.6)], [7.6, 6.6, 6.2], [7.6, 6.6, 6.4], n=12))
    hd = [rsec(-14.2, 33.0, 24.6, 4.2, 29.0, n=14), rsec(-16.8, 34.6, 24.0, 5.8, 29.2, n=14), rsec(-19.8, 33.6, 24.6, 5.4, 29.0, n=14),
          rsec(-22.4, 31.2, 25.6, 3.6, 28.4, n=14), rsec(-24.0, 29.6, 26.4, 2.0, 28.0, n=14)]
    m.add("skull", lof("head", "fur", hd), core=True)
    m.add("muzzle", ell("head", "cream", (0, 26.8, -22.2), (2.6, 1.8, 2.0), segs=8, rings=2))
    m.add("nose", ell("head", "eye", (0, 28.4, -24.0), (0.9, 0.6, 0.6), segs=6, rings=1))
    sk = tri_arrays(m.faces_of(["skull"]))

    def side_parts(s, k):
        d = nrm(np.array([s * 0.7, 0.18, -0.68]))
        c = ray_far(sk, (0, 30.4, -18.4), d) - d * 0.55
        m.add(f"eye_{k}", ell("head", "eye", c, (1.3, 1.2, 1.2), segs=8, rings=2))
        m.eyes.append(dict(c=c, r=1.4, dir=tuple(d), a=1.0, b=1.1, bone="head", iris=(240, 210, 110), lid=(14, 18, 40), pa=0.2, pb=0.9, glow=True))
        ep = np.array([s * 2.8, 33.4, -16.6])
        m.add(f"ear_{k}", tub("head", "fur", [ep, ep + np.array([s * 0.6, 3.0, 0.3]), ep + np.array([s * 1.0, 5.8, 0.6])], [3.0, 2.0, 0.3], [1.3, 1.0, 0.2], sides=4))
        m.add(f"tuft_{k}", cone("head", "fur_d", ep + np.array([s * 1.0, 5.4, 0.6]), (s * 0.1, 1, 0.1), 3.6, 0.6, sides=3, rings=1))
        for j in range(4):
            a = math.radians(-50 + j * 30)
            cp = ray_far(sk, (0, 27.4, -17.0), (s * math.cos(a), math.sin(a) * 0.6 - 0.2, 0.25))
            m.add(f"ruff_{k}{j}", cone("head", "cream" if j < 2 else "fur", cp - np.array([s * 0.5, 0, 0]), (s * 1, -0.2 + 0.2 * j, 0.5), 4.2, 1.5, sides=4, rings=1))
    sides(side_parts)
    tail = [(0, 24.0, 12.4), (0, 22.8, 19.0), (0, 23.4, 26.4), (0, 27.4, 32.0), (0, 32.6, 33.0), (0, 35.0, 30.0)]
    m.add("tail", limb("tail", "fur", tail, [3.4, 3.2, 3.0, 2.8, 2.4, 1.6], [3.4, 3.2, 3.0, 2.8, 2.4, 1.6], n=10))
    arc = [np.array([0, 35.0 + 3.6 * math.sin(math.radians(a)), 30.0 + 3.6 * math.cos(math.radians(a))]) for a in range(60, 301, 40)]
    m.add("tail_moon", tub("tail", "star", arc, [0.5, 1.0, 1.3, 1.4, 1.3, 1.0, 0.5], None, sides=5, hint=(1, 0, 0)))
    for k in ("fl", "fr"):
        s = 1 if k.endswith("l") else -1
        hoof_leg(m, k, None, (s * 3.7, 3.8, -11.4), (4.6, 2.8, 2.4), (5.4, 3.2, 2.6), mat="fur", lo_mat="fur", hoof_mat="fur_d", hoof=(3.4, 2.0, 4.0), foot="paw")
    for k in ("bl", "br"):
        s = 1 if k.endswith("l") else -1
        hoof_leg(m, k, (s * 3.9, 14.6, 6.4), (s * 3.7, 3.8, 10.2), (5.4, 2.8, 2.4), (8.0, 3.2, 2.6), mat="fur", lo_mat="fur", hoof_mat="fur_d",
                 hoof=(3.4, 2.0, 4.0), foot="paw")
    m.saddle_kw = dict(cy=21.0, length=6.4, half_angle=58, seat_len=5.2, seat_half=34, blanket=0.8,
                       mats=dict(pad="clothr", trim="brass", seat="leath", strap="leathd", metal="brass"))
    m.prefer_pose = (-30.0, 24.0)
    m.pal = pal({"fur": M_(NB, streak=0.08, ao=0.14), "fur_d": M_(NB[:3], min=0), "cream": M_(CR, streak=0.08), "star": M_(STAR, base=0.64, lit=0.2)},
                glow=["star"], cloth=((90, 80, 40), (126, 112, 56), (170, 150, 80), (210, 190, 110)))

    def detail(c, col, glow):
        col = fur(c, col, ["fur"], scale=(1.0, 0.24, 1.0), amt=0.1)
        col = countershade(c, col, ["fur"], (150, 160, 196), top_k=0.9, belly_from=-0.35)
        P = c["P"]
        sel = np.isin(c["mats"], ["fur"]) & c["owned"] & (c["N"][..., 1] > -0.2)
        n = RC.hash3(np.floor(P / 0.9), 13)
        star = sel & (n < 0.035)
        col = np.where(star[..., None], np.array([226, 236, 255]), col)
        return col, glow | star
    m.detail = detail
    m.view = dict(center=(0, 24, 2), scale=3.7)
    m.caption = ["月の野の大山猫", "夜の青の毛に星の点（光る）・房の耳・頬の襟毛", "尾の先の三日月が光る。細く速い見た目"]
    return m


def moon_c():
    """C 月の大蛙: 月の溜まりに棲む大蛙。低く幅広い体、上に飛び出た目、折りたたんだ大きな後ろ足。背に光る三日月の斑。"""
    SK = [(70, 76, 92), (100, 108, 124), (134, 142, 158), (168, 174, 188), (200, 206, 216)]
    BL = [(170, 170, 160), (200, 200, 190), (226, 226, 216), (244, 244, 236)]
    GLOW = [(150, 200, 230), (196, 232, 250), (240, 252, 255)]
    m = Mount("moon_toad", "月の大蛙")
    m.bone("body", "root", (0, 14, 2))
    m.bone("head", "body", (0, 17, -12))
    body = [rsec(-11, 22.6, 8.2, 9.4, 15.0, n=16, p=2.1), rsec(-5, 25.2, 6.2, 13.0, 15.0, n=16, p=2.1), rsec(2, 25.6, 5.6, 14.0, 15.0, n=16, p=2.1),
            rsec(9, 23.2, 6.0, 12.6, 14.6, n=16, p=2.1), rsec(14, 18.4, 8.0, 8.6, 13.6, n=16, p=2.1), rsec(16.6, 13.4, 10.0, 3.6, 12.0, n=16, p=2.1)]
    m.add("torso", lof("body", "skin", body), core=True)
    hd = [rsec(-9, 23.2, 9.0, 11.0, 15.4, n=16, p=2.2), rsec(-14.5, 23.0, 10.0, 12.0, 15.8, n=16, p=2.2), rsec(-19.5, 21.0, 11.4, 10.6, 15.8, n=16, p=2.2),
          rsec(-22.6, 18.6, 12.8, 7.2, 15.6, n=16, p=2.2), rsec(-24.4, 16.4, 13.8, 3.6, 15.2, n=16, p=2.2)]
    m.add("skull", lof("head", "skin", hd), core=True)
    lip = [np.array([10.6 * math.sin(math.radians(a)), 14.4, -15.0 - 9.0 * math.cos(math.radians(a))]) for a in range(-80, 81, 20)]
    m.add("lip", tub("head", "skin_d", lip, [0.6] * len(lip), None, sides=4))

    def side_parts(s, k):
        c = np.array([s * 6.6, 24.0, -14.0])
        m.add(f"eyemound_{k}", ell("head", "skin", c - np.array([0, 1.4, 0]), (3.4, 2.6, 3.4), segs=9, rings=2))
        m.add(f"eye_{k}", ell("head", "eye", c + np.array([0, 0.6, 0]), (2.7, 2.7, 2.7), segs=9, rings=3))
        m.eyes.append(dict(c=c + np.array([0, 0.6, 0]), r=2.9, dir=(s * 0.55, 0.45, -0.7), a=2.2, b=0.9, bone="head", iris=(230, 196, 90),
                           lid=(80, 86, 100), pa=0.9, pb=0.25))
        m.bone("leg_f" + k, "body", (s * 9.6, 11.0, -9.0))
        m.add(f"farm_{k}", limb("leg_f" + k, "skin", [(s * 9.0, 12.0, -8.6), (s * 11.6, 6.6, -11.0), (s * 12.0, 2.0, -12.6)], [4.0, 3.2, 2.8], [4.0, 3.2, 2.6], n=8))
        splay_foot(m, "leg_f" + k, "skin", (s * 12.2, 1.0, -13.6), s, toes=4, toe_len=3.0, disc="belly", fwd=-1.0)
        m.bone("leg_b" + k, "body", (s * 11.0, 10.0, 8.0))
        m.add(f"thigh_{k}", ell("leg_b" + k, "skin", (s * 12.0, 9.6, 8.6), (5.0, 6.0, 8.4), segs=10, rings=3, ry=s * 12))
        m.add(f"shin_{k}", limb("leg_b" + k, "skin", [(s * 14.0, 6.0, 14.0), (s * 15.4, 3.4, 8.0), (s * 15.0, 2.2, 2.4)], [4.4, 3.6, 3.0], [4.2, 3.4, 2.6], n=8))
        m.add(f"web_{k}", ell("leg_b" + k, "skin_d", (s * 15.4, 1.0, -0.6), (4.4, 0.9, 5.4), segs=9, rings=2))
        for j in range(4):
            a = math.radians(-30 + 20 * j) * s
            m.add(f"btoe_{k}{j}", tub("leg_b" + k, "skin", [(s * 15.2, 1.1, -0.6), (s * 15.2 + math.sin(a) * 6.0, 0.9, -0.6 - math.cos(a) * 6.0)], [0.8, 0.6], None, sides=5))
    sides(side_parts)
    # 背の光る三日月の斑 (肩と腰。鞍の下は避ける)
    bt = tri_arrays(m.faces_of(["torso"]))
    for i, (a, z) in enumerate(((40, -8.0), (-40, -8.0), (30, 10.5), (-30, 10.5), (0, 13.0))):
        d = np.array([math.sin(math.radians(a)), math.cos(math.radians(a)), 0])
        p = ray_far(bt, (0, 14.0, z), d)
        m.add(f"moonwart{i}", ell("body", "glow", p - d * 0.4, (1.8, 0.9, 1.8), segs=8, rings=2))
    m.saddle_kw = dict(cy=15.0, length=6.0, half_angle=52, seat_len=5.0, seat_half=34, blanket=0.8,
                       mats=dict(pad="clothr", trim="brass", seat="leath", strap="leathd", metal="brass"))
    m.prefer_pose = (-34.0, 40.0)
    m.pal = pal({"skin": M_(SK, streak=0.04, ao=0.12), "skin_d": M_(SK[:4], min=0), "belly": M_(BL), "glow": M_(GLOW, base=0.64, lit=0.2)},
                glow=["glow"], cloth=((40, 50, 92), (56, 72, 128), (80, 100, 164), (116, 136, 196)))

    def detail(c, col, glow):
        col = countershade(c, col, ["skin"], (214, 214, 204), top_k=0.9, belly_from=-0.2)
        P = c["P"]
        sel = np.isin(c["mats"], ["skin"]) & c["owned"] & (c["N"][..., 1] > 0.0)
        f1, f2, idv = cell3(P, 3.2, 41)
        ring = sel & (f1 > 0.22) & (f1 < 0.3) & (idv > 0.45)
        col = shade(col, ring, 0.74)
        col = shade(col, sel & (f1 < 0.22) & (idv > 0.45), 0.9)
        return col, glow
    m.detail = detail
    m.view = dict(center=(0, 14, -2), scale=3.9)
    m.caption = ["月の溜まりに棲む大蛙", "低く幅広い体・上に飛び出た目・環の斑の肌", "背の三日月の斑が光る。跳ぶと高い"]
    return m


# ====================================================================== 空 (飛ぶ乗り物。終章のあと)

def sky_a():
    """A 空鱏: 雲海を泳ぐ大きな鱏。平たい菱形の体と大きな翼、前に二つの頭びれ、長い尾の先に鐘の房。背は夕べの青に金の斑、腹は雲の白。"""
    BK = [(16, 22, 48), (26, 36, 72), (40, 54, 100), (58, 76, 130), (82, 104, 160)]
    BL = [(170, 176, 196), (204, 210, 226), (230, 234, 244), (246, 248, 252)]
    TIP = [(120, 190, 230), (170, 224, 250), (226, 246, 255)]
    m = Mount("sky_ray", "空鱏")
    m.bone("body", "root", (0, 22, 0))
    for s, k in ((1, "l"), (-1, "r")):
        m.bone("wing_" + k, "body", (s * 13.0, 22.4, 0.0))
        m.bone("wing_" + k + "2", "wing_" + k, (s * 27.0, 24.6, 3.0))
    m.bone("tail", "body", (0, 22, 17))
    body = [rsec(-17, 23.4, 21.2, 5.0, 22.2, n=14, p=2.6), rsec(-12, 25.2, 19.4, 12.0, 22.2, n=14, p=2.6), rsec(-6, 26.6, 18.4, 15.6, 22.2, n=14, p=2.6),
            rsec(0, 27.0, 18.0, 16.6, 22.2, n=14, p=2.6), rsec(6, 26.4, 18.6, 14.4, 22.2, n=14, p=2.6), rsec(12, 25.0, 19.6, 9.6, 22.2, n=14, p=2.6),
            rsec(16, 23.6, 20.6, 4.8, 22.2, n=14, p=2.6), rsec(19, 22.8, 21.4, 1.8, 22.2, n=14, p=2.6)]
    m.add("disc", lof("body", "back", body), core=True)

    def wings(s, k):
        st1 = [(s * 11.0, -12.0, 11.0, 22.4, 5.0), (s * 19.0, -7.0, 10.0, 23.6, 3.4), (s * 27.0, -2.0, 9.0, 24.8, 2.4)]
        st2 = [(s * 26.0, -2.6, 9.2, 24.7, 2.5), (s * 34.0, 2.0, 9.4, 25.2, 1.6), (s * 40.0, 6.4, 10.0, 24.4, 1.0), (s * 44.0, 9.6, 10.6, 23.2, 0.5)]
        if s < 0:
            st1, st2 = st1[::-1], st2[::-1]
        m.add(f"wing_{k}", wing("wing_" + k, "back", st1))
        m.add(f"wing_{k}2", wing("wing_" + k + "2", "back", st2))
        m.add(f"fin_{k}", cone("body", "back", (s * 4.4, 21.8, -15.4), (s * 0.15, -0.05, -1), 7.0, 2.2, sides=5, rings=3, bend=(-s * 1.4, -0.6, 0), rb=1.2))
        c = np.array([s * 6.4, 23.0, -13.0])
        m.add(f"eye_{k}", ell("body", "eye", c, (1.2, 1.1, 1.3), segs=7, rings=2))
        m.eyes.append(dict(c=c, r=1.3, dir=(s * 0.8, 0.3, -0.5), a=0.9, b=0.8, bone="body", iris=(240, 200, 100), lid=(20, 26, 50), pa=0.4, pb=0.7, glow=True))
    sides(wings)
    tail = [(0, 22.2, 16.0), (0, 22.0, 26.0), (0, 21.4, 36.0), (0, 21.6, 46.0), (0, 22.6, 54.0)]
    m.add("tail", tub("tail", "back", tail, [1.6, 1.2, 0.9, 0.7, 0.5], None, sides=5))
    m.add("tassel", cone("tail", "tip", (0, 23.0, 53.0), (0, -0.4, 1), 5.0, 1.6, sides=5, rings=2))
    m.add("tassel_bell", ell("tail", "brass", (0, 22.6, 52.6), (1.2, 1.4, 1.2), segs=7, rings=2))
    m.saddle_kw = dict(cy=22.4, length=6.0, half_angle=40, seat_len=5.0, seat_half=30, blanket=0.8,
                       mats=dict(pad="clothr", trim="brass", seat="leath", strap="leathd", metal="brass"))
    m.prefer_pose = (-40.0, 44.0)
    m.pal = pal({"back": M_(BK, lit=0.34, streak=0.04, ao=0.1), "tip": M_(TIP, base=0.64, lit=0.2)}, glow=["tip"],
                cloth=((120, 100, 40), (160, 134, 56), (200, 172, 80), (232, 208, 120)))
    tips = [(44.0, 23.2, 10.0), (-44.0, 23.2, 10.0)]

    def detail(c, col, glow):
        col = countershade(c, col, ["back"], (226, 230, 242), top_k=0.95, belly_from=0.1)
        col = spots(c, col, ["back"], (214, 176, 84), scale=0.3, thr=0.76, a=0.85, up_only=True)
        col, glow = gradient_to(c, col, ["back"], tips, 7.0, TIP, glow=glow, glow_thr=0.45, power=1.4)
        return col, glow
    m.detail = detail
    m.view = dict(center=(0, 20, 6), scale=2.6)
    m.caption = ["雲海を泳ぐ大きな鱏", "菱形の体と大きな翼・二つの頭びれ・尾の鐘", "背は夕べの青に金の斑、腹は雲の白。翼の先が光る"]
    return m


def sky_b():
    """B 鐘鳴りの大鳥: 鐘の国の伝えの白い大鳥。長い首、頭に鐘の形の冠、喉に小さな鐘。大きな翼と流れる尾羽。"""
    WH = [(150, 150, 160), (190, 190, 200), (222, 222, 230), (240, 240, 246), (252, 252, 255)]
    SL = [(40, 50, 76), (60, 74, 104), (86, 102, 136), (118, 134, 168)]
    GOLD = [(120, 84, 24), (178, 132, 44), (222, 178, 70), (246, 214, 120), (255, 240, 190)]
    m = Mount("bell_bird", "鐘鳴りの大鳥")
    m.bone("body", "root", (0, 26, 0))
    m.bone("neck", "body", (0, 30, -10))
    m.bone("head", "neck", (0, 47, -16))
    m.bone("tail", "body", (0, 27, 14))
    for s, k in ((1, "l"), (-1, "r")):
        m.bone("wing_" + k, "body", (s * 8.0, 32.0, -2.0))
        m.bone("wing_" + k + "2", "wing_" + k, (s * 22.0, 38.0, 2.0))
    body = [rsec(-14, 31.0, 21.0, 6.0, 26.0, n=14), rsec(-9, 36.0, 18.0, 11.0, 26.6, n=14), rsec(-2, 37.0, 17.0, 12.4, 26.8, n=14),
            rsec(6, 36.0, 18.0, 12.0, 26.8, n=14), rsec(12, 32.6, 20.6, 9.0, 26.4, n=14), rsec(16.4, 28.6, 23.6, 4.8, 26.0, n=14), rsec(19.0, 26.8, 25.4, 1.8, 26.0, n=14)]
    m.add("torso", lof("body", "wh", body), core=True)
    m.add("neck", limb("neck", "wh", [(0, 29.0, -9.0), (0, 35.0, -15.0), (0, 41.6, -17.4), (0, 46.4, -16.4)], [8.0, 6.2, 5.0, 4.6], [8.0, 6.2, 5.0, 4.8], n=12))
    m.add("skull", ell("head", "wh", (0, 48.6, -17.4), (3.6, 3.4, 4.6), segs=10, rings=3), core=True)
    m.add("beak", cone("head", "gold", (0, 48.2, -21.0), (0, -0.18, -1), 8.0, 1.7, sides=6, rings=3, rb=1.3, hint=(1, 0, 0)))
    bell = [ysec(51.0, 5.6, 5.6, zc=-17.0, cut=0.5), ysec(52.0, 5.0, 5.0, zc=-17.0, cut=0.5), ysec(54.4, 3.8, 3.8, zc=-16.8, cut=0.5),
            ysec(56.6, 3.0, 3.0, zc=-16.6, cut=0.5), ysec(57.6, 1.6, 1.6, zc=-16.6, cut=0.5)]
    m.add("casque", lof("head", "gold", bell))
    m.add("casque_knob", ell("head", "gold", (0, 58.4, -16.6), (0.9, 0.9, 0.9), segs=6, rings=1))
    m.add("wattle", lof("neck", "gold", [ysec(45.6, 1.0, 1.0, zc=-19.6), ysec(44.0, 2.2, 2.2, zc=-19.8), ysec(43.0, 2.8, 2.8, zc=-19.8)]))

    def parts(s, k):
        c = np.array([s * 2.9, 49.4, -19.0])
        m.add(f"eye_{k}", ell("head", "eye", c, (0.9, 1.0, 1.0), segs=7, rings=2))
        m.eyes.append(dict(c=c, r=1.1, dir=(s * 0.9, 0.1, -0.4), a=0.8, b=0.8, bone="head", iris=(40, 30, 20), lid=(200, 200, 210), pa=0.5, pb=0.5))
        st1 = [(s * 8.0, -10.0, 12.0, 32.6, 4.2), (s * 16.0, -9.4, 13.4, 36.4, 3.2), (s * 24.0, -8.0, 14.0, 40.0, 2.4)]
        st2 = [(s * 23.0, -8.2, 14.0, 39.6, 2.4), (s * 31.0, -6.0, 14.6, 42.6, 1.8), (s * 38.0, -3.4, 14.4, 44.0, 1.2)]
        if s < 0:
            st1, st2 = st1[::-1], st2[::-1]
        m.add(f"wing_{k}", wing("wing_" + k, "wh", st1))
        m.add(f"wing_{k}2", wing("wing_" + k + "2", "slate", st2))
        for j in range(7):
            a = math.radians(-6 + j * 15)
            base = np.array([s * (37.0 - j * 2.2), 43.4 - j * 0.6, 2.0 + j * 2.0])
            d = nrm(np.array([s * math.cos(a), 0.04, math.sin(a)]))
            m.add(f"primary_{k}{j}", cone("wing_" + k + "2", "slate", base, d, 13.0 - j * 0.8, 2.6, sides=4, rings=2, rb=0.6, hint=(0, 1, 0)))
        m.bone("leg_" + k, "body", (s * 4.0, 20.0, 3.0))
        m.add(f"leg_{k}", limb("leg_" + k, "gold", [(s * 4.0, 21.0, 3.0), (s * 4.4, 11.0, 5.0), (s * 4.2, 2.0, 3.6)], [2.4, 1.6, 1.4], [2.4, 1.6, 1.4], n=6))
        for j in range(3):
            a = math.radians(-30 + 30 * j)
            m.add(f"toe_{k}{j}", tub("leg_" + k, "gold", [(s * 4.2, 1.4, 3.6), (s * 4.2 + math.sin(a) * 3.6, 0.8, 3.6 - math.cos(a) * 3.6)], [0.7, 0.4], None, sides=4))
        m.add(f"backtoe_{k}", tub("leg_" + k, "gold", [(s * 4.2, 1.4, 3.6), (s * 4.2, 0.8, 6.0)], [0.6, 0.4], None, sides=4))
    sides(parts)
    for j in range(5):
        a = math.radians(-36 + 18 * j)
        d = nrm(np.array([math.sin(a) * 0.7, -0.12, 1.0]))
        m.add(f"plume{j}", cone("tail", "wh" if j % 2 else "slate", (math.sin(a) * 2.0, 27.6, 16.0), d, 22.0 - abs(j - 2) * 3.0, 2.6, sides=4, rings=3, rb=0.7,
                                 bend=(0, -1.0, 0), hint=(0, 1, 0)))
        m.add(f"plume_tip{j}", ell("tail", "gold", (math.sin(a) * 2.0, 27.6, 16.0) + d * (21.0 - abs(j - 2) * 3.0) + np.array([0, -1.0, 0]), (1.0, 1.0, 1.0), segs=6, rings=1))
    m.saddle_kw = dict(cy=27.0, length=6.4, half_angle=56, seat_len=5.0, seat_half=34, blanket=0.8,
                       mats=dict(pad="clothr", trim="brass", seat="leath", strap="leathd", metal="brass"))
    m.prefer_pose = (-34.0, 36.0)
    m.pal = pal({"wh": M_(WH, lit=0.3, streak=0.06, ao=0.1), "slate": M_(SL, streak=0.06), "gold": M_(GOLD, lit=0.38, noise=0.03)},
                cloth=((90, 30, 34), (126, 44, 48), (166, 64, 66), (200, 96, 92)))

    def detail(c, col, glow):
        col = feather_rows(c, col, ["wh", "slate"], row=2.2, k_edge=0.84)
        return col, glow
    m.detail = detail
    m.view = dict(center=(0, 30, 2), scale=2.6)
    m.caption = ["鐘の国の伝えの白い大鳥", "長い首・頭に鐘の形の金の冠・喉の小さな鐘", "大きな翼と流れる尾羽。羽ばたくと鐘の音"]
    return m


def sky_c():
    """C 雲の竜: 雲の上に棲む細身の翼竜。空色の鱗、首と背に雲の房、膜の翼、尾の先も雲。二本の後ろ足で立つ。"""
    SC = [(40, 70, 110), (60, 98, 146), (88, 130, 180), (124, 166, 210), (166, 200, 234)]
    BL = [(170, 180, 196), (206, 214, 226), (232, 236, 244)]
    MEM = [(70, 110, 160), (110, 150, 196), (150, 186, 222), (190, 214, 238)]
    CLOUD = [(200, 206, 220), (226, 230, 240), (244, 246, 252), (255, 255, 255)]
    HORN = [(150, 140, 110), (196, 186, 150), (230, 222, 190)]
    m = Mount("cloud_wyvern", "雲の竜")
    m.bone("body", "root", (0, 24, 0))
    m.bone("neck", "body", (0, 28, -11))
    m.bone("head", "neck", (0, 42, -24))
    m.bone("tail", "body", (0, 24, 14))
    quad_bones(m, {"bl": ((5.0, 21.0, 6.0), (5.4, 11.0, 2.0)), "br": ((-5.0, 21.0, 6.0), (-5.4, 11.0, 2.0))})
    body = [rsec(-13, 28.0, 19.0, 4.6, 23.6, n=14), rsec(-8, 31.0, 17.0, 7.0, 24.0, n=14), rsec(-1, 31.6, 17.0, 7.4, 24.2, n=14),
            rsec(6, 30.0, 18.6, 6.6, 24.2, n=14), rsec(12, 27.0, 21.0, 4.6, 24.0, n=14), rsec(16, 25.0, 22.6, 3.0, 23.8, n=14)]
    m.add("torso", lof("body", "scale", body), core=True)
    m.add("neck", limb("neck", "scale", [(0, 27.0, -10.0), (0, 32.0, -16.0), (0, 38.0, -20.6), (0, 42.0, -24.0)], [7.4, 5.8, 4.8, 4.2], [7.4, 5.8, 4.8, 4.4], n=12))
    hd = [rsec(-22.6, 46.0, 39.0, 3.8, 42.6, n=12), rsec(-26.0, 46.6, 39.4, 4.2, 43.0, n=12), rsec(-29.0, 45.0, 40.4, 3.4, 42.8, n=12),
          rsec(-32.4, 43.6, 41.2, 2.2, 42.4, n=12), rsec(-35.0, 42.6, 41.6, 1.2, 42.2, n=12)]
    m.add("skull", lof("head", "scale", hd), core=True)
    m.add("jaw", lof("head", "belly", [rsec(-24.0, 41.0, 38.6, 3.0, 39.6, n=10), rsec(-30.0, 41.0, 39.6, 2.2, 40.2, n=10), rsec(-34.0, 41.4, 40.6, 1.0, 41.0, n=10)]))
    sk = tri_arrays(m.faces_of(["skull"]))

    def parts(s, k):
        d = nrm(np.array([s * 0.8, 0.3, -0.5]))
        c = ray_far(sk, (0, 43.6, -27.6), d) - d * 0.5
        m.add(f"eye_{k}", ell("head", "eye", c, (1.0, 0.9, 1.1), segs=7, rings=2))
        m.eyes.append(dict(c=c, r=1.2, dir=tuple(d), a=0.9, b=0.5, bone="head", iris=(250, 200, 80), lid=(30, 50, 80), pa=0.25, pb=0.85, glow=True))
        m.add(f"horn_{k}", cone("head", "horn", (s * 2.4, 45.6, -24.6), (s * 0.25, 0.45, 1), 9.0, 1.5, sides=5, rings=3, bend=(0, -1.6, 0.6)))
        # 翼 (腕の骨と三本の指、そのあいだの膜)
        m.bone("wing_" + k, "body", (s * 6.0, 30.0, -6.0))
        sh, el, wr = np.array([s * 6.0, 30.0, -6.0]), np.array([s * 18.0, 38.0, -3.0]), np.array([s * 28.0, 41.0, 1.0])
        tips = [np.array([s * 42.0, 36.0, 9.0]), np.array([s * 37.0, 31.0, 16.0]), np.array([s * 29.0, 27.0, 19.0])]
        back = np.array([s * 6.0, 26.0, 10.0])
        bw = "wing_" + k
        m.add(f"arm_{k}", tub(bw, "scale", [sh, el, wr], [2.2, 1.6, 1.3], None, sides=6))
        m.add(f"claw_{k}", cone(bw, "horn", wr + np.array([0, 0.6, -0.6]), (0, 0.4, -1), 3.0, 0.8, sides=4, rings=1))
        for j, t in enumerate(tips):
            m.add(f"finger_{k}{j}", tub(bw, "scale", [wr, lerp(wr, t, 0.5) + np.array([0, 1.0, 0]), t], [1.1, 0.8, 0.4], None, sides=5))
        up = np.array([0, 1.0, 0])
        for j, tri in enumerate(((sh, el, back), (el, wr, tips[2]), (el, tips[2], back), (wr, tips[0], tips[1]), (wr, tips[1], tips[2]))):
            nn = nrm(np.cross(tri[1] - tri[0], tri[2] - tri[0]))
            m.add(f"membrane_{k}{j}", slab(bw, "mem", list(tri), nn if nn[1] >= 0 else -nn, 0.7))
    sides(parts)
    # 雲の房 (首の上と背の後ろ・尾の先。鞍の下は避ける)
    puffs = [((0, 42.6, -21.6), 2.4), ((0, 38.8, -17.4), 2.8), ((0, 34.6, -13.6), 3.0), ((0, 31.6, -10.0), 3.0), ((0, 30.6, 9.0), 2.6), ((0, 28.8, 12.6), 2.4)]
    for i, (p, r) in enumerate(puffs):
        for j in range(3):
            a = math.radians(j * 120 + i * 40)
            q = np.array(p) + np.array([math.cos(a) * r * 0.5, 0.4 * j, math.sin(a) * r * 0.4])
            m.add(f"puff{i}_{j}", ell("body" if i > 3 else "neck", "cloud", q, (r, r * 0.8, r), segs=8, rings=2))
    tail = [(0, 24.0, 13.0), (0, 21.6, 22.0), (0, 20.0, 31.0), (0, 21.4, 40.0), (0, 24.0, 46.0)]
    m.add("tail", limb("tail", "scale", tail, [5.6, 4.4, 3.2, 2.2, 1.4], [5.6, 4.4, 3.2, 2.2, 1.4], n=10))
    for j in range(4):
        a = math.radians(j * 90 + 20)
        m.add(f"tailpuff{j}", ell("tail", "cloud", (math.cos(a) * 1.6, 24.6 + math.sin(a) * 1.4, 46.4 + j * 0.6), (2.6, 2.2, 2.6), segs=8, rings=2))
    for k in ("bl", "br"):
        s = 1 if k.endswith("l") else -1
        hoof_leg(m, k, (s * 5.8, 15.0, 7.4), (s * 5.4, 3.8, 5.6), (7.4, 4.0, 3.0), (10.0, 4.4, 3.0), mat="scale", lo_mat="scale", hoof_mat="horn",
                 hoof=(4.4, 2.4, 5.0), foot="paw")
    m.saddle_kw = dict(cy=24.2, length=6.0, half_angle=54, seat_len=5.0, seat_half=34, blanket=0.8,
                       mats=dict(pad="clothr", trim="brass", seat="leath", strap="leathd", metal="brass"))
    m.prefer_pose = (-32.0, 30.0)
    m.pal = pal({"scale": M_(SC, lit=0.34, streak=0.04, ao=0.12), "belly": M_(BL), "mem": M_(MEM, lit=0.3, noise=0.03),
                 "cloud": M_(CLOUD, lit=0.26, noise=0.02, ao=0.06), "horn": M_(HORN, lit=0.36)},
                cloth=((140, 140, 150), (176, 176, 186), (210, 210, 220), (236, 236, 244)))

    def detail(c, col, glow):
        col = scales(c, col, ["scale"], size=1.2, edge=0.12, k_edge=0.78)
        col = countershade(c, col, ["scale"], (210, 220, 232), top_k=0.9, belly_from=-0.3)
        col = bands_y(c, col, ["mem"], 3.0, 0.5, k=0.86)
        return col, glow
    m.detail = detail
    m.view = dict(center=(0, 28, 0), scale=2.7)
    m.caption = ["雲の上に棲む細身の翼竜", "空色の鱗・首と背と尾の先の雲の房・膜の翼", "二本の後ろ足で立つ。いちばん「竜」らしい"]
    return m


SUBJECTS = {
    "abyss": ("深淵の乗り物（五章）", [abyss_a, abyss_b, abyss_c]),
    "moon": ("月の乗り物（六章）", [moon_a, moon_b, moon_c]),
    "sky": ("空を飛ぶ乗り物（終章のあと）", [sky_a, sky_b, sky_c]),
}


def render_option(m, PW=340, PH=280):
    seat_top = saddle(m, **m.saddle_kw)
    seat = round((seat_top - 1.0) / 16.0, 3)
    game_top = seat * 16.0
    pitch, spread, n_in = choose_leg_pose(m, game_top, m.prefer_pose)
    if pitch > -62:
        kw = m.saddle_kw
        add_stirrups(m, foot_point(game_top, pitch, spread, 1), kw.get("cy", 20.0), 0.0, kw.get("seat_half", 34.0), kw.get("blanket", 1.0), kw.get("mats"))
    size, atlas, glow = paint_mount(m, m.pal, m.detail, 256)
    cen, sc = np.array(m.view["center"], float), m.view["scale"]
    rider = rider_faces(game_top, pitch, spread)
    ims = [view(m.F, atlas, glow, 215, 14, (PW, PH), cen, sc),
           view(m.F, atlas, glow, 270, 6, (PW, PH), cen, sc),
           BA.view_with_rider(m, rider, atlas, glow, 140, 16, (PW, PH), cen + np.array([0, 4, 0]), sc * 0.92),
           view(m.F, atlas, glow, 330, 30, (PW, PH), cen, sc)]
    tris = sum(len(f.verts) - 2 for f in m.F)
    return ims, dict(seat=seat, tris=tris, leg_inside=n_in)


def sheet(key):
    title, fns = SUBJECTS[key]
    F = voxel.font
    PW, PH = 340, 280
    cols = []
    for i, fn in enumerate(fns):
        m = fn()
        ims, info = render_option(m, PW, PH)
        cols.append((m, ims, info))
        print(key, "ABC"[i], m.title, info, flush=True)
    W_ = 24 + 3 * (PW + 16)
    H_ = 70 + 120 + 4 * (PH + 8) + 20
    cv = Image.new("RGB", (W_, H_), (16, 16, 22))
    d = ImageDraw.Draw(cv)
    d.text((20, 14), f"{title} — 三案のラフ", font=F(28), fill=(236, 232, 218))
    d.text((20, 50), "上から: 斜め前 / 横 / 乗った姿 / 斜め上の後ろ。左の黄色い目盛りが人 (1.8 ブロック)。鞍は体に沿わせた仮のもの", font=F(15), fill=(176, 176, 192))
    for i, (m, ims, info) in enumerate(cols):
        x = 20 + i * (PW + 16)
        y = 80
        d.text((x, y), f"{'ABC'[i]}  {m.title}", font=F(24), fill=(150, 232, 255))
        for j, line in enumerate(m.caption):
            d.text((x, y + 34 + j * 22), line, font=F(15), fill=(210, 208, 196))
        d.text((x, y + 34 + 3 * 22), f"席 {info['seat']:.2f} ブロック・{info['tris']} 三角形", font=F(13), fill=(150, 150, 166))
        y = 80 + 120
        for im in ims:
            cv.paste(im, (x, y))
            y += PH + 8
    OUT.mkdir(parents=True, exist_ok=True)
    cv.save(OUT / f"{key}.png")
    for i, (m, ims, info) in enumerate(cols):
        big = Image.new("RGB", (PW * 2 + 8, PH * 2 + 8), (16, 16, 22))
        for j, im in enumerate(ims):
            big.paste(im, ((j % 2) * (PW + 8), (j // 2) * (PH + 8)))
        big.save(OUT / f"{key}_{'abc'[i]}.png")


if __name__ == "__main__":
    sys.stdout.reconfigure(encoding="utf-8")
    for key in (sys.argv[1:] or list(SUBJECTS)):
        sheet(key)
