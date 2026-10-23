"""角の荷獣 (選んだ案: C 渦角の山羊)。背の高いしなやかな山羊: 巻いた角、胸と首のたてがみ、背に荷を巻いた縄、首の鈴。
骨: body / neck / head / ear_l / ear_r / tail / leg_{fl,fr,bl,br} (+ _lo)。"""
from mlp import *  # noqa

ID = "horn_beast"
TITLE = "角の荷獣"

FUR = [(34, 34, 38), (54, 54, 60), (78, 78, 84), (106, 106, 110), (136, 134, 134), (166, 162, 156), (194, 188, 178)]
FURD = [(22, 22, 26), (36, 36, 40), (54, 52, 56), (74, 72, 74)]
CREAM = [(96, 88, 74), (134, 124, 104), (170, 160, 138), (202, 192, 170), (228, 220, 200), (244, 238, 222)]
HORN = [(60, 52, 42), (92, 82, 66), (128, 116, 94), (164, 152, 124), (196, 186, 156), (222, 214, 188)]
HOOF = [(18, 16, 16), (32, 28, 26), (50, 44, 40), (70, 62, 56)]
NOSE = [(40, 30, 30), (70, 52, 50), (102, 78, 74)]
LEATH = [(46, 28, 18), (76, 48, 28), (108, 72, 42), (142, 102, 62), (172, 132, 86)]
LEATHD = [(28, 18, 14), (48, 32, 22), (70, 48, 32), (94, 66, 44)]
CLOTH = [(70, 26, 24), (108, 40, 34), (146, 62, 48), (180, 92, 66)]
BRASS = [(78, 56, 22), (126, 94, 38), (178, 138, 62), (222, 184, 98), (248, 222, 150)]
ROPE = [(70, 58, 40), (106, 90, 62), (146, 128, 92), (184, 168, 128)]
CANVAS = [(70, 66, 50), (104, 98, 74), (138, 130, 100), (170, 162, 128), (196, 188, 154)]
EYE = [(14, 12, 12), (26, 22, 20), (40, 34, 30)]

PAL = dict(mats={
    "fur": dict(ramp=FUR, base=0.56, lit=0.32, noise=0.05, facevar=0.015, crack=0, min=1, streak=0.12, ao=0.16),
    "furd": dict(ramp=FURD, base=0.55, lit=0.30, noise=0.05, crack=0, min=0, streak=0.1, ao=0.1),
    "cream": dict(ramp=CREAM, base=0.58, lit=0.32, noise=0.06, facevar=0.03, crack=0, min=1, streak=0.16, ao=0.1),
    "horn": dict(ramp=HORN, base=0.58, lit=0.34, noise=0.04, crack=0, min=1, stripe=3, axis="v"),
    "hoof": dict(ramp=HOOF, base=0.55, lit=0.3, noise=0.04, crack=0, min=0),
    "nose": dict(ramp=NOSE, base=0.55, lit=0.3, noise=0.04, crack=0, min=0),
    "eye": dict(ramp=EYE, base=0.5, lit=0.2, noise=0.0, crack=0, min=0),
    "leath": dict(ramp=LEATH, base=0.6, lit=0.28, noise=0.05, crack=0, min=0, streak=0.06),
    "leathd": dict(ramp=LEATHD, base=0.6, lit=0.28, noise=0.04, crack=0, min=0),
    "clothr": dict(ramp=CLOTH, base=0.6, lit=0.24, noise=0.04, crack=0, min=0, stripe=4, axis="u"),
    "cloth": dict(ramp=ROPE, base=0.6, lit=0.26, noise=0.08, crack=0, min=0, stripe=2, axis="u"),
    "canvas": dict(ramp=CANVAS, base=0.6, lit=0.28, noise=0.06, crack=0, min=0, streak=0.08),
    "brass": dict(ramp=BRASS, base=0.6, lit=0.36, noise=0.03, crack=0, min=0),
}, glow_mats=[])


def build():
    m = Mount(ID, TITLE)
    m.bone("body", "root", (0, 25, 0))
    m.bone("neck", "body", (0, 28, -13))
    m.bone("head", "neck", (0, 34.4, -20.5))
    m.bone("ear_l", "head", (3.2, 37.0, -21.2))
    m.bone("ear_r", "head", (-3.2, 37.0, -21.2))
    m.bone("tail", "body", (0, 29.6, 12.4))
    quad_bones(m, {"fl": ((4.2, 21.5, -11.0), (4.4, 11.8, -11.4)), "fr": ((-4.2, 21.5, -11.0), (-4.4, 11.8, -11.4)),
                   "bl": ((4.2, 22.5, 7.6), (4.4, 11.6, 10.6)), "br": ((-4.2, 22.5, 7.6), (-4.4, 11.6, 10.6))})

    # ---------------------------------------------------------------- 胴 (深い胸、締まった腰)
    body = [rsec(-18.0, 31.4, 20.6, 6.0, 26.0, n=14), rsec(-13, 33.0, 17.2, 7.4, 25.0, n=14), rsec(-6, 32.2, 16.4, 7.8, 24.4, n=14),
            rsec(2, 31.6, 17.0, 7.6, 24.6, n=14), rsec(8, 31.4, 18.6, 7.2, 25.2, n=14), rsec(12.6, 29.4, 21.0, 5.2, 25.6, n=14)]
    m.add("torso", lof("body", "fur", body), core=True)
    # 胸のたてがみ (前へふくらむクリーム色の長い毛) と腰の毛
    m.add("mane_chest", ell("body", "cream", (0, 26.0, -15.4), (6.4, 7.6, 5.4), segs=12, rings=3))
    for i in range(9):
        a = math.radians(-60 + i * 15)
        p = np.array([math.sin(a) * 5.4, 21.0 + 0.6 * abs(math.sin(a)), -16.0 + 2.0 * abs(math.sin(a))])
        m.add(f"chest_tuft{i}", cone("body", "cream", p, (math.sin(a) * 0.3, -1.0, -0.25), 6.4 - 1.6 * abs(math.sin(a)), 1.9, sides=4, rings=2,
                                     bend=(0, 0, 1.2)))
    m.add("rump", ell("body", "cream", (0, 26.4, 10.6), (5.6, 4.4, 3.2), segs=10, rings=2))

    # ---------------------------------------------------------------- 首 (たてがみ) と頭
    m.add("neck", limb("neck", "fur", [(0, 25.0, -4.0), (0, 26.6, -10.0), (0, 28.6, -14.0), (0, 31.8, -18.0), (0, 34.6, -20.6)], [11.0, 10.4, 8.4, 6.8, 6.0],
                       [11.0, 10.6, 8.6, 7.0, 6.2], n=12, side=(1, 0, 0)))
    for i in range(7):
        t = i / 6
        p = lerp((0, 31.0, -11.8), (0, 36.2, -19.6), t)
        m.add(f"mane{i}", cone("neck", "cream", p, (0, 0.6, 1.0), 5.0 - 1.6 * t, 2.3 - 0.5 * t, sides=4, rings=2, bend=(0, -1.4, 1.4), rb=1.2))
    for i in range(5):
        t = i / 4
        p = lerp((0, 24.6, -15.6), (0, 30.2, -21.8), t)
        for s in (1, -1):
            m.add(f"throat{i}{'lr'[s < 0]}", cone("neck", "cream", p + np.array([s * 1.6, 0, 0]), (s * 0.3, -1.0, -0.6), 5.0 - 1.4 * t, 1.6, sides=3,
                                                  rings=1))
    piv = m.pv("head")
    R = rot_x(-16)
    hd = [rsec(-18.6, 38.0, 31.0, 4.2, 34.6, n=12), rsec(-21.6, 38.8, 30.6, 4.6, 34.6, n=12), rsec(-25.0, 37.6, 30.8, 3.9, 34.2, n=12),
          rsec(-28.2, 35.8, 31.0, 3.2, 33.6, n=12), rsec(-30.4, 34.6, 31.2, 2.6, 33.0, n=12)]
    m.add("skull", lof("head", "fur", xf(hd, R, piv)), core=True)
    m.add("muzzle", ell("head", "cream", xf(np.array([0, 32.6, -29.6]), R, piv), (2.6, 2.0, 2.4), segs=8, rings=2, rx=-16))
    m.add("nose", ell("head", "nose", xf(np.array([0, 33.4, -31.4]), R, piv), (1.7, 1.1, 0.8), segs=7, rings=1, rx=-16))
    m.add("brow", ell("head", "furd", xf(np.array([0, 38.2, -23.4]), R, piv), (3.8, 1.3, 3.4), segs=8, rings=2, rx=-16))
    # あごひげ
    m.add("beard", cone("head", "cream", xf(np.array([0, 31.4, -27.4]), R, piv), (0, -1, 0.35), 6.0, 1.6, sides=4, rings=2, bend=(0, 0, 0.8)))
    sk = tri_arrays(m.faces_of(["skull"]))
    for s, k in ((1, "l"), (-1, "r")):
        c0 = xf(np.array([0, 35.6, -24.2]), R, piv)
        d = nrm(np.array([s * 1.0, 0.1, -0.35]))
        c = ray_far(sk, c0, d) - d * 0.5
        m.add(f"eye_{k}", ell("head", "eye", c, (1.1, 1.2, 1.2), segs=7, rings=2))
        m.eyes.append(dict(c=c, r=1.3, dir=tuple(d), a=0.9, b=0.85, bone="head", iris=(200, 150, 50), lid=(28, 26, 26), pa=0.85, pb=0.28))
        # 渦の角: 頭の上から後ろ・下・前へ巻く
        base = xf(np.array([s * 2.2, 38.6, -21.4]), R, piv)
        m.add(f"horn_{k}", spiral("head", "horn", base, (s * 1.0, 0.15, 0.1), (0, 0.7, 1.0), 1.15, 3.2, 1.4, 12, 2.1, 0.45, rise=s * 4.6, sides=5))
        # 耳 (横へ垂れる葉の形)
        ep = m.pv("ear_" + k)
        m.add(f"ear_{k}", ell("ear_" + k, "fur", ep + np.array([s * 2.4, -0.6, 0.2]), (2.8, 0.8, 1.4), segs=7, rings=2, rz=-s * 20, ry=s * 10))
        m.add(f"earin_{k}", ell("ear_" + k, "nose", ep + np.array([s * 2.5, -0.35, -0.3]), (2.0, 0.45, 0.8), segs=6, rings=1, rz=-s * 20, ry=s * 10))
    # 首輪と鈴
    nt = tri_arrays(m.faces_of(["neck"]))
    pts = surface_arc(nt, -14.6, 28.6, 0, 360, 10, off=0.4)
    m.add("collar", tub("neck", "leathd", pts, 0.9, 0.6, sides=4, hint=(0, 0, 1)))
    low = min(pts, key=lambda p: p[1])
    m.add("bell", tub("neck", "brass", [low + np.array([0, -0.6, -0.8]), low + np.array([0, -2.6, -1.0]), low + np.array([0, -4.0, -1.0])],
                      [0.9, 1.7, 2.1], None, sides=8))
    m.add("bell_clap", ell("neck", "furd", low + np.array([0, -4.2, -1.0]), (0.8, 0.5, 0.8), segs=6, rings=1))

    # ---------------------------------------------------------------- 脚 (前は真っすぐ、後ろは膝が前・飛節が後ろ。割れた蹄)
    for k in ("fl", "fr"):
        s = 1 if k.endswith("l") else -1
        hoof_leg(m, k, None, (s * 4.4, 4.2, -11.6), (5.4, 3.2, 2.4), (6.4, 3.6, 2.6), mat="fur", lo_mat="fur", hoof=(3.2, 2.4, 3.6), cloven=True,
                 feather=("cream", 2.4))
    for k in ("bl", "br"):
        s = 1 if k.endswith("l") else -1
        hoof_leg(m, k, (s * 4.6, 16.4, 6.2), (s * 4.4, 4.2, 9.8), (5.8, 3.2, 2.4), (8.2, 3.8, 2.6), mat="fur", lo_mat="fur", hoof=(3.2, 2.4, 3.6),
                 cloven=True, feather=("cream", 2.4))
    # 尾 (上へ跳ねた短い房)
    tp = m.pv("tail")
    m.add("tail", cone("tail", "cream", tp + np.array([0, -1.2, -1.0]), (0, 0.8, 1.0), 6.0, 2.2, sides=5, rings=2, bend=(0, 1.2, 0)))

    # ---------------------------------------------------------------- 荷: 鞍の後ろの巻いた荷と、両脇の荷袋 (縄で縛る)
    tt = tri_arrays(m.faces_of(["torso"]))
    for s, k in ((1, "l"), (-1, "r")):
        p = ray_far(tt, (0, 25.0, 7.0), (s * 1.0, 0.25, 0))
        c = p + np.array([s * 2.0, -1.4, 0])
        m.add(f"bag_{k}", cbox("body", "canvas", c, (3.6, 7.0, 6.4), R=rot_z(s * 8), taper=0.86, cut=0.35))
        m.add(f"bag_flap_{k}", box("body", "leath", c + np.array([s * 0.3, 2.6, 0]), (3.9, 2.0, 6.8), R=rot_z(s * 8), cut=0.3))
        for j, dz in enumerate((-2.0, 2.0)):
            m.add(f"bag_rope_{k}{j}", tub("body", "cloth", [c + np.array([-s * 1.9, 3.8, dz]), c + np.array([s * 2.0, 3.6, dz]),
                                                            c + np.array([s * 2.0, -3.6, dz]), c + np.array([-s * 1.6, -3.4, dz])], 0.45, None, sides=4,
                                          hint=(0, 0, 1)))
    m.saddle_kw = dict(cy=25.0, length=7.4, half_angle=60, seat_len=5.6, seat_half=34, girth_z=-1.0, girth_cy=24.6, blanket=0.8,
                       mats=dict(pad="clothr", trim="brass", seat="leath", strap="leathd", metal="brass", roll="canvas"),
                       rear_roll=(8.0, 2.2, 7.6))
    m.prefer_pose = (-30.0, 22.0)
    m.pal = PAL
    m.detail = detail
    m.view = dict(center=(0, 24, -3), scale=4.6)
    m.gaits = gaits(m)
    m.hint = 256
    return m


def detail(c, col, glow):
    P, N, bones = c["P"], c["N"], c["bones"]
    col = fur(c, col, ["fur", "furd"], scale=(1.1, 0.24, 1.1), amt=0.1)
    col = fur(c, col, ["cream"], scale=(1.3, 0.3, 1.3), amt=0.12, seed=8)
    col = countershade(c, col, ["fur"], (150, 146, 140), top_k=0.92, belly_from=-0.3)
    # 顔の暗い仮面 (目から鼻へ) と、脚の下の濃い靴下
    sel = (bones == "head") & (c["mats"] == "fur") & c["owned"]
    col = shade(col, sel & (np.abs(P[..., 0]) < 2.2) & (N[..., 1] > -0.2), 0.72)
    sel = np.isin(bones, ["leg_fl_lo", "leg_fr_lo", "leg_bl_lo", "leg_br_lo"]) & (c["mats"] == "fur") & c["owned"] & (P[..., 1] < 8)
    col = shade(col, sel, 0.62)
    # 背の黒い筋 (背骨に沿って)
    sel = (bones == "body") & (c["mats"] == "fur") & c["owned"] & (np.abs(P[..., 0]) < 1.1) & (N[..., 1] > 0.6)
    col = shade(col, sel, 0.7)
    # 角の年輪 (角の長さ方向の輪)
    sel = (c["mats"] == "horn") & c["owned"]
    col = shade(col, sel & (c["LV"] % 3 == 0), 0.82)
    # 蹄の割れ目・荷袋の継ぎ目・革の縁
    col = edge_dark(c, col, ["leath", "leathd", "clothr", "canvas"], 0.8)
    return col, glow


def gaits(m):
    ears = ("ear_l", "ear_r")
    idle, walk, run = quad_gaits(walk=(1.5, 22, 32, 20, 30), run=(3.2, 36, 56, 32, 44), ears=ears, tails=("tail",))
    idle.rot("tail", x=W(0).s(6.0, 3, 0, "t"), y=W(0).s(10.0, 5, 0, "t"))
    jump = quad_jump()
    return [idle, walk, run, jump]
