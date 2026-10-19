"""A 案: 平背の子亀 — 成体の潮殻をそのまま縮めた子海亀。低く広い青緑の甲羅の頂が平らな腰掛けの板 (真珠層) になり、
真珠の粒を連ねた桃色の縁が 6 人の席を囲む。前ひれ 2 枚で水をかき、額の小さな真珠の杯に歌のかけらが灯る。"""
from __future__ import annotations

from ys import *  # noqa
from pal import *  # noqa

NAME = "A 平背の子亀"
HINT = 512

PAL = dict(
    mats={
        **COMMON,
        "belly": m(BELLY, 0.6, 0.3, 0.08, ao=0.2),
        "rim": m(TEAL_D, 0.62, 0.3, 0.08, 0.08, streak=0.1),
        "shell": m(TEAL, 0.58, 0.3, 0.08, 0.1, 1, stain=dict(ramp=[(150, 134, 100), (196, 180, 136)], thr=0.95, up=0.1, conc=0.3, low=0.0)),
        "deck": m([(54, 100, 100), (80, 134, 128), (112, 166, 152), (150, 196, 178), (196, 224, 206)], 0.55, 0.25, 0.08, 0.05, 1, stripe=16, axis="v"),
        "lip": m(NACRE_IN, 0.62, 0.3, 0.06),
        "skin": m(SAND, 0.58, 0.3, 0.08, 0.06, 1),
        "skin2": m(TEAL_D, 0.6, 0.3, 0.06),
        "fin": m(TEAL, 0.5, 0.3, 0.08, 0.05, 1, stripe=6, axis="u"),
    },
    glow_mats=["song"],
)


def rounded_rect_path(ax, az, rc, y, n_corner=3):
    """角の丸い長方形の輪 (前 -z の真ん中から時計回り)。最初の点を最後にも置く。"""
    pts = []
    corners = [(ax - rc, -(az - rc), 270), (ax - rc, az - rc, 0), (-(ax - rc), az - rc, 90), (-(ax - rc), -(az - rc), 180)]
    pts.append(V(0, y, -az))
    for cx, cz, a0 in corners:
        for k in range(n_corner + 1):
            a = math.radians(a0 + 90 * k / n_corner)
            pts.append(V(cx + rc * math.cos(a), y, cz + rc * math.sin(a)))
    pts.append(V(0, y, -az))
    return pts


CREST = (31.0, 41.2, 66.4)   # 甲羅の縁の頂 (y, rx, rz)
DECK = (38.2, 64.2)           # 鞍の板 (rx, rz)。超楕円 p=2.5


def build():
    F = []
    # ================================================================ 甲羅と腹 (外の壁が座る板を囲む鉢。上は内の壁を下りて塞ぐ)
    prof = [(0.0, 30.0, 42.0), (5.0, 41.0, 55.0), (11.0, 48.0, 64.0), (15.0, 53.0, 71.0), (18.5, 52.0, 70.0),
            (24.0, 47.5, 68.5), (28.5, 43.0, 67.0), (CREST[0], CREST[1], CREST[2]), (30.0, 39.4, 65.2), (21.0, 38.6, 64.6)]
    F += sloft("body", "belly", prof, n=24, p=2.5,
               mats=["belly", "belly", "rim", "rim", "shell", "shell", "shell", "shell", "shell"])
    # 縁の鱗板 (外周の小さな山)
    for k in range(18):
        a = math.radians(10 + 20 * k)
        sx, cz = math.sin(a), -math.cos(a)
        x = 53.5 * math.copysign(abs(sx) ** 0.8, sx)
        z = 71.5 * math.copysign(abs(cz) ** 0.8, cz)
        d = nrm(V(x / 53.5 ** 2, 0.0, z / 71.5 ** 2))
        F += fbox("body", "rim", V(x, 16.8, z) - d * 2.0, (9.0, 3.6, 6.0),
                  R=np.column_stack([np.cross(V(0, 1, 0), d), V(0, 1, 0), d]), taper=(0.6, 0.7), cut=0.3)
    # 甲羅の背の鱗板の山 (壁の外の斜面。座る所の外)
    for x, z in ((44, -30), (44, 0), (44, 30), (-44, -30), (-44, 0), (-44, 30), (0, -62), (24, -56), (-24, -56),
                 (0, 64), (26, 56), (-26, 56)):
        d = nrm(V(x / 44.0 ** 2, 0.9 / 30.0, z / 66.0 ** 2))
        F += fbox("body", "shell", V(x * 1.02, 25.5, z * 1.02), (10.0, 3.0, 10.0),
                  R=np.column_stack([nrm(np.cross(d, V(0, 0, 1)) if abs(d[2]) < 0.9 else V(1, 0, 0)), d,
                                     np.cross(nrm(np.cross(d, V(0, 0, 1)) if abs(d[2]) < 0.9 else V(1, 0, 0)), d)]),
                  taper=(0.65, 0.65), cut=0.4)
    # 甲羅の肩の珊瑚と藤壺 (縁の外)
    F += coral("body", "coral", V(46, 22, -40), (0.5, 1, -0.1), 11.0, 1.9, [(0.5, 40, 0.6, 50), (0.7, 220, 0.5, 45)])
    F += coral("body", "coral2", V(-48, 21, -22), (-0.6, 1, 0), 9.0, 1.7, [(0.55, 120, 0.6, 50)])
    F += coral("body", "coral3", V(47, 21, 30), (0.6, 1, 0.2), 8.0, 1.7, [(0.5, 300, 0.6, 50)])
    F += coral("body", "coral", V(-44, 22, 44), (-0.5, 1, 0.3), 10.0, 1.8, [(0.5, 60, 0.6, 45), (0.75, 250, 0.4, 50)])
    for p, d in ((V(50, 19, 8), (1, 0.6, 0)), (V(-51, 19, -4), (-1, 0.6, 0)), (V(30, 21, 64), (0.3, 0.8, 1)),
                 (V(-26, 21, -66), (-0.3, 0.8, -1))):
        F += cone("body", "barn", p, d, 3.0, 2.2, sides=5)

    # ================================================================ 鞍 (甲羅の鉢の底の平らな板 + 縁の頂の真珠の帯)
    F += sloft("saddle", "deck", [(SEAT - 4.0, DECK[0], DECK[1]), (SEAT, DECK[0], DECK[1])], n=24, p=2.5)
    crest = sring(CREST[1] + 0.3, CREST[2] + 0.3, CREST[0] + 0.8, n=28, p=2.5)
    F += rod("saddle", "lip", crest + [crest[0]], 2.4, sides=6, hint=(0, 1, 0))
    for k in range(0, 28, 2):
        F += pearl("saddle", "pearl", crest[k] + V(0, 1.6, 0), 3.0, segs=6, rings=2)
    # 後ろの背もたれ (殻の縁が高く反る) と前の舳先の山
    F += fbox("saddle", "rim", V(0, CREST[0] + 3.0, 64.0), (40.0, 8.0, 5.0), R=rot_x(-14), taper=(0.7, 0.55), cut=0.4)
    F += fbox("saddle", "rim", V(0, CREST[0] + 1.5, -64.0), (22.0, 5.0, 5.0), R=rot_x(12), taper=(0.6, 0.6), cut=0.4)

    # ================================================================ 首と頭
    F += rod("head", "skin", [V(0, 10.0, -58.0), V(0, 13.0, -68.0), V(0, 16.0, -76.0)], [12.0, 11.0, 10.0], rb=[9.0, 8.5, 8.0],
             sides=8, hint=(0, 1, 0), phase=22.5)
    F += ellipsoid("head", "skin", V(0, 20.0, -86.0), (17.0, 13.5, 15.5), segs=10, rings=4)
    # 額と頬の硬い鱗の板 (成体と同じ青緑)
    F += fbox("head", "skin2", V(0, 32.4, -86.5), (18.0, 2.4, 16.0), R=rot_x(-6), taper=(0.7, 0.75), cut=0.4)
    for s in (1, -1):
        F += fbox("head", "skin2", V(s * 15.0, 15.5, -84.0), (3.0, 7.0, 12.0), R=rot_z(s * 12), taper=(0.6, 0.8), cut=0.4)
    F += cone("head", "skin2", V(0, 16.0, -98.0), (0, -0.3, -1), 6.0, 6.0, sides=6, phase=30)
    eyes = []
    for s in (1, -1):
        c = V(s * 12.6, 23.0, -94.0)
        F += ellipsoid("head", "eye", c, (5.0, 5.4, 4.6), segs=8, rings=3)
        eyes.append(dict(c=tuple(c), r=5.0, dir=(s * 0.7, 0.1, -0.7), a=3.8, b=4.2, tilt=0.0, bone="head"))
    # 歌のかけら: 額の真珠層の杯に灯る粒
    F += fbox("head", "lip", V(0, 34.5, -86.0), (7.0, 3.0, 7.0), taper=(1.35, 1.35), cut=0.4)
    F += pearl("song", "song", V(0, 38.5, -86.0), 3.8, segs=8, rings=3)

    # ================================================================ ひれ・尾
    for s, sd in ((1, "r"), (-1, "l")):
        F += paddle(f"fin_f{sd}", "fin", V(s * 40, 9.0, -40.0), V(s * 84, 5.0, -26.0), [22, 24, 17, 5], [6.0, 5.0, 3.0, 1.5],
                    lead=(0, 0, -1), sweep=6.0)
        F += paddle(f"fin_b{sd}", "fin", V(s * 36, 7.0, 46.0), V(s * 58, 5.0, 68.0), [16, 16, 11, 4], [5.0, 4.0, 3.0, 1.5],
                    lead=(s * 0.3, 0, -1))
    F += cone("tail", "skin", V(0, 8.0, 62.0), (0, -0.1, 1), 18.0, 6.0, sides=6, rings=2, phase=30)
    return F, eyes, paint_extra


SHELL_C = [(0, 0), (0, -34), (0, 34), (24, -14), (-24, -14), (24, 16), (-24, 16)] +           [(int(46 * math.sin(math.radians(a))), int(64 * -math.cos(math.radians(a)))) for a in range(0, 360, 30)]


def paint_extra(c, col, glow):
    col = scutes(c, col, ("shell", "deck"), SHELL_C, TEAL_D[0], (200, 220, 200), width=1.2, ymin=17)
    return col, glow
