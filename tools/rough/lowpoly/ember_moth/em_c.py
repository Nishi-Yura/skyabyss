"""C案「火の粉のはね飾り蛾」: トリバネガ型。翅が 4 本の細い羽の指に裂けていて、付け根は炭の黒、
先へ行くほど赤 → 橙 → 黄の火の色に光る (炎の舌)。指のあいだは空いている。指の先から火の粉が離れて漂う。
体は細長い炭の黒で、腹の節が熾の石炭のように光る。大きな丸い目と櫛状の触角。"""
from em_parts import *  # noqa

HINT = 96
HEAD_CENTER, HEAD_SCALE = (0, 7.6, -6.5), 30.0
# 指 4 本 (各 2 本の筋で先が寄る): (phi, L, phi_tip)
FING = [(58, 9.0), (28, 11.8), (-2, 12.6), (-34, 9.8)]
W = 9.0
RAYS = []
for k, (c, L) in enumerate(FING):
    RAYS += [(c + W, L, c + 1.0), (c - W, L, c - 1.0)]
# 筋の間 j: 0 = 指 0 の中、1 = 指 0 と 1 の間 (すき間)、2 = 指 1 の中 ...
FR = (0.0, 0.36, 0.7, 1.0)
GAPS = {1: 1, 3: 1, 5: 1}
EYES = []

PROFILE = [(-4.6, 0.5, 0.5, 0.45, 7.5), (-3.8, 1.2, 1.25, 1.1, 7.6), (-2.4, 1.6, 1.6, 1.35, 7.6),
           (-0.8, 1.5, 1.45, 1.25, 7.4), (0.8, 1.1, 1.05, 0.95, 7.1), (2.6, 1.15, 1.1, 1.0, 6.7),
           (4.4, 0.9, 0.85, 0.75, 6.3), (6.2, 0.95, 0.9, 0.8, 5.9), (8.0, 0.6, 0.55, 0.5, 5.5), (9.6, 0.1, 0.15, 0.15, 5.1)]
SEGS = ["body", "body", "body", "body", "coal", "body", "coal", "body", "coal"]


def build():
    EYES.clear()
    F = body_loft(PROFILE, n=6, mats=SEGS)
    F += ember_legs(zs=(-2.0, -0.2), drop=3.0, y0=4.9, r=0.24)
    F += ellipsoid("head", "body", HC, (1.5, 1.45, 1.55), segs=6, rings=2)
    ef, E = head_eyes(r=1.25, depth=1.0, theta=38, phi=6, sides=7, hr=(1.5, 1.45, 1.55))
    EYES.extend(E)
    F += ef
    # 櫛状の触角 (炭の軸に火の粉の羽枝)
    for s in (-1, 1):
        a = V(s * 0.6, 8.8, -7.4)
        b = V(s * 1.5, 10.6, -8.6)
        c = V(s * 2.6, 12.0, -9.2)
        F += tube("head", "body", [a, b, c], [0.4, 0.32, 0.0], sides=3, hint=(0, 0, 1), phase=90,
                  axis_fn=lambda p, a=a: a)
        for t in (0.3, 0.55, 0.8):
            p = a + (c - a) * t
            for o in (-1, 1):
                F += spike("head", "coal", p, V(o * 0.8 + s * 0.2, 0.2, -0.9), 1.3 - 0.4 * t, 0.24, sides=3, rings=1)
    F += spike("head", "body", V(0, 6.5, -8.0), V(0, -0.7, -1.0), 1.8, 0.35, sides=3, rings=2, bend=(0, -0.9, 0))
    # 翅: 4 本の指
    for nm in ("wing_r", "wing_l"):
        F += wing2(nm, RAYS, fr=FR, th=(0.7, 0.28), rise=0.9, fold=0.1, a0=1.3, skip_rows=GAPS, droop=0.4,
                   mats=("wtop", "wbot", "wrim"), hole_mat="wrim")
        # 指の先から離れて漂う火の粉 (小さな八面体)
        piv = PIV[nm]
        sd = SIDE[nm]
        for k, (c, L) in enumerate(FING):
            ph = math.radians(c)
            tip = V(piv[0] + sd * math.cos(ph) * (L + 1.6), piv[1] + 1.0 - 0.4, piv[2] - math.sin(ph) * (L + 1.6))
            F += spark_bit(nm, tip, 0.45 + 0.1 * (k % 2))
    return F


def spark_bit(bone, c, r):
    """小さな菱形の火の粉 (6 頂点・8 面)。"""
    ex = [c + V(r, 0, 0), c + V(-r, 0, 0)]
    ey = [c + V(0, r * 1.3, 0), c + V(0, -r * 1.3, 0)]
    ez = [c + V(0, 0, r), c + V(0, 0, -r)]
    F = []
    for a in ex:
        for b in ez:
            for t in ey:
                F += poly(bone, "spark", [a, b, t], c)
    return F


CHAR = [(6, 5, 7), (16, 12, 14), (30, 22, 24), (50, 36, 34)]
FIRE = [(70, 12, 8), (150, 28, 10), (220, 64, 12), (252, 124, 20), (255, 190, 50), (255, 238, 150)]
PAL = {
    "mats": {
        "body": dict(ramp=CHAR, base=0.55, litk=0.2),
        "coal": dict(ramp=FIRE[1:], base=0.6, litk=0.12, minidx=1, edges=False),
        "wtop": dict(ramp=CHAR, base=0.55, litk=0.14, edges=False),
        "wbot": dict(ramp=CHAR, base=0.45, litk=0.12, edges=False),
        "wrim": dict(ramp=FIRE[1:], base=0.6, litk=0.1, minidx=1, edges=False),
        "eye": dict(ramp=[(70, 12, 8), (130, 24, 10)], base=0.5, minidx=0),
        "spark": dict(ramp=FIRE[3:], base=0.8, minidx=1, edges=False),
        "leg": dict(ramp=CHAR, base=0.45),
    },
}


def decor(ctx, col, glow):
    mats = ctx.mats
    col, glow = eye_paint(ctx, col, glow, EYES, (150, 36, 10), (255, 168, 48), glow_r=0.45)
    glow |= (mats == "coal") | (mats == "spark") | (mats == "wrim")
    X, Z, r, phi = wcoords(ctx)
    top = mats == "wtop"
    bot = mats == "wbot"
    wing = top | bot
    fr_ = np.array(FIRE, float)
    # 指の軸への近さと、先端までの距離 (指ごと)
    best_u = np.zeros(r.shape)
    best_d = np.full(r.shape, 99.0)
    for c, L in FING:
        d = r * np.radians(np.abs(phi - c))
        take = d < best_d
        best_d = np.where(take, d, best_d)
        best_u = np.where(take, r / L, best_u)
    # 火の色: 根元 炭 → 先へ赤 → 橙 → 黄 (ディザで段をつなぐ)
    t = np.clip((best_u - 0.3) / 0.8, 0, 1.0)
    n1 = vnoise(ctx.P * 0.5 + 1, 6)
    idx = np.clip((t * 5.2 + 0.7 * ctx.bay + 0.5 * (n1 - 0.5)).astype(int), 0, 5)
    heat = fr_[idx]
    isfire = wing & (t > 0.05)
    col = np.where(isfire[..., None], heat, col)
    # 指の中心の筋は明るく、縁は一段暗い (炎の芯と外炎)
    core = wing & (best_d < 0.45) & (t > 0.55)
    col = np.where(core[..., None], fr_[np.clip(idx + 1, 0, 5)], col)
    glow |= isfire & (t > 0.3) & (ctx.bay < 0.3)
    glow |= core
    # 付け根は炭
    root = wing & (r < 3.2)
    col = np.where(root[..., None], ctx.ramp_color(CHAR, base=0.5, litk=0.2), col)
    glow &= ~root | (mats == "coal")
    return col, glow
