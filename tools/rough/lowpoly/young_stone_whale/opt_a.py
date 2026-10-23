"""A 案: 骨の輿 — 丸くふっくらした石の仔鯨。背に鯨骨の肋が両わきから立ち上がって 6 人の輿を囲む。
肋の先に小さな青緑の光の粒 (歌のかけら)、額に光る歌の石。左わきに親ゆずりの小さな錨石と鎖。腹の下に小さな雲。
取り込まれるのは下の build() (作り込みの版)。body_common は B・C 案が使う最初の版の胴。"""
from __future__ import annotations

from ysw import *  # noqa

NAME = "A 骨の輿"
HINT = 512
KEYS = [(-86, 30, 9, 10), (-83, 30, 17, 19), (-74, 30, 23, 27), (-58, 30, 26, 32), (-32, 30, 26, 35), (0, 30, 26, 35),
        (28, 31, 25, 33), (50, 32, 21, 27), (66, 34, 14, 18), (80, 35, 8, 10)]
TAIL = [(56, 36, 12, 15), (72, 37, 9, 10), (88, 38, 5.5, 6), (98, 38, 4, 4.5)]


def body_common(keys, tail, eye_z=-64, eye_r=(4.5, 5.5, 5.5), belly=0.9, fin_len=1.0, fin_w=1.0):
    F = hull("body", "stone", keys, n=12, belly=belly)
    cy, hh, hw = hull_at(keys, eye_z)
    eyes = []
    for s in (1, -1):
        c = V(s * (hw - 1.5), cy + 1.0, eye_z)
        F += ellipsoid("body", "eye", c, eye_r, segs=8, rings=3)
        # 瞼の骨の筋 (目の上の白い弧)
        F += tube("body", "bone", [c + V(-s * 2.5, 5.0, -5.0), c + V(s * 0.8, 6.6, 0), c + V(-s * 2.5, 5.0, 5.5)],
                  [1.3, 1.6, 1.3], sides=4, hint=(0, 1, 0), caps=(True, True))
        eyes.append(dict(c=tuple(c), r=eye_r[2], dir=(s * 1.0, 0.05, -0.3), a=eye_r[1] * 0.78, b=eye_r[1] * 0.86,
                         tilt=0.0, bone="body"))
    # 胸びれ
    for s, b in ((1, "fin_r"), (-1, "fin_l")):
        p = pv(b)
        path = [p + V(-s * 6, 1, -1), p + V(s * 14 * fin_len, -4, 4), p + V(s * 28 * fin_len, -9, 12)]
        F += flat_blade(b, "stone2", path, [8 * fin_w, 6.5 * fin_w, 0], [2.8, 2.0, 0])
    # 尾と尾びれ
    F += hull("tail", "stone", tail, n=10, belly=1.0)
    fp = pv("fluke")
    F += ellipsoid("fluke", "stone2", fp + V(0, 0, -1), (6, 3.2, 6), segs=8, rings=2)
    for s in (1, -1):
        F += flat_blade("fluke", "stone2", [fp + V(0, 0, -2), fp + V(s * 16, 0, 2), fp + V(s * 32, 0, 8)], [6, 7, 0],
                        [2.6, 2.0, 0])
    # 額の歌のかけら
    F += ellipsoid("mote", "mote", pv("mote") + V(0, 0.5, 0), (4.0, 3.2, 4.6), segs=8, rings=2)
    F += tube("mote", "bone", [pv("mote") + V(0, -1.5, 4.2), pv("mote") + V(0, 2.2, 5.4)], [3.0, 2.2], 3.0,
              sides=6, hint=(0, 1, 0), caps=(True, True))
    F += cloud_puffs()
    return F, eyes


# ====================================================================== 作り込みの版 (2026-10-24: 他の子と同じ細かさ)
# 胴は 16 角の輪 13 本 (岩の面の揺らぎ・頭と尾の付け根の背の稜)、下顎は別の塊で口の段が立つ。頭と脇腹に重ねた
# 六角の石の板、藤壺の群れ (いくつかの口が歌の色に光る)、こぶ付きの胸びれ (ザトウの前縁のこぶ)、二枚の葉の尾びれ、
# 尾の背の竜骨の板、脇腹に走る歌の光の筋 (絵)。鞍は肋の輿のまま、縄の巻きと脇に垂れる織りの布を足す。
# アトラスは 1024 に収まる一番高い密度で描く (FIT)。
FIT = 1024
DKEYS = [(-87, 31, 8, 9), (-85.5, 31, 14, 15.5), (-82, 31, 19.5, 21.5), (-76, 31, 23.5, 27), (-66, 30.3, 26, 31.5),
         (-50, 30, 26.5, 34.5), (-26, 30, 26.5, 35.5), (4, 30, 26.5, 35.5), (26, 30.8, 25.5, 34), (44, 31.8, 22, 29.5),
         (58, 33, 17.5, 22.5), (70, 34.2, 12.5, 15), (80, 35, 7.5, 9)]
TOP_MAX = 57.0   # 鞍の板 (56.5〜58) の下に隠れる胴の背の高さの上限 (揺らぎで鞍の上へ出さない)
DTAIL = [(54, 35, 12.5, 15), (64, 35.8, 10.5, 12.5), (74, 36.6, 8.5, 9.8), (84, 37.3, 6.5, 7.4), (92, 37.8, 5.0, 5.6),
         (99, 38, 3.6, 4.0)]
D_MATS = dict(BASE_MATS)
D_MATS.update({
    "stone": dict(ramp=STONE, base=0.58, lit=0.30, noise=0.12, crack=0.22, min=0, streak=0.08, facevar=0.06,
                  stain=dict(ramp=MOSS, thr=0.90, up=0.35, conc=0.3, low=0.0)),
    "stone2": dict(ramp=STONE, base=0.52, lit=0.32, noise=0.10, crack=0.16, min=0, facevar=0.06,
                   stain=dict(ramp=MOSS, thr=0.95, up=0.3, conc=0.3, low=0.0)),
    "plate": dict(ramp=[(46, 48, 58), (62, 64, 76), (84, 86, 98), (108, 110, 120), (134, 134, 140)], base=0.56, lit=0.32, noise=0.10, crack=0.26, min=0, ao=0.1, facevar=0.08,
                  stain=dict(ramp=MOSS, thr=0.93, up=0.4, conc=0.2, low=0.0)),
    "fin": dict(ramp=STONE, base=0.54, lit=0.32, noise=0.08, crack=0.12, min=0, stripe=9, axis="u", facevar=0.05),
    "tuber": dict(ramp=STONE_W, base=0.60, lit=0.30, noise=0.10, crack=0.0, min=0),
    "barn": dict(ramp=[(96, 90, 84), (148, 140, 128), (192, 186, 170), (224, 218, 204)], base=0.56, lit=0.32,
                 noise=0.14, crack=0.0, min=0, stripe=3, axis="v", facevar=0.08),
    "barnhole": dict(ramp=[(16, 18, 24), (30, 32, 40), (48, 50, 58)], base=0.45, lit=0.2, noise=0.05, crack=0, min=0),
    "cloth": dict(ramp=[(40, 70, 78), (56, 100, 108), (80, 134, 138), (120, 170, 166), (176, 200, 186)], base=0.55,
                  lit=0.28, noise=0.06, crack=0.0, min=0, stripe=6, axis="v", facevar=0.02),
    "rope": dict(ramp=[(86, 70, 50), (122, 100, 72), (160, 136, 98), (190, 168, 128)], base=0.58, lit=0.3,
                 noise=0.08, crack=0.0, min=0, stripe=2, axis="u"),
    "deck": dict(ramp=DECK, base=0.60, lit=0.20, noise=0.07, crack=0.0, min=0, stripe=12, axis="u", facevar=0.05),
    "bone": dict(ramp=BONE, base=0.62, lit=0.30, noise=0.07, crack=0.12, min=0, streak=0.12, facevar=0.05),
})


def _h(i, salt=0):
    q = math.sin(i * 12.9898 + salt * 78.233) * 43758.5453
    return q - math.floor(q)


def dsec(z, cy, hh, hw, ring_i, n=16, belly=0.9, jitter=0.022, ridge=0.0):
    """胴の輪切り (n 角)。0 番が真上。頂点ごとの小さな揺らぎで岩の面にする。ridge で背の稜を立てる。"""
    pts = []
    for k in range(n):
        a = 2 * math.pi * k / n
        s, c = math.sin(a), math.cos(a)
        j = 1.0 + jitter * (2 * _h(ring_i * 31 + k, 3) - 1)
        y = (1.0 if c > 0 else belly) * hh * math.copysign(abs(c) ** 0.8, c)
        x = hw * math.copysign(abs(s) ** 0.8, s)
        if k == 0:
            y += ridge
        pts.append(np.array([x * j, cy + y * j, z]))
    return pts


def surf(keys, z, a, belly=0.9, out=0.0):
    """胴の表 (揺らぎの前の滑らかな面) の点と外向き。a は真上 0 から右 (+x) 回りの角度 (度)。"""
    cy, hh, hw = hull_at(keys, z)
    r = math.radians(a)
    s, c = math.sin(r), math.cos(r)
    hy = hh * (1.0 if c > 0 else belly)
    y = hy * math.copysign(abs(c) ** 0.8, c)
    x = hw * math.copysign(abs(s) ** 0.8, s)
    n = nrm(V(x / hw ** 2, y / hy ** 2, 0))
    return V(x, cy + y, z) + n * out, n


def frame(d, up=(0, 0, 1)):
    """y 軸を d に向けた回転 (fbox・楕円体の向き)。"""
    d = nrm(np.asarray(d, float))
    u = np.asarray(up, float)
    if abs(np.dot(nrm(u), d)) > 0.95:
        u = V(1, 0, 0)
    ex = nrm(np.cross(u, d))
    ez = np.cross(ex, d)
    return np.column_stack([ex, d, ez])


def plate(bone, mat, p, n, w, l, t=3.2, spin=0.0, sink=1.4, taper=(0.72, 0.78), hexa=False):
    """面の外向き n に寝かせた石の板 (下は面の中に沈める)。w は横、l は前後の長さ。hexa で六角の板。"""
    R = frame(n) @ rot_y(spin)
    c = p + nrm(n) * (t / 2 - sink)
    if not hexa:
        return fbox(bone, mat, c, (w, t, l), R=R, taper=taper, cut=0.0)
    ex, ey, ez = R[:, 0], R[:, 1], R[:, 2]
    hexp = [(0.5, 0.0), (0.27, 0.5), (-0.27, 0.5), (-0.5, 0.0), (-0.27, -0.5), (0.27, -0.5)]
    r0 = [c - ey * t / 2 + ex * a * w + ez * b * l for a, b in hexp]
    r1 = [c + ey * t / 2 + (ex * a * w * taper[0] + ez * b * l * taper[1]) for a, b in hexp]
    return rloft(bone, mat, [r0, r1], (True, True))


def barnacle(bone, p, n, r, glow=False, sides=5):
    """藤壺: 先の細る閉じた筒 (円錐台) + 口の穴 (暗い円盤を沈める。光る口は歌の色)。"""
    n = nrm(n)
    h = 0.75 * r + 0.9
    F = tube(bone, "barn", [p - n * 1.2, p + n * h], [r, r * 0.6], sides=sides,
             hint=(0, 0, 1) if abs(n[2]) < 0.9 else (1, 0, 0), phase=_h(r * 7, 1) * 60, caps=(True, True))
    F += ellipsoid(bone, "glowbone" if glow else "barnhole", p + n * (h - 0.2), (r * 0.42, 0.3, r * 0.42),
                   segs=sides, rings=1, rot=frame(n))
    return F


PROF = [(0.0, 0.0), (0.22, 0.5), (0.62, 0.32), (1.0, 0.0)]   # 翼の断面の上の面 (弦の割合, 厚みの割合)


def foil(bone, mat, root, span, chord, stations, tuber=None, rays=(), ray_mat="stone2"):
    """ひれの翼 (閉じた形)。stations = [(span の距離, 前縁の後ろずれ, 弦の長さ, 厚み, 下がり)]。
    断面は前縁が丸く後ろが薄い 6 角形。tuber で前縁にこぶを並べ、rays (弦の割合) で上の面に骨の条を通す。"""
    span, chord = nrm(np.asarray(span, float)), nrm(np.asarray(chord, float))
    up = nrm(np.cross(chord, span))
    if up[1] < 0:
        up = -up
    rings, st = [], []
    for sd, lo, c, th, dr in stations:
        o = np.asarray(root, float) + span * sd - up * dr + chord * lo
        th = max(th, 0.3)
        pts = [o, o + chord * c * 0.22 + up * th * 0.5, o + chord * c * 0.62 + up * th * 0.32, o + chord * c,
               o + chord * c * 0.62 - up * th * 0.28, o + chord * c * 0.22 - up * th * 0.45]
        rings.append(pts)
        st.append((o, c, th))
    F = rloft(bone, mat, rings, (True, True))
    if tuber:
        for (o, c, th), (o2, c2, th2) in zip(st[1:-2], st[2:-1]):
            m = (o + o2) / 2 + chord * 0.5
            F += ellipsoid(bone, tuber, m, (th * 0.5, th * 0.45, th * 0.62), segs=5, rings=1)
    for fr in rays:
        path, rr = [], []
        for o, c, th in st[1:-1]:
            hgt = np.interp(fr, [a for a, _ in PROF], [b for _, b in PROF])
            path.append(o + chord * c * fr + up * th * (hgt - 0.12))
            rr.append(max(0.35, 0.26 * th))
        F += tube(bone, ray_mat, path, rr, sides=4, hint=tuple(up), caps=(True, True))
    return F


def drape(bone, mat, x0, z0, z1, y0, y1, s, out=2.5, thick=0.8):
    """鞍の脇に垂れる織りの布 (厚みのある板。下へ向かって外へ少し開く) と裾の房。"""
    rows = []
    for t in (0.0, 0.5, 1.0):
        x = s * (x0 + out * t * t)
        y = y0 + (y1 - y0) * t
        zz0, zz1 = z0 + 1.2 * t, z1 - 1.2 * t
        rows.append([V(x, y, zz0), V(x, y, zz1), V(x + s * thick, y, zz1), V(x + s * thick, y, zz0)])
    F = rloft(bone, mat, rows, (True, True))
    return F


def build():
    F = []
    K = DKEYS
    # ================================================================ 胴 (16 角 × 13 輪、頭と尾の付け根に背の稜、岩の揺らぎ)
    rings = []
    for i, (z, cy, hh, hw) in enumerate(K):
        ridge = 1.8 if z <= -60 or z >= 58 else 0.0
        r = dsec(z, cy, hh, hw, i, ridge=ridge)
        if abs(z) < 57:
            r = [V(p[0], min(p[1], TOP_MAX), p[2]) for p in r]
        rings.append(r)
    F += rloft("body", "stone", rings, (True, True))
    # 下顎: 口の線から下を一回り外へ張った別の塊 (口の段が影になる)
    jaw = []
    for z in (-85.0, -81.0, -73.0, -62.0, -51.0):
        cy, hh, hw = hull_at(K, z)
        my = 27.0 + (z + 86) * 0.06
        sc = 1.07 if z < -56 else 1.02
        ring = []
        for t in range(9):
            a = math.pi / 2 + math.pi * t / 8
            s_, c_ = math.sin(a), math.cos(a)
            x = hw * sc * math.copysign(abs(s_) ** 0.8, s_)
            y = cy + 0.9 * hh * sc * math.copysign(abs(c_) ** 0.8, c_)
            ring.append(V(x, min(y, my), z))
        ring += [V(-hw * 0.45, my, z), V(hw * 0.45, my, z)]
        jaw.append(ring)
    F += rloft("body", "stone", jaw, (True, True))
    # ================================================================ 目 (眉の石の板)
    eyes = []
    eye_z = -64
    cy, hh, hw = hull_at(K, eye_z)
    for s in (1, -1):
        c = V(s * (hw - 1.6), cy + 1.0, eye_z)
        er = (4.5, 5.5, 5.5)
        F += ellipsoid("body", "eye", c, er, segs=8, rings=3)
        eyes.append(dict(c=tuple(c), r=er[2], dir=(s * 1.0, 0.05, -0.3), a=er[1] * 0.78, b=er[1] * 0.86, tilt=0.0,
                         bone="body"))
        F += plate("body", "plate", c + V(-s * 1.4, 6.0, 0.5), V(s * 0.75, 0.66, -0.05), 7.5, 14.0, t=3.4,
                   sink=1.6, hexa=True)
    # ================================================================ 頭と脇腹の石の板 (重ね鱗。鞍と額の石の所には置かない)
    head = [(-82, 0, 14, 8, 0), (-75, 34, 10, 9, 10), (-75, -34, 10, 9, -10), (-66, 24, 10, 11, 6),
            (-66, -24, 10, 11, -6), (-79, 60, 8, 9, 12), (-79, -60, 8, 9, -12)]
    for z, a, w, l, spin in head:
        p, n = surf(K, z, a)
        F += plate("body", "plate", p, n, w, l, t=3.0, spin=spin, sink=1.5, hexa=True)
    flank = [(-44, 74, 10, 17, 4), (-18, 78, 11, 19, -3), (10, 73, 10, 18, 3), (34, 80, 9, 15, -4),
             (-30, 102, 9, 15, -5), (0, 104, 10, 16, 4)]
    for z, a, w, l, spin in flank:
        for s in (1, -1):
            p, n = surf(K, z + (2 if s < 0 else 0), s * a)
            F += plate("body", "plate", p, n, w, l, t=2.2, spin=-s * spin, sink=1.25, taper=(0.62, 0.7), hexa=True)
    # 鞍の後ろ・尾の付け根の背の板
    for z, a, w, l in ((61, 0, 11, 9), (69, 0, 9, 8)):
        p, n = surf(K, z, a)
        F += plate("body", "plate", p, n, w, l, t=2.8, sink=1.3, hexa=True)
    # ================================================================ 藤壺の群れ (いくつかの口が光る)
    spots = [(-82, 20, 2.4, False), (-80, -16, 2.0, True), (-76, 48, 2.6, False), (-70, -46, 2.2, False),
             (-56, 54, 2.0, True), (-56, -56, 2.4, False), (-34, 58, 2.6, False), (-12, -60, 2.0, True),
             (-20, 122, 2.2, False), (-2, -118, 2.6, False), (12, 60, 1.8, True), (32, -116, 1.8, False),
             (46, 52, 2.4, False), (50, -50, 2.0, True)]
    for z, a, r, g in spots:
        p, n = surf(K, z, a)
        F += barnacle("body", p, n, r, glow=g)
    # ================================================================ 胸びれ (前縁にこぶ、上に骨の条)
    for s, b in ((1, "fin_r"), (-1, "fin_l")):
        p = pv(b)
        root = p + V(-s * 7, 1.5, -3)
        span = V(s * 1.0, -0.34, 0.32)
        F += foil(b, "fin", root, span, (0, 0.05, 1.0),
                  [(0, 0, 16, 5.2, 0), (14, 2.0, 19, 4.4, 0.6), (25, 5.5, 17, 3.2, 1.8), (35, 10.0, 12.5, 2.2, 3.2),
                   (42, 15.0, 6.5, 1.2, 4.4), (46, 19.0, 1.4, 0.4, 5.2)],
                  tuber="tuber", rays=(0.45,))
        F += barnacle(b, root + nrm(span) * 24 + V(0, 0.3, 8.0), V(s * 0.1, 1, 0.05), 1.5)
    # ================================================================ 尾 (12 角、背の竜骨の板)
    tr = [dsec(z, cy, hh, hw, 40 + i, n=12, belly=1.0, jitter=0.02, ridge=1.2) for i, (z, cy, hh, hw) in enumerate(DTAIL)]
    F += rloft("tail", "stone", tr, (True, True))
    for z, w, l in ((66, 7, 9), (76, 6, 8), (85, 4.5, 6.5)):
        p, n = surf(DTAIL, z, 0, belly=1.0)
        F += plate("tail", "plate", p + V(0, 0.4, 0), V(0, 1, 0.12), w, l, t=3.4, sink=1.2, taper=(0.4, 0.7))
    for z, a in ((70, 70), (78, -64)):
        p, n = surf(DTAIL, z, a, belly=1.0)
        F += barnacle("tail", p, n, 1.7)
    # ================================================================ 尾びれ (二枚の葉)
    fp = pv("fluke")
    F += ellipsoid("fluke", "stone2", fp + V(0, 0, -1.5), (6.2, 3.4, 7.0), segs=8, rings=2)
    for s in (1, -1):
        F += foil("fluke", "fin", fp + V(s * 1.2, 0, -3.5), (s * 1.0, 0.0, 0.14), (0, 0, 1.0),
                  [(0, 0, 10, 3.4, 0), (9, 1.2, 10.5, 2.9, -0.3), (18, 3.4, 8.5, 2.1, -0.6), (25, 6.0, 6.0, 1.4, -0.8),
                   (30, 8.2, 3.0, 0.8, -0.8), (32.5, 9.6, 0.8, 0.3, -0.8)])
    # ================================================================ 額の歌のかけら (骨の縁の杯と二本の小さな角)
    m = pv("mote")
    F += ellipsoid("mote", "mote", m + V(0, 0.5, 0), (4.0, 3.4, 4.6), segs=8, rings=3)
    wall, _ = oval_wall("mote", "bone", m[1] - 3.6, m[1] + 0.6, 4.0, 4.8, 1.7, n=8, cz=m[2])
    F += wall
    for s in (1, -1):
        F += sp("mote", "bone", m + V(s * 4.6, -0.4, 1.0), (s * 0.5, 1.0, 0.45), 4.0, 1.1, sides=4)
    F += cloud_puffs()
    # ================================================================ 鞍 (骨の輿)
    F += fbox("saddle", "stone2", (0, 48.5, 0), (40, 13, 90), taper=(54 / 40, 106 / 90), cut=0.15)
    F += fbox("saddle", "deck", (0, SEAT_Y - 1.5, 0), (56, 3, 108), cut=0.12)
    for s in (1, -1):
        F += tube("saddle", "bone", [V(s * 27.6, 58.5, -52), V(s * 27.6, 58.5, 52)], [1.4, 1.4], sides=5,
                  hint=(0, 1, 0), caps=(True, True))
        for z in (-48, -16, 16, 48):
            k = 1.0 - 0.12 * (abs(z) > 30)
            path = [V(s * 22, 42, z), V(s * 31, 52, z), V(s * 33.5, 62, z), V(s * 32.5, 62 + 9 * k, z + 1),
                    V(s * 29.5, 63 + 13 * k, z + 2)]
            F += tube("saddle", "bone", path, [2.8, 2.6, 2.3, 1.8, 1.0], sides=5, hint=(0, 0, 1), caps=(True, True))
            F += ellipsoid("saddle", "glowbone", path[-1] + V(0, 0.6, 0), (1.4, 1.4, 1.4), segs=4, rings=1)
            # 肋を桟に縛る縄の巻き
            F += tube("saddle", "rope", [V(s * 31.5, 53.6, z), V(s * 32.4, 57.2, z)], 3.1, sides=5, hint=(0, 0, 1),
                      caps=(True, True))
        # 脇に垂れる織りの布 (肋の間)
        for z0, z1 in ((-44, -20), (-12, 12), (20, 44)):
            F += drape("saddle", "cloth", 28.2, z0, z1, 56.6, 47.0, s)
    F += tube("saddle", "bone", [V(-26, 57, -55.5), V(-17, 66, -57), V(0, 70, -58), V(17, 66, -57), V(26, 57, -55.5)],
              [2.2, 2.4, 2.6, 2.4, 2.2], sides=5, hint=(0, 0, 1), caps=(True, True))
    F += tube("saddle", "bone", [V(-26, 57, 55.5), V(-16, 64, 56.5), V(0, 67, 57), V(16, 64, 56.5), V(26, 57, 55.5)],
              [2.2, 2.4, 2.6, 2.4, 2.2], sides=5, hint=(0, 0, 1), caps=(True, True))
    F += ellipsoid("saddle", "glowbone", V(0, 71.5, -58), (1.8, 1.8, 1.8), segs=6, rings=2)
    for x in (-10, 0, 10):   # 背もたれの弧の椎骨
        F += ellipsoid("saddle", "bone", V(x, 66.6 - abs(x) * 0.12, 56.9), (2.6, 2.8, 2.4), segs=5, rings=1)
    # 左わきの小さな錨石と鎖 (親の錨の写し)
    F += chain("body", "chain", V(-28.5, 55, 40), V(-32.0, 37, 41.5), links=5, r=1.5)
    F += anchor_stone("body", "stone2", V(-32.4, 37.5, 41.5), size=6.5, mat2="chain")
    return F, eyes, chain_extra(whale_extra(), song_veins)


def song_veins(c, col, glow):
    """脇腹の石に走る歌の筋 (細い青緑の光の線が波打って前から後ろへ、ところどころ下へ枝分かれ)。"""
    P, N, mats, bones, owned = c["P"], c["N"], c["mats"], c["bones"], c["owned"]
    on = (mats == "stone") & (bones == "body") & owned & (np.abs(N[..., 0]) > 0.45)
    z, y = P[..., 2], P[..., 1]
    line = 36.0 + 3.2 * np.sin(z * 0.085 + np.sign(P[..., 0]) * 0.7) + 1.2 * np.sin(z * 0.23)
    v = on & (z > -50) & (z < 50) & (np.abs(y - line) < 0.55)
    br = on & (z > -40) & (z < 44) & (np.abs(((z + 4) % 22.0) - 11.0) < 0.5) & (y < line) & \
        (y > line - 6.5 + 3 * np.sin(z * 0.3))
    vein = v | br
    teal = np.array([(70, 200, 206), (170, 248, 246)], float)
    col = np.where(vein[..., None], teal[(np.abs(y - line) < 0.25).astype(int)], col)
    return col, glow | vein


PAL = dict(mats=D_MATS, glow_mats=["mote", "glowbone"])
