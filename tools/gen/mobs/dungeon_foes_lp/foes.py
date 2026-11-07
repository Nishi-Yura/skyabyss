"""地下のダンジョン専用の敵 12 体 (docs/design/55 の 4 の 7、00 の 372〜383 = みな推奨) の形と動き。
ラフ (tools/rough/lowpoly/dungeon_foes/roughs.py) の選んだ案を、骨に分けて作り込んだもの。長さは 1/16 ブロック、前は -z。
部品は乗り物の低ポリの部品 (mounts_lp/mlp.py)、書き出しは主と同じ (dungeon_bosses_lp/blp.py)。

  道中: pit_digger 骨の坑夫 / bone_roller 骨甲の団子虫 / drowned_sailor 沈み船の水夫 / cave_jelly 洞の水母 /
        horn_beetle 雷角の兜虫 / crystal_eye 晶の目玉 / bellows_imp 鞴の小鬼 / lava_slug 熔岩の蛞蝓
  中ボス: pit_foreman 骨鯨の兜の坑頭 / bell_hermit 鐘負いの大寄居蟹 / geode_golem 晶洞の傀儡 / ox_smith 牛頭の鍛冶番
動きの名前は Java の playAction と同じ (idle・walk は GuardMob が自動で流す)。
"""
import math
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE.parent / "dungeon_bosses_lp"))
from blp import *  # noqa: E402,F401,F403
from blp import keys, eye  # noqa: E402


def P(*a):
    return np.array(a, float)


def pal_(mats, glow=()):
    base = {"eye": M_(EYE, base=0.5, lit=0.2, noise=0.0, min=0)}
    base.update(mats)
    return dict(mats=base, glow_mats=list(glow))


# ---------------------------------------------------------------------- 色 (ラフと同じ)
BONE = [(110, 100, 84), (150, 140, 120), (190, 180, 158), (220, 212, 192), (240, 234, 218)]
FLESH = [(40, 22, 22), (62, 34, 32), (88, 50, 44), (116, 70, 60)]
STONE = [(40, 40, 46), (58, 58, 66), (80, 80, 90), (104, 104, 114)]
ORE = [(150, 90, 30), (210, 140, 50), (250, 196, 96), (255, 236, 170)]
IRON = [(40, 36, 36), (62, 56, 54), (88, 80, 76), (118, 108, 100)]
WOOD = [(46, 30, 18), (70, 46, 28), (98, 66, 40), (128, 90, 56)]
CLOTH = [(40, 34, 30), (62, 54, 46), (86, 76, 64), (112, 100, 84)]
SEA = [(22, 50, 58), (34, 76, 84), (52, 108, 112), (80, 140, 138)]
BARN = [(150, 146, 132), (186, 182, 166), (214, 210, 196), (236, 232, 220)]
CORAL = [(120, 44, 52), (170, 70, 72), (214, 112, 100), (240, 160, 140)]
GLOWSEA = [(40, 150, 160), (90, 210, 210), (170, 245, 240), (230, 255, 252)]
CHIT = [(26, 30, 44), (40, 46, 66), (58, 66, 92), (80, 90, 120)]
CRY = [(60, 120, 200), (110, 180, 240), (170, 220, 255), (230, 245, 255)]
STORM = [(52, 54, 70), (74, 78, 98), (100, 106, 128), (130, 136, 158)]
FUR = [(40, 38, 48), (60, 56, 70), (84, 80, 96), (110, 106, 124)]
BAS = [(24, 22, 24), (38, 34, 36), (54, 48, 50), (72, 64, 64), (94, 84, 82)]
MAG = [(160, 50, 16), (220, 96, 30), (252, 156, 56), (255, 214, 120), (255, 244, 200)]
LEATHR = [(46, 28, 18), (76, 48, 28), (108, 72, 42), (142, 102, 62)]
BRASSY = [(78, 56, 22), (126, 94, 38), (178, 138, 62), (222, 184, 98)]
VERD = [(60, 70, 50), (88, 104, 70), (120, 140, 92), (160, 176, 120)]
STRIPE = [(70, 70, 90), (96, 96, 118), (126, 126, 146), (160, 160, 176)]
SKIN_RED = [(70, 46, 40), (98, 66, 56), (128, 90, 74), (156, 116, 96)]
JELLY = [(80, 120, 160), (110, 156, 196), (150, 196, 226), (200, 230, 246)]
BEETLE = [(30, 30, 40), (46, 46, 62), (66, 66, 88), (92, 92, 118)]
SPARK = [(200, 200, 90), (240, 240, 140), (255, 255, 200), (255, 255, 240)]
SLUG = [(26, 22, 22), (40, 34, 32), (56, 48, 44), (74, 64, 58)]
IMP = [(70, 50, 44), (98, 70, 60), (126, 92, 78), (152, 116, 98)]


# ---------------------------------------------------------------------- 人の形の部品
def leg(m, bone, mat, hip, knee, ank, thick, foot_mat=None, foot=(3.0, 1.8, 4.2)):
    hip, knee, ank = P(*hip), P(*knee), P(*ank)
    m.add(f"{bone}_limb", limb(bone, mat, [hip, knee, ank], [thick, thick * 0.85, thick * 0.7], [thick, thick * 0.85, thick * 0.7], n=8))
    m.add(f"{bone}_foot", ell(bone, foot_mat or mat, ank + P(0, -0.6, -1.2), foot, segs=8, rings=2))


def arm(m, bone, mat, sh, el, wr, r, fist=None, fist_mat=None):
    sh, el, wr = P(*sh), P(*el), P(*wr)
    m.add(f"{bone}_limb", limb(bone, mat, [sh, el, wr], [r, r * 0.85, r * 0.75], [r, r * 0.85, r * 0.75], n=8))
    if fist:
        m.add(f"{bone}_fist", ell(bone, fist_mat or mat, wr + P(0, -1.2, 0), (fist, fist, fist), segs=7, rings=2))
    return wr


def biped_bones(m, hips, torso, head, sh, hip_x, hip_y):
    m.bone("body", "root", hips)
    m.bone("torso", "body", torso)
    m.bone("head", "torso", head)
    m.bone("arm_l", "torso", (sh[0], sh[1], sh[2]))
    m.bone("arm_r", "torso", (-sh[0], sh[1], sh[2]))
    m.bone("leg_l", "body", (hip_x, hip_y, 0))
    m.bone("leg_r", "body", (-hip_x, hip_y, 0))


def biped_gaits(walk_amp=20, period=1.2, death_back=True, sink=12):
    """待機・歩き・被弾・倒れ (人の形)。技は呼ぶ側で足す"""
    g = []
    idle = Gait("idle", 3.0)
    idle.rot("torso", W(0).s(1.6, 1, 0, "t"))
    idle.rot("head", W(0).s(2.4, 1, 60, "t"), W(0).s(3.0, 1, 0, "t"))
    idle.rot("arm_l", W(0).s(2.4, 1, 30, "t"))
    idle.rot("arm_r", W(0).s(2.4, 1, 210, "t"))
    idle.pos("torso", None, W(0).s(0.25, 1, 0, "t"))
    g.append(idle)
    walk = Gait("walk", period)
    walk.rot("leg_l", W(0).s(walk_amp, 1, 0, "t"))
    walk.rot("leg_r", W(0).s(walk_amp, 1, 180, "t"))
    walk.rot("arm_l", W(0).s(-walk_amp * 0.6, 1, 0, "t"))
    walk.rot("arm_r", W(0).s(-walk_amp * 0.6, 1, 180, "t"))
    walk.rot("torso", None, W(0).s(3, 1, 0, "t"))
    walk.pos("body", None, W(0).s(-0.5, 2, 90, "t"))
    g.append(walk)
    g.append(keys("hurt", 0.3, [(0.0, {}), (0.12, {"torso": {"rot": (-8, 0, 3)}, "head": {"rot": (-8, 0, 0)}}), (0.3, {})]))
    knee = {"body": {"pos": (0, -sink * 0.4, 0)}, "leg_l": {"rot": (-50, 0, 0)}, "leg_r": {"rot": (-20, 0, 0)}, "torso": {"rot": (16, 0, 0)}}
    fall = {"body": {"pos": (0, -sink, 4 if death_back else -4), "rot": (-80 if death_back else 80, 0, 0)}, "leg_l": {"rot": (-20, 0, 0)},
            "torso": {"rot": (6, 0, 0)}, "arm_l": {"rot": (-30, 0, 20)}, "arm_r": {"rot": (-40, 0, -20)}}
    g.append(keys("death", 1.4, [(0.0, {}), (0.45, knee), (1.1, fall), (1.4, fall)], hold=True))
    return g


def bug_gaits(legs=("legs_l", "legs_r"), head="head", period=0.6, death_flip=True):
    g = []
    idle = Gait("idle", 2.4)
    idle.rot(head, W(0).s(3, 1, 0, "t"))
    idle.pos("body", None, W(0).s(0.15, 1, 0, "t"))
    g.append(idle)
    walk = Gait("walk", period)
    walk.rot(legs[0], W(0).s(14, 1, 0, "t"), W(0).s(10, 1, 90, "t"))
    walk.rot(legs[1], W(0).s(14, 1, 180, "t"), W(0).s(10, 1, 270, "t"))
    walk.pos("body", None, W(0).s(0.2, 2, 0, "t"))
    g.append(walk)
    g.append(keys("hurt", 0.3, [(0.0, {}), (0.12, {"body": {"rot": (-6, 0, 4)}}), (0.3, {})]))
    flip = {"body": {"rot": (0, 0, 170 if death_flip else 10), "pos": (0, 3, 0)}, legs[0]: {"rot": (0, 0, -30)}, legs[1]: {"rot": (0, 0, 30)}}
    g.append(keys("death", 1.0, [(0.0, {}), (0.6, flip), (1.0, flip)], hold=True))
    return g




# ---------------------------------------------------------------------- 中ボスの動きの作り込み (2026-11-08 の声「ミニボス手抜きだから動きを作ったり、攻撃要素増やしたり」)
def replace_keys(g, name, length, frames, hold=False):
    """同じ名前の動きを置き換える (なければ足す)"""
    for i, x in enumerate(g):
        if x.name == name:
            g[i] = keys(name, length, frames, hold)
            return
    g.append(keys(name, length, frames, hold))


def breath_idle(g, chest="torso", head="head", amp=2.0):
    """待機に胸の上下と首の揺れを重ねる (人の形)"""
    for x in g:
        if x.name == "idle":
            x.rot(head, W(0).s(3.0, 1, 0, "t"), W(0).s(5.0, 1, 90, "t"))
            x.pos(chest, None, W(0).s(amp * 0.2, 2, 0, "t"))


# ====================================================================== 鯨骨の坑

def pit_digger():
    """骨の坑夫 (372 A): 腰の曲がった骸の坑夫。肋骨の籠の胸、布の帽子、鶴嘴を担ぎ、背に灯りの籠"""
    m = Mount("pit_digger", "骨の坑夫")
    biped_bones(m, (0, 14, 0.6), (0, 15, 1), (0, 23.4, -1.6), (4.4, 22.6, -0.5), 3.2, 13)
    for s, b in ((1, "leg_l"), (-1, "leg_r")):
        leg(m, b, "bone", (s * 3.2, 13, 0), (s * 3.6, 6.5, -1.0), (s * 3.5, 2.0, 0), 2.6, foot=(2.4, 1.4, 3.6))
    m.add("pelvis", ell("body", "bone", (0, 14, 0.6), (4.4, 2.6, 3.2), segs=8, rings=2), core=True)
    m.add("spine", tub("torso", "bone", [P(0, 13.6, 1), P(0, 19, 2.5), P(0, 23, 1.0)], [1.3, 1.3, 1.3], None, sides=5))
    m.add("ribs", ell("torso", "bone", (0, 21, -0.6), (5.6, 4.4, 4.4), segs=10, rings=3))
    m.add("ribgap", ell("torso", "flesh", (0, 21, -2.6), (3.6, 3.4, 2.6), segs=8, rings=2))
    m.add("basket", box("torso", "wood", (0, 21, 5.0), (6, 6, 3.6), taper=(0.85, 0.9), cut=0.2))
    m.add("lamp", ell("torso", "ore", (0, 25.4, 5.0), (1.6, 2.0, 1.6), segs=7, rings=2))
    m.add("neck", tub("head", "bone", [P(0, 22, 0.2), P(0, 24.6, -2.4)], [1.2, 1.2], None, sides=5))
    m.add("skull", ell("head", "bone", (0, 25.4, -3.4), (3.2, 3.4, 3.6), segs=9, rings=3))
    m.add("jaw", ell("head", "bone", (0, 23.4, -4.8), (2.4, 1.2, 2.0), segs=7, rings=2))
    m.add("cap", ell("head", "cloth", (0, 27.6, -3.0), (3.6, 1.6, 3.8), segs=9, rings=2))
    for s in (1, -1):
        eye(m, "head", (s * 1.3, 25.8, -6.6), 0.7, (s * 0.2, 0, -1), (255, 190, 90), glow=True)
    arm(m, "arm_l", "bone", (4.4, 22.6, -0.5), (7.5, 18, -3), (6, 14, -6), 1.8, fist=1.6)
    arm(m, "arm_r", "bone", (-4.4, 22.6, -0.5), (-6.5, 26, -2), (-4, 29, -1), 1.8, fist=1.6)
    m.add("haft", tub("arm_r", "wood", [P(-4, 29.6, -2.5), P(-3, 30.4, 10)], [0.8, 0.8], None, sides=5))
    m.add("pick", cone("arm_r", "iron", P(-3, 30.2, 9.2), (0, -0.4, 1), 6.0, 1.0, sides=4, rings=2, bend=(0, -2.0, 0)))
    m.add("pick2", cone("arm_r", "iron", P(-3, 30.2, 9.6), (0, 0.6, -0.2), 4.0, 1.0, sides=4, rings=2, bend=(0, 0, -1.4)))
    m.pal = pal_({"bone": M_(BONE, lit=0.34), "flesh": M_(FLESH), "cloth": M_(CLOTH), "wood": M_(WOOD), "iron": M_(IRON), "ore": M_(ORE, base=0.64, lit=0.2)}, glow=["ore"])
    m.detail = lambda c, col, glow: (speckle(c, col, ["bone"], (120, 110, 92), density=0.06, size=0.8), glow)
    m.view = dict(center=(0, 15, 0), scale=5.0)
    g = biped_gaits(walk_amp=22, period=1.1)
    lift = {"arm_r": {"rot": (-30, 0, 0)}, "torso": {"rot": (-10, 0, 0)}}
    hit = {"arm_r": {"rot": (115, 0, 0)}, "torso": {"rot": (22, 0, 0)}, "head": {"rot": (10, 0, 0)}}
    g.append(keys("swing", 1.25, [(0.0, {}), (0.55, lift), (0.8, hit), (1.0, hit), (1.25, {})]))
    m.gaits = g
    m.poses = [("swing", 0.5), ("swing", 0.8), ("walk", 0.3), ("death", 1.4)]
    return m


def bone_roller():
    """骨甲の団子虫 (373 B): 骨の板を重ねた甲を負う団子虫。丸まって転がり、まっすぐ突っ込む"""
    m = Mount("bone_roller", "骨甲の団子虫")
    m.bone("body", "root", (0, 4, 0))
    m.bone("head", "body", (0, 3.4, -6.2))
    m.bone("legs_l", "body", (3.6, 2.6, 0))
    m.bone("legs_r", "body", (-3.6, 2.6, 0))
    for i in range(7):
        a = math.radians(-70 + 140 * i / 6)
        c = P(0, 3.6 + 2.8 * math.cos(a), -6.4 * math.sin(a))
        m.add(f"plate{i}", ell("body", "bone", c, (5.6 - abs(i - 3) * 0.5, 1.6, 2.4), segs=10, rings=2, rx=-math.degrees(a)), core=i == 3)
    m.add("belly", ell("body", "chitin", (0, 3.4, 0), (4.8, 2.6, 6.0), segs=10, rings=2))
    m.add("headc", ell("head", "chitin", (0, 3.4, -7.0), (2.8, 2.2, 2.2), segs=8, rings=2))
    for s, b in ((1, "legs_l"), (-1, "legs_r")):
        eye(m, "head", (s * 1.4, 4.2, -8.7), 0.5, (s * 0.3, 0.2, -1), (230, 220, 160))
        m.add(f"ant{s}", tub("head", "chitin", [P(s * 1.0, 4.6, -8.4), P(s * 3.2, 6.4, -11), P(s * 4.6, 6.0, -12.6)], [0.4, 0.3, 0.2], None, sides=4))
        for k in range(4):
            z = -4.5 + 3 * k
            m.add(f"leg{s}{k}", tub(b, "chitin", [P(s * 3.0, 2.6, z), P(s * 5.0, 1.6, z - 0.5), P(s * 5.4, 0.3, z - 0.8)], [0.6, 0.5, 0.3], None, sides=4))
    m.pal = pal_({"bone": M_(BONE, lit=0.34), "chitin": M_(CHIT)})
    m.detail = lambda c, col, glow: (edge_dark(c, col, ["bone"], k=0.8), glow)
    m.view = dict(center=(0, 4, -1), scale=7.0)
    g = bug_gaits(period=0.5)
    curl = {"head": {"rot": (60, 0, 0)}, "legs_l": {"rot": (0, 0, -60)}, "legs_r": {"rot": (0, 0, 60)}, "body": {"pos": (0, 1.2, 0)}}
    frames = [(0.0, {}), (0.4, curl)]
    for k in range(1, 9):
        frames.append((0.4 + 0.2 * k, dict(curl, body={"pos": (0, 1.2, 0), "rot": (-90 * k, 0, 0)})))
    frames += [(2.4, dict(curl, body={"pos": (0, 1.2, 0), "rot": (-720, 0, 0)})), (2.7, {})]
    g.append(keys("roll", 2.7, frames))
    m.gaits = g
    m.poses = [("roll", 0.4), ("roll", 0.9), ("walk", 0.15), ("death", 1.0)]
    return m


def pit_foreman():
    """骨鯨の兜の坑頭 (374 A): 人の倍ほどの大男。鯨の頭骨の兜・骨の肩当て、担いだ大きな骨の槌で地を割る"""
    m = Mount("pit_foreman", "骨鯨の兜の坑頭")
    biped_bones(m, (0, 20, 0), (0, 21, 0), (0, 40, -1), (11, 38, 0), 6, 19)
    for s, b in ((1, "leg_l"), (-1, "leg_r")):
        leg(m, b, "leath", (s * 6, 19.5, 0), (s * 6.6, 10, -1.2), (s * 6.4, 3.2, 0), 6.4, foot_mat="leath", foot=(4.6, 2.6, 6.0))
    m.add("hips", ell("body", "leath", (0, 21, 0), (9.6, 4.4, 6.4), segs=12, rings=3), core=True)
    m.add("belly", ell("torso", "flesh", (0, 30, 0), (11, 11, 7.4), segs=14, rings=4))
    m.add("chest", ell("torso", "flesh", (0, 35, -1), (11.6, 6.6, 7.0), segs=14, rings=3))
    m.add("apron", box("torso", "leath", (0, 24, -6.6), (14, 12, 1.4), taper=(1.0, 1.0), cut=0.2))
    m.add("neck", ell("head", "flesh", (0, 40, -1.4), (4.4, 3.2, 4.2), segs=10, rings=2))
    m.add("helm", lof("head", "bone", [rsec(4.6, 51, 40.4, 7.0, n=12), rsec(-1, 53.4, 39.6, 8.0, n=12), rsec(-8, 50, 41, 6.0, n=12), rsec(-14, 46, 43, 2.6, n=12)]))
    m.add("face", ell("head", "flesh", (0, 42.4, -6.2), (4.4, 3.4, 3.0), segs=8, rings=2))
    for s in (1, -1):
        eye(m, "head", (s * 2.6, 46, -8.2), 1.0, (s * 0.2, 0, -1), (255, 170, 70), glow=True)
    for s, b in ((1, "arm_l"), (-1, "arm_r")):
        m.add(f"pauld{s}", ell(b, "bone", (s * 12, 41, 0), (6.0, 3.6, 6.0), segs=10, rings=2))
        m.add(f"spike{s}", cone(b, "bone", P(s * 13, 43, 0), (s * 0.5, 1, 0), 5.0, 1.6, sides=5, rings=2))
    arm(m, "arm_l", "flesh", (11, 38, 0), (15, 28, -3), (13, 20, -8), 4.4, fist=3.2)
    arm(m, "arm_r", "flesh", (-11, 38, 0), (-15, 44, 2), (-11, 50, 4), 4.4, fist=3.2)
    m.add("haft", tub("arm_r", "bone", [P(-11, 49, 2), P(-6, 40, 27)], [1.6, 1.6], None, sides=6))
    m.add("maul", lof("arm_r", "bone", [rsec(25.6, 46, 34, 6, n=10, cx=-6), rsec(30, 47, 33, 7, n=10, cx=-6), rsec(34, 46, 34, 6, n=10, cx=-6)]))
    m.pal = pal_({"bone": M_(BONE, lit=0.34), "flesh": M_([(70, 52, 44), (98, 74, 62), (128, 98, 82), (156, 124, 104)]), "leath": M_(LEATHR)})
    m.detail = lambda c, col, glow: (speckle(c, col, ["bone"], (120, 110, 92), density=0.05, size=0.9), glow)
    m.view = dict(center=(0, 28, 2), scale=2.8)
    g = biped_gaits(walk_amp=16, period=1.6, sink=18)
    up = {"arm_r": {"rot": (-20, 0, 0)}, "torso": {"rot": (-14, 0, 0)}, "head": {"rot": (-8, 0, 0)}}
    slam = {"arm_r": {"rot": (150, 0, 0)}, "torso": {"rot": (26, 0, 0)}, "body": {"pos": (0, -2, 0)}, "head": {"rot": (8, 0, 0)}}
    g.append(keys("slam", 1.7, [(0.0, {}), (0.8, up), (1.0, slam), (1.3, slam), (1.7, {})]))
    wind = {"torso": {"rot": (0, 45, 0)}, "arm_r": {"rot": (60, 30, 0)}}
    swipe = {"torso": {"rot": (0, -50, 0)}, "arm_r": {"rot": (80, -40, 0)}}
    g.append(keys("sweep", 1.35, [(0.0, {}), (0.6, wind), (0.8, swipe), (1.0, swipe), (1.35, {})]))
    lift = {"leg_l": {"rot": (-45, 0, 0)}, "torso": {"rot": (-6, 0, 6)}, "arm_l": {"rot": (-20, 0, 20)}}
    stamp = {"leg_l": {"rot": (4, 0, 0)}, "torso": {"rot": (12, 0, -2)}, "body": {"pos": (0, -2, 0)}}
    g.append(keys("stomp", 1.4, [(0.0, {}), (0.7, lift), (0.9, stamp), (1.1, stamp), (1.4, {})]))
    # 作り込み: 地割りは溜め (腰を落とし槌を背へ) → 振り下ろし (体ごと前へ) → 反動 → 担ぎ直し
    crouch = {"body": {"pos": (0, -2.4, 1.0)}, "torso": {"rot": (-22, 10, 0)}, "arm_r": {"rot": (-40, 0, 10)}, "arm_l": {"rot": (-30, 0, 20)},
              "head": {"rot": (-14, 0, 0)}, "leg_l": {"rot": (-24, 0, 0)}, "leg_r": {"rot": (18, 0, 0)}}
    hit = {"body": {"pos": (0, -3.6, -2.4)}, "torso": {"rot": (34, -6, 0)}, "arm_r": {"rot": (160, 0, -6)}, "arm_l": {"rot": (40, 0, 10)},
           "head": {"rot": (12, 0, 0)}, "leg_l": {"rot": (-36, 0, 0)}, "leg_r": {"rot": (24, 0, 0)}}
    rebound = {"body": {"pos": (0, -2.8, -1.8)}, "torso": {"rot": (24, -4, 0)}, "arm_r": {"rot": (138, 0, -6)}, "head": {"rot": (4, 0, 0)}}
    replace_keys(g, "slam", 1.7, [(0.0, {}), (0.55, crouch), (0.8, crouch), (0.95, hit), (1.1, rebound), (1.3, hit), (1.7, {})])
    # 跳び割り (新): 屈む → 高く跳ぶ → 落ちて槌を地に打つ
    squat = {"body": {"pos": (0, -4.0, 0)}, "torso": {"rot": (20, 0, 0)}, "arm_r": {"rot": (-30, 0, 0)}, "leg_l": {"rot": (-50, 0, 0)}, "leg_r": {"rot": (-50, 0, 0)}}
    air = {"body": {"pos": (0, 10.0, -6.0)}, "torso": {"rot": (-20, 0, 0)}, "arm_r": {"rot": (-170, 0, 0)}, "arm_l": {"rot": (-120, 0, 30)},
           "leg_l": {"rot": (-30, 0, 0)}, "leg_r": {"rot": (20, 0, 0)}}
    land = {"body": {"pos": (0, -3.0, -10.0)}, "torso": {"rot": (36, 0, 0)}, "arm_r": {"rot": (150, 0, 0)}, "arm_l": {"rot": (30, 0, 20)},
            "leg_l": {"rot": (-40, 0, 0)}, "leg_r": {"rot": (30, 0, 0)}}
    g.append(keys("leap", 2.0, [(0.0, {}), (0.5, squat), (0.9, air), (1.15, land), (1.5, land), (2.0, {})]))
    # 骨投げ (新): 左手で背の骨塊を掴み、振りかぶって投げる
    grab = {"arm_l": {"rot": (-150, 0, 40)}, "torso": {"rot": (-10, 30, 0)}, "head": {"rot": (-10, 20, 0)}}
    wind = {"arm_l": {"rot": (-170, 0, 10)}, "torso": {"rot": (-18, 40, 0)}, "leg_r": {"rot": (20, 0, 0)}}
    throw = {"arm_l": {"rot": (-30, 0, -10)}, "torso": {"rot": (20, -30, 0)}, "head": {"rot": (6, -20, 0)}, "leg_l": {"rot": (-24, 0, 0)}}
    g.append(keys("throw", 1.6, [(0.0, {}), (0.4, grab), (0.8, wind), (1.0, throw), (1.2, throw), (1.6, {})]))
    breath_idle(g)
    m.poses = [("slam", 0.8), ("slam", 0.95), ("leap", 0.9), ("leap", 1.2), ("throw", 0.8), ("throw", 1.05)]
    m.gaits = g
    m.poses = [("slam", 0.8), ("slam", 1.05), ("sweep", 0.8), ("stomp", 0.7)]
    return m


# ====================================================================== 潮の洞

def drowned_sailor():
    """沈み船の水夫 (375 A): ふくれた水死人の水夫。縞の服に藤壺と貝の殻、手に銛"""
    m = Mount("drowned_sailor", "沈み船の水夫")
    biped_bones(m, (0, 14, 0), (0, 15, 0), (0, 26.2, -0.5), (5.6, 23.6, 0), 3.2, 13)
    for s, b in ((1, "leg_l"), (-1, "leg_r")):
        leg(m, b, "cloth", (s * 3.2, 13.5, 0), (s * 3.6, 7, -1), (s * 3.5, 2, 0), 3.4, foot_mat="sea")
    m.add("hips", ell("body", "cloth", (0, 14, 0), (5.4, 2.8, 3.6), segs=10, rings=2), core=True)
    m.add("torso_c", ell("torso", "cloth", (0, 19.4, 0), (6.4, 7.4, 4.4), segs=12, rings=4))
    m.add("belly", ell("torso", "sea", (0, 17.6, -2.2), (4.6, 4.4, 3.0), segs=10, rings=3))
    for k, (x, y, z) in enumerate(((3, 22, -3.6), (-4, 18, -3.6), (5.0, 24, 1), (-2, 25.6, 3))):
        m.add(f"barn{k}", cone("torso", "barn", P(x, y, z), (x * 0.1, 0.2, z * 0.1), 1.6, 1.2, sides=6, rings=1))
    m.add("neck", ell("head", "sea", (0, 26.0, -0.4), (2.2, 1.8, 2.2), segs=8, rings=2))
    m.add("headc", ell("head", "sea", (0, 29, -0.6), (3.4, 3.6, 3.4), segs=9, rings=3))
    m.add("hat", lof("head", "cloth", [ysec(31.2, 9, 9), ysec(32.2, 6.4, 6.4), ysec(34.0, 5.0, 5.0)]))
    for s in (1, -1):
        eye(m, "head", (s * 1.3, 29.4, -3.6), 0.7, (s * 0.2, 0, -1), (170, 255, 240), glow=True)
    arm(m, "arm_l", "sea", (5.6, 23.6, 0), (8, 18, -2), (6, 14, -5), 2.6, fist=1.8)
    arm(m, "arm_r", "sea", (-5.6, 23.6, 0), (-8, 22, -4), (-5, 24, -9), 2.6, fist=1.8)
    m.add("spear", tub("arm_r", "wood", [P(-5, 22.8, -1), P(-5, 22.8, -22)], [0.6, 0.6], None, sides=5))
    m.add("tip", cone("arm_r", "iron", P(-5, 22.8, -21.6), (0, 0, -1), 3.4, 1.0, sides=4, rings=1))
    m.pal = pal_({"sea": M_(SEA, streak=0.05), "cloth": M_(STRIPE, stripe=4, axis="u"), "barn": M_(BARN, lit=0.34), "wood": M_(WOOD), "iron": M_(IRON)})
    m.detail = lambda c, col, glow: (speckle(c, col, ["sea"], (30, 90, 80), density=0.08, size=0.8), glow)
    m.view = dict(center=(0, 17, -4), scale=4.6)
    g = biped_gaits(walk_amp=18, period=1.3)
    cock = {"arm_r": {"rot": (-70, 20, 0)}, "torso": {"rot": (0, -25, 0)}}
    throw = {"arm_r": {"rot": (20, -10, 0)}, "torso": {"rot": (10, 20, 0)}}
    g.append(keys("throw", 1.2, [(0.0, {}), (0.6, cock), (0.8, throw), (1.0, throw), (1.2, {})]))
    m.gaits = g
    m.poses = [("throw", 0.6), ("throw", 0.85), ("walk", 0.3), ("death", 1.4)]
    return m


def cave_jelly():
    """洞の水母 (376 C): 傘の下に光る糸を垂らして漂う。触れると痺れる"""
    m = Mount("cave_jelly", "洞の水母")
    m.bone("body", "root", (0, 18, 0))
    m.bone("threads", "body", (0, 18.4, 0))
    m.add("bell", lof("body", "jelly", [ysec(26, 1.0, 1.0), ysec(25, 6, 6), ysec(22, 10, 10), ysec(18.6, 11, 11), ysec(18, 9, 9)]), core=True)
    m.add("core", ell("body", "glow", (0, 21.4, 0), (2.4, 2.0, 2.4), segs=8, rings=2))
    for k in range(10):
        a = math.radians(36 * k)
        r = 4.4
        root = P(math.cos(a) * r, 19.0, math.sin(a) * r)
        m.add(f"thread{k}", tub("threads", "glow", [root, root + P(math.cos(a) * 0.8, -6, math.sin(a) * 0.8), root + P(math.cos(a) * 0.2, -13 - (k % 3) * 2, math.sin(a) * 0.2)],
                                   [0.35, 0.3, 0.15], None, sides=4))
    for k in range(4):
        a = math.radians(45 + 90 * k)
        m.add(f"frill{k}", tub("threads", "jelly", [P(math.cos(a) * 1.4, 19.2, math.sin(a) * 1.4), P(math.cos(a) * 2.0, 12, math.sin(a) * 2.0),
                                                     P(math.cos(a) * 1.2, 6, math.sin(a) * 1.2)], [1.2, 1.0, 0.4], None, sides=5))
    m.pal = pal_({"jelly": M_(JELLY, base=0.6, lit=0.3), "glow": M_(GLOWSEA, base=0.7, lit=0.2)}, glow=["glow"])
    m.detail = lambda c, col, glow: (bands_y(c, col, ["jelly"], 2.0, 0.5, k=0.88), glow)
    m.view = dict(center=(0, 14, 0), scale=4.6)
    g = []
    idle = Gait("idle", 2.6)
    idle.pos("body", None, W(0).s(0.8, 1, 0, "t"))
    idle.rot("threads", W(0).s(6, 1, 90, "t"), None, W(0).s(5, 1, 0, "t"))
    g.append(idle)
    walk = Gait("walk", 1.8)
    walk.pos("body", None, W(0).s(1.0, 1, 0, "t"))
    walk.rot("body", W(0).s(-6, 1, 0, "t"))
    walk.rot("threads", W(14).s(6, 1, 90, "t"))
    g.append(walk)
    squeeze = {"body": {"pos": (0, -1.6, 0)}, "threads": {"rot": (0, 30, 0)}}
    burst = {"body": {"pos": (0, 1.6, 0)}, "threads": {"rot": (0, -20, 0)}}
    g.append(keys("pulse", 1.1, [(0.0, {}), (0.6, squeeze), (0.75, burst), (0.9, burst), (1.1, {})]))
    g.append(keys("hurt", 0.3, [(0.0, {}), (0.12, {"body": {"rot": (8, 0, 4)}}), (0.3, {})]))
    sink = {"body": {"pos": (0, -10, 0), "rot": (30, 0, 20)}, "threads": {"rot": (40, 0, 0)}}
    g.append(keys("death", 1.2, [(0.0, {}), (1.0, sink), (1.2, sink)], hold=True))
    m.gaits = g
    m.poses = [("pulse", 0.6), ("pulse", 0.75), ("idle", 1.0), ("death", 1.2)]
    return m


def bell_hermit():
    """鐘負いの大寄居蟹 (377 A): 沈んだ船の鐘を殻にした大寄居蟹。鐘を鳴らすと波、大きな右の鋏"""
    m = Mount("bell_hermit", "鐘負いの大寄居蟹")
    m.bone("body", "root", (0, 12, 0))
    m.bone("shell", "body", (0, 18, 4))
    m.bone("claw_r", "body", (-7, 12, -10))
    m.bone("claw_l", "body", (7, 12, -10))
    m.bone("legs_l", "body", (8, 12, 0))
    m.bone("legs_r", "body", (-8, 12, 0))
    m.add("front", ell("body", "shell", (0, 13, -6), (9.0, 6.0, 7.0), segs=12, rings=3), core=True)
    m.add("bell", lof("shell", "brass", [rsec(14, 30, 8, 13, n=14, mid=18), rsec(4, 36, 8, 14, n=14, mid=20), rsec(-4, 34, 10, 12, n=14, mid=20)]))
    m.add("lip", lof("shell", "brass", [rsec(16, 26, 6, 15, n=14, mid=16), rsec(13.6, 27, 6, 14.6, n=14, mid=16)]))
    m.add("crown", ell("shell", "wood", (0, 35.6, 4), (4.6, 1.6, 4.6), segs=8, rings=2))
    for s, b in ((1, "legs_l"), (-1, "legs_r")):
        eye(m, "body", (s * 3.0, 20, -11), 1.0, (s * 0.2, 0.3, -1), (40, 30, 20))
        m.add(f"stalk{s}", tub("body", "shell", [P(s * 2.6, 15.6, -9.6), P(s * 3.0, 19.4, -11)], [0.8, 0.7], None, sides=5))
        for k in range(3):
            z = -6 + 5 * k
            m.add(f"leg{s}{k}", tub(b, "shell", [P(s * 7, 12, z), P(s * 15, 15, z - 1), P(s * 18, 0.6, z - 2)], [1.8, 1.4, 0.6], None, sides=5))
    m.add("cr_arm", limb("claw_r", "shell", [P(-6, 12, -9), P(-12, 14, -16), P(-10, 12, -22)], [3, 2.6, 2.4], [3, 2.6, 2.4], n=8))
    m.add("cr_claw", lof("claw_r", "shell", [rsec(-20.6, 16, 6, 4.6, n=12, cx=-10), rsec(-27, 17, 6, 5.6, n=12, cx=-10), rsec(-32, 14, 9, 3.0, n=12, cx=-10)]))
    m.add("cr_fing", cone("claw_r", "shell", P(-10, 8, -25.4), (0, 0.2, -1), 7, 1.8, sides=5, rings=2, bend=(0, 2.0, 0)))
    m.add("cl_arm", limb("claw_l", "shell", [P(6, 12, -9), P(10, 12, -15), P(9, 10, -18)], [2, 1.8, 1.6], [2, 1.8, 1.6], n=7))
    m.add("cl_claw", ell("claw_l", "shell", (9, 10, -20), (2.6, 2.2, 3.4), segs=8, rings=2))
    m.pal = pal_({"brass": M_(VERD, lit=0.36, streak=0.06), "shell": M_(CORAL, lit=0.32), "wood": M_(WOOD)})
    m.detail = lambda c, col, glow: (speckle(c, col, ["brass"], (80, 140, 120), density=0.10, size=1.0), glow)
    m.view = dict(center=(0, 16, -4), scale=2.8)
    g = bug_gaits(period=0.9, death_flip=False)
    g[0].rot("shell", None, None, W(0).s(1.5, 1, 0, "t"))
    raise_ = {"claw_r": {"rot": (-40, 10, 0)}}
    pinch = {"claw_r": {"rot": (20, -10, 0)}, "body": {"pos": (0, 0, -1.4)}}
    g.append(keys("pinch", 1.2, [(0.0, {}), (0.6, raise_), (0.8, pinch), (0.95, pinch), (1.2, {})]))
    frames = [(0.0, {})]
    for k in range(6):
        frames.append((0.3 + 0.18 * k, {"shell": {"rot": (0, 0, 14 if k % 2 == 0 else -14)}, "body": {"pos": (0, -1.0, 0)}}))
    frames += [(1.5, {"shell": {"rot": (0, 0, 0)}}), (1.8, {})]
    g.append(keys("ring", 1.8, frames))
    duck = {"body": {"pos": (0, -3, 0), "rot": (10, 0, 0)}, "claw_r": {"rot": (30, 0, 0)}, "claw_l": {"rot": (30, 0, 0)}}
    g.append(keys("charge", 2.2, [(0.0, {}), (0.6, duck), (2.0, duck), (2.2, {})]))
    # 作り込み: 鋏は振りかぶりを大きく、挟んで引く
    raise2 = {"claw_r": {"rot": (-60, 20, 0)}, "body": {"pos": (0, 1.2, 1.0), "rot": (-8, 0, 0)}, "shell": {"rot": (0, 0, 6)}}
    snap = {"claw_r": {"rot": (30, -20, 0)}, "body": {"pos": (0, -0.8, -2.4), "rot": (8, 0, 0)}, "shell": {"rot": (0, 0, -4)}}
    pull = {"claw_r": {"rot": (0, -10, 0)}, "body": {"pos": (0, 0, 0.8)}}
    replace_keys(g, "pinch", 1.2, [(0.0, {}), (0.5, raise2), (0.75, snap), (0.9, snap), (1.05, pull), (1.2, {})])
    # 殻回し (新): 殻にこもって二回まわる
    frames = [(0.0, {}), (0.4, {"body": {"pos": (0, -2.0, 0)}, "claw_r": {"rot": (40, 0, 0)}, "claw_l": {"rot": (40, 0, 0)}})]
    for k in range(1, 5):
        frames.append((0.4 + 0.3 * k, {"body": {"pos": (0, -2.0, 0), "rot": (0, 180 * k, 0)}, "claw_r": {"rot": (40, 0, 0)}, "claw_l": {"rot": (40, 0, 0)}}))
    frames += [(2.0, {})]
    g.append(keys("spin", 2.0, frames))
    # 泡吐き (新): 身を起こして三度吐く
    rear = {"body": {"rot": (-18, 0, 0), "pos": (0, 2.0, 0)}, "claw_r": {"rot": (-30, 0, 0)}, "claw_l": {"rot": (-30, 0, 0)}}
    spit = {"body": {"rot": (6, 0, 0), "pos": (0, 0.6, -1.0)}}
    g.append(keys("spit", 1.8, [(0.0, {}), (0.4, rear), (0.6, spit), (0.8, rear), (1.0, spit), (1.2, rear), (1.4, spit), (1.8, {})]))
    m.poses = [("pinch", 0.75), ("spin", 1.0), ("spit", 0.6), ("ring", 0.48)]
    m.gaits = g
    m.poses = [("pinch", 0.55), ("ring", 0.3), ("ring", 0.48), ("charge", 1.0)]
    return m


# ====================================================================== 雷晶の空洞

def horn_beetle():
    """雷角の兜虫 (378 B): 二本の大きな結晶の角。角の間に雷を溜めて前へ放つ"""
    m = Mount("horn_beetle", "雷角の兜虫")
    m.bone("body", "root", (0, 7, 0))
    m.bone("head", "body", (0, 9, -7))
    m.bone("legs_l", "body", (5, 5, 0))
    m.bone("legs_r", "body", (-5, 5, 0))
    m.add("elytra", lof("body", "chitin", [rsec(10, 10, 4, 6, n=12), rsec(3, 14, 4, 8, n=12), rsec(-4, 13, 4, 7.4, n=12)]), core=True)
    m.add("pronotum", ell("body", "chitin", (0, 11, -6.0), (6.6, 4.4, 4.0), segs=10, rings=3))
    m.add("headc", ell("head", "chitin", (0, 7.6, -9.6), (3.4, 2.6, 2.8), segs=8, rings=2))
    m.add("horn_up", cone("head", "cry", P(0, 13.4, -7), (0, 0.6, -1), 12, 1.6, sides=6, rings=3, bend=(0, 4.0, 0)))
    m.add("horn_lo", cone("head", "cry", P(0, 7.6, -11.6), (0, 0.2, -1), 9, 1.4, sides=6, rings=3, bend=(0, 3.0, 0)))
    m.add("spark", ell("head", "glow", (0, 13, -19), (1.4, 1.4, 1.4), segs=7, rings=2))
    for s, b in ((1, "legs_l"), (-1, "legs_r")):
        eye(m, "head", (s * 2.2, 8.2, -11.8), 0.6, (s * 0.4, 0, -1), (255, 240, 120), glow=True)
        for k in range(3):
            z = -5 + 5 * k
            m.add(f"leg{s}{k}", tub(b, "chitin", [P(s * 4.6, 5, z), P(s * 9.4, 7, z), P(s * 11, 0.4, z - 1)], [1.0, 0.8, 0.3], None, sides=4))
    m.pal = pal_({"chitin": M_(BEETLE, lit=0.34), "cry": M_(CRY, base=0.6, lit=0.26), "glow": M_(SPARK, base=0.72, lit=0.2)}, glow=["cry", "glow"])
    m.detail = lambda c, col, glow: (edge_dark(c, col, ["chitin"], k=0.8), glow)
    m.view = dict(center=(0, 8, -4), scale=5.0)
    g = bug_gaits(period=0.55)
    rear = {"head": {"rot": (-22, 0, 0)}, "body": {"rot": (-8, 0, 0), "pos": (0, 0.6, 0)}}
    fire = {"head": {"rot": (14, 0, 0)}, "body": {"rot": (6, 0, 0), "pos": (0, 0, -1)}}
    g.append(keys("bolt", 1.6, [(0.0, {}), (0.4, rear), (1.05, rear), (1.2, fire), (1.35, fire), (1.6, {})]))
    m.gaits = g
    m.poses = [("bolt", 0.6), ("bolt", 1.25), ("walk", 0.1), ("death", 1.0)]
    return m


def crystal_eye():
    """晶の目玉 (379 B): 結晶の目玉が小さな翼で浮く。見つめた所に雷を落とす"""
    m = Mount("crystal_eye", "晶の目玉")
    m.bone("body", "root", (0, 18, 0))
    m.bone("ring", "body", (0, 18, 0))
    m.bone("wing_l", "body", (3.4, 18.4, 0))
    m.bone("wing_r", "body", (-3.4, 18.4, 0))
    m.add("orb", ell("body", "stone", (0, 18, 0), (4.2, 4.2, 4.2), segs=12, rings=4), core=True)
    m.add("lens", ell("body", "cry", (0, 18, -3.4), (2.4, 2.4, 1.2), segs=10, rings=2))
    m.eyes.append(dict(c=P(0, 18, -4.5), r=1.2, dir=(0, 0, -1), a=1.0, b=1.0, bone="body", iris=(140, 220, 255), lid=(10, 10, 12), pa=0.5, pb=0.5, glow=True))
    for k in range(5):
        a = math.radians(-60 + 30 * k)
        m.add(f"shard{k}", cone("ring", "cry", P(math.sin(a) * 3.2, 18 + math.cos(a) * 3.2, 1.0), (math.sin(a), math.cos(a), 0.3), 3.4, 0.8, sides=5, rings=1))
    for s, b in ((1, "wing_l"), (-1, "wing_r")):
        m.add(f"wing{s}", wing(b, "memb", [(s * 3.0, -1.0, 2.0, 18.4, 0.4), (s * 7, -1.4, 3.0, 19.6, 0.3), (s * 10, -0.4, 1.4, 19.0, 0.2)]))
    m.add("tail", tub("body", "stone", [P(0, 15.4, 2.6), P(0, 11, 5), P(0, 8, 4)], [0.8, 0.5, 0.2], None, sides=4))
    m.pal = pal_({"stone": M_(STORM, lit=0.32), "cry": M_(CRY, base=0.62, lit=0.24), "memb": M_([(40, 40, 60), (60, 60, 86), (84, 84, 112), (110, 110, 140)])}, glow=["cry"])
    m.detail = lambda c, col, glow: (plates(c, col, ["stone"], size=2.0, edge=0.06, k_edge=0.7), glow)
    m.view = dict(center=(0, 16, 0), scale=6.0)
    g = []
    idle = Gait("idle", 1.0)
    idle.rot("wing_l", None, None, W(0).s(28, 1, 0, "t"))
    idle.rot("wing_r", None, None, W(0).s(-28, 1, 0, "t"))
    idle.pos("body", None, W(0).s(0.6, 1, 90, "t"))
    g.append(idle)
    walk = Gait("walk", 0.6)
    walk.rot("wing_l", None, None, W(0).s(34, 1, 0, "t"))
    walk.rot("wing_r", None, None, W(0).s(-34, 1, 0, "t"))
    walk.rot("body", W(6))
    g.append(walk)
    stare = {"body": {"rot": (-6, 0, 0)}, "ring": {"rot": (0, 0, 120)}}
    g.append(keys("gaze", 1.6, [(0.0, {}), (0.4, stare), (1.2, {"body": {"rot": (-6, 0, 0)}, "ring": {"rot": (0, 0, 360)}}), (1.6, {})]))
    g.append(keys("hurt", 0.3, [(0.0, {}), (0.12, {"body": {"rot": (10, 0, 6)}}), (0.3, {})]))
    drop = {"body": {"pos": (0, -14, 0), "rot": (40, 0, 30)}, "wing_l": {"rot": (0, 0, -40)}, "wing_r": {"rot": (0, 0, 40)}}
    g.append(keys("death", 1.0, [(0.0, {}), (0.8, drop), (1.0, drop)], hold=True))
    m.gaits = g
    m.poses = [("gaze", 0.4), ("gaze", 1.0), ("idle", 0.25), ("death", 1.0)]
    return m


def geode_golem():
    """晶洞の傀儡 (380 A): 割れた晶洞を胴にした岩の巨人。胸の内側の結晶が弱点、大きな岩の拳"""
    m = Mount("geode_golem", "晶洞の傀儡")
    biped_bones(m, (0, 20, 0), (0, 23, 0), (0, 44, -1), (12.5, 40, 0), 6.4, 18)
    for s, b in ((1, "leg_l"), (-1, "leg_r")):
        leg(m, b, "stone", (s * 6.4, 19, 0), (s * 7, 10, -1), (s * 6.8, 3.2, 0), 7.0, foot_mat="stone", foot=(5.0, 2.8, 6.4))
    m.add("pelvis", ell("body", "stone", (0, 21.5, 1), (9.6, 4.6, 6.6), segs=12, rings=3), core=True)
    m.add("torso_c", ell("torso", "stone", (0, 33, 1.0), (13, 12, 10), segs=14, rings=4))
    m.add("hollow", ell("torso", "cry", (0, 33, -7.6), (8.6, 8.0, 3.2), segs=12, rings=3))
    for k in range(8):
        a = math.radians(45 * k)
        m.add(f"inner{k}", cone("torso", "cry", P(math.cos(a) * 6.0, 33 + math.sin(a) * 5.6, -6.6), (-math.cos(a) * 0.7, -math.sin(a) * 0.7, -0.6), 4, 1.4, sides=6, rings=1))
    m.add("headc", ell("head", "stone", (0, 47, -1.0), (6, 5, 5.4), segs=10, rings=3))
    m.add("visor", ell("head", "cry", (0, 47, -5.6), (4.0, 1.4, 1.2), segs=8, rings=1))
    for s, b in ((1, "arm_l"), (-1, "arm_r")):
        m.add(f"shoulder{s}", ell(b, "stone", (s * 12.5, 40.5, 0), (5.4, 5.4, 5.4), segs=10, rings=3))
        arm(m, b, "stone", (s * 13, 40, 0), (s * 19, 30, -2), (s * 18, 21, -5), 5.6, fist=5.0)
    m.pal = pal_({"stone": M_(STORM, lit=0.32, streak=0.04), "cry": M_(CRY, base=0.64, lit=0.24)}, glow=["cry"])
    m.detail = lambda c, col, glow: (plates(c, col, ["stone"], size=4.4, edge=0.05, k_edge=0.6), glow)
    m.view = dict(center=(0, 28, -2), scale=2.8)
    g = biped_gaits(walk_amp=14, period=1.8, sink=18)
    pull = {"arm_r": {"rot": (-40, 0, -10)}, "torso": {"rot": (0, -20, 0)}}
    punch = {"arm_r": {"rot": (-85, 0, 0)}, "torso": {"rot": (8, 25, 0)}}
    g.append(keys("punch", 1.3, [(0.0, {}), (0.6, pull), (0.8, punch), (1.0, punch), (1.3, {})]))
    both = {"arm_l": {"rot": (-160, 0, 10)}, "arm_r": {"rot": (-160, 0, -10)}, "torso": {"rot": (-10, 0, 0)}}
    down = {"arm_l": {"rot": (-30, 0, 0)}, "arm_r": {"rot": (-30, 0, 0)}, "torso": {"rot": (30, 0, 0)}, "body": {"pos": (0, -3, 0)}}
    g.append(keys("pound", 1.8, [(0.0, {}), (0.8, both), (1.05, down), (1.35, down), (1.8, {})]))
    arch = {"torso": {"rot": (-24, 0, 0)}, "arm_l": {"rot": (0, 0, 50)}, "arm_r": {"rot": (0, 0, -50)}, "head": {"rot": (-20, 0, 0)}}
    g.append(keys("shards", 1.8, [(0.0, {}), (0.7, arch), (1.4, arch), (1.8, {})]))
    # 作り込み: 拳は体をひねって打ち抜く
    pull2 = {"arm_r": {"rot": (-30, 0, -30)}, "torso": {"rot": (-6, -35, 0)}, "head": {"rot": (0, 10, 0)}, "leg_r": {"rot": (14, 0, 0)}}
    punch2 = {"arm_r": {"rot": (-95, 0, 0)}, "torso": {"rot": (12, 30, 0)}, "head": {"rot": (6, -10, 0)}, "body": {"pos": (0, -1.0, -3.0)}, "leg_l": {"rot": (-24, 0, 0)}}
    replace_keys(g, "punch", 1.3, [(0.0, {}), (0.55, pull2), (0.75, punch2), (0.95, punch2), (1.3, {})])
    # 岩落とし (新): 両腕で天井を打ち、岩が降る
    up = {"arm_l": {"rot": (-175, 0, 20)}, "arm_r": {"rot": (-175, 0, -20)}, "torso": {"rot": (-24, 0, 0)}, "head": {"rot": (-30, 0, 0)}}
    shake = {"arm_l": {"rot": (-165, 0, 30)}, "arm_r": {"rot": (-165, 0, -30)}, "torso": {"rot": (-18, 0, 4)}, "body": {"pos": (0, 1.0, 0)}}
    g.append(keys("rockfall", 1.8, [(0.0, {}), (0.5, up), (0.7, shake), (0.85, up), (1.0, shake), (1.4, up), (1.8, {})]))
    # 突進 (新): 前かがみで肩から突っ込む
    lean = {"torso": {"rot": (34, 0, 0)}, "arm_l": {"rot": (20, 0, 30)}, "arm_r": {"rot": (20, 0, -30)}, "head": {"rot": (-24, 0, 0)}, "body": {"pos": (0, -2.0, 0)}}
    run = {"torso": {"rot": (40, 0, 0)}, "leg_l": {"rot": (-40, 0, 0)}, "leg_r": {"rot": (30, 0, 0)}, "arm_l": {"rot": (30, 0, 30)}, "arm_r": {"rot": (10, 0, -30)}}
    run2 = {"torso": {"rot": (40, 0, 0)}, "leg_l": {"rot": (30, 0, 0)}, "leg_r": {"rot": (-40, 0, 0)}, "arm_l": {"rot": (10, 0, 30)}, "arm_r": {"rot": (30, 0, -30)}}
    g.append(keys("charge", 2.2, [(0.0, {}), (0.6, lean), (0.9, run), (1.1, run2), (1.3, run), (1.5, run2), (1.7, run), (2.2, {})]))
    breath_idle(g)
    m.poses = [("punch", 0.75), ("rockfall", 0.7), ("charge", 1.1), ("pound", 1.05)]
    m.gaits = g
    m.poses = [("punch", 0.8), ("pound", 0.8), ("pound", 1.1), ("shards", 1.0)]
    return m


# ====================================================================== 熔けた坑道

def bellows_imp():
    """鞴の小鬼 (381 B): 背に鞴を負った背の低い小鬼。鉄の仮面と長い火掻き棒、鞴で前へ火を吹きつける"""
    m = Mount("bellows_imp", "鞴の小鬼")
    biped_bones(m, (0, 9, 0), (0, 10, 0), (0, 17.4, -1.2), (4.2, 15, 0), 2.8, 8.6)
    for s, b in ((1, "leg_l"), (-1, "leg_r")):
        leg(m, b, "skin", (s * 2.8, 9.4, 0), (s * 3.0, 5, -0.8), (s * 3.0, 1.8, 0), 2.8, foot_mat="iron", foot=(2.4, 1.4, 3.4))
    m.add("hips", ell("body", "leath", (0, 9.4, 0), (4.2, 2.4, 3.4), segs=10, rings=2), core=True)
    m.add("torso_c", ell("torso", "skin", (0, 13, 0), (5.0, 5.4, 4.2), segs=10, rings=3))
    m.add("bellows", lof("torso", "leath", [rsec(3.4, 17, 9, 4.6, n=10), rsec(6.6, 16, 10, 3.6, n=10), rsec(8.4, 15, 11, 1.6, n=10)]))
    m.add("nozzle", tub("torso", "iron", [P(0, 16.6, 3.6), P(4, 21, -1), P(5.6, 19.6, -7.6)], [0.7, 0.6, 0.5], None, sides=5))
    m.add("headc", ell("head", "skin", (0, 20, -1.4), (3.6, 3.4, 3.4), segs=9, rings=3))
    m.add("mask", ell("head", "iron", (0, 20, -3.8), (3.4, 3.2, 1.2), segs=9, rings=2))
    for s in (1, -1):
        eye(m, "head", (s * 1.3, 20.6, -4.9), 0.55, (0, 0, -1), (255, 160, 40), glow=True)
        m.add(f"ear{s}", cone("head", "skin", P(s * 3.0, 21, -0.6), (s, 0.4, 0.3), 4.0, 1.0, sides=4, rings=1))
    arm(m, "arm_l", "skin", (4.2, 15, 0), (6.4, 12, -2), (6, 9, -4), 1.8, fist=1.4)
    arm(m, "arm_r", "skin", (-4.2, 15, 0), (-6.4, 13, -3), (-5, 14, -7), 1.8, fist=1.4)
    m.add("poker", tub("arm_r", "iron", [P(-5, 14, -5.4), P(-5, 10, -20)], [0.5, 0.5], None, sides=5))
    m.add("hook", cone("arm_r", "magma", P(-5, 10.2, -19.6), (0, -0.6, -0.6), 2.4, 0.6, sides=4, rings=1))
    m.pal = pal_({"skin": M_(IMP), "iron": M_(IRON), "leath": M_(LEATHR, streak=0.06), "magma": M_(MAG, base=0.68, lit=0.2)}, glow=["magma"])
    m.detail = lambda c, col, glow: (speckle(c, col, ["skin"], (50, 40, 36), density=0.08, size=0.8), glow)
    m.view = dict(center=(0, 12, -2), scale=5.4)
    g = biped_gaits(walk_amp=24, period=0.9, sink=8)
    pump = {"torso": {"rot": (14, 0, 0)}, "head": {"rot": (-10, 0, 0)}, "arm_l": {"rot": (-40, 0, 0)}}
    blow = {"torso": {"rot": (24, 0, 0)}, "head": {"rot": (-16, 0, 0)}, "arm_l": {"rot": (-10, 0, 0)}, "body": {"pos": (0, -0.6, 0)}}
    frames = [(0.0, {}), (0.4, pump), (0.8, blow)]
    for k in range(4):
        frames.append((1.0 + 0.2 * k, pump if k % 2 == 0 else blow))
    frames += [(2.0, {})]
    g.append(keys("breath", 2.0, frames))
    jab0 = {"arm_r": {"rot": (-20, 0, 0)}, "torso": {"rot": (-6, 0, 0)}}
    jab = {"arm_r": {"rot": (30, 0, 0)}, "torso": {"rot": (12, 0, 0)}, "body": {"pos": (0, 0, -1.2)}}
    g.append(keys("poke", 1.15, [(0.0, {}), (0.6, jab0), (0.8, jab), (0.95, jab), (1.15, {})]))
    m.gaits = g
    m.poses = [("breath", 0.8), ("poke", 0.6), ("walk", 0.2), ("death", 1.4)]
    return m


def lava_slug():
    """熔岩の蛞蝓 (382 A): 黒い皮の大きな蛞蝓。背の割れ目が光り、通った跡にしばらく火が残る"""
    m = Mount("lava_slug", "熔岩の蛞蝓")
    m.bone("body", "root", (0, 4, 0))
    m.bone("head", "body", (0, 5, -9))
    m.bone("tail", "body", (0, 4, 6))
    m.add("front", lof("body", "skin", [rsec(4, 7, 0, 5.4, n=12, mid=2.4), rsec(-3, 9, 0, 6.0, n=12, mid=3.0), rsec(-9, 8, 0, 5.0, n=12, mid=3.0)]), core=True)
    m.add("tailc", lof("tail", "skin", [rsec(3, 7.6, 0, 5.6, n=12, mid=2.6), rsec(10, 3, 0, 3.0, n=12, mid=1.2), rsec(13, 1.6, 0, 1.0, n=12, mid=0.6)]))
    m.add("headc", lof("head", "skin", [rsec(-8, 8.4, 0.4, 5.2, n=12, mid=3.0), rsec(-12.4, 6, 0.6, 3.0, n=12, mid=3.0)]))
    for k in range(4):
        m.add(f"fissure{k}", ell("body" if k < 3 else "tail", "magma", (0, 8.8 - abs(k - 1.5) * 0.4, -6 + 4 * k), (1.4, 0.6, 1.6), segs=7, rings=1))
    for s in (1, -1):
        m.add(f"stalk{s}", tub("head", "skin", [P(s * 1.6, 6, -11), P(s * 2.6, 11, -14)], [0.7, 0.5], None, sides=5))
        eye(m, "head", (s * 2.6, 11.4, -14.2), 0.6, (s * 0.3, 0.3, -1), (255, 180, 70), glow=True)
    m.pal = pal_({"skin": M_(SLUG, lit=0.34), "magma": M_(MAG, base=0.68, lit=0.2)}, glow=["magma"])
    m.detail = lambda c, col, glow: (speckle(c, col, ["skin"], (120, 50, 20), density=0.05, size=0.6), glow)
    m.view = dict(center=(0, 4, -2), scale=5.0)
    g = []
    idle = Gait("idle", 2.6)
    idle.rot("head", W(0).s(3, 1, 0, "t"), W(0).s(5, 1, 60, "t"))
    idle.pos("body", None, W(0).s(0.2, 1, 0, "t"))
    g.append(idle)
    walk = Gait("walk", 1.4)
    walk.rot("head", W(0).s(4, 1, 0, "t"))
    walk.rot("tail", W(0).s(-4, 1, 90, "t"), W(0).s(6, 1, 0, "t"))
    walk.pos("body", None, W(0).s(0.4, 1, 0, "t"))
    g.append(walk)
    rear = {"head": {"rot": (-25, 0, 0)}, "body": {"rot": (-6, 0, 0)}}
    bite = {"head": {"rot": (20, 0, 0)}, "body": {"pos": (0, 0, -1.6)}}
    g.append(keys("bite", 1.15, [(0.0, {}), (0.6, rear), (0.8, bite), (0.95, bite), (1.15, {})]))
    g.append(keys("hurt", 0.3, [(0.0, {}), (0.12, {"head": {"rot": (-10, 0, 0)}}), (0.3, {})]))
    melt = {"body": {"pos": (0, -3, 0)}, "head": {"rot": (30, 0, 0)}, "tail": {"rot": (20, 0, 0)}}
    g.append(keys("death", 1.2, [(0.0, {}), (1.0, melt), (1.2, melt)], hold=True))
    m.gaits = g
    m.poses = [("bite", 0.45), ("bite", 0.6), ("walk", 0.4), ("death", 1.2)]
    return m


def ox_smith():
    """牛頭の鍛冶番 (383 B): 牛の頭の大男。左手に焼けた鉄の板の盾、右手の大きな火ばさみで掴んで投げる"""
    m = Mount("ox_smith", "牛頭の鍛冶番")
    biped_bones(m, (0, 20, 0), (0, 21, 0), (0, 43, -2), (12, 38, 0), 6, 19)
    for s, b in ((1, "leg_l"), (-1, "leg_r")):
        leg(m, b, "fur", (s * 6, 19.5, 0), (s * 6.6, 10, -1.2), (s * 6.4, 3.4, 0), 6.4, foot_mat="hoof", foot=(4.0, 3.0, 5.0))
    m.add("kilt", lof("body", "leath", [ysec(23, 21, 14), ysec(14, 24, 15)]), core=True)
    m.add("belly", ell("torso", "skin", (0, 30, 0), (11, 11, 7.4), segs=14, rings=4))
    m.add("chest", ell("torso", "skin", (0, 35, -1), (12, 6.6, 7.2), segs=14, rings=3))
    m.add("neck", ell("head", "fur", (0, 41.6, -1.6), (5, 3.4, 4.6), segs=10, rings=2))
    m.add("headc", lof("head", "fur", [rsec(2, 50, 41, 6, n=12), rsec(-4, 51, 40, 6.6, n=12), rsec(-9, 47, 41, 4.6, n=12), rsec(-12, 45, 42, 3.4, n=12)]))
    m.add("ring", tub("head", "brass", [P(-1.6, 43, -12.2), P(1.6, 43, -12.2)], [0.5, 0.5], None, sides=5))
    for s in (1, -1):
        eye(m, "head", (s * 3.6, 48.4, -6.4), 0.8, (s * 0.6, 0, -1), (255, 120, 40), glow=True)
        m.add(f"horn{s}", cone("head", "bone", P(s * 4.6, 50, -2), (s, 0.5, -0.2), 9, 1.8, sides=6, rings=3, bend=(0, 4.0, -1.0)))
    for sd, b in ((1, "arm_l"), (-1, "arm_r")):
        m.add(f"shoulder{sd}", ell(b, "skin", (sd * 10.6, 37.6, -0.6), (5.0, 5.0, 5.0), segs=10, rings=3))
    arm(m, "arm_l", "skin", (11, 38, 0), (17, 29, -3), (15, 22, -8), 4.4, fist=3.2)
    m.add("shield", box("arm_l", "magma", (18.4, 24, -10), (2, 16, 12), taper=(1.0, 1.0), cut=0.15))
    arm(m, "arm_r", "skin", (-11, 38, 0), (-17, 30, -4), (-15, 26, -12), 4.4, fist=3.2)
    m.add("tong1", tub("arm_r", "iron", [P(-15, 26, -11), P(-14, 28, -26), P(-13, 26, -32)], [0.9, 0.8, 0.6], None, sides=5))
    m.add("tong2", tub("arm_r", "iron", [P(-15, 26, -11), P(-16, 24, -26), P(-17, 26, -32)], [0.9, 0.8, 0.6], None, sides=5))
    m.pal = pal_({"skin": M_(SKIN_RED), "fur": M_(FUR), "leath": M_(LEATHR), "hoof": M_(BAS), "bone": M_(BONE, lit=0.34), "brass": M_(BRASSY), "iron": M_(IRON),
                  "magma": M_(MAG, base=0.6, lit=0.24)}, glow=["magma"])
    m.detail = lambda c, col, glow: (fur(c, col, ["fur"], amt=0.08), glow)
    m.view = dict(center=(0, 28, -2), scale=2.8)
    g = biped_gaits(walk_amp=16, period=1.6, sink=18)
    reach = {"arm_r": {"rot": (-40, 0, 0)}, "torso": {"rot": (-8, 0, 0)}}
    grab = {"arm_r": {"rot": (-75, 0, 0)}, "torso": {"rot": (14, 0, 0)}, "body": {"pos": (0, 0, -2)}}
    toss = {"arm_r": {"rot": (-150, 0, 0)}, "torso": {"rot": (-14, 0, 0)}}
    g.append(keys("grab", 1.8, [(0.0, {}), (0.6, reach), (0.8, grab), (1.1, grab), (1.4, toss), (1.8, {})]))
    brace = {"arm_l": {"rot": (-30, 0, -10)}, "torso": {"rot": (0, 20, 0)}}
    bash = {"arm_l": {"rot": (-80, 0, -20)}, "torso": {"rot": (10, -10, 0)}, "body": {"pos": (0, 0, -2.4)}}
    g.append(keys("bash", 1.2, [(0.0, {}), (0.6, brace), (0.8, bash), (0.95, bash), (1.2, {})]))
    lift = {"arm_l": {"rot": (-170, 0, 0)}, "arm_r": {"rot": (-160, 0, 0)}, "torso": {"rot": (-10, 0, 0)}}
    smash = {"arm_l": {"rot": (-40, 0, 0)}, "arm_r": {"rot": (-40, 0, 0)}, "torso": {"rot": (30, 0, 0)}, "body": {"pos": (0, -3, 0)}}
    g.append(keys("slam", 1.8, [(0.0, {}), (0.8, lift), (1.05, smash), (1.35, smash), (1.8, {})]))
    # 作り込み: 叩きつけは両腕を高く、体ごと落とす
    lift2 = {"arm_l": {"rot": (-175, 0, 10)}, "arm_r": {"rot": (-170, 0, -10)}, "torso": {"rot": (-18, 0, 0)}, "head": {"rot": (-16, 0, 0)}, "body": {"pos": (0, 1.0, 0)}}
    smash2 = {"arm_l": {"rot": (-30, 0, 0)}, "arm_r": {"rot": (-30, 0, 0)}, "torso": {"rot": (36, 0, 0)}, "body": {"pos": (0, -3.6, -1.6)}, "head": {"rot": (10, 0, 0)},
              "leg_l": {"rot": (-30, 0, 0)}, "leg_r": {"rot": (24, 0, 0)}}
    replace_keys(g, "slam", 1.8, [(0.0, {}), (0.7, lift2), (0.95, smash2), (1.1, smash2), (1.25, {"body": {"pos": (0, -2.4, -1.0)}, "torso": {"rot": (26, 0, 0)}}), (1.8, {})])
    # 鉄塊投げ (新): 火ばさみで焼けた鉄を掴み、振りかぶって投げる
    scoop = {"arm_r": {"rot": (-20, 0, 0)}, "torso": {"rot": (30, 0, 0)}, "body": {"pos": (0, -2.0, 0)}}
    back = {"arm_r": {"rot": (-170, 0, -20)}, "torso": {"rot": (-14, 30, 0)}, "head": {"rot": (-6, 10, 0)}}
    fling = {"arm_r": {"rot": (-40, 0, 10)}, "torso": {"rot": (16, -30, 0)}, "leg_l": {"rot": (-24, 0, 0)}}
    g.append(keys("hurl", 1.7, [(0.0, {}), (0.4, scoop), (0.8, back), (1.0, fling), (1.2, fling), (1.7, {})]))
    # 火の息 (新): 鼻から火を吹く。首を振る
    inhale = {"head": {"rot": (-20, 0, 0)}, "torso": {"rot": (-10, 0, 0)}}
    blow_l = {"head": {"rot": (14, 26, 0)}, "torso": {"rot": (10, 10, 0)}}
    blow_r = {"head": {"rot": (14, -26, 0)}, "torso": {"rot": (10, -10, 0)}}
    g.append(keys("breath", 2.0, [(0.0, {}), (0.5, inhale), (0.8, blow_l), (1.2, blow_r), (1.6, blow_l), (2.0, {})]))
    breath_idle(g)
    m.poses = [("slam", 0.95), ("hurl", 0.8), ("hurl", 1.05), ("breath", 1.0)]
    m.gaits = g
    m.poses = [("grab", 0.8), ("grab", 1.4), ("bash", 0.6), ("slam", 1.1)]
    return m


ALL = {f.__name__: f for f in (pit_digger, bone_roller, pit_foreman, drowned_sailor, cave_jelly, bell_hermit,
                                horn_beetle, crystal_eye, geode_golem, bellows_imp, lava_slug, ox_smith)}
BOUNDS = {"pit_foreman": (6, 5), "bell_hermit": (6, 5), "geode_golem": (6, 5), "ox_smith": (6, 5)}
