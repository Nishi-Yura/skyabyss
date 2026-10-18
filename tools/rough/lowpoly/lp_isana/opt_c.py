"""C 案: 若い旅人。短い青緑のジャケット・麦わら色の細いつばの帽子・長い黄の襟巻き・小さな荷と望遠鏡 (軽装)。"""
from __future__ import annotations

from chaser import *  # noqa
from opt_a import M, METAL, SOLE, HAIR

NAME = "C 若い旅人"
HINT = 128

SKINC = [(104, 62, 48), (150, 98, 74), (190, 128, 96), (216, 154, 118), (236, 180, 144)]
STRAW = [(110, 80, 40), (150, 114, 58), (188, 150, 80), (220, 188, 112), (240, 214, 148)]
STRAWD = [(80, 56, 28), (116, 84, 40), (150, 112, 56)]
CORD = [(130, 54, 22), (186, 84, 28), (228, 124, 42), (250, 176, 78)]
JAC = [(24, 58, 62), (40, 88, 92), (62, 120, 118), (92, 154, 142), (132, 186, 168)]
JACD = [(18, 40, 46), (30, 62, 68), (46, 88, 92)]
MUSTARD = [(150, 80, 22), (204, 120, 28), (240, 164, 48), (255, 204, 96)]
TRC = [(70, 62, 52), (104, 94, 78), (138, 126, 104), (170, 158, 132)]
BOOTC = [(44, 28, 20), (76, 50, 32), (110, 76, 46), (146, 104, 64)]
LEATH = [(60, 36, 22), (96, 62, 38), (136, 92, 56), (172, 124, 78)]
CANV = [(120, 110, 84), (168, 156, 120), (210, 198, 160), (238, 228, 192)]

PAL = dict(mats={
    "skin": M(SKINC, base=0.68, lit=0.22, noise=0.03, facevar=0.02, min=1, ao=0.0),
    "hair": M([(54, 34, 22), (88, 58, 34), (124, 84, 48), (160, 116, 68)], noise=0.1),
    "hat": M(STRAW, noise=0.12, streak=0.16, stripe=3, axis="u"), "hat_rim": M(STRAW, base=0.45), "hat_dk": M(STRAWD, base=0.55),
    "band": M(CORD, base=0.6), "coat": M(JAC, noise=0.08, streak=0.08, base=0.6), "coat_dk": M(JACD, base=0.55),
    "cuff": M(LEATH, base=0.5), "belt": M(LEATH, base=0.5), "metal": M(METAL), "trousers": M(TRC, noise=0.1),
    "boot": M(BOOTC, noise=0.08), "sole": M(SOLE), "pack": M(LEATH, noise=0.1, streak=0.1), "pack_dk": M(LEATH, base=0.46),
    "roll": M(CANV, base=0.55, stripe=3, axis="u"), "roll_end": M(CANV, base=0.42), "scarf": M(MUSTARD, stripe=3, axis="v", noise=0.06),
    "glass": M(METAL, base=0.7), "brass": M([(90, 60, 20), (150, 108, 34), (212, 168, 62), (250, 220, 130)], base=0.62),
    "lens": M([(90, 160, 190), (150, 210, 230), (210, 244, 250)], base=0.7),
})

CFG = dict(head_cz=-0.3, eye_y=22.3, eye_w=1.1, eye_h=0.5, stubble=0.1, stubble_col=(140, 92, 72), brow_tilt=0.04,
           brow_t=0.36, brow_dy=1.25, smile=0.12, freckle=0.16, hat_shade_y=23.8, iris=(34, 44, 52), mouth_w=1.4,
           brim_w=11.4, brim_d=11.6, droop=0.5, side_up=0.3, crown_h=4.5, crown_w=7.6, crown_taper=1.0, band_mat="band",
           chest_w=8.6, shoulder_w=9.2, waist_w=7.6, hem_y=8.8, hem_w=9.4, hem_d=6.8, skirt_mid_dy=0.0, boot_top=4.8,
           sleeve_r=1.55, sleeve_rw=1.3, pack_w=6.2, pack_h=6.4, pack_y=13.2, pack_z=4.8, bedroll=True, hair_len=4.5,
           nose_len=0.9, nose_r=0.75)


def build():
    c = CFG
    F = []
    F += head(c) + hair(c)
    F += hat(c)
    F += torso(c) + skirt(dict(c, skirt_mat="coat"))
    F += belt(c, 10.9, 12.0)
    # ジャケットの襟 (折り返し)
    F += rloft("body", "coat_dk", [oc(19.3, 8.4, 5.8, 0, 0.0, 0.45), oc(20.5, 7.6, 5.4, 0, 0.0, 0.45)], caps=(False, False))
    # 襟巻き: ゆるい輪 + 前に短い尾 + 背へ流れる長い尾
    F += rloft("body", "scarf", [oc(19.5, 7.6, 6.2, 0, 0.0, 0.45), oc(20.8, 6.8, 5.8, 0, 0.0, 0.45)], caps=(False, False))
    F += tail(1.0, 19.8, 13.2, -3.05, 1.9, notch=0.9)
    F += tail(-4.1, 19.6, 6.2, 3.2, 1.9, notch=1.6, depth=0.45)
    # 腰の望遠鏡 (斜め)
    path = [np.array([-4.6, 8.6, -1.8]), np.array([-1.4, 13.2, -3.7])]
    F += tube("body", "brass", path, [0.9, 0.7], sides=6, hint=(0, 0, 1), phase=0.0, caps=(True, True))
    F += tube("body", "lens", [path[1], path[1] + (path[1] - path[0]) * 0.12], 0.62, sides=6, hint=(0, 0, 1),
              phase=0.0, caps=(False, True))
    for bone, s in (("armR", 1), ("armL", -1)):
        F += arm(bone, s, dict(c, sleeve_mat="coat", cuff_mat="cuff"))
    for bone, s in (("legR", 1), ("legL", -1)):
        F += leg(bone, s, c)
    F += pack(c)
    return dict(body=F, eyes=[], extra=face_painter(c), gear={}, gear_extra={}, gear_pal=None)
