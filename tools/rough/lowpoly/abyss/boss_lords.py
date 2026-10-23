"""層の主 4 体の本番モデル (docs/design/36 の 8.5)。ラフ docs/review/roughs_abyss/lord_*.png の選んだ案から作り直す。
  肋の主   lord_rib    A 肋の檻の灯守 (胸の肋の檻に消えた灯り、右手に鉤の杖)
  白殻の主 lord_salt   A 巻貝の巨殻 (赤い畝の巨殻を背負って這う。殻が開くと頭が出る)
  鼓動まねの主 lord_storm  A 心臓むき出しの袋 (胸の窓から心臓。背に写しの芽)
  灯り喰いの主 lord_ash    A 灯の山を吊る黒い口 (浮かぶ黒い球、歯の輪の口、食った角灯を吊る鎖)
大きさ: ゲームでの大きさ (AbyssLords.SCALE をかけた後) で作る。描画が SCALE を戻す (BossLook.authorScale)。
実行: python -I tools/rough/lowpoly/abyss/boss_lords.py [名前…]   → docs/review/abyss_boss_models/src/<名前>/
"""
import math
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from boss_kit import *  # noqa: E402,F401,F403
import boss_kit as K  # noqa: E402


# ====================================================================== 肋の主 (肋の檻の灯守)
RIB_BONES = {
    "root": (None, (0, 0, 0)),
    "body": ("root", (0, 27, 0)),
    "chest": ("body", (0, 34, 2)),
    "lamps": ("chest", (0, 46, 0.5)),
    "head": ("chest", (0, 50, 0)),
    "jaw": ("head", (0, 50.5, -3.5)),
    "arm_l": ("chest", (-10.5, 46.5, 0.8)),
    "fore_l": ("arm_l", (-11.6, 35.5, -0.4)),
    "arm_r": ("chest", (10.5, 46.5, 0.8)),
    "fore_r": ("arm_r", (11.6, 35.5, -0.4)),
    "leg_l": ("body", (-5, 27, 0)),
    "shin_l": ("leg_l", (-5.3, 15, 1.0)),
    "leg_r": ("body", (5, 27, 0)),
    "shin_r": ("leg_r", (5.3, 15, 1.0)),
}


def rib_skull(F):
    b = "head"
    # 頭蓋: 縦に長い卵 + 額の張り + 頬骨。眼窩は暗い穴の中に赤い熾き
    F += part("cranium", ellipsoid(b, "bone", (0, 55.2, 0.4), (4.9, 5.4, 5.6), segs=12, rings=6,
                                   deform=lambda p: p + np.array([0, 0, 0.6 * max(0.0, p[1]) / 5.4])))
    F += part("brow", ellipsoid(b, "bone", (0, 56.4, -4.0), (4.6, 1.5, 1.9), segs=10, rings=3))
    F += part("face", ellipsoid(b, "calc", (0, 52.4, -3.0), (3.6, 3.4, 2.6), segs=10, rings=4))
    for s in (-1, 1):
        F += part(f"cheek{s}", ellipsoid(b, "bone", (s * 3.5, 52.6, -2.6), (1.4, 1.1, 2.2), segs=6, rings=2))
        F += part(f"sock{s}", ellipsoid(b, "hollow", (s * 1.9, 54.6, -4.6), (1.45, 1.35, 1.0), segs=8, rings=3))
        F += part(f"ember{s}", ellipsoid(b, "red", (s * 1.9, 54.4, -5.0), (0.55, 0.5, 0.45), segs=6, rings=2))
    F += part("nose", ellipsoid(b, "hollow", (0, 52.9, -5.1), (0.7, 0.9, 0.5), segs=6, rings=2))
    for i, x in enumerate((-2.2, -1.1, 0.0, 1.1, 2.2)):
        F += part(f"utooth{i}", cone(b, "tooth", (x, 50.9, -4.9 + abs(x) * 0.35), (0, -1, -0.15), 1.6, 0.45, sides=4, rings=1))
    # 冠: 後ろへ反った骨の棘 5 本
    for i, (x, ln) in enumerate(((-3.6, 4.0), (-1.8, 5.8), (0.0, 7.4), (1.8, 5.8), (3.6, 4.0))):
        F += part(f"crown{i}", cone(b, "calc", (x, 59.0 - abs(x) * 0.45, 0.6), (x * 0.12, 1.0, 0.45), ln, 0.85, sides=5, rings=3,
                                    bend=(0, 0, 1.6)))
    # 顎
    j = "jaw"
    F += part("mandible", loft(j, "bone", [
        [P(-3.4, 50.8, -0.4), P(-3.0, 48.9, -1.2), P(0, 48.2, -4.6), P(3.0, 48.9, -1.2), P(3.4, 50.8, -0.4), P(0, 50.6, -2.0)],
        [P(-2.6, 50.6, -4.4), P(-2.4, 48.7, -4.0), P(0, 48.0, -5.8), P(2.4, 48.7, -4.0), P(2.6, 50.6, -4.4), P(0, 50.4, -5.2)],
    ], caps=(True, True)))
    for i, x in enumerate((-1.8, -0.6, 0.6, 1.8)):
        F += part(f"ltooth{i}", cone(j, "tooth", (x, 49.9, -5.0 + abs(x) * 0.3), (0, 1, -0.1), 1.3, 0.4, sides=4, rings=1))


def rib_cage(F):
    c = "chest"
    # 背骨 (胸の分): 椎骨の粒 + 棘突起
    for i in range(7):
        y = 33.0 + i * 2.25
        F += part(f"vert{i}", ellipsoid(c, "bone", (0, y, 5.6 - 0.15 * i), (1.6, 1.15, 1.4), segs=6, rings=2))
        F += part(f"spinous{i}", cone(c, "calc", (0, y, 6.6 - 0.15 * i), (0, -0.4, 1), 2.0, 0.55, sides=4, rings=1))
    # 肋: 背骨から前の胸骨へ、左右それぞれ弧 (右半分は剥がれて外へ反る王とは違い、主は閉じた檻)
    ribs = ((33.5, 6.2, 5.0), (36.0, 7.6, 6.0), (38.6, 8.4, 6.6), (41.2, 8.6, 6.8), (43.8, 8.0, 6.4), (46.2, 6.6, 5.4))
    for i, (y, rx, rz) in enumerate(ribs):
        for s in (-1, 1):
            pts = []
            for k in range(7):
                a = math.radians(8 + 150 * k / 6)          # 0 = 背、180 = 前
                pts.append(P(s * rx * math.sin(a), y - 3.2 * (k / 6) ** 1.5, 0.0 + rz * math.cos(a) * 0.98 + 0.2))
            pts[0] = P(s * 1.2, y + 0.4, 5.4)
            F += part(f"rib{i}{s}", tube(c, "bone", pts, ra=[0.95, 0.9, 0.85, 0.8, 0.78, 0.75, 0.7], rb=[0.7] * 7, sides=5,
                                         hint=(0, 1, 0), caps=(True, True)))
    F += part("sternum", tube(c, "calc", [P(0, 47.2, -5.2), P(0, 41.0, -6.6), P(0, 33.6, -5.6)], ra=[1.5, 1.7, 1.0], rb=[0.7, 0.8, 0.6],
                              sides=6, hint=(1, 0, 0), caps=(True, True)))
    # 胸の中は暗い (肋の隙間から奥が見えても、虚ろな塊)
    F += part("hollow_lung", ellipsoid(c, "void", (0, 40.0, 1.4), (6.2, 6.6, 4.4), segs=10, rings=4))
    F += part("collar", tube(c, "bone", [P(-9.6, 47.2, 0.6), P(-4.0, 48.4, -2.4), P(0, 48.2, -3.2), P(4.0, 48.4, -2.4), P(9.6, 47.2, 0.6)],
                             ra=[0.9, 1.0, 1.0, 1.0, 0.9], rb=[0.8] * 5, sides=5, caps=(True, True)))
    for s in (-1, 1):
        F += part(f"scap{s}", ellipsoid(c, "bone", (s * 5.6, 44.0, 5.4), (3.2, 3.8, 0.9), segs=8, rings=3, rot=rot_y(s * 22)))
    # 肩に掛けたぼろの外套 (背から肩へ。前は開いて肋の檻が見える)
    for s in (-1, 1):
        rings = []
        for k, (y, sp) in enumerate(((48.6, 1.0), (44.0, 1.12), (38.0, 1.22), (31.0, 1.3))):
            rings.append([P(s * 1.0, y, 6.6 + k * 0.5), P(s * 6.0 * sp, y + 0.4, 6.2 + k * 0.4), P(s * 10.6 * sp, y - 0.6, 2.6),
                          P(s * 10.0 * sp, y - 1.0, 1.6), P(s * 5.6 * sp, y - 0.4, 5.0 + k * 0.4), P(s * 1.0, y - 0.2, 5.6 + k * 0.5)])
        F += part(f"mantle{s}", loft(c, "cloth", rings, caps=(True, True)))
        for k in range(3):
            x = s * (3.0 + 3.0 * k)
            F += part(f"rag{s}{k}", cone(c, "cloth", (x, 31.0, 6.6 - k * 1.2), (s * 0.1, -1, 0.15), 4.0 + k * 1.5, 1.4, sides=4, rings=2))
    # 首の椎骨
    for i in range(3):
        F += part(f"neck{i}", ellipsoid(c, "bone", (0, 48.5 + i * 1.3, 1.6 - i * 0.4), (1.25, 0.8, 1.2), segs=6, rings=2))


def rib_lamps(F):
    L = "lamps"
    # 檻の中の消えた灯り 4 つ (鉄の籠と煤けたガラス)。一つだけ熾きが残る
    for i, (x, y, z, glow) in enumerate(((-3.6, 39.6, -1.6, False), (3.4, 38.8, -1.2, False), (0.0, 35.8, -2.6, True), (0.2, 42.6, -2.8, False))):
        F += part(f"cord{i}", rod(L, "iron", (x * 0.6, 46.6, 0.4), (x, y + 2.6, z), 0.28, 4))
        F += part(f"cap{i}", ellipsoid(L, "iron", (x, y + 2.3, z), (1.35, 0.6, 1.35), segs=6, rings=1))
        F += part(f"glass{i}", ellipsoid(L, "glass", (x, y, z), (1.25, 2.1, 1.25), segs=6, rings=2))
        if glow:
            F += part(f"emberl{i}", ellipsoid(L, "ember", (x, y - 0.6, z - 0.9), (0.55, 0.55, 0.45), segs=5, rings=2))
        for k in range(3):
            a = math.radians(30 + k * 120)
            F += part(f"bar{i}{k}", rod(L, "iron", (x + math.sin(a) * 1.35, y + 2.0, z - math.cos(a) * 1.35),
                                        (x + math.sin(a) * 1.3, y - 1.8, z - math.cos(a) * 1.3), 0.2, 4))
        F += part(f"base{i}", ellipsoid(L, "iron", (x, y - 2.0, z), (1.2, 0.45, 1.2), segs=6, rings=1))


def rib_body(F):
    b = "body"
    # 骨盤: 腸骨の翼 + 仙骨。腰の背骨
    F += part("sacrum", ellipsoid(b, "bone", (0, 27.6, 2.4), (3.2, 2.8, 2.2), segs=8, rings=3))
    for s in (-1, 1):
        F += part(f"ilium{s}", ellipsoid(b, "bone", (s * 4.6, 29.0, 0.8), (3.4, 2.6, 1.3), segs=8, rings=3, rot=rot_y(s * -28) @ rot_z(s * 18)))
        F += part(f"hipball{s}", ellipsoid(b, "calc", (s * 5.0, 26.6, 0.0), (1.9, 1.9, 1.9), segs=7, rings=3))
    F += part("pubis", tube(b, "bone", [P(-4.2, 26.0, -1.6), P(0, 24.8, -3.0), P(4.2, 26.0, -1.6)], ra=0.85, sides=5, caps=(True, True)))
    for i in range(3):
        F += part(f"lumbar{i}", ellipsoid(b, "bone", (0, 29.4 + i * 1.6, 4.8), (1.7, 1.0, 1.5), segs=6, rings=2))
    # 腰布の残り (ぼろの帯)
    F += part("sash", tube(b, "cloth", [P(-5.6, 27.8, 0), P(0, 27.4, -3.4), P(5.6, 27.8, 0), P(0, 28.2, 3.6), P(-5.6, 27.8, 0.1)],
                           ra=0.8, rb=1.6, sides=4, hint=(0, 1, 0), caps=(True, True)))
    F += part("tatter0", loft(b, "cloth", [[P(-2.6, 27.2, -3.0), P(-0.4, 27.2, -3.4), P(-0.4, 27.2, -2.4), P(-2.6, 27.2, -2.2)],
                                            [P(-2.2, 20.0, -3.2), P(-0.8, 19.4, -3.4), P(-0.8, 19.4, -2.8), P(-2.2, 20.0, -2.7)]], caps=(True, True)))
    F += part("tatter1", loft(b, "cloth", [[P(1.0, 27.2, -3.2), P(3.2, 27.2, -2.6), P(3.2, 27.2, -1.8), P(1.0, 27.2, -2.4)],
                                            [P(1.6, 21.6, -3.2), P(3.0, 22.4, -2.6), P(3.0, 22.4, -2.1), P(1.6, 21.6, -2.6)]], caps=(True, True)))


def rib_legs(F):
    for s in (-1, 1):
        leg, shin = side_bone(s, "leg"), side_bone(s, "shin")
        F += part(f"femur{s}", tube(leg, "bone", [P(s * 5.0, 26.8, 0), P(s * 5.3, 21.0, 0.6), P(s * 5.3, 15.6, 1.0)], ra=[1.5, 1.2, 1.35],
                                    sides=6, caps=(True, True)))
        F += part(f"knee{s}", ellipsoid(shin, "calc", (s * 5.3, 15.0, 0.6), (1.75, 1.6, 1.7), segs=7, rings=3))
        F += part(f"patella{s}", ellipsoid(shin, "bone", (s * 5.3, 15.4, -1.0), (1.0, 1.1, 0.6), segs=6, rings=2))
        F += part(f"tibia{s}", tube(shin, "bone", [P(s * 5.3, 14.6, 1.0), P(s * 5.2, 8.0, 1.4), P(s * 5.1, 2.6, 0.6)], ra=[1.25, 1.0, 0.95],
                                    sides=6, caps=(True, True)))
        F += part(f"fibula{s}", rod(shin, "calc", (s * 6.4, 13.6, 1.6), (s * 6.1, 3.4, 1.2), 0.45, 4))
        F += part(f"heel{s}", ellipsoid(shin, "calc", (s * 5.1, 1.8, 0.8), (1.5, 1.4, 1.7), segs=6, rings=2))
        for k, dx in enumerate((-1.1, 0.0, 1.1)):
            F += part(f"toe{s}{k}", tube(shin, "bone", [P(s * 5.1 + dx, 1.6, -0.4), P(s * 5.1 + dx * 1.4, 0.9, -3.4), P(s * 5.1 + dx * 1.6, 0.4, -5.2)],
                                         ra=[0.6, 0.5, 0.25], sides=4, caps=(True, True)))


def rib_arms(F):
    for s in (-1, 1):
        arm, fore = side_bone(s, "arm"), side_bone(s, "fore")
        F += part(f"shoulder{s}", ellipsoid(arm, "bone", (s * 10.5, 46.8, 0.8), (2.6, 2.4, 2.6), segs=8, rings=3))
        F += part(f"humerus{s}", tube(arm, "bone", [P(s * 10.6, 46.0, 0.8), P(s * 11.2, 41.0, 0.3), P(s * 11.6, 36.2, -0.3)],
                                      ra=[1.35, 1.0, 1.2], sides=6, caps=(True, True)))
        F += part(f"elbow{s}", ellipsoid(fore, "calc", (s * 11.6, 35.5, -0.2), (1.45, 1.4, 1.5), segs=7, rings=3))
        F += part(f"radius{s}", tube(fore, "bone", [P(s * 11.5, 35.0, -0.6), P(s * 11.7, 29.0, -3.0), P(s * 11.8, 24.2, -5.4)],
                                     ra=[1.05, 0.85, 0.9], sides=6, caps=(True, True)))
        F += part(f"ulna{s}", rod(fore, "calc", (s * 12.6, 34.4, 0.2), (s * 12.7, 24.8, -4.6), 0.45, 4))
        F += part(f"palm{s}", ellipsoid(fore, "calc", (s * 11.9, 23.0, -6.0), (1.5, 1.8, 1.2), segs=7, rings=2))
        for k, (dx, dz) in enumerate(((-0.9, -0.6), (-0.3, -0.9), (0.3, -0.9), (0.9, -0.6))):
            x0 = s * 11.9 + dx
            F += part(f"finger{s}{k}", tube(fore, "bone", [P(x0, 21.8, -6.0 + dz), P(x0 * 1.01, 19.2, -6.8 + dz), P(x0 * 1.01, 17.2, -6.2 + dz)],
                                            ra=[0.38, 0.32, 0.18], sides=4, caps=(True, True)))
        F += part(f"thumb{s}", tube(fore, "bone", [P(s * 11.0, 22.6, -7.0), P(s * 10.6, 20.8, -7.8), P(s * 10.8, 19.6, -7.4)],
                                    ra=[0.4, 0.33, 0.2], sides=4, caps=(True, True)))
    # 右手: 鉤の杖 (灯台の火を落とす)
    r = "fore_r"
    F += part("staff", tube(r, "iron", [P(11.9, 6.0, -6.6), P(11.9, 30.0, -6.6), P(11.9, 58.0, -6.6)], ra=[0.6, 0.75, 0.7], sides=6, caps=(True, True)))
    F += part("staff_wrap", tube(r, "sinew", [P(11.9, 20.0, -6.6), P(11.9, 24.6, -6.6)], ra=1.0, sides=6, caps=(True, True)))
    F += part("staff_ferrule", cone(r, "iron", (11.9, 6.6, -6.6), (0, -1, 0), 2.4, 0.8, sides=6, rings=2))
    F += part("hook", tube(r, "iron", [P(11.9, 57.4, -6.6), P(11.6, 62.6, -6.6), P(9.4, 65.0, -6.6), P(6.6, 64.2, -6.6), P(5.6, 61.8, -6.6)],
                           ra=[0.75, 0.7, 0.6, 0.5, 0.35], sides=5, hint=(0, 0, 1), caps=(True, True)))
    F += part("hook_barb", cone(r, "iron", (5.8, 62.2, -6.6), (0.6, -1, 0), 1.8, 0.4, sides=4, rings=1))
    F += part("hlamp_cord", rod(r, "iron", (6.0, 61.6, -6.6), (6.0, 59.2, -6.6), 0.22, 4))
    F += part("hlamp_cap", ellipsoid(r, "iron", (6.0, 59.0, -6.6), (1.2, 0.5, 1.2), segs=6, rings=1))
    F += part("hlamp", ellipsoid(r, "glass", (6.0, 57.0, -6.6), (1.1, 1.8, 1.1), segs=6, rings=2))
    F += part("hlamp_ember", ellipsoid(r, "ember", (6.0, 56.4, -7.4), (0.5, 0.5, 0.4), segs=5, rings=2))
    # 左手: 吹き消した灯りの角灯
    l = "fore_l"
    F += part("lantern_ring", tube(l, "iron", [P(-11.9, 21.2, -7.6), P(-11.9, 19.6, -7.6)], ra=0.6, sides=5, caps=(True, True)))
    F += part("lantern_top", ellipsoid(l, "iron", (-11.9, 18.8, -7.6), (1.9, 0.9, 1.9), segs=8, rings=2))
    F += part("lantern", ellipsoid(l, "glass", (-11.9, 15.8, -7.6), (2.0, 2.8, 2.0), segs=8, rings=3))
    for k in range(4):
        a = math.radians(45 + 90 * k)
        F += part(f"lbar{k}", rod(l, "iron", (-11.9 + math.sin(a) * 2.0, 18.4, -7.6 - math.cos(a) * 2.0),
                                  (-11.9 + math.sin(a) * 1.9, 13.2, -7.6 - math.cos(a) * 1.9), 0.24, 4))
    F += part("lantern_base", ellipsoid(l, "iron", (-11.9, 12.9, -7.6), (1.9, 0.6, 1.9), segs=8, rings=1))


def build_rib():
    F = []
    rib_skull(F)
    rib_cage(F)
    rib_lamps(F)
    rib_body(F)
    rib_legs(F)
    rib_arms(F)
    return F


# ====================================================================== 白殻の主 (巻貝の巨殻)
SALT_BONES = {
    "root": (None, (0, 0, 0)),
    "body": ("root", (0, 6, 0)),
    "tail": ("body", (0, 5, 14)),
    "shell": ("body", (0, 12, 6)),
    "neck": ("body", (0, 10, -10)),
    "head": ("neck", (0, 24, -21)),
    "stalk_l": ("head", (-2.2, 28, -22)),
    "stalk_r": ("head", (2.2, 28, -22)),
}
SHELL_C = np.array([-5.6, 13.0, 8.0])
SPIRE = 0.80


def spiral_r(th):
    return 10.0 * (SPIRE ** th)


def spiral_point(th):
    """巻貝の殻の渦の中心線 (th = 0 が殻の口、増えるほど奥 = 上の小さな巻き)。口は右前で、前 (-Z) へ開く。
    渦の半径は管の太さより小さく、巻きの真ん中は塞がる (殻の芯が見えない)。巻くたびに上へ上がり、後ろへ少し傾く。"""
    r = spiral_r(th)
    R = 0.72 * r
    rise = 8.2 * (1 - SPIRE ** th) / math.log(1 / SPIRE)
    a = th * 2 * math.pi + math.radians(90)
    return np.array([SHELL_C[0] + R * math.sin(a) + 5.6 * (1 - SPIRE ** th) * 0.0,
                     SHELL_C[1] + rise, SHELL_C[2] - R * math.cos(a) + 0.24 * rise])


def build_salt():
    F = []
    # ---- 殻: 渦の管 (口から先へ細る) + 先の尖り + 縫い目の赤い畝 + 縦の肋 + 塩の結晶
    ths = [i * 0.125 for i in range(0, 33)]      # 4 巻き
    pts = [spiral_point(t) for t in ths]
    ras = [spiral_r(t) for t in ths]
    ras[-1] = 0.0
    F += part("whorl", tube("shell", "shell", pts, ra=ras, sides=11, hint=(0, 1, 0), caps=(True, False)))
    # 赤い畝: 巻きの外側を這う光る筋 (縫い目の少し下)
    seam = []
    for t in [i * 0.125 for i in range(0, 27)]:
        c = spiral_point(t)
        out = c - np.array([SHELL_C[0], c[1], SHELL_C[2] + 0.24 * (c[1] - SHELL_C[1])])
        n = np.linalg.norm(out)
        out = out / n if n > 1e-6 else np.array([1.0, 0, 0])
        seam.append(c + out * spiral_r(t) * 0.97 + np.array([0, spiral_r(t) * 0.18, 0]))
    F += part("seam", tube("shell", "red", seam, ra=[max(0.35, 0.95 * 0.8 ** t) for t in [i * 0.125 for i in range(27)]], sides=4, caps=(True, True)))
    # 縦の肋 (成長の筋): 管のまわりの輪
    for i, t in enumerate([0.35, 0.7, 1.05, 1.4, 1.75, 2.1, 2.5]):
        c = spiral_point(t)
        d = spiral_point(t + 0.02) - spiral_point(t - 0.02)
        d = d / np.linalg.norm(d)
        a = np.cross(d, [0, 1, 0]); a = a / np.linalg.norm(a)
        b = np.cross(d, a)
        rr = spiral_r(t) * 1.03
        ring_pts = [c + rr * (math.cos(math.radians(k * 30)) * a + math.sin(math.radians(k * 30)) * b) for k in range(-4, 7)]
        F += part(f"axrib{i}", tube("shell", "saltd", ring_pts, ra=0.55 * 0.85 ** t + 0.25, sides=4, caps=(True, True)))
    # 口の唇 (外へ反る)
    c0 = spiral_point(0.0)
    d0 = spiral_point(0.03) - c0
    d0 = d0 / np.linalg.norm(d0)
    F += part("lip", tube("shell", "salt", [c0 - d0 * 0.4, c0 - d0 * 1.8], ra=[spiral_r(0) * 1.0, spiral_r(0) * 1.16], sides=11, caps=(True, True)))
    F += part("aperture", ellipsoid("shell", "hollow", tuple(c0 - d0 * 1.9), (spiral_r(0) * 0.78, spiral_r(0) * 0.78, spiral_r(0) * 0.78), segs=10, rings=4))
    # 塩の結晶の房
    rng = np.random.default_rng(7)
    for i, t in enumerate([0.2, 0.5, 0.9, 1.25, 1.6, 2.2]):
        c = spiral_point(t)
        out = c - np.array([SHELL_C[0], c[1] - 4, SHELL_C[2] + 0.24 * (c[1] - SHELL_C[1])])
        out = out / np.linalg.norm(out)
        base = c + out * spiral_r(t) * 0.85
        for k in range(3):
            d = out + rng.normal(0, 0.35, 3)
            F += part(f"crys{i}{k}", cone("shell", "salt", tuple(base + rng.normal(0, 0.8, 3)), tuple(d), 3.5 + rng.random() * 4.0 * 0.9 ** t, 1.1,
                                          sides=5, rings=2))
    # ---- 足 (這う肉。縁がひだ)
    F += part("foot", ellipsoid("body", "sea", (0, 4.2, -1.0), (9.6, 4.4, 19.0), segs=12, rings=4,
                                deform=lambda p: p + np.array([0, -max(0.0, -p[1]) * 0.6, 0])))
    F += part("sole", ellipsoid("body", "saltd", (0, 1.6, -1.0), (10.6, 1.6, 20.0), segs=12, rings=2))
    for i in range(7):
        z = -16 + i * 5.2
        for s in (-1, 1):
            F += part(f"frill{i}{s}", ellipsoid("body", "sea", (s * 9.8, 2.6, z), (1.6, 1.4, 2.8), segs=6, rings=2))
    F += part("tailtip", ellipsoid("tail", "sea", (0, 3.4, 18.0), (6.0, 3.0, 7.0), segs=9, rings=3))
    # ---- 首と頭 (殻の口から伸びる青白い首)
    neck_pts = [P(1.2, 9.6, 5.0), P(0.9, 10.4, -3.0), P(0.5, 14.6, -12.0), P(0.2, 20.0, -18.4), P(0, 23.4, -21.0)]
    F += part("neckt", tube("neck", "sea", neck_pts, ra=[6.4, 6.0, 5.0, 4.4, 4.0], rb=[5.2, 5.0, 4.4, 4.0, 3.8], sides=9, caps=(True, True)))
    for i, (x, y, z) in enumerate(((0.8, 11.4, -6.0), (0.6, 13.6, -10.4), (0.4, 16.6, -14.4), (0.2, 19.6, -17.8))):
        F += part(f"neckring{i}", ellipsoid("neck", "saltd", (x, y, z), (5.6 - i * 0.4, 1.4, 4.0 - i * 0.3), segs=9, rings=2,
                                            rot=rot_x(-30 - i * 8)))
    h = "head"
    F += part("skullh", ellipsoid(h, "salt", (0, 25.5, -23.0), (4.6, 3.8, 5.6), segs=10, rings=4))
    F += part("snout", ellipsoid(h, "salt", (0, 23.4, -27.4), (3.4, 2.4, 2.8), segs=8, rings=3))
    F += part("mouth", ellipsoid(h, "hollow", (0, 22.2, -29.4), (2.2, 0.8, 1.0), segs=8, rings=2))
    for s in (-1, 1):
        F += part(f"tent{s}", tube(h, "sea", [P(s * 2.6, 23.0, -28.4), P(s * 4.4, 21.2, -31.4), P(s * 5.4, 19.6, -32.2)], ra=[0.9, 0.6, 0.0],
                                   sides=5, caps=(True, False)))
        st = side_bone(s, "stalk")
        F += part(f"stalk{s}", tube(st, "sea", [P(s * 2.2, 27.5, -22.6), P(s * 3.6, 33.0, -23.4), P(s * 4.6, 37.6, -23.0)], ra=[1.3, 0.95, 0.85],
                                    sides=6, caps=(True, True)))
        F += part(f"eye{s}", ellipsoid(st, "pale", (s * 4.7, 38.6, -23.2), (1.6, 1.6, 1.6), segs=7, rings=3))
        F += part(f"pupil{s}", ellipsoid(st, "hollow", (s * 4.9, 38.7, -24.6), (0.6, 0.8, 0.3), segs=5, rings=2))
    return sculpt(F, lambda p: p * 0.92)


# ====================================================================== 鼓動まねの主 (心臓むき出しの袋)
STORM_LEGS = [(-1, -1, 0), (1, -1, 1), (-1, 0, 2), (1, 0, 3), (-1, 1, 4), (1, 1, 5)]   # (左右, 前中後, 番号)
STORM_BONES = {"root": (None, (0, 0, 0)), "body": ("root", (0, 22, 0)), "heart": ("body", (0, 33, -12)), "jaw": ("body", (0, 17, -12)),
               "buds": ("body", (0, 46, 6))}
for _s, _f, _i in STORM_LEGS:
    STORM_BONES[f"leg{_i}"] = ("body", (_s * 10.0, 14.0, _f * 8.0))
    STORM_BONES[f"shin{_i}"] = ("leg" + str(_i), (_s * 20.0, 13.0, _f * 14.0))


def build_storm():
    F = []
    b = "body"
    lump = lambda p: p + (p - np.array([0, 30, 0])) / max(1e-6, np.linalg.norm(p - np.array([0, 30, 0]))) * (
        1.3 * math.sin(p[0] * 0.45 + 1.0) * math.sin(p[1] * 0.38) * math.sin(p[2] * 0.5 + 2.0))
    F += part("sac", ellipsoid(b, "flesh", (0, 30, 0), (17.0, 19.0, 14.5), segs=16, rings=8, deform=lambda p: p + np.array([0, 0, 0.0])))
    F[-1:] = F[-1:]
    F = sculpt(F, lump)
    F[0].part = "sac"
    for f in F:
        f.part = "sac"
    # 帯 (縫い止めた筋の帯) と鉄の留め金
    for i, (y, sc) in enumerate(((19.5, 0.82), (27.0, 0.99), (35.0, 0.98), (42.5, 0.80))):
        q = math.sqrt(max(0.0, 1 - ((y - 30) / 19.0) ** 2))
        F += part(f"band{i}", loop(b, "sinew", 0, y, 0.2, 17.6 * q + 0.3, 15.0 * q + 0.3, r=1.0, n=14, gap=74 if 25 < y < 40 else 30, at=0, sides=5))
        for k in range(5):
            a = math.radians(60 + k * 60)
            F += part(f"staple{i}{k}", ellipsoid(b, "iron", ((17.6 * q - 0.5) * math.sin(a), y, -(15.0 * q - 0.5) * math.cos(a)),
                                                 (0.9, 1.6, 0.9), segs=5, rings=2))
    # 胸の窓: 骨の枠と二本の骨の格子、窓の奥は暗い
    F += part("window_dark", ellipsoid(b, "hollow", (0, 33, -10.8), (7.6, 7.8, 3.6), segs=12, rings=4))
    F += part("frame", hoop(b, "bone", 0, 33, -13.6, 8.4, 8.6, r=1.35, n=14, gap=8, sides=5))
    for s in (-1, 1):
        F += part(f"bar{s}", tube(b, "bone", [P(s * 3.6, 41.0, -13.2), P(s * 4.6, 33.0, -15.6), P(s * 3.6, 25.0, -13.2)], ra=0.75, sides=5, caps=(True, True)))
    # 心臓 (はみ出す。光る)
    h = "heart"
    F += part("ventricle", ellipsoid(h, "bolt", (0.4, 32.0, -13.0), (5.0, 6.2, 4.6), segs=12, rings=5, rot=rot_z(-12),
                                     deform=lambda p: p + np.array([0, -0.12 * max(0.0, -p[1]) ** 1.4, 0])))
    F += part("atrium_l", ellipsoid(h, "pale", (-3.4, 37.0, -12.0), (2.8, 2.6, 2.6), segs=8, rings=3))
    F += part("atrium_r", ellipsoid(h, "pale", (3.6, 36.4, -12.4), (2.6, 2.4, 2.4), segs=8, rings=3))
    F += part("aorta", tube(h, "membrane", [P(0.6, 37.0, -12.0), P(0.8, 42.0, -11.0), P(-1.6, 44.6, -8.4), P(-3.0, 43.0, -5.0)],
                            ra=[1.8, 1.7, 1.5, 1.4], sides=7, caps=(True, True)))
    F += part("vena", tube(h, "membrane", [P(3.8, 37.4, -12.0), P(5.4, 41.6, -10.4), P(6.4, 43.0, -6.0)], ra=[1.3, 1.2, 1.1], sides=6, caps=(True, True)))
    for i, (x0, x1) in enumerate(((-2.0, -3.4), (1.6, 3.2))):
        F += part(f"coronary{i}", tube(h, "meat", [P(x0, 36.4, -16.8), P((x0 + x1) / 2, 32.0, -17.6), P(x1, 27.6, -15.6)], ra=0.42, sides=4, caps=(True, True)))
    # 下の口 (横に裂けた口、歯の二列)
    F += part("maw", ellipsoid(b, "hollow", (0, 16.6, -12.4), (10.0, 3.4, 3.4), segs=12, rings=3))
    F += part("upperlip", tube(b, "meat", [P(-10.6, 17.0, -10.8), P(-5.0, 19.6, -14.4), P(0, 20.2, -15.2), P(5.0, 19.6, -14.4), P(10.6, 17.0, -10.8)],
                               ra=1.6, rb=1.1, sides=5, caps=(True, True)))
    for i, x in enumerate((-8.4, -5.6, -2.8, 0.0, 2.8, 5.6, 8.4)):
        F += part(f"utooth{i}", cone(b, "tooth", (x, 19.0, -14.4 + abs(x) * 0.26), (0, -1, -0.25), 3.2 - abs(x) * 0.12, 0.9, sides=4, rings=2))
    j = "jaw"
    F += part("lowerlip", tube(j, "meat", [P(-10.0, 15.8, -10.6), P(-5.0, 13.4, -13.6), P(0, 12.8, -14.4), P(5.0, 13.4, -13.6), P(10.0, 15.8, -10.6)],
                               ra=1.6, rb=1.1, sides=5, caps=(True, True)))
    for i, x in enumerate((-7.0, -4.2, -1.4, 1.4, 4.2, 7.0)):
        F += part(f"ltooth{i}", cone(j, "tooth", (x, 14.2, -13.6 + abs(x) * 0.26), (0, 1, -0.25), 2.6, 0.8, sides=4, rings=2))
    # 目 (窓の上の三つ。人の目のまね)
    for i, (x, y) in enumerate(((-7.4, 44.0), (0.0, 46.4), (7.4, 44.0))):
        F += part(f"lid{i}", ellipsoid(b, "membrane", (x, y, -10.6 + abs(x) * 0.25), (2.6, 2.1, 1.6), segs=8, rings=3))
        F += part(f"eye{i}", ellipsoid(b, "pale", (x, y, -11.9 + abs(x) * 0.25), (1.4, 1.1, 0.8), segs=6, rings=2))
        F += part(f"pupil{i}", ellipsoid(b, "hollow", (x, y, -12.5 + abs(x) * 0.25), (0.45, 0.8, 0.3), segs=5, rings=2))
    # 背の写しの芽 (三つ。小さな袋に小さな窓)
    for i, (x, y, z, r) in enumerate(((-8.0, 45.0, 6.0, 4.4), (0.0, 49.0, 7.2, 5.0), (8.0, 45.0, 6.0, 4.4))):
        F += part(f"bud{i}", ellipsoid("buds", "membrane", (x, y, z), (r, r * 1.12, r), segs=9, rings=4))
        F += part(f"budwin{i}", ellipsoid("buds", "heart", (x, y + 0.4, z - r * 0.92), (r * 0.36, r * 0.4, r * 0.2), segs=6, rings=2))
        F += part(f"budstem{i}", tube("buds", "membrane", [P(x * 0.7, y - r * 1.4, z * 0.6), P(x, y - r * 0.6, z)], ra=[r * 0.5, r * 0.4], sides=6, caps=(True, True)))
    # 脚 6 本 (外へ張って膝を上げ、骨の爪で立つ)
    for sx, fz, i in STORM_LEGS:
        lg, sh = f"leg{i}", f"shin{i}"
        hip = P(sx * 10.0, 15.0, fz * 8.0)
        knee = P(sx * 20.0, 13.0, fz * 14.0)
        foot = P(sx * 23.0, 0.8, fz * 18.0)
        F += part(f"thigh{i}", tube(lg, "meat", [hip, (hip + knee) / 2 + P(0, 3.2, 0), knee], ra=[3.0, 2.6, 2.1], sides=7, caps=(True, True)))
        F += part(f"kneej{i}", ellipsoid(sh, "sinew", tuple(knee), (2.3, 2.3, 2.3), segs=7, rings=3))
        F += part(f"shin{i}", tube(sh, "meat", [knee, (knee + foot) / 2 + P(sx * 0.8, 0, 0), foot + P(0, 1.6, 0)], ra=[2.0, 1.7, 1.3], sides=6, caps=(True, True)))
        F += part(f"claw{i}", cone(sh, "bone", tuple(foot + P(0, 2.2, 0)), (sx * 0.2, -1, fz * 0.2 - 0.1), 3.4, 1.3, sides=5, rings=2))
    return sculpt(F, lambda p: p * 0.86)


# ====================================================================== 灯り喰いの主 (灯の山を吊る黒い口)
ASH_BONES = {"root": (None, (0, 0, 0)), "body": ("root", (0, 26, 0)), "jaw": ("body", (0, 24, -11)),
             "lamps": ("body", (0, 12, 1)), "hooks_l": ("body", (-12, 28, 2)), "hooks_r": ("body", (12, 28, 2))}
ASH_C = np.array([0.0, 26.0, 0.0])
ASH_R = 15.0


def lantern(F, bone, top, length, i, lit, s=1.0):
    """鎖で吊った角灯 (食われた灯り)。鎖は輪をつないだ形、角灯は鉄の籠・煤けたガラス (lit なら青緑の残り火)。"""
    x, y, z = top
    n = max(2, int(length / 1.7))
    for k in range(n):
        yy = y - k * 1.7
        F += part(f"link{i}_{k}", ellipsoid(bone, "iron", (x, yy - 0.8, z), (0.45 if k % 2 else 0.8, 1.05, 0.8 if k % 2 else 0.45), segs=6, rings=2))
    by = y - n * 1.7 - 0.6
    F += part(f"lcap{i}", ellipsoid(bone, "iron", (x, by, z), (1.6 * s, 0.7 * s, 1.6 * s), segs=6, rings=1))
    F += part(f"lglass{i}", ellipsoid(bone, "glass", (x, by - 2.4 * s, z), (1.4 * s, 2.2 * s, 1.4 * s), segs=6, rings=2))
    if lit:
        F += part(f"lflame{i}", ellipsoid(bone, "teal", (x, by - 2.6 * s, z - 1.0 * s), (0.6 * s, 0.9 * s, 0.5 * s), segs=5, rings=2))
    for k in range(4):
        a = math.radians(45 + 90 * k)
        F += part(f"lbar{i}{k}", rod(bone, "iron", (x + math.sin(a) * 1.45 * s, by - 0.2, z - math.cos(a) * 1.45 * s),
                                     (x + math.sin(a) * 1.4 * s, by - 4.4 * s, z - math.cos(a) * 1.4 * s), 0.22, 4))
    F += part(f"lbase{i}", ellipsoid(bone, "iron", (x, by - 4.6 * s, z), (1.5 * s, 0.5 * s, 1.5 * s), segs=6, rings=1))


def build_ash():
    F = []
    b = "body"
    # 体: 濡れた黒の球。煤の板が鱗のように重なる
    F += part("orb", ellipsoid(b, "shadow", tuple(ASH_C), (ASH_R, ASH_R * 0.96, ASH_R), segs=18, rings=9))
    rng = np.random.default_rng(11)
    for i in range(22):
        th = rng.uniform(0, 2 * math.pi)
        ph = rng.uniform(-0.9, 1.25)
        d = np.array([math.cos(ph) * math.sin(th), math.sin(ph), -math.cos(ph) * math.cos(th)])
        if d[2] < -0.55 and abs(d[1]) < 0.6:
            continue  # 口のまわりは空ける
        c = ASH_C + d * (ASH_R - 0.6)
        F += part(f"plate{i}", ellipsoid(b, "soot", tuple(c), (4.2, 3.0, 1.2), segs=7, rings=2,
                                         rot=rot_y(math.degrees(-th) + 180) @ rot_x(math.degrees(ph))))
    # 口: 唇の輪 + 奥の暗がり + 喉の青緑 (食った灯りの残り)。上の歯は体、下の歯は顎
    mc = ASH_C + np.array([0, 0.0, -ASH_R + 2.2])
    F += part("throat", ellipsoid(b, "hollow", tuple(mc + [0, 0, 2.2]), (9.6, 9.2, 2.2), segs=14, rings=5))
    F += part("glowcore", ellipsoid(b, "teal", tuple(mc + [0, 0, 0.9]), (6.6, 6.4, 1.6), segs=12, rings=4))
    F += part("gullet", ellipsoid(b, "hollow", tuple(mc + [0, 0, -0.4]), (2.8, 2.8, 0.9), segs=10, rings=3))
    F += part("lip_up", tube(b, "tar", [mc + [-11.0, -1.0, 2.4], mc + [-8.0, 6.4, 0.2], mc + [0, 9.6, -1.0], mc + [8.0, 6.4, 0.2], mc + [11.0, -1.0, 2.4]],
                             ra=2.0, rb=1.6, sides=6, caps=(True, True)))
    for i in range(7):
        a = math.radians(-78 + i * 26)
        p0 = mc + np.array([9.2 * math.sin(a), 8.0 * math.cos(a) + 0.4, -0.4])
        F += part(f"utooth{i}", cone(b, "tooth", tuple(p0), (-math.sin(a) * 0.5, -1.0, -0.35), 4.6 - abs(i - 3) * 0.4, 1.3, sides=5, rings=2))
    j = "jaw"
    F += part("lip_dn", tube(j, "tar", [mc + [-11.0, -1.4, 2.4], mc + [-8.0, -7.6, 0.4], mc + [0, -10.6, -0.8], mc + [8.0, -7.6, 0.4], mc + [11.0, -1.4, 2.4]],
                             ra=2.0, rb=1.6, sides=6, caps=(True, True)))
    F += part("chin", ellipsoid(j, "tar", tuple(mc + [0, -9.0, 2.0]), (8.0, 3.2, 4.0), segs=10, rings=3))
    for i in range(6):
        a = math.radians(-65 + i * 26)
        p0 = mc + np.array([9.0 * math.sin(a), -8.6 * math.cos(a) - 0.4, -0.4])
        F += part(f"ltooth{i}", cone(j, "tooth", tuple(p0), (-math.sin(a) * 0.5, 1.0, -0.35), 4.0 - abs(i - 2.5) * 0.4, 1.2, sides=5, rings=2))
    # 横の鉤 (折りたたんだ脚のような骨の鉤、左右三本)
    for s in (-1, 1):
        hb = side_bone(s, "hooks")
        for k in range(3):
            y = 31.0 - k * 5.2
            p0 = P(s * 12.6, y, 3.0 - k * 1.0)
            p1 = P(s * 23.0, y + 5.0 - k, 0.0 - k * 1.5)
            p2 = P(s * 25.0, y - 6.0 - k * 1.5, -5.0 - k * 1.5)
            F += part(f"hook{s}{k}", arc(hb, "basalt", p0, p1, p2, r=1.0, n=7, taper=(2.2, 0.35), sides=5))
            F += part(f"hookj{s}{k}", ellipsoid(hb, "soot", tuple(p0), (2.6, 2.6, 2.6), segs=7, rings=3))
    # 下に吊る灯の山 (食った角灯。三つに残り火)
    L = "lamps"
    spots = [(-6.0, 3.0, 9.0, True), (-2.0, -3.4, 13.0, False), (3.4, 4.6, 7.0, False), (7.4, -1.0, 11.0, True), (0.6, 7.0, 15.0, False),
             (-8.6, -4.0, 6.0, False), (4.0, -6.8, 10.0, True), (-3.0, 9.0, 5.0, False)]
    for i, (x, z, ln, lit) in enumerate(spots):
        top_y = ASH_C[1] - math.sqrt(max(0.0, ASH_R ** 2 - x * x - z * z)) + 1.2
        lantern(F, L, (x, top_y, z), ln, i, lit, 0.9 + 0.1 * (i % 3))
    return F


LORDS = {
    "lord_ash": (build_ash, ASH_BONES, dict(tpu=2.5, center=(0, 22, 0), scale=4.4)),
    "lord_storm": (build_storm, {k: (v[0], tuple(c * 0.86 for c in v[1])) for k, v in STORM_BONES.items()}, dict(tpu=2.5, center=(0, 20, 0), scale=4.6)),
    "lord_salt": (build_salt, {k: (v[0], tuple(c * 0.92 for c in v[1])) for k, v in SALT_BONES.items()}, dict(tpu=2.5, center=(0, 18, 0), scale=4.6)),
    "lord_rib": (build_rib, RIB_BONES, dict(tpu=2.5, center=(0, 32, 0), scale=4.2)),
}


def main(names):
    for n in names or LORDS:
        build, bones, kw = LORDS[n]
        K.export(n, build(), bones, **kw)


if __name__ == "__main__":
    main(sys.argv[1:])
