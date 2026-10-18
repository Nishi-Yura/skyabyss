"""C 風化した皮が岩になる鯨: やわらかい鯨の体 (額のふくらみ、喉のひだ、長い胸びれ、切れ込みの尾びれ) を、
背から石の殻が広がって覆っていく。小さな遺跡は半ば埋まって苔に呑まれる。"""
from family import *  # noqa

LV_C = [(-0.90, 0.96), (-0.77, 1.0), (-0.64, 0.96), (-0.52, 1.0), ("L", 1.0), (-0.04, 1.0), (0.20, 1.0), (0.42, 1.0),
        (0.62, 1.0), (0.80, 1.0)]
MID_LV_C = [(-0.70, 1.0), ("L", 1.0), (0.15, 1.0), (0.60, 1.0)]
LEVELS_C = [LV_C, MID_LV_C, FAR_LV]
ST_C = [-132, -131.2, -129.8, -127.5, -124, -119, -113, -107, -101, -95, -89, -83, -77, -71, -66, -60, -53, -46, -39, -32,
        -25, -18, -11, -4, 3, 10, 17, 24, 31, 38, 45, 52, 60, 67, 74, 81, 88, 95, 102, 109, 116, 123, 130, 138]
STATIONS_C = [ST_C, MID_ST, FAR_ST]


def hook_c(z, t, x, y, i):
    # 額のふくらみ (メロン) と、肩のこぶ
    if t > 0.1 and -128 < z < -88:
        k = math.sin(math.pi * (z + 128) / 40.0) ** 1.4
        y += 4.0 * k * min(1.0, (t - 0.1) / 0.5)
    if t > 0.5 and -50 < z < 10:
        y += 1.6 * math.sin(math.pi * (z + 50) / 60.0) * (t - 0.5)
    return x, y


def build(lod):
    spec = Spec(levels=LEVELS_C[lod], jit=0.028 if lod == 0 else 0.02, hook=hook_c, p_top=2.1, p_bot=2.0)
    h = Hull(spec, STATIONS_C[lod])
    faces = hull_faces(h, std_matfn(spec), mouth_inner=lod < 2, seam=lod < 2)
    cfg = dict(eye_scale=0.9, brow_h=0.0)
    if lod < 2:
        faces += eye_set(h, cfg, lod)
    if lod == 0:
        faces += tubercles(h)
        faces += jaw_cones(h)
        faces += shell_plates(h)
        faces += barnacle_clusters(h)
    elif lod == 1:
        faces += shell_plates(h, coarse=True)
    fs = dict(rings=7 if lod == 0 else 4 if lod == 1 else 2, th=(7.0, 1.4), L=50, x0=24.0, sweep=1.15, drop=0.45,
              z=(-60.0, -38.0, -22.0), scallop=1.6 if lod == 0 else 0)
    f = fin_left(fs)
    faces += f + mirror_x(f, "fin_right")
    faces += fluke(dict(xs=[-62, -52, -42, -32, -22, -12, -5, 0, 5, 12, 22, 32, 42, 52, 62] if lod == 0 else
                        [-62, -36, -12, 0, 12, 36, 62] if lod == 1 else [-62, 0, 62], thick=1.1,
                        notch=2.4 if lod == 0 else 0, dihedral=0.06))
    if lod < 2:
        faces += spine_c(h, lod)
        faces += anchor(h, cfg, lod)
    faces += ruins_c(h, lod)
    return faces


def tubercles(h):
    """頭のこぶ (ザトウクジラの瘤): 先が丸い低い錐。上と鼻先に。"""
    faces = []
    i = 0
    for z in (-126, -120, -114, -108, -102, -96):
        for xx in (-9, -4.5, 0, 4.5, 9):
            if hash1(i, 61) < 0.35:
                i += 1
                continue
            xo = xx + (hash1(i, 62) - .5) * 2
            zz = z + (hash1(i, 63) - .5) * 3
            top = h.top(zz)
            r = h.ring_at(zz)[0]
            hw = np.abs(r[h.n // 2 + 1][0])
            # 背の上の高さ (幅に合わせる)
            frac = abs(xo) / max(hw + 4, 1)
            p = V(xo, top - 3.0 * frac ** 2 - 0.3, zz)
            faces += spike("head", "crust", p, V(xo * 0.04, 1, 0), 2.2 + 1.8 * hash1(i, 64), 1.3 + 0.6 * hash1(i, 65),
                           sides=5, rings=1, hint=(0, 0, 1), phase=i * 31)
            i += 1
    return faces


def jaw_cones(h):
    faces = []
    n, iL = h.n, h.iL
    i = 0
    for z in (-128, -123, -117, -111, -105, -99, -93, -87, -81):
        for side in (1, -1):
            p, nn = h.at(z, MOUTH - 2.0 - 2 * hash1(i, 3), side)
            faces += spike("jaw", "crust", p - nn * 0.5, nn + V(0, -0.5, 0), 2.6 + 1.6 * hash1(i, 4), 1.4,
                           sides=5, rings=1, hint=(0, 1, 0), phase=i * 29)
            i += 1
    return faces


def barnacle_clusters(h):
    faces = []
    i = 0
    spots = [(-84, 22, 1), (-60, 18, -1), (-30, 17, 1), (4, 16, -1), (34, 18, 1), (-110, 24, -1), (-12, 20, 1), (-45, 22, -1)]
    for (z, y, side) in spots:
        for j in range(6):
            zz = z + (hash1(i * 6 + j, 71) - .5) * 9
            yy = y + (hash1(i * 6 + j, 72) - .5) * 5
            p, nn = h.at(zz, yy, side)
            bone = "jaw" if (zz < -66 and yy < MOUTH) else ("head" if zz < -60 else ("body" if zz < 60 else "tail"))
            faces += spike(bone, "crust", p - nn * 0.5, nn + V(0, -0.4, 0), 2.0 + 2.0 * hash1(i * 6 + j, 73),
                           1.1 + 0.6 * hash1(i * 6 + j, 74), sides=5, rings=1, hint=(0, 1, 0), phase=j * 23)
        i += 1
    return faces


def shell_plates(h, coarse=False):
    """背から広がる石の殻片 (六角の低い板)。上ほど密、脇は下へ薄れる。"""
    faces = []
    N = 70 if not coarse else 20
    for i in range(N):
        z = -100 + 190 * hash1(i, 81)
        t = 0.18 + 0.82 * hash1(i, 82) ** 0.7
        side = 1 if hash1(i, 83) < 0.5 else -1
        y = YC + t * dims(z)[1] / 2 * (0.95 if t < 0.9 else 0.95)
        if t > 0.92:
            side = 1
            y = h.top(z) - 0.8
        p, nn = h.at(z, min(y, h.top(z) - 1.0), side)
        s = (3.6 + 4.0 * hash1(i, 84)) * (1.0 if not coarse else 1.4)
        bone = "head" if z < -60 else ("body" if z < 60 else "tail")
        if coarse:
            faces += plinth(bone, "plate", p, nn, (0, 1, 0), s, s * 0.7, 1.4, 0.8, 4, embed=0.8, rot=45 + hash1(i, 5) * 30)
        else:
            faces += plinth(bone, "plate", p, nn, (0, 1, 0), s, s * 0.78, 1.0 + 1.0 * hash1(i, 85), 0.7, 6, embed=0.8,
                            rot=hash1(i, 86) * 60)
    return faces


def spine_c(h, lod):
    """背の骨が皮を破って出る: 大きさのふぞろいな棘"""
    faces = []
    step = 7.0 if lod == 0 else 14.0
    z = -58.0
    i = 0
    while z <= 62:
        if hash1(i, 91) > 0.15:
            top = h.top(z)
            hh = 2.5 + 5.5 * hash1(i, 92) ** 1.6
            faces += spike("spine", "bone", V(0.4 * (hash1(i, 93) - .5), top - 1.4, z), V(0, 1, 0.1), hh + 1.4,
                           1.7 + 0.9 * hash1(i, 94), sides=4, rings=1, hint=(0, 0, 1), phase=0)
        z += step
        i += 1
    return faces


def ruins_c(h, lod):
    """半ば埋まった小さな遺跡: 傾いたアーチと折れた柱。"""
    faces = []
    top = h.top
    if lod == 0:
        a = arch("ruins", "ruin", 22.0, 12, top(22) - 6.0, depth=7.5, thick=3.2, gaps=(4, 5, 13), broken_ends=(0, 1), n=14)
        pv = V(0, top(22) - 4, 22)
        R = rot_z(-14.0)
        faces += transform(a, R, pv - R @ pv)
        a2 = arch("ruins", "ruin", -48.0, 10, top(-48) - 5.0, depth=6.5, thick=3.0, gaps=(2, 3, 4, 9, 10), broken_ends=(0, 3), n=14)
        faces += a2
        for i, (z, hh, r) in enumerate([(-30, 9, 2.6), (-22, 5, 2.4), (44, 11, 2.7), (52, 4, 2.4), (-70, 7, 2.5)]):
            sx = (hash1(i, 95) - .5) * 14
            faces += column("ruins", "ruin", (sx, top(z) - 2.2 - abs(sx) * 0.12, z), hh, r, sides=6, taper=0.82)
        for i, (z, sx) in enumerate([(-38, 6), (14, -7), (36, 5), (60, -3)]):
            faces += slab("ruins", "ruin", (sx, top(z) - 0.4, z), (5 + 2 * hash1(i, 96), 3.2, 4.5), taper=0.9, jit=0.8, tag=140 + i,
                          rot=hash1(i, 97) * 60)
    elif lod == 1:
        faces += arch("ruins", "ruin", 22.0, 12, top(22) - 6.0, depth=7.5, thick=3.2, gaps=(5, 6), n=8)
        faces += arch("ruins", "ruin", -48.0, 10, top(-48) - 5.0, depth=6.5, thick=3.0, gaps=(3, 4, 5), broken_ends=(0, 2), n=8)
        for i, (z, hh) in enumerate([(-30, 9), (44, 11)]):
            faces += column("ruins", "ruin", (0, top(z) - 2.2, z), hh, 2.6, sides=4, taper=0.82)
    else:
        for z in (22.0, -48.0):
            faces += slab("ruins", "ruin", (0, top(z) + 2, z), (20, 8, 7), taper=1.5)
    return faces


SKIN = np.array([(72, 84, 104), (90, 102, 122), (108, 120, 138), (128, 138, 154), (152, 160, 172)])
STONE_C = np.array([(46, 42, 44), (62, 56, 56), (80, 72, 68), (100, 90, 82), (122, 110, 98), (144, 130, 114)])
BELLY_C = np.array([(112, 108, 108), (136, 132, 130), (158, 154, 150), (180, 174, 168), (204, 196, 188)])


def hook_c_paint(col, glow, e):
    """皮から岩への移り変わり: 下 (腹・脇) はなめらかな皮、上は割れた岩。境目は雲のようにゆらぐ。"""
    Y, N, mats = e["Y"], e["N"], e["mats"]
    r = np.clip((Y - 36.0) / 26.0 + 1.0 * (e["n_mid"] - 0.5) + 0.6 * (e["n_lo"] - 0.5) + 0.35 * N[..., 1], 0, 1)
    rock = r > 0.55
    hullm = mats == "stone"
    sk = e["pick"](SKIN, e["shaded"](SKIN, 0.50, 0.28, 0.08, ed=False) + (e["n_hi"] > 0.72) - (e["n_hi"] < 0.2))
    # 皮のしわ (水平の筋)
    wr = (np.abs(((Y * 0.6 + 2.0 * (e["n_lo"] - 0.5)) % 1.0) - 0.5) < 0.07) & ~rock
    sk = np.where(wr[..., None], e["pick"](SKIN, e["shaded"](SKIN, 0.50, 0.28, 0.08, ed=False) - 2), sk)
    # 皮から岩へ: 境目の帯に一段暗い石の点が混ざる
    ro = e["pick"](STONE_C, e["shaded"](STONE_C, 0.50, 0.30, 0.12) - (e["n_hi"] > 0.78))
    trans = (r > 0.42) & (r <= 0.55)
    mix = trans & (e["bay"] + 0.5 < (r - 0.42) / 0.13)
    c2 = np.where(rock[..., None] | mix[..., None], ro, sk)
    col = np.where(hullm[..., None], c2, col)
    return col, glow


CTX = dict(moss=0.18, rune_p=0.0, stone=STONE_C, plate=STONE_C, belly=BELLY_C, fin=np.array([(52, 56, 68), (66, 72, 86), (82, 88, 102), (100, 106, 120)]),
           ruin=np.array([(50, 52, 60), (68, 70, 80), (86, 88, 98), (106, 106, 112), (124, 124, 128)]),
           strata_w=0.1, strata_y=0.25, stone_base=0.5, belly_lines=0.16, eye_z=-111.0, pupil_w=0.5, ray_f=0.22, ray_w=0.07,
           lid=np.array([(54, 58, 70), (70, 74, 86), (88, 92, 104), (106, 110, 120)]), brow=np.array([(46, 50, 62), (60, 64, 78), (76, 80, 94)]),
           hook=hook_c_paint, rune_mats=["ruin"])
OPT = dict(id="c", build=build, ctx=CTX, tex=(2.0, 1.0, 0.5))
