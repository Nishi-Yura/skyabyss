# -*- coding: utf-8 -*-
"""あいだに座すもの (tower_master) 作り直しのラフ 3 案の共通部品 (2026-11-02 の声「ボスのもっとモデルやアニメーション、攻撃表現もっと凝っていい」)。
手本 Wynncraft の Annihilation の合図を形に持たせる:
  index_r  右の人さし指 (星屑の雨・天の三矢で指を立てる)
  palm_l   左の掌の印 (天と淵の群れで掌を掲げる。ふだんは大きさ 0)
  sigil    角の上の印 (引き合いの高まりで角の上に開く。ふだんは大きさ 0)
  jaw      口 (落ちる月を呼ぶときに開く)
  rift     背の「あいだの門」(倒れると腕を伸ばしたまま門へ引き戻される)
座標は前の一案 (tower_master.py) と同じ: 1 ブロック = 16 で書き、LIFT だけ上と後ろへずらし、SCALE (1/5) で縮める。正面は -Z。
両手の置き場 (橋の北の広場) と顔・胸の高さは前と同じにしてある (TowerMaster.head / chest と舞台がそのまま合う)。
"""
import math
import sys
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).resolve().parent))
from mk import *  # noqa
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from lpkit import _mk  # noqa

STONE = [(22, 20, 28), (38, 36, 46), (58, 56, 68), (84, 82, 96)]
VOIDG = [(120, 70, 210), (160, 110, 240), (200, 160, 255), (235, 215, 255)]
ROBEW = [(176, 172, 160), (208, 204, 192), (230, 228, 218), (246, 244, 236)]
MARBLE = [(150, 146, 138), (190, 186, 176), (220, 216, 206), (240, 238, 230), (252, 250, 244)]
TEETH = [(150, 140, 120), (196, 186, 160), (226, 218, 196), (246, 240, 222)]

SCALE = 0.2
LIFT = (0, 9 * 16, 12 * 16)
_BONES = {"root": (None, (0, 0, 0)), "legs": ("root", (0, 0, 0)), "body": ("root", (0, 60, 0)),
          "core": ("body", (0, 250, -96)), "head": ("body", (0, 360, -10)), "jaw": ("head", (0, 392, -40)),
          "sigil": ("head", (0, 640, -10)), "halo": ("body", (0, 410, 110)),
          "arm_l": ("body", (-150, 320, -10)), "fore_l": ("arm_l", (-205, 120, -120)), "hand_l": ("fore_l", (-210, -110, -330)),
          "palm_l": ("hand_l", (-210, -168, -385)),
          "arm_r": ("body", (150, 320, -10)), "fore_r": ("arm_r", (205, 120, -120)), "hand_r": ("fore_r", (210, -110, -330)),
          "index_r": ("hand_r", (166, -136, -432)), "fingers_r": ("hand_r", (225, -136, -432)),
          "rift": ("root", (0, 420, 300))}
BONES = {k: (v[0], tuple((c + LIFT[i]) * SCALE for i, c in enumerate(v[1]))) for k, v in _BONES.items()}

BASE_MATS = {
    "stone": (STONE, 0.5, dict(crack=0.10, noise=0.22)),
    "robe": (ROBEW, 0.62, dict(crack=0.03, noise=0.14)),
    "marble": (MARBLE, 0.64, dict(crack=0.05, noise=0.12)),
    "gold": (GOLDW, 0.72, dict(crack=0.03, noise=0.12)),
    "veil": (PLASTER, 0.6, dict(crack=0.02, noise=0.10)),
    "hollow": (HOLLOW, 0.4, dict(crack=0.0, noise=0.04, min=0)),
    "void": (VOIDG, 0.85, dict(crack=0.0, noise=0.05, min=1)),
    "dawn": (DAWN, 0.85, dict(crack=0.0, noise=0.05, min=1)),
    "iron": (IRON, 0.5, dict(crack=0.04, noise=0.14)),
    "teeth": (TEETH, 0.62, dict(crack=0.04, noise=0.10)),
}
PAL = make_pal(BASE_MATS, glow=["void", "dawn"])


def V3(*a):
    return np.array(a, float)


# ---------------------------------------------------------------- 輪と筒
def oring(y, rx, zf, zb, n=20, cx=0.0, zc=0.0, fold=0.0, fn=8, fph=0.0, front_only=False):
    """前 (-Z) が 0 番の輪。fold = ひだの深さ (半径の割合)、fn = ひだの数。front_only なら前半分だけにひだ"""
    pts = []
    for k in range(n):
        a = 2 * math.pi * k / n
        s, c = math.sin(a), math.cos(a)
        m = 1.0
        if fold:
            w = 1.0 if not front_only else max(0.0, c)
            m += fold * w * math.sin(fn * a + fph)
        z = zc + (-zf * c if c >= 0 else zb * -c) * m
        pts.append(np.array([cx + rx * s * m, y, z]))
    return pts


def vring(cx, cy, z, r, n=24, ry=None):
    """縦の輪 (正面を向く)。0 番は真上"""
    ry = r if ry is None else ry
    return [np.array([cx + r * math.sin(2 * math.pi * k / n), cy + ry * math.cos(2 * math.pi * k / n), z]) for k in range(n)]


def hoop(bone, mat, y, rx, rz, cx=0.0, zc=0.0, r=4.0, n=16):
    pts = [(cx + math.cos(a) * rx, y, zc + math.sin(a) * rz) for a in [i / n * 2 * math.pi for i in range(n + 1)]]
    return tube(bone, mat, pts, ra=r, sides=4, hint=(0, 1, 0))


def vhoop(bone, mat, cx, cy, z, R, r=4.0, n=24, a0=0.0, a1=360.0, ry=None):
    """縦の輪 (正面に向いた円弧)"""
    ry = R if ry is None else ry
    m = max(2, int(n * abs(a1 - a0) / 360.0))
    pts = [(cx + math.sin(math.radians(a0 + (a1 - a0) * i / m)) * R, cy + math.cos(math.radians(a0 + (a1 - a0) * i / m)) * ry, z) for i in range(m + 1)]
    return tube(bone, mat, pts, ra=r, sides=4, hint=(0, 0, 1))


def chain(bone, x, y0, y1, z, step=24.0, r=6.0, mat="iron"):
    f = []
    k = 0
    y = y0
    while y > y1:
        if k % 2 == 0:
            f += part(f"ch{bone}{x:.0f}{z:.0f}{k}", hoop(bone, mat, y, r * 1.6, r * 0.6, cx=x, zc=z, r=r * 0.35, n=10))
        else:
            pts = [(x + math.cos(a) * r * 0.6, y + math.sin(a) * r * 1.6, z) for a in [i / 10 * 2 * math.pi for i in range(11)]]
            f += part(f"ch{bone}{x:.0f}{z:.0f}{k}", tube(bone, mat, pts, ra=r * 0.35, sides=4, hint=(0, 0, 1)))
        y -= step
        k += 1
    return f


def chain_path(bone, name, pts, step=22.0, r=6.0, mat="iron"):
    """点の列に沿った鎖 (輪を交互に 90 度ひねる)"""
    pts = [np.asarray(p, float) for p in pts]
    out = []
    k = 0
    for a, b in zip(pts, pts[1:]):
        d = b - a
        L = np.linalg.norm(d)
        t = d / L
        h = np.array([0, 1.0, 0]) if abs(t[1]) < 0.9 else np.array([1.0, 0, 0])
        u = np.cross(t, h)
        u /= np.linalg.norm(u)
        w = np.cross(t, u)
        s = 0.0
        while s < L:
            c = a + t * s
            ax = u if k % 2 == 0 else w
            ring = [c + t * math.cos(q) * r * 1.6 + ax * math.sin(q) * r * 0.6 for q in [i / 10 * 2 * math.pi for i in range(11)]]
            out += part(f"{name}{k}", tube(bone, mat, ring, ra=r * 0.35, sides=4, hint=tuple(np.cross(t, ax))))
            s += step
            k += 1
    return out


def blade(bone, mat, pts, width, thick, hint=(0, 0, 1), sides=4):
    """平たい羽根・板 (幅 width を hint と直交する向きに、厚み thick)"""
    return tube(bone, mat, pts, ra=width, rb=thick, sides=sides, hint=hint, phase=0.0)


def disc(bone, mat, c, r, thick=5.0, segs=20, rings=4, ry=None):
    """正面を向いた薄い円盤 (閉じた楕円体)"""
    return ellipsoid(bone, mat, c, (r, r if ry is None else ry, thick), segs=segs, rings=rings)


def sigil_mark(bone, name, c, R, mat="dawn", spokes=6, tri=True, r=4.0):
    """光る印: 二重の輪と、輻、まんなかに天秤 (梁と二つの皿)"""
    cx, cy, z = c
    f = []
    f += part(f"{name}o", vhoop(bone, mat, cx, cy, z, R, r=r, n=28))
    f += part(f"{name}i", vhoop(bone, mat, cx, cy, z - 1, R * 0.62, r=r * 0.7, n=22))
    for i in range(spokes):
        a = 2 * math.pi * i / spokes + math.pi / spokes
        p0 = (cx + math.sin(a) * R * 0.66, cy + math.cos(a) * R * 0.66, z - 1)
        p1 = (cx + math.sin(a) * R * 1.28, cy + math.cos(a) * R * 1.28, z - 1)
        f += part(f"{name}s{i}", tube(bone, mat, [p0, p1], ra=[r * 0.9, 0.0], sides=4, hint=(0, 0, 1)))
    if tri:
        f += part(f"{name}b", tube(bone, mat, [(cx - R * 0.45, cy + R * 0.18, z - 2), (cx + R * 0.45, cy + R * 0.18, z - 2)], ra=r * 0.6, sides=4, hint=(0, 1, 0)))
        f += part(f"{name}p", tube(bone, mat, [(cx, cy + R * 0.4, z - 2), (cx, cy - R * 0.38, z - 2)], ra=r * 0.6, sides=4, hint=(1, 0, 0)))
        for s in (-1, 1):
            f += part(f"{name}q{s}", ellipsoid(bone, mat, (cx + s * R * 0.45, cy - R * 0.05, z - 2), (R * 0.16, R * 0.05, 3), segs=8, rings=2))
    return f


# ---------------------------------------------------------------- 腕と手
def arm_parts(s, mat, upper_r=(64, 66, 56, 46), fore_r=(46, 50, 44, 34), sides=10):
    """肩から手首まで。s = +1 右 (天)、-1 左 (淵)"""
    a, f_ = ("arm_r", "fore_r") if s > 0 else ("arm_l", "fore_l")
    F = []
    F += part(f"shoulder{s}", ellipsoid(a, mat, (s * 158, 322, -10), (72, 64, 72), segs=12, rings=6))
    F += part(f"upper{s}", tube(a, mat, [(s * 162, 312, -12), (s * 178, 258, -42), (s * 194, 186, -82), (s * 205, 126, -118)],
                                  ra=list(upper_r), sides=sides, hint=(1, 0, 0)))
    F += part(f"elbow{s}", ellipsoid(f_, mat, (s * 205, 120, -120), (48, 48, 48), segs=10, rings=5))
    F += part(f"fore{s}", tube(f_, mat, [(s * 205, 118, -122), (s * 207, 62, -170), (s * 209, -18, -240), (s * 210, -96, -316)],
                                 ra=list(fore_r), sides=sides, hint=(1, 0, 0)))
    return F


def hand_parts(s, mat, nail="gold", claws=False, ring_mat=None, fat=1.0):
    """掌を下に、指を前 (-Z) へ。右手の人さし指 (親指に近い指) は index_r の骨"""
    h = "hand_r" if s > 0 else "hand_l"
    F = []
    rings = []
    for z, w, t in ((-330, 40, 22), (-352, 56, 26), (-384, 64, 26), (-418, 64, 22), (-436, 58, 18)):
        rings.append([np.array([s * 210 + w * math.sin(2 * math.pi * k / 14), -140 + t * math.cos(2 * math.pi * k / 14) * (0.8 if math.cos(2 * math.pi * k / 14) < 0 else 1.0), z])
                      for k in range(14)])
    F += part(f"palm{s}", loft(h, mat, rings, caps=(mat, mat)))
    # 拳の骨の出っ張り
    for i, dx in enumerate((-44, -15, 15, 44)):
        F += part(f"knuck{s}{i}", ellipsoid(h, mat, (s * 210 + dx, -126, -428), (13, 10, 12), segs=6, rings=3))
    # 指 (三つの節)。人さし指は親指の側 (s = +1 なら x の小さい側)
    order = (-44, -15, 15, 44)
    for i, dx in enumerate(order):
        fx = s * 210 + dx
        length = (1.0, 1.08, 1.04, 0.86)[i if s > 0 else 3 - i]
        bone_ = ("index_r" if i == 0 else "fingers_r") if s > 0 else h
        L = 32 * length
        pts = [(fx, -136, -432), (fx, -132, -432 - L), (fx, -136, -432 - 2 * L), (fx, -150, -432 - 2.9 * L)]
        F += part(f"finger{s}{i}", tube(bone_, mat, pts, ra=[16 * fat, 14 * fat, 12 * fat, 9 * fat], sides=6, hint=(1, 0, 0)))
        F += part(f"fj{s}{i}", ellipsoid(bone_, mat, (pts[1][0], pts[1][1] + 6, pts[1][2]), (17 * fat, 13 * fat, 11), segs=6, rings=3))
        tip = np.array(pts[3], float)
        if claws:
            F += part(f"claw{s}{i}", spike(bone_, nail, tip + (0, 0, 2), (0, -0.55, -1), 34 * length, 9, sides=5, rings=3, bend=(0, -10, 0)))
        else:
            F += part(f"nail{s}{i}", ellipsoid(bone_, nail, tip + (0, 7, -3), (8, 3, 9), segs=6, rings=2))
        if ring_mat and i == 2:
            F += part(f"fring{s}", hoop(bone_, ring_mat, -133, 15 * fat, 15 * fat, cx=fx, zc=-432 - L * 0.6, r=4, n=10))
    th = [(s * 210 - s * 56, -140, -360), (s * 210 - s * 84, -146, -392), (s * 210 - s * 100, -152, -428)]
    F += part(f"thumb{s}", tube(h, mat, th, ra=[20 * fat, 16 * fat, 11 * fat], sides=6, hint=(1, 0, 0)))
    # 手首の玉 (支点が真ん中なので、手を返しても前腕の端と掌のあいだが開かない。撮影で掲げた手が浮いて見えたので足した)
    F += part(f"wrist{s}", ellipsoid(h, mat, (s * 210, -110, -330), (44 * fat, 40 * fat, 44 * fat), segs=10, rings=5))
    if claws:
        F += part(f"tclaw{s}", spike(h, nail, (s * 210 - s * 100, -152, -430), (-s * 0.3, -0.5, -1), 26, 8, sides=5, rings=2))
    return F


def palm_sigil(s=-1, mat="void"):
    """掌の下の印 (palm_l)。ふだんは大きさ 0、掲げたときに開く"""
    cx = s * 210
    f = []
    f += part("psig_o", tube("palm_l", mat, [(cx + math.cos(a) * 52, -170, -385 + math.sin(a) * 52) for a in [i / 24 * 2 * math.pi for i in range(25)]],
                              ra=4, sides=4, hint=(0, 1, 0)))
    f += part("psig_i", tube("palm_l", mat, [(cx + math.cos(a) * 30, -171, -385 + math.sin(a) * 30) for a in [i / 18 * 2 * math.pi for i in range(19)]],
                              ra=3, sides=4, hint=(0, 1, 0)))
    for i in range(3):
        a = 2 * math.pi * i / 3
        f += part(f"psig_t{i}", tube("palm_l", mat, [(cx + math.cos(a) * 52, -171, -385 + math.sin(a) * 52),
                                                     (cx + math.cos(a + 2.094) * 52, -171, -385 + math.sin(a + 2.094) * 52)], ra=2.5, sides=4, hint=(0, 1, 0)))
    return f


# ---------------------------------------------------------------- 角
def horn(bone, name, mat, s, pts, radii, ridge=None, sides=7):
    f = part(f"{name}{s}", tube(bone, mat, pts, ra=radii, sides=sides, hint=(0, 0, 1)))
    if ridge:
        for i in range(1, len(pts) - 1):
            p = np.asarray(pts[i], float)
            q = np.asarray(pts[i + 1], float) - np.asarray(pts[i - 1], float)
            q /= np.linalg.norm(q)
            r = radii[i] * 1.12
            u = np.cross(q, (0, 0, 1.0))
            if np.linalg.norm(u) < 1e-3:
                u = np.array([1.0, 0, 0])
            u /= np.linalg.norm(u)
            w = np.cross(q, u)
            ring = [p + (u * math.cos(a) + w * math.sin(a)) * r for a in [k / 10 * 2 * math.pi for k in range(11)]]
            f += part(f"{name}r{s}{i}", tube(bone, ridge, ring, ra=2.6, sides=4, hint=tuple(q)))
    return f


# ---------------------------------------------------------------- 背の門 (あいだの門)
def rift_gate(style="stone", R=330, cy=420, z=300):
    """主の背に立つ門。黒い石の欠片の輪・金の縁・中は暗い淵に紫の渦。倒れると主はここへ引き戻される"""
    F = []
    F += part("rift_fill", disc("rift", "hollow", (0, cy, z + 10), R * 0.9, thick=6, segs=28, rings=6))
    F += part("rift_rim", vhoop("rift", "gold", 0, cy, z - 2, R * 0.92, r=9, n=40))
    F += part("rift_rim2", vhoop("rift", "void", 0, cy, z + 2, R * 0.82, r=5, n=36))
    # 渦の腕 (光る)
    for i in range(5):
        pts = []
        for j in range(9):
            t = j / 8
            a = 2 * math.pi * i / 5 + t * 2.4
            rr = R * (0.12 + 0.68 * t)
            pts.append((math.sin(a) * rr, cy + math.cos(a) * rr, z + 2 - 2 * t))
        F += part(f"rift_arm{i}", tube("rift", "void", pts, ra=[3] + [6 + 4 * math.sin(math.pi * j / 8) for j in range(1, 8)] + [2], sides=4, hint=(0, 0, 1)))
    # 輪の欠片
    n = 18
    for i in range(n):
        a = 2 * math.pi * (i + 0.5) / n
        c = (math.sin(a) * R, cy + math.cos(a) * R, z)
        size = 46 + 14 * math.sin(i * 1.7)
        if style == "split":
            mat = "marble" if math.sin(a) > 0 else "stone"
        else:
            mat = "stone"
        rot = rot_z(-a + 0.4 * math.sin(i * 2.3))
        F += part(f"rift_rock{i}", ellipsoid("rift", mat, c, (size, size * 0.7, 30), segs=6, rings=3, rot=rot,
                                             deform=lambda p, i=i: p * (1 + 0.18 * math.sin(p[0] * 0.07 + i) * math.cos(p[1] * 0.05))))
        if i % 3 == 0:
            d = np.array([math.sin(a), math.cos(a), 0.0])
            F += part(f"rift_spk{i}", spike("rift", "void", np.array(c) + d * size * 0.6, d, 70, 14, sides=5, rings=3))
    return F


# ---------------------------------------------------------------- 書き出す前の縮め
def finish(F):
    lift = np.array(LIFT, dtype=float)
    for f in F:
        f.verts = (f.verts + lift) * SCALE
        f.loc = f.verts
        f.ref = (f.ref + lift) * SCALE
    return F, []


# ---------------------------------------------------------------- 姿 (Annihilation の合図)
POSES = {
    "idle": {},
    # 星屑の雨: 右手を掲げ、ほかの指を握って人さし指を立てる (x が正 = 前・上へ)
    "finger": {"arm_r": (128, 0, 8), "fore_r": (8, 0, 0), "hand_r": (-10, 0, 0), "fingers_r": (-110, 0, 0), "head": (12, 0, 0)},
    # 天と淵の群れ: 左の掌を橋へ向けて掲げる (掌の印)
    "palm": {"arm_l": (58, 0, -6), "fore_l": (34, 0, 0), "hand_l": (0, 0, 0), "head": (6, 0, 0), "body": (0, -8, 0)},
    # 落ちる月を呼ぶ: 両手を空へ、口を開く
    "moon": {"arm_l": (128, 0, -10), "arm_r": (128, 0, 10), "fore_l": (18, 0, 0), "fore_r": (18, 0, 0),
             "head": (22, 0, 0), "jaw": (-30, 0, 0), "body": (6, 0, 0)},
    # 側の目: 身を伏せ、両腕を門の左右へ広げる
    "bow": {"body": (-22, 0, 0), "head": (-16, 0, 0), "arm_l": (-24, 0, -58), "arm_r": (-24, 0, 58), "fore_l": (12, 0, 0), "fore_r": (12, 0, 0)},
    # 倒れる: 前へ崩れて腕を伸ばす (このあと門へ引き戻される)
    "fall": {"body": (-30, 0, 0), "head": (-18, 0, 10), "jaw": (-24, 0, 0), "arm_l": (72, 0, -8), "arm_r": (72, 0, 8),
             "fore_l": (10, 0, 0), "fore_r": (10, 0, 0), "rift": (0, 0, 25)},
}

ROWS = [("前 3/4 (橋から見上げる)", "idle", 205, 4, 600, (0, 66, 30), 2.6),
        ("星屑の雨: 指を立てる", "finger", 160, 6, 600, (0, 70, 30), 2.6),
        ("群れ: 掌の印を掲げる", "palm", 200, 6, 600, (0, 66, 30), 2.6),
        ("落ちる月: 両手を空へ・口を開く", "moon", 180, 8, 600, (0, 74, 30), 2.4),
        ("側の目: 伏して腕を広げる", "bow", 215, 10, 600, (0, 60, 30), 2.6),
        ("倒れる: 腕を伸ばし門へ", "fall", 150, 12, 600, (0, 60, 30), 2.6)]
