"""A案「まんまる提灯」: 面取りした丸い玉の体に、すっとぼけた大きな目 (まぶた付き) とすぼめた唇・2 本の前歯。
全身に先の細い短い棘、体の横には丸い扇の胸びれ、後ろに二股の尾びれ。膨らむと玉がそのまま大きくなる、
いちばんフグらしい輪郭。青緑の背・クリーム色の腹・赤みの唇。"""
from pf_common import *  # noqa

CY0 = 6.8
BODY_R = (6.0, 5.4, 6.3)       # 体の楕円体 (rx, ry(上), rz) と、棘や目を置く基準
EYES = []


def prof(f, dz=0.0):
    return (6.0 * f, 5.4 * f, 5.0 * f)


def build():
    EYES.clear()
    F = []
    n = 12
    rings = []
    for z, f, cy in ((-6.0, 0.36, 5.0), (-4.9, 0.66, 6.0), (-3.2, 0.86, 6.6), (-1.5, 0.97, 6.8), (0.2, 1.0, 6.8),
                     (1.9, 0.97, 6.8), (3.5, 0.84, 6.7), (5.0, 0.62, 6.6), (6.0, 0.36, 6.5)):
        rx, rt, rb = prof(f)
        rings.append(zring(z, rx, rt, rb, cy, n=n, phase=0.0))
    F += loft("body", "body", rings, caps=("body", "body"))
    # ---- 唇: すぼめた六角の輪 + 暗い口の中 + 2 本の前歯
    mc = V(0, 4.9, -6.15)
    pts = []
    for k in range(7):
        a = math.radians(360 * k / 6 + 30)
        pts.append(mc + V(2.05 * math.cos(a), 1.05 * math.sin(a), -0.15 * math.cos(2 * a)))
    F += tube("body", "lip", pts, ra=0.75, rb=0.75, sides=4, hint=(0, 0, 1), phase=45)
    hole = [mc + V(1.45 * math.cos(math.radians(360 * k / 6 + 30)), 0.7 * math.sin(math.radians(360 * k / 6 + 30)), -0.25)
            for k in range(6)]
    F += poly("body", "dark", hole, mc + V(0, 0, 3))
    for x in (-0.55, 0.55):
        F += tube("body", "tooth", [V(x, 5.25, -6.5), V(x, 4.05, -6.75)], ra=[0.5, 0.45], rb=[0.32, 0.28], sides=4,
                  hint=(1, 0, 0), caps=(False, True))
    # ---- 目: 横向きの丸屋根。まぶたは上半分にかぶさる
    c0 = V(0, CY0, 0)
    for s in (-1, 1):
        d = sphere_dir(s * 64, 14)
        p, nn = ell_point(c0, BODY_R, d)
        EYES.append(dict(c=p + nn * 0.1, n=nn, r=2.1, s=s))
        F += dome("body", "eye", p, nn, 2.2, 1.7, sides=8, mid=0.72, phase=22.5)
        R = frame_from_normal(nn)
        arc = []
        for a in (170, 110, 55, 8):
            aa = math.radians(a)
            arc.append(p + R @ V(2.35 * math.cos(aa), 2.35 * math.sin(aa) * 0.9 + 0.05, -0.55) + 0.0)
        F += tube("body", "lid", arc, ra=[0.55, 0.85, 0.85, 0.5], rb=[0.55, 0.7, 0.7, 0.5], sides=3, hint=tuple(nn))
    # ---- 棘: らせん状に全身へ。顔 (前の円錐) は避ける
    rng = np.random.default_rng(7)
    k = 0
    dirs = []
    N = 62
    for i in range(N):
        zc = 1 - 2 * (i + 0.5) / N
        ph = math.degrees(math.asin(zc))
        th = math.degrees(i * 2.399963)
        dirs.append((th, ph))
    for th, ph in dirs:
        d = sphere_dir(th, ph)
        if d[2] < -0.50 and d[1] < 0.55:
            continue              # 顔の面
        if abs(abs(math.degrees(math.atan2(d[0], -d[2])) % 360 - 180) - 116) < 26 and abs(d[1] - 0.24) < 0.3 and False:
            continue
        if d[1] < -0.62:
            continue              # 腹の下は短く控えめ
        p, nn = ell_point(c0, BODY_R, d)
        # 目の近くは避ける
        if any(np.dot(nn, e["n"]) > 0.80 for e in EYES):
            continue
        if abs(d[0]) < 0.12 and d[2] > 0.5 and d[1] < 0.5:
            continue              # 尾の付け根
        ln = 2.3 + 1.3 * rng.random() + (0.9 if d[1] > 0.6 else 0)
        F += spike("spines", "spine", p - nn * 0.45, nn + V(0, 0.12, 0.18), ln, 1.0, sides=3, rings=1)
    # ---- 胸びれ: 丸い扇。膜は薄く、骨 (筋) は縁の線
    for nm, s in (("r", 1), ("l", -1)):
        b = f"fin_{nm}"
        bz = [-3.4, -2.2, -1.0, 0.2, 1.4, 2.6]
        base = [V(s * 5.5, 6.2 - 0.3 * i, z) for i, z in enumerate(bz)]
        ang = [-52, -22, 6, 34, 62, 88]
        ln = [4.4, 6.0, 6.8, 6.4, 5.4, 3.8]
        tips = []
        w = V(0, -0.62, 0.78)
        for a, L, bp in zip(ang, ln, base):
            aa = math.radians(a)
            tips.append(bp + V(s, 0, 0) * (L * math.cos(aa)) + w * (L * math.sin(aa)))
        F += fan_membrane(b, "fin", base, tips, V(s * 4, 6.5, 0))
        for bp, tp in zip(base, tips):
            pass
        F += poly(b, "fin_base", [base[0], base[-1], base[-1] + V(-s * 0.5, -1.0, 0), base[0] + V(-s * 0.5, -1.0, 0)], V(s * 9, 5, 0))
    # ---- 尾びれ: 二股
    tb = [V(0, 9.4, 5.9), V(0, 7.0, 5.9), V(0, 4.6, 5.9)]
    tt = [V(1.0, 12.4, 10.8), V(0.0, 7.0, 8.3), V(-1.0, 1.8, 10.8)]
    F += fan_membrane("tail", "fin", tb, tt, V(3, 7, 7))
    F += poly("tail", "fin", [tb[0], tt[0], V(0.5, 10.6, 8.2)], V(3, 7, 7))
    F += poly("tail", "fin", [tb[2], V(-0.5, 3.4, 8.2), tt[2]], V(3, 7, 7))
    return F


def _blob(ctx, thr=0.6):
    p = ctx.P
    return 0.7 * vnoise(p * 0.17 + 5, 3) + 0.3 * vnoise(p * 0.4 + 1, 8)


def decor(ctx, col, glow):
    P, N, bones, mats, bay = ctx.P, ctx.N, ctx.bones, ctx.mats, ctx.bay
    own = ctx.owned
    pal = PAL
    body = (mats == "body")
    # 腹: 向きが下の面をクリーム色に (境目は市松に溶かす)
    belly_t = N[..., 1] + 0.18 + 0.20 * bay
    belly = body & (belly_t < 0.0) | (body & (P[..., 1] < 3.2))
    cream = ctx.ramp_color(pal["cream"], base=0.72, litk=0.17, noise=0.05)
    col = np.where(belly[..., None], cream, col)
    # 背: 濃い斑と明るい点
    back = body & ~belly
    nz = _blob(ctx)
    dark = back & (nz > 0.64 + 0.015 * bay)
    col = np.where(dark[..., None], ctx.ramp_color(pal["teal_dk"], base=0.7, litk=0.17), col)
    dots = back & (hash3(np.floor(P * 0.5), 4) > 0.965) & (ctx.ny > 0.0) & ~ctx.edge
    col = np.where(dots[..., None], np.array(pal["teal"][-1], float), col)
    # 腹の縞 (横)
    # 目: 白目は薄いクリーム、虹彩は暗く、瞳は光る
    for e in EYES:
        sel = (mats == "eye")
        rel = P - e["c"]
        ax = np.einsum("...k,k->...", rel, e["n"])
        perp = np.linalg.norm(rel - ax[..., None] * e["n"], axis=-1)
        near = sel & (np.abs(ax) < 3) & (np.einsum("...k,k->...", rel, e["n"]) > -0.8)
        near &= (np.linalg.norm(rel, axis=-1) < 3.6)
        iris = near & (perp < 1.75 + 0.12 * bay)
        pup = near & (perp < 0.95 + 0.08 * bay)
        col = np.where(iris[..., None], np.array(pal["iris"][0], float), col)
        col = np.where(pup[..., None], np.array(pal["iris"][1], float), col)
        glow |= pup
    # 唇: 内側の縁を暗く、歯は縁を暗い線で
        # 鰭: 筋は縁の線、つけ根は濃く
    fin = (mats == "fin")
    rib = fin & ctx.edge
    col = np.where(rib[..., None], np.array(pal["fin"][1], float) * 0.95, col)
    fb = (mats == "fin_base")
    col = np.where(fb[..., None], ctx.ramp_color(pal["teal_dk"], base=0.6), col)
    finmark = fin & (hash3(np.floor(P * 0.9), 2) > 0.9)
    col = np.where(finmark[..., None], np.array(pal["fin"][-1], float), col)
    # 棘: 根元は暗く、先は明るく (体の中心からの距離で段をつける)
    sp = (mats == "spine")
    dist = np.linalg.norm((P - np.array([0, CY0, 0])) / np.array([6.0, 5.4, 6.3]), axis=-1)
    tcol = np.array(pal["spine"], float)
    ti = np.clip(np.floor(1.2 + (dist - 1.0) * 3.0 + 0.9 * ctx.lit + 0.4 * bay), 0, len(tcol) - 1).astype(int)
    col = np.where(sp[..., None], tcol[ti], col)
    return col, glow


TEAL = [(14, 36, 50), (24, 62, 78), (40, 98, 110), (68, 140, 142), (112, 184, 170)]
PAL = {
    "mats": {
        "body": dict(ramp=TEAL, base=0.70),
        "lip": dict(ramp=[(70, 28, 36), (128, 52, 56), (184, 88, 80), (226, 130, 104)], base=0.65, litk=0.2),
        "dark": dict(ramp=[(6, 8, 14), (12, 14, 22), (24, 26, 36)], base=0.5, minidx=0, edges=False),
        "tooth": dict(ramp=[(150, 140, 120), (204, 196, 170), (238, 232, 208)], base=0.75, minidx=0),
        "eye": dict(ramp=[(150, 150, 132), (196, 198, 176), (230, 232, 208), (250, 250, 232)], base=0.78, minidx=0),
        "lid": dict(ramp=[(16, 44, 58), (30, 74, 86), (52, 112, 118), (86, 150, 146)], base=0.7),
        "fin": dict(ramp=[(44, 92, 106), (84, 148, 152), (140, 200, 184), (206, 234, 212)], base=0.62, litk=0.18, edges=False),
        "fin_base": dict(ramp=TEAL, base=0.6),
        "spine": dict(ramp=[(60, 44, 40), (120, 90, 64), (184, 148, 100), (226, 196, 144)]),
    },
    "teal": TEAL,
    "teal_dk": [(8, 22, 34), (16, 44, 60), (28, 72, 88), (48, 108, 118), (80, 150, 148)],
    "cream": [(96, 90, 82), (146, 136, 116), (196, 186, 156), (232, 224, 190), (250, 244, 214)],
    "tooth": [(150, 140, 120), (96, 88, 74)],
    "iris": [(14, 18, 28), (210, 250, 255)],
    "fin": [(44, 92, 106), (70, 130, 138), (140, 200, 184), (214, 240, 218)],
    "spine": [(70, 56, 52), (140, 112, 84), (200, 170, 124), (232, 210, 162), (250, 238, 196)],
}
HINT = 128
HEAD_CENTER, HEAD_SCALE = (0, 5.4, -4.0), 26.0
