"""A案「綿毛の雷蛾」: ヤママユガ型の丸く大きい翅 (縁は波打ち、白い綿毛の縁取り)、青白い翅に銅の翅脈、
コバルトの目玉模様の中心だけ白く光る。ふさふさの胸と、羽枝のある銅の触角。翅の先に小さな放電のぎざぎざ。
灰青と白の毛・銅・稲妻の白青。"""
from sm_common import *  # noqa

HINT = 128
HEAD_CENTER, HEAD_SCALE = (0, 6.5, -4.5), 30.0
RAYS = [(80, 5.5), (66, 8.6), (52, 10.8), (38, 10.2), (24, 10.6), (10, 8.8), (-4, 7.6), (-20, 8.6), (-36, 9.4),
        (-52, 8.4), (-66, 7.0), (-80, 4.6)]
VEINS = [66, 38, 10, -20, -52]
SPOTS = [(7.0, 33), (6.2, -38)]
SPR = 0.78      # (r, phi) 目玉模様の中心
EYES = []

PROFILE = [(-6.3, 0.45, 0.45, 0.45, 6.2), (-5.6, 1.25, 1.35, 1.15, 6.3), (-4.2, 1.7, 1.7, 1.5, 6.3),
           (-3.2, 1.3, 1.25, 1.1, 6.2), (-2.0, 2.2, 2.1, 1.9, 6.2), (-0.4, 2.45, 2.3, 2.0, 6.3),
           (1.4, 2.0, 1.9, 1.7, 6.0), (3.6, 1.3, 1.25, 1.15, 5.5), (7.0, 0.2, 0.3, 0.3, 4.6)]


def build():
    EYES.clear()
    F = body_loft(PROFILE, n=8)
    ef, E = eyes(r=1.2, depth=0.9, theta=36, phi=5, sides=6)
    EYES.extend(E)
    F += ef
    # 口の巻き管
    F += spike("body", "ant", V(0, 5.2, -6.2), V(0, -0.4, -1.0), 2.4, 0.38, sides=3, rings=2, bend=(0, -0.9, 0))
    # 胸のふさふさ (先の細い毛束)
    c = V(0, 6.2, -1.2)
    rr = (2.5, 2.3, 2.3)
    k = 0
    for th, ph, ln in ((0, 62, 2.6), (45, 38, 2.4), (-45, 38, 2.4), (90, 14, 2.2), (-90, 14, 2.2), (150, 40, 2.2),
                       (-150, 40, 2.2), (180, 62, 2.4)):
        p, nn = ell_point(c, rr, sphere_dir(th, ph))
        F += spike("body", "fur", p - nn * 0.5, nn + V(0, 0.2, 0.55), ln, 0.7, sides=3, rings=1)
    # 首の襟
    for th in range(-120, 121, 60):
        d = sphere_dir(th, 6)
        p, nn = ell_point(V(0, 6.2, -3.3), (1.4, 1.3, 1.1), d)
        F += spike("body", "fur", p, nn + V(0, 0, -0.9), 1.8, 0.55, sides=3, rings=1)
    # 腹の毛 (後ろへ)
    for z, s in ((2.8, 1), (2.8, -1)):
        F += spike("body", "fur", V(s * 1.0, 5.5, z), V(s * 0.8, -0.1, 0.8), 1.8, 0.5, sides=3, rings=1)
    F += legs(zs=(-2.2, -0.2), drop=2.8)
    # 触角: 銅の軸に羽枝
    for s in (-1, 1):
        base = V(s * 0.55, 8.0, -5.4)
        mid = V(s * 1.6, 9.6, -6.6)
        tip = V(s * 3.2, 11.3, -7.8)
        F += tube("antennae", "ant", [base, mid, tip], [0.42, 0.32, 0.0], sides=3, hint=(0, 0, 1), phase=90,
                  axis_fn=lambda p, b=base: b)
        for t in (0.25, 0.5, 0.75):
            p = base + (tip - base) * t + (mid - (base + tip) / 2) * 2 * t * (1 - t) * 1.0
            for o in (-1, 1):
                F += spike("antennae", "barb", p, V(o * 0.9 + s * 0.2, 0.5, -0.6), 1.5 - 0.5 * t, 0.26, sides=3, rings=1)
        F += dome("antennae", "orb", tip, nrm(tip - mid), 0.55, 0.55, sides=4, mid=0.8, phase=45)
    # 翅
    for nm in ("wing_r", "wing_l"):
        F += wing(nm, RAYS, fr=(0.0, 0.55, 1.0), th=(0.7, 0.3), rise=0.8, fold=0.3, mats=("wtop", "wbot", "wrim"))
        ep = wing_edge_points(nm, RAYS)
        s = SIDE[nm]
        # 縁の綿毛 (先の細い毛)
        for j in (2, 9):
            p = ep[j]
            d = nrm(p - PIV[nm])
            F += spike(nm, "fur", p - d * 0.6, d + V(0, 0.15, 0), 2.0, 0.6, sides=3, rings=1)
        # 放電のぎざぎざ (先端の二箇所)
        for j, sd in ((3, 1),):
            p = ep[j]
            d = nrm(p - PIV[nm])
            F += bolt(nm, "bolt", p + d * 0.9 + V(0, 0.5, 0), d + V(0, 0.2, 0), segs=3, seg=1.5, jit=0.9, r=0.26,
                      seed=j + (5 if s < 0 else 0))
    return F


COPPER = [(70, 34, 20), (128, 66, 32), (186, 104, 48), (232, 152, 82)]
FUR = [(36, 46, 70), (62, 78, 108), (100, 120, 152), (150, 170, 198), (196, 212, 232)]
WING = [(66, 86, 122), (112, 142, 178), (164, 192, 220), (214, 232, 246), (244, 250, 255)]
PAL = {
    "mats": {
        "body": dict(ramp=FUR, base=0.60, litk=0.18),
        "fur": dict(ramp=FUR, base=0.78, litk=0.16),
        "wtop": dict(ramp=WING, base=0.66, litk=0.14, edges=False),
        "wbot": dict(ramp=WING, base=0.60, litk=0.12, edges=False),
        "wrim": dict(ramp=FUR, base=0.72, litk=0.1),
        "eye": dict(ramp=[(16, 22, 40), (26, 40, 70), (50, 80, 120)], base=0.6, minidx=0),
        "ant": dict(ramp=COPPER, base=0.62, litk=0.2),
        "barb": dict(ramp=COPPER, base=0.8, litk=0.15),
        "orb": dict(ramp=[(150, 210, 255), (210, 240, 255), (255, 255, 255)], base=0.9, minidx=1, edges=False),
        "leg": dict(ramp=FUR, base=0.4),
        "bolt": dict(ramp=[(190, 230, 255), (255, 255, 255)], base=0.9, edges=False),
    },
}


def decor(ctx, col, glow):
    mats = ctx.mats
    col, glow = eye_paint(ctx, col, glow, EYES, (30, 52, 96), (210, 244, 255), glow_r=0.55)
    glow |= (mats == "bolt") | (mats == "orb")
    X, Z, r, phi, u = wing_coords(ctx, RAYS)
    top = mats == "wtop"
    bot = mats == "wbot"
    rim = mats == "wrim"
    cu = np.array(COPPER, float)
    # 翅脈 (銅): 筋に沿って 1 テクセル
    vd = arc_dist(r, phi, VEINS)
    vein = (vd < 0.36) & (r > 2.6) & (top | bot)
    col = np.where(vein[..., None], cu[np.clip((1.5 + 1.6 * ctx.lit + ctx.bay).astype(int), 0, 3)], col)
    # 外縁の帯 (縁の近くは濃い青灰と白の点)
    band = (u > 0.84) & (top | bot)
    col = np.where((band & (ctx.bay > -0.1))[..., None], np.array(FUR[1], float), col)
    fleck = (u > 0.7) & (u < 0.9) & top & (hash3(np.floor(ctx.P * 0.9), 5) > 0.86)
    col = np.where(fleck[..., None], np.array(WING[4], float), col)
    # 付け根は灰青の毛
    root = (r < 3.3) & (top | bot)
    col = np.where(root[..., None], ctx.ramp_color(FUR, base=0.66, litk=0.15), col)
    # 目玉模様 (上面): 銅の縁 > コバルト > 白の中心 (光る)
    for rr, ph in SPOTS:
        dx = X - rr * math.cos(math.radians(ph))
        dz = Z - (-rr * math.sin(math.radians(ph)))
        dd = np.hypot(dx, dz)
        outer = (dd < 2.5 * SPR) & top
        col = np.where(outer[..., None], cu[1], col)
        mid = (dd < 2.0 * SPR) & top
        col = np.where(mid[..., None], np.array((26, 44, 104), float), col)
        mid2 = (dd < 1.5 * SPR) & top & (ctx.bay < 0.2)
        col = np.where(mid2[..., None], np.array((60, 100, 176), float), col)
        core = (dd < 0.95 * SPR) & top
        col = np.where(core[..., None], np.array((246, 252, 255), float), col)
        glow |= core
        # 下面は淡い写し
        shadow = (dd < 1.8 * SPR) & bot
        col = np.where(shadow[..., None], np.array(WING[1], float), col)
    return col, glow
