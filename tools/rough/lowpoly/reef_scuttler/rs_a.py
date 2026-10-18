"""A案「岩礁蟹」: 今の磯走りに一番忠実。銅色の平たい甲羅 (面の割れた 4 段)、背の海石の板、縁のフジツボ、
二本の目の柄 (先が青白く光る)、前へ出た大小の鋏、太い関節の細い脚 3 本ずつ。"""
from scut import *  # noqa

CHI = [(44, 16, 16), (92, 32, 22), (146, 56, 28), (196, 88, 36), (232, 128, 58), (248, 176, 98)]
CHI_D = [(34, 18, 20), (70, 32, 24), (108, 50, 30), (146, 76, 38), (180, 104, 52)]
SLATE = [(34, 40, 48), (68, 76, 88), (104, 114, 126), (142, 152, 162), (182, 190, 198)]
BARN = [(70, 64, 58), (128, 120, 106), (180, 172, 150), (218, 210, 184), (240, 234, 212)]
ALGAE = [(28, 58, 52), (44, 90, 72), (70, 124, 92)]
PAL = {"mats": {
    "chi": dict(ramp=CHI, base=0.62, lit=0.16, crack=0.0, streak=0.10, stain=dict(ramp=ALGAE, thr=1.0, up=0.25, conc=0.1)),
    "chi_d": dict(ramp=CHI_D, base=0.68, lit=0.14, crack=0.0, min=0),
    "slate": dict(ramp=SLATE, base=0.68, lit=0.15, crack=0.34, stain=dict(ramp=ALGAE, thr=0.93, up=0.3, conc=0.2)),
    "barn": dict(ramp=BARN, base=0.72, lit=0.12, crack=0.0, min=0, noise=0.1),
    "tooth": dict(ramp=BARN[1:], base=0.78, crack=0.0, min=0),
    "dark": dict(ramp=[(8, 8, 16), (22, 16, 22), (40, 26, 28)], base=0.5, crack=0.0, min=0),
}}
HINT = 128
VIEW = dict(cen=(0, 4.6, -1.5), sc=16.5, claw_cen=(0, 3.6, -9.5), claw_sc=34.0)


def hexring(c, w, h, z, n=6, rot=0.0):
    out = []
    for k in range(n):
        a = math.radians(rot + 360.0 * k / n)
        out.append(V(c[0] + w * math.cos(a), c[1] + h * math.sin(a), z))
    return out


def claw(s, big):
    b = f"claw_{side(s)}"
    k = 1.18 if big else 1.0
    F = []
    cx = s * 5.0
    F += tube(b, "chi", [(s * 3.4, 3.9, -4.0), (s * 4.4, 3.8, -5.4), (cx, 3.7, -6.9)], ra=[1.1, 1.0 * k, 1.1 * k],
              rb=[1.0, 0.95, 1.0 * k], sides=4, hint=(0, 1, 0), caps=(False, False))
    R = [hexring((cx, 3.7), 1.0 * k, 1.2 * k, -6.9, rot=0), hexring((cx, 3.7), 1.7 * k, 2.0 * k, -8.2, rot=0),
         hexring((cx, 3.8), 1.3 * k, 1.5 * k, -9.3, rot=0)]
    F += loft(b, "chi", R, caps=("chi", "chi_d"))
    # 指: 上が動く指、下が固定の指。内側に歯
    top = [(cx, 4.5 * 1.0 + 0.1, -9.0), (cx, 4.9 + 0.3 * k, -10.6), (cx - s * 0.2, 4.3, -12.0 * (0.9 + 0.1 * k))]
    low = [(cx, 3.0, -9.0), (cx, 2.5 - 0.1 * k, -10.6), (cx - s * 0.2, 3.1, -11.8 * (0.9 + 0.1 * k))]
    F += tube(b, "chi", top, ra=[0.95 * k, 0.65 * k, 0.0], rb=[0.8 * k, 0.55 * k, 0.0], sides=4, hint=(1, 0, 0))
    F += tube(b, "chi", low, ra=[0.95 * k, 0.65 * k, 0.0], rb=[0.8 * k, 0.55 * k, 0.0], sides=4, hint=(1, 0, 0))
    for z in (-9.7, -10.6):
        F += spike(b, "tooth", (cx, 4.1, z), (0, -1, -0.1), 0.9, 0.35, sides=3, rings=1)
        F += spike(b, "tooth", (cx, 3.3, z - 0.3), (0, 1, -0.1), 0.9, 0.35, sides=3, rings=1)
    # 関節のこぶ (フジツボ)
    F += barnacle(b, "barn", (s * 4.4, 4.5, -5.3), (0.3 * s, 1, -0.2), 1.2, 0.7)
    F += barnacle(b, "barn", (cx + s * 0.9, 5.5 * k - 0.3, -8.1), (0.4 * s, 1, -0.1), 1.3, 0.8)
    return F


def build():
    F = []
    B = "body"
    # ---- 甲羅 (4 段の面取りの輪)
    R = [carapace_ring(3.0, 3.8, 3.2, 3.0), carapace_ring(3.9, 5.1, 4.3, 3.8, corner=0.88),
         carapace_ring(5.3, 4.5, 3.7, 3.4), carapace_ring(6.3, 3.0, 2.4, 2.4, corner=0.7)]
    R[2][0] += V(0, 0.15, -0.9)          # 額のとがり
    R[1][0] += V(0, 0, -0.5)
    R[1][2] += V(0.5, 0.1, 0.0)          # 横のとげの根
    R[1][6] += V(-0.5, 0.1, 0.0)
    R[3][4] += V(0, 0.2, 0.3)
    F += loft(B, "chi", R, caps=("chi_d", "chi"))
    # 側面の棘
    for s in (1, -1):
        F += spike(B, "chi_d", (s * 5.2, 4.0, 0.0), (s, 0.35, -0.25), 2.0, 0.8, sides=3, rings=1)
        F += spike(B, "chi_d", (s * 4.5, 4.0, -2.2), (s * 0.7, 0.45, -0.6), 1.6, 0.65, sides=3, rings=1)
    # ---- 背の海石の板 (斜めに乗る)
    S = [carapace_ring(6.1, 2.6, 1.6, 1.8, cz=1.6), carapace_ring(6.9, 2.1, 1.2, 1.4, cz=1.8)]
    S[1][0] += V(0, 0.4, -0.2)
    S[1][2] += V(0, 0.5, 0.0)
    F += loft(B, "slate", S, caps=("slate", "slate"))
    F += loft(B, "slate", [carapace_ring(6.1, 1.7, 1.2, 1.2, cz=-0.4), carapace_ring(6.7, 1.1, 0.8, 0.8, cz=-0.2)],
              caps=("slate", "slate"))
    # ---- 目の柄と光る目
    for s in (1, -1):
        base = V(s * 1.7, 6.0, -2.3)
        top = V(s * 2.1, 9.0, -3.1)
        F += tube(B, "chi", [base, (s * 1.9, 7.6, -2.7), top], ra=[0.85, 0.6, 0.55], rb=[0.85, 0.6, 0.55], sides=4,
                  hint=(1, 0, 0))
        F += ellipsoid(B, "lamp", top + V(0, 0.55, -0.1), (0.8, 0.9, 0.8), segs=6, rings=1)
    # ---- フジツボ (肩・背)
    for p, d in (((3.3, 5.7, -1.5), (0.5, 1, -0.2)), ((-3.5, 5.6, 0.8), (-0.5, 1, 0)), ((3.6, 5.5, 1.8), (0.5, 1, 0.2)),
                 ((-2.6, 5.9, -2.0), (-0.3, 1, -0.3)), ((0.2, 6.1, 3.6), (0, 1, 0.5)), ((-1.2, 5.5, 3.5), (-0.2, 0.8, 0.8)),
                 ((4.6, 4.9, 0.6), (1, 0.6, 0)), ((-4.8, 4.8, -0.8), (-1, 0.6, 0))):
        F += barnacle(B, "barn", p, d, 1.2, 0.8)
    # ---- 口 (前の下)
    for s in (1, -1):
        F += spike(B, "tooth", (s * 0.9, 3.5, -3.7), (s * 0.15, -0.6, -1), 1.5, 0.45, sides=3, rings=1)
    # ---- 脚 (3 本ずつ)
    for s in (1, -1):
        b = f"leg_{side(s)}"
        for zc in (2.5, 0.5, -1.6):
            k = (zc - 0.5) / 2.0
            pts = [(s * 4.4, 4.3, zc), (s * 7.2, 6.0, zc + 0.35 * k), (s * 9.2, 3.5, zc + 0.8 * k), (s * 9.9, 0.0, zc + 1.1 * k)]
            F += leg_chain(b, "chi", pts, [1.15, 0.95, 0.7, 0.0])
    # 鋏 (右が大きい)
    F += claw(1, True)
    F += claw(-1, False)
    return F
