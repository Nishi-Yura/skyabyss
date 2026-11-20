# -*- coding: utf-8 -*-
"""野・海岸（look5・個性版）。浜と磯で拾う 12 品（稀〜英）。
色: 潮に晒された流木と錆びた船釘（drift 青灰の 1 系統）、差し色は船の綱と砂（rope 淡い黄土）、光は夜光虫（foam 青緑）を品ごとに一か所。
漁師・船大工・浜の貝拾いの道具がもと。柄は綱を巻き、結び目を一つ残す。
"""
import math
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import lib  # noqa: E402
from lib import Model  # noqa: E402

lib.RAMPS.update({
    'drift': ['#141a1e', '#1e262c', '#2a343c', '#38444e', '#485662', '#5a6a76', '#70808c', '#8a98a2', '#a8b4ba', '#ccd4d6'],
    'rope': ['#4a3a22', '#76603a', '#a08654', '#c4aa74', '#e0cc9c', '#f6ead0'],
    'foam': ['#0a4a52', '#127882', '#20a8b0', '#4cd4d4', '#9cf0ea', '#e0fffa'],
})
D, R, F = 'drift', 'rope', 'foam'


def M(name):
    return Model(name, accent=R, glowmat=F)


def rope_grip(m, y0, y1, r=1.5):
    """綱を巻いた流木の握り。下の端に結び目のこぶ"""
    m.box('grip', D, [-r, y0, -r], [r, y1, r], grad=(y0, 3, y1, 5), wrap=(y0, y1))
    m.box('knot', R, [-r - 0.5, y0 - 1.5, -r - 0.5], [r + 0.5, y0, r + 0.5], grad=(y0 - 1.5, 2, y0, 4))


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


def coast_haft(m, top, r=1.3):
    """浜の長柄（鎚・杖）: 綱巻きの握りと潮に晒された流木の柄。柄頭は錆びた船釘を打った木の口金"""
    rope_grip(m, -12, 2, 1.6)
    m.box('shaft', D, [-r, 2, -r], [r, top, r], grad=(2, 4, top, 6))
    m.box('butt', D, [-2.0, -15.5, -2.0], [2.0, -13.4, 2.0], grad=(-15.5, 3, -13.4, 6))
    m.box('butt_nail', D, [-0.7, -16.3, -0.7], [0.7, -15.4, 0.7], 8)


def coast_hilt(m, top=7):
    """浜の剣の握り: 綱巻き、柄頭は丸めた流木の瘤"""
    rope_grip(m, -7, top, 1.5)
    m.box('pommel', D, [-2.2, -10.5, -1.6], [2.2, -8.4, 1.6], grad=(-10.5, 3, -8.4, 6))


# ---------------------------------------------------------------- 鎚・木槌

def wild_crab_hammer():
    """大蟹の鋏鎚（レア、2026-11-20 に作り直し・案 A）: 柄の先に大蟹の鋏を一つ。太い掌が鎚の頭（+x が打つ面）、
    上へ二本の指が大きく開き、指の内側に歯が並ぶ。-x の甲にとげ。指の付け根に夜光虫
    ※ 2026-11-20 に Blockbench で手を入れた（二本の指を曲がって細る帯のメッシュに替え、甲のとげを尖った四角錐にし、指と掌に甲羅の斑点を描いた）。正本は tools/models/x32/wild_crab_hammer.bbmodel。この関数から prep し直すと手入れが消える"""
    m = M('wild_crab_hammer')
    coast_haft(m, 27)
    m.box('palm', D, [-5, 26, -3.4], [7, 35, 3.4], grad=(26, 4, 35, 7))
    m.box('palm_rim', D, [-5.4, 25.2, -3.8], [7.4, 26.6, 3.8], 3)
    m.box('face', D, [7, 26.4, -3.0], [8.6, 34.6, 3.0], 6)
    for k, y in enumerate((28, 31.5)):
        m.seg(f'spike_back_{k}', D, (-4.8, y), (-7.6, y + 1.0), 1.8, 1.4, 5, 7, tip=True)
    m.chain('finger_fix', D, [(4.8, 34.5), (7.2, 39), (6.6, 44), (4.2, 47)], [3.8, 3.0, 2.0], 2.2, 5, 7, tip=True)
    m.chain('finger_mov', D, [(-1.6, 34.5), (-3.4, 39), (-2.4, 43.5)], [3.2, 2.4], 2.0, 5, 7, tip=True)
    for k, (x, y) in enumerate(((5.2, 38.5), (5.4, 41.5))):
        m.seg(f'tooth_fix_{k}', D, (x, y), (x - 1.6, y + 0.6), 1.1, 1.0, 7, 8, tip=True)
    m.seg('tooth_mov_0', D, (-2.0, 38.5), (-0.6, 39.4), 1.1, 1.0, 7, 8, tip=True)
    m.box('joint_glow', F, [0.6, 34.6, -1.2], [3.2, 35.8, 1.2], 4)
    return m


def wild_shipwright_maul():
    """船大工の木槌（アンコモン、2026-11-20 に作り直し・案 C）: 古い帆柱の切れ端を頭にした大槌。両端に鉄の口金
    （表と裏に船釘の頭）、真ん中に綱が三重に巻かれ、帆を張る鉄の輪が上に一つ。光なし
    ※ 2026-11-20 に Blockbench で手を入れた（綱の三重巻きを斜めに傾けて巻きつけた形にし、帆の輪の継ぎ目を詰めてなめらかにし、帆柱に木目と節を描いた）。正本は tools/models/x32/wild_shipwright_maul.bbmodel。この関数から prep し直すと手入れが消える"""
    m = M('wild_shipwright_maul')
    coast_haft(m, 27)
    m.box('mast', D, [-8, 26, -3.4], [8, 35, 3.4], grad=(26, 4, 35, 7))
    m.box('mast_round', D, [-8, 27, -3.9], [8, 34, 3.9], grad=(27, 4, 34, 6))
    for sd, n in ((-1, 'l'), (1, 'r')):
        x0, x1 = sorted((sd * 8, sd * 9.6))
        m.box(f'cap_{n}', D, [x0, 25.4, -4.2], [x1, 35.6, 4.2], 2)
        for zs, zn in ((1, 'f'), (-1, 'b')):
            xc = sd * 6.6
            z0 = 3.9 if zs > 0 else -4.3
            m.box(f'nail_{n}{zn}', D, [xc - 0.6, 29.9, z0], [xc + 0.6, 31.1, z0 + 0.4], 8)
    for k, x in enumerate((-2.2, -0.8, 0.6)):
        m.box(f'wrap_{k}', R, [x, 25.6, -4.2], [x + 1.1, 35.4, 4.2], 3 + (k % 2))
    m.box('ring_post', D, [3.8, 35, -0.5], [4.8, 36.4, 0.5], 3)
    m.arc('ring', D, (4.3, 37.6), 1.6, 0, 360, 8, 0.7, 0.7, 2, 4)
    return m


# ---------------------------------------------------------------- 剣・大剣・短剣

def wild_dune_sword():
    """砂潜りの曲刀（レア、2026-11-20 に作り直し・案 A）: 先へ広がり +x へ大きく反る片刃。背に砂鮫の鰭が二枚。
    鍔は両端が下へ巻く流木。刃の縁に夜光虫の筋（表裏に抜ける）
    ※ 2026-11-20 に Blockbench で手を入れた（刃を背が厚く刃先が薄い反ったメッシュに替え、背の鰭を尖った三角にし、刃に背から刃先への濃淡を描いた）。正本は tools/models/x32/wild_dune_sword.bbmodel。この関数から prep し直すと手入れが消える"""
    m = M('wild_dune_sword')
    coast_hilt(m)
    m.chain('guard', D, [(-5.6, 5.6), (-4.4, 7.6), (4.4, 7.6), (5.6, 5.6)], [2.0, 2.4, 2.0], 3.0, 4, 6)
    m.chain('blade', D, [(0, 7.5), (0.6, 16), (2.4, 25), (5.4, 33), (9.4, 39)], [5.0, 6.0, 6.6, 5.8], 1.0, 4, 7, tip=True)
    m.seg('fin_a', D, (-1.6, 16), (-5.2, 21), 3.2, 0.8, 5, 7, tip=True)
    m.seg('fin_b', D, (-0.6, 24), (-3.4, 27.6), 2.4, 0.8, 5, 7, tip=True)
    m.chain('edge_glow', F, [(2.8, 11), (4.4, 19), (7.0, 27), (10.0, 33)], [0.9, 0.9, 0.8], 1.2, 3, 4, tip=False)
    return m


def wild_oar_blade():
    """渡し守の櫂剣（アンコモン、2026-11-20 に作り直し・案 A）: 渡し舟の櫂がそのまま大剣。長い柄の先が平たく幅広の水かき、
    縁を鉄で打ち直して刃にした。付け根に綱の結びと櫂受けの鉄の輪。光なし
    ※ 2026-11-20 に Blockbench で手を入れた（水かきを縁が薄く中央が厚い一枚のメッシュに替え、流木の木目と節と先の擦れを描いた）。正本は tools/models/x32/wild_oar_blade.bbmodel。この関数から prep し直すと手入れが消える"""
    m = M('wild_oar_blade')
    rope_grip(m, -9, 8, 1.6)
    m.box('pommel', D, [-2.2, -12.5, -2.2], [2.2, -10.4, 2.2], grad=(-12.5, 3, -10.4, 6))
    m.box('loom', D, [-1.5, 8, -1.5], [1.5, 18, 1.5], grad=(8, 4, 18, 6))
    m.box('collar', D, [-2.1, 10, -2.1], [2.1, 11.4, 2.1], 2)
    m.box('tie', R, [-2.0, 16, -2.0], [2.0, 18.6, 2.0], 4)
    m.chain('blade', D, [(0, 17.5), (0, 26), (0, 37), (0, 46)], [5.4, 9.0, 9.6], 1.1, 4, 6, tip=False)
    m.box('rim_l', D, [-5.4, 25, -0.7], [-4.2, 46, 0.7], 8)
    m.box('rim_r', D, [4.2, 25, -0.7], [5.4, 46, 0.7], 8)
    m.box('rim_top', D, [-5.0, 45.6, -0.7], [5.0, 47.4, 0.7], 8)
    m.box('rib', D, [-0.6, 19, -0.8], [0.6, 40, 0.8], 5)
    return m


def wild_hermit_knife():
    """宿借りの隠し刃（レア、2026-11-20 に作り直し・案 C）: 長い角のような角貝の殻そのものが刃。根元が太く先が細く、
    輪の筋が段になって並び、少しずつ反る。殻の口の縁が夜光虫で光る（ぐるりと一周）
    ※ 2026-11-20 に Blockbench で手を入れた（角貝の輪を一段ずつ傾けてなめらかに反らせ、先を反りに合わせ、殻に斑点と成長線を描いた）。正本は tools/models/x32/wild_hermit_knife.bbmodel。この関数から prep し直すと手入れが消える"""
    m = M('wild_hermit_knife')
    rope_grip(m, -4, 3, 1.3)
    m.box('pommel', D, [-1.8, -6.6, -1.6], [1.8, -5.2, 1.6], 5)
    m.box('collar', D, [-2.5, 3, -2.5], [2.5, 5.4, 2.5], grad=(3, 3, 5.4, 5))
    m.box('mouth_glow', F, [-2.7, 5.2, -2.7], [2.7, 5.9, 2.7], 4)
    y = 5.8
    x = 0.0
    for k in range(8):
        w = 4.6 - k * 0.48
        h = 3.0 - k * 0.12
        x = k * k * 0.06
        m.box(f'ring_{k}', R, [x - w / 2, y, -w / 2], [x + w / 2, y + h + 0.1, w / 2], grad=(y, 2, y + h, 4))
        y += h
    m.seg('tip', R, (x, y - 0.2), (x + 0.6, y + 4.0), 1.2, 0.9, 4, 5, tip=True)
    return m


def wild_shore_dagger():
    """磯走りの針（アンコモン、2026-11-20 に作り直し・案 B）: 大きな魚の背骨そのものが刃。魚の頭骨が鍔、背骨の節が並び、
    両側へ細い肋骨が斜め上へ伸びる（先ほど短い）。先は尾骨。光なし
    ※ 2026-11-20 に Blockbench で手を入れた（尾骨を二股の尾びれのメッシュに替え、ひれの筋を描き、頭骨の表と裏に目の穴とえらの線を描いた）。正本は tools/models/x32/wild_shore_dagger.bbmodel。この関数から prep し直すと手入れが消える"""
    m = M('wild_shore_dagger')
    rope_grip(m, -4, 4, 1.3)
    m.box('pommel', D, [-1.8, -6.6, -1.6], [1.8, -5.2, 1.6], 5)
    m.box('skull', R, [-2.8, 4, -2.2], [2.8, 8, 2.2], grad=(4, 3, 8, 5))
    m.box('jaw', R, [-2.2, 3.4, -1.8], [2.2, 4.4, 1.8], 2)
    y = 8
    for k in range(6):
        m.box(f'vert_{k}', R, [-1.1, y, -1.0], [1.1, y + 3.1, 1.0], 4 + (k % 2))
        for sd, n in ((-1, 'l'), (1, 'r')):
            L = 3.6 - k * 0.45
            m.seg(f'rib_{n}{k}', R, (sd * 0.8, y + 1.2), (sd * (0.8 + L), y + 2.9), 0.8, 0.6, 4, 5, tip=True)
        y += 3
    m.seg('tail', R, (0, y - 0.2), (0, y + 4.2), 2.0, 0.7, 4, 5, tip=True)
    return m


# ---------------------------------------------------------------- 杖・鈴杖

def wild_lure_staff():
    """漁り火の杖（レア、2026-11-20 に作り直し・案 A）: 釣り竿のようにしなる杖。竿に糸の輪が三つ、先から綱が下がって
    夜光虫の漁り火の籠を吊るす（籠は四本の鉄の柱と上下の板）
    ※ 2026-11-20 に Blockbench で手を入れた（竿を、しなって先細りする一本のメッシュに替え、流木の筋と糸巻きを描いた）。正本は tools/models/x32/wild_lure_staff.bbmodel。この関数から prep し直すと手入れが消える"""
    m = M('wild_lure_staff')
    coast_haft(m, 20, 1.3)
    m.chain('rod', D, [(0, 19), (0.8, 30), (2.8, 40), (6.4, 47.5)], [2.2, 1.8, 1.3], 1.1, 4, 6, tip=False)
    for k, (x, y) in enumerate(((0.4, 26), (1.7, 35), (4.2, 43))):
        m.box(f'guide_{k}', R, [x - 1.0, y, -1.0], [x + 1.0, y + 0.9, 1.0], 4)
    m.box('line', R, [6.0, 40.4, -0.25], [6.5, 47, 0.25], 3)
    m.box('cage_top', D, [3.6, 39.2, -2.6], [8.8, 40.6, 2.6], 4)
    m.box('cage_cap', D, [5.2, 40.6, -0.9], [7.2, 41.4, 0.9], 6)
    m.box('cage_glow', F, [4.4, 33.4, -1.8], [8.0, 39.2, 1.8], 4)
    for k, (dx, dz) in enumerate(((-2.4, -2.4), (1.9, -2.4), (-2.4, 1.9), (1.9, 1.9))):
        m.box(f'cage_bar_{k}', D, [6.2 + dx, 32.8, dz], [6.2 + dx + 0.5, 39.4, dz + 0.5], 2)
    m.box('cage_base', D, [3.6, 31.8, -2.6], [8.8, 33.4, 2.6], 4)
    m.seg('cage_drip', D, (6.2, 31.9), (6.2, 30.0), 1.2, 1.2, 5, 6, tip=True)
    return m


def wild_tidewatch_staff():
    """潮見の杖（アンコモン、2026-11-20 に作り直し・案 C）: 杖の先に羅針の輪。外の流木の輪と内の綱の輪、
    十字の桟、東西南北に突き出す針（北だけ長い）。真ん中に方位の菱。光なし
    ※ 2026-11-20 に Blockbench で手を入れた（羅針の内外の輪の継ぎ目を詰めてなめらかにし、東西北の針を尖った四角錐にした）。正本は tools/models/x32/wild_tidewatch_staff.bbmodel。この関数から prep し直すと手入れが消える"""
    m = M('wild_tidewatch_staff')
    coast_haft(m, 41.6)
    c = 47.5
    m.arc('ring_o', D, (0, c), 6.4, 0, 360, 14, 1.4, 1.2, 4, 6)
    m.arc('ring_i', R, (0, c), 4.0, 0, 360, 12, 0.9, 0.9, 3, 5)
    m.box('spoke_h', D, [-6.0, c - 0.45, -0.45], [6.0, c + 0.45, 0.45], 3)
    m.box('spoke_v', D, [-0.45, c - 6.0, -0.45], [0.45, c + 6.0, 0.45], 3)
    for k, a in enumerate((0, 90, 180, 270)):
        co, si = math.cos(math.radians(a)), math.sin(math.radians(a))
        L = 11.0 if a == 90 else 9.0
        m.seg(f'needle_{k}', D, (co * 6.6, c + si * 6.6), (co * L, c + si * L), 2.0, 1.0, 5, 8, tip=True)
    diamond(m, 'rose', R, (0, c), 3.4, 1.4, 4, 6)
    return m


def wild_fogbell_bell_staff():
    """霧鐘の鈴杖（レア、2026-11-20 に作り直し・案 A）: 港の霧鐘。杖の先が L 字に折れ、斜めの支えで留めた横木から
    綱で鐘が下がる。鐘の舌が夜光虫で光り、鐘の口から下へのぞく
    ※ 2026-11-20 に Blockbench で手を入れた（鐘を裾の広がった六角の鐘のメッシュに替え（口の奥は暗く）、帯と緑青を描き、支え木を鐘から離した）。正本は tools/models/x32/wild_fogbell_bell_staff.bbmodel。この関数から prep し直すと手入れが消える"""
    m = M('wild_fogbell_bell_staff')
    coast_haft(m, 45)
    m.box('arm', D, [-1.4, 43, -1.4], [10, 45.4, 1.4], grad=(43, 4, 45.4, 6))
    m.box('arm_cap', D, [-1.8, 45, -1.8], [1.8, 46.4, 1.8], 3)
    m.seg('brace', D, (0.4, 36), (6.2, 43.4), 1.4, 1.2, 4, 6)
    m.box('cord', R, [7.2, 40.6, -0.35], [7.9, 43.2, 0.35], 3)
    m.box('bell_top', D, [5.5, 39.0, -1.9], [9.6, 40.8, 1.9], 5)
    m.box('bell', D, [4.6, 32.6, -2.9], [10.5, 39.2, 2.9], grad=(32.6, 3, 39.2, 6))
    m.box('lip', D, [3.9, 31.6, -3.6], [11.2, 33.0, 3.6], 6)
    m.box('tongue_glow', F, [6.8, 29.6, -0.8], [8.3, 32.4, 0.8], 4)
    return m


def wild_shell_bell_staff():
    """浜の貝鈴杖（アンコモン、2026-11-20 に作り直し・案 B）: 巻貝の風鈴。杖の先の流木の横木から、長さの違う巻貝が
    四つ紐で下がる（風で鳴る）。横木の両端は綱を巻いて結び、真ん中の上に結び目。光なし
    ※ 2026-11-20 に Blockbench で手を入れた（巻貝をねじれて先細る四角錐のメッシュに替え、縞と縫合線を描き、柄に重なる真ん中の一つを外して四つにした）。正本は tools/models/x32/wild_shell_bell_staff.bbmodel。この関数から prep し直すと手入れが消える"""
    m = M('wild_shell_bell_staff')
    coast_haft(m, 44)
    m.box('beam', D, [-10, 44, -1.4], [10, 46.4, 1.4], grad=(44, 4, 46.4, 6))
    for sd, n in ((-1, 'l'), (1, 'r')):
        x0, x1 = sorted((sd * 8.6, sd * 9.8))
        m.box(f'beam_wrap_{n}', R, [x0, 43.6, -1.8], [x1, 46.8, 1.8], 4)
    m.box('top_knot', R, [-1.4, 46.4, -1.4], [1.4, 47.8, 1.4], 4)
    for k, (x, L, s) in enumerate(((-7.8, 3.6, 2.6), (-4.0, 6.0, 3.0), (4.0, 5.2, 3.0), (7.8, 4.0, 2.6))):
        mat = R if k % 2 == 0 else D
        m.box(f'cord_{k}', R, [x - 0.3, 44 - L, -0.3], [x + 0.3, 44.2, 0.3], 3)
        m.box(f'shell_cap_{k}', mat, [x - s / 2 - 0.2, 44 - L - 1.4, -s / 2 - 0.2], [x + s / 2 + 0.2, 44 - L, s / 2 + 0.2], 5)
        m.seg(f'shell_{k}', mat, (x, 44 - L - 1.2), (x, 44 - L - 1.2 - s * 1.7), s, s, 4, 6, tip=True)
    return m


# ---------------------------------------------------------------- 弓

def wild_gull_bow():
    """海鳥の弓（アンコモン）: 上下の弓腕で一羽の鴎の輪郭（M の形。腕の中ほどが外へ尖る）。
    流木の弓腕の背の縁を綱の色の羽の線がなぞる。光はない"""
    m = M('wild_gull_bow')
    m.box('grip', D, [10.25, 9.5, -1.9], [13.75, 20.5, 1.9], grad=(9.5, 2, 20.5, 4), wrap=(9.5, 20.5))
    for n, y in (('u', 20.25), ('d', 8.25)):
        m.box(f'band_{n}', R, [9.75, y, -2.3], [14.25, y + 1.5, 2.3], 4)
    arm = [(12, 21), (13, 27), (12.5, 33), (10.5, 38.5), (7.5, 42.5), (4.5, 44.5)]
    arm = [(12, 21), (16, 26.5), (14, 31), (11, 36), (8, 41), (4.5, 44.5)]
    for sign, n in ((1, 'u'), (-1, 'd')):
        P = lambda q, s=sign: mirror(q, s)
        m.chain(f'wing_{n}', D, [P(q) for q in arm], [2.8, 2.6, 2.4, 2.2, 1.8], 1.2, 3, 5, tip=False)
        m.chain(f'edge_{n}', R, [P(q) for q in lib.offset_line(arm, 1.2)[1:4]], [0.9, 0.9], 1.25, 6, 7, tip=False)
    strings(m, (4.5, 44.5), (4.5, -14.5))
    return m


def wild_nacre_bow():
    """真珠層の弓（レア）: 弓腕そのものが幅広の帆立の殻の板。背の縁に放射の肋が段に立ち、
    腹（弦の側）の真珠層の縁に夜光虫の光が一筋流れる"""
    m = M('wild_nacre_bow')
    m.box('grip', D, [10.25, 9.5, -1.9], [13.75, 20.5, 1.9], grad=(9.5, 2, 20.5, 4), wrap=(9.5, 20.5))
    for n, y in (('u', 20.25), ('d', 8.25)):
        m.box(f'band_{n}', R, [9.75, y, -2.3], [14.25, y + 1.5, 2.3], 4)
    arm = [(12, 21), (13, 27), (12.5, 33), (10.5, 38.5), (7.5, 42.5), (4.5, 44.5)]
    nr = lib.line_normals(arm)
    for sign, n in ((1, 'u'), (-1, 'd')):
        P = lambda q, s=sign: mirror(q, s)
        m.chain(f'shell_{n}', R, [P(q) for q in arm], [3.6, 5, 5.2, 4.2, 2.8], 1.2, 5, 7, tip=True)
        for k in range(1, 5):
            a = lib.offset_line(arm, 2.2)[k]
            m.seg(f'rib_{n}{k}', R, P(a), P((a[0] + nr[k][0] * 1.4, a[1] + nr[k][1] * 1.4)), 1.2, 1.3, 7, 8, tip=True)
        m.chain(f'lustre_{n}_glow', F, [P(q) for q in lib.offset_line(arm, -1.8)[1:4]], [0.7, 0.7], 1.25, 4, 5, tip=False)
    strings(m, (4.5, 44.5), (4.5, -14.5))
    return m


ALL = [wild_crab_hammer, wild_shipwright_maul, wild_dune_sword, wild_oar_blade, wild_hermit_knife, wild_shore_dagger,
       wild_lure_staff, wild_tidewatch_staff, wild_fogbell_bell_staff, wild_shell_bell_staff, wild_gull_bow, wild_nacre_bow]

if __name__ == '__main__':
    want = set(sys.argv[1:])
    for fn in ALL:
        if not want or fn.__name__ in want:
            fn().build('.')
