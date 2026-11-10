"""殻負いの大蛸 (shell_octopus)。潮の洞の主 (docs/design/55 の 1 章、00 の 365 = B 殻負いの大蛸)。
珊瑚とフジツボに覆われた大きな巻貝の殻を背負い、殻の口から五本の太い腕と大きな目の頭を出す。殻は硬く、腕と頭を狙う。

作り込み (2026-11-11 の声「他のボスたちもこの方向性で」。骨掘りの大百足と同じ方向):
  深い紅紫の肌、山羊のような横長の瞳の金の目。頭の下の嘴は上下に大きく割れ、奥の赤い口と鉤の歯の輪が見える。
  腕は三つの骨 (根・中・先) で波打ち、吸盤の間に骨の鉤。殻の溝と腕の先に青白く光る斑。殻の渦の縁に棘。

骨: body (殻の口の真ん中) の子に shell (殻)・head (頭と目)・arm<k> → arm<k>_m → arm<k>_tip (腕。k = 0..4、左から右)。嘴は beak_u / beak_d (頭の子)。
動き: idle・walk・slam (真ん中の腕で叩きつけ)・slam3 (後半: 三本で続けて)・sweep (両端の腕で薙ぐ)・whirl (腕を回して引き寄せ、振り上げて叩く)・
      ink (墨を吐く)・grab (腕を突き出して掴み、引き寄せて嘴で噛む)・plunge (腕を床へ突き刺す。そのまま止まる)・unplunge (引き抜く)・
      withdraw (殻にこもる。そのまま止まる)・unwithdraw・intro (登場)・scream (後半に入る咆哮)・hurt・death
打つ瞬間は ShellOctopus の *_WARN に合わせる。
"""
import math

import numpy as np

from blp import *  # noqa: F401,F403
from blp import keys, eye

OCT = [(40, 14, 30), (66, 24, 46), (98, 38, 62), (132, 58, 80), (168, 86, 104)]
SUCK = [(170, 140, 136), (208, 182, 170), (236, 216, 202)]
HOOK = [(120, 112, 96), (176, 168, 146), (226, 220, 200)]
BARN = [(120, 122, 118), (168, 170, 160), (210, 212, 200)]
SHELL = [(60, 40, 30), (104, 74, 48), (150, 112, 74), (196, 160, 114), (228, 204, 160)]
CORAL = [(120, 30, 48), (176, 56, 66), (220, 100, 100)]
WEED = [(22, 46, 36), (36, 72, 50), (56, 100, 68)]
MAW = [(40, 4, 12), (78, 10, 22), (120, 22, 34), (164, 46, 52)]
LUME = [(60, 170, 190), (120, 226, 236), (200, 250, 252)]
BEAK = [(18, 16, 20), (40, 36, 40), (70, 64, 66)]
ARMS = 5


def arm_angle(k):
    return math.radians(-70 + 35 * k)


def arm_path(k):
    """腕 k (0 = 左の端 … 4 = 右の端) の根から先までの点。前 (-Z) へ扇に開き、先は床に巻く"""
    a = arm_angle(k)
    d = np.array([math.sin(a), 0, -math.cos(a)])
    side = np.array([math.cos(a), 0, math.sin(a)])
    base = np.array([0, 9, -8]) + d * 5
    pts = [base]
    for i in range(1, 7):
        t = i / 6
        p = base + d * (38 * t) + np.array([0, -7 * min(1, t * 1.6) + 3 * math.sin(t * math.pi), 0]) + side * (5 * t * t * (1 if k < 2 else -1 if k > 2 else 0))
        pts.append(p)
    pts[-1][1] = 2.0
    return pts


def build():
    m = Mount("shell_octopus", "殻負いの大蛸")
    m.bone("body", "root", (0, 14, 0))
    m.bone("shell", "body", (0, 18, 6))
    m.bone("head", "body", (0, 14, -8))
    m.bone("beak_u", "head", (0, 14.2, -16.6))
    m.bone("beak_d", "head", (0, 10.8, -16.6))
    paths = [arm_path(k) for k in range(ARMS)]
    for k, P in enumerate(paths):
        m.bone(f"arm{k}", "body", P[0])
        m.bone(f"arm{k}_m", f"arm{k}", P[2])
        m.bone(f"arm{k}_tip", f"arm{k}_m", P[4])
    # 殻: 渦を重ねた大きな巻貝。口は前 (-Z)。渦の縁に棘、溝に光る斑
    for k in range(8):
        t = k / 7
        c = np.array([0, 20 + 18 * t, 9 - 5 * t])
        r = 19 - 12 * t
        m.add(f"whorl{k}", ell("shell", "shell", c, (r, r * 0.58, r * 0.92), segs=14, rings=4, rx=-22 + 12 * t), core=k == 0)
        m.add(f"rib{k}", ell("shell", "shell", c + np.array([0, -r * 0.22, r * 0.1]), (r * 1.03, r * 0.16, r * 0.95), segs=14, rings=2, rx=-22 + 12 * t))
        if k % 2 == 0 and k < 7:
            n = 7 - k // 2
            for j in range(n):
                a = math.radians(200 + 140 * j / max(1, n - 1))
                base = c + np.array([math.cos(a) * r * 0.96, -r * 0.18, math.sin(a) * r * 0.88])
                m.add(f"spike{k}_{j}", cone("shell", "shell", base, (math.cos(a), 0.45, math.sin(a)), r * 0.34, r * 0.09, sides=5, rings=2,
                                            bend=(0, r * 0.06, 0)))
        if k in (1, 3, 5):
            for j in range(5):
                a = math.radians(-10 + 50 * j + 17 * k)
                p = c + np.array([math.cos(a) * r * 0.86, -r * 0.30, math.sin(a) * r * 0.80])
                m.add(f"lumes{k}_{j}", ell("shell", "lume", p, (1.1, 0.7, 1.1), segs=6, rings=2))
    m.add("spire", cone("shell", "shell", (0, 40, 4), (0, 1, 0.35), 13.0, 4.6, sides=7, rings=3, bend=(0, 0, 2.6)))
    m.add("lip", ell("shell", "shell", (0, 14, -7), (16, 10, 4.4), segs=14, rings=3))
    for j in range(7):
        a = math.radians(200 + 140 * j / 6)
        m.add(f"lipspike{j}", cone("shell", "shell", np.array([math.cos(a) * 14.5, 14 + math.sin(a) * 8.8, -9]), (math.cos(a) * 0.5, math.sin(a) * 0.5, -1),
                                   4.2, 1.2, sides=4, rings=2))
    # 殻の上の珊瑚・フジツボ・海藻
    for k in range(16):
        a = math.radians(k * 22.5 + 7)
        y = 26 + 8 * math.cos(a * 2) + (k % 3) * 2
        base = np.array([math.sin(a) * 13, y, math.cos(a) * 11 + 7])
        if k % 3 == 0:
            m.add(f"coral{k}", cone("shell", "coral", base, (math.sin(a), 0.9, math.cos(a)), 6.5, 1.6, sides=5, rings=3, bend=(0, 1.6, 0)))
            m.add(f"coralb{k}", cone("shell", "coral", base + np.array([0, 2.6, 0]), (math.sin(a + 0.8), 0.7, math.cos(a + 0.8)), 3.6, 1.0, sides=5, rings=2))
        elif k % 3 == 1:
            m.add(f"barn{k}", cone("shell", "barn", base, (math.sin(a) * 0.4, 1, math.cos(a) * 0.4), 2.6, 1.9, sides=6, rings=1))
        else:
            m.add(f"weed{k}", tub("shell", "weed", [base, base + np.array([math.sin(a) * 2, -3, math.cos(a) * 2]),
                                                  base + np.array([math.sin(a) * 2.6, -7, math.cos(a) * 2.6])], [0.9, 0.7, 0.3], None, sides=4))
    # 頭: 殻の口から出る柔らかい頭、大きな目、嘴と口の奥
    m.add("mantle", ell("head", "oct", (0, 16.5, -10.5), (10.5, 8.6, 7.4), segs=12, rings=4), core=True)
    m.add("brow", ell("head", "oct", (0, 22.4, -12), (8.6, 3.2, 4.8), segs=10, rings=2))
    for s in (1, -1):
        m.add(f"frill{s}", cone("head", "oct", (s * 8.4, 21.0, -11), (s * 0.8, 0.6, -0.2), 5.0, 1.6, sides=5, rings=2, bend=(0, 1.4, 0)))
        m.add(f"socket{s}", ell("head", "oct", (s * 6.6, 19.6, -14.0), (4.0, 3.8, 3.2), segs=10, rings=3))
        eye(m, "head", (s * 7.1, 19.6, -16.4), 3.0, (s * 0.55, 0.12, -0.85), (236, 186, 60), glow=True, lid=(60, 18, 34))
        m.eyes[-1]["pa"], m.eyes[-1]["pb"] = 0.62, 0.14
    m.add("throat", ell("head", "maw", (0, 12.5, -16.4), (3.6, 3.0, 1.7), segs=10, rings=3))
    for j in range(10):
        a = math.radians(18 + 36 * j)
        base = np.array([math.cos(a) * 3.0, 12.5 + math.sin(a) * 2.5, -17.4])
        m.add(f"fang{j}", cone("head", "hook", base, (-math.cos(a), -math.sin(a), -0.8), 1.7, 0.42, sides=4, rings=2,
                               bend=(-math.cos(a) * 0.4, -math.sin(a) * 0.4, 0)))
    m.add("beaku", cone("beak_u", "beak", (0, 14.6, -16.4), (0, -0.42, -1), 6.2, 2.9, sides=6, rings=3, bend=(0, -1.5, 0), rb=2.1))
    m.add("beakd", cone("beak_d", "beak", (0, 10.4, -16.4), (0, 0.40, -1), 5.4, 2.6, sides=6, rings=2, bend=(0, 1.0, 0), rb=1.9))
    m.add("siphon", tub("head", "oct", [(7.4, 12.5, -12), (10.0, 10.5, -15), (10.8, 10.0, -17)], [1.9, 1.7, 1.3], None, sides=6))
    # 腕: 根・中・先の三つの骨。継ぎ目は重ねる。腹側に吸盤、その間に骨の鉤、先に光る斑
    for k, P in enumerate(paths):
        r = [5.6, 4.9, 4.2, 3.5, 2.7, 1.8, 0.8]
        b0, b1, b2 = f"arm{k}", f"arm{k}_m", f"arm{k}_tip"
        m.add(f"armb{k}", tub(b0, "oct", [P[0] + np.array([0, 0, 3]), P[1], P[2] + (P[2] - P[1]) * 0.3], r[0:3], None, sides=8))
        m.add(f"armm{k}", tub(b1, "oct", [P[2] - (P[2] - P[1]) * 0.25, P[3], P[4] + (P[4] - P[3]) * 0.3], r[2:5], None, sides=8))
        m.add(f"armt{k}", tub(b2, "oct", [P[4] - (P[4] - P[3]) * 0.25, P[5], P[6]], r[4:7], None, sides=7))
        a = arm_angle(k)
        side = np.array([math.cos(a), 0, math.sin(a)])
        for i in range(1, 6):
            bone = b0 if i < 2 else (b1 if i < 4 else b2)
            q = P[i] + np.array([0, -r[i] * 0.74, 0])
            for sgn in (1, -1):
                m.add(f"suck{k}{i}{sgn}", ell(bone, "suck", q + side * sgn * r[i] * 0.34, (r[i] * 0.34, 0.45, r[i] * 0.34), segs=7, rings=1))
            if 1 <= i <= 4:
                m.add(f"hook{k}{i}", cone(bone, "hook", q + np.array([0, 0.3, 0]), (0, -1, 0), r[i] * 0.55, r[i] * 0.16, sides=4, rings=2,
                                          bend=tuple(-(P[i + 1] - P[i]) / np.linalg.norm(P[i + 1] - P[i]) * r[i] * 0.25)))
            if i >= 3:
                m.add(f"lumea{k}{i}", ell(bone, "lume", P[i] + np.array([0, r[i] * 0.78, 0]), (r[i] * 0.28, 0.4, r[i] * 0.28), segs=6, rings=1))
        m.add(f"curl{k}", ell(b2, "oct", P[6] + np.array([0, 1.0, 0]), (1.6, 1.4, 1.6), segs=7, rings=2))
    m.pal = dict(mats={"eye": M_(EYE, base=0.5, lit=0.2, noise=0.0, min=0), "oct": M_(OCT, streak=0.05, ao=0.14), "suck": M_(SUCK),
                       "hook": M_(HOOK, lit=0.34), "barn": M_(BARN, lit=0.34), "shell": M_(SHELL, lit=0.34, streak=0.08), "coral": M_(CORAL),
                       "weed": M_(WEED), "maw": M_(MAW, lit=0.2, streak=0.08), "beak": M_(BEAK, lit=0.4), "lume": M_(LUME, base=0.8, lit=0.1, noise=0.0)},
                 glow_mats=["lume"])

    def detail(c, col, glow):
        col, glow = bands_y(c, col, ["shell"], 3.0, 0.8, k=0.78, wobble=1.0), glow
        col, glow = spots(c, col, ["oct"], (26, 8, 20), scale=0.22, thr=0.68, a=0.8), glow
        col = edge_dark(c, col, ["shell", "hook"], k=0.8)
        return col, glow
    m.detail = detail
    m.hint = 512
    m.view = dict(center=(0, 18, -8), scale=2.0)
    m.gaits = gaits()
    return m


# ---------------------------------------------------------------- 動き
def merge(*poses):
    out = {}
    for p in poses:
        for b, ch in p.items():
            out.setdefault(b, {}).update(ch)
    return out


def lift(k, deg):
    """腕 k の向きに合わせた持ち上げ (正で先が上がる) を x・z の回しに分ける"""
    a = arm_angle(k)
    return (-deg * math.cos(a), 0.0, -deg * math.sin(a))


def arm(k, root=0.0, mid=0.0, tip=0.0, swing=0.0):
    """腕 k: 根・中・先をそれぞれ持ち上げ (度)、swing は根の左右の振り"""
    r = lift(k, root)
    return {f"arm{k}": {"rot": (r[0], swing, r[2])}, f"arm{k}_m": {"rot": lift(k, mid)}, f"arm{k}_tip": {"rot": lift(k, tip)}}


def arms(root=0.0, mid=0.0, tip=0.0, which=range(ARMS), swing=0.0):
    out = {}
    for k in which:
        out.update(arm(k, root, mid, tip, swing))
    return out


def beak(a):
    """嘴を a 度開く"""
    return {"beak_u": {"rot": (-a * 0.55, 0, 0)}, "beak_d": {"rot": (a, 0, 0)}}


def head(rx=0.0, ry=0.0, y=0.0, z=0.0):
    return {"head": {"rot": (rx, ry, 0), "pos": (0, y, z)}}


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
    # 待ち: 腕が根から先へ波打ち、頭がゆっくり上下し、嘴が時々かちかち鳴る
    idle = Gait("idle", 3.6)
    for k in range(ARMS):
        for j, b in enumerate((f"arm{k}", f"arm{k}_m", f"arm{k}_tip")):
            amp = (3.0, 6.0, 9.0)[j]
            ph = k * 70 + j * 60
            a = arm_angle(k)
            idle.rot(b, W(0).s(-amp * math.cos(a), 1, ph, "t"), W(0).s(3.0 + 2 * j, 1, ph + 40, "t"), W(0).s(-amp * math.sin(a), 1, ph, "t"))
    idle.rot("head", W(0).s(2.5, 1, 0, "t"), W(0).s(3.0, 1, 90, "t"))
    idle.pos("head", None, W(0).s(0.6, 1, 90, "t"))
    idle.rot("beak_d", W(4).s(5, 3, 0, "t"))
    idle.rot("beak_u", W(-2).s(-2.5, 3, 0, "t"))
    idle.rot("shell", None, None, W(0).s(1.4, 1, 40, "t"))
    g.append(idle)
    # 歩き: 腕で床を掻いて体を引きずる (波が先へ流れる)、殻が揺れる
    walk = Gait("walk", 1.3)
    for k in range(ARMS):
        for j, b in enumerate((f"arm{k}", f"arm{k}_m", f"arm{k}_tip")):
            amp = (6.0, 9.0, 12.0)[j]
            ph = k * 72 + j * 70
            a = arm_angle(k)
            walk.rot(b, W(0).s(-amp * math.cos(a), 1, ph, "t"), W(0).s(6.0, 1, ph + 90, "t"), W(0).s(-amp * math.sin(a), 1, ph, "t"))
    walk.pos("body", None, W(0).s(1.0, 2, 0, "t"))
    walk.rot("shell", None, W(0).s(3.5, 1, 0, "t"), W(0).s(2.5, 1, 90, "t"))
    walk.rot("head", W(0).s(3.0, 2, 30, "t"))
    g.append(walk)

    rest = {}
    # 叩きつけ (SLAM_WARN 16 = 0.80 秒): 真ん中の腕を高く巻き上げ、震わせ、一気に叩きつけ、跳ね返る
    up2 = merge(arm(2, 40, 34, 30), arms(-6, 4, 6, which=(1, 3)), head(-8, y=1), beak(18))
    up2t = merge(arm(2, 44, 36, 32), arms(-6, 4, 6, which=(1, 3)), head(-9, y=1), beak(22))
    hit2 = merge(arm(2, -8, -4, -2), head(8, z=-1.5), beak(40), {"body": {"pos": (0, -1, -1)}})
    over2 = merge(arm(2, -11, -6, -4), head(10, z=-2), beak(46), {"body": {"pos": (0, -1.5, -1)}})
    g.append(keys("slam", 1.5, [(0.0, rest), (0.50, up2), (0.66, up2t), (0.80, hit2), (0.88, over2), (1.02, merge(hit2, beak(10))), (1.5, rest)]))
    # 後半の三本 (真ん中 0.80 秒、両脇 1.20 秒 = SLAM_WARN + 8)
    up13 = merge(arms(40, 30, 26, which=(1, 3)))
    hit13 = merge(arms(-8, -4, -2, which=(1, 3)))
    g.append(keys("slam3", 1.9, [(0.0, rest), (0.50, merge(up2, arms(20, 16, 12, which=(1, 3)))), (0.66, merge(up2t, arms(24, 18, 14, which=(1, 3)))),
                                 (0.80, merge(hit2, arms(30, 24, 20, which=(1, 3)))), (1.02, merge(hit2, up13)), (1.20, merge(hit2, hit13, beak(50))),
                                 (1.30, merge(over2, arms(-11, -6, -4, which=(1, 3)))), (1.9, rest)]))
    # 薙ぎ (SWEEP_WARN 18 = 0.90 秒): 両端の腕を外へ大きく引いて溜め、内へ振り抜いて行き過ぎる
    wind = merge(arm(0, 10, 8, 12, swing=-45), arm(4, 10, 8, 12, swing=45), head(-6), beak(14))
    windt = merge(arm(0, 12, 10, 14, swing=-48), arm(4, 12, 10, 14, swing=48), head(-7), beak(18))
    swing_ = merge(arm(0, -2, -6, -10, swing=50), arm(4, -2, -6, -10, swing=-50), head(6), beak(36))
    swing_o = merge(arm(0, -3, -8, -14, swing=58), arm(4, -3, -8, -14, swing=-58), head(7), beak(40))
    g.append(keys("sweep", 1.4, [(0.0, rest), (0.58, wind), (0.78, windt), (0.90, swing_), (1.00, swing_o), (1.14, swing_), (1.4, rest)]))
    # 渦 (WHIRL_WARN 22 = 1.10 秒に引き寄せ、2.00 秒に足もとを叩く): 腕を順に回して渦を作り、全部の腕を振り上げて叩きつける
    wh = [(0.0, rest)]
    for i in range(6):
        t = 0.15 + i * 0.16
        p = merge(head(-4), beak(20))
        for k in range(ARMS):
            p.update(arm(k, 8, 10, 14, swing=30 * math.sin(i * 1.4 + k * 1.2)))
        wh.append((round(t, 3), p))
    allup = merge(arms(46, 30, 24), head(-14, y=2), beak(44), {"body": {"pos": (0, 1.5, 0)}})
    allup_t = merge(arms(50, 32, 26), head(-15, y=2), beak(50), {"body": {"pos": (0, 1.5, 0)}})
    allhit = merge(arms(-8, -6, -4), head(8, z=-1), beak(54), {"body": {"pos": (0, -2, 0)}})
    wh += [(1.10, merge(arms(-4, 2, 4, swing=0), head(4), beak(30))), (1.62, allup), (1.86, allup_t), (2.00, allhit),
           (2.10, merge(allhit, arms(-10, -8, -6))), (2.4, rest)]
    g.append(keys("whirl", 2.4, wh))
    # 墨 (INK_WARN 24 = 1.20 秒に降る): 外套を大きく膨らませ、のけぞって嘴を開き、二度吐く
    pump = merge(head(-14, y=1.6, z=1.4), {"shell": {"rot": (-5, 0, 0)}}, beak(10), arms(6, 6, 8))
    spit = merge(head(10, y=-0.6, z=-2.0), {"shell": {"rot": (4, 0, 0)}}, beak(56), arms(-4, -2, 0))
    g.append(keys("ink", 1.4, [(0.0, rest), (0.45, pump), (0.62, spit), (0.80, pump), (0.96, spit), (1.4, rest)]))
    # 掴み噛み (GRAB_WARN 16 = 0.80 秒に腕が届き、GRAB_BITE 28 = 1.40 秒に噛む):
    # 真ん中の腕を後ろへ引いて溜め、まっすぐ突き出し、巻き取って引き寄せ、頭を突き出して嘴で噛みつく
    gcoil = merge(arm(2, 18, 24, 30, swing=0), head(-6), beak(10), {"body": {"pos": (0, 0, 1.5)}})
    gthr = merge(arm(2, 2, -2, -2), head(2), beak(16), {"body": {"pos": (0, 0, -1.5)}})
    gpull = merge(arm(2, 14, 44, 56, swing=38), head(-10, y=1.5), beak(50))
    gbite = merge(arm(2, 6, 30, 40, swing=40), head(12, y=1, z=-3.5), beak(68), {"body": {"pos": (0, -1, -2)}})
    gsnap = merge(arm(2, 4, 20, 26, swing=30), head(8, z=-2), beak(0))
    g.append(keys("grab", 2.0, [(0.0, rest), (0.48, gcoil), (0.80, gthr), (0.88, merge(gthr, arm(2, 0, -4, -4))), (1.12, gpull), (1.40, gbite),
                                (1.50, gsnap), (2.0, rest)]))
    # 腕の林 (PLUNGE_DOWN 20 = 1.00 秒に突き刺す): 全部の腕を高く掲げ、床へ突き刺して止まる (床から腕が突き上がる)
    praise = merge(arms(52, 26, 18), head(-12, y=2), beak(40), {"body": {"pos": (0, 2, 0)}})
    praise_t = merge(arms(56, 28, 20), head(-13, y=2), beak(46), {"body": {"pos": (0, 2, 0)}})
    planted = merge(arms(-14, -30, -40), head(-6, y=-1), beak(24), {"body": {"pos": (0, -2.5, 0)}})
    g.append(keys("plunge", 1.2, [(0.0, rest), (0.55, praise), (0.80, praise_t), (1.00, planted), (1.2, planted)], hold=True))
    g.append(keys("unplunge", 0.8, [(0.0, planted), (0.3, merge(arms(20, 10, 6), head(-8), beak(30))), (0.8, rest)]))
    # 殻ごもり
    tucked = merge(head(10, y=-3.0, z=7.0), arms(16, 46, 64), {"body": {"pos": (0, -1.0, 0)}}, beak(0))
    g.append(keys("withdraw", 0.8, [(0.0, rest), (0.25, merge(head(-6), beak(30), arms(10, 6, 4))), (0.8, tucked)], hold=True))
    g.append(keys("unwithdraw", 0.8, [(0.0, tucked), (0.45, merge(arms(30, 16, 10), head(-10), beak(44))), (0.8, rest)]))
    # 後半に入る咆哮 (RAGE_WARN 24 = 1.20 秒): 腕を全部広げて掲げ、頭をのけぞらせて嘴を全開に、震わせて前へ吠える
    # 腕は左右へ大きく広げて掲げ、真ん中の腕は床に伏せる (顔と開いた嘴が前から見える)
    sky = merge({k2: v for k in (0, 1, 3, 4) for k2, v in arm(k, 40, 22, 18, swing=(-1 if k < 2 else 1) * (30 if k in (1, 3) else 18)).items()},
                arm(2, -6, 8, 14), head(-14, y=4.0, z=-2.0), beak(74), {"body": {"pos": (0, 2, 0)}})
    fw = merge(arms(10, 4, 0), head(10, z=-2), beak(70))
    g.append(keys("scream", 1.6, [(0.0, rest), (0.32, sky)] + shake(sky, ["head"], 6, 0.38, 1.06, step=0.07) + [(1.20, fw), (1.6, rest)]))
    # 登場 (5.0 秒): 殻が床を割って迫り上がり (腕は巻いたまま)、どすんと据わると腕が一本ずつ解けて床を叩き、
    # 頭が殻の口から這い出て、全部の腕を掲げて嘴を全開に吠える (名前の札はここ 3.30 秒 = introCardAt 66)、震えて構える
    sunk = merge(tucked, {"root": {"pos": (0, -60, 0)}})
    it = [(0.0, sunk), (0.7, merge(tucked, {"root": {"pos": (0, -34, 0)}}))]
    it += [(0.85, merge(tucked, {"root": {"pos": (0, -32, 0)}, "shell": {"rot": (0, 0, 4)}})),
           (1.0, merge(tucked, {"root": {"pos": (0, -30, 0)}, "shell": {"rot": (0, 0, -4)}})),
           (1.55, merge(tucked, {"root": {"pos": (0, -4, 0)}})), (1.75, merge(tucked, {"root": {"pos": (0, 1.5, 0)}})), (1.9, tucked)]
    unf = merge(tucked)
    for i, k in enumerate((0, 4, 1, 3, 2)):
        t = 2.05 + i * 0.2
        up = merge(unf, arm(k, 40, 24, 16))
        it.append((round(t, 3), up))
        unf = merge(unf, arm(k, -4, -2, 0))
        it.append((round(t + 0.12, 3), merge(unf)))
    emerge = merge(unf, head(-6, y=0, z=0), beak(20))
    it.append((3.10, emerge))
    it.append((3.30, merge(sky)))
    it += shake(sky, ["head"], 6, 3.38, 4.10, step=0.07)
    it += [(4.40, merge(arms(6, 4, 2), head(-4), beak(30))), (4.65, merge(head(4), beak(6))), (5.0, rest)]
    g.append(keys("intro", 5.0, it))
    g.append(keys("hurt", 0.32, [(0.0, rest), (0.1, merge(head(-8), beak(30), arms(8, 6, 6))), (0.32, rest)]))
    # 倒れる: 腕がのたうち、嘴を開いたまま頭が垂れ、殻ごと横へ傾いて腕が力なく伸びる
    writhe1 = merge(arms(20, -20, 30), head(-14, y=1), beak(64))
    writhe2 = merge(arms(-6, 26, -24), head(-10), beak(56))
    slump = merge(head(16, y=-3, z=-1), beak(40), {"shell": {"rot": (0, 0, 18)}, "body": {"pos": (0, -1.5, 0)}}, arms(-2, 2, 4))
    g.append(keys("death", 2.2, [(0.0, rest), (0.3, writhe1), (0.6, writhe2), (0.9, writhe1), (1.2, writhe2), (1.8, slump), (2.2, slump)], hold=True))
    return g


POSES = [("idle", 0.0), ("slam", 0.66), ("slam", 0.80), ("sweep", 0.78), ("whirl", 1.86), ("grab", 1.12), ("grab", 1.40), ("plunge", 1.2),
         ("ink", 0.62), ("withdraw", 0.8), ("scream", 0.5), ("intro", 1.0), ("intro", 2.4), ("intro", 3.3), ("death", 2.2), ("walk", 0.3)]
BOUNDS = (10, 7)
