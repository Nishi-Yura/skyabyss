"""大海月の巫女 (jelly_priestess)。沈んだ神殿の中ボス (2026-11-09 のラフ tools/rough/lowpoly/deep_mids で C)。
珊瑚の中庭に漂う大きな海月。透ける青い傘の縁に光る点、傘の下から長い触手が床まで垂れ、その間に巫女が吊られている
(潮色の衣、貝の面、手を組んで祈る)。傘の芯が光る。

骨: body の子に bell (傘) と priest (巫女)。bell の子に tent0..tent7 (触手)。priest の子に phead。
動き: idle・walk (漂う)・lash (触手の鞭)・rain (毒の雨)・pulse (脈打ち)・hurt・death
"""
import math

import numpy as np

from blp import *  # noqa: F401,F403
from blp import keys, eye

JELLY = [(40, 70, 120), (70, 120, 180), (110, 170, 220), (170, 220, 246), (220, 246, 255)]
FROND = [(90, 60, 150), (130, 90, 200), (180, 140, 236)]
GLOW = [(60, 200, 210), (120, 236, 236), (200, 255, 250)]
ROBE = [(14, 40, 50), (22, 60, 72), (34, 86, 98), (52, 114, 124)]
SHELL = [(150, 136, 116), (196, 182, 160), (226, 216, 198), (244, 238, 226)]
SKIN = [(70, 96, 100), (96, 128, 130), (128, 158, 156)]
TRIM = [(110, 86, 40), (160, 128, 60), (206, 170, 90)]
N_TENT = 8


def build():
    m = Mount("jelly_priestess", "大海月の巫女")
    m.bone("body", "root", (0, 40, 0))
    m.bone("bell", "body", (0, 46, 0))
    m.bone("priest", "body", (0, 38, 0))
    m.bone("phead", "priest", (0, 34, 0))
    for k in range(N_TENT):
        a = math.radians(k * 360 / N_TENT + 22.5)
        m.bone(f"tent{k}", "bell", (math.cos(a) * 11, 41, math.sin(a) * 11))
    # ---- 傘: 外の傘 (縁が波打つ)、内の傘、芯、縁の光る点、上のひだ
    rings = []
    for y, r in ((60, 2.0), (59, 7.0), (57, 11.5), (53.5, 15.0), (49, 16.5), (45, 16.0)):
        rings.append([np.array([math.cos(math.radians(a)) * r * (1 + (0.06 * math.sin(math.radians(a * 8)) if y < 50 else 0)), y,
                                math.sin(math.radians(a)) * r * (1 + (0.06 * math.sin(math.radians(a * 8)) if y < 50 else 0))]) for a in range(0, 360, 15)])
    m.add("bell", lof("bell", "jelly", rings), core=True)
    m.add("bell_in", lof("bell", "jelly_d", [[np.array([math.cos(math.radians(a)) * 13.4, 45.4, math.sin(math.radians(a)) * 13.4]) for a in range(0, 360, 20)],
                                             [np.array([math.cos(math.radians(a)) * 8.0, 50.0, math.sin(math.radians(a)) * 8.0]) for a in range(0, 360, 20)]]))
    m.add("core", ell("bell", "glow", (0, 51, 0), (5.0, 3.6, 5.0), segs=12, rings=3))
    for k in range(4):
        a = math.radians(k * 90 + 45)
        m.add(f"gonad{k}", ell("bell", "frond", (math.cos(a) * 5.4, 48.6, math.sin(a) * 5.4), (2.4, 1.6, 2.4), segs=8, rings=2))
    for k in range(16):
        a = math.radians(k * 22.5)
        m.add(f"rimdot{k}", ell("bell", "glow", (math.cos(a) * 16.2, 45.6, math.sin(a) * 16.2), (0.7, 0.7, 0.7), segs=6, rings=2))
    for k in range(8):
        a = math.radians(k * 45)
        m.add(f"ridge{k}", tub("bell", "jelly_d", [(math.cos(a) * 2.5, 59.6, math.sin(a) * 2.5), (math.cos(a) * 11.6, 57.2, math.sin(a) * 11.6),
                                                     (math.cos(a) * 16.4, 49.4, math.sin(a) * 16.4)], [0.5, 0.6, 0.4], None, sides=4))
    # 口腕 (傘の真ん中から垂れる、ひらひらの紫の帯 4 本)
    for k in range(4):
        a = math.radians(k * 90)
        pts = [np.array([math.cos(a) * 2.5, 45, math.sin(a) * 2.5]), np.array([math.cos(a) * 4.5, 36, math.sin(a) * 4.5]),
               np.array([math.cos(a) * 3.0, 26, math.sin(a) * 3.0]), np.array([math.cos(a) * 5.0, 17, math.sin(a) * 5.0])]
        m.add(f"arm{k}", tub("bell", "frond", pts, [1.4, 1.6, 1.2, 0.5], [0.6, 0.7, 0.5, 0.3], sides=5, hint=(math.sin(a), 0, -math.cos(a))))
    # ---- 触手: 縁から床へ垂れる長い管 (先は細く、ところどころ光る)
    for k in range(N_TENT):
        a = math.radians(k * 360 / N_TENT + 22.5)
        c, s_ = math.cos(a), math.sin(a)
        pts = [np.array([c * 13.6, 45.0, s_ * 13.6]), np.array([c * 15.0, 33.0, s_ * 15.0]), np.array([c * 13.0, 20.0, s_ * 13.0]),
               np.array([c * 14.6, 9.0, s_ * 14.6]), np.array([c * 12.6, 1.2, s_ * 12.6])]
        m.add(f"tent{k}", tub(f"tent{k}", "jelly", pts, [1.4, 1.2, 1.0, 0.7, 0.3], None, sides=5))
        for j, y in enumerate((30, 16)):
            t = (45 - y) / 44
            m.add(f"tglow{k}{j}", ell(f"tent{k}", "glow", (c * (14.0 - j), y, s_ * (14.0 - j)), (0.6, 0.9, 0.6), segs=6, rings=2))
    # ---- 巫女: 吊られた潮色の衣、腰の金の帯、祈る手、貝の面と光る目、頭の後ろから傘へ伸びる触手 (吊り紐)
    m.add("robe", lof("priest", "robe", [[np.array([math.cos(math.radians(a)) * r, y, math.sin(math.radians(a)) * r * 0.85]) for a in range(0, 360, 24)]
                                         for y, r in ((14, 5.4), (20, 4.6), (26, 3.8), (31, 3.6))]), core=True)
    m.add("sash", ell("priest", "trim", (0, 27.0, 0), (3.9, 0.8, 3.3), segs=10, rings=2))
    for k in range(5):
        a = math.radians(k * 72 + 10)
        m.add(f"hem{k}", cone("priest", "robe", (math.cos(a) * 5.0, 14.4, math.sin(a) * 4.2), (math.cos(a) * 0.3, -1, math.sin(a) * 0.3), 3.0, 1.0, sides=4))
    for s in (1, -1):
        m.add(f"sleeve{s}", limb("priest", "robe", [(s * 3.4, 30.4, 0), (s * 4.0, 26.6, -1.6), (s * 1.2, 25.2, -3.2)], [2.2, 2.4, 2.0], [2.2, 2.4, 2.0], n=8))
    m.add("hands", ell("priest", "skin", (0, 25.6, -3.8), (1.4, 1.6, 1.0), segs=8, rings=2))
    ph = np.array([0, 34.0, -0.2])
    m.add("pneck", ell("phead", "skin", (0, 31.6, 0), (1.2, 1.2, 1.2), segs=7, rings=2))
    m.add("phood", ell("phead", "robe", ph + np.array([0, 0.4, 0.6]), (2.8, 3.0, 2.8), segs=10, rings=3))
    m.add("pmask", lof("phead", "shell", [oct_ring(ph + np.array([0, -0.2, -2.0]), 3.6, 4.4), oct_ring(ph + np.array([0, -0.4, -2.8]), 3.0, 3.6),
                                          oct_ring(ph + np.array([0, -0.6, -3.2]), 1.8, 2.2)]))
    for k in range(5):
        a = math.radians(-60 + k * 30)
        m.add(f"mrib{k}", tub("phead", "shell", [ph + np.array([0, 1.6, -2.6]), ph + np.array([math.sin(a) * 2.6, 1.6 + math.cos(a) * 2.6, -2.2])], [0.3, 0.2], None, sides=3))
    for s in (1, -1):
        eye(m, "phead", ph + np.array([s * 0.8, 0.2, -3.0]), 0.45, (s * 0.2, 0, -1), (120, 250, 240), glow=True)
    m.add("tether", tub("phead", "frond", [ph + np.array([0, 2.4, 1.0]), ph + np.array([0, 6.0, 1.6]), (0, 45, 0.6)], [0.7, 0.6, 0.8], None, sides=5))

    m.pal = dict(mats={"eye": M_(GLOW, base=0.7, lit=0.2, noise=0.0, min=1), "jelly": M_(JELLY, lit=0.42), "jelly_d": M_(JELLY[:3], lit=0.36),
                       "frond": M_(FROND, lit=0.36), "glow": M_(GLOW, base=0.7, lit=0.2, min=1), "robe": M_(ROBE, lit=0.32, streak=0.05),
                       "shell": M_(SHELL, lit=0.4), "skin": M_(SKIN, lit=0.3), "trim": M_(TRIM, lit=0.4)}, glow_mats=["eye", "glow"])

    def detail(c, col, glow):
        P = c["P"]
        sel = np.isin(c["mats"], ["jelly"]) & c["owned"]
        f1, f2, idv = cell3(P, 2.0, 3)
        vein = sel & ((f2 - f1) < 0.03)
        col = np.where(vein[..., None], np.array([200, 240, 255]), col)
        col = edge_dark(c, col, ["shell", "trim"], k=0.74)
        return col, glow
    m.detail = detail
    m.hint = 512
    m.view = dict(center=(0, 30, 0), scale=2.0)
    m.gaits = gaits()
    return m


def gaits():
    g = []
    idle = Gait("idle", 3.6)
    idle.pos("body", None, W(0).s(1.2, 1, 0, "t"))
    idle.rot("bell", W(0).s(2.0, 1, 0, "t"), None, W(0).s(2.0, 1, 90, "t"))
    for k in range(N_TENT):
        idle.rot(f"tent{k}", W(0).s(6, 1, k * 45, "t"), None, W(0).s(4, 1, k * 45 + 90, "t"))
    idle.rot("priest", W(0).s(3, 1, 180, "t"), W(0).s(6, 1, 0, "t"))
    g.append(idle)
    walk = Gait("walk", 2.4)
    walk.pos("body", None, W(0.5).s(1.6, 1, 0, "t"))
    walk.rot("bell", W(-6).s(3.0, 1, 0, "t"))
    for k in range(N_TENT):
        walk.rot(f"tent{k}", W(10).s(8, 1, k * 45, "t"))
    walk.rot("priest", W(8).s(3, 1, 180, "t"))
    g.append(walk)
    rest = {}
    lift = {"bell": {"rot": (-10, 0, 0)}}
    for k in range(N_TENT):
        a = k * 360 / N_TENT + 22.5
        front = math.sin(math.radians(a)) < 0
        lift[f"tent{k}"] = {"rot": (40 if front else 10, 0, 0)}
    whip = {"bell": {"rot": (12, 0, 0)}}
    for k in range(N_TENT):
        a = k * 360 / N_TENT + 22.5
        front = math.sin(math.radians(a)) < 0
        whip[f"tent{k}"] = {"rot": (-70 if front else -10, 0, 0)}
    g.append(keys("lash", 1.4, [(0.0, rest), (0.7, lift), (0.9, whip), (1.1, whip), (1.4, rest)]))
    swell = {"bell": {"rot": (0, 0, 0)}, "body": {"pos": (0, 4, 0)}, "priest": {"rot": (-14, 0, 0)}}
    drip = {"body": {"pos": (0, 2, 0)}, "bell": {"rot": (6, 0, 0)}}
    g.append(keys("rain", 1.4, [(0.0, rest), (0.6, swell), (1.0, drip), (1.4, rest)]))
    shrink = {"body": {"pos": (0, -2, 0)}}
    for k in range(N_TENT):
        shrink[f"tent{k}"] = {"rot": (0, 0, 0)}
    burst = {"body": {"pos": (0, 3, 0)}}
    for k in range(N_TENT):
        a = math.radians(k * 360 / N_TENT + 22.5)
        burst[f"tent{k}"] = {"rot": (-30 * math.sin(a), 0, 30 * math.cos(a))}
    g.append(keys("pulse", 1.8, [(0.0, rest), (0.8, shrink), (1.0, burst), (1.2, shrink), (1.4, burst), (1.8, rest)]))
    g.append(keys("hurt", 0.3, [(0.0, rest), (0.1, {"bell": {"rot": (-8, 0, 6)}, "priest": {"rot": (-12, 0, 0)}}), (0.3, rest)]))
    sink = {"body": {"pos": (0, -24, 0)}, "bell": {"rot": (20, 0, 10)}, "priest": {"rot": (30, 0, 0)}}
    for k in range(N_TENT):
        sink[f"tent{k}"] = {"rot": (30, 0, 0)}
    g.append(keys("death", 2.4, [(0.0, rest), (0.8, {"body": {"pos": (0, 4, 0)}}), (2.0, sink), (2.4, sink)], hold=True))
    return g


POSES = [("idle", 0.0), ("walk", 0.6), ("lash", 0.7), ("lash", 0.9), ("rain", 0.6), ("pulse", 1.0), ("hurt", 0.1), ("death", 2.4)]
BOUNDS = (7, 7)
