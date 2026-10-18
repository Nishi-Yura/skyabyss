"""B案「玄武の剣翅蛾」: スズメガ型。後ろへ流れる細長い剣のような翅 (玄武岩の黒い板を継いだ形) で、
板の割れ目に熔けた橙〜黄の筋が走って光る。太い玄武岩の胸と、橙の帯が光る節のある腹、
前へ突き出す 2 本の角、吊り上がった赤い目。矢じりの輪郭でいちばん敵らしい。"""
from em_parts import *  # noqa

HINT = 96
HEAD_CENTER, HEAD_SCALE = (0, 7.6, -6.5), 30.0
RAYS = [(34, 3.0), (18, 5.6), (4, 8.6), (-10, 11.6), (-24, 14.0), (-38, 15.4), (-50, 10.4), (-60, 6.4), (-72, 3.6)]
FR = (0.0, 0.38, 0.72, 1.0)
VEINS = [14, -6, -24, -42]
EYES = []

PROFILE = [(-5.0, 0.5, 0.55, 0.5, 7.4), (-4.2, 1.5, 1.6, 1.3, 7.5), (-2.8, 2.1, 2.1, 1.7, 7.6),
           (-1.2, 2.0, 2.0, 1.6, 7.5), (0.2, 1.6, 1.6, 1.3, 7.2), (1.6, 1.8, 1.7, 1.4, 7.0),
           (3.0, 1.4, 1.35, 1.15, 6.6), (4.4, 1.5, 1.4, 1.2, 6.3), (5.8, 1.0, 0.95, 0.85, 5.9),
           (7.0, 1.05, 1.0, 0.9, 5.6), (8.6, 0.15, 0.25, 0.25, 5.2)]
SEGS = ["body", "body", "body", "body", "seg", "body", "seg", "body", "seg", "body"]


def build():
    EYES.clear()
    F = body_loft(PROFILE, n=6, mats=SEGS)
    # 肩の板と背びれ
    for z, ln in ((-2.6, 2.0), (-1.0, 2.6), (0.6, 2.0)):
        F += spike("body", "plate", V(0, 9.2, z), V(0, 1, 0.8), ln, 0.8, sides=4, rings=1, rb=0.3)
    for s in (-1, 1):
        F += spike("body", "plate", V(s * 1.9, 8.6, -2.0), V(s * 1.0, 0.4, 0.8), 2.8, 0.9, sides=4, rings=1, rb=0.45)
    F += spike("body", "plate", V(0, 5.3, 8.4), V(0, -0.1, 1.0), 3.0, 0.6, sides=4, rings=1)
    F += ember_legs(zs=(-2.2, -0.2), drop=2.4, y0=4.8)
    # 頭: 角ばった玄武岩の頭と、吊り上がった赤い目
    F += ellipsoid("head", "body", HC, (1.65, 1.35, 1.7), segs=6, rings=2)
    for s in (-1, 1):
        d = sphere_dir(s * 46, 8)
        p, nn = ell_point(HC, (1.6, 1.35, 1.6), d)
        EYES.append(dict(c=p + nn * 0.1, n=nn, r=1.1))
        F += dome("head", "eye", p, nn, 1.3, 0.8, sides=5, mid=0.7, ry=0.6, up=(s * 0.75, 1, 0), phase=0)
    # 前へ突き出す 2 本の角
    for s in (-1, 1):
        a = V(s * 0.8, 8.4, -7.6)
        b = V(s * 1.4, 9.0, -10.4)
        c = V(s * 2.4, 9.0, -13.4)
        F += tube("head", "plate", [a, b, c], [0.7, 0.55, 0.0], [0.7, 0.55, 0.0], sides=4, hint=(0, 1, 0), phase=45,
                  axis_fn=lambda p, a=a: a)
    F += spike("head", "plate", V(0, 6.5, -8.2), V(0, -0.4, -1.0), 1.8, 0.5, sides=3, rings=1)
    # 翅
    for nm in ("wing_r", "wing_l"):
        F += wing2(nm, RAYS, fr=FR, th=(0.9, 0.35), rise=1.0, fold=0.4, a0=1.3, lead=0.5,
                   mats=("wtop", "wbot", "wrim"))
    return F


BAS = [(8, 10, 16), (18, 22, 30), (32, 38, 48), (52, 60, 72), (80, 90, 104)]
LAVA = [(130, 24, 10), (214, 62, 12), (252, 124, 20), (255, 188, 48), (255, 238, 140)]
PAL = {
    "mats": {
        "body": dict(ramp=BAS, base=0.55, litk=0.2),
        "plate": dict(ramp=BAS, base=0.62, litk=0.22),
        "seg": dict(ramp=LAVA, base=0.6, litk=0.12, minidx=1, edges=False),
        "wtop": dict(ramp=BAS, base=0.58, litk=0.16, edges=False),
        "wbot": dict(ramp=BAS, base=0.46, litk=0.12, edges=False),
        "wrim": dict(ramp=BAS, base=0.7, litk=0.2),
        "eye": dict(ramp=[(70, 12, 8), (130, 24, 10)], base=0.5, minidx=0),
        "leg": dict(ramp=BAS, base=0.45),
    },
}


def decor(ctx, col, glow):
    mats = ctx.mats
    col, glow = eye_paint(ctx, col, glow, EYES, (150, 30, 10), (255, 214, 96), glow_r=0.5)
    glow |= (mats == "seg")
    X, Z, r, phi = wcoords(ctx)
    L = interp_L(phi, RAYS)
    u = r / np.maximum(L, 1e-3)
    top = mats == "wtop"
    bot = mats == "wbot"
    rim = mats == "wrim"
    wing = top | bot
    lv = np.array(LAVA, float)
    bs = np.array(BAS, float)
    # 板の継ぎ目 (輪の線): 割れ目に熔岩
    wob = vnoise(ctx.P * 0.5 + 3, 4)
    ringline = wing & (np.abs(((u * 3.0 + 0.4 * (wob - 0.5)) % 1.0) - 0.5) > 0.465) & (r > 3.0)
    # 放射の割れ目
    cr = np.full(r.shape, 99.0)
    for v in VEINS:
        cr = np.minimum(cr, r * np.radians(np.abs(phi - v + 6 * (wob - 0.5))))
    crack = wing & (cr < 0.34) & (r > 2.6) & (u < 0.96)
    gl = (ringline | crack)
    ci = np.clip((1.2 + 2.0 * (1 - u) + ctx.bay).astype(int), 0, 4)
    col = np.where(gl[..., None], lv[ci], col)
    glow |= gl & top
    glow |= gl & bot & (ctx.bay > 0)
    # 付け根は暗い板
    root = wing & (r < 2.8)
    col = np.where(root[..., None], ctx.ramp_color(BAS, base=0.5, litk=0.2), col)
    glow &= ~root
    # 先端の熱 (縁の最後の帯は赤熱)
    tip = top & (u > 0.94)
    col = np.where(tip[..., None], lv[1], col)
    glow |= tip & (ctx.bay > 0)
    return col, glow
