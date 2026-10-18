"""C 案: 金床頭の鋳鉄巨人 — 頭が金床 (平らな天板と前へ突き出す角)、肩も小さな金床。鋳鉄の板の継ぎ目に溶けた鉄の筋、
垂れて冷える溶岩の滴、ドラム形の鞴、腰から下がる鎖、本物の大鎚 (インゴットの頭)。"""
from __future__ import annotations

from kit2 import *  # noqa

NAME = "C 金床頭の鋳鉄巨人"
HINT = 256

STEEL = [(14, 18, 26), (26, 32, 44), (44, 52, 68), (68, 78, 96), (98, 110, 130), (136, 148, 168)]
STEELD = [(8, 10, 16), (18, 22, 32), (32, 38, 52), (50, 58, 76), (74, 84, 104)]
BRONZE = [(52, 28, 14), (96, 54, 24), (146, 90, 40), (192, 130, 62), (230, 176, 96)]
WOOD = [(30, 20, 16), (52, 36, 26), (80, 58, 40), (112, 84, 58), (146, 114, 82)]
ASH = [(84, 80, 82), (120, 116, 118), (156, 152, 152), (196, 192, 188)]
EMBER = [(70, 14, 8), (150, 38, 10), (222, 88, 18), (255, 150, 40), (255, 210, 110), (255, 244, 190)]
PAL = dict(
    mats={
        "steel": dict(ramp=STEEL, base=0.56, lit=0.34, noise=0.07, facevar=0.05, crack=0, min=0, streak=0.12, ao=0.12,
                      stain=dict(ramp=ASH, thr=0.97, up=0.4, conc=0.3, low=0.0)),
        "steeld": dict(ramp=STEELD, base=0.56, lit=0.32, noise=0.07, facevar=0.05, crack=0, min=0, streak=0.1, ao=0.1),
        "bronze": dict(ramp=BRONZE, base=0.55, lit=0.34, noise=0.08, facevar=0.04, crack=0, min=0, streak=0.1),
        "wood": dict(ramp=WOOD, base=0.55, lit=0.30, noise=0.10, crack=0, min=0, stripe=3, axis="u"),
        "slag": dict(ramp=EMBER, base=0.6, lit=0.2, noise=0.18, crack=0, min=0, facevar=0.08),
        "strike": dict(ramp=EMBER, base=0.7, lit=0.1, noise=0.22, crack=0, min=1, facevar=0.1),
    },
    glow_mats=["slag", "strike"],
)
GEAR_PAL = PAL


def anvil(bone, X, y, s, w=17.0, d=12.0, horn=10.0, hdir=1):
    """小さな金床 (天板 + くびれ + 土台 + 角)。X は中心、s は左右。"""
    F = []
    F += fbox(bone, "steel", (X, y + 3.6, -5), (w, 3.4, d), taper=(0.92, 0.92), cut=0.15)
    F += fbox(bone, "steeld", (X, y, -5), (w * 0.42, 4.2, d * 0.62), taper=(1.5, 1.3))
    F += hpipe(bone, "steel", (X + s * w / 2 * 0.85, y + 3.6, -5), (X + s * (w / 2 + horn), y + 3.2, -5), 3.4, 0.4, n=4,
               rot=45, caps=(False, False))
    return F


def build():
    F = []
    G = {"hammer": [], "tongs": []}
    # ================================================================ 胴: 鋳鉄の台形
    rings = [ringp(32, 13, -10, 9, -1), ringp(42, 21, -16, 11, -2), ringp(52, 24, -20, 12, -3), ringp(60, 22, -17, 11, -3),
             ringp(65, 14, -12, 8, -3)]
    F += rloft("body", "steel", rings, caps=(False, "steel"))
    F += rloft("body", "steeld", [ringp(40.6, 21.8, -16.8, 11.6, -2), ringp(43.2, 21.9, -16.9, 11.7, -2)], caps=(False, False))
    F += rloft("body", "steeld", [ringp(54.6, 23.5, -19.4, 12.2, -3), ringp(57.2, 23.2, -19.0, 12.0, -3)], caps=(False, False))
    for s, _ in sides():
        for z, y in ((-17, 47), (-17, 56.5)):
            F += hexcol("body", "bronze", (s * 12, y, z), 1.6, 1.2, n=6, caps=(False, True))

    # ================================================================ 腹・帯・鎖・前掛け
    F += rloft("belly", "steeld", [ringp(22, 11.5, -10, 9, 0), ringp(27, 13, -11, 9, 0), ringp(32, 12, -10, 8, 0)],
               caps=(False, False))
    F += rloft("belly", "bronze", [ringp(26, 13.8, -11.8, 9.8, 0), ringp(29.2, 13.8, -11.8, 9.8, 0)], caps=(False, False))
    for s, _ in sides():
        F += tube("belly", "steeld", [np.array([s * 12.6, 27.5, -10]), np.array([s * 14.0, 20, -11]), np.array([s * 13.0, 11, -10])],
                  [1.1, 1.1, 1.1], sides=4, phase=45, caps=(False, True))
        for y in (24.0, 18.5, 13.0):
            F += hband("belly", "bronze", np.array([s * 13.6, y, -10.8]), 1.5, 1.0, n=4, rot=45)
    hem = [(-11, 28), (11, 28), (10.5, 14), (8, 8.5), (5, 13.5), (2.5, 6), (0, 12), (-2.5, 6), (-5, 13.5), (-8, 8.5), (-10.5, 14)]
    F += extrude("apron", "steeld", [np.array([x, y, -13.2]) for x, y in hem], (0, 0, 1.8), caps=(True, False))
    for x, y, ln in ((-8, 8.6, 4.0), (-2.5, 6.2, 5.0), (2.5, 6.2, 5.0), (8, 8.6, 4.0)):
        F += spike("apron", "slag", (x, y + 1.0, -13.2), (0, -1, -0.15), ln, 1.1, sides=3, rings=1)

    # ================================================================ 頭: 金床
    HF = F
    F = []
    zc = -28.0
    F += fbox("head", "steel", (0, 58.0, zc), (21, 4.0, 17), taper=(0.94, 0.94), cut=0.12)
    F += fbox("head", "steeld", (0, 53.0, zc), (10.5, 6.5, 10.5), taper=(1.15, 1.15), cut=0.0)
    F += fbox("head", "steel", (0, 48.0, zc), (15.5, 4.2, 14), taper=(0.78, 0.78), cut=0.15)
    F += hpipe("head", "steel", (0, 58.2, zc - 7.5), (0, 57.0, zc - 21), 5.2, 0.5, n=4, rot=45, caps=(False, False), sq=0.75)
    F += hpipe("head", "steel", (0, 58.2, zc + 7.5), (0, 56.5, zc + 12.5), 4.0, 0.6, n=4, rot=45, caps=(False, False), sq=0.75)
    for s, _ in sides():
        F += hexcol("head", "bronze", (s * 4.6, 56.4, zc - 7.2), 1.0, 1.0, n=6, caps=(False, True))
    F = HF + scale_about(F, 1.2, (0, 45, -27))
    eyes = []

    # ================================================================ 腕・肩 (肩に小さな金床)
    for s, nm in sides():
        arm, fore = "arm_" + nm, "forearm_" + nm
        X = s * 27
        F += hpipe(arm, "steeld", (X, 55, -5), (X, 43, -5), 7.0, 6.4, n=8, rot=22.5, caps=(False, False))
        F += hband(arm, "bronze", (X, 46.4, -5), 7.0, 2.0, n=8, rot=22.5)
        F += anvil(arm, X, 56.0, s, w=19, d=13, horn=9)
        F += hpipe(fore, "steel", (X, 43, -5), (X, 31.5, -5), 6.6, 8.6, n=8, rot=22.5, caps=(False, False))
        F += hband(fore, "bronze", (X, 30.4, -5), 9.2, 3.0, n=8, rot=22.5)
        F += cbox(fore, "steeld", (X, 24.2, -6.2), (12.5, 9, 13), taper=0.8, cut=0.4)
        F += fbox(fore, "steel", (X, 27.0, -12.8), (9.5, 3.0, 2.4), taper=(0.9, 0.8), cut=0.2)

    # ================================================================ 脚
    for s, nm in sides():
        leg = "leg_" + nm
        X = s * 10
        F += hpipe(leg, "steel", (X, 22, -1), (X, 12.5, -2), 8.2, 7.4, n=8, rot=22.5, caps=(False, False))
        F += fbox(leg, "bronze", (X, 14.2, -9.3), (8.0, 5.6, 2.2), R=rot_x(-8), taper=(0.9, 0.9), cut=0.3)
        F += hpipe(leg, "steeld", (X, 12.5, -2), (X, 6, -3), 7.2, 7.9, n=8, rot=22.5, caps=(False, False))
        F += cbox(leg, "steeld", (X, 3.2, -3.2), (13, 6.4, 19), taper=0.82, cut=0.35, caps=(True, True))
        F += fbox(leg, "steel", (X, 3.0, -12.2), (10.5, 5, 2.6), taper=(0.9, 0.8), cut=0.3)

    # ================================================================ 鞴 (ドラム形。先が前へ向く漏斗)
    for s, nm in sides():
        b = "bellows_" + nm
        C = np.array([s * 11.0, 0.0, 12.5])
        ys = [34, 36, 38, 62, 64, 66, 72]
        rr = [7.4, 9.0, 8.0, 8.0, 9.0, 7.4, 4.4]
        rg = [ngon(C + np.array([0, y, 0]), r, 8, (1, 0, 0), (0, 0, 1), 22.5) for y, r in zip(ys, rr)]
        F += rloft(b, "steeld", rg, caps=("steeld", False), mats=["steel", "steel", "steeld", "steel", "steel", "steeld"])
        for y in (45.0, 54.0):
            F += hband(b, "bronze", C + np.array([0, y, 0]), 8.5, 1.8, n=8, rot=22.5)
        F += hband(b, "slag", C + np.array([0, 38.0, 0]), 8.2, 1.2, n=8, rot=22.5)
        F += hpipe(b, "steel", C + np.array([0, 72, 0]), C + np.array([s * 2, 79, -5]), 4.2, 3.0, n=8, rot=22.5, caps=(False, False))
        F += hpipe(b, "slag", C + np.array([s * 2, 79, -5]), C + np.array([s * 2.4, 80, -6.2]), 3.3, 2.4, n=8, rot=22.5,
                   caps=(False, "slag"))

    # ================================================================ 大鎚 (インゴットの頭の大きな鎚)
    P0 = fpivot("socket_main")
    G["hammer"] += hpipe("socket_main", "wood", P0 + V(0, 8, 0), P0 + V(0, -26, 0), 1.6, 1.8, caps=(True, False))
    for y in (4.0, -4.0):
        G["hammer"] += hband("socket_main", "bronze", P0 + V(0, y, 0), 2.2, 1.4)
    hc = P0 + V(0, -28.5, 0)
    G["hammer"] += fbox("socket_main", "steel", hc, (14.5, 12, 22), taper=(0.78, 0.86), cut=0.18, mats=None)
    G["hammer"] += fbox("socket_main", "strike", hc + V(0, -5.2, 0), (13.5, 2.0, 21), taper=(0.9, 0.9), cut=0.15)
    for z in (-6.5, 6.5):
        G["hammer"] += hexcol("socket_main", "bronze", hc + V(0, 5.0, z), 2.0, 1.2, caps=(False, True))
    for z in (-10.4, 10.4):
        G["hammer"] += fbox("socket_main", "strike", hc + V(0, 0, z), (10.5, 8.4, 1.0), cut=0.0)

    # ================================================================ 火箸
    Q0 = fpivot("socket_off")
    for s in (1, -1):
        G["tongs"] += hpipe("socket_off", "steeld", Q0 + V(s * 3.0, 0, 9), Q0 + V(s * 1.2, 0, -17), 1.0, 1.3, n=4, rot=45,
                            caps=(True, False))
        G["tongs"] += hpipe("socket_off", "steeld", Q0 + V(s * 1.2, 0, -17), Q0 + V(s * 2.8, 0, -25), 1.5, 1.0, n=4, rot=45,
                            caps=(False, True), sq=1.8)
        G["tongs"] += hband("socket_off", "bronze", Q0 + V(s * 3.2, -1.0, 8.4), 1.6, 2.0, n=4, rot=45)
    G["tongs"] += hband("socket_off", "bronze", Q0 + V(0, -1.6, -3.5), 2.8, 3.2, n=4, rot=45)
    G["tongs"] += cbox("socket_off", "slag", Q0 + V(0, 0, -22.5), (4.8, 4.8, 7.5), taper=0.7, cut=0.4)

    return dict(body=F, eyes=eyes, extra=extra, gear={k: v for k, v in G.items()}, gear_pal=GEAR_PAL,
                gear_extra=dict(hammer=gx, tongs=None))


def extra(c, col, glow):
    # 板の継ぎ目の溶けた鉄の筋 (胴の前の三本の吹き出し口と、細い熱割れ)
    for y in (45.5, 50.5, 55.5):
        col, glow = paint_slit(c, col, glow, "body", "steel", y - 0.7, y + 0.7, 12.0, -14.0, EMBER[2:], nzmin=0.45)
    col, glow = contour_cracks(c, col, glow, ["steel", "steeld"], EMBER, thr=0.016, scale=0.10, seed=6, rim=(40, 12, 8),
                               gate=lambda P: 0.5 + 1.0 * np.clip((60 - P[..., 1]) / 40, 0, 1))
    # 金床の目: 光る覗き窓
    col, glow = paint_eyes(c, col, glow, "head", "steeld", [(2.8, 53.3, 1.5, 1.5, 0.0), (-2.8, 53.3, 1.5, 1.5, 0.0)], EMBER[2:])
    return col, glow


def gx(c, col, glow):
    return col, glow
