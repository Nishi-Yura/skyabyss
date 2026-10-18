"""3 案で共有する部品 (顔・背骨・錨石・板・藤壺・アーチ)。"""
from common import *  # noqa

NEAR_ST = [-132, -131, -129.5, -127, -124, -118, -111, -104, -97, -90, -83, -76, -70, -66, -60, -52, -44, -36, -28, -20,
           -12, -4, 4, 12, 20, 28, 36, 44, 52, 60, 68, 76, 84, 92, 100, 108, 116, 124, 132, 138]
MID_ST = [-132, -130, -126, -118, -106, -92, -78, -66, -60, -44, -26, -8, 10, 28, 46, 60, 76, 94, 112, 128, 138]
FAR_ST = [-132, -127, -112, -88, -66, -60, -30, 0, 30, 60, 100, 138]
STATIONS = [NEAR_ST, MID_ST, FAR_ST]

NEAR_LV = [(-0.80, 1.0), (-0.55, 1.0), ("L", 1.0), (0.10, 1.0), (0.45, 1.0), (0.80, 1.0)]
MID_LV = [(-0.70, 1.0), ("L", 1.0), (0.15, 1.0), (0.60, 1.0)]
FAR_LV = [(-0.62, 1.0), ("L", 1.0), (0.40, 1.0)]
LEVELS = [NEAR_LV, MID_LV, FAR_LV]


def belly_index(spec):
    return sum(1 for t, f in spec.levels if t != "L" and t <= -0.5) - 1 if any(t != "L" and t <= -0.5 for t, f in spec.levels) else -1


def std_matfn(spec, lip_z=-60.0):
    iB = belly_index(spec)
    iL = [i for i, (t, f) in enumerate(spec.levels) if t == "L"][0]

    def fn(cz, lo, hi, bone, h):
        if hi <= iB and iB >= 0:
            return "belly"
        if cz < lip_z and lo >= iB and hi <= iL and bone in ("jaw", "head") and iB >= 0:
            return "lip"
        return "stone"
    return fn


def eye_set(h, cfg, lod, zc=-111.0, yc=39.5):
    """目 (琥珀色の光る目 + 縦の瞳の線)、2 段のまぶた、眉。左右同じ。"""
    faces = []
    sc = cfg.get("eye_scale", 1.0)
    for side in (1, -1):
        p, n = h.at(zc, yc, side)
        up = (0, 1, 0)
        if lod == 0:
            faces += plinth("head", "lid", p, n, up, 9.6 * sc, 7.0 * sc, 1.7, 0.82, 8, embed=1.2, rot=22.5)
            faces += plinth("head", "lid", p + n * 1.4, n, up, 7.4 * sc, 5.2 * sc, 1.4, 0.80, 8, embed=0.2, rot=22.5)
            faces += plinth("head", "eye", p + n * 2.6, n, up, 5.0 * sc, 3.4 * sc, 0.7, 0.92, 8, embed=0.2, rot=22.5)
            pb, nb = h.at(zc, yc + 6.6 * sc, side)
            faces += plinth("head", "brow", pb + V(0, 0.2, 0), nb, up, 12.5 * sc, 2.5 * sc, 2.6 + cfg.get("brow_h", 0.0), 0.62, 6, embed=1.2)
        else:
            faces += plinth("head", "lid", p, n, up, 8.5, 6.0, 2.4, 0.7, 6, embed=1.0, rot=0)
            faces += plinth("head", "eye", p + n * 2.0, n, up, 4.4, 3.0, 0.8, 0.9, 6, embed=0.2, rot=0)
    return faces


def baleen(h, cfg):
    faces = []
    n = h.n
    iL = h.iL
    for a in range(len(h.st)):
        z = h.st[a]
        if not (-131.5 <= z <= -123):
            continue
        r = h.rings[a]
        for k in (1 + iL, 2 * n + 1 - iL):
            p = r[k]
            if abs(p[0]) < 0.5:
                continue
            d = V(np.sign(p[0]) * 0.2, -0.7, -0.7)
            faces += spike("head", "baleen", p + V(0, 0.3, 0), d, 4.0, 0.9, sides=4, rings=1, hint=(0, 0, 1))
    return faces


def plates_on_flank(h, cfg, spots):
    """石の板 (低い六角柱)。spots: [(z, y, size)]。左右に置く。"""
    faces = []
    for (z, y, s, tag) in spots:
        for side in (1, -1):
            if cfg.get("asym") and side < 0 and hash1(tag, 9) < 0.4:
                continue
            zz = z + (hash1(tag * 3 + side, 4) - 0.5) * 3
            p, n = h.at(zz, y + (hash1(tag * 5 + side, 6) - .5) * 3, side)
            faces += plinth("body" if -60 <= zz < 60 else ("head" if zz < -60 else "tail"), "plate", p, n, (0, 1, 0),
                            s * 1.0, s * 0.7, 0.9 + 0.5 * hash1(tag, 2), 0.78, 5, embed=0.8,
                            rot=hash1(tag, 3) * 70)
    return faces


def vertebrae(h, cfg, lod):
    faces = []
    step = (5.0, 9.0, 40.0)[lod]
    z = -58.0
    i = 0
    while z <= 62:
        top = h.top(z)
        hh = 2.0 + 2.6 * hash1(i, 3) + cfg.get("spine_h", 0.0) * (1 - abs(z) / 80)
        r = 1.6 + 0.4 * hash1(i, 4)
        base = V(0, top - 1.2, z)
        faces += spike("spine", "bone", base, V(0, 1, 0), hh + 1.2, r, sides=4, rings=1, bend=V(0, 0, 0.8), hint=(0, 0, 1), phase=0)
        z += step
        i += 1
    return faces


def anchor(h, cfg, lod):
    """鎖の錨石 (+X 側のわき腹、z=60)。鉄の箱と、垂れる鎖の最初の輪。"""
    p, n = h.at(60.5, 36.0, 1)
    faces = []
    faces += plinth("chain", "iron", p, n, (0, 1, 0), 3.0, 6.5, 3.2, 0.8, 4, embed=1.5, rot=45)
    if lod == 0:
        for k in range(3):
            c = p + n * 2.4 + V(0, -6.5 - 3.2 * k, 0)
            faces += plinth("chain", "iron", c, n, (0, 1, 0), 0.9, 1.7, 0.7, 0.7, 4, embed=0.6, rot=45)
    return faces


def barnacles(h, cfg, spots, bone_fn=None):
    faces = []
    for (z, y, side, r, hh, tag) in spots:
        p, n = h.at(z, y, side)
        bone = "jaw" if (z < -66 and y < MOUTH) else ("head" if z < -60 else ("body" if z < 60 else "tail"))
        faces += spike(bone, "crust", p - n * 0.6, n + V(0, -0.3, 0), hh, r, sides=5, rings=1, hint=(0, 1, 0), phase=tag * 17)
    return faces


def arch(bone, mat, zc, R, cy, depth=8.0, thick=3.0, gaps=(), broken_ends=(0, 0), n=16, mats=None):
    """背のアーチ。半円の筒 (四角い断面) を石の塊ごとの区間に分け、gaps の区間は抜く"""
    ang0, ang1 = -22.0, 202.0
    path = []
    for i in range(n + 1):
        a = math.radians(ang0 + (ang1 - ang0) * i / n)
        path.append(V((R - thick / 2) * math.cos(a), cy + (R - thick / 2) * math.sin(a), zc))
    ra = depth / 2 / 0.7071
    rb = thick / 2 / 0.7071
    faces = []
    i = broken_ends[0]
    segs = []
    start = None
    for i in range(broken_ends[0], n + 1 - broken_ends[1]):
        if i in gaps:
            if start is not None:
                segs.append((start, i))
                start = None
            continue
        if start is None:
            start = i
    if start is not None:
        segs.append((start, n - broken_ends[1]))
    for (s0, s1) in segs:
        if s1 - s0 < 1:
            continue
        faces += tube(bone, mat, path[s0:s1 + 1], ra, rb, sides=4, hint=(0, 0, 1), phase=45, caps=(True, True), mats=mats)
    return faces


def glyph_pad(bone, c, size, axis):
    """彫り込みの光る小板 (薄い箱)。axis: 面の向き"""
    return slab(bone, "glyph", c, size, taper=0.0)
