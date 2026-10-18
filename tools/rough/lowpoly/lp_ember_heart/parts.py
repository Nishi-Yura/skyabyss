"""余燼の心臓: 3 案で使う形の部品 (板割れの殻・心臓・核・血管・垂れ・火の粉)。"""
from __future__ import annotations

from functools import lru_cache

from scipy.spatial import SphericalVoronoi as SphereVoronoi

from common import *  # noqa


# ------------------------------------------------------------------ 球面のボロノイ

@lru_cache(maxsize=None)
def voronoi(n, seed, jit=0.22, relax=5, k=0.65):
    rng = np.random.default_rng(seed)
    i = np.arange(n) + 0.5
    phi = np.arccos(1 - 2 * i / n)
    th = math.pi * (1 + 5 ** 0.5) * i
    pts = np.stack([np.cos(th) * np.sin(phi), np.cos(phi), np.sin(th) * np.sin(phi)], 1)
    pts = pts + rng.normal(size=pts.shape) * jit
    pts /= np.linalg.norm(pts, axis=1, keepdims=True)
    for _ in range(relax):
        sv = SphereVoronoi(pts, 1.0)
        sv.sort_vertices_of_regions()
        new = []
        for p, reg in zip(pts, sv.regions):
            c = sv.vertices[reg].mean(0)
            new.append(p * (1 - k) + c / np.linalg.norm(c) * k)
        pts = np.array(new)
        pts /= np.linalg.norm(pts, axis=1, keepdims=True)
    sv = SphereVoronoi(pts, 1.0)
    sv.sort_vertices_of_regions()
    return pts, [sv.vertices[r].copy() for r in sv.regions]


def rr(d):
    return 1.0 / math.sqrt(float(((d / RAD) ** 2).sum()))


def sp(d, hu):
    d = nrm(d)
    return CEN + d * (rr(d) + hu)


def inset(v, s, delta):
    """単位球上の v を、s の向きへ表面に沿って delta (単位) 寄せる。"""
    ang = math.acos(max(-1.0, min(1.0, float(np.dot(v, s)))))
    step = min(delta / 10.0, ang * 0.85)
    t = s - v * np.dot(v, s)
    t = nrm(t)
    return nrm(v * math.cos(step) + t * math.sin(step))


def tangent(d, a):
    """d に垂直な、角度 a の単位ベクトル。"""
    h = np.array([0, 1.0, 0]) if abs(d[1]) < 0.9 else np.array([1.0, 0, 0])
    u = nrm(np.cross(d, h))
    w = np.cross(d, u)
    return u * math.cos(a) + w * math.sin(a)


# ------------------------------------------------------------------ 板割れの殻

def shell(phase, sh, bone_plate, bone_floor, seeds_cfg, select, salt=1):
    """sh: 板の寸法 (floor_depth は ctx と同じ値)。select(i, dir, rnd) -> None (作らない) / dict (その板の指定)。"""
    pts, regions = voronoi(*seeds_cfg)
    Df = sh["floor_depth"]
    F = []
    for i, (s, vs) in enumerate(zip(pts, regions)):
        rnd = float(hash1(i, salt))
        pick_ = select(i, s, rnd)
        if pick_ is None:
            continue
        o = dict(sh)
        o.update(pick_)
        r1, r2, r3 = (float(hash1(i * 3 + j, salt + 7)) for j in range(3))
        top = o["top"] + (r1 - 0.5) * 2 * o["jit_h"]
        dome = o["dome"] * (0.6 + 0.8 * r2)
        g, ch = o["gap"], o["chamfer"]
        tdir = tangent(s, r3 * 6.283)
        tilt = o.get("tilt", 0.0)
        ao = o.get("apex_off", 0.0)
        ax = nrm(s + tangent(s, r2 * 6.283) * ao * 0.1)
        k = len(vs)
        O, Mr, T = [], [], []
        for j, v in enumerate(vs):
            v = nrm(v)
            tl = tilt * float(np.dot(v - s, tdir)) * 10.0
            jv = (float(hash1(i * 17 + j, salt + 3)) - 0.5) * o.get("jit_v", 0.5)
            O.append(sp(inset(v, s, g / 2), -Df + tl * 0.3))
            Mr.append(sp(inset(v, s, g / 2 + ch * 0.55), -Df + (top + Df) * 0.55 + tl * 0.7 + jv * 0.5))
            T.append(sp(inset(v, s, g / 2 + ch), top + tl + jv))
        A = sp(ax, top + dome)
        if o.get("flake") and r1 > o["flake"][0]:
            A = None
        for j in range(k):
            j2 = (j + 1) % k
            F += poly(bone_plate, "wall", [O[j], O[j2], Mr[j2], Mr[j]], CEN)
            F += poly(bone_plate, "wall", [Mr[j], Mr[j2], T[j2], T[j]], CEN)
            if A is not None:
                F += poly(bone_plate, "plate", [T[j], T[j2], A], CEN)
        if A is None:
            F += poly(bone_plate, "plate", T, CEN)
        # 割れ目の床 (細胞の全体、基準面より Df 下)
        F += poly(bone_floor, "floor", [sp(nrm(v), -Df) for v in vs], CEN)
        # 結晶の棘 (継ぎ目の角)
        if o.get("spikes"):
            ln, rd, pr = o["spikes"]
            for j, v in enumerate(vs):
                if float(hash1(i * 11 + j, salt + 5)) < pr:
                    b0 = T[j]
                    d = nrm(b0 - CEN)
                    F += spike(bone_plate, "plate", b0 - d * 0.4, d + tangent(d, j * 1.7) * 0.25, ln * (0.7 + 0.6 * float(hash1(i + j, 4))),
                               rd, sides=3, rings=1, hint=(0, 1, 0))
    return F


# ------------------------------------------------------------------ 心臓・核・血管

def heart_ball(bone, mat, segs=10, rings=4, jit=0.06, notch=1.2):
    c = HC
    rx, ry, rz = HR

    def deform(p):
        x, y, z = p
        k = math.sin(x * 2.9 + y * 1.7 + 3) * math.cos(z * 2.3 - y * 1.1 + 6) * jit
        x, y, z = x * (1 + k), y * (1 + k), z * (1 + k)
        if y > 0:
            y -= notch * math.exp(-(x / 2.4) ** 2) * smooth(0.5, 4.0, y)
        else:
            q = -y / ry
            x *= 1 - 0.12 * q
        return V(x, y, z)

    return ellipsoid(bone, mat, c, (rx, ry, rz), segs=segs, rings=rings, deform=deform, phase=0.0)


def hsurf(th, ph, lift=0.0):
    """核の極 (-Z) からの角度 th、方位 ph の心臓の表面。"""
    t, f = rad(th), rad(ph)
    d = V(math.sin(t) * math.cos(f), math.sin(t) * math.sin(f), -math.cos(t))
    p = d * HR
    return HC + p + nrm(p) * lift


def veins(bone, mat, n, r=0.38, lift=0.45, seed=1, th0=20):
    F = []
    for k in range(n):
        ph = 25 + 360.0 / n * k + (float(hash1(k, seed)) - 0.5) * 28
        pts = [hsurf(th, ph + 14 * math.sin(j * 1.2 + k + seed), lift) for j, th in enumerate((th0, 42, 66, 92, 118, 140))]
        F += tube(bone, mat, pts, r, r, sides=3, hint=(0, 0, 1), phase=90)
    return F


def core_sphere(phase, segs=8, rings=3, k=1.0):
    return ellipsoid("core_eye", "core", CORE_C[phase], (CORE_R[phase] * k,) * 3, segs=segs, rings=rings, phase=22.5)


def core_ring(phase, n=6, r=3.5, w=0.75, prongs=4, plen=2.2, gaps=()):
    """核を囲む面取りの輪 (段階 2 は大きく割れずに、段階 3 は欠ける)。"""
    c = CORE_C[phase] + V(0, 0, -0.3)
    F = []
    pts = [c + V(math.sin(rad(360 / n * (i + 0.5))) * r, math.cos(rad(360 / n * (i + 0.5))) * r, 0) for i in range(n)]
    for i in range(n):
        if i in gaps:
            continue
        seg = [pts[i], (pts[i] + pts[(i + 1) % n]) / 2 * 1.0 + (c - (pts[i] + pts[(i + 1) % n]) / 2) * 0.0, pts[(i + 1) % n]]
        F += tube("core_ring", "plate", seg, w, w * 0.8, sides=4, hint=(0, 0, 1), phase=45, caps=(True, True))
    for k in range(prongs):
        a = rad(360 / prongs * k + 360 / n / 2 + 30)
        b0 = c + V(math.sin(a) * r, math.cos(a) * r, 0)
        F += spike("core_ring", "plate", b0, V(math.sin(a), math.cos(a), -0.45), plen, w * 0.8, sides=4, rings=1)
    return F


def vessel(phase, dim=False):
    """心臓の上に立つ血管 (大動脈) 3 本。"""
    F = []
    specs = [((0.0, 19.0, 0.0), [(0.4, 3.0, -0.3), (-0.5, 5.2, 0.2), (0.2, 7.0, 0.0)], 1.35),
             ((-2.2, 19.0, 0.4), [(-1.2, 2.2, 0.3), (-3.0, 4.0, 0.5)], 0.95),
             ((2.3, 19.0, 0.3), [(1.4, 2.4, 0.3), (3.4, 3.8, -0.2)], 0.95)]
    for base, offs, r in specs:
        b = V(*base)
        pts = curve([b] + [b + V(*o) for o in offs], 2 * len(offs))
        n = len(pts)
        rs = [r * (1 - 0.35 * i / (n - 1)) for i in range(n)]
        F += tube("vessel", "vessel", pts, rs, rs, sides=5, hint=(0, 0, 1), phase=36, caps=(False, True))
    return F


def curve(ctrl, n):
    c = [np.asarray(p, float) for p in ctrl]
    c = [c[0] * 2 - c[1]] + c + [c[-1] * 2 - c[-2]]
    out = []
    m = len(c) - 3
    for i in range(n + 1):
        u = i / n * m
        k = min(int(u), m - 1)
        t = u - k
        p0, p1, p2, p3 = c[k], c[k + 1], c[k + 2], c[k + 3]
        out.append(0.5 * ((2 * p1) + (-p0 + p2) * t + (2 * p0 - 5 * p1 + 4 * p2 - p3) * t * t + (-p0 + 3 * p1 - 3 * p2 + p3) * t ** 3))
    return out


# ------------------------------------------------------------------ 火の粉・垂れ

def cinders(phase, scale=1.0):
    F = []
    for i, (c, s) in enumerate(cube_list(phase, "cinders")):
        r = (max(s) * 0.62 + 0.12) * scale
        F += ellipsoid("cinders", "cinder", c, (r * 0.85, r * 1.2, r * 0.85), segs=4, rings=1, rot=rot_y(17 * i), phase=45.0)
    return F


def surf_y_low(x, z):
    u = 1 - (x / RAD[0]) ** 2 - (z / RAD[2]) ** 2
    return CEN[1] - RAD[1] * math.sqrt(max(u, 0.0)) if u > 0 else None


STRANDS_P2 = [(-8.5, 3.2, 15, 7.2, 10.8), (-6.5, -7.5, 16, 8.8, 15.2), (-5.9, 1.3, 11, 3.4, 7.5), (-3.9, -4.0, 17, 4.4, 8.2),
              (-2.7, -1.7, 16, -0.4, 6.8), (-2.5, 8.5, 22, 8.8, 15.2), (-1.7, 4.9, 7, 4.2, 8.2), (0.1, -0.2, 10, -2.9, 5.5),
              (1.8, -2.7, 11, 0.4, 6.8), (0.8, -5.6, 11, 4.8, 7.8), (1.6, -8.2, 7, 7.8, 12.2), (3.2, 1.4, 15, -0.9, 6.2),
              (4.4, 4.1, 14, 3.8, 8.2), (6.8, -2.0, 13, 3.1, 9.8), (6.8, 6.7, 20, 8.8, 12.2), (10.2, -3.3, 22, 10.2, 16.8)]


def strands_p2(thick=1.0, keep=1.0):
    """熔けた垂れ (先を細く)。"""
    F = []
    for i, (x, z, n, yb, yt) in enumerate(STRANDS_P2):
        if float(hash1(i, 41)) > keep:
            continue
        r = (0.5 + 0.045 * n) * thick
        ys = surf_y_low(x, z)
        y0 = min(yt, ys + 1.5) if ys is not None else yt
        y0 = max(y0, yb + 2.0)
        pts = [V(x, y0, z), V(x + 0.15 * math.sin(i), (y0 + yb) / 2, z + 0.15 * math.cos(i)), V(x, yb, z)]
        F += tube("drips", "drip", pts, [r, r * 0.7, 0.0], [r, r * 0.7, 0.0], sides=3, hint=(0, 0, 1), phase=90 + 20 * i)
    return F


def drops_p2():
    F = []
    for i, (c, s) in enumerate(cube_list(2, "drops")):
        F += ellipsoid("drops", "drip", c, (0.7, 1.05, 0.7), segs=4, rings=1, rot=rot_y(23 * i), phase=45.0)
    return F


def stalactites(bone, pos, length, r, mat="stalac", glow_tip=True):
    F = []
    for i, (x, z, yt, yb) in enumerate(pos):
        pts = [V(x, yt, z), V(x, (yt + yb) / 2, z), V(x, yb, z)]
        F += tube(bone, mat, pts, [r, r * 0.72, 0.0], [r, r * 0.72, 0.0], sides=4, hint=(0, 0, 1), phase=45 + 15 * i)
    return F
