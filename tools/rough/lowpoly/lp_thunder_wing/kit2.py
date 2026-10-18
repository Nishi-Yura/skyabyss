"""共通の小さな部品 (雲・輪・羽根の房)。"""
from kit import *  # noqa
from common import _mk  # noqa


def puff(bone, c, radii, seed, segs=7, rings=2, mat="cloud"):
    """雲のかたまり (ゆがんだ楕円体)"""
    c = np.asarray(c, float)

    def deform(p):
        h = hash1(np.round(abs(p[0]) * 3 + p[1] * 5 + p[2] * 7, 2) + seed, 2)
        return p * (0.82 + 0.4 * h)
    return ellipsoid(bone, mat, c, radii, segs=segs, rings=rings, deform=deform)


def shell_ring(sh, z, k=12, inset=0.0):
    cy, hw, ht, hb, p, cx = sh.params(z)
    out = []
    for i in range(k):
        a = 2 * math.pi * i / k
        sx, cs = math.sin(a), math.cos(a)
        x = cx + (hw - inset) * math.copysign(abs(sx) ** (2 / p), sx)
        y = cy + ((ht if cs >= 0 else hb) - inset) * math.copysign(abs(cs) ** (2 / p), cs)
        out.append((V(x, y, z), V(sx, cs, 0.0)))
    return out


def loop(c, rx, ry, k, p=2.0, plane="xy"):
    out = []
    for i in range(k + 1):
        a = 2 * math.pi * i / k
        sx, cs = math.sin(a), math.cos(a)
        u = rx * math.copysign(abs(sx) ** (2 / p), sx)
        v = ry * math.copysign(abs(cs) ** (2 / p), cs)
        out.append(c + (V(u, v, 0) if plane == "xy" else V(u, 0, v)))
    return out


def upper_pt(sh, z, x):
    """体の上面 (|x| = x の点)。x の符号で左右。戻り値: 点と外向きの法線"""
    cy, hw, ht, hb, p, cx = sh.params(z)
    ax = min(abs(x), hw * 0.98)
    dy = ht * (1 - (ax / hw) ** p) ** (1 / p)
    return sh.at(z, cy + dy, 1 if x >= 0 else -1)
