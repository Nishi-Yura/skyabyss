"""野の装備 帯 III (Lv 21〜30。嵐の高地 (風待)・灰の荒野 (灰炉)) の武器 18 本 (docs/design/51 の 4.3)。一品一案。
レア = 風待の山の品 (風鳴り・雪割り・雲読み・風切り・霜牙・峰の祈り)、エピック = 灰炉の品 (焼け砦・鞴・熾り蟲・灰ひきずり・灰の屑・灰撒き)、
ヒロイック = 名のある強敵と土地の芯 (灰被りの大蜥蜴の顎・灰の民の葬り・焼けた羽守の冠・火口・灰炉の毒・雷角の太鼓)。豪華さは巨神のヒロイックと同じ段。
"""
from __future__ import annotations

import math

from kit_wild import *  # noqa: F401,F403
from kit_wild import W, haft, bow_grip, bell, lash, flag, leaf_blade, claw_hand, _slab


def wild_gale_glaive(stage=0):
    """風鳴りの薙刀: 長い柄の先に反りの大刃。刃に風の抜ける穴が三つ開き、振ると鳴る。刃の根の口金に風待の織り帯が二筋なびく"""
    f = haft(-6.0, 15.4, r=0.75, mat="grip", wrap=(-2.6, 2.4))
    f += spike(BONE, "spine", (0, -5.9, 0), (0, -1, 0), 1.6, 0.75, sides=6, rings=1)
    f += prism("spine", (0, 15.0, 0), (0, 17.6, 0), 0.95, 0.8, sides=8, caps=(True, True))
    f += ringy("trim", 15.4, 1.05, 0.4)
    st = []
    for y, w, cx in ((17.4, 1.2, 0.2), (20.0, 1.7, 0.5), (23.0, 1.9, 0.4), (26.0, 1.7, -0.3), (28.4, 1.1, -1.2), (29.8, 0.0, -2.2)):
        t = 0.42 if w else 0
        st.append((y, -w * 0.5, w, t, t, cx))
    f += blade("main", st, colmats=["spine", "main", "edge", "edge", "main", "spine"])
    for y, cx in ((20.4, 0.9), (23.0, 0.9), (25.6, 0.5)):
        f += prism("dark", (cx, y, -0.46), (cx, y, 0.46), 0.42, 0.42, sides=6, caps=(True, True), hint=(1, 0, 0))
    for s, z in ((1, -0.6), (-1, 0.6)):
        f += sash([(0.6 * s, 16.6, z), (2.2 * s, 14.6, z * 1.4), (3.2 * s, 12.0, z * 1.8), (3.6 * s, 9.6, z * 2.0)], 0.6, mat="cloth")
    return f


def wild_fortress_greatsword(stage=0):
    """焼け砦の門剣: 焼けた砦の門の閂を鍛えた、厚く四角い大剣。刃に焦げた鋲が二列、鍔は門の蝶番、柄頭は門の環。刃の先は斜めに断ち切った形"""
    f = haft(-5.6, 3.2, r=0.95, mat="grip", wrap=(-4.8, 2.6))
    f += torus("spine", (0, -6.6, 0), 1.1, 0.3, 0.3, n=10, k=4, ax="z")
    f += block("guard", (0, 3.8, 0), (5.6, 1.4, 2.4))
    for s in (-1, 1):
        f += prism("guard", (s * 2.8, 3.8, -1.3), (s * 2.8, 3.8, 1.3), 0.8, 0.8, sides=8, caps=(True, True), hint=(1, 0, 0))
    # 厚い四角の刃 (先は斜めに断つ)
    f += block("main", (0, 14.0, 0), (3.6, 19.6, 1.4), taper=(1.0, 0.92))
    f += extrude("main", [(-1.8, 0), (1.8, 0), (1.8, 1.2), (-1.8, 3.0)], 1.3, c=(0, 23.8, 0))
    f += block("edge", (-1.85, 14.0, 0), (0.3, 19.2, 0.9))
    f += block("edge", (1.85, 14.0, 0), (0.3, 19.2, 0.9))
    for y in range(6, 23, 3):
        for x in (-0.9, 0.9):
            f += ell("spine", (x, y, -0.75), (0.32, 0.32, 0.18), segs=5, rings=1)
            f += ell("spine", (x, y, 0.75), (0.32, 0.32, 0.18), segs=5, rings=1)
    f += lump("soot", (0.6, 9.5, -0.72), (0.9, 1.6, 0.12), seed=6)
    return f


def wild_lavajaw_greatsword(stage=0):
    """熔け顎の大剣: 灰被りの大蜥蜴の顎の骨が刃の背になった大剣。黒い玄武の刃の背に牙が並び、歯の隙から熾の光が漏れる。鍔は頬の骨と角、柄頭は顎の関節"""
    f = haft(-5.8, 3.0, r=0.92, mat="grip", wrap=(-5.0, 2.4))
    f += ell("acc", (0, -6.4, 0), (1.3, 1.0, 1.1), segs=8, rings=2)
    f += spike(BONE, "acc", (0, -6.8, 0), (0, -1, 0), 1.4, 0.6, sides=5, rings=1)
    # 頬の骨と角の鍔
    f += block("acc", (0, 3.8, 0), (5.2, 1.8, 2.4), taper=(0.75, 0.8))
    for s in (-1, 1):
        f += spike(BONE, "acc", (s * 2.2, 4.2, 0), (s * 1.0, 0.6, 0.2), 2.6, 0.55, sides=5, rings=2, bend=(0, 0.6, 0))
    # 刃: 玄武の刃 (前の縁が刃、後ろに顎の骨)
    st = [(4.6, -2.0, 1.5, 0.6, 0.6), (10.0, -2.2, 1.7, 0.6, 0.6), (16.0, -2.1, 1.7, 0.55, 0.55), (21.0, -1.8, 1.4, 0.48, 0.48),
          (25.0, -1.0, 0.9, 0.36, 0.36), (27.6, 0, 0, 0, 0)]
    f += blade("main", st, colmats=["main", "main", "edge", "edge", "main", "main"])
    jaw = [(-2.3, 4.6, 0), (-2.8, 10.0, 0), (-2.7, 16.0, 0), (-2.3, 21.0, 0), (-1.4, 25.0, 0)]
    f += curve("acc", jaw, [0.9, 0.85, 0.8, 0.65, 0.4], [0.9, 0.85, 0.8, 0.65, 0.4], sides=6, caps=(True, True))
    for i in range(10):
        t = i / 9
        y = 5.6 + 19.0 * t
        x = -2.4 + 1.0 * t * t
        f += spike(BONE, "spine", (x - 0.6, y, 0), (-1, 0.35, 0), 1.9 - 0.7 * t, 0.5 - 0.15 * t, sides=4, rings=1)
    for z in (-0.62, 0.62):
        f += gbar([(-1.6, 6.0, z), (-1.9, 12.0, z), (-1.8, 18.0, z), (-1.3, 23.0, z * 0.9)], r=0.2)
    f += ell("gem", (0, 3.8, -1.25), (0.5, 0.5, 0.2), segs=6, rings=1)
    return f


def wild_snowsplitter_axe(stage=0):
    """雪割りの斧: 山小屋の番の幅広の斧。刃に氷が厚く付いて透け、刃の背は雪を割る楔。柄に毛皮、柄に雪の結晶の焼き印"""
    f = haft(-4.4, 12.6, r=0.85, mat="grip", wrap=(-3.6, 0.2), wmat="cloth")
    f += prism("spine", (0, 11.2, 0), (0, 14.4, 0), 1.15, 1.1, sides=8, caps=(True, True))
    f += extrude("main", [(0.8, 9.4), (3.6, 8.6), (5.6, 9.4), (6.2, 12.6), (5.6, 15.8), (3.6, 16.6), (0.8, 15.4)], 1.2, c=(0, 0, 0))
    # 氷の縁 (刃の外に透ける塊)
    for (x, y), r in (((6.2, 10.6), 0.8), ((6.6, 12.6), 0.9), ((6.2, 14.6), 0.8), ((5.2, 16.2), 0.6), ((5.2, 9.0), 0.6)):
        f += ell("gem", (x, y, 0), (r, r * 1.3, r * 0.9), segs=5, rings=2)
    f += block("main", (-2.2, 12.8, 0), (2.4, 2.2, 1.6), rot=rot_z(90), taper=(0.3, 0.8))
    for y in (4.0, 6.6):
        for a in (0, 60, 120):
            f += block("acc", (0, y, -0.86), (1.1, 0.16, 0.06), rot=rot_z(a))
    return f


def wild_bellows_hammer(stage=0):
    """灰炉の鞴鎚: 頭に鞴を組み込んだ鎚。鉄の打ち面の後ろに革の袋と木の板の鞴、口から熾が吹く管。柄は焦げた鉄木、鉄の輪"""
    f = haft(-4.4, 12.0, r=0.92, mat="grip", wrap=(-3.6, 0.8))
    f += ell("spine", (0, -4.6, 0), (1.1, 0.7, 1.1), segs=8, rings=1)
    f += block("main", (1.8, 13.6, 0), (3.8, 3.6, 3.2))
    f += prism("main", (3.7, 13.6, 0), (4.6, 13.6, 0), 2.0, 2.2, sides=8, caps=(True, True), hint=(0, 1, 0))
    f += ringy("trim", 11.8, 1.1, 0.5)
    # 鞴 (二枚の木の板と、あいだの革の袋)
    for dy in (-1.4, 1.4):
        f += block("acc", (-2.0, 13.6 + dy, 0), (3.6, 0.4, 2.8), taper=(0.6, 0.9))
    f += block("cloth", (-1.7, 13.6, 0), (3.0, 2.4, 2.4), taper=(0.5, 0.8))
    # 管と熾の口
    f += prism("spine", (-3.8, 13.6, 0), (-5.4, 13.6, 0), 0.55, 0.45, sides=6, caps=(True, True), hint=(0, 1, 0))
    f += ell("gem", (-5.5, 13.6, 0), (0.45, 0.45, 0.45), segs=6, rings=2)
    f += ell("gem", (1.8, 15.5, 0), (0.6, 0.2, 0.6), segs=6, rings=1)
    return f


def wild_ashrite_maul(stage=0):
    """灰の民の葬り鎚: 灰を納めた骨壺を鉄の帯で締めて頭にした大鎚。壺の蓋の隙から灰の光、壺の腹に灰の民の葬りの紋、両の打つ面は鉄の円盤。柄に葬りの札が下がる"""
    f = haft(-5.0, 13.2, r=1.0, mat="grip", wrap=(-4.2, 0.8))
    f += spike(BONE, "spine", (0, -4.8, 0), (0, -1, 0), 1.6, 0.95, sides=8, rings=1)
    # 骨壺 (横に寝かせた壺)
    prof = ((-3.6, 1.6), (-3.0, 2.3), (-1.2, 2.9), (0.6, 2.9), (2.4, 2.4), (3.4, 1.7))
    rings_ = [[V(x, 15.0 + r * math.cos(math.radians(30 * j)), r * math.sin(math.radians(30 * j))) for j in range(12)] for x, r in prof]
    f += loft(BONE, "main", rings_, caps=("main", "main"))
    for x in (-2.4, 1.6):
        f += prism("spine", (x - 0.3, 15.0, 0), (x + 0.3, 15.0, 0), 2.95, 2.95, sides=12, caps=(False, False), hint=(0, 1, 0))
    # 打つ面 (鉄の円盤)
    for s, x in ((-1, -3.8), (1, 3.6)):
        f += prism("edge", (x, 15.0, 0), (x + s * 0.8, 15.0, 0), 2.0, 2.1, sides=10, caps=(True, True), hint=(0, 1, 0))
    # 蓋と灰の光
    f += prism("acc", (-0.4, 17.9, 0), (-0.4, 18.5, 0), 1.4, 1.2, sides=10, caps=(True, True))
    f += prism("gem", (-0.4, 17.7, 0), (-0.4, 17.95, 0), 1.5, 1.5, sides=10, caps=(False, False))
    f += ell("acc", (-0.4, 18.8, 0), (0.5, 0.4, 0.5), segs=6, rings=1)
    # 葬りの紋 (腹の前)
    f += gbar([(-1.0, 13.6, -2.9), (-0.4, 14.6, -2.95), (0.2, 13.6, -2.9)], r=0.18)
    f += gbar([(-0.4, 14.6, -2.95), (-0.4, 16.0, -2.9)], r=0.18)
    # 葬りの札
    f += prism("cord", (0.9, 12.6, 0), (1.4, 10.8, 0), 0.07, 0.07, sides=3, caps=(False, False))
    f += block("cloth", (1.5, 9.8, 0), (1.2, 2.0, 0.15))
    return f


def wild_cloudreader_staff(stage=0):
    """雲読みの杖: 天気を読む輪の器 (三つの輪が組んだ渾天儀) を頭にした杖。真ん中に雲を閉じた水晶、輪に風向きの小さな矢羽"""
    f = haft(-4.0, 13.8, r=0.75, mat="grip", wrap=(-2.4, 2.0))
    f += prism("acc", (0, 13.6, 0), (0, 14.4, 0), 0.8, 1.2, sides=8, caps=(True, True))
    C = (0, 17.4, 0)
    f += torus("acc", C, 2.6, 0.18, 0.3, n=16, k=4, ax="y")
    f += torus("acc", C, 2.6, 0.18, 0.3, n=16, k=4, ax="z")
    ring3 = at(torus("spine", (0, 0, 0), 2.3, 0.15, 0.25, n=14, k=4, ax="z"), rot_y(90), C)
    f += ring3
    f += ell("gem", C, (1.2, 1.2, 1.2), segs=8, rings=3)
    f += ell("cloth", (C[0] + 0.4, C[1] + 0.1, C[2] - 0.5), (0.6, 0.4, 0.4), segs=6, rings=1)
    f += prism("acc", (0, 14.4, 0), (0, 15.0, 0), 0.4, 0.3, sides=6, caps=(True, True))
    f += curve("spine", [(0, 20.0, 0), (0, 21.2, 0)], 0.12, 0.12, sides=4)
    f += extrude("spine", [(0, 0), (0.9, 0.4), (0, 0.8)], 0.15, c=(0.0, 21.2, 0))
    f += extrude("spine", [(0, 0), (-0.7, 0.3), (-0.7, -0.3)], 0.15, c=(-0.1, 21.2, 0))
    return f


def wild_emberbug_staff(stage=0):
    """熾り蟲の籠杖: 曲がった鉄の杖の先から、熾り蟲を飼う鉄の籠が鎖で下がる。籠の中に光る蟲が三匹、籠の底に灰の受け皿"""
    f = haft(-4.0, 13.0, r=0.75, mat="grip", wrap=(-2.4, 2.0))
    hook = [(0, 12.8, 0), (0.2, 15.6, 0), (1.4, 17.6, 0), (3.2, 18.2, 0), (4.4, 17.2, 0)]
    f += curve("spine", hook, [0.6, 0.55, 0.5, 0.42, 0.3], [0.6, 0.55, 0.5, 0.42, 0.3], sides=6, caps=(False, True))
    f += chain((4.4, 17.0, 0), (4.4, 15.2, 0), n=3)
    # 籠
    C = (4.4, 13.4, 0)
    f += prism("spine", (4.4, 15.2, 0), (4.4, 15.6, 0), 0.4, 0.9, sides=8, caps=(True, True))
    for k in range(6):
        a = math.radians(60 * k)
        f += curve("spine", [(4.4 + 0.9 * math.cos(a), 15.2, 0.9 * math.sin(a)), (4.4 + 1.3 * math.cos(a), 13.4, 1.3 * math.sin(a)),
                             (4.4 + 0.9 * math.cos(a), 11.6, 0.9 * math.sin(a))], 0.1, 0.1, sides=3, caps=(True, True))
    f += prism("acc", (4.4, 11.6, 0), (4.4, 11.2, 0), 1.0, 0.7, sides=8, caps=(True, True))
    for dx, dy, dz in ((0.3, 0.6, 0.2), (-0.4, -0.2, -0.3), (0.2, -0.8, 0.4)):
        f += ell("gem", (C[0] + dx, C[1] + dy, C[2] + dz), (0.32, 0.2, 0.45), segs=6, rings=2)
    return f


def wild_warden_crown_staff(stage=0):
    """羽守の焼け冠杖: 焼けた羽守の羽根を冠にめぐらせた杖。冠の中に嵐の珠が浮き、羽根の先は焦げて光る。冠の下に雷の銅の輪が二重、柄に羽守の革の帯"""
    f = haft(-4.0, 13.6, r=0.8, mat="grip", wrap=(-2.6, 2.4))
    f += spike(BONE, "trim", (0, -3.8, 0), (0, -1, 0), 1.8, 0.8, sides=8, rings=1)
    f += prism("acc", (0, 13.4, 0), (0, 15.0, 0), 0.9, 1.5, sides=8, caps=(True, True))
    f += torus("acc", (0, 15.2, 0), 1.6, 0.22, 0.25, n=12, k=4, ax="y")
    f += torus("trim", (0, 15.7, 0), 1.75, 0.18, 0.2, n=12, k=4, ax="y")
    C = (0, 19.0, 0)
    f += ell("gem", C, (1.5, 1.5, 1.5), segs=10, rings=4)
    for k in range(9):
        a = math.radians(40 * k)
        base = (1.5 * math.cos(a), 15.6, 1.5 * math.sin(a))
        tip = (3.0 * math.cos(a), 22.4, 3.0 * math.sin(a))
        f += plume(base, tip, 0.7, th=0.22, big=True, mat="cloth", tipmat="soot", n=None, scorch=2)
    for k in range(3):
        a = math.radians(120 * k + 20)
        f += bolt([(1.2 * math.cos(a), 18.4, 1.2 * math.sin(a)), (2.0 * math.cos(a), 19.4, 2.0 * math.sin(a)),
                   (1.6 * math.cos(a + 0.3), 20.4, 1.6 * math.sin(a + 0.3))], r=0.14)
    return f


def wild_windcutter_bow(stage=0):
    """風切りの弓: 細く反り返った弓。腕の背に風を切る薄い鰭が並び、上下の先に織り帯の吹き流し"""
    f = bow_grip("grip", "wrap")
    up = bend(stage, [(5.0, CY + 2.4, 0), (4.2, CY + 5.4, 0), (2.8, CY + 8.0, 0), (1.8, CY + 10.2, 0), (2.4, CY + 11.6, 0)])
    half = []
    half += curve("main", up, [0.8, 0.72, 0.6, 0.48, 0.32], [0.9, 0.8, 0.66, 0.52, 0.34], sides=5, caps=(False, True), hint=(0, 0, 1))
    for i in range(0, 4):
        (x0, y0, _), (x1, y1, _) = up[i], up[i + 1]
        dx, dy = x1 - x0, y1 - y0
        ln = math.hypot(dx, dy) or 1
        nx, ny = dy / ln, -dx / ln
        px, py = x0 + dx * 0.5, y0 + dy * 0.5
        half += _slab("spine", [V(px + nx * 0.4 - dx * 0.25, py + ny * 0.4 - dy * 0.25, -0.08), V(px + nx * 0.4 + dx * 0.3, py + ny * 0.4 + dy * 0.3, -0.08),
                                V(px + nx * 1.5 + dx * 0.4, py + ny * 1.5 + dy * 0.4, -0.08)], 0.16)
    tx, ty, _ = up[-1]
    half += sash([(tx, ty, 0.2), (tx + 1.2, ty - 1.0, 0.4), (tx + 2.6, ty - 1.4, 0.6)], 0.45, mat="cloth")
    f += half + mirror_faces_y(half)
    lo = [(x, 2 * CY - y, z) for x, y, z in up]
    return f + string_and_arrow(stage, "rare", up, lo)


def wild_ashhook_bow(stage=0):
    """灰ひきずりの鉤弓: 黒い鉄木の弓。腕の外に灰ひきずりの鉤が三つずつ逆向きに並び、握りの下から鎖が垂れて鉤の錘"""
    f = bow_grip("grip", "wrap")
    up = bend(stage, [(5.0, CY + 2.4, 0), (4.6, CY + 5.4, 0), (3.2, CY + 8.2, 0), (1.2, CY + 10.2, 0), (-1.2, CY + 11.0, 0)])
    half = []
    half += curve("main", up, [0.95, 0.88, 0.76, 0.6, 0.4], [1.0, 0.92, 0.8, 0.62, 0.42], sides=6, caps=(False, True), hint=(0, 0, 1))
    for i in (1, 2, 3):
        x, y, _ = up[i]
        half += curve("spine", [(x + 0.6, y, 0), (x + 1.8, y - 0.6, 0), (x + 1.9, y - 1.6, 0), (x + 1.3, y - 1.9, 0)], [0.32, 0.28, 0.22, 0.06],
                      [0.32, 0.28, 0.22, 0.06], sides=5, caps=(True, True))
    tx, ty, _ = up[-1]
    half += ell("spine", (tx, ty, 0), (0.55, 0.55, 0.55), segs=6, rings=1)
    f += half + mirror_faces_y(half)
    f += chain((5.4, CY - 2.8, 0), (5.8, CY - 7.0, 0), n=4)
    f += curve("spine", [(5.8, CY - 7.0, 0), (6.6, CY - 7.8, 0), (6.2, CY - 8.8, 0), (5.4, CY - 8.4, 0)], [0.3, 0.26, 0.2, 0.06], [0.3, 0.26, 0.2, 0.06], sides=5)
    lo = [(x, 2 * CY - y, z) for x, y, z in up]
    return f + string_and_arrow(stage, "rare", up, lo)


def wild_crater_longbow(stage=0):
    """火口の大弓: 冷えた溶岩の黒い腕に熔けた筋が走る大きな弓。握りは火口の縁の形の玄武の環、腕の先は噴き上がる溶岩の滴の形"""
    f = bow_grip("grip", "wrap", r=1.1)
    f += torus("acc", (6.0, CY, 0), 1.6, 0.4, 0.7, n=10, k=4, ax="z")
    f += ell("gem", (6.0, CY, 0), (1.0, 1.0, 0.5), segs=8, rings=2)
    up = bend(stage, [(5.0, CY + 2.4, 0), (4.8, CY + 6.2, 0), (3.6, CY + 9.6, 0), (1.4, CY + 12.4, 0), (-1.6, CY + 13.8, 0)])
    half = []
    half += curve("main", up, [1.15, 1.05, 0.9, 0.7, 0.45], [1.2, 1.1, 0.95, 0.72, 0.46], sides=6, caps=(False, True), hint=(0, 0, 1))
    for i in range(1, 4):
        half += lump("main", up[i], (1.3, 1.0, 1.2), seed=i + 10)
    for z in (-0.95, 0.95):
        half += gbar([(x + 0.25, y, z * (1.0 - 0.15 * i)) for i, (x, y, _) in enumerate(up[:4])], r=0.2)
    tx, ty, _ = up[-1]
    half += ell("gem", (tx - 0.2, ty + 0.6, 0), (0.55, 0.9, 0.55), segs=6, rings=2)
    half += spike(BONE, "gem", (tx - 0.4, ty + 1.2, 0), (-0.3, 1, 0), 1.2, 0.35, sides=5, rings=1)
    f += half + mirror_faces_y(half)
    lo = [(x, 2 * CY - y, z) for x, y, z in up]
    return f + string_and_arrow(stage, "legend", up, lo)


def wild_frostfang_dagger(stage=0):
    """霜牙の短剣: 氷の牙のように透ける刃の短剣。鍔は六つ角の雪の結晶、柄は白い毛皮、柄頭に氷の粒"""
    f = haft(-3.0, 2.6, r=0.68, mat="grip", wrap=(-2.6, 2.1), wmat="cloth")
    f += ell("gem", (0, -3.3, 0), (0.6, 0.7, 0.6), segs=6, rings=2)
    for k in range(6):
        a = math.radians(60 * k)
        f += spike(BONE, "acc", (0, 3.0, 0), (math.cos(a), 0, math.sin(a)), 1.9, 0.3, sides=4, rings=1)
    f += ell("acc", (0, 3.0, 0), (0.7, 0.4, 0.7), segs=6, rings=1)
    st = [(3.2, -0.85, 0.85, 0.42, 0.42), (6.6, -0.95, 0.95, 0.4, 0.4), (10.0, -0.7, 0.7, 0.32, 0.32), (12.6, -0.3, 0.3, 0.16, 0.16), (13.8, 0, 0, 0, 0)]
    f += blade("main", st, colmats=["edge", "main", "edge", "edge", "main", "edge"])
    return f


def wild_cinder_claw(stage=0):
    """灰の屑の双爪: 灰の屑の固まった殻を拳にはめた双つの爪。玄武の殻の甲から、熾の割れ目の走る二本の太い爪が前へ反る。甲の後ろに燃え残りの棘"""
    f = claw_hand()
    f += ell("spine", (0, -1.3, 0), (0.95, 0.6, 0.95), segs=6, rings=1)
    f += lump("main", (0, 5.0, 0), (2.8, 1.6, 1.5), seed=21)
    f += lump("main", (0.4, 6.2, 0.3), (2.2, 1.0, 1.2), seed=22)
    for x, lean in ((-1.1, -0.4), (1.1, 0.4)):
        pts = [(x, 6.6, 0), (x + lean * 0.5, 9.6, -0.4), (x + lean, 12.6, -1.4), (x + lean * 0.9, 14.8, -2.8)]
        f += curve("main", pts, [1.0, 0.9, 0.6, 0.1], [0.75, 0.66, 0.45, 0.08], sides=6, caps=(False, True), hint=(1, 0, 0))
        f += gbar([(x + lean * 0.3, 8.2, -0.65), (x + lean * 0.7, 11.0, -1.1), (x + lean * 0.9, 13.2, -1.9)], r=0.15)
    for k in range(3):
        f += spike(BONE, "spine", (-1.2 + 1.2 * k, 5.4, 1.2), (0, 0.4, 1), 1.2, 0.3, sides=4, rings=1)
    return f


def wild_venom_fang(stage=0):
    """灰炉の毒牙: 反りの牙刀。柄に灰の毒の小瓶を鉄の籠で抱き、刃の溝を毒の光が伝う。鍔は灰の蛇が二匹絡んだ形、柄頭は蛇の頭"""
    f = haft(-3.6, 2.4, r=0.74, mat="grip", wrap=(-3.0, 1.8))
    # 蛇の頭の柄頭
    f += ell("acc", (0, -4.1, 0), (0.75, 0.9, 0.95), segs=6, rings=2)
    f += spike(BONE, "acc", (0, -4.1, -0.7), (0, -0.2, -1), 0.9, 0.5, sides=5, rings=1)
    for s in (-1, 1):
        f += ell("gem", (s * 0.42, -3.8, -0.75), (0.14, 0.14, 0.1), segs=4, rings=1)
    # 毒の小瓶 (柄の横に)
    f += prism("gem", (0.95, -1.8, 0), (0.95, 0.6, 0), 0.5, 0.5, sides=6, caps=(True, True))
    for y in (-1.9, 0.7):
        f += ringy("spine", y, 0.55, 0.25, cx=0.95)
    # 絡む蛇の鍔
    for s in (-1, 1):
        pts = [(0, 2.4, s * 0.4), (s * 1.4, 2.9, s * 0.6), (s * 2.2, 3.6, 0), (s * 1.6, 4.2, -s * 0.5), (s * 0.6, 4.0, -s * 0.6)]
        f += curve("acc", pts, [0.4, 0.38, 0.34, 0.28, 0.1], [0.4, 0.38, 0.34, 0.28, 0.1], sides=5, caps=(True, True))
    st = []
    for y, w, cx in ((3.6, 1.0, 0.0), (6.4, 1.2, -0.2), (9.4, 1.15, -0.8), (12.0, 0.85, -1.6), (13.8, 0.4, -2.4), (14.8, 0.0, -3.0)):
        t = 0.4 if w else 0
        st.append((y, -w * 0.55, w, t, t, cx))
    f += blade("main", st, colmats=["spine", "main", "edge", "edge", "main", "spine"])
    for z in (-0.42, 0.42):
        f += gbar([(-0.2, 4.6, z), (-0.3, 8.0, z), (-0.9, 11.0, z * 0.9), (-1.6, 12.6, z * 0.7)], r=0.15)
    return f


def wild_peak_totem(stage=0):
    """峰の祈り柱: 嵐の鳥を頂に彫った祈りの柱。四角い柱に雷の紋が刻まれ、頂の鳥は翼を広げて嘴を空へ。柱に布の房と鈴"""
    f = haft(-4.0, 12.6, r=0.8, mat="grip", wrap=(-2.4, 2.0))
    f += block("main", (0, 15.0, 0), (2.0, 5.0, 2.0), taper=(0.9, 0.9))
    for y in (13.4, 16.6):
        f += gbar([(-0.6, y, -1.02), (0.0, y + 0.8, -1.02), (0.6, y, -1.02)], r=0.14, mat="acc")
    f += block("acc", (0, 17.7, 0), (2.4, 0.5, 2.4))
    # 頂の鳥
    f += ell("main", (0, 19.2, 0), (1.0, 1.3, 1.1), segs=8, rings=2)
    f += ell("main", (0, 20.8, -0.3), (0.7, 0.75, 0.75), segs=6, rings=2)
    f += spike(BONE, "acc", (0, 21.1, -0.8), (0, 0.8, -0.6), 1.3, 0.3, sides=4, rings=1)
    for s in (-1, 1):
        for k in range(3):
            f += feather("main", (s * 0.8, 19.6 - 0.3 * k, 0.1), (s * (4.2 - 0.6 * k), 21.6 - 1.0 * k, 0.1), 0.65, th=0.3, big=True)
    for s in (-1, 1):
        f += prism("cord", (s * 1.0, 13.0, 0), (s * 1.2, 11.0, 0), 0.07, 0.07, sides=3, caps=(False, False))
        f += strand("cloth", (s * 1.2, 11.0, 0), 2.4, sway=0.3, r=0.35, n=3)
    f += bell((0, 12.6, -1.1), r=0.45, h=0.8, mat="acc")
    return f


def wild_ashsow_bell_staff(stage=0):
    """灰撒きの鈴杖: 灰の民が畑に灰を撒く篩 (ふるい) を頭にした杖。丸い木枠に網を張り、網の上に灰がこぼれる。篩の縁に鈴が四つ、柄に麦の束"""
    f = haft(-4.0, 14.0, r=0.78, mat="grip", wrap=(-2.4, 2.0))
    # 篩 (前へ傾いた円い枠)
    R = rot_x(-20)
    sieve = prism("main", (0, -0.5, 0), (0, 0.5, 0), 3.0, 3.0, sides=14, caps=(False, False))
    sieve += prism("cloth", (0, -0.15, 0), (0, 0.15, 0), 2.85, 2.85, sides=14, caps=(True, True))
    sieve += lump("soot", (0.4, 0.5, -0.4), (1.6, 0.4, 1.2), seed=31)
    for k in range(4):
        a = math.radians(45 + 90 * k)
        sieve += bell((3.1 * math.cos(a), -0.3, 3.1 * math.sin(a)), r=0.45, h=0.9, mat="acc")
    f += at(sieve, R, (0, 16.0, 0))
    for k in range(3):
        a = math.radians(120 * k)
        f += prism("spine", (0, 13.8, 0), (2.4 * math.cos(a), 15.6, 2.4 * math.sin(a)), 0.15, 0.15, sides=4, caps=(True, True))
    f += ell("gem", (0.4, 16.7, -0.5), (0.5, 0.25, 0.5), segs=6, rings=1)
    # 麦の束
    for dx in (-0.3, 0.3):
        f += prism("acc", (dx + 0.9, 9.0, 0.4), (dx + 1.2, 13.0, 0.4), 0.12, 0.12, sides=4, caps=(True, True))
        f += ell("acc", (dx + 1.25, 13.4, 0.4), (0.25, 0.6, 0.25), segs=5, rings=1)
    f += lash(10.0, 0.95, 2)
    return f


def wild_stormdrum_totem(stage=0):
    """嵐呼びの太鼓柱: 雷角の羊の皮を張った太鼓を頂に載せた柱。太鼓の胴に雷の紋が光り、両脇に巻き角の飾り、撥が二本交差して挿さる。柱に色布の帯と小さな鈴の房"""
    f = haft(-4.0, 13.2, r=0.85, mat="grip", wrap=(-2.6, 2.4))
    f += spike(BONE, "trim", (0, -3.8, 0), (0, -1, 0), 1.6, 0.85, sides=8, rings=1)
    f += prism("main", (0, 12.8, 0), (0, 14.4, 0), 1.0, 1.6, sides=8, caps=(True, True))
    # 太鼓 (横向き、面は前後)
    C = (0, 17.0, 0)
    f += prism("main", (0, 17.0, -1.6), (0, 17.0, 1.6), 2.6, 2.6, sides=12, caps=(False, False), hint=(1, 0, 0))
    for z in (-1.7, 1.7):
        f += prism("cloth", (0, 17.0, z - 0.1), (0, 17.0, z + 0.1), 2.7, 2.7, sides=12, caps=(True, True), hint=(1, 0, 0))
        f += torus("trim", (0, 17.0, z), 2.75, 0.18, 0.2, n=12, k=4, ax="z")
    for k in range(8):
        a = math.radians(45 * k)
        f += prism("cord", (2.6 * math.cos(a), 17.0 + 2.6 * math.sin(a), -1.6), (2.6 * math.cos(a + 0.4), 17.0 + 2.6 * math.sin(a + 0.4), 1.6), 0.08, 0.08,
                   sides=3, caps=(False, False))
    f += gbar([(-0.8, 18.4, -1.85), (0.3, 17.4, -1.85), (-0.3, 16.6, -1.85), (0.8, 15.6, -1.85)], r=0.18)
    # 巻き角
    for s in (-1, 1):
        pts = [(s * (2.6 + 1.2 * (1 - math.cos(math.radians(70 * i)))), 17.0 + 1.4 * math.sin(math.radians(70 * i)), 0) for i in range(6)]
        f += curve("acc", pts, [0.7 - 0.11 * i for i in range(6)], [0.7 - 0.11 * i for i in range(6)], sides=6, caps=(True, True))
    # 撥
    for s in (-1, 1):
        f += prism("spine", (s * 0.6, 18.4, 1.9), (-s * 1.8, 22.0, 2.0), 0.18, 0.15, sides=5, caps=(True, True))
        f += ell("cloth", (-s * 1.9, 22.2, 2.0), (0.4, 0.4, 0.4), segs=6, rings=1)
    f += sash([(0.9, 12.4, -0.4), (1.6, 10.4, -0.8), (1.8, 8.0, -1.1)], 0.6, mat="soot")
    for x in (-0.6, 0.6):
        f += bell((x, 12.2, -0.9), r=0.4, h=0.7, mat="acc")
    return f


WEAPONS = dict([
    W(wild_gale_glaive, "風鳴りの薙刀", "rare", "glaive", "frost",
      dict(main="steel", edge="steel", spine="iron", grip="darkwood", wrap="leather", cloth="bluecloth")),
    W(wild_fortress_greatsword, "焼け砦の門剣", "epic", "greatsword", "ember",
      dict(main="blacksteel", edge="iron", spine="rust", guard="iron", grip="ashwood", wrap="leather", soot="ash")),
    W(wild_lavajaw_greatsword, "熔け顎の大剣", "heroic", "greatsword", "ember",
      dict(main="basalt", edge="steel", acc="bone", spine="tusk", grip="blackleather", wrap="leather"),
      inlay=inlay_line(["main"], 6.0, 24.0, lambda y: -1.2 + 0.0 * y)),
    W(wild_snowsplitter_axe, "雪割りの斧", "rare", "axe", "frost",
      dict(main="steel", edge="steel", spine="iron", grip="darkwood", cloth="whitefur", acc="dark")),
    W(wild_bellows_hammer, "灰炉の鞴鎚", "epic", "hammer", "ember",
      dict(main="iron", edge="steel", spine="iron", acc="ashwood", cloth="leather", grip="ashwood", wrap="blackleather")),
    W(wild_ashrite_maul, "灰の民の葬り鎚", "heroic", "maul", "ember",
      dict(main="ash", edge="iron", spine="blacksteel", acc="bone", grip="ashwood", wrap="blackleather", cord="rope", cloth="paper"),
      inlay=inlay_line(["main"], 13.0, 17.0, lambda y: 0.0 * y)),
    W(wild_cloudreader_staff, "雲読みの杖", "rare", "staff", "frost",
      dict(grip="darkwood", wrap="leather", acc="brass", spine="copper", cloth="cloudwhite")),
    W(wild_emberbug_staff, "熾り蟲の籠杖", "epic", "staff", "ember",
      dict(grip="ashwood", wrap="leather", spine="blacksteel", acc="iron", chain="iron")),
    W(wild_warden_crown_staff, "羽守の焼け冠杖", "heroic", "staff", "spark",
      dict(grip="darkwood", wrap="leather", acc="copper", cloth="brownfur", soot="ash"),
      inlay=inlay_line(["acc"], 13.4, 15.0)),
    W(wild_windcutter_bow, "風切りの弓", "rare", "bow", "frost",
      dict(main="darkwood", spine="steel", cloth="bluecloth", grip="leather", wrap="leather")),
    W(wild_ashhook_bow, "灰ひきずりの鉤弓", "epic", "bow", "ember",
      dict(main="ashwood", spine="blacksteel", chain="iron", acc="iron", grip="blackleather", wrap="leather")),
    W(wild_crater_longbow, "火口の大弓", "heroic", "longbow", "ember",
      dict(main="basalt", acc="basalt", grip="blackleather", wrap="leather"),
      inlay=inlay_line(["main"], 99.0, 99.5)),
    W(wild_frostfang_dagger, "霜牙の短剣", "rare", "dagger", "frost",
      dict(main="ice", edge="ice", acc="silver", grip="leather", cloth="whitefur")),
    W(wild_cinder_claw, "灰の屑の双爪", "epic", "claw", "ember",
      dict(main="basalt", spine="ash", grip="blackleather", wrap="leather")),
    W(wild_venom_fang, "灰炉の毒牙", "heroic", "fang", "venom",
      dict(main="blacksteel", edge="steel", spine="iron", acc="ash", grip="blackleather", wrap="leather"),
      inlay=inlay_line(["main"], 5.0, 12.0, lambda y: -0.2 - 0.05 * (y - 5))),
    W(wild_peak_totem, "峰の祈り柱", "rare", "totem", "frost",
      dict(main="darkwood", acc="copper", grip="darkwood", wrap="leather", cord="rope", cloth="bluecloth")),
    W(wild_ashsow_bell_staff, "灰撒きの鈴杖", "epic", "bell", "ember",
      dict(main="ashwood", cloth="rope", soot="ash", acc="brass", spine="iron", grip="ashwood", wrap="leather", cord="rope")),
    W(wild_stormdrum_totem, "嵐呼びの太鼓柱", "heroic", "totem", "spark",
      dict(main="darkwood", cloth="whitefur", acc="tusk", spine="oak", cord="rope", soot="redcloth", grip="darkwood", wrap="leather"),
      inlay=inlay_line(["main"], 13.0, 14.4)),
])
