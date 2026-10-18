"""A 案: つば広帽の旅人 (今に忠実)。深緑の外套・赤い襟巻き・茶の帽子に白い羽・茶の荷と寝袋。"""
from __future__ import annotations

from chaser import *  # noqa

NAME = "A つば広帽の旅人"
HINT = 128

SKIN = [(86, 48, 38), (128, 80, 60), (170, 108, 80), (200, 136, 102), (220, 160, 124)]
HAT = [(36, 22, 14), (62, 38, 22), (92, 58, 34), (122, 82, 50), (150, 108, 70)]
HATD = [(24, 14, 10), (42, 26, 16), (62, 40, 24), (86, 58, 36)]
BAND = [(112, 98, 80), (164, 150, 124), (212, 202, 176), (238, 230, 208)]
COAT = [(24, 36, 26), (42, 60, 42), (62, 86, 58), (84, 110, 74), (108, 134, 94)]
CUFF = [(44, 30, 20), (68, 48, 32), (96, 70, 46), (126, 96, 64)]
SCARF = [(66, 14, 16), (116, 24, 24), (166, 38, 34), (202, 62, 50), (230, 94, 72)]
TRO = [(64, 66, 74), (98, 100, 108), (132, 134, 140), (164, 166, 168)]
BOOT = [(30, 18, 12), (54, 34, 20), (84, 56, 32), (116, 80, 48)]
SOLE = [(18, 14, 12), (34, 28, 24), (52, 44, 38)]
PACK = [(40, 26, 18), (68, 46, 30), (98, 68, 44), (130, 94, 62), (158, 120, 82)]
METAL = [(50, 50, 56), (96, 96, 104), (150, 150, 156), (206, 204, 198)]
HAIR = [(24, 18, 16), (44, 34, 28), (68, 52, 40), (92, 72, 54)]
CREAM = [(120, 110, 92), (170, 160, 138), (212, 204, 182), (240, 234, 216)]
FEATH = [(150, 146, 136), (196, 192, 180), (230, 226, 214), (250, 248, 238)]


def M(r, **k):
    d = dict(ramp=r, base=0.62, lit=0.28, noise=0.06, facevar=0.05, crack=0, min=0, streak=0.0, ao=0.06)
    d.update(k)
    return d


PAL = dict(mats={
    "skin": M(SKIN, base=0.66, lit=0.22, noise=0.035, facevar=0.02, min=1, ao=0.0),
    "hair": M(HAIR), "hat": M(HAT, noise=0.09, streak=0.06), "hat_rim": M(HAT, base=0.45), "hat_dk": M(HATD, base=0.5),
    "band": M(BAND, base=0.58, lit=0.25), "coat": M(COAT, noise=0.09, streak=0.1, stripe=4, axis="u", base=0.6),
    "cuff": M(CUFF), "belt": M(CUFF, base=0.5), "metal": M(METAL), "trousers": M(TRO, noise=0.08),
    "boot": M(BOOT, noise=0.08), "sole": M(SOLE), "pack": M(PACK, noise=0.1, streak=0.1), "pack_dk": M(PACK, base=0.46),
    "roll": M(BAND, base=0.5, stripe=3, axis="u"), "roll_end": M(CREAM, base=0.45), "strap": M(CUFF, base=0.6),
    "scarf": M(SCARF, stripe=3, axis="v", noise=0.06), "feather": M(FEATH, base=0.7),
})

CFG = dict(head_cz=-0.3, eye_y=22.3, stubble=0.4, stubble_col=(98, 66, 54), brow_tilt=0.18, eye_h=0.4, smile=-0.1)


def tail(x, y_top, y_bot, z, w, notch=1.0, depth=0.5, mat="scarf"):
    pts = [np.array([x - w / 2, y_top, z]), np.array([x + w / 2, y_top, z]), np.array([x + w / 2 + 0.1, y_bot, z - 0.35]),
           np.array([x, y_bot + notch, z - 0.4]), np.array([x - w / 2 - 0.1, y_bot, z - 0.35])]
    return extrude("body", mat, pts, (0, 0, -depth), caps=(True, True))


def build():
    c = CFG
    F = []
    F += head(c) + hair(c)
    F += hat(c)
    # 羽
    F += spike("hat", "feather", (3.9, 25.7, -1.2), (0.35, 0.55, 0.8), 3.4, 0.55, sides=3, rings=1, hint=(1, 0, 0))
    F += torso(c) + skirt(c) + belt(c)
    # 襟巻き (首の輪 + 前へ垂れる尾 2 本)
    F += rloft("body", "scarf", [oc(19.4, 7.2, 6.0, 0, 0.0, 0.45), oc(20.7, 6.6, 5.6, 0, 0.0, 0.45)], caps=(False, False))
    F += tail(1.1, 19.6, 11.8, -3.0, 2.0)
    F += tail(-0.7, 19.3, 14.4, -3.15, 1.6, notch=0.8, depth=0.4)
    for bone, s in (("armR", 1), ("armL", -1)):
        F += arm(bone, s, dict(c, sleeve_mat="coat", cuff_mat="cuff"))
    for bone, s in (("legR", 1), ("legL", -1)):
        F += leg(bone, s, c)
    F += pack(c)
    return dict(body=F, eyes=[], extra=face_painter(c), gear={}, gear_extra={}, gear_pal=None)
