"""潮殻の鎧 (最終)。様式 mix: B 祭具の流れる優雅さ (帆立の扇・真珠層の板・珊瑚の枝・薔薇色の帯・真珠) + C の使い込み (藤壺・海藻の巻き・欠け)。
体は濡れた石色の下着の上に、青緑の貝の胴と真珠層の胸板。光は真珠 (額・胸・帯留め) だけ + 階級の縁の筋。
階級で輪郭が変わる:
  通常 = 開いた貝の頭巾 + 背の小さな扇 + 肩の帆立 3 枚 + 斜めの帯と腰の尾 + 膝の貝
  レア = 扇 5 枚・肩 4 枚・こめかみと肩の珊瑚・帯の尾が 2 本・足首の鰭
  伝説 = 珊瑚の冠 + 大きな背の扇 7 枚・肩 5 枚と真珠・長く流れる帯 2 組・腿の珊瑚・大きな足首の鰭
材質: main = 青緑の貝 / sec = 真珠層 / edge = 明るい真珠層 / cloth = 濡れた下着 / sash = 薔薇色の帯 / cord = 海藻の縄 / acc = 珊瑚 / trim = 階級の縁 / gem = 真珠の光 / dark。"""
from __future__ import annotations

import math

from akit import *  # noqa: F401,F403
from akit import V, bx, ext, spk, crv, rr, shell, rot_x, rot_y, rot_z, ARM, LEG, BOOT, feather, hide_front


def gem(b, c, size=(1.4, 0.8, 0.5)):
    return bx(b, "gem", c, size, taper=(0.8, 0.8), skip=("b",))


def fan(b, mat, base, n, L, spread, w, th=0.7, mid=0.0, plane="xy", normal=None, mats=None):
    """帆立の扇: base から n 枚の板が開く。plane = 'xy' (広い面は ±Z)、'yz' (広い面は ±X)。mid = 扇の中心の向き (度)。"""
    out = []
    for i in range(n):
        a = math.radians(mid + (i - (n - 1) / 2) * spread)
        Li = L * (1.0 - 0.15 * abs(i - (n - 1) / 2) / max(1.0, (n - 1) / 2))
        if plane == "xy":
            tip = (base[0] + math.sin(a) * Li, base[1] + math.cos(a) * Li, base[2])
            nn = normal or (0, 0, 1)
        else:
            tip = (base[0], base[1] + math.cos(a) * Li, base[2] + math.sin(a) * Li)
            nn = normal or (1, 0, 0)
        out += feather(b, mat, base, tip, w, th, big=False, n=nn, mats=mats)
    return out


def scallop(b, mat, rib, base, R, half, mid=0.0, plane="xy", ribs=3, th=0.8, rth=1.4, dz=0.0):
    """帆立の貝殻: 扇形の板 (半径 R、中心の向き mid、開き ±half 度) + 放射状の筋 ribs 本。
    plane = 'xy' (広い面は ±Z)、'yz' (広い面は ±X、後ろ +Z へ開く)。"""
    n = 5
    pts = [(0.0, 0.0)]
    for i in range(n + 1):
        a = math.radians(mid - half + 2 * half * i / n)
        pts.append((math.sin(a) * R, math.cos(a) * R))
    if plane == "xy":
        c = (base[0], base[1], base[2] + dz)
        out = ext(b, mat, pts, th, c=c)
        for k in range(ribs):
            a = math.radians(mid - half * 0.72 + 2 * half * 0.72 * k / max(1, ribs - 1))
            out += feather(b, rib, (base[0], base[1], base[2] + dz), (base[0] + math.sin(a) * R * 1.0, base[1] + math.cos(a) * R * 1.0, base[2] + dz),
                           R * 0.11, rth, big=False, n=(0, 0, 1))
        return out
    pts = [(-x, y) for x, y in pts]
    c = (base[0] + dz, base[1], base[2])
    out = ext(b, mat, pts, th, c=c, rot=rot_y(90))
    for k in range(ribs):
        a = math.radians(mid - half * 0.72 + 2 * half * 0.72 * k / max(1, ribs - 1))
        out += feather(b, rib, c, (c[0], c[1] + math.cos(a) * R, c[2] + math.sin(a) * R), R * 0.11, rth, big=False, n=(1, 0, 0))
    return out


def ribbon(b, pts, w, th=0.3, mat="sash", taper=0.4):
    """流れる帯: 薄く幅のある筒。pts に沿って先が細る。"""
    n = len(pts)
    ra = [w * (1 - (1 - taper) * i / (n - 1)) for i in range(n)]
    return crv(b, mat, pts, ra, [th] * n, sides=4, caps=(False, True), hint=(1, 0, 0))


def tail(b, c, w, length, rot=None, mat="sash", th=0.35):
    """帯の尾: 先が斜めに切れた垂れ。c = 吊り元の中心。"""
    pts = [(-w, 0), (w, 0), (w * 0.8, -length * 0.7), (w * 0.2, -length), (-w * 0.9, -length * 0.55)]
    return ext(b, mat, pts, th, c=c, rot=rot)


def pearl(b, c, r=0.9):
    return bx(b, "gem", c, (r * 1.6, r * 1.6, r * 1.6), taper=(0.7, 0.7), rot=rot_y(45))


def barn(b, c, n=3, d=(0, 0, -1), sp=0.7, mat="edge"):
    """藤壺: 小さな錐の群れ。"""
    out = []
    for i in range(n):
        a = 2.3 * i + 0.6
        off = V(math.cos(a) * sp, math.sin(a * 1.4) * sp, 0)
        if abs(d[0]) > 0.5:
            off = V(0, off[1], off[0])
        out += spk(b, mat, V(*c) + off, V(*d), 0.9, 0.42, sides=3, rings=1)
    return out


def coral(b, base, d, L, r=0.8, bend=(0, 0, 0), rings=2, mat="acc"):
    return spk(b, mat, V(*base), V(*d), L, r, sides=4, rings=rings, bend=bend)


def helmet(r):
    t = {"common": 0, "rare": 1, "legend": 2}[r]
    b = "armorHead"
    f = []
    rings = [rr(24.2, 4.7, -4.5, 4.8, cut=1.3), rr(28.4, 5.1, -5.1, 5.2, cut=1.4), rr(31.6, 4.9, -4.9, 5.0, cut=1.5), rr(33.6, 3.4, -3.4, 3.8, cut=1.5)]
    cap = hide_front(shell(b, "main", rings, caps=(False, "main")), 29.2)       # 顔の前を開けた頭巾
    f += cap
    f += bx(b, "sec", (0, 30.4, -5.3), (10.6, 1.8, 0.8), taper=(0.96, 0.9), skip=("b", "+z"))     # 真珠層の眉庇
    f += bx(b, "cord", (0, 29.2, -5.2), (10.4, 0.5, 0.6), skip=("b", "+z"))                        # 海藻の縁取り
    f += pearl(b, (0, 31.0, -5.7), 0.85)                                                           # 額の真珠
    for s in (-1, 1):
        f += bx(b, "sec", (s * 5.3, 27.2, -0.2), (1.2, 6.0, 7.0), rot=rot_z(-s * 5), taper=(0.8, 0.9))   # 頬の貝板
        f += bx(b, "edge", (s * 5.6, 28.8, -0.8), (0.7, 2.6, 3.2), rot=rot_z(-s * 5), taper=(0.8, 0.9))
    f += barn(b, (5.9, 26.0, -1.2), 3, (1, 0, 0))
    # 背の扇 (後頭部から後ろ上へ開く)
    f += scallop(b, "sec", "edge", (0, 30.6, 4.6), 7.0 + 1.6 * t, (40, 46, 52)[t], mid=-18, plane="yz", ribs=(3, 5, 5)[t])
    # 後ろへ流れる帯
    f += ribbon(b, [(1.4, 29.0, 5.0), (2.8, 26.4, 6.6), (2.0, 23.0, 6.0)], 1.6, th=0.3)
    f += bx(b, "trim", (0, 33.0, 0), (6.2, 0.6, 6.0), skip=("u",), taper=(1.0, 1.0)) if t else []
    if t >= 1:
        for s in (-1, 1):   # こめかみの珊瑚の枝
            f += coral(b, (s * 4.7, 31.4, -1.4), (s * 0.8, 1, -0.1), 4.4, 0.95, bend=(s * 1.0, 0, 0.6))
            f += coral(b, (s * 5.1, 30.0, 0.8), (s * 1.0, 0.7, 0.2), 3.0, 0.7)
        f += ribbon(b, [(-1.4, 29.0, 5.0), (-2.8, 25.8, 6.8), (-2.2, 22.0, 6.2)], 1.5, th=0.3)
    if t == 2:
        # 珊瑚の冠: 頭頂から立ち上がる枝 + 枝先の真珠
        for k, (x, z, L) in enumerate(((0, -0.6, 6.8), (-2.4, 0.6, 5.2), (2.4, 0.6, 5.2), (0, 2.6, 4.2))):
            f += coral(b, (x, 33.2, z), (x * 0.18, 1, z * 0.1), L, 1.1, bend=(x * 0.4, 0, 0.8 if z > 1 else 0), rings=3)
        f += pearl(b, (0, 40.2, -0.2), 0.8)
        f += pearl(b, (-3.0, 38.0, 0.8), 0.6)
        f += pearl(b, (3.0, 38.0, 0.8), 0.6)
        f += ribbon(b, [(0, 28.0, 5.2), (0.6, 23.0, 7.6), (-0.4, 18.0, 6.8)], 1.4, th=0.3)
    return {b: f}


def chest(r):
    t = {"common": 0, "rare": 1, "legend": 2}[r]
    B = "armorBody"
    f = []
    rings = [rr(12.2, 4.6, -3.1, 3.0, cut=0.9, keel=0.5), rr(17.0, 5.1, -3.6, 3.3, cut=1.1, keel=0.8), rr(22.6, 5.1, -3.5, 3.3, cut=1.1, keel=0.5),
             rr(24.3, 4.4, -3.0, 3.0, cut=1.0, keel=0.3)]
    f += shell(B, "main", rings)
    # 真珠層の胸板 + 縦の貝の筋
    f += bx(B, "sec", (0, 19.0, -4.4), (8.2, 7.0, 1.0), taper=(0.92, 0.94), skip=("b", "+z"))
    for x in (-2.8, 0, 2.8):
        f += bx(B, "edge", (x, 19.0, -5.0), (0.9, 6.0, 0.5), taper=(0.7, 0.8), skip=("b", "+z"))
    f += pearl(B, (0.05, 18.4, -6.1), 1.0)
    f += bx(B, "cord", (0, 15.2, -3.9), (9.8, 0.8, 0.8), skip=("b", "+z"))                          # 海藻の縄の帯
    f += bx(B, "trim", (0, 13.0, -3.6), (9.8, 1.2, 1.0), skip=("b", "+z"))
    f += bx(B, "main", (0, 23.4, 0), (9.6, 1.6, 6.4), taper=(0.9, 0.9))                              # 襟
    f += bx(B, "cord", (0, 23.0, -3.0), (8.0, 0.5, 0.5), skip=("b", "+z"))
    # 背: 貝の瘤 + 背の扇
    f += bx(B, "main", (0, 19.0, 4.1), (7.8, 8.0, 2.0), taper=(0.92, 0.92), skip=("b", "-z"))
    f += bx(B, "cord", (0, 15.4, 3.9), (9.6, 0.8, 0.8), skip=("b", "-z"))
    f += scallop(B, "sec", "edge", (0, 14.6, 5.2), 8.4 + 2.2 * t, (46, 54, 62)[t], mid=0, plane="xy", ribs=(3, 5, 7)[t])
    # 斜めの帯 (右肩から左腰へ) と腰の帯留め
    f += ext(B, "sash", [(-1.4, -6.7), (1.4, -6.7), (1.4, 6.7), (-1.4, 6.7)], 0.5, c=(0.05, 18.4, -5.5), rot=rot_z(-42))
    f += bx(B, "trim", (-4.2, 13.6, -3.9), (2.4, 2.0, 0.8), skip=("b", "+z"))
    f += pearl(B, (-4.2, 13.6, -4.5), 0.7)
    f += tail(B, (-4.4, 13.0, -0.6), 1.8, 7.0 + 2.0 * t, rot=rot_y(90))
    f += tail(B, (4.4, 13.0, -0.6), 1.6, 5.0 + 2.0 * t, rot=rot_y(90))
    f += barn(B, (4.2, 22.0, -3.8), 3, (0, 0, -1))
    if t >= 1:
        f += tail(B, (0, 13.0, 3.6), 2.2, 8.0, rot=None)
        f += coral(B, (-3.4, 23.6, 2.6), (-0.4, 1, 0.3), 4.2, 1.0, bend=(-1, 0, 1.0))
    if t == 2:
        # 背の珊瑚の冠と、長く流れる帯 2 本
        for x, L in ((-2.4, 6.0), (0.0, 7.6), (2.4, 6.0)):
            f += coral(B, (x, 22.0, 5.4), (x * 0.14, 1, 0.5), L, 1.1, bend=(x * 0.3, 0, 1.4), rings=3)
        f += ribbon(B, [(-2.0, 16.0, 5.8), (-3.6, 10.0, 7.2), (-2.4, 3.6, 6.4)], 2.0, th=0.3)
        f += ribbon(B, [(2.0, 16.0, 5.8), (3.6, 9.0, 7.0), (2.6, 2.0, 6.0)], 1.8, th=0.3)
    out = {B: f}
    for s in (-1, 1):
        ab = ARM[s]
        g = []
        g += bx(ab, "cloth", (s * 6.2, 18.0, 0), (4.6, 11.0, 4.8), taper=(0.97, 0.97), skip=("u",))
        g += bx(ab, "main", (s * 6.2, 14.6, 0), (5.2, 3.6, 5.4), taper=(0.95, 0.95))                 # 籠手の貝
        g += bx(ab, "sec", (s * 6.2, 13.1, 0), (5.5, 0.8, 5.7))
        g += bx(ab, "cord", (s * 6.2, 16.8, 0), (5.0, 0.7, 5.2), skip=("u",))                         # 二の腕の海藻
        g += bx(ab, "cord", (s * 6.2, 18.8, 0), (4.9, 0.6, 5.1), skip=("u",))
        g += ribbon(ab, [(s * 6.9, 15.0, 2.8), (s * 8.0, 10.6, 3.6), (s * 7.0, 7.0, 3.0)], 1.0, th=0.25) if t >= 1 else []
        # 肩: 帆立の扇 (外へ下へ開く)
        g += bx(ab, "main", (s * 6.2, 23.9, 0), (5.0, 1.0, 5.2), taper=(0.94, 0.94))                  # 肩の当て
        g += scallop(ab, "main", "sec", (s * 8.7, 24.2, 0), 5.2 + 0.8 * t, 46, mid=s * 120, ribs=(3, 4, 5)[t], th=1.0, rth=1.6)
        g += scallop(ab, "sec", "edge", (s * 6.2, 24.9, 0), 5.0 + 0.9 * t, 44, mid=s * 52, ribs=(2, 3, 3)[t], th=0.9, rth=1.5, dz=0.3)
        g += bx(ab, "trim", (s * 6.6, 24.6, 0), (4.0, 0.7, 5.0), skip=("b",), taper=(0.9, 0.9))
        if t >= 1:
            g += coral(ab, (s * 5.6, 25.4, 0.4), (s * 0.4, 1, 0), 4.8 if t == 1 else 6.0, 1.0, bend=(s * 1.2, 0, 0))
        if t == 2:
            g += pearl(ab, (s * 8.2, 25.0, -2.6), 0.8)
            g += coral(ab, (s * 4.4, 25.2, -1.6), (s * 0.2, 1, -0.2), 3.6, 0.8)
        g += barn(ab, (s * 9.4, 24.0, -0.2), 3, (0, 0, -1))
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
        g += bx(lb, "main", (cx, 9.6, -2.7), (4.2, 5.2, 1.0), taper=(0.95, 0.92), skip=("b", "+z"))     # 腿当ての貝
        g += bx(lb, "sec", (cx, 10.6, -3.1), (3.4, 2.8, 0.6), taper=(0.9, 0.9), skip=("b", "+z"))
        g += bx(lb, "cord", (cx, 8.0, 0), (4.8, 0.7, 5.0), skip=("b", "u"))
        g += bx(lb, "trim", (cx, 11.8, 0), (4.8, 0.8, 5.1), skip=("b", "u"))
        # 膝の貝 (小さな帆立)
        g += scallop(lb, "sec", "edge", (cx, 4.8, -3.3), 3.6, 52, mid=0, ribs=2, th=0.7, rth=1.1)
        g += bx(lb, "main", (cx, 3.6, -2.7), (3.8, 4.4, 1.0), taper=(0.9, 0.9), skip=("b", "+z"))     # すね当て
        g += bx(lb, "cord", (cx, 1.8, 0), (4.6, 0.6, 4.8), skip=("b", "u"))
        # 前の垂れ: 帯の裾 (流れる)
        g += ext(lb, "sash", [(-1.7, 0), (1.7, 0), (1.5, -7.0 - t), (0.2, -8.6 - t), (-1.5, -6.8 - t)], 0.4, c=(cx, 11.4, -3.2), rot=rot_x(-6))
        if s == -1:
            g += pearl(lb, (cx, 6.4, -4.1), 0.6)
        g += barn(lb, (cx + s * 2.4, 4.0, -1.6), 3, (s, 0, 0))
        if t >= 1:
            g += ext(lb, "edge", [(-1.6, 0), (1.6, 0), (0.8, 3.4), (-1.2, 2.6)], 0.9, c=(cx + s * 2.6, 8.4, 0.2), rot=rot_y(90) @ rot_z(-s * 16))   # 腿の外の貝
        if t == 2:
            g += coral(lb, (cx + s * 2.4, 9.6, 0.6), (s * 0.8, 0.8, 0.2), 4.2, 0.9, bend=(s * 1.0, 0.6, 0))
            g += ribbon(lb, [(cx + s * 2.4, 11.0, 2.6), (cx + s * 3.6, 6.0, 3.4), (cx + s * 2.6, 0.6, 3.0)], 1.4, th=0.25)
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
        g += bx(bb, "main", (cx, 1.2, -3.6), (4.4, 2.4, 2.6), taper=(0.8, 0.7), skip=("b",))     # 爪先
        g += bx(bb, "sec", (cx, 1.9, -4.8), (3.4, 1.2, 0.8), taper=(0.7, 0.7), skip=("b",))      # 爪先の真珠層
        g += bx(bb, "sec", (cx, 4.2, 0), (5.0, 0.9, 5.2), skip=("b", "u"))                        # 足首の真珠層の輪
        g += bx(bb, "cord", (cx, 3.2, 0), (5.0, 0.6, 5.2), skip=("b", "u"))
        g += bx(bb, "trim", (cx, 0.5, 0), (5.1, 0.8, 5.4), skip=("u",))
        g += pearl(bb, (cx + s * 2.7, 3.6, 0), 0.45)
        g += barn(bb, (cx, 1.0, -5.0), 3, (0, 0, -1), 0.9)
        # 足首の鰭 (流れる尾びれ): 通常は小さく、階級で大きく
        L = (3.0, 4.6, 6.4)[t]
        g += ext(bb, "edge", [(-2.0, 0), (2.0, 0), (1.2, L * 0.55), (-0.2, L), (-1.6, L * 0.6)], 0.9, c=(cx + s * 2.7, 3.2, 0.6), rot=rot_y(90) @ rot_z(-s * 28))
        if t == 2:
            g += coral(bb, (cx, 1.4, 2.4), (0, 0.4, 1), 3.4, 0.9)
            g += coral(bb, (cx + s * 2.2, 1.6, -3.0), (s * 0.4, 0.3, -1), 2.6, 0.7)
        out[bb] = g
    return out


def build(T="tide", rar="common"):
    pieces = {"helmet": helmet(rar), "chestplate": chest(rar), "leggings": legs(rar), "boots": boots(rar)}
    return {k: [f for bone_faces in v.values() for f in bone_faces] for k, v in pieces.items()}
