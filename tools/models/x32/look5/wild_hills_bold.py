# -*- coding: utf-8 -*-
"""野・丘（look5・個性版）。岩と苔の丘で拾う 5 品（稀）。
色: 苔むした岩と古木（loam 緑がかった焦げ茶の 1 系統）、差し色は獣の骨と牙（tusk 象牙）、光は苔の胞子（spore 淡い青緑）を品ごとに一か所。
丘の獣（猪・岩殻の甲虫・骨鳥）から取った素材を、紐と樹脂で縛った手作りの道具。
"""
import math
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import lib  # noqa: E402
from lib import Model  # noqa: E402

lib.RAMPS.update({
    'loam': ['#14160e', '#1e2214', '#2a2e1c', '#383c26', '#484c30', '#5a5e3c', '#6e724a', '#868a5c', '#a2a474', '#c2c294'],
    'tusk': ['#4a4232', '#766a50', '#a29474', '#c8bc9a', '#e6dcc0', '#faf4e4'],
    'spore': ['#0e5244', '#16806a', '#28ac90', '#5ad4b6', '#a0f0da', '#e4fff6'],
})
L, T, S = 'loam', 'tusk', 'spore'


def M(name):
    return Model(name, accent=T, glowmat=S)


def bound_grip(m, y0, y1, r=1.5):
    """樹脂で固めた古木の握り。上下を骨の輪で締める"""
    m.box('grip', L, [-r, y0, -r], [r, y1, r], grad=(y0, 3, y1, 5), wrap=(y0, y1))
    m.box('bone_ring_d', T, [-r - 0.35, y0 - 1, -r - 0.35], [r + 0.35, y0, r + 0.35], 3)
    m.box('bone_ring_u', T, [-r - 0.35, y1, -r - 0.35], [r + 0.35, y1 + 1, r + 0.35], 4)


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


def hill_haft(m, top, r=1.3):
    bound_grip(m, -12, 2, 1.6)
    m.box('shaft', L, [-r, 3, -r], [r, top, r], grad=(3, 3, top, 5))
    m.box('butt', T, [-2.0, -15, -2.0], [2.0, -13, 2.0], grad=(-15, 3, -13, 5))


def wild_bonebird_totem():
    """骨鳥の祈り柱（レア、2026-11-20 に作り直し・案 C）: 三羽の骨鳥。柱に三羽の骨鳥が重なって止まる（下ほど大きく、交互に左右を向く）。いちばん上の鳥の目が光る
    ※ 2026-11-20 に Blockbench で手を入れた（3 羽の鳥を柱の軸のまわりでそれぞれ違う向きに回し、柱を細くして頭が埋もれないようにした）。正本は tools/models/x32/wild_bonebird_totem.bbmodel。この関数から prep し直すと手入れが消える"""
    m = M('wild_bonebird_totem')
    bound_grip(m, -14, 2, 1.6)
    m.box('pillar', L, [-1.9, 3, -1.9], [1.9, 40, 1.9], grad=(3, 3, 40, 5))
    for k, (y, s, sd) in enumerate(((22, 1.25, 1), (30, 1.05, -1), (38, 0.85, 1))):
        m.box(f'body_{k}', T, [-2.2 * s, y, -2.4 * s], [2.2 * s, y + 4 * s, 2.4 * s], grad=(y, 3, y + 4 * s, 5))
        m.box(f'head_{k}', T, [-1.5 * s, y + 4 * s, -1.6 * s], [1.5 * s, y + 6.4 * s, 1.6 * s], 5)
        m.seg(f'beak_{k}', L, (sd * 1.5 * s, y + 5.2 * s), (sd * 4.2 * s, y + 4.7 * s), 1.1, 0.6, 5, 6, tip=True)
        for wd, n in ((-1, 'l'), (1, 'r')):
            m.chain(f'wing_{k}{n}', T, [(wd * 2.0 * s, y + 3 * s), (wd * 5.5 * s, y + 3.5 * s), (wd * 7.5 * s, y + 1.5 * s)], [1.8, 1.2], 0.6, 3, 5, tip=True)
    for sd, z in ((1, 1.36), (-1, -1.66)):
        m.box(f'eye_{"fb"[sd < 0]}_glow', S, [0.2, 43.4, z], [0.9, 44.1, z + 0.3], 4)
    return m


def wild_bonespike_bow():
    """骨棘の弓（レア）: 弓腕そのものが太い獣の牙。根元が太く先へ細り、二か所に古木の帯が巻かれる。
    牙の先の尖りが弦を受ける。握りの上の樹脂の塊に胞子が一粒光る"""
    m = M('wild_bonespike_bow')
    m.box('grip', L, [10.25, 9.5, -1.9], [13.75, 20.5, 1.9], grad=(9.5, 2, 20.5, 4), wrap=(9.5, 20.5))
    for n, y in (('u', 20.25), ('d', 8.25)):
        m.box(f'band_{n}', T, [9.75, y, -2.3], [14.25, y + 1.5, 2.3], 4)
    arm = [(12, 21), (13, 27), (12.5, 33), (10.5, 38.5), (7.5, 42.5), (4.5, 44.5)]
    for sign, n in ((1, 'u'), (-1, 'd')):
        P = lambda q, s=sign: mirror(q, s)
        m.chain(f'tusk_{n}', T, [P(q) for q in arm], [4.4, 4, 3.4, 2.8, 2.0], 1.8, 3, 7, tip=True)
        for k in (1, 3):
            m.seg(f'ring_{n}{k}', L, P(lib.offset_line(arm, 1.6)[k]), P(lib.offset_line(arm, -1.6)[k]), 1.0, 1.9, 3, 4, over=0)
    m.box('resin', L, [13.5, 20.5, -1.2], [15, 23, 1.2], 3)
    m.box('spore_glow', S, [14.6, 21.2, -0.7], [15.3, 22.4, 0.7], 4)
    strings(m, (5, 44), (5, -14))
    return m


def wild_moss_staff():
    """苔灯りの杖（レア、2026-11-20 に作り直し・案 B）: 棚茸の杖。古木の杖の上半分に、棚のような大きな茸が段違いに四枚生える。いちばん上の茸の裏のひだが光る
    ※ 2026-11-20 に Blockbench で手を入れた（棚茸を半月形で上の盛り上がったメッシュにし、年輪の帯を描き、頂の茸を大きくした）。正本は tools/models/x32/wild_moss_staff.bbmodel。この関数から prep し直すと手入れが消える"""
    m = M('wild_moss_staff')
    bound_grip(m, -14, 2, 1.4)
    m.box('shaft', L, [-1.4, 3, -1.4], [1.4, 44, 1.4], grad=(3, 3, 44, 5))
    for k, (sd, y, w) in enumerate(((1, 26, 5.0), (-1, 31, 5.5), (1, 36, 6.0), (-1, 41, 6.5))):
        x0, x1 = sorted((sd * 0.8, sd * (0.8 + w)))
        m.box(f'shelf_{k}', T, [x0, y, -2.6], [x1, y + 1.6, 2.6], grad=(y, 3, y + 1.6, 5))
        m.box(f'gill_{k}', L, [x0 + 0.3, y - 0.5, -2.2], [x1 - 0.3, y, 2.2], 3)
    m.box('cap_top', T, [-3.2, 44, -3.2], [3.2, 46.4, 3.2], grad=(44, 3, 46.4, 5))
    m.box('cap_crown', T, [-2.0, 46.4, -2.0], [2.0, 47.6, 2.0], 5)
    m.box('gill_glow', S, [-2.8, 43.4, -2.8], [2.8, 44.0, 2.8], 4)
    return m


def wild_shell_claw():
    """岩殻の爪（レア、2026-11-20 に作り直し・案 B）: 甲虫の大顎。大きな甲虫の頭の殻が拳を覆い、二本の大顎がはさみのように前（上）へ伸びて内へ曲がる。内側に小さな歯。目が光る
    ※ 2026-11-20 に Blockbench で手を入れた（大顎を内刃の薄い反ったメッシュにして先を噛み合わせ、内側の歯を大顎に付け直した）。正本は tools/models/x32/wild_shell_claw.bbmodel。この関数から prep し直すと手入れが消える"""
    m = M('wild_shell_claw')
    bound_grip(m, -7, 3, 1.4)
    m.box('head', L, [-3.6, 3, -3.0], [3.6, 10, 3.0], grad=(3, 4, 10, 7))
    for sd, n in ((-1, 'l'), (1, 'r')):
        m.chain(f'mandible_{n}', T, [(sd * 2.4, 9.5), (sd * 4.4, 14), (sd * 4.0, 19), (sd * 1.4, 23)], [2.2, 1.9, 1.4], 1.1, 3, 6, tip=True)
        for k, y in enumerate((14.5, 18)):
            m.seg(f'tooth_{n}{k}', T, (sd * 3.4, y), (sd * 2.2, y + 0.8), 1.0, 0.8, 5, 6, tip=True)
        m.box(f'eye_{n}_glow', S, [sd * 3.6 - 0.6, 7, -2.0], [sd * 3.6 + 0.6, 8.4, 2.0], 4)
    return m


def wild_tusk_axe():
    """猪牙の斧（レア、2026-11-20 に作り直し・案 A）: 大猪の牙斧。大猪の二本の牙を背中合わせに縛って、三日月の刃にした斧。柄頭は蹄、縛りの結び目に胞子の光
    ※ 2026-11-20 に Blockbench で手を入れた（2 本の牙の箱を三日月の一枚刃メッシュにまとめ、牙の継ぎ目と象牙の筋を描いた）。正本は tools/models/x32/wild_tusk_axe.bbmodel。この関数から prep し直すと手入れが消える"""
    m = M('wild_tusk_axe')
    hill_haft(m, 32)
    m.box('lashing', T, [-1.8, 24, -1.8], [1.8, 30, 1.8], grad=(24, 2, 30, 4), wrap=(24, 30))
    m.chain('tusk_hi', T, [(1.2, 29), (5.5, 31), (9.5, 34), (12, 38.5)], [4.2, 3.6, 2.6], 1.4, 3, 6, tip=True)
    m.chain('tusk_lo', T, [(1.2, 25), (5.5, 23), (9.5, 20), (12, 15.5)], [4.2, 3.6, 2.6], 1.4, 3, 6, tip=True)
    m.box('bite', L, [8.5, 22, -0.9], [10.5, 32, 0.9], 3)
    m.chain('tusk_back', T, [(-1.2, 28), (-4.5, 29), (-6.5, 31.5)], [2.4, 1.6], 1.1, 3, 5, tip=True)
    m.box('knot_glow', S, [-2.1, 26.5, -0.6], [-1.7, 28, 0.6], 4)
    m.box('knot_glow_b', S, [1.7, 26.5, -0.6], [2.1, 28, 0.6], 4)
    return m


ALL = [wild_bonebird_totem, wild_bonespike_bow, wild_moss_staff, wild_shell_claw, wild_tusk_axe]

if __name__ == '__main__':
    want = set(sys.argv[1:])
    for fn in ALL:
        if not want or fn.__name__ in want:
            fn().build('.')
