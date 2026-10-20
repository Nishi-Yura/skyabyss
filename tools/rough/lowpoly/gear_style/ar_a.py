"""様式 A「量塊」の防具 (岩鯨の一式): 大きな板が体を覆い、肩と兜で輪郭が箱から大きく外れる。
兜 = 全面の兜 (細い視き穴 1 本が光る)、胸 = 骨の胸板と石の殻、肩 = 二段の大きな石板、脚 = 太い腿当てと膝の骨、靴 = 重い鉄靴。
光る所は兜の視き穴・胸の核・膝の宝石の 3 点だけ。"""
from __future__ import annotations

import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE.parent / "armor"))
from akit import *  # noqa: F401,F403,E402
from akit import V, bx, ext, spk, crv, rr, shell, hide_front, rot_x, rot_y, rot_z, ARM, LEG, BOOT  # noqa: E402


def gem(b, c, size=(1.4, 0.8, 0.5)):
    return bx(b, "gem", c, size, taper=(0.8, 0.8), skip=("b",))


def helmet(tier=1):
    b = "armorHead"
    f = []
    rings = [rr(23.8, 4.9, -4.9, 4.9, cut=1.3), rr(28.4, 5.1, -5.1, 5.1, cut=1.3), rr(31.6, 4.8, -4.8, 4.9, cut=1.5), rr(33.6, 3.4, -3.4, 3.8, cut=1.5)]
    f += shell(b, "spine", rings, caps=(False, "spine"))
    f += bx(b, "main", (0, 30.2, -5.2), (10.4, 1.6, 0.8), skip=("b", "+z"))           # 骨の眉庇
    f += bx(b, "dark", (0, 27.9, -5.2), (7.6, 1.8, 0.5), skip=("b", "+z"))             # 視き穴
    f += bx(b, "gem", (0, 27.9, -5.5), (6.0, 0.55, 0.2), skip=("b", "+z"))
    f += bx(b, "guard", (0, 25.0, -5.2), (7.0, 2.6, 0.8), skip=("b", "+z"), taper=(0.9, 0.9))   # 顎当て
    for s in (-1, 1):
        f += bx(b, "main", (s * 5.3, 27.0, -0.4), (1.2, 6.6, 7.4), rot=rot_z(-s * 6), taper=(0.8, 0.9))   # 頬の骨板
    # 骨の背びれ冠: 前から後ろへ流れる
    f += ext(b, "main", [(-5.8, 0), (5.2, 0), (4.0, 2.6), (0.6, 4.8), (-3.4, 3.4)], 1.1, c=(0, 33.4, 0), rot=rot_y(90))
    f += ext(b, "edge", [(-5.0, 0), (4.0, 0), (2.4, 1.4), (-2.0, 1.8)], 1.5, c=(0, 33.2, 0), rot=rot_y(90))
    if tier >= 2:
        f += spk(b, "edge", (0, 35.2, 2.4), (0, 0.5, 1), 4.0, 0.9)
    return {b: f}


def chest(tier=1):
    B = "armorBody"
    f = []
    rings = [rr(12.2, 4.9, -3.1, 3.0, cut=0.9), rr(17.0, 5.1, -3.6, 3.3, cut=1.1), rr(22.6, 5.1, -3.5, 3.3, cut=1.1), rr(24.3, 4.4, -3.0, 3.0, cut=1.0)]
    f += shell(B, "spine", rings)
    f += bx(B, "main", (0, 19.2, -4.2), (7.8, 6.8, 1.0), taper=(0.92, 0.95), skip=("b", "+z"))   # 骨の胸板
    f += bx(B, "main", (0, 19.2, -4.9), (4.2, 5.0, 0.6), taper=(0.9, 0.9), skip=("b", "+z"))
    f += gem(B, (0, 19.8, -5.3), (2.0, 2.0, 0.5))
    f += bx(B, "trim", (0, 13.0, -3.6), (9.6, 1.4, 1.0), skip=("b", "+z"))
    f += bx(B, "guard", (0, 13.1, -4.3), (2.4, 1.8, 0.6), skip=("b", "+z"))
    f += bx(B, "spine", (0, 23.4, 0), (9.6, 1.6, 6.6), taper=(0.9, 0.9))                     # 首当て
    # 背: 石の瘤と骨の背びれ 3 枚
    f += bx(B, "spine", (0, 19.0, 4.1), (7.8, 8.0, 2.0), taper=(0.92, 0.92), skip=("b", "-z"))
    for k, (y, w) in enumerate(((22.6, 3.4), (19.4, 3.0), (16.2, 2.4))):
        f += ext(B, "main", [(-w, 0), (w, 0), (w * 0.5, 3.6 - 0.4 * k), (-w * 0.2, 4.2 - 0.4 * k)], 1.0, c=(0, y, 4.6 + 0.2 * k), rot=rot_x(-20))
    out = {B: f}
    for s in (-1, 1):
        ab = ARM[s]
        g = []
        g += bx(ab, "cloth", (s * 6.2, 18.0, 0), (4.6, 11.0, 4.8), taper=(0.97, 0.97), skip=("u",))
        g += bx(ab, "guard", (s * 6.2, 14.6, 0), (5.2, 3.6, 5.4), taper=(0.95, 0.95))          # 籠手の石
        g += bx(ab, "main", (s * 6.2, 13.1, 0), (5.5, 0.8, 5.7))
        # 大きな二段の肩
        g += bx(ab, "spine", (s * 8.0, 24.5, 0), (8.4, 2.0, 7.0), rot=rot_z(-s * 12), taper=(0.78, 0.85))
        g += bx(ab, "spine", (s * 9.6, 22.0, 0), (7.0, 1.8, 6.0), rot=rot_z(-s * 22), taper=(0.78, 0.85))
        g += bx(ab, "main", (s * 8.0, 25.7, 0), (7.6, 0.8, 6.4), rot=rot_z(-s * 12), taper=(0.78, 0.85))
        if s == -1:   # 右肩だけ大きな牙
            g += spk(ab, "main", V(s * 10.0, 25.4, 0.0), V(s * 0.55, 1, 0.0), 6.4, 1.5, sides=4, rings=3, bend=(s * 1.6, 0, 0))
        out[ab] = g
    return out


def legs(tier=1):
    out = {}
    for s in (-1, 1):
        lb = LEG[s]
        cx = s * 2.15
        g = []
        g += bx(lb, "cloth", (cx, 6.2, 0), (4.4, 11.4, 4.8), taper=(0.97, 0.97), skip=("b", "u"))
        g += bx(lb, "spine", (cx, 9.6, -2.7), (4.2, 5.2, 1.0), taper=(0.95, 0.92), skip=("b", "+z"))     # 腿当て
        g += bx(lb, "guard", (cx, 11.0, -2.9), (4.5, 2.6, 1.0), rot=rot_x(-6), taper=(0.95, 0.9))      # 草摺
        g += bx(lb, "main", (cx, 6.6, -3.0), (4.2, 2.8, 1.4), taper=(0.8, 0.75))                     # 膝の骨
        g += spk(lb, "main", V(cx, 6.6, -3.6), V(0, 0.15, -1), 2.6, 1.0, sides=4, rings=2)
        g += bx(lb, "spine", (cx, 3.6, -2.7), (3.8, 4.4, 1.0), taper=(0.9, 0.9), skip=("b", "+z"))   # すね当て
        g += bx(lb, "trim", (cx, 11.8, 0), (4.8, 0.8, 5.1), skip=("b", "u"))
        if s == -1:
            g += gem(lb, (cx, 6.7, -3.8), (1.2, 1.0, 0.4))
        out[lb] = g
    return out


def boots(tier=1):
    out = {}
    for s in (-1, 1):
        bb = BOOT[s]
        cx = s * 2.15
        g = []
        g += bx(bb, "spine", (cx, 2.2, 0), (4.8, 4.4, 5.0), taper=(0.95, 0.95), skip=("b",))
        g += bx(bb, "guard", (cx, 1.2, -3.6), (4.4, 2.4, 2.6), taper=(0.8, 0.7), skip=("b",))     # 爪先
        g += bx(bb, "main", (cx, 4.2, 0), (5.0, 0.9, 5.2), skip=("b", "u"))                      # 足首の骨
        g += bx(bb, "main", (cx, 3.0, -2.9), (3.4, 2.6, 0.8), skip=("b", "+z"))
        g += bx(bb, "trim", (cx, 0.5, 0), (5.1, 0.8, 5.4), skip=("u",))
        out[bb] = g
    return out


def build(tier=1):
    pieces = {"helmet": helmet(tier), "chestplate": chest(tier), "leggings": legs(tier), "boots": boots(tier)}
    return {k: [f for bone_faces in v.values() for f in bone_faces] for k, v in pieces.items()}
