"""3 案で共有する部品 (翼・体・頭・尾・足・遺跡・門)。パラメータ P (辞書) で形を変える。"""
from common import *  # noqa
from common import _mk  # noqa

NF = lambda lod, a, b, c: (a, b, c)[lod]


# ------------------------------------------------------------------ 翼 (+X 側の左。右は鏡像)

def wing_sect(P, x):
    zle, zte, top, bot = P["zle"](x), P["zte"](x), P["top"](x), P["bot"](x)
    c = zte - zle
    th = top - bot
    pts = [(zle, bot + 0.55 * th), (zle + 0.20 * c, top - 0.02 * th), (zle + 0.50 * c, top - 0.10 * th),
           (zle + 0.82 * c, top - 0.42 * th), (zte, bot + 0.42 * th), (zle + 0.80 * c, bot + 0.18 * th),
           (zle + 0.50 * c, bot + 0.02 * th), (zle + 0.20 * c, bot)]
    return pts, (0.0, 0.20, 0.50, 0.82, 1.0)


def wing_surface(P, x, f, lift=0.0):
    """翼の上面の点 (x, 弦の割合 f)。上面を LE→TE の折れ線で補間する"""
    pts, fr = wing_sect(P, x)
    f = min(max(f, 0.0), 1.0)
    for i in range(4):
        if fr[i] <= f <= fr[i + 1]:
            t = (f - fr[i]) / (fr[i + 1] - fr[i])
            z = lerp(pts[i][0], pts[i + 1][0], t)
            y = lerp(pts[i][1], pts[i + 1][1], t)
            return V(x, y + lift, z)
    return V(x, pts[4][1] + lift, pts[4][0])


def wing_core(P, lod, bone="wing_l"):
    xs = NF(lod, P["xs_near"], P["xs_mid"], P["xs_far"])
    rings = []
    for x in xs:
        pts, _ = wing_sect(P, x)
        rings.append([V(x, y, z) for (z, y) in pts])
    faces = []
    for i in range(len(rings) - 1):
        cen = (np.mean(rings[i], 0) + np.mean(rings[i + 1], 0)) / 2
        for j in range(8):
            j2 = (j + 1) % 8
            mat = "cov" if j < 4 else "wunder"
            f = _mk([rings[i][j], rings[i][j2], rings[i + 1][j2], rings[i + 1][j]], bone, mat, cen)
            if f is not None:
                faces.append(f)
    f = _mk(rings[-1], bone, "cov", np.mean(rings[-2], 0))
    if f is not None:
        faces.append(f)
    return faces


def secondaries(P, lod, bone="wing_l"):
    n = NF(lod, P["sec_n"], max(5, P["sec_n"] // 2), 0)
    if n == 0:
        return []
    faces = []
    x0, x1 = P["sec_x0"], P["sec_x1"]
    for i in range(n):
        x = lerp(x0, x1, i / (n - 1))
        pts, _ = wing_sect(P, x)
        root = V(x, pts[4][1] + 0.3 * (i % 2), pts[4][0] - 5.0)
        zend = P["sec_end"](x) + (hash1(i, 3) - 0.5) * P.get("sec_jit", 2.0)
        tip = V(x + P.get("sec_sweep", 3.0), root[1] - P.get("sec_drop", 3.0), zend)
        w = (x1 - x0) / (n - 1) * P.get("sec_w", 1.45)
        kw = dict(top=P.get("sec_top", "sec"), bot="wunder", bend=(0, -P.get("sec_curl", 2.0), 0),
                  segs=2 if lod == 0 else 1, tipw=P.get("sec_tipw", 0.0), th=P.get("th", 1.0), cap=P.get("sec_cap", P.get("sec_tipw", 0.0) > 0),
                  mid=P.get("sec_mid", 1.0))
        faces += feather(bone, root, tip, w, **kw)
    return faces


def coverts(P, lod, bone="wing_l"):
    faces = []
    for r, row in enumerate(P["cov_rows"]):
        n = NF(lod, row["n"], max(4, row["n"] // 2), 0)
        if n == 0 or (lod == 1 and not row.get("mid", True)):
            continue
        for i in range(n):
            x = lerp(row["x0"], row["x1"], i / max(n - 1, 1)) + 0.4 * (r % 2) * (row["x1"] - row["x0"]) / max(n, 1)
            f0 = row["f"]
            c = P["zte"](x) - P["zle"](x)
            root = wing_surface(P, x, f0, 0.0)
            L = row["len"] * (1 + row.get("grow", 0.0) * i / max(n - 1, 1))
            tip = wing_surface(P, x + row.get("sweep", 2.0), f0 + L / c, row.get("lift", 0.9))
            faces += scale(bone, root + V(0, 0.2, 0), tip, row["w"], row.get("mat", "cov"), th=row.get("th", 1.0), segs=1,
                           tipw=row.get("tipw", 0.0), cap=row.get("cap", row.get("tipw", 0.0) > 0), mid=row.get("mid_w", 1.0))
    return faces


def veins(P, lod, bone="wing_l"):
    if lod > 0 or not P.get("veins"):
        return []
    faces = []
    v = P["veins"]
    n = v["n"]
    pts = []
    for i in range(n + 1):
        x = lerp(v["x0"], v["x1"], i / n)
        f = v["f"] + (v["amp"] if i % 2 else -v["amp"])
        pts.append(wing_surface(P, x, f, v.get("lift", 2.6)))
    faces += ribbon(bone, "vein", pts, v.get("w", 1.3))
    for (x, f, dz) in v.get("forks", []):
        a = wing_surface(P, x, f, v.get("lift", 2.6))
        b = wing_surface(P, x + 5, f + dz, v.get("lift", 2.6))
        c_ = wing_surface(P, x + 8, f + dz * 1.4, v.get("lift", 2.6))
        faces += ribbon(bone, "vein", [a, b, c_], v.get("w", 1.3) * 0.8)
    return faces


def hand(P, lod, bone="hand_l", knob="knob_w"):
    faces = []
    W0 = V(*P["wrist"])
    H1 = V(*P["hand_end"])
    sides = NF(lod, 6, 5, 4)
    path = [W0, lerp(W0, H1, 0.5), H1]
    if lod == 2:
        path = [W0, H1]
    faces += tube(bone, "cov", path, [P["hand_r0"], P["hand_r0"] * 0.6, P["hand_r0"] * 0.28][:len(path)],
                  sides=sides, hint=(0, 1, 0), phase=0, caps=(False, True))
    tips = P["prim_tips"]
    keep = range(len(tips)) if lod == 0 else (range(0, len(tips), 1) if lod == 1 else range(0, len(tips), 2))
    for i in keep:
        s, tx, tz, ty = tips[i]
        root = lerp(W0, H1, s)
        tip = V(tx, ty, tz)
        ln = np.linalg.norm(tip - root)
        w = P.get("prim_w", 9.0) * (1.0 if lod == 0 else 1.25)
        faces += feather(bone, root + V(0, 0.25 * (i % 2), 0), tip, w, th=P.get("pth", 1.2), top=P.get("prim_top", "prim"),
                         bot="wunder", bend=V(0, P.get("prim_curl", 2.5), 0) + P.get("prim_bend", V(0, 0, 0)),
                         segs=2 if lod == 0 else 1, tipw=P.get("prim_tipw", 0.0), mid=P.get("prim_mid", 1.0),
                         cap=P.get("prim_cap", P.get("prim_tipw", 0.0) > 0))
    # 小翼 (前縁の三枚)
    if lod == 0:
        for k in range(3):
            root = lerp(W0, H1, 0.1 + 0.1 * k) + V(0, 0.4, -4)
            faces += feather(bone, root, root + V(5 + 2 * k, 0.6, -9 - k), 3.5, th=0.8, top="prim", bot="wunder")
    # 手首の瘤
    r = P["knob_r"]
    segs = NF(lod, 8, 8, 5)
    rings_ = NF(lod, 3, 3, 1)
    faces += ellipsoid(bone, knob, W0 + V(0, 0.5, 0), (r, r * 0.9, r), segs=segs, rings=rings_)
    if lod < 2 and P.get("knob_ring", True):
        cp = [W0 + V(0, 0.5, 0) + V(math.cos(a) * (r + 1.2), 0.0, math.sin(a) * (r + 1.2))
              for a in [2 * math.pi * k / (12 if lod == 0 else 8) for k in range(13 if lod == 0 else 9)]]
        faces += tube(bone, "copper", cp, 1.5, 1.2, sides=4, hint=(0, 1, 0), phase=45)
    return faces


def build_wing(P, lod, knob="knob_w"):
    faces = wing_core(P, lod) + secondaries(P, lod) + coverts(P, lod) + veins(P, lod)
    faces += hand(P, lod, "hand_l", knob)
    return faces


# ------------------------------------------------------------------ 体

def body_shell(P, lod):
    n = NF(lod, P.get("body_n", 10), 8, 6)
    st = P["body_st"] if lod == 0 else [s for i, s in enumerate(P["body_st"]) if lod == 1 and i % 2 == 0 or lod == 2 and i % 3 == 0 or s is P["body_st"][-1]]
    sh = Shell("body", st, n=n, p=P.get("body_p", 2.5))
    return sh, sh.faces(caps=(False, True))


def neck_shell(P, lod):
    n = NF(lod, P.get("neck_n", 10), 8, 6)
    st = P["neck_st"] if lod == 0 else [s for i, s in enumerate(P["neck_st"]) if i % 2 == 0 or i == len(P["neck_st"]) - 1]
    sh = Shell("neck", st, n=n, p=P.get("neck_p", 2.5))
    return sh, sh.faces(caps=(False, False))


def head_shell(P, lod):
    n = NF(lod, P.get("head_n", 10), 8, 6)
    st = P["head_st"] if lod < 2 else [P["head_st"][0], P["head_st"][2], P["head_st"][-1]]
    sh = Shell("head", st, n=n, p=P.get("head_p", 2.4))

    def mf(z, c, s):
        if c < -0.45:
            return P.get("head_under", "belly")
        return P.get("head_top", "back")
    return sh, sh.faces(mf, caps=(True, True))


def beak(P, lod):
    faces = []
    n = NF(lod, 8, 6, 4)
    up = Shell("head", P["beak_up"] if lod < 2 else [P["beak_up"][0], P["beak_up"][2], P["beak_up"][-1]], n=n, p=P.get("beak_p", 1.9))
    faces += up.faces(lambda z, c, s: "beak", caps=(False, True))
    lo = Shell("head", P["beak_lo"] if lod < 2 else [P["beak_lo"][0], P["beak_lo"][-1]], n=n, p=2.2)
    faces += lo.faces(lambda z, c, s: "beak_d" if c > 0.2 else "beak", caps=(False, True))
    return faces


def eyes(P, sh, lod):
    faces = []
    z, y = P["eye_zy"]
    sc = P.get("eye_sc", 1.0)
    for side in (1, -1):
        p, nn = sh.at(z, y, side)
        up = (0, 1, 0)
        if lod == 0:
            faces += plinth("head", P.get("rim_mat", "beak_d"), p, nn, up, 6.6 * sc, P.get("rim_ry", 5.0) * sc, 1.5, 0.82, 8,
                            embed=1.0, rot=22.5)
            faces += plinth("head", "eye", p + nn * 1.0, nn, up, 4.4 * sc, P.get("eye_ry", 3.6) * sc, 0.8, 0.92, 8,
                            embed=0.2, rot=22.5)
            pb, nb = sh.at(z + 0.5, y + P.get("brow_dy", 5.2) * sc, side)
            faces += plinth("head", P.get("brow_mat", "prim"), pb, nb + V(0, 0.35, -0.2), up, 9.0 * sc, 2.2 * sc,
                            P.get("brow_h", 2.4), 0.6, 6, embed=1.0)
        elif lod == 1:
            faces += plinth("head", "beak_d", p, nn, up, 6.0, 4.6, 1.2, 0.8, 6, embed=0.8)
            faces += plinth("head", "eye", p + nn * 0.9, nn, up, 4.0, 3.2, 0.8, 0.9, 6, embed=0.2)
    return faces


def nostrils(P, lod):
    faces = []
    if lod > 0:
        return faces
    z, y = P["nostril_zy"]
    up = Shell("head", P["beak_up"], n=8, p=P.get("beak_p", 1.9))
    for side in (1, -1):
        p, nn = up.at(z, y, side)
        faces += plinth("head", "beak_d", p, nn, (0, 1, 0), 1.9, 1.1, 0.5, 0.7, 6, embed=0.9)
    return faces


def crest_rods(P, lod):
    faces = []
    if lod == 2:
        return faces
    for i, (x, z, h, lean) in enumerate(P["crest"]):
        if lod == 1 and i % 2:
            continue
        base = V(x, 63.0 + P.get("crest_y0", 0.0), z)
        faces += spike("crest", P.get("crest_mat", "copper"), base, V(x * 0.03 + lean[0], 1, lean[1]), h,
                       P.get("crest_r", 0.9) * (1.0 if lod == 0 else 1.5), sides=4 if lod == 0 else 3, rings=2 if lod == 0 else 1,
                       hint=(0, 0, 1), phase=45)
    return faces


# ------------------------------------------------------------------ 尾・足

def tail_fan(P, lod):
    faces = []
    n = NF(lod, P["tail_n"], max(5, P["tail_n"] // 2 + 1), 5)
    root = V(0, 44.0, 66.0)
    for i in range(n):
        t = i / (n - 1) * 2 - 1
        ph = math.radians(t * P["tail_ang"])
        L = P["tail_len"] * (1 - P.get("tail_taper", 0.0) * abs(t))
        tip = root + V(math.sin(ph) * L, -0.8 * abs(t), math.cos(ph) * L)
        r0 = root + V(math.sin(ph) * 10, 0.3 * (i % 2), math.cos(ph) * 10)
        w = P["tail_w"] * (1.0 if lod == 0 else 1.3)
        faces += feather("tail", r0, tip, w, th=1.4, top=P.get("tail_top", "tailf"), bot="wunder", segs=2 if lod == 0 else 1,
                         tipw=P.get("tail_tipw", 0.0), cap=P.get("tail_cap", P.get("tail_tipw", 0.0) > 0), mid=P.get("tail_mid", 1.0),
                         bend=V(0, P.get("tail_curl", -1.5), 0))
    return faces


def legs(P, lod, side=1):
    faces = []
    b = "leg_l" if side > 0 else "leg_r"
    s = side
    if lod == 2:
        return faces
    hip = V(12 * s, 36.0, 38.0)
    knee = V(12 * s, 24.0, 38.0)
    ank = V(12 * s, 20.0, 30.0)
    fm = P.get("leg_thigh", "belly_f")
    faces += tube(b, fm, [hip, knee], [P.get("thigh_r", 5.5), 3.6], sides=NF(lod, 6, 6, 4), hint=(1, 0, 0), phase=0, caps=(False, False))
    faces += tube(b, "leg", [knee, ank], [2.8, 2.2], sides=NF(lod, 6, 4, 4), hint=(1, 0, 0), phase=0, caps=(False, False))
    if lod == 2:
        return faces
    toes = [(-5.0, 0), (0, 0), (5.0, 0)] if lod == 0 else [(0, 0)]
    for k, (dx, _) in enumerate(toes):
        tip = V(s * (12 + (dx * 1.7 if k != 1 else 0.0)), 17.0, 16.0)
        faces += tube(b, "leg", [ank, ank + (tip - ank) * 0.6, tip], [2.0, 1.7, 1.2], sides=4, hint=(0, 1, 0), phase=45, caps=(False, False))
        if lod == 0:
            faces += spike(b, "talon", tip, V(dx * 0.08 * s, -0.5, -1), 5.0, 1.3, sides=4, rings=1, hint=(0, 1, 0), bend=V(0, -1.6, -0.5))
    if lod == 0:
        h = ank + V(0, -0.5, 1.8)
        faces += spike(b, "talon", h, V(0, -0.4, 1), 4.2, 1.3, sides=4, rings=1, hint=(0, 1, 0), bend=V(0, -1.4, 0.5))
    return faces


# ------------------------------------------------------------------ 遺跡・門

def tower(x, z, y0, h, w, lod, cap_mat="copper", mat="stone"):
    faces = []
    faces += slab("ruins", mat, (x, y0 + h / 2, z), (w, h, w), taper=0.5, jit=0.3, tag=int(abs(x) + z))
    if lod == 0:
        faces += slab("ruins", mat, (x, y0 + h + 1.2, z), (w + 2.2, 2.4, w + 2.2), taper=0.7, jit=0.3, tag=int(abs(x) + z + 3))
        faces += spike("ruins", cap_mat, V(x, y0 + h + 2.4, z), V(0, 1, 0), 8.0, 0.9, sides=4, rings=1, hint=(1, 0, 0))
        for sx, sz in ((0, -1), (0, 1), (-1, 0), (1, 0)):
            c = V(x + sx * (w / 2 + 0.3), y0 + h * 0.62, z + sz * (w / 2 + 0.3))
            sz_ = (0.8, 3.6, 1.6) if sx else (1.6, 3.6, 0.8)
            faces += slab("ruins", "glass", c, sz_)
    else:
        faces += spike("ruins", cap_mat, V(x, y0 + h, z), V(0, 1, 0), 7.0, 1.0, sides=3, rings=1, hint=(1, 0, 0))
    return faces


def skylight(P, lod):
    faces = []
    c = V(*P["sky_c"])
    r = P["sky_r"]
    k = NF(lod, 12, 8, 6)
    pts = [c + V(math.cos(a) * r, 0.8, math.sin(a) * r * 0.9) for a in [2 * math.pi * i / k for i in range(k + 1)]]
    if lod < 2:
        faces += tube("ruins", "copper", pts, 1.8, 1.4, sides=4, hint=(0, 1, 0), phase=45)
    # 窓 (薄い両面の板)
    top = [c + V(math.cos(a) * (r - 1.0), 1.9, math.sin(a) * (r - 1.0) * 0.9) for a in [2 * math.pi * i / k for i in range(k)]]
    bot = [c + V(math.cos(a) * (r - 1.0), 1.4, math.sin(a) * (r - 1.0) * 0.9) for a in [2 * math.pi * i / k for i in range(k)]]
    faces += poly("ruins", "glass", top, c + V(0, -3, 0))
    if lod == 0:
        faces += poly("ruins", "glass", bot[::-1], c + V(0, 3, 0))
        # 窓の格子 (十字の桟)
        faces += slab("ruins", "copper", c + V(0, 2.0, 0), (2 * r - 1.5, 0.7, 0.9))
        faces += slab("ruins", "copper", c + V(0, 2.0, 0), (0.9, 0.7, 1.8 * r - 1.5))
    return faces


def gate(P, lod):
    faces = []
    c = V(0.0, 59.5, 44.5)
    rx, ry = 7.0, 9.2
    k = NF(lod, 14, 10, 6)
    ang = [2 * math.pi * i / k for i in range(k + 1)]
    pts = [c + V(math.cos(a) * rx, math.sin(a) * ry, 0) for a in ang]
    if lod < 2:
        faces += tube("gate", P.get("gate_mat", "copper"), pts, 1.4, 1.8, sides=4, hint=(0, 0, 1), phase=45)
    ell = [c + V(math.cos(a) * (rx - 0.6), math.sin(a) * (ry - 0.6), 0.0) for a in ang[:-1]]
    faces += poly("gate", "gate", ell, c + V(0, 0, 3))
    faces += poly("gate", "gate", ell[::-1], c + V(0, 0, -3))
    if lod == 0:
        # 羽根の枠 (両側へ流れる 3 枚ずつ)
        for sx in (1, -1):
            for j in range(3):
                faces += feather("gate", c + V(sx * (rx + 0.5), -3 + 4 * j, 0), c + V(sx * (rx + 7 - j * 1.2), -2 + 5 * j, 1.5),
                                 3.2, th=0.8, top="plume", bot="plume")
    return faces


def stairs(P, lod):
    faces = []
    if lod > 0:
        return faces
    for i in range(3):
        faces += slab("ruins", "stone", (0, 51.0 - i * 0.2 + i * 1.6, 56 - i * 3.2), (10 - i, 2.4, 3.4), taper=0.3, tag=60 + i)
    return faces
