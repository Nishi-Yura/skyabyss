"""B 案: 荒くれの鯨獲り。紺の油布の長い外套 (ぎざぎざの裾・高い襟)・くたびれた低い帽子・銛の綱を胸に斜めに掛け、背に銛と綱の束。"""
from __future__ import annotations

from chaser import *  # noqa
from opt_a import M, SKIN, METAL, SOLE, BOOT, TRO, HAIR, CUFF

NAME = "B 荒くれの鯨獲り"
HINT = 128

HATB = [(26, 24, 22), (48, 44, 38), (76, 70, 58), (106, 98, 80), (136, 126, 102)]
HATBD = [(16, 15, 14), (30, 28, 24), (48, 44, 38), (70, 64, 52)]
BANDB = [(40, 30, 24), (70, 54, 40), (104, 84, 60), (140, 116, 84)]
OIL = [(14, 22, 32), (24, 38, 54), (38, 58, 78), (56, 82, 104), (80, 108, 128)]
WOOL = [(40, 42, 46), (68, 72, 76), (98, 102, 106), (130, 134, 136)]
ROPE = [(70, 52, 32), (112, 86, 52), (154, 124, 78), (194, 164, 112)]
WOODB = [(34, 24, 18), (58, 42, 30), (88, 66, 46), (118, 92, 64)]
SKINB = [(76, 42, 32), (114, 70, 52), (150, 96, 70), (178, 120, 88), (198, 140, 104)]
BONEW = [(110, 100, 86), (160, 148, 126), (206, 196, 172), (236, 228, 206)]

PAL = dict(mats={
    "skin": M(SKINB, base=0.64, lit=0.22, noise=0.04, facevar=0.02, min=1, ao=0.0),
    "hair": M(HAIR), "hat": M(HATB, noise=0.12, streak=0.12), "hat_rim": M(HATB, base=0.42), "hat_dk": M(HATBD, base=0.5),
    "band": M(BANDB), "coat": M(OIL, noise=0.1, streak=0.14, stripe=5, axis="u", base=0.58),
    "coat_hi": M(OIL, base=0.64, noise=0.08, stripe=5, axis="u"),
    "cuff": M(CUFF, base=0.5), "belt": M(CUFF, base=0.46), "metal": M(METAL), "trousers": M(TRO, base=0.5, noise=0.1),
    "boot": M(BOOT, base=0.5, noise=0.1), "sole": M(SOLE), "pack": M(WOODB, noise=0.1, streak=0.1),
    "pack_dk": M(WOODB, base=0.46), "roll": M(ROPE, base=0.56, stripe=2, axis="u"), "roll_end": M(ROPE, base=0.4),
    "rope": M(ROPE, base=0.58, stripe=2, axis="v", noise=0.08), "scarf": M(WOOL, noise=0.08, stripe=3, axis="u"),
    "shaft": M(WOODB, base=0.6, stripe=3, axis="v"), "iron": M(METAL, base=0.62), "bone": M(BONEW, base=0.6),
})

CFG = dict(head_cz=-0.3, eye_y=22.2, eye_w=1.0, eye_h=0.34, stubble=0.62, stubble_col=(108, 98, 92), brow_tilt=0.3,
           brow_t=0.5, brow_dy=1.0, smile=-0.3, scar=True, age_lines=True, hat_shade_y=23.1, mouth_w=1.7,
           droop=1.5, brim_w=12.6, brim_d=12.8, crown_h=3.4, crown_w=7.9, crown_taper=1.5, side_up=0.9, band_mat="band",
           chest_w=9.4, shoulder_w=10.0, hem_y=3.6, hem_w=11.0, hem_d=7.8, skirt_mid_dy=0.3, boot_top=3.6,
           sleeve_r=1.75, ears=False, pack_w=7.6, pack_h=8.4, pack_y=14.2, bedroll=False, nose_len=1.2, nose_r=0.9,
           hair_len=2.0)


def rope_loop(path, r=0.5, sides=4, bone="body"):
    return tube(bone, "rope", [np.array(p, float) for p in path], r, sides=sides, hint=(0, 1, 0), phase=45.0,
                caps=(False, False))


def build():
    c = CFG
    F = []
    F += head(c)
    F += hat(c)
    F += torso(c)
    jag = [0.0, -0.9, 0.0, -0.7, 0.0, -0.9, 0.0, -0.7]
    F += skirt(dict(c, jag=jag, skirt_mat="coat"))
    F += belt(c, mat="belt")
    # 高い襟 (外へ開く)
    F += rloft("body", "coat_hi", [oc(19.2, 8.6, 6.2, 0, 0.0, 0.45), oc(21.6, 9.8, 7.4, 0, 0.1, 0.45)], caps=(False, False))
    # 灰色の襟巻き (襟の内側に詰めた輪)
    F += rloft("body", "scarf", [oc(19.0, 7.0, 5.8, 0, 0.0, 0.45), oc(20.4, 6.4, 5.4, 0, 0.0, 0.45)], caps=(False, False))
    F += tail(0.6, 19.6, 14.8, -3.1, 2.2, notch=1.0, depth=0.5)
    # 骨の留め具 (トグル)
    F += hpipe("body", "bone", (0.0, 17.0, -3.35), (0.0, 14.6, -3.35), 0.35, 0.35, n=4, rot=45.0, caps=(True, True))
    # 胸を斜めに通る銛の綱 + 腰に掛けた綱の輪
    F += rope_loop([(3.5, 19.7, -2.7), (1.2, 17.0, -3.45), (-1.6, 14.6, -3.6), (-3.6, 12.6, -3.2), (-4.9, 11.6, -1.4),
                    (-4.9, 11.4, 1.0)], r=0.5)
    for bone, s in (("armR", 1), ("armL", -1)):
        F += arm(bone, s, dict(c, sleeve_mat="coat", cuff_mat="cuff"))
    for bone, s in (("legR", 1), ("legL", -1)):
        F += leg(bone, s, c)
    # 背の荷: 小さな箱と、そばに立てかけた銛
    F += pack(c)
    F += hpipe("pack", "shaft", (-5.0, 5.0, 5.6), (-4.2, 29.0, 5.6), 0.55, 0.45, n=4, rot=45.0, caps=(True, False))
    F += spike("pack", "iron", (-4.2, 29.0, 5.6), (0.0, 1.0, 0.0), 3.4, 0.7, sides=4, rings=1, hint=(1, 0, 0))
    for s in (1, -1):
        F += spike("pack", "iron", (-4.2, 29.2, 5.6), (s * 0.9, 0.3, 0.0), 1.2, 0.45, sides=3, rings=1, hint=(0, 0, 1))
    # 荷に巻いた綱
    F += hpipe("pack", "roll", (-4.4, 17.8, 4.0), (4.4, 17.8, 4.0), 0.9, 0.9, n=5, rot=0.0, caps=(True, True))
    return dict(body=F, eyes=[], extra=face_painter(c), gear={}, gear_extra={}, gear_pal=None)
