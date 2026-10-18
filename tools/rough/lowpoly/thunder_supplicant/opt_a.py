"""A 案「風見の籠」: 鐘形の長い襞の法衣 (裾は羽の鋸歯)、銅の鳥籠の頭 (玉ねぎ屋根・天辺に風見の鶏)、
籠の中に浮かぶ稲妻。杖は銅の避雷針の三叉。縦に細長い鐘と灯籠の輪郭。"""
from __future__ import annotations

from kit import *  # noqa

NAME_JP = "A 風見の籠"
HINT = 128

SLATE = [(16, 20, 32), (30, 38, 58), (50, 62, 90), (76, 90, 120), (106, 122, 152)]
FEATH = [(70, 82, 104), (114, 128, 152), (166, 178, 198), (216, 224, 236)]
COPPER = [(48, 24, 16), (98, 50, 24), (156, 84, 36), (206, 126, 54), (242, 176, 92)]
GREEN = [(30, 56, 52), (52, 96, 82), (88, 140, 116)]     # 緑青
BOLT = [(120, 190, 255), (200, 235, 255), (255, 255, 255)]
DARK = [(6, 8, 14), (14, 18, 28), (26, 32, 46)]
PAL = dict(
    mats={
        "robe": dict(ramp=SLATE, base=0.52, lit=0.30, noise=0.06, crack=0.0, min=0, stripe=3, axis="u", facevar=0.05),
        "robeb": dict(ramp=SLATE, base=0.30, lit=0.2, noise=0.05, crack=0, min=0),
        "feather": dict(ramp=FEATH, base=0.52, lit=0.34, noise=0.08, crack=0.0, min=0, streak=0.16),
        "copper": dict(ramp=COPPER, base=0.55, lit=0.34, noise=0.07, crack=0.0, min=0,
                       stain=dict(ramp=GREEN, thr=0.97, up=0.25, conc=0.2, low=0.1)),
        "copperd": dict(ramp=COPPER, base=0.28, lit=0.22, noise=0.06, crack=0, min=0),
        "dark": dict(ramp=DARK, base=0.5, lit=0.1, noise=0.1, crack=0, min=0),
        "bolt": dict(ramp=BOLT, base=0.55, lit=0.5, noise=0.04, crack=0, min=0),
        "skin": SKIN,
    },
    glow_mats=["bolt"],
)
STAFF_PAL = dict(mats={k: PAL["mats"][k] for k in ("copper", "copperd", "dark", "bolt", "feather")} | {
    "wood": dict(ramp=[(28, 22, 22), (58, 46, 40), (92, 74, 62), (128, 106, 88)], base=0.5, lit=0.3, noise=0.12, crack=0, min=0, stripe=6, axis="v"),
}, glow_mats=["bolt"])


def build():
    F = []
    # ================================================================ 頭: 銅の鳥籠 (中心 x=0, z=-2)
    ZC = -2.0
    H = "head"
    F += tube(H, "dark", [(0, 19.4, -1.2), (0, 22.4, ZC)], [1.3, 1.1], [1.3, 1.1], sides=4, hint=(1, 0, 0), caps=(False, False))
    F += disc(H, "copper", 21.9, 2.2, 23.0, 2.7, zc=ZC, n=6, phase=0, caps=(False, True))
    # 籠の柱 6 本 (腹のふくらんだ弧)
    for k in range(6):
        a = 60.0 * k
        pts = []
        for y, r in ((23.0, 2.7), (25.4, 3.4), (28.0, 2.1)):
            ar = math.radians(a)
            pts.append((r * math.sin(ar), y, ZC - r * math.cos(ar)))
        F += tube(H, "copper", pts, [0.4] * 3, sides=3, hint=(0, 1, 0), phase=90, caps=(False, False))
    F += disc(H, "copper", 27.7, 2.2, 28.6, 2.4, zc=ZC, n=6, phase=0, caps=(False, False))
    # 玉ねぎ屋根
    dome = [circ(28.6, 2.7, ZC, n=6, phase=0), circ(29.4, 2.3, ZC, n=6, phase=0), circ(30.8, 1.2, ZC, n=6, phase=0), circ(32.0, 0.35, ZC, n=6, phase=0)]
    F += rloft(H, "copperd", dome, caps=(False, "copper"))
    # 風見の鶏
    F += tube(H, "copper", [(0, 32.0, ZC), (0, 34.4, ZC)], [0.4, 0.3], sides=3, hint=(1, 0, 0), phase=90, caps=(False, False))
    F += plate_yz(H, "copper", [(ZC - 3.6, 35.4), (ZC - 2.4, 36.6), (ZC - 0.6, 36.2), (ZC + 0.2, 35.4), (ZC - 1.4, 34.5)], 0, 0.8)
    F += plate_yz(H, "copperd", [(ZC + 0.0, 35.6), (ZC + 1.9, 38.6), (ZC + 1.1, 35.2)], 0, 0.5)
    # 籠の中: 暗い核と稲妻 (前と後ろ)
    F += ellipsoid(H, "dark", (0, 25.4, ZC), (1.8, 2.7, 1.8), segs=6, rings=1)
    for ang in (0, 180):
        pts = [(0.7, 27.4, ZC - 1.6), (-0.6, 25.8, ZC - 1.9), (0.7, 24.4, ZC - 1.8), (-0.2, 23.2, ZC - 1.5)]
        F += bolt(H, "bolt", [rotp(p, ang, (0, ZC)) for p in pts], r=0.36)
    # ================================================================ 胴: 銅の襟と胸の珠
    B = "body"
    F += disc(B, "copper", 19.4, 3.5, 20.4, 3.0, zc=-0.6, n=6, phase=0, caps=(False, False))
    for k in range(6):
        a = math.radians(60 * k)
        F += sp(B, "copperd", (3.3 * math.sin(a), 20.4, -0.6 - 3.3 * math.cos(a)),
                (math.sin(a) * 0.6, 1, -math.cos(a) * 0.6), 1.4, 0.55, sides=3, rings=1)
    F += fbox(B, "copper", (0, 15.6, -3.3), (2.6, 3.4, 1.4), taper=(0.7, 0.8), cut=0.0)
    F += fbox(B, "bolt", (0, 15.6, -4.0), (1.0, 1.7, 0.5), taper=(0.6, 0.6), cut=0.0)
    # ================================================================ 法衣: 鐘形、襞、羽の裾
    R = "robe"
    nR = 12
    rows = [  # y, rx, zf, zb, zig
        (19.6, 3.5, -2.4, 2.6, 0.0),
        (13.0, 4.0, -3.0, 3.4, 0.08),
        (7.6, 5.3, -4.3, 4.9, 0.10),
        (3.4, 6.5, -5.5, 6.1, 0.12),
    ]
    rings = [tring(y, rx, zf, zb, n=nR, zig=z) for (y, rx, zf, zb, z) in rows]
    F += rloft(R, "robe", rings, caps=("robe", False))
    hem = tring(3.4, 6.5, -5.5, 6.1, n=nR, zig=0.12)
    hem2 = tring(1.2, 6.9, -5.9, 6.5, n=nR, zig=0.0, dy=lambda k: -2.4 if k % 2 == 0 else 0.6)
    F += rloft(R, "feather", [hem, hem2], caps=(False, False))
    mid = tring(8.6, 5.2, -4.2, 4.8, n=nR, zig=0.1)
    mid2 = tring(6.6, 5.85, -4.8, 5.4, n=nR, zig=0.0, dy=lambda k: -1.5 if k % 2 == 0 else 0.5)
    F += rloft(R, "robeb", [mid, mid2], caps=(False, False))
    for s in (1, -1):
        F += sp(R, "skin", (s * 2.0, 1.2, -3.6), (0, -0.3, -1.0), 3.2, 1.1, sides=4, rings=1)
        F += disc(R, "copper", 0.9, 1.25, 1.9, 1.15, zc=-4.2, cx=s * 2.0, n=6, caps=(False, False))
    for i, x in enumerate((-1.8, 1.8)):
        F += sp(R, "feather", (x, 6.0 - abs(x) * 0.2, 4.9 + 0.2 * (i % 2)), (x * 0.04, -0.6, 0.9), 5.4, 1.2, sides=3, rings=1)
    # ================================================================ 腕 (広い袖と銅の袖口)
    for s, nm in ((1, "r"), (-1, "l")):
        a, fa = f"arm_{nm}", f"forearm_{nm}"
        F += fbox(a, "feather", (s * 5.2, 19.0, 0.0), (3.2, 1.6, 3.4), R=rot_z(-s * 30), taper=(0.7, 0.75))
        F += tube(a, "robe", [(s * 4.8, 18.9, 0.0), (s * 5.0, 16.2, -0.4), (s * 5.0, 13.4, -0.9)], [1.5, 1.7, 1.6], [1.6, 1.8, 1.7], sides=4,
                  hint=(0, 0, 1), phase=45, caps=(False, False))
        F += sleeve_hex(fa, "robe", s, [(12.9, -1.2, 2.6, 3.2), (11.6, -3.0, 3.4, 4.6), (10.6, -4.8, 4.4, 5.8)])
        F += sleeve_hex(fa, "copper", s, [(10.6, -4.6, 4.6, 6.0), (10.6, -5.4, 4.6, 6.0)])
        F += fist(fa, s)
    # ================================================================ 杖 (geo 空間の局所座標。避雷針の三叉)
    S = []
    St = "staff"
    S += tube(St, "wood", [(0, -13.0, 0), (0, 22.0, 0)], [0.62, 0.62], sides=4, hint=(1, 0, 0), phase=45, caps=(True, False))
    S += sp(St, "copper", (0, -13.0, 0), (0, -1, 0), 2.6, 0.62, sides=4, rings=1)
    for y in (-1.5, 1.0):
        S += disc(St, "copper", y, 1.0, y + 0.7, 1.0, n=4, phase=45, caps=(False, False))
    S += disc(St, "copper", 22.0, 1.0, 24.0, 1.4, n=6, caps=(False, True))
    S += sp(St, "copper", (0, 24.0, 0), (0, 1, 0), 7.0, 0.5, sides=4, rings=1)
    for sx in (1, -1):
        S += tube(St, "copper", [(sx * 0.6, 24.0, 0), (sx * 2.8, 27.0, 0), (sx * 2.2, 31.5, 0)],
                  [0.5, 0.45, 0.0], sides=3, hint=(0, 0, 1), phase=90, caps=(False, False))
    S += ellipsoid(St, "bolt", (0, 29.0, 0), (1.0, 1.6, 1.0), segs=4, rings=1)
    return F, S, None, None
