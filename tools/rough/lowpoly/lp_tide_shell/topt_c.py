"""C 生きた老亀: 柔らかい動物の体。背骨に沿う高い稜線、重なって盛り上がる鱗板、後ろがぎざぎざの縁、
しわの寄った長い首と重たいまぶたの顔、鉤のくちばし、爪のあるひれ。遺跡は半ば埋もれ、珊瑚と海藻が
割れ目から生え、縁から垂れる。"""
from tcommon import *  # noqa

YR = 12.0


def H_c(u, x, z):
    base = YR + 50.0 * (1 - u ** 2.7) ** 0.62
    keel = 9.0 * math.exp(-(x / 12.0) ** 2) * (1 - u ** 2.4) ** 1.2 * (0.75 + 0.25 * math.cos((z - CZ) / 17.0))
    sh = 2.0 * math.sin((z - CZ) / 12.0 + abs(x) / 25.0) * smooth(0.0, 0.4, u) * (1 - smooth(0.6, 0.95, u))
    return base + keel + sh


def rim_c(phi):
    saw = abs(((18 * phi / (2 * math.pi)) % 1.0) - 0.5) * 2.0 - 0.5
    return 0.012 * math.cos(11 * phi) + max(0.0, math.cos(phi)) * 0.05 * saw


SHAPE = Shape(m=2.4, H=H_c, rimmod=rim_c)
TOP_U = [[0, .09, .18, .27, .36, .45, .53, .60, .67, .73, .79, .84, .88, .92, .95, .98, 1.0],
         [0, .22, .42, .58, .72, .82, .90, .96, 1.0],
         [0, .45, .75, .92, 1.0]]
SECT = [36, 22, 12]
RIM = [[(1.05, 11.0), (1.13, 9.0), (1.20, 7.2), (1.16, 5.2)],
       [(1.07, 11.0), (1.20, 7.2), (1.16, 5.2)],
       [(1.12, 10.0), (1.17, 5.2)]]
UNDER = [[(0.95, 4.6), (0.7, 4.4), (0.4, 4.4), (0.0, 4.4)], [(0.8, 4.4), (0.0, 4.4)], [(0.0, 4.4)]]

# 首が持ち上がり、頭は少し下を向く。首は 5 ユニットごとにしわ (幅が増減)
ST_HEAD = [dict(z=-90, w=23, y0=4, y1=40), dict(z=-98, w=21.5, y0=7, y1=46), dict(z=-103, w=23, y0=9, y1=50),
           dict(z=-108, w=21.0, y0=10, y1=53), dict(z=-113, w=22.6, y0=11, y1=55), dict(z=-118, w=20.6, y0=12, y1=56),
           dict(z=-123, w=22.2, y0=13, y1=56), dict(z=-129, w=22.5, y0=14, y1=56.5), dict(z=-138, w=20.5, y0=15.5, y1=54),
           dict(z=-148, w=16.5, y0=17.5, y1=49), dict(z=-158, w=12, y0=19.5, y1=43), dict(z=-167, w=7.8, y0=21, y1=37),
           dict(z=-173, w=4.4, y0=22, y1=33), dict(z=-176, w=2.2, y0=23, y1=31)]
HEAD_ST = [ST_HEAD, [ST_HEAD[i] for i in (0, 2, 4, 6, 7, 8, 9, 10, 11, 13)], [ST_HEAD[i] for i in (0, 4, 7, 10, 13)]]
HEAD_LV = [[-0.82, -0.55, "L", 0.15, 0.5, 0.82], [-0.6, "L", 0.3, 0.75], ["L", 0.55]]
HEAD0 = Head(ST_HEAD, HEAD_LV[0], p=2.2, lip_t=-0.28)


def head_c(lod):
    h = Head(HEAD_ST[lod], HEAD_LV[lod], p=2.2, lip_t=-0.28, jit=0.02 if lod == 0 else 0.0)

    def mf(cz, lo, hi, hh):
        if cz > -152:
            return "skin"
        return "jaw" if hi <= hh.iL else "beak"
    f = h.faces(mf, "head")
    up = (0, 1, 0)
    ey = 47.0
    for side in (1, -1):
        p, n = h.at(-136.0, ey, side)
        if lod == 0:
            # 重たいまぶた: 目の上に大きく垂れる
            f += plinth("head", "lid", p, n, up, 11.5, 9.0, 2.0, 0.80, 8, embed=1.4, rot=22.5)
            f += plinth("head", "eye", p + n * 2.1, n, up, 4.6, 3.2, 0.7, 0.9, 8, embed=0.2, rot=22.5)
            f += plinth("head", "lid", p + n * 1.6 + V(0, 3.4, 0), n, up, 10.5, 3.8, 1.8, 0.78, 6, embed=0.4)
            f += plinth("head", "lid", p + n * 0.9 - V(0, 3.9, 0), n, up, 9.0, 2.2, 1.4, 0.8, 6, embed=0.4)
            pb, nb = h.at(-136.0, ey + 8.5, side)
            f += plinth("head", "brow", pb + V(0, 0.3, 0), nb, up, 14.0, 3.0, 3.2, 0.6, 6, embed=1.4)
            pn, nn = h.at(-168.0, 33.0, side)
            f += plinth("head", "nostril", pn, nn + V(0, 0.5, -0.2), up, 1.0, 1.6, 0.6, 0.8, 4, embed=0.5)
            # あごと頬の藤壺
            for k, (z, y) in enumerate(((-146, 29), (-141, 24), (-151, 27), (-132, 22))):
                pt, nt = h.at(z, y, side)
                f += spike("head", "crust", pt - nt * 0.6, nt + V(0, -0.3, 0), 2.2 + 1.2 * hash1(k, 3), 1.2, sides=5, rings=1, hint=(0, 1, 0))
            # 口のはしの肉
            pm, nm_ = h.at(-141.0, h.y_lip(-141.0) + 0.2, side)
            f += plinth("head", "skin", pm, nm_, up, 3.6, 2.0, 1.4, 0.7, 6, embed=0.6)
        elif lod == 1:
            f += plinth("head", "lid", p, n, up, 11.0, 8.0, 2.4, 0.76, 6, embed=1.2)
            f += plinth("head", "eye", p + n * 1.4, n, up, 4.4, 3.2, 0.7, 0.9, 6, embed=0.2)
    # 鉤のくちばし
    hook_p = V(0, h.y_lip(-168) + 1.0, -169)
    f += spike("head", "beak", hook_p, V(0, -1.0, -0.25), 8.0, 3.6, sides=4, rings=1, hint=(1, 0, 0), phase=45, bend=V(0, 0, -2.0))
    if lod == 0:
        path = [V(0, h.top(z) + 0.3, z) for z in (-140, -150, -160, -166)]
        f += tube("head", "beak", path, [1.8, 1.6, 1.4, 1.0], [1.0, 0.9, 0.8, 0.6], sides=4, hint=(1, 0, 0), phase=45, caps=(False, True))
    return f, h


def fins_c(lod):
    nr = (7, 5, 2)[lod]
    faces = []
    zlf = lambda s: -60 + 44 * s ** 1.05
    ztf = lambda s: -8 + 12 * s
    thf = lambda s: 8.0 * (1 - s) + 1.4 * s
    yf = lambda s: 14.0 + 3.0 * s - 9.0 * s * s * 0.0
    f = paddle("fin_front_left", "flip", 66.0, 86.0, nr, zlf, ztf, thf, yf, scallop=1.6 if lod == 0 else 0, tag=3)
    if lod == 0:
        for k, (s, sz) in enumerate(((0.93, 5.0), (0.82, 4.4), (0.71, 3.6))):
            p = V(66 + 86 * s, yf(s) + 0.3, zlf(s) + 0.5)
            f += spike("fin_front_left", "claw", p, V(0.3, -0.1, -0.95), sz, 1.3, sides=4, rings=1, hint=(0, 1, 0))
    faces += f + mirror_x(f, "fin_front_right")
    zlr = lambda s: 48 + 40 * s
    ztr = lambda s: 90 + 12 * s
    thr = lambda s: 6.0 * (1 - s) + 1.4 * s
    yr = lambda s: 12.0 + 2.0 * s
    r = paddle("fin_rear_left", "flip", 58.0, 54.0, (2 if lod == 2 else max(3, nr - 2)), zlr, ztr, thr, yr, scallop=1.0 if lod == 0 else 0, tag=4)
    if lod == 0:
        for k, (s, sz) in enumerate(((0.92, 3.8), (0.8, 3.2))):
            p = V(58 + 54 * s, yr(s) + 0.3, zlr(s) + 0.5)
            r += spike("fin_rear_left", "claw", p, V(0.3, -0.1, -0.95), sz, 1.1, sides=4, rings=1, hint=(0, 1, 0))
    faces += r + mirror_x(r, "fin_rear_right")
    faces += spike("tail", "skin", V(0, 17.0, 93), V(0, -0.12, 1), 31, 5.8, sides=5 if lod < 2 else 4, rings=2 if lod == 0 else 1, hint=(1, 0, 0), phase=0, bend=V(0, -2.5, 0))
    return faces


def plates_c(lod):
    """盛り上がった鱗板 (背 5・肋 4 組)・縁の小さな板。重なって生き物の背に見える"""
    faces = []
    ez_v = [-0.82, -0.41, 0.0, 0.41, 0.82]
    ez_c = [-0.80, -0.30, 0.30, 0.80]
    hh = 4.6 if lod == 0 else 5.0
    sides = 6 if lod == 0 else 4
    for k, ez in enumerate(ez_v):
        z = CZ + ez * AZ * (0.97)
        x = 0.0
        p, n = SHAPE.surf(x, z)
        w = 30.0 * (1 - 0.35 * abs(ez) ** 2)
        faces += plinth("body", "plate", p, n, (0, 0, 1), w * 0.5, 17.0 * (0.95 if k in (0, 4) else 1.0), hh + 0.6 * hash1(k, 2), 0.9, sides, embed=0.8, rot=0)
    for side in (1, -1):
        for k, ez in enumerate(ez_c):
            fan = 1.0 + 0.8 * (0.57 - 0.26)
            z = CZ + ez * AZ * 0.93 * 1.0
            x = side * AX * 0.56
            if (abs(ez) > 0.7):
                x = side * AX * 0.50
            p, n = SHAPE.surf(x, z)
            faces += plinth("body", "plate", p, n, (0, 0, 1), 22.0, 24.0 if abs(ez) < 0.5 else 20.0, hh + 0.5 * hash1(k + 7, 2), 0.92, sides, embed=0.8, rot=0)
    if lod == 0:
        n_m = 20
        for k in range(n_m):
            phi = 2 * math.pi * (k + 0.5) / n_m
            x, z = SHAPE.pos(phi, 0.93)
            p, n = SHAPE.surf(x, z)
            ang = math.degrees(math.atan2(x, z - CZ))
            faces += plinth("body", "plate", p, n, (0, 1, 0), 7.4, 7.5, 2.2, 0.86, 4, embed=0.9, rot=0)
    return faces


def ruins_c(lod):
    faces = []
    # 傾いて半ば埋まったアーチ
    top = SHAPE.H
    if lod < 2:
        a = arch("ruins", "ruin", 0.0, 11, 0.0, depth=6.5, thick=3.4, gaps=(4, 9) if lod == 0 else (5,), broken_ends=(0, 2), n=12 if lod == 0 else 8)
        a = transform(a, rot_z(-16.0), V(0, 0, 0))
        faces += transform(a, rot_y(-32.0), V(26, top(26, -28) - 6.5, -28))
    # 傾いた塔の残り
    tx, tz = 34.0, -4.0
    ty = top(tx, tz)
    t = tower(tx, tz, ty - 5.0, 15 if lod == 0 else 14, 11, lod, taper=0.16, crown=False, glyph=lod == 0)
    pv = V(tx, ty, tz)
    R = rot_z(-7.0) @ rot_x(5.0)
    faces += transform(t, R, pv - R @ pv)
    if lod == 0:
        faces += slab("ruins", "glyph", (tx + 1.5, ty + 15.0, tz - 0.3), (3.4, 2.4, 3.4), taper=0.0)
        for i, (x, z, hh, r) in enumerate([(-30, 30, 8, 2.5), (-22, 36, 4, 2.4), (18, 40, 10, 2.5), (12, -44, 6, 2.4), (-14, -52, 9, 2.6)]):
            faces += column("ruins", "ruin", (x, top(x, z) - 2.4, z), hh, r, sides=6, taper=0.84)
        for i, (x, z) in enumerate([(-8, -26), (4, 24), (-36, -4), (46, 24)]):
            faces += slab("ruins", "ruin", (x, top(x, z) - 0.2, z), (6 + hash1(i, 3) * 3, 3.2, 5), taper=0.9, jit=0.8, tag=150 + i, rot=hash1(i, 7) * 60)
        faces += slab("ruins", "glyph", (-12, top(-12, -10) + 0.1, -10), (7.0, 0.7, 7.0), taper=1.4)
    return faces


def corals_c(lod):
    """割れ目 (背骨の稜線・鱗板の間) と縁から生える。大きな塊は少なく、小さな枝と管が散る"""
    if lod == 2:
        return []
    faces = []
    spots = [("branch", 0, -72, 12), ("branch", 0, -34, 14), ("branch", 2, 28, 13), ("branch", 0, 58, 11),
             ("brain", -42, -22, 6.5), ("brain", 48, 36, 6.0), ("brain", -52, 46, 5.5),
             ("tube", 28, -52, 9), ("tube", -30, 12, 8), ("tube", 56, -14, 7.5),
             ("fan", -46, -60, 11), ("fan", 38, 60, 10), ("fan", -18, -4, 9),
             ("branch", 34, -4, 10), ("tube", 20, -22, 6), ("brain", 10, 66, 5)]
    for i, (kind, x, z, s) in enumerate(spots):
        if lod == 1 and i % 2:
            continue
        p, n = on_surf(SHAPE, x, z, sink=1.3)
        if kind == "brain":
            faces += brain_coral(p, s, "coral_o", segs=8 if lod == 0 else 5, rings=2 if lod == 0 else 1, flat=1.0, tag=i)
        elif kind == "branch":
            faces += branch_coral(p, n + V(0, 0.7, 0), s, 2.2, "coral_p" if i % 2 else "coral_o", depth=2 if lod == 0 else 1, sides=4, tag=i + 3)
        elif kind == "tube":
            faces += tube_coral(p, 5 if lod == 0 else 3, s, 1.6, "coral_g" if i % 2 else "coral_v", tag=i + 5)
        else:
            faces += fan_coral(p, s * 1.5, s, rot=hash1(i, 9) * 180, mat="coral_y")
    return faces


def kelp_c(lod):
    """縁の脇腹から垂れる海藻"""
    if lod > 0:
        return []
    faces = []
    n = 16
    for i in range(n):
        phi = 2 * math.pi * (i + 0.5 * hash1(i, 3)) / n
        if abs(math.cos(phi)) > 0.93:
            continue
        x, z = SHAPE.pos(phi, 0.97)
        y = SHAPE.H(x, z) + 3
        ox, oz = math.sin(phi), math.cos(phi)
        faces += kelp_blade((x * 1.0, y, z), -(14 + 10 * hash1(i, 4)), 3.8, 0.7, i + 20, lean=(-ox * 0.45, -oz * 0.45), rows=4)
    for i in range(10):
        phi = 2 * math.pi * (i + 0.5) / 10
        x, z = SHAPE.pos(phi, 1.15)
        faces += kelp_blade((x, 2.0, z), 6.0 + 3 * hash1(i, 4), 3.4, 0.5, i + 60)
    return faces


def barnacles_c(lod):
    if lod > 0:
        return []
    faces = []
    for i in range(70):
        phi = 2 * math.pi * hash1(i, 21)
        u = 0.55 + 0.45 * hash1(i, 22)
        x, z = SHAPE.pos(phi, u)
        p, n = SHAPE.surf(x, z)
        faces += spike("body", "crust", p - n * 0.5, n + V(0, -0.1, 0), 1.8 + 1.8 * hash1(i, 23), 0.9 + 0.5 * hash1(i, 24), sides=5, rings=1, hint=(0, 1, 0))
    return faces


def build(lod):
    sh = shell_faces(SHAPE, SECT[lod], TOP_U[lod], RIM[lod], UNDER[lod], jit=0.5 if lod == 0 else 0.0)
    head, h = head_c(lod)
    faces = sh + head + fins_c(lod) + plates_c(lod) + corals_c(lod) + ruins_c(lod) + kelp_c(lod) + barnacles_c(lod)
    faces += gate_frame(lod, mat="ruin")
    return faces


SHELL = np.array([(46, 44, 34), (64, 60, 44), (84, 78, 56), (106, 98, 70), (130, 120, 86), (156, 144, 104)])
PLATE = np.array([(66, 62, 44), (88, 82, 58), (112, 104, 72), (138, 128, 90), (166, 154, 110)])
SKIN = np.array([(56, 60, 50), (74, 80, 64), (94, 100, 78), (116, 120, 92), (138, 140, 108)])
BELLY = np.array([(150, 138, 106), (176, 164, 128), (198, 188, 150), (218, 208, 170)])
BEAK = np.array([(120, 104, 70), (150, 132, 92), (178, 160, 114), (204, 188, 138), (226, 212, 162)])


def shell_hook(sc, e, d, cell):
    """甲羅の割れ目の暗い筋と、まだらの日焼け"""
    n_lo, n_mid, n_hi, pick, shaded = e["n_lo"], e["n_mid"], e["n_hi"], e["pick"], e["shaded"]
    idx = shaded(SHELL, 0.5 + (n_lo - 0.5) * 0.5, 0.30, 0.18) - (n_hi > 0.78) + (n_hi < 0.14)
    crack = (np.abs(n_mid - 0.5) < 0.025) & (e["Y"] > 24)
    return np.where(crack[..., None], pick(SHELL, idx - 3), pick(SHELL, idx))


CTX = dict(cfg=None, rune_p=0.10, ruin=np.array([(52, 56, 58), (70, 74, 76), (90, 94, 94), (112, 114, 110), (134, 134, 126)]),
           crust=np.array([(150, 146, 130), (176, 172, 152), (204, 198, 172), (228, 224, 196)]), rune_mats=["ruin"], moss=0.0)
CFG = dict(shell=SHELL, plate=PLATE, plast=np.array([(140, 128, 94), (168, 156, 118), (192, 180, 140), (212, 202, 162)]),
           skin=SKIN, skin_belly=BELLY, flip=np.array([(52, 56, 46), (70, 76, 60), (90, 96, 74), (112, 116, 88)]), beak=BEAK,
           lid=np.array([(44, 48, 40), (60, 66, 52), (80, 86, 66), (100, 104, 80)]), brow=np.array([(38, 42, 34), (54, 58, 46), (72, 76, 60)]),
           scale_s=6.4, scale_seam=1, m=2.4, nm=20, vw=0.26, seam_w=1.0, cell_tint=0.3, rings=1, ring_p=3.0, ring_w=0.6, ring_max=18,
           plate_rings=1, pr_p=3.0, pr_max=24, shell_hook=shell_hook,
           algae=0.75, algae_y=52.0, algae_pal=np.array([(24, 62, 56), (34, 86, 72), (52, 112, 90), (78, 140, 108)]),
           ymouth=lambda z: HEAD0.y_lip(z) - 0.2, mouth_z=-148, mouth_w=0.6,
           eye_c=(22, 47.0, -136.0), eye_r=3.2, pupil_r=1.6, claw=np.array([(150, 138, 108), (180, 168, 134), (206, 196, 160), (228, 220, 186)]),
           coral_o=np.array([(176, 78, 24), (214, 106, 34), (240, 138, 52), (255, 172, 84)]),
           coral_p=np.array([(190, 64, 100), (222, 90, 126), (242, 124, 152), (255, 160, 180)]),
           coral_v=np.array([(94, 56, 150), (122, 76, 184), (150, 104, 210), (182, 138, 232)]),
           coral_y=np.array([(196, 150, 30), (226, 184, 48), (246, 212, 80), (255, 232, 130)]),
           coral_g=np.array([(40, 120, 100), (60, 150, 124), (84, 178, 148), (120, 206, 174)]),
           kelp=np.array([(30, 80, 40), (44, 108, 52), (64, 136, 66), (90, 164, 88)]))
CTX["hook"] = make_hook(CFG)
OPT = dict(id="c", build=build, ctx=CTX, tex=(2.0, 1.0, 0.5))
