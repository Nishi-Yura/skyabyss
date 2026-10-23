"""岩の子鯨 (選んだ案: A 四つ足の子鯨)。一章の石の鯨の小さな親戚: 石の肌、腹の白い畝、背と頭のフジツボの石、四枚のひれ脚、尾びれ。
骨: body / head / leg_{fl,fr,bl,br} (+ _lo) / tail / tail2。席は原点の真上 (z = 0)。"""
from mlp import *  # noqa

ID = "whale_calf"
TITLE = "岩の子鯨"

STONE = [(30, 34, 42), (48, 54, 64), (70, 78, 90), (96, 104, 116), (124, 132, 142), (156, 162, 168), (188, 192, 194)]
PALE = [(92, 94, 92), (128, 130, 126), (160, 162, 156), (190, 192, 184), (214, 214, 204)]
BARN = [(70, 64, 54), (112, 104, 88), (156, 148, 128), (196, 190, 168), (226, 222, 202), (244, 240, 226)]
BARND = [(16, 18, 22), (30, 32, 38), (48, 50, 56)]
MOSS = [(34, 52, 40), (56, 80, 54), (84, 110, 68), (118, 140, 84)]
LEATH = [(46, 28, 18), (76, 48, 28), (108, 72, 42), (142, 102, 62), (172, 132, 86)]
LEATHD = [(28, 18, 14), (48, 32, 22), (70, 48, 32), (94, 66, 44)]
CLOTH = [(40, 54, 70), (58, 78, 100), (80, 104, 128), (110, 136, 158)]
BRASS = [(78, 56, 22), (126, 94, 38), (178, 138, 62), (222, 184, 98), (248, 222, 150)]
ROPE = [(70, 58, 40), (106, 90, 62), (146, 128, 92), (184, 168, 128)]

PAL = dict(mats={
    "stone": dict(ramp=STONE, base=0.58, lit=0.32, noise=0.05, facevar=0.015, crack=0.07, min=1, streak=0.04, ao=0.14,
                  stain=dict(ramp=MOSS, up=0.42, conc=0.3, low=0.0, thr=0.985)),
    "pale": dict(ramp=PALE, base=0.6, lit=0.26, noise=0.08, crack=0.08, min=0, ao=0.1),
    "barn": dict(ramp=BARN, base=0.6, lit=0.30, noise=0.12, facevar=0.08, crack=0.0, min=1),
    "barnd": dict(ramp=BARND, base=0.5, lit=0.2, noise=0.1, crack=0, min=0),
    "eye": dict(ramp=BARND, base=0.5, lit=0.2, noise=0.0, crack=0, min=0),
    "leath": dict(ramp=LEATH, base=0.6, lit=0.28, noise=0.08, crack=0, min=0, streak=0.12),
    "leathd": dict(ramp=LEATHD, base=0.6, lit=0.28, noise=0.06, crack=0, min=0),
    "clothr": dict(ramp=CLOTH, base=0.6, lit=0.24, noise=0.06, crack=0, min=0, stripe=4, axis="u"),
    "cloth": dict(ramp=ROPE, base=0.6, lit=0.26, noise=0.1, crack=0, min=0, stripe=2, axis="u"),
    "brass": dict(ramp=BRASS, base=0.6, lit=0.36, noise=0.04, crack=0, min=0),
}, glow_mats=[])


def build():
    m = Mount(ID, TITLE)
    m.bone("body", "root", (0, 22, 0))
    m.bone("head", "body", (0, 22, -13))
    for k, (x, z) in (("fl", (7.2, -8.5)), ("fr", (-7.2, -8.5)), ("bl", (6.8, 9.0)), ("br", (-6.8, 9.0))):
        hind = k[0] == "b"
        s = 1 if x > 0 else -1
        m.bone("leg_" + k, "body", (x, 17.5, z))
        knee = (s * (10.6 if not hind else 9.8), 9.8, z - 1.0 if not hind else z + 1.2)
        m.bone("leg_" + k + "_lo", "leg_" + k, knee)
    m.bone("tail", "body", (0, 22, 13))
    m.bone("tail2", "tail", (0, 21.4, 21))

    # ---------------------------------------------------------------- 胴 (頭と尾へなめらかにつながる丸い石の体)
    body = [rsec(-15, 32.4, 12.2, 11.6, 21.0, n=16), rsec(-8, 34.0, 11.0, 12.8, 21.4, n=16), rsec(0, 33.8, 11.2, 12.8, 21.8, n=16),
            rsec(6, 32.2, 12.6, 11.8, 22.2, n=16), rsec(11, 29.4, 15.0, 9.4, 22.4, n=16), rsec(15, 26.6, 17.4, 6.6, 22.4, n=16)]
    m.add("torso", lof("body", "stone", body), core=True)
    # 腹の白い畝の板 (胴の下に少し出る)
    belly = [rsec(-14, 16.2, 11.5, 10.0, 14.4, n=12), rsec(-6, 15.4, 10.5, 10.8, 13.6, n=12), rsec(3, 15.8, 10.7, 10.4, 13.8, n=12),
             rsec(10, 18.4, 14.5, 7.8, 16.8, n=12)]
    m.add("belly", lof("body", "pale", belly))
    # 背の石の稜 (鞍の後ろ。地層の板のように後ろへ重なる)
    tt = tri_arrays(m.faces_of(["torso"]))
    for i, (z, w, h) in enumerate(((9.4, 4.8, 2.6), (12.2, 3.8, 2.2), (14.6, 2.8, 1.8))):
        yb = top_y(tt, 0, z)
        m.add(f"ridge{i}", box("body", "stone", (0, yb + h * 0.1, z), (w, h * 1.5, 3.8), R=rot_x(-16), taper=(0.6, 0.5), cut=0.5))

    # ---------------------------------------------------------------- 頭 (丸い額、口の線、下あご)
    head = [rsec(-10, 31.6, 12.6, 11.2, 21.0, n=16), rsec(-17, 33.4, 12.4, 11.8, 21.6, n=16), rsec(-23, 33.0, 13.0, 11.0, 21.8, n=16),
            rsec(-27.5, 31.0, 14.2, 9.6, 21.4, n=16), rsec(-30.6, 27.6, 15.6, 7.4, 20.8, n=16), rsec(-32.2, 24.0, 17.2, 4.6, 20.4, n=16)]
    m.add("skull", lof("head", "stone", head), core=True)
    jaw = [rsec(-11, 18.6, 11.8, 10.9, 15.2, n=12), rsec(-19, 18.4, 12.2, 11.0, 15.2, n=12), rsec(-26.5, 18.6, 13.4, 9.4, 15.8, n=12),
           rsec(-30.4, 19.0, 15.2, 6.6, 17.0, n=12), rsec(-32.4, 19.2, 16.7, 3.8, 17.8, n=12)]
    m.add("jaw", lof("head", "pale", jaw))
    sk = tri_arrays(m.faces_of(["skull"]))
    for s, k in ((1, "l"), (-1, "r")):
        d = nrm(np.array([s * 1.0, 0.06, -0.22]))
        hit = ray_far(sk, (0, 22.6, -24.4), d)
        c = hit - d * 0.55
        m.add(f"eye_{k}", ell("head", "eye", c, (1.5, 1.7, 1.7), segs=8, rings=2))
        m.eyes.append(dict(c=c, r=1.8, dir=tuple(d), a=1.1, b=1.25, bone="head", iris=(178, 116, 44), lid=(44, 48, 56)))
        brow = ray_far(sk, (0, 25.6, -24.0), nrm(np.array([s * 1.0, 0.4, -0.2])))
        m.add(f"brow_{k}", ell("head", "stone", brow - np.array([s * 0.5, 0.4, 0]), (2.6, 1.3, 3.2), segs=7, rings=2, rz=s * 24))
    # 額のふくらみ (メロン)
    m.add("melon", ell("head", "stone", (0, 31.4, -24.0), (7.6, 2.8, 6.4), segs=10, rings=2))
    # 潮吹きの穴 (頭の上)
    m.add("blow", tub("head", "stone", [(0, 30.6, -15.0), (0, 33.6, -15.0), (0, 34.2, -15.0)], [3.0, 2.2, 1.5], [2.4, 1.8, 1.1], sides=8))
    m.add("blowhole", ell("head", "barnd", (0, 34.2, -15.0), (1.2, 0.4, 0.8), segs=6, rings=1))

    # ---------------------------------------------------------------- ひれ脚 (太い上腕 + 前腕 + 地面に付く櫂のひれ)
    for k in ("fl", "fr", "bl", "br"):
        up, lo = "leg_" + k, "leg_" + k + "_lo"
        hip, knee = m.pv(up), m.pv(lo)
        s = 1 if hip[0] > 0 else -1
        hind = k[0] == "b"
        sc_ = 0.9 if hind else 1.0
        top = hip + np.array([-s * 1.6, 3.8, 0])
        m.add(f"{k}_up", limb(up, "stone", [top, hip, knee], [8.6 * sc_, 8.4 * sc_, 7.0 * sc_], [8.0 * sc_, 7.6 * sc_, 6.4 * sc_], n=10))
        m.add(f"{k}_knee", ell(lo, "stone", knee, (3.5 * sc_, 3.1, 3.2 * sc_), segs=8, rings=2))
        ank = knee + np.array([s * 0.8, -5.6, -0.8 if not hind else 0.6])
        m.add(f"{k}_lo", limb(lo, "stone", [knee + np.array([0, 1.2, 0]), lerp(knee, ank, 0.5), ank], [6.8 * sc_, 6.3 * sc_, 6.0 * sc_],
                              [6.2 * sc_, 5.8 * sc_, 5.4 * sc_], n=10))
        fz = ank[2] - (2.4 if not hind else 1.4)
        fw = 5.2 * sc_
        flip_c = np.array([ank[0] + s * 0.8, 1.8, fz])
        m.add(f"{k}_flip", ell(lo, "stone", flip_c, (fw, 1.9, 6.8 * sc_), segs=10, rings=2, ry=s * 12))
        for j in range(3):
            tz = fz - 6.0 * sc_ + abs(j - 1) * 1.0
            m.add(f"{k}_nail{j}", ell(lo, "barn", (flip_c[0] + (j - 1) * 2.8 * sc_ + s * 0.4 * (j - 1), 1.4, tz), (1.4, 1.1, 1.5), segs=6, rings=1))

    # ---------------------------------------------------------------- 尾と尾びれ
    tail = [rsec(10, 29.4, 15.2, 9.0, 22.3, n=12), rsec(15, 27.0, 17.2, 6.6, 22.0, n=12), rsec(19, 25.2, 18.4, 4.8, 21.6, n=12),
            rsec(22.5, 24.0, 19.0, 3.4, 21.4, n=12)]
    m.add("tail", lof("tail", "stone", tail))
    t2 = [rsec(20.5, 24.4, 18.6, 3.8, 21.4, n=10), rsec(24, 23.4, 19.4, 3.0, 21.4, n=10), rsec(27.4, 22.8, 20.2, 2.3, 21.5, n=10)]
    m.add("tail2", lof("tail2", "stone", t2))
    for s, k in ((1, "l"), (-1, "r")):
        pts = [(0.4, 21.0, 24.4), (3.4, 21.1, 25.0), (7.0, 21.4, 26.6), (10.6, 21.9, 28.8), (13.0, 22.4, 31.6), (11.4, 22.3, 32.6),
               (8.4, 21.9, 31.8), (5.2, 21.6, 31.4), (2.4, 21.3, 30.8), (0.4, 21.0, 29.8)]
        pts = [np.array([s * x, y, z]) for x, y, z in pts]
        m.add(f"fluke_{k}", RK.extrude("tail2", "stone", pts, (0, 1.6, 0), taper=0.94))

    # ---------------------------------------------------------------- フジツボの石 (頭・肩・鞍のまわり・ひれ)
    core_t = tri_arrays(m.faces_of(["torso", "skull"]))

    def barnacle(name, bone, x, z, h, r, tris=core_t, lean=(0.0, 0.0)):
        y = top_y(tris, x, z)
        if y is None:
            return
        n = np.array([x * 0.06 + lean[0], 1.0, lean[1]])
        n = nrm(n)
        b0 = np.array([x, y - 1.2, z])
        m.add(name, tub(bone, "barn", [b0, b0 + n * (1.2 + h * 0.6), b0 + n * (1.2 + h)], [r, r * 0.92, r * 0.62], None, sides=6))
        m.add(name + "_pit", ell(bone, "barnd", b0 + n * (1.2 + h), (r * 0.42, 0.35, r * 0.42), segs=5, rings=1))

    spots_body = [(-7.6, -6.0, 2.2, 1.6), (-8.4, -2.0, 1.4, 1.3), (8.2, -5.0, 1.8, 1.5), (7.6, 6.4, 2.0, 1.5), (-7.0, 7.0, 1.6, 1.4),
                  (8.8, 2.0, 1.2, 1.2), (-4.4, 10.8, 1.6, 1.3), (4.6, 11.0, 1.4, 1.2), (-9.6, 3.0, 1.2, 1.1)]
    for i, (x, z, h, r) in enumerate(spots_body):
        barnacle(f"bc{i}", "body", x, z, h, r)
    head_t = tri_arrays(m.faces_of(["skull", "melon"]))
    for i, (x, z, h, r) in enumerate(((-3.0, -25.6, 1.6, 1.5), (3.2, -26.4, 1.3, 1.3), (0.2, -28.8, 1.0, 1.1), (-5.2, -20.0, 1.0, 1.1),
                                       (5.6, -19.2, 1.2, 1.2))):
        barnacle(f"hb{i}", "head", x, z, h, r, tris=head_t)

    # ---------------------------------------------------------------- 馬具: 首の綱 (石の札)、頭の綱
    t_all = tri_arrays(m.faces_of(["torso"]))
    pts = surface_arc(t_all, -13.6, 22.0, 0, 360, 10, off=0.5)
    m.add("collar", tub("body", "cloth", pts, 0.8, None, sides=4, hint=(0, 0, 1)))
    low = min(pts, key=lambda p: p[1])
    m.add("collar_tag", box("body", "brass", low + np.array([0, -1.4, -0.6]), (2.6, 3.0, 0.8), cut=0.4))
    hpts = surface_arc(head_t, -27.4, 22.0, 0, 360, 10, off=0.45)
    m.add("halter", tub("head", "leathd", hpts, 0.6, None, sides=4, hint=(0, 0, 1)))
    for s, k in ((1, "l"), (-1, "r")):
        p = max(hpts, key=lambda q: s * q[0])
        m.add(f"halter_ring_{k}", tub("head", "brass", [p + np.array([s * 0.2, 1.2, 0]), p + np.array([s * 0.9, 0, 0]), p + np.array([s * 0.2, -1.2, 0])],
                                      0.45, None, sides=4, hint=(0, 0, 1)))

    m.saddle_kw = dict(cy=22.0, length=8.6, half_angle=54, seat_len=6.0, seat_half=30, girth_z=1.0, girth_cy=22.0,
                       mats=dict(pad="clothr", trim="brass", seat="leath", strap="leathd", metal="brass", roll="cloth"),
                       rear_roll=(8.4, 2.0, 6.2))
    m.prefer_pose = (-35.0, 40.0)
    m.pal = PAL
    m.detail = detail
    m.view = dict(center=(0, 20, -2), scale=4.4)
    m.gaits = gaits(m)
    m.hint = 256
    return m


def detail(c, col, glow):
    P, N = c["P"], c["N"]
    bones = c["bones"]
    # 腹の畝 (喉から腹へ、縦の溝)
    sel = (c["mats"] == "pale") & c["owned"] & (N[..., 1] < -0.2)
    groove = (np.abs(((P[..., 0] + 40) % 2.2) - 1.1) < 0.32)
    col = shade(col, sel & groove, 0.78)
    # 口の線 (あごの上の縁に沿った暗い線)
    sel = (bones == "head") & np.isin(c["mats"], ["stone", "pale"]) & c["owned"]
    mouth = np.abs(P[..., 1] - (18.5 + 0.03 * (P[..., 2] + 32))) < 0.45
    col = shade(col, sel & mouth & (np.abs(N[..., 0]) > 0.25) & (P[..., 2] < -13), 0.42)
    # 石の肌: 苔の点、細かい白い粒 (塩)、腹寄りを明るく
    col = countershade(c, col, ["stone"], (150, 154, 150), top_k=0.95, belly_from=-0.35)
    col = speckle(c, col, ["stone"], (176, 182, 182), density=0.018, seed=4, size=1.0)
    col = spots(c, col, ["stone"], (78, 104, 70), scale=0.22, thr=0.78, seed=9, a=0.5, up_only=True)
    # 地層の筋 (石の鯨の一族の目印): 脇腹に横の層
    side = (c["mats"] == "stone") & c["owned"] & (np.abs(N[..., 0]) > 0.45)
    y = P[..., 1] + 0.6 * np.sin(P[..., 2] * 0.35) + 0.4 * np.sin(P[..., 2] * 0.9 + 1.3)
    col = shade(col, side & ((y % 3.4) < 0.5), 0.84)
    col = shade(col, side & ((y % 3.4) > 3.0), 1.07)
    # フジツボの縦すじ
    sel = (c["mats"] == "barn") & c["owned"]
    ang = np.arctan2(P[..., 0] * 3.1, P[..., 2] * 3.1)
    col = shade(col, sel & (np.sin(ang * 6 + P[..., 0] * 2 + P[..., 2] * 2) > 0.6), 0.82)
    # 革: 縫い目と縁
    col = stitches(c, col, ["leath"], (196, 164, 112))
    col = edge_dark(c, col, ["leath", "leathd", "clothr"], 0.8)
    return col, glow


def gaits(m):
    legs = [("leg_fl", "leg_fl_lo"), ("leg_fr", "leg_fr_lo"), ("leg_bl", "leg_bl_lo"), ("leg_br", "leg_br_lo")]
    # 待機: ゆっくり息をし、尾を持ち上げ、ひれを少し動かす。ときどき頭を振る
    idle = Gait("idle", period=6.0)
    idle.pos("body", y=W(0).s(0.35, 1, 0, "t"))
    idle.rot("body", x=W(0).s(0.8, 1, 90, "t"))
    idle.rot("head", x=W(-1).s(2.4, 1, 40, "t").s(1.0, 3, 0, "t"), y=W(0).s(4.0, 1, 120, "t"))
    idle.rot("tail", x=W(-4).s(5, 1, 0, "t"), y=W(0).s(3, 1, 90, "t"))
    idle.rot("tail2", x=W(-6).s(8, 1, -60, "t"))
    for (up, lo), ph in zip(legs, (0, 180, 90, 270)):
        idle.rot(up, z=W(0).s(1.2 if up.endswith(("fl", "bl")) else -1.2, 1, ph, "t"))
    # 歩き: 重い横の順の歩き。体が左右に揺れ、尾が上下に打つ
    walk = Gait("walk", period=4.0, stride=1.5)
    quad(walk, legs, 20, 22, WALK_PH, Ah=18, Bh=20)
    walk.pos("body", y=W(0.2).s(0.45, 2, 0))
    walk.rot("body", z=W(0).s(2.4, 1, 0), x=W(0).s(1.0, 2, 90))
    walk.rot("head", x=W(0).s(2.0, 2, 200), z=W(0).s(-1.6, 1, 0))
    walk.rot("tail", x=W(-2).s(6, 1, 30), y=W(0).s(5, 1, 90))
    walk.rot("tail2", x=W(-4).s(10, 1, -40))
    # 駆け: 跳ねる走り (前の二本・後ろの二本がそろう)。体がうねり、尾びれが大きく打つ
    run = Gait("run", period=4.0, stride=3.0)
    quad(run, legs, 34, 40, BOUND_PH, Ah=30, Bh=34, base=(-4, 6))
    run.pos("body", y=W(0.6).s(1.3, 1, 60))
    run.rot("body", x=W(0).s(5.0, 1, -30))
    run.rot("head", x=W(2).s(-5.0, 1, -10))
    run.rot("tail", x=W(-6).s(16, 1, 120))
    run.rot("tail2", x=W(-6).s(24, 1, 70))
    jump = jump_keys({
        "body": {"rot": {0.0: (0, 0, 0), 0.12: (6, 0, 0), 0.32: (-12, 0, 0), 0.7: (-4, 0, 0)}},
        "head": {"rot": {0.0: (0, 0, 0), 0.32: (-8, 0, 0), 0.7: (2, 0, 0)}},
        "leg_fl": {"rot": {0.0: (0, 0, 0), 0.32: (-38, 0, 0), 0.7: (-22, 0, 0)}},
        "leg_fr": {"rot": {0.0: (0, 0, 0), 0.32: (-38, 0, 0), 0.7: (-22, 0, 0)}},
        "leg_fl_lo": {"rot": {0.0: (0, 0, 0), 0.32: (36, 0, 0), 0.7: (20, 0, 0)}},
        "leg_fr_lo": {"rot": {0.0: (0, 0, 0), 0.32: (36, 0, 0), 0.7: (20, 0, 0)}},
        "leg_bl": {"rot": {0.0: (0, 0, 0), 0.12: (-10, 0, 0), 0.32: (40, 0, 0), 0.7: (26, 0, 0)}},
        "leg_br": {"rot": {0.0: (0, 0, 0), 0.12: (-10, 0, 0), 0.32: (40, 0, 0), 0.7: (26, 0, 0)}},
        "leg_bl_lo": {"rot": {0.0: (0, 0, 0), 0.32: (-10, 0, 0), 0.7: (8, 0, 0)}},
        "leg_br_lo": {"rot": {0.0: (0, 0, 0), 0.32: (-10, 0, 0), 0.7: (8, 0, 0)}},
        "tail": {"rot": {0.0: (0, 0, 0), 0.32: (-16, 0, 0), 0.7: (6, 0, 0)}},
        "tail2": {"rot": {0.0: (0, 0, 0), 0.32: (-22, 0, 0), 0.7: (12, 0, 0)}},
    })
    return [idle, walk, run, jump]
