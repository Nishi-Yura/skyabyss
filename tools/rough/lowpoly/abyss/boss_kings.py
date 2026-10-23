"""歪んだ王 5 型の本番モデル (docs/design/36 の 9.4)。ラフ docs/review/roughs_abyss/king_*.png の選んだ案から作り直す。
  骨 king_bone   A 肋の檻の王 (檻の右半分が剥がれて肋が外へ反る。盾は割れた骨盤、右腕は骨の刃)
  塩 king_salt   C 結晶の冠の王 (高い結晶の冠と背の結晶の列。胸の窪みに青緑の海)
  雷 king_storm  B 避雷針の林の王 (八角の太い柱の像。頭に十二本の銅の針の冠、宙の輪。動かない)
  灰 king_ash    C 灯火の冠の僧 (頭のまわりに燠の灯しが六つ輪になる。灯しを落とす)
  影 king_shadow C 這いずる影 (四つ足で低く這う。頭を垂らし、背に棘と糸)
周 (cycle 0・1・2) ごとに別の姿: 0 = 案どおり、1 = 伸びて傾き、足した部品と割れ目の光、2 = さらに歪み、部品と光が増える。
骨・骨の名前は三つの周で同じ (動きは共通)。歪みは頂点と骨の pivot を同じ関数でずらす (boss_kit.warp)。
大きさ: 一周目の王 (SCALE 1.6) でちょうど案の大きさ。描画が 1/1.6 をかける (BossLook.authorScale)。周が進むと SCALE が上がって大きくなる。
実行: python -I tools/rough/lowpoly/abyss/boss_kings.py [king_bone …] [--cycle 0]   → docs/review/abyss_boss_models/src/<型>_c<周>/
"""
import math
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from boss_kit import *  # noqa: E402,F401,F403
import boss_kit as K  # noqa: E402

S = 1.8
TH = 1.4        # 王の手足の太さ (案のラフより太く、ボスの重さ)


def hp(x, y, z):
    """人の単位 (人 28.8) → 王の単位。"""
    return P(x * S, y * S, z * S)


# ---------------------------------------------------------------------- 人型の骨 (骨・塩の王)
HUM_BONES = {
    "root": (None, (0, 0, 0)),
    "body": ("root", tuple(hp(0, 11.6, 0))),
    "chest": ("body", tuple(hp(0, 15.0, 0.6))),
    "head": ("chest", tuple(hp(0, 22.8, 0.2))),
    "jaw": ("head", tuple(hp(0, 23.0, -0.9))),
    "arm_l": ("chest", tuple(hp(-4.6, 21.0, 0))),
    "fore_l": ("arm_l", tuple(hp(-5.0, 16.4, 0.2))),
    "arm_r": ("chest", tuple(hp(4.6, 21.0, 0))),
    "fore_r": ("arm_r", tuple(hp(5.0, 16.4, 0.2))),
    "leg_l": ("body", tuple(hp(-2.1, 11.6, 0))),
    "shin_l": ("leg_l", tuple(hp(-2.2, 6.4, 0.2))),
    "leg_r": ("body", tuple(hp(2.1, 11.6, 0))),
    "shin_r": ("leg_r", tuple(hp(2.2, 6.4, 0.2))),
}


def hum_legs(F, m, jm, foot):
    for s in (-1, 1):
        lg, sh = side_bone(s, "leg"), side_bone(s, "shin")
        F += part(f"femur{s}", tube(lg, m, [hp(s * 2.1, 11.8, 0), hp(s * 2.2, 9.0, 0.1), hp(s * 2.2, 6.8, 0.2)], ra=[TH * 1.0 * S, TH * 0.8 * S, TH * 0.85 * S], sides=7, caps=(True, True)))
        F += part(f"knee{s}", ellipsoid(sh, jm, tuple(hp(s * 2.2, 6.4, 0.0)), (1.0 * S * TH, 0.95 * S * TH, 1.0 * S * TH), segs=8, rings=3))
        F += part(f"tibia{s}", tube(sh, m, [hp(s * 2.2, 6.0, 0.3), hp(s * 2.2, 3.4, 0.5), hp(s * 2.15, 1.2, 0.2)], ra=[TH * 0.78 * S, TH * 0.62 * S, TH * 0.6 * S], sides=7, caps=(True, True)))
        F += part(f"foot{s}", ellipsoid(sh, foot, tuple(hp(s * 2.15, 0.7, -0.8)), (1.05 * S * TH, 0.65 * S * TH, 1.9 * S), segs=8, rings=3))
        for k, dx in enumerate((-0.55, 0.0, 0.55)):
            F += part(f"toe{s}{k}", cone(sh, jm, tuple(hp(s * 2.15 + dx, 0.5, -2.2)), (dx * 0.3, -0.25, -1), 1.1 * S, 0.3 * S, sides=4, rings=1))


def hum_arm(F, s, m, jm, hand, ln=1.0):
    a, f = side_bone(s, "arm"), side_bone(s, "fore")
    x = s * 4.6
    F += part(f"shoulder{s}", ellipsoid(a, jm, tuple(hp(x, 21.0, 0)), (1.35 * S * TH, 1.25 * S * TH, 1.35 * S * TH), segs=8, rings=3))
    F += part(f"humerus{s}", tube(a, m, [hp(x, 20.6, 0), hp(x + s * 0.2, 18.4, 0.1), hp(s * 5.0, 16.8, 0.2)], ra=[TH * 0.85 * S, TH * 0.62 * S, TH * 0.7 * S], sides=7, caps=(True, True)))
    F += part(f"elbow{s}", ellipsoid(f, jm, tuple(hp(s * 5.0, 16.4, 0.3)), (0.78 * S * TH, 0.75 * S * TH, 0.8 * S * TH), segs=7, rings=3))
    wy = 16.4 - 4.3 * ln
    F += part(f"forearm{s}", tube(f, m, [hp(s * 5.0, 16.0, 0.2), hp(s * 5.15, (16.0 + wy) / 2, -0.3), hp(s * 5.3, wy, -0.8)], ra=[TH * 0.65 * S, TH * 0.5 * S, TH * 0.55 * S], sides=7, caps=(True, True)))
    F += part(f"hand{s}", ellipsoid(f, hand, tuple(hp(s * 5.35, wy - 0.9, -0.9)), (0.8 * S * TH, 1.0 * S * TH, 0.65 * S * TH), segs=7, rings=3))
    for k, dz in enumerate((-0.5, 0.0, 0.5)):
        F += part(f"finger{s}{k}", tube(f, jm, [hp(s * 5.4, wy - 1.6, -0.9 + dz), hp(s * 5.45, wy - 2.6, -1.3 + dz), hp(s * 5.3, wy - 3.2, -1.0 + dz)],
                                        ra=[TH * 0.22 * S, TH * 0.18 * S, TH * 0.1 * S], sides=4, caps=(True, True)))
    return hp(s * 5.35, wy - 1.0, -0.9)


def skull(F, m, jm, eyem, s=1.0, crown=None):
    b = "head"
    F += part("cranium", ellipsoid(b, m, tuple(hp(0, 25.2, 0.2)), (2.0 * S * s, 2.35 * S * s, 2.3 * S * s), segs=12, rings=6))
    F += part("brow", ellipsoid(b, m, tuple(hp(0, 25.6, -1.6)), (1.9 * S * s, 0.6 * S * s, 0.8 * S * s), segs=10, rings=3))
    F += part("face", ellipsoid(b, jm, tuple(hp(0, 24.0, -1.2)), (1.5 * S * s, 1.4 * S * s, 1.1 * S * s), segs=10, rings=4))
    for sx in (-1, 1):
        F += part(f"sock{sx}", ellipsoid(b, "hollow", tuple(hp(sx * 0.75 * s, 24.9, -1.95 * s)), (0.6 * S * s, 0.55 * S * s, 0.42 * S * s), segs=8, rings=3))
        F += part(f"eye{sx}", ellipsoid(b, eyem, tuple(hp(sx * 0.75 * s, 24.8, -2.12 * s)), (0.24 * S * s, 0.22 * S * s, 0.18 * S * s), segs=6, rings=2))
        F += part(f"cheek{sx}", ellipsoid(b, m, tuple(hp(sx * 1.45 * s, 24.0, -1.0)), (0.55 * S, 0.45 * S, 0.9 * S), segs=6, rings=2))
    F += part("nasal", ellipsoid(b, "hollow", tuple(hp(0, 24.1, -2.15 * s)), (0.28 * S, 0.38 * S, 0.2 * S), segs=6, rings=2))
    for i, x in enumerate((-0.9, -0.45, 0.0, 0.45, 0.9)):
        F += part(f"utooth{i}", cone(b, "tooth", tuple(hp(x * s, 23.35, -1.95 * s + abs(x) * 0.15)), (0, -1, -0.1), 0.6 * S, 0.18 * S, sides=4, rings=1))
    j = "jaw"
    F += part("mandible", loft(j, jm, [
        [hp(-1.45 * s, 23.4, -0.2), hp(-1.3 * s, 22.4, -0.5), hp(0, 22.0, -1.9 * s), hp(1.3 * s, 22.4, -0.5), hp(1.45 * s, 23.4, -0.2), hp(0, 23.3, -0.9)],
        [hp(-1.1 * s, 23.3, -1.8 * s), hp(-1.0 * s, 22.3, -1.7 * s), hp(0, 21.9, -2.4 * s), hp(1.0 * s, 22.3, -1.7 * s), hp(1.1 * s, 23.3, -1.8 * s), hp(0, 23.2, -2.2 * s)],
    ], caps=(True, True)))
    for i, x in enumerate((-0.7, -0.25, 0.25, 0.7)):
        F += part(f"ltooth{i}", cone(j, "tooth", tuple(hp(x * s, 22.75, -2.0 * s + abs(x) * 0.15)), (0, 1, -0.1), 0.5 * S, 0.16 * S, sides=4, rings=1))
    if crown:
        crown(F)


# ====================================================================== 骨の王 (肋の檻の王)
def build_bone(cycle):
    F = []
    c, b = "chest", "body"
    hum_legs(F, "bone", "calc", "calc")
    # 骨盤と腰の背骨
    F += part("sacrum", ellipsoid(b, "bone", tuple(hp(0, 11.6, 1.0)), (1.5 * S, 1.3 * S, 1.0 * S), segs=8, rings=3))
    for s in (-1, 1):
        F += part(f"ilium{s}", ellipsoid(b, "bone", tuple(hp(s * 2.0, 12.4, 0.3)), (1.6 * S, 1.2 * S, 0.6 * S), segs=8, rings=3, rot=rot_y(s * -26) @ rot_z(s * 16)))
    for i in range(3):
        F += part(f"lumbar{i}", ellipsoid(b, "calc", tuple(hp(0, 12.9 + i * 0.75, 2.0)), (0.8 * S, 0.5 * S, 0.7 * S), segs=6, rings=2))
    # 背骨 (胸) と棘
    for i in range(7):
        y = 15.2 + i * 1.05
        F += part(f"vert{i}", ellipsoid(c, "bone", tuple(hp(0, y, 2.2 - 0.05 * i)), (0.75 * S, 0.5 * S, 0.65 * S), segs=6, rings=2))
        F += part(f"spinous{i}", cone(c, "calc", tuple(hp(0, y, 2.7)), (0, -0.3, 1), (1.0 + 0.5 * cycle) * S, 0.25 * S, sides=4, rings=1))
    # 左の檻 (肋が閉じる) と右の剥がれた肋 (外へ反る)
    ribs = [(15.4, 3.0, 2.4), (16.6, 3.6, 2.8), (17.8, 3.95, 3.0), (19.0, 4.0, 3.05), (20.2, 3.7, 2.8), (21.3, 3.0, 2.3)]
    for i, (y, rx, rz) in enumerate(ribs):
        pts = []
        for k in range(7):
            a = math.radians(10 + 155 * k / 6)
            pts.append(hp(-rx * math.sin(a), y - 1.3 * (k / 6) ** 1.5, rz * math.cos(a) * 0.98 + 0.1))
        pts[0] = hp(-0.5, y + 0.2, 2.1)
        F += part(f"ribl{i}", tube(c, "bone", pts, ra=[0.42 * S, 0.4 * S, 0.38 * S, 0.36 * S, 0.34 * S, 0.33 * S, 0.3 * S], rb=[0.3 * S] * 7, sides=5, caps=(True, True)))
        # 右: 背から脇までは同じ、そこから外・前へ反り返る (周が進むほど開く)
        open_ = 1.0 + 0.35 * cycle
        q = [hp(0.5, y + 0.2, 2.1), hp(rx * 0.55, y + 0.1, 2.3), hp(rx * 0.98, y - 0.1, 0.9),
             hp(rx * (1.35 + 0.2 * open_), y + 0.5 * open_, -1.2 - 0.06 * i), hp(rx * (1.75 + 0.4 * open_), y + (1.4 + 0.3 * i) * open_, -3.4 - 0.5 * open_)]
        F += part(f"ribr{i}", tube(c, "bone", q, ra=[0.4 * S, 0.38 * S, 0.36 * S, 0.3 * S, 0.1 * S], rb=[0.3 * S, 0.3 * S, 0.28 * S, 0.24 * S, 0.08 * S], sides=5, caps=(True, True)))
    F += part("sternum", tube(c, "calc", [hp(-0.25, 21.6, -2.3), hp(-0.4, 18.4, -2.9), hp(-0.3, 15.6, -2.4)], ra=[0.55 * S, 0.62 * S, 0.4 * S], rb=[0.3 * S] * 3,
                              sides=6, hint=(1, 0, 0), caps=(True, True)))
    # 胸の洞 (左は暗い虚、右の開いた所の奥に赤い燠)
    F += part("hollow", ellipsoid(c, "void", tuple(hp(0, 18.4, 0.6)), (2.9 * S, 3.0 * S, 2.0 * S), segs=10, rings=4))
    F += part("core", ellipsoid(c, "red", tuple(hp(1.0, 18.2, -0.9)), (1.2 * S, 1.4 * S, 0.9 * S), segs=8, rings=3))
    F += part("collar", tube(c, "bone", [hp(-4.4, 21.2, 0.3), hp(-1.8, 21.9, -1.2), hp(0, 21.8, -1.5), hp(1.8, 21.9, -1.2), hp(4.4, 21.2, 0.3)], ra=0.42 * S, rb=0.36 * S,
                             sides=5, caps=(True, True)))
    for s in (-1, 1):
        F += part(f"scap{s}", ellipsoid(c, "bone", tuple(hp(s * 2.5, 19.8, 2.3)), (1.5 * S, 1.8 * S, 0.4 * S), segs=8, rings=3, rot=rot_y(s * 20)))
    for i in range(3):
        F += part(f"neck{i}", ellipsoid(c, "bone", tuple(hp(0, 21.9 + i * 0.55, 0.8 - 0.15 * i)), (0.55 * S, 0.35 * S, 0.5 * S), segs=6, rings=2))
    # 腕: 左に割れた骨盤の盾、右は骨の刃
    hum_arm(F, -1, "bone", "calc", "calc")
    w = hum_arm(F, 1, "bone", "calc", "calc", ln=1.05)
    sh = "fore_l"
    F += part("shield", ellipsoid(sh, "bone", tuple(hp(-6.6, 12.2, -1.2)), (3.4 * S, 3.9 * S, 0.8 * S), segs=12, rings=5,
                                  deform=lambda p: p + np.array([0, 0, -0.06 * (p[0] + 6.6 * S) ** 2 / S])))
    F += part("shield_hole_l", ellipsoid(sh, "hollow", tuple(hp(-7.8, 12.8, -1.9)), (0.9 * S, 1.2 * S, 0.3 * S), segs=7, rings=2))
    F += part("shield_hole_r", ellipsoid(sh, "hollow", tuple(hp(-5.4, 12.8, -1.9)), (0.9 * S, 1.2 * S, 0.3 * S), segs=7, rings=2))
    F += part("shield_rim", tube(sh, "calc", [hp(-9.9, 15.0, -0.7), hp(-9.6, 10.0, -1.4), hp(-6.6, 7.8, -1.9), hp(-3.6, 10.0, -1.4), hp(-3.3, 15.0, -0.7)],
                                 ra=0.45 * S, sides=5, hint=(0, 0, 1), caps=(True, True)))
    F += part("shield_crack", tube(sh, "red" if cycle >= 1 else "hollow", [hp(-6.6, 15.6, -2.0), hp(-6.0, 13.4, -2.2), hp(-7.0, 11.0, -2.2), hp(-6.4, 8.6, -2.0)],
                                   ra=0.16 * S, sides=4, caps=(True, True)))
    F += part("grip", rod(sh, "sinew", hp(-5.3, 12.4, -0.6), hp(-5.3, 12.4, -1.4), 0.5 * S, 6))
    fr = "fore_r"
    F += part("blade_root", ellipsoid(fr, "calc", tuple(hp(5.35, 11.0, -0.9)), (0.8 * S, 0.9 * S, 0.7 * S), segs=7, rings=3))
    # 骨の刃 (肋の骨を伸ばした大きな片刃。前へ反る)
    F += part("blade", loft(fr, "bone", [
        [hp(4.9, 10.8, -1.6), hp(5.8, 10.8, -1.6), hp(5.8, 10.8, -0.2), hp(4.9, 10.8, -0.2)],
        [hp(4.95, 6.0, -2.6), hp(5.75, 6.0, -2.8), hp(5.75, 6.0, 0.0), hp(4.95, 6.0, 0.3)],
        [hp(5.05, 1.4, -3.6), hp(5.65, 1.4, -3.9), hp(5.65, 1.4, -0.9), hp(5.05, 1.4, -0.6)],
        [hp(5.3, -1.6, -4.6), hp(5.4, -1.6, -4.7), hp(5.4, -1.6, -3.4), hp(5.3, -1.6, -3.3)],
    ], caps=(True, True)))
    F += part("blade_spine", tube(fr, "calc", [hp(5.35, 10.6, -0.2), hp(5.35, 6.0, 0.35), hp(5.35, 1.4, -0.5), hp(5.35, -1.2, -3.2)], ra=0.32 * S, sides=5, caps=(True, True)))
    for i, y in enumerate((8.6, 5.4, 2.4)):
        F += part(f"serr{i}", cone(fr, "calc", tuple(hp(5.35, y, -2.2 - i * 0.55)), (0.0, 0.4, -1), 0.9 * S, 0.22 * S, sides=4, rings=1))

    def crown(F):
        n = 3 + 2 * min(cycle, 2)
        for i in range(n):
            x = (i - (n - 1) / 2) * (3.2 / max(1, n - 1)) * 1.0
            ln = (2.6 + (1.8 if abs(x) < 0.5 else 0) + 0.4 * cycle) * S
            F += part(f"crown{i}", cone("head", "calc", tuple(hp(x * 1.05, 26.6 - abs(x) * 0.35, 0.3)), (x * 0.16, 1, 0.25), ln, 0.42 * S, sides=5, rings=3,
                                        bend=(0, 0, 0.8 * S)))
    skull(F, "bone", "calc", "red", 1.08, crown)
    # 周が進むと足すもの: 背の骨の瘤 (1)、肩の骨の角と二つ目の顎の裂け目 (2)
    if cycle >= 1:
        for i, (x, y) in enumerate(((-1.6, 18.0), (1.8, 19.4), (0.0, 20.6))):
            F += part(f"lump{i}", ellipsoid(c, "calc", tuple(hp(x, y, 2.9)), (0.9 * S, 0.9 * S, 0.7 * S), segs=7, rings=3))
        for s in (-1, 1):
            F += part(f"horn{s}", cone(side_bone(s, "arm"), "bone", tuple(hp(s * 4.9, 21.8, 0.2)), (s * 0.5, 1, 0.3), (1.8 + 1.0 * cycle) * S, 0.5 * S, sides=5, rings=3,
                                       bend=(s * 0.6 * S, 0, 0.4 * S)))
    if cycle >= 2:
        for i in range(4):
            a = math.radians(30 + i * 40)
            F += part(f"backrib{i}", arc(c, "bone", hp(0.4, 16.2 + i * 1.3, 2.4), hp(3.0 * math.cos(a) + 1.0, 17.5 + i * 1.5, 4.6), hp(4.6 * math.cos(a) + 1.5, 19.5 + i * 1.8, 5.8),
                                         r=0.35 * S, n=5, taper=(0.45 * S, 0.08 * S), sides=4))
    return F


# ====================================================================== 塩の王 (結晶の冠の王)
def build_salt(cycle):
    F = []
    c, b = "chest", "body"
    hum_legs(F, "salt", "saltd", "saltd")
    # 胴: 塩の殻の人型 (細い。肋の形の塩の板)
    F += part("pelvis", ellipsoid(b, "salt", tuple(hp(0, 11.9, 0.2)), (2.5 * S, 1.6 * S, 1.6 * S), segs=10, rings=4))
    F += part("waist", tube(b, "saltd", [hp(0, 12.4, 0.2), hp(0, 14.6, 0.3)], ra=[1.9 * S, 1.6 * S], rb=[1.3 * S, 1.15 * S], sides=9, caps=(True, True)))
    F += part("torso", loft(c, "salt", [
        [P(*v) for v in [(x * S, 14.4 * S, z * S) for x, z in ((0, -1.3), (1.5, -1.0), (1.9, 0.2), (1.4, 1.3), (0, 1.5), (-1.4, 1.3), (-1.9, 0.2), (-1.5, -1.0))]],
        [P(*v) for v in [(x * S, 17.5 * S, z * S) for x, z in ((0, -1.9), (2.8, -1.4), (3.3, 0.3), (2.4, 1.8), (0, 2.1), (-2.4, 1.8), (-3.3, 0.3), (-2.8, -1.4))]],
        [P(*v) for v in [(x * S, 20.8 * S, z * S) for x, z in ((0, -1.7), (3.4, -1.1), (3.9, 0.4), (2.8, 1.9), (0, 2.2), (-2.8, 1.9), (-3.9, 0.4), (-3.4, -1.1))]],
        [P(*v) for v in [(x * S, 22.3 * S, z * S) for x, z in ((0, -0.8), (1.4, -0.6), (1.7, 0.3), (1.2, 1.1), (0, 1.3), (-1.2, 1.1), (-1.7, 0.3), (-1.4, -0.6))]],
    ], caps=(True, True)))
    # 胸の窪み: 割れた殻の縁の中に青緑の海 (光る)
    F += part("sea_pit", ellipsoid(c, "sea", tuple(hp(0, 18.8, -1.15)), (1.7 * S, 2.1 * S, 0.7 * S), segs=10, rings=4))
    F += part("sea_glow", ellipsoid(c, "teal", tuple(hp(0, 18.8, -1.55)), (1.25 * S, 1.65 * S, 0.45 * S), segs=10, rings=4))
    F += part("pit_rim", hoop(c, "saltd", 0, 18.8 * S, -1.55 * S, 1.9 * S, 2.3 * S, r=0.35 * S, n=12, gap=10, sides=5))
    for i, y in enumerate((16.2, 17.2, 20.4, 21.2)):
        for s in (-1, 1):
            F += part(f"plate{i}{s}", ellipsoid(c, "saltd", tuple(hp(s * 2.6, y, -1.0)), (1.1 * S, 0.32 * S, 0.5 * S), segs=6, rings=2, rot=rot_z(s * (8 + i * 4))))
    # 背の結晶の列 (背骨に沿って高くなる)
    for i in range(6 + 2 * cycle):
        y = 15.0 + i * (7.0 / (5 + 2 * cycle))
        zb = 1.45 + 0.6 * min(1.0, max(0.0, (y - 14.4) / 3.1)) - 0.15
        F += part(f"spine{i}", cone(c, "salt", tuple(hp(0, y, zb)), (0, 0.5, 1), (1.4 + 0.25 * i) * S * (0.8 if i % 2 else 1.0), 0.45 * S, sides=5, rings=2))
    hum_arm(F, -1, "salt", "saltd", "saltd", ln=1.12)
    hum_arm(F, 1, "salt", "saltd", "saltd", ln=1.12)
    for s in (-1, 1):
        F += part(f"pauldron{s}", ellipsoid(side_bone(s, "arm"), "saltd", tuple(hp(s * 4.8, 21.6, 0)), (1.7 * S, 0.9 * S, 1.6 * S), segs=8, rings=3))
        for k in range(2 + cycle):
            F += part(f"pspike{s}{k}", cone(side_bone(s, "arm"), "salt", tuple(hp(s * (4.4 + k * 0.6), 22.2, -0.4 + k * 0.5)), (s * 0.4, 1, 0.2), (1.2 + 0.4 * k) * S, 0.32 * S, sides=5, rings=2))

    def crown(F):
        # 高い結晶の冠: 真ん中の長い一本と、まわりの段々の針
        F += part("circlet", loop("head", "saltd", 0, 26.6 * S, 0.1 * S, 2.1 * S, 2.1 * S, r=0.32 * S, n=12, gap=6, at=180, sides=5))
        n = 9 + 2 * cycle
        for i in range(n):
            a = math.radians(-100 + 200 * i / (n - 1))
            ln = (2.0 + 7.6 * math.cos(a * 0.9) ** 6 + 0.6 * cycle) * S
            x, z = 1.95 * math.sin(a), -1.95 * math.cos(a) + 0.1
            F += part(f"crown{i}", cone("head", "salt", tuple(hp(x, 26.4, z)), (x * 0.1, 1, z * 0.08 + 0.1), ln, 0.42 * S, sides=5, rings=3))
        F += part("crown_teal", ellipsoid("head", "teal", tuple(hp(0, 27.0, -1.9)), (0.35 * S, 0.45 * S, 0.25 * S), segs=6, rings=2))
    skull(F, "salt", "saltd", "teal", 1.0, crown)
    if cycle >= 1:
        for i, (x, y, z) in enumerate(((-3.0, 17.0, -0.8), (3.2, 19.6, -0.6), (-2.2, 21.0, 1.2), (2.0, 15.8, 0.7))):
            F += part(f"growth{i}", cone(c, "salt", tuple(hp(x, y, z)), (x * 0.3, 0.6, z * 0.3 - 0.2), (1.2 + 0.5 * cycle) * S, 0.5 * S, sides=5, rings=2))
    if cycle >= 2:
        for s in (-1, 1):
            F += part(f"legcrys{s}", cone(side_bone(s, "shin"), "salt", tuple(hp(s * 2.6, 4.4, 0.5)), (s * 0.6, 0.6, 0.3), 2.2 * S, 0.45 * S, sides=5, rings=2))
    return F


# ====================================================================== 雷の王 (避雷針の林の王)。動かない柱の像
STORM_BONES = {"root": (None, (0, 0, 0)), "body": ("root", (0, 4, 0)), "head": ("body", (0, 44, 0)), "halo": ("head", (0, 62, 0)),
               "arm_l": ("body", (-11.5, 32, 0)), "arm_r": ("body", (11.5, 32, 0))}


def octa(y, r, cx=0.0, cz=0.0, rot=22.5):
    return [P(cx + r * math.sin(math.radians(rot + 45 * k)), y, cz - r * math.cos(math.radians(rot + 45 * k))) for k in range(8)]


def build_storm(cycle):
    F = []
    b = "body"
    # 台座と柱の体 (八角。下が広い衣のよう、上で胸、肩は角ばる)
    F += part("plinth", loft("root", "basalt", [octa(0.0, 13.5), octa(2.6, 13.5), octa(3.4, 12.0), octa(4.6, 12.0)], caps=(True, True)))
    F += part("trunk", loft(b, "basalt", [octa(4.2, 11.6), octa(14.0, 10.6), octa(24.0, 9.4), octa(30.0, 10.2), octa(36.0, 10.8), octa(40.0, 9.0), octa(43.0, 6.0)],
                            caps=(True, True)))
    for i, y in enumerate((12.0, 27.0) + ((19.5,) if cycle >= 1 else ())):
        r = {12.0: 10.8, 27.0: 9.9, 19.5: 10.1}[y]
        F += part(f"band{i}", loft(b, "teal", [octa(y - 0.9, r + 0.35), octa(y + 0.9, r + 0.35)], caps=(True, True)))
        F += part(f"bandb{i}", loft(b, "bronze", [octa(y - 1.5, r + 0.1), octa(y - 0.9, r + 0.5), octa(y + 0.9, r + 0.5), octa(y + 1.5, r + 0.1)], caps=(True, True)))
    # 衣の縦の溝 (八つの稜に銅の帯)
    for k in range(8):
        a = math.radians(22.5 + 45 * k)
        F += part(f"rib{k}", tube(b, "bronze", [P(11.4 * math.sin(a), 5.0, -11.4 * math.cos(a)), P(9.6 * math.sin(a), 22.0, -9.6 * math.cos(a)),
                                                 P(10.4 * math.sin(a), 34.0, -10.4 * math.cos(a))], ra=0.6, sides=4, caps=(True, True)))
    # 肩当て・胸の板・刻んだ紋 (青緑の象嵌)
    for s in (-1, 1):
        F += part(f"pauldron{s}", loft(b, "basalt", [octa(40.4, 4.2, cx=s * 8.4), octa(42.6, 4.6, cx=s * 8.6), octa(43.6, 3.0, cx=s * 8.8)], caps=(True, True)))
        F += part(f"pspike{s}", cone(b, "copper", (s * 8.8, 43.4, 0), (s * 0.4, 1, 0), 4.0, 1.0, sides=5, rings=2))
    F += part("chestplate", loft(b, "bronze", [[P(-6.0, 38.0, -10.2), P(6.0, 38.0, -10.2), P(6.0, 38.0, -8.8), P(-6.0, 38.0, -8.8)],
                                               [P(-4.6, 30.0, -10.3), P(4.6, 30.0, -10.3), P(4.6, 30.0, -8.6), P(-4.6, 30.0, -8.6)],
                                               [P(-0.6, 25.6, -10.0), P(0.6, 25.6, -10.0), P(0.6, 25.6, -8.6), P(-0.6, 25.6, -8.6)]], caps=(True, True)))
    for i, (p0, p1) in enumerate((((0, 37.0), (0, 28.0)), ((-3.4, 35.0), (3.4, 35.0)), ((-2.2, 31.6), (2.2, 31.6)))):
        F += part(f"rune{i}", tube(b, "teal", [P(p0[0], p0[1], -10.45), P(p1[0], p1[1], -10.45)], ra=0.35, sides=4, caps=(True, True)))
    for k in range(4):
        a = math.radians(45 + 90 * k)
        F += part(f"crack{k}", tube(b, "bolt" if cycle >= 1 else "hollow", [P(11.0 * math.sin(a), 5.6, -11.0 * math.cos(a)), P(10.5 * math.sin(a + 0.06), 9.0, -10.5 * math.cos(a + 0.06)),
                                                                          P(10.2 * math.sin(a - 0.05), 11.0, -10.2 * math.cos(a - 0.05))], ra=0.3, sides=4, caps=(True, True)))
    # 顔 (割れた面の奥に光)
    h = "head"
    F += part("head", loft(h, "basalt", [octa(42.0, 5.2), octa(46.0, 6.0), octa(52.0, 5.8), octa(55.0, 4.4), octa(56.4, 2.6)], caps=(True, True)))
    F += part("visor", ellipsoid(h, "hollow", (0, 49.0, -5.2), (3.6, 1.4, 1.2), segs=8, rings=2))
    F += part("eyes", ellipsoid(h, "bolt", (0, 49.0, -5.6), (2.6, 0.55, 0.6), segs=8, rings=2))
    # 冠: 十二本の銅の針 (外へ開く林)。針の先が宙の輪に触れる
    n = 12 + 4 * cycle
    for i in range(n):
        a = math.radians(360.0 * i / n)
        r0 = 3.6
        base = P(r0 * math.sin(a), 54.0, -r0 * math.cos(a))
        top = P(8.6 * math.sin(a), 62.0 + (1.6 if i % 2 else 0), -8.6 * math.cos(a))
        F += part(f"needle{i}", tube(h, "copper" if i % 3 else "bronze", [base, (base + top) / 2 + P(0, 0.6, 0), top], ra=[0.75, 0.55, 0.3], sides=5, caps=(True, True)))
        F += part(f"tip{i}", cone(h, "bolt", tuple(top), tuple((top - base) / np.linalg.norm(top - base)), 1.8, 0.4, sides=4, rings=1))
    F += part("halo", loop("halo", "teal", 0, 62.6, 0, 9.0, 9.0, r=0.7, n=24, gap=4, at=180, sides=5))
    # 腕: 短い石の腕、手に光る結晶 (雷を集める)
    for s in (-1, 1):
        a = side_bone(s, "arm")
        F += part(f"shoulder{s}", ellipsoid(a, "basalt", (s * 11.0, 32.0, 0), (3.4, 3.2, 3.4), segs=8, rings=3))
        F += part(f"arm{s}", tube(a, "basalt", [P(s * 11.4, 31.0, 0), P(s * 13.0, 22.0, -1.6), P(s * 12.6, 14.0, -3.6)], ra=[2.6, 2.2, 2.0], sides=8, caps=(True, True)))
        F += part(f"cuff{s}", tube(a, "bronze", [P(s * 12.8, 17.5, -3.0), P(s * 12.6, 15.0, -3.6)], ra=2.5, sides=8, caps=(True, True)))
        F += part(f"hand{s}", ellipsoid(a, "basalt", (s * 12.6, 12.6, -4.0), (2.4, 2.4, 2.4), segs=8, rings=3))
        F += part(f"gem{s}", ellipsoid(a, "bolt", (s * 12.6, 11.6, -6.6), (1.6, 1.6, 1.6), segs=4, rings=1, phase=45))
    if cycle >= 1:
        for i in range(4 + 2 * cycle):
            a = math.radians(30 + i * (300 / (3 + 2 * cycle)))
            F += part(f"shard{i}", cone(b, "basalt", (8.4 * math.sin(a), 36.0 + (i % 2) * 2, -8.4 * math.cos(a)), (math.sin(a), 0.6, -math.cos(a)), 4.0 + cycle, 1.5, sides=4, rings=2))
    if cycle >= 2:
        for i in range(6):
            a = math.radians(60 * i + 30)
            F += part(f"bolt{i}", tube(b, "bolt", [P(10.0 * math.sin(a), 6.0, -10.0 * math.cos(a)), P(9.6 * math.sin(a + 0.15), 12.0, -9.6 * math.cos(a + 0.15)),
                                                    P(9.2 * math.sin(a - 0.1), 18.0, -9.2 * math.cos(a - 0.1))], ra=0.3, sides=4, caps=(True, True)))
    return F


# ====================================================================== 灰の王 (灯火の冠の僧)
ASH_BONES = {
    "root": (None, (0, 0, 0)),
    "body": ("root", tuple(hp(0, 11.6, 0))),
    "robe_f": ("body", tuple(hp(0, 11.0, -1.6))),
    "robe_b": ("body", tuple(hp(0, 11.0, 1.8))),
    "chest": ("body", tuple(hp(0, 15.0, 0.6))),
    "head": ("chest", tuple(hp(0, 22.6, 0.0))),
    "crown": ("head", tuple(hp(0, 26.0, 0.4))),
    "arm_l": ("chest", tuple(hp(-4.4, 21.0, 0))),
    "fore_l": ("arm_l", tuple(hp(-5.2, 16.2, 0.2))),
    "arm_r": ("chest", tuple(hp(4.4, 21.0, 0))),
    "fore_r": ("arm_r", tuple(hp(5.2, 16.2, 0.2))),
    "leg_l": ("body", tuple(hp(-1.9, 11.6, 0))),
    "leg_r": ("body", tuple(hp(1.9, 11.6, 0))),
}


def robe_ring(y, rx, rzf, rzb, n=12, cz=0.0, wob=0.0, seed=0.0):
    pts = []
    for k in range(n):
        a = 2 * math.pi * k / n
        r_ = 1.0 + wob * math.sin(a * 3 + seed)
        z = -math.cos(a) * (rzf if math.cos(a) > 0 else rzb)
        pts.append(hp(rx * math.sin(a) * r_, y, cz + z * r_))
    return pts


def build_ash(cycle):
    F = []
    # 脚 (衣の下。歩くと裾が揺れる。足先だけ見える)
    for s in (-1, 1):
        lg = side_bone(s, "leg")
        F += part(f"legcore{s}", tube(lg, "cloth", [hp(s * 1.9, 11.4, 0), hp(s * 2.0, 2.0, 0.1)], ra=1.2 * S, sides=7, caps=(True, True)))
        F += part(f"foot{s}", ellipsoid(lg, "ash", tuple(hp(s * 2.0, 0.7, -0.9)), (1.0 * S, 0.65 * S, 1.8 * S), segs=8, rings=3))
    # 衣: 前と後ろの裾 (別の骨) と胴の衣
    b = "body"
    F += part("robe_front", loft("robe_f", "cloth", [robe_ring(11.6, 3.0, 1.9, 0.2, n=10, cz=-0.2), robe_ring(6.0, 3.7, 2.5, 0.2, n=10, cz=-0.3, wob=0.04, seed=1),
                                                       robe_ring(1.2, 4.3, 3.0, 0.2, n=10, cz=-0.4, wob=0.08, seed=2), robe_ring(0.2, 4.4, 3.1, 0.2, n=10, cz=-0.4, wob=0.08, seed=2)],
                                     caps=(True, True)))
    F += part("robe_back", loft("robe_b", "cloth", [robe_ring(11.6, 3.0, 0.2, 2.0, n=10, cz=0.2), robe_ring(6.0, 3.7, 0.2, 2.6, n=10, cz=0.3, wob=0.04, seed=3),
                                                      robe_ring(1.2, 4.4, 0.2, 3.2, n=10, cz=0.4, wob=0.08, seed=4), robe_ring(0.2, 4.5, 0.2, 3.3, n=10, cz=0.4, wob=0.08, seed=4)],
                                    caps=(True, True)))
    F += part("girdle", loop(b, "iron", 0, 12.0 * S, 0.1 * S, 3.2 * S, 2.3 * S, r=0.35 * S, n=14, gap=8, at=0, sides=5))
    for i, x in enumerate((-1.0, 1.2)):
        F += part(f"tassel{i}", tube(b, "ash", [hp(x, 11.8, -2.3), hp(x * 1.1, 8.5, -2.6), hp(x * 1.2, 5.6, -2.5)], ra=[0.28 * S, 0.24 * S, 0.1 * S], sides=4, caps=(True, True)))
    c = "chest"
    F += part("torso", loft(c, "cloth", [robe_ring(11.8, 3.1, 2.1, 2.2, n=10), robe_ring(16.0, 3.6, 2.3, 2.5, n=10), robe_ring(20.4, 4.2, 2.4, 2.7, n=10, cz=0.2),
                                         robe_ring(22.0, 3.0, 1.9, 2.3, n=10, cz=0.2)], caps=(True, True)))
    F += part("stole", tube(c, "ash", [hp(-2.6, 22.0, -1.2), hp(-1.4, 17.0, -2.4), hp(-1.2, 12.0, -2.5)], ra=0.6 * S, rb=0.15 * S, sides=4, hint=(0, 0, 1), caps=(True, True)))
    F += part("stole2", tube(c, "ash", [hp(2.6, 22.0, -1.2), hp(1.4, 17.0, -2.4), hp(1.2, 12.0, -2.5)], ra=0.6 * S, rb=0.15 * S, sides=4, hint=(0, 0, 1), caps=(True, True)))
    # 肩の灰の外套 (肩を張る)
    for s in (-1, 1):
        F += part(f"mantle{s}", ellipsoid(c, "ash", tuple(hp(s * 3.2, 21.0, 0.3)), (2.6 * S, 1.3 * S, 2.6 * S), segs=10, rings=4, rot=rot_z(s * -18)))
        for k in range(3):
            F += part(f"mrag{s}{k}", cone(c, "ash", tuple(hp(s * (2.0 + k * 1.2), 19.8, -0.8 + k * 1.1)), (s * 0.2, -1, 0.1), (1.6 + 0.4 * k) * S, 0.6 * S, sides=4, rings=2))
    # 胸の燠 (衣の裂け目の奥の熱)
    F += part("rift", ellipsoid(c, "soot", tuple(hp(0, 18.2, -2.1)), (0.9 * S, 1.8 * S, 0.4 * S), segs=8, rings=3))
    F += part("embers", ellipsoid(c, "ember", tuple(hp(0, 18.2, -2.35)), (0.5 * S, 1.3 * S, 0.25 * S), segs=6, rings=3))
    # 腕: 長い袖、灰の手。左手に数珠、右手は灯しを落とす手
    for s in (-1, 1):
        a, f = side_bone(s, "arm"), side_bone(s, "fore")
        F += part(f"sleeve_u{s}", tube(a, "cloth", [hp(s * 4.0, 21.2, 0.1), hp(s * 4.8, 18.6, 0.2), hp(s * 5.2, 16.4, 0.3)], ra=[1.3 * S, 1.15 * S, 1.15 * S], sides=8, caps=(True, True)))
        F += part(f"sleeve_l{s}", tube(f, "cloth", [hp(s * 5.2, 16.6, 0.3), hp(s * 5.4, 13.6, -0.3), hp(s * 5.6, 11.0, -0.9)], ra=[1.1 * S, 1.3 * S, 1.6 * S], sides=8, caps=(True, True)))
        F += part(f"cuffd{s}", ellipsoid(f, "soot", tuple(hp(s * 5.6, 10.9, -0.95)), (1.25 * S, 0.35 * S, 1.25 * S), segs=8, rings=2))
        F += part(f"hand{s}", ellipsoid(f, "ash", tuple(hp(s * 5.6, 10.0, -1.1)), (0.75 * S, 1.0 * S, 0.6 * S), segs=7, rings=3))
        for k, dz in enumerate((-0.45, 0.0, 0.45)):
            F += part(f"finger{s}{k}", tube(f, "ash", [hp(s * 5.6, 9.3, -1.1 + dz), hp(s * 5.65, 8.2, -1.5 + dz), hp(s * 5.55, 7.4, -1.3 + dz)], ra=[0.2 * S, 0.16 * S, 0.08 * S],
                                            sides=4, caps=(True, True)))
    for k in range(7):
        a = math.radians(-60 + k * 20)
        F += part(f"bead{k}", ellipsoid("fore_l", "bone", tuple(hp(-5.6 + 0.9 * math.sin(a), 9.0 - 1.6 * math.cos(a) * 0.6 - k * 0.15, -1.6)), (0.3 * S, 0.3 * S, 0.3 * S),
                                        segs=5, rings=2))
    # 頭: 深い頭巾、顔は暗がり、口の燠
    h = "head"
    F += part("hood", loft(h, "cloth", [robe_ring(21.6, 2.3, 1.9, 2.2, n=10), robe_ring(23.6, 2.5, 2.2, 2.4, n=10), robe_ring(25.6, 2.2, 2.0, 2.1, n=10, cz=0.2),
                                        robe_ring(26.9, 1.3, 1.2, 1.4, n=10, cz=0.4), robe_ring(27.4, 0.3, 0.3, 0.3, n=10, cz=0.5)], caps=(True, True)))
    F += part("face_dark", ellipsoid(h, "hollow", tuple(hp(0, 24.0, -1.85)), (1.45 * S, 1.75 * S, 0.6 * S), segs=10, rings=4))
    for sx in (-1, 1):
        F += part(f"eye{sx}", ellipsoid(h, "ember", tuple(hp(sx * 0.55, 24.6, -2.25)), (0.2 * S, 0.12 * S, 0.12 * S), segs=5, rings=2))
    F += part("mouthfire", ellipsoid(h, "flame", tuple(hp(0, 23.2, -2.2)), (0.32 * S, 0.45 * S, 0.15 * S), segs=6, rings=2))
    # 冠: 頭のまわりの鉄の輪に、六つの燠の灯し (周で数が増える)
    cr = "crown"
    n = 6 + 2 * cycle
    R = 3.9
    F += part("crown_ring", loop(cr, "iron", 0, 26.0 * S, 0.4 * S, R * S, R * S, r=0.26 * S, n=18, gap=4, at=180, sides=5))
    for i in range(4):
        a = math.radians(45 + 90 * i)
        F += part(f"crown_strut{i}", rod(cr, "iron", hp(0.9 * math.sin(a), 26.4, 0.4 - 0.9 * math.cos(a)), hp(R * 0.98 * math.sin(a), 26.0, 0.4 - R * 0.98 * math.cos(a)), 0.18 * S, 4))
    for i in range(n):
        a = math.radians(360.0 * i / n)
        x, z = R * math.sin(a), 0.4 - R * math.cos(a)
        F += part(f"cup{i}", ellipsoid(cr, "iron", tuple(hp(x, 26.2, z)), (0.62 * S, 0.4 * S, 0.62 * S), segs=7, rings=2))
        F += part(f"wick{i}", tube(cr, "wax", [hp(x, 26.3, z), hp(x, 27.3, z)], ra=0.36 * S, sides=6, caps=(True, True)))
        F += part(f"flame{i}", ellipsoid(cr, "ember", tuple(hp(x, 27.5, z)), (0.42 * S, 0.6 * S, 0.42 * S), segs=6, rings=3))
        F += part(f"flamet{i}", cone(cr, "flame", tuple(hp(x, 27.6, z)), (0, 1, 0), 1.9 * S, 0.36 * S, sides=5, rings=2))
    if cycle >= 1:
        for i in range(3 + cycle):
            a = math.radians(-50 + i * (100 / (2 + cycle)))
            F += part(f"spine{i}", cone(c, "ash", tuple(hp(2.6 * math.sin(a), 19.0 + i * 0.6, 2.2 * math.cos(a) + 0.4)), (math.sin(a) * 0.4, 0.5, 1), (1.6 + 0.4 * cycle) * S,
                                        0.4 * S, sides=4, rings=2))
    if cycle >= 2:
        for s in (-1, 1):
            F += part(f"shoulder_flame{s}", cone(side_bone(s, "arm"), "flame", tuple(hp(s * 4.6, 21.8, 0.4)), (s * 0.2, 1, 0), 1.6 * S, 0.5 * S, sides=5, rings=2))
            F += part(f"shoulder_cup{s}", ellipsoid(side_bone(s, "arm"), "iron", tuple(hp(s * 4.6, 21.6, 0.4)), (0.6 * S, 0.4 * S, 0.6 * S), segs=6, rings=2))
    return F


# ====================================================================== 影の王 (這いずる影)
SH_LEGS = [("fl", -1, -1), ("fr", 1, -1), ("bl", -1, 1), ("br", 1, 1)]
SHADOW_BONES = {"root": (None, (0, 0, 0)), "body": ("root", (0, 22, 0)), "neck": ("body", (0, 22, -12)), "head": ("neck", (0, 14, -22)),
                "threads": ("body", (0, 20, 2))}
for _n, _s, _f in SH_LEGS:
    SHADOW_BONES["leg_" + _n] = ("body", (_s * 6.0, 23.0, _f * 9.0))
    SHADOW_BONES["low_" + _n] = ("leg_" + _n, (_s * 17.0, 31.0, _f * 14.0))


def build_shadow(cycle):
    F = []
    b = "body"
    # 胴: 低く長い背 (濡れた影)。背骨の棘
    F += part("torso", loft(b, "shadow", [
        [P(x, y, -14.0) for x, y in ((0, 25.0), (3.6, 23.6), (4.2, 21.6), (2.6, 19.6), (0, 19.0), (-2.6, 19.6), (-4.2, 21.6), (-3.6, 23.6))],
        [P(x, y, -6.0) for x, y in ((0, 27.6), (6.0, 25.6), (7.0, 22.0), (4.8, 18.6), (0, 17.6), (-4.8, 18.6), (-7.0, 22.0), (-6.0, 25.6))],
        [P(x, y, 4.0) for x, y in ((0, 27.0), (5.6, 25.2), (6.4, 21.8), (4.4, 18.8), (0, 18.0), (-4.4, 18.8), (-6.4, 21.8), (-5.6, 25.2))],
        [P(x, y, 12.0) for x, y in ((0, 24.6), (3.8, 23.4), (4.4, 21.2), (3.0, 19.6), (0, 19.2), (-3.0, 19.6), (-4.4, 21.2), (-3.8, 23.4))],
        [P(x, y, 17.0) for x, y in ((0, 22.4), (1.4, 22.0), (1.6, 21.2), (1.0, 20.6), (0, 20.4), (-1.0, 20.6), (-1.6, 21.2), (-1.4, 22.0))],
    ], caps=(True, True)))
    F = sculpt(F, lambda p: p + np.array([0.25 * p[0], 0.5 * math.sin(p[2] * 0.4), 0]))
    for f in F:
        f.part = "torso"
    def top(z):
        zs, ys = (-14.0, -6.0, 4.0, 12.0, 17.0), (25.0, 27.6, 27.0, 24.6, 22.4)
        return float(np.interp(z, zs, ys)) + 0.5 * math.sin(z * 0.4)

    for i in range(7 + 2 * cycle):
        z = -11.0 + i * (25.0 / (6 + 2 * cycle))
        y = top(z) - 0.7
        F += part(f"spike{i}", cone(b, "tar", (0, y, z), (0, 1, 0.35), 3.0 + 1.4 * math.sin((z + 12) / 28 * math.pi) + 0.6 * cycle, 0.8, sides=5, rings=2))
    # 首と垂れた頭 (顔はのっぺり、口の裂け目に青白い光)
    n = "neck"
    F += part("neckt", tube(n, "shadow", [P(0, 23.0, -12.0), P(0, 22.0, -17.0), P(0, 18.0, -21.0)], ra=[3.2, 2.4, 2.0], rb=[2.8, 2.2, 1.8], sides=8, caps=(True, True)))
    h = "head"
    F += part("skullh", ellipsoid(h, "shadow", (0, 14.0, -23.0), (3.2, 4.4, 3.4), segs=10, rings=5, rot=rot_x(-20)))
    F += part("slit", ellipsoid(h, "hollow", (0, 12.0, -25.6), (2.0, 1.6, 0.8), segs=8, rings=3))
    F += part("slitglow", ellipsoid(h, "pale", (0, 12.0, -25.9), (1.2, 0.35, 0.4), segs=6, rings=2))
    for sx in (-1, 1):
        F += part(f"eye{sx}", ellipsoid(h, "pale", (sx * 1.3, 15.6, -25.4), (0.45, 0.35, 0.3), segs=5, rings=2))
    # 四つの細い脚 (肘・膝が背より高い)
    for nm, sx, fz in SH_LEGS:
        lg, lo = "leg_" + nm, "low_" + nm
        hip = P(sx * 6.0, 23.0, fz * 9.0)
        knee = P(sx * 17.0, 31.0, fz * 14.0)
        foot = P(sx * 21.0, 0.6, fz * 19.0)
        F += part(f"hipj{nm}", ellipsoid(lg, "shadow", tuple(hip), (2.6, 2.6, 2.6), segs=7, rings=3))
        F += part(f"upper{nm}", tube(lg, "shadow", [hip, (hip + knee) / 2 + P(0, 2.5, 0), knee], ra=[3.0, 2.3, 1.8], sides=7, caps=(True, True)))
        F += part(f"kneej{nm}", ellipsoid(lo, "tar", tuple(knee), (2.3, 2.3, 2.3), segs=7, rings=3))
        F += part(f"kneespike{nm}", cone(lo, "tar", tuple(knee + P(0, 1.2, 0)), (sx * 0.3, 1, fz * 0.3), 3.6, 0.8, sides=4, rings=2))
        F += part(f"lower{nm}", tube(lo, "shadow", [knee, (knee + foot) / 2 + P(sx * 1.0, 0, fz * 0.5), foot + P(0, 1.2, 0)], ra=[1.8, 1.3, 0.9], sides=7, caps=(True, True)))
        for k, d in enumerate((-0.6, 0.0, 0.6)):
            F += part(f"claw{nm}{k}", cone(lo, "tar", tuple(foot + P(0, 1.4, 0)), (sx * 0.3 + d * 0.5, -1, fz * 0.3 - d * 0.5), 2.4, 0.4, sides=4, rings=1))
    # 腹から垂れる糸 (先が青白く光る)
    t = "threads"
    for i, (x, z, ln) in enumerate(((-3.0, -6.0, 8.0), (2.2, -2.0, 11.0), (-1.2, 3.0, 9.0), (3.0, 7.0, 7.0), (-2.6, 9.0, 12.0)) + (((0.4, -9.0, 10.0),) if cycle >= 1 else ())):
        F += part(f"thread{i}", tube(t, "tar", [P(x, 19.4, z), P(x * 1.1, 19.4 - ln * 0.5, z + 0.4), P(x * 1.15, 19.4 - ln, z + 0.6)], ra=[0.35, 0.25, 0.2], sides=4, caps=(True, True)))
        F += part(f"bead{i}", ellipsoid(t, "pale", (x * 1.15, 19.4 - ln - 0.4, z + 0.6), (0.5, 0.6, 0.5), segs=5, rings=2))
    if cycle >= 2:
        for s in (-1, 1):
            F += part(f"armx{s}", arc(b, "shadow", P(s * 5.6, 22.0, -10.0), P(s * 12.0, 28.0, -18.0), P(s * 10.0, 6.0, -24.0), r=0.9, n=7, taper=(1.4, 0.3), sides=5))
    return F


KINGS = {
    "king_bone": (build_bone, HUM_BONES, 52.0, dict(center=(0, 26, 0), scale=4.0)),
    "king_salt": (build_salt, HUM_BONES, 52.0, dict(center=(0, 27, 0), scale=3.8)),
    "king_storm": (build_storm, STORM_BONES, 60.0, dict(center=(0, 30, 0), scale=3.6)),
    "king_ash": (build_ash, ASH_BONES, 52.0, dict(center=(0, 26, 0), scale=4.0)),
    "king_shadow": (build_shadow, SHADOW_BONES, 30.0, dict(center=(0, 16, 0), scale=4.0)),
}
WARP = {0: 0.0, 1: 0.5, 2: 1.0}
CRACK = {0: 0.0, 1: 0.5, 2: 1.0}


def make(name, cycle):
    build, bones, h, kw = KINGS[name]
    F = build(cycle)
    bs = dict(bones)
    if WARP[cycle] > 0:
        F, bs = K.warp(F, bones, WARP[cycle], h, lean=0.0 if name == "king_storm" else 1.0)
    return K.export(f"{name}_c{cycle}", F, bs, tpu=2.5, crackglow=CRACK[cycle], seed=cycle * 7.0, **kw)


def main(argv):
    names = [a for a in argv if not a.startswith("--") and not a.isdigit()]
    cycles = [int(a) for a in argv if a.isdigit()] or [0, 1, 2]
    for n in names or KINGS:
        for c in cycles:
            make(n, c)


if __name__ == "__main__":
    main(sys.argv[1:])
