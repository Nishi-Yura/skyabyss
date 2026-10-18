"""B案「稲妻翅」: 嵐雲色 (暗い藍の石盤) のぎざぎざ翅。縁は稲妻の折れ線で、後ろの翅は長い雷の尾。
翅脈はそのまま白金に光る稲妻の筋。胸は銅の肩当てと背びれ、腹は銅の帯、前へ突き出す 2 本の避雷針の角、
吊り上がった細い目。いちばん敵らしく攻撃的。"""
from sm_common import *  # noqa

HINT = 128
HEAD_CENTER, HEAD_SCALE = (0, 6.5, -4.8), 30.0
RAYS = [(78, 5.5), (56, 11.8), (36, 7.0), (16, 12.8), (-6, 6.5), (-26, 10.4), (-46, 6.0), (-68, 13.2), (-84, 4.0)]
VEINS = [56, 16, -26, -68]
EYES = []

PROFILE = [(-6.0, 0.4, 0.45, 0.4, 6.3), (-5.2, 1.1, 1.2, 1.0, 6.4), (-3.8, 1.55, 1.5, 1.3, 6.3),
           (-2.9, 1.15, 1.1, 1.0, 6.2), (-1.8, 1.9, 1.9, 1.6, 6.3), (-0.2, 2.1, 2.0, 1.7, 6.2),
           (1.4, 1.75, 1.7, 1.5, 5.9), (3.2, 1.35, 1.35, 1.2, 5.5), (5.0, 0.95, 0.95, 0.9, 5.1), (7.0, 0.2, 0.3, 0.3, 4.7)]
GAP_MATS = ["body", "body", "body", "body", "plate", "plate", "body", "plate", "plate"]


def build():
    EYES.clear()
    F = body_loft(PROFILE, n=6, mats=GAP_MATS)
    # 目: 吊り上がった細い丸屋根
    for s in (-1, 1):
        d = sphere_dir(s * 44, 8)
        p, nn = ell_point(HC, (1.6, 1.5, 1.6), d)
        EYES.append(dict(c=p + nn * 0.1, n=nn, r=1.2))
        F += dome("body", "eye", p, nn, 1.35, 0.8, sides=5, mid=0.7, ry=0.62, up=(s * 0.75, 1, 0), phase=0)
    # 背びれと肩当て
    for z, ln in ((-2.4, 2.2), (-0.8, 2.8), (0.8, 2.2), (2.2, 1.5)):
        F += spike("body", "plate", V(0, 7.9 - 0.4 * (z > 0.5) - 0.5 * (z > 2), z), V(0, 1, 0.9), ln, 0.75, sides=4,
                   rings=1, rb=0.3)
    for s in (-1, 1):
        F += spike("body", "plate", V(s * 1.9, 7.0, -1.8), V(s * 1.0, 0.5, 0.9), 3.2, 0.9, sides=4, rings=1, rb=0.45)
        F += spike("body", "plate", V(s * 1.7, 6.0, 1.0), V(s * 0.8, 0.0, 1.0), 2.4, 0.7, sides=4, rings=1, rb=0.4)
    # 尾の避雷針 2 本
    for s in (-1, 1):
        F += spike("body", "tip", V(s * 0.25, 4.8, 6.4), V(s * 0.28, -0.1, 1.0), 3.6, 0.45, sides=3, rings=1)
    F += legs(zs=(-1.8, 0.2), drop=2.8, y0=3.9)
    # 避雷針の角 (触角): 前へ突き出して先で外へ
    for s in (-1, 1):
        a = V(s * 0.5, 8.2, -5.2)
        b = V(s * 1.5, 9.6, -8.4)
        c = V(s * 3.3, 10.0, -12.2)
        F += tube("antennae", "horn", [a, b, c], [0.55, 0.45, 0.0], [0.55, 0.45, 0.0], sides=4, hint=(0, 1, 0),
                  phase=45, axis_fn=lambda p, a=a: a)
        F += spike("antennae", "tip", b + V(0, 0.2, 0), V(s * 0.7, 0.5, -0.2), 1.9, 0.35, sides=3, rings=1)
    # 翅
    for nm in ("wing_r", "wing_l"):
        F += wing(nm, RAYS, fr=(0.0, 0.42, 0.75, 1.0), th=(0.8, 0.28), rise=1.5, fold=0.45, lead=0.8,
                  mats=("wtop", "wbot", "wrim"))
    return F


SLATE = [(8, 12, 26), (18, 24, 46), (32, 44, 76), (52, 70, 110), (82, 106, 150)]
COPPER = [(64, 30, 16), (118, 58, 28), (178, 98, 44), (230, 150, 80)]
PAL = {
    "mats": {
        "body": dict(ramp=SLATE, base=0.58, litk=0.18),
        "plate": dict(ramp=COPPER, base=0.6, litk=0.22),
        "wtop": dict(ramp=SLATE, base=0.55, litk=0.16, edges=False),
        "wbot": dict(ramp=SLATE, base=0.48, litk=0.14, edges=False),
        "wrim": dict(ramp=COPPER, base=0.6, litk=0.18),
        "eye": dict(ramp=[(10, 14, 28), (20, 30, 56)], base=0.5, minidx=0),
        "horn": dict(ramp=COPPER, base=0.62, litk=0.22),
        "tip": dict(ramp=[(200, 232, 255), (255, 250, 220), (255, 255, 255)], base=0.8, minidx=1, edges=False),
        "leg": dict(ramp=SLATE, base=0.45),
    },
}


def decor(ctx, col, glow):
    mats = ctx.mats
    col, glow = eye_paint(ctx, col, glow, EYES, (14, 24, 52), (236, 246, 255), glow_r=0.5)
    glow |= (mats == "tip")
    X, Z, r, phi, u = wing_coords(ctx, RAYS)
    top = mats == "wtop"
    bot = mats == "wbot"
    rim = mats == "wrim"
    wcol = np.array(SLATE, float)
    cu = np.array(COPPER, float)
    # 暗い雲のむら
    cl = vnoise(ctx.P * 0.22 + 4, 2)
    dk = (top | bot) & (cl > 0.6 + 0.02 * ctx.bay)
    col = np.where(dk[..., None], wcol[1], col)
    # 稲妻の筋 (光る): 付け根から先へ。途中で小さく枝分かれ
    vd = arc_dist(r, phi, VEINS)
    bolt_main = (vd < 0.27) & (r > 2.4) & (u < 0.97) & (top | bot)
    branch = (arc_dist(r, phi, [v + 9 * (1 if k % 2 else -1) for k, v in enumerate(VEINS)]) < 0.22) & (u > 0.38) & \
             (u < 0.78) & (r > 2.4) & (top | bot) & ((np.floor(r * 0.5).astype(int)) % 3 == 0)
    cross = (np.abs(((r * 0.5) % 2.2) - 1.1) < 0.18) & (u > 0.3) & (u < 0.9) & top & (hash3(np.floor(ctx.P * 0.7), 3) > 0.35)
    gl = (bolt_main | branch) & (top | bot)
    hot = gl & (vd < 0.2)
    gold = np.array([(120, 170, 255), (200, 228, 255), (255, 244, 190)], float)
    ci = np.clip((0.9 + 1.4 * (1 - u) + ctx.bay).astype(int), 0, 2)
    col = np.where(gl[..., None], gold[ci], col)
    col = np.where(hot[..., None], gold[2], col)
    glow |= gl
    # 翅の先の稲妻の点 (先端の帯は白金に光る)
    tipg = (u > 0.93) & top & (ctx.bay > 0.0)
    col = np.where(tipg[..., None], gold[2], col)
    glow |= tipg
    # 付け根は銅の板
    root = (r < 3.0) & (top | bot)
    col = np.where(root[..., None], ctx.ramp_color(SLATE, base=0.5, litk=0.2), col)
    glow &= ~root
    return col, glow
