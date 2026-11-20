# -*- coding: utf-8 -*-
"""野・雲（look5・個性版）。雲の上の浮島で拾う 6 品（叙）。
色: 雲の白銀（nimbus 青みの白〜灰青の 1 系統）、差し色は朝焼けの淡い金（gilt）、光は空の青（azure）を品ごとに一か所。
雲を固めた白い金属と、鳥の羽の意匠。丸い雲のふくらみ（段々の箱）を形の芯にする。
"""
import math
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import lib  # noqa: E402
from lib import Model  # noqa: E402

lib.RAMPS.update({
    'nimbus': ['#2a3446', '#3a465c', '#4c5a72', '#606e88', '#76849e', '#8e9cb4', '#a8b4c8', '#c2ccdc', '#dce2ec', '#f2f5fa'],
    'gilt': ['#5a4420', '#86682e', '#b08e44', '#d2b264', '#ecd290', '#fcf0c8'],
    'sky': ['#101c3c', '#18295a', '#22397c', '#2e4c9c', '#4064bc', '#5880d4'],
    'azure': ['#0c3a7a', '#1660b0', '#2a8ce0', '#5cb8f8', '#a4dcff', '#e6f6ff'],
})
N, G, Z = 'nimbus', 'gilt', 'azure'


def M(name):
    return Model(name, accent=G, glowmat=Z)


def ribbon_grip(m, y0, y1, r=1.5):
    """白い布を斜めに巻いた握り。上下は金の細い輪"""
    m.box('grip', N, [-r, y0, -r], [r, y1, r], grad=(y0, 4, y1, 6), wrap=(y0, y1))
    m.box('gilt_d', G, [-r - 0.3, y0 - 0.75, -r - 0.3], [r + 0.3, y0, r + 0.3], 3)
    m.box('gilt_u', G, [-r - 0.3, y1, -r - 0.3], [r + 0.3, y1 + 0.75, r + 0.3], 4)


def puff(m, name, c, w, h, d, t=7, mat=N):
    """雲のふくらみ: 幅の違う 3 段の箱を重ねて丸く見せる"""
    x, y = c
    m.box(f'{name}_a', mat, [x - w / 2, y, -d / 2], [x + w / 2, y + h, d / 2], t)
    m.box(f'{name}_b', mat, [x - w / 2 + 0.75, y - 0.75, -d / 2 + 0.4], [x + w / 2 - 0.75, y, d / 2 - 0.4], t - 2)
    m.box(f'{name}_c', mat, [x - w / 2 + 0.75, y + h, -d / 2 + 0.4], [x + w / 2 - 0.75, y + h + 0.75, d / 2 - 0.4], t + 1)


def diamond(m, name, mat, c, s, d, t0, t1, zc=0.0):
    h = s / (2 * math.sqrt(2))
    m.seg(name, mat, (c[0] - h, c[1] - h), (c[0] + h, c[1] + h), s, d, t0, t1, over=0, zc=zc)


def mirror(p, sign):
    return (p[0], 15 + sign * (p[1] - 15))


def strings(m, top, bot):
    for key, pull in (('0', 0), ('p0', 2.4), ('p1', 6.8), ('p2', 11.6)):
        if pull == 0:
            m.box(f'string_{key}', 'string', [top[0] - 0.5, bot[1], -0.5], [top[0] + 0.5, top[1], 0.5], 3)
        else:
            mid = (top[0] - pull, 15)
            m.seg(f'string_{key}_u', 'string', mid, top, 1, 0.5, 3, 3, over=0.2)
            m.seg(f'string_{key}_d', 'string', bot, mid, 1, 0.5, 3, 3, over=0.2)


def wild_skylark_bow():
    """雲雀の弓（ヒロイック）: 弓腕そのものが雲雀の翼。白い翼の背の縁は金。翼の先からは風切羽が扇に大きく開き
    （板の絵。羽軸は灰、縁は空の青、羽先は金）、翼の先の羽の付け根を金の帯が束ねる。握りは空の青に金の帯"""
    m = M('wild_skylark_bow')
    m.box('grip', 'sky', [10.25, 9.5, -2], [13.75, 20.5, 2], grad=(9.5, 2, 20.5, 4), wrap=(9.5, 20.5))
    for n, y in (('u', 20.25), ('d', 8.25)):
        m.box(f'band_{n}', G, [9.75, y, -2.4], [14.25, y + 1.5, 2.4], 4)
    arm = [(12, 21), (15, 27), (16, 33), (14.5, 39), (11, 43.5), (6.5, 45.5), (3.25, 45.5)]
    cv = lib.Canvas(-10, 38, 24, 64)
    # 翼の先の縁（11,43.5)→(3.25,45.5) に沿って付け根を並べ、外ほど長く外へ倒す
    quills = []
    for k in range(6):
        f = k / 5
        a = (11 - 7.5 * f, 44 + 1.6 * f)
        ang = math.radians(62 + 82 * f)
        ln = 7.5 + 5 * math.sin(math.pi * (0.25 + 0.6 * f))
        quills.append((a, (a[0] + ln * math.cos(ang), a[1] + ln * math.sin(ang)), 3.4 - 0.2 * abs(k - 3)))

    def f(x, y, cur):
        for k, (a, b, w) in reversed(list(enumerate(quills))):
            d, t = lib.near_line((x, y), [a, b])
            ww = w * (0.75 + 0.35 * math.sin(math.pi * min(t, 1) * 0.9))
            if d < ww / 2 and t < 1 - 0.35 * (d / (ww / 2)) ** 2:
                if t > 0.84:
                    return (G, 4)
                if d < 0.3 and t < 0.78:
                    return (N, 6)
                if d > ww / 2 - 0.5:
                    return ('sky', 5)
                return (N, 9) if k % 2 else (N, 8)
        return cur
    cv.paint(f)
    for sign, n in ((1, 'u'), (-1, 'd')):
        P = lambda q, s=sign: mirror(q, s)
        m.chain(f'wing_{n}', N, [P(q) for q in arm], [6, 7, 6, 4.5, 3, 1.8], 1.4, 6, 9, tip=False)
        m.chain(f'lead_{n}', G, [P(q) for q in lib.offset_line(arm, 3.1)[1:5]], [0.8, 0.8, 0.7], 1.45, 3, 5, tip=False)
        m.seg(f'quill_band_{n}', G, P((11, 43.75)), P((4, 45.5)), 1.2, 1.6, 4, 5, over=0.3)
        cv.put(m, f'quills_{n}', at=0.0, mirror15=sign < 0)
    strings(m, (3.25, 45.5), (3.25, -15.5))
    return m

def wild_cloudhymn_bell_staff():
    """（ヒロイック、2026-11-21 に作り直し・案 B）天の鐘楼: 金の半円の門に鐘が三つ（中の大鐘が光る）。門の足に雲、空の青の音符が三つ浮く
    ※ 2026-11-21 に Blockbench で手を入れた（節をつないだ金の門を一本のなめらかな半円の帯のメッシュに、三つの鐘を六角の釣鐘のメッシュに替え、空の青の舌を鐘の口から下へのぞかせ、音符を菱形に傾け、鐘の口の照りと門の彫りの点を描いた）。メッシュの UV はスクリプトで書き出した .bbmodel に入れた。正本は tools/models/x32/wild_cloudhymn_bell_staff.bbmodel。この関数から prep し直すと手入れが消える"""
    m = M('wild_cloudhymn_bell_staff')
    ribbon_grip(m, -14, 2)
    m.box('shaft', N, [-1.2, 2.75, -1.2], [1.2, 30, 1.2], grad=(2.75, 4, 30, 6))
    m.box('beam', G, [-8, 29.5, -1.1], [8, 31, 1.1], 4)
    m.arc('arch', G, (0, 30.5), 7.2, 5, 175, 8, 1.6, 1.0, 3, 5)
    m.box('hanger_m', G, [-0.3, 35.5, -0.3], [0.3, 37.8, 0.3], 3)
    m.box('bell_m', G, [-2.2, 31.8, -2.2], [2.2, 35.5, 2.2], grad=(31.8, 2, 35.5, 4))
    m.box('bell_m_lip', G, [-2.6, 31, -2.6], [2.6, 31.8, 2.6], 3)
    m.box('tongue_glow', Z, [-0.6, 31.2, -0.6], [0.6, 32.4, 0.6], 4)
    for sd, n in ((-1, 'l'), (1, 'r')):
        x = sd * 4.8
        m.box(f'hanger_{n}', G, [x - 0.3, 27.5, -0.3], [x + 0.3, 29.5, 0.3], 3)
        m.box(f'bell_{n}', G, [x - 1.6, 25, -1.6], [x + 1.6, 27.5, 1.6], grad=(25, 2, 27.5, 4))
        m.box(f'bell_{n}_lip', G, [x - 1.9, 24.4, -1.9], [x + 1.9, 25, 1.9], 3)
        puff(m, f'foot_{n}', (sd * 7.5, 28.5), 3.5, 2.5, 3.5, 7)
    for k, (x, y) in enumerate(((-9.5, 37), (9, 39.5), (0.5, 41))):
        m.box(f'note_{k}', Z, [x - 0.6, y - 0.6, -0.6], [x + 0.6, y + 0.6, 0.6], 4)
    return m


def wild_prism_staff():
    """（ヒロイック、2026-11-21 に作り直し・案 A）大プリズム: 背丈ほど長い三角柱の水晶を金の三本爪が支え、まわりに小さな水晶のかけらが四つ浮く。芯が空の青
    ※ 2026-11-21 に Blockbench で手を入れた（四角い水晶と先を一本の六角の結晶のメッシュ（先が尖る）に替え、光る芯は結晶の表裏の稜にわずかに出し、四つのかけらをそれぞれ違う向きに傾け、結晶の面を明暗交互に塗った）。メッシュの UV はスクリプトで書き出した .bbmodel に入れた。正本は tools/models/x32/wild_prism_staff.bbmodel。この関数から prep し直すと手入れが消える"""
    m = M('wild_prism_staff')
    ribbon_grip(m, -14, 2, 1.3)
    m.box('shaft', N, [-1.1, 2.75, -1.1], [1.1, 29, 1.1], grad=(2.75, 4, 29, 6))
    m.box('cup', G, [-2.4, 28.5, -2.4], [2.4, 30.2, 2.4], 4)
    for k, (x, z) in enumerate(((-2.6, 0), (1.4, -2.2), (1.4, 2.2))):
        m.seg(f'prong_{k}', G, (x * 0.6, 29.6), (x * 1.1, 35), 0.9, 0.9, 3, 5, tip=True, zc=z)
    m.seg('prism', N, (0, 30), (0, 45), 5, 2.6, 7, 9, over=0)
    m.seg('prism_core_glow', Z, (0, 31.5), (0, 43.5), 1.6, 2.8, 3, 5, over=0)
    m.seg('prism_tip', N, (0, 45), (0, 49), 3.6, 2, 8, 9, tip=True, over=0.2)
    for k, (x, y, s) in enumerate(((-6.5, 41, 2.6), (6, 37.5, 2.2), (-5.5, 34, 1.8), (5.5, 45, 2))):
        diamond(m, f'float_shard_{k}', N, (x, y), s, 0.9, 7, 9)
    return m


def wild_cloudcleaver_greatsword():
    """（ヒロイック、2026-11-21 に作り直し・案 B）翼の大剣: 細めの白銀の刃を、鍔から立ち上がる大きな二枚の翼が包むように挟む。翼の外の縁から風切羽が三本ずつ跳ねる
    ※ 2026-11-21 に Blockbench で手を入れた（節をつないだ翼と金の縁を、根元が厚く先へ細って刃へ巻き戻る一枚の帯のメッシュに替え（金の縁は塗りにした）、刃先を四角錐にし、羽の筋を描いた）。メッシュの UV はスクリプトで書き出した .bbmodel に入れた。正本は tools/models/x32/wild_cloudcleaver_greatsword.bbmodel。この関数から prep し直すと手入れが消える"""
    m = M('wild_cloudcleaver_greatsword')
    ribbon_grip(m, -8, 8, 1.6)
    m.box('pommel', G, [-1.9, -10.6, -1.9], [1.9, -8.75, 1.9], 4)
    m.box('guard', G, [-3.6, 8.75, -1.8], [3.6, 11, 1.8], grad=(8.75, 2, 11, 4))
    m.box('blade', N, [-2.2, 11, -0.9], [2.2, 42, 0.9], grad=(11, 5, 42, 8))
    m.seg('tip', N, (0, 41.5), (0, 47.5), 4.4, 0.85, 8, 9, tip=True, over=0)
    for sd, n in ((-1, 'l'), (1, 'r')):
        m.chain(f'wing_{n}', N, [(sd * 2.6, 11), (sd * 6.2, 15), (sd * 7.6, 22), (sd * 6.6, 29), (sd * 3.6, 34)], [4, 3.6, 3, 2], 0.7, 6, 9, tip=True)
        m.chain(f'lead_{n}', G, [(sd * 3.4, 10.5), (sd * 7.6, 15), (sd * 9, 22), (sd * 8, 29)], [1, 0.9, 0.8], 0.8, 3, 5, tip=False)
        for j, (y, ln) in enumerate(((16, 4.5), (21, 5.5), (26, 4.5))):
            m.seg(f'quill_{n}_{j}', N, (sd * 8, y), (sd * (8 + ln), y - 3), 1.4, 0.5, 8, 9, tip=True)
    m.box('gem_glow', Z, [-0.8, 12, -1.05], [0.8, 13.6, 1.05], 4)
    return m


def wild_skysplit_axe():
    """（ヒロイック、2026-11-21 に作り直し・案 A）天割りの大半月: 柄より大きい半月の刃に、上から下へ稲妻の切れ込みが走って空の青に光る。背に雲、柄頭に金の羽
    ※ 2026-11-21 に Blockbench で手を入れた（節をつないだ半月と板を、背が厚く刃先が薄い一枚の半月のメッシュに替え、稲妻の切れ込みを刃から出過ぎない厚みにし、刃先の白・背の影・金の点を描いた）。メッシュの UV はスクリプトで書き出した .bbmodel に入れた。正本は tools/models/x32/wild_skysplit_axe.bbmodel。この関数から prep し直すと手入れが消える"""
    m = M('wild_skysplit_axe')
    ribbon_grip(m, -6, 6)
    m.seg('tail_feather', G, (0, -6.75), (-1.75, -10.5), 1.75, 0.6, 3, 5, tip=True)
    m.box('shaft', N, [-1.25, 6.75, -1.25], [1.25, 28, 1.25], grad=(6.75, 4, 28, 6))
    m.arc('edge', N, (2, 21), 10, -75, 75, 7, 3.4, 1.0, 6, 9)
    m.box('web', N, [1, 15, -0.95], [10.4, 27, 0.95], grad=(15, 5, 27, 7))
    m.box('web_d', N, [1, 13, -0.95], [6.5, 15, 0.95], 6)
    m.box('web_u', N, [1, 27, -0.95], [6.5, 29, 0.95], 6)
    m.chain('rift_glow', Z, [(11, 26.5), (7.5, 23), (10, 20.5), (6.5, 16)], 1.2, 1.25, 3, 5, tip=False)
    puff(m, 'back', (-3, 18.5), 4, 5, 3.4, 6)
    m.box('cap', G, [-1.6, 28, -1.6], [1.6, 29.4, 1.6], 4)
    return m


def wild_cloudveil_claw():
    """（ヒロイック、2026-11-21 に作り直し・案 B）鷹の足: 籠手から四本の太い鉤爪が扇に開いて内へ巻く。籠手の下に金の羽の房、甲に空の青の目
    ※ 2026-11-21 に Blockbench で手を入れた（節をつないだ四本の鉤爪を、内の刃が薄く背が厚い反った帯のメッシュに替え、爪先を濃い鋼に塗り、金の羽の房を前後・左右へ開くよう傾けた）。メッシュの UV はスクリプトで書き出した .bbmodel に入れた。正本は tools/models/x32/wild_cloudveil_claw.bbmodel。この関数から prep し直すと手入れが消える"""
    m = M('wild_cloudveil_claw')
    ribbon_grip(m, -7, 3, 1.4)
    m.box('gauntlet', N, [-3, 3.75, -2.6], [3, 9, 2.6], grad=(3.75, 5, 9, 7))
    m.box('eye_glow', Z, [-0.8, 5.6, 2.6], [0.8, 7.2, 2.9], 4)
    m.box('eye_glow_b', Z, [-0.8, 5.6, -2.9], [0.8, 7.2, -2.6], 4)
    for k, sx in enumerate((-1.5, -0.5, 0.5, 1.5)):
        x0 = sx * 1.5
        m.chain(f'talon_{k}', N, [(x0, 8.5), (sx * 3.4, 14.5), (sx * 5, 20), (sx * 3.6, 24.5)], [1.8, 1.4, 1.0], 0.75, 6, 9, tip=True)
    for k, x in enumerate((-2.4, -1.2, 0, 1.2, 2.4)):
        m.seg(f'plume_{k}', G, (x, 4.2), (x * 1.6, -0.5 - abs(x) * 0.4), 1.2, 0.5, 3, 5, tip=True)
    return m


ALL = [wild_skylark_bow, wild_cloudhymn_bell_staff, wild_prism_staff, wild_cloudcleaver_greatsword,
       wild_skysplit_axe, wild_cloudveil_claw]

if __name__ == '__main__':
    want = set(sys.argv[1:])
    for fn in ALL:
        if not want or fn.__name__ in want:
            fn().build('.')
