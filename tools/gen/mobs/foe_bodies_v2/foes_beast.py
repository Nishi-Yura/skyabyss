# -*- coding: utf-8 -*-
"""人でない形の敵 (案 A 作り込み): 帯電毛玉・雷角の大羊・灰の渦・月の欠片の盾・潮だまり貝。前 = -Z。"""
from __future__ import annotations

from fb import *  # noqa: F401,F403
from foes_human import taper_limb


def fib(n, seed=0.0, lo=-0.75):
    out = []
    g = math.pi * (3 - math.sqrt(5))
    for i in range(n):
        y = 1 - (i + 0.5) / n * (1 - lo)
        r = math.sqrt(max(0, 1 - y * y))
        a = g * i + seed
        out.append(np.array([math.cos(a) * r, y, math.sin(a) * r]))
    return out


# ================================================================ 帯電毛玉 (static_puff, 静電の羊毛)
TUFTS = {"tuft_n": (0, 0, -1), "tuft_s": (0, 0, 1), "tuft_e": (1, 0, 0), "tuft_w": (-1, 0, 0)}


def tuft_of(d):
    d = nrm(np.asarray(d, float))
    if d[1] > 0.62:
        return "tuft_t"
    return max(TUFTS, key=lambda b: float(np.dot(d, np.asarray(TUFTS[b], float))))


def static_puff():
    m = start("static_puff", "帯電毛玉")
    C0 = P(0, 8.0, 0)
    m.add("core", ell("body", "wool", tuple(C0), (4.0, 3.6, 4.0), segs=14, rings=5), core=True)
    rng = np.random.default_rng(3)
    for i, d in enumerate(fib(12, 1.1, -0.6)):
        if d[2] < -0.75 and abs(d[1]) < 0.4:
            continue                       # 顔の前はあける
        b = tuft_of(d)
        c = C0 + d * 3.5
        r = 1.6 + (i % 2) * 0.3
        side = nrm(np.cross(d, P(0, 1, 0.01)))
        up = np.cross(side, d)
        # 房は小さな毛の玉の寄せ集め (ふわっとした輪郭)、先に巻いた毛
        for j, (u, v, k) in enumerate(((0, 0, 1.0), (0.7, 0.4, 0.72), (-0.7, 0.3, 0.7), (0.1, -0.7, 0.68))):
            cc = c + side * u * r * 0.7 + up * v * r * 0.7 + d * (0.2 if j else 0.4)
            m.add(f"lump{i}_{j}", ell(b, "wool" if (i + j) % 3 else "down", tuple(cc), (r * k, r * k * 0.9, r * k), segs=10, rings=4))
        m.add(f"curl{i}", cone(b, "down", tuple(c + d * r * 0.9), tuple(d + up * 0.3), 1.2, 0.5, sides=6, rings=2, bend=tuple(side * 0.6)))
        if i % 3 == 1:
            m.add(f"spk{i}", cone(b, "spark", tuple(c + d * (r * 0.6)), tuple(d), 1.6, 0.32, sides=4, rings=2, bend=tuple(side * 0.4)))
            m.add(f"spkb{i}", ell(b, "spark", tuple(c + d * (r * 0.6 + 1.7)), (0.25, 0.25, 0.25), segs=6, rings=2))
    # 顔: 前の毛の段、黒い目 (光の点)、小さな口
    m.add("fringe", ell("tuft_n", "down", (0, 10.0, -3.2), (2.6, 1.2, 1.3), segs=12, rings=3))
    for s in (-1, 1):
        m.add(f"eye_rim{s}", ell("body", "wool", (s * 1.3, 8.6, -3.7), (0.8, 0.85, 0.4), segs=8, rings=2))
        eye(m, "body", (s * 1.3, 8.6, -3.95), 0.6, (s * 0.15, 0, -1), (20, 20, 30), glow=False)
        m.add(f"cheek{s}", ell("body", "blush", (s * 2.2, 7.4, -3.5), (0.5, 0.3, 0.2), segs=6, rings=1))
    m.add("mouth", ell("body", "dark", (0, 7.2, -3.95), (0.4, 0.18, 0.15), segs=6, rings=1))
    # 下の銅の種: 筋と根
    m.add("seed", ell("body", "seed", (0, 4.4, 0), (1.1, 0.95, 1.1), segs=10, rings=3))
    for k in range(4):
        a = k / 4 * math.tau
        m.add(f"seed_rib{k}", tub("body", "seedd", [P(math.cos(a) * 0.4, 5.2, math.sin(a) * 0.4), P(math.cos(a) * 1.1, 4.4, math.sin(a) * 1.1),
                                                    P(math.cos(a) * 0.4, 3.6, math.sin(a) * 0.4)], [0.12, 0.2, 0.12], None, sides=4))
    m.add("seed_tail", cone("body", "seedd", P(0, 3.6, 0), (0.2, -1, 0.1), 1.4, 0.3, sides=5, rings=2, bend=(0.4, 0, 0)))
    m.pal = mats(wool=M_(DOWN, lit=0.32), down=M_([(196, 202, 218), (220, 226, 238), (238, 242, 250), (250, 252, 255)], lit=0.28), dark=M_(HOLLOW, min=0),
                 seed=M_(COPPER, lit=0.42), seedd=M_([(70, 38, 22), (104, 60, 34), (140, 86, 48), (176, 116, 70)]), blush=M_([(170, 140, 170), (196, 166, 196), (216, 190, 216), (232, 212, 232)]),
                 spark=M_(BOLT, base=0.8, lit=0.1, min=1), glow=["spark"])
    m.detail = None
    return m


# ================================================================ 雷角の大羊 (storm_ram)
def storm_ram():
    m = start("storm_ram", "雷角の大羊")
    # 胴: 厚い毛の塊 (丸めた胴の上に、房を段に重ねる)
    m.add("torso", ell("body", "wool", (0, 21.0, 0.0), (7.4, 7.6, 12.0), segs=16, rings=7), core=True)
    rng = np.random.default_rng(11)
    for i in range(30):
        a = (i * 137.5 + 11) * math.pi / 180
        bb = ((i * 61 + 33) % 180 - 90) * math.pi / 180 * 0.75
        x, y, z = 6.9 * math.cos(bb) * math.cos(a), 20.0 + 7.0 * math.sin(bb), 11.2 * math.cos(bb) * math.sin(a)
        r = 2.6 * (0.8 + 0.4 * ((i * 7) % 5) / 4)
        m.add(f"tuft{i}", ell("body", "wool" if i % 4 else "woold", (x, y, z), (r, r * 1.25, r), segs=9, rings=3))
    # 房の垂れ (脇腹と胸の毛先。先が霜で白い)
    for sx in (1, -1):
        for i, z in enumerate((-8.4, -4.6, -0.8, 3.0, 6.8)):
            top = P(sx * 6.4, 16.6, z)
            m.add(f"lock{sx}{i}", tub("body", "wool", [top, top + P(sx * 0.6, -3.2, 0.2), top + P(sx * 0.4, -5.6, 0.6)], [1.7, 1.3, 0.5], None, sides=8))
            m.add(f"frost{sx}{i}", cone("body", "frost", tuple(top + P(sx * 0.4, -5.2, 0.6)), (0, -1, 0.1), 1.0, 0.5, sides=6, rings=1))
    for i, (x, z) in enumerate(((-2.4, -11.6), (0, -12.2), (2.4, -11.6))):
        m.add(f"chest_lock{i}", tub("body", "wool", [P(x, 18.0, z), P(x * 1.1, 14.0, z - 0.2), P(x * 1.1, 11.6, z + 0.4)], [1.6, 1.2, 0.4], None, sides=8))
    m.add("scorch", tub("body", "scorch", [P(0, 28.6, -6.0), P(1.2, 28.9, 0.0), P(-0.8, 28.3, 6.0), P(0.4, 26.9, 10.0)], [0.9, 0.9, 0.8, 0.5], [0.4] * 4, sides=6, hint=(1, 0, 0)))
    for k, (z, dx) in enumerate(((-3.0, 0.8), (3.0, -0.8))):
        m.add(f"scorch_b{k}", tub("body", "scorch", [P(0.4, 28.8, z), P(dx * 2.6, 28.0, z + 1.2)], [0.5, 0.2], [0.3, 0.1], sides=6, hint=(1, 0, 0)))
    # 首と頭: 黒い顔 (鼻筋・鼻の穴・目の上の骨)、顎ひげ、耳
    m.add("neck", tub("head", "wool", [P(0, 23.0, -8.0), P(0, 24.8, -9.6), P(0, 26.0, -11.0)], [4.6, 4.2, 3.6], None, sides=12))
    m.add("ruff", ell("head", "wool", (0, 24.2, -10.6), (4.4, 3.4, 2.8), segs=12, rings=4))
    m.add("skull", ell("head", "face", (0, 27.6, -13.6), (3.2, 3.8, 4.4), segs=14, rings=5))
    m.add("muzzle", tub("head", "face", [P(0, 26.6, -16.2), P(0, 25.6, -18.2), P(0, 24.6, -19.8)], [2.4, 2.1, 1.7], [2.1, 1.8, 1.5], sides=10, hint=(1, 0, 0)))
    m.add("nose", ell("head", "faced", (0, 25.0, -20.3), (1.5, 1.2, 0.8), segs=10, rings=3))
    for s in (-1, 1):
        m.add(f"nostril{s}", ell("head", "hollow", (s * 0.6, 25.1, -20.95), (0.32, 0.4, 0.2), segs=6, rings=1))
        m.add(f"brow{s}", ell("head", "faced", (s * 2.1, 29.4, -15.6), (1.2, 0.6, 1.0), segs=8, rings=2))
        m.add(f"socket{s}", ell("head", "faced", (s * 2.5, 28.3, -15.8), (0.8, 0.65, 0.7), segs=8, rings=2))
        m.add(f"eye{s}", ell("head", "glow_pale", (s * 2.75, 28.35, -16.0), (0.45, 0.35, 0.45), segs=8, rings=2))
        m.add(f"ear{s}", ell("head", "face", (s * 3.9, 28.0, -12.2), (1.6, 0.55, 0.9), segs=8, rings=2, rz=s * 20))
    m.add("beard", tub("head", "wool", [P(0, 23.4, -17.0), P(0, 21.0, -16.9), P(0, 19.0, -16.2), P(0, 17.2, -15.0)], [1.7, 1.4, 0.9, 0.2], [1.1, 0.9, 0.6, 0.2], sides=8, hint=(1, 0, 0)))
    for s in (-1, 1):
        m.add(f"beard_side{s}", tub("head", "wool", [P(s * 1.0, 23.6, -16.4), P(s * 1.1, 20.6, -16.0), P(s * 0.8, 18.6, -15.4)], [1.0, 0.7, 0.2], None, sides=6))
    # 巻いた角: 輪の畝 (年輪) と外側の稲妻の筋
    for sx in (1, -1):
        pts, ra = [], []
        n = 26
        for i in range(n):
            t = i / (n - 1)
            a = t * 1.45 * 2 * math.pi
            r = 7.0 * (1 - 0.5 * t)
            pts.append(P(sx * (3.2 + 5.6 * t), 25.0 + r * math.cos(a), -8.0 + r * math.sin(a)))
            ra.append(3.0 * (1 - 0.8 * t) + 0.3)
        m.add(f"horn{sx}", tub("head", "horn", pts, ra, None, sides=10, hint=(1, 0, 0)))
        for i in range(2, n - 3, 3):
            d = nrm(pts[i + 1] - pts[i - 1])
            m.add(f"ridge{sx}{i}", tub("head", "hornd", [pts[i] - d * 0.18, pts[i] + d * 0.18], [ra[i] * 1.07, ra[i] * 1.07], None, sides=10, hint=(1, 0, 0)))
        vein = [p + P(sx * r_ * 0.86, 0, 0) for p, r_ in zip(pts[2:22], ra[2:22])]
        zig = [v + P(0, 0.45 * (1 if k % 2 else -1), 0) for k, v in enumerate(vein)]
        m.add(f"vein{sx}", tub("head", "glow_blue", zig, [0.42] * len(zig), None, sides=5, hint=(0, 1, 0)))
    # 脚: 膝まで毛、下は黒く細い。蹴爪の毛、割れた黒い蹄
    for sx, fb, hb in ((1, "fore_l", "hind_l"), (-1, "fore_r", "hind_r")):
        taper_limb(m, f"foreup{sx}", fb, "wool", [(sx * 4.0, 17.0, -8.0), (sx * 4.1, 13.0, -8.2), (sx * 4.2, 9.0, -8.4)], [3.0, 2.7, 2.2])
        taper_limb(m, f"forelo{sx}", fb, "face", [(sx * 4.2, 9.4, -8.4), (sx * 4.2, 5.4, -8.3), (sx * 4.2, 2.2, -8.0)], [1.25, 1.05, 1.0])
        m.add(f"forefet{sx}", tub(fb, "woold", [P(sx * 4.2, 3.6, -8.0), P(sx * 4.2, 2.4, -7.9)], [1.3, 1.5], None, sides=10))
        thigh_c = P(sx * 4.2, 15.6, 9.6)
        m.add(f"thigh{sx}", ell(hb, "wool", tuple(thigh_c), (3.0, 5.0, 3.6), segs=12, rings=5))
        taper_limb(m, f"hock{sx}", hb, "face", [(sx * 4.2, 11.4, 10.6), (sx * 4.2, 8.6, 11.4), (sx * 4.2, 5.4, 10.8), (sx * 4.2, 2.2, 10.2)], [1.4, 1.2, 1.05, 1.0])
        m.add(f"hindfet{sx}", tub(hb, "woold", [P(sx * 4.2, 3.6, 10.3), P(sx * 4.2, 2.4, 10.2)], [1.3, 1.5], None, sides=10))
        for b, z in ((fb, -8.2), (hb, 10.0)):
            for k in (-1, 1):
                m.add(f"hoof{b}{k}", ell(b, "hoof", (sx * 4.2 + k * 0.55, 0.85, z - 0.4), (0.62, 0.85, 1.25), segs=8, rings=3))
            m.add(f"dew{b}", cone(b, "hoof", P(sx * 4.2, 2.0, z + 1.0), (0, -0.6, 1), 0.6, 0.3, sides=4, rings=1))
    m.add("tail", ell("tail", "wool", (0, 22.0, 13.4), (1.8, 2.4, 1.6), segs=10, rings=4))
    m.add("tail_tip", cone("tail", "woold", P(0, 20.4, 13.9), (0, -1, 0.3), 1.4, 0.8, sides=6, rings=2))
    m.pal = mats(wool=M_(WOOL, lit=0.3), woold=M_([(80, 84, 92), (112, 116, 124), (146, 150, 158), (176, 180, 188)]), frost=M_([(200, 214, 230), (222, 232, 244), (240, 246, 252), (252, 254, 255)], lit=0.4),
                 face=M_(FACE, lit=0.36), faced=M_([(8, 8, 10), (18, 18, 22), (30, 30, 36), (44, 44, 52)]), horn=M_(HORN, lit=0.4), hornd=M_([(12, 14, 18), (24, 26, 32), (38, 40, 48), (54, 56, 66)]),
                 scorch=M_([(10, 10, 14), (24, 24, 30), (40, 40, 48)], min=0), hoof=M_([(8, 8, 10), (20, 20, 24), (34, 34, 40)], lit=0.4, min=0), hollow=M_(HOLLOW, min=0),
                 glow_pale=M_([(200, 230, 255), (226, 242, 255), (244, 250, 255), (255, 255, 255)], base=0.8, lit=0.1, min=1),
                 glow_blue=M_([(90, 170, 255), (140, 205, 255), (200, 235, 255), (240, 250, 255)], base=0.8, lit=0.1, min=1), glow=["glow_pale", "glow_blue"])
    m.detail = lambda c, col, glow: (fur(c, col, ["wool", "woold"], amt=0.1), glow)
    return m


# ================================================================ 灰の渦 (ash_whirl, 灰の壺の精)
def ribbon(m, pid, bone, mat, y, r, h, n=8, tw=28.0, w=0.7, th=0.22, r_top=1.15):
    """竜巻の一段: ねじれた灰の帯を輪に並べる (薄く平たい帯)"""
    for i in range(n):
        pts = []
        for k in range(5):
            t = k / 4
            a = math.radians(i * 360 / n + tw * t)
            rr = r * (1 + (r_top - 1) * t)
            pts.append(P(rr * math.cos(a), y - h / 2 + h * t, rr * math.sin(a)))
        wid = [w * (0.6 + 0.6 * math.sin(math.pi * k / 4)) for k in range(5)]
        m.add(f"{pid}{i}", tub(bone, mat, pts, wid, [th] * 5, sides=6, hint=(0, 1, 0)))


def ash_whirl():
    m = start("ash_whirl", "灰の渦")
    # 壺 (ring0): 胴・肩・首・口縁・取っ手・帯・割れ目の熾
    m.add("pot", lof("ring0", "clay", [fsec(1.8, 2.0, 2.0), fsec(2.6, 3.4, 3.4), fsec(4.4, 4.2, 4.2), fsec(6.4, 4.3, 4.3), fsec(8.4, 3.6, 3.6),
                                       fsec(9.6, 2.4, 2.4), fsec(10.4, 2.2, 2.2), fsec(11.0, 2.7, 2.7), fsec(11.4, 2.9, 2.9), fsec(11.0, 2.4, 2.4)]))
    m.add("foot", lof("ring0", "claYd" if False else "clayd", [fsec(1.6, 1.8, 1.8), fsec(2.2, 2.3, 2.3)]))
    for s in (1, -1):
        m.add(f"handle{s}", tub("ring0", "clay", [P(s * 3.4, 9.0, 0), P(s * 4.9, 8.6, 0), P(s * 5.0, 7.0, 0), P(s * 4.2, 5.8, 0)], [0.45] * 4, [0.3] * 4, sides=6, hint=(0, 0, 1)))
    for k, y in enumerate((4.0, 7.6)):
        r = 4.25 if k == 0 else 4.0
        m.add(f"band{k}", lof("ring0", "soot", [fsec(y - 0.2, r + 0.05, r + 0.05), fsec(y + 0.2, r + 0.03, r + 0.03)]))
    for k in range(10):
        a = k / 10 * math.tau
        m.add(f"zig{k}", ell("ring0", "soot", (math.cos(a) * 4.3, 5.8, math.sin(a) * 4.3), (0.45, 0.8, 0.45), segs=6, rings=2))
    for k, (x, y, z, h) in enumerate(((2.6, 6.4, -3.3, 2.6), (-3.1, 5.2, -2.6, 2.0), (0.4, 3.8, 3.9, 1.6))):
        out = nrm(P(x, 0, z))
        pts = [P(x, y + h / 2, z) + out * 0.1, P(x + 0.3, y, z) + out * 0.25, P(x - 0.2, y - h / 2, z) + out * 0.1]
        m.add(f"crack{k}", tub("ring0", "ember", pts, [0.2, 0.32, 0.18], None, sides=5))
    m.add("chip", ell("ring0", "hollow", (-1.4, 11.1, -2.4), (0.8, 0.5, 0.5), segs=6, rings=2))
    # 渦 (spin): 灰の漏斗、熾の芯、灰の帯 (ring1・ring2)、灰の粒
    m.add("funnel", lof("spin", "soot", [fsec(10.6, 1.6, 1.6), fsec(13.0, 2.1, 2.1, folds=5, amp=0.1), fsec(16.0, 2.8, 2.8, folds=5, amp=0.12, ph=0.8),
                                         fsec(19.0, 3.6, 3.6, folds=5, amp=0.12, ph=1.6), fsec(22.0, 4.4, 4.4, folds=5, amp=0.1, ph=2.4), fsec(22.4, 3.0, 3.0)]))
    ribbon(m, "r1_", "ring1", "ash", 14.0, 3.0, 4.0, n=8)
    ribbon(m, "r2_", "ring2", "ash", 20.0, 4.4, 4.0, n=10, tw=34)
    ribbon(m, "r2b_", "ring2", "ashl", 18.6, 4.9, 2.4, n=6, tw=-24, w=0.45)
    m.add("core", ell("core", "ember", (0, 11.6, 0), (1.6, 1.0, 1.6), segs=10, rings=3))
    m.add("core_hot", ell("core", "emberh", (0, 11.9, 0), (0.9, 0.6, 0.9), segs=8, rings=2))
    for i in range(14):
        a = math.radians(i * 37)
        m.add(f"mote{i}", ell("ring2", "ember" if i % 4 == 0 else "ashl", (5.8 * math.cos(a), 15.6 + (i % 5) * 1.8, 5.8 * math.sin(a)), (0.35, 0.35, 0.35), segs=6, rings=2))
    # 面 (mask): 素焼きの面、目の穴の熾、割れ目、額の印
    m.add("mask", ell("mask", "clay", (0, 24.0, -2.4), (2.0, 2.3, 1.0), segs=12, rings=4))
    m.add("mask_rim", tub("mask", "clayd", [P(math.sin(a) * 2.0, 24.0 + math.cos(a) * 2.3, -2.2) for a in np.linspace(0, math.tau, 17)], [0.22] * 17, None, sides=5, hint=(0, 0, 1)))
    for s in (-1, 1):
        m.add(f"mask_eye{s}", ell("mask", "hollow", (s * 0.8, 24.6, -3.25), (0.55, 0.32, 0.2), segs=8, rings=2))
        m.add(f"mask_glow{s}", ell("mask", "ember", (s * 0.8, 24.6, -3.2), (0.3, 0.18, 0.15), segs=6, rings=1))
    m.add("mask_mouth", ell("mask", "hollow", (0, 22.9, -3.25), (0.6, 0.18, 0.15), segs=6, rings=1))
    m.add("mask_crack", tub("mask", "ember", [P(0.6, 26.0, -3.2), P(0.9, 25.0, -3.35), P(0.5, 24.0, -3.3)], [0.12, 0.18, 0.1], None, sides=4))
    m.add("mask_mark", ell("mask", "soot", (0, 25.6, -3.25), (0.35, 0.35, 0.12), segs=6, rings=1))
    m.pal = mats(clay=M_(CLAY, lit=0.32), clayd=M_([(60, 36, 26), (90, 58, 40), (120, 84, 60), (150, 110, 82)]), soot=M_(CHAR, lit=0.3), ash=M_(ASH, streak=0.1, lit=0.3),
                 ashl=M_([(120, 116, 110), (150, 146, 140), (180, 176, 168), (206, 202, 194)]), ember=M_(EMBER, base=0.75, lit=0.15, min=1),
                 emberh=M_([(255, 200, 120), (255, 226, 170), (255, 244, 214), (255, 252, 240)], base=0.8, lit=0.1, min=1), hollow=M_(HOLLOW, min=0), glow=["ember", "emberh"])
    m.detail = None
    return m


# ================================================================ 月の欠片の盾 (core_shard)
def core_shard():
    m = start("core_shard", "月の欠片の盾")
    CY = 24.0

    def frame(t):
        a = math.radians(-75 + t * 150)
        return CY + math.sin(a) * 20.0, -math.cos(a) * 9.0 + 4.0, 1.0 + 4.2 * math.sin(t * math.pi), 1.5 + 7.0 * math.sin(t * math.pi)
    rings = []
    n = 25
    rng = np.random.default_rng(5)
    for i in range(n):
        t = i / (n - 1)
        y, zc, th, w = frame(t)
        ring_ = []
        for j in range(14):
            b = -math.pi * 0.75 + j / 14 * math.tau
            jag = 1.0 + (0.06 * math.sin(i * 1.7 + j * 2.3) if 0 < i < n - 1 else 0)
            ring_.append(P(math.cos(b) * w * jag, y, zc + math.sin(b) * th * (0.3 if math.sin(b) < 0 else 1.0) * (jag if math.sin(b) > 0 else 1.0)))
        rings.append(ring_)
    m.add("rock", lof("shard", "dust", rings))
    # 外の面: 玄武岩の瘤と、クレーター (縁と暗い底)
    for i in range(12):
        t = 0.1 + 0.8 * rng.random()
        y, zc, th, w = frame(t)
        x = rng.uniform(-0.6, 0.6) * w
        z = zc + th * math.sqrt(max(0.0, 1 - (x / w) ** 2)) - 0.3
        if i < 7:
            r = rng.uniform(1.2, 2.2)
            m.add(f"knob{i}", ell("shard", "basalt", (x, y, z), (r, r * 0.9, r * 0.7), segs=10, rings=4))
        else:
            r = rng.uniform(0.9, 1.6)
            nrmv = nrm(P(x / w * 0.6, 0, 1))
            pts = [P(x + math.cos(a) * r, y + math.sin(a) * r, z + 0.15) for a in np.linspace(0, math.tau, 13)]
            m.add(f"crater{i}", tub("shard", "dustl", pts, [0.28] * 13, None, sides=5, hint=(0, 0, 1)))
            m.add(f"crater_in{i}", ell("shard", "basaltd", (x, y, z - 0.05), (r * 0.85, r * 0.85, 0.3), segs=10, rings=2))
    # 内の面: 銀の鏡と縁の枠、星の刻み (四つの光の先)
    mrings = []
    stars = []
    for i in range(n):
        t = i / (n - 1)
        a = math.radians(-72 + t * 144)
        y = CY + math.sin(a) * 19.4
        th = 1.0 + 4.2 * math.sin(t * math.pi)
        w = (1.5 + 7.0 * math.sin(t * math.pi)) * 0.78
        z0 = -math.cos(a) * 9.0 + 4.0 - th * 0.7 * 0.55
        mrings.append([P(-w, y, z0 - 0.5), P(w, y, z0 - 0.5), P(w, y, z0 + 0.6), P(-w, y, z0 + 0.6)])
        if i % 4 == 2:
            stars.append(P(0.0, y, z0 - 0.62))
    m.add("mirror", lof("shard", "silver", mrings))
    for s in (-1, 1):
        edge = [P(s * r_[0][0] * -1, r_[0][1], r_[0][2] + 0.05) for r_ in mrings]
        m.add(f"frame{s}", tub("shard", "silverd", edge, [0.35] * len(edge), None, sides=6))
    for j, p in enumerate(stars):
        m.add(f"star{j}", ell("shard", "star", tuple(p), (0.55, 0.55, 0.35), segs=8, rings=3))
        for k in range(4):
            a = k / 4 * math.tau
            m.add(f"ray{j}{k}", cone("shard", "star", tuple(p), (math.cos(a), math.sin(a), 0), 1.6 if k % 2 else 1.1, 0.28, sides=4, rings=1, rb=0.1))
    m.pal = mats(dust=M_(DUST, lit=0.34), dustl=M_([(140, 136, 130), (170, 166, 158), (198, 194, 186), (222, 218, 210)], lit=0.36), basalt=M_(BASALT, lit=0.36),
                 basaltd=M_([(20, 20, 26), (32, 32, 40), (46, 46, 56), (62, 62, 74)]), silver=M_(SILVER, lit=0.5), silverd=M_([(90, 96, 108), (120, 128, 140), (150, 158, 170), (180, 188, 198)], lit=0.45),
                 star=M_(STAR, base=0.86, lit=0.08, min=1), glow=["star"])
    m.detail = lambda c, col, glow: (speckle(c, col, ["dust"], (110, 106, 100), density=0.05, size=0.7), glow)

    def stages(atlas, glow):
        return {"_s1": stage_tex(atlas, glow, (200, 225, 255), 0.84, 0.05, 5), "_s2": stage_tex(atlas, glow, (170, 205, 255), 0.7, 0.1, 6)}
    m.stages = stages
    return m


# ================================================================ 潮だまり貝 (tidepool_clam, 縁飾りの帆立)
def tidepool_clam():
    m = start("tidepool_clam", "潮だまり貝")
    W, Fz, B = 7.4, 7.6, 5.2
    N = 28

    def rim_wave(t):
        return 0.18 * math.cos(t * 9)
    # 下の殻 (body): 浅い鉢、縁は波打つ
    lo = [fsec(0.4, W * 0.42, Fz * 0.42, B * 0.42, n=N), fsec(1.9, W * 0.86, Fz * 0.86, B * 0.86, n=N, folds=9, amp=0.03),
          fsec(3.3, W, Fz, B, n=N, folds=9, amp=0.04, dy=rim_wave)]
    m.add("lower", lof("body", "shell", lo, caps=("shell", "nacre")))
    # 上の殻 (lid): 放射の畝 (ひだ) が縁で深くなる扇の殻
    up = [fsec(3.7, W, Fz, B, n=N, folds=9, amp=0.05, dy=lambda t: -rim_wave(t) * 0.0 + rim_wave(t)), fsec(4.6, W * 0.95, Fz * 0.95, B * 0.95, n=N, folds=9, amp=0.06),
          fsec(5.6, W * 0.82, Fz * 0.8, B * 0.84, n=N, folds=9, amp=0.06), fsec(6.6, W * 0.6, Fz * 0.55, B * 0.62, n=N, folds=9, amp=0.05),
          fsec(7.2, W * 0.34, Fz * 0.28, B * 0.36, n=N, folds=9, amp=0.03), fsec(7.6, W * 0.12, Fz * 0.08, B * 0.14, n=N)]
    m.add("upper", lof("lid", "shell", up, caps=("nacre", "shell")))
    # 成長の線 (同心の細い段)
    for k, (y, f) in enumerate(((4.3, 0.98), (5.1, 0.89), (6.0, 0.72))):
        m.add(f"growth{k}", lof("lid", "rib", [fsec(y - 0.08, W * f + 0.08, Fz * f + 0.08, B * f + 0.08, n=N, folds=9, amp=0.06),
                                               fsec(y + 0.08, W * f + 0.02, Fz * f + 0.02, B * f + 0.02, n=N, folds=9, amp=0.06)]))
    # 放射の筋 (上の殻の表面に沈めた丸い畝)
    for i in range(9):
        a = math.radians(-72 + 144 * i / 8)
        sa, ca = math.sin(a), math.cos(a)
        pts = [P(sa * W * 0.14, 7.4, -ca * Fz * 0.06 + 0.3), P(sa * W * 0.5, 6.7, -ca * Fz * 0.48), P(sa * W * 0.8, 5.5, -ca * Fz * 0.8), P(sa * W * 0.97, 4.0, -ca * Fz * 0.98)]
        m.add(f"rib{i}", tub("lid", "rib", pts, [0.25, 0.42, 0.5, 0.42], None, sides=6))
    # 蝶番の耳 (帆立の三角の耳) と蝶番のこぶ
    for s in (-1, 1):
        m.add(f"ear{s}", lof("lid", "rib", [[P(s * 0.8, 3.75, 4.4), P(s * 0.8, 3.75, 5.9), P(s * 0.8, 4.9, 5.4), P(s * 0.8, 4.9, 4.4)],
                                             [P(s * 4.0, 3.75, 4.0), P(s * 4.0, 3.75, 5.6), P(s * 3.8, 4.3, 5.3), P(s * 3.8, 4.3, 4.2)]]))
    m.add("hinge", ell("body", "shell", (0, 3.5, 5.4), (2.0, 1.0, 0.9), segs=10, rings=2))
    # 外套の縁の青い目 (下の殻の前の縁)、外套の舌、真珠
    for i in range(9):
        a = math.radians(-70 + 140 * i / 8)
        m.add(f"eye{i}", ell("body", "eye", (math.sin(a) * 6.5, 3.4, -math.cos(a) * 6.8 + 0.2), (0.45, 0.38, 0.45), segs=8, rings=2))
    m.add("mantle", lof("body", "flesh", [fsec(3.1, W * 0.92, Fz * 0.92, B * 0.9, n=N, folds=14, amp=0.03), fsec(3.45, W * 0.9, Fz * 0.9, B * 0.88, n=N, folds=14, amp=0.03)]))
    m.add("tongue", ell("body", "flesh", (0, 3.2, -0.8), (4.6, 0.32, 4.4), segs=12, rings=2))
    m.add("pearl", ell("pearl", "pearl", (0, 3.4, -6.9), (1.35, 1.35, 1.35), segs=12, rings=5))
    m.add("pearl_shine", ell("pearl", "pearlh", (-0.4, 3.9, -8.05), (0.35, 0.3, 0.2), segs=6, rings=1))
    # 小さな藤壺と海藻 (殻に付く)
    for k, (x, z) in enumerate(((-3.0, -1.0), (2.6, 1.4))):
        m.add(f"barn{k}", cone("lid", "barn", P(x, 6.0, z), (x * 0.05, 1, z * 0.05), 0.8, 0.7, sides=6, rings=1))
    m.add("weed", tub("lid", "kelp", [P(3.4, 5.4, 2.6), P(5.0, 5.0, 4.2), P(6.2, 3.8, 5.6)], [0.4, 0.3, 0.12], [0.15] * 3, sides=4, hint=(0, 1, 0)))
    m.pal = mats(shell=M_(SHELL, lit=0.34), rib=M_([(150, 70, 70), (200, 104, 96), (230, 146, 128), (246, 186, 166)], lit=0.36), nacre=M_(NACRE, lit=0.4),
                 flesh=M_([(70, 30, 44), (120, 56, 70), (170, 92, 98), (214, 136, 128)]), pearl=M_([(200, 200, 210), (226, 226, 234), (242, 240, 246), (252, 250, 255)], lit=0.5),
                 pearlh=M_([(255, 255, 255)] * 3, min=2), eye=M_([(40, 120, 150), (90, 200, 220), (180, 248, 255)], base=0.75, lit=0.15, min=1), barn=M_(BARN, lit=0.34),
                 kelp=M_(KELP, streak=0.1), glow=["eye"])
    m.detail = None
    return m


ALL = {f.__name__: f for f in (static_puff, storm_ram, ash_whirl, core_shard, tidepool_clam)}
