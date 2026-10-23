"""殻の亀 (選んだ案: A 珊瑚の丸甲亀)。二章の潮の殻の小さな親戚: 高く丸い甲羅、脇に珊瑚の茂み、四本の太い柱の脚。鞍は甲羅の頂。
骨: body (甲羅) / neck / head / tail / leg_{fl,fr,bl,br} (+ _lo)。"""
from mlp import *  # noqa

ID = "shell_tortoise"
TITLE = "殻の亀"

SHELL = [(22, 36, 40), (34, 54, 58), (48, 74, 76), (66, 96, 94), (88, 120, 112), (116, 146, 132), (150, 172, 154)]
RIM = [(70, 74, 62), (104, 108, 90), (138, 142, 118), (170, 172, 146), (200, 200, 176)]
SKIN = [(42, 56, 48), (60, 78, 64), (82, 104, 82), (108, 130, 102), (138, 158, 126), (170, 186, 152)]
PLAST = [(110, 104, 78), (146, 140, 108), (180, 174, 138), (208, 202, 166)]
BEAK = [(46, 40, 30), (76, 66, 48), (110, 98, 72), (144, 132, 100)]
CORAL = [(120, 34, 52), (170, 58, 74), (210, 92, 102), (238, 134, 132), (252, 182, 170)]
CORALB = [(28, 88, 98), (40, 124, 128), (70, 162, 158), (118, 200, 184), (170, 226, 208)]
BARN = [(96, 90, 76), (140, 134, 114), (184, 178, 156), (214, 208, 186), (238, 234, 216)]
LEATH = [(46, 28, 18), (76, 48, 28), (108, 72, 42), (142, 102, 62), (172, 132, 86)]
LEATHD = [(28, 18, 14), (48, 32, 22), (70, 48, 32), (94, 66, 44)]
CLOTH = [(40, 34, 70), (58, 52, 104), (84, 78, 140), (118, 112, 170)]
BRASS = [(78, 56, 22), (126, 94, 38), (178, 138, 62), (222, 184, 98), (248, 222, 150)]
EYE = [(10, 8, 8), (20, 16, 16), (34, 28, 26)]

PAL = dict(mats={
    "shell": dict(ramp=SHELL, base=0.56, lit=0.32, noise=0.05, facevar=0.02, crack=0.04, min=1, ao=0.1),
    "rim": dict(ramp=RIM, base=0.56, lit=0.3, noise=0.05, crack=0.04, min=1),
    "skin": dict(ramp=SKIN, base=0.55, lit=0.3, noise=0.05, facevar=0.02, crack=0, min=1, ao=0.16),
    "plast": dict(ramp=PLAST, base=0.56, lit=0.28, noise=0.04, crack=0, min=0),
    "beak": dict(ramp=BEAK, base=0.58, lit=0.32, noise=0.04, crack=0, min=0),
    "coral": dict(ramp=CORAL, base=0.6, lit=0.34, noise=0.06, facevar=0.05, crack=0, min=1),
    "coralb": dict(ramp=CORALB, base=0.6, lit=0.34, noise=0.06, facevar=0.05, crack=0, min=1),
    "barn": dict(ramp=BARN, base=0.6, lit=0.3, noise=0.08, crack=0, min=1),
    "eye": dict(ramp=EYE, base=0.5, lit=0.2, noise=0.0, crack=0, min=0),
    "leath": dict(ramp=LEATH, base=0.6, lit=0.28, noise=0.05, crack=0, min=0, streak=0.06),
    "leathd": dict(ramp=LEATHD, base=0.6, lit=0.28, noise=0.04, crack=0, min=0),
    "clothr": dict(ramp=CLOTH, base=0.6, lit=0.24, noise=0.04, crack=0, min=0, stripe=4, axis="u"),
    "brass": dict(ramp=BRASS, base=0.6, lit=0.36, noise=0.03, crack=0, min=0),
}, glow_mats=[])


def coral(m, name, bone, base, h, mat, tip, lean=(0.0, 0.0, 0.0), n=3, r=1.4, seed=0):
    """枝珊瑚: 太い幹から n 本の枝。先に丸い玉。根元は甲羅に沈める。"""
    base = np.asarray(base, float)
    lean = np.asarray(lean, float)
    top = base + np.array([0, h * 0.5, 0]) + lean * 0.5
    m.add(name + "_t", tub(bone, mat, [base - np.array([0, 1.2, 0]), top], [r * 1.25, r], None, sides=5))
    for i in range(n):
        a = math.radians(i * 360.0 / n + 30 + seed * 40)
        tipp = top + np.array([math.sin(a) * h * 0.36, h * (0.46 + 0.1 * (i % 2)), math.cos(a) * h * 0.36]) + lean * 0.5
        mid = lerp(top, tipp, 0.5) + np.array([0, h * 0.12, 0])
        m.add(f"{name}_b{i}", tub(bone, mat, [top, mid, tipp], [r * 0.95, r * 0.8, r * 0.62], None, sides=5))
        m.add(f"{name}_k{i}", ell(bone, tip, tipp, (r * 0.85, r * 0.85, r * 0.85), segs=6, rings=2))


def build():
    m = Mount(ID, TITLE)
    m.bone("body", "root", (0, 20, 0))
    m.bone("neck", "body", (0, 15.0, -13.6))
    m.bone("head", "neck", (0, 17.6, -22.6))
    m.bone("tail", "body", (0, 13.6, 15.0))
    quad_bones(m, {"fl": ((8.4, 13.4, -9.6), (9.4, 7.6, -10.4)), "fr": ((-8.4, 13.4, -9.6), (-9.4, 7.6, -10.4)),
                   "bl": ((8.4, 13.4, 9.6), (9.4, 7.6, 10.4)), "br": ((-8.4, 13.4, 9.6), (-9.4, 7.6, 10.4))})

    # ---------------------------------------------------------------- 甲羅 (丸い高い背甲、縁の甲板、腹甲)
    rings = []
    for z in (-17.2, -15.0, -11.0, -5.0, 0.0, 5.0, 11.0, 15.0, 17.2):
        q = max(0.06, 1 - (z / 17.6) ** 2) ** 0.5
        rings.append(rsec(z, 13.6 + 16.6 * q ** 0.9, 11.6, 13.2 * q ** 0.55, 13.8, n=18, p=2.0, pb=4.0))
    m.add("carapace", lof("body", "shell", rings), core=True)
    rim = []
    for z in (-17.8, -15.4, -11.0, -5.0, 0.0, 5.0, 11.0, 15.4, 17.8):
        q = max(0.05, 1 - (z / 18.2) ** 2) ** 0.5
        rim.append(rsec(z, 14.6, 11.0, 13.9 * q ** 0.5, 12.8, n=16, p=3.0))
    m.add("rim", lof("body", "rim", rim))
    m.add("plastron", lof("body", "plast", [rsec(-13.6, 11.6, 9.0, 8.4, 10.8, n=12, p=3), rsec(0, 11.6, 8.4, 10.4, 10.4, n=12, p=3),
                                            rsec(13.6, 11.6, 9.0, 8.4, 10.8, n=12, p=3)]))
    ct = tri_arrays(m.faces_of(["carapace"]))
    # 背の稜の甲板 (鞍の前と後ろ)
    for i, z in enumerate((-11.6, 11.6)):
        y = top_y(ct, 0, z)
        m.add(f"scute{i}", cbox("body", "shell", (0, y - 0.3, z), (6.4, 2.2, 5.2), R=rot_x(-math.copysign(34, z)), taper=0.7, cut=0.5))
    # 縁の甲板の段 (前と後ろの反り返り)
    for i, (x, z) in enumerate(((0, -17.6), (5.6, -16.6), (-5.6, -16.6), (0, 17.6), (5.8, 16.6), (-5.8, 16.6))):
        m.add(f"marg{i}", ell("body", "rim", (x, 13.4, z), (3.0, 1.4, 1.8), segs=6, rings=2))
    # 珊瑚の茂み (甲羅の脇と後ろ。鞍の上には生やさない)
    for i, (x, z, h, mat, tip) in enumerate(((-10.4, -4.0, 10.0, "coral", "coral"), (10.4, 5.0, 11.0, "coralb", "coral"),
                                              (-9.6, 8.0, 8.2, "coralb", "coralb"), (10.0, -8.0, 7.6, "coral", "coralb"),
                                              (-7.6, -12.0, 5.6, "coral", "coral"))):
        d = nrm(np.array([x, 6.0, z * 0.3]))
        p = ray_far(ct, (0, 18, z), d)
        coral(m, f"cor{i}", "body", p - d * 0.6, h, mat, tip, lean=(math.copysign(2.4, x), 0, 0), seed=i)
    p = ray_far(ct, (0, 18, 10), (0, 0.5, 1.0))
    coral(m, "cor_r", "body", p, 6.4, "coralb", "coral", lean=(0, 0, 1.6), seed=3)
    for i, (x, z) in enumerate(((8.6, -11), (-8.8, 12), (11.0, 1.0), (-11.2, 2.0), (7.0, 12.4), (-6.4, -14.0))):
        d = nrm(np.array([x, 7.0, z * 0.25]))
        p = ray_far(ct, (0, 18, z), d)
        m.add(f"barn{i}", tub("body", "barn", [p - d * 1.0, p + d * 1.0, p + d * 1.6], [1.4, 1.2, 0.8], None, sides=6))

    # ---------------------------------------------------------------- 首と頭 (くちばし)
    m.add("neck", limb("neck", "skin", [(0, 14.2, -10.6), (0, 15.0, -15.0), (0, 16.4, -19.4), (0, 17.6, -22.6)], [8.4, 7.2, 6.2, 5.8],
                       [7.4, 6.6, 5.8, 5.4], n=12))
    for i, z in enumerate((-15.4, -18.2)):
        m.add(f"fold{i}", ell("neck", "skin", (0, 15.2 + i * 1.2, z), (3.9 - i * 0.4, 3.4 - i * 0.3, 1.2), segs=10, rings=2))
    hd = [rsec(-21.6, 21.0, 14.2, 4.2, 17.6, n=12), rsec(-25.0, 21.4, 14.0, 4.4, 17.6, n=12), rsec(-28.2, 20.4, 14.4, 3.6, 17.2, n=12),
          rsec(-30.6, 18.8, 15.2, 2.6, 16.8, n=12)]
    m.add("skull", lof("head", "skin", hd), core=True)
    m.add("beak", ell("head", "beak", (0, 16.2, -30.4), (2.5, 2.0, 2.0), segs=8, rings=2))
    m.add("beak_lo", ell("head", "beak", (0, 14.8, -29.4), (2.4, 1.0, 2.0), segs=8, rings=2))
    sk = tri_arrays(m.faces_of(["skull"]))
    for s, k in ((1, "l"), (-1, "r")):
        d = nrm(np.array([s * 1.0, 0.2, -0.45]))
        c = ray_far(sk, (0, 18.8, -26.0), d) - d * 0.45
        m.add(f"eye_{k}", ell("head", "eye", c, (1.1, 1.1, 1.1), segs=7, rings=2))
        m.eyes.append(dict(c=c, r=1.2, dir=tuple(d), a=0.88, b=0.88, bone="head", iris=(70, 46, 20), lid=(30, 40, 32), pa=0.62, pb=0.62))
        m.add(f"cheek_{k}", ell("head", "skin", c + np.array([-s * 0.4, -2.2, 1.2]), (1.6, 1.4, 2.0), segs=6, rings=2))

    # ---------------------------------------------------------------- 柱の脚 (うろこの皮と爪)
    for k in ("fl", "fr", "bl", "br"):
        up, lo = "leg_" + k, "leg_" + k + "_lo"
        hip, knee = m.pv(up), m.pv(lo)
        s = 1 if hip[0] > 0 else -1
        front = k[0] == "f"
        m.add(f"{k}_up", limb(up, "skin", [hip + np.array([-s * 1.6, 3.0, 0]), hip, knee], [8.0, 7.8, 7.0], [8.0, 7.6, 6.8], n=10))
        m.add(f"{k}_knee", ell(lo, "skin", knee, (3.6, 3.0, 3.4), segs=8, rings=2))
        ank = knee + np.array([s * 0.3, -4.4, -0.4 if front else 0.4])
        m.add(f"{k}_lo", limb(lo, "skin", [knee + np.array([0, 0.8, 0]), ank], [7.0, 6.8], [6.8, 6.6], n=10))
        fc = np.array([ank[0], 1.8, ank[2] - 0.6])
        m.add(f"{k}_foot", ell(lo, "skin", fc, (4.4, 1.9, 4.6), segs=10, rings=2))
        for j in range(4):
            a = math.radians((j - 1.5) * 30)
            p = fc + np.array([math.sin(a) * 3.6, -0.5, -math.cos(a) * 3.8])
            m.add(f"{k}_nail{j}", ell(lo, "beak", p, (1.0, 0.8, 1.1), segs=5, rings=1))
        for j in range(3):
            p = knee + np.array([s * 2.8, -0.6 - j * 1.6, -1.4 + j * 1.2])
            m.add(f"{k}_scale{j}", ell(lo, "skin", p, (1.4, 1.2, 1.4), segs=5, rings=1))
    tp = m.pv("tail")
    m.add("tail", cone("tail", "skin", tp + np.array([0, 0, -1.6]), (0, -0.5, 1), 6.2, 2.4, sides=6, rings=2))

    m.saddle_kw = dict(cy=14.0, length=7.4, half_angle=40, seat_len=5.4, seat_half=24, girth=False, blanket=0.8,
                       mats=dict(pad="clothr", trim="brass", seat="leath", strap="leathd", metal="brass"), fender=(20.0, 40.0, 4.4))
    m.prefer_pose = (-30.0, 50.0)
    m.pal = PAL
    m.detail = detail
    m.view = dict(center=(0, 18, -3), scale=4.4)
    m.gaits = gaits(m)
    m.hint = 256
    return m


def detail(c, col, glow):
    P, N, bones = c["P"], c["N"], c["bones"]
    col = plates(c, col, ["shell"], size=5.2, edge=0.07, rings=0.2, k_edge=0.55)
    col = speckle(c, col, ["shell"], (180, 196, 170), density=0.015, seed=3, size=1.0)
    col = spots(c, col, ["shell"], (70, 112, 70), scale=0.2, thr=0.8, seed=12, a=0.5, up_only=True)
    col = plates(c, col, ["rim"], size=2.6, edge=0.09, rings=0.5, k_edge=0.7, seed=4)
    col = scales(c, col, ["skin"], size=1.1, edge=0.12, k_edge=0.78)
    col = countershade(c, col, ["skin"], (176, 186, 150), top_k=0.94, belly_from=-0.3)
    col = bands_y(c, col, ["plast"], 2.6, 0.5, k=0.8)
    col = speckle(c, col, ["coral", "coralb"], (252, 230, 214), density=0.06, seed=9, size=0.6)
    col = edge_dark(c, col, ["leath", "leathd", "clothr"], 0.8)
    return col, glow


def gaits(m):
    idle, walk, run = quad_gaits(walk=(1.2, 16, 18, 16, 18), run=(2.2, 28, 30, 26, 28), run_ph=TROT_PH, neck="neck", head="head",
                                 tails=("tail",), bob=(0.4, 0.9), pitch=(0.8, 2.0), idle_period=7.0, run_base=(0, 0))
    idle.rot("neck", x=W(0).s(3.0, 1, 30, "t"), y=W(0).s(8.0, 1, 0, "t"))
    idle.rot("head", x=W(0).s(3.0, 1, 90, "t"), y=W(0).s(6.0, 1, 200, "t"))
    walk.rot("body", z=W(0).s(2.2, 1, 0), x=W(0).s(0.8, 2, 90))
    run.rot("body", z=W(0).s(3.0, 1, 0), x=W(0).s(1.4, 2, 90))
    jump = quad_jump(extra={"leg_fl": {"rot": {0.0: (0, 0, 0), 0.3: (-30, 0, 0), 0.7: (-16, 0, 0)}},
                            "leg_fr": {"rot": {0.0: (0, 0, 0), 0.3: (-30, 0, 0), 0.7: (-16, 0, 0)}},
                            "leg_fl_lo": {"rot": {0.0: (0, 0, 0), 0.3: (20, 0, 0), 0.7: (10, 0, 0)}},
                            "leg_fr_lo": {"rot": {0.0: (0, 0, 0), 0.3: (20, 0, 0), 0.7: (10, 0, 0)}}})
    return [idle, walk, run, jump]
