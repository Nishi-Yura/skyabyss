"""炉負いの巨人 (forge_giant)。熔けた坑道の主 (docs/design/55 の 1 章、00 の 367 = A 炉負いの巨人)。
前かがみの玄武岩の巨人。背に大きな炉と煙突 (中が光る)、右手は鎚と一体、肌の割れ目から熔けた光。

作り込み (2026-11-11 の声「他のボスたちもこの方向性で」。骨掘りの大百足と同じ方向):
  頭は下あごが外れるように開き、中は熔けた火 (炉の口のような顔)。岩の角と冠の棘。胸の真ん中に鉄格子越しに光る炉の心臓。
  脚は膝で折れる二つの骨。背の炉には扉があり、噴き上げ・咆哮で開いて中の火が見える。鎚の面に棘、左手に岩の爪、肩に棘。

骨: body (腰) の子に torso (胸)・leg_l → leg_l_lo、leg_r → leg_r_lo。torso の子に head → jaw・forge → forge_door (背の炉と扉)・
    arm_l → fore_l、arm_r → fore_r (鎚)。
動き: idle・walk・hammer (振り下ろし)・combo (三連打ち)・sweep (横に薙ぐ)・stomp (足踏み)・trench (鎚で床を裂いて前へ引く)・
      blast (炉から火を噴き上げる)・leap (跳び叩き)・breath (口から火を吐く)・intro (登場)・scream (後半の咆哮)・hurt・death
打つ瞬間は ForgeGiant の *_WARN に合わせる。
"""
import math

import numpy as np

from blp import *  # noqa: F401,F403
from blp import keys, eye

BAS = [(22, 20, 22), (34, 30, 32), (50, 44, 46), (68, 60, 60), (90, 80, 78)]
MAG = [(160, 50, 16), (220, 96, 30), (252, 156, 56), (255, 214, 120), (255, 244, 200)]
IRON = [(36, 32, 32), (58, 52, 50), (84, 76, 72), (114, 104, 96)]
BRICK = [(70, 30, 22), (104, 46, 32), (138, 66, 44), (170, 92, 62)]
HUNCH = 1.2
HEAD = np.array([0, 60 - 6 * HUNCH, -10 - 4 * HUNCH])


def build():
    m = Mount("forge_giant", "炉負いの巨人")
    m.bone("body", "root", (0, 34, 2))
    m.bone("torso", "body", (0, 38, 2))
    m.bone("head", "torso", (0, 54, -12))
    m.bone("jaw", "head", HEAD + np.array([0, -1.6, 1.8]))
    m.bone("forge", "torso", (0, 46, 12))
    m.bone("forge_door", "forge", (0, 49.0, 28.0))
    for s, side in ((1, "l"), (-1, "r")):
        m.bone(f"leg_{side}", "body", (s * 7, 34, 2))
        m.bone(f"leg_{side}_lo", f"leg_{side}", (s * 8.5, 20, 0))
        m.bone(f"arm_{side}", "torso", (s * 15, 56, -2))
        m.bone(f"fore_{side}", f"arm_{side}", (s * 21, 42, -6))
    # 胴: 前かがみの厚い胸、腰、胸の炉の心臓と鉄格子
    torso = [rsec(z, 62 - abs(z + 2) * 0.6 * HUNCH, 34, 14.5 - abs(z) * 0.1, mid=48, n=16) for z in (-10, -5, 0, 5, 10)]
    m.add("chest", lof("torso", "basalt", torso), core=True)
    m.add("belly", ell("torso", "basalt", (0, 40, -4), (12, 7, 8), segs=12, rings=3))
    m.add("core", ell("torso", "magma", (0, 48, -9.8), (5.0, 5.4, 1.6), segs=10, rings=2))
    for k in range(4):
        m.add(f"grille{k}", box("torso", "iron", ((k - 1.5) * 2.6, 48, -11.0), (0.9, 11.0, 1.2)))
    m.add("grilleh", box("torso", "iron", (0, 48, -11.2), (11.0, 0.9, 1.2)))
    for s in (1, -1):
        for k in range(3):
            m.add(f"rib{s}{k}", ell("torso", "iron", (s * 9.0, 52 - k * 5, -8.6 + k * 0.4), (4.4, 1.0, 2.4), segs=8, rings=2, rz=s * (14 + k * 6)))
    m.add("hips", ell("body", "basalt", (0, 34, 2), (12.5, 6.5, 9.5), segs=12, rings=3), core=True)
    m.add("belt", ell("body", "iron", (0, 36, 2), (13.4, 2.0, 10.2), segs=14, rings=2))
    # 頭: 低く前に突き出た岩の頭、光る目、外れるように開く下あご、口の中は熔けた火、角と冠
    head = HEAD
    m.add("skull", ell("head", "basalt", head + np.array([0, 1.0, 0]), (5.8, 4.6, 5.6), segs=10, rings=3), core=True)
    m.add("neck", ell("head", "basalt", (0, 54, -8.5), (4.8, 4.6, 5.8), segs=10, rings=3))
    m.add("brow", ell("head", "basalt", head + np.array([0, 2.8, -2.6]), (6.2, 1.9, 3.0), segs=10, rings=2))
    m.add("mouthfire", ell("head", "magma", head + np.array([0, -1.6, -1.6]), (3.8, 1.5, 3.4), segs=10, rings=2))
    m.add("jawbone", ell("jaw", "basalt", head + np.array([0, -3.8, -2.0]), (4.8, 2.3, 4.4), segs=9, rings=2))
    m.add("jawiron", ell("jaw", "iron", head + np.array([0, -4.6, -2.4]), (4.4, 1.2, 3.8), segs=8, rings=2))
    for k in range(4):
        x = (k - 1.5) * 2.0
        m.add(f"utooth{k}", cone("head", "iron", head + np.array([x, -0.6, -4.4 + abs(x) * 0.3]), (0, -1, -0.15), 2.2, 0.6, sides=4, rings=1))
        m.add(f"ltooth{k}", cone("jaw", "iron", head + np.array([x * 0.9, -2.8, -4.2 + abs(x) * 0.3]), (0, 1, -0.15), 1.8, 0.55, sides=4, rings=1))
    for s in (1, -1):
        eye(m, "head", head + np.array([s * 2.4, 1.2, -4.6]), 1.0, (s * 0.3, 0, -1), (255, 160, 60), glow=True)
        m.add(f"cheek{s}", ell("head", "basalt", head + np.array([s * 4.4, -0.6, -1.2]), (2.3, 2.6, 2.6), segs=8, rings=2))
        m.add(f"horn{s}", cone("head", "basalt", head + np.array([s * 4.0, 3.4, 0.6]), (s * 0.8, 0.5, -0.5), 8.0, 1.8, sides=6, rings=3, bend=(0, 3.4, -1.2)))
    for k in range(5):
        a = math.radians(-50 + 25 * k)
        m.add(f"crown{k}", cone("head", "basalt", head + np.array([math.sin(a) * 3.6, 4.4, 1.4]), (math.sin(a) * 0.5, 1, 0.4), 3.8 - abs(k - 2) * 0.6, 1.0, sides=5, rings=2))
    # 脚: 太腿 (上) と脛・足 (下)。膝は鉄の当て
    for s, side in ((1, "l"), (-1, "r")):
        lo = f"leg_{side}_lo"
        m.add(f"thigh_{side}", limb(f"leg_{side}", "basalt", [(s * 7, 34, 2), (s * 8.2, 26, 0.6), (s * 8.5, 19, 0)], [9.5, 9.0, 8.4], [9.5, 9.0, 8.4], n=12))
        m.add(f"shin_{side}", limb(lo, "basalt", [(s * 8.5, 21, 0), (s * 8.5, 13, 0.6), (s * 8.5, 6, 2)], [8.4, 7.8, 7.4], [8.4, 7.8, 7.4], n=12))
        m.add(f"knee_{side}", ell(lo, "iron", (s * 8.8, 20, -3.4), (4.4, 4.2, 2.4), segs=8, rings=2))
        m.add(f"kneespike_{side}", cone(lo, "iron", (s * 8.8, 20, -5.0), (0, 0.3, -1), 3.2, 1.2, sides=5, rings=2))
        m.add(f"foot_{side}", ell(lo, "basalt", (s * 8.5, 3, -1.5), (5.8, 3.3, 7.8), segs=10, rings=2))
        for k in range(3):
            m.add(f"toe_{side}{k}", cone(lo, "basalt", (s * 8.5 + (k - 1) * 2.6, 2.0, -7.6), (0, -0.3, -1), 2.6, 1.3, sides=5, rings=2))
    # 腕: 肩の鉄の当てと棘、上の腕、前の腕。右手は棘のある鎚と一体、左手は岩の爪の拳
    for s, side in ((1, "l"), (-1, "r")):
        sh = np.array([s * 15, 56, -2])
        el = np.array([s * 21, 42, -6])
        wr = np.array([s * 22, 28, -8])
        m.add(f"upper_{side}", limb(f"arm_{side}", "basalt", [sh, (sh + el) / 2, el + (el - sh) * 0.15], [9.5, 8.5, 7.8], [9.5, 8.5, 7.8], n=12))
        m.add(f"pauldron_{side}", ell(f"arm_{side}", "iron", sh + np.array([s * 1.2, 3.2, 0]), (7.8, 5.4, 7.8), segs=10, rings=3))
        for k in range(3):
            m.add(f"pspike_{side}{k}", cone(f"arm_{side}", "iron", sh + np.array([s * (1.0 + k * 2.2), 7.4 - k * 1.0, (k - 1) * 2.4]), (s * 0.35, 1, 0),
                                            5.0 - k * 1.0, 1.3, sides=5, rings=2))
        m.add(f"lower_{side}", limb(f"fore_{side}", "basalt", [el - (el - sh) * 0.15, (el + wr) / 2, wr], [8.0, 7.4, 6.8], [8.0, 7.4, 6.8], n=12))
        m.add(f"bracer_{side}", ell(f"fore_{side}", "iron", (el + wr) / 2 + np.array([0, -1, 0]), (5.2, 4.2, 5.2), segs=10, rings=2))
        if side == "r":
            m.add("haft", tub("fore_r", "iron", [wr + np.array([0, -3, 0]), wr + np.array([0, -3, -19])], [1.9, 1.9], None, sides=7))
            m.add("hammer", lof("fore_r", "iron", [oct_ring(wr + np.array([-8.5, -3, -22]), 12, 12, R=rot_y(90)), oct_ring(wr + np.array([-6.5, -3, -22]), 13.5, 13.5, R=rot_y(90)),
                                                    oct_ring(wr + np.array([6.5, -3, -22]), 13.5, 13.5, R=rot_y(90)), oct_ring(wr + np.array([8.5, -3, -22]), 12, 12, R=rot_y(90))]))
            for k in range(4):
                a = math.radians(45 + 90 * k)
                for sx in (1, -1):
                    m.add(f"hspike{k}{sx}", cone("fore_r", "iron", wr + np.array([sx * 8.6, -3 + math.sin(a) * 3.6, -22 + math.cos(a) * 3.6]), (sx, 0, 0), 3.2, 1.1, sides=4, rings=2))
            m.add("hammer_glow", ell("fore_r", "magma", wr + np.array([0, -3, -28.9]), (4.4, 4.4, 0.9), segs=8, rings=1))
            m.add("hammer_rune", ell("fore_r", "magma", wr + np.array([0, 3.9, -22]), (5.6, 0.5, 4.2), segs=8, rings=1))
            m.add("fist_r", ell("fore_r", "basalt", wr + np.array([0, -3, 0]), (5.4, 5.4, 5.4), segs=9, rings=2))
        else:
            m.add("fist_l", ell("fore_l", "basalt", wr + np.array([0, -3.5, 0]), (6.2, 6.0, 6.2), segs=10, rings=3))
            for k in range(4):
                m.add(f"knuckle{k}", ell("fore_l", "basalt", wr + np.array([(k - 1.5) * 2.4, -6.5, -3.0]), (1.6, 1.6, 1.6), segs=6, rings=2))
                m.add(f"talon{k}", cone("fore_l", "iron", wr + np.array([(k - 1.5) * 2.4, -7.4, -3.6]), (0, -0.6, -1), 3.4, 0.8, sides=4, rings=2, bend=(0, -1.2, 0)))
    # 背の炉: 煉瓦の胴、火の口と扉、煙突、鉄の帯
    m.add("forge_body", lof("forge", "brick", [oct_ring((0, 46, 13), 19, 23, R=np.eye(3)), oct_ring((0, 46, 21), 18, 22, R=np.eye(3)),
                                               oct_ring((0, 46, 27), 15, 19, R=np.eye(3))]))
    m.add("forge_band", lof("forge", "iron", [oct_ring((0, 46, 19.5), 19.6, 23.6, R=np.eye(3)), oct_ring((0, 46, 21.5), 19.6, 23.6, R=np.eye(3))]))
    m.add("forge_mouth", ell("forge", "magma", (0, 43, 27.2), (6.0, 5.4, 1.0), segs=10, rings=1))
    m.add("forge_door", box("forge_door", "iron", (0, 43.4, 28.3), (13.0, 11.0, 1.2)))
    for k in range(3):
        m.add(f"door_slit{k}", box("forge_door", "magma", ((k - 1) * 3.4, 43.4, 28.9), (1.0, 7.0, 0.3)))
    m.add("door_hinge", tub("forge_door", "iron", [(-6.5, 49.0, 28.2), (6.5, 49.0, 28.2)], [0.9, 0.9], None, sides=6))
    m.add("forge_grate", box("forge", "iron", (0, 37.6, 27.6), (12, 1.6, 1.4)))
    m.add("chimney", tub("forge", "brick", [(4.5, 56, 20), (5.0, 66, 21.5), (5.5, 76, 23)], [3.6, 3.2, 2.8], None, sides=7))
    m.add("chimney2", tub("forge", "brick", [(-5.0, 56, 20), (-5.6, 63, 21.0), (-6.2, 70, 22)], [2.8, 2.5, 2.2], None, sides=7))
    m.add("chimney_cap", ell("forge", "iron", (5.5, 76.4, 23), (4.0, 1.2, 4.0), segs=8, rings=1))
    m.add("smoke_glow", ell("forge", "magma", (5.5, 77.2, 23), (2.6, 0.8, 2.6), segs=6, rings=1))
    m.add("smoke_glow2", ell("forge", "magma", (-6.2, 70.8, 22), (1.9, 0.7, 1.9), segs=6, rings=1))
    m.pal = dict(mats={"eye": M_(EYE, base=0.5, lit=0.2, noise=0.0, min=0), "basalt": M_(BAS, lit=0.34, streak=0.05), "magma": M_(MAG, base=0.66, lit=0.2),
                       "iron": M_(IRON, lit=0.36), "brick": M_(BRICK, streak=0.04)}, glow_mats=["magma"])

    def detail(c, col, glow):
        P = c["P"]
        sel = np.isin(c["mats"], ["basalt"]) & c["owned"]
        f1, f2, idv = cell3(P, 4.0, 17)
        crack = sel & ((f2 - f1) < 0.035) & (idv > 0.5)
        col = np.where(crack[..., None], np.array([250, 140, 50]), col)
        col = edge_dark(c, col, ["iron"], k=0.75)
        return col, glow | crack
    m.detail = detail
    m.hint = 512
    m.view = dict(center=(0, 38, 4), scale=1.7)
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
    return {b: {"rot": v} for b, v in kw.items()}


def P(**kw):
    return {b: {"pos": v} for b, v in kw.items()}


def jaw(a):
    return R(jaw=(a, 0, 0))


def door(a):
    """背の炉の扉を a 度開く"""
    return R(forge_door=(-a, 0, 0))


def knees(l=0.0, r=0.0, hip_l=0.0, hip_r=0.0):
    return R(leg_l=(-hip_l, 0, 0), leg_l_lo=(l, 0, 0), leg_r=(-hip_r, 0, 0), leg_r_lo=(r, 0, 0))


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
    # 待ち: 肩で重く息をし、頭が辺りを見回し、口の火が漏れる
    idle = Gait("idle", 3.4)
    idle.rot("torso", W(0).s(2.0, 1, 0, "t"))
    idle.rot("head", W(0).s(2.5, 1, 60, "t"), W(0).s(5.0, 1, 0, "t"))
    idle.rot("jaw", W(4).s(4.0, 2, 0, "t"))
    idle.rot("arm_l", W(0).s(2.5, 1, 30, "t"))
    idle.rot("arm_r", W(0).s(2.5, 1, 210, "t"))
    idle.rot("forge_door", W(-3).s(3.0, 3, 0, "t"))
    idle.pos("torso", None, W(0).s(0.5, 1, 0, "t"))
    g.append(idle)
    # 歩き: 膝を折って一歩ずつ重く踏み、体が沈み、上体が揺れる
    walk = Gait("walk", 1.6)
    walk.rot("leg_l", W(0).s(18, 1, 0, "t"))
    walk.rot("leg_r", W(0).s(18, 1, 180, "t"))
    walk.rot("leg_l_lo", W(10).s(12, 1, 90, "t"))
    walk.rot("leg_r_lo", W(10).s(12, 1, 270, "t"))
    walk.rot("arm_l", W(0).s(-12, 1, 0, "t"))
    walk.rot("arm_r", W(0).s(-12, 1, 180, "t"))
    walk.rot("torso", None, W(0).s(5, 1, 0, "t"), W(0).s(3, 1, 90, "t"))
    walk.rot("head", None, W(0).s(-3, 1, 0, "t"))
    walk.pos("body", None, W(-0.6).s(-1.2, 2, 90, "t"))
    g.append(walk)
    rest = {}
    # 振り下ろし (HAMMER_WARN 20 = 1.00 秒): のけぞって鎚を高く掲げ、震え、全身で叩きつけ、跳ね返る
    up = merge(R(torso=(-18, 0, 0), arm_r=(-160, 0, -10), fore_r=(-40, 0, 0), arm_l=(-24, 0, 14), head=(-10, 0, 0)), jaw(20), knees(6, 6, 4, 4))
    upt = merge(up, R(arm_r=(-166, 0, -10), torso=(-20, 0, 0)), jaw(26))
    smash = merge(R(torso=(26, 0, 0), arm_r=(-34, 0, -6), fore_r=(-8, 0, 0), arm_l=(12, 0, 0), head=(12, 0, 0)), jaw(34), P(body=(0, -3, -1)), knees(20, 20, 12, 12))
    over = merge(smash, R(torso=(30, 0, 0), head=(14, 0, 0)), P(body=(0, -3.6, -1)))
    g.append(keys("hammer", 1.7, [(0.0, rest), (0.6, up), (0.85, upt), (1.00, smash), (1.08, over), (1.3, smash), (1.7, rest)]))
    # 三連打ち (0.90・1.30・1.70 秒 = COMBO_WARN + 0・8・16): 叩き、半分振り上げ、叩き、最後は一歩踏み込んで全力で
    half = merge(R(torso=(-6, 0, 0), arm_r=(-120, 0, -8), fore_r=(-30, 0, 0)), jaw(16))
    hit = merge(R(torso=(20, 0, 0), arm_r=(-36, 0, -6), fore_r=(-8, 0, 0), head=(8, 0, 0)), jaw(26), P(body=(0, -2, -1)), knees(14, 14, 8, 8))
    big = merge(over, P(body=(0, -3.6, -5)), knees(24, 8, 20, 0))
    g.append(keys("combo", 2.4, [(0.0, rest), (0.55, up), (0.90, hit), (1.08, half), (1.30, merge(hit, P(body=(0, -2, -3)))), (1.46, merge(up, P(body=(0, 0, -3)))),
                                 (1.62, merge(upt, P(body=(0, 0, -3)))), (1.70, big), (1.9, big), (2.4, rest)]))
    # 横薙ぎ (SWEEP_WARN 18 = 0.90 秒): 体を大きく捻って溜め、振り抜いて行き過ぎる
    wind = merge(R(torso=(0, 46, 0), arm_r=(-72, 52, 0), fore_r=(-20, 0, 0), arm_l=(-10, 0, 0), head=(0, -20, 0)), jaw(14), knees(8, 8, 6, 6))
    swipe = merge(R(torso=(0, -50, 0), arm_r=(-70, -54, 0), fore_r=(-10, 0, 0), arm_l=(-10, 0, 0), head=(0, 16, 0)), jaw(30))
    swipe_o = merge(swipe, R(torso=(0, -58, 0)))
    g.append(keys("sweep", 1.4, [(0.0, rest), (0.58, wind), (0.78, merge(wind, R(torso=(0, 50, 0)))), (0.90, swipe), (1.0, swipe_o), (1.12, swipe), (1.4, rest)]))
    # 足踏み (STOMP_WARN 20 = 1.00 秒): 片足を高く上げ、両腕を広げて溜め、踏み抜く
    lift = merge(knees(46, 6, 52, 0), R(torso=(-8, 0, 6), arm_l=(-24, 0, 24), arm_r=(-24, 0, -24)), jaw(24))
    stamp = merge(knees(6, 14, 0, 6), R(torso=(12, 0, -2)), P(body=(0, -3, 0)), jaw(36))
    g.append(keys("stomp", 1.4, [(0.0, rest), (0.68, lift), (0.86, merge(lift, knees(50, 6, 56, 0))), (1.00, stamp), (1.1, merge(stamp, P(body=(0, -3.6, 0)))), (1.4, rest)]))
    # 熔けた溝 (TRENCH_WARN 20 = 1.00 秒): 鎚を脇へ振りかぶり、床へ叩き込んで前へ引き裂く
    tup = merge(R(torso=(-10, 20, 0), arm_r=(-140, 30, -10), fore_r=(-30, 0, 0)), jaw(16))
    tdown = merge(R(torso=(28, 0, 0), arm_r=(-24, 0, -4), fore_r=(-6, 0, 0), head=(10, 0, 0)), jaw(30), P(body=(0, -3, -1)), knees(18, 18, 10, 10))
    tdrag = merge(R(torso=(32, -12, 0), arm_r=(-14, -16, -4), fore_r=(0, 0, 0), head=(10, 0, 0)), jaw(34), P(body=(0, -3, -4)), knees(20, 6, 16, 0))
    g.append(keys("trench", 1.8, [(0.0, rest), (0.6, tup), (1.00, tdown), (1.4, tdrag), (1.8, rest)]))
    # 炉の噴き上げ (BLAST_WARN 28 = 1.40 秒に落ちる): 前へ深くかがみ、背の扉が開き、頭を上げて吠えながら脈打つ
    bow = merge(R(torso=(34, 0, 0), head=(-22, 0, 0), arm_l=(30, 0, 14), arm_r=(30, 0, -14), forge=(-6, 0, 0)), door(70), jaw(40), knees(10, 10, 6, 6))
    vent = merge(R(torso=(40, 0, 0), head=(-28, 0, 0), arm_l=(36, 0, 16), arm_r=(36, 0, -16), forge=(4, 0, 0)), door(90), jaw(48), knees(12, 12, 8, 8))
    g.append(keys("blast", 1.8, [(0.0, rest), (0.5, merge(bow, door(20))), (0.65, bow), (0.8, vent), (1.0, bow), (1.2, vent), (1.4, bow), (1.8, rest)]))
    # 跳び叩き (LEAP_WARN 26 = 1.30 秒に落ちる): 深く沈み、鎚を掲げて跳び、空で振りかぶり、落ちざまに叩きつける
    crouch = merge(knees(40, 40, 30, 30), R(torso=(16, 0, 0), arm_r=(-40, 0, -10), head=(6, 0, 0)), P(body=(0, -6, 0)), jaw(20))
    air = merge(knees(30, 10, 34, 6), R(torso=(-16, 0, 0), arm_r=(-170, 0, -8), fore_r=(-40, 0, 0), arm_l=(-60, 0, 20), head=(-10, 0, 0)), P(root=(0, 30, 0)), jaw(36))
    land = merge(smash, knees(30, 30, 20, 20), P(body=(0, -5, -2)))
    g.append(keys("leap", 2.0, [(0.0, rest), (0.45, crouch), (0.62, merge(crouch, P(body=(0, -7, 0)))), (0.82, air), (1.12, merge(air, P(root=(0, 26, 0)))),
                                (1.30, land), (1.42, merge(land, P(body=(0, -6, -2)))), (2.0, rest)]))
    # 火の息 (BREATH_WARN 20 = 1.00 秒から 0.8 秒焼く): のけぞって口を外し、喉の火を見せ、首を左から右へ振って吐く
    inh = merge(R(torso=(-12, 0, 0), head=(-24, 0, 0)), jaw(46), door(30), knees(8, 8, 4, 4))
    bl = merge(R(torso=(10, -18, 0), head=(6, -16, 0)), jaw(52), door(40))
    brr = merge(R(torso=(10, 18, 0), head=(6, 16, 0)), jaw(52), door(40))
    g.append(keys("breath", 2.2, [(0.0, rest), (0.6, inh)] + shake(inh, ["head"], 2, 0.66, 0.92, step=0.06) + [(1.0, bl), (1.45, brr), (1.8, merge(brr, jaw(20))), (2.2, rest)]))
    # 後半の咆哮 (RAGE_WARN 24 = 1.20 秒): 両腕を広げて胸を反らし、扉が開いて火が噴き、天へ吠えてから前へ
    sky = merge(R(torso=(-22, 0, 0), head=(-26, 0, 0), arm_l=(-40, 0, 50), arm_r=(-40, 0, -50), fore_l=(-20, 0, 0), fore_r=(-20, 0, 0)), jaw(56), door(90))
    fw = merge(R(torso=(18, 0, 0), head=(10, 0, 0), arm_l=(10, 0, 30), arm_r=(10, 0, -30)), jaw(56), door(60))
    g.append(keys("scream", 1.6, [(0.0, rest), (0.32, sky)] + shake(sky, ["head"], 4, 0.38, 1.04, step=0.07, axis=2) + [(1.2, fw), (1.6, rest)]))
    # 登場 (5.0 秒): 鎚を握った右腕が床を割って突き出し、左手を床に突いて頭と肩が這い出て、体を引き上げ、片膝をつき、
    # 立ち上がると背の炉の扉が弾けて火が噴き、鎚を掲げて吠える (名前の札はここ 3.30 秒 = introCardAt 66)、鎚を床へ叩き下ろして構える
    arm_up = R(arm_r=(-170, 0, -6), fore_r=(-20, 0, 0))
    it = [(0.0, merge(arm_up, P(root=(0, -80, 0)), knees(60, 60, 40, 40))),
          (0.5, merge(arm_up, P(root=(0, -56, 0)), knees(60, 60, 40, 40), R(torso=(20, 0, 0))))]
    it += shake(merge(arm_up, P(root=(0, -54, 0)), knees(60, 60, 40, 40), R(torso=(20, 0, 0))), ["arm_r"], 5, 0.6, 1.0, step=0.08, axis=2)
    it += [(1.35, merge(R(arm_r=(-120, 0, -10), arm_l=(-70, 0, 10), torso=(30, 0, 0), head=(-14, 0, 0)), P(root=(0, -34, 0)), knees(60, 60, 40, 40), jaw(20))),
           (1.85, merge(R(arm_r=(-60, 0, -10), arm_l=(-30, 0, 10), torso=(26, 0, 0), head=(-8, 0, 0)), P(root=(0, -14, 0)), knees(70, 40, 50, 20), jaw(30))),
           (2.3, merge(R(torso=(20, 0, 0), arm_l=(10, 0, 0), head=(-6, 0, 0)), P(body=(0, -10, 0)), knees(80, 10, 70, 0), jaw(16))),
           (2.75, merge(R(torso=(4, 0, 0)), jaw(10))),
           (2.95, merge(R(torso=(10, 0, 0)), door(90), jaw(30))),
           (3.30, merge(R(torso=(-20, 0, 0), head=(-26, 0, 0), arm_r=(-170, 0, -10), fore_r=(-30, 0, 0), arm_l=(-40, 0, 46)), door(90), jaw(56)))]
    roar = merge(R(torso=(-20, 0, 0), head=(-26, 0, 0), arm_r=(-170, 0, -10), fore_r=(-30, 0, 0), arm_l=(-40, 0, 46)), door(90), jaw(56))
    it += shake(roar, ["head"], 4, 3.38, 4.1, step=0.07, axis=2)
    it += [(4.45, merge(smash, door(50))), (4.6, merge(over, door(40))), (5.0, rest)]
    g.append(keys("intro", 5.0, it))
    g.append(keys("hurt", 0.32, [(0.0, rest), (0.12, merge(R(torso=(-8, 0, 4), head=(-10, 0, 0)), jaw(26))), (0.32, rest)]))
    # 倒れる: 膝をつき、扉が開いて火が漏れ、口を開いたまま前へ崩れる
    kneel = merge(P(body=(0, -10, 0)), knees(90, 10, 80, 0), R(torso=(30, 0, 0), head=(20, 0, 0), arm_l=(20, 0, 10), arm_r=(-30, 0, -20)), door(60), jaw(40))
    fall = merge(P(body=(0, -14, -6)), R(body=(60, 0, 0), torso=(20, 0, 0), head=(10, 0, 0), arm_l=(-60, 0, 30), arm_r=(-80, 0, -30)), knees(40, 30, 40, 30), door(90), jaw(50))
    g.append(keys("death", 2.2, [(0.0, rest), (0.3, merge(R(torso=(-14, 0, 0), head=(-20, 0, 0)), jaw(56)))] + [(0.8, kneel), (1.2, kneel), (1.9, fall), (2.2, fall)], hold=True))
    return g


POSES = [("idle", 0.0), ("walk", 0.4), ("hammer", 0.85), ("hammer", 1.0), ("combo", 1.7), ("sweep", 0.78), ("stomp", 0.86), ("trench", 1.4),
         ("blast", 0.8), ("leap", 0.62), ("leap", 1.12), ("breath", 1.0), ("scream", 0.5), ("intro", 0.6), ("intro", 2.3), ("intro", 3.3)]
BOUNDS = (10, 9)
