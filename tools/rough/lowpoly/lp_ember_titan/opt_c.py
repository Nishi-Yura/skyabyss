"""C 皮が岩になる火蜥蜴: 生きた火蜥蜴の体 (丸い腹・喉のひだ・関節のある足と長い指・まぶたのある目) の背から、
冷えかけた玄武岩の殻が広がって皮を覆っていく案。割れ目の下に溶岩が脈打ち、背の火山は殻を破ってふくらむ傷口のよう。"""
from parts import *  # noqa

TMPL = [[(0, 0), (0.52, 0.0), (0.82, 0.07), (0.97, 0.24), (1.0, 0.46), (0.93, 0.68), (0.76, 0.87), (0.45, 0.98), (0, 1.0)],
        [(0, 0), (0.7, 0.0), (1.0, 0.36), (0.9, 0.78), (0.45, 1.0), (0, 1.0)],
        [(0, 0), (1.0, 0.25), (0.7, 0.9), (0, 1.0)]]


def hook(z, k, u, t, x, y, hw, yb, yt):
    # 頬のふくらみ (顎のつけね)、肩と腰のもりあがり、腹のたるみ
    if -186 < z < -150 and 0.2 < t < 0.8:
        x *= 1.0 + 0.07 * math.sin(math.pi * (z + 186) / 36.0)
    if t > 0.6 and -130 < z < -70:
        y += 3.0 * math.sin(math.pi * (z + 130) / 60.0) * (t - 0.6) / 0.4
    if t > 0.6 and 50 < z < 100:
        y += 2.5 * math.sin(math.pi * (z - 50) / 50.0) * (t - 0.6) / 0.4
    if t < 0.05 and -90 < z < 90 and u < 0.9:
        y -= 2.0 * math.sin(math.pi * (z + 90) / 180.0) * (1 - u / 0.9) * 0.0
    return x, y


def hull(lod):
    return Hull(TMPL[lod], KEYS, STATIONS[lod], hook=hook, jit=0.035 if lod == 0 else 0.02, jit_y=0.9 if lod == 0 else 0.4,
                bone_fn=bone_fn, mat_fn=skin_mat, seed=4)


def skin_mat(zc, lo, hi, bone):
    if hi <= 1:
        return "mouth" if zc < -146 else "belly"
    if zc > 238:
        return "lava"          # 尾の先は熾のように赤熱している
    return "skin"


def crust_plates(h, lod):
    """背から広がる殻の板 (上ほど密で大きく、脇へ薄れる)。重なって傾く"""
    fs = []
    if lod == 2:
        return fs
    K = h.K
    N = 80 if lod == 0 else 22
    for i in range(N):
        z = -140 + 270 * hash1(i, 81)
        if abs(z - VOLC_C[1]) < 30 and hash1(i, 82) < 0.7:
            continue
        # 背の中心に近いほどたくさん
        sv = K - 1.0 - (K - 2.6) * (hash1(i, 83) ** 1.7)
        zc = max(-196, min(246, z))
        t_up = (sv / (K - 1.0))
        size = (3.2 + 4.8 * hash1(i, 85)) * (0.65 + 0.7 * t_up) * (1.0 if lod == 0 else 1.5)
        bone = "ridge" if -96 <= zc < 100 else ("body" if -136 <= zc < 108 else ("tail" if zc >= 108 else "head"))
        hh = 1.0 + 1.6 * hash1(i, 86)
        if zc < -120 or zc > 112:
            size *= 0.75
        if sv > K - 1.5:
            sv = K - 1.0
        for side in ((1, -1) if sv < K - 1.0 else (1,)):
            p, n = h.at(zc, sv, side)
            if lod == 0:
                fs += plinth(bone, "plate", p, n, (0, 1, 0), size, size * 0.8, hh, 0.66, 6, embed=0.9, rot=hash1(i, 87) * 60 * side)
            else:
                fs += plinth(bone, "plate", p, n, (0, 1, 0), size, size * 0.8, 2.0, 0.7, 4, embed=1.0, rot=45)
    return fs


def mound(lod):
    """火山: 殻を破って膨らむ傷口。ゆがんだ円錐、根元は皮のしわ、縁はたれた溶岩"""
    cx, cz = VOLC_C
    prof = [(56, 58), (64, 53), (74, 46), (86, 38), (98, 30), (110, 23), (121, 17), (130, 13.5), (137, 12.5)]
    if lod == 1:
        prof = [(56, 58), (74, 46), (98, 30), (121, 17), (137, 12.5)]
    elif lod == 2:
        prof = [(56, 58), (92, 33), (137, 12.5)]
    n = (13, 9, 7)[lod]
    rings = []
    for i, (y, r) in enumerate(prof):
        bulge = 1.0 + 0.08 * math.sin(i * 1.7)
        lean = (0.0, -2.0 * (y - 56) / 80.0)
        rz = r * (1.0 + 0.1 * math.sin(i * 0.9 + 1.0))
        rings.append(poly_ring(cx, cz, y, r * bulge, n, rot=17 * i + 5, jit=0.12 if lod == 0 else 0.06, tag=i + 30, rz=rz, lean=lean))
    mats = ["skin" if (i < 2) else "cone" for i in range(len(rings) - 1)]
    if lod == 2:
        mats = ["cone"] * (len(rings) - 1)
    fs = ring_loft("ridge", "cone", rings, mats=mats)
    last = len(prof) - 1
    lean = (0.0, -2.0 * (137 - 56) / 80.0)
    rim_o = poly_ring(cx, cz, 139.0, 12.6, n, rot=17 * last + 5, jit=0.10, tag=last + 30, lean=lean)
    rim_i = poly_ring(cx, cz, 138.0, 8.2, n, rot=17 * last + 5, jit=0.10, tag=last + 30, lean=lean)
    pool = poly_ring(cx, cz, 127.0, 6.0, n, rot=17 * last + 5, tag=99, lean=lean)
    fs += ring_loft("ridge", "rimcrust", [rings[-1], rim_o])
    fs += ring_loft("ridge", "rimcrust", [rim_o, rim_i])
    if lod < 2:
        fs += ring_loft("ridge", "cone", [rim_i, pool], inward=True)
    fs += disc("ridge", "lava", pool if lod < 2 else rim_i)
    # たれた溶岩 (縁から垂れる光る舌)
    if lod < 2:
        for k in range(5 if lod == 0 else 3):
            a = math.radians(30 + 73 * k)
            px = cx + lean[0] + math.sin(a) * 12.0
            pz = cz + lean[1] + math.cos(a) * 12.0
            ln = 9.0 + 9.0 * hash1(k, 31)
            fs += spike("ridge", "lava", V(px, 138.0, pz), V(math.sin(a) * 0.55, -1.0, math.cos(a) * 0.55), ln, 2.4, sides=3, rings=1, hint=(0, 1, 0), phase=30)
    return fs


def mound_plates(lod):
    """火山の斜面に重なる殻の板"""
    fs = []
    if lod > 0:
        return fs
    cx, cz = VOLC_C
    for i in range(34):
        a = hash1(i, 41) * 2 * math.pi
        y = 66 + 62 * hash1(i, 42) ** 0.9
        r = 58 + (13 - 58) * (y - 56) / 81.0 - 1.0
        lean = (0.0, -2.0 * (y - 56) / 80.0)
        px, pz = cx + lean[0] + math.sin(a) * r, cz + lean[1] + math.cos(a) * r
        nrm_ = V(math.sin(a), 0.5, math.cos(a))
        size = 4.0 + 4.0 * hash1(i, 43)
        fs += plinth("ridge", "plate", V(px, y, pz), nrm_, (0, 1, 0), size, size * 0.8, 1.4, 0.7, 6, embed=1.0, rot=hash1(i, 44) * 60)
    return fs


def back_spikes(h, lod):
    """背骨の殻から出る棘 (大きさのふぞろい)。尾へ向けて小さく"""
    fs = []
    if lod == 2:
        return fs
    step = 8.0 if lod == 0 else 16.0
    z = -128.0
    i = 0
    while z < 238:
        if abs(z - VOLC_C[1]) < 34:
            z += step
            continue
        zc = z
        cx = h.key_at(zc)[0]
        top = h.top(zc)
        f = 1.0 if zc < 110 else max(0.3, 1.0 - (zc - 110) / 140.0)
        hh = (3.0 + 8.0 * hash1(i, 92) ** 1.5) * f
        bone = "ridge" if -96 <= zc < 100 else ("body" if -136 <= zc < 108 else ("tail" if zc >= 108 else "head"))
        fs += spike(bone, "plate", V(cx, top - 1.4, zc), V(0.1 * (hash1(i, 93) - .5), 1, 0.35), hh + 1.4, (2.2 + 0.8 * hash1(i, 94)) * max(f, 0.55),
                    sides=4, rings=1, hint=(0, 0, 1), phase=0)
        z += step
        i += 1
    return fs


def lids_and_face(head, lod):
    fs = []
    K = head.K
    if lod == 2:
        return fs
    for side in (1, -1):
        p, n = head.at(EYE_Z, K - 2.0, side)
        if lod == 0:
            # まぶた (皮の輪) が目を丸くふちどる
            fs += plinth("head", "skin", p + n * 0.2, n, (0, 1, 0), 10.5, 7.4, 2.2, 0.75, 8, embed=1.0, rot=22.5)
            fs += plinth("head", "rim", p + n * 1.5, n, (0, 1, 0), 8.0, 5.2, 0.6, 0.9, 8, embed=0.2, rot=22.5)
        # 眉の盛り上がり (殻の小さな板)
        pb, nb = head.at(EYE_Z, K - 1.4, side)
        fs += plinth("head", "plate", pb + V(0, 0.8, 0), nb, (0, 1, 0), 11.0, 3.0, 2.6, 0.62, 6, embed=1.0)
        if lod == 0:
            # 頬のとげ (あごのつけね)
            for j, z in enumerate((-168.0, -158.0, -150.0)):
                pc, nc = head.at(z, 2.2, side)
                fs += spike("head", "horn", pc - nc * 0.6, nc + V(0, 0.15, 0.6), 7.0 - j * 1.4, 2.4 - j * 0.3, sides=4, rings=1, hint=(0, 1, 0), phase=45)
    # 喉のひだ (顎のうしろの腹の筋)
    return fs


def toes_leg(front, lod, tag):
    """関節のある足: 上腕のふくらみ、ひじ、手首、三本の長い指と爪"""
    bone = "leg_fl" if front else "leg_rl"
    fz = -1.0 if front else 1.0
    n = (5, 4, 3)[lod]
    path = leg_path(front, n)
    radii = (21.0, 17.5, 14.0, 11.5, 10.0)
    ras = [radii[min(int(round(i / (n - 1) * 4)), 4)] for i in range(n)]
    sides = (7, 5, 4)[lod]
    fs = tube(bone, "skin", path, ras, [r * 0.88 for r in ras], sides=sides, hint=(0, 1, 0), phase=22, caps=(False, False))
    end = path[-1]
    if lod == 0:
        # ひじのこぶ
        el = path[2]
        fs += blob(bone, "plate", (el[0] + 4.0, el[1] + 7.0, el[2] - fz * 3.0), 6.0, tag=tag + 5, jit=0.3, flat=0.7)
    # 手のひら + 指
    fs += slab(bone, "skin", (end[0] - 1.0, 3.0, end[2] + fz * 3.0), (17.0, 6.0, 12.0), taper=2.2, jit=0.6 if lod == 0 else 0, tag=tag + 1)
    if lod < 2:
        for k in range(3):
            a = (k - 1) * 0.5
            base = V(end[0] - 1.0 + (k - 1) * 5.2, 3.0, end[2] + fz * 7.0)
            d = V(math.sin(a) * 0.6, -0.05, fz * math.cos(a))
            toe_len = 9.0 if k == 1 else 7.5
            if lod == 0:
                fs += tube(bone, "skin", [base, base + nrm(d) * toe_len * 0.55, base + nrm(d) * toe_len], [3.2, 2.7, 2.0], sides=4, hint=(0, 1, 0), phase=45, caps=(False, False))
                fs += spike(bone, "claw", base + nrm(d) * toe_len, nrm(d) + V(0, -0.15, 0), 5.0, 2.0, sides=3, rings=1, hint=(0, 1, 0), phase=90)
            else:
                fs += spike(bone, "claw", base, nrm(d), toe_len + 4, 2.8, sides=3, rings=1, hint=(0, 1, 0), phase=90)
    return fs


def build(lod):
    h = hull(lod)
    fs = h.faces()
    jw = jaw(lod, jit=0.03, seed=8)
    for f in jw.faces():
        if f.mat == "rock":
            f.mat = "skin"
        fs.append(f)
    fs += teeth(h, jw, lod, count_scale=0.75, size=0.95, tag=11)
    fs += eyes(h, lod, sc=0.9) if lod > 0 else []
    fs += nostrils(h, lod, mat="plate")
    fs += lids_and_face(h, lod)
    # 目 (lod 0 は lids_and_face の縁の上に、琥珀の目を足す)
    if lod == 0:
        K = h.K
        for side in (1, -1):
            p, n = h.at(EYE_Z, K - 2.0, side)
            fs += plinth("head", "eye", p + n * 2.0, n, (0, 1, 0), 5.4, 3.5, 0.6, 0.92, 8, embed=0.2, rot=22.5)
    for sd in (1, -1):
        # 角は後ろへ反る (曲がった骨の角)。先が光る
        fs += horn(sd, length=30.0, r=4.6, segs=(5, 3, 2)[lod], sides=(5, 4, 3)[lod], dirv=(0.28, 0.5, 0.82), bend=(4.0, 10.0, 12.0))
    fs += legs_all(lambda: toes_leg(True, lod, 10), lambda: toes_leg(False, lod, 20))
    fs += crust_plates(h, lod)
    fs += mound(lod)
    fs += mound_plates(lod)
    fs += back_spikes(h, lod)
    fs += smoke(lod, sizes=(1.0, 1.1, 1.2, 1.25))
    return fs


def heat(X, Y, Z, N):
    d = np.hypot(X - VOLC_C[0], Z - VOLC_C[1])
    P = np.stack([X, Y, Z], -1)
    patch = vnoise(P * 0.04 + 5, 11)
    tail = np.clip((Z - 170.0) / 70.0, 0, 1)                    # 尾の先は熾のように熱い
    belly = np.clip((14.0 - Y) / 14.0, 0, 1) * 0.35             # 腹の脈
    spine = np.clip(1.0 - np.abs(X) / 30.0, 0, 1) * np.clip((Y - 40.0) / 24.0, 0, 1) * 0.2
    return np.clip(0.06 + 0.62 * np.exp(-d / 70.0) + 0.7 * tail + belly + spine + 1.2 * np.clip(patch - 0.62, 0, 1), 0, 1)


def crust_hook(col, e):
    """皮から殻へ: 背 (高い所・上向き) ほど殻の岩、脇や腹は皮。境目は雲のようにゆらぐ"""
    Y, N, mats = e["Y"], e["N"], e["mats"]
    r = np.clip((Y - 30.0) / 34.0 + 1.1 * (e["n_mid"] - 0.5) + 0.7 * (e["n_lo"] - 0.5) + 0.4 * (N[..., 1] - 0.4), 0, 1)
    rock = r > 0.60
    trans = (r > 0.46) & (r <= 0.60)
    mix = trans & (e["bay"] + 0.5 < (r - 0.46) / 0.14)
    ro = e["pick"](e["PAL"]["ROCK"], e["shaded"](e["PAL"]["ROCK"], 0.42, 0.22, 0.08) - (e["n_hi"] > 0.80))
    sk = np.isin(mats, ["skin"])
    return np.where((sk & (rock | mix))[..., None], ro, col)


CTX = dict(nz=0.6, rock_lw=0.24, heat=heat, crack_w=0.95, crack_cell=13.0, seam_p=0.5, seam_mats=["plate", "cone"], streaks=9, streak_w=0.05, ash_dust=0.09, cone_c=VOLC_C,
           ash_cap_y=128.0, eye_z=EYE_Z, crack_mats=["skin", "cone"], crust_hook=crust_hook,
           skin=np.array([(58, 48, 46), (80, 68, 63), (104, 90, 82), (128, 112, 102), (152, 134, 122)]),
           plate=np.array([(14, 12, 14), (24, 20, 21), (36, 30, 30), (50, 42, 40), (66, 56, 52)]),
           cone=np.array([(24, 19, 19), (38, 30, 28), (54, 44, 40), (72, 60, 54)]))
OPT = dict(id="c", build=build, ctx=CTX, tex=(2.0, 1.0, 0.5))
