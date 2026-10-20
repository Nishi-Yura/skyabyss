"""雷翼の弓 (作り直し)。雷鳥の翼が弓の肢: 銅の肢の背 (+x) へ、焦げた風切羽が扇に重なる。握りは鋼 + 銅の巻きと輪。
弦 x -3.7 (休め)、握り x +5 (変えない)、上下の先 y -1 / 17。引き 3 段 (stage 1..3) で弦と矢が -x へ。
階級: 通常 = 肢 + 風切羽 3 枚ずつ / レア = 内側の雨覆い + 肢の輪 + 握りの輪 + 羽 4 枚 / 伝説 = 大きな翼 (外の長い風切羽 +2) + 先の叉 + 握りの翼。"""
from __future__ import annotations

from st_kit import *  # noqa: F401,F403
from st_kit import plume, torus, band, bolt, vein_x
from d_thunder import string_and_arrow, TIP_PULL, STRING_X, CY, mirrored_path
from paint import line_mask, spot_mask

TITAN = "thunder"
GRIP = (5.0, 8.0, 0.0)
NAME = "雷翼の弓"
ID = "thunder_wing_bow"
EDGE = ("edge",)
STAGES = 4


def limb(pull):
    return [(5.0, 10.6, 0), (4.3, 13.0, 0), (1.8 - pull * 0.5, 15.8, 0), (-2.4 - pull, 17.2, 0)]


def side_feathers(t, sg, pull):
    """上の肢 (sg=+1) の風切羽。下は y を CY で折り返す。"""
    f = []
    my = (lambda p: p) if sg > 0 else (lambda p: (p[0], 2 * CY - p[1], p[2]))
    z = (0.0, 0.5, 0.0, 0.5, 0.0)
    prim = [((4.8, 11.2), (12.0, 15.0), 1.15, -0.7),
            ((4.1, 12.8), (10.6, 17.4), 1.2, -0.6),
            ((2.8, 14.4), (7.0, 18.6), 1.1, -0.4)]
    if t == 1:
        prim.append(((5.2, 10.4), (11.4, 12.4), 1.0, -0.5))
    if t == 2:
        prim = [((5.0, 10.6), (14.6, 14.0), 1.3, -0.9)] + prim
    for i, (b, tp, w, bd) in enumerate(prim):
        zz = z[i % 5] * (1 if sg > 0 else -1)
        f += plume(my((b[0], b[1], zz)), my((tp[0] - pull * 0.1, tp[1], zz)), w, 0.45, bend=bd * sg, big=(t == 2 and i == 0),
                   scorch=1)
    if t >= 1:   # 雨覆い: 肢の背に重なる短い羽
        for (b, tp) in (((3.6, 13.6), (7.0, 15.4)),):
            f += plume(my((b[0], b[1], -0.6)), my((tp[0], tp[1], -0.6)), 0.8, 0.35, bend=-0.3 * sg, scorch=1)
    return f


def faces(r="common", stage=0):
    t = {"common": 0, "rare": 1, "legend": 2}[r]
    pull = TIP_PULL[stage]
    f = []
    # 握り: 鋼 + 銅の巻き + 輪の鍔
    f += prism("grip", (5.0, CY - 2.6, 0), (5.0, CY - 0.9, 0), 1.0, 1.15, sides=6, caps=(False, False), hint=(0, 0, 1))
    f += prism("wrap", (5.0, CY - 0.9, 0), (5.0, CY + 0.9, 0), 1.15, 1.3, sides=6, caps=(False, False), hint=(0, 0, 1))
    f += prism("grip", (5.0, CY + 0.9, 0), (5.0, CY + 2.6, 0), 1.15, 1.0, sides=6, caps=(False, False), hint=(0, 0, 1))
    f += block("trim", (5.0, CY + 2.9, 0), (2.4, 0.5, 2.4))
    f += block("trim", (5.0, CY - 2.9, 0), (2.4, 0.5, 2.4))
    f += block("gem", (6.3, CY, 0), (0.9, 1.2, 1.4), taper=(0.8, 0.8))
    up = limb(pull)
    lo = mirrored_path(up)
    for path in (up, lo):
        sg = 1 if path is up else -1
        f += curve("main", path, [1.0, 0.9, 0.7, 0.4], [1.1, 1.0, 0.8, 0.5], sides=4, caps=(False, False), hint=(0, 0, 1))
        px, py, _ = path[-1]
        f += side_feathers(t, sg, pull)
        if t >= 1:   # 肢の銅の輪 (雷鳥の脚環)
            for i in (2,):
                a, c = path[i - 1], path[i + 1]
                f += band(path[i], (c[0] - a[0], c[1] - a[1], 0), 1.35 if i == 1 else 1.2, 0.8, "trim")
    f += string_and_arrow(stage, r, ((up[-1][0], up[-1][1]), (lo[-1][0], lo[-1][1])))
    if t == 2:
        # 握りの翼 (握りの上下から大きな羽が背へ流れる) + 肢の先の外へ尖る銅の叉
        for sg in (1, -1):
            f += plume((6.2, CY + sg * 2.6, 0.0), (12.6, CY + sg * 7.6, 0.0), 1.4, 0.5, bend=-1.0 * sg, big=False, scorch=1)
    return f


def _cx(y):
    yy = np.where(y > CY, y, 2 * CY - y)
    base = np.interp(yy, [10.6, 13.0, 15.8, 17.2], [5.0, 4.3, 1.8, -2.4])
    return base + vein_x(yy, 10.6, 0.35, 3.0)


def inlay(env, kind):
    if kind == "rare":
        return line_mask(env, ["main"], _cx, 11.2, 15.0, 0.42) | line_mask(env, ["main"], _cx, 1.0, 4.8, 0.42)
    if kind == "legend":
        return (line_mask(env, ["main"], _cx, 10.8, 16.8, 0.45) | line_mask(env, ["main"], _cx, 0.2, 5.2, 0.45)
                | spot_mask(env, ["gem"], (6.3, CY, 0), 1.8))
    if kind == "p5":
        return line_mask(env, ["main"], _cx, 10.8, 16.8, 0.42, dash=(2.2, 1.2)) | line_mask(env, ["main"], _cx, 0.2, 5.2, 0.42, dash=(2.2, 1.2))
    if kind == "p10":
        return (line_mask(env, ["main"], _cx, 10.6, 17.2, 0.5) | line_mask(env, ["main"], _cx, -0.2, 5.4, 0.5)
                | (np.isin(env["mats"], ["edge", "trim"]) & env["convex"]))
    return env["lit"] < -9


import wd2  # noqa: E402
wd2.install(ID, __name__)   # D2 (整え): 面取り + 輪 + 宝石 + 光る線
