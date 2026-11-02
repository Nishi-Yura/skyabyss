"""野の装備 帯 II (Lv 11〜20。岩鯨の丘陵の奥・潮の浜 (汐守)・嵐の高地の入口 (風待)) の武器 18 本 (docs/design/51 の 4.2)。一品一案。
汐守の渡し守・船大工・潮見・海鳥・磯走り・浜の貝 (アンコモン)、砂潜り・潮招きの大蟹・漁り火・真珠層・宿借り・霧鐘 (レア)、
雷角の大羊・雷拾い・帯電毛玉・峰の雛・雷狼・風待の機織り (エピック)。
"""
from __future__ import annotations

import math

from kit_wild import *  # noqa: F401,F403
from kit_wild import W, haft, bow_grip, bell, lash, flag, leaf_blade, claw_hand, _slab


def wild_oar_blade(stage=0):
    """渡し守の櫂剣: 渡し舟の櫂を鍛え直した幅広の大剣。刃の先は櫂の水かきの丸み、刃の根は櫂の首の細さ。柄に縄の巻きと船の釘、柄頭は舫いの輪"""
    f = haft(-5.6, 3.4, r=0.85, mat="grip", wrap=(-4.8, 2.6), wmat="cord")
    f += torus("spine", (0, -6.6, 0), 1.0, 0.28, 0.28, n=10, k=4, ax="z")
    for y in (-3.2, 0.8):
        f += ell("spine", (0.95, y, 0), (0.25, 0.25, 0.25), segs=4, rings=1)
    # 鍔: 櫂受け (ぶ厚い板)
    f += block("guard", (0, 3.9, 0), (4.4, 1.2, 1.9), taper=(0.85, 0.85))
    # 刃: 根は細く、先へ広がって丸い水かき
    st = [(4.4, -0.9, 0.9, 0.55, 0.55), (8.0, -1.0, 1.0, 0.5, 0.5), (12.0, -1.6, 1.6, 0.48, 0.48), (17.0, -2.5, 2.5, 0.45, 0.45),
          (22.0, -2.7, 2.7, 0.42, 0.42), (25.0, -2.3, 2.3, 0.36, 0.36), (26.6, -1.3, 1.3, 0.28, 0.28), (27.3, 0, 0, 0, 0)]
    f += blade("main", st)
    # 水かきの真ん中の稜と、櫂の焼き印
    f += gbar([(0, 9.0, -0.5), (0, 24.0, -0.43)], r=0.16, mat="spine")
    f += gbar([(0, 9.0, 0.5), (0, 24.0, 0.43)], r=0.16, mat="spine")
    f += prism("acc", (0, 19.0, -0.5), (0, 19.0, -0.62), 0.9, 0.9, sides=8, caps=(True, True), hint=(1, 0, 0))
    return f


def wild_dune_sword(stage=0):
    """砂潜りの曲刀: 砂潜りの背びれを刃にした曲刀。刃は後ろへ反り、背に鰭の筋が櫛のように並ぶ。鍔は砂色の殻の半月、柄は鮫肌のような革"""
    f = haft(-3.8, 2.6, r=0.78, mat="grip", wrap=(-3.2, 2.0))
    f += spike(BONE, "guard", (0, -3.7, 0), (0.3, -1, 0), 1.6, 0.8, sides=6, rings=1, hint=(0, 0, 1))
    f += extrude("guard", [(-2.6, 0), (-1.8, 0.9), (0, 1.3), (1.8, 0.9), (2.6, 0), (0, -0.4)], 1.6, c=(0, 2.4, 0))
    st = []
    for y, w, cx in ((3.4, 1.0, 0.0), (6.0, 1.25, -0.3), (9.0, 1.45, -0.9), (12.0, 1.55, -1.7), (15.0, 1.45, -2.6), (17.6, 1.1, -3.4),
                     (19.4, 0.55, -3.9), (20.4, 0.0, -4.0)):
        t = 0.4 if w > 0 else 0
        st.append((y, -w * 0.6, w, t, t, cx))
    f += blade("main", st, colmats=["spine", "main", "edge", "edge", "main", "spine"])
    # 背の鰭の筋 (背 -x へ小さな棘)
    for y, cx in ((6.5, -0.3), (9.0, -0.9), (11.5, -1.6), (14.0, -2.3), (16.4, -3.0)):
        f += spike(BONE, "spine", (cx - 0.6, y, 0), (-1, 0.5, 0), 1.1, 0.3, sides=4, rings=1, hint=(0, 0, 1))
    return f


def wild_ramhorn_greatsword(stage=0):
    """羊角の大剣: 雷角の大羊の渦巻く二本の角が鍔になった大剣。刃の根に巻いた毛皮の帯、刃の真ん中に雷の通る溝 (青く光る)。柄頭は蹄の形"""
    f = haft(-5.4, 3.0, r=0.88, mat="grip", wrap=(-4.6, 2.4))
    f += block("spine", (0, -6.0, 0), (1.8, 1.2, 1.4), taper=(0.7, 0.8))
    f += extrude("spine", [(-0.9, 0), (0.9, 0), (0.5, -0.9), (-0.5, -0.9)], 1.5, c=(0, -6.6, 0))
    # 角の鍔 (左右に渦を巻く)
    for s in (-1, 1):
        pts = []
        for i in range(9):
            a = math.radians(-90 + 70 * i)
            R = 2.6 - 0.22 * i
            pts.append((s * (1.6 + R + R * math.cos(a) * 0.9), 3.6 + R * math.sin(a) * 0.8 + 0.2 * i, -0.2 * i * s * 0))
        rs = [0.95 - 0.09 * i for i in range(9)]
        f += curve("acc", pts, rs, rs, sides=6, caps=(True, True), hint=(0, 0, 1))
    f += block("guard", (0, 3.8, 0), (3.6, 1.6, 2.2), taper=(0.8, 0.8))
    # 毛皮の帯と刃
    f += prism("cloth", (0, 4.4, 0), (0, 6.4, 0), 1.7, 1.6, sides=8, caps=(True, True))
    st = [(6.0, -2.0, 2.0, 0.6, 0.6), (11.0, -2.15, 2.15, 0.58, 0.58), (17.0, -2.0, 2.0, 0.54, 0.54), (22.0, -1.7, 1.7, 0.48, 0.48),
          (25.5, -1.0, 1.0, 0.36, 0.36), (27.6, 0, 0, 0, 0)]
    f += blade("main", st)
    zz = [(0, 7.0), (0.5, 9.5), (-0.4, 12.0), (0.5, 14.5), (-0.4, 17.0), (0.4, 19.5), (0, 22.0)]
    for z in (-0.6, 0.6):
        f += gbar([(x, y, z) for x, y in zz], r=0.2)
    return f


def wild_shipwright_maul(stage=0):
    """船大工の木槌: 汐守の船大工の大きな木槌。樫の頭を銅の帯二本で締め、打つ面に船釘の跡、柄に麻の巻きと墨壺の糸"""
    f = haft(-4.4, 12.0, r=0.95, mat="grip", wrap=(-3.8, 1.0), wmat="cord")
    f += ell("grip", (0, -4.6, 0), (1.1, 0.7, 1.1), segs=8, rings=1)
    # 頭: 横向きの太い円柱
    f += prism("main", (-4.4, 13.4, 0), (4.4, 13.4, 0), 2.5, 2.5, sides=10, caps=(True, True), hint=(0, 1, 0))
    for x in (-2.8, 2.8):
        f += prism("acc", (x - 0.45, 13.4, 0), (x + 0.45, 13.4, 0), 2.7, 2.7, sides=10, caps=(False, False), hint=(0, 1, 0))
    # 打つ面の釘の跡
    for s in (-1, 1):
        for dy, dz in ((0.9, 0.9), (-0.9, -0.6), (0.2, -1.2), (-0.6, 1.1)):
            f += prism("spine", (s * 4.4, 13.4 + dy, dz), (s * 4.55, 13.4 + dy, dz), 0.25, 0.25, sides=4, caps=(True, True), hint=(0, 1, 0))
    # 墨壺の糸
    f += curve("cord", [(0.9, 11.0, 0), (1.4, 8.0, 0.4), (1.1, 5.0, 0.6)], 0.08, 0.08, sides=3)
    f += block("dark", (1.1, 4.6, 0.6), (0.7, 0.8, 0.7))
    return f


def wild_crab_hammer(stage=0):
    """大蟹の鋏鎚: 潮招きの大蟹の閉じた大鋏を頭にした鎚。赤い殻の鋏の歯がかみ合い、殻に藤壺。柄は流木、根に殻の輪"""
    f = haft(-4.4, 11.6, r=0.9, mat="grip", wrap=(-3.6, 1.0), wmat="cord")
    f += spike(BONE, "grip", (0, -4.2, 0), (0, -1, 0), 1.4, 0.85, sides=6, rings=1, hint=(0, 0, 1))
    f += prism("acc", (0, 10.8, 0), (0, 12.6, 0), 1.4, 1.2, sides=8, caps=(True, True))
    # 鋏の掌 (ふくらんだ殻)
    f += ell("main", (0.6, 13.6, 0), (3.0, 2.2, 1.9), segs=8, rings=3)
    # 不動指と可動指 (+x へ伸び、先が曲がって合わさる)
    f += curve("main", [(2.8, 14.4, 0), (5.0, 14.6, 0), (6.6, 13.8, 0)], [1.1, 0.8, 0.25], [1.1, 0.8, 0.25], sides=6, caps=(True, True), hint=(0, 0, 1))
    f += curve("main", [(2.8, 12.6, 0), (5.0, 12.2, 0), (6.5, 13.1, 0)], [1.1, 0.8, 0.25], [1.1, 0.8, 0.25], sides=6, caps=(True, True), hint=(0, 0, 1))
    for x in (3.6, 4.6, 5.5):
        f += spike(BONE, "edge", (x, 14.0, 0), (0, -1, 0), 0.6, 0.2, sides=4, rings=1, hint=(0, 0, 1))
        f += spike(BONE, "edge", (x, 12.9, 0), (0, 1, 0), 0.6, 0.2, sides=4, rings=1, hint=(0, 0, 1))
    # 後ろの打つ面 (掌の尻)
    f += prism("main", (-1.6, 13.6, 0), (-2.8, 13.6, 0), 1.6, 1.3, sides=8, caps=(True, True), hint=(0, 1, 0))
    f += barnacles((0.2, 15.4, -1.2), n=3, dirv=(0, 0.5, -1), mat="spine")
    return f


def wild_conductor_axe(stage=0):
    """雷拾いの避雷斧: 柄が避雷の竿になった斧。銅の巻き線が柄を上り、柄の頭に雷の受け針。三日月の刃の背に碍子が二つ、刃の縁を雷が走る"""
    f = haft(-4.4, 14.0, r=0.8, mat="grip", wrap=(-3.8, 0.6))
    # 銅の巻き線 (螺旋)
    pts = [(0.95 * math.cos(t * 0.9), 1.0 + t * 0.55, 0.95 * math.sin(t * 0.9)) for t in range(18)]
    f += curve("acc", pts, 0.15, 0.15, sides=3, caps=(True, True))
    # 受け針
    f += spike(BONE, "acc", (0, 13.8, 0), (0, 1, 0), 4.2, 0.6, sides=6, rings=2, hint=(0, 0, 1))
    for y in (14.6, 15.8):
        f += ringy("spine", y, 0.7, 0.3)
    # 三日月の刃 (+x)
    pts2 = arc(1.4, 11.2, 4.2, -60, 60, 7)
    f += extrude("main", [(0.4, 9.2), (2.0, 7.9), (4.2, 8.2), (5.4, 9.6), (5.8, 11.2), (5.4, 12.8), (4.2, 14.2), (2.0, 14.5), (0.4, 13.2)], 0.9, c=(0, 0, 0))
    f += curve("edge", [(x + 0.2, y, 0) for x, y, z in pts2], 0.2, 0.5, sides=4, caps=(True, True), hint=(0, 0, 1))
    f += gbar([(x + 0.1, y, -0.5) for x, y, z in pts2[1:-1]], r=0.14)
    # 碍子 (刃の背)
    for y in (10.0, 12.4):
        f += prism("cloth", (-0.6, y, 0), (-2.0, y, 0), 0.45, 0.45, sides=8, caps=(True, True), hint=(0, 1, 0))
        f += prism("cloth", (-1.0, y, 0), (-1.2, y, 0), 0.7, 0.7, sides=8, caps=(True, True), hint=(0, 1, 0))
    return f


def wild_tidewatch_staff(stage=0):
    """潮見の杖: 汐守の潮見の柱を杖にしたもの。白い柱に目盛りの刻みが並び、頭に硝子の浮き玉を縄の網で包み、海藻が垂れる"""
    f = haft(-4.0, 14.0, r=0.8, mat="grip", sides=6, wrap=(-2.4, 2.0), wmat="cord")
    for y in range(3, 14, 2):
        f += block("dark", (0, y, -0.78), (0.9 if y % 4 == 1 else 0.5, 0.2, 0.1))
    f += block("acc", (0, 12.0, -0.85), (1.6, 0.6, 0.12))
    # 浮き玉と網
    f += ell("gem", (0, 16.4, 0), (2.0, 2.0, 2.0), segs=10, rings=4)
    for k in range(4):
        a = math.radians(45 + 90 * k)
        f += curve("cord", [(0.5 * math.cos(a), 14.2, 0.5 * math.sin(a)), (2.1 * math.cos(a), 16.4, 2.1 * math.sin(a)), (0.5 * math.cos(a), 18.6, 0.5 * math.sin(a))],
                   0.12, 0.12, sides=3, caps=(True, True))
    f += torus("cord", (0, 16.4, 0), 2.05, 0.12, 0.12, n=12, k=3, ax="y")
    f += prism("cord", (0, 18.3, 0), (0, 19.2, 0), 0.4, 0.3, sides=6, caps=(True, True))
    for x, z, ln in ((1.4, 0.8, 3.6), (-1.2, -1.0, 3.0), (0.4, -1.5, 2.4)):
        f += strand("cloth", (x, 15.2, z), ln, sway=0.6, r=0.28, n=4)
    return f


def wild_lure_staff(stage=0):
    """漁り火の杖: 釣り竿の形にしなる杖。曲がった先から糸で小さな提灯 (霧の漁り火) が下がり、竿に網の切れ端が巻き付く"""
    f = haft(-4.0, 10.0, r=0.75, mat="grip", wrap=(-2.4, 2.0))
    rod = [(0, 9.8, 0), (0.2, 13.0, 0), (1.0, 16.0, 0), (2.4, 18.4, 0), (4.2, 19.6, 0)]
    f += curve("grip", rod, [0.7, 0.55, 0.45, 0.34, 0.2], [0.7, 0.55, 0.45, 0.34, 0.2], sides=6, caps=(False, True))
    for y in (10.0, 13.0):
        f += ringy("spine", y, 0.78 if y < 11 else 0.65, 0.3)
    # 糸と提灯
    f += prism("cord", (4.2, 19.5, 0), (4.2, 15.2, 0), 0.06, 0.06, sides=3, caps=(False, False))
    f += prism("spine", (4.2, 15.2, 0), (4.2, 14.8, 0), 0.6, 0.9, sides=6, caps=(True, True))
    f += prism("gem", (4.2, 14.8, 0), (4.2, 12.6, 0), 0.95, 0.95, sides=6, caps=(False, False))
    for k in range(6):
        a = math.radians(60 * k)
        f += prism("spine", (4.2 + 0.98 * math.cos(a), 14.8, 0.98 * math.sin(a)), (4.2 + 0.98 * math.cos(a), 12.6, 0.98 * math.sin(a)), 0.1, 0.1,
                   sides=3, caps=(False, False))
    f += prism("spine", (4.2, 12.6, 0), (4.2, 12.2, 0), 0.9, 0.5, sides=6, caps=(True, True))
    # 網の切れ端
    f += flag((-0.2, 8.4), 1.8, 3.4, z=-0.9, mat="cloth", tears=2, wave=0.3, th=0.12)
    return f


def wild_fluff_staff(stage=0):
    """綿雷の杖: 帯電毛玉の綿を銅の籠に閉じ込めた杖。籠の格子から逆立った毛が突き出し、毛のあいだに青白い火花。柄頭は避雷の銅の玉"""
    f = haft(-4.0, 13.2, r=0.75, mat="grip", wrap=(-2.4, 2.0))
    f += ell("acc", (0, -4.2, 0), (0.9, 0.9, 0.9), segs=8, rings=2)
    C = (0, 16.2, 0)
    f += ell("cloth", C, (2.1, 2.1, 2.1), segs=10, rings=4)
    f += ell("gem", C, (1.2, 1.2, 1.2), segs=6, rings=2)
    for k in range(6):
        a = math.radians(30 + 60 * k)
        f += curve("acc", [(0.6 * math.cos(a), 13.4, 0.6 * math.sin(a)), (2.4 * math.cos(a), 15.0, 2.4 * math.sin(a)),
                           (2.4 * math.cos(a), 17.4, 2.4 * math.sin(a)), (0.4 * math.cos(a), 19.0, 0.4 * math.sin(a))], 0.15, 0.15, sides=4, caps=(True, True))
    # 逆立つ毛 (格子のあいだから外へ)
    for k in range(10):
        a = math.radians(36 * k + 18)
        el = (k % 3 - 1) * 0.7
        d = (math.cos(a), el, math.sin(a))
        f += spike(BONE, "cloth", (C[0] + d[0] * 1.8, C[1] + el * 1.5, C[2] + d[2] * 1.8), d, 1.4, 0.32, sides=4, rings=1)
    for a in (40, 200):
        d = V(math.cos(math.radians(a)), 0.4, math.sin(math.radians(a)))
        f += spike(BONE, "gem", tuple(V(*C) + d * 2.2), tuple(d), 1.1, 0.14, sides=3, rings=1)
    f += spike(BONE, "acc", (0, 18.8, 0), (0, 1, 0), 2.0, 0.4, sides=6, rings=1)
    return f


def wild_gull_bow(stage=0):
    """海鳥の弓: 海鳥の広げた翼の形の弓。白い翼の腕に灰の風切羽が重なり、先は黒い羽の先。握りは帆の縄巻き、真ん中に小さな嘴の留め"""
    f = bow_grip("cord", "cord")
    f += spike(BONE, "acc", (6.1, CY, 0), (1, 0, 0), 1.6, 0.4, sides=4, rings=1, hint=(0, 0, 1))
    up = bend(stage, [(5.0, CY + 2.4, 0), (5.4, CY + 4.6, 0), (4.6, CY + 7.0, 0), (2.6, CY + 9.2, 0), (-0.2, CY + 10.4, 0)])
    half = []
    half += curve("main", up, [0.85, 0.82, 0.7, 0.52, 0.32], [1.0, 0.95, 0.8, 0.58, 0.36], sides=5, caps=(False, True), hint=(0, 0, 1))
    # 風切羽: 腕の外 (+x) に、腕の先の向きへ寝かせて重ねる (白、先の一枚だけ黒)
    for i in range(0, 4):
        (x0, y0, _), (x1, y1, _) = up[i], up[i + 1]
        dx, dy = x1 - x0, y1 - y0
        ln = math.hypot(dx, dy) or 1
        ux, uy = dx / ln, dy / ln
        nx, ny = uy, -ux
        mat = "soot" if i == 3 else "cloth"
        half += plume((x0 + nx * 0.4, y0 + ny * 0.4, 0.1), (x0 + nx * 1.6 + ux * 3.0, y0 + ny * 1.6 + uy * 3.0, 0.1), 0.7, th=0.2, big=True,
                      mat=mat, tipmat="soot", scorch=1 if i >= 2 else 0)
    tx, ty, _ = up[-1]
    half += feather("soot", (tx + 0.4, ty, 0), (tx - 1.6, ty + 0.6, 0), 0.5, th=0.2, big=False)
    f += half + mirror_faces_y(half)
    lo = [(x, 2 * CY - y, z) for x, y, z in up]
    return f + string_and_arrow(stage, "rare", up, lo)


def wild_nacre_bow(stage=0):
    """真珠層の弓: 貝の真珠層を薄く重ねた虹色の弓。腕は板を重ねた段になり、先に白い巻き貝の弭。握りの外に真珠"""
    f = bow_grip("grip", "wrap")
    f += ell("gem", (6.2, CY, 0), (0.7, 0.7, 0.7), segs=8, rings=3)
    f += torus("acc", (6.2, CY, 0), 0.8, 0.14, 0.3, n=10, k=4, ax="z")
    up = bend(stage, [(5.0, CY + 2.4, 0), (4.5, CY + 5.2, 0), (3.2, CY + 7.9, 0), (1.2, CY + 9.9, 0), (-1.2, CY + 10.8, 0)])
    half = []
    half += curve("main", up, [0.9, 0.85, 0.74, 0.58, 0.4], [1.0, 0.95, 0.82, 0.64, 0.42], sides=6, caps=(False, True), hint=(0, 0, 1))
    # 重ねた層 (腕の外に短い板を段に)
    for i in range(len(up) - 1):
        (x0, y0, _), (x1, y1, _) = up[i], up[i + 1]
        half += curve("main", [(x0 + 0.65, y0 + 0.3, 0), (x1 + 0.55, y1 - 0.3, 0)], [0.32, 0.22], [0.75, 0.6], sides=4, caps=(True, True), hint=(0, 0, 1))
    # 巻き貝の弭
    tx, ty, _ = up[-1]
    spiral = [(tx + 0.6 * math.cos(t) * (1 - t / 9), ty + 0.3 * t, 0.6 * math.sin(t) * (1 - t / 9)) for t in [0.0, 1.2, 2.4, 3.6, 4.8, 6.0]]
    half += curve("acc", spiral, [0.6, 0.55, 0.45, 0.35, 0.25, 0.08], [0.6, 0.55, 0.45, 0.35, 0.25, 0.08], sides=6, caps=(True, True))
    f += half + mirror_faces_y(half)
    lo = [(x, 2 * CY - y, z) for x, y, z in up]
    return f + string_and_arrow(stage, "rare", up, lo)


def wild_eaglet_longbow(stage=0):
    """雛鷲の長弓: 峰の雛の茶の羽を腕に沿って生やした長い弓。腕の芯は黒い角、先は鉤の爪。握りの外に鷲の爪が握りを掴む留め"""
    f = bow_grip("wrap", "grip", r=1.05)
    for dy in (-1.0, 0.0, 1.0):
        f += curve("spine", [(6.1, CY + dy, -0.4), (6.9, CY + dy - 0.3, -0.2), (6.9, CY + dy - 0.9, 0.3)], [0.3, 0.22, 0.06], [0.3, 0.22, 0.06], sides=4)
    up = bend(stage, [(5.0, CY + 2.4, 0), (4.7, CY + 6.0, 0), (3.6, CY + 9.4, 0), (1.6, CY + 12.2, 0), (-1.0, CY + 13.6, 0)])
    half = []
    half += curve("main", up, [0.95, 0.88, 0.76, 0.58, 0.36], [1.05, 0.96, 0.82, 0.62, 0.38], sides=5, caps=(False, True), hint=(0, 0, 1))
    for i in range(1, 4):
        (x0, y0, _), (x1, y1, _) = up[i], up[i + 1]
        for t in (0.15, 0.55, 0.9):
            px, py = x0 + (x1 - x0) * t, y0 + (y1 - y0) * t
            half += plume((px + 0.4, py, 0.15), (px + 2.6, py - 1.6, 0.15), 0.55, th=0.22, mat="cloth", tipmat="soot", scorch=1)
    tx, ty, _ = up[-1]
    half += curve("spine", [(tx, ty, 0), (tx - 0.9, ty + 0.4, 0), (tx - 1.4, ty - 0.3, 0)], [0.4, 0.3, 0.06], [0.4, 0.3, 0.06], sides=4)
    f += half + mirror_faces_y(half)
    lo = [(x, 2 * CY - y, z) for x, y, z in up]
    return f + string_and_arrow(stage, "rare", up, lo)


def wild_shore_dagger(stage=0):
    """磯走りの針: 磯走りの細い脚を研いだ針の短剣。関節ごとに節のある赤い殻の刃、鍔は甲羅の小片、柄に浜の紐"""
    f = haft(-3.0, 2.6, r=0.66, mat="grip", wrap=(-2.6, 2.1), wmat="cord")
    f += ell("main", (0, -3.2, 0), (0.75, 0.6, 0.75), segs=6, rings=1)
    f += ell("guard", (0, 3.0, 0), (1.9, 0.6, 1.2), segs=8, rings=2)
    segs = [(3.4, 6.6, 0.62), (6.6, 9.8, 0.5), (9.8, 13.6, 0.36)]
    for i, (y0, y1, r) in enumerate(segs):
        f += prism("main", (0, y0, 0), (0, y1, 0), r, r * 0.8, sides=6, caps=(True, True))
        f += ell("main", (0, y1, 0), (r * 0.95, 0.4, r * 0.95), segs=6, rings=1)
    f += spike(BONE, "edge", (0, 13.6, 0), (0, 1, 0), 2.4, 0.3, sides=6, rings=2)
    for y in (5.0, 8.2, 11.6):
        f += spike(BONE, "spine", (0.4, y, 0), (1, 0.6, 0), 0.6, 0.16, sides=3, rings=1)
    return f


def wild_hermit_knife(stage=0):
    """宿借りの隠し刃: 宿借りの巻き貝が柄で、口から刃が覗く隠し刃。口の縁に蟹の鋏の形の鍔、刃は細く反り、貝の上に小さな藤壺"""
    f = []
    # 巻き貝の柄 (下へ尖る)
    spiral = []
    for i in range(9):
        t = i / 8
        spiral.append((0.35 * math.sin(i * 1.4) * (1 - t), 3.0 - 6.6 * t, 0.35 * math.cos(i * 1.4) * (1 - t)))
    f += curve("grip", spiral, [1.35 - 1.2 * i / 8 for i in range(9)], [1.35 - 1.2 * i / 8 for i in range(9)], sides=7, caps=(True, True))
    for i in range(1, 8, 2):
        x, y, z = spiral[i]
        f += ringy("wrap", y, 1.38 - 1.2 * i / 8, 0.3, cx=x, cz=z)
    f += ell("acc", (0, 3.0, 0), (1.6, 0.6, 1.6), segs=8, rings=1)
    # 鋏の鍔
    for s in (-1, 1):
        f += curve("guard", [(s * 1.2, 3.2, 0), (s * 2.4, 4.0, 0), (s * 2.3, 5.0, 0)], [0.55, 0.42, 0.12], [0.45, 0.35, 0.1], sides=5, caps=(True, True))
    st = [(3.4, -0.6, 0.7, 0.32, 0.32), (6.0, -0.65, 0.78, 0.3, 0.3, -0.2), (9.4, -0.5, 0.7, 0.28, 0.28, -0.6), (12.0, -0.25, 0.35, 0.18, 0.18, -1.0),
          (13.2, 0, 0, 0, 0, -1.2)]
    f += blade("main", st)
    f += barnacles((0.9, -0.6, -0.9), n=2, spread=0.6, dirv=(0.6, 0, -1), mat="spine")
    return f


def wild_stormwolf_fang(stage=0):
    """雷狼の牙: 雷狼の大きな犬歯を刃にした反りの牙刀。牙の根を帯電した灰の毛皮で包み、鍔に小さな牙の列、牙の内の筋を青い雷が走る"""
    f = haft(-3.4, 2.4, r=0.74, mat="grip", wrap=(-2.8, 1.8))
    f += spike(BONE, "spine", (0, -3.3, 0), (0, -1, 0), 1.2, 0.7, sides=6, rings=1)
    f += ell("cloth", (0, 3.0, 0), (1.7, 1.2, 1.5), segs=8, rings=2)
    for k in range(5):
        a = math.radians(-60 + 30 * k)
        f += spike(BONE, "spine", (1.4 * math.sin(a), 3.4, -1.0 * math.cos(a)), (0, 1, -0.2), 0.9, 0.2, sides=4, rings=1)
    # 牙 (太い根から反って尖る。断面は丸みのある楕円)
    pts = [(0, 3.4, 0), (-0.1, 6.0, 0), (-0.6, 8.6, 0), (-1.5, 11.0, 0), (-2.8, 12.8, 0), (-4.2, 13.6, 0)]
    f += curve("main", pts, [1.3, 1.25, 1.1, 0.85, 0.5, 0.06], [0.7, 0.68, 0.6, 0.46, 0.28, 0.05], sides=6, caps=(False, True), hint=(0, 0, 1))
    f += curve("edge", [(0.95, 5.0, 0), (0.75, 8.2, 0), (-0.2, 11.0, 0), (-1.8, 12.8, 0)], [0.18, 0.18, 0.15, 0.06], [0.35, 0.34, 0.28, 0.08],
               sides=4, caps=(True, True), hint=(0, 0, 1))
    for z in (-0.62, 0.62):
        f += gbar([(-0.1, 5.0, z), (-0.4, 7.6, z * 0.98), (-1.0, 9.8, z * 0.9)], r=0.13)
    return f


def wild_shell_bell_staff(stage=0):
    """浜の貝鈴杖: 流木の杖に貝殻の鈴を連ねたもの。頭は白い大きな巻き貝、その口から紐で貝殻が鳴るように下がる。潮の紐が杖に巻く"""
    f = haft(-4.0, 13.6, r=0.8, mat="grip", wrap=(-2.4, 2.0), wmat="cord")
    f += curve("grip", [(0, 13.4, 0), (0.6, 14.6, 0), (0.2, 15.8, 0)], 0.7, 0.6, sides=6)
    # 巻き貝 (横向き、口は前)
    spiral = []
    for i in range(10):
        t = i / 9
        spiral.append((1.6 * math.cos(i * 0.9) * (1 - 0.7 * t), 17.4 + 1.6 * math.sin(i * 0.9) * (1 - 0.7 * t) + 0.3 * t, -0.2 + 1.6 * t))
    f += curve("main", spiral, [1.4 - 1.1 * i / 9 for i in range(10)], [1.4 - 1.1 * i / 9 for i in range(10)], sides=7, caps=(True, True))
    f += ell("acc", (0.9, 17.0, -0.8), (1.0, 1.1, 0.4), segs=8, rings=2)
    # 下がる貝殻 (扇貝)
    for x, y, ln in ((-1.8, 16.4, 2.6), (1.9, 16.0, 3.2), (0.2, 15.6, 3.8)):
        f += prism("cord", (x, y, 0), (x, y - ln, 0), 0.07, 0.07, sides=3, caps=(False, False))
        f += scallop((x, y - ln - 0.2, 0), 5, 1.4, 70, 0.35, th=0.3, mat="main")
    return f


def wild_fogbell_bell_staff(stage=0):
    """霧鐘の鈴杖: 杖の頭が小さな灯台の形 (白と赤の縞の塔)。塔の頂の灯室に、霧鐘を小さく写した青銅の鐘が吊られ、鐘の中に霧の灯が光る"""
    f = haft(-4.0, 12.6, r=0.8, mat="grip", wrap=(-2.4, 2.0))
    # 塔 (下が太い八角)
    for i, (y0, y1, mat) in enumerate(((12.4, 14.0, "main"), (14.0, 15.6, "cloth"), (15.6, 17.0, "main"))):
        r0 = 1.5 - 0.18 * i
        f += prism(mat, (0, y0, 0), (0, y1, 0), r0, r0 - 0.18, sides=8, caps=(True, True))
    f += prism("spine", (0, 17.0, 0), (0, 17.4, 0), 1.6, 1.6, sides=8, caps=(True, True))
    # 灯室の柱と屋根
    for k in range(4):
        a = math.radians(45 + 90 * k)
        f += prism("spine", (1.1 * math.cos(a), 17.4, 1.1 * math.sin(a)), (1.1 * math.cos(a), 20.4, 1.1 * math.sin(a)), 0.16, 0.16, sides=4, caps=(True, True))
    f += prism("cloth", (0, 20.4, 0), (0, 21.4, 0), 1.5, 0.4, sides=8, caps=(True, True))
    f += spike(BONE, "spine", (0, 21.3, 0), (0, 1, 0), 0.9, 0.18, sides=4, rings=1)
    f += bell((0, 20.2, 0), r=0.85, h=1.8, mat="acc", mouth="gem")
    return f


def wild_loom_bell_staff(stage=0):
    """機織りの風鈴杖: 風待の機織りの枠を頭にした杖。四角い木の枠に経糸が張られ、真ん中に梭 (ひ) が通る。枠の下の横木に風鈴が三つ下がり、色糸の房が流れる"""
    f = haft(-4.0, 13.4, r=0.78, mat="grip", wrap=(-2.4, 2.0))
    # 枠 (前を向く四角)
    for x in (-2.6, 2.6):
        f += prism("main", (x, 13.0, 0), (x, 20.6, 0), 0.4, 0.4, sides=6, caps=(True, True))
    for y in (13.4, 20.2):
        f += prism("main", (-3.0, y, 0), (3.0, y, 0), 0.42, 0.42, sides=6, caps=(True, True), hint=(0, 1, 0))
    f += ell("main", (0, 20.9, 0), (0.6, 0.6, 0.6), segs=6, rings=1)
    # 経糸
    for x in (-1.8, -0.9, 0.0, 0.9, 1.8):
        f += prism("cloth", (x, 13.6, 0), (x, 20.0, 0), 0.06, 0.06, sides=3, caps=(False, False))
    # 織った布の段と梭
    f += _slab("soot", [V(-2.2, 15.4, -0.1), V(2.2, 15.4, -0.1), V(2.2, 13.8, -0.1), V(-2.2, 13.8, -0.1)], 0.2)
    f += block("acc", (0, 16.6, -0.1), (3.6, 0.6, 0.6), taper=(0.6, 0.8))
    # 風鈴
    for x in (-2.0, 0.0, 2.0):
        f += prism("cord", (x, 13.2, 0), (x, 11.6, 0), 0.06, 0.06, sides=3, caps=(False, False))
        f += bell((x, 11.6, 0), r=0.55, h=1.0, mat="gem", mouth="spine")
        f += _slab("cloth", [V(x - 0.25, 10.4, -0.05), V(x + 0.25, 10.4, -0.05), V(x + 0.25, 9.0, -0.05), V(x - 0.25, 9.0, -0.05)], 0.1)
    f += sash([(2.8, 19.0, 0.4), (4.0, 17.4, 0.6), (4.6, 15.0, 0.8)], 0.7, mat="soot")
    return f


WEAPONS = dict([
    W(wild_oar_blade, "渡し守の櫂剣", "uncommon", "greatsword", "sea",
      dict(main="driftwood", edge="steel", spine="iron", guard="darkwood", grip="leather", cord="rope", acc="bronze")),
    W(wild_dune_sword, "砂潜りの曲刀", "rare", "sword", "sea",
      dict(main="steel", edge="steel", spine="shell", guard="shell", grip="leather", wrap="blackleather")),
    W(wild_ramhorn_greatsword, "羊角の大剣", "epic", "greatsword", "spark",
      dict(main="steel", edge="steel", spine="iron", guard="iron", acc="tusk", cloth="whitefur", grip="darkwood", wrap="leather"),
      inlay=None),
    W(wild_shipwright_maul, "船大工の木槌", "uncommon", "maul", "sea",
      dict(main="oak", grip="oak", acc="copper", spine="iron", cord="rope")),
    W(wild_crab_hammer, "大蟹の鋏鎚", "rare", "hammer", "sea",
      dict(main="crabshell", edge="bone", spine="shell", acc="crabshell", grip="driftwood", cord="rope")),
    W(wild_conductor_axe, "雷拾いの避雷斧", "epic", "axe", "spark",
      dict(main="steel", edge="steel", acc="copper", spine="iron", grip="darkwood", wrap="leather", cloth="whitecloth")),
    W(wild_tidewatch_staff, "潮見の杖", "uncommon", "staff", "sea",
      dict(grip="driftwood", cord="rope", acc="redcloth", cloth="greencloth", dark="dark")),
    W(wild_lure_staff, "漁り火の杖", "rare", "staff", "lamp",
      dict(grip="bamboo", spine="iron", cord="rope", cloth="rope", wrap="leather")),
    W(wild_fluff_staff, "綿雷の杖", "epic", "staff", "spark",
      dict(grip="darkwood", wrap="leather", acc="copper", cloth="whitefur")),
    W(wild_gull_bow, "海鳥の弓", "uncommon", "bow", "sea",
      dict(main="driftwood", cloth="whitecloth", soot="blackleather", cord="rope", acc="brass")),
    W(wild_nacre_bow, "真珠層の弓", "rare", "bow", "sea",
      dict(main="nacre", acc="shell", grip="leather", wrap="leather")),
    W(wild_eaglet_longbow, "雛鷲の長弓", "epic", "longbow", "spark",
      dict(main="darkwood", spine="tusk", cloth="straw", soot="darkwood", grip="leather", wrap="leather")),
    W(wild_shore_dagger, "磯走りの針", "uncommon", "dagger", "sea",
      dict(main="crabshell", edge="bone", guard="shell", spine="crabshell", grip="leather", cord="rope")),
    W(wild_hermit_knife, "宿借りの隠し刃", "rare", "dagger", "sea",
      dict(main="steel", edge="steel", grip="shell", wrap="rockshell", acc="nacre", guard="crabshell", spine="stone")),
    W(wild_stormwolf_fang, "雷狼の牙", "epic", "fang", "spark",
      dict(main="tusk", edge="bone", spine="bone", cloth="fur", grip="blackleather", wrap="leather")),
    W(wild_shell_bell_staff, "浜の貝鈴杖", "uncommon", "bell", "sea",
      dict(grip="driftwood", cord="rope", main="shell", acc="nacre")),
    W(wild_fogbell_bell_staff, "霧鐘の鈴杖", "rare", "bell", "lamp",
      dict(grip="darkwood", wrap="leather", main="whitecloth", cloth="redcloth", spine="iron", acc="bronze")),
    W(wild_loom_bell_staff, "機織りの風鈴杖", "epic", "bell", "frost",
      dict(grip="darkwood", wrap="leather", main="oak", cloth="whitecloth", soot="bluecloth", acc="darkwood", cord="rope", spine="brass")),
])
