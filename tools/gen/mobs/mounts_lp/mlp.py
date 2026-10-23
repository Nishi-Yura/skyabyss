"""乗り物 (docs/design/38) の低ポリモデルの共通部品。ラフ (docs/review/roughs_mounts/、tools/rough/lowpoly/mounts/) で選んだ案を、
骨のある低ポリメッシュ・アトラス・光る画素・GeckoLib の骨だけの geo・アニメにする。

座標: 1 単位 = 1/16 ブロック、y が上、前 = -Z (GeckoLib の geo の cube と同じ空間。メッシュはそのまま書き出す)。
乗る人の席はモデルの原点の真上 (MountEntity の乗る位置は x = z = 0)。席の高さ (鞍の上面) は作ったメッシュから測る。

一体 = 一つのファイル (<id>.py)。そこで Mount を作り、骨 (bone)・形 (各関数)・色 (PAL)・模様 (DETAIL)・動き (GAITS) を書く。
build_all.py が全部を作り、assets へ入れ、比べシートと動きの帯を docs/review/mounts_v2/ に書く。
"""
from __future__ import annotations

import copy
import json
import math
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[3]
LP = ROOT / "tools/rough/lowpoly"
sys.path[:0] = [str(LP / "raiju"), str(LP), str(ROOT / "tools/rough"), str(ROOT / "tools/audit")]
from lpkit import *  # noqa: E402,F401,F403
from lpkit import _mk  # noqa: E402
import lpkit  # noqa: E402
import common as RC  # noqa: E402  (雷獣の絵の道具: paint / vnoise / hash1 / BAYER)
import kit as RK  # noqa: E402     (雷獣の形の道具: sring / Prof / fbox / cbox / rrect)
import voxel  # noqa: E402
import mesh_audit as MA  # noqa: E402

ASSETS = ROOT / "src/main/resources/assets/skyabyss"
WORK = ROOT / "build/mount_lp"            # 作業の出力 (コミットしない)
REVIEW = ROOT / "docs/review/mounts_v2"
vnoise, hash1, BAYER = RC.vnoise, RC.hash1, RC.BAYER
sring, Prof, rrect = RK.sring, RK.Prof, RK.rrect
INSET = 0.02


def V(*a):
    return np.array(a, float)


def lerp(a, b, t):
    return np.asarray(a, float) * (1 - t) + np.asarray(b, float) * t


def R_(rx=0.0, ry=0.0, rz=0.0):
    """回転 (度): x → z → y の順。rx 正で前 (-Z) が上がる。"""
    return rot_y(ry) @ rot_z(rz) @ rot_x(rx)


# ====================================================================== 一体の乗り物

class Mount:
    def __init__(self, mid, title):
        self.id = mid
        self.title = title
        self.bones = {"root": (None, (0.0, 0.0, 0.0))}
        self.F = []
        self.eyes = []
        self.core = set()       # 鞍や帯をかぶせる「体の面」の部品名
        self.notes = []

    # ---- 骨
    def bone(self, name, parent, pivot):
        assert parent in self.bones, parent
        self.bones[name] = (parent, tuple(float(x) for x in pivot))
        return name

    def pv(self, name):
        return np.array(self.bones[name][1], float)

    # ---- 部品 (面の一覧に部品の名前を付けて足す)
    def add(self, pid, faces, core=False):
        faces = [f for f in faces if f is not None]
        for f in faces:
            f.part = pid
        self.F += faces
        if core:
            self.core.add(pid)
        return faces

    def mirror(self, pid, fn):
        """fn(s) を s = +1 (左、x 正), -1 の両側で作る。部品名に _l / _r を付ける。"""
        for s, k in ((1, "l"), (-1, "r")):
            self.add(f"{pid}_{k}", fn(s, k))

    def faces_of(self, parts):
        return [f for f in self.F if getattr(f, "part", None) in parts]


# ====================================================================== 形 (すべて閉じた形)

def ell(bone, m, c, r, segs=8, rings=3, rx=0.0, ry=0.0, rz=0.0, deform=None):
    return ellipsoid(bone, m, c, r, segs=segs, rings=rings, rot=R_(rx, ry, rz), deform=deform)


def tub(bone, m, pts, ra, rb=None, sides=6, hint=(1, 0, 0), phase=None, caps=(True, True)):
    ph = 180.0 / sides if phase is None else phase
    return tube(bone, m, pts, ra, rb, sides=sides, hint=hint, phase=ph, caps=caps)


def cone(bone, m, base, d, length, r, sides=4, rings=2, bend=(0, 0, 0), rb=None, hint=(0, 1, 0)):
    """根元を塞いだ先細りの棘 (根元は体に沈める)。"""
    d = nrm(np.asarray(d, float))
    base = np.asarray(base, float)
    path = [base + d * length * (i / rings) + np.asarray(bend, float) * (i / rings) ** 2 for i in range(rings + 1)]
    ras = [r * (1 - i / rings) ** 0.8 for i in range(rings + 1)]
    rbs = None if rb is None else [rb * (1 - i / rings) ** 0.8 for i in range(rings + 1)]
    return tube(bone, m, path, ras, rbs, sides=sides, hint=hint, phase=180.0 / sides, caps=(True, False))


def lof(bone, m, rings, caps=(True, True), mats=None):
    return loft(bone, m, rings, caps=tuple(m if c is True else c for c in caps), mats=mats)


def box(bone, m, c, size, R=None, taper=(1.0, 1.0), cut=0.0, shift=(0.0, 0.0)):
    return RK.fbox(bone, m, c, size, R=R, taper=taper, shift=shift, cut=cut)


def cbox(bone, m, c, size, R=None, taper=0.78, cut=0.45):
    return RK.cbox(bone, m, c, size, R=R, taper=taper, cut=cut)


def oct_ring(c, w, h, cut=0.3, R=None, axis="z"):
    """中心 c、幅 w・高さ h の面取りした八角形の輪 (断面は x-y 平面。R で回す)。"""
    R = np.eye(3) if R is None else R
    k = cut * min(w, h) / 2
    hw, hh = w / 2, h / 2
    pts = [(-hw + k, hh), (hw - k, hh), (hw, hh - k), (hw, -hh + k), (hw - k, -hh), (-hw + k, -hh), (-hw, -hh + k), (-hw, hh - k)]
    return [np.asarray(c, float) + R @ np.array([x, y, 0.0]) for x, y in pts]


def sec(z, top, bot, w, wt=None, wb=None, cx=0.0, yu=0.28, yl=0.30, sk=0.92):
    """胴の輪 (面取りした箱の断面、8 点)。"""
    return sring(z, top, bot, w, wt, wb, cx=cx, yu=yu, yl=yl, sk=sk)


def rsec(z, top, bot, w, mid=None, n=12, p=2.3, pb=None, wt=None, cx=0.0, ytilt=0.0):
    """丸い胴の輪 (超楕円、n 点。0 番が真上)。mid = いちばん幅の広い高さ (省略は上下の真ん中)。
    p: 角の丸さ (2 = 楕円、大きいほど箱に近い)。pb: 下半分の p。wt: 上半分の幅 (省略は w)。"""
    yc = (top + bot) / 2 if mid is None else mid
    ht, hb = top - yc, yc - bot
    pb = p if pb is None else pb
    wt = w if wt is None else wt
    pts = []
    for k in range(n):
        a = math.radians(90 - 360.0 * k / n)
        c, s = math.cos(a), math.sin(a)
        pp = p if s >= 0 else pb
        ww = wt if s >= 0 else w
        x = ww * math.copysign(abs(c) ** (2 / pp), c)
        y = yc + (ht if s >= 0 else hb) * math.copysign(abs(s) ** (2 / pp), s)
        pts.append(np.array([cx + x, y + ytilt * x, z]))
    return pts


def ysec(y, w, d, zc=0.0, cut=0.3, xc=0.0):
    """横に切った輪 (y 一定の平面の八角形)。脚・首の縦の筒に。"""
    k = cut * min(w, d) / 2
    hw, hd = w / 2, d / 2
    pts = [(-hw + k, -hd), (hw - k, -hd), (hw, -hd + k), (hw, hd - k), (hw - k, hd), (-hw + k, hd), (-hw, hd - k), (-hw, -hd + k)]
    return [np.array([xc + x, y, zc + z]) for x, z in pts]


def limb(bone, m, path, widths, depths, n=10, p=2.2, caps=(True, True), side=(1, 0, 0), offs=None):
    """点列 path に沿った丸い管 (超楕円の断面、n 点)。widths は side 方向の全幅、depths はそれに直交する全幅。
    offs[i] = 断面の中心のずれ (筋肉のふくらみを前・後ろへ寄せる: (side 方向, 直交方向))。"""
    path = [np.asarray(p_, float) for p_ in path]
    N = len(path)
    rings = []
    sv = np.asarray(side, float)
    for i, c in enumerate(path):
        t = nrm(path[min(i + 1, N - 1)] - path[max(i - 1, 0)])
        a = nrm(sv - np.dot(sv, t) * t)
        if np.linalg.norm(a) < 1e-6:
            a = nrm(np.cross(t, (0, 0, 1)))
        b = np.cross(t, a)
        hw, hd = widths[i] / 2, depths[i] / 2
        o = np.zeros(2) if offs is None else np.asarray(offs[i], float)
        ring = []
        for k in range(n):
            ang = math.radians(90 - 360.0 * k / n)
            cc, ss = math.cos(ang), math.sin(ang)
            x = hw * math.copysign(abs(cc) ** (2 / p), cc)
            y = hd * math.copysign(abs(ss) ** (2 / p), ss)
            ring.append(c + a * (x + o[0]) + b * (y + o[1]))
        rings.append(ring)
    return lof(bone, m, rings, caps)


def seg_loft(bone, m, path, widths, depths, cut=0.35, caps=(True, True), twist=None, up=(0, 0, -1)):
    """点列 path に沿った面取りの八角形の管 (脚・首・尾)。断面は path に垂直、幅 widths (x 方向)・奥行き depths。"""
    path = [np.asarray(p, float) for p in path]
    n = len(path)
    rings = []
    upv = np.asarray(up, float)
    for i, p in enumerate(path):
        t = nrm(path[min(i + 1, n - 1)] - path[max(i - 1, 0)])
        a = np.array([1.0, 0, 0]) if abs(t[0]) < 0.9 else nrm(np.cross(upv, t))
        a = nrm(a - np.dot(a, t) * t)
        b = np.cross(t, a)
        w, d = widths[i], depths[i]
        k = cut * min(w, d) / 2
        hw, hd = w / 2, d / 2
        pts = [(-hw + k, -hd), (hw - k, -hd), (hw, -hd + k), (hw, hd - k), (hw - k, hd), (-hw + k, hd), (-hw, hd - k), (-hw, -hd + k)]
        rings.append([p + a * x + b * y for x, y in pts])
    return lof(bone, m, rings, caps)


def fan_cones(bone, m, pts, d, length, r, sides=3, bend=(0, 0, 0)):
    out = []
    for i, p in enumerate(pts):
        out += cone(bone, m, p, d, length * (0.85 + 0.3 * hash1(i, 3)), r, sides=sides, rings=1, bend=bend)
    return out


def spiral(bone, m, base, axis_dir, out_dir, turns, R0, R1, n, r0, r1, rise=0.0, sides=5):
    """巻いた角: base から out_dir へ広がりながら axis_dir のまわりを回る。"""
    ax = nrm(np.asarray(axis_dir, float))
    u = nrm(np.asarray(out_dir, float) - np.dot(out_dir, ax) * ax)
    w = np.cross(ax, u)
    pts = []
    for i in range(n + 1):
        t = i / n
        a = 2 * math.pi * turns * t
        R = R0 + (R1 - R0) * t
        pts.append(np.asarray(base, float) + u * R * (1 - math.cos(a)) + w * R * math.sin(a) + ax * rise * t)
    ra = [r0 + (r1 - r0) * (i / n) for i in range(n + 1)]
    return tube(bone, m, pts, ra, None, sides=sides, hint=tuple(ax), phase=180.0 / sides, caps=(True, True))


# ====================================================================== 光線 (体の表面を拾う: 鞍・帯を体に沿わせる)

def tri_arrays(faces):
    A, B, C = [], [], []
    for f in faces:
        for a, b, c in f.tris():
            A.append(f.verts[a])
            B.append(f.verts[b])
            C.append(f.verts[c])
    return np.array(A), np.array(B), np.array(C)


def ray_far(tris, o, d):
    """o から d の向きの光線が当たる面のうち、いちばん遠い点 (外側の表面)。当たらなければ None。"""
    A, B, C = tris
    d = nrm(np.asarray(d, float) + np.array([0.00031, 0.00017, 0.00023]))
    o = np.asarray(o, float)
    E1, E2 = B - A, C - A
    pv = np.cross(d, E2)
    det = (E1 * pv).sum(1)
    ok = np.abs(det) > 1e-12
    inv = np.where(ok, 1.0 / np.where(ok, det, 1), 0)
    tv = o - A
    u = (tv * pv).sum(1) * inv
    qv = np.cross(tv, E1)
    v = (qv * d).sum(1) * inv
    t = (qv * E2).sum(1) * inv
    hit = ok & (u >= 0) & (v >= 0) & (u + v <= 1) & (t > 1e-6)
    if not hit.any():
        return None
    return o + d * t[hit].max()


def surface_arc(tris, z, cy, a0, a1, n, off=0.0, cx=0.0):
    """z 一定の面で、中心 (cx, cy) から角度 a0..a1 (度。0 = 真上、90 = +x) の向きの外側の表面の点 (off だけ外へ)。"""
    pts = []
    for i in range(n):
        a = math.radians(a0 + (a1 - a0) * i / max(1, n - 1))
        d = np.array([math.sin(a), math.cos(a), 0.0])
        p = ray_far(tris, (cx, cy, z), d)
        if p is None:
            p = np.array([cx, cy, z]) + d * 2
        pts.append(p + d * off)
    return pts


def top_y(tris, x, z, y0=200.0):
    """(x, z) の真上から下へ見た最初の表面の高さ。"""
    A, B, C = tris
    o = np.array([x + 0.00137, y0, z + 0.00071])
    d = np.array([0, -1.0, 0])
    E1, E2 = B - A, C - A
    pv = np.cross(d, E2)
    det = (E1 * pv).sum(1)
    ok = np.abs(det) > 1e-12
    inv = np.where(ok, 1.0 / np.where(ok, det, 1), 0)
    tv = o - A
    u = (tv * pv).sum(1) * inv
    qv = np.cross(tv, E1)
    v = (qv * d).sum(1) * inv
    t = (qv * E2).sum(1) * inv
    hit = ok & (u >= 0) & (v >= 0) & (u + v <= 1) & (t > 1e-6)
    if not hit.any():
        return None
    return float(y0 - t[hit].min())


# ====================================================================== 鞍と馬具 (体の表面に沿わせる)

def slab_rows(rows_out, thick_in, center_y):
    """外側の弧の点列 rows_out[i] (z ごと) から、輪切りの中心 (0, center_y) の向きへ thick_in だけ沈めた板の輪 (閉じた断面) の列。"""
    rings = []
    for row in rows_out:
        row = [np.asarray(p, float) for p in row]
        inner = []
        for p in row:
            d = p - np.array([0.0, center_y, p[2]])
            inner.append(p - nrm(d) * thick_in)
        rings.append(row + inner[::-1])
    return rings


def saddle(m, seat_z=0.0, cy=None, length=9.0, half_angle=58.0, blanket=1.0, seat_half=34.0, seat_len=6.5, mats=None,
           girth=True, girth_z=None, girth_cy=None, girth_open=(-60, 60), pommel=2.2, cantle=3.0, horn=True, stirrups=None, fender=(28.0, 60.0, 4.6),
           strap_from=None, front_bag=None, rear_roll=None, blanket_drop=None, cloth_trim=True):
    """鞍一式を body 骨に足す。体 (m.core) の表面を光線で拾って沿わせる。戻り値: 席の上面の高さ (y)。
    cy = 体の輪切りの中心の高さ (光線の出どころ)。stirrups = [(x, y, z), ...] あぶみの位置 (人の足先)。"""
    mats = dict(dict(pad="clothr", trim="brass", seat="leath", seat_d="leathd", metal="brass", strap="leathd"), **(mats or {}))
    tris = tri_arrays(m.faces_of(m.core))
    if cy is None:
        cy = top_y(tris, 0, seat_z) - 7.0
    zs = np.linspace(seat_z - length, seat_z + length, 5)
    # ---- 敷き布 (毛布): 背に沿う板。前後の縁はやや狭く
    rows = []
    for i, z in enumerate(zs):
        k = 1 - 0.18 * abs(i - 2) / 2
        rows.append(surface_arc(tris, z, cy, -half_angle * k, half_angle * k, 8, off=blanket))
    rings = slab_rows(rows, blanket + 1.2, cy)
    m.add("sd_blanket", lof("body", mats["pad"], rings))
    if cloth_trim:
        # 縁の飾り布 (下の縁に沿った細い帯)
        for s, k in ((1, "l"), (-1, "r")):
            edge = []
            for i, z in enumerate(zs):
                kk = 1 - 0.18 * abs(i - 2) / 2
                a = math.radians(s * half_angle * kk)
                p = ray_far(tris, (0, cy, z), (math.sin(a), math.cos(a), 0))
                edge.append(p + np.array([math.sin(a), math.cos(a), 0]) * (blanket + 0.7))
            m.add(f"sd_trim_{k}", tub("body", mats["trim"], edge, 0.55, None, sides=4, hint=(0, 1, 0)))
    # ---- 座 (革): 布の上の厚い板。前の山 (pommel)・後ろの山 (cantle)
    top_c = top_y(tris, 0, seat_z)
    seat_rows = []
    zz = np.linspace(seat_z - seat_len, seat_z + seat_len, 6)
    for i, z in enumerate(zz):
        t = i / (len(zz) - 1)
        rise = pommel * max(0.0, 1 - t * 4) ** 1.5 + cantle * max(0.0, t * 4 - 3) ** 1.3
        ha = seat_half * (0.62 + 0.38 * math.sin(math.pi * t) ** 0.6)
        row = surface_arc(tris, z, cy, -ha, ha, 7, off=blanket + 1.0)
        row = [p + np.array([0, rise * max(0.0, 1 - abs(p[0]) / 6.0) + 0.9 * math.sin(math.pi * t), 0]) for p in row]
        seat_rows.append(row)
    srings = slab_rows(seat_rows, 2.0, cy)
    m.add("sd_seat", lof("body", mats["seat"], srings, caps=(True, True)))
    seat_top = top_y(tri_arrays(m.faces_of(["sd_seat"])), 0, seat_z)
    if fender:
        # 横の垂れ (革の板): 座の下から脇腹へ垂れる
        a0, a1, fl = fender
        for s_, k in ((1, "l"), (-1, "r")):
            rows_f = []
            for z in np.linspace(seat_z - fl, seat_z + fl, 3):
                rows_f.append(surface_arc(tris, z, cy, s_ * a0, s_ * a1, 4, off=blanket + 0.9) if s_ > 0 else
                              surface_arc(tris, z, cy, s_ * a1, s_ * a0, 4, off=blanket + 0.9))
            m.add(f"sd_fender_{k}", lof("body", mats["seat"], slab_rows(rows_f, 0.7, cy)))
    if horn:
        hz = seat_z - seat_len + 0.6
        hy = top_y(tri_arrays(m.faces_of(["sd_seat"])), 0, hz + 0.4)
        m.add("sd_horn", tub("body", mats["metal"], [(0, hy - 1.6, hz + 0.6), (0, hy + 1.8, hz - 0.2), (0, hy + 2.6, hz - 0.6)], [1.0, 0.8, 1.3],
                             None, sides=6, hint=(1, 0, 0)))
    # ---- 腹帯 (体の輪切りに沿う輪。上は鞍の下に沈める)
    if girth:
        gz = seat_z + (girth_z if girth_z is not None else 0.0)
        gcy = cy if girth_cy is None else girth_cy
        pts = surface_arc(tris, gz, gcy, girth_open[1], 360 + girth_open[0], 11, off=0.45)
        m.add("sd_girth", tub("body", mats["strap"], pts, 0.55, 1.1, sides=4, hint=(0, 0, 1)))
        for s, k in ((1, "l"), (-1, "r")):
            a = math.radians(s * (half_angle * 0.85))
            p = ray_far(tris, (0, gcy, gz), (math.sin(a), math.cos(a), 0)) + np.array([math.sin(a), math.cos(a), 0]) * (blanket + 0.9)
            m.add(f"sd_buckle_{k}", box("body", mats["metal"], p, (1.0, 2.2, 2.4), R=rot_z(-s * half_angle * 0.85)))
    # ---- あぶみ: 席の横から足先まで吊る
    if stirrups:
        add_stirrups(m, stirrups, cy, seat_z, seat_half, blanket, mats)
    if rear_roll:
        rz, rr, rw = rear_roll
        ry_ = top_y(tris, 0, seat_z + rz) + rr * 0.7
        m.add("sd_roll", tub("body", mats.get("roll", "cloth"), [(-rw, ry_, seat_z + rz), (rw, ry_, seat_z + rz)], rr, None, sides=7, hint=(0, 1, 0)))
        for s, k in ((1, "l"), (-1, "r")):
            m.add(f"sd_roll_tie_{k}", tub("body", mats["strap"], [(s * rw * 0.55, ry_ - rr - 0.6, seat_z + rz), (s * rw * 0.55, ry_ + rr + 0.3, seat_z + rz)],
                                          rr + 0.25, 0.9, sides=6, hint=(0, 0, 1)))
    return seat_top


def add_stirrups(m, foot, cy, seat_z=0.0, seat_half=34.0, blanket=1.0, mats=None):
    """あぶみ: 席の横の革から人の足先 foot = (x, y, z) へ吊る。踏み板と弓形の金具。"""
    mats = dict(dict(metal="brass", strap="leathd"), **(mats or {}))
    tris = tri_arrays(m.faces_of(m.core))
    for s, k in ((1, "l"), (-1, "r")):
        fx, fy, fz = foot
        f = np.array([s * abs(fx), fy, fz])
        a = math.radians(s * seat_half * 0.95)
        d = np.array([math.sin(a), math.cos(a), 0])
        top = ray_far(tris, (0, cy, seat_z), d)
        top = (top if top is not None else np.array([s * 6.0, cy + 6, seat_z])) + d * (blanket + 1.6)
        eye = f + np.array([0, 3.4, 0])
        m.add(f"sd_leather_{k}", tub("body", mats["strap"], [top + np.array([0, 0.8, 0]), eye + np.array([0, 0.4, 0])], 0.45, 1.0, sides=4,
                                     hint=(0, 0, 1)))
        m.add(f"sd_tread_{k}", box("body", mats["metal"], f + np.array([0, -0.3, 0]), (3.4, 0.7, 2.2), cut=0.3))
        arc = [f + np.array([-1.6, -0.2, 0]), f + np.array([-1.7, 1.8, 0]), eye + np.array([-0.6, -0.4, 0]), eye + np.array([0.6, -0.4, 0]),
               f + np.array([1.7, 1.8, 0]), f + np.array([1.6, -0.2, 0])]
        m.add(f"sd_iron_{k}", tub("body", mats["metal"], arc, 0.42, 0.55, sides=4, hint=(0, 0, 1)))


# ====================================================================== 乗る人 (バニラの人の型。脚の向きは MountLook の legPitch / legSpread)

def rider_boxes(seat_top, pitch, spread, lean=0.0):
    """乗った人の箱 (geo 空間)。腰 = 席の上面 + 2.4 (人の足もと = 乗る位置 - 0.6 ブロック、腰は足もと + 12)。
    pitch: 脚の前への回転 (度、負 = 前へ)。spread: 脚の外への開き (度)。戻り値: [(名前, 中心, 半径の大きさ, 回転行列, 材質)]"""
    hip = seat_top + 2.4
    out = []
    # 体 (腰から上 12)・頭 8・腕
    out.append(("pl_body", np.array([0, hip + 6, 0]), (4, 6, 2), np.eye(3), "plc"))
    out.append(("pl_head", np.array([0, hip + 16, 0]), (4, 4, 4), np.eye(3), "pls"))
    for s, k in ((1, "l"), (-1, "r")):
        # 脚: 腰の pivot (s*1.9, hip, 0) から下へ 12。人のモデル空間の回転 Rz Ry Rx を geo 空間 (x, y を反転) に写す
        Rm = rot_z_m(-s * spread) @ rot_x_m(pitch)
        Rg = np.diag([1, -1, 1]) @ Rm @ np.diag([1, -1, 1])
        piv = np.array([s * 1.9, hip, 0])
        cen = piv + Rg @ np.array([0, -6.0, 0])
        out.append((f"pl_leg_{k}", cen, (2, 6, 2), Rg, "plc"))
        # 腕: 前へ 36 度
        Ra = np.diag([1, -1, 1]) @ rot_x_m(-36.0) @ np.diag([1, -1, 1])
        piva = np.array([s * 5.0, hip + 10, 0])
        out.append((f"pl_arm_{k}", piva + Ra @ np.array([s * 1.0, -4.0, 0]), (2, 6, 2), Ra, "pls"))
    return out


def rot_x_m(a):
    """人のモデル空間 (y 下向き) の xRot。バニラの ModelPart と同じ回し方。"""
    c, s = math.cos(math.radians(a)), math.sin(math.radians(a))
    return np.array([[1, 0, 0], [0, c, -s], [0, s, c]])


def rot_z_m(a):
    c, s = math.cos(math.radians(a)), math.sin(math.radians(a))
    return np.array([[c, -s, 0], [s, c, 0], [0, 0, 1]])


def foot_point(seat_top, pitch, spread, s=1):
    hip = seat_top + 2.4
    Rm = rot_z_m(-s * spread) @ rot_x_m(pitch)
    Rg = np.diag([1, -1, 1]) @ Rm @ np.diag([1, -1, 1])
    return np.array([s * 1.9, hip, 0]) + Rg @ np.array([0, -11.0, 0])


def rider_faces(seat_top, pitch, spread):
    F = []
    for name, c, h, R, mt in rider_boxes(seat_top, pitch, spread):
        ex, ey, ez = (R @ np.array(a, float) for a in ((1, 0, 0), (0, 1, 0), (0, 0, 1)))
        r0 = [c + sx * h[0] * ex + (-h[1]) * ey + sz * h[2] * ez for sx, sz in ((-1, -1), (1, -1), (1, 1), (-1, 1))]
        r1 = [c + sx * h[0] * ex + h[1] * ey + sz * h[2] * ez for sx, sz in ((-1, -1), (1, -1), (1, 1), (-1, 1))]
        fs = lof("root", mt, [r0, r1])
        for f in fs:
            f.part = name
        F += fs
    return F


def leg_samples(seat_top, pitch, spread):
    """脚の箱の表面と芯の標本点 (腰の 2 単位から先)。"""
    pts = []
    for s in (1, -1):
        hip = seat_top + 2.4
        Rm = rot_z_m(-s * spread) @ rot_x_m(pitch)
        Rg = np.diag([1, -1, 1]) @ Rm @ np.diag([1, -1, 1])
        piv = np.array([s * 1.9, hip, 0])
        for t in np.linspace(-3.0, -11.8, 8):
            for a in range(8):
                ang = 2 * math.pi * a / 8
                pts.append(piv + Rg @ np.array([1.8 * math.cos(ang), t, 1.8 * math.sin(ang)]))
            pts.append(piv + Rg @ np.array([0, t, 0]))
    return np.array(pts)


def penetration(m, pts, exclude=("sd_",)):
    """点のうち、乗り物のどれかの部品 (鞍の類は除く) の中にある数。"""
    parts = {}
    for f in m.F:
        p = getattr(f, "part", "")
        if any(p.startswith(e) for e in exclude):
            continue
        parts.setdefault(p, []).append(f)
    inside = np.zeros(len(pts), bool)
    for p, fs in parts.items():
        V_, T_ = [], []
        for f in fs:
            base = len(V_)
            V_ += list(f.verts)
            T_ += [[base + a, base + b, base + c] for a, b, c in f.tris()]
        V_, T_ = np.array(V_), np.array(T_)
        lo, hi = V_.min(0) - 0.01, V_.max(0) + 0.01
        cand = np.all((pts >= lo) & (pts <= hi), 1) & ~inside
        if cand.any():
            inside[cand] |= MA.inside_mask(pts[cand], V_, T_)
    return int(inside.sum())


def choose_leg_pose(m, seat_top, prefer=(-40.0, 30.0)):
    """脚が体に入らない向き (pitch, spread) を探す。好み (prefer) に近いほど良い。"""
    best = None
    for pitch in range(-85, -4, 5):
        for spread in range(4, 76, 4):
            n = penetration(m, leg_samples(seat_top, pitch, spread))
            cost = n * 4.0 + abs(pitch - prefer[0]) * 0.04 + abs(spread - prefer[1]) * 0.06
            if best is None or cost < best[0]:
                best = (cost, pitch, spread, n)
    return best[1], best[2], best[3]


# ====================================================================== 絵

def paint_mount(m, pal, detail=None, hint=256):
    faces = m.F
    size, P, N, M, L = build_atlas(faces, hint, 0)

    def ex(c, col, glow):
        if detail:
            col, glow = detail(c, col, glow)
        col, glow = eye_paint(c, col, glow, m.eyes, pal)
        return col, glow
    atlas, glow = RC.paint(faces, size, P, N, M, pal, (), ex, L)
    return size, atlas, glow


def eye_paint(c, col, glow, eyes, pal):
    """目 (球) の表面に、まぶたの縁・黒目・瞳・光の点を描く。eyes = [dict(c, r, dir, a, b, bone, iris, glow)]"""
    P, bay, owned = c["P"], c["bay"], c["owned"]
    for e in eyes:
        cen = np.array(e["c"], float)
        f = nrm(np.array(e.get("dir", (0, 0, -1)), float))
        up = np.array([0, 1.0, 0])
        rt = nrm(np.cross(up, f))
        up2 = np.cross(f, rt)
        d = P - cen
        df, dr, du = d @ f, d @ rt, d @ up2
        rad = e["r"]
        ok = owned & (np.linalg.norm(d, axis=-1) < rad * 1.6) & (df > -rad * 0.2) & (c["bones"] == e.get("bone", "head"))
        rr = np.sqrt((dr / e["a"]) ** 2 + (du / e["b"]) ** 2)
        rim = ok & (rr < 1.25 + 0.06 * bay) & (rr >= 1.0)
        col = np.where(rim[..., None], np.array(e.get("lid", (34, 24, 20)), float), col)
        sock = ok & (rr < 1.0)
        iris = np.array(e.get("iris", (60, 40, 24)), float)
        dark = np.array(e.get("pupil", (12, 10, 12)), float)
        k = np.clip(0.75 + 0.35 * (du / e["b"]), 0.5, 1.1)
        col = np.where(sock[..., None], iris * k[..., None], col)
        pr = np.sqrt((dr / (e["a"] * 0.55)) ** 2 + (du / (e["b"] * 0.62)) ** 2)
        pup = sock & (pr < 1.0)
        col = np.where(pup[..., None], dark, col)
        hl = sock & (np.sqrt(((dr + e["a"] * 0.3 * np.sign(cen[0] + 1e-9)) / (e["a"] * 0.28)) ** 2 + ((du - e["b"] * 0.38) / (e["b"] * 0.28)) ** 2) < 1.0)
        col = np.where(hl[..., None], np.array((244, 240, 228), float), col)
        if e.get("glow"):
            glow = glow | (sock & ~pup)
    return col, glow


# ---- 模様の道具 (アトラスの画素ごとの 3D 位置 P・法線 N を使う)

def cell3(P, s, seed=0):
    """3D のセル (ボロノイ) の F1・F2 距離と、いちばん近いセルの番号の乱数。"""
    q = P / s
    i0 = np.floor(q)
    f1 = np.full(q.shape[:-1], 9.0)
    f2 = np.full(q.shape[:-1], 9.0)
    idv = np.zeros(q.shape[:-1])
    for dx in (-1, 0, 1):
        for dy in (-1, 0, 1):
            for dz in (-1, 0, 1):
                c = i0 + np.array([dx, dy, dz])
                j = np.stack([RC.hash3(c, seed + 1), RC.hash3(c, seed + 2), RC.hash3(c, seed + 3)], -1)
                dd = np.linalg.norm(q - (c + j), axis=-1)
                closer = dd < f1
                f2 = np.where(closer, f1, np.minimum(f2, dd))
                idv = np.where(closer, RC.hash3(c, seed + 9), idv)
                f1 = np.where(closer, dd, f1)
    return f1, f2, idv


def shade(col, sel, k):
    """sel の画素の色を k 倍 (k はスカラーか配列)。"""
    k = np.broadcast_to(np.asarray(k, float), sel.shape)
    return np.where(sel[..., None], np.clip(col * k[..., None], 0, 255), col)


def tint(col, sel, rgb, a=1.0):
    rgb = np.asarray(rgb, float)
    a = np.broadcast_to(np.asarray(a, float), sel.shape)
    mix = col * (1 - a[..., None]) + rgb * a[..., None]
    return np.where(sel[..., None], mix, col)


def ramp_pick(ramp, v):
    ramp = np.asarray(ramp, float)
    k = np.clip(np.floor(v * len(ramp)).astype(int), 0, len(ramp) - 1)
    return ramp[k]


def fur(c, col, mats, scale=(0.9, 0.22, 0.9), amt=0.10, seed=3):
    """毛並み: 上下に伸びた細かい筋 (明るい筋と暗い筋)。"""
    P = c["P"]
    sel = np.isin(c["mats"], mats) & c["owned"]
    n = vnoise(P * np.array(scale) + seed, seed)
    n2 = vnoise(P * np.array(scale) * 2.3 + seed * 3, seed + 1)
    v = 0.65 * n + 0.35 * n2
    col = shade(col, sel & (v > 0.62), 1 + amt)
    col = shade(col, sel & (v < 0.36), 1 - amt * 1.1)
    return col


def countershade(c, col, mats, belly_rgb, top_k=0.9, belly_from=-0.25, low_y=None):
    """腹側を明るく (belly_rgb へ寄せる)、背の真上をやや暗く。"""
    N, owned, bay = c["N"], c["owned"], c["bay"]
    sel = np.isin(c["mats"], mats) & owned
    ny = N[..., 1]
    a = np.clip((belly_from - ny) / 0.6, 0, 1) * 0.75
    a = np.where(a + 0.12 * bay > 0.18, a, 0)
    col = tint(col, sel, belly_rgb, a)
    col = shade(col, sel & (ny > 0.8), top_k)
    return col


def scales(c, col, mats, size=1.6, edge=0.13, k_edge=0.72, k_hi=1.08, seed=11):
    P = c["P"]
    sel = np.isin(c["mats"], mats) & c["owned"]
    f1, f2, idv = cell3(P, size, seed)
    e = (f2 - f1) < edge
    col = shade(col, sel & e, k_edge)
    col = shade(col, sel & ~e & (f1 < 0.25), k_hi)
    col = shade(col, sel & ~e, 0.94 + 0.12 * idv)
    return col


def plates(c, col, mats, size=4.5, edge=0.07, rings=0.18, seed=21, k_edge=0.6):
    """甲羅の甲板: 大きなセル、縁の溝、年輪。"""
    P = c["P"]
    sel = np.isin(c["mats"], mats) & c["owned"]
    f1, f2, idv = cell3(P, size, seed)
    e = (f2 - f1) < edge
    ring = (np.floor((f2 - f1) / rings) % 2 == 1) & ~e
    col = shade(col, sel & ring, 0.9)
    col = shade(col, sel & e, k_edge)
    col = shade(col, sel & ~e, 0.92 + 0.16 * idv)
    return col


def speckle(c, col, mats, rgb, density=0.06, seed=5, size=0.8):
    P = c["P"]
    sel = np.isin(c["mats"], mats) & c["owned"]
    n = RC.hash3(np.floor(P / size), seed)
    return tint(col, sel & (n < density), rgb, 0.85)


def spots(c, col, mats, rgb, scale=0.18, thr=0.72, seed=7, a=0.9, up_only=False):
    P = c["P"]
    sel = np.isin(c["mats"], mats) & c["owned"]
    if up_only:
        sel = sel & (c["N"][..., 1] > -0.1)
    n = 0.7 * vnoise(P * scale + seed, seed) + 0.3 * vnoise(P * scale * 2.7 + seed, seed + 4)
    on = n + 0.05 * c["bay"] > thr
    return tint(col, sel & on, rgb, a)


def stitches(c, col, mats, rgb=(200, 170, 120), every=3):
    """革の縁の縫い目 (面の縁から 1 画素内の点線)。"""
    D, LU, LV, INS = c["D"], c["LU"], c["LV"], c["INS"]
    sel = np.isin(c["mats"], mats) & c["owned"] & INS & (D >= 1.0) & (D < 2.0) & (((LU + LV) % every) == 0)
    return tint(col, sel, rgb, 0.8)


def edge_dark(c, col, mats, k=0.78):
    sel = np.isin(c["mats"], mats) & c["owned"] & (c["D"] < 1.0)
    return shade(col, sel, k)


def gradient_to(c, col, mats, pts, radius, ramp, glow=None, glow_thr=0.55, power=1.0):
    """点 pts (先の点) に近いほど ramp の明るい側の色 (熾の尾の先・雷の羽の先)。glow に光る画素を足す。"""
    P = c["P"]
    sel = np.isin(c["mats"], mats) & c["owned"]
    d = np.full(P.shape[:-1], 1e9)
    for p in pts:
        d = np.minimum(d, np.linalg.norm(P - np.asarray(p, float), axis=-1))
    t = np.clip(1 - d / radius, 0, 1) ** power
    v = np.clip(t + 0.08 * c["bay"] + 0.06 * (c["lit"]), 0, 0.999)
    col = np.where((sel & (t > 0.02))[..., None], ramp_pick(ramp, v), col)
    if glow is not None:
        glow = glow | (sel & (t > glow_thr))
        return col, glow
    return col


def bands_y(c, col, mats, period, width, k=0.8, phase=0.0, wobble=0.0):
    P = c["P"]
    sel = np.isin(c["mats"], mats) & c["owned"]
    y = P[..., 1] + wobble * np.sin(P[..., 2] * 0.7 + P[..., 0] * 0.5)
    on = ((y + phase) % period) < width
    return shade(col, sel & on, k)


def feather_rows(c, col, mats, row=2.0, k_edge=0.74, k_tip=1.1, axis=1, wav=1.4):
    """羽の列: 横に並んだうろこ形 (上から下へ重なる)。"""
    P = c["P"]
    sel = np.isin(c["mats"], mats) & c["owned"]
    a = P[..., axis]
    u = P[..., 0] * 0.9 + P[..., 2] * 0.9
    r = (a + wav * np.abs(np.sin(u * 0.9))) / row
    fr = r - np.floor(r)
    col = shade(col, sel & (fr < 0.16), k_edge)
    col = shade(col, sel & (fr > 0.78), k_tip)
    return col


# ====================================================================== 書き出し

def export(m, size, atlas, glow, out_dir, extra=None):
    out_dir = Path(out_dir)
    out_dir.mkdir(parents=True, exist_ok=True)
    name = "mount_" + m.id
    Image.fromarray(atlas, "RGBA").save(out_dir / f"{name}_atlas.png")
    gm = np.zeros((size, size, 4), np.uint8)
    gm[glow] = (255, 255, 255, 255)
    has_glow = bool(glow.any())
    if has_glow:
        Image.fromarray(gm, "RGBA").save(out_dir / f"{name}_glowmask.png")
    bs = {b: {"vertices": [], "uvs": [], "triangles": []} for b in m.bones}
    for f in m.F:
        b = bs[f.bone]
        base = len(b["vertices"])
        cen = f.uv.mean(0)
        for p, uv in zip(f.verts, f.uv):
            d = cen - uv
            nn = np.linalg.norm(d)
            uv = uv + d / nn * min(INSET, nn * 0.4) if nn > 1e-9 else uv
            b["vertices"].append([round(float(x) + 0.0, 4) for x in p])
            b["uvs"].append([round(float(uv[0]) / size, 5), round(float(uv[1]) / size, 5)])
        for a, c, d in f.tris():
            b["triangles"].append([base + a, base + c, base + d])
    tot, out = 0, {}
    for k, (parent, pivot) in m.bones.items():
        b = bs[k]
        b["triangle_count"] = len(b["triangles"])
        tot += b["triangle_count"]
        out[k] = {"parent": parent, "pivot": [float(x) for x in pivot], **b}
    data = {"format_version": 1, "name": name, "units": "1/16 block, y up, front = -Z",
            "uv": "normalized, origin top-left (v down)", "texture": f"{name}_atlas.png", "texture_size": [size, size],
            "texels_per_unit": TEX, "uv_inset_texels": INSET, "triangle_count": tot,
            "note": "mount look (docs/design/38). Generated by tools/gen/mobs/mounts_lp/" + m.id + ".py"}
    if extra:
        data.update(extra)
    data["bones"] = out
    (out_dir / f"{name}_mesh.json").write_text(json.dumps(data, ensure_ascii=False), encoding="utf-8")
    return data, has_glow


def cube_geo(m, path):
    """箱の geo (LowPoly を切ったときの代わり、と骨の定義の元)。骨ごとにメッシュを包む箱 1 つ。"""
    bounds = {}
    for f in m.F:
        lo, hi = bounds.get(f.bone, (np.full(3, 1e9), np.full(3, -1e9)))
        bounds[f.bone] = (np.minimum(lo, f.verts.min(0)), np.maximum(hi, f.verts.max(0)))
    bones = []
    for k, (parent, pivot) in m.bones.items():
        b = {"name": k, "pivot": [round(float(x), 4) for x in pivot]}
        if parent:
            b["parent"] = parent
        if k in bounds and not k.startswith("sd_"):
            lo, hi = bounds[k]
            # GeckoLib は cube の x を反転して焼く。メッシュと同じ所に出すため origin の x は -hi
            o = [-float(hi[0]), float(lo[1]), float(lo[2])]
            s = [float(hi[0] - lo[0]), float(hi[1] - lo[1]), float(hi[2] - lo[2])]
            b["cubes"] = [{"origin": [round(x, 3) for x in o], "size": [round(max(x, 0.01), 3) for x in s], "uv": [0, 0]}]
        bones.append(b)
    geo = {"format_version": "1.12.0", "minecraft:geometry": [{
        "description": {"identifier": "geometry.mount_" + m.id, "texture_width": 16, "texture_height": 16,
                        "visible_bounds_width": 6, "visible_bounds_height": 5, "visible_bounds_offset": [0, 1.5, 0]},
        "bones": bones}]}
    Path(path).write_text(json.dumps(geo, ensure_ascii=False, indent=1), encoding="utf-8")


# ====================================================================== 動き (molang の式の小さな言葉)
# 一つの式 = offset + Σ amp * sin(src * k + ph)。src は "t" (時間: 周期 period 秒) か "d" (歩いた距離: 一歩の長さ stride ブロック)。
# GeckoLib の回転の値 (度): x 正 = 前 (-Z) が下がる / 脚の先が後ろへ。

class W:
    """波の和。W(off).s(amp, k, ph, src)"""

    def __init__(self, off=0.0):
        self.off = off
        self.terms = []

    def s(self, amp, k=1, ph=0.0, src="d"):
        self.terms.append((amp, k, ph, src))
        return self

    def eval(self, t, d, period, stride):
        v = self.off
        for amp, k, ph, src in self.terms:
            x = t * 360.0 / period if src == "t" else d * 360.0 / stride
            v += amp * math.sin(math.radians(x * k + ph))
        return v

    def molang(self, period, stride):
        if not self.terms:
            return round(self.off, 3)
        parts = [f"{self.off:.3f}"] if abs(self.off) > 1e-6 else []
        for amp, k, ph, src in self.terms:
            if src == "t":
                x = f"query.anim_time*{360.0 * k / period:.4f}"
            else:
                x = f"variable.mount_dist*{360.0 * k / stride:.4f}"
            if ph:
                x += f"+{ph % 360:.1f}"
            parts.append(f"{amp:.3f}*math.sin({x})")
        return "+".join(parts).replace("+-", "-")


def Z():
    return W(0.0)


class Gait:
    """一つのアニメ (idle / walk / run)。bones = {骨: {"rot": (W, W, W), "pos": (W, W, W)}}"""

    def __init__(self, name, period=2.0, stride=1.0, loop=True):
        self.name = name
        self.period = period
        self.stride = stride
        self.loop = loop
        self.bones = {}
        self.keys = None          # 跳び: {骨: {"rot": {時刻: (x,y,z)}, ...}}
        self.length = period

    def rot(self, bone, x=None, y=None, z=None):
        b = self.bones.setdefault(bone, {})
        b["rot"] = tuple(w if w is not None else W(0) for w in (x, y, z))
        return self

    def pos(self, bone, x=None, y=None, z=None):
        b = self.bones.setdefault(bone, {})
        b["pos"] = tuple(w if w is not None else W(0) for w in (x, y, z))
        return self

    def pose_at(self, t, d):
        out = {}
        if self.keys is not None:
            for bone, ch in self.keys.items():
                out[bone] = {k: _interp(v, t) for k, v in ch.items()}
            return out
        for bone, ch in self.bones.items():
            out[bone] = {k: tuple(w.eval(t, d, self.period, self.stride) for w in v) for k, v in ch.items()}
        return out

    def to_json(self):
        a = {"loop": True if self.loop is True else self.loop, "animation_length": round(self.length, 4), "bones": {}}
        if self.keys is not None:
            for bone, ch in self.keys.items():
                bj = {}
                for k, kv in ch.items():
                    name = "rotation" if k == "rot" else "position"
                    bj[name] = {f"{t:.4f}": {"vector": [round(x, 3) for x in v], "easing": "easeInOutSine"} for t, v in sorted(kv.items())}
                a["bones"][bone] = bj
            return a
        for bone, ch in self.bones.items():
            bj = {}
            for k, v in ch.items():
                name = "rotation" if k == "rot" else "position"
                bj[name] = [w.molang(self.period, self.stride) for w in v]
            a["bones"][bone] = bj
        return a


def _interp(kv, t):
    ts = sorted(kv)
    if t <= ts[0]:
        return tuple(kv[ts[0]])
    if t >= ts[-1]:
        return tuple(kv[ts[-1]])
    for a, b in zip(ts[:-1], ts[1:]):
        if a <= t <= b:
            u = (t - a) / (b - a)
            u = 0.5 - 0.5 * math.cos(math.pi * u)
            return tuple(np.asarray(kv[a]) * (1 - u) + np.asarray(kv[b]) * u)


def write_anims(gaits, path):
    data = {"format_version": "1.8.0", "animations": {f"animation.mount.{g.name}": g.to_json() for g in gaits}}
    Path(path).write_text(json.dumps(data, ensure_ascii=False, indent=1), encoding="utf-8")


# ---- 姿勢をメッシュにかける (確かめの絵用)

def anim_R(rx, ry, rz):
    """GeckoLib のアニメの回転 (度) を作図空間の回転へ。x 正 = 前が下がる。"""
    return rot_z(rz) @ rot_y(ry) @ rot_x(-rx)


def posed(m, pose, faces=None):
    faces = m.F if faces is None else faces
    cache = {}

    def M(bone):
        if bone in cache:
            return cache[bone]
        parent, pv = m.bones[bone]
        pv = np.array(pv, float)
        ch = pose.get(bone, {})
        r = ch.get("rot", (0, 0, 0))
        p = ch.get("pos", (0, 0, 0))
        R = anim_R(*r)
        t = pv - R @ pv + np.array([-p[0], p[1], p[2]])
        if parent is None:
            res = (R, t)
        else:
            PR, Pt = M(parent)
            res = (PR @ R, PR @ t + Pt)
        cache[bone] = res
        return res
    out = []
    for f in faces:
        R, t = M(f.bone)
        g = copy.copy(f)
        g.verts = f.verts @ R.T + t
        n = g.verts
        nn = np.cross(n[2] - n[0], n[3 % len(n)] - n[1]) if len(n) == 4 else np.cross(n[1] - n[0], n[2] - n[0])
        nn = nrm(nn)
        g.normal = nn if np.dot(nn, R @ f.normal) >= 0 else -nn
        out.append(g)
    return out


# ====================================================================== 確かめの絵

def bg_panel(w, h):
    return Image.fromarray(np.clip(voxel._background(w, h, "dark"), 0, 255).astype(np.uint8))


def view(faces, atlas, glow, yaw, pitch, size, center, scale, ground=True, ref=True):
    img = render(faces, atlas, glow, yaw, pitch, size, center, scale)
    bg = bg_panel(*size)
    d = ImageDraw.Draw(bg, "RGBA")
    Vm = rot_x(pitch) @ rot_y(yaw)
    cen = Vm @ np.asarray(center, float)

    def scr(p):
        c = Vm @ np.asarray(p, float)
        return ((c[0] - cen[0]) * scale + size[0] / 2, -(c[1] - cen[1]) * scale + size[1] / 2)
    if ground:
        gy = scr((0, 0, 0))[1]
        d.line([(0, gy), (size[0], gy)], fill=(120, 130, 150, 90), width=1)
        if ref:
            x = 10
            top = gy - 28.8 * scale
            d.line([(x, gy), (x, top)], fill=(255, 220, 140, 150), width=2)
            d.line([(x - 4, top), (x + 4, top)], fill=(255, 220, 140, 150), width=2)
            d.text((x + 6, top - 2), "人 1.8", font=voxel.font(11), fill=(255, 220, 140, 190))
    bg.paste(img, (0, 0), img)
    return bg


# ====================================================================== 動きの組み立て

def leg_cycle(g, up, lo, A, B, ph, base=0.0, lo_base=0.0, lag=-90.0, src="d"):
    """一本の脚の振り: 上の骨は A*sin(位相)、下の骨は前へ振り戻すあいだ曲がる (0..B)。x 正 = 足先が後ろ。"""
    g.rot(up, x=W(base).s(A, 1, ph, src))
    if lo:
        g.rot(lo, x=W(lo_base + B / 2).s(B / 2, 1, ph + lag, src))


def quad(g, legs, A, B, phases, Ah=None, Bh=None, base=(0.0, 0.0), lo_base=(0.0, 0.0), lag=-90.0):
    """四つ足。legs = [fl, fr, bl, br] の (上, 下) の骨。phases = 同じ順の位相 (度)。"""
    Ah = A if Ah is None else Ah
    Bh = B if Bh is None else Bh
    for i, ((up, lo), ph) in enumerate(zip(legs, phases)):
        hind = i >= 2
        leg_cycle(g, up, lo, Ah if hind else A, Bh if hind else B, ph, base[1 if hind else 0], lo_base[1 if hind else 0], lag)


WALK_PH = (90.0, 270.0, 0.0, 180.0)      # 横の順の歩き: 左後 → 左前 → 右後 → 右前
TROT_PH = (0.0, 180.0, 180.0, 0.0)       # 対角の速歩
GALLOP_PH = (0.0, 30.0, 200.0, 230.0)    # 回る襲歩: 前の二本が続けて、後ろの二本が続けて
BOUND_PH = (0.0, 12.0, 180.0, 192.0)     # 跳ね: 前・後ろがほぼそろう


def jump_keys(spec):
    """跳び: spec = {骨: {"rot": {時刻: (x, y, z)}}}"""
    g = Gait("jump", period=0.8, loop="hold_on_last_frame")
    g.keys = spec
    g.length = max(max(kv) for ch in spec.values() for kv in ch.values())
    return g
