"""元からの品の作り直し (2026-10-27 の声「元からあったやつがヒロイックになって変に見える」「モデルのクオリティ統一されてる?」)。
はじめのころの巨神の武器 (大きな平たい棘の面が目立つ) を、一品一案の段 (o_whale / o_tide / o_storm / o_ember) にそろえる。名前と効き目はそのまま、形の構想を描き直す。
塗りは品ごとの巨神 (glow = whale / tide / thunder / ember。../paint.py)。ヒロイックとアーティファクトは形の段 legend (金の縁と淵)、エピックは rare。
アーティファクト (深淵の 3 本) だけ浮く淵の欠片 (legend_finish) を付ける。ヒロイックは付けない。
寸法は元の品に合わせる (握りの位置・長さ。item モデルの持ち方をそのまま使うため)。
"""
from __future__ import annotations

import math

from kit_one import *  # noqa: F401,F403
from kit_one import legend_finish, ell, lump, bone, vertebra, strand, ringy, gbar, gem, arc, bend, mirror_faces_y, string_and_arrow, inlay_line, CY
from o_tide import kelp_grip, shell_fan
from o_storm import coil, zig, steel_haft, shide
from o_ember import wood_haft


# ================================================================== 岩鯨
def waterway_fang(stage=0):
    """水路の顎牙: 岩鯨の下顎の先を切り出した短剣。反った顎の骨の内側に歯が一列に並び、顎に沿って水の筋が光る。鍔は鰓のような骨の板、柄は石に苔の縄と金の輪"""
    f = []
    f += prism("grip", (0, -0.9, 0), (0, 4.6, 0), 0.85, 0.85, sides=6, caps=(True, False))
    f += prism("wrap", (0, -0.4, 0), (0, 3.8, 0), 0.98, 0.98, sides=6, caps=(False, False))
    for y in (0.2, 1.4, 2.6):
        f += ringy("cord", y, 1.06, 0.3)
    f += ringy("trim", -0.6, 1.1, 0.4)
    f += ringy("trim", 4.2, 1.1, 0.4)
    f += ell("guard", (0, -1.4, 0), (1.0, 0.8, 1.0), segs=6, rings=2)
    # 鍔: 鰓の骨の板 (左右へ三枚ずつ、後ろへ反る)
    f += lump("spine", (0, 5.2, 0), (2.0, 0.9, 1.2), seed=11, segs=7, rings=2)
    for s in (1, -1):
        for k, (L, a) in enumerate(((3.4, 18), (2.8, 2), (2.2, -14))):
            r = math.radians(a)
            f += feather("edge", (s * 1.2, 5.0 - 0.5 * k, 0.2 * k), (s * (1.2 + L * math.cos(r)), 5.0 - 0.5 * k + L * math.sin(r), 0.2 * k), 0.55, 0.35, big=False)
    # 刃: 反った顎の骨 (+x へ反り、根は太い)
    jaw = [(0, 5.6, 0), (0.5, 8.6, 0), (1.4, 11.6, 0), (2.6, 14.2, 0), (3.6, 15.8, 0)]
    f += curve("main", jaw, [0.8, 0.75, 0.65, 0.5, 0.2], [1.7, 1.55, 1.3, 0.95, 0.35], sides=6, caps=(False, True), hint=(0, 0, 1))
    # 歯 (内側 -x に一列、先ほど小さい)
    for i, (x, y, _) in enumerate(jaw[:4]):
        for dy in (0.0, 1.5):
            h = 1.25 - 0.22 * i - 0.1 * (dy > 0)
            f += spike(BONE, "edge", (x - 1.1 + 0.15 * i, y + dy + 0.3, 0), (-0.6, 0.8, 0), h, 0.36, sides=4, rings=1, hint=(0, 0, 1))
    # 水の筋 (顎の外側に沿う光) と藤壺
    f += gbar([(0.9, 6.4, -0.72), (1.3, 9.0, -0.7), (2.2, 12.0, -0.6), (3.0, 14.4, -0.45)], r=0.18)
    f += barnacles((1.7, 7.6, 0.75), n=3, spread=0.4, r=0.24, h=0.4, dirv=(0, 0, 1), mat="guard")
    f += strand("acc", (-1.0, 5.0, 0.8), 2.2, 0.35, r=0.26)
    return f


def captain_pike(stage=0):
    """番兵長の鉾: 長い石の柄の先に、岩鯨の肩甲骨を削った幅広の穂。穂の根から左右へ肋が三日月に開き、苔の房飾りと、番兵長の破れた苔の旗が下がる。柄に金の帯"""
    f = []
    f += prism("grip", (0, -3.0, 0), (0, 13.0, 0), 0.9, 0.85, sides=8, caps=(True, True))
    f += prism("wrap", (0, -1.6, 0), (0, 2.6, 0), 1.05, 1.05, sides=8, caps=(False, False))
    for y in (-2.4, 3.2, 7.8, 12.2):
        f += ringy("trim", y, 1.12, 0.4)
    f += spike(BONE, "spine", (0, -3.0, 0), (0, -1, 0), 1.4, 0.8, sides=6, rings=1, hint=(0, 0, 1))
    # 首: 椎骨の襟
    f += vertebra("guard", (0, 13.4, 0), 1.3, 0.9, procs=4, plen=0.7)
    # 三日月の肋 (左右、上へ反る)
    for s in (1, -1):
        rib = [(s * 0.8, 13.8, 0), (s * 3.4, 13.0, 0), (s * 5.8, 13.8, 0), (s * 7.1, 16.4, 0)]
        f += curve("edge", rib, [0.5, 0.5, 0.45, 0.15], [1.0, 1.05, 0.8, 0.15], sides=6, caps=(False, True), hint=(0, 0, 1))
        f += curve("main", [(s * 1.0, 14.6, 0), (s * 3.0, 14.6, 0), (s * 4.8, 15.6, 0)], [0.45, 0.4, 0.12], [0.35, 0.3, 0.12], sides=5, caps=(False, True), hint=(0, 0, 1))
        f += spike(BONE, "edge", (s * 5.0, 13.6, 0), (s * 0.4, -1, 0), 1.2, 0.3, sides=4, rings=1, hint=(0, 0, 1))
    # 穂: 肩甲骨の板 (幅広、先は尖る)
    f += blade("main", [(13.8, -2.2, 2.2, 0.65, 0.65), (16.0, -2.6, 2.6, 0.6, 0.6), (18.0, -1.7, 1.7, 0.5, 0.5), (19.6, 0, 0, 0, 0)])
    f += block("edge", (0, 15.2, 0), (0.8, 3.4, 1.5), taper=(0.6, 0.9))
    f += gbar([(0.0, 14.6, -0.6), (0.0, 16.8, -0.55), (0.0, 18.4, -0.42)], r=0.2)
    # 苔の房と破れた旗
    f += strand("acc", (0.9, 12.8, 0.6), 2.6, 0.4, r=0.3)
    f += strand("acc", (-0.9, 12.8, 0.6), 2.2, 0.35, r=0.26)
    f += extrude("wrap", [(-0.95, 11.6), (-0.95, 8.2), (-3.2, 8.8), (-2.6, 9.6), (-3.6, 10.6), (-3.0, 11.6)], 0.22, c=(0, 0, -0.2))
    f += barnacles((0.9, 5.0, -0.8), n=3, spread=0.4, r=0.24, h=0.4, mat="guard")
    return f


def whalebone_greatsword(stage=0):
    """鯨骨の大剣: 岩鯨の太い肋骨一本をそのまま刃にした大剣。ゆるく反った骨の背に割れ目が水色に光り、根は椎骨の鍔。刃に藤壺、柄は石に苔を巻く"""
    f = []
    f += prism("grip", (0, -3.8, 0), (0, 3.4, 0), 1.0, 0.95, sides=8, caps=(True, False))
    f += prism("wrap", (0, -3.0, 0), (0, 2.6, 0), 1.12, 1.12, sides=8, caps=(False, False))
    for y in (-2.0, -0.4, 1.2):
        f += ringy("cord", y, 1.2, 0.32)
    f += vertebra("guard", (0, -4.2, 0), 1.2, 0.8, procs=4, plen=0.6)
    # 鍔: 大きな椎骨 (横突起が左右へ張る)
    f += vertebra("spine", (0, 4.0, 0), 2.0, 1.3, procs=2, plen=2.6, ph=0.0)
    # 刃: 肋骨 (幅広の板が +x へゆるく反る。先は丸く尖る)
    f += blade("main", [(5.0, -2.6, 2.4, 0.95, 0.95), (9.0, -2.7, 2.5, 0.9, 0.9, 0.3), (13.0, -2.5, 2.3, 0.85, 0.85, 0.75),
                        (16.6, -2.1, 1.9, 0.7, 0.7, 1.3), (19.2, -1.3, 1.1, 0.5, 0.5, 1.8), (20.8, 0, 0, 0, 0, 2.2)])
    # 骨の背の稜 (芯の盛り上がり) と割れ目の光
    f += curve("edge", [(0.1, 5.2, -0.85), (0.5, 10.0, -0.82), (1.2, 15.0, -0.7), (1.9, 19.0, -0.45)], [0.45, 0.45, 0.38, 0.2], [0.25, 0.25, 0.22, 0.12], sides=4, caps=(True, True), hint=(0, 0, 1))
    f += gbar([(-1.3, 6.6, -0.9), (-0.9, 9.4, -0.88), (-1.2, 12.0, -0.85)], r=0.2)
    f += gbar([(1.6, 13.4, -0.8), (1.2, 16.0, -0.72), (1.8, 18.0, -0.6)], r=0.18)
    f += barnacles((-1.4, 15.2, 0.85), n=4, spread=0.6, r=0.28, h=0.45, dirv=(0, 0, 1), mat="guard")
    f += barnacles((1.6, 8.0, -0.9), n=3, spread=0.5, r=0.26, h=0.4, mat="guard")
    # 背 (-x) の縁の欠けと、刃の根の骨の節 (二本の輪)
    for y, w in ((7.6, 1.2), (11.0, 1.0), (14.4, 0.9)):
        f += block("main", (-2.25 + 0.15 * (y > 12), y, 0), (w, 1.4, 1.2), rot=rot_z(-14), taper=(0.55, 0.85))
    for y in (5.6, 6.6):
        f += ringy("edge", y, 2.6 - 0.2 * (y - 5.6), 0.45, sides=6)
    f += strand("acc", (2.2, 4.4, 0.6), 2.6, 0.4, r=0.3)
    f += strand("acc", (-2.0, 4.2, 0.7), 2.0, 0.35, r=0.26)
    return f


def abyss_greatsword(stage=0):
    """底なしの大剣: 鯨骨の枠だけが刃の形をなし、枠の内は底の見えない淵。淵の中を星の粒が落ちてゆく。枠の縁は金、鍔は沈んだ鯨の頭骨、柄は金を巻いた骨"""
    f = []
    f += bone("grip", [(0, -3.1, 0), (0, 0.0, 0), (0, 3.4, 0)], [0.95, 0.9, 0.95], knob=False)
    f += prism("cord", (0, -2.4, 0), (0, 2.8, 0), 1.08, 1.08, sides=8, caps=(False, False))
    for y in (-2.6, 0.2, 3.0):
        f += ringy("trim", y, 1.2, 0.42)
    f += gem((0, -3.6, 0), 0.6, 0.6, 0.6, segs=6, rings=2, mat="void")
    # 鍔: 鯨の頭骨 (横長の塊、両の眼窩に淵)
    f += lump("spine", (0, 4.4, 0), (3.4, 1.4, 1.6), seed=7, segs=8, rings=2)
    for s in (1, -1):
        f += ell("void", (s * 1.5, 4.6, -1.3), (0.6, 0.45, 0.3), segs=6, rings=1)
        f += spike(BONE, "edge", (s * 3.2, 4.2, 0), (s, -0.4, 0), 1.6, 0.45, sides=5, rings=1, hint=(0, 0, 1))
    # 刃の枠 (左右の骨の縁と先) と、内の淵の板
    L = [(-2.3, 5.6), (-2.5, 10.0), (-2.2, 15.0), (-1.4, 19.0), (0.0, 21.0)]
    for s in (1, -1):
        pts = [(-s * x, y, 0) for x, y in L]
        f += curve("main", pts, [0.7, 0.7, 0.6, 0.5, 0.3], [0.75, 0.72, 0.65, 0.5, 0.3], sides=6, caps=(True, True), hint=(0, 0, 1))
        f += curve("trim", [(p[0] * 1.12, p[1], 0) for p in pts[:4]], [0.18] * 4, [0.4] * 4, sides=4, caps=(True, True), hint=(0, 0, 1))
    f += blade("void", [(5.4, -1.9, 1.9, 0.3, 0.3), (10.0, -2.05, 2.05, 0.3, 0.3), (15.0, -1.8, 1.8, 0.28, 0.28), (19.0, -1.0, 1.0, 0.22, 0.22), (20.4, 0, 0, 0, 0)],
               colmats=["void"] * 6)
    # 落ちてゆく星の粒
    for x, y in ((-0.8, 8.0), (0.6, 10.6), (-0.3, 13.4), (0.7, 16.2), (-0.4, 18.2)):
        f += gem((x, y, -0.35), 0.28, 0.28, 0.2, segs=4, rings=1)
    return f


# ================================================================== 潮殻
def tidecaller_claw(stage=0):
    """潮招きの大鋏: 招き蟹の片方だけ大きな鋏を刃にした武器。太い親指 (動かない指) の内縁に真珠の粒の歯が並び、細い動く指が寄り添う。甲羅の継ぎ目は珊瑚の赤、根に薄紅の光"""
    f = []
    f += prism("grip", (0, -2.3, 0), (0, 4.0, 0), 0.9, 0.9, sides=6, caps=(True, False))
    f += kelp_grip(-1.8, 3.4, r=1.05, n=3)
    f += pearl((0, -2.8, 0), 0.8, mat="main")
    # 腕の節 (甲羅の筒二つ)
    f += prism("spine", (0, 4.0, 0), (0.6, 7.0, 0), 1.6, 1.9, sides=7, caps=(True, False), phase=10)
    f += ringy("acc", 7.0, 2.0, 0.5, cx=0.6)
    # 鋏の掌 (膨らんだ殻)
    f += ell("spine", (1.4, 9.6, 0), (3.0, 3.0, 1.9), segs=9, rings=3)
    f += ell("acc", (0.0, 8.2, -1.4), (0.9, 0.7, 0.5), segs=6, rings=1)
    f += gem((1.0, 9.4, -1.75), 0.55, 0.55, 0.3, segs=6, rings=1)
    # 親指 (太い、内へ反る) と、動く指 (細い)
    thumb = [(2.2, 11.6, 0), (2.7, 14.4, 0), (2.4, 16.8, 0), (1.2, 18.6, 0), (0.0, 19.2, 0)]
    f += curve("main", thumb, [1.0, 0.95, 0.8, 0.55, 0.2], [1.5, 1.35, 1.1, 0.7, 0.2], sides=6, caps=(False, True), hint=(0, 0, 1))
    for i, (x, y, _) in enumerate(thumb[:4]):
        f += pearl((x - 1.15 + 0.15 * i, y + 0.4, 0), 0.42 - 0.05 * i, mat="edge")
    finger = [(-0.6, 11.0, 0), (-1.6, 13.2, 0), (-1.9, 15.4, 0), (-1.2, 16.8, 0)]
    f += curve("main", finger, [0.7, 0.62, 0.45, 0.15], [0.95, 0.8, 0.55, 0.15], sides=6, caps=(False, True), hint=(0, 0, 1))
    f += ell("guard", (-0.4, 11.0, 0), (0.9, 0.9, 0.9), segs=6, rings=1)
    # 甲羅の模様 (珊瑚の赤い点) と潮の筋
    for x, y in ((2.9, 12.8), (2.2, 9.0), (3.4, 10.4)):
        f += ell("acc", (x, y, -0.9), (0.38, 0.38, 0.25), segs=5, rings=1)
    f += gbar([(2.0, 12.4, -0.95), (2.4, 15.0, -0.85), (1.6, 17.6, -0.55)], r=0.17)
    f += barnacles((-0.6, 7.6, 1.6), n=3, spread=0.5, r=0.26, h=0.45, dirv=(0, 0, 1), mat="guard")
    return f


def coral_harpoon(stage=0):
    """珊瑚憑きの銛: 古い銛に珊瑚が根を張った。返しの付いた真珠の穂、柄を這い上がる珊瑚の枝、ほどけた昆布の綱がとぐろを巻いて垂れる。穂の根に薄紅の光"""
    f = []
    f += prism("grip", (0, -3.8, 0), (0, 14.4, 0), 0.85, 0.8, sides=8, caps=(True, True))
    f += kelp_grip(-2.6, 2.4, r=1.0, n=3)
    for y in (-3.2, 3.0, 13.8):
        f += ringy("trim", y, 1.08, 0.4)
    f += spike(BONE, "spine", (0, -3.8, 0), (0, -1, 0), 1.2, 0.75, sides=6, rings=1, hint=(0, 0, 1))
    # 穂: 真珠の矢じりと、二段の返し
    f += prism("spine", (0, 14.4, 0), (0, 16.0, 0), 0.9, 0.7, sides=6, caps=(True, False))
    f += blade("main", [(15.6, -1.3, 1.3, 0.5, 0.5), (18.0, -1.4, 1.4, 0.45, 0.45), (20.0, -0.7, 0.7, 0.32, 0.32), (21.0, 0, 0, 0, 0)])
    for s in (1, -1):
        f += extrude("edge", [(s * 1.2, 16.0), (s * 1.4, 17.6), (s * 2.6, 15.2)][::s], 0.5, c=(0, 0, 0))
        f += extrude("edge", [(s * 0.8, 14.6), (s * 0.9, 15.6), (s * 2.0, 13.6)][::s], 0.45, c=(0, 0, 0))
    f += gem((0, 16.2, -0.55), 0.45, 0.55, 0.25, segs=5, rings=1)
    # 這い上がる珊瑚 (柄に絡む枝)
    for y0, s in ((5.0, 1), (8.4, -1), (11.6, 1)):
        f += curve("acc", [(s * 0.6, y0, -0.5), (s * 1.8, y0 + 1.0, -0.8), (s * 2.3, y0 + 2.4, -0.7)], [0.42, 0.32, 0.12], [0.42, 0.32, 0.12], sides=4, caps=(False, True))
        f += curve("acc", [(s * 1.8, y0 + 1.0, -0.8), (s * 2.8, y0 + 1.2, -0.6)], [0.24, 0.08], [0.24, 0.08], sides=4, caps=(False, True))
        f += ell("acc", (s * 0.7, y0 + 0.2, -0.5), (0.6, 0.8, 0.5), segs=5, rings=1)
    # 昆布の綱 (穂の根から垂れて、とぐろ)
    rope = [(0.9, 13.6, 0.7), (2.4, 12.4, 1.2), (3.0, 10.0, 1.4), (2.2, 8.4, 1.6), (3.2, 7.0, 1.4), (3.6, 5.0, 1.0)]
    f += curve("wrap", rope, [0.28] * 6, [0.28] * 6, sides=4, caps=(True, True))
    f += torus("wrap", (0, 13.0, 0), 1.25, 0.25, 0.3, n=8, k=4, ax="y")
    f += barnacles((0.7, 10.0, 0.8), n=3, spread=0.4, r=0.22, h=0.4, dirv=(0, 0, 1), mat="guard")
    return f


# ================================================================== 雷翼
def raiju_claw(stage=0):
    """雷獣の爪: 握りを包む銅の手甲から、雷獣の鉤爪が三本そろって前へ反る。爪のあいだを青白い稲妻が渡り、手甲の縁に雷獣の白い毛の房。柄は鋼に紐巻き"""
    f = steel_haft(-0.9, 4.0, r=0.85, wrap=(-0.4, 3.4))
    f += ell("guard", (0, -1.3, 0), (0.95, 0.6, 0.95), segs=6, rings=1)
    # 手甲 (握りの上を包む銅の甲。前に三つの拳の節)
    f += block("main", (0, 5.0, 0), (5.4, 2.2, 2.2), taper=(0.9, 0.85))
    for x in (-1.8, 0.0, 1.8):
        f += ell("spine", (x, 6.1, 0), (0.9, 0.7, 1.0), segs=6, rings=1)
    f += ringy("trim", 3.9, 1.1, 0.4)
    # 三本の爪 (鋼、前へ反って先は鋭い)
    for x, h, lean in ((-2.0, 9.8, -0.8), (0.0, 11.6, 0.0), (2.0, 9.8, 0.8)):
        pts = [(x, 6.4, 0), (x + lean * 0.5, 6.4 + h * 0.45, -0.3), (x + lean, 6.4 + h * 0.8, -1.0), (x + lean * 0.9, 6.4 + h, -2.0)]
        f += curve("spine", pts, [0.75, 0.65, 0.45, 0.1], [0.55, 0.5, 0.38, 0.1], sides=5, caps=(False, True), hint=(1, 0, 0))
        f += curve("main", pts[:3], [0.28, 0.24, 0.16], [0.6, 0.55, 0.42], sides=4, caps=(True, True), hint=(1, 0, 0))
    # 爪のあいだを渡る稲妻
    f += zig([(-1.6, 10.0, -0.4), (-0.6, 11.0, -0.4), (-1.0, 12.0, -0.4), (0.0, 13.0, -0.6)], w=0.22, th=0.16, mat="gem")
    f += zig([(1.6, 10.0, -0.4), (0.7, 11.2, -0.4), (1.1, 12.2, -0.4), (0.2, 13.4, -0.6)], w=0.22, th=0.16, mat="gem")
    # 白い毛の房 (手甲の縁から左右と後ろへ)
    for s in (1, -1):
        f += feather("acc", (s * 2.4, 4.4, 0.4), (s * 4.4, 2.6, 0.6), 0.8, 0.35, big=False)
        f += feather("acc", (s * 2.6, 5.2, 0.6), (s * 5.0, 4.6, 0.9), 0.7, 0.3, big=False)
    f += coil(1.2, 3.2, 1.05, 2.0, w=0.2)
    return f


def warden_glaive(stage=0):
    """羽守の鉤槍: 鋼の長い柄の先に、ゆるく反った片刃の銅の大刃 (薙刀の形)。刃の背の根から、落ちるものを掛ける鋼の鉤が一本大きく前へ反る。刃の根の口金に雷翼の羽が二枚"""
    f = steel_haft(-3.6, 12.6, r=0.8, wrap=(-2.6, 2.2))
    for y in (6.0, 11.8):
        f += ringy("main", y, 1.1, 0.4)
    f += spike(BONE, "guard", (0, -3.6, 0), (0, -1, 0), 1.3, 0.7, sides=6, rings=1, hint=(0, 0, 1))
    # 口金 (鋼の筒と銅の輪)
    f += prism("guard", (0, 12.2, 0), (0, 14.2, 0), 1.25, 1.1, sides=6, caps=(True, True))
    f += ringy("main", 13.2, 1.35, 0.45)
    # 刃: 薙刀 (刃は +x、背は真っすぐ、先が背の側へ反る)。背に鋼の筋
    f += blade("main", [(14.0, -0.8, 2.2, 0.55, 0.55), (17.0, -0.8, 3.3, 0.55, 0.55, 0.2), (20.0, -0.8, 3.1, 0.5, 0.5, 0.15),
                        (22.4, -0.7, 1.9, 0.4, 0.4, -0.4), (23.8, 0, 0, 0, 0, -1.3)], colmats=["edge", "main", "edge", "edge", "main", "edge"])
    f += gbar([(1.2, 14.8, -0.57), (1.6, 17.8, -0.55), (1.2, 21.0, -0.45)], r=0.18)
    # 鉤 (背の根から -x へ出て、上へ大きく反る)
    f += curve("spine", [(-0.7, 14.6, 0), (-2.6, 14.8, 0), (-4.0, 16.2, 0), (-4.3, 18.4, 0), (-3.6, 19.8, 0)],
               [0.5, 0.48, 0.42, 0.32, 0.1], [0.75, 0.7, 0.58, 0.42, 0.1], sides=5, caps=(False, True), hint=(0, 0, 1))
    # 雷翼の羽 (口金の左右へ、下向きに二枚)
    for s_ in (1, -1):
        f += feather("acc", (s_ * 0.9, 12.8, 0.4), (s_ * 2.6, 9.6, 0.5), 0.75, 0.32, big=False)
    f += torus("cord", (0, 12.0, 0), 1.0, 0.2, 0.26, n=8, k=4, ax="y")
    return f


def abyss_storm_bow(stage=0):
    """雷淵の弓: 雷雲の底で曲がった弓。腕は黒ずんだ銅の板で、雲の渦のように外へ巻いて段になる。腕の芯に稲妻が走り、先は避雷の小さな穂。握りは金を巻いた鋼と淵の核"""
    f = []
    f += prism("grip", (5.0, CY - 2.8, 0), (5.0, CY + 2.8, 0), 1.0, 1.0, sides=8, caps=(False, False), hint=(0, 0, 1))
    f += prism("trim", (5.0, CY - 1.4, 0), (5.0, CY + 1.4, 0), 1.2, 1.2, sides=8, caps=(True, True), hint=(0, 0, 1))
    f += ell("void", (6.2, CY, 0), (0.8, 1.4, 0.9), segs=6, rings=2)
    f += gem((6.8, CY, 0), 0.42, 0.62, 0.5, segs=5, rings=1)
    up = bend(stage, [(5.0, CY + 2.4, 0), (4.6, CY + 5.0, 0), (3.2, CY + 7.6, 0), (1.0, CY + 9.6, 0), (-1.4, CY + 10.8, 0)])
    half = []
    half += curve("main", up, [0.75, 0.7, 0.62, 0.5, 0.3], [0.55, 0.5, 0.45, 0.4, 0.3], sides=5, caps=(False, True), hint=(0, 0, 1))
    # 雲の段 (外へ巻く丸い板。腕の外側に三つ)
    for i in range(3):
        (x0, y0, _), (x1, y1, _) = up[i], up[i + 1]
        mx, my = (x0 + x1) / 2, (y0 + y1) / 2
        dx, dy = x1 - x0, y1 - y0
        Ln = math.hypot(dx, dy)
        nx, ny = dy / Ln, -dx / Ln
        r = 1.35 - 0.2 * i
        half += ell("spine", (mx + nx * 0.9, my + ny * 0.9, 0), (r, r * 0.8, 0.55), segs=7, rings=1)
        half += torus("main", (mx + nx * 0.9, my + ny * 0.9, 0), r * 0.75, 0.16, 0.2, n=8, k=3, ax="z")
    # 芯の稲妻と先の避雷の穂
    half += zig([(p[0] - 0.2, p[1], -0.5) for p in up[:4]], w=0.2, th=0.14, mat="gem")
    tx, ty, _ = up[-1]
    half += spike(BONE, "main", (tx, ty, 0), (-0.6, 0.8, 0), 1.5, 0.3, sides=4, rings=1, hint=(0, 0, 1))
    half += prism("trim", (up[1][0], up[1][1] - 0.3, 0), (up[1][0], up[1][1] + 0.3, 0), 0.85, 0.85, sides=6, caps=(True, True), hint=(0, 0, 1))
    f += half + mirror_faces_y(half)
    lo = [(x, 2 * CY - y, z) for x, y, z in up]
    f += coil(CY - 1.0, CY + 1.0, 1.15, 2.0, w=0.18, cx=5.0)
    return f + string_and_arrow(stage, "legend", up, lo)


# ================================================================== 余燼
def smith_hammer(stage=0):
    """鍛え手の大鎚: 両手で振る大鎚。頭は金床の形の鉄塊 (片方は平らな打つ面、片方は楔)、芯に玄武岩の熾火がのぞく。長い木の柄に鉄の帯と鎖、柄頭に焼けた岩"""
    f = wood_haft(-11.5, 15.0, r=1.0, wrap=(-10.6, -4.0), bands=3)
    f += lump("guard", (0, -11.9, 0), (1.3, 1.0, 1.3), seed=5, segs=6, rings=2)
    f += prism("wrap", (0, 2.0, 0), (0, 6.0, 0), 1.18, 1.18, sides=6, caps=(False, False))
    # 頭: 金床の形 (中央の胴、上の広い台、左右の角)
    f += block("spine", (0, 18.6, 0), (6.4, 5.2, 3.6), taper=(1.0, 1.0))
    f += block("edge", (0, 21.8, 0), (8.6, 1.6, 3.9), taper=(0.92, 0.92))
    f += block("spine", (5.0, 18.6, 0), (3.2, 4.4, 3.4), taper=(1.0, 1.0))
    f += prism("edge", (6.6, 18.6, -1.6), (6.6, 18.6, 1.6), 2.2, 2.2, sides=8, caps=(True, True), hint=(1, 0, 0))
    f += block("spine", (-5.0, 18.8, 0), (3.6, 3.6, 3.0), taper=(0.35, 0.6), rot=rot_z(90))
    # 胴の割れ目から熾火、鉄の帯と鋲
    f += block("main", (0, 18.4, -1.6), (3.0, 3.4, 0.6))
    f += gbar([(-0.8, 17.2, -1.95), (0.2, 18.6, -1.95), (-0.4, 19.8, -1.95)], r=0.22)
    for x in (-2.4, 2.4):
        f += block("chain", (x, 18.6, 0), (0.5, 5.4, 3.8))
        for y in (16.8, 20.4):
            f += ell("edge", (x, y, -1.95), (0.3, 0.3, 0.2), segs=4, rings=1)
    # 柄の首の鉄の口金と鎖
    f += prism("chain", (0, 14.4, 0), (0, 16.2, 0), 1.35, 1.35, sides=6, caps=(True, True))
    f += chain((1.2, 15.0, -0.6), (2.2, 9.0, -0.8), n=5)
    f += lump("acc", (2.4, 8.4, -0.8), (0.6, 0.5, 0.5), seed=9, segs=5, rings=1)
    return f


def ember_war_axe(stage=0):
    """余燼の戦斧: 長い木の柄の先に、三日月の玄武岩の刃。縁は打った鉄、面の割れ目がくすぶって光る。背は鉄の鉤爪、首に灰の布が巻かれて垂れる"""
    f = wood_haft(-7.4, 18.6, r=0.95, wrap=(-6.6, -1.6), bands=3)
    f += prism("edge", (0, -7.8, 0), (0, -7.0, 0), 1.2, 1.1, sides=6, caps=(True, True))
    # 首の口金
    f += prism("spine", (0, 14.0, 0), (0, 19.4, 0), 1.25, 1.2, sides=6, caps=(True, True))
    f += spike(BONE, "spine", (0, 19.4, 0), (0, 1, 0), 2.8, 0.9, sides=6, rings=1, hint=(0, 0, 1))
    # 刃: 三日月 (-x 側。上下に角が張る)
    # 刃: 外へ張った弧 (上下の角が鉤のように反る) と、柄の側の真っすぐな根を四角でつなぐ
    n = 8
    outer = [(-2.2 + 6.6 * math.cos(math.radians(a)), 16.6 + 6.2 * math.sin(math.radians(a))) for a in [96 + (264 - 96) * k / (n - 1) for k in range(n)]]
    outer[0] = (outer[0][0] + 0.9, outer[0][1] + 0.6)
    outer[-1] = (outer[-1][0] + 0.9, outer[-1][1] - 0.6)
    root = [(-0.9, 19.6 - (19.6 - 13.6) * k / (n - 1)) for k in range(n)]
    for k in range(n - 1):
        f += extrude("main", [root[k], outer[k], outer[k + 1], root[k + 1]], 0.95 - 0.25 * abs(k - (n - 2) / 2) / n, c=(0, 0, 0))
    f += curve("edge", [(x, y, 0) for x, y in outer], [0.42] * n, [0.55] * n, sides=4, caps=(True, True), hint=(0, 0, 1))
    f += gbar([(-5.4, 20.4, -0.52), (-6.6, 18.4, -0.52), (-7.0, 16.6, -0.52), (-6.4, 14.4, -0.52), (-5.2, 12.6, -0.52)], r=0.2)
    f += gbar([(-3.2, 20.6, -0.5), (-4.4, 19.4, -0.5)], r=0.16)
    f += gbar([(-3.2, 12.6, -0.5), (-4.4, 13.6, -0.5)], r=0.16)
    # 背の鉤爪 (+x)
    f += curve("edge", [(1.0, 17.0, 0), (2.8, 17.4, 0), (4.2, 16.2, 0), (4.6, 14.6, 0)], [0.6, 0.5, 0.38, 0.1], [0.5, 0.45, 0.35, 0.1], sides=5, caps=(False, True), hint=(0, 0, 1))
    # 灰の布
    f += prism("wrap", (0, 12.2, 0), (0, 13.8, 0), 1.25, 1.25, sides=6, caps=(False, False))
    f += rag((1.0, 12.6, -0.6), 3.4, 1.0, sign=1, mat="wrap", th=0.25)
    return f


def abyss_ash_scythe(stage=0):
    """灰喰いの鎌: 長い焦げた柄の先から、黒い玄武岩の大きな刃が大きく弧を描く。刃の内縁は灰に光り、刃の背に灰の燃え殻が這う。首に金の環と淵の核、柄に鎖"""
    f = wood_haft(-3.3, 18.6, r=0.9, wrap=(-2.6, 2.4), bands=3)
    f += lump("guard", (0, -3.7, 0), (1.0, 0.8, 1.0), seed=3, segs=6, rings=2)
    f += torus("trim", (0, 17.6, 0), 1.3, 0.28, 0.32, n=8, k=4, ax="y")
    f += prism("spine", (0, 18.0, 0), (0, 20.0, 0), 1.3, 1.1, sides=6, caps=(True, True))
    f += ell("void", (0.9, 19.0, -0.9), (0.6, 0.6, 0.4), segs=6, rings=1)
    # 刃: -x へ大きく弧を描く (根は太く、先は細く尖る)
    arcp = [(-0.6, 19.4, 0), (-3.4, 20.0, 0), (-6.6, 19.6, 0), (-9.6, 18.2, 0), (-12.2, 16.0, 0), (-13.8, 13.4, 0)]
    f += curve("main", arcp, [0.6, 0.58, 0.55, 0.48, 0.38, 0.15], [1.9, 1.9, 1.7, 1.35, 0.9, 0.15], sides=6, caps=(False, True), hint=(0, 0, 1))
    # 内縁の灰の光 (弧の下側に沿う)
    f += gbar([(-3.4, 18.4, -0.55), (-6.4, 18.0, -0.5), (-9.2, 16.6, -0.45), (-11.6, 14.6, -0.38)], r=0.2)
    # 背の燃え殻 (弧の上に小さな塊)
    for x, y in ((-3.2, 21.2), (-6.4, 20.9), (-9.4, 19.6)):
        f += lump("acc", (x, y, 0), (0.7, 0.5, 0.55), seed=int(-x * 10), segs=5, rings=1)
        f += flame_tip((x, y + 0.4, 0))
    f += chain((0.9, 15.0, -0.6), (1.2, 9.4, -0.8), n=4, mat="trim")
    return f


def flame_tip(c):
    return spike(BONE, "gem", c, (0, 1, 0), 0.9, 0.22, sides=4, rings=1, hint=(0, 0, 1))


# ================================================================== 一覧
def _w(fn, jp, tier, glow, pose, grip, cen, scale, inlay, finish=None, stages=1):
    if finish:
        base, fn = fn, (lambda stage=0, base=fn: base(stage) + legend_finish(finish, seed=len(jp)))
        fn.__doc__ = base.__doc__
    e = dict(jp=jp, tier=tier, glow=glow, pose=pose, grip=grip, cen=cen, scale=scale,
             opts={"a": ((lambda stage=0: fn(stage)), inlay, ("edge",), (fn.__doc__ or "").split(":", 1)[-1].strip())})
    if stages > 1:
        e["stages"] = stages
    return e


DG = dict(pose="whale", grip=(0.0, 1.5, 0.0), cen=(0.0, 8.0, 0.0), scale=12.5)
PL = dict(pose="whale", grip=(0.0, 1.0, 0.0), cen=(0.0, 9.0, 0.0), scale=9.0)
SW = dict(pose="whale", grip=(0.0, 1.0, 0.0), cen=(0.0, 9.0, 0.0), scale=10.0)
HM = dict(pose="ember", grip=(0.0, 0.0, 0.0), cen=(0.0, 8.0, 0.0), scale=7.5)
BW = dict(pose="thunder", grip=(5.0, 8.0, 0.0), cen=(2.0, 8.0, 0.0), scale=12.0, stages=4)
off = inlay_line(["main"], 99.0, 99.5)
WEAPONS = {
    "waterway_fang": _w(waterway_fang, "水路の顎牙", "legend", "whale", inlay=inlay_line(["main"], 6.0, 14.0), **DG),
    "captain_pike": _w(captain_pike, "番兵長の鉾", "legend", "whale", inlay=inlay_line(["main"], 14.5, 19.0), **PL),
    "whalebone_greatsword": _w(whalebone_greatsword, "鯨骨の大剣", "rare", "whale", inlay=inlay_line(["main"], 6.0, 18.0), **SW),
    "abyss_greatsword": _w(abyss_greatsword, "底なしの大剣", "legend", "whale", inlay=inlay_line(["main"], 6.0, 19.0), finish="sword", **SW),
    "tidecaller_claw": _w(tidecaller_claw, "潮招きの大鋏", "legend", "tide", inlay=inlay_line(["main"], 12.0, 18.0, lambda y: 0.02 * y + 1.6), **DG),
    "coral_harpoon": _w(coral_harpoon, "珊瑚憑きの銛", "legend", "tide", inlay=inlay_line(["main"], 16.0, 20.0), **PL),
    "raiju_claw": _w(raiju_claw, "雷獣の爪", "legend", "thunder", inlay=inlay_line(["main"], 4.0, 6.0), **DG),
    "warden_glaive": _w(warden_glaive, "羽守の鉤槍", "legend", "thunder", inlay=inlay_line(["main"], 14.0, 20.0, lambda y: 0.0 * y + 1.0), **PL),
    "abyss_storm_bow": _w(abyss_storm_bow, "雷淵の弓", "legend", "thunder", inlay=inlay_line(["main"], 9.0, 18.0), finish="bow", **BW),
    "smith_hammer": _w(smith_hammer, "鍛え手の大鎚", "legend", "ember", inlay=off, **HM),
    "ember_war_axe": _w(ember_war_axe, "余燼の戦斧", "rare", "ember", inlay=off, **HM),
    "abyss_ash_scythe": _w(abyss_ash_scythe, "灰喰いの鎌", "legend", "ember", inlay=off, finish="staff", **PL),
}
