"""B 案: 灰被りの炉守 — 灰色の外套を羽織った細長い炉守。腹が炉 (開いた口が燃える)、とがった頭巾と煙突、
革の鞴は洋梨形で先の煙突が曲がる。大鎚の頭は冷えかけの溶岩の塊。"""
from __future__ import annotations

from kit2 import *  # noqa

NAME = "B 灰被りの炉守"
HINT = 256

ROBE = [(52, 48, 50), (84, 80, 82), (118, 114, 112), (156, 152, 146), (198, 194, 184), (228, 224, 212)]
ROBED = [(22, 20, 24), (40, 37, 42), (62, 58, 62), (90, 86, 88), (122, 118, 116)]
BRICK = [(40, 20, 18), (76, 38, 30), (112, 58, 42), (150, 82, 56), (188, 112, 76)]
LEATH = [(28, 20, 18), (52, 38, 30), (82, 60, 46), (116, 88, 66), (150, 118, 90)]
MASK = [(70, 64, 60), (114, 106, 98), (158, 148, 136), (200, 190, 176), (232, 222, 206)]
IRON = [(14, 12, 14), (28, 24, 26), (48, 42, 42), (74, 66, 64), (104, 94, 88), (138, 124, 112)]
WOOD = [(30, 20, 16), (52, 36, 26), (80, 58, 40), (112, 84, 58), (146, 114, 82)]
COAL = [(10, 8, 10), (24, 18, 20), (42, 30, 30), (66, 44, 40), (94, 62, 52)]
ASH = [(84, 80, 82), (120, 116, 118), (156, 152, 152), (196, 192, 188)]
EMBER = [(70, 14, 8), (150, 38, 10), (222, 88, 18), (255, 150, 40), (255, 210, 110), (255, 244, 190)]
PAL = dict(
    mats={
        "robe": dict(ramp=ROBE, base=0.52, lit=0.34, noise=0.08, facevar=0.06, crack=0, min=0, streak=0.16, ao=0.18,
                     stain=dict(ramp=ROBED, thr=0.82, up=0.0, conc=0.35, low=0.55)),
        "robed": dict(ramp=ROBED, base=0.55, lit=0.30, noise=0.08, facevar=0.05, crack=0, min=0, streak=0.12, ao=0.12),
        "brick": dict(ramp=BRICK, base=0.55, lit=0.30, noise=0.08, facevar=0.07, crack=0, min=0, streak=0.1,
                      stripe=4, axis="v"),
        "leather": dict(ramp=LEATH, base=0.55, lit=0.32, noise=0.1, facevar=0.05, crack=0, min=0, streak=0.14),
        "mask": dict(ramp=MASK, base=0.62, lit=0.30, noise=0.05, crack=0, min=0, facevar=0.03),
        "iron": dict(ramp=IRON, base=0.55, lit=0.34, noise=0.08, facevar=0.04, crack=0, min=0, streak=0.1),
        "wood": dict(ramp=WOOD, base=0.55, lit=0.30, noise=0.10, crack=0, min=0, stripe=3, axis="u"),
        "coal": dict(ramp=COAL, base=0.55, lit=0.30, noise=0.12, crack=0, min=0, facevar=0.1),
        "door": dict(ramp=EMBER, base=0.7, lit=0.1, noise=0.2, crack=0, min=1, facevar=0.05),
        "spout": dict(ramp=EMBER, base=0.7, lit=0.1, noise=0.2, crack=0, min=1, facevar=0.05),
    },
    glow_mats=["door", "spout"],
)
GEAR_PAL = PAL


def build():
    F = []
    G = {"hammer": [], "tongs": []}
    # ================================================================ 胴: 炉の樽
    rings = [ringp(32, 11, -9, 8, -1), ringp(40, 19, -16, 10, -2), ringp(50, 22, -20, 11, -3), ringp(58, 18, -16, 10, -3),
             ringp(64, 10, -10, 7, -3)]
    F += rloft("body", "robed", rings, caps=(False, "robed"))
    for ya, yb, rx, zf, zb, zc in ((37, 39.6, 18.2, -15.2, 10.5, -1.8), (52.6, 55.2, 20.6, -18.6, 11.2, -3)):
        F += rloft("body", "iron", [ringp(ya, rx, zf, zb, zc), ringp(yb, rx, zf, zb, zc)], caps=(False, False))
    # 炉の口 (れんがの枠と、燃える戸)
    frame = [(-8.4, 31.5), (8.4, 31.5), (8.4, 43), (5.6, 48.5), (-5.6, 48.5), (-8.4, 43)]
    F += extrude("body", "brick", [np.array([x, y, -17.6]) for x, y in frame], (0, 0, -5.4), caps=(True, False))
    door = [(-6.0, 33.2), (6.0, 33.2), (6.0, 42.5), (3.6, 46.6), (-3.6, 46.6), (-6.0, 42.5)]
    F += extrude("body", "door", [np.array([x, y, -17.9]) for x, y in door], (0, 0, -5.8), caps=(True, False))
    # 灰の吹き溜まり
    F += fbox("body", "robe", (0, 31.6, -19.8), (20, 2.8, 6.5), R=rot_x(-14), taper=(0.7, 0.7), cut=0.3)
    # 外套 (正面を開けた二段の肩掛け。裾はぎざぎざ)
    for yt, yb, rxt, rxb, dp in ((67, 57, 10.5, 22, 4.5), (61, 47, 18, 23.5, 5.5)):
        top = arc_pts(yt, rxt, rxt * 0.8, 40, 320, 13, cz=-3)
        bot = arc_pts(yb, rxb, rxb * 0.8, 40, 320, 13, cz=-3, depth=dp, k=1.0)
        F += grid("body", "robe", [top, bot], np.array([0, (yt + yb) / 2, -3.0]))

    # ================================================================ 腹・裾・帯
    F += rloft("belly", "robe", [smooth_ring(31, 12.5, 10), tooth_ring(15, 19, 15.5, n=14, depth=6.5)], caps=(False, False))
    F += rloft("belly", "robed", [smooth_ring(29, 13.8, 11.2), smooth_ring(28, 13.6, 11.0)], caps=(False, False))
    F += rloft("belly", "iron", [smooth_ring(30.6, 13.2, 10.6), smooth_ring(28.8, 13.6, 11.0)], caps=(False, False))
    F += rloft("belly", "robe", [smooth_ring(26, 14.5, 11.8), tooth_ring(18.5, 19.5, 15.8, n=14, depth=4.5)],
               caps=(False, False))
    # 前掛け (れんが色の細い帯の房)
    for i, (x, yt, yb) in enumerate(((-10.5, 27, 8), (-5.3, 27, 4), (0, 27, 2), (5.3, 27, 4), (10.5, 27, 8))):
        F += poly("apron", "brick", [np.array([x - 3.3, yt, -14.4]), np.array([x + 3.3, yt, -14.4]),
                                      np.array([x, yb, -17.6])], np.array([x, 20, -6]))

    # ================================================================ 頭 (尖った頭巾と仮面)
    HF = F
    F = []
    hd = [ringp(42.5, 6.4, -5.6, 6.5, -28), ringp(48, 9, -6.8, 8, -28), ringp(54, 9.2, -7.4, 8.4, -28),
          ringp(60, 7.4, -6.0, 8, -27), ringp(67, 4.4, -3.6, 5.6, -24.5), ringp(74, 1.8, -1.6, 2.6, -19)]
    F += rloft("head", "robe", hd, caps=(False, "robe"))
    F += fbox("head", "mask", (0, 47.6, -34.2), (9.6, 11.5, 3.0), taper=(0.9, 0.85), cut=0.35)
    F += fbox("head", "robed", (0, 54.3, -34.2), (13.5, 2.6, 4.2), R=rot_x(-8), taper=(0.9, 0.7), cut=0.2)
    F += fbox("head", "mask", (0, 46.0, -36.2), (2.2, 6.0, 2.0), taper=(0.6, 0.6), cut=0.0)
    for s, _ in sides():
        F += hpipe("head", "iron", (s * 3.4, 61.5, -26), (s * 3.4, 67.5, -26), 1.9, 1.5, n=4, caps=(False, "iron"))
        F += hpipe("head", "spout", (s * 3.4, 67.5, -26), (s * 3.4, 68.6, -26), 1.9, 1.5, n=4, caps=(False, "spout"))
    F = HF + scale_about(F, 1.25, (0, 44, -27))
    eyes = []

    # ================================================================ 腕
    for s, nm in sides():
        arm, fore = "arm_" + nm, "forearm_" + nm
        X = s * 27
        F += ellipsoid(arm, "robe", (X, 55.5, -5), (9.0, 5.6, 9.0), segs=8, rings=2)
        F += hpipe(arm, "robe", (X, 55, -5), (X, 43, -5), 6.0, 5.2, caps=(False, False))
        F += hband(arm, "iron", (X, 47.5, -5), 5.9, 1.8)
        F += hpipe(fore, "robe", (X, 43, -5), (X, 31.5, -5), 5.0, 8.3, caps=(False, False))
        F += hband(fore, "iron", (X, 30.5, -5), 8.9, 2.4)
        F += hband(fore, "robed", (X, 38.0, -5), 6.4, 1.6)
        F += ellipsoid(fore, "coal", (X, 24.5, -6.2), (6.4, 5.6, 7.2), segs=6, rings=2, rot=rot_y(15))

    # ================================================================ 脚
    for s, nm in sides():
        leg = "leg_" + nm
        X = s * 10
        F += hpipe(leg, "robed", (X, 22, -1), (X, 8.5, -2), 5.4, 4.5, caps=(False, False))
        F += hband(leg, "iron", (X, 16.5, -1.4), 5.8, 1.6)
        F += hband(leg, "iron", (X, 11.5, -1.8), 5.2, 1.6)
        F += cbox(leg, "iron", (X, 3.3, -3.6), (11, 6.6, 18), taper=0.8, cut=0.4)
        F += fbox(leg, "robed", (X, 6.4, -2.6), (9, 3.2, 8), taper=(0.9, 0.9), cut=0.3)

    # ================================================================ 鞴 (洋梨形の革袋。先の煙突が外へ曲がる)
    ys = [34, 40, 48, 58, 66, 72]
    rr = [3.8, 7.6, 9.4, 8.4, 5.6, 3.2]
    for s, nm in sides():
        b = "bellows_" + nm
        C = np.array([s * 11.0, 0.0, 12.5])
        rg = [ngon(C + np.array([0, y, 0]), r, 8, (1, 0, 0), (0, 0, 1), 22.5) for y, r in zip(ys, rr)]
        F += rloft(b, "leather", rg, caps=("leather", False))
        F += hband(b, "iron", C + np.array([0, 44.5, 0]), 9.9, 1.8, n=8, rot=22.5)
        F += hband(b, "iron", C + np.array([0, 55.5, 0]), 9.2, 1.8, n=8, rot=22.5)
        F += hband(b, "iron", C + np.array([0, 36.8, 0]), 5.8, 1.6, n=8, rot=22.5)
        path = [C + np.array([0, 72, 0]), C + np.array([s * 1.5, 79, -0.5]), C + np.array([s * 5.5, 84.5, -3.5])]
        F += tube(b, "iron", path, [3.2, 2.5, 2.1], sides=6, phase=30, caps=(False, False))
        F += hpipe(b, "spout", path[-1], path[-1] + np.array([s * 0.8, 1.4, -0.6]), 2.4, 2.0, caps=(False, "spout"))

    # ================================================================ 大鎚 (長い柄、溶岩の塊の頭)
    P0 = fpivot("socket_main")
    G["hammer"] += hpipe("socket_main", "wood", P0 + V(0, 8, 0), P0 + V(0, -33, 0), 1.5, 1.8, caps=(True, False))
    for y in (4.0, -4.0, -12.0):
        G["hammer"] += hband("socket_main", "iron", P0 + V(0, y, 0), 2.2, 1.4)
    hc = P0 + V(0, -39.5, 0)
    G["hammer"] += ellipsoid("socket_main", "coal", hc, (8.6, 9.2, 11.5), segs=8, rings=3,
                             deform=lambda p: p * (1 + 0.12 * math.sin(p[0] * 0.8 + 1.3) * math.cos(p[2] * 0.6)))
    G["hammer"] += fbox("socket_main", "iron", P0 + V(0, -30.5, 0), (3.2, 1.8, 20), taper=(0.9, 0.9), cut=0.2)
    G["hammer"] += hband("socket_main", "iron", P0 + V(0, -33.4, 0), 2.7, 2.2)

    # ================================================================ 火箸
    Q0 = fpivot("socket_off")
    for s in (1, -1):
        G["tongs"] += hpipe("socket_off", "iron", Q0 + V(s * 3.0, 0, 10), Q0 + V(s * 1.0, 0, -22), 0.9, 1.1, n=4, rot=45,
                            caps=(True, False))
        G["tongs"] += hpipe("socket_off", "iron", Q0 + V(s * 1.0, 0, -22), Q0 + V(s * 3.0, 0, -31), 1.4, 0.8, n=4, rot=45,
                            caps=(False, True), sq=1.8)
        G["tongs"] += hband("socket_off", "robe", Q0 + V(s * 3.1, -1.0, 9.2), 1.7, 2.4, n=4, rot=45)
    G["tongs"] += hband("socket_off", "iron", Q0 + V(0, -1.6, -5), 2.7, 3.0, n=4, rot=45)
    G["tongs"] += ellipsoid("socket_off", "door", Q0 + V(0, 0, -27.5), (3.6, 3.3, 4.8), segs=6, rings=1)

    return dict(body=F, eyes=eyes, extra=extra, gear=G, gear_pal=GEAR_PAL, gear_extra=dict(hammer=gx, tongs=None))


def extra(c, col, glow):
    P, N, bay = c["P"], c["N"], c["bay"]
    # 炉の戸: 下ほど白く熱い。格子は暗い鉄。
    sel = c["owned"] & (c["mats"] == "door") & (N[..., 2] < -0.5)
    t = np.clip((P[..., 1] - 33.0) / 14.0, 0, 1)
    k = np.clip(np.floor((1 - t * 0.85) * 5.2 + 0.5 * bay), 0, 5).astype(int)
    col = np.where(sel[..., None], np.array(EMBER, float)[k], col)
    bars = sel & ((np.mod(P[..., 0] + 20.0, 2.6) < 0.55) | (np.abs(P[..., 1] - 39.5) < 0.5))
    col = np.where(bars[..., None], np.array(IRON[1], float), col)
    glow = glow | (sel & ~bars)
    # 仮面の目と口の格子
    col, glow = paint_eyes(c, col, glow, "head", "mask", [(2.9, 50.8, 1.9, 1.0, 0.55), (-2.9, 50.8, 1.9, 1.0, 0.55)], EMBER[2:])
    msel = c["owned"] & (c["bones"] == "head") & (c["mats"] == "mask") & (N[..., 2] < -0.5) & (P[..., 1] > 40.0) & (P[..., 1] < 44.6)
    mb = msel & (np.mod(P[..., 0] + 20.0, 1.3) < 0.55)
    col = np.where(mb[..., None], np.array(IRON[0], float), col)
    # 外套に付いた火の粉 (まばらな光る点)
    h = hash1(np.floor(P[..., 0] * 2) * 7.1 + np.floor(P[..., 1] * 2) * 13.7 + np.floor(P[..., 2] * 2) * 3.3, 11)
    sp = c["owned"] & np.isin(c["mats"], ["robe", "robed"]) & (h > 0.992)
    col = np.where(sp[..., None], np.array(EMBER[3], float), col)
    glow = glow | sp
    # 手の石炭の熱割れ
    col, glow = contour_cracks(c, col, glow, ["coal"], EMBER, thr=0.07, scale=0.14, seed=5, rim=(40, 10, 8))
    return col, glow


def gx(c, col, glow):
    return contour_cracks(c, col, glow, ["coal"], EMBER, thr=0.075, scale=0.12, seed=8, rim=(40, 10, 8))
