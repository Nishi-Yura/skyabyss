"""C案「ひげ板の頭巾」: 頭は鯨のひげ板 (バリーン) を重ねた尖った頭巾に包まれ、闇の中に光る 2 点の目だけが見える。
肩の重ね板・胸の前垂れ・背の長いマントも同じひげ板。細く真っ直ぐな脚、薄い長弓。
輪郭は「三角形のフード + 垂れる布」で、A (角) と B (棘) と違い、尖りより面の重なりで見せる。"""
from common import *  # noqa

BONE_C = [(40, 38, 46), (88, 84, 94), (132, 128, 134), (174, 168, 166), (208, 202, 192), (234, 228, 212)]
BAL = [(20, 16, 18), (44, 34, 32), (74, 56, 46), (106, 82, 62), (140, 112, 84)]
SALT = [(48, 66, 72), (78, 104, 108), (112, 142, 142), (150, 176, 172)]
PAL = {"mats": {
    "bone": dict(ramp=BONE_C, base=0.68, stain=dict(ramp=SALT, thr=0.97, up=0.2, low=0.04)),
    "tooth": dict(ramp=BONE_C[2:], base=0.8, min=0, crack=0.0),
    "dark": dict(ramp=[(6, 8, 14), (14, 16, 26), (28, 30, 42)], base=0.5, crack=0.0, min=0),
    "baleen": dict(ramp=BAL, base=0.55, lit=0.2, crack=0.0, min=1, stripe=3, axis="v", noise=0.1),
    "fibre": dict(ramp=BAL, base=0.5, lit=0.18, crack=0.0, min=1, stripe=3, axis="u", noise=0.1),
    "wrap": dict(ramp=[(24, 18, 20), (50, 38, 36), (80, 60, 50), (108, 86, 68)], base=0.6, crack=0.0, min=0, noise=0.2),
    "string": dict(ramp=[(140, 140, 130), (186, 188, 176), (220, 222, 208)], base=0.7, crack=0.0, min=0),
}, "noses": []}


def hood_pts(y, rx, rz, cz, phis, apex=None):
    pts = []
    for ph in phis:
        a = math.radians(ph)
        pts.append(V(rx * math.sin(a), y, cz - rz * math.cos(a)))
    return pts


def strip(bone, mat, x, ytop, ybot, z, w, flare=0.3, nrow=2, tilt=0.0, ref=None):
    """垂れるひげ板の帯: 先が尖る。"""
    rows = []
    for i in range(nrow + 1):
        t = i / nrow
        y = ytop + (ybot - ytop) * t
        hw = w * (1 - 0.8 * t ** 2.2) + flare * t
        zz = z + tilt * t
        if i == nrow:
            rows.append([V(x, y, zz), V(x, y, zz)])
        else:
            rows.append([V(x - hw, y, zz), V(x + hw, y, zz)])
    # 最後の行は 1 点 → 三角形 (2 列を 1 列に)
    pts = [rows[i] for i in range(nrow + 1)]
    return grid(bone, mat, pts, ref if ref is not None else (x, (ytop + ybot) / 2, z - 5))


def build():
    F = []
    # ------------------------------------------------------------ 顔 (頭巾の奥の小さな頭蓋)
    R = [ring(30.2, 1.5, -2.2, 2.0, 0.0), ring(29.5, 2.2, -3.3, 2.4, 0.0), ring(28.1, 2.4, -4.6, 2.4, -0.1),
         ring(26.8, 1.7, -5.8, 2.0, -0.1), ring(25.6, 0.9, -5.2, 1.3, 0.0)]
    for s, k in ((1, 1), (-1, 7)):
        R[2][k] += V(-s * 0.3, -0.1, 1.2)
        R[1][k] += V(s * 0.1, 0.3, -0.9)
    R[3][0] += V(0, 0.2, -0.3)
    eyes = [dict(c=R[2][1] + V(0, 0, -0.2), rx=0.9, ry=0.55, pr=(0.5, 0.3), tilt=0.2),
            dict(c=R[2][7] + V(0, 0, -0.2), rx=0.9, ry=0.55, pr=(0.5, 0.3), tilt=0.2)]
    F += loft("skull", "bone", R, caps=("bone", "dark"))
    for s in (-1, 1):
        for z in (-5.2, -3.6):
            F += spike("skull", "tooth", (s * 0.95, 25.7, z), (0, -1, 0), 1.3, 0.38, sides=3, rings=1)
    F += tube("skull", "bone", [(-2.0, 25.2, 1.6), (-1.3, 24.5, -2.4), (0, 24.4, -5.0), (1.3, 24.5, -2.4), (2.0, 25.2, 1.6)],
              ra=[0.55, 0.6, 0.5, 0.6, 0.55], rb=[0.5, 0.5, 0.45, 0.5, 0.5], sides=4, hint=(0, 1, 0),
              axis_fn=lambda p: np.array([0, p[1] + 0.3, p[2] + 2.0]), hide_cos=0.7)
    F += tube("skull", "bone", [(0, 23.0, 1.0), (0, 24.6, 1.0)], ra=[1.0, 0.85], rb=[1.0, 0.85], sides=4, hint=(1, 0, 0))
    # ------------------------------------------------------------ 頭巾 (ひげ板を段に重ねる)
    phis = [62, 105, 150, 210, 255, 298]
    tiers = [(24.4, 4.5, 4.5, 0.2), (26.6, 4.2, 4.2, 0.2), (28.8, 3.7, 3.8, 0.3), (31.0, 3.0, 3.1, 0.5), (33.0, 1.9, 2.0, 1.0),
             (35.0, 0.7, 0.9, 2.2)]
    for i in range(len(tiers) - 1):
        y0, rx0, rz0, c0 = tiers[i]
        y1, rx1, rz1, c1 = tiers[i + 1]
        top = hood_pts(y1 + 0.8, rx1 - 0.15, rz1 - 0.15, c1 + 0.1, phis)      # 次の段が下の段の外側へ重なる
        bot = hood_pts(y0, rx0 + 0.15, rz0 + 0.15, c0, phis)
        F += grid("skull", "baleen", [top, bot], (0, (y0 + y1) / 2, c0 - 0.3 + 0.0) if False else (0, (y0 + y1) / 2, c0))
    # 頭巾の先: 後ろへ反る尖り
    F += spike("skull", "baleen", (0, 34.8, 2.2), (0, 0.5, 1.0), 6.0, 0.9, sides=4, rings=2, bend=(0, -1.8, 0), hint=(1, 0, 0))
    # ------------------------------------------------------------ 胴
    F += tube("spine", "bone", [(0, 14.4, 1.6), (0, 17.0, 2.2), (0, 19.6, 2.2), (0, 22.0, 1.4)], ra=[1.2, 1.35, 1.1, 1.0],
              rb=[1.1, 1.2, 1.0, 0.9], sides=4, hint=(1, 0, 0))
    zc, rz = -0.4, 2.2
    for s in (-1, 1):
        for (y, rx) in ((20.8, 2.9), (18.6, 3.2), (16.4, 2.9)):
            pts = []
            for ph in (15, 90, 170):
                a = math.radians(ph)
                pts.append((s * rx * math.sin(a), y - 1.4 * (ph / 170.0), zc + rz * math.cos(a)))
            F += tube("spine", "bone", pts, ra=[0.9, 0.95, 0.7], rb=[0.7, 0.75, 0.55], sides=3, hint=(0, 1, 0),
                      axis_fn=lambda p: np.array([0, p[1], zc]), hide_cos=0.45, ridge_out=True)
    F += tube("spine", "bone", [(0, 21.0, -2.6), (0, 14.6, -2.9)], ra=[1.0, 0.7], rb=[0.8, 0.55], sides=4, hint=(1, 0, 0),
              axis_fn=lambda p: np.array([0, p[1], p[2] + 2.0]), hide_cos=0.5)
    F += ellipsoid("spine", "bone", (0, 12.6, 0.6), (3.0, 1.5, 1.8), segs=4, rings=1)
    # 前垂れ (胸から腰まで 3 枚) と背のマント
    for x, yb, w in ((0, 11.6, 1.0), (-2.4, 14.0, 0.8), (2.4, 14.0, 0.8)):
        F += strip("spine", "fibre", x, 21.2, yb, -3.5, w, flare=0.35, nrow=3, tilt=-0.7)
    for x, yb in ((-2.4, 11.0), (0, 8.8), (2.4, 11.0)):
        F += strip("spine", "fibre", x, 22.4, yb, 3.7, 1.2, flare=0.45, nrow=3, tilt=1.2, ref=(x, 17, 0))
    # ------------------------------------------------------------ 腕
    for nm, s in (("l", -1), ("r", 1)):
        a, fa = f"arm_{nm}", f"forearm_{nm}"
        for i, (dy, sc, tilt) in enumerate(((0.0, 1.0, 18), (-1.5, 0.82, 24), (-3.0, 0.62, 30))):    # 肩の重ね板
            F += ellipsoid(a, "baleen", (s * (3.8 + 0.4 * i), 22.0 + dy * 0.45, 0.0), (2.5 * sc, 0.55, 2.0 * sc), segs=4, rings=1,
                           rot=rot_z(-s * tilt))
        F += tube(a, "bone", [(s * 3.0, 20.4, 0), (s * 3.1, 18.4, 0.1), (s * 3.2, 16.6, 0.3)], ra=[0.85, 0.75, 0.9],
                  rb=[0.85, 0.75, 0.9], sides=5, hint=(1, 0, 0))
        F += tube(fa, "bone", [(s * 3.2, 16.5, 0.3), (s * 3.2, 14.0, -0.1), (s * 3.2, 11.8, -0.3)], ra=[0.9, 0.65, 0.75],
                  rb=[0.9, 0.65, 0.75], sides=5, hint=(1, 0, 0))
        F += tube(fa, "baleen", [(s * 3.2, 15.6, 0.0), (s * 3.2, 13.2, -0.3)], ra=[1.6, 1.3], rb=[1.5, 1.2], sides=4, hint=(1, 0, 0), phase=0,
                  caps=(False, False))
        F += ellipsoid(fa, "bone", (s * 3.2, 11.3, -0.3), (0.9, 0.8, 0.9), segs=4, rings=1)
        n = 3 if nm == "l" else 4
        for i in range(n):
            dx = (i - (n - 1) / 2) * 0.6
            F += spike(fa, "bone", V(s * 3.2 + dx * 0.6, 11.0, -0.3), V(dx * 0.12, -0.5, -1.0 if nm == "l" else -0.4), 3.2, 0.42,
                       sides=3, rings=1, bend=(0, -0.6, 0))
    # ------------------------------------------------------------ 脚 (細く真っ直ぐ、膝に板)
    for nm, s in (("l", -1), ("r", 1)):
        b = f"leg_{nm}"
        F += tube(b, "bone", [(s * 1.4, 10.8, 0.1), (s * 1.5, 8.0, -0.3), (s * 1.5, 5.8, -0.6)], ra=[1.1, 0.95, 0.9], rb=[1.2, 1.0, 0.9],
                  sides=4, hint=(1, 0, 0))
        F += tube(b, "bone", [(s * 1.5, 5.8, -0.6), (s * 1.5, 3.4, -0.2), (s * 1.5, 1.4, 0.0)], ra=[0.9, 0.7, 0.85], rb=[0.9, 0.7, 0.85],
                  sides=4, hint=(1, 0, 0))
        F += ellipsoid(b, "baleen", (s * 1.5, 5.8, -1.3), (1.5, 1.3, 0.9), segs=4, rings=1)
        F += tube(b, "bone", [(s * 1.5, 1.4, 0.3), (s * 1.5, 0.7, -1.4), (s * 1.5, 0.4, -3.6)], ra=[0.8, 0.8, 0.1], rb=[0.7, 0.5, 0.1],
                  sides=4, hint=(1, 0, 0))
        F += strip(b, "fibre", s * 2.9, 10.8, 5.6, 0.3, 0.6, flare=0.2, nrow=2, tilt=0.0, ref=(s * 9.0, 8, 0.3))
    # ------------------------------------------------------------ 弓: 薄い積層の長弓
    B = []
    for sy in (1, -1):
        B += tube("bow", "baleen", [(0.25, sy * 1.8, 0.0), (0.25, sy * 4.2, -0.35), (0.25, sy * 6.4, -0.95), (0.25, sy * 7.6, -1.85)],
                  ra=[0.95, 0.9, 0.7, 0.3], rb=[0.4, 0.4, 0.35, 0.2], sides=4, hint=(1, 0, 0), phase=0)
        for dx in (-1, 1):                                     # 先の小さな鰭
            B += poly("bow", "bone", [(0.25, sy * 7.6, -1.85), (0.25 + dx * 1.7, sy * 8.2, -2.1), (0.25 + dx * 0.4, sy * 6.5, -1.2)],
                      (0.25, sy * 5, -3))
        B += tube("bow", "wrap", [(0.25, sy * 5.1, -0.55), (0.25, sy * 5.9, -0.8)], ra=[0.95, 0.95], rb=[0.55, 0.55], sides=4, hint=(1, 0, 0), phase=0,
                  caps=(True, True))
    B += tube("bow", "wrap", [(0.25, -2.0, 0.0), (0.25, 2.0, 0.0)], ra=[1.0, 1.0], rb=[0.8, 0.8], sides=4, hint=(1, 0, 0), phase=0, caps=(True, True))
    B += poly("bow", "bone", [(1.2, 1.2, 0.2), (1.2, -0.4, 0.2), (0.25, -0.6, -0.8), (0.25, 1.0, -0.8)], (-3, 0, 0))   # 矢当て
    for sy, bn in ((1, "string_up"), (-1, "string_lo")):
        B += tube(bn, "string", [(0.25, sy * 7.0, -1.75), (0.25, 0.0, -1.75)], ra=[0.3, 0.3], sides=3, hint=(1, 0, 0))
    return F, B, eyes
HEAD_CENTER, HEAD_SCALE = (0, 29.0, -0.5), 12.5
