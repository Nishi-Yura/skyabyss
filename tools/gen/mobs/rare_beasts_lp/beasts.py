"""逃げる獣 5 体 (2026-11-03 の選定 = みな推奨。ラフは tools/rough/lowpoly/rare_beasts/roughs.py) の形と動き。
長さは 1/16 ブロック、前は -z。部品は乗り物の低ポリの部品 (mounts_lp/mlp.py)、書き出しは地下の敵と同じ (dungeon_bosses_lp/blp.py)。

  bell_hare     鈴尾の野兎 (都のまわり): 跳ねて逃げ、尾の先の鈴が鳴る
  moss_roller   苔石の丸獣 (鯨見): 丸まって転がって逃げる (roll)
  pearl_otter   真珠背の海獺 (汐守): 水へ逃げて泳ぐ (swim)
  bolt_weasel   雷尾の鼬 (風待): 稲妻のように折れて走る
  ember_mole    熔背の土竜 (灰炉): 潜って (dig) 別の所から出る (emerge)
骨はどれも body / head / ear_l・ear_r (あれば) / tail・tail2 / leg_{fl,fr,bl,br} (+ _lo)。
動きの名前は Java (world/continent/RareBeast) と同じ: idle・walk・run・hurt・death と、逃げ方の動き。
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


WHEAT = [(96, 66, 34), (132, 96, 54), (168, 128, 78), (204, 164, 106), (232, 200, 144)]
CREAM = [(150, 140, 120), (186, 178, 158), (218, 210, 192), (240, 236, 222)]
BRASS = [(78, 56, 22), (126, 94, 38), (178, 138, 62), (222, 184, 98), (248, 222, 150)]
DARK = [(20, 18, 18), (34, 30, 30), (50, 44, 42)]
STONE = [(40, 40, 46), (56, 56, 64), (76, 76, 86), (98, 98, 110), (122, 122, 134)]
MOSS = [(34, 56, 28), (50, 80, 38), (72, 108, 50), (100, 138, 66)]
SKIN = [(110, 92, 70), (146, 126, 98), (180, 160, 128), (210, 192, 160)]
OTTER = [(40, 28, 22), (60, 44, 34), (84, 62, 46), (110, 84, 62), (136, 108, 82)]
SHELL = [(110, 80, 60), (150, 112, 84), (190, 150, 112), (220, 186, 146)]
PEARL = [(170, 176, 196), (204, 208, 222), (230, 232, 242), (250, 250, 255)]
SLATE = [(40, 44, 60), (58, 64, 86), (80, 88, 114), (106, 116, 146), (136, 146, 176)]
PALE = [(140, 146, 164), (176, 182, 198), (208, 212, 224), (234, 236, 244)]
SPARK = [(180, 190, 80), (230, 236, 120), (250, 252, 180), (255, 255, 236)]
CHAR = [(16, 14, 14), (28, 24, 22), (42, 36, 32), (58, 50, 44), (76, 66, 58)]
BASALT = [(24, 22, 24), (38, 34, 36), (54, 48, 50), (72, 64, 64), (94, 84, 82)]
MAG = [(160, 50, 16), (220, 96, 30), (252, 156, 56), (255, 214, 120), (255, 244, 200)]
PINK = [(120, 70, 66), (156, 100, 92), (190, 130, 118), (220, 164, 150)]


# ---------------------------------------------------------------------- 共通の部品
def leg4(m, mat, spec, thick, paw, paw_mat=None, lo_thin=0.72):
    """四つの脚。spec = {k: (腰, 膝, 足首)}。上は leg_k、下と足は leg_k_lo (継ぎ目は互いに埋める)"""
    quad_bones(m, {k: (h, kn) for k, (h, kn, a) in spec.items()})
    for k, (hip, knee, ank) in spec.items():
        hip, knee, ank = P(*hip), P(*knee), P(*ank)
        over = knee + (knee - hip) * 0.18
        m.add(f"up_{k}", limb("leg_" + k, mat, [hip + P(0, 0.8, 0), knee, over], [thick, thick * 0.86, thick * 0.8], [thick, thick * 0.86, thick * 0.8], n=8))
        m.add(f"lo_{k}", limb("leg_" + k + "_lo", mat, [knee, ank], [thick * 0.84, thick * lo_thin], [thick * 0.84, thick * lo_thin], n=8))
        m.add(f"paw_{k}", ell("leg_" + k + "_lo", paw_mat or mat, ank + P(0, -0.25, -paw[2] * 0.3), paw, segs=8, rings=2))


def side_fall(m, extra=None, length=1.2, drop=-1.0):
    """倒れる: 横倒しに転がる (death)。抜けた骨は 0"""
    pose = {"body": {"rot": (0, 0, 84), "pos": (0, drop, 0)}, "head": {"rot": (14, 0, 0)}}
    for k in ("fl", "fr", "bl", "br"):
        pose["leg_" + k] = {"rot": (-14 if k[0] == "f" else 14, 0, 0)}
    if extra:
        pose.update(extra)
    return keys("death", length, [(0.0, {}), (0.5, pose), (length, pose)], hold=True)


def flinch(bone="body", amt=(-8, 0, 6)):
    return keys("hurt", 0.3, [(0.0, {}), (0.12, {bone: {"rot": amt}, "head": {"rot": (-10, 0, 0)}}), (0.3, {})])


# ====================================================================== 鈴尾の野兎 (都のまわり)
def bell_hare():
    m = Mount("bell_hare", "鈴尾の野兎")
    m.bone("body", "root", (0, 6.2, 0))
    m.bone("head", "body", (0, 8.6, -4.0))
    m.bone("ear_l", "head", (1.0, 10.8, -4.2))
    m.bone("ear_r", "head", (-1.0, 10.8, -4.2))
    m.bone("tail", "body", (0, 6.8, 5.2))
    m.bone("tail2", "tail", (0, 8.8, 8.6))
    body = [rsec(-3.6, 8.4, 4.2, 2.6, 6.2, n=12), rsec(-1.0, 9.0, 3.6, 3.2, 6.2, n=12), rsec(2.0, 9.2, 3.4, 3.6, 6.2, n=12),
            rsec(4.4, 8.6, 3.6, 3.4, 6.0, n=12), rsec(6.0, 7.2, 4.4, 2.2, 5.8, n=12)]
    m.add("torso", lof("body", "fur", body), core=True)
    m.add("belly", ell("body", "cream", (0, 4.6, 0.6), (2.4, 1.4, 3.6), segs=8, rings=2))
    m.add("neck", ell("body", "fur", (0, 7.8, -3.6), (2.2, 2.4, 2.0), segs=8, rings=2))
    m.add("skull", ell("head", "fur", (0, 9.4, -4.8), (2.2, 2.2, 2.6), segs=10, rings=3), core=True)
    m.add("cheek", ell("head", "cream", (0, 8.4, -6.6), (1.6, 1.2, 1.2), segs=8, rings=2))
    m.add("nose", ell("head", "pink", (0, 8.9, -7.6), (0.45, 0.35, 0.3), segs=6, rings=1))
    for s, k in ((1, "l"), (-1, "r")):
        e = "ear_" + k
        m.add(f"ear{k}", cone(e, "fur", P(s * 1.0, 10.4, -4.2), (s * 0.18, 1, 0.3), 7.4, 1.05, sides=6, rings=3, bend=(0, 0, 1.6)))
        m.add(f"earin{k}", cone(e, "pink", P(s * 1.0, 10.9, -4.55), (s * 0.18, 1, 0.3), 6.0, 0.6, sides=5, rings=2, bend=(0, 0, 1.4)))
        eye(m, "head", (s * 1.55, 9.9, -6.0), 0.55, (s * 0.7, 0.1, -0.6), (70, 46, 30))
    leg4(m, "fur", {"fl": ((1.6, 5.4, -2.6), (1.7, 3.0, -3.0), (1.7, 0.9, -3.2)), "fr": ((-1.6, 5.4, -2.6), (-1.7, 3.0, -3.0), (-1.7, 0.9, -3.2)),
                    "bl": ((2.0, 6.0, 3.4), (2.2, 3.0, 5.0), (2.2, 0.9, 3.8)), "br": ((-2.0, 6.0, 3.4), (-2.2, 3.0, 5.0), (-2.2, 0.9, 3.8))},
         1.25, (0.9, 0.55, 2.2), paw_mat="cream")
    m.add("haunchl", ell("body", "fur", (2.0, 5.6, 3.4), (1.6, 2.2, 2.4), segs=8, rings=2))
    m.add("haunchr", ell("body", "fur", (-2.0, 5.6, 3.4), (1.6, 2.2, 2.4), segs=8, rings=2))
    m.add("tail_a", tub("tail", "fur", [P(0, 6.8, 4.6), P(0, 8.0, 6.8), P(0, 9.0, 9.0)], [0.75, 0.6, 0.5], None, sides=6))
    m.add("tail_b", tub("tail2", "fur", [P(0, 8.8, 8.4), P(0, 8.9, 10.4), P(0, 8.4, 11.8)], [0.5, 0.42, 0.38], None, sides=6))
    m.add("strap", tub("tail2", "leath", [P(0, 8.4, 11.6), P(0, 7.9, 12.3)], [0.42, 0.42], None, sides=6))
    m.add("bell", lof("tail2", "brass", [ysec(7.8, 0.7, 0.7, zc=12.4), ysec(7.2, 1.2, 1.2, zc=12.4), ysec(6.2, 1.4, 1.4, zc=12.4),
                                          ysec(5.7, 1.5, 1.5, zc=12.4)]))
    m.add("lip", tub("tail2", "brass", [P(0, 5.8, 12.4), P(0, 5.5, 12.4)], [1.55, 1.55], None, sides=10))
    m.add("clapper", ell("tail2", "dark", (0, 5.3, 12.4), (0.42, 0.42, 0.42), segs=6, rings=2))
    m.pal = pal_({"fur": M_(WHEAT, lit=0.32), "cream": M_(CREAM), "pink": M_(PINK), "brass": M_(BRASS, base=0.62, lit=0.34), "leath": M_(OTTER),
                  "dark": M_(DARK)})
    m.detail = lambda c, col, glow: (speckle(c, col, ["fur"], (150, 112, 64), density=0.05, size=0.6), glow)
    m.view = dict(center=(0, 6, 1), scale=6.4)
    idle, wk, rn = quad_gaits(walk=(0.5, 18, 26, 22, 30), run=(1.8, 46, 60, 52, 56), run_ph=BOUND_PH, neck=None, tails=("tail", "tail2"),
                              ears=("ear_l", "ear_r"), bob=(0.3, 1.4), pitch=(1.0, 6.0), idle_period=4.0, run_base=(-6.0, 8.0))
    m.gaits = [idle, wk, rn, flinch(), side_fall(m)]
    m.poses = [("run", 0.25), ("run", 0.6), ("walk", 0.3), ("death", 1.2)]
    return m


# ====================================================================== 苔石の丸獣 (鯨見)
def moss_roller():
    m = Mount("moss_roller", "苔石の丸獣")
    m.bone("body", "root", (0, 5.8, 0))
    # 殻は前と後ろの二つの骨 (転がるときに丸く曲げて玉にする)。頭は前、尾は後ろに付く
    m.bone("shell_f", "body", (0, 5.8, 0))
    m.bone("shell_b", "body", (0, 5.8, 0))
    m.bone("head", "shell_f", (0, 5.2, -5.4))
    m.bone("tail", "shell_b", (0, 4.8, 4.8))
    for i, z in enumerate((-3.4, -1.15, 1.15, 3.4)):
        r = 4.7 - abs(z) * 0.2
        sb = "shell_f" if i < 2 else "shell_b"
        m.add(f"band{i}", lof(sb, "stone", [rsec(z - 1.25, 5.8 + r * 0.95, 5.8 - r * 0.55, r * 0.98, 5.8, n=14),
                                                rsec(z + 1.25, 5.8 + r * 0.92, 5.8 - r * 0.52, r * 0.95, 5.8, n=14)]), core=(i == 1))
        m.add(f"rim{i}", tub(sb, "stone2", [P(0, 5.8 + r * 0.96, z + 1.2), P(0, 5.8 + r * 0.96, z + 1.4)], [r * 0.3, r * 0.3], None, sides=6))
        for j, a in enumerate((-50, -20, 15, 45)):
            ar = math.radians(a)
            m.add(f"moss{i}{j}", ell(sb, "moss", (math.sin(ar) * r * 0.85, 5.8 + math.cos(ar) * r * 0.86, z + (j % 2) * 0.4),
                                     (1.3, 0.55, 1.1), segs=6, rings=2, rz=-a))
    m.add("belly", ell("body", "skin", (0, 3.4, 0), (3.6, 2.0, 5.0), segs=10, rings=2))
    m.add("snout", lof("head", "skin", [rsec(-5.0, 6.4, 3.4, 1.7, 5.0, n=10), rsec(-7.2, 5.8, 3.8, 1.3, 4.8, n=10), rsec(-8.8, 5.0, 4.0, 0.7, 4.5, n=10)]))
    m.add("helm", lof("head", "stone", [rsec(-4.8, 7.4, 5.4, 2.2, 6.4, n=10), rsec(-7.0, 6.8, 5.4, 1.6, 6.0, n=10)]), core=True)
    m.add("nose", ell("head", "dark", (0, 4.7, -9.0), (0.5, 0.4, 0.3), segs=6, rings=1))
    for s in (1, -1):
        m.add(f"ear{s}", ell("head", "skin", (s * 1.6, 6.9, -4.8), (0.55, 0.9, 0.45), segs=6, rings=2))
        eye(m, "head", (s * 1.3, 5.9, -7.0), 0.42, (s * 0.8, 0.1, -0.5), (40, 30, 20))
    leg4(m, "skin", {"fl": ((2.4, 3.6, -2.8), (2.6, 2.0, -3.0), (2.6, 0.8, -3.2)), "fr": ((-2.4, 3.6, -2.8), (-2.6, 2.0, -3.0), (-2.6, 0.8, -3.2)),
                     "bl": ((2.4, 3.6, 2.8), (2.6, 2.0, 3.2), (2.6, 0.8, 2.8)), "br": ((-2.4, 3.6, 2.8), (-2.6, 2.0, 3.2), (-2.6, 0.8, 2.8))},
         1.2, (1.0, 0.5, 1.3), paw_mat="dark")
    m.add("tailc", cone("tail", "stone", P(0, 4.8, 4.6), (0, -0.25, 1), 3.4, 1.05, sides=6, rings=3))
    m.pal = pal_({"stone": M_(STONE, lit=0.3), "stone2": M_(STONE, base=0.45), "moss": M_(MOSS), "skin": M_(SKIN), "dark": M_(DARK)})
    m.detail = lambda c, col, glow: (plates(c, col, ["stone"], size=1.8, edge=0.08, k_edge=0.6), glow)
    m.view = dict(center=(0, 5, 0), scale=6.4)
    idle, wk, rn = quad_gaits(walk=(0.4, 20, 26, 20, 26), run=(0.9, 34, 44, 32, 40), run_ph=TROT_PH, neck=None, tails=("tail",),
                              bob=(0.2, 0.6), pitch=(0.6, 2.0), idle_period=5.0, run_base=(0.0, 0.0))
    # 転がる: 頭と脚をしまって殻の玉になり、前へ回り続ける (Java は転がるあいだ速く進む)
    tuck = {"shell_f": {"rot": (48, 0, 0), "pos": (0, -0.6, 1.0)}, "shell_b": {"rot": (-48, 0, 0), "pos": (0, -0.6, -1.0)},
            "head": {"rot": (60, 0, 0), "pos": (0, -0.4, 1.8)}, "tail": {"rot": (-70, 0, 0)}}
    for k in ("fl", "fr", "bl", "br"):
        tuck["leg_" + k] = {"rot": (0, 0, 0), "pos": (0, 2.4, 0)}
        tuck["leg_" + k + "_lo"] = {"rot": (0, 0, 0), "pos": (0, 1.2, 0)}
    roll = Gait("roll", period=0.6, loop=True)
    roll.keys = {b: {k: {0.0: v, 0.6: v} for k, v in ch.items()} for b, ch in tuck.items()}
    roll.keys["body"] = {"rot": {0.0: (0, 0, 0), 0.3: (-180, 0, 0), 0.6: (-360, 0, 0)}, "pos": {0.0: (0, -0.8, 0), 0.6: (0, -0.8, 0)}}
    roll.length = 0.6
    m.gaits = [idle, wk, rn, roll, flinch(), side_fall(m, drop=-0.6)]
    m.poses = [("roll", 0.15), ("roll", 0.45), ("walk", 0.3), ("death", 1.2)]
    return m


# ====================================================================== 真珠背の海獺 (汐守)
def pearl_otter():
    m = Mount("pearl_otter", "真珠背の海獺")
    m.bone("body", "root", (0, 4.8, 0))
    m.bone("head", "body", (0, 6.2, -5.8))
    m.bone("tail", "body", (0, 4.4, 5.8))
    m.bone("tail2", "tail", (0, 3.6, 8.4))
    body = [rsec(-5.6, 6.8, 3.4, 2.2, 5.2, n=12), rsec(-3.0, 7.4, 2.6, 2.8, 5.0, n=12), rsec(0.6, 7.6, 2.4, 3.0, 5.0, n=12),
            rsec(3.8, 7.2, 2.6, 2.8, 4.9, n=12), rsec(6.4, 5.8, 3.2, 1.8, 4.6, n=12)]
    m.add("torso", lof("body", "fur", body), core=True)
    m.add("belly", ell("body", "cream", (0, 3.2, -1.0), (2.0, 1.0, 4.4), segs=8, rings=2))
    m.add("skull", ell("head", "fur", (0, 6.8, -7.0), (2.2, 2.0, 2.2), segs=10, rings=3), core=True)
    m.add("face", ell("head", "cream", (0, 6.0, -8.6), (1.5, 1.2, 0.9), segs=8, rings=2))
    m.add("nose", ell("head", "dark", (0, 6.5, -9.4), (0.5, 0.35, 0.3), segs=6, rings=1))
    for s in (1, -1):
        m.add(f"ear{s}", ell("head", "fur", (s * 1.8, 7.8, -6.4), (0.5, 0.5, 0.35), segs=5, rings=2))
        eye(m, "head", (s * 1.2, 7.4, -8.6), 0.42, (s * 0.5, 0.15, -0.8), (16, 16, 20))
        for w in range(2):
            m.add(f"whisk{s}{w}", tub("head", "cream", [P(s * 1.0, 5.9 - w * 0.3, -9.0), P(s * 2.8, 6.0 - w * 0.5, -9.2)], [0.08, 0.06], None, sides=3))
    leg4(m, "fur", {"fl": ((1.9, 3.4, -3.6), (2.1, 2.0, -3.8), (2.1, 0.7, -4.0)), "fr": ((-1.9, 3.4, -3.6), (-2.1, 2.0, -3.8), (-2.1, 0.7, -4.0)),
                    "bl": ((1.9, 3.4, 3.6), (2.1, 2.0, 4.0), (2.1, 0.7, 3.8)), "br": ((-1.9, 3.4, 3.6), (-2.1, 2.0, 4.0), (-2.1, 0.7, 3.8))},
         1.0, (1.0, 0.4, 1.5), paw_mat="dark")
    m.add("tail_a", tub("tail", "fur", [P(0, 4.6, 5.4), P(0, 4.0, 7.2), P(0, 3.6, 8.8)], [1.5, 1.1, 0.9], None, sides=7))
    m.add("tail_b", tub("tail2", "fur", [P(0, 3.6, 8.2), P(0, 3.2, 10.0), P(0, 3.0, 11.4)], [0.9, 0.7, 0.35], None, sides=7))
    # 背の真珠貝: 下の殻は背に埋め、上の殻を少し開いて真珠を見せる
    m.add("shell_lo", lof("body", "shell", [rsec(-2.2, 8.0, 6.6, 3.0, 7.4, n=12), rsec(1.0, 8.4, 6.8, 3.4, 7.6, n=12), rsec(3.8, 8.0, 6.8, 2.8, 7.4, n=12)]))
    m.add("shell_hi", lof("body", "shell", [rsec(-1.8, 10.4, 8.4, 2.8, 9.0, n=12), rsec(1.2, 10.6, 8.6, 3.2, 9.2, n=12), rsec(3.8, 9.4, 8.2, 2.6, 8.6, n=12)]))
    for i in range(5):
        a = -50 + 25 * i
        m.add(f"rib{i}", tub("body", "shell2", [P(math.sin(math.radians(a)) * 1.0, 10.5, -1.4), P(math.sin(math.radians(a)) * 2.8, 9.8, 3.6)],
                              [0.18, 0.14], None, sides=4))
    m.add("pearl", ell("body", "pearl", (0, 8.6, -2.1), (1.15, 1.15, 1.15), segs=10, rings=4))
    m.pal = pal_({"fur": M_(OTTER, lit=0.32), "cream": M_(CREAM), "shell": M_(SHELL, lit=0.32), "shell2": M_(SHELL, base=0.4), "pearl": M_(PEARL, base=0.64, lit=0.3),
                  "dark": M_(DARK)}, glow=["pearl"])
    m.detail = lambda c, col, glow: (col, glow)
    m.view = dict(center=(0, 6, 0), scale=6.4)
    idle, wk, rn = quad_gaits(walk=(0.4, 16, 22, 18, 24), run=(1.2, 34, 44, 38, 46), run_ph=BOUND_PH, neck=None, tails=("tail", "tail2"),
                              bob=(0.3, 1.2), pitch=(1.0, 6.0), idle_period=5.0, run_base=(-2.0, 4.0))
    # 泳ぐ: 前脚をたたみ、後ろ脚で蹴り、体と尾を上下にうねらせる
    sw = Gait("swim", period=1.0)
    sw.rot("body", x=W(-4).s(6, 1, 0, "t"))
    sw.rot("head", x=W(8).s(-5, 1, 0, "t"))
    sw.rot("tail", x=W(4).s(14, 1, 60, "t"))
    sw.rot("tail2", x=W(4).s(18, 1, 120, "t"))
    for k in ("fl", "fr"):
        sw.rot("leg_" + k, x=W(60))
        sw.rot("leg_" + k + "_lo", x=W(-40))
    for k, ph in (("bl", 0), ("br", 180)):
        sw.rot("leg_" + k, x=W(50).s(30, 1, ph, "t"))
        sw.rot("leg_" + k + "_lo", x=W(10).s(20, 1, ph + 90, "t"))
    m.gaits = [idle, wk, rn, sw, flinch(), side_fall(m, drop=-0.4)]
    m.poses = [("run", 0.3), ("swim", 0.3), ("walk", 0.3), ("death", 1.2)]
    return m


# ====================================================================== 雷尾の鼬 (風待)
ZIG = [(0, 5.0, 6.0), (0, 8.0, 7.8), (0, 6.4, 9.4), (0, 10.0, 11.4), (0, 8.6, 12.8), (0, 12.4, 14.8)]


def bolt_weasel():
    m = Mount("bolt_weasel", "雷尾の鼬")
    m.bone("body", "root", (0, 4.2, 0))
    m.bone("head", "body", (0, 5.6, -6.4))
    m.bone("tail", "body", ZIG[0])
    m.bone("tail2", "tail", ZIG[3])
    body = [rsec(-6.4, 6.2, 3.0, 1.6, 4.6, n=12), rsec(-3.6, 6.4, 2.6, 2.0, 4.5, n=12), rsec(0.0, 6.6, 2.4, 2.2, 4.5, n=12),
            rsec(3.6, 6.4, 2.6, 2.0, 4.5, n=12), rsec(6.6, 5.6, 3.2, 1.4, 4.4, n=12)]
    m.add("torso", lof("body", "fur", body), core=True)
    m.add("belly", ell("body", "pale", (0, 3.0, -0.6), (1.5, 0.8, 5.0), segs=8, rings=2))
    hd = [rsec(-5.8, 7.4, 4.4, 1.6, 5.8, n=10), rsec(-7.4, 7.6, 4.6, 1.7, 6.0, n=10), rsec(-9.0, 6.8, 5.0, 1.1, 5.8, n=10), rsec(-10.0, 6.1, 5.3, 0.5, 5.7, n=10)]
    m.add("skull", lof("head", "fur", hd), core=True)
    m.add("jaw", ell("head", "pale", (0, 5.0, -8.0), (1.1, 0.6, 1.6), segs=7, rings=2))
    m.add("nose", ell("head", "dark", (0, 6.0, -10.0), (0.35, 0.3, 0.25), segs=6, rings=1))
    for s in (1, -1):
        m.add(f"ear{s}", ell("head", "fur", (s * 1.3, 7.8, -6.4), (0.7, 0.75, 0.35), segs=6, rings=2))
        eye(m, "head", (s * 1.1, 7.0, -8.4), 0.42, (s * 0.55, 0.15, -0.7), (130, 220, 255), glow=True)
    leg4(m, "fur", {"fl": ((1.4, 3.0, -4.6), (1.5, 1.9, -5.0), (1.5, 0.6, -5.0)), "fr": ((-1.4, 3.0, -4.6), (-1.5, 1.9, -5.0), (-1.5, 0.6, -5.0)),
                    "bl": ((1.4, 3.0, 4.4), (1.5, 1.9, 4.8), (1.5, 0.6, 4.4)), "br": ((-1.4, 3.0, 4.4), (-1.5, 1.9, 4.8), (-1.5, 0.6, 4.4))},
         0.85, (0.7, 0.35, 1.0), paw_mat="dark")
    zig = [P(*p) for p in ZIG]
    m.add("tail_a", tub("tail", "spark", zig[:4], [1.05, 0.95, 0.85, 0.75], None, sides=5))
    m.add("tail_b", tub("tail2", "spark", zig[3:], [0.75, 0.6, 0.3], None, sides=5))
    m.add("tail_root", ell("tail", "fur", (0, 5.2, 6.0), (1.4, 1.4, 1.3), segs=7, rings=2))
    m.pal = pal_({"fur": M_(SLATE, lit=0.32), "pale": M_(PALE), "spark": M_(SPARK, base=0.72, lit=0.2), "dark": M_(DARK)}, glow=["spark"])
    m.detail = lambda c, col, glow: (col, glow)
    m.view = dict(center=(0, 5, 2), scale=6.0)
    idle, wk, rn = quad_gaits(walk=(0.4, 18, 24, 18, 24), run=(1.6, 40, 52, 40, 50), run_ph=BOUND_PH, neck=None, tails=("tail", "tail2"),
                              bob=(0.2, 1.0), pitch=(1.0, 7.0), idle_period=3.0, run_base=(-4.0, 6.0))
    # 走りで胴を左右にくねらせる (稲妻のように折れて走る)
    rn.rot("body", x=W(0).s(7.0, 1, -20), y=W(0).s(6.0, 1, 90))
    m.gaits = [idle, wk, rn, flinch(), side_fall(m, drop=-0.3)]
    m.poses = [("run", 0.2), ("run", 0.55), ("idle", 1.0), ("death", 1.2)]
    return m


# ====================================================================== 熔背の土竜 (灰炉)
def ember_mole():
    m = Mount("ember_mole", "熔背の土竜")
    m.bone("body", "root", (0, 4.8, 0))
    m.bone("head", "body", (0, 4.8, -5.2))
    m.bone("tail", "body", (0, 4.4, 5.4))
    body = [rsec(-5.0, 6.6, 2.6, 3.0, 4.6, n=12), rsec(-2.0, 7.4, 2.2, 3.6, 4.8, n=12), rsec(1.6, 7.6, 2.2, 3.6, 4.8, n=12),
            rsec(4.6, 6.8, 2.6, 3.0, 4.6, n=12), rsec(6.0, 5.6, 3.4, 1.8, 4.4, n=12)]
    m.add("torso", lof("body", "fur", body), core=True)
    for i, z in enumerate((-2.6, 0.2, 3.0)):
        w = 3.4 - abs(z) * 0.22
        m.add(f"plate{i}", box("body", "rock", (0, 8.0, z), (w * 2, 1.4, 2.4), taper=(0.78, 0.9), cut=0.3))
        m.add(f"seam{i}", box("body", "ember", (0, 8.75, z), (w * 1.3, 0.3, 0.55), taper=(1, 1), cut=0.1))
        for s in (1, -1):
            m.add(f"crack{i}{s}", box("body", "ember", (s * w * 0.62, 8.2, z + 0.6), (0.3, 0.5, 0.9), taper=(1, 1), cut=0.1))
    m.add("skull", ell("head", "fur", (0, 4.8, -6.2), (2.2, 1.9, 2.4), segs=10, rings=3), core=True)
    m.add("snout", cone("head", "pink", P(0, 4.6, -7.8), (0, -0.12, -1), 2.0, 0.85, sides=7, rings=2))
    m.add("nose", ell("head", "pink", (0, 4.4, -9.8), (0.7, 0.55, 0.4), segs=7, rings=2))
    for s in (1, -1):
        eye(m, "head", (s * 1.3, 5.6, -7.6), 0.3, (s * 0.6, 0.2, -0.7), (255, 120, 60), glow=True)
    # 前は大きな掘る手 (leg_fl / leg_fr)、後ろは短い脚
    quad_bones(m, {"fl": ((2.6, 4.2, -3.6), (3.6, 2.4, -4.6)), "fr": ((-2.6, 4.2, -3.6), (-3.6, 2.4, -4.6)),
                   "bl": ((2.2, 3.4, 3.6), (2.4, 1.8, 3.8)), "br": ((-2.2, 3.4, 3.6), (-2.4, 1.8, 3.8))})
    for s, k in ((1, "l"), (-1, "r")):
        up, lo = "leg_f" + k, "leg_f" + k + "_lo"
        m.add(f"arm{k}", limb(up, "fur", [P(s * 2.4, 4.6, -3.4), P(s * 3.6, 2.4, -4.6), P(s * 3.9, 2.0, -4.9)], [1.2, 1.0, 0.9], [1.2, 1.0, 0.9], n=8))
        m.add(f"hand{k}", ell(lo, "pink", (s * 4.0, 1.5, -5.4), (1.7, 0.6, 1.5), segs=8, rings=2))
        for j in range(3):
            m.add(f"nail{k}{j}", cone(lo, "rock", P(s * (3.3 + j * 0.6), 1.3, -6.5), (0, -0.25, -1), 1.3, 0.3, sides=4, rings=1))
        bu, bl = "leg_b" + k, "leg_b" + k + "_lo"
        m.add(f"thigh{k}", limb(bu, "fur", [P(s * 2.2, 3.8, 3.4), P(s * 2.4, 1.8, 3.8), P(s * 2.4, 1.5, 3.9)], [1.0, 0.85, 0.8], [1.0, 0.85, 0.8], n=8))
        m.add(f"foot{k}", ell(bl, "pink", (s * 2.4, 0.7, 3.6), (0.8, 0.45, 1.4), segs=7, rings=2))
    m.add("tailc", cone("tail", "pink", P(0, 4.4, 5.2), (0, -0.2, 1), 2.2, 0.5, sides=5, rings=2))
    m.pal = pal_({"fur": M_(CHAR, lit=0.3), "pink": M_(PINK), "rock": M_(BASALT, lit=0.3), "ember": M_(MAG, base=0.74, lit=0.2)}, glow=["ember"])
    m.detail = lambda c, col, glow: (speckle(c, col, ["fur"], (90, 60, 50), density=0.04, size=0.6), glow)
    m.view = dict(center=(0, 4.6, 0), scale=6.6)
    idle, wk, rn = quad_gaits(walk=(0.4, 18, 22, 16, 20), run=(1.0, 34, 40, 30, 36), run_ph=TROT_PH, neck=None, tails=("tail",),
                              bob=(0.2, 0.7), pitch=(0.6, 3.0), idle_period=4.0, run_base=(0.0, 0.0))
    # 潜る: 頭から地へ (掻く手のあとに沈む)。出る: 地の下から跳ね上がる。Java は潜った間は見えない所へ移る
    dig_pose = {"body": {"rot": (40, 0, 0), "pos": (0, -9.0, -2.0)}, "head": {"rot": (30, 0, 0)},
                "leg_fl": {"rot": (-60, 0, 0)}, "leg_fr": {"rot": (-60, 0, 0)}}
    scrape = {"body": {"rot": (20, 0, 0), "pos": (0, -1.0, 0)}, "head": {"rot": (20, 0, 0)}, "leg_fl": {"rot": (40, 0, 0)}, "leg_fr": {"rot": (-30, 0, 0)}}
    scrape2 = {"body": {"rot": (24, 0, 0), "pos": (0, -2.0, 0)}, "head": {"rot": (24, 0, 0)}, "leg_fl": {"rot": (-30, 0, 0)}, "leg_fr": {"rot": (40, 0, 0)}}
    dig = keys("dig", 0.9, [(0.0, {}), (0.2, scrape), (0.4, scrape2), (0.55, scrape), (0.9, dig_pose)], hold=True)
    up = {"body": {"rot": (-30, 0, 0), "pos": (0, 1.6, 0)}, "head": {"rot": (-20, 0, 0)}, "leg_fl": {"rot": (-40, 0, 0)}, "leg_fr": {"rot": (-40, 0, 0)}}
    emerge = keys("emerge", 0.7, [(0.0, dig_pose), (0.35, up), (0.7, {})])
    m.gaits = [idle, wk, rn, dig, emerge, flinch(), side_fall(m, drop=-0.5)]
    m.poses = [("dig", 0.4), ("emerge", 0.35), ("walk", 0.3), ("death", 1.2)]
    return m


ALL = {"bell_hare": bell_hare, "moss_roller": moss_roller, "pearl_otter": pearl_otter, "bolt_weasel": bolt_weasel, "ember_mole": ember_mole}
BOUNDS = {k: (3, 2) for k in ALL}
