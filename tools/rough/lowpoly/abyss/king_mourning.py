"""深みの王「喪服の王」C 冠の檻 (2026-11-08 の選定。ラフ docs/review/roughs_abyss/reshape_king.png の C)。五つの元素の王で同じ形。
  - 深い紫の天鵞絨の衣と金の縁。裾は八本の帯に裂けて床を引きずる (前の帯は robe_f、後ろは robe_b の骨で揺れる)
  - 頭を包む金の檻の冠 (crown の骨で回る) と、その上の小さな冠。檻の中で元素の色の目が光る
  - 胸の前に、下の一対の腕 (胸の骨に付く) が弔いの花輪を抱える。花は元素の色に光る
  - 元素ごとの差し: 骨 = 冠の棘と肩の肋、塩 = 冠と肩の結晶、雷 = 冠の銅の針、灰 = 冠の燠の灯し、影 = 肩から垂れる影の糸
周 (cycle) が進むと、冠の檻の輪と帯が増え、背に骨の扇 (喪の飾り) が開き、割れ目が光る (boss_kit.warp で伸びて傾く)。
骨は灰の王と同じ (ASH_BONES。動きは gen_abyss_boss_models.humanoid(robe=True))。
"""
import math
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from boss_kit import *  # noqa: E402,F401,F403
import boss_kit as K  # noqa: E402

S = 1.8

K.MATS.setdefault("velvet", dict(ramp=[(12, 6, 18), (26, 12, 36), (44, 20, 58), (70, 38, 88), (104, 66, 126)], grain=0.2, crev=0.5, weave=0.35))
K.MATS.setdefault("velvet2", dict(ramp=[(8, 4, 12), (18, 8, 26), (32, 14, 42), (52, 26, 64)], grain=0.16, crev=0.45, weave=0.3))
K.MATS.setdefault("gold", dict(ramp=[(90, 60, 18), (140, 100, 32), (196, 152, 60), (236, 200, 104), (255, 234, 168)], grain=0.08, crev=0.5, rim=0.4))
K.MATS.setdefault("lace", dict(ramp=[(4, 4, 6), (12, 10, 14), (24, 20, 26), (38, 34, 40)], grain=0.3, crev=0.3, weave=0.6))

ELEM = {
    "king_bone": dict(glow="red", accent="bone"),
    "king_salt": dict(glow="teal", accent="salt"),
    "king_storm": dict(glow="bolt", accent="copper"),
    "king_ash": dict(glow="ember", accent="iron"),
    "king_shadow": dict(glow="pale", accent="tar"),
}


def hp(x, y, z):
    return P(x * S, y * S, z * S)


def ring_pts(y, rx, rzf, rzb, n=12, cz=0.0, wob=0.0, seed=0.0):
    pts = []
    for k in range(n):
        a = 2 * math.pi * k / n
        r_ = 1.0 + wob * math.sin(a * 3 + seed)
        z = -math.cos(a) * (rzf if math.cos(a) > 0 else rzb)
        pts.append(hp(rx * math.sin(a) * r_, y, cz + z * r_))
    return pts


def build(name):
    e = ELEM[name]
    glow, acc = e["glow"], e["accent"]

    def f(cycle):
        F = []
        b, c, h, cr = "body", "chest", "head", "crown"
        # 脚 (衣の下。足先だけ見える)
        for s in (-1, 1):
            lg = side_bone(s, "leg")
            F += part(f"legcore{s}", tube(lg, "velvet2", [hp(s * 1.9, 11.4, 0), hp(s * 2.0, 0.9, 0.1)], ra=1.2 * S, sides=7, caps=(True, True)))
            F += part(f"foot{s}", ellipsoid(lg, "gold", tuple(hp(s * 2.0, 0.7, -0.9)), (0.95 * S, 0.6 * S, 1.7 * S), segs=8, rings=3))
        # 衣: 前と後ろの裾 (腰から膝下)。そこから八本の帯に裂けて床を引きずる
        F += part("robe_front", loft("robe_f", "velvet", [ring_pts(11.6, 3.0, 1.9, 0.2, n=12, cz=-0.2), ring_pts(7.0, 3.6, 2.4, 0.2, n=12, cz=-0.3, wob=0.03, seed=1),
                                                         ring_pts(5.0, 3.9, 2.6, 0.2, n=12, cz=-0.3, wob=0.05, seed=2)], caps=(True, True)))
        F += part("robe_back", loft("robe_b", "velvet", [ring_pts(11.6, 3.0, 0.2, 2.0, n=12, cz=0.2), ring_pts(7.0, 3.7, 0.2, 2.5, n=12, cz=0.3, wob=0.03, seed=3),
                                                        ring_pts(5.0, 4.0, 0.2, 2.8, n=12, cz=0.3, wob=0.05, seed=4)], caps=(True, True)))
        F += part("hem_f", loop("robe_f", "gold", 0, 5.1 * S, -0.3 * S, 3.95 * S, 2.65 * S, r=0.3 * S, n=12, gap=170, at=180, sides=5))
        F += part("hem_b", loop("robe_b", "gold", 0, 5.1 * S, 0.3 * S, 4.05 * S, 2.85 * S, r=0.3 * S, n=12, gap=170, at=0, sides=5))
        n_strip = 8 + 2 * cycle
        for i in range(n_strip):
            a = 2 * math.pi * (i + 0.5) / n_strip
            front = math.cos(a) > 0
            bone_ = "robe_f" if front else "robe_b"
            x0, z0 = 3.6 * math.sin(a), -2.4 * math.cos(a) if front else -2.7 * math.cos(a)
            p0 = hp(x0, 5.4, z0)
            p1 = hp(x0 * 1.25, 2.6, z0 * 1.3)
            p2 = hp(x0 * 1.6 + 0.4 * math.sin(a * 3), 0.25, z0 * 1.7)
            m = "velvet" if i % 2 == 0 else "lace"
            F += part(f"strip{i}", tube(bone_, m, [p0, p1, p2], ra=[0.95 * S, 0.8 * S, 0.55 * S], rb=[0.25 * S, 0.22 * S, 0.15 * S], sides=4,
                                        hint=(math.sin(a), 0, -math.cos(a)), caps=(True, True)))
            if i % 2 == 0:
                F += part(f"stripgold{i}", ellipsoid(bone_, "gold", tuple(np.asarray(p2) + np.array([0, 0.3 * S, 0])), (0.5 * S, 0.3 * S, 0.3 * S), segs=6, rings=2))
        F += part("girdle", loop(b, "gold", 0, 12.0 * S, 0.1 * S, 3.2 * S, 2.3 * S, r=0.38 * S, n=14, gap=8, at=0, sides=5))
        F += part("girdle_gem", ellipsoid(b, glow, tuple(hp(0, 12.0, -2.4)), (0.6 * S, 0.7 * S, 0.3 * S), segs=7, rings=3))
        # 胴の衣と金の前立て
        F += part("torso", loft(c, "velvet", [ring_pts(11.8, 3.1, 2.1, 2.2, n=12), ring_pts(16.0, 3.6, 2.3, 2.5, n=12), ring_pts(20.4, 4.2, 2.4, 2.7, n=12, cz=0.2),
                                              ring_pts(22.0, 3.0, 1.9, 2.3, n=12, cz=0.2)], caps=(True, True)))
        for s in (-1, 1):
            F += part(f"placket{s}", tube(c, "gold", [hp(s * 0.9, 21.6, -2.0), hp(s * 0.8, 16.6, -2.4), hp(s * 0.7, 12.2, -2.2)], ra=0.28 * S, sides=5, caps=(True, True)))
        for k in range(4):
            F += part(f"button{k}", ellipsoid(c, "gold", tuple(hp(0, 20.8 - k * 2.0, -2.3)), (0.32 * S, 0.32 * S, 0.2 * S), segs=6, rings=2))
        # 高い襟 (金の縁) と肩の外套
        for s in (-1, 1):
            F += part(f"collar{s}", cone(c, "velvet2", tuple(hp(s * 1.9, 21.8, 0.4)), (s * 0.45, 1, 0.35), 4.2 * S, 1.1 * S, sides=4, rings=3, bend=(s * 0.4 * S, 0, 0.6 * S)))
            F += part(f"collar_rim{s}", tube(c, "gold", [hp(s * 1.9, 21.8, -0.4), hp(s * 3.0, 25.4, 0.6), hp(s * 3.6, 27.6, 1.8)], ra=0.22 * S, sides=4, caps=(True, True)))
            F += part(f"mantle{s}", ellipsoid(c, "velvet", tuple(hp(s * 3.3, 21.0, 0.3)), (2.6 * S, 1.3 * S, 2.6 * S), segs=10, rings=4, rot=rot_z(s * -18)))
            F += part(f"epaulet{s}", ellipsoid(c, "gold", tuple(hp(s * 4.4, 21.6, 0.2)), (1.4 * S, 0.5 * S, 1.4 * S), segs=10, rings=2))
            for k in range(4):
                F += part(f"fringe{s}{k}", tube(c, "gold", [hp(s * (3.6 + k * 0.45), 21.2, -0.9 + k * 0.6), hp(s * (3.8 + k * 0.5), 19.6, -1.0 + k * 0.6)], ra=0.12 * S, sides=4, caps=(True, True)))
        # 背の外套 (胸から裾の上まで)
        F += part("cape", loft(c, "velvet2", [ring_pts(21.6, 4.2, 0.1, 0.5, n=10, cz=2.4), ring_pts(15.0, 4.8, 0.1, 0.6, n=10, cz=2.9),
                                              ring_pts(8.0, 5.2, 0.1, 0.6, n=10, cz=3.4, wob=0.05, seed=5)], caps=(True, True)))
        # 腕 (上の一対。袖と骨の手)
        for s in (-1, 1):
            a_, f_ = side_bone(s, "arm"), side_bone(s, "fore")
            F += part(f"sleeve_u{s}", tube(a_, "velvet", [hp(s * 4.0, 21.2, 0.1), hp(s * 4.8, 18.6, 0.2), hp(s * 5.2, 16.4, 0.3)], ra=[1.3 * S, 1.15 * S, 1.15 * S], sides=8, caps=(True, True)))
            F += part(f"sleeve_l{s}", tube(f_, "velvet", [hp(s * 5.2, 16.6, 0.3), hp(s * 5.4, 13.6, -0.3), hp(s * 5.6, 11.0, -0.9)], ra=[1.1 * S, 1.35 * S, 1.75 * S], sides=8, caps=(True, True)))
            F += part(f"cuff{s}", loop(f_, "gold", s * 5.6 * S, 11.1 * S, -0.9 * S, 1.75 * S, 1.75 * S, r=0.25 * S, n=10, gap=10, sides=4))
            F += part(f"cuff_lace{s}", ellipsoid(f_, "lace", tuple(hp(s * 5.6, 10.6, -0.95)), (1.5 * S, 0.45 * S, 1.5 * S), segs=10, rings=2))
            F += part(f"hand{s}", ellipsoid(f_, "bone", tuple(hp(s * 5.6, 9.8, -1.1)), (0.7 * S, 0.95 * S, 0.58 * S), segs=7, rings=3))
            for k, dz in enumerate((-0.45, 0.0, 0.45)):
                F += part(f"finger{s}{k}", tube(f_, "bone", [hp(s * 5.6, 9.1, -1.1 + dz), hp(s * 5.65, 7.8, -1.5 + dz), hp(s * 5.55, 6.8, -1.3 + dz)], ra=[0.2 * S, 0.16 * S, 0.08 * S],
                                                sides=4, caps=(True, True)))
                F += part(f"ring{s}{k}", ellipsoid(f_, "gold", tuple(hp(s * 5.62, 8.4, -1.35 + dz)), (0.24 * S, 0.14 * S, 0.24 * S), segs=6, rings=2))
        # 下の一対の腕 (胸の骨) と弔いの花輪
        for s in (-1, 1):
            p0, p1, p2 = hp(s * 3.0, 17.6, -1.4), hp(s * 3.6, 15.6, -3.2), hp(s * 2.2, 15.0, -4.8)
            F += part(f"lowarm{s}", tube(c, "velvet", [p0, p1, p2], ra=[0.85 * S, 0.75 * S, 0.7 * S], sides=7, caps=(True, True)))
            F += part(f"lowhand{s}", ellipsoid(c, "bone", tuple(hp(s * 1.8, 15.0, -5.1)), (0.55 * S, 0.7 * S, 0.5 * S), segs=7, rings=3))
        F += part("wreath", loop(c, "lace", 0, 15.0 * S, -5.6 * S, 2.6 * S, 0.9 * S, r=0.62 * S, n=14, gap=6, sides=5, tilt=0.0))
        for i in range(7):
            a = 2 * math.pi * i / 7
            q = hp(2.6 * math.sin(a), 15.0 + 0.0, -5.6 - 0.9 * math.cos(a))
            F += part(f"bloom{i}", ellipsoid(c, glow if i % 2 == 0 else "velvet", tuple(q), (0.62 * S, 0.62 * S, 0.5 * S), segs=7, rings=3))
            F += part(f"leafw{i}", cone(c, "lace", tuple(q), (math.sin(a), 0.3, -math.cos(a) * 0.3), 1.1 * S, 0.3 * S, sides=4, rings=1))
        F += part("ribbon_l", tube(c, "gold", [hp(-0.6, 14.4, -5.9), hp(-1.0, 11.0, -5.6), hp(-0.8, 8.4, -5.2)], ra=0.4 * S, rb=0.1 * S, sides=4, hint=(0, 0, 1), caps=(True, True)))
        F += part("ribbon_r", tube(c, "gold", [hp(0.6, 14.4, -5.9), hp(1.1, 11.4, -5.6), hp(1.0, 9.0, -5.2)], ra=0.4 * S, rb=0.1 * S, sides=4, hint=(0, 0, 1), caps=(True, True)))
        # 頭: 骨の顔、檻の中で元素の目が光る
        F += part("cranium", ellipsoid(h, "bone", tuple(hp(0, 24.8, 0.2)), (1.9 * S, 2.25 * S, 2.2 * S), segs=12, rings=6))
        F += part("face", ellipsoid(h, "calc", tuple(hp(0, 23.6, -1.1)), (1.45 * S, 1.35 * S, 1.05 * S), segs=10, rings=4))
        for sx in (-1, 1):
            F += part(f"sock{sx}", ellipsoid(h, "hollow", tuple(hp(sx * 0.72, 24.5, -1.85)), (0.58 * S, 0.52 * S, 0.4 * S), segs=8, rings=3))
            F += part(f"eye{sx}", ellipsoid(h, glow, tuple(hp(sx * 0.72, 24.4, -2.02)), (0.26 * S, 0.22 * S, 0.16 * S), segs=6, rings=2))
        F += part("veil", loft(h, "lace", [ring_pts(26.6, 2.1, 2.1, 2.1, n=10, cz=0.3), ring_pts(24.6, 2.5, 0.2, 2.5, n=10, cz=0.5), ring_pts(22.0, 2.9, 0.2, 2.8, n=10, cz=0.8)],
                                caps=(True, True)))
        # 冠の檻 (crown の骨): 縦の金の帯と横の輪、頂の小さな冠
        Rr = 3.1
        nv = 8 + 2 * cycle
        for i in range(nv):
            a = 2 * math.pi * i / nv
            pts = []
            for k in range(7):
                t = k / 6
                y = 22.2 + t * 7.0
                r_ = Rr * math.sin(math.pi * (0.15 + 0.7 * t)) + 0.4
                pts.append(hp(r_ * math.sin(a), y, 0.4 - r_ * math.cos(a)))
            F += part(f"cagebar{i}", tube(cr, "gold", pts, ra=0.2 * S, sides=4, caps=(True, True)))
        for j, (y, r_) in enumerate(((23.6, 2.95), (26.0, 3.5), (28.2, 2.3))):
            F += part(f"cagering{j}", loop(cr, "gold", 0, y * S, 0.4 * S, r_ * S, r_ * S, r=0.24 * S, n=16, gap=4, at=180, sides=4))
        F += part("cage_top", ellipsoid(cr, "gold", tuple(hp(0, 29.3, 0.4)), (1.0 * S, 0.5 * S, 1.0 * S), segs=8, rings=3))
        for i in range(5):
            a = 2 * math.pi * i / 5
            F += part(f"topspike{i}", cone(cr, "gold", tuple(hp(0.7 * math.sin(a), 29.6, 0.4 - 0.7 * math.cos(a))), (math.sin(a) * 0.3, 1, -math.cos(a) * 0.3), 1.6 * S, 0.24 * S, sides=4, rings=1))
        F += part("top_gem", ellipsoid(cr, glow, tuple(hp(0, 30.3, 0.4)), (0.45 * S, 0.6 * S, 0.45 * S), segs=6, rings=3))
        # 元素の差し
        if name == "king_bone":
            for i in range(nv // 2):
                a = 2 * math.pi * (i * 2) / nv
                F += part(f"cagethorn{i}", cone(cr, "bone", tuple(hp(3.4 * math.sin(a), 26.0, 0.4 - 3.4 * math.cos(a))), (math.sin(a), 0.4, -math.cos(a)), 1.4 * S, 0.22 * S, sides=4, rings=1))
            for s in (-1, 1):
                for k in range(3):
                    F += part(f"shrib{s}{k}", arc(side_bone(s, "arm"), "bone", hp(s * 4.4, 22.0, -0.6 + k * 0.6), hp(s * (6.0 + k * 0.4), 23.4 + k * 0.4, -0.4 + k * 0.6),
                                                  hp(s * (7.0 + k * 0.6), 22.4 + k * 0.6, 0.2 + k * 0.6), r=0.3 * S, n=4, taper=(0.3 * S, 0.06 * S), sides=4))
        elif name == "king_salt":
            for i in range(nv // 2):
                a = 2 * math.pi * (i * 2 + 1) / nv
                F += part(f"cagecrys{i}", cone(cr, "salt", tuple(hp(3.3 * math.sin(a), 27.0, 0.4 - 3.3 * math.cos(a))), (math.sin(a) * 0.3, 1, -math.cos(a) * 0.3), 2.2 * S, 0.5 * S, sides=5, rings=2))
            for s in (-1, 1):
                for k in range(3):
                    F += part(f"shcrys{s}{k}", cone(side_bone(s, "arm"), "salt", tuple(hp(s * (4.0 + k * 0.6), 22.0, -0.4 + k * 0.6)), (s * 0.4, 1, 0.1), (1.6 + 0.4 * k) * S, 0.5 * S, sides=5, rings=2))
        elif name == "king_storm":
            for i in range(nv):
                a = 2 * math.pi * i / nv
                F += part(f"needle{i}", cone(cr, "copper", tuple(hp(3.6 * math.sin(a), 26.0, 0.4 - 3.6 * math.cos(a))), (math.sin(a) * 0.5, 1, -math.cos(a) * 0.5), (2.4 + (i % 2)) * S, 0.18 * S, sides=4, rings=1))
                if i % 2 == 0:
                    F += part(f"needle_tip{i}", ellipsoid(cr, "bolt", tuple(hp(4.6 * math.sin(a), 28.6 + (i % 2), 0.4 - 4.6 * math.cos(a))), (0.22 * S, 0.22 * S, 0.22 * S), segs=5, rings=2))
        elif name == "king_ash":
            for i in range(nv // 2):
                a = 2 * math.pi * (i * 2) / nv
                x, z = 3.5 * math.sin(a), 0.4 - 3.5 * math.cos(a)
                F += part(f"cup{i}", ellipsoid(cr, "iron", tuple(hp(x, 26.0, z)), (0.55 * S, 0.35 * S, 0.55 * S), segs=7, rings=2))
                F += part(f"flame{i}", cone(cr, "flame", tuple(hp(x, 26.2, z)), (0, 1, 0), 1.7 * S, 0.34 * S, sides=5, rings=2))
        elif name == "king_shadow":
            for s in (-1, 1):
                for k in range(4):
                    p0 = np.asarray(hp(s * (3.6 + k * 0.5), 21.0, -0.6 + k * 0.7))
                    F += part(f"thread{s}{k}", tube(c, "tar", [p0, p0 + np.asarray(hp(s * 0.6, -6.0, 0.3)), p0 + np.asarray(hp(s * 0.4, -12.0 + k, 0.8))], ra=0.16 * S, rb=0.06 * S, sides=4, caps=(True, True)))
            for i in range(nv // 2):
                a = 2 * math.pi * (i * 2 + 1) / nv
                F += part(f"shadowdrip{i}", cone(cr, "tar", tuple(hp(3.0 * math.sin(a), 23.0, 0.4 - 3.0 * math.cos(a))), (0, -1, 0), 2.2 * S, 0.3 * S, sides=4, rings=1))
        # 周が進むと: 背に骨の喪の扇 (1)、肩の金の棘と胸の割れ目の光 (2)
        if cycle >= 1:
            for i in range(5 + cycle):
                a = math.radians(-60 + i * (120 / (4 + cycle)))
                F += part(f"fan{i}", arc(c, "bone", hp(0, 19.0, 2.6), hp(4.0 * math.sin(a), 24.0 + 2.0 * math.cos(a), 4.0), hp(7.0 * math.sin(a), 27.0 + 4.0 * math.cos(a), 4.6),
                                         r=0.35 * S, n=5, taper=(0.4 * S, 0.08 * S), sides=4))
                F += part(f"fanlace{i}", ellipsoid(c, "lace", tuple(hp(5.0 * math.sin(a), 25.6 + 3.0 * math.cos(a), 4.4)), (0.9 * S, 0.9 * S, 0.12 * S), segs=6, rings=2))
        if cycle >= 2:
            for s in (-1, 1):
                F += part(f"shoulder_spike{s}", cone(side_bone(s, "arm"), "gold", tuple(hp(s * 4.6, 22.0, 0.3)), (s * 0.4, 1, 0.2), 2.6 * S, 0.5 * S, sides=5, rings=2))
            F += part("chest_rift", ellipsoid(c, glow, tuple(hp(0, 18.4, -2.45)), (0.4 * S, 1.6 * S, 0.2 * S), segs=6, rings=3))
        return F
    return f


KINGS_M = {n: (build(n), 52.0, dict(center=(0, 26, 0), scale=4.0)) for n in ELEM}


def make(name, cycle):
    import boss_kings as BK
    fn, h, kw = KINGS_M[name]
    F = fn(cycle)
    bs = dict(BK.ASH_BONES)
    if BK.WARP[cycle] > 0:
        F, bs = K.warp(F, BK.ASH_BONES, BK.WARP[cycle], h, lean=1.0)
    return K.export(f"{name}_c{cycle}", F, bs, tpu=2.5, crackglow=BK.CRACK[cycle], seed=cycle * 7.0, **kw)


def main(argv):
    names = [a for a in argv if not a.startswith("--") and not a.isdigit()]
    cycles = [int(a) for a in argv if a.isdigit()] or [0, 1, 2]
    for n in names or list(ELEM):
        for c in cycles:
            make(n, c)


if __name__ == "__main__":
    main(sys.argv[1:])
