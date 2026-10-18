"""B 案: 毛糸の語り部 — もっと年老いて、あたたかい祖母。丸くやわらかい頭巾 (頂に小さな房)、ふくよかな頬と赤み、
笑いじわ、まるい肩、房飾りの肩掛け、前掛けと、手に毛糸玉。色は藍を少しあたたかく、長衣は茶。"""
from __future__ import annotations

from char import *  # noqa

NAME = "B 毛糸の語り部"
HINT = 128

NAVY = [(20, 24, 64), (34, 42, 96), (50, 62, 128), (72, 86, 158), (98, 112, 186)]
NAVY2 = [(26, 30, 74), (42, 50, 106), (60, 72, 138), (84, 98, 168), (112, 126, 196)]
TRIM = [(36, 42, 96), (56, 68, 124), (80, 94, 154), (110, 124, 184), (146, 158, 208)]
ROBE = [(70, 50, 42), (104, 78, 62), (140, 108, 82), (174, 140, 108), (204, 172, 136)]
HEM = [(56, 40, 34), (86, 64, 50), (118, 90, 68), (150, 118, 90)]
APRON = [(120, 104, 84), (164, 148, 120), (204, 188, 156), (232, 218, 186), (250, 240, 214)]
FRINGE = [(150, 124, 96), (190, 164, 128), (222, 200, 160), (244, 228, 190)]
YARN = [(110, 28, 28), (160, 44, 40), (204, 70, 58), (232, 108, 88), (248, 150, 120)]
BELT = [(70, 40, 18), (116, 72, 30), (160, 106, 44), (200, 142, 70), (232, 180, 104)]
SK = [(130, 76, 62), (180, 114, 90), (222, 154, 118), (242, 182, 142), (252, 208, 170)]

PAL = dict(
    mats={
        "robe": dict(ramp=ROBE, base=0.58, lit=0.30, noise=0.10, facevar=0.05, crack=0, min=0, streak=0.12, ao=0.10),
        "hem": dict(ramp=HEM, base=0.60, lit=0.30, noise=0.12, facevar=0.05, crack=0, min=0, streak=0.1),
        "belt": dict(ramp=BELT, base=0.55, lit=0.30, noise=0.08, crack=0, min=0, stripe=2, axis="u"),
        "cordm": dict(ramp=BELT, base=0.58, lit=0.30, noise=0.08, crack=0, min=0, facevar=0.05),
        "shawl": dict(ramp=NAVY2, base=0.55, lit=0.32, noise=0.08, facevar=0.05, crack=0, min=0, stripe=4, axis="v"),
        "hood": dict(ramp=NAVY, base=0.55, lit=0.34, noise=0.08, facevar=0.06, crack=0, min=0, streak=0.10),
        "rim": dict(ramp=TRIM, base=0.55, lit=0.32, noise=0.06, facevar=0.04, crack=0, min=0, stripe=3, axis="u"),
        "skin": dict(ramp=SK, base=0.88, lit=0.06, noise=0.0, facevar=0.0, crack=0, min=1, streak=0.0, ao=0.04),
        "white": dict(ramp=WHITE, base=0.66, lit=0.30, noise=0.04, facevar=0.03, crack=0, min=1, streak=0.0),
        "sleeve": dict(ramp=ROBE, base=0.58, lit=0.30, noise=0.10, facevar=0.05, crack=0, min=0, streak=0.10),
        "cuff": dict(ramp=APRON, base=0.58, lit=0.30, noise=0.06, crack=0, min=0, stripe=2, axis="v"),
        "boot": dict(ramp=BOOT, base=0.55, lit=0.30, noise=0.1, crack=0, min=0),
        "apron": dict(ramp=APRON, base=0.60, lit=0.30, noise=0.05, facevar=0.03, crack=0, min=0, stripe=4, axis="v"),
        "pocket": dict(ramp=FRINGE, base=0.60, lit=0.30, noise=0.05, crack=0, min=0),
        "fringe": dict(ramp=FRINGE, base=0.62, lit=0.30, noise=0.05, crack=0, min=0),
        "yarn": dict(ramp=YARN, base=0.58, lit=0.30, noise=0.06, crack=0, min=0, stripe=2, axis="u"),
        "bobble": dict(ramp=YARN, base=0.60, lit=0.30, noise=0.06, crack=0, min=0),
    },
    glow_mats=[],
)

CFG = dict(
    robe=[(1.2, 6.6, -5.0, 4.8, 0), (4.5, 5.9, -4.8, 4.6, 0), (8.5, 5.1, -4.6, 4.3, 0), (11.8, 4.6, -4.3, 4.2, 0),
          (13.8, 4.8, -4.1, 4.7, 0)],
    robe_mats=["hem", "robe", "robe", "robe"],
    belt_d=0.45,
    shawl=[(12.4, 5.7, -4.9, 4.2, 0), (15.0, 6.3, -5.1, 4.5, 0), (17.0, 5.2, -4.3, 4.0, 0), (18.4, 3.3, -2.8, 3.0, -0.2)],
    hood=[(17.0, 3.6, -3.8, 3.8, 1.3, 0.0), (18.6, 4.8, -4.3, 4.8, 1.8, 0), (21.0, 5.1, -4.3, 5.4, 2.0, 0),
          (23.6, 4.8, -4.2, 5.2, 2.0, 0.1), (25.6, 4.0, -3.8, 4.6, 1.7, 0.2), (27.2, 2.6, -2.8, 3.4, 1.2, 0.4),
          (28.2, 1.2, -1.4, 1.8, 0.6, 0.6)],
    rim=[(2.4, 17.4, -4.6, 1.0), (3.8, 19.0, -5.1, 1.2), (4.4, 21.0, -5.3, 1.3), (4.0, 23.2, -5.0, 1.2),
         (2.8, 24.8, -4.7, 1.1), (1.2, 25.6, -4.4, 1.0), (0.0, 25.9, -4.2, 1.0)],
    face=[(17.9, 1.8, -1.4, 1.1), (19.0, 2.9, -2.0, 1.5), (20.5, 3.4, -2.4, 1.7), (22.3, 3.4, -2.3, 1.7),
          (24.0, 2.7, -1.8, 1.4), (24.9, 1.8, -1.2, 1.0)],
    nose=(0.0, 20.5, -5.45, 1.0, 0.75),
    brow=(0.6, 3.0, 23.15, 22.7, -5.65), brow_mid=0.3, brow_r=0.55,
    tufts=[(21.2, 2.6, 0.55), (20.0, 3.4, 0.55), (18.8, 3.0, 0.5)], tuft_x=4.5, tuft_z=-5.2,
    sleeve=[2.1, 2.3, 2.1], cuff=(2.2, 2.3),
)

FACE = dict(pats=[
    ([".DD.", "DHPD", "dPPd"], 2, 43),
    (["..M", "MM."], 0, 38),
    (["k"], 4, 38),                              # えくぼ
    (["ww", ".w"], 6, 42), (["w."], 6, 40),      # 目じりの笑いじわ
    (["w"], 2, 36),                              # 口の脇
], blush=(2.5, 19.7, 0.9, 0.7, 0.32))


def build():
    F = base_body(CFG)
    # 丸い頭巾の頂の房
    F += ellipsoid("hair2", "bobble", (0, 28.9, 0.7), (1.3, 1.1, 1.3), segs=6, rings=2)
    # 肩掛けの房飾り (前の半周)
    sp = CFG["shawl"]
    for x in np.linspace(-5.0, 5.0, 7):
        z = front_z(sp, 12.4, x, 0.2)
        # 肩掛けの前の縁は最下段の輪の上
        F += spike("shawl", "fringe", (x, 12.6, z), (0, -1, -0.15), 2.5, 0.6, sides=3, rings=1, hint=(0, 0, 1))
    # 前掛け (長衣の前に、下ほど広い)
    rows = []
    for y, w in ((9.0, 2.7), (5.4, 3.5), (2.4, 4.1)):
        rows.append([np.array([x, y, front_z(CFG["robe"], y, x, 0.35)]) for x in (-w, -w / 3, w / 3, w)])
    F += grid("body", "apron", rows, np.array([0, 5, 3.0]))
    # 前掛けのポケット
    pz = lambda x, y: front_z(CFG["robe"], y, x, 0.35 + 0.35)
    F += poly("body", "pocket", [np.array([-1.7, 3.2, pz(-1.7, 3.2)]), np.array([1.7, 3.2, pz(1.7, 3.2)]),
                                  np.array([1.5, 6.0, pz(1.5, 6.0)]), np.array([-1.5, 6.0, pz(-1.5, 6.0)])],
              np.array([0, 4, 3.0]))
    # 手の毛糸玉
    F += ellipsoid("armR", "yarn", (0.0, 11.6, -6.4), (1.6, 1.5, 1.6), segs=6, rings=2, rot=rot_y(20))
    return dict(body=F, eyes=[], extra=extra)


def extra(c, col, glow):
    col, glow = face_paint(c, col, glow, FACE)
    # 前掛けの縦の縞を細い糸でふち取る (ポケットの縁)
    P, bay, owned = c["P"], c["bay"], c["owned"]
    m = owned & (c["mats"] == "pocket") & (np.floor(P[..., 1] * 2) % 6 == 0)
    col = np.where(m[..., None], np.array(APRON[1], float), col)
    return col, glow
