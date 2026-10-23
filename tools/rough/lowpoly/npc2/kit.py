"""NPC v2: 語り部と同じ作り (低ポリのメッシュ + 手描き風のアトラス + 部品ごとの絵の決まり) で、人ごとに形を変えて組む道具。

語り部 (tools/rough/lowpoly/storyteller) の描き方 (storyteller/common.py の paint: 材質ごとの階調・光・陰・筋・縞) と、
顔のドット (2 画素/単位で目・眉・口を置く) をそのまま使い、体・髪・かぶり物・服の重ね・持ち物を、人の仕様 (cast.py) から組む。

骨 (前 = -Z、y が上。root は回さない。係の人・町の人と同じ向き):
  root > body > head > hairB (後ろ髪・結い・頭巾の垂れ。揺れる)
              > armL / armR (上腕) > foreL / foreR (肘から先・手) > prop_l / prop_r (手の持ち物の受け口)
              > legL / legR
              > hem (腰から下の裾。揺れる)
              > prop_back (背の荷の受け口)
持ち物は体のメッシュに入れず、受け口ごとの別メッシュ (lowpoly/<名>_<受け口>.json、絵も別) にする。
座標: 1 単位 = 1/16 ブロック。人の背丈は約 29.5 単位。
"""
from __future__ import annotations

import importlib.util
import json
import math
import sys
from pathlib import Path

import numpy as np

HERE = Path(__file__).resolve().parent
LP = HERE.parent
ROOT = HERE.parents[3]
sys.path.insert(0, str(LP))
sys.path.insert(0, str(ROOT / "tools/rough"))
from lpkit import *  # noqa
from lpkit import _mk  # noqa

_spec = importlib.util.spec_from_file_location("st_common", LP / "storyteller" / "common.py")
ST = importlib.util.module_from_spec(_spec)
sys.path.insert(0, str(LP / "storyteller"))
_spec.loader.exec_module(ST)
paint_st, vnoise, hash1, BAYER = ST.paint, ST.vnoise, ST.hash1, ST.BAYER

INSET = 0.02
SOCKETS = ("prop_r", "prop_l", "prop_back")


def part(pid, faces):
    for f in faces:
        f.part = pid
    return faces


def V(*a):
    return np.array(a, float)


# ====================================================================== 色

def ramp(base, n=5, lo=0.34, hi=1.34, cool=(8, 10, 22)):
    """基準色から暗→明の n 段 (暗い側は少し青へ、明るい側は少し白へ)。"""
    out = []
    for k in range(n):
        t = lo + (hi - lo) * k / (n - 1)
        c = np.array(base, float) * t
        if t < 1:
            c = c + np.array(cool, float) * (1 - t)
        else:
            c = c + (255 - c) * (t - 1) * 0.35
        out.append(tuple(int(v) for v in np.clip(c, 0, 255)))
    return out


def skin_ramp(base):
    b = np.array(base, float)
    warm = [b * 0.52 + np.array([10, -2, 0]), b * 0.74 + np.array([8, 0, 0]), b * 0.9, b, b * 1.08 + 12]
    return [tuple(int(v) for v in np.clip(c, 0, 255)) for c in warm]


def cloth(col, **kw):
    d = dict(ramp=ramp(col), base=0.56, lit=0.30, noise=0.10, facevar=0.05, crack=0, min=0, streak=0.10, ao=0.10)
    d.update(kw)
    return d


# ====================================================================== 形の道具

def ringe(y, rx, zf, zb, n=10, cx=0.0, zc=0.0, phase=None):
    """楕円の輪 (前 zf < 0、後ろ zb > 0)。正面が面になる向き (phase = 180/n)。"""
    ph = 180.0 / n if phase is None else phase
    pts = []
    for k in range(n):
        a = math.radians(ph + 360.0 * k / n)
        s, c = math.sin(a), math.cos(a)
        z = zc + (zf * c if c >= 0 else zb * -c)
        pts.append(np.array([cx + rx * s, y, z]))
    return pts


def prof_at(spec, y):
    """spec = [(y, rx, zf, zb), ...] を y で補間 (範囲外は端の値)。"""
    st = sorted(spec)
    y = min(max(y, st[0][0]), st[-1][0])
    for a, b in zip(st[:-1], st[1:]):
        if a[0] <= y <= b[0]:
            t = (y - a[0]) / max(b[0] - a[0], 1e-9)
            return [a[i] * (1 - t) + b[i] * t for i in range(1, 4)]
    return list(st[-1][1:])


def surf(spec, y, ang, off=0.0, cx=0.0, zc=0.0):
    """楕円の輪の面の上の点 (ang = 0 が正面 -Z、90 が +x)。off だけ外へ。"""
    rx, zf, zb = prof_at(spec, y)
    a = math.radians(ang)
    s, c = math.sin(a), math.cos(a)
    rz = -zf if c >= 0 else zb
    x = (rx + off) * s
    z = -(rz + off) * c
    return np.array([cx + x, y, zc + z])


def shell(bone, mat, spec, y_of_ang, y_top, off, inset=0.35, n=16, rows=4, cx=0.0, zc=0.0, top_off=None, angs=None,
          lift=None):
    """頭や胴の面に沿う殻 (髪・頭巾・肩掛け)。縁の高さ y_of_ang(ang) から y_top まで、面から off 浮かせる。
    縁は面の内側 (inset) へ折り込む (縁から中が見えない)。ang の列が閉じていれば輪、angs を渡せば開いた帯。"""
    closed = angs is None
    angs = list(np.linspace(0, 360, n, endpoint=False)) if closed else list(angs)
    top_off = off if top_off is None else top_off
    grid_pts = []
    # 縁の内側 (頭の中へ沈める)
    grid_pts.append([surf(spec, y_of_ang(a), a, -inset, cx, zc) for a in angs])
    for r in range(rows + 1):
        t = r / rows
        row = []
        for a in angs:
            y0 = y_of_ang(a)
            y = y0 + (y_top - y0) * t
            o = off + (top_off - off) * t
            if lift:
                o += lift(a, t)
            row.append(surf(spec, y, a, o, cx, zc))
        grid_pts.append(row)
    F = []
    ref = np.array([cx, (y_top + min(y_of_ang(a) for a in angs)) / 2, zc])
    cols = len(angs) if closed else len(angs) - 1
    for i in range(len(grid_pts) - 1):
        for j in range(cols):
            j2 = (j + 1) % len(angs)
            q = [grid_pts[i][j], grid_pts[i][j2], grid_pts[i + 1][j2], grid_pts[i + 1][j]]
            f = _mk(q, bone, mat, ref)
            if f is not None:
                F.append(f)
    # 頂の蓋 (最後の輪を閉じる)
    top = grid_pts[-1]
    cen = np.mean(top, 0) + np.array([0, 0.25, 0])
    for j in range(cols):
        j2 = (j + 1) % len(angs)
        f = _mk([top[j], top[j2], cen], bone, mat, ref)
        if f is not None:
            F.append(f)
    if not closed:
        # 開いた帯の両端を閉じる (端の列の内側と外側をつなぐ)
        for j in (0, len(angs) - 1):
            col = [grid_pts[i][j] for i in range(len(grid_pts))]
            for i in range(len(col) - 1):
                c2 = surf(spec, y_of_ang(angs[j]) + (y_top - y_of_ang(angs[j])) * max(0, i - 1) / rows, angs[j], -inset, cx, zc)
                f = _mk([col[i], col[i + 1], c2], bone, mat, ref)
                if f is not None:
                    F.append(f)
    return F


def box(bone, m, c, h, taper=1.0, R=None, mats=None):
    """中心 c、半分の大きさ h = (hx, hy, hz) の箱 (上へ taper で細る)。R は中心まわりの回転。"""
    c = np.asarray(c, float)
    hx, hy, hz = h
    R = np.eye(3) if R is None else R

    def sq(y, k):
        return [c + R @ np.array(p) for p in ((-hx * k, y, -hz * k), (hx * k, y, -hz * k), (hx * k, y, hz * k), (-hx * k, y, hz * k))]
    return loft(bone, m, [sq(-hy, 1.0), sq(hy, taper)], caps=(m, m), mats=mats)


def rod(bone, m, a, b, r=0.35, r1=None, sides=5, caps=(True, True), hint=(1, 0, 0)):
    return tube(bone, m, [a, b], ra=[r, r if r1 is None else r1], sides=sides, caps=caps, hint=hint)


def disc(bone, m, c, r, h, n=10, axis="y", r_top=None):
    """円柱 (軸 y / x / z)。c = 中心、h = 半分の高さ。"""
    c = np.asarray(c, float)
    r_top = r if r_top is None else r_top
    rings = []
    for yy, rr in ((-h, r), (h, r_top)):
        pts = []
        for k in range(n):
            a = 2 * math.pi * (k + 0.5) / n
            u, v = rr * math.cos(a), rr * math.sin(a)
            if axis == "y":
                p = (u, yy, v)
            elif axis == "x":
                p = (yy, u, v)
            else:
                p = (u, v, yy)
            pts.append(c + np.array(p))
        rings.append(pts)
    return loft(bone, m, rings, caps=(m, m))


def tri_strip_cap(F, ring_pts, bone, mat, ref):
    f = _mk(list(ring_pts), bone, mat, ref)
    if f is not None:
        F.append(f)


# ====================================================================== 体の型

# 胴の断面 (y, 半幅, 前, 後ろ)。y は絶対の高さ。体の型ごとに少し変える
TORSO = {
    "avg":    [(10.6, 3.9, -2.6, 2.5), (12.4, 3.8, -2.5, 2.3), (15.4, 4.1, -2.8, 2.3), (18.4, 4.6, -3.0, 2.5), (20.3, 4.8, -2.7, 2.4), (21.4, 3.4, -2.1, 2.0), (22.1, 1.9, -1.6, 1.6)],
    "slim":   [(10.6, 3.6, -2.4, 2.3), (12.4, 3.4, -2.3, 2.1), (15.4, 3.7, -2.5, 2.1), (18.4, 4.2, -2.8, 2.3), (20.3, 4.4, -2.5, 2.2), (21.4, 3.1, -1.9, 1.8), (22.1, 1.8, -1.5, 1.5)],
    "lady":   [(10.6, 4.0, -2.5, 2.6), (12.6, 3.4, -2.2, 2.1), (15.2, 3.6, -2.6, 2.1), (17.6, 4.0, -3.2, 2.2), (19.4, 4.3, -2.9, 2.2), (20.6, 4.2, -2.4, 2.1), (21.4, 3.0, -1.9, 1.8), (22.1, 1.7, -1.4, 1.4)],
    "stout":  [(10.6, 4.4, -3.2, 2.7), (12.4, 4.6, -3.8, 2.6), (15.0, 4.8, -4.0, 2.6), (18.0, 4.9, -3.4, 2.6), (20.3, 5.0, -2.8, 2.5), (21.4, 3.6, -2.2, 2.1), (22.1, 2.0, -1.7, 1.7)],
    "broad":  [(10.6, 4.1, -2.7, 2.6), (12.4, 4.0, -2.6, 2.4), (15.4, 4.6, -3.0, 2.5), (18.4, 5.3, -3.3, 2.7), (20.3, 5.5, -3.0, 2.6), (21.4, 3.8, -2.3, 2.2), (22.1, 2.1, -1.7, 1.7)],
    "old":    [(10.6, 3.8, -2.6, 2.6), (12.4, 3.7, -2.7, 2.5), (15.4, 4.0, -2.8, 2.7), (18.4, 4.4, -2.7, 3.0), (20.1, 4.5, -2.2, 3.0), (21.2, 3.2, -1.6, 2.4), (21.9, 1.8, -1.0, 1.8)],
}
SHOULDER = {"avg": 4.7, "slim": 4.3, "lady": 4.3, "stout": 4.9, "broad": 5.4, "old": 4.4}

# 頭の断面 (y, 半幅, 前, 後ろ)。顔の前は平らに近く、顎は細い
HEAD = {
    "avg":   [(21.0, 1.2, -2.3, 0.6), (21.7, 2.4, -3.2, 1.8), (22.9, 3.3, -3.65, 2.9), (24.4, 3.75, -3.85, 3.5), (26.0, 3.9, -3.85, 3.8), (27.6, 3.7, -3.55, 3.7), (28.9, 2.9, -2.8, 3.0), (29.7, 1.4, -1.4, 1.6)],
    "round": [(21.0, 1.6, -2.4, 0.8), (21.7, 2.9, -3.3, 2.0), (22.9, 3.7, -3.75, 3.0), (24.4, 4.05, -3.9, 3.6), (26.0, 4.1, -3.9, 3.85), (27.6, 3.85, -3.6, 3.75), (28.9, 3.0, -2.85, 3.0), (29.6, 1.5, -1.5, 1.6)],
    "long":  [(20.6, 1.1, -2.4, 0.6), (21.4, 2.3, -3.2, 1.8), (22.7, 3.1, -3.6, 2.8), (24.3, 3.55, -3.8, 3.4), (26.0, 3.7, -3.8, 3.7), (27.7, 3.55, -3.5, 3.6), (29.0, 2.8, -2.75, 2.9), (29.9, 1.3, -1.3, 1.5)],
    "square": [(21.0, 2.0, -2.6, 1.0), (21.7, 3.1, -3.4, 2.0), (22.9, 3.65, -3.75, 3.0), (24.4, 3.85, -3.9, 3.5), (26.0, 3.95, -3.9, 3.8), (27.6, 3.75, -3.6, 3.7), (28.9, 2.95, -2.85, 3.0), (29.7, 1.4, -1.4, 1.6)],
}
EYE_Y = 25.3     # 目の中心の高さ (頭の型によらず同じにして、顔のドットの位置を揃える)


def head_spec(kind, dy=0.0):
    return [(y + dy, rx, zf, zb) for (y, rx, zf, zb) in HEAD[kind]]


# ====================================================================== 部品

def build_head(c):
    """頭・首・鼻・耳。c は人の仕様。"""
    hs = head_spec(c.get("head", "avg"), c.get("head_dy", 0.0))
    c["_hs"] = hs
    F = []
    rings = [ringe(y, rx, zf, zb, n=12) for (y, rx, zf, zb) in hs]
    F += part("head", loft("head", "skin", rings, caps=("skin", "skin")))
    # 首: 胴の上の面から頭の中へ (両端とも沈める)
    F += part("neck", tube("head", "skin", [V(0, 20.9, 0.2), V(0, 22.6, 0.3)], ra=[1.45, 1.35], sides=6, caps=(True, True)))
    # 鼻 (くさび)。先は顔の前へ 1 単位ほど
    nz = prof_at(hs, 24.3)[1]
    nose = c.get("nose", 1.0)
    F += part("nose", spike("head", "skin", V(0, 24.75, nz + 0.35), (0, -0.35, -1), 0.95 * nose + 0.2, 0.6 * nose, sides=4,
                            rings=1, hint=(0, 1, 0), phase=45.0))
    for s in (-1, 1):
        rx = prof_at(hs, 25.0)[0]
        F += part(f"ear{s}", ellipsoid("head", "skin", (s * (rx - 0.05), 25.0, 0.3), (0.55, 1.05, 0.8), segs=5, rings=2))
    return F


def hair_shell(c, h):
    """髪の殻。h = dict(front, side, back, top, off, fringe, part)。"""
    hs = c["_hs"]
    fr, sd, bk = h.get("front", 27.4), h.get("side", 25.0), h.get("back", 23.4)
    peak = h.get("widow", 0.0)

    def y_of(a):
        a = (a + 180) % 360 - 180
        t = abs(a) / 180.0
        # 正面 → こめかみ (60 度) → 耳 (100 度) → うなじ (180 度)
        if t < 0.33:
            u = t / 0.33
            y = fr * (1 - u) + (fr - 0.8) * u - peak * (1 - u) ** 2
        elif t < 0.56:
            u = (t - 0.33) / 0.23
            y = (fr - 0.8) * (1 - u) + sd * u
        else:
            u = (t - 0.56) / 0.44
            y = sd * (1 - u) + bk * u
        return y
    top = hs[-1][0] + 0.15
    off = h.get("off", 0.45)
    F = shell("head", "hair", hs, y_of, top, off, inset=0.4, n=16, rows=4, top_off=h.get("top_off", off + 0.35),
              lift=h.get("lift"))
    return part("hair", F)


def fringe(c, h, mat="hair"):
    """前髪・横の房 (下向きの楔)。h["fringe"] = [(x, y, 長さ, 幅, 曲がり x)]。"""
    hs = c["_hs"]
    F = []
    for i, (x, y, ln, r, bx) in enumerate(h.get("fringe", [])):
        a = math.degrees(math.asin(max(-0.95, min(0.95, x / max(prof_at(hs, y)[0], 1e-6)))))
        base = surf(hs, y, a, h.get("off", 0.45) * 0.6)
        F += part(f"fr{i}", spike("head", mat, base, (bx * 0.3, -1, -0.25), ln, r, sides=3, rings=1, hint=(0, 0, 1),
                                  phase=0.0, bend=(bx, 0, -0.35)))
    return F


def build_hair(c):
    h = c.get("hair")
    if not h:
        return []
    style = h.get("style", "short")
    F = []
    covered = (c.get("hat") or {}).get("kind") in ("kerchief", "hood", "helmet")   # 布や兜の下の髪の殻は見えないので作らない
    if style != "bald" and not covered:
        F += hair_shell(c, h)
    else:
        # 禿げ: 耳の上から後ろへの帯だけ
        hs = c["_hs"]
        F += part("hair", shell("head", "hair", hs, lambda a: 24.6 if abs((a + 180) % 360 - 180) > 70 else 30.0, 27.2, 0.4,
                                angs=np.linspace(75, 285, 11), rows=2))
    F += fringe(c, h)
    hs = c["_hs"]
    if style == "bun":
        F += part("bun", ellipsoid("hairB", "hair", (0, 28.6, 3.9), (1.7, 1.5, 1.4), segs=7, rings=3))
        F += part("bun_tie", disc("hairB", "hairtie", (0, 28.0, 3.3), 0.9, 0.35, n=6, axis="z"))
    elif style == "ponytail":
        F += part("tie", ellipsoid("hairB", "hairtie", (0, 26.6, 4.05), (0.8, 0.8, 0.6), segs=6, rings=2))
        F += part("tail", tube("hairB", "hair", [V(0, 26.8, 3.6), V(0, 26.2, 4.9), V(0, 23.6, 5.4), V(0, 20.8, 5.0)],
                               ra=[0.9, 1.2, 1.1, 0.0], sides=6, caps=(True, False), hint=(1, 0, 0)))
    elif style == "braid":
        pts = [V(0, 25.4, 3.8), V(0.3, 23.6, 4.4), V(0.4, 21.6, 4.6), V(0.3, 19.6, 4.5), V(0.2, 17.8, 4.3)]
        for i, p in enumerate(pts):
            F += part(f"br{i}", ellipsoid("hairB", "hair", p, (0.95 - i * 0.07, 1.15, 0.8), segs=6, rings=2))
        F += part("br_tie", ellipsoid("hairB", "hairtie", pts[-1] + V(0, -1.1, 0), (0.55, 0.45, 0.5), segs=5, rings=1))
    elif style == "long":
        # 背へ垂れる髪の板 (頭の後ろから肩甲骨まで)
        rows = [ringe(y, rx, zf, zb, n=10, zc=zc) for (y, rx, zf, zb, zc) in
                [(25.2, 3.5, -1.0, 3.4, 0.6), (23.0, 3.6, -0.4, 2.6, 1.6), (20.4, 3.7, -0.2, 1.4, 3.2), (18.0, 3.5, -0.2, 1.1, 3.6)]]
        F += part("long", loft("hairB", "hair", rows, caps=(False, "hair")))
    elif style == "topknot":
        F += part("knot", ellipsoid("head", "hair", (0, 30.2, 0.6), (1.2, 1.3, 1.2), segs=6, rings=2))
        F += part("knot_tie", disc("head", "hairtie", (0, 29.4, 0.6), 0.85, 0.3, n=6))
    elif style == "sidelocks":
        for s in (-1, 1):
            F += part(f"lock{s}", tube("head", "hair", [V(s * 3.6, 26.0, -1.4), V(s * 3.9, 24.0, -1.6), V(s * 3.7, 21.6, -1.4)],
                                       ra=[0.8, 0.9, 0.0], sides=5, caps=(True, False)))
    return F


def build_beard(c):
    b = c.get("beard")
    if not b:
        return []
    hs = c["_hs"]
    F = []
    if b in ("full", "long"):
        ln = 2.2 if b == "full" else 4.0
        # 顎の周りに沿う房 (頬から顎の先へ)
        rows = []
        for y, k in ((24.0, 1.0), (22.8, 1.0), (21.6, 0.85), (21.6 - ln * 0.6, 0.62), (21.6 - ln, 0.22)):
            yy = min(y, 21.6)
            rx, zf, zb = prof_at(hs, max(yy, 21.2))
            row = []
            for a in np.linspace(-100, 100, 9):
                p = surf(hs, max(yy, 21.2), a, 0.45)
                if y < 21.6:
                    p = p * np.array([k, 1, 1]) + np.array([0, y - 21.2, -0.35 * (21.6 - y)])
                row.append(p)
            rows.append(row)
        inner = [surf(hs, 24.2, a, -0.45) for a in np.linspace(-100, 100, 9)]
        rows = [inner] + rows
        ref = np.array([0, 22.0, 0.8])
        for i in range(len(rows) - 1):
            for j in range(8):
                f = _mk([rows[i][j], rows[i][j + 1], rows[i + 1][j + 1], rows[i + 1][j]], "head", "beard", ref)
                if f is not None:
                    F.append(f)
        # 内側 (首の側) を閉じる
        last = rows[-1]
        f = _mk(list(last) + [surf(hs, 21.4, 0, -1.6)], "head", "beard", ref)
        if f is not None:
            F.append(f)
        for j in (0, 8):
            col = [rows[i][j] for i in range(len(rows))]
            for i in range(len(col) - 1):
                f = _mk([col[i], col[i + 1], np.array([0, 21.8, 0.4])], "head", "beard", ref)
                if f is not None:
                    F.append(f)
        part("beard", F)
    zf = prof_at(hs, 23.7)[1]
    if b in ("full", "long", "moustache"):
        for s in (-1, 1):
            F += part(f"mous{s}", tube("head", "beard", [V(0, 23.85, zf - 0.25), V(s * 1.3, 23.55, zf - 0.05), V(s * 2.2, 22.7, zf + 0.5)],
                                       ra=[0.42, 0.38, 0.0], sides=4, caps=(True, False), hint=(0, 1, 0)))
    if b == "goatee":
        F += part("goatee", spike("head", "beard", V(0, 21.6, prof_at(hs, 21.8)[1] + 0.5), (0, -1, -0.3), 1.6, 0.8, sides=4, rings=1))
    if b == "stubble":
        pass  # 絵だけ (頬と顎を暗く)
    return F


def build_brows(c):
    """太い眉 (年寄り・強面): 形のある眉。細い眉は絵 (face の B)。"""
    b = c.get("brow3d")
    if not b:
        return []
    hs = c["_hs"]
    F = []
    zf = prof_at(hs, EYE_Y + 1.1)[1]
    for s in (-1, 1):
        inner_y, outer_y = b.get("inner", EYE_Y + 1.05), b.get("outer", EYE_Y + 0.9)
        path = [V(s * 0.55, inner_y, zf - 0.05), V(s * 1.6, (inner_y + outer_y) / 2 + b.get("arch", 0.2), zf + 0.05), V(s * 2.8, outer_y, zf + 0.6)]
        F += part(f"brow{s}", tube("head", b.get("mat", "brow"), path, [0.3, b.get("r", 0.45), 0.05], sides=4, hint=(0, 0, 1), phase=45.0,
                                   caps=(True, True)))
    return F


def build_specs(c):
    if not c.get("specs"):
        return []
    hs = c["_hs"]
    zf = prof_at(hs, EYE_Y)[1]
    F = []
    for s in (-1, 1):
        ring_pts = []
        for k in range(8):
            a = 2 * math.pi * k / 8
            ring_pts.append(V(s * 1.55 + 0.95 * math.cos(a), EYE_Y + 0.05 + 0.8 * math.sin(a), zf - 0.55))
        ring_pts.append(ring_pts[0])
        F += part(f"spec{s}", tube("head", "metal", ring_pts, 0.14, sides=3, caps=(False, False), hint=(0, 0, 1)))
        F += part(f"arm{s}", rod("head", "metal", V(s * 2.5, EYE_Y + 0.2, zf - 0.5), V(s * 3.85, EYE_Y + 0.4, zf + 2.6), 0.12, sides=3))
    F += part("bridge", rod("head", "metal", V(-0.6, EYE_Y + 0.25, zf - 0.6), V(0.6, EYE_Y + 0.25, zf - 0.6), 0.12, sides=3))
    return F


def build_hat(c):
    h = c.get("hat")
    if not h:
        return []
    kind = h["kind"]
    hs = c["_hs"]
    m = h.get("mat", "hat")
    F = []
    if kind == "band":
        F += part("band", shell("head", m, hs, lambda a: 27.45, 28.45, 0.62, top_off=0.62, n=16, rows=1))
        F = [f for f in F if f.verts[:, 1].max() < 28.1 or True]
        # 後ろの結び目と垂れ
        F += part("band_knot", ellipsoid("hairB", m, (0.0, 27.5, 4.35), (0.9, 0.7, 0.55), segs=5, rings=1))
        for s in (-1, 1):
            F += part(f"band_tail{s}", tube("hairB", m, [V(s * 0.3, 27.3, 4.4), V(s * 0.9, 25.6, 4.7), V(s * 1.2, 24.4, 4.6)],
                                            ra=[0.42, 0.36, 0.3], rb=[0.12, 0.12, 0.12], sides=4, caps=(True, True), hint=(0, 0, 1)))
    elif kind == "kerchief":
        def y_of(a):
            t = abs((a + 180) % 360 - 180) / 180
            return 27.6 - 1.6 * t if t < 0.6 else 26.6 - 3.0 * (t - 0.6)
        F += part("kerchief", shell("head", m, hs, y_of, hs[-1][0] + 0.2, 0.6, top_off=0.95, n=16, rows=4))
        # 額の前の折り返し (布の縁の厚み)
        rim = [surf(hs, y_of(a) + 0.25, a, 0.78) for a in np.linspace(-110, 110, 11)]
        F += part("k_rim", tube("head", "hatd", rim, 0.4, sides=4, caps=(True, True), hint=(0, 1, 0)))
        F += part("k_knot", ellipsoid("hairB", m, (0.0, 25.3, 4.25), (1.1, 0.8, 0.7), segs=6, rings=2))
        for s in (-1, 1):
            F += part(f"k_tail{s}", tube("hairB", m, [V(s * 0.4, 25.0, 4.3), V(s * 1.2, 23.4, 4.6), V(s * 1.5, 22.3, 4.4)],
                                         ra=[0.6, 0.5, 0.0], rb=[0.18, 0.16, 0.0], sides=4, caps=(True, False), hint=(0, 0, 1)))
    elif kind == "cap":
        F += part("cap", shell("head", m, hs, lambda a: 27.3 if abs((a + 180) % 360 - 180) < 80 else 26.4, hs[-1][0] + 0.6, 0.55,
                               top_off=0.9, n=16, rows=3))
        zf = prof_at(hs, 27.3)[1]
        pts = [V(x, 27.25, zf - 0.2 + 0.0 * abs(x)) for x in (-3.2, -1.6, 0, 1.6, 3.2)]
        F += part("visor", loft("head", m + "2", [[V(-3.0, 27.3, zf + 0.6), V(3.0, 27.3, zf + 0.6), V(2.4, 26.9, zf - 2.0), V(-2.4, 26.9, zf - 2.0)],
                                                  [V(-3.0, 27.75, zf + 0.6), V(3.0, 27.75, zf + 0.6), V(2.4, 27.35, zf - 2.0), V(-2.4, 27.35, zf - 2.0)]],
                                caps=(m + "2", m + "2")))
    elif kind == "beret":
        R = rot_z(-12)
        F += part("beret", ellipsoid("head", m, (0.6, 29.2, 0.3), (4.6, 1.4, 4.5), segs=10, rings=3, rot=R))
        F += part("beret_band", shell("head", m + "2", hs, lambda a: 27.6, 28.5, 0.5, n=16, rows=1))
        F += part("beret_stalk", rod("head", m, V(0.9, 30.4, 0.3), V(1.0, 31.3, 0.3), 0.25, sides=4))
        if h.get("feather"):
            F += part("feather", spike("head", "feather", V(-3.4, 29.0, 1.0), (-0.5, 1.0, 0.6), 5.0, 0.7, sides=3, rings=2, bend=(-0.8, 0, 0.4),
                                       hint=(0, 0, 1)))
    elif kind in ("brim", "kasa", "wide"):
        crown_y = 27.0
        rb = {"brim": 6.6, "kasa": 7.8, "wide": 7.2}[kind]
        if kind == "kasa":
            # 円錐の笠 (縁は少し下がる)。頭に載る輪
            rings = [ringe(26.6, rb, -rb, rb, n=12), ringe(27.3, rb * 0.92, -rb * 0.92, rb * 0.92, n=12),
                     ringe(30.4, 2.2, -2.2, 2.2, n=12), ringe(31.6, 0.4, -0.4, 0.4, n=12)]
            F += part("kasa", loft("head", m, rings, caps=(m, m)))
            F += part("kasa_knob", ellipsoid("head", m + "2", (0, 31.6, 0), (0.6, 0.5, 0.6), segs=6, rings=1))
            for s in (-1, 1):
                F += part(f"kasa_cord{s}", tube("head", "cord", [V(s * 3.2, 26.8, -0.6), V(s * 3.0, 23.0, -2.6), V(s * 1.0, 21.4, -2.6)],
                                                ra=[0.18, 0.18, 0.18], sides=3, caps=(True, True)))
        else:
            F += part("crown", loft("head", m, [ringe(crown_y - 0.6, 4.25, -4.15, 4.15, n=12), ringe(crown_y + 2.6, 4.05, -3.95, 3.95, n=12),
                                                ringe(crown_y + 3.4, 3.4, -3.3, 3.3, n=12)], caps=(m, m)))
            F += part("hatband", loft("head", m + "2", [ringe(crown_y + 0.15, 4.35, -4.25, 4.25, n=12), ringe(crown_y + 1.0, 4.3, -4.2, 4.2, n=12)],
                                      caps=(False, False)))
            dip = h.get("dip", 0.5)
            F += part("brim", loft("head", m, [ringe(crown_y - 0.3, rb, -rb, rb, n=14), ringe(crown_y + 0.15, rb - 0.4, -(rb - 0.4), rb - 0.4, n=14)],
                                   caps=(m, m)))
            if h.get("feather"):
                F += part("feather", spike("head", "feather", V(3.9, crown_y + 1.2, 1.4), (0.4, 1.0, 0.5), 4.6, 0.7, sides=3, rings=2,
                                           bend=(0.5, 0, 0.4), hint=(0, 0, 1)))
    elif kind == "tall":
        F += part("tall", loft("head", m, [ringe(26.6, 4.2, -4.1, 4.0, n=12), ringe(28.6, 4.4, -4.3, 4.2, n=12), ringe(33.0, 3.6, -3.2, 3.8, n=12),
                                           ringe(34.0, 2.6, -2.2, 2.8, n=12)], caps=(m, m)))
        F += part("tall_band", loft("head", m + "2", [ringe(26.5, 4.3, -4.2, 4.1, n=12), ringe(27.6, 4.4, -4.3, 4.2, n=12)], caps=(False, False)))
        F += part("tall_mark", box("head", "trim", V(0, 30.4, -4.1), (0.9, 1.2, 0.2)))
    elif kind == "hood":
        def y_of(a):
            t = abs((a + 180) % 360 - 180) / 180
            return 27.9 - 6.4 * t ** 1.2 if t > 0.25 else 27.9 - 0.8 * t / 0.25
        F += part("hood", shell("head", m, hs, y_of, hs[-1][0] + 0.6, 0.9, top_off=1.35, n=16, rows=4,
                                lift=lambda a, t: 0.6 * (1 - t) * (abs((a + 180) % 360 - 180) / 180)))
        F += part("hood_tip", spike("hairB", m, V(0, 28.0, 4.2), (0, -0.6, 1), 3.0, 1.6, sides=5, rings=2, bend=(0, -1.2, 0.4)))
        # 顔のまわりの縁
        rim = []
        for a in np.linspace(-85, 85, 9):
            y = y_of(a)
            rim.append(surf(hs, y + 0.15, a, 1.05))
        F += part("hood_rim", tube("head", m + "2", rim, 0.45, sides=4, caps=(True, True), hint=(0, 0, 1)))
    elif kind == "helmet":
        F += part("helm", shell("head", m, hs, lambda a: 26.6 if abs((a + 180) % 360 - 180) < 70 else 25.4, hs[-1][0] + 0.4, 0.7,
                                top_off=1.0, n=16, rows=4))
        F += part("helm_rim", loft("head", m + "2", [ringe(26.2, 4.9, -4.9, 4.7, n=12), ringe(26.9, 4.7, -4.7, 4.5, n=12)], caps=(m + "2", m + "2")))
        F += part("helm_crest", box("head", m + "2", V(0, 29.3, 0.2), (0.35, 0.9, 3.6)))
    return F


# ---------------------------------------------------------------- 胴と服

def torso_spec(c):
    b = c.get("build", "avg")
    t = [list(r) for r in TORSO[b]]
    k = c.get("width", 1.0)
    for r in t:
        r[1] *= k
    return [tuple(r) for r in t]


def build_torso(c):
    ts = torso_spec(c)
    c["_ts"] = ts
    F = []
    rings = [ringe(y, rx, zf, zb, n=10) for (y, rx, zf, zb) in ts]
    F += part("torso", loft("body", "coat", rings, caps=("coat", "coat")))
    return F


def band_on(spec, bone, m, y0, y1, off, n=10, zc=0.0):
    rings = [ringe(y, *(np.array(prof_at(spec, y)) + np.array([off, -off, off])), n=n, zc=zc) for y in (y0, y1)]
    return loft(bone, m, rings, caps=(m, m))


def front_panel(spec, bone, m, y0, y1, w0, w1, off, rows=3, cols=4, ang_span=None):
    """胴の前の面に沿う板 (前掛け・胸当て・前合わせ)。w0/w1 = 下・上の半幅。閉じた厚み 0.3。"""
    pts_o, pts_i = [], []
    for r in range(rows + 1):
        t = r / rows
        y = y0 + (y1 - y0) * t
        w = w0 + (w1 - w0) * t
        rx = prof_at(spec, y)[0]
        ro, ri = [], []
        for cc in range(cols + 1):
            x = -w + 2 * w * cc / cols
            a = math.degrees(math.asin(max(-0.98, min(0.98, x / max(rx, 1e-6)))))
            ro.append(surf(spec, y, a, off))
            ri.append(surf(spec, y, a, -0.3))
        pts_o.append(ro)
        pts_i.append(ri)
    F = grid(bone, m, pts_o, np.array([0, (y0 + y1) / 2, 0]))
    # 縁 (外と内をつなぐ。内は胴の中)
    ref = np.array([0, (y0 + y1) / 2, 0])
    edges = [(0, c_, 0, c_ + 1) for c_ in range(cols)] + [(rows, c_, rows, c_ + 1) for c_ in range(cols)]
    edges += [(r, 0, r + 1, 0) for r in range(rows)] + [(r, cols, r + 1, cols) for r in range(rows)]
    for (r0, c0, r1, c1) in edges:
        f = _mk([pts_o[r0][c0], pts_o[r1][c1], pts_i[r1][c1], pts_i[r0][c0]], bone, m, ref + np.array([0, 0, 4.0]))
        if f is not None:
            F.append(f)
    return F


def build_clothes(c):
    ts = c["_ts"]
    F = []
    lw = c.get("lower", "trousers")
    # ---- 腰から下 (hem の骨: 歩くとき揺れる)
    if lw in ("skirt", "robe", "coat_long", "tails"):
        bot = {"skirt": 4.2, "robe": 1.4, "coat_long": 5.6, "tails": 7.6}[lw]
        flare = c.get("flare", {"skirt": 1.3, "robe": 1.2, "coat_long": 0.9, "tails": 0.6}[lw])
        top = prof_at(ts, 11.6)
        bw = top[0] + flare
        m = c.get("lower_mat", "coat" if lw in ("coat_long", "tails") else "skirt")
        rings = [ringe(12.0, top[0] - 0.2, top[1] + 0.2, top[2] - 0.2, n=12),
                 ringe(10.6, top[0] + 0.25, top[1] - 0.25, top[2] + 0.25, n=12),
                 ringe((10.6 + bot) / 2, (top[0] + bw) / 2 + 0.2, -(abs(top[1]) + flare * 0.6), top[2] + flare * 0.6, n=12),
                 ringe(bot, bw, -(abs(top[1]) + flare), top[2] + flare, n=12)]
        F += part("skirt", loft("hem", m, rings, caps=(m, m)))
        F += part("skirt_hem", loft("hem", c.get("hem_mat", "trim"), [ringe(bot - 0.05, bw + 0.08, -(abs(top[1]) + flare) - 0.08, top[2] + flare + 0.08, n=12),
                                                                      ringe(bot + 0.75, bw - 0.06, -(abs(top[1]) + flare) + 0.06, top[2] + flare - 0.06, n=12)],
                                      caps=(False, False)))
    # ---- 上着の重ね
    up = c.get("upper", {})
    if up.get("vest"):
        F += part("vest", band_on(ts, "body", "vest", 11.4, 20.6, 0.22))
        F += part("vest_open", front_panel(ts, "body", "shirt", 11.6, 20.9, 0.55, 1.2, 0.36, rows=2, cols=2))
    if up.get("placket"):
        F += part("placket", front_panel(ts, "body", up.get("placket_mat", "shirt"), 11.2, 21.1, 0.6, 1.25, 0.12, rows=2, cols=2))
    if up.get("lapels"):
        zf = prof_at(ts, 20.2)[1]
        for s in (-1, 1):
            F += part(f"lapel{s}", tube("body", "trim", [V(s * 1.6, 21.3, zf + 0.75), V(s * 1.6, 18.6, prof_at(ts, 18.6)[1] - 0.18),
                                                          V(s * 0.9, 15.4, prof_at(ts, 15.4)[1] - 0.18)],
                                        ra=[0.55, 0.45, 0.3], rb=[0.2, 0.2, 0.2], sides=4, caps=(True, True), hint=(0, 0, 1)))
    if up.get("apron"):
        ap = up["apron"]
        y0 = ap.get("bottom", 5.0)
        # 前掛けの下の部分 (腰から下。hem の骨)
        F += part("apron_low", front_panel([(12.0, 4.4, -2.9, 2.4), (y0, 4.8, -3.1 - c.get("flare", 0.6) * (0.6 if lw == "skirt" else 0.3), 2.4)]
                                           if lw in ("skirt", "robe") else [(12.0, 4.0, -2.7, 2.4), (y0, 4.0, -2.9, 2.4)],
                                           "hem", "apron", y0, 11.6, ap.get("w", 3.0) + 0.3, ap.get("w", 3.0), 0.35, rows=2, cols=3))
        if ap.get("bib", True):
            F += part("apron_bib", front_panel(ts, "body", "apron", 11.4, ap.get("top", 18.4), ap.get("w", 3.0), ap.get("w", 3.0) * 0.72,
                                               0.32, rows=2, cols=3))
            zf = prof_at(ts, 19.4)[1]
            for s in (-1, 1):
                F += part(f"ap_strap{s}", tube("body", "apron2", [V(s * 2.2, ap.get("top", 18.4) - 0.2, prof_at(ts, 18.4)[1] - 0.28),
                                                                  V(s * 2.5, 21.0, zf + 0.4), V(s * 1.9, 21.9, 0.4), V(s * 1.6, 21.0, 2.3)],
                                               ra=0.32, rb=0.14, sides=4, caps=(True, True), hint=(0, 0, 1)))
    if up.get("mantle"):
        mt = up["mantle"]
        bot = mt.get("bottom", 16.6)
        sp = [(bot, prof_at(ts, bot)[0] + 1.4, prof_at(ts, bot)[1] - 1.0, prof_at(ts, bot)[2] + 0.9),
              (19.2, prof_at(ts, 19.2)[0] + 1.2, prof_at(ts, 19.2)[1] - 0.8, prof_at(ts, 19.2)[2] + 0.8),
              (21.2, 4.0, -2.7, 2.6), (22.3, 2.4, -2.0, 2.0)]
        rings = [ringe(y, rx, zf, zb, n=12) for (y, rx, zf, zb) in sp]
        F += part("mantle", loft("body", "mantle", rings, caps=("mantle", False)))
        F += part("mantle_hem", loft("body", "trim", [ringe(bot - 0.1, sp[0][1] + 0.1, sp[0][2] - 0.1, sp[0][3] + 0.1, n=12),
                                                      ringe(bot + 0.6, sp[0][1] + 0.02, sp[0][2] - 0.02, sp[0][3] + 0.02, n=12)], caps=(False, False)))
        if mt.get("clasp", True):
            F += part("clasp", ellipsoid("body", "metal", (0, 21.0, -2.95), (0.55, 0.55, 0.3), segs=6, rings=1))
        if mt.get("hood_down"):
            F += part("hood_down", ellipsoid("body", "mantle", (0, 21.6, 3.2), (3.4, 1.8, 1.7), segs=8, rings=2))
    if up.get("sash"):
        F += part("sash", band_on(ts, "body", "sash", 11.0, 13.2, 0.3))
        zf = prof_at(ts, 12.0)[1]
        F += part("sash_knot", ellipsoid("body", "sash", (-1.8, 12.1, zf - 0.55), (0.95, 0.85, 0.5), segs=6, rings=2))
        F += part("sash_tail", tube("body", "sash", [V(-1.8, 11.4, zf - 0.6), V(-2.1, 9.4, zf - 0.85), V(-2.3, 8.0, zf - 0.8)],
                                    ra=[0.6, 0.55, 0.5], rb=[0.18, 0.18, 0.18], sides=4, caps=(True, True), hint=(0, 0, 1)))
    if up.get("belt", True) and not up.get("sash"):
        F += part("belt", band_on(ts, "body", "leather", 11.0, 11.9, 0.22))
        zf = prof_at(ts, 11.45)[1]
        F += part("buckle", box("body", "metal", V(0, 11.45, zf - 0.32), (0.6, 0.55, 0.15)))
    if up.get("collar"):
        cm = up.get("collar_mat", "trim")
        F += part("collar", loft("body", cm, [ringe(21.0, 3.6, -2.4, 2.3, n=10), ringe(22.2, 2.6, -2.0, 1.95, n=10), ringe(22.6, 2.3, -1.85, 1.8, n=10)],
                                 caps=(False, cm)))
    if up.get("scarf"):
        sm = up.get("scarf_mat", "scarf")
        F += part("scarf", loft("body", sm, [ringe(20.8, 3.9, -2.9, 2.7, n=10), ringe(21.9, 3.4, -2.6, 2.4, n=10), ringe(22.7, 2.5, -2.1, 2.0, n=10)],
                                caps=(sm, sm)))
        zf = prof_at(ts, 19.0)[1]
        F += part("scarf_tail", tube("body", sm, [V(1.8, 21.0, -2.7), V(2.2, 18.6, zf - 0.55), V(2.3, 15.8, zf - 0.6)],
                                     ra=[0.85, 0.8, 0.75], rb=[0.25, 0.25, 0.25], sides=4, caps=(True, True), hint=(0, 0, 1)))
    if up.get("bandolier"):
        bm = up.get("bandolier_mat", "leather")
        p = [surf(ts, 20.6, -40, 0.2), surf(ts, 16.4, 0, 0.32), surf(ts, 12.2, 40, 0.3), surf(ts, 14.0, 140, 0.3), surf(ts, 20.4, 210, 0.25)]
        F += part("bandolier", tube("body", bm, p, ra=0.42, rb=0.16, sides=4, caps=(True, True), hint=(0, 0, 1)))
    for i, (x, y, kind) in enumerate(up.get("pouches", [])):
        zf = prof_at(ts, y)[1]
        a = math.degrees(math.asin(max(-0.95, min(0.95, x / prof_at(ts, y)[0]))))
        p = surf(ts, y, a, 0.6)
        if kind == "pouch":
            F += part(f"pouch{i}", box("body", "leather", p, (0.85, 1.0, 0.55), taper=0.85))
            F += part(f"pouch_lid{i}", box("body", "leather2", p + V(0, 0.75, -0.08), (0.92, 0.3, 0.62)))
        elif kind == "vial":
            F += part(f"vial{i}", ellipsoid("body", "glass", p + V(0, -0.2, -0.1), (0.5, 0.85, 0.5), segs=6, rings=2))
            F += part(f"cork{i}", disc("body", "wood", p + V(0, 0.75, -0.1), 0.3, 0.25, n=5))
        elif kind == "keys":
            F += part(f"keyring{i}", tube("body", "metal", [p + V(0.8 * math.cos(t), -0.9 + 0.8 * math.sin(t), -0.2) for t in np.linspace(0, 2 * math.pi, 7)],
                                          0.13, sides=3, caps=(False, False)))
            for j, dx in enumerate((-0.5, 0.1, 0.6)):
                F += part(f"key{i}{j}", box("body", "metal", p + V(dx, -2.2, -0.25), (0.14, 0.9, 0.1)))
        elif kind == "book":
            F += part(f"pbook{i}", box("body", "book", p + V(0, 0, 0.1), (1.0, 1.25, 0.4)))
    for i, y in enumerate(up.get("buttons", [])):
        zf = prof_at(ts, y)[1]
        F += part(f"btn{i}", ellipsoid("body", "metal", (0, y, zf - 0.32 - (0.12 if up.get("placket") else 0)), (0.32, 0.32, 0.18), segs=5, rings=1))
    if up.get("pauldron"):
        F += part("pauldron", ellipsoid("armL", "metal", (SHOULDER[c.get("build", "avg")] + 0.3, 20.6, 0.0), (2.5, 1.7, 2.6), segs=8, rings=2))
        F += part("pauldron_rim", ellipsoid("armL", "metal2", (SHOULDER[c.get("build", "avg")] + 0.5, 19.4, 0.0), (2.3, 0.6, 2.4), segs=8, rings=1))
    if up.get("rope_coil"):
        zf = prof_at(ts, 17.0)[1]
        for k, dz in enumerate((0.0, 0.55)):
            p = [surf(ts, 21.0, 38, 0.6 + dz), surf(ts, 17.2, 0, 0.85 + dz), surf(ts, 12.8, -38, 0.7 + dz), surf(ts, 13.0, -150, 0.7 + dz),
                 surf(ts, 20.4, 150, 0.7 + dz)]
            F += part(f"coil{k}", tube("body", "rope", p, ra=0.55, sides=5, caps=(True, True)))
    return F


# ---------------------------------------------------------------- 腕と脚

def default_arm(c, side):
    sw = SHOULDER[c.get("build", "avg")] * c.get("width", 1.0)
    sh = V(side * sw, 20.2, 0.1)
    el = V(side * (sw + 0.45), 15.9, -0.2)
    wr = V(side * (sw + 0.25), 12.2, -1.8)
    return sh, el, wr


def arm_pose(c, side):
    """腕の (肩, 肘, 手首)。c["arms"] = {"r": (肘, 手首), "l": ...} で上書き (肩は胴で決まる)。"""
    sh, el, wr = default_arm(c, side)
    key = "l" if side > 0 else "r"
    p = c.get("arms", {}).get(key)
    if p:
        el, wr = V(*p[0]), V(*p[1])
    return sh, el, wr


def build_arm(c, side):
    nm = "L" if side > 0 else "R"
    up_b, fo_b = "arm" + nm, "fore" + nm
    sh, el, wr = arm_pose(c, side)
    sl = c.get("sleeve", "fitted")
    F = []
    r_up = {"fitted": 1.45, "wide": 1.75, "rolled": 1.45, "puff": 1.8}[sl]
    r_el = {"fitted": 1.3, "wide": 1.65, "rolled": 1.25, "puff": 1.3}[sl]
    r_wr = {"fitted": 1.12, "wide": 1.95, "rolled": 0.95, "puff": 1.1}[sl]
    sm = c.get("sleeve_mat", "coat")
    # 上腕 (肩は胴の中へ 0.8 沈める)
    du = (el - sh) / np.linalg.norm(el - sh)
    sh_in = sh - du * 1.2   # 肩の付け根は上腕の向きのまま胴の中へ (曲げない: 折れ目で面が裏返らない)
    F += part(up_b + "u", tube(up_b, sm, [sh_in, el], ra=[r_up, r_el], sides=7, caps=(True, True)))
    F += part(up_b + "s", ellipsoid(up_b, sm, sh + du * 0.4, (r_up * 1.08, r_up * 1.1, r_up * 1.08), segs=7, rings=3))
    # 肘から先 (肘の球で上腕とつなぐ。前腕の付け根は上腕の中)
    d = wr - el
    el_in = el - d / np.linalg.norm(d) * 1.0   # 前腕の付け根は前腕の向きのまま上腕の中へ
    # 肘の玉 (前腕の骨。肘を曲げても上腕との間に隙間が開かない)
    F += part(fo_b + "e", ellipsoid(fo_b, sm, el, (r_el * 1.02, r_el * 1.02, r_el * 1.02), segs=7, rings=3))
    if sl == "rolled":
        F += part(fo_b + "roll", tube(fo_b, sm, [el_in, el + (wr - el) * 0.22], ra=[r_el + 0.12, r_el + 0.12], sides=7, caps=(True, True)))
        F += part(fo_b + "f", tube(fo_b, "skin", [el, wr], ra=[r_el * 0.85, r_wr], sides=6, caps=(True, True)))
    else:
        F += part(fo_b + "f", tube(fo_b, sm, [el_in, wr + (wr - el) * 0.06], ra=[r_el, r_wr], sides=7, caps=(True, True)))
        cm = c.get("cuff_mat", "trim")
        if cm:
            c0 = wr + (el - wr) * 0.16
            F += part(fo_b + "cuff", tube(fo_b, cm, [c0, wr + (wr - el) * 0.08], ra=[r_wr + 0.14, r_wr + 0.16], sides=7, caps=(True, True)))
    if c.get("gloves"):
        hm = "glove"
    else:
        hm = "skin"
    # 手 (手首の先。指をまとめた楕円体と親指)
    dirv = (wr - el) / np.linalg.norm(wr - el)
    hc = wr + dirv * 1.15
    yaw = math.degrees(math.atan2(dirv[0], -dirv[2])) if abs(dirv[1]) < 0.9 else 0.0
    pitch = math.degrees(math.asin(np.clip(-dirv[1], -1, 1)))
    R = rot_y(-yaw) @ rot_x(90 - pitch) if c.get("hand_rot", True) else None
    F += part(fo_b + "h", ellipsoid(fo_b, hm, hc, (0.95, 1.25, 0.75), segs=6, rings=2, rot=R))
    F += part(fo_b + "t", ellipsoid(fo_b, hm, hc + V(-side * 0.75, 0.25, -0.35) + dirv * -0.2, (0.38, 0.6, 0.38), segs=4, rings=1))
    return F


def build_legs(c):
    F = []
    lw = c.get("lower", "trousers")
    for side in (-1, 1):
        b = "legL" if side > 0 else "legR"
        x = side * 1.9 * c.get("width", 1.0)
        top = 12.4
        if lw == "robe":
            pass  # 衣の下: 靴だけ (脛は衣の中で見えないので作らない)
        else:
            F += part(b + "t", tube(b, "trousers", [V(x * 0.9, top + 0.8, 0.1), V(x, 7.0, 0.05), V(x * 1.02, 3.6, 0.0)],
                                    ra=[1.5, 1.5, 1.3], sides=7, caps=(True, True)))
        bt = c.get("boots", "boot") if lw != "robe" else "shoe"
        if bt == "boot":
            F += part(b + "b", tube(b, "boot", [V(x * 1.02, 0.9, 0.15), V(x * 1.02, 4.9, 0.05)], ra=[1.5, 1.48], sides=7, caps=(True, True)))
            F += part(b + "bc", tube(b, "boot2", [V(x * 1.02, 4.4, 0.05), V(x * 1.02, 5.3, 0.05)], ra=[1.62, 1.66], sides=7, caps=(True, True)))
        else:
            F += part(b + "b", tube(b, "boot", [V(x * 1.02, 0.9, 0.15), V(x * 1.02, 2.6, 0.05)], ra=[1.4, 1.35], sides=7, caps=(True, True)))
        # 足 (つま先は前へ)
        F += part(b + "f", loft(b, "boot", [[V(x - 1.25, 0.0, 1.5), V(x + 1.25, 0.0, 1.5), V(x + 1.3, 0.0, -2.9), V(x - 1.3, 0.0, -2.9)],
                                             [V(x - 1.25, 2.0, 1.3), V(x + 1.25, 2.0, 1.3), V(x + 1.05, 1.5, -2.6), V(x - 1.05, 1.5, -2.6)]],
                                    caps=("sole", "boot")))
    return F


# ====================================================================== 顔の絵

FACE_KEYS = "DdIiWHBbMmnwfSckEeLlO"


def face_pixels(c):
    """顔のドットの並び (語り部の face_paint と同じ書式: [(行の列, ax0, ty0)]。行は上から)。"""
    f = c.get("face", {})
    ey = int(math.floor(EYE_Y * 2.0))       # 目の中心の行
    pats = []
    eye = f.get("eye", "calm")
    ex = f.get("eye_x", 1)                  # 内側の画素 (x = ex/2 単位から)
    E = {
        "calm":   [".DDD", "WHIW", "WIIk"],
        "round":  [".DD.", "DHID", "WIIW", ".kk."],
        "narrow": ["DDDD", "WiIW"],
        "sleepy": ["kkkk", "DDDD", "WiiW"],
        "kind":   ["....", ".DDD", "WHIW"],
        "sharp":  ["DD..", "WDDD", "WHIW"],
        "lady":   ["...D", "DDDD", "WHIW", "WIIk"],
        "child":  [".DD.", "DHID", "DIID", ".kk."],
        "old":    ["kkk.", "DDDk", "WiIW", ".ww."],
        "closed": ["....", ".kkk", "D..D"],
        "patch":  ["EEEE", "EEEE", "EEEE"],
        "glint":  [".DDD", "WHIW", "WIIW"],
    }
    rows = E[eye]
    top = ey + (len(rows) // 2)
    pats.append((rows, ex, top))
    if f.get("patch_r"):
        pass
    brow = f.get("brow", "soft")
    BR = {"soft": (["BBB."], 1), "flat": (["BBBB"], 1), "stern": (["...B", "BBB."], 2), "worried": (["B...", ".BBB"], 2),
          "arched": ([".BB.", "B..B"], 2), "thick": (["BBBB", "BBB."], 2), "none": ([], 1), "high": (["BBB."], 2)}
    rows_b, gap = BR[brow]
    if rows_b:
        pats.append((rows_b, ex, top + gap + len(rows_b) - (0 if brow != "high" else -1)))
    mouth = f.get("mouth", "neutral")
    my = int(math.floor(23.15 * 2.0))
    MO = {"neutral": (["MM"], 0), "smile": (["..M", "MM."], 0), "grin": (["mMMm", ".mm."], 0), "frown": (["MM.", "..M"], 0),
          "lips": (["Mm"], 0), "small": (["M"], 0), "smirk": (["MMM", "..."], 0), "o": (["mM", "MM"], 0), "none": ([], 0),
          "wide": (["MMM"], 0)}
    rows_m, mx = MO[mouth]
    if rows_m:
        pats.append((rows_m, mx, my + len(rows_m) // 2))
    pats.append((["n"], 0, int(math.floor(24.35 * 2))))   # 鼻の下の影
    for extra in f.get("marks", []):
        pats.append(extra)
    return pats


def face_paint(c, ctx, col, glow):
    P, N, bay, owned = ctx["P"], ctx["N"], ctx["bay"], ctx["owned"]
    f = c.get("face", {})
    ax = np.abs(P[..., 0])
    skin = owned & (ctx["bones"] == "head") & (ctx["mats"] == "skin")
    sel = skin & (N[..., 2] < -0.30)
    tx = np.floor(ax * 2.0 + 1e-6).astype(int)
    ty = np.floor(P[..., 1] * 2.0 + 1e-6).astype(int)
    pal = c["_pal_face"]
    # 頬の赤み
    if f.get("blush", 0.0) > 0:
        r = np.sqrt(((ax - 2.4) / 0.95) ** 2 + ((P[..., 1] - 24.2) / 0.55) ** 2)
        m = sel & (r < 1.0)
        tint = np.array(f.get("blush_col", (226, 116, 108)), float)
        amt = f["blush"]
        col = np.where(m[..., None], col * (1 - amt) + tint * amt, col)
    if f.get("drunk"):
        nose = skin & (P[..., 2] < -3.9) & (ax < 0.7) & (np.abs(P[..., 1] - 24.6) < 0.9)
        col = np.where(nose[..., None], col * 0.45 + np.array([206, 74, 66]) * 0.55, col)
    if f.get("stubble"):
        st = skin & (P[..., 1] < 23.9) & (N[..., 2] < 0.2) & (P[..., 1] > 20.9)
        dark = np.array(c.get("hair_col", (60, 50, 44)), float)
        on = st & (hash1(np.floor(P[..., 0] * 4) * 7 + np.floor(P[..., 1] * 4) * 13, 9) < 0.55)
        col = np.where(on[..., None], col * 0.55 + dark * 0.45, col)
    if f.get("mask") is not None:
        m = np.array(f["mask"], float)
        cover = skin & (P[..., 1] < 24.55) & (P[..., 2] < 1.0)
        shade = np.clip(0.75 + 0.35 * (-N[..., 2]) + 0.1 * N[..., 1], 0.5, 1.15)
        cc = np.clip(m[None, None, :] * shade[..., None], 0, 255) * (1 + 0.08 * bay[..., None])
        col = np.where(cover[..., None], cc, col)
        topm = cover & (P[..., 1] > 24.05)
        col = np.where(topm[..., None], col * 0.72, col)
    # 髪の生え際の影 (前髪の下の額を一段暗く) と、顎の下の首の影
    hl = c.get("hairline")
    if hl:
        sh = skin & (P[..., 1] > hl - 0.7) & (N[..., 2] < -0.2)
        col = np.where(sh[..., None], col * 0.86, col)
    neck = owned & (ctx["bones"] == "head") & (ctx["mats"] == "skin") & (P[..., 1] < 21.4)
    col = np.where(neck[..., None], col * 0.8, col)
    # 髪の毛筋 (頭頂から放射に下りる暗い筋) と、頭の上の照り (途切れた明るい帯)
    hm = owned & np.isin(ctx["mats"], ["hair", "beard"]) & np.isin(ctx["bones"], ["head", "hairB"])
    if hm.any():
        ang = np.degrees(np.arctan2(P[..., 0], -(P[..., 2] + 0.2)))
        wav = 0.35 * np.sin(P[..., 1] * 1.4 + ang * 0.05)
        sfr = np.mod(ang / 11.0 + wav, 1.0)
        strand = hm & (sfr < 0.2)
        col = np.where(strand[..., None], col * 0.78, col)
        top = max((y for (y, _, _, _) in c["_hs"]), default=29.7)
        band = hm & (ctx["bones"] == "head") & (P[..., 1] > top - 2.9) & (P[..., 1] < top - 1.7) & (N[..., 1] > -0.2) & \
            (np.mod(ang / 11.0 + 0.5, 1.0) > 0.25) & (hash1(np.floor(ang / 11.0) * 3 + 17, 4) > 0.25)
        col = np.where(band[..., None], np.clip(col * 1.22 + 14, 0, 255), col)

    def put(pat, ax0, ty0, side=0):
        nonlocal col
        sd = sel if side == 0 else sel & (np.sign(P[..., 0]) == side)
        for r_, row in enumerate(pat):
            for k, ch in enumerate(row):
                if ch == ".":
                    continue
                m = sd & (tx == ax0 + k) & (ty == ty0 - r_)
                if f.get("mask") is not None and ty0 - r_ < 49:
                    continue
                cc = pal.get(ch)
                if cc is None:
                    continue
                if ch in "nwk":
                    col = np.where(m[..., None], col * cc, col)
                else:
                    col = np.where(m[..., None], np.array(cc, float), col)
    for p_ in face_pixels(c):
        put(*p_)
    return col, glow


def face_palette(c):
    f = c.get("face", {})
    iris = np.array(f.get("iris", (92, 64, 44)), float)
    hair = np.array(c.get("hair_col", (70, 52, 40)), float)
    brow = np.array(f.get("brow_col", hair * 0.6), float)
    return {
        "D": (28, 18, 20), "d": (70, 46, 44), "I": tuple(iris), "i": tuple(iris * 0.55), "W": (236, 232, 222), "H": (255, 255, 250),
        "B": tuple(brow), "b": tuple(brow * 1.3), "M": (96, 40, 38), "m": f.get("lip", (176, 84, 80)), "n": 0.82, "w": 0.8, "k": 0.78,
        "f": (176, 110, 84), "S": (206, 132, 120), "c": (226, 128, 118), "E": (24, 20, 22), "e": (60, 50, 48),
        "L": (40, 26, 26), "l": (120, 60, 58), "O": (60, 30, 28),
    }


# ====================================================================== 人を組む

def build_body(c):
    F = []
    F += build_head(c)
    F += build_hair(c)
    F += build_beard(c)
    F += build_brows(c)
    F += build_specs(c)
    F += build_hat(c)
    F += build_torso(c)
    F += build_clothes(c)
    F += build_arm(c, 1)
    F += build_arm(c, -1)
    F += build_legs(c)
    for fn in c.get("extra_body", []):
        F += fn(c)
    return F


def bones_for(c):
    sw = SHOULDER[c.get("build", "avg")] * c.get("width", 1.0)
    shL, elL, wrL = arm_pose(c, 1)
    shR, elR, wrR = arm_pose(c, -1)
    sockets = c.get("sockets", {})

    def hand(el, wr):
        d = (wr - el) / np.linalg.norm(wr - el)
        return tuple(np.round(wr + d * 1.15, 4))
    B = {
        "root": (None, (0, 0, 0)),
        "body": ("root", (0, 12, 0)),
        "head": ("body", (0, 21.6, 0)),
        "hairB": ("head", (0, 27.0, 3.2)),
        "armL": ("body", tuple(np.round(shL, 4))), "armR": ("body", tuple(np.round(shR, 4))),
        "foreL": ("armL", tuple(np.round(elL, 4))), "foreR": ("armR", tuple(np.round(elR, 4))),
        "legL": ("body", (1.9 * c.get("width", 1.0), 11.8, 0)), "legR": ("body", (-1.9 * c.get("width", 1.0), 11.8, 0)),
        "hem": ("body", (0, 11.4, 0)),
        "prop_r": ("foreR", sockets.get("prop_r", hand(elR, wrR))),
        "prop_l": ("foreL", sockets.get("prop_l", hand(elL, wrL))),
        "prop_back": ("body", sockets.get("prop_back", (0, 17.0, 3.2))),
    }
    for k, v in c.get("socket_parent", {}).items():
        B[k] = (v, B[k][1])
    return B


def palette(c):
    p = c["pal"]
    mats = {
        "skin": dict(ramp=skin_ramp(p["skin"]), base=0.86, lit=0.10, noise=0.0, facevar=0.0, crack=0, min=1, streak=0.0, ao=0.03),
        "hair": dict(ramp=ramp(p["hair"], 5, 0.4, 1.35), base=0.58, lit=0.34, noise=0.06, facevar=0.05, crack=0, min=0, streak=0.0, ao=0.02),
        "beard": dict(ramp=ramp(p.get("beard", p["hair"]), 5, 0.42, 1.3), base=0.6, lit=0.3, noise=0.08, facevar=0.05, crack=0, min=0,
                      streak=0.0, stripe=2, axis="v", ao=0.0),
        "brow": dict(ramp=ramp(p.get("beard", p["hair"]), 4, 0.5, 1.2), base=0.6, lit=0.25, noise=0.0, crack=0, min=0, ao=0.0),
        "hairtie": cloth(p.get("tie", (150, 50, 46))),
        "coat": cloth(p["coat"], stripe=p.get("coat_stripe", 0), axis="v"),
        "skirt": cloth(p.get("skirt", p["coat"]), streak=0.14, stripe=p.get("skirt_stripe", 0), axis="u"),
        "shirt": cloth(p.get("shirt", (196, 186, 160)), streak=0.06),
        "vest": cloth(p.get("vest", p["coat"]), stripe=p.get("vest_stripe", 0), axis="u"),
        "apron": cloth(p.get("apron", (190, 176, 140)), streak=0.12, noise=0.12),
        "apron2": cloth(tuple(np.array(p.get("apron", (190, 176, 140))) * 0.72)),
        "mantle": cloth(p.get("mantle", p["coat"]), stripe=p.get("mantle_stripe", 0), axis="v"),
        "sash": cloth(p.get("sash", (130, 54, 44)), stripe=2, axis="u"),
        "scarf": cloth(p.get("scarf", (150, 60, 50)), stripe=p.get("scarf_stripe", 3), axis="u"),
        "trim": cloth(p.get("trim", (60, 46, 34)), stripe=2, axis="u", streak=0.0),
        "trousers": cloth(p.get("trousers", (52, 48, 56)), streak=0.08),
        "boot": cloth(p.get("boot", (58, 42, 32)), base=0.5, lit=0.34, streak=0.04),
        "boot2": cloth(tuple(np.array(p.get("boot", (58, 42, 32))) * 1.25), base=0.52, lit=0.3, stripe=2, axis="u"),
        "sole": cloth((30, 24, 22), base=0.5),
        "leather": cloth(p.get("leather", (104, 70, 44)), base=0.52, lit=0.32, streak=0.06),
        "leather2": cloth(tuple(np.array(p.get("leather", (104, 70, 44))) * 0.8), base=0.52, lit=0.32),
        "metal": dict(ramp=ramp(p.get("metal", (190, 160, 80)), 5, 0.4, 1.45), base=0.6, lit=0.42, noise=0.02, facevar=0.06, crack=0, min=0, ao=0.0),
        "metal2": dict(ramp=ramp(p.get("metal2", (120, 124, 132)), 5, 0.4, 1.4), base=0.58, lit=0.4, noise=0.04, facevar=0.06, crack=0, min=0),
        "hat": cloth(p.get("hat", p["coat"])),
        "hat2": cloth(p.get("hat2", p.get("trim", (60, 46, 34))), stripe=2, axis="u"),
        "hatd": cloth(tuple(int(x * 0.82) for x in p.get("hat", p["coat"])), stripe=2, axis="u"),
        "glove": cloth(p.get("glove", (96, 70, 48)), base=0.52, lit=0.3),
        "feather": cloth(p.get("feather", (220, 214, 200)), stripe=2, axis="v"),
        "cord": cloth(p.get("cord", (140, 110, 70)), stripe=2, axis="u"),
        "rope": cloth((150, 124, 84), stripe=2, axis="u", streak=0.0),
        "glass": dict(ramp=ramp(p.get("glass", (120, 176, 150)), 5, 0.4, 1.4), base=0.6, lit=0.45, noise=0.0, crack=0, min=0),
        "wood": cloth((112, 80, 52), base=0.52, lit=0.3, stripe=3, axis="v", streak=0.1),
        "book": cloth(p.get("book", (120, 44, 40)), base=0.54),
    }
    for k, v in p.get("extra_mats", {}).items():
        mats[k] = v
    return {"mats": mats, "glow_mats": p.get("glow_mats", [])}
