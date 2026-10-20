"""余燼の装飾品 5 品 x 3 案。玄武岩の黒い板 (割れ目が光る)・鍛冶の鉄、光は橙 (武器 A 割れ板と同じ言葉)。
A = 割れ板 (黒い板が割れて光る) / B = 鉄枠 (鍛冶の鉄の枠が光る芯を抱く) / C = 岩塊 (積んだ岩と溶けた滴)。
どれも直立 (y 上)。広い面は ±Z。外套は上端 y = 0 から下へ垂れ、後ろ (+Z) へ少し開く。"""
from __future__ import annotations

from common import *
from em_kit import chain, ridge, band

TITAN = "ember"


def link(c, R=1.5, ax="z", mat="edge"):
    return torus(mat, c, R, 0.4, 0.4, n=6, k=3, ax=ax)


def drips(mat, xs, y, length=3.0, r=0.7, z=0.0):
    f = []
    for i, x in enumerate(xs):
        f += spike(BONE, mat, V(x, y, z), (0.05 * (i - 1), -1, 0), length - 0.6 * (i % 2), r, sides=4, rings=1, hint=(0, 0, 1))
    return f


# ====================================================================== 熾りの護符 (護符・レア)
def charm_a(r):
    # 黒い六角の板が斜めに割れ、割れ目が光る。鉄の帯が下をくくる
    hx = [(5.2, 0), (2.6, 4.5), (-2.6, 4.5), (-5.2, 0), (-2.6, -4.5), (2.6, -4.5)]
    left = [(1.0, 4.5), (-2.6, 4.5), (-5.2, 0), (-2.6, -4.5), (-1.0, -4.5)]
    right = [(2.0, 4.5), (2.6, 4.5), (5.2, 0), (2.6, -4.5), (0.8, -4.5)]
    f = extrude("main", left, 1.9)
    f += extrude("main", right, 1.9, c=(0.3, 0, 0))
    f += block("gem", (0.9, 0.2, 0.0), (1.4, 8.8, 1.2), rot=rot_z(-12))
    f += block("spine", (0, -2.5, 0), (10.6, 1.3, 2.4))
    f += link((0.5, 6.4, 0))
    f += chain((0.0, -3.2, 0.0), (0.6, -7.0, 0.0), 3, sag=0.3)                          # 鍛冶場の鎖 (下へ垂れる)
    if r in ("rare", "legend"):
        f += block("trim", (0, 4.7, 0), (3.0, 0.8, 2.2))
    return f


def charm_b(r):
    # 鍛冶の鉄の環に十字の桟、真ん中に光る芯
    f = torus("edge", (0, 0, 0), 4.6, 0.9, 1.1, n=6, k=4, ax="z")
    f += block("spine", (0, 0, 0), (9.4, 0.9, 1.0))
    f += block("spine", (0, 0, 0), (0.9, 9.4, 1.0))
    f += ell("gem", (0, 0, -0.2), (2.3, 2.3, 1.5), segs=6, rings=1)
    f += block("edge", (0, 6.2, 0), (2.4, 1.6, 2.0))
    f += link((0, 8.0, 0))
    if r in ("rare", "legend"):
        f += block("trim", (0, -5.8, 0), (2.6, 0.8, 2.0))
    return f


def charm_c(r):
    f = block("main", (0, -2.6, 0), (7.6, 3.4, 5.6), rot=rot_y(10) @ rot_z(3), taper=(0.9, 0.9))
    f += block("main", (-0.4, 1.4, 0), (6.4, 3.6, 5.0), rot=rot_y(-16) @ rot_z(-5), taper=(0.8, 0.8))
    f += block("main", (0.4, 4.7, 0), (4.0, 3.0, 3.6), rot=rot_y(24), taper=(0.7, 0.7))
    f += block("gem", (0, -0.6, 0), (6.4, 0.7, 4.4), rot=rot_y(-4))
    f += drips("gem", (-2.2, 0.0, 2.2), -4.4, 3.0, 0.7, z=-1.6)
    f += link((0.4, 7.8, 0))
    if r in ("rare", "legend"):
        f += block("trim", (0, 2.9, 0), (6.8, 0.7, 5.6), rot=rot_y(-16))
    return f


# ====================================================================== 熾火の外套 (装飾・レア・背中)
def cape(mat_out, mat_in, rows, zs, w_top, w_hem, jag=1.0):
    """上端 y=0 から rows 行ぶん下へ垂れる布。zs は行ごとの後ろ (+Z) への開き。裾はぎざぎざ。"""
    n = len(zs)
    cols = 5
    pts = []
    for i in range(n):
        y = -rows * i / (n - 1)
        w = w_top + (w_hem - w_top) * i / (n - 1)
        row = []
        for j in range(cols):
            x = -w + 2 * w * j / (cols - 1)
            yy = y - (jag * (1 if j % 2 else -0.4) if i == n - 1 else 0)
            row.append(V(x, yy, zs[i]))
        pts.append(row)
    inner = [[p + V(0, 0, -0.5) for p in row] for row in pts]
    outer = grid(BONE, mat_out, pts, V(0, -rows / 2, -10))
    lin = grid(BONE, mat_in, inner, V(0, -rows / 2, 10))
    return outer + lin


def mantle_a(r):
    f = cape("acc", "wrap", 17.0, [0.4, 1.0, 1.8, 2.8], 6.2, 8.0)
    for sx in (-1, 1):
        f += extrude("main", [(-3.0, -0.6), (3.2, 0.2), (2.6, 2.6), (-2.4, 3.0), (-3.6, 1.2)], 2.2,
                     c=(sx * 7.6, 0.6, -0.4), rot=rot_z(-sx * 14) @ rot_y(sx * 20))
        f += block("gem", (sx * 7.6, 1.4, -1.7), (3.4, 0.6, 0.5), rot=rot_z(-sx * 14))
        f += ridge((sx * 6.0, 2.6, 0.2), (0, 1, 0), 2, 2.6, (sx * 2.4, 0.0, 0.0), 0.8)       # 肩の背びれ
    f += block("spine", (0, 0.6, -1.2), (3.2, 1.6, 1.4))
    f += block("gem", (0, 0.6, -2.0), (1.0, 1.0, 0.4))
    if r in ("rare", "legend"):
        f += block("trim", (0, -4.0, 1.1), (13.0, 0.7, 1.0))
    return f


def mantle_b(r):
    # 鉄と黒い板の鱗の外套: 3 段の板が重なって垂れる
    f = []
    for row in range(3):
        y = -3.0 - 5.2 * row
        for k in range((4, 4, 2)[row]):
            n = (4, 4, 2)[row]
            x = (k - (n - 1) / 2) * (3.8 + 0.4 * row)
            f += block("main" if (k + row) % 2 == 0 else "edge", (x, y, 0.5 + 0.9 * row + 0.3 * (k % 2)),
                       (3.6, 5.8, 1.2), rot=rot_z(((k - (n - 1) / 2) * -5)) @ rot_x(-8), taper=(0.8, 1.0))
    for sx in (-1, 1):
        f += block("spine", (sx * 6.2, 0.4, -0.2), (5.8, 1.6, 3.4), rot=rot_z(-sx * 22), taper=(0.9, 0.9))
        f += block("edge", (sx * 8.8, -0.9, -0.2), (3.4, 1.4, 3.0), rot=rot_z(-sx * 38))
    f += block("gem", (0, -0.4, -1.2), (2.4, 1.0, 0.6))
    if r in ("rare", "legend"):
        f += block("trim", (0, 0.8, 0), (8.0, 0.7, 2.2))
    return f


def mantle_c(r):
    f = cape("acc", "wrap", 12.0, [0.4, 1.1, 2.2], 5.8, 8.4, jag=1.8)
    for sx in (-1, 1):
        f += block("main", (sx * 7.4, 1.4, -0.2), (5.4, 3.8, 4.8), rot=rot_z(-sx * 16) @ rot_y(sx * 12), taper=(0.8, 0.8))
        f += block("main", (sx * 8.8, 4.0, -0.3), (3.4, 3.0, 3.4), rot=rot_z(-sx * 8) @ rot_y(-sx * 20), taper=(0.7, 0.7))
        f += drips("gem", (sx * 6.4, sx * 8.6), -0.6, 3.2, 0.7, z=-2.4)
    f += block("gem", (0, 0.4, -1.2), (1.4, 1.0, 0.6))
    if r in ("rare", "legend"):
        f += block("trim", (0, -3.0, 1.0), (11.0, 0.7, 1.0))
    return f


# ====================================================================== 余燼の灯 (装飾・伝説・腰)
def lantern_a(r):
    # 黒い板を 2 段に積み、間に割れ目の光。上に鉄の笠と環
    f = block("main", (0, -2.6, 0), (7.0, 3.6, 7.0), taper=(0.9, 0.9))
    f += block("main", (0.3, 2.0, 0), (6.2, 3.6, 6.2), rot=rot_y(24), taper=(0.9, 0.9))
    f += block("gem", (0.1, -0.2, 0), (6.0, 0.9, 6.0), rot=rot_y(12))
    f += block("spine", (0.3, 4.3, 0), (6.8, 1.2, 6.8), rot=rot_y(24), taper=(0.35, 0.35))
    f += link((0.3, 6.8, 0), R=1.4, ax="z")
    f += band((0, -4.2, 0), 3.7, 0.9, sides=4, mat="spine")                               # 太い鉄の帯 (枠)
    f += band((0.3, 3.0, 0), 3.5, 0.8, sides=4, mat="spine")
    if r in ("rare", "legend"):
        f += block("trim", (0, -4.7, 0), (7.6, 0.7, 7.6))
    if r == "legend":
        f += block("trim", (0.3, 3.6, 0), (7.0, 0.6, 7.0), rot=rot_y(24))
        f += block("gem", (0.3, 2.0, -3.2), (1.0, 2.4, 0.4), rot=rot_y(24))
    return f


def lantern_b(r):
    f = []
    for sx in (-2.8, 2.8):
        for sz in (-2.8, 2.8):
            f += prism("spine", (sx, -4.2, sz), (sx, 3.6, sz), 0.7, 0.7, sides=4, caps=(True, True), hint=(1, 0, 0))
    f += block("edge", (0, 4.3, 0), (7.6, 1.4, 7.6), taper=(0.35, 0.35))
    f += block("edge", (0, -4.7, 0), (7.4, 1.0, 7.4))
    f += ell("gem", (0, -0.2, 0), (2.4, 3.0, 2.4), segs=6, rings=2)
    f += link((0, 6.9, 0), R=1.4, ax="z")
    if r in ("rare", "legend"):
        f += block("trim", (0, -0.2, 0), (6.6, 0.7, 6.6))
    if r == "legend":
        f += block("trim", (0, 3.4, 0), (6.6, 0.6, 6.6))
    return f


def lantern_c(r):
    f = block("main", (0, -2.4, 0), (7.4, 4.2, 6.4), rot=rot_y(8), taper=(0.9, 0.9))
    f += block("main", (-0.3, 1.7, 0), (6.4, 3.6, 5.6), rot=rot_y(-14), taper=(0.75, 0.8))
    f += block("gem", (0, -1.4, -3.0), (3.8, 3.2, 1.0), rot=rot_y(8), taper=(0.8, 0.8))
    for k, x in enumerate((-2.4, -0.8, 0.8, 2.4)):
        f += spike(BONE, "edge", V(x, 3.4, 0.3 * (k % 2)), (0.1 * x, 1, 0), 3.2 - 0.6 * (k % 2), 0.8, sides=4, rings=1, hint=(0, 0, 1))
    f += link((0, 7.4, 0), R=1.3, ax="z")
    if r in ("rare", "legend"):
        f += block("trim", (0, -4.8, 0), (7.6, 0.7, 6.8), rot=rot_y(8))
    if r == "legend":
        f += block("gem", (0, 1.4, -2.8), (2.0, 1.4, 0.8), rot=rot_y(-14))
    return f


# ====================================================================== 熾火の角杯 (飲み物・手に持つ)
def horn_path():
    return [V(-5.5, -6.0, 0), V(-3.4, -2.8, 0), V(-0.4, 0.2, 0), V(3.0, 2.8, 0), V(6.0, 3.8, 0)]


def horn_liquid(mouth_p, t):
    t = lpkit.nrm(t)
    return prism("gem", tuple(mouth_p - t * 0.7), tuple(mouth_p - t * 0.1), 2.2, 2.2, sides=6, caps=(True, True), hint=(0, 0, 1))


def horn_a(r):
    p = horn_path()
    f = curve("main", p, [0.9, 1.5, 2.1, 2.7, 3.1], sides=6, caps=(True, False), hint=(0, 0, 1), phase=0)
    f += horn_liquid(p[-1], p[-1] - p[-2])
    f += prism("edge", tuple(p[-1] - V(2.2, 0.6, 0)), tuple(p[-1] + V(0.4, 0.1, 0)), 3.3, 3.3, sides=6, caps=(False, False), hint=(0, 0, 1))
    f += prism("spine", tuple(p[1]), tuple(p[1] + V(1.0, 1.0, 0)), 1.8, 1.8, sides=6, caps=(False, False), hint=(0, 0, 1))
    if r in ("rare", "legend"):
        f += block("trim", tuple(p[2]), (1.2, 4.0, 4.0), rot=rot_z(40))
    return f


def horn_b(r):
    p = horn_path()
    f = curve("edge", p, [0.9, 1.5, 2.1, 2.7, 3.1], sides=6, caps=(True, False), hint=(0, 0, 1), phase=0)
    f += horn_liquid(p[-1], p[-1] - p[-2])
    for i in (1, 2, 3):
        t = lpkit.nrm(p[i + 1] - p[i - 1])
        f += prism("spine", tuple(p[i] - t * 0.5), tuple(p[i] + t * 0.5), [2.0, 2.6, 3.1][i - 1], [2.0, 2.6, 3.1][i - 1], sides=6,
                   caps=(False, False), hint=(0, 0, 1))
    f += link((-1.0, 3.0, 0), R=1.6, ax="z")
    if r in ("rare", "legend"):
        f += block("trim", tuple(p[-1] + V(0.2, 0, 0)), (1.0, 6.4, 6.4), rot=rot_z(20))
    return f


def horn_c(r):
    # 岩の杯: 積んだ岩の杯と柄、縁から溶けた滴
    f = block("main", (0, -5.0, 0), (7.4, 2.0, 5.6), rot=rot_y(10), taper=(0.9, 0.9))
    f += block("main", (0.2, -2.6, 0), (3.2, 3.0, 3.0), rot=rot_y(-20), taper=(1.2, 1.2))
    f += block("main", (0, 1.4, 0), (8.6, 5.2, 6.8), rot=rot_y(-8), taper=(1.15, 1.15), skip=("u",))
    f += prism("gem", (0, 3.7, 0), (0, 4.1, 0), 3.4, 3.4, sides=6, caps=(True, True), hint=(1, 0, 0))
    f += drips("gem", (-3.4, 1.0), 4.0, 3.4, 0.8, z=-2.8)
    f += block("edge", (-4.6, 0.8, 0), (1.2, 2.4, 2.0), rot=rot_z(20))
    if r in ("rare", "legend"):
        f += block("trim", (0, -3.7, 0), (4.0, 0.7, 3.8))
    return f


# ====================================================================== 燠の灯 (吊り灯のブロック・光 12)
def lamp_a(r):
    # 燠の殻が割れて、中の火が見える。鉄の鎖で吊る
    f = loft(BONE, "main", [ring(-5.9, 2.0, 2.0, 2.0, n=8), ring(-3.9, 4.6, 4.6, 4.6, n=8), ring(-0.9, 5.4, 5.4, 5.4, n=8)],
             caps=("main", False))
    f += loft(BONE, "main", [ring(5.9, 2.0, 2.0, 2.0, n=8), ring(3.9, 4.6, 4.6, 4.6, n=8), ring(0.9, 5.4, 5.4, 5.4, n=8)],
              caps=("main", False))
    f += ell("gem", (0, 0, 0), (3.4, 2.2, 3.4), segs=6, rings=1)
    f += link((0, 8.3, 0), R=1.5, ax="z")
    f += prism("edge", (0, 5.9, 0), (0, 6.9, 0), 0.5, 0.5, sides=4, caps=(False, False), hint=(1, 0, 0))
    return f


def lamp_b(r):
    f = loft(BONE, "spine", [ring(-0.5, 5.6, 5.6, 5.6, n=6), ring(-3.2, 4.4, 4.4, 4.4, n=6), ring(-6.0, 1.6, 1.6, 1.6, n=6)],
             caps=(False, "spine"))
    f += torus("edge", (0, -0.5, 0), 5.6, 0.5, 0.5, n=6, k=3, ax="y")
    f += ell("gem", (-1.2, 0.2, 0.4), (2.2, 1.6, 2.2), segs=5, rings=1)
    f += ell("gem", (1.8, 0.3, -0.8), (1.8, 1.4, 1.8), segs=5, rings=1)
    for a in (90, 210, 330):
        x, z = math.cos(math.radians(a)) * 5.4, math.sin(math.radians(a)) * 5.4
        f += prism("edge", (x, -0.5, z), (0, 7.8, 0), 0.3, 0.3, sides=4, caps=(False, False), hint=(0, 0, 1))
    f += link((0, 8.9, 0), R=1.3, ax="z")
    return f


def lamp_c(r):
    f = block("main", (0, 1.4, 0), (8.0, 4.4, 6.6), rot=rot_y(14), taper=(0.9, 0.9))
    f += block("main", (-0.6, -2.2, 0), (6.4, 3.6, 5.6), rot=rot_y(-18), taper=(0.9, 0.9))
    f += block("main", (0.5, 4.6, 0), (4.0, 2.6, 3.6), rot=rot_y(30), taper=(0.7, 0.7))
    f += block("gem", (0, -4.2, 0), (5.0, 0.8, 4.2), rot=rot_y(-18))
    f += drips("gem", (-1.8, 0.2, 2.0), -4.4, 3.0, 0.7, z=0.0)
    f += link((0.5, 7.8, 0), R=1.5, ax="z")
    return f


ITEMS = dict(
    kindling_charm=dict(jp="熾りの護符", kind="charm", rar="rare", fn=dict(a=charm_a, b=charm_b, c=charm_c)),
    ember_mantle=dict(jp="熾火の外套", kind="worn", rar="rare", fn=dict(a=mantle_a, b=mantle_b, c=mantle_c)),
    ember_lantern=dict(jp="余燼の灯", kind="worn", rar="legend", fn=dict(a=lantern_a, b=lantern_b, c=lantern_c)),
    ember_horn=dict(jp="熾火の角杯", kind="held", rar="common", fn=dict(a=horn_a, b=horn_b, c=horn_c)),
    cinder_lamp=dict(jp="燠の灯 (吊り灯)", kind="held", rar="common", fn=dict(a=lamp_a, b=lamp_b, c=lamp_c)),
)
