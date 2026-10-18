"""雷翼・余燼の武器のうち、build_all の 4 武器 (雷翼の弓 B・余燼の戦斧 A) 以外の 4 つ。
雷獣の爪・羽守の鉤槍 (雷翼: 銅・白い羽・紺の鋼、広がる輪郭) と
鍛え手の大鎚・灰被りの火かき棒 (余燼: 玄武岩・光る割れ目・鍛冶の鉄、太い一つの輪郭)。
座標は今の item モデルと同じ向き (x = 0・z = 0 が中心、+Y が先)。"""
from __future__ import annotations

from wkit import *  # noqa: F401,F403
from wkit import block, blade, prism, curve, extrude, feather, mirror_x, remat, at, V, rot_x, rot_y, rot_z
from paint import line_mask, edge_mask, spot_mask


def _off(env):
    return env["lit"] < -9


# ================================================================ 雷獣の爪 (雷翼) y -0.5..16.5, x ±4.6
CLAW_GRIP = (0.0, 2.5, 0.0)


def claw_curve(y):
    """中央の爪の反り (y から中心の x)"""
    return 0.05 * np.maximum(0.0, y - 6.0) ** 2 / 2.0


def claw(r="common"):
    f = []
    # 握り: 紺の鋼 + 銅の巻き、柄頭
    f += prism("grip", (0, 0.2, 0), (0, 5.0, 0), 0.9, 0.9, sides=6, caps=(False, False), hint=(0, 0, 1))
    f += prism("wrap", (0, 1.2, 0), (0, 3.8, 0), 1.05, 1.05, sides=6, caps=(False, False), hint=(0, 0, 1))
    f += block("spine", (0, -0.1, 0), (2.4, 0.9, 2.2), taper=(0.7, 0.7), skip=("u",))
    # 鍔: 銅の小さな板 + 光る石
    f += block("guard", (0, 5.4, 0), (3.0, 0.9, 2.2))
    f += block("gem", (0, 5.5, 0), (1.2, 1.2, 2.6), taper=(0.8, 0.8))
    # 中央の爪: 反った銅の刃 (縁は明るい銅)
    main = [(5.8, -1.7, 1.7, 0.9, 0.9, claw_curve(5.8)), (8.4, -2.0, 2.0, 0.9, 0.9, claw_curve(8.4)),
            (11.4, -1.7, 1.7, 0.8, 0.8, claw_curve(11.4)), (14.2, -1.0, 1.0, 0.6, 0.6, claw_curve(14.2)),
            (16.5, 0.0, 0.0, 0.0, 0.0, claw_curve(16.5) + 0.2)]
    f += blade("main", main)
    # 側の爪 2 本 (広がる輪郭。左が高く、右が低い)
    left = [(5.4, -1.0, 1.0, 0.8, 0.8, -0.8), (8.2, -1.1, 1.1, 0.8, 0.8, -2.4), (11.0, -0.8, 0.8, 0.6, 0.6, -3.8),
            (13.4, 0.0, 0.0, 0.0, 0.0, -4.5)]
    right = [(5.4, -1.0, 1.0, 0.8, 0.8, 0.8), (7.8, -1.1, 1.1, 0.8, 0.8, 2.4), (10.0, -0.7, 0.7, 0.6, 0.6, 3.8),
             (11.8, 0.0, 0.0, 0.0, 0.0, 4.5)]
    f += blade("main", left)
    f += blade("main", right)
    # 鍔の下へ流れる白い羽 2 枚 (輪郭を広げる)
    f += feather("acc", (-1.2, 5.2, 0), (-4.2, 2.4, 0), 1.3, 0.5, big=False)
    f += feather("acc", (1.2, 5.2, 0), (4.2, 2.4, 0), 1.3, 0.5, big=False)
    if r in ("rare", "legend"):
        f += block("trim", (0, 4.6, 0), (3.2, 0.8, 2.6))
        f += block("trim", (0, 0.9, 0), (2.6, 0.7, 2.4))
        # 背の副爪 (中央の爪の根元に、外へ反る短い棘)
        for sg in (-1, 1):
            f += spike(BONE, "edge", (sg * 1.5, 6.6, 0), (sg * 0.9, 1, 0), 3.6, 0.8, sides=4, rings=2, hint=(0, 0, 1))
    if r == "legend":
        # 雷の翼: 鍔から羽が 3 枚ずつ扇に開く
        for sg in (-1, 1):
            f += feather("acc", (sg * 1.6, 5.6, 0), (sg * 4.6, 8.6, 0), 1.2, 0.55, big=False)
            f += feather("acc", (sg * 1.6, 5.4, 0), (sg * 4.6, 5.6, 0), 1.0, 0.55, big=False)
        f += block("gem", (-3.8, 11.2, 0), (0.8, 1.4, 1.4), taper=(0.8, 0.8))
        f += block("gem", (3.4, 9.0, 0), (0.8, 1.4, 1.4), taper=(0.8, 0.8))
    return f


_CL_L = ([5.4, 8.2, 11.0, 13.4], [-0.8, -2.4, -3.8, -4.5])
_CL_R = ([5.4, 7.8, 10.0, 11.8], [0.8, 2.4, 3.8, 4.5])


def _tines(env, y0, y1, half=0.5, dash=None, left=True, right=True):
    m = line_mask(env, ["main"], lambda y: claw_curve(y), y0, y1, half, dash=dash)
    if left:
        m |= line_mask(env, ["main"], lambda y: np.interp(y, *_CL_L), y0, min(y1, 13.0), half * 0.8, dash=dash)
    if right:
        m |= line_mask(env, ["main"], lambda y: np.interp(y, *_CL_R), y0, min(y1, 11.4), half * 0.8, dash=dash)
    return m


def inlay_claw(env, kind):
    if kind == "rare":
        return line_mask(env, ["main"], lambda y: claw_curve(y), 6.2, 9.6, 0.5)
    if kind == "legend":
        return spot_mask(env, ["acc"], (-3.4, 7.0, 0), 1.6) | spot_mask(env, ["acc"], (3.4, 7.0, 0), 1.6)             | line_mask(env, ["main"], lambda y: np.interp(y, *_CL_L), 6.0, 10.5, 0.4)
    if kind == "p5":
        return _tines(env, 6.0, 15.0, dash=(2.0, 1.2), left=False, right=False)
    if kind == "p10":
        return _tines(env, 6.0, 15.6) | (np.isin(env["mats"], ["edge"]) & env["convex"] & (env["Y"] > 9.0)
                                         & (np.abs(env["N"][..., 2]) < 0.35))
    return _off(env)


# ================================================================ 羽守の鉤槍 (雷翼) y -3.5..19.5, x ±4.4
GLAIVE_GRIP = (0.0, 7.0, 0.0)


def glaive_curve(y):
    return 0.08 * np.maximum(0.0, y - 13.0) ** 2


def glaive(r="common"):
    f = []
    # 柄: 紺の鋼 + 銅の巻き 2 か所
    f += prism("grip", (0, -2.2, 0), (0, 13.0, 0), 0.8, 0.8, sides=6, caps=(False, False), hint=(0, 0, 1))
    f += prism("wrap", (0, 5.0, 0), (0, 9.0, 0), 0.95, 0.95, sides=6, caps=(False, False), hint=(0, 0, 1))
    f += prism("wrap", (0, -1.4, 0), (0, 0.6, 0), 0.95, 0.95, sides=6, caps=(False, False), hint=(0, 0, 1))
    # 石突き: 銅の小さな円錐
    f += spike(BONE, "edge", (0, -2.2, 0), (0, -1, 0), 1.4, 1.0, sides=6, rings=1, hint=(0, 0, 1))
    # 穂の根の継ぎ手 (銅) と光る石
    f += block("guard", (0, 12.8, 0), (2.6, 1.6, 2.2), taper=(0.9, 0.9))
    f += block("gem", (0, 12.8, 0), (1.0, 1.0, 2.5), taper=(0.9, 0.9))
    # 穂: 薙ぐ反りの銅の刃 (先が +x へ曲がる)
    head = [(13.4, -1.6, 1.6, 0.9, 0.9, 0.0), (15.6, -2.0, 2.0, 0.9, 0.9, glaive_curve(15.6)),
            (17.6, -1.6, 1.6, 0.8, 0.8, glaive_curve(17.6)), (19.5, 0.0, 0.0, 0.0, 0.0, glaive_curve(19.5))]
    f += blade("main", head)
    # 鉤 (-x へ張り出して下へ曲がる銅の鉤)
    f += curve("edge", [(-0.8, 14.2, 0), (-2.6, 15.0, 0), (-4.0, 13.8, 0), (-3.8, 11.6, 0)],
               [0.75, 0.65, 0.5, 0.2], [0.85, 0.75, 0.6, 0.25], sides=4, caps=(False, False), hint=(0, 0, 1))
    # 結んだ白い羽 2 枚 (輪郭の幅を広げる)
    f += feather("acc", (0.9, 12.2, 0), (3.2, 8.2, 0), 1.2, 0.5, big=False)
    f += feather("acc", (-0.9, 12.2, 0), (-2.8, 8.4, 0), 1.0, 0.5, big=False)
    if r in ("rare", "legend"):
        f += block("trim", (0, 11.9, 0), (2.8, 0.8, 2.6))
        f += block("trim", (0, 4.4, 0), (2.4, 0.7, 2.2))
        f += block("trim", (0, 9.6, 0), (2.4, 0.7, 2.2))
        # もう一つの小さな鉤 (+x の下側)
        f += curve("edge", [(0.6, 13.8, 0), (2.4, 13.2, 0), (3.6, 11.8, 0), (3.2, 10.4, 0)],
                   [0.6, 0.5, 0.4, 0.15], [0.7, 0.6, 0.5, 0.2], sides=4, caps=(False, False), hint=(0, 0, 1))
    if r == "legend":
        # 風切の羽の冠: 継ぎ手の後ろから大きな羽が上へ開く
        f += feather("acc", (-0.6, 13.2, 0), (-4.2, 18.0, 0), 1.7, 0.6, big=True)
        f += feather("acc", (0.6, 11.2, 0), (4.2, 5.4, 0), 1.5, 0.6, big=True)
        f += block("gem", (-2.4, 15.6, 0), (0.8, 1.4, 1.4), taper=(0.8, 0.8))
    return f


def inlay_glaive(env, kind):
    if kind == "rare":
        return line_mask(env, ["main"], lambda y: glaive_curve(y), 13.6, 16.6, 0.5)
    if kind == "legend":
        return spot_mask(env, ["acc"], (-2.2, 15.6, 0), 1.6) | spot_mask(env, ["acc"], (2.6, 8.8, 0), 1.6)
    if kind == "p5":
        return line_mask(env, ["main"], lambda y: glaive_curve(y), 13.6, 19.0, 0.5, dash=(2.0, 1.2))
    if kind == "p10":
        return line_mask(env, ["main"], lambda y: glaive_curve(y), 13.6, 19.3, 0.5) | (
            np.isin(env["mats"], ["edge"]) & env["convex"] & (env["Y"] > 14.5) & (np.abs(env["N"][..., 2]) < 0.35))
    return _off(env)


# ================================================================ 鍛え手の大鎚 (余燼) y -7..23, x ±9.6, z ±1.7
HAMMER_GRIP = (0.0, 3.0, 0.0)


def hammer(r="common"):
    f = []
    # 柄: 焦げた木 + 灰色の布の巻き、石突き (鉄)
    f += prism("grip", (0, -6.0, 0), (0, 15.2, 0), 1.15, 1.15, sides=6, caps=(False, True), hint=(0, 0, 1))
    f += prism("wrap", (0, -1.0, 0), (0, 5.0, 0), 1.3, 1.3, sides=6, caps=(False, False), hint=(0, 0, 1))
    f += block("spine", (0, -6.4, 0), (3.0, 1.2, 2.6), taper=(0.7, 0.7), skip=("u",))
    f += block("trim", (0, 5.4, 0), (3.2, 0.7, 2.8))
    f += block("trim", (0, -1.4, 0), (3.0, 0.7, 2.6))
    # 頭: 玄武岩の板が割れて段差になった 1 つの塊 (左は低く、右は高い)
    f += block("main", (-4.3, 17.4, 0), (8.6, 6.4, 3.2), rot=rot_z(5), taper=(0.96, 0.95))
    f += block("main", (4.4, 19.4, 0), (8.6, 7.0, 3.4), rot=rot_z(-5), taper=(0.96, 0.95))
    # 割れ目 (光る)
    f += block("gem", (0.0, 18.6, 0), (1.0, 6.4, 3.5), taper=(0.9, 0.9))
    # 打面の鉄 (両端)
    f += block("edge", (-8.9, 17.6, 0), (1.8, 5.6, 3.0), rot=rot_z(3))
    f += block("edge", (8.9, 19.2, 0), (1.8, 6.0, 3.2), rot=rot_z(-3))
    # 柄の頭の鉄の襟
    f += block("spine", (0, 14.4, 0), (3.4, 1.6, 2.8), taper=(0.9, 0.9))
    if r in ("rare", "legend"):
        f += block("trim", (-8.9, 20.5, 0), (2.2, 0.8, 3.2))
        f += block("trim", (8.9, 22.4, 0), (2.2, 0.8, 3.4))
        f += block("trim", (-8.9, 14.8, 0), (2.2, 0.8, 3.2))
        f += block("trim", (8.9, 15.8, 0), (2.2, 0.8, 3.4))
        # 頭の上の鉄の棘
        f += spike(BONE, "edge", (4.4, 22.4, 0), (0.1, 1, 0), 0.6, 1.3, sides=4, rings=1, hint=(0, 0, 1))
    if r == "legend":
        # 割れた背板 2 枚 (上へ傾いて立つ) と、頭の下へ垂れる鉄のひげ
        f += at(extrude("main", [(0, 0), (3.2, 0.4), (2.4, 2.4), (-0.6, 2.2)], 1.8), None, (-6.6, 20.6, 0))
        f += at(extrude("main", [(0, 0), (3.0, 0.2), (3.6, 2.0), (0.4, 1.8)], 1.8), None, (1.2, 22.4, 0))
        f += at(extrude("edge", [(0, 0), (2.4, -0.4), (1.6, -3.0), (-0.4, -2.2)], 1.6), None, (-7.6, 14.6, 0))
        f += at(extrude("edge", [(0, 0), (2.4, -0.4), (1.6, -3.0), (-0.4, -2.2)], 1.6), None, (5.6, 15.6, 0))
        f += block("gem", (-4.6, 20.8, 0), (0.8, 1.6, 1.0), taper=(0.8, 0.8))
    return f


def inlay_hammer(env, kind):
    if kind == "rare":
        return spot_mask(env, ["spine"], (0, 14.4, 0), 1.2)
    if kind == "legend":
        return spot_mask(env, ["main"], (-4.0, 17.0, -1.7), 1.7) | spot_mask(env, ["main"], (4.6, 20.4, -1.7), 1.7)
    if kind == "p5":
        return (np.isin(env["mats"], ["edge"]) & env["convex"]) & (env["Y"] > 14) & (env["Y"] % 2.0 < 1.1)
    if kind == "p10":
        return (np.isin(env["mats"], ["edge"]) & env["convex"]) & (env["Y"] > 14)
    return _off(env)


# ================================================================ 灰被りの火かき棒 (余燼) y -3..19, x ±3.1, z ±1.5
POKER_GRIP = (0.0, 2.0, 0.0)


def poker(r="common"):
    f = []
    # 握り: 焦げた木 + 灰色の布の巻き、石突き (鉄の玉)
    f += prism("grip", (0, -2.4, 0), (0, 4.6, 0), 0.95, 0.95, sides=6, caps=(False, True), hint=(0, 0, 1))
    f += prism("wrap", (0, -0.4, 0), (0, 2.8, 0), 1.1, 1.1, sides=6, caps=(False, False), hint=(0, 0, 1))
    f += block("spine", (0, -2.7, 0), (2.4, 1.0, 2.2), taper=(0.7, 0.7), skip=("u",))
    f += block("trim", (0, 4.4, 0), (2.4, 0.6, 2.2))
    # 鉄の軸
    f += prism("edge", (0, 4.6, 0), (0, 15.4, 0), 0.7, 0.7, sides=6, caps=(False, False), hint=(0, 0, 1))
    # 玄武岩の割れた熱よけ板 (軸にはまる。割れ目が光る)
    f += block("main", (0, 10.6, 0), (4.4, 1.8, 2.8), rot=rot_z(-4), taper=(0.9, 0.9))
    f += block("gem", (0.2, 10.6, 0), (0.9, 2.0, 3.0), taper=(0.9, 0.9))
    # 穂先: まっすぐな鉄の棘 + 右へ巻く鉤
    f += spike(BONE, "edge", (0, 15.2, 0), (0, 1, 0), 3.8, 0.75, sides=4, rings=2, hint=(0, 0, 1))
    f += curve("edge", [(0, 14.2, 0), (0.2, 16.2, 0), (1.6, 17.8, 0), (3.0, 17.2, 0), (3.3, 15.6, 0)],
               [0.65, 0.6, 0.55, 0.45, 0.2], [0.75, 0.7, 0.65, 0.55, 0.25], sides=4, caps=(False, False), hint=(0, 0, 1))
    f += block("gem", (3.3, 15.4, 0), (0.9, 1.0, 1.4), taper=(0.8, 0.8))
    if r in ("rare", "legend"):
        f += block("trim", (0, 12.3, 0), (3.0, 0.7, 2.6))
        f += block("trim", (0, 8.9, 0), (3.0, 0.7, 2.6))
        # 左の返しの鉤 (小さい)
        f += curve("edge", [(0, 6.8, 0), (-1.6, 7.6, 0), (-2.8, 9.0, 0), (-2.9, 10.4, 0)],
                   [0.6, 0.5, 0.4, 0.15], [0.7, 0.6, 0.5, 0.2], sides=4, caps=(False, False), hint=(0, 0, 1))
    if r == "legend":
        # 板の割れた角 (左右へ張る 2 枚) と長い返し
        f += at(extrude("main", [(0, 0), (2.8, 0.2), (2.0, 2.6), (0.2, 2.0)], 1.6), None, (-2.9, 11.4, 0))
        f += at(extrude("main", [(0, 0), (-2.8, 0.2), (-2.0, 2.6), (-0.2, 2.0)], 1.6), None, (2.9, 11.0, 0))
        f += spike(BONE, "edge", (0.6, 7.4, 0), (1, -0.2, 0), 2.6, 0.55, sides=4, rings=2, hint=(0, 0, 1))
        f += block("gem", (-2.8, 10.6, 0), (0.7, 1.2, 1.2), taper=(0.8, 0.8))
    return f


def inlay_poker(env, kind):
    if kind == "rare":
        return spot_mask(env, ["edge"], (0, 13.6, 0), 1.0)
    if kind == "legend":
        return spot_mask(env, ["main"], (-1.6, 10.6, -1.5), 1.2) | spot_mask(env, ["main"], (1.6, 10.6, -1.5), 1.2)
    if kind == "p5":
        return (np.isin(env["mats"], ["edge"]) & env["convex"]) & (env["Y"] > 5) & (env["Y"] < 15.0) & (env["Y"] % 4.0 < 1.2)
    if kind == "p10":
        return (np.isin(env["mats"], ["edge"]) & env["convex"]) & (env["Y"] > 5) & (env["Y"] < 15.0) & (env["Y"] % 3.0 < 1.8)
    return _off(env)
