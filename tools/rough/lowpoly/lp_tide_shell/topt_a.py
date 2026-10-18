"""A 忠実な珊瑚礁の大亀: 今の姿 (平たい丸い甲羅・縁が張り出す・頂の塔・珊瑚・左後ろの潮門・くさび形の頭と淡い嘴)
を、斜めの面だけで素直に刻む。"""
from tcommon import *  # noqa

YR = 14.0


def H_a(u, x, z):
    base = YR + (68.0 - YR) * (1 - u ** 4.4) ** 0.55
    return base + 2.2 * (1 - u * u) ** 1.5 + 0.9 * math.cos((z - CZ) / 14.0) * smooth(0.0, 0.4, u) * (1 - smooth(0.5, 0.9, u))


SHAPE = Shape(m=2.5, H=H_a, rimmod=lambda phi: 0.010 * math.cos(22 * phi))

TOP_U = [[0, .10, .20, .30, .40, .50, .58, .66, .73, .79, .84, .88, .92, .95, .98, 1.0],
         [0, .22, .42, .58, .72, .82, .90, .96, 1.0],
         [0, .45, .75, .92, 1.0]]
SECT = [32, 20, 12]
RIM = [[(1.05, 12.5), (1.12, 10.5), (1.19, 8.8), (1.17, 6.2)],
       [(1.07, 12.0), (1.19, 8.8), (1.17, 6.2)],
       [(1.12, 11.0), (1.17, 6.2)]]
UNDER = [[(0.95, 5.2), (0.7, 5.0), (0.4, 5.0), (0.0, 5.0)],
         [(0.8, 5.0), (0.0, 5.0)],
         [(0.0, 5.0)]]

ST_HEAD = [dict(z=-92, w=21.5, y0=5, y1=40), dict(z=-102, w=22.5, y0=3, y1=42), dict(z=-112, w=23, y0=3, y1=43),
           dict(z=-124, w=22, y0=5, y1=43), dict(z=-136, w=20, y0=6.5, y1=40), dict(z=-147, w=16.5, y0=8.5, y1=36.5),
           dict(z=-157, w=12.5, y0=10.5, y1=32), dict(z=-166, w=8.2, y0=12.5, y1=27.5), dict(z=-172, w=5, y0=14, y1=24),
           dict(z=-175.5, w=2.4, y0=15.5, y1=21.5)]
ST_HEAD_MID = [ST_HEAD[i] for i in (0, 2, 4, 5, 6, 7, 9)]
ST_HEAD_FAR = [ST_HEAD[i] for i in (0, 3, 6, 9)]
HEAD_ST = [ST_HEAD, ST_HEAD_MID, ST_HEAD_FAR]
HEAD_LV = [[-0.82, -0.55, "L", 0.15, 0.5, 0.82], [-0.6, "L", 0.3, 0.75], ["L", 0.55]]


HEAD0 = Head(ST_HEAD, HEAD_LV[0], p=2.3, lip_t=-0.28)


def head_a(lod):
    h = Head(HEAD_ST[lod], HEAD_LV[lod], p=2.3, lip_t=-0.28, jit=0.012 if lod == 0 else 0.0)

    def mf(cz, lo, hi, hh):
        if cz > -146:
            return "skin"
        return "jaw" if hi <= hh.iL else "beak"
    f = h.faces(mf, "head")
    if lod == 0:
        for side in (1, -1):
            p, n = h.at(-130.5, 30.5, side)
            up = (0, 1, 0)
            f += plinth("head", "lid", p, n, up, 8.8, 6.2, 1.7, 0.82, 8, embed=1.2, rot=22.5)
            f += plinth("head", "lid", p + n * 1.4, n, up, 6.8, 4.6, 1.3, 0.80, 8, embed=0.2, rot=22.5)
            f += plinth("head", "eye", p + n * 2.5, n, up, 3.8, 2.7, 0.7, 0.92, 8, embed=0.2, rot=22.5)
            pb, nb = h.at(-130.5, 36.8, side)
            f += plinth("head", "brow", pb + V(0, 0.2, 0), nb, up, 11.5, 2.6, 2.4, 0.62, 6, embed=1.2)
            pn, nn = h.at(-169.0, 22.5, side)
            f += plinth("head", "nostril", pn, nn + V(0, 0.5, -0.2), up, 1.0, 1.5, 0.6, 0.8, 4, embed=0.5)
        path = [V(0, h.top(z) + 0.3, z) for z in (-146, -156, -165, -172)]
        f += tube("head", "beak", path, [1.5, 1.3, 1.0, 0.7], [1.0, 0.9, 0.8, 0.6], sides=4, hint=(1, 0, 0), phase=45, caps=(False, True))
    elif lod == 1:
        for side in (1, -1):
            p, n = h.at(-130.5, 30.5, side)
            f += plinth("head", "lid", p, n, (0, 1, 0), 8.0, 5.6, 2.0, 0.78, 6, embed=1.0)
            f += plinth("head", "eye", p + n * 1.8, n, (0, 1, 0), 4.2, 2.8, 0.7, 0.9, 6, embed=0.2)
    return f, h


def fins_a(lod):
    nr = (7, 5, 2)[lod]
    faces = []
    zlf = lambda s: -52 + 50 * s ** 1.15
    ztf = lambda s: -12 + 16 * s
    thf = lambda s: 7.0 * (1 - s) + 1.5 * s
    yf = lambda s: 14.0 - 11.0 * s
    f = paddle("fin_front_left", "flip", 68.0, 80.0, nr, zlf, ztf, thf, yf, scallop=1.3 if lod == 0 else 0, tag=1)
    if lod == 0:
        for k, (s, sz) in enumerate(((0.9, 4.6), (0.78, 3.6))):
            p = V(68 + 80 * s, yf(s) + 0.2, zlf(s) + 0.4)
            f += spike("fin_front_left", "claw", p, V(0.35, -0.1, -0.9), sz, 1.2, sides=4, rings=1, hint=(0, 1, 0))
    faces += f + mirror_x(f, "fin_front_right")
    zlr = lambda s: 50 + 42 * s
    ztr = lambda s: 88 + 12 * s
    thr = lambda s: 5.5 * (1 - s) + 1.2 * s
    yr = lambda s: 13.0 - 7.0 * s
    r = paddle("fin_rear_left", "flip", 58.0, 52.0, (2 if lod == 2 else max(3, nr - 2)), zlr, ztr, thr, yr, scallop=0.9 if lod == 0 else 0, tag=2)
    faces += r + mirror_x(r, "fin_rear_right")
    faces += spike("tail", "skin", V(0, 17.5, 93), V(0, -0.08, 1), 30, 5.6, sides=5 if lod < 2 else 4, rings=2 if lod == 0 else 1, hint=(1, 0, 0), phase=0)
    return faces


def corals_a(lod):
    if lod == 2:
        return []
    faces = []
    spots = [("brain", -46, -62, 7.0), ("brain", 52, -46, 6.0), ("brain", -54, 38, 7.5), ("brain", 20, 66, 5.0),
             ("branch", 30, 50, 15.0), ("branch", -32, -16, 14.0), ("branch", 62, -8, 11.0),
             ("tube", 8, -80, 9.0), ("tube", -64, -10, 8.0), ("tube", -14, 76, 8.0),
             ("fan", 40, -70, 12.0), ("fan", -38, 64, 11.0), ("fan", 60, 28, 9.0), ("fan", -60, -40, 10.0)]
    for i, (kind, x, z, s) in enumerate(spots):
        if lod == 1 and kind in ("tube", "fan") and i % 2:
            continue
        p, n = on_surf(SHAPE, x, z, sink=1.0)
        if kind == "brain":
            faces += brain_coral(p, s, "coral_o", segs=8 if lod == 0 else 5, rings=2 if lod == 0 else 1, flat=1.0, tag=i)
        elif kind == "branch":
            faces += branch_coral(p, n + V(0, 0.6, 0), s, 2.0, "coral_p", depth=2 if lod == 0 else 1, sides=4, tag=i + 3)
        elif kind == "tube":
            faces += tube_coral(p, 5 if lod == 0 else 3, s, 1.7, "coral_v", tag=i + 5)
        else:
            faces += fan_coral(p, s * 1.5, s, rot=hash1(i, 9) * 180, mat="coral_y")
    return faces


def ruins_a(lod):
    faces = []
    bt, _ = on_surf(SHAPE, 30, -6, 0.0)
    faces += tower(30, -6, bt[1] - 2.0, 19, 11, lod, glyph=lod == 0)
    if lod == 2:
        return faces
    # 光る井戸 (潮の広間の真上): 石の輪
    wy = SHAPE.H(-14, -8) - 0.6
    n = 8 if lod == 0 else 6
    for k in range(n):
        a = 2 * math.pi * k / n
        faces += slab("ruins", "ruin", (-14 + 9 * math.cos(a), wy + 1.6, -8 + 9 * math.sin(a)), (5.0, 3.2, 3.2), taper=0.5, rot=-math.degrees(a))
    faces += slab("ruins", "glyph", (-14, wy + 0.9, -8), (10.5, 0.8, 10.5), taper=2.0)
    # 崩れたアーチと柱
    a = arch("ruins", "ruin", 0.0, 10, 0.0, depth=6.5, thick=3.2, gaps=(5, 9) if lod == 0 else (6,), broken_ends=(0, 1), n=12 if lod == 0 else 8)
    ay = SHAPE.H(-34, -34) - 4
    faces += transform(a, rot_y(25.0), V(-34, ay, -34))
    if lod == 0:
        for i, (x, z, hh, r) in enumerate([(-8, 24, 9, 2.4), (-2, 30, 5, 2.2), (-28, 18, 8, 2.3), (14, 30, 12, 2.5)]):
            faces += column("ruins", "ruin", (x, SHAPE.H(x, z) - 1.8, z), hh, r, sides=6, taper=0.84)
        for i, (x, z) in enumerate([(14, -30), (-6, -36), (44, 18)]):
            faces += slab("ruins", "ruin", (x, SHAPE.H(x, z) - 0.4, z), (6 + hash1(i, 3) * 3, 3.2, 5), taper=0.9, jit=0.8, tag=140 + i, rot=hash1(i, 7) * 60)
    return faces


def kelp_a(lod):
    if lod > 0:
        return []
    faces = []
    for i in range(14):
        phi = 2 * math.pi * (i + 0.5 * hash1(i, 3)) / 14
        x, z = SHAPE.pos(phi, 1.13)
        faces += kelp_blade((x, 2.0, z), 6.0 + 3 * hash1(i, 4), 3.4, 0.6, i, lean=(0.3 * math.sin(phi), 0.3 * math.cos(phi)))
    return faces


def barnacles_a(lod):
    if lod > 0:
        return []
    faces = []
    for i in range(46):
        phi = 2 * math.pi * hash1(i, 21)
        u = 0.9 + 0.2 * hash1(i, 22)
        x, z = SHAPE.pos(phi, u)
        y = SHAPE.H(x, z) if u <= 1 else 12.0 - (u - 1.0) * 30
        p, n = SHAPE.surf(x, z)
        faces += spike("body", "crust", V(x, min(y, 34) - 0.4, z), n + V(0, -0.2, 0), 1.8 + 1.4 * hash1(i, 23), 0.9 + 0.5 * hash1(i, 24), sides=5, rings=1, hint=(0, 1, 0))
    return faces


def build(lod):
    sh = shell_faces(SHAPE, SECT[lod], TOP_U[lod], RIM[lod], UNDER[lod], jit=0.45 if lod == 0 else 0.0)
    head, h = head_a(lod)
    faces = sh + head + fins_a(lod) + corals_a(lod) + ruins_a(lod) + kelp_a(lod) + barnacles_a(lod)
    faces += gate_frame(lod)
    return faces


SHELL = np.array([(34, 78, 74), (44, 98, 90), (56, 120, 106), (72, 142, 124), (92, 164, 142), (116, 186, 160)])
SKIN = np.array([(44, 62, 46), (58, 80, 58), (74, 98, 70), (92, 116, 84), (112, 134, 98)])
BELLY = np.array([(150, 140, 110), (176, 166, 132), (198, 188, 152), (216, 206, 170)])
BEAK = np.array([(150, 134, 100), (178, 162, 124), (204, 190, 148), (226, 214, 174), (240, 232, 198)])
PL = np.array([(150, 142, 112), (176, 168, 134), (198, 190, 154), (218, 210, 174), (234, 226, 192)])

CTX = dict(cfg=None, rune_p=0.10, ruin=np.array([(52, 54, 62), (70, 72, 82), (90, 92, 102), (112, 112, 118), (134, 132, 134)]),
           crust=np.array([(150, 146, 130), (176, 172, 152), (204, 198, 172), (228, 224, 196)]),
           rune_mats=["ruin"], moss=0.0)
CFG = dict(shell=SHELL, plast=PL, skin=SKIN, skin_belly=BELLY, flip=np.array([(40, 58, 44), (54, 76, 56), (70, 94, 68), (88, 112, 82)]),
           beak=BEAK, lid=np.array([(40, 56, 42), (56, 78, 56), (74, 98, 70), (92, 116, 84)]),
           brow=np.array([(36, 50, 38), (50, 70, 50), (66, 88, 62)]),
           m=2.5, nm=22, vw=0.26, seam_w=0.95, cell_tint=0.26, rings=1, ring_p=3.4, ring_w=0.55, ring_max=14,
           algae=0.55, algae_y=34.0, algae_pal=np.array([(24, 56, 44), (36, 80, 56), (54, 108, 66), (80, 134, 82)]),
           ymouth=lambda z: HEAD0.y_lip(z) - 0.2, mouth_z=-146, mouth_w=0.55,
           eye_c=(22, 30.5, -130.5), eye_r=3.0, pupil_r=1.5,
           coral_o=np.array([(176, 78, 24), (214, 106, 34), (240, 138, 52), (255, 172, 84)]),
           coral_p=np.array([(190, 64, 100), (222, 90, 126), (242, 124, 152), (255, 160, 180)]),
           coral_v=np.array([(94, 56, 150), (122, 76, 184), (150, 104, 210), (182, 138, 232)]),
           coral_y=np.array([(196, 150, 30), (226, 184, 48), (246, 212, 80), (255, 232, 130)]),
           coral_g=np.array([(40, 120, 100), (60, 150, 124), (84, 178, 148), (120, 206, 174)]),
           kelp=np.array([(30, 80, 40), (44, 108, 52), (64, 136, 66), (90, 164, 88)]))
CTX["hook"] = make_hook(CFG)
OPT = dict(id="a", build=build, ctx=CTX, tex=(2.0, 1.0, 0.5))
