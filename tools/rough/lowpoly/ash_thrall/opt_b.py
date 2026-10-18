"""B 案「鉱滓の鍛冶像」: 溶けた鉄から出る鉱滓 (かす) が冷えて固まった、肩幅の広い鍛冶の像。
鉱滓の塊の肩当て・前掛け・平たい兜 (横長の光る覗き穴と天辺の鉱滓の棘)。殻が割れると、火床のような胸の火口が開き熱い鉄の体が出る。
火掻き棒は太い鉄の棒に鉱滓のこぶ、先は鉄床の角のような鉤。"""
from __future__ import annotations

from shapes import *  # noqa

NAME_JP = "B 鉱滓の鍛冶像"
HINT = 112

SLAG = [(16, 14, 16), (38, 32, 34), (74, 62, 58), (114, 94, 80), (158, 130, 106)]
SLAGD = [(12, 10, 12), (26, 22, 24), (48, 40, 38), (76, 62, 54)]
CHAR = [(8, 7, 10), (22, 16, 18), (40, 28, 28), (66, 42, 36)]
EMBER = [(200, 70, 20), (255, 140, 36), (255, 205, 90)]
FLAME = [(255, 130, 24), (255, 190, 64), (255, 234, 130), (255, 250, 200)]
IRON = [(14, 14, 18), (36, 36, 44), (66, 66, 78), (104, 104, 118)]
RUST = [(40, 18, 12), (84, 40, 22), (130, 70, 34), (176, 104, 52)]
WRAP = [(24, 16, 14), (50, 36, 28), (80, 58, 42)]

BASE = {
    "slag": dict(ramp=SLAG, base=0.5, lit=0.36, noise=0.14, crack=0.0, min=0, streak=0.1, facevar=0.07),
    "slagd": dict(ramp=SLAGD, base=0.5, lit=0.3, noise=0.12, crack=0.0, min=0),
    "plate": dict(ramp=IRON, base=0.5, lit=0.3, noise=0.1, crack=0.0, min=0, stripe=4, axis="v",
                  stain=dict(ramp=RUST, thr=0.9, up=0.3, conc=0.3, low=0.2)),
    "char": dict(ramp=CHAR, base=0.5, lit=0.25, noise=0.14, crack=0.0, min=0),
    "ember": dict(ramp=EMBER, base=0.7, lit=0.2, noise=0.04, crack=0, min=0),
    "flame": dict(ramp=FLAME, base=0.6, lit=0.3, noise=0.06, crack=0, min=0),
}
PAL_N = dict(mats=BASE, glow_mats=["ember"])
PAL_F = dict(mats=BASE, glow_mats=["ember", "flame"])
WPAL = dict(mats={
    "iron": dict(ramp=IRON, base=0.55, lit=0.34, noise=0.08, crack=0, min=0, stripe=5, axis="v"),
    "slag": BASE["slag"],
    "wrap": dict(ramp=WRAP, base=0.5, lit=0.3, noise=0.1, crack=0, min=0, stripe=2, axis="v"),
    "ember": BASE["ember"], "flame": BASE["flame"],
}, glow_mats=["ember", "flame"])

EX_N = chain_extra(
    crack2d_extra(["slag", "slagd"], cell=6.5, thr=0.12, seed=6, prob=0.35, color=(30, 20, 20)),
    crack2d_extra(["slag", "slagd", "plate"], cell=5.0, thr=0.2, seed=16, prob=0.07, min_area=28, color=(255, 150, 40), glowing=True),
    lava_extra(["char"], cell=3.6, thr=0.22, halo=0.5, seed=9, glowing=False, core=(150, 52, 26), mid=(120, 42, 24), edge=(60, 28, 24)))
EX_F = chain_extra(lava_extra(["char"], cell=5.2, thr=0.62, halo=1.1, seed=13, glowing=True, core=(255, 240, 170), mid=(255, 150, 44), exclude=("head",)),
                   lava_extra(["char"], cell=5.0, thr=0.2, halo=0.5, seed=23, glowing=True, bones=("head",)),
                   flame_extra(("flame",)))


def lump(bone, mat, c, r, segs=6, rings=2, squash=(1, 1, 1), jit=0.2, seed=0):
    """鉱滓の塊 (不ぞろいな楕円体)。"""
    def deform(p):
        h = math.sin((p[0] * 7.1 + p[1] * 3.3 + p[2] * 5.7 + seed) * 12.9898) * 43758.5453
        h = h - math.floor(h) - 0.5
        return p * (1 + jit * 2 * h)
    return ellipsoid(bone, mat, c, [r * q for q in squash], segs=segs, rings=rings, deform=deform)


def build():
    F = []
    ZC = -1.0
    # ============================================================ 頭の中身 (低く幅広い黒い頭と細い覗き穴の光)
    H = "head"
    hr = [tring(28.4, 3.0, -3.0, 2.8, n=8, phase=22.5, zc=ZC), tring(31.6, 3.6, -3.7, 3.2, n=8, phase=22.5, zc=ZC),
          tring(35.2, 3.0, -3.0, 2.8, n=8, phase=22.5, zc=ZC)]
    F += rloft(H, "char", hr, caps=(False, "char"))
    F += fbox(H, "ember", (0, 32.2, ZC - 3.55), (5.4, 0.9, 0.5), taper=(0.95, 0.9), caps=(False, False))
    # ============================================================ 胴: 太い樽
    B = "body"
    br = [tring(15.0, 3.6, -3.0, 2.4, n=8, phase=22.5, zc=-0.5), tring(21.5, 4.3, -3.6, 2.8, n=8, phase=22.5, zc=-0.5),
          tring(27.8, 4.2, -2.8, 2.2, n=8, phase=22.5, zc=-0.5)]
    F += rloft(B, "char", br, caps=(False, "char"))
    # ============================================================ 腕 (太い) と脚
    for s, nm in ((1, "r"), (-1, "l")):
        a, fa = f"arm_{nm}", f"forearm_{nm}"
        F += limb(a, "char", [(s * 5.0, 25.4, -0.75), (s * 6.0, 19.0, -1.0)], [2.2, 1.8], sides=5, hint=(0, 0, 1), caps=(True, False))
        F += limb(fa, "char", [(s * 6.0, 19.0, -1.0), (s * 6.0, 13.2, -2.0)], [1.8, 1.4], sides=5, hint=(0, 0, 1), caps=(False, False))
        F += fbox(fa, "char", (s * 6.0, 12.2, -2.0), (3.0, 3.0, 3.0), taper=(0.8, 0.8), cut=0.0)
    inner_legs(F, x=2.6, w=1.9, sides=4, foot=False)
    # ============================================================ 兜 (殻): 平たい鉄床のような箱形
    C = "crust_head"
    rows = [(28.2, 5.0, -5.0, 4.6), (31.0, 5.5, -5.5, 5.0), (34.4, 5.2, -5.2, 4.8), (36.6, 4.6, -4.4, 4.2)]
    helm = [tring(y, rx, zf, zb, n=8, phase=22.5, zc=ZC) for (y, rx, zf, zb) in rows]
    F += loft_skip(C, "slag", helm, skip=lambda i, j: i == 1 and j == 7 or i == 0 and j in (6, 7, 0), caps=(False, "slagd"))
    # 鉱滓の棘の冠
    for k, (x, z, h, lx) in enumerate([(0, 0, 3.8, 0), (2.4, 0.4, 2.8, 0.25), (-2.4, 0.4, 3.0, -0.25), (1.2, -2.0, 2.4, 0.15), (-1.2, -2.0, 2.2, -0.15)]):
        F += spike(C, "slag", (x, 36.4, ZC + z), (lx, 1, 0), h, 1.0, sides=3, rings=1)
    # 兜のひさし
    F += fbox(C, "slagd", (0, 32.9, ZC - 5.4), (7.6, 0.8, 0.9), taper=(1.0, 0.8))
    # ============================================================ 胴の殻: 胸甲・前掛け・背のこぶ
    C = "crust_body"
    chest = [tring(15.6, 5.0, -4.4, 3.4, n=8, phase=22.5, zc=-0.5), tring(21.6, 6.0, -5.0, 3.8, n=8, phase=22.5, zc=-0.5),
             tring(28.0, 5.6, -4.0, 3.2, n=8, phase=22.5, zc=-0.5)]
    F += loft_skip(C, "slag", chest, skip=lambda i, j: i == 0 and j == 7, caps=(False, "slagd"))
    for k, (x, y, w, h) in enumerate([(-2.4, 11.0, 2.8, 7.0), (0.0, 10.4, 2.8, 8.2), (2.4, 11.0, 2.8, 7.0)]):
        F += fbox(C, "plate", (x, y, -4.8), (w, h, 0.9), R=rot_x(-4), taper=(0.8, 1.0))
    F += lump(C, "slag", (0, 24.0, 4.2), 3.6, squash=(1.3, 0.9, 0.8), jit=0.15, seed=1)
    F += lump(C, "slagd", (-2.5, 18.6, 3.9), 2.2, squash=(1, 1, 0.8), jit=0.15, seed=3)
    # ============================================================ 腕・脚の殻
    for s, nm in ((1, "r"), (-1, "l")):
        F += lump(f"crust_arm_{nm}", "slag", (s * 7.0, 26.0, -0.8), 4.0, segs=6, rings=2, squash=(1.15, 0.75, 1.05), jit=0.18, seed=5 + s)
        F += spike(f"crust_arm_{nm}", "slag", (s * 7.4, 28.0, -0.8), (s * 0.5, 1, 0), 3.4, 1.0, sides=3, rings=1)
        F += fbox(f"crust_arm_{nm}", "slagd", (s * 7.0, 21.6, -0.9), (3.6, 3.2, 4.0), taper=(0.8, 0.8))
        F += fbox(f"crust_fore_{nm}", "plate", (s * 6.0, 16.4, -1.3), (4.2, 4.6, 4.4), taper=(0.82, 0.82))
        F += fbox(f"crust_fore_{nm}", "slag", (s * 6.0, 13.6, -2.2), (3.4, 1.8, 3.6), taper=(1.0, 1.0))
        F += lump(f"crust_leg_{nm}", "slag", (s * 2.8, 9.4, -1.0), 3.2, segs=5, rings=1, squash=(1.0, 1.1, 1.0), jit=0.15, seed=9 + s)
        F += fbox(f"crust_leg_{nm}", "slagd", (s * 2.7, 2.6, -1.5), (4.4, 4.6, 6.0), taper=(0.85, 0.8))
    # ============================================================ 炎・火口 (燃え上がり): 炉の戸が開き、肩の煙突から炎、頭から曲がった炎の角
    F += fbox("flare_body", "flame", (0, 21.5, -4.0), (4.8, 4.0, 0.8), taper=(0.9, 0.9), caps=(False, False))
    for s in (1, -1):
        F += fbox("flare_body", "slagd", (s * 3.9, 21.5, -4.9), (0.9, 4.4, 2.6), R=rot_y(s * 42), taper=(0.9, 0.8))       # 開いた炉の戸
        F += tube("flare_body", "slagd", [(s * 5.0, 26.5, 1.8), (s * 5.2, 30.5, 1.8), (s * 5.2, 33.0, 1.8)], [1.5, 1.4, 1.7], sides=5, hint=(1, 0, 0), phase=0, caps=(True, False))
        F += flame("flare_body", (s * 5.2, 33.0, 1.8), 6.5, 1.3, lean=(s * 0.25, 0, 0.1))
        F += ribbon("flare_head", "flame", [(s * 3.2, 33.5, ZC), (s * 6.6, 35.0, ZC), (s * 7.6, 38.4, ZC), (s * 5.8, 42.0, ZC)], w=1.5, t=0.55, hint=(0, 0, 1))
    F += flame("flare_body", (0, 26.0, 2.6), 4.4, 1.2, lean=(0, 0, 0.5))
    F += flame("flare_head", (0, 36.0, ZC), 3.6, 0.9)
    # ============================================================ 火掻き棒: 太い鉄の棒・鉱滓のこぶ・鉄床の角の鉤
    W = "weapon"
    P = []
    P += tube(W, "iron", [(0, -9.0, 0), (0, 16.0, 0)], [0.8, 0.8], sides=4, hint=(1, 0, 0), phase=45, caps=(True, False))
    P += tube(W, "wrap", [(0, -3.0, 0), (0, 4.5, 0)], [1.2, 1.2], sides=6, hint=(1, 0, 0), phase=0, caps=(True, True))
    P += ellipsoid(W, "iron", (0, -10.2, 0), (1.7, 1.7, 1.7), segs=6, rings=1)
    P += lump(W, "slag", (0, 10.0, 0), 1.8, segs=5, rings=1, squash=(1, 1.3, 1), jit=0.2, seed=2)
    P += disc(W, "iron", 15.4, 0.9, 16.6, 1.5, n=6, phase=0, caps=(False, True))
    # 鉤: 平たい角 (YZ 平面)
    P += plate_yz(W, "iron", [(-0.9, 16.5), (1.6, 19.5), (4.4, 21.0), (6.4, 20.2), (4.6, 19.2), (2.4, 17.6), (0.9, 16.5)], 0.0, 1.0)
    P += ellipsoid(W, "ember", (0, 18.5, 3.2), (1.1, 1.3, 1.5), segs=5, rings=1)
    P += flame(W, (0, 19.6, 3.0), 3.6, 0.8, lean=(0, 0, 0.2))
    return dict(body=F, weapon=P, pal_n=PAL_N, pal_f=PAL_F, ex_n=EX_N, ex_f=EX_F, wpal=WPAL, wex=None)
