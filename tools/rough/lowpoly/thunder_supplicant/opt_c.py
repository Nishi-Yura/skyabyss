"""C 案「蓑の雷乞い」: 藁の蓑を何段も重ねた円錐の外套 (裾は藁の鋸歯、銅線がささくれ立つ)、
笠をかぶった紙の灯籠のような六角の籠の頭 (光る面に黒い稲妻の影)。杖は錫杖 (輪の冠と遊環)。
雨乞いの巡礼。裾広がりの藁山と、大きな笠の輪郭。"""
from __future__ import annotations

from kit import *  # noqa

NAME_JP = "C 蓑の雷乞い"
HINT = 128

STRAW = [(54, 38, 20), (104, 78, 36), (152, 118, 54), (196, 160, 80), (230, 200, 120)]
INDIGO = [(12, 16, 36), (24, 32, 64), (42, 56, 100), (68, 86, 136)]
COPPER = [(48, 24, 16), (98, 50, 24), (156, 84, 36), (206, 126, 54), (242, 176, 92)]
GREEN = [(30, 56, 52), (52, 96, 82), (88, 140, 116)]
PANEL = [(110, 170, 230), (170, 216, 250), (222, 244, 255)]
DARK = [(6, 8, 14), (14, 18, 28), (26, 32, 46)]
BAMBOO = [(36, 44, 22), (74, 84, 42), (112, 124, 62), (150, 162, 90)]
PAL = dict(
    mats={
        "straw": dict(ramp=STRAW, base=0.55, lit=0.32, noise=0.10, crack=0.0, min=0, stripe=3, axis="v", facevar=0.08),
        "strawb": dict(ramp=STRAW, base=0.30, lit=0.24, noise=0.08, crack=0.0, min=0, stripe=3, axis="v", facevar=0.06),
        "strawt": dict(ramp=STRAW, base=0.72, lit=0.30, noise=0.08, crack=0.0, min=0, stripe=4, axis="u", facevar=0.06),
        "cloth": dict(ramp=INDIGO, base=0.50, lit=0.30, noise=0.06, crack=0.0, min=0, facevar=0.05),
        "copper": dict(ramp=COPPER, base=0.55, lit=0.34, noise=0.07, crack=0.0, min=0,
                       stain=dict(ramp=GREEN, thr=0.97, up=0.25, conc=0.2, low=0.1)),
        "copperd": dict(ramp=COPPER, base=0.28, lit=0.22, noise=0.06, crack=0, min=0),
        "dark": dict(ramp=DARK, base=0.5, lit=0.1, noise=0.1, crack=0, min=0),
        "panel": dict(ramp=PANEL, base=0.6, lit=0.3, noise=0.03, crack=0, min=0),
        "bolt": dict(ramp=PANEL, base=0.6, lit=0.4, noise=0.03, crack=0, min=0),
        "skin": SKIN,
    },
    glow_mats=["panel", "bolt"],
)
STAFF_PAL = dict(mats={k: PAL["mats"][k] for k in ("copper", "copperd", "dark", "bolt", "straw")} | {
    "wood": dict(ramp=BAMBOO, base=0.5, lit=0.3, noise=0.10, crack=0, min=0, stripe=6, axis="v"),
}, glow_mats=["bolt"])

ZC = -2.0


def lantern_marks(c, col, glow):
    """光る面の一枚ごとに、黒い稲妻の影を手で描く (折れ線)。"""
    mats, owned, INS, LU, LV, CW, CH = c["mats"], c["owned"], c["INS"], c["LU"], c["LV"], c["CW"], c["CH"]
    sel = (mats == "panel") & owned & INS
    u = LU + 0.5 - CW / 2.0
    ph = (LV + 0.5) / np.maximum(CH, 1)
    cx = np.interp(ph, [0.0, 0.42, 0.58, 1.0], [0.24, -0.2, 0.2, -0.24]) * CW
    hit = sel & (np.abs(u - cx) < 0.8) & (LV >= 1) & (LV < CH - 1)
    col = np.where(hit[..., None], np.array([14, 18, 34], float), col)
    glow = glow & ~hit
    return col, glow


def build():
    F = []
    H = "head"
    # ================================================================ 頭: 笠と六角の灯籠
    F += tube(H, "dark", [(0, 19.4, -1.2), (0, 22.4, ZC)], [1.3, 1.1], [1.3, 1.1], sides=4, hint=(1, 0, 0), caps=(False, False))
    F += disc(H, "copper", 21.8, 2.2, 22.7, 2.5, zc=ZC, n=6, phase=0, caps=(False, True))
    # 光る紙の面 (六角の筒) と銅の枠
    pan = [circ(22.7, 2.5, ZC, n=6, phase=0), circ(28.4, 2.8, ZC, n=6, phase=0)]
    F += rloft(H, "panel", pan, caps=(False, False))
    for k in range(6):
        a = math.radians(60.0 * k)
        F += tube(H, "copper", [(2.58 * math.sin(a), 22.7, ZC - 2.58 * math.cos(a)), (2.88 * math.sin(a), 28.4, ZC - 2.88 * math.cos(a))],
                  [0.42, 0.42], sides=3, hint=(0, 1, 0), phase=90, caps=(False, False))
    F += disc(H, "copper", 28.0, 2.85, 28.9, 3.0, zc=ZC, n=6, phase=0, caps=(False, False))
    # 笠 (大きな浅い円錐、縁は藁の鋸歯)
    nK = 12
    brim = tring(29.6, 7.2, -7.2, 7.2, n=nK, zc=ZC, phase=0, dy=lambda k: -1.2 if k % 2 == 0 else 0.3)
    mid = tring(31.4, 4.2, -4.2, 4.2, n=nK, zc=ZC, phase=0)
    top = tring(33.3, 1.0, -1.0, 1.0, n=nK, zc=ZC, phase=0)
    F += rloft(H, "strawt", [brim, mid, top], caps=(False, "copper"))
    F += poly(H, "dark", tring(29.6, 7.2, -7.2, 7.2, n=nK, zc=ZC, phase=0, dy=lambda k: -1.1), np.array([0, 40, ZC]))
    F += sp(H, "copper", (0, 33.2, ZC), (0, 1, 0), 3.4, 0.5, sides=3, rings=1)
    # 笠の縁から立つ銅線 (静電気でささくれる)
    for k in range(0, nK, 2):
        a = math.radians(360.0 * k / nK)
        p = V(6.8 * math.sin(a), 29.8, ZC - 6.8 * math.cos(a))
        F += sp(H, "copper", p, (math.sin(a) * 0.4, 1.0, -math.cos(a) * 0.4), 3.0, 0.38, sides=3, rings=1)
    # ================================================================ 胴: 肩の蓑 (2 段) と襟
    B = "body"
    F += disc(B, "copper", 19.2, 3.3, 20.3, 2.7, zc=-0.6, n=6, phase=0, caps=(False, False))
    nS = 12
    for i, (y0, r0, y1, r1, mat) in enumerate(((20.0, 3.7, 15.4, 5.2, "straw"), (17.2, 4.6, 11.6, 6.3, "strawb"))):
        top = tring(y0, r0, -r0 * 0.78, r0 * 0.9, n=nS, zig=0.0)
        bot = tring(y1, r1, -r1 * 0.78, r1 * 0.9, n=nS, zig=0.0, dy=lambda k, i=i: -2.8 if (k + i) % 2 == 0 else 0.4)
        F += rloft(B, mat, [top, bot], caps=(False, False))
    F += fbox(B, "copper", (0, 16.8, -4.6), (2.0, 2.6, 0.9), taper=(0.7, 0.8))
    # ================================================================ 蓑の裾 (3 段) と藍の袴
    R = "robe"
    nR = 14
    F += tube(R, "cloth", [(0, 11.0, 0.0), (0, 4.0, 0.0)], [4.0, 5.0], [3.4, 4.4], sides=6, hint=(1, 0, 0), phase=0, caps=(False, False))
    layers = [(12.0, 4.9, 7.0, 6.2), (8.2, 5.6, 3.4, 7.0), (4.6, 6.4, 0.0, 7.8)]
    for i, (y0, r0, y1, r1) in enumerate(layers):
        top = tring(y0, r0, -r0 * 0.9, r0, n=nR, zig=0.0)
        bot = tring(y1, r1, -r1 * 0.9, r1, n=nR, zig=0.0, dy=lambda k, i=i: -3.2 if (k + i) % 2 == 0 else 0.5)
        F += rloft(R, ("straw", "strawb", "straw")[i], [top, bot], caps=(False, False))
    F += poly(R, "dark", tring(1.0, 6.4, -5.8, 6.4, n=nR), np.array([0, 10, 0]))
    for s in (1, -1):
        F += sp(R, "skin", (s * 2.0, 0.8, -3.8), (0, -0.2, -1.0), 3.2, 1.1, sides=4, rings=1)
        F += disc(R, "copper", 0.8, 1.2, 1.7, 1.1, zc=-4.2, cx=s * 2.0, n=6, caps=(False, False))
    # ささくれた藁の長い房 (背)
    for i, x in enumerate((-3.4, -1.1, 1.1, 3.4)):
        F += sp(R, "straw", (x, 8.0, 6.2), (x * 0.08, -0.7, 0.8), 6.5 - 0.7 * (i % 2), 1.0, sides=3, rings=1)
    # ================================================================ 腕 (藍の細い袖、銅の腕輪、藁の手甲)
    for s, nm in ((1, "r"), (-1, "l")):
        a, fa = f"arm_{nm}", f"forearm_{nm}"
        F += tube(a, "cloth", [(s * 4.8, 18.9, 0.0), (s * 5.0, 16.2, -0.4), (s * 5.0, 13.4, -0.9)], [1.4, 1.5, 1.3], [1.5, 1.6, 1.4], sides=4,
                  hint=(0, 0, 1), phase=45, caps=(False, False))
        F += sleeve_hex(fa, "cloth", s, [(12.8, -1.2, 2.5, 2.9), (11.8, -3.0, 2.7, 3.2), (11.0, -4.6, 3.0, 3.6)])
        F += sleeve_hex(fa, "straw", s, [(11.0, -4.2, 3.5, 4.4), (11.0, -5.2, 3.5, 4.4)])
        F += sleeve_hex(fa, "copper", s, [(12.5, -2.0, 3.0, 3.5), (12.5, -2.6, 3.0, 3.5)])
        F += fist(fa, s)
    # ================================================================ 杖: 錫杖
    S = []
    St = "staff"
    S += tube(St, "wood", [(0, -13.0, 0), (0, 24.0, 0)], [0.62, 0.62], sides=4, hint=(1, 0, 0), phase=45, caps=(True, False))
    S += sp(St, "copper", (0, -13.0, 0), (0, -1, 0), 2.6, 0.62, sides=4, rings=1)
    for y in (-1.5, 1.0, 11.0, 19.0):
        S += disc(St, "copper" if y < 5 else "straw", y, 1.0, y + 0.7, 1.0, n=4, phase=45, caps=(False, False))
    S += disc(St, "copper", 23.2, 1.0, 24.4, 1.3, n=6, caps=(False, True))
    S += hoop(St, "copper", V(0, 29.0, 0), 4.2, 6, V(1, 0, 0), V(0, 1, 0), 0.5)
    S += sp(St, "copper", (0, 24.4, 0), (0, 1, 0), 4.0, 0.5, sides=4, rings=1)
    S += ellipsoid(St, "bolt", (0, 29.0, 0), (1.1, 1.8, 1.1), segs=4, rings=1)
    return F, S, lantern_marks, None
