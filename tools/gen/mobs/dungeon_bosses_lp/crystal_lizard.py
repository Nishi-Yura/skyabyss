"""背晶の大蜥蜴 (crystal_lizard)。雷晶の空洞の主 (docs/design/55 の 1 章、00 の 366 = A 背晶の大蜥蜴)。
がっしりした四つ足。背骨に沿って雷の結晶の房が並び (光る)、満ちると雷を放つ。頭に二本の角。

作り込み (2026-11-11 の声「他のボスたちもこの方向性で」。骨掘りの大百足と同じ方向):
  首の骨を足して頭を蛇のようにもたげる。脚は膝で折れる二つの骨、肘と踵に骨の棘。背には甲板の列。
  口の中は結晶の牙が並び、喉の奥が青白く光る (口を開くと見える)。頭に結晶の冠。尾は三つの骨で、先に結晶の槌。

骨: body (胴) の子に neck → head → jaw、<脚> → <脚>_lo (fore_l / fore_r / hind_l / hind_r)、tail → tail2 → tail3、crystals (背の結晶)。
動き: idle・walk・bite (噛む)・tail (尾で後ろを薙ぐ)・charge_up (結晶を満たす。後ろ脚で立つ)・discharge (前脚を叩きつけて雷)・
      dash (突進)・breath (雷の息: のけぞって喉を光らせ、首を振って前を焼く)・pounce (跳びかかり)・intro (登場)・scream (後半の咆哮)・hurt・death
打つ瞬間は CrystalLizard の *_WARN に合わせる。
"""
import math

import numpy as np

from blp import *  # noqa: F401,F403
from blp import keys, eye

SCL = [(20, 24, 38), (34, 40, 58), (52, 60, 84), (76, 86, 116), (104, 116, 150)]
BELLY = [(84, 88, 104), (120, 124, 138), (160, 162, 174)]
CRY = [(70, 140, 226), (120, 190, 250), (184, 226, 255), (236, 250, 255)]
SPARK = [(150, 210, 255), (210, 240, 255), (250, 254, 255)]
HORN = [(96, 98, 112), (140, 142, 156), (186, 188, 200)]
CLAW = [(24, 24, 30), (46, 46, 54), (74, 74, 82)]
MAW = [(30, 8, 26), (60, 16, 44), (96, 30, 66)]
ZS = [-20.7, -13.8, -4.6, 4.6, 13.8, 20.7]
BULK = 1.15
LEGS = {"fore_l": (1, ZS[1] + 2), "fore_r": (-1, ZS[1] + 2), "hind_l": (1, ZS[-2]), "hind_r": (-1, ZS[-2])}


def build():
    m = Mount("crystal_lizard", "背晶の大蜥蜴")
    m.bone("body", "root", (0, 16, 0))
    m.bone("neck", "body", (0, 21, -17))
    m.bone("head", "neck", (0, 22, -24))
    m.bone("jaw", "head", (0, 19.5, -27))
    m.bone("crystals", "body", (0, 28, 0))
    m.bone("tail", "body", (0, 17, 18))
    m.bone("tail2", "tail", (0, 13, 32))
    m.bone("tail3", "tail2", (0, 10, 44))
    feet = {}
    for name, (s, z) in LEGS.items():
        hip = np.array([s * 7 * BULK, 15, z])
        fore = name.startswith("fore")
        knee = np.array([s * 13.5 * BULK, 9.5, z + (2.0 if fore else -2.5)])
        foot = np.array([s * 14.5 * BULK, 1.6, z - 3])
        feet[name] = (hip, knee, foot)
        m.bone(name, "body", hip)
        m.bone(name + "_lo", name, knee)
    # 胴: 幅のある肩と腰、腹は明るい鱗、背に甲板の列
    body = [rsec(z, 26 * BULK - abs(z) * 0.2, 9 + abs(z) * 0.12, (9.6 - abs(z) * 0.16) * BULK, mid=17, n=16) for z in ZS]
    m.add("torso", lof("body", "scale", body), core=True)
    m.add("belly", ell("body", "belly", (0, 11.5, 0), (8.0, 3.2, 17.0), segs=12, rings=3))
    m.add("shoulders", ell("body", "scale", (0, 22, -12), (11.5, 7.0, 7.0), segs=12, rings=3))
    m.add("hips", ell("body", "scale", (0, 21, 12), (10.5, 6.5, 6.5), segs=12, rings=3))
    for k, z in enumerate(np.linspace(ZS[0] + 1, ZS[-1] - 1, 7)):
        y = 26 * BULK - abs(z) * 0.2 - 1.2
        for s in (1, -1):
            m.add(f"plate{k}{s}", ell("body", "horn", (s * 6.2, y - 2.4, z), (2.6, 1.1, 2.4), segs=6, rings=2, rz=s * 30))
    # 首: 胴の肩に深く沈めて、頭の後ろまで
    m.add("neckb", limb("neck", "scale", [np.array([0, 21, -12]), np.array([0, 22, -19]), np.array([0, 22.5, -26])], [8.6, 7.4, 6.6], [7.6, 6.6, 5.8], n=12))
    for k in range(3):
        m.add(f"neckcry{k}", cone("neck", "cry", (0, 27.2 - k * 0.4, -14 - k * 4), (0, 1, 0.3), 4.6 - k * 0.8, 1.4, sides=6, rings=2))
    # 頭: 上あご (頭) と下あご (jaw)、結晶の冠、角、目、牙、光る喉
    hc = np.array([0, 22, -31])
    m.add("skull", lof("head", "scale", [rsec(hc[2] + 7, hc[1] + 5, hc[1] - 2, 6.4, n=12), rsec(hc[2] + 1, hc[1] + 5.6, hc[1] - 2, 7.0, n=12),
                                          rsec(hc[2] - 5, hc[1] + 3.2, hc[1] - 1.5, 5.0, n=12), rsec(hc[2] - 11, hc[1] + 1.2, hc[1] - 1, 2.8, n=12)]), core=True)
    m.add("throat", ell("head", "spark", hc + np.array([0, -1.6, 3.0]), (3.4, 1.6, 4.0), segs=10, rings=2))
    m.add("palate", ell("head", "maw", hc + np.array([0, -1.4, -2.0]), (4.4, 1.1, 6.0), segs=10, rings=2))
    m.add("jawbone", lof("jaw", "belly", [rsec(hc[2] + 6, hc[1] - 1.5, hc[1] - 5.4, 6.0, n=10), rsec(hc[2] - 2, hc[1] - 1.5, hc[1] - 4.8, 5.0, n=10),
                                          rsec(hc[2] - 10, hc[1] - 1.2, hc[1] - 3.2, 2.6, n=10)]))
    m.add("tongue", ell("jaw", "maw", hc + np.array([0, -2.2, -1.0]), (3.2, 0.8, 5.6), segs=8, rings=2))
    for k in range(5):
        for s in (1, -1):
            m.add(f"tooth{k}{s}", cone("head", "cry", hc + np.array([s * (4.0 - 0.6 * k), -1.4, 1.0 - 2.4 * k]), (s * 0.1, -1, -0.15), 2.2 - 0.2 * k, 0.6, sides=4, rings=1))
            m.add(f"ltooth{k}{s}", cone("jaw", "cry", hc + np.array([s * (3.6 - 0.55 * k), -2.0, 0.0 - 2.4 * k]), (s * 0.1, 1, -0.15), 1.7 - 0.15 * k, 0.5, sides=4, rings=1))
    for s in (1, -1):
        m.add(f"horn{s}", cone("head", "horn", hc + np.array([s * 3.4, 4.8, 3.0]), (s * 0.45, 0.55, 1), 10.0, 1.6, sides=6, rings=3, bend=(0, 1.8, 0)))
        m.add(f"hornb{s}", cone("head", "horn", hc + np.array([s * 5.0, 2.6, 4.0]), (s * 0.9, 0.1, 0.8), 4.4, 1.0, sides=5, rings=2))
        m.add(f"cheek{s}", cone("head", "cry", hc + np.array([s * 5.6, 0.4, 2.0]), (s * 1, -0.2, 0.5), 3.6, 1.0, sides=5, rings=2))
        for k, (dx, dy, dz) in enumerate(((4.8, 2.9, -2.4), (4.0, 3.6, -0.6), (5.4, 1.8, -0.8))):
            eye(m, "head", hc + np.array([s * dx, dy, dz]), 1.3 - 0.3 * k, (s, 0.3, -0.5), (250, 236, 140), glow=True)
        m.add(f"nostril{s}", ell("head", "scale", hc + np.array([s * 1.2, 1.6, -10.4]), (0.7, 0.5, 0.7), segs=6, rings=1))
    for k in range(5):
        a = math.radians(-50 + 25 * k)
        m.add(f"crown{k}", cone("head", "cry", hc + np.array([math.sin(a) * 3.0, 5.2, 3.6 + abs(k - 2) * 0.6]), (math.sin(a) * 0.6, 1, 0.5),
                                5.6 - abs(k - 2) * 1.2, 1.2, sides=6, rings=2))
    # 脚: 太腿 (上の骨) と脛・足 (下の骨)。肘・踵に棘、前へ向いた三本の爪
    for name, (hip, knee, foot) in feet.items():
        s = 1 if name.endswith("_l") else -1
        lo = name + "_lo"
        m.add(f"thigh_{name}", ell(name, "scale", hip + np.array([s * 2, 0, 0]), (5.4, 5.8, 5.8), segs=10, rings=3))
        m.add(f"upper_{name}", tub(name, "scale", [hip, knee], [4.6, 3.8], None, sides=8))
        m.add(f"knee_{name}", ell(lo, "scale", knee, (3.6, 3.6, 3.6), segs=8, rings=2))
        m.add(f"lower_{name}", tub(lo, "scale", [knee, foot + np.array([0, 1.2, 0])], [3.4, 2.8], None, sides=7))
        back = 1 if name.startswith("fore") else -1
        m.add(f"spur_{name}", cone(lo, "horn", knee + np.array([s * 1.0, 0.6, back * 2.4]), (s * 0.3, 0.5, back), 4.0, 1.0, sides=5, rings=2))
        m.add(f"foot_{name}", ell(lo, "scale", foot + np.array([0, -0.4, -1.0]), (3.4, 1.7, 4.0), segs=8, rings=2))
        for j in range(3):
            a = math.radians(-32 + 32 * j)
            m.add(f"claw_{name}{j}", cone(lo, "claw", foot + np.array([math.sin(a) * 1.6, -0.6, -2.6]), (math.sin(a), -0.25, -math.cos(a)), 4.0, 0.9, sides=4, rings=2,
                                          bend=(0, -0.8, 0)))
    # 尾: 根・中・先。先に結晶の槌
    m.add("tailb", limb("tail", "scale", [np.array([0, 17, 16]), np.array([0, 15, 26]), np.array([0, 13, 34])], [12 * BULK, 9.4, 7.6], [10 * BULK, 8.4, 6.6], n=12))
    m.add("tailm", limb("tail2", "scale", [np.array([0, 13.2, 31]), np.array([0, 11.4, 38]), np.array([0, 10, 46])], [7.8, 6.2, 4.6], [6.8, 5.4, 4.0], n=12))
    m.add("tailt", limb("tail3", "scale", [np.array([0, 10.2, 43]), np.array([0, 9, 50]), np.array([0, 8.2, 57])], [4.8, 3.6, 2.6], [4.2, 3.2, 2.2], n=10))
    for k, z in enumerate((34, 40)):
        m.add(f"tailcry{k}", cone("tail2" if k else "tail", "cry", (0, 17 - k * 3.4, z), (0, 1, 0.3), 4.8 - k, 1.4, sides=6, rings=2))
    for k in range(7):
        a = math.radians(k * 51)
        d = np.array([math.cos(a), math.sin(a) * 0.9 + 0.2, 0.25])
        m.add(f"club{k}", cone("tail3", "cry", np.array([0, 8.4, 56]), d, 5.2 - (k % 3) * 0.8, 1.6, sides=6, rings=2))
    m.add("clubcore", ell("tail3", "cry", (0, 8.4, 56.5), (3.0, 3.0, 3.4), segs=8, rings=3))
    # 背の結晶の房 (crystals): 大小を混ぜたぎざぎざの房 8 つ。真ん中が高い
    for k, z in enumerate(np.linspace(ZS[0] + 2, ZS[-1] + 2, 8)):
        h = 12 - abs(k - 3.5) * 1.5
        y = 26 * BULK - abs(z) * 0.2 - 2.6
        for j in (-1, 0, 1):
            a = j * 0.42 + (0.14 if k % 2 else -0.14)
            m.add(f"cry{k}{j}", cone("crystals", "cry", (j * 2.6, y, z + j * 0.8), (a, 1, 0.12 + 0.1 * j), h * (0.6 if j else 1.0) + 1.2, 2.0 if j else 2.8, sides=6, rings=2))
        m.add(f"cryb{k}", cone("crystals", "cry", (0, y + 1, z + 1.6), (0.25 * (-1) ** k, 0.8, 0.6), h * 0.45, 1.3, sides=5, rings=2))
    m.pal = dict(mats={"eye": M_(EYE, base=0.5, lit=0.2, noise=0.0, min=0), "scale": M_(SCL, lit=0.34, streak=0.04), "belly": M_(BELLY, lit=0.34),
                       "cry": M_(CRY, base=0.64, lit=0.22), "spark": M_(SPARK, base=0.9, lit=0.05, noise=0.0), "horn": M_(HORN), "claw": M_(CLAW),
                       "maw": M_(MAW, lit=0.2, streak=0.06)}, glow_mats=["cry", "spark"])

    def detail(c, col, glow):
        col, glow = scales(c, col, ["scale"], size=1.3, edge=0.12), glow
        col, glow = scales(c, col, ["belly"], size=1.9, edge=0.08, k_edge=0.8), glow
        col = edge_dark(c, col, ["horn"], k=0.8)
        return col, glow
    m.detail = detail
    m.hint = 512
    m.view = dict(center=(0, 16, 4), scale=2.3)
    m.gaits = gaits()
    return m


# ---------------------------------------------------------------- 動き
def merge(*poses):
    out = {}
    for p in poses:
        for b, ch in p.items():
            out.setdefault(b, {}).update(ch)
    return out


def R(**kw):
    """骨: (x, y, z) の回し。R(head=(-10, 0, 0), ...)"""
    return {b: {"rot": v} for b, v in kw.items()}


def P(**kw):
    return {b: {"pos": v} for b, v in kw.items()}


def legs(fore=(0, 0), hind=(0, 0)):
    """脚の曲げ: (上の骨, 下の骨) の x の回し。前脚・後ろ脚"""
    out = {}
    for n in ("fore_l", "fore_r"):
        out.update(R(**{n: (fore[0], 0, 0), n + "_lo": (fore[1], 0, 0)}))
    for n in ("hind_l", "hind_r"):
        out.update(R(**{n: (hind[0], 0, 0), n + "_lo": (hind[1], 0, 0)}))
    return out


def jaw(a):
    return R(jaw=(a, 0, 0))


def tail(y=0.0, x=0.0):
    return R(tail=(x, y, 0), tail2=(x * 0.8, y * 0.9, 0), tail3=(x * 0.6, y * 0.8, 0))


def shake(base, bones, amp, t0, t1, step=0.08, axis=1):
    out = []
    t, k = t0, 0
    while t <= t1 + 1e-6:
        p = merge(base)
        for b in bones:
            r = list(p.get(b, {}).get("rot", (0, 0, 0)))
            r[axis] += amp if k % 2 == 0 else -amp
            p.setdefault(b, {})["rot"] = tuple(r)
        out.append((round(t, 3), p))
        t += step
        k += 1
    return out


def gaits():
    g = []
    # 待ち: 首をゆっくり左右に振って辺りを睨み、尾が波打ち、口から時々息が漏れる
    idle = Gait("idle", 3.4)
    idle.rot("neck", W(-3).s(2.0, 1, 0, "t"), W(0).s(7.0, 1, 90, "t"))
    idle.rot("head", W(2).s(2.0, 1, 40, "t"), W(0).s(-4.0, 1, 90, "t"))
    idle.rot("jaw", W(4).s(4.0, 2, 0, "t"))
    idle.rot("tail", None, W(0).s(5.0, 1, 30, "t"))
    idle.rot("tail2", None, W(0).s(7.0, 1, 80, "t"))
    idle.rot("tail3", None, W(0).s(9.0, 1, 130, "t"))
    idle.pos("body", None, W(0).s(0.35, 1, 0, "t"))
    g.append(idle)
    # 歩き: 対角の脚を組で、膝を折って踏み出す。体を左右にくねらせ、尾が逆に振れる
    walk = Gait("walk", 1.0)
    for n, ph in (("fore_l", 0), ("hind_r", 0), ("fore_r", 180), ("hind_l", 180)):
        walk.rot(n, W(0).s(16, 1, ph, "t"))
        walk.rot(n + "_lo", W(8).s(10, 1, ph + 90, "t"))
    walk.rot("body", None, W(0).s(5.0, 1, 0, "t"), W(0).s(2.0, 1, 90, "t"))
    walk.rot("neck", None, W(0).s(-5.0, 1, 20, "t"))
    walk.rot("head", None, W(0).s(-3.0, 1, 40, "t"))
    walk.rot("tail", None, W(0).s(-8.0, 1, 30, "t"))
    walk.rot("tail2", None, W(0).s(-10.0, 1, 80, "t"))
    walk.rot("tail3", None, W(0).s(-12.0, 1, 130, "t"))
    walk.pos("body", None, W(0).s(0.7, 2, 0, "t"))
    g.append(walk)
    rest = {}

    # 噛む (BITE_WARN 14 = 0.70 秒): 首を引いて口を開き、震え、一気に首を伸ばして噛み、行き過ぎて戻る
    o1 = merge(R(neck=(-14, 0, 0), head=(-10, 0, 0), body=(-4, 0, 0)), jaw(34), P(body=(0, 0, 2)), legs((-6, 4), (4, 0)))
    o2 = merge(R(neck=(-15, 0, 0), head=(-12, 0, 0), body=(-4, 0, 0)), jaw(38), P(body=(0, 0, 2)), legs((-6, 4), (4, 0)))
    sn = merge(R(neck=(12, 0, 0), head=(8, 0, 0), body=(4, 0, 0)), jaw(0), P(body=(0, -0.5, -4)), legs((10, -6), (-6, 2)))
    so = merge(R(neck=(15, 0, 0), head=(10, 0, 0), body=(5, 0, 0)), jaw(0), P(body=(0, -1, -5)), legs((12, -8), (-8, 2)))
    g.append(keys("bite", 1.1, [(0.0, rest), (0.42, o1), (0.58, o2), (0.70, sn), (0.78, so), (0.9, sn), (1.1, rest)]))
    # 尾で薙ぐ (TAIL_WARN 16 = 0.80 秒): 体ごと捻って尾を引き、振り抜いて行き過ぎる
    coil = merge(tail(-30), R(body=(0, 12, 0), neck=(0, 14, 0), head=(0, 8, 0)), jaw(10))
    lash = merge(tail(36), R(body=(0, -14, 0), neck=(0, -12, 0), head=(0, -6, 0)), jaw(20))
    lasho = merge(tail(44), R(body=(0, -16, 0), neck=(0, -14, 0), head=(0, -8, 0)), jaw(24))
    g.append(keys("tail", 1.3, [(0.0, rest), (0.5, coil), (0.68, merge(coil, tail(-34))), (0.80, lash), (0.90, lasho), (1.02, lash), (1.3, rest)]))
    # 溜め (CHARGE_WARN 32 = 1.60 秒): 後ろ脚で立ち上がり、首を天へ伸ばして吠え、結晶が震える
    rear = merge(R(body=(-26, 0, 0), neck=(-10, 0, 0), head=(-8, 0, 0)), P(body=(0, 3, 2)), legs((-36, 20), (26, -10)), jaw(36), tail(0, 16))
    g.append(keys("charge_up", 1.6, [(0.0, rest), (0.55, rear)] + shake(rear, ["crystals", "head"], 3, 0.62, 1.4, step=0.07) + [(1.6, rear)], hold=True))
    # 放電: 前脚を床へ叩きつけ、口を全開に
    slam = merge(R(body=(10, 0, 0), neck=(8, 0, 0), head=(6, 0, 0)), P(body=(0, -1.5, -1)), legs((18, -8), (-4, 0)), jaw(40))
    g.append(keys("discharge", 1.0, [(0.0, rear), (0.18, slam), (0.26, merge(slam, P(body=(0, -2.2, -1)))), (0.6, slam), (1.0, rest)]))
    # 突進 (DASH_WARN 24 = 1.20 秒): 身を沈めて溜め、頭を下げて走る
    crouch = merge(R(body=(8, 0, 0), neck=(10, 0, 0), head=(4, 0, 0)), P(body=(0, -2, 2)), legs((-10, 24), (10, 20)), jaw(20), tail(0, -4))
    run = merge(R(body=(6, 0, 0), neck=(8, 0, 0), head=(6, 0, 0)), legs((-20, 10), (20, 0)), jaw(14))
    run_b = merge(R(body=(6, 0, 0), neck=(8, 0, 0), head=(6, 0, 0)), legs((18, 2), (-18, 14)), jaw(14))
    g.append(keys("dash", 2.2, [(0.0, rest), (0.5, crouch), (1.0, crouch), (1.2, run), (1.35, run_b), (1.5, run), (1.65, run_b), (1.8, run), (2.2, rest)]))
    # 雷の息 (BREATH_WARN 20 = 1.00 秒から 0.75 秒焼く): のけぞって喉を光らせ、首を左から右へ振りながら吐く
    inh = merge(R(neck=(-20, 0, 0), head=(-14, 0, 0), body=(-6, 0, 0)), jaw(44), P(body=(0, 0.5, 2)))
    br_l = merge(R(neck=(6, -16, 0), head=(4, -8, 0)), jaw(48), P(body=(0, 0, -1)))
    br_r = merge(R(neck=(6, 16, 0), head=(4, 8, 0)), jaw(48), P(body=(0, 0, -1)))
    g.append(keys("breath", 2.2, [(0.0, rest), (0.6, inh)] + shake(inh, ["head"], 2, 0.66, 0.92, step=0.06) +
                  [(1.0, br_l), (1.4, br_r), (1.75, merge(br_r, jaw(20))), (2.2, rest)]))
    # 跳びかかり (POUNCE_WARN 24 = 1.20 秒に落ちる): 深く沈んで溜め、跳び上がり (体が浮く)、四肢を広げて落ちる
    sink = merge(R(body=(6, 0, 0), neck=(8, 0, 0)), P(body=(0, -3, 1)), legs((-12, 30), (14, 26)), jaw(24), tail(0, -6))
    air = merge(R(body=(-10, 0, 0), neck=(-6, 0, 0), head=(4, 0, 0)), P(root=(0, 26, 0)), legs((-34, 10), (30, -6)), jaw(40), tail(0, 12))
    land = merge(R(body=(8, 0, 0), neck=(8, 0, 0), head=(6, 0, 0)), P(body=(0, -2.5, 0)), legs((16, 10), (-10, 18)), jaw(36))
    g.append(keys("pounce", 1.8, [(0.0, rest), (0.45, sink), (0.62, merge(sink, P(body=(0, -3.6, 1)))), (0.82, air), (1.05, air), (1.20, land),
                                  (1.32, merge(land, P(body=(0, -3.2, 0)))), (1.8, rest)]))
    # 後半の咆哮 (RAGE_WARN 24 = 1.20 秒): 後ろ脚で立ち、首を天へ、口を全開に、結晶を震わせて前へ吠えつける
    sky = merge(R(body=(-30, 0, 0), neck=(-14, 0, 0), head=(-12, 0, 0)), P(body=(0, 4, 2)), legs((-40, 24), (28, -10)), jaw(48), tail(0, 18))
    fw = merge(R(body=(-6, 0, 0), neck=(10, 0, 0), head=(8, 0, 0)), jaw(48), legs((-4, 0), (4, 0)))
    g.append(keys("scream", 1.6, [(0.0, rest), (0.32, sky)] + shake(sky, ["head", "crystals"], 4, 0.38, 1.04, step=0.07, axis=2) +
                  [(1.20, fw), (1.6, rest)]))
    # 登場 (5.0 秒): 床を割って前脚の爪が掛かり、頭が這い出て辺りを嗅ぎ、体を引き抜き、
    # 身を震わせて土を払うと背の結晶が順に灯り、立ち上がって吠える (名前の札はここ 3.40 秒 = introCardAt 68)、四つ足に戻って睨む
    under = merge(R(body=(-24, 0, 0), neck=(-8, 0, 0), head=(-6, 0, 0)), P(root=(0, -40, 0)), legs((-30, 10), (20, 0)))
    claw_in = merge(under, P(root=(0, -30, 0)), legs((-50, 30), (20, 0)))
    head_out = merge(R(body=(-26, 0, 0), neck=(-4, -10, 0), head=(2, -8, 0)), P(root=(0, -20, 0)), legs((-30, 20), (20, 0)), jaw(16))
    sniff = merge(head_out, R(neck=(-4, 12, 0), head=(2, 10, 0)), jaw(8))
    pull = merge(R(body=(-14, 0, 0), neck=(4, 0, 0)), P(root=(0, -6, 0)), legs((10, -10), (24, 10)), jaw(30))
    out = merge(rest, P(root=(0, 0, 0)))
    it = [(0.0, merge(under, P(root=(0, -60, 0)))), (0.45, claw_in), (0.6, merge(claw_in, P(root=(0, -28, 0)))), (1.0, head_out), (1.3, sniff),
          (1.6, head_out), (2.05, pull), (2.3, out)]
    it += shake(merge(out, R(body=(0, 0, 0))), ["body"], 5, 2.4, 2.9, step=0.06, axis=2)
    it += [(3.15, merge(sky)), (3.40, merge(sky, jaw(52)))]
    it += shake(merge(sky, jaw(52)), ["head", "crystals"], 4, 3.46, 4.10, step=0.07, axis=2)
    it += [(4.45, merge(fw, jaw(20))), (4.75, merge(R(neck=(-6, 0, 0)), jaw(12))), (5.0, rest)]
    g.append(keys("intro", 5.0, it))
    g.append(keys("hurt", 0.3, [(0.0, rest), (0.1, merge(R(neck=(-10, 8, 0), head=(-6, 0, 0), body=(0, 0, 3)), jaw(24))), (0.3, rest)]))
    # 倒れる: のけぞって吠え、よろめき、横倒しに。結晶はそのまま
    agony = merge(R(body=(-14, 0, 0), neck=(-16, 0, 0), head=(-10, 0, 0)), jaw(50), legs((-20, 10), (10, 0)))
    fall = merge(R(body=(0, 0, 70), neck=(10, 0, 10), head=(6, 0, 8)), P(body=(0, -4, 0)), jaw(26), R(fore_l=(-30, 0, 0), hind_l=(-20, 0, 0)), tail(12))
    g.append(keys("death", 2.0, [(0.0, rest), (0.35, agony)] + shake(agony, ["neck"], 5, 0.42, 0.9, step=0.07) + [(1.5, fall), (2.0, fall)], hold=True))
    return g


POSES = [("idle", 0.0), ("walk", 0.3), ("bite", 0.58), ("bite", 0.70), ("tail", 0.80), ("charge_up", 0.9), ("discharge", 0.26), ("breath", 1.0),
         ("pounce", 0.62), ("pounce", 1.0), ("pounce", 1.2), ("scream", 0.5), ("intro", 0.6), ("intro", 1.3), ("intro", 3.4), ("death", 2.0)]
BOUNDS = (10, 6)
