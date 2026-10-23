"""雷の雛 (選んだ案: B まるまる雛)。雷翼の巨神の雛: 大きな綿毛の玉に短い脚、小さな翼、稲妻形の冠。羽の先が雷 (青白く光る)。
骨: body / head / crest / wing_l / wing_r / tail / leg_l / leg_l_lo / leg_r / leg_r_lo。"""
from mlp import *  # noqa

ID = "storm_chick"
TITLE = "雷の雛"

FEATH = [(118, 88, 26), (160, 122, 34), (200, 158, 48), (232, 194, 76), (248, 222, 118), (255, 240, 170)]
CREAM = [(150, 132, 92), (190, 172, 126), (222, 206, 160), (242, 232, 194), (252, 248, 224)]
STORMB = [(28, 38, 72), (40, 58, 104), (58, 82, 140), (86, 114, 176), (124, 152, 208), (172, 196, 236)]
BOLT = [(110, 170, 230), (160, 214, 252), (206, 238, 255), (240, 250, 255)]
BEAK = [(140, 70, 18), (190, 104, 28), (228, 146, 44), (250, 186, 84)]
CHEEK = [(196, 92, 92), (228, 128, 120), (246, 164, 150)]
LEATH = [(46, 28, 18), (76, 48, 28), (108, 72, 42), (142, 102, 62), (172, 132, 86)]
LEATHD = [(28, 18, 14), (48, 32, 22), (70, 48, 32), (94, 66, 44)]
CLOTH = [(40, 50, 92), (56, 72, 128), (80, 100, 164), (116, 136, 196)]
BRASS = [(78, 56, 22), (126, 94, 38), (178, 138, 62), (222, 184, 98), (248, 222, 150)]
EYE = [(8, 8, 14), (16, 16, 26), (30, 30, 42)]

PAL = dict(mats={
    "feath": dict(ramp=FEATH, base=0.58, lit=0.32, noise=0.05, facevar=0.02, crack=0, min=1, streak=0.06, ao=0.12),
    "cream": dict(ramp=CREAM, base=0.58, lit=0.3, noise=0.05, facevar=0.02, crack=0, min=1, ao=0.08),
    "stormb": dict(ramp=STORMB, base=0.58, lit=0.34, noise=0.04, facevar=0.03, crack=0, min=1),
    "bolt": dict(ramp=BOLT, base=0.62, lit=0.3, noise=0.03, crack=0, min=1),
    "beak": dict(ramp=BEAK, base=0.6, lit=0.34, noise=0.03, crack=0, min=0),
    "cheek": dict(ramp=CHEEK, base=0.6, lit=0.24, noise=0.03, crack=0, min=0),
    "eye": dict(ramp=EYE, base=0.5, lit=0.2, noise=0.0, crack=0, min=0),
    "leath": dict(ramp=LEATH, base=0.6, lit=0.28, noise=0.05, crack=0, min=0, streak=0.06),
    "leathd": dict(ramp=LEATHD, base=0.6, lit=0.28, noise=0.04, crack=0, min=0),
    "clothr": dict(ramp=CLOTH, base=0.6, lit=0.24, noise=0.04, crack=0, min=0, stripe=4, axis="u"),
    "brass": dict(ramp=BRASS, base=0.6, lit=0.36, noise=0.03, crack=0, min=0),
}, glow_mats=["bolt"])


def bolt_feather(m, name, bone, p0, d, length, r=1.5, zig=1.8, n=3, side=None):
    """稲妻の折れ線の羽: 根元から n 回折れ、先の三分の一が光る雷。"""
    d = nrm(np.asarray(d, float))
    sd = nrm(np.cross(d, (0, 0, 1.0))) if side is None else nrm(np.asarray(side, float))
    if np.linalg.norm(sd) < 1e-6:
        sd = np.array([1.0, 0, 0])
    p0 = np.asarray(p0, float)
    pts = [p0]
    for i in range(1, n + 1):
        pts.append(p0 + d * length * i / n + sd * zig * (1 if i % 2 else -1) * (1 if i < n else 0.25))
    ra = [r * (1 - 0.4 * i / max(1, n - 1)) for i in range(n)]
    m.add(name, tub(bone, "stormb", pts[:-1], ra, None, sides=4, hint=tuple(np.cross(d, sd))))
    m.add(name + "_tip", cone(bone, "bolt", pts[-2] - (pts[-1] - pts[-2]) * 0.12, pts[-1] - pts[-2], np.linalg.norm(pts[-1] - pts[-2]) * 1.1,
                              ra[-1] * 1.05, sides=4, rings=2))


def build():
    m = Mount(ID, TITLE)
    m.bone("body", "root", (0, 14, -4))
    m.bone("head", "body", (0, 27.0, -12.0))
    m.bone("crest", "head", (0, 35.0, -15.0))
    m.bone("wing_l", "body", (10.2, 25.0, -4.0))
    m.bone("wing_r", "body", (-10.2, 25.0, -4.0))
    m.bone("tail", "body", (0, 23.0, 6.4))
    for s, k in ((1, "l"), (-1, "r")):
        m.bone("leg_" + k, "body", (s * 5.0, 12.6, -4.0))
        m.bone("leg_" + k + "_lo", "leg_" + k, (s * 5.2, 8.4, -4.6))

    # ---------------------------------------------------------------- 体 (綿毛の玉)
    rings = []
    for z in (-15.2, -13.0, -9.0, -4.0, 1.0, 5.0, 7.2):
        q = max(0.06, 1 - ((z + 4.0) / 11.4) ** 2) ** 0.5
        rings.append(rsec(z, 21.0 + 11.0 * q, 21.0 - 10.6 * q, 11.4 * q, 21.0, n=16, p=2.0))
    m.add("ball", lof("body", "feath", rings), core=True)
    # 綿毛の房 (体のまわりに生える)
    bt = tri_arrays(m.faces_of(["ball"]))
    for i in range(16):
        a = math.radians(i * 360 / 16 + 11)
        el = math.radians(-20 + 50 * ((i * 7) % 5) / 4)
        d = np.array([math.sin(a) * math.cos(el), math.sin(el), math.cos(a) * math.cos(el)])
        if d[2] < -0.55 and d[1] > -0.1:
            continue
        p = ray_far(bt, (0, 21, -4), d)
        if p is None:
            continue
        mat = "cream" if d[1] < -0.15 else "feath"
        m.add(f"fluff{i}", cone("body", mat, p - d * 0.9, d + np.array([0, -0.5, 0.6]), 2.6, 2.2, sides=4, rings=1))

    # ---------------------------------------------------------------- 頭 (大きな目、ほっぺ、くちばし)
    hd = []
    for z in (-8.4, -10.6, -14.0, -17.6, -20.6, -21.8):
        q = max(0.08, 1 - ((z + 14.6) / 7.0) ** 2) ** 0.5
        hd.append(rsec(z, 29.4 + 6.8 * q, 29.4 - 6.0 * q, 7.4 * q, 29.4, n=14, p=2.0))
    m.add("skull", lof("head", "feath", hd), core=True)
    m.add("beak", cone("head", "beak", (0, 28.8, -20.4), (0, -0.25, -1), 4.6, 2.6, sides=6, rings=2, rb=1.6, hint=(1, 0, 0)))
    m.add("beak_lo", cone("head", "beak", (0, 27.2, -19.8), (0, -0.45, -1), 3.0, 1.8, sides=6, rings=1, rb=1.0, hint=(1, 0, 0)))
    sk = tri_arrays(m.faces_of(["skull"]))
    for s, k in ((1, "l"), (-1, "r")):
        d = nrm(np.array([s * 0.75, 0.12, -0.66]))
        c = ray_far(sk, (0, 30.4, -15.0), d) - d * 0.7
        m.add(f"eye_{k}", ell("head", "eye", c, (1.8, 2.0, 1.8), segs=8, rings=2))
        m.eyes.append(dict(c=c, r=2.1, dir=tuple(d), a=1.4, b=1.6, bone="head", iris=(30, 46, 90), lid=(120, 90, 28), pa=0.62, pb=0.62))
        dc = nrm(np.array([s * 0.85, -0.3, -0.45]))
        cc = ray_far(sk, (0, 29.4, -15.0), dc)
        m.add(f"cheek_{k}", ell("head", "cheek", cc - dc * 0.5, (1.8, 1.4, 1.4), segs=7, rings=2))
    # 冠: 稲妻の羽が五本
    ct = m.pv("crest")
    for j, (dx, dz, ln) in enumerate(((-1.2, 0.3, 8.0), (-0.6, -0.1, 10.0), (0, -0.25, 11.0), (0.6, -0.1, 10.0), (1.2, 0.3, 8.0))):
        bolt_feather(m, f"crest{j}", "crest", ct + np.array([dx * 1.8, -1.6, dz * 2]), (dx * 0.7, 1.0, dz + 0.25), ln, r=1.3, zig=1.6,
                     side=(1, 0, 0) if abs(dx) < 0.1 else (0, 0, 1))
    for j in range(4):
        a = math.radians(j * 90 + 45)
        p = ray_far(sk, (0, 30, -14), (math.sin(a) * 0.6, 1, math.cos(a) * 0.6))
        m.add(f"headfluff{j}", cone("head", "feath", p - np.array([0, 0.8, 0]), (math.sin(a) * 0.6, 1, math.cos(a) * 0.6 + 0.4), 2.6, 1.4,
                                    sides=4, rings=1))

    # ---------------------------------------------------------------- 翼 (小さな青い翼、後ろの縁に雷の羽)
    for s, k in ((1, "l"), (-1, "r")):
        b = "wing_" + k
        piv = m.pv(b)
        m.add(f"wing_{k}", ell(b, "stormb", piv + np.array([s * 1.4, -3.4, 0.6]), (2.0, 6.0, 7.2), segs=10, rings=3, rz=s * 24, rx=-8))
        for j, (dy, dz, ln) in enumerate(((-6.0, 3.0, 7.0), (-7.4, 0.0, 8.0), (-6.4, -3.2, 6.0))):
            bolt_feather(m, f"wf_{k}{j}", b, piv + np.array([s * 3.0, dy + 2.0, dz]), (s * 0.55, -0.55, 0.6 + dz * 0.05), ln, r=1.3, zig=1.5,
                         side=(0, 0, 1))

    # ---------------------------------------------------------------- 尾羽 (雷)
    tp = m.pv("tail")
    m.add("tail_fluff", ell("tail", "feath", tp + np.array([0, 0.4, 0.6]), (4.4, 3.6, 2.6), segs=10, rings=2))
    for j, dx in enumerate((-0.55, 0.0, 0.55)):
        bolt_feather(m, f"tailf{j}", "tail", tp + np.array([dx * 3.0, 0.6, 1.4]), (dx, 0.8, 1.0), 8.5 + (j == 1) * 1.5, r=1.6, zig=2.0)

    # ---------------------------------------------------------------- 脚 (太もも = 綿毛、すね = 黄色の鱗、三本の前指と後ろ指)
    for s, k in ((1, "l"), (-1, "r")):
        up, lo = "leg_" + k, "leg_" + k + "_lo"
        hip, knee = m.pv(up), m.pv(lo)
        m.add(f"thigh_{k}", ell(up, "feath", lerp(hip, knee, 0.4) + np.array([s * 0.4, 1.2, 0]), (3.8, 4.6, 4.2), segs=9, rings=2))
        ank = np.array([s * 5.4, 1.6, -5.2])
        m.add(f"shin_{k}", limb(lo, "beak", [knee + np.array([0, 1.4, 0]), ank], [2.6, 2.0], [2.6, 2.0], n=8))
        for j, ang in enumerate((-30, 0, 30)):
            dd = np.array([math.sin(math.radians(ang)), -0.1, -math.cos(math.radians(ang))])
            m.add(f"toe_{k}{j}", tub(lo, "beak", [ank, ank + dd * 4.6], [0.95, 0.7], None, sides=4))
            m.add(f"claw_{k}{j}", cone(lo, "leathd", ank + dd * 4.4, dd + np.array([0, -0.5, 0]), 1.2, 0.55, sides=3, rings=1))
        m.add(f"toe_{k}b", tub(lo, "beak", [ank, ank + np.array([0, -0.1, 2.6])], [0.9, 0.6], None, sides=4))

    m.saddle_kw = dict(cy=20.0, length=6.6, half_angle=44, seat_len=5.2, seat_half=28, girth_z=-1.0, girth_cy=20.4, blanket=0.8,
                       mats=dict(pad="clothr", trim="brass", seat="leath", strap="leathd", metal="brass"), fender=(22.0, 44.0, 4.0))
    m.prefer_pose = (-35.0, 50.0)
    m.pal = PAL
    m.detail = detail
    m.view = dict(center=(0, 22, -4), scale=4.6)
    m.gaits = gaits(m)
    m.hint = 256
    return m


def detail(c, col, glow):
    P, N, bones = c["P"], c["N"], c["bones"]
    col = feather_rows(c, col, ["feath"], row=2.2, k_edge=0.84, k_tip=1.06)
    col = fur(c, col, ["cream"], scale=(1.4, 0.4, 1.4), amt=0.08, seed=8)
    col = countershade(c, col, ["feath"], (246, 234, 196), top_k=0.94, belly_from=-0.2)
    col = feather_rows(c, col, ["stormb"], row=1.8, k_edge=0.8, k_tip=1.1, axis=1)
    # 背と頭に淡い稲妻の筋 (光らない、雷の雛の印)
    sel = np.isin(bones, ["body"]) & (c["mats"] == "feath") & c["owned"] & (N[..., 1] > 0.2)
    zz = P[..., 2] + 3.0 * np.sign(np.sin(P[..., 0] * 0.55)) * 0.6
    col = tint(col, sel & (np.abs(np.abs(P[..., 0]) - (3.0 + 1.2 * np.sign(np.sin(zz * 0.8)))) < 0.45) & (P[..., 2] > 2), (250, 246, 210), 0.7)
    # 光る羽の先: 先ほど白く
    sel = (c["mats"] == "bolt") & c["owned"]
    col = tint(col, sel & (c["bay"] > 0.1), (250, 254, 255), 0.4)
    col = edge_dark(c, col, ["leath", "leathd", "clothr"], 0.8)
    return col, glow


def gaits(m):
    legs = (("leg_l", "leg_l_lo", 0.0), ("leg_r", "leg_r_lo", 180.0))
    idle = Gait("idle", period=4.0)
    idle.pos("body", y=W(0).s(0.5, 1, 0, "t"))
    idle.rot("body", x=W(0).s(1.0, 1, 90, "t"))
    idle.rot("head", z=W(0).s(7.0, 1, 0, "t"), x=W(0).s(3.0, 2, 30, "t"), y=W(0).s(10.0, 1, 120, "t"))
    idle.rot("crest", x=W(0).s(5.0, 2, 0, "t"), z=W(0).s(6.0, 1, 90, "t"))
    idle.rot("wing_l", z=W(-2).s(5.0, 2, 0, "t"))
    idle.rot("wing_r", z=W(2).s(-5.0, 2, 0, "t"))
    idle.rot("tail", x=W(0).s(6.0, 1, 40, "t"), y=W(0).s(10.0, 3, 0, "t"))
    # 歩き: 左右に揺れながらよちよち。冠と翼が跳ねる
    walk = Gait("walk", period=4.0, stride=1.1)
    for up, lo, ph in legs:
        leg_cycle(walk, up, lo, 30, 34, ph)
    walk.pos("body", y=W(0.4).s(0.9, 2, 90))
    walk.rot("body", z=W(0).s(6.0, 1, 0), x=W(2))
    walk.rot("head", z=W(0).s(-5.0, 1, 0), x=W(0).s(4.0, 2, 0))
    walk.rot("crest", x=W(0).s(8.0, 2, 60))
    walk.rot("wing_l", z=W(-4).s(8.0, 2, 0))
    walk.rot("wing_r", z=W(4).s(-8.0, 2, 0))
    walk.rot("tail", y=W(0).s(12.0, 1, 0))
    # 駆け: 前へ傾いて跳ねる走り。翼を広げてはばたく
    run = Gait("run", period=4.0, stride=2.2)
    for up, lo, ph in legs:
        leg_cycle(run, up, lo, 46, 56, ph, base=-6)
    run.pos("body", y=W(1.2).s(1.8, 2, 90))
    run.rot("body", x=W(10).s(3.0, 2, 0), z=W(0).s(4.0, 1, 0))
    run.rot("head", x=W(-10).s(5.0, 2, 30))
    run.rot("crest", x=W(14).s(10.0, 2, 90))
    run.rot("wing_l", z=W(-28).s(22.0, 2, 0))
    run.rot("wing_r", z=W(28).s(-22.0, 2, 0))
    run.rot("tail", x=W(-10).s(8.0, 2, 30))
    jump = jump_keys({
        "body": {"rot": {0.0: (0, 0, 0), 0.12: (6, 0, 0), 0.3: (-6, 0, 0), 0.7: (0, 0, 0)}},
        "leg_l": {"rot": {0.0: (0, 0, 0), 0.12: (10, 0, 0), 0.3: (-30, 0, 0), 0.7: (-18, 0, 0)}},
        "leg_r": {"rot": {0.0: (0, 0, 0), 0.12: (10, 0, 0), 0.3: (-30, 0, 0), 0.7: (-18, 0, 0)}},
        "leg_l_lo": {"rot": {0.0: (0, 0, 0), 0.3: (50, 0, 0), 0.7: (30, 0, 0)}},
        "leg_r_lo": {"rot": {0.0: (0, 0, 0), 0.3: (50, 0, 0), 0.7: (30, 0, 0)}},
        "wing_l": {"rot": {0.0: (0, 0, 0), 0.15: (0, 0, -60), 0.3: (0, 0, -10), 0.45: (0, 0, -60), 0.6: (0, 0, -10), 0.75: (0, 0, -50)}},
        "wing_r": {"rot": {0.0: (0, 0, 0), 0.15: (0, 0, 60), 0.3: (0, 0, 10), 0.45: (0, 0, 60), 0.6: (0, 0, 10), 0.75: (0, 0, 50)}},
        "crest": {"rot": {0.0: (0, 0, 0), 0.3: (20, 0, 0), 0.7: (8, 0, 0)}},
        "tail": {"rot": {0.0: (0, 0, 0), 0.3: (-18, 0, 0), 0.7: (-8, 0, 0)}},
    })
    return [idle, walk, run, jump]
