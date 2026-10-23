"""灰色の鹿 (選んだ案: A 枝角の牡鹿)。灰の降る世界の鹿: 高く枝分かれした白い角、胸の白い毛、すらりとした王道の鹿。背に灰 (煤) の斑。
骨: body / neck / head / ear_l / ear_r / tail / leg_{fl,fr,bl,br} (+ _lo)。"""
from mlp import *  # noqa

ID = "ash_deer"
TITLE = "灰色の鹿"

ASH = [(36, 36, 40), (56, 56, 62), (80, 80, 86), (108, 106, 110), (138, 134, 134), (168, 162, 158), (196, 190, 182)]
ASHD = [(20, 20, 24), (34, 34, 38), (50, 48, 52), (70, 66, 68)]
CREAM = [(110, 104, 94), (150, 144, 130), (188, 182, 166), (218, 212, 196), (240, 236, 222)]
ANTLER = [(84, 74, 58), (124, 112, 90), (162, 150, 124), (196, 186, 160), (226, 218, 196), (246, 242, 228)]
HOOF = [(16, 14, 14), (30, 26, 24), (46, 40, 36), (64, 56, 50)]
CINDER = [(12, 10, 12), (24, 20, 22), (40, 34, 34), (60, 52, 50)]
LEATH = [(46, 28, 18), (76, 48, 28), (108, 72, 42), (142, 102, 62), (172, 132, 86)]
LEATHD = [(28, 18, 14), (48, 32, 22), (70, 48, 32), (94, 66, 44)]
CLOTH = [(34, 46, 40), (50, 70, 60), (72, 98, 82), (102, 128, 104)]
BRASS = [(78, 56, 22), (126, 94, 38), (178, 138, 62), (222, 184, 98), (248, 222, 150)]
EYE = [(10, 8, 8), (20, 16, 16), (34, 28, 26)]

PAL = dict(mats={
    "fur": dict(ramp=ASH, base=0.56, lit=0.32, noise=0.05, facevar=0.015, crack=0, min=1, streak=0.1, ao=0.16),
    "furd": dict(ramp=ASHD, base=0.55, lit=0.30, noise=0.05, crack=0, min=0, streak=0.08, ao=0.1),
    "cream": dict(ramp=CREAM, base=0.58, lit=0.32, noise=0.05, facevar=0.03, crack=0, min=1, streak=0.14, ao=0.08),
    "antler": dict(ramp=ANTLER, base=0.6, lit=0.34, noise=0.05, crack=0, min=1),
    "hoof": dict(ramp=HOOF, base=0.55, lit=0.3, noise=0.04, crack=0, min=0),
    "cinder": dict(ramp=CINDER, base=0.55, lit=0.3, noise=0.05, crack=0, min=0),
    "eye": dict(ramp=EYE, base=0.5, lit=0.2, noise=0.0, crack=0, min=0),
    "leath": dict(ramp=LEATH, base=0.6, lit=0.28, noise=0.05, crack=0, min=0, streak=0.06),
    "leathd": dict(ramp=LEATHD, base=0.6, lit=0.28, noise=0.04, crack=0, min=0),
    "clothr": dict(ramp=CLOTH, base=0.6, lit=0.24, noise=0.04, crack=0, min=0, stripe=4, axis="u"),
    "brass": dict(ramp=BRASS, base=0.6, lit=0.36, noise=0.03, crack=0, min=0),
}, glow_mats=[])


def build():
    m = Mount(ID, TITLE)
    m.bone("body", "root", (0, 22, 0))
    m.bone("neck", "body", (0, 25, -11))
    m.bone("head", "neck", (0, 35.0, -18.8))
    for s_, k in ((1, "l"), (-1, "r")):
        m.bone("ear_" + k, "head", xf(np.array([s_ * 2.4, 38.2, -19.4]), rot_x(-24), np.array([0, 35.0, -18.8])))
    m.bone("tail", "body", (0, 26.2, 12.4))
    quad_bones(m, {"fl": ((3.6, 19.6, -9.6), (3.8, 11.4, -10.0)), "fr": ((-3.6, 19.6, -9.6), (-3.8, 11.4, -10.0)),
                   "bl": ((3.6, 20.6, 7.8), (3.8, 11.2, 10.6)), "br": ((-3.6, 20.6, 7.8), (-3.8, 11.2, 10.6))})

    # ---------------------------------------------------------------- 胴 (すらりと、胸が深い)
    body = [rsec(-14.4, 26.8, 18.4, 4.4, 22.6, n=14), rsec(-10, 28.6, 15.8, 5.9, 22.0, n=14), rsec(-3, 28.4, 15.4, 6.4, 21.8, n=14),
            rsec(4, 28.0, 16.0, 6.2, 22.2, n=14), rsec(9.4, 28.0, 17.6, 5.9, 22.8, n=14), rsec(13.2, 26.0, 19.8, 4.0, 23.0, n=14)]
    m.add("torso", lof("body", "fur", body), core=True)
    m.add("ruff", ell("body", "cream", (0, 23.4, -13.2), (4.6, 5.4, 3.4), segs=10, rings=3))
    for i in range(7):
        a = math.radians(-54 + i * 18)
        p = np.array([math.sin(a) * 4.0, 19.2 + 0.4 * abs(math.sin(a)), -14.0 + 1.6 * abs(math.sin(a))])
        m.add(f"ruff_tuft{i}", cone("body", "cream", p, (math.sin(a) * 0.3, -1.0, -0.3), 4.2 - abs(math.sin(a)), 1.6, sides=3, rings=1))
    m.add("rump", ell("body", "cream", (0, 23.4, 12.0), (4.2, 3.8, 2.4), segs=10, rings=2))

    # ---------------------------------------------------------------- 首と頭
    m.add("neck", limb("neck", "fur", [(0, 22.6, -8.0), (0, 25.6, -12.0), (0, 30.4, -16.2), (0, 35.0, -18.8)], [8.6, 7.0, 5.4, 4.6],
                       [9.2, 7.4, 5.8, 5.0], n=12))
    m.add("throat", ell("neck", "cream", (0, 27.0, -16.2), (2.6, 3.6, 2.0), segs=8, rings=2, rx=-30))
    piv = m.pv("head")
    R = rot_x(-24)
    hd = [rsec(-17.2, 38.6, 32.4, 3.2, 35.4, n=12), rsec(-20.0, 39.2, 32.2, 3.6, 35.6, n=12), rsec(-23.2, 38.0, 32.6, 3.0, 35.4, n=12),
          rsec(-26.2, 36.6, 33.0, 2.3, 34.8, n=12), rsec(-28.2, 35.8, 33.2, 1.8, 34.4, n=12)]
    m.add("skull", lof("head", "fur", xf(hd, R, piv)), core=True)
    m.add("muzzle", ell("head", "cream", xf(np.array([0, 33.6, -26.2]), R, piv), (2.0, 1.6, 2.4), segs=8, rings=2, rx=-24))
    m.add("nose", ell("head", "cinder", xf(np.array([0, 34.6, -28.4]), R, piv), (1.3, 1.0, 0.8), segs=7, rings=1, rx=-24))
    sk = tri_arrays(m.faces_of(["skull"]))
    for s, k in ((1, "l"), (-1, "r")):
        c0 = xf(np.array([0, 36.6, -21.6]), R, piv)
        d = nrm(np.array([s * 1.0, 0.1, -0.3]))
        c = ray_far(sk, c0, d) - d * 0.45
        m.add(f"eye_{k}", ell("head", "eye", c, (1.0, 1.1, 1.1), segs=7, rings=2))
        m.eyes.append(dict(c=c, r=1.2, dir=tuple(d), a=0.85, b=0.85, bone="head", iris=(40, 28, 20), lid=(20, 18, 18), pa=0.6, pb=0.6))
        # 耳 (大きく横へ)
        ep = m.pv("ear_" + k)
        m.add(f"ear_{k}", ell("ear_" + k, "fur", ep + np.array([s * 2.6, 0.8, 0.4]), (3.0, 1.3, 0.7), segs=7, rings=2, rz=-s * 28, ry=-s * 12))
        m.add(f"earin_{k}", ell("ear_" + k, "cream", ep + np.array([s * 2.7, 0.7, -0.15]), (2.2, 0.8, 0.4), segs=6, rings=1, rz=-s * 28, ry=-s * 12))
        # 枝角: 主の幹が上・後ろ・外へ、眉の枝が前へ、上に二本の枝
        b0 = xf(np.array([s * 1.6, 39.6, -19.8]), R, piv)
        beam = [b0, b0 + np.array([s * 2.6, 3.6, 1.8]), b0 + np.array([s * 6.2, 7.4, 2.6]), b0 + np.array([s * 8.8, 11.8, 1.0]),
                b0 + np.array([s * 9.8, 16.0, -2.4])]
        m.add(f"beam_{k}", tub("head", "antler", beam, [1.35, 1.1, 0.95, 0.75, 0.25], None, sides=5, hint=(1, 0, 0)))
        m.add(f"burr_{k}", ell("head", "antler", b0 + np.array([0, 0.4, 0]), (1.7, 0.8, 1.7), segs=6, rings=1))
        tines = [(beam[1], (s * 0.2, 0.3, -1.0), 5.6, 0.8), (beam[2], (s * 0.05, 1.0, -0.7), 6.0, 0.75), (beam[3], (s * 0.3, 1.0, 0.5), 4.6, 0.62),
                 (beam[3], (-s * 0.25, 1.0, -0.5), 4.4, 0.55)]
        for j, (p, dd, ln, r) in enumerate(tines):
            m.add(f"tine_{k}{j}", cone("head", "antler", p, dd, ln, r, sides=4, rings=2, bend=(0, 0.8, 0)))

    # ---------------------------------------------------------------- 脚 (細く長い。後ろ脚は膝が前・飛節が後ろ)
    for k in ("fl", "fr"):
        s = 1 if k.endswith("l") else -1
        hoof_leg(m, k, None, (s * 3.8, 4.0, -10.4), (4.6, 2.6, 2.0), (5.6, 3.0, 2.2), mat="fur", lo_mat="fur", hoof=(2.6, 2.2, 3.0), cloven=True)
    for k in ("bl", "br"):
        s = 1 if k.endswith("l") else -1
        hoof_leg(m, k, (s * 4.0, 15.6, 6.0), (s * 3.8, 4.0, 9.8), (5.2, 2.6, 2.0), (7.8, 3.2, 2.2), mat="fur", lo_mat="fur", hoof=(2.6, 2.2, 3.0),
                 cloven=True)
    # 白い尾 (旗のように上がる)
    tp = m.pv("tail")
    m.add("tail", ell("tail", "cream", tp + np.array([0, -1.4, 1.4]), (2.0, 3.0, 1.6), segs=8, rings=2, rx=30))
    m.add("tail_top", ell("tail", "fur", tp + np.array([0, -0.6, 2.2]), (1.5, 2.4, 0.9), segs=7, rings=2, rx=30))

    m.saddle_kw = dict(cy=22.0, length=7.0, half_angle=60, seat_len=5.4, seat_half=34, girth_z=-1.6, girth_cy=21.8, blanket=0.8,
                       mats=dict(pad="clothr", trim="brass", seat="leath", strap="leathd", metal="brass"))
    m.prefer_pose = (-30.0, 22.0)
    m.pal = PAL
    m.detail = detail
    m.view = dict(center=(0, 26, -3), scale=4.4)
    m.gaits = gaits(m)
    m.hint = 256
    return m


def detail(c, col, glow):
    P, N, bones = c["P"], c["N"], c["bones"]
    col = fur(c, col, ["fur", "furd"], scale=(1.2, 0.26, 1.2), amt=0.09)
    col = fur(c, col, ["cream"], scale=(1.3, 0.3, 1.3), amt=0.1, seed=8)
    col = countershade(c, col, ["fur"], (204, 198, 186), top_k=0.9, belly_from=-0.35)
    # 背の煤の斑 (灰の降る世界)、細かな灰の粒
    sel = (bones == "body") & (c["mats"] == "fur") & c["owned"] & (N[..., 1] > -0.1)
    n = 0.7 * vnoise(P * 0.34 + 3, 2) + 0.3 * vnoise(P * 0.9, 5)
    col = tint(col, sel & (n > 0.66), (40, 38, 40), 0.75)
    col = speckle(c, col, ["fur"], (210, 206, 198), density=0.02, seed=6, size=1.0)
    # 鼻すじの暗い線、脚の下の暗さ、目のまわりの白い輪
    sel = (bones == "head") & (c["mats"] == "fur") & c["owned"]
    col = shade(col, sel & (np.abs(P[..., 0]) < 1.4) & (N[..., 1] > 0.0), 0.72)
    sel = np.isin(bones, ["leg_fl_lo", "leg_fr_lo", "leg_bl_lo", "leg_br_lo"]) & (c["mats"] == "fur") & c["owned"] & (P[..., 1] < 9)
    col = shade(col, sel, 0.7)
    # 角の先を白く、根元をざらつかせる
    sel = (c["mats"] == "antler") & c["owned"]
    col = tint(col, sel & (P[..., 1] > 52), (240, 236, 224), 0.6)
    col = shade(col, sel & (P[..., 1] < 43) & (c["bay"] > 0.2), 0.86)
    col = edge_dark(c, col, ["leath", "leathd", "clothr"], 0.8)
    return col, glow


def gaits(m):
    ears = ("ear_l", "ear_r")
    idle, walk, run = quad_gaits(walk=(1.7, 22, 34, 20, 32), run=(3.8, 42, 64, 38, 52), run_ph=BOUND_PH, ears=ears, tails=("tail",),
                                 bob=(0.6, 1.8), pitch=(1.4, 6.0))
    idle.rot("tail", x=W(-6).s(10.0, 3, 0, "t"))
    jump = quad_jump()
    return [idle, walk, run, jump]
