#!/usr/bin/env python3
"""番人・淵の王・層の主の作り込みのラフ (2026-11-11)。
声「ラフの要素の足し方同じなのやめて」: 主ごとに、その主の持ち物・由来から別々の三案を考える (同じ足し方を使い回さない)。
今の模型 (11/08 に選んだ姿) はそのまま読み、足す部品だけを作って重ねて描く。

    python -X utf8 -I tools/rough/lowpoly/late_boss_horror/build.py [名前 ...]
出力: docs/review/late_boss_horror/<名前>.png
"""
from __future__ import annotations

import importlib.util
import math
import sys
from pathlib import Path

import numpy as np

HERE = Path(__file__).resolve().parent
spec_ = importlib.util.spec_from_file_location("heart_horror_build", HERE.parent / "heart_horror/build.py")
hh = importlib.util.module_from_spec(spec_)
spec_.loader.exec_module(hh)
from blp import *  # noqa: E402,F401,F403
import blp  # noqa: E402
from lpkit import render  # noqa: E402

OUT = ROOT / "docs/review/late_boss_horror"
V3 = np.array

PAL = {
    "bone": [(150, 140, 120), (196, 186, 164), (228, 220, 200), (244, 238, 224)],
    "rust": [(60, 30, 20), (96, 52, 32), (134, 80, 50), (170, 110, 72)],
    "iron": [(34, 32, 36), (58, 56, 60), (88, 86, 90), (124, 122, 126)],
    "gold": [(110, 80, 30), (160, 120, 50), (206, 166, 80), (236, 206, 130)],
    "maw": [(40, 6, 12), (80, 14, 22), (124, 26, 34), (168, 50, 50)],
    "flesh": [(60, 24, 26), (92, 40, 38), (126, 60, 52)],
    "salt": [(176, 184, 182), (212, 220, 216), (236, 242, 240), (250, 252, 250)],
    "brine": [(30, 110, 110), (60, 170, 160), (130, 230, 214)],
    "storm": [(90, 160, 255), (150, 200, 255), (220, 240, 255)],
    "fire": [(200, 70, 20), (250, 140, 40), (255, 210, 110)],
    "ash": [(40, 36, 34), (66, 60, 56), (96, 88, 82), (128, 120, 112)],
    "shadow": [(10, 8, 18), (22, 16, 36), (40, 30, 64), (70, 56, 104)],
    "cyan": [(40, 170, 180), (90, 220, 220), (180, 250, 248)],
    "violet": [(120, 80, 200), (170, 130, 240), (220, 200, 255)],
}
GLOW = {"storm", "fire", "cyan", "violet", "brine"}


class Ref:
    """今の模型の骨ごとの範囲"""

    def __init__(self, faces):
        self.faces = faces
        V = np.concatenate([f.verts for f in faces])
        self.lo, self.hi = V.min(0), V.max(0)
        self.c = (self.lo + self.hi) / 2
        self.H = float(self.hi[1] - self.lo[1])

    def box(self, *names):
        sel = [f for f in self.faces if f.bone in names]
        if not sel:
            return self.lo, self.hi, self.c
        V = np.concatenate([f.verts for f in sel])
        lo, hi = V.min(0), V.max(0)
        return lo, hi, (lo + hi) / 2


def chain(m, tag, a, b, w, mat="iron"):
    n = max(2, int(np.linalg.norm(b - a) / (w * 2.2)))
    for j in range(n):
        p = a + (b - a) * (j + 0.5) / n
        m.add(f"{tag}{j}", ell("root", mat, p, (w * 0.45, w, w * 0.45), segs=6, rings=2, ry=90 * (j % 2)))


def padlock(m, tag, p, s):
    m.add(tag + "b", box("root", "rust", p, (s * 1.6, s * 1.4, s * 0.7)))
    m.add(tag + "s", tub("root", "iron", [p + V3([-s * 0.5, s * 0.6, 0]), p + V3([-s * 0.5, s * 1.4, 0]), p + V3([s * 0.5, s * 1.4, 0]), p + V3([s * 0.5, s * 0.6, 0])],
                          [s * 0.18] * 4, None, sides=5))
    m.add(tag + "k", ell("root", "maw", p + V3([0, 0, -s * 0.36]), (s * 0.2, s * 0.35, s * 0.05), segs=6, rings=2))


def key(m, tag, base, d, L, s):
    d = nrm(np.asarray(d, float))
    m.add(tag + "shaft", tub("root", "iron", [base, base + d * L], [s * 0.18, s * 0.18], None, sides=5))
    m.add(tag + "bow", ell("root", "rust", base - d * s * 0.5, (s * 0.7, s * 0.7, s * 0.2), segs=8, rings=2))
    side = nrm(np.cross(d, [0, 0, 1.0]) + 1e-6)
    for j in range(2):
        m.add(tag + f"bit{j}", box("root", "iron", base + d * L * (0.8 + 0.1 * j) + side * s * 0.4, (s * 0.5, s * 0.3, s * 0.3)))


def teeth_ring(m, tag, c, r, n, L, inward=True, mat="bone"):
    for k in range(n):
        a = k / n * math.tau
        p = c + V3([math.cos(a) * r, math.sin(a) * r, 0])
        d = V3([-math.cos(a), -math.sin(a), -0.4]) if inward else V3([math.cos(a), math.sin(a), -0.2])
        m.add(f"{tag}{k}", cone("root", mat, p, d, L, L * 0.22, sides=4, rings=2))


# ======================================================================== 心臓の番人 (鍵の騎士・錆びた門番)
def warden_a(m, R):
    """A 鍵穴の胸: 胸が巨大な鍵穴の形に開き、奥で心臓が脈打つ。縁に牙"""
    lo, hi, c = R.box("body", "ribs", "heart")
    z = lo[2] - 0.6
    cy = c[1] + R.H * 0.04
    m.add("hole_ring", ell("root", "rust", V3([c[0], cy + 3, z]), (4.2, 4.2, 0.9), segs=12, rings=2))
    m.add("hole_slot", box("root", "rust", V3([c[0], cy - 3, z]), (3.6, 7.0, 1.6), taper=(0.55, 1.0)))
    m.add("hole_dark", ell("root", "maw", V3([c[0], cy + 3, z - 0.3]), (3.1, 3.1, 0.6), segs=12, rings=2))
    m.add("hole_dark2", box("root", "maw", V3([c[0], cy - 2.6, z - 0.4]), (2.4, 6.0, 0.6), taper=(0.5, 1.0)))
    m.add("heartbeat", ell("root", "flesh", V3([c[0], cy + 3, z + 0.4]), (2.2, 2.6, 1.6), segs=10, rings=3))
    teeth_ring(m, "keytooth", V3([c[0], cy + 3, z - 0.6]), 3.3, 10, 1.6)


def warden_b(m, R):
    """B 吊るされた錠前: 肩と腰から鎖で錠前がいくつも垂れ、背に鎖の束"""
    lo, hi, c = R.box("body", "ribs")
    for k, (x, y, z) in enumerate([(-9, 44, -1), (9, 44, -1), (-7, 30, -3), (7, 30, -3), (0, 26, -5), (-10, 36, 2), (10, 36, 2)]):
        top = V3([x, y, z])
        L = 6 + (k % 3) * 3
        chain(m, f"lch{k}", top, top + V3([0, -L, 0]), 0.7)
        padlock(m, f"lock{k}", top + V3([0, -L - 1.6, 0]), 1.3 + 0.2 * (k % 2))
    for s in (1, -1):
        chain(m, f"back{s}", V3([s * 8, 48, 4]), V3([-s * 6, 22, 6]), 0.9)


def warden_c(m, R):
    """C 鍵の後光: 頭の後ろに、錆びた鍵が扇のように並ぶ後光"""
    lo, hi, c = R.box("head")
    hc = V3([c[0], c[1], hi[2] + 2.0])
    ring = [hc + V3([math.cos(t) * 10, math.sin(t) * 10, 0]) for t in np.linspace(0, math.tau, 25)]
    m.add("halo", tub("root", "rust", ring, [0.7] * len(ring), None, sides=5))
    for k in range(11):
        a = math.radians(-10 + k * 20)
        d = V3([math.cos(a), math.sin(a), 0])
        key(m, f"hkey{k}", hc + d * 10.5, d, 8 + (k % 3) * 3, 1.5)


# ======================================================================== 淵の王 (王冠の籠・紫の衣、五つの王で体は同じ)
def king_parts(R):
    hlo, hhi, hc = R.box("head", "crown")
    clo, chi, cc = R.box("chest", "body")
    return hlo, hhi, hc, clo, chi, cc


def bone_a(m, R):
    """骨の王 A 籠の中の頭蓋: 王冠の籠の中に、大きな顎の頭蓋が閉じ込められ、顎が外れて垂れる"""
    hlo, hhi, hc, *_ = king_parts(R)
    m.add("skull", ell("root", "bone", hc + V3([0, 1, 0]), (4.2, 4.4, 4.2), segs=10, rings=4))
    for s in (1, -1):
        m.add(f"socket{s}", ell("root", "maw", hc + V3([s * 1.6, 1.6, -3.6]), (1.2, 1.3, 0.8), segs=8, rings=2))
    m.add("jaw", box("root", "bone", hc + V3([0, -4.6, -1.6]), (5.6, 1.6, 4.6), taper=(0.8, 1.0)))
    for k in range(6):
        m.add(f"jt{k}", cone("root", "bone", hc + V3([-2.2 + k * 0.9, -3.9, -3.6]), (0, 1, 0), 1.4, 0.3, sides=4, rings=1))


def bone_b(m, R):
    """骨の王 B 玉座の背骨: 背から玉座の背もたれのように、太い背骨と肋が扇に立つ"""
    *_, clo, chi, cc = king_parts(R)
    root = V3([cc[0], chi[1] - 4, chi[2] + 1.5])
    m.add("spine", tub("root", "bone", [root + V3([0, -10, 0]), root + V3([0, 6, 2]), root + V3([0, 22, 1])], [2.0, 1.8, 1.2], None, sides=7))
    for k in range(9):
        t = (k - 4) / 4
        top = root + V3([t * 16, 16 + (1 - abs(t)) * 10, 4 - abs(t) * 2])
        m.add(f"throne{k}", tub("root", "bone", [root + V3([t * 3, 4 + k * 0.5, 1]), (root + top) / 2 + V3([t * 3, 3, 2]), top], [1.1, 0.9, 0.4], None, sides=5))
        m.add(f"tip{k}", cone("root", "bone", top, (t * 0.4, 1, 0.2), 3.0, 0.5, sides=4))


def bone_c(m, R):
    """骨の王 C 衣を破る肋: 胸の衣が裂け、肋骨が前へ突き出て獣の顎のように閉じる"""
    *_, clo, chi, cc = king_parts(R)
    z = clo[2] - 0.4
    m.add("tear", ell("root", "maw", V3([cc[0], cc[1] + 4, z + 0.4]), (4.4, 7.0, 1.0), segs=12, rings=3))
    for k in range(5):
        y = cc[1] + 9 - k * 2.6
        for s in (1, -1):
            m.add(f"rib{k}{s}", tub("root", "bone", [V3([cc[0] + s * 0.8, y, z + 0.6]), V3([cc[0] + s * 5.5, y + 0.4, z - 2.5]),
                                                      V3([cc[0] + s * 2.4, y - 0.6, z - 5.0])], [0.55, 0.5, 0.25], None, sides=5))


def salt_a(m, R):
    """塩の王 A 塩の晶の冠: 王冠の籠と肩から、白い塩の結晶が群れて伸びる"""
    hlo, hhi, hc, clo, chi, cc = king_parts(R)
    rng = np.random.default_rng(1)
    for k in range(14):
        base = (hc + V3([rng.uniform(-3, 3), 2, rng.uniform(-2, 2)])) if k < 7 else V3([cc[0] + (1 if k % 2 else -1) * rng.uniform(6, 9), chi[1] - 1, cc[2] + rng.uniform(-2, 2)])
        d = V3([rng.uniform(-0.6, 0.6), 1, rng.uniform(-0.4, 0.4)])
        m.add(f"salt{k}", cone("root", "salt", base, d, rng.uniform(4, 9), rng.uniform(0.8, 1.6), sides=6, rings=2))


def salt_b(m, R):
    """塩の王 B 塩の柱の裳裾: 衣の裾が塩の柱になって床に根を張り、王を床に縫い止める"""
    lo, hi = R.lo, R.hi
    rng = np.random.default_rng(2)
    for k in range(12):
        a = k / 12 * math.tau
        r = 9 + rng.uniform(-1, 2)
        p = V3([R.c[0] + math.cos(a) * r, 0, R.c[2] + math.sin(a) * r * 0.8])
        h = rng.uniform(8, 18)
        m.add(f"pillar{k}", tub("root", "salt", [p + V3([0, -1, 0]), p + V3([math.cos(a) * -2, h * 0.6, math.sin(a) * -2]), V3([R.c[0] + math.cos(a) * 6, h, R.c[2] + math.sin(a) * 5])],
                                 [2.0, 1.5, 0.8], None, sides=6))
    m.add("pool", ell("root", "brine", V3([R.c[0], 0.3, R.c[2]]), (13, 0.5, 11), segs=16, rings=1))


def salt_c(m, R):
    """塩の王 C 塩の涙の面紗: 顔の前に塩の面紗が垂れ、目から流れた塩の跡が衣まで固まる"""
    hlo, hhi, hc, clo, chi, cc = king_parts(R)
    z = hlo[2] - 0.6
    for k in range(9):
        x = hc[0] - 4 + k
        L = 6 + (k % 3) * 2
        m.add(f"veil{k}", tub("root", "salt", [V3([x, hc[1] + 3, z]), V3([x, hc[1] - L * 0.5, z - 0.6]), V3([x + 0.2, hc[1] - L, z - 0.2])], [0.35, 0.3, 0.15], None, sides=4))
    for s in (1, -1):
        m.add(f"tear{s}", tub("root", "brine", [V3([hc[0] + s * 1.6, hc[1] + 1, z - 0.8]), V3([hc[0] + s * 2.2, hc[1] - 6, z - 1.2]),
                                                 V3([hc[0] + s * 2.8, cc[1] - 2, clo[2] - 1.0])], [0.45, 0.4, 0.3], None, sides=5))


def storm_a(m, R):
    """雷の王 A 雷の輪: 体のまわりを三つの光る輪が斜めに巡る"""
    for k in range(3):
        y = R.H * (0.35 + 0.22 * k)
        m.add(f"ring{k}", ell("root", "storm", V3([R.c[0], y, R.c[2]]), (12 - k * 1.5, 0.5, 12 - k * 1.5), segs=20, rings=1, rx=18 - k * 18, rz=12 * (k - 1)))
        for j in range(4):
            a = j * math.pi / 2 + k
            m.add(f"node{k}{j}", ell("root", "storm", V3([R.c[0] + math.cos(a) * (12 - k * 1.5), y, R.c[2] + math.sin(a) * (12 - k * 1.5)]), (1, 1, 1), segs=6, rings=2))


def storm_b(m, R):
    """雷の王 B 避雷の角: 肩と冠から鉄の避雷針が角のように突き出て、先に光の玉"""
    hlo, hhi, hc, clo, chi, cc = king_parts(R)
    spots = [(hc + V3([0, 4, 0]), V3([0, 1, 0]), 12)] + [(V3([cc[0] + s * 7, chi[1], cc[2]]), V3([s * 0.6, 1, 0.1]), 10) for s in (1, -1)] + \
            [(V3([cc[0] + s * 4, chi[1] - 2, chi[2]]), V3([s * 0.3, 1, 0.6]), 8) for s in (1, -1)]
    for k, (b, d, L) in enumerate(spots):
        m.add(f"rod{k}", cone("root", "iron", b, d, L, 0.7, sides=6, rings=3))
        m.add(f"orb{k}", ell("root", "storm", b + nrm(d) * L, (1.2, 1.2, 1.2), segs=8, rings=3))
        for j in range(3):
            m.add(f"coil{k}{j}", ell("root", "gold", b + nrm(d) * L * (0.25 + j * 0.2), (1.1 - j * 0.2, 0.25, 1.1 - j * 0.2), segs=8, rings=1))


def storm_c(m, R):
    """雷の王 C 胸の稲妻の裂け目: 胸から腹へ、光る稲妻の形に体が裂けている"""
    *_, clo, chi, cc = king_parts(R)
    z = clo[2] - 0.3
    pts = [V3([cc[0] + x, cc[1] + y, z]) for x, y in ((1, 10), (-2, 6), (1.5, 3), (-1.5, -1), (1, -4), (-0.5, -8))]
    for k in range(len(pts) - 1):
        m.add(f"bolt{k}", tub("root", "storm", [pts[k], pts[k + 1]], [0.9, 0.8], None, sides=5))
        m.add(f"edge{k}", tub("root", "maw", [pts[k] + V3([0.9, 0, 0.3]), pts[k + 1] + V3([0.9, 0, 0.3])], [0.5, 0.5], None, sides=4))


def ash_a(m, R):
    """灰の王 A 燃える冠: 王冠の籠の中で火が燃え、火の舌が籠からはみ出す"""
    hlo, hhi, hc, *_ = king_parts(R)
    m.add("core", ell("root", "fire", hc + V3([0, 1, 0]), (3.6, 4.0, 3.6), segs=10, rings=3))
    for k in range(9):
        a = k / 9 * math.tau
        m.add(f"flame{k}", cone("root", "fire", hc + V3([math.cos(a) * 2.6, 3, math.sin(a) * 2.6]), (math.cos(a) * 0.4, 1, math.sin(a) * 0.4), 5 + (k % 3) * 2, 1.0,
                                sides=5, rings=3, bend=(math.sin(a) * 1.4, 0, -math.cos(a) * 1.4)))


def ash_b(m, R):
    """灰の王 B 背の煙突: 背中から熔けた煙突が三本伸び、先から火が漏れる"""
    *_, clo, chi, cc = king_parts(R)
    for k, x in enumerate((-4, 0, 4)):
        b = V3([cc[0] + x, chi[1] - 6, chi[2] + 1])
        top = b + V3([x * 0.6, 14 + (1 - abs(x) / 4) * 6, 4])
        m.add(f"stack{k}", tub("root", "ash", [b, (b + top) / 2 + V3([0, 0, 1]), top], [1.8, 1.6, 1.4], None, sides=7))
        m.add(f"mouth{k}", ell("root", "fire", top + V3([0, 0.3, 0]), (1.2, 0.4, 1.2), segs=8, rings=1))
        m.add(f"band{k}", ell("root", "iron", (b + top) / 2, (2.0, 0.5, 2.0), segs=8, rings=1))


def ash_c(m, R):
    """灰の王 C 灰の翼: 背から燃え残った布と骨の翼が垂れ下がる (縁は燠で光る)"""
    *_, clo, chi, cc = king_parts(R)
    root = V3([cc[0], chi[1] - 3, chi[2] + 1])
    for s in (1, -1):
        for k in range(4):
            tip = root + V3([s * (8 + k * 3), -k * 5 + 4, 3 + k])
            m.add(f"wbone{s}{k}", tub("root", "ash", [root, (root + tip) / 2 + V3([s * 1, 3, 1]), tip], [0.8, 0.6, 0.3], None, sides=5))
            m.add(f"wtip{s}{k}", ell("root", "fire", tip, (0.8, 0.8, 0.8), segs=6, rings=2))
        m.add(f"cloth{s}", slab("root", "ash", [root, root + V3([s * 8, 4, 3]), root + V3([s * 17, -11, 6]), root + V3([s * 4, -14, 2])], (0, 0, 1), 0.5))


def shadow_a(m, R):
    """影の王 A 背の影の腕: 背中から細長い黒い腕が何本も伸び、指が鉤に曲がる"""
    *_, clo, chi, cc = king_parts(R)
    root = V3([cc[0], chi[1] - 4, chi[2]])
    for k in range(6):
        s = 1 if k % 2 else -1
        lvl = k // 2
        el = root + V3([s * (7 + lvl * 2), 8 - lvl * 6, 6])
        hand = el + V3([s * 4, -6 - lvl * 2, -4])
        m.add(f"arm{k}", tub("root", "shadow", [root + V3([0, -lvl * 3, 0]), el, hand], [0.9, 0.7, 0.5], None, sides=5))
        for j in range(4):
            m.add(f"fing{k}{j}", cone("root", "shadow", hand, (s * 0.3 + (j - 1.5) * 0.3, -1, -0.5), 3.0, 0.25, sides=4, rings=2, bend=(0, 0, -1.2)))


def shadow_b(m, R):
    """影の王 B 浮かぶ仮面の群れ: 王のまわりに白い仮面が浮かび、どれも王の方を向く"""
    for k in range(8):
        a = k / 8 * math.tau
        p = V3([R.c[0] + math.cos(a) * 11, R.H * (0.55 + 0.15 * math.sin(a * 2)), R.c[2] + math.sin(a) * 9])
        face = nrm(R.c + V3([0, R.H * 0.1, 0]) - p)
        yaw = math.degrees(math.atan2(face[0], face[2]))
        m.add(f"mask{k}", ell("root", "salt", p, (1.8, 2.4, 0.6), segs=10, rings=3, ry=yaw))
        for s in (1, -1):
            m.add(f"mhole{k}{s}", ell("root", "shadow", p + face * 0.5 + nrm(np.cross(face, [0, 1, 0])) * s * 0.7 + V3([0, 0.5, 0]), (0.35, 0.45, 0.2), segs=6, rings=2, ry=yaw))


def shadow_c(m, R):
    """影の王 C 裾の触手: 衣の裾がほどけて影の触手になり、床を這って広がる"""
    rng = np.random.default_rng(4)
    for k in range(10):
        a = k / 10 * math.tau
        b = V3([R.c[0] + math.cos(a) * 5, 2, R.c[2] + math.sin(a) * 4])
        mid = b + V3([math.cos(a) * 6, -1.2, math.sin(a) * 6])
        tip = mid + V3([math.cos(a + 0.6) * 6, 0, math.sin(a + 0.6) * 6])
        m.add(f"tent{k}", tub("root", "shadow", [b + V3([0, 2, 0]), mid, tip], [1.6, 1.0, 0.2], None, sides=6))


# ======================================================================== 層の主 (金継ぎの黒い体)
def rib_a(m, R):
    """肋の主 A 灯籠の頭: 頭が肋の籠の灯籠になり、中で青い火が燃える"""
    lo, hi, c = R.box("head", "jaw")
    hc = c + V3([0, 2, 0])
    m.add("flame", ell("root", "cyan", hc, (2.6, 3.4, 2.6), segs=10, rings=3))
    for k in range(8):
        a = k / 8 * math.tau
        m.add(f"bar{k}", tub("root", "bone", [hc + V3([math.cos(a) * 2, -4, math.sin(a) * 2]), hc + V3([math.cos(a) * 3.8, 0, math.sin(a) * 3.8]),
                                              hc + V3([math.cos(a) * 1.2, 5, math.sin(a) * 1.2])], [0.35, 0.4, 0.25], None, sides=4))
    m.add("cap", cone("root", "bone", hc + V3([0, 5, 0]), (0, 1, 0), 4, 1.4, sides=6, rings=2))


def rib_b(m, R):
    """肋の主 B 背骨の尾: 腰から長い背骨の尾が床を這い、先に鎌の骨"""
    lo, hi, c = R.box("body")
    b = V3([c[0], c[1], hi[2]])
    pts = [b, b + V3([0, -6, 6]), b + V3([3, -12, 14]), b + V3([-2, -16, 22]), b + V3([-6, -14, 30])]
    pts = [V3([p[0], max(1.0, p[1]), p[2]]) for p in pts]
    for k in range(len(pts) - 1):
        for j in range(3):
            p = pts[k] + (pts[k + 1] - pts[k]) * j / 3
            m.add(f"vert{k}{j}", ell("root", "bone", p, (1.2 - k * 0.15, 1.0, 1.2 - k * 0.15), segs=6, rings=2))
            m.add(f"vspike{k}{j}", cone("root", "bone", p, (0, 1, 0.3), 1.6, 0.35, sides=4, rings=1))
    m.add("scythe", cone("root", "bone", pts[-1], (-1, 0.8, 0.2), 9, 1.0, sides=5, rings=3, bend=(0, -2.5, -2)))


def rib_c(m, R):
    """肋の主 C 増えた腕: 脇腹からもう二対の細い骨の腕が生え、灯りを掲げる"""
    lo, hi, c = R.box("chest")
    for s in (1, -1):
        for k in range(2):
            sh = V3([c[0] + s * 4, c[1] - 2 - k * 5, c[2]])
            el = sh + V3([s * 7, 2 - k * 2, -2])
            hand = el + V3([s * 2, 7 - k * 3, -3])
            m.add(f"xarm{s}{k}", tub("root", "bone", [sh, el, hand], [0.7, 0.55, 0.4], None, sides=5))
            m.add(f"xlamp{s}{k}", ell("root", "cyan", hand + V3([0, 1.4, 0]), (0.9, 1.2, 0.9), segs=8, rings=2))


def salt_lord_a(m, R):
    """白殻の主 A 殻の塩の尖塔: 殻の渦から塩の尖塔がいくつも伸び、聖堂のように立つ"""
    lo, hi, c = R.box("shell")
    rng = np.random.default_rng(6)
    for k in range(9):
        b = V3([c[0] + rng.uniform(-6, 6), hi[1] - 3, c[2] + rng.uniform(-6, 6)])
        h = rng.uniform(8, 18)
        m.add(f"spire{k}", cone("root", "salt", b, (rng.uniform(-0.15, 0.15), 1, rng.uniform(-0.15, 0.15)), h, 1.6, sides=6, rings=3))
        m.add(f"sring{k}", ell("root", "brine", b + V3([0, h * 0.35, 0]), (1.4, 0.3, 1.4), segs=8, rings=1))


def salt_lord_b(m, R):
    """白殻の主 B 眼柄の群れ: 頭のまわりから細い眼柄が十本ほど伸び、先の目がばらばらを向く"""
    lo, hi, c = R.box("head", "stalk_l", "stalk_r")
    rng = np.random.default_rng(7)
    for k in range(10):
        a = rng.uniform(0, math.tau)
        b = c + V3([math.cos(a) * 2, 1, math.sin(a) * 2])
        tip = b + V3([math.cos(a) * rng.uniform(3, 6), rng.uniform(5, 10), math.sin(a) * rng.uniform(3, 6)])
        m.add(f"stalk{k}", tub("root", "salt", [b, (b + tip) / 2 + V3([0, 1, 0]), tip], [0.45, 0.35, 0.3], None, sides=5))
        eye(m, "root", tip, 0.9, tuple(nrm(tip - b)), (120, 240, 220), glow=True, lid=(30, 60, 60))


def salt_lord_c(m, R):
    """白殻の主 C 殻口の牙: 殻の口の縁に塩の牙が並び、殻そのものが顎になる"""
    lo, hi, c = R.box("shell")
    mc = V3([c[0], c[1] - 2, lo[2] + 2])
    m.add("mouth", ell("root", "maw", mc, (6, 5, 1.5), segs=14, rings=3))
    for k in range(14):
        a = k / 14 * math.tau
        p = mc + V3([math.cos(a) * 6.2, math.sin(a) * 5.2, -0.5])
        m.add(f"fang{k}", cone("root", "salt", p, (-math.cos(a) * 0.8, -math.sin(a) * 0.8, -0.6), 3.2, 0.6, sides=4, rings=2))


def storm_lord_a(m, R):
    """鼓動まねの主 A 鼓動の管: 体を太い管 (血管) が締め上げ、脈打つ瘤が並ぶ"""
    lo, hi, c = R.box("body", "heart")
    r = float(max(hi[0] - lo[0], hi[2] - lo[2])) / 2
    for k in range(5):
        a0 = k / 5 * math.tau
        pts = [V3([c[0] + math.cos(a0 + t * 2.4) * r * 1.02, lo[1] + 4 + t * (hi[1] - lo[1] - 6), c[2] + math.sin(a0 + t * 2.4) * r * 1.02]) for t in np.linspace(0, 1, 6)]
        m.add(f"vessel{k}", tub("root", "flesh", pts, [1.2] * 6, None, sides=6))
        for j in (1, 3):
            m.add(f"node{k}{j}", ell("root", "violet", pts[j], (1.8, 1.8, 1.8), segs=8, rings=2))


def storm_lord_b(m, R):
    """鼓動まねの主 B 背の鼓: 背に皮を張った太鼓が三つ並び、骨の撥が刺さる"""
    lo, hi, c = R.box("body")
    for k, x in enumerate((-8, 0, 8)):
        p = V3([c[0] + x, hi[1] - 2 - abs(x) * 0.4, c[2] + 6])
        m.add(f"drum{k}", tub("root", "flesh", [p + V3([0, -2.5, 0]), p + V3([0, 2.5, 0])], [4.0, 4.0], None, sides=12))
        m.add(f"rim{k}", ell("root", "bone", p + V3([0, 2.6, 0]), (4.3, 0.5, 4.3), segs=12, rings=1))
        m.add(f"stick{k}", cone("root", "bone", p + V3([1, 3, -1]), (0.3, 1, -0.4), 7, 0.5, sides=4, rings=2))


def storm_lord_c(m, R):
    """鼓動まねの主 C 這い出る舌: 口から長い舌が床へ垂れ、先が三つに裂けて光る"""
    lo, hi, c = R.box("jaw", "heart")
    b = V3([c[0], c[1], lo[2]])
    pts = [b, b + V3([0, -3, -5]), b + V3([1, -9, -10]), V3([b[0] - 1, 1.0, b[2] - 15])]
    m.add("tongue", tub("root", "flesh", pts, [2.0, 1.8, 1.4, 1.0], None, sides=7))
    for k in range(3):
        a = (k - 1) * 0.6
        m.add(f"fork{k}", cone("root", "violet", pts[-1], (math.sin(a), 0, -math.cos(a)), 4, 0.6, sides=4, rings=2))


def ash_lord_a(m, R):
    """灯り喰いの主 A 呑んだ灯籠: 背から、喰った灯籠がいくつも突き刺さって燠火を灯す"""
    lo, hi, c = R.box("body")
    rng = np.random.default_rng(8)
    for k in range(7):
        b = V3([c[0] + rng.uniform(-10, 10), hi[1] - 2, c[2] + rng.uniform(-6, 8)])
        d = V3([rng.uniform(-0.4, 0.4), 1, rng.uniform(-0.3, 0.3)])
        top = b + nrm(d) * 6
        m.add(f"post{k}", tub("root", "iron", [b, top], [0.4, 0.4], None, sides=5))
        m.add(f"lamp{k}", box("root", "iron", top + V3([0, 1.4, 0]), (2.2, 2.6, 2.2)))
        m.add(f"lfire{k}", ell("root", "fire", top + V3([0, 1.4, 0]), (0.9, 1.1, 0.9), segs=8, rings=2))


def ash_lord_b(m, R):
    """灯り喰いの主 B 燠の腹: 腹の下に、喰った火を溜めた光る袋が垂れる"""
    lo, hi, c = R.box("body")
    m.add("sac", ell("root", "fire", V3([c[0], lo[1] + 6, c[2] - 2]), (11, 9, 11), segs=14, rings=4))
    for k in range(8):
        a = k / 8 * math.tau
        m.add(f"strap{k}", tub("root", "ash", [V3([c[0] + math.cos(a) * 9, lo[1] + 13, c[2] - 2 + math.sin(a) * 9]), V3([c[0] + math.cos(a) * 11.6, lo[1] + 6, c[2] - 2 + math.sin(a) * 11.6]),
                                               V3([c[0] + math.cos(a) * 5, lo[1] - 2.5, c[2] - 2 + math.sin(a) * 5])], [0.8, 0.8, 0.5], None, sides=4))


def ash_lord_c(m, R):
    """灯り喰いの主 C 鎌の前脚: 前に大きな二本の鎌の脚が増え、灰をかき寄せる構え"""
    lo, hi, c = R.box("body", "jaw")
    for s in (1, -1):
        sh = V3([c[0] + s * 7, c[1] + 4, lo[2] + 3])
        el = sh + V3([s * 6, 9, -6])
        tip = el + V3([s * -2, -12, -8])
        m.add(f"sleg{s}", tub("root", "ash", [sh, el, tip], [1.4, 1.1, 0.8], None, sides=6))
        m.add(f"blade{s}", cone("root", "bone", tip, (-s * 0.3, -0.6, 1), 10, 1.4, sides=4, rings=3, bend=(0, 3, 2)))
        for j in range(4):
            m.add(f"serr{s}{j}", cone("root", "bone", tip + V3([0, j * 0.6, j * 2.2]), (s * 0.6, 1, 0), 1.6, 0.35, sides=4))


BOSSES = {
    "heart_warden": ("心臓の番人", [("A 鍵穴の胸", warden_a), ("B 吊るされた錠前", warden_b), ("C 鍵の後光", warden_c)]),
    "king_bone_c0": ("骨の王", [("A 籠の中の頭蓋", bone_a), ("B 玉座の背骨", bone_b), ("C 衣を破る肋", bone_c)]),
    "king_salt_c0": ("塩の王", [("A 塩の晶の冠", salt_a), ("B 塩の柱の裳裾", salt_b), ("C 塩の涙の面紗", salt_c)]),
    "king_storm_c0": ("雷の王", [("A 雷の輪", storm_a), ("B 避雷の角", storm_b), ("C 胸の稲妻の裂け目", storm_c)]),
    "king_ash_c0": ("灰の王", [("A 燃える冠", ash_a), ("B 背の煙突", ash_b), ("C 灰の翼", ash_c)]),
    "king_shadow_c0": ("影の王", [("A 背の影の腕", shadow_a), ("B 浮かぶ仮面の群れ", shadow_b), ("C 裾の触手", shadow_c)]),
    "lord_rib": ("肋の主", [("A 灯籠の頭", rib_a), ("B 背骨の尾", rib_b), ("C 増えた腕", rib_c)]),
    "lord_salt": ("白殻の主", [("A 殻の塩の尖塔", salt_lord_a), ("B 眼柄の群れ", salt_lord_b), ("C 殻口の牙", salt_lord_c)]),
    "lord_storm": ("鼓動まねの主", [("A 鼓動の管", storm_lord_a), ("B 背の鼓", storm_lord_b), ("C 這い出る舌", storm_lord_c)]),
    "lord_ash": ("灯り喰いの主", [("A 呑んだ灯籠", ash_lord_a), ("B 燠の腹", ash_lord_b), ("C 鎌の前脚", ash_lord_c)]),
}


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    F = voxel.font
    names = [a for a in sys.argv[1:] if not a.startswith("--")] or list(BOSSES)
    for name in names:
        title, variants = BOSSES[name]
        base = hh.load_base(name)
        R = Ref(base[0])
        cols = [("今の姿", base)]
        allV = [R.lo, R.hi]
        for label, fn in variants:
            m = Mount("lbh_" + name, title)
            fn(m, R)
            m.pal = dict(mats={"eye": M_(EYE, base=0.5, lit=0.2, noise=0.0, min=0), **{k: (M_(v, base=0.75, lit=0.15, min=1) if k in GLOW else M_(v, lit=0.36))
                                                                                    for k, v in PAL.items()}}, glow_mats=sorted(GLOW))
            size, atlas, glow = blp.paint_mount(m, m.pal, None, 256)
            V = np.concatenate([f.verts for f in m.F])
            allV += [V.min(0), V.max(0)]
            cols.append((label, hh.merge(base, (m.F, atlas, glow))))
        lo, hi = np.min(allV, axis=0), np.max(allV, axis=0)
        c = (lo + hi) / 2
        Rf = float(max(hi[0] - lo[0], hi[1] - lo[1], hi[2] - lo[2])) / 2
        PW, PH = 360, 400
        cv = Image.new("RGB", (20 + len(cols) * (PW + 10), 80 + 2 * (PH + 10)), (16, 16, 22))
        d = ImageDraw.Draw(cv)
        d.text((20, 14), f"{title} — 作り込みのラフ (主ごとの三案)", font=F(26), fill=(236, 232, 218))
        for i, (label, (faces, atlas, glow)) in enumerate(cols):
            x = 20 + i * (PW + 10)
            d.text((x, 50), label, font=F(18), fill=(150, 232, 255))
            for j, (yaw, pitch) in enumerate(((180, 6), (215, 12))):
                im = render(faces, atlas[..., :3], glow, yaw, pitch, (PW, PH), c, PW * 0.46 / Rf)
                bgi = Image.new("RGBA", (PW, PH), (28, 28, 38, 255))
                bgi.alpha_composite(im)
                cv.paste(bgi.convert("RGB"), (x, 80 + j * (PH + 10)))
        cv.save(OUT / f"{name}.png")
        print(name, "ok", flush=True)


if __name__ == "__main__":
    sys.stdout.reconfigure(encoding="utf-8")
    main()
