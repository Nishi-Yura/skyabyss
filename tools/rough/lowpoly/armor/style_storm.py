"""雷翼の羽衣 (最終)。雷鳥: B の軽さ (輪・長い風切羽) + 嵐に焼けた傷み (羽先の焦げ・銅のひび)。
体は紺の鋼の軽い胸当て + 重なる羽の鱗。光は額・胸の稲妻の筋と銅のひびの中の細い線だけ。
階級で輪郭が変わる:
  通常 = 頭の羽の冠 3 + 耳の羽 + 肩の風切羽 3 + 背の翼 3 組 + 脚の長い羽 1 枚 + 足首の羽
  レア = 冠 5・肩 4・背の翼 4 組・銅の腕輪と膝の輪
  伝説 = 冠 7・肩 5・背の翼 5 組と銅の大きな輪 (光輪)・長い尾羽・足首の大きな羽
材質: main = 紺の鋼 / sec = 白い羽 / edge = 明るい羽 / soot = 焦げた羽先 / acc = 銅 / cloth = 暗い鋼の下着 / trim = 階級の縁 / gem = 稲妻の光 / dark。"""
from __future__ import annotations

import math

from akit import *  # noqa: F401,F403
from akit import V, bx, ext, spk, crv, rr, shell, rot_x, rot_y, rot_z, ARM, LEG, BOOT, feather, hide_front


def pl(b, base, tip, w, th=0.5, n=None, big=False, mat="sec", tipmat="soot", scorch=1):
    """風切羽 (先が焦げる)。"""
    if big:
        mats = [mat, mat, tipmat, tipmat, mat, mat] if scorch else None
    else:
        mats = [mat, tipmat, tipmat, mat] if scorch else None
    return feather(b, mat, base, tip, w, th, mats=mats, big=big, n=n)


def ring(b, c, R, r, mat="acc", n=8, plane="xy"):
    """銅の輪 (多角形の環)。plane = 'xy' 正面向き / 'xz' 水平。"""
    pts = []
    for i in range(n + 1):
        a = 2 * math.pi * (i % n) / n
        if plane == "xy":
            pts.append((c[0] + math.cos(a) * R, c[1] + math.sin(a) * R, c[2]))
        else:
            pts.append((c[0] + math.cos(a) * R, c[1], c[2] + math.sin(a) * R))
    return crv(b, mat, pts, [r] * (n + 1), [r] * (n + 1), sides=4, caps=(False, False), hint=(0, 0, 1) if plane == "xy" else (0, 1, 0))


def bolt(b, pts, w=0.55, z=-4.1, th=0.6):
    """胸・額の稲妻の筋 (光る折れ板)。"""
    out = []
    for (x0, y0), (x1, y1) in zip(pts[:-1], pts[1:]):
        out += ext(b, "gem", [(x0 - w, y0), (x0 + w, y0), (x1 + w, y1), (x1 - w, y1)], th, c=(0, 0, z))
    return out


def helmet(r):
    t = {"common": 0, "rare": 1, "legend": 2}[r]
    b = "armorHead"
    f = []
    rings = [rr(24.2, 4.6, -4.4, 4.7, cut=1.3), rr(28.2, 5.0, -5.0, 5.1, cut=1.4), rr(31.2, 4.8, -4.8, 4.9, cut=1.5), rr(32.8, 3.4, -3.4, 3.8, cut=1.4)]
    f += hide_front(shell(b, "main", rings, caps=(False, "main")), 29.2)
    f += bx(b, "acc", (0, 29.8, -5.0), (10.4, 0.9, 0.8), skip=("b", "+z"))                      # 銅の額帯
    f += bx(b, "gem", (0, 29.8, -5.5), (1.4, 0.9, 0.4), taper=(0.8, 0.8), skip=("b", "+z"))
    f += bx(b, "acc", (0, 28.0, 5.1), (10.4, 0.9, 0.8), skip=("b", "-z"))
    for s in (-1, 1):   # 耳の羽: 側頭から後ろ上へ流れる
        f += pl(b, (s * 5.2, 28.0, 0.5), (s * 8.4, 34.0, 3.0), 1.5, 0.5, n=(0, 0, 1), big=True)
        f += bx(b, "sec", (s * 5.3, 26.6, -0.2), (1.0, 4.0, 5.0), taper=(0.8, 0.9))                 # 頬の羽の板
    # 後頭部の羽の冠: 扇に開く
    n = (3, 5, 7)[t]
    for i in range(n):
        a = (i - (n - 1) / 2) * 15
        ar = math.radians(a)
        L = 6.6 + 1.3 * t - 0.5 * abs(i - (n - 1) / 2)
        f += pl(b, (math.sin(ar) * 1.2, 32.4, 3.4), (math.sin(ar) * L * 0.9, 33.0 + L * math.cos(ar) * 0.9, 4.4 + 2.0), 1.15, 0.5,
                n=(0, 0, 1), big=(i == n // 2))
    f += bx(b, "trim", (0, 33.0, 0), (6.2, 0.6, 6.0), skip=("u",)) if t else []
    if t == 2:
        f += ring(b, (0, 36.0, 6.4), 4.4, 0.35, plane="xy", n=8)
    return {b: f}


def chest(r):
    t = {"common": 0, "rare": 1, "legend": 2}[r]
    B = "armorBody"
    f = []
    rings = [rr(12.2, 4.5, -3.0, 2.9, cut=0.9), rr(17.0, 4.9, -3.4, 3.2, cut=1.0), rr(22.6, 4.9, -3.4, 3.2, cut=1.0), rr(24.2, 4.2, -2.9, 2.9, cut=1.0)]
    f += shell(B, "main", rings)
    # 胸: 重なる羽の鱗 (3 段、上が広い) + 稲妻の筋
    for k, (y, wdt) in enumerate(((21.6, 8.6), (19.0, 7.6), (16.4, 6.4))):
        f += bx(B, "sec", (0, y, -3.6), (wdt, 2.7, 1.0), rot=rot_x(10), taper=(0.9, 0.9), skip=("b", "+z"))
        f += bx(B, "soot", (0, y - 1.2, -4.0), (wdt * 0.9, 0.5, 0.4), skip=("b", "+z"))
    f += bolt(B, [(0.8, 22.6), (-0.9, 19.6), (0.9, 18.2), (-0.6, 15.4)], 0.5)
    f += bx(B, "acc", (0, 13.4, -3.4), (9.6, 1.2, 1.0), skip=("b", "+z"))                          # 銅の帯
    f += bx(B, "acc", (0, 23.4, 0), (9.6, 1.4, 6.0), taper=(0.9, 0.9))                              # 襟
    # 襟の羽
    for i, x in enumerate((-3.4, -1.1, 1.1, 3.4)):
        f += pl(B, (x, 23.6, -2.8), (x * 1.5, 26.4, -3.6), 0.9, 0.4, n=(0, 0, 1))
    f += bx(B, "main", (0, 19.0, 3.3), (7.8, 8.0, 1.2), taper=(0.92, 0.92), skip=("b", "-z"))
    # 背の翼: 肩甲骨から外へ下へ、長い風切羽を揃えて (3 / 4 / 5 組)
    nw = (3, 4, 5)[t]
    for s in (-1, 1):
        for k in range(nw):
            L = 9.0 + 2.0 * (nw - k) * 0.9
            f += pl(B, (s * (1.2 + 1.2 * k), 22.4 - 1.0 * k, 3.9 + 0.35 * k), (s * (6.0 + 2.3 * k + 0.8 * t), 22.4 - L * 0.55 - 1.0 * k, 4.4 + 0.35 * k),
                    1.5 - 0.1 * k, 0.55, n=(0, 0, 1), big=True)
    f += bx(B, "acc", (0, 22.2, 3.9), (4.4, 1.0, 1.2))
    if t == 2:
        f += ring(B, (0, 19.5, 6.6), 8.2, 0.45, plane="xy", n=10)                                  # 背の光輪
        for dx in (-2.0, 2.0):
            f += pl(B, (dx, 13.6, 3.2), (dx * 1.6, 2.4, 5.2), 1.4, 0.5, n=(0, 0, 1), big=True)   # 尾羽
    out = {B: f}
    for s in (-1, 1):
        ab = ARM[s]
        g = []
        g += bx(ab, "cloth", (s * 6.2, 18.0, 0), (4.5, 11.0, 4.7), taper=(0.97, 0.97), skip=("u",))
        g += bx(ab, "main", (s * 6.2, 14.6, 0), (5.0, 3.4, 5.2), taper=(0.95, 0.95))                 # 籠手
        g += bx(ab, "acc", (s * 6.2, 13.0, 0), (5.3, 0.8, 5.5))
        if t >= 1:
            g += bx(ab, "acc", (s * 6.2, 17.4, 0), (5.0, 0.7, 5.2), skip=("u",))                      # 銅の腕輪
        g += bx(ab, "main", (s * 6.2, 23.9, 0), (5.0, 1.0, 5.2), taper=(0.94, 0.94))                  # 肩当て
        # 肩の風切羽: 外へ下へ重なる (3 / 4 / 5)
        ns = (3, 4, 5)[t]
        for k in range(ns):
            g += pl(ab, (s * (6.0 + 0.5 * k), 24.4 - 0.3 * k, -0.6 + 0.5 * k), (s * (10.6 + 1.2 * k), 20.6 - 2.0 * k, -0.6 + 0.5 * k),
                    1.5 - 0.1 * k, 0.55, n=(0, 0, 1), big=(k == 1))
        g += bx(ab, "trim", (s * 6.6, 24.6, 0), (4.0, 0.7, 5.0), skip=("b",), taper=(0.9, 0.9))
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
        g += bx(lb, "main", (cx, 9.6, -2.7), (4.2, 5.0, 1.0), taper=(0.95, 0.92), skip=("b", "+z"))     # 腿当て
        g += bx(lb, "sec", (cx, 10.4, -3.2), (3.4, 2.4, 0.6), taper=(0.9, 0.9), skip=("b", "+z"))
        g += bx(lb, "trim", (cx, 11.8, 0), (4.8, 0.8, 5.1), skip=("b", "u"))
        g += bx(lb, "acc", (cx, 5.0, -2.9), (4.0, 1.4, 1.0), skip=("b", "+z"))                         # 膝の銅の輪の前
        g += bx(lb, "main", (cx, 3.2, -2.7), (3.8, 4.4, 1.0), taper=(0.9, 0.9), skip=("b", "+z"))     # すね当て
        if s == -1:   # 膝の稲妻の石 (光る画素を各部位に 1 つ)
            g += bx(lb, "gem", (cx, 5.0, -3.5), (1.6, 1.2, 0.5), taper=(0.8, 0.8), skip=("b", "+z"))
        if t >= 1:
            g += bx(lb, "acc", (cx, 5.0, 0), (4.8, 0.7, 5.0), skip=("b", "u"))
        # 外側の長い羽 1 枚 (裾の動き)
        g += pl(lb, (s * 4.7, 11.4, 0.3), (s * 7.6 + s * t * 0.6, 0.6 - t * 0.6, 0.3), 1.7, 0.6, n=(0, 0, 1), big=True)
        if t >= 1:
            g += pl(lb, (s * 4.7, 10.6, -1.0), (s * 6.4, 3.6, -1.0), 1.1, 0.5, n=(0, 0, 1))
        if t == 2:
            g += pl(lb, (cx, 11.4, 2.6), (cx * 1.2, 1.0, 4.4), 1.4, 0.5, n=(0, 0, 1))
        out[lb] = g
    return out


def boots(r):
    t = {"common": 0, "rare": 1, "legend": 2}[r]
    out = {}
    for s in (-1, 1):
        bb = BOOT[s]
        cx = s * 2.15
        g = []
        g += bx(bb, "main", (cx, 2.2, 0), (4.8, 4.4, 5.0), taper=(0.95, 0.95), skip=("b",))
        g += bx(bb, "main", (cx, 1.2, -3.6), (4.2, 2.4, 2.6), taper=(0.8, 0.7), skip=("b",))
        if s == -1:
            g += bx(bb, "gem", (cx, 3.2, -2.7), (1.4, 1.0, 0.5), taper=(0.8, 0.8), skip=("b", "+z"))
        g += spk(bb, "acc", V(cx, 1.0, -4.8), V(0, -0.1, -1), 2.6, 0.9, sides=4, rings=1)           # 蹴爪
        g += bx(bb, "acc", (cx, 4.2, 0), (5.0, 0.8, 5.2), skip=("b", "u"))
        g += bx(bb, "trim", (cx, 0.5, 0), (5.1, 0.8, 5.4), skip=("u",))
        # 足首の羽 (大きな 1 枚)
        L = (5.0, 6.4, 8.0)[t]
        g += pl(bb, (s * 4.4, 3.6, 0.6), (s * (5.8 + t * 0.6), 3.6 - L, 0.6), 1.6, 0.6, n=(0, 0, 1), big=True)
        if t == 2:
            g += pl(bb, (cx, 3.6, 2.6), (cx * 1.3, -1.6, 4.2), 1.3, 0.5, n=(0, 0, 1))
        out[bb] = g
    return out


def build(T="thunder", rar="common"):
    pieces = {"helmet": helmet(rar), "chestplate": chest(rar), "leggings": legs(rar), "boots": boots(rar)}
    return {k: [f for bone_faces in v.values() for f in bone_faces] for k, v in pieces.items()}
