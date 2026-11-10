"""骨掘りの大百足 (bone_centipede)。鯨骨の坑の主 (docs/design/55 の 1 章、00 の 364 = A 骨殻の大百足)。
白い骨の甲板を重ねた長い体 (14 節)、節ごとに尖った脚、前の三分の一を持ち上げて構える。

頭 (2026-11-11 の作り込み): 頭蓋の前は四枚の骨の花弁 (上・下・左・右) が閉じた尖った口先。開くと花のように裂け、
内側の歯の列と、赤い喉の奥の大きな一つ目が見える。首の三節からは肋骨が前へ反って頭を囲む。脚は一本ずつ骨を持ち、歩くと波のように送る。

骨: body (真ん中の節 6) から前へ f5…f0 (f0 = 頭)、後ろへ r7…r13 (r13 = 尾) の鎖。大顎は jaw_l / jaw_r、口の花弁は lip_u / lip_d / lip_l / lip_r (頭の子)。
    脚は leg<節><l|r> (節の子)。
動き: idle・walk・bite (噛む)・combo (三連: 噛む・噛む・叩きつけ)・sweep (尾で薙ぐ)・spit (骨の礫)・burrow (潜る)・emerge (掘り穴から出て突進の構え)・
      erupt (人の足もとから突き上げ、立ち上がって吠え、前へ叩きつける)・roar (落石を呼ぶ)・scream (後半に入る咆哮)・hurt・death
動きはどれも「溜め → 一気に → 行き過ぎ → 戻る」。打つ瞬間は主の技の予告の長さに合わせる (BoneCentipede の *_WARN)。
"""
import math

import numpy as np

from blp import *  # noqa: F401,F403
from blp import keys
import mlp

BONE = [(108, 98, 82), (148, 138, 118), (188, 178, 156), (220, 212, 192), (242, 236, 220)]
FLESH = [(38, 20, 22), (60, 32, 32), (86, 48, 44), (114, 68, 58)]
MAW = [(46, 6, 12), (82, 12, 20), (124, 22, 30), (168, 44, 46)]
CHITIN = [(30, 26, 26), (52, 44, 40), (78, 66, 58), (104, 90, 78)]
N = 14
MID = 6
LIPS = {"lip_u": 90.0, "lip_l": 0.0, "lip_d": 270.0, "lip_r": 180.0}


def path():
    pts = [(0, 34, -36), (0, 30, -28), (0, 20, -18), (0, 10, -6), (0, 7, 8), (0, 7, 22), (0, 7, 36), (4, 7, 50), (10, 7, 62)]
    P = [np.asarray(p, float) for p in pts]
    P = [P[0]] + P + [P[-1]]
    out = []
    segs = len(P) - 3
    for i in range(N):
        t = i / (N - 1) * segs
        k = min(int(t), segs - 1)
        u = t - k
        p0, p1, p2, p3 = P[k], P[k + 1], P[k + 2], P[k + 3]
        out.append(0.5 * ((2 * p1) + (-p0 + p2) * u + (2 * p0 - 5 * p1 + 4 * p2 - p3) * u * u + (-p0 + 3 * p1 - 3 * p2 + p3) * u ** 3))
    return out


def seg_bone(i):
    return "body" if i == MID else (f"f{i}" if i < MID else f"r{i}")


FRONT = [f"f{i}" for i in range(MID - 1, -1, -1)]   # f5 … f0
REAR = [f"r{i}" for i in range(MID + 1, N)]          # r7 … r13
LEGS = [(i, s) for i in range(3, N) for s in (1, -1)]


def leg_name(i, s):
    return f"leg{i}{'l' if s > 0 else 'r'}"


def fell(bone, mat, c, r, Z, Y, segs=10, rings=3, taper=0.0):
    """軸を決めた楕円体: r = (横, 厚み, 長さ)、Z = 長さの向き、Y = 厚みの向き"""
    Z = nrm(np.asarray(Z, float))
    Y = np.asarray(Y, float)
    Y = nrm(Y - Z * np.dot(Y, Z))
    X = np.cross(Y, Z)
    rz = r[2]

    def deform(q):
        # 先 (+Z) へ行くほど横幅を細め、花弁・爪の形にする
        u = max(0.0, q[2] / rz)
        k = 1.0 - taper * u ** 1.2
        return np.array([q[0] * k, q[1] * (1.0 - taper * 0.5 * u), q[2]])
    return mlp.ellipsoid(bone, mat, np.asarray(c, float), r, segs=segs, rings=rings, rot=np.column_stack([X, Y, Z]),
                         deform=deform if taper else None)


def build():
    m = Mount("bone_centipede", "骨掘りの大百足")
    P = path()
    m.bone("body", "root", P[MID])
    for i in range(MID - 1, -1, -1):
        m.bone(f"f{i}", seg_bone(i + 1), (P[i] + P[i + 1]) / 2)
    for i in range(MID + 1, N):
        m.bone(f"r{i}", seg_bone(i - 1), (P[i] + P[i - 1]) / 2)
    head = P[0] + np.array([0, 2, -4])
    m.bone("jaw_l", "f0", head + np.array([5.6, -3.2, -1.0]))
    m.bone("jaw_r", "f0", head + np.array([-5.6, -3.2, -1.0]))
    hinge = {}
    for name, a in LIPS.items():
        ca, sa = math.cos(math.radians(a)), math.sin(math.radians(a))
        hinge[name] = head + np.array([ca * 4.2, sa * 3.8, -4.0])
        m.bone(name, "f0", hinge[name])

    for i, p in enumerate(P):
        b = seg_bone(i)
        r = 7.0 - 2.4 * (i / N) ** 1.3
        d = nrm(P[min(i + 1, N - 1)] - P[max(i - 1, 0)])
        pitch = -math.degrees(math.atan2(d[1], abs(d[2]) + 1e-6))
        # 体の肉 (節の玉。隣と重なって継ぎ目が開かない)
        m.add(f"seg{i}", ell(b, "flesh", p, (r * 1.12, r * 0.80, r * 0.95), segs=12, rings=4, rx=pitch), core=True)
        # 腹の板 (黒い甲)
        m.add(f"belly{i}", ell(b, "chitin", p + np.array([0, -r * 0.42, 0]), (r * 0.86, r * 0.34, r * 0.74), segs=10, rings=2, rx=pitch))
        # 背の骨の甲板: 大きな一枚と、縁の小さな棘 (左右に三つずつ)
        top = p + np.array([0, r * 0.60, 0])
        m.add(f"plate{i}", ell(b, "bone", top, (r * 1.42, r * 0.36, r * 0.78), segs=12, rings=3, rx=pitch))
        m.add(f"ridge{i}", ell(b, "bone", top + np.array([0, r * 0.28, 0.6]), (r * 0.36, r * 0.24, r * 0.66), segs=8, rings=2, rx=pitch))
        m.add(f"spine{i}", cone(b, "bone", top + np.array([0, r * 0.30, 0.4]), (0, 1, 0.35), r * (0.62 if i > 3 else 0.9), r * 0.28, sides=5, rings=2))
        for s in (1, -1):
            for k, dz in enumerate((-0.5, 0.0, 0.5)):
                base = top + np.array([s * r * 1.15, -r * 0.05, dz * r * 0.9])
                m.add(f"edge{i}{s}{k}", cone(b, "bone", base, (s, -0.25, 0.1), r * 0.42, r * 0.13, sides=4, rings=1))
        # 首の肋骨: 前の三節から前へ反って頭を囲む籠
        if 1 <= i <= 3:
            L = (15.0, 13.0, 10.5)[i - 1]
            for s in (1, -1):
                base = p + np.array([s * r * 0.85, r * 0.35, 0.5])
                m.add(f"rib{i}{s}", cone(b, "bone", base, (s * 0.75, 0.55, -0.55), L, r * 0.24, sides=5, rings=4,
                                         bend=(-s * L * 0.30, L * 0.05, -L * 0.45)))
        # 脚 (前の三節には無い)。一本ずつ骨を持つ: 付け根 (腰) で回る
        if i >= 3:
            for s in (1, -1):
                hip = p + np.array([s * r * 0.85, -r * 0.25, 0.4])
                lb = m.bone(leg_name(i, s), b, hip)
                knee = hip + np.array([s * r * 1.15, r * 0.75, 0.6])
                foot = np.array([hip[0] + s * r * 1.9, 0.3, p[2] + 1.6])
                # 腰の玉は体の肉に沈める (回しても付け根が開かない)
                m.add(f"hip{i}{s}", ell(lb, "chitin", hip - np.array([s * 0.6, 0, 0]), (1.6, 1.4, 1.5), segs=6, rings=2))
                m.add(f"thigh{i}{s}", tub(lb, "chitin", [hip - np.array([s * 0.8, 0, 0]), knee], [1.25, 0.95], None, sides=6))
                m.add(f"shin{i}{s}", tub(lb, "bone", [knee, (knee + foot) / 2 + np.array([0, 0.6, 0]), foot], [0.95, 0.65, 0.22], None, sides=5))
                m.add(f"knee{i}{s}", ell(lb, "bone", knee, (1.15, 1.05, 1.15), segs=6, rings=2))
                m.add(f"spur{i}{s}", cone(lb, "bone", knee + np.array([0, 0.4, 0]), (s * 0.3, 1, -0.2), 2.4, 0.5, sides=4, rings=1))
    # 頭の節の付け根: 次の節 (f1) の肉の中まで伸ばす。頭を振っても節の間が開かない
    r1 = 7.0 - 2.4 * (1 / N) ** 1.3
    d1 = nrm(P[0] - P[2])
    m.add("neck0", ell("f0", "flesh", P[0] * 0.35 + P[1] * 0.65, (r1 * 0.92, r1 * 0.66, r1 * 0.80), segs=12, rings=3,
                       rx=-math.degrees(math.atan2(-d1[1], abs(d1[2]) + 1e-6))))
    # 頭蓋 (前は短く切り、口の花弁の付け根を受ける)
    m.add("skull", ell("f0", "bone", head + np.array([0, 0.6, 1.0]), (6.8, 5.0, 5.6), segs=12, rings=4), core=True)
    m.add("brow", ell("f0", "bone", head + np.array([0, 3.6, -0.6]), (5.6, 1.9, 4.2), segs=10, rings=2))
    for s in (1, -1):
        m.add(f"horn{s}", cone("f0", "bone", head + np.array([s * 3.2, 4.4, 0.6]), (s * 0.5, 0.7, 0.6), 7.5, 1.3, sides=5, rings=3, bend=(s * 0.6, -1.4, 1.8)))
    # 喉の奥: 赤い肉の窪み、歯の輪、一つ目 (花弁が開くと見える)
    m.add("throat", ell("f0", "maw", head + np.array([0, 0, -4.2]), (3.7, 3.3, 1.3), segs=12, rings=3))
    for k in range(10):
        a = math.radians(18 + 36 * k)
        base = head + np.array([math.cos(a) * 3.2, math.sin(a) * 2.9, -4.6])
        m.add(f"fang{k}", cone("f0", "bone", base, (-math.cos(a), -math.sin(a), -0.9), 1.9, 0.45, sides=4, rings=1))
    eye(m, "f0", head + np.array([0, 0, -4.4]), 2.25, (0, 0, -1), (236, 206, 120), glow=True, lid=(60, 8, 14))
    # 口の花弁: 閉じると尖った口先。外は骨、内は赤い歯茎と歯の列
    for name, a in LIPS.items():
        ca, sa = math.cos(math.radians(a)), math.sin(math.radians(a))
        out = np.array([ca, sa, 0.0])
        h = hinge[name]
        tip = head + np.array([ca * 0.9, sa * 0.8, -12.5])
        ax = tip - h
        Ln = np.linalg.norm(ax)
        m.add(f"{name}_shell", fell(name, "bone", h + ax * 0.5, (3.6, 1.05, Ln * 0.5 + 1.2), ax, out, segs=10, rings=3, taper=0.8))
        m.add(f"{name}_gum", fell(name, "maw", h + ax * 0.45 - out * 0.55, (3.0, 0.7, Ln * 0.44), ax, out, segs=8, rings=2, taper=0.75))
        m.add(f"{name}_crest", cone(name, "bone", h + ax * 0.35 + out * 0.6, out * 0.9 + nrm(ax) * 0.6, 3.4, 0.9, sides=4, rings=2))
        tang = np.cross(out, nrm(ax))
        for k, (t, w) in enumerate(((0.22, 0.0), (0.42, 1.4), (0.42, -1.4), (0.62, 0.0), (0.78, 0.8), (0.78, -0.8))):
            base = h + ax * t - out * 0.9 + tang * w
            rad = 4.0 - 3.1 * t
            m.add(f"{name}_tooth{k}", cone(name, "bone", base, -out + nrm(ax) * 0.25, min(1.9, rad * 0.62), 0.42, sides=4, rings=1))
    # 横の大顎 (鶴嘴)
    for s, jb in ((1, "jaw_l"), (-1, "jaw_r")):
        root = head + np.array([s * 5.6, -3.2, -1.0])
        m.add(f"jawroot{s}", ell(jb, "chitin", root, (1.9, 1.7, 1.9), segs=8, rings=2))
        m.add(f"mandible{s}", cone(jb, "chitin", root, (s * 0.6, -0.45, -1.0), 12.0, 2.3, sides=6, rings=4, bend=(-s * 5.2, -2.0, 0)))
        m.add(f"pick{s}", cone(jb, "bone", root + np.array([s * 0.8, 0.8, -1.6]), (s * 0.6, -0.85, -0.7), 9.0, 1.8, sides=5, rings=3, bend=(-s * 1.0, -2.2, -2.4)))
        md, mb = nrm(np.array([s * 0.6, -0.45, -1.0])), np.array([-s * 5.2, -2.0, 0])
        for k, u in enumerate((0.35, 0.55, 0.75)):
            on = root + md * 12.0 * u + mb * u * u + np.array([-s * 0.9 * (1 - u), 0, 0])
            m.add(f"tooth{s}{k}", cone(jb, "bone", on, (-s, -0.2, -0.3), 2.0 - 0.4 * k, 0.55, sides=4, rings=1))
        m.add(f"antenna{s}", tub("f0", "chitin", [head + np.array([s * 2.6, 4.0, -1.5]), head + np.array([s * 7.0, 9.5, -6.0]),
                                                  head + np.array([s * 12.0, 10.5, -4.0]), head + np.array([s * 15.0, 8.5, -1.0])], [0.75, 0.55, 0.35, 0.15], None, sides=5))
        for k, (dx, dy) in enumerate(((4.6, 2.2), (3.4, 3.2), (5.4, 0.8))):
            eye(m, "f0", head + np.array([s * dx, dy, -3.4 + 0.5 * k]), 0.8 - 0.12 * k, (s, 0.3, -1), (240, 120, 60), glow=True)
    # 尾の先: 骨の二又
    tail = P[-1]
    for s in (1, -1):
        m.add(f"tailfork{s}", cone("r13", "bone", tail + np.array([s * 1.6, 1.0, 2.0]), (s * 0.5, 0.25, 1), 9.0, 1.6, sides=5, rings=3, bend=(s * 1.6, 1.6, 0)))
    m.pal = dict(mats={"eye": M_(EYE, base=0.5, lit=0.2, noise=0.0, min=0), "bone": M_(BONE, lit=0.34), "flesh": M_(FLESH, streak=0.06),
                       "maw": M_(MAW, lit=0.2, streak=0.08), "chitin": M_(CHITIN, lit=0.36, streak=0.04)}, glow_mats=[])

    def detail(c, col, glow):
        col, glow = speckle(c, col, ["bone"], (118, 108, 90), density=0.06, size=0.8), glow
        col = edge_dark(c, col, ["bone"], k=0.8)
        P_ = c["P"]
        sel = np.isin(c["mats"], ["bone"]) & c["owned"]
        f1, f2, idv = cell3(P_, 3.0, 23)
        crack = sel & ((f2 - f1) < 0.05) & (idv > 0.5)
        col = np.where(crack[..., None], col * 0.62, col)
        # 喉の肉の筋
        maw = np.isin(c["mats"], ["maw"]) & c["owned"]
        g1, g2, _ = cell3(P_, 1.4, 41)
        vein = maw & ((g2 - g1) < 0.08)
        col = np.where(vein[..., None], col * 0.55, col)
        return col, glow
    m.detail = detail
    m.hint = 512
    m.view = dict(center=(0, 16, 6), scale=2.2)
    m.gaits = gaits()
    return m


# ---------------------------------------------------------------- 動き
def chain(names, rx=0.0, ry=0.0, rz=0.0):
    return {b: {"rot": (rx, ry, rz)} for b in names}


def merge(*poses):
    out = {}
    for p in poses:
        for b, ch in p.items():
            out.setdefault(b, {}).update(ch)
    return out


def mouth(a):
    """口の花弁を a 度開く (0 = 閉じた口先)"""
    return {"lip_u": {"rot": (-a, 0, 0)}, "lip_d": {"rot": (a, 0, 0)}, "lip_l": {"rot": (0, -a, 0)}, "lip_r": {"rot": (0, a, 0)}}


def jaws(a):
    """横の大顎を a 度開く (負で閉じて噛み合わせる)"""
    return {"jaw_l": {"rot": (0, -a, 0)}, "jaw_r": {"rot": (0, a, 0)}}


def neck(rx, ry=0.0, head=None):
    """首の鎖 (f5…f1) を節ごとに rx 度、頭 (f0) は head 度 (省けば首の半分を戻して前を見る)"""
    p = chain(FRONT[:-1], rx=rx, ry=ry)
    p["f0"] = {"rot": (-rx * 2.0 if head is None else head, ry, 0)}
    return p


def legs_spread(a):
    """脚をすべて広げる・縮める (z の回し。正で足先が上がる)"""
    return {leg_name(i, s): {"rot": (0, 0, s * a)} for i, s in LEGS}


def shake(base, bones, amp, t0, t1, step=0.08, axis=1):
    """t0〜t1 のあいだ、bones を左右に震わせる"""
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
    # 待ち: 首がゆっくりうねり、口の花弁が呼吸のようにわずかに開き、時々ぴくりと割れる
    idle = Gait("idle", 3.6)
    for k, b in enumerate(FRONT):
        idle.rot(b, W(0).s(2.2, 1, k * 30, "t"), W(0).s(3.0, 1, k * 40 + 90, "t"))
    for k, b in enumerate(REAR):
        idle.rot(b, None, W(0).s(2.0, 1, k * 35, "t"))
    for name, (sx, sy) in {"lip_u": (-1, 0), "lip_d": (1, 0), "lip_l": (0, -1), "lip_r": (0, 1)}.items():
        if sx:
            idle.bones.setdefault(name, {})["rot"] = (W(sx * 5).s(sx * 4, 1, 0, "t").s(sx * 6, 5, 40, "t"), W(0), W(0))
        else:
            idle.bones.setdefault(name, {})["rot"] = (W(0), W(sy * 5).s(sy * 4, 1, 0, "t").s(sy * 6, 5, 40, "t"), W(0))
    idle.rot("jaw_l", None, W(-4).s(6, 2, 0, "t"))
    idle.rot("jaw_r", None, W(4).s(-6, 2, 0, "t"))
    for k, (i, s) in enumerate(LEGS):
        idle.rot(leg_name(i, s), None, W(0).s(3, 1, i * 40 + (0 if s > 0 else 180), "t"), W(0).s(s * 2, 1, i * 50, "t"))
    g.append(idle)
    # 歩き: 体を S 字にうねらせ、脚を前から後ろへ波で送る
    walk = Gait("walk", 1.0)
    for k, b in enumerate(FRONT):
        walk.rot(b, W(0).s(2.5, 2, k * 50, "t"), W(0).s(5.0, 1, k * 45, "t"))
    walk.rot("body", None, W(0).s(5.0, 1, 270, "t"))
    for k, b in enumerate(REAR):
        walk.rot(b, None, W(0).s(6.5, 1, 300 + k * 45, "t"))
    walk.rot("jaw_l", None, W(-8).s(8, 2, 0, "t"))
    walk.rot("jaw_r", None, W(8).s(-8, 2, 0, "t"))
    for name, (sx, sy) in {"lip_u": (-1, 0), "lip_d": (1, 0), "lip_l": (0, -1), "lip_r": (0, 1)}.items():
        w = W(sx * 4 + sy * 4).s((sx + sy) * 3, 2, 0, "t")
        walk.bones.setdefault(name, {})["rot"] = (w if sx else W(0), w if sy else W(0), W(0))
    for i, s in LEGS:
        ph = i * 62 + (0 if s > 0 else 180)
        # 前後に振り (y)、前へ出すときだけ上げる (z。半分の波)
        walk.rot(leg_name(i, s), None, W(0).s(-s * 20, 1, ph, "t"), W(s * 7).s(s * 7, 1, ph + 90, "t"))
    g.append(walk)

    rest = merge(mouth(0), jaws(0), neck(0, head=0))

    # 噛む (予告 0.70 秒 = BITE_WARN 14): 首を縮めて溜め、震え、口を割って一気に突き出し、行き過ぎて戻る
    coil = merge(neck(-11, head=14), mouth(12), jaws(30), legs_spread(6))
    coil_t = merge(neck(-12, head=16), mouth(18), jaws(34), legs_spread(6))
    strike = merge(neck(3, head=10), mouth(72), jaws(-8), {"body": {"pos": (0, 0, -3)}})
    over = merge(neck(4, head=13), mouth(80), jaws(-12), {"body": {"pos": (0, -1, -4)}})
    snap = merge(neck(2, head=6), mouth(4), jaws(-14))
    g.append(keys("bite", 1.2, [(0.0, rest), (0.40, coil), (0.56, coil_t), (0.68, strike), (0.76, over), (0.86, snap), (1.2, rest)]))

    # 三連 (1.30 秒と 2.10 秒にも打つ = COMBO_HIT2 26 / COMBO_HIT3 42): 噛む・反対へ噛む・高く立ち上がって叩きつけ
    c1 = merge(neck(-10, ry=-4, head=14), mouth(14), jaws(30))
    s1 = merge(neck(3, ry=5, head=10), mouth(74), jaws(-8), {"body": {"pos": (0, 0, -3)}})
    c2 = merge(neck(-9, ry=6, head=12), mouth(16), jaws(28))
    s2 = merge(neck(3, ry=-5, head=10), mouth(76), jaws(-10), {"body": {"pos": (0, 0, -3)}})
    up = merge(neck(-17, head=20), mouth(36), jaws(40), legs_spread(10), {"f5": {"rot": (-22, 0, 0)}})
    up_t = merge(neck(-18, head=22), mouth(44), jaws(42), legs_spread(10), {"f5": {"rot": (-24, 0, 0)}})
    slam = merge(neck(5, head=4), mouth(80), jaws(-10), legs_spread(-4), {"body": {"pos": (0, -1, -4)}})
    slam_o = merge(neck(6, head=6), mouth(84), jaws(-12), legs_spread(-6), {"body": {"pos": (0, -2, -4)}})
    g.append(keys("combo", 2.6, [(0.0, rest), (0.38, c1), (0.58, c1), (0.70, s1), (0.80, merge(s1, mouth(30))), (1.08, c2), (1.30, s2),
                                 (1.42, merge(s2, mouth(30))), (1.75, up), (1.96, up_t), (2.10, slam), (2.20, slam_o), (2.36, merge(slam, mouth(20))), (2.6, rest)]))

    # 尾で薙ぐ (0.90 秒 = SWEEP_WARN 18): 尾を大きく引き、首も逆へ捻って溜め、一気に振り抜いて行き過ぎる
    wind = merge(chain(REAR, ry=11), neck(-4, ry=-8, head=4), mouth(20), {"body": {"rot": (0, 10, 0)}})
    wind_t = merge(chain(REAR, ry=12), neck(-5, ry=-9, head=4), mouth(24), {"body": {"rot": (0, 11, 0)}})
    whip = merge(chain(REAR, ry=-14), neck(2, ry=9, head=0), mouth(46), {"body": {"rot": (0, -12, 0)}})
    whip_o = merge(chain(REAR, ry=-17), neck(2, ry=11, head=0), mouth(50), {"body": {"rot": (0, -14, 0)}})
    g.append(keys("sweep", 1.4, [(0.0, rest), (0.55, wind), (0.78, wind_t), (0.90, whip), (1.0, whip_o), (1.14, whip), (1.4, rest)]))

    # 骨の礫 (0.90 秒 = SPIT_WARN 18): 反り返って口を開き、喉の目を見せ、首を投げて吐く
    sp1 = merge(neck(-13, head=8), mouth(40), jaws(36))
    sp2 = merge(neck(-14, head=10), mouth(56), jaws(40))
    sp3 = merge(neck(2, head=-2), mouth(86), jaws(44), {"body": {"pos": (0, 0, -2)}})
    g.append(keys("spit", 1.4, [(0.0, rest), (0.48, sp1), (0.78, sp2), (0.90, sp3), (1.02, merge(sp3, neck(3, head=0))), (1.4, rest)]))

    # 潜る (1.0 秒 = BURROW_DOWN 20): 一度伸び上がって鳴き、頭から床へ突っ込む
    rear = merge(neck(-14, head=18), mouth(50), jaws(40), {"body": {"rot": (-6, 0, 0)}})
    dive = merge(chain(FRONT, rx=22), mouth(10), jaws(-10), {"root": {"pos": (0, -16, 0)}, "body": {"rot": (12, 0, 0)}})
    under = merge(chain(FRONT, rx=26), mouth(0), {"root": {"pos": (0, -90, 0)}, "body": {"rot": (14, 0, 0)}})
    g.append(keys("burrow", 1.0, [(0.0, rest), (0.25, rear), (0.50, dive), (1.0, under)], hold=True))

    # 掘り穴から出る (突進の構え。CHARGE_WARN 26 = 1.3 秒のうちに): 跳び出して花のように口を開いて吠え、頭を低く下げて構える
    burst = merge(neck(-15, head=12), mouth(80), jaws(46), legs_spread(14), {"root": {"pos": (0, 4, 0)}, "body": {"rot": (-8, 0, 0)}})
    low = merge(neck(2, head=-6), mouth(22), jaws(30), legs_spread(-4))
    em = [(0.0, under), (0.22, burst)] + shake(burst, ["f1", "f0"], 7, 0.30, 0.78) + [(1.02, low), (1.3, low)]
    g.append(keys("emerge", 1.3, em))

    # 突き上げ (足もとの円は出て 0.18 秒で打つ、前への叩きつけは 1.40 秒 = ERUPT_SLAM 28):
    # 地の下から縦に跳び上がり、高く立って口を全開にして吠え、のけぞって溜め、前へ倒れ込んで叩きつける
    tall = merge(chain(FRONT, rx=-15), {"f0": {"rot": (12, 0, 0)}}, mouth(84), jaws(48), legs_spread(16),
                 {"root": {"pos": (0, 8, 0)}, "body": {"rot": (-14, 0, 0)}})
    lean = merge(chain(FRONT, rx=-18), {"f0": {"rot": (16, 0, 0)}}, mouth(40), jaws(40), legs_spread(10), {"root": {"pos": (0, 4, 0)}, "body": {"rot": (-16, 0, 0)}})
    crash = merge(chain(FRONT, rx=5), {"f0": {"rot": (6, 0, 0)}}, mouth(82), jaws(-10), legs_spread(-6), {"body": {"pos": (0, -1, -5)}})
    crash_o = merge(crash, chain(FRONT[:-1], rx=6), mouth(86), {"body": {"pos": (0, -2, -5)}})
    er = ([(0.0, under), (0.18, tall)] + shake(tall, ["f2", "f1", "f0"], 8, 0.26, 0.90) +
          [(1.12, lean), (1.40, crash), (1.50, crash_o), (1.66, merge(crash, mouth(24))), (2.0, rest)])
    g.append(keys("erupt", 2.0, er))

    # 吠えて天井を崩す (ROCKS_WARN 30 = 1.5 秒): 伸び上がって口を全開、首を激しく振る
    rise = merge(neck(-13, head=10), mouth(86), jaws(50), legs_spread(10), {"f5": {"rot": (-24, 0, 0)}})
    g.append(keys("roar", 1.6, [(0.0, rest), (0.32, rise)] + shake(rise, ["f2", "f1", "f0"], 9, 0.40, 1.30, step=0.075) + [(1.6, rest)]))

    # 後半に入る咆哮 (RAGE_WARN 24 = 1.2 秒): 顔を天へ突き上げ、口を限界まで開いて震わせ、最後に前へ吠えつける
    sky = merge(neck(-16, head=-4), mouth(88), jaws(52), legs_spread(14), {"f5": {"rot": (-26, 0, 0)}})
    fw = merge(neck(2, head=8), mouth(88), jaws(52), legs_spread(-4), {"body": {"pos": (0, -1, -3)}})
    g.append(keys("scream", 1.6, [(0.0, rest), (0.30, sky)] + shake(sky, ["f1", "f0"], 6, 0.36, 1.06, step=0.07, axis=2) +
                  [(1.20, fw), (1.30, merge(fw, neck(3, head=10))), (1.6, rest)]))

    # 登場 (5.5 秒。DungeonBoss の登場の数え下ろしのあと)。一瞬で出ず、段を踏んで這い出る:
    #   0.4 秒  閉じた口先が床を割って覗く → もがく
    #   1.3 秒  頭が抜ける (大顎を開いて) → 首を振って体を引き抜こうと力む
    #   2.1 秒  首と前の脚が抜ける → もう一度力む
    #   2.75 秒 最後のひと引きで体が抜け、のけぞって口を全開に吠える (名前の札はここ。BoneCentipede.introCardAt = 55 tick)
    #   4.2 秒〜 首を振って辺りを睨み、人の方へ顔を下ろして構える
    stand = merge(chain(FRONT, rx=-16), {"f0": {"rot": (8, 0, 0)}}, {"body": {"rot": (-12, 0, 0)}})

    def up(y, mouth_a=0, jaw_a=0, legs=0, ry=0.0, rx=0.0):
        p = merge(stand, mouth(mouth_a), jaws(jaw_a), legs_spread(legs), {"root": {"pos": (0, y, 0)}})
        for b in ("f1", "f0"):
            r = list(p[b]["rot"])
            r[0] += rx
            r[1] += ry
            p[b]["rot"] = tuple(r)
        return p
    out = merge(up(6, 86, 50, 18), {"f0": {"rot": (6, 0, 0)}})
    intro = [(0.0, merge(stand, {"root": {"pos": (0, -95, 0)}})),
             (0.40, up(-66, 0, 10, ry=0)),
             (0.52, up(-64, 4, 16, ry=6, rx=-3)), (0.64, up(-65, 0, 12, ry=-6, rx=2)), (0.76, up(-63, 6, 18, ry=5, rx=-2)), (0.88, up(-63, 2, 14, ry=-4)),
             (1.30, up(-48, 14, 40, legs=0, rx=-4)),
             (1.40, up(-46, 18, 44, ry=7, rx=-6)), (1.50, up(-46, 12, 40, ry=-7, rx=-4)), (1.60, up(-45, 20, 46, ry=6, rx=-6)), (1.72, up(-44, 10, 38, ry=-3)),
             (2.10, up(-22, 24, 46, legs=10, rx=-6)),
             (2.22, up(-20, 30, 48, legs=12, ry=6, rx=-8)), (2.34, up(-20, 22, 44, legs=12, ry=-6, rx=-6)), (2.46, up(-18, 26, 48, legs=14, ry=4, rx=-8)),
             (2.75, out), (2.90, merge(out, {"root": {"pos": (0, 0, 0)}}))]
    intro += shake(merge(out, {"root": {"pos": (0, 0, 0)}}), ["f2", "f1", "f0"], 7, 2.98, 3.90, step=0.07)
    glare_l = merge(neck(-10, ry=8, head=14), mouth(30), jaws(30), legs_spread(8))
    glare_r = merge(neck(-10, ry=-8, head=14), mouth(30), jaws(30), legs_spread(8))
    face = merge(neck(-4, head=12), mouth(70), jaws(46))
    intro += [(4.20, glare_l), (4.55, glare_r), (4.85, face), (5.00, merge(face, mouth(84))), (5.25, merge(face, mouth(20))), (5.5, rest)]
    g.append(keys("intro", 5.5, intro))

    hurt1 = merge(neck(-6, head=10), mouth(30), jaws(30), chain(["r9", "r10"], ry=5))
    g.append(keys("hurt", 0.36, [(0.0, rest), (0.08, hurt1), (0.2, merge(hurt1, mouth(10))), (0.36, rest)]))

    # 倒れる: 伸び上がって断末魔、震え、横倒しに崩れて体が波打ち、口が力なく開いたまま
    agony = merge(neck(-14, head=-2), mouth(88), jaws(54), legs_spread(14), {"f5": {"rot": (-24, 0, 0)}})
    fall = merge(chain(FRONT, rx=4), chain(REAR, ry=4), mouth(48), jaws(20), legs_spread(18),
                 {"root": {"rot": (0, 0, 24), "pos": (0, -3, 0)}, "f0": {"rot": (6, 0, 14)}})
    limp = merge(fall, chain(REAR, ry=-3), mouth(54), legs_spread(24))
    dth = ([(0.0, rest), (0.32, agony)] + shake(agony, ["f2", "f1", "f0"], 7, 0.40, 1.0, step=0.06) +
           [(1.5, fall), (1.8, merge(fall, chain(REAR, ry=7))), (2.1, limp), (2.6, limp)])
    g.append(keys("death", 2.6, dth, hold=True))
    return g


POSES = [("idle", 0.0), ("walk", 0.25), ("bite", 0.40), ("bite", 0.68), ("combo", 1.96), ("combo", 2.10), ("sweep", 0.90), ("spit", 0.90),
         ("intro", 0.4), ("intro", 1.3), ("intro", 2.1), ("intro", 2.9), ("erupt", 0.18), ("erupt", 1.12), ("erupt", 1.50), ("emerge", 1.02), ("scream", 0.5), ("roar", 0.5), ("death", 2.6)]
BOUNDS = (12, 8)
