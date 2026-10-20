"""余燼の鎧 (最終)。A の量塊 (厚い玄武岩の胸・左右非対称の大きな肩) + C の鍛冶場の使い込み (冷めゆく溶岩の 1 本の縦の割れ目・灰・鉄の帯・鎖)
+ 火とかげの同一性: 兜の左右に開く熱の襟 (えら) と折れた角、背から腰へ続く背びれと短い尾板。
階級で輪郭が変わる:
  通常 = 折れた角 + 襟 2 枚ずつ + 大きな左肩 + 背びれ 4 + 尾板 2 + 腰の前垂れ
  レア = 角の根の副角 + 襟 3 枚 + 右肩の板 + 背びれ 6 + 尾板 3 + 胸の鎖 1 本 + 膝の板
  伝説 = 角の冠 (3 本) + 襟 4 枚 + 左肩の三段の山 + 背びれ 8 + 尾板 4 (反る) + 鎖 2 本と頬の鎖
材質: main = 玄武岩 / sec = 鉄の帯 / edge = 明るい鉄 / acc = 風化した岩 / cloth = 灰を被った下着 / chain = 鎖 / soot = 灰 / gem = 溶岩の割れ目 / trim / dark。"""
from __future__ import annotations

import math

from akit import *  # noqa: F401,F403
from akit import V, bx, ext, spk, crv, rr, shell, rot_x, rot_y, rot_z, ARM, LEG, BOOT, feather, hide_front


def chain_link(b, p0, p1, n=4, sag=0.0):
    """鎖: p0 -> p1 に n 個の輪 (交互に 90 度)。"""
    p0, p1 = V(*p0), V(*p1)
    d = p1 - p0
    out = []
    L = float(np.linalg.norm(d)) / n * 1.3
    ang = math.degrees(math.atan2(d[0], d[1]))
    for i in range(n):
        u = (i + 0.5) / n
        c = p0 + d * u + V(0, -sag * math.sin(math.pi * u), 0)
        R = rot_z(-ang)
        if i % 2:
            R = R @ rot_y(90)
        out += bx(b, "chain", tuple(c), (0.7, L, 1.4), rot=R)
    return out


def fins(b, base, dirv, n, L0, step, r=1.0, shrink=0.84, mat="main"):
    """背びれ: base から step ずつ進みながら n 本の棘 (先が細る)。"""
    out = []
    for i in range(n):
        p = V(*base) + V(*step) * i
        out += spk(b, mat, p, V(*dirv), L0 * (shrink ** i), r * (shrink ** (i * 0.5)), sides=4, rings=1)
    return out


def helmet(r):
    t = {"common": 0, "rare": 1, "legend": 2}[r]
    b = "armorHead"
    f = []
    rings = [rr(24.2, 4.8, -4.6, 4.9, cut=1.2), rr(28.4, 5.3, -5.2, 5.3, cut=1.3), rr(31.4, 5.0, -5.0, 5.1, cut=1.4), rr(33.2, 3.7, -3.6, 4.0, cut=1.4)]
    f += hide_front(shell(b, "main", rings, caps=(False, "main")), 29.4)
    f += bx(b, "main", (0, 30.4, -5.5), (10.8, 2.0, 1.2), taper=(0.96, 0.9), skip=("b", "+z"))            # 厚い眉庇
    f += bx(b, "sec", (0, 29.3, -5.6), (10.6, 0.8, 0.8), skip=("b", "+z"))                                 # 鉄の額帯
    f += bx(b, "gem", (0, 31.2, -6.15), (1.0, 1.8, 0.4), taper=(0.8, 0.8), skip=("b", "+z"))              # 眉間の細い割れ目
    for s in (-1, 1):   # 顎当て
        f += bx(b, "main", (s * 5.3, 26.4, -1.6), (1.2, 4.8, 5.8), rot=rot_z(-s * 4), taper=(0.8, 0.9))
        f += bx(b, "sec", (s * 5.7, 25.2, -2.2), (0.7, 1.0, 4.4))
    # 熱の襟 (えら): 側頭から後ろ外へ開く段の板
    nf = (2, 3, 4)[t]
    for s in (-1, 1):
        for k in range(nf):
            f += bx(b, "main" if k % 2 == 0 else "acc", (s * (6.2 + 1.3 * k), 28.8 - 1.5 * k, 1.6 + 0.9 * k), (0.9, 4.6 - 0.5 * k, 3.6),
                    rot=rot_y(s * (-12 - 8 * k)) @ rot_z(-s * 10), taper=(0.9, 0.7))
    # 折れた角: 左は長く、右は折れた株
    f += spk(b, "main", V(-2.6, 32.8, -0.4), V(-0.35, 1, 0.1), 6.4, 1.7, sides=4, rings=2, bend=(-0.8, 0, 0.6))
    f += spk(b, "edge", V(2.8, 32.8, -0.4), V(0.3, 1, 0.1), 2.0, 1.5, sides=4, rings=1)
    # 背びれ: 頭頂から後頭部へ
    f += fins(b, (0, 33.0, -1.8), (0, 1, 0.4), (3, 5, 6)[t], 3.4, (0, -0.1, 1.4), 0.95)
    if t >= 1:
        f += spk(b, "main", V(-4.4, 31.8, -0.6), V(-0.8, 1, 0.1), 3.6, 1.2, sides=4, rings=1)
        f += bx(b, "trim", (0, 33.0, 0), (6.2, 0.6, 6.0), skip=("u",))
    if t == 2:
        f += spk(b, "main", V(-0.4, 33.4, 1.4), V(0, 1, 0.1), 5.2, 1.5, sides=4, rings=2, bend=(0, 0, 0.8))
        f += chain_link(b, (5.8, 25.8, -2.0), (6.6, 22.4, -1.0), 3, sag=0.4)                              # 頬の鎖
    return {b: f}


def chest(r):
    t = {"common": 0, "rare": 1, "legend": 2}[r]
    B = "armorBody"
    f = []
    rings = [rr(12.2, 4.8, -3.3, 3.1, cut=1.0, keel=0.4), rr(17.0, 5.4, -3.9, 3.5, cut=1.2, keel=0.6), rr(22.6, 5.4, -3.8, 3.5, cut=1.2, keel=0.4),
             rr(24.3, 4.6, -3.2, 3.1, cut=1.1, keel=0.2)]
    f += shell(B, "main", rings)
    # 厚い胸板: 玄武岩の大きな面 + 1 本の縦の割れ目 (光る)
    f += bx(B, "acc", (0, 18.8, -4.5), (8.0, 7.8, 1.0), taper=(0.94, 0.94), skip=("b", "+z"))
    f += bx(B, "gem", (0.1, 18.8, -5.1), (0.6, 7.0, 0.5), taper=(0.7, 0.9), skip=("b", "+z"))
    # 鉄の帯: 胸の下と腰
    f += bx(B, "sec", (0, 14.8, -3.9), (10.4, 1.2, 1.0), skip=("b", "+z"))
    f += bx(B, "edge", (0, 13.0, -3.6), (10.6, 1.0, 1.0), skip=("b", "+z"))
    f += bx(B, "trim", (0, 12.6, 0), (9.8, 1.0, 6.0), skip=("u",)) if t else []
    f += bx(B, "main", (0, 23.5, 0), (10.4, 1.8, 6.6), taper=(0.9, 0.9))                                   # 襟
    # 背: 背びれが首から腰へ並び、短い尾板が垂れる
    f += bx(B, "main", (0, 19.0, 3.7), (7.8, 8.4, 1.8), taper=(0.92, 0.92), skip=("b", "-z"))
    nsp = (4, 6, 8)[t]
    f += fins(B, (0, 23.6, 4.4), (0, 0.5, 1), nsp, 3.6, (0, -(9.6 / nsp), 0.0), 1.2, shrink=0.93)
    for k in range(2 + t):   # 尾板: 先が細り、少しずつ後ろへ反る
        wd = 6.2 - 1.3 * k
        f += bx(B, "main" if k % 2 == 0 else "acc", (0, 12.0 - 2.5 * k, 3.5 + 0.55 * k * (1 + 0.4 * t)), (wd, 2.5, 1.5), taper=(0.9, 0.8), rot=rot_x(-8 * k))
    f += bx(B, "sec", (0, 13.0, 3.6), (9.6, 1.0, 1.4))
    # 鎖: 右肩から左腰へ斜めに (レアから)
    if t >= 1:
        f += chain_link(B, (4.0, 22.6, -4.4), (-3.6, 14.8, -4.4), 6, sag=0.0)
    if t == 2:
        f += chain_link(B, (-4.0, 22.6, 4.6), (3.6, 15.0, 4.6), 6, sag=0.0)
    out = {B: f}
    for s in (-1, 1):
        ab = ARM[s]
        g = []
        g += bx(ab, "cloth", (s * 6.2, 18.0, 0), (4.6, 11.0, 4.8), taper=(0.97, 0.97), skip=("u",))
        g += bx(ab, "main", (s * 6.2, 14.6, 0), (5.3, 3.6, 5.4), taper=(0.95, 0.95))                      # 籠手の岩
        g += bx(ab, "sec", (s * 6.2, 13.0, 0), (5.6, 0.9, 5.7))                                           # 鉄の帯
        g += bx(ab, "sec", (s * 6.2, 17.4, 0), (5.1, 0.8, 5.3), skip=("u",))
        if s == 1:   # 大きな左肩: 傾いた 2〜3 枚の山
            g += bx(ab, "main", (s * 6.6, 24.6, 0), (6.4, 1.4, 6.0), taper=(0.85, 0.9))
            g += bx(ab, "main", (s * 8.8, 25.6, 0), (6.8, 1.6, 5.8), rot=rot_z(-s * 16), taper=(0.8, 0.85))
            g += bx(ab, "acc", (s * 10.4, 22.8, 0), (5.4, 1.4, 5.0), rot=rot_z(-s * 34), taper=(0.8, 0.85))
            if t >= 1:
                g += bx(ab, "main", (s * 9.6, 28.0, 0), (5.2, 1.6, 4.6), rot=rot_z(-s * 10), taper=(0.75, 0.8))
            if t == 2:
                g += spk(ab, "main", V(s * 9.6, 28.6, 0), V(s * 0.3, 1, 0), 4.6, 1.5, sides=4, rings=1)
                g += bx(ab, "main", (s * 11.8, 25.4, 0), (4.0, 1.4, 4.2), rot=rot_z(-s * 46), taper=(0.8, 0.8))
            g += bx(ab, "sec", (s * 7.6, 24.0, 0), (1.0, 1.0, 5.4), rot=rot_z(-s * 10))
        else:        # 小さな右肩
            g += bx(ab, "main", (s * 6.4, 24.4, 0), (5.4, 1.2, 5.2), taper=(0.88, 0.9))
            g += bx(ab, "acc", (s * 8.2, 23.0, 0), (4.0, 1.2, 4.6), rot=rot_z(-s * 26), taper=(0.8, 0.85))
            if t >= 1:
                g += bx(ab, "main", (s * 8.6, 25.2, 0), (3.8, 1.2, 4.2), rot=rot_z(-s * 12), taper=(0.8, 0.85))
        g += bx(ab, "trim", (s * 6.4, 23.7, 0), (4.4, 0.6, 5.0), skip=("b",), taper=(0.9, 0.9))
        out[ab] = g
    return out


def legs(r):
    t = {"common": 0, "rare": 1, "legend": 2}[r]
    out = {}
    for s in (-1, 1):
        lb = LEG[s]
        cx = s * 2.15
        g = []
        g += bx(lb, "cloth", (cx, 6.2, 0), (4.4, 11.4, 4.8), taper=(0.97, 0.97), skip=("b", "u"))
        g += bx(lb, "main", (cx, 9.4, -2.8), (4.4, 5.6, 1.4), taper=(0.95, 0.92), skip=("b", "+z"))        # 腿当ての岩
        g += bx(lb, "sec", (cx, 11.8, 0), (4.9, 0.9, 5.2), skip=("b", "u"))
        g += bx(lb, "gem", (cx, 9.6, -3.55), (0.4, 4.2, 0.4), taper=(0.7, 0.9), skip=("b", "+z"))         # 腿の縦の割れ目 (細い線)
        g += bx(lb, "edge", (cx, 6.0, 0), (4.8, 0.7, 5.0), skip=("b", "u"))
        g += bx(lb, "main", (cx, 5.2, -3.0), (4.2, 2.6, 1.6), rot=rot_x(-10), taper=(0.85, 0.85), skip=("b",))   # 膝の板
        g += bx(lb, "main", (cx, 2.8, -2.7), (3.9, 4.6, 1.2), taper=(0.9, 0.9), skip=("b", "+z"))         # すね当て
        # 鍛冶の前垂れ (風化した岩の板、鉄の縁)
        g += bx(lb, "acc", (cx, 8.0, -3.6), (4.0, 7.2, 0.6), taper=(0.96, 0.9), skip=("b", "+z")) if False else []
        if s == 1:
            g += bx(lb, "acc", (cx - 0.6, 7.4, -3.4), (5.2, 6.4, 0.7), taper=(0.9, 0.85), skip=("b", "+z"))
            g += bx(lb, "sec", (cx - 0.6, 4.4, -3.7), (5.2, 0.6, 0.6), skip=("b", "+z"))
        if t >= 1:
            g += bx(lb, "main", (cx + s * 2.5, 9.2, 0.2), (1.0, 4.0, 3.6), rot=rot_z(-s * 12), taper=(0.8, 0.9))   # 腿の外の板
        if t == 2:
            g += chain_link(lb, (cx + s * 2.6, 10.8, 1.0), (cx + s * 3.2, 6.6, 1.0), 3, sag=0.4)
        out[lb] = g
    return out


def boots(r):
    t = {"common": 0, "rare": 1, "legend": 2}[r]
    out = {}
    for s in (-1, 1):
        bb = BOOT[s]
        cx = s * 2.15
        g = []
        g += bx(bb, "main", (cx, 2.2, 0), (4.9, 4.4, 5.1), taper=(0.95, 0.95), skip=("b",))
        g += bx(bb, "main", (cx, 1.2, -3.7), (4.5, 2.6, 2.8), taper=(0.8, 0.7), skip=("b",))               # 重い爪先
        g += bx(bb, "sec", (cx, 1.9, -5.2), (3.6, 1.0, 0.7), taper=(0.8, 0.8), skip=("b",))               # 爪先の鉄
        g += bx(bb, "sec", (cx, 4.2, 0), (5.2, 0.9, 5.4), skip=("b", "u"))                                  # 足首の鉄の帯
        g += bx(bb, "trim", (cx, 0.5, 0), (5.2, 0.8, 5.5), skip=("u",))
        if s == -1:
            g += bx(bb, "gem", (cx, 3.2, -2.9), (0.5, 1.6, 0.4), taper=(0.7, 0.9), skip=("b", "+z"))   # 甲の細い割れ目
        g += bx(bb, "acc", (cx, 4.8, 0), (4.6, 1.6, 4.8), taper=(0.9, 0.9), skip=("b",))                  # 足首の岩の襟
        if t >= 1:
            g += spk(bb, "main", V(cx + s * 2.6, 2.8, 0.4), V(s * 1, 0.4, 0.1), 2.6, 1.0, sides=4, rings=1)
        if t == 2:
            g += spk(bb, "main", V(cx, 4.6, 2.4), V(0, 0.5, 1), 3.2, 1.1, sides=4, rings=1)
        out[bb] = g
    return out


def build(T="ember", rar="common"):
    pieces = {"helmet": helmet(rar), "chestplate": chest(rar), "leggings": legs(rar), "boots": boots(rar)}
    return {k: [f for bone_faces in v.values() for f in bone_faces] for k, v in pieces.items()}
