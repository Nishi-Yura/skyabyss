"""A 忠実な面取りの火山: 今の姿 (低く平たい火蜥蜴・背の火山・頭の後ろの角 2 本・目の縁・溶岩の継ぎ目) を、
斜めの面だけで刻んだ素直な低ポリ。玄武岩の板を重ね、板と板のあいだの継ぎ目が赤く光る。"""
from parts import *  # noqa

TMPL = [[(0, 0), (0.80, 0), (0.97, 0.14), (1, 0.50), (0.92, 0.82), (0.62, 0.97), (0, 1)],
        [(0, 0), (0.92, 0), (1, 0.58), (0.56, 1), (0, 1)],
        [(0, 0), (1, 0.3), (0.62, 1), (0, 1)]]


def hook(z, k, u, t, x, y, hw, yb, yt):
    return x, y


def hull(lod):
    h = Hull(TMPL[lod], KEYS, STATIONS[lod], hook=hook, jit=0.03 if lod == 0 else 0.02, jit_y=0.5 if lod == 0 else 0.3,
             bone_fn=bone_fn, mat_fn=std_mat("rock", "belly"), seed=1)
    return h


def plates(h, lod):
    """背と脇の玄武岩の板。左右は鏡写し"""
    fs = []
    if lod == 2:
        return fs
    N = 40 if lod == 0 else 11
    K = h.K
    for i in range(N):
        z = -110 + 215 * hash1(i, 81)
        if abs(z - VOLC_C[1]) < 40 and hash1(i, 82) < 0.8:
            continue
        sv = K - 1.0 - 3.2 * hash1(i, 83) ** 0.8          # 背の上から脇へ
        size = (4.8 + 5.0 * hash1(i, 85)) * (1.0 if lod == 0 else 1.5)
        bone = "ridge" if -96 <= z < 100 else ("body" if z < 108 and z >= -136 else ("tail" if z >= 108 else "head"))
        if sv > K - 1.5:
            sv = K - 1.0
        for side in ((1, -1) if sv < K - 1.0 else (1,)):
            p, n = h.at(z, sv, side)
            if lod == 0:
                fs += plinth(bone, "plate", p, n, (0, 1, 0), size, size * 0.8, 1.2 + 1.2 * hash1(i, 86), 0.72, 6, embed=0.9, rot=hash1(i, 87) * 60 * side)
            else:
                fs += plinth(bone, "plate", p, n, (0, 1, 0), size, size * 0.8, 2.0, 0.7, 4, embed=1.0, rot=45)
    return fs


def volcano(lod):
    cx, cz = VOLC_C
    prof = [(58, 56), (68, 49), (80, 41), (92, 33), (104, 26), (114, 20), (124, 15.5), (131, 13), (138, 12)]
    if lod == 1:
        prof = [(58, 56), (80, 41), (104, 26), (124, 15.5), (138, 12)]
    elif lod == 2:
        prof = [(58, 56), (96, 30), (138, 12)]
    n = (14, 10, 8)[lod]
    rings = [poly_ring(cx, cz, y, r, n, rot=11 * i + 7, jit=0.07 if lod == 0 else 0.04, tag=i) for i, (y, r) in enumerate(prof)]
    fs = ring_loft("ridge", "cone", rings)
    rim_o = poly_ring(cx, cz, 140.0, 12.0, n, rot=11 * (len(prof) - 1) + 7, jit=0.03, tag=len(prof) - 1)
    rim_i = poly_ring(cx, cz, 140.0, 8.0, n, rot=11 * (len(prof) - 1) + 7, jit=0.03, tag=len(prof) - 1)
    pool = poly_ring(cx, cz, 128.0, 6.0, n, rot=11 * (len(prof) - 1) + 7, tag=99)
    fs += ring_loft("ridge", "rimcrust", [rings[-1], rim_o])
    fs += ring_loft("ridge", "rimcrust", [rim_o, rim_i])
    if lod < 2:
        fs += ring_loft("ridge", "cone", [rim_i, pool], inward=True)
    fs += disc("ridge", "lava", pool if lod < 2 else rim_i)
    return fs


def dorsal(h, lod):
    """火山の後ろ、背骨ぞいの溶岩の流れと、並ぶ棘"""
    fs = []
    if lod == 2:
        return fs
    # 溶岩の流れ (細い光る帯)
    z0, z1 = 6.0, 98.0
    segs = 6 if lod == 0 else 3
    for i in range(segs):
        za = z0 + (z1 - z0) * i / segs
        zb = z0 + (z1 - z0) * (i + 1) / segs - 1.5
        cxx = -3.0 + 2.0 * math.sin(i * 1.3)
        top = h.top((za + zb) / 2, cxx)
        fs += slab("ridge", "lava", (cxx, top + 0.15, (za + zb) / 2), (6.5, 0.7, zb - za), taper=0.6)
    step = 11.0 if lod == 0 else 24.0
    z = 8.0
    i = 0
    while z < 104:
        top = h.top(z)
        hh = 6.0 + 8.0 * hash1(i, 91) ** 1.2
        side = 1 if i % 2 else -1
        for sd in (1, -1):
            xo = sd * 10.0
            fs += spike("ridge" if z < 100 else "tail", "plate", V(xo, h.top(z, xo) - 1.2, z), V(sd * 0.25, 1, 0.12), hh + 1.2, 3.2, sides=4, rings=1, hint=(0, 0, 1), phase=0)
        z += step
        i += 1
    return fs


def tail_spikes(h, lod):
    fs = []
    if lod > 0:
        return fs
    z = 116.0
    i = 0
    while z < 236:
        cx = h.key_at(z)[0]
        top = h.top(z)
        hh = max(2.5, 11.0 - (z - 116) * 0.06) * (0.8 + 0.4 * hash1(i, 5))
        fs += spike("tail", "plate", V(cx, top - 1.2, z), V(0, 1, 0.2), hh + 1.2, max(1.6, 3.0 - (z - 116) * 0.012), sides=4, rings=1, hint=(0, 0, 1), phase=0)
        z += 11.0
        i += 1
    return fs


def brow(head, lod):
    fs = []
    if lod > 0:
        return fs
    K = head.K
    for side in (1, -1):
        p, n = head.at(EYE_Z, K - 1.6, side)
        fs += plinth("head", "plate", p + V(0, 1.0, 0), n, (0, 1, 0), 13.0, 3.2, 3.0, 0.62, 6, embed=1.0)
        p2, n2 = head.at(-160.0, K - 2.0, side)
        fs += plinth("head", "plate", p2, n2, (0, 1, 0), 7.0, 5.0, 1.8, 0.7, 6, embed=0.8, rot=20)
    return fs


def head_crest(head, lod):
    """頭のうしろの板の冠と、頬の板"""
    fs = []
    if lod > 0:
        return fs
    K = head.K
    for i, z in enumerate((-170.0, -160.0, -150.0)):
        top = head.top(z)
        fs += slab("head", "plate", (0, top + 0.2, z), (12.0 - i * 1.5, 2.6, 7.0), taper=1.2, jit=0.6, tag=70 + i)
    for side in (1, -1):
        for j, z in enumerate((-186.0, -172.0)):
            p, n = head.at(z, 2.4, side)
            fs += plinth("head", "plate", p, n, (0, 1, 0), 8.0, 5.5, 1.6, 0.75, 6, embed=0.9, rot=15 * j)
    return fs


def leg_armor(lod):
    """肘・膝の板 (足の骨)"""
    fs = []
    if lod > 0:
        return fs
    for front in (True, False):
        bone = "leg_fl" if front else "leg_rl"
        path = leg_path(front, 5)
        el = path[2]
        fz = -1.0 if front else 1.0
        arm = slab(bone, "plate", (el[0] + 2.0, el[1] + 9.0, el[2]), (16.0, 5.0, 14.0), taper=3.0, jit=0.8, tag=90 + int(front))
        sh = slab(bone, "plate", (path[1][0], path[1][1] + 12.0, path[1][2]), (14.0, 4.0, 14.0), taper=2.5, jit=0.6, tag=95 + int(front))
        fs += arm + sh
    return fs


def build(lod):
    h = hull(lod)
    fs = h.faces()
    jw = jaw(lod)
    fs += jw.faces()
    fs += teeth(h, jw, lod)
    fs += eyes(h, lod)
    fs += nostrils(h, lod)
    fs += brow(h, lod)
    hs = (5, 4, 3)[lod]
    fs += horn(1, length=26.0, r=4.8, segs=(4, 3, 2)[lod], sides=(5, 4, 3)[lod])
    fs += horn(-1, length=26.0, r=4.8, segs=(4, 3, 2)[lod], sides=(5, 4, 3)[lod])
    lf = lambda: leg(True, lod, "rock", sides=(6, 5, 4)[lod], claw_n=3, tag=10, claw_len=9.0) + [f for f in leg_armor(lod) if f.bone == "leg_fl"]
    lr = lambda: leg(False, lod, "rock", sides=(6, 5, 4)[lod], claw_n=3, tag=20, claw_len=9.0) + [f for f in leg_armor(lod) if f.bone == "leg_rl"]
    fs += legs_all(lf, lr)
    fs += head_crest(h, lod)
    fs += plates(h, lod)
    fs += volcano(lod)
    fs += dorsal(h, lod)
    fs += tail_spikes(h, lod)
    fs += smoke(lod)
    return fs


def heat(X, Y, Z, N):
    d = np.hypot(X - VOLC_C[0], Z - VOLC_C[1])
    P = np.stack([X, Y, Z], -1)
    patch = vnoise(P * 0.035 + 9, 9)
    return np.clip(0.0 + 0.80 * np.exp(-d / 46.0) * np.clip((Y - 50.0) / 30.0, 0, 1) + 1.3 * np.clip(patch - 0.70, 0, 1) * np.clip((Y - 40.0) / 30.0, 0.0, 1), 0.0, 1.0)


def hook_vents(col, glow, e):
    """脇腹の溶岩の細い窓 (今のモデルの脇の光る点と同じ)"""
    X, Y, Z, N, mats = e["X"], e["Y"], e["Z"], e["N"], e["mats"]
    side = (np.abs(N[..., 0]) > 0.55) & np.isin(mats, ["rock", "plate"]) & (Y > 14) & (Z > -135) & (Z < 220)
    cz = np.floor(Z / 15.0)
    cy = np.floor((Y - 8.0) / 11.0)
    on = hash1(cz * 7 + cy * 13 + np.sign(X) * 5, 21) < 0.13
    slit = ((Z % 15.0) < 8.0) & (((Y - 8.0) % 11.0) < 2.6)
    m = side & on & slit
    lv = np.clip(0.45 + 0.25 * e["bay"] + 0.2 * hash1(np.floor(Z / 2) + np.floor(Y / 2) * 5, 3), 0, 0.99)
    col = np.where(m[..., None], e["ramp"](PAL["LAVA"], lv), col)
    return col, glow | m


CTX = dict(hook=hook_vents, nz=0.4, rock_lw=0.22, heat=heat, crack_w=1.2, crack_cell=16.0, seam_p=0.55, seam_mats=["rock", "plate", "cone"], streaks=7, streak_w=0.06,
           ash_dust=0.09, cone_c=VOLC_C, ash_cap_y=122.0, eye_z=EYE_Z, rock_base=0.48,
           crack_mats=["cone", "rock"])
OPT = dict(id="a", build=build, ctx=CTX, tex=(2.0, 1.0, 0.5))
