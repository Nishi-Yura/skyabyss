"""水路の顎 (waterway_maw) の低ポリ ラフ 3 案のメッシュ。

骨の名前・階層・pivot は今の geo (waterway_maw.geo.json) と同じ。座標は geo と同じモデル空間の絶対座標
(1 単位 = 1/16 ブロック、y が上、正面は -Z)。
  A 川カマス型   細長く鋭い楔の頭、針のような歯、細い釣り竿のルアー、しなやかな青い鱗
  B 提灯鮟鱇型   深く丸い頭、突き出る下顎、前の大きな牙、枝分かれの提灯、なめらかな肉の皮膚
  C 古代甲冑魚型 角ばった骨板の甲冑頭、刃の歯 (のこぎり)、背に帆、石の肌に光る刻印
"""
from __future__ import annotations

import sys
from pathlib import Path

import numpy as np

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE.parent))
from lpkit import (ellipsoid, loft, poly, spike, tri_count, tube)  # noqa

BONES = {
    "root": (None, [0.0, 0.0, 0.0]),
    "seg0": ("root", [0.0, 14.0, -8.0]),
    "head": ("seg0", [0.0, 14.0, -8.0]),
    "lure": ("head", [0.0, 26.0, -18.0]),
    "jaw": ("head", [0.0, 5.0, -8.0]),
    "seg1": ("seg0", [0.0, 14.0, 0.0]),
    "seg2": ("seg1", [0.0, 14.0, 8.0]),
    "seg3": ("seg2", [0.0, 14.0, 16.0]),
    "seg4": ("seg3", [0.0, 14.0, 24.0]),
    "seg5": ("seg4", [0.0, 14.0, 32.0]),
    "tail": ("seg5", [0.0, 14.0, 40.0]),
}


def V(*a):
    return np.array(a, float)


class Prof:
    """z をキーにした輪郭の表。at(z) で線形補間。列: hw, yt, yb, vault, crest ..."""

    def __init__(self, rows):
        self.rows = np.array(rows, float)

    def at(self, z):
        r = self.rows
        return [float(np.interp(z, r[:, 0], r[:, c])) for c in range(1, r.shape[1])]

    @property
    def zs(self):
        return list(self.rows[:, 0])


# ------------------------------------------------------------------ 輪 (断面)

def hring(z, hw, yt, yb, vault=1.5, crest=0.0, shoulder=0.58, lip=0.66, cheek=0.5, dx=0.0, n8=False):
    """頭 (上あご) の輪: 10 頂点 = 天辺, 右 4 (肩・頬・頬の下・唇), 口の天井, 左 4。n8 は頬の下を省いた 8 頂点。"""
    H = yt - yb
    R = [(shoulder * hw, yt - 0.10 * H), (hw, yb + H * cheek), (0.93 * hw, yb + 0.2 * H), (lip * hw, yb)]
    if n8:
        R = [R[0], R[1], R[3]]
    pts = [(0, yt + crest)] + R + [(0, yb + vault)] + [(-x, y) for x, y in reversed(R)]
    return [V(x + dx, y, z) for x, y in pts]


def jring(z, hw, yt, yb, dip=0.8, keel=0.5, rim=0.76, chin=0.55, side=0.5):
    """下あごの輪: 8 頂点 = 口の床の真ん中, 右 3 (縁・横・あご), 底, 左 3。"""
    H = yt - yb
    R = [(rim * hw, yt), (hw, yt - H * side), (chin * hw, yb)]
    pts = [(0, yt - dip)] + R + [(0, yb - keel)] + [(-x, y) for x, y in reversed(R)]
    return [V(x, y, z) for x, y in pts]


def bring(z, hw, yt, yb, crest=0.0, belly=0.3, lat=0.0, sh=0.62):
    """胴の輪: 8 頂点 = 背, 右 3, 腹, 左 3。"""
    H = yt - yb
    R = [(sh * hw, yt - 0.12 * H), (hw + lat, (yt + yb) / 2 + 0.08 * H), (0.64 * hw, yb + 0.12 * H)]
    pts = [(0, yt + crest)] + R + [(0, yb - belly)] + [(-x, y) for x, y in reversed(R)]
    return [V(x, y, z) for x, y in pts]


def blade(bone, mat, base, d, length, thick, width, bend=(0, 0, 0), rings=2, hint=(1, 0, 0), sides=4):
    """板状の先細りの棘 (ひれ・背の棘)。thick = 板の厚み方向の半径、width = 幅方向の半径。"""
    return spike(bone, mat, base, d, length, thick, sides=sides, rings=rings, bend=bend, hint=hint, rb=width)


# ------------------------------------------------------------------ 共通部品

def fix_mouth(faces, lipf):
    """上あごの天井・下あごの床を口の中の面にする。"""
    for f in faces:
        c = f.verts.mean(0)
        if f.bone == "head" and f.mat in ("skin", "plate") and f.normal[1] < -0.35 and abs(c[0]) < lipf(c[2]) * 0.95:
            f.mat = "mouth"
        if f.bone == "jaw" and f.mat == "skin" and f.normal[1] > 0.35:
            f.mat = "mouth"


def teeth_up(prof, lip, zs, lens, r, sides=3, wide=None, back=0.15, rings=1, bend=0.0, drop=0.5, splay=0.05, mat="tooth"):
    out = []
    for z, ln in zip(zs, lens):
        hw, yt, yb, *_ = prof.at(z)
        for s in (-1, 1):
            base = V(s * lip * hw, yb + drop, z)
            if wide:
                out += blade("head", mat, base, V(s * splay, -1, back), ln, r, wide, bend=(-s * bend, 0, 0), rings=rings)
            else:
                out += spike("head", mat, base, V(s * splay, -1, back), ln, r, sides=sides, rings=rings,
                             bend=(-s * bend, 0, 0))
    return out


def teeth_low(jprof, rim, zs, lens, r, sides=3, wide=None, fwd=0.1, rings=1, bend=0.0, inset=0.9, mat="tooth"):
    out = []
    for z, ln in zip(zs, lens):
        hw, yt, yb, *_ = jprof.at(z)
        for s in (-1, 1):
            base = V(s * rim * hw * inset, yt - 0.5, z)
            if wide:
                out += blade("jaw", mat, base, V(-s * 0.04, 1, -fwd), ln, r, wide, bend=(-s * bend, 0, 0), rings=rings)
            else:
                out += spike("jaw", mat, base, V(-s * 0.04, 1, -fwd), ln, r, sides=sides, rings=rings,
                             bend=(-s * bend, 0, 0))
    return out


def body_segments(rows, caps_front=True, mats=None):
    """rows: (z, hw, yt, yb, crest, belly, lat) を前から並べる。各区間を骨 seg0〜seg5 に割り当てる。"""
    rings = [bring(*r) for r in rows]
    out = []
    zs = [r[0] for r in rows]
    names = {-14: None}
    for i in range(len(rings) - 1):
        z0, z1 = zs[i], zs[i + 1]
        zm = (z0 + z1) / 2
        if zm < 0:
            bone = "seg0"
        elif zm < 8:
            bone = "seg1"
        elif zm < 16:
            bone = "seg2"
        elif zm < 24:
            bone = "seg3"
        elif zm < 32:
            bone = "seg4"
        else:
            bone = "seg5"
        out += loft(bone, "skin", [rings[i], rings[i + 1]])
    return out, rings


def finalize_bones(faces):
    for f in faces:
        assert f.bone in BONES, f.bone
    return faces


# ================================================================ A 川カマス型

def build_a():
    F = []
    ex = {}
    P = Prof([(-36, 2.0, 11.4, 8.6, 0.6, 0.3), (-33, 3.0, 13.2, 8.4, 0.8, 0.3), (-29, 3.9, 15.6, 8.2, 1.2, 0.5),
              (-25, 4.9, 16.2, 8.0, 1.6, 0.2), (-21, 6.5, 20.6, 8.0, 2.0, 0.6), (-17, 7.6, 22.8, 8.0, 2.4, 0.9),
              (-12.5, 8.3, 23.8, 8.0, 2.5, 0.8), (-8, 7.0, 20.6, 8.0, 1.5, 0.0)])
    zs = [-36, -33, -29, -25, -21, -17, -12.5, -8]
    R = [hring(z, *P.at(z), n8=True) for z in zs]
    for k in (1, 7):
        R[4][k] += V(0, 0.9, 0)          # 眉の盛り上がり
        R[3][k] += V(-0.3 if k == 1 else 0.3, -0.3, 0)   # 目の前の凹み
    R[0][0] += V(0, 0.4, -0.5)            # 鼻先
    R[2][0] += V(0, 0.6, 0)               # 鼻の瘤
    F += loft("head", "skin", R, caps=("skin", False))
    lipf = lambda z: 0.66 * P.at(z)[0]
    # 上あご 10 対: 細い針、前の二対は長い牙
    zt = np.linspace(-33.2, -10.0, 10)
    lens = [7.4, 6.8, 5.0, 4.4, 4.2, 4.0, 3.7, 3.4, 3.0, 2.6]
    F += teeth_up(P, 0.66, zt, lens, 0.7, sides=3, back=0.22, rings=1)
    # 下あご
    J = Prof([(-36.5, 1.0, 6.9, 4.4), (-30, 2.6, 7.0, 3.4), (-22, 4.1, 7.0, 2.6), (-14, 5.4, 7.0, 2.0), (-8.5, 6.0, 7.0, 2.2)])
    JR = [jring(z, *J.at(z)) for z in J.zs]
    JR[0][0] += V(0, 0.9, -0.8)
    F += loft("jaw", "skin", JR, caps=("skin", False))
    zl = np.linspace(-31.5, -14.5, 7)
    F += teeth_low(J, 0.76, zl, [5.8, 5.0, 4.4, 4.0, 3.6, 3.3, 3.0], 0.7, sides=3, fwd=0.2)
    fix_mouth(F, lipf)
    # 頬のえら蓋 (薄い板) と頬骨の角
    for s, k in ((-1, 7), (1, 1)):
        F += blade("head", "plate", V(s * 7.6, 15.4, -14.5), V(s * 0.9, -0.3, 1.0), 6.5, 0.7, 2.6, rings=1)
        F += blade("head", "plate", V(s * 7.9, 12.6, -13.0), V(s * 0.9, -0.5, 1.0), 5.0, 0.6, 1.8, rings=1)
        bv = R[4][k] * np.array([0.9, 1, 1])
        F += spike("head", "bone", bv, V(s * 0.3, 0.35, -1.0), 4.4, 1.1, sides=4, rings=1)   # 眉の角
    ex["eyes"] = [dict(c=V(s * 5.4, 17.6, -19.0), rz=3.0, rw=1.3, pz=0.72, pw=0.62) for s in (-1, 1)]
    # ルアー: 細い釣り竿 (前へ弧を描く) と光の玉
    stalk = [(0, 23.0, -18.0), (0, 31.0, -19.6), (0, 36.2, -24.0), (0, 36.6, -29.0)]
    F += tube("lure", "bone", stalk, ra=[1.0, 0.62, 0.5, 0.4], rb=[1.0, 0.62, 0.5, 0.4], sides=4, hint=(1, 0, 0))
    F += ellipsoid("lure", "lure", V(0, 35.2, -30.8), (1.8, 1.8, 1.8), segs=6, rings=2)
    F += spike("lure", "bone", V(0, 36.6, -29.0), V(0, 0.3, -1), 1.4, 0.4, sides=3, rings=1)
    # 胴: 細く長い。背に低い稜
    rows = [(-14, 5.6, 18.5, 9.8, 0, 0.2, 0), (-8, 7.0, 20.6, 8.0, 0.0, 0.5, 0), (0, 6.0, 19.0, 9.2, 1.5, 0.4, 0.4),
            (8, 5.2, 17.8, 9.6, 0.3, 0.4, 0.3), (16, 4.3, 16.8, 10.6, 1.3, 0.3, 0.3), (24, 3.3, 16.2, 11.2, 0.3, 0.3, 0.2),
            (32, 2.4, 15.6, 12.0, 1.0, 0.2, 0.1), (40, 1.4, 15.0, 12.8, 0.2, 0.1, 0)]
    body, brs = body_segments(rows)
    F += body
    F += poly("seg0", "skin", brs[0], np.mean(brs[1], axis=0))
    # 背びれ: 後ろへ寝た薄い刃を連ねる
    for i, (z, ln) in enumerate([(-4.5, 6.5), (1.0, 8.2), (6.0, 8.8), (11.0, 8.0), (16.0, 7.0), (21.0, 5.8), (26.0, 4.6),
                                 (31.0, 3.6)]):
        hw, yt = rows[min(i // 1 + 1, len(rows) - 1)][1:3]
        top = float(np.interp(z, [r[0] for r in rows], [r[2] for r in rows])) + 0.3
        bone = "seg%d" % max(0, min(5, int((z + 8) // 8)))
        F += blade(bone, "fin", V(0, top, z), V(0, 1, 0.9), ln, 0.55, 2.2, bend=(0, -0.5, 2.2), rings=2 if ln > 7 else 1)
    # 胸びれ・腹びれ
    for s in (-1, 1):
        F += blade("seg0", "fin", V(s * 6.2, 11.8, -4.5), V(s * 1.0, -0.45, 0.9), 10.0, 0.5, 2.6, bend=(s * 1.0, -1.0, 1.2),
                   hint=(0, 1, 0), rings=2)
        F += blade("seg2", "fin", V(s * 3.0, 11.0, 11.5), V(s * 0.8, -0.6, 0.8), 6.0, 0.45, 1.6, hint=(0, 1, 0), rings=1)
    F += blade("seg1", "fin", V(0, 9.4, 4.0), V(0, -1, 0.7), 5.5, 0.5, 2.0, rings=1)
    # 尾: 二股の三日月
    F += loft("tail", "skin", [bring(40, 1.4, 15.0, 12.8), bring(44.0, 1.0, 15.2, 12.6)], caps=(False, "skin"))
    F += blade("tail", "fin", V(0, 15.0, 41.0), V(0, 1.0, 0.8), 12.5, 0.7, 2.6, bend=(0, 0, 2.5), rings=2)
    F += blade("tail", "fin", V(0, 12.9, 41.0), V(0, -1.0, 0.8), 12.5, 0.7, 2.6, bend=(0, 0, 2.5), rings=2)
    F += blade("tail", "fin", V(0, 14.0, 43.0), V(0, 0, 1.0), 4.2, 0.5, 1.8, rings=1)
    return finalize_bones(F), ex


# ================================================================ B 提灯鮟鱇型

def build_b():
    F = []
    ex = {}
    P = Prof([(-33, 3.6, 13.0, 8.8, 1.0, 0.0), (-30, 6.4, 18.6, 8.4, 2.0, 0.5), (-25, 8.6, 22.6, 8.0, 3.0, 0.8),
              (-18, 9.6, 24.0, 8.0, 3.4, 1.0), (-12, 9.2, 23.4, 8.0, 3.2, 0.6), (-8, 7.2, 20.8, 8.0, 1.5, 0.0)])
    zs = [-33, -30, -25, -18, -12, -8]
    R = [hring(z, *P.at(z), shoulder=0.7, cheek=0.55, lip=0.68) for z in zs]
    R[1][0] += V(0, -0.4, -0.3)
    R[0][0] += V(0, -0.5, 0)
    F += loft("head", "skin", R, caps=("skin", False))
    lipf = lambda z: 0.68 * P.at(z)[0]
    # 上あご 10 対: 前の一対は大きな牙、後ろへ小さく
    zt = np.linspace(-31.0, -10.0, 10)
    lens = [11.0, 7.5, 5.4, 4.4, 4.2, 3.8, 3.4, 3.0, 2.6, 2.2]
    for i, (z, ln) in enumerate(zip(zt, lens)):
        hw, yt, yb, *_ = P.at(z)
        for s in (-1, 1):
            base = V(s * 0.68 * hw, yb + 0.6, z)
            F += spike("head", "tooth", base, V(-s * 0.18, -1, 0.15), ln, 1.4 if i < 2 else 0.85,
                       sides=4 if i < 3 else 3, rings=2 if i < 2 else 1, bend=(-s * (1.4 if i < 2 else 0), 0, 0.8 if i < 2 else 0))
    # 下あご: 大きく前に突き出る
    J = Prof([(-37.5, 3.0, 7.6, 2.6), (-33, 5.6, 7.6, 0.8), (-26, 7.0, 7.6, 0.4), (-17, 8.0, 7.4, 0.8), (-9, 7.4, 7.4, 1.6)])
    JR = [jring(z, *J.at(z), dip=1.0, rim=0.8, keel=1.0) for z in J.zs]
    JR[0][0] += V(0, 0.3, -0.5)
    F += loft("jaw", "skin", JR, caps=("skin", False))
    zl = np.linspace(-35.0, -14.0, 7)
    lens_l = [6.8, 6.0, 5.2, 4.4, 4.0, 3.4, 3.0]
    for i, (z, ln) in enumerate(zip(zl, lens_l)):
        hw, yt, yb, *_ = J.at(z)
        for s in (-1, 1):
            base = V(s * 0.8 * hw * 0.88, yt - 0.5, z)
            F += spike("jaw", "tooth", base, V(-s * 0.06, 1, -0.28), ln, 0.95 if i < 3 else 0.75, sides=3,
                       rings=2 if i < 2 else 1, bend=(s * 0.8 if i < 2 else 0, 0, -1.4 if i < 2 else 0))
    fix_mouth(F, lipf)
    # 頬の返し (後ろへ流れる棘)・眉の突起・あごの肉の垂れ
    for s in (-1, 1):
        F += spike("head", "bone", V(s * 8.6, 17.5, -13.0), V(s * 0.8, 0.0, 1.0), 6.0, 1.5, sides=4, rings=1, bend=(0, -0.8, 0))
        F += spike("head", "bone", V(s * 6.0, 22.0, -23.5), V(s * 0.4, 0.55, -0.9), 3.8, 1.3, sides=4, rings=1)
        F += spike("jaw", "bone", V(s * 4.0, 1.8, -25.0), V(s * 0.1, -1, -0.5), 2.8, 0.9, sides=3, rings=1)
        F += spike("jaw", "bone", V(s * 5.0, 1.6, -17.0), V(s * 0.2, -1, -0.3), 2.4, 0.9, sides=3, rings=1)
    ex["eyes"] = [dict(c=V(s * 8.1, 19.6, -21.0), rz=2.2, rw=1.6) for s in (-1, 1)]
    # ルアー: 太い根元から前へ垂れる茎、枝分かれの提灯
    stalk = [(0, 23.2, -18.0), (0, 31.0, -18.6), (0, 35.8, -23.5), (0, 33.6, -30.0), (0, 30.4, -32.4)]
    F += tube("lure", "bone", stalk, ra=[1.5, 0.9, 0.7, 0.55, 0.45], rb=[1.5, 0.9, 0.7, 0.55, 0.45], sides=4, hint=(1, 0, 0))
    F += ellipsoid("lure", "lure", V(0, 28.2, -33.0), (2.4, 2.6, 2.4), segs=6, rings=2)
    for a in (-1, 0, 1):
        F += spike("lure", "bone", V(a * 1.4, 30.2, -32.8), V(a * 0.8, 0.8, -0.2), 2.8, 0.45, sides=3, rings=1)
    # 胴: 太く、すぐに細る
    rows = [(-14, 6.4, 19.0, 9.6, 0, 0.4, 0), (-8, 7.2, 20.8, 8.0, 0.0, 1.0, 0), (0, 7.6, 20.5, 8.2, 1.0, 1.3, 0.6),
            (8, 6.6, 19.0, 9.0, 0.5, 1.0, 0.5), (16, 4.8, 17.2, 10.6, 1.4, 0.6, 0.3), (24, 3.2, 16.2, 11.6, 0.4, 0.3, 0.2),
            (32, 2.2, 15.4, 12.4, 0.8, 0.2, 0.1), (40, 1.3, 15.0, 12.8, 0.2, 0.1, 0)]
    body, brs = body_segments(rows)
    F += body
    F += poly("seg0", "skin", brs[0], np.mean(brs[1], axis=0))
    for z, ln, bz in [(-2.0, 6.0, 0.6), (4.0, 7.0, 0.7), (11.0, 5.6, 0.6), (18.0, 4.4, 0.5), (25.0, 3.4, 0.4)]:
        top = float(np.interp(z, [r[0] for r in rows], [r[2] + r[4] * 0.5 for r in rows])) + 0.2
        bone = "seg%d" % max(0, min(5, int((z + 8) // 8)))
        F += spike(bone, "fin", V(0, top, z), V(0, 1, 0.55), ln, bz, sides=4, rings=1, bend=(0, 0, 1.6))
    for s in (-1, 1):
        F += blade("seg0", "fin", V(s * 7.0, 12.5, -3.5), V(s * 1.0, -0.3, 0.7), 8.5, 0.6, 3.4, bend=(s * 1.0, -1.5, 1.0),
                   hint=(0, 1, 0), rings=2)
        F += blade("seg2", "fin", V(s * 3.4, 11.0, 11.5), V(s * 0.8, -0.7, 0.8), 5.0, 0.5, 2.0, hint=(0, 1, 0), rings=1)
    # 尾: 大きな丸いうちわ
    T = [bring(40, 1.3, 15.0, 12.8), bring(43.0, 1.0, 20.5, 7.5, crest=0.5), bring(46.5, 0.6, 22.5, 5.5, crest=1.5, belly=1.2)]
    T[1][0] += V(0, 0, -1.2)
    T[2][0] += V(0, 1.0, 1.0)
    T[2][1] += V(0, 0.5, -1)
    T[2][7] += V(0, 0.5, -1)
    F += loft("tail", "fin", T, caps=(False, "fin"))
    return finalize_bones(F), ex


# ================================================================ C 古代甲冑魚型

def build_c():
    F = []
    ex = {}
    P = Prof([(-35, 5.4, 12.2, 8.6, 0.8, 0.0), (-32, 7.4, 17.0, 8.2, 1.6, 0.4), (-26, 9.0, 22.0, 8.0, 2.4, 0.8),
              (-18, 9.6, 24.0, 8.0, 2.8, 1.2), (-12, 8.8, 22.6, 8.0, 2.4, 0.6), (-8, 7.0, 20.6, 8.0, 1.5, 0.0)])
    zs = [-35, -32, -26, -18, -12, -8]
    R = [hring(z, *P.at(z), shoulder=0.78, cheek=0.52, lip=0.7) for z in zs]
    # 甲冑の稜: 肩の頂点を持ち上げて平らな天板に
    for i in (1, 2, 3, 4):
        R[i][1] += V(0, 0.9, 0)
        R[i][9] += V(0, 0.9, 0)
    R[3][0] += V(0, -1.2, 0)       # 天板の真ん中は少し凹ませて中央の溝に
    R[2][0] += V(0, -0.8, 0)
    R[0][0] += V(0, -0.5, -0.4)
    F += loft("head", "plate", R, caps=("plate", False), mats=["plate"] * 5)
    lipf = lambda z: 0.7 * P.at(z)[0]
    # 上あご 10 対: 幅の広い刃の歯 (のこぎり)
    zt = np.linspace(-33.0, -10.0, 10)
    lens = [4.2, 4.6, 4.4, 4.0, 3.8, 3.6, 3.4, 3.2, 3.0, 2.8]
    F += teeth_up(P, 0.7, zt, lens, 0.7, wide=1.5, back=0.0, rings=1, splay=0.08)
    J = Prof([(-35.5, 3.4, 7.2, 3.6), (-30, 5.0, 7.2, 2.4), (-22, 6.2, 7.2, 1.8), (-14, 6.8, 7.2, 1.6), (-8.5, 6.6, 7.2, 2.0)])
    JR = [jring(z, *J.at(z), rim=0.8, chin=0.62) for z in J.zs]
    F += loft("jaw", "plate", JR, caps=("plate", False))
    zl = np.linspace(-32.0, -14.0, 7)
    F += teeth_low(J, 0.8, zl, [4.2, 4.0, 3.8, 3.6, 3.4, 3.2, 3.0], 0.7, wide=1.5, fwd=0.0, rings=1)
    fix_mouth(F, lipf)
    for f in F:
        if f.bone == "jaw" and f.mat == "plate" and f.normal[1] > 0.35:
            f.mat = "mouth"
    # 頭の飾り: 後ろへ反る三本の角の冠、頬の板
    for s in (-1, 1):
        F += blade("head", "bone", V(s * 3.2, 23.4, -16.0), V(s * 0.25, 0.8, 0.85), 8.0, 0.9, 1.8, bend=(0, 0.5, 1.5), rings=2)
        F += blade("head", "bone", V(s * 7.6, 20.5, -14.5), V(s * 0.9, 0.35, 0.9), 6.0, 0.8, 1.6, rings=1)
        F += blade("head", "bone", V(s * 9.0, 14.0, -17.0), V(s * 0.9, -0.5, 0.7), 4.8, 0.8, 1.4, rings=1)
    F += blade("head", "bone", V(0, 23.0, -22.0), V(0, 0.5, -0.9), 4.6, 0.9, 1.4, rings=1)
    ex["eyes"] = [dict(c=V(s * 8.0, 20.2, -24.0), rz=2.8, rw=1.5) for s in (-1, 1)]
    ex["runes"] = True
    # ルアー: 短い骨の角が囲む角灯
    F += tube("lure", "bone", [(0, 23.0, -18.0), (0, 27.2, -18.0)], ra=[1.6, 1.0], rb=[1.6, 1.0], sides=4, hint=(1, 0, 0))
    F += ellipsoid("lure", "lure", V(0, 30.0, -18.0), (1.9, 2.0, 1.9), segs=6, rings=2)
    for a, b in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        F += spike("lure", "bone", V(a * 1.9, 27.4, -18.0 + b * 1.9), V(a * 0.35, 1, b * 0.35), 6.2, 0.6, sides=3, rings=1)
    # 胴: 板を重ねた鎧の輪、背の稜が高い
    rows = [(-14, 5.8, 18.8, 9.6, 0, 0.3, 0), (-8, 7.0, 20.6, 8.0, 0.0, 0.6, 0), (0, 6.6, 20.0, 8.8, 2.0, 0.6, 0.8),
            (8, 5.8, 18.6, 9.4, 0.5, 0.5, 0.5), (16, 4.6, 17.4, 10.4, 2.0, 0.4, 0.5), (24, 3.4, 16.4, 11.2, 0.5, 0.3, 0.3),
            (32, 2.4, 15.6, 12.0, 1.4, 0.2, 0.2), (40, 1.4, 15.0, 12.8, 0.2, 0.1, 0)]
    body, brs = body_segments(rows)
    for f in body:
        f.mat = "plate" if f.normal[1] > -0.3 else "skin"
    F += body
    F += poly("seg0", "skin", brs[0], np.mean(brs[1], axis=0))
    # 背の帆: 骨の筋が通る大きな板 (三つ)
    for z0, h, w in [(-3.0, 9.5, 5.5), (6.0, 12.5, 6.5), (15.0, 9.5, 5.0)]:
        top = float(np.interp(z0, [r[0] for r in rows], [r[2] + r[4] * 0.6 for r in rows]))
        bone = "seg%d" % max(0, min(5, int((z0 + 8) // 8)))
        F += blade(bone, "fin", V(0, top, z0), V(0, 1, 0.45), h, 0.7, w, bend=(0, 0, 3.0), rings=2)
        F += spike(bone, "bone", V(0, top, z0 - w * 0.5), V(0, 1, 0.1), h * 0.55, 0.8, sides=3, rings=1)
    for z, ln in [(24.0, 4.6), (31.0, 3.6)]:
        top = float(np.interp(z, [r[0] for r in rows], [r[2] + r[4] * 0.5 for r in rows]))
        bone = "seg%d" % max(0, min(5, int((z + 8) // 8)))
        F += spike(bone, "bone", V(0, top, z), V(0, 1, 0.8), ln, 0.8, sides=4, rings=1)
    for s in (-1, 1):
        F += spike("seg0", "bone", V(s * 6.4, 12.0, -3.5), V(s * 1.0, -0.2, 0.8), 8.0, 1.1, sides=4, rings=2, bend=(s * 1.0, -1.5, 1.5))
        F += spike("seg0", "bone", V(s * 6.4, 14.5, -1.5), V(s * 1.0, 0.1, 0.9), 5.5, 0.9, sides=4, rings=1)
        F += blade("seg2", "fin", V(s * 3.4, 11.0, 11.5), V(s * 0.8, -0.7, 0.8), 5.0, 0.5, 1.8, hint=(0, 1, 0), rings=1)
    # 尾: 重い三日月と骨の筋
    F += loft("tail", "skin", [bring(40, 1.4, 15.0, 12.8), bring(43.5, 1.2, 15.8, 12.2)], caps=(False, "skin"))
    F += blade("tail", "fin", V(0, 15.4, 41.5), V(0, 1.0, 0.6), 11.0, 0.8, 3.4, bend=(0, 0, 3.8), rings=2)
    F += blade("tail", "fin", V(0, 12.6, 41.5), V(0, -1.0, 0.6), 11.0, 0.8, 3.4, bend=(0, 0, 3.8), rings=2)
    F += spike("tail", "bone", V(0, 14.0, 43.0), V(0, 0, 1), 5.0, 0.9, sides=4, rings=1)
    return finalize_bones(F), ex


BUILDERS = {"a": build_a, "b": build_b, "c": build_c}

if __name__ == "__main__":
    for k, fn in BUILDERS.items():
        F, ex = fn()
        per = {}
        for f in F:
            per[f.bone] = per.get(f.bone, 0) + len(f.verts) - 2
        print(k, tri_count(F), per)
