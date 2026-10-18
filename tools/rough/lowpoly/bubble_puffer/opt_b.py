"""B案「棘玉の鎧」: ウニと潮殻を混ぜた、角ばった甲冑のフグ。八角の殻に二本の帯 (骨色のふち) が巻き、
帯から放射状に長い棘が出る星形の輪郭。目は怒って吊り上がった細い目と重いまゆ板、横に広い口に太い牙 2 本。
胸びれは骨ばった小さな刃、尾は短い三本の棘。濃い藍の殻・骨色の帯・橙の縞の棘。"""
from pf_common import *  # noqa

CY0 = 6.6
BODY_R = (5.8, 5.2, 6.2)
EYES = []


def build():
    EYES.clear()
    F = []
    n = 8
    spec = [(-6.0, 0.36, 1.0, 0), (-4.7, 0.72, 1.0, 0), (-3.9, 0.88, 1.0, 0), (-3.0, 0.95, 1.2, 1), (-2.1, 0.96, 1.0, 0),
            (-0.8, 1.0, 1.0, 0), (0.7, 1.0, 1.0, 0), (1.6, 0.97, 1.0, 0), (2.5, 0.93, 1.2, 1), (3.4, 0.86, 1.0, 0),
            (4.7, 0.66, 1.0, 0), (6.0, 0.36, 1.0, 0)]
    rings = []
    mats = []
    for i, (z, f, k, peak) in enumerate(spec):
        cy = 6.6 if z > -4 else 5.8 + (z + 6.0) * 0.4
        rings.append(zring(z, 5.8 * f * k, 5.2 * f * k, 4.8 * f * k, cy, n=n, phase=22.5))
    for i in range(len(spec) - 1):
        mats.append("belt" if (spec[i][3] or spec[i + 1][3]) else "shell")
    F += loft("body", "shell", rings, caps=("shell", "shell"), mats=mats)
    # ---- 口: 横に広い口と暗い穴、太い牙 2 本、下あごの板
    mc = V(0, 4.9, -6.15)
    pts = []
    for k in range(7):
        a = math.radians(360 * k / 6)
        pts.append(mc + V(2.7 * math.cos(a), 0.95 * math.sin(a), -0.1 * math.cos(2 * a)))
    F += tube("body", "lip", pts, ra=0.55, rb=0.55, sides=3, hint=(0, 0, 1), phase=0)
    hole = [mc + V(2.2 * math.cos(math.radians(360 * k / 6)), 0.55 * math.sin(math.radians(360 * k / 6)), -0.15) for k in range(6)]
    F += poly("body", "dark", hole, mc + V(0, 0, 3))
    for x in (-1.15, 1.15):
        F += spike("body", "fang", V(x, 5.15, -6.35), V(x * 0.05, -1.0, -0.15), 2.9, 0.62, sides=4, rings=1, hint=(1, 0, 0))
    F += spike("body", "fang", V(0, 3.9, -6.0), V(0, -0.55, -1.0), 1.6, 0.6, sides=3, rings=1)
    jaw = [V(-2.4, 4.3, -5.9), V(2.4, 4.3, -5.9), V(1.5, 3.0, -6.0), V(-1.5, 3.0, -6.0)]
    F += poly("body", "belt", jaw, V(0, 5, 0))
    # ---- 目: 吊り上がった細い目と重いまゆ板
    c0 = V(0, CY0, 0)
    for s in (-1, 1):
        d = sphere_dir(s * 58, 12)
        p, nn = ell_point(c0, BODY_R, d)
        up = (s * -0.45, 1.0, 0)
        R = frame_from_normal(nn, up)
        EYES.append(dict(c=p + nn * 0.1, n=nn, r=2.0, s=s, R=R))
        F += dome("body", "eye", p, nn, 2.5, 1.2, sides=6, mid=0.7, ry=1.3, up=up, phase=0.0)
        arc = []
        for a, rr, lift in ((176, 2.7, 0.2), (130, 2.9, 0.9), (84, 2.9, 1.5), (30, 2.6, 1.2), (-6, 2.2, 0.6)):
            aa = math.radians(a)
            arc.append(p + R @ V(rr * math.cos(aa), 1.0 * math.sin(aa) + 0.3 + 0.0 * lift, -0.45 - 0.2 * lift))
        # 吊り上げ: 内側 (正面側) が下がる
        F += tube("body", "brow", arc, ra=[0.4, 1.0, 1.15, 1.0, 0.4], rb=[0.4, 0.8, 0.9, 0.8, 0.4], sides=3, hint=tuple(nn))
    # ---- 棘: 二本の帯から放射状に長く
    for z, ln, sgn, ph0 in ((-3.0, 5.4, 0.28, 22.5), (2.5, 5.0, -0.28, 22.5)):
        f = [q for q in spec if q[0] == z][0]
        fk = f[1] * f[2]
        for k in range(n):
            a = math.radians(ph0 + 360 * k / n)
            sx, sy = math.sin(a), math.cos(a)
            low = sy < -0.35
            if abs(sx) > 0.8 and z < 0 and abs(sy) < 0.5 and False:
                continue
            rx, ry = 5.8 * fk, (5.2 if sy >= 0 else 4.8) * fk
            base = V(sx * rx * 0.9, 6.6 + sy * ry * 0.9, z)
            L = ln * (0.6 if low else 1.0)
            F += spike("spines", "spine", base, V(sx * 1.0, sy * 1.0, sgn), L, 1.15, sides=3, rings=1, phase=30)
    # 背の中央と前後の短い棘
    for z, L in ((4.4, 4.4),):
        p = V(0, 6.6 + 5.2 * math.sqrt(max(0.0, 1 - (z / 6.2) ** 2)) * 0.96, z)
        F += spike("spines", "spine", p, V(0, 1, 0.3 if z > 2 else -0.15), L, 1.1, sides=3, rings=1, phase=30)
    for s in (-1, 1):
        for z, th, L in ((-4.6, 62, 2.8), (4.6, 48, 3.0)):
            pp, nn = ell_point(c0, BODY_R, sphere_dir(s * th if z < 0 else s * (180 - th), 34))
            F += spike("spines", "spine", pp, nn + V(0, 0.2, 0.3 if z > 0 else -0.2), L, 0.9, sides=3, rings=1, phase=30)
    # ---- 殻のふじつぼ (短い太い円錐)
    rng = np.random.default_rng(3)
    for th, ph in ((-20, 55), (20, 62), (-52, 38), (52, 30), (-130, 50), (136, 44), (8, 20), (170, 30)):
        pp, nn = ell_point(c0, BODY_R, sphere_dir(th, ph))
        F += spike("body", "barn", pp - nn * 0.3, nn, 1.2, 0.85, sides=4, rings=1)
    # ---- 胸びれ: 骨ばった三本の刃
    for nm, s in (("r", 1), ("l", -1)):
        b = f"fin_{nm}"
        for z0, d, L in ((-2.4, (1.0, -0.15, -0.75), 5.6), (-0.3, (1.0, -0.35, 0.0), 7.2), (1.8, (1.0, -0.5, 0.7), 5.6)):
            dd = V(s * d[0], d[1], d[2])
            F += spike(b, "fin", V(s * 5.2, 5.8, z0), dd, L, 1.3, sides=3, rings=1, phase=90, hint=(0, 0, 1))
    # ---- 尾: 短い三本の棘と膜
    tb = [V(0, 8.8, 5.9), V(0, 6.5, 5.9), V(0, 4.3, 5.9)]
    tt = [V(0.0, 10.2, 10.0), V(0.0, 6.5, 10.8), V(0.0, 2.8, 10.0)]
    F += fan_membrane("tail", "fin", tb, tt, V(3, 7, 7))
    for t in tt:
        F += spike("tail", "spine", t - V(0, 0, 1.0), V(0, 0, 1.0), 2.6, 0.7, sides=3, rings=1, phase=30)
    return F


def decor(ctx, col, glow):
    P, N, mats, bay = ctx.P, ctx.N, ctx.mats, ctx.bay
    pal = PAL
    # 殻: 面ごとに板の継ぎ目、腹は少し明るく
    sh = (mats == "shell")
    belly = sh & (N[..., 1] < -0.3)
    col = np.where(belly[..., None], ctx.ramp_color(pal["shell_lt"], base=0.62, litk=0.2), col)
    nz = vnoise(P * 0.2 + 9, 5)
    pit = sh & ~belly & (hash3(np.floor(P * 0.5), 6) > 0.95) & ~ctx.edge
    col = np.where(pit[..., None], np.array(pal["shell"][0], float), col)
    sea = sh & ~belly & (nz > 0.66 + 0.02 * bay)
    col = np.where(sea[..., None], ctx.ramp_color(pal["shell_dk"], base=0.7), col)
    # 帯: 面の中に二本のこげ茶の筋
    belt = (mats == "belt")
    line = belt & (((ctx.LV * 1) % 4) == 1) & ctx.INS & ~ctx.edge
    col = np.where(line[..., None], np.array(pal["belt"][1], float), col)
    # 棘: 橙の縞。根元から先へ橙と暗い赤の縞、先は骨色
    spn = (mats == "spine")
    cen = np.array([0, CY0, 0])
    dist = np.linalg.norm(P - cen, axis=-1)
    tcol = np.array(pal["spine"], float)
    band = np.floor(dist * 0.95) % 2
    ti = np.clip(np.floor(2.0 + 0.9 * ctx.lit + 0.35 * bay - 1.0 * band), 0, 3).astype(int)
    c2 = tcol[ti]
    tip = (dist > 9.6) | ((dist > 8.2) & (ctx.bones == "tail"))
    c2 = np.where((tip & (ctx.bones != "tail"))[..., None], np.array(pal["spine"][4], float) * (0.7 + 0.2 * ctx.lit[..., None] + 0.1 * bay[..., None]), c2)
    col = np.where(spn[..., None], c2, col)
    # 目: 暗い目に細い光る横長の瞳
    for e in EYES:
        sel = (mats == "eye")
        rel = P - e["c"]
        loc = np.einsum("...k,kj->...j", rel, e["R"])
        near = sel & (np.linalg.norm(rel, axis=-1) < 3.4) & (loc[..., 2] < 0.0)
        dk = near & ((loc[..., 0] / 2.2) ** 2 + (loc[..., 1] / 1.25) ** 2 < 1.0 + 0.05 * bay)
        pup = near & (np.abs(loc[..., 0]) < 1.45) & (np.abs(loc[..., 1] + 0.1 * loc[..., 0] * e["s"]) < 0.5 + 0.05 * bay)
        col = np.where(dk[..., None], np.array(pal["eye_dk"], float), col)
        col = np.where(pup[..., None], np.array([220, 252, 255], float), col)
        glow |= pup
    # ひれの筋
    fin = (mats == "fin")
    rib = fin & ctx.edge
    col = np.where(rib[..., None], np.array(pal["fin"][1], float), col)
    return col, glow


SHELL = [(10, 16, 34), (22, 36, 62), (40, 64, 94), (68, 100, 128), (108, 140, 160)]
PAL = {
    "mats": {
        "shell": dict(ramp=SHELL, base=0.66),
        "belt": dict(ramp=[(86, 76, 70), (140, 128, 108), (190, 176, 144), (230, 218, 182)], base=0.72, litk=0.18),
        "lip": dict(ramp=[(50, 22, 30), (92, 36, 44), (140, 58, 56), (186, 90, 72)], base=0.6, litk=0.2),
        "dark": dict(ramp=[(6, 6, 12), (12, 12, 20), (22, 22, 32)], base=0.5, minidx=0, edges=False),
        "fang": dict(ramp=[(130, 120, 100), (190, 180, 152), (232, 224, 196), (250, 246, 222)], base=0.78, minidx=0),
        "eye": dict(ramp=[(60, 40, 40), (120, 80, 70), (180, 130, 90), (230, 190, 120)], base=0.7, minidx=0),
        "brow": dict(ramp=[(8, 14, 28), (18, 30, 52), (34, 56, 84), (58, 88, 116)], base=0.62),
        "fin": dict(ramp=[(70, 40, 40), (140, 80, 56), (208, 128, 70), (244, 184, 104)], base=0.6, litk=0.2, edges=False),
        "barn": dict(ramp=[(100, 92, 80), (160, 148, 124), (206, 196, 164), (238, 230, 198)], base=0.7),
        "spine": dict(ramp=[(60, 28, 28), (122, 58, 36), (190, 100, 48), (236, 152, 66)]),
    },
    "shell": SHELL,
    "shell_lt": [(26, 40, 64), (50, 74, 100), (82, 112, 136), (122, 152, 168), (166, 192, 196)],
    "shell_dk": [(6, 10, 22), (14, 22, 42), (26, 40, 66), (44, 68, 98), (70, 100, 128)],
    "belt": [(86, 76, 70), (60, 52, 50), (190, 176, 144), (230, 218, 182)],
    "spine": [(60, 28, 28), (122, 58, 36), (190, 100, 48), (236, 152, 66), (250, 232, 190)],
    "fin": [(70, 40, 40), (92, 52, 44), (208, 128, 70), (244, 184, 104)],
    "eye_dk": (12, 10, 18),
}
HINT = 128
HEAD_CENTER, HEAD_SCALE = (0, 5.6, -4.0), 26.0
