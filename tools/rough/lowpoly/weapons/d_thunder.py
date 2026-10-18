"""雷翼の弓 3 案 (引き絵 4 段階つき)。銅・白い羽・紺の鋼、光は白青。
座標は今の item モデルと同じ向き: 弦が x = -3.7 (休め)、握りが x = +5、上下の先が y = -1 / 17。
引くと弦が -x へ寄り、矢が +x へ向く。"""
from __future__ import annotations

from wkit import *  # noqa: F401,F403
from wkit import block, blade, prism, curve, extrude, feather, mirror_x, remat, at, V, rot_x, rot_y, rot_z
from paint import line_mask, edge_mask, spot_mask

TITAN = "thunder"
GRIP = (5.0, 8.0, 0.0)
CY = 8.0
STAGES = 4
STRING_X = [-3.7, -5.2, -6.9, -8.6]   # 休め / 引き 0 / 1 / 2
TIP_PULL = [0.0, 0.8, 1.7, 2.6]       # 先が弦の側 (-x) へ寄る量


def mirrored_path(path):
    return [(x, 2 * CY - y, z) for x, y, z in path]


def string_and_arrow(stage, r, tips):
    f = []
    sx = STRING_X[stage]
    (tx_u, ty_u), (tx_l, ty_l) = tips
    if stage == 0:
        f += prism("cord", (tx_u, ty_u, 0), (tx_l, ty_l, 0), 0.22, 0.22, sides=4, caps=(False, False), hint=(0, 0, 1))
    else:
        f += prism("cord", (tx_u, ty_u, 0), (sx, CY, 0), 0.22, 0.22, sides=4, caps=(False, False), hint=(0, 0, 1))
        f += prism("cord", (tx_l, ty_l, 0), (sx, CY, 0), 0.22, 0.22, sides=4, caps=(False, False), hint=(0, 0, 1))
        # 矢: 軸 (+x へ)、鏃、羽
        x0, x1 = sx + 0.4, 15.6
        f += prism("grip", (x0, CY, 0), (x1, CY, 0), 0.38, 0.38, sides=4, caps=(False, False), hint=(0, 1, 0))
        f += spike(BONE, "edge", (x1, CY, 0), (1, 0, 0), 2.2, 0.8, sides=4, rings=1, hint=(0, 1, 0))
        f += at(extrude("acc", [(0, -1.1), (2.6, -0.5), (2.6, 0.5), (0, 1.1)], 0.3), None, (x0 + 0.3, CY, 0))
    return f


def grip_set(r):
    f = []
    f += prism("grip", (5.0, CY - 2.6, 0), (5.0, CY - 0.9, 0), 1.0, 1.15, sides=6, caps=(False, False), hint=(0, 0, 1))
    f += prism("wrap", (5.0, CY - 0.9, 0), (5.0, CY + 0.9, 0), 1.15, 1.3, sides=6, caps=(False, False), hint=(0, 0, 1))
    f += prism("grip", (5.0, CY + 0.9, 0), (5.0, CY + 2.6, 0), 1.15, 1.0, sides=6, caps=(False, False), hint=(0, 0, 1))
    f += block("trim", (5.0, CY + 2.9, 0), (2.4, 0.5, 2.4))
    f += block("trim", (5.0, CY - 2.9, 0), (2.4, 0.5, 2.4))
    f += block("gem", (6.2, CY, 0), (0.9, 1.2, 1.4), taper=(0.8, 0.8))
    return f


# ---------------------------------------------------------------- A 羽の剛弓
def limb_a(stage):
    pull = TIP_PULL[stage]
    return [(5.0, 10.6, 0), (4.6, 12.4, 0), (3.0, 14.8, 0), (0.4 - pull * 0.5, 16.6, 0), (-2.6 - pull, 17.4, 0)]


def a(r="common", stage=0):
    f = grip_set(r)
    up = limb_a(stage)
    lo = mirrored_path(up)
    for path in (up, lo):
        f += curve("spine", path, [1.0, 0.95, 0.8, 0.6, 0.35], [1.15, 1.05, 0.9, 0.7, 0.45], sides=4, caps=(False, False), hint=(0, 0, 1))
        px, py, _ = path[-1]
        sg = 1 if path is up else -1
        f += block("main", (px, py + sg * 0.3, 0), (1.2, 1.3, 1.5), taper=(0.7, 0.7), rot=None)
        # 腹の羽: 3 枚 (外側 +x へ流れる)
        for k, (i, ln, ang) in enumerate(((1, 5.6, 62), (2, 5.8, 52), (3, 5.0, 40))):
            bx, by, _ = path[i]
            f += feather("acc", (bx + 0.7, by + (0 if sg else 0), 0), (bx + 0.7 + math.cos(math.radians(ang)) * ln * 0.55,
                                                                       by + sg * math.sin(math.radians(ang)) * ln, 0), 1.25, 0.5, big=False)
    f += string_and_arrow(stage, r, ((up[-1][0], up[-1][1]), (lo[-1][0], lo[-1][1])))
    if r in ("rare", "legend"):
        f += block("trim", (3.4, 14.3, 0), (1.6, 0.9, 1.8))
        f += block("trim", (3.4, 1.7, 0), (1.6, 0.9, 1.8))
        f += block("trim", (5.0, CY + 3.6, 0), (2.4, 0.8, 2.4))
        f += block("trim", (5.0, CY - 3.6, 0), (2.4, 0.8, 2.4))
    if r == "legend":
        # 握りの上下に大きな羽の翼 (腹の外へ)
        for sg in (1, -1):
            f += feather("acc", (6.0, CY + sg * 2.4, 0), (10.8, CY + sg * 6.2, 0), 1.7, 0.6, big=True)
            f += feather("acc", (6.0, CY + sg * 2.0, 0), (11.4, CY + sg * 2.8, 0), 1.3, 0.6, big=False)
    return f


def inlay_a(env, kind):
    # 光の筋: 握りの近くの鋼の肢の稜
    if kind == "rare":
        return spot_mask(env, ["main"], (-2.6, 17.4, 0), 1.2) | spot_mask(env, ["main"], (-2.6, -1.4, 0), 1.2)
    if kind == "legend":
        return spot_mask(env, ["acc"], (9.0, 13.0, 0), 1.6) | spot_mask(env, ["acc"], (9.0, 3.0, 0), 1.6)
    if kind == "p5":
        return line_mask(env, ["spine"], lambda y: 3.8 + 0 * y, 10.5, 14.5, 0.5) | line_mask(env, ["spine"], lambda y: 3.8 + 0 * y, 1.5, 5.5, 0.5)
    if kind == "p10":
        return (np.isin(env["mats"], ["spine"]) & env["convex"]) & (np.abs(env["N"][..., 2]) > 0.3)
    return env["lit"] < -9


# ---------------------------------------------------------------- B 翼弓 (風切羽の重なり)
def b(r="common", stage=0):
    f = grip_set(r)
    pull = TIP_PULL[stage]
    up = [(5.0, 10.6, 0), (4.2, 13.0, 0), (1.6 - pull * 0.5, 15.8, 0), (-2.4 - pull, 17.2, 0)]
    lo = mirrored_path(up)
    for path in (up, lo):
        sg = 1 if path is up else -1
        f += curve("main", path, [0.9, 0.8, 0.6, 0.35], [1.0, 0.9, 0.7, 0.45], sides=4, caps=(False, False), hint=(0, 0, 1))
        # 風切羽: 弓の腹 (+x) へ重なって 3 枚、外ほど長い
        for k, (base_i, tipv) in enumerate(((0, (9.2, 17.4)), (1, (8.0, 15.6)), (2, (4.6, 14.2)))):
            bx, by, _ = path[base_i + 0]
            tx, ty = tipv
            f += feather("spine" if k != 1 else "acc", (bx + 0.2, by if sg > 0 else by, 0),
                         (tx - pull * 0.2, ty if sg > 0 else 2 * CY - ty, 0), 1.2 + 0.2 * k, 0.55, big=(k == 1))
        px, py, _ = path[-1]
        f += block("main", (px, py + sg * 0.3, 0), (1.2, 1.3, 1.5), taper=(0.7, 0.7))
    f += string_and_arrow(stage, r, ((up[-1][0], up[-1][1]), (lo[-1][0], lo[-1][1])))
    if r in ("rare", "legend"):
        for sg in (1, -1):
            f += feather("acc", (3.2, CY + sg * 6.2, 0), (-0.4, CY + sg * 9.0, 0), 0.9, 0.5, big=False)
        f += block("trim", (3.8, 13.0, 0), (1.6, 0.9, 1.8))
        f += block("trim", (3.8, 3.0, 0), (1.6, 0.9, 1.8))
    if r == "legend":
        for sg in (1, -1):
            f += feather("spine", (7.2, CY + sg * 1.8, 0), (13.0, CY + sg * 5.8, 0), 1.8, 0.6, big=True)
    return f


def inlay_b(env, kind):
    if kind == "rare":
        return spot_mask(env, ["main"], (-2.4, 17.2, 0), 1.2) | spot_mask(env, ["main"], (-2.4, -1.2, 0), 1.2)
    if kind == "legend":
        return spot_mask(env, ["acc"], (9.0, 14.0, 0), 1.6) | spot_mask(env, ["acc"], (9.0, 2.0, 0), 1.6)
    if kind == "p5":
        return line_mask(env, ["main"], lambda y: 3.0 + 0 * y, 10.5, 15.0, 0.5) | line_mask(env, ["main"], lambda y: 3.0 + 0 * y, 1.0, 5.5, 0.5)
    if kind == "p10":
        return (np.isin(env["mats"], ["main"]) & env["convex"]) & (np.abs(env["N"][..., 2]) > 0.3)
    return env["lit"] < -9


# ---------------------------------------------------------------- C 雷の弓 (稲妻の折れ)
def c(r="common", stage=0):
    f = grip_set(r)
    pull = TIP_PULL[stage]
    up = [(5.0, 10.6, 0), (2.6, 12.8, 0), (5.0, 13.4, 0), (0.6, 16.0, 0), (-2.8 - pull, 17.2, 0)]
    lo = mirrored_path(up)
    for path in (up, lo):
        sg = 1 if path is up else -1
        f += curve("main", path, [1.05, 0.95, 0.85, 0.7, 0.35], [1.15, 1.05, 0.95, 0.8, 0.45], sides=4, caps=(False, False), hint=(0, 0, 1))
        # 折れ目の光る石
        for i in (1, 3):
            bx, by, _ = path[i]
            f += block("gem", (bx, by, 0), (1.0, 1.0, 1.5), taper=(0.7, 0.7))
        px, py, _ = path[-1]
        f += block("spine", (px, py + sg * 0.3, 0), (1.2, 1.3, 1.5), taper=(0.7, 0.7))
        # 先の羽房 (短い 2 枚)
        f += feather("acc", (px + 0.4, py, 0), (px + 3.0, py + sg * 1.8, 0), 0.8, 0.45, big=False)
    f += string_and_arrow(stage, r, ((up[-1][0], up[-1][1]), (lo[-1][0], lo[-1][1])))
    if r in ("rare", "legend"):
        for sg in (1, -1):
            f += spike(BONE, "acc", (4.6, CY + sg * 5.0, 0), (1, sg * 0.7, 0), 3.4, 0.8, sides=4, rings=2, hint=(0, 0, 1))
        f += block("trim", (4.2, 13.0, 0), (1.6, 0.9, 1.8))
        f += block("trim", (4.2, 3.0, 0), (1.6, 0.9, 1.8))
        f += block("trim", (5.0, CY + 3.6, 0), (2.4, 0.8, 2.4))
        f += block("trim", (5.0, CY - 3.6, 0), (2.4, 0.8, 2.4))
    if r == "legend":
        for sg in (1, -1):
            f += feather("acc", (5.6, CY + sg * 3.0, 0), (10.8, CY + sg * 7.0, 0), 1.6, 0.6, big=True)
            f += block("gem", (6.6, CY + sg * 4.4, 0), (0.9, 0.9, 1.3), taper=(0.7, 0.7))
    return f


def inlay_c(env, kind):
    if kind == "rare":
        return spot_mask(env, ["main"], (4.6, 13.2, 0), 1.4) | spot_mask(env, ["main"], (4.6, 2.8, 0), 1.4)
    if kind == "legend":
        return spot_mask(env, ["main"], (1.2, 15.8, 0), 1.4) | spot_mask(env, ["main"], (1.2, 0.2, 0), 1.4)
    if kind == "p5":
        return (np.isin(env["mats"], ["main"]) & env["convex"]) & (env["Y"] % 2.0 < 0.9) & (np.abs(env["N"][..., 2]) > 0.3)
    if kind == "p10":
        return (np.isin(env["mats"], ["main"]) & env["convex"]) & (np.abs(env["N"][..., 2]) > 0.3)
    return env["lit"] < -9


OPTS = dict(a=(a, inlay_a, ()), b=(b, inlay_b, ()), c=(c, inlay_c, ()))
