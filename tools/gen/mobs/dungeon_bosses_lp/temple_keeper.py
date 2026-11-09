"""珊瑚に呑まれた祭司 (temple_keeper)。沈んだ神殿の主 (docs/design/55 の 7 章。2026-11-09 の頁で A)。
2026-11-09 の声「ボスのモデルもラフのままだし、めっちゃ小さい。クオリティ低い」で、ラフ (tools/rough/lowpoly/late_foes) から作り直した。
裾の広い潮色の法衣の祭司が、肩から頭へ珊瑚に呑まれている。顔は珊瑚の仮面 (光る目)、背と肩に枝珊瑚と藤壺、垂れる海藻、
胸に光る潮の玉、右手に三叉の潮の槍。足はなく、裾で滑るように進む。

骨: body (腰) の子に torso・robe (裾)。torso の子に head・arm_l → fore_l、arm_r → fore_r (槍)。
動き: idle・walk・thrust (潮の槍)・whirl (渦)・flood (満ち潮)・hurt・death
"""
import math

import numpy as np

from blp import *  # noqa: F401,F403
from blp import keys, eye

ROBE = [(14, 40, 50), (22, 60, 72), (34, 86, 98), (52, 114, 124), (80, 146, 150)]
TRIM = [(110, 86, 40), (160, 128, 60), (206, 170, 90), (236, 210, 140)]
CORAL = [(120, 34, 48), (176, 56, 66), (224, 92, 92), (246, 140, 126)]
CORAL2 = [(150, 70, 24), (206, 110, 40), (240, 156, 70), (252, 200, 120)]
SHELL = [(150, 136, 116), (196, 182, 160), (226, 216, 198), (244, 238, 226)]
BARN = [(70, 70, 74), (104, 102, 104), (140, 138, 136), (176, 172, 166)]
KELP = [(22, 46, 24), (36, 70, 34), (56, 96, 46), (82, 124, 60)]
SKIN = [(70, 96, 100), (96, 128, 130), (128, 158, 156)]
WOOD = [(70, 56, 40), (104, 84, 60), (140, 116, 84)]
GLOW = [(30, 150, 160), (70, 210, 214), (150, 244, 240), (220, 255, 250)]


def branch(m, name, bone, mat, base, d, length, r, forks=2, seed=0):
    """枝珊瑚: 太い幹から二、三の小枝 (先は丸く)"""
    d = nrm(np.asarray(d, float))
    base = np.asarray(base, float)
    tip = base + d * length
    m.add(name, tub(bone, mat, [base, base + d * length * 0.5 + np.array([0, 0.3, 0]), tip], [r, r * 0.8, r * 0.55], None, sides=5))
    m.add(name + "_tip", ell(bone, mat, tip, (r * 0.75, r * 0.75, r * 0.75), segs=6, rings=2))
    for k in range(forks):
        a = math.radians(seed * 47 + k * 140)
        side = nrm(np.cross(d, np.array([math.cos(a), 0.3, math.sin(a)])) + d * 0.6)
        s0 = base + d * length * (0.45 + 0.2 * k)
        s1 = s0 + side * length * 0.45
        m.add(f"{name}_f{k}", tub(bone, mat, [s0, s1], [r * 0.55, r * 0.35], None, sides=4))
        m.add(f"{name}_f{k}t", ell(bone, mat, s1, (r * 0.45, r * 0.45, r * 0.45), segs=5, rings=2))


def build():
    m = Mount("temple_keeper", "珊瑚に呑まれた祭司")
    m.bone("body", "root", (0, 20, 0))
    m.bone("robe", "body", (0, 20, 0))
    m.bone("torso", "body", (0, 24, 0))
    m.bone("head", "torso", (0, 41, -1))
    for s, side in ((1, "l"), (-1, "r")):
        m.bone(f"arm_{side}", "torso", (s * 9.0, 38, 0))
        m.bone(f"fore_{side}", f"arm_{side}", (s * 11.0, 28.5, -2))
    # ---- 裾: 床に広がる法衣 (縁の金の帯と、裾の破れ)
    rings = []
    for y, w, d in ((0.6, 13.0, 12.0), (5, 11.6, 10.6), (11, 10.0, 9.0), (17, 8.6, 7.6), (23, 8.4, 6.6)):
        rings.append([np.array([math.cos(math.radians(a)) * w, y, math.sin(math.radians(a)) * d]) for a in range(0, 360, 20)])
    m.add("robe", lof("robe", "robe", rings), core=True)
    m.add("hem", lof("robe", "trim", [[np.array([math.cos(math.radians(a)) * 13.3, 0.6, math.sin(math.radians(a)) * 12.3]) for a in range(0, 360, 20)],
                                       [np.array([math.cos(math.radians(a)) * 12.9, 2.0, math.sin(math.radians(a)) * 11.9]) for a in range(0, 360, 20)]]))
    for k in range(10):
        a = math.radians(k * 36 + 10)
        m.add(f"robe_fold{k}", tub("robe", "robe", [(math.cos(a) * 12.4, 1.0, math.sin(a) * 11.4), (math.cos(a) * 8.8, 18, math.sin(a) * 7.8)],
                                    [1.0, 0.6], None, sides=4))
    m.add("sash", box("robe", "trim", (0, 12, -9.6), (3.4, 20, 0.8), taper=(0.8, 1.0)))
    for k in range(3):
        m.add(f"sash_glyph{k}", box("robe", "glow", (0, 20 - k * 5.5, -10.1), (1.4, 1.4, 0.3)))
    # 裾の藤壺と、床をはう珊瑚
    for k in range(8):
        a = math.radians(k * 45 + 22)
        p = np.array([math.cos(a) * 11.2, 3.0 + (k % 3) * 2.5, math.sin(a) * 10.2])
        m.add(f"barn{k}", cone("robe", "barnacle", p, (math.cos(a), 0.4, math.sin(a)), 1.4, 1.1, sides=6, rings=1))
    # ---- 胴と帯
    torso = [rsec(z, 40.5 - abs(z) * 0.3, 22, 7.6 - abs(z) * 0.2, mid=34, n=16) for z in (-5, -2.5, 0, 2.5, 5)]
    m.add("chest", lof("torso", "robe", torso), core=True)
    m.add("belt", ell("torso", "trim", (0, 24, 0), (8.0, 1.6, 6.6), segs=14, rings=2))
    m.add("stole", lof("torso", "trim", [oct_ring((0, 39.6, 0), 15.6, 11.0, R=R_(90, 0, 0)), oct_ring((0, 38.0, 0), 16.6, 11.8, R=R_(90, 0, 0))]))
    # 胸の潮の玉と、まわりの金の枠
    m.add("orb", ell("torso", "glow", (0, 33.5, -6.6), (2.4, 2.4, 1.8), segs=10, rings=3))
    m.add("orb_frame", lof("torso", "trim", [oct_ring((0, 33.5, -6.2), 6.4, 6.4), oct_ring((0, 33.5, -5.6), 6.8, 6.8)]))
    # 肩の貝の当て
    for s, side in ((1, "l"), (-1, "r")):
        for k in range(3):
            m.add(f"shell_{side}{k}", ell("torso", "shell", (s * (8.6 - k * 0.6), 40.4 - k * 1.6, 0.4 - k * 0.2), (4.0 - k * 0.6, 1.4, 4.0 - k * 0.6), segs=10, rings=2))
            for r_ in range(5):
                a = math.radians(r_ * 36 - 72)
                m.add(f"rib_{side}{k}{r_}", tub("torso", "shell", [(s * (8.6 - k * 0.6), 41.6 - k * 1.6, 0.4), (s * (8.6 - k * 0.6) + math.sin(a) * 3.4 * s, 40.9 - k * 1.6, 0.4 + math.cos(a) * 3.4)],
                                                 [0.35, 0.25], None, sides=3))
    # ---- 珊瑚: 右の肩から首・頭へ呑み込む塊と枝、背の大きな枝
    m.add("coral_mass", ell("torso", "coral", (-4.6, 40.0, 1.6), (5.4, 4.4, 5.0), segs=11, rings=3))
    m.add("coral_mass2", ell("torso", "coral2", (-1.4, 42.6, 3.2), (3.6, 3.0, 3.6), segs=9, rings=3))
    for k, (b, d, L, r) in enumerate((((-6.0, 42.0, 3.0), (-0.5, 1, 0.4), 9.0, 1.4), ((-3.0, 43.0, 4.6), (0.1, 1, 0.6), 11.0, 1.5),
                                     ((-8.4, 40.0, 1.0), (-1, 0.6, 0.0), 7.0, 1.2), ((0.6, 42.0, 5.4), (0.5, 1, 0.8), 8.0, 1.2),
                                     ((3.6, 38.0, 5.6), (0.6, 0.7, 1), 6.0, 1.0))):
        branch(m, f"cbranch{k}", "torso", "coral" if k % 2 == 0 else "coral2", b, d, L, r, forks=2 if L > 7 else 1, seed=k)
    # ---- 頭: 頭巾の影の中の珊瑚の仮面、光る目、頭の珊瑚の冠
    head = np.array([0, 45.5, -1.2])
    m.add("neck", ell("head", "skin", (0, 41.6, -0.6), (2.6, 2.4, 2.6), segs=9, rings=2))
    m.add("hood", lof("head", "robe", [oct_ring(head + np.array([0, -2.6, 1.2]), 9.4, 9.0, R=R_(90, 0, 0)),
                                       oct_ring(head + np.array([0, 1.0, 0.8]), 9.0, 9.6, R=R_(90, 0, 0)),
                                       oct_ring(head + np.array([0, 4.6, 1.4]), 6.4, 7.0, R=R_(90, 0, 0)),
                                       oct_ring(head + np.array([0, 6.2, 2.4]), 2.4, 2.6, R=R_(90, 0, 0))]))
    m.add("face", ell("head", "skin", head + np.array([0, 0, -1.0]), (3.0, 3.4, 3.0), segs=10, rings=3), core=True)
    m.add("mask", lof("head", "coral", [oct_ring(head + np.array([0, 0.2, -3.2]), 5.6, 6.4), oct_ring(head + np.array([0, 0.4, -4.2]), 5.0, 5.6),
                                        oct_ring(head + np.array([0, 0.6, -4.7]), 3.8, 4.2)]))
    for s in (1, -1):
        eye(m, "head", head + np.array([s * 1.3, 1.0, -4.6]), 0.7, (s * 0.2, 0, -1), (120, 250, 240), glow=True)
        m.add(f"mask_horn{s}", cone("head", "coral2", head + np.array([s * 2.0, 2.6, -3.8]), (s * 0.6, 1, -0.2), 3.0, 0.7, sides=5, rings=2, bend=(s * 0.4, 0.4, 0.6)))
    for k in range(5):
        a = math.radians(200 + k * 35)
        p = head + np.array([math.cos(a) * 3.6, 4.0, math.sin(a) * 2.6 + 1.4])
        branch(m, f"crown{k}", "head", "coral" if k % 2 else "coral2", p, (math.cos(a) * 0.4, 1, math.sin(a) * 0.3 + 0.2), 4.0 + (k % 2) * 1.6, 0.7, forks=1, seed=k + 9)
    # ---- 腕: 広い袖、細い腕 (肌は潮で青白い)、手。右手は三叉の槍
    for s, side in ((1, "l"), (-1, "r")):
        sh = np.array([s * 9.0, 38, 0])
        el = np.array([s * 11.0, 28.5, -2])
        wr = np.array([s * 11.0, 21.5, -4.6])
        m.add(f"upper_{side}", limb(f"arm_{side}", "robe", [sh, (sh + el) / 2, el], [5.0, 5.4, 5.8], [5.0, 5.4, 5.8], n=10))
        m.add(f"sleeve_{side}", lof(f"fore_{side}", "robe", [oct_ring(el, 6.0, 6.0, R=R_(90, 0, 0)), oct_ring((el + wr) / 2 + np.array([0, -0.6, 0]), 8.4, 8.0, R=R_(90, 0, 0)),
                                                             oct_ring(wr + np.array([0, 1.6, 0.4]), 9.2, 8.6, R=R_(90, 0, 0))]))
        m.add(f"cuff_{side}", lof(f"fore_{side}", "trim", [oct_ring(wr + np.array([0, 1.4, 0.4]), 9.4, 8.8, R=R_(90, 0, 0)), oct_ring(wr + np.array([0, 0.8, 0.4]), 9.4, 8.8, R=R_(90, 0, 0))]))
        m.add(f"wrist_{side}", limb(f"fore_{side}", "skin", [wr + np.array([0, 2.0, 0]), wr - np.array([0, 1.2, 0])], [2.6, 2.4], [2.6, 2.4], n=8))
        m.add(f"hand_{side}", ell(f"fore_{side}", "skin", wr + np.array([0, -1.8, -0.4]), (1.6, 2.0, 1.6), segs=8, rings=2))
        for k in range(3):
            m.add(f"finger_{side}{k}", cone(f"fore_{side}", "skin", wr + np.array([s * (k - 1) * 0.6, -2.6, -0.8]), (0, -1, -0.3), 2.0, 0.45, sides=4))
        # 海藻の垂れ (袖から)
        for k in range(3):
            base = wr + np.array([s * (2.6 - k * 1.8), 0.6, 2.6 - k])
            m.add(f"kelp_{side}{k}", tub(f"fore_{side}", "kelp", [base, base + np.array([0.3, -3.0, 0.4]), base + np.array([-0.2, -6.0 - k, 0.8])], [0.5, 0.4, 0.2], None, sides=3))
    grip = np.array([-11.0, 19.7, -5.0])
    m.add("shaft", tub("fore_r", "wood", [grip + np.array([0, -16, 4.0]), grip + np.array([0, 18, -4.0])], [1.0, 1.0], None, sides=7))
    top = grip + np.array([0, 18, -4.0])
    m.add("spear_collar", ell("fore_r", "trim", top, (1.4, 1.4, 1.4), segs=8, rings=2))
    dv = nrm(np.array([0, 16, -8.0]))
    for k, off in enumerate((-2.4, 0, 2.4)):
        b = top + np.array([0, 0.6, 0]) + np.array([0, 0, 0])
        m.add(f"prong{k}", tub("fore_r", "shell", [b + np.array([off * 0.3, 0, 0]), b + np.array([off * 1.4, 2.6, -1.2]), b + dv * (9 if k == 1 else 7.5) + np.array([off * 1.3, 0, 0])],
                                [0.8, 0.7, 0.2], None, sides=5))
    m.add("spear_glow", ell("fore_r", "glow", top + dv * 2.6, (0.9, 0.9, 0.9), segs=7, rings=2))
    for k in range(4):
        m.add(f"shaft_coral{k}", ell("fore_r", "coral2" if k % 2 else "coral", grip + np.array([0.5, -6 + k * 4.5, 1.6 - k * 1.1]), (0.9, 1.2, 0.9), segs=6, rings=2))
    m.add("spear_kelp", tub("fore_r", "kelp", [top + np.array([0.6, -1, 0]), top + np.array([1.2, -5, 0.6]), top + np.array([0.8, -9, 1.0])], [0.5, 0.35, 0.15], None, sides=3))

    m.pal = dict(mats={"eye": M_(GLOW, base=0.7, lit=0.2, noise=0.0, min=1), "robe": M_(ROBE, lit=0.32, streak=0.05), "trim": M_(TRIM, lit=0.4),
                       "coral": M_(CORAL, lit=0.36), "coral2": M_(CORAL2, lit=0.36), "shell": M_(SHELL, lit=0.4), "barnacle": M_(BARN, lit=0.3),
                       "kelp": M_(KELP, lit=0.3), "skin": M_(SKIN, lit=0.3), "wood": M_(WOOD, streak=0.08), "glow": M_(GLOW, base=0.7, lit=0.2, min=1)},
                 glow_mats=["eye", "glow"])

    def detail(c, col, glow):
        P = c["P"]
        sel = np.isin(c["mats"], ["coral", "coral2"]) & c["owned"]
        f1, f2, idv = cell3(P, 1.4, 5)
        pores = sel & (f1 < 0.18)
        col = np.where(pores[..., None], (col * 0.6).astype(col.dtype), col)
        col = speckle(c, col, ["robe"], (150, 190, 180), density=0.02)
        col = edge_dark(c, col, ["trim", "shell"], k=0.72)
        return col, glow
    m.detail = detail
    m.hint = 512
    m.view = dict(center=(0, 26, 0), scale=2.4)
    m.gaits = gaits()
    return m


def gaits():
    g = []
    idle = Gait("idle", 4.0)
    idle.pos("body", None, W(0.6).s(0.6, 1, 0, "t"))
    idle.rot("torso", W(0).s(1.5, 1, 30, "t"), W(0).s(2.0, 1, 0, "t"))
    idle.rot("head", W(0).s(3.0, 1, 90, "t"))
    idle.rot("arm_l", W(-6).s(3.0, 1, 0, "t"), None, W(8))
    idle.rot("arm_r", W(-10).s(3.0, 1, 90, "t"), None, W(-4))
    idle.rot("robe", None, W(0).s(3.0, 1, 0, "t"))
    g.append(idle)
    walk = Gait("walk", 2.0)
    walk.pos("body", None, W(0.8).s(0.7, 2, 0, "t"))
    walk.rot("torso", W(6), W(0).s(3.0, 1, 0, "t"))
    walk.rot("robe", W(-4), W(0).s(5.0, 1, 0, "t"), W(0).s(2.0, 1, 90, "t"))
    walk.rot("arm_l", W(-8).s(4.0, 1, 0, "t"), None, W(10))
    walk.rot("arm_r", W(-14).s(4.0, 1, 180, "t"))
    g.append(walk)
    rest = {}
    pull = {"torso": {"rot": (-6, 20, 0)}, "arm_r": {"rot": (10, 20, -10)}, "fore_r": {"rot": (-10, 0, 0)}, "arm_l": {"rot": (-50, 0, 20)}}
    stab = {"torso": {"rot": (16, -10, 0)}, "arm_r": {"rot": (-80, -10, 0)}, "fore_r": {"rot": (-10, 0, 0)}, "arm_l": {"rot": (20, 0, 20)}, "robe": {"rot": (-6, 0, 0)}}
    g.append(keys("thrust", 1.2, [(0.0, rest), (0.7, pull), (0.85, stab), (0.95, stab), (1.2, rest)]))
    up = {"arm_l": {"rot": (-150, 0, 20)}, "arm_r": {"rot": (-150, 0, -20)}, "head": {"rot": (-20, 0, 0)}, "torso": {"rot": (-8, 0, 0)}, "body": {"pos": (0, 2, 0)}}
    spin = dict(up, robe={"rot": (0, 60, 0)}, torso={"rot": (-8, 40, 0)})
    g.append(keys("whirl", 1.6, [(0.0, rest), (0.6, up), (1.0, spin), (1.3, up), (1.6, rest)]))
    spread = {"arm_l": {"rot": (-80, 0, 50)}, "arm_r": {"rot": (-80, 0, -50)}, "head": {"rot": (-14, 0, 0)}, "body": {"pos": (0, 3, 0)}}
    turn = dict(spread, torso={"rot": (0, 180, 0)})
    full = dict(spread, torso={"rot": (0, 359, 0)})
    g.append(keys("flood", 1.6, [(0.0, rest), (0.4, spread), (0.8, turn), (1.2, full), (1.6, rest)]))
    g.append(keys("hurt", 0.3, [(0.0, rest), (0.1, {"torso": {"rot": (-8, 0, 4)}, "head": {"rot": (-10, 0, 0)}}), (0.3, rest)]))
    sag = {"body": {"pos": (0, -4, 0)}, "torso": {"rot": (30, 0, 0)}, "head": {"rot": (30, 0, 0)}, "arm_l": {"rot": (20, 0, 10)}, "arm_r": {"rot": (20, 0, -10)}}
    sink = {"body": {"pos": (0, -16, 0)}, "torso": {"rot": (50, 0, 0)}, "head": {"rot": (30, 0, 0)}, "arm_l": {"rot": (40, 0, 30)}, "arm_r": {"rot": (40, 0, -30)},
            "robe": {"rot": (0, 0, 0)}}
    g.append(keys("death", 2.0, [(0.0, rest), (0.6, sag), (1.6, sink), (2.0, sink)], hold=True))
    return g


POSES = [("idle", 0.0), ("walk", 0.5), ("thrust", 0.7), ("thrust", 0.85), ("whirl", 1.0), ("flood", 0.8), ("hurt", 0.1), ("death", 2.0)]
BOUNDS = (6, 6)
