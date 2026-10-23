"""大トカゲ (選んだ案: B 帆背のトカゲ)。脚を立てて高く構える。首の赤い襟と喉袋、背に並ぶ棘の帆 (鞍の前と後ろ)、反り上がる尾。
骨: body / neck / head / jaw / tail / tail2 / tail3 / leg_{fl,fr,bl,br} (+ _lo)。"""
from mlp import *  # noqa

ID = "great_lizard"
TITLE = "大トカゲ"

SKIN = [(40, 38, 24), (60, 58, 34), (84, 82, 48), (112, 108, 64), (140, 134, 84), (168, 160, 108), (194, 186, 136)]
SKIND = [(22, 26, 18), (34, 40, 26), (50, 58, 36), (70, 78, 48)]
BELLY = [(112, 102, 68), (148, 138, 96), (180, 170, 124), (206, 198, 152), (226, 220, 180)]
CORAL = [(80, 20, 22), (128, 34, 32), (172, 56, 46), (210, 86, 64), (236, 126, 92), (248, 168, 130)]
BONE = [(110, 102, 86), (150, 142, 122), (190, 182, 160), (222, 216, 196)]
LEATH = [(46, 28, 18), (76, 48, 28), (108, 72, 42), (142, 102, 62), (172, 132, 86)]
LEATHD = [(28, 18, 14), (48, 32, 22), (70, 48, 32), (94, 66, 44)]
CLOTH = [(76, 58, 22), (116, 88, 34), (154, 120, 52), (190, 154, 76)]
BRASS = [(78, 56, 22), (126, 94, 38), (178, 138, 62), (222, 184, 98), (248, 222, 150)]
EYE = [(10, 8, 8), (20, 16, 16), (34, 28, 26)]

PAL = dict(mats={
    "skin": dict(ramp=SKIN, base=0.56, lit=0.32, noise=0.05, facevar=0.02, crack=0, min=1, streak=0.05, ao=0.16),
    "skind": dict(ramp=SKIND, base=0.55, lit=0.30, noise=0.05, crack=0, min=0, ao=0.1),
    "belly": dict(ramp=BELLY, base=0.58, lit=0.3, noise=0.04, crack=0, min=1, ao=0.06),
    "coral": dict(ramp=CORAL, base=0.58, lit=0.34, noise=0.05, facevar=0.04, crack=0, min=1),
    "bone": dict(ramp=BONE, base=0.6, lit=0.32, noise=0.04, crack=0, min=0),
    "eye": dict(ramp=EYE, base=0.5, lit=0.2, noise=0.0, crack=0, min=0),
    "leath": dict(ramp=LEATH, base=0.6, lit=0.28, noise=0.05, crack=0, min=0, streak=0.06),
    "leathd": dict(ramp=LEATHD, base=0.6, lit=0.28, noise=0.04, crack=0, min=0),
    "clothr": dict(ramp=CLOTH, base=0.6, lit=0.24, noise=0.04, crack=0, min=0, stripe=4, axis="u"),
    "brass": dict(ramp=BRASS, base=0.6, lit=0.36, noise=0.03, crack=0, min=0),
}, glow_mats=[])


def blade(bone, base_y, z, h, w=2.4, lean=1.4, th=0.9):
    """帆の一枚 (棘の間の膜): 根元は背に沈め、上は後ろへ倒れた尖り。"""
    pts = [np.array([0, base_y - 1.4, z - w]), np.array([0, base_y - 1.4, z + w]), np.array([0, base_y + h * 0.62, z + w * 0.7 + lean * 0.6]),
           np.array([0, base_y + h, z + lean])]
    return RK.extrude(bone, "coral", pts, (th, 0, 0), taper=0.7)


def build():
    m = Mount(ID, TITLE)
    m.bone("body", "root", (0, 19, 0))
    m.bone("neck", "body", (0, 19.6, -18))
    m.bone("head", "neck", (0, 22.0, -23.4))
    m.bone("jaw", "head", (0, 21.0, -24.6))
    m.bone("tail", "body", (0, 18.6, 7.0))
    m.bone("tail2", "tail", (0, 17.0, 16.6))
    m.bone("tail3", "tail2", (0, 15.6, 25.8))
    quad_bones(m, {"fl": ((4.6, 17.0, -13.6), (10.0, 11.8, -14.6)), "fr": ((-4.6, 17.0, -13.6), (-10.0, 11.8, -14.6)),
                   "bl": ((4.6, 17.0, 3.6), (10.2, 11.8, 3.0)), "br": ((-4.6, 17.0, 3.6), (-10.2, 11.8, 3.0))})

    # ---------------------------------------------------------------- 胴 (平たく幅広、肩と腰が張る)
    body = [rsec(-22, 22.2, 15.4, 4.6, 18.6, n=14, p=2.2), rsec(-16, 24.0, 13.6, 6.6, 18.4, n=14, p=2.2),
            rsec(-8, 24.6, 13.2, 7.0, 18.4, n=14, p=2.2), rsec(0, 24.4, 13.4, 6.8, 18.4, n=14, p=2.2),
            rsec(5.6, 23.4, 14.4, 6.2, 18.6, n=14, p=2.2), rsec(10, 21.8, 15.6, 4.8, 18.6, n=14, p=2.2)]
    m.add("torso", lof("body", "skin", body), core=True)
    belly = [rsec(-19, 16.4, 13.0, 4.6, 14.8, n=10), rsec(-8, 15.6, 12.4, 5.6, 14.0, n=10), rsec(4, 16.0, 13.0, 5.2, 14.4, n=10)]
    m.add("belly", lof("body", "belly", belly))
    # 肩と腰の張り (脚の付け根の筋肉)
    for s, k in ((1, "l"), (-1, "r")):
        m.add(f"shoulder_{k}", ell("body", "skin", (s * 5.4, 19.2, -13.6), (3.0, 3.6, 4.2), segs=8, rings=2))
        m.add(f"hip_{k}", ell("body", "skin", (s * 5.4, 19.2, 3.6), (3.0, 3.8, 4.6), segs=8, rings=2))

    # ---------------------------------------------------------------- 首・襟・喉袋・頭・あご
    m.add("neck", limb("neck", "skin", [(0, 19.0, -19.0), (0, 20.0, -22.0), (0, 21.6, -25.0)], [8.2, 7.4, 6.6], [7.4, 6.6, 6.0], n=12))
    m.add("pouch", ell("neck", "coral", (0, 17.8, -24.6), (2.4, 3.0, 3.4), segs=8, rings=2))
    fc = np.array([0, 22.8, -21.4])
    Rf = rot_x(14)
    m.add("frill", ell("neck", "coral", fc, (8.6, 7.8, 0.8), segs=14, rings=3, rx=14))
    for i in range(9):
        a = math.radians(-80 + i * 20)
        d = Rf @ np.array([math.sin(a), math.cos(a), 0.0])
        p = fc + d * np.array([7.6, 6.9, 7.6])
        m.add(f"fspike{i}", cone("neck", "skind", p - d * 0.6, d + np.array([0, 0, 0.25]), 3.6, 0.95, sides=4, rings=2))
    piv = m.pv("head")
    R = rot_x(-6)
    hd = [rsec(-23.6, 25.4, 19.8, 3.6, 22.4, n=12), rsec(-27.0, 26.0, 20.6, 3.9, 23.0, n=12), rsec(-30.6, 25.0, 21.0, 3.3, 22.8, n=12),
          rsec(-33.6, 23.6, 21.2, 2.5, 22.2, n=12), rsec(-35.4, 22.8, 21.4, 1.6, 22.0, n=12)]
    m.add("skull", lof("head", "skin", xf(hd, R, piv)), core=True)
    jw = [rsec(-24.0, 21.4, 18.6, 3.4, 20.0, n=10), rsec(-29.0, 21.6, 19.2, 3.2, 20.4, n=10), rsec(-33.4, 21.6, 20.0, 2.1, 20.8, n=10),
          rsec(-35.0, 21.6, 20.6, 1.3, 21.0, n=10)]
    m.add("jaw", lof("jaw", "belly", xf(jw, R, piv)))
    for j, z in enumerate((-31.0, -33.2)):
        for s in (1, -1):
            m.add(f"tooth{j}{'lr'[s < 0]}", cone("head", "bone", xf(np.array([s * (2.2 - j * 0.5), 21.4, z]), R, piv), (0, -1, 0), 1.4, 0.45, sides=3,
                                                rings=1))
    sk = tri_arrays(m.faces_of(["skull"]))
    for s, k in ((1, "l"), (-1, "r")):
        c0 = xf(np.array([0, 24.0, -28.4]), R, piv)
        d = nrm(np.array([s * 1.0, 0.25, -0.2]))
        c = ray_far(sk, c0, d) - d * 0.5
        m.add(f"eye_{k}", ell("head", "eye", c, (1.1, 1.1, 1.2), segs=7, rings=2))
        m.eyes.append(dict(c=c, r=1.25, dir=tuple(d), a=0.9, b=0.9, bone="head", iris=(222, 178, 40), lid=(40, 44, 26), pa=0.22, pb=0.85))
        # 眉の棘の稜
        for j in range(3):
            p = ray_far(sk, xf(np.array([0, 24.4, -26.2 - j * 1.8]), R, piv), nrm(np.array([s * 0.7, 1.0, 0])))
            m.add(f"brow_{k}{j}", cone("head", "skind", p - np.array([0, 0.4, 0]), (s * 0.5, 1.0, 0.5), 2.0 - j * 0.3, 0.7, sides=3, rings=1))

    # ---------------------------------------------------------------- 帆 (鞍の前と後ろ)
    tt = tri_arrays(m.faces_of(["torso"]))
    for i, (z, h) in enumerate(((-20.0, 4.0), (-17.0, 7.0), (-14.0, 9.2), (-11.0, 8.4), (-8.0, 5.6))):
        y = top_y(tt, 0, z)
        m.add(f"sail_f{i}", blade("body", y, z, h))
        m.add(f"spine_f{i}", cone("body", "skind", (0, y + h - 0.6, z + 1.4), (0, 1, 0.3), 2.2, 0.6, sides=3, rings=1))
    m.add("sail_b0", blade("body", top_y(tt, 0, 8.4), 8.4, 5.0))

    # ---------------------------------------------------------------- 尾 (三つの骨。先は上へ反る)
    tail = [rsec(5, 23.0, 14.8, 5.2, 18.6, n=12), rsec(11, 21.4, 15.4, 3.9, 18.4, n=12), rsec(17.6, 19.4, 14.8, 3.0, 17.2, n=12)]
    m.add("tail", lof("tail", "skin", tail))
    t2 = [rsec(15.6, 19.8, 14.4, 3.3, 17.0, n=10), rsec(21, 18.2, 14.0, 2.6, 16.0, n=10), rsec(26.8, 17.2, 14.0, 1.9, 15.6, n=10)]
    m.add("tail2", lof("tail2", "skin", t2))
    m.add("tail3", limb("tail3", "skin", [(0, 15.6, 25.0), (0, 15.8, 30.0), (0, 18.4, 34.6), (0, 23.0, 36.8), (0, 25.6, 37.0)],
                        [3.6, 3.0, 2.4, 1.6, 0.5], [3.0, 2.6, 2.1, 1.4, 0.5], n=8, side=(1, 0, 0)))
    t1t = tri_arrays(m.faces_of(["tail"]))
    for i, (z, h) in enumerate(((11.4, 4.2), (15.0, 3.2))):
        m.add(f"sail_t{i}", blade("tail", top_y(t1t, 0, z), z, h, w=2.0))
    t2t = tri_arrays(m.faces_of(["tail2"]))
    for i, (z, h) in enumerate(((19.0, 2.4), (22.4, 1.8))):
        m.add(f"tail_spike{i}", cone("tail2", "skind", (0, top_y(t2t, 0, z) - 0.5, z), (0, 1, 0.5), h, 0.7, sides=3, rings=1))

    # ---------------------------------------------------------------- 脚 (肘・膝が外へ張り、足は長い指と爪)
    for k in ("fl", "fr", "bl", "br"):
        up, lo = "leg_" + k, "leg_" + k + "_lo"
        hip, knee = m.pv(up), m.pv(lo)
        s = 1 if hip[0] > 0 else -1
        hind = k[0] == "b"
        m.add(f"{k}_up", limb(up, "skin", [hip + np.array([-s * 1.4, 2.4, 0]), hip, knee], [5.6, 5.2, 3.8], [5.8, 5.4, 4.0], n=8, side=(0, 1, 0)))
        m.add(f"{k}_knee", ell(lo, "skin", knee, (2.2, 2.2, 2.2), segs=7, rings=2))
        ank = knee + np.array([-s * 1.2, -8.6, -0.8 if not hind else 1.0])
        m.add(f"{k}_lo", limb(lo, "skin", [knee + np.array([0, 0.6, 0]), lerp(knee, ank, 0.5), ank], [3.6, 3.0, 2.6], [3.8, 3.2, 2.8], n=8))
        pad = np.array([ank[0], 1.1, ank[2] - 1.2])
        m.add(f"{k}_pad", ell(lo, "skin", pad, (2.6, 1.1, 2.8), segs=8, rings=2))
        for j in range(4):
            a = math.radians((j - 1.5) * 24 - s * 8)
            dd = np.array([math.sin(a), -0.08, -math.cos(a)])
            p0 = pad + np.array([math.sin(a) * 1.2, 0.2, -math.cos(a) * 1.6])
            m.add(f"{k}_toe{j}", tub(lo, "skin", [p0, p0 + dd * (2.6 + 0.6 * (j in (1, 2)))], [0.75, 0.55], None, sides=4))
            m.add(f"{k}_claw{j}", cone(lo, "bone", p0 + dd * (2.4 + 0.6 * (j in (1, 2))), dd + np.array([0, -0.4, 0]), 1.5, 0.5, sides=3, rings=1))

    m.saddle_kw = dict(cy=18.6, length=6.6, half_angle=62, seat_len=5.2, seat_half=36, girth_z=-1.6, girth_cy=18.4, blanket=0.8,
                       mats=dict(pad="clothr", trim="brass", seat="leath", strap="leathd", metal="brass"))
    m.prefer_pose = (-40.0, 34.0)
    m.pal = PAL
    m.detail = detail
    m.view = dict(center=(0, 18, -2), scale=4.0)
    m.gaits = gaits(m)
    m.hint = 256
    return m


def detail(c, col, glow):
    P, N, bones = c["P"], c["N"], c["bones"]
    col = scales(c, col, ["skin"], size=1.25, edge=0.12, k_edge=0.74, k_hi=1.08)
    col = scales(c, col, ["belly"], size=1.7, edge=0.1, k_edge=0.8)
    # 背の横縞 (暗い帯) と、脇腹の点
    sel = np.isin(bones, ["body", "tail", "tail2", "tail3", "neck"]) & (c["mats"] == "skin") & c["owned"] & (N[..., 1] > -0.2)
    band = ((P[..., 2] + 0.8 * np.sin(P[..., 0] * 0.6) + 60) % 7.0) < 2.4
    col = shade(col, sel & band, 0.66)
    col = spots(c, col, ["skin"], (196, 182, 120), scale=0.5, thr=0.78, seed=5, a=0.45)
    col = countershade(c, col, ["skin"], (176, 166, 118), top_k=0.94, belly_from=-0.35)
    # 襟と帆の膜の筋 (根元から外へ)
    sel = (c["mats"] == "coral") & c["owned"]
    ang = np.arctan2(P[..., 1] - 22.8, P[..., 0])
    col = shade(col, sel & (bones == "neck") & (np.abs(np.sin(ang * 9)) < 0.22), 0.74)
    col = shade(col, sel & (bones != "neck") & (np.abs(np.sin(P[..., 2] * 1.4)) < 0.2), 0.78)
    col = tint(col, sel & (np.abs(P[..., 0]) > 6.8) & (bones == "neck"), (240, 196, 120), 0.5)
    col = edge_dark(c, col, ["leath", "leathd", "clothr"], 0.8)
    return col, glow


def gaits(m):
    legs = LEGS4
    idle = Gait("idle", period=6.0)
    idle.pos("body", y=W(0).s(0.25, 1, 0, "t"))
    idle.rot("neck", x=W(0).s(2.0, 1, 30, "t"), y=W(0).s(5.0, 1, 90, "t"))
    idle.rot("head", x=W(0).s(2.0, 2, 60, "t"), y=W(0).s(8.0, 1, 200, "t"))
    idle.rot("jaw", x=W(5.0).s(5.0, 1, -90, "t"))
    idle.rot("tail", y=W(0).s(5.0, 1, 0, "t"))
    idle.rot("tail2", y=W(0).s(7.0, 1, -50, "t"))
    idle.rot("tail3", y=W(0).s(10.0, 1, -100, "t"), x=W(0).s(4.0, 2, 0, "t"))
    # 歩き・駆け: 対角の脚がそろう。体は左右にうねり (y)、尾はその波を後ろへ送る
    walk = Gait("walk", period=4.0, stride=1.6)
    quad(walk, legs, 24, 24, TROT_PH, Ah=22, Bh=22)
    walk.rot("body", y=W(0).s(5.0, 1, 0), z=W(0).s(1.6, 1, 90))
    walk.pos("body", y=W(0.1).s(0.35, 2, 0))
    walk.rot("neck", y=W(0).s(-4.0, 1, 20))
    walk.rot("head", y=W(0).s(-3.0, 1, 40))
    walk.rot("tail", y=W(0).s(-6.0, 1, 50))
    walk.rot("tail2", y=W(0).s(-9.0, 1, 100))
    walk.rot("tail3", y=W(0).s(-12.0, 1, 150), x=W(-4))
    for (up, lo), ph in zip(legs, TROT_PH):
        walk.rot(up, x=W(0).s(24 if up.endswith(("fl", "fr")) else 22, 1, ph), z=W(0).s(6.0 if up[-1] == "l" else -6.0, 1, ph - 90))
    run = Gait("run", period=4.0, stride=3.0)
    quad(run, legs, 38, 40, TROT_PH, Ah=36, Bh=38, base=(-4, 4))
    run.rot("body", y=W(0).s(8.0, 1, 0), x=W(-2).s(2.0, 2, 0))
    run.pos("body", y=W(0.5).s(0.8, 2, 30))
    run.rot("neck", y=W(0).s(-6.0, 1, 20), x=W(4))
    run.rot("head", y=W(0).s(-4.0, 1, 40), x=W(-4))
    run.rot("jaw", x=W(6))
    run.rot("tail", y=W(0).s(-9.0, 1, 50), x=W(-3))
    run.rot("tail2", y=W(0).s(-13.0, 1, 100))
    run.rot("tail3", y=W(0).s(-16.0, 1, 150), x=W(-8))
    jump = jump_keys({
        "body": {"rot": {0.0: (0, 0, 0), 0.12: (4, 0, 0), 0.3: (-12, 0, 0), 0.7: (-4, 0, 0)}},
        "neck": {"rot": {0.0: (0, 0, 0), 0.3: (-10, 0, 0), 0.7: (0, 0, 0)}},
        "jaw": {"rot": {0.0: (0, 0, 0), 0.3: (14, 0, 0), 0.7: (4, 0, 0)}},
        "leg_fl": {"rot": {0.0: (0, 0, 0), 0.3: (-40, 0, 20), 0.7: (-24, 0, 10)}},
        "leg_fr": {"rot": {0.0: (0, 0, 0), 0.3: (-40, 0, -20), 0.7: (-24, 0, -10)}},
        "leg_bl": {"rot": {0.0: (0, 0, 0), 0.12: (-10, 0, 0), 0.3: (40, 0, 0), 0.7: (20, 0, 0)}},
        "leg_br": {"rot": {0.0: (0, 0, 0), 0.12: (-10, 0, 0), 0.3: (40, 0, 0), 0.7: (20, 0, 0)}},
        "tail": {"rot": {0.0: (0, 0, 0), 0.3: (-10, 0, 0), 0.7: (4, 0, 0)}},
        "tail2": {"rot": {0.0: (0, 0, 0), 0.3: (-14, 0, 0), 0.7: (6, 0, 0)}},
        "tail3": {"rot": {0.0: (0, 0, 0), 0.3: (-18, 0, 0), 0.7: (8, 0, 0)}},
    })
    return [idle, walk, run, jump]
