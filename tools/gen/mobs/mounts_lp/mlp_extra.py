# -*- coding: utf-8 -*-
"""乗り物の低ポリの、あとから足した見た目 (灯り守の大守宮・星読みの大猫・空鱏) が使う部品 (ラフ tools/rough/lowpoly/mounts2/roughs.py から移した)。"""
from mlp import *  # noqa


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
