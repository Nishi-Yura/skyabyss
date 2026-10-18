"""B 案「羽の司祭」: 白と灰の羽の外套 (肩から段々に垂れる羽、袖から翼のように垂れる羽板)。
丸い銅の鳥籠の頭 (中に翼を広げた雷の鳥、頭の後ろに羽の扇、天に銅の避雷針 7 本の冠)。杖は止まり木の鉤。
白く広がる翼の輪郭と、針の冠。"""
from __future__ import annotations

from kit import *  # noqa

NAME_JP = "B 羽の司祭"
HINT = 128

PEARL = [(52, 62, 82), (98, 110, 134), (150, 162, 184), (200, 210, 226), (238, 242, 250)]
GREYB = [(24, 30, 46), (46, 56, 80), (76, 90, 118), (112, 126, 154)]
COPPER = [(48, 24, 16), (98, 50, 24), (156, 84, 36), (206, 126, 54), (242, 176, 92)]
GREEN = [(30, 56, 52), (52, 96, 82), (88, 140, 116)]
GOLD = [(230, 170, 60), (255, 222, 120), (255, 246, 200), (255, 255, 255)]
DARK = [(6, 8, 14), (14, 18, 28), (26, 32, 46)]
PAL = dict(
    mats={
        "robe": dict(ramp=GREYB, base=0.50, lit=0.30, noise=0.06, crack=0.0, min=0, facevar=0.05),
        "feather": dict(ramp=PEARL, base=0.62, lit=0.34, noise=0.06, crack=0.0, min=0, stripe=4, axis="v", facevar=0.06),
        "featherg": dict(ramp=PEARL, base=0.34, lit=0.30, noise=0.06, crack=0.0, min=0, stripe=3, axis="v", facevar=0.05),
        "copper": dict(ramp=COPPER, base=0.55, lit=0.34, noise=0.07, crack=0.0, min=0,
                       stain=dict(ramp=GREEN, thr=0.97, up=0.25, conc=0.2, low=0.1)),
        "copperd": dict(ramp=COPPER, base=0.28, lit=0.22, noise=0.06, crack=0, min=0),
        "dark": dict(ramp=DARK, base=0.5, lit=0.1, noise=0.1, crack=0, min=0),
        "bolt": dict(ramp=GOLD, base=0.45, lit=0.5, noise=0.04, crack=0, min=0),
        "skin": SKIN,
    },
    glow_mats=["bolt"],
)
STAFF_PAL = dict(mats={k: PAL["mats"][k] for k in ("copper", "copperd", "dark", "bolt", "feather")} | {
    "wood": dict(ramp=[(28, 22, 22), (58, 46, 40), (92, 74, 62), (128, 106, 88)], base=0.5, lit=0.3, noise=0.12, crack=0, min=0, stripe=6, axis="v"),
}, glow_mats=["bolt"])


def build():
    F = []
    ZC = -2.0
    H = "head"
    # ================================================================ 頭: 丸い籠
    F += tube(H, "dark", [(0, 19.4, -1.2), (0, 22.4, ZC)], [1.3, 1.1], [1.3, 1.1], sides=4, hint=(1, 0, 0), caps=(False, False))
    F += disc(H, "copper", 22.0, 1.4, 22.9, 1.9, zc=ZC, n=6, phase=0, caps=(False, True))
    for k in range(6):                       # 子午線の柱 (下で細く、腹で広く、上で寄る)
        a = math.radians(60.0 * k)
        pts = [(r * math.sin(a), y, ZC - r * math.cos(a)) for y, r in ((22.9, 1.9), (25.2, 3.5), (27.9, 1.5))]
        F += tube(H, "copper", pts, [0.4] * 3, sides=3, hint=(0, 1, 0), phase=90, caps=(False, False))
    F += disc(H, "copper", 27.6, 1.5, 28.4, 1.1, zc=ZC, n=6, phase=0, caps=(False, True))
    # 冠の避雷針 7 本 (扇形に開く)
    for i in range(7):
        t = (i - 3) / 3.0
        ang = math.radians(t * 62.0)
        d = (math.sin(ang), math.cos(ang) * 1.0, 0.0)
        ln = 5.6 - 1.7 * abs(t)
        F += sp(H, "copper", (math.sin(ang) * 0.5, 28.2, ZC), d, ln, 0.45, sides=3, rings=1)
    # 籠の中: 翼を広げた雷の鳥 (正面から見る板) と、その奥の暗い板
    F += ellipsoid(H, "dark", (0, 25.4, ZC + 1.2), (2.3, 2.9, 0.9), segs=6, rings=1)
    zb = ZC - 0.4
    F += extrude(H, "bolt", [V(0, 27.0, zb), V(0.7, 25.2, zb), V(0, 22.9, zb), V(-0.7, 25.2, zb)], V(0, 0, 0.55))
    for s in (1, -1):
        F += extrude(H, "bolt", [V(s * 0.45, 25.8, zb), V(s * 2.5, 27.6, zb), V(s * 2.0, 24.8, zb)], V(0, 0, 0.5))
    # 頭の後ろの羽の扇 (5 枚)
    for i in range(4):
        t = (i - 1.5) / 1.5
        ang = math.radians(t * 50.0)
        u = V(math.sin(ang), math.cos(ang), 0.0)
        F += leaf(H, "feather" if i % 2 == 0 else "featherg", V(math.sin(ang) * 0.4, 24.6, ZC + 3.0), u, V(math.cos(ang), -math.sin(ang), 0.0),
                  7.0 - 1.8 * abs(t), 2.6, 0.5, mid=0.45)
    # ================================================================ 胴: 肩の羽の外套 (3 段)
    B = "body"
    nM = 10
    F += disc(B, "copperd", 19.4, 3.3, 20.3, 2.6, zc=-0.6, n=6, phase=0, caps=(False, False))
    tiers = [(20.2, 3.9, 16.6, 5.0), (17.6, 4.7, 13.6, 6.0), (14.8, 5.0, 11.0, 6.8)]
    for i, (y0, r0, y1, r1) in enumerate(tiers):
        top = tring(y0, r0, -r0 * 0.78, r0 * 0.9, n=nM, zig=0.0)
        bot = tring(y1, r1, -r1 * 0.78, r1 * 0.9, n=nM, zig=0.0, dy=lambda k: -2.4 if k % 2 == 0 else 0.0)
        F += rloft(B, "feather" if i != 1 else "featherg", [top, bot], caps=(False, False))
    F += fbox(B, "copper", (0, 17.0, -4.2), (1.8, 3.2, 1.0), taper=(0.6, 0.7))
    # ================================================================ 法衣: 細い直線の裾
    R = "robe"
    nR = 10
    rows = [(14.0, 3.7, -2.9, 3.3), (8.0, 4.0, -3.3, 3.8), (3.2, 4.8, -4.0, 4.6)]
    F += rloft(R, "robe", [tring(y, rx, zf, zb, n=nR, zig=0.06) for (y, rx, zf, zb) in rows], caps=("robe", False))
    hem = tring(3.2, 4.8, -4.0, 4.6, n=nR, zig=0.06)
    hem2 = tring(0.6, 5.1, -4.3, 4.9, n=nR, dy=lambda k: -2.6 if k % 2 == 0 else 0.4)
    F += rloft(R, "featherg", [hem, hem2], caps=(False, False))
    F += poly(R, "dark", tring(0.6, 5.1, -4.3, 4.9, n=nR, dy=lambda k: 0.4), np.array([0, 10, 0]))
    for s in (1, -1):
        F += sp(R, "skin", (s * 1.8, 1.2, -3.8), (0, -0.3, -1.0), 3.0, 1.0, sides=4, rings=1)
        F += disc(R, "copper", 0.9, 1.2, 1.9, 1.1, zc=-3.9, cx=s * 1.8, n=6, caps=(False, False))
    for x, ln in ((-2.2, 5.0), (0.0, 6.2), (2.2, 5.0)):
        F += sp(R, "feather", (x, 7.0, 4.2), (0, -0.5, 0.9), ln, 1.1, sides=3, rings=1)
    # ================================================================ 腕: 細い袖と、翼のように垂れる羽板
    for s, nm in ((1, "r"), (-1, "l")):
        a, fa = f"arm_{nm}", f"forearm_{nm}"
        F += tube(a, "robe", [(s * 4.8, 18.9, 0.0), (s * 5.0, 16.2, -0.4), (s * 5.0, 13.4, -0.9)], [1.4, 1.5, 1.4], [1.5, 1.6, 1.5], sides=4,
                  hint=(0, 0, 1), phase=45, caps=(False, False))
        F += sleeve_hex(fa, "robe", s, [(12.8, -1.2, 2.6, 3.0), (11.8, -3.0, 3.0, 3.6), (11.0, -4.8, 3.6, 4.4)])
        F += sleeve_hex(fa, "copper", s, [(11.0, -4.6, 3.8, 4.6), (11.0, -5.3, 3.8, 4.6)])
        F += fist(fa, s)
        # 袖から垂れる羽板 (肘から手首に沿う、下へ向かって広がる翼の羽)
        for k, (zz, ln) in enumerate(((-1.8, 8.5), (-4.0, 7.0))):
            F += leaf(fa, "feather" if k != 1 else "featherg", V(s * 5.7, 12.0, zz), V(s * 0.18, -1, 0.0), V(0, 0, 1), ln, 1.9, 0.5, mid=0.35)
    # ================================================================ 杖: 止まり木の鉤
    S = []
    St = "staff"
    S += tube(St, "wood", [(0, -13.0, 0), (0, 24.0, 0)], [0.62, 0.62], sides=4, hint=(1, 0, 0), phase=45, caps=(True, False))
    S += sp(St, "copper", (0, -13.0, 0), (0, -1, 0), 2.6, 0.62, sides=4, rings=1)
    for y in (-1.5, 1.0):
        S += disc(St, "copper", y, 1.0, y + 0.7, 1.0, n=4, phase=45, caps=(False, False))
    crook = [(0, 24.0, 0), (0.4, 28.0, 0), (2.0, 31.0, 0), (4.6, 31.8, 0), (6.6, 30.0, 0), (6.8, 27.4, 0)]
    S += tube(St, "wood", crook, [0.62, 0.6, 0.55, 0.5, 0.45, 0.3], sides=4, hint=(0, 0, 1), phase=45, caps=(False, False))
    S += disc(St, "copper", 23.2, 1.0, 24.2, 1.3, n=6, caps=(False, True))
    S += hoop(St, "copper", V(6.8, 25.4, 0), 1.1, 4, V(1, 0, 0), V(0, 1, 0), 0.38)
    S += leaf(St, "bolt", V(6.8, 24.2, 0), V(0, -1, 0), V(1, 0, 0), 5.0, 2.0, 0.5, mid=0.4)
    return F, S, None, None
