"""B 段になった珊瑚礁の島 (動く遺跡の都市): 甲羅を四段の台地にして頂に高い塔を載せ、縁は胸壁。
頭は彫った仮面、ひれは幅広い櫂。遠くから見て「動く島」に読める。"""
from tcommon import *  # noqa

EDGES = [(0.30, 12.0), (0.50, 14.0), (0.68, 16.0)]


def H_b(u, x, z):
    y = 16.0 + 36.0 * (1 - smooth(0.84, 0.99, u))
    for e, h in EDGES:
        y += h * (1 - smooth(e - 0.02, e + 0.02, u))
    return y + 0.6 * math.sin((z - CZ) / 9.0) * smooth(0.0, 0.2, u) * 0.0


def rim_b(phi):
    return 0.034 if math.cos(24 * phi) > 0 else 0.0


SHAPE = Shape(m=2.6, H=H_b, rimmod=rim_b)
TOP_U = [[0, .14, .28, .32, .40, .48, .52, .60, .66, .70, .77, .82, .86, .90, .94, .98, 1.0],
         [0, .2, .28, .32, .48, .52, .66, .70, .82, .90, .97, 1.0],
         [0, .28, .32, .5, .68, .9, 1.0]]
SECT = [48, 24, 16]
RIM = [[(1.04, 14.5), (1.10, 11.0), (1.17, 9.0), (1.15, 6.0)],
       [(1.10, 11.0), (1.17, 9.0), (1.15, 6.0)],
       [(1.12, 10.0), (1.15, 6.0)]]
UNDER = [[(0.95, 5.2), (0.6, 5.0), (0.0, 5.0)], [(0.7, 5.0), (0.0, 5.0)], [(0.0, 5.0)]]

ST_HEAD = [dict(z=-92, w=22, y0=5, y1=42), dict(z=-104, w=23.5, y0=3, y1=43), dict(z=-118, w=23.5, y0=4, y1=43),
           dict(z=-132, w=21.5, y0=5, y1=42), dict(z=-143, w=18.5, y0=7, y1=39), dict(z=-153, w=15, y0=9, y1=34),
           dict(z=-163, w=10.5, y0=11, y1=29), dict(z=-171, w=6.5, y0=13, y1=25), dict(z=-175, w=3.5, y0=14.5, y1=22)]
HEAD_ST = [ST_HEAD, [ST_HEAD[i] for i in (0, 2, 3, 5, 6, 8)], [ST_HEAD[i] for i in (0, 3, 6, 8)]]
HEAD_LV = [[-0.8, -0.5, "L", 0.15, 0.5, 0.82], [-0.6, "L", 0.3, 0.75], ["L", 0.55]]
HEAD0 = Head(ST_HEAD, HEAD_LV[0], p=4.2, lip_t=-0.3)


def head_b(lod):
    h = Head(HEAD_ST[lod], HEAD_LV[lod], p=4.2, lip_t=-0.3)

    def mf(cz, lo, hi, hh):
        if cz > -143:
            return "skin"
        return "jaw" if hi <= hh.iL else "beak"
    f = h.faces(mf, "head")
    up = (0, 1, 0)
    for side in (1, -1):
        p, n = h.at(-128.0, 30.0, side)
        if lod == 0:
            f += plinth("head", "lid", p, n, up, 9.2, 6.6, 1.8, 0.86, 4, embed=1.2, rot=0)
            f += plinth("head", "lid", p + n * 1.4, n, up, 7.0, 4.8, 1.2, 0.86, 4, embed=0.2, rot=0)
            f += plinth("head", "eye", p + n * 2.4, n, up, 4.2, 3.0, 0.7, 0.92, 4, embed=0.2, rot=0)
            pb, nb = h.at(-130.0, 37.5, side)
            f += plinth("head", "brow", pb + V(0, 0.3, 0), nb, up, 15.5, 3.4, 4.6, 0.66, 4, embed=1.4)
            pn, nn = h.at(-168.0, 22.0, side)
            f += plinth("head", "nostril", pn, nn + V(0, 0.5, -0.2), up, 1.1, 1.6, 0.6, 0.8, 4, embed=0.5)
            # 唇に並ぶ石の歯
            for k, z in enumerate((-149, -156, -163)):
                pt, nt = h.at(z, h.y_lip(z) - 0.8, side)
                f += spike("head", "claw", pt - nt * 0.3, nt + V(0, -0.9, 0), 4.2, 1.5, sides=4, rings=1, hint=(0, 0, 1), phase=45)
        elif lod == 1:
            f += plinth("head", "lid", p, n, up, 8.2, 5.8, 2.4, 0.8, 4, embed=1.0)
            f += plinth("head", "eye", p + n * 1.9, n, up, 4.0, 2.8, 0.7, 0.9, 4, embed=0.2)
            pb, nb = h.at(-130.0, 37.5, side)
            f += plinth("head", "brow", pb, nb, up, 14.0, 3.0, 4.4, 0.66, 4, embed=1.2)
    if lod == 0:
        path = [V(0, h.top(z) + 0.4, z) for z in (-120, -135, -150, -163, -172)]
        f += tube("head", "beak", path, [3.0, 2.8, 2.4, 1.8, 1.0], [1.7, 1.5, 1.3, 1.0, 0.6], sides=4, hint=(1, 0, 0), phase=45, caps=(False, True))
    return f, h


def fins_b(lod):
    nr = (5, 4, 2)[lod]
    faces = []
    zlf = lambda s: -50 + 36 * s ** 1.1
    ztf = lambda s: -4 + 6 * s
    thf = lambda s: 8.0 * (1 - s) + 3.0 * s
    yf = lambda s: 15.0 - 9.0 * s
    f = paddle("fin_front_left", "flip", 66.0, 70.0, nr, zlf, ztf, thf, yf, scallop=0.0, tag=1)
    faces += f + mirror_x(f, "fin_front_right")
    zlr = lambda s: 50 + 30 * s
    ztr = lambda s: 90 + 8 * s
    thr = lambda s: 6.0 * (1 - s) + 2.2 * s
    yr = lambda s: 13.0 - 6.0 * s
    r = paddle("fin_rear_left", "flip", 58.0, 48.0, (2 if lod == 2 else max(3, nr - 1)), zlr, ztr, thr, yr, tag=2)
    faces += r + mirror_x(r, "fin_rear_right")
    faces += spike("tail", "skin", V(0, 17.5, 93), V(0, -0.08, 1), 30, 6.0, sides=4, rings=1, hint=(1, 0, 0), phase=45)
    return faces


def forest_b(lod):
    """段のふちに生える珊瑚の林"""
    faces = []
    if lod == 2:
        return faces
    ring_u = [(0.80, 13), (0.64, 10), (0.47, 8), (0.27, 6)] if lod == 0 else [(0.80, 7), (0.47, 5)]
    i = 0
    for (u, n) in ring_u:
        for k in range(n):
            phi = 2 * math.pi * (k + 0.3 * hash1(i * 31 + k, 2)) / n + 0.2 * u
            x, z = SHAPE.pos(phi, u)
            p, nn = on_surf(SHAPE, x, z, sink=1.2)
            kind = (i + k) % 5
            s = 8 + 7 * hash1(i * 13 + k, 5)
            if kind in (0, 3):
                faces += branch_coral(p, nn + V(0, 0.8, 0), s * 1.3, 2.4, "coral_p" if kind == 0 else "coral_o", depth=1, sides=4, tag=i * 17 + k)
            elif kind == 1:
                faces += tube_coral(p, 4 if lod == 0 else 3, s, 1.8, "coral_v", tag=i * 5 + k)
            elif kind == 2:
                faces += fan_coral(p, s * 1.5, s * 1.1, rot=phi * 57.3 + 90, mat="coral_y")
            else:
                faces += brain_coral(p, s * 0.55, "coral_o", segs=8 if lod == 0 else 5, rings=2 if lod == 0 else 1, flat=1.0, tag=i + k)
        i += 1
    return faces


def city_b(lod):
    faces = []
    top = SHAPE.H(0, CZ)
    faces += tower(0, CZ, top - 3, 36, 17, lod, taper=0.2, glyph=lod == 0)
    if lod == 2:
        return faces
    # 第三の段の柱廊
    y3 = H_b(0.58, 0, 0)
    ncol = 16 if lod == 0 else 8
    for k in range(ncol):
        a = 2 * math.pi * k / ncol
        x, z = SHAPE.pos(a, 0.585)
        hh = 15 if lod == 0 else 12
        if lod == 0 and hash1(k, 4) < 0.25:
            hh = 7
        faces += column("ruins", "ruin", (x, y3 - 1.5, z), hh, 2.6, sides=6 if lod == 0 else 4, taper=0.86)
    if lod == 0:
        # 梁 (崩れた)
        for k in range(ncol):
            if hash1(k, 6) < 0.45:
                continue
            a0, a1 = 2 * math.pi * k / ncol, 2 * math.pi * (k + 1) / ncol
            x0, z0 = SHAPE.pos(a0, 0.585)
            x1, z1 = SHAPE.pos(a1, 0.585)
            c = V((x0 + x1) / 2, y3 + 14.0, (z0 + z1) / 2)
            ang = math.degrees(math.atan2(x1 - x0, z1 - z0))
            faces += slab("ruins", "ruin", tuple(c), (3.2, 3.0, float(np.hypot(x1 - x0, z1 - z0)) + 1.5), taper=0.4, rot=ang)
        # 台の上の石の輪 (光る井戸)
        for k in range(8):
            a = 2 * math.pi * k / 8
            x, z = SHAPE.pos(a, 0.20)
            faces += slab("ruins", "ruin", (x, top - 0.4 + 1.6, z), (5.0, 3.2, 3.4), taper=0.5, rot=-math.degrees(a))
        faces += slab("ruins", "glyph", (0, top + 0.6, 0), (9.0, 0.8, 9.0), taper=1.6) if False else []
        # 階段 (後ろ側の三段を貫く)
        for (u0, y0, w) in ((0.30, 94.0, 14), (0.50, 82.0, 16), (0.68, 68.0, 18)):
            z0 = SHAPE.pos(0.0, u0)[1]
            n = 5
            for j in range(n):
                zz = z0 + 4 + j * 2.6
                yy = y0 - 12 + (j + 1) * (12.0 / n) * 0.0 - j * 0.0
                faces += slab("ruins", "ruin", (0, y0 - (j + 0.5) * (12.0 if u0 < 0.6 else 14.0) / n - 0.4, zz - 0.0), (w, 2.4, 3.2), taper=0.0)
    return faces


def build(lod):
    sh = shell_faces(SHAPE, SECT[lod], TOP_U[lod], RIM[lod], UNDER[lod], jit=0.0)
    head, h = head_b(lod)
    faces = sh + head + fins_b(lod) + forest_b(lod) + city_b(lod)
    faces += gate_frame(lod, mat="ruin")
    if lod == 0:
        for i in range(10):
            phi = 2 * math.pi * (i + 0.5) / 10
            x, z = SHAPE.pos(phi, 1.14)
            faces += kelp_blade((x, 2.0, z), 6.0 + 3 * hash1(i, 4), 3.4, 0.5, i + 40)
    return faces


SAND = np.array([(112, 98, 74), (140, 126, 96), (168, 154, 120), (196, 184, 146), (222, 212, 172)])
STONE_B = np.array([(88, 100, 98), (112, 126, 122), (138, 152, 146), (164, 178, 168), (190, 202, 190)])


def shell_hook(sc, e, d, cell):
    """段ごとの地層の帯"""
    Y = e["Y"]
    band = np.floor(Y * 0.22)
    sh = (hash1(band, 5) - 0.5) * 2.0
    sp = SAND
    base = e["shaded"](sp, 0.5 + 0.14 * sh, 0.30, 0.14) - (e["n_hi"] > 0.8) + (e["n_hi"] < 0.12)
    return e["pick"](sp, base)


def extra_hook(out, glow, e):
    """段の壁に刻文の光る帯"""
    N, X, Y, Z, mats = e["N"], e["X"], e["Y"], e["Z"], e["mats"]
    rr = ((np.abs((X - CX) / AX)) ** 2.6 + (np.abs((Z - CZ) / AZ)) ** 2.6) ** (1 / 2.6)
    riser = (N[..., 1] < 0.55) & (mats == "shell")
    zone = ((rr > 0.26) & (rr < 0.34)) | ((rr > 0.46) & (rr < 0.54)) | ((rr > 0.64) & (rr < 0.72))
    ang = np.arctan2(X, Z - CZ)
    dash = (np.floor(ang * 14.0) % 2 == 0) & (hash1(np.floor(ang * 14.0), 3) < 0.8)
    gl = riser & zone & dash & ((Y % 6.0) < 1.5)
    out = np.where(gl[..., None], e["ramp"](wc.PAL["CYAN"], 0.45 + 0.3 * hash1(np.floor(ang * 14.0), 2)), out)
    glow = glow | gl
    return out, glow


CTX = dict(cfg=None, rune_p=0.12, ruin=np.array([(70, 74, 74), (92, 98, 96), (116, 122, 118), (142, 146, 138), (168, 170, 158)]),
           crust=np.array([(150, 146, 130), (176, 172, 152), (204, 198, 172), (228, 224, 196)]), rune_mats=["ruin"], moss=0.0)
CFG = dict(shell=SAND, plast=np.array([(120, 110, 86), (150, 140, 110), (176, 166, 132), (200, 190, 154)]),
           skin=STONE_B, skin_belly=SAND, flip=STONE_B, beak=np.array([(150, 138, 108), (180, 168, 134), (208, 196, 158), (230, 220, 182), (244, 236, 204)]),
           lid=np.array([(78, 90, 88), (100, 114, 110), (124, 138, 130), (150, 164, 154)]),
           brow=np.array([(70, 82, 80), (92, 106, 102), (116, 130, 124)]), scale_s=7.0, scale_seam=1,
           m=2.6, nm=24, seams=0, cell_tint=0.05, rings=0, shell_hook=shell_hook, extra_hook=extra_hook,
           algae=0.45, algae_y=36.0, algae_pal=np.array([(24, 74, 70), (36, 100, 90), (54, 128, 112), (80, 156, 134)]),
           ymouth=lambda z: HEAD0.y_lip(z) - 0.2, mouth_z=-146, mouth_w=0.6,
           eye_c=(22, 30.0, -128.0), eye_r=3.0, pupil_r=1.5, claw=np.array([(176, 166, 138), (204, 194, 160), (226, 218, 186), (244, 238, 208)]),
           coral_o=np.array([(176, 78, 24), (214, 106, 34), (240, 138, 52), (255, 172, 84)]),
           coral_p=np.array([(190, 64, 100), (222, 90, 126), (242, 124, 152), (255, 160, 180)]),
           coral_v=np.array([(94, 56, 150), (122, 76, 184), (150, 104, 210), (182, 138, 232)]),
           coral_y=np.array([(196, 150, 30), (226, 184, 48), (246, 212, 80), (255, 232, 130)]),
           coral_g=np.array([(40, 120, 100), (60, 150, 124), (84, 178, 148), (120, 206, 174)]),
           kelp=np.array([(30, 80, 40), (44, 108, 52), (64, 136, 66), (90, 164, 88)]))
CTX["hook"] = make_hook(CFG)
OPT = dict(id="b", build=build, ctx=CTX, tex=(2.0, 1.0, 0.5))
