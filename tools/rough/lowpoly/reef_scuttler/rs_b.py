"""B案「真珠の扇」: 背に貝の扇 (ホタテのような筋のある白い板) を斜めに立てて背負う、青灰色の石の楔形の蟹。
鋏は細く反った鎌 (象牙色)、脚は短く太くフジツボのこぶ付き。目は甲羅の前の縁から覗く短い柄。"""
from scut import *  # noqa

STONE = [(24, 34, 48), (50, 68, 88), (82, 106, 128), (118, 146, 164), (160, 186, 198)]
PEARL = [(78, 70, 88), (140, 128, 144), (192, 182, 190), (230, 222, 220), (252, 246, 238)]
PINK = [(120, 66, 92), (178, 106, 126), (220, 148, 156), (244, 190, 184)]
IVORY = [(70, 56, 50), (128, 108, 90), (184, 164, 132), (224, 210, 176), (248, 240, 208)]
RUST = [(36, 18, 18), (74, 36, 26), (118, 60, 34), (160, 90, 46)]
ALGAE = [(24, 56, 58), (40, 88, 82), (66, 122, 104)]
PAL = {"mats": {
    "stone": dict(ramp=STONE, base=0.66, lit=0.16, crack=0.28, noise=0.12, facevar=0.07, stain=dict(ramp=ALGAE, thr=0.98, up=0.25, conc=0.12)),
    "pearl": dict(ramp=PEARL, base=0.70, lit=0.13, crack=0.0, noise=0.04, streak=0.0, min=0),
    "hinge": dict(ramp=PINK, base=0.62, lit=0.14, crack=0.0, min=0),
    "ivory": dict(ramp=IVORY, base=0.68, lit=0.14, crack=0.0, streak=0.05),
    "rust": dict(ramp=RUST, base=0.62, lit=0.12, crack=0.0, min=0),
    "barn": dict(ramp=PEARL, base=0.62, lit=0.12, crack=0.0, min=0),
    "dark": dict(ramp=[(8, 10, 18), (18, 22, 32), (32, 40, 52)], base=0.5, crack=0.0, min=0),
}}
HINT = 128
VIEW = dict(cen=(0, 5.2, -1.5), sc=17.0, claw_cen=(0, 3.4, -9.5), claw_sc=32.0)


def hexring(c, w, h, z, n=6, rot=0.0):
    return [V(c[0] + w * math.cos(math.radians(rot + 360.0 * k / n)), c[1] + h * math.sin(math.radians(rot + 360.0 * k / n)), z)
            for k in range(n)]


def fan(bone, H, tilt, R, span=62.0, ribs=6, bump=0.55):
    """ホタテの扇。H = 蝶番の点、tilt = 水平からの起き上がり (度、後ろへ倒れる)。"""
    t = math.radians(tilt)
    v = V(0, math.sin(t), math.cos(t))          # 蝶番から縁へ (後ろ +Z、上)
    u = V(1, 0, 0)
    n = nrm(np.cross(u, v))                      # 上の面の外向き (後ろ上)
    if n[1] < 0:
        n = -n
    cols = ribs * 2 + 1
    angs = [math.radians(-span + 2 * span * k / (cols - 1)) for k in range(cols)]
    rows = [0.0, 0.42, 1.0]

    def P(r, k, off):
        a = angs[k]
        ridge = (k % 2 == 0)
        rr = R * r * (1.0 if ridge else 0.9 + 0.03 * r)
        up = (bump if ridge else 0.0) * math.sin(math.pi * min(r, 1.0) * 0.5 + 0.25) * (0.35 + r)
        return H + rr * (math.cos(a) * v + math.sin(a) * u) + n * (up + off)

    top0 = [[P(r, k, 0.0) for k in range(cols)] for r in rows]
    F = []
    ref_in = H - n * 3.0
    F += grid(bone, "hinge", top0[:2], ref_in)
    F += grid(bone, "pearl", top0[1:], ref_in)
    # 裏 (平ら)
    under = [P(1.0, k, -0.6) for k in range(cols)]
    ref_up = H + n * 3.0
    und = [[P(0.0, k, -0.6) for k in range(cols)], [P(1.0, k, -0.6 - (bump * 0.8 if k % 2 == 0 else 0.0)) for k in range(cols)]]
    F += grid(bone, "hinge", und, ref_up)
    # 縁の壁
    rim = [[P(1.0, k, 0.0) for k in range(cols)], under]
    F += grid(bone, "pearl", rim, H + v * R * 0.4)
    return F


def sickle(s, big):
    b = f"claw_{side(s)}"
    F = []
    cx = s * 5.4
    F += tube(b, "stone", [(s * 3.4, 3.9, -4.0), (s * 4.5, 3.8, -5.2), (cx, 3.7, -6.4)], ra=[1.0, 0.85, 0.9], rb=[0.9, 0.8, 0.85],
              sides=4, hint=(0, 1, 0))
    R = [hexring((cx, 3.7), 0.9, 1.0, -6.4), hexring((cx, 3.7), 1.2, 1.35, -7.6), hexring((cx, 3.7), 0.9, 1.0, -8.6)]
    F += loft(b, "ivory", R, caps=("ivory", "ivory"))
    # 反った鎌 2 枚 (上は長く下へ曲がる、下は短く上へ曲がる)
    up = [(cx, 4.2, -8.4), (cx - s * 0.1, 5.3, -10.4), (cx - s * 0.6, 5.1, -12.6), (cx - s * 1.3, 3.9, -13.9)]
    lo = [(cx, 3.1, -8.4), (cx - s * 0.1, 2.3, -10.2), (cx - s * 0.5, 2.5, -11.8), (cx - s * 1.0, 3.3, -12.6)]
    F += tube(b, "ivory", up, ra=[0.85, 0.7, 0.5, 0.0], rb=[0.55, 0.5, 0.35, 0.0], sides=4, hint=(1, 0, 0))
    F += tube(b, "ivory", lo, ra=[0.8, 0.62, 0.42, 0.0], rb=[0.5, 0.42, 0.3, 0.0], sides=4, hint=(1, 0, 0))
    for z, y in ((-9.8, 4.9), (-11.0, 5.0)):
        F += spike(b, "rust", (cx - s * 0.2, y - 0.2, z), (0, -1, -0.35), 1.0, 0.32, sides=3, rings=1)
    F += spike(b, "rust", (cx - s * 0.2, 2.6, -10.8), (0, 1, -0.3), 0.9, 0.3, sides=3, rings=1)
    F += barnacle(b, "barn", (s * 4.5, 4.55, -5.2), (0.3 * s, 1, 0), 1.0, 0.6)
    return F


def build():
    F = []
    B = "body"
    # ---- 楔形の石の体 (前が尖る)
    R = [carapace_ring(3.0, 3.6, 4.2, 3.0, corner=0.8, fc=0.45), carapace_ring(3.9, 4.6, 4.8, 3.6, corner=0.82, fc=0.42),
         carapace_ring(5.0, 3.9, 4.0, 3.2, corner=0.7, fc=0.45),
         carapace_ring(5.7, 2.2, 2.4, 1.9, corner=0.7, fc=0.5, cz=-0.3)]
    R[1][0] += V(0, 0, -0.8)
    R[2][0] += V(0, -0.3, -0.9)
    R[3][0] += V(0, 0.1, -0.5)
    R[2][4] += V(0, 0.2, 0.2)
    F += loft(B, "stone", R, caps=("stone", "stone"))
    # ---- 背負う貝の扇
    F += fan(B, V(0, 4.9, 1.4), 54.0, 6.6)
    # ---- 目: 甲羅の前の縁から覗く短い柄
    for s in (1, -1):
        F += tube(B, "stone", [(s * 1.5, 4.7, -3.0), (s * 1.7, 5.5, -3.8), (s * 1.8, 6.1, -4.2)], ra=[0.9, 0.7, 0.6],
                  rb=[0.9, 0.7, 0.6], sides=4, hint=(1, 0, 0))
        F += ellipsoid(B, "lamp", V(s * 1.8, 6.5, -4.35), (0.8, 0.8, 0.8), segs=6, rings=1)
    # 眉の板 (目の上)
    F += spike(B, "pearl", (0.0, 5.0, -4.6), (0, 0.5, -1), 1.8, 0.7, sides=3, rings=1)
    # ---- フジツボの房 (横腹)
    for s in (1, -1):
        for p, d, h in (((s * 4.3, 4.6, -1.0), (s * 1, 0.6, -0.1), 1.6), ((s * 4.3, 4.2, 0.9), (s * 1, 0.5, 0.2), 1.3),
                        ((s * 3.9, 4.9, 2.3), (s * 0.8, 0.8, 0.3), 1.4), ((s * 3.4, 5.1, -2.0), (s * 0.7, 0.9, -0.2), 1.1)):
            F += barnacle(B, "barn", p, d, h, 0.75)
    # 口の牙
    for s in (1, -1):
        F += spike(B, "pearl", (s * 0.8, 3.4, -4.3), (s * 0.2, -0.5, -1), 1.4, 0.4, sides=3, rings=1)
    # ---- 脚: 短く太く、前へ向く (3 本ずつ)
    for s in (1, -1):
        b = f"leg_{side(s)}"
        for zc, fw in ((2.6, -0.3), (0.5, 0.5), (-1.5, 1.4)):
            pts = [(s * 4.0, 4.2, zc), (s * 6.6, 5.5, zc - fw * 0.3), (s * 8.4, 3.0, zc - fw * 0.9), (s * 9.2, 0.0, zc - fw * 1.5)]
            F += leg_chain(b, "stone", pts, [1.3, 1.1, 0.85, 0.0])
        F += barnacle(b, "barn", (s * 6.9, 5.7, 0.4), (0.3 * s, 1, 0), 1.0, 0.65)
        F += barnacle(b, "barn", (s * 7.0, 5.6, 2.6), (0.3 * s, 1, 0), 0.9, 0.55)
    F += sickle(1, True)
    F += sickle(-1, False)
    return F
