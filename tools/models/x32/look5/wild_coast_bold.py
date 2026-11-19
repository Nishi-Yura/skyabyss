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


# ---------------------------------------------------------------- 鎚・木槌

def wild_crab_hammer():
    """大蟹の鋏鎚: 柄の先に大蟹の鋏を一つ。太い掌が鎚の頭になり（+x が打つ面）、上へ二本の指が開く。
    指の間の付け根に夜光虫が溜まって光る"""
    m = M('wild_crab_hammer')
    rope_grip(m, -6, 6)
    m.box('shaft', D, [-1.25, 6, -1.25], [1.25, 19, 1.25], grad=(6, 4, 19, 6))
    m.box('palm', D, [-4.5, 18.5, -3], [5.5, 25.5, 3], grad=(18.5, 5, 25.5, 7))
    m.box('palm_face', R, [5.5, 19.25, -2.5], [6.75, 24.75, 2.5], grad=(19.25, 3, 24.75, 5))
    m.chain('finger_fix', D, [(4, 25), (5.5, 29), (4.5, 33)], [3, 2.25], 1.5, 6, 8, tip=True)
    m.chain('finger_mov', D, [(-3, 25), (-4.5, 28.5), (-2.5, 31.5)], [2.5, 2], 1.25, 6, 8, tip=True)
    for k, y in enumerate((27, 29.5)):
        m.seg(f'tooth_{k}', R, (3.75, y), (2.5, y + 0.75), 1, 0.6, 4, 5, tip=True)
    m.box('joint_glow', F, [-1, 25.25, -1], [1.5, 26.5, 1], 4)
    m.box('knuckle', D, [-4.75, 21, -3.25], [-3.5, 23, 3.25], 8)
    return m


def wild_shipwright_maul():
    """船大工の木槌: 太い円筒の木の頭に鉄の帯が二本、片側に船釘を抜く鉤爪。
    柄は長めで綱巻き、頭の打つ面の真ん中に、打ち込んだ釘の頭が夜光虫で光る"""
    m = M('wild_shipwright_maul')
    rope_grip(m, -7, 7, 1.6)
    m.box('shaft', D, [-1.3, 7, -1.3], [1.3, 23, 1.3], grad=(7, 4, 23, 6))
    m.box('head', R, [-6, 21.5, -3.75], [6, 29.5, 3.75], grad=(21.5, 1, 29.5, 4))
    m.box('head_round', R, [-6, 22.5, -4.25], [6, 28.5, 4.25], grad=(22.5, 1, 28.5, 4))
    for k, x in enumerate((-4, 3.5)):
        m.box(f'band_{k}', D, [x, 21.25, -4.5], [x + 1.25, 29.75, 4.5], grad=(21.25, 4, 29.75, 7))
    m.box('face', D, [6, 22.25, -3.5], [6.75, 28.75, 3.5], 6)
    m.box('nail_glow', F, [6.75, 24.75, -0.75], [7.25, 26.25, 0.75], 4)
    m.chain('claw', D, [(-6, 26.5), (-8.5, 27.5), (-10, 25.5)], [2, 1.5], 1, 6, 8, tip=True)
    m.box('claw_split', R, [-9.25, 25.75, -0.2], [-7.75, 27.25, 0.2], 2)
    return m


# ---------------------------------------------------------------- 剣・大剣・短剣

def wild_dune_sword():
    """砂潜りの曲刀: 幅が先へ広がり、+x へ大きく反る片刃（背は -x）。刃の背に砂鮫の鰭のような突起が一つ。
    鍔は綱で巻いた流木の横木、刃の縁の反りの一番外に夜光虫の細い筋"""
    m = M('wild_dune_sword')
    rope_grip(m, -6, 6)
    m.box('pommel', D, [-1.75, -9.5, -1.4], [1.75, -7.5, 1.4], 7)
    m.box('guard', D, [-4, 7, -1.5], [4.5, 8.75, 1.5], grad=(7, 5, 8.75, 7))
    m.box('guard_wrap', R, [-1.9, 6.75, -1.7], [1.9, 9, 1.7], 3)
    pts = [(0.25, 8.75), (1, 15), (2.5, 21), (4.5, 26.5), (7, 31), (9.5, 34)]
    wid = [4, 4.5, 5, 5, 4]
    m.chain('blade', D, pts, wid, 0.75, 6, 9, tip=True)
    m.chain('edge_glow', F, [(x + 2.2, y - 1) for x, y in pts[1:5]], [0.9, 0.9, 0.9], 0.8, 2, 4, tip=False)
    m.seg('fin', D, (0, 17), (-2.75, 20.5), 2.5, 0.6, 6, 8, tip=True)
    return m


def wild_oar_blade():
    """渡し守の櫂剣: 渡し舟の櫂をそのまま大剣にした。長い柄の先が平たく幅広の櫂の水かきになり、
    縁だけ鉄で打ち直して刃にしてある。水かきの付け根に綱の結び、水かきの真ん中に夜光虫の水の筋"""
    m = M('wild_oar_blade')
    rope_grip(m, -8, 8, 1.6)
    m.box('loom', D, [-1.3, 8, -1.3], [1.3, 20, 1.3], grad=(8, 4, 20, 6))
    m.box('throat_tie', R, [-2, 18, -2], [2, 21, 2], grad=(18, 2, 21, 4))
    # 水かき: 下で細く、上で最大幅、先は丸めて段
    for k, (y0, y1, w) in enumerate(((21, 25, 3), (25, 31, 4.5), (31, 40, 5.5), (40, 46, 5.25), (46, 49, 4.25), (49, 51, 2.75))):
        m.box(f'blade_{k}', R, [-w, y0, -0.9], [w, y1, 0.9], grad=(y0, 2 + k * 0.3, y1, 3 + k * 0.3))
    for sd, n in ((-1, 'l'), (1, 'r')):
        m.box(f'edge_{n}', D, [sd * 5.5 - (0.75 if sd < 0 else 0), 30, -0.7], [sd * 5.5 + (0.75 if sd > 0 else 0), 46, 0.7], grad=(30, 6, 46, 8))
    m.box('edge_top', D, [-3, 50.5, -0.7], [3, 51.5, 0.7], 8)
    m.box('water_glow', F, [-0.5, 27, -1], [0.5, 45, 1], grad=(27, 1, 45, 4))
    return m


def wild_hermit_knife():
    """宿借りの隠し刃: 巻貝の殻を鞘のように柄頭にかぶせ、殻の口から細い刃が突き出る。
    刃は細い三角、殻の渦の芯が夜光虫で光る"""
    m = M('wild_hermit_knife')
    rope_grip(m, -4, 4, 1.3)
    m.box('shell_0', R, [-3, 4, -2.5], [3, 8, 2.5], grad=(4, 2, 8, 4))            # 鍔になった巻貝の殻
    m.box('shell_1', R, [-3.5, 5, -2], [-2.5, 9.5, 2], grad=(5, 3, 9.5, 5))
    m.seg('shell_spire', R, (-3, 9), (-5, 11.5), 2.25, 1.3, 4, 5, tip=True)       # 殻の尖りは -x へ
    m.box('shell_lip', D, [-2.5, 7.75, -2.25], [2.75, 8.5, 2.25], 5)             # 殻の口（刃が出る）
    m.box('whorl_glow', F, [-1.5, 5.5, -2.6], [-0.25, 6.75, -2.4], 4)
    m.box('blade', D, [-1, 8.5, -0.5], [1.75, 11, 0.5], 7)
    m.seg('blade_tip', D, (0.4, 10.5), (0.9, 21), 2.5, 0.5, 7, 9, tip=True)
    return m


def wild_shore_dagger():
    """磯走りの針: ウニの棘のような細長い針。根元に小さな磯の貝の鍔、柄は細い綱巻き。
    針の真ん中あたりに夜光虫の輪が一つ"""
    m = M('wild_shore_dagger')
    rope_grip(m, -4, 4, 1.2)
    m.box('cap', D, [-1.5, 4, -1.5], [1.5, 5.25, 1.5], 7)
    for k, (dx, dz) in enumerate(((-2, 0), (2, 0), (0, -2), (0, 2))):
        m.box(f'limpet_{k}', R, [dx - 1, 4.75, dz - 1], [dx + 1, 6, dz + 1], 4)
    m.seg('needle_0', D, (0, 5.25), (0, 13), 2, 1, 6, 7)
    m.box('ring_glow', F, [-1.25, 12.5, -1.25], [1.25, 13.75, 1.25], 4)
    m.seg('needle_1', D, (0, 13.5), (0, 23), 1.5, 0.75, 7, 9, tip=True)
    return m


# ---------------------------------------------------------------- 杖・鈴杖

def wild_lure_staff():
    """漁り火の杖: 釣り竿のように細くしなる杖の先から、綱が下がって夜光虫の漁り火の籠を吊るす。
    竿の途中に糸を通す輪が三つ"""
    m = M('wild_lure_staff')
    rope_grip(m, -14, 2, 1.3)
    m.chain('rod', D, [(0, 2), (0.5, 14), (1.5, 25), (3.5, 33), (6, 38)], [2.25, 2, 1.75, 1.25], 1, 4, 7, tip=False)
    for k, (x, y) in enumerate(((0.5, 12), (1.25, 21), (3, 30))):
        m.box(f'guide_{k}', R, [x - 1.25, y, -0.4], [x + 1.25, y + 1, 0.4], 4)
    m.box('line', R, [5.75, 27, -0.25], [6.25, 37.5, 0.25], 3)
    m.box('cage_top', D, [4.25, 26, -1.75], [7.75, 27, 1.75], 6)
    m.box('cage_glow', F, [4.5, 22, -1.5], [7.5, 26, 1.5], grad=(22, 2, 26, 4))
    for k, (x, z) in enumerate(((4.25, -1.75), (7.35, -1.75), (4.25, 1.35), (7.35, 1.35))):
        m.box(f'cage_bar_{k}', D, [x, 22, z], [x + 0.4, 26, z + 0.4], 5)
    m.box('cage_bot', D, [4.25, 21, -1.75], [7.75, 22, 1.75], 5)
    return m


def wild_tidewatch_staff():
    """潮見の杖: 潮の満ち引きを測る目盛りの杖。まっすぐな流木に綱の目盛りが等間隔、
    てっぺんは丸い潮見の輪（月の満ち欠けの印）、輪の中に夜光虫の小さな玉"""
    m = M('wild_tidewatch_staff')
    rope_grip(m, -14, 2)
    m.box('shaft', D, [-1.2, 2, -1.2], [1.2, 33, 1.2], grad=(2, 4, 33, 6))
    for k in range(6):
        y = 6 + k * 4.5
        w = 1.9 if k % 2 == 0 else 1.55
        m.box(f'mark_{k}', R, [-w, y, -w], [w, y + 0.75, w], 4)
    m.arc('ring', D, (0, 37.5), 4, 0, 360, 10, 1.5, 0.8, 5, 7)
    m.box('ring_bar', R, [-4, 37, -0.4], [4, 38, 0.4], 3)
    m.box('orb_glow', F, [-1, 36, -1], [1, 38, 1], 4)
    return m


def wild_fogbell_bell_staff():
    """霧鐘の鈴杖: 港の霧鐘を小さくして杖に吊るした。杖の先が L 字に折れ、横木から鐘が下がる。
    鐘の舌が夜光虫で光り、鐘の口の下に覗く"""
    m = M('wild_fogbell_bell_staff')
    rope_grip(m, -14, 2)
    m.box('shaft', D, [-1.25, 2, -1.25], [1.25, 34, 1.25], grad=(2, 4, 34, 6))
    m.box('arm', D, [-1.25, 32, -1.1], [8, 34, 1.1], grad=(32, 5, 34, 7))
    m.seg('brace', D, (0.5, 27), (5, 32.5), 1.5, 0.9, 5, 6)
    m.box('hanger', R, [6, 30.5, -0.4], [7, 32, 0.4], 3)
    m.box('bell_top', D, [5, 28.5, -1.5], [8, 30.5, 1.5], 6)
    m.box('bell_body', D, [4.25, 24.5, -2.25], [8.75, 28.5, 2.25], grad=(24.5, 4, 28.5, 6))
    m.box('bell_lip', R, [3.75, 23.5, -2.75], [9.25, 24.75, 2.75], 4)
    m.box('tongue_glow', F, [6, 22, -0.6], [7, 24, 0.6], 4)
    return m


def wild_shell_bell_staff():
    """浜の貝鈴杖: 杖の先に二枚貝が開いて立ち、貝の縁から小さな巻貝の鈴が三つ下がる。
    開いた貝の中に夜光虫の真珠"""
    m = M('wild_shell_bell_staff')
    rope_grip(m, -14, 2)
    m.box('shaft', D, [-1.2, 2, -1.2], [1.2, 31, 1.2], grad=(2, 4, 31, 6))
    m.box('cup', R, [-2, 30, -2], [2, 32, 2], 4)
    for sd, n in ((-1, 'l'), (1, 'r')):
        m.chain(f'valve_{n}', R, [(0, 31.5), (sd * 3.5, 33), (sd * 5, 36.5), (sd * 4, 39.5)], [3, 3.5, 2.5], 0.6, 3, 5, tip=False)
        for k, y in enumerate((34, 37.5)):
            m.box(f'rib_{n}{k}', D, [sd * (2.5 + k) - 0.3, y, -0.7], [sd * (2.5 + k) + 0.3, y + 1.5, 0.7], 5)
    m.box('pearl_glow', F, [-1, 33.5, -1], [1, 35.5, 1], 4)
    for k, x in enumerate((-4, 0, 4)):
        y0 = 26.5 if x else 25
        m.box(f'string_bell_{k}', R, [x - 0.2, y0 + 3, -0.2], [x + 0.2, 31, 0.2], 3)
        m.seg(f'snail_{k}', D, (x, y0 + 3), (x + 0.5, y0), 2, 1, 5, 7, tip=True)
    return m


# ---------------------------------------------------------------- 弓

def wild_gull_bow():
    """海鳥の弓: 海鳥の翼の形。握りから弓腕が翼の骨のように一度後ろへ折れ、先で前へ戻る。
    弓腕の外側に白い羽根の段（綱の色）、上の先に夜光虫の目のような点"""
    m = M('wild_gull_bow')
    m.box('grip', R, [10.5, 10, -1.5], [13.5, 20, 1.5], grad=(10, 2, 20, 4), wrap=(10, 20))
    arm = [(12, 19.5), (13.5, 26), (11.5, 33), (8, 39), (5, 43.5)]
    for sign, n in ((1, 'u'), (-1, 'd')):
        m.chain(f'arm_{n}', D, [mirror(p, sign) for p in arm], [3, 2.75, 2.5, 2], 1.25, 4, 7, tip=False)
        for k, (x, y) in enumerate(((14, 27), (12.5, 33), (9.5, 38.5))):
            m.seg(f'feather_{n}{k}', R, mirror((x, y), sign), mirror((x + 3, y - 2.5), sign), 2, 0.5, 4, 5, tip=True)
        m.seg(f'nock_{n}', D, mirror((5.5, 43.5), sign), mirror((2.5, 43.5), sign), 2, 1, 6, 7)
    m.box('eye_glow', F, [4.75, 41.5, -1.35], [5.75, 42.5, 1.35], 4)
    strings(m, (3, 43.5), (3, -13.5))
    return m


def wild_nacre_bow():
    """真珠層の弓: 二枚の大きな貝殻を背中合わせに削って作った短い弓。弓腕は幅広の板で、外の縁が段になって細る。
    弓腕の内側（真珠層）に夜光虫の光が一筋流れる"""
    m = M('wild_nacre_bow')
    m.box('grip', D, [10.5, 10, -1.75], [13.5, 20, 1.75], grad=(10, 3, 20, 5), wrap=(10, 20))
    for sign, n in ((1, 'u'), (-1, 'd')):
        pts = [(12, 19.5), (11.25, 26), (9.75, 32), (7.75, 37), (6, 41)]
        m.chain(f'plate_{n}', R, [mirror(p, sign) for p in pts], [3.75, 3.5, 3, 2.5], 1.1, 3, 5, tip=False)
        for k, (x, y) in enumerate(((11.5, 26), (9.75, 32), (7.75, 37))):          # 貝の成長線の段
            m.seg(f'growth_{n}{k}', D, mirror((x - 2, y), sign), mirror((x + 2, y), sign), 0.6, 1.2, 4, 5, over=0)
        m.seg(f'nock_{n}', D, mirror((6.5, 41), sign), mirror((3.5, 41), sign), 2, 1, 5, 6)
    m.chain('nacre_glow', F, [(12.25, 21), (11, 27), (9, 33)], [0.8, 0.8], 1.2, 2, 4, tip=False)
    strings(m, (4, 41), (4, -11))
    for key, pull in lib.PULLS:
        for k, y in enumerate((33, 30, -3, 0)):
            q = lib.string_at((4, 41), (4, -11), pull, lib.string_t((4, 41), (4, -11), y))
            m.box(f'string_{key}_pearl{k}', R, [q[0] - 0.75, q[1] - 0.75, -0.75], [q[0] + 0.75, q[1] + 0.75, 0.75], 5)
    return m


ALL = [wild_crab_hammer, wild_shipwright_maul, wild_dune_sword, wild_oar_blade, wild_hermit_knife, wild_shore_dagger,
       wild_lure_staff, wild_tidewatch_staff, wild_fogbell_bell_staff, wild_shell_bell_staff, wild_gull_bow, wild_nacre_bow]

if __name__ == '__main__':
    want = set(sys.argv[1:])
    for fn in ALL:
        if not want or fn.__name__ in want:
            fn().build('.')
