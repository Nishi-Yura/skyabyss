"""3 案で共有する部品 (胴の輪の列・顎・歯・目・角・足・煙・火山)。"""
from common import *  # noqa
from common import _mk  # noqa

# ---- 胴・頭・尾の断面 (z, 中心 x, 半幅, 底, 上)。今の geo の箱の外形 (頭 -208..-136、体 -136..108、尾 108..248) に合わせる
KEYS = [(-208, 0, 19, 7, 27), (-203, 0, 26, 8, 30), (-196, 0, 33, 8, 32), (-186, 0, 38, 8, 35), (-176, 0, 40, 8, 38),
        (-162, 0, 40, 8, 37), (-152, 0, 40, 8, 38), (-144, 0, 40, 6, 40), (-136, 0, 41, 4, 36),
        (-120, 0, 44, 4, 41), (-104, 0, 49, 4, 48), (-88, 0, 53, 4, 54), (-72, 0, 56, 4, 58), (-56, 0, 56, 4, 61),
        (-40, 0, 56, 4, 63), (40, 0, 56, 4, 63), (56, 0, 55, 4, 62), (72, 0, 53, 4, 60), (88, 0, 50, 4, 58),
        (104, 0, 45, 3, 51), (108, 0, 44, 2, 50), (120, -1, 40, 1, 46), (144, -3, 30, 0, 37), (168, -5, 24, 0, 28),
        (192, -8, 16, 0, 23), (216, -16, 12, 0, 16), (240, -22, 6, 0, 12), (248, -24, 2, 0, 5)]

ST_NEAR = ([-208, -203, -197, -190, -182, -174, -166, -158, -150, -144, -136]
           + [-128, -120, -110, -100, -90, -80, -70, -60, -50, -40, -28, -16, -4, 8, 20, 32, 44, 56, 68, 80, 92, 102, 108]
           + [118, 130, 142, 154, 166, 178, 190, 202, 214, 226, 238, 248])
ST_MID = [-208, -200, -188, -174, -160, -148, -136, -118, -96, -72, -46, -18, 10, 38, 66, 92, 108, 128, 152, 178, 204, 228, 248]
ST_FAR = [-208, -186, -160, -136, -96, -40, 20, 70, 108, 160, 210, 248]
STATIONS = [ST_NEAR, ST_MID, ST_FAR]

VOLC_C = (0.0, -46.0)        # 火山の中心 (x, z)
EYE_Z = -176.0


def bone_fn(zc, lo, hi):
    return "head" if zc < -136 else ("body" if zc < 108 else "tail")


def std_mat(body="rock", belly="belly"):
    def fn(zc, lo, hi, bone):
        if hi <= 1:
            return "mouth" if zc < -146 else belly
        return body
    return fn


# ---- 顎 (下あごの板)

JAW_KEYS = [(-204, 0, 21, 0, 7.6), (-196, 0, 30, 0, 7.6), (-186, 0, 36, 0, 7.6), (-176, 0, 38, 0, 7.6), (-160, 0, 38, 0, 7.6),
            (-150, 0, 37, 0, 7.6)]
JAW_ST = [[-204, -198, -190, -182, -174, -166, -158, -150], [-204, -190, -172, -150], [-204, -176, -150]]
JAW_T = [[(0, 0), (0.9, 0), (1.0, 0.4), (0.92, 1.0), (0, 1.0)], [(0, 0), (1.0, 0.3), (0.9, 1.0), (0, 1.0)], [(0, 0), (1.0, 0), (1.0, 1.0), (0, 1.0)]]


def jaw(lod, jit=0.015, seed=3):
    h = Hull(JAW_T[lod], JAW_KEYS, JAW_ST[lod], jit=jit, jit_y=0.0,
             bone_fn=lambda zc, lo, hi: "jaw",
             mat_fn=lambda zc, lo, hi, bone: ("belly" if hi <= 1 else ("tongue" if lo >= h.K - 2 else "rock")), seed=seed)
    return h


def teeth(head: Hull, jaw_h: Hull, lod, count_scale=1.0, size=1.0, tag=0):
    """上あご (頭の骨) は下向き、下あご (顎の骨) は上向きに、口の縁に沿った牙"""
    fs = []
    if lod >= 2:
        return fs
    step = 10.0 / count_scale if lod == 0 else 22.0
    z = -198.0
    i = 0
    while z <= -150:
        for side in (1, -1):
            hh = (4.2 + 2.2 * hash1(i * 2, 41 + tag)) * size
            # 上
            p, n = head.at(z, 1.0, side)
            fs += spike("head", "tooth", p + V(0, 0.4, 0), V(0, -1, 0), hh, 1.3 * size, sides=3, rings=1, hint=(0, 0, 1), phase=90)
            # 下 (半歩ずらす)
            zz = z + step * 0.5
            if zz < -150:
                pj, nj = jaw_h.at(zz, jaw_h.K - 2, side)
                hh2 = (3.4 + 2.0 * hash1(i * 2, 43 + tag)) * size
                fs += spike("jaw", "tooth", pj - V(0, 0.6, 0), V(0, 1, 0), hh2, 1.2 * size, sides=3, rings=1, hint=(0, 0, 1), phase=90)
        z += step
        i += 1
    if lod == 0:
        for xx in (-9, -3, 3, 9):
            fs += spike("head", "tooth", V(xx, 8.4, -206.0 + abs(xx) * 0.25), V(0, -0.7, -0.7), 3.6 * size, 1.2 * size, sides=3, rings=1, hint=(1, 0, 0), phase=90)
    return fs


def eyes(head: Hull, lod, style="rim", sc=1.0):
    """目 (光る琥珀 + 縦の瞳) と、まわりの光る縁。左右対称"""
    fs = []
    if lod == 2:
        return fs
    K = head.K
    for side in (1, -1):
        p, n = head.at(EYE_Z, K - 2.0, side)
        up = (0, 1, 0)
        if lod == 0:
            # 目の窪みの黒い台、光る縁の輪、琥珀の目
            fs += plinth("head", "dark", p, n, up, 9.0 * sc, 6.4 * sc, 1.6, 0.8, 8, embed=1.2, rot=22.5)
            fs += plinth("head", "rim", p + n * 1.2, n, up, 7.6 * sc, 5.0 * sc, 0.7, 0.9, 8, embed=0.2, rot=22.5)
            fs += plinth("head", "eye", p + n * 1.8, n, up, 5.2 * sc, 3.3 * sc, 0.6, 0.92, 8, embed=0.2, rot=22.5)
        else:
            fs += plinth("head", "dark", p, n, up, 8.0 * sc, 5.6 * sc, 1.6, 0.8, 6, embed=1.0)
            fs += plinth("head", "eye", p + n * 1.4, n, up, 5.4 * sc, 3.4 * sc, 0.8, 0.9, 6, embed=0.2)
    return fs


def nostrils(head: Hull, lod, mat="dark"):
    fs = []
    if lod > 0:
        return fs
    K = head.K
    for side in (1, -1):
        p, n = head.at(-199.0, K - 2.2, side)
        fs += plinth("head", mat, p, n, (0, 1, 0), 2.2, 1.6, 1.0, 0.7, 5, embed=0.6)
    return fs


def horn(side, bone="head", mat="horn", tip="hornglow", length=24.0, r=4.6, base=(26.0, 36.0, -148.0), dirv=(0.18, 0.55, 0.82),
         bend=(2.0, 8.0, 10.0), segs=4, sides=5, glow_frac=0.42):
    """頭の後ろの角。根元から後ろ上へ、先が光る (先の 1 区間だけ別の材料)"""
    b = V(base[0] * side, base[1], base[2])
    d = V(dirv[0] * side, dirv[1], dirv[2])
    bd = V(bend[0] * side, bend[1], bend[2])
    path, ras = [], []
    for i in range(segs + 1):
        t = i / segs
        path.append(b + nrm(d) * length * t + bd * t * t)
        ras.append(max(0.0, r * (1 - t) ** 0.6) if i < segs else 0.0)
    ras[0] = r
    fs = tube(bone, mat, path, ras, sides=sides, hint=(0, 0, 1), phase=45, caps=(True, False))
    # 先の 2 区間を光らせる (面の中心が先端に近いもの)
    tipz = path[-1]
    for f in fs:
        if np.linalg.norm(f.verts.mean(0) - tipz) < length * glow_frac:
            f.mat = tip
    return fs


# ---- 足

def leg_path(front, n=5):
    if front:
        keys = [(40, 30, -78), (60, 26, -84), (82, 20, -92), (95, 11, -103), (100, 5, -112)]
    else:
        keys = [(40, 30, 86), (60, 26, 92), (82, 18, 100), (95, 10, 111), (99, 5, 119)]
    keys = [V(*k) for k in keys]
    if n == len(keys):
        return keys
    out = []
    for i in range(n):
        t = i / (n - 1) * (len(keys) - 1)
        k = min(int(t), len(keys) - 2)
        out.append(keys[k] * (1 - (t - k)) + keys[k + 1] * (t - k))
    return out


def leg(front, lod, mat="rock", sides=6, radii=None, claw_n=3, foot=True, claw_len=7.0, toe_mat=None, tag=0):
    """+X 側の足 (近: 6 角、中: 4〜5 角、遠: 3 角)。leg_fl / leg_rl"""
    bone = "leg_fl" if front else "leg_rl"
    n = (5, 4, 3)[lod]
    path = leg_path(front, n)
    base_r = radii or (19.0, 16.0, 13.0, 11.0, 10.0)
    ras = [base_r[min(int(round(i / (n - 1) * (len(base_r) - 1))), len(base_r) - 1)] for i in range(n)]
    rb = [r * 0.85 for r in ras]
    fs = tube(bone, mat, path, ras, rb, sides=sides, hint=(0, 1, 0), phase=360.0 / sides / 2 if sides % 2 == 0 else 90, caps=(False, False))
    end = path[-1]
    if foot:
        fz = -1.0 if front else 1.0
        fs += slab(bone, toe_mat or mat, (end[0] - 1.0, 3.2, end[2] + fz * 3.0), (20.0, 6.4, 16.0), taper=2.0, rot=(-18 if front else 18) * 0.0, jit=0.8 if lod == 0 else 0, tag=tag + (1 if front else 2))
        if lod < 2:
            for k in range(claw_n):
                a = (k - (claw_n - 1) / 2) * 0.55
                dx, dz = math.sin(a) * 0.8 + 0.25, fz * math.cos(a)
                base = V(end[0] - 1.0 + (k - (claw_n - 1) / 2) * 5.5, 3.0, end[2] + fz * 9.0)
                fs += spike(bone, "claw", base, V(dx * 0.4, -0.08, dz), claw_len, 2.2, sides=3, rings=1, hint=(0, 1, 0), phase=90)
    return fs


def legs_all(front_fn, rear_fn):
    fl, rl = front_fn(), rear_fn()
    return fl + mirror_x(fl, "leg_fr") + rl + mirror_x(rl, "leg_rr")


# ---- 煙 (骨 smoke_0..3。ridge の子。アニメで揺れる)

SMOKE_C = [((-2.2, 140.0, -26.0), 8.5), ((-4.0, 153.0, -21.0), 9.5), ((0.8, 167.0, -16.0), 10.5), ((-5.0, 181.0, -11.0), 10.5)]


def smoke(lod, sizes=None, mat="smoke"):
    fs = []
    for i, (c, r) in enumerate(SMOKE_C):
        r = r * (sizes[i] if sizes else 1.0)
        if lod == 2 and i % 2 == 1:
            continue
        if lod == 0:
            def dfm(p, i=i):
                return p * (1 + 0.28 * (hash1(int(abs(p[0]) * 3 + abs(p[2]) * 5 + p[1] * 7) + i * 13, 5) - 0.5))
            fs += ellipsoid(f"smoke_{i}", mat, c, (r, r * 0.85, r * 0.9), segs=7, rings=2, deform=dfm, phase=20 * i)
        else:
            fs += blob(f"smoke_{i}", mat, c, r, tag=i + 11, jit=0.22, flat=0.85)
    return fs


# ---- 火山 (ridge の骨)

def ring_loft(bone, mat, rings, inward=False, mats=None):
    """同じ点数の輪をつなぐ。inward=True は面を軸の側 (内側) に向ける (火口の内壁)"""
    rings = [np.array(r, float) for r in rings]
    cen = [r.mean(0) for r in rings]
    k = len(rings[0])
    fs = []
    for i in range(len(rings) - 1):
        for j in range(k):
            quad = [rings[i][j], rings[i][(j + 1) % k], rings[i + 1][(j + 1) % k], rings[i + 1][j]]
            fc = np.mean(quad, 0)
            axis = (cen[i] + cen[i + 1]) / 2
            ref = axis if not inward else fc + (fc - axis) * 1.0 + V(0, 0, 0)
            f = _mk(quad, bone, mats[i] if mats else mat, ref)
            if f is not None:
                fs.append(f)
    return fs


def poly_ring(cx, cz, y, r, n, rot=0.0, jit=0.0, tag=0, rz=None, lean=(0.0, 0.0)):
    rz = r if rz is None else rz
    pts = []
    for k in range(n):
        a = math.radians(rot + 360.0 * k / n)
        rj = 1 + jit * (hash1(tag * 31 + k * 7, 5) - 0.5) * 2
        pts.append(V(cx + lean[0] + math.sin(a) * r * rj, y, cz + lean[1] + math.cos(a) * rz * rj))
    return pts


def disc(bone, mat, ring_pts, up=True):
    pts = list(ring_pts)
    c = np.mean(pts, 0)
    ref = c - V(0, 5, 0) if up else c + V(0, 5, 0)
    f = _mk(pts, bone, mat, ref)
    return [f] if f else []
