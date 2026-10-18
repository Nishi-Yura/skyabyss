"""珊瑚憑き 2 回目の 3 案の共通部品 (枝珊瑚・昆布の布・管海綿など)。"""
from common import *  # noqa

KELP = [(14, 22, 44), (26, 40, 80), (44, 66, 118), (72, 98, 156), (108, 134, 190)]
GLASS = [(60, 150, 176), (110, 210, 226), (180, 244, 252)]
WOOD = [(20, 20, 28), (42, 38, 46), (70, 60, 64), (104, 88, 84)]
DARKR = [(6, 8, 14), (14, 16, 26), (28, 30, 42)]


def stag(bone, mat, base, d, length, r, branches=(), bend=(0, 0, 0), sides=4, rings=3, hint=(1, 0, 0)):
    """枝珊瑚: 根元から d へ伸びる先細りの幹と、途中から出る枝 (t, 向きの足し, 長さ, 半径[, 材質])。"""
    d = nrm(d)
    base = np.asarray(base, float)
    bend = np.asarray(bend, float)
    F = spike(bone, mat, base, d, length, r, sides=sides, rings=rings, bend=bend, hint=hint)
    for (t, add, ln, rr, *m) in branches:
        p = base + d * length * t + bend * t * t
        dd = nrm(nrm(d * length + 2 * bend * t) + np.asarray(add, float))
        F += spike(bone, (m[0] if m else mat), p, dd, ln, rr, sides=max(3, sides - 1), rings=1)
    return F


def cloth(bone, mat, rows, ref):
    """rows: 行ごとの (左, 右) の点の組。垂れ布。"""
    return grid(bone, mat, [[np.asarray(a, float), np.asarray(b, float)] for a, b in rows], ref)


def lumps(bone, mat, pts, flat=0.8, segs=5):
    F = []
    for p, rr in pts:
        F += ellipsoid(bone, mat, p, (rr, rr * flat, rr), segs=segs, rings=1)
    return F


def pipe(bone, mat, base, d, length, r, cap="dark", sides=5):
    """管海綿: 先が開いた筒 (先端に暗い蓋)。"""
    d = nrm(d)
    base = np.asarray(base, float)
    F = tube(bone, mat, [base, base + d * length * 0.55, base + d * length], ra=[r, r * 1.05, r * 0.85], rb=[r, r * 1.05, r * 0.85],
             sides=sides, hint=(1, 0, 0), phase=0)
    top = base + d * length
    h = np.array([1.0, 0, 0]) if abs(d[0]) < 0.9 else np.array([0, 0, 1.0])
    a = nrm(h - np.dot(h, d) * d)
    b = np.cross(d, a)
    ringp = [top + (a * math.cos(2 * math.pi * k / sides) + b * math.sin(2 * math.pi * k / sides)) * r * 0.85 * 0.98 for k in range(sides)]
    F += poly(bone, cap, ringp, base)
    return F
