"""様式 C「遺骸」の防具 (岩鯨の一式): 鯨の骨そのものを着る。左右非対称 (左の大きな肩・右の長い角)、
ぼろ布と包帯、苔、ひびの奥の水色。兜 = 顎骨と頭骨の兜、胸 = 肋の外骨格と背の椎骨の列、脚 = 包帯と骨の膝、靴 = 爪先の骨爪。"""
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


def rags(bone, mat, xs, ytop, ybot, z, hems, w=1.6, thick=0.5, rot=None, tilt=0.0):
    f = []
    for i, x in enumerate(xs):
        L = ytop - ybot + hems[i % len(hems)]
        pts = [(x - w / 2, ytop), (x + w / 2, ytop), (x + w * 0.5 + tilt * 0.4, ytop - L * 0.6), (x + tilt, ytop - L), (x - w * 0.5 + tilt * 0.3, ytop - L * 0.7)]
        f += ext(bone, mat, pts, thick, c=(0, 0, z), rot=rot)
    return f


def helmet(tier=1):
    b = "armorHead"
    f = []
    # 頭骨のドーム (額は低く、後ろへ長く)
    rings = [rr(28.4, 4.9, -4.9, 5.2, cut=1.4), rr(31.2, 4.7, -4.8, 5.2, cut=1.6), rr(33.4, 3.2, -3.4, 4.6, cut=1.5)]
    f += shell(b, "main", rings, caps=(False, "main"))
    # 顎骨: 頬から前へ回り、歯が並ぶ
    for s in (-1, 1):
        f += bx(b, "main", (s * 5.2, 26.6, -0.6), (1.4, 4.8, 7.0), rot=rot_z(-s * 5), taper=(0.8, 0.9))
        f += spk(b, "edge", V(s * 5.0, 25.0, -3.8), V(0, -1, -0.15), 2.4, 0.55, sides=4, rings=1)
        f += spk(b, "edge", V(s * 5.0, 25.0, -1.4), V(0, -1, -0.1), 2.0, 0.55, sides=4, rings=1)
    f += bx(b, "wrap", (0, 28.6, -5.1), (9.4, 1.8, 0.8), skip=("b", "+z"))     # 包帯の額帯
    f += gem(b, (0, 30.6, -5.2), (1.2, 1.6, 0.5))
    f += bx(b, "dark", (-2.0, 26.2, -5.0), (3.0, 0.8, 0.5), skip=("b", "+z"))
    # 非対称の角: 右 (s=-1) は長く反り、左は折れて短い
    f += spk(b, "edge", V(-3.4, 32.8, -0.4), V(-0.7, 1, 0.1), 6.4, 1.4, sides=4, rings=4, bend=(-1.6, 0, 1.2))
    f += spk(b, "edge", V(3.0, 32.6, 0.2), V(0.4, 1, 0.1), 2.6, 1.3, sides=4, rings=2)
    f += spk(b, "main", V(0, 33.2, 3.0), V(0, 0.5, 1), 3.6, 1.2, sides=4, rings=3, bend=(0, -0.8, 1.0))
    f += rags(b, "cloth", [-3.4, -0.6, 2.2, 4.2], 29.0, 22.6, 5.2, [0, -2.4, -0.6, -1.6], w=1.9, rot=rot_x(6), tilt=0.5)
    if tier >= 2:
        f += spk(b, "edge", V(-0.4, 33.4, -3.0), V(0, 0.9, -0.5), 3.2, 1.0, sides=4, rings=2)
    return {b: f}


def chest(tier=1):
    B = "armorBody"
    f = []
    rings = [rr(12.2, 4.6, -2.8, 2.7, cut=0.9), rr(17.0, 4.6, -3.0, 2.8, cut=0.9), rr(22.8, 4.8, -3.1, 2.9, cut=1.0), rr(24.2, 4.2, -2.8, 2.6, cut=1.0)]
    f += shell(B, "cloth", rings)
    # 肋の外骨格: 胸の前に反る肋 4 対、中央の胸骨
    f += bx(B, "main", (0, 18.6, -3.4), (1.8, 8.6, 0.9), taper=(0.7, 1.0), skip=("b", "+z"))
    for s in (-1, 1):
        for k in range(4):
            y = 22.2 - 2.2 * k
            L = 1.0 + 0.0 * k
            f += crv(B, "edge", [(s * 0.9, y + 0.3, -3.7), (s * 2.7, y - 0.1, -3.9), (s * 4.2, y - 0.8, -3.2), (s * 4.9, y - 1.6, -1.6)],
                     [0.62, 0.68, 0.58, 0.3], sides=3, hint=(0, 1, 0), phase=90)
    f += gem(B, (0, 21.2, -4.1), (1.2, 1.6, 0.5))
    f += bx(B, "wrap", (0, 14.2, -3.2), (9.6, 2.0, 0.9), skip=("b", "+z"))
    f += bx(B, "trim", (0, 13.0, -3.0), (9.4, 0.7, 0.8), skip=("b", "+z"))
    # 背: 椎骨の列と棘
    for k in range(5):
        y = 22.6 - 2.5 * k
        f += bx(B, "main", (0.3 * (k % 2), y, 3.4), (2.6 - 0.2 * k, 1.9, 1.8), taper=(0.8, 0.8))
        f += spk(B, "edge", V(0.3 * (k % 2), y, 4.2), V(0, 0.2, 1), 2.6 - 0.2 * k, 0.6, sides=4, rings=2, bend=(0, 0.6, 0))
    f += rags(B, "cloth", [-3.6, -1.2, 1.4, 3.8], 14.0, 7.0, 3.0, [0, -2.6, -0.8, -1.8], w=2.2, rot=rot_x(5), tilt=0.4)
    out = {B: f}
    for s in (-1, 1):
        ab = ARM[s]
        g = []
        g += bx(ab, "cloth", (s * 6.4, 18.0, 0), (4.8, 11.0, 4.8), taper=(0.97, 0.97), skip=("u",))
        g += bx(ab, "wrap", (s * 6.4, 14.4, 0), (5.1, 2.6, 5.1), skip=("b", "u"))
        g += bx(ab, "wrap", (s * 6.4, 19.4, 0), (5.1, 1.6, 5.1), skip=("b", "u"))
        if s == 1:   # 左: 大きな肩 (椎骨を重ねた塔 + 肋の庇)
            for k, (dy, w) in enumerate(((0.0, 6.4), (2.0, 5.4), (3.8, 4.2))):
                g += bx(ab, "main", (s * 8.4, 24.0 + dy, 0), (w, 2.0, w - 0.4), rot=rot_z(-s * (10 + 4 * k)), taper=(0.85, 0.85))
            g += spk(ab, "edge", V(s * 8.6, 28.6, 0), V(s * 0.3, 1, 0), 6.0, 1.6, sides=4, rings=3, bend=(s * 1.4, 0, 0))
            g += spk(ab, "edge", V(s * 11.0, 23.6, 0.2), V(s * 1, 0.3, 0), 4.2, 1.0, sides=4, rings=2, bend=(0, -1.0, 0))
            g += spk(ab, "edge", V(s * 8.4, 24.0, 3.0), V(0, 0.4, 1), 3.4, 1.0, sides=4, rings=2)
        else:        # 右: 小さな肩当て + 包帯 + 短い棘
            g += bx(ab, "guard", (s * 7.6, 24.2, 0), (5.4, 1.8, 5.4), rot=rot_z(-s * 14), taper=(0.8, 0.85))
            g += bx(ab, "wrap", (s * 7.6, 25.0, 0), (5.6, 0.8, 5.6), rot=rot_z(-s * 14))
            g += spk(ab, "edge", V(s * 9.0, 24.8, 0), V(s * 0.5, 1, 0), 3.2, 1.0, sides=4, rings=2, bend=(s * 0.8, 0, 0))
        out[ab] = g
    return out


def legs(tier=1):
    out = {}
    for s in (-1, 1):
        lb = LEG[s]
        cx = s * 2.15
        g = []
        g += bx(lb, "cloth", (cx, 6.4, 0), (4.4, 11.4, 4.8), taper=(0.97, 0.97), skip=("b", "u"))
        for y in (10.2, 7.4):
            g += bx(lb, "wrap", (cx, y, 0), (4.8, 1.8, 5.1), skip=("b", "u"))
        g += bx(lb, "main", (cx, 6.4, -2.9), (3.8, 2.6, 1.2), taper=(0.8, 0.75))
        g += spk(lb, "edge", V(cx, 6.8, -3.3), V(0, 0.4, -1), 2.2, 0.9, sides=4, rings=2)
        g += bx(lb, "main", (cx, 3.2, -2.7), (3.0, 4.2, 0.8), taper=(0.8, 0.9), skip=("b", "+z"))
        g += bx(lb, "trim", (cx, 11.8, 0), (4.8, 0.8, 5.1), skip=("b", "u"))
        g += rags(lb, "cloth", [cx - 1.0, cx + 1.1], 11.8, 5.0, -2.9, [0, -2.4], w=1.8, rot=None, tilt=0.3 * s)
        if s == -1:
            g += gem(lb, (cx, 8.6, -3.0), (1.0, 1.2, 0.4))
        out[lb] = g
    return out


def boots(tier=1):
    out = {}
    for s in (-1, 1):
        bb = BOOT[s]
        cx = s * 2.15
        g = []
        g += bx(bb, "wrap", (cx, 2.2, 0), (4.6, 4.4, 4.8), taper=(0.95, 0.95), skip=("b",))
        g += bx(bb, "cloth", (cx, 3.6, 0), (4.9, 1.0, 5.1), skip=("b", "u"))
        g += bx(bb, "main", (cx, 1.0, -3.0), (4.0, 2.0, 2.0), taper=(0.6, 0.6), skip=("b", "+z"))
        for dx in (-1.2, 0.0, 1.2):   # 爪先の骨爪
            g += spk(bb, "edge", V(cx + dx, 1.0, -3.8), V(dx * 0.15, -0.1, -1), 2.6 + 0.4 * (dx == 0), 0.55, sides=4, rings=2)
        g += spk(bb, "edge", V(cx, 2.4, 3.0), V(0, 0.3, 1), 2.6, 0.7, sides=4, rings=2)    # 踵の距
        g += bx(bb, "trim", (cx, 0.5, 0), (4.8, 0.8, 5.2), skip=("u",))
        out[bb] = g
    return out


def build(tier=1):
    pieces = {"helmet": helmet(tier), "chestplate": chest(tier), "leggings": legs(tier), "boots": boots(tier)}
    return {k: [f for bone_faces in v.values() for f in bone_faces] for k, v in pieces.items()}
