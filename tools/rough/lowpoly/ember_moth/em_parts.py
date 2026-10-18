"""火の粉蛾用の部品 (翅・頭・目・翅の座標)。em_common の上に積む。"""
from em_common import *  # noqa


def wing2(bone, rays, fr=(0.0, 0.5, 1.0), th=(0.6, 0.3), rise=0.6, fold=0.2, a0=1.2, lead=0.0,
          mats=("wtop", "wbot", "wrim"), skip=(), skip_rows=None, hole_mat="whole", droop=0.0, ydrop=None):
    """翅 1 枚 (両面の薄い板 + 縁)。rays = [(phi, L)] または [(phi, L, phi_tip)] (先で角度が寄る)。
    phi は +x 軸から前 (-Z) へ測る。skip = 描かない (輪 i, 筋の間 j) の集合、
    skip_rows = {j: 最初に抜く輪 i} (筋の間 j は輪 i 以降を全部抜く = 指の間のすき間)。
    描いた面と抜けた面の境の縁は hole_mat、翅の外周の縁は wrim。"""
    side = SIDE[bone]
    piv = PIV[bone]
    J = len(rays)
    NI = len(fr) - 1
    skipset = set(skip)
    for j, i0 in (skip_rows or {}).items():
        for i in range(i0, NI):
            skipset.add((i, j))
    T, B = [], []
    for i, f in enumerate(fr):
        rt, rb = [], []
        for j, ray in enumerate(rays):
            phi, L = ray[0], ray[1]
            pt = ray[2] if len(ray) > 2 else phi
            ph = math.radians(phi * (1 - f) + pt * f)
            d = np.array([side * math.cos(ph), 0.0, -math.sin(ph)])
            r = a0 * (1 - f) + L * f
            y = piv[1] + rise * f + fold * f * (1 if j % 2 else -1) * (1.0 if 0 < j < J - 1 else 0.3) \
                + lead * f * max(0.0, math.sin(ph)) - droop * f * f
            if ydrop is not None:
                y += ydrop(j, f)
            t = th[0] * (1 - f) + th[1] * f
            p = np.array([piv[0], y, piv[2]]) + d * r
            rt.append(p + V(0, t / 2, 0))
            rb.append(p - V(0, t / 2, 0))
        T.append(rt)
        B.append(rb)
    F = []
    mt, mb, mr = mats
    cen = piv + V(side * 6, 0, 0)

    def inrange(i, j):
        return 0 <= i < NI and 0 <= j < J - 1

    def drawn(i, j):
        return inrange(i, j) and (i, j) not in skipset

    for i in range(NI):
        for j in range(J - 1):
            if not drawn(i, j):
                continue
            q = [T[i][j], T[i][j + 1], T[i + 1][j + 1], T[i + 1][j]]
            F += poly(bone, mt, q, np.mean(q, 0) - V(0, 4, 0))
            q = [B[i][j], B[i][j + 1], B[i + 1][j + 1], B[i + 1][j]]
            F += poly(bone, mb, q, np.mean(q, 0) + V(0, 4, 0))
            for (di, dj, a, b) in ((0, -1, (i, j), (i + 1, j)), (0, 1, (i, j + 1), (i + 1, j + 1)),
                                   (-1, 0, (i, j), (i, j + 1)), (1, 0, (i + 1, j), (i + 1, j + 1))):
                ni, nj = i + di, j + dj
                if drawn(ni, nj):
                    continue
                m = hole_mat if inrange(ni, nj) else mr
                if m is None:
                    continue
                q = [T[a[0]][a[1]], T[b[0]][b[1]], B[b[0]][b[1]], B[a[0]][a[1]]]
                F += poly(bone, m, q, cen)
    return F


HC = V(0, 7.6, -6.7)       # 頭の中心 (head 骨の中)


def head_eyes(bone="head", mat="eye", r=1.15, depth=0.9, theta=42, phi=8, sides=6, hr=(1.7, 1.6, 1.7), up=(0, 1, 0),
              ry=None, hc=None):
    hc = HC if hc is None else hc
    F, E = [], []
    for s in (-1, 1):
        d = sphere_dir(s * theta, phi)
        p, nn = ell_point(hc, hr, d)
        E.append(dict(c=p + nn * 0.1, n=nn, r=r))
        F += dome(bone, mat, p, nn, r, depth, sides=sides, mid=0.72, phase=22.5, ry=ry,
                  up=(s * up[0], up[1], up[2]))
    return F, E


def eye_paint(ctx, col, glow, E, ramp_iris, ramp_pupil, glow_r=0.62, mat="eye"):
    sel = ctx.mats == mat
    for e in E:
        rel = ctx.P - e["c"]
        ax = np.einsum("...k,k->...", rel, e["n"])
        perp = np.linalg.norm(rel - ax[..., None] * e["n"], axis=-1)
        near = sel & (np.linalg.norm(rel, axis=-1) < e["r"] * 2.2)
        col = np.where(near[..., None], np.array(ramp_iris, float), col)
        pup = near & (perp < e["r"] * glow_r)
        col = np.where(pup[..., None], np.array(ramp_pupil, float), col)
        glow = glow | pup
    return col, glow


def wcoords(ctx):
    """翅の局所座標 (翅の付け根からの X, Z, 距離 r、角度 phi (+x から前へ))。"""
    p = PIV["wing_r"]
    X = np.abs(ctx.P[..., 0]) - p[0]
    Z = ctx.P[..., 2] - p[2]
    r = np.hypot(X, Z)
    phi = np.degrees(np.arctan2(-Z, X))
    return X, Z, r, phi


def interp_L(phi, rays):
    ph = np.array([a[0] for a in rays], float)
    Ls = np.array([a[1] for a in rays], float)
    o = np.argsort(ph)
    return np.interp(phi, ph[o], Ls[o])


def body_loft(profile, n=8, phase=0.0, mats=None, bone="body", mat="body"):
    rings = [zring(z, rx, rt, rb, cy, n=n, phase=phase) for (z, rx, rt, rb, cy) in profile]
    return loft(bone, mat, rings, caps=(mat, mat), mats=mats)


def ember_legs(mat="leg", y0=4.6, zs=(-2.8, -1.2, 0.6), x=1.2, bone="body", r=0.28, drop=2.6):
    F = []
    for s in (-1, 1):
        for k, z in enumerate(zs):
            a = V(s * x, y0 + 1.2, z)
            m = V(s * (x + 1.1), y0 - 0.1, z - 0.4 + 0.5 * k)
            b = V(s * (x + 1.6), y0 - drop + 0.9, z - 0.8 + 0.7 * k)
            F += tube(bone, mat, [a, m, b], [r, r * 0.8, 0.0], sides=3, hint=(0, 0, 1), phase=90,
                      axis_fn=lambda p, a=a: a)
    return F
