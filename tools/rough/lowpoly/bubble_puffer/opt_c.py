"""C案「ラッパ喉の泡吹き」: 後ろへ細くなる涙形の体。前へ突き出したラッパ形の口 (縁に 2 本の前歯) と、
あごの下の泡袋 (輪の筋)。目は短い柄の先に乗った大きな丸い目 (半分閉じたまぶた)、背には扇状の背びれ、
横の大きな翼のような胸びれ、尾は三枚の長いひらひら。藍の背・ミントの腹と袋・珊瑚色のひれ・泡の輪の模様。
棘は背びれの先と頬の短いものだけで、泡を吹く道具としての形を前に出す。"""
from pf_common import *  # noqa

EYES = []
SPEC = [(-5.5, 1.9, 1.9, 1.9, 5.3), (-4.4, 3.8, 3.9, 3.4, 6.1), (-2.2, 5.2, 5.0, 4.4, 6.6), (0.4, 5.6, 5.4, 4.8, 6.9),
        (3.0, 5.0, 4.8, 4.3, 6.8), (5.2, 3.6, 3.6, 3.2, 6.6), (7.0, 2.3, 2.4, 2.2, 6.6), (8.6, 1.4, 1.5, 1.4, 6.6)]


def top_y(z):
    zs = [q[0] for q in SPEC]
    ys = [q[4] + q[2] for q in SPEC]
    return float(np.interp(z, zs, ys))


def build():
    EYES.clear()
    F = []
    rings = [zring(z, rx, rt, rb, cy, n=10, phase=0.0) for z, rx, rt, rb, cy in SPEC]
    F += loft("body", "body", rings, caps=("body", "body"))
    # ---- ラッパの口: 前へ細い筒が開いていく。縁の内側に 2 本の前歯
    path = [V(0, 5.3, -5.4), V(0, 5.2, -7.0), V(0, 5.1, -8.4), V(0, 5.0, -9.6)]
    F += tube("body", "lip", path, ra=[1.75, 1.45, 1.9, 2.9], rb=[1.75, 1.45, 1.9, 2.9], sides=6, hint=(0, 1, 0), phase=0)
    hole = [V(2.15 * math.sin(math.radians(60 * k)), 5.0 + 2.15 * math.cos(math.radians(60 * k)), -9.45) for k in range(6)]
    F += poly("body", "dark", hole, V(0, 5, -4))
    for x in (-0.65, 0.65):
        F += tube("body", "tooth", [V(x, 7.15, -9.7), V(x, 5.85, -9.85)], ra=[0.55, 0.5], rb=[0.35, 0.3], sides=4, hint=(1, 0, 0),
                  caps=(False, True))
    # ---- 泡袋 (あごの下)
    F += ellipsoid("body", "sac", V(0, 3.0, -3.2), (3.5, 2.5, 3.3), segs=8, rings=2, phase=22.5)
    # ---- 目: 短い柄の先の丸い目
    for s in (-1, 1):
        nn = nrm(V(s * 0.5, 0.3, -0.82))
        tip = V(s * 3.3, 12.0, -2.6)
        root = V(s * 2.4, 10.2, -2.6)
        F += tube("body", "stalk", [root, tip], ra=[1.35, 1.2], rb=[1.35, 1.2], sides=5, hint=(0, 0, 1))
        c = tip + nn * 0.2
        EYES.append(dict(c=c, n=nn, s=s))
        F += dome("body", "eye", c, nn, 2.9, 2.2, sides=8, mid=0.78, phase=22.5, base_in=0.9)
        R = frame_from_normal(nn)
        arc = []
        for a in (172, 120, 70, 20):
            aa = math.radians(a)
            arc.append(c + R @ V(3.15 * math.cos(aa), 3.0 * math.sin(aa) - 0.15, -0.6))
        F += tube("body", "lid", arc, ra=[0.6, 1.0, 1.0, 0.55], rb=[0.55, 0.9, 0.9, 0.5], sides=3, hint=tuple(nn))
    # ---- 背びれ (扇) と先の短い棘。背骨の線の上
    zs = [-2.6, -0.8, 1.0, 2.8, 4.4, 5.8]
    hs = [3.2, 5.2, 6.4, 5.6, 4.0, 2.4]
    base = [V(0, top_y(z) - 0.5, z) for z in zs]
    tips = [V(0.5 * (-1) ** i, top_y(z) + h, z + 1.8 + 0.2 * i) for i, (z, h) in enumerate(zip(zs, hs))]
    F += fan_membrane("spines", "sail", base, tips, V(3, 7, 0))
    for t, bp in zip(tips, base):
        F += spike("spines", "ray", t - nrm(t - bp) * 0.6, t - bp, 2.0, 0.7, sides=3, rings=1)
    for s in (-1, 1):
        for z, y, L in ((-3.4, 5.4, 2.4), (-1.2, 4.0, 2.6)):
            F += spike("spines", "ray", V(s * 5.0, y + 1.0, z), V(s * 1.0, 0.25, -0.35), L, 0.8, sides=3, rings=1)
    # ---- 胸びれ: 翼のように上後ろへ開く扇
    for nm, s in (("r", 1), ("l", -1)):
        b = f"fin_{nm}"
        bz = [-2.6, -1.4, -0.2, 1.0, 2.2, 3.4]
        base = [V(s * 5.5, 5.0 - 0.15 * i, z) for i, z in enumerate(bz)]
        ang = [-60, -34, -8, 20, 48, 76]
        ln = [3.8, 5.8, 7.4, 7.6, 6.4, 4.4]
        w = V(0, 0.55, 0.83)
        tips = []
        for a, L, bp in zip(ang, ln, base):
            aa = math.radians(a)
            tips.append(bp + V(s, 0.12, 0) * (L * math.cos(aa)) + w * (L * math.sin(aa)))
        F += fan_membrane(b, "wing", base, tips, V(s * 4, 6.5, 0))
    # ---- 尾: 三枚のひらひら (格子の板)
    rows = []
    ys0 = [8.2, 7.2, 6.2, 5.2]
    ys1 = [10.2, 8.0, 5.2, 3.0]
    ys2 = [13.0, 9.0, 4.4, 0.8]
    rows = [[V(0, y, 8.4) for y in ys0], [V(0.9 * (-1) ** i, y, 11.0) for i, y in enumerate(ys1)],
            [V(-1.0 * (-1) ** i, y, 13.8) for i, y in enumerate(ys2)]]
    F += grid("tail", "ribbon", rows, V(3, 7, 5))
    return F


def _rings(P, seed, cell=4.6, rmin=1.3, rmax=2.0, th=0.5):
    q = np.floor(P / cell)
    ctr = (q + 0.5 + (np.stack([hash3(q, seed), hash3(q, seed + 1), hash3(q, seed + 2)], -1) - 0.5) * 0.4) * cell
    r = rmin + (rmax - rmin) * hash3(q, seed + 5)
    d = np.linalg.norm(P - ctr, axis=-1)
    on = hash3(q, seed + 7) > 0.35
    return on & (np.abs(d - r) < th), on & (np.abs(d - r * 0.35) < 0.3)


def decor(ctx, col, glow):
    P, N, mats, bay = ctx.P, ctx.N, ctx.mats, ctx.bay
    pal = PAL
    body = (mats == "body")
    belly_t = N[..., 1] + 0.2 + 0.2 * bay
    belly = body & (belly_t < 0.0)
    col = np.where(belly[..., None], ctx.ramp_color(pal["mint"], base=0.72, litk=0.17), col)
    back = body & ~belly
    # 背の中心線を明るい帯に
    stripe = back & (np.abs(P[..., 0]) < 1.5 + 0.2 * bay) & (N[..., 1] > 0.5)
    col = np.where(stripe[..., None], ctx.ramp_color(pal["blue_lt"], base=0.7, litk=0.17), col)
    ring, dot = _rings(P, 3)
    rb = body & ring & ~ctx.edge
    col = np.where((rb & back)[..., None], np.array(pal["blue_lt"][3], float), col)
    col = np.where((rb & belly)[..., None], np.array(pal["mint"][4], float), col)
    # 泡袋: 輪の筋
    sac = (mats == "sac")
    band = sac & ((np.floor((P[..., 2] + 20) * 1.1) % 2) == 0) & ~ctx.edge
    col = np.where(band[..., None], ctx.ramp_color(pal["sac2"], base=0.7, litk=0.17), col)
    r2, d2 = _rings(P + 11.0, 9, cell=3.0, rmin=0.8, rmax=1.2)
    col = np.where((sac & r2)[..., None], np.array(pal["sac"][4], float), col)
    # 唇の縁
    lip = (mats == "lip") & (((ctx.LV) % 5) == 2) & ctx.INS
    col = np.where(lip[..., None], ctx.ramp_color(pal["lip_dk"], base=0.7), col)
    # 目: 白目、大きな黒目、光る瞳
    for e in EYES:
        sel = (mats == "eye")
        rel = P - e["c"]
        ax = rel @ e["n"]
        perp = np.linalg.norm(rel - ax[..., None] * e["n"], axis=-1)
        near = sel & (ax > -0.5) & (np.linalg.norm(rel, axis=-1) < 4.0)
        iris = near & (perp < 2.05 + 0.1 * bay)
        pup = near & (perp < 1.0 + 0.08 * bay)
        col = np.where(iris[..., None], np.array(pal["iris"], float), col)
        col = np.where(pup[..., None], np.array([214, 252, 255], float), col)
        glow |= pup
    # ひれ: 筋 (縁の線) と先ほど明るく
    for m in ("sail", "wing", "ribbon"):
        sel = (mats == m)
        rib = sel & ctx.edge
        col = np.where(rib[..., None], np.array(pal[m][1], float), col)
    ribn = (mats == "ribbon") & (hash3(np.floor(P * 0.7), 4) > 0.9)
    col = np.where(ribn[..., None], np.array(pal["ribbon"][3], float), col)
    return col, glow


BLUE = [(8, 12, 36), (18, 28, 74), (34, 54, 122), (62, 94, 168), (110, 140, 206)]
PAL = {
    "mats": {
        "body": dict(ramp=BLUE, base=0.68),
        "lip": dict(ramp=[(88, 46, 40), (150, 84, 62), (206, 130, 84), (242, 184, 120)], base=0.66, litk=0.2),
        "dark": dict(ramp=[(6, 8, 18), (12, 14, 26), (24, 26, 40)], base=0.5, minidx=0, edges=False),
        "tooth": dict(ramp=[(150, 150, 140), (208, 208, 192), (240, 240, 224)], base=0.78, minidx=0),
        "sac": dict(ramp=[(60, 124, 128), (110, 176, 168), (170, 216, 198), (220, 244, 224)], base=0.64, litk=0.2),
        "stalk": dict(ramp=BLUE, base=0.7),
        "eye": dict(ramp=[(150, 160, 170), (196, 206, 214), (232, 238, 242), (252, 252, 252)], base=0.78, minidx=0),
        "lid": dict(ramp=[(12, 20, 56), (26, 44, 100), (46, 76, 144), (80, 114, 184)], base=0.7),
        "sail": dict(ramp=[(118, 40, 82), (176, 70, 104), (226, 112, 120), (250, 170, 150)], base=0.58, litk=0.2, edges=False),
        "wing": dict(ramp=[(100, 44, 96), (150, 76, 124), (206, 120, 148), (244, 180, 176)], base=0.6, litk=0.2, edges=False),
        "ribbon": dict(ramp=[(100, 44, 96), (150, 76, 124), (206, 120, 148), (244, 180, 176)], base=0.6, litk=0.2, edges=False),
        "ray": dict(ramp=[(160, 140, 130), (206, 190, 170), (238, 226, 200), (252, 244, 224)], base=0.72, minidx=0),
    },
    "mint": [(56, 100, 110), (100, 156, 154), (156, 204, 188), (206, 238, 214), (240, 252, 232)],
    "blue_lt": [(14, 22, 56), (28, 46, 100), (50, 80, 148), (96, 130, 192), (150, 176, 222)],
    "sac": [(60, 124, 128), (110, 176, 168), (170, 216, 198), (220, 244, 224), (246, 254, 240)],
    "sac2": [(40, 96, 112), (76, 140, 148), (130, 190, 180), (190, 228, 208)],
    "lip_dk": [(60, 28, 32), (110, 56, 48), (160, 90, 62), (200, 130, 84)],
    "sail": [(118, 40, 82), (92, 30, 70), (226, 112, 120), (250, 170, 150)],
    "wing": [(100, 44, 96), (82, 32, 82), (206, 120, 148), (244, 180, 176)],
    "ribbon": [(100, 44, 96), (82, 32, 82), (206, 120, 148), (250, 200, 196)],
    "iris": (14, 18, 36),
}
HINT = 128
HEAD_CENTER, HEAD_SCALE = (0, 7.0, -5.0), 20.0
