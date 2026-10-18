"""B 玄武岩の柱と大炉の記念碑: 全身を段 (テラス) と柱状節理の玄武岩で刻み、背の火山は四段の火口の段丘 (ジグラット)。
背には灰の民の煉瓦の炉・煙突・鞴が並び、溶岩は決まった溝を流れる。動く遺跡の鍛冶場。"""
from parts import *  # noqa

TMPL = [[(0, 0), (0.86, 0), (1.0, 0.10), (1.0, 0.50), (0.80, 0.54), (0.80, 1.0), (0, 1.0)],
        [(0, 0), (0.92, 0), (1.0, 0.1), (1.0, 0.5), (0.7, 0.52), (0.7, 1.0), (0, 1.0)],
        [(0, 0), (1.0, 0.2), (1.0, 0.5), (0.68, 1.0), (0, 1.0)]]

# 頭は四角い仮面 (幅は広いまま、上を平らに)
KEYS_B = [(z, cx, hw, yb, yt) for (z, cx, hw, yb, yt) in KEYS]


ST_B0 = [-208, -200, -190, -180, -170, -160, -150, -142, -136, -124, -110, -96, -82, -68, -54, -40, -20, 0, 20, 40, 60, 76, 92, 108,
         124, 140, 156, 172, 188, 204, 220, 236, 248]
ST_B = [ST_B0, ST_MID, ST_FAR]


def hull(lod):
    return Hull(TMPL[lod], KEYS_B, ST_B[lod], jit=0.012 if lod == 0 else 0.008, jit_y=0.15, bone_fn=bone_fn,
                mat_fn=std_mat("rock", "belly"), seed=2)


def columns(h, lod):
    """段の上に並ぶ玄武岩の六角柱 (柱状節理)。背丈はふぞろい、頭が少し欠ける"""
    fs = []
    if lod == 2:
        return fs
    K = h.K
    step = 18.0 if lod == 0 else 26.0
    sidx = (K - 3.0, K - 1.5) if lod == 0 else (K - 3.0,)
    z = -118.0
    i = 0
    while z < 104:
        for side in (1, -1):
            for ti, s in enumerate(sidx):
                if (abs(z - VOLC_C[1]) < 36 and ti == len(sidx) - 1) or (ti == len(sidx) - 1 and lod == 0 and i % 2):
                    continue
                zz = z + (hash1(i * 7 + ti, 21) - .5) * 4
                p, n = h.at(zz, s, side)
                hh = 5.0 + 10.0 * hash1(i * 5 + ti, 22) ** 1.3
                r = 3.2 + 1.2 * hash1(i * 3 + ti, 23)
                bone = "ridge" if -96 <= zz < 100 else ("body" if -136 <= zz < 108 else ("tail" if zz >= 108 else "head"))
                if lod == 0:
                    fs += col6(bone, "plate", (p[0] - side * 1.0, p[1] - 1.2, p[2]), hh + 1.2, r, sides=6, taper=0.92, top_mat=None, cap=True)
                else:
                    fs += col6(bone, "plate", (p[0] - side * 1.0, p[1] - 1.2, p[2]), hh + 1.2, r * 1.15, sides=4, taper=0.9, top_mat=None, cap=True)
        z += step
        i += 1
    return fs


def ziggurat(lod):
    """四段の火口の段丘。段ごとに垂直の壁と平らなひさし、火口の縁の煉瓦、段の隅の溶岩の溝"""
    cx, cz = VOLC_C
    n = (8, 8, 8)[lod]
    tiers = [(58, 76, 54, 46), (76, 94, 42, 34), (94, 112, 30, 24), (112, 130, 19, 15)]
    if lod == 1:
        tiers = [(58, 80, 54, 42), (80, 104, 40, 28), (104, 130, 26, 15)]
    if lod == 2:
        tiers = [(58, 94, 54, 30), (94, 130, 28, 15)]
    fs = []
    prev_r = None
    for ti, (y0, y1, r0, r1) in enumerate(tiers):
        rot = 22.5
        a = poly_ring(cx, cz, y0, r0, n, rot=rot, jit=0.0)
        b = poly_ring(cx, cz, y1, r0 - 1.2, n, rot=rot, jit=0.0)
        fs += ring_loft("ridge", "cone", [a, b])
        # ひさし (段の上面): 外 r0-1.2 から内 r1
        c = poly_ring(cx, cz, y1, r1, n, rot=rot, jit=0.0)
        fs += ring_loft("ridge", "plate", [b, c])
    # 火口の縁の壁 (煉瓦) と中の溶岩
    top_y = tiers[-1][1]
    ro = poly_ring(cx, cz, top_y, tiers[-1][3], n, rot=22.5)
    rb = poly_ring(cx, cz, top_y + 8.0, tiers[-1][3] - 0.6, n, rot=22.5)
    ri = poly_ring(cx, cz, top_y + 8.0, tiers[-1][3] - 4.0, n, rot=22.5)
    rp = poly_ring(cx, cz, top_y - 2.0, tiers[-1][3] - 4.5, n, rot=22.5)
    fs += ring_loft("ridge", "brick", [ro, rb])
    fs += ring_loft("ridge", "rimcrust", [rb, ri])
    fs += ring_loft("ridge", "brick", [ri, rp], inward=True)
    fs += disc("ridge", "lava", rp)
    # 溝: 段ごとの四隅から流れる光る帯 (煉瓦の縁取り付き)
    if True:
        for k in (range(4) if lod < 2 else (0, 2)):
            a = math.radians(45.0 + 90.0 * k + 22.5 * 0)
            for ti, (y0, y1, r0, r1) in enumerate(tiers):
                if lod == 1 and ti == 0:
                    pass
                rr = r0 - 0.6
                px, pz = cx + math.sin(a) * rr, cz + math.cos(a) * rr
                dirv = V(math.sin(a), 0, math.cos(a))
                tang = V(math.cos(a), 0, -math.sin(a))
                fs += slab("ridge", "lava", (px + dirv[0] * 0.2, (y0 + y1) / 2, pz + dirv[2] * 0.2), (4.0, y1 - y0 - 1.0, 1.2), taper=0.0, rot=math.degrees(a) + 0.0)
    # 段の隅の炉小屋 (煉瓦の箱に光る窓)
    if lod == 0:
        for ti, (y0, y1, r0, r1) in enumerate(tiers[:3]):
            for k in range(2):
                a = math.radians(90.0 * k * 2 + 20.0 + ti * 40.0)
                rr = (r0 - 1.2 + r1) / 2 + 1.0
                px, pz = cx + math.sin(a) * rr, cz + math.cos(a) * rr
                fs += slab("ridge", "brick", (px, y1 + 3.0, pz), (7.0, 6.0, 7.0), taper=0.8, rot=math.degrees(a))
                dv = V(math.sin(a), 0, math.cos(a))
                fs += slab("ridge", "ruinglow", (px + dv[0] * 3.3, y1 + 2.4, pz + dv[2] * 3.3), (2.6, 2.4, 1.0), taper=0.0, rot=math.degrees(a))
    return fs


def forge_row(h, lod):
    """背の火山のうしろ: 灰の民の煉瓦の炉と煙突、銅の鞴、溶岩の溝"""
    fs = []
    if lod == 2:
        return fs
    # 溶岩の溝 (まっすぐ・煉瓦の縁)
    top = h.top(50.0, 0.0)
    za, zb = 6.0, 98.0
    fs += slab("ridge", "brick", (0, top + 0.6, (za + zb) / 2), (12.0, 1.4, zb - za), taper=0.3)
    fs += slab("ridge", "lava", (0, top + 1.5, (za + zb) / 2), (6.5, 0.6, zb - za - 2.0), taper=0.0)
    for i, z in enumerate((14.0, 46.0, 78.0) if lod == 0 else (24.0, 70.0)):
        for sd in (1, -1):
            xo = sd * 17.0
            top = h.top(z, xo)
            # 煙突 (煉瓦の柱 + 銅の帯)
            fs += col6("ridge", "brick", (xo, top - 1.0, z), 17.0 + 4 * (i % 2), 5.0, sides=4, taper=0.82, top_mat="rimcrust")
            if lod == 0:
                fs += col6("ridge", "copper", (xo, top + 7.0, z), 2.2, 5.2, sides=4, taper=1.0, cap=False)
                fs += slab("ridge", "ruinglow", (xo, top + 2.4, z - 4.3), (2.4, 2.6, 0.8), taper=0.0)
            # 鞴 (銅の樽)
            if lod == 0:
                fs += col6("ridge", "copper", (sd * 8.0, h.top(z + 6, sd * 8.0) - 1.0, z + 6.0), 6.0, 4.4, sides=6, taper=0.9, top_mat="plate")
    return fs


def tail_pillars(h, lod):
    fs = []
    if lod > 0:
        return fs
    z = 118.0
    i = 0
    while z < 226:
        cx = h.key_at(z)[0]
        top = h.top(z)
        hh = max(3.0, 12.0 - (z - 118) * 0.07)
        fs += col6("tail", "plate", (cx, top - 1.2, z), hh + 1.2, max(2.0, 4.6 - (z - 118) * 0.02), sides=6, taper=0.9, top_mat=None)
        z += 12.0
        i += 1
    return fs


def head_mask(head, lod):
    """四角い仮面: 分厚い眉の板、頬の板、頭頂の柱の冠"""
    fs = []
    K = head.K
    if lod == 2:
        return fs
    for side in (1, -1):
        p, n = head.at(EYE_Z, K - 1.5, side)
        fs += plinth("head", "plate", p + V(0, 1.0, 0), n, (0, 1, 0), 14.0, 3.6, 3.6 if lod == 0 else 3.0, 0.7, 4, embed=1.2, rot=45)
        if lod == 0:
            p2, n2 = head.at(-190.0, K - 2.6, side)
            fs += plinth("head", "plate", p2, n2, (0, 1, 0), 8.0, 5.0, 2.0, 0.8, 4, embed=1.0, rot=45)
            p3, n3 = head.at(-162.0, K - 3.4, side)
            fs += plinth("head", "plate", p3, n3, (0, 1, 0), 9.0, 7.0, 1.6, 0.8, 4, embed=1.0, rot=45)
    # 冠 (頭頂の柱)
    zs = (-186.0, -176.0, -166.0, -156.0) if lod == 0 else (-180.0, -160.0)
    for i, z in enumerate(zs):
        for xx in ((-9.0, 0.0, 9.0) if lod == 0 else (0.0,)):
            if lod == 0 and xx == 0 and i % 2:
                continue
            top = head.top(z, xx)
            hh = 3.0 + 3.5 * hash1(i * 3 + int(xx), 24)
            fs += col6("head", "plate", (xx, top - 1.0, z), hh + 1.0, 3.0, sides=4 if lod else 6, taper=0.85, top_mat=None)
    return fs


def blocky_leg(front, lod, tag):
    """積んだ石の足: 太い柱 3 つを斜めに積む。足先は平たい台と爪"""
    bone = "leg_fl" if front else "leg_rl"
    fz = -1.0 if front else 1.0
    fs = []
    n = 6 if lod == 0 else 4
    pts = leg_path(front, 4)
    ras = [(17.0, 15.0), (13.5, 11.0), (10.5, 9.0)]
    for i in range(3 if lod < 2 else 1):
        a, b = pts[i], pts[i + 1]
        c = (a + b) / 2
        d = b - a
        rot = math.degrees(math.atan2(d[0], d[2]))
        ln = float(np.linalg.norm(d)) + 5.0
        wd = ras[i][0] * 1.7
        fs += slab(bone, "rock" if i != 1 else "plate", (c[0], c[1] - 3.0 - i * 1.0, c[2]), (wd, 20.0 - 3.0 * i, ln), taper=2.4, rot=rot, jit=0.0, tag=tag + i)
    end = pts[-1]
    fs += slab(bone, "rock", (end[0] - 1.0, 3.2, end[2] + fz * 3.0), (20.0, 6.4, 16.0), taper=2.0)
    if lod < 2:
        for k in range(3):
            base = V(end[0] - 1.0 + (k - 1) * 5.5, 3.0, end[2] + fz * 9.0)
            fs += spike(bone, "claw", base, V(0.1 * (k - 1), -0.08, fz), 7.0, 2.4, sides=4, rings=1, hint=(0, 1, 0), phase=45)
    return fs


def build(lod):
    h = hull(lod)
    fs = h.faces()
    jw = jaw(lod)
    fs += jw.faces()
    fs += teeth(h, jw, lod, count_scale=0.7, size=1.1, tag=5)
    fs += eyes(h, lod, sc=1.0)
    fs += nostrils(h, lod)
    fs += head_mask(h, lod)
    # 角: 太い柱 (銅の帯つき)、先が光る
    for sd in (1, -1):
        fs += horn(sd, length=28.0, r=5.6, segs=(3, 2, 2)[lod], sides=(4, 4, 3)[lod], dirv=(0.1, 0.8, 0.5), bend=(1.0, 3.0, 8.0))
        if lod == 0:
            for k, t in enumerate((0.25, 0.5)):
                c = V(26.0 * sd + 0.1 * 28 * t * sd, 36.0 + 28 * 0.8 * t * 0.9, -148.0 + 28 * 0.5 * t)
                fs += col6("head", "copper", (c[0], c[1] - 2.0, c[2]), 2.0, 6.2 - 3.0 * t, sides=4, taper=1.0, cap=False, hint=(0, 0, 1))
    fs += legs_all(lambda: blocky_leg(True, lod, 10), lambda: blocky_leg(False, lod, 20))
    fs += columns(h, lod)
    fs += ziggurat(lod)
    fs += forge_row(h, lod)
    fs += tail_pillars(h, lod)
    fs += smoke(lod, sizes=(1.0, 1.1, 1.2, 1.2))
    return fs


def heat(X, Y, Z, N):
    return np.clip(0.18 + 0.0 * X, 0, 1)


CTX = dict(nz=0.35, rock_lw=0.22, heat=heat, crack_w=0.0, seam_p=0.95, seam_mats=["rock", "plate", "cone", "brick", "copper"], streaks=4, streak_w=0.0,
           ash_dust=0.05, cone_c=VOLC_C, ash_cap_y=200.0, eye_z=EYE_Z, rock_base=0.46, crack_mats=["rock"],
           plate=np.array([(18, 17, 21), (30, 29, 34), (44, 43, 49), (60, 58, 66), (78, 76, 84)]))
OPT = dict(id="b", build=build, ctx=CTX, tex=(2.0, 1.0, 0.5))
