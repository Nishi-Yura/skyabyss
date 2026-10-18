"""A 案: 頭巾の語り部 (そのまま) — 今の B 頭巾の語り部を低ポリ + ドット絵にそのまま写す。藍の尖り頭巾、
顔は開口の縁の中、白い眉と房、淡く光る目、藍の肩掛け、灰茶の長衣に橙の縄の帯、組んだ手。"""
from __future__ import annotations

from char import *  # noqa

NAME = "A 頭巾の語り部"
HINT = 128

NAVY = [(12, 20, 54), (22, 36, 90), (34, 54, 122), (50, 74, 152), (72, 100, 180)]
NAVY2 = [(16, 28, 70), (28, 46, 104), (42, 66, 138), (60, 88, 168), (84, 114, 196)]
TRIM = [(28, 44, 96), (46, 70, 130), (68, 98, 164), (96, 128, 194), (130, 160, 214)]
ROBE = [(52, 48, 44), (84, 77, 68), (116, 107, 94), (148, 137, 120), (178, 166, 146)]
HEM = [(40, 36, 34), (64, 58, 52), (92, 84, 74), (120, 110, 96)]
BELT = [(56, 32, 14), (100, 62, 26), (146, 94, 40), (190, 132, 62), (224, 172, 96)]

PAL = dict(
    mats={
        "robe": dict(ramp=ROBE, base=0.55, lit=0.30, noise=0.10, facevar=0.05, crack=0, min=0, streak=0.12, ao=0.12,),
        "hem": dict(ramp=HEM, base=0.60, lit=0.30, noise=0.12, facevar=0.05, crack=0, min=0, streak=0.1),
        "belt": dict(ramp=BELT, base=0.55, lit=0.30, noise=0.08, crack=0, min=0, stripe=2, axis="u"),
        "cordm": dict(ramp=BELT, base=0.58, lit=0.30, noise=0.08, crack=0, min=0, facevar=0.05),
        "shawl": dict(ramp=NAVY2, base=0.55, lit=0.32, noise=0.08, facevar=0.05, crack=0, min=0, stripe=4, axis="v"),
        "hood": dict(ramp=NAVY, base=0.55, lit=0.34, noise=0.08, facevar=0.06, crack=0, min=0, streak=0.10),
        "rim": dict(ramp=TRIM, base=0.55, lit=0.32, noise=0.06, facevar=0.04, crack=0, min=0, stripe=3, axis="u"),
        "skin": dict(ramp=SKIN, base=0.88, lit=0.06, noise=0.0, facevar=0.0, crack=0, min=1, streak=0.0, ao=0.04),
        "white": dict(ramp=WHITE, base=0.66, lit=0.30, noise=0.04, facevar=0.03, crack=0, min=1, streak=0.0),
        "sleeve": dict(ramp=ROBE, base=0.55, lit=0.30, noise=0.10, facevar=0.05, crack=0, min=0, streak=0.10),
        "cuff": dict(ramp=NAVY2, base=0.58, lit=0.30, noise=0.06, crack=0, min=0, stripe=2, axis="v"),
        "boot": dict(ramp=BOOT, base=0.55, lit=0.30, noise=0.1, crack=0, min=0),
    },
    glow_mats=[],
)

CFG = dict(
    robe=[(1.2, 6.0, -4.6, 4.4, 0), (4.5, 5.3, -4.4, 4.2, 0), (8.5, 4.6, -4.2, 3.9, 0), (11.8, 4.3, -4.0, 3.9, 0),
          (13.8, 4.7, -4.0, 4.6, 0)],
    robe_mats=["hem", "robe", "robe", "robe"],
    shawl=[(12.6, 5.2, -4.5, 3.8, 0), (15.0, 5.8, -4.7, 4.2, 0), (17.0, 4.8, -4.0, 3.8, 0), (18.4, 3.0, -2.6, 2.8, -0.2)],
    hood=[(17.0, 3.4, -3.8, 3.6, 1.2, 0.0), (18.6, 4.5, -4.3, 4.6, 1.5, 0), (21.0, 4.8, -4.3, 5.2, 1.6, 0),
          (23.6, 4.4, -4.2, 5.0, 1.5, 0.2), (25.6, 3.3, -3.6, 4.2, 1.2, 0.5), (27.4, 1.8, -2.2, 2.8, 0.8, 1.2),
          (29.4, 0.35, -0.5, 0.9, 0.2, 2.2)],
    rim=[(2.1, 17.3, -4.5, 0.9), (3.5, 18.9, -5.0, 1.05), (4.1, 21.0, -5.1, 1.1), (3.7, 23.3, -4.9, 1.05),
         (2.5, 25.0, -4.6, 1.0), (1.1, 25.9, -4.3, 0.95), (0.0, 26.2, -4.1, 0.95)],
    face=[(17.7, 1.4, -1.3, 1.0), (18.9, 2.5, -1.9, 1.4), (20.4, 3.1, -2.3, 1.6), (22.3, 3.2, -2.2, 1.6),
          (24.0, 2.6, -1.7, 1.4), (24.9, 1.7, -1.2, 1.0)],
)

FACE = dict(pats=[
    ([".DD.", "DHPD", "dPPd"], 2, 43),              # 目 (内側の画素 = 中心から 0.5 単位)
    (["..M", "MM."], 0, 38),                # 口: 中央 2 画素の線と、上がる両端
], blush=None)


def build():
    F = base_body(CFG)
    return dict(body=F, eyes=[], extra=extra)


def extra(c, col, glow):
    return face_paint(c, col, glow, FACE)
