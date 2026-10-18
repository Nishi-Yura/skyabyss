"""A 案「灰衣の火番」: 灰をかぶった頭巾と外套の火の番。灰の板を重ねた尖り頭巾・肩の板・裾の欠け。
殻が割れると、黒く焦げた体に溶岩のひびが走り、肩と頭から炎が上がる。火掻き棒は鉄の棒・青銅の輪・先に燠火。"""
from __future__ import annotations

from shapes import *  # noqa

NAME_JP = "A 灰衣の火番"
HINT = 112

ASH = [(38, 36, 40), (76, 72, 74), (116, 110, 108), (156, 150, 144), (196, 190, 180)]
CHAR = [(8, 7, 10), (20, 16, 20), (36, 27, 29), (60, 40, 36)]
EMBER = [(200, 70, 20), (255, 140, 36), (255, 205, 90)]
FLAME = [(255, 130, 24), (255, 190, 64), (255, 234, 130), (255, 250, 200)]
IRON = [(14, 14, 18), (36, 36, 44), (66, 66, 78), (102, 102, 116)]
BRONZE = [(48, 28, 12), (92, 58, 22), (146, 96, 38), (200, 146, 66)]
WRAP = [(26, 18, 16), (52, 38, 30), (82, 60, 44)]

BASE = {
    "ash": dict(ramp=ASH, base=0.55, lit=0.34, noise=0.07, crack=0.0, min=0, streak=0.12),
    "ashd": dict(ramp=ASH, base=0.34, lit=0.22, noise=0.06, crack=0.0, min=0),
    "char": dict(ramp=CHAR, base=0.5, lit=0.25, noise=0.14, crack=0.0, min=0),
    "ember": dict(ramp=EMBER, base=0.7, lit=0.2, noise=0.04, crack=0, min=0),
    "flame": dict(ramp=FLAME, base=0.6, lit=0.3, noise=0.06, crack=0, min=0),
}
PAL_N = dict(mats=BASE, glow_mats=["ember"])
PAL_F = dict(mats=BASE, glow_mats=["ember", "flame"])
WPAL = dict(mats={
    "iron": dict(ramp=IRON, base=0.55, lit=0.34, noise=0.08, crack=0, min=0, stripe=5, axis="v"),
    "bronze": dict(ramp=BRONZE, base=0.55, lit=0.34, noise=0.06, crack=0, min=0),
    "wrap": dict(ramp=WRAP, base=0.5, lit=0.3, noise=0.1, crack=0, min=0, stripe=2, axis="v"),
    "ember": BASE["ember"], "flame": BASE["flame"],
}, glow_mats=["ember", "flame"])

EX_N = chain_extra(crack2d_extra(["ash", "ashd"], cell=6.0, thr=0.12, seed=5, prob=0.45, color=(44, 40, 42)),
                   crack2d_extra(["ash", "ashd"], cell=7.0, thr=0.13, seed=15, prob=0.14, min_area=40, color=(176, 62, 28)),
                   lava_extra(["char"], cell=3.6, thr=0.22, halo=0.5, seed=9, glowing=False,
                              core=(150, 52, 26), mid=(120, 42, 24), edge=(60, 28, 24)))
EX_F = chain_extra(lava_extra(["char"], cell=3.3, thr=0.36, halo=0.85, seed=11, glowing=True, spark=0.02, exclude=("head",)),
                   lava_extra(["char"], cell=4.6, thr=0.2, halo=0.5, seed=21, glowing=True, bones=("head",)),
                   flame_extra(("flame",)))


def build():
    F = []
    ZC = -1.0
    # ============================================================ 頭 (殻の下の黒い顔)
    H = "head"
    hr = [tring(28.4, 2.3, -2.4, 2.2, n=8, phase=22.5, zc=ZC), tring(31.6, 3.0, -3.3, 2.8, n=8, phase=22.5, zc=ZC),
          tring(35.4, 2.0, -2.4, 2.0, n=8, phase=22.5, zc=ZC)]
    F += rloft(H, "char", hr, caps=(False, "char"))
    for s in (1, -1):
        F += fbox(H, "ember", (s * 1.3, 32.6, ZC - 3.2), (1.5, 0.9, 0.5), R=rot_z(-s * 14), taper=(0.9, 0.9), caps=(False, False))
    # ============================================================ 胴 (黒い体)
    B = "body"
    br = [tring(15.0, 3.0, -2.6, 2.0, n=8, phase=22.5, zc=-0.5), tring(22.0, 3.6, -3.1, 2.3, n=8, phase=22.5, zc=-0.5),
          tring(28.0, 3.0, -2.4, 1.9, n=8, phase=22.5, zc=-0.5)]
    F += rloft(B, "char", br, caps=(False, "char"))
    # ============================================================ 腕・脚の中身
    for s, nm in ((1, "r"), (-1, "l")):
        a, fa = f"arm_{nm}", f"forearm_{nm}"
        F += limb(a, "char", [(s * 5.0, 25.4, -0.75), (s * 6.0, 19.0, -1.0)], [1.8, 1.4], sides=5, hint=(0, 0, 1), caps=(True, False))
        F += limb(fa, "char", [(s * 6.0, 19.0, -1.0), (s * 6.0, 13.2, -2.0)], [1.4, 1.15], sides=5, hint=(0, 0, 1), caps=(False, False))
        F += fbox(fa, "char", (s * 6.0, 12.3, -2.0), (2.4, 2.6, 2.6), taper=(0.8, 0.8), cut=0.0)
    inner_legs(F, x=2.5, w=1.6, sides=5)
    # ============================================================ 頭巾 (殻): 尖った灰の頭巾、前が開く
    C = "crust_head"
    rows = [(28.3, 4.7, -4.9, 4.5, 0.0), (31.0, 4.5, -5.2, 4.7, 0.0), (34.0, 3.5, -4.4, 4.0, 0.0), (36.8, 2.0, -2.8, 2.8, 0.0),
            (39.4, 0.9, -1.4, 1.6, 0.0)]
    hood = [tring(y, rx, zf, zb, n=8, phase=0, zc=ZC + (y - 28) * 0.05) for (y, rx, zf, zb, _) in rows]
    F += loft_skip(C, "ash", hood, skip=lambda i, j: j in (7, 0), caps=(False, "ashd"))
    # 頭巾の縁の板 (顔の両側に垂れる)
    for s in (1, -1):
        F += fbox(C, "ashd", (s * 5.0, 29.5, ZC - 3.4), (1.2, 4.0, 3.0), R=rot_z(-s * 8), taper=(0.6, 0.6))
    F += sp(C, "ash", (0, 39.4, ZC + 1.6), (0, 1.0, 0.7), 3.2, 0.8, sides=3, rings=1)       # 頭巾の先の折れ
    # ============================================================ 外套 (殻): 肩から裾まで
    C = "crust_body"
    crows = [(27.8, 4.7, -3.5, 3.1), (23.0, 5.1, -4.0, 3.6), (15.5, 5.3, -4.5, 4.3), (8.5, 5.5, -5.0, 4.9)]
    cloak = [tring(y, rx, zf, zb, n=12, phase=0, zc=-0.5) for (y, rx, zf, zb) in crows]
    F += loft_skip(C, "ash", cloak, skip=lambda i, j: i >= 1 and j in (11, 0))
    hem = tring(8.5, 5.5, -5.0, 4.9, n=12, phase=0, zc=-0.5)
    hem2 = tring(5.6, 5.9, -5.4, 5.3, n=12, phase=0, zc=-0.5, dy=lambda k: -2.6 if k % 2 == 0 else 0.4)
    F += loft_skip(C, "ashd", [hem, hem2], skip=lambda i, j: j in (11, 0))
    # 肩の外套の板 (2 段)
    mantle = [tring(28.2, 3.4, -3.2, 2.6, n=8, phase=22.5, zc=-0.5), tring(26.2, 6.3, -4.4, 3.8, n=8, phase=22.5, zc=-0.5)]
    F += rloft(C, "ashd", mantle, caps=("ash", False))
    # 前の垂れ (胸の割れ目の縁)
    for s in (1, -1):
        F += fbox(C, "ashd", (s * 3.5, 19.0, -4.7), (2.2, 7.5, 0.9), R=rot_z(s * 4), taper=(0.7, 1.0))
    # ============================================================ 腕・脚の殻
    for s, nm in ((1, "r"), (-1, "l")):
        F += fbox(f"crust_arm_{nm}", "ash", (s * 6.2, 26.2, -0.8), (5.6, 1.5, 5.4), R=rot_z(-s * 16), taper=(0.78, 0.78), cut=0.4)
        F += fbox(f"crust_arm_{nm}", "ashd", (s * 6.9, 24.2, -0.9), (4.8, 1.3, 4.8), R=rot_z(-s * 24), taper=(0.78, 0.78))
        F += fbox(f"crust_fore_{nm}", "ash", (s * 6.0, 16.6, -1.2), (3.6, 3.8, 4.0), taper=(0.78, 0.78))
        F += fbox(f"crust_leg_{nm}", "ash", (s * 2.6, 3.4, -1.0), (3.6, 4.6, 4.2), taper=(0.78, 0.8))
    # ============================================================ 炎 (燃え上がりの姿だけ)
    # 頭巾の形に燃える炎 (両側から立ち上がり頭の上で寄る) と、背へ流れる炎の外套
    for s in (1, -1):
        F += ribbon("flare_head", "flame", [(s * 3.8, 29.5, ZC), (s * 5.6, 33.0, ZC - 0.3), (s * 3.8, 37.0, ZC), (s * 4.3, 40.5, ZC + 0.4), (s * 1.0, 43.5, ZC + 0.8)],
                    w=1.5, t=0.5, hint=(0, 0, 1))
    F += flame("flare_head", (0, 36.0, ZC), 5.0, 0.8)
    for x, ln in ((-3.4, 11.0), (-1.2, 6.0), (1.2, 8.0), (3.4, 13.0)):
        F += ribbon("flare_body", "flame", [(x, 26.8, 2.2), (x * 1.15, 21.0, 3.4), (x * 1.3, 26.8 - ln - 6, 4.2 + 0.3 * abs(x))],
                    w=1.5, t=0.45, hint=(1, 0, 0))
    for s in (1, -1):
        F += flame("flare_body", (s * 5.4, 27.0, -0.8), 5.0, 1.1, lean=(s * 0.4, 0, 0))
    # ============================================================ 火掻き棒 (geo 局所座標: 前 = +Z)
    W = "weapon"
    P = []
    P += tube(W, "iron", [(0, -9.0, 0), (0, 17.0, 0)], [0.6, 0.6], sides=4, hint=(1, 0, 0), phase=45, caps=(True, False))
    P += tube(W, "wrap", [(0, -3.5, 0), (0, 4.0, 0)], [0.95, 0.95], sides=6, hint=(1, 0, 0), phase=0, caps=(True, True))
    P += hoop(W, "iron", (0, -10.2, 0), 1.5, 6, (1, 0, 0), (0, 1, 0), 0.3, sides=3)
    for y in (6.0, 12.0):
        P += disc(W, "bronze", y, 1.15, y + 1.0, 1.05, n=6, phase=0, caps=(False, False))
    P += disc(W, "bronze", 16.2, 0.9, 17.4, 1.3, n=6, phase=0, caps=(False, True))
    P += tube(W, "iron", [(0, 17.4, 0), (0, 20.2, 0.5), (0, 23.0, 2.6), (0, 22.6, 5.2), (0, 20.0, 6.2)], [0.6, 0.58, 0.55, 0.46, 0.0],
              sides=3, hint=(1, 0, 0), phase=90, caps=(False, False))
    P += ellipsoid(W, "ember", (0, 22.4, 1.0), (1.5, 1.9, 1.5), segs=5, rings=1)
    P += flame(W, (0, 24.0, 1.0), 4.8, 1.0, lean=(0, 0, 0.2))
    return dict(body=F, weapon=P, pal_n=PAL_N, pal_f=PAL_F, ex_n=EX_N, ex_f=EX_F, wpal=WPAL, wex=None)
