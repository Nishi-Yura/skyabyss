#!/usr/bin/env python3
"""通常攻撃の形 (2026-11-12 の声「通常攻撃の魔弾もお願い」): 魔法使いの魔弾 (combat/StaffBolts) と呪術師の呪印 (combat/HexMarks) の模型。

    python -X utf8 -I tools/gen/mobs/attack_props/build_basic_props.py [種類 ...] [--no-install]

どちらもレベルの段 (10 ごと、0〜4) で形が変わる (今の粒の見た目の段に合わせる):
  sk_bolt_<段>  魔弾。前 (-Z) へ飛ぶ。芯は y=4 (描く側の傾きの軸 0.25 ブロック) に置く
      0 星の粒 / 1 双子星 / 2 彗星 / 3 三日月 / 4 星座
  sk_hex_<段>   呪印の噴き上がり。床から突き出る (高さは当たりの HEIGHT 2.6 ブロックほど)
      0 三角の牙 / 1 二重の輪の潮柱 / 2 五芒の杭 / 3 鈴の柱 / 4 渦の三本腕
人の技の印 (青緑に光る印) はどれも持つ。確かめの絵は docs/review/skill_props/basic.png
"""
from __future__ import annotations

import math
import sys
from pathlib import Path

import numpy as np

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
import build_skill_props as SK  # noqa: E402
from build_skill_props import *  # noqa: E402,F401,F403

CURSE = [(30, 12, 40), (62, 26, 84), (100, 48, 130), (146, 86, 180)]
AQUA = [(90, 200, 240), (140, 230, 255), (200, 246, 255), (240, 252, 255)]
LILAC = [(150, 120, 230), (184, 160, 250), (220, 206, 255), (246, 240, 255)]
AMBER = [(220, 140, 60), (246, 184, 90), (255, 222, 150), (255, 244, 214)]
NAVY = [(40, 60, 170), (70, 100, 220), (120, 150, 250), (190, 210, 255)]


def bmats():
    m = SK.smats()
    m.update({"curse": M_(CURSE, base=0.55, lit=0.3), "aqua": M_(AQUA, base=0.9, lit=0.1, noise=0.0, min=1),
              "lilac": M_(LILAC, base=0.9, lit=0.1, noise=0.0, min=1), "amber": M_(AMBER, base=0.9, lit=0.1, noise=0.0, min=1),
              "navy": M_(NAVY, base=0.9, lit=0.1, noise=0.0, min=1)})
    return m


BGLOW = set(SK.SGLOW) | {"aqua", "lilac", "amber", "navy"}
CY = 4.0


def star_core(m, name, mat, c, r):
    """八面の星の芯と、前後左右上下の短い光の角"""
    m.add(name, ell("body", mat, c, (r, r, r), segs=4, rings=2, ry=45))
    for k, d in enumerate(((1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0))):
        p = (c[0] + d[0] * r * 0.8, c[1] + d[1] * r * 0.8, c[2])
        m.add(f"{name}_ray{k}", cone("body", mat, p, d, r * 0.9, r * 0.3, sides=4, rings=1))


def tail(m, name, mat, c, length, r, n=1, spread=0.0):
    for k in range(n):
        a = k / max(1, n) * math.tau
        dx, dy = math.cos(a) * spread, math.sin(a) * spread
        m.add(f"{name}{k}", cone("body", mat, (c[0] + dx, c[1] + dy, c[2] + r * 0.6), (dx * 0.05, dy * 0.05, 1), length, r, sides=5, rings=2))


def sig_dots(m, r, n):
    """芯を囲む青緑の小さな点の輪 (人の技の印)"""
    for k in range(n):
        a = k / n * math.tau + math.pi / n
        m.add(f"sig{k}", ell("body", "sigil", (math.cos(a) * r, CY + math.sin(a) * r, 0.5), (0.6, 0.6, 0.6), segs=4, rings=1))


# ================================================================ 魔弾 (前 = -Z)
def bolt_0(m):
    # 星の粒: 青白の芯と金の尾
    star_core(m, "core", "star", (0, CY, 0), 2.6)
    tail(m, "tail", "goldl", (0, CY, 0), 8, 1.4)
    sig_dots(m, 3.8, 6)


def bolt_1(m):
    # 双子星: 水色と白の二つの芯 (回る)、銀青の尾が二本
    for s, mat in ((1, "aqua"), (-1, "star")):
        star_core(m, f"core{s}", mat, (s * 2.6, CY, 0), 2.0)
        tail(m, f"tail{s}", "rune", (s * 2.6, CY, 0), 6, 1.0)
    m.add("link", tub("body", "sigil", [np.array([-2.6, CY, 0]), np.array([2.6, CY, 0])], [0.5, 0.5], None, sides=4))


def bolt_2(m):
    # 彗星: 大きな薄紫の芯と、藤色から青へ長く引く三本の尾
    m.add("core", ell("body", "lilac", (0, CY, 0), (3.4, 3.4, 3.8), segs=10, rings=3))
    for k, (dx, dy, ln, r) in enumerate(((0, 0, 16, 2.4), (1.6, 1.0, 11, 1.4), (-1.5, -0.8, 10, 1.3))):
        m.add(f"tail{k}", cone("body", "rune" if k else "lilac", (dx, CY + dy, 2.0), (dx * 0.04, dy * 0.04, 1), ln, r, sides=6, rings=2))
    m.add("sig", ell("body", "sigil", (0, CY, -2.6), (1.4, 1.4, 1.4), segs=6, rings=2))


def bolt_3(m):
    # 三日月: 白金の弧 (飛ぶ向きに垂直な面、回る) と琥珀の短い尾
    n = 9
    for k in range(n):
        t = k / (n - 1)
        a = math.radians(-120 + t * 240)
        r = 4.2
        w = 1.5 * math.sin(t * math.pi) ** 0.7 + 0.35
        m.add(f"arc{k}", ell("body", "amber", (math.cos(a) * r, CY + math.sin(a) * r, 0), (w, w, 1.0), segs=6, rings=1))
    m.add("core", ell("body", "goldl", (0, CY, 0), (1.6, 1.6, 1.6), segs=6, rings=2))
    tail(m, "tail", "amber", (0, CY, 0), 6, 1.0)
    m.add("sig", ell("body", "sigil", (-4.2 * 0.5, CY, 0), (0.9, 0.9, 0.9), segs=6, rings=1))


def bolt_4(m):
    # 星座: 深い青の芯、三つの白い衛星、芯から衛星へ金の線 (回る)
    m.add("core", ell("body", "navy", (0, CY, 0), (3.0, 3.0, 3.0), segs=8, rings=3))
    for k in range(3):
        a = k / 3 * math.tau + math.pi / 2
        p = np.array([math.cos(a) * 6.0, CY + math.sin(a) * 6.0, 0.0])
        star_core(m, f"sat{k}", "star", tuple(p), 1.3)
        m.add(f"line{k}", tub("body", "goldl", [np.array([0, CY, 0]), p], [0.35, 0.35], None, sides=4))
    tail(m, "tail", "navy", (0, CY, 0), 7, 1.4)
    sig_dots(m, 4.2, 6)


# ================================================================ 呪印の噴き上がり (rise)
def hex_0(m):
    # 三角の牙: 黒紫の牙が三本、内へ反って三角に立つ
    for k in range(3):
        a = k / 3 * math.tau
        p0 = np.array([math.cos(a) * 7, -6.0, math.sin(a) * 7])
        mid = np.array([math.cos(a) * 6.5, 14.0, math.sin(a) * 6.5])
        top = np.array([math.cos(a) * 2.5, 32.0, math.sin(a) * 2.5])
        m.add(f"fang{k}", tub("body", "curse", [p0, mid, top], [3.2, 2.2, 0.4], None, sides=5))
        m.add(f"vein{k}", ell("body", "sigil", tuple(mid + np.array([math.cos(a) * 1.8, 0, math.sin(a) * 1.8])), (1.0, 3.0, 1.0), segs=4, rings=1))
    base_debris(m, "curse", 8, seed=51)
    ring_sigil(m, 9)


def hex_1(m):
    # 二重の輪の潮柱: 渦巻く水の柱を、逆に回る二つの輪が巻く
    m.add("spout", tub("body", "water", [np.array([0, -6.0, 0]), np.array([0, 18.0, 0]), np.array([0, 34.0, 0])], [4.6, 3.6, 1.2], None, sides=8))
    m.add("crest", ell("body", "foam", (0, 32, 0), (3.6, 2.4, 3.6), segs=8, rings=2))
    for k, (y, r, tilt) in enumerate(((10, 7.0, 12), (22, 5.6, -12))):
        n = 12
        for j in range(n):
            a = j / n * math.tau
            m.add(f"ring{k}_{j}", ell("body", "sigil" if j % 3 == 0 else "seaw",
                                      (math.cos(a) * r, y + math.sin(a) * r * math.sin(math.radians(tilt)), math.sin(a) * r), (1.4, 0.9, 1.4), segs=4, rings=1))
    base_debris(m, "rock", 8, seed=52)
    ring_sigil(m, 9)


def hex_2(m):
    # 五芒の杭: 黒い杭の頭に、五方へ尖る呪の棘の冠
    m.add("stake", tub("body", "obsid", [np.array([0, -6.0, 0]), np.array([0, 28.0, 0])], [4.4, 3.2], None, sides=5, caps=(True, True)))
    m.add("tip", cone("body", "obsid", (0, 28, 0), (0, 1, 0), 9, 3.2, sides=5, rings=1))
    for k in range(5):
        a = k / 5 * math.tau
        m.add(f"spike{k}", cone("body", "curse", (math.cos(a) * 2.6, 23, math.sin(a) * 2.6), (math.cos(a), 0.6, math.sin(a)), 12, 2.0, sides=4, rings=1))
        m.add(f"rune{k}", ell("body", "sigil", (math.cos(a + 0.6) * 4.0, 10 + k * 2.6, math.sin(a + 0.6) * 4.0), (0.9, 1.5, 0.9), segs=4, rings=1))
    base_debris(m, "curse", 8, seed=53)
    ring_sigil(m, 9)


def hex_3(m):
    # 鈴の柱: 彫った柱に大きな鈴、四方に小さな鈴
    m.add("pole", tub("body", "totem", [np.array([0, -6.0, 0]), np.array([0, 26.0, 0])], [2.6, 2.2], None, sides=8))
    m.add("beam", tub("body", "totem", [np.array([-8.0, 24.0, 0]), np.array([8.0, 24.0, 0])], [1.2, 1.2], None, sides=6, hint=(0, 1, 0)))
    m.add("beam2", tub("body", "totem", [np.array([0, 24.0, -8.0]), np.array([0, 24.0, 8.0])], [1.2, 1.2], None, sides=6, hint=(0, 1, 0)))
    m.add("bell", tub("body", "bronze", [np.array([0, 36.0, 0]), np.array([0, 30.0, 0]), np.array([0, 26.5, 0])], [1.6, 3.6, 5.6], None, sides=10, caps=(True, False)))
    m.add("lip", ell("body", "goldl", (0, 26.6, 0), (5.4, 0.8, 5.4), segs=10, rings=1))
    m.add("clap", ell("body", "sigil", (0, 25.6, 0), (1.3, 1.3, 1.3), segs=6, rings=2))
    for k in range(4):
        a = k / 4 * math.tau
        x, z = math.cos(a) * 8, math.sin(a) * 8
        m.add(f"sb{k}", tub("body", "bronze", [np.array([x, 23.0, z]), np.array([x, 19.0, z])], [1.0, 2.2], None, sides=8, caps=(True, True)))
        m.add(f"sc{k}", ell("body", "sigil", (x, 18.6, z), (0.7, 0.7, 0.7), segs=4, rings=1))
    base_debris(m, "rock", 8, seed=54)
    ring_sigil(m, 9)


def hex_4(m):
    # 渦の三本腕: 床からねじれて昇る黒紫の三本の腕が、上で一つにまとまる
    for k in range(3):
        pts, rad = [], []
        n = 8
        for j in range(n):
            t = j / (n - 1)
            a = k / 3 * math.tau + t * 2.4
            r = 7.0 * (1 - t) + 1.2
            pts.append(np.array([math.cos(a) * r, -6.0 + t * 40.0, math.sin(a) * r]))
            rad.append(2.8 * (1 - t) + 0.4)
        m.add(f"arm{k}", tub("body", "curse", pts, rad, None, sides=5))
        m.add(f"sucker{k}", ell("body", "sigil", tuple(pts[3] + np.array([0, 0.5, 0])), (1.0, 1.0, 1.0), segs=4, rings=1))
    m.add("eye", ell("body", "sigil", (0, 33, 0), (2.0, 2.0, 2.0), segs=6, rings=2))
    base_debris(m, "curse", 8, seed=55)
    ring_sigil(m, 9)


BUILDERS = {"sk_bolt_0": bolt_0, "sk_bolt_1": bolt_1, "sk_bolt_2": bolt_2, "sk_bolt_3": bolt_3, "sk_bolt_4": bolt_4,
            "sk_hex_0": hex_0, "sk_hex_1": hex_1, "sk_hex_2": hex_2, "sk_hex_3": hex_3, "sk_hex_4": hex_4}


def build(kind):
    m = Mount("prop_" + kind, kind)
    m.bone("body", "root", (0, CY, 0) if "bolt" in kind else (0, 0, 0))
    BUILDERS[kind](m)
    m.pal = dict(mats=bmats(), glow_mats=sorted(BGLOW))
    m.detail = None
    m.hint = 256
    return m


def idle(kind, m):
    g = Gait("idle", 1.6)
    if kind in ("sk_bolt_1", "sk_bolt_3", "sk_bolt_4"):
        # 飛ぶ向きを軸に回る
        g.rot("body", None, None, W(0).s(90, 1, 0, "t"))
    elif "bolt" in kind:
        g.rot("body", None, None, W(0).s(20, 1, 0, "t"))
    else:
        g.rot("body", W(0).s(0.6, 1, 0, "t"))
    return g


def main():
    args = [a for a in sys.argv[1:] if not a.startswith("--")]
    kinds = args or list(BUILDERS)
    REVIEW.mkdir(parents=True, exist_ok=True)
    tiles = {}
    for kind in kinds:
        m = build(kind)
        size, atlas, glow = blp.paint_mount(m, m.pal, m.detail, m.hint)
        if "--no-install" not in sys.argv:
            blp.install(m, "prop_" + kind, size, atlas, glow, [idle(kind, m)], (4, 8))
        lo = np.min([f.verts.min(0) for f in m.F], axis=0)
        hi = np.max([f.verts.max(0) for f in m.F], axis=0)
        c = (lo + hi) / 2
        sc = 220 / max(1.0, float(np.max(hi - lo)))
        tiles[kind] = (view(m.F, atlas, glow, 210, 14, (300, 300), c, sc, ground=False), sum(len(f.verts) - 2 for f in m.F))
        print(kind, "tris", tiles[kind][1], flush=True)
    cv = Image.new("RGB", (5 * 310 + 10, 2 * 334 + 10), (16, 16, 22))
    d = ImageDraw.Draw(cv)
    for i, fam in enumerate(("bolt", "hex")):
        for j in range(5):
            k = f"sk_{fam}_{j}"
            if k not in tiles:
                continue
            x, y = 10 + j * 310, 10 + i * 334
            d.text((x, y), f"{fam} tier {j}  {tiles[k][1]} tris", font=voxel.font(15), fill=(150, 232, 255))
            cv.paste(tiles[k][0], (x, y + 24))
    cv.save(REVIEW / "basic.png")


if __name__ == "__main__":
    sys.stdout.reconfigure(encoding="utf-8")
    main()
