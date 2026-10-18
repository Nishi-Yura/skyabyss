"""C 案「窯の人」: 灰の民の窯 (かま) をそのまま人にした、焼き物の像。煙突の頭 (縁が煤けて、前に光る通気の覗き穴)、
輪を重ねた焼き物の腹、背に煙道の管、天辺や縁に灰がうっすらかぶる。殻が割れると煙突の口から炎が吹き上がり、体の赤い炎が透ける。
火掻き棒は粘土の握りと、先に平たい熊手の羽根。"""
from __future__ import annotations

from shapes import *  # noqa

NAME_JP = "C 窯の人"
HINT = 112

CLAY = [(30, 14, 12), (72, 32, 24), (120, 58, 38), (166, 88, 54), (208, 124, 78)]
CLAYD = [(22, 10, 10), (48, 22, 18), (82, 38, 28), (116, 56, 38)]
ASHW = [(80, 74, 72), (126, 118, 112), (170, 162, 152), (210, 204, 194)]
SOOT = [(8, 7, 9), (20, 17, 19), (38, 32, 33), (60, 50, 48)]
CHAR = [(8, 7, 10), (22, 16, 18), (40, 28, 28), (66, 42, 36)]
EMBER = [(200, 70, 20), (255, 140, 36), (255, 205, 90)]
FLAME = [(255, 130, 24), (255, 190, 64), (255, 234, 130), (255, 250, 200)]
IRON = [(14, 14, 18), (36, 36, 44), (66, 66, 78), (104, 104, 118)]

BASE = {
    "clay": dict(ramp=CLAY, base=0.5, lit=0.36, noise=0.08, crack=0.0, min=0, stripe=4, axis="v", facevar=0.05,
                 stain=dict(ramp=ASHW, thr=0.78, up=0.55, conc=0.3, low=0.0)),
    "clayd": dict(ramp=CLAYD, base=0.5, lit=0.3, noise=0.08, crack=0.0, min=0,
                  stain=dict(ramp=ASHW, thr=0.9, up=0.5, conc=0.2, low=0.0)),
    "soot": dict(ramp=SOOT, base=0.5, lit=0.25, noise=0.14, crack=0.0, min=0),
    "char": dict(ramp=CHAR, base=0.5, lit=0.25, noise=0.14, crack=0.0, min=0),
    "ember": dict(ramp=EMBER, base=0.7, lit=0.2, noise=0.04, crack=0, min=0),
    "flame": dict(ramp=FLAME, base=0.6, lit=0.3, noise=0.06, crack=0, min=0),
}
PAL_N = dict(mats=BASE, glow_mats=["ember"])
PAL_F = dict(mats=BASE, glow_mats=["ember", "flame"])
WPAL = dict(mats={
    "iron": dict(ramp=IRON, base=0.55, lit=0.34, noise=0.08, crack=0, min=0, stripe=5, axis="v"),
    "clay": BASE["clay"],
    "clayd": BASE["clayd"],
    "ember": BASE["ember"], "flame": BASE["flame"],
}, glow_mats=["ember", "flame"])

EX_N = chain_extra(
    crack2d_extra(["clay", "clayd"], cell=6.5, thr=0.12, seed=7, prob=0.35, color=(40, 18, 14)),
    crack2d_extra(["clay", "clayd"], cell=7.0, thr=0.13, seed=17, prob=0.045, min_area=60, color=(255, 150, 40), glowing=True),
    lava_extra(["char"], cell=3.6, thr=0.22, halo=0.5, seed=9, glowing=False, core=(150, 52, 26), mid=(120, 42, 24), edge=(60, 28, 24)))
EX_F = chain_extra(brick_extra(["char"], bh=5, bw=9, seed=3, hot=0.1), flame_extra(("flame",)))


def hexband(bone, mat, y0, y1, r0, r1, zc=-0.5, cx=0.0, n=6, phase=30.0, depth=1.0, caps=(False, False)):
    return rloft(bone, mat, [tring(y0, r0, -r0 * depth, r0 * depth, n=n, phase=phase, zc=zc, cx=cx),
                             tring(y1, r1, -r1 * depth, r1 * depth, n=n, phase=phase, zc=zc, cx=cx)], caps)


def paddle(bone, mat, pts, thick, z=0.0):
    """XY 平面の多角形を z 方向に押し出した板。"""
    return extrude(bone, mat, [np.array([x, y, z]) for x, y in pts], np.array([0, 0, thick]))


def build():
    F = []
    ZC = -1.0
    # ============================================================ 頭の中身: 六角の短い煙突 (口が開いて、中が赤い)
    H = "head"
    hr = [tring(28.4, 2.6, -3.0, 2.8, n=6, phase=30, zc=ZC), tring(33.4, 2.9, -3.1, 2.9, n=6, phase=30, zc=ZC),
          tring(36.0, 2.5, -2.6, 2.6, n=6, phase=30, zc=ZC)]
    F += rloft(H, "char", hr, caps=(False, False))
    F += hexband(H, "soot", 36.0, 36.9, 2.5, 2.8, zc=ZC, depth=1.0)                       # 縁
    for s in (1, -1):
        F += fbox(H, "ember", (s * 1.3, 32.6, ZC - 2.75), (1.6, 0.6, 0.5), R=rot_z(-s * 10), taper=(0.9, 0.9), caps=(False, False))
    F += fbox(H, "ember", (0, 30.2, ZC - 2.7), (2.6, 0.45, 0.4), taper=(1, 1), caps=(False, False))
    # ============================================================ 胴の中身
    B = "body"
    br = [tring(15.0, 3.2, -2.8, 2.2, n=8, phase=22.5, zc=-0.5), tring(21.5, 3.6, -3.1, 2.4, n=8, phase=22.5, zc=-0.5),
          tring(28.0, 3.0, -2.4, 1.9, n=8, phase=22.5, zc=-0.5)]
    F += rloft(B, "char", br, caps=(False, "char"))
    for s, nm in ((1, "r"), (-1, "l")):
        a, fa = f"arm_{nm}", f"forearm_{nm}"
        F += limb(a, "char", [(s * 5.0, 25.4, -0.75), (s * 6.0, 19.0, -1.0)], [1.6, 1.3], sides=5, hint=(0, 0, 1), caps=(True, False))
        F += limb(fa, "char", [(s * 6.0, 19.0, -1.0), (s * 6.0, 13.2, -2.0)], [1.3, 1.1], sides=5, hint=(0, 0, 1), caps=(False, False))
        F += fbox(fa, "char", (s * 6.0, 12.3, -2.0), (2.4, 2.6, 2.6), taper=(0.8, 0.8), cut=0.0)
    inner_legs(F, x=2.5, w=1.4, sides=4, foot=False)
    # ============================================================ 煙突の頭 (殻)
    C = "crust_head"
    rows = [(28.2, 4.2, -4.2, 4.0), (31.0, 4.2, -4.3, 4.0), (34.2, 3.6, -3.6, 3.4), (36.4, 3.0, -3.0, 2.9)]
    chim = [tring(y, rx, zf, zb, n=6, phase=30, zc=ZC) for (y, rx, zf, zb) in rows]
    F += loft_skip(C, "clay", chim, skip=lambda i, j: i == 1 and j == 5)
    # 首すぼまりの煙突と縁 (口は開いている)
    neck = [tring(36.4, 3.0, -3.0, 2.9, n=6, phase=30, zc=ZC), tring(38.8, 2.1, -2.1, 2.1, n=6, phase=30, zc=ZC),
            tring(39.6, 3.6, -3.6, 3.6, n=6, phase=30, zc=ZC), tring(41.0, 3.6, -3.6, 3.6, n=6, phase=30, zc=ZC)]
    F += rloft(C, "clay", neck[:3], caps=(False, False))
    F += rloft(C, "clayd", neck[2:], caps=(False, False))
    F += rloft(C, "soot", [tring(41.0, 3.6, -3.6, 3.6, n=6, phase=30, zc=ZC), tring(40.2, 2.6, -2.6, 2.6, n=6, phase=30, zc=ZC)], caps=(False, "soot"))
    # ============================================================ 胴の殻: 輪の重なり・襟・火口の戸・背の煙道
    C = "crust_body"
    for (y0, y1, r0, r1) in [(14.8, 18.4, 4.6, 5.2), (18.8, 22.4, 5.0, 5.6), (22.8, 26.2, 5.0, 5.4)]:
        F += hexband(C, "clay", y0, y1, r0, r1, zc=-0.5, n=8, phase=22.5, depth=0.9)
    F += hexband(C, "clayd", 26.4, 28.0, 5.6, 3.4, zc=-0.5, n=8, phase=22.5, depth=0.8, caps=(False, "clayd"))
    F += fbox(C, "clayd", (0, 19.8, -5.1), (3.6, 4.6, 1.0), taper=(0.8, 0.9))
    F += fbox(C, "soot", (0, 19.4, -5.7), (2.0, 2.4, 0.4), taper=(0.9, 0.9), caps=(False, False))
    F += tube(C, "clay", [(0, 13.0, 4.6), (0, 24.0, 4.6), (0, 33.0, 4.6)], [1.5, 1.5, 1.3], sides=6, hint=(1, 0, 0), phase=0, caps=(True, False))
    F += hexband(C, "clayd", 33.0, 34.0, 1.3, 2.1, zc=4.6, depth=1.0, caps=(False, False))
    F += hexband(C, "soot", 34.0, 33.3, 2.1, 1.3, zc=4.6, depth=1.0, caps=(False, "soot"))
    # ============================================================ 腕・脚の殻
    for s, nm in ((1, "r"), (-1, "l")):
        F += ellipsoid(f"crust_arm_{nm}", "clayd", (s * 6.4, 26.2, -0.8), (3.9, 1.7, 3.6), segs=6, rings=1)
        F += tube(f"crust_arm_{nm}", "clay", [(s * 5.8, 24.8, -0.8), (s * 6.0, 20.6, -1.0)], [2.5, 2.5], sides=6, hint=(0, 0, 1), caps=(False, True))
        F += hexband(f"crust_fore_{nm}", "clay", 18.0, 14.6, 2.3, 2.1, zc=-1.4, cx=s * 6.0, depth=1.0, caps=(False, True))
        F += hexband(f"crust_leg_{nm}", "clay", 8.4, 3.8, 2.4, 2.6, zc=-0.7, cx=s * 2.6, depth=1.0, caps=(False, True))
        F += hexband(f"crust_leg_{nm}", "clayd", 3.6, 0.0, 3.4, 3.6, zc=-0.9, cx=s * 2.7, depth=1.15, caps=(False, "clayd"))
    # ============================================================ 炎 (燃え上がり): 赤熱した輪と煙突の炎の柱
    for (y0, y1, r) in [(15.4, 16.8, 3.9), (19.6, 21.0, 4.3), (23.6, 25.0, 4.2)]:
        F += hexband("flare_body", "ember", y0, y1, r, r, zc=-0.5, n=8, phase=22.5, depth=0.95)
    F += hexband("flare_head", "ember", 36.2, 37.6, 3.4, 3.4, zc=ZC, depth=1.0)
    F += fbox("flare_body", "flame", (0, 19.8, -3.3), (3.4, 3.8, 0.7), taper=(0.9, 0.9), caps=(False, False))      # 火口の戸の隙間
    F += tube("flare_body", "soot", [(0, 13.0, 3.6), (0, 24.0, 3.6), (0, 31.0, 3.6)], [1.1, 1.1, 1.0], sides=6, hint=(1, 0, 0), phase=0, caps=(True, False))
    F += flame("flare_body", (0, 31.0, 3.6), 6.0, 1.1, lean=(0, 0, 0.1))
    for k, (dx, dz, h, lean) in enumerate([(0, 0, 9.0, 0.0), (1.8, -0.5, 6.0, 0.25), (-1.8, 0.5, 6.4, -0.25)]):
        F += flame("flare_head", (dx, 37.6, ZC + dz), h, 1.5, lean=(lean, 0, 0))
    # ============================================================ 火掻き棒: 粘土の握りと熊手の羽根
    W = "weapon"
    P = []
    P += tube(W, "iron", [(0, -9.0, 0), (0, 17.2, 0)], [0.55, 0.55], sides=4, hint=(1, 0, 0), phase=45, caps=(True, False))
    P += tube(W, "clay", [(0, -5.0, 0), (0, 4.0, 0)], [1.15, 1.15], sides=6, hint=(1, 0, 0), phase=0, caps=(True, True))
    P += hexband(W, "clayd", -6.2, -5.0, 1.8, 1.2, zc=0, depth=1.0, caps=("clayd", False))
    P += hexband(W, "clayd", 4.0, 5.4, 1.3, 1.9, zc=0, depth=1.0, caps=(False, "clayd"))
    # 熊手: 横の梁に 3 本の爪 (先が赤熱)
    P += fbox(W, "iron", (0, 17.8, 0), (6.4, 1.3, 1.2), taper=(1.0, 1.0))
    for x in (-2.4, 0.0, 2.4):
        P += spike(W, "iron", (x, 18.4, 0), (x * 0.05, 1, 0), 6.0, 0.7, sides=3, rings=2)
        P += spike(W, "ember", (x, 22.0, 0), (x * 0.03, 1, 0), 3.0, 0.62, sides=3, rings=1)
    P += ellipsoid(W, "ember", (0, 17.8, -0.2), (1.1, 1.0, 1.2), segs=5, rings=1)
    return dict(body=F, weapon=P, pal_n=PAL_N, pal_f=PAL_F, ex_n=EX_N, ex_f=EX_F, wpal=WPAL, wex=None)
