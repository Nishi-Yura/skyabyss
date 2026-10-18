"""A 忠実な面取りの石の鯨: 今の形 (太い青鯨型・背の遺跡二つのアーチと塔・骨の背骨・琥珀の目) を、
斜めの面で刻んだ低ポリにする。"""
from family import *  # noqa


def hook_a(z, t, x, y, i):
    return x, y


def build(lod):
    spec = Spec(levels=LEVELS[lod], jit=0.05 if lod == 0 else 0.03)
    h = Hull(spec, STATIONS[lod])
    faces = hull_faces(h, std_matfn(spec), mouth_inner=lod < 2, seam=lod < 2)
    cfg = {}
    # 顔
    faces += eye_set(h, cfg, lod) if lod < 2 else []
    if lod == 0:
        faces += baleen(h, cfg)
    # ひれ・尾びれ
    fs = dict(rings=5 if lod == 0 else 3 if lod == 1 else 2, th=(7.0, 1.4), L=42)
    f = fin_left(fs)
    faces += f + mirror_x(f, "fin_right")
    faces += fluke(dict(xs=[-58, -48, -37, -26, -15, -7, 0, 7, 15, 26, 37, 48, 58] if lod == 0 else
                        [-58, -36, -14, 0, 14, 36, 58] if lod == 1 else [-58, 0, 58]))
    # 背骨・錨石
    if lod < 2:
        faces += vertebrae(h, cfg, lod)
        faces += anchor(h, cfg, lod)
    # 板・藤壺 (近景)
    if lod == 0:
        spots = [(-100, 44, 5, 1), (-86, 50, 6, 2), (-74, 38, 5, 3), (-54, 46, 7, 4), (-38, 52, 6, 5), (-20, 36, 6, 6),
                 (-8, 46, 7, 7), (12, 50, 6, 8), (24, 40, 6, 9), (40, 46, 5, 10), (56, 36, 5, 11), (72, 42, 4, 12),
                 (-120, 42, 3.5, 13), (88, 40, 3.5, 14)]
        faces += plates_on_flank(h, cfg, spots)
        bs = []
        for i in range(10):
            bs.append((-122 + i * 6.4, 28 + 3 * hash1(i, 1), 1 if i % 2 else -1, 1.5 + hash1(i, 2), 2.5 + 2 * hash1(i, 3), i))
        for i in range(8):
            bs.append((-30 + i * 12, 22 + 2 * hash1(i, 5), 1 if i % 2 else -1, 1.4, 2.4, 40 + i))
        faces += barnacles(h, cfg, bs)
    # 背の遺跡
    faces += ruins_a(h, lod)
    return faces


def ruins_a(h, lod):
    faces = []
    top = lambda z: h.top(z)
    if lod == 0:
        faces += arch("ruins", "ruin", -1.0, 14, top(-1) - 1.0, depth=8, thick=3.2, gaps=(3, 9, 10), broken_ends=(0, 1), n=16)
        faces += arch("ruins", "ruin", 34.0, 14, top(34) - 1.0, depth=8, thick=3.2, gaps=(5, 6, 13), broken_ends=(1, 0), n=16)
        # 塔
        zt = -38.0
        y0 = top(zt) - 3
        faces += slab("ruins", "ruin", (0, y0 + 6, zt), (13, 12, 13), taper=1.4, jit=0.6, tag=1)
        faces += slab("ruins", "ruin", (0, y0 + 16, zt), (10, 9, 10), taper=1.2, jit=0.5, tag=2)
        faces += slab("ruins", "ruin", (0, y0 + 25, zt), (8.5, 9, 8.5), taper=1.0, jit=0.4, tag=3)
        for k, (dx, dz) in enumerate([(-3, -3), (3, -3), (-3, 3), (3, 3)]):
            if k == 2:
                continue
            faces += slab("ruins", "ruin", (dx, y0 + 31.5, zt + dz), (2.4, 4, 2.4), taper=0.4, jit=0.3, tag=10 + k)
        for k, yy in enumerate((y0 + 9, y0 + 18, y0 + 26)):
            w = 6.8 - 1.2 * k
            for sx, sz in ((0, -1), (0, 1), (-1, 0), (1, 0)):
                c = V(sx * (6.0 - 1.1 * k + 0.4), yy, zt + sz * (6.0 - 1.1 * k + 0.4))
                size = (0.7, 3.2, 1.4) if sx else (1.4, 3.2, 0.7)
                faces += slab("ruins", "glyph", c, size)
        # 柱 (崩れた)
        faces += column("ruins", "ruin", (0, top(-66) - 2, -66), 14, 3.2, sides=6, taper=0.85)
        faces += slab("ruins", "ruin", (0, top(-66) + 12.5, -66), (9, 2.4, 9), taper=0.6, jit=0.5, tag=21)
    elif lod == 1:
        faces += arch("ruins", "ruin", -1.0, 14, top(-1) - 1.0, depth=8, thick=3.2, gaps=(), n=8)
        faces += arch("ruins", "ruin", 34.0, 14, top(34) - 1.0, depth=8, thick=3.2, gaps=(4, 5), n=8)
        zt = -38.0
        y0 = top(zt) - 3
        faces += slab("ruins", "ruin", (0, y0 + 14, zt), (12, 28, 12), taper=2.0)
        faces += column("ruins", "ruin", (0, top(-66) - 2, -66), 14, 3.2, sides=4, taper=0.85)
    else:
        zt = -38.0
        y0 = top(zt) - 3
        faces += slab("ruins", "ruin", (0, y0 + 14, zt), (12, 28, 12), taper=2.0)
        for zc in (-1.0, 34.0):
            faces += slab("ruins", "ruin", (-12.5, top(zc) + 6, zc), (3, 14, 8), taper=0.5)
            faces += slab("ruins", "ruin", (12.5, top(zc) + 6, zc), (3, 14, 8), taper=0.5)
            faces += slab("ruins", "ruin", (0, top(zc) + 13.5, zc), (28, 3, 8), taper=0.8)
    return faces


CTX = dict(moss=0.24, rune_p=0.28, strata_w=0.22, strata_y=0.30, stone_base=0.50, belly_lines=0.1, eye_z=-111.0,
           pupil_w=0.5)
OPT = dict(id="a", build=build, ctx=CTX, tex=(2.0, 1.0, 0.5))
