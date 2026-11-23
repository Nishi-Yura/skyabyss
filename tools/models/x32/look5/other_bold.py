# -*- coding: utf-8 -*-
"""その他（look5・個性版）。どのシリーズにも入らない 5 品。品ごとに出どころの土地が違うので、色も品ごとに 1 系統＋差し色＋光ひとつ。
  captain_pike   番兵長の鉾（鯨の背の番兵長。三度目ごとに骨が噴き上がる）: 煤けた鉄の柄、骨の幅広の穂、髄の青白い光
  smith_hammer   鍛冶の大鎚（燠の工房）: 煤の鉄の角頭、革巻きの短い柄、打つ面（+x）が赤く焼けている
  thrall_poker   下僕の火かき棒（燠の工房の下働き）: ねじった鉄の細い棒、輪の柄頭、先の鉤が赤熱
  warden_glaive  番人の薙刀（雷の塔の番人）: 嵐の色の長柄に銅の輪、+x へ反る片刃、刃の縁に稲妻の黄
  travel_shield  旅の盾（はじめの盾）: 木の板を鉄の縁と帯で留めた丸盾。光は入れない
"""
import math
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import lib  # noqa: E402
from lib import Model  # noqa: E402

lib.RAMPS.update({
    # 鉾: 煤けた鉄（青みの灰）、骨、髄の光
    'soot': ['#121418', '#1c2026', '#272c34', '#343a44', '#434a56', '#545c6a', '#68707e', '#828a96', '#a2a8b2', '#c6cad0'],
    'pbone': ['#4a3e32', '#76664e', '#a08c6c', '#c8b490', '#e6d8b8', '#fbf4e2'],
    'marrow': ['#1a5a6a', '#2a8a9a', '#4cb8c4', '#8ee2e6', '#d0fafa', '#ffffff'],
    # 鎚・火かき棒: 煤の鉄（赤みの黒）、革、焼けた鉄
    'forge': ['#120e0e', '#1e1818', '#2c2422', '#3a302c', '#4a3e38', '#5e5048', '#76665a', '#90806e', '#ae9e8a', '#ccc0aa'],
    'hide': ['#2e1a10', '#4a2c1a', '#6a4228', '#8c5a38', '#ae7a52', '#d0a07a'],
    'heat': ['#6a1404', '#b02a06', '#e85010', '#ff8a2a', '#ffc870', '#fff2d0'],
    # 薙刀: 嵐の鉄紺、銅、稲妻
    'gale': ['#121426', '#1c1f38', '#282c4c', '#363c62', '#464e78', '#5a648e', '#7480a6', '#949ebe', '#b8c0d6', '#dce0ec'],
    'brass': ['#3e2410', '#6a3e1a', '#966028', '#c08640', '#e2ae66', '#f8d69c'],
    'spark': ['#6a5c10', '#ae9a1e', '#e6d03c', '#fff070', '#fffbc4', '#ffffff'],
    # 盾: 樫の板、黒鉄
    'oak': ['#2a1a0e', '#3e2814', '#56381c', '#704a26', '#8a5e32', '#a47442', '#bc8c56', '#d2a670', '#e4c292', '#f2dcb6'],
    'iron': ['#16181c', '#24272c', '#34383e', '#464b52', '#5a6068', '#727880', '#8e949a', '#acb0b6', '#cacdd2', '#e8eaec'],
})


def M(name, accent, glow):
    return Model(name, accent=accent, glowmat=glow)


def diamond(m, name, mat, c, s, d, t0, t1, zc=0.0):
    h = s / (2 * math.sqrt(2))
    m.seg(name, mat, (c[0] - h, c[1] - h), (c[0] + h, c[1] + h), s, d, t0, t1, over=0, zc=zc)


I, Bn, Mw = 'soot', 'pbone', 'marrow'
FI, H, R = 'forge', 'hide', 'heat'
G_, Cu, Sp = 'gale', 'brass', 'spark'
W, FE = 'oak', 'iron'


def pike_haft(m, top):
    m.box('grip', I, [-1.5, -16, -1.5], [1.5, 0, 1.5], grad=(-16, 2, 0, 4), wrap=(-16, 0))
    m.box('butt', Bn, [-1.9, -18.5, -1.9], [1.9, -16, 1.9], grad=(-18.5, 2, -16, 4))
    m.seg('butt_tip', Bn, (0, -18.5), (0, -21), 2.25, 1.1, 3, 5, tip=True)
    m.box('haft', I, [-1.25, 0, -1.25], [1.25, top, 1.25], grad=(0, 3, top, 6))
    for k, y in enumerate((0, 12, 24)):
        m.box(f'ring_{k}', Bn, [-1.9, y - 0.75, -1.9], [1.9, y + 0.75, 1.9], 4)


def smith_grip(m, top):
    m.box('grip', H, [-1.75, -7, -1.75], [1.75, 7, 1.75], grad=(-7, 2, 7, 4), wrap=(-7, 7))
    m.box('pommel', FI, [-2.25, -9.5, -2.25], [2.25, -7, 2.25], grad=(-9.5, 2, -7, 4))
    m.box('haft', FI, [-1.5, 7, -1.5], [1.5, top, 1.5], grad=(7, 3, top, 5))


def wild_sparks(m, pts, s=1.0):
    for k, (x, y) in enumerate(pts):
        diamond(m, f'spark_{k}_glow', R, (x, y), s * (1 - 0.12 * (k % 3)), 0.7, 3, 5)


def poker_rod(m, top):
    m.box('grip', H, [-1.4, -10, -1.4], [1.4, 2, 1.4], grad=(-10, 2, 2, 4), wrap=(-10, 2))
    m.box('grip_cap', FI, [-1.8, -10.75, -1.8], [1.8, -9.5, 1.8], 4)
    m.arc('loop', FI, (0, -14.25), 3.25, 90, 450, 8, 1.25, 0.8, 4, 6)
    m.box('ferrule', FI, [-1.8, 1.5, -1.8], [1.8, 3, 1.8], 5)
    n = int((top - 3) // 3)
    for k in range(n):
        y = 3 + k * 3
        if k % 2:
            diamond(m, f'twist_{k}', FI, (0, y + 1.5), 2.6, 0.95, 3 + k * 0.15, 3.5 + k * 0.15)
        else:
            m.box(f'twist_{k}', FI, [-0.95, y, -0.95], [0.95, y + 3, 0.95], grad=(y, 3 + k * 0.15, y + 3, 3.5 + k * 0.15))
    return 3 + n * 3


def embers(m, pts, s=1.0):
    for k, (x, y) in enumerate(pts):
        diamond(m, f'mote_{k}_glow', R, (x, y), s * (1 - 0.15 * (k % 3)), 0.6, 2 + k % 2, 4)


def glaive_haft(m, top):
    m.box('grip', G_, [-1.5, -16, -1.5], [1.5, 0, 1.5], grad=(-16, 2, 0, 4), wrap=(-16, 0))
    m.box('butt', Cu, [-1.9, -19, -1.9], [1.9, -16, 1.9], grad=(-19, 2, -16, 4))
    diamond(m, 'butt_tip', Cu, (0, -20.25), 2.5, 1.1, 3, 5)
    m.box('haft', G_, [-1.25, 0, -1.25], [1.25, top, 1.25], grad=(0, 3, top, 6))
    for k, y in enumerate((0.5, 10, 20)):
        m.box(f'ring_{k}', Cu, [-1.8, y - 0.6, -1.8], [1.8, y + 0.6, 1.8], 4)


def bolts(m, pts, s=1.0):
    for k, (x, y) in enumerate(pts):
        diamond(m, f'spark_{k}_glow', Sp, (x, y), s * (1 - 0.15 * (k % 3)), 0.6, 3, 5)


# ================================================================ 番兵長の鉾

def captain_pike():
    """（伝説、2026-11-21 に作り直し・案 B）番兵長の鉾: 穂の根元が大きな鯨の椎骨。左右に横突起が翼のように張り、上の棘突起がそのまま骨の刃になる。
    椎骨の穴（髄の通り道）が髄の色に光り、穴から髄のしずくが上へ浮かぶ
    ※ 2026-11-21 に Blockbench で手を入れた（段の骨の刃を菱形の断面で先へ細る一枚のメッシュに、左右の横突起を先へ細る帯のメッシュに替え、横突起の小さな棘を前後へ開いた。筆で刃の稜線を明るく、縁を暗く、横突起の上の縁を明るく）。メッシュの形と UV はスクリプト（oth_gen.py）で作って MCP で置き、UV は書き出した .bbmodel にスクリプトで入れた。筆は塗る画素をスクリプト（oth_paint.py）で決めて paint_with_brush で 1 画素ずつ打った。正本は tools/models/x32/captain_pike.bbmodel。この関数から prep し直すと手入れが消える"""
    m = M('captain_pike', Bn, Mw)
    pike_haft(m, 32)
    m.box('body', Bn, [-3.5, 32, -2.75], [3.5, 38, 2.75], grad=(32, 3, 38, 5))
    m.box('body_rim_u', Bn, [-3.9, 37.25, -3.1], [3.9, 38.5, 3.1], 5)
    m.box('body_rim_d', Bn, [-3.9, 31.5, -3.1], [3.9, 32.75, 3.1], 3)
    m.box('canal_glow', Mw, [-1.1, 33.75, -2.85], [1.1, 36.25, 2.85], 4)
    for sd, n in ((-1, 'l'), (1, 'r')):
        m.chain(f'process_{n}', Bn, [(sd * 3.5, 35), (sd * 7, 36.5), (sd * 9.5, 39.5), (sd * 10.25, 43)], [2.6, 2, 1.4], 1.6, 3, 5, tip=True)
        m.seg(f'process_spur_{n}', Bn, (sd * 6.5, 35.8), (sd * 8, 33), 1.3, 1.1, 3, 4, tip=True)
    for k, (y, w) in enumerate(((38.5, 2.75), (42, 3.25), (46, 3), (50, 2.5), (54, 1.75))):
        y1 = (42, 46, 50, 54, 57.5)[k]
        m.box(f'blade_{k}', Bn, [-w, y, -1], [w, y1, 1], grad=(y, 3 + k * 0.3, y1, 4 + k * 0.3))
    m.seg('blade_tip', Bn, (0, 57), (0, 61), 1.8, 1.2, 5, 5, tip=True)
    m.box('spine_glow', Mw, [-0.45, 39, -1.1], [0.45, 55, 1.1], grad=(39, 2, 55, 4))
    for k, (x, y, s) in enumerate(((-1.75, 40.5, 1.2), (2, 43, 1.0), (-2.25, 47, 0.9))):
        diamond(m, f'drop_{k}_glow', Mw, (x * 2.2, y), s, 0.9, 3, 5, zc=0)
    return m


# ================================================================ 鍛冶の大鎚

def smith_hammer():
    """（伝説、2026-11-21 に作り直し・案 A）鍛冶の大鎚: 頭が金床そのもの。-x へ長い角、+x は広い打ち面が赤く焼け、打ち面の前に火花が散って浮かぶ。
    金床の腰はくびれ、足が柄に食い込む。柄に革の締めが二つ
    ※ 2026-11-21 に Blockbench で手を入れた（金床の角を六角の回転体（先へ細る円錐）のメッシュに替え、五つの火花を違う向きに傾けた。筆で角の上の縁を明るく下を暗く、金床の上の面の縁を明るく）。メッシュの形と UV はスクリプト（oth_gen.py）で作って MCP で置き、UV は書き出した .bbmodel にスクリプトで入れた。筆は塗る画素をスクリプト（oth_paint.py）で決めて paint_with_brush で 1 画素ずつ打った。正本は tools/models/x32/smith_hammer.bbmodel。この関数から prep し直すと手入れが消える"""
    m = M('smith_hammer', H, R)
    smith_grip(m, 19)
    for k, y in enumerate((10, 14)):
        m.box(f'strap_{k}', H, [-1.9, y, -1.9], [1.9, y + 1.3, 1.9], 3)
    m.box('foot', FI, [-4, 19, -3], [4, 21, 3], grad=(19, 3, 21, 4))
    m.box('waist', FI, [-2.75, 21, -2.25], [2.75, 23.5, 2.25], 3)
    m.box('top', FI, [-6, 23.5, -3.25], [6.5, 28, 3.25], grad=(23.5, 4, 28, 7))
    m.seg('horn', FI, (-6, 26.2), (-13, 26.8), 4, 2.6, 5, 7, tip=True)
    m.box('face', FI, [6.5, 23, -3.5], [8, 28.5, 3.5], grad=(23, 5, 28.5, 7))
    m.box('face_glow', R, [8, 23.75, -2.75], [8.6, 27.75, 2.75], grad=(23.75, 2, 27.75, 4))
    m.box('hardy_hole', FI, [1, 27.9, -0.8], [2.6, 28.3, 0.8], 1)
    wild_sparks(m, [(10.5, 28.5), (12, 25.5), (10.75, 22.5), (13.25, 29.75), (14, 23.5)], 1.3)
    return m


# ================================================================ 下僕の火かき棒

def thrall_poker():
    """（伝説、2026-11-21 に作り直し・案 B）下僕の火かき棒: 先が大きな焼き印。炎を三つ並べた紋の輪（表裏）が赤熱し、輪の芯が白く光る。
    焼き印の上下に熱の帯、棒のまわりに熾火の粒
    ※ 2026-11-21 に Blockbench で手を入れた（節をつないだ焼き印の輪を一本の輪の帯のメッシュに替え、ねじりの四角い段を 15〜75 度ずつ回して本当にねじれて見えるようにした。筆でねじりの段の縁に明暗）。メッシュの形と UV はスクリプト（oth_gen.py）で作って MCP で置き、UV は書き出した .bbmodel にスクリプトで入れた。筆は塗る画素をスクリプト（oth_paint.py）で決めて paint_with_brush で 1 画素ずつ打った。正本は tools/models/x32/thrall_poker.bbmodel。この関数から prep し直すと手入れが消える"""
    m = M('thrall_poker', H, R)
    top = poker_rod(m, 27)
    m.box('neck', FI, [-0.9, top, -0.9], [0.9, top + 3, 0.9], 5)
    m.box('heat_neck', R, [-0.95, top + 3, -0.95], [0.95, top + 5, 0.95], grad=(top + 3, 0, top + 5, 2))
    cy = top + 10
    m.arc('brand_ring', R, (0, cy), 4.5, 0, 360, 12, [1.1] * 12, 1, 2, 3)
    m.box('brand_core', R, [-1.25, cy - 1.25, -0.6], [1.25, cy + 1.25, 0.6], 5)
    for k, a in enumerate((90, 210, 330)):
        ra = math.radians(a)
        m.seg(f'flame_{k}', R, (math.cos(ra) * 1.25, cy + math.sin(ra) * 1.25), (math.cos(ra) * 3.75, cy + math.sin(ra) * 3.75), 1.6, 0.9, 3, 4, tip=True)
    m.box('stem', R, [-0.7, top + 5, -0.7], [0.7, cy - 4.2, 0.7], 2)
    embers(m, [(6, cy + 2), (-6, cy - 1), (5, cy + 6.5), (-4, cy + 6), (0.5, cy + 8)], 1)
    return m


# ================================================================ 番人の薙刀

def warden_glaive():
    """（伝説、2026-11-21 に作り直し・案 A）番人の薙刀: 幅の広い三日月の刃の背が、稲妻の形に三度折れる。刃の縁は稲妻の黄に光り（表裏）、
    受け口の銅の輪から黄の房が下がり、刃のまわりに稲妻の欠片が浮く
    ※ 2026-11-21 に Blockbench で手を入れた（節をつないだ三日月の刃を外の縁が薄い刃の帯のメッシュに、背の稲妻を折れ曲がる帯のメッシュに替え、五つの稲妻の欠片を違う向きに傾けた。筆で刃の縁を明るく、背の上の縁に明るい銅）。メッシュの形と UV はスクリプト（oth_gen.py）で作って MCP で置き、UV は書き出した .bbmodel にスクリプトで入れた。筆は塗る画素をスクリプト（oth_paint.py）で決めて paint_with_brush で 1 画素ずつ打った。正本は tools/models/x32/warden_glaive.bbmodel。この関数から prep し直すと手入れが消える"""
    m = M('warden_glaive', Cu, Sp)
    glaive_haft(m, 31)
    m.box('socket', Cu, [-2.1, 30, -2.1], [2.1, 34, 2.1], grad=(30, 3, 34, 5))
    m.arc('blade', G_, (-2, 44), 10, -70, 75, 7, [4, 4.5, 4.75, 4.75, 4.5, 4, 3], 1.4, 4, 7, tip=True)
    m.arc('edge_glow', Sp, (-2, 44), 12, -55, 60, 6, [0.9] * 6, 1.5, 3, 4)
    m.chain('back', Cu, [(-1.25, 34), (1.5, 38), (-0.5, 42), (2, 46.5), (0, 50.5), (1.25, 54)], [1.4, 1.3, 1.2, 1.1, 1], 1.6, 4, 5, tip=True)
    m.chain('tassel', Sp, [(2, 33), (3.25, 30.5), (3, 27)], [0.9, 0.8], 0.6, 3, 4, tip=False)
    m.box('tassel_glow', Sp, [2.25, 24.5, -0.6], [3.75, 27.25, 0.6], 4)
    bolts(m, [(13.5, 50), (14.5, 42), (12.5, 35), (8, 56.5), (15.5, 46.5)], 1.3)
    return m


# ================================================================ 旅の盾

def travel_shield():
    """（コモン、2026-11-21 に作り直し・案 A）旅の盾: 縦の樫の板を丸く切り、黒鉄の縁と十字の帯で留めた丸盾。真ん中に鉄の突起と鋲。裏に革の握り
    ※ 2026-11-21 に Blockbench で手を入れた（節をつないだ縁を一本の輪の帯のメッシュに、突起を八角の丸い回転体のメッシュに替えた。鉄が光る素材になっていたのを直して光をなくした（はじめの盾は光なし）。筆で縁に明暗、樫の板に木目の点）。メッシュの形と UV はスクリプト（oth_gen.py）で作って MCP で置き、UV は書き出した .bbmodel にスクリプトで入れた。筆は塗る画素をスクリプト（oth_paint.py）で決めて paint_with_brush で 1 画素ずつ打った。正本は tools/models/x32/travel_shield.bbmodel。この関数から prep し直すと手入れが消える"""
    m = M('travel_shield', FE, Mw)  # 光なし（光の素材に使っていない髄を指定して、鉄が光らないように）
    cy = 10
    for k, (x0, x1, t) in enumerate(((-11, -6.6, 4), (-6.6, -2.2, 5), (-2.2, 2.2, 4.5), (2.2, 6.6, 5.5), (6.6, 11, 4))):
        xm = max(abs(x0), abs(x1)) - 0.5
        dy = math.sqrt(max(0.0, 12 ** 2 - xm ** 2))
        m.box(f'plank_{k}', W, [x0 + 0.1, cy - dy, -2], [x1 - 0.1, cy + dy, 0], grad=(cy - dy, t - 1.5, cy + dy, t + 0.5))
    m.arc('rim', FE, (0, cy), 12.25, 0, 360, 12, 2, 1.4, 4, 6, zc=-1)
    m.box('band_h', FE, [-11.5, cy - 1, -2.5], [11.5, cy + 1, -1.75], 5)
    m.box('band_v', FE, [-1, cy - 11.5, -2.5], [1, cy + 11.5, -1.75], 5)
    for k, (x, y) in enumerate(((-8, cy), (8, cy), (0, cy - 8), (0, cy + 8))):
        m.box(f'rivet_{k}', FE, [x - 0.6, y - 0.6, -2.9], [x + 0.6, y + 0.6, -2.4], 8)
    m.box('boss', FE, [-2.75, cy - 2.75, -3.75], [2.75, cy + 2.75, -2.25], grad=(cy - 2.75, 4, cy + 2.75, 7))
    m.box('boss_top', FE, [-1.5, cy - 1.5, -4.5], [1.5, cy + 1.5, -3.75], 8)
    m.box('handle', 'hide', [-1.5, cy - 4.5, 0], [1.5, cy + 4.5, 2.5], grad=(cy - 4.5, 2, cy + 4.5, 4))
    m.box('brace_0', W, [-9, cy - 7, 0], [9, cy - 5.5, 1], 3)
    m.box('brace_1', W, [-9, cy + 5.5, 0], [9, cy + 7, 1], 3)
    return m


ALL = [captain_pike, smith_hammer, thrall_poker, warden_glaive, travel_shield]

if __name__ == '__main__':
    want = set(sys.argv[1:])
    for fn in ALL:
        if not want or fn.__name__ in want:
            fn().build('.')
