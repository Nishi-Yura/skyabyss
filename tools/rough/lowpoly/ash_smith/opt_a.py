"""A 案: 玄武の鍛冶王 — 六角の玄武岩の柱を束ねた重い体。柱の継ぎ目に冷えかけの溶岩の熱割れが走り、覗き穴の兜、黒鉄の鞴。"""
from __future__ import annotations

from kit2 import *  # noqa

NAME = "A 玄武の鍛冶王"
HINT = 256

BAS = [(12, 12, 18), (24, 23, 32), (40, 39, 52), (60, 59, 74), (86, 85, 102), (116, 115, 134)]
BASD = [(8, 8, 12), (16, 16, 24), (28, 28, 38), (44, 44, 58), (64, 64, 80), (88, 88, 106)]
IRON = [(18, 15, 16), (34, 29, 30), (58, 50, 50), (88, 77, 72), (122, 108, 98), (160, 144, 128)]
WOOD = [(30, 20, 16), (52, 36, 26), (80, 58, 40), (112, 84, 58), (146, 114, 82)]
ASH = [(84, 80, 82), (120, 116, 118), (156, 152, 152), (196, 192, 188)]
EMBER = [(70, 14, 8), (150, 38, 10), (222, 88, 18), (255, 150, 40), (255, 210, 110), (255, 244, 190)]
SEAM = [(90, 22, 10), (170, 50, 12), (230, 100, 20), (255, 160, 48), (255, 220, 120)]
PAL = dict(
    mats={
        "basalt": dict(ramp=BAS, base=0.60, lit=0.30, noise=0.06, facevar=0.05, crack=0, min=0, streak=0.12, ao=0.12,
                       stain=dict(ramp=ASH, thr=0.93, up=0.50, conc=0.30, low=0.10)),
        "basalt_dk": dict(ramp=BASD, base=0.55, lit=0.30, noise=0.06, facevar=0.05, crack=0, min=0, streak=0.1, ao=0.10,
                          stain=dict(ramp=ASH, thr=0.95, up=0.45, conc=0.3, low=0.1)),
        "iron": dict(ramp=IRON, base=0.55, lit=0.34, noise=0.08, facevar=0.04, crack=0, min=0, streak=0.1),
        "wood": dict(ramp=WOOD, base=0.55, lit=0.30, noise=0.10, crack=0, min=0, stripe=3, axis="u"),
        "seam": dict(ramp=SEAM, base=0.6, lit=0.2, noise=0.1, crack=0, min=0, facevar=0.1),
        "slag": dict(ramp=EMBER, base=0.55, lit=0.2, noise=0.18, crack=0, min=0, facevar=0.08),
        "strike": dict(ramp=EMBER, base=0.7, lit=0.1, noise=0.22, crack=0, min=1, facevar=0.1),
    },
    glow_mats=["seam", "slag", "strike"],
)
GEAR_PAL = PAL


def build():
    F = []
    G = {"hammer": [], "tongs": []}
    # ================================================================ 胴 (body)
    rings = [ringp(32, 12, -9, 8, -1), ringp(40, 18, -15, 10, -2), ringp(50, 22, -19, 11, -3), ringp(60, 21, -18, 10, -3),
             ringp(66, 12, -12, 7, -3)]
    F += rloft("body", "basalt", rings, caps=(False, "basalt"))
    for x, y0, z, r, h in ((0, 41, -20.5, 4.8, 17), (8.8, 40, -19.5, 4.3, 14.5), (-8.8, 40, -19.5, 4.3, 14.5),
                           (16.5, 41, -15.5, 4.0, 12), (-16.5, 41, -15.5, 4.0, 12)):
        F += hexcol("body", "basalt_dk", (x, y0, z), r, h, taper=(0.85, 0.85), rot=30)
    # 首の後ろの低い柱
    for x, h in ((0, 9), (-7, 6.5), (7, 6.5)):
        F += hexcol("body", "basalt", (x, 62, -1), 4.3, h, taper=(0.8, 0.8))

    # ================================================================ 腹・帯・前掛け
    rb = [ringp(22, 11.5, -10, 9, 0), ringp(27, 13, -11, 9, 0), ringp(32, 12, -10, 8, 0)]
    F += rloft("belly", "basalt_dk", rb, caps=(False, False))
    F += rloft("belly", "iron", [ringp(25.8, 13.8, -11.8, 9.8, 0), ringp(29, 13.8, -11.8, 9.8, 0)], caps=(False, False))
    F += fbox("belly", "slag", (0, 27.4, -12.6), (7, 5, 2.4), taper=(0.9, 0.9), cut=0.3)
    rows = [(27.0, 19.5, 4, 6.4, -12.6), (22.0, 13.5, 3, 7.8, -13.4), (15.5, 6.5, 2, 9.2, -14.2)]
    for (yt, yb, cnt, w, z) in rows:
        for i in range(cnt):
            x = (i - (cnt - 1) / 2) * (w + 0.4)
            pts = [(x - w / 2, yt, z), (x + w / 2, yt, z), (x + w / 2 + 0.2, (yt + yb) / 2 + 1.2, z - 0.4), (x, yb, z - 0.9),
                   (x - w / 2 - 0.2, (yt + yb) / 2 + 1.2, z - 0.4)]
            F += extrude("apron", "iron", pts, (0, 0, 1.4), caps=(True, False))

    # ================================================================ 頭
    HF = F
    F = []
    zc = -28.5
    hd = [ringp(43.5, 5, -5.5, 5.5, zc), ringp(47, 7.2, -7, 6.5, zc), ringp(52, 7.8, -7.2, 6.8, zc),
          ringp(56, 7.4, -6.5, 6.5, zc), ringp(60, 5.2, -4.5, 5, zc)]
    F += rloft("head", "basalt_dk", hd, caps=(False, "basalt_dk"))
    F += fbox("head", "iron", (0, 54.2, -35.6), (15, 2.4, 3.4), R=rot_x(-6), taper=(0.9, 0.7), cut=0.2)   # 眉庇
    F += fbox("head", "iron", (0, 45.6, -34.0), (7, 3.0, 3.0), taper=(0.8, 0.8), cut=0.2)                 # 顎当て
    F += hexcol("head", "basalt", (0, 59, -28), 3.2, 12, taper=(0.65, 0.65))
    for s, _ in sides():
        F += hexcol("head", "basalt", (s * 5.2, 58, -27.5), 2.5, 8, taper=(0.6, 0.6), R=rot_z(-s * 20))
        F += hpipe("head", "iron", (s * 7.2, 52, -28.5), (s * 14, 61, -27), 2.4, 0.3, n=4, caps=(False, False))
        F += hpipe("head", "iron", (s * 5.6, 46, -33), (s * 6.4, 40.5, -36.5), 1.6, 0.2, n=4, caps=(False, False))
    F = HF + scale_about(F, 1.3, (0, 45, -27))
    eyes = []

    # ================================================================ 腕・肩
    for s, nm in sides():
        arm, fore = "arm_" + nm, "forearm_" + nm
        X = s * 27
        F += hexcol(arm, "basalt", (X, 53.5, -5), 10.2, 7, taper=(0.82, 0.82), caps=(False, "basalt"))
        F += hexcol(arm, "basalt_dk", (X, 60.5, -5), 7.4, 5.5, taper=(0.65, 0.65))
        F += hpipe(arm, "basalt", (s * 31.5, 59, -5), (s * 41, 69, -6.5), 3.6, 0.4, n=6, caps=(False, False))
        F += hpipe(arm, "basalt_dk", (X, 55, -5), (X, 43, -5), 7.4, 6.7, caps=(False, False))
        F += hband(arm, "iron", (X, 46.0, -5), 7.6, 2.6, grow=1.0)
        F += hpipe(fore, "basalt", (X, 43, -5), (X, 31, -5), 7.0, 9.0, caps=(False, False))
        F += hband(fore, "iron", (X, 30.5, -5), 9.8, 3.4)
        F += cbox(fore, "basalt_dk", (X, 24.2, -6.2), (12.5, 9, 13), taper=0.78, cut=0.4)
        for k in (-1, 0, 1):
            F += hexcol(fore, "basalt", (X + k * 4.2, 26.5, -12.6), 1.9, 3.2, taper=(0.8, 0.8))

    # ================================================================ 脚
    for s, nm in sides():
        leg = "leg_" + nm
        X = s * 10
        F += hpipe(leg, "basalt", (X, 22, -1), (X, 12.5, -2), 8.4, 7.4, caps=(False, False))
        F += fbox(leg, "iron", (X, 14.2, -9.3), (8.4, 6, 2.2), R=rot_x(-8), taper=(0.9, 0.9), cut=0.3)
        F += hpipe(leg, "basalt_dk", (X, 12.5, -2), (X, 6, -3), 7.2, 7.8, caps=(False, False))
        F += cbox(leg, "basalt_dk", (X, 3.2, -3.2), (13, 6.4, 19), taper=0.82, cut=0.35, caps=(True, True))
        F += fbox(leg, "iron", (X, 3.0, -12.2), (10.5, 5, 2.6), taper=(0.9, 0.8), cut=0.3)

    # ================================================================ 鞴 (背の黒鉄の蛇腹)
    ys = [34, 40, 44, 50, 54, 60, 64, 70]
    rr = [6.4, 8.4, 6.0, 8.5, 6.0, 8.3, 5.8, 7.0]
    for s, nm in sides():
        b = "bellows_" + nm
        C = np.array([s * 11.0, 0.0, 12.5])
        rg = [ngon(C + np.array([0, y, 0]), r, 6, (1, 0, 0), (0, 0, 1), 30) for y, r in zip(ys, rr)]
        mats = ["iron", "iron", "seam", "iron", "iron", "seam", "iron"]
        mats = ["iron", "seam", "iron", "iron", "seam", "iron", "iron"]
        F += rloft(b, "iron", rg, caps=("iron", "iron"), mats=mats)
        F += hpipe(b, "iron", C + np.array([0, 70, 0]), C + np.array([0, 78, 0]), 3.4, 2.7, caps=(False, False))
        F += hpipe(b, "slag", C + np.array([0, 78, 0]), C + np.array([0, 79.4, 0]), 3.6, 3.0, caps=(False, "slag"))

    # ================================================================ 大鎚 (受け口 socket_main の原点)
    P0 = fpivot("socket_main")
    G["hammer"] += hpipe("socket_main", "wood", P0 + V(0, 7, 0), P0 + V(0, -26, 0), 1.5, 1.7, caps=(True, False))
    for y in (4.0, -3.0, -9.0):
        G["hammer"] += hband("socket_main", "iron", P0 + V(0, y, 0), 2.1, 1.4)
    hc = P0 + V(0, -27.5, 0)
    G["hammer"] += hpipe("socket_main", "basalt", hc - V(0, 0, 11.5), hc + V(0, 0, 11.5), 7.8, 7.8, caps=("strike", "strike"))
    for z in (-8.2, 6.2):
        G["hammer"] += hpipe("socket_main", "iron", hc + V(0, 0, z), hc + V(0, 0, z + 2.6), 8.7, 8.7, caps=(False, False))
    G["hammer"] += hpipe("socket_main", "iron", hc + V(0, 7.2, -3), hc + V(0, 11.5, 1), 1.4, 0.2, n=4, caps=(False, False))

    # ================================================================ 火箸 (受け口 socket_off の原点)
    Q0 = fpivot("socket_off")
    for s in (1, -1):
        G["tongs"] += hpipe("socket_off", "iron", Q0 + V(s * 3.0, 0, 9), Q0 + V(s * 1.2, 0, -17), 1.0, 1.3, n=4, rot=45,
                            caps=(True, False))
        G["tongs"] += hpipe("socket_off", "iron", Q0 + V(s * 1.2, 0, -17), Q0 + V(s * 2.6, 0, -25), 1.5, 1.0, n=4, rot=45,
                            caps=(False, True), sq=1.8)
        G["tongs"] += hband("socket_off", "iron", Q0 + V(s * 3.2, -1.0, 8.4), 1.6, 2.0, n=4, rot=45)
    G["tongs"] += hband("socket_off", "iron", Q0 + V(0, -1.6, -3.5), 2.8, 3.2, n=4, rot=45)
    G["tongs"] += cbox("socket_off", "slag", Q0 + V(0, 0, -22.5), (4.6, 4.6, 7.5), taper=0.7, cut=0.4)

    return dict(body=F, eyes=eyes, extra=extra, gear=G, gear_pal=GEAR_PAL, gear_extra=dict(hammer=gx, tongs=gx))


def extra(c, col, glow):
    col, glow = contour_cracks(c, col, glow, ["basalt", "basalt_dk"], EMBER, thr=0.032, scale=0.10, seed=4,
                               rim=(52, 12, 8), gate=lambda P: 0.55 + 0.95 * np.clip((66 - P[..., 1]) / 44, 0, 1))
    col, glow = paint_slit(c, col, glow, "head", "basalt_dk", 50.5, 54.0, 7.6, -35.0, EMBER[2:])
    return col, glow


def gx(c, col, glow):
    return contour_cracks(c, col, glow, ["basalt", "basalt_dk"], EMBER, thr=0.05, scale=0.16, seed=9, rim=(52, 12, 8))
