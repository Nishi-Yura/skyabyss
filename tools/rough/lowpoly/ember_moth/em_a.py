"""A案「燃え殻の灰蛾」: 燃え残りの紙のような、ぼろぼろに焦げた幅広の三角の翅。縁はぎざぎざに焼け落ち、
縁の筋が橙に燃え (光る)、焼け抜けた穴のふちも熾の橙。灰色の紙・炭の黒・熾の橙。体は炭の黒に灰をかぶった細身、
焦げた小枝のような曲がった触角 (先が赤く光る)。"""
from em_parts import *  # noqa

HINT = 96
HEAD_CENTER, HEAD_SCALE = (0, 7.6, -6.5), 30.0
RAYS = [(82, 4.6), (68, 7.4), (54, 6.0), (40, 9.8), (26, 8.0), (12, 10.8), (-2, 8.8), (-16, 10.6), (-30, 8.2),
        (-44, 9.6), (-58, 7.0), (-70, 8.4), (-82, 4.8)]
FR = (0.0, 0.42, 0.74, 1.0)
HOLES = {(1, 3), (1, 8), (2, 5), (2, 10)}
EYES = []

PROFILE = [(-4.6, 0.5, 0.55, 0.5, 7.5), (-3.9, 1.3, 1.4, 1.2, 7.6), (-2.6, 1.85, 1.8, 1.55, 7.6),
           (-1.2, 1.75, 1.7, 1.5, 7.5), (0.4, 1.35, 1.3, 1.15, 7.2), (2.4, 1.2, 1.15, 1.0, 6.8),
           (4.4, 0.95, 0.9, 0.8, 6.4), (6.4, 0.6, 0.6, 0.55, 6.0), (8.4, 0.12, 0.2, 0.2, 5.6)]


def build():
    EYES.clear()
    F = body_loft(PROFILE, n=6, mats=["body", "body", "back", "back", "band", "body", "band", "body"])
    # 頭: 灰かぶりの丸い頭 + 大きな橙の目
    F += ellipsoid("head", "head", HC, (1.6, 1.5, 1.6), segs=6, rings=2)
    ef, E = head_eyes(r=1.05, depth=0.85, theta=44, phi=10, sides=6)
    EYES.extend(E)
    F += ef
    # 口: 短い焦げた牙
    F += spike("head", "char", V(0, 6.6, -8.1), V(0, -0.6, -1.0), 1.7, 0.42, sides=3, rings=1)
    # 背の灰の瘤
    F += spike("body", "back", V(0, 9.1, -2.2), V(0, 1, 0.5), 1.8, 0.8, sides=4, rings=1, rb=0.5)
    F += spike("body", "back", V(0, 8.8, -0.2), V(0, 1, 0.7), 1.4, 0.7, sides=4, rings=1, rb=0.4)
    F += ember_legs(zs=(-2.4, -0.4), drop=2.6)
    # 触角: 焦げた小枝 (曲がって二股、先が赤く光る)
    for s in (-1, 1):
        a = V(s * 0.7, 8.7, -7.0)
        b = V(s * 1.8, 10.6, -8.0)
        c = V(s * 3.4, 12.2, -8.4)
        F += tube("head", "char", [a, b, c], [0.45, 0.36, 0.0], sides=3, hint=(0, 0, 1), phase=90,
                  axis_fn=lambda p, a=a: a)
        F += spike("head", "char", b, V(s * 0.2, 1.0, -0.5), 2.0, 0.3, sides=3, rings=1)
        F += dome("head", "tip", c, nrm(c - b), 0.5, 0.55, sides=4, mid=0.8, phase=45)
    # 翅
    for nm in ("wing_r", "wing_l"):
        F += wing2(nm, RAYS, fr=FR, th=(0.7, 0.3), rise=0.7, fold=0.3, a0=1.2, skip=HOLES, droop=0.6,
                   mats=("wtop", "wbot", "wrim"), hole_mat="whole")
    return F


ASH = [(30, 28, 32), (58, 54, 56), (92, 88, 88), (130, 124, 120), (170, 164, 156)]
CHAR = [(8, 6, 8), (20, 16, 16), (36, 28, 26), (58, 44, 38)]
EMB = [(120, 26, 10), (196, 62, 14), (244, 116, 22), (255, 178, 44), (255, 230, 130)]
PAL = {
    "mats": {
        "body": dict(ramp=CHAR, base=0.55, litk=0.2),
        "back": dict(ramp=ASH, base=0.55, litk=0.2),
        "band": dict(ramp=EMB, base=0.7, litk=0.15, minidx=1, edges=False),
        "head": dict(ramp=ASH, base=0.5, litk=0.2),
        "char": dict(ramp=CHAR, base=0.65, litk=0.2),
        "wtop": dict(ramp=ASH, base=0.6, litk=0.14, edges=False),
        "wbot": dict(ramp=ASH, base=0.5, litk=0.12, edges=False),
        "wrim": dict(ramp=CHAR, base=0.5, litk=0.15),
        "whole": dict(ramp=EMB, base=0.62, litk=0.12, minidx=1, edges=False),
        "eye": dict(ramp=[(60, 14, 8), (110, 28, 10)], base=0.5, minidx=0),
        "tip": dict(ramp=EMB, base=0.8, minidx=2, edges=False),
        "leg": dict(ramp=CHAR, base=0.5),
    },
}


def decor(ctx, col, glow):
    mats = ctx.mats
    col, glow = eye_paint(ctx, col, glow, EYES, (150, 40, 10), (255, 180, 60), glow_r=0.5)
    glow |= (mats == "tip") | (mats == "whole") | (mats == "band")
    X, Z, r, phi = wcoords(ctx)
    L = interp_L(phi, RAYS)
    u = r / np.maximum(L, 1e-3)
    top = mats == "wtop"
    bot = mats == "wbot"
    rim = mats == "wrim"
    wing = top | bot
    ash = np.array(ASH, float)
    ch = np.array(CHAR, float)
    em = np.array(EMB, float)
    # 紙の繊維のむら
    n1 = vnoise(ctx.P * 0.35 + 2, 3)
    mott = wing & (n1 > 0.62 + 0.02 * ctx.bay)
    col = np.where(mott[..., None], ash[1], col)
    # 焼け焦げた縁: 外 → 炭の黒 → 橙 (光る) の帯
    jag = vnoise(ctx.P * 0.9 + 7, 5)
    char_band = wing & (u > 0.8 + 0.08 * (jag - 0.5)) & (u <= 1.2)
    col = np.where(char_band[..., None], ch[np.clip((1 + ctx.bay * 2 + 0.5 * ctx.lit).astype(int), 0, 2)], col)
    burn = wing & (u > 0.92 + 0.04 * (jag - 0.5)) & (u < 1.3)
    ci = np.clip((1.0 + 1.6 * (u - 0.9) * 10 + ctx.bay).astype(int), 0, 3)
    col = np.where(burn[..., None], em[ci], col)
    glow |= burn & (ctx.bay < 0.35)
    # 焼け穴のふち: 穴の中心の周りを炭 → 熾の橙に
    for (hi, hj) in HOLES:
        rm = (FR[hi] + FR[hi + 1]) / 2 * (RAYS[hj][1] + RAYS[hj + 1][1]) / 2 + 1.2 * (1 - (FR[hi] + FR[hi + 1]) / 2)
        pm = math.radians((RAYS[hj][0] + RAYS[hj + 1][0]) / 2)
        dd = np.hypot(X - rm * math.cos(pm), Z + rm * math.sin(pm))
        sc = wing & (dd < 2.6)
        col = np.where(sc[..., None], ch[np.clip((1 + ctx.bay * 2).astype(int), 0, 2)], col)
        sb = wing & (dd < 2.05)
        col = np.where(sb[..., None], em[np.clip((1.2 + 1.6 * (2.05 - dd) + ctx.bay).astype(int), 0, 3)], col)
        glow |= sb & (ctx.bay < 0.3)
    # 点々と散る熾 (翅の上の小さな火の粉)
    sp = top & (hash3(np.floor(ctx.P * 0.8), 9) > 0.96) & (u > 0.35) & (u < 0.85)
    col = np.where(sp[..., None], em[2], col)
    glow |= sp
    # 付け根は炭と灰
    root = wing & (r < 3.0)
    col = np.where(root[..., None], ctx.ramp_color(CHAR, base=0.6, litk=0.2), col)
    glow &= ~root | (mats == "whole")
    # 縁の板は炭
    return col, glow
