"""C案「銅コイルの燕尾蛾」: オナガミズアオ型の長い尾を持つ翅。翅は銅の枠と淡い水色のガラス板を組んだ窓 (枠の交点が
小さなコンデンサのように青く光る)。腹は銅線を巻いたコイルの輪で、輪のすき間が光る。触角は細い鞭で先に光の玉、
目は大きな複眼。緑青 (青緑のさび) と銅と水色。"""
from sm_common import *  # noqa

HINT = 128
HEAD_CENTER, HEAD_SCALE = (0, 6.4, -4.6), 30.0
RAYS = [(76, 5.5), (58, 9.0), (40, 10.8), (22, 10.4), (4, 8.8), (-14, 7.8), (-32, 9.0), (-50, 8.4), (-66, 13.8),
        (-80, 4.5)]
FR = (0.0, 0.4, 0.72, 1.0)
EYES = []

PROFILE = [(-6.1, 0.45, 0.5, 0.45, 6.2), (-5.4, 1.3, 1.4, 1.2, 6.3), (-4.0, 1.7, 1.7, 1.5, 6.3),
           (-3.0, 1.3, 1.25, 1.1, 6.2), (-1.8, 2.1, 2.0, 1.8, 6.2), (-0.2, 2.2, 2.1, 1.8, 6.1),
           (1.2, 1.5, 1.5, 1.4, 5.8), (2.3, 1.95, 1.9, 1.8, 5.6), (3.4, 1.15, 1.15, 1.1, 5.3), (4.5, 1.6, 1.55, 1.45, 5.1),
           (5.6, 0.9, 0.9, 0.85, 4.9), (6.7, 1.15, 1.1, 1.0, 4.7), (7.6, 0.2, 0.3, 0.3, 4.5)]
GAP_MATS = ["body"] * 5 + ["gap", "coil", "gap", "coil", "gap", "coil", "gap"]


def build():
    EYES.clear()
    F = body_loft(PROFILE, n=8, mats=GAP_MATS)
    ef, E = eyes(r=1.45, depth=0.95, theta=34, phi=4, sides=6)
    EYES.extend(E)
    F += ef
    # 胸の肩当て (銅の板)
    for s in (-1, 1):
        F += spike("body", "coil", V(s * 2.0, 6.6, -2.2), V(s * 0.9, 0.3, 0.7), 2.6, 0.8, sides=3, rings=1)
    # 背のこぶ 2 つ
    for z in (-1.6, 0.0):
        F += spike("body", "coil", V(0, 8.0, z), V(0, 1, 0.3), 1.6, 0.7, sides=4, rings=1, rb=0.5)
    F += legs(zs=(-2.2, -0.4), drop=2.8, y0=3.9)
    # 触角: 細い鞭 (緩く曲がる) と先の光の玉
    for s in (-1, 1):
        a = V(s * 0.5, 8.0, -5.2)
        b = V(s * 1.9, 10.6, -7.0)
        c = V(s * 4.2, 12.2, -7.4)
        F += tube("antennae", "whip", [a, b, c], [0.4, 0.3, 0.2], sides=3, hint=(0, 0, 1), phase=90,
                  axis_fn=lambda p, a=a: a)
        F += dome("antennae", "orb", c + V(0, 0.1, 0), nrm(c - b), 0.6, 0.6, sides=6, mid=0.85, phase=0, base_in=0.5)
    # 翅
    for nm in ("wing_r", "wing_l"):
        F += wing(nm, RAYS, fr=FR, th=(0.7, 0.3), rise=0.9, fold=0.12, mats=("wtop", "wbot", "wrim"))
        ep = wing_edge_points(nm, RAYS)
        # 尾の先の小さな雷
        s = SIDE[nm]
        p = ep[8]
        F += bolt(nm, "orb", p + V(0, 0.3, 0.6), V(0, 0.1, 1), segs=2, seg=1.3, jit=0.7, r=0.26, seed=3)
    return F


COPPER = [(70, 34, 22), (126, 66, 34), (184, 106, 52), (232, 156, 88), (250, 204, 140)]
VERD = [(24, 70, 70), (44, 110, 102), (80, 160, 140), (128, 200, 170)]
GLASS = [(96, 150, 176), (140, 196, 216), (186, 226, 238), (226, 246, 252)]
PAL = {
    "mats": {
        "body": dict(ramp=COPPER, base=0.58, litk=0.2),
        "coil": dict(ramp=COPPER, base=0.62, litk=0.24),
        "gap": dict(ramp=[(40, 100, 170), (80, 150, 220)], base=0.6, minidx=0, edges=False),
        "wtop": dict(ramp=GLASS, base=0.62, litk=0.12, edges=False),
        "wbot": dict(ramp=GLASS, base=0.56, litk=0.12, edges=False),
        "wrim": dict(ramp=COPPER, base=0.62, litk=0.18),
        "eye": dict(ramp=[(16, 40, 56), (30, 70, 90)], base=0.5, minidx=0),
        "whip": dict(ramp=COPPER, base=0.62, litk=0.2),
        "orb": dict(ramp=[(160, 224, 255), (220, 246, 255), (255, 255, 255)], base=0.9, minidx=1, edges=False),
        "leg": dict(ramp=VERD, base=0.4),
    },
}


def decor(ctx, col, glow):
    mats = ctx.mats
    col, glow = eye_paint(ctx, col, glow, EYES, (20, 60, 76), (200, 246, 255), glow_r=0.6)
    glow |= (mats == "orb") | (mats == "gap")
    X, Z, r, phi, u = wing_coords(ctx, RAYS)
    top = mats == "wtop"
    bot = mats == "wbot"
    rim = mats == "wrim"
    cu = np.array(COPPER, float)
    vg = np.array(VERD, float)
    phis = [p for p, _ in RAYS]
    # 板の中の淡い濃淡 (ガラス)
    cl = vnoise(ctx.P * 0.12 + 2, 6)
    pane = (top | bot) & (cl > 0.55)
    col = np.where(pane[..., None], np.array(GLASS[2], float), col)
    sheen = (top | bot) & (hash3(np.floor(ctx.P * 0.8), 8) > 0.985)
    col = np.where(sheen[..., None], np.array(GLASS[3], float), col)
    # 銅の枠: 筋 + 輪 + 縁
    ray = arc_dist(r, phi, phis) < 0.33
    ringl = np.zeros(r.shape, bool)
    Lint = r / np.maximum(u, 1e-3)
    for f in FR[1:-1]:
        ringl |= np.abs(r - f * Lint) < 0.3
    edge = u > 0.93
    frame = (ray | ringl | edge) & (r > 2.5) & (top | bot)
    fc = cu[np.clip((1.7 + 1.5 * ctx.lit + ctx.bay).astype(int), 0, 4)]
    col = np.where(frame[..., None], fc, col)
    # 緑青: 縁と枠の一部
    pat = frame & (vnoise(ctx.P * 0.2 + 7, 3) > 0.62)
    col = np.where(pat[..., None], vg[np.clip((1.3 + ctx.lit + ctx.bay).astype(int), 0, 3)], col)
    # 交点の光る節 (コンデンサ)
    node = np.zeros(r.shape, bool)
    for f in FR[1:-1]:
        node |= (np.abs(r - f * Lint) < 0.6) & (arc_dist(r, phi, phis) < 0.6)
    node &= (r > 2.5) & (top | bot) & ~frame
    node &= hash3(np.floor(ctx.P * 2.0 / 2.0), 1) > -1
    col = np.where(node[..., None], np.array((126, 206, 255), float), col)
    core = node & (np.abs(r - np.round(r / 1.0) * 1.0) >= 0) & (arc_dist(r, phi, phis) < 0.5) & (ctx.bay > -0.3)
    col = np.where(core[..., None], np.array((236, 252, 255), float), col)
    glow |= node & top
    # 付け根は緑青の金具
    root = (r < 3.0) & (top | bot)
    col = np.where(root[..., None], ctx.ramp_color(VERD, base=0.55, litk=0.2), col)
    glow &= ~root
    return col, glow
