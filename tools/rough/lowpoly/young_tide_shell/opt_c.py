"""C 案: 開き貝の稚貝 — 平たい底の二枚貝 (扇貝)。下の殻が丸い舟、上の殻は後ろで蝶番から立ち上がった扇の背もたれ。
殻の間からはみ出す鮮やかな外套膜の縁に、光る小さな目の粒が並ぶ。甲板の左右に珊瑚の手すり、前の唇に大きな真珠 (歌のかけら)。"""
from __future__ import annotations

from ys import *  # noqa
from pal import *  # noqa

NAME = "C 開き貝の稚貝"
HINT = 512

MANTLE = [(20, 40, 90), (34, 80, 140), (50, 130, 180), (96, 190, 214), (170, 236, 240)]
MANTLE2 = [(50, 24, 80), (90, 50, 130), (130, 86, 176), (180, 140, 214)]
VALVE = [(30, 66, 70), (48, 100, 100), (78, 140, 132), (124, 180, 164), (186, 216, 196)]
PAL = dict(
    mats={
        **COMMON,
        "valve": m(VALVE, 0.56, 0.3, 0.08, 0.06, 1),
        "rib": m(SAND, 0.6, 0.3, 0.07, 0.04, 1),
        "belly": m(BELLY, 0.6, 0.3, 0.08, ao=0.2),
        "mantle": m(MANTLE, 0.58, 0.32, 0.1, 0.0, 1,
                    stain=dict(ramp=[(150, 230, 236), (210, 250, 250)], thr=0.9, up=0.2, conc=0.0, low=0.0)),
        "mantle2": m(MANTLE2, 0.58, 0.3, 0.08, 0.0, 1),
        "deck": m(NACRE, 0.56, 0.25, 0.07, 0.0, 1,
                  stain=dict(ramp=[(150, 104, 128), (204, 150, 168), (236, 196, 206)], thr=0.92, up=0.3, conc=0.0, low=0.0)),
        "nacre": m(NACRE, 0.6, 0.28, 0.06, 0.0, 1),
        "eyedot": m(SONG, 0.6, 0.3, 0.04),
        "flesh": m(CORAL_P, 0.55, 0.3, 0.08, 0.0, 1),
    },
    glow_mats=["song", "eyedot"],
)
VR = (54.0, 58.0)     # 下の殻の縁 (rx, rz)
DECK = (40.0, 58.0)   # 甲板 (rx, rz)、p = 3
HINGE = V(0, 22.0, 54.0)


def growth(c, col, glow):
    """殻の同心の成長線 (蝶番・殻の中心から)。"""
    P, mats, owned, bay = c["P"], c["mats"], c["owned"], c["bay"]
    r = np.hypot(P[..., 0], P[..., 2])
    on = (mats == "valve") & owned & ((r % 7.0) < 1.0) & (P[..., 1] > 3)
    col = np.where(on[..., None], col * 0.75, col)
    rh = np.linalg.norm(P - HINGE, axis=-1)
    on = (mats == "nacre") & owned & ((rh % 6.0) < 0.9)
    col = np.where(on[..., None], col * 0.86, col)
    on = (mats == "deck") & owned & (((r + 2 * bay) % 9.0) < 0.8)
    col = np.where(on[..., None], col * 0.88, col)
    return col, glow


def fan_sector(a0, a1, R, thick, tilt=22.0):
    up = rot_x(-tilt) @ V(0, 1, 0)      # 後ろ (+z) へ倒す
    sd = V(1, 0, 0)
    nrm_ = nrm(np.cross(sd, up))         # 前 (-z) 側を向く法線
    if nrm_[2] > 0:
        nrm_ = -nrm_
    pts = [HINGE + V(0, -2.0, 0)]
    for a in (a0, a1):
        ar = math.radians(a)
        pts.append(HINGE + R * (math.sin(ar) * sd + math.cos(ar) * up))
    front = [p + nrm_ * thick * 0.5 for p in pts]
    back = [p - nrm_ * thick * 0.5 for p in pts]
    return rloft("body", "valve", [front, back], caps=("nacre", "valve")), pts


def build():
    F = []
    # ================================================================ 下の殻 (丸い舟) と放射の畝
    F += sloft("body", "belly", [(0.0, 36.0, 38.0), (3.0, 45.0, 47.0), (8.0, 51.0, 54.0), (13.0, VR[0], VR[1]),
                                 (17.0, VR[0] - 0.5, VR[1] - 0.5)], n=24, p=2.2, mats=["belly", "valve", "valve", "valve"])
    for k in range(22):
        a = math.radians(360.0 * (k + 0.5) / 22)
        s_, c_ = math.sin(a), -math.cos(a)
        p0 = V(36.5 * s_, 0.5, 38.5 * c_)
        p1 = V(46.0 * s_, 3.5, 48.0 * c_)
        p2 = V(52.0 * s_, 8.5, 55.0 * c_)
        p3 = V((VR[0] + 1.4) * s_, 15.0, (VR[1] + 1.4) * c_)
        F += rod("body", "rib", [p0, p1, p2, p3], [1.6, 2.4, 2.8, 3.0], sides=4, hint=(0, 1, 0), phase=45)
    # 外套膜 (殻の間からはみ出す鮮やかな肉) と、縁に並ぶ光る目
    F += sloft("body", "mantle", [(14.5, VR[0] - 1.0, VR[1] - 1.0), (18.0, VR[0] + 1.5, VR[1] + 1.5), (21.0, VR[0] - 2.0, VR[1] - 2.0)],
               n=24, p=2.2)
    for k in range(26):
        a = math.radians(360.0 * k / 26)
        s_, c_ = math.sin(a), -math.cos(a)
        p = V((VR[0] + 1.8) * s_, 18.0, (VR[1] + 1.8) * c_)
        if p[2] > 46 and abs(p[0]) < 30:
            continue   # 蝶番の下は扇に隠れる
        F += ellipsoid("body", "eyedot", p, (1.5, 1.5, 1.5), segs=5, rings=2)
    # ================================================================ 上の殻 = 扇の背もたれ (後ろの蝶番から立つ)
    angs = np.linspace(-84, 84, 13)
    rim_pts = []
    for i in range(len(angs) - 1):
        th = 3.0 if i % 2 == 0 else 4.6
        f, pts = fan_sector(angs[i], angs[i + 1], 46.0 + (1.5 if i % 2 else 0.0), th)
        F += f
        rim_pts.append((pts[1] + pts[2]) / 2)
    for i in (2, 5, 8, 10):
        F += cone("body", "barn", rim_pts[i] * 0.85 + HINGE * 0.15 + V(0, 0, 3.0), (0, 0.3, 1), 2.6, 2.0, sides=5)
    # 蝶番の耳
    for s in (1, -1):
        F += fbox("body", "valve", HINGE + V(s * 9.0, 1.0, 1.0), (14.0, 5.0, 6.0), R=rot_z(s * 10), taper=(0.6, 0.8), cut=0.4)
    F += coral("body", "coral", rim_pts[3] * 0.7 + HINGE * 0.3 + V(0, 0, 3.5), (0.3, 0.6, 1), 8.0, 1.5, [(0.5, 40, 0.6, 50)])

    # ================================================================ 鞍 = 甲板 (真珠層) + 左右の珊瑚の手すり
    F += sloft("saddle", "deck", [(SEAT - 4.0, DECK[0], DECK[1]), (SEAT, DECK[0], DECK[1])], n=24, p=3.0)
    for s in (1, -1):
        rail = [V(s * 33.5, SEAT + 1.0, z) for z in (-44.0, -22.0, 0.0, 22.0, 44.0)]
        rail = [p + V(0, 1.2 * math.cos(p[2] / 44.0 * 1.5), 0) for p in rail]
        F += rod("saddle", "coral3", rail, [2.0, 2.6, 2.8, 2.6, 2.0], sides=5, hint=(1, 0, 0))
        for i, z in enumerate((-40.0, -14.0, 14.0, 40.0)):
            F += coral("saddle", "coral" if i % 2 else "coral2", V(s * 34.0, SEAT + 2.0, z), (s * 0.45, 1, 0.1 * (i - 1.5)),
                       9.0 + 2 * (i % 2), 1.8, [(0.5, 90 if s > 0 else 270, 0.55, 45)])
    # ================================================================ 前: 足 (舌のような肉)・真珠の唇
    F += ellipsoid("head", "flesh", V(0, 9.0, -64.0), (16.0, 6.5, 14.0), segs=10, rings=3)
    F += cone("head", "flesh", V(0, 8.0, -74.0), (0, -0.15, -1), 12.0, 7.0, sides=6, rings=2, phase=30)
    eyes = []
    for s in (1, -1):
        F += rod("head", "mantle2", [V(s * 8.0, 12.0, -66.0), V(s * 10.0, 20.0, -73.0)], [3.0, 2.6], sides=6)
        F += ellipsoid("head", "eye", V(s * 10.2, 21.0, -74.0), (2.8, 2.8, 2.8), segs=6, rings=2)
        eyes.append(dict(c=(s * 10.2, 21.0, -74.0), r=2.8, dir=(s * 0.4, 0.3, -1.0), a=2.0, b=2.0, tilt=0.0, bone="head"))
    F += fbox("head", "nacre", V(0, 22.0, -73.0), (10.0, 5.0, 9.0), taper=(1.3, 1.3), cut=0.45)
    F += rod("head", "nacre", [V(0, 12.0, -68.0), V(0, 20.0, -72.5)], [3.0, 3.4], sides=6)
    F += pearl("song", "song", V(0, 29.5, -76.0), 5.6, segs=10, rings=4)

    # ================================================================ ひれ (外套膜の襞)・尾 (水管)
    for s, sd in ((1, "r"), (-1, "l")):
        F += paddle(f"fin_f{sd}", "mantle", V(s * 42, 13.0, -36.0), V(s * 70, 10.0, -30.0), [24, 22, 16, 6], [4.0, 3.5, 2.5, 1.2],
                    lead=(0, 0, -1), sweep=5.0)
        F += paddle(f"fin_b{sd}", "mantle", V(s * 42, 12.0, 38.0), V(s * 62, 10.0, 48.0), [18, 16, 11, 4], [3.5, 3.0, 2.2, 1.0],
                    lead=(s * 0.3, 0, -1))
    F += rod("tail", "mantle2", [V(0, 9.0, 52.0), V(0, 10.0, 62.0), V(0, 12.5, 72.0)], [5.0, 4.6, 4.4], sides=8)
    F += rod("tail", "mantle", [V(0, 12.4, 71.5), V(0, 12.9, 74.0)], [5.4, 5.4], sides=8)
    return F, eyes, growth
