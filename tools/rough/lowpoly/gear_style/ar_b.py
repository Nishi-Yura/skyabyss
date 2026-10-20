"""様式 B「祭具の優美」の防具 (岩鯨の一式): 布が輪郭を作り、板は要所だけ。縦に長く、金の縁と浮く環で品を出す。
兜 = 顔を出した頭巾と、背に浮く尾びれの環、胸 = 細い胴衣と金の帯、肩 = 小さな淡い石の肩当てと浮く環、背の長い外套、
脚 = 前垂れと細いすね当て、靴 = 足首に小さな尾びれ。"""
from __future__ import annotations

import math
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE.parent / "armor"))
from akit import *  # noqa: F401,F403,E402
from akit import V, bx, ext, spk, crv, rr, shell, hide_front, rot_x, rot_y, rot_z, ARM, LEG, BOOT  # noqa: E402


def gem(b, c, size=(1.4, 0.8, 0.5)):
    return bx(b, "gem", c, size, taper=(0.8, 0.8), skip=("b",))


def ring3(bone, mat, cx, cy, cz, R, a0=0, a1=360, n=12, r=0.45, plane="xy"):
    pts = []
    for i in range(n + 1):
        a = math.radians(a0 + (a1 - a0) * i / n)
        u, v = R * math.sin(a), R * math.cos(a)
        pts.append((cx + u, cy + v, cz) if plane == "xy" else (cx + u, cy, cz + v))
    return crv(bone, mat, pts, r, sides=4, hint=(0, 0, 1) if plane == "xy" else (0, 1, 0), phase=45)


def fluke(bone, mat, c, sign, size=3.0, rot=None, rim="trim"):
    pts = [(0, 0), (size * 0.5, -0.2 * size), (size, 0.25 * size), (size * 0.95, 0.8 * size), (size * 0.4, 0.55 * size)]
    pts = [(sign * x, y) for x, y in pts]
    if sign < 0:
        pts = pts[::-1]
    return ext(bone, mat, pts, 0.7, c=c, rot=rot)


def strips(bone, mat, xl, xr, ytop, ybot, z, k, hem, thick=0.5, flare=0.4, rot=None):
    f = []
    for i in range(k):
        x0 = xl + (xr - xl) * i / k
        x1 = xl + (xr - xl) * (i + 1) / k
        mid = (xl + xr) / 2
        half = max(1e-6, (xr - xl) / 2)
        fl0 = flare * (x0 - mid) / half
        fl1 = flare * (x1 - mid) / half
        h = hem[i % len(hem)]
        if h is None:
            c = (x0 + x1) / 2 + (fl0 + fl1) / 2
            pts = [(c, ybot - 2.4), (x1 + fl1, ybot + 0.6), (x1, ytop), (x0, ytop), (x0 + fl0, ybot + 0.6)]
        else:
            pts = [(x0 + fl0, ybot + h), (x1 + fl1, ybot + h), (x1, ytop), (x0, ytop)]
        f += ext(bone, mat, pts, thick, c=(0, 0, z), rot=rot)
    return f


def helmet(tier=1):
    b = "armorHead"
    f = []
    rings = [rr(25.0, 4.9, -4.9, 5.3, cut=1.4), rr(29.6, 4.95, -4.9, 5.3, cut=1.3), rr(32.6, 4.1, -4.3, 5.1, cut=1.4),
             rr(35.2, 1.6, -1.0, 5.6, cut=0.6), rr(36.4, 0.4, 1.2, 6.4, cut=0.2)]
    f += hide_front(shell(b, "cloth", rings), 29.8)
    f += bx(b, "trim", (0, 30.2, -5.0), (9.0, 0.8, 0.8), skip=("b", "+z"))      # 金の額帯
    f += gem(b, (0, 30.2, -5.5), (1.2, 1.4, 0.5))
    for s in (-1, 1):
        f += fluke(b, "guard", (s * 5.0, 29.0, -0.4), s, 3.6, rot=rot_y(90 * s) @ rot_z(0))
    f += ring3(b, "trim", 0, 28.0, 6.2, 7.6, -140, 140, n=14, r=0.5)     # 背に浮く環
    f += ring3(b, "trim", 0, 28.0, 6.2, 5.6, -120, 120, n=10, r=0.35)
    for a in (-140, 140):
        x = 7.6 * math.sin(math.radians(a))
        y = 28.0 + 7.6 * math.cos(math.radians(a))
        f += bx(b, "acc", (x, y, 6.2), (1.2, 1.2, 1.2), taper=(0.8, 0.8))
    f += strips(b, "cloth", -4.6, 4.6, 29.6, 22.0, 5.6, 5, [0.0, -1.0, 0.0, -2.0, 0.0], thick=0.6, flare=0.8)   # 後ろに垂れる頭巾の布
    if tier >= 2:
        f += ring3(b, "trim", 0, 28.0, 6.2, 9.6, -150, 150, n=16, r=0.4)
    return {b: f}


def chest(tier=1):
    B = "armorBody"
    f = []
    rings = [rr(12.2, 4.7, -2.8, 2.7, cut=0.9), rr(17.0, 4.5, -2.8, 2.7, cut=0.9), rr(22.8, 4.8, -3.0, 2.8, cut=1.0), rr(24.2, 4.2, -2.8, 2.6, cut=1.0)]
    f += shell(B, "cloth", rings)
    # 前: 金の帯の斜め掛けと、胸の紋
    f += bx(B, "pale" if False else "guard", (0, 20.4, -3.3), (6.4, 3.6, 0.9), taper=(0.9, 0.9), skip=("b", "+z"))
    f += bx(B, "trim", (0, 20.4, -3.85), (7.0, 0.6, 0.4), skip=("b", "+z"))
    f += gem(B, (0, 20.6, -4.2), (1.5, 1.7, 0.5))
    f += bx(B, "trim", (0, 15.0, -3.1), (9.4, 1.0, 0.9), skip=("b", "+z"))       # 帯
    f += bx(B, "acc", (0, 15.0, -3.7), (2.0, 1.4, 0.6), skip=("b", "+z"))
    f += bx(B, "trim", (0, 23.7, 0), (8.6, 0.7, 5.6), taper=(0.92, 0.92))         # 襟の金縁
    # 背の外套: 肩から長く垂れ、裾は尾びれ状の切れ込み
    f += strips(B, "cloth", -4.6, 4.6, 23.6, 6.0, 3.2, 6, [0.0, -1.6, 0.0, 0.0, -1.6, 0.0], thick=0.6, flare=1.2, rot=rot_x(5))
    f += bx(B, "trim", (0, 23.2, 3.4), (9.2, 0.8, 0.8))
    out = {B: f}
    for s in (-1, 1):
        ab = ARM[s]
        g = []
        g += bx(ab, "cloth", (s * 6.4, 18.0, 0), (4.8, 11.0, 4.8), taper=(0.97, 0.97), skip=("u",))
        g += bx(ab, "trim", (s * 6.4, 13.2, 0), (5.1, 0.8, 5.1), skip=("b", "u"))
        # 広がる袖の先
        g += strips(ab, "cloth", s * 4.2 - 2.4 if s > 0 else s * 8.6, s * 8.6 if s > 0 else s * 4.2 + 2.4, 13.2, 8.4, 0.0, 3, [-0.6, 0.0, -1.2], thick=4.6, flare=0.0) if False else []
        # 小さな淡い石の肩当てと、浮く環
        g += bx(ab, "guard", (s * 7.4, 24.4, 0), (5.8, 1.2, 5.6), rot=rot_z(-s * 14), taper=(0.8, 0.85))
        g += bx(ab, "trim", (s * 7.4, 25.1, 0), (5.2, 0.5, 5.0), rot=rot_z(-s * 14), taper=(0.8, 0.85))
        g += ring3(ab, "trim", s * 9.4, 24.8, 0.0, 2.8, 0, 360, n=10, r=0.35)
        g += bx(ab, "acc", (s * 9.4, 27.6, 0), (1.0, 1.0, 1.0), taper=(0.8, 0.8))
        out[ab] = g
    return out


def legs(tier=1):
    out = {}
    for s in (-1, 1):
        lb = LEG[s]
        cx = s * 2.15
        g = []
        g += bx(lb, "cloth", (cx, 6.4, 0), (4.3, 11.2, 4.6), taper=(0.97, 0.97), skip=("b", "u"))
        g += bx(lb, "trim", (cx, 11.7, 0), (4.6, 0.8, 4.9), skip=("b", "u"))
        # 前垂れ: 腰から長く下がる 2 枚。裾は金の縁
        g += ext(lb, "acc", [(cx - 2.0, 3.6), (cx + 2.0, 3.6), (cx + 2.2, 11.4), (cx - 2.2, 11.4)], 0.5, c=(0, 0, -2.9), rot=None)
        g += bx(lb, "trim", (cx, 3.7, -3.0), (4.4, 0.6, 0.6), skip=("b",))
        g += gem(lb, (cx, 8.0, -3.3), (1.0, 1.4, 0.4)) if s == -1 else []
        g += bx(lb, "trim", (cx, 6.0, -3.2), (2.8, 0.5, 0.4)) if s == 1 else []
        out[lb] = g
    return out


def boots(tier=1):
    out = {}
    for s in (-1, 1):
        bb = BOOT[s]
        cx = s * 2.15
        g = []
        g += bx(bb, "cloth", (cx, 2.2, 0), (4.4, 4.4, 4.7), taper=(0.95, 0.95), skip=("b",))
        g += bx(bb, "guard", (cx, 1.0, -3.0), (4.0, 2.0, 2.0), taper=(0.6, 0.6), skip=("b", "+z"))
        g += bx(bb, "trim", (cx, 4.2, 0), (4.7, 0.7, 5.0), skip=("b", "u"))
        g += bx(bb, "guard", (cx, 3.0, -2.7), (3.2, 2.4, 0.7), skip=("b", "+z"))
        g += fluke(bb, "guard", (cx, 3.6, 2.6), s, 2.6, rot=rot_x(-60))
        g += bx(bb, "trim", (cx, 0.5, 0), (4.8, 0.8, 5.2), skip=("u",))
        out[bb] = g
    return out


def build(tier=1):
    pieces = {"helmet": helmet(tier), "chestplate": chest(tier), "leggings": legs(tier), "boots": boots(tier)}
    return {k: [f for bone_faces in v.values() for f in bone_faces] for k, v in pieces.items()}
