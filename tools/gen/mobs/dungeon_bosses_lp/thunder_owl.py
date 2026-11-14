"""雷の大梟 (thunder_owl)。吹雪の氷穴の中ボス (2026-11-09 のラフ tools/rough/lowpoly/deep_mids で C)。
氷の舞台に降りた大きな梟。濃い藍の羽に白い腹と霜の斑、大きな丸い顔盤に雷色の目、耳の房、広げると大きな翼 (風切り羽の先に雷の光)、
足の爪は雷を帯びて光る。

骨: body の子に head・wing_l / wing_r・leg_l / leg_r・tail。
動き: idle・walk (跳ね歩き)・dive (急降下)・gust (雷の羽)・strike (落雷)・hurt・death
"""
import math

import numpy as np

from blp import *  # noqa: F401,F403
from blp import keys, eye, punch

FEATH = [(24, 28, 50), (40, 46, 74), (62, 70, 104), (90, 100, 136), (124, 134, 170)]
BELLY = [(150, 156, 176), (190, 196, 212), (222, 226, 236), (244, 246, 250)]
FACE = [(170, 176, 196), (206, 210, 224), (232, 234, 242)]
BEAK = [(60, 54, 40), (110, 100, 70), (170, 156, 110)]
TALON = [(20, 20, 26), (40, 40, 50), (64, 64, 78)]
BOLT = [(80, 160, 255), (150, 210, 255), (220, 244, 255), (255, 255, 255)]


def wing_feathers(m, s, side):
    """翼 (畳んだ形。体の横に沿って下へ): 腕の骨・雨覆いの板・重ねた風切り羽 (先が光る)。広げるのは動きで (翼の骨を z で回す)"""
    root = np.array([s * 8.4, 31.0, 1.0])
    elbow = np.array([s * 11.2, 24.0, 3.6])
    hand = np.array([s * 10.6, 15.0, 6.0])
    m.add(f"warm_{side}", tub(f"wing_{side}", "feath", [root, elbow, hand], [2.6, 2.0, 1.4], None, sides=6))
    m.add(f"covert_{side}", slab(f"wing_{side}", "feath", [root + np.array([-s * 0.4, 1.0, -2.0]), root + np.array([s * 0.6, 0, 5.0]),
                                                           hand + np.array([s * 0.4, 0, 3.0]), hand + np.array([-s * 0.4, 0, -2.4])], (1, 0, 0), 1.2))
    for k in range(7):
        t = k / 6
        base = elbow * (1 - t) + hand * t + np.array([s * 0.6, 0, 1.6 - t * 1.0])
        L = 7.0 + t * 4.0
        d = nrm(np.array([s * 0.12, -1.0, 0.55 - t * 0.2]))
        tip = base + d * L
        w = np.array([0, 0, 1.3])
        m.add(f"prim_{side}{k}", slab(f"wing_{side}", "feath", [base - w, base + w, tip + w * 0.4, tip - w * 0.4], (1, 0, 0), 0.6))
        m.add(f"ptip_{side}{k}", ell(f"wing_{side}", "bolt", tip, (0.5, 0.8, 0.7), segs=6, rings=2))


def build():
    m = Mount("thunder_owl", "雷の大梟")
    m.bone("body", "root", (0, 18, 0))
    m.bone("head", "body", (0, 36, -1))
    m.bone("tail", "body", (0, 16, 7))
    for s, side in ((1, "l"), (-1, "r")):
        m.bone(f"wing_{side}", "body", (s * 8.0, 32, 1))
        m.bone(f"leg_{side}", "body", (s * 4.0, 12, -1))
    # ---- 胴: 卵形の胴、白い腹、霜の斑、背の羽の重なり
    m.add("torso", lof("body", "feath", [rsec(z, 34 - abs(z) * 0.4, 9, 9.6 - abs(z) * 0.3, mid=21, n=16, p=2.0) for z in (-7, -4, 0, 4, 7)]), core=True)
    m.add("belly", ell("body", "belly", (0, 20, -5.0), (7.6, 10.4, 4.4), segs=12, rings=4))
    for k in range(12):
        row, col = divmod(k, 4)
        m.add(f"bar{k}", box("body", "feath", ((col - 1.5) * 3.2, 26 - row * 4.6, -9.0 + abs(col - 1.5) * 0.6), (1.6, 0.6, 0.4)))
    for k in range(10):
        a = math.radians(-70 + k * 15.5)
        m.add(f"mantle{k}", cone("body", "feath", (math.sin(a) * 8.6, 33.0, math.cos(a) * 6.0 + 1.0), (math.sin(a) * 0.4, -1, 0.5), 4.4, 1.6, sides=4))
    # ---- 頭: 大きな頭、顔盤、雷色の目、くちばし、耳の房
    hd = np.array([0, 41.0, -1.0])
    m.add("neck", ell("head", "feath", (0, 35.0, -0.5), (6.4, 3.4, 5.6), segs=12, rings=2))
    m.add("skull", ell("head", "feath", hd, (8.2, 6.8, 7.0), segs=14, rings=4), core=True)
    m.add("disc", ell("head", "face", hd + np.array([0, -0.6, -5.4]), (7.0, 5.6, 2.0), segs=14, rings=3))
    m.add("disc_rim", lof("head", "feath", [oct_ring(hd + np.array([0, -0.6, -6.6]), 14.6, 11.8), oct_ring(hd + np.array([0, -0.6, -7.2]), 13.4, 10.8)]))
    for s in (1, -1):
        m.add(f"eyering{s}", ell("head", "feath", hd + np.array([s * 3.0, 0.4, -6.6]), (2.4, 2.4, 0.6), segs=10, rings=2))
        eye(m, "head", hd + np.array([s * 3.0, 0.4, -7.0]), 1.7, (s * 0.15, 0, -1), (150, 220, 255), glow=True)
        m.add(f"ear{s}", cone("head", "feath", hd + np.array([s * 5.0, 5.0, -0.4]), (s * 0.5, 1, 0.3), 5.0, 1.8, sides=4, rings=2, bend=(s * 0.6, 0, 0.6)))
        m.add(f"brow{s}", tub("head", "belly", [hd + np.array([s * 0.8, 2.4, -7.0]), hd + np.array([s * 4.2, 3.4, -6.0])], [0.5, 0.4], None, sides=4))
    m.add("beak", cone("head", "beak", hd + np.array([0, -0.8, -7.4]), (0, -0.6, -1), 3.4, 1.3, sides=5, rings=2, bend=(0, -0.8, 0)))
    # ---- 翼
    for s, side in ((1, "l"), (-1, "r")):
        wing_feathers(m, s, side)
    # ---- 脚: 羽に覆われた太い脚、雷を帯びた爪
    for s, side in ((1, "l"), (-1, "r")):
        m.add(f"thigh_{side}", ell(f"leg_{side}", "belly", (s * 4.0, 10.5, -1.0), (3.2, 4.0, 3.2), segs=10, rings=3))
        m.add(f"shank_{side}", tub(f"leg_{side}", "belly", [(s * 4.0, 8.0, -1.4), (s * 4.0, 2.4, -2.0)], [1.6, 1.3], None, sides=6))
        for k, (dx, dz) in enumerate(((0, -1), (0.8, -0.6), (-0.8, -0.6), (0, 1))):
            base = np.array([s * 4.0, 1.6, -2.2])
            d = nrm(np.array([dx, 0, dz]))
            m.add(f"toe_{side}{k}", tub(f"leg_{side}", "belly", [base, base + d * 2.4 + np.array([0, -0.6, 0])], [0.8, 0.6], None, sides=5))
            m.add(f"claw_{side}{k}", cone(f"leg_{side}", "talon", base + d * 2.4 + np.array([0, -0.6, 0]), d + np.array([0, -0.8, 0]), 1.8, 0.5, sides=4))
        m.add(f"spark_{side}", ell(f"leg_{side}", "bolt", (s * 4.0, 2.0, -3.4), (0.6, 0.6, 0.6), segs=6, rings=2))
    # ---- 尾羽
    for k in range(5):
        a = math.radians(-30 + k * 15)
        base = np.array([math.sin(a) * 2.0, 15.0, 7.0])
        tip = base + np.array([math.sin(a) * 4.0, -6.0, 6.0])
        w = np.array([1.2, 0, 0])
        m.add(f"tailf{k}", slab("tail", "feath", [base - w, base + w, tip + w * 0.6, tip - w * 0.6], (0, 1, 0.6), 0.6))

    m.pal = dict(mats={"eye": M_(BOLT, base=0.7, lit=0.2, noise=0.0, min=1), "feath": M_(FEATH, lit=0.34), "belly": M_(BELLY, lit=0.32),
                       "face": M_(FACE, lit=0.3), "beak": M_(BEAK, lit=0.36), "talon": M_(TALON, lit=0.3), "bolt": M_(BOLT, base=0.7, lit=0.2, min=1)},
                 glow_mats=["eye", "bolt"])

    def detail(c, col, glow):
        col = feather_rows(c, col, ["feath"], row=1.6)
        col = speckle(c, col, ["belly"], (110, 116, 140), density=0.08)
        col = fur(c, col, ["face"], amt=0.1)
        return col, glow
    m.detail = detail
    m.hint = 512
    m.view = dict(center=(0, 24, 0), scale=2.0)
    m.gaits = gaits()
    return m



def _merge(*poses):
    out = {}
    for p in poses:
        for b, ch in p.items():
            out.setdefault(b, {}).update(ch)
    return out


def _shake(base, bones, amp, t0, t1, step=0.07, axis=1):
    out = []
    t, k = t0, 0
    while t <= t1 + 1e-6:
        p = _merge(base)
        for b in bones:
            r = list(p.get(b, {}).get("rot", (0, 0, 0)))
            r[axis] += amp if k % 2 == 0 else -amp
            p.setdefault(b, {})["rot"] = tuple(r)
        out.append((round(t, 3), p))
        t += step
        k += 1
    return out

# 作り込み (2026-11-11 の声「他のボスたちもこの方向性で」): 技の動きに溜めの震えと行き過ぎ (blp.punch)、登場 (intro) と後半の咆哮 (scream) を足した

def gaits():
    g = []
    idle = Gait("idle", 3.2)
    idle.rot("head", W(0).s(4, 1, 0, "t"), W(0).s(18, 1, 0, "t"), W(0).s(6, 1, 90, "t"))
    idle.rot("wing_l", None, None, W(0).s(3, 1, 0, "t"))
    idle.rot("wing_r", None, None, W(0).s(-3, 1, 0, "t"))
    idle.pos("body", None, W(0).s(0.3, 1, 0, "t"))
    g.append(idle)
    walk = Gait("walk", 0.8)
    walk.rot("leg_l", W(0).s(26, 1, 0, "t"))
    walk.rot("leg_r", W(0).s(26, 1, 180, "t"))
    walk.rot("body", W(6), None, W(0).s(5, 1, 0, "t"))
    walk.pos("body", None, W(0.6).s(1.0, 2, 0, "t"))
    walk.rot("wing_l", None, None, W(-10).s(6, 2, 0, "t"))
    walk.rot("wing_r", None, None, W(10).s(-6, 2, 0, "t"))
    g.append(walk)
    rest = {}
    rise = {"body": {"pos": (0, 14, 0), "rot": (-20, 0, 0)}, "wing_l": {"rot": (0, 70, -60)}, "wing_r": {"rot": (0, -70, 60)}, "leg_l": {"rot": (30, 0, 0)},
            "leg_r": {"rot": (30, 0, 0)}}
    flap = dict(rise, wing_l={"rot": (0, 0, 20)}, wing_r={"rot": (0, 0, -20)})
    stoop = {"body": {"pos": (0, 4, 0), "rot": (30, 0, 0)}, "wing_l": {"rot": (0, 30, -30)}, "wing_r": {"rot": (0, -30, 30)}, "leg_l": {"rot": (-60, 0, 0)},
             "leg_r": {"rot": (-60, 0, 0)}, "head": {"rot": (-20, 0, 0)}}
    land = {"body": {"pos": (0, -2, 0), "rot": (10, 0, 0)}, "wing_l": {"rot": (0, 70, -40)}, "wing_r": {"rot": (0, -70, 40)}}
    g.append(keys("dive", 1.8, punch([(0.0, rest), (0.5, rise), (0.7, flap), (0.9, rise), (1.1, stoop), (1.25, land), (1.8, rest)], 5)))
    spread = {"wing_l": {"rot": (0, 70, -70)}, "wing_r": {"rot": (0, -70, 70)}, "body": {"rot": (-10, 0, 0)}, "head": {"rot": (-10, 0, 0)}}
    beat = {"wing_l": {"rot": (0, -40, 10)}, "wing_r": {"rot": (0, 40, -10)}, "body": {"rot": (10, 0, 0)}}
    g.append(keys("gust", 1.4, punch([(0.0, rest), (0.7, spread), (0.9, beat), (1.05, spread), (1.15, beat), (1.4, rest)], 2)))
    call = {"head": {"rot": (-30, 0, 0)}, "wing_l": {"rot": (0, 70, -50)}, "wing_r": {"rot": (0, -70, 50)}, "body": {"rot": (-14, 0, 0), "pos": (0, 2, 0)}}
    g.append(keys("strike", 1.6, punch([(0.0, rest), (0.8, call), (1.2, call), (1.6, rest)], 1)))
    # 登場 (3.8 秒): 天井の闇から翼を広げて舞い降り、羽ばたいて着地し、翼を大きく広げて叫ぶ (名前の札 2.30 秒 = introCardAt 46)
    cry = _merge(spread, call)
    g.append(keys("intro", 3.8, [(0.0, _merge(rise, {"root": {"pos": (0, 70, 0)}})), (0.6, _merge(flap, {"root": {"pos": (0, 48, 0)}})),
                                 (1.0, _merge(rise, {"root": {"pos": (0, 30, 0)}})), (1.4, _merge(flap, {"root": {"pos": (0, 12, 0)}})), (1.8, land),
                                 (2.3, cry)] + _shake(cry, ["head"], 5, 2.36, 3.0, axis=2) + [(3.3, spread), (3.8, rest)]))
    g.append(keys("scream", 1.6, [(0.0, rest), (0.3, cry)] + _shake(cry, ["head"], 5, 0.36, 1.04, axis=2) + [(1.2, beat), (1.6, rest)]))
    g.append(keys("hurt", 0.3, [(0.0, rest), (0.1, {"head": {"rot": (-14, 0, 8)}, "body": {"rot": (-6, 0, 0)}}), (0.3, rest)]))
    fall = {"body": {"pos": (0, -10, -4), "rot": (70, 0, 0)}, "wing_l": {"rot": (0, 70, -50)}, "wing_r": {"rot": (0, -70, 50)}, "head": {"rot": (-20, 0, 0)},
            "leg_l": {"rot": (40, 0, 0)}, "leg_r": {"rot": (40, 0, 0)}}
    g.append(keys("death", 2.0, [(0.0, rest), (0.6, {"wing_l": {"rot": (0, 70, -60)}, "wing_r": {"rot": (0, -70, 60)}}), (1.4, fall), (2.0, fall)], hold=True))
    return g


POSES = [("idle", 0.0), ("walk", 0.2), ("dive", 0.7), ("dive", 1.1), ("gust", 0.7), ("strike", 0.8), ("hurt", 0.1), ("death", 2.0), ("intro", 1.0), ("intro", 2.3), ("scream", 0.5)]
BOUNDS = (7, 6)
