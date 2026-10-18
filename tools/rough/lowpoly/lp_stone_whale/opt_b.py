"""B 古い彫刻の記念碑: 胴の脇が地層の段 (テラス) で刻まれ、背に崩れた神殿・階段・塔・アーチが載る。
顔は彫った仮面 (四角い 2 段のまぶた、分厚い眉、唇に並ぶ石の歯)。"""
from family import *  # noqa

LV_B = [(-0.80, 0.95), (-0.54, 1.0), ("L", 1.0), (-0.02, 1.0), (0.02, 0.92), (0.30, 0.98), (0.34, 0.89),
        (0.58, 0.93), (0.62, 0.82), (0.82, 0.66)]
LEVELS_B = [LV_B, MID_LV, FAR_LV]

SAND = np.array([(56, 52, 56), (74, 68, 70), (92, 86, 84), (112, 104, 98), (134, 124, 114), (156, 144, 130)])
LIME = np.array([(66, 62, 68), (88, 84, 90), (112, 108, 112), (138, 132, 132), (164, 156, 148)])
DKSTONE = np.array([(40, 38, 46), (54, 52, 62), (70, 68, 80), (88, 86, 98)])


def build(lod):
    spec = Spec(levels=LEVELS_B[lod], jit=0.012 if lod == 0 else 0.02, p_top=2.4, p_bot=2.6)
    h = Hull(spec, STATIONS[lod])
    faces = hull_faces(h, std_matfn(spec), mouth_inner=lod < 2, seam=lod < 2)
    cfg = dict(eye_scale=1.0, brow_h=1.2)
    if lod == 0:
        faces += eye_b(h)
        faces += teeth_b(h)
        faces += crest_b(h)
    elif lod == 1:
        faces += eye_set(h, cfg, 1)
    fs = dict(rings=4 if lod == 0 else 3 if lod == 1 else 2, th=(8.0, 2.0), L=42, sweep=1.0, drop=0.25)
    f = fin_left(fs)
    faces += f + mirror_x(f, "fin_right")
    faces += fluke(dict(xs=[-58, -46, -34, -20, -9, 0, 9, 20, 34, 46, 58] if lod == 0 else
                        [-58, -30, 0, 30, 58] if lod == 1 else [-58, 0, 58], thick=1.8))
    if lod < 2:
        faces += anchor(h, cfg, lod)
        faces += keel_b(h, lod)
    faces += ruins_b(h, lod)
    if lod == 0:
        bs = []
        for i in range(12):
            bs.append((-120 + i * 5.5, 28 + 3 * hash1(i, 1), 1 if i % 2 else -1, 1.4 + hash1(i, 2), 2.2 + 1.6 * hash1(i, 3), i))
        faces += barnacles(h, cfg, bs)
    return faces


def eye_b(h, zc=-111.0, yc=39.5):
    faces = []
    for side in (1, -1):
        p, n = h.at(zc, yc, side)
        up = (0, 1, 0)
        faces += plinth("head", "lid", p, n, up, 11.0, 7.4, 1.8, 0.86, 4, embed=1.4, rot=45)
        faces += plinth("head", "lid", p + n * 1.5, n, up, 8.6, 5.4, 1.6, 0.84, 4, embed=0.2, rot=45)
        faces += plinth("head", "eye", p + n * 2.9, n, up, 6.0, 3.4, 0.6, 0.94, 4, embed=0.2, rot=45)
        pb, nb = h.at(zc, yc + 8.0, side)
        faces += plinth("head", "brow", pb, nb, up, 15.0, 2.8, 3.6, 0.7, 4, embed=1.4, rot=45)
    return faces


def teeth_b(h):
    """唇に並ぶ石の歯 (上顎から下へ、四角錐)。"""
    faces = []
    n, iL = h.n, h.iL
    for a in range(len(h.st)):
        z = h.st[a]
        if not (-126 <= z <= -70):
            continue
        r = h.rings[a]
        for k in (1 + iL, 2 * n + 1 - iL):
            p = r[k]
            faces += spike("head", "baleen", p + V(0, 0.6, 0), V(0, -1, 0), 3.8, 1.3, sides=4, rings=1, hint=(0, 0, 1), phase=45)
    return faces


def crest_b(h):
    """頭の上の彫った冠 (低い段の石)。"""
    faces = []
    for i, z in enumerate((-122, -112, -102, -92)):
        top = h.top(z)
        faces += slab("head", "ruin", (0, top - 0.5, z), (10 - i * 1.5, 3.4, 7), taper=0.9, jit=0.5, tag=40 + i)
    return faces


def keel_b(h, lod):
    """背骨の代わりの、石の塊の稜線。"""
    faces = []
    z = -70.0
    step = 10.0 if lod == 0 else 18.0
    i = 0
    while z <= 62:
        if not (-56 < z < 30):
            top = h.top(z)
            faces += slab("spine", "ruin", (0, top + 0.5, z), (4.5, 3.4, step * 0.7), taper=0.8, jit=0.4, tag=60 + i)
        z += step
        i += 1
    return faces


def ruins_b(h, lod):
    faces = []
    top = h.top
    P0 = 70.0
    if lod == 0:
        # 三段の台
        faces += slab("ruins", "ruin", (0, 63.5, -12), (30, 11, 70), taper=2.2, jit=0.7, tag=1, bottom=False)
        faces += slab("ruins", "ruin", (0, 71.5, -12), (22, 5, 56), taper=1.6, jit=0.6, tag=2)
        faces += slab("ruins", "ruin", (0, 76, -12), (15, 4.4, 40), taper=1.3, jit=0.5, tag=3)
        y0 = 78.2
        # 柱廊 (崩れた柱を抜く)
        zs = [-27, -16, -5, 6, 17]
        for i, z in enumerate(zs):
            for sx in (-1, 1):
                if hash1(i * 2 + (sx > 0), 21) < 0.2:
                    h_ = 4 + 4 * hash1(i, 22)
                else:
                    h_ = 15
                faces += column("ruins", "ruin", (sx * 5.6, y0, z), h_, 1.9, sides=6, taper=0.88)
            faces += slab("ruins", "ruin", (0, y0 + 15.8, z), (13.4, 1.8, 3.6), taper=0.4, jit=0.3, tag=80 + i) \
                if hash1(i, 24) > 0.25 else []
        # 塔 (台の頭側)
        zt = -52.0
        yb = 70.5
        faces += slab("ruins", "ruin", (0, yb + 6, zt), (11, 12, 11), taper=1.3, jit=0.5, tag=5)
        faces += slab("ruins", "ruin", (0, yb + 16, zt), (8.6, 9, 8.6), taper=1.0, jit=0.4, tag=6)
        faces += slab("ruins", "ruin", (0, yb + 25, zt), (7, 9, 7), taper=0.9, jit=0.3, tag=7)
        for k, (dx, dz) in enumerate([(-2.6, -2.6), (2.6, -2.6), (-2.6, 2.6), (2.6, 2.6)]):
            if k == 3:
                continue
            faces += slab("ruins", "ruin", (dx, yb + 31.5, zt + dz), (2.2, 4, 2.2), taper=0.4, jit=0.3, tag=15 + k)
        for k, yy in enumerate((yb + 9, yb + 18, yb + 26)):
            for sx, sz in ((0, -1), (0, 1), (-1, 0), (1, 0)):
                off = 5.4 - 1.0 * k + 0.3
                c = V(sx * off, yy, zt + sz * off)
                faces += slab("ruins", "glyph", c, (0.7, 3.4, 1.4) if sx else (1.4, 3.4, 0.7))
        # 階段 (尾側から台へ)
        for i in range(7):
            zc = 36 + 3.6 * (6 - i)
            yc = top(zc) - 3 + 1.2 * i
            faces += slab("ruins", "ruin", (0, yc + 1.5, zc), (9 - 0.3 * i, 3.6 + 1.2 * i, 3.6), taper=0.2, jit=0.2, tag=100 + i)
        # アーチ (尾側)
        faces += arch("ruins", "ruin", 54.0, 11, top(54) - 1.0, depth=7, thick=2.8, gaps=(3, 12), broken_ends=(0, 2), n=14)
        faces += arch("ruins", "ruin", 74.0, 9, top(74) - 1.0, depth=6, thick=2.6, gaps=(2, 3, 4, 10), broken_ends=(1, 0), n=12)
        # 頭側の壁
        for i, (z, hh) in enumerate([(-68, 7), (-76, 4), (-84, 9)]):
            faces += slab("ruins", "ruin", (0, top(z) + hh / 2 - 1.5, z), (13 - i, hh, 6.2), taper=0.6, jit=0.6, tag=120 + i)
        # 光る刻文の帯 (台の側面)
        for z in (-36, -14, 8):
            for sx in (-1, 1):
                faces += slab("ruins", "glyph", (sx * 14.7, 63.5, z), (0.6, 2.2, 6.0))
    elif lod == 1:
        faces += slab("ruins", "ruin", (0, 63.5, -12), (30, 11, 70), taper=2.2)
        faces += slab("ruins", "ruin", (0, 71.5, -12), (22, 5, 56), taper=1.6)
        faces += slab("ruins", "ruin", (0, 76, -12), (15, 4.4, 40), taper=1.3)
        for z in (-27, -5, 17):
            for sx in (-1, 1):
                faces += column("ruins", "ruin", (sx * 5.6, 78.2, z), 15, 1.9, sides=4, taper=0.88)
        faces += slab("ruins", "ruin", (0, 82, -52), (9, 26, 9), taper=1.4)
        for i in range(4):
            zc = 38 + 5 * (3 - i)
            faces += slab("ruins", "ruin", (0, top(zc) - 1 + 1.6 * i, zc), (9, 4 + 1.6 * i, 5), taper=0.2)
        faces += arch("ruins", "ruin", 54.0, 11, top(54) - 1.0, depth=7, thick=2.8, gaps=(), n=8)
    else:
        faces += slab("ruins", "ruin", (0, 66, -12), (30, 16, 70), taper=3.0)
        faces += slab("ruins", "ruin", (0, 77, -12), (14, 14, 40), taper=2.5)
        faces += slab("ruins", "ruin", (0, 82, -52), (9, 26, 9), taper=1.4)
    return faces


def hook_b(col, glow, e):
    """脇の刻文の帯: 胴の脇の段の上に、光る文字を並べる"""
    band = (e["mats"] == "stone") & (e["Y"] > 44.5) & (e["Y"] < 56) & (np.abs(e["N"][..., 1]) < 0.65) & (np.abs(e["X"]) > 8)
    gl = rune(e["LU"], e["LV"], e["CW"], e["CH"], e["D"], e["INS"], e["fi"], 0.34) & band
    e["put"](gl, ramp(PAL["CYAN"], 0.45 + 0.3 * hash1(e["LU"] + e["LV"] * 3, 2)))
    return col if False else _mixed(col, gl, ramp(PAL["CYAN"], 0.5 + 0.3 * hash1(e["LU"] + e["LV"] * 3, 2))), glow | gl


def _mixed(col, mask, c):
    return np.where(mask[..., None], c, col)


CTX = dict(moss=0.12, rune_p=0.35, strata_w=0.55, strata_y=0.40, stone=SAND, plate=SAND, ruin=LIME, lid=DKSTONE, brow=DKSTONE,
           fin=np.array([(48, 46, 54), (64, 62, 72), (82, 80, 92), (102, 100, 112), (124, 122, 134)]),
           stone_base=0.52, seam_w=0.07, belly_lines=0.08, eye_z=-111.0, pupil_w=0.5, ray_f=0.3, ray_w=0.1, hook=hook_b)
OPT = dict(id="b", build=build, ctx=CTX, tex=(2.0, 1.0, 0.5))
