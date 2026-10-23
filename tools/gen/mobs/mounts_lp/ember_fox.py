"""炉の狐 (選んだ案: C 三尾の熾狐)。余燼の巨神の炉の狐: 細く素早い体、三本の尾が扇に開き、首から背へ灰の房。尾の先と背の割れ目が熾 (光る)。
骨: body / neck / head / ear_l / ear_r / tail_c / tail_c2 / tail_l / tail_l2 / tail_r / tail_r2 / leg_{fl,fr,bl,br} (+ _lo)。"""
from mlp import *  # noqa

ID = "ember_fox"
TITLE = "炉の狐"

FOX = [(70, 22, 10), (110, 38, 14), (150, 58, 20), (190, 84, 30), (222, 116, 46), (242, 152, 72), (252, 190, 110)]
CREAM = [(120, 108, 92), (164, 150, 130), (204, 192, 170), (232, 224, 206), (248, 244, 232)]
CINDER = [(10, 8, 10), (20, 16, 18), (34, 28, 28), (52, 44, 42), (74, 64, 60)]
EMBER = [(150, 46, 16), (210, 84, 24), (250, 140, 40), (255, 196, 90), (255, 236, 170)]
LEATH = [(46, 28, 18), (76, 48, 28), (108, 72, 42), (142, 102, 62), (172, 132, 86)]
LEATHD = [(28, 18, 14), (48, 32, 22), (70, 48, 32), (94, 66, 44)]
CLOTH = [(30, 30, 34), (46, 46, 52), (66, 66, 74), (92, 92, 100)]
BRASS = [(78, 56, 22), (126, 94, 38), (178, 138, 62), (222, 184, 98), (248, 222, 150)]
EYE = [(10, 8, 8), (20, 16, 16), (34, 28, 26)]

PAL = dict(mats={
    "fox": dict(ramp=FOX, base=0.56, lit=0.32, noise=0.05, facevar=0.015, crack=0, min=1, streak=0.1, ao=0.14),
    "cream": dict(ramp=CREAM, base=0.58, lit=0.3, noise=0.05, facevar=0.02, crack=0, min=1, streak=0.1, ao=0.08),
    "cinder": dict(ramp=CINDER, base=0.56, lit=0.3, noise=0.05, crack=0, min=0, streak=0.08, ao=0.08),
    "ember": dict(ramp=EMBER, base=0.62, lit=0.24, noise=0.04, crack=0, min=1),
    "eye": dict(ramp=EYE, base=0.5, lit=0.2, noise=0.0, crack=0, min=0),
    "leath": dict(ramp=LEATH, base=0.6, lit=0.28, noise=0.05, crack=0, min=0, streak=0.06),
    "leathd": dict(ramp=LEATHD, base=0.6, lit=0.28, noise=0.04, crack=0, min=0),
    "clothr": dict(ramp=CLOTH, base=0.6, lit=0.24, noise=0.04, crack=0, min=0, stripe=4, axis="u"),
    "brass": dict(ramp=BRASS, base=0.6, lit=0.36, noise=0.03, crack=0, min=0),
}, glow_mats=["ember"])

TAILS = {
    "c": ([(0, 23.0, 10.6), (0, 26.6, 16.6), (0, 32.0, 21.0)], [(0, 32.0, 21.0), (0, 37.6, 23.0), (0, 42.2, 22.6)]),
    "l": ([(1.2, 23.0, 10.6), (5.0, 24.8, 16.4), (9.4, 28.2, 20.6)], [(9.4, 28.2, 20.6), (13.0, 32.6, 22.8), (15.2, 36.6, 22.8)]),
}
TAILS["r"] = tuple([tuple((-x, y, z) for x, y, z in seg) for seg in TAILS["l"]])
TIPS = [TAILS[k][1][-1] for k in "clr"]


def build():
    m = Mount(ID, TITLE)
    m.bone("body", "root", (0, 21, 0))
    m.bone("neck", "body", (0, 24.0, -12.0))
    m.bone("head", "neck", (0, 29.6, -17.4))
    m.bone("ear_l", "head", (2.4, 33.4, -17.6))
    m.bone("ear_r", "head", (-2.4, 33.4, -17.6))
    for k in "clr":
        m.bone("tail_" + k, "body", TAILS[k][0][0])
        m.bone("tail_" + k + "2", "tail_" + k, TAILS[k][1][0])
    quad_bones(m, {"fl": ((3.2, 18.8, -11.4), (3.3, 10.2, -11.8)), "fr": ((-3.2, 18.8, -11.4), (-3.3, 10.2, -11.8)),
                   "bl": ((3.2, 19.4, 7.4), (3.3, 10.0, 10.0)), "br": ((-3.2, 19.4, 7.4), (-3.3, 10.0, 10.0))})

    # ---------------------------------------------------------------- 胴 (細く、胸は白い)
    body = [rsec(-14.6, 24.4, 16.4, 4.2, 20.4, n=14), rsec(-10, 26.6, 14.8, 5.8, 20.6, n=14), rsec(-3, 27.0, 15.0, 6.0, 20.8, n=14),
            rsec(4, 26.6, 15.6, 5.8, 21.0, n=14), rsec(9, 26.4, 16.6, 5.6, 21.4, n=14), rsec(12.6, 24.8, 18.2, 4.2, 21.6, n=14)]
    m.add("torso", lof("body", "fox", body), core=True)
    m.add("chest", ell("body", "cream", (0, 19.6, -12.4), (4.0, 4.6, 3.0), segs=10, rings=3))
    for i in range(5):
        a = math.radians(-40 + i * 20)
        m.add(f"chest_tuft{i}", cone("body", "cream", (math.sin(a) * 3.0, 16.2, -13.0 + abs(math.sin(a))), (math.sin(a) * 0.3, -1, -0.3), 3.6, 1.5,
                                     sides=3, rings=1))
    # ---------------------------------------------------------------- 首 (灰の房) と頭
    m.add("neck", limb("neck", "fox", [(0, 22.0, -9.0), (0, 24.4, -12.6), (0, 27.6, -15.6), (0, 29.6, -17.4)], [8.0, 6.8, 5.6, 5.0],
                       [8.0, 6.8, 5.8, 5.2], n=12))
    m.add("throat", ell("neck", "cream", (0, 24.0, -16.0), (2.8, 3.4, 2.4), segs=8, rings=2, rx=-30))
    for i in range(5):
        t = i / 4
        p = lerp((0, 27.4, -10.6), (0, 32.4, -16.4), t)
        m.add(f"mane{i}", cone("neck", "cinder", p, (0, 0.55, 1.0), 6.4 - 2.0 * t, 2.0 - 0.4 * t, sides=4, rings=2, bend=(0, -1.0, 1.2), rb=1.1))
    piv = m.pv("head")
    R = rot_x(-6)
    hd = [rsec(-15.8, 33.4, 27.0, 3.7, 30.2, n=12), rsec(-18.8, 34.0, 27.2, 4.1, 30.4, n=12), rsec(-21.6, 32.8, 27.8, 3.2, 30.2, n=12),
          rsec(-24.2, 31.2, 28.2, 2.0, 29.8, n=12), rsec(-26.6, 30.4, 28.6, 1.2, 29.6, n=12)]
    m.add("skull", lof("head", "fox", xf(hd, R, piv)), core=True)
    m.add("snout", ell("head", "cream", xf(np.array([0, 28.6, -22.6]), R, piv), (2.4, 1.4, 3.6), segs=8, rings=2, rx=-6))
    m.add("nose", ell("head", "cinder", xf(np.array([0, 30.2, -26.6]), R, piv), (1.0, 0.8, 0.8), segs=6, rings=1))
    sk = tri_arrays(m.faces_of(["skull"]))
    for s, k in ((1, "l"), (-1, "r")):
        c0 = xf(np.array([0, 31.6, -20.4]), R, piv)
        d = nrm(np.array([s * 0.9, 0.15, -0.5]))
        c = ray_far(sk, c0, d) - d * 0.45
        m.add(f"eye_{k}", ell("head", "eye", c, (1.0, 0.9, 1.0), segs=7, rings=2))
        m.eyes.append(dict(c=c, r=1.15, dir=tuple(d), a=0.95, b=0.72, bone="head", iris=(255, 176, 60), lid=(16, 12, 12), pa=0.25, pb=0.8,
                           glow=True))
        # 頬の灰の房 (横・後ろへ)
        cp = ray_far(sk, xf(np.array([0, 29.0, -18.6]), R, piv), (s * 1.0, -0.2, 0.1))
        m.add(f"cheek_{k}", cone("head", "cinder", cp - np.array([s * 0.6, 0, 0]), (s * 1.0, -0.25, 0.5), 4.6, 1.5, sides=4, rings=2))
        # 大きな三角の耳 (内側は熾)
        ep = m.pv("ear_" + k)
        m.add(f"ear_{k}", tub("ear_" + k, "fox", [ep + np.array([0, -0.8, 0.2]), ep + np.array([s * 0.9, 3.4, 0.3]), ep + np.array([s * 1.8, 6.6, 0.8])],
                              [2.0, 1.4, 0.15], [1.0, 0.75, 0.1], sides=4, hint=(0, 0, 1)))
        m.add(f"earin_{k}", tub("ear_" + k, "ember", [ep + np.array([s * 0.1, 0.2, -0.5]), ep + np.array([s * 0.9, 3.2, -0.4]),
                                                     ep + np.array([s * 1.5, 5.4, 0.0])], [1.2, 0.8, 0.1], [0.35, 0.3, 0.1], sides=4, hint=(0, 0, 1)))
        m.add(f"eartip_{k}", ell("ear_" + k, "cinder", ep + np.array([s * 1.7, 6.2, 0.8]), (0.5, 0.9, 0.4), segs=5, rings=1))

    # ---------------------------------------------------------------- 三本の尾 (ふくらんだ毛、先は熾)
    for k in "clr":
        a, b = TAILS[k]
        m.add(f"tail_{k}", limb("tail_" + k, "fox", [np.array(a[0]) - (np.array(a[1]) - np.array(a[0])) * 0.25] + list(a), [2.6, 3.0, 4.6, 5.2],
                                [2.6, 3.0, 4.4, 5.0], n=10, side=(1, 0, 0)))
        m.add(f"tail_{k}2", limb("tail_" + k + "2", "fox", [np.array(b[0]) - (np.array(b[1]) - np.array(b[0])) * 0.2] + list(b),
                                 [5.0, 5.2, 4.6, 1.2], [4.8, 5.0, 4.4, 1.2], n=10, side=(1, 0, 0)))
        tip = np.array(b[-1])
        m.add(f"tail_{k}_flame", cone("tail_" + k + "2", "ember", tip - (tip - np.array(b[1])) * 0.3, tip - np.array(b[1]), 4.4, 1.7, sides=5, rings=2,
                                      bend=(0, 0.8, -0.6)))

    # ---------------------------------------------------------------- 脚 (黒い靴下、肉球の足と爪)
    for k in ("fl", "fr"):
        s = 1 if k.endswith("l") else -1
        hoof_leg(m, k, None, (s * 3.3, 4.0, -12.2), (4.4, 2.6, 2.2), (5.2, 3.0, 2.4), mat="fox", lo_mat="cinder", hoof_mat="cinder",
                 hoof=(3.0, 2.0, 3.8), foot="paw")
    for k in ("bl", "br"):
        s = 1 if k.endswith("l") else -1
        hoof_leg(m, k, (s * 3.5, 14.6, 5.8), (s * 3.3, 4.0, 9.2), (5.0, 2.6, 2.2), (7.6, 3.2, 2.4), mat="fox", lo_mat="cinder", hoof_mat="cinder",
                 hoof=(3.0, 2.0, 3.8), foot="paw")

    m.saddle_kw = dict(cy=21.0, length=6.6, half_angle=60, seat_len=5.2, seat_half=34, girth_z=-1.4, girth_cy=20.8, blanket=0.8,
                       mats=dict(pad="clothr", trim="brass", seat="leath", strap="leathd", metal="brass"))
    m.prefer_pose = (-30.0, 24.0)
    m.pal = PAL
    m.detail = detail
    m.view = dict(center=(0, 24, 0), scale=4.2)
    m.gaits = gaits(m)
    m.hint = 256
    return m


def detail(c, col, glow):
    P, N, bones = c["P"], c["N"], c["bones"]
    col = fur(c, col, ["fox"], scale=(1.1, 0.26, 1.1), amt=0.1)
    col = fur(c, col, ["cream", "cinder"], scale=(1.3, 0.3, 1.3), amt=0.1, seed=8)
    col = countershade(c, col, ["fox"], (232, 214, 186), top_k=0.9, belly_from=-0.4)
    # 脚の上の方も黒く (靴下が上へ溶ける)
    sel = np.isin(bones, ["leg_fl", "leg_fr", "leg_bl", "leg_br"]) & (c["mats"] == "fox") & c["owned"]
    col = shade(col, sel & (P[..., 1] < 15), 0.55)
    # 背の熾の割れ目 (光る): 背骨のまわりの細胞の縁
    sel = (bones == "body") & (c["mats"] == "fox") & c["owned"] & (N[..., 1] > 0.35) & (P[..., 2] > 4.5)
    f1, f2, idv = cell3(P, 2.6, 31)
    crack = sel & ((f2 - f1) < 0.09) & (idv > 0.25)
    col = np.where(crack[..., None], ramp_pick(EMBER, np.clip(0.55 + 0.3 * c["lit"], 0, 0.99)), col)
    glow = glow | crack
    # 尾の先へ: 毛が熾の色へ変わる (先の 7 単位)
    col, glow = gradient_to(c, col, ["fox"], TIPS, 9.0, EMBER, glow=glow, glow_thr=0.6, power=1.6)
    # 尾の白い帯 (熾の手前)
    col = edge_dark(c, col, ["leath", "leathd", "clothr"], 0.8)
    return col, glow


def gaits(m):
    ears = ("ear_l", "ear_r")
    idle, walk, run = quad_gaits(walk=(1.5, 24, 34, 22, 34), run=(3.4, 44, 66, 40, 56), run_ph=GALLOP_PH, ears=ears, tails=(),
                                 bob=(0.5, 1.6), pitch=(1.2, 5.0))
    # 三本の尾: ずれた位相でゆらゆら (扇が波打つ)
    for i, k in enumerate("clr"):
        ph = i * 120
        side = {"c": 0, "l": 1, "r": -1}[k]
        idle.rot("tail_" + k, x=W(0).s(4.0, 1, ph, "t"), z=W(side * 3).s(5.0, 1, ph + 60, "t"), y=W(0).s(6.0, 1, ph + 30, "t"))
        idle.rot("tail_" + k + "2", x=W(0).s(6.0, 1, ph - 50, "t"), z=W(0).s(7.0, 1, ph + 10, "t"))
        walk.rot("tail_" + k, x=W(0).s(5.0, 2, ph), y=W(0).s(8.0, 1, ph))
        walk.rot("tail_" + k + "2", x=W(0).s(7.0, 2, ph - 40), z=W(0).s(6.0, 1, ph))
        run.rot("tail_" + k, x=W(-22).s(8.0, 1, 100 + ph * 0.3), z=W(side * 8))
        run.rot("tail_" + k + "2", x=W(-16).s(10.0, 1, 60 + ph * 0.3), z=W(side * 6).s(5.0, 2, ph))
    idle.rot("ear_l", z=W(0).s(6.0, 1, 0, "t").s(4.0, 7, 0, "t"))
    jump = quad_jump(tails=(), extra={"tail_c": {"rot": {0.0: (0, 0, 0), 0.3: (-26, 0, 0), 0.7: (6, 0, 0)}},
                                      "tail_l": {"rot": {0.0: (0, 0, 0), 0.3: (-22, 0, 12), 0.7: (6, 0, 4)}},
                                      "tail_r": {"rot": {0.0: (0, 0, 0), 0.3: (-22, 0, -12), 0.7: (6, 0, -4)}}})
    return [idle, walk, run, jump]
